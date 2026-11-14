package io.detergentmc.bedrock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.detergentmc.bedrock.BedrockBatch.GamePacket;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;

/**
 * Bedrock セッションのバージョン別テスト。30 の全対応プロトコルで
 * Login→リソースパック→Start Game まで進み、送られたパケットが
 * そのバージョンの形式で読めることを検証する。対象外プロトコルは切断する。
 */
class BedrockSessionVersionTest {

    /** 送られたデータグラムをためる偽 Transport。 */
    private static final class FakeTransport implements BedrockSession.Transport {
        final List<byte[]> sent = new CopyOnWriteArrayList<>();
        final RakNetCodec.SplitReassembler splits = new RakNetCodec.SplitReassembler();

        @Override
        public void send(byte[] datagram) {
            sent.add(datagram.clone());
        }

        @Override
        public void onSessionClosed(BedrockSession session) {
        }

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

        boolean hasGamePacket(int id) throws IOException {
            return gamePackets().stream().anyMatch(p -> p.id() == id);
        }
    }

    /** 偽 Java バックエンド。Listener を保持し、参加だけを記録する。 */
    private static final class FakeBackend implements JavaBackend {
        JavaBackendConnection.Listener listener;
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
        }

        @Override
        public void sendCommand(String command) {
        }

        @Override
        public void sendPosition(double x, double y, double z,
                float yaw, float pitch, boolean onGround) {
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

        void sendFrameSet(byte[] payload, int sequence) {
            session.handleFrameSet(RakNetCodec.buildFrameSet(sequence,
                    RakNetCodec.RELIABILITY_RELIABLE, sequence, payload));
        }

        void sendGamePacket(int id, byte[] payload, int sequence) {
            sendFrameSet(BedrockBatch.encodeOne(id, payload), sequence);
        }

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

        /** 指定プロトコルでログイン→リソースパック→Java 参加→completed まで進める。 */
        void playUntilStartGame(int protocol) {
            establishRakNet();
            sendGamePacket(BedrockProtocol.PACKET_LOGIN,
                    TestJwt.loginPayload(protocol, "Steve", "2535461234567890",
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

        GamePacket findGamePacket(int id) throws IOException {
            return transport.gamePackets().stream()
                    .filter(p -> p.id() == id).findFirst().orElse(null);
        }
    }

    @Test
    void allThirtyProtocolsReachStartGame() throws Exception {
        BedrockBlockPalette palette = BedrockBlockPalette.load();
        int[] protocols = {589, 594, 618, 622, 630, 649, 662, 671, 685, 686,
                712, 729, 748, 766, 776, 786, 800, 818, 819, 827,
                844, 859, 898, 924, 944, 975, 1001, 2168, 2169, 2193};
        for (int protocol : protocols) {
            BedrockVersion version = BedrockVersion.fromProtocol(protocol);
            Fixture fixture = new Fixture();
            fixture.playUntilStartGame(protocol);
            assertEquals(version, fixture.session.bedrockVersion(),
                    "セッションの Bedrock バージョン（" + protocol + "）");
            assertTrue(fixture.backend.connected, "Java バックエンドが開かれること");

            // ResourcePacksInfo はそのバージョンの形式で読めること。
            GamePacket infoPacket = fixture.findGamePacket(
                    BedrockProtocol.PACKET_RESOURCE_PACKS_INFO);
            assertNotNull(infoPacket, "ResourcePacksInfo が送られること（" + protocol + "）");
            BedrockPacketsVersionTest.parseResourcePacksInfo(infoPacket.payload(),
                    version);
            // ResourcePackStack も同様。
            GamePacket stackPacket = fixture.findGamePacket(
                    BedrockProtocol.PACKET_RESOURCE_PACK_STACK);
            assertNotNull(stackPacket, "ResourcePackStack が送られること（" + protocol + "）");
            BedrockPacketsVersionTest.parseResourcePackStack(stackPacket.payload(),
                    version);
            // StartGame はそのバージョンの形式で最後まで読め、game version は
            // クライアントの表示名になること。
            GamePacket startGame = fixture.findGamePacket(
                    BedrockProtocol.PACKET_START_GAME);
            assertNotNull(startGame, "StartGame が送られること（" + protocol + "）");
            BedrockPacketsVersionTest.ParsedStartGame parsed =
                    BedrockPacketsVersionTest.parseStartGame(startGame.payload(), version,
                            palette);
            assertEquals(version.displayName(), parsed.gameVersion(),
                    "StartGame の game version（" + protocol + "）");
        }
    }

    @Test
    void unknownProtocolIsDisconnected() throws Exception {
        int[] unknown = {0, 588, 631, 999, 2194, 3000};
        for (int protocol : unknown) {
            Fixture fixture = new Fixture();
            fixture.establishRakNet();
            fixture.sendGamePacket(BedrockProtocol.PACKET_LOGIN,
                    TestJwt.loginPayload(protocol, "Steve", "2535",
                            "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee"),
                    3);
            assertTrue(fixture.transport.hasGamePacket(BedrockProtocol.PACKET_DISCONNECT),
                    "対応外プロトコル " + protocol + " は Disconnect で切ること");
            assertTrue(fixture.session.isClosed(), "セッションは閉じること");
        }
    }

    @Test
    void netherNetProtocolIsDisconnectedWithExplanation() throws Exception {
        Fixture fixture = new Fixture();
        fixture.establishRakNet();
        fixture.sendGamePacket(BedrockProtocol.PACKET_LOGIN,
                TestJwt.loginPayload(BedrockVersion.NETHERNET_PROTOCOL, "Steve", "2535",
                        "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee"),
                3);
        assertTrue(fixture.transport.hasGamePacket(BedrockProtocol.PACKET_DISCONNECT),
                "26.60（2225）は Disconnect で切ること");
        assertTrue(fixture.session.isClosed());
        GamePacket disconnect = fixture.findGamePacket(BedrockProtocol.PACKET_DISCONNECT);
        assertNotNull(disconnect);
        BedrockVarInt.ByteReader reader =
                new BedrockVarInt.ByteReader(disconnect.payload());
        BedrockVarInt.readVarInt(reader); // reason
        reader.readBoolean(); // hide disconnect reason
        String message = BedrockVarInt.readString(reader);
        assertTrue(message.contains("NetherNet"),
                "切断メッセージに NetherNet の旨が入ること: " + message);
        assertTrue(message.contains("2225"), "切断メッセージにプロトコル番号が入ること");
    }
}
