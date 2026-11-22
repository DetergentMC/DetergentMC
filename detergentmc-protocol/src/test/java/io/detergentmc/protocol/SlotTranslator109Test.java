package io.detergentmc.protocol;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.detergentmc.protocol.Nbt.Tag;
import io.detergentmc.protocol.PacketIds.PacketName;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * 109（1.9.2）の実ワイヤ差分の付け替え（SlotTranslator の 109 層）。
 * スロットの itemDamage・メタデータの型フィールド（i8）・World
 * Particles の粒子 ID（i32）は 340 と同一。109 固有の差分は
 * メタデータの型が 0〜12 のみで、ハイブリッド側の型 13〜16
 * （blockid・optblockid・nbt・particle）が実ワイヤ向けに落ちること。
 */
class SlotTranslator109Test {

    private static int id(PacketName name) {
        return PacketIds.id(name, ProtocolVersion.V1_9_2).getAsInt();
    }

    /** ハイブリッド形式のスロット（Damage タグ付きも作れる）。 */
    private static byte[] hybridSlot(int item, int count, Integer damage) {
        PacketIO.Writer writer = new PacketIO.Writer();
        writer.writeBoolean(true);
        writer.writeVarInt(item);
        writer.writeByte(count);
        if (damage == null) {
            writer.writeByte(0);
        } else {
            Map<String, Tag> data = new LinkedHashMap<>();
            data.put("Damage", new Tag.IntTag(damage));
            writer.writeNamedNbt("", new Tag.CompoundTag(data));
        }
        return writer.bytes();
    }

    private static byte[] emptyHybrid() {
        return new byte[]{0};
    }

    @Test
    void emptySlotBothWays() {
        PacketIO.Reader reader = new PacketIO.Reader(SlotTranslator.slotTo109(emptyHybrid()));
        assertEquals(-1, reader.readShort());
        assertEquals(0, reader.remaining());
        assertArrayEquals(emptyHybrid(),
                SlotTranslator.slotFrom109(new byte[]{(byte) 0xFF, (byte) 0xFF}));
    }

    @Test
    void damageTagBecomesItemDamageField() {
        PacketIO.Reader reader = new PacketIO.Reader(
                SlotTranslator.slotTo109(hybridSlot(351, 1, 14)));
        assertEquals(351, reader.readShort());
        assertEquals(1, reader.readUnsignedByte());
        assertEquals(14, reader.readShort());
        assertEquals(0, reader.readByte());
        assertEquals(0, reader.remaining());
        assertArrayEquals(hybridSlot(351, 1, 14),
                SlotTranslator.slotFrom109(SlotTranslator.slotTo109(hybridSlot(351, 1, 14))));
    }

    @Test
    void filledSlotWithoutDamageKeepsOtherNbt() {
        PacketIO.Writer hybrid = new PacketIO.Writer();
        hybrid.writeBoolean(true);
        hybrid.writeVarInt(276);
        hybrid.writeByte(1);
        Map<String, Tag> data = new LinkedHashMap<>();
        data.put("Damage", new Tag.IntTag(30));
        data.put("display", new Tag.CompoundTag(Map.of(
                "Name", new Tag.StringTag("剣"))));
        hybrid.writeNamedNbt("", new Tag.CompoundTag(data));
        PacketIO.Reader reader = new PacketIO.Reader(
                SlotTranslator.slotTo109(hybrid.bytes()));
        assertEquals(276, reader.readShort());
        assertEquals(1, reader.readUnsignedByte());
        assertEquals(30, reader.readShort());
        Tag rest = reader.readNamedNbt().tag();
        assertTrue(rest instanceof Tag.CompoundTag);
        assertTrue(((Tag.CompoundTag) rest).get("display").isPresent());
        assertTrue(((Tag.CompoundTag) rest).get("Damage").isEmpty());
    }

    @Test
    void outOfRangeItemFallsBackToStone() {
        PacketIO.Reader reader = new PacketIO.Reader(
                SlotTranslator.slotTo109(hybridSlot(40000, 2, null)));
        assertEquals(1, reader.readShort());
        assertEquals(2, reader.readUnsignedByte());
    }

    @Test
    void windowItemsRoundTrip() {
        PacketIO.Writer writer = new PacketIO.Writer();
        writer.writeUnsignedByte(0); // windowId
        writer.writeShort(3); // count
        writer.writeBytes(hybridSlot(276, 1, null));
        writer.writeBytes(emptyHybrid());
        writer.writeBytes(hybridSlot(351, 1, 3));
        byte[] hybrid = writer.bytes();
        MinecraftPacket to109 = SlotTranslator.clientboundTo109(
                new MinecraftPacket(id(PacketName.PLAY_WINDOW_ITEMS), hybrid));
        PacketIO.Reader reader = new PacketIO.Reader(to109.payload());
        assertEquals(0, reader.readUnsignedByte());
        assertEquals(3, reader.readShort());
        assertEquals(276, reader.readShort());
        assertEquals(1, reader.readUnsignedByte());
        assertEquals(0, reader.readShort());
        reader.readNamedNbt();
        assertEquals(-1, reader.readShort());
        assertEquals(351, reader.readShort());
        assertEquals(1, reader.readUnsignedByte());
        assertEquals(3, reader.readShort());
        assertArrayEquals(hybrid, SlotTranslator.clientboundFrom109(to109).payload());
    }

    @Test
    void entityMetadataTypeBecomesI8AndTypes13To16AreDropped() {
        // ハイブリッド：index 7 = slot（型6）、index 8 = particle（型16、
        // 109 に無い）、index 9 = nbt（型15、109 に無い）。
        // 実ワイヤでは型 16・15 のエントリが落ち、slot だけが残る。
        PacketIO.Writer writer = new PacketIO.Writer();
        writer.writeVarInt(77); // entityId
        writer.writeUnsignedByte(7); // index
        writer.writeVarInt(6); // type = slot
        writer.writeBytes(hybridSlot(276, 1, null));
        writer.writeUnsignedByte(8); // index
        writer.writeVarInt(16); // type = particle（109 に無い）
        writer.writeVarInt(36); // iconcrack
        writer.writeBytes(hybridSlot(351, 1, 3));
        writer.writeUnsignedByte(9); // index
        writer.writeVarInt(15); // type = nbt（109 に無い）
        writer.writeByte(0); // 空 NBT
        writer.writeUnsignedByte(0xFF);
        byte[] hybrid = writer.bytes();
        MinecraftPacket to109 = SlotTranslator.clientboundTo109(
                new MinecraftPacket(id(PacketName.PLAY_ENTITY_METADATA), hybrid));
        PacketIO.Reader reader = new PacketIO.Reader(to109.payload());
        assertEquals(77, reader.readVarInt());
        assertEquals(7, reader.readUnsignedByte());
        assertEquals(6, reader.readByte()); // 型は i8
        assertEquals(276, reader.readShort());
        assertEquals(1, reader.readUnsignedByte());
        assertEquals(0, reader.readShort());
        reader.readNamedNbt();
        assertEquals(0xFF, reader.readUnsignedByte()); // 型16・15は落ちて終端
        assertEquals(0, reader.remaining());
        // 逆向き：残ったエントリはそのままハイブリッドへ戻る（落ちた分は戻らない）。
        PacketIO.Writer expected = new PacketIO.Writer();
        expected.writeVarInt(77);
        expected.writeUnsignedByte(7);
        expected.writeVarInt(6);
        expected.writeBytes(hybridSlot(276, 1, null));
        expected.writeUnsignedByte(0xFF);
        assertArrayEquals(expected.bytes(),
                SlotTranslator.clientboundFrom109(to109).payload());
    }

    @Test
    void worldParticlesIdBecomesI32() {
        PacketIO.Writer writer = new PacketIO.Writer();
        writer.writeVarInt(36); // iconcrack
        writer.writeBoolean(true); // longDistance
        writer.writeFloat(1.5f);
        writer.writeFloat(64.0f);
        writer.writeFloat(-2.5f);
        writer.writeFloat(0.1f);
        writer.writeFloat(0.2f);
        writer.writeFloat(0.3f);
        writer.writeFloat(0.0f); // speed
        writer.writeInt(10); // count
        writer.writeBytes(hybridSlot(276, 1, 2));
        byte[] hybrid = writer.bytes();
        MinecraftPacket to109 = SlotTranslator.clientboundTo109(
                new MinecraftPacket(id(PacketName.PLAY_WORLD_PARTICLES), hybrid));
        PacketIO.Reader reader = new PacketIO.Reader(to109.payload());
        assertEquals(36, reader.readInt()); // 粒子 ID は i32
        assertEquals(true, reader.readBoolean());
        for (int i = 0; i < 7; i++) {
            reader.readFloat();
        }
        assertEquals(10, reader.readInt());
        assertEquals(276, reader.readVarInt());
        assertEquals(2, reader.readVarInt());
        assertEquals(0, reader.remaining());
        assertArrayEquals(hybrid, SlotTranslator.clientboundFrom109(to109).payload());
    }

    @Test
    void windowClickFrom109() {
        PacketIO.Writer writer = new PacketIO.Writer();
        writer.writeUnsignedByte(0); // windowId
        writer.writeShort(5); // slot
        writer.writeByte(0); // button
        writer.writeShort(1); // actionNumber
        writer.writeByte(0); // mode（109 は i8）
        writer.writeShort(351); // clickedItem（109 ワイヤ形式）
        writer.writeByte(1);
        writer.writeShort(14); // damage
        writer.writeByte(0); // NBT 無し
        MinecraftPacket from109 = SlotTranslator.serverboundFrom109(
                new MinecraftPacket(id(PacketName.PLAY_WINDOW_CLICK), writer.bytes()));
        PacketIO.Reader reader = new PacketIO.Reader(from109.payload());
        assertEquals(0, reader.readUnsignedByte());
        assertEquals(5, reader.readShort());
        assertEquals(0, reader.readByte());
        assertEquals(1, reader.readShort());
        assertEquals(0, reader.readByte());
        assertTrue(reader.readBoolean()); // present
        assertEquals(351, reader.readVarInt());
        assertEquals(1, reader.readUnsignedByte());
        Tag nbt = reader.readNamedNbt().tag();
        assertTrue(nbt instanceof Tag.CompoundTag);
        assertEquals(14, ((Tag.IntTag) ((Tag.CompoundTag) nbt).get("Damage")
                .orElseThrow()).value());
    }

    @Test
    void setCreativeSlotFrom109() {
        PacketIO.Writer writer = new PacketIO.Writer();
        writer.writeShort(13); // slot
        writer.writeShort(276); // ダイヤ剣（damage 0）
        writer.writeByte(1);
        writer.writeShort(0);
        writer.writeByte(0);
        MinecraftPacket from109 = SlotTranslator.serverboundFrom109(
                new MinecraftPacket(id(PacketName.PLAY_SET_CREATIVE_SLOT), writer.bytes()));
        PacketIO.Reader reader = new PacketIO.Reader(from109.payload());
        assertEquals(13, reader.readShort());
        assertTrue(reader.readBoolean());
        assertEquals(276, reader.readVarInt());
        assertEquals(1, reader.readUnsignedByte());
        assertEquals(0, reader.readByte());
        assertEquals(0, reader.remaining());
    }

    @Test
    void nonSlotPacketsPassThrough() {
        MinecraftPacket packet = new MinecraftPacket(
                id(PacketName.PLAY_KEEP_ALIVE), new byte[]{1, 2, 3});
        assertArrayEquals(packet.payload(),
                SlotTranslator.clientboundTo109(packet).payload());
        assertArrayEquals(packet.payload(),
                SlotTranslator.serverboundFrom109(packet).payload());
    }
}
