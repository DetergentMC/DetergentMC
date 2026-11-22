package io.detergentmc.protocol;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import io.detergentmc.protocol.PacketIds.Direction;
import io.detergentmc.protocol.PacketIds.PacketName;
import io.detergentmc.protocol.PacketIds.State;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * 401（1.13.1）⇔404（1.13.2）のスロット形式付け替え。
 * 401 は旧形式（itemId: i16、-1 = 空）、404 は現行形式
 * （present: bool＋itemId: varint）。アイテムIDの数値は同一。
 */
class SlotTranslator401Test {

    private static final int V401 = ProtocolVersion.V1_13_1;

    private static int id(PacketName name) {
        return PacketIds.id(name, V401).orElseThrow();
    }

    /** 404形式のスロット（NBT付き）を作る。 */
    private static byte[] newSlot(int item, int count, boolean withNbt) {
        PacketIO.Writer writer = new PacketIO.Writer();
        writer.writeBoolean(true);
        writer.writeVarInt(item);
        writer.writeByte(count);
        if (withNbt) {
            writer.writeNamedNbt("", new Nbt.Tag.CompoundTag(
                    Map.of("Damage", new Nbt.Tag.IntTag(3))));
        } else {
            writer.writeByte(0);
        }
        return writer.bytes();
    }

    /** 401形式のスロット（NBT付き）を作る。 */
    private static byte[] oldSlot(int item, int count, boolean withNbt) {
        PacketIO.Writer writer = new PacketIO.Writer();
        writer.writeShort(item);
        writer.writeByte(count);
        if (withNbt) {
            writer.writeNamedNbt("", new Nbt.Tag.CompoundTag(
                    Map.of("Damage", new Nbt.Tag.IntTag(3))));
        } else {
            writer.writeByte(0);
        }
        return writer.bytes();
    }

    private static byte[] emptyNew() {
        return new byte[]{0};
    }

    private static byte[] emptyOld() {
        return new byte[]{(byte) 0xFF, (byte) 0xFF};
    }

    @Test
    void emptySlotBothWays() {
        assertArrayEquals(emptyOld(), SlotTranslator.slotTo401(emptyNew()));
        assertArrayEquals(emptyNew(), SlotTranslator.slotFrom401(emptyOld()));
    }

    @Test
    void filledSlotWithoutNbt() {
        assertArrayEquals(oldSlot(276, 1, false),
                SlotTranslator.slotTo401(newSlot(276, 1, false)));
        assertArrayEquals(newSlot(276, 1, false),
                SlotTranslator.slotFrom401(oldSlot(276, 1, false)));
    }

    @Test
    void filledSlotWithNbtIsCopiedVerbatim() {
        assertArrayEquals(oldSlot(513, 64, true),
                SlotTranslator.slotTo401(newSlot(513, 64, true)));
        assertArrayEquals(newSlot(513, 64, true),
                SlotTranslator.slotFrom401(oldSlot(513, 64, true)));
    }

    @Test
    void maxItemId789FitsInI16() {
        // 404 の最大アイテムID（789）は i16 に収まる。
        assertArrayEquals(oldSlot(789, 1, false),
                SlotTranslator.slotTo401(newSlot(789, 1, false)));
    }

    @Test
    void outOfRangeItemFallsBackToStone() {
        // 404 のレジストリには存在しない値だが、i16 に収まらない値は
        // 落とさず石（1）に置き換える（変換表の代替方針と同じ）。
        PacketIO.Reader reader = new PacketIO.Reader(
                SlotTranslator.slotTo401(newSlot(40000, 2, false)));
        assertEquals(1, reader.readShort());
        assertEquals(2, reader.readUnsignedByte());
    }

    @Test
    void windowItemsRoundTrip() {
        PacketIO.Writer writer = new PacketIO.Writer();
        writer.writeUnsignedByte(0); // windowId
        writer.writeShort(3); // count
        writer.writeBytes(newSlot(276, 1, true));
        writer.writeBytes(emptyNew());
        writer.writeBytes(newSlot(1, 64, false));
        byte[] packet404 = writer.bytes();

        MinecraftPacket to401 = SlotTranslator.clientboundTo401(
                new MinecraftPacket(id(PacketName.PLAY_WINDOW_ITEMS), packet404));
        PacketIO.Reader reader = new PacketIO.Reader(to401.payload());
        assertEquals(0, reader.readUnsignedByte());
        assertEquals(3, reader.readShort());
        // 1件目：旧形式で読めること（i16＋count＋NBT）。
        assertEquals(276, reader.readShort());
        assertEquals(1, reader.readUnsignedByte());
        reader.readNamedNbt();
        // 2件目：空は -1 のみ。
        assertEquals(-1, reader.readShort());
        // 3件目。
        assertEquals(1, reader.readShort());
        assertEquals(64, reader.readUnsignedByte());
        reader.readNamedNbt();
        assertEquals(0, reader.remaining());

        MinecraftPacket back = SlotTranslator.clientboundFrom401(to401);
        assertArrayEquals(packet404, back.payload());
    }

    @Test
    void setSlotRoundTrip() {
        PacketIO.Writer writer = new PacketIO.Writer();
        writer.writeByte(-1); // windowId（プレイヤーインベントリ）
        writer.writeShort(36); // slot
        writer.writeBytes(newSlot(297, 5, false));
        byte[] packet404 = writer.bytes();
        MinecraftPacket to401 = SlotTranslator.clientboundTo401(
                new MinecraftPacket(id(PacketName.PLAY_SET_SLOT), packet404));
        PacketIO.Reader reader = new PacketIO.Reader(to401.payload());
        assertEquals(-1, reader.readByte());
        assertEquals(36, reader.readShort());
        assertEquals(297, reader.readShort());
        assertEquals(5, reader.readUnsignedByte());
        assertArrayEquals(packet404,
                SlotTranslator.clientboundFrom401(to401).payload());
    }

    @Test
    void entityEquipmentRoundTrip() {
        PacketIO.Writer writer = new PacketIO.Writer();
        writer.writeVarInt(123); // entityId
        writer.writeVarInt(4); // slot（ブーツ）
        writer.writeBytes(newSlot(309, 1, true));
        byte[] packet404 = writer.bytes();
        MinecraftPacket to401 = SlotTranslator.clientboundTo401(
                new MinecraftPacket(id(PacketName.PLAY_ENTITY_EQUIPMENT), packet404));
        PacketIO.Reader reader = new PacketIO.Reader(to401.payload());
        assertEquals(123, reader.readVarInt());
        assertEquals(4, reader.readVarInt());
        assertEquals(309, reader.readShort());
        assertArrayEquals(packet404,
                SlotTranslator.clientboundFrom401(to401).payload());
    }

    @Test
    void entityMetadataWithSlotAndItemParticleRoundTrip() {
        PacketIO.Writer writer = new PacketIO.Writer();
        writer.writeVarInt(77); // entityId
        writer.writeUnsignedByte(7); // index
        writer.writeVarInt(6); // type = slot
        writer.writeBytes(newSlot(276, 1, false));
        writer.writeUnsignedByte(8); // index
        writer.writeVarInt(15); // type = particle
        writer.writeVarInt(27); // 404 の item 粒子
        writer.writeBytes(newSlot(152, 1, false)); // 粒子のスロット
        writer.writeUnsignedByte(0xFF);
        byte[] packet404 = writer.bytes();
        MinecraftPacket to401 = SlotTranslator.clientboundTo401(
                new MinecraftPacket(id(PacketName.PLAY_ENTITY_METADATA), packet404));
        PacketIO.Reader reader = new PacketIO.Reader(to401.payload());
        assertEquals(77, reader.readVarInt());
        assertEquals(7, reader.readUnsignedByte());
        assertEquals(6, reader.readVarInt());
        assertEquals(276, reader.readShort()); // 旧形式の itemId
        assertEquals(1, reader.readUnsignedByte());
        reader.readNamedNbt();
        assertEquals(8, reader.readUnsignedByte());
        assertEquals(15, reader.readVarInt());
        assertEquals(27, reader.readVarInt());
        assertEquals(152, reader.readShort());
        assertArrayEquals(packet404,
                SlotTranslator.clientboundFrom401(to401).payload());
    }

    @Test
    void worldParticlesWithItemRoundTrip() {
        PacketIO.Writer writer = new PacketIO.Writer();
        writer.writeVarInt(27); // 404 の item 粒子
        writer.writeBoolean(true); // longDistance
        writer.writeFloat(1.5f);
        writer.writeFloat(64.0f);
        writer.writeFloat(-2.5f);
        writer.writeFloat(0.1f);
        writer.writeFloat(0.2f);
        writer.writeFloat(0.3f);
        writer.writeFloat(0.0f); // speed
        writer.writeInt(10); // count
        writer.writeBytes(newSlot(46, 1, false));
        byte[] packet404 = writer.bytes();
        MinecraftPacket to401 = SlotTranslator.clientboundTo401(
                new MinecraftPacket(id(PacketName.PLAY_WORLD_PARTICLES), packet404));
        PacketIO.Reader reader = new PacketIO.Reader(to401.payload());
        assertEquals(27, reader.readVarInt());
        assertEquals(true, reader.readBoolean());
        reader.readFloat();
        reader.readFloat();
        reader.readFloat();
        reader.readFloat();
        reader.readFloat();
        reader.readFloat();
        reader.readFloat();
        assertEquals(10, reader.readInt());
        assertEquals(46, reader.readShort());
        assertArrayEquals(packet404,
                SlotTranslator.clientboundFrom401(to401).payload());
    }

    @Test
    void advancementsIconRoundTrip() {
        PacketIO.Writer writer = new PacketIO.Writer();
        writer.writeBoolean(true); // reset
        writer.writeVarInt(1); // size
        writer.writeString("minecraft:story/root");
        writer.writeBoolean(false); // parent 無し
        writer.writeBoolean(true); // display 有り
        writer.writeString("{\"text\":\"Stone Age\"}"); // title
        writer.writeString("{\"text\":\"desc\"}"); // description
        writer.writeBytes(newSlot(1, 1, false)); // icon
        writer.writeVarInt(0); // frameType
        writer.writeInt(2); // flags（背景なし・toast なし等）
        writer.writeFloat(1.0f);
        writer.writeFloat(2.0f);
        writer.writeVarInt(0); // criteria
        writer.writeVarInt(0); // requirements
        writer.writeVarInt(0); // removed
        writer.writeVarInt(0); // progress
        byte[] packet404 = writer.bytes();
        MinecraftPacket to401 = SlotTranslator.clientboundTo401(
                new MinecraftPacket(id(PacketName.PLAY_ADVANCEMENTS), packet404));
        PacketIO.Reader reader = new PacketIO.Reader(to401.payload());
        assertEquals(true, reader.readBoolean());
        assertEquals(1, reader.readVarInt());
        assertEquals("minecraft:story/root", reader.readString());
        assertEquals(false, reader.readBoolean());
        assertEquals(true, reader.readBoolean());
        reader.readString();
        reader.readString();
        assertEquals(1, reader.readShort()); // icon が旧形式
        assertEquals(1, reader.readUnsignedByte());
        assertArrayEquals(packet404,
                SlotTranslator.clientboundFrom401(to401).payload());
    }

    @Test
    void spawnLivingTrailingMetadataRoundTrip() {
        PacketIO.Writer writer = new PacketIO.Writer();
        writer.writeVarInt(55); // entityId
        writer.writeUuid(UUID.fromString("123e4567-e89b-12d3-a456-426614174000"));
        writer.writeVarInt(87); // type（zombie）
        writer.writeDouble(1.0);
        writer.writeDouble(2.0);
        writer.writeDouble(3.0);
        writer.writeByte(0); // yaw
        writer.writeByte(0); // pitch
        writer.writeByte(0); // headPitch
        writer.writeShort(0); // velocity
        writer.writeShort(0);
        writer.writeShort(0);
        writer.writeUnsignedByte(6); // index（手持ち）
        writer.writeVarInt(6); // type = slot
        writer.writeBytes(newSlot(276, 1, false));
        writer.writeUnsignedByte(0xFF);
        byte[] packet404 = writer.bytes();
        MinecraftPacket to401 = SlotTranslator.clientboundTo401(
                new MinecraftPacket(id(PacketName.PLAY_SPAWN_ENTITY_LIVING), packet404));
        // 先頭からメタデータ直前までは不変、スロットだけ旧形式になる。
        PacketIO.Reader reader = new PacketIO.Reader(to401.payload());
        assertEquals(55, reader.readVarInt());
        reader.readBytes(16);
        assertEquals(87, reader.readVarInt());
        reader.readDouble();
        reader.readDouble();
        reader.readDouble();
        reader.readByte();
        reader.readByte();
        reader.readByte();
        reader.readShort();
        reader.readShort();
        reader.readShort();
        assertEquals(6, reader.readUnsignedByte());
        assertEquals(6, reader.readVarInt());
        assertEquals(276, reader.readShort());
        assertArrayEquals(packet404,
                SlotTranslator.clientboundFrom401(to401).payload());
    }

    @Test
    void windowClickFrom401() {
        PacketIO.Writer writer = new PacketIO.Writer();
        writer.writeUnsignedByte(0); // windowId
        writer.writeShort(9); // slot
        writer.writeByte(0); // button
        writer.writeShort(123); // actionNumber
        writer.writeByte(0); // mode
        writer.writeBytes(oldSlot(276, 3, false)); // clickedItem（旧形式）
        byte[] packet401 = writer.bytes();
        MinecraftPacket to404 = SlotTranslator.serverboundFrom401(
                new MinecraftPacket(id(PacketName.PLAY_WINDOW_CLICK), packet401));
        PacketIO.Reader reader = new PacketIO.Reader(to404.payload());
        assertEquals(0, reader.readUnsignedByte());
        assertEquals(9, reader.readShort());
        assertEquals(0, reader.readByte());
        assertEquals(123, reader.readShort());
        assertEquals(0, reader.readByte());
        assertEquals(true, reader.readBoolean()); // present
        assertEquals(276, reader.readVarInt());
        assertEquals(3, reader.readUnsignedByte());
        reader.readNamedNbt(); // NBT 無し
        assertEquals(0, reader.remaining());
    }

    @Test
    void setCreativeSlotFrom401() {
        PacketIO.Writer writer = new PacketIO.Writer();
        writer.writeShort(13); // slot
        writer.writeBytes(oldSlot(513, 1, true));
        byte[] packet401 = writer.bytes();
        MinecraftPacket to404 = SlotTranslator.serverboundFrom401(
                new MinecraftPacket(id(PacketName.PLAY_SET_CREATIVE_SLOT), packet401));
        PacketIO.Reader reader = new PacketIO.Reader(to404.payload());
        assertEquals(13, reader.readShort());
        assertEquals(true, reader.readBoolean());
        assertEquals(513, reader.readVarInt());
        assertEquals(1, reader.readUnsignedByte());
        reader.readNamedNbt();
        assertEquals(0, reader.remaining());
    }

    @Test
    void nonSlotPacketsPassThrough() {
        MinecraftPacket keepAlive = new MinecraftPacket(
                id(PacketName.PLAY_KEEP_ALIVE), new byte[]{1, 2, 3, 4, 5, 6, 7, 8});
        assertSame(keepAlive, SlotTranslator.clientboundTo401(keepAlive));
        assertSame(keepAlive, SlotTranslator.clientboundFrom401(keepAlive));
        MinecraftPacket chat = new MinecraftPacket(
                PacketIds.id(PacketName.PLAY_CHAT_TO_SERVER, State.PLAY,
                        Direction.SERVERBOUND, V401).orElseThrow(),
                new byte[]{2, 'h', 'i'});
        assertSame(chat, SlotTranslator.serverboundFrom401(chat));
    }
}
