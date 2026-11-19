package com.steamdeck.mc.power;

import com.steamdeck.mc.config.ModConfig;
import com.steamdeck.mc.platform.PlatformDetector;
import net.minecraft.TracingExecutor;

import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Limits background chunk-mesh compilation concurrency on handhelds.
 *
 * <p>Chunk compile tasks are routed to a small dedicated, low-priority pool
 * (battery: {@code batteryChunkThreads}, AC: {@code acChunkThreads}, AFK: 1 thread).
 * In DESKTOP mode or with the feature disabled, every task goes to Minecraft's own
 * background executor untouched. The decision is made per task, so switching the
 * device mode at runtime takes effect immediately.</p>
 */
public class DynamicChunkThreading {
    private static final AtomicInteger THREAD_COUNTER = new AtomicInteger(1);
    private static ThreadPoolExecutor pool;
    private static volatile int currentPoolSize = 0;

    public static boolean isThrottlingActive() {
        return ModConfig.getInstance().enableDynamicChunkThreading && PlatformDetector.isHandheld();
    }

    private static ThreadPoolExecutor getPool(int desiredThreads) {
        ThreadPoolExecutor p = pool;
        if (p != null && currentPoolSize == desiredThreads && !p.isShutdown()) {
            return p; // fast path without locking
        }
        synchronized (DynamicChunkThreading.class) {
            if (pool == null || pool.isShutdown()) {
                pool = new ThreadPoolExecutor(
                        desiredThreads, desiredThreads,
                        60L, TimeUnit.SECONDS,
                        new LinkedBlockingQueue<>(),
                        r -> {
                            Thread t = new Thread(r, "SteamDeck-Chunk-Worker-" + THREAD_COUNTER.getAndIncrement());
                            t.setDaemon(true);
                            t.setPriority(Thread.NORM_PRIORITY - 1);
                            return t;
                        });
                pool.allowCoreThreadTimeOut(true);
                currentPoolSize = desiredThreads;
            } else if (currentPoolSize != desiredThreads) {
                // Order matters: core must never exceed max
                if (desiredThreads < currentPoolSize) {
                    pool.setCorePoolSize(desiredThreads);
                    pool.setMaximumPoolSize(desiredThreads);
                } else {
                    pool.setMaximumPoolSize(desiredThreads);
                    pool.setCorePoolSize(desiredThreads);
                }
                currentPoolSize = desiredThreads;
            }
            return pool;
        }
    }

    /**
     * Wraps Minecraft's chunk background executor. Only {@link ExecutorService#execute} is
     * redirected; lifecycle methods are deliberately NOT forwarded to Minecraft's shared
     * {@code Util.backgroundExecutor()}, which is used by many other systems and must never be
     * shut down because a chunk renderer was rebuilt.
     */
    public static TracingExecutor wrapExecutor(TracingExecutor original) {
        if (original == null) return null;

        ExecutorService originalService = original.service();
        ExecutorService dynamicService = new AbstractExecutorService() {
            private volatile boolean shutdown = false;

            @Override
            public void execute(Runnable command) {
                ModConfig config = ModConfig.getInstance();
                if (!PlatformDetector.isHandheld()) {
                    originalService.execute(command);
                    return;
                }

                boolean isAfk = config.enableAfkChunkWorkerIdle && PowerManager.getInstance().isAfkThrottled();
                if (isAfk) {
                    getPool(1).execute(command);
                } else if (config.enableDynamicChunkThreading) {
                    int threads = BatteryReader.isOnBattery()
                            ? Math.max(1, config.batteryChunkThreads)
                            : Math.max(1, config.acChunkThreads);
                    getPool(threads).execute(command);
                } else {
                    originalService.execute(command);
                }
            }

            @Override
            public void shutdown() {
                shutdown = true;
            }

            @Override
            public List<Runnable> shutdownNow() {
                shutdown = true;
                return List.of();
            }

            @Override
            public boolean isShutdown() {
                return shutdown;
            }

            @Override
            public boolean isTerminated() {
                return shutdown;
            }

            @Override
            public boolean awaitTermination(long timeout, TimeUnit unit) {
                return true;
            }
        };

        return new TracingExecutor(dynamicService);
    }
}
