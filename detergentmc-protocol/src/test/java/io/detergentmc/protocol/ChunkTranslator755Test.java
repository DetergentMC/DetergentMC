package io.detergentmc.protocol;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.detergentmc.protocol.Nbt.Tag;
import io.detergentmc.protocol.PacketIO.Reader;
import io.detergentmc.protocol.PacketIO.Writer;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * {@link ChunkTranslator} の755（1.17）⇔756（1.17.1）向け変換テスト。
 * 755・756 ともデータコンポーネントは無い（NBT 時代）のため、スロットは
 * bool＋アイテムID＋個数＋名前付き NBT で、NBT は素通しする。
 * レジストリ差分は0件（Stage A §11.1：ブロック状態 20,342件・ブロック
 * 898件・アイテム 1,100件・音 1,190件・パーティクル 89件・エンティティ
 * 113件とも同一順序・同一ID。reports／registries.json はバイト同一）。
 * そのためスロットのアイテムIDは付け替えず、Chunk Data は素通し。
 * フィールド構成の差分は protocol.json の突き合わせで5件だけ見つかり
 * （Set Slot・Window Items・Window Click・Entity Destroy・Edit Book）、
 * うち Edit Book を除く4件に変換器がある。Edit Book は現状どこにも
 * 変換器が無いため対象外（ProxySession 担当への申し送り）。
 * stateId の補完値 0 は ViaVersion の ItemPacketRewriter1_17_1
 * （"Add arbitrary state id"）と同じ。
 */
class ChunkTranslator755Test {

    /** スロット1個（755／756形式：bool＋アイテムID＋個数＋名前付き NBT 無し）。 */
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

    /** NBT 付きスロット1個（755／756形式）。 */
    private static void writeSlotWithNbt(Writer writer, int itemId, int count, Tag nbt) {
        writer.writeBoolean(true);
        writer.writeVarInt(itemId);
        writer.writeByte(count);
        writer.writeNamedNbt("", nbt);
    }

    /** 755形式の Set Slot ペイロード（stateId 無し）。 */
    private static byte[] setSlotPayload755(int windowId, int slot, int itemId, int count) {
        Writer writer = new Writer();
        writer.writeByte(windowId);
        writer.writeShort(slot);
        writeSlot(writer, itemId, count);
        return writer.bytes();
    }

    /** 756形式の Set Slot ペイロード（stateId 付き）。 */
    private static byte[] setSlotPayload756(int windowId, int stateId, int slot, int itemId, int count) {
        Writer writer = new Writer();
        writer.writeByte(windowId);
        writer.writeVarInt(stateId);
        writer.writeShort(slot);
        writeSlot(writer, itemId, count);
        return writer.bytes();
    }

    /** 755形式の Window Items ペイロード（stateId・carriedItem 無し、count は i16）。 */
    private static byte[] windowItemsPayload755(int windowId, int[][] slots) {
        Writer writer = new Writer();
        writer.writeUnsignedByte(windowId);
        writer.writeShort(slots.length);
        for (int[] slot : slots) {
            writeSlot(writer, slot[0], slot[1]);
        }
        return writer.bytes();
    }

    /** 756形式の Window Items ペイロード（stateId・carriedItem 付き、count は varint）。 */
    private static byte[] windowItemsPayload756(int windowId, int stateId, int[][] slots,
            int[] carried) {
        Writer writer = new Writer();
        writer.writeUnsignedByte(windowId);
        writer.writeVarInt(stateId);
        writer.writeVarInt(slots.length);
        for (int[] slot : slots) {
            writeSlot(writer, slot[0], slot[1]);
        }
        if (carried == null) {
            writer.writeBoolean(false);
        } else {
            writeSlot(writer, carried[0], carried[1]);
        }
        return writer.bytes();
    }

    /** 756形式の Window Click ペイロード（stateId 付き）。 */
    private static byte[] windowClickPayload756(int windowId, int stateId, int slot, int button,
            int mode, int[][] changed, int[] cursor) {
        Writer writer = new Writer();
        writer.writeUnsignedByte(windowId);
        writer.writeVarInt(stateId);
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

    // ---- Chunk Data（素通し） ----

    @Test
    void realCapturedChunk755Passthrough() throws Exception {
        // 実バニラ 1.17.1 サーバーからの生キャプチャ（チャンク -21,-17、
        // 13,449バイト）を 755⇔756 の Chunk Data に通すとバイト完全一致で返る。
        // このキャプチャを755のテストに使うのが適切な理由：1.17と1.17.1の
        // チャンク形式は同一であることが実測で確定している
        // （packet_map_chunk は protocol.json で完全一致。実バニラ両 jar の
        // Chunk Data パケットクラス qw のフィールド・javap -c も同一で、
        // 差分は難読化クラス名の付け替えのみ。レジストリ差分も0件）。
        // そのため 1.17.1 の生キャプチャはそのまま 755 形式のチャンクでもあり、
        // 素通しであることの裏付けになる。1.17 専用の生キャプチャは無い
        // （Stage E の統合テストで実バニラ 1.17 から取るのが本来の筋だが、
        // 形式同一が確定しているためここでは 1.17.1 のものを使う）。
        byte[] captured;
        try (InputStream in = getClass().getResourceAsStream(
                "/cap756-chunk-21-17.bin")) {
            assertTrue(in != null, "テストリソースが見つかりません");
            captured = in.readAllBytes();
        }
        assertEquals(13449, captured.length);
        assertArrayEquals(captured, ChunkTranslator.translateChunkData755To756(captured));
        assertArrayEquals(captured, ChunkTranslator.translateChunkData756To755(captured));
    }

    // ---- スロット ----

    @Test
    void slotRoundTrip755To756To755() {
        Writer writer = new Writer();
        writeSlot(writer, 1, 64); // stone（755=1・756=1）
        byte[] original = writer.bytes();
        Reader reader = new Reader(original);
        Writer mid = new Writer();
        ChunkTranslator.translateSlot755To756(reader, mid);
        assertEquals(0, reader.remaining());
        Reader reader2 = new Reader(mid.bytes());
        Writer back = new Writer();
        ChunkTranslator.translateSlot756To755(reader2, back);
        assertEquals(0, reader2.remaining());
        assertArrayEquals(original, back.bytes());
    }

    @Test
    void slotRoundTrip756To755To756() {
        Writer writer = new Writer();
        writeSlot(writer, 5, 3); // oak_planks（755=5・756=5）
        byte[] original = writer.bytes();
        Reader reader = new Reader(original);
        Writer mid = new Writer();
        ChunkTranslator.translateSlot756To755(reader, mid);
        assertEquals(0, reader.remaining());
        Reader reader2 = new Reader(mid.bytes());
        Writer back = new Writer();
        ChunkTranslator.translateSlot755To756(reader2, back);
        assertEquals(0, reader2.remaining());
        assertArrayEquals(original, back.bytes());
    }

    @Test
    void slotAbsentRoundTrip() {
        Writer writer = new Writer();
        writer.writeBoolean(false);
        byte[] original = writer.bytes();
        Reader reader = new Reader(original);
        Writer mid = new Writer();
        ChunkTranslator.translateSlot755To756(reader, mid);
        assertArrayEquals(original, mid.bytes());
        Reader reader2 = new Reader(mid.bytes());
        Writer back = new Writer();
        ChunkTranslator.translateSlot756To755(reader2, back);
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
        ChunkTranslator.translateSlot755To756(reader, mid);
        assertEquals(0, reader.remaining());
        Reader reader2 = new Reader(mid.bytes());
        Writer back = new Writer();
        ChunkTranslator.translateSlot756To755(reader2, back);
        assertEquals(0, reader2.remaining());
        assertArrayEquals(original, back.bytes());
    }

    // ---- Set Slot ----

    @Test
    void setSlot755To756InsertsStateId() {
        byte[] original = setSlotPayload755(0, 5, 1, 64);
        byte[] up = ChunkTranslator.translateSetSlot755To756(original);
        Reader reader = new Reader(up);
        assertEquals(0, reader.readByte()); // windowId
        assertEquals(0, reader.readVarInt()); // stateId（補完値 0）
        assertEquals(5, reader.readShort()); // slot
        assertArrayEquals(new int[]{1, 64}, readSlotHead(reader));
        assertEquals(0, reader.remaining());
        assertArrayEquals(original, ChunkTranslator.translateSetSlot756To755(up));
    }

    @Test
    void setSlot756To755DropsStateId() {
        byte[] original = setSlotPayload756(1, 7, 9, 5, 3);
        byte[] down = ChunkTranslator.translateSetSlot756To755(original);
        Reader reader = new Reader(down);
        assertEquals(1, reader.readByte()); // windowId
        assertEquals(9, reader.readShort()); // slot（stateId は無い）
        assertArrayEquals(new int[]{5, 3}, readSlotHead(reader));
        assertEquals(0, reader.remaining());
        // 往復では stateId は 0 に正規化される（元の 7 は失われる）。
        byte[] back = ChunkTranslator.translateSetSlot755To756(down);
        assertArrayEquals(setSlotPayload756(1, 0, 9, 5, 3), back);
    }

    // ---- Window Items ----

    @Test
    void windowItems755To756AddsStateIdAndCarriedItem() {
        byte[] original = windowItemsPayload755(0, new int[][]{{1, 64}, {5, 3}});
        byte[] up = ChunkTranslator.translateWindowItems755To756(original);
        Reader reader = new Reader(up);
        assertEquals(0, reader.readUnsignedByte()); // windowId
        assertEquals(0, reader.readVarInt()); // stateId（補完値 0）
        assertEquals(2, reader.readVarInt()); // count（varint 化）
        assertArrayEquals(new int[]{1, 64}, readSlotHead(reader));
        assertArrayEquals(new int[]{5, 3}, readSlotHead(reader));
        assertEquals(false, reader.readBoolean()); // carriedItem 無し
        assertEquals(0, reader.remaining());
        assertArrayEquals(original, ChunkTranslator.translateWindowItems756To755(up));
    }

    @Test
    void windowItems756To755DropsStateIdAndCarriedItem() {
        byte[] original = windowItemsPayload756(2, 11, new int[][]{{1, 64}}, new int[]{5, 3});
        byte[] down = ChunkTranslator.translateWindowItems756To755(original);
        Reader reader = new Reader(down);
        assertEquals(2, reader.readUnsignedByte()); // windowId
        assertEquals(1, reader.readShort()); // count（i16 化）
        assertArrayEquals(new int[]{1, 64}, readSlotHead(reader));
        assertEquals(0, reader.remaining()); // carriedItem は落とされる
        // 往復では stateId は 0・carriedItem は無しに正規化される。
        byte[] back = ChunkTranslator.translateWindowItems755To756(down);
        assertArrayEquals(windowItemsPayload756(2, 0, new int[][]{{1, 64}}, null), back);
    }

    // ---- Window Click ----

    @Test
    void windowClick755To756InsertsStateId() {
        // 755形式：windowId・slot・button・mode・changedSlots 1件・cursor 無し。
        Writer writer = new Writer();
        writer.writeUnsignedByte(0);
        writer.writeShort(5);
        writer.writeByte(0);
        writer.writeVarInt(1); // mode
        writer.writeVarInt(1); // changedSlots 1件
        writer.writeShort(5);
        writeSlot(writer, 1, 64);
        writer.writeBoolean(false); // cursor 無し
        byte[] click755 = writer.bytes();
        byte[] up = ChunkTranslator.translateWindowClick755To756(click755);
        Reader reader = new Reader(up);
        assertEquals(0, reader.readUnsignedByte()); // windowId
        assertEquals(0, reader.readVarInt()); // stateId（補完値 0）
        assertEquals(5, reader.readShort()); // slot
        assertEquals(0, reader.readByte()); // button
        assertEquals(1, reader.readVarInt()); // mode（varint のまま）
        assertEquals(1, reader.readVarInt()); // changedSlots の件数
        assertEquals(5, reader.readShort());
        assertArrayEquals(new int[]{1, 64}, readSlotHead(reader));
        assertEquals(false, reader.readBoolean()); // cursor 無し
        assertEquals(0, reader.remaining());
        assertArrayEquals(click755, ChunkTranslator.translateWindowClick756To755(up));
    }

    @Test
    void windowClick756To755DropsStateId() {
        byte[] original = windowClickPayload756(1, 9, 3, 1, 2, new int[][]{}, new int[]{5, 3});
        // changedSlots が空・cursor 有りの 756 ペイロードを作り直す。
        Writer writer = new Writer();
        writer.writeUnsignedByte(1);
        writer.writeVarInt(9);
        writer.writeShort(3);
        writer.writeByte(1);
        writer.writeVarInt(2); // mode QUICK_MOVE 相当
        writer.writeVarInt(0); // changedSlots 0件
        writeSlot(writer, 5, 3); // cursor
        byte[] click756 = writer.bytes();
        assertArrayEquals(original, click756);
        byte[] down = ChunkTranslator.translateWindowClick756To755(click756);
        Reader reader = new Reader(down);
        assertEquals(1, reader.readUnsignedByte()); // windowId
        assertEquals(3, reader.readShort()); // slot（stateId は無い）
        assertEquals(1, reader.readByte()); // button
        assertEquals(2, reader.readVarInt()); // mode（varint のまま）
        assertEquals(0, reader.readVarInt()); // changedSlots 0件
        assertArrayEquals(new int[]{5, 3}, readSlotHead(reader));
        assertEquals(0, reader.remaining());
        // 往復では stateId は 0 に正規化される。
        byte[] back = ChunkTranslator.translateWindowClick755To756(down);
        assertArrayEquals(windowClickPayload756(1, 0, 3, 1, 2, new int[][]{}, new int[]{5, 3}), back);
    }

    // ---- Entity Destroy ----

    @Test
    void entityDestroy755To756WrapsInArray() {
        Writer writer = new Writer();
        writer.writeVarInt(42);
        byte[] up = ChunkTranslator.translateEntityDestroy755To756(writer.bytes());
        Reader reader = new Reader(up);
        assertEquals(1, reader.readVarInt()); // count
        assertEquals(42, reader.readVarInt()); // entityId
        assertEquals(0, reader.remaining());
        assertArrayEquals(writer.bytes(), ChunkTranslator.translateEntityDestroy756To755(up));
    }

    @Test
    void entityDestroy756To755DropsMultipleAndEmpty() {
        // count 2以上は755の1パケットでは表せないため落とす（null）。
        Writer multi = new Writer();
        multi.writeVarInt(2);
        multi.writeVarInt(7);
        multi.writeVarInt(8);
        assertNull(ChunkTranslator.translateEntityDestroy756To755(multi.bytes()));
        // count 0 は消すものが無いので落として正しい（null）。
        Writer empty = new Writer();
        empty.writeVarInt(0);
        assertNull(ChunkTranslator.translateEntityDestroy756To755(empty.bytes()));
        // count 1 は単体に直る。
        Writer single = new Writer();
        single.writeVarInt(1);
        single.writeVarInt(99);
        Writer expected = new Writer();
        expected.writeVarInt(99);
        assertArrayEquals(expected.bytes(),
                ChunkTranslator.translateEntityDestroy756To755(single.bytes()));
    }
}
