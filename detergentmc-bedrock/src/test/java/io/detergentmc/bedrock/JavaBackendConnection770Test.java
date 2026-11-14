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
 * Bedrock → 内部 770（Java 1.21.5）サーバーのテスト。
 * 偽 Java サーバー（770 役）とのログイン〜プレイの流れを確認する。
 * 770 は Login Success に strictErrorHandling が無く、Join Game／Respawn の
 * SpawnInfo の末尾に seaLevel が付く（769 と同形）。Client Information には
 * 末尾の particleStatus が付く（769 と同じ）。変わるのはパケットIDだけ
 * （Join Game 0x2B、Player Info 0x3F）と、770で増えたレジストリ
 * （blue_egg・lingering_potion・trial_spawner 等）の正規化である。
 */
class JavaBackendConnection770Test {

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
            }, "fake-java-server-reader-770");
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
            // 770テストではスポーン座標を使わない。
        }

        @Override
        public void onChat(String sender, String message) {
            // 770テストではチャットを使わない。
        }

        @Override
        public void onTeleport(double x, double y, double z, float yaw, float pitch) {
            // 770テストではテレポートを使わない。
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
        }, "fake-java-server-accept-770");
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

    /** 770 の Login Success ペイロード（UUID・名前・空の属性。strictErrorHandling は無い）。 */
    private static byte[] loginSuccess770(String name, UUID uuid) {
        PacketIO.Writer writer = new PacketIO.Writer();
        writer.writeString(name);
        writer.writeUuid(uuid);
        writer.writeVarInt(0); // properties（空）
        return writer.bytes();
    }

    /** 770 の Join Game ペイロード（769 と同形。次元は番号）。 */
    private static byte[] joinGame770(int entityId, int dimensionId) {
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
        writer.writeVarInt(63); // seaLevel（769 と同じ）
        writer.writeBoolean(true); // enforcesSecureChat
        return writer.bytes();
    }

    /**
     * 770 の Player Info Update は update_hat（0x40）と update_list_order（0x80）を
     * 含む。番号だけ 769 の 0x40 から 0x3F に変わる。同じパケット内の add player
     * （0x01）の名前・ゲームモードが正しく読め、その後の Player Info Update
     * （0x04 ゲームモード変更）もずれることなく読めること。
     */
    @Test
    void playerInfo770ReadsHatAndListOrderBits() throws Exception {
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
                    "127.0.0.1", port, 770, ".Steve", uuid, events);
            backend.connect();
            try {
                FakeJavaServer server = waitForServer(holder, error);
                try (FakeJavaServer fake = server) {
                    assertNotNull(fake.waitForId(0x00, 5_000), "ハンドシェイクが届きませんでした。");
                    fake.send(new MinecraftPacket(0x02, loginSuccess770(".Steve", uuid)));
                    assertNotNull(fake.waitForId(0x03, 5_000),
                            "Login Acknowledged が届きませんでした。");
                    assertNotNull(fake.waitForId(0x00, 5_000),
                            "Client Information が届きませんでした。");
                    fake.send(new MinecraftPacket(0x03, new byte[0]));
                    assertNotNull(fake.waitForId(0x03, 5_000),
                            "Finish Configuration の承認が届きませんでした。");
                    // Join Game は770では 0x2B（769は 0x2C）。
                    fake.send(new MinecraftPacket(0x2b, joinGame770(42, 0)));
                    assertTrue(events.joined.await(5, TimeUnit.SECONDS),
                            "Join Game が処理されませんでした。");

                    // Player Info Update（0x3F）: add player + update_hat + update_list_order。
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
                    fake.send(new MinecraftPacket(0x3f, info.bytes()));
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
                    fake.send(new MinecraftPacket(0x3f, gamemode.bytes()));
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
     * 770の Chat Message の末尾には checksum u8 が増えた。Bedrock からの
     * チャット送信（sendChat）が770では末尾に 0x00 を付けないと、プロキシが
     * 末尾の acknowledged バイトを checksum として削って壊す
     * （BedrockMatrixIntegrationTest.matrixAgainst1_21_5 で発覚）。
     * 769では checksum は無い（末尾は acknowledged の3バイトで終わる）。
     */
    @Test
    void sendChat770AppendsChecksumByte() throws Exception {
        try (ServerSocket serverSocket = new ServerSocket(0, 1,
                java.net.InetAddress.getByName("127.0.0.1"))) {
            int port = serverSocket.getLocalPort();
            AtomicReference<FakeJavaServer> holder = new AtomicReference<>();
            AtomicReference<Exception> error = new AtomicReference<>();
            acceptInBackground(serverSocket, holder, error);

            ListenerEvents events = new ListenerEvents();
            UUID uuid = UUID.fromString("11111111-2222-3333-4444-555555555555");
            JavaBackendConnection backend = new JavaBackendConnection(
                    "127.0.0.1", port, 770, ".Steve", uuid, events);
            backend.connect();
            try {
                FakeJavaServer server = waitForServer(holder, error);
                try (FakeJavaServer fake = server) {
                    assertNotNull(fake.waitForId(0x00, 5_000), "ハンドシェイクが届きませんでした。");
                    fake.send(new MinecraftPacket(0x02, loginSuccess770(".Steve", uuid)));
                    assertNotNull(fake.waitForId(0x03, 5_000),
                            "Login Acknowledged が届きませんでした。");
                    assertNotNull(fake.waitForId(0x00, 5_000),
                            "Client Information が届きませんでした。");
                    fake.send(new MinecraftPacket(0x03, new byte[0]));
                    assertNotNull(fake.waitForId(0x03, 5_000),
                            "Finish Configuration の承認が届きませんでした。");
                    fake.send(new MinecraftPacket(0x2b, joinGame770(42, 0)));
                    assertTrue(events.joined.await(5, TimeUnit.SECONDS),
                            "Join Game が処理されませんでした。");

                    backend.sendChat("hello bedrock");
                    // Chat Message は770では 0x07。
                    MinecraftPacket chat = fake.waitForId(0x07, 5_000);
                    assertNotNull(chat, "Chat Message が届きませんでした。");
                    PacketIO.Reader reader = new PacketIO.Reader(chat.payload());
                    assertEquals("hello bedrock", reader.readString());
                    reader.readLong(); // timestamp
                    assertEquals(0L, reader.readLong()); // salt
                    assertEquals(false, reader.readBoolean()); // 署名なし
                    assertEquals(0, reader.readVarInt()); // offset
                    assertEquals((byte) 0, reader.readBytes(3)[0]); // acknowledged
                    assertEquals((byte) 0, reader.readByte(),
                            "770の Chat Message の末尾に checksum 0x00 が無かった。");
                    assertEquals(0, reader.remaining(),
                            "Chat Message の末尾に余分なバイトがあった。");
                }
            } finally {
                backend.close();
            }
        }
    }

    /**
     * 770 の Update Attributes は769と同じ（数値キー・modifier UUID は文字列）。
     * 属性の順序は769→770で変わらないため、generic.movement_speed は 21 のまま。
     */
    @Test
    void updateAttributes770UsesNumericKeysAndStringUuid() {
        PacketIO.Writer writer = new PacketIO.Writer();
        writer.writeVarInt(7); // entityId
        writer.writeVarInt(1); // count
        writer.writeVarInt(21); // generic.movement_speed（770）
        writer.writeDouble(0.1);
        writer.writeVarInt(1); // modifierCount
        writer.writeString("123e4567-e89b-12d3-a456-426614174000"); // modifier UUID（文字列）
        writer.writeDouble(0.0);
        writer.writeByte(0);
        BedrockEntityTranslator.UpdateAttributes attrs =
                BedrockEntityTranslator.parseUpdateAttributes(writer.bytes(), 770);
        assertEquals(7, attrs.entityId());
        assertEquals(0.1, attrs.movementSpeed());
    }

    /** 770 の新アイテム blue_egg（970）は 765 には無いので石（1）に落とす。 */
    @Test
    void slot770NormalizesBlueEggToStone() {
        PacketIO.Writer writer = new PacketIO.Writer();
        writer.writeBoolean(true); // present
        writer.writeVarInt(970); // blue_egg（770）
        writer.writeVarInt(1); // count
        writer.writeVarInt(0); // added
        writer.writeVarInt(0); // removed
        BedrockEntityTranslator.JavaSlot slot =
                BedrockEntityTranslator.readSlot(new PacketIO.Reader(writer.bytes()), 770);
        assertTrue(slot.present());
        assertEquals(1, slot.item765());
    }

    /** 770 の oak_boat（810）は 765 の oak_boat（773）に正規化される。 */
    @Test
    void slot770NormalizesOakBoatTo765() {
        PacketIO.Writer writer = new PacketIO.Writer();
        writer.writeBoolean(true); // present
        writer.writeVarInt(810); // oak_boat（770）
        writer.writeVarInt(1); // count
        writer.writeVarInt(0); // added
        writer.writeVarInt(0); // removed
        BedrockEntityTranslator.JavaSlot slot =
                BedrockEntityTranslator.readSlot(new PacketIO.Reader(writer.bytes()), 770);
        assertTrue(slot.present());
        assertEquals(773, slot.item765());
    }

    /** 770 のブロック状態は 765 に正規化される（石 1→1、trial_spawner 27698→26638）。 */
    @Test
    void blockState770NormalizesTo765() {
        assertEquals(1, BedrockChunkTranslator.normalizeBlockState(1, 770));
        assertEquals(26638, BedrockChunkTranslator.normalizeBlockState(27698, 770));
    }

    /** 770 のブタ（94）は 765 のブタ（73）に正規化される。 */
    @Test
    void entityType770NormalizesPigTo765() {
        assertEquals(73, BedrockEntityTranslator.normalizeEntityType(94, 770));
    }

    /** 770 で分かれた lingering_potion（100）は 765 の potion（78）に正規化される。 */
    @Test
    void entityType770NormalizesLingeringPotionTo765() {
        assertEquals(78, BedrockEntityTranslator.normalizeEntityType(100, 770));
        assertEquals(78, BedrockEntityTranslator.normalizeEntityType(99, 770));
    }

    /** 770 のクリイキング（29）は 765 には無いのでブタ（73）に落とす。 */
    @Test
    void entityType770NormalizesCreakingToPig() {
        assertEquals(73, BedrockEntityTranslator.normalizeEntityType(29, 770));
    }
}
