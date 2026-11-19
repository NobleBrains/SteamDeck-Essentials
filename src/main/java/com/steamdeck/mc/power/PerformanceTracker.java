package com.steamdeck.mc.power;

import com.steamdeck.mc.SteamDeckEssentials;
import com.steamdeck.mc.config.ModConfig;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * High-resolution Performance & Battery Telemetry Tracker.
 * Categorizes player activity (Moving/Exploring, Stationary Base, In Menu, AFK, Combat)
 * and logs power dissipation, FPS, and chunk load metrics to CSV for analysis.
 */
public class PerformanceTracker {
    private static final PerformanceTracker INSTANCE = new PerformanceTracker();
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private BufferedWriter writer = null;
    private int tickCount = 0;
    private GameplayCategory currentCategory = GameplayCategory.STATIONARY_BASE;

    public enum GameplayCategory {
        MOVING_EXPLORING("Moving/Exploring"),
        STATIONARY_BASE("Stationary/Base"),
        IN_MENU("In Menu"),
        AIMING_COMBAT("Combat/Aiming"),
        AFK_THROTTLED("AFK Throttled");

        private final String label;
        GameplayCategory(String label) { this.label = label; }
        public String getLabel() { return label; }
    }

    public static PerformanceTracker getInstance() {
        return INSTANCE;
    }

    private PerformanceTracker() {}

    public GameplayCategory getCurrentCategory() {
        return currentCategory;
    }

    public GameplayCategory evaluateCategory(Minecraft client) {
        if (PowerManager.getInstance().isAfkThrottled()) {
            return GameplayCategory.AFK_THROTTLED;
        }
        if (PowerManager.hasActiveScreen(client)) {
            return GameplayCategory.IN_MENU;
        }
        LocalPlayer player = client != null ? client.player : null;
        if (player != null) {
            if (player.isUsingItem() || player.isSwinging()) {
                return GameplayCategory.AIMING_COMBAT;
            }
            if (player.isSprinting() || player.isFallFlying() || player.getDeltaMovement().horizontalDistanceSqr() > 0.004) {
                return GameplayCategory.MOVING_EXPLORING;
            }
        }
        return GameplayCategory.STATIONARY_BASE;
    }

    /**
     * Polled on every client tick. Records metrics at the configured interval.
     */
    public void tick(Minecraft client) {
        ModConfig config = ModConfig.getInstance();
        if (!config.enablePerformanceTracking || client == null) {
            closeWriter();
            return;
        }

        this.currentCategory = evaluateCategory(client);
        this.tickCount++;

        int intervalTicks = Math.max(20, config.performanceTrackIntervalSeconds * 20);
        if (this.tickCount % intervalTicks != 0) {
            return;
        }

        recordSample(client);
    }

    private void ensureWriterOpen() {
        if (this.writer != null) return;
        try {
            Path logPath = FabricLoader.getInstance().getGameDir().resolve("steamdeck_perf_log.csv");
            boolean exists = Files.exists(logPath);
            this.writer = Files.newBufferedWriter(logPath,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);

            if (!exists || Files.size(logPath) == 0) {
                this.writer.write("timestamp,category,fps,target_fps,battery_percent,power_watts,voltage_volts,battery_status,loaded_chunks,ram_used_mb");
                this.writer.newLine();
                this.writer.flush();
            }
        } catch (IOException e) {
            SteamDeckEssentials.LOGGER.warn("[PERF TRACKER] Failed to initialize performance log file", e);
            this.writer = null;
        }
    }

    private void recordSample(Minecraft client) {
        ensureWriterOpen();
        if (this.writer == null) return;

        try {
            String timestamp = LocalDateTime.now().format(TIME_FORMAT);
            String category = this.currentCategory.getLabel();
            int fps = client.getFps();
            int targetFps = PowerManager.getInstance().getCustomFramerateCap(client);
            if (targetFps < 0) {
                targetFps = client.options.framerateLimit().get();
                if (targetFps > 260 || targetFps <= 0) targetFps = 90;
            }

            BatteryInfo battery = BatteryReader.getBatteryInfo();
            int percent = battery.percent();
            double watts = battery.powerWatts();
            double volts = battery.voltageVolts();
            String status = battery.isCharging() ? "Charging" : (battery.isDischarging() ? "Discharging" : "AC");

            int loadedChunks = (client.level != null) ? client.level.getChunkSource().getLoadedChunksCount() : 0;
            long ramUsedMb = (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / (1024L * 1024L);

            String line = String.format("%s,%s,%d,%d,%d,%.2f,%.2f,%s,%d,%d",
                    timestamp, category, fps, targetFps, percent, watts, volts, status, loadedChunks, ramUsedMb);

            this.writer.write(line);
            this.writer.newLine();
            this.writer.flush();
        } catch (Exception e) {
            SteamDeckEssentials.LOGGER.warn("[PERF TRACKER] Error writing telemetry sample", e);
        }
    }

    public void closeWriter() {
        if (this.writer != null) {
            try {
                this.writer.flush();
                this.writer.close();
            } catch (IOException ignored) {}
            this.writer = null;
        }
    }
}
