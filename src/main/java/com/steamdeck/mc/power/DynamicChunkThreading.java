package com.steamdeck.mc.power;

import com.steamdeck.mc.SteamDeckEssentials;
import com.steamdeck.mc.config.ModConfig;
import net.minecraft.TracingExecutor;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Manages background chunk compilation concurrency without blocking shared worker threads.
 * Uses a dedicated, isolated ThreadPoolExecutor when running on battery to prevent CPU spikes,
 * while routing tasks to the vanilla background executor on AC power.
 */
public class DynamicChunkThreading {
    private static final AtomicInteger THREAD_COUNTER = new AtomicInteger(1);
    private static ThreadPoolExecutor batteryPool;
    private static int currentPoolSize = 2;

    /**
     * Checks if dynamic chunk threading is active (both on Battery and AC power to keep fan quiet & FPS locked).
     */
    public static boolean isThrottlingActive() {
        ModConfig config = ModConfig.getInstance();
        return config.enableDynamicChunkThreading;
    }

    /**
     * Retrieves or updates the dedicated thread pool for chunk building.
     */
    private static synchronized ThreadPoolExecutor getOrCreateBatteryPool(int desiredThreads) {
        if (batteryPool == null || batteryPool.isShutdown()) {
            currentPoolSize = desiredThreads;
            batteryPool = new ThreadPoolExecutor(
                    desiredThreads,
                    desiredThreads,
                    60L,
                    TimeUnit.SECONDS,
                    new LinkedBlockingQueue<>(),
                    r -> {
                        Thread t = new Thread(r, "SteamDeck-Chunk-Worker-" + THREAD_COUNTER.getAndIncrement());
                        t.setDaemon(true);
                        t.setPriority(Thread.NORM_PRIORITY - 1);
                        return t;
                    }
            );
        } else if (currentPoolSize != desiredThreads && desiredThreads > 0) {
            if (desiredThreads < currentPoolSize) {
                batteryPool.setCorePoolSize(desiredThreads);
                batteryPool.setMaximumPoolSize(desiredThreads);
            } else {
                batteryPool.setMaximumPoolSize(desiredThreads);
                batteryPool.setCorePoolSize(desiredThreads);
            }
            currentPoolSize = desiredThreads;
        }
        return batteryPool;
    }

    /**
     * Wraps Minecraft's chunk background executor with an isolated, non-blocking pool.
     * Uses 2 threads on Battery, 3 threads on AC Power (Netzbetrieb) to maintain 90/60 FPS while keeping the fan silent,
     * and 1 thread during AFK.
     */
    public static TracingExecutor wrapExecutor(TracingExecutor original) {
        if (original == null) return null;

        ExecutorService originalService = original.service();
        ExecutorService dynamicService = new AbstractExecutorService() {
            @Override
            public void execute(Runnable command) {
                ModConfig config = ModConfig.getInstance();
                boolean isAfk = config.enableAfkChunkWorkerIdle && PowerManager.getInstance().isAfkThrottled();

                if (isAfk) {
                    ThreadPoolExecutor pool = getOrCreateBatteryPool(1);
                    pool.execute(command);
                } else if (config.enableDynamicChunkThreading) {
                    int threads = BatteryReader.isOnBattery()
                            ? Math.max(1, config.batteryChunkThreads)
                            : Math.max(2, config.acChunkThreads);
                    ThreadPoolExecutor pool = getOrCreateBatteryPool(threads);
                    pool.execute(command);
                } else {
                    originalService.execute(command);
                }
            }

            @Override
            public void shutdown() {
                synchronized (DynamicChunkThreading.class) {
                    if (batteryPool != null) batteryPool.shutdown();
                }
                originalService.shutdown();
            }

            @Override
            public java.util.List<Runnable> shutdownNow() {
                synchronized (DynamicChunkThreading.class) {
                    if (batteryPool != null) batteryPool.shutdownNow();
                }
                return originalService.shutdownNow();
            }

            @Override
            public boolean isShutdown() {
                return originalService.isShutdown();
            }

            @Override
            public boolean isTerminated() {
                return originalService.isTerminated();
            }

            @Override
            public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
                return originalService.awaitTermination(timeout, unit);
            }
        };

        return new TracingExecutor(dynamicService);
    }
}
