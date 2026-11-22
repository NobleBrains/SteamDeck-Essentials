package com.steamdeck.mc.gui;

import com.steamdeck.mc.config.ModConfig;
import com.steamdeck.mc.input.ControllerKind;
import com.steamdeck.mc.input.SteamDeckInputManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.lwjgl.BufferUtils;
import org.lwjgl.sdl.SDLGamepad;
import org.lwjgl.sdl.SDLSensor;

import java.nio.FloatBuffer;

/**
 * Small controller-shaped box in the configurator that follows the controller's gyro, as a live
 * gyro test. Orientation = gyro integration with a complementary filter (Mahony style): the
 * accelerometer pulls pitch/roll back to gravity, so real tilt is shown without drift. Yaw can't be
 * corrected by gravity, so it slowly returns to the front.
 *
 * <p>SDL sensor axes (gamepad): +X right, +Y up (out of the face), +Z toward the player; gyro in
 * rad/s, counter-clockwise positive. Drawn as pixel lines; the face (top) is highlighted.</p>
 */
public final class GyroCube {
    private static final float KP = 2.0f;              // gravity correction gain (1/s)
    private static final float YAW_RETURN = 0.6f;      // heading decay toward the front (1/s)
    private static final float VIEW_TILT = (float) Math.toRadians(35.0); // look from front-above

    // orientation quaternion device -> world (w, x, y, z)
    private float qw = 1, qx = 0, qy = 0, qz = 0;
    private long lastNanos = 0L;
    private long handle = 0L;
    private boolean enabledAccel = false;
    private boolean hasData = false;
    private final FloatBuffer buf = BufferUtils.createFloatBuffer(3);

    /** Called every frame while the configurator is open. */
    public void update() {
        SteamDeckInputManager input = SteamDeckInputManager.getInstance();
        long h = input.getActiveGamepadHandle();
        long now = System.nanoTime();
        float dt = lastNanos == 0L ? 0.0f : Math.min(0.05f, (now - lastNanos) / 1e9f);
        lastNanos = now;
        if (h != handle) {
            handle = h;
            enabledAccel = false;
            hasData = false;
            qw = 1; qx = qy = qz = 0;
        }
        if (h == 0L || dt <= 0.0f) return;
        try {
            if (!SDLGamepad.SDL_GamepadHasSensor(h, SDLSensor.SDL_SENSOR_GYRO)) {
                hasData = false;
                return;
            }
            SDLGamepad.SDL_SetGamepadSensorEnabled(h, SDLSensor.SDL_SENSOR_GYRO, true);
            if (!enabledAccel && SDLGamepad.SDL_GamepadHasSensor(h, SDLSensor.SDL_SENSOR_ACCEL)) {
                SDLGamepad.SDL_SetGamepadSensorEnabled(h, SDLSensor.SDL_SENSOR_ACCEL, true);
                enabledAccel = true;
            }
            buf.clear();
            if (!SDLGamepad.SDL_GetGamepadSensorData(h, SDLSensor.SDL_SENSOR_GYRO, buf)) return;
            float gx = buf.get(0), gy = buf.get(1), gz = buf.get(2);
            // Same correction as the camera gyro: SDL's Steam Controller (2026) driver mirrors yaw/roll
            if (ModConfig.getInstance().tritonGyroYawCorrection
                    && input.getActiveKind() == ControllerKind.STEAM_CONTROLLER_2026) {
                gy = -gy;
                gz = -gz;
            }
            hasData = true;

            // Gravity correction: measured "up" vs. "up" predicted from the orientation
            if (enabledAccel) {
                buf.clear();
                if (SDLGamepad.SDL_GetGamepadSensorData(h, SDLSensor.SDL_SENSOR_ACCEL, buf)) {
                    float ax = buf.get(0), ay = buf.get(1), az = buf.get(2);
                    float n = (float) Math.sqrt(ax * ax + ay * ay + az * az);
                    if (n > 9.81f * 0.7f && n < 9.81f * 1.3f) { // only when not being shaken
                        ax /= n; ay /= n; az /= n;
                        // world up (0,1,0) in device coordinates = conj(q) * up * q
                        float vx = 2 * (qx * qy + qw * qz);
                        float vy = qw * qw - qx * qx + qy * qy - qz * qz;
                        float vz = 2 * (qy * qz - qw * qx);
                        // error = measured x predicted
                        gx += KP * (ay * vz - az * vy);
                        gy += KP * (az * vx - ax * vz);
                        gz += KP * (ax * vy - ay * vx);
                    }
                }
            }

            // Integrate q' = 0.5 * q * (0, w)
            float hx = 0.5f * gx * dt, hy = 0.5f * gy * dt, hz = 0.5f * gz * dt;
            float nw = qw - qx * hx - qy * hy - qz * hz;
            float nx = qx + qw * hx + qy * hz - qz * hy;
            float ny = qy + qw * hy - qx * hz + qz * hx;
            float nz = qz + qw * hz + qx * hy - qy * hx;
            qw = nw; qx = nx; qy = ny; qz = nz;

            // Slowly turn the heading back to the front (rotation about world Y)
            float[] f = rotate(0, 0, 1);
            float yaw = (float) Math.atan2(f[0], f[2]);
            float a = -yaw * Math.min(1.0f, YAW_RETURN * dt) * 0.5f;
            float cw = (float) Math.cos(a), sy = (float) Math.sin(a);
            // q = qy(a) * q
            nw = cw * qw - sy * qy;
            nx = cw * qx + sy * qz;
            ny = cw * qy + sy * qw;
            nz = cw * qz - sy * qx;
            qw = nw; qx = nx; qy = ny; qz = nz;
            normalize();
        } catch (Throwable ignored) {
            hasData = false;
        }
    }

    /** Switches the accelerometer off again (saves controller battery). */
    public void close() {
        if (handle != 0L && enabledAccel) {
            try {
                SDLGamepad.SDL_SetGamepadSensorEnabled(handle, SDLSensor.SDL_SENSOR_ACCEL, false);
            } catch (Throwable ignored) {}
        }
        enabledAccel = false;
        handle = 0L;
        lastNanos = 0L;
    }

    private void normalize() {
        float n = (float) Math.sqrt(qw * qw + qx * qx + qy * qy + qz * qz);
        if (n < 1e-6f) { qw = 1; qx = qy = qz = 0; return; }
        qw /= n; qx /= n; qy /= n; qz /= n;
    }

    /** Rotates a device-frame vector into the world frame. */
    private float[] rotate(float x, float y, float z) {
        // v' = q * v * conj(q)
        float tx = 2 * (qy * z - qz * y);
        float ty = 2 * (qz * x - qx * z);
        float tz = 2 * (qx * y - qy * x);
        return new float[]{
                x + qw * tx + (qy * tz - qz * ty),
                y + qw * ty + (qz * tx - qx * tz),
                z + qw * tz + (qx * ty - qy * tx)
        };
    }

    // Box shaped like a controller: wide, flat, a bit deep
    private static final float[][] V = {
            {-0.8f, -0.2f, -0.5f}, {0.8f, -0.2f, -0.5f}, {0.8f, 0.2f, -0.5f}, {-0.8f, 0.2f, -0.5f},
            {-0.8f, -0.2f, 0.5f}, {0.8f, -0.2f, 0.5f}, {0.8f, 0.2f, 0.5f}, {-0.8f, 0.2f, 0.5f}
    };
    private static final int[][] EDGES = {
            {0, 1}, {1, 5}, {5, 4}, {4, 0},          // bottom
            {0, 3}, {1, 2}, {5, 6}, {4, 7},          // sides
            {3, 2}, {2, 6}, {6, 7}, {7, 3}           // top (face with the buttons)
    };

    public void render(GuiGraphicsExtractor g, Font font, int cx, int cy, int size, int accent) {
        if (!hasData) {
            String s = handle == 0L ? "" : "kein Gyro";
            if (!s.isEmpty()) g.text(font, s, cx - font.width(s) / 2, cy - 4, 0xFF5A626C, false);
            return;
        }
        float ct = (float) Math.cos(VIEW_TILT), st = (float) Math.sin(VIEW_TILT);
        int[][] p = new int[8][2];
        for (int i = 0; i < 8; i++) {
            float[] w = rotate(V[i][0], V[i][1], V[i][2]);
            // view: tilt the world so the top face leans toward the viewer
            float y2 = w[1] * ct - w[2] * st;
            float z2 = w[1] * st + w[2] * ct;
            float persp = 1.0f / (1.0f - z2 * 0.12f);
            p[i][0] = Math.round(cx + w[0] * size * persp);
            p[i][1] = Math.round(cy - y2 * size * persp);
        }
        for (int e = 0; e < EDGES.length; e++) {
            int col = e >= 8 ? accent : 0xFFFFFFFF;
            line(g, p[EDGES[e][0]][0], p[EDGES[e][0]][1], p[EDGES[e][1]][0], p[EDGES[e][1]][1], col);
        }
    }

    /** Bresenham line of 1x1 fills (pixel look). */
    private static void line(GuiGraphicsExtractor g, int x0, int y0, int x1, int y1, int col) {
        int dx = Math.abs(x1 - x0), sx = x0 < x1 ? 1 : -1;
        int dy = -Math.abs(y1 - y0), sy = y0 < y1 ? 1 : -1;
        int err = dx + dy;
        for (int guard = 0; guard < 400; guard++) {
            g.fill(x0, y0, x0 + 1, y0 + 1, col);
            if (x0 == x1 && y0 == y1) break;
            int e2 = 2 * err;
            if (e2 >= dy) { err += dy; x0 += sx; }
            if (e2 <= dx) { err += dx; y0 += sy; }
        }
    }

    @SuppressWarnings("unused")
    private static Minecraft mc() {
        return Minecraft.getInstance();
    }
}
