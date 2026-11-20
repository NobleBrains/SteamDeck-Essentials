package com.steamdeck.mc.gui;

import com.steamdeck.mc.config.ModConfig;
import com.steamdeck.mc.input.ActionBinding;
import com.steamdeck.mc.input.DeckAction;
import com.steamdeck.mc.input.DeckButton;
import com.steamdeck.mc.input.SteamDeckInputManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;

/**
 * In-game overlay for the left trackpad in 3x3 grid mode (like Steam's touch menu): shown while a
 * finger rests on the pad, highlights the field under the finger and shows what each field does.
 * Fields bound to a hotbar slot show the item of that slot.
 */
public final class TrackpadGridOverlay {
    private static final int CELL = 22;
    private static final int GAP = 2;

    private TrackpadGridOverlay() {}

    public static void render(GuiGraphicsExtractor g, int screenWidth, int screenHeight) {
        SteamDeckInputManager input = SteamDeckInputManager.getInstance();
        if (!input.isGridOverlayVisible()) return;
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.player == null || client.font == null) return;
        Font font = client.font;

        int size = 3 * CELL + 2 * GAP;
        int x0 = 12;
        int y0 = (screenHeight - size) / 2;
        int hover = input.getGridHoverCell();
        int pressed = input.getGridPressedCell();
        int selectedSlot = client.player.getInventory().getSelectedSlot();
        ModConfig cfg = ModConfig.getInstance();

        g.fill(x0 - 3, y0 - 3, x0 + size + 3, y0 + size + 3, 0x88000000);
        for (int i = 0; i < 9; i++) {
            int x = x0 + (i % 3) * (CELL + GAP);
            int y = y0 + (i / 3) * (CELL + GAP);
            boolean isHover = i == hover;
            boolean isPressed = i == pressed;
            int bg = isPressed ? 0xEE00E5FF : (isHover ? 0xDD3D4450 : 0xBB111822);
            g.fill(x, y, x + CELL, y + CELL, bg);
            if (isHover && !isPressed) g.outline(x, y, CELL, CELL, 0xFF00E5FF);

            ActionBinding b = cfg.getBinding(input.getCurrentActionSet(), DeckButton.gridCell(i));
            int slot = b.getAction().hotbarSlot();
            if (slot >= 0) {
                ItemStack stack = client.player.getInventory().getItem(slot);
                if (!stack.isEmpty()) {
                    g.item(stack, x + 3, y + 3);
                } else {
                    tinyCentered(g, font, String.valueOf(slot + 1), x + CELL / 2, y + 8, 0xFF8B929A);
                }
                if (slot == selectedSlot) {
                    g.fill(x + 2, y + CELL - 3, x + CELL - 2, y + CELL - 2, 0xFFFFFFFF);
                }
            } else if (b.getAction() != DeckAction.NONE) {
                String label = b.isCustomKey() ? b.getCustomNickname() : b.getAction().getLabel();
                tinyCentered(g, font, shorten(font, label), x + CELL / 2, y + 9, isPressed ? 0xFF0E141B : 0xFFFFFFFF);
            }
        }
    }

    private static String shorten(Font font, String s) {
        // Text is drawn at 0.5 scale: about 40 font pixels fit into a field
        return font.plainSubstrByWidth(s, 40);
    }

    private static void tinyCentered(GuiGraphicsExtractor g, Font font, String s, int cx, int y, int col) {
        var pose = g.pose();
        pose.pushMatrix();
        pose.translate(cx, y);
        pose.scale(0.5f, 0.5f);
        g.text(font, s, -font.width(s) / 2, 0, col, false);
        pose.popMatrix();
    }
}
