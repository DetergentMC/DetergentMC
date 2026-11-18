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
 * {@link ChunkTranslator} の735（1.16）⇔736（1.16.1）向け変換テスト。
 * 735・736 ともデータコンポーネントは無い（NBT 時代）のため、スロットは
 * bool＋アイテムID＋個数＋名前付き NBT で、NBT は素通しする。
 * レジストリ差分は0件（Stage A §11：1.16と1.16.1の blocks／registries
 * レポートはバイト同一。ブロック763・ブロック状態17,104・アイテム975・
 * エンティティ107・音985・パーティクル72・メニュー24・属性13・
 * エンチャント38・エフェクト32・ブロックエンティティ33・バイオーム79は
 * 全て同一順序・同一 id。verify-735-tables.py 43/43）のため、
 * RegistryRemapper の 735⇔736 写像は全て恒等写像で、スロットの
 * アイテムID・チャンクのブロック状態ID・バイオームIDは付け替わらない。
 * パケットIDも全272件が同一（Stage A §8：変更0・新規0・削除0。
 * Chunk Data 0x21、Set Slot 0x16、Window Items 0x14、Window Click 0x09、
 * Entity Destroy 0x37、Update Light 0x24、Multi Block Change 0x0f）。
 * stateId は 735⇔736 には無い（756 で追加されたもの）。
 * フィールド構成の差分は実バニラ 1.16 jar の javap と生キャプチャで
 * 確認した（ChunkTranslator.java の735節の javadoc を参照）。
 * minecraft-data の pc/1.16/protocol.json は pc/1.16.2 とバイト同一の
 * 誤写で 1.16.2 のレイアウトのため、1.16 の根拠には使っていない
 * （Stage A パケット側ノート Step 2 参照）。
 * Chunk Data の生キャプチャは実バニラ 1.16 サーバーから取ったもの
 * （チャンク -3,-15、13,011バイト。Java 8・オフラインモード・圧縮 -1・
 * view-distance 10・ポート 25568。Python ソケットの偽クライアント。
 * groundUp=true、ignoreOldData=true、bitMap 0x000f、biomes 1024件全て 24、
 * セクション y=0〜3 は BPE 4・Data Array 長 256、ブロックエンティティ 0件。
 * cap735-chunk-x--3-z--15.bin）。
 * 736 の実バニラ生キャプチャ cap736-chunk-x--17-z--23.bin（15,083バイト）
 * も流用する（735と736でチャンク形式が同一のため。実バニラ 1.16 jar の
 * Chunk Data（ot）・ChunkBiomeContainer（cgz）・BitStorage（adj）等の
 * 9クラスが 1.16.1 の同名クラスとバイト同一であることを cmp で確認済み）。
 */
class ChunkTranslator735Test {

    /** スロット1個（735／736形式：bool＋アイテムID＋個数＋名前付き NBT 無し）。 */
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

    /** NBT 付きスロット1個（735／736形式）。 */
    private static void writeSlotWithNbt(Writer writer, int itemId, int count, Tag nbt) {
        writer.writeBoolean(true);
        writer.writeVarInt(itemId);
        writer.writeByte(count);
        writer.writeNamedNbt("", nbt);
    }

    /** 735形式の Set Slot ペイロード（stateId 無し）。 */
    private static byte[] setSlotPayload735(int windowId, int slot, int itemId, int count) {
        Writer writer = new Writer();
        writer.writeByte(windowId);
        writer.writeShort(slot);
        writeSlot(writer, itemId, count);
        return writer.bytes();
    }

    /** 736形式の Set Slot ペイロード（stateId 無し。735 と同じ形）。 */
    private static byte[] setSlotPayload736(int windowId, int slot, int itemId, int count) {
        return setSlotPayload735(windowId, slot, itemId, count);
    }

    /** 735／736形式の Window Items ペイロード（count は i16。両版で同じ形）。 */
    private static byte[] windowItemsPayload(int windowId, int[][] slots) {
        Writer writer = new Writer();
        writer.writeUnsignedByte(windowId);
        writer.writeShort(slots.length);
        for (int[] slot : slots) {
            writeSlot(writer, slot[0], slot[1]);
        }
        return writer.bytes();
    }

    /** 735形式の Window Click ペイロード（action 付き・単一スロット）。 */
    private static byte[] windowClickPayload735(int windowId, int slot, int button,
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

    /** 736形式の Window Click ペイロード（735 と同じ形。action 付き・単一スロット）。 */
    private static byte[] windowClickPayload736(int windowId, int slot, int button,
            int action, int mode, int[] item) {
        return windowClickPayload735(windowId, slot, button, action, mode, item);
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

    /** 735形式の Chunk Data ペイロードを組み立てる（biomes は i32×1024・件数接頭辞なし）。 */
    private static byte[] chunkPayload735(int x, int z, boolean groundUp, boolean ignoreOldData,
            int bitMap, byte[] sectionsBytes, int[] biomes, List<Tag> blockEntities) {
        Writer writer = new Writer();
        writer.writeInt(x);
        writer.writeInt(z);
        writer.writeBoolean(groundUp);
        writer.writeBoolean(ignoreOldData);
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

    /** 736形式の Chunk Data ペイロードを組み立てる（735 と同じ形）。 */
    private static byte[] chunkPayload736(int x, int z, boolean groundUp, boolean ignoreOldData,
            int bitMap, byte[] sectionsBytes, int[] biomes, List<Tag> blockEntities) {
        return chunkPayload735(x, z, groundUp, ignoreOldData, bitMap, sectionsBytes,
                biomes, blockEntities);
    }

    /** 1024件のバイオーム配列（全て同じ値）。 */
    private static int[] filledBiomes(int value) {
        int[] biomes = new int[1024];
        for (int i = 0; i < biomes.length; i++) {
            biomes[i] = value;
        }
        return biomes;
    }

    /** 735形式の Chunk Data を読み、(bitMap, biomes, セクション生バイト列, ブロックエンティティ id 列) を返す。 */
    private static Object[] parseChunk735(byte[] payload, int expectX, int expectZ,
            boolean expectIgnoreOldData) {
        Reader reader = new Reader(payload);
        assertEquals(expectX, reader.readInt());
        assertEquals(expectZ, reader.readInt());
        assertTrue(reader.readBoolean()); // groundUp
        assertEquals(expectIgnoreOldData, reader.readBoolean()); // ignoreOldData
        int bitMap = reader.readVarInt();
        assertTrue(reader.readNamedNbt().tag() instanceof Tag.CompoundTag);
        int[] biomes = new int[1024];
        for (int i = 0; i < 1024; i++) {
            biomes[i] = reader.readInt(); // biomes は i32（件数接頭辞なし）
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
    void realCapturedChunk735To736AndBack() throws Exception {
        // 実バニラ 1.16 サーバーからの生キャプチャ（チャンク -3,-15、
        // 13,011バイト。groundUp=true、ignoreOldData=true、bitMap 0x000f、
        // biomes 1024件全て 24（i32）、セクション y=0〜3 は BPE 4・
        // Data Array 長 256＝non-straddling、ブロックエンティティ 0件）。
        byte[] captured;
        try (InputStream in = getClass().getResourceAsStream(
                "/cap735-chunk-x--3-z--15.bin")) {
            assertTrue(in != null, "テストリソースが見つかりません");
            captured = in.readAllBytes();
        }
        assertEquals(13011, captured.length);
        // 735→736 は恒等写像のためバイト完全一致する。
        byte[] up = ChunkTranslator.translateChunkData735To736(captured);
        assertArrayEquals(captured, up);
        Object[] parsed = parseChunk735(up, -3, -15, true);
        assertEquals(0x0F, parsed[0]); // bitMap
        int[] biomes = (int[]) parsed[1];
        for (int biome : biomes) {
            assertEquals(24, biome); // 全て 24（恒等写像）
        }
        byte[] sections = (byte[]) parsed[2];
        assertEquals(List.of(), parsed[3]); // ブロックエンティティ 0件
        Object[] first = readFirstSectionPalette(sections);
        assertEquals(4, first[1]); // BPE
        assertEquals(256, first[3]); // Data Array 長（non-straddling）
        // 往復で元のバイトに完全一致する。
        assertArrayEquals(captured, ChunkTranslator.translateChunkData736To735(up));
    }

    @Test
    void reusedCapturedChunk736IdentityBothWays() throws Exception {
        // 736 の実バニラ生キャプチャ（チャンク -17,-23、15,083バイト）を
        // 735形式として読んでもバイト完全一致する（735と736でチャンク形式が
        // 同一のため。実バニラ 1.16 jar の ot／cgz／adj 等 9クラスが
        // 1.16.1 の同名クラスとバイト同一であることを cmp で確認済み）。
        byte[] captured;
        try (InputStream in = getClass().getResourceAsStream(
                "/cap736-chunk-x--17-z--23.bin")) {
            assertTrue(in != null, "テストリソースが見つかりません");
            captured = in.readAllBytes();
        }
        assertEquals(15083, captured.length);
        byte[] up = ChunkTranslator.translateChunkData735To736(captured);
        assertArrayEquals(captured, up);
        Object[] parsed = parseChunk735(up, -17, -23, true);
        assertEquals(0x1F, parsed[0]); // bitMap
        int[] biomes = (int[]) parsed[1];
        for (int biome : biomes) {
            assertEquals(1, biome); // 平原（両版とも id 1。恒等写像）
        }
        byte[] sections = (byte[]) parsed[2];
        assertEquals(List.of(), parsed[3]); // ブロックエンティティ 0件
        Object[] first = readFirstSectionPalette(sections);
        assertEquals(3949, first[0]); // blockCount（生キャプチャの実測値）
        assertEquals(4, first[1]); // BPE
        assertEquals(256, first[3]); // Data Array 長（non-straddling）
        assertEquals(14, ((int[]) first[2]).length); // パレット長
        // 逆向きも恒等写像。
        assertArrayEquals(captured, ChunkTranslator.translateChunkData736To735(captured));
        assertArrayEquals(captured, ChunkTranslator.translateChunkData736To735(up));
    }

    // ---- Chunk Data（合成ペイロード） ----

    @Test
    void chunkDataGroundUpFalseHasNoBiomes() {
        // 両版とも biomes は groundUp のときだけ。groundUp=false の 735 チャンクを
        // 736 にするとき、biomes は書かない。ignoreOldData=false もそのまま通す
        // （736→751 のように落とさない。735⇔736 は両版とも ignoreOldData がある）。
        Writer sections = new Writer();
        writePaletteSection(sections, 4096, 4, new int[]{0, 1});
        byte[] original = chunkPayload735(3, -2, false, false, 0x1, sections.bytes(),
                null, List.of());
        byte[] up = ChunkTranslator.translateChunkData735To736(original);
        assertArrayEquals(original, up);
        Reader reader = new Reader(up);
        assertEquals(3, reader.readInt());
        assertEquals(-2, reader.readInt());
        assertEquals(false, reader.readBoolean()); // groundUp=false のまま
        assertEquals(false, reader.readBoolean()); // ignoreOldData=false のまま
        assertEquals(0x1, reader.readVarInt()); // bitMap
        assertTrue(reader.readNamedNbt().tag() instanceof Tag.CompoundTag);
        // heightmaps の直後が chunkData（biomes は無い）。
        byte[] upSections = reader.readByteArray();
        Reader upSectionReader = new Reader(upSections);
        assertEquals(4096, upSectionReader.readShort());
        assertEquals(4, upSectionReader.readUnsignedByte()); // BPE
        assertEquals(2, upSectionReader.readVarInt()); // パレット長
        assertEquals(0, upSectionReader.readVarInt()); // air（恒等写像）
        assertEquals(1, upSectionReader.readVarInt()); // stone（恒等写像）
        assertEquals(0, reader.readVarInt()); // ブロックエンティティ 0件
        assertEquals(0, reader.remaining());
        // 往復で元のバイトに完全一致する。
        assertArrayEquals(original, ChunkTranslator.translateChunkData736To735(up));
    }

    @Test
    void chunkDataDirectPaletteIdentity() {
        // 直接パレット（BPE 15）のセクション。エントリはブロック状態IDそのもの。
        // glass_pane（全接続・waterlogged=true）は 735=4731・736=4731（恒等写像。
        // 736→751 では 4731→4735 だったが、735⇔736 では変わらない）。
        assertEquals(4731, RegistryRemapper.mapBlockState735To736(4731));
        assertEquals(4731, RegistryRemapper.mapBlockState736To735(4731));
        int[] entries = new int[4096];
        entries[0] = 0; // air
        entries[1] = 1; // stone
        entries[2] = 4731; // glass_pane
        Writer sections = new Writer();
        writeDirectSection(sections, 3, 15, entries);
        byte[] original = chunkPayload735(0, 0, true, true, 0x1, sections.bytes(),
                filledBiomes(50), List.of());
        byte[] up = ChunkTranslator.translateChunkData735To736(original);
        assertArrayEquals(original, up);
        // 736 形式のセクションをほぐして確認する。
        Reader reader = new Reader(up);
        reader.readInt();
        reader.readInt();
        assertTrue(reader.readBoolean()); // groundUp
        assertTrue(reader.readBoolean()); // ignoreOldData
        assertEquals(0x1, reader.readVarInt());
        assertTrue(reader.readNamedNbt().tag() instanceof Tag.CompoundTag);
        for (int i = 0; i < 1024; i++) {
            assertEquals(50, reader.readInt()); // deep_frozen_ocean（恒等写像）
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
            assertEquals(entries[i], value);
        }
        // 往復で元のバイトに完全一致する。
        assertArrayEquals(original, ChunkTranslator.translateChunkData736To735(up));
    }

    @Test
    void chunkDataSinglePaletteIdentity() {
        Writer sections = new Writer();
        writePaletteSection(sections, 4096, 0, new int[]{1}); // stone だけ
        byte[] original = chunkPayload735(0, 0, true, true, 0x1, sections.bytes(),
                filledBiomes(50), List.of());
        byte[] up = ChunkTranslator.translateChunkData735To736(original);
        assertArrayEquals(original, up);
        Reader reader = new Reader(up);
        reader.readInt();
        reader.readInt();
        assertTrue(reader.readBoolean()); // groundUp
        assertTrue(reader.readBoolean()); // ignoreOldData
        assertEquals(0x1, reader.readVarInt());
        assertTrue(reader.readNamedNbt().tag() instanceof Tag.CompoundTag);
        for (int i = 0; i < 1024; i++) {
            assertEquals(50, reader.readInt());
        }
        byte[] upSections = reader.readByteArray();
        Reader s = new Reader(upSections);
        assertEquals(4096, s.readShort());
        assertEquals(0, s.readUnsignedByte());
        assertEquals(1, s.readVarInt()); // stone（恒等写像）
        assertEquals(0, s.readVarInt()); // Data Array 長 0
        assertArrayEquals(original, ChunkTranslator.translateChunkData736To735(up));
    }

    @Test
    void chunkDataBiomeInt32Encoding() {
        // 735⇔736 は両方向とも biomes が i32×1024（件数接頭辞なし）。
        // 736→751 のような varint 配列への変換は無い。
        Writer sections = new Writer();
        writePaletteSection(sections, 4096, 4, new int[]{0, 1});
        int[] biomes = new int[1024];
        biomes[0] = 2; // desert
        for (int i = 1; i < 1024; i++) {
            biomes[i] = 1; // plains
        }
        byte[] original = chunkPayload735(5, 6, true, true, 0x1, sections.bytes(),
                biomes, List.of());
        byte[] up = ChunkTranslator.translateChunkData735To736(original);
        assertArrayEquals(original, up);
        Reader reader = new Reader(up);
        assertEquals(5, reader.readInt());
        assertEquals(6, reader.readInt());
        assertTrue(reader.readBoolean());
        assertTrue(reader.readBoolean()); // ignoreOldData=true
        assertEquals(0x1, reader.readVarInt());
        assertTrue(reader.readNamedNbt().tag() instanceof Tag.CompoundTag);
        assertEquals(2, reader.readInt()); // desert（i32。件数接頭辞なし）
        for (int i = 1; i < 1024; i++) {
            assertEquals(1, reader.readInt());
        }
        // 逆向きも同じ。
        byte[] back = ChunkTranslator.translateChunkData736To735(up);
        assertArrayEquals(original, back);
    }

    @Test
    void chunkDataBlockEntitiesPassThrough() {
        // 735 と 736 のブロックエンティティ種別は 33件・同一順序のため、
        // 736節の sculk_sensor のような落としは無い。両方とも通す。
        List<Tag> entities = List.of(
                blockEntityNbt("minecraft:furnace", 1, 64, 2),
                blockEntityNbt("minecraft:chest", 3, 64, 4));
        Writer sections = new Writer();
        writePaletteSection(sections, 4096, 4, new int[]{0, 1});
        byte[] original735 = chunkPayload735(-3, -15, true, true, 0x1, sections.bytes(),
                filledBiomes(24), entities);
        byte[] up = ChunkTranslator.translateChunkData735To736(original735);
        assertArrayEquals(original735, up);
        Object[] parsed = parseChunk735(up, -3, -15, true);
        assertEquals(List.of("minecraft:furnace", "minecraft:chest"), parsed[3]);
        byte[] back = ChunkTranslator.translateChunkData736To735(up);
        assertArrayEquals(original735, back);
    }

    // ---- スロット ----

    @Test
    void slotRoundTrip735To736To735() {
        Writer writer = new Writer();
        writeSlot(writer, 1, 64); // stone（735=1・736=1）
        byte[] original = writer.bytes();
        Reader reader = new Reader(original);
        Writer mid = new Writer();
        ChunkTranslator.translateSlot735To736(reader, mid);
        assertEquals(0, reader.remaining());
        assertArrayEquals(original, mid.bytes());
        Reader reader2 = new Reader(mid.bytes());
        Writer back = new Writer();
        ChunkTranslator.translateSlot736To735(reader2, back);
        assertEquals(0, reader2.remaining());
        assertArrayEquals(original, back.bytes());
    }

    @Test
    void slotPillagerSpawnEggIdentity() {
        // pillager_spawn_egg は 735=791・736=791（恒等写像。Stage A §12.4）。
        // 736→751 では 791→790 だったが、735⇔736 では変わらない。
        assertEquals(791, RegistryRemapper.mapItem735To736(791));
        Writer writer = new Writer();
        writeSlot(writer, 791, 1);
        byte[] original = writer.bytes();
        Reader reader = new Reader(original);
        Writer mid = new Writer();
        ChunkTranslator.translateSlot735To736(reader, mid);
        assertArrayEquals(new int[]{791, 1}, readSlotHead(new Reader(mid.bytes())));
        Reader reader2 = new Reader(mid.bytes());
        Writer back = new Writer();
        ChunkTranslator.translateSlot736To735(reader2, back);
        assertArrayEquals(original, back.bytes());
    }

    @Test
    void slotAbsentRoundTrip() {
        Writer writer = new Writer();
        writer.writeBoolean(false);
        byte[] original = writer.bytes();
        Reader reader = new Reader(original);
        Writer mid = new Writer();
        ChunkTranslator.translateSlot735To736(reader, mid);
        assertArrayEquals(original, mid.bytes());
        Reader reader2 = new Reader(mid.bytes());
        Writer back = new Writer();
        ChunkTranslator.translateSlot736To735(reader2, back);
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
        ChunkTranslator.translateSlot735To736(reader, mid);
        assertEquals(0, reader.remaining());
        Reader reader2 = new Reader(mid.bytes());
        Writer back = new Writer();
        ChunkTranslator.translateSlot736To735(reader2, back);
        assertEquals(0, reader2.remaining());
        assertArrayEquals(original, back.bytes());
    }

    // ---- Set Slot ----

    @Test
    void setSlot735To736KeepsItem() {
        byte[] original = setSlotPayload735(0, 5, 791, 1); // pillager_spawn_egg
        byte[] up = ChunkTranslator.translateSetSlot735To736(original);
        assertArrayEquals(original, up);
        Reader reader = new Reader(up);
        assertEquals(0, reader.readByte()); // windowId
        assertEquals(5, reader.readShort()); // slot（stateId は無い）
        assertArrayEquals(new int[]{791, 1}, readSlotHead(reader)); // 変わらない
        assertEquals(0, reader.remaining());
        assertArrayEquals(original, ChunkTranslator.translateSetSlot736To735(up));
    }

    @Test
    void setSlot736To735KeepsItem() {
        byte[] original = setSlotPayload736(1, 9, 809, 3); // turtle_spawn_egg
        byte[] down = ChunkTranslator.translateSetSlot736To735(original);
        assertArrayEquals(original, down);
        Reader reader = new Reader(down);
        assertEquals(1, reader.readByte()); // windowId
        assertEquals(9, reader.readShort()); // slot
        assertArrayEquals(new int[]{809, 3}, readSlotHead(reader)); // 変わらない
        assertEquals(0, reader.remaining());
        assertArrayEquals(original, ChunkTranslator.translateSetSlot735To736(down));
    }

    // ---- Window Items ----

    @Test
    void windowItemsKeepsItemsBothWays() {
        byte[] original = windowItemsPayload(0, new int[][]{{791, 1}, {1, 64}});
        byte[] up = ChunkTranslator.translateWindowItems735To736(original);
        assertArrayEquals(original, up);
        Reader reader = new Reader(up);
        assertEquals(0, reader.readUnsignedByte()); // windowId
        assertEquals(2, reader.readShort()); // count（i16 のまま）
        assertArrayEquals(new int[]{791, 1}, readSlotHead(reader));
        assertArrayEquals(new int[]{1, 64}, readSlotHead(reader));
        assertEquals(0, reader.remaining());
        assertArrayEquals(original, ChunkTranslator.translateWindowItems736To735(up));
    }

    // ---- Window Click ----

    @Test
    void windowClick735To736KeepsAction() {
        // 735⇔736 は両版とも [windowId u8][slot i16][button i8][action i16]
        // [mode varint][単一スロット] のため、action は落とさずそのまま通す。
        // 736⇔751 のような changedSlots／cursorItem への組み替えは無い。
        byte[] click735 = windowClickPayload735(0, 5, 0, 7, 1, new int[]{791, 1});
        byte[] up = ChunkTranslator.translateWindowClick735To736(click735);
        assertArrayEquals(click735, up);
        Reader reader = new Reader(up);
        assertEquals(0, reader.readUnsignedByte()); // windowId
        assertEquals(5, reader.readShort()); // slot
        assertEquals(0, reader.readByte()); // button
        assertEquals(7, reader.readShort()); // action（uid はそのまま）
        assertEquals(1, reader.readVarInt()); // mode（varint のまま）
        assertArrayEquals(new int[]{791, 1}, readSlotHead(reader)); // 変わらない
        assertEquals(0, reader.remaining());
        // 往復で元のバイトに完全一致する（action も失われない）。
        assertArrayEquals(click735, ChunkTranslator.translateWindowClick736To735(up));
    }

    @Test
    void windowClick736To735KeepsAction() {
        byte[] click736 = windowClickPayload736(1, 3, 1, 9, 2, new int[]{809, 2});
        byte[] down = ChunkTranslator.translateWindowClick736To735(click736);
        assertArrayEquals(click736, down);
        Reader reader = new Reader(down);
        assertEquals(1, reader.readUnsignedByte()); // windowId
        assertEquals(3, reader.readShort()); // slot
        assertEquals(1, reader.readByte()); // button
        assertEquals(9, reader.readShort()); // action（uid はそのまま）
        assertEquals(2, reader.readVarInt()); // mode（varint のまま）
        assertArrayEquals(new int[]{809, 2}, readSlotHead(reader)); // 変わらない
        assertEquals(0, reader.remaining());
        assertArrayEquals(click736, ChunkTranslator.translateWindowClick735To736(down));
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
        assertArrayEquals(original, ChunkTranslator.translateEntityDestroy735To736(original));
        assertArrayEquals(original, ChunkTranslator.translateEntityDestroy736To735(original));
        Writer single = new Writer();
        single.writeVarInt(1);
        single.writeVarInt(42);
        assertArrayEquals(single.bytes(),
                ChunkTranslator.translateEntityDestroy735To736(single.bytes()));
        assertArrayEquals(single.bytes(),
                ChunkTranslator.translateEntityDestroy736To735(single.bytes()));
    }

    // ---- Multi Block Change ----

    @Test
    void multiBlockChangeIdentityBothWays() {
        // 両版とも [chunkX i32][chunkZ i32][records varint
        // [{horizontalPos u8, y u8, blockId varint}]] の同一形式のため恒等写像。
        // 736→751 のような List版への分割は不要（形式が違うための分割で、
        // 735⇔736 は同一形式のため）。blockId 4731（glass_pane）は変わらない。
        Writer writer = new Writer();
        writer.writeInt(-3);
        writer.writeInt(-15);
        writer.writeVarInt(2);
        writer.writeUnsignedByte(0x12); // horizontalPos（localX=1, localZ=2）
        writer.writeUnsignedByte(65); // y
        writer.writeVarInt(4731); // glass_pane
        writer.writeUnsignedByte(0x34); // horizontalPos（localX=3, localZ=4）
        writer.writeUnsignedByte(70); // y
        writer.writeVarInt(1); // stone
        byte[] original = writer.bytes();
        assertArrayEquals(original, ChunkTranslator.translateMultiBlockChange735To736(original));
        assertArrayEquals(original, ChunkTranslator.translateMultiBlockChange736To735(original));
    }

    // ---- Update Light ----

    @Test
    void updateLightIdentityBothWays() {
        // 両版とも [chunkX varint][chunkZ varint][trustEdges bool]
        // [skyLightMask／blockLightMask／emptySkyLightMask／emptyBlockLightMask
        // varint][マスクで決まる byte[] 列] の同一形式のため恒等写像。
        // 実バニラ 1.16 jar の ow が 1.16.1 の ow とバイト同一であることと、
        // ViaVersion Protocol1_16To1_16_1 が空プロトコルであることで確認。
        Writer writer = new Writer();
        writer.writeVarInt(5); // chunkX
        writer.writeVarInt(7); // chunkZ
        writer.writeBoolean(true); // trustEdges
        writer.writeVarInt(1); // skyLightMask（bit 0 のみ）
        writer.writeVarInt(0); // blockLightMask
        writer.writeVarInt(0); // emptySkyLightMask
        writer.writeVarInt(0); // emptyBlockLightMask
        byte[] light = new byte[2048];
        for (int i = 0; i < light.length; i++) {
            light[i] = (byte) 0xAB;
        }
        writer.writeBytes(light);
        byte[] original = writer.bytes();
        assertArrayEquals(original, ChunkTranslator.translateUpdateLight735To736(original));
        assertArrayEquals(original, ChunkTranslator.translateUpdateLight736To735(original));
    }
}
