package io.detergentmc.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.detergentmc.protocol.Nbt.Tag;
import io.detergentmc.protocol.PacketIO.Reader;
import io.detergentmc.protocol.PacketIO.Writer;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * 575（1.15.1）のチャンク変換。575 のチャンク形式は 578 と同一
 * （Stage A で実データ証明済み）で、ProxySession は 575 のチャンクを
 * 578 と同じ {@link ChunkTranslator} の pairwise 変換へ通す。
 * ここでは ChunkTranslator578Test と同じ形のチャンクで往復を固定する。
 */
class ChunkTranslator575Test {

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

    /** 575／578 のチャンク1個（セクション0のみ、石と草ブロック、BE は chest）。 */
    private static byte[] chunk575() {
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

    @Test
    void chunk575RoundTripsThrough758() {
        byte[] out = ChunkTranslator.translateChunkData578To758(chunk575(), 64, null);
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
        assertEquals(1, reader.readVarInt()); // ブロックエンティティ 1件
        Tag beTag = reader.readNamedNbt().tag();
        Tag.CompoundTag be = (Tag.CompoundTag) beTag;
        assertEquals("minecraft:chest", be.getString("id").orElse(""));
    }
}
