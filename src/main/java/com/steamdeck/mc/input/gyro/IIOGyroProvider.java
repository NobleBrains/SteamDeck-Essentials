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
        if (!available || devicePath == null) return GyroData.ZERO;

        try {
            // Read angular velocities (X, Y, Z)
            double rawX = readDoubleFile(devicePath.resolve("in_anglvel_x_raw"), 0.0);
            double rawY = readDoubleFile(devicePath.resolve("in_anglvel_y_raw"), 0.0);
            double rawZ = readDoubleFile(devicePath.resolve("in_anglvel_z_raw"), 0.0);

            // Convert to degrees per second / frame delta
            float pitch = (float) (rawX * scale * 1.5);
            float yaw = (float) (rawZ * scale * 1.5);

            // Apply deadzone to avoid gyro drift
            if (Math.abs(pitch) < 0.015f) pitch = 0.0f;
            if (Math.abs(yaw) < 0.015f) yaw = 0.0f;

            return new GyroData(pitch, yaw);
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
        return available;
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
