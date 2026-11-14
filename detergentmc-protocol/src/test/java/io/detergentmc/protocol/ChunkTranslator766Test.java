package io.detergentmc.protocol;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.detergentmc.protocol.Nbt.Tag;
import io.detergentmc.protocol.PacketIO.Reader;
import io.detergentmc.protocol.PacketIO.Writer;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * {@link ChunkTranslator} の766（1.20.5／1.20.6）向け変換テスト。
 * 765形式と766形式のペイロードを合成して変換し、読み直して中身を確認する。
 * 766のスロットはコンポーネント形式のため、765の NBT は落とし、
 * 766のコンポーネント付きスロットはパケットごと落とす。
 */
class ChunkTranslator766Test {

    // ---- ヘルパー ----

    /** 高さマップ（名前なし NBT のコンパウンド）。 */
    private static Tag heightmaps() {
        return new Tag.CompoundTag(Map.of(
                "MOTION_BLOCKING", new Tag.LongArrayTag(new long[37]),
                "WORLD_SURFACE", new Tag.LongArrayTag(new long[37])));
    }

    /** スロット（765形式：bool＋アイテムID＋個数＋名前なし NBT）。 */
    private static void writeSlot765(Writer writer, int itemId, int count, Tag nbt) {
        writer.writeBoolean(true);
        writer.writeVarInt(itemId);
        writer.writeByte(count);
        if (nbt == null) {
            writer.writeByte(0);
        } else {
            writer.writeAnonymousNbt(nbt);
        }
    }

    /** 空きスロット（765形式）。 */
    private static void writeEmptySlot765(Writer writer) {
        writer.writeBoolean(false);
    }

    /** スロット（766形式：個数 i8＋アイテムID＋追加数＋削除数）。 */
    private static void writeSlot766(Writer writer, int itemId, int count) {
        writer.writeByte(count);
        writer.writeVarInt(itemId);
        writer.writeVarInt(0); // 追加コンポーネント 0
        writer.writeVarInt(0); // 削除コンポーネント 0
    }

    /** コンポーネント付きスロット（766形式：追加／削除の数を指定）。 */
    private static void writeSlot766WithComponents(Writer writer, int itemId, int count,
            int added, int removed) {
        writer.writeByte(count);
        writer.writeVarInt(itemId);
        writer.writeVarInt(added);
        writer.writeVarInt(removed);
    }

    /** 空きスロット（766形式：個数 0）。 */
    private static void writeEmptySlot766(Writer writer) {
        writer.writeByte(0);
    }

    /** 765形式のスロットを読み、{present, itemId, count} を返す。 */
    private static int[] readSlot765(Reader reader) {
        boolean present = reader.readBoolean();
        if (!present) {
            return new int[]{0, 0, 0};
        }
        int itemId = reader.readVarInt();
        int count = reader.readByte();
        reader.readAnonymousNbt();
        return new int[]{1, itemId, count};
    }

    /** 766形式のスロットを読み、{count, itemId, added, removed} を返す。 */
    private static int[] readSlot766(Reader reader) {
        int count = reader.readByte();
        if (count == 0) {
            return new int[]{0, 0, 0, 0};
        }
        int itemId = reader.readVarInt();
        int added = reader.readVarInt();
        int removed = reader.readVarInt();
        return new int[]{count, itemId, added, removed};
    }

    /** 間接パレットのセクション1個（ブロック＋バイオーム）。 */
    private static byte[] indirectSection(int[] blockPalette, int biomeId) {
        Writer writer = new Writer();
        writer.writeShort(1); // ブロック数
        writer.writeUnsignedByte(4); // BPE（間接）
        writer.writeVarInt(blockPalette.length);
        for (int value : blockPalette) {
            writer.writeVarInt(value);
        }
        writer.writeVarInt(256); // Data Array：4096エントリ×4bit＝256 long
        for (int i = 0; i < 256; i++) {
            writer.writeLong(0L);
        }
        writer.writeUnsignedByte(0); // バイオームはシングル
        writer.writeVarInt(biomeId);
        writer.writeVarInt(0); // Data Array 長 0
        return writer.bytes();
    }

    /** セクションのパレットだけ読み、ブロック状態IDの配列を返す。 */
    private static int[] readIndirectPalette(byte[] sections) {
        Reader section = new Reader(sections);
        section.readShort();
        section.readUnsignedByte();
        int paletteSize = section.readVarInt();
        int[] palette = new int[paletteSize];
        for (int i = 0; i < paletteSize; i++) {
            palette[i] = section.readVarInt();
        }
        return palette;
    }

    // ---- Chunk Data ----

    @Test
    void chunkData765To766RemapsTrialSpawner() {
        Writer payload = new Writer();
        payload.writeInt(3);
        payload.writeInt(-2);
        payload.writeAnonymousNbt(heightmaps());
        payload.writeByteArray(indirectSection(new int[]{0, 1, 26638, 26643}, 5));
        payload.writeVarInt(1);
        payload.writeUnsignedByte(0x11);
        payload.writeShort(64);
        payload.writeVarInt(1); // chest
        payload.writeByte(0); // NBT 無し
        payload.writeBytes(new byte[]{0x0A, 0x0B, 0x0C}); // 光データ

        byte[] translated = ChunkTranslator.translateChunkData765To766(payload.bytes());
        Reader reader = new Reader(translated);
        assertEquals(3, reader.readInt());
        assertEquals(-2, reader.readInt());
        assertTrue(reader.readAnonymousNbt().tag() instanceof Tag.CompoundTag);
        byte[] sections = reader.readBytes(reader.readVarInt());
        assertArrayEquals(new int[]{0, 1, 26644, 26649}, readIndirectPalette(sections));
        assertEquals(1, reader.readVarInt()); // ブロックエンティティはチェストだけ
        assertEquals(0x11, reader.readUnsignedByte());
        assertEquals(64, reader.readShort());
        assertEquals(1, reader.readVarInt());
        assertEquals(0, reader.readByte()); // NBT 無し（0x00）
        assertArrayEquals(new byte[]{0x0A, 0x0B, 0x0C}, reader.rest());
    }

    @Test
    void chunkData766To765DropsVaultAndUnOminous() {
        Writer payload = new Writer();
        payload.writeInt(0);
        payload.writeInt(0);
        payload.writeAnonymousNbt(heightmaps());
        payload.writeByteArray(indirectSection(new int[]{0, 1, 26644, 26650}, 5));
        payload.writeVarInt(2);
        payload.writeUnsignedByte(0x11);
        payload.writeShort(64);
        payload.writeVarInt(1); // chest
        payload.writeByte(0);
        payload.writeUnsignedByte(0x22);
        payload.writeShort(65);
        payload.writeVarInt(43); // vault（766で新規）
        payload.writeByte(0);

        byte[] translated = ChunkTranslator.translateChunkData766To765(payload.bytes());
        Reader reader = new Reader(translated);
        reader.readInt();
        reader.readInt();
        reader.readAnonymousNbt();
        byte[] sections = reader.readBytes(reader.readVarInt());
        // trial_spawner（ominous=false）→ 765の 26638、vault → trial_spawner 既定 26638。
        assertArrayEquals(new int[]{0, 1, 26638, 26638}, readIndirectPalette(sections));
        assertEquals(1, reader.readVarInt()); // vault のエントリは落とされる
        assertEquals(0x11, reader.readUnsignedByte());
        assertEquals(64, reader.readShort());
        assertEquals(1, reader.readVarInt());
    }

    // ---- スロット ----

    @Test
    void setSlot765To766KeepsIdAndCountDropsNbt() {
        Writer payload = new Writer();
        payload.writeByte(0); // ウィンドウID
        payload.writeVarInt(7); // stateId
        payload.writeShort(10); // スロット
        writeSlot765(payload, 794, 3, // scute ×3、NBT 付き（エンチャント等を想定）
                new Tag.CompoundTag(Map.of("display",
                        new Tag.CompoundTag(Map.of("Name", new Tag.StringTag("test"))))));

        byte[] translated = ChunkTranslator.translateSetSlot765To766(payload.bytes());
        Reader reader = new Reader(translated);
        assertEquals(0, reader.readByte());
        assertEquals(7, reader.readVarInt());
        assertEquals(10, reader.readShort());
        int[] slot = readSlot766(reader);
        assertEquals(3, slot[0]); // 個数
        assertEquals(795, slot[1]); // scute → turtle_scute
        assertEquals(0, slot[2]); // 追加コンポーネント無し
        assertEquals(0, slot[3]); // 削除コンポーネント無し
        assertEquals(0, reader.remaining());
    }

    @Test
    void setSlot765To766EmptySlot() {
        Writer payload = new Writer();
        payload.writeByte(0);
        payload.writeVarInt(0);
        payload.writeShort(0);
        writeEmptySlot765(payload);

        byte[] translated = ChunkTranslator.translateSetSlot765To766(payload.bytes());
        Reader reader = new Reader(translated);
        reader.readByte();
        reader.readVarInt();
        reader.readShort();
        assertEquals(0, readSlot766(reader)[0]); // 個数 0＝空き
        assertEquals(0, reader.remaining());
    }

    @Test
    void setSlot766To765KeepsIdAndCount() {
        Writer payload = new Writer();
        payload.writeByte(0);
        payload.writeVarInt(7);
        payload.writeShort(10);
        writeSlot766(payload, 795, 2); // turtle_scute ×2

        byte[] translated = ChunkTranslator.translateSetSlot766To765(payload.bytes());
        Reader reader = new Reader(translated);
        assertEquals(0, reader.readByte());
        assertEquals(7, reader.readVarInt());
        assertEquals(10, reader.readShort());
        int[] slot = readSlot765(reader);
        assertEquals(1, slot[0]); // あり
        assertEquals(794, slot[1]); // turtle_scute → scute
        assertEquals(2, slot[2]); // 個数
        assertEquals(0, reader.remaining());
    }

    @Test
    void setSlot766To765DropsComponentSlot() {
        Writer payload = new Writer();
        payload.writeByte(0);
        payload.writeVarInt(7);
        payload.writeShort(10);
        writeSlot766WithComponents(payload, 1, 1, 1, 0); // 追加コンポーネント 1個

        assertNull(ChunkTranslator.translateSetSlot766To765(payload.bytes()));
    }

    @Test
    void setSlot766To765EmptySlot() {
        Writer payload = new Writer();
        payload.writeByte(0);
        payload.writeVarInt(0);
        payload.writeShort(0);
        writeEmptySlot766(payload);

        byte[] translated = ChunkTranslator.translateSetSlot766To765(payload.bytes());
        Reader reader = new Reader(translated);
        reader.readByte();
        reader.readVarInt();
        reader.readShort();
        assertEquals(0, readSlot765(reader)[0]); // 空き
        assertEquals(0, reader.remaining());
    }

    @Test
    void windowItems765To766AndBack() {
        Writer payload = new Writer();
        payload.writeUnsignedByte(1);
        payload.writeVarInt(3);
        payload.writeVarInt(2);
        writeSlot765(payload, 1, 64, null); // stone ×64
        writeSlot765(payload, 794, 1, null); // scute ×1
        writeEmptySlot765(payload); // カーソルは空き

        byte[] to766 = ChunkTranslator.translateWindowItems765To766(payload.bytes());
        Reader reader = new Reader(to766);
        assertEquals(1, reader.readUnsignedByte());
        assertEquals(3, reader.readVarInt());
        assertEquals(2, reader.readVarInt());
        assertArrayEquals(new int[]{64, 1, 0, 0}, readSlot766(reader));
        assertArrayEquals(new int[]{1, 795, 0, 0}, readSlot766(reader));
        assertEquals(0, readSlot766(reader)[0]);
        assertEquals(0, reader.remaining());

        byte[] back = ChunkTranslator.translateWindowItems766To765(to766);
        Reader backReader = new Reader(back);
        assertEquals(1, backReader.readUnsignedByte());
        assertEquals(3, backReader.readVarInt());
        assertEquals(2, backReader.readVarInt());
        assertArrayEquals(new int[]{1, 1, 64}, readSlot765(backReader));
        assertArrayEquals(new int[]{1, 794, 1}, readSlot765(backReader));
        assertEquals(0, readSlot765(backReader)[0]);
        assertEquals(0, backReader.remaining());
    }

    // ---- ブロック・スポーン ----

    @Test
    void blockChange765To766AndBack() {
        Writer payload = new Writer();
        payload.writePositionRaw(0x123456789ABCDEFL);
        payload.writeVarInt(26638); // trial_spawner

        byte[] to766 = ChunkTranslator.translateBlockChange765To766(payload.bytes());
        Reader reader = new Reader(to766);
        assertEquals(0x123456789ABCDEFL, reader.readPositionRaw());
        assertEquals(26644, reader.readVarInt());
        assertEquals(0, reader.remaining());

        Writer back = new Writer();
        back.writePositionRaw(0x123456789ABCDEFL);
        back.writeVarInt(26650); // vault
        byte[] to765 = ChunkTranslator.translateBlockChange766To765(back.bytes());
        Reader backReader = new Reader(to765);
        assertEquals(0x123456789ABCDEFL, backReader.readPositionRaw());
        assertEquals(26638, backReader.readVarInt()); // vault → trial_spawner 既定
    }

    @Test
    void multiBlockChange765To766() {
        Writer payload = new Writer();
        payload.writeLong(0x1122334455667788L);
        payload.writeVarInt(2);
        payload.writeVarInt((26638 << 12) | 0x005);
        payload.writeVarInt((1 << 12) | 0xABC);

        byte[] translated = ChunkTranslator.translateMultiBlockChange765To766(payload.bytes());
        Reader reader = new Reader(translated);
        assertEquals(0x1122334455667788L, reader.readLong());
        assertEquals(2, reader.readVarInt());
        assertEquals((26644 << 12) | 0x005, reader.readVarInt());
        assertEquals((1 << 12) | 0xABC, reader.readVarInt());
        assertEquals(0, reader.remaining());
    }

    @Test
    void spawnEntityFallingBlock765To766AndBack() {
        Writer payload = new Writer();
        payload.writeVarInt(5); // entityId
        payload.writeBytes(new byte[16]); // UUID
        payload.writeVarInt(37); // falling_block（765）
        payload.writeDouble(1.5);
        payload.writeDouble(64.0);
        payload.writeDouble(-2.5);
        payload.writeByte(0);
        payload.writeByte(0);
        payload.writeByte(0);
        payload.writeVarInt(26638); // objectData：trial_spawner の状態
        payload.writeShort(0);
        payload.writeShort(0);
        payload.writeShort(0);

        byte[] to766 = ChunkTranslator.translateSpawnEntity765To766(payload.bytes());
        Reader reader = new Reader(to766);
        assertEquals(5, reader.readVarInt());
        reader.readBytes(16);
        assertEquals(40, reader.readVarInt()); // falling_block（766）
        assertEquals(1.5, reader.readDouble());
        assertEquals(64.0, reader.readDouble());
        assertEquals(-2.5, reader.readDouble());
        reader.readByte();
        reader.readByte();
        reader.readByte();
        assertEquals(26644, reader.readVarInt()); // objectData も変換
        assertEquals(0, reader.readShort()); // velX
        assertEquals(0, reader.readShort()); // velY
        assertEquals(0, reader.readShort()); // velZ
        assertEquals(0, reader.remaining());

        Writer back = new Writer();
        back.writeVarInt(5);
        back.writeBytes(new byte[16]);
        back.writeVarInt(40); // falling_block（766）
        back.writeDouble(1.5);
        back.writeDouble(64.0);
        back.writeDouble(-2.5);
        back.writeByte(0);
        back.writeByte(0);
        back.writeByte(0);
        back.writeVarInt(26644);
        back.writeShort(0);
        back.writeShort(0);
        back.writeShort(0);
        byte[] to765 = ChunkTranslator.translateSpawnEntity766To765(back.bytes());
        Reader backReader = new Reader(to765);
        assertEquals(5, backReader.readVarInt());
        backReader.readBytes(16);
        assertEquals(37, backReader.readVarInt());
        backReader.readDouble();
        backReader.readDouble();
        backReader.readDouble();
        backReader.readByte();
        backReader.readByte();
        backReader.readByte();
        assertEquals(26638, backReader.readVarInt());
    }

    @Test
    void spawnEntityPig765To766AndBack() {
        Writer payload = new Writer();
        payload.writeVarInt(9);
        payload.writeBytes(new byte[16]);
        payload.writeVarInt(73); // pig（765）
        payload.writeDouble(0.0);
        payload.writeDouble(0.0);
        payload.writeDouble(0.0);
        payload.writeByte(0);
        payload.writeByte(0);
        payload.writeByte(0);
        payload.writeVarInt(0);
        payload.writeShort(0);
        payload.writeShort(0);
        payload.writeShort(0);

        byte[] to766 = ChunkTranslator.translateSpawnEntity765To766(payload.bytes());
        Reader reader = new Reader(to766);
        assertEquals(9, reader.readVarInt());
        reader.readBytes(16);
        assertEquals(77, reader.readVarInt()); // pig（766）

        Writer back = new Writer();
        back.writeVarInt(9);
        back.writeBytes(new byte[16]);
        back.writeVarInt(2); // armadillo（766）
        back.writeDouble(0.0);
        back.writeDouble(0.0);
        back.writeDouble(0.0);
        back.writeByte(0);
        back.writeByte(0);
        back.writeByte(0);
        back.writeVarInt(0);
        back.writeShort(0);
        back.writeShort(0);
        back.writeShort(0);
        byte[] to765 = ChunkTranslator.translateSpawnEntity766To765(back.bytes());
        Reader backReader = new Reader(to765);
        assertEquals(9, backReader.readVarInt());
        backReader.readBytes(16);
        assertEquals(73, backReader.readVarInt()); // armadillo → pig
    }

    @Test
    void blockEntityData766To765DropsVault() {
        Writer payload = new Writer();
        payload.writePositionRaw(0x123456789ABCDEFL);
        payload.writeVarInt(43); // vault
        payload.writeByte(0); // NBT 無し

        assertNull(ChunkTranslator.translateBlockEntityData766To765(payload.bytes()));

        Writer ok = new Writer();
        ok.writePositionRaw(0x123456789ABCDEFL);
        ok.writeVarInt(42); // trial_spawner
        ok.writeByte(0);
        byte[] translated = ChunkTranslator.translateBlockEntityData766To765(ok.bytes());
        Reader reader = new Reader(translated);
        assertEquals(0x123456789ABCDEFL, reader.readPositionRaw());
        assertEquals(42, reader.readVarInt());
        assertEquals(0, reader.readByte()); // NBT 無し（0x00）
        assertEquals(0, reader.remaining());
    }

    @Test
    void blockAction765To766AndBack() {
        Writer payload = new Writer();
        payload.writePositionRaw(0x123456789ABCDEFL);
        payload.writeUnsignedByte(1);
        payload.writeUnsignedByte(2);
        payload.writeVarInt(1057); // trial_spawner

        byte[] to766 = ChunkTranslator.translateBlockAction765To766(payload.bytes());
        Reader reader = new Reader(to766);
        assertEquals(0x123456789ABCDEFL, reader.readPositionRaw());
        assertEquals(1, reader.readUnsignedByte());
        assertEquals(2, reader.readUnsignedByte());
        assertEquals(1057, reader.readVarInt());

        Writer back = new Writer();
        back.writePositionRaw(0x123456789ABCDEFL);
        back.writeUnsignedByte(1);
        back.writeUnsignedByte(2);
        back.writeVarInt(1058); // vault
        byte[] to765 = ChunkTranslator.translateBlockAction766To765(back.bytes());
        Reader backReader = new Reader(to765);
        backReader.readPositionRaw();
        backReader.readUnsignedByte();
        backReader.readUnsignedByte();
        assertEquals(1, backReader.readVarInt()); // vault → stone
    }

    // ---- パーティクル ----

    @Test
    void worldParticles765To766AmbientBecomesEntityEffect() {
        Writer payload = new Writer();
        payload.writeVarInt(0); // ambient_entity_effect（765）
        payload.writeBoolean(false); // longDistance
        payload.writeDouble(1.0);
        payload.writeDouble(2.0);
        payload.writeDouble(3.0);
        payload.writeFloat(0.1f);
        payload.writeFloat(0.2f);
        payload.writeFloat(0.3f);
        payload.writeFloat(0.5f); // speed
        payload.writeInt(10); // count

        byte[] translated = ChunkTranslator.translateWorldParticles765To766(payload.bytes());
        Reader reader = new Reader(translated);
        assertEquals(false, reader.readBoolean());
        assertEquals(1.0, reader.readDouble());
        assertEquals(2.0, reader.readDouble());
        assertEquals(3.0, reader.readDouble());
        assertEquals(0.1f, reader.readFloat());
        assertEquals(0.2f, reader.readFloat());
        assertEquals(0.3f, reader.readFloat());
        assertEquals(0.5f, reader.readFloat());
        assertEquals(10, reader.readInt());
        assertEquals(20, reader.readVarInt()); // entity_effect（766）
        assertEquals(0, reader.readInt()); // 色は合成（765に情報が無い）
        assertEquals(0, reader.remaining());
    }

    @Test
    void worldParticles766To765EntityEffectStaysEntityEffect() {
        Writer payload = new Writer();
        payload.writeBoolean(true);
        payload.writeDouble(1.0);
        payload.writeDouble(2.0);
        payload.writeDouble(3.0);
        payload.writeFloat(0.1f);
        payload.writeFloat(0.2f);
        payload.writeFloat(0.3f);
        payload.writeFloat(0.5f);
        payload.writeInt(10);
        payload.writeVarInt(20); // entity_effect（766）
        payload.writeInt(0xFF0000); // 色（765では落とす）

        byte[] translated = ChunkTranslator.translateWorldParticles766To765(payload.bytes());
        Reader reader = new Reader(translated);
        // 766の entity_effect（20）は 765の entity_effect（21）になる。
        // 765の ambient_entity_effect（0）には戻らない（同名の 21 を選ぶ）。
        assertEquals(21, reader.readVarInt());
        assertEquals(true, reader.readBoolean());
        assertEquals(1.0, reader.readDouble());
        assertEquals(2.0, reader.readDouble());
        assertEquals(3.0, reader.readDouble());
        assertEquals(0.1f, reader.readFloat());
        assertEquals(0.2f, reader.readFloat());
        assertEquals(0.3f, reader.readFloat());
        assertEquals(0.5f, reader.readFloat());
        assertEquals(10, reader.readInt());
        assertEquals(0, reader.remaining());
    }

    @Test
    void worldParticles765To766EntityEffectSynthesizesColor() {
        // 765の entity_effect（21）には色のデータが無い。766の
        // entity_effect（20）は i32 が要るため 0 を合成する。
        Writer payload = new Writer();
        payload.writeVarInt(21); // entity_effect（765）
        payload.writeBoolean(false);
        payload.writeDouble(1.0);
        payload.writeDouble(2.0);
        payload.writeDouble(3.0);
        payload.writeFloat(0.1f);
        payload.writeFloat(0.2f);
        payload.writeFloat(0.3f);
        payload.writeFloat(0.5f);
        payload.writeInt(10);

        byte[] translated = ChunkTranslator.translateWorldParticles765To766(payload.bytes());
        Reader reader = new Reader(translated);
        assertEquals(false, reader.readBoolean());
        assertEquals(1.0, reader.readDouble());
        assertEquals(2.0, reader.readDouble());
        assertEquals(3.0, reader.readDouble());
        assertEquals(0.1f, reader.readFloat());
        assertEquals(0.2f, reader.readFloat());
        assertEquals(0.3f, reader.readFloat());
        assertEquals(0.5f, reader.readFloat());
        assertEquals(10, reader.readInt());
        assertEquals(20, reader.readVarInt()); // entity_effect（766）
        assertEquals(0, reader.readInt()); // 合成した色
        assertEquals(0, reader.remaining());
    }

    @Test
    void worldParticlesVibration765To766AndBack() {
        // 765：vibration（43）は [種別文字列][block なら位置][tick 数]。
        // 765のペイロードは [粒子][longDistance][座標]…の順。
        Writer correct = new Writer();
        correct.writeVarInt(43); // vibration（765）
        correct.writeString("minecraft:block");
        correct.writePositionRaw(0x123456789ABCDEFL);
        correct.writeVarInt(20); // tick 数
        correct.writeBoolean(false);
        correct.writeDouble(0.0);
        correct.writeDouble(0.0);
        correct.writeDouble(0.0);
        correct.writeFloat(0.0f);
        correct.writeFloat(0.0f);
        correct.writeFloat(0.0f);
        correct.writeFloat(0.0f);
        correct.writeInt(1);

        byte[] to766 = ChunkTranslator.translateWorldParticles765To766(correct.bytes());
        Reader reader = new Reader(to766);
        assertEquals(false, reader.readBoolean());
        reader.readDouble();
        reader.readDouble();
        reader.readDouble();
        reader.readFloat();
        reader.readFloat();
        reader.readFloat();
        reader.readFloat();
        assertEquals(1, reader.readInt());
        assertEquals(45, reader.readVarInt()); // vibration（766）
        assertEquals(0, reader.readVarInt()); // 位置種別 block
        assertEquals(0x123456789ABCDEFL, reader.readPositionRaw());
        assertEquals(20, reader.readVarInt());
        assertEquals(0, reader.remaining());

        // 逆向き：766の vibration（block）→ 765。
        Writer back = new Writer();
        back.writeBoolean(false);
        back.writeDouble(0.0);
        back.writeDouble(0.0);
        back.writeDouble(0.0);
        back.writeFloat(0.0f);
        back.writeFloat(0.0f);
        back.writeFloat(0.0f);
        back.writeFloat(0.0f);
        back.writeInt(1);
        back.writeVarInt(45);
        back.writeVarInt(0);
        back.writePositionRaw(0x123456789ABCDEFL);
        back.writeVarInt(20);
        byte[] to765 = ChunkTranslator.translateWorldParticles766To765(back.bytes());
        Reader backReader = new Reader(to765);
        assertEquals(43, backReader.readVarInt());
        assertEquals("minecraft:block", backReader.readString());
        assertEquals(0x123456789ABCDEFL, backReader.readPositionRaw());
        assertEquals(20, backReader.readVarInt());
        assertEquals(false, backReader.readBoolean());
        assertEquals(0.0, backReader.readDouble());
        assertEquals(0.0, backReader.readDouble());
        assertEquals(0.0, backReader.readDouble());
        assertEquals(0.0f, backReader.readFloat());
        assertEquals(0.0f, backReader.readFloat());
        assertEquals(0.0f, backReader.readFloat());
        assertEquals(0.0f, backReader.readFloat());
        assertEquals(1, backReader.readInt());
        assertEquals(0, backReader.remaining());
    }

    // ---- メタデータ ----

    @Test
    void entityMetadata765To766RenumberAndDropHolderInline() {
        Writer payload = new Writer();
        payload.writeVarInt(7); // entityId
        payload.writeUnsignedByte(0);
        payload.writeVarInt(28); // vector3（765）
        payload.writeFloat(1.0f);
        payload.writeFloat(2.0f);
        payload.writeFloat(3.0f);
        payload.writeUnsignedByte(1);
        payload.writeVarInt(23); // wolf holder（番号）
        payload.writeVarInt(1); // pale
        payload.writeUnsignedByte(2);
        payload.writeVarInt(23); // wolf holder（直書き NBT）
        payload.writeVarInt(0);
        payload.writeAnonymousNbt(new Tag.CompoundTag(Map.of()));
        payload.writeUnsignedByte(0xFF);

        byte[] translated = ChunkTranslator.translateEntityMetadata765To766(payload.bytes());
        Reader reader = new Reader(translated);
        assertEquals(7, reader.readVarInt());
        assertEquals(0, reader.readUnsignedByte());
        assertEquals(29, reader.readVarInt()); // vector3 → 29（766）
        assertEquals(1.0f, reader.readFloat());
        assertEquals(2.0f, reader.readFloat());
        assertEquals(3.0f, reader.readFloat());
        assertEquals(1, reader.readUnsignedByte());
        assertEquals(23, reader.readVarInt());
        assertEquals(1, reader.readVarInt());
        assertEquals(0xFF, reader.readUnsignedByte()); // 直書きのエントリは落とされた
        assertEquals(0, reader.remaining());
    }

    @Test
    void entityMetadata766To765DropsArmadilloAndClampsWolf() {
        Writer payload = new Writer();
        payload.writeVarInt(7);
        payload.writeUnsignedByte(0);
        payload.writeVarInt(28); // armadillo_state（766）
        payload.writeVarInt(2); // idle
        payload.writeUnsignedByte(1);
        payload.writeVarInt(29); // vector3（766）
        payload.writeFloat(1.0f);
        payload.writeFloat(2.0f);
        payload.writeFloat(3.0f);
        payload.writeUnsignedByte(2);
        payload.writeVarInt(23); // wolf holder
        payload.writeVarInt(4); // 766の wolf（snowy 等）
        payload.writeUnsignedByte(0xFF);

        byte[] translated = ChunkTranslator.translateEntityMetadata766To765(payload.bytes());
        Reader reader = new Reader(translated);
        assertEquals(7, reader.readVarInt());
        assertEquals(1, reader.readUnsignedByte()); // armadillo のエントリは落とされた
        assertEquals(28, reader.readVarInt()); // vector3 → 28（765）
        assertEquals(1.0f, reader.readFloat());
        assertEquals(2.0f, reader.readFloat());
        assertEquals(3.0f, reader.readFloat());
        assertEquals(2, reader.readUnsignedByte());
        assertEquals(23, reader.readVarInt());
        assertEquals(1, reader.readVarInt()); // 765には pale しかないため 1 にする
        assertEquals(0xFF, reader.readUnsignedByte());
        assertEquals(0, reader.remaining());
    }

    @Test
    void entityMetadata766To765DropsPacketOnHolderInline() {
        Writer payload = new Writer();
        payload.writeVarInt(7);
        payload.writeUnsignedByte(0);
        payload.writeVarInt(23); // wolf holder（直書き＝構造化データ）
        payload.writeVarInt(0);
        payload.writeUnsignedByte(0xFF);

        // 直書きの構造化データは読み飛ばせないため、パケットごと落とす。
        assertNull(ChunkTranslator.translateEntityMetadata766To765(payload.bytes()));
    }

    // ---- 音・エフェクト ----

    @Test
    void soundEffect765To766AndBack() {
        // holder 0（直書きの名前）。
        Writer payload = new Writer();
        payload.writeVarInt(0);
        payload.writeString("entity.generic.wind_burst");
        payload.writeBoolean(false);
        payload.writeVarInt(1); // category
        payload.writeInt(0);
        payload.writeInt(64);
        payload.writeInt(0);
        payload.writeFloat(1.0f);
        payload.writeFloat(1.0f);
        payload.writeLong(12345L);

        byte[] to766 = ChunkTranslator.translateSoundEffect765To766(payload.bytes());
        Reader reader = new Reader(to766);
        assertEquals(0, reader.readVarInt());
        assertEquals("entity.wind_charge.wind_burst", reader.readString());
        assertEquals(false, reader.readBoolean());
        assertEquals(1, reader.readVarInt());

        // holder N（番号）：stone.break（765の 1312 → holder 1313）。
        Writer numbered = new Writer();
        numbered.writeVarInt(1313);
        numbered.writeVarInt(1);
        numbered.writeInt(0);
        numbered.writeInt(64);
        numbered.writeInt(0);
        numbered.writeFloat(1.0f);
        numbered.writeFloat(1.0f);
        numbered.writeLong(12345L);
        byte[] to766n = ChunkTranslator.translateSoundEffect765To766(numbered.bytes());
        Reader readerN = new Reader(to766n);
        assertEquals(1359, readerN.readVarInt()); // stone.break（766の 1358 → holder 1359）

        // 逆向き：766の holder 1359 → 765の holder 1313。
        Writer back = new Writer();
        back.writeVarInt(1359);
        back.writeVarInt(1);
        back.writeInt(0);
        back.writeInt(64);
        back.writeInt(0);
        back.writeFloat(1.0f);
        back.writeFloat(1.0f);
        back.writeLong(12345L);
        byte[] to765 = ChunkTranslator.translateSoundEffect766To765(back.bytes());
        Reader backReader = new Reader(to765);
        assertEquals(1313, backReader.readVarInt());
    }

    @Test
    void stopSound765To766AndBack() {
        Writer payload = new Writer();
        payload.writeByte(0x03); // source＋name
        payload.writeVarInt(2); // source
        payload.writeString("entity.generic.wind_burst");

        byte[] to766 = ChunkTranslator.translateStopSound765To766(payload.bytes());
        Reader reader = new Reader(to766);
        assertEquals(0x03, reader.readByte());
        assertEquals(2, reader.readVarInt());
        assertEquals("entity.wind_charge.wind_burst", reader.readString());
        assertEquals(0, reader.remaining());

        Writer back = new Writer();
        back.writeByte(0x03);
        back.writeVarInt(2);
        back.writeString("entity.wind_charge.wind_burst");
        byte[] to765 = ChunkTranslator.translateStopSound766To765(back.bytes());
        Reader backReader = new Reader(to765);
        assertEquals(0x03, backReader.readByte());
        assertEquals(2, backReader.readVarInt());
        assertEquals("entity.generic.wind_burst", backReader.readString());
    }

    @Test
    void entityEffect765To766AndBack() {
        Writer payload = new Writer();
        payload.writeVarInt(7); // entityId
        payload.writeVarInt(0); // Speed
        payload.writeByte(2); // amplifier（i8）
        payload.writeVarInt(100); // duration
        payload.writeByte(0); // flags
        payload.writeBoolean(false); // factorCodec 無し

        byte[] to766 = ChunkTranslator.translateEntityEffect765To766(payload.bytes());
        Reader reader = new Reader(to766);
        assertEquals(7, reader.readVarInt());
        assertEquals(0, reader.readVarInt());
        assertEquals(2, reader.readVarInt()); // amplifier は VarInt へ
        assertEquals(100, reader.readVarInt());
        assertEquals(0, reader.readByte());
        assertEquals(0, reader.remaining());

        // 逆向き：766の TrialOmen（33）→ 765の Speed（0）。
        Writer back = new Writer();
        back.writeVarInt(7);
        back.writeVarInt(33); // TrialOmen
        back.writeVarInt(2); // amplifier（VarInt）
        back.writeVarInt(100);
        back.writeByte(0);
        byte[] to765 = ChunkTranslator.translateEntityEffect766To765(back.bytes());
        Reader backReader = new Reader(to765);
        assertEquals(7, backReader.readVarInt());
        assertEquals(0, backReader.readVarInt());
        assertEquals(2, backReader.readByte());
        assertEquals(100, backReader.readVarInt());
        assertEquals(0, backReader.readByte());
        assertEquals(false, backReader.readBoolean()); // factorCodec 無し
        assertEquals(0, backReader.remaining());
    }

    // ---- その他 ----

    @Test
    void openWindow765To766AndBack() {
        Writer payload = new Writer();
        payload.writeVarInt(1); // windowId
        payload.writeVarInt(12); // crafting
        payload.writeAnonymousNbt(new Tag.StringTag("Test"));

        byte[] to766 = ChunkTranslator.translateOpenWindow765To766(payload.bytes());
        Reader reader = new Reader(to766);
        assertEquals(1, reader.readVarInt());
        assertEquals(12, reader.readVarInt());
        assertEquals("Test", ((Tag.StringTag) reader.readAnonymousNbt().tag()).value());

        Writer back = new Writer();
        back.writeVarInt(1);
        back.writeVarInt(7); // crafter_3x3
        back.writeAnonymousNbt(new Tag.StringTag("Test"));
        byte[] to765 = ChunkTranslator.translateOpenWindow766To765(back.bytes());
        Reader backReader = new Reader(to765);
        assertEquals(1, backReader.readVarInt());
        assertEquals(7, backReader.readVarInt()); // crafter_3x3 は 765にもある
    }

    @Test
    void tradeList765To766AndBack() {
        Writer payload = new Writer();
        payload.writeVarInt(1); // windowId
        payload.writeVarInt(1); // 取引 1件
        writeSlot765(payload, 1, 1, null); // 入力1：stone ×1
        writeSlot765(payload, 100, 1, null); // 出力：アイテム 100 ×1
        payload.writeBoolean(false); // 入力2 無し
        payload.writeBoolean(false);
        payload.writeInt(0);
        payload.writeInt(0);
        payload.writeInt(0);
        payload.writeInt(0);
        payload.writeFloat(0.0f);
        payload.writeInt(0);
        payload.writeVarInt(1);
        payload.writeVarInt(0);
        payload.writeBoolean(false);
        payload.writeBoolean(false);

        byte[] to766 = ChunkTranslator.translateTradeList765To766(payload.bytes());
        Reader reader = new Reader(to766);
        assertEquals(1, reader.readVarInt());
        assertEquals(1, reader.readVarInt());
        assertEquals(1, reader.readVarInt()); // 入力1：ItemCost の id
        assertEquals(1, reader.readVarInt()); // 入力1：個数
        assertEquals(0, reader.readVarInt()); // 入力1：追加 0
        int[] out = readSlot766(reader);
        assertEquals(1, out[0]);
        assertEquals(101, out[1]); // exposed_cut_copper：heavy_core（85）の挿入で1つ後ろへ
        assertEquals(false, reader.readBoolean()); // 入力2 無し
        assertEquals(false, reader.readBoolean());
        assertEquals(0, reader.readInt());
        assertEquals(0, reader.readInt());
        assertEquals(0, reader.readInt());
        assertEquals(0, reader.readInt());
        assertEquals(0.0f, reader.readFloat());
        assertEquals(0, reader.readInt());
        assertEquals(1, reader.readVarInt());
        assertEquals(0, reader.readVarInt());
        assertEquals(false, reader.readBoolean());
        assertEquals(false, reader.readBoolean());
        assertEquals(0, reader.remaining());

        byte[] back = ChunkTranslator.translateTradeList766To765(to766);
        Reader backReader = new Reader(back);
        assertEquals(1, backReader.readVarInt());
        assertEquals(1, backReader.readVarInt());
        assertArrayEquals(new int[]{1, 1, 1}, readSlot765(backReader));
        assertArrayEquals(new int[]{1, 100, 1}, readSlot765(backReader));
        assertEquals(false, backReader.readBoolean()); // 入力2 無し
        assertEquals(false, backReader.readBoolean());
        assertEquals(0, backReader.readInt());
        assertEquals(0, backReader.readInt());
        assertEquals(0, backReader.readInt());
        assertEquals(0, backReader.readInt());
        assertEquals(0.0f, backReader.readFloat());
        assertEquals(0, backReader.readInt());
        assertEquals(1, backReader.readVarInt());
        assertEquals(0, backReader.readVarInt());
        assertEquals(false, backReader.readBoolean());
        assertEquals(false, backReader.readBoolean());
        assertEquals(0, backReader.remaining());
    }

    @Test
    void setCooldown765To766AndBack() {
        Writer payload = new Writer();
        payload.writeVarInt(794); // scute
        payload.writeVarInt(20);

        byte[] to766 = ChunkTranslator.translateSetCooldown765To766(payload.bytes());
        Reader reader = new Reader(to766);
        assertEquals(795, reader.readVarInt()); // turtle_scute
        assertEquals(20, reader.readVarInt());

        Writer back = new Writer();
        back.writeVarInt(1093); // mace（766）
        back.writeVarInt(20);
        byte[] to765 = ChunkTranslator.translateSetCooldown766To765(back.bytes());
        Reader backReader = new Reader(to765);
        assertEquals(1, backReader.readVarInt()); // mace → stone
        assertEquals(20, backReader.readVarInt());
    }

    @Test
    void entityEquipment765To766() {
        Writer payload = new Writer();
        payload.writeVarInt(7); // entityId
        payload.writeUnsignedByte(0x00); // スロット 0、継続なし
        writeSlot765(payload, 794, 1, null); // scute

        byte[] translated = ChunkTranslator.translateEntityEquipment765To766(payload.bytes());
        Reader reader = new Reader(translated);
        assertEquals(7, reader.readVarInt());
        assertEquals(0x00, reader.readUnsignedByte());
        assertArrayEquals(new int[]{1, 795, 0, 0}, readSlot766(reader));
        assertEquals(0, reader.remaining());
    }

    @Test
    void windowClick765To766AndBack() {
        Writer payload = new Writer();
        payload.writeUnsignedByte(1); // windowId
        payload.writeVarInt(5); // stateId
        payload.writeShort(10); // slot
        payload.writeByte(0); // button
        payload.writeVarInt(0); // mode
        payload.writeVarInt(1); // 変更スロット 1件
        payload.writeShort(3);
        writeSlot765(payload, 1, 64, null); // stone ×64
        writeEmptySlot765(payload); // カーソルは空き

        byte[] to766 = ChunkTranslator.translateWindowClick765To766(payload.bytes());
        Reader reader = new Reader(to766);
        assertEquals(1, reader.readUnsignedByte());
        assertEquals(5, reader.readVarInt());
        assertEquals(10, reader.readShort());
        assertEquals(0, reader.readByte());
        assertEquals(0, reader.readVarInt());
        assertEquals(1, reader.readVarInt());
        assertEquals(3, reader.readShort());
        assertArrayEquals(new int[]{64, 1, 0, 0}, readSlot766(reader));
        assertEquals(0, readSlot766(reader)[0]);
        assertEquals(0, reader.remaining());

        byte[] back = ChunkTranslator.translateWindowClick766To765(to766);
        Reader backReader = new Reader(back);
        assertEquals(1, backReader.readUnsignedByte());
        assertEquals(5, backReader.readVarInt());
        assertEquals(10, backReader.readShort());
        assertEquals(0, backReader.readByte());
        assertEquals(0, backReader.readVarInt());
        assertEquals(1, backReader.readVarInt());
        assertEquals(3, backReader.readShort());
        assertArrayEquals(new int[]{1, 1, 64}, readSlot765(backReader));
        assertEquals(0, readSlot765(backReader)[0]);
        assertEquals(0, backReader.remaining());
    }

    @Test
    void windowClick766To765DropsComponentSlot() {
        Writer payload = new Writer();
        payload.writeUnsignedByte(1);
        payload.writeVarInt(5);
        payload.writeShort(10);
        payload.writeByte(0);
        payload.writeVarInt(0);
        payload.writeVarInt(1);
        payload.writeShort(3);
        writeSlot766WithComponents(payload, 1, 1, 1, 0); // 追加コンポーネント付き
        writeEmptySlot766(payload);

        assertNull(ChunkTranslator.translateWindowClick766To765(payload.bytes()));
    }

    @Test
    void setCreativeSlot765To766AndBack() {
        Writer payload = new Writer();
        payload.writeShort(5);
        writeSlot765(payload, 794, 1, null); // scute

        byte[] to766 = ChunkTranslator.translateSetCreativeSlot765To766(payload.bytes());
        Reader reader = new Reader(to766);
        assertEquals(5, reader.readShort());
        assertArrayEquals(new int[]{1, 795, 0, 0}, readSlot766(reader));
        assertEquals(0, reader.remaining());

        byte[] back = ChunkTranslator.translateSetCreativeSlot766To765(to766);
        Reader backReader = new Reader(back);
        assertEquals(5, backReader.readShort());
        assertArrayEquals(new int[]{1, 794, 1}, readSlot765(backReader));
        assertEquals(0, backReader.remaining());
    }
}
