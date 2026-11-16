// Copyright (c) 2026 DetergentMC contributors.
// SPDX-License-Identifier: Apache-2.0

package io.detergentmc.bedrock;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.detergentmc.protocol.MinecraftPacket;
import io.detergentmc.protocol.Nbt;
import io.detergentmc.protocol.PacketIO;
import io.detergentmc.protocol.PlayTranslator;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.LinkedHashMap;
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
 * Bedrock → 内部 757（Java 1.18／1.18.1）サーバーのテスト。
 * 偽 Java サーバー（757 役）とのログイン〜プレイの流れを確認する。
 * 757 は設定フェーズが無い（Login Success の後そのままプレイに入る。
 * 759〜763 と同じ）。Join Game は 0x26、dimension は DimensionType の
 * 値そのもの（NBT）。チャットは 0x0F（受信）／0x03（送信）で署名なし。
 * Player Info は 0x36 で、add_player に crypto（公開鍵・署名）が無い
 * （1.19 で追加）。モブスポーンは 0x02（Living）／0x03（Painting）と
 * 0x00（Spawn Entity）に分かれている（1.19 で統合）。
 * エンティティは 757 の 113 件（ブタ 64、ガスト 30、絵画 60）。
 * アイテムは 1,101 件（オークのボート 670）。エフェクトは 1 ベース 32 件
 * （Speed=1）。パーティクルは 88 件。絵画は 26 件（donkey_kong は 25）。
 */
class JavaBackendConnection757Test {

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
            }, "fake-java-server-reader-757");
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
        final CountDownLatch chat = new CountDownLatch(1);
        final CountDownLatch spawnEntity = new CountDownLatch(1);
        final CountDownLatch paintingMetadata = new CountDownLatch(1);
        final int[] entityId = {-1};
        final List<int[]> dimensionInfos = new ArrayList<>();
        final List<List<String>> dimensionBiomes = new ArrayList<>();
        final List<String> disconnectReasons = new ArrayList<>();
        final List<String> addedPlayerNames = new ArrayList<>();
        final List<Integer> addedPlayerGamemodes = new ArrayList<>();
        final List<String> chatSenders = new ArrayList<>();
        final List<String> chatMessages = new ArrayList<>();
        final List<BedrockEntityTranslator.SpawnEntity> spawns = new ArrayList<>();
        final List<BedrockEntityTranslator.JavaMetadata> metadatas = new ArrayList<>();

        @Override
        public void onPlayerInfoAdd(UUID uuid, String name, int gamemode) {
            addedPlayerNames.add(name);
            addedPlayerGamemodes.add(gamemode);
            playerInfoAdd.countDown();
        }

        @Override
        public void onJoined(int javaEntityId) {
            entityId[0] = javaEntityId;
            joined.countDown();
        }

        @Override
        public void onSpawnPosition(double x, double y, double z) {
            // 757テストでは使わない。
        }

        @Override
        public void onChat(String sender, String message) {
            chatSenders.add(sender);
            chatMessages.add(message);
            chat.countDown();
        }

        @Override
        public void onTeleport(double x, double y, double z, float yaw, float pitch) {
            // 757テストではテレポートを使わない。
        }

        @Override
        public void onDimensionInfo(int minY, int height, List<String> biomeNames) {
            dimensionInfos.add(new int[]{minY, height});
            dimensionBiomes.add(biomeNames);
            dimensionInfo.countDown();
        }

        @Override
        public void onSpawnEntity(BedrockEntityTranslator.SpawnEntity spawn) {
            spawns.add(spawn);
            spawnEntity.countDown();
        }

        @Override
        public void onEntityMetadata(BedrockEntityTranslator.JavaMetadata metadata) {
            metadatas.add(metadata);
            paintingMetadata.countDown();
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
        }, "fake-java-server-accept-757");
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

    /** 757 の Login Success ペイロード（UUID・名前・空の属性。strictErrorHandling は無い）。 */
    private static byte[] loginSuccess757(String name, UUID uuid) {
        PacketIO.Writer writer = new PacketIO.Writer();
        writer.writeString(name);
        writer.writeUuid(uuid);
        writer.writeVarInt(0); // properties（空）
        return writer.bytes();
    }

    /** オーバーワールドの DimensionType element（min_y -64、高さ 384）。 */
    static Nbt.Tag.CompoundTag overworldElement() {
        Map<String, Nbt.Tag> element = new LinkedHashMap<>();
        element.put("min_y", new Nbt.Tag.IntTag(-64));
        element.put("height", new Nbt.Tag.IntTag(384));
        element.put("effects", new Nbt.Tag.StringTag("minecraft:overworld"));
        element.put("has_ceiling", new Nbt.Tag.ByteTag((byte) 0));
        return new Nbt.Tag.CompoundTag(element);
    }

    /** ネザーの DimensionType element（min_y 0、高さ 128）。 */
    static Nbt.Tag.CompoundTag netherElement() {
        Map<String, Nbt.Tag> element = new LinkedHashMap<>();
        element.put("min_y", new Nbt.Tag.IntTag(0));
        element.put("height", new Nbt.Tag.IntTag(128));
        element.put("effects", new Nbt.Tag.StringTag("minecraft:the_nether"));
        element.put("has_ceiling", new Nbt.Tag.ByteTag((byte) 1));
        return new Nbt.Tag.CompoundTag(element);
    }

    /** 757 の dimensionCodec（dimension_type＋worldgen/biome の最小構成）。 */
    private static Nbt.Tag.CompoundTag codec757() {
        Nbt.Tag.CompoundTag overworld = overworldElement();
        Nbt.Tag.CompoundTag nether = netherElement();
        Map<String, Nbt.Tag> overworldEntry = new LinkedHashMap<>();
        overworldEntry.put("name", new Nbt.Tag.StringTag("minecraft:overworld"));
        overworldEntry.put("id", new Nbt.Tag.IntTag(0));
        overworldEntry.put("element", overworld);
        Map<String, Nbt.Tag> netherEntry = new LinkedHashMap<>();
        netherEntry.put("name", new Nbt.Tag.StringTag("minecraft:the_nether"));
        netherEntry.put("id", new Nbt.Tag.IntTag(1));
        netherEntry.put("element", nether);
        Map<String, Nbt.Tag> dimensionRegistry = new LinkedHashMap<>();
        dimensionRegistry.put("type", new Nbt.Tag.StringTag("minecraft:dimension_type"));
        dimensionRegistry.put("value", new Nbt.Tag.ListTag(Nbt.COMPOUND,
                List.of(new Nbt.Tag.CompoundTag(overworldEntry),
                        new Nbt.Tag.CompoundTag(netherEntry))));
        Map<String, Nbt.Tag> plainsEntry = new LinkedHashMap<>();
        plainsEntry.put("name", new Nbt.Tag.StringTag("minecraft:plains"));
        plainsEntry.put("id", new Nbt.Tag.IntTag(1));
        plainsEntry.put("element", new Nbt.Tag.CompoundTag(Map.of()));
        Map<String, Nbt.Tag> desertEntry = new LinkedHashMap<>();
        desertEntry.put("name", new Nbt.Tag.StringTag("minecraft:desert"));
        desertEntry.put("id", new Nbt.Tag.IntTag(5));
        desertEntry.put("element", new Nbt.Tag.CompoundTag(Map.of()));
        Map<String, Nbt.Tag> biomeRegistry = new LinkedHashMap<>();
        biomeRegistry.put("type", new Nbt.Tag.StringTag("minecraft:worldgen/biome"));
        biomeRegistry.put("value", new Nbt.Tag.ListTag(Nbt.COMPOUND,
                List.of(new Nbt.Tag.CompoundTag(plainsEntry),
                        new Nbt.Tag.CompoundTag(desertEntry))));
        Map<String, Nbt.Tag> codec = new LinkedHashMap<>();
        codec.put("minecraft:dimension_type", new Nbt.Tag.CompoundTag(dimensionRegistry));
        codec.put("minecraft:worldgen/biome", new Nbt.Tag.CompoundTag(biomeRegistry));
        return new Nbt.Tag.CompoundTag(codec);
    }

    /** 757 の Join Game ペイロード（entityId 42、オーバーワールド）。 */
    private static byte[] joinGame757() {
        return PlayTranslator.writeJoinGame757(new PlayTranslator.JoinGame757(
                42, false, 0, -1, List.of("minecraft:overworld"), codec757(),
                overworldElement(), "minecraft:overworld", 123L, 20, 10, 10,
                false, true, false, false));
    }

    /** 757 の Respawn ペイロード（ネザーの element、worldName はネザー）。 */
    private static byte[] respawn757ToNether() {
        PacketIO.Writer writer = new PacketIO.Writer();
        writer.writeNamedNbt("", netherElement());
        writer.writeString("minecraft:the_nether");
        writer.writeLong(123L);
        writer.writeByte(0); // gameMode
        writer.writeByte(-1); // previousGameMode（u8 の -1＝255 として書く。読む側は u8）
        writer.writeBoolean(false); // isDebug
        writer.writeBoolean(false); // isFlat
        writer.writeBoolean(false); // copyMetadata
        return writer.bytes();
    }

    /**
     * 757 は設定フェーズが無い：Login Success の後、Login Acknowledged や
     * Client Information を送らず、そのまま Join Game（0x26）でプレイに入る。
     * Join Game の codec から次元（minY -64、高さ 384）とバイオーム名
     * （平原 1、砂漠 5）が決まること。
     */
    @Test
    void login757GoesStraightToPlay() throws Exception {
        try (ServerSocket serverSocket = new ServerSocket(0, 1,
                java.net.InetAddress.getByName("127.0.0.1"))) {
            int port = serverSocket.getLocalPort();
            AtomicReference<FakeJavaServer> holder = new AtomicReference<>();
            AtomicReference<Exception> error = new AtomicReference<>();
            acceptInBackground(serverSocket, holder, error);

            ListenerEvents events = new ListenerEvents();
            UUID uuid = UUID.fromString("11111111-2222-3333-4444-555555555555");
            JavaBackendConnection backend = new JavaBackendConnection(
                    "127.0.0.1", port, 757, ".Steve", uuid, events);
            backend.connect();
            try {
                FakeJavaServer server = waitForServer(holder, error);
                try (FakeJavaServer fake = server) {
                    assertNotNull(fake.waitForId(0x00, 5_000), "ハンドシェイクが届きませんでした。");
                    fake.send(new MinecraftPacket(0x02, loginSuccess757(".Steve", uuid)));
                    // 757 には設定フェーズが無い：少し待っても何も送ってこないこと。
                    Thread.sleep(500);
                    assertTrue(fake.received.isEmpty(),
                            "757 は Login Success の後に何も送らないはずです。");
                    // Join Game は757では 0x26。
                    fake.send(new MinecraftPacket(0x26, joinGame757()));
                    assertTrue(events.joined.await(5, TimeUnit.SECONDS),
                            "Join Game が処理されませんでした。");
                    assertEquals(42, events.entityId[0]);
                    assertTrue(events.dimensionInfo.await(5, TimeUnit.SECONDS),
                            "次元情報が決まりませんでした。");
                    assertArrayEquals(new int[]{-64, 384}, events.dimensionInfos.get(0));
                    assertEquals("minecraft:plains", events.dimensionBiomes.get(0).get(1));
                    assertEquals("minecraft:desert", events.dimensionBiomes.get(0).get(5));
                }
            } finally {
                backend.close();
            }
        }
    }

    /**
     * 757 のチャット送信は 0x03 の {message} だけ（署名なし。1.19 で
     * Chat Message／Chat Command に分離される前の形式）。
     */
    @Test
    void sendChat757SendsPlainMessage() throws Exception {
        try (ServerSocket serverSocket = new ServerSocket(0, 1,
                java.net.InetAddress.getByName("127.0.0.1"))) {
            int port = serverSocket.getLocalPort();
            AtomicReference<FakeJavaServer> holder = new AtomicReference<>();
            AtomicReference<Exception> error = new AtomicReference<>();
            acceptInBackground(serverSocket, holder, error);

            ListenerEvents events = new ListenerEvents();
            UUID uuid = UUID.fromString("11111111-2222-3333-4444-555555555555");
            JavaBackendConnection backend = new JavaBackendConnection(
                    "127.0.0.1", port, 757, ".Steve", uuid, events);
            backend.connect();
            try {
                FakeJavaServer server = waitForServer(holder, error);
                try (FakeJavaServer fake = server) {
                    loginToPlay(fake, events, uuid);
                    backend.sendChat("hello bedrock");
                    MinecraftPacket chat = fake.waitForId(0x03, 5_000);
                    assertNotNull(chat, "チャットが届きませんでした。");
                    PacketIO.Reader reader = new PacketIO.Reader(chat.payload());
                    assertEquals("hello bedrock", reader.readString());
                    assertEquals(0, reader.remaining(), "757 のチャットは文字列だけのはずです。");
                }
            } finally {
                backend.close();
            }
        }
    }

    /** 757 には Chat Command が無いため、コマンドは "/" 付きのチャット（0x03）で送る。 */
    @Test
    void sendCommand757SendsSlashChat() throws Exception {
        try (ServerSocket serverSocket = new ServerSocket(0, 1,
                java.net.InetAddress.getByName("127.0.0.1"))) {
            int port = serverSocket.getLocalPort();
            AtomicReference<FakeJavaServer> holder = new AtomicReference<>();
            AtomicReference<Exception> error = new AtomicReference<>();
            acceptInBackground(serverSocket, holder, error);

            ListenerEvents events = new ListenerEvents();
            UUID uuid = UUID.fromString("11111111-2222-3333-4444-555555555555");
            JavaBackendConnection backend = new JavaBackendConnection(
                    "127.0.0.1", port, 757, ".Steve", uuid, events);
            backend.connect();
            try {
                FakeJavaServer server = waitForServer(holder, error);
                try (FakeJavaServer fake = server) {
                    loginToPlay(fake, events, uuid);
                    backend.sendCommand("help");
                    MinecraftPacket chat = fake.waitForId(0x03, 5_000);
                    assertNotNull(chat, "コマンドが届きませんでした。");
                    PacketIO.Reader reader = new PacketIO.Reader(chat.payload());
                    assertEquals("/help", reader.readString());
                    assertEquals(0, reader.remaining());
                }
            } finally {
                backend.close();
            }
        }
    }

    /**
     * 757 の Player Info（0x36）は旧式（action 付き）で、add_player に
     * crypto（公開鍵・署名）が無い。crypto を読もうとすると後続がずれ、
     * add player が処理されない。続けて送るチャット（0x0F）では
     * Player Info で覚えた名前で送信者が出ること。
     */
    @Test
    void playerInfo757HasNoCryptoAndChatUsesName() throws Exception {
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
                    "127.0.0.1", port, 757, ".Steve", uuid, events);
            backend.connect();
            try {
                FakeJavaServer server = waitForServer(holder, error);
                try (FakeJavaServer fake = server) {
                    loginToPlay(fake, events, uuid);

                    // Player Info（0x36）: action 0（add player）、crypto 節は無い。
                    PacketIO.Writer info = new PacketIO.Writer();
                    info.writeVarInt(0); // add player
                    info.writeVarInt(1);
                    info.writeUuid(other);
                    info.writeString("Alex");
                    info.writeVarInt(0); // properties（空）
                    info.writeVarInt(1); // gamemode: クリエイティブ
                    info.writeVarInt(50); // ping
                    info.writeBoolean(false); // display name なし
                    fake.send(new MinecraftPacket(0x36, info.bytes()));
                    assertTrue(events.playerInfoAdd.await(5, TimeUnit.SECONDS),
                            "Player Info の add player が処理されませんでした。");
                    assertEquals(List.of("Alex"), events.addedPlayerNames);
                    assertEquals(List.of(1), events.addedPlayerGamemodes);

                    // チャット（0x0F）: {message, position, sender}。
                    PacketIO.Writer chat = new PacketIO.Writer();
                    chat.writeString("{\"text\":\"hi\"}");
                    chat.writeByte(0); // position: chat
                    chat.writeUuid(other);
                    fake.send(new MinecraftPacket(0x0F, chat.bytes()));
                    assertTrue(events.chat.await(5, TimeUnit.SECONDS),
                            "チャットが処理されませんでした。");
                    assertEquals(List.of("Alex"), events.chatSenders);
                    assertEquals(List.of("hi"), events.chatMessages);
                }
            } finally {
                backend.close();
            }
        }
    }

    /** 757 のモブスポーン（0x02）は yaw→pitch の順で、objectData は無い。ブタ 64→73。 */
    @Test
    void spawnEntityLiving757Dispatches() throws Exception {
        try (ServerSocket serverSocket = new ServerSocket(0, 1,
                java.net.InetAddress.getByName("127.0.0.1"))) {
            int port = serverSocket.getLocalPort();
            AtomicReference<FakeJavaServer> holder = new AtomicReference<>();
            AtomicReference<Exception> error = new AtomicReference<>();
            acceptInBackground(serverSocket, holder, error);

            ListenerEvents events = new ListenerEvents();
            UUID uuid = UUID.fromString("11111111-2222-3333-4444-555555555555");
            UUID mob = UUID.fromString("33333333-4444-5555-6666-777777777777");
            JavaBackendConnection backend = new JavaBackendConnection(
                    "127.0.0.1", port, 757, ".Steve", uuid, events);
            backend.connect();
            try {
                FakeJavaServer server = waitForServer(holder, error);
                try (FakeJavaServer fake = server) {
                    loginToPlay(fake, events, uuid);
                    PacketIO.Writer spawn = new PacketIO.Writer();
                    spawn.writeVarInt(7); // entityId
                    spawn.writeUuid(mob);
                    spawn.writeVarInt(64); // pig（757）
                    spawn.writeDouble(10.5);
                    spawn.writeDouble(65.0);
                    spawn.writeDouble(-20.5);
                    spawn.writeByte(64); // yaw
                    spawn.writeByte(32); // pitch
                    spawn.writeByte(64); // headPitch
                    spawn.writeShort(0); // velocity x
                    spawn.writeShort(0); // velocity y
                    spawn.writeShort(0); // velocity z
                    fake.send(new MinecraftPacket(0x02, spawn.bytes()));
                    assertTrue(events.spawnEntity.await(5, TimeUnit.SECONDS),
                            "Spawn Entity Living が処理されませんでした。");
                    BedrockEntityTranslator.SpawnEntity got = events.spawns.get(0);
                    assertEquals(7, got.entityId());
                    assertEquals(73, got.type765(), "757 のブタ 64 は 765 のブタ 73");
                    assertEquals(10.5, got.x());
                    assertEquals(65.0, got.y());
                    assertEquals(-20.5, got.z());
                    assertEquals(0, got.objectData(), "Living には objectData が無い");
                }
            } finally {
                backend.close();
            }
        }
    }

    /**
     * 757 の絵画スポーン（0x03）は title（varint）＋位置＋向き。
     * donkey_kong は 757 の 25 → 765 の 29。スポーン直後に題名の
     * 合成メタデータも送られること。
     */
    @Test
    void spawnEntityPainting757Dispatches() throws Exception {
        try (ServerSocket serverSocket = new ServerSocket(0, 1,
                java.net.InetAddress.getByName("127.0.0.1"))) {
            int port = serverSocket.getLocalPort();
            AtomicReference<FakeJavaServer> holder = new AtomicReference<>();
            AtomicReference<Exception> error = new AtomicReference<>();
            acceptInBackground(serverSocket, holder, error);

            ListenerEvents events = new ListenerEvents();
            UUID uuid = UUID.fromString("11111111-2222-3333-4444-555555555555");
            UUID painting = UUID.fromString("44444444-5555-6666-7777-888888888888");
            JavaBackendConnection backend = new JavaBackendConnection(
                    "127.0.0.1", port, 757, ".Steve", uuid, events);
            backend.connect();
            try {
                FakeJavaServer server = waitForServer(holder, error);
                try (FakeJavaServer fake = server) {
                    loginToPlay(fake, events, uuid);
                    long packed = ((10L & 0x3FFFFFF) << 38)
                            | ((-20L & 0x3FFFFFF) << 12) | (64L & 0xFFF);
                    PacketIO.Writer spawn = new PacketIO.Writer();
                    spawn.writeVarInt(9); // entityId
                    spawn.writeUuid(painting);
                    spawn.writeVarInt(25); // title: donkey_kong（757）
                    spawn.writeLong(packed);
                    spawn.writeByte(2); // direction
                    fake.send(new MinecraftPacket(0x03, spawn.bytes()));
                    assertTrue(events.spawnEntity.await(5, TimeUnit.SECONDS),
                            "Spawn Entity Painting が処理されませんでした。");
                    BedrockEntityTranslator.SpawnEntity got = events.spawns.get(0);
                    assertEquals(9, got.entityId());
                    assertEquals(69, got.type765(), "絵画は 765 の 69");
                    assertEquals(10.5, got.x());
                    assertEquals(64.5, got.y());
                    assertEquals(-19.5, got.z());
                    assertEquals(2, got.objectData(), "向きが objectData に入る");
                    assertTrue(events.paintingMetadata.await(5, TimeUnit.SECONDS),
                            "絵画の題名メタデータが送られませんでした。");
                    assertEquals("donkey_kong",
                            events.metadatas.get(0).paintingVariant());
                }
            } finally {
                backend.close();
            }
        }
    }

    /** 757 の Respawn（0x3D）は dimension が NBT の値そのもの。ネザーに行くと次元が変わること。 */
    @Test
    void respawn757ReadsDimensionName() throws Exception {
        try (ServerSocket serverSocket = new ServerSocket(0, 1,
                java.net.InetAddress.getByName("127.0.0.1"))) {
            int port = serverSocket.getLocalPort();
            AtomicReference<FakeJavaServer> holder = new AtomicReference<>();
            AtomicReference<Exception> error = new AtomicReference<>();
            acceptInBackground(serverSocket, holder, error);

            ListenerEvents events = new ListenerEvents();
            UUID uuid = UUID.fromString("11111111-2222-3333-4444-555555555555");
            JavaBackendConnection backend = new JavaBackendConnection(
                    "127.0.0.1", port, 757, ".Steve", uuid, events);
            backend.connect();
            try {
                FakeJavaServer server = waitForServer(holder, error);
                try (FakeJavaServer fake = server) {
                    loginToPlay(fake, events, uuid);
                    events.dimensionInfos.clear();
                    events.dimensionBiomes.clear();
                    fake.send(new MinecraftPacket(0x3D, respawn757ToNether()));
                    // Respawn は onDimensionInfo をもう一度呼ぶ（ラッチは
                    // Join Game で既に開いているため、リストの件数で待つ）。
                    boolean respawned = false;
                    long deadline = System.currentTimeMillis() + 5_000;
                    while (System.currentTimeMillis() < deadline) {
                        if (!events.dimensionInfos.isEmpty()) {
                            respawned = true;
                            break;
                        }
                        Thread.sleep(50);
                    }
                    assertTrue(respawned, "Respawn の次元情報が決まりませんでした。");
                    assertArrayEquals(new int[]{0, 128}, events.dimensionInfos.get(0));
                }
            } finally {
                backend.close();
            }
        }
    }

    /** ハンドシェイク〜Join Game まで進める（ソケットテストの共通手順）。 */
    private static void loginToPlay(FakeJavaServer fake, ListenerEvents events, UUID uuid)
            throws Exception {
        assertNotNull(fake.waitForId(0x00, 5_000), "ハンドシェイクが届きませんでした。");
        fake.send(new MinecraftPacket(0x02, loginSuccess757(".Steve", uuid)));
        fake.send(new MinecraftPacket(0x26, joinGame757()));
        assertTrue(events.joined.await(5, TimeUnit.SECONDS), "Join Game が処理されませんでした。");
    }

    // ---- ソケット不要の写像・ディスパッチ ----

    /** 757 のブタ（64）は 765 のブタ（73）に正規化される。 */
    @Test
    void entityType757NormalizesPigTo765() {
        assertEquals(73, BedrockEntityTranslator.normalizeEntityType(64, 757));
    }

    /** 757 のガスト（30）は 765 のガスト（42）に正規化される。 */
    @Test
    void entityType757NormalizesGhastTo765() {
        assertEquals(42, BedrockEntityTranslator.normalizeEntityType(30, 757));
    }

    /** 757 の絵画（60）は 765 の絵画（69）に正規化される。 */
    @Test
    void entityType757NormalizesPaintingTo765() {
        assertEquals(69, BedrockEntityTranslator.normalizeEntityType(60, 757));
    }

    /** 757 の範囲外のエンティティはブタ（73）に落とす。 */
    @Test
    void entityType757OutOfRangeFallsBackToPig() {
        assertEquals(73, BedrockEntityTranslator.normalizeEntityType(999, 757));
    }

    /** 757 の oak_boat（670）は 765 の oak_boat（773）に正規化される。 */
    @Test
    void slot757NormalizesOakBoatTo765() {
        PacketIO.Writer writer = new PacketIO.Writer();
        writer.writeBoolean(true); // present
        writer.writeVarInt(670); // oak_boat（757）
        writer.writeByte(1); // count
        writer.writeByte(0); // NBT なし（END）
        BedrockEntityTranslator.JavaSlot slot =
                BedrockEntityTranslator.readSlot(new PacketIO.Reader(writer.bytes()), 757);
        assertTrue(slot.present());
        assertEquals(773, slot.item765());
    }

    /** 757 の music_disc_cat（1016）は 765 の music_disc_cat（1162）。 */
    @Test
    void slot757NormalizesMusicDiscCatTo765() {
        PacketIO.Writer writer = new PacketIO.Writer();
        writer.writeBoolean(true);
        writer.writeVarInt(1016); // music_disc_cat（757）
        writer.writeByte(1);
        writer.writeByte(0);
        BedrockEntityTranslator.JavaSlot slot =
                BedrockEntityTranslator.readSlot(new PacketIO.Reader(writer.bytes()), 757);
        assertTrue(slot.present());
        assertEquals(1162, slot.item765());
    }

    /** 757 の範囲外のアイテムは石（1）に落とす。 */
    @Test
    void slot757OutOfRangeFallsBackToStone() {
        PacketIO.Writer writer = new PacketIO.Writer();
        writer.writeBoolean(true);
        writer.writeVarInt(99999);
        writer.writeByte(1);
        writer.writeByte(0);
        BedrockEntityTranslator.JavaSlot slot =
                BedrockEntityTranslator.readSlot(new PacketIO.Reader(writer.bytes()), 757);
        assertTrue(slot.present());
        assertEquals(1, slot.item765());
    }

    /** 757 のエフェクトは 1 ベース（Speed=1）で、765 は 0 ベース（Speed=0）。 */
    @Test
    void effect757NormalizesOneBasedToZeroBased() {
        assertEquals(0, BedrockEntityTranslator.normalizeEffectTo765(1, 757));
        assertEquals(31, BedrockEntityTranslator.normalizeEffectTo765(32, 757));
        assertEquals(0, BedrockEntityTranslator.normalizeEffectTo765(33, 757));
    }

    /** 757 の angry_villager（1）は 765 の angry_villager（1）。範囲外は 0。 */
    @Test
    void particle757NormalizesTo765() {
        assertEquals(1, BedrockEntityTranslator.mapParticle757To765(1));
        assertEquals(0, BedrockEntityTranslator.mapParticle757To765(88));
    }

    /** 757 の絵画の題名：0〜24 は同じ、donkey_kong は 25→29、範囲外は 0。 */
    @Test
    void painting757MapsDonkeyKongTo29() {
        assertEquals(0, BedrockEntityTranslator.paintingId757To765(0));
        assertEquals(24, BedrockEntityTranslator.paintingId757To765(24));
        assertEquals(29, BedrockEntityTranslator.paintingId757To765(25));
        assertEquals(0, BedrockEntityTranslator.paintingId757To765(26));
    }

    /** 757 の Spawn Entity（0x00）は headPitch が無く、yaw で代用する。objectData は i32。 */
    @Test
    void spawnEntity757ParsesWithoutHeadPitch() {
        UUID mob = UUID.fromString("33333333-4444-5555-6666-777777777777");
        PacketIO.Writer writer = new PacketIO.Writer();
        writer.writeVarInt(7);
        writer.writeUuid(mob);
        writer.writeVarInt(64); // pig（757）
        writer.writeDouble(10.5);
        writer.writeDouble(65.0);
        writer.writeDouble(-20.5);
        writer.writeByte(64); // pitch
        writer.writeByte(32); // yaw
        writer.writeInt(12345); // objectData（i32）
        writer.writeShort(100); // velocity x
        writer.writeShort(0);
        writer.writeShort(-50);
        BedrockEntityTranslator.SpawnEntity spawn =
                BedrockEntityTranslator.parseSpawnEntity(writer.bytes(), 757);
        assertEquals(7, spawn.entityId());
        assertEquals(73, spawn.type765());
        // 角度はバイト（256＝360°）から度に直される。pitch 64→90°、yaw 32→45°。
        assertEquals(90.0f, spawn.pitch());
        assertEquals(45.0f, spawn.yaw());
        assertEquals(45.0f, spawn.headPitch(), "757 には headPitch が無いため yaw で代用する");
        assertEquals(12345, spawn.objectData());
    }

    /** 757 の Spawn Entity Living（0x02）は yaw→pitch の順。objectData は無い。 */
    @Test
    void spawnEntityLiving757ParsesYawBeforePitch() {
        UUID mob = UUID.fromString("33333333-4444-5555-6666-777777777777");
        PacketIO.Writer writer = new PacketIO.Writer();
        writer.writeVarInt(7);
        writer.writeUuid(mob);
        writer.writeVarInt(64); // pig（757）
        writer.writeDouble(10.5);
        writer.writeDouble(65.0);
        writer.writeDouble(-20.5);
        writer.writeByte(64); // yaw
        writer.writeByte(32); // pitch
        writer.writeByte(16); // headPitch
        writer.writeShort(0);
        writer.writeShort(0);
        writer.writeShort(0);
        BedrockEntityTranslator.SpawnEntity spawn =
                BedrockEntityTranslator.parseSpawnEntityLiving(writer.bytes(), 757);
        assertEquals(7, spawn.entityId());
        assertEquals(73, spawn.type765());
        // 角度は度に直される。yaw 64→90°、pitch 32→45°、headPitch 16→22.5°。
        assertEquals(90.0f, spawn.yaw());
        assertEquals(45.0f, spawn.pitch());
        assertEquals(22.5f, spawn.headPitch());
        assertEquals(0, spawn.objectData());
    }

    /** 757 の Spawn Entity Painting（0x03）は title（varint）＋位置＋向き。 */
    @Test
    void spawnPainting757ParsesTitle() {
        UUID painting = UUID.fromString("44444444-5555-6666-7777-888888888888");
        long packed = ((10L & 0x3FFFFFF) << 38)
                | ((-20L & 0x3FFFFFF) << 12) | (64L & 0xFFF);
        PacketIO.Writer writer = new PacketIO.Writer();
        writer.writeVarInt(9);
        writer.writeUuid(painting);
        writer.writeVarInt(25); // donkey_kong（757）
        writer.writeLong(packed);
        writer.writeByte(2); // direction
        BedrockEntityTranslator.SpawnPainting got =
                BedrockEntityTranslator.parseSpawnEntityPainting(writer.bytes());
        assertEquals(9, got.entityId());
        assertEquals(25, got.title757());
        assertEquals(2, got.direction());
        assertEquals(packed, got.positionRaw());
        assertEquals(29, BedrockEntityTranslator.paintingId757To765(got.title757()));
    }

    /**
     * 757 のメタデータ型（763 以降とは番号が違う）：2=f32、3=string、18=pose。
     * 763 の番号で読むと string が float として読まれるため、757 分岐が要る。
     */
    @Test
    void metadata757TypesReadCorrectly() {
        PacketIO.Writer writer = new PacketIO.Writer();
        writer.writeVarInt(7); // entityId
        writer.writeByte(0); // index 0: flags
        writer.writeVarInt(0); // type 0: byte
        writer.writeByte(1);
        writer.writeByte(2); // index 2: custom name
        writer.writeVarInt(3); // type 3: string（757）
        writer.writeString("Alex");
        writer.writeByte(6); // index 6: pose（757 でも 763 でも 6）
        writer.writeVarInt(18); // type 18: pose varint（757）
        writer.writeVarInt(1); // fall_flying
        writer.writeByte(0xFF); // 終端
        BedrockEntityTranslator.JavaMetadata meta =
                BedrockEntityTranslator.parseMetadata(writer.bytes(), 757, 73);
        assertEquals(7, meta.entityId());
        assertEquals(1, meta.flags());
        assertEquals("Alex", meta.customName());
        assertEquals(1, meta.pose());
    }

    /** 757 の Update Attributes はキーが文字列、modifier の UUID は 16 バイト。 */
    @Test
    void updateAttributes757UsesStringKeysAndByteUuid() {
        PacketIO.Writer writer = new PacketIO.Writer();
        writer.writeVarInt(7); // entityId
        writer.writeVarInt(1); // count
        writer.writeString("minecraft:generic.movement_speed"); // 識別子文字列
        writer.writeDouble(0.1);
        writer.writeVarInt(1); // modifierCount
        writer.writeUuid(UUID.fromString("123e4567-e89b-12d3-a456-426614174000"));
        writer.writeDouble(0.0);
        writer.writeByte(0);
        BedrockEntityTranslator.UpdateAttributes attrs =
                BedrockEntityTranslator.parseUpdateAttributes(writer.bytes(), 757);
        assertEquals(7, attrs.entityId());
        assertEquals(0.1, attrs.movementSpeed());
        assertNull(attrs.maxHealth());
    }

    /** 757 と 759 の Client Information ペイロードは同一（particleStatus は 768 から）。 */
    @Test
    void clientSettings757Equals759() {
        assertArrayEquals(JavaBackendConnection.clientSettingsPayload(759),
                JavaBackendConnection.clientSettingsPayload(757));
        // 768 は末尾に particleStatus（varint 0）が 1 バイト付く。
        assertEquals(JavaBackendConnection.clientSettingsPayload(757).length + 1,
                JavaBackendConnection.clientSettingsPayload(768).length);
    }

    /** 757 のエフェクト読み：{effectId i8, amplifier i8, duration varint, hideParticles i8}。 */
    @Test
    void entityEffect757ReadsByteEffectId() {
        PacketIO.Writer writer = new PacketIO.Writer();
        writer.writeVarInt(7); // entityId
        writer.writeByte(1); // effectId: Speed（757 は 1 ベース）
        writer.writeByte(0); // amplifier
        writer.writeVarInt(600); // duration
        writer.writeByte(0); // hideParticles: false
        BedrockEntityTranslator.EntityEffect effect =
                BedrockEntityTranslator.parseEntityEffect(writer.bytes(), 757);
        assertEquals(7, effect.entityId());
        assertEquals(0, effect.effect765(), "757 の Speed=1 は 765 の 0");
        assertTrue(effect.showParticles());
    }

    /** 757 の Remove Entity Effect は effectId を byte で読む。 */
    @Test
    void removeEntityEffect757ReadsByteEffectId() {
        PacketIO.Writer writer = new PacketIO.Writer();
        writer.writeVarInt(7); // entityId
        writer.writeByte(1); // effectId: Speed（757 は 1 ベース）
        BedrockEntityTranslator.RemoveEntityEffect removed =
                BedrockEntityTranslator.parseRemoveEntityEffect(writer.bytes(), 757);
        assertEquals(7, removed.entityId());
        assertEquals(0, removed.effect765());
    }
}
