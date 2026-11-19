package com.steamdeck.mc.input.gyro;

import com.steamdeck.mc.SteamDeckEssentials;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Universal DSU (Cemuhook) UDP motion client for SteamDeckGyroDSU on port 26760.
 * Operates reliably across SteamOS desktop mode, Game mode, and flatpak containers.
 * Only reports available when actual UDP motion packets are received from the server.
 */
public class DsuGyroProvider implements GyroProvider {
    private static final int DEFAULT_PORT = 26760;
    private static final String HOST = "127.0.0.1";

    private DatagramSocket socket;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private Thread listenerThread;
    private volatile float latestPitch = 0.0f;
    private volatile float latestYaw = 0.0f;
    private volatile long lastPacketTime = 0L;

    @Override
    public boolean init() {
        try {
            socket = new DatagramSocket();
            socket.setSoTimeout(500);
            running.set(true);

            sendSubscription();

            listenerThread = new Thread(this::listenLoop, "SteamDeck-DSU-Gyro");
            listenerThread.setDaemon(true);
            listenerThread.start();

            return true;
        } catch (Exception e) {
            SteamDeckEssentials.LOGGER.debug("DSU Gyro provider failed to bind", e);
            return false;
        }
    }

    private void sendSubscription() {
        try {
            byte[] packetData = new byte[16];
            ByteBuffer buf = ByteBuffer.wrap(packetData).order(ByteOrder.LITTLE_ENDIAN);
            buf.put(new byte[]{'D', 'S', 'U', 'S'}); // Magic
            buf.putShort((short) 1001);             // Protocol version
            buf.putShort((short) 0);                // Length
            buf.putInt(0);                          // CRC32
            buf.putInt(0x100001);                   // Client ID

            DatagramPacket packet = new DatagramPacket(packetData, packetData.length, InetAddress.getByName(HOST), DEFAULT_PORT);
            socket.send(packet);
        } catch (Exception ignored) {}
    }

    private void listenLoop() {
        byte[] buffer = new byte[128];
        long lastSubscribeTime = 0;

        while (running.get() && socket != null && !socket.isClosed()) {
            try {
                long now = System.currentTimeMillis();
                if (now - lastSubscribeTime > 2000) {
                    sendSubscription();
                    lastSubscribeTime = now;
                }

                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);

                if (packet.getLength() >= 80) {
                    ByteBuffer bb = ByteBuffer.wrap(packet.getData(), 0, packet.getLength()).order(ByteOrder.LITTLE_ENDIAN);
                    float pitch = bb.getFloat(56);
                    float yaw = bb.getFloat(60);

                    if (Math.abs(pitch) < 0.02f) pitch = 0.0f;
                    if (Math.abs(yaw) < 0.02f) yaw = 0.0f;

                    latestPitch += pitch;
                    latestYaw += yaw;
                    lastPacketTime = System.currentTimeMillis();
                }
            } catch (Exception ignored) {}
        }
    }

    @Override
    public GyroData poll() {
        if (!isAvailable()) return GyroData.ZERO;

        float p = latestPitch;
        float y = latestYaw;
        latestPitch = 0.0f;
        latestYaw = 0.0f;
        return new GyroData(p * 0.15f, y * 0.15f);
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
        return "SteamDeck DSU (UDP:26760)";
    }
}
