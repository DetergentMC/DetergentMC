// Copyright (c) 2026 DetergentMC contributors.
// SPDX-License-Identifier: Apache-2.0

package io.detergentmc.bedrock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
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
 * Bedrock → 内部 776（Java 26.2）サーバーのテスト。
 * 偽 Java サーバー（776 役）とのログイン〜プレイの流れを確認する。
 * 776 は 775 とパケットIDが同じ（Join Game 0x31、Player Info Update 0x46、
 * Chat Message 0x09、Spawn Position 0x61）。ペイロードの違いは 4 件だけ
 * （Join Game の末尾 onlineMode、Login Success の末尾 sessionId、
 * Teams の並べ替え、SpectatorAction の OptionalInt）で、このうち
 * Bedrock 側で読むのは Join Game だけ（末尾の onlineMode bool を落として
 * から 768 形式に直す。落とさないと translateJoinGame768To767 が余分な
 * バイトで失敗し、Join Game が処理されない）。
 * Login Success の末尾 sessionId は Bedrock 側では読まないが、偽サーバーは
 * 本物と同じく付けて送る。
 * 変わるのは776で増えたレジストリの正規化と属性表のずれだけで、属性は
 * 新規 5 件の挿入で generic.movement_speed は 26番、camera_distance は
 * 11番にずれる。
 * エンティティは sulfur_cube（130）が増えただけでブタ・ガスト・ハッピーガストの
 * 番号は 775 と同じ。776で増えたのは music_disc_bounce（アイテム 1342）、
 * 硫黄・辰砂系のブロック状態などで、776の番号で正規化することを確認する。
 */
class JavaBackendConnection776Test {

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
            }, "fake-java-server-reader-776");
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
        final CountDownLatch spawnPosition = new CountDownLatch(1);
        final int[] entityId = {-1};
        final List<int[]> dimensionInfos = new ArrayList<>();
        final List<String> disconnectReasons = new ArrayList<>();
        final List<String> addedPlayerNames = new ArrayList<>();
        final List<Integer> addedPlayerGamemodes = new ArrayList<>();
        final List<Integer> gamemodeChanges = new ArrayList<>();
        final List<double[]> spawnPositions = new ArrayList<>();

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
            spawnPositions.add(new double[]{x, y, z});
            spawnPosition.countDown();
        }

        @Override
        public void onChat(String sender, String message) {
            // 776テストではチャットを使わない。
        }

        @Override
        public void onTeleport(double x, double y, double z, float yaw, float pitch) {
            // 776テストではテレポートを使わない。
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
        }, "fake-java-server-accept-776");
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

    /** 776 の Login Success ペイロード（UUID・名前・空の属性＋末尾の sessionId UUID）。 */
    private static byte[] loginSuccess776(String name, UUID uuid) {
        PacketIO.Writer writer = new PacketIO.Writer();
        writer.writeString(name);
        writer.writeUuid(uuid);
        writer.writeVarInt(0); // properties（空）
        writer.writeUuid(UUID.fromString("33333333-4444-5555-6666-777777777777")); // sessionId（776で追加）
        return writer.bytes();
    }

    /** 776 の Join Game ペイロード（775 と同形＋末尾の onlineMode bool。次元は番号、末尾に seaLevel）。 */
    private static byte[] joinGame776(int entityId, int dimensionId) {
        return joinGame776Online(entityId, dimensionId, false);
    }

    /** onlineMode を指定できる 776 の Join Game ペイロード。 */
    private static byte[] joinGame776Online(int entityId, int dimensionId, boolean onlineMode) {
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
        writer.writeVarInt(63); // seaLevel（775 と同じ）
        writer.writeBoolean(true); // enforcesSecureChat
        writer.writeBoolean(onlineMode); // onlineMode（776で追加）
        return writer.bytes();
    }

    /**
     * 776 の Player Info Update は update_hat（0x40）と update_list_order（0x80）を
     * 含む。番号は 776 でも 0x46（775 と同じ）。同じパケット内の add player
     * （0x01）の名前・ゲームモードが正しく読め、その後の Player Info Update
     * （0x04 ゲームモード変更）もずれることなく読めること。
     */
    @Test
    void playerInfo776ReadsHatAndListOrderBits() throws Exception {
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
                    "127.0.0.1", port, 776, ".Steve", uuid, events);
            backend.connect();
            try {
                FakeJavaServer server = waitForServer(holder, error);
                try (FakeJavaServer fake = server) {
                    assertNotNull(fake.waitForId(0x00, 5_000), "ハンドシェイクが届きませんでした。");
                    fake.send(new MinecraftPacket(0x02, loginSuccess776(".Steve", uuid)));
                    assertNotNull(fake.waitForId(0x03, 5_000),
                            "Login Acknowledged が届きませんでした。");
                    assertNotNull(fake.waitForId(0x00, 5_000),
                            "Client Information が届きませんでした。");
                    fake.send(new MinecraftPacket(0x03, new byte[0]));
                    assertNotNull(fake.waitForId(0x03, 5_000),
                            "Finish Configuration の承認が届きませんでした。");
                    // Join Game は776では 0x31（774 では 0x30）。
                    fake.send(new MinecraftPacket(0x31, joinGame776(42, 0)));
                    assertTrue(events.joined.await(5, TimeUnit.SECONDS),
                            "Join Game が処理されませんでした。");

                    // Player Info Update（0x46）: add player + update_hat + update_list_order。
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
                    fake.send(new MinecraftPacket(0x46, info.bytes()));
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
                    fake.send(new MinecraftPacket(0x46, gamemode.bytes()));
                    assertTrue(events.playerInfoGamemode.await(5, TimeUnit.SECONDS),
                            "Player Info の gamemode 変更が処理されませんでした。");
                    assertEquals(List.of(0), events.gamemodeChanges);
                }
            } finally {
                backend.close();
            }
        }
    }

    /**
     * 776の Chat Message は 0x09（774 では 0x08）。末尾には checksum u8 が付く。
     * Bedrock からのチャット送信（sendChat）が776では 0x09 に末尾 0x00 を
     * 付けないと、プロキシが末尾の acknowledged バイトを checksum として
     * 削って壊す（774と同じ）。
     */
    @Test
    void sendChat776AppendsChecksumByte() throws Exception {
        try (ServerSocket serverSocket = new ServerSocket(0, 1,
                java.net.InetAddress.getByName("127.0.0.1"))) {
            int port = serverSocket.getLocalPort();
            AtomicReference<FakeJavaServer> holder = new AtomicReference<>();
            AtomicReference<Exception> error = new AtomicReference<>();
            acceptInBackground(serverSocket, holder, error);

            ListenerEvents events = new ListenerEvents();
            UUID uuid = UUID.fromString("11111111-2222-3333-4444-555555555555");
            JavaBackendConnection backend = new JavaBackendConnection(
                    "127.0.0.1", port, 776, ".Steve", uuid, events);
            backend.connect();
            try {
                FakeJavaServer server = waitForServer(holder, error);
                try (FakeJavaServer fake = server) {
                    assertNotNull(fake.waitForId(0x00, 5_000), "ハンドシェイクが届きませんでした。");
                    fake.send(new MinecraftPacket(0x02, loginSuccess776(".Steve", uuid)));
                    assertNotNull(fake.waitForId(0x03, 5_000),
                            "Login Acknowledged が届きませんでした。");
                    assertNotNull(fake.waitForId(0x00, 5_000),
                            "Client Information が届きませんでした。");
                    fake.send(new MinecraftPacket(0x03, new byte[0]));
                    assertNotNull(fake.waitForId(0x03, 5_000),
                            "Finish Configuration の承認が届きませんでした。");
                    fake.send(new MinecraftPacket(0x31, joinGame776(42, 0)));
                    assertTrue(events.joined.await(5, TimeUnit.SECONDS),
                            "Join Game が処理されませんでした。");

                    backend.sendChat("hello bedrock");
                    // Chat Message は776では 0x09。
                    MinecraftPacket chat = fake.waitForId(0x09, 5_000);
                    assertNotNull(chat, "Chat Message が届きませんでした。");
                    PacketIO.Reader reader = new PacketIO.Reader(chat.payload());
                    assertEquals("hello bedrock", reader.readString());
                    reader.readLong(); // timestamp
                    assertEquals(0L, reader.readLong()); // salt
                    assertEquals(false, reader.readBoolean()); // 署名なし
                    assertEquals(0, reader.readVarInt()); // offset
                    assertEquals((byte) 0, reader.readBytes(3)[0]); // acknowledged
                    assertEquals((byte) 0, reader.readByte(),
                            "776の Chat Message の末尾に checksum 0x00 が無かった。");
                    assertEquals(0, reader.remaining(),
                            "Chat Message の末尾に余分なバイトがあった。");
                }
            } finally {
                backend.close();
            }
        }
    }

    /**
     * 776 の Update Attributes は775と同じく数値キー・modifier UUID は文字列。
     * 属性の順序は775→776で新規 5 件の挿入でずれる：camera_distance は 11番、
     * generic.movement_speed は 26番。
     */
    @Test
    void updateAttributes776UsesNumericKeysAndStringUuid() {
        PacketIO.Writer writer = new PacketIO.Writer();
        writer.writeVarInt(7); // entityId
        writer.writeVarInt(1); // count
        writer.writeVarInt(26); // generic.movement_speed（776。775では 22）
        writer.writeDouble(0.1);
        writer.writeVarInt(1); // modifierCount
        writer.writeString("123e4567-e89b-12d3-a456-426614174000"); // modifier UUID（文字列）
        writer.writeDouble(0.0);
        writer.writeByte(0);
        BedrockEntityTranslator.UpdateAttributes attrs =
                BedrockEntityTranslator.parseUpdateAttributes(writer.bytes(), 776);
        assertEquals(7, attrs.entityId());
        assertEquals(0.1, attrs.movementSpeed());
    }

    /** 776 の camera_distance（11）は movement_speed でも max_health でもないため読まない。 */
    @Test
    void updateAttributes776SkipsCameraDistance() {
        PacketIO.Writer writer = new PacketIO.Writer();
        writer.writeVarInt(7); // entityId
        writer.writeVarInt(1); // count
        writer.writeVarInt(11); // camera_distance（776）
        writer.writeDouble(0.5);
        writer.writeVarInt(0); // modifierCount
        BedrockEntityTranslator.UpdateAttributes attrs =
                BedrockEntityTranslator.parseUpdateAttributes(writer.bytes(), 776);
        assertEquals(7, attrs.entityId());
        assertNull(attrs.maxHealth());
        assertNull(attrs.movementSpeed());
    }

    /** 776 の music_disc_lava_chicken（1347）は 765 には無いので石（1）に落とす。 */
    @Test
    void slot776NormalizesNewItemToStone() {
        PacketIO.Writer writer = new PacketIO.Writer();
        writer.writeBoolean(true); // present
        writer.writeVarInt(1347); // music_disc_lava_chicken（776。775では 1317）
        writer.writeVarInt(1); // count
        writer.writeVarInt(0); // added
        writer.writeVarInt(0); // removed
        BedrockEntityTranslator.JavaSlot slot =
                BedrockEntityTranslator.readSlot(new PacketIO.Reader(writer.bytes()), 776);
        assertTrue(slot.present());
        assertEquals(1, slot.item765());
    }

    /** 776 の金のタンポポ（257）は 765 には無いので石（1）に落とす（775で 230 に挿入されたアイテム。776では 257）。 */
    @Test
    void slot776NormalizesGoldenDandelionToStone() {
        PacketIO.Writer writer = new PacketIO.Writer();
        writer.writeBoolean(true); // present
        writer.writeVarInt(257); // golden_dandelion（775で新規）
        writer.writeVarInt(1); // count
        writer.writeVarInt(0); // added
        writer.writeVarInt(0); // removed
        BedrockEntityTranslator.JavaSlot slot =
                BedrockEntityTranslator.readSlot(new PacketIO.Reader(writer.bytes()), 776);
        assertTrue(slot.present());
        assertEquals(1, slot.item765());
    }

    /** 776 の oak_boat（864）は 765 の oak_boat（773）に正規化される。 */
    @Test
    void slot776NormalizesOakBoatTo765() {
        PacketIO.Writer writer = new PacketIO.Writer();
        writer.writeBoolean(true); // present
        writer.writeVarInt(891); // oak_boat（776。775では 864）
        writer.writeVarInt(1); // count
        writer.writeVarInt(0); // added
        writer.writeVarInt(0); // removed
        BedrockEntityTranslator.JavaSlot slot =
                BedrockEntityTranslator.readSlot(new PacketIO.Reader(writer.bytes()), 776);
        assertTrue(slot.present());
        assertEquals(773, slot.item765());
    }

    /** 776 のブロック状態は 765 に正規化される（石 1→1、trial_spawner 32150→26638、トランペットの音符ブロック 1381→石 1、硫黄 24687→石 1）。 */

    /** 776 のエフェクト 39（BreathOfTheNautilus）は 765 には無いので Speed（0）に落とす。 */
    @Test
    void effect776NormalizesBreathOfTheNautilusToSpeed() {
        assertEquals(0, BedrockEntityTranslator.normalizeEffectTo765(39, 776));
        assertEquals(5, BedrockEntityTranslator.normalizeEffectTo765(5, 776));
    }

    @Test
    void blockState776NormalizesTo765() {
        assertEquals(1, BedrockChunkTranslator.normalizeBlockState(1, 776));
        assertEquals(26638, BedrockChunkTranslator.normalizeBlockState(32150, 776));
        assertEquals(1, BedrockChunkTranslator.normalizeBlockState(1381, 776));
        // 776で新規の硫黄（24687）は 765 には無いので石（1）に落とす。
        assertEquals(1, BedrockChunkTranslator.normalizeBlockState(24687, 776));
    }

    /** 776 のブタ（100）は 765 のブタ（73）に正規化される。 */
    @Test
    void entityType776NormalizesPigTo765() {
        assertEquals(73, BedrockEntityTranslator.normalizeEntityType(100, 776));
    }

    /** 776 のガスト（57）は 765 のガスト（42）に正規化される。 */
    @Test
    void entityType776NormalizesGhastTo765() {
        assertEquals(42, BedrockEntityTranslator.normalizeEntityType(57, 776));
    }

    /**
     * 776 のハッピーガスト（58）は 765 には無いのでブタ（73）に落とす。
     * Bedrock の正規化表は合成表（776→775ではそのまま、775→765でブタ）のため、
     * Java プロキシの Spawn Entity（ガストに代替）とは違う代替になる。
     */
    @Test
    void entityType776NormalizesHappyGhastToPig() {
        assertEquals(73, BedrockEntityTranslator.normalizeEntityType(58, 776));
    }

    /** ハンドシェイク〜Join Game まで進める（2件の追加テストの共通手順）。 */
    private static void loginToPlay(FakeJavaServer fake, ListenerEvents events, UUID uuid)
            throws Exception {
        assertNotNull(fake.waitForId(0x00, 5_000), "ハンドシェイクが届きませんでした。");
        fake.send(new MinecraftPacket(0x02, loginSuccess776(".Steve", uuid)));
        assertNotNull(fake.waitForId(0x03, 5_000), "Login Acknowledged が届きませんでした。");
        assertNotNull(fake.waitForId(0x00, 5_000), "Client Information が届きませんでした。");
        fake.send(new MinecraftPacket(0x03, new byte[0]));
        assertNotNull(fake.waitForId(0x03, 5_000), "Finish Configuration の承認が届きませんでした。");
        fake.send(new MinecraftPacket(0x31, joinGame776(42, 0)));
        assertTrue(events.joined.await(5, TimeUnit.SECONDS), "Join Game が処理されませんでした。");
    }

    /** 776 の Spawn Position ペイロード（次元名＋位置＋yaw＋pitch）。 */
    private static byte[] spawnPosition776(int x, int y, int z) {
        PacketIO.Writer writer = new PacketIO.Writer();
        writer.writeString("minecraft:overworld");
        long packed = ((x & 0x3FFFFFFL) << 38) | ((z & 0x3FFFFFFL) << 12) | (y & 0xFFFL);
        writer.writePositionRaw(packed);
        writer.writeFloat(0.0f); // yaw
        writer.writeFloat(0.0f); // pitch
        return writer.bytes();
    }

    /**
     * 776 の Spawn Position は RespawnData（次元名の文字列＋位置＋yaw＋pitch）。
     * 位置の先頭に次元名が付くため、そのまま位置として読むと座標が化ける。
     * 774と同一形式のため、スポーン（88, 70, -104）が
     * (88.5, 70, -103.5) として届くこと。
     */
    @Test
    void spawnPosition776ReadsRespawnData() throws Exception {
        try (ServerSocket serverSocket = new ServerSocket(0, 1,
                java.net.InetAddress.getByName("127.0.0.1"))) {
            int port = serverSocket.getLocalPort();
            AtomicReference<FakeJavaServer> holder = new AtomicReference<>();
            AtomicReference<Exception> error = new AtomicReference<>();
            acceptInBackground(serverSocket, holder, error);

            ListenerEvents events = new ListenerEvents();
            UUID uuid = UUID.fromString("11111111-2222-3333-4444-555555555555");
            JavaBackendConnection backend = new JavaBackendConnection(
                    "127.0.0.1", port, 776, ".Steve", uuid, events);
            backend.connect();
            try {
                FakeJavaServer server = waitForServer(holder, error);
                try (FakeJavaServer fake = server) {
                    loginToPlay(fake, events, uuid);
                    // Spawn Position は776では 0x61（774 では 0x5F）。
                    fake.send(new MinecraftPacket(0x61, spawnPosition776(88, 70, -104)));
                    assertTrue(events.spawnPosition.await(5, TimeUnit.SECONDS),
                            "Spawn Position が処理されませんでした。");
                    assertEquals(1, events.spawnPositions.size());
                    assertEquals(88.5, events.spawnPositions.get(0)[0], 0.001);
                    assertEquals(70.0, events.spawnPositions.get(0)[1], 0.001);
                    assertEquals(-103.5, events.spawnPositions.get(0)[2], 0.001);
                }
            } finally {
                backend.close();
            }
        }
    }

    /**
     * Chunk Batch Finished（776でも 0x0B）を受けたら Chunk Batch Received
     * （776でも 0x0B、f32 の chunksPerTick）を返すこと。774と同一のため、
     * Ack を返さないと最初のバッチ（9チャンク）のあと
     * チャンクが送られてこない。
     */
    @Test
    void chunkBatchFinished776SendsReceivedAck() throws Exception {
        try (ServerSocket serverSocket = new ServerSocket(0, 1,
                java.net.InetAddress.getByName("127.0.0.1"))) {
            int port = serverSocket.getLocalPort();
            AtomicReference<FakeJavaServer> holder = new AtomicReference<>();
            AtomicReference<Exception> error = new AtomicReference<>();
            acceptInBackground(serverSocket, holder, error);

            ListenerEvents events = new ListenerEvents();
            UUID uuid = UUID.fromString("11111111-2222-3333-4444-555555555555");
            JavaBackendConnection backend = new JavaBackendConnection(
                    "127.0.0.1", port, 776, ".Steve", uuid, events);
            backend.connect();
            try {
                FakeJavaServer server = waitForServer(holder, error);
                try (FakeJavaServer fake = server) {
                    loginToPlay(fake, events, uuid);
                    PacketIO.Writer finished = new PacketIO.Writer();
                    finished.writeVarInt(9); // batchSize
                    fake.send(new MinecraftPacket(0x0B, finished.bytes()));
                    MinecraftPacket ack = fake.waitForId(0x0B, 5_000);
                    assertNotNull(ack, "Chunk Batch Received が届きませんでした。");
                    assertEquals(2.0f,
                            new PacketIO.Reader(ack.payload()).readFloat(), 0.001f);
                }
            } finally {
                backend.close();
            }
        }
    }

    /** 776 の music_disc_bounce（1342）は 765 には無いので石（1）に落とす（776で新規）。 */
    @Test
    void slot776NormalizesNewDiscBounceToStone() {
        PacketIO.Writer writer = new PacketIO.Writer();
        writer.writeBoolean(true); // present
        writer.writeVarInt(1342); // music_disc_bounce（776で新規）
        writer.writeVarInt(1); // count
        writer.writeVarInt(0); // added
        writer.writeVarInt(0); // removed
        BedrockEntityTranslator.JavaSlot slot =
                BedrockEntityTranslator.readSlot(new PacketIO.Reader(writer.bytes()), 776);
        assertTrue(slot.present());
        assertEquals(1, slot.item765());
    }

    /**
     * 776 の sulfur_cube（130）は 765 には無いのでブタ（73）に落とす。
     * 776→775 の代替はブタ（100）で、775→765 でもブタ（73）になる。
     */
    @Test
    void entityType776NormalizesSulfurCubeToPig() {
        assertEquals(73, BedrockEntityTranslator.normalizeEntityType(130, 776));
    }

    /**
     * Join Game の末尾 onlineMode が true でも処理されること。
     * onlineMode の値を読まずに末尾1バイトを落としているため、true／false
     * どちらでも Join Game が処理されなければならない（落とし忘れると
     * translateJoinGame768To767 が余分なバイトで失敗する）。
     */
    @Test
    void joinGame776WithOnlineModeTrueStillProcesses() throws Exception {
        try (ServerSocket serverSocket = new ServerSocket(0, 1,
                java.net.InetAddress.getByName("127.0.0.1"))) {
            int port = serverSocket.getLocalPort();
            AtomicReference<FakeJavaServer> holder = new AtomicReference<>();
            AtomicReference<Exception> error = new AtomicReference<>();
            acceptInBackground(serverSocket, holder, error);

            ListenerEvents events = new ListenerEvents();
            UUID uuid = UUID.fromString("11111111-2222-3333-4444-555555555555");
            JavaBackendConnection backend = new JavaBackendConnection(
                    "127.0.0.1", port, 776, ".Steve", uuid, events);
            backend.connect();
            try {
                FakeJavaServer server = waitForServer(holder, error);
                try (FakeJavaServer fake = server) {
                    assertNotNull(fake.waitForId(0x00, 5_000), "ハンドシェイクが届きませんでした。");
                    fake.send(new MinecraftPacket(0x02, loginSuccess776(".Steve", uuid)));
                    assertNotNull(fake.waitForId(0x03, 5_000),
                            "Login Acknowledged が届きませんでした。");
                    assertNotNull(fake.waitForId(0x00, 5_000),
                            "Client Information が届きませんでした。");
                    fake.send(new MinecraftPacket(0x03, new byte[0]));
                    assertNotNull(fake.waitForId(0x03, 5_000),
                            "Finish Configuration の承認が届きませんでした。");
                    fake.send(new MinecraftPacket(0x31, joinGame776Online(42, 0, true)));
                    assertTrue(events.joined.await(5, TimeUnit.SECONDS),
                            "Join Game（onlineMode=true）が処理されませんでした。");
                    assertEquals(42, events.entityId[0]);
                }
            } finally {
                backend.close();
            }
        }
    }

    /** dimension_type の Registry Data（776 は 765+ の NBT 形式）。 */
    private static Nbt.Tag.CompoundTag dimensionEntry776(String name, int id, int minY, int height) {
        return new Nbt.Tag.CompoundTag(Map.of(
                "name", new Nbt.Tag.StringTag(name),
                "id", new Nbt.Tag.IntTag(id),
                "element", new Nbt.Tag.CompoundTag(Map.of(
                        "min_y", new Nbt.Tag.IntTag(minY),
                        "height", new Nbt.Tag.IntTag(height)))));
    }

    private static byte[] dimensionTypeRegistryData776() {
        Nbt.Tag.CompoundTag dimensionType = new Nbt.Tag.CompoundTag(Map.of(
                "type", new Nbt.Tag.StringTag("minecraft:dimension_type"),
                "value", new Nbt.Tag.ListTag(Nbt.COMPOUND, List.of(
                        dimensionEntry776("minecraft:overworld", 0, -64, 384),
                        dimensionEntry776("minecraft:the_nether", 1, 0, 128)))));
        return Nbt.writeAnonymous(new Nbt.Tag.CompoundTag(
                Map.of("minecraft:dimension_type", dimensionType)));
    }

    /**
     * Join Game の末尾 onlineMode を落として読むことの証明。
     * Registry Data で覚えた dimension_type の 1 番（ネザー）で Join Game が
     * 届くと、次元情報が minY 0・高さ 128 に変わる。onlineMode を落とさないと
     * parseJoinGame766 が余分なバイトで失敗し、次元はオーバーワールドの
     * まま（-64・384）になってこのテストが失敗する。
     */
    @Test
    void joinGame776ReadsNetherDimensionAfterStrippingOnlineMode() throws Exception {
        try (ServerSocket serverSocket = new ServerSocket(0, 1,
                java.net.InetAddress.getByName("127.0.0.1"))) {
            int port = serverSocket.getLocalPort();
            AtomicReference<FakeJavaServer> holder = new AtomicReference<>();
            AtomicReference<Exception> error = new AtomicReference<>();
            acceptInBackground(serverSocket, holder, error);

            ListenerEvents events = new ListenerEvents();
            UUID uuid = UUID.fromString("11111111-2222-3333-4444-555555555555");
            JavaBackendConnection backend = new JavaBackendConnection(
                    "127.0.0.1", port, 776, ".Steve", uuid, events);
            backend.connect();
            try {
                FakeJavaServer server = waitForServer(holder, error);
                try (FakeJavaServer fake = server) {
                    assertNotNull(fake.waitForId(0x00, 5_000), "ハンドシェイクが届きませんでした。");
                    fake.send(new MinecraftPacket(0x02, loginSuccess776(".Steve", uuid)));
                    assertNotNull(fake.waitForId(0x03, 5_000),
                            "Login Acknowledged が届きませんでした。");
                    assertNotNull(fake.waitForId(0x00, 5_000),
                            "Client Information が届きませんでした。");

                    // Registry Data（0x07）で dimension_type を送る。
                    fake.send(new MinecraftPacket(0x07, dimensionTypeRegistryData776()));
                    assertTrue(events.dimensionInfo.await(5, TimeUnit.SECONDS),
                            "Registry Data で dimensionInfo が届きませんでした。");

                    fake.send(new MinecraftPacket(0x03, new byte[0]));
                    assertNotNull(fake.waitForId(0x03, 5_000),
                            "Finish Configuration の承認が届きませんでした。");

                    // Join Game（次元 1＝ネザー、onlineMode は false）。
                    fake.send(new MinecraftPacket(0x31, joinGame776(42, 1)));
                    assertTrue(events.joined.await(5, TimeUnit.SECONDS),
                            "Join Game が処理されませんでした。");
                    assertEquals(42, events.entityId[0]);
                    assertTrue(events.dimensionInfos.stream()
                                    .anyMatch(d -> d[0] == 0 && d[1] == 128),
                            "ネザーの次元情報が届くこと: " + events.dimensionInfos);
                }
            } finally {
                backend.close();
            }
        }
    }
}
