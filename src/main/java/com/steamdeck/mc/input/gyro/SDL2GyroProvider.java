package com.steamdeck.mc.input.gyro;

import com.steamdeck.mc.SteamDeckEssentials;
import com.steamdeck.mc.input.SteamDeckInputManager;
import org.lwjgl.BufferUtils;
import org.lwjgl.sdl.SDLGamepad;
import org.lwjgl.sdl.SDLSensor;
import org.lwjgl.sdl.SDLStdinc;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;

/**
 * Native SDL3 gyro provider: gamepad-embedded sensors (Steam Deck / Steam Controller via HIDAPI,
 * DualSense, Switch Pro, ...) with a fallback to standalone SDL sensors.
 * SDL reports angular velocity in rad/s; it is integrated over the real time between polls.
 */
public class SDL2GyroProvider implements GyroProvider {
    private static final float RAD_TO_DEG = (float) (180.0 / Math.PI);
    private static final float DEADZONE_DEG_PER_SEC = 0.6f;

    private final FloatBuffer sensorBuffer = BufferUtils.createFloatBuffer(3);
    private long lastGamepadHandle = 0;
    private int enabledSensorType = -1;
    private long standaloneSensorHandle = 0;
    private long lastSensorScanTime = 0;
    private long lastPollNanos = 0;

    @Override
    public boolean init() {
        return true;
    }

    @Override
    public GyroData poll() {
        long now = System.nanoTime();
        float dt = lastPollNanos == 0 ? 0.0f : Math.min(0.1f, (now - lastPollNanos) / 1_000_000_000.0f);
        lastPollNanos = now;

        try {
            SDLSensor.SDL_UpdateSensors();
        } catch (Throwable ignored) {}

        // 1. Gyro embedded in the active SDL gamepad
        long handle = SteamDeckInputManager.getInstance().getActiveGamepadHandle();
        if (handle != lastGamepadHandle) {
            lastGamepadHandle = handle;
            enabledSensorType = -1;
        }

        if (handle != 0) {
            if (enabledSensorType < 0) {
                for (int type : new int[]{SDLSensor.SDL_SENSOR_GYRO, SDLSensor.SDL_SENSOR_GYRO_L, SDLSensor.SDL_SENSOR_GYRO_R}) {
                    if (SDLGamepad.SDL_GamepadHasSensor(handle, type)) {
                        SDLGamepad.SDL_SetGamepadSensorEnabled(handle, type, true);
                        enabledSensorType = type;
                        SteamDeckEssentials.LOGGER.info("[GYRO] SDL gamepad gyro enabled (sensor type {}, {} Hz).",
                                type, SDLGamepad.SDL_GetGamepadSensorDataRate(handle, type));
                        break;
                    }
                }
            }

            if (enabledSensorType >= 0) {
                sensorBuffer.clear();
                // Read the sensor that was actually enabled (previously always SDL_SENSOR_GYRO was read)
                if (SDLGamepad.SDL_GetGamepadSensorData(handle, enabledSensorType, sensorBuffer)) {
                    return toGyroData(dt);
                }
            }
        }

        // 2. Fallback: standalone SDL sensor
        long nowMs = System.currentTimeMillis();
        if (standaloneSensorHandle == 0 && (nowMs - lastSensorScanTime > 3000)) {
            lastSensorScanTime = nowMs;
            try {
                IntBuffer sensors = SDLSensor.SDL_GetSensors();
                if (sensors != null) {
                    try {
                        for (int i = 0; i < sensors.limit(); i++) {
                            int id = sensors.get(i);
                            int type = SDLSensor.SDL_GetSensorTypeForID(id);
                            if (type == SDLSensor.SDL_SENSOR_GYRO || type == SDLSensor.SDL_SENSOR_GYRO_L || type == SDLSensor.SDL_SENSOR_GYRO_R) {
                                long sh = SDLSensor.SDL_OpenSensor(id);
                                if (sh != 0) {
                                    standaloneSensorHandle = sh;
                                    SteamDeckEssentials.LOGGER.info("[GYRO] Opened standalone SDL sensor: {}", SDLSensor.SDL_GetSensorName(sh));
                                    break;
                                }
                            }
                        }
                    } finally {
                        SDLStdinc.SDL_free(sensors);
                    }
                }
            } catch (Throwable ignored) {}
        }

        if (standaloneSensorHandle != 0) {
            sensorBuffer.clear();
            if (SDLSensor.SDL_GetSensorData(standaloneSensorHandle, sensorBuffer)) {
                return toGyroData(dt);
            }
        }

        return GyroData.ZERO;
    }

    private GyroData toGyroData(float dt) {
        // SDL: [0] = pitch (x), [1] = yaw (y), [2] = roll (z) in rad/s
        float pitch = sensorBuffer.get(0) * RAD_TO_DEG;
        float yaw = sensorBuffer.get(1) * RAD_TO_DEG;
        float roll = sensorBuffer.get(2) * RAD_TO_DEG;
        return GyroData.fromRates(pitch, yaw, roll, dt, DEADZONE_DEG_PER_SEC);
    }

    @Override
    public boolean isAvailable() {
        return enabledSensorType >= 0 || standaloneSensorHandle != 0;
    }

    @Override
    public void close() {
        if (lastGamepadHandle != 0 && enabledSensorType >= 0) {
            SDLGamepad.SDL_SetGamepadSensorEnabled(lastGamepadHandle, enabledSensorType, false);
        }
        if (standaloneSensorHandle != 0) {
            SDLSensor.SDL_CloseSensor(standaloneSensorHandle);
            standaloneSensorHandle = 0;
        }
        enabledSensorType = -1;
    }

    @Override
    public String getName() {
        return "SDL3 Native Sensor";
    }
}
