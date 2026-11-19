package com.steamdeck.mc.input.gyro;

/**
 * Camera rotation accumulated since the previous poll, in DEGREES (already integrated over time,
 * so it is independent of the frame rate). Sign convention matches Minecraft:
 * positive yaw turns right, positive pitch looks down.
 */
public record GyroData(float pitchDelta, float yawDelta) {
    public static final GyroData ZERO = new GyroData(0.0f, 0.0f);

    public boolean isZero() {
        return pitchDelta == 0.0f && yawDelta == 0.0f;
    }

    /**
     * Converts angular velocities (deg/s, SDL/right-handed convention: positive pitch = tilt up,
     * positive yaw = turn left, positive roll = tilt left) into a camera delta for {@code dtSeconds}.
     * Yaw uses whichever of yaw/roll is dominant, so both "turn" and "steer" motions work on handhelds.
     */
    public static GyroData fromRates(float pitchRate, float yawRate, float rollRate, float dtSeconds, float deadzoneDegPerSec) {
        float yawLike = Math.abs(yawRate) >= Math.abs(rollRate) ? yawRate : rollRate;
        if (Math.abs(pitchRate) < deadzoneDegPerSec) pitchRate = 0.0f;
        if (Math.abs(yawLike) < deadzoneDegPerSec) yawLike = 0.0f;
        return new GyroData(-pitchRate * dtSeconds, -yawLike * dtSeconds);
    }
}
