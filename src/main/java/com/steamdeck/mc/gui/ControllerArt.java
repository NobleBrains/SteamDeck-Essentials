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
        PAD("Steam Controller");

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
        if (variant == Variant.HANDHELD) {
            drawHandheld(g, x0, y0, bg, accent, state);
        } else {
            drawPad(g, x0, y0, bg, accent, state);
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

        // View / Menu next to the screen
        part(g, bx + 50, by + 6, 6, 5, 1, state.apply(DeckButton.SELECT), accent, bg);
        part(g, bx + bw - 56, by + 6, 6, 5, 1, state.apply(DeckButton.START), accent, bg);
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
    }

    // ------------------------------------------------------------------------------------------
    // Glyphs (11 x 11) used in the binding lists
    // ------------------------------------------------------------------------------------------

    public static final int GLYPH = 11;

    /** Draws the glyph of a physical input at (x, y). */
    public static void glyph(GuiGraphicsExtractor g, Font font, DeckButton b, int x, int y) {
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
                centered(g, font, b.name(), x + 5, y + 2, ink);
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
