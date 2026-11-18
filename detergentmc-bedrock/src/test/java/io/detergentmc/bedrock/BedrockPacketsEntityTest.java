package io.detergentmc.bedrock;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.detergentmc.bedrock.BedrockVarInt.ByteReader;
import io.detergentmc.bedrock.BedrockVarInt.ByteWriter;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * エンティティ系 Bedrock パケットの符号化テスト。
 * minecraft-data Bedrock 1.20.40 のレイアウトで読み直し、種別・位置・
 * 向き・名前・体力が正しいことを確認する。
 */
class BedrockPacketsEntityTest {

    /** AddEntity を読む（type・位置・速度・向き・属性まで）。 */
    private record AddEntityData(String type, float x, float y, float z,
            float health, float maxHealth, int metadataCount) {
    }

    private static AddEntityData decodeAddEntity(byte[] payload) {
        ByteReader in = new ByteReader(payload);
        long uniqueId = BedrockVarInt.readZigzag64(in);
        long runtimeId = BedrockVarInt.readVarLong(in);
        assertEquals(uniqueId, runtimeId);
        String type = BedrockVarInt.readString(in);
        float x = in.readLittleFloat();
        float y = in.readLittleFloat();
        float z = in.readLittleFloat();
        in.readLittleFloat(); // vel x
        in.readLittleFloat(); // vel y
        in.readLittleFloat(); // vel z
        in.readLittleFloat(); // pitch
        in.readLittleFloat(); // yaw
        in.readLittleFloat(); // head yaw
        in.readLittleFloat(); // body yaw
        long attrCount = BedrockVarInt.readUnsignedVarInt(in);
        float health = 0;
        float maxHealth = 0;
        for (long i = 0; i < attrCount; i++) {
            String name = BedrockVarInt.readString(in);
            in.readLittleFloat(); // min
            float value = in.readLittleFloat();
            float max = in.readLittleFloat();
            if ("minecraft:health".equals(name)) {
                health = value;
                maxHealth = max;
            }
        }
        long metadataCount = BedrockVarInt.readUnsignedVarInt(in);
        // metadata は読み飛ばす（エントリの形は SetEntityData のテストで確認する）。
        for (long i = 0; i < metadataCount; i++) {
            BedrockVarInt.readUnsignedVarInt(in); // key
            int entryType = (int) BedrockVarInt.readUnsignedVarInt(in);
            skipMetadataValue(in, entryType);
        }
        long ints = BedrockVarInt.readUnsignedVarInt(in);
        long floats = BedrockVarInt.readUnsignedVarInt(in);
        assertEquals(0, ints);
        assertEquals(0, floats);
        long links = BedrockVarInt.readUnsignedVarInt(in);
        assertEquals(0, links);
        assertEquals(0, in.remaining());
        return new AddEntityData(type, x, y, z, health, maxHealth, (int) metadataCount);
    }

    private static void skipMetadataValue(ByteReader in, int type) {
        switch (type) {
            case 0 -> in.readUnsignedByte();
            case 1 -> in.readLittleShort();
            case 2 -> BedrockVarInt.readZigzag32(in);
            case 3 -> in.readLittleFloat();
            case 4 -> BedrockVarInt.readString(in);
            case 6 -> {
                BedrockVarInt.readZigzag32(in);
                BedrockVarInt.readUnsignedVarInt(in);
                BedrockVarInt.readZigzag32(in);
            }
            case 7 -> BedrockVarInt.readZigzag64(in);
            case 8 -> {
                in.readLittleFloat();
                in.readLittleFloat();
                in.readLittleFloat();
            }
            default -> throw new IllegalArgumentException("未知のメタデータ型です: " + type);
        }
    }

    /** AddPlayer を読む（名前・位置・ゲームモードまで）。 */
    private record AddPlayerData(UUID uuid, String username, float x, float y, float z,
            int gamemode, long uniqueId) {
    }

    private static AddPlayerData decodeAddPlayer(byte[] payload) {
        ByteReader in = new ByteReader(payload);
        long msb = in.readBigLong();
        long lsb = in.readBigLong();
        UUID uuid = new UUID(msb, lsb);
        String username = BedrockVarInt.readString(in);
        long runtimeId = BedrockVarInt.readVarLong(in);
        BedrockVarInt.readString(in); // platform chat id
        float x = in.readLittleFloat();
        float y = in.readLittleFloat();
        float z = in.readLittleFloat();
        in.readLittleFloat(); // vel x
        in.readLittleFloat(); // vel y
        in.readLittleFloat(); // vel z
        in.readLittleFloat(); // pitch
        in.readLittleFloat(); // yaw
        in.readLittleFloat(); // head yaw
        int heldNetworkId = BedrockVarInt.readZigzag32(in);
        assertEquals(0, heldNetworkId, "held item は空");
        int gamemode = BedrockVarInt.readZigzag32(in);
        // metadata・properties・unique id・権限・abilities・links・device は
        // 形だけ読み飛ばす。
        long metadataCount = BedrockVarInt.readUnsignedVarInt(in);
        for (long i = 0; i < metadataCount; i++) {
            BedrockVarInt.readUnsignedVarInt(in);
            int entryType = (int) BedrockVarInt.readUnsignedVarInt(in);
            skipMetadataValue(in, entryType);
        }
        BedrockVarInt.readUnsignedVarInt(in); // properties ints
        BedrockVarInt.readUnsignedVarInt(in); // properties floats
        long uniqueId = in.readLittleLong();
        assertEquals(runtimeId, uniqueId);
        in.readUnsignedByte(); // permission level
        in.readUnsignedByte(); // command permission
        int abilities = in.readUnsignedByte();
        assertEquals(0, abilities);
        assertEquals(0, BedrockVarInt.readUnsignedVarInt(in)); // links
        BedrockVarInt.readString(in); // device id
        in.readLittleInt(); // device os
        assertEquals(0, in.remaining());
        return new AddPlayerData(uuid, username, x, y, z, gamemode, uniqueId);
    }

    /** Item を読む（network id・count・block runtime id）。 */
    private record ItemData(int networkId, int count, int blockRuntimeId) {
    }

    private static ItemData decodeItem(ByteReader in) {
        int networkId = BedrockVarInt.readZigzag32(in);
        if (networkId == 0) {
            return new ItemData(0, 0, 0);
        }
        int count = in.readLittleUnsignedShort();
        BedrockVarInt.readUnsignedVarInt(in); // metadata
        int hasStackId = in.readUnsignedByte();
        if (hasStackId != 0) {
            BedrockVarInt.readZigzag32(in);
        }
        int blockRuntimeId = BedrockVarInt.readZigzag32(in);
        long extraLength = BedrockVarInt.readUnsignedVarInt(in);
        in.readBytes((int) extraLength);
        return new ItemData(networkId, count, blockRuntimeId);
    }

    /** MetadataDictionary をキー→値の対応で読む（long／int／string のみ）。 */
    private static java.util.Map<Integer, Object> decodeMetadataDictionary(ByteReader in) {
        java.util.Map<Integer, Object> map = new java.util.HashMap<>();
        long count = BedrockVarInt.readUnsignedVarInt(in);
        for (long i = 0; i < count; i++) {
            int key = (int) BedrockVarInt.readUnsignedVarInt(in);
            int type = (int) BedrockVarInt.readUnsignedVarInt(in);
            Object value = switch (type) {
                case 0 -> in.readUnsignedByte();
                case 1 -> in.readLittleShort();
                case 2 -> BedrockVarInt.readZigzag32(in);
                case 3 -> in.readLittleFloat();
                case 4 -> BedrockVarInt.readString(in);
                case 7 -> BedrockVarInt.readZigzag64(in);
                default -> throw new IllegalArgumentException("未対応のメタデータ型です: " + type);
            };
            map.put(key, value);
        }
        return map;
    }

    @Test
    void addEntityRoundTrip() {
        BedrockEntityTranslator.BedrockEntityData data =
                new BedrockEntityTranslator.BedrockEntityData();
        data.addLong(BedrockEntityTranslator.BedrockEntityData.KEY_FLAGS,
                (1L << BedrockEntityTranslator.BedrockEntityData.FLAG_BABY)
                        | (1L << BedrockEntityTranslator.BedrockEntityData.FLAG_HAS_COLLISION));
        data.addInt(BedrockEntityTranslator.BedrockEntityData.KEY_HEALTH, 20);
        byte[] payload = BedrockPackets.encodeAddEntity(101, "minecraft:zombie",
                10.5f, 64.0f, -3.25f, 0, 90, 45,
                0, 0, 0, 20, 20, data);
        AddEntityData decoded = decodeAddEntity(payload);
        assertEquals("minecraft:zombie", decoded.type());
        assertEquals(10.5f, decoded.x(), 0.001f);
        assertEquals(64.0f, decoded.y(), 0.001f);
        assertEquals(-3.25f, decoded.z(), 0.001f);
        assertEquals(20.0f, decoded.health(), 0.001f);
        assertEquals(20.0f, decoded.maxHealth(), 0.001f);
        assertEquals(2, decoded.metadataCount());
    }

    @Test
    void addPlayerRoundTrip() {
        UUID uuid = UUID.randomUUID();
        byte[] payload = BedrockPackets.encodeAddPlayer(uuid, "Alex", 42,
                8.5f, 64.0f, 8.5f, 0, 180, 180, 0,
                new BedrockEntityTranslator.BedrockEntityData());
        AddPlayerData decoded = decodeAddPlayer(payload);
        assertEquals(uuid, decoded.uuid());
        assertEquals("Alex", decoded.username());
        assertEquals(8.5f, decoded.x(), 0.001f);
        assertEquals(0, decoded.gamemode());
        assertEquals(42, decoded.uniqueId());
    }

    @Test
    void addItemEntityRoundTrip() {
        BedrockEntityTranslator.BedrockItem stone =
                new BedrockEntityTranslator.BedrockItem(1, 3, 1774);
        byte[] payload = BedrockPackets.encodeAddItemEntity(105, stone,
                3.0f, 65.0f, 3.0f, 0, 0, 0,
                new BedrockEntityTranslator.BedrockEntityData());
        ByteReader in = new ByteReader(payload);
        assertEquals(105, BedrockVarInt.readZigzag64(in));
        assertEquals(105, BedrockVarInt.readVarLong(in));
        ItemData item = decodeItem(in);
        assertEquals(1, item.networkId());
        assertEquals(3, item.count());
        assertEquals(1774, item.blockRuntimeId());
        assertEquals(3.0f, in.readLittleFloat(), 0.001f);
        assertEquals(65.0f, in.readLittleFloat(), 0.001f);
        assertEquals(3.0f, in.readLittleFloat(), 0.001f);
        in.readLittleFloat(); // vel x
        in.readLittleFloat(); // vel y
        in.readLittleFloat(); // vel z
        assertEquals(0, BedrockVarInt.readUnsignedVarInt(in)); // metadata: なし
        assertEquals(false, in.readBoolean()); // is from fishing
        assertEquals(0, in.remaining());
    }

    @Test
    void removeEntityRoundTrip() {
        byte[] payload = BedrockPackets.encodeRemoveEntity(101);
        ByteReader in = new ByteReader(payload);
        assertEquals(101, BedrockVarInt.readZigzag64(in));
        assertEquals(0, in.remaining());
    }

    @Test
    void moveEntityAndDeltaRoundTrip() {
        byte[] move = BedrockPackets.encodeMoveEntity(101,
                10.0f, 64.0f, -5.0f, 64, 0, 32, true, false);
        ByteReader in = new ByteReader(move);
        assertEquals(101, BedrockVarInt.readVarLong(in));
        assertEquals(1, in.readUnsignedByte()); // on ground
        assertEquals(10.0f, in.readLittleFloat(), 0.001f);
        assertEquals(64.0f, in.readLittleFloat(), 0.001f);
        assertEquals(-5.0f, in.readLittleFloat(), 0.001f);
        assertEquals(64, in.readUnsignedByte()); // yaw
        assertEquals(0, in.readUnsignedByte()); // pitch
        assertEquals(32, in.readUnsignedByte()); // head yaw
        assertEquals(0, in.remaining());

        byte[] delta = BedrockPackets.encodeMoveEntityDelta(101,
                BedrockProtocol.MOVE_DELTA_HAS_X | BedrockProtocol.MOVE_DELTA_HAS_Y
                        | BedrockProtocol.MOVE_DELTA_HAS_Z | BedrockProtocol.MOVE_DELTA_ON_GROUND,
                11.0f, 65.0f, -4.0f, null, null, null);
        ByteReader din = new ByteReader(delta);
        assertEquals(101, BedrockVarInt.readVarLong(din));
        int flags = din.readLittleUnsignedShort();
        assertEquals(1 | 2 | 4 | 64, flags);
        assertEquals(11.0f, din.readLittleFloat(), 0.001f);
        assertEquals(65.0f, din.readLittleFloat(), 0.001f);
        assertEquals(-4.0f, din.readLittleFloat(), 0.001f);
        assertEquals(0, din.remaining());
    }

    @Test
    void movePlayerEntityRoundTrip() {
        byte[] payload = BedrockPackets.encodeMovePlayerEntity(42,
                8.5f, 64.0f, 8.5f, 10, 20, 30, 2, true);
        ByteReader in = new ByteReader(payload);
        assertEquals(42, BedrockVarInt.readVarLong(in));
        assertEquals(8.5f, in.readLittleFloat(), 0.001f);
        assertEquals(64.0f, in.readLittleFloat(), 0.001f);
        assertEquals(8.5f, in.readLittleFloat(), 0.001f);
        assertEquals(10.0f, in.readLittleFloat(), 0.001f);
        assertEquals(20.0f, in.readLittleFloat(), 0.001f);
        assertEquals(30.0f, in.readLittleFloat(), 0.001f);
        assertEquals(2, in.readUnsignedByte()); // mode: teleport
        assertTrue(in.readBoolean()); // on ground
        assertEquals(0, BedrockVarInt.readUnsignedVarInt(in)); // ridden
        in.readLittleInt(); // cause
        BedrockVarInt.readZigzag32(in); // source entity type
        assertEquals(0, BedrockVarInt.readUnsignedVarInt(in)); // tick
        assertEquals(0, in.remaining());
    }

    @Test
    void setEntityDataRoundTrip() {
        BedrockEntityTranslator.BedrockEntityData data =
                new BedrockEntityTranslator.BedrockEntityData();
        data.addLong(BedrockEntityTranslator.BedrockEntityData.KEY_FLAGS, 1L << 11); // baby
        data.addInt(BedrockEntityTranslator.BedrockEntityData.KEY_HEALTH, 15);
        data.addString(BedrockEntityTranslator.BedrockEntityData.KEY_NAMETAG, "Boss");
        byte[] payload = BedrockPackets.encodeSetEntityData(101, data);
        ByteReader in = new ByteReader(payload);
        assertEquals(101, BedrockVarInt.readVarLong(in));
        java.util.Map<Integer, Object> decoded = decodeMetadataDictionary(in);
        assertEquals(1L << 11, decoded.get(0));
        assertEquals(15, decoded.get(1));
        assertEquals("Boss", decoded.get(4));
        assertEquals(0, BedrockVarInt.readUnsignedVarInt(in)); // properties ints
        assertEquals(0, BedrockVarInt.readUnsignedVarInt(in)); // properties floats
        assertEquals(0, BedrockVarInt.readUnsignedVarInt(in)); // tick
        assertEquals(0, in.remaining());
    }

    @Test
    void setEntityMotionAndLinkRoundTrip() {
        byte[] motion = BedrockPackets.encodeSetEntityMotion(101, 1.0f, 0.5f, 0.0f);
        ByteReader in = new ByteReader(motion);
        assertEquals(101, BedrockVarInt.readVarLong(in));
        assertEquals(1.0f, in.readLittleFloat(), 0.001f);
        assertEquals(0.5f, in.readLittleFloat(), 0.001f);
        assertEquals(0.0f, in.readLittleFloat(), 0.001f);
        assertEquals(0, in.remaining());

        byte[] link = BedrockPackets.encodeSetEntityLink(200, 201,
                BedrockProtocol.ENTITY_LINK_RIDER);
        ByteReader lin = new ByteReader(link);
        assertEquals(200, BedrockVarInt.readZigzag64(lin));
        assertEquals(201, BedrockVarInt.readZigzag64(lin));
        assertEquals(1, lin.readUnsignedByte());
        assertTrue(lin.readBoolean()); // immediate
        assertEquals(false, lin.readBoolean()); // rider initiated
        assertEquals(0, lin.remaining());
    }

    @Test
    void mobEquipmentAndArmorRoundTrip() {
        BedrockEntityTranslator.BedrockItem sword =
                new BedrockEntityTranslator.BedrockItem(318, 1, 0);
        byte[] equipment = BedrockPackets.encodeMobEquipment(101, sword, 0, 0);
        ByteReader in = new ByteReader(equipment);
        assertEquals(101, BedrockVarInt.readVarLong(in));
        ItemData item = decodeItem(in);
        assertEquals(318, item.networkId());
        assertEquals(0, in.readUnsignedByte()); // slot
        assertEquals(0, in.readUnsignedByte()); // selected slot
        assertEquals(0, in.readUnsignedByte()); // window id
        assertEquals(0, in.remaining());

        BedrockEntityTranslator.BedrockItem helmet =
                new BedrockEntityTranslator.BedrockItem(350, 1, 0);
        byte[] armor = BedrockPackets.encodeMobArmorEquipment(101, helmet,
                BedrockEntityTranslator.BedrockItem.air(),
                BedrockEntityTranslator.BedrockItem.air(),
                BedrockEntityTranslator.BedrockItem.air());
        ByteReader ain = new ByteReader(armor);
        assertEquals(101, BedrockVarInt.readVarLong(ain));
        assertEquals(350, decodeItem(ain).networkId());
        assertEquals(0, decodeItem(ain).networkId());
        assertEquals(0, decodeItem(ain).networkId());
        assertEquals(0, decodeItem(ain).networkId());
        assertEquals(0, ain.remaining());
    }

    @Test
    void mobEffectAndEntityEventAndAttributesRoundTrip() {
        byte[] effect = BedrockPackets.encodeMobEffect(101,
                BedrockProtocol.MOB_EFFECT_ADD, 1, 2, true, 200);
        ByteReader ein = new ByteReader(effect);
        assertEquals(101, BedrockVarInt.readVarLong(ein));
        assertEquals(1, ein.readUnsignedByte());
        assertEquals(1, BedrockVarInt.readZigzag32(ein));
        assertEquals(2, BedrockVarInt.readZigzag32(ein));
        assertTrue(ein.readBoolean());
        assertEquals(200, BedrockVarInt.readZigzag32(ein));
        assertEquals(0, ein.remaining());

        byte[] event = BedrockPackets.encodeEntityEvent(101,
                BedrockProtocol.ENTITY_EVENT_HURT, 0);
        ByteReader vin = new ByteReader(event);
        assertEquals(101, BedrockVarInt.readVarLong(vin));
        assertEquals(2, vin.readUnsignedByte());
        assertEquals(0, BedrockVarInt.readZigzag32(vin));
        assertEquals(0, vin.remaining());

        byte[] attributes = BedrockPackets.encodeUpdateAttributes(101, 15.0f, 30.0f);
        ByteReader ain = new ByteReader(attributes);
        assertEquals(101, BedrockVarInt.readVarLong(ain));
        assertEquals(1, BedrockVarInt.readUnsignedVarInt(ain));
        assertEquals(0.0f, ain.readLittleFloat(), 0.001f); // min
        assertEquals(30.0f, ain.readLittleFloat(), 0.001f); // max
        assertEquals(15.0f, ain.readLittleFloat(), 0.001f); // current
        assertEquals(30.0f, ain.readLittleFloat(), 0.001f); // default
        assertEquals("minecraft:health", BedrockVarInt.readString(ain));
        assertEquals(0, BedrockVarInt.readUnsignedVarInt(ain)); // modifiers
        assertEquals(0, BedrockVarInt.readUnsignedVarInt(ain)); // tick
        assertEquals(0, ain.remaining());
    }

    @Test
    void spawnExperienceOrbAndPaintingRoundTrip() {
        byte[] orb = BedrockPackets.encodeSpawnExperienceOrb(5.0f, 64.0f, 5.0f, 7);
        ByteReader oin = new ByteReader(orb);
        assertEquals(5.0f, oin.readLittleFloat(), 0.001f);
        assertEquals(64.0f, oin.readLittleFloat(), 0.001f);
        assertEquals(5.0f, oin.readLittleFloat(), 0.001f);
        assertEquals(7, BedrockVarInt.readZigzag32(oin));
        assertEquals(0, oin.remaining());

        byte[] painting = BedrockPackets.encodeAddPainting(108,
                10.0f, 65.0f, 10.0f, 2, "Kebab");
        ByteReader pin = new ByteReader(painting);
        assertEquals(108, BedrockVarInt.readZigzag64(pin));
        assertEquals(108, BedrockVarInt.readVarLong(pin));
        assertEquals(10.0f, pin.readLittleFloat(), 0.001f);
        assertEquals(65.0f, pin.readLittleFloat(), 0.001f);
        assertEquals(10.0f, pin.readLittleFloat(), 0.001f);
        assertEquals(2, BedrockVarInt.readZigzag32(pin));
        assertEquals("Kebab", BedrockVarInt.readString(pin));
        assertEquals(0, pin.remaining());
    }

    @Test
    void playerListAddAndRemoveRoundTrip() {
        UUID uuid = UUID.randomUUID();
        byte[] add = BedrockPackets.encodePlayerListAdd(uuid, 42, "Alex");
        ByteReader in = new ByteReader(add);
        assertEquals(0, in.readUnsignedByte()); // type: add
        assertEquals(1, BedrockVarInt.readUnsignedVarInt(in)); // records
        assertEquals(uuid.getMostSignificantBits(), in.readBigLong());
        assertEquals(uuid.getLeastSignificantBits(), in.readBigLong());
        assertEquals(42, BedrockVarInt.readZigzag64(in));
        assertEquals("Alex", BedrockVarInt.readString(in));
        assertEquals("", BedrockVarInt.readString(in)); // xbox user id
        assertEquals("", BedrockVarInt.readString(in)); // platform chat id
        assertEquals(0, in.readLittleInt()); // build platform
        // skin（空）
        assertEquals("", BedrockVarInt.readString(in)); // skin id
        assertEquals("", BedrockVarInt.readString(in)); // play fab id
        assertEquals("", BedrockVarInt.readString(in)); // skin resource pack
        assertEquals(0, in.readLittleInt()); // skin image width
        assertEquals(0, in.readLittleInt()); // skin image height
        assertEquals(0, BedrockVarInt.readUnsignedVarInt(in)); // skin image data
        assertEquals(0, in.readLittleInt()); // animations
        assertEquals(0, in.readLittleInt()); // cape width
        assertEquals(0, in.readLittleInt()); // cape height
        assertEquals(0, BedrockVarInt.readUnsignedVarInt(in)); // cape data
        assertEquals("", BedrockVarInt.readString(in)); // geometry data
        assertEquals("", BedrockVarInt.readString(in)); // geometry data version
        assertEquals("", BedrockVarInt.readString(in)); // animation data
        assertEquals("", BedrockVarInt.readString(in)); // cape id
        assertEquals("", BedrockVarInt.readString(in)); // full skin id
        assertEquals("", BedrockVarInt.readString(in)); // arm size
        assertEquals("", BedrockVarInt.readString(in)); // skin color
        assertEquals(0, in.readLittleInt()); // personal pieces
        assertEquals(0, in.readLittleInt()); // piece tint colors
        assertEquals(false, in.readBoolean()); // premium
        assertEquals(false, in.readBoolean()); // persona
        assertEquals(false, in.readBoolean()); // cape on classic
        assertEquals(false, in.readBoolean()); // primary user
        assertEquals(false, in.readBoolean()); // overriding player appearance
        assertEquals(false, in.readBoolean()); // is teacher
        assertEquals(false, in.readBoolean()); // is host
        assertEquals(false, in.readBoolean()); // verified
        assertEquals(0, in.remaining());

        byte[] remove = BedrockPackets.encodePlayerListRemove(uuid);
        ByteReader rin = new ByteReader(remove);
        assertEquals(1, rin.readUnsignedByte()); // type: remove
        assertEquals(1, BedrockVarInt.readUnsignedVarInt(rin));
        assertEquals(uuid.getMostSignificantBits(), rin.readBigLong());
        assertEquals(uuid.getLeastSignificantBits(), rin.readBigLong());
        assertEquals(0, rin.remaining());
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

    @Test
    void decodeInventoryTransaction() {
        BedrockPackets.InventoryTransactionUse attack =
                BedrockPackets.decodeInventoryTransaction(
                        inventoryTransactionPayload(101, 1));
        assertEquals(101, attack.entityRuntimeId());
        assertEquals(1, attack.actionType());

        BedrockPackets.InventoryTransactionUse interact =
                BedrockPackets.decodeInventoryTransaction(
                        inventoryTransactionPayload(102, 0));
        assertEquals(102, interact.entityRuntimeId());
        assertEquals(0, interact.actionType());

        // transaction type が違うものは null。
        ByteWriter out = new ByteWriter();
        BedrockVarInt.writeZigzag32(out, 0);
        BedrockVarInt.writeVarInt(out, 0); // normal transaction
        BedrockVarInt.writeUnsignedVarInt(out, 0);
        assertNull(BedrockPackets.decodeInventoryTransaction(out.bytes()));
    }

    @Test
    void decodePlayerActionAndInteract() {
        ByteWriter action = new ByteWriter();
        BedrockVarInt.writeVarLong(action, 7); // runtime id（自分）
        BedrockVarInt.writeZigzag32(action, 11); // start sneak
        BedrockVarInt.writeZigzag32(action, 0); // position x
        BedrockVarInt.writeUnsignedVarInt(action, 64); // position y
        BedrockVarInt.writeZigzag32(action, 0); // position z
        BedrockVarInt.writeZigzag32(action, 0); // result position x
        BedrockVarInt.writeUnsignedVarInt(action, 64); // result position y
        BedrockVarInt.writeZigzag32(action, 0); // result position z
        BedrockVarInt.writeZigzag32(action, 0); // face
        BedrockPackets.PlayerAction decodedAction =
                BedrockPackets.decodePlayerAction(action.bytes());
        assertEquals(7, decodedAction.runtimeId());
        assertEquals(11, decodedAction.action());

        ByteWriter interact = new ByteWriter();
        interact.writeByte(4); // mouse over entity
        BedrockVarInt.writeVarLong(interact, 101);
        interact.writeLittleFloat(10.0f);
        interact.writeLittleFloat(65.0f);
        interact.writeLittleFloat(10.0f);
        BedrockPackets.Interact decodedInteract =
                BedrockPackets.decodeInteract(interact.bytes());
        assertEquals(4, decodedInteract.actionId());
        assertEquals(101, decodedInteract.targetEntityId());
    }
}
