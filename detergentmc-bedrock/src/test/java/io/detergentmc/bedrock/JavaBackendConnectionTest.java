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
 * Java バックエンド（内部バニラ役の偽サーバー）とのログイン〜プレイのテスト。
 * 偽サーバーは本物のバニラと同じ順番でパケットを送る。
 */
class JavaBackendConnectionTest {

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
            }, "fake-java-server-reader");
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
        final CountDownLatch chatSeen = new CountDownLatch(1);
        final CountDownLatch teleportSeen = new CountDownLatch(1);
        final CountDownLatch disconnected = new CountDownLatch(1);
        final List<String> chats = new ArrayList<>();
        final List<String> senders = new ArrayList<>();
        final List<String> disconnectReasons = new ArrayList<>();
        final int[] entityId = {-1};
        final double[] spawn = new double[3];
        final double[] teleport = new double[3];
        final CountDownLatch dimensionInfo = new CountDownLatch(1);
        final CountDownLatch chunkSeen = new CountDownLatch(1);
        final List<int[]> blockChanges = new ArrayList<>();
        final List<int[]> dimensionInfos = new ArrayList<>();
        volatile byte[] lastChunkPayload;
        volatile int lastChunkPayloadLength;

        @Override
        public void onJoined(int javaEntityId) {
            entityId[0] = javaEntityId;
            joined.countDown();
        }

        @Override
        public void onSpawnPosition(double x, double y, double z) {
            spawn[0] = x;
            spawn[1] = y;
            spawn[2] = z;
        }

        @Override
        public void onChat(String sender, String message) {
            senders.add(sender);
            chats.add(message);
            chatSeen.countDown();
        }

        @Override
        public void onTeleport(double x, double y, double z, float yaw, float pitch) {
            teleport[0] = x;
            teleport[1] = y;
            teleport[2] = z;
            teleportSeen.countDown();
        }

        @Override
        public void onChunk(byte[] javaChunkPayload) {
            lastChunkPayload = javaChunkPayload.clone();
            lastChunkPayloadLength = javaChunkPayload.length;
            chunkSeen.countDown();
        }

        @Override
        public void onBlockChange(int x, int y, int z, int stateId) {
            blockChanges.add(new int[]{x, y, z, stateId});
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

    private static byte[] textComponentNbt(String text) {
        Nbt.Tag.CompoundTag compound = new Nbt.Tag.CompoundTag(
                Map.of("text", new Nbt.Tag.StringTag(text)));
        return Nbt.writeAnonymous(compound);
    }

    private static byte[] playerChatPayload(UUID sender, String message) {
        PacketIO.Writer writer = new PacketIO.Writer();
        writer.writeUuid(sender);
        writer.writeVarInt(0); // index
        writer.writeBoolean(false); // 署名なし
        writer.writeString(message);
        return writer.bytes();
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
        }, "fake-java-server-accept");
        thread.setDaemon(true);
        thread.start();
        return null;
    }

    /**
     * 766（1.20.5）の設定フェーズ：Select Known Packs と Ping に答える。
     * 返事をしないと本物のバニラ 1.20.5 は先に進まず、Finish Configuration が
     * 来ない（内部 766 サーバーの参加が止まる原因だった）。
     */
    @Test
    void selectKnownPacksAndPingInConfig766() throws Exception {
        try (ServerSocket serverSocket = new ServerSocket(0, 1,
                java.net.InetAddress.getByName("127.0.0.1"))) {
            int port = serverSocket.getLocalPort();
            AtomicReference<FakeJavaServer> holder = new AtomicReference<>();
            AtomicReference<Exception> error = new AtomicReference<>();
            acceptInBackground(serverSocket, holder, error);

            ListenerEvents events = new ListenerEvents();
            UUID uuid = UUID.fromString("11111111-2222-3333-4444-555555555555");
            JavaBackendConnection backend = new JavaBackendConnection(
                    "127.0.0.1", port, 766, ".Steve", uuid, events);
            backend.connect();
            try {
                FakeJavaServer server = null;
                for (int i = 0; i < 100 && server == null; i++) {
                    Thread.sleep(50);
                    server = holder.get();
                }
                assertNotNull(server, "偽サーバーが受け入れませんでした: " + error.get());
                try (FakeJavaServer fake = server) {
                    // ハンドシェイク（0x00）とログイン開始（0x00）が届く。
                    assertNotNull(fake.waitForId(0x00, 5_000), "ハンドシェイクが届きませんでした。");

                    // Login Success（0x02）→ Login Acknowledged（0x03）と
                    // Client Information（0x00）が返ってくる。
                    fake.send(new MinecraftPacket(0x02,
                            new PacketIO.Writer().writeString(".Steve")
                                    .writeUuid(uuid).bytes()));
                    assertNotNull(fake.waitForId(0x03, 5_000),
                            "Login Acknowledged が届きませんでした。");
                    assertNotNull(fake.waitForId(0x00, 5_000),
                            "Client Information が届きませんでした。");

                    // Select Known Packs（0x0e）：既知パック 1 件を送る。
                    // → 空の一覧（VarInt 0）の応答（0x07）が返ってくること。
                    PacketIO.Writer knownPacks = new PacketIO.Writer();
                    knownPacks.writeVarInt(1);
                    knownPacks.writeString("minecraft");
                    knownPacks.writeString("vanilla");
                    knownPacks.writeString("1.20.5");
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

                    // Join Game（0x2b）：エンティティ ID だけ読む。
                    PacketIO.Writer joinGame = new PacketIO.Writer();
                    joinGame.writeInt(42);
                    fake.send(new MinecraftPacket(0x2b, joinGame.bytes()));
                    assertTrue(events.joined.await(5, TimeUnit.SECONDS),
                            "Join Game が処理されませんでした。");
                    assertEquals(42, events.entityId[0]);
                }
            } finally {
                backend.close();
            }
        }
    }

    @Test
    void loginConfigAndPlayFlow765() throws Exception {
        try (ServerSocket serverSocket = new ServerSocket(0, 1,
                java.net.InetAddress.getByName("127.0.0.1"))) {
            int port = serverSocket.getLocalPort();
            AtomicReference<FakeJavaServer> holder = new AtomicReference<>();
            AtomicReference<Exception> error = new AtomicReference<>();
            acceptInBackground(serverSocket, holder, error);

            ListenerEvents events = new ListenerEvents();
            UUID uuid = UUID.fromString("11111111-2222-3333-4444-555555555555");
            JavaBackendConnection backend = new JavaBackendConnection(
                    "127.0.0.1", port, 765, ".Steve", uuid, events);
            backend.connect();
            try {
                // 偽サーバーの受け入れを待つ。
                FakeJavaServer server = null;
                for (int i = 0; i < 100 && server == null; i++) {
                    Thread.sleep(50);
                    server = holder.get();
                }
                assertNotNull(server, "偽サーバーが受け入れませんでした: " + error.get());
                try (FakeJavaServer fake = server) {
                    // ハンドシェイク（0x00）とログイン開始（0x00）が届く。
                    MinecraftPacket handshake = fake.waitForId(0x00, 5_000);
                    assertNotNull(handshake, "ハンドシェイクが届きませんでした。");

                    // Login Success（0x02）→ Login Acknowledged（0x03）と
                    // Client Information（0x00）が返ってくる。
                    fake.send(new MinecraftPacket(0x02,
                            new PacketIO.Writer().writeString(".Steve")
                                    .writeUuid(uuid).bytes()));
                    assertNotNull(fake.waitForId(0x03, 5_000),
                            "Login Acknowledged が届きませんでした。");
                    assertNotNull(fake.waitForId(0x00, 5_000),
                            "Client Information が届きませんでした。");

                    // Add Resource Pack（0x07）→ accepted 応答（0x05）。
                    PacketIO.Writer pack = new PacketIO.Writer();
                    UUID packId = UUID.fromString("22222222-3333-4444-5555-666666666666");
                    pack.writeUuid(packId);
                    pack.writeString("https://127.0.0.1/pack.zip");
                    pack.writeString("0123456789012345678901234567890123456789");
                    pack.writeBoolean(false);
                    pack.writeString("");
                    fake.send(new MinecraftPacket(0x07, pack.bytes()));
                    MinecraftPacket packResponse = fake.waitForId(0x05, 5_000);
                    assertNotNull(packResponse, "Resource Pack 応答が届きませんでした。");
                    PacketIO.Reader packReader = new PacketIO.Reader(packResponse.payload());
                    assertEquals(packId, packReader.readUuid());
                    assertEquals(3, packReader.readVarInt());

                    // Finish Configuration（0x02）→ 承認（0x02）。
                    fake.send(new MinecraftPacket(0x02, new byte[0]));
                    assertNotNull(fake.waitForId(0x02, 5_000),
                            "Finish Configuration の承認が届きませんでした。");

                    // Join Game（0x29）：エンティティ ID だけ読む。
                    PacketIO.Writer joinGame = new PacketIO.Writer();
                    joinGame.writeInt(42);
                    fake.send(new MinecraftPacket(0x29, joinGame.bytes()));
                    assertTrue(events.joined.await(5, TimeUnit.SECONDS),
                            "Join Game が処理されませんでした。");
                    assertEquals(42, events.entityId[0]);

                    // Spawn Position（0x54）。Position は x 26ビット・z 26ビット・y 12ビット。
                    PacketIO.Writer spawn = new PacketIO.Writer();
                    long packed = ((100L & 0x3FFFFFF) << 38)
                            | (((-50L) & 0x3FFFFFF) << 12)
                            | (64L & 0xFFF);
                    spawn.writePositionRaw(packed);
                    fake.send(new MinecraftPacket(0x54, spawn.bytes()));
                    Thread.sleep(200);
                    assertEquals(100.5, events.spawn[0], 0.001);
                    assertEquals(64.0, events.spawn[1], 0.001);

                    // Keep Alive（0x24）→ 応答（0x15）。
                    PacketIO.Writer keepAlive = new PacketIO.Writer();
                    keepAlive.writeLong(987654321L);
                    fake.send(new MinecraftPacket(0x24, keepAlive.bytes()));
                    MinecraftPacket keepAliveResponse = fake.waitForId(0x15, 5_000);
                    assertNotNull(keepAliveResponse, "Keep Alive 応答が届きませんでした。");
                    assertEquals(987654321L,
                            new PacketIO.Reader(keepAliveResponse.payload()).readLong());

                    // System Chat（0x69、NBT）→ チャットが届く。
                    fake.send(new MinecraftPacket(0x69, textComponentNbt("hello from vanilla")));
                    assertTrue(events.chatSeen.await(5, TimeUnit.SECONDS),
                            "System Chat が届きませんでした。");
                    assertEquals("hello from vanilla", events.chats.get(0));

                    // Player Chat（0x37）→ 名前付きで届く（Player Info Update 経由）。
                    UUID alex = UUID.fromString("33333333-4444-5555-6666-777777777777");
                    PacketIO.Writer infoUpdate = new PacketIO.Writer();
                    infoUpdate.writeVarInt(1); // add player のみ
                    infoUpdate.writeVarInt(1); // 1 人
                    infoUpdate.writeUuid(alex);
                    infoUpdate.writeString("Alex");
                    infoUpdate.writeVarInt(0); // プロパティなし
                    infoUpdate.writeVarInt(0); // gamemode
                    infoUpdate.writeVarInt(10); // ping
                    infoUpdate.writeBoolean(false); // display name なし
                    fake.send(new MinecraftPacket(0x3c, infoUpdate.bytes()));
                    Thread.sleep(200);
                    fake.send(new MinecraftPacket(0x37, playerChatPayload(alex, "yo")));
                    Thread.sleep(300);
                    assertTrue(chatsContains(events, "yo"),
                            "Player Chat が届きませんでした: " + events.chats);
                    assertTrue(events.senders.contains("Alex"),
                            "Player Chat の送信者名が Alex になること: " + events.senders);

                    // Synchronize Player Position（0x3e）→ テレポート＋承認（0x00）。
                    PacketIO.Writer teleport = new PacketIO.Writer();
                    teleport.writeDouble(200.5);
                    teleport.writeDouble(70.0);
                    teleport.writeDouble(-100.5);
                    teleport.writeFloat(45.0f);
                    teleport.writeFloat(10.0f);
                    teleport.writeByte(0);
                    teleport.writeVarInt(7);
                    fake.send(new MinecraftPacket(0x3e, teleport.bytes()));
                    assertTrue(events.teleportSeen.await(5, TimeUnit.SECONDS),
                            "テレポートが届きませんでした。");
                    assertEquals(200.5, events.teleport[0], 0.001);
                    MinecraftPacket confirm = fake.waitForId(0x00, 5_000);
                    assertNotNull(confirm, "Teleport Confirm が届きませんでした。");
                    assertEquals(7, new PacketIO.Reader(confirm.payload()).readVarInt());

                    // こちらからのチャット・移動が Java 側へ届く。
                    backend.sendChat("hello bedrock");
                    MinecraftPacket chat = fake.waitForId(0x05, 5_000);
                    assertNotNull(chat, "Chat Message が届きませんでした。");
                    assertEquals("hello bedrock",
                            new PacketIO.Reader(chat.payload()).readString());

                    backend.sendPosition(1.5, 65.0, 2.5, 90.0f, 5.0f, true);
                    MinecraftPacket move = fake.waitForId(0x18, 5_000);
                    assertNotNull(move, "Set Player Position が届きませんでした。");
                    PacketIO.Reader moveReader = new PacketIO.Reader(move.payload());
                    assertEquals(1.5, moveReader.readDouble(), 0.001);
                    assertEquals(65.0, moveReader.readDouble(), 0.001);

                    backend.sendCommand("say hi");
                    MinecraftPacket command = fake.waitForId(0x04, 5_000);
                    assertNotNull(command, "Chat Command が届きませんでした。");
                    assertEquals("say hi", new PacketIO.Reader(command.payload()).readString());

                    // Disconnect（0x1b）→ 切断が伝わる。
                    fake.send(new MinecraftPacket(0x1b, textComponentNbt("Kicked.")));
                    assertTrue(events.disconnected.await(5, TimeUnit.SECONDS),
                            "Disconnect が届きませんでした。");
                    assertEquals("Kicked.", events.disconnectReasons.get(0));
                }
            } finally {
                backend.close();
            }
        }
    }

    @Test
    void loginFlow763HasNoConfigurationPhase() throws Exception {
        try (ServerSocket serverSocket = new ServerSocket(0, 1,
                java.net.InetAddress.getByName("127.0.0.1"))) {
            int port = serverSocket.getLocalPort();
            AtomicReference<FakeJavaServer> holder = new AtomicReference<>();
            AtomicReference<Exception> error = new AtomicReference<>();
            acceptInBackground(serverSocket, holder, error);

            ListenerEvents events = new ListenerEvents();
            UUID uuid = UUID.fromString("11111111-2222-3333-4444-555555555555");
            JavaBackendConnection backend = new JavaBackendConnection(
                    "127.0.0.1", port, 763, ".Steve", uuid, events);
            backend.connect();
            try {
                FakeJavaServer server = null;
                for (int i = 0; i < 100 && server == null; i++) {
                    Thread.sleep(50);
                    server = holder.get();
                }
                assertNotNull(server, "偽サーバーが受け入れませんでした: " + error.get());
                try (FakeJavaServer fake = server) {
                    // 763 の Login Start は名前＋bool＋UUID。
                    MinecraftPacket loginStart = fake.waitForId(0x00, 5_000);
                    assertNotNull(loginStart);
                    // 2 個目の 0x00 が Login Start。
                    MinecraftPacket second = fake.waitForId(0x00, 5_000);
                    assertNotNull(second, "Login Start が届きませんでした。");
                    PacketIO.Reader reader = new PacketIO.Reader(second.payload());
                    assertEquals(".Steve", reader.readString());
                    assertTrue(reader.readBoolean());
                    assertEquals(uuid, reader.readUuid());

                    // Login Success 後、763 は設定フェーズなしで Join Game が来る。
                    fake.send(new MinecraftPacket(0x02,
                            new PacketIO.Writer().writeString(".Steve")
                                    .writeUuid(uuid).bytes()));
                    PacketIO.Writer joinGame = new PacketIO.Writer();
                    joinGame.writeInt(9);
                    fake.send(new MinecraftPacket(0x28, joinGame.bytes()));
                    assertTrue(events.joined.await(5, TimeUnit.SECONDS),
                            "763 の Join Game が処理されませんでした。");
                    assertEquals(9, events.entityId[0]);
                    assertTrue(backend.isJoined());
                }
            } finally {
                backend.close();
            }
        }
    }

    @Test
    void registryAndChunkFlow765() throws Exception {
        try (ServerSocket serverSocket = new ServerSocket(0, 1,
                java.net.InetAddress.getByName("127.0.0.1"))) {
            int port = serverSocket.getLocalPort();
            AtomicReference<FakeJavaServer> holder = new AtomicReference<>();
            AtomicReference<Exception> error = new AtomicReference<>();
            acceptInBackground(serverSocket, holder, error);

            ListenerEvents events = new ListenerEvents();
            UUID uuid = UUID.fromString("11111111-2222-3333-4444-555555555555");
            JavaBackendConnection backend = new JavaBackendConnection(
                    "127.0.0.1", port, 765, ".Steve", uuid, events);
            backend.connect();
            try {
                FakeJavaServer server = null;
                for (int i = 0; i < 100 && server == null; i++) {
                    Thread.sleep(50);
                    server = holder.get();
                }
                assertNotNull(server, "偽サーバーが受け入れませんでした: " + error.get());
                try (FakeJavaServer fake = server) {
                    assertNotNull(fake.waitForId(0x00, 5_000), "ハンドシェイクが届きませんでした。");
                    fake.send(new MinecraftPacket(0x02,
                            new PacketIO.Writer().writeString(".Steve")
                                    .writeUuid(uuid).bytes()));
                    assertNotNull(fake.waitForId(0x03, 5_000),
                            "Login Acknowledged が届きませんでした。");
                    assertNotNull(fake.waitForId(0x00, 5_000),
                            "Client Information が届きませんでした。");

                    // Registry Data：バイオーム（名前だけ）と dimension_type（min_y/height 付き）。
                    PacketIO.Writer biomes = new PacketIO.Writer();
                    biomes.writeString("minecraft:worldgen/biome");
                    biomes.writeVarInt(2);
                    biomes.writeString("minecraft:plains");
                    biomes.writeBoolean(false);
                    biomes.writeString("minecraft:desert");
                    biomes.writeBoolean(false);
                    fake.send(new MinecraftPacket(0x05, biomes.bytes()));

                    PacketIO.Writer dimensions = new PacketIO.Writer();
                    dimensions.writeString("minecraft:dimension_type");
                    dimensions.writeVarInt(2);
                    dimensions.writeString("minecraft:overworld");
                    dimensions.writeBoolean(true);
                    dimensions.writeBytes(Nbt.writeAnonymous(new Nbt.Tag.CompoundTag(Map.of(
                            "min_y", new Nbt.Tag.IntTag(-64),
                            "height", new Nbt.Tag.IntTag(384)))));
                    dimensions.writeString("minecraft:the_nether");
                    dimensions.writeBoolean(true);
                    dimensions.writeBytes(Nbt.writeAnonymous(new Nbt.Tag.CompoundTag(Map.of(
                            "min_y", new Nbt.Tag.IntTag(0),
                            "height", new Nbt.Tag.IntTag(128)))));
                    fake.send(new MinecraftPacket(0x05, dimensions.bytes()));
                    assertTrue(events.dimensionInfo.await(5, TimeUnit.SECONDS),
                            "Registry Data で dimensionInfo が届きませんでした。");

                    // Finish Configuration → Join Game（765 の全フィールド）。
                    fake.send(new MinecraftPacket(0x02, new byte[0]));
                    assertNotNull(fake.waitForId(0x02, 5_000),
                            "Finish Configuration の承認が届きませんでした。");
                    PacketIO.Writer joinGame = new PacketIO.Writer();
                    joinGame.writeInt(99);
                    joinGame.writeBoolean(false); // hardcore
                    PacketIO.writeStringList(joinGame, List.of("minecraft:overworld"));
                    joinGame.writeVarInt(20); // maxPlayers
                    joinGame.writeVarInt(10); // viewDistance
                    joinGame.writeVarInt(10); // simulationDistance
                    joinGame.writeBoolean(false); // reducedDebug
                    joinGame.writeBoolean(true); // enableRespawnScreen
                    joinGame.writeBoolean(false); // doLimitedCrafting
                    joinGame.writeString("minecraft:overworld"); // worldType
                    joinGame.writeString("minecraft:overworld"); // worldName
                    joinGame.writeLong(123L); // hashedSeed
                    joinGame.writeByte(0); // gameMode
                    joinGame.writeByte(-1); // previousGameMode
                    joinGame.writeBoolean(false); // isDebug
                    joinGame.writeBoolean(false); // isFlat
                    joinGame.writeBoolean(false); // hasDeath
                    joinGame.writeVarInt(0); // portalCooldown
                    fake.send(new MinecraftPacket(0x29, joinGame.bytes()));
                    assertTrue(events.joined.await(5, TimeUnit.SECONDS),
                            "Join Game が処理されませんでした。");
                    assertEquals(99, events.entityId[0]);
                    // オーバーワールドの次元情報：minY -64、高さ 384。
                    assertTrue(events.dimensionInfos.stream()
                                    .anyMatch(d -> d[0] == -64 && d[1] == 384),
                            "オーバーワールドの次元情報が届くこと: " + events.dimensionInfos);

                    // Chunk Data（0x25）→ onChunk にペイロードそのまま届く。
                    byte[] chunk = TestJavaChunk.build765(3, -2,
                            BedrockChunkTranslatorTest.TERRAIN,
                            BedrockChunkTranslatorTest.TWO_BIOMES);
                    fake.send(new MinecraftPacket(0x25, chunk));
                    assertTrue(events.chunkSeen.await(5, TimeUnit.SECONDS),
                            "Chunk Data が届きませんでした。");
                    assertEquals(chunk.length, events.lastChunkPayloadLength);
                    assertTrue(java.util.Arrays.equals(chunk, events.lastChunkPayload),
                            "チャンクのペイロードがそのまま届くこと");

                    // Block Change（0x09）：（8, 64, 8）に草ブロック。
                    PacketIO.Writer blockChange = new PacketIO.Writer();
                    long packed = ((8L & 0x3FFFFFF) << 38)
                            | (((8L) & 0x3FFFFFF) << 12)
                            | (64L & 0xFFF);
                    blockChange.writePositionRaw(packed);
                    blockChange.writeVarInt(8);
                    fake.send(new MinecraftPacket(0x09, blockChange.bytes()));
                    Thread.sleep(300);
                    assertTrue(events.blockChanges.stream().anyMatch(
                                    b -> b[0] == 8 && b[1] == 64 && b[2] == 8 && b[3] == 8),
                            "Block Change が届くこと: " + events.blockChanges);

                    // Multi Block Change（0x47）：セクション（3, 4, -2）に 2 ブロック。
                    PacketIO.Writer multi = new PacketIO.Writer();
                    long sectionPos = ((3L & 0x3FFFFF) << 42)
                            | (((-2L) & 0x3FFFFF) << 20)
                            | (4L & 0xFFFFF);
                    multi.writeLong(sectionPos);
                    multi.writeVarInt(2);
                    multi.writeVarInt(8 << 12); // （48, 64, -32）に草
                    multi.writeVarInt((14 << 12) | 1); // （49, 64, -32）に丸石
                    fake.send(new MinecraftPacket(0x47, multi.bytes()));
                    Thread.sleep(300);
                    assertTrue(events.blockChanges.stream().anyMatch(
                                    b -> b[0] == 48 && b[1] == 64 && b[2] == -32 && b[3] == 8),
                            "Multi Block Change の 1 個目が届くこと: " + events.blockChanges);
                    assertTrue(events.blockChanges.stream().anyMatch(
                                    b -> b[0] == 49 && b[1] == 64 && b[2] == -32 && b[3] == 14),
                            "Multi Block Change の 2 個目が届くこと: " + events.blockChanges);

                    // Respawn（0x45）でネザーへ → 次元情報が minY 0・高さ 128 に変わる。
                    PacketIO.Writer respawn = new PacketIO.Writer();
                    respawn.writeString("minecraft:the_nether");
                    respawn.writeString("minecraft:the_nether");
                    respawn.writeLong(0L);
                    respawn.writeByte(0);
                    respawn.writeByte(-1);
                    respawn.writeBoolean(false);
                    respawn.writeBoolean(false);
                    respawn.writeBoolean(false);
                    respawn.writeVarInt(0);
                    fake.send(new MinecraftPacket(0x45, respawn.bytes()));
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

    private static boolean chatsContains(ListenerEvents events, String message) {
        return events.chats.contains(message);
    }

    @Test
    void textComponentExtraction() {
        // NBT コンポーネント。
        assertEquals("hello", JavaBackendConnection.extractTextComponent(
                textComponentNbt("hello")));
        // JSON 文字列コンポーネント（763 / 764）。
        assertEquals("hi", JavaBackendConnection.extractTextComponent(
                "{\"text\":\"hi\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        assertEquals("", JavaBackendConnection.extractTextComponent(new byte[0]));
        assertEquals("", JavaBackendConnection.extractTextComponent(null));
    }
}
