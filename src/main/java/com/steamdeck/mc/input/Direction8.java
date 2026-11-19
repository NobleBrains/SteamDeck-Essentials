package com.steamdeck.mc.input;

/**
 * The 8 discrete cardinal and ordinal directions for anticheat-safe analog stick mapping.
 */
public enum Direction8 {
    NONE(false, false, false, false),
    UP(true, false, false, false),
    UP_RIGHT(true, false, false, true),
    RIGHT(false, false, false, true),
    DOWN_RIGHT(false, true, false, true),
    DOWN(false, true, false, false),
    DOWN_LEFT(false, true, true, false),
    LEFT(false, false, true, false),
    UP_LEFT(true, false, true, false);

    public final boolean forward;
    public final boolean backward;
    public final boolean left;
    public final boolean right;

    Direction8(boolean forward, boolean backward, boolean left, boolean right) {
        this.forward = forward;
        this.backward = backward;
        this.left = left;
        this.right = right;
    }

    /**
     * Maps an analog (x, y) stick position into one of 8 discrete directions with a deadzone threshold.
     * Guaranteed to produce 100% standard keyboard movement (W, A, S, D).
     *
     * @param x Left/Right axis [-1.0 ... 1.0]
     * @param y Up/Down axis [-1.0 (up) ... 1.0 (down)]
     * @param deadzone Deadzone radius (e.g. 0.25)
     */
    public static Direction8 fromAnalog(float x, float y, float deadzone) {
        double magnitude = Math.hypot(x, y);
        if (magnitude < deadzone) {
            return NONE;
        }

        // Angle in degrees: 0 = Right, 90 = Down, 180 = Left, -90 = Up
        double angle = Math.toDegrees(Math.atan2(y, x));
        if (angle < 0) {
            angle += 360.0;
        }

        // 8 sectors each spanning 45 degrees, centered around 0, 45, 90, 135, 180, 225, 270, 315
        if (angle >= 337.5 || angle < 22.5) {
            return RIGHT;
        } else if (angle >= 22.5 && angle < 67.5) {
            return DOWN_RIGHT;
        } else if (angle >= 67.5 && angle < 112.5) {
            return DOWN;
        } else if (angle >= 112.5 && angle < 157.5) {
            return DOWN_LEFT;
        } else if (angle >= 157.5 && angle < 202.5) {
            return LEFT;
        } else if (angle >= 202.5 && angle < 247.5) {
            return UP_LEFT;
        } else if (angle >= 247.5 && angle < 292.5) {
            return UP;
        } else {
            return UP_RIGHT;
        }
    }
}
