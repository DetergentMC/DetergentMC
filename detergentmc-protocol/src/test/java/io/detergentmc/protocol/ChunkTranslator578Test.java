package io.detergentmc.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.detergentmc.protocol.Nbt.Tag;
import io.detergentmc.protocol.PacketIO.Reader;
import io.detergentmc.protocol.PacketIO.Writer;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * {@link ChunkTranslator} の578（1.15.2）⇔758（1.18.2）向け変換テスト。
 *
 * <p>578 のチャンクは 758 と別世代：セクションはビットマスク方式（16節）、
 * バイオームは 1024 個の int 配列、ビット詰めはロング境界をまたぐ旧方式、
 * 光は別パケット（Update Light）。heightmap は 9bit×256＝36 ロングで、
 * 値は絶対 Y（758 は次元 minY 基準のため overworld では +64 ずれる）。
 * 形式は実バニラ 1.15.2／1.18.2 のキャプチャで確認済み。
 */
class ChunkTranslator578Test {

    /** 9bit×256 をまたぎ方式で詰めた heightmap（全値 value）。 */
    private static long[] heightmapCrossing(int value) {
        long[] longs = new long[36];
        for (int i = 0; i < 256; i++) {
            long bitPos = (long) i * 9;
            int li = (int) (bitPos >>> 6);
            int off = (int) (bitPos & 63);
            if (off + 9 <= 64) {
                longs[li] |= (long) value << off;
            } else {
                int rem = 64 - off;
                longs[li] |= (long) value << off;
                longs[li + 1] |= (long) value >>> rem;
            }
        }
        return longs;
    }

    /** 578 のチャンク1個（セクション0のみ、石と草ブロック、BE は chest）。 */
    private static byte[] chunk578() {
        Writer writer = new Writer();
        writer.writeInt(0);
        writer.writeInt(0);
        writer.writeBoolean(true); // groundUp
        writer.writeVarInt(1); // mask: section 0
        Map<String, Tag> heightmaps = new LinkedHashMap<>();
        heightmaps.put("MOTION_BLOCKING", new Tag.LongArrayTag(heightmapCrossing(4)));
        heightmaps.put("WORLD_SURFACE", new Tag.LongArrayTag(heightmapCrossing(4)));
        writer.writeNamedNbt("", new Tag.CompoundTag(heightmaps));
        for (int i = 0; i < 1024; i++) {
            writer.writeInt(1); // plains
        }
        Writer section = new Writer();
        section.writeShort(3); // blockCount
        section.writeUnsignedByte(4); // bits per block（パレット）
        section.writeVarInt(3);
        section.writeVarInt(0); // air
        section.writeVarInt(1); // stone
        section.writeVarInt(9); // grass_block
        long[] data = new long[256];
        data[0] = 1; // (0,0,0) = パレット1（stone）
        data[16] = 2; // (0,1,0) = パレット2（grass_block）
        section.writeVarInt(256);
        for (long l : data) {
            section.writeLong(l);
        }
        byte[] sectionBytes = section.bytes();
        writer.writeVarInt(sectionBytes.length);
        writer.writeBytes(sectionBytes);
        writer.writeVarInt(1); // ブロックエンティティ 1件
        Map<String, Tag> be = new LinkedHashMap<>();
        be.put("x", new Tag.IntTag(0));
        be.put("y", new Tag.IntTag(0));
        be.put("z", new Tag.IntTag(0));
        be.put("id", new Tag.StringTag("minecraft:chest"));
        writer.writeNamedNbt("", new Tag.CompoundTag(be));
        return writer.bytes();
    }

    /** 758 チャンクの heightmap 値を読む（MOTION_BLOCKING の先頭値）。 */
    private static long firstHeightmapValue758(byte[] chunk758) {
        Reader reader = new Reader(chunk758);
        reader.readInt();
        reader.readInt();
        Tag tag = reader.readNamedNbt().tag();
        Tag.CompoundTag root = (Tag.CompoundTag) tag;
        Tag.LongArrayTag array = (Tag.LongArrayTag) root.value().get("MOTION_BLOCKING");
        long[] longs = array.value();
        // 758 は非またぎ 9bit：先頭ロングの下位 9bit が先頭値。
        return longs[0] & 0x1FFL;
    }

    @Test
    void chunk578To758ShiftsSectionsAndHeightmaps() {
        byte[] out = ChunkTranslator.translateChunkData578To758(chunk578(), 64, null);
        // heightmap：578 の絶対値 4 → 758 は minY 基準で 4+64=68。
        assertEquals(68, firstHeightmapValue758(out));
        // 戻し変換で元の 578 チャンク相当になること（石・草・BE）。
        byte[] back = ChunkTranslator.translateChunkData758To578(out, 64);
        Reader reader = new Reader(back);
        assertEquals(0, reader.readInt());
        assertEquals(0, reader.readInt());
        assertTrue(reader.readBoolean());
        assertEquals(1, reader.readVarInt()); // mask はセクション0のみ
        Tag tag = reader.readNamedNbt().tag();
        Tag.CompoundTag root = (Tag.CompoundTag) tag;
        Tag.LongArrayTag array = (Tag.LongArrayTag) root.value().get("MOTION_BLOCKING");
        assertEquals(36, array.value().length);
        assertEquals(4, array.value()[0] & 0x1FFL);
        for (int i = 0; i < 1024; i++) {
            assertEquals(1, reader.readInt()); // バイオーム plains
        }
        int size = reader.readVarInt();
        Reader section = new Reader(reader.readBytes(size));
        assertEquals(2, section.readShort()); // blockCount（石＋草の2個）
        int bits = section.readUnsignedByte();
        assertEquals(4, bits);
        int paletteLength = section.readVarInt();
        int[] palette = new int[paletteLength];
        for (int i = 0; i < paletteLength; i++) {
            palette[i] = section.readVarInt();
        }
        assertEquals(1, palette[0]); // 走査順で最初に現れる stone
        assertTrue(java.util.Arrays.stream(palette).anyMatch(v -> v == 9)); // grass_block
        // ブロックエンティティは chest の NBT（id 付き）に戻る。
        assertEquals(1, reader.readVarInt());
        Tag beTag = reader.readNamedNbt().tag();
        Tag.CompoundTag be = (Tag.CompoundTag) beTag;
        assertEquals("minecraft:chest", be.getString("id").orElse(""));
    }

    @Test
    void slotItemsAreRemappedViaSetSlot() {
        // 578 の Set Slot：windowId i8, slot i16, item。
        Writer writer = new Writer();
        writer.writeByte(0);
        writer.writeShort(5);
        writer.writeBoolean(true);
        writer.writeVarInt(754); // zombie_pigman_spawn_egg（578）
        writer.writeByte(1);
        writer.writeByte(0); // NBT 無し
        byte[] out = ChunkTranslator.translateSetSlot578To758(writer.bytes());
        Reader reader = new Reader(out);
        assertEquals(0, reader.readByte());
        assertEquals(0, reader.readVarInt()); // stateId（758で追加）
        assertEquals(5, reader.readShort());
        assertTrue(reader.readBoolean());
        assertEquals(939, reader.readVarInt()); // zombified_piglin_spawn_egg（758）
    }

    @Test
    void windowItems578To758AddsStateIdAndCarriedItem() {
        Writer writer = new Writer();
        writer.writeByte(0); // windowId（578 は u8）
        writer.writeShort(1); // count（578 は i16）
        writer.writeBoolean(true);
        writer.writeVarInt(1); // stone
        writer.writeByte(3);
        writer.writeByte(0);
        byte[] out = ChunkTranslator.translateWindowItems578To758(writer.bytes());
        Reader reader = new Reader(out);
        assertEquals(0, reader.readByte());
        assertEquals(0, reader.readVarInt()); // stateId
        assertEquals(1, reader.readVarInt()); // count（758 は varint）
        assertTrue(reader.readBoolean());
        assertEquals(1, reader.readVarInt());
        assertEquals(3, reader.readByte());
        assertEquals(0, reader.readByte());
        assertEquals(0, reader.readByte()); // carried item は空スロット（bool false の 0）
    }

    @Test
    void spawnWeatherBecomesLightningBoltAndBack() {
        Writer writer = new Writer();
        writer.writeVarInt(7); // entityId
        writer.writeByte(1); // lightning
        writer.writeDouble(1.5);
        writer.writeDouble(64.0);
        writer.writeDouble(-2.5);
        byte[] entity = ChunkTranslator.spawnWeather578ToEntity758(writer.bytes());
        Reader reader = new Reader(entity);
        assertEquals(7, reader.readVarInt());
        reader.readBytes(16); // uuid（nil）
        assertEquals(45, reader.readVarInt()); // lightning_bolt（758）
        byte[] weather = ChunkTranslator.spawnEntity758ToWeather578(entity);
        assertNotNull(weather);
        Reader back = new Reader(weather);
        assertEquals(7, back.readVarInt());
        assertEquals(1, back.readByte());
        // 雷でない Spawn Entity は天気にならない。
        Writer other = new Writer();
        other.writeVarInt(8);
        other.writeUuid(UUID.fromString("11111111-2222-3333-4444-555555555555"));
        other.writeVarInt(46);
        other.writeDouble(0);
        other.writeDouble(0);
        other.writeDouble(0);
        other.writeByte(0);
        other.writeByte(0);
        other.writeInt(0);
        other.writeShort(0);
        other.writeShort(0);
        other.writeShort(0);
        assertNull(ChunkTranslator.spawnEntity758ToWeather578(other.bytes()));
    }

    @Test
    void metadataUsesOldTypeNumbering() {
        // 578／758 のメタデータ型番号は旧式：13=blockstate、6=slot。
        Writer writer = new Writer();
        writer.writeVarInt(3); // entityId
        writer.writeUnsignedByte(0); // index
        writer.writeUnsignedByte(13); // blockstate
        writer.writeVarInt(8687); // grass_path（578）
        writer.writeUnsignedByte(1);
        writer.writeUnsignedByte(6); // slot
        writer.writeBoolean(true);
        writer.writeVarInt(754); // zombie_pigman_spawn_egg
        writer.writeByte(1);
        writer.writeByte(0);
        writer.writeUnsignedByte(0xFF);
        byte[] out = ChunkTranslator.translateEntityMetadata578To758(writer.bytes());
        Reader reader = new Reader(out);
        assertEquals(3, reader.readVarInt());
        assertEquals(0, reader.readUnsignedByte());
        assertEquals(13, reader.readUnsignedByte());
        assertEquals(9473, reader.readVarInt()); // dirt_path（758）
        assertEquals(1, reader.readUnsignedByte());
        assertEquals(6, reader.readUnsignedByte());
        assertTrue(reader.readBoolean());
        assertEquals(939, reader.readVarInt());
    }

    @Test
    void multiBlockChange578To758AddsSuppressLight() {
        Writer writer = new Writer();
        writer.writeLong((1L << 42) | (2L << 20) | 3L); // section pos
        writer.writeVarInt(1); // record count
        writer.writeVarLong((9473L << 12) | 0x123L); // dirt_path は 758 の値ではないので stone で確認
        byte[] out = ChunkTranslator.translateMultiBlockChange578To758(writer.bytes());
        Reader reader = new Reader(out);
        reader.readLong();
        assertTrue(reader.readBoolean()); // suppressLight=true が付く
        assertEquals(1, reader.readVarInt()); // 配列長
        long record = reader.readVarLong();
        assertEquals(0x123L, record & 0xFFFL);
    }

    @Test
    void soundEffectIdsAreRemapped() {
        Writer writer = new Writer();
        writer.writeVarInt(651); // block.stone.break（578）
        writer.writeByte(0); // category
        writer.writeInt(8);
        writer.writeInt(64);
        writer.writeInt(8);
        writer.writeFloat(1.0f);
        writer.writeFloat(1.0f);
        byte[] out = ChunkTranslator.translateSoundEffect578To758(writer.bytes());
        Reader reader = new Reader(out);
        assertEquals(1018, reader.readVarInt()); // block.stone.break（758）
    }

    @Test
    void equipment758To578SplitsIntoSinglePackets() {
        Writer writer = new Writer();
        writer.writeVarInt(9); // entityId
        writer.writeByte(0x80); // slot 0 + 継続
        writer.writeBoolean(true);
        writer.writeVarInt(719); // diamond_sword（758）
        writer.writeByte(1);
        writer.writeByte(0);
        writer.writeByte(1); // slot 1（継続なし）
        writer.writeBoolean(true);
        writer.writeVarInt(1); // stone
        writer.writeByte(1);
        writer.writeByte(0);
        List<byte[]> out = ChunkTranslator.translateEntityEquipment758To578(writer.bytes());
        assertEquals(2, out.size());
        Reader first = new Reader(out.get(0));
        assertEquals(9, first.readVarInt());
        assertEquals(0, first.readByte());
        assertTrue(first.readBoolean());
        assertEquals(541, first.readVarInt()); // diamond_sword（578）
    }

    @Test
    void updateLightRoundTrip() {
        Writer writer = new Writer();
        writer.writeVarInt(0); // chunkX
        writer.writeVarInt(0); // chunkZ
        writer.writeVarInt(0b10); // skyMask: セクション0
        writer.writeVarInt(0); // blockMask
        writer.writeVarInt(0); // emptySkyMask
        writer.writeVarInt(0); // emptyBlockMask
        writer.writeVarInt(2048);
        writer.writeBytes(new byte[2048]);
        byte[] as758 = ChunkTranslator.translateUpdateLight578To758(writer.bytes());
        byte[] back = ChunkTranslator.translateUpdateLight758To578(as758);
        Reader reader = new Reader(back);
        assertEquals(0, reader.readVarInt());
        assertEquals(0, reader.readVarInt());
        assertEquals(0b10, reader.readVarInt()); // skyMask
        assertEquals(0, reader.readVarInt()); // blockMask
        reader.readVarInt(); // emptySkyMask（758 側の都合で値が変わりうる）
        reader.readVarInt(); // emptyBlockMask
        assertEquals(2048, reader.readVarInt()); // セクション0の空光配列
    }
}
