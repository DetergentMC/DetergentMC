package io.detergentmc.bedrock;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.detergentmc.bedrock.BedrockBatch.GamePacket;
import io.detergentmc.bedrock.BedrockVarInt.ByteReader;
import io.detergentmc.bedrock.BedrockVarInt.ByteWriter;
import io.detergentmc.protocol.Nbt;
import io.detergentmc.protocol.PacketIO;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;

/**
 * Bedrock セッションのエンティティ翻訳テスト。偽の Java バックエンドの
 * Listener へ Java パケットを渡し、Bedrock 側へ出るパケットを確認する。
 */
class BedrockSessionEntityTest {

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

        List<GamePacket> findAll(int id) throws IOException {
            return gamePackets().stream().filter(p -> p.id() == id).toList();
        }

        GamePacket find(int id) throws IOException {
            return findAll(id).stream().findFirst().orElse(null);
        }

        void clear() {
            sent.clear();
        }
    }

    /** 偽 Java バックエンド。送られたものを記録し、Listener を保持する。 */
    private static final class FakeBackend implements JavaBackend {
        JavaBackendConnection.Listener listener;
        final List<String> chats = new ArrayList<>();
        final List<int[]> useEntities = new ArrayList<>();
        final List<Integer> entityActions = new ArrayList<>();
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
            chats.add(message);
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
            useEntities.add(new int[]{entityId, action, hand});
        }

        @Override
        public void sendEntityAction(int action) {
            entityActions.add(action);
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

        void playUntilStartGame() {
            establishRakNet();
            sendGamePacket(BedrockProtocol.PACKET_LOGIN,
                    TestJwt.loginPayload(630, "Steve", "2535461234567890",
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
    }

    // ---- Java パケットの組み立て（765）----

    private static byte[] spawnEntityPayload(int entityId, UUID uuid, int type,
            double x, double y, double z, int pitch, int yaw, int headPitch,
            int objectData) {
        PacketIO.Writer writer = new PacketIO.Writer();
        writer.writeVarInt(entityId);
        writer.writeUuid(uuid);
        writer.writeVarInt(type);
        writer.writeDouble(x);
        writer.writeDouble(y);
        writer.writeDouble(z);
        writer.writeByte(pitch);
        writer.writeByte(yaw);
        writer.writeByte(headPitch);
        writer.writeVarInt(objectData);
        writer.writeShort(0);
        writer.writeShort(0);
        writer.writeShort(0);
        return writer.bytes();
    }

    private static byte[] metadataPayload(int entityId, byte[] entries) {
        PacketIO.Writer writer = new PacketIO.Writer();
        writer.writeVarInt(entityId);
        writer.writeBytes(entries);
        writer.writeByte(0xFF);
        return writer.bytes();
    }

    private static byte[] floatEntry(int index, float value) {
        PacketIO.Writer w = new PacketIO.Writer();
        w.writeByte(index);
        w.writeVarInt(3);
        w.writeFloat(value);
        return w.bytes();
    }

    private static byte[] booleanEntry(int index, boolean value) {
        PacketIO.Writer w = new PacketIO.Writer();
        w.writeByte(index);
        w.writeVarInt(8);
        w.writeBoolean(value);
        return w.bytes();
    }

    private static byte[] slotEntry(int index, int item, int count) {
        PacketIO.Writer w = new PacketIO.Writer();
        w.writeByte(index);
        w.writeVarInt(7);
        w.writeBoolean(true);
        w.writeVarInt(item);
        w.writeByte(count);
        w.writeBytes(Nbt.writeAnonymous(new Nbt.Tag.CompoundTag(Map.of())));
        return w.bytes();
    }

    private static byte[] holderEntry(int index, String assetId) {
        PacketIO.Writer w = new PacketIO.Writer();
        w.writeByte(index);
        w.writeVarInt(26);
        w.writeVarInt(0);
        w.writeBytes(Nbt.writeAnonymous(new Nbt.Tag.CompoundTag(
                Map.of("asset_id", new Nbt.Tag.StringTag(assetId)))));
        return w.bytes();
    }

    private static byte[] concat(byte[]... parts) {
        int length = 0;
        for (byte[] part : parts) {
            length += part.length;
        }
        byte[] out = new byte[length];
        int pos = 0;
        for (byte[] part : parts) {
            System.arraycopy(part, 0, out, pos, part.length);
            pos += part.length;
        }
        return out;
    }

    // ---- Bedrock パケットの読み直し ----

    /** AddEntity（type・位置）だけ読む。 */
    private static String decodeAddEntityType(byte[] payload) {
        ByteReader in = new ByteReader(payload);
        BedrockVarInt.readZigzag64(in); // unique id
        BedrockVarInt.readVarLong(in); // runtime id
        return BedrockVarInt.readString(in);
    }

    private static float[] decodeAddEntityPosition(byte[] payload) {
        ByteReader in = new ByteReader(payload);
        BedrockVarInt.readZigzag64(in);
        BedrockVarInt.readVarLong(in);
        BedrockVarInt.readString(in);
        return new float[]{in.readLittleFloat(), in.readLittleFloat(), in.readLittleFloat()};
    }

    /** AddPlayer（名前・ゲームモード）だけ読む。 */
    private static String decodeAddPlayerName(byte[] payload) {
        ByteReader in = new ByteReader(payload);
        in.readBigLong();
        in.readBigLong();
        return BedrockVarInt.readString(in);
    }

    /** SetEntityData のエントリをキー→値で読む（long／int／string）。 */
    private static java.util.Map<Integer, Object> decodeSetEntityData(byte[] payload) {
        ByteReader in = new ByteReader(payload);
        BedrockVarInt.readVarLong(in); // runtime id
        long count = BedrockVarInt.readUnsignedVarInt(in);
        java.util.Map<Integer, Object> map = new java.util.HashMap<>();
        for (long i = 0; i < count; i++) {
            int key = (int) BedrockVarInt.readUnsignedVarInt(in);
            int type = (int) BedrockVarInt.readUnsignedVarInt(in);
            Object value = switch (type) {
                case 0 -> in.readUnsignedByte();
                case 2 -> BedrockVarInt.readZigzag32(in);
                case 3 -> in.readLittleFloat();
                case 4 -> BedrockVarInt.readString(in);
                case 7 -> BedrockVarInt.readZigzag64(in);
                default -> throw new IllegalArgumentException("未対応の型: " + type);
            };
            map.put(key, value);
        }
        return map;
    }

    /** MoveEntityDelta のフラグと位置を読む。 */
    private static int decodeMoveDeltaFlags(byte[] payload) {
        ByteReader in = new ByteReader(payload);
        BedrockVarInt.readVarLong(in);
        return in.readLittleUnsignedShort();
    }

    /** InventoryTransaction（item use on entity）のペイロードを作る。 */
    private static byte[] inventoryTransactionPayload(long entityRuntimeId, int actionType) {
        ByteWriter out = new ByteWriter();
        BedrockVarInt.writeZigzag32(out, 0); // legacy request id
        BedrockVarInt.writeVarInt(out, 3); // transaction type: item use on entity
        BedrockVarInt.writeUnsignedVarInt(out, 0); // actions: なし
        BedrockVarInt.writeVarLong(out, entityRuntimeId);
        BedrockVarInt.writeVarInt(out, actionType);
        BedrockVarInt.writeZigzag32(out, 0); // hotbar slot
        BedrockVarInt.writeZigzag32(out, 0); // held item: air
        out.writeLittleFloat(0); // player pos
        out.writeLittleFloat(0);
        out.writeLittleFloat(0);
        out.writeLittleFloat(0); // click pos
        out.writeLittleFloat(0);
        out.writeLittleFloat(0);
        return out.bytes();
    }

    /** PlayerAction のペイロードを作る。 */
    private static byte[] playerActionPayload(int action) {
        ByteWriter out = new ByteWriter();
        BedrockVarInt.writeVarLong(out, 7); // runtime id（自分）
        BedrockVarInt.writeZigzag32(out, action);
        BedrockVarInt.writeZigzag32(out, 0); // position x
        BedrockVarInt.writeUnsignedVarInt(out, 64); // position y
        BedrockVarInt.writeZigzag32(out, 0); // position z
        BedrockVarInt.writeZigzag32(out, 0); // result position x
        BedrockVarInt.writeUnsignedVarInt(out, 64); // result position y
        BedrockVarInt.writeZigzag32(out, 0); // result position z
        BedrockVarInt.writeZigzag32(out, 0); // face
        return out.bytes();
    }

    // ---- テスト ----

    @Test
    void zombieSpawnMoveDespawn() throws Exception {
        Fixture fixture = new Fixture();
        fixture.playUntilStartGame();
        fixture.transport.clear();
        JavaBackendConnection.Listener listener = fixture.backend.listener;

        // ゾンビ出現（type 120）。
        listener.onSpawnEntity(BedrockEntityTranslator.parseSpawnEntity(
                spawnEntityPayload(101, UUID.randomUUID(), 120,
                        10.5, 64.0, -3.25, 0, 64, 32, 0), 765));
        GamePacket add = fixture.transport.find(BedrockProtocol.PACKET_ADD_ENTITY);
        assertNotNull(add, "AddEntity が送られること");
        assertEquals("minecraft:zombie", decodeAddEntityType(add.payload()));
        float[] pos = decodeAddEntityPosition(add.payload());
        assertEquals(10.5f, pos[0], 0.001f);
        assertEquals(64.0f, pos[1], 0.001f);
        assertEquals(-3.25f, pos[2], 0.001f);

        // メタデータ（体力 20・赤ちゃん）。
        listener.onEntityMetadata(BedrockEntityTranslator.parseMetadata(
                metadataPayload(101, concat(floatEntry(9, 20.0f), booleanEntry(16, true))),
                765, 120));
        GamePacket setData = fixture.transport.find(BedrockProtocol.PACKET_SET_ENTITY_DATA);
        assertNotNull(setData, "SetEntityData が送られること");
        java.util.Map<Integer, Object> data = decodeSetEntityData(setData.payload());
        assertEquals(20, data.get(BedrockEntityTranslator.BedrockEntityData.KEY_HEALTH));
        long flags = (Long) data.get(BedrockEntityTranslator.BedrockEntityData.KEY_FLAGS);
        assertTrue((flags & (1L << BedrockEntityTranslator.BedrockEntityData.FLAG_BABY)) != 0);

        // 相対移動。
        PacketIO.Writer move = new PacketIO.Writer();
        move.writeVarInt(101);
        move.writeShort(4096); // +1.0
        move.writeShort(0);
        move.writeShort(0);
        move.writeBoolean(true);
        listener.onEntityMove(BedrockEntityTranslator.parseRelativeMove(move.bytes()));
        GamePacket delta = fixture.transport.find(BedrockProtocol.PACKET_MOVE_ENTITY_DELTA);
        assertNotNull(delta, "MoveEntityDelta が送られること");
        int deltaFlags = decodeMoveDeltaFlags(delta.payload());
        assertEquals(BedrockProtocol.MOVE_DELTA_HAS_X | BedrockProtocol.MOVE_DELTA_HAS_Y
                | BedrockProtocol.MOVE_DELTA_HAS_Z | BedrockProtocol.MOVE_DELTA_ON_GROUND,
                deltaFlags);

        // 消滅。
        listener.onRemoveEntities(List.of(101));
        GamePacket remove = fixture.transport.find(BedrockProtocol.PACKET_REMOVE_ENTITY);
        assertNotNull(remove, "RemoveEntity が送られること");
        assertEquals(101, BedrockVarInt.readZigzag64(new ByteReader(remove.payload())));
    }

    @Test
    void otherPlayerSpawnWithPlayerList() throws Exception {
        Fixture fixture = new Fixture();
        fixture.playUntilStartGame();
        fixture.transport.clear();
        JavaBackendConnection.Listener listener = fixture.backend.listener;
        UUID alex = UUID.randomUUID();

        // 名前が先に届いた場合。
        listener.onPlayerInfoAdd(alex, "Alex", 0);
        listener.onSpawnEntity(BedrockEntityTranslator.parseSpawnEntity(
                spawnEntityPayload(42, alex, 124, 8.5, 64.0, 8.5, 0, 0, 0, 0), 765));
        GamePacket playerList = fixture.transport.find(BedrockProtocol.PACKET_PLAYER_LIST);
        assertNotNull(playerList, "PlayerList add が送られること");
        GamePacket addPlayer = fixture.transport.find(BedrockProtocol.PACKET_ADD_PLAYER);
        assertNotNull(addPlayer, "AddPlayer が送られること");
        assertEquals("Alex", decodeAddPlayerName(addPlayer.payload()));

        // 退場：Player Info Remove で RemoveEntity＋PlayerList remove。
        fixture.transport.clear();
        listener.onPlayerInfoRemove(List.of(alex));
        assertNotNull(fixture.transport.find(BedrockProtocol.PACKET_REMOVE_ENTITY),
                "RemoveEntity が送られること");
        assertNotNull(fixture.transport.find(BedrockProtocol.PACKET_PLAYER_LIST),
                "PlayerList remove が送られること");
    }

    @Test
    void otherPlayerSpawnBeforeNameIsDelayed() throws Exception {
        Fixture fixture = new Fixture();
        fixture.playUntilStartGame();
        fixture.transport.clear();
        JavaBackendConnection.Listener listener = fixture.backend.listener;
        UUID alex = UUID.randomUUID();

        // 出現が名前より先：まだ送らない。
        listener.onSpawnEntity(BedrockEntityTranslator.parseSpawnEntity(
                spawnEntityPayload(43, alex, 124, 9.5, 64.0, 9.5, 0, 0, 0, 0), 765));
        assertNull(fixture.transport.find(BedrockProtocol.PACKET_ADD_PLAYER),
                "名前が無いうちは AddPlayer を送らないこと");

        // 名前が届いたら送る。
        listener.onPlayerInfoAdd(alex, "Alex", 1);
        GamePacket addPlayer = fixture.transport.find(BedrockProtocol.PACKET_ADD_PLAYER);
        assertNotNull(addPlayer, "名前が届いたら AddPlayer が送られること");
        assertEquals("Alex", decodeAddPlayerName(addPlayer.payload()));
    }

    @Test
    void itemEntityNeedsMetadata() throws Exception {
        Fixture fixture = new Fixture();
        fixture.playUntilStartGame();
        fixture.transport.clear();
        JavaBackendConnection.Listener listener = fixture.backend.listener;

        // アイテム出現（type 55）：中身が無いのでまだ送らない。
        listener.onSpawnEntity(BedrockEntityTranslator.parseSpawnEntity(
                spawnEntityPayload(105, UUID.randomUUID(), 55,
                        3.0, 65.0, 3.0, 0, 0, 0, 0), 765));
        assertNull(fixture.transport.find(BedrockProtocol.PACKET_ADD_ITEM_ENTITY),
                "中身が無いうちは AddItemEntity を送らないこと");

        // メタデータ（スロット：石 3 個）で送る。
        listener.onEntityMetadata(BedrockEntityTranslator.parseMetadata(
                metadataPayload(105, slotEntry(8, 1, 3)), 765, 55));
        GamePacket addItem = fixture.transport.find(BedrockProtocol.PACKET_ADD_ITEM_ENTITY);
        assertNotNull(addItem, "AddItemEntity が送られること");
        ByteReader in = new ByteReader(addItem.payload());
        assertEquals(105, BedrockVarInt.readZigzag64(in));
        assertEquals(105, BedrockVarInt.readVarLong(in));
        assertEquals(1, BedrockVarInt.readZigzag32(in)); // 石の network id
    }

    @Test
    void paintingNeedsMetadata() throws Exception {
        Fixture fixture = new Fixture();
        fixture.playUntilStartGame();
        fixture.transport.clear();
        JavaBackendConnection.Listener listener = fixture.backend.listener;

        listener.onSpawnEntity(BedrockEntityTranslator.parseSpawnEntity(
                spawnEntityPayload(108, UUID.randomUUID(), 69,
                        10.0, 65.0, 10.0, 0, 0, 0, 2), 765));
        assertNull(fixture.transport.find(BedrockProtocol.PACKET_ADD_PAINTING),
                "題名が無いうちは AddPainting を送らないこと");

        listener.onEntityMetadata(BedrockEntityTranslator.parseMetadata(
                metadataPayload(108, holderEntry(8, "minecraft:kebab")), 765, 69));
        GamePacket painting = fixture.transport.find(BedrockProtocol.PACKET_ADD_PAINTING);
        assertNotNull(painting, "AddPainting が送られること");
        ByteReader in = new ByteReader(painting.payload());
        assertEquals(108, BedrockVarInt.readZigzag64(in));
        assertEquals(108, BedrockVarInt.readVarLong(in));
        in.readLittleFloat();
        in.readLittleFloat();
        in.readLittleFloat();
        assertEquals(2, BedrockVarInt.readZigzag32(in)); // direction
        assertEquals("Kebab", BedrockVarInt.readString(in));
    }

    @Test
    void xpOrbSpawns() throws Exception {
        Fixture fixture = new Fixture();
        fixture.playUntilStartGame();
        fixture.transport.clear();
        JavaBackendConnection.Listener listener = fixture.backend.listener;

        PacketIO.Writer writer = new PacketIO.Writer();
        writer.writeVarInt(106);
        writer.writeDouble(5.0);
        writer.writeDouble(64.0);
        writer.writeDouble(5.0);
        writer.writeShort(7);
        listener.onSpawnExperienceOrb(
                BedrockEntityTranslator.parseSpawnExperienceOrb(writer.bytes()));
        GamePacket orb = fixture.transport.find(BedrockProtocol.PACKET_SPAWN_EXPERIENCE_ORB);
        assertNotNull(orb, "SpawnExperienceOrb が送られること");
        ByteReader in = new ByteReader(orb.payload());
        assertEquals(5.0f, in.readLittleFloat(), 0.001f);
        assertEquals(64.0f, in.readLittleFloat(), 0.001f);
        assertEquals(5.0f, in.readLittleFloat(), 0.001f);
        assertEquals(7, BedrockVarInt.readZigzag32(in));
    }

    @Test
    void selfIsNeverSentAsEntity() throws Exception {
        Fixture fixture = new Fixture();
        fixture.playUntilStartGame();
        fixture.transport.clear();
        JavaBackendConnection.Listener listener = fixture.backend.listener;

        // 自分（javaEntityId=7）の Spawn Entity は送らない。
        listener.onSpawnEntity(BedrockEntityTranslator.parseSpawnEntity(
                spawnEntityPayload(7, UUID.randomUUID(), 124,
                        8.5, 64.0, 8.5, 0, 0, 0, 0), 765));
        assertNull(fixture.transport.find(BedrockProtocol.PACKET_ADD_PLAYER),
                "自分自身は AddPlayer で送らないこと");
        assertNull(fixture.transport.find(BedrockProtocol.PACKET_ADD_ENTITY),
                "自分自身は AddEntity で送らないこと");
    }

    @Test
    void attackAndInteractViaInventoryTransaction() throws Exception {
        Fixture fixture = new Fixture();
        fixture.playUntilStartGame();
        // ゾンビを出しておく（殴る対象）。
        fixture.backend.listener.onSpawnEntity(BedrockEntityTranslator.parseSpawnEntity(
                spawnEntityPayload(101, UUID.randomUUID(), 120,
                        10.5, 64.0, -3.25, 0, 0, 0, 0), 765));

        fixture.sendGamePacket(BedrockProtocol.PACKET_INVENTORY_TRANSACTION,
                inventoryTransactionPayload(101, 1), 10); // 攻撃
        assertEquals(1, fixture.backend.useEntities.size());
        assertArrayEquals(new int[]{101, 1, 0}, fixture.backend.useEntities.get(0));

        fixture.sendGamePacket(BedrockProtocol.PACKET_INVENTORY_TRANSACTION,
                inventoryTransactionPayload(101, 0), 11); // インタラクト
        assertEquals(2, fixture.backend.useEntities.size());
        assertArrayEquals(new int[]{101, 0, 0}, fixture.backend.useEntities.get(1));
    }

    @Test
    void sneakAndSprintViaPlayerAction() throws Exception {
        Fixture fixture = new Fixture();
        fixture.playUntilStartGame();

        fixture.sendGamePacket(BedrockProtocol.PACKET_PLAYER_ACTION,
                playerActionPayload(11), 10); // start sneak
        fixture.sendGamePacket(BedrockProtocol.PACKET_PLAYER_ACTION,
                playerActionPayload(9), 11); // start sprint
        fixture.sendGamePacket(BedrockProtocol.PACKET_PLAYER_ACTION,
                playerActionPayload(12), 12); // stop sneak
        fixture.sendGamePacket(BedrockProtocol.PACKET_PLAYER_ACTION,
                playerActionPayload(10), 13); // stop sprint
        assertEquals(List.of(0, 3, 1, 4), fixture.backend.entityActions);
    }

    @Test
    void entityPacketsBufferedBeforePlaying() throws Exception {
        Fixture fixture = new Fixture();
        fixture.establishRakNet();
        fixture.sendGamePacket(BedrockProtocol.PACKET_LOGIN,
                TestJwt.loginPayload(630, "Steve", "2535461234567890",
                        "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee"),
                3);
        fixture.backend.listener.onJoined(7);
        fixture.backend.listener.onSpawnPosition(8.5, 64.0, 8.5);
        // まだ PLAYING ではない（リソースパック未完了）。
        assertEquals(BedrockSession.State.LOGGED_IN, fixture.session.state());

        fixture.backend.listener.onSpawnEntity(BedrockEntityTranslator.parseSpawnEntity(
                spawnEntityPayload(101, UUID.randomUUID(), 120,
                        10.5, 64.0, -3.25, 0, 0, 0, 0), 765));
        assertNull(fixture.transport.find(BedrockProtocol.PACKET_ADD_ENTITY),
                "PLAYING 前は AddEntity を送らないこと");

        // リソースパック完了で PLAYING になり、溜めていた分が送られる。
        fixture.sendGamePacket(BedrockProtocol.PACKET_RESOURCE_PACK_CLIENT_RESPONSE,
                new byte[]{3}, 4);
        fixture.sendGamePacket(BedrockProtocol.PACKET_RESOURCE_PACK_CLIENT_RESPONSE,
                new byte[]{4}, 5);
        assertEquals(BedrockSession.State.PLAYING, fixture.session.state());
        assertNotNull(fixture.transport.find(BedrockProtocol.PACKET_ADD_ENTITY),
                "PLAYING になったら溜めていた AddEntity が送られること");
    }

    @Test
    void closeClearsEntityState() throws Exception {
        Fixture fixture = new Fixture();
        fixture.playUntilStartGame();
        fixture.backend.listener.onSpawnEntity(BedrockEntityTranslator.parseSpawnEntity(
                spawnEntityPayload(101, UUID.randomUUID(), 120,
                        10.5, 64.0, -3.25, 0, 0, 0, 0), 765));
        assertNotNull(fixture.transport.find(BedrockProtocol.PACKET_ADD_ENTITY));

        fixture.session.close();
        fixture.transport.clear();
        // 切断後のパケットは送られない。
        fixture.backend.listener.onRemoveEntities(List.of(101));
        fixture.backend.listener.onSpawnEntity(BedrockEntityTranslator.parseSpawnEntity(
                spawnEntityPayload(102, UUID.randomUUID(), 73,
                        1.0, 64.0, 1.0, 0, 0, 0, 0), 765));
        assertTrue(fixture.transport.sent.isEmpty(), "切断後は送らないこと");
        assertFalse(fixture.session.state() == BedrockSession.State.PLAYING);
    }

    @Test
    void passengersLinkIsSent() throws Exception {
        Fixture fixture = new Fixture();
        fixture.playUntilStartGame();
        fixture.transport.clear();
        JavaBackendConnection.Listener listener = fixture.backend.listener;

        // 馬（type 50）とプレイヤー（type 124）を出す。
        listener.onSpawnEntity(BedrockEntityTranslator.parseSpawnEntity(
                spawnEntityPayload(200, UUID.randomUUID(), 50,
                        10.0, 64.0, 10.0, 0, 0, 0, 0), 765));
        UUID rider = UUID.randomUUID();
        listener.onPlayerInfoAdd(rider, "Rider", 0);
        listener.onSpawnEntity(BedrockEntityTranslator.parseSpawnEntity(
                spawnEntityPayload(201, rider, 124, 10.0, 65.5, 10.0, 0, 0, 0, 0), 765));
        fixture.transport.clear();

        // 201 が 200 に乗る。
        PacketIO.Writer passengers = new PacketIO.Writer();
        passengers.writeVarInt(200);
        passengers.writeVarInt(1);
        passengers.writeVarInt(201);
        listener.onSetPassengers(
                BedrockEntityTranslator.parseSetPassengers(passengers.bytes()));
        GamePacket link = fixture.transport.find(BedrockProtocol.PACKET_SET_ENTITY_LINK);
        assertNotNull(link, "SetEntityLink が送られること");
        ByteReader in = new ByteReader(link.payload());
        assertEquals(200, BedrockVarInt.readZigzag64(in));
        assertEquals(201, BedrockVarInt.readZigzag64(in));
        assertEquals(BedrockProtocol.ENTITY_LINK_RIDER, in.readUnsignedByte());
    }

    @Test
    void equipmentAndEffectAreSent() throws Exception {
        Fixture fixture = new Fixture();
        fixture.playUntilStartGame();
        fixture.transport.clear();
        JavaBackendConnection.Listener listener = fixture.backend.listener;

        listener.onSpawnEntity(BedrockEntityTranslator.parseSpawnEntity(
                spawnEntityPayload(101, UUID.randomUUID(), 120,
                        10.5, 64.0, -3.25, 0, 0, 0, 0), 765));
        fixture.transport.clear();

        // 装備（メインハンド：ダイヤの剣）。
        PacketIO.Writer equipment = new PacketIO.Writer();
        equipment.writeVarInt(101);
        equipment.writeByte(0); // mainhand、最後
        equipment.writeBoolean(true);
        equipment.writeVarInt(834); // ダイヤの剣
        equipment.writeByte(1);
        equipment.writeBytes(Nbt.writeAnonymous(new Nbt.Tag.CompoundTag(Map.of())));
        listener.onEntityEquipment(
                BedrockEntityTranslator.parseEntityEquipment(equipment.bytes(), 765));
        GamePacket mobEquipment = fixture.transport.find(BedrockProtocol.PACKET_MOB_EQUIPMENT);
        assertNotNull(mobEquipment, "MobEquipment が送られること");
        ByteReader ein = new ByteReader(mobEquipment.payload());
        assertEquals(101, BedrockVarInt.readVarLong(ein));
        // ダイヤの剣の Bedrock id は 1.20.50 のレジストリで 319
        //（旧い静的表の 318 は 1.26 世代の値だった）。
        assertEquals(319, BedrockVarInt.readZigzag32(ein));
        assertNotNull(fixture.transport.find(BedrockProtocol.PACKET_MOB_ARMOR_EQUIPMENT),
                "MobArmorEquipment が送られること");

        // エフェクト（速度）。
        fixture.transport.clear();
        PacketIO.Writer effect = new PacketIO.Writer();
        effect.writeVarInt(101);
        effect.writeVarInt(0); // speed
        effect.writeByte(1);
        effect.writeVarInt(200);
        effect.writeByte(0x02);
        effect.writeBoolean(false);
        listener.onEntityEffect(
                BedrockEntityTranslator.parseEntityEffect(effect.bytes(), 765));
        GamePacket mobEffect = fixture.transport.find(BedrockProtocol.PACKET_MOB_EFFECT);
        assertNotNull(mobEffect, "MobEffect が送られること");
        ByteReader min = new ByteReader(mobEffect.payload());
        assertEquals(101, BedrockVarInt.readVarLong(min));
        assertEquals(BedrockProtocol.MOB_EFFECT_ADD, min.readUnsignedByte());
        assertEquals(1, BedrockVarInt.readZigzag32(min)); // Bedrock の速度は 1
    }

    @Test
    void attributesUpdateHealth() throws Exception {
        Fixture fixture = new Fixture();
        fixture.playUntilStartGame();
        fixture.transport.clear();
        JavaBackendConnection.Listener listener = fixture.backend.listener;

        listener.onSpawnEntity(BedrockEntityTranslator.parseSpawnEntity(
                spawnEntityPayload(101, UUID.randomUUID(), 120,
                        10.5, 64.0, -3.25, 0, 0, 0, 0), 765));
        fixture.transport.clear();

        PacketIO.Writer attributes = new PacketIO.Writer();
        attributes.writeVarInt(101);
        attributes.writeVarInt(1);
        attributes.writeString("minecraft:generic.max_health");
        attributes.writeDouble(40.0);
        attributes.writeVarInt(0);
        listener.onUpdateAttributes(
                BedrockEntityTranslator.parseUpdateAttributes(attributes.bytes(), 765));
        GamePacket update = fixture.transport.find(BedrockProtocol.PACKET_UPDATE_ATTRIBUTES);
        assertNotNull(update, "UpdateAttributes が送られること");
        ByteReader in = new ByteReader(update.payload());
        assertEquals(101, BedrockVarInt.readVarLong(in));
        assertEquals(1, BedrockVarInt.readUnsignedVarInt(in));
        in.readLittleFloat(); // min
        assertEquals(40.0f, in.readLittleFloat(), 0.001f); // max
    }
}
