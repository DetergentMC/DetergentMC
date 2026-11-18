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
import org.junit.jupiter.api.Test;

/**
 * 754⇔755のフィールド差分・値付け替え変換器のテスト（Stage B 統合時の残作業分）。
 * ChunkTranslator の754節に追加した変換器と、PlayTranslator の754節に追加した
 * Resource Pack Send 変換器を、往復バイト完全一致（754→755→754＝入力）と
 * 片方向の付け替え値で確かめる。
 * 使うIDは実測値（RegistryRemapper の表・実バニラ registries.json）：
 * アイテム 754:8→755:14（最初に違うもの。表を走査して実測）、
 * ブロック状態 754:70→755:71（最初に違うもの。同上）、
 * エンティティ falling_block 754:26→755:27（registries.json で実測）、
 * 音 754:831→755:1020（registries-1.16.5.json 831＝item.sweet_berries.pick_from_bush、
 * registries-1.17.json 1020＝block.sweet_berry_bush.pick_berries）、
 * 音の名前 754:item.sweet_berries.pick_from_bush→755:block.sweet_berry_bush.pick_berries
 * （同じく実測。改名はこの1件のみ）、パーティクル block 754:3→755:4。
 * 作らない変換器：Entity Effect（エフェクト 32件・同一順序のため恒等写像。
 * Stage A §11.1）、Open Window（メニュー 24件・同一順序のため恒等写像。
 * Stage A §11.1）。Update Light は変換器なし（未実装。落とす）。
 */
class ChunkTranslator754ValueTest {

    /** アイテムIDが実際に違うもの（754:8→755:14）。 */
    private static final int ITEM_754 = 8;
    private static final int ITEM_755 = 14;

    /** ブロック状態IDが実際に違うもの（754:70→755:71）。 */
    private static final int STATE_754 = 70;
    private static final int STATE_755 = 71;

    /** 音IDが実際に違うもの（754:831→755:1020）。 */
    private static final int SOUND_754 = 831;
    private static final int SOUND_755 = 1020;

    /** 音の名前（改名1件）。754名→755名。 */
    private static final String SOUND_NAME_754 = "item.sweet_berries.pick_from_bush";
    private static final String SOUND_NAME_755 = "block.sweet_berry_bush.pick_berries";

    private static byte[] slot754(int item) {
        Writer w = new Writer();
        w.writeBoolean(true);
        w.writeVarInt(item);
        w.writeByte(1);
        w.writeByte(0); // NBT無し
        return w.bytes();
    }

    private static void assertSlotItem755(Reader r, int expectedItem) {
        assertTrue(r.readBoolean());
        assertEquals(expectedItem, r.readVarInt());
        assertEquals(1, r.readByte());
        assertEquals(0, r.readByte());
    }

    // ------------------------------------------------------------------
    // Explosion
    // ------------------------------------------------------------------

    private static byte[] explosion754() {
        Writer w = new Writer();
        w.writeFloat(1.0f);
        w.writeFloat(2.0f);
        w.writeFloat(3.0f);
        w.writeFloat(4.0f);
        w.writeInt(1); // count i32
        w.writeByte(1);
        w.writeByte(2);
        w.writeByte(3);
        w.writeFloat(0.0f);
        w.writeFloat(0.0f);
        w.writeFloat(0.0f);
        return w.bytes();
    }

    @Test
    void explosion754To755ConvertsCountType() {
        byte[] out = ChunkTranslator.translateExplosion754To755(explosion754());
        Reader r = new Reader(out);
        assertEquals(1.0f, r.readFloat());
        assertEquals(2.0f, r.readFloat());
        assertEquals(3.0f, r.readFloat());
        assertEquals(4.0f, r.readFloat());
        assertEquals(1, r.readVarInt()); // count（i32→varint）
        assertEquals(1, r.readByte());
        assertEquals(2, r.readByte());
        assertEquals(3, r.readByte());
        assertEquals(0.0f, r.readFloat());
        assertEquals(0.0f, r.readFloat());
        assertEquals(0.0f, r.readFloat());
        assertEquals(0, r.remaining());
        assertArrayEquals(explosion754(),
                ChunkTranslator.translateExplosion755To754(out));
    }

    // ------------------------------------------------------------------
    // Entity Update Attributes
    // ------------------------------------------------------------------

    private static byte[] attributes754() {
        Writer w = new Writer();
        w.writeVarInt(7); // entityId
        w.writeInt(1); // properties count i32
        w.writeString("generic.max_health");
        w.writeDouble(20.0);
        w.writeVarInt(0); // modifiers 0件
        return w.bytes();
    }

    @Test
    void entityUpdateAttributes754To755ConvertsCountType() {
        byte[] out = ChunkTranslator.translateEntityUpdateAttributes754To755(attributes754());
        Reader r = new Reader(out);
        assertEquals(7, r.readVarInt());
        assertEquals(1, r.readVarInt()); // count（i32→varint）
        assertEquals("generic.max_health", r.readString());
        assertEquals(20.0, r.readDouble());
        assertEquals(0, r.readVarInt());
        assertEquals(0, r.remaining());
        assertArrayEquals(attributes754(),
                ChunkTranslator.translateEntityUpdateAttributes755To754(out));
    }

    // ------------------------------------------------------------------
    // Map
    // ------------------------------------------------------------------

    /** 754の Map（アイコン1件＋地図データ2x2。trackingPosition=true）。 */
    private static byte[] map754() {
        Writer w = new Writer();
        w.writeVarInt(0); // itemDamage
        w.writeByte(0); // scale
        w.writeBoolean(true); // trackingPosition
        w.writeBoolean(false); // locked
        w.writeVarInt(1); // icons 1件
        w.writeVarInt(0); // type
        w.writeByte(1); // x
        w.writeByte(2); // z
        w.writeUnsignedByte(3); // direction
        w.writeBoolean(true); // displayName あり
        w.writeString("Home");
        w.writeByte(2); // columns
        w.writeByte(2); // rows
        w.writeByte(0); // x
        w.writeByte(0); // y
        w.writeByteArray(new byte[]{1, 2, 3, 4}); // data
        return w.bytes();
    }

    @Test
    void map754To755DropsTrackingPositionAndOptionizesIcons() {
        byte[] out = ChunkTranslator.translateMap754To755(map754());
        Reader r = new Reader(out);
        assertEquals(0, r.readVarInt());
        assertEquals(0, r.readByte());
        assertFalse(r.readBoolean()); // locked（trackingPosition は無い）
        assertTrue(r.readBoolean()); // icons あり
        assertEquals(1, r.readVarInt());
        assertEquals(0, r.readVarInt());
        assertEquals(1, r.readByte());
        assertEquals(2, r.readByte());
        assertEquals(3, r.readUnsignedByte());
        assertTrue(r.readBoolean());
        assertEquals("Home", r.readString());
        assertEquals(2, r.readByte()); // columns
        assertEquals(2, r.readByte()); // rows
        assertEquals(0, r.readByte());
        assertEquals(0, r.readByte());
        assertArrayEquals(new byte[]{1, 2, 3, 4}, r.readByteArray());
        assertEquals(0, r.remaining());
        // 往復は完全一致（trackingPosition=true は補完値 true と同じため）。
        assertArrayEquals(map754(), ChunkTranslator.translateMap755To754(out));
    }

    @Test
    void map754To755WithNoIconsWritesAbsent() {
        Writer w = new Writer();
        w.writeVarInt(0);
        w.writeByte(0);
        w.writeBoolean(true);
        w.writeBoolean(false);
        w.writeVarInt(0); // icons 0件
        w.writeByte(0); // columns 0（データ無し）
        byte[] out = ChunkTranslator.translateMap754To755(w.bytes());
        Reader r = new Reader(out);
        assertEquals(0, r.readVarInt());
        assertEquals(0, r.readByte());
        assertFalse(r.readBoolean()); // locked
        assertFalse(r.readBoolean()); // icons 無し
        assertEquals(0, r.readByte()); // columns
        assertEquals(0, r.remaining());
    }

    // ------------------------------------------------------------------
    // Tags
    // ------------------------------------------------------------------

    /** 754の Tags（4固定グループ、各1タグ・各1エントリ）。 */
    private static byte[] tags754() {
        Writer w = new Writer();
        w.writeVarInt(1);
        w.writeString("minecraft:planks");
        w.writeVarInt(1);
        w.writeVarInt(1); // block 1
        w.writeVarInt(1);
        w.writeString("minecraft:planks");
        w.writeVarInt(1);
        w.writeVarInt(1); // item 1
        w.writeVarInt(1);
        w.writeString("minecraft:water");
        w.writeVarInt(1);
        w.writeVarInt(0); // fluid 0
        w.writeVarInt(1);
        w.writeString("minecraft:skeletons");
        w.writeVarInt(1);
        w.writeVarInt(0); // entity 0
        return w.bytes();
    }

    @Test
    void tags754To755BecomesFiveGroupsWithEmptyGameEvent() {
        byte[] out = ChunkTranslator.translateTags754To755(tags754());
        Reader r = new Reader(out);
        assertEquals(5, r.readVarInt());
        assertEquals("minecraft:block", r.readString());
        assertEquals(1, r.readVarInt());
        assertEquals("minecraft:planks", r.readString());
        assertEquals(1, r.readVarInt());
        assertEquals(RegistryRemapper.mapBlock754To755(1), r.readVarInt());
        assertEquals("minecraft:item", r.readString());
        assertEquals(1, r.readVarInt());
        assertEquals("minecraft:planks", r.readString());
        assertEquals(1, r.readVarInt());
        assertEquals(RegistryRemapper.mapItem754To755(1), r.readVarInt());
        assertEquals("minecraft:fluid", r.readString());
        assertEquals(1, r.readVarInt());
        assertEquals("minecraft:water", r.readString());
        assertEquals(1, r.readVarInt());
        assertEquals(0, r.readVarInt()); // fluid は同一順序のためそのまま
        assertEquals("minecraft:entity_type", r.readString());
        assertEquals(1, r.readVarInt());
        assertEquals("minecraft:skeletons", r.readString());
        assertEquals(1, r.readVarInt());
        assertEquals(RegistryRemapper.mapEntityType754To755(0), r.readVarInt());
        assertEquals("minecraft:game_event", r.readString());
        assertEquals(0, r.readVarInt()); // 空タグ列（754には無い）
        assertEquals(0, r.remaining());
        assertArrayEquals(tags754(), ChunkTranslator.translateTags755To754(out));
    }

    // ------------------------------------------------------------------
    // Resource Pack Send（PlayTranslator）
    // ------------------------------------------------------------------

    private static byte[] resourcePack754() {
        Writer w = new Writer();
        w.writeString("https://example.invalid/pack.zip");
        w.writeString("aa".repeat(20));
        return w.bytes();
    }

    @Test
    void resourcePack754To755AddsForcedFalseAndNoPrompt() {
        byte[] out = PlayTranslator.translateResourcePack754To755(resourcePack754());
        Reader r = new Reader(out);
        assertEquals("https://example.invalid/pack.zip", r.readString());
        assertEquals("aa".repeat(20), r.readString());
        assertFalse(r.readBoolean()); // forced=false を補う
        assertFalse(r.readBoolean()); // promptMessage 無し
        assertEquals(0, r.remaining());
        assertArrayEquals(resourcePack754(),
                PlayTranslator.translateResourcePack755To754(out));
    }

    @Test
    void resourcePack755To754DropsForcedAndPrompt() {
        Writer w = new Writer();
        w.writeString("https://example.invalid/pack.zip");
        w.writeString("aa".repeat(20));
        w.writeBoolean(true); // forced
        w.writeBoolean(true); // promptMessage あり
        w.writeString("{\"text\":\"play?\"}");
        byte[] out = PlayTranslator.translateResourcePack755To754(w.bytes());
        assertArrayEquals(resourcePack754(), out);
    }

    // ------------------------------------------------------------------
    // Set Cooldown
    // ------------------------------------------------------------------

    @Test
    void setCooldown754To755RemapsItem() {
        Writer w = new Writer();
        w.writeVarInt(ITEM_754);
        w.writeVarInt(20);
        byte[] out = ChunkTranslator.translateSetCooldown754To755(w.bytes());
        Reader r = new Reader(out);
        assertEquals(ITEM_755, r.readVarInt());
        assertEquals(20, r.readVarInt());
        assertEquals(0, r.remaining());
        Writer back = new Writer();
        back.writeVarInt(ITEM_754);
        back.writeVarInt(20);
        assertArrayEquals(back.bytes(),
                ChunkTranslator.translateSetCooldown755To754(out));
    }

    // ------------------------------------------------------------------
    // Entity Equipment
    // ------------------------------------------------------------------

    private static byte[] entityEquipment754(int item) {
        Writer w = new Writer();
        w.writeVarInt(7); // entityId
        w.writeUnsignedByte(0); // slot 0（終端）
        w.writeBytes(slot754(item));
        return w.bytes();
    }

    @Test
    void entityEquipment754To755RemapsSlotItem() {
        byte[] out = ChunkTranslator.translateEntityEquipment754To755(
                entityEquipment754(ITEM_754));
        Reader r = new Reader(out);
        assertEquals(7, r.readVarInt());
        assertEquals(0, r.readUnsignedByte());
        assertSlotItem755(r, ITEM_755);
        assertEquals(0, r.remaining());
        assertArrayEquals(entityEquipment754(ITEM_754),
                ChunkTranslator.translateEntityEquipment755To754(out));
    }

    // ------------------------------------------------------------------
    // Trade List
    // ------------------------------------------------------------------

    private static byte[] tradeList754(int item) {
        Writer w = new Writer();
        w.writeVarInt(0); // windowId
        w.writeUnsignedByte(1); // trades 1件
        w.writeBytes(slot754(item)); // input
        w.writeBytes(slot754(item)); // output
        w.writeBoolean(false); // inputItem2 無し
        w.writeBoolean(false); // tradeDisabled
        w.writeInt(0); // nbTradeUses
        w.writeInt(12); // maximumNbTradeUses
        w.writeInt(0); // xp
        w.writeInt(0); // specialPrice
        w.writeFloat(1.0f); // priceMultiplier
        w.writeInt(0); // demand
        w.writeVarInt(1); // villagerLevel
        w.writeVarInt(0); // experience
        w.writeBoolean(true); // isRegularVillager
        w.writeBoolean(true); // canRestock
        return w.bytes();
    }

    @Test
    void tradeList754To755RemapsSlotItems() {
        byte[] out = ChunkTranslator.translateTradeList754To755(tradeList754(ITEM_754));
        Reader r = new Reader(out);
        assertEquals(0, r.readVarInt());
        assertEquals(1, r.readUnsignedByte());
        // input／output スロットのアイテムIDが付け替わる。
        assertSlotItem755(r, ITEM_755);
        assertSlotItem755(r, ITEM_755);
        assertFalse(r.readBoolean()); // inputItem2 無し
        assertFalse(r.readBoolean());
        assertEquals(0, r.readInt());
        assertEquals(12, r.readInt());
        assertEquals(0, r.readInt());
        assertEquals(0, r.readInt());
        assertEquals(1.0f, r.readFloat());
        assertEquals(0, r.readInt());
        assertEquals(1, r.readVarInt());
        assertEquals(0, r.readVarInt());
        assertTrue(r.readBoolean());
        assertTrue(r.readBoolean());
        assertEquals(0, r.remaining());
        assertArrayEquals(tradeList754(ITEM_754),
                ChunkTranslator.translateTradeList755To754(out));
    }

    // ------------------------------------------------------------------
    // Sound Effect
    // ------------------------------------------------------------------

    private static byte[] soundEffect754(int sound) {
        Writer w = new Writer();
        w.writeVarInt(sound);
        w.writeVarInt(0); // category
        w.writeInt(0); // x
        w.writeInt(0); // y
        w.writeInt(0); // z
        w.writeFloat(1.0f);
        w.writeFloat(1.0f);
        return w.bytes();
    }

    @Test
    void soundEffect754To755RemapsSound() {
        byte[] out = ChunkTranslator.translateSoundEffect754To755(soundEffect754(SOUND_754));
        Reader r = new Reader(out);
        assertEquals(SOUND_755, r.readVarInt());
        assertEquals(0, r.readVarInt());
        assertEquals(0, r.readInt());
        assertEquals(0, r.readInt());
        assertEquals(0, r.readInt());
        assertEquals(1.0f, r.readFloat());
        assertEquals(1.0f, r.readFloat());
        assertEquals(0, r.remaining());
        assertArrayEquals(soundEffect754(SOUND_754),
                ChunkTranslator.translateSoundEffect755To754(out));
    }

    // ------------------------------------------------------------------
    // Entity Sound Effect
    // ------------------------------------------------------------------

    private static byte[] entitySoundEffect754(int sound) {
        Writer w = new Writer();
        w.writeVarInt(sound);
        w.writeVarInt(0); // category
        w.writeVarInt(7); // entityId
        w.writeFloat(1.0f);
        w.writeFloat(1.0f);
        return w.bytes();
    }

    @Test
    void entitySoundEffect754To755RemapsSound() {
        byte[] out = ChunkTranslator.translateEntitySoundEffect754To755(
                entitySoundEffect754(SOUND_754));
        Reader r = new Reader(out);
        assertEquals(SOUND_755, r.readVarInt());
        assertEquals(0, r.readVarInt());
        assertEquals(7, r.readVarInt());
        assertEquals(1.0f, r.readFloat());
        assertEquals(1.0f, r.readFloat());
        assertEquals(0, r.remaining());
        assertArrayEquals(entitySoundEffect754(SOUND_754),
                ChunkTranslator.translateEntitySoundEffect755To754(out));
    }

    // ------------------------------------------------------------------
    // Named Sound Effect
    // ------------------------------------------------------------------

    private static byte[] namedSoundEffect754(String name) {
        Writer w = new Writer();
        w.writeString(name);
        w.writeVarInt(0); // category
        w.writeInt(0);
        w.writeInt(0);
        w.writeInt(0);
        w.writeFloat(1.0f);
        w.writeFloat(1.0f);
        return w.bytes();
    }

    @Test
    void namedSoundEffect754To755RenamesSound() {
        byte[] out = ChunkTranslator.translateNamedSoundEffect754To755(
                namedSoundEffect754(SOUND_NAME_754));
        Reader r = new Reader(out);
        assertEquals(SOUND_NAME_755, r.readString());
        assertEquals(0, r.readVarInt());
        assertEquals(0, r.readInt());
        assertEquals(0, r.readInt());
        assertEquals(0, r.readInt());
        assertEquals(1.0f, r.readFloat());
        assertEquals(1.0f, r.readFloat());
        assertEquals(0, r.remaining());
        assertArrayEquals(namedSoundEffect754(SOUND_NAME_754),
                ChunkTranslator.translateNamedSoundEffect755To754(out));
    }

    // ------------------------------------------------------------------
    // Stop Sound
    // ------------------------------------------------------------------

    @Test
    void stopSound754To755RenamesSoundName() {
        Writer w = new Writer();
        w.writeByte(0x03); // source＋name あり
        w.writeVarInt(0); // source
        w.writeString(SOUND_NAME_754);
        byte[] out = ChunkTranslator.translateStopSound754To755(w.bytes());
        Reader r = new Reader(out);
        assertEquals(0x03, r.readByte());
        assertEquals(0, r.readVarInt());
        assertEquals(SOUND_NAME_755, r.readString());
        assertEquals(0, r.remaining());
        assertArrayEquals(w.bytes(), ChunkTranslator.translateStopSound755To754(out));
    }

    @Test
    void stopSound754To755WithSourceOnlyRoundTrips() {
        Writer w = new Writer();
        w.writeByte(0x01); // source のみ
        w.writeVarInt(0);
        byte[] out = ChunkTranslator.translateStopSound754To755(w.bytes());
        assertArrayEquals(w.bytes(), out);
        assertArrayEquals(w.bytes(), ChunkTranslator.translateStopSound755To754(out));
    }

    // ------------------------------------------------------------------
    // World Particles
    // ------------------------------------------------------------------

    /** 754の World Particles（block＋ブロック状態。データは末尾）。 */
    private static byte[] worldParticlesBlock754(int state) {
        Writer w = new Writer();
        w.writeInt(3); // block（754）
        w.writeBoolean(false); // longDistance
        w.writeDouble(0.0);
        w.writeDouble(0.0);
        w.writeDouble(0.0);
        w.writeFloat(0.0f);
        w.writeFloat(0.0f);
        w.writeFloat(0.0f);
        w.writeFloat(0.0f);
        w.writeInt(1); // count
        w.writeVarInt(state); // particleData（ブロック状態）
        return w.bytes();
    }

    @Test
    void worldParticlesBlock754To755RemapsParticleAndState() {
        byte[] out = ChunkTranslator.translateWorldParticles754To755(
                worldParticlesBlock754(STATE_754));
        Reader r = new Reader(out);
        assertEquals(4, r.readInt()); // block（754:3→755:4）
        assertFalse(r.readBoolean());
        assertEquals(0.0, r.readDouble());
        assertEquals(0.0, r.readDouble());
        assertEquals(0.0, r.readDouble());
        assertEquals(0.0f, r.readFloat());
        assertEquals(0.0f, r.readFloat());
        assertEquals(0.0f, r.readFloat());
        assertEquals(0.0f, r.readFloat());
        assertEquals(1, r.readInt());
        assertEquals(STATE_755, r.readVarInt());
        assertEquals(0, r.remaining());
        assertArrayEquals(worldParticlesBlock754(STATE_754),
                ChunkTranslator.translateWorldParticles755To754(out));
    }

    /** 754の World Particles（dust＋4 float）。 */
    private static byte[] worldParticlesDust754() {
        Writer w = new Writer();
        w.writeInt(14); // dust（754）
        w.writeBoolean(true);
        w.writeDouble(1.0);
        w.writeDouble(2.0);
        w.writeDouble(3.0);
        w.writeFloat(0.5f);
        w.writeFloat(0.5f);
        w.writeFloat(0.5f);
        w.writeFloat(1.0f);
        w.writeInt(2);
        w.writeFloat(1.0f);
        w.writeFloat(0.0f);
        w.writeFloat(0.0f);
        w.writeFloat(1.0f);
        return w.bytes();
    }

    @Test
    void worldParticlesDust754To755RemapsParticleId() {
        byte[] out = ChunkTranslator.translateWorldParticles754To755(worldParticlesDust754());
        Reader r = new Reader(out);
        assertEquals(15, r.readInt()); // dust（754:14→755:15）
        assertTrue(r.readBoolean());
        assertEquals(1.0, r.readDouble());
        assertEquals(2.0, r.readDouble());
        assertEquals(3.0, r.readDouble());
        assertEquals(0.5f, r.readFloat());
        assertEquals(0.5f, r.readFloat());
        assertEquals(0.5f, r.readFloat());
        assertEquals(1.0f, r.readFloat());
        assertEquals(2, r.readInt());
        assertEquals(1.0f, r.readFloat());
        assertEquals(0.0f, r.readFloat());
        assertEquals(0.0f, r.readFloat());
        assertEquals(1.0f, r.readFloat());
        assertEquals(0, r.remaining());
        assertArrayEquals(worldParticlesDust754(),
                ChunkTranslator.translateWorldParticles755To754(out));
    }

    /** 755の World Particles（dust_color_transition＋7 float）。754には無い。 */
    private static byte[] worldParticlesDustTransition755() {
        Writer w = new Writer();
        w.writeInt(16); // dust_color_transition（755）
        w.writeBoolean(false);
        w.writeDouble(0.0);
        w.writeDouble(0.0);
        w.writeDouble(0.0);
        w.writeFloat(0.0f);
        w.writeFloat(0.0f);
        w.writeFloat(0.0f);
        w.writeFloat(0.0f);
        w.writeInt(1);
        for (float f : new float[]{1.0f, 0.0f, 0.0f, 2.0f, 0.0f, 1.0f, 0.0f}) {
            w.writeFloat(f);
        }
        return w.bytes();
    }

    @Test
    void worldParticlesDustTransition755To754FallsBackToDust() {
        byte[] out = ChunkTranslator.translateWorldParticles755To754(
                worldParticlesDustTransition755());
        Reader r = new Reader(out);
        assertEquals(14, r.readInt()); // dust（754）
        assertFalse(r.readBoolean());
        assertEquals(0.0, r.readDouble());
        assertEquals(0.0, r.readDouble());
        assertEquals(0.0, r.readDouble());
        assertEquals(0.0f, r.readFloat());
        assertEquals(0.0f, r.readFloat());
        assertEquals(0.0f, r.readFloat());
        assertEquals(0.0f, r.readFloat());
        assertEquals(1, r.readInt());
        // 先頭4 float（fromRed／fromGreen／fromBlue／scale）だけ残る。
        assertEquals(1.0f, r.readFloat());
        assertEquals(0.0f, r.readFloat());
        assertEquals(0.0f, r.readFloat());
        assertEquals(2.0f, r.readFloat());
        assertEquals(0, r.remaining());
    }

    /** 755の World Particles（vibration）。754には無いため note へ写る。 */
    private static byte[] worldParticlesVibration755() {
        Writer w = new Writer();
        w.writeInt(37); // vibration（755）
        w.writeBoolean(false);
        w.writeDouble(0.0);
        w.writeDouble(0.0);
        w.writeDouble(0.0);
        w.writeFloat(0.0f);
        w.writeFloat(0.0f);
        w.writeFloat(0.0f);
        w.writeFloat(0.0f);
        w.writeInt(1);
        w.writePositionRaw(0L); // origin
        w.writeString("minecraft:block"); // positionType
        w.writePositionRaw(0L); // dest
        w.writeVarInt(20); // ticks
        return w.bytes();
    }

    @Test
    void worldParticlesVibration755To754FallsBackToNote() {
        byte[] out = ChunkTranslator.translateWorldParticles755To754(
                worldParticlesVibration755());
        Reader r = new Reader(out);
        assertEquals(40, r.readInt()); // note（754）
        assertFalse(r.readBoolean());
        assertEquals(0.0, r.readDouble());
        assertEquals(0.0, r.readDouble());
        assertEquals(0.0, r.readDouble());
        assertEquals(0.0f, r.readFloat());
        assertEquals(0.0f, r.readFloat());
        assertEquals(0.0f, r.readFloat());
        assertEquals(0.0f, r.readFloat());
        assertEquals(1, r.readInt());
        assertEquals(0, r.remaining()); // データは無い
    }

    // ------------------------------------------------------------------
    // Entity Metadata
    // ------------------------------------------------------------------

    /** 754の Entity Metadata（block state／slot／particle の3エントリ）。 */
    private static byte[] entityMetadata754() {
        Writer w = new Writer();
        w.writeVarInt(7); // entityId
        w.writeUnsignedByte(0);
        w.writeVarInt(13); // block state
        w.writeVarInt(STATE_754);
        w.writeUnsignedByte(1);
        w.writeVarInt(6); // slot
        w.writeBytes(slot754(ITEM_754));
        w.writeUnsignedByte(2);
        w.writeVarInt(15); // particle
        w.writeVarInt(3); // block
        w.writeVarInt(STATE_754);
        w.writeUnsignedByte(0xFF);
        return w.bytes();
    }

    @Test
    void entityMetadata754To755RemapsValues() {
        byte[] out = ChunkTranslator.translateEntityMetadata754To755(entityMetadata754());
        Reader r = new Reader(out);
        assertEquals(7, r.readVarInt());
        assertEquals(0, r.readUnsignedByte());
        assertEquals(13, r.readVarInt());
        assertEquals(STATE_755, r.readVarInt());
        assertEquals(1, r.readUnsignedByte());
        assertEquals(6, r.readVarInt());
        assertSlotItem755(r, ITEM_755);
        assertEquals(2, r.readUnsignedByte());
        assertEquals(15, r.readVarInt());
        assertEquals(4, r.readVarInt()); // block particle（754:3→755:4）
        assertEquals(STATE_755, r.readVarInt());
        assertEquals(0xFF, r.readUnsignedByte());
        assertEquals(0, r.remaining());
        assertArrayEquals(entityMetadata754(),
                ChunkTranslator.translateEntityMetadata755To754(out));
    }

    // ------------------------------------------------------------------
    // Spawn Entity
    // ------------------------------------------------------------------

    private static byte[] spawnEntity754(int type, int objectData) {
        Writer w = new Writer();
        w.writeVarInt(7); // entityId
        w.writeBytes(new byte[16]); // uuid
        w.writeVarInt(type);
        w.writeDouble(1.0);
        w.writeDouble(2.0);
        w.writeDouble(3.0);
        w.writeByte(0); // pitch
        w.writeByte(0); // yaw
        w.writeInt(objectData);
        w.writeShort(0);
        w.writeShort(0);
        w.writeShort(0);
        return w.bytes();
    }

    @Test
    void spawnEntityFallingBlock754To755RemapsTypeAndObjectData() {
        byte[] out = ChunkTranslator.translateSpawnEntity754To755(
                spawnEntity754(26, STATE_754));
        Reader r = new Reader(out);
        assertEquals(7, r.readVarInt());
        r.readBytes(16);
        assertEquals(27, r.readVarInt()); // falling_block（754:26→755:27）
        assertEquals(1.0, r.readDouble());
        assertEquals(2.0, r.readDouble());
        assertEquals(3.0, r.readDouble());
        assertEquals(0, r.readByte());
        assertEquals(0, r.readByte());
        assertEquals(STATE_755, r.readInt()); // objectData＝ブロック状態
        assertEquals(0, r.readShort());
        assertEquals(0, r.readShort());
        assertEquals(0, r.readShort());
        assertEquals(0, r.remaining());
        assertArrayEquals(spawnEntity754(26, STATE_754),
                ChunkTranslator.translateSpawnEntity755To754(out));
    }

    @Test
    void spawnEntityNonFallingBlock754To755KeepsObjectData() {
        // falling_block 以外の objectData はそのまま（種別IDだけ変換）。
        byte[] out = ChunkTranslator.translateSpawnEntity754To755(spawnEntity754(3, 99));
        Reader r = new Reader(out);
        assertEquals(7, r.readVarInt());
        r.readBytes(16);
        assertEquals(RegistryRemapper.mapEntityType754To755(3), r.readVarInt());
        assertEquals(1.0, r.readDouble());
        assertEquals(2.0, r.readDouble());
        assertEquals(3.0, r.readDouble());
        assertEquals(0, r.readByte());
        assertEquals(0, r.readByte());
        assertEquals(99, r.readInt());
        assertEquals(0, r.readShort());
        assertEquals(0, r.readShort());
        assertEquals(0, r.readShort());
        assertEquals(0, r.remaining());
    }

    // ------------------------------------------------------------------
    // Block Change
    // ------------------------------------------------------------------

    @Test
    void blockChange754To755RemapsState() {
        Writer w = new Writer();
        w.writePositionRaw(0L);
        w.writeVarInt(STATE_754);
        byte[] out = ChunkTranslator.translateBlockChange754To755(w.bytes());
        Reader r = new Reader(out);
        assertEquals(0L, r.readPositionRaw());
        assertEquals(STATE_755, r.readVarInt());
        assertEquals(0, r.remaining());
        assertArrayEquals(w.bytes(), ChunkTranslator.translateBlockChange755To754(out));
    }

    // ------------------------------------------------------------------
    // Multi Block Change
    // ------------------------------------------------------------------

    private static byte[] multiBlockChange754(int state) {
        Writer w = new Writer();
        w.writeLong(0L); // chunkCoordinates
        w.writeBoolean(false); // notTrustEdges
        w.writeVarInt(1);
        w.writeVarLong(((long) state << 12) | 5); // レコード（状態｜局所座標）
        return w.bytes();
    }

    @Test
    void multiBlockChange754To755RemapsStateKeepsLocal() {
        byte[] out = ChunkTranslator.translateMultiBlockChange754To755(
                multiBlockChange754(STATE_754));
        Reader r = new Reader(out);
        assertEquals(0L, r.readLong());
        assertFalse(r.readBoolean());
        assertEquals(1, r.readVarInt());
        long record = r.readVarLong();
        assertEquals(STATE_755, (int) (record >>> 12));
        assertEquals(5, (int) (record & 0xFFF));
        assertEquals(0, r.remaining());
        assertArrayEquals(multiBlockChange754(STATE_754),
                ChunkTranslator.translateMultiBlockChange755To754(out));
    }

    // ------------------------------------------------------------------
    // Block Entity Data
    // ------------------------------------------------------------------

    private static byte[] blockEntityData754(int type) {
        Writer w = new Writer();
        w.writePositionRaw(0L);
        w.writeUnsignedByte(type);
        w.writeByte(0); // NBT無し
        return w.bytes();
    }

    @Test
    void blockEntityData754To755RoundTrips() {
        byte[] out = ChunkTranslator.translateBlockEntityData754To755(blockEntityData754(0));
        Reader r = new Reader(out);
        assertEquals(0L, r.readPositionRaw());
        assertEquals(0, r.readUnsignedByte());
        assertEquals(0, r.readByte());
        assertEquals(0, r.remaining());
        assertArrayEquals(blockEntityData754(0),
                ChunkTranslator.translateBlockEntityData755To754(out));
    }

    @Test
    void blockEntityData755To754DropsSculkSensor() {
        // sculk_sensor（755:33）は1.16.5に無いためパケットごと落とす。
        assertNull(ChunkTranslator.translateBlockEntityData755To754(blockEntityData754(33)));
    }

    // ------------------------------------------------------------------
    // Entity Destroy（757 <=> 759、756 <=> 757）
    // ------------------------------------------------------------------

    @Test
    void entityDestroy757To759WrapsSingleIntoArray() {
        Writer w = new Writer();
        w.writeVarInt(42);
        byte[] out = ChunkTranslator.translateEntityDestroy757To759(w.bytes());
        Reader r = new Reader(out);
        assertEquals(1, r.readVarInt()); // count
        assertEquals(42, r.readVarInt());
        assertEquals(0, r.remaining());
        assertArrayEquals(w.bytes(),
                ChunkTranslator.translateEntityDestroy759To757(out));
    }

    @Test
    void entityDestroy759To757DropsNonSingleCounts() {
        Writer zero = new Writer();
        zero.writeVarInt(0);
        assertNull(ChunkTranslator.translateEntityDestroy759To757(zero.bytes()));
        Writer two = new Writer();
        two.writeVarInt(2);
        two.writeVarInt(42);
        two.writeVarInt(43);
        assertNull(ChunkTranslator.translateEntityDestroy759To757(two.bytes()));
    }

    @Test
    void entityDestroy756To757UnwrapsSingleFromArray() {
        Writer w = new Writer();
        w.writeVarInt(1);
        w.writeVarInt(42);
        byte[] out = ChunkTranslator.translateEntityDestroy756To757(w.bytes());
        Reader r = new Reader(out);
        assertEquals(42, r.readVarInt());
        assertEquals(0, r.remaining());
        assertArrayEquals(w.bytes(),
                ChunkTranslator.translateEntityDestroy757To756(out));
    }

    @Test
    void entityDestroy756To757DropsNonSingleCounts() {
        Writer zero = new Writer();
        zero.writeVarInt(0);
        assertNull(ChunkTranslator.translateEntityDestroy756To757(zero.bytes()));
        Writer two = new Writer();
        two.writeVarInt(2);
        two.writeVarInt(42);
        two.writeVarInt(43);
        assertNull(ChunkTranslator.translateEntityDestroy756To757(two.bytes()));
    }
}
