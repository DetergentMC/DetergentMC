package io.detergentmc.protocol;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.detergentmc.protocol.Nbt.Tag;
import io.detergentmc.protocol.PacketIO.Reader;
import io.detergentmc.protocol.PacketIO.Writer;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * {@link ChunkTranslator} の756（1.17.1）⇔757（1.18／1.18.1）向け変換テスト。
 * 756・757 ともデータコンポーネントは無い（NBT 時代）のため、スロットは
 * bool＋アイテムID＋個数＋名前付き NBT で、NBT は素通しする。
 * レジストリ差分（Stage A §11.1 で実測）：ブロック状態 20,342 件・同一順序、
 * ブロック 898 件・同一、アイテム 1,101→1,100（music_disc_otherside のみ新規）、
 * エンティティ 113 件・同一、音 1,203→1,190（新規 13 件）、
 * パーティクル 88→89（barrier＋light→block_marker 統合）、
 * バイオーム 61→81（改名 11・削除 26・新規 6）、メニュー 24・属性 13・
 * エンチャント 38・ブロックエンティティ 34・エフェクト 32 はいずれも同一。
 * チャンク形式の差は実バニラ 1.17.1 サーバーからの生キャプチャと両バージョンの
 * jar の javap で実測：756 は 16 セクション＋バイオーム int[1024]＋光無し、
 * 757 は 24 セクション＋セクションごとのバイオームパレット＋光内包。
 * BitStorage は両方とも non-straddling（BPE 5 の Data Array 長は 342）。
 */
class ChunkTranslator756Test {

    /** 高さマップ（名前付き NBT のコンパウンド）。 */
    private static Tag heightmaps() {
        Map<String, Tag> entries = new LinkedHashMap<>();
        entries.put("MOTION_BLOCKING", new Tag.LongArrayTag(new long[37]));
        entries.put("WORLD_SURFACE", new Tag.LongArrayTag(new long[37]));
        return new Tag.CompoundTag(entries);
    }

    /** スロット1個（756／757形式：bool＋アイテムID＋個数＋名前付き NBT 無し）。 */
    private static void writeSlot(Writer writer, int itemId, int count) {
        writer.writeBoolean(true);
        writer.writeVarInt(itemId);
        writer.writeByte(count);
        writer.writeByte(0); // NBT 無し
    }

    /** スロットの先頭（個数＋アイテムID）を読む。 */
    private static int[] readSlotHead(Reader reader) {
        assertTrue(reader.readBoolean());
        int itemId = reader.readVarInt();
        int count = reader.readByte();
        assertEquals(0, reader.readByte()); // NBT 無し
        return new int[]{itemId, count};
    }

    /** 間接パレットの756セクション1個（ブロックコンテナのみ）。 */
    private static byte[] section756(int blockCount, int[] palette) {
        Writer writer = new Writer();
        writer.writeShort(blockCount);
        writer.writeUnsignedByte(4); // BPE（間接）
        writer.writeVarInt(palette.length);
        for (int value : palette) {
            writer.writeVarInt(value);
        }
        writer.writeVarInt(256); // Data Array：4096エントリ×4bit＝256 long
        for (int i = 0; i < 256; i++) {
            writer.writeLong(0L);
        }
        return writer.bytes();
    }

    /** 間接パレットの757セクション1個（ブロック＋バイオーム）。 */
    private static byte[] section757(int blockCount, int[] blockPalette, int[] biomePalette) {
        Writer writer = new Writer();
        writer.writeShort(blockCount);
        writer.writeUnsignedByte(4); // ブロック BPE（間接）
        writer.writeVarInt(blockPalette.length);
        for (int value : blockPalette) {
            writer.writeVarInt(value);
        }
        writer.writeVarInt(256); // Data Array：4096エントリ×4bit＝256 long
        for (int i = 0; i < 256; i++) {
            writer.writeLong(0L);
        }
        writer.writeUnsignedByte(2); // バイオーム BPE（間接）
        writer.writeVarInt(biomePalette.length);
        for (int value : biomePalette) {
            writer.writeVarInt(value);
        }
        writer.writeVarInt(2); // Data Array：64エントリ×2bit＝2 long
        for (int i = 0; i < 2; i++) {
            writer.writeLong(0L);
        }
        return writer.bytes();
    }

    /** 空の757セクション1個（ブロック・バイオームともシングルパレット）。 */
    private static byte[] emptySection757() {
        Writer writer = new Writer();
        writer.writeShort(0);
        writer.writeUnsignedByte(0);
        writer.writeVarInt(0); // air
        writer.writeVarInt(0);
        writer.writeUnsignedByte(0);
        writer.writeVarInt(1); // plains
        writer.writeVarInt(0);
        return writer.bytes();
    }

    /** 756 のブロックエンティティ NBT（id／x／y／z＋追加データ）。 */
    private static Tag blockEntityNbt756(String id, int x, int y, int z,
            Map<String, Tag> extra) {
        Map<String, Tag> entries = new LinkedHashMap<>();
        entries.put("id", new Tag.StringTag(id));
        entries.put("x", new Tag.IntTag(x));
        entries.put("y", new Tag.IntTag(y));
        entries.put("z", new Tag.IntTag(z));
        entries.putAll(extra);
        return new Tag.CompoundTag(entries);
    }

    /** 756形式の Chunk Data ペイロード。 */
    private static byte[] chunkDataPayload756(int chunkX, int chunkZ, long bitmask,
            byte[] chunkData, int[] biomes, Tag[] blockEntities) {
        Writer payload = new Writer();
        payload.writeInt(chunkX);
        payload.writeInt(chunkZ);
        payload.writeVarInt(1); // bitMap の long 数
        payload.writeLong(bitmask);
        payload.writeNamedNbt("", heightmaps());
        payload.writeVarInt(biomes.length);
        for (int biome : biomes) {
            payload.writeVarInt(biome);
        }
        payload.writeByteArray(chunkData);
        payload.writeVarInt(blockEntities.length);
        for (Tag tag : blockEntities) {
            payload.writeNamedNbt("", tag);
        }
        return payload.bytes();
    }

    /** 757形式の Chunk Data ペイロード。blockEntities は {packedXZ, y, type, NBT} の列。 */
    private static byte[] chunkDataPayload757(int chunkX, int chunkZ,
            byte[] chunkData, Object[][] blockEntities, boolean trustEdges, byte[] light) {
        Writer payload = new Writer();
        payload.writeInt(chunkX);
        payload.writeInt(chunkZ);
        payload.writeNamedNbt("", heightmaps());
        payload.writeByteArray(chunkData);
        payload.writeVarInt(blockEntities.length);
        for (Object[] be : blockEntities) {
            payload.writeUnsignedByte((int) be[0]);
            payload.writeShort((int) be[1]);
            payload.writeVarInt((int) be[2]);
            Tag nbt = (Tag) be[3];
            if (nbt == null) {
                payload.writeByte(0);
            } else {
                payload.writeNamedNbt("", nbt);
            }
        }
        payload.writeBoolean(trustEdges);
        payload.writeBytes(light);
        return payload.bytes();
    }

    /** 756形式の Chunk Data を読む。 */
    private static class ParsedChunk756 {
        long bitmask;
        int[] biomes;
        byte[] sections;
        List<Tag> blockEntities = new ArrayList<>();
    }

    private static ParsedChunk756 readChunkData756(byte[] payload) {
        Reader reader = new Reader(payload);
        ParsedChunk756 out = new ParsedChunk756();
        reader.readInt(); // chunkX
        reader.readInt(); // chunkZ
        int maskCount = reader.readVarInt();
        assertEquals(1, maskCount);
        out.bitmask = reader.readLong();
        assertTrue(reader.readNamedNbt().tag() instanceof Tag.CompoundTag);
        int biomeCount = reader.readVarInt();
        assertEquals(1024, biomeCount);
        out.biomes = new int[1024];
        for (int i = 0; i < 1024; i++) {
            out.biomes[i] = reader.readVarInt();
        }
        out.sections = reader.readByteArray();
        int count = reader.readVarInt();
        for (int i = 0; i < count; i++) {
            out.blockEntities.add(reader.readNamedNbt().tag());
        }
        assertEquals(0, reader.remaining());
        return out;
    }

    /** 757形式の Chunk Data を読む。 */
    private static class ParsedChunk757 {
        byte[] sections;
        int[] blockEntityTypes;
        List<Tag> blockEntityNbt = new ArrayList<>();
        boolean trustEdges;
        byte[] light;
    }

    private static ParsedChunk757 readChunkData757(byte[] payload) {
        Reader reader = new Reader(payload);
        ParsedChunk757 out = new ParsedChunk757();
        reader.readInt(); // chunkX
        reader.readInt(); // chunkZ
        assertTrue(reader.readNamedNbt().tag() instanceof Tag.CompoundTag);
        out.sections = reader.readByteArray();
        int count = reader.readVarInt();
        out.blockEntityTypes = new int[count];
        for (int i = 0; i < count; i++) {
            reader.readUnsignedByte();
            reader.readShort();
            out.blockEntityTypes[i] = reader.readVarInt();
            out.blockEntityNbt.add(reader.readNamedNbt().tag());
        }
        out.trustEdges = reader.readBoolean();
        out.light = reader.rest();
        assertEquals(0, reader.remaining());
        return out;
    }

    /** 757形式のセクション列を読み、各セクションの {blockCount, biomeValues[64]} を返す。 */
    private static List<int[]> readSections757(byte[] sections, List<Integer> blockCounts) {
        Reader reader = new Reader(sections);
        List<int[]> biomes = new ArrayList<>();
        while (reader.remaining() > 0) {
            blockCounts.add((int) reader.readShort());
            skipBlockContainer(reader);
            biomes.add(readBiomeContainer(reader));
        }
        return biomes;
    }

    /** 757形式のブロックコンテナ1個を読み飛ばす（テスト用。長さは宣言値を信じる）。 */
    private static void skipBlockContainer(Reader reader) {
        int bits = reader.readUnsignedByte();
        if (bits == 0) {
            reader.readVarInt();
            assertEquals(0, reader.readVarInt());
            return;
        }
        if (bits <= 8) {
            int paletteLength = reader.readVarInt();
            for (int i = 0; i < paletteLength; i++) {
                reader.readVarInt();
            }
        }
        int dataLength = reader.readVarInt();
        for (int i = 0; i < dataLength; i++) {
            reader.readLong();
        }
    }

    /** 757形式のバイオームコンテナ1個を読み、64 件の値を返す（テスト用）。 */
    private static int[] readBiomeContainer(Reader reader) {
        int bits = reader.readUnsignedByte();
        int[] values = new int[64];
        if (bits == 0) {
            int value = reader.readVarInt();
            assertEquals(0, reader.readVarInt());
            for (int i = 0; i < 64; i++) {
                values[i] = value;
            }
            return values;
        }
        if (bits <= 3) {
            int paletteLength = reader.readVarInt();
            int[] palette = new int[paletteLength];
            for (int i = 0; i < paletteLength; i++) {
                palette[i] = reader.readVarInt();
            }
            int dataLength = reader.readVarInt();
            long[] longs = new long[dataLength];
            for (int i = 0; i < dataLength; i++) {
                longs[i] = reader.readLong();
            }
            int perLong = 64 / bits;
            long mask = (1L << bits) - 1;
            for (int i = 0; i < 64; i++) {
                int index = (int) ((longs[i / perLong] >>> ((i % perLong) * bits)) & mask);
                values[i] = palette[index];
            }
            return values;
        }
        int dataLength = reader.readVarInt();
        long[] longs = new long[dataLength];
        for (int i = 0; i < dataLength; i++) {
            longs[i] = reader.readLong();
        }
        int perLong = 64 / bits;
        long mask = (1L << bits) - 1;
        for (int i = 0; i < 64; i++) {
            values[i] = (int) ((longs[i / perLong] >>> ((i % perLong) * bits)) & mask);
        }
        return values;
    }

    /** 756形式の Update Light ペイロード。マスクは long[1]、光配列は 2048 バイトのゼロ列。 */
    private static byte[] updateLightPayload756(int chunkX, int chunkZ, boolean trustEdges,
            long[] masks, int[] arrayCounts) {
        Writer writer = new Writer();
        writer.writeVarInt(chunkX);
        writer.writeVarInt(chunkZ);
        writer.writeBoolean(trustEdges);
        for (long mask : masks) {
            writer.writeVarInt(1);
            writer.writeLong(mask);
        }
        for (int count : arrayCounts) {
            writer.writeVarInt(count);
            for (int i = 0; i < count; i++) {
                writer.writeVarInt(2048);
                writer.writeBytes(new byte[2048]);
            }
        }
        return writer.bytes();
    }

    /** Update Light を読み、マスク4個と光配列の件数を返す。 */
    private static class ParsedLight {
        long[] masks = new long[4];
        int[] arrayCounts = new int[2];
        List<byte[]> skyArrays = new ArrayList<>();
        List<byte[]> blockArrays = new ArrayList<>();
    }

    private static ParsedLight readUpdateLight(byte[] payload) {
        Reader reader = new Reader(payload);
        ParsedLight out = new ParsedLight();
        reader.readVarInt(); // chunkX
        reader.readVarInt(); // chunkZ
        reader.readBoolean(); // trustEdges
        for (int i = 0; i < 4; i++) {
            int count = reader.readVarInt();
            if (count == 0) {
                out.masks[i] = 0L;
            } else {
                assertEquals(1, count);
                out.masks[i] = reader.readLong();
            }
        }
        for (int k = 0; k < 2; k++) {
            int count = reader.readVarInt();
            out.arrayCounts[k] = count;
            List<byte[]> arrays = k == 0 ? out.skyArrays : out.blockArrays;
            for (int i = 0; i < count; i++) {
                arrays.add(reader.readByteArray());
            }
        }
        assertEquals(0, reader.remaining());
        return out;
    }

    /** 759形式の World Particles ペイロード（粒子IDは varint）。data は既に書いたバイト列。 */
    private static byte[] worldParticlesPacket(int particleId, byte[] data) {
        Writer writer = new Writer();
        writer.writeInt(particleId);
        writer.writeBytes(data);
        writer.writeBoolean(true); // longDistance
        writer.writeDouble(1.5);
        writer.writeDouble(2.5);
        writer.writeDouble(3.5);
        writer.writeFloat(0.1f);
        writer.writeFloat(0.2f);
        writer.writeFloat(0.3f);
        writer.writeFloat(0.4f); // speed
        writer.writeInt(5); // count
        return writer.bytes();
    }

    // ---- スロット ----

    @Test
    void slotRoundTrip756To757To756() {
        Writer writer = new Writer();
        writeSlot(writer, 1, 64); // stone（756=1・757=1）
        byte[] original = writer.bytes();
        Reader reader = new Reader(original);
        Writer mid = new Writer();
        ChunkTranslator.translateSlot756To757(reader, mid);
        assertEquals(0, reader.remaining());
        Reader reader2 = new Reader(mid.bytes());
        Writer back = new Writer();
        ChunkTranslator.translateSlot757To756(reader2, back);
        assertEquals(0, reader2.remaining());
        assertArrayEquals(original, back.bytes());
    }

    @Test
    void slotNewItem757FallsBackTo756() {
        // music_disc_otherside（757=1027）→ music_disc_13（756=1015）。
        Writer writer = new Writer();
        writeSlot(writer, 1027, 1);
        Reader reader = new Reader(writer.bytes());
        Writer out = new Writer();
        ChunkTranslator.translateSlot757To756(reader, out);
        assertArrayEquals(new int[]{1015, 1}, readSlotHead(new Reader(out.bytes())));
        // 逆向き：music_disc_13（756=1015）→ music_disc_13（757=1015）。
        Writer writer2 = new Writer();
        writeSlot(writer2, 1015, 2);
        Reader reader2 = new Reader(writer2.bytes());
        Writer out2 = new Writer();
        ChunkTranslator.translateSlot756To757(reader2, out2);
        assertArrayEquals(new int[]{1015, 2}, readSlotHead(new Reader(out2.bytes())));
    }

    // ---- Chunk Data ----

    @Test
    void chunkDataRoundTrip756To757To756() {
        // 756 チャンク：セクション 0 と 2 があり、バイオームはセクション 0 が
        // swamp（6）尽くし、セクション 2 が snowy_tundra（12）＋plains（1）。
        // ブロックエンティティはチェストとスポナー。
        int[] biomes = new int[1024];
        for (int i = 0; i < 1024; i++) {
            biomes[i] = 1; // plains
        }
        for (int i = 0; i < 64; i++) {
            biomes[i] = 6; // セクション 0：swamp
        }
        for (int i = 128; i < 160; i++) {
            biomes[i] = 12; // セクション 2 の前半：snowy_tundra
        }
        Writer sections = new Writer();
        sections.writeBytes(section756(4096, new int[]{0, 1, 2}));
        sections.writeBytes(section756(100, new int[]{0, 1, 5}));
        Map<String, Tag> spawnerExtra = new LinkedHashMap<>();
        spawnerExtra.put("Delay", new Tag.ShortTag((short) 20));
        byte[] original = chunkDataPayload756(3, -2, 0b101L, sections.bytes(), biomes,
                new Tag[]{
                    blockEntityNbt756("minecraft:chest", 49, 64, -31, Map.of()),
                    blockEntityNbt756("minecraft:mob_spawner", 50, 65, -30, spawnerExtra),
                });
        byte[] up = ChunkTranslator.translateChunkData756To757(original);
        ParsedChunk757 parsed = readChunkData757(up);
        // 757 は 24 セクション（-4〜19）。セクション 0（位置 4）と 2（位置 6）だけ中身がある。
        List<Integer> blockCounts = new ArrayList<>();
        List<int[]> sectionBiomes = readSections757(parsed.sections, blockCounts);
        assertEquals(24, sectionBiomes.size());
        assertEquals(4096, (int) blockCounts.get(4));
        assertEquals(100, (int) blockCounts.get(6));
        assertEquals(0, (int) blockCounts.get(0)); // y -4 は空
        assertEquals(0, (int) blockCounts.get(23)); // y 19 は空
        for (int value : sectionBiomes.get(4)) {
            assertEquals(6, value); // swamp は両方とも 6
        }
        for (int i = 0; i < 32; i++) {
            assertEquals(3, sectionBiomes.get(6)[i]); // snowy_tundra（12）→ snowy_plains（3）
        }
        for (int i = 32; i < 64; i++) {
            assertEquals(1, sectionBiomes.get(6)[i]); // plains
        }
        // ブロックエンティティは種別 1（chest）・8（mob_spawner）。
        // チェストは id／x／y／z 以外のデータが無いため、剥がした NBT は空になり
        // 757 では NBT 無し（null）になる。スポナーは Delay が残る。
        assertArrayEquals(new int[]{1, 8}, parsed.blockEntityTypes);
        assertNull(parsed.blockEntityNbt.get(0));
        Tag spawnerNbt = parsed.blockEntityNbt.get(1);
        assertTrue(spawnerNbt instanceof Tag.CompoundTag);
        assertTrue(((Tag.CompoundTag) spawnerNbt).get("id").isEmpty());
        assertTrue(((Tag.CompoundTag) spawnerNbt).get("x").isEmpty());
        assertEquals(20, (int) ((Tag.ShortTag) ((Tag.CompoundTag) spawnerNbt)
                .get("Delay").orElseThrow()).value());
        // 光は空で合成される（マスク 4 個＋配列件数 2 個がすべて varint 0 で 6 バイト）。
        assertEquals(false, parsed.trustEdges);
        assertArrayEquals(new byte[]{0, 0, 0, 0, 0, 0}, parsed.light);
        // 往復で元に完全に戻る。
        assertArrayEquals(original, ChunkTranslator.translateChunkData757To756(up));
    }

    @Test
    void chunkData757To756DropsLightAndKeepsBiomes() {
        // 757 チャンク：y 0 が snowy_slopes（30）、y 1 が swamp／plains、
        // 光は trustEdges 付きでマスクと配列が入っている。
        Writer sections = new Writer();
        for (int i = 0; i < 4; i++) {
            sections.writeBytes(emptySection757()); // y -4〜-1
        }
        sections.writeBytes(section757(10, new int[]{0, 1}, new int[]{30}));
        sections.writeBytes(section757(20, new int[]{0, 1}, new int[]{6, 1}));
        for (int i = 0; i < 18; i++) {
            sections.writeBytes(emptySection757()); // y 2〜19
        }
        Writer light = new Writer();
        light.writeVarInt(1);
        light.writeLong(0b110L); // skyLightMask：ビット 1・2
        light.writeVarInt(1);
        light.writeLong(0b100L); // blockLightMask：ビット 2
        light.writeVarInt(1);
        light.writeLong(0L); // emptySkyLightMask
        light.writeVarInt(1);
        light.writeLong(0L); // emptyBlockLightMask
        light.writeVarInt(2); // skyLight：2 配列
        light.writeVarInt(2048);
        light.writeBytes(new byte[2048]);
        light.writeVarInt(2048);
        light.writeBytes(new byte[2048]);
        light.writeVarInt(1); // blockLight：1 配列
        light.writeVarInt(2048);
        light.writeBytes(new byte[2048]);
        Map<String, Tag> signExtra = new LinkedHashMap<>();
        signExtra.put("Text1", new Tag.StringTag("{\"text\":\"hi\"}"));
        byte[] payload757 = chunkDataPayload757(3, -2, sections.bytes(),
                new Object[][]{{0x11, 64, 1, new Tag.CompoundTag(signExtra)}}, true,
                light.bytes());
        byte[] translated = ChunkTranslator.translateChunkData757To756(payload757);
        ParsedChunk756 parsed = readChunkData756(translated);
        assertEquals(0b11L, parsed.bitmask); // セクション 0・1 だけ
        for (int i = 0; i < 64; i++) {
            assertEquals(12, parsed.biomes[i]); // snowy_slopes（30）→ snowy_tundra（12）
            assertEquals(6, parsed.biomes[64 + i]); // swamp（間接パレットの先頭）
        }
        for (int i = 128; i < 1024; i++) {
            assertEquals(1, parsed.biomes[i]); // 残りは plains
        }
        assertEquals(1, parsed.blockEntities.size());
        Tag signNbt = parsed.blockEntities.get(0);
        assertTrue(signNbt instanceof Tag.CompoundTag);
        Tag.CompoundTag sign = (Tag.CompoundTag) signNbt;
        assertEquals("minecraft:chest",
                sign.getString("id").orElseThrow());
        assertEquals(49, (int) ((Tag.IntTag) sign.get("x").orElseThrow()).value());
        assertEquals(64, (int) ((Tag.IntTag) sign.get("y").orElseThrow()).value());
        assertEquals(-31, (int) ((Tag.IntTag) sign.get("z").orElseThrow()).value());
        assertEquals("{\"text\":\"hi\"}",
                sign.getString("Text1").orElseThrow());
        // 抜き出した Update Light は 756 形式。sky マスクのビット {1,2} は
        // -4 ずれて落ちるため、光配列は全部落ちる。
        byte[] updateLight = ChunkTranslator.extractUpdateLightFromChunkData757(payload757);
        ParsedLight parsedLight = readUpdateLight(updateLight);
        assertEquals(0L, parsedLight.masks[0]);
        assertEquals(0, parsedLight.arrayCounts[0]); // sky 配列は全部落ちる
        assertEquals(0, parsedLight.arrayCounts[1]);
    }

    @Test
    void realCapturedChunk756RoundTrip() throws Exception {
        // 実バニラ 1.17.1 サーバーからの生キャプチャ（チャンク -21,-17。
        // bitMap 0x1f、セクション 0〜4、バイオームは swamp（6）尽くし、
        // BPE 5 の Data Array 長は 342＝non-straddling）。
        byte[] captured;
        try (InputStream in = getClass().getResourceAsStream(
                "/cap756-chunk-21-17.bin")) {
            assertTrue(in != null, "テストリソースが見つかりません");
            captured = in.readAllBytes();
        }
        assertEquals(13449, captured.length);
        byte[] up = ChunkTranslator.translateChunkData756To757(captured);
        ParsedChunk757 parsed = readChunkData757(up);
        List<Integer> blockCounts = new ArrayList<>();
        List<int[]> sectionBiomes = readSections757(parsed.sections, blockCounts);
        assertEquals(24, sectionBiomes.size());
        assertEquals(4096, (int) blockCounts.get(4)); // y 0
        assertEquals(434, (int) blockCounts.get(8)); // y 4
        assertEquals(0, (int) blockCounts.get(9)); // y 5 は空
        for (int s = 0; s < 5; s++) {
            for (int value : sectionBiomes.get(4 + s)) {
                assertEquals(6, value);
            }
        }
        // 往復で元のバイト列に完全に戻る。
        assertArrayEquals(captured, ChunkTranslator.translateChunkData757To756(up));
    }

    // ---- 光 ----

    @Test
    void updateLightRoundTrip756To757To756() {
        // 実キャプチャ（チャンク -21,-17）のマスク値を使う。
        byte[] original = updateLightPayload756(-21, -17, true,
                new long[]{0b1110000L, 0b111100L, 0b1111L, 0b1000011L},
                new int[]{3, 4});
        byte[] up = ChunkTranslator.translateUpdateLight756To757(original);
        ParsedLight parsedUp = readUpdateLight(up);
        assertEquals(0b1110000L << 4, parsedUp.masks[0]);
        assertEquals(0b111100L << 4, parsedUp.masks[1]);
        assertEquals(0b1111L << 4, parsedUp.masks[2]);
        assertEquals(0b1000011L << 4, parsedUp.masks[3]);
        assertArrayEquals(new int[]{3, 4}, parsedUp.arrayCounts);
        assertArrayEquals(original, ChunkTranslator.translateUpdateLight757To756(up));
    }

    @Test
    void updateLight757OutOfRangeBitsAreDropped() {
        // 757 の skyMask ビット {0, 4, 21, 25}（配列は先頭バイトで区別する）。
        Writer writer = new Writer();
        writer.writeVarInt(3);
        writer.writeVarInt(-2);
        writer.writeBoolean(true);
        writer.writeVarInt(1);
        writer.writeLong((1L << 0) | (1L << 4) | (1L << 21) | (1L << 25));
        writer.writeVarInt(0); // blockLightMask
        writer.writeVarInt(0); // emptySkyLightMask
        writer.writeVarInt(0); // emptyBlockLightMask
        writer.writeVarInt(4); // skyLight：4 配列
        for (int i = 0; i < 4; i++) {
            byte[] array = new byte[2048];
            array[0] = (byte) (i + 1);
            writer.writeVarInt(2048);
            writer.writeBytes(array);
        }
        writer.writeVarInt(0); // blockLight
        byte[] translated = ChunkTranslator.translateUpdateLight757To756(writer.bytes());
        ParsedLight parsed = readUpdateLight(translated);
        // ビット 0・25 は落ち、ビット 4→0・21→17 が残る。
        assertEquals((1L << 0) | (1L << 17), parsed.masks[0]);
        assertEquals(2, parsed.arrayCounts[0]);
        assertEquals(2, parsed.skyArrays.get(0)[0]); // 元の配列 1（ビット 4）
        assertEquals(3, parsed.skyArrays.get(1)[0]); // 元の配列 2（ビット 21）
    }

    // ---- ブロック変化 ----

    @Test
    void multiBlockChange757OutOfRangeIsDropped() {
        // y=3（chunkCoordinates の下位 20 ビット＝3）は通る。
        Writer writer = new Writer();
        writer.writeLong(3L);
        writer.writeBoolean(true);
        writer.writeVarInt(1);
        writer.writeVarLong((5L << 12) | 0x123L);
        byte[] translated = ChunkTranslator.translateMultiBlockChange757To756(writer.bytes());
        Reader reader = new Reader(translated);
        assertEquals(3L, reader.readLong());
        assertEquals(true, reader.readBoolean());
        assertEquals(1, reader.readVarInt());
        assertEquals((5L << 12) | 0x123L, reader.readVarLong());
        assertEquals(0, reader.remaining());
        // y=-1（下位 20 ビットが全部 1）は 756 に無いセクションのため落とす。
        Writer writer2 = new Writer();
        writer2.writeLong(0xFFFFFL);
        writer2.writeBoolean(true);
        writer2.writeVarInt(1);
        writer2.writeVarLong((5L << 12) | 0x123L);
        assertNull(ChunkTranslator.translateMultiBlockChange757To756(writer2.bytes()));
        // 756→757 はそのまま通る。
        byte[] up = ChunkTranslator.translateMultiBlockChange756To757(writer.bytes());
        assertArrayEquals(writer.bytes(), up);
    }

    // ---- メタデータ ----

    @Test
    void entityMetadataSlotParticleBlockState() {
        // 756：スロット（型 6、stone 1）・粒子（型 15、falling_dust 25＋
        // ブロック状態 21）・ブロック状態（型 13、5）・float（型 2、1.5f）。
        Writer writer = new Writer();
        writer.writeVarInt(5); // entityId
        writer.writeUnsignedByte(0);
        writer.writeVarInt(6); // slot
        writeSlot(writer, 1, 1);
        writer.writeUnsignedByte(1);
        writer.writeVarInt(15); // particle
        writer.writeVarInt(25); // falling_dust（756）
        writer.writeVarInt(21); // ブロック状態
        writer.writeUnsignedByte(2);
        writer.writeVarInt(13); // ブロック状態
        writer.writeVarInt(5);
        writer.writeUnsignedByte(3);
        writer.writeVarInt(2); // float
        writer.writeFloat(1.5f);
        writer.writeUnsignedByte(0xFF);
        byte[] translated = ChunkTranslator.translateEntityMetadata756To757(writer.bytes());
        Reader reader = new Reader(translated);
        assertEquals(5, reader.readVarInt());
        assertEquals(0, reader.readUnsignedByte());
        assertEquals(6, reader.readVarInt());
        assertArrayEquals(new int[]{1, 1}, readSlotHead(reader));
        assertEquals(1, reader.readUnsignedByte());
        assertEquals(15, reader.readVarInt());
        assertEquals(24, reader.readVarInt()); // falling_dust（757）
        assertEquals(21, reader.readVarInt());
        assertEquals(2, reader.readUnsignedByte());
        assertEquals(13, reader.readVarInt());
        assertEquals(5, reader.readVarInt());
        assertEquals(3, reader.readUnsignedByte());
        assertEquals(2, reader.readVarInt());
        assertEquals(1.5f, reader.readFloat());
        assertEquals(0xFF, reader.readUnsignedByte());
        assertEquals(0, reader.remaining());
        // 往復で元に戻る。
        assertArrayEquals(writer.bytes(),
                ChunkTranslator.translateEntityMetadata757To756(translated));
        // 757→756：スロットの otherside（1027）は music_disc_13（1015）に落ちる。
        Writer writer757 = new Writer();
        writer757.writeVarInt(5);
        writer757.writeUnsignedByte(0);
        writer757.writeVarInt(6); // slot
        writeSlot(writer757, 1027, 1);
        writer757.writeUnsignedByte(0xFF);
        byte[] translatedDown = ChunkTranslator.translateEntityMetadata757To756(writer757.bytes());
        Reader readerDown = new Reader(translatedDown);
        assertEquals(5, readerDown.readVarInt());
        assertEquals(0, readerDown.readUnsignedByte());
        assertEquals(6, readerDown.readVarInt());
        assertArrayEquals(new int[]{1015, 1}, readSlotHead(readerDown));
        assertEquals(0xFF, readerDown.readUnsignedByte());
        assertEquals(0, readerDown.remaining());
    }

    // ---- 音 ----

    @Test
    void soundEffect757OthersideFallsBackTo756() {
        // music_disc.otherside（757=633）→ music_disc.13（756=617）。
        Writer writer = new Writer();
        writer.writeVarInt(633);
        writer.writeVarInt(0); // category
        writer.writeInt(10);
        writer.writeInt(20);
        writer.writeInt(30);
        writer.writeFloat(1.0f);
        writer.writeFloat(0.5f);
        byte[] translated = ChunkTranslator.translateSoundEffect757To756(writer.bytes());
        Reader reader = new Reader(translated);
        assertEquals(617, reader.readVarInt());
        assertEquals(0, reader.readVarInt());
        assertEquals(10, reader.readInt());
        assertEquals(20, reader.readInt());
        assertEquals(30, reader.readInt());
        assertEquals(1.0f, reader.readFloat());
        assertEquals(0.5f, reader.readFloat());
        assertEquals(0, reader.remaining());
        // Named Sound Effect：名前で指定する形式。
        Writer named = new Writer();
        named.writeString("music_disc.otherside");
        named.writeVarInt(0);
        named.writeInt(10);
        named.writeInt(20);
        named.writeInt(30);
        named.writeFloat(1.0f);
        named.writeFloat(0.5f);
        byte[] translatedNamed = ChunkTranslator.translateNamedSoundEffect757To756(named.bytes());
        Reader readerNamed = new Reader(translatedNamed);
        assertEquals("music_disc.13", readerNamed.readString());
        assertEquals(0, readerNamed.readVarInt());
        assertEquals(10, readerNamed.readInt());
        assertEquals(20, readerNamed.readInt());
        assertEquals(30, readerNamed.readInt());
        assertEquals(1.0f, readerNamed.readFloat());
        assertEquals(0.5f, readerNamed.readFloat());
        assertEquals(0, readerNamed.remaining());
        // Stop Sound：flags=3（音源＋名前）で名前だけ付け替える。
        Writer stop = new Writer();
        stop.writeByte(3);
        stop.writeVarInt(2); // source
        stop.writeString("music_disc.otherside");
        byte[] translatedStop = ChunkTranslator.translateStopSound757To756(stop.bytes());
        Reader readerStop = new Reader(translatedStop);
        assertEquals(3, readerStop.readByte());
        assertEquals(2, readerStop.readVarInt());
        assertEquals("music_disc.13", readerStop.readString());
        assertEquals(0, readerStop.remaining());
    }

    // ---- パーティクル ----

    @Test
    void worldParticlesBlockMarkerAndLight() {
        // block_marker（757=3、ブロック状態 7）→ barrier（756=2、データ無し）。
        Writer marker = new Writer();
        marker.writeVarInt(7);
        byte[] translated = ChunkTranslator.translateWorldParticles757To756(
                worldParticlesPacket(3, marker.bytes()));
        Reader reader = new Reader(translated);
        assertEquals(2, reader.readInt());
        assertEquals(true, reader.readBoolean()); // longDistance
        // falling_dust（757=24、ブロック状態 9）→ falling_dust（756=25、ブロック状態 9）。
        Writer dust = new Writer();
        dust.writeVarInt(9);
        byte[] translated2 = ChunkTranslator.translateWorldParticles757To756(
                worldParticlesPacket(24, dust.bytes()));
        Reader reader2 = new Reader(translated2);
        assertEquals(25, reader2.readInt());
        assertEquals(9, reader2.readVarInt());
        // light（756=3）→ block_marker（757=3、ブロック状態 0 を合成）。
        byte[] translated3 = ChunkTranslator.translateWorldParticles756To757(
                worldParticlesPacket(3, new byte[0]));
        Reader reader3 = new Reader(translated3);
        assertEquals(3, reader3.readInt());
        assertEquals(0, reader3.readVarInt());
        // barrier（756=2）→ block_marker（757=3、ブロック状態 0 を合成）。
        byte[] translated4 = ChunkTranslator.translateWorldParticles756To757(
                worldParticlesPacket(2, new byte[0]));
        Reader reader4 = new Reader(translated4);
        assertEquals(3, reader4.readInt());
        assertEquals(0, reader4.readVarInt());
        // dust（756=15、4 float）→ dust（757=14、4 float）。
        Writer floats = new Writer();
        floats.writeFloat(1.0f);
        floats.writeFloat(0.0f);
        floats.writeFloat(0.0f);
        floats.writeFloat(2.0f);
        byte[] translated5 = ChunkTranslator.translateWorldParticles756To757(
                worldParticlesPacket(15, floats.bytes()));
        Reader reader5 = new Reader(translated5);
        assertEquals(14, reader5.readInt());
        assertEquals(1.0f, reader5.readFloat());
        assertEquals(0.0f, reader5.readFloat());
        assertEquals(0.0f, reader5.readFloat());
        assertEquals(2.0f, reader5.readFloat());
        // vibration（757=36、block 行き先）→ vibration（756=37）。origin はそのまま。
        Writer vibration = new Writer();
        vibration.writePositionRaw((1L << 38) | (2L << 12) | 3L); // origin (1, 3, 2)
        vibration.writeString("minecraft:block");
        vibration.writePositionRaw((3L << 38) | (5L << 12) | 4L); // 行き先 (3, 4, 5)
        vibration.writeVarInt(9); // ticks
        byte[] translated6 = ChunkTranslator.translateWorldParticles757To756(
                worldParticlesPacket(36, vibration.bytes()));
        Reader reader6 = new Reader(translated6);
        assertEquals(37, reader6.readInt());
        assertEquals((1L << 38) | (2L << 12) | 3L, reader6.readPositionRaw());
        assertEquals("minecraft:block", reader6.readString());
        assertEquals((3L << 38) | (5L << 12) | 4L, reader6.readPositionRaw());
        assertEquals(9, reader6.readVarInt());
    }

    // ---- Block Entity Data ----

    @Test
    void tileEntityDataActionAndNbt() {
        // 756：action u8=9、NBT は id／x／y／z＋Text1。
        long position = (5L << 38) | (3L << 12) | 64L; // (x=5, y=64, z=3)
        Map<String, Tag> extra = new LinkedHashMap<>();
        extra.put("Text1", new Tag.StringTag("{\"text\":\"hi\"}"));
        Writer writer = new Writer();
        writer.writePositionRaw(position);
        writer.writeUnsignedByte(9);
        writer.writeNamedNbt("", blockEntityNbt756("minecraft:sign", 5, 64, 3, extra));
        byte[] translated = ChunkTranslator.translateTileEntityData756To757(writer.bytes());
        Reader reader = new Reader(translated);
        assertEquals(position, reader.readPositionRaw());
        assertEquals(9, reader.readVarInt()); // action は varint に
        Tag nbt = reader.readNamedNbt().tag();
        assertTrue(nbt instanceof Tag.CompoundTag);
        Tag.CompoundTag compound = (Tag.CompoundTag) nbt;
        assertTrue(compound.get("id").isEmpty());
        assertTrue(compound.get("x").isEmpty());
        assertEquals("{\"text\":\"hi\"}", compound.getString("Text1").orElseThrow());
        assertEquals(0, reader.remaining());
        // 757→756：action varint 9 → u8 9。NBT に x／y／z が付く（id は付けられない）。
        Writer writer2 = new Writer();
        writer2.writePositionRaw(position);
        writer2.writeVarInt(9);
        Map<String, Tag> data = new LinkedHashMap<>();
        data.put("Text1", new Tag.StringTag("{\"text\":\"hi\"}"));
        writer2.writeNamedNbt("", new Tag.CompoundTag(data));
        byte[] translated2 = ChunkTranslator.translateTileEntityData757To756(writer2.bytes());
        Reader reader2 = new Reader(translated2);
        assertEquals(position, reader2.readPositionRaw());
        assertEquals(9, reader2.readUnsignedByte());
        Tag nbt2 = reader2.readNamedNbt().tag();
        Tag.CompoundTag compound2 = (Tag.CompoundTag) nbt2;
        assertEquals(5, (int) ((Tag.IntTag) compound2.get("x").orElseThrow()).value());
        assertEquals(64, (int) ((Tag.IntTag) compound2.get("y").orElseThrow()).value());
        assertEquals(3, (int) ((Tag.IntTag) compound2.get("z").orElseThrow()).value());
        assertEquals("{\"text\":\"hi\"}", compound2.getString("Text1").orElseThrow());
        assertEquals(0, reader2.remaining());
        // action 300 は 756 の u8 に入らないため落とす。
        Writer writer3 = new Writer();
        writer3.writePositionRaw(position);
        writer3.writeVarInt(300);
        writer3.writeByte(0); // NBT 無し
        assertNull(ChunkTranslator.translateTileEntityData757To756(writer3.bytes()));
    }

    // ---- Trade List・ウィンドウ ----

    @Test
    void tradeListAndWindowItemsRemapOtherside() {
        Writer writer = new Writer();
        writer.writeVarInt(1); // windowId
        writer.writeUnsignedByte(1); // 1 trade
        writeSlot(writer, 1027, 1); // inputItem1：otherside
        writeSlot(writer, 1, 2); // outputItem：stone
        writer.writeBoolean(true); // inputItem2 あり
        writeSlot(writer, 1027, 3);
        writer.writeBoolean(false); // tradeDisabled
        writer.writeInt(0); // nbTradeUses
        writer.writeInt(10); // maximumNbTradeUses
        writer.writeInt(5); // xp
        writer.writeInt(0); // specialPrice
        writer.writeFloat(0.05f); // priceMultiplier
        writer.writeInt(0); // demand
        writer.writeVarInt(2); // villagerLevel
        writer.writeVarInt(10); // experience
        writer.writeBoolean(true); // isRegularVillager
        writer.writeBoolean(true); // canRestock
        byte[] translated = ChunkTranslator.translateTradeList757To756(writer.bytes());
        Reader reader = new Reader(translated);
        assertEquals(1, reader.readVarInt());
        assertEquals(1, reader.readUnsignedByte());
        assertArrayEquals(new int[]{1015, 1}, readSlotHead(reader));
        assertArrayEquals(new int[]{1, 2}, readSlotHead(reader));
        assertEquals(true, reader.readBoolean());
        assertArrayEquals(new int[]{1015, 3}, readSlotHead(reader));
        assertEquals(false, reader.readBoolean()); // tradeDisabled
        assertEquals(0, reader.readInt()); // nbTradeUses
        assertEquals(10, reader.readInt()); // maximumNbTradeUses
        assertEquals(5, reader.readInt()); // xp
        assertEquals(0, reader.readInt()); // specialPrice
        assertEquals(0.05f, reader.readFloat()); // priceMultiplier
        assertEquals(0, reader.readInt()); // demand
        assertEquals(2, reader.readVarInt()); // villagerLevel
        assertEquals(10, reader.readVarInt()); // experience
        assertEquals(true, reader.readBoolean()); // isRegularVillager
        assertEquals(true, reader.readBoolean()); // canRestock
        assertEquals(0, reader.remaining());
        // Window Items：carriedItem が otherside。
        Writer items = new Writer();
        items.writeUnsignedByte(0); // windowId
        items.writeVarInt(3); // stateId
        items.writeVarInt(1); // 1 item
        writeSlot(items, 1, 64);
        writeSlot(items, 1027, 1); // carriedItem
        byte[] translatedItems = ChunkTranslator.translateWindowItems757To756(items.bytes());
        Reader readerItems = new Reader(translatedItems);
        assertEquals(0, readerItems.readUnsignedByte());
        assertEquals(3, readerItems.readVarInt());
        assertEquals(1, readerItems.readVarInt());
        assertArrayEquals(new int[]{1, 64}, readSlotHead(readerItems));
        assertArrayEquals(new int[]{1015, 1}, readSlotHead(readerItems));
        assertEquals(0, readerItems.remaining());
    }

    @Test
    void setSlotAndWindowClickRemapOtherside() {
        // Set Slot（757 → 756）。
        Writer writer = new Writer();
        writer.writeByte(0); // windowId
        writer.writeVarInt(3); // stateId
        writer.writeShort(5); // slot
        writeSlot(writer, 1027, 1);
        byte[] translated = ChunkTranslator.translateSetSlot757To756(writer.bytes());
        Reader reader = new Reader(translated);
        assertEquals(0, reader.readByte());
        assertEquals(3, reader.readVarInt());
        assertEquals(5, reader.readShort());
        assertArrayEquals(new int[]{1015, 1}, readSlotHead(reader));
        assertEquals(0, reader.remaining());
        // Window Click（756 → 757）。変更スロットとカーソルが otherside。
        Writer click = new Writer();
        click.writeUnsignedByte(0); // windowId
        click.writeVarInt(3); // stateId
        click.writeShort(5); // slot
        click.writeByte(0); // mouseButton
        click.writeVarInt(0); // mode
        click.writeVarInt(1); // 1 changed slot
        click.writeShort(5);
        writeSlot(click, 1015, 1);
        writeSlot(click, 1015, 2); // cursorItem
        byte[] translatedClick = ChunkTranslator.translateWindowClick756To757(click.bytes());
        Reader readerClick = new Reader(translatedClick);
        assertEquals(0, readerClick.readUnsignedByte());
        assertEquals(3, readerClick.readVarInt());
        assertEquals(5, readerClick.readShort());
        assertEquals(0, readerClick.readByte());
        assertEquals(0, readerClick.readVarInt());
        assertEquals(1, readerClick.readVarInt());
        assertEquals(5, readerClick.readShort());
        assertArrayEquals(new int[]{1015, 1}, readSlotHead(readerClick));
        assertArrayEquals(new int[]{1015, 2}, readSlotHead(readerClick));
        assertEquals(0, readerClick.remaining());
    }
}
