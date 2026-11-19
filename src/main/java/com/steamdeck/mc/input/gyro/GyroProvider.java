package com.steamdeck.mc.input.gyro;

/**
 * Interface representing a hardware sensor provider for Steam Deck gyro aiming.
 */
public interface GyroProvider {
    /**
     * Initializes the provider and checks hardware sensor readiness.
     */
    boolean init();

    /**
     * Polls the sensor and returns current pitch & yaw motion deltas.
     */
    GyroData poll();

    /**
     * Checks if this provider is currently available on the host system.
     */
    boolean isAvailable();

    /**
     * Closes connections and frees resources.
     */
    void close();

    /**
     * Display name of the provider (e.g. "Linux IIO", "SDL2", "DSU UDP").
     */
    String getName();
}
