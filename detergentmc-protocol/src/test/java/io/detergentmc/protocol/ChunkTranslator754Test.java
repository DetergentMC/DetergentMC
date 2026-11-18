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
 * {@link ChunkTranslator} の754（1.16.5）⇔755（1.17）向け変換テスト。
 * 754・755 ともデータコンポーネントは無い（NBT 時代）のため、スロットは
 * bool＋アイテムID＋個数＋名前付き NBT で、NBT は素通しする。
 * レジストリ差分は大きい（Stage A §11.1：ブロック状態 17,112⇔20,342件・
 * ブロック 763⇔898件・アイテム 976⇔1,100件・バイオーム 79⇔81件）ため、
 * スロットのアイテムID・チャンクのブロック状態ID・バイオームIDは
 * RegistryTables754 の表で付け替える。stateId は 754⇔755 には無い
 * （756 で追加されたもの）。
 * フィールド構成の差分は protocol.json の突き合わせ
 * （tools/compare-754-755-protocol.py）で洗い出し、javap と ViaVersion
 * でも裏付けた（ChunkTranslator.java の754節の javadoc を参照）。
 * Chunk Data の生キャプチャは実バニラ 1.16.5 サーバーから取ったもの
 * （チャンク -15,-7、10,633バイト。Java 8・オフラインモード・圧縮 -1・
 * view-distance 10・ポート 25571。Python ソケットの偽クライアント）。
 * 755 テストで使った cap756-chunk-21-17.bin は 1.17.1 のものなので
 * 754 には使えない（使っていない）。
 */
class ChunkTranslator754Test {

    /** スロット1個（754／755形式：bool＋アイテムID＋個数＋名前付き NBT 無し）。 */
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

    /** NBT 付きスロット1個（754／755形式）。 */
    private static void writeSlotWithNbt(Writer writer, int itemId, int count, Tag nbt) {
        writer.writeBoolean(true);
        writer.writeVarInt(itemId);
        writer.writeByte(count);
        writer.writeNamedNbt("", nbt);
    }

    /** 754形式の Set Slot ペイロード（stateId 無し）。 */
    private static byte[] setSlotPayload754(int windowId, int slot, int itemId, int count) {
        Writer writer = new Writer();
        writer.writeByte(windowId);
        writer.writeShort(slot);
        writeSlot(writer, itemId, count);
        return writer.bytes();
    }

    /** 755形式の Set Slot ペイロード（stateId 無し。754 と同じ形）。 */
    private static byte[] setSlotPayload755(int windowId, int slot, int itemId, int count) {
        return setSlotPayload754(windowId, slot, itemId, count);
    }

    /** 754／755形式の Window Items ペイロード（count は i16。両版で同じ形）。 */
    private static byte[] windowItemsPayload(int windowId, int[][] slots) {
        Writer writer = new Writer();
        writer.writeUnsignedByte(windowId);
        writer.writeShort(slots.length);
        for (int[] slot : slots) {
            writeSlot(writer, slot[0], slot[1]);
        }
        return writer.bytes();
    }

    /** 754形式の Window Click ペイロード（action 付き・単一スロット）。 */
    private static byte[] windowClickPayload754(int windowId, int slot, int button,
            int action, int mode, int[] item) {
        Writer writer = new Writer();
        writer.writeUnsignedByte(windowId);
        writer.writeShort(slot);
        writer.writeByte(button);
        writer.writeShort(action);
        writer.writeVarInt(mode);
        writeSlot(writer, item[0], item[1]);
        return writer.bytes();
    }

    /** 755形式の Window Click ペイロード（changedSlots＋cursor）。 */
    private static byte[] windowClickPayload755(int windowId, int slot, int button,
            int mode, int[][] changed, int[] cursor) {
        Writer writer = new Writer();
        writer.writeUnsignedByte(windowId);
        writer.writeShort(slot);
        writer.writeByte(button);
        writer.writeVarInt(mode);
        writer.writeVarInt(changed.length);
        for (int[] entry : changed) {
            writer.writeShort(entry[0]);
            writeSlot(writer, entry[1], entry[2]);
        }
        writeSlot(writer, cursor[0], cursor[1]);
        return writer.bytes();
    }

    /** 空の heightmaps NBT（名前付きコンパウンド）。 */
    private static Tag emptyHeightmaps() {
        return new Tag.CompoundTag(new LinkedHashMap<>());
    }

    /** ブロックエンティティ NBT 1件（id／x／y／z＋data）。 */
    private static Tag blockEntityNbt(String id, int x, int y, int z) {
        Map<String, Tag> entries = new LinkedHashMap<>();
        entries.put("id", new Tag.StringTag(id));
        entries.put("x", new Tag.IntTag(x));
        entries.put("y", new Tag.IntTag(y));
        entries.put("z", new Tag.IntTag(z));
        entries.put("data", new Tag.StringTag("test"));
        return new Tag.CompoundTag(entries);
    }

    /** パレット付きセクション1個（BPE 0〜8。Data Array は全て添字 0）。 */
    private static void writePaletteSection(Writer writer, int blockCount, int bpe,
            int[] palette) {
        writer.writeShort(blockCount);
        writer.writeUnsignedByte(bpe);
        if (bpe == 0) {
            writer.writeVarInt(palette[0]);
            writer.writeVarInt(0);
            return;
        }
        writer.writeVarInt(palette.length);
        for (int state : palette) {
            writer.writeVarInt(state);
        }
        int perLong = 64 / bpe;
        int dataLength = (4096 + perLong - 1) / perLong;
        writer.writeVarInt(dataLength);
        for (int i = 0; i < dataLength; i++) {
            writer.writeLong(0L);
        }
    }

    /** 直接パレットのセクション1個（BPE 9〜15。エントリ列を non-straddling で詰める）。 */
    private static void writeDirectSection(Writer writer, int blockCount, int bpe,
            int[] entries) {
        writer.writeShort(blockCount);
        writer.writeUnsignedByte(bpe);
        int perLong = 64 / bpe;
        long mask = (1L << bpe) - 1;
        long[] longs = new long[(entries.length + perLong - 1) / perLong];
        for (int i = 0; i < entries.length; i++) {
            longs[i / perLong] |= (entries[i] & mask) << ((i % perLong) * bpe);
        }
        writer.writeVarInt(longs.length);
        for (long value : longs) {
            writer.writeLong(value);
        }
    }

    /** 754形式の Chunk Data ペイロードを組み立てる。 */
    private static byte[] chunkPayload754(int x, int z, boolean groundUp, int bitMap,
            byte[] sectionsBytes, int[] biomes, List<Tag> blockEntities) {
        Writer writer = new Writer();
        writer.writeInt(x);
        writer.writeInt(z);
        writer.writeBoolean(groundUp);
        writer.writeVarInt(bitMap);
        writer.writeNamedNbt("", emptyHeightmaps());
        if (groundUp) {
            writer.writeVarInt(biomes.length);
            for (int biome : biomes) {
                writer.writeVarInt(biome);
            }
        }
        writer.writeByteArray(sectionsBytes);
        writer.writeVarInt(blockEntities.size());
        for (Tag tag : blockEntities) {
            writer.writeNamedNbt("", tag);
        }
        return writer.bytes();
    }

    /** 755形式の Chunk Data ペイロードを組み立てる。 */
    private static byte[] chunkPayload755(int x, int z, long[] bitMapLongs,
            byte[] sectionsBytes, int[] biomes, List<Tag> blockEntities) {
        Writer writer = new Writer();
        writer.writeInt(x);
        writer.writeInt(z);
        writer.writeVarInt(bitMapLongs.length);
        for (long value : bitMapLongs) {
            writer.writeLong(value);
        }
        writer.writeNamedNbt("", emptyHeightmaps());
        writer.writeVarInt(biomes.length);
        for (int biome : biomes) {
            writer.writeVarInt(biome);
        }
        writer.writeByteArray(sectionsBytes);
        writer.writeVarInt(blockEntities.size());
        for (Tag tag : blockEntities) {
            writer.writeNamedNbt("", tag);
        }
        return writer.bytes();
    }

    /** 1024件のバイオーム配列（全て同じ値）。 */
    private static int[] filledBiomes(int value) {
        int[] biomes = new int[1024];
        for (int i = 0; i < biomes.length; i++) {
            biomes[i] = value;
        }
        return biomes;
    }

    /** 755形式の Chunk Data を読み、(bitMap longs, biomes, セクション生バイト列) を返す。 */
    private static Object[] parseChunk755(byte[] payload) {
        Reader reader = new Reader(payload);
        assertEquals(-15, reader.readInt());
        assertEquals(-7, reader.readInt());
        int count = reader.readVarInt();
        long[] bitMap = new long[count];
        for (int i = 0; i < count; i++) {
            bitMap[i] = reader.readLong();
        }
        assertTrue(reader.readNamedNbt().tag() instanceof Tag.CompoundTag);
        int biomeCount = reader.readVarInt();
        assertEquals(1024, biomeCount);
        int[] biomes = new int[1024];
        for (int i = 0; i < 1024; i++) {
            biomes[i] = reader.readVarInt();
        }
        byte[] sections = reader.readByteArray();
        int beCount = reader.readVarInt();
        List<String> beIds = new ArrayList<>();
        for (int i = 0; i < beCount; i++) {
            Tag tag = reader.readNamedNbt().tag();
            assertTrue(tag instanceof Tag.CompoundTag);
            beIds.add(((Tag.CompoundTag) tag).getString("id").orElse(null));
        }
        assertEquals(0, reader.remaining());
        return new Object[]{bitMap, biomes, sections, beIds};
    }

    /** セクション列から最初のセクションのパレットを読み、(BPE, パレット, Data Array 長) を返す。 */
    private static Object[] readFirstSectionPalette(byte[] sectionsBytes) {
        Reader reader = new Reader(sectionsBytes);
        assertEquals(4096, reader.readShort());
        int bpe = reader.readUnsignedByte();
        int[] palette;
        if (bpe == 0) {
            palette = new int[]{reader.readVarInt()};
            assertEquals(0, reader.readVarInt());
            return new Object[]{bpe, palette, 0};
        }
        int paletteLength = reader.readVarInt();
        palette = new int[paletteLength];
        for (int i = 0; i < paletteLength; i++) {
            palette[i] = reader.readVarInt();
        }
        int dataLength = reader.readVarInt();
        return new Object[]{bpe, palette, dataLength};
    }

    // ---- Chunk Data（生キャプチャ） ----

    @Test
    void realCapturedChunk754To755AndBack() throws Exception {
        // 実バニラ 1.16.5 サーバーからの生キャプチャ（チャンク -15,-7、
        // 10,633バイト。groundUp=true、bitMap 0x000f、biomes 1024件全て 50、
        // セクション y=0 は BPE 5・パレット 17・Data Array 長 342
        // ＝non-straddling、ブロックエンティティ 0件）。
        byte[] captured;
        try (InputStream in = getClass().getResourceAsStream(
                "/cap754-chunk-x-15-z-7.bin")) {
            assertTrue(in != null, "テストリソースが見つかりません");
            captured = in.readAllBytes();
        }
        assertEquals(10633, captured.length);
        byte[] up = ChunkTranslator.translateChunkData754To755(captured);
        Object[] parsed = parseChunk755(up);
        long[] bitMap = (long[]) parsed[0];
        int[] biomes = (int[]) parsed[1];
        byte[] sections = (byte[]) parsed[2];
        assertArrayEquals(new long[]{0xFL}, bitMap);
        for (int biome : biomes) {
            assertEquals(50, biome); // deep_frozen_ocean（両版とも id 50）
        }
        assertEquals(List.of(), parsed[3]); // ブロックエンティティ 0件
        // 最初のセクションのパレットが付け替わっていること。
        Object[] first = readFirstSectionPalette(sections);
        assertEquals(5, first[0]); // BPE
        assertEquals(342, first[2]); // Data Array 長（non-straddling）
        int[] paletteUp = (int[]) first[1];
        assertEquals(17, paletteUp.length);
        // 元の 754 パレットと突き合わせる。
        Reader origSections = new Reader(captured);
        origSections.readInt();
        origSections.readInt();
        assertTrue(origSections.readBoolean()); // groundUp
        assertEquals(0xF, origSections.readVarInt()); // bitMap
        assertTrue(origSections.readNamedNbt().tag() instanceof Tag.CompoundTag);
        assertEquals(1024, origSections.readVarInt());
        for (int i = 0; i < 1024; i++) {
            origSections.readVarInt();
        }
        byte[] origChunkData = origSections.readByteArray();
        Object[] origFirst = readFirstSectionPalette(origChunkData);
        int[] paletteOrig = (int[]) origFirst[1];
        assertEquals(17, paletteOrig.length);
        for (int i = 0; i < paletteOrig.length; i++) {
            assertEquals(RegistryRemapper.mapBlockState754To755(paletteOrig[i]), paletteUp[i]);
        }
        // 往復で元のバイトに完全一致する。
        assertArrayEquals(captured, ChunkTranslator.translateChunkData755To754(up));
    }

    // ---- Chunk Data（合成ペイロード） ----

    @Test
    void chunkDataGroundUpFalseSynthesizesPlains() {
        Writer sections = new Writer();
        writePaletteSection(sections, 4096, 4, new int[]{0, 1});
        byte[] original = chunkPayload754(3, -2, false, 0x1, sections.bytes(),
                null, List.of());
        byte[] up = ChunkTranslator.translateChunkData754To755(original);
        Reader reader = new Reader(up);
        assertEquals(3, reader.readInt());
        assertEquals(-2, reader.readInt());
        assertEquals(1, reader.readVarInt()); // bitMap long 数
        assertEquals(0x1L, reader.readLong()); // bitMap
        assertTrue(reader.readNamedNbt().tag() instanceof Tag.CompoundTag);
        assertEquals(1024, reader.readVarInt());
        for (int i = 0; i < 1024; i++) {
            assertEquals(1, reader.readVarInt()); // 平原で補完
        }
        byte[] upSections = reader.readByteArray();
        Reader upSectionReader = new Reader(upSections);
        assertEquals(4096, upSectionReader.readShort());
        assertEquals(4, upSectionReader.readUnsignedByte()); // BPE
        assertEquals(2, upSectionReader.readVarInt()); // パレット長
        assertEquals(RegistryRemapper.mapBlockState754To755(0), upSectionReader.readVarInt());
        assertEquals(RegistryRemapper.mapBlockState754To755(1), upSectionReader.readVarInt());
        assertEquals(0, reader.readVarInt()); // ブロックエンティティ 0件
        assertEquals(0, reader.remaining());
        // 逆向きは groundUp=true になる（755 のチャンクは常に全チャンク）。
        // biomes は平原（754 でも 1）のまま、セクションは保たれる。
        byte[] back = ChunkTranslator.translateChunkData755To754(up);
        Reader backReader = new Reader(back);
        assertEquals(3, backReader.readInt());
        assertEquals(-2, backReader.readInt());
        assertTrue(backReader.readBoolean()); // groundUp=true
        assertEquals(0x1, backReader.readVarInt()); // bitMap
        assertTrue(backReader.readNamedNbt().tag() instanceof Tag.CompoundTag);
        assertEquals(1024, backReader.readVarInt());
        for (int i = 0; i < 1024; i++) {
            assertEquals(1, backReader.readVarInt());
        }
        byte[] backSections = backReader.readByteArray();
        Reader backSectionReader = new Reader(backSections);
        assertEquals(4096, backSectionReader.readShort());
        assertEquals(4, backSectionReader.readUnsignedByte()); // BPE
        assertEquals(2, backSectionReader.readVarInt()); // パレット長
        assertEquals(0, backSectionReader.readVarInt());
        assertEquals(1, backSectionReader.readVarInt());
        assertEquals(0, backReader.readVarInt()); // ブロックエンティティ 0件
        assertEquals(0, backReader.remaining());
    }

    @Test
    void chunkDataDirectPaletteRemaps() {
        // 直接パレット（BPE 15）のセクション。エントリはブロック状態IDそのもの。
        int[] entries = new int[4096];
        entries[0] = 0; // air
        entries[1] = 1; // stone
        entries[2] = 2;
        Writer sections = new Writer();
        writeDirectSection(sections, 3, 15, entries);
        byte[] original = chunkPayload754(0, 0, true, 0x1, sections.bytes(),
                filledBiomes(50), List.of());
        byte[] up = ChunkTranslator.translateChunkData754To755(original);
        // 755 形式のセクションをほぐして確認する。
        Reader reader = new Reader(up);
        reader.readInt();
        reader.readInt();
        assertEquals(1, reader.readVarInt());
        assertEquals(0x1L, reader.readLong());
        assertTrue(reader.readNamedNbt().tag() instanceof Tag.CompoundTag);
        assertEquals(1024, reader.readVarInt());
        for (int i = 0; i < 1024; i++) {
            reader.readVarInt();
        }
        byte[] upSections = reader.readByteArray();
        Reader s = new Reader(upSections);
        assertEquals(3, s.readShort());
        assertEquals(15, s.readUnsignedByte());
        int dataLength = s.readVarInt();
        assertEquals((4096 + 3) / 4, dataLength); // BPE 15 は 1 long に 4 エントリ
        long[] longs = new long[dataLength];
        for (int i = 0; i < dataLength; i++) {
            longs[i] = s.readLong();
        }
        int perLong = 64 / 15;
        long mask = (1L << 15) - 1;
        for (int i = 0; i < 3; i++) {
            int value = (int) ((longs[i / perLong] >>> ((i % perLong) * 15)) & mask);
            assertEquals(RegistryRemapper.mapBlockState754To755(entries[i]), value);
        }
        // 往復で元のバイトに完全一致する。
        assertArrayEquals(original, ChunkTranslator.translateChunkData755To754(up));
    }

    @Test
    void chunkDataSinglePaletteRemaps() {
        Writer sections = new Writer();
        writePaletteSection(sections, 4096, 0, new int[]{1}); // stone だけ
        byte[] original = chunkPayload754(0, 0, true, 0x1, sections.bytes(),
                filledBiomes(50), List.of());
        byte[] up = ChunkTranslator.translateChunkData754To755(original);
        Reader reader = new Reader(up);
        reader.readInt();
        reader.readInt();
        assertEquals(1, reader.readVarInt());
        assertEquals(0x1L, reader.readLong());
        assertTrue(reader.readNamedNbt().tag() instanceof Tag.CompoundTag);
        assertEquals(1024, reader.readVarInt());
        for (int i = 0; i < 1024; i++) {
            reader.readVarInt();
        }
        byte[] upSections = reader.readByteArray();
        Reader s = new Reader(upSections);
        assertEquals(4096, s.readShort());
        assertEquals(0, s.readUnsignedByte());
        assertEquals(RegistryRemapper.mapBlockState754To755(1), s.readVarInt());
        assertEquals(0, s.readVarInt()); // Data Array 長 0
        assertArrayEquals(original, ChunkTranslator.translateChunkData755To754(up));
    }

    @Test
    void chunkDataSculkSensorBlockEntityDropped() {
        // 755 側のチャンクに furnace と sculk_sensor のブロックエンティティを入れる。
        List<Tag> entities755 = List.of(
                blockEntityNbt("minecraft:furnace", 1, 64, 2),
                blockEntityNbt("minecraft:sculk_sensor", 3, 64, 4));
        Writer sections = new Writer();
        writePaletteSection(sections, 4096, 4, new int[]{0, 1});
        byte[] original755 = chunkPayload755(-15, -7, new long[]{0x1L}, sections.bytes(),
                filledBiomes(50), entities755);
        byte[] down = ChunkTranslator.translateChunkData755To754(original755);
        Reader reader = new Reader(down);
        assertEquals(-15, reader.readInt());
        assertEquals(-7, reader.readInt());
        assertTrue(reader.readBoolean()); // groundUp
        assertEquals(0x1, reader.readVarInt());
        assertTrue(reader.readNamedNbt().tag() instanceof Tag.CompoundTag);
        assertEquals(1024, reader.readVarInt());
        for (int i = 0; i < 1024; i++) {
            reader.readVarInt();
        }
        reader.readByteArray(); // セクション
        assertEquals(1, reader.readVarInt()); // sculk_sensor だけ落とされる
        Tag tag = reader.readNamedNbt().tag();
        assertTrue(tag instanceof Tag.CompoundTag);
        assertEquals("minecraft:furnace",
                ((Tag.CompoundTag) tag).getString("id").orElse(null));
        assertEquals(0, reader.remaining());
        // 754→755 はブロックエンティティを全て通す。
        byte[] back = ChunkTranslator.translateChunkData754To755(down);
        Reader backReader = new Reader(back);
        backReader.readInt();
        backReader.readInt();
        assertEquals(1, backReader.readVarInt());
        assertEquals(0x1L, backReader.readLong());
        assertTrue(backReader.readNamedNbt().tag() instanceof Tag.CompoundTag);
        assertEquals(1024, backReader.readVarInt());
        for (int i = 0; i < 1024; i++) {
            backReader.readVarInt();
        }
        backReader.readByteArray();
        assertEquals(1, backReader.readVarInt());
        Tag backTag = backReader.readNamedNbt().tag();
        assertTrue(backTag instanceof Tag.CompoundTag);
        assertEquals("minecraft:furnace",
                ((Tag.CompoundTag) backTag).getString("id").orElse(null));
        assertEquals(0, backReader.remaining());
    }

    // ---- スロット ----

    @Test
    void slotRoundTrip754To755To754() {
        Writer writer = new Writer();
        writeSlot(writer, 1, 64); // stone（754=1・755=1）
        byte[] original = writer.bytes();
        Reader reader = new Reader(original);
        Writer mid = new Writer();
        ChunkTranslator.translateSlot754To755(reader, mid);
        assertEquals(0, reader.remaining());
        Reader reader2 = new Reader(mid.bytes());
        Writer back = new Writer();
        ChunkTranslator.translateSlot755To754(reader2, back);
        assertEquals(0, reader2.remaining());
        assertArrayEquals(original, back.bytes());
    }

    @Test
    void slotGrassPathToDirtPath() {
        // 754 の grass_path（372）は 755 では dirt_path（393）に改名。
        Writer writer = new Writer();
        writeSlot(writer, 372, 1);
        byte[] original = writer.bytes();
        Reader reader = new Reader(original);
        Writer mid = new Writer();
        ChunkTranslator.translateSlot754To755(reader, mid);
        assertArrayEquals(new int[]{393, 1}, readSlotHead(new Reader(mid.bytes())));
        Reader reader2 = new Reader(mid.bytes());
        Writer back = new Writer();
        ChunkTranslator.translateSlot755To754(reader2, back);
        assertArrayEquals(original, back.bytes());
    }

    @Test
    void slotAbsentRoundTrip() {
        Writer writer = new Writer();
        writer.writeBoolean(false);
        byte[] original = writer.bytes();
        Reader reader = new Reader(original);
        Writer mid = new Writer();
        ChunkTranslator.translateSlot754To755(reader, mid);
        assertArrayEquals(original, mid.bytes());
        Reader reader2 = new Reader(mid.bytes());
        Writer back = new Writer();
        ChunkTranslator.translateSlot755To754(reader2, back);
        assertArrayEquals(original, back.bytes());
    }

    @Test
    void slotWithNbtRoundTrip() {
        Map<String, Tag> entries = new LinkedHashMap<>();
        entries.put("id", new Tag.StringTag("minecraft:diamond_sword"));
        entries.put("Count", new Tag.ByteTag((byte) 1));
        Tag nbt = new Tag.CompoundTag(entries);
        Writer writer = new Writer();
        writeSlotWithNbt(writer, 100, 1, nbt);
        byte[] original = writer.bytes();
        Reader reader = new Reader(original);
        Writer mid = new Writer();
        ChunkTranslator.translateSlot754To755(reader, mid);
        assertEquals(0, reader.remaining());
        Reader reader2 = new Reader(mid.bytes());
        Writer back = new Writer();
        ChunkTranslator.translateSlot755To754(reader2, back);
        assertEquals(0, reader2.remaining());
        assertArrayEquals(original, back.bytes());
    }

    // ---- Set Slot ----

    @Test
    void setSlot754To755RemapsItem() {
        byte[] original = setSlotPayload754(0, 5, 372, 1); // grass_path
        byte[] up = ChunkTranslator.translateSetSlot754To755(original);
        Reader reader = new Reader(up);
        assertEquals(0, reader.readByte()); // windowId
        assertEquals(5, reader.readShort()); // slot（stateId は無い）
        assertArrayEquals(new int[]{393, 1}, readSlotHead(reader)); // dirt_path
        assertEquals(0, reader.remaining());
        assertArrayEquals(original, ChunkTranslator.translateSetSlot755To754(up));
    }

    @Test
    void setSlot755To754RemapsItem() {
        byte[] original = setSlotPayload755(1, 9, 393, 3); // dirt_path
        byte[] down = ChunkTranslator.translateSetSlot755To754(original);
        Reader reader = new Reader(down);
        assertEquals(1, reader.readByte()); // windowId
        assertEquals(9, reader.readShort()); // slot
        assertArrayEquals(new int[]{372, 3}, readSlotHead(reader)); // grass_path
        assertEquals(0, reader.remaining());
        assertArrayEquals(original, ChunkTranslator.translateSetSlot754To755(down));
    }

    // ---- Window Items ----

    @Test
    void windowItemsRemapsItemsBothWays() {
        byte[] original = windowItemsPayload(0, new int[][]{{372, 1}, {1, 64}});
        byte[] up = ChunkTranslator.translateWindowItems754To755(original);
        Reader reader = new Reader(up);
        assertEquals(0, reader.readUnsignedByte()); // windowId
        assertEquals(2, reader.readShort()); // count（i16 のまま）
        assertArrayEquals(new int[]{393, 1}, readSlotHead(reader));
        assertArrayEquals(new int[]{1, 64}, readSlotHead(reader));
        assertEquals(0, reader.remaining());
        assertArrayEquals(original, ChunkTranslator.translateWindowItems755To754(up));
    }

    // ---- Window Click ----

    @Test
    void windowClick754To755DropsActionAndCursor() {
        // 754形式：windowId・slot・button・action・mode・単一スロット。
        byte[] click754 = windowClickPayload754(0, 5, 0, 7, 1, new int[]{372, 1});
        byte[] up = ChunkTranslator.translateWindowClick754To755(click754);
        Reader reader = new Reader(up);
        assertEquals(0, reader.readUnsignedByte()); // windowId
        assertEquals(5, reader.readShort()); // slot
        assertEquals(0, reader.readByte()); // button
        assertEquals(1, reader.readVarInt()); // mode（varint のまま）
        assertEquals(0, reader.readVarInt()); // changedSlots 0件
        assertArrayEquals(new int[]{393, 1}, readSlotHead(reader)); // cursorItem
        assertEquals(0, reader.remaining());
        // 往復では action は 0 に正規化される（元の 7 は失われる）。
        byte[] back = ChunkTranslator.translateWindowClick755To754(up);
        assertArrayEquals(windowClickPayload754(0, 5, 0, 0, 1, new int[]{372, 1}), back);
    }

    @Test
    void windowClick755To754DropsChangedSlots() {
        // 755形式：changedSlots 1件・cursor 有り。
        byte[] click755 = windowClickPayload755(1, 3, 1, 2,
                new int[][]{{3, 393, 1}}, new int[]{1, 64});
        byte[] down = ChunkTranslator.translateWindowClick755To754(click755);
        Reader reader = new Reader(down);
        assertEquals(1, reader.readUnsignedByte()); // windowId
        assertEquals(3, reader.readShort()); // slot
        assertEquals(1, reader.readByte()); // button
        assertEquals(0, reader.readShort()); // action（uid は 0 を補う）
        assertEquals(2, reader.readVarInt()); // mode（varint のまま）
        assertArrayEquals(new int[]{1, 64}, readSlotHead(reader)); // 単一スロット
        assertEquals(0, reader.remaining());
        // 往復では changedSlots は空になる。
        byte[] back = ChunkTranslator.translateWindowClick754To755(down);
        assertArrayEquals(windowClickPayload755(1, 3, 1, 2,
                new int[][]{}, new int[]{1, 64}), back);
    }

    // ---- Entity Destroy ----

    @Test
    void entityDestroy755To754WrapsInArray() {
        Writer writer = new Writer();
        writer.writeVarInt(42);
        byte[] down = ChunkTranslator.translateEntityDestroy755To754(writer.bytes());
        Reader reader = new Reader(down);
        assertEquals(1, reader.readVarInt()); // count
        assertEquals(42, reader.readVarInt()); // entityId
        assertEquals(0, reader.remaining());
        assertArrayEquals(writer.bytes(), ChunkTranslator.translateEntityDestroy754To755(down));
    }

    @Test
    void entityDestroy754To755DropsMultipleAndEmpty() {
        // count 2以上は755の1パケットでは表せないため落とす（null）。
        Writer multi = new Writer();
        multi.writeVarInt(2);
        multi.writeVarInt(7);
        multi.writeVarInt(8);
        assertNull(ChunkTranslator.translateEntityDestroy754To755(multi.bytes()));
        // count 0 は消すものが無いので落として正しい（null）。
        Writer empty = new Writer();
        empty.writeVarInt(0);
        assertNull(ChunkTranslator.translateEntityDestroy754To755(empty.bytes()));
        // count 1 は単体に直る。
        Writer single = new Writer();
        single.writeVarInt(1);
        single.writeVarInt(99);
        Writer expected = new Writer();
        expected.writeVarInt(99);
        assertArrayEquals(expected.bytes(),
                ChunkTranslator.translateEntityDestroy754To755(single.bytes()));
    }
}
