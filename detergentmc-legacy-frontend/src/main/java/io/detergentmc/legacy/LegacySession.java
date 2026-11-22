package io.detergentmc.legacy;

import io.detergentmc.legacy.LegacyPacketIO.LegacyPacket;
import io.detergentmc.protocol.MinecraftPacket;

import java.io.Closeable;
import java.io.EOFException;
import java.io.IOException;
import java.net.Socket;
import java.security.KeyPair;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * One legacy client's session: legacy ping, or the legacy login
 * sequence (terminated here, encryption included), then a play-phase
 * pump to the far side — the core proxy as a synthetic protocol-5
 * client (bridge mode) or an internal legacy vanilla as a legacy
 * client (direct mode).
 *
 * <p>Legacy generations are offline-only: the old Mojang session
 * endpoints are gone, so no online authentication is attempted on
 * either face.
 */
public final class LegacySession implements Runnable {

    private static final Logger LOG = Logger.getLogger(LegacySession.class.getName());

    private final LegacyFrontendConfig config;
    private final KeyPair keyPair;
    private final Socket clientSocket;
    private final java.util.function.IntSupplier onlineCount;
    private final Runnable onClose;
    private final AtomicBoolean closed = new AtomicBoolean();

    private LegacyConnection client;
    private Closeable farSide;
    /** The client's legacy protocol, adopted at the handshake. */
    private int clientProtocol = LegacyProtocol.V1_6_4;

    public LegacySession(LegacyFrontendConfig config, KeyPair keyPair, Socket clientSocket,
                         java.util.function.IntSupplier onlineCount, Runnable onClose) {
        this.config = config;
        this.keyPair = keyPair;
        this.clientSocket = clientSocket;
        this.onlineCount = onlineCount;
        this.onClose = onClose;
    }

    @Override
    public void run() {
        try {
            clientSocket.setSoTimeout(10_000);
            client = new LegacyConnection(clientSocket);
            int first = client.readRawByte();
            if (first == 0xFE) {
                handlePing();
                return;
            }
            if (first != 0x02) {
                return; // not a legacy client at all
            }
            int second = client.readRawByte();
            client.feedRaw(new byte[]{0x02, (byte) second});
            String username;
            if (second == 0x00) {
                // A plaintext-generation handshake (protocols 29,
                // 28, 23, 22 — 1.2.5 down to 1.0.0): the byte after
                // 0x02 is the high byte of the handshake string's
                // length — the string is a username, optionally
                // followed by ";host:port", always far shorter
                // than 256 chars. Protocols 39-78 put their
                // protocol number (39..78, never 0) in this byte
                // instead, so the two login forms can be told
                // apart from the first two bytes alone. The
                // connection starts on the protocol-29 table,
                // whose single-string handshake definition all
                // four plaintext generations share; the exact
                // protocol is adopted from the login packet inside
                // legacyLoginPlaintext().
                client.setLegacyProtocol(LegacyProtocol.V1_2_5);
                clientProtocol = LegacyProtocol.V1_2_5;
                username = legacyLoginPlaintext();
            } else {
                username = legacyLogin();
            }
            if (username == null) {
                return;
            }
            clientSocket.setSoTimeout(60_000);
            if (config.mode() == LegacyFrontendConfig.Mode.BRIDGE) {
                runBridge(username);
            } else {
                runDirect(username);
            }
        } catch (EOFException e) {
            // peer went away; normal
        } catch (IOException e) {
            LOG.fine("legacy session ended: " + e.getMessage());
        } catch (RuntimeException e) {
            LOG.log(Level.FINE, "legacy session failed", e);
        } finally {
            closeBoth();
            onClose.run();
        }
    }

    // ------------------------------------------------------------------
    // Ping
    // ------------------------------------------------------------------

    private void handlePing() throws IOException {
        boolean section1 = false;
        try {
            clientSocket.setSoTimeout(350);
            int second = client.readRawByte();
            if (second == 0x01) {
                // Section-1 is answered only when the full PingHost
                // form follows (an 0xFA plugin message) — this mirrors
                // real 1.6.1, the oldest supported legacy version,
                // which answers a bare FE 01 with the old form. Real
                // 1.6.x clients always send the full form, so they get
                // section-1 exactly like against 1.6.2/1.6.4 vanilla.
                try {
                    section1 = client.readRawByte() == 0xFA;
                } catch (IOException ignored) {
                    // bare FE 01: the old form
                }
            }
        } catch (IOException ignored) {
            // no second byte: the pre-1.4 ping form
        }
        byte[] response = section1
                ? LegacyPing.section1Response(config.legacyProtocol(), config.versionName(),
                        config.motd(), onlineCount.getAsInt(), config.maxPlayers())
                : LegacyPing.oldResponse(config.motd(), onlineCount.getAsInt(),
                        config.maxPlayers());
        client.sendRaw(response);
    }

    // ------------------------------------------------------------------
    // Login (client face)
    // ------------------------------------------------------------------

    /**
     * Runs the legacy login sequence up to (and including) the
     * client's 0x01 login packet.
     *
     * @return the username from the handshake, or null to abort
     */
    private String legacyLogin() throws IOException {
        LegacyPacket handshake = requireClientPacket(0x02);
        int protocol = handshake.intField(0);
        String username = (String) handshake.field(1);
        if (!LegacyProtocol.isSupported(protocol)) {
            List<Integer> supported = LegacyProtocol.supportedLegacyProtocols();
            int oldest = supported.get(supported.size() - 1);
            kick(protocol < oldest ? "Outdated client!" : "Outdated server!");
            return null;
        }
        // Direct mode is a dumb pipe to one legacy vanilla: a client
        // of any other legacy protocol gets the same kick the vanilla
        // itself would send.
        if (config.mode() == LegacyFrontendConfig.Mode.DIRECT
                && protocol != config.legacyProtocol()) {
            kick(protocol < config.legacyProtocol()
                    ? "Outdated client!" : "Outdated server!");
            return null;
        }
        client.setLegacyProtocol(protocol);
        clientProtocol = protocol;

        byte[] token = LegacyCrypto.randomBytes(4);
        client.sendPacket(new LegacyPacket(0xFD, List.of("-",
                LegacyCrypto.publicKeyBytes(keyPair.getPublic()), token)));

        LegacyPacket response = requireClientPacket(0xFC);
        byte[] secret;
        try {
            secret = LegacyCrypto.rsaDecrypt(keyPair.getPrivate(), (byte[]) response.field(0));
            byte[] tokenBack = LegacyCrypto.rsaDecrypt(keyPair.getPrivate(),
                    (byte[]) response.field(1));
            if (!Arrays.equals(token, tokenBack)) {
                return null;
            }
        } catch (java.security.GeneralSecurityException e) {
            return null;
        }
        // Our 0xFC goes out in plaintext; encryption starts only
        // after both 0xFC packets have been exchanged (the real
        // 1.6.4 order — see LegacyBackendLogin). Sending it encrypted
        // would leave a real client unable to read it.
        client.sendPacket(new LegacyPacket(0xFC,
                List.of(new byte[0], new byte[0])));
        client.enableEncryption(secret);

        LegacyPacket login = requireClientPacket(0x01);
        if (login.intField(0) != protocol) {
            kick("Outdated client!");
            return null;
        }
        // The client completes its login burst with 0xCD client
        // status (payload 0 = initial spawn), sent unprompted; a
        // real server consumes it as the login-completion trigger.
        // Consume it here too: relaying it onward would hand the
        // backend a second status-0 mid-play (a respawn request).
        requireClientPacket(0xCD);
        if (username == null || username.isEmpty() || username.length() > 16) {
            return null;
        }
        return username;
    }

    /**
     * The plaintext login sequence of protocols 29, 28, 23 and 22
     * (1.2.5 down to 1.0.0): these generations are below the
     * encryption boundary, so there is no 0xFD / 0xFC exchange at
     * all. The handshake is a single string the vanilla server
     * itself ignores; the server answers with the connection-hash
     * string ("-" offline), and the protocol number is checked on
     * the client's 0x01 login packet — this mirrors the real
     * servers' own sequence and kick strings (measured in their
     * jars, docs/PROXY-1.2.5.md, docs/PROXY-1.2.3.md,
     * docs/PROXY-1.1.md, docs/PROXY-1.0.md). The four generations'
     * login packets have different layouts, so the protocol is
     * read from the login packet's first five raw bytes before
     * the packet is parsed: the connection adopts it, the peeked
     * bytes are fed back into the parser, and the packet is then
     * read normally with the correct table.
     *
     * @return the username from the login packet, or null to abort
     */
    private String legacyLoginPlaintext() throws IOException {
        requireClientPacket(0x02); // handshake string; content unused
        client.sendPacket(new LegacyPacket(0x02, List.of("-")));
        // Peek the login packet's protocol number (packet id +
        // i32) before parsing: the four plaintext generations lay
        // the rest of the packet out differently.
        byte[] head = new byte[5];
        for (int i = 0; i < head.length; i++) {
            head[i] = (byte) client.readRawByte();
        }
        if ((head[0] & 0xFF) != 0x01) {
            throw new IOException("expected legacy login packet 0x01, got 0x"
                    + Integer.toHexString(head[0] & 0xFF));
        }
        int protocol = ((head[1] & 0xFF) << 24) | ((head[2] & 0xFF) << 16)
                | ((head[3] & 0xFF) << 8) | (head[4] & 0xFF);
        if (!LegacyProtocol.isPlaintextGeneration(protocol)) {
            kick(protocol < LegacyProtocol.V1_0
                    ? "Outdated client!" : "Outdated server!");
            return null;
        }
        client.setLegacyProtocol(protocol);
        clientProtocol = protocol;
        // Direct mode is a dumb pipe to one legacy vanilla: a
        // client of any other generation gets the same kick the
        // vanilla itself would send.
        if (config.mode() == LegacyFrontendConfig.Mode.DIRECT
                && protocol != config.legacyProtocol()) {
            kick(protocol < config.legacyProtocol()
                    ? "Outdated client!" : "Outdated server!");
            return null;
        }
        client.feedRaw(head);
        LegacyPacket login = requireClientPacket(0x01);
        if (login.intField(0) != protocol) {
            kick("Outdated client!");
            return null;
        }
        String username = (String) login.field(1);
        if (username == null || username.isEmpty() || username.length() > 16) {
            return null;
        }
        return username;
    }

    private LegacyPacket requireClientPacket(int expectedId) throws IOException {
        long deadline = System.currentTimeMillis() + 10_000;
        while (System.currentTimeMillis() < deadline) {
            LegacyPacket packet = client.readPacket();
            if (packet == null) {
                continue;
            }
            if (packet.id() != expectedId) {
                throw new IOException("expected legacy packet 0x"
                        + Integer.toHexString(expectedId) + ", got 0x"
                        + Integer.toHexString(packet.id()));
            }
            return packet;
        }
        throw new EOFException("login timed out");
    }

    private void kick(String reason) {
        try {
            client.sendPacket(new LegacyPacket(0xFF, List.of(reason)));
        } catch (IOException | RuntimeException ignored) {
            // closing anyway
        }
    }

    // ------------------------------------------------------------------
    // Bridge mode
    // ------------------------------------------------------------------

    private void runBridge(String username) throws IOException {
        ModernBridgeConnection modern = ModernBridgeConnection.connectAndLogin(
                config.coreHost(), config.corePort(), username);
        farSide = modern;
        LegacyPlayTranslator translator = new LegacyPlayTranslator(clientProtocol);
        LegacyPlayTranslator.State state = new LegacyPlayTranslator.State();

        Object gate = new Object();
        boolean[] joined = {false};
        Queue<MinecraftPacket> earlyClientPackets = new ArrayDeque<>();

        Thread clientPump = new Thread(() -> {
            try {
                while (!closed.get()) {
                    LegacyPacket packet = client.readPacket();
                    if (packet == null) {
                        continue;
                    }
                    Optional<MinecraftPacket> translated = translator.toModern(packet, state);
                    if (translated.isEmpty()) {
                        continue;
                    }
                    synchronized (gate) {
                        if (!joined[0]) {
                            if (earlyClientPackets.size() < 256) {
                                earlyClientPackets.add(translated.get());
                            }
                            continue;
                        }
                    }
                    modern.send(translated.get());
                }
            } catch (IOException | RuntimeException e) {
                // fall through to close
            } finally {
                closeBoth();
            }
        }, "legacy-frontend-bridge-client-pump");
        clientPump.setDaemon(true);
        clientPump.start();

        // Modern -> legacy pump runs on this thread. Packets before the
        // join game are buffered (in practice the core sends join
        // first); the join game itself becomes the legacy login packet.
        Queue<LegacyPacket> preJoin = new ArrayDeque<>();
        try {
            while (!closed.get()) {
                MinecraftPacket packet = modern.readPacket();
                if (packet == null) {
                    continue;
                }
                // Almost every packet translates to exactly one
                // legacy packet; protocol 29 can produce several
                // (chunk pairs, split destroys), each gated on the
                // join exactly like a single packet.
                List<LegacyPacket> translated = translator.toLegacyAll(packet, state);
                for (LegacyPacket legacy : translated) {
                    boolean sendNow;
                    synchronized (gate) {
                        if (!joined[0] && legacy.id() != 0x01) {
                            preJoin.add(legacy);
                            sendNow = false;
                        } else if (!joined[0]) {
                            client.sendPacket(legacy);
                            joined[0] = true;
                            for (LegacyPacket queued : preJoin) {
                                client.sendPacket(queued);
                            }
                            preJoin.clear();
                            for (MinecraftPacket queued : earlyClientPackets) {
                                modern.send(queued);
                            }
                            earlyClientPackets.clear();
                            sendNow = false;
                        } else {
                            sendNow = true;
                        }
                    }
                    if (sendNow) {
                        client.sendPacket(legacy);
                    }
                }
            }
        } finally {
            closeBoth();
            try {
                clientPump.join(2_000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    // ------------------------------------------------------------------
    // Direct mode
    // ------------------------------------------------------------------

    private void runDirect(String username) throws IOException {
        LegacyBackendLogin.Result backend = LegacyBackendLogin.connectAndLogin(
                config.backendHost(), config.backendPort(), username, clientProtocol);
        LegacyConnection server = backend.connection();
        farSide = server;
        // The backend's login response becomes the client's.
        client.sendPacket(backend.loginResponse());

        Thread clientPump = new Thread(() -> {
            try {
                while (!closed.get()) {
                    LegacyPacket packet = client.readPacket();
                    if (packet == null) {
                        continue;
                    }
                    // Reserialize (validates against the table); the
                    // bytes are identical for well-formed packets.
                    server.sendPacket(packet);
                }
            } catch (IOException | RuntimeException e) {
                // fall through to close
            } finally {
                closeBoth();
            }
        }, "legacy-frontend-direct-client-pump");
        clientPump.setDaemon(true);
        clientPump.start();

        try {
            while (!closed.get()) {
                LegacyPacket packet = server.readPacket();
                if (packet == null) {
                    continue;
                }
                client.sendPacket(packet);
            }
        } finally {
            closeBoth();
            try {
                clientPump.join(2_000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    // ------------------------------------------------------------------

    private void closeBoth() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        if (client != null) {
            client.close();
        }
        if (farSide != null) {
            try {
                farSide.close();
            } catch (IOException ignored) {
                // closing anyway
            }
        }
    }
}
