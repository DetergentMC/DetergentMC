// Copyright (c) 2026 DetergentMC contributors.
// SPDX-License-Identifier: Apache-2.0

package io.detergentmc.protocol;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.detergentmc.protocol.PacketIO.Reader;
import io.detergentmc.protocol.PacketIO.Writer;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * {@link ChunkTranslator} の736（1.16.1）⇔751（1.16.2）向け
 * 値付け替え変換器のテスト（Stage B フォローアップ）。
 * フィールド構成は736・751で同一のため、レジストリIDだけ付け替える
 * （ChunkTranslator.java の736節末尾の javadoc を参照）。
 * アンカーは Stage A の実測値（RegistryRemapper736Test と同じ）：
 * ブロック状態 5145→5149（cauldron）、アイテム 791→792、音 626→633、
 * 音名 entity.piglin_brute.ambient→entity.piglin.ambient
 * （751→736方向のみ。736→751は改名無しのため恒等写像）。
 * falling_block のエンティティ種別IDは両版とも26。
 */
class ChunkTranslator736FollowUpTest {

    /** ブロック状態IDのアンカー（736:5145 → 751:5149。cauldron）。 */
    private static final int STATE_736 = 5145;
    private static final int STATE_751 = 5149;

    /** アイテムIDのアンカー（736:791 → 751:792）。 */
    private static final int ITEM_736 = 791;
    private static final int ITEM_751 = 792;

    /** 音IDのアンカー（736:626 → 751:633）。 */
    private static final int SOUND_736 = 626;
    private static final int SOUND_751 = 633;

    /** スロット1個（736／751形式：bool＋アイテムID＋個数＋名前付き NBT 無し）。 */
    private static void writeSlot(Writer writer, int itemId, int count) {
        writer.writeBoolean(true);
        writer.writeVarInt(itemId);
        writer.writeByte(count);
        writer.writeByte(0); // NBT 無し
    }

    /** スロット1個を読む（アイテムIDと個数を返す）。 */
    private static int[] readSlot(Reader reader) {
        assertTrue(reader.readBoolean());
        int itemId = reader.readVarInt();
        int count = reader.readByte();
        assertEquals(0, reader.readByte()); // NBT 無し
        return new int[]{itemId, count};
    }

    /** 位置（packed long。0x123456789L はそのまま通ることを確かめるための値）。 */
    private static final long POSITION = 0x123456789L;

    // ------------------------------------------------------------------
    // Block Change
    // ------------------------------------------------------------------

    private static byte[] blockChangePayload(int state) {
        Writer writer = new Writer();
        writer.writePositionRaw(POSITION);
        writer.writeVarInt(state);
        return writer.bytes();
    }

    @Test
    void blockChange736To751RemapsState() {
        byte[] out = ChunkTranslator.translateBlockChange736To751(blockChangePayload(STATE_736));
        Reader reader = new Reader(out);
        assertEquals(POSITION, reader.readPositionRaw());
        assertEquals(STATE_751, reader.readVarInt());
        assertEquals(0, reader.remaining());
    }

    @Test
    void blockChange751To736RemapsState() {
        byte[] out = ChunkTranslator.translateBlockChange751To736(blockChangePayload(STATE_751));
        Reader reader = new Reader(out);
        assertEquals(POSITION, reader.readPositionRaw());
        assertEquals(STATE_736, reader.readVarInt());
        assertEquals(0, reader.remaining());
        assertArrayEquals(blockChangePayload(STATE_751),
                ChunkTranslator.translateBlockChange736To751(out));
    }

    // ------------------------------------------------------------------
    // Set Cooldown
    // ------------------------------------------------------------------

    private static byte[] setCooldownPayload(int item, int ticks) {
        Writer writer = new Writer();
        writer.writeVarInt(item);
        writer.writeVarInt(ticks);
        return writer.bytes();
    }

    @Test
    void setCooldown736To751RemapsItem() {
        byte[] out = ChunkTranslator.translateSetCooldown736To751(setCooldownPayload(ITEM_736, 20));
        Reader reader = new Reader(out);
        assertEquals(ITEM_751, reader.readVarInt());
        assertEquals(20, reader.readVarInt());
        assertEquals(0, reader.remaining());
    }

    @Test
    void setCooldown751To736RemapsItem() {
        byte[] out = ChunkTranslator.translateSetCooldown751To736(setCooldownPayload(ITEM_751, 20));
        Reader reader = new Reader(out);
        assertEquals(ITEM_736, reader.readVarInt());
        assertEquals(20, reader.readVarInt());
        assertEquals(0, reader.remaining());
    }

    // ------------------------------------------------------------------
    // Entity Equipment
    // ------------------------------------------------------------------

    /** Entity Equipment：[entityId][slotByte 0x80付き＋スロット][slotByte＋スロット]。 */
    private static byte[] entityEquipmentPayload(int item1, int item2) {
        Writer writer = new Writer();
        writer.writeVarInt(42); // entityId
        writer.writeUnsignedByte(0x80 | 0); // slot 0、続きあり
        writeSlot(writer, item1, 1);
        writer.writeUnsignedByte(1); // slot 1、終端
        writeSlot(writer, item2, 2);
        return writer.bytes();
    }

    @Test
    void entityEquipment736To751RemapsSlots() {
        byte[] out = ChunkTranslator.translateEntityEquipment736To751(
                entityEquipmentPayload(ITEM_736, 1));
        Reader reader = new Reader(out);
        assertEquals(42, reader.readVarInt());
        assertEquals(0x80, reader.readUnsignedByte());
        assertArrayEquals(new int[]{ITEM_751, 1}, readSlot(reader));
        assertEquals(1, reader.readUnsignedByte());
        assertArrayEquals(new int[]{1, 2}, readSlot(reader));
        assertEquals(0, reader.remaining());
    }

    @Test
    void entityEquipment751To736RemapsSlots() {
        byte[] out = ChunkTranslator.translateEntityEquipment751To736(
                entityEquipmentPayload(ITEM_751, 1));
        Reader reader = new Reader(out);
        assertEquals(42, reader.readVarInt());
        assertEquals(0x80, reader.readUnsignedByte());
        assertArrayEquals(new int[]{ITEM_736, 1}, readSlot(reader));
        assertEquals(1, reader.readUnsignedByte());
        assertArrayEquals(new int[]{1, 2}, readSlot(reader));
        assertEquals(0, reader.remaining());
    }

    // ------------------------------------------------------------------
    // Trade List
    // ------------------------------------------------------------------

    /** Trade List：取引2件（inputItem2 あり／なし）。 */
    private static byte[] tradeListPayload(int item) {
        Writer writer = new Writer();
        writer.writeVarInt(3); // windowId
        writer.writeUnsignedByte(2); // 取引2件
        // 取引1：inputItem2 あり。
        writeSlot(writer, item, 1);
        writeSlot(writer, 1, 1);
        writer.writeBoolean(true);
        writeSlot(writer, item, 2);
        writer.writeBoolean(false); // tradeDisabled
        writer.writeInt(3); // nbTradeUses
        writer.writeInt(10); // maximumNbTradeUses
        writer.writeInt(5); // xp
        writer.writeInt(0); // specialPrice
        writer.writeFloat(1.0f); // priceMultiplier
        writer.writeInt(2); // demand
        // 取引2：inputItem2 なし。
        writeSlot(writer, 1, 1);
        writeSlot(writer, item, 1);
        writer.writeBoolean(false);
        writer.writeBoolean(true); // tradeDisabled
        writer.writeInt(0);
        writer.writeInt(5);
        writer.writeInt(0);
        writer.writeInt(-1);
        writer.writeFloat(0.5f);
        writer.writeInt(0);
        writer.writeVarInt(2); // villagerLevel
        writer.writeVarInt(7); // experience
        writer.writeBoolean(true); // isRegularVillager
        writer.writeBoolean(false); // canRestock
        return writer.bytes();
    }

    @Test
    void tradeList736To751RemapsSlots() {
        byte[] out = ChunkTranslator.translateTradeList736To751(tradeListPayload(ITEM_736));
        Reader reader = new Reader(out);
        assertEquals(3, reader.readVarInt());
        assertEquals(2, reader.readUnsignedByte());
        assertArrayEquals(new int[]{ITEM_751, 1}, readSlot(reader));
        assertArrayEquals(new int[]{1, 1}, readSlot(reader));
        assertTrue(reader.readBoolean());
        assertArrayEquals(new int[]{ITEM_751, 2}, readSlot(reader));
        assertFalse(reader.readBoolean());
        assertEquals(3, reader.readInt());
        assertEquals(10, reader.readInt());
        assertEquals(5, reader.readInt());
        assertEquals(0, reader.readInt());
        assertEquals(1.0f, reader.readFloat());
        assertEquals(2, reader.readInt());
        assertArrayEquals(new int[]{1, 1}, readSlot(reader));
        assertArrayEquals(new int[]{ITEM_751, 1}, readSlot(reader));
        assertFalse(reader.readBoolean());
        assertTrue(reader.readBoolean());
        assertEquals(0, reader.readInt());
        assertEquals(5, reader.readInt());
        assertEquals(0, reader.readInt());
        assertEquals(-1, reader.readInt());
        assertEquals(0.5f, reader.readFloat());
        assertEquals(0, reader.readInt());
        assertEquals(2, reader.readVarInt());
        assertEquals(7, reader.readVarInt());
        assertTrue(reader.readBoolean());
        assertFalse(reader.readBoolean());
        assertEquals(0, reader.remaining());
    }

    @Test
    void tradeList751To736RemapsSlots() {
        byte[] out = ChunkTranslator.translateTradeList751To736(tradeListPayload(ITEM_751));
        Reader reader = new Reader(out);
        assertEquals(3, reader.readVarInt());
        assertEquals(2, reader.readUnsignedByte());
        assertArrayEquals(new int[]{ITEM_736, 1}, readSlot(reader));
        assertArrayEquals(new int[]{1, 1}, readSlot(reader));
        assertTrue(reader.readBoolean());
        assertArrayEquals(new int[]{ITEM_736, 2}, readSlot(reader));
        assertFalse(reader.readBoolean());
        assertEquals(3, reader.readInt());
        assertEquals(10, reader.readInt());
        assertEquals(5, reader.readInt());
        assertEquals(0, reader.readInt());
        assertEquals(1.0f, reader.readFloat());
        assertEquals(2, reader.readInt());
        assertArrayEquals(new int[]{1, 1}, readSlot(reader));
        assertArrayEquals(new int[]{ITEM_736, 1}, readSlot(reader));
        assertFalse(reader.readBoolean());
        assertTrue(reader.readBoolean());
        assertEquals(0, reader.readInt());
        assertEquals(5, reader.readInt());
        assertEquals(0, reader.readInt());
        assertEquals(-1, reader.readInt());
        assertEquals(0.5f, reader.readFloat());
        assertEquals(0, reader.readInt());
        assertEquals(2, reader.readVarInt());
        assertEquals(7, reader.readVarInt());
        assertTrue(reader.readBoolean());
        assertFalse(reader.readBoolean());
        assertEquals(0, reader.remaining());
        assertArrayEquals(tradeListPayload(ITEM_751),
                ChunkTranslator.translateTradeList736To751(out));
    }

    // ------------------------------------------------------------------
    // Block Entity Data
    // ------------------------------------------------------------------

    private static byte[] blockEntityDataPayload(int type) {
        Writer writer = new Writer();
        writer.writePositionRaw(POSITION);
        writer.writeUnsignedByte(type);
        writer.writeByte(0); // NBT 無し
        return writer.bytes();
    }

    @Test
    void blockEntityData736To751RoundTrips() {
        byte[] out = ChunkTranslator.translateBlockEntityData736To751(blockEntityDataPayload(0));
        Reader reader = new Reader(out);
        assertEquals(POSITION, reader.readPositionRaw());
        assertEquals(0, reader.readUnsignedByte());
        assertEquals(0, reader.readByte());
        assertEquals(0, reader.remaining());
        assertArrayEquals(blockEntityDataPayload(0),
                ChunkTranslator.translateBlockEntityData751To736(out));
    }

    @Test
    void blockEntityDataDropsUnknownType() {
        // 範囲外の種別（33件まで）は DROP（-1）のためパケットごと落とす。
        assertNull(ChunkTranslator.translateBlockEntityData736To751(blockEntityDataPayload(200)));
        assertNull(ChunkTranslator.translateBlockEntityData751To736(blockEntityDataPayload(200)));
    }

    // ------------------------------------------------------------------
    // Entity Metadata
    // ------------------------------------------------------------------

    /** Entity Metadata：型13（ブロック状態）・型15（粒子）・型6（スロット）・型16（村人）・型1。 */
    private static byte[] entityMetadataPayload(int state, int item) {
        Writer writer = new Writer();
        writer.writeVarInt(42); // entityId
        writer.writeUnsignedByte(0);
        writer.writeVarInt(13);
        writer.writeVarInt(state);
        writer.writeUnsignedByte(1);
        writer.writeVarInt(15);
        writer.writeVarInt(3); // 粒子 block
        writer.writeVarInt(state);
        writer.writeUnsignedByte(2);
        writer.writeVarInt(6);
        writeSlot(writer, item, 1);
        writer.writeUnsignedByte(3);
        writer.writeVarInt(16);
        writer.writeVarInt(2); // 村人の種別
        writer.writeVarInt(3); // 職業
        writer.writeVarInt(1); // レベル
        writer.writeUnsignedByte(4);
        writer.writeVarInt(1);
        writer.writeVarInt(7);
        writer.writeUnsignedByte(0xFF); // 終端
        return writer.bytes();
    }

    @Test
    void entityMetadata736To751RemapsSlotBlockStateParticle() {
        byte[] out = ChunkTranslator.translateEntityMetadata736To751(
                entityMetadataPayload(STATE_736, ITEM_736));
        Reader reader = new Reader(out);
        assertEquals(42, reader.readVarInt());
        assertEquals(0, reader.readUnsignedByte());
        assertEquals(13, reader.readVarInt());
        assertEquals(STATE_751, reader.readVarInt());
        assertEquals(1, reader.readUnsignedByte());
        assertEquals(15, reader.readVarInt());
        assertEquals(3, reader.readVarInt());
        assertEquals(STATE_751, reader.readVarInt());
        assertEquals(2, reader.readUnsignedByte());
        assertEquals(6, reader.readVarInt());
        assertArrayEquals(new int[]{ITEM_751, 1}, readSlot(reader));
        assertEquals(3, reader.readUnsignedByte());
        assertEquals(16, reader.readVarInt());
        assertEquals(2, reader.readVarInt());
        assertEquals(3, reader.readVarInt());
        assertEquals(1, reader.readVarInt());
        assertEquals(4, reader.readUnsignedByte());
        assertEquals(1, reader.readVarInt());
        assertEquals(7, reader.readVarInt());
        assertEquals(0xFF, reader.readUnsignedByte());
        assertEquals(0, reader.remaining());
    }

    @Test
    void entityMetadata751To736RoundTrips() {
        byte[] out = ChunkTranslator.translateEntityMetadata751To736(
                entityMetadataPayload(STATE_751, ITEM_751));
        assertArrayEquals(entityMetadataPayload(STATE_736, ITEM_736), out);
    }

    // ------------------------------------------------------------------
    // Sound Effect 系
    // ------------------------------------------------------------------

    private static byte[] soundEffectPayload(int sound) {
        Writer writer = new Writer();
        writer.writeVarInt(sound);
        writer.writeVarInt(2); // soundCategory
        writer.writeInt(100); // x
        writer.writeInt(64); // y
        writer.writeInt(-200); // z
        writer.writeFloat(1.0f); // volume
        writer.writeFloat(0.5f); // pitch
        return writer.bytes();
    }

    @Test
    void soundEffect736To751RemapsSound() {
        byte[] out = ChunkTranslator.translateSoundEffect736To751(soundEffectPayload(SOUND_736));
        Reader reader = new Reader(out);
        assertEquals(SOUND_751, reader.readVarInt());
        assertEquals(2, reader.readVarInt());
        assertEquals(100, reader.readInt());
        assertEquals(64, reader.readInt());
        assertEquals(-200, reader.readInt());
        assertEquals(1.0f, reader.readFloat());
        assertEquals(0.5f, reader.readFloat());
        assertEquals(0, reader.remaining());
    }

    @Test
    void soundEffect751To736RemapsSound() {
        byte[] out = ChunkTranslator.translateSoundEffect751To736(soundEffectPayload(SOUND_751));
        Reader reader = new Reader(out);
        assertEquals(SOUND_736, reader.readVarInt());
        assertEquals(2, reader.readVarInt());
        assertEquals(100, reader.readInt());
        assertEquals(64, reader.readInt());
        assertEquals(-200, reader.readInt());
        assertEquals(1.0f, reader.readFloat());
        assertEquals(0.5f, reader.readFloat());
        assertEquals(0, reader.remaining());
        assertArrayEquals(soundEffectPayload(SOUND_751),
                ChunkTranslator.translateSoundEffect736To751(out));
    }

    private static byte[] entitySoundEffectPayload(int sound) {
        Writer writer = new Writer();
        writer.writeVarInt(sound);
        writer.writeVarInt(2); // soundCategory
        writer.writeVarInt(42); // entityId
        writer.writeFloat(1.0f);
        writer.writeFloat(0.5f);
        return writer.bytes();
    }

    @Test
    void entitySoundEffect736To751RemapsSound() {
        byte[] out = ChunkTranslator.translateEntitySoundEffect736To751(
                entitySoundEffectPayload(SOUND_736));
        Reader reader = new Reader(out);
        assertEquals(SOUND_751, reader.readVarInt());
        assertEquals(2, reader.readVarInt());
        assertEquals(42, reader.readVarInt());
        assertEquals(1.0f, reader.readFloat());
        assertEquals(0.5f, reader.readFloat());
        assertEquals(0, reader.remaining());
    }

    @Test
    void entitySoundEffect751To736RoundTrips() {
        byte[] out = ChunkTranslator.translateEntitySoundEffect751To736(
                entitySoundEffectPayload(SOUND_751));
        assertArrayEquals(entitySoundEffectPayload(SOUND_736), out);
    }

    private static byte[] namedSoundEffectPayload(String name) {
        Writer writer = new Writer();
        writer.writeString(name);
        writer.writeVarInt(2);
        writer.writeInt(100);
        writer.writeInt(64);
        writer.writeInt(-200);
        writer.writeFloat(1.0f);
        writer.writeFloat(0.5f);
        return writer.bytes();
    }

    @Test
    void namedSoundEffect736To751KeepsName() {
        // 736→751は改名が無いため音の名前はそのまま通る。
        byte[] out = ChunkTranslator.translateNamedSoundEffect736To751(
                namedSoundEffectPayload("block.stone.break"));
        Reader reader = new Reader(out);
        assertEquals("block.stone.break", reader.readString());
        assertEquals(2, reader.readVarInt());
        assertEquals(100, reader.readInt());
        assertEquals(64, reader.readInt());
        assertEquals(-200, reader.readInt());
        assertEquals(1.0f, reader.readFloat());
        assertEquals(0.5f, reader.readFloat());
        assertEquals(0, reader.remaining());
    }

    @Test
    void namedSoundEffect751To736RemapsPiglinBruteName() {
        byte[] out = ChunkTranslator.translateNamedSoundEffect751To736(
                namedSoundEffectPayload("entity.piglin_brute.ambient"));
        Reader reader = new Reader(out);
        assertEquals("entity.piglin.ambient", reader.readString());
        assertEquals(2, reader.readVarInt());
        assertEquals(100, reader.readInt());
        assertEquals(64, reader.readInt());
        assertEquals(-200, reader.readInt());
        assertEquals(1.0f, reader.readFloat());
        assertEquals(0.5f, reader.readFloat());
        assertEquals(0, reader.remaining());
    }

    /** Stop Sound：[flags][音源 varint（bit 0）][音名（bit 1）]。 */
    private static byte[] stopSoundPayload(int flags, String name) {
        Writer writer = new Writer();
        writer.writeByte(flags);
        if ((flags & 0x01) != 0) {
            writer.writeVarInt(3); // 音源
        }
        if ((flags & 0x02) != 0) {
            writer.writeString(name);
        }
        return writer.bytes();
    }

    @Test
    void stopSound736To751KeepsNameAndSource() {
        byte[] out = ChunkTranslator.translateStopSound736To751(
                stopSoundPayload(3, "block.stone.break"));
        Reader reader = new Reader(out);
        assertEquals(3, reader.readByte());
        assertEquals(3, reader.readVarInt());
        assertEquals("block.stone.break", reader.readString());
        assertEquals(0, reader.remaining());
    }

    @Test
    void stopSound751To736RemapsName() {
        byte[] out = ChunkTranslator.translateStopSound751To736(
                stopSoundPayload(3, "entity.piglin_brute.ambient"));
        Reader reader = new Reader(out);
        assertEquals(3, reader.readByte());
        assertEquals(3, reader.readVarInt());
        assertEquals("entity.piglin.ambient", reader.readString());
        assertEquals(0, reader.remaining());
    }

    @Test
    void stopSoundFlagsZeroPassesThrough() {
        byte[] out = ChunkTranslator.translateStopSound736To751(stopSoundPayload(0, null));
        assertArrayEquals(stopSoundPayload(0, null), out);
    }

    // ------------------------------------------------------------------
    // World Particles
    // ------------------------------------------------------------------

    /** World Particles（block 粒子。データは末尾のブロック状態ID）。 */
    private static byte[] worldParticlesBlockPayload(int state) {
        Writer writer = new Writer();
        writer.writeInt(3); // 粒子 block
        writer.writeBoolean(true); // longDistance
        writer.writeDouble(1.5);
        writer.writeDouble(64.0);
        writer.writeDouble(-2.5);
        writer.writeFloat(0.1f);
        writer.writeFloat(0.2f);
        writer.writeFloat(0.3f);
        writer.writeFloat(0.5f); // speed
        writer.writeInt(4); // count
        writer.writeVarInt(state);
        return writer.bytes();
    }

    @Test
    void worldParticles736To751BlockParticleRemapsState() {
        byte[] out = ChunkTranslator.translateWorldParticles736To751(
                worldParticlesBlockPayload(STATE_736));
        Reader reader = new Reader(out);
        assertEquals(3, reader.readInt());
        assertTrue(reader.readBoolean());
        assertEquals(1.5, reader.readDouble());
        assertEquals(64.0, reader.readDouble());
        assertEquals(-2.5, reader.readDouble());
        assertEquals(0.1f, reader.readFloat());
        assertEquals(0.2f, reader.readFloat());
        assertEquals(0.3f, reader.readFloat());
        assertEquals(0.5f, reader.readFloat());
        assertEquals(4, reader.readInt());
        assertEquals(STATE_751, reader.readVarInt());
        assertEquals(0, reader.remaining());
    }

    @Test
    void worldParticles751To736RoundTrips() {
        byte[] out = ChunkTranslator.translateWorldParticles751To736(
                worldParticlesBlockPayload(STATE_751));
        assertArrayEquals(worldParticlesBlockPayload(STATE_736), out);
    }

    /** World Particles（dust 粒子。データは末尾の 4 float）。 */
    private static byte[] worldParticlesDustPayload() {
        Writer writer = new Writer();
        writer.writeInt(14); // 粒子 dust
        writer.writeBoolean(false);
        writer.writeDouble(0.0);
        writer.writeDouble(0.0);
        writer.writeDouble(0.0);
        writer.writeFloat(0.0f);
        writer.writeFloat(0.0f);
        writer.writeFloat(0.0f);
        writer.writeFloat(0.0f);
        writer.writeInt(1);
        writer.writeFloat(1.0f);
        writer.writeFloat(0.0f);
        writer.writeFloat(0.0f);
        writer.writeFloat(2.0f);
        return writer.bytes();
    }

    @Test
    void worldParticlesDustKeepsFloats() {
        byte[] payload = worldParticlesDustPayload();
        assertArrayEquals(payload, ChunkTranslator.translateWorldParticles736To751(payload));
        assertArrayEquals(payload, ChunkTranslator.translateWorldParticles751To736(payload));
    }

    /** World Particles（item 粒子。データは末尾のスロット）。 */
    private static byte[] worldParticlesItemPayload(int item) {
        Writer writer = new Writer();
        writer.writeInt(34); // 粒子 item
        writer.writeBoolean(false);
        writer.writeDouble(0.0);
        writer.writeDouble(0.0);
        writer.writeDouble(0.0);
        writer.writeFloat(0.0f);
        writer.writeFloat(0.0f);
        writer.writeFloat(0.0f);
        writer.writeFloat(0.0f);
        writer.writeInt(1);
        writeSlot(writer, item, 1);
        return writer.bytes();
    }

    @Test
    void worldParticlesItem736To751RemapsSlot() {
        byte[] out = ChunkTranslator.translateWorldParticles736To751(
                worldParticlesItemPayload(ITEM_736));
        Reader reader = new Reader(out);
        assertEquals(34, reader.readInt());
        assertFalse(reader.readBoolean());
        reader.readDouble();
        reader.readDouble();
        reader.readDouble();
        reader.readFloat();
        reader.readFloat();
        reader.readFloat();
        reader.readFloat();
        assertEquals(1, reader.readInt());
        assertArrayEquals(new int[]{ITEM_751, 1}, readSlot(reader));
        assertEquals(0, reader.remaining());
    }

    /** World Particles（データ無し粒子。ペイロードはそのまま通る）。 */
    private static byte[] worldParticlesPlainPayload() {
        Writer writer = new Writer();
        writer.writeInt(0);
        writer.writeBoolean(false);
        writer.writeDouble(0.0);
        writer.writeDouble(0.0);
        writer.writeDouble(0.0);
        writer.writeFloat(0.0f);
        writer.writeFloat(0.0f);
        writer.writeFloat(0.0f);
        writer.writeFloat(0.0f);
        writer.writeInt(1);
        return writer.bytes();
    }

    @Test
    void worldParticlesPlainPassesThrough() {
        byte[] payload = worldParticlesPlainPayload();
        assertArrayEquals(payload, ChunkTranslator.translateWorldParticles736To751(payload));
        assertArrayEquals(payload, ChunkTranslator.translateWorldParticles751To736(payload));
    }

    // ------------------------------------------------------------------
    // Spawn Entity
    // ------------------------------------------------------------------

    /** Spawn Entity：[entityId][UUID][type][x/y/z][pitch][yaw][objectData i32][vel×3]。 */
    private static byte[] spawnEntityPayload(int type, int objectData) {
        Writer writer = new Writer();
        writer.writeVarInt(7); // entityId
        writer.writeBytes(new byte[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16});
        writer.writeVarInt(type);
        writer.writeDouble(1.0);
        writer.writeDouble(64.0);
        writer.writeDouble(2.0);
        writer.writeByte(10); // pitch
        writer.writeByte(20); // yaw
        writer.writeInt(objectData);
        writer.writeShort(100); // velX
        writer.writeShort(-200); // velY
        writer.writeShort(300); // velZ
        return writer.bytes();
    }

    @Test
    void spawnEntity736To751FallingBlockRemapsObjectData() {
        byte[] out = ChunkTranslator.translateSpawnEntity736To751(
                spawnEntityPayload(26, STATE_736));
        Reader reader = new Reader(out);
        assertEquals(7, reader.readVarInt());
        assertArrayEquals(new byte[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16},
                reader.readBytes(16));
        assertEquals(26, reader.readVarInt());
        assertEquals(1.0, reader.readDouble());
        assertEquals(64.0, reader.readDouble());
        assertEquals(2.0, reader.readDouble());
        assertEquals(10, reader.readByte());
        assertEquals(20, reader.readByte());
        assertEquals(STATE_751, reader.readInt());
        assertEquals(100, reader.readShort());
        assertEquals(-200, reader.readShort());
        assertEquals(300, reader.readShort());
        assertEquals(0, reader.remaining());
    }

    @Test
    void spawnEntity751To736FallingBlockRoundTrips() {
        byte[] out = ChunkTranslator.translateSpawnEntity751To736(
                spawnEntityPayload(26, STATE_751));
        assertArrayEquals(spawnEntityPayload(26, STATE_736), out);
    }

    @Test
    void spawnEntityPiglinKeepsObjectData() {
        // falling_block 以外の objectData は付け替えない。
        byte[] payload = spawnEntityPayload(60, 123);
        assertArrayEquals(payload, ChunkTranslator.translateSpawnEntity736To751(payload));
        assertArrayEquals(payload, ChunkTranslator.translateSpawnEntity751To736(payload));
    }

    // ------------------------------------------------------------------
    // Multi Block Change
    // ------------------------------------------------------------------

    /** Multi Block Change（736）：[chunkX i32][chunkZ i32][count varint][{hPos u8, y u8, state}]。 */
    private static byte[] multiBlockChange736Payload(int chunkX, int chunkZ, int[][] records) {
        Writer writer = new Writer();
        writer.writeInt(chunkX);
        writer.writeInt(chunkZ);
        writer.writeVarInt(records.length);
        for (int[] record : records) {
            writer.writeUnsignedByte((record[0] << 4) | record[1]); // horizontalPos
            writer.writeUnsignedByte(record[2]); // y（絶対Y）
            writer.writeVarInt(record[3]); // blockId
        }
        return writer.bytes();
    }

    /** 751の chunkCoordinates（SectionPos.asLong）。 */
    private static long sectionPos(int chunkX, int sectionY, int chunkZ) {
        return ((long) (chunkX & 0x3FFFFF) << 42)
                | ((long) (chunkZ & 0x3FFFFF) << 20)
                | (long) (sectionY & 0xFFFFF);
    }

    @Test
    void multiBlockChange736To751SingleSection() {
        byte[] payload = multiBlockChange736Payload(-2, 3,
                new int[][]{{1, 2, 65, STATE_736}, {15, 0, 66, STATE_736}});
        byte[] out = ChunkTranslator.translateMultiBlockChange736To751(payload);
        Reader reader = new Reader(out);
        assertEquals(sectionPos(-2, 4, 3), reader.readLong());
        assertFalse(reader.readBoolean()); // notTrustEdges（補完は false）
        assertEquals(2, reader.readVarInt());
        assertEquals(((long) STATE_751 << 12) | 0x121, reader.readVarLong());
        assertEquals(((long) STATE_751 << 12) | 0xF02, reader.readVarLong());
        assertEquals(0, reader.remaining());
    }

    @Test
    void multiBlockChange736To751ListSplitsSections() {
        // y=65（セクション4）・y=16（セクション1）・y=80（セクション5）の3件。
        // セクション昇順（1・4・5）に分かれる。
        byte[] payload = multiBlockChange736Payload(-2, 3,
                new int[][]{{1, 2, 65, STATE_736}, {3, 4, 16, STATE_736}, {0, 0, 80, STATE_736}});
        List<byte[]> list = ChunkTranslator.translateMultiBlockChange736To751List(payload);
        assertEquals(3, list.size());
        Reader first = new Reader(list.get(0));
        assertEquals(sectionPos(-2, 1, 3), first.readLong());
        assertFalse(first.readBoolean());
        assertEquals(1, first.readVarInt());
        assertEquals(((long) STATE_751 << 12) | 0x340, first.readVarLong());
        Reader second = new Reader(list.get(1));
        assertEquals(sectionPos(-2, 4, 3), second.readLong());
        assertFalse(second.readBoolean()); // notTrustEdges
        assertEquals(1, second.readVarInt());
        assertEquals(((long) STATE_751 << 12) | 0x121, second.readVarLong());
        Reader third = new Reader(list.get(2));
        assertEquals(sectionPos(-2, 5, 3), third.readLong());
        assertFalse(third.readBoolean()); // notTrustEdges
        assertEquals(1, third.readVarInt());
        assertEquals(((long) STATE_751 << 12) | 0x000, third.readVarLong());
        // 複数セクションのとき単一パケット版は null（List版を使うこと）。
        assertNull(ChunkTranslator.translateMultiBlockChange736To751(payload));
    }

    @Test
    void multiBlockChange736To751EmptyDrops() {
        byte[] payload = multiBlockChange736Payload(-2, 3, new int[][]{});
        assertTrue(ChunkTranslator.translateMultiBlockChange736To751List(payload).isEmpty());
        assertNull(ChunkTranslator.translateMultiBlockChange736To751(payload));
    }

    @Test
    void multiBlockChange751To736RoundTrips() {
        byte[] payload = multiBlockChange736Payload(-2, 3,
                new int[][]{{1, 2, 65, STATE_736}, {15, 0, 66, STATE_736}});
        byte[] as751 = ChunkTranslator.translateMultiBlockChange736To751(payload);
        assertArrayEquals(payload, ChunkTranslator.translateMultiBlockChange751To736(as751));
    }

    @Test
    void multiBlockChange751To736DropsNotTrustEdges() {
        // 751の notTrustEdges=true は736には無いため捨てる。
        Writer writer = new Writer();
        writer.writeLong(sectionPos(-2, 4, 3));
        writer.writeBoolean(true);
        writer.writeVarInt(1);
        writer.writeVarLong(((long) STATE_751 << 12) | 0x121);
        byte[] out = ChunkTranslator.translateMultiBlockChange751To736(writer.bytes());
        assertArrayEquals(
                multiBlockChange736Payload(-2, 3, new int[][]{{1, 2, 65, STATE_736}}), out);
    }

    // ------------------------------------------------------------------
    // Update Light
    // ------------------------------------------------------------------

    @Test
    void updateLight736To751IsIdentity() {
        // フィールド同一のため恒等写像（ペイロードはそのまま通る）。
        byte[] payload = new byte[]{0x01, 0x02, 0x03, 0x04, 0x05};
        assertArrayEquals(payload, ChunkTranslator.translateUpdateLight736To751(payload));
        assertArrayEquals(payload, ChunkTranslator.translateUpdateLight751To736(payload));
    }

    // ------------------------------------------------------------------
    // Edit Book
    // ------------------------------------------------------------------

    private static byte[] editBookPayload(int item) {
        Writer writer = new Writer();
        writeSlot(writer, item, 1);
        writer.writeBoolean(true); // signing
        writer.writeVarInt(0); // hand main hand
        return writer.bytes();
    }

    @Test
    void editBook736To751RemapsSlot() {
        byte[] out = ChunkTranslator.translateEditBook736To751(editBookPayload(ITEM_736));
        Reader reader = new Reader(out);
        assertArrayEquals(new int[]{ITEM_751, 1}, readSlot(reader));
        assertTrue(reader.readBoolean());
        assertEquals(0, reader.readVarInt());
        assertEquals(0, reader.remaining());
    }

    @Test
    void editBook751To736RoundTrips() {
        byte[] out = ChunkTranslator.translateEditBook751To736(editBookPayload(ITEM_751));
        assertArrayEquals(editBookPayload(ITEM_736), out);
    }
}
