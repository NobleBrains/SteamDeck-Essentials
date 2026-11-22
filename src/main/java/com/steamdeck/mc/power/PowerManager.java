package com.steamdeck.mc.power;

import com.steamdeck.mc.config.ModConfig;
import com.steamdeck.mc.platform.PlatformDetector;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Manages dynamic frame rate throttling, AFK power-saving (smooth multi-stage throttling,
 * OLED screen dimming, background idle), and battery conservation on the Steam Deck.
 */
public class PowerManager {
    private static final PowerManager INSTANCE = new PowerManager();

    private long lastInputTime = System.currentTimeMillis();
    private boolean lowBatteryToastShown20 = false;
    private boolean lowBatteryToastShown10 = false;
    private long lowBatteryToastTime = 0L;
    private String lowBatteryToastText = "";

    public static PowerManager getInstance() {
        return INSTANCE;
    }

    private PowerManager() {}

    /**
     * Called on any user activity (controller stick/button, mouse, keyboard, touch).
     */
    public void onUserActivity() {
        this.lastInputTime = System.currentTimeMillis();
    }

    /**
     * Milliseconds elapsed since the last user input.
     */
    public long getIdleTimeMs() {
        return Math.max(0, System.currentTimeMillis() - this.lastInputTime);
    }

    /**
     * Checks if a screen/GUI is currently active on Minecraft 26.3.
     * Treats TitleScreen, menus, and when not in a world as active screens.
     */
    public static boolean hasActiveScreen(Minecraft client) {
        if (client == null) return true;
        if (client.level == null || client.player == null) return true;
        return client.gui != null && (client.gui.screen() != null || client.gui.overlay() != null);
    }

    /**
     * Calculates the dynamic AFK throttle multiplier.
     * On Battery: drops 10%/min after 2 min down to -80% (15 FPS).
     * On AC Power (Netzbetrieb): dialed back! Starts after 4 min, drops 5%/min, max -50% (40 FPS).
     */
    public float getAfkThrottleFactor() {
        ModConfig config = ModConfig.getInstance();
        if (!config.enableDynamicAfkThrottle || !PlatformDetector.isHandheld()) return 1.0f;

        boolean onBattery = BatteryReader.isOnBattery();
        long idleMs = getIdleTimeMs();
        int startMinutes = onBattery ? config.afkThrottleStartMinutes : config.acAfkThrottleStartMinutes;
        long startMs = (long) startMinutes * 60_000L;
        if (idleMs < startMs) {
            return 1.0f;
        }

        long elapsedAfkMs = idleMs - startMs;
        float minutesAfk = (float) elapsedAfkMs / 60_000.0f;
        float ratePerMin = onBattery ? config.afkThrottleRatePerMinute : (config.afkThrottleRatePerMinute * 0.5f);
        float maxThrottle = onBattery ? config.maxAfkThrottle : config.acMaxAfkThrottle;
        float throttlePercent = Math.min(maxThrottle, minutesAfk * ratePerMin);
        return Math.max(1.0f - maxThrottle, 1.0f - throttlePercent);
    }

    /**
     * Returns true if AFK throttling is currently active.
     */
    public boolean isAfkThrottled() {
        return getAfkThrottleFactor() < 0.999f;
    }

    /**
     * Determines whether custom FPS capping should apply and returns the target FPS.
     * Returns -1 if standard Minecraft frame rate limit should remain untouched.
     */
    public int getCustomFramerateCap(Minecraft client) {
        if (client == null) return -1;
        // Desktop mode: never touch Minecraft's own frame rate handling
        if (!PlatformDetector.isHandheld()) return -1;

        ModConfig config = ModConfig.getInstance();
        boolean onBattery = BatteryReader.isOnBattery();

        // 1. Unfocused / Iconified throttle (highest priority)
        if (client.getWindow() != null && client.getWindow().isIconified()) {
            return Math.max(5, config.unfocusedFpsCap);
        }

        // 2. Base target FPS: menu cap (30 on battery, 60 on AC) or in-game target (90 OLED / 60 LCD)
        int panelHz = PlatformDetector.getHardware().panelRefreshHz();
        int activeMenuCap = onBattery ? Math.max(15, config.menuFpsCap) : Math.max(30, config.acMenuFpsCap);
        // The trackpad mouse pointer moves once per frame: keep it smooth while a finger is on the pad
        if (com.steamdeck.mc.input.SteamDeckInputManager.getInstance().isTrackpadTouched(1)
                && com.steamdeck.mc.input.SteamDeckInputManager.getInstance().getActiveKind().hasNativeTouchpad()) {
            activeMenuCap = Math.max(activeMenuCap, Math.min(60, panelHz));
        }
        int baseFps;
        if (hasActiveScreen(client)) {
            baseFps = activeMenuCap;
        } else {
            baseFps = client.options.framerateLimit().get();
            if (baseFps <= 0 || baseFps > 260) {
                baseFps = panelHz; // "Unlimited": use the native panel refresh rate as the reference
            } else if (config.enableThermalFanGuard && baseFps > panelHz) {
                baseFps = panelHz; // Prevent pointless rendering above the panel refresh rate (fan stays quiet)
            }
        }

        // 3. Dynamic AFK throttle (smooth continuous descent, gentler on AC power)
        if (config.enableDynamicAfkThrottle) {
            float factor = getAfkThrottleFactor();
            if (factor < 0.999f) {
                int minFloor = onBattery ? config.minAfkFps : config.acMinAfkFps;
                // The floor may never lift the rate above the un-throttled base (e.g. a 30 FPS user limit)
                int throttledFps = Math.max(Math.min(minFloor, baseFps), Math.round(baseFps * factor));
                return throttledFps;
            }
        }

        // 4. Standard in-game menu throttle (30 FPS on Battery, 60 FPS on AC)
        if (hasActiveScreen(client)) {
            return activeMenuCap;
        }

        // 4b. Low-Battery Eco Saver (< 20% battery while discharging: cap at 60 FPS)
        if (onBattery && config.enableLowBatterySaver) {
            BatteryInfo info = BatteryReader.getBatteryInfo();
            if (info.isAvailable() && info.percent() > 0 && info.percent() <= 20) {
                // Never raise the frame rate: a lower limit set by the player always wins
                int ecoCap = info.percent() <= 10 ? 45 : 60;
                int userLimit = client.options.framerateLimit().get();
                if (userLimit > 0 && userLimit <= 260) {
                    ecoCap = Math.min(ecoCap, userLimit);
                }
                if (info.percent() <= 10 && !lowBatteryToastShown10) {
                    lowBatteryToastShown10 = true;
                    lowBatteryToastTime = System.currentTimeMillis();
                    lowBatteryToastText = "🪫 Kritischer Akku (" + info.percent() + "%) • Eco-Cap (" + ecoCap + " FPS) aktiv";
                } else if (info.percent() <= 20 && !lowBatteryToastShown20) {
                    lowBatteryToastShown20 = true;
                    lowBatteryToastTime = System.currentTimeMillis();
                    lowBatteryToastText = "🪫 Akku niedrig (" + info.percent() + "%) • Eco-Cap (" + ecoCap + " FPS) aktiv";
                }
                return ecoCap;
            } else if (info.percent() > 25) {
                lowBatteryToastShown20 = false;
                lowBatteryToastShown10 = false;
            }
        }

        // 5. Thermal & Fan Noise Guard during active gameplay (caps FPS to the panel refresh: 90 Hz OLED / 60 Hz LCD)
        if (config.enableThermalFanGuard) {
            int userLimit = client.options.framerateLimit().get();
            if (userLimit <= 0 || userLimit > panelHz) {
                return panelHz;
            }
        }

        return -1;
    }

    /**
     * Calculates the alpha transparency for the OLED-friendly AFK screen dimming overlay.
     * On AC Power (Netzbetrieb), dimming starts later and is much gentler (30% vs 65%).
     */
    public float getAfkDimAlpha() {
        ModConfig config = ModConfig.getInstance();
        if (!config.enableAfkScreenDimming || !PlatformDetector.isHandheld()) return 0.0f;

        boolean onBattery = BatteryReader.isOnBattery();
        long idleMs = getIdleTimeMs();
        int startMinutes = onBattery ? config.afkDimStartMinutes : config.acAfkDimStartMinutes;
        long startDimMs = (long) startMinutes * 60_000L;
        if (idleMs < startDimMs) return 0.0f;

        // Smoothly fade in over 45 seconds
        float elapsedDimSeconds = (float) (idleMs - startDimMs) / 1000.0f;
        float fadeRatio = Math.min(1.0f, elapsedDimSeconds / 45.0f);
        float maxAlpha = onBattery ? config.maxAfkDimAlpha : config.acMaxAfkDimAlpha;
        return fadeRatio * maxAlpha;
    }

    /**
     * Renders a smooth screen dimming overlay during AFK to save battery on OLED displays.
     * Keeps the display clean without intrusive text indicators.
     */
    public void renderAfkOverlay(GuiGraphicsExtractor graphics, int width, int height) {
        if (!PlatformDetector.isHandheld()) return;
        // Render low-battery toast if triggered recently
        long toastElapsed = System.currentTimeMillis() - lowBatteryToastTime;
        if (toastElapsed < 4500L && !lowBatteryToastText.isEmpty()) {
            Minecraft client = Minecraft.getInstance();
            if (client != null && client.font != null) {
                Font font = client.font;
                int tw = font.width(lowBatteryToastText) + 14;
                int tx = (width - tw) / 2;
                int ty = 10;
                graphics.fill(tx, ty, tx + tw, ty + 16, 0xDD221111);
                graphics.fill(tx, ty, tx + tw, ty + 1, 0xFFFF5555);
                graphics.centeredText(font, lowBatteryToastText, width / 2, ty + 4, 0xFFFF7777);
            }
        }

        float alpha = getAfkDimAlpha();
        if (alpha <= 0.01f) return;

        int alphaByte = Math.round(alpha * 255.0f);
        int color = (alphaByte << 24) | 0x000000;
        graphics.fill(0, 0, width, height, color);
    }
}
