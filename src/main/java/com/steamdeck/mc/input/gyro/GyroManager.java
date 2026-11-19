package com.steamdeck.mc.input.gyro;

import com.steamdeck.mc.SteamDeckEssentials;
import com.steamdeck.mc.config.ModConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

/**
 * Coordinates hardware gyro providers and enforces state-based contextual triggering.
 * Only applies gyro deltas to player camera when actively drawing/using aiming items
 * (Bow, Crossbow, Trident, Spyglass) and no GUI screen is open.
 */
public class GyroManager {
    private static final GyroManager INSTANCE = new GyroManager();

    private final List<GyroProvider> providers = new ArrayList<>();
    private GyroProvider activeProvider = null;

    public static GyroManager getInstance() {
        return INSTANCE;
    }

    private GyroManager() {}

    public void init() {
        providers.clear();
        GyroProvider[] candidates = {
                new IIOGyroProvider(),
                new SDL2GyroProvider(),
                new DsuGyroProvider()
        };

        for (GyroProvider provider : candidates) {
            if (provider.init()) {
                providers.add(provider);
                if (activeProvider == null && provider.isAvailable()) {
                    activeProvider = provider;
                }
            }
        }

        SteamDeckEssentials.LOGGER.info("[GYRO] Initialized {} gyro provider backends ( contextual bow/aiming trigger ready ).", providers.size());
    }

    /** Minecraft's Entity.turn() multiplies its arguments by 0.15 to get degrees. */
    private static final double TURN_UNITS_PER_DEGREE = 1.0 / 0.15;

    /**
     * Polled on every frame to evaluate contextual aim and apply rotation deltas.
     *
     * <p>All providers are drained every frame, even while not aiming. Otherwise motion that
     * accumulated while e.g. walking around (DSU) was applied as one big camera jump the moment
     * the bow was drawn.</p>
     */
    public void update(Minecraft client) {
        if (client == null) return;

        // Use the first provider delivering motion; if several deliver the same physical gyro
        // (e.g. SDL + DSU), stick with the one used last frame.
        GyroData chosen = GyroData.ZERO;
        GyroProvider chosenProvider = null;
        for (GyroProvider provider : providers) {
            GyroData data = provider.poll();
            if (data.isZero()) continue;
            if (chosenProvider == null || provider == activeProvider) {
                chosen = data;
                chosenProvider = provider;
            }
        }
        if (chosenProvider != null) {
            activeProvider = chosenProvider;
        }

        ModConfig config = ModConfig.getInstance();
        if (!config.enableGyroAiming || chosen.isZero()) return;

        // 1. Disabled while any screen/GUI is open
        if (client.gui != null && client.gui.screen() != null) return;

        // 2. Player must exist and mouse must be captured
        LocalPlayer player = client.player;
        if (player == null || !client.mouseHandler.isMouseGrabbed()) return;

        // 3. Activation like Steam Input's "gyro enable button"
        if (!isGyroActive(config, player)) return;

        // 4. Sensitivity 1.0 = 1:1 real-world rotation (frame-rate independent)
        double yaw = chosen.yawDelta() * config.gyroSensitivityX * (config.invertGyroX ? -1.0 : 1.0);
        double pitch = chosen.pitchDelta() * config.gyroSensitivityY * (config.invertGyroY ? -1.0 : 1.0);
        player.turn(yaw * TURN_UNITS_PER_DEGREE, pitch * TURN_UNITS_PER_DEGREE);
    }

    /** Whether the gyro may turn the camera right now (see ModConfig.gyroActivation). */
    public boolean isGyroActive(ModConfig config, LocalPlayer player) {
        com.steamdeck.mc.input.SteamDeckInputManager input = com.steamdeck.mc.input.SteamDeckInputManager.getInstance();
        return switch (config.gyroActivation) {
            case "ALWAYS" -> true;
            case "TRACKPAD_TOUCH" -> input.isTrackpadTouched(0) || input.isTrackpadTouched(1);
            case "RIGHT_STICK_TOUCH" -> input.isButtonDown(com.steamdeck.mc.input.DeckButton.RIGHT_STICK_TOUCH);
            default -> isContextuallyAiming(player);
        };
    }

    public static String activationLabel(String activation) {
        return switch (activation) {
            case "ALWAYS" -> "Immer an";
            case "TRACKPAD_TOUCH" -> "Trackpad berührt";
            case "RIGHT_STICK_TOUCH" -> "R-Stick berührt";
            default -> "Nur beim Zielen";
        };
    }

    /**
     * Checks if the player is actively charging/drawing an aim-capable item (Bow, Crossbow, Trident, Spyglass).
     */
    public boolean isContextuallyAiming(LocalPlayer player) {
        if (player == null || !player.isUsingItem()) return false;

        ItemStack useItem = player.getUseItem();
        if (useItem.isEmpty()) return false;

        Item item = useItem.getItem();
        return item == Items.BOW
                || item == Items.CROSSBOW
                || item == Items.TRIDENT
                || item == Items.SPYGLASS;
    }

    public GyroProvider getActiveProvider() {
        return activeProvider;
    }

    public String getActiveProviderName() {
        if (activeProvider != null && activeProvider.isAvailable()) {
            return activeProvider.getName();
        }
        for (GyroProvider p : providers) {
            if (p.isAvailable()) {
                return p.getName();
            }
        }
        return "Steam Input / Standby (Aktiv beim Zielen)";
    }
}
