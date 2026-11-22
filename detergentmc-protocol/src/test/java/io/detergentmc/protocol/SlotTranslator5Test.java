package io.detergentmc.protocol;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import io.detergentmc.protocol.PacketIO.Reader;
import io.detergentmc.protocol.PacketIO.Writer;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.zip.GZIPInputStream;
import org.junit.jupiter.api.Test;

/**
 * 5（1.7.6〜1.7.10）のスロット・メタデータ変換層。47 との差分は
 * スロットの NBT が gzip 付き compressedNbt であること、
 * entityId が i32 化すること、ブロック座標がバラ数値3つ
 * （y の幅は u8／i16／i32）になること。メタデータのヘッダ
 * （bitfield・終端 0x7f）は 47 と完全同一。
 */
class SlotTranslator5Test {

    /**
     * ハイブリッドのスロット（ダイヤ剣、個数1、Damage=25 の NBT 付き）。
     * ハイブリッドのアイテム番号は 47 の番号（ダイヤ剣=276）。
     */
    private static byte[] hybridSlotWithDamage() {
        Writer writer = new Writer();
        writer.writeBoolean(true);
        writer.writeVarInt(276); // 47 のダイヤ剣
        writer.writeUnsignedByte(1);
        // NBT：ルート compound に short の Damage=25 だけ
        writer.writeByte(10);
        writer.writeShort(0);
        writer.writeByte(2);
        writer.writeShort(6);
        writer.writeBytes(new byte[] {'D', 'a', 'm', 'a', 'g', 'e'});
        writer.writeShort(25);
        writer.writeByte(0);
        return writer.bytes();
    }

    @Test
    void slotTo5ExtractsDamageAndDropsEmptyNbt() {
        byte[] wire = SlotTranslator.slotTo5(hybridSlotWithDamage());
        Reader reader = new Reader(wire);
        assertEquals(276, reader.readShort()); // 5 のダイヤ剣（i16）
        assertEquals(1, reader.readByte()); // 個数（i8）
        assertEquals(25, reader.readShort()); // damage（i16）へ分離
        // NBT は Damage だけだったため、残りは無し（長さ -1）。
        assertEquals(-1, reader.readShort());
        assertEquals(0, reader.remaining());
        // 逆変換では Damage が NBT（int タグ）へ戻る。
        Reader back = new Reader(SlotTranslator.slotFrom5(wire));
        assertEquals(true, back.readBoolean());
        assertEquals(276, back.readVarInt());
        assertEquals(1, back.readUnsignedByte());
        Nbt.Tag tag = back.readNamedNbt().tag();
        assertEquals(new Nbt.Tag.IntTag(25),
                ((Nbt.Tag.CompoundTag) tag).value().get("Damage"));
        assertEquals(0, back.remaining());
    }

    @Test
    void slotTo5GzipsRemainingNbt() throws Exception {
        // Damage 以外のタグが残る場合は gzip されて載る。
        Writer hybrid = new Writer();
        hybrid.writeBoolean(true);
        hybrid.writeVarInt(276);
        hybrid.writeUnsignedByte(1);
        hybrid.writeByte(10);
        hybrid.writeShort(0);
        hybrid.writeByte(2);
        hybrid.writeShort(6);
        hybrid.writeBytes(new byte[] {'D', 'a', 'm', 'a', 'g', 'e'});
        hybrid.writeShort(7);
        hybrid.writeByte(3); // int タグ "Count" = 3（残る側）
        hybrid.writeShort(5);
        hybrid.writeBytes(new byte[] {'C', 'o', 'u', 'n', 't'});
        hybrid.writeInt(3);
        hybrid.writeByte(0);
        byte[] wire = SlotTranslator.slotTo5(hybrid.bytes());
        Reader reader = new Reader(wire);
        assertEquals(276, reader.readShort());
        assertEquals(1, reader.readByte());
        assertEquals(7, reader.readShort());
        int nbtLength = reader.readShort();
        byte[] gzipped = reader.readBytes(nbtLength);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (GZIPInputStream gzip = new GZIPInputStream(
                new ByteArrayInputStream(gzipped))) {
            gzip.transferTo(out);
        }
        byte[] raw = out.toByteArray();
        // Damage は除かれ、int タグだけが残る。
        assertEquals(10, raw[0]);
        assertEquals(3, raw[3]);
        assertEquals(0, reader.remaining());
        // 逆変換では Damage（int タグ）が末尾に戻る。
        Reader back = new Reader(SlotTranslator.slotFrom5(wire));
        assertEquals(true, back.readBoolean());
        assertEquals(276, back.readVarInt());
        back.readUnsignedByte();
        Nbt.Tag tag = back.readNamedNbt().tag();
        java.util.Map<String, Nbt.Tag> entries =
                ((Nbt.Tag.CompoundTag) tag).value();
        assertEquals(new Nbt.Tag.IntTag(3), entries.get("Count"));
        assertEquals(new Nbt.Tag.IntTag(7), entries.get("Damage"));
    }

    @Test
    void emptySlotIsMinusOneOnly() {
        byte[] wire = SlotTranslator.slotTo5(new byte[] {0});
        assertEquals(2, wire.length);
        assertEquals(-1, new Reader(wire).readShort());
        assertArrayEquals(new byte[] {0}, SlotTranslator.slotFrom5(wire));
    }

    @Test
    void metadataBitfieldRoundTrip() {
        // ハイブリッド：index0=byte 型 0x3F、index2=int 型 0x01020304
        Writer hybrid = new Writer();
        hybrid.writeByte(0);
        hybrid.writeVarInt(0); // 型0（byte）
        hybrid.writeByte(0x3F);
        hybrid.writeByte(2);
        hybrid.writeVarInt(2); // 型2（int）
        hybrid.writeInt(0x01020304);
        hybrid.writeByte(0xFF); // 終端
        byte[] wire = SlotTranslator.metadataTo5(hybrid.bytes());
        Reader reader = new Reader(wire);
        assertEquals(0x00, reader.readUnsignedByte()); // (0<<5)|0
        assertEquals(0x3F, reader.readByte());
        assertEquals(0x42, reader.readUnsignedByte()); // (2<<5)|2
        assertEquals(0x01020304, reader.readInt());
        assertEquals(0x7F, reader.readUnsignedByte()); // 終端は 0x7f
        assertEquals(0, reader.remaining());
        assertArrayEquals(hybrid.bytes(),
                SlotTranslator.metadataFrom5(wire));
    }

    @Test
    void entityEquipmentUsesInt32EntityId() {
        int id5 = PacketIds.id(PacketIds.PacketName.PLAY_ENTITY_EQUIPMENT,
                PacketIds.State.PLAY, PacketIds.Direction.CLIENTBOUND,
                ProtocolVersion.V1_7_10).orElseThrow();
        Writer hybrid = new Writer();
        hybrid.writeVarInt(300);
        hybrid.writeVarInt(0); // slot（頭）
        hybrid.writeBytes(hybridSlotWithDamage());
        MinecraftPacket wire = SlotTranslator.clientboundTo5(
                new MinecraftPacket(id5, hybrid.bytes()));
        Reader reader = new Reader(wire.payload());
        assertEquals(300, reader.readInt()); // entityId は i32
        assertEquals(0, reader.readShort()); // slot は i16
        assertEquals(276, reader.readShort());
        // 逆変換でハイブリッドに戻る（Damage は int タグで戻る）。
        MinecraftPacket back = SlotTranslator.clientboundFrom5(wire);
        Reader br = new Reader(back.payload());
        assertEquals(300, br.readVarInt());
        assertEquals(0, br.readVarInt());
        assertEquals(true, br.readBoolean());
        assertEquals(276, br.readVarInt());
        br.readUnsignedByte();
        Nbt.Tag tag = br.readNamedNbt().tag();
        assertEquals(new Nbt.Tag.IntTag(25),
                ((Nbt.Tag.CompoundTag) tag).value().get("Damage"));
    }

    @Test
    void blockChangeSplitsPackedPosition() {
        int id5 = PacketIds.id(PacketIds.PacketName.PLAY_BLOCK_CHANGE,
                PacketIds.State.PLAY, PacketIds.Direction.CLIENTBOUND,
                ProtocolVersion.V1_7_10).orElseThrow();
        // ハイブリッド：詰め込み座標 (5, 64, -3)＋状態（47 の石=16）
        long packed = ((5L & 0x3FFFFFFL) << 38) | ((64L & 0xFFFL) << 26)
                | (-3L & 0x3FFFFFFL);
        Writer hybrid = new Writer();
        hybrid.writeLong(packed);
        hybrid.writeVarInt(16);
        MinecraftPacket wire = SlotTranslator.clientboundTo5(
                new MinecraftPacket(id5, hybrid.bytes()));
        Reader reader = new Reader(wire.payload());
        assertEquals(5, reader.readInt()); // x（i32）
        assertEquals(64, reader.readUnsignedByte()); // y（u8）
        assertEquals(-3, reader.readInt()); // z（i32）
        assertEquals(1, reader.readVarInt()); // ブロック ID（石）
        assertEquals(0, reader.readUnsignedByte()); // メタ
        assertEquals(0, reader.remaining());
    }

    @Test
    void spawnLivingRemaps18Types() {
        int id5 = PacketIds.id(PacketIds.PacketName.PLAY_SPAWN_ENTITY_LIVING,
                PacketIds.State.PLAY, PacketIds.Direction.CLIENTBOUND,
                ProtocolVersion.V1_7_10).orElseThrow();
        // ハイブリッド：種別 67（エンダーマイト、47 の番号、1バイト）
        // ※ ハイブリッドの Spawn Entity Living に UUID は無い（47 と同じ約束）。
        Writer hybrid = new Writer();
        hybrid.writeVarInt(11);
        hybrid.writeByte(67);
        hybrid.writeDouble(1.5);
        hybrid.writeDouble(64.0);
        hybrid.writeDouble(-2.5);
        hybrid.writeByte(0); // yaw
        hybrid.writeByte(0); // pitch
        hybrid.writeByte(0); // headPitch
        hybrid.writeShort(0);
        hybrid.writeShort(0);
        hybrid.writeShort(0);
        hybrid.writeByte(0xFF); // メタデータ（空）
        MinecraftPacket wire = SlotTranslator.clientboundTo5(
                new MinecraftPacket(id5, hybrid.bytes()));
        Reader reader = new Reader(wire.payload());
        assertEquals(11, reader.readVarInt());
        assertEquals(90, reader.readUnsignedByte()); // 種別はブタ（u8）
        assertEquals(48, reader.readInt()); // x は i32 固定小数点（1.5×32）
        assertEquals(2048, reader.readInt());
        assertEquals(-80, reader.readInt());
    }
}
