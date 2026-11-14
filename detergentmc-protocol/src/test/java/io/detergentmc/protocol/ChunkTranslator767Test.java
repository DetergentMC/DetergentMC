package io.detergentmc.protocol;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.detergentmc.protocol.Nbt.Tag;
import io.detergentmc.protocol.PacketIO.Reader;
import io.detergentmc.protocol.PacketIO.Writer;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * {@link ChunkTranslator} の767（1.21／1.21.1）向け変換テスト。
 * 766形式と767形式のペイロードを合成して変換し、読み直して中身を確認する。
 * ブロック状態・エンティティ・パーティクルは同一のため、主にスロットの
 * データコンポーネント・属性・絵画・バナー・装飾パターン・ダメージ種別を扱う。
 */
class ChunkTranslator767Test {

    // ---- ヘルパー ----

    /** 空のコンパウンド NBT。 */
    private static Tag emptyCompound() {
        return new Tag.CompoundTag(Map.of());
    }

    /** スロット（766／767共通の形：個数 i8＋アイテムID＋追加数＋削除数）。追加・削除は 0。 */
    private static void writeSlot(Writer writer, int itemId, int count) {
        writer.writeByte(count);
        writer.writeVarInt(itemId);
        writer.writeVarInt(0);
        writer.writeVarInt(0);
    }

    /** 空きスロット（個数 0）。 */
    private static void writeEmptySlot(Writer writer) {
        writer.writeByte(0);
    }

    /** スロットの先頭（個数＋アイテムID）を読み、追加コンポーネント数を返す。 */
    private static int[] readSlotHead(Reader reader) {
        int count = reader.readByte();
        int itemId = reader.readVarInt();
        int added = reader.readVarInt();
        return new int[]{count, itemId, added};
    }

    /** Set Slot ペイロード（766／767共通：windowId＋stateId＋slotIndex＋スロット）。 */
    private static byte[] setSlotPacket(int itemId) {
        Writer writer = new Writer();
        writer.writeByte(0); // windowId
        writer.writeVarInt(0); // stateId
        writer.writeShort(3); // slotIndex
        writeSlot(writer, itemId, 1);
        return writer.bytes();
    }

    /** Set Slot ペイロードのスロット部分を読み、{slotIndex, count, itemId, added} を返す。 */
    private static int[] readSetSlot(Reader reader) {
        reader.readByte(); // windowId
        reader.readVarInt(); // stateId
        int slotIndex = reader.readShort();
        int[] head = readSlotHead(reader);
        return new int[]{slotIndex, head[0], head[1], head[2]};
    }

    // ---- Set Slot / アイテムID ----

    @Test
    void setSlot766To767BowlMoves() {
        // bowl は 766の 848 から 767の 799 へ移る。
        byte[] out = ChunkTranslator.translateSetSlot766To767(setSlotPacket(848));
        int[] read = readSetSlot(new Reader(out));
        assertEquals(3, read[0]);
        assertEquals(1, read[1]);
        assertEquals(799, read[2]);
        assertEquals(0, read[3]);
    }

    @Test
    void setSlot766To767DiscsShift() {
        // music_disc_creator 等 3件の追加で 1172→1174、1300→1303。
        byte[] out = ChunkTranslator.translateSetSlot766To767(setSlotPacket(1172));
        assertEquals(1174, readSetSlot(new Reader(out))[2]);
        byte[] out2 = ChunkTranslator.translateSetSlot766To767(setSlotPacket(1300));
        assertEquals(1303, readSetSlot(new Reader(out2))[2]);
    }

    @Test
    void setSlot767To766BowlMovesBack() {
        byte[] out = ChunkTranslator.translateSetSlot767To766(setSlotPacket(799));
        assertEquals(848, readSetSlot(new Reader(out))[2]);
        byte[] out2 = ChunkTranslator.translateSetSlot767To766(setSlotPacket(1174));
        assertEquals(1172, readSetSlot(new Reader(out2))[2]);
    }

    @Test
    void setSlot767To766NewDiscBecomesStone() {
        // 767にしか無い music_disc_creator（1172）は766に無いため石（1）になる。
        byte[] out = ChunkTranslator.translateSetSlot767To766(setSlotPacket(1172));
        assertEquals(1, readSetSlot(new Reader(out))[2]);
    }

    // ---- エンチャント ----

    @Test
    void setSlot766To767Enchantments() {
        Writer writer = new Writer();
        writer.writeByte(0);
        writer.writeVarInt(0);
        writer.writeShort(0);
        writer.writeByte(1);
        writer.writeVarInt(1); // stone
        writer.writeVarInt(1); // added 1
        writer.writeVarInt(9); // enchantments
        writer.writeVarInt(1); // count 1
        writer.writeVarInt(40); // mending（766）
        writer.writeVarInt(1); // level 1
        writer.writeBoolean(true); // showTooltip
        writer.writeVarInt(0); // removed 0
        byte[] out = ChunkTranslator.translateSetSlot766To767(writer.bytes());
        Reader reader = new Reader(out);
        reader.readByte();
        reader.readVarInt();
        reader.readShort();
        assertEquals(1, reader.readByte());
        assertEquals(1, reader.readVarInt());
        assertEquals(1, reader.readVarInt()); // added 1
        assertEquals(9, reader.readVarInt());
        assertEquals(1, reader.readVarInt()); // count 1
        assertEquals(22, reader.readVarInt()); // mending（767）
        assertEquals(1, reader.readVarInt()); // level 1
        assertTrue(reader.readBoolean());
        assertEquals(0, reader.readVarInt()); // removed 0
    }

    @Test
    void setSlot766To767StoredEnchantments() {
        Writer writer = new Writer();
        writer.writeByte(0);
        writer.writeVarInt(0);
        writer.writeShort(0);
        writer.writeByte(1);
        writer.writeVarInt(1);
        writer.writeVarInt(1);
        writer.writeVarInt(23); // stored_enchantments
        writer.writeVarInt(1);
        writer.writeVarInt(13); // sharpness（766）
        writer.writeVarInt(3); // level 3
        writer.writeBoolean(false);
        writer.writeVarInt(0);
        byte[] out = ChunkTranslator.translateSetSlot766To767(writer.bytes());
        Reader reader = new Reader(out);
        reader.readByte();
        reader.readVarInt();
        reader.readShort();
        reader.readByte();
        reader.readVarInt();
        reader.readVarInt();
        assertEquals(23, reader.readVarInt());
        assertEquals(1, reader.readVarInt());
        assertEquals(32, reader.readVarInt()); // sharpness（767）
        assertEquals(3, reader.readVarInt());
        assertEquals(false, reader.readBoolean());
    }

    // ---- 属性修飾子 ----

    @Test
    void setSlot766To767AttributeModifiersDropUuid() {
        Writer writer = new Writer();
        writer.writeByte(0);
        writer.writeVarInt(0);
        writer.writeShort(0);
        writer.writeByte(1);
        writer.writeVarInt(1);
        writer.writeVarInt(1);
        writer.writeVarInt(12); // attribute_modifiers
        writer.writeVarInt(1); // count 1
        writer.writeVarInt(17); // generic.movement_speed（766）
        writer.writeUuid(new UUID(0x0102030405060708L, 0x090A0B0C0D0E0F10L));
        writer.writeString("test");
        writer.writeDouble(1.5);
        writer.writeVarInt(0); // operation
        writer.writeVarInt(0); // slot
        writer.writeBoolean(true); // showTooltip
        writer.writeVarInt(0);
        byte[] out = ChunkTranslator.translateSetSlot766To767(writer.bytes());
        Reader reader = new Reader(out);
        reader.readByte();
        reader.readVarInt();
        reader.readShort();
        reader.readByte();
        reader.readVarInt();
        reader.readVarInt();
        assertEquals(12, reader.readVarInt());
        assertEquals(1, reader.readVarInt());
        assertEquals(21, reader.readVarInt()); // generic.movement_speed（767）
        // 767には UUID が無いため、次はそのまま名前が来る。
        assertEquals("test", reader.readString());
        assertEquals(1.5, reader.readDouble());
        assertEquals(0, reader.readVarInt());
        assertEquals(0, reader.readVarInt());
        assertTrue(reader.readBoolean());
    }

    @Test
    void setSlot767To766AttributeModifiersSynthesizeUuid() {
        Writer writer = new Writer();
        writer.writeByte(0);
        writer.writeVarInt(0);
        writer.writeShort(0);
        writer.writeByte(1);
        writer.writeVarInt(1);
        writer.writeVarInt(1);
        writer.writeVarInt(12); // attribute_modifiers
        writer.writeVarInt(1);
        writer.writeVarInt(21); // generic.movement_speed（767）
        writer.writeString("test");
        writer.writeDouble(1.5);
        writer.writeVarInt(0);
        writer.writeVarInt(0);
        writer.writeBoolean(true);
        writer.writeVarInt(0);
        byte[] out = ChunkTranslator.translateSetSlot767To766(writer.bytes());
        Reader reader = new Reader(out);
        reader.readByte();
        reader.readVarInt();
        reader.readShort();
        reader.readByte();
        reader.readVarInt();
        reader.readVarInt();
        assertEquals(12, reader.readVarInt());
        assertEquals(1, reader.readVarInt());
        assertEquals(17, reader.readVarInt()); // generic.movement_speed（766）
        // 766には UUID が要るため、名前から決定的に合成される。
        assertEquals(UUID.nameUUIDFromBytes("test".getBytes(StandardCharsets.UTF_8)), reader.readUuid());
        assertEquals("test", reader.readString());
        assertEquals(1.5, reader.readDouble());
        assertEquals(0, reader.readVarInt());
        assertEquals(0, reader.readVarInt());
        assertTrue(reader.readBoolean());
    }

    @Test
    void setSlot767To766NewAttributeModifierDropped() {
        // player.mining_efficiency（767の 19）は766に無いため修飾子ごと落とす。
        Writer writer = new Writer();
        writer.writeByte(0);
        writer.writeVarInt(0);
        writer.writeShort(0);
        writer.writeByte(1);
        writer.writeVarInt(1);
        writer.writeVarInt(1);
        writer.writeVarInt(12);
        writer.writeVarInt(1);
        writer.writeVarInt(19); // player.mining_efficiency（767）
        writer.writeString("mining");
        writer.writeDouble(2.0);
        writer.writeVarInt(0);
        writer.writeVarInt(0);
        writer.writeBoolean(true);
        writer.writeVarInt(0);
        byte[] out = ChunkTranslator.translateSetSlot767To766(writer.bytes());
        Reader reader = new Reader(out);
        reader.readByte();
        reader.readVarInt();
        reader.readShort();
        reader.readByte();
        reader.readVarInt();
        assertEquals(1, reader.readVarInt()); // added 1
        assertEquals(12, reader.readVarInt());
        assertEquals(0, reader.readVarInt()); // 修飾子 0件に減る
        assertTrue(reader.readBoolean());
        assertEquals(0, reader.readVarInt());
    }

    // ---- jukebox_playable ----

    @Test
    void setSlot767To766DropsJukeboxPlayable() {
        Writer writer = new Writer();
        writer.writeByte(0);
        writer.writeVarInt(0);
        writer.writeShort(0);
        writer.writeByte(1);
        writer.writeVarInt(1);
        writer.writeVarInt(1);
        writer.writeVarInt(42); // jukebox_playable（767）
        writer.writeBoolean(false); // hasHolder false → 曲名の文字列
        writer.writeString("minecraft:precipice");
        writer.writeBoolean(true); // showInTooltip
        writer.writeVarInt(0);
        byte[] out = ChunkTranslator.translateSetSlot767To766(writer.bytes());
        Reader reader = new Reader(out);
        reader.readByte();
        reader.readVarInt();
        reader.readShort();
        reader.readByte();
        reader.readVarInt();
        assertEquals(0, reader.readVarInt()); // added 0件に減る
        assertEquals(0, reader.readVarInt()); // removed 0
    }

    // ---- 種別IDのずれ（lodestone_tracker／recipes） ----

    @Test
    void setSlot766To767TypeIdShift() {
        Writer writer = new Writer();
        writer.writeByte(0);
        writer.writeVarInt(0);
        writer.writeShort(0);
        writer.writeByte(1);
        writer.writeVarInt(1);
        writer.writeVarInt(1);
        writer.writeVarInt(43); // lodestone_tracker（766）
        writer.writeBoolean(false); // globalPosition 無し
        writer.writeBoolean(true); // tracked
        writer.writeVarInt(0);
        byte[] out = ChunkTranslator.translateSetSlot766To767(writer.bytes());
        Reader reader = new Reader(out);
        reader.readByte();
        reader.readVarInt();
        reader.readShort();
        reader.readByte();
        reader.readVarInt();
        reader.readVarInt();
        assertEquals(44, reader.readVarInt()); // lodestone_tracker（767）
        assertEquals(false, reader.readBoolean());
        assertEquals(true, reader.readBoolean());
    }

    @Test
    void setSlot766To767RecipesTypeIdShift() {
        Writer writer = new Writer();
        writer.writeByte(0);
        writer.writeVarInt(0);
        writer.writeShort(0);
        writer.writeByte(1);
        writer.writeVarInt(1);
        writer.writeVarInt(1);
        writer.writeVarInt(42); // recipes（766）
        writer.writeAnonymousNbt(emptyCompound());
        writer.writeVarInt(0);
        byte[] out = ChunkTranslator.translateSetSlot766To767(writer.bytes());
        Reader reader = new Reader(out);
        reader.readByte();
        reader.readVarInt();
        reader.readShort();
        reader.readByte();
        reader.readVarInt();
        reader.readVarInt();
        assertEquals(43, reader.readVarInt()); // recipes（767）
        assertEquals(emptyCompound(), reader.readAnonymousNbt().tag());
    }

    // ---- food／bundle／trim／instrument／banner／pot ----

    @Test
    void setSlot766To767FoodWithSlot() {
        Writer writer = new Writer();
        writer.writeByte(0);
        writer.writeVarInt(0);
        writer.writeShort(0);
        writer.writeByte(1);
        writer.writeVarInt(1);
        writer.writeVarInt(1);
        writer.writeVarInt(20); // food
        writer.writeVarInt(3); // nutrition
        writer.writeFloat(0.6f);
        writer.writeBoolean(true); // canAlwaysEat
        writer.writeFloat(1.6f);
        writeSlot(writer, 848, 1); // usingConvertsTo：bowl
        writer.writeVarInt(1); // effects 1件
        writer.writeVarInt(0); // effect 0
        writer.writeFloat(1.0f);
        writer.writeVarInt(0);
        byte[] out = ChunkTranslator.translateSetSlot766To767(writer.bytes());
        Reader reader = new Reader(out);
        reader.readByte();
        reader.readVarInt();
        reader.readShort();
        reader.readByte();
        reader.readVarInt();
        reader.readVarInt();
        assertEquals(20, reader.readVarInt());
        assertEquals(3, reader.readVarInt());
        assertEquals(0.6f, reader.readFloat());
        assertTrue(reader.readBoolean());
        assertEquals(1.6f, reader.readFloat());
        assertEquals(1, reader.readByte());
        assertEquals(799, reader.readVarInt()); // bowl（767）
        reader.readVarInt();
        reader.readVarInt();
        assertEquals(1, reader.readVarInt());
        assertEquals(0, reader.readVarInt());
        assertEquals(1.0f, reader.readFloat());
    }

    @Test
    void setSlot766To767BundleContents() {
        Writer writer = new Writer();
        writer.writeByte(0);
        writer.writeVarInt(0);
        writer.writeShort(0);
        writer.writeByte(1);
        writer.writeVarInt(1);
        writer.writeVarInt(1);
        writer.writeVarInt(30); // bundle_contents
        writer.writeVarInt(2);
        writeSlot(writer, 848, 1);
        writeEmptySlot(writer);
        writer.writeVarInt(0);
        byte[] out = ChunkTranslator.translateSetSlot766To767(writer.bytes());
        Reader reader = new Reader(out);
        reader.readByte();
        reader.readVarInt();
        reader.readShort();
        reader.readByte();
        reader.readVarInt();
        reader.readVarInt();
        assertEquals(30, reader.readVarInt());
        assertEquals(2, reader.readVarInt());
        assertEquals(1, reader.readByte());
        assertEquals(799, reader.readVarInt());
        reader.readVarInt();
        reader.readVarInt();
        assertEquals(0, reader.readByte()); // 空きスロット
    }

    @Test
    void setSlot766To767TrimReferences() {
        Writer writer = new Writer();
        writer.writeByte(0);
        writer.writeVarInt(0);
        writer.writeShort(0);
        writer.writeByte(1);
        writer.writeVarInt(1);
        writer.writeVarInt(1);
        writer.writeVarInt(35); // trim
        writer.writeVarInt(1); // material：参照（id 0）
        writer.writeVarInt(7); // pattern：sentry（766の id 6 → holder 7）
        writer.writeBoolean(true); // showInTooltip
        writer.writeVarInt(0);
        byte[] out = ChunkTranslator.translateSetSlot766To767(writer.bytes());
        Reader reader = new Reader(out);
        reader.readByte();
        reader.readVarInt();
        reader.readShort();
        reader.readByte();
        reader.readVarInt();
        reader.readVarInt();
        assertEquals(35, reader.readVarInt());
        assertEquals(1, reader.readVarInt()); // material は同一
        assertEquals(9, reader.readVarInt()); // sentry は767の id 8 → holder 9
        assertTrue(reader.readBoolean());
    }

    @Test
    void setSlot767To766TrimBoltDropped() {
        // bolt（767の id 0 → holder 1）は766に無いため trim ごと落とす。
        Writer writer = new Writer();
        writer.writeByte(0);
        writer.writeVarInt(0);
        writer.writeShort(0);
        writer.writeByte(1);
        writer.writeVarInt(1);
        writer.writeVarInt(1);
        writer.writeVarInt(35); // trim
        writer.writeVarInt(1); // material：参照
        writer.writeVarInt(1); // pattern：bolt（767の id 0）
        writer.writeBoolean(true);
        writer.writeVarInt(0);
        byte[] out = ChunkTranslator.translateSetSlot767To766(writer.bytes());
        Reader reader = new Reader(out);
        reader.readByte();
        reader.readVarInt();
        reader.readShort();
        reader.readByte();
        reader.readVarInt();
        assertEquals(0, reader.readVarInt()); // added 0件に減る
        assertEquals(0, reader.readVarInt());
    }

    @Test
    void setSlot766To767InstrumentInline() {
        Writer writer = new Writer();
        writer.writeByte(0);
        writer.writeVarInt(0);
        writer.writeShort(0);
        writer.writeByte(1);
        writer.writeVarInt(1);
        writer.writeVarInt(1);
        writer.writeVarInt(40); // instrument
        writer.writeVarInt(0); // 直書き
        writer.writeVarInt(0); // 音の Holder：直書き
        writer.writeString("block.trial_spawner.charge_activate");
        writer.writeBoolean(false); // fixedRange 無し
        writer.writeFloat(1.0f); // useDuration
        writer.writeFloat(16.0f); // range
        writer.writeAnonymousNbt(emptyCompound()); // description
        writer.writeVarInt(0);
        byte[] out = ChunkTranslator.translateSetSlot766To767(writer.bytes());
        Reader reader = new Reader(out);
        reader.readByte();
        reader.readVarInt();
        reader.readShort();
        reader.readByte();
        reader.readVarInt();
        reader.readVarInt();
        assertEquals(40, reader.readVarInt());
        assertEquals(0, reader.readVarInt());
        assertEquals(0, reader.readVarInt());
        assertEquals("block.trial_spawner.ominous_activate", reader.readString());
        assertEquals(false, reader.readBoolean());
        assertEquals(1.0f, reader.readFloat());
        assertEquals(16.0f, reader.readFloat());
        assertEquals(emptyCompound(), reader.readAnonymousNbt().tag());
    }

    @Test
    void setSlot766To767InstrumentSoundReference() {
        // 直書きの楽器の中の音 Holder（参照：音 886）は 767では音 889 の Holder になる。
        Writer writer = new Writer();
        writer.writeByte(0);
        writer.writeVarInt(0);
        writer.writeShort(0);
        writer.writeByte(1);
        writer.writeVarInt(1);
        writer.writeVarInt(1);
        writer.writeVarInt(40); // instrument
        writer.writeVarInt(0); // 直書き
        writer.writeVarInt(887); // 音の Holder（参照：音 886）
        writer.writeFloat(1.0f); // useDuration
        writer.writeFloat(16.0f); // range
        writer.writeAnonymousNbt(emptyCompound()); // description
        writer.writeVarInt(0);
        byte[] out = ChunkTranslator.translateSetSlot766To767(writer.bytes());
        Reader reader = new Reader(out);
        reader.readByte();
        reader.readVarInt();
        reader.readShort();
        reader.readByte();
        reader.readVarInt();
        reader.readVarInt();
        assertEquals(40, reader.readVarInt());
        assertEquals(0, reader.readVarInt());
        assertEquals(890, reader.readVarInt()); // 音 889 の Holder
        assertEquals(1.0f, reader.readFloat());
        assertEquals(16.0f, reader.readFloat());
        assertEquals(emptyCompound(), reader.readAnonymousNbt().tag());
    }

    @Test
    void setSlot766To767BannerPatterns() {
        Writer writer = new Writer();
        writer.writeByte(0);
        writer.writeVarInt(0);
        writer.writeShort(0);
        writer.writeByte(1);
        writer.writeVarInt(1);
        writer.writeVarInt(1);
        writer.writeVarInt(48); // banner_patterns（766）
        writer.writeVarInt(1); // layers 1件
        writer.writeVarInt(12); // flower（766の id 11 → holder 12）
        writer.writeVarInt(1); // colorId
        writer.writeVarInt(0);
        byte[] out = ChunkTranslator.translateSetSlot766To767(writer.bytes());
        Reader reader = new Reader(out);
        reader.readByte();
        reader.readVarInt();
        reader.readShort();
        reader.readByte();
        reader.readVarInt();
        reader.readVarInt();
        assertEquals(49, reader.readVarInt()); // banner_patterns（767）
        assertEquals(1, reader.readVarInt());
        assertEquals(13, reader.readVarInt()); // flower（767の id 12 → holder 13）
        assertEquals(1, reader.readVarInt());
    }

    @Test
    void setSlot767To766BannerFlowLayerDropped() {
        // flow（767の id 11 → holder 12）は766に無いため層ごと落とす。
        Writer writer = new Writer();
        writer.writeByte(0);
        writer.writeVarInt(0);
        writer.writeShort(0);
        writer.writeByte(1);
        writer.writeVarInt(1);
        writer.writeVarInt(1);
        writer.writeVarInt(49); // banner_patterns（767）
        writer.writeVarInt(1);
        writer.writeVarInt(12); // flow（767の id 11）
        writer.writeVarInt(1);
        writer.writeVarInt(0);
        byte[] out = ChunkTranslator.translateSetSlot767To766(writer.bytes());
        Reader reader = new Reader(out);
        reader.readByte();
        reader.readVarInt();
        reader.readShort();
        reader.readByte();
        reader.readVarInt();
        reader.readVarInt();
        assertEquals(48, reader.readVarInt());
        assertEquals(0, reader.readVarInt()); // 層 0件に減る
    }

    @Test
    void setSlot766To767PotDecorations() {
        Writer writer = new Writer();
        writer.writeByte(0);
        writer.writeVarInt(0);
        writer.writeShort(0);
        writer.writeByte(1);
        writer.writeVarInt(1);
        writer.writeVarInt(1);
        writer.writeVarInt(50); // pot_decorations（766）
        writer.writeVarInt(2);
        writer.writeVarInt(848); // bowl → 799
        writer.writeVarInt(1); // stone → 1
        writer.writeVarInt(0);
        byte[] out = ChunkTranslator.translateSetSlot766To767(writer.bytes());
        Reader reader = new Reader(out);
        reader.readByte();
        reader.readVarInt();
        reader.readShort();
        reader.readByte();
        reader.readVarInt();
        reader.readVarInt();
        assertEquals(51, reader.readVarInt()); // pot_decorations（767）
        assertEquals(2, reader.readVarInt());
        assertEquals(799, reader.readVarInt());
        assertEquals(1, reader.readVarInt());
    }

    @Test
    void setSlot766To767OpaqueCopyKeepsBytes() {
        // can_place_on（種別 10）は中身のブロックIDが同一のためバイト列のまま写る。
        Writer writer = new Writer();
        writer.writeByte(0);
        writer.writeVarInt(0);
        writer.writeShort(0);
        writer.writeByte(1);
        writer.writeVarInt(1);
        writer.writeVarInt(1);
        writer.writeVarInt(10); // can_place_on
        writer.writeVarInt(1); // predicates 1件
        writer.writeBoolean(true); // blockSet 有り
        writer.writeVarInt(0); // IDSet：タグ
        writer.writeString("#minecraft:logs");
        writer.writeBoolean(false); // properties 無し
        writer.writeByte(0); // nbt 無し
        writer.writeBoolean(true); // showTooltip
        writer.writeVarInt(0);
        byte[] in = writer.bytes();
        byte[] out = ChunkTranslator.translateSetSlot766To767(in);
        assertArrayEquals(in, out);
    }

    // ---- その他のパケット ----

    @Test
    void setCooldown766To767() {
        Writer writer = new Writer();
        writer.writeVarInt(1300);
        writer.writeVarInt(100);
        byte[] out = ChunkTranslator.translateSetCooldown766To767(writer.bytes());
        Reader reader = new Reader(out);
        assertEquals(1303, reader.readVarInt());
        assertEquals(100, reader.readVarInt());
    }

    @Test
    void tradeList766To767() {
        Writer writer = new Writer();
        writer.writeVarInt(1); // windowId
        writer.writeVarInt(1); // count 1
        // ItemCost：入力1（bowl）
        writer.writeVarInt(848);
        writer.writeVarInt(1);
        writer.writeVarInt(0);
        // Slot：出力（stone）
        writeSlot(writer, 1, 1);
        // OptionalItemCost：入力2 無し
        writer.writeBoolean(false);
        writer.writeBoolean(true); // hasStock
        writer.writeInt(0); // uses
        writer.writeInt(10); // maxUses
        writer.writeInt(5); // xp
        writer.writeInt(0); // specialPrice
        writer.writeFloat(1.0f); // priceMultiplier
        writer.writeInt(0); // demand
        writer.writeVarInt(1); // villagerLevel
        writer.writeVarInt(2); // xp
        writer.writeBoolean(true); // isRegular
        writer.writeBoolean(true); // canRestock
        byte[] out = ChunkTranslator.translateTradeList766To767(writer.bytes());
        Reader reader = new Reader(out);
        assertEquals(1, reader.readVarInt());
        assertEquals(1, reader.readVarInt());
        assertEquals(799, reader.readVarInt()); // bowl（767）
        assertEquals(1, reader.readVarInt());
        assertEquals(0, reader.readVarInt());
        assertEquals(1, reader.readByte());
        assertEquals(1, reader.readVarInt());
        assertEquals(0, reader.readVarInt());
        assertEquals(0, reader.readVarInt());
        assertEquals(false, reader.readBoolean());
        assertEquals(true, reader.readBoolean());
        assertEquals(0, reader.readInt());
        assertEquals(10, reader.readInt());
        assertEquals(5, reader.readInt());
        assertEquals(0, reader.readInt());
        assertEquals(1.0f, reader.readFloat());
        assertEquals(0, reader.readInt());
        assertEquals(1, reader.readVarInt());
        assertEquals(2, reader.readVarInt());
        assertEquals(true, reader.readBoolean());
        assertEquals(true, reader.readBoolean());
    }

    @Test
    void windowClick766To767() {
        Writer writer = new Writer();
        writer.writeUnsignedByte(0); // windowId
        writer.writeVarInt(1); // stateId
        writer.writeShort(5); // slot
        writer.writeByte(0); // button
        writer.writeVarInt(0); // mode
        writer.writeVarInt(1); // changed 1件
        writer.writeShort(5);
        writeSlot(writer, 848, 1);
        writeEmptySlot(writer); // carried
        byte[] out = ChunkTranslator.translateWindowClick766To767(writer.bytes());
        Reader reader = new Reader(out);
        assertEquals(0, reader.readUnsignedByte());
        assertEquals(1, reader.readVarInt());
        assertEquals(5, reader.readShort());
        assertEquals(0, reader.readByte());
        assertEquals(0, reader.readVarInt());
        assertEquals(1, reader.readVarInt());
        assertEquals(5, reader.readShort());
        assertEquals(1, reader.readByte());
        assertEquals(799, reader.readVarInt());
        reader.readVarInt();
        reader.readVarInt();
        assertEquals(0, reader.readByte()); // carried は空き
    }

    @Test
    void windowItems766To767() {
        Writer writer = new Writer();
        writer.writeUnsignedByte(1); // windowId
        writer.writeVarInt(2); // stateId
        writer.writeVarInt(1); // count 1
        writeSlot(writer, 848, 1);
        writeEmptySlot(writer); // carried
        byte[] out = ChunkTranslator.translateWindowItems766To767(writer.bytes());
        Reader reader = new Reader(out);
        assertEquals(1, reader.readUnsignedByte());
        assertEquals(2, reader.readVarInt());
        assertEquals(1, reader.readVarInt());
        assertEquals(1, reader.readByte());
        assertEquals(799, reader.readVarInt());
        reader.readVarInt();
        reader.readVarInt();
        assertEquals(0, reader.readByte());
    }

    @Test
    void entityEquipment766To767() {
        Writer writer = new Writer();
        writer.writeVarInt(7); // entityId
        writer.writeUnsignedByte(0); // slotByte（最後）
        writeSlot(writer, 848, 1);
        byte[] out = ChunkTranslator.translateEntityEquipment766To767(writer.bytes());
        Reader reader = new Reader(out);
        assertEquals(7, reader.readVarInt());
        assertEquals(0, reader.readUnsignedByte());
        assertEquals(1, reader.readByte());
        assertEquals(799, reader.readVarInt());
    }

    @Test
    void openWindowBlockActionSpawnEntityAreIdentity() {
        // メニュー・ブロック・エンティティは同一のため読み直して書き直すだけ。
        Writer open = new Writer();
        open.writeVarInt(1);
        open.writeVarInt(12); // crafting
        open.writeAnonymousNbt(emptyCompound());
        assertArrayEquals(open.bytes(), ChunkTranslator.translateOpenWindow766To767(open.bytes()));

        Writer action = new Writer();
        action.writePositionRaw(0x0400000000000000L);
        action.writeUnsignedByte(1);
        action.writeUnsignedByte(2);
        action.writeVarInt(5);
        assertArrayEquals(action.bytes(), ChunkTranslator.translateBlockAction766To767(action.bytes()));

        Writer spawn = new Writer();
        spawn.writeVarInt(1);
        spawn.writeBytes(new byte[16]);
        spawn.writeVarInt(77); // pig
        spawn.writeDouble(0.0);
        spawn.writeDouble(64.0);
        spawn.writeDouble(0.0);
        spawn.writeByte(0);
        spawn.writeByte(0);
        spawn.writeByte(0);
        spawn.writeVarInt(0);
        spawn.writeShort(0);
        spawn.writeShort(0);
        spawn.writeShort(0);
        assertArrayEquals(spawn.bytes(), ChunkTranslator.translateSpawnEntity766To767(spawn.bytes()));
    }

    @Test
    void entityEffect766To767IsIdentity() {
        Writer writer = new Writer();
        writer.writeVarInt(1);
        writer.writeVarInt(0); // speed
        writer.writeVarInt(1); // amplifier
        writer.writeVarInt(100); // duration
        writer.writeUnsignedByte(0); // flags
        assertArrayEquals(writer.bytes(), ChunkTranslator.translateEntityEffect766To767(writer.bytes()));
        Writer remove = new Writer();
        remove.writeVarInt(1);
        remove.writeVarInt(0);
        assertArrayEquals(remove.bytes(), ChunkTranslator.translateRemoveEntityEffect766To767(remove.bytes()));
    }

    // ---- メタデータ ----

    @Test
    void entityMetadata766To767Painting() {
        Writer writer = new Writer();
        writer.writeVarInt(1); // entityId
        writer.writeUnsignedByte(0); // index
        writer.writeVarInt(26); // painting_variant
        writer.writeVarInt(1); // kebab（766の id 0 → holder 1）
        writer.writeUnsignedByte(0xFF);
        byte[] out = ChunkTranslator.translateEntityMetadata766To767(writer.bytes());
        Reader reader = new Reader(out);
        assertEquals(1, reader.readVarInt());
        assertEquals(0, reader.readUnsignedByte());
        assertEquals(26, reader.readVarInt());
        assertEquals(24, reader.readVarInt()); // kebab（767の id 23 → holder 24）
        assertEquals(0xFF, reader.readUnsignedByte());
    }

    @Test
    void entityMetadata767To766NewPaintingDropped() {
        // backyard は767にしか無い（766には無い）絵画のため、エントリごと落とす。
        // 旧版では water を例にしていたが、water は1.19からあるコマンド限定の
        // 絵画で766にも存在する（766 id 27）ため、落とす例として誤りだった。
        Writer writer = new Writer();
        writer.writeVarInt(1);
        writer.writeUnsignedByte(0);
        writer.writeVarInt(26);
        writer.writeVarInt(4); // backyard（767の id 3 → holder 4）
        writer.writeUnsignedByte(0xFF);
        byte[] out = ChunkTranslator.translateEntityMetadata767To766(writer.bytes());
        Reader reader = new Reader(out);
        assertEquals(1, reader.readVarInt());
        assertEquals(0xFF, reader.readUnsignedByte()); // エントリごと落とす
    }

    @Test
    void entityMetadata766To767WolfInlineRoundTrip() {
        Writer writer = new Writer();
        writer.writeVarInt(1);
        writer.writeUnsignedByte(0);
        writer.writeVarInt(23); // wolf_variant
        writer.writeVarInt(0); // 直書き
        writer.writeString("minecraft:entity/wolf/wolf_ashen");
        writer.writeString("minecraft:entity/wolf/wolf_ashen_tame");
        writer.writeString("minecraft:entity/wolf/wolf_ashen_angry");
        writer.writeVarInt(0); // biome：タグ
        writer.writeString("minecraft:plains");
        writer.writeUnsignedByte(0xFF);
        byte[] in = writer.bytes();
        byte[] out = ChunkTranslator.translateEntityMetadata766To767(in);
        assertArrayEquals(in, out);
    }

    @Test
    void entityMetadata766To767ItemSlot() {
        Writer writer = new Writer();
        writer.writeVarInt(1);
        writer.writeUnsignedByte(0);
        writer.writeVarInt(7); // item_stack
        writeSlot(writer, 848, 1);
        writer.writeUnsignedByte(0xFF);
        byte[] out = ChunkTranslator.translateEntityMetadata766To767(writer.bytes());
        Reader reader = new Reader(out);
        assertEquals(1, reader.readVarInt());
        assertEquals(0, reader.readUnsignedByte());
        assertEquals(7, reader.readVarInt());
        assertEquals(1, reader.readByte());
        assertEquals(799, reader.readVarInt());
        assertEquals(0, reader.readVarInt()); // added 0
        assertEquals(0, reader.readVarInt()); // removed 0
        assertEquals(0xFF, reader.readUnsignedByte());
    }

    // ---- Update Attributes ----

    @Test
    void updateAttributes766To767() {
        UUID uuid = new UUID(0x0102030405060708L, 0x090A0B0C0D0E0F10L);
        Writer writer = new Writer();
        writer.writeVarInt(1); // entityId
        writer.writeVarInt(1); // count 1
        writer.writeVarInt(17); // generic.movement_speed（766）
        writer.writeDouble(0.1);
        writer.writeVarInt(1); // modifiers 1件
        writer.writeUuid(uuid);
        writer.writeDouble(0.5);
        writer.writeByte(0); // operation
        byte[] out = ChunkTranslator.translateUpdateAttributes766To767(writer.bytes());
        Reader reader = new Reader(out);
        assertEquals(1, reader.readVarInt());
        assertEquals(1, reader.readVarInt());
        assertEquals(21, reader.readVarInt()); // generic.movement_speed（767）
        assertEquals(0.1, reader.readDouble());
        assertEquals(1, reader.readVarInt());
        assertEquals("01020304-0506-0708-090a-0b0c0d0e0f10", reader.readString());
        assertEquals(0.5, reader.readDouble());
        assertEquals(0, reader.readByte());
    }

    @Test
    void updateAttributes767To766() {
        Writer writer = new Writer();
        writer.writeVarInt(1);
        writer.writeVarInt(1);
        writer.writeVarInt(21); // generic.movement_speed（767）
        writer.writeDouble(0.1);
        writer.writeVarInt(1);
        writer.writeString("123e4567-e89b-12d3-a456-426614174000");
        writer.writeDouble(0.5);
        writer.writeByte(0);
        byte[] out = ChunkTranslator.translateUpdateAttributes767To766(writer.bytes());
        Reader reader = new Reader(out);
        assertEquals(1, reader.readVarInt());
        assertEquals(1, reader.readVarInt());
        assertEquals(17, reader.readVarInt());
        assertEquals(0.1, reader.readDouble());
        assertEquals(1, reader.readVarInt());
        assertEquals(UUID.fromString("123e4567-e89b-12d3-a456-426614174000"), reader.readUuid());
        assertEquals(0.5, reader.readDouble());
        assertEquals(0, reader.readByte());
    }

    @Test
    void updateAttributes767To766NewAttributeDropped() {
        Writer writer = new Writer();
        writer.writeVarInt(1);
        writer.writeVarInt(1);
        writer.writeVarInt(19); // player.mining_efficiency（767）
        writer.writeDouble(0.1);
        writer.writeVarInt(0);
        byte[] out = ChunkTranslator.translateUpdateAttributes767To766(writer.bytes());
        Reader reader = new Reader(out);
        assertEquals(1, reader.readVarInt());
        assertEquals(0, reader.readVarInt()); // プロパティ 0件に減る
    }

    @Test
    void updateAttributes765To767() {
        // 765 のワイヤ上のキーは "minecraft:generic.movement_speed" のような
        // 接頭辞付き（本物のバニラ1.20.4から受け取って確認済み）。
        Writer writer = new Writer();
        writer.writeVarInt(1);
        writer.writeVarInt(2);
        writer.writeString("minecraft:generic.movement_speed");
        writer.writeDouble(0.1);
        writer.writeVarInt(0);
        writer.writeString("minecraft:horse.jump_strength");
        writer.writeDouble(0.2);
        writer.writeVarInt(0);
        byte[] out = ChunkTranslator.translateUpdateAttributes765To767(writer.bytes());
        Reader reader = new Reader(out);
        assertEquals(1, reader.readVarInt());
        assertEquals(2, reader.readVarInt());
        assertEquals(21, reader.readVarInt()); // generic.movement_speed（767）
        assertEquals(0.1, reader.readDouble());
        assertEquals(0, reader.readVarInt());
        assertEquals(14, reader.readVarInt()); // horse.jump_strength → generic.jump_strength（767）
        assertEquals(0.2, reader.readDouble());
        assertEquals(0, reader.readVarInt());
    }

    @Test
    void updateAttributes767To765() {
        Writer writer = new Writer();
        writer.writeVarInt(1);
        writer.writeVarInt(3);
        writer.writeVarInt(14); // generic.jump_strength（767）
        writer.writeDouble(0.1);
        writer.writeVarInt(0);
        writer.writeVarInt(19); // player.mining_efficiency（767）
        writer.writeDouble(0.2);
        writer.writeVarInt(0);
        writer.writeVarInt(21); // generic.movement_speed（767）
        writer.writeDouble(0.3);
        writer.writeVarInt(0);
        byte[] out = ChunkTranslator.translateUpdateAttributes767To765(writer.bytes());
        Reader reader = new Reader(out);
        assertEquals(1, reader.readVarInt());
        assertEquals(2, reader.readVarInt()); // mining_efficiency は落とす
        // 765 のワイヤ上のキーは接頭辞付きで出す。
        assertEquals("minecraft:horse.jump_strength", reader.readString());
        assertEquals(0.1, reader.readDouble());
        assertEquals(0, reader.readVarInt());
        assertEquals("minecraft:generic.movement_speed", reader.readString());
        assertEquals(0.3, reader.readDouble());
        assertEquals(0, reader.readVarInt());
    }

    // ---- Damage Event / World Particles / 音 ----

    @Test
    void damageEvent766To767() {
        Writer writer = new Writer();
        writer.writeVarInt(1); // entityId
        writer.writeVarInt(0); // arrow
        writer.writeVarInt(0); // sourceCauseId
        writer.writeVarInt(0); // sourceDirectId
        writer.writeBoolean(false); // sourcePosition 無し
        byte[] out = ChunkTranslator.translateDamageEvent766To767(writer.bytes());
        Reader reader = new Reader(out);
        assertEquals(1, reader.readVarInt());
        assertEquals(0, reader.readVarInt());
        assertEquals(0, reader.readVarInt());
        assertEquals(0, reader.readVarInt());
        assertEquals(false, reader.readBoolean());
    }

    @Test
    void damageEvent767To766CampfireDropped() {
        Writer writer = new Writer();
        writer.writeVarInt(1);
        writer.writeVarInt(3); // campfire（767）
        writer.writeVarInt(0);
        writer.writeVarInt(0);
        writer.writeBoolean(false);
        assertNull(ChunkTranslator.translateDamageEvent767To766(writer.bytes()));
    }

    @Test
    void damageEvent767To766WindChargeDropped() {
        Writer writer = new Writer();
        writer.writeVarInt(1);
        writer.writeVarInt(44); // wind_charge（767）
        writer.writeVarInt(0);
        writer.writeVarInt(0);
        writer.writeBoolean(false);
        assertNull(ChunkTranslator.translateDamageEvent767To766(writer.bytes()));
    }

    @Test
    void worldParticles766To767Item() {
        Writer writer = new Writer();
        writer.writeBoolean(false); // longDistance
        writer.writeDouble(0.0);
        writer.writeDouble(64.0);
        writer.writeDouble(0.0);
        writer.writeFloat(0.0f);
        writer.writeFloat(0.0f);
        writer.writeFloat(0.0f);
        writer.writeFloat(1.0f); // speed
        writer.writeInt(1); // count
        writer.writeVarInt(44); // item 粒子
        writeSlot(writer, 848, 1);
        byte[] out = ChunkTranslator.translateWorldParticles766To767(writer.bytes());
        Reader reader = new Reader(out);
        assertEquals(false, reader.readBoolean());
        assertEquals(0.0, reader.readDouble());
        assertEquals(64.0, reader.readDouble());
        assertEquals(0.0, reader.readDouble());
        assertEquals(0.0f, reader.readFloat());
        assertEquals(0.0f, reader.readFloat());
        assertEquals(0.0f, reader.readFloat());
        assertEquals(1.0f, reader.readFloat());
        assertEquals(1, reader.readInt());
        assertEquals(44, reader.readVarInt());
        assertEquals(1, reader.readByte());
        assertEquals(799, reader.readVarInt());
    }

    @Test
    void soundEffect766To767() {
        Writer writer = new Writer();
        writer.writeVarInt(887); // 音 886 の Holder（参照）
        writer.writeVarInt(0); // category
        writer.writeInt(0);
        writer.writeInt(0);
        writer.writeInt(0);
        writer.writeFloat(1.0f);
        writer.writeFloat(1.0f);
        writer.writeLong(0L);
        byte[] out = ChunkTranslator.translateSoundEffect766To767(writer.bytes());
        Reader reader = new Reader(out);
        assertEquals(890, reader.readVarInt()); // 音 889 の Holder
        assertEquals(0, reader.readVarInt());
    }

    @Test
    void stopSound766To767() {
        Writer writer = new Writer();
        writer.writeByte(2); // flags：音の名前あり
        writer.writeString("block.trial_spawner.charge_activate");
        byte[] out = ChunkTranslator.translateStopSound766To767(writer.bytes());
        Reader reader = new Reader(out);
        assertEquals(2, reader.readByte());
        assertEquals("block.trial_spawner.ominous_activate", reader.readString());
    }

    @Test
    void setCreativeSlot767To766() {
        Writer writer = new Writer();
        writer.writeShort(5);
        writeSlot(writer, 799, 1); // bowl（767）
        byte[] out = ChunkTranslator.translateSetCreativeSlot767To766(writer.bytes());
        Reader reader = new Reader(out);
        assertEquals(5, reader.readShort());
        assertEquals(1, reader.readByte());
        assertEquals(848, reader.readVarInt()); // bowl（766）
    }
}
