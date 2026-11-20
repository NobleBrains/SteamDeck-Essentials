package com.steamdeck.mc.gui;

import com.steamdeck.mc.config.ModConfig;
import com.steamdeck.mc.input.ControllerKind;
import com.steamdeck.mc.input.SteamDeckInputManager;
import com.steamdeck.mc.platform.PlatformDetector;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.lwjgl.sdl.SDLPower;

/**
 * Short notification in the bottom-right corner when a controller connects or disconnects, like
 * Steam's "controller connected" popup: controller type, name and battery level. Also warns once
 * when the controller battery runs low (&le; 15 %).
 *
 * <p>Rendered from the HUD (in-game without a screen) and from the screen overlay hook (menus),
 * so it is visible everywhere. The battery value is read live while the popup is shown, because
 * SDL often reports it only a moment after the controller has connected.</p>
 */
public final class ControllerToast {
    private enum Kind { CONNECTED, DISCONNECTED, LOW_BATTERY }

    private static final ControllerToast INSTANCE = new ControllerToast();

    private static final long SLIDE_IN_MS = 200L;
    private static final long HOLD_MS = 4000L;
    private static final long SLIDE_OUT_MS = 250L;
    private static final int LOW_BATTERY_PERCENT = 15;

    private Kind kind = null;
    private String name = "";
    /** True while the popup describes the currently active controller (live battery possible). */
    private boolean live = false;
    private long startTime = 0L;

    private boolean firstConnection = true;
    private boolean lowBatteryWarned = false;

    public static ControllerToast getInstance() {
        return INSTANCE;
    }

    private ControllerToast() {}

    /** Display name for the popup: our label for known controllers, the SDL name otherwise. */
    private static String displayName(ControllerKind controllerKind, String sdlName) {
        if (controllerKind == ControllerKind.OTHER || controllerKind == ControllerKind.NONE) {
            return sdlName == null || sdlName.isBlank() || "null".equals(sdlName) ? "Gamepad" : sdlName;
        }
        if (controllerKind == ControllerKind.STEAM_VIRTUAL) {
            return "Steam Input";
        }
        return controllerKind.getLabel();
    }

    public void onConnected(ControllerKind controllerKind, String sdlName) {
        boolean first = firstConnection;
        firstConnection = false;
        lowBatteryWarned = false;
        // The built-in controller of a handheld is "connected" at every game start: no popup for that
        if (first && PlatformDetector.isHandheld()
                && (controllerKind == ControllerKind.STEAM_DECK || controllerKind == ControllerKind.STEAM_VIRTUAL)) {
            return;
        }
        show(Kind.CONNECTED, displayName(controllerKind, sdlName), true);
    }

    public void onDisconnected(ControllerKind controllerKind, String sdlName) {
        firstConnection = false;
        show(Kind.DISCONNECTED, displayName(controllerKind, sdlName), false);
    }

    private void show(Kind newKind, String newName, boolean isLive) {
        if (!ModConfig.getInstance().showControllerToasts) return;
        this.kind = newKind;
        this.name = newName;
        this.live = isLive;
        this.startTime = System.currentTimeMillis();
    }

    private boolean isVisible(long now) {
        return kind != null && now - startTime < SLIDE_IN_MS + HOLD_MS + SLIDE_OUT_MS;
    }

    /** Called every client tick: one-time warning when the controller battery runs low. */
    public void tick() {
        SteamDeckInputManager input = SteamDeckInputManager.getInstance();
        if (!input.hasController()) return;
        int[] bat = input.getControllerBattery();
        if (bat[0] != SDLPower.SDL_POWERSTATE_ON_BATTERY || bat[1] < 0) {
            if (bat[0] == SDLPower.SDL_POWERSTATE_CHARGING || bat[0] == SDLPower.SDL_POWERSTATE_CHARGED) {
                lowBatteryWarned = false;
            }
            return;
        }
        if (bat[1] > LOW_BATTERY_PERCENT + 5) {
            lowBatteryWarned = false;
        } else if (bat[1] <= LOW_BATTERY_PERCENT && !lowBatteryWarned) {
            lowBatteryWarned = true;
            long now = System.currentTimeMillis();
            // The connect popup already shows the low value in red; don't repeat it right after
            if (isVisible(now) && kind == Kind.CONNECTED && live) return;
            show(Kind.LOW_BATTERY, displayName(input.getActiveKind(), input.getActiveDeviceName()), true);
        }
    }

    public void render(GuiGraphicsExtractor graphics, int screenWidth, int screenHeight) {
        long now = System.currentTimeMillis();
        if (!isVisible(now)) {
            kind = null;
            return;
        }
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.font == null) return;
        Font font = client.font;

        // Slide in from the right edge, hold, slide out
        long t = now - startTime;
        float shown;
        if (t < SLIDE_IN_MS) {
            shown = ease(t / (float) SLIDE_IN_MS);
        } else if (t < SLIDE_IN_MS + HOLD_MS) {
            shown = 1.0f;
        } else {
            shown = 1.0f - ease((t - SLIDE_IN_MS - HOLD_MS) / (float) SLIDE_OUT_MS);
        }

        String title = switch (kind) {
            case CONNECTED -> "Controller verbunden";
            case DISCONNECTED -> "Controller getrennt";
            case LOW_BATTERY -> "Controller-Akku schwach";
        };
        int accent = switch (kind) {
            case CONNECTED -> 0xFF00E5FF;
            case DISCONNECTED -> 0xFFFF5555;
            case LOW_BATTERY -> 0xFFFFAA00;
        };

        // Battery of the active controller (only while the popup is about it)
        int state = SDLPower.SDL_POWERSTATE_UNKNOWN;
        int percent = -1;
        if (live && kind != Kind.DISCONNECTED) {
            int[] bat = SteamDeckInputManager.getInstance().getControllerBattery();
            state = bat[0];
            percent = bat[1];
        }
        String batteryText = batteryText(state, percent);
        boolean hasBatteryBar = percent >= 0 && (state == SDLPower.SDL_POWERSTATE_ON_BATTERY
                || state == SDLPower.SDL_POWERSTATE_CHARGING || state == SDLPower.SDL_POWERSTATE_CHARGED);

        int iconW = 22;
        int pad = 7;
        int nameW = font.width(name);
        int batteryW = batteryText.isEmpty() ? 0 : font.width(batteryText) + (hasBatteryBar ? 20 : 0) + 8;
        int contentW = Math.max(font.width(title), nameW + batteryW);
        int boxW = Math.min(screenWidth - 16, pad + iconW + 6 + contentW + pad);
        int boxH = 30;

        int margin = 8;
        int x = screenWidth - Math.round((boxW + margin) * shown);
        // Above the hotbar in-game; in menus the same corner (bottom buttons are short enough)
        int y = screenHeight - boxH - 30;

        graphics.fill(x, y, x + boxW, y + boxH, 0xE0111822);
        graphics.fill(x, y, x + 2, y + boxH, accent);
        graphics.fill(x, y + boxH - 1, x + boxW, y + boxH, (accent & 0x00FFFFFF) | 0x66000000);

        drawControllerIcon(graphics, x + pad, y + (boxH - 12) / 2, kind == Kind.DISCONNECTED ? 0xFF8B929A : 0xFFFFFFFF);
        if (kind == Kind.DISCONNECTED) {
            // Diagonal "unplugged" stroke over the icon
            for (int i = 0; i < 14; i++) {
                graphics.fill(x + pad + 3 + i, y + boxH / 2 - 7 + i, x + pad + 5 + i, y + boxH / 2 - 6 + i, accent);
            }
        }

        int tx = x + pad + iconW + 6;
        graphics.text(font, title, tx, y + 5, 0xFF8B929A);
        String shownName = font.plainSubstrByWidth(name, Math.max(20, boxW - (tx - x) - pad - batteryW));
        graphics.text(font, shownName, tx, y + 17, 0xFFFFFFFF);

        if (!batteryText.isEmpty()) {
            int bx = x + boxW - pad - batteryW + 8;
            int by = y + 16;
            boolean low = state == SDLPower.SDL_POWERSTATE_ON_BATTERY && percent >= 0 && percent <= LOW_BATTERY_PERCENT;
            int col = low ? 0xFFFF5555 : (state == SDLPower.SDL_POWERSTATE_CHARGING ? 0xFF55FF55 : 0xFFDCDEDF);
            if (hasBatteryBar) {
                drawBattery(graphics, bx, by + 1, percent, col);
                bx += 20;
            }
            graphics.text(font, batteryText, bx, by + 1, col);
        }
    }

    private static String batteryText(int state, int percent) {
        if (state == SDLPower.SDL_POWERSTATE_NO_BATTERY) return "Kabel";
        if (percent < 0) return "";
        if (state == SDLPower.SDL_POWERSTATE_CHARGING) return percent + " % ⚡";
        if (state == SDLPower.SDL_POWERSTATE_CHARGED) return "voll";
        if (state == SDLPower.SDL_POWERSTATE_ON_BATTERY) return percent + " %";
        return "";
    }

    private static float ease(float v) {
        v = Math.max(0.0f, Math.min(1.0f, v));
        return 1.0f - (1.0f - v) * (1.0f - v);
    }

    /** Small controller pictogram (22 x 12): body, two grips, D-pad cross and two face buttons. */
    private static void drawControllerIcon(GuiGraphicsExtractor g, int x, int y, int col) {
        g.fill(x + 3, y, x + 19, y + 8, col);           // body
        g.fill(x, y + 2, x + 6, y + 12, col);           // left grip
        g.fill(x + 16, y + 2, x + 22, y + 12, col);     // right grip
        int hole = 0xFF111822;
        g.fill(x + 4, y + 3, x + 7, y + 4, hole);       // D-pad horizontal
        g.fill(x + 5, y + 2, x + 6, y + 5, hole);       // D-pad vertical
        g.fill(x + 15, y + 2, x + 17, y + 4, hole);     // face button
        g.fill(x + 17, y + 4, x + 19, y + 6, hole);     // face button
    }

    /** Battery outline (16 x 7) filled by percent. */
    private static void drawBattery(GuiGraphicsExtractor g, int x, int y, int percent, int col) {
        g.fill(x, y, x + 15, y + 1, col);
        g.fill(x, y + 6, x + 15, y + 7, col);
        g.fill(x, y, x + 1, y + 7, col);
        g.fill(x + 14, y, x + 15, y + 7, col);
        g.fill(x + 15, y + 2, x + 16, y + 5, col);
        int inner = Math.round(12 * Math.max(0, Math.min(100, percent)) / 100.0f);
        if (inner > 0) g.fill(x + 2, y + 2, x + 2 + inner, y + 5, col);
    }
}
