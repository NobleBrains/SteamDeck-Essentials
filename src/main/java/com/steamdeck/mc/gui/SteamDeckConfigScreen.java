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
    private int currentTab = 0; // 0=In-Game, 1=Menus, 2=Aiming, 3=Eco/Akku, 4=Hardware/Bridge
    private DeckButton selectingButton = null;
    private int modalSubTab = 0; // 0=Game Actions, 1=Mouse & Special

    // Custom Key modal state
    private boolean configuringCustomKey = false;
    private boolean listeningForKey = false;
    private int selectedCustomKey = 19; // Default SDL3 Scancode 'P' (InputConstants.KEY_P = 19)
    private String customKeyName = "Minimap";

    // Bridge Key configuration state
    private DeckButton listeningForBridgeKey = null;
    private String layoutInstallStatus = null;
    private long layoutInstallStatusTime = 0L;

    private static final DeckButton[] LEFT_COLUMN_BUTTONS = {
            DeckButton.A, DeckButton.B, DeckButton.X, DeckButton.Y,
            DeckButton.LB, DeckButton.RB, DeckButton.LT, DeckButton.RT,
            DeckButton.L3, DeckButton.R3, DeckButton.START,
            DeckButton.LEFT_STICK_TOUCH
    };

    private static final DeckButton[] RIGHT_COLUMN_BUTTONS = {
            DeckButton.L4, DeckButton.R4, DeckButton.L5, DeckButton.R5,
            DeckButton.LEFT_TRACKPAD, DeckButton.RIGHT_TRACKPAD,
            DeckButton.DPAD_UP, DeckButton.DPAD_DOWN, DeckButton.DPAD_LEFT, DeckButton.DPAD_RIGHT,
            DeckButton.SELECT,
            DeckButton.RIGHT_STICK_TOUCH
    };

    private static final DeckAction[] GAME_ACTIONS = {
            DeckAction.ATTACK, DeckAction.USE,
            DeckAction.JUMP, DeckAction.SNEAK,
            DeckAction.SNEAK_TOGGLE, DeckAction.SPRINT,
            DeckAction.ZOOM, DeckAction.PICK_BLOCK,
            DeckAction.DROP, DeckAction.INVENTORY,
            DeckAction.SWAP_OFFHAND, DeckAction.HOTBAR_PREV,
            DeckAction.HOTBAR_NEXT, DeckAction.TOGGLE_PERSPECTIVE,
            DeckAction.TOGGLE_DEBUG, DeckAction.OPEN_CHAT,
            DeckAction.PAUSE
    };

    private static final DeckAction[] MOUSE_AND_SPECIAL_ACTIONS = {
            DeckAction.MOUSE_LEFT_CLICK, DeckAction.MOUSE_RIGHT_CLICK,
            DeckAction.MOUSE_MIDDLE_CLICK, DeckAction.MOUSE_QUICK_MOVE,
            DeckAction.RADIAL_MENU_CYCLE, DeckAction.CLOSE_SCREEN,
            DeckAction.CUSTOM_KEY, DeckAction.NONE
    };

    // Uses Minecraft 26.3 SDL3 Scancodes (InputConstants.KEY_M=16, KEY_B=5, KEY_V=25, KEY_C=6, KEY_L=15, KEY_P=19, KEY_T=23)
    private static final String[][] PRESETS = {
            {"Minimap", "M", "16"},
            {"Wegpunkte", "B", "5"},
            {"VoiceChat", "V", "25"},
            {"Rucksack", "B", "5"},
            {"Zoom", "C", "6"},
            {"Quests", "L", "15"},
            {"Mod-Menü", "P", "19"},
            {"Chat", "T", "23"}
    };

    public SteamDeckConfigScreen(Screen parent) {
        super(Component.literal("Steam Deck Controller"));
        this.parent = parent;
    }

    public boolean isListening() {
        return this.listeningForKey || this.listeningForBridgeKey != null;
    }

    private ActionSet getSelectedActionSet() {
        return switch (this.currentTab) {
            case 1 -> ActionSet.MENUS;
            case 2 -> ActionSet.AIMING;
            default -> ActionSet.IN_GAME;
        };
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

        // Tab Navigation Bar (5 Tabs)
        int tabCount = 5;
        int tabW = Math.min(95, (this.width - 24) / tabCount);
        int tabH = 17;
        int tabY = 27;
        int tabStartX = (this.width - tabW * tabCount - (tabCount - 1) * 3) / 2;

        String[] tabLabels = {"🎮 Spiel", "📦 Menüs", "🎯 Zielen", "⚡ Akku & Eco", "⚙️ Bridge"};
        for (int i = 0; i < tabCount; i++) {
            int tx = tabStartX + i * (tabW + 3);
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
        if (this.currentTab < 3) {
            renderActionSetTab(graphics, font, mouseX, mouseY, getSelectedActionSet());
        } else if (this.currentTab == 3) {
            renderEcoTab(graphics, font, mouseX, mouseY);
        } else {
            renderBridgeAndHardwareTab(graphics, font, mouseX, mouseY);
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

        // Done Button
        int doneX = this.width - btnW - 10;
        boolean doneHover = mouseX >= doneX && mouseX <= doneX + btnW && mouseY >= btnY && mouseY <= btnY + btnH;
        graphics.fill(doneX, btnY, doneX + btnW, btnY + btnH, doneHover ? 0xFF104A7B : 0xFF1E3A4A);
        graphics.fill(doneX, btnY, doneX + btnW, btnY + 1, 0xFF00E5FF);
        graphics.fill(doneX, btnY + btnH - 1, doneX + btnW, btnY + btnH, 0xFF00E5FF);
        graphics.fill(doneX, btnY, doneX + 1, btnY + btnH, 0xFF00E5FF);
        graphics.fill(doneX + btnW - 1, btnY, doneX + btnW, btnY + btnH, 0xFF00E5FF);
        graphics.centeredText(font, "Fertig", doneX + btnW / 2, btnY + 6, doneHover ? 0xFF00FFCC : -1);

        // Action Selector / Custom Key Modal
        if (this.selectingButton != null) {
            if (this.configuringCustomKey) {
                renderCustomKeyModal(graphics, font, mouseX, mouseY);
            } else {
                renderActionSelectorModal(graphics, font, mouseX, mouseY);
            }
        }
    }

    private void renderActionSetTab(GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY, ActionSet set) {
        int rowSlot = Math.min(16, (this.height - 70) / LEFT_COLUMN_BUTTONS.length);
        int rowH = rowSlot - 2;
        int startY = 46;
        int colW = (this.width - 24) / 2;
        int leftColX = 8;
        int rightColX = leftColX + colW + 8;

        for (int i = 0; i < LEFT_COLUMN_BUTTONS.length; i++) {
            DeckButton btn = LEFT_COLUMN_BUTTONS[i];
            int y = startY + i * rowSlot;
            renderButtonConfigRow(graphics, font, leftColX, y, colW, rowH, btn, set, mouseX, mouseY);
        }

        for (int i = 0; i < RIGHT_COLUMN_BUTTONS.length; i++) {
            DeckButton btn = RIGHT_COLUMN_BUTTONS[i];
            int y = startY + i * rowSlot;
            renderButtonConfigRow(graphics, font, rightColX, y, colW, rowH, btn, set, mouseX, mouseY);
        }
    }

    private void renderButtonConfigRow(GuiGraphicsExtractor graphics, Font font, int x, int y, int width, int height,
                                       DeckButton btn, ActionSet set, int mouseX, int mouseY) {
        graphics.fill(x, y, x + width, y + height, 0xAA161B22);

        int badgeW = 74;
        // Clean button name badge (never overlaps)
        graphics.text(font, btn.getDisplayName(), x + 4, y + (height - 7) / 2, 0xFF00E5FF);

        // Action Pill
        int pillX = x + badgeW + 2;
        int pillW = width - badgeW - 4;
        int pillY = y + 1;
        int pillH = height - 2;

        ActionBinding binding = ModConfig.getInstance().getBinding(set, btn);
        boolean hover = mouseX >= pillX && mouseX <= pillX + pillW && mouseY >= pillY && mouseY <= pillY + pillH;
        int pillBg = hover ? 0xFF2A3D52 : (binding.isCustomKey() ? 0xFF1B3B36 : 0xFF1E2632);
        int border = hover ? 0xFF00FFCC : (binding.isCustomKey() ? 0xFF00FFAA : 0x4400E5FF);

        graphics.fill(pillX, pillY, pillX + pillW, pillY + pillH, pillBg);
        graphics.fill(pillX, pillY, pillX + pillW, pillY + 1, border);
        graphics.fill(pillX, pillY + pillH - 1, pillX + pillW, pillY + pillH, border);
        graphics.fill(pillX, pillY, pillX + 1, pillY + pillH, border);
        graphics.fill(pillX + pillW - 1, pillY, pillX + pillW, pillY + pillH, border);

        // Truncate text cleanly if needed so it never bleeds outside pill
        String fullText = binding.getDisplayLabel();
        int maxTextW = pillW - 6;
        String displayText = font.plainSubstrByWidth(fullText, maxTextW);
        if (displayText.length() < fullText.length()) {
            displayText = font.plainSubstrByWidth(fullText, Math.max(10, maxTextW - 6)) + "…";
        }

        graphics.centeredText(font, displayText, pillX + pillW / 2, pillY + (pillH - 7) / 2, hover ? 0xFF00FFCC : -1);
    }

    private void renderEcoTab(GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY) {
        ModConfig config = ModConfig.getInstance();
        int y = 46;
        int x = (this.width - 340) / 2;
        int rowW = 340;
        int rowH = 16;

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
    }

    private void renderBridgeAndHardwareTab(GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY) {
        ModConfig config = ModConfig.getInstance();
        int y = 46;
        int x = (this.width - 340) / 2;
        int rowW = 340;
        int rowH = 16;

        renderToggleOption(graphics, font, x, y, rowW, rowH, "Rechter Stick Radial-Menü (R3 wechselt)", config.enableRightStickRadialMenu, mouseX, mouseY);
        y += rowH + 2;
        renderToggleOption(graphics, font, x, y, rowW, rowH, "Contextual Gyro Zielen (beim Spannen)", config.enableGyroAiming, mouseX, mouseY);
        y += rowH + 2;
        renderToggleOption(graphics, font, x, y, rowW, rowH, "8-Wege Stick Anticheat-Mapping", config.enable8WayStickMapping, mouseX, mouseY);
        y += rowH + 2;

        // Stick Deadzone
        graphics.fill(x, y, x + rowW, y + rowH, 0xAA161B22);
        graphics.text(font, "Stick Deadzone: " + Math.round(config.stickDeadzone * 100) + "%", x + 8, y + 4, -1);
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
        graphics.centeredText(font, "Steam Input Bridge: Rücktasten & Stick Touch", x + rowW / 2, y + 2, 0xFF00E5FF);
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

    private void renderCustomKeyModal(GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY) {
        graphics.fill(0, 0, this.width, this.height, 0xDD000000);

        int modalW = Math.min(380, this.width - 20);
        int modalH = 210;
        int modalX = (this.width - modalW) / 2;
        int modalY = (this.height - modalH) / 2;

        graphics.fill(modalX, modalY, modalX + modalW, modalY + modalH, 0xFF0D1117);
        graphics.fill(modalX, modalY, modalX + modalW, modalY + 1, 0xFF00FFAA);
        graphics.fill(modalX, modalY + modalH - 1, modalX + modalW, modalY + modalH, 0xFF00FFAA);
        graphics.fill(modalX, modalY, modalX + 1, modalY + modalH, 0xFF00FFAA);
        graphics.fill(modalX + modalW - 1, modalY, modalX + modalW, modalY + modalH, 0xFF00FFAA);

        String title = "Tastatur-Taste für " + this.selectingButton.getDisplayName() + ":";
        graphics.centeredText(font, title, modalX + modalW / 2, modalY + 8, 0xFF00FFAA);

        // Key Box (Click to Listen)
        int keyBoxY = modalY + 24;
        int keyBoxW = modalW - 20;
        int keyBoxX = modalX + 10;
        int keyBoxH = 22;

        boolean keyHover = mouseX >= keyBoxX && mouseX <= keyBoxX + keyBoxW && mouseY >= keyBoxY && mouseY <= keyBoxY + keyBoxH;
        int keyBg = this.listeningForKey ? 0xFF3D3A16 : (keyHover ? 0xFF2A3D52 : 0xFF161B22);
        int keyBorder = this.listeningForKey ? 0xFFFFCC00 : 0xFF00FFAA;
        graphics.fill(keyBoxX, keyBoxY, keyBoxX + keyBoxW, keyBoxY + keyBoxH, keyBg);
        graphics.fill(keyBoxX, keyBoxY, keyBoxX + keyBoxW, keyBoxY + 1, keyBorder);
        graphics.fill(keyBoxX, keyBoxY + keyBoxH - 1, keyBoxX + keyBoxW, keyBoxY + keyBoxH, keyBorder);
        graphics.fill(keyBoxX, keyBoxY, keyBoxX + 1, keyBoxY + keyBoxH, keyBorder);
        graphics.fill(keyBoxX + keyBoxW - 1, keyBoxY, keyBoxX + keyBoxW, keyBoxY + keyBoxH, keyBorder);

        String keyText = this.listeningForKey
                ? ">> DRÜCKE JETZT EINE TASTE... <<"
                : "Taste: " + ActionBinding.getKeyLabel(this.selectedCustomKey) + " (Klicken zum Ändern)";
        int keyTextColor = this.listeningForKey ? 0xFFFFCC00 : 0xFFFFFFFF;
        graphics.centeredText(font, keyText, keyBoxX + keyBoxW / 2, keyBoxY + 7, keyTextColor);

        // Current Nickname display
        int nameY = keyBoxY + keyBoxH + 6;
        graphics.text(font, "Spitzname: " + this.customKeyName, keyBoxX, nameY + 2, 0xFF88CCFF);

        // Presets Heading
        int presetsHeadingY = nameY + 15;
        graphics.text(font, "Schnell-Presets (Taste & Name):", keyBoxX, presetsHeadingY, 0xFFAAAAAA);

        // Presets Grid
        int presetStartY = presetsHeadingY + 11;
        int pCols = 2;
        int pItemW = (modalW - 24) / pCols;
        int pItemH = 15;

        for (int i = 0; i < PRESETS.length; i++) {
            String name = PRESETS[i][0];
            String letter = PRESETS[i][1];
            int c = i % pCols;
            int r = i / pCols;
            int px = modalX + 10 + c * (pItemW + 4);
            int py = presetStartY + r * (pItemH + 3);

            boolean hover = mouseX >= px && mouseX <= px + pItemW && mouseY >= py && mouseY <= py + pItemH;
            graphics.fill(px, py, px + pItemW, py + pItemH, hover ? 0xFF2A3D52 : 0xFF1E2632);
            graphics.centeredText(font, name + " (" + letter + ")", px + pItemW / 2, py + 4, hover ? 0xFF00FFCC : -1);
        }

        // Bottom Action Buttons
        int bY = modalY + modalH - 22;
        int bW = (modalW - 26) / 2;

        // Cancel
        int cX = modalX + 10;
        boolean cHover = mouseX >= cX && mouseX <= cX + bW && mouseY >= bY && mouseY <= bY + 18;
        graphics.fill(cX, bY, cX + bW, bY + 18, cHover ? 0xFF442222 : 0xFF221111);
        graphics.centeredText(font, "Abbrechen", cX + bW / 2, bY + 5, cHover ? 0xFFFF7777 : 0xFFCC5555);

        // Save
        int sX = cX + bW + 6;
        boolean sHover = mouseX >= sX && mouseX <= sX + bW && mouseY >= bY && mouseY <= bY + 18;
        graphics.fill(sX, bY, sX + bW, bY + 18, sHover ? 0xFF0E5A35 : 0xFF0A3D24);
        graphics.fill(sX, bY, sX + bW, bY + 1, 0xFF00FF88);
        graphics.fill(sX, bY + 17, sX + bW, bY + 18, 0xFF00FF88);
        graphics.fill(sX, bY, sX + 1, bY + 18, 0xFF00FF88);
        graphics.fill(sX + bW - 1, bY, sX + bW, bY + 18, 0xFF00FF88);
        graphics.centeredText(font, "Speichern & Zuweisen", sX + bW / 2, bY + 5, sHover ? 0xFF55FFAA : -1);
    }

    private void renderActionSelectorModal(GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY) {
        graphics.fill(0, 0, this.width, this.height, 0xCC000000);

        int modalW = Math.min(380, this.width - 16);
        int modalH = Math.min(222, this.height - 16);
        int modalX = (this.width - modalW) / 2;
        int modalY = (this.height - modalH) / 2;

        graphics.fill(modalX, modalY, modalX + modalW, modalY + modalH, 0xFF0D1117);
        graphics.fill(modalX, modalY, modalX + modalW, modalY + 1, 0xFF00E5FF);
        graphics.fill(modalX, modalY + modalH - 1, modalX + modalW, modalY + modalH, 0xFF00E5FF);
        graphics.fill(modalX, modalY, modalX + 1, modalY + modalH, 0xFF00E5FF);
        graphics.fill(modalX + modalW - 1, modalY, modalX + modalW, modalY + modalH, 0xFF00E5FF);

        String title = "Aktion für " + this.selectingButton.getDisplayName() + " wählen:";
        graphics.centeredText(font, title, modalX + modalW / 2, modalY + 6, 0xFF00E5FF);

        // Sub-Tabs inside Modal: [🎮 Spiel-Aktionen]  [🖱️ Maus & Spezial]
        int subTabY = modalY + 18;
        int subTabH = 16;
        int subTabW = (modalW - 20) / 2;

        int tab0X = modalX + 8;
        boolean t0Active = (this.modalSubTab == 0);
        boolean t0Hover = mouseX >= tab0X && mouseX <= tab0X + subTabW && mouseY >= subTabY && mouseY <= subTabY + subTabH;
        graphics.fill(tab0X, subTabY, tab0X + subTabW, subTabY + subTabH, t0Active ? 0xFF1E3A4A : (t0Hover ? 0xFF2A3D52 : 0xFF161B22));
        graphics.fill(tab0X, subTabY + subTabH - 1, tab0X + subTabW, subTabY + subTabH, t0Active ? 0xFF00FFCC : 0x4400E5FF);
        graphics.centeredText(font, "🎮 Spiel-Aktionen", tab0X + subTabW / 2, subTabY + 4, t0Active ? 0xFF00FFCC : -1);

        int tab1X = tab0X + subTabW + 4;
        boolean t1Active = (this.modalSubTab == 1);
        boolean t1Hover = mouseX >= tab1X && mouseX <= tab1X + subTabW && mouseY >= subTabY && mouseY <= subTabY + subTabH;
        graphics.fill(tab1X, subTabY, tab1X + subTabW, subTabY + subTabH, t1Active ? 0xFF1E3A4A : (t1Hover ? 0xFF2A3D52 : 0xFF161B22));
        graphics.fill(tab1X, subTabY + subTabH - 1, tab1X + subTabW, subTabY + subTabH, t1Active ? 0xFF00FFCC : 0x4400E5FF);
        graphics.centeredText(font, "🖱️ Maus & Spezial", tab1X + subTabW / 2, subTabY + 4, t1Active ? 0xFF00FFCC : -1);

        // Actions Grid
        DeckAction[] actions = (this.modalSubTab == 0) ? GAME_ACTIONS : MOUSE_AND_SPECIAL_ACTIONS;
        int cols = 2;
        int itemW = (modalW - 20) / cols;
        int itemH = 15;
        int startY = subTabY + subTabH + 4;

        for (int i = 0; i < actions.length; i++) {
            DeckAction action = actions[i];
            int c = i % cols;
            int r = i / cols;
            int ax = modalX + 8 + c * (itemW + 4);
            int ay = startY + r * (itemH + 3);

            if (ay + itemH > modalY + modalH - 22) break;

            boolean hover = mouseX >= ax && mouseX <= ax + itemW && mouseY >= ay && mouseY <= ay + itemH;
            int bg = hover ? 0xFF2A3D52 : (action == DeckAction.CUSTOM_KEY ? 0xFF1B3B36 : 0xFF161B22);
            int textCol = hover ? 0xFF00FFCC : (action == DeckAction.CUSTOM_KEY ? 0xFF00FFAA : -1);

            graphics.fill(ax, ay, ax + itemW, ay + itemH, bg);
            graphics.centeredText(font, action.getLabel(), ax + itemW / 2, ay + (itemH - 7) / 2, textCol);
        }

        // Cancel button
        int closeBtnY = modalY + modalH - 20;
        boolean closeHover = mouseX >= modalX + 8 && mouseX <= modalX + modalW - 8 && mouseY >= closeBtnY && mouseY <= closeBtnY + 16;
        graphics.fill(modalX + 8, closeBtnY, modalX + modalW - 8, closeBtnY + 16, closeHover ? 0xFF442222 : 0xFF221111);
        graphics.centeredText(font, "Abbrechen", modalX + modalW / 2, closeBtnY + 4, closeHover ? 0xFFFF7777 : 0xFFCC5555);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mouseX = event.x();
        double mouseY = event.y();

        // 1. If Modal is open, handle modal click
        if (this.selectingButton != null) {
            if (this.configuringCustomKey) {
                // Handle Custom Key modal clicks
                int modalW = Math.min(380, this.width - 20);
                int modalH = 210;
                int modalX = (this.width - modalW) / 2;
                int modalY = (this.height - modalH) / 2;

                int keyBoxY = modalY + 24;
                int keyBoxW = modalW - 20;
                int keyBoxX = modalX + 10;
                int keyBoxH = 22;

                // Click Key Box to listen
                if (mouseX >= keyBoxX && mouseX <= keyBoxX + keyBoxW && mouseY >= keyBoxY && mouseY <= keyBoxY + keyBoxH) {
                    this.listeningForKey = !this.listeningForKey;
                    VirtualKeyboard.playClickSound();
                    return true;
                }

                // Presets Click
                int presetsHeadingY = keyBoxY + keyBoxH + 6 + 15;
                int presetStartY = presetsHeadingY + 11;
                int pCols = 2;
                int pItemW = (modalW - 24) / pCols;
                int pItemH = 15;

                for (int i = 0; i < PRESETS.length; i++) {
                    int c = i % pCols;
                    int r = i / pCols;
                    int px = modalX + 10 + c * (pItemW + 4);
                    int py = presetStartY + r * (pItemH + 3);

                    if (mouseX >= px && mouseX <= px + pItemW && mouseY >= py && mouseY <= py + pItemH) {
                        try {
                            this.selectedCustomKey = Integer.parseInt(PRESETS[i][2]);
                            this.customKeyName = PRESETS[i][0];
                            this.listeningForKey = false;
                            VirtualKeyboard.playClickSound();
                        } catch (Exception ignored) {}
                        return true;
                    }
                }

                // Bottom Buttons (Cancel & Save)
                int bY = modalY + modalH - 22;
                int bW = (modalW - 26) / 2;
                int cX = modalX + 10;
                int sX = cX + bW + 6;

                // Cancel
                if (mouseX >= cX && mouseX <= cX + bW && mouseY >= bY && mouseY <= bY + 18) {
                    this.configuringCustomKey = false;
                    this.listeningForKey = false;
                    VirtualKeyboard.playClickSound();
                    return true;
                }

                // Save & Assign
                if (mouseX >= sX && mouseX <= sX + bW && mouseY >= bY && mouseY <= bY + 18) {
                    ModConfig.getInstance().setBinding(getSelectedActionSet(), this.selectingButton,
                            new ActionBinding(this.selectedCustomKey, this.customKeyName));
                    this.configuringCustomKey = false;
                    this.listeningForKey = false;
                    this.selectingButton = null;
                    VirtualKeyboard.playClickSound();
                    return true;
                }
                return true;
            }

            // Normal Action Selector Modal
            int modalW = Math.min(380, this.width - 16);
            int modalH = Math.min(222, this.height - 16);
            int modalX = (this.width - modalW) / 2;
            int modalY = (this.height - modalH) / 2;

            // Close Button
            int closeBtnY = modalY + modalH - 20;
            if (mouseX >= modalX + 8 && mouseX <= modalX + modalW - 8 && mouseY >= closeBtnY && mouseY <= closeBtnY + 16) {
                this.selectingButton = null;
                VirtualKeyboard.playClickSound();
                return true;
            }

            // Sub-Tab Switcher Clicks
            int subTabY = modalY + 18;
            int subTabH = 16;
            int subTabW = (modalW - 20) / 2;
            int tab0X = modalX + 8;
            int tab1X = tab0X + subTabW + 4;

            if (mouseX >= tab0X && mouseX <= tab0X + subTabW && mouseY >= subTabY && mouseY <= subTabY + subTabH) {
                this.modalSubTab = 0;
                VirtualKeyboard.playClickSound();
                return true;
            }
            if (mouseX >= tab1X && mouseX <= tab1X + subTabW && mouseY >= subTabY && mouseY <= subTabY + subTabH) {
                this.modalSubTab = 1;
                VirtualKeyboard.playClickSound();
                return true;
            }

            // Action Items Clicks
            DeckAction[] actions = (this.modalSubTab == 0) ? GAME_ACTIONS : MOUSE_AND_SPECIAL_ACTIONS;
            int cols = 2;
            int itemW = (modalW - 20) / cols;
            int itemH = 15;
            int startY = subTabY + subTabH + 4;

            for (int i = 0; i < actions.length; i++) {
                int c = i % cols;
                int r = i / cols;
                int ax = modalX + 8 + c * (itemW + 4);
                int ay = startY + r * (itemH + 3);

                if (ay + itemH > modalY + modalH - 22) break;

                if (mouseX >= ax && mouseX <= ax + itemW && mouseY >= ay && mouseY <= ay + itemH) {
                    DeckAction chosen = actions[i];
                    if (chosen == DeckAction.CUSTOM_KEY) {
                        ActionBinding currentBinding = ModConfig.getInstance().getBinding(getSelectedActionSet(), this.selectingButton);
                        if (currentBinding.isCustomKey()) {
                            this.selectedCustomKey = currentBinding.getCustomKeyCode();
                            this.customKeyName = currentBinding.getCustomNickname();
                        } else {
                            this.selectedCustomKey = 19; // SDL3 Scancode 'P'
                            this.customKeyName = "Taste P";
                        }
                        this.configuringCustomKey = true;
                        this.listeningForKey = false;
                        VirtualKeyboard.playClickSound();
                        return true;
                    }

                    ModConfig.getInstance().setAction(getSelectedActionSet(), this.selectingButton, chosen);
                    this.selectingButton = null;
                    VirtualKeyboard.playClickSound();
                    return true;
                }
            }
            return true;
        }

        // 2. Tab Bar Clicks (5 Tabs)
        int tabCount = 5;
        int tabW = Math.min(95, (this.width - 24) / tabCount);
        int tabH = 17;
        int tabY = 27;
        int tabStartX = (this.width - tabW * tabCount - (tabCount - 1) * 3) / 2;
        for (int i = 0; i < tabCount; i++) {
            int tx = tabStartX + i * (tabW + 3);
            if (mouseX >= tx && mouseX <= tx + tabW && mouseY >= tabY && mouseY <= tabY + tabH) {
                this.currentTab = i;
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
            if (this.currentTab < 3) {
                ModConfig.getInstance().resetActionSetToDefaults(getSelectedActionSet());
                VirtualKeyboard.playClickSound();
            } else if (this.currentTab == 3) {
                ModConfig cfg = ModConfig.getInstance();
                cfg.enableDynamicAfkThrottle = true;
                cfg.enableAfkScreenDimming = true;
                cfg.enableDynamicChunkThreading = true;
                cfg.enableThermalFanGuard = true;
                cfg.enableHotbarHaptics = true;
                cfg.enablePerformanceTracking = true;
                cfg.enableF3ControllerDebug = true;
                cfg.save();
                VirtualKeyboard.playClickSound();
            } else if (this.currentTab == 4) {
                ModConfig cfg = ModConfig.getInstance();
                cfg.enableRightStickRadialMenu = true;
                cfg.enableGyroAiming = true;
                cfg.enable8WayStickMapping = true;
                cfg.stickDeadzone = 0.25f;
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
        int doneX = this.width - btnW - 10;
        if (mouseX >= doneX && mouseX <= doneX + btnW && mouseY >= btnY && mouseY <= btnY + btnH) {
            this.onClose();
            return true;
        }

        // 4. Action Set Row Clicks (Tabs 0, 1, 2)
        if (this.currentTab < 3) {
            int rowSlot = Math.min(16, (this.height - 70) / LEFT_COLUMN_BUTTONS.length);
            int rowH = rowSlot - 2;
            int startY = 46;
            int colW = (this.width - 24) / 2;
            int leftColX = 8;
            int rightColX = leftColX + colW + 8;
            int badgeW = 74;

            for (int i = 0; i < LEFT_COLUMN_BUTTONS.length; i++) {
                int y = startY + i * rowSlot;
                int pillX = leftColX + badgeW + 2;
                int pillW = colW - badgeW - 4;
                if (mouseX >= pillX && mouseX <= pillX + pillW && mouseY >= y && mouseY <= y + rowH) {
                    this.selectingButton = LEFT_COLUMN_BUTTONS[i];
                    ActionBinding b = ModConfig.getInstance().getBinding(getSelectedActionSet(), this.selectingButton);
                    this.modalSubTab = (b.isCustomKey() || b.getAction().name().startsWith("MOUSE_") || b.getAction() == DeckAction.CLOSE_SCREEN || b.getAction() == DeckAction.RADIAL_MENU_CYCLE) ? 1 : 0;
                    VirtualKeyboard.playClickSound();
                    return true;
                }
            }

            for (int i = 0; i < RIGHT_COLUMN_BUTTONS.length; i++) {
                int y = startY + i * rowSlot;
                int pillX = rightColX + badgeW + 2;
                int pillW = colW - badgeW - 4;
                if (mouseX >= pillX && mouseX <= pillX + pillW && mouseY >= y && mouseY <= y + rowH) {
                    this.selectingButton = RIGHT_COLUMN_BUTTONS[i];
                    ActionBinding b = ModConfig.getInstance().getBinding(getSelectedActionSet(), this.selectingButton);
                    this.modalSubTab = (b.isCustomKey() || b.getAction().name().startsWith("MOUSE_") || b.getAction() == DeckAction.CLOSE_SCREEN || b.getAction() == DeckAction.RADIAL_MENU_CYCLE) ? 1 : 0;
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
                    config.enableGyroAiming = !config.enableGyroAiming;
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
                        config.stickDeadzone = Math.max(0.05f, config.stickDeadzone - 0.05f);
                        config.save();
                        VirtualKeyboard.playClickSound();
                        return true;
                    } else if (mouseX >= x + rowW - 30 && mouseX <= x + rowW - 4) {
                        config.stickDeadzone = Math.min(0.50f, config.stickDeadzone + 0.05f);
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
                    com.steamdeck.mc.input.SteamLayoutInstaller.installLayout();
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

        if (this.selectingButton != null && !this.configuringCustomKey) {
            DeckAction[] actions = (this.modalSubTab == 0) ? GAME_ACTIONS : MOUSE_AND_SPECIAL_ACTIONS;
            int rows = (actions.length + 1) / 2;
            this.navCol = Math.max(0, Math.min(1, this.navCol + dx));
            this.navRow = Math.max(-1, Math.min(rows, this.navRow + dy));

            int modalW = Math.min(380, this.width - 16);
            int modalH = Math.min(222, this.height - 16);
            int modalX = (this.width - modalW) / 2;
            int modalY = (this.height - modalH) / 2;
            int subTabY = modalY + 18;
            int subTabH = 16;
            int subTabW = (modalW - 20) / 2;

            int targetX;
            int targetY;
            if (this.navRow == -1) {
                targetX = modalX + 8 + this.navCol * (subTabW + 4) + subTabW / 2;
                targetY = subTabY + subTabH / 2;
            } else if (this.navRow == rows) {
                targetX = modalX + modalW / 2;
                targetY = modalY + modalH - 12;
            } else {
                int itemW = (modalW - 20) / 2;
                int itemH = 15;
                int startY = subTabY + subTabH + 4;
                targetX = modalX + 8 + this.navCol * (itemW + 4) + itemW / 2;
                targetY = startY + this.navRow * (itemH + 3) + itemH / 2;
            }
            warpCursorToGui(client, targetX, targetY);
            return;
        }

        int maxRows = (this.currentTab < 3) ? LEFT_COLUMN_BUTTONS.length : 7;
        int maxCols = (this.currentTab < 3) ? 1 : 0;

        if (this.navRow == -1) {
            this.currentTab = Math.max(0, Math.min(4, this.currentTab + dx));
            this.navRow = Math.max(-1, Math.min(maxRows, this.navRow + dy));
        } else {
            this.navCol = Math.max(0, Math.min(maxCols, this.navCol + dx));
            this.navRow = Math.max(-1, Math.min(maxRows, this.navRow + dy));
        }

        int targetX;
        int targetY;
        if (this.navRow == -1) {
            int tabBarWidth = Math.min(450, this.width - 16);
            int tabW = (tabBarWidth - 16) / 5;
            int startX = (this.width - tabBarWidth) / 2;
            targetX = startX + this.currentTab * (tabW + 4) + tabW / 2;
            targetY = 30;
        } else if (this.navRow == maxRows) {
            int btnY = this.height - 23;
            targetX = (this.navCol == 0) ? 74 : (this.width - 62);
            targetY = btnY + 9;
        } else if (this.currentTab < 3) {
            int rowSlot = Math.min(16, (this.height - 70) / LEFT_COLUMN_BUTTONS.length);
            int startY = 46;
            int colW = (this.width - 24) / 2;
            int leftColX = 8;
            int rightColX = leftColX + colW + 8;
            int colX = (this.navCol == 0) ? leftColX : rightColX;
            targetX = colX + 76 + (colW - 78) / 2;
            targetY = startY + this.navRow * rowSlot + (rowSlot - 2) / 2;
        } else {
            int startY = 46;
            int stepY = (this.currentTab == 3) ? 19 : 18;
            targetX = this.width / 2;
            targetY = startY + this.navRow * stepY + 8;
        }
        warpCursorToGui(client, targetX, targetY);
    }

    private void warpCursorToGui(Minecraft client, int guiX, int guiY) {
        com.mojang.blaze3d.platform.Window win = client.getWindow();
        float winX = (float) guiX * (float) win.getWidth() / (float) Math.max(1, win.getGuiScaledWidth());
        float winY = (float) guiY * (float) win.getHeight() / (float) Math.max(1, win.getGuiScaledHeight());
        org.lwjgl.sdl.SDLMouse.SDL_WarpMouseInWindow(win.handle(), winX, winY);
        client.mouseHandler.onMove(win.handle(), winX, winY, 0.0, 0.0);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int key = event.key();
        boolean isEsc = event.isEscape() || key == 41 || key == 256;

        // 1. Listening for Custom Key in modal
        if (this.configuringCustomKey && this.listeningForKey) {
            if (!isEsc) { // Not Escape
                this.selectedCustomKey = key;
                this.customKeyName = "Taste " + ActionBinding.getKeyLabel(key);
            }
            this.listeningForKey = false;
            VirtualKeyboard.playClickSound();
            return true;
        }

        // 2. Listening for Bridge Key
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

        // 3. Escape handling
        if (isEsc) {
            if (this.configuringCustomKey) {
                this.configuringCustomKey = false;
                this.listeningForKey = false;
                return true;
            }
            if (this.selectingButton != null) {
                this.selectingButton = null;
                return true;
            }
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
