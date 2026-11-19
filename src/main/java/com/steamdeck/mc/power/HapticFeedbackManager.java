package com.steamdeck.mc.power;

import com.steamdeck.mc.SteamDeckEssentials;
import com.steamdeck.mc.config.ModConfig;
import com.steamdeck.mc.input.SteamDeckInputManager;
import org.lwjgl.sdl.SDLGamepad;
import org.lwjgl.sdl.SDLJoystick;

/**
 * Handles precision haptic rumble on the Steam Deck's dual trackpad actuators.
 * Routes left motor to the Left Trackpad actuator and right motor to the Right Trackpad actuator.
 */
public class HapticFeedbackManager {
    private static final HapticFeedbackManager INSTANCE = new HapticFeedbackManager();

    public static HapticFeedbackManager getInstance() {
        return INSTANCE;
    }

    private HapticFeedbackManager() {}

    /**
     * Triggers a crisp directional haptic pulse on the trackpad corresponding to the scroll direction.
     * @param direction -1 for Left Trackpad, +1 for Right Trackpad
     */
    public void triggerHotbarHaptic(int direction) {
        if (!ModConfig.getInstance().enableHotbarHaptics) return;
        // Steam Controller 2026: real trackpad actuator tick instead of the grip motors
        if (TritonTrackpadHaptics.getInstance().pulse(direction > 0, TritonTrackpadHaptics.COMMAND_TICK, 2, 0)) return;
        triggerHaptic(direction < 0, direction > 0, (short) 38000, 35);
    }

    /**
     * Click feel for a physical trackpad press (strong, short) or release (softer), on the side of the pad.
     * Steam Input produces this itself, so it is only used for native Valve controllers.
     */
    public void triggerTrackpadClickHaptic(boolean rightPad, boolean pressed) {
        if (!ModConfig.getInstance().enableTrackpadClickHaptics) return;
        if (!SteamDeckInputManager.getInstance().getActiveKind().isNativeValve()) return;
        // Steam Controller 2026: the actuator inside the pressed trackpad (click on press, soft tick on release)
        // press: Click, intensity 3, 0 dB · release: Tick, intensity 2, -3 dB
        if (TritonTrackpadHaptics.getInstance().pulse(rightPad,
                pressed ? TritonTrackpadHaptics.COMMAND_CLICK : TritonTrackpadHaptics.COMMAND_TICK,
                pressed ? 3 : 2, pressed ? 0 : -3)) {
            return;
        }
        short intensity = pressed ? (short) 40000 : (short) 22000;
        int durationMs = pressed ? 22 : 14;
        triggerHaptic(!rightPad, rightPad, intensity, durationMs);
    }

    /**
     * Haptic meant for a trackpad (UI ticks/clicks). Uses the real pad actuator on the Steam
     * Controller 2026 and falls back to the rumble motor of that side otherwise.
     */
    public void triggerPadHaptic(boolean rightPad, boolean strong, short fallbackIntensity, int fallbackMs) {
        // strong: Click, intensity 3 · light: Tick, intensity 1, 0 dB (= SteamOS keyboard key change)
        if (TritonTrackpadHaptics.getInstance().pulse(rightPad,
                strong ? TritonTrackpadHaptics.COMMAND_CLICK : TritonTrackpadHaptics.COMMAND_TICK, strong ? 3 : 1, 0)) {
            return;
        }
        triggerHaptic(!rightPad, rightPad, fallbackIntensity, fallbackMs);
    }

    /**
     * Triggers haptic feedback with side selection, intensity and duration.
     */
    public void triggerHaptic(boolean leftSide, boolean rightSide, short intensity, int durationMs) {
        SteamDeckInputManager input = SteamDeckInputManager.getInstance();
        long gamepad = input.getActiveGamepadHandle();
        long joystick = input.getActiveJoystickHandle();

        short left = leftSide ? intensity : 0;
        short right = rightSide ? intensity : 0;

        try {
            if (gamepad != 0) {
                SDLGamepad.SDL_RumbleGamepad(gamepad, left, right, durationMs);
            } else if (joystick != 0) {
                SDLJoystick.SDL_RumbleJoystick(joystick, left, right, durationMs);
            }
        } catch (Throwable t) {
            SteamDeckEssentials.LOGGER.debug("Haptic rumble dispatch error", t);
        }
    }
}
