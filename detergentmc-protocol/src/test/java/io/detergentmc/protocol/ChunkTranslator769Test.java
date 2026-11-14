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
 * 768（1.21.2／1.21.3）⇔769（1.21.4）の変換テスト。
 * 期待値は ViaVersion mapping-1.21.2.json／mapping-1.21.4.json と
 * minecraft-data のレジストリから計算したもの（推測ではない）。
 * 768の機械自体は ChunkTranslator768Test が既に網羅しているため、
 * ここでは769で変わる部分（新要素・IDのずれ・追加フィールド）に絞る。
 */
class ChunkTranslator769Test {

    // ---- ヘルパー ----

    /** 空のコンパウンド NBT。 */
    private static Tag emptyCompound() {
        return new Tag.CompoundTag(Map.of());
    }

    /** スロット（768／769共通の形：個数 VarInt＋アイテムID＋追加数＋削除数）。追加・削除は 0。 */
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

    /** Set Slot ペイロード（768／769共通：windowId（varint）＋stateId＋slotIndex＋スロット）。 */
    private static byte[] setSlotPacket(int itemId) {
        Writer writer = new Writer();
        writer.writeVarInt(0); // windowId
        writer.writeVarInt(0); // stateId
        writer.writeShort(3); // slotIndex
        writeSlot(writer, itemId, 1);
        return writer.bytes();
    }

    /** 高さマップ（MOTION_BLOCKING／WORLD_SURFACE 各37要素）。 */
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

    /** セクションのバイオームパレットを読む（単一の場合はその1件を返す）。 */
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
        int bits = section.readUnsignedByte(); // バイオーム BPE
        if (bits == 0) {
            return new int[]{section.readVarInt()};
        }
        int biomeSize = section.readVarInt();
        int[] palette = new int[biomeSize];
        for (int i = 0; i < biomeSize; i++) {
            palette[i] = section.readVarInt();
        }
        return palette;
    }

    /** セクションの単一バイオームを読む。 */
    private static int readSingleBiome(byte[] sections) {
        return readBiomePalette(sections)[0];
    }

    /** エンティティメタデータのパケット（entityId＋エントリ＋0xFF）。 */
    private static byte[] metadataPacket(int entityId, Writer entries) {
        Writer writer = new Writer();
        writer.writeVarInt(entityId);
        writer.writeBytes(entries.bytes());
        writer.writeUnsignedByte(0xFF);
        return writer.bytes();
    }

    /** World Particles ペイロード（768の形：alwaysShow 無し）。 */
    private static byte[] worldParticlesPacket768(int particleId, Writer particleData) {
        Writer writer = new Writer();
        writer.writeBoolean(false); // longDistance
        writer.writeDouble(1.0);
        writer.writeDouble(2.0);
        writer.writeDouble(3.0);
        writer.writeFloat(0.5f); // offsetX
        writer.writeFloat(0.5f); // offsetY
        writer.writeFloat(0.5f); // offsetZ
        writer.writeFloat(1.0f); // speed
        writer.writeInt(4); // count
        writer.writeVarInt(particleId);
        writer.writeBytes(particleData.bytes());
        return writer.bytes();
    }

    /** World Particles ペイロード（769の形：longDistance の後に alwaysShow）。 */
    private static byte[] worldParticlesPacket769(boolean alwaysShow, int particleId, Writer particleData) {
        Writer writer = new Writer();
        writer.writeBoolean(false); // longDistance
        writer.writeBoolean(alwaysShow); // alwaysShow（769で追加）
        writer.writeDouble(1.0);
        writer.writeDouble(2.0);
        writer.writeDouble(3.0);
        writer.writeFloat(0.5f); // offsetX
        writer.writeFloat(0.5f); // offsetY
        writer.writeFloat(0.5f); // offsetZ
        writer.writeFloat(1.0f); // speed
        writer.writeInt(4); // count
        writer.writeVarInt(particleId);
        writer.writeBytes(particleData.bytes());
        return writer.bytes();
    }

    /** World Particles ペイロードのヘッダを読み飛ばし、粒子IDを返す（768の形）。 */
    private static int readWorldParticlesId768(Reader reader) {
        reader.readBoolean(); // longDistance
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

    /** World Particles ペイロードのヘッダを読み飛ばし、粒子IDを返す（769の形）。 */
    private static int readWorldParticlesId769(Reader reader) {
        reader.readBoolean(); // longDistance
        reader.readBoolean(); // alwaysShow
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

    /** Spawn Entity ペイロード（entityId＋UUID＋種別＋座標＋向き＋objectData＋速度）。 */
    private static byte[] spawnEntityPacket(int type, int objectData) {
        Writer writer = new Writer();
        writer.writeVarInt(7); // entityId
        writer.writeBytes(new byte[16]); // uuid
        writer.writeVarInt(type);
        writer.writeDouble(1.0);
        writer.writeDouble(2.0);
        writer.writeDouble(3.0);
        writer.writeByte(0); // pitch
        writer.writeByte(0); // yaw
        writer.writeByte(0); // headPitch
        writer.writeVarInt(objectData);
        writer.writeShort(0); // velocityX
        writer.writeShort(0); // velocityY
        writer.writeShort(0); // velocityZ
        return writer.bytes();
    }

    /** Spawn Entity ペイロードを読み、{entityId, type, objectData} を返す。 */
    private static int[] readSpawnEntity(Reader reader) {
        int entityId = reader.readVarInt();
        reader.readBytes(16); // uuid
        int type = reader.readVarInt();
        reader.readDouble();
        reader.readDouble();
        reader.readDouble();
        reader.readByte();
        reader.readByte();
        reader.readByte();
        int objectData = reader.readVarInt();
        reader.readShort();
        reader.readShort();
        reader.readShort();
        return new int[]{entityId, type, objectData};
    }

    // ---- スロット ----

    @Test
    void slot768To769OakBoat() {
        // oak_boat 794→803（アイテム追加でずれる）。
        byte[] out = ChunkTranslator.translateSetSlot768To769(setSlotPacket(794));
        Reader reader = new Reader(out);
        reader.readVarInt(); // windowId
        reader.readVarInt(); // stateId
        assertEquals(3, reader.readShort());
        assertEquals(803, readSlotHead(reader)[1]);
    }

    @Test
    void slot768To769TurtleHelmet() {
        // turtle_helmet 816→825（アイテム追加でずれる）。
        byte[] out = ChunkTranslator.translateSetSlot768To769(setSlotPacket(816));
        Reader reader = new Reader(out);
        reader.readVarInt(); // windowId
        reader.readVarInt(); // stateId
        assertEquals(3, reader.readShort());
        assertEquals(825, readSlotHead(reader)[1]);

        // 逆向き：769の825→768の816。
        byte[] back = ChunkTranslator.translateSetSlot769To768(setSlotPacket(825));
        Reader reader2 = new Reader(back);
        reader2.readVarInt();
        reader2.readVarInt();
        assertEquals(3, reader2.readShort());
        assertEquals(816, readSlotHead(reader2)[1]);
    }

    @Test
    void slot768To769DiscAndBreezeRod() {
        // music_disc_creator 1214→1224、breeze_rod 1134→1143。
        byte[] out = ChunkTranslator.translateSetSlot768To769(setSlotPacket(1214));
        Reader reader = new Reader(out);
        reader.readVarInt();
        reader.readVarInt();
        reader.readShort();
        assertEquals(1224, readSlotHead(reader)[1]);

        byte[] out2 = ChunkTranslator.translateSetSlot768To769(setSlotPacket(1134));
        Reader reader2 = new Reader(out2);
        reader2.readVarInt();
        reader2.readVarInt();
        reader2.readShort();
        assertEquals(1143, readSlotHead(reader2)[1]);
    }

    @Test
    void slot769To768NewItemsFallBackToStone() {
        // 769の新アイテム（resin_brick 1167／resin_clump 376／open_eyeblossom 226）は
        // 768に無いため石（1）に落とす。
        byte[] out = ChunkTranslator.translateSetSlot769To768(setSlotPacket(1167));
        Reader reader = new Reader(out);
        reader.readVarInt();
        reader.readVarInt();
        reader.readShort();
        assertEquals(1, readSlotHead(reader)[1]);

        byte[] out2 = ChunkTranslator.translateSetSlot769To768(setSlotPacket(376));
        Reader reader2 = new Reader(out2);
        reader2.readVarInt();
        reader2.readVarInt();
        reader2.readShort();
        assertEquals(1, readSlotHead(reader2)[1]);

        byte[] out3 = ChunkTranslator.translateSetSlot769To768(setSlotPacket(226));
        Reader reader3 = new Reader(out3);
        reader3.readVarInt();
        reader3.readVarInt();
        reader3.readShort();
        assertEquals(1, readSlotHead(reader3)[1]);
    }

    @Test
    void slot769To768OminousBottle() {
        // ominous_bottle 1384→1374（769では creaking_transient 削除で1つ前にずれる）。
        byte[] out = ChunkTranslator.translateSetSlot769To768(setSlotPacket(1384));
        Reader reader = new Reader(out);
        reader.readVarInt();
        reader.readVarInt();
        reader.readShort();
        assertEquals(1374, readSlotHead(reader)[1]);
    }

    @Test
    void emptySlotIdentityBothWays() {
        // 空きスロットは両方向で素通し。
        Writer writer = new Writer();
        writer.writeVarInt(0); // windowId
        writer.writeVarInt(0); // stateId
        writer.writeShort(3);
        writeEmptySlot(writer);
        byte[] out = ChunkTranslator.translateSetSlot768To769(writer.bytes());
        Reader reader = new Reader(out);
        reader.readVarInt();
        reader.readVarInt();
        reader.readShort();
        assertEquals(0, reader.readVarInt());

        byte[] back = ChunkTranslator.translateSetSlot769To768(writer.bytes());
        Reader reader2 = new Reader(back);
        reader2.readVarInt();
        reader2.readVarInt();
        reader2.readShort();
        assertEquals(0, reader2.readVarInt());
    }

    // ---- コンポーネント ----

    @Test
    void enchantment768To769Identity() {
        // エンチャントは42種とも同一（mending 22、種別は両方 10）。
        Writer writer = new Writer();
        writer.writeVarInt(0); // windowId
        writer.writeVarInt(0); // stateId
        writer.writeShort(3);
        writer.writeVarInt(1);
        writer.writeVarInt(1);
        writer.writeVarInt(1); // added 1
        writer.writeVarInt(10); // enchantments
        writer.writeVarInt(1); // count 1
        writer.writeVarInt(22); // mending
        writer.writeVarInt(3); // level
        writer.writeVarInt(0); // removed 0
        byte[] out = ChunkTranslator.translateSetSlot768To769(writer.bytes());
        Reader reader = new Reader(out);
        reader.readVarInt();
        reader.readVarInt();
        reader.readShort();
        assertEquals(1, reader.readVarInt());
        assertEquals(1, reader.readVarInt());
        assertEquals(1, reader.readVarInt()); // added 1
        assertEquals(10, reader.readVarInt()); // enchantments（769）
        assertEquals(1, reader.readVarInt());
        assertEquals(22, reader.readVarInt()); // mending（769）
        assertEquals(3, reader.readVarInt());
        assertEquals(0, reader.readVarInt()); // removed 0
    }

    @Test
    void customModelData768To769Colors() {
        // custom_model_data は両方とも種別 14（colors）。色 42 はそのまま。
        Writer writer = new Writer();
        writer.writeVarInt(0); // windowId
        writer.writeVarInt(0); // stateId
        writer.writeShort(3);
        writer.writeVarInt(1);
        writer.writeVarInt(1);
        writer.writeVarInt(1); // added 1
        writer.writeVarInt(14); // custom_model_data
        writer.writeVarInt(1); // colors 1件
        writer.writeInt(42);
        writer.writeVarInt(0); // floats 0件
        writer.writeVarInt(0); // flags 0件
        writer.writeVarInt(0); // strings 0件
        writer.writeVarInt(0); // removed 0
        byte[] out = ChunkTranslator.translateSetSlot768To769(writer.bytes());
        Reader reader = new Reader(out);
        reader.readVarInt();
        reader.readVarInt();
        reader.readShort();
        assertEquals(1, reader.readVarInt());
        assertEquals(1, reader.readVarInt());
        assertEquals(1, reader.readVarInt()); // added 1
        assertEquals(14, reader.readVarInt()); // custom_model_data（769）
        assertEquals(1, reader.readVarInt());
        assertEquals(42, reader.readInt());
        assertEquals(0, reader.readVarInt());
        assertEquals(0, reader.readVarInt());
        assertEquals(0, reader.readVarInt());
        assertEquals(0, reader.readVarInt()); // removed 0
    }

    @Test
    void food768To769Identity() {
        // food は両方とも種別 21（栄養・満腹度・常時食べ可のみ）。
        Writer writer = new Writer();
        writer.writeVarInt(0); // windowId
        writer.writeVarInt(0); // stateId
        writer.writeShort(3);
        writer.writeVarInt(1);
        writer.writeVarInt(1);
        writer.writeVarInt(1); // added 1
        writer.writeVarInt(21); // food
        writer.writeVarInt(4); // nutrition
        writer.writeFloat(0.3f); // saturationModifier
        writer.writeBoolean(true); // canAlwaysEat
        writer.writeVarInt(0); // removed 0
        byte[] out = ChunkTranslator.translateSetSlot768To769(writer.bytes());
        Reader reader = new Reader(out);
        reader.readVarInt();
        reader.readVarInt();
        reader.readShort();
        assertEquals(1, reader.readVarInt());
        assertEquals(1, reader.readVarInt());
        assertEquals(1, reader.readVarInt()); // added 1
        assertEquals(21, reader.readVarInt()); // food（769）
        assertEquals(4, reader.readVarInt());
        assertEquals(0.3f, reader.readFloat(), 0.0001f);
        assertTrue(reader.readBoolean());
        assertEquals(0, reader.readVarInt()); // removed 0
    }

    @Test
    void attributeModifiers768To769Identity() {
        // 属性は32種とも同一（burning_time 7／movement_speed 21／
        // water_movement_efficiency 31）。修飾子の形も同じ。
        Writer writer = new Writer();
        writer.writeVarInt(0); // windowId
        writer.writeVarInt(0); // stateId
        writer.writeShort(3);
        writer.writeVarInt(1);
        writer.writeVarInt(1);
        writer.writeVarInt(1); // added 1
        writer.writeVarInt(13); // attribute_modifiers
        writer.writeVarInt(2); // count 2
        writer.writeVarInt(7); // burning_time
        writer.writeString("burn");
        writer.writeDouble(1.5);
        writer.writeVarInt(0);
        writer.writeVarInt(0);
        writer.writeVarInt(21); // movement_speed
        writer.writeString("speed");
        writer.writeDouble(2.0);
        writer.writeVarInt(1);
        writer.writeVarInt(1);
        writer.writeBoolean(true); // showTooltip
        writer.writeVarInt(0); // removed 0
        byte[] out = ChunkTranslator.translateSetSlot768To769(writer.bytes());
        Reader reader = new Reader(out);
        reader.readVarInt();
        reader.readVarInt();
        reader.readShort();
        assertEquals(1, reader.readVarInt());
        assertEquals(1, reader.readVarInt());
        assertEquals(1, reader.readVarInt()); // added 1
        assertEquals(13, reader.readVarInt()); // attribute_modifiers（769）
        assertEquals(2, reader.readVarInt());
        assertEquals(7, reader.readVarInt()); // burning_time（769）
        assertEquals("burn", reader.readString());
        assertEquals(1.5, reader.readDouble(), 0.0001);
        assertEquals(0, reader.readVarInt());
        assertEquals(0, reader.readVarInt());
        assertEquals(21, reader.readVarInt()); // movement_speed（769）
        assertEquals("speed", reader.readString());
        assertEquals(2.0, reader.readDouble(), 0.0001);
        assertEquals(1, reader.readVarInt());
        assertEquals(1, reader.readVarInt());
        assertTrue(reader.readBoolean()); // showTooltip
        assertEquals(0, reader.readVarInt()); // removed 0
    }

    @Test
    void trim768To769InlineMaterial() {
        // trim の直書きの材料（ingredient は oak_boat 794→803）は付け替える。
        Writer writer = new Writer();
        writer.writeVarInt(0); // windowId
        writer.writeVarInt(0); // stateId
        writer.writeShort(3);
        writer.writeVarInt(1);
        writer.writeVarInt(1);
        writer.writeVarInt(1); // added 1
        writer.writeVarInt(45); // trim
        writer.writeVarInt(0); // material（直書き）
        writer.writeString("minecraft:quartz");
        writer.writeVarInt(794); // ingredient（oak_boat）
        writer.writeVarInt(0); // overrides 0件
        writer.writeBoolean(false); // description 無し
        writer.writeVarInt(1); // pattern（参照 0）
        writer.writeBoolean(true); // showInTooltip
        writer.writeVarInt(0); // removed 0
        byte[] out = ChunkTranslator.translateSetSlot768To769(writer.bytes());
        Reader reader = new Reader(out);
        reader.readVarInt();
        reader.readVarInt();
        reader.readShort();
        assertEquals(1, reader.readVarInt());
        assertEquals(1, reader.readVarInt());
        assertEquals(1, reader.readVarInt()); // added 1
        assertEquals(45, reader.readVarInt()); // trim（769）
        assertEquals(0, reader.readVarInt()); // material（直書き）
        assertEquals("minecraft:quartz", reader.readString());
        assertEquals(803, reader.readVarInt()); // ingredient（769）
        assertEquals(0, reader.readVarInt()); // overrides 0件
        assertEquals(false, reader.readBoolean()); // description 無し
        assertEquals(1, reader.readVarInt()); // pattern（参照 0）
        assertEquals(true, reader.readBoolean()); // showInTooltip
        assertEquals(0, reader.readVarInt()); // removed 0
    }

    @Test
    void bannerPatterns768To769Identity() {
        // banner_patterns のパターンIDは同一（43種）。層はそのまま。
        Writer writer = new Writer();
        writer.writeVarInt(0); // windowId
        writer.writeVarInt(0); // stateId
        writer.writeShort(3);
        writer.writeVarInt(1);
        writer.writeVarInt(1);
        writer.writeVarInt(1); // added 1
        writer.writeVarInt(59); // banner_patterns
        writer.writeVarInt(1); // count 1
        writer.writeVarInt(2); // pattern 参照 1
        writer.writeVarInt(3); // color
        writer.writeVarInt(0); // removed 0
        byte[] out = ChunkTranslator.translateSetSlot768To769(writer.bytes());
        Reader reader = new Reader(out);
        reader.readVarInt();
        reader.readVarInt();
        reader.readShort();
        assertEquals(1, reader.readVarInt());
        assertEquals(1, reader.readVarInt());
        assertEquals(1, reader.readVarInt()); // added 1
        assertEquals(59, reader.readVarInt()); // banner_patterns（769）
        assertEquals(1, reader.readVarInt()); // count 1
        assertEquals(2, reader.readVarInt()); // pattern 参照 1
        assertEquals(3, reader.readVarInt()); // color
        assertEquals(0, reader.readVarInt()); // removed 0
    }

    @Test
    void potDecorations768To769ItemIds() {
        // pot_decorations の中身はアイテムID（oak_boat 794→803）。
        Writer writer = new Writer();
        writer.writeVarInt(0); // windowId
        writer.writeVarInt(0); // stateId
        writer.writeShort(3);
        writer.writeVarInt(1);
        writer.writeVarInt(1);
        writer.writeVarInt(1); // added 1
        writer.writeVarInt(61); // pot_decorations
        writer.writeVarInt(1); // count 1
        writer.writeVarInt(794); // oak_boat
        writer.writeVarInt(0); // removed 0
        byte[] out = ChunkTranslator.translateSetSlot768To769(writer.bytes());
        Reader reader = new Reader(out);
        reader.readVarInt();
        reader.readVarInt();
        reader.readShort();
        assertEquals(1, reader.readVarInt());
        assertEquals(1, reader.readVarInt());
        assertEquals(1, reader.readVarInt()); // added 1
        assertEquals(61, reader.readVarInt()); // pot_decorations（769）
        assertEquals(1, reader.readVarInt()); // count 1
        assertEquals(803, reader.readVarInt()); // oak_boat（769）
        assertEquals(0, reader.readVarInt()); // removed 0
    }

    @Test
    void tool768To769BlockIds() {
        // tool のルールの中のブロックID（heavy_core 1059→1066）を付け替える。
        Writer writer = new Writer();
        writer.writeVarInt(0); // windowId
        writer.writeVarInt(0); // stateId
        writer.writeShort(3);
        writer.writeVarInt(1);
        writer.writeVarInt(1);
        writer.writeVarInt(1); // added 1
        writer.writeVarInt(26); // tool
        writer.writeVarInt(1); // rules 1件
        writer.writeVarInt(2); // IDSet：1件のID
        writer.writeVarInt(1059); // heavy_core
        writer.writeBoolean(true); // speed 有り
        writer.writeFloat(2.0f);
        writer.writeBoolean(false); // correctDropForBlocks 無し
        writer.writeFloat(1.0f); // defaultMiningSpeed
        writer.writeVarInt(1); // damagePerBlock
        writer.writeVarInt(0); // removed 0
        byte[] out = ChunkTranslator.translateSetSlot768To769(writer.bytes());
        Reader reader = new Reader(out);
        reader.readVarInt();
        reader.readVarInt();
        reader.readShort();
        assertEquals(1, reader.readVarInt());
        assertEquals(1, reader.readVarInt());
        assertEquals(1, reader.readVarInt()); // added 1
        assertEquals(26, reader.readVarInt()); // tool（769）
        assertEquals(1, reader.readVarInt()); // rules 1件
        assertEquals(2, reader.readVarInt()); // IDSet：1件のID
        assertEquals(1066, reader.readVarInt()); // heavy_core（769）
        assertEquals(true, reader.readBoolean());
        assertEquals(2.0f, reader.readFloat(), 0.0001f);
        assertEquals(false, reader.readBoolean());
        assertEquals(1.0f, reader.readFloat(), 0.0001f);
        assertEquals(1, reader.readVarInt()); // damagePerBlock
        assertEquals(0, reader.readVarInt()); // removed 0
    }

    @Test
    void canPlaceOn768To769BlockIds() {
        // can_place_on の述語の中のブロックID（heavy_core 1059→1066）を付け替える。
        Writer writer = new Writer();
        writer.writeVarInt(0); // windowId
        writer.writeVarInt(0); // stateId
        writer.writeShort(3);
        writer.writeVarInt(1);
        writer.writeVarInt(1);
        writer.writeVarInt(1); // added 1
        writer.writeVarInt(11); // can_place_on
        writer.writeVarInt(1); // predicates 1件
        writer.writeBoolean(true); // hasBlocks
        writer.writeVarInt(2); // IDSet：1件のID
        writer.writeVarInt(1059); // heavy_core
        writer.writeBoolean(false); // hasProps 無し
        writer.writeAnonymousNbt(emptyCompound()); // nbt
        writer.writeBoolean(true); // showTooltip
        writer.writeVarInt(0); // removed 0
        byte[] out = ChunkTranslator.translateSetSlot768To769(writer.bytes());
        Reader reader = new Reader(out);
        reader.readVarInt();
        reader.readVarInt();
        reader.readShort();
        assertEquals(1, reader.readVarInt());
        assertEquals(1, reader.readVarInt());
        assertEquals(1, reader.readVarInt()); // added 1
        assertEquals(11, reader.readVarInt()); // can_place_on（769）
        assertEquals(1, reader.readVarInt()); // predicates 1件
        assertEquals(true, reader.readBoolean()); // hasBlocks
        assertEquals(2, reader.readVarInt()); // IDSet：1件のID
        assertEquals(1066, reader.readVarInt()); // heavy_core（769）
        assertEquals(false, reader.readBoolean()); // hasProps 無し
        assertTrue(reader.readAnonymousNbt().tag() instanceof Tag.CompoundTag); // nbt
        assertEquals(true, reader.readBoolean()); // showTooltip
        assertEquals(0, reader.readVarInt()); // removed 0
    }

    @Test
    void jukeboxPlayable768To769SoundHolder() {
        // jukebox_playable の直書きの曲データ（音の Holder）は付け替える（stone.break 1386→1401）。
        Writer writer = new Writer();
        writer.writeVarInt(0); // windowId
        writer.writeVarInt(0); // stateId
        writer.writeShort(3);
        writer.writeVarInt(1);
        writer.writeVarInt(1);
        writer.writeVarInt(1); // added 1
        writer.writeVarInt(52); // jukebox_playable
        writer.writeBoolean(true); // hasHolder
        writer.writeVarInt(0); // 直書き
        writer.writeVarInt(1387); // 音の Holder（参照 1386＝stone.break）
        writer.writeAnonymousNbt(emptyCompound()); // description
        writer.writeFloat(3.5f); // lengthInSeconds
        writer.writeVarInt(15); // comparatorOutput
        writer.writeBoolean(true); // showInTooltip
        writer.writeVarInt(0); // removed 0
        byte[] out = ChunkTranslator.translateSetSlot768To769(writer.bytes());
        Reader reader = new Reader(out);
        reader.readVarInt();
        reader.readVarInt();
        reader.readShort();
        assertEquals(1, reader.readVarInt());
        assertEquals(1, reader.readVarInt());
        assertEquals(1, reader.readVarInt()); // added 1
        assertEquals(52, reader.readVarInt()); // jukebox_playable（769）
        assertEquals(true, reader.readBoolean()); // hasHolder
        assertEquals(0, reader.readVarInt()); // 直書き
        assertEquals(1402, reader.readVarInt()); // 音の Holder（参照 1401＝stone.break）
        assertTrue(reader.readAnonymousNbt().tag() instanceof Tag.CompoundTag); // description
        assertEquals(3.5f, reader.readFloat(), 0.0001f);
        assertEquals(15, reader.readVarInt());
        assertEquals(true, reader.readBoolean()); // showInTooltip
        assertEquals(0, reader.readVarInt()); // removed 0
    }

    @Test
    void noteBlockSound768To769Identity() {
        // note_block_sound の音の名前（goat_horn.sound.0）は両方にあるためそのまま。
        Writer writer = new Writer();
        writer.writeVarInt(0); // windowId
        writer.writeVarInt(0); // stateId
        writer.writeShort(3);
        writer.writeVarInt(1);
        writer.writeVarInt(1);
        writer.writeVarInt(1); // added 1
        writer.writeVarInt(58); // note_block_sound
        writer.writeString("minecraft:item.goat_horn.sound.0");
        writer.writeVarInt(0); // removed 0
        byte[] out = ChunkTranslator.translateSetSlot768To769(writer.bytes());
        Reader reader = new Reader(out);
        reader.readVarInt();
        reader.readVarInt();
        reader.readShort();
        assertEquals(1, reader.readVarInt());
        assertEquals(1, reader.readVarInt());
        assertEquals(1, reader.readVarInt()); // added 1
        assertEquals(58, reader.readVarInt()); // note_block_sound（769）
        assertEquals("minecraft:item.goat_horn.sound.0", reader.readString());
        assertEquals(0, reader.readVarInt()); // removed 0
    }

    @Test
    void consumable768To769SoundHolder() {
        // consumable（22）の音の Holder（stone.break 1386→1401）を付け替える。
        // 効果は 0 件。
        Writer writer = new Writer();
        writer.writeVarInt(0); // windowId
        writer.writeVarInt(0); // stateId
        writer.writeShort(3);
        writer.writeVarInt(1);
        writer.writeVarInt(1);
        writer.writeVarInt(1); // added 1
        writer.writeVarInt(22); // consumable
        writer.writeFloat(1.6f); // consumeSeconds
        writer.writeVarInt(0); // animation
        writer.writeVarInt(1387); // 音の Holder（参照 1386）
        writer.writeBoolean(false); // hasParticles
        writer.writeVarInt(0); // effects 0件
        writer.writeVarInt(0); // removed 0
        byte[] out = ChunkTranslator.translateSetSlot768To769(writer.bytes());
        Reader reader = new Reader(out);
        reader.readVarInt();
        reader.readVarInt();
        reader.readShort();
        assertEquals(1, reader.readVarInt());
        assertEquals(1, reader.readVarInt());
        assertEquals(1, reader.readVarInt()); // added 1
        assertEquals(22, reader.readVarInt()); // consumable（769）
        assertEquals(1.6f, reader.readFloat(), 0.0001f);
        assertEquals(0, reader.readVarInt());
        assertEquals(1402, reader.readVarInt()); // 音の Holder（参照 1401）
        assertEquals(false, reader.readBoolean());
        assertEquals(0, reader.readVarInt()); // effects 0件
        assertEquals(0, reader.readVarInt()); // removed 0
    }

    @Test
    void equippable768To769SoundHolder() {
        // equippable（28）の装備音の Holder（stone.break 1386→1401）を付け替える。
        Writer writer = new Writer();
        writer.writeVarInt(0); // windowId
        writer.writeVarInt(0); // stateId
        writer.writeShort(3);
        writer.writeVarInt(1);
        writer.writeVarInt(1);
        writer.writeVarInt(1); // added 1
        writer.writeVarInt(28); // equippable
        writer.writeVarInt(3); // slot
        writer.writeVarInt(1387); // 音の Holder（参照 1386）
        writer.writeBoolean(false); // model 無し
        writer.writeBoolean(false); // cameraOverlay 無し
        writer.writeBoolean(false); // allowedEntities 無し
        writer.writeBoolean(true); // dispensable
        writer.writeBoolean(true); // swappable
        writer.writeBoolean(false); // damageable
        writer.writeVarInt(0); // removed 0
        byte[] out = ChunkTranslator.translateSetSlot768To769(writer.bytes());
        Reader reader = new Reader(out);
        reader.readVarInt();
        reader.readVarInt();
        reader.readShort();
        assertEquals(1, reader.readVarInt());
        assertEquals(1, reader.readVarInt());
        assertEquals(1, reader.readVarInt()); // added 1
        assertEquals(28, reader.readVarInt()); // equippable（769）
        assertEquals(3, reader.readVarInt()); // slot
        assertEquals(1402, reader.readVarInt()); // 音の Holder（参照 1401）
        assertEquals(false, reader.readBoolean());
        assertEquals(false, reader.readBoolean());
        assertEquals(false, reader.readBoolean());
        assertEquals(true, reader.readBoolean());
        assertEquals(true, reader.readBoolean());
        assertEquals(false, reader.readBoolean());
        assertEquals(0, reader.readVarInt()); // removed 0
    }

    @Test
    void repairable768To769ItemIds() {
        // repairable（29）の修理可能アイテムの IDSet（oak_boat 794→803）を付け替える。
        Writer writer = new Writer();
        writer.writeVarInt(0); // windowId
        writer.writeVarInt(0); // stateId
        writer.writeShort(3);
        writer.writeVarInt(1);
        writer.writeVarInt(1);
        writer.writeVarInt(1); // added 1
        writer.writeVarInt(29); // repairable
        writer.writeVarInt(2); // IDSet：1件のID
        writer.writeVarInt(794); // oak_boat
        writer.writeVarInt(0); // removed 0
        byte[] out = ChunkTranslator.translateSetSlot768To769(writer.bytes());
        Reader reader = new Reader(out);
        reader.readVarInt();
        reader.readVarInt();
        reader.readShort();
        assertEquals(1, reader.readVarInt());
        assertEquals(1, reader.readVarInt());
        assertEquals(1, reader.readVarInt()); // added 1
        assertEquals(29, reader.readVarInt()); // repairable（769）
        assertEquals(2, reader.readVarInt()); // IDSet：1件のID
        assertEquals(803, reader.readVarInt()); // oak_boat（769）
        assertEquals(0, reader.readVarInt()); // removed 0
    }

    @Test
    void useRemainder768To769Slot() {
        // use_remainder（23）は残り物のスロット1個（oak_boat 794→803）。
        Writer writer = new Writer();
        writer.writeVarInt(0); // windowId
        writer.writeVarInt(0); // stateId
        writer.writeShort(3);
        writer.writeVarInt(1);
        writer.writeVarInt(1);
        writer.writeVarInt(1); // added 1
        writer.writeVarInt(23); // use_remainder
        writeSlot(writer, 794, 1); // oak_boat
        writer.writeVarInt(0); // removed 0
        byte[] out = ChunkTranslator.translateSetSlot768To769(writer.bytes());
        Reader reader = new Reader(out);
        reader.readVarInt();
        reader.readVarInt();
        reader.readShort();
        assertEquals(1, reader.readVarInt());
        assertEquals(1, reader.readVarInt());
        assertEquals(1, reader.readVarInt()); // added 1
        assertEquals(23, reader.readVarInt()); // use_remainder（769）
        assertEquals(803, readSlotHead(reader)[1]); // oak_boat（769）
        assertEquals(0, reader.readVarInt()); // removed 0
    }

    @Test
    void deathProtection769To768SoundFallback() {
        // death_protection（32）の消費時効果 play_sound：769の新音 resin.break は
        // 768では stone.break になる。効果は 1件（play_sound）。
        Writer writer = new Writer();
        writer.writeVarInt(0); // windowId
        writer.writeVarInt(0); // stateId
        writer.writeShort(3);
        writer.writeVarInt(1);
        writer.writeVarInt(1);
        writer.writeVarInt(1); // added 1
        writer.writeVarInt(32); // death_protection
        writer.writeVarInt(1); // effects 1件
        writer.writeVarInt(4); // play_sound
        writer.writeVarInt(0); // 直書き
        writer.writeString("minecraft:block.resin.break");
        writer.writeBoolean(false); // fixedRange 無し
        writer.writeVarInt(0); // removed 0
        byte[] out = ChunkTranslator.translateSetSlot769To768(writer.bytes());
        Reader reader = new Reader(out);
        reader.readVarInt();
        reader.readVarInt();
        reader.readShort();
        assertEquals(1, reader.readVarInt());
        assertEquals(1, reader.readVarInt());
        assertEquals(1, reader.readVarInt()); // added 1
        assertEquals(32, reader.readVarInt()); // death_protection（768）
        assertEquals(1, reader.readVarInt()); // effects 1件
        assertEquals(4, reader.readVarInt()); // play_sound
        assertEquals(0, reader.readVarInt()); // 直書き
        assertEquals("minecraft:block.stone.break", reader.readString());
        assertEquals(false, reader.readBoolean());
        assertEquals(0, reader.readVarInt()); // removed 0
    }

    // ---- パーティクル ----

    @Test
    void worldParticles768To769Shriek() {
        // shriek 100→101。データは delay（varint）。
        Writer data = new Writer();
        data.writeVarInt(5); // delay
        byte[] out = ChunkTranslator.translateWorldParticles768To769(
                worldParticlesPacket768(100, data));
        Reader reader = new Reader(out);
        assertEquals(101, readWorldParticlesId769(reader));
        assertEquals(5, reader.readVarInt());
    }

    @Test
    void worldParticles769To768Shriek() {
        Writer data = new Writer();
        data.writeVarInt(5); // delay
        byte[] out = ChunkTranslator.translateWorldParticles769To768(
                worldParticlesPacket769(false, 101, data));
        Reader reader = new Reader(out);
        assertEquals(100, readWorldParticlesId768(reader));
        assertEquals(5, reader.readVarInt());
    }

    @Test
    void worldParticles768To769TrailAppendsDuration() {
        // trail 46→47。769では末尾に duration（varint）が追加される。
        // 768側には無いため既定値 20（1秒）を補う。
        Writer data = new Writer();
        data.writeDouble(4.0); // targetX
        data.writeDouble(5.0); // targetY
        data.writeDouble(6.0); // targetZ
        data.writeInt(0xFF0000); // color
        byte[] out = ChunkTranslator.translateWorldParticles768To769(
                worldParticlesPacket768(46, data));
        Reader reader = new Reader(out);
        assertEquals(47, readWorldParticlesId769(reader));
        assertEquals(4.0, reader.readDouble(), 0.0001);
        assertEquals(5.0, reader.readDouble(), 0.0001);
        assertEquals(6.0, reader.readDouble(), 0.0001);
        assertEquals(0xFF0000, reader.readInt());
        assertEquals(20, reader.readVarInt()); // duration（既定値）
    }

    @Test
    void worldParticles769To768TrailStripsDuration() {
        // trail 47→46。769の duration は768では落とす。
        Writer data = new Writer();
        data.writeDouble(4.0); // targetX
        data.writeDouble(5.0); // targetY
        data.writeDouble(6.0); // targetZ
        data.writeInt(0xFF0000); // color
        data.writeVarInt(30); // duration
        byte[] out = ChunkTranslator.translateWorldParticles769To768(
                worldParticlesPacket769(true, 47, data));
        Reader reader = new Reader(out);
        assertEquals(46, readWorldParticlesId768(reader));
        assertEquals(4.0, reader.readDouble(), 0.0001);
        assertEquals(5.0, reader.readDouble(), 0.0001);
        assertEquals(6.0, reader.readDouble(), 0.0001);
        assertEquals(0xFF0000, reader.readInt());
        assertEquals(0, reader.remaining());
    }

    @Test
    void worldParticles769To768PaleOakLeavesDropped() {
        // pale_oak_leaves（769の 34）は768に無いためパケットごと落とす。
        byte[] out = ChunkTranslator.translateWorldParticles769To768(
                worldParticlesPacket769(false, 34, new Writer()));
        assertNull(out);
    }

    @Test
    void worldParticles768To769BlockCrumble() {
        // block_crumble 110→111。データはブロック状態（trial_spawner 27107→27651）。
        Writer data = new Writer();
        data.writeVarInt(27107); // trial_spawner
        byte[] out = ChunkTranslator.translateWorldParticles768To769(
                worldParticlesPacket768(110, data));
        Reader reader = new Reader(out);
        assertEquals(111, readWorldParticlesId769(reader));
        assertEquals(27651, reader.readVarInt());
    }

    @Test
    void worldParticles768To769DustPillar() {
        // dust_pillar 106→107。データはブロック状態（vault 27119→27663）。
        Writer data = new Writer();
        data.writeVarInt(27119); // vault
        byte[] out = ChunkTranslator.translateWorldParticles768To769(
                worldParticlesPacket768(106, data));
        Reader reader = new Reader(out);
        assertEquals(107, readWorldParticlesId769(reader));
        assertEquals(27663, reader.readVarInt());
    }

    @Test
    void worldParticles768To769SculkCharge() {
        // sculk_charge 35→36。データは roll（float）。
        Writer data = new Writer();
        data.writeFloat(1.5f); // roll
        byte[] out = ChunkTranslator.translateWorldParticles768To769(
                worldParticlesPacket768(35, data));
        Reader reader = new Reader(out);
        assertEquals(36, readWorldParticlesId769(reader));
        assertEquals(1.5f, reader.readFloat(), 0.0001f);
    }

    @Test
    void worldParticles768To769AlwaysShowInserted() {
        // 768の World Particles には alwaysShow が無い。769では false を補う。
        Writer data = new Writer();
        byte[] out = ChunkTranslator.translateWorldParticles768To769(
                worldParticlesPacket768(0, data));
        Reader reader = new Reader(out);
        assertEquals(false, reader.readBoolean()); // longDistance
        assertEquals(false, reader.readBoolean()); // alwaysShow（補った既定値）
        reader.readDouble(); // x
        reader.readDouble(); // y
        reader.readDouble(); // z
        reader.readFloat(); // offsetX
        reader.readFloat(); // offsetY
        reader.readFloat(); // offsetZ
        reader.readFloat(); // speed
        assertEquals(4, reader.readInt()); // count
        assertEquals(0, reader.readVarInt()); // ambient_entity_effect
    }

    @Test
    void worldParticles769To768AlwaysShowDropped() {
        // 769の alwaysShow（true）は768では落とす。longDistance はそのまま。
        Writer data = new Writer();
        byte[] out = ChunkTranslator.translateWorldParticles769To768(
                worldParticlesPacket769(true, 0, data));
        Reader reader = new Reader(out);
        assertEquals(false, reader.readBoolean()); // longDistance
        reader.readDouble(); // x
        reader.readDouble(); // y
        reader.readDouble(); // z
        reader.readFloat(); // offsetX
        reader.readFloat(); // offsetY
        reader.readFloat(); // offsetZ
        reader.readFloat(); // speed
        assertEquals(4, reader.readInt()); // count
        assertEquals(0, reader.readVarInt()); // ambient_entity_effect
    }

    @Test
    void worldParticles768To769ItemSlot() {
        // item 44→45。データはスロット（oak_boat 794→803）。
        Writer data = new Writer();
        writeSlot(data, 794, 1);
        byte[] out = ChunkTranslator.translateWorldParticles768To769(
                worldParticlesPacket768(44, data));
        Reader reader = new Reader(out);
        assertEquals(45, readWorldParticlesId769(reader));
        assertEquals(803, readSlotHead(reader)[1]);
    }

    @Test
    void worldParticles768To769DustIdentity() {
        // dust（13）は両方とも同じ。データは色（int）＋大きさ（float）。
        Writer data = new Writer();
        data.writeInt(0xFF0000);
        data.writeFloat(1.5f);
        byte[] out = ChunkTranslator.translateWorldParticles768To769(
                worldParticlesPacket768(13, data));
        Reader reader = new Reader(out);
        assertEquals(13, readWorldParticlesId769(reader));
        assertEquals(0xFF0000, reader.readInt());
        assertEquals(1.5f, reader.readFloat(), 0.0001f);
    }

    // ---- エンティティメタデータ ----

    @Test
    void entityMetadata768To769BlockState() {
        // 型 14（ブロック状態）：trial_spawner 27107→27651。
        // 型 15（ブロック状態）：vault 27119→27663。
        Writer entries = new Writer();
        entries.writeUnsignedByte(0);
        entries.writeVarInt(14);
        entries.writeVarInt(27107); // trial_spawner
        entries.writeUnsignedByte(1);
        entries.writeVarInt(15);
        entries.writeVarInt(27119); // vault
        byte[] out = ChunkTranslator.translateEntityMetadata768To769(metadataPacket(5, entries));
        Reader reader = new Reader(out);
        assertEquals(5, reader.readVarInt());
        assertEquals(0, reader.readUnsignedByte());
        assertEquals(14, reader.readVarInt());
        assertEquals(27651, reader.readVarInt());
        assertEquals(1, reader.readUnsignedByte());
        assertEquals(15, reader.readVarInt());
        assertEquals(27663, reader.readVarInt());
        assertEquals(0xFF, reader.readUnsignedByte());
    }

    @Test
    void entityMetadata769To768CreakingHeartCorrection() {
        // 769の creaking_heart 2917（active=true／natural=false）は
        // 768の 2919（creaking=active）に補正する。
        Writer entries = new Writer();
        entries.writeUnsignedByte(0);
        entries.writeVarInt(14);
        entries.writeVarInt(2917);
        byte[] out = ChunkTranslator.translateEntityMetadata769To768(metadataPacket(5, entries));
        Reader reader = new Reader(out);
        assertEquals(5, reader.readVarInt());
        assertEquals(0, reader.readUnsignedByte());
        assertEquals(14, reader.readVarInt());
        assertEquals(2919, reader.readVarInt());
        assertEquals(0xFF, reader.readUnsignedByte());
    }

    @Test
    void entityMetadata768To769WolfVariantBiomes() {
        // 型 23（オオカミの variant の Holder）。直書き（0）の場合は
        // テクスチャ3文字列＋バイオームの IDSet で、768の plains 39 → 769の plains 40。
        Writer entries = new Writer();
        entries.writeUnsignedByte(0);
        entries.writeVarInt(23);
        entries.writeVarInt(0); // 直書き
        entries.writeString("minecraft:entity/wolf/wolf");
        entries.writeString("minecraft:entity/wolf/wolf_tame");
        entries.writeString("minecraft:entity/wolf/wolf_angry");
        entries.writeVarInt(2); // IDSet：1件のID
        entries.writeVarInt(39); // plains（768）
        byte[] out = ChunkTranslator.translateEntityMetadata768To769(metadataPacket(5, entries));
        Reader reader = new Reader(out);
        assertEquals(5, reader.readVarInt());
        assertEquals(0, reader.readUnsignedByte());
        assertEquals(23, reader.readVarInt());
        assertEquals(0, reader.readVarInt()); // 直書き
        assertEquals("minecraft:entity/wolf/wolf", reader.readString());
        assertEquals("minecraft:entity/wolf/wolf_tame", reader.readString());
        assertEquals("minecraft:entity/wolf/wolf_angry", reader.readString());
        assertEquals(2, reader.readVarInt()); // IDSet：1件のID
        assertEquals(40, reader.readVarInt()); // plains（769）
        assertEquals(0xFF, reader.readUnsignedByte());
    }

    @Test
    void entityMetadata769To768WolfVariantBiomes() {
        // 769の plains 40 → 768の plains 39。
        Writer entries = new Writer();
        entries.writeUnsignedByte(0);
        entries.writeVarInt(23);
        entries.writeVarInt(0); // 直書き
        entries.writeString("minecraft:entity/wolf/wolf");
        entries.writeString("minecraft:entity/wolf/wolf_tame");
        entries.writeString("minecraft:entity/wolf/wolf_angry");
        entries.writeVarInt(2); // IDSet：1件のID
        entries.writeVarInt(40); // plains（769）
        byte[] out = ChunkTranslator.translateEntityMetadata769To768(metadataPacket(5, entries));
        Reader reader = new Reader(out);
        assertEquals(5, reader.readVarInt());
        assertEquals(0, reader.readUnsignedByte());
        assertEquals(23, reader.readVarInt());
        assertEquals(0, reader.readVarInt()); // 直書き
        assertEquals("minecraft:entity/wolf/wolf", reader.readString());
        assertEquals("minecraft:entity/wolf/wolf_tame", reader.readString());
        assertEquals("minecraft:entity/wolf/wolf_angry", reader.readString());
        assertEquals(2, reader.readVarInt()); // IDSet：1件のID
        assertEquals(39, reader.readVarInt()); // plains（768）
        assertEquals(0xFF, reader.readUnsignedByte());
    }

    @Test
    void entityMetadata768To769TrailParticleAppendsDuration() {
        // 型 17（粒子）：trail 46→47、duration 20 を補う。
        Writer entries = new Writer();
        entries.writeUnsignedByte(0);
        entries.writeVarInt(17);
        entries.writeVarInt(46); // trail
        entries.writeDouble(4.0);
        entries.writeDouble(5.0);
        entries.writeDouble(6.0);
        entries.writeInt(0xFF0000);
        byte[] out = ChunkTranslator.translateEntityMetadata768To769(metadataPacket(5, entries));
        Reader reader = new Reader(out);
        assertEquals(5, reader.readVarInt());
        assertEquals(0, reader.readUnsignedByte());
        assertEquals(17, reader.readVarInt());
        assertEquals(47, reader.readVarInt()); // trail（769）
        assertEquals(4.0, reader.readDouble(), 0.0001);
        assertEquals(5.0, reader.readDouble(), 0.0001);
        assertEquals(6.0, reader.readDouble(), 0.0001);
        assertEquals(0xFF0000, reader.readInt());
        assertEquals(20, reader.readVarInt()); // duration（既定値）
        assertEquals(0xFF, reader.readUnsignedByte());
    }

    @Test
    void entityMetadata769To768TrailParticleStripsDuration() {
        // 型 17（粒子）：trail 47→46、duration を落とす。
        Writer entries = new Writer();
        entries.writeUnsignedByte(0);
        entries.writeVarInt(17);
        entries.writeVarInt(47); // trail
        entries.writeDouble(4.0);
        entries.writeDouble(5.0);
        entries.writeDouble(6.0);
        entries.writeInt(0xFF0000);
        entries.writeVarInt(30); // duration
        byte[] out = ChunkTranslator.translateEntityMetadata769To768(metadataPacket(5, entries));
        Reader reader = new Reader(out);
        assertEquals(5, reader.readVarInt());
        assertEquals(0, reader.readUnsignedByte());
        assertEquals(17, reader.readVarInt());
        assertEquals(46, reader.readVarInt()); // trail（768）
        assertEquals(4.0, reader.readDouble(), 0.0001);
        assertEquals(5.0, reader.readDouble(), 0.0001);
        assertEquals(6.0, reader.readDouble(), 0.0001);
        assertEquals(0xFF0000, reader.readInt());
        assertEquals(0xFF, reader.readUnsignedByte());
    }

    @Test
    void entityMetadata768To769Slot() {
        // 型 7（スロット）：oak_boat 794→803。
        Writer entries = new Writer();
        entries.writeUnsignedByte(6);
        entries.writeVarInt(7);
        writeSlot(entries, 794, 1);
        byte[] out = ChunkTranslator.translateEntityMetadata768To769(metadataPacket(5, entries));
        Reader reader = new Reader(out);
        assertEquals(5, reader.readVarInt());
        assertEquals(6, reader.readUnsignedByte());
        assertEquals(7, reader.readVarInt());
        int[] head = readSlotHead(reader);
        assertEquals(1, head[0]);
        assertEquals(803, head[1]);
        assertEquals(0, head[2]);
        assertEquals(0xFF, reader.readUnsignedByte());
    }

    @Test
    void entityMetadata768To769PaintingIdentity() {
        // 型 26（絵画の variant。参照＋1）：6→6（同一）。
        Writer entries = new Writer();
        entries.writeUnsignedByte(0);
        entries.writeVarInt(26);
        entries.writeVarInt(7); // variant 6 の参照
        byte[] out = ChunkTranslator.translateEntityMetadata768To769(metadataPacket(5, entries));
        Reader reader = new Reader(out);
        assertEquals(5, reader.readVarInt());
        assertEquals(0, reader.readUnsignedByte());
        assertEquals(26, reader.readVarInt());
        assertEquals(7, reader.readVarInt());
        assertEquals(0xFF, reader.readUnsignedByte());
    }

    // ---- チャンク / ブロック変化 / ブロックエンティティ ----

    @Test
    void chunkData768To769RemapsTrialSpawnerAndBiome() {
        Writer payload = new Writer();
        payload.writeInt(3);
        payload.writeInt(-2);
        payload.writeAnonymousNbt(heightmaps());
        payload.writeByteArray(indirectSectionWithBiomePalette(
                new int[]{0, 1, 27107, 27119}, new int[]{38, 39}));
        payload.writeVarInt(2);
        payload.writeUnsignedByte(0x11);
        payload.writeShort(64);
        payload.writeVarInt(10); // mob_spawner
        payload.writeByte(0); // NBT 無し
        payload.writeUnsignedByte(0x22);
        payload.writeShort(65);
        payload.writeVarInt(43); // trial_spawner
        payload.writeByte(0);
        payload.writeBytes(new byte[]{0x0A, 0x0B, 0x0C}); // 光データ

        byte[] translated = ChunkTranslator.translateChunkData768To769(payload.bytes());
        Reader reader = new Reader(translated);
        assertEquals(3, reader.readInt());
        assertEquals(-2, reader.readInt());
        assertTrue(reader.readAnonymousNbt().tag() instanceof Tag.CompoundTag);
        byte[] sections = reader.readBytes(reader.readVarInt());
        assertArrayEquals(new int[]{0, 1, 27651, 27663}, readIndirectPalette(sections));
        assertArrayEquals(new int[]{38, 40}, readBiomePalette(sections));
        assertEquals(2, reader.readVarInt()); // ブロックエンティティは2件
        assertEquals(0x11, reader.readUnsignedByte());
        assertEquals(64, reader.readShort());
        assertEquals(10, reader.readVarInt()); // mob_spawner（769）
        assertEquals(0, reader.readByte()); // NBT 無し
        assertEquals(0x22, reader.readUnsignedByte());
        assertEquals(65, reader.readShort());
        assertEquals(43, reader.readVarInt()); // trial_spawner（769）
        assertEquals(0, reader.readByte()); // NBT 無し
        assertArrayEquals(new byte[]{0x0A, 0x0B, 0x0C}, reader.rest());
    }

    @Test
    void chunkData769To768PaleGardenToPlains() {
        // 769の pale_garden（39）は768では plains（39）になる。
        // plains（769の 40）も768では plains（39）。
        Writer payload = new Writer();
        payload.writeInt(0);
        payload.writeInt(0);
        payload.writeAnonymousNbt(heightmaps());
        payload.writeByteArray(indirectSectionWithBiomePalette(
                new int[]{0, 1, 2917, 7230}, new int[]{38, 39}));
        payload.writeVarInt(0);

        byte[] translated = ChunkTranslator.translateChunkData769To768(payload.bytes());
        Reader reader = new Reader(translated);
        reader.readInt();
        reader.readInt();
        reader.readAnonymousNbt();
        byte[] sections = reader.readBytes(reader.readVarInt());
        assertArrayEquals(new int[]{0, 1, 2919, 1}, readIndirectPalette(sections));
        assertArrayEquals(new int[]{38, 39}, readBiomePalette(sections));
        assertEquals(0, reader.readVarInt());
    }

    @Test
    void chunkData768To769SingleBiomePlainsShifts() {
        // バイオームがシングルの場合も plains（768の 39）は 769の 40 になる。
        Writer payload = new Writer();
        payload.writeInt(0);
        payload.writeInt(0);
        payload.writeAnonymousNbt(heightmaps());
        payload.writeByteArray(indirectSection(new int[]{0, 1}, 39));
        payload.writeVarInt(0);
        byte[] translated = ChunkTranslator.translateChunkData768To769(payload.bytes());
        Reader reader = new Reader(translated);
        reader.readInt();
        reader.readInt();
        reader.readAnonymousNbt();
        byte[] sections = reader.readBytes(reader.readVarInt());
        assertEquals(40, readSingleBiome(sections));
        assertEquals(0, reader.readVarInt());
    }

    @Test
    void chunkData769To768SingleBiomePlainsShiftsBack() {
        // 769の plains（40）は768では plains（39）になる。
        Writer payload = new Writer();
        payload.writeInt(0);
        payload.writeInt(0);
        payload.writeAnonymousNbt(heightmaps());
        payload.writeByteArray(indirectSection(new int[]{0, 1}, 40));
        payload.writeVarInt(0);
        byte[] translated = ChunkTranslator.translateChunkData769To768(payload.bytes());
        Reader reader = new Reader(translated);
        reader.readInt();
        reader.readInt();
        reader.readAnonymousNbt();
        byte[] sections = reader.readBytes(reader.readVarInt());
        assertEquals(39, readSingleBiome(sections));
        assertEquals(0, reader.readVarInt());
    }

    @Test
    void blockChange768To769() {
        Writer writer = new Writer();
        writer.writePositionRaw(0x123456789AL);
        writer.writeVarInt(27107); // trial_spawner
        byte[] out = ChunkTranslator.translateBlockChange768To769(writer.bytes());
        Reader reader = new Reader(out);
        assertEquals(0x123456789AL, reader.readPositionRaw());
        assertEquals(27651, reader.readVarInt());
    }

    @Test
    void blockChange769To768() {
        Writer writer = new Writer();
        writer.writePositionRaw(0x123456789AL);
        writer.writeVarInt(27663); // vault
        byte[] out = ChunkTranslator.translateBlockChange769To768(writer.bytes());
        Reader reader = new Reader(out);
        assertEquals(0x123456789AL, reader.readPositionRaw());
        assertEquals(27119, reader.readVarInt());
    }

    @Test
    void multiBlockChange768To769() {
        Writer writer = new Writer();
        writer.writeLong(0x123456789AL);
        writer.writeVarInt(1);
        writer.writeVarInt((27107 << 12) | 5);
        byte[] out = ChunkTranslator.translateMultiBlockChange768To769(writer.bytes());
        Reader reader = new Reader(out);
        assertEquals(0x123456789AL, reader.readLong());
        assertEquals(1, reader.readVarInt());
        assertEquals((27651 << 12) | 5, reader.readVarInt());
    }

    @Test
    void blockEntityData768To769CreakingHeartKept() {
        // creaking_heart（10）は768にも769にもあるため落とさない（767向けには落としていた）。
        Writer writer = new Writer();
        writer.writePositionRaw(0x123456789AL);
        writer.writeVarInt(10); // creaking_heart
        writer.writeByte(0); // NBT 無し
        byte[] out = ChunkTranslator.translateBlockEntityData768To769(writer.bytes());
        Reader reader = new Reader(out);
        assertEquals(0x123456789AL, reader.readPositionRaw());
        assertEquals(10, reader.readVarInt());
        assertEquals(0, reader.readByte());
    }

    @Test
    void blockEntityData769To768TrialSpawnerKept() {
        // trial_spawner（43）は両方にあるためそのまま。
        Writer writer = new Writer();
        writer.writePositionRaw(0x123456789AL);
        writer.writeVarInt(43); // trial_spawner
        writer.writeByte(0); // NBT 無し
        byte[] out = ChunkTranslator.translateBlockEntityData769To768(writer.bytes());
        Reader reader = new Reader(out);
        assertEquals(0x123456789AL, reader.readPositionRaw());
        assertEquals(43, reader.readVarInt());
        assertEquals(0, reader.readByte());
    }

    // ---- Spawn Entity / ボート ----

    @Test
    void spawnEntity768To769FallingBlock() {
        // falling_block 50→49。objectData のブロック状態も付け替える。
        byte[] out = ChunkTranslator.translateSpawnEntity768To769(spawnEntityPacket(50, 27107));
        int[] fields = readSpawnEntity(new Reader(out));
        assertEquals(7, fields[0]);
        assertEquals(49, fields[1]); // falling_block（769）
        assertEquals(27651, fields[2]); // trial_spawner（769）
    }

    @Test
    void spawnEntity769To768FallingBlock() {
        byte[] out = ChunkTranslator.translateSpawnEntity769To768(spawnEntityPacket(49, 27663));
        int[] fields = readSpawnEntity(new Reader(out));
        assertEquals(7, fields[0]);
        assertEquals(50, fields[1]); // falling_block（768）
        assertEquals(27119, fields[2]); // vault（768）
    }

    @Test
    void spawnEntity768To769OakBoat() {
        // oak_boat 85→84。
        byte[] out = ChunkTranslator.translateSpawnEntity768To769(spawnEntityPacket(85, 0));
        int[] fields = readSpawnEntity(new Reader(out));
        assertEquals(84, fields[1]); // oak_boat（769）
        assertEquals(0, fields[2]);
    }

    @Test
    void spawnEntity768To769CreakingTransient() {
        // creaking_transient（768の 30）は769では creaking（29）になる。
        byte[] out = ChunkTranslator.translateSpawnEntity768To769(spawnEntityPacket(30, 0));
        int[] fields = readSpawnEntity(new Reader(out));
        assertEquals(29, fields[1]); // creaking（769）
    }

    @Test
    void spawnEntity769To768Creaking() {
        // creaking（769の 29）は768では creaking（29）のまま。
        byte[] out = ChunkTranslator.translateSpawnEntity769To768(spawnEntityPacket(29, 0));
        int[] fields = readSpawnEntity(new Reader(out));
        assertEquals(29, fields[1]); // creaking（768）
    }

    @Test
    void boatVariantForEntityType769() {
        // 769のボートIDは768から1つずつ前にずれる（creaking_transient 削除のため）。
        assertEquals(0, ChunkTranslator.boatVariantForEntityType769(84)); // oak_boat
        assertEquals(9, ChunkTranslator.boatVariantForEntityType769(89)); // pale_oak_boat
        assertEquals(-1, ChunkTranslator.boatVariantForEntityType769(94)); // pig はボートではない
    }

    @Test
    void boatEntityType769ForVariant() {
        assertEquals(118, ChunkTranslator.boatEntityType769ForVariant(1, false)); // spruce_boat
        assertEquals(8, ChunkTranslator.boatEntityType769ForVariant(8, true)); // bamboo_chest_raft
        assertEquals(89, ChunkTranslator.boatEntityType769ForVariant(9, false)); // pale_oak_boat
        assertEquals(85, ChunkTranslator.boatEntityType769ForVariant(0, true)); // oak_chest_boat
        assertEquals(-1, ChunkTranslator.boatEntityType769ForVariant(10, false)); // 範囲外
    }

    // ---- 音 ----

    @Test
    void soundEffect768To769Holder() {
        // 音の参照：stone.break 1386→1401（Holder は id＋1）。
        Writer writer = new Writer();
        writer.writeVarInt(1387); // 音の Holder（参照 1386）
        writer.writeVarInt(0); // category
        writer.writeInt(100);
        writer.writeInt(64);
        writer.writeInt(200);
        writer.writeFloat(1.0f);
        writer.writeFloat(1.0f);
        writer.writeLong(12345L); // seed
        byte[] out = ChunkTranslator.translateSoundEffect768To769(writer.bytes());
        Reader reader = new Reader(out);
        assertEquals(1402, reader.readVarInt()); // 音の Holder（参照 1401）
        assertEquals(0, reader.readVarInt());
        assertEquals(100, reader.readInt());
        assertEquals(64, reader.readInt());
        assertEquals(200, reader.readInt());
        assertEquals(1.0f, reader.readFloat(), 0.0001f);
        assertEquals(1.0f, reader.readFloat(), 0.0001f);
        assertEquals(12345L, reader.readLong());
    }

    @Test
    void soundEffect769To768NewSoundFallsBack() {
        // 音の直書きの名前：769にしか無い resin の音は 768では石の破壊音になる。
        // 両方にある音の名前（goat_horn.sound.0 等）はそのまま通る。
        Writer writer = new Writer();
        writer.writeVarInt(0); // 直書き
        writer.writeString("minecraft:block.resin.break");
        writer.writeBoolean(false); // fixedRange 無し
        writer.writeVarInt(0); // category
        writer.writeInt(100);
        writer.writeInt(64);
        writer.writeInt(200);
        writer.writeFloat(1.0f);
        writer.writeFloat(1.0f);
        writer.writeLong(0L);
        byte[] out = ChunkTranslator.translateSoundEffect769To768(writer.bytes());
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
    void soundEffect769To768GoatHornIdentity() {
        // 両方にある音の名前はそのまま。
        Writer writer = new Writer();
        writer.writeVarInt(0); // 直書き
        writer.writeString("minecraft:item.goat_horn.sound.0");
        writer.writeBoolean(false); // fixedRange 無し
        writer.writeVarInt(0); // category
        writer.writeInt(100);
        writer.writeInt(64);
        writer.writeInt(200);
        writer.writeFloat(1.0f);
        writer.writeFloat(1.0f);
        writer.writeLong(0L);
        byte[] out = ChunkTranslator.translateSoundEffect769To768(writer.bytes());
        Reader reader = new Reader(out);
        assertEquals(0, reader.readVarInt()); // 直書き
        assertEquals("minecraft:item.goat_horn.sound.0", reader.readString());
        assertEquals(false, reader.readBoolean());
    }

    @Test
    void stopSound768To769GoatHorn() {
        // Stop Sound の音の名前：goat_horn.sound.0 は両方にあるためそのまま。
        Writer writer = new Writer();
        writer.writeByte(0x02); // 音の名前あり
        writer.writeString("minecraft:item.goat_horn.sound.0");
        byte[] out = ChunkTranslator.translateStopSound768To769(writer.bytes());
        Reader reader = new Reader(out);
        assertEquals(0x02, reader.readByte());
        assertEquals("minecraft:item.goat_horn.sound.0", reader.readString());
    }

    @Test
    void entitySoundEffect768To769() {
        Writer writer = new Writer();
        writer.writeVarInt(1387); // 音の Holder（参照 1386＝stone.break）
        writer.writeVarInt(0); // category
        writer.writeVarInt(7); // entityId
        writer.writeFloat(1.0f);
        writer.writeFloat(1.0f);
        writer.writeLong(99L); // seed
        byte[] out = ChunkTranslator.translateEntitySoundEffect768To769(writer.bytes());
        Reader reader = new Reader(out);
        assertEquals(1402, reader.readVarInt()); // 音の Holder（参照 1401）
        assertEquals(0, reader.readVarInt());
        assertEquals(7, reader.readVarInt());
        assertEquals(1.0f, reader.readFloat(), 0.0001f);
        assertEquals(1.0f, reader.readFloat(), 0.0001f);
        assertEquals(99L, reader.readLong());
    }

    // ---- Explosion ----

    @Test
    void explosion768To769KnockbackFloatToDouble() {
        // knockback が f32×3 から f64×3 に変わる。粒子は dust、音は stone.break。
        Writer writer = new Writer();
        writer.writeDouble(1.0); // x
        writer.writeDouble(2.0); // y
        writer.writeDouble(3.0); // z
        writer.writeBoolean(true); // hasKnockback
        writer.writeFloat(1.0f);
        writer.writeFloat(2.0f);
        writer.writeFloat(3.0f);
        writer.writeVarInt(13); // dust
        writer.writeInt(0xFF0000);
        writer.writeFloat(1.5f);
        writer.writeVarInt(1387); // 音の Holder（参照 1386）
        byte[] out = ChunkTranslator.translateExplosion768To769(writer.bytes());
        Reader reader = new Reader(out);
        assertEquals(1.0, reader.readDouble(), 0.0001);
        assertEquals(2.0, reader.readDouble(), 0.0001);
        assertEquals(3.0, reader.readDouble(), 0.0001);
        assertEquals(true, reader.readBoolean());
        assertEquals(1.0, reader.readDouble(), 0.0001); // f64 になった
        assertEquals(2.0, reader.readDouble(), 0.0001);
        assertEquals(3.0, reader.readDouble(), 0.0001);
        assertEquals(13, reader.readVarInt()); // dust（769）
        assertEquals(0xFF0000, reader.readInt());
        assertEquals(1.5f, reader.readFloat(), 0.0001f);
        assertEquals(1402, reader.readVarInt()); // 音の Holder（参照 1401）
    }

    @Test
    void explosion769To768KnockbackDoubleToFloat() {
        Writer writer = new Writer();
        writer.writeDouble(1.0); // x
        writer.writeDouble(2.0); // y
        writer.writeDouble(3.0); // z
        writer.writeBoolean(true); // hasKnockback
        writer.writeDouble(1.0);
        writer.writeDouble(2.0);
        writer.writeDouble(3.0);
        writer.writeVarInt(13); // dust
        writer.writeInt(0xFF0000);
        writer.writeFloat(1.5f);
        writer.writeVarInt(1402); // 音の Holder（参照 1401）
        byte[] out = ChunkTranslator.translateExplosion769To768(writer.bytes());
        Reader reader = new Reader(out);
        assertEquals(1.0, reader.readDouble(), 0.0001);
        assertEquals(2.0, reader.readDouble(), 0.0001);
        assertEquals(3.0, reader.readDouble(), 0.0001);
        assertEquals(true, reader.readBoolean());
        assertEquals(1.0f, reader.readFloat(), 0.0001f); // f32 に戻る
        assertEquals(2.0f, reader.readFloat(), 0.0001f);
        assertEquals(3.0f, reader.readFloat(), 0.0001f);
        assertEquals(13, reader.readVarInt()); // dust（768）
        assertEquals(0xFF0000, reader.readInt());
        assertEquals(1.5f, reader.readFloat(), 0.0001f);
        assertEquals(1387, reader.readVarInt()); // 音の Holder（参照 1386）
    }

    @Test
    void explosion769To768PaleOakLeavesDropped() {
        // 粒子が pale_oak_leaves のときはパケットごと落とす。
        Writer writer = new Writer();
        writer.writeDouble(1.0);
        writer.writeDouble(2.0);
        writer.writeDouble(3.0);
        writer.writeBoolean(false); // hasKnockback 無し
        writer.writeVarInt(34); // pale_oak_leaves
        writer.writeVarInt(1402); // 音の Holder
        assertNull(ChunkTranslator.translateExplosion769To768(writer.bytes()));
    }

    @Test
    void explosion768To769NoKnockback() {
        // knockback 無しの場合はそのまま（追加の double は無い）。
        Writer writer = new Writer();
        writer.writeDouble(1.0);
        writer.writeDouble(2.0);
        writer.writeDouble(3.0);
        writer.writeBoolean(false); // hasKnockback 無し
        writer.writeVarInt(0); // ambient_entity_effect
        writer.writeVarInt(1387); // 音の Holder
        byte[] out = ChunkTranslator.translateExplosion768To769(writer.bytes());
        Reader reader = new Reader(out);
        assertEquals(1.0, reader.readDouble(), 0.0001);
        assertEquals(2.0, reader.readDouble(), 0.0001);
        assertEquals(3.0, reader.readDouble(), 0.0001);
        assertEquals(false, reader.readBoolean());
        assertEquals(0, reader.readVarInt());
        assertEquals(1402, reader.readVarInt());
    }

    // ---- Damage / エフェクト ----

    @Test
    void damageEvent768To769Identity() {
        // ダメージ種別は49種とも同一（magic 27）。
        Writer writer = new Writer();
        writer.writeVarInt(7); // entityId
        writer.writeVarInt(27); // magic
        writer.writeVarInt(0); // sourceCauseId
        writer.writeVarInt(0); // sourceDirectId
        writer.writeBoolean(false); // hasPosition 無し
        byte[] out = ChunkTranslator.translateDamageEvent768To769(writer.bytes());
        Reader reader = new Reader(out);
        assertEquals(7, reader.readVarInt());
        assertEquals(27, reader.readVarInt()); // magic（769）
        assertEquals(0, reader.readVarInt());
        assertEquals(0, reader.readVarInt());
        assertEquals(false, reader.readBoolean());
    }

    @Test
    void damageEvent769To768Identity() {
        Writer writer = new Writer();
        writer.writeVarInt(7); // entityId
        writer.writeVarInt(8); // ender_pearl（769にもある）
        writer.writeVarInt(0);
        writer.writeVarInt(0);
        writer.writeBoolean(false);
        byte[] out = ChunkTranslator.translateDamageEvent769To768(writer.bytes());
        Reader reader = new Reader(out);
        assertEquals(7, reader.readVarInt());
        assertEquals(8, reader.readVarInt()); // ender_pearl（768）
        assertEquals(0, reader.readVarInt());
        assertEquals(0, reader.readVarInt());
        assertEquals(false, reader.readBoolean());
    }

    @Test
    void entityEffect768To769Identity() {
        // エフェクトIDは同一（speed 1）。
        Writer writer = new Writer();
        writer.writeVarInt(7); // entityId
        writer.writeVarInt(1); // speed
        writer.writeVarInt(2); // amplifier
        writer.writeVarInt(100); // duration
        writer.writeUnsignedByte(0); // flags
        byte[] out = ChunkTranslator.translateEntityEffect768To769(writer.bytes());
        Reader reader = new Reader(out);
        assertEquals(7, reader.readVarInt());
        assertEquals(1, reader.readVarInt());
        assertEquals(2, reader.readVarInt());
        assertEquals(100, reader.readVarInt());
        assertEquals(0, reader.readUnsignedByte());
    }

    @Test
    void removeEntityEffect769To768Identity() {
        Writer writer = new Writer();
        writer.writeVarInt(7); // entityId
        writer.writeVarInt(1); // speed
        byte[] out = ChunkTranslator.translateRemoveEntityEffect769To768(writer.bytes());
        Reader reader = new Reader(out);
        assertEquals(7, reader.readVarInt());
        assertEquals(1, reader.readVarInt());
    }

    // ---- Set Cooldown ----

    @Test
    void setCooldown768To769GroupName() {
        // Set Cooldown は両方ともグループ名（文字列）＋tick。
        Writer writer = new Writer();
        writer.writeString("minecraft:ender_pearl");
        writer.writeVarInt(20);
        byte[] out = ChunkTranslator.translateSetCooldown768To769(writer.bytes());
        Reader reader = new Reader(out);
        assertEquals("minecraft:ender_pearl", reader.readString());
        assertEquals(20, reader.readVarInt());
    }

    @Test
    void setCooldown769To768GroupName() {
        Writer writer = new Writer();
        writer.writeString("minecraft:chorus_fruit");
        writer.writeVarInt(20);
        byte[] out = ChunkTranslator.translateSetCooldown769To768(writer.bytes());
        Reader reader = new Reader(out);
        assertEquals("minecraft:chorus_fruit", reader.readString());
        assertEquals(20, reader.readVarInt());
    }

    // ---- Window / 取引 / その他 ----

    @Test
    void setCursorItem768To769() {
        // Set Cursor Item は両方ともスロット1個（breeze_rod 1134→1143）。
        Writer writer = new Writer();
        writeSlot(writer, 1134, 1); // breeze_rod
        byte[] out = ChunkTranslator.translateSetCursorItem768To769(writer.bytes());
        Reader reader = new Reader(out);
        assertEquals(1143, readSlotHead(reader)[1]);
    }

    @Test
    void setPlayerInventory768To769() {
        // Set Player Inventory は slotId＋スロット（oak_boat 794→803）。
        Writer writer = new Writer();
        writer.writeVarInt(5); // slotId
        writeSlot(writer, 794, 1); // oak_boat
        byte[] out = ChunkTranslator.translateSetPlayerInventory768To769(writer.bytes());
        Reader reader = new Reader(out);
        assertEquals(5, reader.readVarInt());
        assertEquals(803, readSlotHead(reader)[1]);
    }

    @Test
    void windowItems768To769() {
        Writer writer = new Writer();
        writer.writeVarInt(0); // windowId（varint）
        writer.writeVarInt(0); // stateId
        writer.writeVarInt(1); // count 1
        writeSlot(writer, 794, 1); // oak_boat
        writeSlot(writer, 1214, 1); // music_disc_creator（携帯品）
        byte[] out = ChunkTranslator.translateWindowItems768To769(writer.bytes());
        Reader reader = new Reader(out);
        assertEquals(0, reader.readVarInt());
        assertEquals(0, reader.readVarInt());
        assertEquals(1, reader.readVarInt());
        assertEquals(803, readSlotHead(reader)[1]);
        assertEquals(1224, readSlotHead(reader)[1]);
    }

    @Test
    void entityEquipment768To769() {
        Writer writer = new Writer();
        writer.writeVarInt(5); // entityId
        writer.writeUnsignedByte(0); // slotByte（継続なし）
        writeSlot(writer, 794, 1); // oak_boat
        byte[] out = ChunkTranslator.translateEntityEquipment768To769(writer.bytes());
        Reader reader = new Reader(out);
        assertEquals(5, reader.readVarInt());
        assertEquals(0, reader.readUnsignedByte());
        assertEquals(803, readSlotHead(reader)[1]);
    }

    @Test
    void tradeList768To769() {
        Writer writer = new Writer();
        writer.writeVarInt(3); // windowId
        writer.writeVarInt(1); // count 1
        // 1件の取引：コスト（ItemCost＝{itemId, 個数, 追加成分}）＋結果＋
        // 2つ目のコスト無し＋残りの数値。
        writer.writeVarInt(794); // ItemCost：itemId（oak_boat、768）
        writer.writeVarInt(1); // ItemCost：個数
        writer.writeVarInt(0); // ItemCost：追加コンポーネント数（0）
        writeSlot(writer, 1214, 1); // music_disc_creator（結果）
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
        byte[] out = ChunkTranslator.translateTradeList768To769(writer.bytes());
        Reader reader = new Reader(out);
        assertEquals(3, reader.readVarInt());
        assertEquals(1, reader.readVarInt());
        assertEquals(803, reader.readVarInt()); // ItemCost：itemId（oak_boat、769）
        assertEquals(1, reader.readVarInt()); // ItemCost：個数
        assertEquals(0, reader.readVarInt()); // ItemCost：追加コンポーネント数
        assertEquals(1224, readSlotHead(reader)[1]); // 結果（769）
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
    void openWindow768To769() {
        // メニュー種別は同一（crafter_3x3 は 768も769も 7）。
        Writer writer = new Writer();
        writer.writeVarInt(1); // windowId
        writer.writeVarInt(7); // crafter_3x3
        writer.writeAnonymousNbt(emptyCompound()); // title
        byte[] out = ChunkTranslator.translateOpenWindow768To769(writer.bytes());
        Reader reader = new Reader(out);
        assertEquals(1, reader.readVarInt());
        assertEquals(7, reader.readVarInt());
        assertTrue(reader.readAnonymousNbt().tag() instanceof Tag.CompoundTag);
    }

    @Test
    void blockAction768To769() {
        // ブロックID（heavy_core 1059→1066）を付け替える。
        Writer writer = new Writer();
        writer.writePositionRaw(0x123456789AL);
        writer.writeUnsignedByte(1);
        writer.writeUnsignedByte(2);
        writer.writeVarInt(1059);
        byte[] out = ChunkTranslator.translateBlockAction768To769(writer.bytes());
        Reader reader = new Reader(out);
        assertEquals(0x123456789AL, reader.readPositionRaw());
        assertEquals(1, reader.readUnsignedByte());
        assertEquals(2, reader.readUnsignedByte());
        assertEquals(1066, reader.readVarInt());
    }

    @Test
    void windowClick768To769() {
        Writer writer = new Writer();
        writer.writeVarInt(0); // windowId（varint）
        writer.writeVarInt(1); // stateId
        writer.writeShort(3); // slot
        writer.writeByte(0); // button
        writer.writeVarInt(0); // mode
        writer.writeVarInt(1); // changed 1件
        writer.writeShort(3);
        writeSlot(writer, 794, 1); // oak_boat
        writeSlot(writer, 1214, 1); // music_disc_creator（携帯品）
        byte[] out = ChunkTranslator.translateWindowClick768To769(writer.bytes());
        Reader reader = new Reader(out);
        assertEquals(0, reader.readVarInt()); // windowId（varint）
        assertEquals(1, reader.readVarInt());
        assertEquals(3, reader.readShort());
        assertEquals(0, reader.readByte());
        assertEquals(0, reader.readVarInt());
        assertEquals(1, reader.readVarInt()); // changed 1件
        assertEquals(3, reader.readShort());
        assertEquals(803, readSlotHead(reader)[1]);
        assertEquals(1224, readSlotHead(reader)[1]);
    }

    @Test
    void setCreativeSlot768To769() {
        Writer writer = new Writer();
        writer.writeShort(3);
        writeSlot(writer, 1134, 1); // breeze_rod
        byte[] out = ChunkTranslator.translateSetCreativeSlot768To769(writer.bytes());
        Reader reader = new Reader(out);
        assertEquals(3, reader.readShort());
        assertEquals(1143, readSlotHead(reader)[1]);
    }

    @Test
    void updateAttributes768To769Identity() {
        // water_movement_efficiency 31→31、burning_time 7→7。修飾子の UUID は文字列のまま。
        Writer writer = new Writer();
        writer.writeVarInt(7); // entityId
        writer.writeVarInt(2); // count 2
        writer.writeVarInt(31); // water_movement_efficiency
        writer.writeDouble(1.5);
        writer.writeVarInt(1); // modifiers 1件
        writer.writeString("123e4567-e89b-12d3-a456-426614174000");
        writer.writeDouble(0.5);
        writer.writeByte(0);
        writer.writeVarInt(7); // burning_time
        writer.writeDouble(2.0);
        writer.writeVarInt(0); // modifiers 0件
        byte[] out = ChunkTranslator.translateUpdateAttributes768To769(writer.bytes());
        Reader reader = new Reader(out);
        assertEquals(7, reader.readVarInt());
        assertEquals(2, reader.readVarInt());
        assertEquals(31, reader.readVarInt()); // water_movement_efficiency（769）
        assertEquals(1.5, reader.readDouble(), 0.0001);
        assertEquals(1, reader.readVarInt());
        assertEquals("123e4567-e89b-12d3-a456-426614174000", reader.readString());
        assertEquals(0.5, reader.readDouble(), 0.0001);
        assertEquals(0, reader.readByte());
        assertEquals(7, reader.readVarInt()); // burning_time（769）
        assertEquals(2.0, reader.readDouble(), 0.0001);
        assertEquals(0, reader.readVarInt());
    }
}
