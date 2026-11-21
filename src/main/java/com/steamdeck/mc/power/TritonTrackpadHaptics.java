package com.steamdeck.mc.power;

import com.steamdeck.mc.SteamDeckEssentials;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Drives the haptic actuators INSIDE the two trackpads of the 2026 Steam Controller ("Triton").
 *
 * <p>SDL's rumble API only reaches the two grip motors. The pad actuators are controlled with the
 * vendor output report {@code 0x82} ("haptic command"), written straight to the controller's
 * hidraw node: {@code [0x82, side (0 = left, 1 = right), command (1 = tick, 2 = click), gain dB (int8)]}.
 * Protocol as documented by SteamlessController (MIT, src/steam/SteamController.cpp); the report
 * length (3 data bytes) matches this controller's HID report descriptor.</p>
 *
 * <p>Writes happen on a background thread so a slow Bluetooth write never stalls a frame.</p>
 */
public final class TritonTrackpadHaptics {
    public static final byte COMMAND_TICK = 1;
    public static final byte COMMAND_CLICK = 2;

    private static final byte REPORT_HAPTIC_COMMAND = (byte) 0x82;
    /** The waveform needs ~50 ms; a new command on the same pad before that is dropped by the firmware. */
    private static final long MIN_GAP_NANOS = 50_000_000L;

    private static final TritonTrackpadHaptics INSTANCE = new TritonTrackpadHaptics();

    private final ExecutorService writer = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "SteamDeck-Triton-Haptics");
        t.setDaemon(true);
        return t;
    });
    private final long[] lastSendNanos = new long[2];

    private volatile String devicePath = null; // resolved hidraw node of the active controller
    private FileOutputStream out = null;        // only touched by the writer thread
    private String openPath = null;
    private volatile boolean warned = false;

    public static TritonTrackpadHaptics getInstance() {
        return INSTANCE;
    }

    private TritonTrackpadHaptics() {}

    /**
     * Sets the controller to address. {@code sdlPath} is SDL_GetGamepadPath() (usually "/dev/hidrawN"
     * for HIDAPI devices); if it is not a hidraw node, the node is looked up via sysfs.
     */
    public void setDevice(String sdlPath, int vendor, int product) {
        String path = null;
        if (sdlPath != null && sdlPath.startsWith("/dev/hidraw")) {
            path = sdlPath;
        } else {
            path = findHidraw(vendor, product);
        }
        devicePath = path;
        warned = false;
        SteamDeckEssentials.LOGGER.info("[HAPTIC] Steam Controller trackpad haptics via {}", path != null ? path : "(kein hidraw gefunden)");
    }

    public void clearDevice() {
        devicePath = null;
    }

    public boolean isAvailable() {
        return devicePath != null;
    }

    /** Resolved hidraw node of the active Steam Controller (2026), or null. */
    public String getDevicePath() {
        return devicePath;
    }

    /**
     * One-shot haptic on a trackpad, parameterised like Steam Input: {@code intensity} 0..3 and a dB offset.
     * The gain byte follows steamclient (FINDINGS 6.2): {@code gain = dB - 12 + 3 * intensity} (intensity 1..3),
     * plus the user's per-pad level (+-6 dB per step around "normal").
     * Returns false if no device is set (caller may fall back to rumble).
     */
    public boolean pulse(boolean rightPad, byte command, int intensity, int db) {
        String path = devicePath;
        if (path == null) return false;

        int side = rightPad ? 1 : 0;
        com.steamdeck.mc.config.ModConfig cfg = com.steamdeck.mc.config.ModConfig.getInstance();
        int level = rightPad ? cfg.rightTrackpadHapticLevel : cfg.leftTrackpadHapticLevel;
        if (level <= 0) return true; // silenced on purpose: no rumble fallback either
        int gain = db - 12 + (intensity >= 1 && intensity <= 3 ? 3 * intensity : 0) + (level - 3) * 6;

        long now = System.nanoTime();
        synchronized (lastSendNanos) {
            if (command != COMMAND_CLICK && now - lastSendNanos[side] < MIN_GAP_NANOS) {
                return true; // previous waveform still playing: drop ticks, never queue them
            }
            lastSendNanos[side] = now;
        }

        byte[] report = {REPORT_HAPTIC_COMMAND, (byte) side, command, (byte) Math.max(-30, Math.min(12, gain))};
        writer.execute(() -> write(path, report));
        return true;
    }

    private void write(String path, byte[] report) {
        try {
            if (out == null || !path.equals(openPath)) {
                closeQuietly();
                out = new FileOutputStream(path);
                openPath = path;
            }
            out.write(report);
        } catch (IOException e) {
            closeQuietly();
            if (!warned) {
                warned = true;
                SteamDeckEssentials.LOGGER.warn("[HAPTIC] Write to {} failed ({}). Missing hidraw permission?", path, e.getMessage());
            }
        }
    }

    private void closeQuietly() {
        if (out != null) {
            try {
                out.close();
            } catch (IOException ignored) {}
        }
        out = null;
        openPath = null;
    }

    /** Finds /dev/hidrawN whose HID_ID matches vendor/product and that has output report 0x82. */
    private static String findHidraw(int vendor, int product) {
        String wanted = String.format(Locale.ROOT, "%08X:%08X", vendor, product);
        try (DirectoryStream<Path> nodes = Files.newDirectoryStream(Path.of("/sys/class/hidraw"))) {
            for (Path node : nodes) {
                Path dev = node.resolve("device");
                String uevent = Files.readString(dev.resolve("uevent")).toUpperCase(Locale.ROOT);
                if (!uevent.contains(wanted)) continue;
                byte[] desc = Files.readAllBytes(dev.resolve("report_descriptor"));
                if (hasReportId(desc, REPORT_HAPTIC_COMMAND)) {
                    return "/dev/" + node.getFileName();
                }
            }
        } catch (Exception e) {
            SteamDeckEssentials.LOGGER.debug("[HAPTIC] hidraw lookup failed", e);
        }
        return null;
    }

    /** True if the HID report descriptor declares the given report id (short item 0x85). */
    private static boolean hasReportId(byte[] d, byte id) {
        int i = 0;
        while (i < d.length) {
            int b = d[i] & 0xFF;
            if (b == 0xFE) { // long item
                if (i + 1 >= d.length) break;
                i += 3 + (d[i + 1] & 0xFF);
                continue;
            }
            int size = switch (b & 0x03) { case 0 -> 0; case 1 -> 1; case 2 -> 2; default -> 4; };
            if ((b & 0xFC) == 0x84 && size >= 1 && i + 1 < d.length && d[i + 1] == id) {
                return true;
            }
            i += 1 + size;
        }
        return false;
    }
}
