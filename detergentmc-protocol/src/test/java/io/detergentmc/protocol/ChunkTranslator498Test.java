package io.detergentmc.protocol;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import io.detergentmc.protocol.Nbt.Tag;
import io.detergentmc.protocol.PacketIO.Reader;
import io.detergentmc.protocol.PacketIO.Writer;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * 498（1.14.4）のチャンク・スポーン・粒子変換。573 との構造差：
 * バイオームは別フィールド（1024件）ではなくデータバッファ末尾の
 * 列ごと256件、Spawn Player／Spawn Mob に末尾メタデータ、
 * World Particles の座標は f32。
 */
class ChunkTranslator498Test {

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

    /** 498 のチャンク1個（セクション0のみ、石と草ブロック、BE は chest）。 */
    private static byte[] chunk498() {
        Writer writer = new Writer();
        writer.writeInt(0);
        writer.writeInt(0);
        writer.writeBoolean(true); // groundUp
        writer.writeVarInt(1); // mask: section 0
        Map<String, Tag> heightmaps = new LinkedHashMap<>();
        heightmaps.put("MOTION_BLOCKING", new Tag.LongArrayTag(heightmapCrossing(4)));
        heightmaps.put("WORLD_SURFACE", new Tag.LongArrayTag(heightmapCrossing(4)));
        writer.writeNamedNbt("", new Tag.CompoundTag(heightmaps));
        Writer buffer = new Writer();
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
        buffer.writeBytes(section.bytes());
        for (int i = 0; i < 256; i++) {
            buffer.writeInt(1); // バイオーム列（plains）＝バッファ末尾
        }
        byte[] bufferBytes = buffer.bytes();
        writer.writeVarInt(bufferBytes.length);
        writer.writeBytes(bufferBytes);
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
    void chunk498BiomeColumnsSurviveRoundTrip() {
        // 列ごとに違うバイオームを持つ 498 チャンクを作る。
        Writer writer = new Writer();
        writer.writeInt(0);
        writer.writeInt(0);
        writer.writeBoolean(true);
        writer.writeVarInt(0); // セクション無し（バイオームだけ見る）
        Map<String, Tag> heightmaps = new LinkedHashMap<>();
        heightmaps.put("MOTION_BLOCKING", new Tag.LongArrayTag(heightmapCrossing(4)));
        writer.writeNamedNbt("", new Tag.CompoundTag(heightmaps));
        Writer buffer = new Writer();
        for (int i = 0; i < 256; i++) {
            buffer.writeInt(i % 75);
        }
        byte[] bufferBytes = buffer.bytes();
        writer.writeVarInt(bufferBytes.length);
        writer.writeBytes(bufferBytes);
        writer.writeVarInt(0); // ブロックエンティティなし
        byte[] chunk = writer.bytes();

        byte[] out = ChunkTranslator.translateChunkData498To758(chunk, 64, null);
        byte[] back = ChunkTranslator.translateChunkData758To498(out, 64);
        Reader reader = new Reader(back);
        reader.readInt();
        reader.readInt();
        reader.readBoolean();
        reader.readVarInt();
        reader.readNamedNbt();
        int size = reader.readVarInt();
        Reader backBuffer = new Reader(reader.readBytes(size));
        // 758 の 4x4x4 バイオームセルは列の代表値で戻る。ただしレジストリ
        // 写像は変種バイオーム（hills 等）を素のバイオームへ寄せるため、
        // 往復後の期待値は代表値を 498→758→498 の写像に通したものになる。
        for (int bz = 0; bz < 16; bz++) {
            for (int bx = 0; bx < 16; bx++) {
                int representative = (((bz & ~3) * 16) + (bx & ~3)) % 75;
                int expected = RegistryRemapper.mapBiome758To498(
                        RegistryRemapper.mapBiome498To758(representative));
                assertEquals(expected, backBuffer.readInt(),
                        "biome column " + bx + "," + bz);
            }
        }
        assertEquals(0, backBuffer.remaining());
    }

    @Test
    void chunk498RoundTripsThrough758() {
        byte[] out = ChunkTranslator.translateChunkData498To758(chunk498(), 64, null);
        byte[] back = ChunkTranslator.translateChunkData758To498(out, 64);
        Reader reader = new Reader(back);
        assertEquals(0, reader.readInt());
        assertEquals(0, reader.readInt());
        assertEquals(true, reader.readBoolean());
        assertEquals(1, reader.readVarInt()); // mask はセクション0のみ
        Tag tag = reader.readNamedNbt().tag();
        Tag.CompoundTag root = (Tag.CompoundTag) tag;
        Tag.LongArrayTag array = (Tag.LongArrayTag) root.value().get("MOTION_BLOCKING");
        assertEquals(36, array.value().length);
        assertEquals(4, array.value()[0] & 0x1FFL);
        int size = reader.readVarInt();
        Reader buffer = new Reader(reader.readBytes(size));
        assertEquals(2, buffer.readShort()); // blockCount（石＋草の2個）
        buffer.readUnsignedByte();
        int paletteLength = buffer.readVarInt();
        assertEquals(3, paletteLength);
        for (int i = 0; i < paletteLength; i++) {
            buffer.readVarInt();
        }
        int dataLength = buffer.readVarInt();
        for (int i = 0; i < dataLength; i++) {
            buffer.readLong();
        }
        for (int i = 0; i < 256; i++) {
            assertEquals(1, buffer.readInt(), "biome column " + i);
        }
        assertEquals(0, buffer.remaining());
        assertEquals(1, reader.readVarInt()); // ブロックエンティティ 1件
        Tag beTag = reader.readNamedNbt().tag();
        Tag.CompoundTag be = (Tag.CompoundTag) beTag;
        assertEquals("minecraft:chest", be.getString("id").orElse(""));
    }

    @Test
    void namedEntitySpawn498SplitsTrailingMetadata() {
        UUID uuid = UUID.fromString("11111111-2222-3333-4444-555555555555");
        Writer writer = new Writer();
        writer.writeVarInt(7);
        writer.writeUuid(uuid);
        writer.writeDouble(1.5);
        writer.writeDouble(64.0);
        writer.writeDouble(-2.5);
        writer.writeByte(10);
        writer.writeByte(-20);
        byte[] head = writer.bytes();
        Writer withMeta = new Writer();
        withMeta.writeBytes(head);
        withMeta.writeByte(0); // index 0
        withMeta.writeByte(0); // type byte
        withMeta.writeByte(0x7F); // value
        withMeta.writeByte(0xFF); // terminator
        byte[][] out = ChunkTranslator.namedEntitySpawn498To758(withMeta.bytes());
        assertArrayEquals(head, out[0]);
        assertNotNull(out[1]);
    }

    @Test
    void spawnLiving758To498AppendsMetadataTerminator() {
        int pig758 = RegistryRemapper.mapEntityType498To758(54);
        UUID uuid = UUID.fromString("11111111-2222-3333-4444-555555555555");
        Writer writer = new Writer();
        writer.writeVarInt(9);
        writer.writeUuid(uuid);
        writer.writeVarInt(pig758);
        writer.writeDouble(1.0);
        writer.writeDouble(64.0);
        writer.writeDouble(3.0);
        writer.writeByte(10);
        writer.writeByte(20);
        writer.writeByte(30);
        writer.writeShort(0);
        writer.writeShort(0);
        writer.writeShort(0);
        byte[] out = ChunkTranslator.translateSpawnLiving758To498(writer.bytes());
        assertEquals(0xFF, out[out.length - 1] & 0xFF);
        Reader reader = new Reader(out);
        assertEquals(9, reader.readVarInt());
        reader.readBytes(16);
        assertEquals(54, reader.readVarInt()); // 498 の pig
    }

    @Test
    void worldParticles498DustConvertsCoordsToDouble() {
        Writer writer = new Writer();
        writer.writeVarInt(14); // dust（498）
        writer.writeBoolean(false);
        writer.writeFloat(1.5f);
        writer.writeFloat(64.0f);
        writer.writeFloat(-2.5f);
        writer.writeFloat(0.1f);
        writer.writeFloat(0.2f);
        writer.writeFloat(0.3f);
        writer.writeFloat(0.5f);
        writer.writeInt(7);
        writer.writeFloat(1.0f);
        writer.writeFloat(0.5f);
        writer.writeFloat(0.25f);
        writer.writeFloat(2.0f);
        byte[] out = ChunkTranslator.translateWorldParticles498To758(writer.bytes());
        Reader reader = new Reader(out);
        assertEquals(RegistryRemapper.mapParticle498To758(14), reader.readVarInt());
        assertEquals(false, reader.readBoolean());
        assertEquals(1.5, reader.readDouble());
        assertEquals(64.0, reader.readDouble());
        assertEquals(-2.5, reader.readDouble());
        assertEquals(0.1f, reader.readFloat());
        assertEquals(0.2f, reader.readFloat());
        assertEquals(0.3f, reader.readFloat());
        assertEquals(0.5f, reader.readFloat());
        assertEquals(7, reader.readInt());
        assertEquals(1.0f, reader.readFloat());
        assertEquals(0.5f, reader.readFloat());
        assertEquals(0.25f, reader.readFloat());
        assertEquals(2.0f, reader.readFloat());
        assertEquals(0, reader.remaining());
    }

    @Test
    void worldParticles758DustConvertsCoordsToFloat() {
        int dust758 = RegistryRemapper.mapParticle498To758(14);
        Writer writer = new Writer();
        writer.writeVarInt(dust758);
        writer.writeBoolean(true);
        writer.writeDouble(1.5);
        writer.writeDouble(64.0);
        writer.writeDouble(-2.5);
        writer.writeFloat(0.1f);
        writer.writeFloat(0.2f);
        writer.writeFloat(0.3f);
        writer.writeFloat(0.5f);
        writer.writeInt(3);
        writer.writeFloat(0.0f);
        writer.writeFloat(1.0f);
        writer.writeFloat(0.0f);
        writer.writeFloat(1.5f);
        byte[] out = ChunkTranslator.translateWorldParticles758To498(writer.bytes());
        Reader reader = new Reader(out);
        assertEquals(14, reader.readVarInt());
        assertEquals(true, reader.readBoolean());
        assertEquals(1.5f, reader.readFloat());
        assertEquals(64.0f, reader.readFloat());
        assertEquals(-2.5f, reader.readFloat());
        assertEquals(0.1f, reader.readFloat());
        assertEquals(0.2f, reader.readFloat());
        assertEquals(0.3f, reader.readFloat());
        assertEquals(0.5f, reader.readFloat());
        assertEquals(3, reader.readInt());
        assertEquals(0.0f, reader.readFloat());
        assertEquals(1.0f, reader.readFloat());
        assertEquals(0.0f, reader.readFloat());
        assertEquals(1.5f, reader.readFloat());
        assertEquals(0, reader.remaining());
    }
}
