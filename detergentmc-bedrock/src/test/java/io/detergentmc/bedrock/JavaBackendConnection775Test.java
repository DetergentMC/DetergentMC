// Copyright (c) 2026 DetergentMC contributors.
// SPDX-License-Identifier: Apache-2.0

package io.detergentmc.bedrock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.detergentmc.protocol.MinecraftPacket;
import io.detergentmc.protocol.PacketIO;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

/**
 * Bedrock → 内部 775（Java 26.1）サーバーのテスト。
 * 偽 Java サーバー（775 役）とのログイン〜プレイの流れを確認する。
 * 775 は 774 とペイロードが同じでパケットIDだけがずれた（Join Game 0x31、Player Info Update 0x46、
 * Chat Message 0x09、Spawn Position 0x61）。Login Success に
 * strictErrorHandling は無く、Join Game／Respawn の SpawnInfo の末尾に
 * seaLevel が付く（774 と同形）。Client Information には末尾の
 * particleStatus が付く（774 と同じ）。
 * 変わるのは775で増えたレジストリの正規化だけで、属性表は774と同じ
 * （camera_distance は 8番、generic.movement_speed は 22）。
 * エンティティは 774 と同一（157件）のためブタ・ガスト・ハッピーガストの
 * 番号は 774 と同じ。775で増えたのは金のタンポポ（アイテム 230 に挿入）、
 * トランペットの音符ブロックのブロック状態 200 件、新粒子
 * pause_mob_growth／reset_mob_growth（50・51）などで、775の番号で
 * 正規化することを確認する。
 */
class JavaBackendConnection775Test {

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
            }, "fake-java-server-reader-775");
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
            // 775テストではチャットを使わない。
        }

        @Override
        public void onTeleport(double x, double y, double z, float yaw, float pitch) {
            // 775テストではテレポートを使わない。
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
        }, "fake-java-server-accept-775");
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

    /** 775 の Login Success ペイロード（UUID・名前・空の属性。strictErrorHandling は無い）。 */
    private static byte[] loginSuccess775(String name, UUID uuid) {
        PacketIO.Writer writer = new PacketIO.Writer();
        writer.writeString(name);
        writer.writeUuid(uuid);
        writer.writeVarInt(0); // properties（空）
        return writer.bytes();
    }

    /** 775 の Join Game ペイロード（774 と同形。次元は番号、末尾に seaLevel）。 */
    private static byte[] joinGame775(int entityId, int dimensionId) {
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
        writer.writeVarInt(63); // seaLevel（774 と同じ）
        writer.writeBoolean(true); // enforcesSecureChat
        return writer.bytes();
    }

    /**
     * 775 の Player Info Update は update_hat（0x40）と update_list_order（0x80）を
     * 含む。番号は 775 では 0x46（774 では 0x44）。同じパケット内の add player
     * （0x01）の名前・ゲームモードが正しく読め、その後の Player Info Update
     * （0x04 ゲームモード変更）もずれることなく読めること。
     */
    @Test
    void playerInfo775ReadsHatAndListOrderBits() throws Exception {
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
                    "127.0.0.1", port, 775, ".Steve", uuid, events);
            backend.connect();
            try {
                FakeJavaServer server = waitForServer(holder, error);
                try (FakeJavaServer fake = server) {
                    assertNotNull(fake.waitForId(0x00, 5_000), "ハンドシェイクが届きませんでした。");
                    fake.send(new MinecraftPacket(0x02, loginSuccess775(".Steve", uuid)));
                    assertNotNull(fake.waitForId(0x03, 5_000),
                            "Login Acknowledged が届きませんでした。");
                    assertNotNull(fake.waitForId(0x00, 5_000),
                            "Client Information が届きませんでした。");
                    fake.send(new MinecraftPacket(0x03, new byte[0]));
                    assertNotNull(fake.waitForId(0x03, 5_000),
                            "Finish Configuration の承認が届きませんでした。");
                    // Join Game は775では 0x31（774 では 0x30）。
                    fake.send(new MinecraftPacket(0x31, joinGame775(42, 0)));
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
     * 775の Chat Message は 0x09（774 では 0x08）。末尾には checksum u8 が付く。
     * Bedrock からのチャット送信（sendChat）が775では 0x09 に末尾 0x00 を
     * 付けないと、プロキシが末尾の acknowledged バイトを checksum として
     * 削って壊す（774と同じ）。
     */
    @Test
    void sendChat775AppendsChecksumByte() throws Exception {
        try (ServerSocket serverSocket = new ServerSocket(0, 1,
                java.net.InetAddress.getByName("127.0.0.1"))) {
            int port = serverSocket.getLocalPort();
            AtomicReference<FakeJavaServer> holder = new AtomicReference<>();
            AtomicReference<Exception> error = new AtomicReference<>();
            acceptInBackground(serverSocket, holder, error);

            ListenerEvents events = new ListenerEvents();
            UUID uuid = UUID.fromString("11111111-2222-3333-4444-555555555555");
            JavaBackendConnection backend = new JavaBackendConnection(
                    "127.0.0.1", port, 775, ".Steve", uuid, events);
            backend.connect();
            try {
                FakeJavaServer server = waitForServer(holder, error);
                try (FakeJavaServer fake = server) {
                    assertNotNull(fake.waitForId(0x00, 5_000), "ハンドシェイクが届きませんでした。");
                    fake.send(new MinecraftPacket(0x02, loginSuccess775(".Steve", uuid)));
                    assertNotNull(fake.waitForId(0x03, 5_000),
                            "Login Acknowledged が届きませんでした。");
                    assertNotNull(fake.waitForId(0x00, 5_000),
                            "Client Information が届きませんでした。");
                    fake.send(new MinecraftPacket(0x03, new byte[0]));
                    assertNotNull(fake.waitForId(0x03, 5_000),
                            "Finish Configuration の承認が届きませんでした。");
                    fake.send(new MinecraftPacket(0x31, joinGame775(42, 0)));
                    assertTrue(events.joined.await(5, TimeUnit.SECONDS),
                            "Join Game が処理されませんでした。");

                    backend.sendChat("hello bedrock");
                    // Chat Message は775では 0x09。
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
                            "775の Chat Message の末尾に checksum 0x00 が無かった。");
                    assertEquals(0, reader.remaining(),
                            "Chat Message の末尾に余分なバイトがあった。");
                }
            } finally {
                backend.close();
            }
        }
    }

    /**
     * 775 の Update Attributes は774と同じく数値キー・modifier UUID は文字列。
     * 属性の順序は774→775で変わらない：camera_distance は 8番、
     * generic.movement_speed は 22 のまま。
     */
    @Test
    void updateAttributes775UsesNumericKeysAndStringUuid() {
        PacketIO.Writer writer = new PacketIO.Writer();
        writer.writeVarInt(7); // entityId
        writer.writeVarInt(1); // count
        writer.writeVarInt(22); // generic.movement_speed（775。774でも 22）
        writer.writeDouble(0.1);
        writer.writeVarInt(1); // modifierCount
        writer.writeString("123e4567-e89b-12d3-a456-426614174000"); // modifier UUID（文字列）
        writer.writeDouble(0.0);
        writer.writeByte(0);
        BedrockEntityTranslator.UpdateAttributes attrs =
                BedrockEntityTranslator.parseUpdateAttributes(writer.bytes(), 775);
        assertEquals(7, attrs.entityId());
        assertEquals(0.1, attrs.movementSpeed());
    }

    /** 775 の camera_distance（8）は movement_speed でも max_health でもないため読まない。 */
    @Test
    void updateAttributes775SkipsCameraDistance() {
        PacketIO.Writer writer = new PacketIO.Writer();
        writer.writeVarInt(7); // entityId
        writer.writeVarInt(1); // count
        writer.writeVarInt(8); // camera_distance（775）
        writer.writeDouble(0.5);
        writer.writeVarInt(0); // modifierCount
        BedrockEntityTranslator.UpdateAttributes attrs =
                BedrockEntityTranslator.parseUpdateAttributes(writer.bytes(), 775);
        assertEquals(7, attrs.entityId());
        assertNull(attrs.maxHealth());
        assertNull(attrs.movementSpeed());
    }

    /** 775 の music_disc_lava_chicken（1317）は 765 には無いので石（1）に落とす。 */
    @Test
    void slot775NormalizesNewItemToStone() {
        PacketIO.Writer writer = new PacketIO.Writer();
        writer.writeBoolean(true); // present
        writer.writeVarInt(1317); // music_disc_lava_chicken（775。774では 1316）
        writer.writeVarInt(1); // count
        writer.writeVarInt(0); // added
        writer.writeVarInt(0); // removed
        BedrockEntityTranslator.JavaSlot slot =
                BedrockEntityTranslator.readSlot(new PacketIO.Reader(writer.bytes()), 775);
        assertTrue(slot.present());
        assertEquals(1, slot.item765());
    }

    /** 775 の金のタンポポ（230）は 765 には無いので石（1）に落とす（775で 230 に挿入された新アイテム）。 */
    @Test
    void slot775NormalizesGoldenDandelionToStone() {
        PacketIO.Writer writer = new PacketIO.Writer();
        writer.writeBoolean(true); // present
        writer.writeVarInt(230); // golden_dandelion（775で新規）
        writer.writeVarInt(1); // count
        writer.writeVarInt(0); // added
        writer.writeVarInt(0); // removed
        BedrockEntityTranslator.JavaSlot slot =
                BedrockEntityTranslator.readSlot(new PacketIO.Reader(writer.bytes()), 775);
        assertTrue(slot.present());
        assertEquals(1, slot.item765());
    }

    /** 775 の oak_boat（864）は 765 の oak_boat（773）に正規化される。 */
    @Test
    void slot775NormalizesOakBoatTo765() {
        PacketIO.Writer writer = new PacketIO.Writer();
        writer.writeBoolean(true); // present
        writer.writeVarInt(864); // oak_boat（775。774では 863）
        writer.writeVarInt(1); // count
        writer.writeVarInt(0); // added
        writer.writeVarInt(0); // removed
        BedrockEntityTranslator.JavaSlot slot =
                BedrockEntityTranslator.readSlot(new PacketIO.Reader(writer.bytes()), 775);
        assertTrue(slot.present());
        assertEquals(773, slot.item765());
    }

    /** 775 のブロック状態は 765 に正規化される（石 1→1、trial_spawner 29657→26638、トランペットの音符ブロック 1381→石 1）。 */

    /** 775 のエフェクト 39（BreathOfTheNautilus）は 765 には無いので Speed（0）に落とす。 */
    @Test
    void effect775NormalizesBreathOfTheNautilusToSpeed() {
        assertEquals(0, BedrockEntityTranslator.normalizeEffectTo765(39, 775));
        assertEquals(5, BedrockEntityTranslator.normalizeEffectTo765(5, 775));
    }

    @Test
    void blockState775NormalizesTo765() {
        assertEquals(1, BedrockChunkTranslator.normalizeBlockState(1, 775));
        assertEquals(26638, BedrockChunkTranslator.normalizeBlockState(29657, 775));
        assertEquals(1, BedrockChunkTranslator.normalizeBlockState(1381, 775));
    }

    /** 775 のブタ（100）は 765 のブタ（73）に正規化される。 */
    @Test
    void entityType775NormalizesPigTo765() {
        assertEquals(73, BedrockEntityTranslator.normalizeEntityType(100, 775));
    }

    /** 775 のガスト（57）は 765 のガスト（42）に正規化される。 */
    @Test
    void entityType775NormalizesGhastTo765() {
        assertEquals(42, BedrockEntityTranslator.normalizeEntityType(57, 775));
    }

    /**
     * 775 のハッピーガスト（58）は 765 には無いのでブタ（73）に落とす。
     * Bedrock の正規化表は合成表（775→774ではそのまま、774→765でブタ）のため、
     * Java プロキシの Spawn Entity（ガストに代替）とは違う代替になる。
     */
    @Test
    void entityType775NormalizesHappyGhastToPig() {
        assertEquals(73, BedrockEntityTranslator.normalizeEntityType(58, 775));
    }

    /** ハンドシェイク〜Join Game まで進める（2件の追加テストの共通手順）。 */
    private static void loginToPlay(FakeJavaServer fake, ListenerEvents events, UUID uuid)
            throws Exception {
        assertNotNull(fake.waitForId(0x00, 5_000), "ハンドシェイクが届きませんでした。");
        fake.send(new MinecraftPacket(0x02, loginSuccess775(".Steve", uuid)));
        assertNotNull(fake.waitForId(0x03, 5_000), "Login Acknowledged が届きませんでした。");
        assertNotNull(fake.waitForId(0x00, 5_000), "Client Information が届きませんでした。");
        fake.send(new MinecraftPacket(0x03, new byte[0]));
        assertNotNull(fake.waitForId(0x03, 5_000), "Finish Configuration の承認が届きませんでした。");
        fake.send(new MinecraftPacket(0x31, joinGame775(42, 0)));
        assertTrue(events.joined.await(5, TimeUnit.SECONDS), "Join Game が処理されませんでした。");
    }

    /** 775 の Spawn Position ペイロード（次元名＋位置＋yaw＋pitch）。 */
    private static byte[] spawnPosition775(int x, int y, int z) {
        PacketIO.Writer writer = new PacketIO.Writer();
        writer.writeString("minecraft:overworld");
        long packed = ((x & 0x3FFFFFFL) << 38) | ((z & 0x3FFFFFFL) << 12) | (y & 0xFFFL);
        writer.writePositionRaw(packed);
        writer.writeFloat(0.0f); // yaw
        writer.writeFloat(0.0f); // pitch
        return writer.bytes();
    }

    /**
     * 775 の Spawn Position は RespawnData（次元名の文字列＋位置＋yaw＋pitch）。
     * 位置の先頭に次元名が付くため、そのまま位置として読むと座標が化ける。
     * 774と同一形式のため、スポーン（88, 70, -104）が
     * (88.5, 70, -103.5) として届くこと。
     */
    @Test
    void spawnPosition775ReadsRespawnData() throws Exception {
        try (ServerSocket serverSocket = new ServerSocket(0, 1,
                java.net.InetAddress.getByName("127.0.0.1"))) {
            int port = serverSocket.getLocalPort();
            AtomicReference<FakeJavaServer> holder = new AtomicReference<>();
            AtomicReference<Exception> error = new AtomicReference<>();
            acceptInBackground(serverSocket, holder, error);

            ListenerEvents events = new ListenerEvents();
            UUID uuid = UUID.fromString("11111111-2222-3333-4444-555555555555");
            JavaBackendConnection backend = new JavaBackendConnection(
                    "127.0.0.1", port, 775, ".Steve", uuid, events);
            backend.connect();
            try {
                FakeJavaServer server = waitForServer(holder, error);
                try (FakeJavaServer fake = server) {
                    loginToPlay(fake, events, uuid);
                    // Spawn Position は775では 0x61（774 では 0x5F）。
                    fake.send(new MinecraftPacket(0x61, spawnPosition775(88, 70, -104)));
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
     * Chunk Batch Finished（775でも 0x0B）を受けたら Chunk Batch Received
     * （775でも 0x0B、f32 の chunksPerTick）を返すこと。774と同一のため、
     * Ack を返さないと最初のバッチ（9チャンク）のあと
     * チャンクが送られてこない。
     */
    @Test
    void chunkBatchFinished775SendsReceivedAck() throws Exception {
        try (ServerSocket serverSocket = new ServerSocket(0, 1,
                java.net.InetAddress.getByName("127.0.0.1"))) {
            int port = serverSocket.getLocalPort();
            AtomicReference<FakeJavaServer> holder = new AtomicReference<>();
            AtomicReference<Exception> error = new AtomicReference<>();
            acceptInBackground(serverSocket, holder, error);

            ListenerEvents events = new ListenerEvents();
            UUID uuid = UUID.fromString("11111111-2222-3333-4444-555555555555");
            JavaBackendConnection backend = new JavaBackendConnection(
                    "127.0.0.1", port, 775, ".Steve", uuid, events);
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
}
