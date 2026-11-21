package com.steamdeck.mc.gui;

import com.steamdeck.mc.config.ModConfig;
import com.steamdeck.mc.input.SteamDeckInputManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Live measurement line for the right trackpad (option "trackpadDebugOverlay"): real pad samples per
 * second while the finger moves, average interval and jitter, rendered frames per second. If the pad
 * delivers far fewer samples than frames, the pointer only moves at the pad rate without interpolation.
 */
public final class TrackpadDebugOverlay {
    private TrackpadDebugOverlay() {}

    public static void render(GuiGraphicsExtractor g, int screenWidth, int screenHeight) {
        if (!ModConfig.getInstance().trackpadDebugOverlay) return;
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.font == null) return;
        Font font = client.font;
        String line = SteamDeckInputManager.getInstance().getTrackpadDebugLine();
        int w = font.width(line) + 8;
        int x = (screenWidth - w) / 2;
        int y = screenHeight - 14;
        g.fill(x, y, x + w, y + 12, 0xCC000000);
        g.text(font, line, x + 4, y + 2, 0xFFFFEE88, false);
    }
}
