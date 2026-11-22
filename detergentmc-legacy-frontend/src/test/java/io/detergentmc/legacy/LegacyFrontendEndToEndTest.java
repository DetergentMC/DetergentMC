package io.detergentmc.legacy;

import io.detergentmc.legacy.LegacyPacketIO.LegacyPacket;
import io.detergentmc.legacy.LegacyWire.Slot;
import io.detergentmc.protocol.MinecraftPacket;
import io.detergentmc.protocol.PacketIO;
import org.junit.jupiter.api.Test;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.KeyPair;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * End-to-end tests of the frontend without a real vanilla server:
 * a fake legacy backend (direct mode), a fake protocol-5 core
 * (bridge mode), and the legacy ping. The test client is the
 * frontend's own backend-face login implementation, which mirrors a
 * real 1.6.4 client.
 */
class LegacyFrontendEndToEndTest {

    // ------------------------------------------------------------------
    // Fake legacy backend (server role of the legacy protocol)
    // ------------------------------------------------------------------

    static final class FakeLegacyBackend implements Runnable {
        final ServerSocket serverSocket;
        final KeyPair keyPair = LegacyCrypto.generateKeyPair();
        final BlockingQueue<LegacyPacket> received = new LinkedBlockingQueue<>();
        final int protocol;
        volatile LegacyConnection connection;
        volatile String username;

        FakeLegacyBackend() throws IOException {
            this(LegacyProtocol.V1_6_4);
        }

        FakeLegacyBackend(int protocol) throws IOException {
            this.protocol = protocol;
            serverSocket = new ServerSocket(0);
        }

        int port() {
            return serverSocket.getLocalPort();
        }

        @Override
        public void run() {
            try (Socket socket = serverSocket.accept()) {
                socket.setSoTimeout(10_000);
                LegacyConnection conn = new LegacyConnection(socket, protocol);
                connection = conn;
                LegacyPacket handshake = conn.readPacket();
                username = (String) handshake.field(1);
                byte[] token = LegacyCrypto.randomBytes(4);
                conn.sendPacket(new LegacyPacket(0xFD, List.of("-",
                        LegacyCrypto.publicKeyBytes(keyPair.getPublic()), token)));
                LegacyPacket fc = conn.readPacket();
                byte[] secret = LegacyCrypto.rsaDecrypt(keyPair.getPrivate(),
                        (byte[]) fc.field(0));
                // The server's 0xFC is plaintext; encryption starts
                // only after both 0xFC packets (the real 1.6.4 order).
                conn.sendPacket(new LegacyPacket(0xFC, List.of(new byte[0], new byte[0])));
                conn.enableEncryption(secret);
                conn.readPacket(); // 0x01 login
                LegacyPacket status = conn.readPacket(); // 0xCD client status
                if (status == null || status.id() != 0xCD) {
                    throw new IllegalStateException("expected 0xCD client status, got "
                            + (status == null ? "timeout" : "0x" + Integer.toHexString(status.id())));
                }
                conn.sendPacket(new LegacyPacket(0x01,
                        List.of(42, "default", 0, 0, 2, 0, 20)));
                socket.setSoTimeout(200);
                while (true) {
                    LegacyPacket packet = conn.readPacket();
                    if (packet != null) {
                        received.add(packet);
                    }
                }
            } catch (EOFException | java.net.SocketException e) {
                // closed
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        void send(LegacyPacket packet) throws IOException {
            connection.sendPacket(packet);
        }

        void close() throws IOException {
            serverSocket.close();
            if (connection != null) {
                connection.close();
            }
        }
    }

    /**
     * The protocol-29 (1.2.5) fake backend: the login is plaintext
     * (no encryption request/response at all), the handshake is a
     * single string whose content is ignored, and the username
     * comes from the 0x01 login packet — exactly as measured
     * against the real 1.2.5 server in Stage A.
     */
    static final class FakeLegacyBackend29 implements Runnable {
        final ServerSocket serverSocket;
        final BlockingQueue<LegacyPacket> received = new LinkedBlockingQueue<>();
        volatile LegacyConnection connection;
        volatile String username;
        volatile int loginProtocol = -1;

        FakeLegacyBackend29() throws IOException {
            serverSocket = new ServerSocket(0);
        }

        int port() {
            return serverSocket.getLocalPort();
        }

        @Override
        public void run() {
            try (Socket socket = serverSocket.accept()) {
                socket.setSoTimeout(10_000);
                LegacyConnection conn = new LegacyConnection(
                        socket, LegacyProtocol.V1_2_5);
                connection = conn;
                conn.readPacket(); // 0x02 handshake: content ignored
                conn.sendPacket(new LegacyPacket(0x02, List.of("-")));
                LegacyPacket login = conn.readPacket(); // 0x01 login
                loginProtocol = login.intField(0);
                username = (String) login.field(1);
                conn.sendPacket(new LegacyPacket(0x01, List.of(
                        42, "", "default", 0, 0, 2, 0, 20)));
                socket.setSoTimeout(200);
                while (true) {
                    LegacyPacket packet = conn.readPacket();
                    if (packet != null) {
                        received.add(packet);
                    }
                }
            } catch (EOFException | java.net.SocketException e) {
                // closed
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        void send(LegacyPacket packet) throws IOException {
            connection.sendPacket(packet);
        }

        void close() throws IOException {
            serverSocket.close();
            if (connection != null) {
                connection.close();
            }
        }
    }

    /**
     * A plaintext-generation fake backend (protocols 28, 23, 22 —
     * and usable for 29): the login is the plaintext exchange, the
     * handshake content is ignored, the username comes from the
     * 0x01 login packet, and the login response is whatever field
     * list the test supplies for its generation — exactly as
     * measured against the real servers in Stage A.
     */
    static final class FakePlaintextBackend implements Runnable {
        final ServerSocket serverSocket;
        final BlockingQueue<LegacyPacket> received = new LinkedBlockingQueue<>();
        final int protocol;
        final List<Object> loginResponseFields;
        volatile LegacyConnection connection;
        volatile String username;
        volatile int loginProtocol = -1;

        FakePlaintextBackend(int protocol, List<Object> loginResponseFields)
                throws IOException {
            serverSocket = new ServerSocket(0);
            this.protocol = protocol;
            this.loginResponseFields = loginResponseFields;
        }

        int port() {
            return serverSocket.getLocalPort();
        }

        @Override
        public void run() {
            try (Socket socket = serverSocket.accept()) {
                socket.setSoTimeout(10_000);
                LegacyConnection conn = new LegacyConnection(socket, protocol);
                connection = conn;
                conn.readPacket(); // 0x02 handshake: content ignored
                conn.sendPacket(new LegacyPacket(0x02, List.of("-")));
                LegacyPacket login = conn.readPacket(); // 0x01 login
                loginProtocol = login.intField(0);
                username = (String) login.field(1);
                conn.sendPacket(new LegacyPacket(0x01, loginResponseFields));
                socket.setSoTimeout(200);
                while (true) {
                    LegacyPacket packet = conn.readPacket();
                    if (packet != null) {
                        received.add(packet);
                    }
                }
            } catch (EOFException | java.net.SocketException e) {
                // closed
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        void send(LegacyPacket packet) throws IOException {
            connection.sendPacket(packet);
        }

        void close() throws IOException {
            serverSocket.close();
            if (connection != null) {
                connection.close();
            }
        }
    }

    // ------------------------------------------------------------------
    // Fake modern core (protocol-5 server role)
    // ------------------------------------------------------------------

    static final class FakeCore implements Runnable {
        final ServerSocket serverSocket;
        final BlockingQueue<MinecraftPacket> received = new LinkedBlockingQueue<>();
        volatile OutputStream out;
        volatile String loginName;

        FakeCore() throws IOException {
            serverSocket = new ServerSocket(0);
        }

        int port() {
            return serverSocket.getLocalPort();
        }

        @Override
        public void run() {
            try (Socket socket = serverSocket.accept()) {
                socket.setSoTimeout(10_000);
                InputStream in = socket.getInputStream();
                out = socket.getOutputStream();
                MinecraftPacket.Decoder decoder = new MinecraftPacket.Decoder();
                Thread reader = new Thread(() -> {
                    byte[] buf = new byte[65536];
                    try {
                        int n;
                        while ((n = in.read(buf)) >= 0) {
                            for (MinecraftPacket p : decoder.feed(Arrays.copyOf(buf, n))) {
                                received.add(p);
                            }
                        }
                    } catch (IOException ignored) {
                        // closed
                    }
                });
                reader.setDaemon(true);
                reader.start();

                MinecraftPacket handshake = received.poll(5, TimeUnit.SECONDS);
                assertNotNull(handshake);
                MinecraftPacket loginStart = received.poll(5, TimeUnit.SECONDS);
                assertNotNull(loginStart);
                loginName = new PacketIO.Reader(loginStart.payload()).readString();

                PacketIO.Writer success = new PacketIO.Writer();
                success.writeString("123e4567-e89b-12d3-a456-426614174000");
                success.writeString(loginName);
                success.writeVarInt(0);
                send(new MinecraftPacket(2, success.bytes()));

                PacketIO.Writer join = new PacketIO.Writer();
                join.writeInt(7);
                join.writeByte(0);
                join.writeByte(0);
                join.writeByte(2);
                join.writeByte(20);
                join.writeString("default");
                send(new MinecraftPacket(0x01, join.bytes()));

                PacketIO.Writer chat = new PacketIO.Writer();
                chat.writeString("{\"text\":\"Welcome\"}");
                send(new MinecraftPacket(0x02, chat.bytes()));

                PacketIO.Writer ka = new PacketIO.Writer();
                ka.writeInt(999);
                send(new MinecraftPacket(0x00, ka.bytes()));

                // Sign editor open (0x36): legacy 0x85, absent in 73.
                PacketIO.Writer sign = new PacketIO.Writer();
                sign.writeInt(1);
                sign.writeInt(64);
                sign.writeInt(2);
                send(new MinecraftPacket(0x36, sign.bytes()));

                // Entity properties (0x20) with one attribute modifier.
                PacketIO.Writer attrs = new PacketIO.Writer();
                attrs.writeInt(7);
                attrs.writeInt(1);
                attrs.writeString("generic.maxHealth");
                attrs.writeDouble(20.0);
                attrs.writeShort(1);
                attrs.writeUuid(java.util.UUID.fromString(
                        "123e4567-e89b-12d3-a456-426614174000"));
                attrs.writeDouble(2.0);
                attrs.writeByte(0);
                send(new MinecraftPacket(0x20, attrs.bytes()));

                reader.join(15_000);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        synchronized void send(MinecraftPacket packet) throws IOException {
            out.write(packet.encode());
            out.flush();
        }

        void close() throws IOException {
            serverSocket.close();
        }
    }

    // ------------------------------------------------------------------
    // Tests
    // ------------------------------------------------------------------

    @Test
    void directModeRelaysLoginAndPlayPackets() throws Exception {
        FakeLegacyBackend backend = new FakeLegacyBackend();
        Thread backendThread = new Thread(backend);
        backendThread.setDaemon(true);
        backendThread.start();

        LegacyFrontendConfig config = LegacyFrontendConfig.direct(
                0, "127.0.0.1", backend.port(), "Test MOTD", 20);
        try (LegacyFrontendServer frontend = new LegacyFrontendServer(config)) {
            frontend.start();
            LegacyBackendLogin.Result login = LegacyBackendLogin.connectAndLogin(
                    "127.0.0.1", frontend.boundPort(), "Tester");
            assertEquals(42, login.loginResponse().intField(0));
            assertEquals("Tester", backend.username);
            LegacyConnection client = login.connection();

            // Client -> backend chat.
            client.sendPacket(new LegacyPacket(0x03, List.of("hello backend")));
            LegacyPacket atBackend = backend.received.poll(5, TimeUnit.SECONDS);
            assertNotNull(atBackend, "backend should receive the chat");
            assertEquals(0x03, atBackend.id());
            assertEquals("hello backend", atBackend.field(0));

            // Backend -> client chat and keep-alive.
            backend.send(new LegacyPacket(0x03, List.of("hello client")));
            backend.send(new LegacyPacket(0x00, List.of(31337)));
            LegacyPacket chat = readUntil(client, 0x03);
            assertEquals("hello client", chat.field(0));
            LegacyPacket ka = readUntil(client, 0x00);
            assertEquals(31337, ka.intField(0));

            client.sendPacket(new LegacyPacket(0x00, List.of(31337)));
            LegacyPacket echo = backend.received.poll(5, TimeUnit.SECONDS);
            assertNotNull(echo);
            assertEquals(31337, echo.intField(0));
            client.close();
        } finally {
            backend.close();
        }
    }

    @Test
    void directMode74RelaysLoginAndPlayPackets() throws Exception {
        FakeLegacyBackend backend = new FakeLegacyBackend(LegacyProtocol.V1_6_2);
        Thread backendThread = new Thread(backend);
        backendThread.setDaemon(true);
        backendThread.start();

        LegacyFrontendConfig config = LegacyFrontendConfig.direct(
                0, "127.0.0.1", backend.port(), "Test MOTD", 20, "1.6.2");
        try (LegacyFrontendServer frontend = new LegacyFrontendServer(config)) {
            frontend.start();
            LegacyBackendLogin.Result login = LegacyBackendLogin.connectAndLogin(
                    "127.0.0.1", frontend.boundPort(), "Tester162", LegacyProtocol.V1_6_2);
            assertEquals(42, login.loginResponse().intField(0));
            assertEquals("Tester162", backend.username);
            LegacyConnection client = login.connection();
            assertEquals(LegacyProtocol.V1_6_2, client.legacyProtocol());

            client.sendPacket(new LegacyPacket(0x03, List.of("hello 1.6.2 backend")));
            LegacyPacket atBackend = backend.received.poll(5, TimeUnit.SECONDS);
            assertNotNull(atBackend, "1.6.2 backend should receive the chat");
            assertEquals(0x03, atBackend.id());
            assertEquals("hello 1.6.2 backend", atBackend.field(0));

            backend.send(new LegacyPacket(0x00, List.of(22222)));
            LegacyPacket ka = readUntil(client, 0x00);
            assertEquals(22222, ka.intField(0));
            client.close();
        } finally {
            backend.close();
        }
    }

    @Test
    void directMode74KicksMismatchedProtocolsLikeVanilla() throws Exception {
        FakeLegacyBackend backend = new FakeLegacyBackend(LegacyProtocol.V1_6_2);
        Thread backendThread = new Thread(backend);
        backendThread.setDaemon(true);
        backendThread.start();

        LegacyFrontendConfig config = LegacyFrontendConfig.direct(
                0, "127.0.0.1", backend.port(), "Test MOTD", 20, "1.6.2");
        try (LegacyFrontendServer frontend = new LegacyFrontendServer(config)) {
            frontend.start();
            // A 1.6.4 (78) client against a 1.6.2 backend: the real
            // vanilla kicks with "Outdated server!".
            String newer = rawHandshakeKick(frontend.boundPort(), LegacyProtocol.V1_6_4);
            assertEquals("Outdated server!", newer);
            // A 1.6.1 (73) client is not even supported yet.
            String older = rawHandshakeKick(frontend.boundPort(), 73);
            assertEquals("Outdated client!", older);
        } finally {
            backend.close();
        }
    }

    /** Sends a raw handshake with the given protocol and reads the kick. */
    private static String rawHandshakeKick(int port, int protocol) throws Exception {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress("127.0.0.1", port), 5_000);
            socket.setSoTimeout(5_000);
            LegacyConnection conn = new LegacyConnection(socket);
            conn.sendRaw(new LegacyWire.Writer(0x02)
                    .u8(protocol)
                    .string("Kicker")
                    .string("127.0.0.1")
                    .i32(port)
                    .bytes());
            LegacyPacket kick = conn.readPacket();
            assertNotNull(kick, "expected a kick packet");
            assertEquals(0xFF, kick.id());
            return (String) kick.field(0);
        }
    }

    @Test
    void bridgeMode74ClientJoinsThroughTranslator() throws Exception {
        FakeCore core = new FakeCore();
        Thread coreThread = new Thread(core);
        coreThread.setDaemon(true);
        coreThread.start();

        LegacyFrontendConfig config = LegacyFrontendConfig.bridge(
                0, "127.0.0.1", core.port(), "Test MOTD", 20);
        try (LegacyFrontendServer frontend = new LegacyFrontendServer(config)) {
            frontend.start();
            LegacyBackendLogin.Result login = LegacyBackendLogin.connectAndLogin(
                    "127.0.0.1", frontend.boundPort(), "Bridge162", LegacyProtocol.V1_6_2);
            assertEquals(7, login.loginResponse().intField(0));
            assertEquals("Bridge162", core.loginName);
            LegacyConnection client = login.connection();
            LegacyPacket chat = readUntil(client, 0x03);
            assertEquals("Welcome", chat.field(0));
            client.close();
        } finally {
            core.close();
        }
    }

    @Test
    void legacyPingSection1For162Config() throws Exception {
        LegacyFrontendConfig config = LegacyFrontendConfig.direct(
                0, "127.0.0.1", 9, "Ping MOTD", 20, "1.6.2");
        try (LegacyFrontendServer frontend = new LegacyFrontendServer(config)) {
            frontend.start();
            String payload = pingPayload(frontend.boundPort(), true);
            assertTrue(payload.startsWith("§1\0" + "74\0" + "1.6.2\0" + "Ping MOTD\0"),
                    "unexpected ping payload: " + payload);
        }
    }

    @Test
    void directMode73RelaysLoginPlayAndProperties() throws Exception {
        FakeLegacyBackend backend = new FakeLegacyBackend(LegacyProtocol.V1_6_1);
        Thread backendThread = new Thread(backend);
        backendThread.setDaemon(true);
        backendThread.start();

        LegacyFrontendConfig config = LegacyFrontendConfig.direct(
                0, "127.0.0.1", backend.port(), "Test MOTD", 20, "1.6.1");
        try (LegacyFrontendServer frontend = new LegacyFrontendServer(config)) {
            frontend.start();
            LegacyBackendLogin.Result login = LegacyBackendLogin.connectAndLogin(
                    "127.0.0.1", frontend.boundPort(), "Tester161", LegacyProtocol.V1_6_1);
            assertEquals(42, login.loginResponse().intField(0));
            assertEquals("Tester161", backend.username);
            LegacyConnection client = login.connection();
            assertEquals(LegacyProtocol.V1_6_1, client.legacyProtocol());

            client.sendPacket(new LegacyPacket(0x03, List.of("hello 1.6.1 backend")));
            LegacyPacket atBackend = backend.received.poll(5, TimeUnit.SECONDS);
            assertNotNull(atBackend, "1.6.1 backend should receive the chat");
            assertEquals("hello 1.6.1 backend", atBackend.field(0));

            // The backend sends entity properties carrying a modifier;
            // on the 1.6.1 wire the modifier does not exist, so the
            // client receives the property with an empty list.
            backend.send(new LegacyPacket(0x2C, List.of(42,
                    List.of(new LegacyPacketIO.Property("generic.maxHealth", 20.0,
                            List.of(new LegacyPacketIO.Modifier(1L, 2L, 0.5, 1)))))));
            LegacyPacket props = readUntil(client, 0x2C);
            @SuppressWarnings("unchecked")
            List<LegacyPacketIO.Property> received =
                    (List<LegacyPacketIO.Property>) props.field(1);
            assertEquals(1, received.size());
            assertEquals("generic.maxHealth", received.get(0).key());
            assertEquals(20.0, received.get(0).base());
            assertTrue(received.get(0).modifiers().isEmpty());
            client.close();
        } finally {
            backend.close();
        }
    }

    @Test
    void bridgeMode73DropsSignEditorAndModifiers() throws Exception {
        FakeCore core = new FakeCore();
        Thread coreThread = new Thread(core);
        coreThread.setDaemon(true);
        coreThread.start();

        LegacyFrontendConfig config = LegacyFrontendConfig.bridge(
                0, "127.0.0.1", core.port(), "Test MOTD", 20);
        try (LegacyFrontendServer frontend = new LegacyFrontendServer(config)) {
            frontend.start();
            LegacyBackendLogin.Result login = LegacyBackendLogin.connectAndLogin(
                    "127.0.0.1", frontend.boundPort(), "Bridge161", LegacyProtocol.V1_6_1);
            assertEquals(7, login.loginResponse().intField(0));
            assertEquals("Bridge161", core.loginName);
            LegacyConnection client = login.connection();

            // Collect everything up to the attributes packet: the
            // sign editor (sent by the core before it) must not
            // appear, and the attributes must arrive modifier-free.
            LegacyPacket props = null;
            long deadline = System.currentTimeMillis() + 5_000;
            while (System.currentTimeMillis() < deadline && props == null) {
                LegacyPacket packet = client.readPacket();
                if (packet == null) {
                    continue;
                }
                assertTrue(packet.id() != 0x85,
                        "0x85 must never reach a 1.6.1 client");
                if (packet.id() == 0x2C) {
                    props = packet;
                }
            }
            assertNotNull(props, "attributes should reach the 1.6.1 client");
            @SuppressWarnings("unchecked")
            List<LegacyPacketIO.Property> received =
                    (List<LegacyPacketIO.Property>) props.field(1);
            assertEquals(1, received.size());
            assertEquals("generic.maxHealth", received.get(0).key());
            assertTrue(received.get(0).modifiers().isEmpty(),
                    "modifiers are dropped for 1.6.1");
            client.close();
        } finally {
            core.close();
        }
    }

    @Test
    void legacyPingSection1For161Config() throws Exception {
        LegacyFrontendConfig config = LegacyFrontendConfig.direct(
                0, "127.0.0.1", 9, "Ping MOTD", 20, "1.6.1");
        try (LegacyFrontendServer frontend = new LegacyFrontendServer(config)) {
            frontend.start();
            String payload = pingPayload(frontend.boundPort(), true);
            assertTrue(payload.startsWith("§1\0" + "73\0" + "1.6.1\0" + "Ping MOTD\0"),
                    "unexpected ping payload: " + payload);
        }
    }

    @Test
    void directMode61RelaysLoginPlayAndHealth() throws Exception {
        FakeLegacyBackend backend = new FakeLegacyBackend(LegacyProtocol.V1_5_2);
        Thread backendThread = new Thread(backend);
        backendThread.setDaemon(true);
        backendThread.start();

        LegacyFrontendConfig config = LegacyFrontendConfig.direct(
                0, "127.0.0.1", backend.port(), "Test MOTD", 20, "1.5.2");
        try (LegacyFrontendServer frontend = new LegacyFrontendServer(config)) {
            frontend.start();
            LegacyBackendLogin.Result login = LegacyBackendLogin.connectAndLogin(
                    "127.0.0.1", frontend.boundPort(), "Tester152", LegacyProtocol.V1_5_2);
            assertEquals(42, login.loginResponse().intField(0));
            assertEquals("Tester152", backend.username);
            LegacyConnection client = login.connection();
            assertEquals(LegacyProtocol.V1_5_2, client.legacyProtocol());

            client.sendPacket(new LegacyPacket(0x03, List.of("hello 1.5.2 backend")));
            LegacyPacket atBackend = backend.received.poll(5, TimeUnit.SECONDS);
            assertNotNull(atBackend, "1.5.2 backend should receive the chat");
            assertEquals("hello 1.5.2 backend", atBackend.field(0));

            // Health on the 61 wire: I16 health, I16 food, F32 saturation.
            backend.send(new LegacyPacket(0x08, List.of(20, 18, 0.5f)));
            LegacyPacket health = readUntil(client, 0x08);
            assertEquals(20, health.intField(0));
            assertEquals(18, health.intField(1));
            assertEquals(0.5f, (Float) health.field(2));

            // Abilities on the 61 wire: flags + two speed bytes.
            backend.send(new LegacyPacket(0xCA, List.of(3, 13, 26)));
            LegacyPacket abilities = readUntil(client, 0xCA);
            assertEquals(13, abilities.intField(1));
            assertEquals(26, abilities.intField(2));
            client.close();
        } finally {
            backend.close();
        }
    }

    @Test
    void bridgeMode61DropsSignEditorAndAttributes() throws Exception {
        FakeCore core = new FakeCore();
        Thread coreThread = new Thread(core);
        coreThread.setDaemon(true);
        coreThread.start();

        LegacyFrontendConfig config = LegacyFrontendConfig.bridge(
                0, "127.0.0.1", core.port(), "Test MOTD", 20);
        try (LegacyFrontendServer frontend = new LegacyFrontendServer(config)) {
            frontend.start();
            LegacyBackendLogin.Result login = LegacyBackendLogin.connectAndLogin(
                    "127.0.0.1", frontend.boundPort(), "Bridge152", LegacyProtocol.V1_5_2);
            assertEquals(7, login.loginResponse().intField(0));
            assertEquals("Bridge152", core.loginName);
            LegacyConnection client = login.connection();

            // The core sends chat, keep alive, sign editor (0x36) and
            // attributes (0x20) in that order. Neither 0x85 nor 0x2C
            // exists in protocol 61, so after the keep alive nothing
            // else may arrive. The collection runs on a reader thread
            // for a short window (a blocking read on the client
            // connection would otherwise wait out its 60s timeout,
            // and FakeCore's own 10s read timeout closes idle cores).
            LegacyPacket ka = readUntil(client, 0x00);
            assertEquals(999, ka.intField(0));
            List<LegacyPacket> extra = new java.util.concurrent.CopyOnWriteArrayList<>();
            Thread drainer = new Thread(() -> {
                try {
                    LegacyPacket p;
                    while ((p = client.readPacket()) != null) {
                        extra.add(p);
                    }
                } catch (IOException ignored) {
                    // closed at test end
                }
            });
            drainer.setDaemon(true);
            drainer.start();
            Thread.sleep(800);
            for (LegacyPacket packet : extra) {
                assertTrue(packet.id() != 0x85 && packet.id() != 0x2C,
                        "packet 0x" + Integer.toHexString(packet.id())
                                + " must never reach a 1.5.2 client");
            }

            // A 61 entity action has no aux field; the core receives
            // a synthesized 0.
            client.sendPacket(new LegacyPacket(0x13, List.of(5, 1)));
            MinecraftPacket action = pollModern(core, 0x0B);
            PacketIO.Reader r = new PacketIO.Reader(action.payload());
            assertEquals(5, r.readInt());
            assertEquals(1, r.readByte());
            assertEquals(0, r.readInt());
            client.close();
        } finally {
            core.close();
        }
    }

    @Test
    void legacyPingSection1For152Config() throws Exception {
        LegacyFrontendConfig config = LegacyFrontendConfig.direct(
                0, "127.0.0.1", 9, "Ping MOTD", 20, "1.5.2");
        try (LegacyFrontendServer frontend = new LegacyFrontendServer(config)) {
            frontend.start();
            String payload = pingPayload(frontend.boundPort(), true);
            assertTrue(payload.startsWith("§1\0" + "61\0" + "1.5.2\0" + "Ping MOTD\0"),
                    "unexpected ping payload: " + payload);
        }
    }

    @Test
    void directMode51RelaysLoginPlayAndHealth() throws Exception {
        FakeLegacyBackend backend = new FakeLegacyBackend(LegacyProtocol.V1_4_7);
        Thread backendThread = new Thread(backend);
        backendThread.setDaemon(true);
        backendThread.start();

        LegacyFrontendConfig config = LegacyFrontendConfig.direct(
                0, "127.0.0.1", backend.port(), "Test MOTD", 20, "1.4.7");
        try (LegacyFrontendServer frontend = new LegacyFrontendServer(config)) {
            frontend.start();
            LegacyBackendLogin.Result login = LegacyBackendLogin.connectAndLogin(
                    "127.0.0.1", frontend.boundPort(), "Tester147", LegacyProtocol.V1_4_7);
            assertEquals(42, login.loginResponse().intField(0));
            assertEquals("Tester147", backend.username);
            LegacyConnection client = login.connection();
            assertEquals(LegacyProtocol.V1_4_7, client.legacyProtocol());

            client.sendPacket(new LegacyPacket(0x03, List.of("hello 1.4.7 backend")));
            LegacyPacket atBackend = backend.received.poll(5, TimeUnit.SECONDS);
            assertNotNull(atBackend, "1.4.7 backend should receive the chat");
            assertEquals("hello 1.4.7 backend", atBackend.field(0));

            // Health on the 51 wire has the 1.5-generation shape:
            // I16 health, I16 food, F32 saturation.
            backend.send(new LegacyPacket(0x08, List.of(20, 18, 0.5f)));
            LegacyPacket health = readUntil(client, 0x08);
            assertEquals(20, health.intField(0));
            assertEquals(18, health.intField(1));
            assertEquals(0.5f, (Float) health.field(2));

            // Abilities on the 51 wire: flags + two speed bytes.
            backend.send(new LegacyPacket(0xCA, List.of(3, 13, 26)));
            LegacyPacket abilities = readUntil(client, 0xCA);
            assertEquals(13, abilities.intField(1));
            assertEquals(26, abilities.intField(2));

            // Open window on the 51 wire has no useTitle byte; the
            // relayed packet still parses with the supplied default.
            backend.send(new LegacyPacket(0x64,
                    java.util.Arrays.asList(2, 0, "Chest", 27, true, null)));
            LegacyPacket window = readUntil(client, 0x64);
            assertEquals("Chest", window.field(2));
            assertEquals(Boolean.TRUE, window.field(4));
            client.close();
        } finally {
            backend.close();
        }
    }

    @Test
    void bridgeMode51DropsSignEditorAndAttributes() throws Exception {
        FakeCore core = new FakeCore();
        Thread coreThread = new Thread(core);
        coreThread.setDaemon(true);
        coreThread.start();

        LegacyFrontendConfig config = LegacyFrontendConfig.bridge(
                0, "127.0.0.1", core.port(), "Test MOTD", 20);
        try (LegacyFrontendServer frontend = new LegacyFrontendServer(config)) {
            frontend.start();
            LegacyBackendLogin.Result login = LegacyBackendLogin.connectAndLogin(
                    "127.0.0.1", frontend.boundPort(), "Bridge147", LegacyProtocol.V1_4_7);
            assertEquals(7, login.loginResponse().intField(0));
            assertEquals("Bridge147", core.loginName);
            LegacyConnection client = login.connection();

            // The core sends chat, keep alive, sign editor (0x36) and
            // attributes (0x20) in that order. Neither 0x85 nor 0x2C
            // exists in protocol 51 (the sign editor arrived in 1.6.2,
            // attributes in 1.6), so after the keep alive nothing else
            // may arrive. Same drainer pattern as the 61 bridge test.
            LegacyPacket ka = readUntil(client, 0x00);
            assertEquals(999, ka.intField(0));
            List<LegacyPacket> extra = new java.util.concurrent.CopyOnWriteArrayList<>();
            Thread drainer = new Thread(() -> {
                try {
                    LegacyPacket p;
                    while ((p = client.readPacket()) != null) {
                        extra.add(p);
                    }
                } catch (IOException ignored) {
                    // closed at test end
                }
            });
            drainer.setDaemon(true);
            drainer.start();
            Thread.sleep(800);
            for (LegacyPacket packet : extra) {
                assertTrue(packet.id() != 0x85 && packet.id() != 0x2C,
                        "packet 0x" + Integer.toHexString(packet.id())
                                + " must never reach a 1.4.7 client");
            }
            client.close();
        } finally {
            core.close();
        }
    }

    @Test
    void directMode49RelaysLoginPlayAndHealth() throws Exception {
        FakeLegacyBackend backend = new FakeLegacyBackend(LegacyProtocol.V1_4_5);
        Thread backendThread = new Thread(backend);
        backendThread.setDaemon(true);
        backendThread.start();

        LegacyFrontendConfig config = LegacyFrontendConfig.direct(
                0, "127.0.0.1", backend.port(), "Test MOTD", 20, "1.4.5");
        try (LegacyFrontendServer frontend = new LegacyFrontendServer(config)) {
            frontend.start();
            LegacyBackendLogin.Result login = LegacyBackendLogin.connectAndLogin(
                    "127.0.0.1", frontend.boundPort(), "Tester145", LegacyProtocol.V1_4_5);
            assertEquals(42, login.loginResponse().intField(0));
            assertEquals("Tester145", backend.username);
            LegacyConnection client = login.connection();
            assertEquals(LegacyProtocol.V1_4_5, client.legacyProtocol());

            client.sendPacket(new LegacyPacket(0x03, List.of("hello 1.4.5 backend")));
            LegacyPacket atBackend = backend.received.poll(5, TimeUnit.SECONDS);
            assertNotNull(atBackend, "1.4.5 backend should receive the chat");
            assertEquals("hello 1.4.5 backend", atBackend.field(0));

            // Health and abilities have the 1.5-generation shapes on
            // the 49 wire too (measured identical to 51).
            backend.send(new LegacyPacket(0x08, List.of(20, 18, 0.5f)));
            LegacyPacket health = readUntil(client, 0x08);
            assertEquals(20, health.intField(0));
            backend.send(new LegacyPacket(0xCA, List.of(3, 13, 26)));
            LegacyPacket abilities = readUntil(client, 0xCA);
            assertEquals(13, abilities.intField(1));

            // A dropped item spawns through the 49-only 0x15
            // pickup_spawn in direct mode and is relayed natively.
            backend.send(new LegacyPacket(0x15, List.of(
                    7, new Slot(276, 1, 0, null), 160, 2048, -160, 10, 20, 30)));
            LegacyPacket pickup = readUntil(client, 0x15);
            assertEquals(7, pickup.intField(0));
            client.close();
        } finally {
            backend.close();
        }
    }

    @Test
    void bridgeMode49DropsSignEditorAndAttributes() throws Exception {
        FakeCore core = new FakeCore();
        Thread coreThread = new Thread(core);
        coreThread.setDaemon(true);
        coreThread.start();

        LegacyFrontendConfig config = LegacyFrontendConfig.bridge(
                0, "127.0.0.1", core.port(), "Test MOTD", 20);
        try (LegacyFrontendServer frontend = new LegacyFrontendServer(config)) {
            frontend.start();
            LegacyBackendLogin.Result login = LegacyBackendLogin.connectAndLogin(
                    "127.0.0.1", frontend.boundPort(), "Bridge145", LegacyProtocol.V1_4_5);
            assertEquals(7, login.loginResponse().intField(0));
            assertEquals("Bridge145", core.loginName);
            LegacyConnection client = login.connection();

            // Same shape as the 51 bridge test: neither 0x85 nor
            // 0x2C exists in protocol 49 either.
            LegacyPacket ka = readUntil(client, 0x00);
            assertEquals(999, ka.intField(0));
            List<LegacyPacket> extra = new java.util.concurrent.CopyOnWriteArrayList<>();
            Thread drainer = new Thread(() -> {
                try {
                    LegacyPacket p;
                    while ((p = client.readPacket()) != null) {
                        extra.add(p);
                    }
                } catch (IOException ignored) {
                    // closed at test end
                }
            });
            drainer.setDaemon(true);
            drainer.start();
            Thread.sleep(800);
            for (LegacyPacket packet : extra) {
                assertTrue(packet.id() != 0x85 && packet.id() != 0x2C,
                        "packet 0x" + Integer.toHexString(packet.id())
                                + " must never reach a 1.4.5 client");
            }
            client.close();
        } finally {
            core.close();
        }
    }

    @Test
    void legacyPingSection1For145And144Configs() throws Exception {
        LegacyFrontendConfig config145 = LegacyFrontendConfig.direct(
                0, "127.0.0.1", 9, "Ping MOTD", 20, "1.4.5");
        try (LegacyFrontendServer frontend = new LegacyFrontendServer(config145)) {
            frontend.start();
            String payload = pingPayload(frontend.boundPort(), true);
            assertTrue(payload.startsWith("§1\0" + "49\0" + "1.4.5\0" + "Ping MOTD\0"),
                    "unexpected ping payload: " + payload);
        }
        // 1.4.4 is the same protocol number (measured identical), so
        // its config answers with 49 and its own version name.
        LegacyFrontendConfig config144 = LegacyFrontendConfig.direct(
                0, "127.0.0.1", 9, "Ping MOTD", 20, "1.4.4");
        try (LegacyFrontendServer frontend = new LegacyFrontendServer(config144)) {
            frontend.start();
            String payload = pingPayload(frontend.boundPort(), true);
            assertTrue(payload.startsWith("§1\0" + "49\0" + "1.4.4\0" + "Ping MOTD\0"),
                    "unexpected ping payload: " + payload);
        }
    }

    @Test
    void directMode47OldRelaysLoginPlayAndHealth() throws Exception {
        FakeLegacyBackend backend = new FakeLegacyBackend(LegacyProtocol.V1_4_2);
        Thread backendThread = new Thread(backend);
        backendThread.setDaemon(true);
        backendThread.start();

        LegacyFrontendConfig config = LegacyFrontendConfig.direct(
                0, "127.0.0.1", backend.port(), "Test MOTD", 20, "1.4.2");
        try (LegacyFrontendServer frontend = new LegacyFrontendServer(config)) {
            frontend.start();
            LegacyBackendLogin.Result login = LegacyBackendLogin.connectAndLogin(
                    "127.0.0.1", frontend.boundPort(), "Tester142", LegacyProtocol.V1_4_2);
            assertEquals(42, login.loginResponse().intField(0));
            assertEquals("Tester142", backend.username);
            LegacyConnection client = login.connection();
            assertEquals(LegacyProtocol.V1_4_2, client.legacyProtocol());

            client.sendPacket(new LegacyPacket(0x03, List.of("hello 1.4.2 backend")));
            LegacyPacket atBackend = backend.received.poll(5, TimeUnit.SECONDS);
            assertNotNull(atBackend, "1.4.2 backend should receive the chat");
            assertEquals("hello 1.4.2 backend", atBackend.field(0));

            // The wire is identical to 49 (measured), so health,
            // abilities and the 0x15 pickup spawn relay natively.
            backend.send(new LegacyPacket(0x08, List.of(20, 18, 0.5f)));
            LegacyPacket health = readUntil(client, 0x08);
            assertEquals(20, health.intField(0));
            backend.send(new LegacyPacket(0xCA, List.of(3, 13, 26)));
            LegacyPacket abilities = readUntil(client, 0xCA);
            assertEquals(13, abilities.intField(1));
            backend.send(new LegacyPacket(0x15, List.of(
                    7, new Slot(276, 1, 0, null), 160, 2048, -160, 10, 20, 30)));
            LegacyPacket pickup = readUntil(client, 0x15);
            assertEquals(7, pickup.intField(0));
            client.close();
        } finally {
            backend.close();
        }
    }

    @Test
    void bridgeMode47OldDropsSignEditorAndAttributes() throws Exception {
        FakeCore core = new FakeCore();
        Thread coreThread = new Thread(core);
        coreThread.setDaemon(true);
        coreThread.start();

        LegacyFrontendConfig config = LegacyFrontendConfig.bridge(
                0, "127.0.0.1", core.port(), "Test MOTD", 20);
        try (LegacyFrontendServer frontend = new LegacyFrontendServer(config)) {
            frontend.start();
            LegacyBackendLogin.Result login = LegacyBackendLogin.connectAndLogin(
                    "127.0.0.1", frontend.boundPort(), "Bridge142", LegacyProtocol.V1_4_2);
            assertEquals(7, login.loginResponse().intField(0));
            assertEquals("Bridge142", core.loginName);
            LegacyConnection client = login.connection();

            // Same shape as the 49 bridge test: neither 0x85 nor
            // 0x2C exists in legacy protocol 47 (1.4.2) either.
            LegacyPacket ka = readUntil(client, 0x00);
            assertEquals(999, ka.intField(0));
            List<LegacyPacket> extra = new java.util.concurrent.CopyOnWriteArrayList<>();
            Thread drainer = new Thread(() -> {
                try {
                    LegacyPacket p;
                    while ((p = client.readPacket()) != null) {
                        extra.add(p);
                    }
                } catch (IOException ignored) {
                    // closed at test end
                }
            });
            drainer.setDaemon(true);
            drainer.start();
            Thread.sleep(800);
            for (LegacyPacket packet : extra) {
                assertTrue(packet.id() != 0x85 && packet.id() != 0x2C,
                        "packet 0x" + Integer.toHexString(packet.id())
                                + " must never reach a 1.4.2 client");
            }
            client.close();
        } finally {
            core.close();
        }
    }

    @Test
    void legacyPingSection1For142Config() throws Exception {
        // The ping answers with the LEGACY protocol number 47 and
        // the configured version name 1.4.2 (measured on the real
        // 1.4.2 jar). This number is the legacy 47, not the Netty
        // protocol 47 (1.8) — the frontend's separate port is what
        // keeps the two apart.
        LegacyFrontendConfig config = LegacyFrontendConfig.direct(
                0, "127.0.0.1", 9, "Ping MOTD", 20, "1.4.2");
        try (LegacyFrontendServer frontend = new LegacyFrontendServer(config)) {
            frontend.start();
            String payload = pingPayload(frontend.boundPort(), true);
            assertTrue(payload.startsWith("§1\0" + "47\0" + "1.4.2\0" + "Ping MOTD\0"),
                    "unexpected ping payload: " + payload);
        }
    }

    @Test
    void directMode39RelaysLoginPlayAndExpandedPickup() throws Exception {
        FakeLegacyBackend backend = new FakeLegacyBackend(LegacyProtocol.V1_3_2);
        Thread backendThread = new Thread(backend);
        backendThread.setDaemon(true);
        backendThread.start();

        LegacyFrontendConfig config = LegacyFrontendConfig.direct(
                0, "127.0.0.1", backend.port(), "Test MOTD", 20, "1.3.2");
        try (LegacyFrontendServer frontend = new LegacyFrontendServer(config)) {
            frontend.start();
            LegacyBackendLogin.Result login = LegacyBackendLogin.connectAndLogin(
                    "127.0.0.1", frontend.boundPort(), "Tester132", LegacyProtocol.V1_3_2);
            assertEquals(42, login.loginResponse().intField(0));
            assertEquals("Tester132", backend.username);
            LegacyConnection client = login.connection();
            assertEquals(LegacyProtocol.V1_3_2, client.legacyProtocol());

            client.sendPacket(new LegacyPacket(0x03, List.of("hello 1.3.2 backend")));
            LegacyPacket atBackend = backend.received.poll(5, TimeUnit.SECONDS);
            assertNotNull(atBackend, "1.3.2 backend should receive the chat");
            assertEquals("hello 1.3.2 backend", atBackend.field(0));

            // The 39 wire traits relay natively in direct mode:
            // single-long time update, i16 health and the expanded
            // 0x15 pickup spawn (inlined stack, no NBT).
            backend.send(new LegacyPacket(0x04, List.of(6000L)));
            LegacyPacket time = readUntil(client, 0x04);
            assertEquals(List.of(6000L), time.fields());
            backend.send(new LegacyPacket(0x08, List.of(20, 18, 0.5f)));
            LegacyPacket health = readUntil(client, 0x08);
            assertEquals(20, health.intField(0));
            backend.send(new LegacyPacket(0x15, List.of(
                    7, new Slot(276, 1, 0, null), 160, 2048, -160, 10, 20, 30)));
            LegacyPacket pickup = readUntil(client, 0x15);
            assertEquals(7, pickup.intField(0));
            assertEquals(276, ((Slot) pickup.field(1)).itemId());
            client.close();
        } finally {
            backend.close();
        }
    }

    @Test
    void bridgeMode39DropsSignEditorAndAttributes() throws Exception {
        FakeCore core = new FakeCore();
        Thread coreThread = new Thread(core);
        coreThread.setDaemon(true);
        coreThread.start();

        LegacyFrontendConfig config = LegacyFrontendConfig.bridge(
                0, "127.0.0.1", core.port(), "Test MOTD", 20);
        try (LegacyFrontendServer frontend = new LegacyFrontendServer(config)) {
            frontend.start();
            LegacyBackendLogin.Result login = LegacyBackendLogin.connectAndLogin(
                    "127.0.0.1", frontend.boundPort(), "Bridge132", LegacyProtocol.V1_3_2);
            assertEquals(7, login.loginResponse().intField(0));
            assertEquals("Bridge132", core.loginName);
            LegacyConnection client = login.connection();

            // Same shape as the 49 / legacy-47 bridge tests:
            // neither 0x85 nor 0x2C may reach a 1.3.2 client.
            LegacyPacket ka = readUntil(client, 0x00);
            assertEquals(999, ka.intField(0));
            List<LegacyPacket> extra = new java.util.concurrent.CopyOnWriteArrayList<>();
            Thread drainer = new Thread(() -> {
                try {
                    LegacyPacket p;
                    while ((p = client.readPacket()) != null) {
                        extra.add(p);
                    }
                } catch (IOException ignored) {
                    // closed at test end
                }
            });
            drainer.setDaemon(true);
            drainer.start();
            Thread.sleep(800);
            for (LegacyPacket packet : extra) {
                assertTrue(packet.id() != 0x85 && packet.id() != 0x2C,
                        "packet 0x" + Integer.toHexString(packet.id())
                                + " must never reach a 1.3.2 client");
            }
            client.close();
        } finally {
            core.close();
        }
    }

    @Test
    void legacyPingSection1For132And131Configs() throws Exception {
        // Protocol 39 covers both 1.3.2 and 1.3.1 (measured on the
        // real jars; the widespread "1.3.1 = 38" table entry is
        // wrong). Each config answers with 39 and its own name.
        LegacyFrontendConfig config132 = LegacyFrontendConfig.direct(
                0, "127.0.0.1", 9, "Ping MOTD", 20, "1.3.2");
        try (LegacyFrontendServer frontend = new LegacyFrontendServer(config132)) {
            frontend.start();
            String payload = pingPayload(frontend.boundPort(), true);
            assertTrue(payload.startsWith("§1\0" + "39\0" + "1.3.2\0" + "Ping MOTD\0"),
                    "unexpected ping payload: " + payload);
        }
        LegacyFrontendConfig config131 = LegacyFrontendConfig.direct(
                0, "127.0.0.1", 9, "Ping MOTD", 20, "1.3.1");
        try (LegacyFrontendServer frontend = new LegacyFrontendServer(config131)) {
            frontend.start();
            String payload = pingPayload(frontend.boundPort(), true);
            assertTrue(payload.startsWith("§1\0" + "39\0" + "1.3.1\0" + "Ping MOTD\0"),
                    "unexpected ping payload: " + payload);
        }
    }

    @Test
    void legacyPingSection1For147And146Configs() throws Exception {
        LegacyFrontendConfig config147 = LegacyFrontendConfig.direct(
                0, "127.0.0.1", 9, "Ping MOTD", 20, "1.4.7");
        try (LegacyFrontendServer frontend = new LegacyFrontendServer(config147)) {
            frontend.start();
            String payload = pingPayload(frontend.boundPort(), true);
            assertTrue(payload.startsWith("§1\0" + "51\0" + "1.4.7\0" + "Ping MOTD\0"),
                    "unexpected ping payload: " + payload);
        }
        // 1.4.6 is the same protocol number (measured identical), so
        // its config answers with 51 and its own version name.
        LegacyFrontendConfig config146 = LegacyFrontendConfig.direct(
                0, "127.0.0.1", 9, "Ping MOTD", 20, "1.4.6");
        try (LegacyFrontendServer frontend = new LegacyFrontendServer(config146)) {
            frontend.start();
            String payload = pingPayload(frontend.boundPort(), true);
            assertTrue(payload.startsWith("§1\0" + "51\0" + "1.4.6\0" + "Ping MOTD\0"),
                    "unexpected ping payload: " + payload);
        }
    }

    @Test
    void directMode60RelaysLoginPlayAndHealth() throws Exception {
        FakeLegacyBackend backend = new FakeLegacyBackend(LegacyProtocol.V1_5_1);
        Thread backendThread = new Thread(backend);
        backendThread.setDaemon(true);
        backendThread.start();

        LegacyFrontendConfig config = LegacyFrontendConfig.direct(
                0, "127.0.0.1", backend.port(), "Test MOTD", 20, "1.5.1");
        try (LegacyFrontendServer frontend = new LegacyFrontendServer(config)) {
            frontend.start();
            LegacyBackendLogin.Result login = LegacyBackendLogin.connectAndLogin(
                    "127.0.0.1", frontend.boundPort(), "Tester151", LegacyProtocol.V1_5_1);
            assertEquals(42, login.loginResponse().intField(0));
            assertEquals("Tester151", backend.username);
            LegacyConnection client = login.connection();
            assertEquals(LegacyProtocol.V1_5_1, client.legacyProtocol());

            client.sendPacket(new LegacyPacket(0x03, List.of("hello 1.5.1 backend")));
            LegacyPacket atBackend = backend.received.poll(5, TimeUnit.SECONDS);
            assertNotNull(atBackend, "1.5.1 backend should receive the chat");
            assertEquals("hello 1.5.1 backend", atBackend.field(0));

            // Health on the 60 wire is the 61 layout (measured
            // identical): I16 health, I16 food, F32 saturation.
            backend.send(new LegacyPacket(0x08, List.of(20, 18, 0.5f)));
            LegacyPacket health = readUntil(client, 0x08);
            assertEquals(20, health.intField(0));
            assertEquals(18, health.intField(1));
            assertEquals(0.5f, (Float) health.field(2));
            client.close();
        } finally {
            backend.close();
        }
    }

    @Test
    void bridgeMode60DropsSignEditorAndAttributes() throws Exception {
        FakeCore core = new FakeCore();
        Thread coreThread = new Thread(core);
        coreThread.setDaemon(true);
        coreThread.start();

        LegacyFrontendConfig config = LegacyFrontendConfig.bridge(
                0, "127.0.0.1", core.port(), "Test MOTD", 20);
        try (LegacyFrontendServer frontend = new LegacyFrontendServer(config)) {
            frontend.start();
            LegacyBackendLogin.Result login = LegacyBackendLogin.connectAndLogin(
                    "127.0.0.1", frontend.boundPort(), "Bridge151", LegacyProtocol.V1_5_1);
            assertEquals(7, login.loginResponse().intField(0));
            assertEquals("Bridge151", core.loginName);
            LegacyConnection client = login.connection();

            // Same drops as 61: neither 0x85 nor 0x2C exists in
            // protocol 60 either.
            LegacyPacket ka = readUntil(client, 0x00);
            assertEquals(999, ka.intField(0));
            List<LegacyPacket> extra = new java.util.concurrent.CopyOnWriteArrayList<>();
            Thread drainer = new Thread(() -> {
                try {
                    LegacyPacket p;
                    while ((p = client.readPacket()) != null) {
                        extra.add(p);
                    }
                } catch (IOException ignored) {
                    // closed at test end
                }
            });
            drainer.setDaemon(true);
            drainer.start();
            Thread.sleep(800);
            for (LegacyPacket packet : extra) {
                assertTrue(packet.id() != 0x85 && packet.id() != 0x2C,
                        "packet 0x" + Integer.toHexString(packet.id())
                                + " must never reach a 1.5.1 client");
            }

            // A 60 entity action has no aux field, exactly like 61.
            client.sendPacket(new LegacyPacket(0x13, List.of(5, 1)));
            MinecraftPacket action = pollModern(core, 0x0B);
            PacketIO.Reader r = new PacketIO.Reader(action.payload());
            assertEquals(5, r.readInt());
            assertEquals(1, r.readByte());
            assertEquals(0, r.readInt());
            client.close();
        } finally {
            core.close();
        }
    }

    @Test
    void legacyPingSection1For151And15Configs() throws Exception {
        LegacyFrontendConfig config151 = LegacyFrontendConfig.direct(
                0, "127.0.0.1", 9, "Ping MOTD", 20, "1.5.1");
        try (LegacyFrontendServer frontend = new LegacyFrontendServer(config151)) {
            frontend.start();
            String payload = pingPayload(frontend.boundPort(), true);
            assertTrue(payload.startsWith("§1\0" + "60\0" + "1.5.1\0" + "Ping MOTD\0"),
                    "unexpected ping payload: " + payload);
        }
        // A 1.5 backend config advertises its own version name with
        // the same protocol 60.
        LegacyFrontendConfig config15 = LegacyFrontendConfig.direct(
                0, "127.0.0.1", 9, "Ping MOTD", 20, "1.5");
        try (LegacyFrontendServer frontend = new LegacyFrontendServer(config15)) {
            frontend.start();
            String payload = pingPayload(frontend.boundPort(), true);
            assertTrue(payload.startsWith("§1\0" + "60\0" + "1.5\0" + "Ping MOTD\0"),
                    "unexpected ping payload: " + payload);
        }
    }

    @Test
    void bridgeModeTranslatesBetweenLegacyAndModernCore() throws Exception {
        FakeCore core = new FakeCore();
        Thread coreThread = new Thread(core);
        coreThread.setDaemon(true);
        coreThread.start();

        LegacyFrontendConfig config = LegacyFrontendConfig.bridge(
                0, "127.0.0.1", core.port(), "Test MOTD", 20);
        try (LegacyFrontendServer frontend = new LegacyFrontendServer(config)) {
            frontend.start();
            LegacyBackendLogin.Result login = LegacyBackendLogin.connectAndLogin(
                    "127.0.0.1", frontend.boundPort(), "BridgeTester");
            // The login response is the translated join game (entity 7).
            assertEquals(7, login.loginResponse().intField(0));
            assertEquals("BridgeTester", core.loginName);
            LegacyConnection client = login.connection();

            // Core's chat (JSON) arrives as legacy plain chat; KA follows.
            LegacyPacket chat = readUntil(client, 0x03);
            assertEquals("Welcome", chat.field(0));
            LegacyPacket ka = readUntil(client, 0x00);
            assertEquals(999, ka.intField(0));

            // Client answers KA and chats; the core sees modern packets.
            client.sendPacket(new LegacyPacket(0x00, List.of(999)));
            client.sendPacket(new LegacyPacket(0x03, List.of("hi core")));
            MinecraftPacket kaBack = pollModern(core, 0x00);
            assertEquals(999, new PacketIO.Reader(kaBack.payload()).readInt());
            MinecraftPacket chatBack = pollModern(core, 0x01);
            assertEquals("hi core",
                    new PacketIO.Reader(chatBack.payload()).readString());
            client.close();
        } finally {
            core.close();
        }
    }

    @Test
    void legacyPingSection1() throws Exception {
        LegacyFrontendConfig config = LegacyFrontendConfig.bridge(
                0, "127.0.0.1", 9, "Ping MOTD", 20);
        try (LegacyFrontendServer frontend = new LegacyFrontendServer(config)) {
            frontend.start();
            String payload = pingPayload(frontend.boundPort(), true);
            assertTrue(payload.startsWith("§1\0" + "78\0" + "1.6.4\0" + "Ping MOTD\0"),
                    "unexpected ping payload: " + payload);
        }
    }

    @Test
    void directMode29RelaysPlaintextLoginPlayAndEquipment() throws Exception {
        FakeLegacyBackend29 backend = new FakeLegacyBackend29();
        Thread backendThread = new Thread(backend);
        backendThread.setDaemon(true);
        backendThread.start();

        LegacyFrontendConfig config = LegacyFrontendConfig.direct(
                0, "127.0.0.1", backend.port(), "Test MOTD", 20, "1.2.5");
        try (LegacyFrontendServer frontend = new LegacyFrontendServer(config)) {
            frontend.start();
            LegacyBackendLogin.Result login = LegacyBackendLogin.connectAndLogin(
                    "127.0.0.1", frontend.boundPort(), "Tester125", LegacyProtocol.V1_2_5);
            assertEquals(42, login.loginResponse().intField(0));
            assertEquals(8, login.loginResponse().fields().size(),
                    "the 29 login response has eight fields");
            assertEquals("Tester125", backend.username,
                    "the username comes from the 0x01 login packet, not the handshake");
            assertEquals(29, backend.loginProtocol);
            LegacyConnection client = login.connection();
            assertEquals(LegacyProtocol.V1_2_5, client.legacyProtocol());

            client.sendPacket(new LegacyPacket(0x03, List.of("hello 1.2.5 backend")));
            LegacyPacket atBackend = backend.received.poll(5, TimeUnit.SECONDS);
            assertNotNull(atBackend, "1.2.5 backend should receive the chat");
            assertEquals("hello 1.2.5 backend", atBackend.field(0));

            // The 29 wire traits relay natively in direct mode:
            // single-long time update and the inline equipment form.
            backend.send(new LegacyPacket(0x04, List.of(6000L)));
            LegacyPacket time = readUntil(client, 0x04);
            assertEquals(List.of(6000L), time.fields());
            backend.send(new LegacyPacket(0x05, List.of(
                    7, 0, new Slot(276, 1, 3, null))));
            LegacyPacket equip = readUntil(client, 0x05);
            assertEquals(276, ((Slot) equip.field(2)).itemId());
            assertEquals(3, ((Slot) equip.field(2)).damage());
            client.close();
        } finally {
            backend.close();
        }
    }

    @Test
    void bridgeMode29ClientJoinsWithEightFieldLogin() throws Exception {
        FakeCore core = new FakeCore();
        Thread coreThread = new Thread(core);
        coreThread.setDaemon(true);
        coreThread.start();

        LegacyFrontendConfig config = LegacyFrontendConfig.bridge(
                0, "127.0.0.1", core.port(), "Test MOTD", 20);
        try (LegacyFrontendServer frontend = new LegacyFrontendServer(config)) {
            frontend.start();
            LegacyBackendLogin.Result login = LegacyBackendLogin.connectAndLogin(
                    "127.0.0.1", frontend.boundPort(), "Bridge125", LegacyProtocol.V1_2_5);
            assertEquals(7, login.loginResponse().intField(0));
            assertEquals(8, login.loginResponse().fields().size(),
                    "the bridge join reaches a 29 client as the eight-field login");
            assertEquals("", login.loginResponse().field(1),
                    "the second login field is the empty level-type slot filler");
            assertEquals("Bridge125", core.loginName);
            LegacyConnection client = login.connection();

            // Neither the sign editor (0x85) nor entity properties
            // (0x2C) exists in protocol 29.
            LegacyPacket ka = readUntil(client, 0x00);
            assertEquals(999, ka.intField(0));
            List<LegacyPacket> extra = new java.util.concurrent.CopyOnWriteArrayList<>();
            Thread drainer = new Thread(() -> {
                try {
                    LegacyPacket p;
                    while ((p = client.readPacket()) != null) {
                        extra.add(p);
                    }
                } catch (IOException ignored) {
                    // closed at test end
                }
            });
            drainer.setDaemon(true);
            drainer.start();
            Thread.sleep(800);
            for (LegacyPacket packet : extra) {
                assertTrue(packet.id() != 0x85 && packet.id() != 0x2C,
                        "packet 0x" + Integer.toHexString(packet.id())
                                + " must never reach a 1.2.5 client");
            }
            client.close();
        } finally {
            core.close();
        }
    }

    @Test
    void directMode28RelaysPlaintextLoginAndPlay() throws Exception {
        FakePlaintextBackend backend = new FakePlaintextBackend(
                LegacyProtocol.V1_2_3,
                List.of(42, "", "default", 0, 0, 2, 0, 20));
        Thread backendThread = new Thread(backend);
        backendThread.setDaemon(true);
        backendThread.start();

        LegacyFrontendConfig config = LegacyFrontendConfig.direct(
                0, "127.0.0.1", backend.port(), "Test MOTD", 20, "1.2.3");
        try (LegacyFrontendServer frontend = new LegacyFrontendServer(config)) {
            frontend.start();
            LegacyBackendLogin.Result login = LegacyBackendLogin.connectAndLogin(
                    "127.0.0.1", frontend.boundPort(), "Tester123", LegacyProtocol.V1_2_3);
            assertEquals(42, login.loginResponse().intField(0));
            assertEquals(8, login.loginResponse().fields().size(),
                    "the 28 login response is the eight-field form, like 29");
            assertEquals("Tester123", backend.username);
            assertEquals(28, backend.loginProtocol,
                    "the frontend's backend face speaks protocol 28");
            LegacyConnection client = login.connection();
            assertEquals(LegacyProtocol.V1_2_3, client.legacyProtocol());

            client.sendPacket(new LegacyPacket(0x03, List.of("hello 1.2.3 backend")));
            LegacyPacket atBackend = backend.received.poll(5, TimeUnit.SECONDS);
            assertNotNull(atBackend);
            assertEquals("hello 1.2.3 backend", atBackend.field(0));

            backend.send(new LegacyPacket(0x04, List.of(6000L)));
            LegacyPacket time = readUntil(client, 0x04);
            assertEquals(List.of(6000L), time.fields());
            client.close();
        } finally {
            backend.close();
        }
    }

    @Test
    void directMode23RelaysPlaintextSeedLoginAndPlay() throws Exception {
        FakePlaintextBackend backend = new FakePlaintextBackend(
                LegacyProtocol.V1_1,
                List.of(42, "", 4242424242L, "default", 0, 0, 2, 128, 20));
        Thread backendThread = new Thread(backend);
        backendThread.setDaemon(true);
        backendThread.start();

        LegacyFrontendConfig config = LegacyFrontendConfig.direct(
                0, "127.0.0.1", backend.port(), "Test MOTD", 20, "1.1");
        try (LegacyFrontendServer frontend = new LegacyFrontendServer(config)) {
            frontend.start();
            LegacyBackendLogin.Result login = LegacyBackendLogin.connectAndLogin(
                    "127.0.0.1", frontend.boundPort(), "Tester11", LegacyProtocol.V1_1);
            assertEquals(42, login.loginResponse().intField(0));
            assertEquals(9, login.loginResponse().fields().size(),
                    "the 23 login response has nine fields");
            assertEquals(4242424242L, login.loginResponse().field(2),
                    "the backend's world seed reaches the client in direct mode");
            assertEquals("Tester11", backend.username);
            assertEquals(23, backend.loginProtocol);
            LegacyConnection client = login.connection();
            assertEquals(LegacyProtocol.V1_1, client.legacyProtocol());

            client.sendPacket(new LegacyPacket(0x03, List.of("hello 1.1 backend")));
            LegacyPacket atBackend = backend.received.poll(5, TimeUnit.SECONDS);
            assertNotNull(atBackend);
            assertEquals("hello 1.1 backend", atBackend.field(0));

            backend.send(new LegacyPacket(0x04, List.of(12000L)));
            LegacyPacket time = readUntil(client, 0x04);
            assertEquals(List.of(12000L), time.fields());
            client.close();
        } finally {
            backend.close();
        }
    }

    @Test
    void directMode22RelaysPlaintextSeedLoginAndPlay() throws Exception {
        FakePlaintextBackend backend = new FakePlaintextBackend(
                LegacyProtocol.V1_0,
                List.of(42, "", 4242424242L, 0, 0, 2, 128, 20));
        Thread backendThread = new Thread(backend);
        backendThread.setDaemon(true);
        backendThread.start();

        LegacyFrontendConfig config = LegacyFrontendConfig.direct(
                0, "127.0.0.1", backend.port(), "Test MOTD", 20, "1.0.0");
        try (LegacyFrontendServer frontend = new LegacyFrontendServer(config)) {
            frontend.start();
            LegacyBackendLogin.Result login = LegacyBackendLogin.connectAndLogin(
                    "127.0.0.1", frontend.boundPort(), "Tester100", LegacyProtocol.V1_0);
            assertEquals(42, login.loginResponse().intField(0));
            assertEquals(8, login.loginResponse().fields().size(),
                    "the 22 login response has eight fields (no level type)");
            assertEquals(4242424242L, login.loginResponse().field(2),
                    "the backend's world seed reaches the client in direct mode");
            assertEquals("Tester100", backend.username);
            assertEquals(22, backend.loginProtocol);
            LegacyConnection client = login.connection();

            client.sendPacket(new LegacyPacket(0x03, List.of("hello 1.0.0 backend")));
            LegacyPacket atBackend = backend.received.poll(5, TimeUnit.SECONDS);
            assertNotNull(atBackend);
            assertEquals("hello 1.0.0 backend", atBackend.field(0));
            client.close();
        } finally {
            backend.close();
        }
    }

    @Test
    void directMode11KicksMismatchedPlaintextClientsLikeVanilla() throws Exception {
        FakePlaintextBackend backend = new FakePlaintextBackend(
                LegacyProtocol.V1_1,
                List.of(42, "", 0L, "default", 0, 0, 2, 128, 20));
        Thread backendThread = new Thread(backend);
        backendThread.setDaemon(true);
        backendThread.start();

        LegacyFrontendConfig config = LegacyFrontendConfig.direct(
                0, "127.0.0.1", backend.port(), "Test MOTD", 20, "1.1");
        try (LegacyFrontendServer frontend = new LegacyFrontendServer(config)) {
            frontend.start();
            // A 1.0.0 (22) client against a 1.1 backend: the real
            // vanilla kicks with "Outdated client!". The kick
            // surfaces in the login helper as its disconnect error.
            try {
                LegacyBackendLogin.connectAndLogin(
                        "127.0.0.1", frontend.boundPort(), "Old", LegacyProtocol.V1_0);
                fail("a 22 client must be kicked by a 1.1 direct frontend");
            } catch (IOException e) {
                assertTrue(e.getMessage().contains("Outdated client!"),
                        "unexpected kick: " + e.getMessage());
            }
            // A 1.2.3 (28) client is newer than the backend.
            try {
                LegacyBackendLogin.connectAndLogin(
                        "127.0.0.1", frontend.boundPort(), "New", LegacyProtocol.V1_2_3);
                fail("a 28 client must be kicked by a 1.1 direct frontend");
            } catch (IOException e) {
                assertTrue(e.getMessage().contains("Outdated server!"),
                        "unexpected kick: " + e.getMessage());
            }
        } finally {
            backend.close();
        }
    }

    @Test
    void bridgeMode28ClientJoinsWithEightFieldLogin() throws Exception {
        FakeCore core = new FakeCore();
        Thread coreThread = new Thread(core);
        coreThread.setDaemon(true);
        coreThread.start();

        LegacyFrontendConfig config = LegacyFrontendConfig.bridge(
                0, "127.0.0.1", core.port(), "Test MOTD", 20);
        try (LegacyFrontendServer frontend = new LegacyFrontendServer(config)) {
            frontend.start();
            LegacyBackendLogin.Result login = LegacyBackendLogin.connectAndLogin(
                    "127.0.0.1", frontend.boundPort(), "Bridge123", LegacyProtocol.V1_2_3);
            assertEquals(7, login.loginResponse().intField(0));
            assertEquals(8, login.loginResponse().fields().size(),
                    "the bridge join reaches a 28 client as the eight-field login");
            assertEquals("Bridge123", core.loginName);
            LegacyConnection client = login.connection();
            LegacyPacket ka = readUntil(client, 0x00);
            assertEquals(999, ka.intField(0));
            client.close();
        } finally {
            core.close();
        }
    }

    @Test
    void bridgeMode23ClientJoinsWithSeedLogin() throws Exception {
        FakeCore core = new FakeCore();
        Thread coreThread = new Thread(core);
        coreThread.setDaemon(true);
        coreThread.start();

        LegacyFrontendConfig config = LegacyFrontendConfig.bridge(
                0, "127.0.0.1", core.port(), "Test MOTD", 20);
        try (LegacyFrontendServer frontend = new LegacyFrontendServer(config)) {
            frontend.start();
            LegacyBackendLogin.Result login = LegacyBackendLogin.connectAndLogin(
                    "127.0.0.1", frontend.boundPort(), "Bridge11", LegacyProtocol.V1_1);
            assertEquals(7, login.loginResponse().intField(0));
            assertEquals(9, login.loginResponse().fields().size(),
                    "the bridge join reaches a 23 client as the nine-field seed login");
            assertEquals(0L, login.loginResponse().field(2),
                    "the bridge has no world seed: 0 is sent");
            // The height travels as one byte: 128 is 0x80, which
            // the signed field readback reports as -128 — exactly
            // the byte the real 1.1 server sent in Stage A.
            assertEquals(-128, login.loginResponse().intField(7),
                    "the 1.1 world height byte is 0x80 (128 unsigned)");
            assertEquals("Bridge11", core.loginName);
            LegacyConnection client = login.connection();
            assertEquals(LegacyProtocol.V1_1, client.legacyProtocol(),
                    "the session adopted protocol 23 from the login packet");

            // The keep-alive reaches the client; neither the sign
            // editor (0x85) nor entity properties (0x2C) exists in
            // protocol 23 and neither may arrive.
            LegacyPacket ka = readUntil(client, 0x00);
            assertEquals(999, ka.intField(0));
            List<LegacyPacket> extra = new java.util.concurrent.CopyOnWriteArrayList<>();
            Thread drainer = new Thread(() -> {
                try {
                    LegacyPacket p;
                    while ((p = client.readPacket()) != null) {
                        extra.add(p);
                    }
                } catch (IOException ignored) {
                    // closed at test end
                }
            });
            drainer.setDaemon(true);
            drainer.start();
            Thread.sleep(800);
            for (LegacyPacket packet : extra) {
                assertTrue(packet.id() != 0x85 && packet.id() != 0x2C,
                        "packet 0x" + Integer.toHexString(packet.id())
                                + " must never reach a 1.1 client");
            }
            client.close();
        } finally {
            core.close();
        }
    }

    @Test
    void bridgeMode22ClientJoinsWithoutLevelType() throws Exception {
        FakeCore core = new FakeCore();
        Thread coreThread = new Thread(core);
        coreThread.setDaemon(true);
        coreThread.start();

        LegacyFrontendConfig config = LegacyFrontendConfig.bridge(
                0, "127.0.0.1", core.port(), "Test MOTD", 20);
        try (LegacyFrontendServer frontend = new LegacyFrontendServer(config)) {
            frontend.start();
            LegacyBackendLogin.Result login = LegacyBackendLogin.connectAndLogin(
                    "127.0.0.1", frontend.boundPort(), "Bridge100", LegacyProtocol.V1_0);
            assertEquals(7, login.loginResponse().intField(0));
            assertEquals(8, login.loginResponse().fields().size(),
                    "the bridge join reaches a 22 client as the eight-field seed login");
            assertEquals(0L, login.loginResponse().field(2),
                    "the bridge has no world seed: 0 is sent");
            assertEquals("Bridge100", core.loginName);
            LegacyConnection client = login.connection();
            LegacyPacket ka = readUntil(client, 0x00);
            assertEquals(999, ka.intField(0));
            client.close();
        } finally {
            core.close();
        }
    }

    @Test
    void legacyPingOldFormFor125Config() throws Exception {
        // 1.2.5 answers only the old ping form (the section-1 form
        // starts at legacy 47 / 1.4.2, measured in Stage A), and a
        // 1.2.5 client sends the bare FE 01.
        LegacyFrontendConfig config = LegacyFrontendConfig.direct(
                0, "127.0.0.1", 9, "Ping MOTD", 20, "1.2.5");
        try (LegacyFrontendServer frontend = new LegacyFrontendServer(config)) {
            frontend.start();
            String payload = pingPayload(frontend.boundPort(), false);
            assertTrue(payload.startsWith("Ping MOTD§"),
                    "unexpected ping payload: " + payload);
            assertTrue(payload.endsWith("§20"),
                    "unexpected ping payload: " + payload);
            assertTrue(!payload.startsWith("§1"),
                    "the old form has no section-1 prefix: " + payload);
        }
    }

    @Test
    void legacyPingBareFormGetsOldResponseLike161() throws Exception {
        // Mirrors real 1.6.1: a bare FE 01 (no PingHost message) is
        // answered with the old form, not section-1.
        LegacyFrontendConfig config = LegacyFrontendConfig.bridge(
                0, "127.0.0.1", 9, "Ping MOTD", 20);
        try (LegacyFrontendServer frontend = new LegacyFrontendServer(config)) {
            frontend.start();
            String payload = pingPayload(frontend.boundPort(), false);
            assertEquals("Ping MOTD§1§20", payload);
        }
    }

    /** Sends a legacy ping (full PingHost form or bare) and reads the reply string. */
    private static String pingPayload(int port, boolean fullForm) throws Exception {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress("127.0.0.1", port), 5_000);
            socket.setSoTimeout(5_000);
            java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
            java.io.DataOutputStream d = new java.io.DataOutputStream(bos);
            d.writeByte(0xFE);
            d.writeByte(0x01);
            if (fullForm) {
                d.writeByte(0xFA);
                writeLegacyString(d, "MC|PingHost");
                java.io.ByteArrayOutputStream pb = new java.io.ByteArrayOutputStream();
                java.io.DataOutputStream pd = new java.io.DataOutputStream(pb);
                pd.writeByte(73);
                writeLegacyString(pd, "127.0.0.1");
                pd.writeInt(port);
                pd.flush();
                d.writeShort(pb.size());
                d.write(pb.toByteArray());
            }
            d.flush();
            socket.getOutputStream().write(bos.toByteArray());
            socket.getOutputStream().flush();
            byte[] response = socket.getInputStream().readAllBytes();
            assertTrue(response.length > 2);
            assertEquals(0xFF, response[0] & 0xFF);
            LegacyWire.Reader reader = new LegacyWire.Reader(
                    Arrays.copyOfRange(response, 1, response.length));
            return reader.string();
        }
    }

    private static void writeLegacyString(java.io.DataOutputStream d, String s)
            throws IOException {
        d.writeShort(s.length());
        d.writeChars(s);
    }

    // ------------------------------------------------------------------

    private static LegacyPacket readUntil(LegacyConnection connection, int id)
            throws IOException {
        long deadline = System.currentTimeMillis() + 5_000;
        while (System.currentTimeMillis() < deadline) {
            LegacyPacket packet = connection.readPacket();
            if (packet == null) {
                continue;
            }
            if (packet.id() == id) {
                return packet;
            }
        }
        fail("timed out waiting for legacy packet 0x" + Integer.toHexString(id));
        return null;
    }

    private static MinecraftPacket pollModern(FakeCore core, int id)
            throws InterruptedException {
        long deadline = System.currentTimeMillis() + 5_000;
        while (System.currentTimeMillis() < deadline) {
            MinecraftPacket packet = core.received.poll(200, TimeUnit.MILLISECONDS);
            if (packet == null) {
                continue;
            }
            if (packet.id() == id) {
                return packet;
            }
        }
        fail("timed out waiting for modern packet " + id);
        return null;
    }
}
