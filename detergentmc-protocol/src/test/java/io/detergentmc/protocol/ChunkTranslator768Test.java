// Copyright (c) 2026 DetergentMC contributors.
// SPDX-License-Identifier: Apache-2.0

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
 * 767（1.21／1.21.1）⇔768（1.21.2／1.21.3）の変換テスト。
 * 期待値は ViaVersion mapping-1.21.json／mapping-1.21.2.json と
 * minecraft-data のレジストリから計算したもの（推測ではない）。
 */
class ChunkTranslator768Test {

    // ---- ヘルパー ----

    /** 空のコンパウンド NBT。 */
    private static Tag emptyCompound() {
        return new Tag.CompoundTag(Map.of());
    }

    /** スロット（767／768共通の形：個数 VarInt＋アイテムID＋追加数＋削除数）。追加・削除は 0。 */
    private static void writeSlot(Writer writer, int itemId, int count) {
        writer.writeVarInt(count);
        writer.writeVarInt(itemId);
        writer.writeVarInt(0);
        writer.writeVarInt(0);
    }

    /** 空きスロット（個数 0）。 */
    private static void writeEmptySlot(Writer writer) {
        writer.writeVarInt(0);
    }

    /** スロットの先頭（個数＋アイテムID）を読み、追加コンポーネント数を返す。削除数も読み飛ばす。 */
    private static int[] readSlotHead(Reader reader) {
        int count = reader.readVarInt();
        int itemId = reader.readVarInt();
        int added = reader.readVarInt();
        int removed = reader.readVarInt();
        return new int[]{count, itemId, added, removed};
    }

    /** Set Slot ペイロード（767／768共通：windowId＋stateId＋slotIndex＋スロット）。 */
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

    /** 高さマップ（名前なし NBT のコンパウンド）。 */
    private static Tag heightmaps() {
        return new Tag.CompoundTag(Map.of(
                "MOTION_BLOCKING", new Tag.LongArrayTag(new long[37]),
                "WORLD_SURFACE", new Tag.LongArrayTag(new long[37])));
    }

    /** 間接パレットのセクション1個（ブロック＋バイオームはシングル）。 */
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

    /** 間接パレットのセクション1個（ブロック＋バイオームともに間接パレット）。 */
    private static byte[] indirectSectionWithBiomePalette(int[] blockPalette, int[] biomePalette) {
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
        writer.writeUnsignedByte(2); // バイオーム BPE（間接）
        writer.writeVarInt(biomePalette.length);
        for (int value : biomePalette) {
            writer.writeVarInt(value);
        }
        writer.writeVarInt(16); // Data Array：64エントリ×2bit＝16 long
        for (int i = 0; i < 16; i++) {
            writer.writeLong(0L);
        }
        return writer.bytes();
    }

    /** セクションのブロックパレットだけ読み、ブロック状態IDの配列を返す。 */
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

    /** セクションのバイオームパレットだけ読み、バイオームIDの配列を返す。 */
    private static int[] readBiomePalette(byte[] sections) {
        Reader section = new Reader(sections);
        section.readShort();
        section.readUnsignedByte();
        int paletteSize = section.readVarInt();
        for (int i = 0; i < paletteSize; i++) {
            section.readVarInt();
        }
        int dataLength = section.readVarInt();
        for (int i = 0; i < dataLength; i++) {
            section.readLong();
        }
        section.readUnsignedByte(); // バイオーム BPE
        int biomeSize = section.readVarInt();
        int[] palette = new int[biomeSize];
        for (int i = 0; i < biomeSize; i++) {
            palette[i] = section.readVarInt();
        }
        return palette;
    }

    /** セクションのバイオーム（シングル）を読み、バイオームIDを返す。 */
    private static int readSingleBiome(byte[] sections) {
        Reader section = new Reader(sections);
        section.readShort();
        section.readUnsignedByte();
        int paletteSize = section.readVarInt();
        for (int i = 0; i < paletteSize; i++) {
            section.readVarInt();
        }
        int dataLength = section.readVarInt();
        for (int i = 0; i < dataLength; i++) {
            section.readLong();
        }
        section.readUnsignedByte(); // バイオーム BPE（0＝シングル）
        return section.readVarInt();
    }

    // ---- Set Slot / アイテムID ----

    @Test
    void setSlot767To768TurtleHelmetMoves() {
        // turtle_helmet は 767の 794 から 768の 816 へ移る。
        byte[] out = ChunkTranslator.translateSetSlot767To768(setSlotPacket(794));
        assertEquals(816, readSetSlot(new Reader(out))[2]);
    }

    @Test
    void setSlot767To768DiscsShift() {
        // music_disc_creator 1172→1214、breeze_rod 1332→1134。
        byte[] out = ChunkTranslator.translateSetSlot767To768(setSlotPacket(1172));
        assertEquals(1214, readSetSlot(new Reader(out))[2]);
        byte[] out2 = ChunkTranslator.translateSetSlot767To768(setSlotPacket(1332));
        assertEquals(1134, readSetSlot(new Reader(out2))[2]);
    }

    @Test
    void setSlot768To767NewItemsBecomeStone() {
        // 768にしか無い pale_oak_log（140）／white_bundle（955）／creaking_spawn_egg（1124）は石（1）になる。
        assertEquals(1, readSetSlot(new Reader(
                ChunkTranslator.translateSetSlot768To767(setSlotPacket(140))))[2]);
        assertEquals(1, readSetSlot(new Reader(
                ChunkTranslator.translateSetSlot768To767(setSlotPacket(955))))[2]);
        assertEquals(1, readSetSlot(new Reader(
                ChunkTranslator.translateSetSlot768To767(setSlotPacket(1124))))[2]);
    }

    @Test
    void setSlot768To767OminousBottleMovesBack() {
        // ominous_bottle は 768の 1374 から 767の 1331 へ戻る。
        byte[] out = ChunkTranslator.translateSetSlot768To767(setSlotPacket(1374));
        assertEquals(1331, readSetSlot(new Reader(out))[2]);
    }

    @Test
    void setSlot767To768EnchantmentsIdentity() {
        // mending は 767も768も 22。エンチャントの並びは同一。
        Writer writer = new Writer();
        writer.writeByte(0);
        writer.writeVarInt(0);
        writer.writeShort(0);
        writer.writeVarInt(1);
        writer.writeVarInt(1); // stone
        writer.writeVarInt(1); // added 1
        writer.writeVarInt(9); // enchantments
        writer.writeVarInt(1); // count 1
        writer.writeVarInt(22); // mending
        writer.writeVarInt(1); // level 1
        writer.writeBoolean(true); // showTooltip
        writer.writeVarInt(0); // removed 0
        byte[] out = ChunkTranslator.translateSetSlot767To768(writer.bytes());
        Reader reader = new Reader(out);
        reader.readByte();
        reader.readVarInt();
        reader.readShort();
        assertEquals(1, reader.readVarInt());
        assertEquals(1, reader.readVarInt());
        assertEquals(1, reader.readVarInt()); // added 1
        assertEquals(10, reader.readVarInt()); // enchantments（768。item_model 挿入で 9→10）
        assertEquals(1, reader.readVarInt()); // count 1
        assertEquals(22, reader.readVarInt()); // mending（768）
        assertEquals(1, reader.readVarInt()); // level 1
        assertTrue(reader.readBoolean());
        assertEquals(0, reader.readVarInt()); // removed 0
    }

    // ---- custom_model_data / food / 成分の追加・削除 ----

    @Test
    void customModelData767To768BecomesColors() {
        // 767の int 42 は 768の colors[42]（floats／flags／strings は空）になる。
        Writer writer = new Writer();
        writer.writeByte(0);
        writer.writeVarInt(0);
        writer.writeShort(0);
        writer.writeVarInt(1);
        writer.writeVarInt(1);
        writer.writeVarInt(1); // added 1
        writer.writeVarInt(13); // custom_model_data（767）
        writer.writeVarInt(42);
        writer.writeVarInt(0); // removed 0
        byte[] out = ChunkTranslator.translateSetSlot767To768(writer.bytes());
        Reader reader = new Reader(out);
        reader.readByte();
        reader.readVarInt();
        reader.readShort();
        assertEquals(1, reader.readVarInt());
        assertEquals(1, reader.readVarInt());
        assertEquals(1, reader.readVarInt()); // added 1
        assertEquals(14, reader.readVarInt()); // custom_model_data（768）
        assertEquals(0, reader.readVarInt()); // floats
        assertEquals(0, reader.readVarInt()); // flags
        assertEquals(0, reader.readVarInt()); // strings
        assertEquals(1, reader.readVarInt()); // colors
        assertEquals(42, reader.readInt());
        assertEquals(0, reader.readVarInt()); // removed 0
    }

    @Test
    void customModelData768To767PrefersColors() {
        // 768の colors[7] は 767の int 7 になる。
        Writer writer = new Writer();
        writer.writeByte(0);
        writer.writeVarInt(0);
        writer.writeShort(0);
        writer.writeVarInt(1);
        writer.writeVarInt(1);
        writer.writeVarInt(1); // added 1
        writer.writeVarInt(14); // custom_model_data（768）
        writer.writeVarInt(0); // floats
        writer.writeVarInt(0); // flags
        writer.writeVarInt(0); // strings
        writer.writeVarInt(1); // colors
        writer.writeInt(7);
        writer.writeVarInt(0); // removed 0
        byte[] out = ChunkTranslator.translateSetSlot768To767(writer.bytes());
        Reader reader = new Reader(out);
        reader.readByte();
        reader.readVarInt();
        reader.readShort();
        assertEquals(1, reader.readVarInt());
        assertEquals(1, reader.readVarInt());
        assertEquals(1, reader.readVarInt()); // added 1
        assertEquals(13, reader.readVarInt()); // custom_model_data（767）
        assertEquals(7, reader.readVarInt());
        assertEquals(0, reader.readVarInt()); // removed 0
    }

    @Test
    void customModelData768To767FloatsFallback() {
        // colors が無く floats[1.5] だけのときは int 1（小数部は失われる）。
        Writer writer = new Writer();
        writer.writeByte(0);
        writer.writeVarInt(0);
        writer.writeShort(0);
        writer.writeVarInt(1);
        writer.writeVarInt(1);
        writer.writeVarInt(1); // added 1
        writer.writeVarInt(14); // custom_model_data（768）
        writer.writeVarInt(1); // floats
        writer.writeFloat(1.5f);
        writer.writeVarInt(0); // flags
        writer.writeVarInt(0); // strings
        writer.writeVarInt(0); // colors
        writer.writeVarInt(0); // removed 0
        byte[] out = ChunkTranslator.translateSetSlot768To767(writer.bytes());
        Reader reader = new Reader(out);
        reader.readByte();
        reader.readVarInt();
        reader.readShort();
        reader.readVarInt();
        reader.readVarInt();
        reader.readVarInt();
        assertEquals(13, reader.readVarInt()); // custom_model_data（767）
        assertEquals(1, reader.readVarInt());
    }

    @Test
    void food767To768DropsEffects() {
        // 767の food（効果1件付き）は 768の food（栄養・満腹度・常時食べ可のみ）になり、効果は落とす。
        Writer writer = new Writer();
        writer.writeByte(0);
        writer.writeVarInt(0);
        writer.writeShort(0);
        writer.writeVarInt(1);
        writer.writeVarInt(1);
        writer.writeVarInt(1); // added 1
        writer.writeVarInt(20); // food（767）
        writer.writeVarInt(4); // nutrition
        writer.writeFloat(0.3f); // saturationModifier
        writer.writeBoolean(true); // canAlwaysEat
        writer.writeFloat(1.6f); // secondsToEat
        writeEmptySlot(writer); // usingConvertsTo（空）
        writer.writeVarInt(1); // effects 1件
        writer.writeVarInt(3); // effect
        writer.writeFloat(0.5f); // probability
        writer.writeVarInt(0); // removed 0
        byte[] out = ChunkTranslator.translateSetSlot767To768(writer.bytes());
        Reader reader = new Reader(out);
        reader.readByte();
        reader.readVarInt();
        reader.readShort();
        assertEquals(1, reader.readVarInt());
        assertEquals(1, reader.readVarInt());
        assertEquals(1, reader.readVarInt()); // added 1
        assertEquals(21, reader.readVarInt()); // food（768）
        assertEquals(4, reader.readVarInt()); // nutrition
        assertEquals(0.3f, reader.readFloat(), 0.0001f);
        assertTrue(reader.readBoolean()); // canAlwaysEat
        assertEquals(0, reader.readVarInt()); // removed 0
    }

    @Test
    void food768To767FillsDefaults() {
        // 768の food は 767の food になり、secondsToEat／usingConvertsTo／effects は既定値で補う。
        Writer writer = new Writer();
        writer.writeByte(0);
        writer.writeVarInt(0);
        writer.writeShort(0);
        writer.writeVarInt(1);
        writer.writeVarInt(1);
        writer.writeVarInt(1); // added 1
        writer.writeVarInt(21); // food（768）
        writer.writeVarInt(5); // nutrition
        writer.writeFloat(0.6f); // saturationModifier
        writer.writeBoolean(false); // canAlwaysEat
        writer.writeVarInt(0); // removed 0
        byte[] out = ChunkTranslator.translateSetSlot768To767(writer.bytes());
        Reader reader = new Reader(out);
        reader.readByte();
        reader.readVarInt();
        reader.readShort();
        assertEquals(1, reader.readVarInt());
        assertEquals(1, reader.readVarInt());
        assertEquals(1, reader.readVarInt()); // added 1
        assertEquals(20, reader.readVarInt()); // food（767）
        assertEquals(5, reader.readVarInt()); // nutrition
        assertEquals(0.6f, reader.readFloat(), 0.0001f);
        assertEquals(false, reader.readBoolean()); // canAlwaysEat
        assertEquals(1.6f, reader.readFloat(), 0.0001f); // secondsToEat（既定値）
        assertEquals(0, reader.readVarInt()); // usingConvertsTo（空スロット）
        assertEquals(0, reader.readVarInt()); // effects（無し）
        assertEquals(0, reader.readVarInt()); // removed 0
    }

    @Test
    void fireResistant767To768Dropped() {
        // 767の fire_resistant（21）は 768に無いため落とす。追加成分は 0 件になる。
        Writer writer = new Writer();
        writer.writeByte(0);
        writer.writeVarInt(0);
        writer.writeShort(0);
        writer.writeVarInt(1);
        writer.writeVarInt(1);
        writer.writeVarInt(1); // added 1
        writer.writeVarInt(21); // fire_resistant（void、中身なし）
        writer.writeVarInt(0); // removed 0
        byte[] out = ChunkTranslator.translateSetSlot767To768(writer.bytes());
        Reader reader = new Reader(out);
        reader.readByte();
        reader.readVarInt();
        reader.readShort();
        assertEquals(1, reader.readVarInt());
        assertEquals(1, reader.readVarInt());
        assertEquals(0, reader.readVarInt()); // added 0
        assertEquals(0, reader.readVarInt()); // removed 0
    }

    @Test
    void newComponents768To767Dropped() {
        // 768の item_model（7）と consumable（22）は 767に無いため落とす。追加成分は 0 件になる。
        Writer writer = new Writer();
        writer.writeByte(0);
        writer.writeVarInt(0);
        writer.writeShort(0);
        writer.writeVarInt(1);
        writer.writeVarInt(1);
        writer.writeVarInt(2); // added 2
        writer.writeVarInt(7); // item_model
        writer.writeString("minecraft:stone");
        writer.writeVarInt(22); // consumable
        writer.writeFloat(1.6f); // consume_seconds
        writer.writeVarInt(1); // animation（eat）
        writer.writeVarInt(1); // sound holder（参照 0＝stone.break）
        writer.writeBoolean(true); // makes_particles
        writer.writeVarInt(0); // effects 0件
        writer.writeVarInt(0); // removed 0
        byte[] out = ChunkTranslator.translateSetSlot768To767(writer.bytes());
        Reader reader = new Reader(out);
        reader.readByte();
        reader.readVarInt();
        reader.readShort();
        assertEquals(1, reader.readVarInt());
        assertEquals(1, reader.readVarInt());
        assertEquals(0, reader.readVarInt()); // added 0
        assertEquals(0, reader.readVarInt()); // removed 0
    }

    @Test
    void removedComponent767To768RemapsKind() {
        // 削除リストの種別も付け替える。food（20）→21、fire_resistant（21）は落とす。
        Writer writer = new Writer();
        writer.writeByte(0);
        writer.writeVarInt(0);
        writer.writeShort(0);
        writer.writeVarInt(1);
        writer.writeVarInt(1);
        writer.writeVarInt(0); // added 0
        writer.writeVarInt(2); // removed 2
        writer.writeVarInt(20); // food
        writer.writeVarInt(21); // fire_resistant
        byte[] out = ChunkTranslator.translateSetSlot767To768(writer.bytes());
        Reader reader = new Reader(out);
        reader.readByte();
        reader.readVarInt();
        reader.readShort();
        assertEquals(1, reader.readVarInt());
        assertEquals(1, reader.readVarInt());
        assertEquals(0, reader.readVarInt()); // added 0
        assertEquals(1, reader.readVarInt()); // removed 1
        assertEquals(21, reader.readVarInt()); // food（768）
    }

    // ---- attribute_modifiers / trim / banner / pot / tool / can_place_on ----

    @Test
    void attributeModifiers767To768Renames() {
        // water_movement_efficiency は 767の 30 から 768の 31 へ、burning_time は 7 のまま。
        Writer writer = new Writer();
        writer.writeByte(0);
        writer.writeVarInt(0);
        writer.writeShort(0);
        writer.writeVarInt(1);
        writer.writeVarInt(1);
        writer.writeVarInt(1); // added 1
        writer.writeVarInt(12); // attribute_modifiers（767）
        writer.writeVarInt(2); // count 2
        writer.writeVarInt(30); // water_movement_efficiency
        writer.writeString("test");
        writer.writeDouble(1.5);
        writer.writeVarInt(0);
        writer.writeVarInt(0);
        writer.writeVarInt(7); // burning_time
        writer.writeString("test2");
        writer.writeDouble(2.0);
        writer.writeVarInt(1);
        writer.writeVarInt(1);
        writer.writeVarInt(0); // removed 0
        byte[] out = ChunkTranslator.translateSetSlot767To768(writer.bytes());
        Reader reader = new Reader(out);
        reader.readByte();
        reader.readVarInt();
        reader.readShort();
        assertEquals(1, reader.readVarInt());
        assertEquals(1, reader.readVarInt());
        assertEquals(1, reader.readVarInt()); // added 1
        assertEquals(13, reader.readVarInt()); // attribute_modifiers（768）
        assertEquals(2, reader.readVarInt()); // count 2
        assertEquals(31, reader.readVarInt()); // water_movement_efficiency（768）
        assertEquals("test", reader.readString());
        assertEquals(1.5, reader.readDouble(), 0.0001);
        assertEquals(0, reader.readVarInt());
        assertEquals(0, reader.readVarInt());
        assertEquals(7, reader.readVarInt()); // burning_time（768）
        assertEquals("test2", reader.readString());
        assertEquals(2.0, reader.readDouble(), 0.0001);
        assertEquals(1, reader.readVarInt());
        assertEquals(1, reader.readVarInt());
        assertEquals(0, reader.readVarInt()); // removed 0
    }

    @Test
    void attributeModifiers768To767TemptRangeDropped() {
        // tempt_range（768の 30）は 767に無いため修飾子ごと落とす。water_movement_efficiency は 31→30。
        Writer writer = new Writer();
        writer.writeByte(0);
        writer.writeVarInt(0);
        writer.writeShort(0);
        writer.writeVarInt(1);
        writer.writeVarInt(1);
        writer.writeVarInt(1); // added 1
        writer.writeVarInt(13); // attribute_modifiers（768）
        writer.writeVarInt(2); // count 2
        writer.writeVarInt(30); // tempt_range
        writer.writeString("tempt");
        writer.writeDouble(3.0);
        writer.writeVarInt(0);
        writer.writeVarInt(0);
        writer.writeVarInt(31); // water_movement_efficiency
        writer.writeString("water");
        writer.writeDouble(1.0);
        writer.writeVarInt(0);
        writer.writeVarInt(0);
        writer.writeVarInt(0); // removed 0
        byte[] out = ChunkTranslator.translateSetSlot768To767(writer.bytes());
        Reader reader = new Reader(out);
        reader.readByte();
        reader.readVarInt();
        reader.readShort();
        assertEquals(1, reader.readVarInt());
        assertEquals(1, reader.readVarInt());
        assertEquals(1, reader.readVarInt()); // added 1
        assertEquals(12, reader.readVarInt()); // attribute_modifiers（767）
        assertEquals(1, reader.readVarInt()); // count 1（tempt_range は落とした）
        assertEquals(30, reader.readVarInt()); // water_movement_efficiency（767）
        assertEquals("water", reader.readString());
        assertEquals(1.0, reader.readDouble(), 0.0001);
        assertEquals(0, reader.readVarInt());
        assertEquals(0, reader.readVarInt());
        assertEquals(0, reader.readVarInt()); // removed 0
    }

    @Test
    void trim767To768InlineMaterial() {
        // trim の直書きの材料（ingredient は turtle_helmet 794→816）は付け替える。型は参照のまま。
        Writer writer = new Writer();
        writer.writeByte(0);
        writer.writeVarInt(0);
        writer.writeShort(0);
        writer.writeVarInt(1);
        writer.writeVarInt(1);
        writer.writeVarInt(1); // added 1
        writer.writeVarInt(35); // trim（767）
        writer.writeVarInt(0); // material（直書き）
        writer.writeString("minecraft:quartz");
        writer.writeVarInt(794); // ingredient（turtle_helmet）
        writer.writeVarInt(0); // overrides 0件
        writer.writeBoolean(false); // description 無し
        writer.writeVarInt(1); // pattern（参照 0）
        writer.writeBoolean(true); // showInTooltip
        writer.writeVarInt(0); // removed 0
        byte[] out = ChunkTranslator.translateSetSlot767To768(writer.bytes());
        Reader reader = new Reader(out);
        reader.readByte();
        reader.readVarInt();
        reader.readShort();
        assertEquals(1, reader.readVarInt());
        assertEquals(1, reader.readVarInt());
        assertEquals(1, reader.readVarInt()); // added 1
        assertEquals(45, reader.readVarInt()); // trim（768）
        assertEquals(0, reader.readVarInt()); // material（直書き）
        assertEquals("minecraft:quartz", reader.readString());
        assertEquals(816, reader.readVarInt()); // ingredient（768）
        assertEquals(0, reader.readVarInt()); // overrides 0件
        assertEquals(false, reader.readBoolean()); // description 無し
        assertEquals(1, reader.readVarInt()); // pattern（参照 0）
        assertEquals(true, reader.readBoolean()); // showInTooltip
        assertEquals(0, reader.readVarInt()); // removed 0
    }

    @Test
    void bannerPatterns767To768Identity() {
        // banner_patterns のパターンIDは同一（43種）。層はそのまま。
        Writer writer = new Writer();
        writer.writeByte(0);
        writer.writeVarInt(0);
        writer.writeShort(0);
        writer.writeVarInt(1);
        writer.writeVarInt(1);
        writer.writeVarInt(1); // added 1
        writer.writeVarInt(49); // banner_patterns（767）
        writer.writeVarInt(1); // count 1
        writer.writeVarInt(2); // pattern 参照 1
        writer.writeVarInt(3); // color
        writer.writeVarInt(0); // removed 0
        byte[] out = ChunkTranslator.translateSetSlot767To768(writer.bytes());
        Reader reader = new Reader(out);
        reader.readByte();
        reader.readVarInt();
        reader.readShort();
        assertEquals(1, reader.readVarInt());
        assertEquals(1, reader.readVarInt());
        assertEquals(1, reader.readVarInt()); // added 1
        assertEquals(59, reader.readVarInt()); // banner_patterns（768）
        assertEquals(1, reader.readVarInt()); // count 1
        assertEquals(2, reader.readVarInt()); // pattern 参照 1
        assertEquals(3, reader.readVarInt()); // color
        assertEquals(0, reader.readVarInt()); // removed 0
    }

    @Test
    void potDecorations767To768ItemIds() {
        // pot_decorations の中身はアイテムID（turtle_helmet 794→816）。
        Writer writer = new Writer();
        writer.writeByte(0);
        writer.writeVarInt(0);
        writer.writeShort(0);
        writer.writeVarInt(1);
        writer.writeVarInt(1);
        writer.writeVarInt(1); // added 1
        writer.writeVarInt(51); // pot_decorations（767）
        writer.writeVarInt(1); // count 1
        writer.writeVarInt(794); // turtle_helmet
        writer.writeVarInt(0); // removed 0
        byte[] out = ChunkTranslator.translateSetSlot767To768(writer.bytes());
        Reader reader = new Reader(out);
        reader.readByte();
        reader.readVarInt();
        reader.readShort();
        assertEquals(1, reader.readVarInt());
        assertEquals(1, reader.readVarInt());
        assertEquals(1, reader.readVarInt()); // added 1
        assertEquals(61, reader.readVarInt()); // pot_decorations（768）
        assertEquals(1, reader.readVarInt()); // count 1
        assertEquals(816, reader.readVarInt()); // turtle_helmet（768）
        assertEquals(0, reader.readVarInt()); // removed 0
    }

    @Test
    void tool767To768BlockIds() {
        // tool のルールの中のブロックID（heavy_core 1059→1080）を付け替える。
        Writer writer = new Writer();
        writer.writeByte(0);
        writer.writeVarInt(0);
        writer.writeShort(0);
        writer.writeVarInt(1);
        writer.writeVarInt(1);
        writer.writeVarInt(1); // added 1
        writer.writeVarInt(22); // tool（767）
        writer.writeVarInt(1); // rules 1件
        writer.writeVarInt(2); // IDSet：1件のID
        writer.writeVarInt(1059); // heavy_core
        writer.writeBoolean(true); // speed 有り
        writer.writeFloat(2.0f);
        writer.writeBoolean(false); // correctDropForBlocks 無し
        writer.writeFloat(1.0f); // defaultMiningSpeed
        writer.writeVarInt(1); // damagePerBlock
        writer.writeVarInt(0); // removed 0
        byte[] out = ChunkTranslator.translateSetSlot767To768(writer.bytes());
        Reader reader = new Reader(out);
        reader.readByte();
        reader.readVarInt();
        reader.readShort();
        assertEquals(1, reader.readVarInt());
        assertEquals(1, reader.readVarInt());
        assertEquals(1, reader.readVarInt()); // added 1
        assertEquals(26, reader.readVarInt()); // tool（768）
        assertEquals(1, reader.readVarInt()); // rules 1件
        assertEquals(2, reader.readVarInt()); // IDSet：1件のID
        assertEquals(1080, reader.readVarInt()); // heavy_core（768）
        assertEquals(true, reader.readBoolean());
        assertEquals(2.0f, reader.readFloat(), 0.0001f);
        assertEquals(false, reader.readBoolean());
        assertEquals(1.0f, reader.readFloat(), 0.0001f);
        assertEquals(1, reader.readVarInt()); // damagePerBlock
        assertEquals(0, reader.readVarInt()); // removed 0
    }

    @Test
    void canPlaceOn767To768BlockIds() {
        // can_place_on の述語の中のブロックID（heavy_core 1059→1080）を付け替える。
        Writer writer = new Writer();
        writer.writeByte(0);
        writer.writeVarInt(0);
        writer.writeShort(0);
        writer.writeVarInt(1);
        writer.writeVarInt(1);
        writer.writeVarInt(1); // added 1
        writer.writeVarInt(10); // can_place_on（767）
        writer.writeVarInt(1); // predicates 1件
        writer.writeBoolean(true); // hasBlocks
        writer.writeVarInt(2); // IDSet：1件のID
        writer.writeVarInt(1059); // heavy_core
        writer.writeBoolean(false); // hasProps 無し
        writer.writeAnonymousNbt(emptyCompound()); // nbt
        writer.writeBoolean(true); // showTooltip
        writer.writeVarInt(0); // removed 0
        byte[] out = ChunkTranslator.translateSetSlot767To768(writer.bytes());
        Reader reader = new Reader(out);
        reader.readByte();
        reader.readVarInt();
        reader.readShort();
        assertEquals(1, reader.readVarInt());
        assertEquals(1, reader.readVarInt());
        assertEquals(1, reader.readVarInt()); // added 1
        assertEquals(11, reader.readVarInt()); // can_place_on（768）
        assertEquals(1, reader.readVarInt()); // predicates 1件
        assertEquals(true, reader.readBoolean()); // hasBlocks
        assertEquals(2, reader.readVarInt()); // IDSet：1件のID
        assertEquals(1080, reader.readVarInt()); // heavy_core（768）
        assertEquals(false, reader.readBoolean()); // hasProps 無し
        assertTrue(reader.readAnonymousNbt().tag() instanceof Tag.CompoundTag); // nbt
        assertEquals(true, reader.readBoolean()); // showTooltip
        assertEquals(0, reader.readVarInt()); // removed 0
    }

    @Test
    void jukeboxPlayable767To768SoundHolder() {
        // jukebox_playable の直書きの曲データ（音の Holder）は付け替える（stone.break 1361→1386）。
        Writer writer = new Writer();
        writer.writeByte(0);
        writer.writeVarInt(0);
        writer.writeShort(0);
        writer.writeVarInt(1);
        writer.writeVarInt(1);
        writer.writeVarInt(1); // added 1
        writer.writeVarInt(42); // jukebox_playable（767）
        writer.writeBoolean(true); // hasHolder
        writer.writeVarInt(0); // 直書き
        writer.writeVarInt(1362); // 音の Holder（参照 1361＝stone.break）
        writer.writeAnonymousNbt(emptyCompound()); // description
        writer.writeFloat(3.5f); // lengthInSeconds
        writer.writeVarInt(15); // comparatorOutput
        writer.writeBoolean(true); // showInTooltip
        writer.writeVarInt(0); // removed 0
        byte[] out = ChunkTranslator.translateSetSlot767To768(writer.bytes());
        Reader reader = new Reader(out);
        reader.readByte();
        reader.readVarInt();
        reader.readShort();
        assertEquals(1, reader.readVarInt());
        assertEquals(1, reader.readVarInt());
        assertEquals(1, reader.readVarInt()); // added 1
        assertEquals(52, reader.readVarInt()); // jukebox_playable（768）
        assertEquals(true, reader.readBoolean()); // hasHolder
        assertEquals(0, reader.readVarInt()); // 直書き
        assertEquals(1387, reader.readVarInt()); // 音の Holder（参照 1386＝stone.break）
        assertTrue(reader.readAnonymousNbt().tag() instanceof Tag.CompoundTag); // description
        assertEquals(3.5f, reader.readFloat(), 0.0001f);
        assertEquals(15, reader.readVarInt());
        assertEquals(true, reader.readBoolean()); // showInTooltip
        assertEquals(0, reader.readVarInt()); // removed 0
    }

    @Test
    void noteBlockSound767To768GoatHorn() {
        // note_block_sound の音の名前（goat_horn.play → goat_horn.sound.0）を付け替える。
        Writer writer = new Writer();
        writer.writeByte(0);
        writer.writeVarInt(0);
        writer.writeShort(0);
        writer.writeVarInt(1);
        writer.writeVarInt(1);
        writer.writeVarInt(1); // added 1
        writer.writeVarInt(48); // note_block_sound（767）
        writer.writeString("minecraft:item.goat_horn.play");
        writer.writeVarInt(0); // removed 0
        byte[] out = ChunkTranslator.translateSetSlot767To768(writer.bytes());
        Reader reader = new Reader(out);
        reader.readByte();
        reader.readVarInt();
        reader.readShort();
        assertEquals(1, reader.readVarInt());
        assertEquals(1, reader.readVarInt());
        assertEquals(1, reader.readVarInt()); // added 1
        assertEquals(58, reader.readVarInt()); // note_block_sound（768）
        assertEquals("minecraft:item.goat_horn.sound.0", reader.readString());
        assertEquals(0, reader.readVarInt()); // removed 0
    }

    // ---- パーティクル ----

    /** World Particles ペイロード（longDistance＋座標＋オフセット＋速度＋個数＋粒子）。 */
    private static byte[] worldParticlesPacket(boolean longDistance, int particleId, Writer particleData) {
        Writer writer = new Writer();
        writer.writeBoolean(longDistance);
        writer.writeDouble(1.0);
        writer.writeDouble(2.0);
        writer.writeDouble(3.0);
        writer.writeFloat(0.5f);
        writer.writeFloat(0.25f);
        writer.writeFloat(0.125f);
        writer.writeFloat(1.0f);
        writer.writeInt(10);
        writer.writeVarInt(particleId);
        writer.writeBytes(particleData.bytes());
        return writer.bytes();
    }

    /** World Particles ペイロードの粒子IDだけ読む。 */
    private static int readWorldParticlesId(Reader reader) {
        reader.readBoolean();
        reader.readDouble();
        reader.readDouble();
        reader.readDouble();
        reader.readFloat();
        reader.readFloat();
        reader.readFloat();
        reader.readFloat();
        reader.readInt();
        return reader.readVarInt();
    }

    @Test
    void worldParticles767To768Dust() {
        // dust：赤1.0／緑0.5／青0.0＋大きさ2.0 → 色 0xFF8000＋大きさ2.0。
        Writer data = new Writer();
        data.writeFloat(1.0f);
        data.writeFloat(0.5f);
        data.writeFloat(0.0f);
        data.writeFloat(2.0f);
        byte[] out = ChunkTranslator.translateWorldParticles767To768(
                worldParticlesPacket(false, 13, data));
        Reader reader = new Reader(out);
        assertEquals(13, readWorldParticlesId(reader));
        assertEquals(0xFF8000, reader.readInt());
        assertEquals(2.0f, reader.readFloat(), 0.0001f);
    }

    @Test
    void worldParticles768To767Dust() {
        // dust：色 0xFF8000＋大きさ2.0 → 赤1.0／緑0.50196…／青0.0＋大きさ2.0。
        Writer data = new Writer();
        data.writeInt(0xFF8000);
        data.writeFloat(2.0f);
        byte[] out = ChunkTranslator.translateWorldParticles768To767(
                worldParticlesPacket(false, 13, data));
        Reader reader = new Reader(out);
        assertEquals(13, readWorldParticlesId(reader));
        assertEquals(1.0f, reader.readFloat(), 0.0001f);
        assertEquals(128 / 255.0f, reader.readFloat(), 0.0001f);
        assertEquals(0.0f, reader.readFloat(), 0.0001f);
        assertEquals(2.0f, reader.readFloat(), 0.0001f);
    }

    @Test
    void worldParticles767To768DustColorTransition() {
        // dust_color_transition：from（1,0,0）＋大きさ1.5＋to（0,0,1）→ from 0xFF0000＋to 0x0000FF＋大きさ1.5。
        Writer data = new Writer();
        data.writeFloat(1.0f);
        data.writeFloat(0.0f);
        data.writeFloat(0.0f);
        data.writeFloat(1.5f);
        data.writeFloat(0.0f);
        data.writeFloat(0.0f);
        data.writeFloat(1.0f);
        byte[] out = ChunkTranslator.translateWorldParticles767To768(
                worldParticlesPacket(false, 14, data));
        Reader reader = new Reader(out);
        assertEquals(14, readWorldParticlesId(reader));
        assertEquals(0xFF0000, reader.readInt());
        assertEquals(0x0000FF, reader.readInt());
        assertEquals(1.5f, reader.readFloat(), 0.0001f);
    }

    @Test
    void worldParticles767To768ShriekMoves() {
        // shriek は 767の 99 から 768の 100 へ移る。
        Writer data = new Writer();
        data.writeVarInt(20); // 遅延 tick 数
        byte[] out = ChunkTranslator.translateWorldParticles767To768(
                worldParticlesPacket(false, 99, data));
        Reader reader = new Reader(out);
        assertEquals(100, readWorldParticlesId(reader));
        assertEquals(20, reader.readVarInt());
    }

    @Test
    void worldParticles768To767TrailDropped() {
        // trail（768=46）は767に無いためパケットごと落とす。
        Writer data = new Writer();
        data.writeDouble(0.0);
        data.writeDouble(0.0);
        data.writeDouble(0.0);
        data.writeInt(0xFF0000);
        assertNull(ChunkTranslator.translateWorldParticles768To767(
                worldParticlesPacket(false, 46, data)));
    }

    @Test
    void worldParticles768To767BlockCrumbleDropped() {
        // block_crumble（768=110）は767に無いためパケットごと落とす。
        Writer data = new Writer();
        data.writeVarInt(1); // ブロック状態
        assertNull(ChunkTranslator.translateWorldParticles768To767(
                worldParticlesPacket(false, 110, data)));
    }

    @Test
    void worldParticles768To767DustPillarMoves() {
        // dust_pillar は 768の 106 から 767の 105 へ戻る。
        Writer data = new Writer();
        data.writeVarInt(1); // ブロック状態
        byte[] out = ChunkTranslator.translateWorldParticles768To767(
                worldParticlesPacket(false, 106, data));
        Reader reader = new Reader(out);
        assertEquals(105, readWorldParticlesId(reader));
        assertEquals(1, reader.readVarInt());
    }

    // ---- エンティティメタデータ ----

    /** Entity Metadata ペイロード（entityId＋エントリ＋0xFF）を組み立てる。 */
    private static byte[] metadataPacket(int entityId, Writer entries) {
        Writer writer = new Writer();
        writer.writeVarInt(entityId);
        writer.writeBytes(entries.bytes());
        writer.writeUnsignedByte(0xFF);
        return writer.bytes();
    }

    @Test
    void entityMetadata767To768BlockState() {
        // 型 14（ブロック状態）：trial_spawner 26638→27107。
        Writer entries = new Writer();
        entries.writeUnsignedByte(0);
        entries.writeVarInt(14);
        entries.writeVarInt(26638);
        byte[] out = ChunkTranslator.translateEntityMetadata767To768(metadataPacket(5, entries));
        Reader reader = new Reader(out);
        assertEquals(5, reader.readVarInt());
        assertEquals(0, reader.readUnsignedByte());
        assertEquals(14, reader.readVarInt());
        assertEquals(27107, reader.readVarInt());
        assertEquals(0xFF, reader.readUnsignedByte());
    }

    @Test
    void entityMetadata768To767BlockState() {
        // 型 15（ブロック状態・任意）：vault 27119→26650。
        Writer entries = new Writer();
        entries.writeUnsignedByte(2);
        entries.writeVarInt(15);
        entries.writeVarInt(27119);
        byte[] out = ChunkTranslator.translateEntityMetadata768To767(metadataPacket(5, entries));
        Reader reader = new Reader(out);
        assertEquals(5, reader.readVarInt());
        assertEquals(2, reader.readUnsignedByte());
        assertEquals(15, reader.readVarInt());
        assertEquals(26650, reader.readVarInt());
        assertEquals(0xFF, reader.readUnsignedByte());
    }

    @Test
    void entityMetadata767To768WolfVariantBiome() {
        // 型 23（狼バリアント）：直書きのバイオーム IDSet（old_growth_spruce_taiga 38／plains 39）は
        // 768（既定のサーバー）でも同じ順番のため素通し。
        Writer entries = new Writer();
        entries.writeUnsignedByte(3);
        entries.writeVarInt(23);
        entries.writeVarInt(0); // 直書き
        entries.writeString("minecraft:entity/wolf/wolf_wild");
        entries.writeString("minecraft:entity/wolf/wolf_tame");
        entries.writeString("minecraft:entity/wolf/wolf_angry");
        entries.writeVarInt(3); // IDSet：2件のID
        entries.writeVarInt(38);
        entries.writeVarInt(39);
        byte[] out = ChunkTranslator.translateEntityMetadata767To768(metadataPacket(5, entries));
        Reader reader = new Reader(out);
        assertEquals(5, reader.readVarInt());
        assertEquals(3, reader.readUnsignedByte());
        assertEquals(23, reader.readVarInt());
        assertEquals(0, reader.readVarInt()); // 直書き
        assertEquals("minecraft:entity/wolf/wolf_wild", reader.readString());
        assertEquals("minecraft:entity/wolf/wolf_tame", reader.readString());
        assertEquals("minecraft:entity/wolf/wolf_angry", reader.readString());
        assertEquals(3, reader.readVarInt()); // IDSet：2件のID
        assertEquals(38, reader.readVarInt()); // old_growth_spruce_taiga（768）
        assertEquals(39, reader.readVarInt()); // plains（768）
        assertEquals(0xFF, reader.readUnsignedByte());
    }

    @Test
    void entityMetadata767To768Painting() {
        // 型 26（絵画）：参照IDは同一（5→5）。
        Writer entries = new Writer();
        entries.writeUnsignedByte(1);
        entries.writeVarInt(26);
        entries.writeVarInt(6); // 参照 5
        byte[] out = ChunkTranslator.translateEntityMetadata767To768(metadataPacket(9, entries));
        Reader reader = new Reader(out);
        assertEquals(9, reader.readVarInt());
        assertEquals(1, reader.readUnsignedByte());
        assertEquals(26, reader.readVarInt());
        assertEquals(6, reader.readVarInt()); // 参照 5
        assertEquals(0xFF, reader.readUnsignedByte());
    }

    @Test
    void entityMetadata768To767TrailParticleDropped() {
        // 型 17（粒子）：trail は767に無いためエントリごと落とす。終端だけが残る。
        Writer entries = new Writer();
        entries.writeUnsignedByte(4);
        entries.writeVarInt(17);
        entries.writeVarInt(46); // trail
        entries.writeDouble(0.0);
        entries.writeDouble(0.0);
        entries.writeDouble(0.0);
        entries.writeInt(0xFF0000);
        byte[] out = ChunkTranslator.translateEntityMetadata768To767(metadataPacket(5, entries));
        Reader reader = new Reader(out);
        assertEquals(5, reader.readVarInt());
        assertEquals(0xFF, reader.readUnsignedByte());
    }

    @Test
    void entityMetadata767To768Slot() {
        // 型 7（スロット）：turtle_helmet 794→816。
        Writer entries = new Writer();
        entries.writeUnsignedByte(6);
        entries.writeVarInt(7);
        writeSlot(entries, 794, 1);
        byte[] out = ChunkTranslator.translateEntityMetadata767To768(metadataPacket(5, entries));
        Reader reader = new Reader(out);
        assertEquals(5, reader.readVarInt());
        assertEquals(6, reader.readUnsignedByte());
        assertEquals(7, reader.readVarInt());
        int[] head = readSlotHead(reader);
        assertEquals(1, head[0]);
        assertEquals(816, head[1]);
        assertEquals(0, head[2]);
        assertEquals(0xFF, reader.readUnsignedByte());
    }

    // ---- チャンク / ブロック変化 / ブロックエンティティ ----

    @Test
    void chunkData767To768RemapsTrialSpawnerAndBiome() {
        Writer payload = new Writer();
        payload.writeInt(3);
        payload.writeInt(-2);
        payload.writeAnonymousNbt(heightmaps());
        payload.writeByteArray(indirectSectionWithBiomePalette(
                new int[]{0, 1, 26638, 26643}, new int[]{38, 39}));
        payload.writeVarInt(2);
        payload.writeUnsignedByte(0x11);
        payload.writeShort(64);
        payload.writeVarInt(10); // mob_spawner
        payload.writeByte(0); // NBT 無し
        payload.writeUnsignedByte(0x22);
        payload.writeShort(65);
        payload.writeVarInt(42); // trial_spawner
        payload.writeByte(0);
        payload.writeBytes(new byte[]{0x0A, 0x0B, 0x0C}); // 光データ

        byte[] translated = ChunkTranslator.translateChunkData767To768(payload.bytes());
        Reader reader = new Reader(translated);
        assertEquals(3, reader.readInt());
        assertEquals(-2, reader.readInt());
        assertTrue(reader.readAnonymousNbt().tag() instanceof Tag.CompoundTag);
        byte[] sections = reader.readBytes(reader.readVarInt());
        assertArrayEquals(new int[]{0, 1, 27107, 27112}, readIndirectPalette(sections));
        assertArrayEquals(new int[]{38, 39}, readBiomePalette(sections));
        assertEquals(2, reader.readVarInt()); // ブロックエンティティは2件
        assertEquals(0x11, reader.readUnsignedByte());
        assertEquals(64, reader.readShort());
        assertEquals(11, reader.readVarInt()); // mob_spawner（768）
        assertEquals(0, reader.readByte()); // NBT 無し
        assertEquals(0x22, reader.readUnsignedByte());
        assertEquals(65, reader.readShort());
        assertEquals(43, reader.readVarInt()); // trial_spawner（768）
        assertEquals(0, reader.readByte()); // NBT 無し
        assertArrayEquals(new byte[]{0x0A, 0x0B, 0x0C}, reader.rest());
    }

    @Test
    void chunkData767To768SingleBiomePlainsStays() {
        // バイオームがシングルの場合も plains（39）はそのまま（既定の768は767と同じ64件・同じ順番）。
        Writer payload = new Writer();
        payload.writeInt(0);
        payload.writeInt(0);
        payload.writeAnonymousNbt(heightmaps());
        payload.writeByteArray(indirectSection(new int[]{0, 1}, 39));
        payload.writeVarInt(0);
        byte[] translated = ChunkTranslator.translateChunkData767To768(payload.bytes());
        Reader reader = new Reader(translated);
        reader.readInt();
        reader.readInt();
        reader.readAnonymousNbt();
        byte[] sections = reader.readBytes(reader.readVarInt());
        assertEquals(39, readSingleBiome(sections));
        assertEquals(0, reader.readVarInt());
    }

    @Test
    void chunkData768To767DropsCreakingHeart() {
        Writer payload = new Writer();
        payload.writeInt(0);
        payload.writeInt(0);
        payload.writeAnonymousNbt(heightmaps());
        payload.writeByteArray(indirectSection(new int[]{0, 1, 2917}, 39));
        payload.writeVarInt(2);
        payload.writeUnsignedByte(0x11);
        payload.writeShort(64);
        payload.writeVarInt(10); // creaking_heart
        payload.writeByte(0);
        payload.writeUnsignedByte(0x22);
        payload.writeShort(65);
        payload.writeVarInt(44); // vault
        payload.writeByte(0);

        byte[] translated = ChunkTranslator.translateChunkData768To767(payload.bytes());
        Reader reader = new Reader(translated);
        reader.readInt();
        reader.readInt();
        reader.readAnonymousNbt();
        byte[] sections = reader.readBytes(reader.readVarInt());
        assertArrayEquals(new int[]{0, 1, 1}, readIndirectPalette(sections));
        assertEquals(39, readSingleBiome(sections)); // plains（39）はそのまま
        assertEquals(1, reader.readVarInt()); // creaking_heart のエントリは落とした
        assertEquals(0x22, reader.readUnsignedByte());
        assertEquals(65, reader.readShort());
        assertEquals(43, reader.readVarInt()); // vault（767）
        assertEquals(0, reader.readByte());
    }

    @Test
    void chunkData768To767DropsOutOfRangeBiomeToPlains() {
        // 768のバイオーム64（winter_drop 有効時の pale_garden 等、既定のサーバーでは
        // 現れない）は767向けに plains（39）に落とす。64未満は素通し。
        Writer payload = new Writer();
        payload.writeInt(0);
        payload.writeInt(0);
        payload.writeAnonymousNbt(heightmaps());
        payload.writeByteArray(indirectSection(new int[]{0, 1}, 64));
        payload.writeVarInt(0);
        byte[] translated = ChunkTranslator.translateChunkData768To767(payload.bytes());
        Reader reader = new Reader(translated);
        reader.readInt();
        reader.readInt();
        reader.readAnonymousNbt();
        byte[] sections = reader.readBytes(reader.readVarInt());
        assertEquals(39, readSingleBiome(sections));
        assertEquals(0, reader.readVarInt());
    }

    @Test
    void blockChange767To768() {
        Writer writer = new Writer();
        writer.writePositionRaw(0x123456789AL);
        writer.writeVarInt(26638);
        byte[] out = ChunkTranslator.translateBlockChange767To768(writer.bytes());
        Reader reader = new Reader(out);
        assertEquals(0x123456789AL, reader.readPositionRaw());
        assertEquals(27107, reader.readVarInt());
    }

    @Test
    void blockChange768To767() {
        Writer writer = new Writer();
        writer.writePositionRaw(0x123456789AL);
        writer.writeVarInt(27119);
        byte[] out = ChunkTranslator.translateBlockChange768To767(writer.bytes());
        Reader reader = new Reader(out);
        assertEquals(0x123456789AL, reader.readPositionRaw());
        assertEquals(26650, reader.readVarInt());
    }

    @Test
    void multiBlockChange767To768() {
        Writer writer = new Writer();
        writer.writeLong(0x123456789AL);
        writer.writeVarInt(1);
        writer.writeVarInt((26638 << 12) | 5);
        byte[] out = ChunkTranslator.translateMultiBlockChange767To768(writer.bytes());
        Reader reader = new Reader(out);
        assertEquals(0x123456789AL, reader.readLong());
        assertEquals(1, reader.readVarInt());
        assertEquals((27107 << 12) | 5, reader.readVarInt());
    }

    @Test
    void blockEntityData767To768() {
        Writer writer = new Writer();
        writer.writePositionRaw(0x123456789AL);
        writer.writeVarInt(42); // trial_spawner
        writer.writeByte(0); // NBT 無し
        byte[] out = ChunkTranslator.translateBlockEntityData767To768(writer.bytes());
        Reader reader = new Reader(out);
        assertEquals(0x123456789AL, reader.readPositionRaw());
        assertEquals(43, reader.readVarInt());
        assertEquals(0, reader.readByte());
    }

    @Test
    void blockEntityData768To767CreakingHeartDropped() {
        // creaking_heart（768=10）は767に無いためパケットごと落とす。
        Writer writer = new Writer();
        writer.writePositionRaw(0x123456789AL);
        writer.writeVarInt(10); // creaking_heart
        writer.writeByte(0);
        assertNull(ChunkTranslator.translateBlockEntityData768To767(writer.bytes()));
    }

    // ---- Spawn Entity / ボート ----

    /** Spawn Entity ペイロード（type＋objectData＋速度）。 */
    private static byte[] spawnEntityPacket(int type, int objectData) {
        Writer writer = new Writer();
        writer.writeVarInt(7); // entityId
        writer.writeBytes(new byte[16]); // uuid
        writer.writeVarInt(type);
        writer.writeDouble(1.0);
        writer.writeDouble(64.0);
        writer.writeDouble(2.0);
        writer.writeByte(0); // pitch
        writer.writeByte(0); // yaw
        writer.writeByte(0); // headPitch
        writer.writeVarInt(objectData);
        writer.writeShort(0);
        writer.writeShort(0);
        writer.writeShort(0);
        return writer.bytes();
    }

    /** Spawn Entity ペイロードの type／objectData を読む。 */
    private static int[] readSpawnEntity(Reader reader) {
        reader.readVarInt(); // entityId
        reader.readBytes(16); // uuid
        int type = reader.readVarInt();
        reader.readDouble();
        reader.readDouble();
        reader.readDouble();
        reader.readByte();
        reader.readByte();
        reader.readByte();
        int objectData = reader.readVarInt();
        return new int[]{type, objectData};
    }

    @Test
    void spawnEntity767To768BoatBecomesOak() {
        // 767の汎用 boat（10）は 768の oak_boat（85）になる。
        byte[] out = ChunkTranslator.translateSpawnEntity767To768(spawnEntityPacket(10, 0));
        assertArrayEquals(new int[]{85, 0}, readSpawnEntity(new Reader(out)));
    }

    @Test
    void spawnEntity768To767SpruceBoatBecomesBoat() {
        // 768の spruce_boat（119）は 767の汎用 boat（10）になる。
        byte[] out = ChunkTranslator.translateSpawnEntity768To767(spawnEntityPacket(119, 0));
        assertArrayEquals(new int[]{10, 0}, readSpawnEntity(new Reader(out)));
    }

    @Test
    void spawnEntity767To768FallingBlockObjectData() {
        // falling_block は 767の 40 から 768の 50 へ。objectData のブロック状態も付け替える。
        byte[] out = ChunkTranslator.translateSpawnEntity767To768(spawnEntityPacket(40, 26638));
        assertArrayEquals(new int[]{50, 27107}, readSpawnEntity(new Reader(out)));
    }

    @Test
    void spawnEntity768To767FallingBlockObjectData() {
        byte[] out = ChunkTranslator.translateSpawnEntity768To767(spawnEntityPacket(50, 27119));
        assertArrayEquals(new int[]{40, 26650}, readSpawnEntity(new Reader(out)));
    }

    @Test
    void boatVariantForEntityType768() {
        assertEquals(0, ChunkTranslator.boatVariantForEntityType768(85)); // oak_boat
        assertEquals(1, ChunkTranslator.boatVariantForEntityType768(120)); // spruce_chest_boat
        assertEquals(8, ChunkTranslator.boatVariantForEntityType768(8)); // bamboo_chest_raft
        assertEquals(9, ChunkTranslator.boatVariantForEntityType768(91)); // pale_oak_chest_boat
        assertEquals(-1, ChunkTranslator.boatVariantForEntityType768(77)); // pig はボートではない
    }

    @Test
    void boatEntityType768ForVariant() {
        assertEquals(119, ChunkTranslator.boatEntityType768ForVariant(1, false)); // spruce
        assertEquals(8, ChunkTranslator.boatEntityType768ForVariant(8, true)); // bamboo_chest_raft
        assertEquals(90, ChunkTranslator.boatEntityType768ForVariant(9, false)); // pale_oak
        assertEquals(86, ChunkTranslator.boatEntityType768ForVariant(0, true)); // oak_chest_boat
        assertEquals(-1, ChunkTranslator.boatEntityType768ForVariant(10, false)); // 範囲外
    }

    // ---- 音 ----

    @Test
    void soundEffect767To768() {
        // 音の参照：stone.break 1361→1386（Holder は id＋1）。
        Writer writer = new Writer();
        writer.writeVarInt(1362); // 音の Holder（参照 1361）
        writer.writeVarInt(0); // category
        writer.writeInt(100);
        writer.writeInt(64);
        writer.writeInt(200);
        writer.writeFloat(1.0f);
        writer.writeFloat(1.0f);
        writer.writeLong(12345L); // seed
        byte[] out = ChunkTranslator.translateSoundEffect767To768(writer.bytes());
        Reader reader = new Reader(out);
        assertEquals(1387, reader.readVarInt()); // 音の Holder（参照 1386）
        assertEquals(0, reader.readVarInt());
        assertEquals(100, reader.readInt());
        assertEquals(64, reader.readInt());
        assertEquals(200, reader.readInt());
        assertEquals(1.0f, reader.readFloat(), 0.0001f);
        assertEquals(1.0f, reader.readFloat(), 0.0001f);
        assertEquals(12345L, reader.readLong());
    }

    @Test
    void soundEffect768To767NewSoundFallsBack() {
        // 音の直書きの名前：768にしか無い creaking の音は 767では石の破壊音になる。
        // 両方にある音の名前（goat_horn.sound.0 等）はそのまま通る。
        Writer writer = new Writer();
        writer.writeVarInt(0); // 直書き
        writer.writeString("minecraft:entity.creaking.ambient");
        writer.writeBoolean(false); // fixedRange 無し
        writer.writeVarInt(0); // category
        writer.writeInt(100);
        writer.writeInt(64);
        writer.writeInt(200);
        writer.writeFloat(1.0f);
        writer.writeFloat(1.0f);
        writer.writeLong(0L);
        byte[] out = ChunkTranslator.translateSoundEffect768To767(writer.bytes());
        Reader reader = new Reader(out);
        assertEquals(0, reader.readVarInt()); // 直書き
        assertEquals("minecraft:block.stone.break", reader.readString());
        assertEquals(false, reader.readBoolean());
        assertEquals(0, reader.readVarInt());
        assertEquals(100, reader.readInt());
        assertEquals(64, reader.readInt());
        assertEquals(200, reader.readInt());
        assertEquals(1.0f, reader.readFloat(), 0.0001f);
        assertEquals(1.0f, reader.readFloat(), 0.0001f);
        assertEquals(0L, reader.readLong());
    }

    @Test
    void stopSound767To768GoatHorn() {
        // Stop Sound の音の名前：screaming.horn_break → horn_break。
        Writer writer = new Writer();
        writer.writeByte(0x02); // 音の名前あり
        writer.writeString("minecraft:entity.goat.screaming.horn_break");
        byte[] out = ChunkTranslator.translateStopSound767To768(writer.bytes());
        Reader reader = new Reader(out);
        assertEquals(0x02, reader.readByte());
        assertEquals("minecraft:entity.goat.horn_break", reader.readString());
    }

    @Test
    void entitySoundEffect767To768() {
        Writer writer = new Writer();
        writer.writeVarInt(1362); // 音の Holder（参照 1361＝stone.break）
        writer.writeVarInt(0); // category
        writer.writeVarInt(7); // entityId
        writer.writeFloat(1.0f);
        writer.writeFloat(1.0f);
        writer.writeLong(99L); // seed
        byte[] out = ChunkTranslator.translateEntitySoundEffect767To768(writer.bytes());
        Reader reader = new Reader(out);
        assertEquals(1387, reader.readVarInt()); // 音の Holder（参照 1386）
        assertEquals(0, reader.readVarInt());
        assertEquals(7, reader.readVarInt());
        assertEquals(1.0f, reader.readFloat(), 0.0001f);
        assertEquals(1.0f, reader.readFloat(), 0.0001f);
        assertEquals(99L, reader.readLong());
    }

    // ---- Set Cooldown ----

    @Test
    void setCooldown767To768EnderPearl() {
        Writer writer = new Writer();
        writer.writeVarInt(993); // ender_pearl（767）
        writer.writeVarInt(20); // ticks
        byte[] out = ChunkTranslator.translateSetCooldown767To768(writer.bytes());
        Reader reader = new Reader(out);
        assertEquals("minecraft:ender_pearl", reader.readString());
        assertEquals(20, reader.readVarInt());
    }

    @Test
    void setCooldown767To768UnknownDropped() {
        // グループ名を決められないアイテム（stone）はパケットごと落とす。
        Writer writer = new Writer();
        writer.writeVarInt(1); // stone
        writer.writeVarInt(20);
        assertNull(ChunkTranslator.translateSetCooldown767To768(writer.bytes()));
    }

    @Test
    void setCooldown768To767ChorusFruit() {
        Writer writer = new Writer();
        writer.writeString("minecraft:chorus_fruit");
        writer.writeVarInt(20);
        byte[] out = ChunkTranslator.translateSetCooldown768To767(writer.bytes());
        Reader reader = new Reader(out);
        assertEquals(1150, reader.readVarInt()); // chorus_fruit（767）
        assertEquals(20, reader.readVarInt());
    }

    @Test
    void setCooldown768To767UnknownDropped() {
        Writer writer = new Writer();
        writer.writeString("minecraft:unknown_thing");
        writer.writeVarInt(20);
        assertNull(ChunkTranslator.translateSetCooldown768To767(writer.bytes()));
    }

    // ---- Window / 取引 / その他 ----

    @Test
    void windowItems767To768() {
        Writer writer = new Writer();
        writer.writeUnsignedByte(0); // windowId
        writer.writeVarInt(0); // stateId
        writer.writeVarInt(1); // count 1
        writeSlot(writer, 794, 1); // turtle_helmet
        writeSlot(writer, 1172, 1); // music_disc_creator（携帯品）
        byte[] out = ChunkTranslator.translateWindowItems767To768(writer.bytes());
        Reader reader = new Reader(out);
        assertEquals(0, reader.readUnsignedByte());
        assertEquals(0, reader.readVarInt());
        assertEquals(1, reader.readVarInt());
        assertEquals(816, readSlotHead(reader)[1]);
        assertEquals(1214, readSlotHead(reader)[1]);
    }

    @Test
    void entityEquipment767To768() {
        Writer writer = new Writer();
        writer.writeVarInt(5); // entityId
        writer.writeUnsignedByte(0); // slotByte（継続なし）
        writeSlot(writer, 794, 1); // turtle_helmet
        byte[] out = ChunkTranslator.translateEntityEquipment767To768(writer.bytes());
        Reader reader = new Reader(out);
        assertEquals(5, reader.readVarInt());
        assertEquals(0, reader.readUnsignedByte());
        assertEquals(816, readSlotHead(reader)[1]);
    }

    @Test
    void tradeList767To768() {
        Writer writer = new Writer();
        writer.writeVarInt(3); // windowId
        writer.writeVarInt(1); // count 1
        // 1件の取引：コスト（スロット1個）＋結果＋2つ目のコスト無し＋残りの数値。
        writer.writeVarInt(1); // ItemCost：1件
        writeSlot(writer, 794, 1); // turtle_helmet（コスト）
        writeSlot(writer, 1172, 1); // music_disc_creator（結果）
        writer.writeBoolean(false); // 2つ目のコスト無し
        writer.writeBoolean(true); // tradeExperience
        writer.writeInt(10); // maxUses
        writer.writeInt(5); // uses
        writer.writeInt(2); // xp
        writer.writeInt(0); // specialPrice
        writer.writeFloat(0.05f); // priceMultiplier
        writer.writeInt(3); // demand
        writer.writeVarInt(5); // villagerLevel
        writer.writeVarInt(3); // villagerXp
        writer.writeBoolean(true); // restock
        writer.writeBoolean(false); // show…
        byte[] out = ChunkTranslator.translateTradeList767To768(writer.bytes());
        Reader reader = new Reader(out);
        assertEquals(3, reader.readVarInt());
        assertEquals(1, reader.readVarInt());
        assertEquals(1, reader.readVarInt()); // ItemCost：1件
        assertEquals(816, readSlotHead(reader)[1]); // コスト（768）
        assertEquals(1214, readSlotHead(reader)[1]); // 結果（768）
        assertEquals(false, reader.readBoolean()); // 2つ目のコスト無し
        assertEquals(true, reader.readBoolean());
        assertEquals(10, reader.readInt());
        assertEquals(5, reader.readInt());
        assertEquals(2, reader.readInt());
        assertEquals(0, reader.readInt());
        assertEquals(0.05f, reader.readFloat(), 0.0001f);
        assertEquals(3, reader.readInt());
        assertEquals(5, reader.readVarInt());
        assertEquals(3, reader.readVarInt());
        assertEquals(true, reader.readBoolean());
        assertEquals(false, reader.readBoolean());
    }

    @Test
    void openWindow767To768() {
        // メニュー種別は同一（crafter_3x3 は 767も768も 7）。
        Writer writer = new Writer();
        writer.writeVarInt(1); // windowId
        writer.writeVarInt(7); // crafter_3x3
        writer.writeAnonymousNbt(emptyCompound()); // title
        byte[] out = ChunkTranslator.translateOpenWindow767To768(writer.bytes());
        Reader reader = new Reader(out);
        assertEquals(1, reader.readVarInt());
        assertEquals(7, reader.readVarInt());
        assertTrue(reader.readAnonymousNbt().tag() instanceof Tag.CompoundTag);
    }

    @Test
    void blockAction767To768() {
        // ブロックID（heavy_core 1059→1080）を付け替える。
        Writer writer = new Writer();
        writer.writePositionRaw(0x123456789AL);
        writer.writeUnsignedByte(1);
        writer.writeUnsignedByte(2);
        writer.writeVarInt(1059);
        byte[] out = ChunkTranslator.translateBlockAction767To768(writer.bytes());
        Reader reader = new Reader(out);
        assertEquals(0x123456789AL, reader.readPositionRaw());
        assertEquals(1, reader.readUnsignedByte());
        assertEquals(2, reader.readUnsignedByte());
        assertEquals(1080, reader.readVarInt());
    }

    @Test
    void windowClick767To768() {
        Writer writer = new Writer();
        writer.writeUnsignedByte(0); // windowId
        writer.writeVarInt(1); // stateId
        writer.writeShort(3); // slot
        writer.writeByte(0); // button
        writer.writeVarInt(0); // mode
        writer.writeVarInt(1); // changed 1件
        writer.writeShort(3);
        writeSlot(writer, 794, 1); // turtle_helmet
        writeSlot(writer, 1172, 1); // music_disc_creator（携帯品）
        byte[] out = ChunkTranslator.translateWindowClick767To768(writer.bytes());
        Reader reader = new Reader(out);
        assertEquals(0, reader.readUnsignedByte());
        assertEquals(1, reader.readVarInt());
        assertEquals(3, reader.readShort());
        assertEquals(0, reader.readByte());
        assertEquals(0, reader.readVarInt());
        assertEquals(1, reader.readVarInt()); // changed 1件
        assertEquals(3, reader.readShort());
        assertEquals(816, readSlotHead(reader)[1]);
        assertEquals(1214, readSlotHead(reader)[1]);
    }

    // ---- windowId（ContainerID の型が u8→varint に変わる） ----

    /** 768形式の Set Slot ペイロード（windowId は varint）。 */
    private static byte[] setSlotPacket768(int itemId, int windowId) {
        Writer writer = new Writer();
        writer.writeVarInt(windowId); // windowId（varint）
        writer.writeVarInt(0); // stateId
        writer.writeShort(3); // slotIndex
        writeSlot(writer, itemId, 1);
        return writer.bytes();
    }

    /** 768形式の Set Slot ペイロードを読み、{windowId, slotIndex, count, itemId, added} を返す。 */
    private static int[] readSetSlot768(Reader reader) {
        int windowId = reader.readVarInt();
        reader.readVarInt(); // stateId
        int slotIndex = reader.readShort();
        int[] head = readSlotHead(reader);
        return new int[]{windowId, slotIndex, head[0], head[1], head[2]};
    }

    @Test
    void setSlotWindowIdMinusOne767To768() {
        // カーソル（windowId -1）：767は u8 の 0xFF、768は varint の -1（5バイト）。
        Writer writer = new Writer();
        writer.writeByte(-1); // windowId（u8）
        writer.writeVarInt(0); // stateId
        writer.writeShort(-1); // slotIndex（カーソル）
        writeSlot(writer, 794, 1); // turtle_helmet
        byte[] out = ChunkTranslator.translateSetSlot767To768(writer.bytes());
        assertEquals(-1, readSetSlot768(new Reader(out))[0]);
        assertEquals(816, readSetSlot768(new Reader(out))[3]);
        assertArrayEquals(new byte[]{(byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, 0x0F},
                java.util.Arrays.copyOfRange(out, 0, 5));
    }

    @Test
    void setSlotWindowIdMinusOne768To767() {
        byte[] out = ChunkTranslator.translateSetSlot768To767(setSlotPacket768(816, -1));
        assertArrayEquals(new byte[]{(byte) 0xFF}, java.util.Arrays.copyOfRange(out, 0, 1));
        assertEquals(794, readSetSlot(new Reader(out))[2]);
    }

    @Test
    void setSlotWindowId200RoundTrip() {
        // 767の u8 0xC8 は符号付きで -56。768では varint の -56 になる。
        Writer writer = new Writer();
        writer.writeByte(200); // windowId（u8。0xC8）
        writer.writeVarInt(0); // stateId
        writer.writeShort(3);
        writeSlot(writer, 794, 1);
        byte[] out = ChunkTranslator.translateSetSlot767To768(writer.bytes());
        assertEquals(-56, readSetSlot768(new Reader(out))[0]);
        // 逆向き：768の varint 200 は 767では u8 の 0xC8（符号付きで -56）になる。
        byte[] back = ChunkTranslator.translateSetSlot768To767(setSlotPacket768(816, 200));
        assertEquals(0xC8, back[0] & 0xFF);
        assertEquals(794, readSetSlot(new Reader(back))[2]);
    }

    @Test
    void windowItemsWindowId200() {
        Writer writer = new Writer();
        writer.writeByte(200); // windowId（u8。0xC8）
        writer.writeVarInt(0); // stateId
        writer.writeVarInt(1); // count 1
        writeSlot(writer, 794, 1); // turtle_helmet
        writeSlot(writer, 1172, 1); // music_disc_creator（携帯品）
        byte[] out = ChunkTranslator.translateWindowItems767To768(writer.bytes());
        Reader reader = new Reader(out);
        assertEquals(-56, reader.readVarInt()); // windowId（varint）
        assertEquals(0, reader.readVarInt());
        assertEquals(1, reader.readVarInt());
        assertEquals(816, readSlotHead(reader)[1]);
        assertEquals(1214, readSlotHead(reader)[1]);
    }

    @Test
    void windowItemsWindowId200Reverse() {
        Writer writer = new Writer();
        writer.writeVarInt(200); // windowId（varint）
        writer.writeVarInt(0); // stateId
        writer.writeVarInt(1); // count 1
        writeSlot(writer, 816, 1); // turtle_helmet（768）
        writeSlot(writer, 1214, 1); // music_disc_creator（768。携帯品）
        byte[] out = ChunkTranslator.translateWindowItems768To767(writer.bytes());
        assertEquals(0xC8, out[0] & 0xFF); // windowId（u8）
        Reader reader = new Reader(out);
        reader.readByte(); // windowId
        assertEquals(0, reader.readVarInt());
        assertEquals(1, reader.readVarInt());
        assertEquals(794, readSlotHead(reader)[1]);
        assertEquals(1172, readSlotHead(reader)[1]);
    }

    @Test
    void tradeListWindowId200() {
        Writer writer = new Writer();
        writer.writeByte(200); // windowId（u8。0xC8）
        writer.writeVarInt(1); // count 1
        writer.writeVarInt(1); // ItemCost：1件
        writeSlot(writer, 794, 1); // turtle_helmet（コスト）
        writeSlot(writer, 1172, 1); // music_disc_creator（結果）
        writer.writeBoolean(false); // 2つ目のコスト無し
        writer.writeBoolean(true); // tradeExperience
        writer.writeInt(10); // maxUses
        writer.writeInt(5); // uses
        writer.writeInt(2); // xp
        writer.writeInt(0); // specialPrice
        writer.writeFloat(0.05f); // priceMultiplier
        writer.writeInt(3); // demand
        writer.writeVarInt(5); // villagerLevel
        writer.writeVarInt(3); // villagerXp
        writer.writeBoolean(true); // restock
        writer.writeBoolean(false); // show…
        byte[] out = ChunkTranslator.translateTradeList767To768(writer.bytes());
        Reader reader = new Reader(out);
        assertEquals(-56, reader.readVarInt()); // windowId（varint）
        assertEquals(1, reader.readVarInt());
        assertEquals(1, reader.readVarInt()); // ItemCost：1件
        assertEquals(816, readSlotHead(reader)[1]); // コスト（768）
        assertEquals(1214, readSlotHead(reader)[1]); // 結果（768）
    }

    @Test
    void windowClickWindowId200() {
        Writer writer = new Writer();
        writer.writeByte(200); // windowId（u8。0xC8）
        writer.writeVarInt(1); // stateId
        writer.writeShort(3); // slot
        writer.writeByte(0); // button
        writer.writeVarInt(0); // mode
        writer.writeVarInt(1); // changed 1件
        writer.writeShort(3);
        writeSlot(writer, 794, 1); // turtle_helmet
        writeSlot(writer, 1172, 1); // music_disc_creator（携帯品）
        byte[] out = ChunkTranslator.translateWindowClick767To768(writer.bytes());
        Reader reader = new Reader(out);
        assertEquals(-56, reader.readVarInt()); // windowId（varint）
        assertEquals(1, reader.readVarInt());
        assertEquals(3, reader.readShort());
        assertEquals(0, reader.readByte());
        assertEquals(0, reader.readVarInt());
        assertEquals(1, reader.readVarInt()); // changed 1件
        assertEquals(3, reader.readShort());
        assertEquals(816, readSlotHead(reader)[1]);
        assertEquals(1214, readSlotHead(reader)[1]);
    }

    @Test
    void setCreativeSlot767To768() {
        Writer writer = new Writer();
        writer.writeShort(3);
        writeSlot(writer, 1332, 1); // breeze_rod
        byte[] out = ChunkTranslator.translateSetCreativeSlot767To768(writer.bytes());
        Reader reader = new Reader(out);
        assertEquals(3, reader.readShort());
        assertEquals(1134, readSlotHead(reader)[1]);
    }

    @Test
    void updateAttributes767To768() {
        // water_movement_efficiency 30→31、burning_time 7→7。修飾子の UUID は文字列のまま。
        Writer writer = new Writer();
        writer.writeVarInt(7); // entityId
        writer.writeVarInt(2); // count 2
        writer.writeVarInt(30); // water_movement_efficiency
        writer.writeDouble(1.5);
        writer.writeVarInt(1); // modifiers 1件
        writer.writeString("123e4567-e89b-12d3-a456-426614174000");
        writer.writeDouble(0.5);
        writer.writeByte(0);
        writer.writeVarInt(7); // burning_time
        writer.writeDouble(2.0);
        writer.writeVarInt(0); // modifiers 0件
        byte[] out = ChunkTranslator.translateUpdateAttributes767To768(writer.bytes());
        Reader reader = new Reader(out);
        assertEquals(7, reader.readVarInt());
        assertEquals(2, reader.readVarInt());
        assertEquals(31, reader.readVarInt()); // water_movement_efficiency（768）
        assertEquals(1.5, reader.readDouble(), 0.0001);
        assertEquals(1, reader.readVarInt());
        assertEquals("123e4567-e89b-12d3-a456-426614174000", reader.readString());
        assertEquals(0.5, reader.readDouble(), 0.0001);
        assertEquals(0, reader.readByte());
        assertEquals(7, reader.readVarInt()); // burning_time（768）
        assertEquals(2.0, reader.readDouble(), 0.0001);
        assertEquals(0, reader.readVarInt());
    }

    @Test
    void updateAttributes768To767TemptRangeDropped() {
        // tempt_range（768の 30）は 767に無いため落とす。water_movement_efficiency は 31→30。
        Writer writer = new Writer();
        writer.writeVarInt(7); // entityId
        writer.writeVarInt(2); // count 2
        writer.writeVarInt(30); // tempt_range
        writer.writeDouble(3.0);
        writer.writeVarInt(0); // modifiers 0件
        writer.writeVarInt(31); // water_movement_efficiency
        writer.writeDouble(1.0);
        writer.writeVarInt(0); // modifiers 0件
        byte[] out = ChunkTranslator.translateUpdateAttributes768To767(writer.bytes());
        Reader reader = new Reader(out);
        assertEquals(7, reader.readVarInt());
        assertEquals(1, reader.readVarInt()); // tempt_range は落とした
        assertEquals(30, reader.readVarInt()); // water_movement_efficiency（767）
        assertEquals(1.0, reader.readDouble(), 0.0001);
        assertEquals(0, reader.readVarInt());
    }

    // ---- 768だけのパケット → 767 ----

    @Test
    void setCursorItem768To767() {
        // Set Cursor Item（768）は 767の Set Slot（windowId=-1／slot=-1）に変換する。
        Writer writer = new Writer();
        writeSlot(writer, 1374, 1); // ominous_bottle
        byte[] out = ChunkTranslator.translateSetCursorItem768To767(writer.bytes());
        Reader reader = new Reader(out);
        assertEquals(-1, reader.readByte()); // windowId -1
        assertEquals(0, reader.readVarInt()); // stateId
        assertEquals(-1, reader.readShort()); // slot -1
        assertEquals(1331, readSlotHead(reader)[1]); // ominous_bottle（767）
    }

    @Test
    void setPlayerInventory768To767() {
        // Set Player Inventory（768）は 767の Set Slot（windowId=0）に変換する。
        Writer writer = new Writer();
        writer.writeVarInt(5); // slotId
        writeSlot(writer, 816, 1); // turtle_helmet（768）
        byte[] out = ChunkTranslator.translateSetPlayerInventory768To767(writer.bytes());
        Reader reader = new Reader(out);
        assertEquals(0, reader.readByte()); // windowId 0
        assertEquals(0, reader.readVarInt()); // stateId
        assertEquals(5, reader.readShort()); // slot 5
        assertEquals(794, readSlotHead(reader)[1]); // turtle_helmet（767）
    }

    // ---- Damage / エフェクト ----

    @Test
    void damageEvent767To768Magic() {
        // magic は 767の 25 から 768の 27 へ移る。
        Writer writer = new Writer();
        writer.writeVarInt(7); // entityId
        writer.writeVarInt(25); // magic
        writer.writeVarInt(0); // sourceCauseId
        writer.writeVarInt(0); // sourceDirectId
        writer.writeBoolean(false); // hasPosition 無し
        byte[] out = ChunkTranslator.translateDamageEvent767To768(writer.bytes());
        Reader reader = new Reader(out);
        assertEquals(7, reader.readVarInt());
        assertEquals(27, reader.readVarInt()); // magic（768）
        assertEquals(0, reader.readVarInt());
        assertEquals(0, reader.readVarInt());
        assertEquals(false, reader.readBoolean());
    }

    @Test
    void damageEvent768To767EnderPearlDropped() {
        // ender_pearl（768=8）は767に無いためパケットごと落とす。
        Writer writer = new Writer();
        writer.writeVarInt(7);
        writer.writeVarInt(8); // ender_pearl
        writer.writeVarInt(0);
        writer.writeVarInt(0);
        writer.writeBoolean(false);
        assertNull(ChunkTranslator.translateDamageEvent768To767(writer.bytes()));
    }

    @Test
    void damageEvent768To767MaceSmashDropped() {
        // mace_smash（768=26）は767に無いためパケットごと落とす。
        Writer writer = new Writer();
        writer.writeVarInt(7);
        writer.writeVarInt(26); // mace_smash
        writer.writeVarInt(0);
        writer.writeVarInt(0);
        writer.writeBoolean(false);
        assertNull(ChunkTranslator.translateDamageEvent768To767(writer.bytes()));
    }

    @Test
    void entityEffect767To768Identity() {
        // エフェクトIDは同一（speed 1→1）。
        Writer writer = new Writer();
        writer.writeVarInt(7); // entityId
        writer.writeVarInt(1); // speed
        writer.writeVarInt(2); // amplifier
        writer.writeVarInt(100); // duration
        writer.writeUnsignedByte(0); // flags
        byte[] out = ChunkTranslator.translateEntityEffect767To768(writer.bytes());
        Reader reader = new Reader(out);
        assertEquals(7, reader.readVarInt());
        assertEquals(1, reader.readVarInt());
        assertEquals(2, reader.readVarInt());
        assertEquals(100, reader.readVarInt());
        assertEquals(0, reader.readUnsignedByte());
    }

    @Test
    void removeEntityEffect768To767Identity() {
        Writer writer = new Writer();
        writer.writeVarInt(7); // entityId
        writer.writeVarInt(1); // speed
        byte[] out = ChunkTranslator.translateRemoveEntityEffect768To767(writer.bytes());
        Reader reader = new Reader(out);
        assertEquals(7, reader.readVarInt());
        assertEquals(1, reader.readVarInt());
    }
}
