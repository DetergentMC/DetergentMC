package io.detergentmc.protocol;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.detergentmc.protocol.Nbt.Tag;
import io.detergentmc.protocol.PacketIO.Reader;
import io.detergentmc.protocol.PacketIO.Writer;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * {@link ChunkTranslator} の753（1.16.3）⇔754（1.16.5）向け変換テスト。
 * 753⇔754 は恒等写像である（ChunkTranslator.java の753節の javadoc を参照）：
 * パケットID差分 0件（packet-ids-753.json。変更 0・新規 0・削除 0）、
 * レジストリ差分 0件（実バニラ 1.16.3 の registries.json・blocks レポートは
 * 1.16.5 版とバイト同一。ブロック状態 17,112・ブロック 763・アイテム 976・
 * バイオーム 79。RegistryTables753 は恒等写像で verify-753-tables.py 43/43）、
 * チャンク形式も同一（[groundUp bool][bitMap varint][biomes は groundUp の
 * ときだけ]、セクションは non-straddling。実バニラ 1.16.3 から Chunk Data
 * 0x20 を 482件受信済み。753ノート §8.1・§11.1・§11.6）。
 * そのため全変換器は入力をそのまま返す。754⇔755 のような grass_path→
 * dirt_path の付け替え、Entity Destroy の単体化、Window Click の action
 * 落としは一切無い。値付け替えの変換器は作らない（SetCooldown・
 * EntityEquipment・TradeList・Sound系・World Particles・Entity Metadata・
 * Spawn Entity・Block Change・Multi Block Change・Block Entity Data・Tags・
 * Map・Explosion・EntityUpdateAttributes・Edit Book・Update Light はすべて
 * 恒等写像のため不要）。
 * Chunk Data の生キャプチャは実バニラ 1.16.5 のもの
 * （cap754-chunk-x-15-z-7.bin、チャンク -15,-7、10,633バイト）だが、
 * 754形式バイト＝753形式バイトと同一（上記の通り形式・レジストリとも同一）
 * であるため 753 の入力として使える。
 * スロットは 753／754 とも [present bool][id varint][個数 i8]
 * [任意の名前付き NBT]（NBT 時代。データコンポーネントは無い）。
 */
class ChunkTranslator753Test {

    /** スロット1個（753／754形式：bool＋アイテムID＋個数＋名前付き NBT 無し）。 */
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

    /** NBT 付きスロット1個（753／754形式）。 */
    private static void writeSlotWithNbt(Writer writer, int itemId, int count, Tag nbt) {
        writer.writeBoolean(true);
        writer.writeVarInt(itemId);
        writer.writeByte(count);
        writer.writeNamedNbt("", nbt);
    }

    /** 753／754形式の Set Slot ペイロード（stateId 無し）。 */
    private static byte[] setSlotPayload(int windowId, int slot, int itemId, int count) {
        Writer writer = new Writer();
        writer.writeByte(windowId);
        writer.writeShort(slot);
        writeSlot(writer, itemId, count);
        return writer.bytes();
    }

    /** 753／754形式の Window Items ペイロード（count は i16）。 */
    private static byte[] windowItemsPayload(int windowId, int[][] slots) {
        Writer writer = new Writer();
        writer.writeUnsignedByte(windowId);
        writer.writeShort(slots.length);
        for (int[] slot : slots) {
            writeSlot(writer, slot[0], slot[1]);
        }
        return writer.bytes();
    }

    /** 753／754形式の Window Click ペイロード（action 付き・単一スロット）。 */
    private static byte[] windowClickPayload(int windowId, int slot, int button,
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

    /** 753／754形式の Entity Destroy ペイロード（[count][entityIds] の配列形式）。 */
    private static byte[] entityDestroyPayload(int... entityIds) {
        Writer writer = new Writer();
        writer.writeVarInt(entityIds.length);
        for (int id : entityIds) {
            writer.writeVarInt(id);
        }
        return writer.bytes();
    }

    /** 空の heightmaps NBT（名前付きコンパウンド）。 */
    private static Tag emptyHeightmaps() {
        return new Tag.CompoundTag(new LinkedHashMap<>());
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

    /** 753／754形式の Chunk Data ペイロードを組み立てる。 */
    private static byte[] chunkPayload(int x, int z, boolean groundUp, int bitMap,
            byte[] sectionsBytes, int[] biomes) {
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
        writer.writeVarInt(0); // ブロックエンティティ 0件
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

    // ---- Chunk Data（生キャプチャ） ----

    @Test
    void realCapturedChunk753To754AndBack() throws Exception {
        // 実バニラ 1.16.5 サーバーからの生キャプチャ（チャンク -15,-7、
        // 10,633バイト。groundUp=true、bitMap 0x000f、biomes 1024件全て 50、
        // セクション y=0 は BPE 5・パレット 17・Data Array 長 342
        // ＝non-straddling、ブロックエンティティ 0件）。
        // 754形式バイト＝753形式バイトと同一（形式・レジストリとも同一。
        // 753ノート §8.1・§11.1・§11.6）のため 753 の入力として使える。
        byte[] captured;
        try (InputStream in = getClass().getResourceAsStream(
                "/cap754-chunk-x-15-z-7.bin")) {
            assertTrue(in != null, "テストリソースが見つかりません");
            captured = in.readAllBytes();
        }
        assertEquals(10633, captured.length);
        // 両方向ともバイト完全一致の恒等写像。
        assertArrayEquals(captured, ChunkTranslator.translateChunkData753To754(captured));
        assertArrayEquals(captured, ChunkTranslator.translateChunkData754To753(captured));
        // 753→754→753 の往復もバイト完全一致。
        byte[] up = ChunkTranslator.translateChunkData753To754(captured);
        assertArrayEquals(captured, ChunkTranslator.translateChunkData754To753(up));
    }

    // ---- Chunk Data（合成ペイロード） ----

    @Test
    void chunkDataGroundUpFalseIdentity() {
        Writer sections = new Writer();
        writePaletteSection(sections, 4096, 4, new int[]{0, 1});
        byte[] original = chunkPayload(3, -2, false, 0x1, sections.bytes(), null);
        assertArrayEquals(original, ChunkTranslator.translateChunkData753To754(original));
        assertArrayEquals(original, ChunkTranslator.translateChunkData754To753(original));
        byte[] up = ChunkTranslator.translateChunkData753To754(original);
        assertArrayEquals(original, ChunkTranslator.translateChunkData754To753(up));
        // groundUp=false のまま（754→755 のような biomes 補完は無い）。
        Reader reader = new Reader(up);
        assertEquals(3, reader.readInt());
        assertEquals(-2, reader.readInt());
        assertEquals(false, reader.readBoolean()); // groundUp
        assertEquals(0x1, reader.readVarInt()); // bitMap
        assertTrue(reader.readNamedNbt().tag() instanceof Tag.CompoundTag); // heightmaps
        reader.readByteArray(); // セクション
        assertEquals(0, reader.readVarInt()); // ブロックエンティティ 0件
        assertEquals(0, reader.remaining());
    }

    @Test
    void chunkDataDirectPaletteIdentity() {
        // 直接パレット（BPE 15）のセクション。エントリはブロック状態IDそのもの。
        int[] entries = new int[4096];
        entries[0] = 0; // air
        entries[1] = 1; // stone
        entries[2] = 2;
        Writer sections = new Writer();
        writeDirectSection(sections, 3, 15, entries);
        byte[] original = chunkPayload(0, 0, true, 0x1, sections.bytes(),
                filledBiomes(50));
        assertArrayEquals(original, ChunkTranslator.translateChunkData753To754(original));
        assertArrayEquals(original, ChunkTranslator.translateChunkData754To753(original));
        byte[] up = ChunkTranslator.translateChunkData753To754(original);
        assertArrayEquals(original, ChunkTranslator.translateChunkData754To753(up));
    }

    // ---- スロット ----

    @Test
    void slotRoundTrip753To754To753() {
        Writer writer = new Writer();
        writeSlot(writer, 1, 64); // stone（753=1・754=1）
        byte[] original = writer.bytes();
        Reader reader = new Reader(original);
        Writer mid = new Writer();
        ChunkTranslator.translateSlot753To754(reader, mid);
        assertEquals(0, reader.remaining());
        assertArrayEquals(original, mid.bytes());
        Reader reader2 = new Reader(mid.bytes());
        Writer back = new Writer();
        ChunkTranslator.translateSlot754To753(reader2, back);
        assertEquals(0, reader2.remaining());
        assertArrayEquals(original, back.bytes());
    }

    @Test
    void slotGrassPathStaysGrassPath() {
        // 753 の grass_path（372）は 754 でも grass_path（372）。754→755 の
        // ような dirt_path（393）への改名は無い。恒等写像の決定的な確認。
        Writer writer = new Writer();
        writeSlot(writer, 372, 1);
        byte[] original = writer.bytes();
        Reader reader = new Reader(original);
        Writer mid = new Writer();
        ChunkTranslator.translateSlot753To754(reader, mid);
        assertArrayEquals(new int[]{372, 1}, readSlotHead(new Reader(mid.bytes())));
        Reader reader2 = new Reader(mid.bytes());
        Writer back = new Writer();
        ChunkTranslator.translateSlot754To753(reader2, back);
        assertArrayEquals(original, back.bytes());
    }

    @Test
    void slotAbsentIdentity() {
        Writer writer = new Writer();
        writer.writeBoolean(false);
        byte[] original = writer.bytes();
        Reader reader = new Reader(original);
        Writer mid = new Writer();
        ChunkTranslator.translateSlot753To754(reader, mid);
        assertArrayEquals(original, mid.bytes());
        Reader reader2 = new Reader(mid.bytes());
        Writer back = new Writer();
        ChunkTranslator.translateSlot754To753(reader2, back);
        assertArrayEquals(original, back.bytes());
    }

    @Test
    void slotWithNbtIdentity() {
        Map<String, Tag> entries = new LinkedHashMap<>();
        entries.put("id", new Tag.StringTag("minecraft:diamond_sword"));
        entries.put("Count", new Tag.ByteTag((byte) 1));
        Tag nbt = new Tag.CompoundTag(entries);
        Writer writer = new Writer();
        writeSlotWithNbt(writer, 100, 1, nbt);
        byte[] original = writer.bytes();
        Reader reader = new Reader(original);
        Writer mid = new Writer();
        ChunkTranslator.translateSlot753To754(reader, mid);
        assertEquals(0, reader.remaining());
        assertArrayEquals(original, mid.bytes());
        Reader reader2 = new Reader(mid.bytes());
        Writer back = new Writer();
        ChunkTranslator.translateSlot754To753(reader2, back);
        assertEquals(0, reader2.remaining());
        assertArrayEquals(original, back.bytes());
    }

    // ---- Set Slot ----

    @Test
    void setSlotIdentityBothWays() {
        byte[] original = setSlotPayload(0, 5, 372, 1); // grass_path
        byte[] up = ChunkTranslator.translateSetSlot753To754(original);
        assertArrayEquals(original, up);
        Reader reader = new Reader(up);
        assertEquals(0, reader.readByte()); // windowId
        assertEquals(5, reader.readShort()); // slot（stateId は無い）
        assertArrayEquals(new int[]{372, 1}, readSlotHead(reader)); // 改名なし
        assertEquals(0, reader.remaining());
        assertArrayEquals(original, ChunkTranslator.translateSetSlot754To753(up));
        assertArrayEquals(original, ChunkTranslator.translateSetSlot754To753(original));
    }

    // ---- Window Items ----

    @Test
    void windowItemsIdentityBothWays() {
        byte[] original = windowItemsPayload(0, new int[][]{{372, 1}, {1, 64}});
        byte[] up = ChunkTranslator.translateWindowItems753To754(original);
        assertArrayEquals(original, up);
        Reader reader = new Reader(up);
        assertEquals(0, reader.readUnsignedByte()); // windowId
        assertEquals(2, reader.readShort()); // count（i16 のまま）
        assertArrayEquals(new int[]{372, 1}, readSlotHead(reader));
        assertArrayEquals(new int[]{1, 64}, readSlotHead(reader));
        assertEquals(0, reader.remaining());
        assertArrayEquals(original, ChunkTranslator.translateWindowItems754To753(up));
        assertArrayEquals(original, ChunkTranslator.translateWindowItems754To753(original));
    }

    // ---- Window Click ----

    @Test
    void windowClickIdentityBothWays() {
        // 753／754形式：windowId・slot・button・action・mode・単一スロット。
        // action（uid）は両版にあるため 7 のまま残る（754→755 のような
        // 落とし・0補完は無い）。mode・cursorItem もそのまま。
        byte[] original = windowClickPayload(0, 5, 0, 7, 1, new int[]{372, 1});
        byte[] up = ChunkTranslator.translateWindowClick753To754(original);
        assertArrayEquals(original, up);
        Reader reader = new Reader(up);
        assertEquals(0, reader.readUnsignedByte()); // windowId
        assertEquals(5, reader.readShort()); // slot
        assertEquals(0, reader.readByte()); // button
        assertEquals(7, reader.readShort()); // action（uid。そのまま）
        assertEquals(1, reader.readVarInt()); // mode
        assertArrayEquals(new int[]{372, 1}, readSlotHead(reader)); // 単一スロット
        assertEquals(0, reader.remaining());
        assertArrayEquals(original, ChunkTranslator.translateWindowClick754To753(up));
        assertArrayEquals(original, ChunkTranslator.translateWindowClick754To753(original));
    }

    // ---- Entity Destroy ----

    @Test
    void entityDestroyCount1Identity() {
        byte[] original = entityDestroyPayload(42);
        byte[] up = ChunkTranslator.translateEntityDestroy753To754(original);
        assertArrayEquals(original, up);
        Reader reader = new Reader(up);
        assertEquals(1, reader.readVarInt()); // count
        assertEquals(42, reader.readVarInt()); // entityId
        assertEquals(0, reader.remaining());
        assertArrayEquals(original, ChunkTranslator.translateEntityDestroy754To753(up));
        assertArrayEquals(original, ChunkTranslator.translateEntityDestroy754To753(original));
    }

    @Test
    void entityDestroyCount2AndEmptyIdentity() {
        // count 2以上も配列形式のまま通す（754→755 のような落としは無い）。
        byte[] multi = entityDestroyPayload(7, 8);
        assertArrayEquals(multi, ChunkTranslator.translateEntityDestroy753To754(multi));
        assertArrayEquals(multi, ChunkTranslator.translateEntityDestroy754To753(multi));
        Reader reader = new Reader(multi);
        assertEquals(2, reader.readVarInt());
        assertEquals(7, reader.readVarInt());
        assertEquals(8, reader.readVarInt());
        assertEquals(0, reader.remaining());
        // count 0 もそのまま通す。
        byte[] empty = entityDestroyPayload();
        assertArrayEquals(empty, ChunkTranslator.translateEntityDestroy753To754(empty));
        assertArrayEquals(empty, ChunkTranslator.translateEntityDestroy754To753(empty));
    }
}
