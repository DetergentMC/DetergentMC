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
 * Bedrock → 内部 736（Java 1.16.1）サーバーのテスト。
 * 偽 Java サーバー（736 役）とのログイン〜プレイの流れを確認する。
 * 736 は設定フェーズが無い（Login Success の後そのままプレイに入る。
 * 757・759〜763 と同じ）。Join Game は 0x25、dimension は文字列
 * （751 の NBT とは違う）。チャンクは 16 セクション＋bitMap＋バイオーム int[1024]
 * （BedrockChunkTranslatorTest で確認）。チャットは 0x0E（受信）／0x03（送信）で署名なし。
 * Player Info は 0x33 で、add_player に crypto（公開鍵・署名）が無い
 * （1.19 で追加）。モブスポーンは 0x02（Living）／0x03（Painting）と
 * 0x00（Spawn Entity）に分かれている（1.19 で統合）。
 * エンティティは 736 の 107 件（ブタ 59、ガスト 29、絵画 55）。
 * アイテムは 975 件（オークのボート 667）。エフェクトは 1 ベース 32 件
 * （Speed=1）。パーティクルは 72 件（barrier 2 は 757 の
 * block_marker 3 に統合。light は 1.16.1 には無く、736 の 3 は block）。
 * 絵画は 26 件（donkey_kong は 25）。
 * Client Information は 6 フィールドで、disableTextFiltering は無い。
 * 736 の Join Game の codec は {"dimension": [...]} だけでバイオームレジストリは無く、
 * JBC は translateDimensionCodec736To751 で dimension_type 4 件＋79 バイオームの
 * フルレジストリを再構築する（Stage A §11.4・Stage D 柱2 プローブ実測）。
 */
class JavaBackendConnection736Test {

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
            }, "fake-java-server-reader-736");
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
        final List<int[]> blockChanges = new ArrayList<>();
        final CountDownLatch blockChange = new CountDownLatch(1);

        @Override
        public void onBlockChange(int x, int y, int z, int stateId) {
            blockChanges.add(new int[]{x, y, z, stateId});
            blockChange.countDown();
        }

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
            // 736テストでは使わない。
        }

        @Override
        public void onChat(String sender, String message) {
            chatSenders.add(sender);
            chatMessages.add(message);
            chat.countDown();
        }

        @Override
        public void onTeleport(double x, double y, double z, float yaw, float pitch) {
            // 736テストではテレポートを使わない。
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
        }, "fake-java-server-accept-736");
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

    /** 736 の Login Success ペイロード（UUID・名前・空の属性。strictErrorHandling は無い）。 */
    private static byte[] loginSuccess736(String name, UUID uuid) {
        PacketIO.Writer writer = new PacketIO.Writer();
        writer.writeString(name);
        writer.writeUuid(uuid);
        writer.writeVarInt(0); // properties（空）
        return writer.bytes();
    }

    /**
     * オーバーワールドの DimensionType element（751）。min_y／height は無く、
     * 高さは logical_height 256 で表す（Stage A §11.6 の実バニラ 1.16.2 実測）。
     * 736 の Join Game には translateDimensionCodec751To736 で変換して入れる。
     */
    static Nbt.Tag.CompoundTag overworldElement751() {
        Map<String, Nbt.Tag> element = new LinkedHashMap<>();
        element.put("logical_height", new Nbt.Tag.IntTag(256));
        element.put("effects", new Nbt.Tag.StringTag("minecraft:overworld"));
        element.put("has_ceiling", new Nbt.Tag.ByteTag((byte) 0));
        return new Nbt.Tag.CompoundTag(element);
    }

    /** ネザーの DimensionType element（751）。min_y／height は無く、logical_height 128。 */
    static Nbt.Tag.CompoundTag netherElement751() {
        Map<String, Nbt.Tag> element = new LinkedHashMap<>();
        element.put("logical_height", new Nbt.Tag.IntTag(128));
        element.put("effects", new Nbt.Tag.StringTag("minecraft:the_nether"));
        element.put("has_ceiling", new Nbt.Tag.ByteTag((byte) 1));
        return new Nbt.Tag.CompoundTag(element);
    }

    /** 751 の dimensionCodec（dimension_type＋worldgen/biome の最小構成）。736 の Join Game には translateDimensionCodec751To736 で変換して入れる。 */
    private static Nbt.Tag.CompoundTag codec751() {
        Nbt.Tag.CompoundTag overworld = overworldElement751();
        Nbt.Tag.CompoundTag nether = netherElement751();
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

    /**
     * 736 の Join Game ペイロード（entityId 42、オーバーワールド）。
     * 736 の dimensionCodec は {"dimension": [...]} だけ（Stage A §11.4。
     * 実バニラ 1.16.1 の Join Game 1,159 バイトで確認）。751 の最小 codec を
     * translateDimensionCodec751To736 で変換して入れる（dimension_type 4 件。
     * バイオームレジストリは 736 の codec には無く、JBC が 751 へ戻すときに
     * 79 件のフルレジストリを再構築する）。
     * dimension は文字列 "minecraft:overworld"（751 の NBT とは違う）。
     */
    private static byte[] joinGame736() {
        return PlayTranslator.writeJoinGame736(new PlayTranslator.JoinGame736(
                42, false, 0, -1, List.of("minecraft:overworld"),
                PlayTranslator.translateDimensionCodec751To736(codec751()),
                "minecraft:overworld", "minecraft:overworld", 123L, 20, 10,
                false, true, false, false));
    }

    /** 736 の Respawn ペイロード（dimension は文字列 "minecraft:the_nether"、worldName はネザー）。 */
    private static byte[] respawn736ToNether() {
        PacketIO.Writer writer = new PacketIO.Writer();
        writer.writeString("minecraft:the_nether"); // dimension（文字列。751 の NBT とは違う）
        writer.writeString("minecraft:the_nether"); // worldName
        writer.writeLong(123L);
        writer.writeByte(0); // gameMode
        writer.writeByte(-1); // previousGameMode（u8 の -1＝255 として書く。読む側は u8。736 も 755 と同じ）
        writer.writeBoolean(false); // isDebug
        writer.writeBoolean(false); // isFlat
        writer.writeBoolean(false); // copyMetadata
        return writer.bytes();
    }

    /**
     * 736 は設定フェーズが無い：Login Success の後、Login Acknowledged や
     * Client Information を送らず、そのまま Join Game（0x25）でプレイに入る。
     * Join Game の codec から次元（minY 0、高さ 256）とバイオーム名
     * （平原 1、砂漠 2）が決まること。736 の element には min_y／height が無く、
     * 高さは logical_height（overworld 256）から決まる。
     */
    @Test
    void login736GoesStraightToPlay() throws Exception {
        try (ServerSocket serverSocket = new ServerSocket(0, 1,
                java.net.InetAddress.getByName("127.0.0.1"))) {
            int port = serverSocket.getLocalPort();
            AtomicReference<FakeJavaServer> holder = new AtomicReference<>();
            AtomicReference<Exception> error = new AtomicReference<>();
            acceptInBackground(serverSocket, holder, error);

            ListenerEvents events = new ListenerEvents();
            UUID uuid = UUID.fromString("11111111-2222-3333-4444-555555555555");
            JavaBackendConnection backend = new JavaBackendConnection(
                    "127.0.0.1", port, 736, ".Steve", uuid, events);
            backend.connect();
            try {
                FakeJavaServer server = waitForServer(holder, error);
                try (FakeJavaServer fake = server) {
                    assertNotNull(fake.waitForId(0x00, 5_000), "ハンドシェイクが届きませんでした。");
                    fake.send(new MinecraftPacket(0x02, loginSuccess736(".Steve", uuid)));
                    // 736 には設定フェーズが無い：少し待っても何も送ってこないこと。
                    Thread.sleep(500);
                    assertTrue(fake.received.isEmpty(),
                            "736 は Login Success の後に何も送らないはずです。");
                    // Join Game は 736 では 0x25。
                    fake.send(new MinecraftPacket(0x25, joinGame736()));
                    assertTrue(events.joined.await(5, TimeUnit.SECONDS),
                            "Join Game が処理されませんでした。");
                    assertEquals(42, events.entityId[0]);
                    assertTrue(events.dimensionInfo.await(5, TimeUnit.SECONDS),
                            "次元情報が決まりませんでした。");
                    assertArrayEquals(new int[]{0, 256}, events.dimensionInfos.get(0));
                    assertEquals("minecraft:ocean", events.dimensionBiomes.get(0).get(0));
                    assertEquals("minecraft:plains", events.dimensionBiomes.get(0).get(1));
                    assertEquals("minecraft:desert", events.dimensionBiomes.get(0).get(2));
                    assertEquals(174, events.dimensionBiomes.get(0).size(),
                            "736 の codec にはバイオームレジストリが無く、JBC が 751 の 79 件（id 0〜173）を再構築する");
                    assertEquals("minecraft:mountains", events.dimensionBiomes.get(0).get(3));
                    assertEquals("minecraft:plains", events.dimensionBiomes.get(0).get(100),
                            "隙間のバイオーム id は平原で埋まる");
                }
            } finally {
                backend.close();
            }
        }
    }

    /**
     * 736 のチャット送信は 0x03 の {message} だけ（署名なし。1.19 で
     * Chat Message／Chat Command に分離される前の形式）。
     */
    @Test
    void sendChat736SendsPlainMessage() throws Exception {
        try (ServerSocket serverSocket = new ServerSocket(0, 1,
                java.net.InetAddress.getByName("127.0.0.1"))) {
            int port = serverSocket.getLocalPort();
            AtomicReference<FakeJavaServer> holder = new AtomicReference<>();
            AtomicReference<Exception> error = new AtomicReference<>();
            acceptInBackground(serverSocket, holder, error);

            ListenerEvents events = new ListenerEvents();
            UUID uuid = UUID.fromString("11111111-2222-3333-4444-555555555555");
            JavaBackendConnection backend = new JavaBackendConnection(
                    "127.0.0.1", port, 736, ".Steve", uuid, events);
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
                    assertEquals(0, reader.remaining(), "736 のチャットは文字列だけのはずです。");
                }
            } finally {
                backend.close();
            }
        }
    }

    /** 736 には Chat Command が無いため、コマンドは "/" 付きのチャット（0x03）で送る。 */
    @Test
    void sendCommand736SendsSlashChat() throws Exception {
        try (ServerSocket serverSocket = new ServerSocket(0, 1,
                java.net.InetAddress.getByName("127.0.0.1"))) {
            int port = serverSocket.getLocalPort();
            AtomicReference<FakeJavaServer> holder = new AtomicReference<>();
            AtomicReference<Exception> error = new AtomicReference<>();
            acceptInBackground(serverSocket, holder, error);

            ListenerEvents events = new ListenerEvents();
            UUID uuid = UUID.fromString("11111111-2222-3333-4444-555555555555");
            JavaBackendConnection backend = new JavaBackendConnection(
                    "127.0.0.1", port, 736, ".Steve", uuid, events);
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
     * 736 の Player Info（0x33）は旧式（action 付き）で、add_player に
     * crypto（公開鍵・署名）が無い。crypto を読もうとすると後続がずれ、
     * add player が処理されない。続けて送るチャット（0x0E）では
     * Player Info で覚えた名前で送信者が出ること。
     */
    @Test
    void playerInfo736HasNoCryptoAndChatUsesName() throws Exception {
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
                    "127.0.0.1", port, 736, ".Steve", uuid, events);
            backend.connect();
            try {
                FakeJavaServer server = waitForServer(holder, error);
                try (FakeJavaServer fake = server) {
                    loginToPlay(fake, events, uuid);

                    // Player Info（0x33）: action 0（add player）、crypto 節は無い。
                    PacketIO.Writer info = new PacketIO.Writer();
                    info.writeVarInt(0); // add player
                    info.writeVarInt(1);
                    info.writeUuid(other);
                    info.writeString("Alex");
                    info.writeVarInt(0); // properties（空）
                    info.writeVarInt(1); // gamemode: クリエイティブ
                    info.writeVarInt(50); // ping
                    info.writeBoolean(false); // display name なし
                    fake.send(new MinecraftPacket(0x33, info.bytes()));
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

    /** 736 のモブスポーン（0x02）は yaw→pitch の順で、objectData は無い。ブタ 59→73。 */
    @Test
    void spawnEntityLiving736Dispatches() throws Exception {
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
                    "127.0.0.1", port, 736, ".Steve", uuid, events);
            backend.connect();
            try {
                FakeJavaServer server = waitForServer(holder, error);
                try (FakeJavaServer fake = server) {
                    loginToPlay(fake, events, uuid);
                    PacketIO.Writer spawn = new PacketIO.Writer();
                    spawn.writeVarInt(7); // entityId
                    spawn.writeUuid(mob);
                    spawn.writeVarInt(59); // pig（736）
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
                    assertEquals(73, got.type765(), "736 のブタ 59 は 765 のブタ 73");
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
     * 736 の絵画スポーン（0x03）は title（varint）＋位置＋向き。
     * donkey_kong は 736 の 25 → 765 の 29。スポーン直後に題名の
     * 合成メタデータも送られること。
     */
    @Test
    void spawnEntityPainting736Dispatches() throws Exception {
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
                    "127.0.0.1", port, 736, ".Steve", uuid, events);
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
                    spawn.writeVarInt(25); // title: donkey_kong（736）
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

    /** 736 の Respawn（0x3a）は dimension が文字列。ネザーに行くと次元が変わること。 */
    @Test
    void respawn736ReadsDimensionName() throws Exception {
        try (ServerSocket serverSocket = new ServerSocket(0, 1,
                java.net.InetAddress.getByName("127.0.0.1"))) {
            int port = serverSocket.getLocalPort();
            AtomicReference<FakeJavaServer> holder = new AtomicReference<>();
            AtomicReference<Exception> error = new AtomicReference<>();
            acceptInBackground(serverSocket, holder, error);

            ListenerEvents events = new ListenerEvents();
            UUID uuid = UUID.fromString("11111111-2222-3333-4444-555555555555");
            JavaBackendConnection backend = new JavaBackendConnection(
                    "127.0.0.1", port, 736, ".Steve", uuid, events);
            backend.connect();
            try {
                FakeJavaServer server = waitForServer(holder, error);
                try (FakeJavaServer fake = server) {
                    loginToPlay(fake, events, uuid);
                    events.dimensionInfos.clear();
                    events.dimensionBiomes.clear();
                    fake.send(new MinecraftPacket(0x3a, respawn736ToNether()));
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
        fake.send(new MinecraftPacket(0x02, loginSuccess736(".Steve", uuid)));
        fake.send(new MinecraftPacket(0x25, joinGame736()));
        assertTrue(events.joined.await(5, TimeUnit.SECONDS), "Join Game が処理されませんでした。");
    }

    // ---- ソケット不要の写像・ディスパッチ ----

    /** 736 のブタ（59）は 765 のブタ（73）に正規化される。 */
    @Test
    void entityType736NormalizesPigTo765() {
        assertEquals(73, BedrockEntityTranslator.normalizeEntityType(59, 736));
    }

    /** 736 のガスト（29）は 765 のガスト（42）に正規化される。 */
    @Test
    void entityType736NormalizesGhastTo765() {
        assertEquals(42, BedrockEntityTranslator.normalizeEntityType(29, 736));
    }

    /** 736 の絵画（55）は 765 の絵画（69）に正規化される。 */
    @Test
    void entityType736NormalizesPaintingTo765() {
        assertEquals(69, BedrockEntityTranslator.normalizeEntityType(55, 736));
    }

    /** 736 の範囲外のエンティティはブタ（73）に落とす。 */
    @Test
    void entityType736OutOfRangeFallsBackToPig() {
        assertEquals(73, BedrockEntityTranslator.normalizeEntityType(999, 736));
    }

    /** 736 の oak_boat（667）は 765 の oak_boat（773）に正規化される。 */
    @Test
    void slot736NormalizesOakBoatTo765() {
        PacketIO.Writer writer = new PacketIO.Writer();
        writer.writeBoolean(true); // present
        writer.writeVarInt(667); // oak_boat（736）
        writer.writeByte(1); // count
        writer.writeByte(0); // NBT なし（END）
        BedrockEntityTranslator.JavaSlot slot =
                BedrockEntityTranslator.readSlot(new PacketIO.Reader(writer.bytes()), 736);
        assertTrue(slot.present());
        assertEquals(773, slot.item765());
    }

    /** 736 の music_disc_cat（909）は 765 の music_disc_cat（1162）。 */
    @Test
    void slot736NormalizesMusicDiscCatTo765() {
        PacketIO.Writer writer = new PacketIO.Writer();
        writer.writeBoolean(true);
        writer.writeVarInt(909); // music_disc_cat（736。751 の 910 ではなく 909）
        writer.writeByte(1);
        writer.writeByte(0);
        BedrockEntityTranslator.JavaSlot slot =
                BedrockEntityTranslator.readSlot(new PacketIO.Reader(writer.bytes()), 736);
        assertTrue(slot.present());
        assertEquals(1162, slot.item765());
    }

    /**
     * 736 の grass_path（372）は 753 でも grass_path（372）のまま
     * （736→753 は恒等写像）で、755 で dirt_path（393）に改名された
     * （Stage A §11.1 実測）。736 の grass_path と 755 の dirt_path は
     * 同じ 765 番号になること。
     */
    @Test
    void slot736GrassPathRenamesToDirtPath() {
        assertEquals(BedrockEntityTranslator.normalizeItem(393, 755),
                BedrockEntityTranslator.normalizeItem(372, 736));
    }

    /** 736 の範囲外のアイテムは石（1）に落とす。 */
    @Test
    void slot736OutOfRangeFallsBackToStone() {
        PacketIO.Writer writer = new PacketIO.Writer();
        writer.writeBoolean(true);
        writer.writeVarInt(99999);
        writer.writeByte(1);
        writer.writeByte(0);
        BedrockEntityTranslator.JavaSlot slot =
                BedrockEntityTranslator.readSlot(new PacketIO.Reader(writer.bytes()), 736);
        assertTrue(slot.present());
        assertEquals(1, slot.item765());
    }

    /**
     * 736 のスロット NBT は名前付き（型＋空名＋ペイロード）。中身のある NBT を
     * 名前付きで書いても読み切れること（匿名で読むと空名の 2 バイトが残る）。
     */
    @Test
    void slot736ReadsNamedNbt() {
        PacketIO.Writer writer = new PacketIO.Writer();
        writer.writeBoolean(true);
        writer.writeVarInt(1); // stone（736）
        writer.writeByte(1);
        Nbt.Tag.CompoundTag display = new Nbt.Tag.CompoundTag(
                Map.of("Name", new Nbt.Tag.StringTag("test")));
        Nbt.Tag.CompoundTag root = new Nbt.Tag.CompoundTag(
                Map.of("display", display));
        writer.writeBytes(Nbt.writeNamed("", root));
        PacketIO.Reader reader = new PacketIO.Reader(writer.bytes());
        BedrockEntityTranslator.JavaSlot slot =
                BedrockEntityTranslator.readSlot(reader, 736);
        assertTrue(slot.present());
        assertEquals(1, slot.item765());
        assertEquals(0, reader.remaining(), "名前付き NBT を読み切ること");
    }

    /** 736 のエフェクトは 1 ベース（Speed=1）で、765 は 0 ベース（Speed=0）。 */
    @Test
    void effect736NormalizesOneBasedToZeroBased() {
        assertEquals(0, BedrockEntityTranslator.normalizeEffectTo765(1, 736));
        assertEquals(31, BedrockEntityTranslator.normalizeEffectTo765(32, 736));
        assertEquals(0, BedrockEntityTranslator.normalizeEffectTo765(33, 736));
    }

    /**
     * 736 のパーティクル：angry_villager（1）はそのまま、barrier（2）は 757 の
     * block_marker（3）に統合されて 765 の 3、block（3）は 755 の block（4）を
     * 経て 765 の block（2）。736 に light は無い（1.17 で追加）。
     * 範囲外（72。736 は 0〜71）は 0。
     */
    @Test
    void particle736NormalizesTo765() {
        assertEquals(1, BedrockEntityTranslator.mapParticle736To765(1));
        assertEquals(3, BedrockEntityTranslator.mapParticle736To765(2));
        assertEquals(2, BedrockEntityTranslator.mapParticle736To765(3));
        assertEquals(0, BedrockEntityTranslator.mapParticle736To765(72));
    }

    /** 736 の絵画の題名：0〜24 は同じ、donkey_kong は 25→29、範囲外は 0。 */
    @Test
    void painting736MapsDonkeyKongTo29() {
        assertEquals(0, BedrockEntityTranslator.paintingId736To765(0));
        assertEquals(24, BedrockEntityTranslator.paintingId736To765(24));
        assertEquals(29, BedrockEntityTranslator.paintingId736To765(25));
        assertEquals(0, BedrockEntityTranslator.paintingId736To765(26));
    }

    /** 736 の Spawn Entity（0x00）は headPitch が無く、yaw で代用する。objectData は i32。 */
    @Test
    void spawnEntity736ParsesWithoutHeadPitch() {
        UUID mob = UUID.fromString("33333333-4444-5555-6666-777777777777");
        PacketIO.Writer writer = new PacketIO.Writer();
        writer.writeVarInt(7);
        writer.writeUuid(mob);
        writer.writeVarInt(59); // pig（736）
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
                BedrockEntityTranslator.parseSpawnEntity(writer.bytes(), 736);
        assertEquals(7, spawn.entityId());
        assertEquals(73, spawn.type765());
        // 角度はバイト（256＝360°）から度に直される。pitch 64→90°、yaw 32→45°。
        assertEquals(90.0f, spawn.pitch());
        assertEquals(45.0f, spawn.yaw());
        assertEquals(45.0f, spawn.headPitch(), "736 には headPitch が無いため yaw で代用する");
        assertEquals(12345, spawn.objectData());
    }

    /** 736 の Spawn Entity Living（0x02）は yaw→pitch の順。objectData は無い。 */
    @Test
    void spawnEntityLiving736ParsesYawBeforePitch() {
        UUID mob = UUID.fromString("33333333-4444-5555-6666-777777777777");
        PacketIO.Writer writer = new PacketIO.Writer();
        writer.writeVarInt(7);
        writer.writeUuid(mob);
        writer.writeVarInt(59); // pig（736）
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
                BedrockEntityTranslator.parseSpawnEntityLiving(writer.bytes(), 736);
        assertEquals(7, spawn.entityId());
        assertEquals(73, spawn.type765());
        // 角度は度に直される。yaw 64→90°、pitch 32→45°、headPitch 16→22.5°。
        assertEquals(90.0f, spawn.yaw());
        assertEquals(45.0f, spawn.pitch());
        assertEquals(22.5f, spawn.headPitch());
        assertEquals(0, spawn.objectData());
    }

    /** 736 の Spawn Entity Painting（0x03）は title（varint）＋位置＋向き。 */
    @Test
    void spawnPainting736ParsesTitle() {
        UUID painting = UUID.fromString("44444444-5555-6666-7777-888888888888");
        long packed = ((10L & 0x3FFFFFF) << 38)
                | ((-20L & 0x3FFFFFF) << 12) | (64L & 0xFFF);
        PacketIO.Writer writer = new PacketIO.Writer();
        writer.writeVarInt(9);
        writer.writeUuid(painting);
        writer.writeVarInt(25); // donkey_kong（736）
        writer.writeLong(packed);
        writer.writeByte(2); // direction
        BedrockEntityTranslator.SpawnPainting got =
                BedrockEntityTranslator.parseSpawnEntityPainting(writer.bytes());
        assertEquals(9, got.entityId());
        assertEquals(25, got.title757());
        assertEquals(2, got.direction());
        assertEquals(packed, got.positionRaw());
        assertEquals(29, BedrockEntityTranslator.paintingId736To765(got.title757()));
    }

    /**
     * 736 のメタデータ型（763 以降とは番号が違う）：2=f32、3=string、7=bool、18=pose。
     * pose は index 6（全バージョン共通）。型 14（NBT）は名前付きで読む
     * （匿名で読むと空名の 2 バイトが残り、後続のエントリがずれる）。
     * 型 15 の barrier パーティクルはデータ無し（765 の block_marker の
     * ブロック状態 varint を読んではいけない）。後続の pose まで正しく
     * 読めれば、読み飛ばしが合っている。index 16・17 はブタでは無視される
     * ため、読み飛ばしの確認用に使う。
     */
    @Test
    void metadata736TypesReadCorrectly() {
        PacketIO.Writer writer = new PacketIO.Writer();
        writer.writeVarInt(7); // entityId
        writer.writeByte(0); // index 0: flags
        writer.writeVarInt(0); // type 0: byte
        writer.writeByte(1);
        writer.writeByte(2); // index 2: custom name
        writer.writeVarInt(3); // type 3: string（736）
        writer.writeString("Alex");
        writer.writeByte(5); // index 5: no gravity
        writer.writeVarInt(7); // type 7: bool（736）
        writer.writeBoolean(true);
        writer.writeByte(6); // index 6: pose
        writer.writeVarInt(18); // type 18: pose varint（736）
        writer.writeVarInt(1); // fall_flying
        writer.writeByte(16); // index 16: NBT（読み飛ばし）
        writer.writeVarInt(14); // type 14: NBT（736 は名前付き）
        writer.writeBytes(Nbt.writeNamed("", new Nbt.Tag.CompoundTag(Map.of())));
        writer.writeByte(17); // index 17: particle（読み飛ばし）
        writer.writeVarInt(15); // type 15: particle
        writer.writeVarInt(2); // barrier（736。データ無し）
        writer.writeByte(0xFF); // 終端
        BedrockEntityTranslator.JavaMetadata meta =
                BedrockEntityTranslator.parseMetadata(writer.bytes(), 736, 73);
        assertEquals(7, meta.entityId());
        assertEquals(1, meta.flags());
        assertEquals("Alex", meta.customName());
        assertEquals(1, meta.pose());
        assertTrue(meta.complete(), "全エントリを読み切ること");
    }

    /** 736 の Update Attributes はキーが文字列、modifier の UUID は 16 バイト。 */
    @Test
    void updateAttributes736UsesStringKeysAndByteUuid() {
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
                BedrockEntityTranslator.parseUpdateAttributes(writer.bytes(), 736);
        assertEquals(7, attrs.entityId());
        assertEquals(0.1, attrs.movementSpeed());
        assertNull(attrs.maxHealth());
    }

    /**
     * 736 の Client Information は 6 フィールド（末尾の mainHand が最後で、
     * disableTextFiltering は無い。755 は 7 フィールド、757 は 8 フィールド）。
     * mc-1.16-protocol.json の packet_settings で確認（Stage A §11.6）。
     */
    @Test
    void clientSettings736HasSixFields() {
        byte[] payload736 = JavaBackendConnection.clientSettingsPayload(736);
        byte[] payload754 = JavaBackendConnection.clientSettingsPayload(754);
        byte[] payload755 = JavaBackendConnection.clientSettingsPayload(755);
        byte[] payload757 = JavaBackendConnection.clientSettingsPayload(757);
        assertEquals(payload754.length, payload736.length,
                "736 は 754 と同じ 6 フィールド");
        assertEquals(payload755.length - 1, payload736.length,
                "736 は disableTextFiltering が無い分 755 より 1 バイト短い");
        assertEquals(payload757.length - 2, payload736.length,
                "736 は disableTextFiltering と allowServerListings が無い分 757 より 2 バイト短い");
        PacketIO.Reader reader = new PacketIO.Reader(payload736);
        assertEquals("en_US", reader.readString());
        assertEquals(8, reader.readByte());
        assertEquals(0, reader.readVarInt());
        assertTrue(reader.readBoolean());
        assertEquals(0x7f, reader.readUnsignedByte());
        assertEquals(1, reader.readVarInt());
        assertEquals(0, reader.remaining(), "736 は mainHand が最後のフィールド");
    }

    /** 736 のエフェクト読み：{effectId i8, amplifier i8, duration varint, hideParticles i8}。 */
    @Test
    void entityEffect736ReadsByteEffectId() {
        PacketIO.Writer writer = new PacketIO.Writer();
        writer.writeVarInt(7); // entityId
        writer.writeByte(1); // effectId: Speed（736 は 1 ベース）
        writer.writeByte(0); // amplifier
        writer.writeVarInt(600); // duration
        writer.writeByte(0); // hideParticles: false
        BedrockEntityTranslator.EntityEffect effect =
                BedrockEntityTranslator.parseEntityEffect(writer.bytes(), 736);
        assertEquals(7, effect.entityId());
        assertEquals(0, effect.effect765(), "736 の Speed=1 は 765 の 0");
        assertTrue(effect.showParticles());
    }

    /** 736 の Remove Entity Effect は effectId を byte で読む。 */
    @Test
    void removeEntityEffect736ReadsByteEffectId() {
        PacketIO.Writer writer = new PacketIO.Writer();
        writer.writeVarInt(7); // entityId
        writer.writeByte(1); // effectId: Speed（736 は 1 ベース）
        BedrockEntityTranslator.RemoveEntityEffect removed =
                BedrockEntityTranslator.parseRemoveEntityEffect(writer.bytes(), 736);
        assertEquals(7, removed.entityId());
        assertEquals(0, removed.effect765());
    }

    // ---- ソケット不要の直接ディスパッチ（handleServerPacket） ----

    /** 接続なしで Login Success（0x02）→そのままプレイ（状態 2）に入ること。 */
    @Test
    void login736DirectGoesStraightToPlay() throws Exception {
        ListenerEvents events = new ListenerEvents();
        UUID uuid = UUID.fromString("11111111-2222-3333-4444-555555555555");
        JavaBackendConnection backend = new JavaBackendConnection(
                "127.0.0.1", 1, 736, ".Steve", uuid, events);
        try {
            int state = backend.handleServerPacket(
                    new MinecraftPacket(0x02, loginSuccess736(".Steve", uuid)), 0);
            assertEquals(2, state, "736 は Login Success の後そのままプレイに入る");
        } finally {
            backend.close();
        }
    }

    /** 接続なしで Join Game（0x25）→参加＋次元情報（minY 0、高さ 256）が決まること。 */
    @Test
    void joinGame736DirectSetsDimension() throws Exception {
        ListenerEvents events = new ListenerEvents();
        UUID uuid = UUID.fromString("11111111-2222-3333-4444-555555555555");
        JavaBackendConnection backend = new JavaBackendConnection(
                "127.0.0.1", 1, 736, ".Steve", uuid, events);
        try {
            int state = backend.handleServerPacket(
                    new MinecraftPacket(0x25, joinGame736()), 2);
            assertEquals(2, state);
            assertTrue(events.joined.await(1, TimeUnit.SECONDS));
            assertEquals(42, events.entityId[0]);
            assertTrue(events.dimensionInfo.await(1, TimeUnit.SECONDS));
            assertArrayEquals(new int[]{0, 256}, events.dimensionInfos.get(0));
            assertEquals("minecraft:ocean", events.dimensionBiomes.get(0).get(0));
            assertEquals("minecraft:plains", events.dimensionBiomes.get(0).get(1));
            assertEquals("minecraft:desert", events.dimensionBiomes.get(0).get(2));
            assertEquals(174, events.dimensionBiomes.get(0).size(),
                    "736 の codec にはバイオームレジストリが無く、JBC が 751 の 79 件（id 0〜173）を再構築する");
        } finally {
            backend.close();
        }
    }

    /** 接続なしで Respawn（0x3a）→ネザー（minY 0、高さ 128）に次元が変わること。 */
    @Test
    void respawn736DirectChangesDimension() throws Exception {
        ListenerEvents events = new ListenerEvents();
        UUID uuid = UUID.fromString("11111111-2222-3333-4444-555555555555");
        JavaBackendConnection backend = new JavaBackendConnection(
                "127.0.0.1", 1, 736, ".Steve", uuid, events);
        try {
            backend.handleServerPacket(new MinecraftPacket(0x25, joinGame736()), 2);
            assertTrue(events.dimensionInfo.await(1, TimeUnit.SECONDS));
            events.dimensionInfos.clear();
            backend.handleServerPacket(
                    new MinecraftPacket(0x3a, respawn736ToNether()), 2);
            assertArrayEquals(new int[]{0, 128}, events.dimensionInfos.get(0));
        } finally {
            backend.close();
        }
    }

    /** 接続なしで Player Info（0x33）の add player（crypto 無し）が読めること。 */
    @Test
    void playerInfo736DirectHasNoCrypto() throws Exception {
        ListenerEvents events = new ListenerEvents();
        UUID uuid = UUID.fromString("11111111-2222-3333-4444-555555555555");
        UUID other = UUID.fromString("22222222-3333-4444-5555-666666666666");
        JavaBackendConnection backend = new JavaBackendConnection(
                "127.0.0.1", 1, 736, ".Steve", uuid, events);
        try {
            backend.handleServerPacket(new MinecraftPacket(0x25, joinGame736()), 2);
            PacketIO.Writer info = new PacketIO.Writer();
            info.writeVarInt(0); // add player
            info.writeVarInt(1);
            info.writeUuid(other);
            info.writeString("Alex");
            info.writeVarInt(0); // properties（空）
            info.writeVarInt(1); // gamemode: クリエイティブ
            info.writeVarInt(50); // ping
            info.writeBoolean(false); // display name なし
            backend.handleServerPacket(new MinecraftPacket(0x33, info.bytes()), 2);
            assertTrue(events.playerInfoAdd.await(1, TimeUnit.SECONDS));
            assertEquals(List.of("Alex"), events.addedPlayerNames);
            assertEquals(List.of(1), events.addedPlayerGamemodes);
        } finally {
            backend.close();
        }
    }

    /** 接続なしでチャット受信（0x0E）→ Player Info で覚えた名前で出ること。 */
    @Test
    void chat736DirectUsesPlayerName() throws Exception {
        ListenerEvents events = new ListenerEvents();
        UUID uuid = UUID.fromString("11111111-2222-3333-4444-555555555555");
        UUID other = UUID.fromString("22222222-3333-4444-5555-666666666666");
        JavaBackendConnection backend = new JavaBackendConnection(
                "127.0.0.1", 1, 736, ".Steve", uuid, events);
        try {
            backend.handleServerPacket(new MinecraftPacket(0x25, joinGame736()), 2);
            PacketIO.Writer info = new PacketIO.Writer();
            info.writeVarInt(0);
            info.writeVarInt(1);
            info.writeUuid(other);
            info.writeString("Alex");
            info.writeVarInt(0);
            info.writeVarInt(1);
            info.writeVarInt(50);
            info.writeBoolean(false);
            backend.handleServerPacket(new MinecraftPacket(0x33, info.bytes()), 2);
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
    void spawnEntityLiving736DirectDispatches() throws Exception {
        ListenerEvents events = new ListenerEvents();
        UUID uuid = UUID.fromString("11111111-2222-3333-4444-555555555555");
        UUID mob = UUID.fromString("33333333-4444-5555-6666-777777777777");
        JavaBackendConnection backend = new JavaBackendConnection(
                "127.0.0.1", 1, 736, ".Steve", uuid, events);
        try {
            backend.handleServerPacket(new MinecraftPacket(0x25, joinGame736()), 2);
            PacketIO.Writer spawn = new PacketIO.Writer();
            spawn.writeVarInt(7);
            spawn.writeUuid(mob);
            spawn.writeVarInt(59); // pig（736）
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
    void spawnEntityPainting736DirectDispatches() throws Exception {
        ListenerEvents events = new ListenerEvents();
        UUID uuid = UUID.fromString("11111111-2222-3333-4444-555555555555");
        UUID painting = UUID.fromString("44444444-5555-6666-7777-888888888888");
        JavaBackendConnection backend = new JavaBackendConnection(
                "127.0.0.1", 1, 736, ".Steve", uuid, events);
        try {
            backend.handleServerPacket(new MinecraftPacket(0x25, joinGame736()), 2);
            long packed = ((10L & 0x3FFFFFF) << 38)
                    | ((-20L & 0x3FFFFFF) << 12) | (64L & 0xFFF);
            PacketIO.Writer spawn = new PacketIO.Writer();
            spawn.writeVarInt(9);
            spawn.writeUuid(painting);
            spawn.writeVarInt(25); // donkey_kong（736）
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
     * 接続なしで Entity Destroy（0x37）→736 は [count varint][entityIds] の配列。
     * 755 の [entityId varint] 単体として読むと、count 2 が entityId 2 になって
     * 後続の 7・9 が余り、落とされる（Stage B §14.4 のフィールド差分）。
     */
    @Test
    void entityDestroy736DirectReadsArray() throws Exception {
        ListenerEvents events = new ListenerEvents();
        UUID uuid = UUID.fromString("11111111-2222-3333-4444-555555555555");
        JavaBackendConnection backend = new JavaBackendConnection(
                "127.0.0.1", 1, 736, ".Steve", uuid, events);
        try {
            backend.handleServerPacket(new MinecraftPacket(0x25, joinGame736()), 2);
            PacketIO.Writer destroy = new PacketIO.Writer();
            destroy.writeVarInt(2); // count
            destroy.writeVarInt(7);
            destroy.writeVarInt(9);
            backend.handleServerPacket(new MinecraftPacket(0x37, destroy.bytes()), 2);
            assertTrue(events.removeEntities.await(1, TimeUnit.SECONDS),
                    "Entity Destroy が処理されませんでした。");
            assertEquals(List.of(7, 9), events.removedEntityIds);
        } finally {
            backend.close();
        }
    }

    /** 736 の Entity Destroy は [count varint][entityIds] の配列（755 の単体とは違う）。 */
    @Test
    void parseRemoveEntity736ReadsArray() {
        PacketIO.Writer writer = new PacketIO.Writer();
        writer.writeVarInt(2); // count
        writer.writeVarInt(7);
        writer.writeVarInt(9);
        assertEquals(List.of(7, 9),
                BedrockEntityTranslator.parseRemoveEntity736(writer.bytes()));
        PacketIO.Writer single = new PacketIO.Writer();
        single.writeVarInt(1); // count 1
        single.writeVarInt(7);
        assertEquals(List.of(7),
                BedrockEntityTranslator.parseRemoveEntity736(single.bytes()));
    }

    // ---- 736 固有 ----

    /**
     * 736 の 61 は piglin_brute（751 の 61）ではなく 751 の 62（pillager）。
     * 751 の piglin_brute は 736 には無い（Stage A §11.1）。736 の 60（piglin）は
     * 751 の 60 と同一で 765 の 74。プローブ実測：61→76、60→74、59→73。
     */
    @Test
    void entityType736PiglinBruteGap() {
        assertEquals(76, BedrockEntityTranslator.normalizeEntityType(61, 736),
                "736 の 61 は 751 の 62 で、765 の 76");
        assertEquals(74, BedrockEntityTranslator.normalizeEntityType(60, 736),
                "736 の piglin 60 は 751 の piglin 60 と同じで、765 の 74");
    }

    /**
     * 736 のアイテム：oak_boat は 751 と同じ 667（765 は 773）、
     * music_disc_cat は 751 の 910 ではなく 736 の 909（765 は 1162）、
     * grass_path は 751 と同じ 372（765 は 463）。プローブ実測。
     */
    @Test
    void item736Anchors() {
        assertEquals(773, BedrockEntityTranslator.normalizeItem(667, 736),
                "736 の oak_boat 667 は 765 の 773");
        assertEquals(1162, BedrockEntityTranslator.normalizeItem(909, 736),
                "736 の music_disc_cat 909 は 765 の 1162");
        assertEquals(463, BedrockEntityTranslator.normalizeItem(372, 736),
                "736 の grass_path 372 は 765 の 463");
        assertEquals(BedrockEntityTranslator.normalizeItem(372, 751),
                BedrockEntityTranslator.normalizeItem(372, 736),
                "grass_path は 736 と 751 で同じ 765 値");
    }

    /**
     * 接続なしで Multi Block Change（0x0f）→736 は [chunkX i32][chunkZ i32]
     * [count varint][{horizontalPos u8, y u8, blockId varint}]（751 の
     * [chunkCoordinates bitfield][notTrustEdges][varlong records] とは違う。
     * 実バニラ 1.16.1 jar の ClientboundChunkBlocksUpdatePacket（javap）で確認）。
     * short は (localX<<12)|(localZ<<8)|y。stateId は 736 の生 id のまま渡す
     * （BedrockSession 側で 765 に正規化する）。
     */
    @Test
    void multiBlockChange736DirectDispatches() throws Exception {
        ListenerEvents events = new ListenerEvents();
        UUID uuid = UUID.fromString("11111111-2222-3333-4444-555555555555");
        JavaBackendConnection backend = new JavaBackendConnection(
                "127.0.0.1", 1, 736, ".Steve", uuid, events);
        try {
            backend.handleServerPacket(new MinecraftPacket(0x25, joinGame736()), 2);
            PacketIO.Writer change = new PacketIO.Writer();
            change.writeInt(0); // chunkX
            change.writeInt(0); // chunkZ
            change.writeVarInt(2); // count
            change.writeShort((1 << 12) | (2 << 8) | 64); // local (1, 64, 2)
            change.writeVarInt(9); // grass（736 の生 id）
            change.writeShort((3 << 12) | (4 << 8) | 65); // local (3, 65, 4)
            change.writeVarInt(1); // stone（736 の生 id）
            backend.handleServerPacket(new MinecraftPacket(0x0f, change.bytes()), 2);
            assertTrue(events.blockChange.await(1, TimeUnit.SECONDS),
                    "Multi Block Change が処理されませんでした。");
            assertEquals(2, events.blockChanges.size());
            assertArrayEquals(new int[]{1, 64, 2, 9}, events.blockChanges.get(0));
            assertArrayEquals(new int[]{3, 65, 4, 1}, events.blockChanges.get(1));
        } finally {
            backend.close();
        }
    }
}
