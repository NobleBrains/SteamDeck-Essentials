package com.steamdeck.mc.input.gyro;

import com.steamdeck.mc.SteamDeckEssentials;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.CRC32;

/**
 * DSU / Cemuhook UDP motion client (e.g. SteamDeckGyroDSU on 127.0.0.1:26760).
 *
 * <p>Protocol (little endian), see the cemuhook "DSU protocol" description:</p>
 * <pre>
 *  0  magic "DSUC" (client) / "DSUS" (server)
 *  4  u16 protocol version (1001)
 *  6  u16 length of everything after the 16-byte header
 *  8  u32 CRC32 of the whole packet with this field zeroed
 * 12  u32 client/server id
 * 16  u32 message type (0x100002 = pad data)
 * -- pad data response --
 * 20  slot, 31 connected, 68 u64 motion timestamp (µs),
 * 76  accel x/y/z (g), 88 gyro pitch/yaw/roll (deg/s)   -> packet length 100
 * </pre>
 *
 * <p>The previous implementation sent a request with server magic, no message type and no CRC
 * (ignored by servers) and read the gyro from offsets 56/60 (touch data). Rates are now integrated
 * per packet using the motion timestamps, so the result is independent of packet and frame rate.</p>
 */
public class DsuGyroProvider implements GyroProvider {
    private static final int DEFAULT_PORT = 26760;
    private static final String HOST = "127.0.0.1";
    private static final int MSG_PAD_DATA = 0x100002;
    private static final int PROTOCOL_VERSION = 1001;
    private static final float DEADZONE_DEG_PER_SEC = 0.8f;

    private final int clientId = (int) (Math.random() * Integer.MAX_VALUE);
    private DatagramSocket socket;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private Thread listenerThread;

    // Guarded by 'this'
    private float accumulatedPitch = 0.0f;
    private float accumulatedYaw = 0.0f;
    private long lastMotionTimestampUs = 0L;
    private int lockedSlot = -1;

    private volatile long lastPacketTime = 0L;

    @Override
    public boolean init() {
        try {
            socket = new DatagramSocket();
            socket.setSoTimeout(500);
            running.set(true);

            listenerThread = new Thread(this::listenLoop, "SteamDeck-DSU-Gyro");
            listenerThread.setDaemon(true);
            listenerThread.start();
            return true;
        } catch (Exception e) {
            SteamDeckEssentials.LOGGER.debug("DSU Gyro provider failed to bind", e);
            return false;
        }
    }

    /** "Pad data request": subscribe to all slots (flags = 0). */
    private void sendSubscription() {
        try {
            byte[] data = new byte[28];
            ByteBuffer buf = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
            buf.put(new byte[]{'D', 'S', 'U', 'C'});
            buf.putShort((short) PROTOCOL_VERSION);
            buf.putShort((short) (data.length - 16));
            buf.putInt(0); // CRC placeholder
            buf.putInt(clientId);
            buf.putInt(MSG_PAD_DATA);
            buf.put((byte) 0); // flags: all controllers
            buf.put((byte) 0); // slot
            // 6 byte MAC = 0 (already zero)

            CRC32 crc = new CRC32();
            crc.update(data, 0, data.length);
            ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN).putInt(8, (int) crc.getValue());

            socket.send(new DatagramPacket(data, data.length, InetAddress.getByName(HOST), DEFAULT_PORT));
        } catch (Exception ignored) {}
    }

    private void listenLoop() {
        byte[] buffer = new byte[256];
        long lastSubscribeTime = 0;

        while (running.get() && socket != null && !socket.isClosed()) {
            try {
                long now = System.currentTimeMillis();
                if (now - lastSubscribeTime > 2000) { // servers drop subscriptions after ~5 s
                    sendSubscription();
                    lastSubscribeTime = now;
                }

                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);
                handlePacket(ByteBuffer.wrap(packet.getData(), 0, packet.getLength()).order(ByteOrder.LITTLE_ENDIAN),
                        packet.getLength());
            } catch (java.net.SocketTimeoutException ignored) {
                // no server running - keep polling
            } catch (Exception e) {
                if (running.get()) {
                    SteamDeckEssentials.LOGGER.debug("DSU receive error", e);
                }
            }
        }
    }

    private void handlePacket(ByteBuffer bb, int length) {
        if (length < 100) return;
        if (bb.get(0) != 'D' || bb.get(1) != 'S' || bb.get(2) != 'U' || bb.get(3) != 'S') return;
        if (bb.getInt(16) != MSG_PAD_DATA) return;

        int slot = bb.get(20) & 0xFF;
        boolean connected = bb.get(31) != 0;
        if (!connected) return;

        long timestampUs = bb.getLong(68);
        float pitchRate = bb.getFloat(88);
        float yawRate = bb.getFloat(92);
        float rollRate = bb.getFloat(96);

        synchronized (this) {
            if (lockedSlot < 0) {
                lockedSlot = slot;
                SteamDeckEssentials.LOGGER.info("[GYRO] DSU motion server found (slot {}).", slot);
            }
            if (slot != lockedSlot) return;

            if (lastMotionTimestampUs != 0L && timestampUs > lastMotionTimestampUs) {
                float dt = Math.min(0.1f, (timestampUs - lastMotionTimestampUs) / 1_000_000.0f);
                GyroData d = GyroData.fromRates(pitchRate, yawRate, rollRate, dt, DEADZONE_DEG_PER_SEC);
                accumulatedPitch += d.pitchDelta();
                accumulatedYaw += d.yawDelta();
            }
            lastMotionTimestampUs = timestampUs;
        }
        lastPacketTime = System.currentTimeMillis();
    }

    @Override
    public GyroData poll() {
        synchronized (this) {
            if (!isAvailable()) {
                accumulatedPitch = 0.0f;
                accumulatedYaw = 0.0f;
                return GyroData.ZERO;
            }
            GyroData data = new GyroData(accumulatedPitch, accumulatedYaw);
            accumulatedPitch = 0.0f;
            accumulatedYaw = 0.0f;
            return data;
        }
    }

    @Override
    public boolean isAvailable() {
        return (System.currentTimeMillis() - lastPacketTime) < 1500L;
    }

    @Override
    public void close() {
        running.set(false);
        if (socket != null && !socket.isClosed()) {
            socket.close();
        }
    }

    @Override
    public String getName() {
        return "DSU (UDP:26760)";
    }
}
