package io.detergentmc.bedrock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.detergentmc.protocol.MinecraftPacket;
import io.detergentmc.protocol.Nbt;
import io.detergentmc.protocol.PacketIO;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

/**
 * Bedrock → 内部 767（Java 1.21／1.21.1）サーバーのテスト。
 * 偽 Java サーバー（767 役）とのログイン〜プレイの流れを確認する。
 */
class JavaBackendConnection767Test {

    /** 偽 Java サーバー（サーバー役）。送られてきたパケットを記録する。 */
    private static final class FakeJavaServer implements AutoCloseable {
        final ServerSocket serverSocket;
        final Socket socket;
        final InputStream in;
        final OutputStream out;
        final BlockingQueue<MinecraftPacket> received = new LinkedBlockingQueue<>();
        final MinecraftPacket.Decoder decoder = new MinecraftPacket.Decoder();
        volatile boolean closed;

        FakeJavaServer(ServerSocket serverSocket) throws IOException {
            this.serverSocket = serverSocket;
            socket = serverSocket.accept();
            socket.setSoTimeout(10_000);
            in = socket.getInputStream();
            out = socket.getOutputStream();
            Thread reader = new Thread(() -> {
                try {
                    byte[] buffer = new byte[8192];
                    int read;
                    while ((read = in.read(buffer)) >= 0) {
                        received.addAll(decoder.feed(buffer, 0, read));
                    }
                } catch (IOException e) {
                    closed = true;
                }
            }, "fake-java-server-reader-767");
            reader.setDaemon(true);
            reader.start();
        }

        synchronized void send(MinecraftPacket packet) throws IOException {
            out.write(packet.encode());
            out.flush();
        }

        MinecraftPacket waitForId(int id, long timeoutMillis) throws InterruptedException {
            long deadline = System.currentTimeMillis() + timeoutMillis;
            while (true) {
                long remaining = deadline - System.currentTimeMillis();
                if (remaining <= 0) {
                    return null;
                }
                MinecraftPacket packet = received.poll(remaining, TimeUnit.MILLISECONDS);
                if (packet == null) {
                    return null;
                }
                if (packet.id() == id) {
                    return packet;
                }
            }
        }

        @Override
        public void close() throws IOException {
            socket.close();
            serverSocket.close();
        }
    }

    private static final class ListenerEvents implements JavaBackendConnection.Listener {
        final CountDownLatch joined = new CountDownLatch(1);
        final CountDownLatch disconnected = new CountDownLatch(1);
        final CountDownLatch dimensionInfo = new CountDownLatch(1);
        final int[] entityId = {-1};
        final List<int[]> dimensionInfos = new ArrayList<>();
        final List<String> disconnectReasons = new ArrayList<>();

        @Override
        public void onJoined(int javaEntityId) {
            entityId[0] = javaEntityId;
            joined.countDown();
        }

        @Override
        public void onSpawnPosition(double x, double y, double z) {
            // 767テストではスポーン座標を使わない。
        }

        @Override
        public void onChat(String sender, String message) {
            // 767テストではチャットを使わない。
        }

        @Override
        public void onTeleport(double x, double y, double z, float yaw, float pitch) {
            // 767テストではテレポートを使わない。
        }

        @Override
        public void onDimensionInfo(int minY, int height, List<String> biomeNames) {
            dimensionInfos.add(new int[]{minY, height});
            dimensionInfo.countDown();
        }

        @Override
        public void onDisconnected(String reason) {
            disconnectReasons.add(reason);
            disconnected.countDown();
        }
    }

    /** 偽サーバーを別スレッドで受け入れる。 */
    private static FakeJavaServer acceptInBackground(ServerSocket serverSocket,
            AtomicReference<FakeJavaServer> holder, AtomicReference<Exception> error)
            throws InterruptedException {
        Thread thread = new Thread(() -> {
            try {
                holder.set(new FakeJavaServer(serverSocket));
            } catch (IOException e) {
                error.set(e);
            }
        }, "fake-java-server-accept-767");
        thread.setDaemon(true);
        thread.start();
        return null;
    }

    private static FakeJavaServer waitForServer(
            AtomicReference<FakeJavaServer> holder, AtomicReference<Exception> error)
            throws Exception {
        FakeJavaServer server = null;
        for (int i = 0; i < 100 && server == null; i++) {
            Thread.sleep(50);
            server = holder.get();
        }
        assertNotNull(server, "偽サーバーが受け入れませんでした: " + error.get());
        return server;
    }

    /** 767 の Login Success ペイロード（UUID・名前・空の属性・strictErrorHandling）。 */
    private static byte[] loginSuccess767(String name, UUID uuid) {
        PacketIO.Writer writer = new PacketIO.Writer();
        writer.writeString(name);
        writer.writeUuid(uuid);
        writer.writeVarInt(0); // properties（空）
        writer.writeBoolean(false); // strictErrorHandling
        return writer.bytes();
    }

    /** 767 の Join Game ペイロード（766 と同じ形式。次元は番号）。 */
    private static byte[] joinGame767(int entityId, int dimensionId) {
        PacketIO.Writer writer = new PacketIO.Writer();
        writer.writeInt(entityId);
        writer.writeBoolean(false); // hardcore
        PacketIO.writeStringList(writer, List.of("minecraft:overworld"));
        writer.writeVarInt(20); // maxPlayers
        writer.writeVarInt(10); // viewDistance
        writer.writeVarInt(10); // simulationDistance
        writer.writeBoolean(false); // reducedDebug
        writer.writeBoolean(true); // enableRespawnScreen
        writer.writeBoolean(false); // doLimitedCrafting
        writer.writeVarInt(dimensionId);
        writer.writeString("minecraft:overworld"); // worldName
        writer.writeLong(123L); // hashedSeed
        writer.writeByte(0); // gameMode
        writer.writeByte(-1); // previousGameMode
        writer.writeBoolean(false); // isDebug
        writer.writeBoolean(false); // isFlat
        writer.writeBoolean(false); // hasDeath
        writer.writeVarInt(0); // portalCooldown
        writer.writeBoolean(true); // enforcesSecureChat
        return writer.bytes();
    }

    /** 767 の Respawn ペイロード（SpawnInfo 766 形式＋copyMetadata）。 */
    private static byte[] respawn767(int dimensionId, String worldName) {
        PacketIO.Writer writer = new PacketIO.Writer();
        writer.writeVarInt(dimensionId);
        writer.writeString(worldName);
        writer.writeLong(0L);
        writer.writeByte(0);
        writer.writeByte(-1);
        writer.writeBoolean(false);
        writer.writeBoolean(false);
        writer.writeBoolean(false);
        writer.writeVarInt(0);
        writer.writeByte(0); // copyMetadata
        return writer.bytes();
    }

    /** dimension_type の Registry Data（766／767 のエントリ配列形式）。 */
    private static byte[] dimensionTypeRegistryData() {
        PacketIO.Writer writer = new PacketIO.Writer();
        writer.writeString("minecraft:dimension_type");
        writer.writeVarInt(2);
        writer.writeString("minecraft:overworld");
        writer.writeBoolean(true);
        writer.writeBytes(Nbt.writeAnonymous(new Nbt.Tag.CompoundTag(Map.of(
                "min_y", new Nbt.Tag.IntTag(-64),
                "height", new Nbt.Tag.IntTag(384)))));
        writer.writeString("minecraft:the_nether");
        writer.writeBoolean(true);
        writer.writeBytes(Nbt.writeAnonymous(new Nbt.Tag.CompoundTag(Map.of(
                "min_y", new Nbt.Tag.IntTag(0),
                "height", new Nbt.Tag.IntTag(128)))));
        return writer.bytes();
    }

    /**
     * 767（1.21）の設定フェーズ：Select Known Packs と Ping に答える。
     * 766 と設定フェーズの番号は同じ（Select Known Packs 0x0E、Ping 0x05、
     * Finish 0x03、Join Game 0x2B）。
     */
    @Test
    void selectKnownPacksAndPingInConfig767() throws Exception {
        try (ServerSocket serverSocket = new ServerSocket(0, 1,
                java.net.InetAddress.getByName("127.0.0.1"))) {
            int port = serverSocket.getLocalPort();
            AtomicReference<FakeJavaServer> holder = new AtomicReference<>();
            AtomicReference<Exception> error = new AtomicReference<>();
            acceptInBackground(serverSocket, holder, error);

            ListenerEvents events = new ListenerEvents();
            UUID uuid = UUID.fromString("11111111-2222-3333-4444-555555555555");
            JavaBackendConnection backend = new JavaBackendConnection(
                    "127.0.0.1", port, 767, ".Steve", uuid, events);
            backend.connect();
            try {
                FakeJavaServer server = waitForServer(holder, error);
                try (FakeJavaServer fake = server) {
                    assertNotNull(fake.waitForId(0x00, 5_000), "ハンドシェイクが届きませんでした。");

                    // Login Success（0x02、末尾に strictErrorHandling）
                    // → Login Acknowledged（0x03）と Client Information（0x00）。
                    fake.send(new MinecraftPacket(0x02, loginSuccess767(".Steve", uuid)));
                    assertNotNull(fake.waitForId(0x03, 5_000),
                            "Login Acknowledged が届きませんでした。");
                    assertNotNull(fake.waitForId(0x00, 5_000),
                            "Client Information が届きませんでした。");

                    // Select Known Packs（0x0e）→ 空の一覧（VarInt 0）の応答（0x07）。
                    PacketIO.Writer knownPacks = new PacketIO.Writer();
                    knownPacks.writeVarInt(1);
                    knownPacks.writeString("minecraft");
                    knownPacks.writeString("vanilla");
                    knownPacks.writeString("1.21");
                    fake.send(new MinecraftPacket(0x0e, knownPacks.bytes()));
                    MinecraftPacket packsResponse = fake.waitForId(0x07, 5_000);
                    assertNotNull(packsResponse, "Select Known Packs の応答が届きませんでした。");
                    assertEquals(0,
                            new PacketIO.Reader(packsResponse.payload()).readVarInt(),
                            "知っているパックは無い（空の一覧）こと");

                    // Ping（0x05）→ Pong（0x05）に同じ ID が返ること。
                    PacketIO.Writer ping = new PacketIO.Writer();
                    ping.writeInt(12345);
                    fake.send(new MinecraftPacket(0x05, ping.bytes()));
                    MinecraftPacket pong = fake.waitForId(0x05, 5_000);
                    assertNotNull(pong, "Pong が届きませんでした。");
                    assertEquals(12345,
                            new PacketIO.Reader(pong.payload()).readInt());

                    // Finish Configuration（0x03）→ 承認（0x03）。
                    fake.send(new MinecraftPacket(0x03, new byte[0]));
                    assertNotNull(fake.waitForId(0x03, 5_000),
                            "Finish Configuration の承認が届きませんでした。");

                    // Join Game（0x2b）。
                    fake.send(new MinecraftPacket(0x2b, joinGame767(42, 0)));
                    assertTrue(events.joined.await(5, TimeUnit.SECONDS),
                            "Join Game が処理されませんでした。");
                    assertEquals(42, events.entityId[0]);
                }
            } finally {
                backend.close();
            }
        }
    }

    /**
     * 767 の Respawn は次元を番号で送る（文字列ではない）。
     * Registry Data で覚えた dimension_type の 1 番（ネザー）へ移ると、
     * 次元情報が minY 0・高さ 128 に変わる。
     */
    @Test
    void respawn767UsesDimensionNumber() throws Exception {
        try (ServerSocket serverSocket = new ServerSocket(0, 1,
                java.net.InetAddress.getByName("127.0.0.1"))) {
            int port = serverSocket.getLocalPort();
            AtomicReference<FakeJavaServer> holder = new AtomicReference<>();
            AtomicReference<Exception> error = new AtomicReference<>();
            acceptInBackground(serverSocket, holder, error);

            ListenerEvents events = new ListenerEvents();
            UUID uuid = UUID.fromString("11111111-2222-3333-4444-555555555555");
            JavaBackendConnection backend = new JavaBackendConnection(
                    "127.0.0.1", port, 767, ".Steve", uuid, events);
            backend.connect();
            try {
                FakeJavaServer server = waitForServer(holder, error);
                try (FakeJavaServer fake = server) {
                    assertNotNull(fake.waitForId(0x00, 5_000), "ハンドシェイクが届きませんでした。");
                    fake.send(new MinecraftPacket(0x02, loginSuccess767(".Steve", uuid)));
                    assertNotNull(fake.waitForId(0x03, 5_000),
                            "Login Acknowledged が届きませんでした。");
                    assertNotNull(fake.waitForId(0x00, 5_000),
                            "Client Information が届きませんでした。");

                    // Registry Data（0x07）で dimension_type を送る。
                    fake.send(new MinecraftPacket(0x07, dimensionTypeRegistryData()));
                    assertTrue(events.dimensionInfo.await(5, TimeUnit.SECONDS),
                            "Registry Data で dimensionInfo が届きませんでした。");

                    fake.send(new MinecraftPacket(0x03, new byte[0]));
                    assertNotNull(fake.waitForId(0x03, 5_000),
                            "Finish Configuration の承認が届きませんでした。");

                    // Join Game（次元 0＝オーバーワールド）。
                    fake.send(new MinecraftPacket(0x2b, joinGame767(99, 0)));
                    assertTrue(events.joined.await(5, TimeUnit.SECONDS),
                            "Join Game が処理されませんでした。");
                    assertEquals(99, events.entityId[0]);
                    assertTrue(events.dimensionInfos.stream()
                                    .anyMatch(d -> d[0] == -64 && d[1] == 384),
                            "オーバーワールドの次元情報が届くこと: " + events.dimensionInfos);

                    // Respawn（0x47）でネザー（次元 1）へ。
                    fake.send(new MinecraftPacket(0x47,
                            respawn767(1, "minecraft:the_nether")));
                    Thread.sleep(300);
                    assertTrue(events.dimensionInfos.stream()
                                    .anyMatch(d -> d[0] == 0 && d[1] == 128),
                            "ネザーの次元情報が届くこと: " + events.dimensionInfos);
                }
            } finally {
                backend.close();
            }
        }
    }

    /**
     * 767 の Update Attributes は属性キーが番号、modifier の UUID が文字列。
     * 767 の generic.movement_speed は 21 番。
     */
    @Test
    void updateAttributes767UsesNumericKeysAndStringUuid() {
        PacketIO.Writer writer = new PacketIO.Writer();
        writer.writeVarInt(7); // entityId
        writer.writeVarInt(1); // count
        writer.writeVarInt(21); // generic.movement_speed（767）
        writer.writeDouble(0.1);
        writer.writeVarInt(1); // modifierCount
        writer.writeString("123e4567-e89b-12d3-a456-426614174000"); // modifier UUID（文字列）
        writer.writeDouble(0.0);
        writer.writeByte(0);
        BedrockEntityTranslator.UpdateAttributes attrs =
                BedrockEntityTranslator.parseUpdateAttributes(writer.bytes(), 767);
        assertEquals(7, attrs.entityId());
        assertEquals(0.1, attrs.movementSpeed());
    }

    /**
     * 766 の Update Attributes は属性キーが番号、modifier の UUID が 16 バイト。
     * 766 の generic.movement_speed は 17 番。
     */
    @Test
    void updateAttributes766UsesNumericKeys() {
        PacketIO.Writer writer = new PacketIO.Writer();
        writer.writeVarInt(7); // entityId
        writer.writeVarInt(1); // count
        writer.writeVarInt(17); // generic.movement_speed（766）
        writer.writeDouble(0.2);
        writer.writeVarInt(1); // modifierCount
        writer.writeBytes(new byte[16]); // modifier UUID
        writer.writeDouble(0.0);
        writer.writeByte(0);
        BedrockEntityTranslator.UpdateAttributes attrs =
                BedrockEntityTranslator.parseUpdateAttributes(writer.bytes(), 766);
        assertEquals(7, attrs.entityId());
        assertEquals(0.2, attrs.movementSpeed());
    }

    /** 767 のスロット（bowl 799）は 765 の bowl（845）に正規化される。 */
    @Test
    void slot767NormalizesBowlTo765() {
        PacketIO.Writer writer = new PacketIO.Writer();
        writer.writeBoolean(true); // present
        writer.writeVarInt(799); // bowl（767）
        writer.writeVarInt(1); // count
        writer.writeVarInt(0); // added
        writer.writeVarInt(0); // removed
        BedrockEntityTranslator.JavaSlot slot =
                BedrockEntityTranslator.readSlot(new PacketIO.Reader(writer.bytes()), 767);
        assertTrue(slot.present());
        assertEquals(845, slot.item765());
        assertEquals(1, slot.count());
    }

    /** 767 のブロック状態は 765 に正規化される（石 1→1、草ブロック 8→8）。 */
    @Test
    void blockState767NormalizesTo765() {
        assertEquals(1, BedrockChunkTranslator.normalizeBlockState(1, 767));
        assertEquals(8, BedrockChunkTranslator.normalizeBlockState(8, 767));
    }

    /** 767 のブタ（77）は 765 のブタ（73）に正規化される。 */
    @Test
    void entityType767NormalizesPigTo765() {
        assertEquals(73, BedrockEntityTranslator.normalizeEntityType(77, 767));
    }
}
