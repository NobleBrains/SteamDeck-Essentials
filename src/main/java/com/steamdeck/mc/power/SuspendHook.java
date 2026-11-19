package com.steamdeck.mc.power;

import com.steamdeck.mc.SteamDeckEssentials;
import com.steamdeck.mc.config.ModConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Detects Linux suspend/sleep events and safely pauses and saves singleplayer sessions.
 * Combines systemd logind D-Bus PrepareForSleep monitoring with a monotonic time-gap watchdog.
 */
public class SuspendHook {
    private static final SuspendHook INSTANCE = new SuspendHook();
    private final AtomicBoolean running = new AtomicBoolean(false);
    private Thread dbusThread;
    private Thread watchdogThread;

    public static SuspendHook getInstance() {
        return INSTANCE;
    }

    private SuspendHook() {}

    public synchronized void start() {
        if (running.getAndSet(true)) return;

        // 1. D-Bus listener for systemd logind PrepareForSleep
        startDbusListener();

        // 2. Monotonic watchdog fallback
        startWatchdog();

        SteamDeckEssentials.LOGGER.info("Steam Deck suspend/sleep hook registered successfully.");
    }

    public synchronized void stop() {
        running.set(false);
        if (dbusThread != null) {
            dbusThread.interrupt();
        }
        if (watchdogThread != null) {
            watchdogThread.interrupt();
        }
    }

    private void startDbusListener() {
        dbusThread = new Thread(() -> {
            try {
                // Monitor logind signals for PrepareForSleep
                ProcessBuilder pb = new ProcessBuilder(
                        "busctl", "--system", "monitor", "org.freedesktop.login1"
                );
                pb.redirectErrorStream(true);
                Process process = pb.start();

                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                    String line;
                    boolean pendingPrepareForSleep = false;
                    int linesSinceSignal = 0;

                    while (running.get() && (line = reader.readLine()) != null) {
                        // 1. Detect signal header / member
                        if (line.contains("PrepareForSleep")) {
                            pendingPrepareForSleep = true;
                            linesSinceSignal = 0;
                            continue;
                        }

                        // 2. Check subsequent lines for the boolean parameter
                        if (pendingPrepareForSleep) {
                            linesSinceSignal++;

                            if (line.contains("true") || line.contains("BOOLEAN true")) {
                                SteamDeckEssentials.LOGGER.info("PrepareForSleep(true) detected via D-Bus.");
                                onSuspendTriggered();
                                pendingPrepareForSleep = false;
                            } else if (line.contains("false") || line.contains("BOOLEAN false")) {
                                // PrepareForSleep(false) is the wake signal
                                pendingPrepareForSleep = false;
                            } else if (linesSinceSignal > 6 || line.contains("Type=") || line.contains("Member=")) {
                                pendingPrepareForSleep = false;
                            }
                        }
                    }
                } finally {
                    process.destroy();
                }
            } catch (Exception e) {
                SteamDeckEssentials.LOGGER.debug("D-Bus monitor unavailable, relying on monotonic watchdog", e);
            }
        }, "SteamDeck-Suspend-Dbus");
        dbusThread.setDaemon(true);
        dbusThread.start();
    }

    private void startWatchdog() {
        watchdogThread = new Thread(() -> {
            long lastTime = System.currentTimeMillis();
            while (running.get()) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    break;
                }

                long currentTime = System.currentTimeMillis();
                long elapsed = currentTime - lastTime;
                lastTime = currentTime;

                // If sleep time significantly exceeded 1000ms (e.g. > 3500ms), system was suspended
                if (elapsed > 3500) {
                    SteamDeckEssentials.LOGGER.info("System clock jump detected ({}ms elapsed) - wake from suspend.", elapsed);
                    onSuspendTriggered();
                }
            }
        }, "SteamDeck-Suspend-Watchdog");
        watchdogThread.setDaemon(true);
        watchdogThread.start();
    }

    /**
     * Executes singleplayer pause and immediate chunk flush.
     */
    public void onSuspendTriggered() {
        if (!ModConfig.getInstance().enableAutoSuspendSave) return;

        Minecraft client = Minecraft.getInstance();
        if (client == null) return;

        client.execute(() -> {
            if (client.isLocalServer()) {
                IntegratedServer server = client.getSingleplayerServer();
                if (server != null) {
                    SteamDeckEssentials.LOGGER.info("Initiating emergency world save before suspend...");
                    try {
                        server.saveAllChunks(true, true, true);
                    } catch (Exception e) {
                        SteamDeckEssentials.LOGGER.error("Error during emergency save on suspend", e);
                    }
                }

                // Pause game if not already paused (Minecraft 26.3 Screen check via client.gui.screen())
                if (client.gui != null && client.gui.screen() == null) {
                    client.pauseGame(false);
                    SteamDeckEssentials.LOGGER.info("Singleplayer game paused automatically for suspend.");
                }
            }
        });
    }
}
