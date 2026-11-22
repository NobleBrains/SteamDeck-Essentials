package com.steamdeck.mc.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.sounds.SoundSource;

/**
 * Minecraft's master volume and brightness from the controller, with a short on-screen bar like
 * Steam's volume popup. Changes the same options as the Options screen (brightness stays within the
 * vanilla 0-100 % range). options.txt is written once, 1 s after the last step.
 */
public final class LevelOsd {
    private static final double STEP = 0.05;
    private static final long SHOW_MS = 1500L;
    private static final long SAVE_DELAY_MS = 1000L;

    private static String label = "";
    private static double value = 0.0;
    private static long shownAt = 0L;
    private static long lastChange = 0L;
    private static boolean dirty = false;

    private LevelOsd() {}

    public static void adjustVolume(Minecraft mc, int dir) {
        if (mc == null || mc.options == null) return;
        OptionInstance<Double> opt = mc.options.getSoundSourceOptionInstance(SoundSource.MASTER);
        show("Lautstärke", step(opt, dir));
    }

    public static void adjustBrightness(Minecraft mc, int dir) {
        if (mc == null || mc.options == null) return;
        show("Helligkeit", step(mc.options.gamma(), dir));
    }

    private static double step(OptionInstance<Double> opt, int dir) {
        double v = Math.round((opt.get() + dir * STEP) * 20.0) / 20.0;
        v = Math.max(0.0, Math.min(1.0, v));
        opt.set(v);
        dirty = true;
        lastChange = System.currentTimeMillis();
        com.steamdeck.mc.power.HapticFeedbackManager.getInstance().triggerHotbarHaptic(dir);
        return v;
    }

    private static void show(String what, double v) {
        label = what;
        value = v;
        shownAt = System.currentTimeMillis();
    }

    /** Client tick: save options.txt once the player stopped changing values. */
    public static void tick(Minecraft mc) {
        if (dirty && System.currentTimeMillis() - lastChange > SAVE_DELAY_MS && mc != null && mc.options != null) {
            dirty = false;
            mc.options.save();
        }
    }

    public static void render(GuiGraphicsExtractor g, int screenWidth, int screenHeight) {
        if (System.currentTimeMillis() - shownAt > SHOW_MS) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.font == null) return;
        Font font = mc.font;
        String text = label + "  " + Math.round(value * 100) + " %";
        int barW = 100;
        int w = Math.max(barW, font.width(text)) + 16;
        int x = (screenWidth - w) / 2;
        int y = 24;
        g.fill(x, y, x + w, y + 26, 0xE0111822);
        g.fill(x, y, x + w, y + 1, 0xFF00E5FF);
        g.text(font, text, x + (w - font.width(text)) / 2, y + 4, 0xFFFFFFFF, false);
        int bx = x + (w - barW) / 2;
        int by = y + 17;
        g.fill(bx, by, bx + barW, by + 4, 0xFF2C333D);
        g.fill(bx, by, bx + (int) Math.round(barW * value), by + 4, 0xFF00E5FF);
    }
}
