package com.steamdeck.mc.gui;

import com.steamdeck.mc.config.ModConfig;
import com.steamdeck.mc.input.ActionBinding;
import com.steamdeck.mc.input.ActionSet;
import com.steamdeck.mc.input.DeckButton;
import com.steamdeck.mc.power.BatteryInfo;
import com.steamdeck.mc.power.BatteryReader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;

/**
 * Renders minimalist, clean battery metrics onto screens (PauseScreen, F3 Debug Overlay).
 */
public class BatteryHudRenderer {

    /**
     * Renders a sleek battery pill in the Pause menu.
     */
    public static void renderPauseScreenHud(GuiGraphicsExtractor graphics, int screenWidth, int screenHeight) {
        if (!ModConfig.getInstance().enableBatteryHudInPause) return;
        int nextY = 8;
        if (com.steamdeck.mc.platform.PlatformDetector.isHandheld()) {
            nextY = renderDeviceBatteryPill(graphics);
        }
        renderControllerBatteryPill(graphics, nextY);
    }

    /** Battery of a wireless controller (e.g. Steam Controller 2026), in both device modes. */
    private static void renderControllerBatteryPill(GuiGraphicsExtractor graphics, int y) {
        com.steamdeck.mc.input.SteamDeckInputManager input = com.steamdeck.mc.input.SteamDeckInputManager.getInstance();
        if (!input.hasController()) return;
        // The Deck's built-in controller has no own battery: the device pill above already covers it
        if (input.getActiveKind() == com.steamdeck.mc.input.ControllerKind.STEAM_DECK) return;
        int[] bat = input.getControllerBattery();
        int state = bat[0];
        int percent = bat[1];
        if (state != org.lwjgl.sdl.SDLPower.SDL_POWERSTATE_ON_BATTERY
                && state != org.lwjgl.sdl.SDLPower.SDL_POWERSTATE_CHARGING
                && state != org.lwjgl.sdl.SDLPower.SDL_POWERSTATE_CHARGED) {
            return; // wired without battery / unknown
        }
        String label = input.getActiveKind().getLabel();
        String text = "🎮 " + label + ": " + (percent >= 0 ? percent + "%" : "?")
                + (state == org.lwjgl.sdl.SDLPower.SDL_POWERSTATE_CHARGING ? " ⚡" : "")
                + (state == org.lwjgl.sdl.SDLPower.SDL_POWERSTATE_CHARGED ? " (voll)" : "");
        Font font = Minecraft.getInstance().font;
        int pillW = font.width(text) + 12;
        int pillH = 15;
        int x = 8;
        graphics.fill(x, y, x + pillW, y + pillH, 0xCC111822);
        graphics.fill(x, y, x + pillW, y + 1, 0x9900E5FF);
        graphics.fill(x, y + pillH - 1, x + pillW, y + pillH, 0x9900E5FF);
        int col = (percent >= 0 && percent <= 15 && state == org.lwjgl.sdl.SDLPower.SDL_POWERSTATE_ON_BATTERY) ? 0xFFFF5555
                : (state == org.lwjgl.sdl.SDLPower.SDL_POWERSTATE_CHARGING ? 0xFF55FF55 : 0xFFFFFFFF);
        graphics.text(font, text, x + 6, y + 4, col);
    }

    /** Device (Steam Deck) battery pill; returns the y below it. */
    private static int renderDeviceBatteryPill(GuiGraphicsExtractor graphics) {

        BatteryInfo info = BatteryReader.getBatteryInfo();
        if (!info.isAvailable()) return 8;

        Minecraft client = Minecraft.getInstance();
        Font font = client.font;

        String hudText = info.toHudString();
        int textWidth = font.width(hudText);
        int padX = 6;
        int padY = 3;
        int pillW = textWidth + padX * 2;
        int pillH = 9 + padY * 2;

        // Position at top left (avoids overlapping the top-right '🎮 Controller' button)
        int x = 8;
        int y = 8;

        // Semi-transparent rounded background panel
        graphics.fill(x, y, x + pillW, y + pillH, 0xCC111822);
        // Subtle cyan top/bottom border
        graphics.fill(x, y, x + pillW, y + 1, 0x9900E5FF);
        graphics.fill(x, y + pillH - 1, x + pillW, y + pillH, 0x9900E5FF);
        graphics.fill(x, y, x + 1, y + pillH, 0x9900E5FF);
        graphics.fill(x + pillW - 1, y, x + pillW, y + pillH, 0x9900E5FF);

        int textCol = info.percent() <= 15 ? 0xFFFF5555 : (info.isCharging() ? 0xFF55FF55 : 0xFFFFFFFF);
        graphics.text(font, hudText, x + padX, y + padY + 1, textCol);
        return y + pillH + 4;
    }

    /**
     * Appends battery metrics to F3 debug screen.
     */
    public static void renderDebugOverlayHud(GuiGraphicsExtractor graphics, int screenWidth, int screenHeight) {
        Minecraft client = Minecraft.getInstance();
        Font font = client.font;

        boolean handheld = com.steamdeck.mc.platform.PlatformDetector.isHandheld();
        BatteryInfo info = handheld ? BatteryReader.getBatteryInfo() : BatteryInfo.UNAVAILABLE;
        String modeText = "Modus: " + com.steamdeck.mc.platform.PlatformDetector.describe();
        String hudText = info.isAvailable() ? (modeText + " • " + info.toHudString()) : modeText;

        if (ModConfig.getInstance().enableF3ControllerDebug) {
            String controllerTelemetry = com.steamdeck.mc.input.SteamDeckInputManager.getInstance().getDebugTelemetry();
            String gyroTelemetry = "Gyro: " + com.steamdeck.mc.input.gyro.GyroManager.getInstance().getActiveProviderName();
            String afkTelemetry;
            if (!handheld) {
                afkTelemetry = "Power: Desktop (Eco-Engine aus)";
            } else {
                afkTelemetry = "Power: " + (com.steamdeck.mc.power.PowerManager.getInstance().isAfkThrottled()
                        ? String.format("AFK (-%d%% FPS)", Math.round((1f - com.steamdeck.mc.power.PowerManager.getInstance().getAfkThrottleFactor()) * 100f))
                        : "Aktiv (100% FPS)");
            }

            int maxW = Math.max(font.width(hudText), Math.max(font.width(controllerTelemetry), Math.max(font.width(gyroTelemetry), font.width(afkTelemetry))));
            int dx = screenWidth - maxW - 8;
            int dy = screenHeight - 48;

            graphics.fill(dx - 4, dy - 4, screenWidth - 2, screenHeight - 2, 0xCC0D1117);
            graphics.fill(dx - 4, dy - 4, screenWidth - 2, dy - 3, 0xFF00E5FF);
            graphics.text(font, hudText, dx, dy, 0xFF00FFCC);
            graphics.text(font, controllerTelemetry, dx, dy + 11, 0xFFFFFFFF);
            graphics.text(font, gyroTelemetry, dx, dy + 22, 0xFF88CCFF);
            graphics.text(font, afkTelemetry, dx, dy + 33, 0xFFFFAA00);
            return;
        }

        if (!ModConfig.getInstance().enableBatteryHudInF3 || !info.isAvailable()) return;
        hudText = "Steam Deck " + info.toHudString();

        int textWidth = font.width(hudText);
        int x = screenWidth - textWidth - 4;
        int y = screenHeight - 12;

        graphics.fill(x - 2, y - 2, screenWidth - 2, y + 10, 0x90000000);
        graphics.text(font, hudText, x, y, 0xFF00FFCC);
    }

    /**
     * Renders a subtle SteamOS-style controller legend bar at the bottom-left of inventory/container screens.
     */
    public static void renderMenuControllerHints(GuiGraphicsExtractor graphics, Screen screen, int screenWidth, int screenHeight) {
        if (!ModConfig.getInstance().enableControllerMenuHints) return;
        if (VirtualKeyboard.getInstance().isOpen()) return;
        // Only useful while a controller is actually being used (not for mouse/keyboard players on desktop)
        if (!com.steamdeck.mc.input.SteamDeckInputManager.getInstance().isControllerRecentlyUsed()) return;
        if (!(screen instanceof AbstractContainerScreen)) return;

        Minecraft client = Minecraft.getInstance();
        if (client == null || client.font == null) return;
        Font font = client.font;

        ModConfig cfg = ModConfig.getInstance();
        ActionBinding bindA = cfg.getBinding(ActionSet.MENUS, DeckButton.A);
        ActionBinding bindX = cfg.getBinding(ActionSet.MENUS, DeckButton.X);
        ActionBinding bindY = cfg.getBinding(ActionSet.MENUS, DeckButton.Y);
        ActionBinding bindB = cfg.getBinding(ActionSet.MENUS, DeckButton.B);

        String extra = (screen instanceof net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen)
                ? "  •  [L1/R1] Tab" : "";
        String hint = String.format("[A] %s  •  [X] %s  •  [Y] %s  •  [B] %s  •  [D-Pad] Slot-Sprung%s",
                bindA.getDisplayLabel(ActionSet.MENUS), bindX.getDisplayLabel(ActionSet.MENUS), bindY.getDisplayLabel(ActionSet.MENUS), bindB.getDisplayLabel(ActionSet.MENUS), extra);

        int w = font.width(hint) + 10;
        int h = 13;
        int x = 6;
        int y = screenHeight - h - 4;

        graphics.fill(x, y, x + w, y + h, 0xBB0D1117);
        graphics.fill(x, y, x + w, y + 1, 0x6600E5FF);
        graphics.text(font, hint, x + 5, y + 3, 0xFF88CCFF);
    }
}
