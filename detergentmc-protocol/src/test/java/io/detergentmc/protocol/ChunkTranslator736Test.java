package io.detergentmc.protocol;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
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
 * {@link ChunkTranslator} の736（1.16.1）⇔751（1.16.2）向け変換テスト。
 * 736・751 ともデータコンポーネントは無い（NBT 時代）のため、スロットは
 * bool＋アイテムID＋個数＋名前付き NBT で、NBT は素通しする。
 * レジストリ差分は大きい（Stage A §11：ブロック状態 12,381件・アイテム
 * 249件・エンティティ 47件・音 405件が非恒等。piglin_brute 系の削除あり。
 * バイオーム 79件は両版とも同じ id・同じ名前のため恒等写像）のため、
 * スロットのアイテムID・チャンクのブロック状態IDは RegistryTables736 の
 * 表で付け替える。stateId は 736⇔751 には無い（756 で追加されたもの）。
 * フィールド構成の差分は protocol.json の突き合わせで洗い出し、javap と
 * ViaVersion でも裏付けた（ChunkTranslator.java の736節の javadoc を参照）。
 * Chunk Data の生キャプチャは実バニラ 1.16.1 サーバーから取ったもの
 * （チャンク -17,-23、15,083バイト。Java 8・オフラインモード・圧縮 -1・
 * view-distance 10・ポート 25569。Python ソケットの偽クライアント）。
 * 754 テストで使った cap754-chunk-x-15-z-7.bin は 1.16.5 のものなので
 * 736 には使えない（使っていない）。
 */
class ChunkTranslator736Test {

    /** スロット1個（736／751形式：bool＋アイテムID＋個数＋名前付き NBT 無し）。 */
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

    /** NBT 付きスロット1個（736／751形式）。 */
    private static void writeSlotWithNbt(Writer writer, int itemId, int count, Tag nbt) {
        writer.writeBoolean(true);
        writer.writeVarInt(itemId);
        writer.writeByte(count);
        writer.writeNamedNbt("", nbt);
    }

    /** 736形式の Set Slot ペイロード（stateId 無し）。 */
    private static byte[] setSlotPayload736(int windowId, int slot, int itemId, int count) {
        Writer writer = new Writer();
        writer.writeByte(windowId);
        writer.writeShort(slot);
        writeSlot(writer, itemId, count);
        return writer.bytes();
    }

    /** 751形式の Set Slot ペイロード（stateId 無し。736 と同じ形）。 */
    private static byte[] setSlotPayload751(int windowId, int slot, int itemId, int count) {
        return setSlotPayload736(windowId, slot, itemId, count);
    }

    /** 736／751形式の Window Items ペイロード（count は i16。両版で同じ形）。 */
    private static byte[] windowItemsPayload(int windowId, int[][] slots) {
        Writer writer = new Writer();
        writer.writeUnsignedByte(windowId);
        writer.writeShort(slots.length);
        for (int[] slot : slots) {
            writeSlot(writer, slot[0], slot[1]);
        }
        return writer.bytes();
    }

    /** 736形式の Window Click ペイロード（action 付き・単一スロット）。 */
    private static byte[] windowClickPayload736(int windowId, int slot, int button,
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

    /** 751形式の Window Click ペイロード（736 と同じ形。action 付き・単一スロット）。 */
    private static byte[] windowClickPayload751(int windowId, int slot, int button,
            int action, int mode, int[] item) {
        return windowClickPayload736(windowId, slot, button, action, mode, item);
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

    /** 736形式の Chunk Data ペイロードを組み立てる（biomes は i32×1024・件数接頭辞なし）。 */
    private static byte[] chunkPayload736(int x, int z, boolean groundUp, int bitMap,
            byte[] sectionsBytes, int[] biomes, List<Tag> blockEntities) {
        Writer writer = new Writer();
        writer.writeInt(x);
        writer.writeInt(z);
        writer.writeBoolean(groundUp);
        writer.writeBoolean(groundUp); // ignoreOldData（全チャンクは true。部分更新は false）
        writer.writeVarInt(bitMap);
        writer.writeNamedNbt("", emptyHeightmaps());
        if (groundUp) {
            for (int biome : biomes) {
                writer.writeInt(biome);
            }
        }
        writer.writeByteArray(sectionsBytes);
        writer.writeVarInt(blockEntities.size());
        for (Tag tag : blockEntities) {
            writer.writeNamedNbt("", tag);
        }
        return writer.bytes();
    }

    /** 751形式の Chunk Data ペイロードを組み立てる（biomes は [count varint][varint×1024]）。 */
    private static byte[] chunkPayload751(int x, int z, boolean groundUp, int bitMap,
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

    /** 1024件のバイオーム配列（全て同じ値）。 */
    private static int[] filledBiomes(int value) {
        int[] biomes = new int[1024];
        for (int i = 0; i < biomes.length; i++) {
            biomes[i] = value;
        }
        return biomes;
    }

    /** 751形式の Chunk Data を読み、(bitMap, biomes, セクション生バイト列, ブロックエンティティ id 列) を返す。 */
    private static Object[] parseChunk751(byte[] payload) {
        Reader reader = new Reader(payload);
        assertEquals(-17, reader.readInt());
        assertEquals(-23, reader.readInt());
        assertTrue(reader.readBoolean()); // groundUp
        int bitMap = reader.readVarInt();
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

    /** セクション列から最初のセクションを読み、(blockCount, BPE, パレット, Data Array 長) を返す。 */
    private static Object[] readFirstSectionPalette(byte[] sectionsBytes) {
        Reader reader = new Reader(sectionsBytes);
        int blockCount = reader.readShort();
        int bpe = reader.readUnsignedByte();
        int[] palette;
        if (bpe == 0) {
            palette = new int[]{reader.readVarInt()};
            assertEquals(0, reader.readVarInt());
            return new Object[]{blockCount, bpe, palette, 0};
        }
        int paletteLength = reader.readVarInt();
        palette = new int[paletteLength];
        for (int i = 0; i < paletteLength; i++) {
            palette[i] = reader.readVarInt();
        }
        int dataLength = reader.readVarInt();
        return new Object[]{blockCount, bpe, palette, dataLength};
    }

    // ---- Chunk Data（生キャプチャ） ----

    @Test
    void realCapturedChunk736To751AndBack() throws Exception {
        // 実バニラ 1.16.1 サーバーからの生キャプチャ（チャンク -17,-23、
        // 15,083バイト。groundUp=true、ignoreOldData=true、bitMap 0x001f、
        // biomes 1024件全て 1（平原。i32）、セクション y=0〜4 は BPE 4・
        // Data Array 長 256＝non-straddling、ブロックエンティティ 0件）。
        byte[] captured;
        try (InputStream in = getClass().getResourceAsStream(
                "/cap736-chunk-x--17-z--23.bin")) {
            assertTrue(in != null, "テストリソースが見つかりません");
            captured = in.readAllBytes();
        }
        assertEquals(15083, captured.length);
        byte[] up = ChunkTranslator.translateChunkData736To751(captured);
        Object[] parsed = parseChunk751(up);
        assertEquals(0x1F, parsed[0]); // bitMap
        int[] biomes = (int[]) parsed[1];
        for (int biome : biomes) {
            assertEquals(1, biome); // 平原（両版とも id 1。恒等写像）
        }
        byte[] sections = (byte[]) parsed[2];
        assertEquals(List.of(), parsed[3]); // ブロックエンティティ 0件
        // 最初のセクションのパレットが付け替わっていること。
        Object[] first = readFirstSectionPalette(sections);
        assertEquals(3949, first[0]); // blockCount（生キャプチャの実測値）
        assertEquals(4, first[1]); // BPE
        assertEquals(256, first[3]); // Data Array 長（non-straddling）
        int[] paletteUp = (int[]) first[2];
        assertEquals(14, paletteUp.length);
        // 元の 736 パレットと突き合わせる。
        Reader origSections = new Reader(captured);
        origSections.readInt();
        origSections.readInt();
        assertTrue(origSections.readBoolean()); // groundUp
        assertTrue(origSections.readBoolean()); // ignoreOldData
        assertEquals(0x1F, origSections.readVarInt()); // bitMap
        assertTrue(origSections.readNamedNbt().tag() instanceof Tag.CompoundTag);
        for (int i = 0; i < 1024; i++) {
            assertEquals(1, origSections.readInt()); // biomes は i32
        }
        byte[] origChunkData = origSections.readByteArray();
        Object[] origFirst = readFirstSectionPalette(origChunkData);
        int[] paletteOrig = (int[]) origFirst[2];
        assertEquals(14, paletteOrig.length);
        for (int i = 0; i < paletteOrig.length; i++) {
            assertEquals(RegistryRemapper.mapBlockState736To751(paletteOrig[i]), paletteUp[i]);
        }
        // 往復で元のバイトに完全一致する。
        assertArrayEquals(captured, ChunkTranslator.translateChunkData751To736(up));
    }

    // ---- Chunk Data（合成ペイロード） ----

    @Test
    void chunkDataGroundUpFalseHasNoBiomes() {
        // 751 の biomes は groundUp のときだけ。groundUp=false の 736 チャンクを
        // 751 にするとき、biomes は書かない（平原補完はしない。754→755 の前例は
        // 755 には groundUp が無いためのもので、751 には当てはまらない。
        // biomes を書くと実バニラ 1.16.2 クライアントが件数 varint を chunkData 長と
        // 誤読して壊れる。javap pt.a(nf) の `ifeq 58` で確認）。
        Writer sections = new Writer();
        writePaletteSection(sections, 4096, 4, new int[]{0, 1});
        byte[] original = chunkPayload736(3, -2, false, 0x1, sections.bytes(),
                null, List.of());
        byte[] up = ChunkTranslator.translateChunkData736To751(original);
        Reader reader = new Reader(up);
        assertEquals(3, reader.readInt());
        assertEquals(-2, reader.readInt());
        assertEquals(false, reader.readBoolean()); // groundUp=false のまま
        assertEquals(0x1, reader.readVarInt()); // bitMap
        assertTrue(reader.readNamedNbt().tag() instanceof Tag.CompoundTag);
        // heightmaps の直後が chunkData（biomes は無い）。
        byte[] upSections = reader.readByteArray();
        Reader upSectionReader = new Reader(upSections);
        assertEquals(4096, upSectionReader.readShort());
        assertEquals(4, upSectionReader.readUnsignedByte()); // BPE
        assertEquals(2, upSectionReader.readVarInt()); // パレット長
        assertEquals(RegistryRemapper.mapBlockState736To751(0), upSectionReader.readVarInt());
        assertEquals(RegistryRemapper.mapBlockState736To751(1), upSectionReader.readVarInt());
        assertEquals(0, reader.readVarInt()); // ブロックエンティティ 0件
        assertEquals(0, reader.remaining());
        // 往復で元のバイトに完全一致する。
        assertArrayEquals(original, ChunkTranslator.translateChunkData751To736(up));
    }

    @Test
    void chunkDataDirectPaletteRemaps() {
        // 直接パレット（BPE 15）のセクション。エントリはブロック状態IDそのもの。
        // glass_pane（全接続・waterlogged=true）は 736=4731 → 751=4735。
        int[] entries = new int[4096];
        entries[0] = 0; // air
        entries[1] = 1; // stone
        entries[2] = 4731; // glass_pane
        Writer sections = new Writer();
        writeDirectSection(sections, 3, 15, entries);
        byte[] original = chunkPayload736(0, 0, true, 0x1, sections.bytes(),
                filledBiomes(50), List.of());
        byte[] up = ChunkTranslator.translateChunkData736To751(original);
        // 751 形式のセクションをほぐして確認する。
        Reader reader = new Reader(up);
        reader.readInt();
        reader.readInt();
        assertTrue(reader.readBoolean()); // groundUp
        assertEquals(0x1, reader.readVarInt());
        assertTrue(reader.readNamedNbt().tag() instanceof Tag.CompoundTag);
        assertEquals(1024, reader.readVarInt());
        for (int i = 0; i < 1024; i++) {
            assertEquals(50, reader.readVarInt()); // deep_frozen_ocean（恒等写像）
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
            assertEquals(RegistryRemapper.mapBlockState736To751(entries[i]), value);
        }
        assertEquals(4735, RegistryRemapper.mapBlockState736To751(4731));
        // 往復で元のバイトに完全一致する。
        assertArrayEquals(original, ChunkTranslator.translateChunkData751To736(up));
    }

    @Test
    void chunkDataSinglePaletteRemaps() {
        Writer sections = new Writer();
        writePaletteSection(sections, 4096, 0, new int[]{1}); // stone だけ
        byte[] original = chunkPayload736(0, 0, true, 0x1, sections.bytes(),
                filledBiomes(50), List.of());
        byte[] up = ChunkTranslator.translateChunkData736To751(original);
        Reader reader = new Reader(up);
        reader.readInt();
        reader.readInt();
        assertTrue(reader.readBoolean()); // groundUp
        assertEquals(0x1, reader.readVarInt());
        assertTrue(reader.readNamedNbt().tag() instanceof Tag.CompoundTag);
        assertEquals(1024, reader.readVarInt());
        for (int i = 0; i < 1024; i++) {
            reader.readVarInt();
        }
        byte[] upSections = reader.readByteArray();
        Reader s = new Reader(upSections);
        assertEquals(4096, s.readShort());
        assertEquals(0, s.readUnsignedByte());
        assertEquals(RegistryRemapper.mapBlockState736To751(1), s.readVarInt());
        assertEquals(0, s.readVarInt()); // Data Array 長 0
        assertArrayEquals(original, ChunkTranslator.translateChunkData751To736(up));
    }

    @Test
    void chunkDataBiomeInt32Encoding() {
        // 736→751 で biomes が i32 から varint 配列に変わること、751→736 で
        // i32×1024（件数接頭辞なし）に戻ることをバイトで確認する。
        Writer sections = new Writer();
        writePaletteSection(sections, 4096, 4, new int[]{0, 1});
        int[] biomes = new int[1024];
        biomes[0] = 2; // desert
        for (int i = 1; i < 1024; i++) {
            biomes[i] = 1; // plains
        }
        byte[] original = chunkPayload736(5, 6, true, 0x1, sections.bytes(),
                biomes, List.of());
        byte[] up = ChunkTranslator.translateChunkData736To751(original);
        Reader reader = new Reader(up);
        assertEquals(5, reader.readInt());
        assertEquals(6, reader.readInt());
        assertTrue(reader.readBoolean());
        assertEquals(0x1, reader.readVarInt());
        assertTrue(reader.readNamedNbt().tag() instanceof Tag.CompoundTag);
        assertEquals(1024, reader.readVarInt()); // 件数接頭辞あり
        assertEquals(2, reader.readVarInt()); // desert（恒等写像）
        for (int i = 1; i < 1024; i++) {
            assertEquals(1, reader.readVarInt());
        }
        // 逆向きの 736 出力は biomes が i32×1024（件数接頭辞なし）。
        byte[] back = ChunkTranslator.translateChunkData751To736(up);
        Reader backReader = new Reader(back);
        assertEquals(5, backReader.readInt());
        assertEquals(6, backReader.readInt());
        assertTrue(backReader.readBoolean()); // groundUp
        assertTrue(backReader.readBoolean()); // ignoreOldData=true
        assertEquals(0x1, backReader.readVarInt());
        assertTrue(backReader.readNamedNbt().tag() instanceof Tag.CompoundTag);
        assertEquals(2, backReader.readInt()); // desert（i32）
        for (int i = 1; i < 1024; i++) {
            assertEquals(1, backReader.readInt());
        }
        assertArrayEquals(original, back);
    }

    @Test
    void chunkDataBlockEntitiesPassThrough() {
        // 736 と 751 のブロックエンティティ種別は 33件・同一順序のため、
        // 754→755 の sculk_sensor のような落としは無い。両方とも通す。
        List<Tag> entities = List.of(
                blockEntityNbt("minecraft:furnace", 1, 64, 2),
                blockEntityNbt("minecraft:chest", 3, 64, 4));
        Writer sections = new Writer();
        writePaletteSection(sections, 4096, 4, new int[]{0, 1});
        byte[] original736 = chunkPayload736(-17, -23, true, 0x1, sections.bytes(),
                filledBiomes(1), entities);
        byte[] up = ChunkTranslator.translateChunkData736To751(original736);
        Object[] parsed = parseChunk751(up);
        assertEquals(List.of("minecraft:furnace", "minecraft:chest"), parsed[3]);
        byte[] back = ChunkTranslator.translateChunkData751To736(up);
        assertArrayEquals(original736, back);
    }

    // ---- スロット ----

    @Test
    void slotRoundTrip736To751To736() {
        Writer writer = new Writer();
        writeSlot(writer, 1, 64); // stone（736=1・751=1）
        byte[] original = writer.bytes();
        Reader reader = new Reader(original);
        Writer mid = new Writer();
        ChunkTranslator.translateSlot736To751(reader, mid);
        assertEquals(0, reader.remaining());
        Reader reader2 = new Reader(mid.bytes());
        Writer back = new Writer();
        ChunkTranslator.translateSlot751To736(reader2, back);
        assertEquals(0, reader2.remaining());
        assertArrayEquals(original, back.bytes());
    }

    @Test
    void slotIronShovelRemaps() {
        // iron_shovel は 736=572 → 751=599（アイテムの並び替え。Stage A §11）。
        Writer writer = new Writer();
        writeSlot(writer, 572, 1);
        byte[] original = writer.bytes();
        Reader reader = new Reader(original);
        Writer mid = new Writer();
        ChunkTranslator.translateSlot736To751(reader, mid);
        assertArrayEquals(new int[]{599, 1}, readSlotHead(new Reader(mid.bytes())));
        Reader reader2 = new Reader(mid.bytes());
        Writer back = new Writer();
        ChunkTranslator.translateSlot751To736(reader2, back);
        assertArrayEquals(original, back.bytes());
    }

    @Test
    void slotAbsentRoundTrip() {
        Writer writer = new Writer();
        writer.writeBoolean(false);
        byte[] original = writer.bytes();
        Reader reader = new Reader(original);
        Writer mid = new Writer();
        ChunkTranslator.translateSlot736To751(reader, mid);
        assertArrayEquals(original, mid.bytes());
        Reader reader2 = new Reader(mid.bytes());
        Writer back = new Writer();
        ChunkTranslator.translateSlot751To736(reader2, back);
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
        ChunkTranslator.translateSlot736To751(reader, mid);
        assertEquals(0, reader.remaining());
        Reader reader2 = new Reader(mid.bytes());
        Writer back = new Writer();
        ChunkTranslator.translateSlot751To736(reader2, back);
        assertEquals(0, reader2.remaining());
        assertArrayEquals(original, back.bytes());
    }

    // ---- Set Slot ----

    @Test
    void setSlot736To751RemapsItem() {
        byte[] original = setSlotPayload736(0, 5, 572, 1); // iron_shovel
        byte[] up = ChunkTranslator.translateSetSlot736To751(original);
        Reader reader = new Reader(up);
        assertEquals(0, reader.readByte()); // windowId
        assertEquals(5, reader.readShort()); // slot（stateId は無い）
        assertArrayEquals(new int[]{599, 1}, readSlotHead(reader)); // iron_shovel（751）
        assertEquals(0, reader.remaining());
        assertArrayEquals(original, ChunkTranslator.translateSetSlot751To736(up));
    }

    @Test
    void setSlot751To736RemapsItem() {
        byte[] original = setSlotPayload751(1, 9, 599, 3); // iron_shovel
        byte[] down = ChunkTranslator.translateSetSlot751To736(original);
        Reader reader = new Reader(down);
        assertEquals(1, reader.readByte()); // windowId
        assertEquals(9, reader.readShort()); // slot
        assertArrayEquals(new int[]{572, 3}, readSlotHead(reader)); // iron_shovel（736）
        assertEquals(0, reader.remaining());
        assertArrayEquals(original, ChunkTranslator.translateSetSlot736To751(down));
    }

    // ---- Window Items ----

    @Test
    void windowItemsRemapsItemsBothWays() {
        byte[] original = windowItemsPayload(0, new int[][]{{572, 1}, {1, 64}});
        byte[] up = ChunkTranslator.translateWindowItems736To751(original);
        Reader reader = new Reader(up);
        assertEquals(0, reader.readUnsignedByte()); // windowId
        assertEquals(2, reader.readShort()); // count（i16 のまま）
        assertArrayEquals(new int[]{599, 1}, readSlotHead(reader));
        assertArrayEquals(new int[]{1, 64}, readSlotHead(reader));
        assertEquals(0, reader.remaining());
        assertArrayEquals(original, ChunkTranslator.translateWindowItems751To736(up));
    }

    // ---- Window Click ----

    @Test
    void windowClick736To751KeepsAction() {
        // 736⇔751 は両版とも [windowId u8][slot i16][button i8][action i16]
        // [mode varint][単一スロット] のため、action は落とさずそのまま通す。
        // 754→755 のような changedSlots／cursorItem への組み替えは無い。
        byte[] click736 = windowClickPayload736(0, 5, 0, 7, 1, new int[]{572, 1});
        byte[] up = ChunkTranslator.translateWindowClick736To751(click736);
        Reader reader = new Reader(up);
        assertEquals(0, reader.readUnsignedByte()); // windowId
        assertEquals(5, reader.readShort()); // slot
        assertEquals(0, reader.readByte()); // button
        assertEquals(7, reader.readShort()); // action（uid はそのまま）
        assertEquals(1, reader.readVarInt()); // mode（varint のまま）
        assertArrayEquals(new int[]{599, 1}, readSlotHead(reader)); // iron_shovel
        assertEquals(0, reader.remaining());
        // 往復で元のバイトに完全一致する（action も失われない）。
        assertArrayEquals(click736, ChunkTranslator.translateWindowClick751To736(up));
    }

    @Test
    void windowClick751To736KeepsAction() {
        byte[] click751 = windowClickPayload751(1, 3, 1, 9, 2, new int[]{599, 2});
        byte[] down = ChunkTranslator.translateWindowClick751To736(click751);
        Reader reader = new Reader(down);
        assertEquals(1, reader.readUnsignedByte()); // windowId
        assertEquals(3, reader.readShort()); // slot
        assertEquals(1, reader.readByte()); // button
        assertEquals(9, reader.readShort()); // action（uid はそのまま）
        assertEquals(2, reader.readVarInt()); // mode（varint のまま）
        assertArrayEquals(new int[]{572, 2}, readSlotHead(reader)); // iron_shovel
        assertEquals(0, reader.remaining());
        assertArrayEquals(click751, ChunkTranslator.translateWindowClick736To751(down));
    }

    // ---- Entity Destroy ----

    @Test
    void entityDestroyIdentityBothWays() {
        // 両版とも [count varint][entityIds] の配列形式のため恒等写像。
        // count 2以上もそのまま通す（754→755 のような単体化・落としは無い）。
        Writer multi = new Writer();
        multi.writeVarInt(2);
        multi.writeVarInt(7);
        multi.writeVarInt(8);
        byte[] original = multi.bytes();
        assertArrayEquals(original, ChunkTranslator.translateEntityDestroy736To751(original));
        assertArrayEquals(original, ChunkTranslator.translateEntityDestroy751To736(original));
        Writer single = new Writer();
        single.writeVarInt(1);
        single.writeVarInt(42);
        assertArrayEquals(single.bytes(),
                ChunkTranslator.translateEntityDestroy736To751(single.bytes()));
        assertArrayEquals(single.bytes(),
                ChunkTranslator.translateEntityDestroy751To736(single.bytes()));
    }
}
