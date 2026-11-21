package com.steamdeck.mc.input;

import com.steamdeck.mc.SteamDeckEssentials;
import com.steamdeck.mc.config.ModConfig;
import org.lwjgl.BufferUtils;
import org.lwjgl.sdl.SDLHIDAPI;

import java.nio.ByteBuffer;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Turns off the "lizard mode" of the Steam Controller (2026) while Minecraft uses it natively.
 *
 * <p>Without Steam the controller firmware emulates a keyboard and mouse ("lizard mode"): B sends
 * Escape, A Enter, the right trackpad moves the system mouse, the triggers click. In Minecraft that
 * doubled every input: B opened the pause menu on top of its own binding, and the firmware mouse
 * fought with the mod's trackpad pointer. Steam (and SteamlessController) switch it off by clearing
 * the digital mappings and the trackpad mouse modes, and repeat that regularly because the firmware
 * silently falls back after a while without host commands. On disconnect / game exit the defaults
 * are restored so the controller works as a mouse on the desktop again.</p>
 *
 * <p>Protocol facts (docs/FINDINGS.md 6, 8.9, 10.5): feature report 0x01 = [0x01, cmd, len, payload…];
 * 0x81 clear digital mappings, 0x87 set settings ({id, u16 LE} triples, 0x07/0x08 = right/left
 * trackpad mode, 0 = none), 0x85 default mappings, 0x8E load default settings.</p>
 *
 * <p>All HID writes run on a background thread: a feature report over Bluetooth waits for the
 * controller's acknowledgement and must never stall a frame.</p>
 */
public final class LizardMode {
    private static final LizardMode INSTANCE = new LizardMode();

    private static final byte REPORT_ID = 0x01;
    private static final byte CMD_CLEAR_DIGITAL_MAPPINGS = (byte) 0x81;
    private static final byte CMD_SET_DEFAULT_MAPPINGS = (byte) 0x85;
    private static final byte CMD_SET_SETTINGS = (byte) 0x87;
    private static final byte CMD_LOAD_DEFAULT_SETTINGS = (byte) 0x8E;
    private static final byte SETTING_RIGHT_TRACKPAD_MODE = 0x07;
    private static final byte SETTING_LEFT_TRACKPAD_MODE = 0x08;
    private static final long KEEPALIVE_MS = 1000L;

    private final ScheduledExecutorService worker = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "SteamDeck-LizardMode");
        t.setDaemon(true);
        return t;
    });
    private final ByteBuffer buf = BufferUtils.createByteBuffer(64);

    // Only touched on the worker thread
    private long device = 0L;
    private String openPath = null;
    private boolean warned = false;
    private ScheduledFuture<?> keepalive = null;

    private LizardMode() {
        Runtime.getRuntime().addShutdownHook(new Thread(this::restoreNow, "SteamDeck-LizardMode-Restore"));
    }

    public static LizardMode getInstance() {
        return INSTANCE;
    }

    /** A native Steam Controller (2026) became the active controller; {@code hidrawPath} may be null. */
    public void onControllerActive(String hidrawPath) {
        if (hidrawPath == null) return;
        worker.execute(() -> {
            if (!ModConfig.getInstance().disableLizardMode) return;
            if (hidrawPath.equals(openPath) && device != 0L) return;
            closeAndRestore();
            try {
                SDLHIDAPI.SDL_hid_init();
                long h = SDLHIDAPI.SDL_hid_open_path(hidrawPath);
                if (h == 0L) {
                    warnOnce("could not open " + hidrawPath);
                    return;
                }
                device = h;
                openPath = hidrawPath;
                if (sendLizardOff()) {
                    SteamDeckEssentials.LOGGER.info("[INPUT] Steam Controller lizard mode (keyboard/mouse emulation) off via {}", hidrawPath);
                }
                keepalive = worker.scheduleAtFixedRate(this::keepaliveTick, KEEPALIVE_MS, KEEPALIVE_MS, TimeUnit.MILLISECONDS);
            } catch (Throwable t) {
                warnOnce(t.toString());
            }
        });
    }

    /** The controller is gone or replaced: give the firmware its desktop behaviour back. */
    public void onControllerInactive() {
        worker.execute(this::closeAndRestore);
    }

    private void keepaliveTick() {
        if (device == 0L) return;
        if (!ModConfig.getInstance().disableLizardMode) {
            closeAndRestore();
            return;
        }
        sendLizardOff();
    }

    private boolean sendLizardOff() {
        boolean ok = send(CMD_CLEAR_DIGITAL_MAPPINGS);
        ok &= send(CMD_SET_SETTINGS,
                SETTING_LEFT_TRACKPAD_MODE, 0, 0,
                SETTING_RIGHT_TRACKPAD_MODE, 0, 0);
        return ok;
    }

    private void closeAndRestore() {
        if (keepalive != null) {
            keepalive.cancel(false);
            keepalive = null;
        }
        if (device != 0L) {
            send(CMD_SET_DEFAULT_MAPPINGS);
            send(CMD_LOAD_DEFAULT_SETTINGS);
            try {
                SDLHIDAPI.SDL_hid_close(device);
            } catch (Throwable ignored) {}
        }
        device = 0L;
        openPath = null;
    }

    /** JVM shutdown: restore synchronously (the worker may already be gone). */
    private void restoreNow() {
        try {
            worker.submit(this::closeAndRestore).get(500, TimeUnit.MILLISECONDS);
        } catch (Throwable ignored) {}
    }

    private boolean send(byte cmd, int... payload) {
        if (device == 0L) return false;
        buf.clear();
        for (int i = 0; i < 64; i++) buf.put(i, (byte) 0);
        buf.put(0, REPORT_ID);
        buf.put(1, cmd);
        buf.put(2, (byte) payload.length);
        for (int i = 0; i < payload.length; i++) buf.put(3 + i, (byte) payload[i]);
        buf.limit(64);
        int written = SDLHIDAPI.SDL_hid_send_feature_report(device, buf);
        if (written < 0) {
            warnOnce("feature report 0x" + Integer.toHexString(cmd & 0xFF) + " failed");
            return false;
        }
        return true;
    }

    private void warnOnce(String what) {
        if (warned) return;
        warned = true;
        SteamDeckEssentials.LOGGER.warn("[INPUT] Could not switch off the Steam Controller lizard mode ({}). "
                + "B may also act as Escape. Workaround: echo N | sudo tee /sys/module/hid_steam/parameters/lizard_mode", what);
    }
}
