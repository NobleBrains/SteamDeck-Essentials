package com.steamdeck.mc.gui;

import com.steamdeck.mc.input.DeckButton;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.function.Function;

/**
 * Own line-art of the supported controllers and small button glyphs, drawn only with rectangles
 * (Minecraft has no vector drawing). Style follows the Steam Input configurator: white outlines on a
 * dark background. No Valve assets are used.
 *
 * <p>All coordinates are in the 200 x 160 drawing box of {@link ControllerLayoutScreen}.</p>
 */
public final class ControllerArt {
    public enum Variant {
        /** Steam Deck: wide handheld with a screen in the middle. */
        HANDHELD("Steam Deck"),
        /** Steam Controller (2026) and other gamepads: two grips, two trackpads. */
        PAD("Steam Controller"),
        /** Sony PS5 DualSense / DualSense Edge: center touchpad, symmetrical sticks. */
        PS5("PS5 DualSense");

        private final String label;

        Variant(String label) {
            this.label = label;
        }

        public String getLabel() {
            return label;
        }
    }

    public static final int BOX_W = 200;
    public static final int BOX_H = 160;

    private static final int LINE = 0xFFFFFFFF;
    private static final int DIM_LINE = 0xFF8B929A;

    private ControllerArt() {}

    // ------------------------------------------------------------------------------------------
    // Primitive shapes (scanline fills)
    // ------------------------------------------------------------------------------------------

    /** Filled rounded rectangle. */
    static void roundRect(GuiGraphicsExtractor g, int x, int y, int w, int h, int r, int col) {
        if (w <= 0 || h <= 0) return;
        r = Math.max(0, Math.min(r, Math.min(w, h) / 2));
        if (r == 0) {
            g.fill(x, y, x + w, y + h, col);
            return;
        }
        g.fill(x, y + r, x + w, y + h - r, col);
        for (int i = 0; i < r; i++) {
            double dy = r - i - 0.5;
            int inset = (int) Math.round(r - Math.sqrt(Math.max(0.0, r * r - dy * dy)));
            g.fill(x + inset, y + i, x + w - inset, y + i + 1, col);
            g.fill(x + inset, y + h - 1 - i, x + w - inset, y + h - i, col);
        }
    }

    /** Rounded rectangle outline with thickness {@code t}. */
    static void roundRectOutline(GuiGraphicsExtractor g, int x, int y, int w, int h, int r, int t, int col, int bg) {
        roundRect(g, x, y, w, h, r, col);
        roundRect(g, x + t, y + t, w - 2 * t, h - 2 * t, Math.max(0, r - t), bg);
    }

    /** Filled circle. */
    static void circle(GuiGraphicsExtractor g, int cx, int cy, int r, int col) {
        for (int dy = -r; dy < r; dy++) {
            double yy = dy + 0.5;
            int half = (int) Math.round(Math.sqrt(Math.max(0.0, r * r - yy * yy)));
            if (half > 0) g.fill(cx - half, cy + dy, cx + half, cy + dy + 1, col);
        }
    }

    static void ring(GuiGraphicsExtractor g, int cx, int cy, int r, int t, int col, int bg) {
        circle(g, cx, cy, r, col);
        circle(g, cx, cy, r - t, bg);
    }

    // ------------------------------------------------------------------------------------------
    // Controller drawings
    // ------------------------------------------------------------------------------------------

    /**
     * Draws the controller. {@code state} returns for each button how to highlight it:
     * 0 = normal, 1 = pressed right now, 2 = focused in the configurator.
     */
    public static void draw(GuiGraphicsExtractor g, Variant variant, int x0, int y0, int bg, int accent,
                            Function<DeckButton, Integer> state) {
        if (com.steamdeck.mc.config.ModConfig.getInstance().originalControllerArt) {
            drawOriginal(g, variant, x0, y0, accent, state);
            return;
        }
        if (variant == Variant.HANDHELD) {
            drawHandheld(g, x0, y0, bg, accent, state);
        } else {
            drawPad(g, x0, y0, bg, accent, state);
        }
    }

    // ------------------------------------------------------------------------------------------
    // Pixel-art versions of Steam's own controller outlines (controller_config_controller_*.svg,
    // rasterised to white 1-bit pixels at the drawing size). Hot spots below are the button
    // positions taken from the SVG geometry, in texture pixels.
    // ------------------------------------------------------------------------------------------

    private static final net.minecraft.resources.Identifier TEX_PAD =
            net.minecraft.resources.Identifier.fromNamespaceAndPath("steamdeck_essentials", "textures/gui/controller_triton.png");
    private static final net.minecraft.resources.Identifier TEX_DECK =
            net.minecraft.resources.Identifier.fromNamespaceAndPath("steamdeck_essentials", "textures/gui/controller_deck.png");
    private static final net.minecraft.resources.Identifier TEX_PS5 =
            net.minecraft.resources.Identifier.fromNamespaceAndPath("steamdeck_essentials", "textures/gui/controller_ps5.png");

    /** kind 0 = circle (a, b = centre, c = radius), 1 = rect (a, b, c, d = x, y, w, h). */
    private record Hot(DeckButton button, int kind, int a, int b, int c, int d) {
        static Hot circle(DeckButton b, int cx, int cy, int r) { return new Hot(b, 0, cx, cy, r, 0); }
        static Hot rect(DeckButton b, int x, int y, int w, int h) { return new Hot(b, 1, x, y, w, h); }
    }

    /** Steam Controller (2026), texture 200 x 140 (SVG 456 x 320, scale 0.4386). */
    private static final Hot[] PAD_HOTS = {
            Hot.circle(DeckButton.L3, 71, 48, 15), Hot.circle(DeckButton.LEFT_STICK_TOUCH, 71, 48, 15),
            Hot.circle(DeckButton.R3, 129, 48, 15), Hot.circle(DeckButton.RIGHT_STICK_TOUCH, 129, 48, 15),
            Hot.circle(DeckButton.Y, 161, 22, 6), Hot.circle(DeckButton.B, 172, 33, 6),
            Hot.circle(DeckButton.X, 150, 33, 6), Hot.circle(DeckButton.A, 161, 45, 6),
            Hot.circle(DeckButton.STEAM, 100, 33, 6),
            Hot.rect(DeckButton.LEFT_TRACKPAD, 43, 66, 38, 38), Hot.rect(DeckButton.RIGHT_TRACKPAD, 119, 66, 38, 38),
            Hot.rect(DeckButton.DPAD_UP, 33, 18, 12, 9), Hot.rect(DeckButton.DPAD_DOWN, 33, 38, 12, 9),
            Hot.rect(DeckButton.DPAD_LEFT, 24, 26, 9, 13), Hot.rect(DeckButton.DPAD_RIGHT, 45, 26, 9, 13),
            Hot.rect(DeckButton.SELECT, 58, 17, 14, 6), Hot.rect(DeckButton.START, 128, 17, 14, 6),
            Hot.rect(DeckButton.QAM, 91, 81, 18, 7),
            Hot.rect(DeckButton.LB, 25, 1, 31, 9), Hot.rect(DeckButton.RB, 144, 1, 31, 9),
    };

    /** Steam Deck, texture 200 x 81 (SVG 449 x 181, scale 0.4454). */
    private static final Hot[] DECK_HOTS = {
            Hot.circle(DeckButton.L3, 31, 18, 6), Hot.circle(DeckButton.LEFT_STICK_TOUCH, 31, 18, 6),
            Hot.circle(DeckButton.R3, 168, 18, 6), Hot.circle(DeckButton.RIGHT_STICK_TOUCH, 168, 18, 6),
            Hot.circle(DeckButton.Y, 187, 8, 3), Hot.circle(DeckButton.X, 181, 14, 3),
            Hot.circle(DeckButton.B, 193, 14, 3), Hot.circle(DeckButton.A, 187, 20, 3),
            Hot.rect(DeckButton.LEFT_TRACKPAD, 18, 29, 22, 23), Hot.rect(DeckButton.RIGHT_TRACKPAD, 159, 29, 23, 23),
            Hot.rect(DeckButton.DPAD_UP, 8, 6, 7, 6), Hot.rect(DeckButton.DPAD_DOWN, 8, 17, 7, 6),
            Hot.rect(DeckButton.DPAD_LEFT, 4, 11, 6, 7), Hot.rect(DeckButton.DPAD_RIGHT, 15, 11, 6, 7),
            Hot.rect(DeckButton.SELECT, 19, 5, 9, 4), Hot.rect(DeckButton.START, 172, 5, 9, 4),
            Hot.rect(DeckButton.STEAM, 29, 55, 11, 5), Hot.rect(DeckButton.QAM, 159, 55, 11, 5),
            Hot.rect(DeckButton.LB, 3, -1, 28, 4), Hot.rect(DeckButton.RB, 169, -1, 28, 4),
    };

    /** Sony PS5 DualSense, texture 200 x 135 (from controller_config_controller_ps5.png). */
    private static final Hot[] PS5_HOTS = {
            Hot.circle(DeckButton.L3, 71, 61, 13), Hot.circle(DeckButton.LEFT_STICK_TOUCH, 71, 61, 13),
            Hot.circle(DeckButton.R3, 129, 61, 13), Hot.circle(DeckButton.RIGHT_STICK_TOUCH, 129, 61, 13),
            Hot.circle(DeckButton.Y, 161, 22, 6), Hot.circle(DeckButton.B, 173, 34, 6),
            Hot.circle(DeckButton.X, 149, 34, 6), Hot.circle(DeckButton.A, 161, 46, 6),
            Hot.circle(DeckButton.STEAM, 100, 62, 6),
            Hot.rect(DeckButton.RIGHT_TRACKPAD, 59, 4, 82, 38),
            Hot.rect(DeckButton.DPAD_UP, 34, 20, 10, 9), Hot.rect(DeckButton.DPAD_DOWN, 34, 38, 10, 9),
            Hot.rect(DeckButton.DPAD_LEFT, 25, 29, 9, 10), Hot.rect(DeckButton.DPAD_RIGHT, 44, 29, 9, 10),
            Hot.rect(DeckButton.SELECT, 54, 15, 6, 9), Hot.rect(DeckButton.START, 140, 15, 6, 9),
            Hot.rect(DeckButton.QAM, 96, 73, 8, 5),
            Hot.rect(DeckButton.LB, 22, 1, 26, 8), Hot.rect(DeckButton.RB, 152, 1, 26, 8),
    };

    private static void drawOriginal(GuiGraphicsExtractor g, Variant variant, int x0, int y0, int accent,
                                     Function<DeckButton, Integer> state) {
        boolean deck = variant == Variant.HANDHELD;
        boolean ps5 = variant == Variant.PS5;
        int texW = 200;
        int texH = deck ? 81 : (ps5 ? 135 : 140);
        int tx = x0;
        int ty = deck ? y0 + 40 : (ps5 ? y0 + 10 : y0 + 8);
        int bg = 0xFF0E141B;

        Hot[] hots = deck ? DECK_HOTS : (ps5 ? PS5_HOTS : PAD_HOTS);
        net.minecraft.resources.Identifier tex = deck ? TEX_DECK : (ps5 ? TEX_PS5 : TEX_PAD);

        // 1. Highlights underneath the outline (focused = accent, pressed = half accent)
        for (Hot h : hots) {
            int st = state.apply(h.button());
            if (st == 0) continue;
            int col = st == 2 ? accent : (accent & 0x00FFFFFF) | 0x77000000;
            if (h.kind() == 0) circle(g, tx + h.a(), ty + h.b(), h.c(), col);
            else roundRect(g, tx + h.a(), ty + h.b(), h.c(), h.d(), 2, col);
        }

        // 2. The outline itself (white pixels, transparent elsewhere)
        g.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, tex,
                tx, ty, 0.0f, 0.0f, texW, texH, texW, texH);

        // 3. 3x3 grid on the left pad in grid mode (dual-pad controllers only)
        if (!ps5 && leftGrid()) {
            Hot pad = null;
            for (Hot h : hots) if (h.button() == DeckButton.LEFT_TRACKPAD) pad = h;
            for (int k = 1; k < 3 && pad != null; k++) {
                g.fill(tx + pad.a() + pad.c() * k / 3, ty + pad.b() + 2, tx + pad.a() + pad.c() * k / 3 + 1, ty + pad.b() + pad.d() - 2, 0xFF8B929A);
                g.fill(tx + pad.a() + 2, ty + pad.b() + pad.d() * k / 3, tx + pad.a() + pad.c() - 2, ty + pad.b() + pad.d() * k / 3 + 1, 0xFF8B929A);
            }
        }

        // 3b. Live finger position dot on touched trackpad(s)
        var input = com.steamdeck.mc.input.SteamDeckInputManager.getInstance();
        for (int side = 0; side < 2; side++) {
            if (ps5 && side == 0) continue;
            if (!input.isTrackpadTouched(side)) continue;
            DeckButton padBtn = side == 0 ? DeckButton.LEFT_TRACKPAD : DeckButton.RIGHT_TRACKPAD;
            for (Hot h : hots) {
                if (h.button() == padBtn) {
                    int fx = tx + h.a() + 2 + Math.round(input.getTrackpadX(side) * Math.max(1, h.c() - 4));
                    int fy = ty + h.b() + 2 + Math.round(input.getTrackpadY(side) * Math.max(1, h.d() - 4));
                    circle(g, fx, fy, 3, accent);
                    circle(g, fx, fy, 1, 0xFFFFFFFF);
                    break;
                }
            }
        }

        // 4. Inputs not visible from the front: triggers above, back buttons below (own small tabs)
        if (deck) {
            part(g, tx + 6, ty - 8, 22, 5, 2, state.apply(DeckButton.LT), accent, bg);
            part(g, tx + 172, ty - 8, 22, 5, 2, state.apply(DeckButton.RT), accent, bg);
            part(g, tx + 28, ty + 83, 9, 5, 2, state.apply(DeckButton.L5), accent, bg);
            part(g, tx + 39, ty + 83, 9, 5, 2, state.apply(DeckButton.L4), accent, bg);
            part(g, tx + 152, ty + 83, 9, 5, 2, state.apply(DeckButton.R4), accent, bg);
            part(g, tx + 163, ty + 83, 9, 5, 2, state.apply(DeckButton.R5), accent, bg);
        } else if (ps5) {
            part(g, tx + 23, ty - 7, 24, 5, 2, state.apply(DeckButton.LT), accent, bg);
            part(g, tx + 153, ty - 7, 24, 5, 2, state.apply(DeckButton.RT), accent, bg);
            part(g, tx + 42, ty + 137, 10, 5, 2, state.apply(DeckButton.L4), accent, bg);
            part(g, tx + 148, ty + 137, 10, 5, 2, state.apply(DeckButton.R4), accent, bg);
        } else {
            part(g, tx + 28, ty - 7, 25, 5, 2, state.apply(DeckButton.LT), accent, bg);
            part(g, tx + 147, ty - 7, 25, 5, 2, state.apply(DeckButton.RT), accent, bg);
            part(g, tx + 20, ty + 142, 9, 5, 2, state.apply(DeckButton.L5), accent, bg);
            part(g, tx + 31, ty + 142, 9, 5, 2, state.apply(DeckButton.L4), accent, bg);
            part(g, tx + 160, ty + 142, 9, 5, 2, state.apply(DeckButton.R4), accent, bg);
            part(g, tx + 171, ty + 142, 9, 5, 2, state.apply(DeckButton.R5), accent, bg);
        }
    }

    private static int fillFor(int st, int accent, int bg) {
        return switch (st) {
            case 2 -> accent;
            case 1 -> (accent & 0x00FFFFFF) | 0x88000000;
            default -> bg;
        };
    }

    private static void part(GuiGraphicsExtractor g, int x, int y, int w, int h, int r, int st, int accent, int bg) {
        roundRect(g, x, y, w, h, r, st == 2 ? accent : LINE);
        roundRect(g, x + 2, y + 2, w - 4, h - 4, Math.max(0, r - 2), st == 2 ? accent : fillFor(st, accent, bg));
    }

    private static void stick(GuiGraphicsExtractor g, int cx, int cy, int r, int stClick, int stTouch, int accent, int bg) {
        int outer = stTouch == 2 ? accent : (stTouch == 1 ? (accent & 0x00FFFFFF) | 0xCC000000 : LINE);
        ring(g, cx, cy, r, 2, outer, bg);
        circle(g, cx, cy, r - 4, stClick == 2 ? accent : (stClick == 1 ? (accent & 0x00FFFFFF) | 0x88000000 : DIM_LINE));
        circle(g, cx, cy, r - 6, stClick == 2 ? accent : bg);
    }

    private static void dpad(GuiGraphicsExtractor g, int cx, int cy, int accent, int bg, Function<DeckButton, Integer> state) {
        int a = 5; // arm width
        int l = 7; // arm length
        part(g, cx - a / 2 - 1, cy - l - 2, a + 2, l, 1, state.apply(DeckButton.DPAD_UP), accent, bg);
        part(g, cx - a / 2 - 1, cy + 3, a + 2, l, 1, state.apply(DeckButton.DPAD_DOWN), accent, bg);
        part(g, cx - l - 2, cy - a / 2 - 1, l, a + 2, 1, state.apply(DeckButton.DPAD_LEFT), accent, bg);
        part(g, cx + 3, cy - a / 2 - 1, l, a + 2, 1, state.apply(DeckButton.DPAD_RIGHT), accent, bg);
    }

    private static void faceButtons(GuiGraphicsExtractor g, Font font, int cx, int cy, int accent, int bg,
                                    Function<DeckButton, Integer> state) {
        face(g, font, cx, cy - 8, "Y", state.apply(DeckButton.Y), accent, bg);
        face(g, font, cx, cy + 8, "A", state.apply(DeckButton.A), accent, bg);
        face(g, font, cx - 8, cy, "X", state.apply(DeckButton.X), accent, bg);
        face(g, font, cx + 8, cy, "B", state.apply(DeckButton.B), accent, bg);
    }

    private static void face(GuiGraphicsExtractor g, Font font, int cx, int cy, String letter, int st, int accent, int bg) {
        ring(g, cx, cy, 5, 1, st == 2 ? accent : LINE, fillFor(st, accent, bg));
    }

    private static void trackpad(GuiGraphicsExtractor g, int x, int y, int w, int h, int st, int accent, int bg) {
        trackpad(g, x, y, w, h, st, accent, bg, false);
    }

    private static void trackpad(GuiGraphicsExtractor g, int x, int y, int w, int h, int st, int accent, int bg, boolean grid) {
        roundRectOutline(g, x, y, w, h, 5, 2, st == 2 ? accent : LINE, fillFor(st, accent, bg));
        int c = st == 2 ? bg : 0xFF3D4450;
        if (grid) {
            // 3x3 grid mode: show the nine fields
            int c2 = st == 2 ? bg : 0xFF8B929A;
            for (int k = 1; k < 3; k++) {
                g.fill(x + w * k / 3, y + 3, x + w * k / 3 + 1, y + h - 3, c2);
                g.fill(x + 3, y + h * k / 3, x + w - 3, y + h * k / 3 + 1, c2);
            }
            return;
        }
        // Subtle centre cross like the haptic pad markings
        g.fill(x + w / 2, y + 5, x + w / 2 + 1, y + h - 5, c);
        g.fill(x + 5, y + h / 2, x + w - 5, y + h / 2 + 1, c);
    }

    private static boolean leftGrid() {
        return "GRID_3X3".equals(com.steamdeck.mc.config.ModConfig.getInstance().leftTrackpadMode);
    }

    private static void drawHandheld(GuiGraphicsExtractor g, int x0, int y0, int bg, int accent,
                                     Function<DeckButton, Integer> state) {
        net.minecraft.client.gui.Font font = net.minecraft.client.Minecraft.getInstance().font;
        int bx = x0 + 2;
        int by = y0 + 46;
        int bw = 196;
        int bh = 78;

        // Triggers and bumpers behind the body
        part(g, bx + 14, by - 15, 30, 9, 3, state.apply(DeckButton.LT), accent, bg);
        part(g, bx + bw - 44, by - 15, 30, 9, 3, state.apply(DeckButton.RT), accent, bg);
        part(g, bx + 8, by - 7, 42, 9, 3, state.apply(DeckButton.LB), accent, bg);
        part(g, bx + bw - 50, by - 7, 42, 9, 3, state.apply(DeckButton.RB), accent, bg);
        // Back grip buttons, shown as tabs under the body
        part(g, bx + 22, by + bh - 2, 14, 8, 2, state.apply(DeckButton.L5), accent, bg);
        part(g, bx + 40, by + bh - 2, 14, 8, 2, state.apply(DeckButton.L4), accent, bg);
        part(g, bx + bw - 54, by + bh - 2, 14, 8, 2, state.apply(DeckButton.R4), accent, bg);
        part(g, bx + bw - 36, by + bh - 2, 14, 8, 2, state.apply(DeckButton.R5), accent, bg);

        roundRectOutline(g, bx, by, bw, bh, 18, 2, LINE, bg);

        // Screen
        roundRectOutline(g, bx + 58, by + 8, 80, 50, 3, 2, DIM_LINE, bg);
        g.fill(bx + 62, by + 12, bx + 134, by + 54, 0xFF151B23);

        dpad(g, bx + 18, by + 22, accent, bg, state);
        stick(g, bx + 43, by + 22, 9, state.apply(DeckButton.L3), state.apply(DeckButton.LEFT_STICK_TOUCH), accent, bg);
        faceButtons(g, font, bx + bw - 18, by + 22, accent, bg, state);
        stick(g, bx + bw - 43, by + 22, 9, state.apply(DeckButton.R3), state.apply(DeckButton.RIGHT_STICK_TOUCH), accent, bg);

        trackpad(g, bx + 24, by + 40, 26, 26, state.apply(DeckButton.LEFT_TRACKPAD), accent, bg, leftGrid());
        trackpad(g, bx + bw - 50, by + 40, 26, 26, state.apply(DeckButton.RIGHT_TRACKPAD), accent, bg);

        // View / Menu next to the screen, Steam / QAM below it
        part(g, bx + 50, by + 6, 6, 5, 1, state.apply(DeckButton.SELECT), accent, bg);
        part(g, bx + bw - 56, by + 6, 6, 5, 1, state.apply(DeckButton.START), accent, bg);
        part(g, bx + 53, by + 62, 8, 6, 2, state.apply(DeckButton.STEAM), accent, bg);
        part(g, bx + bw - 61, by + 62, 8, 6, 2, state.apply(DeckButton.QAM), accent, bg);
    }

    private static void drawPad(GuiGraphicsExtractor g, int x0, int y0, int bg, int accent,
                                Function<DeckButton, Integer> state) {
        net.minecraft.client.gui.Font font = net.minecraft.client.Minecraft.getInstance().font;
        int cx = x0 + BOX_W / 2;

        // Shoulders behind the body
        part(g, x0 + 30, y0 + 18, 34, 10, 3, state.apply(DeckButton.LT), accent, bg);
        part(g, x0 + BOX_W - 64, y0 + 18, 34, 10, 3, state.apply(DeckButton.RT), accent, bg);
        part(g, x0 + 24, y0 + 27, 46, 9, 3, state.apply(DeckButton.LB), accent, bg);
        part(g, x0 + BOX_W - 70, y0 + 27, 46, 9, 3, state.apply(DeckButton.RB), accent, bg);
        // Back buttons as tabs at the grip ends
        part(g, x0 + 22, y0 + 142, 13, 8, 2, state.apply(DeckButton.L5), accent, bg);
        part(g, x0 + 38, y0 + 142, 13, 8, 2, state.apply(DeckButton.L4), accent, bg);
        part(g, x0 + BOX_W - 51, y0 + 142, 13, 8, 2, state.apply(DeckButton.R4), accent, bg);
        part(g, x0 + BOX_W - 35, y0 + 142, 13, 8, 2, state.apply(DeckButton.R5), accent, bg);

        // Body as union of a wide top and two grips: white expanded shapes first, then the fill
        int t = 2;
        roundRect(g, x0 + 14 - t, y0 + 34 - t, 172 + 2 * t, 62 + 2 * t, 20, LINE);
        roundRect(g, x0 + 14 - t, y0 + 60 - t, 52 + 2 * t, 86 + 2 * t, 22, LINE);
        roundRect(g, x0 + BOX_W - 66 - t, y0 + 60 - t, 52 + 2 * t, 86 + 2 * t, 22, LINE);
        roundRect(g, x0 + 14, y0 + 34, 172, 62, 18, bg);
        roundRect(g, x0 + 14, y0 + 60, 52, 86, 20, bg);
        roundRect(g, x0 + BOX_W - 66, y0 + 60, 52, 86, 20, bg);

        dpad(g, x0 + 38, y0 + 56, accent, bg, state);
        stick(g, x0 + 68, y0 + 52, 9, state.apply(DeckButton.L3), state.apply(DeckButton.LEFT_STICK_TOUCH), accent, bg);
        faceButtons(g, font, x0 + BOX_W - 38, y0 + 56, accent, bg, state);
        stick(g, x0 + BOX_W - 68, y0 + 52, 9, state.apply(DeckButton.R3), state.apply(DeckButton.RIGHT_STICK_TOUCH), accent, bg);

        trackpad(g, x0 + 46, y0 + 72, 34, 32, state.apply(DeckButton.LEFT_TRACKPAD), accent, bg, leftGrid());
        trackpad(g, x0 + BOX_W - 80, y0 + 72, 34, 32, state.apply(DeckButton.RIGHT_TRACKPAD), accent, bg);

        part(g, cx - 14, y0 + 44, 8, 5, 1, state.apply(DeckButton.SELECT), accent, bg);
        part(g, cx + 6, y0 + 44, 8, 5, 1, state.apply(DeckButton.START), accent, bg);
        part(g, cx - 5, y0 + 54, 10, 8, 3, state.apply(DeckButton.STEAM), accent, bg);
        part(g, cx - 4, y0 + 66, 8, 5, 1, state.apply(DeckButton.QAM), accent, bg);
    }

    // ------------------------------------------------------------------------------------------
    // Glyphs (11 x 11) used in the binding lists
    // ------------------------------------------------------------------------------------------

    public static final int GLYPH = 11;

    /** Draws the glyph of a physical input at (x, y). */
    public static void glyph(GuiGraphicsExtractor g, Font font, DeckButton b, int x, int y) {
        glyph(g, font, b, x, y, false);
    }

    /** Draws the glyph of a physical input at (x, y), optionally with PlayStation face symbols. */
    public static void glyph(GuiGraphicsExtractor g, Font font, DeckButton b, int x, int y, boolean playstation) {
        int fg = 0xFFFFFFFF;
        int ink = 0xFF0E141B;
        if (b.gridIndex() >= 0) {
            // Mini 3x3 grid with the field filled
            int cell = b.gridIndex();
            roundRect(g, x, y, 11, 11, 2, 0xFF5A626C);
            for (int i = 0; i < 9; i++) {
                int cx = x + 1 + (i % 3) * 3;
                int cy = y + 1 + (i / 3) * 3;
                g.fill(cx, cy, cx + 3, cy + 3, i == cell ? fg : ink);
            }
            return;
        }
        switch (b) {
            case A, B, X, Y -> {
                circle(g, x + 5, y + 5, 6, fg);
                String label = playstation ? switch (b) {
                    case A -> "✕";
                    case B -> "○";
                    case X -> "□";
                    default -> "△";
                } : b.name();
                centered(g, font, label, x + 5, y + 2, ink);
            }
            case DPAD_UP, DPAD_DOWN, DPAD_LEFT, DPAD_RIGHT -> {
                g.fill(x + 4, y, x + 7, y + 11, 0xFF5A626C);
                g.fill(x, y + 4, x + 11, y + 7, 0xFF5A626C);
                switch (b) {
                    case DPAD_UP -> g.fill(x + 4, y, x + 7, y + 4, fg);
                    case DPAD_DOWN -> g.fill(x + 4, y + 7, x + 7, y + 11, fg);
                    case DPAD_LEFT -> g.fill(x, y + 4, x + 4, y + 7, fg);
                    default -> g.fill(x + 7, y + 4, x + 11, y + 7, fg);
                }
            }
            case L3, R3 -> {
                ring(g, x + 5, y + 5, 6, 1, fg, ink);
                circle(g, x + 5, y + 5, 3, fg);
            }
            case LEFT_STICK_TOUCH, RIGHT_STICK_TOUCH -> {
                ring(g, x + 5, y + 5, 6, 1, fg, ink);
                g.fill(x + 3, y + 4, x + 8, y + 5, fg);
                g.fill(x + 3, y + 6, x + 8, y + 7, fg);
            }
            case LEFT_TRACKPAD, RIGHT_TRACKPAD -> {
                roundRectOutline(g, x, y, 11, 11, 3, 1, fg, ink);
                circle(g, x + 5, y + 5, 2, fg);
            }
            case STEAM -> {
                // Steam logo-like: ring with a dot and a short arm
                ring(g, x + 5, y + 5, 6, 1, fg, ink);
                circle(g, x + 7, y + 4, 2, fg);
                g.fill(x + 2, y + 6, x + 6, y + 7, fg);
            }
            case START -> {
                roundRect(g, x, y + 1, 11, 9, 3, fg);
                g.fill(x + 3, y + 3, x + 8, y + 4, ink);
                g.fill(x + 3, y + 5, x + 8, y + 6, ink);
                g.fill(x + 3, y + 7, x + 8, y + 8, ink);
            }
            case SELECT -> {
                roundRect(g, x, y + 1, 11, 9, 3, fg);
                g.fill(x + 2, y + 3, x + 6, y + 7, ink);
                g.fill(x + 5, y + 4, x + 9, y + 8, ink);
            }
            default -> {
                // Shoulder / trigger / back buttons: rounded label
                String label = switch (b) {
                    case LB -> "L1";
                    case RB -> "R1";
                    case LT -> "L2";
                    case RT -> "R2";
                    case L4 -> "L4";
                    case R4 -> "R4";
                    case L5 -> "L5";
                    case R5 -> "R5";
                    case QAM -> "...";
                    default -> "?";
                };
                roundRect(g, x - 2, y + 1, 15, 9, 3, fg);
                centered(g, font, label, x + 5, y + 2, ink);
            }
        }
    }

    /** Tiny text, 2/3 size, centred at cx. Used inside glyphs. */
    private static void centered(GuiGraphicsExtractor g, Font font, String s, int cx, int y, int col) {
        var pose = g.pose();
        pose.pushMatrix();
        pose.translate(cx, y);
        pose.scale(0.75f, 0.75f);
        g.text(font, s, -font.width(s) / 2, 0, col, false);
        pose.popMatrix();
    }
}
