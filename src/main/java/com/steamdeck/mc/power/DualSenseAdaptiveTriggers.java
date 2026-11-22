package com.steamdeck.mc.power;

import com.steamdeck.mc.SteamDeckEssentials;
import com.steamdeck.mc.config.ModConfig;
import com.steamdeck.mc.input.ControllerKind;
import org.lwjgl.BufferUtils;
import org.lwjgl.sdl.SDLGamepad;

import java.nio.ByteBuffer;
import java.util.Arrays;

/**
 * Controls the Sony PS5 DualSense adaptive triggers (L2 / R2 motorized resistance)
 * via SDL3's {@link SDLGamepad#SDL_SendGamepadEffect(long, ByteBuffer)} (47-byte DS5EffectsState_t).
 *
 * <p>SDL3 handles USB vs Bluetooth framing and CRC32 automatically. We only send an effect packet
 * when the computed trigger parameters actually change, and reset both triggers to OFF on disconnect
 * or when disabled.</p>
 */
public final class DualSenseAdaptiveTriggers {
    private static final DualSenseAdaptiveTriggers INSTANCE = new DualSenseAdaptiveTriggers();

    private static final int EFFECT_SIZE = 47;
    private static final byte ENABLE_RIGHT_TRIGGER = 0x04;
    private static final byte ENABLE_LEFT_TRIGGER = 0x08;

    // DualSense trigger effect modes (DS5EffectsState_t rgucRightTriggerEffect / rgucLeftTriggerEffect)
    private static final byte MODE_OFF = 0x00;
    private static final byte MODE_CONTINUOUS = 0x01; // [mode, startPos, force, ...]
    private static final byte MODE_SECTION = 0x02;    // [mode, startPos, endPos, force, ...]

    private final ByteBuffer effectBuf = BufferUtils.createByteBuffer(EFFECT_SIZE);
    private final byte[] lastRightEffect = new byte[11];
    private final byte[] lastLeftEffect = new byte[11];
    private long activeHandle = 0;
    private boolean initialized = false;

    public static DualSenseAdaptiveTriggers getInstance() {
        return INSTANCE;
    }

    private DualSenseAdaptiveTriggers() {}

    /**
     * Updates adaptive trigger resistance for the active controller.
     *
     * @param handle         SDL_Gamepad handle (0 if disconnected)
     * @param kind           Active controller kind
     * @param inMenu         True when a GUI screen / menu is open
     * @param isAiming       True when actively drawing a bow, crossbow, trident, or spyglass
     * @param bowProgress    0..1 draw progress when aiming (0 otherwise)
     * @param blockHardness  -2 = no block targeted, -1 = unbreakable (bedrock), >= 0 = destroySpeed
     * @param blocking       True when actively blocking with a shield
     * @param lowDurability  True when held main-hand tool has < 10% durability remaining
     */
    public void update(long handle, ControllerKind kind, boolean inMenu, boolean isAiming,
                       float bowProgress, float blockHardness, boolean blocking, boolean lowDurability) {
        if (handle == 0 || !kind.hasAdaptiveTriggers()) {
            clear(handle);
            return;
        }

        if (handle != activeHandle) {
            activeHandle = handle;
            initialized = false;
        }

        ModConfig cfg = ModConfig.getInstance();
        String mode = cfg.dualSenseTriggerMode;
        float strength = Math.max(0.05f, Math.min(1.0f, cfg.dualSenseTriggerResistance));

        byte[] left = new byte[11];
        byte[] right = new byte[11];

        if (inMenu) {
            left[0] = MODE_OFF;
            right[0] = MODE_OFF;
        } else {
            // Left trigger (L2): Shield block > base mode
            if (cfg.adaptiveTriggerShield && blocking) {
                buildContinuous(left, 0x05, toByte(Math.min(1.0f, 0.80f * strength)));
            } else {
                applyBaseMode(left, mode, cfg.leftTriggerThreshold, strength);
            }

            // Right trigger (R2): Bow tension > Block hardness > Low durability > base mode
            if (cfg.adaptiveTriggerBowTension && isAiming) {
                float dynamicForce = Math.min(1.0f, 0.30f + bowProgress * 0.70f) * strength;
                buildContinuous(right, 0x10, toByte(dynamicForce));
            } else if (cfg.adaptiveTriggerBlockHardness && blockHardness != -2.0f && blockHardness != 0.0f) {
                if (blockHardness < 0.0f) {
                    // Unbreakable (e.g. bedrock): full lock resistance
                    buildContinuous(right, 0x05, toByte(strength));
                } else {
                    // Logarithmic mapping: dirt (0.5) ~ 10%, stone (1.5) ~ 23%, iron (3) ~ 35%, obsidian (50) = 100%
                    float norm = (float) (Math.log1p(blockHardness) / Math.log1p(50.0));
                    float scaled = Math.max(0.12f, Math.min(1.0f, norm)) * strength;
                    buildContinuous(right, 0x10, toByte(scaled));
                }
            } else if (cfg.adaptiveTriggerLowDurability && lowDurability) {
                buildSection(right, cfg.rightTriggerThreshold, Math.min(1.0f, Math.max(0.75f, strength)));
            } else {
                applyBaseMode(right, mode, cfg.rightTriggerThreshold, strength);
            }
        }

        if (!initialized || !Arrays.equals(left, lastLeftEffect) || !Arrays.equals(right, lastRightEffect)) {
            send(handle, left, right);
        }
    }

    private static void applyBaseMode(byte[] out, String mode, float threshold, float strength) {
        if ("RESISTANCE".equals(mode)) {
            buildContinuous(out, 0x00, toByte(strength));
        } else if ("WEAPON_CLICK".equals(mode)) {
            buildSection(out, threshold, strength);
        } else {
            out[0] = MODE_OFF;
        }
    }

    /** Resets triggers to OFF if a DualSense handle was previously active. */
    public void clear(long currentHandle) {
        long target = activeHandle != 0 ? activeHandle : currentHandle;
        if (initialized && target != 0) {
            byte[] off = new byte[11];
            off[0] = MODE_OFF;
            send(target, off, off);
        }
        activeHandle = 0;
        initialized = false;
        Arrays.fill(lastLeftEffect, (byte) 0);
        Arrays.fill(lastRightEffect, (byte) 0);
    }

    private static void buildContinuous(byte[] out, int startPos, byte force) {
        out[0] = MODE_CONTINUOUS;
        out[1] = (byte) Math.max(0, Math.min(255, startPos));
        out[2] = force;
    }

    private static void buildSection(byte[] out, float threshold, float strength) {
        int start = Math.max(10, Math.min(220, Math.round(threshold * 255.0f)));
        int end = Math.min(255, start + 35);
        out[0] = MODE_SECTION;
        out[1] = (byte) start;
        out[2] = (byte) end;
        out[3] = toByte(strength);
    }

    private static byte toByte(float normalized) {
        return (byte) Math.max(0, Math.min(255, Math.round(normalized * 255.0f)));
    }

    private void send(long handle, byte[] left, byte[] right) {
        effectBuf.clear();
        for (int i = 0; i < EFFECT_SIZE; i++) {
            effectBuf.put(i, (byte) 0);
        }
        effectBuf.put(0, (byte) (ENABLE_RIGHT_TRIGGER | ENABLE_LEFT_TRIGGER));
        for (int i = 0; i < 11; i++) {
            effectBuf.put(10 + i, right[i]);
            effectBuf.put(21 + i, left[i]);
        }
        effectBuf.position(0);
        effectBuf.limit(EFFECT_SIZE);
        try {
            boolean ok = SDLGamepad.SDL_SendGamepadEffect(handle, effectBuf);
            if (!ok && !initialized) {
                SteamDeckEssentials.LOGGER.debug("[INPUT] SDL_SendGamepadEffect returned false for DualSense triggers");
            }
        } catch (Throwable t) {
            SteamDeckEssentials.LOGGER.debug("[INPUT] DualSense adaptive trigger update failed", t);
        }
        System.arraycopy(left, 0, lastLeftEffect, 0, 11);
        System.arraycopy(right, 0, lastRightEffect, 0, 11);
        initialized = true;
    }
}
