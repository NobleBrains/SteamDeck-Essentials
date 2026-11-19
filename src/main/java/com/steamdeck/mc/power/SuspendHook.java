package com.steamdeck.mc.power;

import com.steamdeck.mc.SteamDeckEssentials;
import com.steamdeck.mc.config.ModConfig;
import com.steamdeck.mc.platform.PlatformDetector;
import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Detects Linux suspend/sleep and saves + pauses singleplayer worlds (HANDHELD mode only).
 *
 * <ul>
 *     <li><b>D-Bus:</b> {@code busctl monitor} on logind's {@code PrepareForSleep} signal (before sleep).
 *     Not available inside the Prism Flatpak sandbox; then only the watchdog is used.</li>
 *     <li><b>Watchdog (after wake):</b> compares wall-clock time ({@code currentTimeMillis}, keeps
 *     running during suspend) with {@code nanoTime} (CLOCK_MONOTONIC, stops during suspend).
 *     A gap between the two can only come from a suspend, so long GC pauses, game hangs or
 *     NTP/time-zone changes no longer trigger false "suspend" saves.</li>
 * </ul>
 *
 * The world save runs on the integrated server thread (it previously ran on the render thread,
 * which is not thread-safe and could corrupt chunks).
 */
public class SuspendHook {
    private static final SuspendHook INSTANCE = new SuspendHook();
    private static final long SUSPEND_GAP_MS = 3000L;

    private final AtomicBoolean running = new AtomicBoolean(false);
    private Thread dbusThread;
    private Thread watchdogThread;
    private volatile Process busctlProcess;
    private volatile long lastTriggerTime = 0L;

    public static SuspendHook getInstance() {
        return INSTANCE;
    }

    private SuspendHook() {}

    public boolean isRunning() {
        return running.get();
    }

    /** Starts or stops the hook depending on the current device mode and config. Cheap; called every tick. */
    public void syncWithMode() {
        boolean wanted = ModConfig.getInstance().enableAutoSuspendSave && PlatformDetector.isHandheld();
        if (wanted && !running.get()) {
            start();
        } else if (!wanted && running.get()) {
            stop();
        }
    }

    public synchronized void start() {
        if (running.getAndSet(true)) return;
        startDbusListener();
        startWatchdog();
        SteamDeckEssentials.LOGGER.info("[POWER] Suspend/sleep hook started.");
    }

    public synchronized void stop() {
        if (!running.getAndSet(false)) return;
        Process p = busctlProcess;
        if (p != null) {
            p.destroy();
        }
        if (dbusThread != null) dbusThread.interrupt();
        if (watchdogThread != null) watchdogThread.interrupt();
        SteamDeckEssentials.LOGGER.info("[POWER] Suspend/sleep hook stopped.");
    }

    private void startDbusListener() {
        dbusThread = new Thread(() -> {
            try {
                ProcessBuilder pb = new ProcessBuilder(
                        "busctl", "--system", "monitor", "--match",
                        "type='signal',interface='org.freedesktop.login1.Manager',member='PrepareForSleep'");
                pb.redirectErrorStream(true);
                Process process = pb.start();
                busctlProcess = process;

                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                    String line;
                    boolean pendingPrepareForSleep = false;
                    int linesSinceSignal = 0;

                    while (running.get() && (line = reader.readLine()) != null) {
                        if (line.contains("PrepareForSleep")) {
                            pendingPrepareForSleep = true;
                            linesSinceSignal = 0;
                            continue;
                        }
                        if (pendingPrepareForSleep) {
                            linesSinceSignal++;
                            String trimmed = line.trim();
                            if (trimmed.startsWith("BOOLEAN true")) {
                                SteamDeckEssentials.LOGGER.info("[POWER] PrepareForSleep(true) received via D-Bus.");
                                onSuspendTriggered("D-Bus PrepareForSleep");
                                pendingPrepareForSleep = false;
                            } else if (trimmed.startsWith("BOOLEAN false") || linesSinceSignal > 8) {
                                pendingPrepareForSleep = false;
                            }
                        }
                    }
                } finally {
                    process.destroy();
                    busctlProcess = null;
                }
            } catch (Exception e) {
                SteamDeckEssentials.LOGGER.debug("[POWER] busctl unavailable (Flatpak?), using clock watchdog only", e);
            }
        }, "SteamDeck-Suspend-Dbus");
        dbusThread.setDaemon(true);
        dbusThread.start();
    }

    private void startWatchdog() {
        watchdogThread = new Thread(() -> {
            long lastWall = System.currentTimeMillis();
            long lastMono = System.nanoTime();
            while (running.get()) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    break;
                }
                long wall = System.currentTimeMillis();
                long mono = System.nanoTime();
                long wallElapsed = wall - lastWall;
                long monoElapsed = (mono - lastMono) / 1_000_000L;
                lastWall = wall;
                lastMono = mono;

                // Wall clock advanced a lot more than the monotonic clock -> the machine was suspended.
                // (A forward NTP jump could also cause this; a backward jump cannot.)
                if (wallElapsed - monoElapsed > SUSPEND_GAP_MS) {
                    SteamDeckEssentials.LOGGER.info("[POWER] Woke from suspend (~{} s asleep).",
                            (wallElapsed - monoElapsed) / 1000L);
                    onSuspendTriggered("Wake-Watchdog");
                }
            }
        }, "SteamDeck-Suspend-Watchdog");
        watchdogThread.setDaemon(true);
        watchdogThread.start();
    }

    /** Saves the singleplayer world on the server thread and pauses the game. */
    public void onSuspendTriggered(String source) {
        if (!ModConfig.getInstance().enableAutoSuspendSave || !PlatformDetector.isHandheld()) return;

        long now = System.currentTimeMillis();
        if (now - lastTriggerTime < 10_000L) return; // D-Bus + watchdog may both fire for one suspend
        lastTriggerTime = now;

        Minecraft client = Minecraft.getInstance();
        if (client == null) return;

        client.execute(() -> {
            if (!client.isLocalServer()) return;

            IntegratedServer server = client.getSingleplayerServer();
            if (server != null) {
                server.execute(() -> {
                    try {
                        SteamDeckEssentials.LOGGER.info("[POWER] Saving world ({})...", source);
                        server.saveEverything(true, true, false);
                    } catch (Exception e) {
                        SteamDeckEssentials.LOGGER.error("[POWER] Error during suspend save", e);
                    }
                });
            }

            if (client.gui != null && client.gui.screen() == null) {
                client.pauseGame(false);
                SteamDeckEssentials.LOGGER.info("[POWER] Singleplayer game paused for suspend.");
            }
        });
    }
}
