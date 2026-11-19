package com.steamdeck.mc.input.gyro;

/**
 * Angular velocity deltas for camera pitch and yaw.
 */
public record GyroData(float pitchDelta, float yawDelta) {
    public static final GyroData ZERO = new GyroData(0.0f, 0.0f);
}
