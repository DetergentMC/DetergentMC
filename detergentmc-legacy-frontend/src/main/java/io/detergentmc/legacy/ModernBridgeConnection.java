package io.detergentmc.legacy;

import io.detergentmc.protocol.MinecraftPacket;
import io.detergentmc.protocol.PacketIO;

import java.io.Closeable;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.ArrayDeque;
import java.util.Queue;

/**
 * The bridge mode's modern face: a protocol-5 (1.7.10) client
 * connection to the DetergentMC core proxy, over a plain socket with
 * modern (VarInt-framed) packets. The login is the offline-mode form
 * (a synthetic client cannot perform Mojang authentication — which
 * matches the legacy generations being offline-only by design). If
 * the core answers with an encryption request, the login fails with
 * a clear error: bridge mode requires the core in offline mode.
 */
public final class ModernBridgeConnection implements Closeable {

    private final Socket socket;
    private final InputStream in;
    private final OutputStream out;
    private final MinecraftPacket.Decoder decoder = new MinecraftPacket.Decoder();
    private final Queue<MinecraftPacket> pending = new ArrayDeque<>();
    private final byte[] readBuffer = new byte[65536];

    private ModernBridgeConnection(Socket socket) throws IOException {
        this.socket = socket;
        this.in = socket.getInputStream();
        this.out = socket.getOutputStream();
    }

    /**
     * Connects to the core and performs the protocol-5 offline login
     * for {@code username}.
     *
     * @throws IOException on connect failure, a disconnect during
     *         login, or when the core requests encryption (online mode)
     */
    public static ModernBridgeConnection connectAndLogin(String host, int port,
                                                         String username)
            throws IOException {
        Socket socket = new Socket();
        socket.connect(new InetSocketAddress(host, port), 10_000);
        socket.setSoTimeout(15_000);
        ModernBridgeConnection connection = new ModernBridgeConnection(socket);
        try {
            connection.login(username, host, port);
            socket.setSoTimeout(60_000);
            return connection;
        } catch (IOException | RuntimeException e) {
            connection.close();
            throw e;
        }
    }

    private void login(String username, String host, int port) throws IOException {
        PacketIO.Writer handshake = new PacketIO.Writer();
        handshake.writeVarInt(LegacyProtocol.BRIDGE_CLIENT_PROTOCOL);
        handshake.writeString(host);
        handshake.writeShort(port);
        handshake.writeVarInt(2); // login state
        send(new MinecraftPacket(0, handshake.bytes()));

        PacketIO.Writer loginStart = new PacketIO.Writer();
        loginStart.writeString(username);
        send(new MinecraftPacket(0, loginStart.bytes()));

        long deadline = System.currentTimeMillis() + 15_000;
        while (System.currentTimeMillis() < deadline) {
            MinecraftPacket packet = readPacket();
            if (packet == null) {
                continue;
            }
            switch (packet.id()) {
                case 2 -> {
                    // Login Success: uuid string, name string, properties.
                    return;
                }
                case 1 -> throw new IOException("the core requested encryption "
                        + "(online mode); legacy bridge mode requires the core "
                        + "in offline mode");
                case 0 -> {
                    String reason = "";
                    try {
                        reason = new PacketIO.Reader(packet.payload()).readString();
                    } catch (IllegalArgumentException ignored) {
                        // keep the generic message
                    }
                    throw new IOException("the core disconnected the bridge login: " + reason);
                }
                default -> {
                    // Set Compression (id 3) never occurs: the core does
                    // not use compression. Anything else is unexpected.
                    throw new IOException("unexpected packet during bridge login: "
                            + packet.id());
                }
            }
        }
        throw new IOException("bridge login timed out");
    }

    public synchronized void send(MinecraftPacket packet) throws IOException {
        out.write(packet.encode());
        out.flush();
    }

    /**
     * Reads the next packet.
     *
     * @return the packet, or null on socket timeout (caller retries)
     * @throws EOFException when the core closed the connection
     */
    public MinecraftPacket readPacket() throws IOException {
        while (true) {
            MinecraftPacket packet = pending.poll();
            if (packet != null) {
                return packet;
            }
            int n;
            try {
                n = in.read(readBuffer);
            } catch (SocketTimeoutException e) {
                return null;
            }
            if (n < 0) {
                throw new EOFException("core connection closed");
            }
            pending.addAll(decoder.feed(java.util.Arrays.copyOf(readBuffer, n)));
        }
    }

    @Override
    public void close() {
        try {
            socket.close();
        } catch (IOException ignored) {
            // closing anyway
        }
    }
}
