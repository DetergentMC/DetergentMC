package io.detergentmc.bedrock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import io.detergentmc.bedrock.BedrockBatch.GamePacket;
import io.detergentmc.bedrock.BedrockVarInt.ByteWriter;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

/**
 * Bedrock サーバー（UDP ループバック）のテスト。偽 Bedrock クライアント
 * （生 UDP）で Ping→接続→ログイン→チャット→移動までを通す。
 * Java バックエンドは偽物に差し替える（本物のバニラは使わない）。
 */
class BedrockServerTest {

    /** 偽 Java バックエンド（BedrockServerTest 用）。 */
    private static final class FakeBackend implements JavaBackend {
        JavaBackendConnection.Listener listener;
        final List<String> chats = new ArrayList<>();
        final List<double[]> positions = new ArrayList<>();
        final String javaName;
        final UUID javaUuid;
        boolean connected;
        boolean closed;

        FakeBackend(String javaName, UUID javaUuid) {
            this.javaName = javaName;
            this.javaUuid = javaUuid;
        }

        @Override
        public void connect() {
            connected = true;
        }

        @Override
        public boolean isJoined() {
            return connected;
        }

        @Override
        public boolean isClosed() {
            return closed;
        }

        @Override
        public void sendChat(String message) {
            chats.add(message);
        }

        @Override
        public void sendCommand(String command) {
        }

        @Override
        public void sendPosition(double x, double y, double z,
                float yaw, float pitch, boolean onGround) {
            positions.add(new double[]{x, y, z});
        }

        @Override
        public void sendUseEntity(int entityId, int action, int hand) {
        }

        @Override
        public void sendEntityAction(int action) {
        }

        @Override
        public void close() {
            closed = true;
        }
    }

    /** 偽 Bedrock クライアント（生 UDP）。受信したものをキューにためる。 */
    private static final class FakeBedrockClient implements AutoCloseable {
        final DatagramSocket socket;
        final InetSocketAddress serverAddress;
        final BlockingQueue<byte[]> received = new LinkedBlockingQueue<>();
        volatile boolean closed;
        int sendSequence;
        int messageIndex;
        final long clientGuid = -424242L;

        FakeBedrockClient(int serverPort) throws IOException {
            socket = new DatagramSocket(0, InetAddress.getByName("127.0.0.1"));
            socket.setSoTimeout(5_000);
            serverAddress = new InetSocketAddress("127.0.0.1", serverPort);
            Thread reader = new Thread(() -> {
                byte[] buffer = new byte[65535];
                while (!closed) {
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                    try {
                        socket.receive(packet);
                    } catch (IOException e) {
                        return;
                    }
                    byte[] datagram = new byte[packet.getLength()];
                    System.arraycopy(packet.getData(), packet.getOffset(),
                            datagram, 0, datagram.length);
                    received.add(datagram);
                }
            }, "fake-bedrock-client-reader");
            reader.setDaemon(true);
            reader.start();
        }

        void send(byte[] datagram) throws IOException {
            socket.send(new DatagramPacket(datagram, datagram.length, serverAddress));
        }

        void sendFrameSet(byte[] payload) throws IOException {
            send(RakNetCodec.buildFrameSet(sendSequence++ & 0xFFFFFF,
                    RakNetCodec.RELIABILITY_RELIABLE, messageIndex++, payload));
        }

        void sendGamePacket(int id, byte[] payload) throws IOException {
            sendFrameSet(BedrockBatch.encodeOne(id, payload));
        }

        /** 指定した先頭バイトのデータグラムが届くまで待つ。 */
        byte[] waitForFirstByte(int firstByte, long timeoutMillis)
                throws InterruptedException {
            long deadline = System.currentTimeMillis() + timeoutMillis;
            List<byte[]> others = new ArrayList<>();
            try {
                while (true) {
                    long remaining = deadline - System.currentTimeMillis();
                    if (remaining <= 0) {
                        return null;
                    }
                    byte[] datagram = received.poll(remaining, TimeUnit.MILLISECONDS);
                    if (datagram == null) {
                        return null;
                    }
                    if (datagram.length > 0 && (datagram[0] & 0xFF) == firstByte) {
                        return datagram;
                    }
                    others.add(datagram);
                }
            } finally {
                received.addAll(others);
            }
        }

        /** 指定 ID のゲームパケットが届くまで待つ。 */
        GamePacket waitForGamePacket(int id, long timeoutMillis) throws Exception {
            long deadline = System.currentTimeMillis() + timeoutMillis;
            List<byte[]> others = new ArrayList<>();
            try {
                while (true) {
                    long remaining = deadline - System.currentTimeMillis();
                    if (remaining <= 0) {
                        return null;
                    }
                    byte[] datagram = received.poll(remaining, TimeUnit.MILLISECONDS);
                    if (datagram == null) {
                        return null;
                    }
                    if (RakNetCodec.isFrameSet(datagram)) {
                        RakNetCodec.FrameSet frameSet = RakNetCodec.parseFrameSet(datagram);
                        for (RakNetCodec.Frame frame : frameSet.frames()) {
                            if (frame.payload().length > 0
                                    && (frame.payload()[0] & 0xFF) == BedrockProtocol.ID_BATCH) {
                                for (GamePacket packet : BedrockBatch.decode(frame.payload())) {
                                    if (packet.id() == id) {
                                        return packet;
                                    }
                                }
                            }
                        }
                    }
                    others.add(datagram);
                }
            } finally {
                received.addAll(others);
            }
        }

        @Override
        public void close() {
            closed = true;
            socket.close();
        }
    }

    /**
     * この環境で UDP 送信ができるかどうかを確かめる。このサンドボックスでは
     * UDP の bind はできても send が「Operation not permitted」で
     * 禁止されているため、送れない環境では UDP 経由のテストをスキップする
     * （ルーティング自体のテストは {@link #routingWithoutNetwork()} で行う）。
     */
    private static boolean udpSendWorks() {
        try (DatagramSocket probe = new DatagramSocket(0, InetAddress.getByName("127.0.0.1"))) {
            probe.send(new DatagramPacket(new byte[]{0}, 1,
                    new InetSocketAddress("127.0.0.1", 9)));
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    @Test
    void routingWithoutNetwork() throws Exception {
        // UDP 送信ができなくても、受信したデータグラムの振り分け自体は
        // handleDatagram 経由で検証できる（応答の送信失敗は警告ログになるだけ）。
        BedrockConfig config = BedrockConfig.defaults(765, 25566);
        BedrockServer server = new BedrockServer(config,
                (name, uuid, listener) -> new FakeBackend(name, uuid));
        server.start();
        try {
            InetSocketAddress remote = new InetSocketAddress("127.0.0.1", 50001);
            // Unconnected Ping は例外なく処理できること。
            ByteWriter ping = new ByteWriter();
            ping.writeByte(BedrockProtocol.ID_UNCONNECTED_PING);
            ping.writeBigLong(777L);
            ping.writeBytes(BedrockProtocol.RAKNET_MAGIC);
            ping.writeBigLong(-1L);
            server.handleDatagram(remote, ping.bytes());
            assertEquals(0, server.sessionCount());

            // Connection Request を含むフレームセットでセッションができること。
            ByteWriter connectionRequest = new ByteWriter();
            connectionRequest.writeByte(BedrockProtocol.ID_CONNECTION_REQUEST);
            connectionRequest.writeBigLong(-424242L);
            connectionRequest.writeBigLong(1000L);
            connectionRequest.writeBoolean(false);
            server.handleDatagram(remote, RakNetCodec.buildFrameSet(0,
                    RakNetCodec.RELIABILITY_RELIABLE, 0, connectionRequest.bytes()));
            assertEquals(1, server.sessionCount());
            assertNotNull(server.sessionsSnapshot().get(remote));
        } finally {
            server.stop();
        }
    }

    @Test
    void unconnectedPingReturnsMotd() throws Exception {
        assumeTrue(udpSendWorks(),
                "この環境では UDP 送信が禁止されているためスキップします。");
        BedrockConfig config = BedrockConfig.defaults(765, 25566);
        BedrockServer server = new BedrockServer(config,
                (name, uuid, listener) -> new FakeBackend(name, uuid));
        server.start();
        try (FakeBedrockClient client = new FakeBedrockClient(server.localPort())) {
            ByteWriter ping = new ByteWriter();
            ping.writeByte(BedrockProtocol.ID_UNCONNECTED_PING);
            ping.writeBigLong(777L);
            ping.writeBytes(BedrockProtocol.RAKNET_MAGIC);
            ping.writeBigLong(client.clientGuid);
            client.send(ping.bytes());

            byte[] pong = client.waitForFirstByte(
                    BedrockProtocol.ID_UNCONNECTED_PONG, 5_000);
            assertNotNull(pong, "Unconnected Pong が返ること");
            BedrockVarInt.ByteReader in = new BedrockVarInt.ByteReader(pong, 1);
            assertEquals(777L, in.readBigLong());
            in.readBigLong(); // server GUID
            in.readBytes(16); // Magic
            int motdLength = in.readBigUnsignedShort();
            String motd = new String(in.readBytes(motdLength),
                    java.nio.charset.StandardCharsets.UTF_8);
            BedrockMotd.Parsed parsed = BedrockMotd.parse(motd);
            assertNotNull(parsed, "MOTD: " + motd);
            assertEquals(630, parsed.protocol());
            assertEquals("1.20.50", parsed.version());
            assertEquals(0, parsed.players());
        } finally {
            server.stop();
        }
    }

    @Test
    void fullJoinChatAndMoveOverUdp() throws Exception {
        assumeTrue(udpSendWorks(),
                "この環境では UDP 送信が禁止されているためスキップします。");
        BedrockConfig config = BedrockConfig.defaults(765, 25566);
        List<FakeBackend> backends = new CopyOnWriteArrayList<>();
        BedrockServer server = new BedrockServer(config, (name, uuid, listener) -> {
            FakeBackend backend = new FakeBackend(name, uuid);
            backend.listener = listener;
            backends.add(backend);
            return backend;
        });
        server.start();
        try (FakeBedrockClient client = new FakeBedrockClient(server.localPort())) {
            // Open Connection Request 1（MTU 探りのゼロ詰め付き）。
            ByteWriter request1 = new ByteWriter();
            request1.writeByte(BedrockProtocol.ID_OPEN_CONNECTION_REQUEST_1);
            request1.writeBytes(BedrockProtocol.RAKNET_MAGIC);
            request1.writeByte(11);
            for (int i = 0; i < 200; i++) {
                request1.writeByte(0);
            }
            client.send(request1.bytes());
            byte[] reply1 = client.waitForFirstByte(
                    BedrockProtocol.ID_OPEN_CONNECTION_REPLY_1, 5_000);
            assertNotNull(reply1, "Open Connection Reply 1 が返ること");

            // Open Connection Request 2。
            ByteWriter request2 = new ByteWriter();
            request2.writeByte(BedrockProtocol.ID_OPEN_CONNECTION_REQUEST_2);
            request2.writeBytes(BedrockProtocol.RAKNET_MAGIC);
            request2.writeBytes(RakNetCodec.encodeAddress(
                    new InetSocketAddress("127.0.0.1", server.localPort())));
            request2.writeByte(0x05);
            request2.writeByte(0xdc); // MTU 1500
            request2.writeBigLong(client.clientGuid);
            client.send(request2.bytes());
            assertNotNull(client.waitForFirstByte(
                            BedrockProtocol.ID_OPEN_CONNECTION_REPLY_2, 5_000),
                    "Open Connection Reply 2 が返ること");

            // Connection Request（フレーム）→ Accepted。
            ByteWriter connectionRequest = new ByteWriter();
            connectionRequest.writeByte(BedrockProtocol.ID_CONNECTION_REQUEST);
            connectionRequest.writeBigLong(client.clientGuid);
            connectionRequest.writeBigLong(1000L);
            connectionRequest.writeBoolean(false);
            client.sendFrameSet(connectionRequest.bytes());
            GamePacket accepted = null;
            long deadline = System.currentTimeMillis() + 5_000;
            while (System.currentTimeMillis() < deadline && accepted == null) {
                byte[] datagram = client.received.poll(5_000 - (System.currentTimeMillis()
                        - (deadline - 5_000)), TimeUnit.MILLISECONDS);
                if (datagram == null) {
                    break;
                }
                if (RakNetCodec.isFrameSet(datagram)) {
                    for (RakNetCodec.Frame frame :
                            RakNetCodec.parseFrameSet(datagram).frames()) {
                        if (frame.payload().length > 0
                                && (frame.payload()[0] & 0xFF)
                                        == BedrockProtocol.ID_CONNECTION_REQUEST_ACCEPTED) {
                            accepted = new GamePacket(
                                    BedrockProtocol.ID_CONNECTION_REQUEST_ACCEPTED,
                                    frame.payload());
                        }
                    }
                }
            }
            assertNotNull(accepted, "Connection Request Accepted が返ること");

            // New Incoming Connection。
            ByteWriter incoming = new ByteWriter();
            incoming.writeByte(BedrockProtocol.ID_NEW_INCOMING_CONNECTION);
            incoming.writeBytes(RakNetCodec.encodeAddress(
                    new InetSocketAddress("127.0.0.1", server.localPort())));
            incoming.writeBigLong(1000L);
            incoming.writeBigLong(2000L);
            client.sendFrameSet(incoming.bytes());

            // Login。
            client.sendGamePacket(BedrockProtocol.PACKET_LOGIN,
                    TestJwt.loginPayload(630, "Steve", "2535461234567890",
                            "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee"));
            GamePacket playStatus = client.waitForGamePacket(
                    BedrockProtocol.PACKET_PLAY_STATUS, 5_000);
            assertNotNull(playStatus, "Play Status（login_success）が返ること");
            assertEquals(0, new BedrockVarInt.ByteReader(playStatus.payload()).readBigInt());
            assertEquals(1, backends.size(), "Java バックエンドが1個開かれること");
            FakeBackend backend = backends.get(0);
            assertEquals(".Steve", backend.javaName);
            assertEquals(FloodgateId.uuidForXuid("2535461234567890"), backend.javaUuid);
            assertEquals(1, server.sessionCount());
            assertNotNull(client.waitForGamePacket(
                    BedrockProtocol.PACKET_RESOURCE_PACKS_INFO, 5_000),
                    "Resource Packs Info が返ること");

            // リソースパックのやり取り→Java 参加→Start Game。
            client.sendGamePacket(BedrockProtocol.PACKET_RESOURCE_PACK_CLIENT_RESPONSE,
                    new byte[]{3});
            assertNotNull(client.waitForGamePacket(
                    BedrockProtocol.PACKET_RESOURCE_PACK_STACK, 5_000),
                    "Resource Pack Stack が返ること");
            backend.listener.onJoined(11);
            client.sendGamePacket(BedrockProtocol.PACKET_RESOURCE_PACK_CLIENT_RESPONSE,
                    new byte[]{4});
            assertNotNull(client.waitForGamePacket(
                    BedrockProtocol.PACKET_START_GAME, 5_000),
                    "Start Game が返ること");
            assertNotNull(client.waitForGamePacket(
                    BedrockProtocol.PACKET_PLAY_STATUS, 5_000),
                    "Play Status（player_spawn）が返ること");

            // チャット→Java バックエンド。
            client.sendGamePacket(BedrockProtocol.PACKET_TEXT,
                    BedrockPackets.encodeTextChat("Steve", "hello from bedrock"));
            long chatDeadline = System.currentTimeMillis() + 5_000;
            while (System.currentTimeMillis() < chatDeadline && backend.chats.isEmpty()) {
                Thread.sleep(50);
            }
            assertEquals(List.of("hello from bedrock"), backend.chats);

            // Java 側のチャット→Bedrock クライアント。
            backend.listener.onChat("Alex", "hi bedrock");
            GamePacket text = client.waitForGamePacket(BedrockProtocol.PACKET_TEXT, 5_000);
            assertNotNull(text, "Bedrock へ Text が返ること");
            assertEquals("Alex", BedrockPackets.decodeText(text.payload()).sourceName());
            assertEquals("hi bedrock", BedrockPackets.decodeText(text.payload()).message());

            // 移動→Java バックエンド。
            client.sendGamePacket(BedrockProtocol.PACKET_MOVE_PLAYER,
                    BedrockPackets.encodeMovePlayerTeleport(11L, 5.5f, 64.0f, 5.5f, 0f, 0f));
            long moveDeadline = System.currentTimeMillis() + 5_000;
            while (System.currentTimeMillis() < moveDeadline && backend.positions.isEmpty()) {
                Thread.sleep(50);
            }
            assertEquals(1, backend.positions.size());
            assertEquals(5.5, backend.positions.get(0)[0], 0.001);

            // 切断。
            client.sendGamePacket(BedrockProtocol.PACKET_DISCONNECT,
                    BedrockPackets.encodeDisconnect("bye"));
            long closeDeadline = System.currentTimeMillis() + 5_000;
            while (System.currentTimeMillis() < closeDeadline && !backend.closed) {
                Thread.sleep(50);
            }
            assertTrue(backend.closed, "切断で Java バックエンドが閉じること");
        } finally {
            server.stop();
        }
    }

    @Test
    void bedrockConfigDefaults() {
        BedrockConfig config = BedrockConfig.defaults(765, 25566);
        assertTrue(config.enabled());
        assertEquals(19132, config.port());
        assertEquals(".", config.floodgatePrefix());
        assertEquals(765, config.serverProtocol());
        assertEquals(25566, config.internalPort());
    }
}
