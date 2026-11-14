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
 * Bedrock → 内部 769（Java 1.21.4）サーバーのテスト。
 * 偽 Java サーバー（769 役）とのログイン〜プレイの流れを確認する。
 * 769 は Login Success に strictErrorHandling が無く、Join Game／Respawn の
 * SpawnInfo の末尾に seaLevel が付く（768 と同形）。Client Information には
 * 末尾の particleStatus が付く（768 と同じ）。
 */
class JavaBackendConnection769Test {

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
            }, "fake-java-server-reader-769");
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
        final CountDownLatch playerInfoAdd = new CountDownLatch(1);
        final CountDownLatch playerInfoGamemode = new CountDownLatch(1);
        final int[] entityId = {-1};
        final List<int[]> dimensionInfos = new ArrayList<>();
        final List<String> disconnectReasons = new ArrayList<>();
        final List<String> addedPlayerNames = new ArrayList<>();
        final List<Integer> addedPlayerGamemodes = new ArrayList<>();
        final List<Integer> gamemodeChanges = new ArrayList<>();

        @Override
        public void onPlayerInfoAdd(UUID uuid, String name, int gamemode) {
            addedPlayerNames.add(name);
            addedPlayerGamemodes.add(gamemode);
            playerInfoAdd.countDown();
        }

        @Override
        public void onPlayerInfoGamemode(UUID uuid, int gamemode) {
            gamemodeChanges.add(gamemode);
            playerInfoGamemode.countDown();
        }

        @Override
        public void onJoined(int javaEntityId) {
            entityId[0] = javaEntityId;
            joined.countDown();
        }

        @Override
        public void onSpawnPosition(double x, double y, double z) {
            // 769テストではスポーン座標を使わない。
        }

        @Override
        public void onChat(String sender, String message) {
            // 769テストではチャットを使わない。
        }

        @Override
        public void onTeleport(double x, double y, double z, float yaw, float pitch) {
            // 769テストではテレポートを使わない。
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
        }, "fake-java-server-accept-769");
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

    /** 769 の Login Success ペイロード（UUID・名前・空の属性。strictErrorHandling は無い）。 */
    private static byte[] loginSuccess769(String name, UUID uuid) {
        PacketIO.Writer writer = new PacketIO.Writer();
        writer.writeString(name);
        writer.writeUuid(uuid);
        writer.writeVarInt(0); // properties（空）
        return writer.bytes();
    }

    /** 769 の Join Game ペイロード（768 と同形。次元は番号）。 */
    private static byte[] joinGame769(int entityId, int dimensionId) {
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
        writer.writeVarInt(63); // seaLevel（768 と同じ）
        writer.writeBoolean(true); // enforcesSecureChat
        return writer.bytes();
    }

    /** 769 の Respawn ペイロード（SpawnInfo 768 形式＋copyMetadata）。 */
    private static byte[] respawn769(int dimensionId, String worldName) {
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
        writer.writeVarInt(63); // seaLevel（768 と同じ）
        writer.writeByte(0); // copyMetadata
        return writer.bytes();
    }

    /** dimension_type の Registry Data（766／767／768／769 のエントリ配列形式）。 */
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
     * 769（1.21.4）の設定フェーズ：Select Known Packs と Ping に答える。
     * 設定フェーズの番号は 768 と同じ（Select Known Packs 0x0E、Ping 0x05、
     * Finish 0x03、Join Game 0x2C）。
     */
    @Test
    void selectKnownPacksAndPingInConfig769() throws Exception {
        try (ServerSocket serverSocket = new ServerSocket(0, 1,
                java.net.InetAddress.getByName("127.0.0.1"))) {
            int port = serverSocket.getLocalPort();
            AtomicReference<FakeJavaServer> holder = new AtomicReference<>();
            AtomicReference<Exception> error = new AtomicReference<>();
            acceptInBackground(serverSocket, holder, error);

            ListenerEvents events = new ListenerEvents();
            UUID uuid = UUID.fromString("11111111-2222-3333-4444-555555555555");
            JavaBackendConnection backend = new JavaBackendConnection(
                    "127.0.0.1", port, 769, ".Steve", uuid, events);
            backend.connect();
            try {
                FakeJavaServer server = waitForServer(holder, error);
                try (FakeJavaServer fake = server) {
                    assertNotNull(fake.waitForId(0x00, 5_000), "ハンドシェイクが届きませんでした。");

                    // Login Success（0x02、strictErrorHandling は無い）
                    // → Login Acknowledged（0x03）と Client Information（0x00）。
                    fake.send(new MinecraftPacket(0x02, loginSuccess769(".Steve", uuid)));
                    assertNotNull(fake.waitForId(0x03, 5_000),
                            "Login Acknowledged が届きませんでした。");
                    MinecraftPacket clientInfo = fake.waitForId(0x00, 5_000);
                    assertNotNull(clientInfo, "Client Information が届きませんでした。");
                    // 769の Client Information は9フィールド（末尾の particleStatus=0 付き）。
                    // 8フィールドのまま送ると実バニラ1.21.4が読み取り途中で切断する。
                    assertEquals(14, clientInfo.payload().length);
                    assertEquals(0, clientInfo.payload()[clientInfo.payload().length - 1]);

                    // Select Known Packs（0x0e）→ 空の一覧（VarInt 0）の応答（0x07）。
                    PacketIO.Writer knownPacks = new PacketIO.Writer();
                    knownPacks.writeVarInt(1);
                    knownPacks.writeString("minecraft");
                    knownPacks.writeString("vanilla");
                    knownPacks.writeString("1.21.4");
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

                    // Join Game（0x2c）。seaLevel 付きでも参加できること。
                    fake.send(new MinecraftPacket(0x2c, joinGame769(42, 0)));
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
     * 769 の Respawn は次元を番号で送る（文字列ではない）。
     * Registry Data で覚えた dimension_type の 1 番（ネザー）へ移ると、
     * 次元情報が minY 0・高さ 128 に変わる。
     */
    @Test
    void respawn769UsesDimensionNumber() throws Exception {
        try (ServerSocket serverSocket = new ServerSocket(0, 1,
                java.net.InetAddress.getByName("127.0.0.1"))) {
            int port = serverSocket.getLocalPort();
            AtomicReference<FakeJavaServer> holder = new AtomicReference<>();
            AtomicReference<Exception> error = new AtomicReference<>();
            acceptInBackground(serverSocket, holder, error);

            ListenerEvents events = new ListenerEvents();
            UUID uuid = UUID.fromString("11111111-2222-3333-4444-555555555555");
            JavaBackendConnection backend = new JavaBackendConnection(
                    "127.0.0.1", port, 769, ".Steve", uuid, events);
            backend.connect();
            try {
                FakeJavaServer server = waitForServer(holder, error);
                try (FakeJavaServer fake = server) {
                    assertNotNull(fake.waitForId(0x00, 5_000), "ハンドシェイクが届きませんでした。");
                    fake.send(new MinecraftPacket(0x02, loginSuccess769(".Steve", uuid)));
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
                    fake.send(new MinecraftPacket(0x2c, joinGame769(99, 0)));
                    assertTrue(events.joined.await(5, TimeUnit.SECONDS),
                            "Join Game が処理されませんでした。");
                    assertEquals(99, events.entityId[0]);
                    assertTrue(events.dimensionInfos.stream()
                                    .anyMatch(d -> d[0] == -64 && d[1] == 384),
                            "オーバーワールドの次元情報が届くこと: " + events.dimensionInfos);

                    // Respawn（0x4c）でネザー（次元 1）へ。
                    fake.send(new MinecraftPacket(0x4c,
                            respawn769(1, "minecraft:the_nether")));
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
     * 769 の Update Attributes は属性キーが番号、modifier の UUID が文字列。
     * 769 の generic.movement_speed は 21 番（768 と同じ番号。属性は768⇔769で同一）。
     */
    @Test
    void updateAttributes769UsesNumericKeysAndStringUuid() {
        PacketIO.Writer writer = new PacketIO.Writer();
        writer.writeVarInt(7); // entityId
        writer.writeVarInt(1); // count
        writer.writeVarInt(21); // generic.movement_speed（769）
        writer.writeDouble(0.1);
        writer.writeVarInt(1); // modifierCount
        writer.writeString("123e4567-e89b-12d3-a456-426614174000"); // modifier UUID（文字列）
        writer.writeDouble(0.0);
        writer.writeByte(0);
        BedrockEntityTranslator.UpdateAttributes attrs =
                BedrockEntityTranslator.parseUpdateAttributes(writer.bytes(), 769);
        assertEquals(7, attrs.entityId());
        assertEquals(0.1, attrs.movementSpeed());
    }

    /**
     * 769 の Update Attributes で名前の変わった属性（generic.burning_time →
     * burning_time）は 769 の名前表で読めること。burning_time は 769 で 7 番。
     */
    @Test
    void updateAttributes769ReadsRenamedAttributeName() {
        PacketIO.Writer writer = new PacketIO.Writer();
        writer.writeVarInt(7); // entityId
        writer.writeVarInt(1); // count
        writer.writeVarInt(7); // burning_time（769）
        writer.writeDouble(5.0);
        writer.writeVarInt(0); // modifierCount
        BedrockEntityTranslator.UpdateAttributes attrs =
                BedrockEntityTranslator.parseUpdateAttributes(writer.bytes(), 769);
        assertEquals(7, attrs.entityId());
        // burning_time は maxHealth／movementSpeed ではないので null のまま。
        // ここでは「読めないキーで例外にならない」ことだけを確認する。
    }

    /**
     * 769 の Player Info Update は update_hat（0x40）と update_list_order（0x80）を
     * 含む。同じパケット内の add player（0x01）の名前・ゲームモードが正しく読め、
     * その後の Player Info Update（0x04 ゲームモード変更）もずれることなく
     * 読めること。
     */
    @Test
    void playerInfo769ReadsHatAndListOrderBits() throws Exception {
        try (ServerSocket serverSocket = new ServerSocket(0, 1,
                java.net.InetAddress.getByName("127.0.0.1"))) {
            int port = serverSocket.getLocalPort();
            AtomicReference<FakeJavaServer> holder = new AtomicReference<>();
            AtomicReference<Exception> error = new AtomicReference<>();
            acceptInBackground(serverSocket, holder, error);

            ListenerEvents events = new ListenerEvents();
            UUID uuid = UUID.fromString("11111111-2222-3333-4444-555555555555");
            UUID other = UUID.fromString("22222222-3333-4444-5555-666666666666");
            JavaBackendConnection backend = new JavaBackendConnection(
                    "127.0.0.1", port, 769, ".Steve", uuid, events);
            backend.connect();
            try {
                FakeJavaServer server = waitForServer(holder, error);
                try (FakeJavaServer fake = server) {
                    assertNotNull(fake.waitForId(0x00, 5_000), "ハンドシェイクが届きませんでした。");
                    fake.send(new MinecraftPacket(0x02, loginSuccess769(".Steve", uuid)));
                    assertNotNull(fake.waitForId(0x03, 5_000),
                            "Login Acknowledged が届きませんでした。");
                    assertNotNull(fake.waitForId(0x00, 5_000),
                            "Client Information が届きませんでした。");
                    fake.send(new MinecraftPacket(0x03, new byte[0]));
                    assertNotNull(fake.waitForId(0x03, 5_000),
                            "Finish Configuration の承認が届きませんでした。");
                    fake.send(new MinecraftPacket(0x2c, joinGame769(42, 0)));
                    assertTrue(events.joined.await(5, TimeUnit.SECONDS),
                            "Join Game が処理されませんでした。");

                    // Player Info Update（0x40）: add player + update_hat + update_list_order。
                    PacketIO.Writer info = new PacketIO.Writer();
                    info.writeVarInt(0x01 | 0x40 | 0x80);
                    info.writeVarInt(1); // add player: 1人
                    info.writeUuid(other);
                    info.writeString("Alex");
                    info.writeVarInt(0); // properties（空）
                    info.writeVarInt(1); // gamemode: クリエイティブ
                    info.writeVarInt(50); // ping
                    info.writeBoolean(false); // display name なし
                    info.writeVarInt(1); // update_hat: 1人
                    info.writeUuid(other);
                    info.writeBoolean(true); // showHat
                    info.writeVarInt(1); // update_list_order: 1人
                    info.writeUuid(other);
                    info.writeVarInt(5); // listPriority
                    fake.send(new MinecraftPacket(0x40, info.bytes()));
                    assertTrue(events.playerInfoAdd.await(5, TimeUnit.SECONDS),
                            "Player Info の add player が処理されませんでした。");
                    assertEquals(List.of("Alex"), events.addedPlayerNames);
                    assertEquals(List.of(1), events.addedPlayerGamemodes);

                    // 後続の Player Info Update（0x04 ゲームモード変更）も読めること。
                    PacketIO.Writer gamemode = new PacketIO.Writer();
                    gamemode.writeVarInt(0x04);
                    gamemode.writeVarInt(1);
                    gamemode.writeUuid(other);
                    gamemode.writeVarInt(0); // サバイバル
                    fake.send(new MinecraftPacket(0x40, gamemode.bytes()));
                    assertTrue(events.playerInfoGamemode.await(5, TimeUnit.SECONDS),
                            "Player Info の gamemode 変更が処理されませんでした。");
                    assertEquals(List.of(0), events.gamemodeChanges);
                }
            } finally {
                backend.close();
            }
        }
    }

    /** 769 のスロット（bowl 830）は 765 の bowl（845）に正規化される。 */
    @Test
    void slot769NormalizesBowlTo765() {
        PacketIO.Writer writer = new PacketIO.Writer();
        writer.writeBoolean(true); // present
        writer.writeVarInt(830); // bowl（769）
        writer.writeVarInt(1); // count
        writer.writeVarInt(0); // added
        writer.writeVarInt(0); // removed
        BedrockEntityTranslator.JavaSlot slot =
                BedrockEntityTranslator.readSlot(new PacketIO.Reader(writer.bytes()), 769);
        assertTrue(slot.present());
        assertEquals(845, slot.item765());
        assertEquals(1, slot.count());
    }

    /** 769 の新アイテム resin_brick（1167）は 765 には無いので石（1）に落とす。 */
    @Test
    void slot769NormalizesResinBrickToStone() {
        PacketIO.Writer writer = new PacketIO.Writer();
        writer.writeBoolean(true); // present
        writer.writeVarInt(1167); // resin_brick（769）
        writer.writeVarInt(1); // count
        writer.writeVarInt(0); // added
        writer.writeVarInt(0); // removed
        BedrockEntityTranslator.JavaSlot slot =
                BedrockEntityTranslator.readSlot(new PacketIO.Reader(writer.bytes()), 769);
        assertTrue(slot.present());
        assertEquals(1, slot.item765());
    }

    /** 769 のブロック状態は 765 に正規化される（石 1→1、草ブロック 8→8）。 */
    @Test
    void blockState769NormalizesTo765() {
        assertEquals(1, BedrockChunkTranslator.normalizeBlockState(1, 769));
        assertEquals(8, BedrockChunkTranslator.normalizeBlockState(8, 769));
    }

    /** 769 のブタ（94）は 765 のブタ（73）に正規化される。 */
    @Test
    void entityType769NormalizesPigTo765() {
        assertEquals(73, BedrockEntityTranslator.normalizeEntityType(94, 769));
    }

    /** 769 のボートは木の種類ごとに分かれた（oak_boat 84 → 765 の boat 9）。 */
    @Test
    void entityType769NormalizesOakBoatTo765() {
        assertEquals(9, BedrockEntityTranslator.normalizeEntityType(84, 769));
    }

    /** 769 のクリイキング（29）は 765 には無いのでブタ（73）に落とす。 */
    @Test
    void entityType769NormalizesCreakingToPig() {
        assertEquals(73, BedrockEntityTranslator.normalizeEntityType(29, 769));
    }
}
