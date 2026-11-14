package io.detergentmc.bedrock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.detergentmc.bedrock.BedrockBatch.GamePacket;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;

/**
 * Bedrock セッション（RakNet→Login→リソースパック→Start Game→
 * チャット・移動の翻訳）のテスト。UDP も Java バックエンドも偽物に差し替える。
 */
class BedrockSessionTest {

    /** 送られたデータグラムをためる偽 Transport。 */
    private static final class FakeTransport implements BedrockSession.Transport {
        final List<byte[]> sent = new CopyOnWriteArrayList<>();
        boolean closedNotified;
        /** 分割フレームの繋ぎ直し（StartGame など大きいパケット用）。 */
        final RakNetCodec.SplitReassembler splits = new RakNetCodec.SplitReassembler();

        @Override
        public void send(byte[] datagram) {
            sent.add(datagram.clone());
        }

        @Override
        public void onSessionClosed(BedrockSession session) {
            closedNotified = true;
        }

        /** 送られたフレームセットの中のゲームパケットを全部取り出す。 */
        List<GamePacket> gamePackets() throws IOException {
            List<GamePacket> out = new ArrayList<>();
            for (byte[] datagram : sent) {
                if (!RakNetCodec.isFrameSet(datagram)) {
                    continue;
                }
                for (byte[] payload : RakNetCodec.parseFrameSetPayloads(datagram, splits)) {
                    if (payload.length > 0
                            && (payload[0] & 0xFF) == BedrockProtocol.ID_BATCH) {
                        out.addAll(BedrockBatch.decode(payload));
                    }
                }
            }
            return out;
        }

        /** 指定 ID のゲームパケットが送られたかどうか。 */
        boolean hasGamePacket(int id) throws IOException {
            return gamePackets().stream().anyMatch(p -> p.id() == id);
        }

        void clear() {
            sent.clear();
        }
    }

    /** 偽 Java バックエンド。送られたものを記録し、Listener を保持する。 */
    private static final class FakeBackend implements JavaBackend {
        JavaBackendConnection.Listener listener;
        final List<String> chats = new ArrayList<>();
        final List<String> commands = new ArrayList<>();
        final List<double[]> positions = new ArrayList<>();
        final List<int[]> useEntities = new ArrayList<>();
        final List<Integer> entityActions = new ArrayList<>();
        boolean connected;
        boolean closed;

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
            commands.add(command);
        }

        @Override
        public void sendPosition(double x, double y, double z,
                float yaw, float pitch, boolean onGround) {
            positions.add(new double[]{x, y, z, yaw, pitch, onGround ? 1 : 0});
        }

        @Override
        public void sendUseEntity(int entityId, int action, int hand) {
            useEntities.add(new int[]{entityId, action, hand});
        }

        @Override
        public void sendEntityAction(int action) {
            entityActions.add(action);
        }

        @Override
        public void close() {
            closed = true;
        }
    }

    private static final class Fixture {
        final FakeTransport transport = new FakeTransport();
        final FakeBackend backend = new FakeBackend();
        final BedrockSession session;

        Fixture() {
            session = new BedrockSession(
                    new InetSocketAddress("127.0.0.1", 50000), -12345L, 999L, 1400,
                    transport,
                    (name, uuid, listener) -> {
                        backend.listener = listener;
                        return backend;
                    },
                    "DetergentMC", ".");
        }

        /** フレームセット 1 個をセッションへ渡す。 */
        void sendFrameSet(byte[] payload, int sequence) {
            session.handleFrameSet(RakNetCodec.buildFrameSet(sequence,
                    RakNetCodec.RELIABILITY_RELIABLE, sequence, payload));
        }

        /** ゲームパケット 1 個をバッチにして送る。 */
        void sendGamePacket(int id, byte[] payload, int sequence) {
            sendFrameSet(BedrockBatch.encodeOne(id, payload), sequence);
        }

        /** RakNet 接続（Connection Request＋New Incoming Connection）を済ませる。 */
        void establishRakNet() {
            BedrockVarInt.ByteWriter request = new BedrockVarInt.ByteWriter();
            request.writeByte(BedrockProtocol.ID_CONNECTION_REQUEST);
            request.writeBigLong(-12345L);
            request.writeBigLong(1000L);
            request.writeBoolean(false);
            sendFrameSet(request.bytes(), 1);
            BedrockVarInt.ByteWriter incoming = new BedrockVarInt.ByteWriter();
            incoming.writeByte(BedrockProtocol.ID_NEW_INCOMING_CONNECTION);
            incoming.writeBytes(RakNetCodec.encodeAddress(
                    new InetSocketAddress("127.0.0.1", 19132)));
            incoming.writeBigLong(1000L);
            incoming.writeBigLong(2000L);
            sendFrameSet(incoming.bytes(), 2);
            assertEquals(BedrockSession.State.WAIT_LOGIN, session.state());
        }

        /** ログイン→リソースパック→Java 参加→completed まで進め、PLAYING にする。 */
        void playUntilStartGame() {
            establishRakNet();
            sendGamePacket(BedrockProtocol.PACKET_LOGIN,
                    TestJwt.loginPayload(630, "Steve", "2535461234567890",
                            "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee"),
                    3);
            backend.listener.onJoined(7);
            backend.listener.onSpawnPosition(8.5, 64.0, 8.5);
            sendGamePacket(BedrockProtocol.PACKET_RESOURCE_PACK_CLIENT_RESPONSE,
                    new byte[]{3}, 4);
            sendGamePacket(BedrockProtocol.PACKET_RESOURCE_PACK_CLIENT_RESPONSE,
                    new byte[]{4}, 5);
            assertEquals(BedrockSession.State.PLAYING, session.state());
        }

        /** 指定 ID のゲームパケットを 1 個取り出す（無ければ null）。 */
        GamePacket findGamePacket(int id) throws IOException {
            return transport.gamePackets().stream()
                    .filter(p -> p.id() == id).findFirst().orElse(null);
        }
    }

    @Test
    void rakNetHandshakeSendsConnectionAccepted() throws Exception {
        Fixture fixture = new Fixture();
        BedrockVarInt.ByteWriter request = new BedrockVarInt.ByteWriter();
        request.writeByte(BedrockProtocol.ID_CONNECTION_REQUEST);
        request.writeBigLong(-12345L);
        request.writeBigLong(1000L);
        request.writeBoolean(false);
        fixture.sendFrameSet(request.bytes(), 1);
        // Connection Request Accepted（0x10）がフレームで返ってくること。
        boolean accepted = false;
        for (byte[] datagram : fixture.transport.sent) {
            if (!RakNetCodec.isFrameSet(datagram)) {
                continue;
            }
            for (RakNetCodec.Frame frame : RakNetCodec.parseFrameSet(datagram).frames()) {
                if (frame.payload().length > 0
                        && (frame.payload()[0] & 0xFF)
                                == BedrockProtocol.ID_CONNECTION_REQUEST_ACCEPTED) {
                    accepted = true;
                }
            }
        }
        assertTrue(accepted, "Connection Request Accepted が送られること");
    }

    @Test
    void loginFlowSendsPlayStatusAndResourcePacks() throws Exception {
        Fixture fixture = new Fixture();
        fixture.establishRakNet();
        fixture.sendGamePacket(BedrockProtocol.PACKET_LOGIN,
                TestJwt.loginPayload(630, "Steve", "2535461234567890",
                        "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee"),
                3);
        assertTrue(fixture.backend.connected, "Java バックエンドが開かれること");
        assertEquals(".Steve", fixture.session.javaName());
        assertEquals(FloodgateId.uuidForXuid("2535461234567890"),
                fixture.session.javaUuid());
        assertEquals(BedrockSession.State.LOGGED_IN, fixture.session.state());

        List<GamePacket> sent = fixture.transport.gamePackets();
        assertTrue(sent.stream().anyMatch(p -> p.id() == BedrockProtocol.PACKET_PLAY_STATUS
                        && p.payload().length == 4
                        && new BedrockVarInt.ByteReader(p.payload()).readBigInt() == 0),
                "Play Status（login_success）が送られること: " + sent);
        assertTrue(fixture.transport.hasGamePacket(BedrockProtocol.PACKET_RESOURCE_PACKS_INFO),
                "Resource Packs Info が送られること");
    }

    @Test
    void unsupportedBedrockProtocolIsDisconnected() throws Exception {
        Fixture fixture = new Fixture();
        fixture.establishRakNet();
        fixture.sendGamePacket(BedrockProtocol.PACKET_LOGIN,
                TestJwt.loginPayload(999, "Steve", "2535",
                        "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee"),
                3);
        assertTrue(fixture.transport.hasGamePacket(BedrockProtocol.PACKET_DISCONNECT),
                "対応外プロトコルは Disconnect で切ること");
        assertTrue(fixture.session.isClosed());
    }

    @Test
    void resourcePackFlowReachesStartGame() throws Exception {
        Fixture fixture = new Fixture();
        fixture.establishRakNet();
        fixture.sendGamePacket(BedrockProtocol.PACKET_LOGIN,
                TestJwt.loginPayload(630, "Steve", "2535461234567890",
                        "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee"),
                3);
        // have all packs（3）→ Resource Pack Stack が返る。
        fixture.sendGamePacket(BedrockProtocol.PACKET_RESOURCE_PACK_CLIENT_RESPONSE,
                new byte[]{3}, 4);
        assertTrue(fixture.transport.hasGamePacket(BedrockProtocol.PACKET_RESOURCE_PACK_STACK),
                "Resource Pack Stack が送られること");
        // Java 側の参加がまだなら Start Game はまだ送らない。
        assertFalse(fixture.transport.hasGamePacket(BedrockProtocol.PACKET_START_GAME),
                "Java 参加前は Start Game を送らないこと");
        // Java 側が参加しても、completed（4）がまだなら送らない。
        fixture.backend.listener.onJoined(7);
        assertFalse(fixture.transport.hasGamePacket(BedrockProtocol.PACKET_START_GAME),
                "Resource Pack completed 前は Start Game を送らないこと");
        // completed（4）→ Start Game＋Play Status（player_spawn）。
        fixture.sendGamePacket(BedrockProtocol.PACKET_RESOURCE_PACK_CLIENT_RESPONSE,
                new byte[]{4}, 5);
        assertTrue(fixture.transport.hasGamePacket(BedrockProtocol.PACKET_START_GAME),
                "Start Game が送られること");
        List<GamePacket> sent = fixture.transport.gamePackets();
        assertTrue(sent.stream().anyMatch(p -> p.id() == BedrockProtocol.PACKET_PLAY_STATUS
                        && new BedrockVarInt.ByteReader(p.payload()).readBigInt() == 3),
                "Play Status（player_spawn）が送られること");
        assertEquals(BedrockSession.State.PLAYING, fixture.session.state());
    }

    @Test
    void chatAndMovementAreTranslatedToJava() throws Exception {
        Fixture fixture = new Fixture();
        fixture.establishRakNet();
        fixture.sendGamePacket(BedrockProtocol.PACKET_LOGIN,
                TestJwt.loginPayload(630, "Steve", "2535461234567890",
                        "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee"),
                3);
        fixture.backend.listener.onJoined(7);
        fixture.backend.listener.onSpawnPosition(8.5, 64.0, 8.5);
        fixture.sendGamePacket(BedrockProtocol.PACKET_RESOURCE_PACK_CLIENT_RESPONSE,
                new byte[]{4}, 4);

        // チャット→Java。
        fixture.sendGamePacket(BedrockProtocol.PACKET_TEXT,
                BedrockPackets.encodeTextChat("Steve", "hello bedrock"), 5);
        assertEquals(List.of("hello bedrock"), fixture.backend.chats);
        // 「/」始まりはコマンドになる。
        fixture.sendGamePacket(BedrockProtocol.PACKET_TEXT,
                BedrockPackets.encodeTextChat("Steve", "/say hi"), 6);
        assertEquals(List.of("say hi"), fixture.backend.commands);

        // MovePlayer→Java の位置。
        fixture.sendGamePacket(BedrockProtocol.PACKET_MOVE_PLAYER,
                BedrockPackets.encodeMovePlayerTeleport(7L, 10.5f, 64.0f, -3.0f, 0f, 90f), 7);
        assertEquals(1, fixture.backend.positions.size());
        assertEquals(10.5, fixture.backend.positions.get(0)[0], 0.001);
        assertEquals(64.0, fixture.backend.positions.get(0)[1], 0.001);
        assertEquals(-3.0, fixture.backend.positions.get(0)[2], 0.001);

        // PlayerAuthInput→Java の位置。
        BedrockVarInt.ByteWriter authInput = new BedrockVarInt.ByteWriter();
        authInput.writeLittleFloat(0f);
        authInput.writeLittleFloat(180f);
        authInput.writeLittleFloat(20f);
        authInput.writeLittleFloat(65f);
        authInput.writeLittleFloat(20f);
        fixture.sendGamePacket(BedrockProtocol.PACKET_PLAYER_AUTH_INPUT, authInput.bytes(), 8);
        assertEquals(2, fixture.backend.positions.size());
        assertEquals(20.0, fixture.backend.positions.get(1)[0], 0.001);

        // Java 側のチャット→Bedrock の Text。
        fixture.transport.clear();
        fixture.backend.listener.onChat("Alex", "yo");
        List<GamePacket> sent = fixture.transport.gamePackets();
        assertEquals(1, sent.size());
        assertEquals(BedrockProtocol.PACKET_TEXT, sent.get(0).id());
        BedrockPackets.TextMessage text = BedrockPackets.decodeText(sent.get(0).payload());
        assertEquals("Alex", text.sourceName());
        assertEquals("yo", text.message());

        // Java 側のシステム文（送信者なし）は raw でそのまま出す。
        fixture.transport.clear();
        fixture.backend.listener.onChat("", "<Alex> yo");
        sent = fixture.transport.gamePackets();
        assertEquals(1, sent.size());
        assertEquals(0, BedrockPackets.decodeText(sent.get(0).payload()).type());
        assertEquals("<Alex> yo", BedrockPackets.decodeText(sent.get(0).payload()).message());

        // Java 側のテレポート→Bedrock の MovePlayer（チャンクが変わるので
        // ChunkRadiusUpdated＋NetworkChunkPublisherUpdate も一緒に送られる）。
        fixture.transport.clear();
        fixture.backend.listener.onTeleport(100.5, 70.0, -50.5, 45f, 10f);
        sent = fixture.transport.gamePackets();
        assertEquals(3, sent.size());
        GamePacket movePacket = sent.stream()
                .filter(p -> p.id() == BedrockProtocol.PACKET_MOVE_PLAYER)
                .findFirst().orElseThrow();
        BedrockPackets.MovePlayer move =
                BedrockPackets.decodeMovePlayer(movePacket.payload());
        assertEquals(100.5f, move.x(), 0.001f);
        assertEquals(2, move.mode());
        assertTrue(sent.stream().anyMatch(
                p -> p.id() == BedrockProtocol.PACKET_NETWORK_CHUNK_PUBLISHER_UPDATE),
                "移動先のチャンク更新が送られること");
    }

    @Test
    void disconnectClosesBackendAndNotifiesTransport() throws Exception {
        Fixture fixture = new Fixture();
        fixture.establishRakNet();
        fixture.sendGamePacket(BedrockProtocol.PACKET_LOGIN,
                TestJwt.loginPayload(630, "Steve", "2535461234567890",
                        "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee"),
                3);
        assertFalse(fixture.backend.closed);
        // ゲーム内の Disconnect パケット。
        fixture.sendGamePacket(BedrockProtocol.PACKET_DISCONNECT,
                BedrockPackets.encodeDisconnect("bye"), 4);
        assertTrue(fixture.session.isClosed());
        assertTrue(fixture.backend.closed);
        assertTrue(fixture.transport.closedNotified);
    }

    @Test
    void javaDisconnectIsForwardedToBedrock() throws Exception {
        Fixture fixture = new Fixture();
        fixture.establishRakNet();
        fixture.sendGamePacket(BedrockProtocol.PACKET_LOGIN,
                TestJwt.loginPayload(630, "Steve", "2535461234567890",
                        "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee"),
                3);
        fixture.backend.listener.onDisconnected("Kicked.");
        assertTrue(fixture.transport.hasGamePacket(BedrockProtocol.PACKET_DISCONNECT));
        assertTrue(fixture.session.isClosed());
        assertNotNull(fixture.session.javaUuid());
    }

    @Test
    void chunkFlowSendsSpawnChunksAfterStartGame() throws Exception {
        Fixture fixture = new Fixture();
        fixture.playUntilStartGame();
        fixture.transport.clear();
        // スポーン周辺のチャンク（0, 0）と遠いチャンク（20, 20）が届く。
        byte[] spawnChunk = TestJavaChunk.build765(0, 0,
                BedrockChunkTranslatorTest.TERRAIN, BedrockChunkTranslatorTest.TWO_BIOMES);
        byte[] farChunk = TestJavaChunk.build765(20, 20,
                BedrockChunkTranslatorTest.TERRAIN, BedrockChunkTranslatorTest.TWO_BIOMES);
        fixture.backend.listener.onChunk(spawnChunk);
        fixture.backend.listener.onChunk(farChunk);
        List<GamePacket> sent = fixture.transport.gamePackets();
        GamePacket levelChunk = sent.stream()
                .filter(p -> p.id() == BedrockProtocol.PACKET_LEVEL_CHUNK)
                .findFirst().orElse(null);
        assertNotNull(levelChunk, "スポーン周辺のチャンクが送られること: " + sent);
        // チャンク座標（0, 0）で、中の地面ブロックが草であること。
        BedrockVarInt.ByteReader in = new BedrockVarInt.ByteReader(levelChunk.payload());
        assertEquals(0, BedrockVarInt.readZigzag32(in));
        assertEquals(0, BedrockVarInt.readZigzag32(in));
        assertEquals(24, BedrockVarInt.readUnsignedVarInt(in));
        assertEquals(false, in.readBoolean());
        long payloadLength = BedrockVarInt.readUnsignedVarInt(in);
        byte[] payload = in.readBytes((int) payloadLength);
        BedrockLevelChunkDecoder.Decoded decoded =
                BedrockLevelChunkDecoder.decode(payload, 24);
        assertEquals(12183, decoded.blockAt(7, 8, 15, 8, 0), "スポーン地点の地面は草ブロック");
        assertEquals(1774, decoded.blockAt(7, 8, 11, 8, 0), "地面の下は石");
        // 遠いチャンクは送らない（1 個だけ）。
        assertEquals(1, sent.stream()
                .filter(p -> p.id() == BedrockProtocol.PACKET_LEVEL_CHUNK).count(),
                "遠いチャンクは送らないこと");
    }

    @Test
    void requestChunkRadiusRepliesWithPublisherUpdate() throws Exception {
        Fixture fixture = new Fixture();
        fixture.playUntilStartGame();
        fixture.transport.clear();
        BedrockVarInt.ByteWriter request = new BedrockVarInt.ByteWriter();
        BedrockVarInt.writeZigzag32(request, 4);
        request.writeByte(8);
        fixture.sendGamePacket(BedrockProtocol.PACKET_REQUEST_CHUNK_RADIUS, request.bytes(), 6);
        List<GamePacket> sent = fixture.transport.gamePackets();
        GamePacket radiusUpdated = sent.stream()
                .filter(p -> p.id() == BedrockProtocol.PACKET_CHUNK_RADIUS_UPDATED)
                .findFirst().orElse(null);
        assertNotNull(radiusUpdated, "ChunkRadiusUpdated が返ること");
        assertEquals(4,
                BedrockVarInt.readZigzag32(new BedrockVarInt.ByteReader(radiusUpdated.payload())),
                "要求された半径 4 を返すこと");
        GamePacket publisher = sent.stream()
                .filter(p -> p.id() == BedrockProtocol.PACKET_NETWORK_CHUNK_PUBLISHER_UPDATE)
                .findFirst().orElse(null);
        assertNotNull(publisher, "NetworkChunkPublisherUpdate が送られること");
        BedrockVarInt.ByteReader in = new BedrockVarInt.ByteReader(publisher.payload());
        assertEquals(8, BedrockVarInt.readZigzag32(in)); // スポーンのブロック x
        assertEquals(64, BedrockVarInt.readUnsignedVarInt(in)); // y
        assertEquals(8, BedrockVarInt.readZigzag32(in)); // z
        assertEquals(64, BedrockVarInt.readUnsignedVarInt(in)); // 半径（ブロック単位：4×16）
    }

    @Test
    void blockChangeSendsUpdateBlock() throws Exception {
        Fixture fixture = new Fixture();
        fixture.playUntilStartGame();
        byte[] spawnChunk = TestJavaChunk.build765(0, 0,
                BedrockChunkTranslatorTest.TERRAIN, BedrockChunkTranslatorTest.TWO_BIOMES);
        fixture.backend.listener.onChunk(spawnChunk);
        fixture.transport.clear();
        // 草ブロックを置く（y=64 は空気だった場所）。
        fixture.backend.listener.onBlockChange(8, 64, 8, 8);
        List<GamePacket> sent = fixture.transport.gamePackets();
        assertEquals(1, sent.size());
        assertEquals(BedrockProtocol.PACKET_UPDATE_BLOCK, sent.get(0).id());
        BedrockVarInt.ByteReader in = new BedrockVarInt.ByteReader(sent.get(0).payload());
        assertEquals(8, BedrockVarInt.readZigzag32(in));
        assertEquals(64, BedrockVarInt.readUnsignedVarInt(in));
        assertEquals(8, BedrockVarInt.readZigzag32(in));
        assertEquals(12183, BedrockVarInt.readUnsignedVarInt(in));
        assertEquals(BedrockProtocol.UPDATE_BLOCK_FLAG_NETWORK,
                BedrockVarInt.readUnsignedVarInt(in));
        assertEquals(0, BedrockVarInt.readUnsignedVarInt(in));

        // 水没ブロックは層 0＋層 1（水）の 2 回送る。
        fixture.transport.clear();
        fixture.backend.listener.onBlockChange(2, 63, 2, 39);
        sent = fixture.transport.gamePackets();
        assertEquals(2, sent.size(), "水没は 2 パケット");
        BedrockVarInt.ByteReader waterIn = new BedrockVarInt.ByteReader(sent.get(1).payload());
        BedrockVarInt.readZigzag32(waterIn);
        BedrockVarInt.readUnsignedVarInt(waterIn);
        BedrockVarInt.readZigzag32(waterIn);
        assertEquals(9304, BedrockVarInt.readUnsignedVarInt(waterIn));
        BedrockVarInt.readUnsignedVarInt(waterIn);
        assertEquals(1, BedrockVarInt.readUnsignedVarInt(waterIn));

        // 送っていないチャンクのブロック更新は送らない。
        fixture.transport.clear();
        fixture.backend.listener.onBlockChange(400, 64, 400, 8);
        assertTrue(fixture.transport.gamePackets().isEmpty(),
                "未送信チャンクの更新は送らないこと");
    }

    @Test
    void movementSendsNewChunksNearPlayer() throws Exception {
        Fixture fixture = new Fixture();
        fixture.playUntilStartGame();
        // プレイヤーが (8, 64, 8) から (200, 64, 8) へ移動する。
        byte[] newChunk = TestJavaChunk.build765(12, 0,
                BedrockChunkTranslatorTest.TERRAIN, BedrockChunkTranslatorTest.TWO_BIOMES);
        fixture.backend.listener.onChunk(newChunk);
        fixture.transport.clear();
        fixture.sendGamePacket(BedrockProtocol.PACKET_MOVE_PLAYER,
                BedrockPackets.encodeMovePlayerTeleport(7L, 200.5f, 64.0f, 8.5f, 0f, 90f), 6);
        List<GamePacket> sent = fixture.transport.gamePackets();
        assertTrue(sent.stream().anyMatch(
                        p -> p.id() == BedrockProtocol.PACKET_NETWORK_CHUNK_PUBLISHER_UPDATE),
                "移動したら NetworkChunkPublisherUpdate が送られること");
        GamePacket levelChunk = sent.stream()
                .filter(p -> p.id() == BedrockProtocol.PACKET_LEVEL_CHUNK)
                .findFirst().orElse(null);
        assertNotNull(levelChunk, "新しい位置のチャンクが送られること");
        BedrockVarInt.ByteReader in = new BedrockVarInt.ByteReader(levelChunk.payload());
        assertEquals(12, BedrockVarInt.readZigzag32(in));
        assertEquals(0, BedrockVarInt.readZigzag32(in));
    }
}
