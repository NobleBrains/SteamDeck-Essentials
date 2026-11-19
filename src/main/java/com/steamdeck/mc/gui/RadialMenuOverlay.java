package com.steamdeck.mc.gui;

import com.steamdeck.mc.SteamDeckEssentials;
import com.steamdeck.mc.config.ModConfig;
import com.steamdeck.mc.mixin.KeyMappingAccessor;
import com.steamdeck.mc.mixin.KeyboardHandlerAccessor;
import com.steamdeck.mc.power.HapticFeedbackManager;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.world.item.ItemStack;

/**
 * 3-Page SteamOS-styled Radial Menu controlled by the Right Joystick.
 * Press Right Stick Click (R3) to cycle between:
 *  - Page 0: Hotbar 1-9 Quick Selector (with live 3D item icons)
 *  - Page 1: Standard F-Keys (F1, F2, F3, F5, F8, F3+2, F3+F4, F3+F6)
 *  - Page 2: F3+ Debug Utilities (F3+G, F3+B, F3+H, F3+A, F3+N, F3+D, F3+C, F3+T)
 */
public class RadialMenuOverlay {
    private static final RadialMenuOverlay INSTANCE = new RadialMenuOverlay();

    public static final int PAGE_HOTBAR = 0;
    public static final int PAGE_F_KEYS = 1;
    public static final int PAGE_F3_COMBOS = 2;
    private static final int PAGE_COUNT = 3;

    private static final String[] PAGE_TITLES = {
            "🎒 Hotbar 1–9",
            "🖥️ F-Tasten (F1–F8)",
            "🔧 F3+ Utilities"
    };

    private static final String[][] F_KEYS_LABELS = {
            {"F1", "HUD ein/aus", "HUD & Hand ein-/ausblenden"},
            {"F2", "Screenshot", "Screenshot aufnehmen"},
            {"F3", "Debug-HUD", "F3 Debug-Bildschirm an/aus"},
            {"F3+2", "FPS-Graph", "Frametime & TPS Graph an/aus"},
            {"F5", "Perspektive", "Kamera-Ansicht wechseln"},
            {"F8", "Kino-Kamera", "Weiche Kameraschwenks an/aus"},
            {"F3+F4", "Spielmodus", "Spielmodus-Umschalter öffnen"},
            {"F3+F6", "Debug-Menü", "Debug-Optionen öffnen"}
    };

    private static final String[][] F3_COMBO_LABELS = {
            {"F3+G", "Chunk-Grid", "Chunk-Grenzen ein-/ausblenden"},
            {"F3+B", "Hitboxen", "Entity-Hitboxen ein-/ausblenden"},
            {"F3+H", "Tooltips", "Erweiterte Item-Tooltips (IDs/Haltbarkeit)"},
            {"F3+A", "Chunks laden", "Alle Chunks neu rendern"},
            {"F3+N", "Spectator", "Zuschauer ↔ Kreativ wechseln"},
            {"F3+D", "Chat leeren", "Chat-Verlauf bereinigen"},
            {"F3+C", "Pos kopieren", "Koordinaten / TP-Befehl kopieren"},
            {"F3+T", "Texturen", "Ressourcenpacks neu laden"}
    };

    private int currentPage = PAGE_HOTBAR;
    private boolean open = false;
    private int hoveredSlice = -1;
    private float currentStickX = 0.0f;
    private float currentStickY = 0.0f;
    private boolean switchedWhileOpen = false;
    private long lastPageSwitchToastTime = 0L;

    public static RadialMenuOverlay getInstance() {
        return INSTANCE;
    }

    private RadialMenuOverlay() {}

    public boolean isOpen() {
        return open;
    }

    public int getCurrentPage() {
        return currentPage;
    }

    /**
     * Cycles to the next Radial Menu page when R3 (Right Stick Click) is pressed.
     */
    public void cyclePage() {
        if (!ModConfig.getInstance().enableRightStickRadialMenu) return;

        currentPage = (currentPage + 1) % PAGE_COUNT;
        lastPageSwitchToastTime = System.currentTimeMillis();
        VirtualKeyboard.playClickSound();
        HapticFeedbackManager.getInstance().triggerHaptic(false, true, (short) 44000, 45);

        if (open) {
            switchedWhileOpen = true;
            hoveredSlice = computeSlice(currentStickX, currentStickY, getSliceCount(currentPage));
            if (currentPage == PAGE_HOTBAR && hoveredSlice >= 0 && hoveredSlice < 9) {
                Minecraft client = Minecraft.getInstance();
                if (client.player != null) {
                    client.player.getInventory().setSelectedSlot(hoveredSlice);
                }
            }
        }
    }

    /**
     * Cancels/closes the Radial Menu without executing an action (e.g. when entering a menu or drawing a bow).
     */
    public void cancel() {
        this.open = false;
        this.hoveredSlice = -1;
        this.currentStickX = 0.0f;
        this.currentStickY = 0.0f;
        this.switchedWhileOpen = false;
    }

    /**
     * Updates the Radial Menu state from the Right Joystick axes.
     */
    public void updateStick(Minecraft client, float stickX, float stickY) {
        if (!ModConfig.getInstance().enableRightStickRadialMenu || client == null || client.player == null) {
            cancel();
            return;
        }

        this.currentStickX = stickX;
        this.currentStickY = stickY;
        float mag = (float) Math.hypot(stickX, stickY);

        if (!open) {
            if (mag >= 0.38f) {
                open = true;
                switchedWhileOpen = false;
                int sliceCount = getSliceCount(currentPage);
                int slice = computeSlice(stickX, stickY, sliceCount);
                setHoveredSlice(client, slice);
            }
        } else {
            if (mag >= 0.32f) {
                int sliceCount = getSliceCount(currentPage);
                int slice = computeSlice(stickX, stickY, sliceCount);
                if (slice != hoveredSlice) {
                    switchedWhileOpen = false;
                    setHoveredSlice(client, slice);
                }
            } else if (mag < 0.20f) {
                // Stick released back to center -> execute selected slice!
                int selected = hoveredSlice;
                int page = currentPage;
                open = false;
                hoveredSlice = -1;
                if (selected >= 0) {
                    executeSlice(client, page, selected);
                }
                switchedWhileOpen = false;
            }
        }
    }

    private void setHoveredSlice(Minecraft client, int newSlice) {
        if (newSlice != this.hoveredSlice) {
            this.hoveredSlice = newSlice;
            HapticFeedbackManager.getInstance().triggerHaptic(false, true, (short) 26000, 22);

            // For Hotbar 1-9, immediately switch the hotbar slot on hover for zero-latency preview
            if (currentPage == PAGE_HOTBAR && newSlice >= 0 && newSlice < 9 && client.player != null) {
                client.player.getInventory().setSelectedSlot(newSlice);
            }
        }
    }

    private int getSliceCount(int page) {
        return (page == PAGE_HOTBAR) ? 9 : 8;
    }

    /**
     * Computes slice index (0 at 12 o'clock / Top, increasing clockwise).
     */
    private int computeSlice(float x, float y, int sliceCount) {
        double angle = Math.atan2(y, x) + Math.PI / 2.0; // 0 at top (-Y)
        if (angle < 0) angle += Math.PI * 2.0;
        double step = (Math.PI * 2.0) / sliceCount;
        int idx = (int) Math.floor((angle + step / 2.0) / step) % sliceCount;
        return idx;
    }

    private void executeSlice(Minecraft client, int page, int slice) {
        if (client == null || client.player == null) return;

        if (page == PAGE_HOTBAR) {
            if (slice >= 0 && slice < 9) {
                client.player.getInventory().setSelectedSlot(slice);
                HapticFeedbackManager.getInstance().triggerHaptic(false, true, (short) 34000, 30);
            }
            return;
        }

        VirtualKeyboard.playClickSound();
        HapticFeedbackManager.getInstance().triggerHaptic(false, true, (short) 38000, 35);

        if (page == PAGE_F_KEYS) {
            switch (slice) {
                case 0 -> clickKeyMapping(client.options.keyToggleGui); // F1
                case 1 -> Screenshot.grab(client, false); // F2
                case 2 -> client.debugEntries.toggleDebugOverlay(); // F3
                case 3 -> client.getDebugOverlay().toggleFpsCharts(); // F3+2
                case 4 -> client.options.setCameraType(client.options.getCameraType().cycle()); // F5
                case 5 -> client.options.smoothCamera = !client.options.smoothCamera; // F8
                case 6 -> triggerDebugKey(client, client.options.keyDebugSwitchGameMode); // F3+F4
                case 7 -> triggerDebugKey(client, client.options.keyDebugDebugOptions); // F3+F6
            }
        } else if (page == PAGE_F3_COMBOS) {
            switch (slice) {
                case 0 -> triggerDebugKey(client, client.options.keyDebugShowChunkBorders); // F3+G
                case 1 -> triggerDebugKey(client, client.options.keyDebugShowHitboxes); // F3+B
                case 2 -> triggerDebugKey(client, client.options.keyDebugShowAdvancedTooltips); // F3+H
                case 3 -> triggerDebugKey(client, client.options.keyDebugReloadChunk); // F3+A
                case 4 -> triggerDebugKey(client, client.options.keyDebugSpectate); // F3+N
                case 5 -> triggerDebugKey(client, client.options.keyDebugClearChat); // F3+D
                case 6 -> triggerDebugKey(client, client.options.keyDebugCopyLocation); // F3+C
                case 7 -> triggerDebugKey(client, client.options.keyDebugReloadResourcePacks); // F3+T
            }
        }
    }

    private void clickKeyMapping(KeyMapping mapping) {
        if (mapping == null) return;
        try {
            KeyMappingAccessor accessor = (KeyMappingAccessor) mapping;
            accessor.setClickCount(accessor.getClickCount() + 1);
        } catch (Throwable ignored) {}
    }

    private void triggerDebugKey(Minecraft client, KeyMapping debugKey) {
        if (client == null || client.keyboardHandler == null || debugKey == null) return;
        try {
            int keyVal = ((KeyMappingAccessor) debugKey).getKey().getValue();
            KeyEvent event = new KeyEvent(keyVal, keyVal, 0);
            ((KeyboardHandlerAccessor) client.keyboardHandler).invokeHandleDebugKeys(event);
        } catch (Throwable t) {
            SteamDeckEssentials.LOGGER.warn("Failed to invoke debug key {}", debugKey.getName(), t);
        }
    }

    /**
     * Renders the Radial Menu overlay or the brief page-switch HUD toast.
     */
    public void render(GuiGraphicsExtractor graphics, int screenWidth, int screenHeight) {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.player == null) return;
        Font font = client.font;

        if (!open) {
            // Render brief toast when switching pages with R3 while wheel is closed
            long elapsed = System.currentTimeMillis() - lastPageSwitchToastTime;
            if (elapsed < 1800L) {
                String toast = String.format("🎡 Radial [%d/3]: %s", currentPage + 1, PAGE_TITLES[currentPage]);
                int tw = font.width(toast) + 16;
                int tx = (screenWidth - tw) / 2;
                int ty = screenHeight - 54;
                graphics.fill(tx, ty, tx + tw, ty + 18, 0xDD0D1117);
                graphics.outline(tx, ty, tw, 18, 0xFF00E5FF);
                graphics.centeredText(font, toast, screenWidth / 2, ty + 5, 0xFF00FFCC);
            }
            return;
        }

        int cx = screenWidth / 2;
        int cy = screenHeight / 2;

        // Subtle backdrop vignette around center
        graphics.fill(cx - 115, cy - 95, cx + 115, cy + 95, 0x55000000);

        int sliceCount = getSliceCount(currentPage);
        double step = (Math.PI * 2.0) / sliceCount;
        int radius = (currentPage == PAGE_HOTBAR) ? 68 : 72;

        // 1. Render all Radial Slices around the circle
        for (int i = 0; i < sliceCount; i++) {
            double angle = -Math.PI / 2.0 + i * step;
            int sx = cx + (int) Math.round(Math.cos(angle) * radius);
            int sy = cy + (int) Math.round(Math.sin(angle) * radius);
            boolean hovered = (i == hoveredSlice);

            if (currentPage == PAGE_HOTBAR) {
                renderHotbarSlice(graphics, font, client, i, sx, sy, hovered);
            } else {
                String[] info = (currentPage == PAGE_F_KEYS) ? F_KEYS_LABELS[i] : F3_COMBO_LABELS[i];
                renderActionSlice(graphics, font, info[0], info[1], sx, sy, hovered);
            }
        }

        // 2. Render Center Hub
        int hubW = 104;
        int hubH = 42;
        int hubX = cx - hubW / 2;
        int hubY = cy - hubH / 2;

        graphics.fill(hubX, hubY, hubX + hubW, hubY + hubH, 0xF00D1117);
        graphics.outline(hubX, hubY, hubW, hubH, 0xFF00E5FF);

        // Page title & indicator
        String pageHeader = String.format("[%d/3] %s", currentPage + 1, PAGE_TITLES[currentPage]);
        graphics.centeredText(font, pageHeader, cx, hubY + 5, 0xFF00E5FF);

        // Hovered slice full description
        String hoverDesc = getHoveredDescription(client);
        String trimmedDesc = font.plainSubstrByWidth(hoverDesc, hubW - 8);
        graphics.centeredText(font, trimmedDesc, cx, hubY + 17, 0xFFFFCC00);

        // R3 Hint
        graphics.centeredText(font, "[R3] Menü wechseln", cx, hubY + 29, 0xFF88AAFF);

        // 3. Render Stick Direction Pointer Dot
        int dotX = cx + Math.round(currentStickX * 34.0f);
        int dotY = cy + Math.round(currentStickY * 34.0f);
        graphics.fill(dotX - 2, dotY - 2, dotX + 3, dotY + 3, 0xFF00FFCC);
    }

    private void renderHotbarSlice(GuiGraphicsExtractor graphics, Font font, Minecraft client,
                                   int slot, int sx, int sy, boolean hovered) {
        int boxW = hovered ? 32 : 28;
        int boxH = hovered ? 32 : 28;
        int bx = sx - boxW / 2;
        int by = sy - boxH / 2;

        boolean currentSelected = (client.player.getInventory().getSelectedSlot() == slot);
        int bg = hovered ? 0xFF1E3A4A : (currentSelected ? 0xDD182B36 : 0xDD12171F);
        int border = hovered ? 0xFF00FFCC : (currentSelected ? 0xFF00E5FF : 0x6600E5FF);

        graphics.fill(bx, by, bx + boxW, by + boxH, bg);
        graphics.outline(bx, by, boxW, boxH, border);

        ItemStack stack = client.player.getInventory().getItem(slot);
        if (!stack.isEmpty()) {
            graphics.item(stack, sx - 8, sy - 6);
            graphics.itemDecorations(font, stack, sx - 8, sy - 6);
        }

        // Slot number badge (1-9)
        graphics.text(font, String.valueOf(slot + 1), bx + 2, by + 2, hovered ? 0xFF00FFCC : 0xFFAAAAAA);
    }

    private void renderActionSlice(GuiGraphicsExtractor graphics, Font font,
                                   String keyBadge, String shortLabel, int sx, int sy, boolean hovered) {
        int boxW = hovered ? 58 : 54;
        int boxH = hovered ? 26 : 24;
        int bx = sx - boxW / 2;
        int by = sy - boxH / 2;

        int bg = hovered ? 0xFF1E3A4A : 0xDD12171F;
        int border = hovered ? 0xFF00FFCC : 0x6600E5FF;

        graphics.fill(bx, by, bx + boxW, by + boxH, bg);
        graphics.outline(bx, by, boxW, boxH, border);

        graphics.centeredText(font, keyBadge, sx, by + 3, hovered ? 0xFF00FFCC : 0xFF00E5FF);
        graphics.centeredText(font, shortLabel, sx, by + 13, hovered ? 0xFFFFFFFF : 0xFFCCCCCC);
    }

    private String getHoveredDescription(Minecraft client) {
        if (hoveredSlice < 0) return "Stick bewegen...";
        if (currentPage == PAGE_HOTBAR) {
            ItemStack stack = client.player.getInventory().getItem(hoveredSlice);
            if (stack.isEmpty()) {
                return "Slot " + (hoveredSlice + 1) + ": Leer";
            }
            return "Slot " + (hoveredSlice + 1) + ": " + stack.getHoverName().getString();
        } else if (currentPage == PAGE_F_KEYS) {
            return F_KEYS_LABELS[hoveredSlice][2];
        } else {
            return F3_COMBO_LABELS[hoveredSlice][2];
        }
    }
}
