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

    /**
     * Polled on every frame to evaluate contextual aim and apply rotation deltas.
     */
    public void update(Minecraft client) {
        if (client == null) return;

        ModConfig config = ModConfig.getInstance();
        if (!config.enableGyroAiming) return;

        // 1. Disabled immediately if any screen/GUI is active
        if (client.gui != null && client.gui.screen() != null) return;

        // 2. Player must exist and mouse must be captured
        LocalPlayer player = client.player;
        if (player == null || !client.mouseHandler.isMouseGrabbed()) return;

        // 3. Contextual Trigger: Only when actively drawing/using Bow, Crossbow, Trident, or Spyglass
        if (config.aimItemsOnly && !isContextuallyAiming(player)) {
            return;
        }

        // 4. Poll all initialized providers and use whichever is actively delivering motion data
        for (GyroProvider provider : providers) {
            GyroData data = provider.poll();
            if (provider.isAvailable() && activeProvider == null) {
                activeProvider = provider;
            }
            if (data.pitchDelta() != 0.0f || data.yawDelta() != 0.0f) {
                activeProvider = provider;
                float yaw = data.yawDelta() * config.gyroSensitivityX * (config.invertGyroX ? -1.0f : 1.0f);
                float pitch = data.pitchDelta() * config.gyroSensitivityY * (config.invertGyroY ? -1.0f : 1.0f);
                player.turn((double) yaw, (double) pitch);
                break;
            }
        }
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
        for (GyroProvider p : providers) {
            if (p.isAvailable()) {
                return p.getName();
            }
        }
        return "Steam Input / Standby (Aktiv beim Zielen)";
    }
}
