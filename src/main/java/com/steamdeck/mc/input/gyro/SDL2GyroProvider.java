package com.steamdeck.mc.input.gyro;

import com.steamdeck.mc.SteamDeckEssentials;
import com.steamdeck.mc.input.SteamDeckInputManager;
import org.lwjgl.BufferUtils;
import org.lwjgl.sdl.SDLGamepad;
import org.lwjgl.sdl.SDLSensor;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;

/**
 * Native SDL3 Gyro Provider.
 * Integrates with both SDL Gamepad embedded sensors and standalone SDL Sensor devices.
 */
public class SDL2GyroProvider implements GyroProvider {
    private final FloatBuffer sensorBuffer = BufferUtils.createFloatBuffer(3);
    private long lastGamepadHandle = 0;
    private boolean gamepadSensorEnabled = false;
    private long standaloneSensorHandle = 0;
    private long lastSensorScanTime = 0;

    @Override
    public boolean init() {
        return true;
    }

    @Override
    public GyroData poll() {
        try {
            SDLSensor.SDL_UpdateSensors();
        } catch (Throwable ignored) {}

        // 1. Check active SDL Gamepad sensor
        long handle = SteamDeckInputManager.getInstance().getActiveGamepadHandle();
        if (handle != lastGamepadHandle) {
            lastGamepadHandle = handle;
            gamepadSensorEnabled = false;
        }

        if (handle != 0) {
            if (!gamepadSensorEnabled) {
                for (int type : new int[]{SDLSensor.SDL_SENSOR_GYRO, SDLSensor.SDL_SENSOR_GYRO_L, SDLSensor.SDL_SENSOR_GYRO_R}) {
                    if (SDLGamepad.SDL_GamepadHasSensor(handle, type)) {
                        SDLGamepad.SDL_SetGamepadSensorEnabled(handle, type, true);
                        gamepadSensorEnabled = true;
                        SteamDeckEssentials.LOGGER.info("[GYRO] SDL Gamepad gyro sensor enabled (type {}).", type);
                        break;
                    }
                }
            }

            if (gamepadSensorEnabled) {
                sensorBuffer.clear();
                if (SDLGamepad.SDL_GetGamepadSensorData(handle, SDLSensor.SDL_SENSOR_GYRO, sensorBuffer)) {
                    GyroData data = extractGyroData();
                    if (data.pitchDelta() != 0.0f || data.yawDelta() != 0.0f) {
                        return data;
                    }
                }
            }
        }

        // 2. Fallback: Check standalone SDL3 Sensor subsystem
        long now = System.currentTimeMillis();
        if (standaloneSensorHandle == 0 && (now - lastSensorScanTime > 3000)) {
            lastSensorScanTime = now;
            try {
                IntBuffer sensors = SDLSensor.SDL_GetSensors();
                if (sensors != null && sensors.hasRemaining()) {
                    while (sensors.hasRemaining()) {
                        int id = sensors.get();
                        int type = SDLSensor.SDL_GetSensorTypeForID(id);
                        if (type == SDLSensor.SDL_SENSOR_GYRO || type == SDLSensor.SDL_SENSOR_GYRO_L || type == SDLSensor.SDL_SENSOR_GYRO_R) {
                            long sh = SDLSensor.SDL_OpenSensor(id);
                            if (sh != 0) {
                                standaloneSensorHandle = sh;
                                SteamDeckEssentials.LOGGER.info("[GYRO] Opened standalone SDL Sensor: {}", SDLSensor.SDL_GetSensorName(sh));
                                break;
                            }
                        }
                    }
                }
            } catch (Throwable ignored) {}
        }

        if (standaloneSensorHandle != 0) {
            sensorBuffer.clear();
            if (SDLSensor.SDL_GetSensorData(standaloneSensorHandle, sensorBuffer)) {
                return extractGyroData();
            }
        }

        return GyroData.ZERO;
    }

    private GyroData extractGyroData() {
        float gyroX = sensorBuffer.get(0);
        float gyroY = sensorBuffer.get(1);
        float gyroZ = sensorBuffer.get(2);

        float pitch = -gyroX * 2.2f;
        float yaw = -gyroZ * 2.2f;
        if (Math.abs(gyroY) > Math.abs(gyroZ)) {
            yaw = -gyroY * 2.2f;
        }

        if (Math.abs(pitch) < 0.015f) pitch = 0.0f;
        if (Math.abs(yaw) < 0.015f) yaw = 0.0f;

        return new GyroData(pitch, yaw);
    }

    @Override
    public boolean isAvailable() {
        return gamepadSensorEnabled || standaloneSensorHandle != 0;
    }

    @Override
    public void close() {
        if (lastGamepadHandle != 0 && gamepadSensorEnabled) {
            SDLGamepad.SDL_SetGamepadSensorEnabled(lastGamepadHandle, SDLSensor.SDL_SENSOR_GYRO, false);
        }
        if (standaloneSensorHandle != 0) {
            SDLSensor.SDL_CloseSensor(standaloneSensorHandle);
            standaloneSensorHandle = 0;
        }
        gamepadSensorEnabled = false;
    }

    @Override
    public String getName() {
        return "SDL3 Native Sensor";
    }
}
