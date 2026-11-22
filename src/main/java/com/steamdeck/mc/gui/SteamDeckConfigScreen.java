package com.steamdeck.mc.gui;

import com.steamdeck.mc.config.ModConfig;
import com.steamdeck.mc.input.ActionBinding;
import com.steamdeck.mc.input.ActionSet;
import com.steamdeck.mc.input.DeckAction;
import com.steamdeck.mc.input.DeckButton;
import com.steamdeck.mc.input.SteamDeckInputManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * Visual Controller Dashboard and Action Set configurator tailored for the Steam Deck.
 * Clean, non-overlapping responsive layout, categorized action modal with mouse click support,
 * custom keyboard key assignments, and full hardware bridge configuration.
 */
public class SteamDeckConfigScreen extends Screen {
    private final Screen parent;
    private int currentTab = 3; // 0=In-Game, 1=Menus (old binding tabs, now in ControllerLayoutScreen), 3=Eco/Akku, 4=Hardware/Bridge, 5=Trackpads
    /** Bindings are edited in the controller configurator; this screen only holds the further settings. */
    private static final int[] VISIBLE_TABS = {3, 4, 5};
    private static final int TAB_COUNT = VISIBLE_TABS.length;

    private static int tabPosition(int tab) {
        for (int i = 0; i < VISIBLE_TABS.length; i++) if (VISIBLE_TABS[i] == tab) return i;
        return 0;
    }

    // Bridge Key configuration state
    private DeckButton listeningForBridgeKey = null;
    private String layoutInstallStatus = null;
    private long presetConfirmUntil = 0L;
    private long presetAppliedTime = 0L;
    private long layoutInstallStatusTime = 0L;

    public SteamDeckConfigScreen(Screen parent) {
        super(Component.literal("Steam Deck Controller"));
        this.parent = parent;
    }

    public boolean isListening() {
        return this.listeningForBridgeKey != null;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float tickDelta) {
        // Dark metallic SteamOS background
        graphics.fill(0, 0, this.width, this.height, 0xF00D1117);

        Font font = this.font;

        // Top Header Bar
        graphics.fill(0, 0, this.width, 24, 0xFF161B22);
        graphics.fill(0, 23, this.width, 24, 0xFF00E5FF);
        graphics.text(font, "🎮 Steam Deck Essentials", 8, 8, 0xFF00E5FF);

        // Status text on right side of top bar (never collides with content)
        String statusText = String.format("Aktiv: %s • %s",
                SteamDeckInputManager.getInstance().getCurrentActionSet().getTitle(),
                SteamDeckInputManager.getInstance().getActiveDeviceName());
        int statusW = font.width(statusText);
        graphics.text(font, statusText, this.width - statusW - 8, 8, 0xFF88CCFF);

        // Tab Navigation Bar
        int tabCount = TAB_COUNT;
        int tabW = Math.min(95, (this.width - 24) / tabCount);
        int tabH = 17;
        int tabY = 27;
        int tabStartX = (this.width - tabW * tabCount - (tabCount - 1) * 3) / 2;

        String[] tabLabels = {"🎮 Spiel", "📦 Menüs", "", "⚡ Eco", "⚙️ Bridge", "🖐 Pads"};
        for (int p = 0; p < tabCount; p++) {
            int i = VISIBLE_TABS[p];
            int tx = tabStartX + p * (tabW + 3);
            boolean active = (i == this.currentTab);
            boolean hover = mouseX >= tx && mouseX <= tx + tabW && mouseY >= tabY && mouseY <= tabY + tabH;

            int bg = active ? 0xFF1E3A4A : (hover ? 0xFF2A3D52 : 0xAA161B22);
            int border = active ? 0xFF00E5FF : 0x4400E5FF;
            int textCol = active ? 0xFF00FFCC : (hover ? -1 : 0xFFAAAAAA);

            graphics.fill(tx, tabY, tx + tabW, tabY + tabH, bg);
            graphics.fill(tx, tabY, tx + tabW, tabY + 1, border);
            graphics.fill(tx, tabY + tabH - 1, tx + tabW, tabY + tabH, border);
            graphics.fill(tx, tabY, tx + 1, tabY + tabH, border);
            graphics.fill(tx + tabW - 1, tabY, tx + tabW, tabY + tabH, border);

            graphics.centeredText(font, tabLabels[i], tx + tabW / 2, tabY + (tabH - 7) / 2, textCol);
        }

        // Main Content Area
        if (this.currentTab == 3) {
            renderEcoTab(graphics, font, mouseX, mouseY);
        } else if (this.currentTab == 4) {
            renderBridgeAndHardwareTab(graphics, font, mouseX, mouseY);
        } else {
            renderTrackpadTab(graphics, font, mouseX, mouseY);
        }

        // Bottom Bar (Done & Reset)
        int btnW = 100;
        int btnH = 19;
        int btnY = this.height - 22;

        // Reset Button
        int resetX = 10;
        boolean resetHover = mouseX >= resetX && mouseX <= resetX + 110 && mouseY >= btnY && mouseY <= btnY + btnH;
        graphics.fill(resetX, btnY, resetX + 110, btnY + btnH, resetHover ? 0xFF442222 : 0xFF221111);
        graphics.centeredText(font, "Standard laden", resetX + 55, btnY + 6, resetHover ? 0xFFFF7777 : 0xFFCC5555);

        // Preset Button (applies the whole workshop layout: bindings of all sets + pad/stick settings)
        {
            int presetW = Math.min(170, this.width - 2 * 120 - 20);
            int presetX = (this.width - presetW) / 2;
            boolean presetHover = mouseX >= presetX && mouseX <= presetX + presetW && mouseY >= btnY && mouseY <= btnY + btnH;
            boolean confirming = System.currentTimeMillis() < this.presetConfirmUntil;
            String presetLabel = System.currentTimeMillis() - this.presetAppliedTime < 3000L ? "✓ Workshop-Profil aktiv"
                    : (confirming ? "Nochmal: Workshop-Profil?" : "📋 Workshop-Profil laden");
            graphics.fill(presetX, btnY, presetX + presetW, btnY + btnH, presetHover ? 0xFF2A3D52 : (confirming ? 0xFF4A3A10 : 0xFF1E2632));
            graphics.centeredText(font, font.plainSubstrByWidth(presetLabel, presetW - 4), presetX + presetW / 2, btnY + 6,
                    confirming ? 0xFFFFCC55 : 0xFF88CCFF);
        }

        // Done Button
        int doneX = this.width - btnW - 10;
        boolean doneHover = mouseX >= doneX && mouseX <= doneX + btnW && mouseY >= btnY && mouseY <= btnY + btnH;
        graphics.fill(doneX, btnY, doneX + btnW, btnY + btnH, doneHover ? 0xFF104A7B : 0xFF1E3A4A);
        graphics.fill(doneX, btnY, doneX + btnW, btnY + 1, 0xFF00E5FF);
        graphics.fill(doneX, btnY + btnH - 1, doneX + btnW, btnY + btnH, 0xFF00E5FF);
        graphics.fill(doneX, btnY, doneX + 1, btnY + btnH, 0xFF00E5FF);
        graphics.fill(doneX + btnW - 1, btnY, doneX + btnW, btnY + btnH, 0xFF00E5FF);
        graphics.centeredText(font, "Fertig", doneX + btnW / 2, btnY + 6, doneHover ? 0xFF00FFCC : -1);
    }

    private void renderEcoTab(GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY) {
        ModConfig config = ModConfig.getInstance();
        int y = 46;
        int x = (this.width - 340) / 2;
        int rowW = 340;
        int rowH = 16;

        // Device mode (Auto / Handheld / Desktop)
        boolean modeHover = mouseX >= x && mouseX <= x + rowW && mouseY >= y && mouseY <= y + rowH;
        graphics.fill(x, y, x + rowW, y + rowH, modeHover ? 0xFF2A3D52 : 0xFF1E3A4A);
        graphics.outline(x, y, rowW, rowH, modeHover ? 0xFF00FFCC : 0xFF00E5FF);
        String modeLabel = "Gerätemodus: " + config.getDeviceMode().getLabel()
                + "  →  " + com.steamdeck.mc.platform.PlatformDetector.describe();
        graphics.text(font, font.plainSubstrByWidth(modeLabel, rowW - 16), x + 8, y + (rowH - 7) / 2, 0xFF00FFCC);
        y += rowH + 3;
        int ecoRowsTop = y;

        renderToggleOption(graphics, font, x, y, rowW, rowH, "AFK Drosselung (Akku -80% / Netz -50%)", config.enableDynamicAfkThrottle, mouseX, mouseY);
        y += rowH + 3;
        renderToggleOption(graphics, font, x, y, rowW, rowH, "AFK Display-Dimming (Akku 65% / Netz 30%)", config.enableAfkScreenDimming, mouseX, mouseY);
        y += rowH + 3;
        renderToggleOption(graphics, font, x, y, rowW, rowH, "Chunk-Threads Steuerung (Akku 2 / Netz 3)", config.enableDynamicChunkThreading, mouseX, mouseY);
        y += rowH + 3;
        renderToggleOption(graphics, font, x, y, rowW, rowH, "Lüfter-Leisemodus (90Hz Thermal-Cap)", config.enableThermalFanGuard, mouseX, mouseY);
        y += rowH + 3;
        renderToggleOption(graphics, font, x, y, rowW, rowH, "Hotbar Haptik (L/R Trackpad-Impuls)", config.enableHotbarHaptics, mouseX, mouseY);
        y += rowH + 3;
        renderToggleOption(graphics, font, x, y, rowW, rowH, "Performance CSV-Tracker (Debug-Log)", config.enablePerformanceTracking, mouseX, mouseY);
        y += rowH + 3;
        renderToggleOption(graphics, font, x, y, rowW, rowH, "F3 Telemetrie-HUD", config.enableF3ControllerDebug, mouseX, mouseY);
        y += rowH + 3;
        renderToggleOption(graphics, font, x, y, rowW, rowH, "Trackpad-Kamera ohne Steam (nativ)", config.nativeTrackpadCamera, mouseX, mouseY);
        y += rowH + 3;
        renderToggleOption(graphics, font, x, y, rowW, rowH, "Controller-Hinweis (Verbinden, Trennen, Akku)", config.showControllerToasts, mouseX, mouseY);
        y += rowH + 3;

        if (!com.steamdeck.mc.platform.PlatformDetector.isHandheld()) {
            // Dim the handheld-only power options (AFK, dimming, chunk threads, thermal cap)
            graphics.fill(x, ecoRowsTop, x + rowW, ecoRowsTop + 4 * (rowH + 3) - 3, 0x99000000);
            graphics.centeredText(font, "Desktop-Modus: Akku-, FPS- & Eco-Funktionen sind inaktiv",
                    x + rowW / 2, y + 2, 0xFFFFAA00);
        }
    }

    // ---------------------------------------------------------------------------------------
    // Tab 5: Trackpads (click pressure, live meter, haptic intensity) - Steam Input parity
    // ---------------------------------------------------------------------------------------
    private static final int TRACKPAD_ROWS = 13;
    private static final int PAD_STEP = 17;
    private static final int PAD_ROW_H = 15;
    private static final String[] HAPTIC_LEVEL_NAMES = {"Aus", "Sehr schwach", "Schwach", "Normal", "Stark", "Sehr stark"};

    private int trackpadRowAt(double mouseX, double mouseY) {
        int x = (this.width - 340) / 2;
        if (mouseX < x || mouseX > x + 340 || mouseY < 46) return -1;
        int row = (int) ((mouseY - 46) / PAD_STEP);
        double inRow = (mouseY - 46) - row * PAD_STEP;
        return (row < TRACKPAD_ROWS && inRow <= PAD_ROW_H) ? row : -1;
    }

    /** 0.5 % steps up to 20 % (real presses are 5-15 %), 5 % steps above. */
    private static float stepPressure(float value, int delta) {
        float step = (value < 0.20f || (value <= 0.201f && delta < 0)) ? 0.005f : 0.05f;
        float next = Math.round((value + delta * step) * 200f) / 200f;
        return Math.max(0.01f, Math.min(1.0f, next));
    }

    private static String toggleClickMode(String mode) {
        return "PRESSURE".equals(mode) ? "HARDWARE" : "PRESSURE";
    }

    /** Applies a click (delta = 0) or -/+ (delta = -1/+1) on a trackpad-tab row. */
    private boolean adjustTrackpadSetting(int row, int delta) {
        ModConfig c = ModConfig.getInstance();
        if (SteamDeckInputManager.getInstance().getActiveKind().isDualSense()) {
            return adjustDualSenseTrackpadSetting(c, row, delta);
        }
        switch (row) {
            case 0 -> c.leftTrackpadClickMode = toggleClickMode(c.leftTrackpadClickMode);
            case 1 -> {
                if (delta == 0) return false;
                c.leftTrackpadClickPressure = stepPressure(c.leftTrackpadClickPressure, delta);
            }
            case 3 -> c.rightTrackpadClickMode = toggleClickMode(c.rightTrackpadClickMode);
            case 4 -> {
                if (delta == 0) return false;
                c.rightTrackpadClickPressure = stepPressure(c.rightTrackpadClickPressure, delta);
            }
            case 6 -> {
                if (delta == 0) return false;
                c.leftTrackpadHapticLevel = Math.max(0, Math.min(5, c.leftTrackpadHapticLevel + delta));
                com.steamdeck.mc.power.HapticFeedbackManager.getInstance().triggerPadHaptic(false, true, (short) 38000, 30);
            }
            case 7 -> {
                if (delta == 0) return false;
                c.rightTrackpadHapticLevel = Math.max(0, Math.min(5, c.rightTrackpadHapticLevel + delta));
                com.steamdeck.mc.power.HapticFeedbackManager.getInstance().triggerPadHaptic(true, true, (short) 38000, 30);
            }
            case 8 -> c.enableTrackpadClickHaptics = !c.enableTrackpadClickHaptics;
            case 9 -> c.leftTrackpadMode = switch (c.leftTrackpadMode) {
                case "BUTTON" -> "DPAD_MOVE";
                case "DPAD_MOVE" -> "GRID_3X3";
                default -> "BUTTON";
            };
            case 10 -> c.sprintOnForward = !c.sprintOnForward;
            default -> {
                return false;
            }
        }
        c.save();
        return true;
    }

    private boolean adjustDualSenseTrackpadSetting(ModConfig c, int row, int delta) {
        switch (row) {
            case 0 -> c.centerTrackpadMode = switch (c.centerTrackpadMode) {
                case "CAMERA" -> "BUTTON";
                case "BUTTON" -> "OFF";
                default -> "CAMERA";
            };
            case 1 -> c.centerTrackpadMenuMouse = !c.centerTrackpadMenuMouse;
            case 2 -> {
                if (delta == 0) return false;
                c.leftTriggerThreshold = Math.max(0.05f, Math.min(0.95f, Math.round((c.leftTriggerThreshold + delta * 0.05f) * 20f) / 20f));
            }
            case 4 -> {
                if (delta == 0) return false;
                c.rightTriggerThreshold = Math.max(0.05f, Math.min(0.95f, Math.round((c.rightTriggerThreshold + delta * 0.05f) * 20f) / 20f));
            }
            case 6 -> c.dualSenseTriggerMode = switch (c.dualSenseTriggerMode) {
                case "WEAPON_CLICK" -> "RESISTANCE";
                case "RESISTANCE" -> "OFF";
                default -> "WEAPON_CLICK";
            };
            case 7 -> {
                if (delta == 0) return false;
                c.dualSenseTriggerResistance = Math.max(0.10f, Math.min(1.0f, Math.round((c.dualSenseTriggerResistance + delta * 0.05f) * 20f) / 20f));
            }
            case 8 -> c.adaptiveTriggerBlockHardness = !c.adaptiveTriggerBlockHardness;
            case 9 -> c.adaptiveTriggerBowTension = !c.adaptiveTriggerBowTension;
            case 10 -> c.adaptiveTriggerShield = !c.adaptiveTriggerShield;
            case 11 -> c.adaptiveTriggerLowDurability = !c.adaptiveTriggerLowDurability;
            case 12 -> c.enableTrackpadClickHaptics = !c.enableTrackpadClickHaptics;
            default -> {
                return false;
            }
        }
        c.save();
        return true;
    }

    private void renderPadValueRow(GuiGraphicsExtractor graphics, Font font, int x, int y, int rowW, String label,
                                   int color, int mouseX, int mouseY) {
        graphics.fill(x, y, x + rowW, y + PAD_ROW_H, 0xAA161B22);
        graphics.text(font, label, x + 8, y + (PAD_ROW_H - 7) / 2, color);
        renderSmallButton(graphics, font, x + rowW - 60, y + 1, 26, PAD_ROW_H - 2, "-", mouseX, mouseY);
        renderSmallButton(graphics, font, x + rowW - 30, y + 1, 26, PAD_ROW_H - 2, "+", mouseX, mouseY);
    }

    private void renderPadModeRow(GuiGraphicsExtractor graphics, Font font, int x, int y, int rowW, int row,
                                  String label, int mouseX, int mouseY) {
        boolean hover = trackpadRowAt(mouseX, mouseY) == row;
        graphics.fill(x, y, x + rowW, y + PAD_ROW_H, hover ? 0xFF2A3D52 : 0xFF1E3A4A);
        graphics.text(font, label, x + 8, y + (PAD_ROW_H - 7) / 2, 0xFF00FFCC);
    }

    private void renderTrackpadTab(GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY) {
        ModConfig c = ModConfig.getInstance();
        SteamDeckInputManager input = SteamDeckInputManager.getInstance();
        int x = (this.width - 340) / 2;
        int rowW = 340;
        int y = 46;

        if (input.getActiveKind().isDualSense()) {
            String padModeLabel = switch (c.centerTrackpadMode) {
                case "CAMERA" -> "Kamera";
                case "BUTTON" -> "Nur Taste";
                default -> "Aus";
            };
            renderPadModeRow(graphics, font, x, y, rowW, 0, "Zentral-Touchpad (Im Spiel): " + padModeLabel, mouseX, mouseY);
            y += PAD_STEP;
            renderToggleOption(graphics, font, x, y, rowW, PAD_ROW_H, "Touchpad als Maus in Menüs", c.centerTrackpadMenuMouse, mouseX, mouseY);
            y += PAD_STEP;

            renderPadValueRow(graphics, font, x, y, rowW,
                    String.format(java.util.Locale.ROOT, "L2-Trigger Schwelle: %.0f%%", c.leftTriggerThreshold * 100f),
                    -1, mouseX, mouseY);
            y += PAD_STEP;
            renderPressureMeter(graphics, font, x, y, rowW, PAD_ROW_H, input.getTriggerValue(0), c.leftTriggerThreshold,
                    input.getTriggerValue(0) >= c.leftTriggerThreshold);
            y += PAD_STEP;

            renderPadValueRow(graphics, font, x, y, rowW,
                    String.format(java.util.Locale.ROOT, "R2-Trigger Schwelle: %.0f%%", c.rightTriggerThreshold * 100f),
                    -1, mouseX, mouseY);
            y += PAD_STEP;
            renderPressureMeter(graphics, font, x, y, rowW, PAD_ROW_H, input.getTriggerValue(1), c.rightTriggerThreshold,
                    input.getTriggerValue(1) >= c.rightTriggerThreshold);
            y += PAD_STEP;

            String dsModeLabel = switch (c.dualSenseTriggerMode) {
                case "RESISTANCE" -> "Dauer-Widerstand";
                case "OFF" -> "Aus";
                default -> "Klickpunkt";
            };
            renderPadModeRow(graphics, font, x, y, rowW, 6, "Basis-Trigger (L2/R2): " + dsModeLabel, mouseX, mouseY);
            y += PAD_STEP;
            renderPadValueRow(graphics, font, x, y, rowW,
                    String.format(java.util.Locale.ROOT, "Trigger-Widerstand: %.0f%%", c.dualSenseTriggerResistance * 100f),
                    -1, mouseX, mouseY);
            y += PAD_STEP;
            renderToggleOption(graphics, font, x, y, rowW, PAD_ROW_H, "Block-Härte beim Abbauen (R2)", c.adaptiveTriggerBlockHardness, mouseX, mouseY);
            y += PAD_STEP;
            renderToggleOption(graphics, font, x, y, rowW, PAD_ROW_H, "Bogen / Armbrust / Dreizack Spannung (R2)", c.adaptiveTriggerBowTension, mouseX, mouseY);
            y += PAD_STEP;
            renderToggleOption(graphics, font, x, y, rowW, PAD_ROW_H, "Schild-Block Widerstand (L2)", c.adaptiveTriggerShield, mouseX, mouseY);
            y += PAD_STEP;
            renderToggleOption(graphics, font, x, y, rowW, PAD_ROW_H, "Werkzeug-Warnung (< 10% Haltbarkeit, R2)", c.adaptiveTriggerLowDurability, mouseX, mouseY);
            y += PAD_STEP;
            renderToggleOption(graphics, font, x, y, rowW, PAD_ROW_H, "Klick-Haptik (Touchpad)", c.enableTrackpadClickHaptics, mouseX, mouseY);
            y += PAD_STEP;
            graphics.centeredText(font, font.plainSubstrByWidth("L2/R2 ziehen: die Balken zeigen die aktuelle Druckintensität live.", rowW),
                    x + rowW / 2, y + 1, 0xFF88CCFF);
            return;
        }

        for (int side = 0; side < 2; side++) {
            String mode = side == 0 ? c.leftTrackpadClickMode : c.rightTrackpadClickMode;
            boolean pressureMode = "PRESSURE".equals(mode);
            float threshold = side == 0 ? c.leftTrackpadClickPressure : c.rightTrackpadClickPressure;
            String padName = side == 0 ? "Linkes Pad" : "Rechtes Pad";

            renderPadModeRow(graphics, font, x, y, rowW, side * 3,
                    padName + " – Klick: " + (pressureMode ? "Druckschwelle (Soft Press)" : "Hardware-Klick"), mouseX, mouseY);
            y += PAD_STEP;
            renderPadValueRow(graphics, font, x, y, rowW, padName + " – Klickdruck: " + String.format(java.util.Locale.ROOT, "%.1f%%", threshold * 100) + (Math.abs(threshold - 0.125f) < 0.001f ? " (Steam)" : ""),
                    pressureMode ? -1 : 0xFF777777, mouseX, mouseY);
            y += PAD_STEP;
            renderPressureMeter(graphics, font, x, y, rowW, PAD_ROW_H, input.getTrackpadPressure(side), threshold,
                    pressureMode && input.isTrackpadPressureClicked(side));
            y += PAD_STEP;
        }

        for (int side = 0; side < 2; side++) {
            int level = side == 0 ? c.leftTrackpadHapticLevel : c.rightTrackpadHapticLevel;
            renderPadValueRow(graphics, font, x, y, rowW, "Haptik " + (side == 0 ? "links" : "rechts") + ": " + HAPTIC_LEVEL_NAMES[level],
                    -1, mouseX, mouseY);
            y += PAD_STEP;
        }

        renderToggleOption(graphics, font, x, y, rowW, PAD_ROW_H, "Klick-Haptik (Drücken & Loslassen)", c.enableTrackpadClickHaptics, mouseX, mouseY);
        y += PAD_STEP;
        renderPadModeRow(graphics, font, x, y, rowW, 9, "Linkes Pad: "
                + ("DPAD_MOVE".equals(c.leftTrackpadMode) ? "D-Pad zum Laufen (mit Klick)"
                        : ("GRID_3X3".equals(c.leftTrackpadMode) ? "Raster 3×3 (9 Felder)" : "Taste (Trackpad L)")), mouseX, mouseY);
        y += PAD_STEP;
        renderToggleOption(graphics, font, x, y, rowW, PAD_ROW_H, "Vorwärts = Sprinten (Stick & Pad)", c.sprintOnForward, mouseX, mouseY);
        y += PAD_STEP;

        String info;
        if (!input.getActiveKind().isNativeValve()) {
            info = "Nur für native Valve-Controller (ohne Steam Input).";
        } else if (!com.steamdeck.mc.power.TritonTrackpadHaptics.getInstance().isAvailable()) {
            info = "Pad-Haptik: Rumble-Fallback (kein Steam Controller 2026)";
        } else {
            info = "Finger aufs Pad legen und drücken: der Balken zeigt den Druck live.";
        }
        graphics.centeredText(font, font.plainSubstrByWidth(info, rowW), x + rowW / 2, y + 1, 0xFF88CCFF);
    }

    private void renderPressureMeter(GuiGraphicsExtractor graphics, Font font, int x, int y, int w, int h,
                                     float pressure, float threshold, boolean clicked) {
        int barX = x + 8;
        int barW = w - 16;
        graphics.fill(x, y, x + w, y + h, 0x66161B22);
        graphics.fill(barX, y + 4, barX + barW, y + h - 4, 0xFF0D1117);
        if (pressure >= 0.0f) {
            int fillW = Math.round(barW * pressure);
            int col = clicked ? 0xFF00FF88 : (pressure >= threshold ? 0xFFFFAA00 : 0xFF00A5CC);
            graphics.fill(barX, y + 4, barX + fillW, y + h - 4, col);
            String val = String.format(java.util.Locale.ROOT, "%.1f%%", pressure * 100f);
            graphics.text(font, val, barX + barW - font.width(val) - 2, y + 4, 0xFFFFFFFF);
        } else {
            graphics.centeredText(font, "kein Finger", x + w / 2, y + 4, 0xFF555555);
        }
        int tx = barX + Math.round(barW * threshold);
        graphics.fill(tx - 1, y + 1, tx + 1, y + h - 1, 0xFFFF5555); // threshold marker
    }

    private void renderBridgeAndHardwareTab(GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY) {
        ModConfig config = ModConfig.getInstance();
        int y = 46;
        int x = (this.width - 340) / 2;
        int rowW = 340;
        int rowH = 16;

        renderToggleOption(graphics, font, x, y, rowW, rowH, "Rechter Stick Radial-Menü (R3 wechselt)", config.enableRightStickRadialMenu, mouseX, mouseY);
        y += rowH + 2;
        renderToggleOption(graphics, font, x, y, rowW, rowH, "Gyro: " + (config.enableGyroAiming
                ? com.steamdeck.mc.input.gyro.GyroManager.activationLabel(config.gyroActivation) + String.format(" (%.1fx)", config.gyroSensitivityX)
                : "Aus"), config.enableGyroAiming, mouseX, mouseY);
        y += rowH + 2;
        renderToggleOption(graphics, font, x, y, rowW, rowH, "8-Wege Stick Anticheat-Mapping", config.enable8WayStickMapping, mouseX, mouseY);
        y += rowH + 2;

        // Stick Deadzone
        graphics.fill(x, y, x + rowW, y + rowH, 0xAA161B22);
        graphics.text(font, "Stick Deadzone: " + String.format(java.util.Locale.ROOT, "%.1f%%",
                SteamDeckInputManager.getInstance().getStickDeadzone() * 100) + (config.stickDeadzoneAuto ? " (Auto wie Steam)" : ""), x + 8, y + 4, -1);
        renderSmallButton(graphics, font, x + rowW - 60, y + 1, 26, 14, "-", mouseX, mouseY);
        renderSmallButton(graphics, font, x + rowW - 30, y + 1, 26, 14, "+", mouseX, mouseY);
        y += rowH + 2;

        // Trackpad Sensitivity
        graphics.fill(x, y, x + rowW, y + rowH, 0xAA161B22);
        graphics.text(font, "Trackpad Sensitivität: " + Math.round(config.trackpadSensitivity * 100) + "%", x + 8, y + 4, -1);
        renderSmallButton(graphics, font, x + rowW - 60, y + 1, 26, 14, "-", mouseX, mouseY);
        renderSmallButton(graphics, font, x + rowW - 30, y + 1, 26, 14, "+", mouseX, mouseY);
        y += rowH + 3;

        // Header for Steam Input Bridge
        graphics.fill(x, y, x + rowW, y + 12, 0xFF161B22);
        graphics.fill(x, y + 11, x + rowW, y + 12, 0xFF00E5FF);
        boolean bridgeActive = SteamDeckInputManager.getInstance().isSteamInputBridgeActive();
        graphics.centeredText(font, bridgeActive ? "Steam Input Bridge: Rücktasten & Stick Touch"
                        : "Steam Input Bridge: inaktiv (nativer Controller / Desktop – nicht nötig)",
                x + rowW / 2, y + 2, bridgeActive ? 0xFF00E5FF : 0xFF777777);
        y += 14;

        // Paddles & Stick Touch in 2 columns (Left / Right)
        int halfW = (rowW - 6) / 2;
        int rx = x + halfW + 6;

        renderBridgeKeyRow(graphics, font, x, y, halfW, rowH, DeckButton.L4, config.steamBridgeKeyL4, mouseX, mouseY);
        renderBridgeKeyRow(graphics, font, rx, y, halfW, rowH, DeckButton.R4, config.steamBridgeKeyR4, mouseX, mouseY);
        y += rowH + 2;

        renderBridgeKeyRow(graphics, font, x, y, halfW, rowH, DeckButton.L5, config.steamBridgeKeyL5, mouseX, mouseY);
        renderBridgeKeyRow(graphics, font, rx, y, halfW, rowH, DeckButton.R5, config.steamBridgeKeyR5, mouseX, mouseY);
        y += rowH + 2;

        renderBridgeKeyRow(graphics, font, x, y, halfW, rowH, DeckButton.LEFT_STICK_TOUCH, config.steamBridgeKeyL3Touch, mouseX, mouseY);
        renderBridgeKeyRow(graphics, font, rx, y, halfW, rowH, DeckButton.RIGHT_STICK_TOUCH, config.steamBridgeKeyR3Touch, mouseX, mouseY);
        y += rowH + 4;

        // 1-Click Steam Input Layout Installer Button
        boolean installed = com.steamdeck.mc.input.SteamLayoutInstaller.isLayoutInstalled();
        boolean btnHover = mouseX >= x && mouseX <= x + rowW && mouseY >= y && mouseY <= y + 16;
        int btnBg = btnHover ? 0xFF104A7B : (installed ? 0xFF0E3B28 : 0xFF1E3A4A);
        int btnBorder = btnHover ? 0xFF00FFCC : (installed ? 0xFF00FF88 : 0xFF00E5FF);
        graphics.fill(x, y, x + rowW, y + 16, btnBg);
        graphics.outline(x, y, rowW, 16, btnBorder);

        String btnLabel;
        if (this.layoutInstallStatus != null && (System.currentTimeMillis() - this.layoutInstallStatusTime) < 4000L) {
            btnLabel = this.layoutInstallStatus;
        } else {
            btnLabel = installed
                    ? "📥 Steam-Input Vorlage aktualisieren (✓ Vorlage installiert)"
                    : "📥 Steam-Input Vorlage jetzt automatisch installieren";
        }
        graphics.centeredText(font, btnLabel, x + rowW / 2, y + 4, btnHover ? 0xFF00FFCC : 0xFFFFFFFF);
    }

    private void renderBridgeKeyRow(GuiGraphicsExtractor graphics, Font font, int x, int y, int w, int h,
                                    DeckButton btn, int keyCode, int mouseX, int mouseY) {
        graphics.fill(x, y, x + w, y + h, 0xAA161B22);
        String name = btn.getDisplayName();
        graphics.text(font, name + ":", x + 4, y + (h - 7) / 2, -1);

        int pillW = 68;
        int pillH = h - 4;
        int pillX = x + w - pillW - 3;
        int pillY = y + 2;

        boolean listening = (this.listeningForBridgeKey == btn);
        boolean hover = mouseX >= pillX && mouseX <= pillX + pillW && mouseY >= pillY && mouseY <= pillY + pillH;

        int bg = listening ? 0xFF4A3E10 : (hover ? 0xFF2A3D52 : 0xFF1E2632);
        int border = listening ? 0xFFFFCC00 : (hover ? 0xFF00FFCC : 0x4400E5FF);
        int textCol = listening ? 0xFFFFCC00 : (hover ? 0xFF00FFCC : -1);

        graphics.fill(pillX, pillY, pillX + pillW, pillY + pillH, bg);
        graphics.fill(pillX, pillY, pillX + pillW, pillY + 1, border);
        graphics.fill(pillX, pillY + pillH - 1, pillX + pillW, pillY + pillH, border);
        graphics.fill(pillX, pillY, pillX + 1, pillY + pillH, border);
        graphics.fill(pillX + pillW - 1, pillY, pillX + pillW, pillY + pillH, border);

        String label = listening ? ">> TASTE <<" : (keyCode <= 0 ? "Keine" : ActionBinding.getKeyLabel(keyCode));
        graphics.centeredText(font, label, pillX + pillW / 2, pillY + (pillH - 7) / 2, textCol);
    }

    private void renderToggleOption(GuiGraphicsExtractor graphics, Font font, int x, int y, int w, int h,
                                    String label, boolean enabled, int mouseX, int mouseY) {
        graphics.fill(x, y, x + w, y + h, 0xAA161B22);
        graphics.text(font, label, x + 8, y + (h - 7) / 2, -1);

        int pillW = 60;
        int pillH = h - 4;
        int pillX = x + w - pillW - 4;
        int pillY = y + 2;

        int bg = enabled ? 0xFF0E5A35 : 0xFF5A1E1E;
        int text = enabled ? 0xFF00FF88 : 0xFFFF5555;
        graphics.fill(pillX, pillY, pillX + pillW, pillY + pillH, bg);
        graphics.centeredText(font, enabled ? "AN" : "AUS", pillX + pillW / 2, pillY + (pillH - 7) / 2, text);
    }

    private void renderSmallButton(GuiGraphicsExtractor graphics, Font font, int x, int y, int w, int h,
                                   String text, int mouseX, int mouseY) {
        boolean hover = mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h;
        graphics.fill(x, y, x + w, y + h, hover ? 0xFF2A3D52 : 0xFF1E2632);
        graphics.centeredText(font, text, x + w / 2, y + (h - 7) / 2, hover ? 0xFF00FFCC : -1);
    }


    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mouseX = event.x();
        double mouseY = event.y();

        // 2. Tab Bar Clicks (5 Tabs)
        int tabCount = TAB_COUNT;
        int tabW = Math.min(95, (this.width - 24) / tabCount);
        int tabH = 17;
        int tabY = 27;
        int tabStartX = (this.width - tabW * tabCount - (tabCount - 1) * 3) / 2;
        for (int p = 0; p < tabCount; p++) {
            int tx = tabStartX + p * (tabW + 3);
            if (mouseX >= tx && mouseX <= tx + tabW && mouseY >= tabY && mouseY <= tabY + tabH) {
                this.currentTab = VISIBLE_TABS[p];
                this.listeningForBridgeKey = null;
                VirtualKeyboard.playClickSound();
                return true;
            }
        }

        // 3. Bottom Bar Clicks
        int btnW = 100;
        int btnH = 19;
        int btnY = this.height - 22;
        if (mouseX >= 10 && mouseX <= 120 && mouseY >= btnY && mouseY <= btnY + btnH) {
            // Reset to defaults
            if (this.currentTab == 3) {
                ModConfig cfg = ModConfig.getInstance();
                cfg.enableDynamicAfkThrottle = true;
                cfg.enableAfkScreenDimming = true;
                cfg.enableDynamicChunkThreading = true;
                cfg.enableThermalFanGuard = true;
                cfg.enableHotbarHaptics = true;
                cfg.enablePerformanceTracking = true;
                cfg.enableF3ControllerDebug = true;
                cfg.nativeTrackpadCamera = true;
                cfg.showControllerToasts = true;
                cfg.save();
                VirtualKeyboard.playClickSound();
            } else if (this.currentTab == 5) {
                ModConfig cfg = ModConfig.getInstance();
                cfg.leftTrackpadClickMode = "PRESSURE";
                cfg.rightTrackpadClickMode = "PRESSURE";
                cfg.leftTrackpadMode = "BUTTON";
                cfg.sprintOnForward = false;
                cfg.leftTrackpadClickPressure = 0.125f;
                cfg.rightTrackpadClickPressure = 0.125f;
                cfg.leftTrackpadHapticLevel = 3;
                cfg.rightTrackpadHapticLevel = 3;
                cfg.enableTrackpadClickHaptics = true;
                cfg.save();
                VirtualKeyboard.playClickSound();
            } else if (this.currentTab == 4) {
                ModConfig cfg = ModConfig.getInstance();
                cfg.enableRightStickRadialMenu = true;
                cfg.enableGyroAiming = true;
                cfg.enable8WayStickMapping = true;
                cfg.stickDeadzone = 0.25f;
                cfg.stickDeadzoneAuto = true;
                cfg.trackpadSensitivity = 1.0f;
                cfg.steamBridgeKeyL4 = 47; // '[' (SDL3 Scancode 47)
                cfg.steamBridgeKeyR4 = 48; // ']' (SDL3 Scancode 48)
                cfg.steamBridgeKeyL5 = 49; // '\' (SDL3 Scancode 49)
                cfg.steamBridgeKeyR5 = 56; // '/' (SDL3 Scancode 56)
                cfg.steamBridgeKeyL3Touch = 104; // F13
                cfg.steamBridgeKeyR3Touch = 105; // F14
                cfg.save();
                VirtualKeyboard.playClickSound();
            }
            return true;
        }
        {
            int presetW = Math.min(170, this.width - 2 * 120 - 20);
            int presetX = (this.width - presetW) / 2;
            if (mouseX >= presetX && mouseX <= presetX + presetW && mouseY >= btnY && mouseY <= btnY + btnH) {
                long now = System.currentTimeMillis();
                if (now < this.presetConfirmUntil) {
                    com.steamdeck.mc.input.ControllerPresets.applyWorkshopMinecraft(ModConfig.getInstance());
                    this.presetConfirmUntil = 0L;
                    this.presetAppliedTime = now;
                } else {
                    this.presetConfirmUntil = now + 3000L;
                }
                VirtualKeyboard.playClickSound();
                return true;
            }
        }
        int doneX = this.width - btnW - 10;
        if (mouseX >= doneX && mouseX <= doneX + btnW && mouseY >= btnY && mouseY <= btnY + btnH) {
            this.onClose();
            return true;
        }

        // 4. Tab content clicks
        if (this.currentTab == 5) {
            int row = trackpadRowAt(mouseX, mouseY);
            if (row >= 0) {
                int x = (this.width - 340) / 2;
                int rowW = 340;
                int delta = 0;
                if (mouseX >= x + rowW - 60 && mouseX <= x + rowW - 34) delta = -1;
                else if (mouseX >= x + rowW - 30 && mouseX <= x + rowW - 4) delta = 1;
                if (adjustTrackpadSetting(row, delta)) {
                    VirtualKeyboard.playClickSound();
                    return true;
                }
            }
        } else if (this.currentTab == 3) {
            // Tab 3: Eco & Akku toggles
            ModConfig config = ModConfig.getInstance();
            int y = 46;
            int x = (this.width - 340) / 2;
            int rowW = 340;
            int rowH = 16;

            if (mouseX >= x && mouseX <= x + rowW) {
                if (mouseY >= y && mouseY <= y + rowH) {
                    config.setDeviceMode(config.getDeviceMode().next());
                    VirtualKeyboard.playClickSound();
                    return true;
                }
                y += rowH + 3;
                if (mouseY >= y && mouseY <= y + rowH) {
                    config.enableDynamicAfkThrottle = !config.enableDynamicAfkThrottle;
                    config.save();
                    VirtualKeyboard.playClickSound();
                    return true;
                }
                y += rowH + 3;
                if (mouseY >= y && mouseY <= y + rowH) {
                    config.enableAfkScreenDimming = !config.enableAfkScreenDimming;
                    config.save();
                    VirtualKeyboard.playClickSound();
                    return true;
                }
                y += rowH + 3;
                if (mouseY >= y && mouseY <= y + rowH) {
                    config.enableDynamicChunkThreading = !config.enableDynamicChunkThreading;
                    config.save();
                    VirtualKeyboard.playClickSound();
                    return true;
                }
                y += rowH + 3;
                if (mouseY >= y && mouseY <= y + rowH) {
                    config.enableThermalFanGuard = !config.enableThermalFanGuard;
                    config.save();
                    VirtualKeyboard.playClickSound();
                    return true;
                }
                y += rowH + 3;
                if (mouseY >= y && mouseY <= y + rowH) {
                    config.enableHotbarHaptics = !config.enableHotbarHaptics;
                    config.save();
                    VirtualKeyboard.playClickSound();
                    return true;
                }
                y += rowH + 3;
                if (mouseY >= y && mouseY <= y + rowH) {
                    config.enablePerformanceTracking = !config.enablePerformanceTracking;
                    config.save();
                    VirtualKeyboard.playClickSound();
                    return true;
                }
                y += rowH + 3;
                if (mouseY >= y && mouseY <= y + rowH) {
                    config.enableF3ControllerDebug = !config.enableF3ControllerDebug;
                    config.save();
                    VirtualKeyboard.playClickSound();
                    return true;
                }
                y += rowH + 3;
                if (mouseY >= y && mouseY <= y + rowH) {
                    config.nativeTrackpadCamera = !config.nativeTrackpadCamera;
                    config.save();
                    VirtualKeyboard.playClickSound();
                    return true;
                }
                y += rowH + 3;
                if (mouseY >= y && mouseY <= y + rowH) {
                    config.showControllerToasts = !config.showControllerToasts;
                    config.save();
                    VirtualKeyboard.playClickSound();
                    return true;
                }
            }
        } else {
            // Tab 4: Hardware & Steam Input Bridge
            ModConfig config = ModConfig.getInstance();
            int y = 46;
            int x = (this.width - 340) / 2;
            int rowW = 340;
            int rowH = 16;

            if (mouseX >= x && mouseX <= x + rowW) {
                if (mouseY >= y && mouseY <= y + rowH) {
                    config.enableRightStickRadialMenu = !config.enableRightStickRadialMenu;
                    config.save();
                    VirtualKeyboard.playClickSound();
                    return true;
                }
                y += rowH + 2;
                if (mouseY >= y && mouseY <= y + rowH) {
                    // Aus -> Nur beim Zielen -> Immer -> Trackpad berührt -> R-Stick berührt -> Aus
                    String[] modes = {"AIM_ONLY", "ALWAYS", "TRACKPAD_TOUCH", "RIGHT_STICK_TOUCH"};
                    if (!config.enableGyroAiming) {
                        config.enableGyroAiming = true;
                        config.gyroActivation = modes[0];
                    } else {
                        int idx = java.util.Arrays.asList(modes).indexOf(config.gyroActivation);
                        if (idx >= modes.length - 1) {
                            config.enableGyroAiming = false;
                        } else {
                            config.gyroActivation = modes[idx + 1];
                        }
                    }
                    config.save();
                    VirtualKeyboard.playClickSound();
                    return true;
                }
                y += rowH + 2;
                if (mouseY >= y && mouseY <= y + rowH) {
                    config.enable8WayStickMapping = !config.enable8WayStickMapping;
                    config.save();
                    VirtualKeyboard.playClickSound();
                    return true;
                }
                y += rowH + 2;
                // Deadzone - / +
                if (mouseY >= y && mouseY <= y + rowH) {
                    if (mouseX >= x + rowW - 60 && mouseX <= x + rowW - 34) {
                        config.stickDeadzone = Math.max(0.05f, SteamDeckInputManager.getInstance().getStickDeadzone() - 0.025f);
                        config.stickDeadzoneAuto = false;
                        config.save();
                        VirtualKeyboard.playClickSound();
                        return true;
                    } else if (mouseX >= x + rowW - 30 && mouseX <= x + rowW - 4) {
                        config.stickDeadzone = Math.min(0.50f, SteamDeckInputManager.getInstance().getStickDeadzone() + 0.025f);
                        config.stickDeadzoneAuto = false;
                        config.save();
                        VirtualKeyboard.playClickSound();
                        return true;
                    }
                }
                y += rowH + 2;
                // Trackpad sens - / +
                if (mouseY >= y && mouseY <= y + rowH) {
                    if (mouseX >= x + rowW - 60 && mouseX <= x + rowW - 34) {
                        config.trackpadSensitivity = Math.max(0.2f, config.trackpadSensitivity - 0.1f);
                        config.save();
                        VirtualKeyboard.playClickSound();
                        return true;
                    } else if (mouseX >= x + rowW - 30 && mouseX <= x + rowW - 4) {
                        config.trackpadSensitivity = Math.min(3.0f, config.trackpadSensitivity + 0.1f);
                        config.save();
                        VirtualKeyboard.playClickSound();
                        return true;
                    }
                }
                y += rowH + 3 + 14; // Skip header

                // Bridge buttons in 2 columns
                int halfW = (rowW - 6) / 2;
                int rx = x + halfW + 6;
                DeckButton[][] pairs = {
                    {DeckButton.L4, DeckButton.R4},
                    {DeckButton.L5, DeckButton.R5},
                    {DeckButton.LEFT_STICK_TOUCH, DeckButton.RIGHT_STICK_TOUCH}
                };

                for (DeckButton[] pair : pairs) {
                    // Left column
                    if (mouseX >= x && mouseX <= x + halfW && mouseY >= y && mouseY <= y + rowH) {
                        this.listeningForBridgeKey = (this.listeningForBridgeKey == pair[0]) ? null : pair[0];
                        VirtualKeyboard.playClickSound();
                        return true;
                    }
                    // Right column
                    if (mouseX >= rx && mouseX <= rx + halfW && mouseY >= y && mouseY <= y + rowH) {
                        this.listeningForBridgeKey = (this.listeningForBridgeKey == pair[1]) ? null : pair[1];
                        VirtualKeyboard.playClickSound();
                        return true;
                    }
                    y += rowH + 2;
                }

                y += 2;
                // 1-Click Steam Input Layout Installer click
                if (mouseX >= x && mouseX <= x + rowW && mouseY >= y && mouseY <= y + 16) {
                    com.steamdeck.mc.input.SteamLayoutInstaller.installLayout(true);
                    this.layoutInstallStatus = com.steamdeck.mc.input.SteamLayoutInstaller.getLastStatusMessage();
                    this.layoutInstallStatusTime = System.currentTimeMillis();
                    VirtualKeyboard.playClickSound();
                    return true;
                }
            }
        }

        return super.mouseClicked(event, doubleClick);
    }

    private int navCol = 0;
    private int navRow = 0;

    public void navigateController(int dx, int dy) {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.getWindow() == null) return;

        // Trackpad tab: left/right on a value row changes the value directly
        if (this.currentTab == 5 && this.navRow >= 0 && this.navRow < TRACKPAD_ROWS && dx != 0 && dy == 0) {
            if (adjustTrackpadSetting(this.navRow, dx)) {
                VirtualKeyboard.playClickSound();
            }
            return;
        }

        int maxRows = (this.currentTab == 3 ? 10 : (this.currentTab == 5 ? TRACKPAD_ROWS : 7));
        int maxCols = 0;

        if (this.navRow == -1) {
            this.currentTab = VISIBLE_TABS[Math.max(0, Math.min(TAB_COUNT - 1, tabPosition(this.currentTab) + dx))];
            this.navRow = Math.max(-1, Math.min(maxRows, this.navRow + dy));
        } else {
            this.navCol = Math.max(0, Math.min(maxCols, this.navCol + dx));
            this.navRow = Math.max(-1, Math.min(maxRows, this.navRow + dy));
        }

        int targetX;
        int targetY;
        if (this.navRow == -1) {
            // Same geometry as the tab bar in extractRenderState()
            int tabW = Math.min(95, (this.width - 24) / TAB_COUNT);
            int startX = (this.width - tabW * TAB_COUNT - (TAB_COUNT - 1) * 3) / 2;
            targetX = startX + tabPosition(this.currentTab) * (tabW + 3) + tabW / 2;
            targetY = 27 + 17 / 2;
        } else if (this.navRow == maxRows) {
            int btnY = this.height - 23;
            targetX = (this.navCol == 0) ? 74 : (this.width - 62);
            targetY = btnY + 9;
        } else {
            int startY = 46;
            int stepY = this.currentTab == 3 ? 19 : (this.currentTab == 5 ? PAD_STEP : 18);
            targetX = this.width / 2;
            targetY = startY + this.navRow * stepY + 8;
        }
        warpCursorToGui(client, targetX, targetY);
    }

    private void warpCursorToGui(Minecraft client, int guiX, int guiY) {
        com.mojang.blaze3d.platform.Window win = client.getWindow();
        // SDL window points (logical size), not framebuffer pixels - matters with display scaling
        float winX = (float) guiX * (float) win.getScreenWidth() / (float) Math.max(1, win.getGuiScaledWidth());
        float winY = (float) guiY * (float) win.getScreenHeight() / (float) Math.max(1, win.getGuiScaledHeight());
        org.lwjgl.sdl.SDLMouse.SDL_WarpMouseInWindow(win.handle(), winX, winY);
        client.mouseHandler.onMove(win.handle(), winX, winY, 0.0, 0.0);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int key = event.key();
        boolean isEsc = event.isEscape() || key == 41 || key == 256;

        // 1. Listening for Bridge Key
        if (this.listeningForBridgeKey != null) {
            if (!isEsc) {
                int assignKey = (key == 42 || key == 76 || key == 259 || key == 261) ? 0 : key;
                ModConfig cfg = ModConfig.getInstance();
                if (this.listeningForBridgeKey == DeckButton.L4) cfg.steamBridgeKeyL4 = assignKey;
                else if (this.listeningForBridgeKey == DeckButton.R4) cfg.steamBridgeKeyR4 = assignKey;
                else if (this.listeningForBridgeKey == DeckButton.L5) cfg.steamBridgeKeyL5 = assignKey;
                else if (this.listeningForBridgeKey == DeckButton.R5) cfg.steamBridgeKeyR5 = assignKey;
                else if (this.listeningForBridgeKey == DeckButton.LEFT_STICK_TOUCH) cfg.steamBridgeKeyL3Touch = assignKey;
                else if (this.listeningForBridgeKey == DeckButton.RIGHT_STICK_TOUCH) cfg.steamBridgeKeyR3Touch = assignKey;
                cfg.save();
                VirtualKeyboard.playClickSound();
            }
            this.listeningForBridgeKey = null;
            return true;
        }

        // 2. Escape handling
        if (isEsc) {
            this.onClose();
            return true;
        }

        return super.keyPressed(event);
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().gui.setScreen(this.parent);
    }
}
