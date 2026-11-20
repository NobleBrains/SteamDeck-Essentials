package com.steamdeck.mc.gui;

import com.mojang.blaze3d.platform.InputConstants;
import com.steamdeck.mc.config.ModConfig;
import com.steamdeck.mc.input.ActionBinding;
import com.steamdeck.mc.input.ActionSet;
import com.steamdeck.mc.input.ControllerKind;
import com.steamdeck.mc.input.DeckAction;
import com.steamdeck.mc.input.DeckButton;
import com.steamdeck.mc.input.SteamDeckInputManager;
import com.steamdeck.mc.input.gyro.GyroManager;
import com.steamdeck.mc.platform.PlatformDetector;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.sdl.SDLPower;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Controller configurator in the style of Steam Input: a drawing of the controller in the middle,
 * every input with its binding around it, one page per action set.
 *
 * <p>Controller input reaches this screen directly from {@link SteamDeckInputManager} (not through the
 * MENUS bindings), so a broken MENUS layout can never lock the player out of the configurator:
 * D-pad / left stick = move focus, A = assign, B = back, X = clear, Y = drawing, L1/R1 = action set,
 * Menu = further settings, right trackpad click = select under the cursor. Pressing any other input
 * (triggers, back buttons, stick clicks, left pad, View) jumps straight to that input.</p>
 *
 * <p>Everything is laid out on a fixed 600 x 290 canvas and scaled to the window, like the mockup.</p>
 */
public class ControllerLayoutScreen extends Screen {
    private static final int W = 600;
    private static final int H = 290;
    private static final int ROW_H = 12;

    private static final int BG = 0xFF0E141B;
    private static final int BAR = 0xFF0B0F14;
    private static final int TEXT = 0xFFDCDEDF;
    private static final int DIM = 0xFF8B929A;
    private static final int FOCUS = 0xFF3D4450;
    private static final int ACCENT = 0xFF00E5FF;
    private static final int MODE_TEXT = 0xFF8FD8FF;

    private static final int ART_X = 200;
    private static final int ART_Y = 24;

    /** Global (not per action set) modes shown as rows like Steam's "mode" lines. */
    private enum Mode { LEFT_STICK, RIGHT_STICK, GYRO, LEFT_PAD, LEFT_PAD_GRID, RIGHT_PAD }

    /** side: 0 = left column (right aligned), 1 = right column, 2 = bottom section */
    private record Item(DeckButton button, Mode mode, int x, int y, int w, int h, int side) {
        int cx() { return x + w / 2; }
        int cy() { return y + h / 2; }
        boolean contains(double px, double py) { return px >= x && px < x + w && py >= y && py < y + h; }
    }

    private record Header(String text, int x, int y, int side, int w) {}

    private final Screen parent;
    private final List<Item> items = new ArrayList<>();
    private final List<Header> headers = new ArrayList<>();
    private Item focus;
    private ActionSet set = ActionSet.IN_GAME;
    private ControllerArt.Variant variant;
    private boolean variantManual = false;

    private float scale = 1.0f;
    private float offX = 0.0f;
    private float offY = 0.0f;
    private int lastMouseX = Integer.MIN_VALUE;
    private int lastMouseY = Integer.MIN_VALUE;

    private final Picker picker = new Picker();
    private String toast = null;
    private long toastTime = 0L;

    public ControllerLayoutScreen(Screen parent) {
        super(Component.literal("Controller-Belegung"));
        this.parent = parent;
        buildLayout();
        this.focus = findItem(DeckButton.RT, null);
    }

    // ==========================================================================================
    // Layout
    // ==========================================================================================

    private void buildLayout() {
        items.clear();
        headers.clear();
        // Left column: right-aligned, glyph on the right
        column(0, 30, null, DeckButton.LT, DeckButton.LB);
        column(0, 60, null, DeckButton.L4, DeckButton.L5);
        column(0, 90, null, DeckButton.SELECT);
        column(0, 108, "Linkes Trackpad", DeckButton.LEFT_TRACKPAD, Mode.LEFT_PAD, Mode.LEFT_PAD_GRID);
        // Right column
        column(1, 30, null, DeckButton.RT, DeckButton.RB);
        column(1, 60, null, DeckButton.R4, DeckButton.R5);
        column(1, 90, null, DeckButton.START);
        column(1, 108, "Rechtes Trackpad", DeckButton.RIGHT_TRACKPAD, Mode.RIGHT_PAD);
        // Bottom: five sections
        bottom(0, "Linker Stick", Mode.LEFT_STICK, DeckButton.L3, DeckButton.LEFT_STICK_TOUCH);
        bottom(1, "Steuerkreuz", DeckButton.DPAD_UP, DeckButton.DPAD_DOWN, DeckButton.DPAD_LEFT, DeckButton.DPAD_RIGHT);
        bottom(2, "Gyro", Mode.GYRO);
        bottom(3, "Rechter Stick", Mode.RIGHT_STICK, DeckButton.R3, DeckButton.RIGHT_STICK_TOUCH);
        bottom(4, "Tasten", DeckButton.A, DeckButton.B, DeckButton.X, DeckButton.Y);
    }

    private void column(int side, int y, String header, Object... entries) {
        int x = side == 0 ? 8 : 408;
        int w = 184;
        if (header != null) {
            headers.add(new Header(header, side == 0 ? x + w - 4 : x + 4, y + 2, side, w));
            y += 11;
        }
        for (Object e : entries) {
            items.add(new Item(e instanceof DeckButton b ? b : null, e instanceof Mode m ? m : null, x, y, w, ROW_H, side));
            y += ROW_H;
        }
    }

    private void bottom(int index, String header, Object... entries) {
        int x = 6 + index * 118;
        int y = 190;
        headers.add(new Header(header, x + 3, y + 2, 2, 114));
        y += 11;
        for (Object e : entries) {
            items.add(new Item(e instanceof DeckButton b ? b : null, e instanceof Mode m ? m : null, x, y, 114, ROW_H, 2));
            y += ROW_H;
        }
    }

    private Item findItem(DeckButton button, Mode mode) {
        for (Item it : items) {
            if (button != null && it.button() == button) return it;
            if (mode != null && it.mode() == mode) return it;
        }
        return items.isEmpty() ? null : items.get(0);
    }

    private ControllerArt.Variant autoVariant() {
        ControllerKind kind = SteamDeckInputManager.getInstance().getActiveKind();
        if (kind == ControllerKind.STEAM_DECK) return ControllerArt.Variant.HANDHELD;
        if (kind == ControllerKind.STEAM_VIRTUAL && PlatformDetector.getHardware().steamDeck()) return ControllerArt.Variant.HANDHELD;
        if (kind == ControllerKind.NONE && PlatformDetector.getHardware().steamDeck()) return ControllerArt.Variant.HANDHELD;
        return ControllerArt.Variant.PAD;
    }

    // ==========================================================================================
    // Labels
    // ==========================================================================================

    private static String modeLabel(Mode mode) {
        ModConfig c = ModConfig.getInstance();
        return switch (mode) {
            case LEFT_STICK -> "Bewegen";
            case RIGHT_STICK -> c.enableRightStickRadialMenu ? "Radial-Menü (R3 blättert)" : "Kamera";
            case GYRO -> c.enableGyroAiming ? "Kamera: " + GyroManager.activationLabel(c.gyroActivation) : "Aus";
            case LEFT_PAD -> switch (c.leftTrackpadMode) {
                case "DPAD_MOVE" -> "Steuerkreuz (Bewegen)";
                case "GRID_3X3" -> "Raster 3×3 (9 Felder)";
                default -> "Taste";
            };
            case LEFT_PAD_GRID -> "Raster 3×3 belegen …";
            case RIGHT_PAD -> c.nativeTrackpadCamera ? "Kamera / Mauszeiger" : "Aus (Steam Input)";
        };
    }

    private static String modeHelp(Mode mode) {
        return switch (mode) {
            case LEFT_STICK -> "Der linke Stick bewegt die Figur (fest).";
            case RIGHT_STICK -> "A: zwischen Kamera und Radial-Menü wechseln";
            case GYRO -> "A: Aus / Nur beim Zielen / Immer / Trackpad berührt / R-Stick berührt";
            case LEFT_PAD -> "A: Taste / Steuerkreuz (gedrückt = laufen) / Raster 3×3 (9 Felder, Klick löst aus)";
            case LEFT_PAD_GRID -> "A: die 9 Felder des linken Trackpads belegen (schaltet den Raster-Modus ein)";
            case RIGHT_PAD -> "A: Kamera/Mauszeiger ohne Steam Input an/aus";
        };
    }

    private String bindingLabel(DeckButton b) {
        ActionBinding binding = ModConfig.getInstance().getBinding(set, b);
        if (binding.getAction() == DeckAction.NONE) return "--";
        return binding.getDisplayLabel();
    }

    // ==========================================================================================
    // Rendering
    // ==========================================================================================

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float tickDelta) {
        // Own solid background (no blur / panorama)
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float tickDelta) {
        if (!variantManual) variant = autoVariant();
        graphics.fill(0, 0, this.width, this.height, 0xFF000000);

        scale = Math.min(this.width / (float) W, this.height / (float) H);
        offX = (this.width - W * scale) / 2.0f;
        offY = (this.height - H * scale) / 2.0f;
        double cmx = (mouseX - offX) / scale;
        double cmy = (mouseY - offY) / scale;

        // Mouse / touch / trackpad cursor hover moves the focus (only when the pointer actually moved)
        if (mouseX != lastMouseX || mouseY != lastMouseY) {
            if (lastMouseX != Integer.MIN_VALUE) {
                if (picker.open) picker.hover(cmx, cmy);
                else if (grid.open) grid.hover(cmx, cmy);
                else {
                    for (Item it : items) {
                        if (it.contains(cmx, cmy)) {
                            focus = it;
                            break;
                        }
                    }
                }
            }
            lastMouseX = mouseX;
            lastMouseY = mouseY;
        }

        var pose = graphics.pose();
        pose.pushMatrix();
        pose.translate(offX, offY);
        pose.scale(scale, scale);

        Font font = this.font;
        graphics.fill(0, 0, W, H, BG);
        renderTopBar(graphics, font);
        renderArt(graphics);
        for (Header h : headers) {
            int col = DIM;
            if (h.side() == 0) graphics.text(font, h.text().toUpperCase(), h.x() - font.width(h.text().toUpperCase()), h.y(), col, false);
            else graphics.text(font, h.text().toUpperCase(), h.x(), h.y(), col, false);
        }
        for (Item it : items) renderItem(graphics, font, it);
        renderHelpLine(graphics, font);
        renderFooter(graphics, font);
        if (grid.open) grid.render(graphics, font);
        if (picker.open) picker.render(graphics, font);
        renderToast(graphics, font);

        pose.popMatrix();
    }

    private void renderTopBar(GuiGraphicsExtractor g, Font font) {
        g.fill(0, 0, W, 16, BAR);
        g.text(font, "Controller-Belegung", 8, 4, 0xFFFFFFFF, false);

        // Action set tabs with L1 / R1 hints
        ActionSet[] sets = ActionSet.values();
        int gap = 14;
        int total = 0;
        for (ActionSet s : sets) total += font.width(setName(s));
        total += gap * (sets.length - 1);
        int x = (W - total) / 2;
        ControllerArt.glyph(g, font, DeckButton.LB, x - 22, 3);
        for (ActionSet s : sets) {
            String name = setName(s);
            boolean active = s == set;
            g.text(font, name, x, 4, active ? 0xFFFFFFFF : DIM, false);
            if (active) g.fill(x - 1, 14, x + font.width(name) + 1, 16, ACCENT);
            x += font.width(name) + gap;
        }
        ControllerArt.glyph(g, font, DeckButton.RB, x - gap + 10, 3);

        // Connected controller + battery on the right
        SteamDeckInputManager input = SteamDeckInputManager.getInstance();
        String dev = input.hasController() ? input.getActiveKind().getLabel() : "Kein Controller";
        int[] bat = input.getControllerBattery();
        if (bat[1] >= 0 && (bat[0] == SDLPower.SDL_POWERSTATE_ON_BATTERY || bat[0] == SDLPower.SDL_POWERSTATE_CHARGING)) {
            dev += "  " + bat[1] + " %" + (bat[0] == SDLPower.SDL_POWERSTATE_CHARGING ? " ⚡" : "");
        }
        g.text(font, dev, W - 8 - font.width(dev), 4, DIM, false);

        String sub = set.getDescription();
        g.text(font, sub, (W - font.width(sub)) / 2, 19, DIM, false);
    }

    private static String setName(ActionSet s) {
        return switch (s) {
            case IN_GAME -> "Im Spiel";
            case MENUS -> "Menüs & Inventar";
            case AIMING -> "Zielen";
        };
    }

    private void renderArt(GuiGraphicsExtractor g) {
        SteamDeckInputManager input = SteamDeckInputManager.getInstance();
        DeckButton focused = grid.open ? DeckButton.LEFT_TRACKPAD
                : (focus == null ? null : (focus.button() != null ? focus.button() : modeButton(focus.mode())));
        boolean gridDown = false;
        for (int i = 0; i < 9; i++) gridDown |= input.isButtonDown(DeckButton.gridCell(i));
        final boolean anyGridDown = gridDown;
        ControllerArt.draw(g, variant, ART_X, ART_Y, BG, ACCENT, b -> {
            if (b == focused) return 2;
            if (b == DeckButton.LEFT_TRACKPAD && anyGridDown) return 1;
            return input.isButtonDown(b) ? 1 : 0;
        });
    }

    private static DeckButton modeButton(Mode m) {
        if (m == null) return null;
        return switch (m) {
            case LEFT_STICK -> DeckButton.LEFT_STICK_TOUCH;
            case RIGHT_STICK -> DeckButton.RIGHT_STICK_TOUCH;
            case LEFT_PAD, LEFT_PAD_GRID -> DeckButton.LEFT_TRACKPAD;
            case RIGHT_PAD -> DeckButton.RIGHT_TRACKPAD;
            case GYRO -> null;
        };
    }

    private void renderItem(GuiGraphicsExtractor g, Font font, Item it) {
        boolean focused = it == focus && !picker.open && !grid.open;
        if (focused) {
            g.fill(it.x(), it.y(), it.x() + it.w(), it.y() + it.h(), FOCUS);
        }
        String label;
        int col;
        if (it.mode() != null) {
            label = modeLabel(it.mode());
            col = MODE_TEXT;
        } else {
            label = bindingLabel(it.button());
            col = "--".equals(label) ? DIM : TEXT;
        }
        int ty = it.y() + 2;
        int gy = it.y() + 1;
        boolean hasGlyph = it.button() != null;
        if (it.side() == 0) {
            int gx = it.x() + it.w() - 4 - ControllerArt.GLYPH;
            if (hasGlyph) ControllerArt.glyph(g, font, it.button(), gx, gy);
            int right = hasGlyph ? gx - 6 : it.x() + it.w() - 4;
            String s = font.plainSubstrByWidth(label, right - it.x() - 4);
            g.text(font, s, right - font.width(s), ty, col, false);
        } else {
            int gx = it.x() + 4;
            if (hasGlyph) ControllerArt.glyph(g, font, it.button(), gx, gy);
            int left = hasGlyph ? gx + ControllerArt.GLYPH + 6 : it.x() + 4;
            String s = font.plainSubstrByWidth(label, it.x() + it.w() - left - 2);
            g.text(font, s, left, ty, col, false);
        }
    }

    private void renderHelpLine(GuiGraphicsExtractor g, Font font) {
        if (focus == null || picker.open || grid.open) return;
        String help;
        if (focus.mode() != null) {
            help = modeHelp(focus.mode()) + "  •  gilt für alle Action-Sets";
        } else {
            ActionBinding b = ModConfig.getInstance().getBinding(set, focus.button());
            help = focus.button().getDisplayName() + ": " + (b.getAction() == DeckAction.NONE ? "nicht belegt"
                    : (b.isCustomKey() ? "Tastatur-Taste " + ActionBinding.getKeyLabel(b.getCustomKeyCode()) : b.getAction().getDescription()))
                    + (b.hasHaptic() ? "  •  Haptik" : "") + (b.hasRepeat() ? "  •  Halten wiederholt" : "");
        }
        g.text(font, font.plainSubstrByWidth(help, W - 16), (W - Math.min(W - 16, font.width(help))) / 2, H - 30, DIM, false);
    }

    /** Footer hints; positions are remembered for mouse clicks. */
    private final List<int[]> footerHits = new ArrayList<>(); // {x, w, actionId}

    private void renderFooter(GuiGraphicsExtractor g, Font font) {
        footerHits.clear();
        g.fill(0, H - 17, W, H, BAR);
        int y = H - 13;
        int x = 8;
        x = footerItem(g, font, x, y, DeckButton.START, "Weitere Einstellungen", 5);
        int right = W - 8;
        String[] labels = {"Zurück", "Ansicht: " + variant.getLabel(), "Leeren", "Belegen"};
        DeckButton[] glyphs = {DeckButton.B, DeckButton.Y, DeckButton.X, DeckButton.A};
        int[] ids = {1, 4, 3, 2};
        for (int i = 0; i < labels.length; i++) {
            int w = ControllerArt.GLYPH + 4 + font.width(labels[i]);
            right -= w;
            footerItem(g, font, right, y, glyphs[i], labels[i], ids[i]);
            right -= 14;
        }
    }

    private int footerItem(GuiGraphicsExtractor g, Font font, int x, int y, DeckButton glyph, String label, int id) {
        ControllerArt.glyph(g, font, glyph, x, y - 1);
        g.text(font, label, x + ControllerArt.GLYPH + 4, y + 1, 0xFFFFFFFF, false);
        int w = ControllerArt.GLYPH + 4 + font.width(label);
        footerHits.add(new int[]{x, w, id});
        return x + w + 14;
    }

    private void showToast(String text) {
        toast = text;
        toastTime = System.currentTimeMillis();
    }

    private void renderToast(GuiGraphicsExtractor g, Font font) {
        if (toast == null || System.currentTimeMillis() - toastTime > 1500L) return;
        int w = font.width(toast) + 16;
        int x = (W - w) / 2;
        g.fill(x, 30, x + w, 44, 0xFFFFFFFF);
        g.text(font, toast, x + 8, 33, BG, false);
    }

    // ==========================================================================================
    // Actions
    // ==========================================================================================

    private void activateFocus() {
        if (focus == null) return;
        if (focus.mode() == Mode.LEFT_PAD_GRID) {
            ModConfig c = ModConfig.getInstance();
            if (!"GRID_3X3".equals(c.leftTrackpadMode)) {
                c.leftTrackpadMode = "GRID_3X3";
                c.save();
                showToast("Linkes Trackpad: Raster 3×3 eingeschaltet");
            }
            grid.open = true;
            VirtualKeyboard.playClickSound();
            return;
        }
        if (focus.mode() != null) {
            cycleMode(focus.mode());
            VirtualKeyboard.playClickSound();
            return;
        }
        picker.openFor(focus.button());
        VirtualKeyboard.playClickSound();
    }

    private void cycleMode(Mode mode) {
        ModConfig c = ModConfig.getInstance();
        switch (mode) {
            case LEFT_STICK -> { return; }
            case RIGHT_STICK -> c.enableRightStickRadialMenu = !c.enableRightStickRadialMenu;
            case LEFT_PAD -> c.leftTrackpadMode = switch (c.leftTrackpadMode) {
                case "BUTTON" -> "DPAD_MOVE";
                case "DPAD_MOVE" -> "GRID_3X3";
                default -> "BUTTON";
            };
            case LEFT_PAD_GRID -> { return; }
            case RIGHT_PAD -> c.nativeTrackpadCamera = !c.nativeTrackpadCamera;
            case GYRO -> {
                String[] order = {"AIM_ONLY", "ALWAYS", "TRACKPAD_TOUCH", "RIGHT_STICK_TOUCH"};
                if (!c.enableGyroAiming) {
                    c.enableGyroAiming = true;
                    c.gyroActivation = order[0];
                } else {
                    int i = java.util.Arrays.asList(order).indexOf(c.gyroActivation);
                    if (i < 0 || i == order.length - 1) c.enableGyroAiming = false;
                    else c.gyroActivation = order[i + 1];
                }
            }
        }
        c.save();
    }

    private void clearBinding() {
        if (grid.open) {
            clearBinding(DeckButton.gridCell(grid.index));
            return;
        }
        if (focus == null || focus.button() == null) return;
        clearBinding(focus.button());
    }

    private void clearBinding(DeckButton b) {
        ModConfig.getInstance().setBinding(set, b, new ActionBinding(DeckAction.NONE));
        showToast(b.getDisplayName() + " geleert");
        VirtualKeyboard.playClickSound();
    }

    private void switchSet(int dir) {
        ActionSet[] sets = ActionSet.values();
        set = sets[(set.ordinal() + dir + sets.length) % sets.length];
        VirtualKeyboard.playClickSound();
    }

    private void toggleVariant() {
        variantManual = true;
        variant = variant == ControllerArt.Variant.HANDHELD ? ControllerArt.Variant.PAD : ControllerArt.Variant.HANDHELD;
        VirtualKeyboard.playClickSound();
    }

    private void openSettings() {
        Minecraft.getInstance().gui.setScreen(new SteamDeckConfigScreen(this));
    }

    private void back() {
        if (picker.open) {
            if (picker.listening) picker.listening = false;
            else picker.open = false;
            return;
        }
        if (grid.open) {
            grid.open = false;
            return;
        }
        onClose();
    }

    /** Spatial navigation like Steam's gamepad focus: nearest item in the requested direction. */
    public void navigate(int dx, int dy) {
        if (picker.open) {
            picker.move(dx, dy);
            return;
        }
        if (grid.open) {
            grid.move(dx, dy);
            return;
        }
        if (focus == null) {
            focus = items.isEmpty() ? null : items.get(0);
            return;
        }
        Item best = null;
        double bestScore = Double.MAX_VALUE;
        for (Item it : items) {
            if (it == focus) continue;
            double vx = it.cx() - focus.cx();
            double vy = it.cy() - focus.cy();
            double along = vx * dx + vy * dy;
            if (along <= 2) continue;
            double across = Math.abs(vx * dy - vy * dx);
            double score = along + across * 2.5;
            if (score < bestScore) {
                bestScore = score;
                best = it;
            }
        }
        if (best != null) focus = best;
    }

    /** A physical controller button was pressed (edge) while this screen is open. */
    public void onControllerButton(DeckButton b) {
        switch (b) {
            case A, RIGHT_TRACKPAD -> {
                // Right pad click = click under the trackpad cursor (hover already moved the focus there)
                if (picker.open) picker.choose();
                else if (grid.open) grid.edit();
                else activateFocus();
            }
            case B -> back();
            case X -> {
                if (!picker.open) clearBinding();
            }
            case Y -> {
                if (!picker.open && !grid.open) toggleVariant();
            }
            case LB -> {
                if (!picker.open) switchSet(-1);
            }
            case RB -> {
                if (!picker.open) switchSet(1);
            }
            case START -> {
                if (!picker.open && !grid.open) openSettings();
            }
            // Stick touch fires constantly while the thumb rests on the stick: no jump for those
            case LEFT_STICK_TOUCH, RIGHT_STICK_TOUCH, DPAD_UP, DPAD_DOWN, DPAD_LEFT, DPAD_RIGHT -> {}
            default -> {
                // Any other input: jump to it (press R4 -> R4 is selected)
                if (!picker.open && !grid.open) {
                    Item it = findItem(b, null);
                    if (it != null && it.button() == b) {
                        focus = it;
                        com.steamdeck.mc.power.HapticFeedbackManager.getInstance().triggerHotbarHaptic(1);
                    }
                }
            }
        }
    }

    // ==========================================================================================
    // Keyboard & mouse
    // ==========================================================================================

    @Override
    public boolean keyPressed(KeyEvent event) {
        int key = event.key();
        boolean esc = event.isEscape();
        if (picker.open && picker.listening) {
            if (!esc) picker.assignKey(key);
            picker.listening = false;
            return true;
        }
        if (esc || key == InputConstants.KEY_BACKSPACE) {
            back();
            return true;
        }
        if (key == InputConstants.KEY_UP) navigate(0, -1);
        else if (key == InputConstants.KEY_DOWN) navigate(0, 1);
        else if (key == InputConstants.KEY_LEFT) navigate(-1, 0);
        else if (key == InputConstants.KEY_RIGHT) navigate(1, 0);
        else if (key == InputConstants.KEY_RETURN || key == InputConstants.KEY_SPACE) onControllerButton(DeckButton.A);
        else if (key == InputConstants.KEY_DELETE) onControllerButton(DeckButton.X);
        else if (key == InputConstants.KEY_TAB) onControllerButton(DeckButton.RB);
        else return super.keyPressed(event);
        return true;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double cx = (event.x() - offX) / scale;
        double cy = (event.y() - offY) / scale;
        if (picker.open) {
            picker.click(cx, cy, event.button() == 1);
            return true;
        }
        if (grid.open) {
            grid.click(cx, cy, event.button() == 1);
            return true;
        }
        if (event.button() == 1) {
            // Right click clears like X
            for (Item it : items) {
                if (it.contains(cx, cy)) {
                    focus = it;
                    clearBinding();
                    return true;
                }
            }
            return true;
        }
        for (Item it : items) {
            if (it.contains(cx, cy)) {
                focus = it;
                activateFocus();
                return true;
            }
        }
        if (cy >= H - 17) {
            for (int[] hit : footerHits) {
                if (cx >= hit[0] && cx < hit[0] + hit[1]) {
                    switch (hit[2]) {
                        case 1 -> back();
                        case 2 -> activateFocus();
                        case 3 -> clearBinding();
                        case 4 -> toggleVariant();
                        case 5 -> openSettings();
                        default -> {}
                    }
                    return true;
                }
            }
        }
        if (cy < 16) {
            // Click on an action set tab
            Font font = this.font;
            ActionSet[] sets = ActionSet.values();
            int gap = 14;
            int total = 0;
            for (ActionSet s : sets) total += font.width(setName(s));
            total += gap * (sets.length - 1);
            int x = (W - total) / 2;
            for (ActionSet s : sets) {
                int w = font.width(setName(s));
                if (cx >= x - 4 && cx < x + w + 4) {
                    set = s;
                    VirtualKeyboard.playClickSound();
                    return true;
                }
                x += w + gap;
            }
        }
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (picker.open) {
            picker.scrollBy(scrollY > 0 ? -3 : 3);
            return true;
        }
        return false;
    }

    @Override
    public boolean isPauseScreen() {
        return true;
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().gui.setScreen(this.parent);
    }

    // ==========================================================================================
    // Grid editor: the nine fields of the left trackpad in 3x3 mode
    // ==========================================================================================

    private final GridEditor grid = new GridEditor();

    private final class GridEditor {
        static final int PX = 166;
        static final int PY = 30;
        static final int PW = 268;
        static final int PH = 222;
        static final int CW = 80;
        static final int CH = 54;
        static final int GAP = 4;
        static final int CX0 = PX + (PW - 3 * CW - 2 * GAP) / 2;
        static final int CY0 = PY + 24;

        boolean open = false;
        int index = 4;

        int cellX(int i) { return CX0 + (i % 3) * (CW + GAP); }
        int cellY(int i) { return CY0 + (i / 3) * (CH + GAP); }

        int cellAt(double cx, double cy) {
            for (int i = 0; i < 9; i++) {
                if (cx >= cellX(i) && cx < cellX(i) + CW && cy >= cellY(i) && cy < cellY(i) + CH) return i;
            }
            return -1;
        }

        void move(int dx, int dy) {
            int col = Math.max(0, Math.min(2, index % 3 + dx));
            int row = Math.max(0, Math.min(2, index / 3 + dy));
            index = row * 3 + col;
        }

        void hover(double cx, double cy) {
            int i = cellAt(cx, cy);
            if (i >= 0) index = i;
        }

        void edit() {
            picker.openFor(DeckButton.gridCell(index));
            VirtualKeyboard.playClickSound();
        }

        void click(double cx, double cy, boolean right) {
            if (cx < PX || cx >= PX + PW || cy < PY || cy >= PY + PH) {
                open = false;
                return;
            }
            int i = cellAt(cx, cy);
            if (i < 0) return;
            index = i;
            if (right) clearBinding(DeckButton.gridCell(i));
            else edit();
        }

        void render(GuiGraphicsExtractor g, Font font) {
            g.fill(0, 0, W, H, 0x99000000);
            g.fill(PX, PY, PX + PW, PY + PH, 0xFF1A1F27);
            g.fill(PX, PY, PX + PW, PY + 1, ACCENT);
            String title = "Linkes Trackpad – Raster 3×3  –  " + setName(set);
            g.text(font, font.plainSubstrByWidth(title, PW - 16), PX + 8, PY + 8, 0xFFFFFFFF, false);

            SteamDeckInputManager input = SteamDeckInputManager.getInstance();
            int fingerCell = input.isGridModeActive() ? input.getGridHoverCell() : -1;
            ModConfig cfg = ModConfig.getInstance();
            for (int i = 0; i < 9; i++) {
                int x = cellX(i);
                int y = cellY(i);
                boolean focused = i == index && !picker.open;
                g.fill(x, y, x + CW, y + CH, focused ? 0xFFFFFFFF : 0xFF242A33);
                if (i == fingerCell) g.outline(x, y, CW, CH, ACCENT); // live: where the thumb is right now
                int fg = focused ? BG : TEXT;
                ControllerArt.glyph(g, font, DeckButton.gridCell(i), x + 4, y + 4);
                ActionBinding b = cfg.getBinding(set, DeckButton.gridCell(i));
                String label = b.getAction() == DeckAction.NONE ? "--" : b.getDisplayLabel();
                // up to two lines
                List<net.minecraft.util.FormattedCharSequence> lines =
                        font.split(net.minecraft.network.chat.Component.literal(label), CW - 8);
                for (int l = 0; l < Math.min(2, lines.size()); l++) {
                    g.text(font, lines.get(l), x + 4, y + 20 + l * 10, "--".equals(label) ? DIM : fg, false);
                }
            }
            String hint = "A belegen  •  X leeren  •  B zurück  •  L1/R1 Action-Set  •  Klick aufs Pad löst aus";
            g.text(font, font.plainSubstrByWidth(hint, PW - 16), PX + 8, PY + PH - 12, DIM, false);
        }
    }

    // ==========================================================================================
    // Picker: list of actions for one input, with "already used by" glyphs
    // ==========================================================================================

    // Uses Minecraft 26.3 SDL3 scancodes (same table as the settings screen)
    private static final Object[][] KEY_PRESETS = {
            {"Minimap", 16}, {"Wegpunkte", 5}, {"VoiceChat", 25}, {"Zoom", 6},
            {"Quests", 15}, {"Mod-Menü", 19}, {"Chat", 23}
    };

    private enum EntryType { HEADER, ACTION, KEY, LISTEN, TOGGLE_HAPTIC, TOGGLE_REPEAT }

    private record Entry(EntryType type, String text, DeckAction action, int keyCode) {
        boolean selectable() { return type != EntryType.HEADER; }
    }

    private final class Picker {
        static final int PX = 140;
        static final int PY = 26;
        static final int PW = 320;
        static final int PH = 236;
        static final int LIST_Y = PY + 22;
        static final int VISIBLE = (PH - 22 - 16) / ROW_H;

        boolean open = false;
        boolean listening = false;
        DeckButton button;
        final List<Entry> entries = new ArrayList<>();
        /** Other inputs in this action set that already use an action / key ("ACTION" or "KEY:code"). */
        final Map<String, List<DeckButton>> usedBy = new LinkedHashMap<>();
        int index = 0;
        int scroll = 0;

        void openFor(DeckButton b) {
            button = b;
            open = true;
            listening = false;
            rebuild();
            // Focus the current binding
            String cur = keyOf(ModConfig.getInstance().getBinding(set, b));
            index = firstSelectable();
            for (int i = 0; i < entries.size(); i++) {
                if (cur.equals(keyOf(entries.get(i)))) {
                    index = i;
                    break;
                }
            }
            ensureVisible();
        }

        void rebuild() {
            entries.clear();
            usedBy.clear();
            ModConfig cfg = ModConfig.getInstance();
            for (DeckButton other : DeckButton.values()) {
                if (other == button) continue;
                ActionBinding ob = cfg.getBinding(set, other);
                if (ob.getAction() == DeckAction.NONE) continue;
                usedBy.computeIfAbsent(keyOf(ob), k -> new ArrayList<>()).add(other);
            }

            ActionBinding cur = cfg.getBinding(set, button);
            if (cur.getAction() != DeckAction.NONE) {
                entries.add(new Entry(EntryType.TOGGLE_HAPTIC, "", null, 0));
                entries.add(new Entry(EntryType.TOGGLE_REPEAT, "", null, 0));
            }
            entries.add(new Entry(EntryType.ACTION, "-- Keine Aktion", DeckAction.NONE, 0));
            entries.add(new Entry(EntryType.HEADER, "SPIEL", null, 0));
            for (DeckAction a : new DeckAction[]{DeckAction.ATTACK, DeckAction.USE, DeckAction.JUMP, DeckAction.SNEAK,
                    DeckAction.SNEAK_TOGGLE, DeckAction.SPRINT, DeckAction.INVENTORY, DeckAction.DROP,
                    DeckAction.SWAP_OFFHAND, DeckAction.PICK_BLOCK, DeckAction.HOTBAR_PREV, DeckAction.HOTBAR_NEXT,
                    DeckAction.ZOOM, DeckAction.TOGGLE_PERSPECTIVE, DeckAction.OPEN_CHAT, DeckAction.TOGGLE_DEBUG,
                    DeckAction.PAUSE, DeckAction.RADIAL_MENU_CYCLE}) {
                entries.add(new Entry(EntryType.ACTION, a.getLabel(), a, 0));
            }
            entries.add(new Entry(EntryType.HEADER, "HOTBAR DIREKT", null, 0));
            for (int slot = 0; slot < 9; slot++) {
                DeckAction a = DeckAction.hotbarSlot(slot);
                entries.add(new Entry(EntryType.ACTION, a.getLabel(), a, 0));
            }
            entries.add(new Entry(EntryType.HEADER, "MAUS & MENÜ", null, 0));
            for (DeckAction a : new DeckAction[]{DeckAction.MOUSE_LEFT_CLICK, DeckAction.MOUSE_RIGHT_CLICK,
                    DeckAction.MOUSE_MIDDLE_CLICK, DeckAction.MOUSE_QUICK_MOVE, DeckAction.MOUSE_SCROLL_UP,
                    DeckAction.MOUSE_SCROLL_DOWN, DeckAction.CLOSE_SCREEN}) {
                entries.add(new Entry(EntryType.ACTION, a.getLabel(), a, 0));
            }
            entries.add(new Entry(EntryType.HEADER, "TASTATUR", null, 0));
            entries.add(new Entry(EntryType.LISTEN, "Taste auf der Tastatur drücken …", null, 0));
            // Keys already used anywhere in the config come first, then presets
            Map<Integer, String> keys = new LinkedHashMap<>();
            if (cfg.actionSets != null) {
                for (Map<String, String> m : cfg.actionSets.values()) {
                    for (String v : m.values()) {
                        ActionBinding kb = ActionBinding.deserialize(v);
                        if (kb.isCustomKey()) keys.putIfAbsent(kb.getCustomKeyCode(), kb.getCustomNickname());
                    }
                }
            }
            for (Object[] p : KEY_PRESETS) keys.putIfAbsent((Integer) p[1], (String) p[0]);
            for (Map.Entry<Integer, String> k : keys.entrySet()) {
                entries.add(new Entry(EntryType.KEY, k.getValue(), DeckAction.CUSTOM_KEY, k.getKey()));
            }
        }

        static String keyOf(ActionBinding b) {
            return b.isCustomKey() ? "KEY:" + b.getCustomKeyCode() : b.getAction().name();
        }

        static String keyOf(Entry e) {
            return switch (e.type()) {
                case ACTION -> e.action().name();
                case KEY -> "KEY:" + e.keyCode();
                default -> "";
            };
        }

        int firstSelectable() {
            for (int i = 0; i < entries.size(); i++) if (entries.get(i).selectable()) return i;
            return 0;
        }

        void move(int dx, int dy) {
            if (listening) return;
            int step = dy != 0 ? dy : dx * 8;
            int dir = step > 0 ? 1 : -1;
            int target = index;
            for (int n = 0; n < Math.abs(step); n++) {
                int i = target + dir;
                while (i >= 0 && i < entries.size() && !entries.get(i).selectable()) i += dir;
                if (i < 0 || i >= entries.size()) break;
                target = i;
            }
            index = target;
            ensureVisible();
        }

        void scrollBy(int rows) {
            scroll = Math.max(0, Math.min(Math.max(0, entries.size() - VISIBLE), scroll + rows));
        }

        void ensureVisible() {
            if (index < scroll) scroll = index;
            if (index >= scroll + VISIBLE) scroll = index - VISIBLE + 1;
            // keep the section header above the focused row visible
            if (scroll > 0 && scroll == index && !entries.get(scroll - 1).selectable()) scroll--;
            scrollBy(0);
        }

        int rowAt(double cx, double cy) {
            if (cx < PX || cx >= PX + PW || cy < LIST_Y || cy >= LIST_Y + VISIBLE * ROW_H) return -1;
            int i = scroll + (int) ((cy - LIST_Y) / ROW_H);
            return i < entries.size() ? i : -1;
        }

        void hover(double cx, double cy) {
            int i = rowAt(cx, cy);
            if (i >= 0 && entries.get(i).selectable()) index = i;
        }

        void click(double cx, double cy, boolean right) {
            if (cx < PX || cx >= PX + PW || cy < PY || cy >= PY + PH) {
                open = false; // click outside closes
                return;
            }
            int i = rowAt(cx, cy);
            if (i >= 0 && entries.get(i).selectable()) {
                index = i;
                choose();
            }
        }

        void choose() {
            if (index < 0 || index >= entries.size()) return;
            Entry e = entries.get(index);
            ModConfig cfg = ModConfig.getInstance();
            ActionBinding cur = cfg.getBinding(set, button);
            switch (e.type()) {
                case TOGGLE_HAPTIC -> {
                    cfg.setBinding(set, button, copyFlags(cur, cur, !cur.hasHaptic(), cur.hasRepeat()));
                    VirtualKeyboard.playClickSound();
                    return; // stay in the list
                }
                case TOGGLE_REPEAT -> {
                    cfg.setBinding(set, button, copyFlags(cur, cur, cur.hasHaptic(), !cur.hasRepeat()));
                    VirtualKeyboard.playClickSound();
                    return;
                }
                case LISTEN -> {
                    listening = true;
                    VirtualKeyboard.playClickSound();
                    return;
                }
                case ACTION -> {
                    ActionBinding nb = new ActionBinding(e.action());
                    cfg.setBinding(set, button, e.action() == DeckAction.NONE ? nb
                            : copyFlags(nb, cur, cur.hasHaptic(), cur.hasRepeat()));
                }
                case KEY -> cfg.setBinding(set, button,
                        copyFlags(new ActionBinding(e.keyCode(), e.text()), cur, cur.hasHaptic(), cur.hasRepeat()));
                default -> { return; }
            }
            VirtualKeyboard.playClickSound();
            showToast(button.getDisplayName() + " → " + cfg.getBinding(set, button).getDisplayLabel());
            open = false;
        }

        void assignKey(int key) {
            ModConfig cfg = ModConfig.getInstance();
            ActionBinding cur = cfg.getBinding(set, button);
            ActionBinding nb = new ActionBinding(key, "Taste " + ActionBinding.getKeyLabel(key));
            cfg.setBinding(set, button, copyFlags(nb, cur, cur.hasHaptic(), cur.hasRepeat()));
            VirtualKeyboard.playClickSound();
            showToast(button.getDisplayName() + " → " + nb.getDisplayLabel());
            open = false;
        }

        /** New binding {@code base} with haptic / repeat settings (repeat keeps old timings if there were any). */
        ActionBinding copyFlags(ActionBinding base, ActionBinding old, boolean haptic, boolean repeat) {
            ActionBinding b = base.isCustomKey()
                    ? new ActionBinding(base.getCustomKeyCode(), base.getCustomNickname())
                    : new ActionBinding(base.getAction());
            b.withHaptic(haptic);
            if (repeat) {
                int delay = old.hasRepeat() ? old.getRepeatDelayMs() : 400;
                int rate = old.hasRepeat() ? old.getRepeatRateMs() : 100;
                b.withRepeat(delay, rate);
            }
            return b;
        }

        void render(GuiGraphicsExtractor g, Font font) {
            g.fill(0, 0, W, H, 0x99000000);
            g.fill(PX, PY, PX + PW, PY + PH, 0xFF1A1F27);
            g.fill(PX, PY, PX + PW, PY + 1, ACCENT);

            ControllerArt.glyph(g, font, button, PX + 10, PY + 6);
            String title = button.getDisplayName() + "  –  " + setName(set);
            g.text(font, title, PX + 10 + ControllerArt.GLYPH + 6, PY + 8, 0xFFFFFFFF, false);

            ActionBinding cur = ModConfig.getInstance().getBinding(set, button);
            String curKey = keyOf(cur);

            for (int row = 0; row < VISIBLE; row++) {
                int i = scroll + row;
                if (i >= entries.size()) break;
                Entry e = entries.get(i);
                int y = LIST_Y + row * ROW_H;
                if (e.type() == EntryType.HEADER) {
                    g.text(font, e.text(), PX + 10, y + 3, DIM, false);
                    g.fill(PX + 14 + font.width(e.text()), y + 6, PX + PW - 10, y + 7, 0xFF2C333D);
                    continue;
                }
                boolean focused = i == index;
                if (focused) g.fill(PX + 6, y, PX + PW - 6, y + ROW_H, 0xFFFFFFFF);
                int fg = focused ? BG : TEXT;

                String text;
                switch (e.type()) {
                    case TOGGLE_HAPTIC -> text = "Haptik beim Drücken: " + (cur.hasHaptic() ? "An" : "Aus");
                    case TOGGLE_REPEAT -> text = "Gedrückt halten = wiederholen: " + (cur.hasRepeat() ? "An" : "Aus");
                    case KEY -> text = e.text() + " (" + ActionBinding.getKeyLabel(e.keyCode()) + ")";
                    default -> text = e.text();
                }
                boolean isCurrent = !keyOf(e).isEmpty() && keyOf(e).equals(curKey);
                if (isCurrent) text = "✓ " + text;
                if (e.type() == EntryType.TOGGLE_HAPTIC || e.type() == EntryType.TOGGLE_REPEAT) {
                    fg = focused ? BG : MODE_TEXT;
                }
                g.text(font, font.plainSubstrByWidth(text, PW - 110), PX + 12, y + 2, fg, false);

                // Which other inputs of this action set already use it
                List<DeckButton> users = usedBy.get(keyOf(e));
                if (users != null && !users.isEmpty()) {
                    int gx = PX + PW - 12 - ControllerArt.GLYPH;
                    int shown = 0;
                    for (int u = users.size() - 1; u >= 0 && shown < 5; u--, shown++) {
                        ControllerArt.glyph(g, font, users.get(u), gx, y + 1);
                        gx -= ControllerArt.GLYPH + 5;
                    }
                    if (users.size() > 5) g.text(font, "+" + (users.size() - 5), gx - 4, y + 2, focused ? BG : DIM, false);
                }
            }

            // Scroll indicator
            if (entries.size() > VISIBLE) {
                int trackH = VISIBLE * ROW_H;
                int barH = Math.max(10, trackH * VISIBLE / entries.size());
                int barY = LIST_Y + (trackH - barH) * scroll / Math.max(1, entries.size() - VISIBLE);
                g.fill(PX + PW - 4, barY, PX + PW - 2, barY + barH, 0xFF5A626C);
            }

            String hint = listening ? "Drücke jetzt eine Taste auf der Tastatur  •  Esc/B = Abbrechen"
                    : "A wählen  •  B zurück  •  Symbole rechts = schon belegt in diesem Action-Set";
            g.text(font, font.plainSubstrByWidth(hint, PW - 16), PX + 8, PY + PH - 12, listening ? 0xFFFFCC55 : DIM, false);
            if (listening) {
                g.fill(PX + 30, PY + 90, PX + PW - 30, PY + 130, 0xFF0E141B);
                g.fill(PX + 30, PY + 90, PX + PW - 30, PY + 91, 0xFFFFCC55);
                String s = "Taste drücken …";
                g.text(font, s, PX + (PW - font.width(s)) / 2, PY + 106, 0xFFFFCC55, false);
            }
        }
    }
}
