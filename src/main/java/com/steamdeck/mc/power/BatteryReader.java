package com.steamdeck.mc.power;

import com.steamdeck.mc.SteamDeckEssentials;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Reads battery telemetry directly from Linux sysfs (/sys/class/power_supply/BAT0/).
 * Caches readings to keep game thread performance high and reduce file system overhead.
 */
public class BatteryReader {
    private static final Path SYSFS_BAT0 = Path.of("/sys/class/power_supply/BAT0");
    private static final Path SYSFS_BAT1 = Path.of("/sys/class/power_supply/BAT1");
    private static final long CACHE_DURATION_MS = 1500;

    private static Path activeBatteryPath = null;
    private static BatteryInfo cachedInfo = BatteryInfo.UNAVAILABLE;
    private static long lastReadTime = 0;

    static {
        findBatteryPath();
    }

    private static void findBatteryPath() {
        if (Files.exists(SYSFS_BAT0)) {
            activeBatteryPath = SYSFS_BAT0;
        } else if (Files.exists(SYSFS_BAT1)) {
            activeBatteryPath = SYSFS_BAT1;
        } else {
            // Search /sys/class/power_supply for any battery device
            Path base = Path.of("/sys/class/power_supply");
            if (Files.exists(base)) {
                try (var stream = Files.list(base)) {
                    Optional<Path> found = stream
                            .filter(p -> p.getFileName().toString().toUpperCase().startsWith("BAT"))
                            .findFirst();
                    found.ifPresent(path -> activeBatteryPath = path);
                } catch (IOException e) {
                    SteamDeckEssentials.LOGGER.debug("Could not scan power_supply directory", e);
                }
            }
        }
    }

    /**
     * Retrieves the latest battery metrics, refreshed periodically.
     */
    public static synchronized BatteryInfo getBatteryInfo() {
        long now = System.currentTimeMillis();
        if (now - lastReadTime < CACHE_DURATION_MS && cachedInfo.isAvailable()) {
            return cachedInfo;
        }

        if (activeBatteryPath == null || !Files.exists(activeBatteryPath)) {
            findBatteryPath();
            if (activeBatteryPath == null) {
                cachedInfo = BatteryInfo.UNAVAILABLE;
                lastReadTime = now;
                return cachedInfo;
            }
        }

        try {
            int capacity = readIntFile(activeBatteryPath.resolve("capacity"), 0);
            String status = readStringFile(activeBatteryPath.resolve("status"), "Unknown").trim();

            boolean isDischarging = "Discharging".equalsIgnoreCase(status);
            boolean isCharging = "Charging".equalsIgnoreCase(status);

            // power_now is in microwatts (µW)
            long powerMicroWatts = readLongFile(activeBatteryPath.resolve("power_now"), 0L);
            long energyMicroWattHours = readLongFile(activeBatteryPath.resolve("energy_now"), 0L);
            long voltageMicroVolts = readLongFile(activeBatteryPath.resolve("voltage_now"), 0L);

            // Fallback for devices reporting current_now (µA) instead of power_now
            if (powerMicroWatts <= 0 && voltageMicroVolts > 0) {
                long currentMicroAmps = readLongFile(activeBatteryPath.resolve("current_now"), 0L);
                if (currentMicroAmps > 0) {
                    powerMicroWatts = (currentMicroAmps * voltageMicroVolts) / 1_000_000L;
                }
            }

            // Fallback for devices reporting charge_now (µAh) instead of energy_now
            if (energyMicroWattHours <= 0 && voltageMicroVolts > 0) {
                long chargeMicroAmpHours = readLongFile(activeBatteryPath.resolve("charge_now"), 0L);
                if (chargeMicroAmpHours > 0) {
                    energyMicroWattHours = (chargeMicroAmpHours * voltageMicroVolts) / 1_000_000L;
                }
            }

            double powerWatts = powerMicroWatts / 1_000_000.0;
            double voltageVolts = voltageMicroVolts / 1_000_000.0;

            long remainingMinutes = -1;
            if (isDischarging && powerMicroWatts > 0 && energyMicroWattHours > 0) {
                double hours = (double) energyMicroWattHours / (double) powerMicroWatts;
                remainingMinutes = Math.round(hours * 60.0);
            }

            cachedInfo = new BatteryInfo(
                    capacity,
                    powerWatts,
                    voltageVolts,
                    isCharging,
                    isDischarging,
                    remainingMinutes,
                    true
            );
            lastReadTime = now;
            return cachedInfo;

        } catch (Exception e) {
            SteamDeckEssentials.LOGGER.debug("Error reading battery sysfs", e);
            cachedInfo = BatteryInfo.UNAVAILABLE;
            lastReadTime = now;
            return cachedInfo;
        }
    }

    public static boolean isOnBattery() {
        BatteryInfo info = getBatteryInfo();
        return info.isAvailable() && info.isDischarging();
    }

    private static int readIntFile(Path path, int fallback) {
        try {
            if (Files.exists(path)) {
                return Integer.parseInt(Files.readString(path).trim());
            }
        } catch (Exception ignored) {}
        return fallback;
    }

    private static long readLongFile(Path path, long fallback) {
        try {
            if (Files.exists(path)) {
                return Long.parseLong(Files.readString(path).trim());
            }
        } catch (Exception ignored) {}
        return fallback;
    }

    private static String readStringFile(Path path, String fallback) {
        try {
            if (Files.exists(path)) {
                return Files.readString(path).trim();
            }
        } catch (Exception ignored) {}
        return fallback;
    }
}
