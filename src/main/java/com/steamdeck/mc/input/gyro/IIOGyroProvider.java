package com.steamdeck.mc.input.gyro;

import com.steamdeck.mc.SteamDeckEssentials;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Reads gyro orientation directly from Linux Industrial I/O (IIO) sensors
 * (/sys/bus/iio/devices/iio:device* or /dev/iio:device*).
 */
public class IIOGyroProvider implements GyroProvider {
    private static final Path IIO_BASE = Path.of("/sys/bus/iio/devices");
    private Path devicePath = null;
    private double scale = 0.001; // default scale rad/s
    private boolean available = false;
    private long lastPollNanos = 0;

    @Override
    public boolean init() {
        if (!Files.exists(IIO_BASE)) {
            available = false;
            return false;
        }

        try (var stream = Files.list(IIO_BASE)) {
            Optional<Path> found = stream.filter(this::isGyroDevice).findFirst();
            if (found.isPresent()) {
                devicePath = found.get();
                scale = readDoubleFile(devicePath.resolve("in_anglvel_scale"), 0.001);
                available = true;
                SteamDeckEssentials.LOGGER.info("IIO Gyro sensor detected at {}", devicePath);
                return true;
            }
        } catch (IOException e) {
            SteamDeckEssentials.LOGGER.debug("Error inspecting IIO devices", e);
        }

        available = false;
        return false;
    }

    private boolean isGyroDevice(Path path) {
        return Files.exists(path.resolve("in_anglvel_x_raw"))
                || Files.exists(path.resolve("in_anglvel_z_raw"));
    }

    @Override
    public GyroData poll() {
        long now = System.nanoTime();
        float dt = lastPollNanos == 0 ? 0.0f : Math.min(0.1f, (now - lastPollNanos) / 1_000_000_000.0f);
        lastPollNanos = now;

        // IIO sensors of laptops/tablets are not controllers: only used in handheld mode
        if (!available || devicePath == null || !com.steamdeck.mc.platform.PlatformDetector.isHandheld()) {
            return GyroData.ZERO;
        }

        try {
            // in_anglvel_*_raw * in_anglvel_scale = rad/s
            float x = (float) Math.toDegrees(readDoubleFile(devicePath.resolve("in_anglvel_x_raw"), 0.0) * scale);
            float y = (float) Math.toDegrees(readDoubleFile(devicePath.resolve("in_anglvel_y_raw"), 0.0) * scale);
            float z = (float) Math.toDegrees(readDoubleFile(devicePath.resolve("in_anglvel_z_raw"), 0.0) * scale);
            return GyroData.fromRates(x, y, z, dt, 0.8f);
        } catch (Exception e) {
            return GyroData.ZERO;
        }
    }

    private double readDoubleFile(Path file, double fallback) {
        try {
            if (Files.exists(file)) {
                return Double.parseDouble(Files.readString(file).trim());
            }
        } catch (Exception ignored) {}
        return fallback;
    }

    @Override
    public boolean isAvailable() {
        return available && com.steamdeck.mc.platform.PlatformDetector.isHandheld();
    }

    @Override
    public void close() {
        available = false;
        devicePath = null;
    }

    @Override
    public String getName() {
        return "Linux IIO";
    }
}
