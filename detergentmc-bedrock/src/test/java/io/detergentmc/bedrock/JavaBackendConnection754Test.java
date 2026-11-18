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
 * Bedrock → 内部 754（Java 1.16.5）サーバーのテスト。
 * 偽 Java サーバー（754 役）とのログイン〜プレイの流れを確認する。
 * 754 は設定フェーズが無い（Login Success の後そのままプレイに入る。
 * 757・759〜763 と同じ）。Join Game は 0x24、dimension は DimensionType の
 * 値そのもの（NBT）。チャンクは 16 セクション＋bitMap＋バイオーム int[1024]
 * （BedrockChunkTranslatorTest で確認）。チャットは 0x0E（受信）／0x03（送信）で署名なし。
 * Player Info は 0x32 で、add_player に crypto（公開鍵・署名）が無い
 * （1.19 で追加）。モブスポーンは 0x02（Living）／0x03（Painting）と
 * 0x00（Spawn Entity）に分かれている（1.19 で統合）。
 * エンティティは 754 の 108 件（ブタ 59、ガスト 29、絵画 55）。
 * アイテムは 976 件（オークのボート 667）。エフェクトは 1 ベース 32 件
 * （Speed=1）。パーティクルは 72 件（barrier 2 は 757 の
 * block_marker 3 に統合。light は 1.16.5 には無く、754 の 3 は block）。
 * 絵画は 26 件（donkey_kong は 25）。
 * Client Information は 6 フィールドで、disableTextFiltering は無い。
 */
class JavaBackendConnection754Test {

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
            }, "fake-java-server-reader-754");
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
        final List<Integer> removedEntityIds = new ArrayList<>();
        final CountDownLatch removeEntities = new CountDownLatch(1);

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
            // 754テストでは使わない。
        }

        @Override
        public void onChat(String sender, String message) {
            chatSenders.add(sender);
            chatMessages.add(message);
            chat.countDown();
        }

        @Override
        public void onTeleport(double x, double y, double z, float yaw, float pitch) {
            // 754テストではテレポートを使わない。
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
        public void onRemoveEntities(List<Integer> entityIds) {
            removedEntityIds.addAll(entityIds);
            removeEntities.countDown();
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
        }, "fake-java-server-accept-754");
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

    /** 754 の Login Success ペイロード（UUID・名前・空の属性。strictErrorHandling は無い）。 */
    private static byte[] loginSuccess754(String name, UUID uuid) {
        PacketIO.Writer writer = new PacketIO.Writer();
        writer.writeString(name);
        writer.writeUuid(uuid);
        writer.writeVarInt(0); // properties（空）
        return writer.bytes();
    }

    /**
     * オーバーワールドの DimensionType element（754）。min_y／height は無く、
     * 高さは logical_height 256 で表す（Stage A §11.6 の実バニラ 1.16.5 実測）。
     */
    static Nbt.Tag.CompoundTag overworldElement754() {
        Map<String, Nbt.Tag> element = new LinkedHashMap<>();
        element.put("logical_height", new Nbt.Tag.IntTag(256));
        element.put("effects", new Nbt.Tag.StringTag("minecraft:overworld"));
        element.put("has_ceiling", new Nbt.Tag.ByteTag((byte) 0));
        return new Nbt.Tag.CompoundTag(element);
    }

    /** ネザーの DimensionType element（754）。min_y／height は無く、logical_height 128。 */
    static Nbt.Tag.CompoundTag netherElement754() {
        Map<String, Nbt.Tag> element = new LinkedHashMap<>();
        element.put("logical_height", new Nbt.Tag.IntTag(128));
        element.put("effects", new Nbt.Tag.StringTag("minecraft:the_nether"));
        element.put("has_ceiling", new Nbt.Tag.ByteTag((byte) 1));
        return new Nbt.Tag.CompoundTag(element);
    }

    /** 754 の dimensionCodec（dimension_type＋worldgen/biome の最小構成）。 */
    private static Nbt.Tag.CompoundTag codec754() {
        Nbt.Tag.CompoundTag overworld = overworldElement754();
        Nbt.Tag.CompoundTag nether = netherElement754();
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
        desertEntry.put("id", new Nbt.Tag.IntTag(2));
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

    /** 754 の Join Game ペイロード（entityId 42、オーバーワールド）。 */
    private static byte[] joinGame754() {
        return PlayTranslator.writeJoinGame754(new PlayTranslator.JoinGame754(
                42, false, 0, -1, List.of("minecraft:overworld"), codec754(),
                overworldElement754(), "minecraft:overworld", 123L, 20, 10,
                false, true, false, false));
    }

    /** 754 の Respawn ペイロード（ネザーの element、worldName はネザー）。 */
    private static byte[] respawn754ToNether() {
        PacketIO.Writer writer = new PacketIO.Writer();
        writer.writeNamedNbt("", netherElement754());
        writer.writeString("minecraft:the_nether");
        writer.writeLong(123L);
        writer.writeByte(0); // gameMode
        writer.writeByte(-1); // previousGameMode（u8 の -1＝255 として書く。読む側は u8。754 も 755 と同じ）
        writer.writeBoolean(false); // isDebug
        writer.writeBoolean(false); // isFlat
        writer.writeBoolean(false); // copyMetadata
        return writer.bytes();
    }

    /**
     * 754 は設定フェーズが無い：Login Success の後、Login Acknowledged や
     * Client Information を送らず、そのまま Join Game（0x24）でプレイに入る。
     * Join Game の codec から次元（minY 0、高さ 256）とバイオーム名
     * （平原 1、砂漠 2）が決まること。754 の element には min_y／height が無く、
     * 高さは logical_height（overworld 256）から決まる。
     */
    @Test
    void login754GoesStraightToPlay() throws Exception {
        try (ServerSocket serverSocket = new ServerSocket(0, 1,
                java.net.InetAddress.getByName("127.0.0.1"))) {
            int port = serverSocket.getLocalPort();
            AtomicReference<FakeJavaServer> holder = new AtomicReference<>();
            AtomicReference<Exception> error = new AtomicReference<>();
            acceptInBackground(serverSocket, holder, error);

            ListenerEvents events = new ListenerEvents();
            UUID uuid = UUID.fromString("11111111-2222-3333-4444-555555555555");
            JavaBackendConnection backend = new JavaBackendConnection(
                    "127.0.0.1", port, 754, ".Steve", uuid, events);
            backend.connect();
            try {
                FakeJavaServer server = waitForServer(holder, error);
                try (FakeJavaServer fake = server) {
                    assertNotNull(fake.waitForId(0x00, 5_000), "ハンドシェイクが届きませんでした。");
                    fake.send(new MinecraftPacket(0x02, loginSuccess754(".Steve", uuid)));
                    // 754 には設定フェーズが無い：少し待っても何も送ってこないこと。
                    Thread.sleep(500);
                    assertTrue(fake.received.isEmpty(),
                            "754 は Login Success の後に何も送らないはずです。");
                    // Join Game は 754 では 0x24。
                    fake.send(new MinecraftPacket(0x24, joinGame754()));
                    assertTrue(events.joined.await(5, TimeUnit.SECONDS),
                            "Join Game が処理されませんでした。");
                    assertEquals(42, events.entityId[0]);
                    assertTrue(events.dimensionInfo.await(5, TimeUnit.SECONDS),
                            "次元情報が決まりませんでした。");
                    assertArrayEquals(new int[]{0, 256}, events.dimensionInfos.get(0));
                    assertEquals("minecraft:plains", events.dimensionBiomes.get(0).get(1));
                    assertEquals("minecraft:desert", events.dimensionBiomes.get(0).get(2));
                    assertEquals(3, events.dimensionBiomes.get(0).size(),
                            "754 の codec には平原・砂漠の 2 件だけが入る");
                    assertEquals("minecraft:plains", events.dimensionBiomes.get(0).get(0),
                            "隙間のバイオーム id は平原で埋まる");
                }
            } finally {
                backend.close();
            }
        }
    }

    /**
     * 754 のチャット送信は 0x03 の {message} だけ（署名なし。1.19 で
     * Chat Message／Chat Command に分離される前の形式）。
     */
    @Test
    void sendChat754SendsPlainMessage() throws Exception {
        try (ServerSocket serverSocket = new ServerSocket(0, 1,
                java.net.InetAddress.getByName("127.0.0.1"))) {
            int port = serverSocket.getLocalPort();
            AtomicReference<FakeJavaServer> holder = new AtomicReference<>();
            AtomicReference<Exception> error = new AtomicReference<>();
            acceptInBackground(serverSocket, holder, error);

            ListenerEvents events = new ListenerEvents();
            UUID uuid = UUID.fromString("11111111-2222-3333-4444-555555555555");
            JavaBackendConnection backend = new JavaBackendConnection(
                    "127.0.0.1", port, 754, ".Steve", uuid, events);
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
                    assertEquals(0, reader.remaining(), "754 のチャットは文字列だけのはずです。");
                }
            } finally {
                backend.close();
            }
        }
    }

    /** 754 には Chat Command が無いため、コマンドは "/" 付きのチャット（0x03）で送る。 */
    @Test
    void sendCommand754SendsSlashChat() throws Exception {
        try (ServerSocket serverSocket = new ServerSocket(0, 1,
                java.net.InetAddress.getByName("127.0.0.1"))) {
            int port = serverSocket.getLocalPort();
            AtomicReference<FakeJavaServer> holder = new AtomicReference<>();
            AtomicReference<Exception> error = new AtomicReference<>();
            acceptInBackground(serverSocket, holder, error);

            ListenerEvents events = new ListenerEvents();
            UUID uuid = UUID.fromString("11111111-2222-3333-4444-555555555555");
            JavaBackendConnection backend = new JavaBackendConnection(
                    "127.0.0.1", port, 754, ".Steve", uuid, events);
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
     * 754 の Player Info（0x32）は旧式（action 付き）で、add_player に
     * crypto（公開鍵・署名）が無い。crypto を読もうとすると後続がずれ、
     * add player が処理されない。続けて送るチャット（0x0E）では
     * Player Info で覚えた名前で送信者が出ること。
     */
    @Test
    void playerInfo754HasNoCryptoAndChatUsesName() throws Exception {
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
                    "127.0.0.1", port, 754, ".Steve", uuid, events);
            backend.connect();
            try {
                FakeJavaServer server = waitForServer(holder, error);
                try (FakeJavaServer fake = server) {
                    loginToPlay(fake, events, uuid);

                    // Player Info（0x32）: action 0（add player）、crypto 節は無い。
                    PacketIO.Writer info = new PacketIO.Writer();
                    info.writeVarInt(0); // add player
                    info.writeVarInt(1);
                    info.writeUuid(other);
                    info.writeString("Alex");
                    info.writeVarInt(0); // properties（空）
                    info.writeVarInt(1); // gamemode: クリエイティブ
                    info.writeVarInt(50); // ping
                    info.writeBoolean(false); // display name なし
                    fake.send(new MinecraftPacket(0x32, info.bytes()));
                    assertTrue(events.playerInfoAdd.await(5, TimeUnit.SECONDS),
                            "Player Info の add player が処理されませんでした。");
                    assertEquals(List.of("Alex"), events.addedPlayerNames);
                    assertEquals(List.of(1), events.addedPlayerGamemodes);

                    // チャット（0x0E）: {message, position, sender}。
                    PacketIO.Writer chat = new PacketIO.Writer();
                    chat.writeString("{\"text\":\"hi\"}");
                    chat.writeByte(0); // position: chat
                    chat.writeUuid(other);
                    fake.send(new MinecraftPacket(0x0E, chat.bytes()));
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

    /** 754 のモブスポーン（0x02）は yaw→pitch の順で、objectData は無い。ブタ 59→73。 */
    @Test
    void spawnEntityLiving754Dispatches() throws Exception {
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
                    "127.0.0.1", port, 754, ".Steve", uuid, events);
            backend.connect();
            try {
                FakeJavaServer server = waitForServer(holder, error);
                try (FakeJavaServer fake = server) {
                    loginToPlay(fake, events, uuid);
                    PacketIO.Writer spawn = new PacketIO.Writer();
                    spawn.writeVarInt(7); // entityId
                    spawn.writeUuid(mob);
                    spawn.writeVarInt(59); // pig（754）
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
                    assertEquals(73, got.type765(), "754 のブタ 59 は 765 のブタ 73");
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
     * 754 の絵画スポーン（0x03）は title（varint）＋位置＋向き。
     * donkey_kong は 754 の 25 → 765 の 29。スポーン直後に題名の
     * 合成メタデータも送られること。
     */
    @Test
    void spawnEntityPainting754Dispatches() throws Exception {
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
                    "127.0.0.1", port, 754, ".Steve", uuid, events);
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
                    spawn.writeVarInt(25); // title: donkey_kong（754）
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

    /** 754 の Respawn（0x39）は dimension が NBT の値そのもの。ネザーに行くと次元が変わること。 */
    @Test
    void respawn754ReadsDimensionName() throws Exception {
        try (ServerSocket serverSocket = new ServerSocket(0, 1,
                java.net.InetAddress.getByName("127.0.0.1"))) {
            int port = serverSocket.getLocalPort();
            AtomicReference<FakeJavaServer> holder = new AtomicReference<>();
            AtomicReference<Exception> error = new AtomicReference<>();
            acceptInBackground(serverSocket, holder, error);

            ListenerEvents events = new ListenerEvents();
            UUID uuid = UUID.fromString("11111111-2222-3333-4444-555555555555");
            JavaBackendConnection backend = new JavaBackendConnection(
                    "127.0.0.1", port, 754, ".Steve", uuid, events);
            backend.connect();
            try {
                FakeJavaServer server = waitForServer(holder, error);
                try (FakeJavaServer fake = server) {
                    loginToPlay(fake, events, uuid);
                    events.dimensionInfos.clear();
                    events.dimensionBiomes.clear();
                    fake.send(new MinecraftPacket(0x39, respawn754ToNether()));
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
        fake.send(new MinecraftPacket(0x02, loginSuccess754(".Steve", uuid)));
        fake.send(new MinecraftPacket(0x24, joinGame754()));
        assertTrue(events.joined.await(5, TimeUnit.SECONDS), "Join Game が処理されませんでした。");
    }

    // ---- ソケット不要の写像・ディスパッチ ----

    /** 754 のブタ（59）は 765 のブタ（73）に正規化される。 */
    @Test
    void entityType754NormalizesPigTo765() {
        assertEquals(73, BedrockEntityTranslator.normalizeEntityType(59, 754));
    }

    /** 754 のガスト（29）は 765 のガスト（42）に正規化される。 */
    @Test
    void entityType754NormalizesGhastTo765() {
        assertEquals(42, BedrockEntityTranslator.normalizeEntityType(29, 754));
    }

    /** 754 の絵画（55）は 765 の絵画（69）に正規化される。 */
    @Test
    void entityType754NormalizesPaintingTo765() {
        assertEquals(69, BedrockEntityTranslator.normalizeEntityType(55, 754));
    }

    /** 754 の範囲外のエンティティはブタ（73）に落とす。 */
    @Test
    void entityType754OutOfRangeFallsBackToPig() {
        assertEquals(73, BedrockEntityTranslator.normalizeEntityType(999, 754));
    }

    /** 754 の oak_boat（667）は 765 の oak_boat（773）に正規化される。 */
    @Test
    void slot754NormalizesOakBoatTo765() {
        PacketIO.Writer writer = new PacketIO.Writer();
        writer.writeBoolean(true); // present
        writer.writeVarInt(667); // oak_boat（754）
        writer.writeByte(1); // count
        writer.writeByte(0); // NBT なし（END）
        BedrockEntityTranslator.JavaSlot slot =
                BedrockEntityTranslator.readSlot(new PacketIO.Reader(writer.bytes()), 754);
        assertTrue(slot.present());
        assertEquals(773, slot.item765());
    }

    /** 754 の music_disc_cat（910）は 765 の music_disc_cat（1162）。 */
    @Test
    void slot754NormalizesMusicDiscCatTo765() {
        PacketIO.Writer writer = new PacketIO.Writer();
        writer.writeBoolean(true);
        writer.writeVarInt(910); // music_disc_cat（754）
        writer.writeByte(1);
        writer.writeByte(0);
        BedrockEntityTranslator.JavaSlot slot =
                BedrockEntityTranslator.readSlot(new PacketIO.Reader(writer.bytes()), 754);
        assertTrue(slot.present());
        assertEquals(1162, slot.item765());
    }

    /**
     * 754 の grass_path（372）は 755 で dirt_path（393）に改名された
     * （Stage A §11.1 実測）。754 の grass_path と 755 の dirt_path は
     * 同じ 765 番号になること。
     */
    @Test
    void slot754GrassPathRenamesToDirtPath() {
        assertEquals(BedrockEntityTranslator.normalizeItem(393, 755),
                BedrockEntityTranslator.normalizeItem(372, 754));
    }

    /** 754 の範囲外のアイテムは石（1）に落とす。 */
    @Test
    void slot754OutOfRangeFallsBackToStone() {
        PacketIO.Writer writer = new PacketIO.Writer();
        writer.writeBoolean(true);
        writer.writeVarInt(99999);
        writer.writeByte(1);
        writer.writeByte(0);
        BedrockEntityTranslator.JavaSlot slot =
                BedrockEntityTranslator.readSlot(new PacketIO.Reader(writer.bytes()), 754);
        assertTrue(slot.present());
        assertEquals(1, slot.item765());
    }

    /**
     * 754 のスロット NBT は名前付き（型＋空名＋ペイロード）。中身のある NBT を
     * 名前付きで書いても読み切れること（匿名で読むと空名の 2 バイトが残る）。
     */
    @Test
    void slot754ReadsNamedNbt() {
        PacketIO.Writer writer = new PacketIO.Writer();
        writer.writeBoolean(true);
        writer.writeVarInt(1); // stone（754）
        writer.writeByte(1);
        Nbt.Tag.CompoundTag display = new Nbt.Tag.CompoundTag(
                Map.of("Name", new Nbt.Tag.StringTag("test")));
        Nbt.Tag.CompoundTag root = new Nbt.Tag.CompoundTag(
                Map.of("display", display));
        writer.writeBytes(Nbt.writeNamed("", root));
        PacketIO.Reader reader = new PacketIO.Reader(writer.bytes());
        BedrockEntityTranslator.JavaSlot slot =
                BedrockEntityTranslator.readSlot(reader, 754);
        assertTrue(slot.present());
        assertEquals(1, slot.item765());
        assertEquals(0, reader.remaining(), "名前付き NBT を読み切ること");
    }

    /** 754 のエフェクトは 1 ベース（Speed=1）で、765 は 0 ベース（Speed=0）。 */
    @Test
    void effect754NormalizesOneBasedToZeroBased() {
        assertEquals(0, BedrockEntityTranslator.normalizeEffectTo765(1, 754));
        assertEquals(31, BedrockEntityTranslator.normalizeEffectTo765(32, 754));
        assertEquals(0, BedrockEntityTranslator.normalizeEffectTo765(33, 754));
    }

    /**
     * 754 のパーティクル：angry_villager（1）はそのまま、barrier（2）は 757 の
     * block_marker（3）に統合されて 765 の 3、block（3）は 755 の block（4）を
     * 経て 765 の block（2）。754 に light は無い（1.17 で追加）。
     * 範囲外（72。754 は 0〜71）は 0。
     */
    @Test
    void particle754NormalizesTo765() {
        assertEquals(1, BedrockEntityTranslator.mapParticle754To765(1));
        assertEquals(3, BedrockEntityTranslator.mapParticle754To765(2));
        assertEquals(2, BedrockEntityTranslator.mapParticle754To765(3));
        assertEquals(0, BedrockEntityTranslator.mapParticle754To765(72));
    }

    /** 754 の絵画の題名：0〜24 は同じ、donkey_kong は 25→29、範囲外は 0。 */
    @Test
    void painting754MapsDonkeyKongTo29() {
        assertEquals(0, BedrockEntityTranslator.paintingId754To765(0));
        assertEquals(24, BedrockEntityTranslator.paintingId754To765(24));
        assertEquals(29, BedrockEntityTranslator.paintingId754To765(25));
        assertEquals(0, BedrockEntityTranslator.paintingId754To765(26));
    }

    /** 754 の Spawn Entity（0x00）は headPitch が無く、yaw で代用する。objectData は i32。 */
    @Test
    void spawnEntity754ParsesWithoutHeadPitch() {
        UUID mob = UUID.fromString("33333333-4444-5555-6666-777777777777");
        PacketIO.Writer writer = new PacketIO.Writer();
        writer.writeVarInt(7);
        writer.writeUuid(mob);
        writer.writeVarInt(59); // pig（754）
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
                BedrockEntityTranslator.parseSpawnEntity(writer.bytes(), 754);
        assertEquals(7, spawn.entityId());
        assertEquals(73, spawn.type765());
        // 角度はバイト（256＝360°）から度に直される。pitch 64→90°、yaw 32→45°。
        assertEquals(90.0f, spawn.pitch());
        assertEquals(45.0f, spawn.yaw());
        assertEquals(45.0f, spawn.headPitch(), "754 には headPitch が無いため yaw で代用する");
        assertEquals(12345, spawn.objectData());
    }

    /** 754 の Spawn Entity Living（0x02）は yaw→pitch の順。objectData は無い。 */
    @Test
    void spawnEntityLiving754ParsesYawBeforePitch() {
        UUID mob = UUID.fromString("33333333-4444-5555-6666-777777777777");
        PacketIO.Writer writer = new PacketIO.Writer();
        writer.writeVarInt(7);
        writer.writeUuid(mob);
        writer.writeVarInt(59); // pig（754）
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
                BedrockEntityTranslator.parseSpawnEntityLiving(writer.bytes(), 754);
        assertEquals(7, spawn.entityId());
        assertEquals(73, spawn.type765());
        // 角度は度に直される。yaw 64→90°、pitch 32→45°、headPitch 16→22.5°。
        assertEquals(90.0f, spawn.yaw());
        assertEquals(45.0f, spawn.pitch());
        assertEquals(22.5f, spawn.headPitch());
        assertEquals(0, spawn.objectData());
    }

    /** 754 の Spawn Entity Painting（0x03）は title（varint）＋位置＋向き。 */
    @Test
    void spawnPainting754ParsesTitle() {
        UUID painting = UUID.fromString("44444444-5555-6666-7777-888888888888");
        long packed = ((10L & 0x3FFFFFF) << 38)
                | ((-20L & 0x3FFFFFF) << 12) | (64L & 0xFFF);
        PacketIO.Writer writer = new PacketIO.Writer();
        writer.writeVarInt(9);
        writer.writeUuid(painting);
        writer.writeVarInt(25); // donkey_kong（754）
        writer.writeLong(packed);
        writer.writeByte(2); // direction
        BedrockEntityTranslator.SpawnPainting got =
                BedrockEntityTranslator.parseSpawnEntityPainting(writer.bytes());
        assertEquals(9, got.entityId());
        assertEquals(25, got.title757());
        assertEquals(2, got.direction());
        assertEquals(packed, got.positionRaw());
        assertEquals(29, BedrockEntityTranslator.paintingId754To765(got.title757()));
    }

    /**
     * 754 のメタデータ型（763 以降とは番号が違う）：2=f32、3=string、7=bool、18=pose。
     * pose は index 6（全バージョン共通）。型 14（NBT）は名前付きで読む
     * （匿名で読むと空名の 2 バイトが残り、後続のエントリがずれる）。
     * 型 15 の barrier パーティクルはデータ無し（765 の block_marker の
     * ブロック状態 varint を読んではいけない）。後続の pose まで正しく
     * 読めれば、読み飛ばしが合っている。index 16・17 はブタでは無視される
     * ため、読み飛ばしの確認用に使う。
     */
    @Test
    void metadata754TypesReadCorrectly() {
        PacketIO.Writer writer = new PacketIO.Writer();
        writer.writeVarInt(7); // entityId
        writer.writeByte(0); // index 0: flags
        writer.writeVarInt(0); // type 0: byte
        writer.writeByte(1);
        writer.writeByte(2); // index 2: custom name
        writer.writeVarInt(3); // type 3: string（754）
        writer.writeString("Alex");
        writer.writeByte(5); // index 5: no gravity
        writer.writeVarInt(7); // type 7: bool（754）
        writer.writeBoolean(true);
        writer.writeByte(6); // index 6: pose
        writer.writeVarInt(18); // type 18: pose varint（754）
        writer.writeVarInt(1); // fall_flying
        writer.writeByte(16); // index 16: NBT（読み飛ばし）
        writer.writeVarInt(14); // type 14: NBT（754 は名前付き）
        writer.writeBytes(Nbt.writeNamed("", new Nbt.Tag.CompoundTag(Map.of())));
        writer.writeByte(17); // index 17: particle（読み飛ばし）
        writer.writeVarInt(15); // type 15: particle
        writer.writeVarInt(2); // barrier（754。データ無し）
        writer.writeByte(0xFF); // 終端
        BedrockEntityTranslator.JavaMetadata meta =
                BedrockEntityTranslator.parseMetadata(writer.bytes(), 754, 73);
        assertEquals(7, meta.entityId());
        assertEquals(1, meta.flags());
        assertEquals("Alex", meta.customName());
        assertEquals(1, meta.pose());
        assertTrue(meta.complete(), "全エントリを読み切ること");
    }

    /** 754 の Update Attributes はキーが文字列、modifier の UUID は 16 バイト。 */
    @Test
    void updateAttributes754UsesStringKeysAndByteUuid() {
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
                BedrockEntityTranslator.parseUpdateAttributes(writer.bytes(), 754);
        assertEquals(7, attrs.entityId());
        assertEquals(0.1, attrs.movementSpeed());
        assertNull(attrs.maxHealth());
    }

    /**
     * 754 の Client Information は 6 フィールド（末尾の mainHand が最後で、
     * disableTextFiltering は無い。755 は 7 フィールド、757 は 8 フィールド）。
     * mc-1.16-protocol.json の packet_settings で確認（Stage A §11.6）。
     */
    @Test
    void clientSettings754HasSixFields() {
        byte[] payload754 = JavaBackendConnection.clientSettingsPayload(754);
        byte[] payload755 = JavaBackendConnection.clientSettingsPayload(755);
        byte[] payload757 = JavaBackendConnection.clientSettingsPayload(757);
        assertEquals(payload755.length - 1, payload754.length,
                "754 は disableTextFiltering が無い分 755 より 1 バイト短い");
        assertEquals(payload757.length - 2, payload754.length,
                "754 は disableTextFiltering と allowServerListings が無い分 757 より 2 バイト短い");
        PacketIO.Reader reader = new PacketIO.Reader(payload754);
        assertEquals("en_US", reader.readString());
        assertEquals(8, reader.readByte());
        assertEquals(0, reader.readVarInt());
        assertTrue(reader.readBoolean());
        assertEquals(0x7f, reader.readUnsignedByte());
        assertEquals(1, reader.readVarInt());
        assertEquals(0, reader.remaining(), "754 は mainHand が最後のフィールド");
    }

    /** 754 のエフェクト読み：{effectId i8, amplifier i8, duration varint, hideParticles i8}。 */
    @Test
    void entityEffect754ReadsByteEffectId() {
        PacketIO.Writer writer = new PacketIO.Writer();
        writer.writeVarInt(7); // entityId
        writer.writeByte(1); // effectId: Speed（754 は 1 ベース）
        writer.writeByte(0); // amplifier
        writer.writeVarInt(600); // duration
        writer.writeByte(0); // hideParticles: false
        BedrockEntityTranslator.EntityEffect effect =
                BedrockEntityTranslator.parseEntityEffect(writer.bytes(), 754);
        assertEquals(7, effect.entityId());
        assertEquals(0, effect.effect765(), "754 の Speed=1 は 765 の 0");
        assertTrue(effect.showParticles());
    }

    /** 754 の Remove Entity Effect は effectId を byte で読む。 */
    @Test
    void removeEntityEffect754ReadsByteEffectId() {
        PacketIO.Writer writer = new PacketIO.Writer();
        writer.writeVarInt(7); // entityId
        writer.writeByte(1); // effectId: Speed（754 は 1 ベース）
        BedrockEntityTranslator.RemoveEntityEffect removed =
                BedrockEntityTranslator.parseRemoveEntityEffect(writer.bytes(), 754);
        assertEquals(7, removed.entityId());
        assertEquals(0, removed.effect765());
    }

    // ---- ソケット不要の直接ディスパッチ（handleServerPacket） ----

    /** 接続なしで Login Success（0x02）→そのままプレイ（状態 2）に入ること。 */
    @Test
    void login754DirectGoesStraightToPlay() throws Exception {
        ListenerEvents events = new ListenerEvents();
        UUID uuid = UUID.fromString("11111111-2222-3333-4444-555555555555");
        JavaBackendConnection backend = new JavaBackendConnection(
                "127.0.0.1", 1, 754, ".Steve", uuid, events);
        try {
            int state = backend.handleServerPacket(
                    new MinecraftPacket(0x02, loginSuccess754(".Steve", uuid)), 0);
            assertEquals(2, state, "754 は Login Success の後そのままプレイに入る");
        } finally {
            backend.close();
        }
    }

    /** 接続なしで Join Game（0x24）→参加＋次元情報（minY 0、高さ 256）が決まること。 */
    @Test
    void joinGame754DirectSetsDimension() throws Exception {
        ListenerEvents events = new ListenerEvents();
        UUID uuid = UUID.fromString("11111111-2222-3333-4444-555555555555");
        JavaBackendConnection backend = new JavaBackendConnection(
                "127.0.0.1", 1, 754, ".Steve", uuid, events);
        try {
            int state = backend.handleServerPacket(
                    new MinecraftPacket(0x24, joinGame754()), 2);
            assertEquals(2, state);
            assertTrue(events.joined.await(1, TimeUnit.SECONDS));
            assertEquals(42, events.entityId[0]);
            assertTrue(events.dimensionInfo.await(1, TimeUnit.SECONDS));
            assertArrayEquals(new int[]{0, 256}, events.dimensionInfos.get(0));
            assertEquals("minecraft:plains", events.dimensionBiomes.get(0).get(1));
            assertEquals("minecraft:desert", events.dimensionBiomes.get(0).get(2));
            assertEquals(3, events.dimensionBiomes.get(0).size(),
                    "754 の codec には平原・砂漠の 2 件だけが入る");
        } finally {
            backend.close();
        }
    }

    /** 接続なしで Respawn（0x39）→ネザー（minY 0、高さ 128）に次元が変わること。 */
    @Test
    void respawn754DirectChangesDimension() throws Exception {
        ListenerEvents events = new ListenerEvents();
        UUID uuid = UUID.fromString("11111111-2222-3333-4444-555555555555");
        JavaBackendConnection backend = new JavaBackendConnection(
                "127.0.0.1", 1, 754, ".Steve", uuid, events);
        try {
            backend.handleServerPacket(new MinecraftPacket(0x24, joinGame754()), 2);
            assertTrue(events.dimensionInfo.await(1, TimeUnit.SECONDS));
            events.dimensionInfos.clear();
            backend.handleServerPacket(
                    new MinecraftPacket(0x39, respawn754ToNether()), 2);
            assertArrayEquals(new int[]{0, 128}, events.dimensionInfos.get(0));
        } finally {
            backend.close();
        }
    }

    /** 接続なしで Player Info（0x32）の add player（crypto 無し）が読めること。 */
    @Test
    void playerInfo754DirectHasNoCrypto() throws Exception {
        ListenerEvents events = new ListenerEvents();
        UUID uuid = UUID.fromString("11111111-2222-3333-4444-555555555555");
        UUID other = UUID.fromString("22222222-3333-4444-5555-666666666666");
        JavaBackendConnection backend = new JavaBackendConnection(
                "127.0.0.1", 1, 754, ".Steve", uuid, events);
        try {
            backend.handleServerPacket(new MinecraftPacket(0x24, joinGame754()), 2);
            PacketIO.Writer info = new PacketIO.Writer();
            info.writeVarInt(0); // add player
            info.writeVarInt(1);
            info.writeUuid(other);
            info.writeString("Alex");
            info.writeVarInt(0); // properties（空）
            info.writeVarInt(1); // gamemode: クリエイティブ
            info.writeVarInt(50); // ping
            info.writeBoolean(false); // display name なし
            backend.handleServerPacket(new MinecraftPacket(0x32, info.bytes()), 2);
            assertTrue(events.playerInfoAdd.await(1, TimeUnit.SECONDS));
            assertEquals(List.of("Alex"), events.addedPlayerNames);
            assertEquals(List.of(1), events.addedPlayerGamemodes);
        } finally {
            backend.close();
        }
    }

    /** 接続なしでチャット受信（0x0E）→ Player Info で覚えた名前で出ること。 */
    @Test
    void chat754DirectUsesPlayerName() throws Exception {
        ListenerEvents events = new ListenerEvents();
        UUID uuid = UUID.fromString("11111111-2222-3333-4444-555555555555");
        UUID other = UUID.fromString("22222222-3333-4444-5555-666666666666");
        JavaBackendConnection backend = new JavaBackendConnection(
                "127.0.0.1", 1, 754, ".Steve", uuid, events);
        try {
            backend.handleServerPacket(new MinecraftPacket(0x24, joinGame754()), 2);
            PacketIO.Writer info = new PacketIO.Writer();
            info.writeVarInt(0);
            info.writeVarInt(1);
            info.writeUuid(other);
            info.writeString("Alex");
            info.writeVarInt(0);
            info.writeVarInt(1);
            info.writeVarInt(50);
            info.writeBoolean(false);
            backend.handleServerPacket(new MinecraftPacket(0x32, info.bytes()), 2);
            PacketIO.Writer chat = new PacketIO.Writer();
            chat.writeString("{\"text\":\"hi\"}");
            chat.writeByte(0);
            chat.writeUuid(other);
            backend.handleServerPacket(new MinecraftPacket(0x0E, chat.bytes()), 2);
            assertTrue(events.chat.await(1, TimeUnit.SECONDS));
            assertEquals(List.of("Alex"), events.chatSenders);
            assertEquals(List.of("hi"), events.chatMessages);
        } finally {
            backend.close();
        }
    }

    /** 接続なしで Spawn Entity Living（0x02）→ブタ 59 が 765 の 73 で出ること。 */
    @Test
    void spawnEntityLiving754DirectDispatches() throws Exception {
        ListenerEvents events = new ListenerEvents();
        UUID uuid = UUID.fromString("11111111-2222-3333-4444-555555555555");
        UUID mob = UUID.fromString("33333333-4444-5555-6666-777777777777");
        JavaBackendConnection backend = new JavaBackendConnection(
                "127.0.0.1", 1, 754, ".Steve", uuid, events);
        try {
            backend.handleServerPacket(new MinecraftPacket(0x24, joinGame754()), 2);
            PacketIO.Writer spawn = new PacketIO.Writer();
            spawn.writeVarInt(7);
            spawn.writeUuid(mob);
            spawn.writeVarInt(59); // pig（754）
            spawn.writeDouble(10.5);
            spawn.writeDouble(65.0);
            spawn.writeDouble(-20.5);
            spawn.writeByte(64); // yaw
            spawn.writeByte(32); // pitch
            spawn.writeByte(64); // headPitch
            spawn.writeShort(0);
            spawn.writeShort(0);
            spawn.writeShort(0);
            backend.handleServerPacket(new MinecraftPacket(0x02, spawn.bytes()), 2);
            assertTrue(events.spawnEntity.await(1, TimeUnit.SECONDS));
            assertEquals(73, events.spawns.get(0).type765());
        } finally {
            backend.close();
        }
    }

    /** 接続なしで Spawn Entity Painting（0x03）→絵画＋題名メタデータが出ること。 */
    @Test
    void spawnEntityPainting754DirectDispatches() throws Exception {
        ListenerEvents events = new ListenerEvents();
        UUID uuid = UUID.fromString("11111111-2222-3333-4444-555555555555");
        UUID painting = UUID.fromString("44444444-5555-6666-7777-888888888888");
        JavaBackendConnection backend = new JavaBackendConnection(
                "127.0.0.1", 1, 754, ".Steve", uuid, events);
        try {
            backend.handleServerPacket(new MinecraftPacket(0x24, joinGame754()), 2);
            long packed = ((10L & 0x3FFFFFF) << 38)
                    | ((-20L & 0x3FFFFFF) << 12) | (64L & 0xFFF);
            PacketIO.Writer spawn = new PacketIO.Writer();
            spawn.writeVarInt(9);
            spawn.writeUuid(painting);
            spawn.writeVarInt(25); // donkey_kong（754）
            spawn.writeLong(packed);
            spawn.writeByte(2); // direction
            backend.handleServerPacket(new MinecraftPacket(0x03, spawn.bytes()), 2);
            assertTrue(events.spawnEntity.await(1, TimeUnit.SECONDS));
            assertEquals(69, events.spawns.get(0).type765());
            assertTrue(events.paintingMetadata.await(1, TimeUnit.SECONDS));
            assertEquals("donkey_kong", events.metadatas.get(0).paintingVariant());
        } finally {
            backend.close();
        }
    }

    /**
     * 接続なしで Entity Destroy（0x36）→754 は [count varint][entityIds] の配列。
     * 755 の [entityId varint] 単体として読むと、count 2 が entityId 2 になって
     * 後続の 7・9 が余り、落とされる（Stage B §14.4 のフィールド差分）。
     */
    @Test
    void entityDestroy754DirectReadsArray() throws Exception {
        ListenerEvents events = new ListenerEvents();
        UUID uuid = UUID.fromString("11111111-2222-3333-4444-555555555555");
        JavaBackendConnection backend = new JavaBackendConnection(
                "127.0.0.1", 1, 754, ".Steve", uuid, events);
        try {
            backend.handleServerPacket(new MinecraftPacket(0x24, joinGame754()), 2);
            PacketIO.Writer destroy = new PacketIO.Writer();
            destroy.writeVarInt(2); // count
            destroy.writeVarInt(7);
            destroy.writeVarInt(9);
            backend.handleServerPacket(new MinecraftPacket(0x36, destroy.bytes()), 2);
            assertTrue(events.removeEntities.await(1, TimeUnit.SECONDS),
                    "Entity Destroy が処理されませんでした。");
            assertEquals(List.of(7, 9), events.removedEntityIds);
        } finally {
            backend.close();
        }
    }

    /** 754 の Entity Destroy は [count varint][entityIds] の配列（755 の単体とは違う）。 */
    @Test
    void parseRemoveEntity754ReadsArray() {
        PacketIO.Writer writer = new PacketIO.Writer();
        writer.writeVarInt(2); // count
        writer.writeVarInt(7);
        writer.writeVarInt(9);
        assertEquals(List.of(7, 9),
                BedrockEntityTranslator.parseRemoveEntity754(writer.bytes()));
        PacketIO.Writer single = new PacketIO.Writer();
        single.writeVarInt(1); // count 1
        single.writeVarInt(7);
        assertEquals(List.of(7),
                BedrockEntityTranslator.parseRemoveEntity754(single.bytes()));
    }
}
