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
 * {@link ChunkTranslator} の762（1.19.4）⇔763（1.20／1.20.1）向け変換テスト。
 * 763形式のペイロードを合成して762形式に変換し、読み直して中身を確認する。
 *
 * <p>762と763のペイロード差（minecraft-data protocol.json で確認）：
 * Chunk Data／Update Light の trustEdges、Multi Block Change の
 * suppressLightUpdates、Open Sign／Update Sign の isFrontText のみ。
 * パケットIDは0件変更。NBT は両方とも名前付きのためそのまま写す。
 * エンティティメタデータの variant 系は 762・763 で形式が同一のため
 * 落とさない（765→763 では落としている）。スロットは両方とも
 * bool＋アイテムID＋個数＋名前付き NBT。
 */
class ChunkTranslator762Test {

    // ---- ヘルパー ----

    /** 高さマップ（名前付き NBT のコンパウンド）。 */
    private static Tag heightmaps() {
        return new Tag.CompoundTag(Map.of(
                "MOTION_BLOCKING", new Tag.LongArrayTag(new long[37]),
                "WORLD_SURFACE", new Tag.LongArrayTag(new long[37])));
    }

    /** スロット1個（762／763形式：bool＋アイテムID＋個数＋名前付き NBT 無し）。 */
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

    /** 間接パレットのセクション1個（ブロック＋バイオーム）。762／763形式（Data Array に長さ varint あり）。 */
    private static byte[] indirectSection(int[] blockPalette, int[] biomePalette) {
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
        writer.writeVarInt(2); // Data Array：64エントリ×2bit＝2 long
        for (int i = 0; i < 2; i++) {
            writer.writeLong(0L);
        }
        return writer.bytes();
    }

    /** セクションのブロックパレットだけ読み、ブロック状態IDの配列を返す。 */
    private static int[] readBlockPalette(byte[] sections) {
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

    /** セクションのバイオームパレットを読む。 */
    private static int[] readBiomePalette(byte[] sections) {
        Reader section = new Reader(sections);
        section.readShort();
        section.readUnsignedByte(); // ブロック BPE
        int paletteSize = section.readVarInt();
        for (int i = 0; i < paletteSize; i++) {
            section.readVarInt();
        }
        int blockDataLength = section.readVarInt();
        for (int i = 0; i < blockDataLength; i++) {
            section.readLong();
        }
        section.readUnsignedByte();
        int biomePaletteSize = section.readVarInt();
        int[] palette = new int[biomePaletteSize];
        for (int i = 0; i < biomePaletteSize; i++) {
            palette[i] = section.readVarInt();
        }
        return palette;
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

    /** Spawn Entity ペイロードの種別と objectData を読む。 */
    private static int[] readSpawnEntityTypeAndData(byte[] payload) {
        Reader reader = new Reader(payload);
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

    /** チャンク Data ペイロード（763形式）。セクションは1個、ブロックエンティティは引数通り。 */
    private static byte[] chunkDataPayload763(int chunkX, int chunkZ,
            int[] blockPalette, int[] biomePalette, int[][] blockEntities) {
        Writer payload = new Writer();
        payload.writeInt(chunkX);
        payload.writeInt(chunkZ);
        payload.writeNamedNbt("", heightmaps());
        payload.writeByteArray(indirectSection(blockPalette, biomePalette));
        payload.writeVarInt(blockEntities.length);
        for (int[] be : blockEntities) {
            payload.writeUnsignedByte(be[0]);
            payload.writeShort(be[1]);
            payload.writeVarInt(be[2]);
            payload.writeByte(0); // NBT 無し
        }
        payload.writeBytes(new byte[]{0x0A, 0x0B, 0x0C}); // 光データ（そのまま写る想定）
        return payload.bytes();
    }

    /** チャンク Data ペイロード（762形式）。763形式に trustEdges を足したもの。 */
    private static byte[] chunkDataPayload762(int chunkX, int chunkZ,
            int[] blockPalette, int[] biomePalette, int[][] blockEntities) {
        Writer payload = new Writer();
        payload.writeInt(chunkX);
        payload.writeInt(chunkZ);
        payload.writeNamedNbt("", heightmaps());
        payload.writeByteArray(indirectSection(blockPalette, biomePalette));
        payload.writeVarInt(blockEntities.length);
        for (int[] be : blockEntities) {
            payload.writeUnsignedByte(be[0]);
            payload.writeShort(be[1]);
            payload.writeVarInt(be[2]);
            payload.writeByte(0); // NBT 無し
        }
        payload.writeBoolean(true); // trustEdges（762のみ）
        payload.writeBytes(new byte[]{0x0A, 0x0B, 0x0C}); // 光データ
        return payload.bytes();
    }

    // ---- Chunk Data ----

    @Test
    void chunkData763To762RemapsBlockStatesBiomesAndDropsBlockEntity() {
        byte[] translated = ChunkTranslator.translateChunkData763To762(
                chunkDataPayload763(3, -2,
                        new int[]{0, 1, 24128, 12357}, new int[]{39, 5},
                        new int[][]{{0x11, 64, 1}, {0x22, 65, 35}}));
        Reader reader = new Reader(translated);
        assertEquals(3, reader.readInt());
        assertEquals(-2, reader.readInt());
        assertTrue(reader.readNamedNbt().tag() instanceof Tag.CompoundTag);
        byte[] sections = reader.readByteArray();
        assertArrayEquals(new int[]{0, 1, 23718, 4274}, readBlockPalette(sections));
        assertArrayEquals(new int[]{38, 31}, readBiomePalette(sections));
        // ブロックエンティティはチェスト（763 type 1→762 type 1）と
        // calibrated_sculk_sensor（763 type 35→762 sculk_sensor type 34）が残る。
        assertEquals(2, reader.readVarInt());
        assertEquals(0x11, reader.readUnsignedByte());
        assertEquals(64, reader.readShort());
        assertEquals(1, reader.readVarInt());
        assertEquals(0, reader.readByte()); // NBT 無し
        assertEquals(0x22, reader.readUnsignedByte());
        assertEquals(65, reader.readShort());
        assertEquals(34, reader.readVarInt()); // sculk_sensor（762）
        assertEquals(0, reader.readByte()); // NBT 無し
        // trustEdges（true）が光データの前に挿入される。
        assertTrue(reader.readBoolean());
        assertArrayEquals(new byte[]{0x0A, 0x0B, 0x0C}, reader.rest());
    }

    @Test
    void chunkData762To763RemapsBackAndDropsTrustEdges() {
        byte[] translated = ChunkTranslator.translateChunkData762To763(
                chunkDataPayload762(3, -2,
                        new int[]{0, 1, 23718, 12352}, new int[]{38, 31},
                        new int[][]{{0x11, 64, 1}, {0x22, 65, 38}}));
        Reader reader = new Reader(translated);
        assertEquals(3, reader.readInt());
        assertEquals(-2, reader.readInt());
        assertTrue(reader.readNamedNbt().tag() instanceof Tag.CompoundTag);
        byte[] sections = reader.readByteArray();
        assertArrayEquals(new int[]{0, 1, 24128, 12355}, readBlockPalette(sections));
        assertArrayEquals(new int[]{39, 32}, readBiomePalette(sections));
        // suspicious_sand（762 type 38）は brushable_block（763 type 39）へ。
        assertEquals(2, reader.readVarInt());
        assertEquals(0x11, reader.readUnsignedByte());
        assertEquals(64, reader.readShort());
        assertEquals(1, reader.readVarInt());
        assertEquals(0, reader.readByte());
        assertEquals(0x22, reader.readUnsignedByte());
        assertEquals(65, reader.readShort());
        assertEquals(39, reader.readVarInt());
        assertEquals(0, reader.readByte());
        // trustEdges は落とされ、光データがそのまま続く。
        assertArrayEquals(new byte[]{0x0A, 0x0B, 0x0C}, reader.rest());
    }

    @Test
    void chunkDataBrokenPayloadPassesThrough() {
        byte[] broken = {0x00, 0x01, 0x02}; // 短すぎて読めない
        assertArrayEquals(broken, ChunkTranslator.translateChunkData763To762(broken));
        assertArrayEquals(broken, ChunkTranslator.translateChunkData762To763(broken));
    }

    // ---- Block Change / Multi Block Change / Block Entity Data / Update Light ----

    @Test
    void blockChange() {
        Writer payload = new Writer();
        payload.writePositionRaw(0x123456789AL);
        payload.writeVarInt(24128); // decorated_pot（763）
        byte[] translated = ChunkTranslator.translateBlockChange763To762(payload.bytes());
        Reader reader = new Reader(translated);
        assertEquals(0x123456789AL, reader.readPositionRaw());
        assertEquals(23718, reader.readVarInt());
        assertEquals(0, reader.remaining());

        Writer payload762 = new Writer();
        payload762.writePositionRaw(0x123456789AL);
        payload762.writeVarInt(23718);
        byte[] back = ChunkTranslator.translateBlockChange762To763(payload762.bytes());
        Reader reader762 = new Reader(back);
        assertEquals(0x123456789AL, reader762.readPositionRaw());
        assertEquals(24128, reader762.readVarInt());
        assertEquals(0, reader762.remaining());
    }

    @Test
    void multiBlockChangeInsertsAndDropsSuppressLightUpdates() {
        Writer payload = new Writer();
        payload.writeLong(0x1122334455667788L);
        payload.writeVarInt(2);
        payload.writeVarInt((24128 << 12) | 0x123); // decorated_pot
        payload.writeVarInt((12357 << 12) | 0x456); // pitcher_crop → wheat
        byte[] translated = ChunkTranslator.translateMultiBlockChange763To762(payload.bytes());
        Reader reader = new Reader(translated);
        assertEquals(0x1122334455667788L, reader.readLong());
        assertEquals(false, reader.readBoolean()); // suppressLightUpdates（762のみ）
        assertEquals(2, reader.readVarInt());
        assertEquals((23718 << 12) | 0x123, reader.readVarInt());
        assertEquals((4274 << 12) | 0x456, reader.readVarInt());
        assertEquals(0, reader.remaining());

        byte[] back = ChunkTranslator.translateMultiBlockChange762To763(translated);
        Reader reader762 = new Reader(back);
        assertEquals(0x1122334455667788L, reader762.readLong());
        assertEquals(2, reader762.readVarInt());
        assertEquals((24128 << 12) | 0x123, reader762.readVarInt());
        assertEquals((4278 << 12) | 0x456, reader762.readVarInt()); // wheat（763）
        assertEquals(0, reader762.remaining());
    }

    @Test
    void blockEntityData() {
        Writer payload = new Writer();
        payload.writePositionRaw(0x123456789AL);
        payload.writeVarInt(36); // sculk_catalyst（763）
        payload.writeByte(0); // NBT 無し
        byte[] translated = ChunkTranslator.translateBlockEntityData763To762(payload.bytes());
        Reader reader = new Reader(translated);
        assertEquals(0x123456789AL, reader.readPositionRaw());
        assertEquals(35, reader.readVarInt()); // sculk_catalyst（762）
        assertEquals(0, reader.readByte());
        assertEquals(0, reader.remaining());

        // calibrated_sculk_sensor（763）は sculk_sensor（762）へ写す（落とさない）。
        Writer calibrated = new Writer();
        calibrated.writePositionRaw(0x123456789AL);
        calibrated.writeVarInt(35); // calibrated_sculk_sensor（763）
        calibrated.writeByte(0);
        byte[] calibratedOut = ChunkTranslator.translateBlockEntityData763To762(calibrated.bytes());
        Reader calibratedReader = new Reader(calibratedOut);
        assertEquals(0x123456789AL, calibratedReader.readPositionRaw());
        assertEquals(34, calibratedReader.readVarInt()); // sculk_sensor（762）
        assertEquals(0, calibratedReader.readByte());
        assertEquals(0, calibratedReader.remaining());

        // 範囲外の種別は null（パケットごと落とす）。
        Writer unknown = new Writer();
        unknown.writePositionRaw(0x123456789AL);
        unknown.writeVarInt(99999);
        unknown.writeByte(0);
        assertNull(ChunkTranslator.translateBlockEntityData763To762(unknown.bytes()));

        // 逆向き：suspicious_sand（762=38）は brushable_block（763=39）へ。
        Writer payload762 = new Writer();
        payload762.writePositionRaw(0x123456789AL);
        payload762.writeVarInt(38);
        payload762.writeByte(0);
        byte[] back = ChunkTranslator.translateBlockEntityData762To763(payload762.bytes());
        Reader reader762 = new Reader(back);
        assertEquals(0x123456789AL, reader762.readPositionRaw());
        assertEquals(39, reader762.readVarInt());
        assertEquals(0, reader762.readByte()); // NBT 無し
        assertEquals(0, reader762.remaining());
    }

    @Test
    void updateLightInsertsAndDropsTrustEdges() {
        Writer payload = new Writer();
        payload.writeVarInt(3);
        payload.writeVarInt(-2);
        payload.writeBytes(new byte[]{0x01, 0x02, 0x03});
        byte[] translated = ChunkTranslator.translateUpdateLight763To762(payload.bytes());
        Reader reader = new Reader(translated);
        assertEquals(3, reader.readVarInt());
        assertEquals(-2, reader.readVarInt());
        assertTrue(reader.readBoolean()); // trustEdges（762のみ）
        assertArrayEquals(new byte[]{0x01, 0x02, 0x03}, reader.rest());

        byte[] back = ChunkTranslator.translateUpdateLight762To763(translated);
        Reader reader762 = new Reader(back);
        assertEquals(3, reader762.readVarInt());
        assertEquals(-2, reader762.readVarInt());
        assertArrayEquals(new byte[]{0x01, 0x02, 0x03}, reader762.rest());
    }

    // ---- スロット・ウィンドウ ----

    @Test
    void setSlot() {
        Writer payload = new Writer();
        payload.writeByte(0); // windowId
        payload.writeVarInt(0); // stateId
        payload.writeShort(3); // slot
        writeSlot(payload, 1236, 1); // archer_pottery_sherd（763）
        byte[] translated = ChunkTranslator.translateSetSlot763To762(payload.bytes());
        Reader reader = new Reader(translated);
        assertEquals(0, reader.readByte());
        assertEquals(0, reader.readVarInt());
        assertEquals(3, reader.readShort());
        assertArrayEquals(new int[]{1224, 1}, readSlotHead(reader)); // pottery_shard_archer（762）
        assertEquals(0, reader.remaining());

        Writer payload762 = new Writer();
        payload762.writeByte(0);
        payload762.writeVarInt(0);
        payload762.writeShort(3);
        writeSlot(payload762, 1224, 1);
        byte[] back = ChunkTranslator.translateSetSlot762To763(payload762.bytes());
        Reader reader762 = new Reader(back);
        assertEquals(0, reader762.readByte());
        assertEquals(0, reader762.readVarInt());
        assertEquals(3, reader762.readShort());
        assertArrayEquals(new int[]{1236, 1}, readSlotHead(reader762));
        assertEquals(0, reader762.remaining());
    }

    @Test
    void setSlotRelicBecomesCat() {
        Writer payload = new Writer();
        payload.writeByte(0);
        payload.writeVarInt(0);
        payload.writeShort(3);
        writeSlot(payload, 1135, 1); // music_disc_relic（763にしか無い）
        byte[] translated = ChunkTranslator.translateSetSlot763To762(payload.bytes());
        Reader reader = new Reader(translated);
        assertEquals(0, reader.readByte());
        assertEquals(0, reader.readVarInt());
        assertEquals(3, reader.readShort());
        assertArrayEquals(new int[]{1118, 1}, readSlotHead(reader)); // music_disc_cat に代替
        assertEquals(0, reader.remaining());
    }

    @Test
    void windowItems() {
        Writer payload = new Writer();
        payload.writeUnsignedByte(1); // windowId
        payload.writeVarInt(5); // stateId
        payload.writeVarInt(2); // count
        writeSlot(payload, 1236, 1);
        writeSlot(payload, 1135, 2);
        writeSlot(payload, 1, 3); // carried
        byte[] translated = ChunkTranslator.translateWindowItems763To762(payload.bytes());
        Reader reader = new Reader(translated);
        assertEquals(1, reader.readUnsignedByte());
        assertEquals(5, reader.readVarInt());
        assertEquals(2, reader.readVarInt());
        assertArrayEquals(new int[]{1224, 1}, readSlotHead(reader));
        assertArrayEquals(new int[]{1118, 2}, readSlotHead(reader)); // relic→cat
        assertArrayEquals(new int[]{1, 3}, readSlotHead(reader));
        assertEquals(0, reader.remaining());
    }

    @Test
    void setCooldown() {
        Writer payload = new Writer();
        payload.writeVarInt(1135); // music_disc_relic（763）
        payload.writeVarInt(20);
        byte[] translated = ChunkTranslator.translateSetCooldown763To762(payload.bytes());
        Reader reader = new Reader(translated);
        assertEquals(1118, reader.readVarInt()); // music_disc_cat に代替
        assertEquals(20, reader.readVarInt());
        assertEquals(0, reader.remaining());
    }

    @Test
    void entityEquipment() {
        Writer payload = new Writer();
        payload.writeVarInt(7); // entityId
        payload.writeUnsignedByte(0); // slot byte（終端）
        writeSlot(payload, 1236, 1);
        byte[] translated = ChunkTranslator.translateEntityEquipment763To762(payload.bytes());
        Reader reader = new Reader(translated);
        assertEquals(7, reader.readVarInt());
        assertEquals(0, reader.readUnsignedByte());
        assertArrayEquals(new int[]{1224, 1}, readSlotHead(reader));
        assertEquals(0, reader.remaining());
    }

    @Test
    void openWindow() {
        Writer payload = new Writer();
        payload.writeVarInt(1); // windowId
        payload.writeVarInt(20); // smithing（763）
        payload.writeString("{\"text\":\"Smithing\"}");
        byte[] translated = ChunkTranslator.translateOpenWindow763To762(payload.bytes());
        Reader reader = new Reader(translated);
        assertEquals(1, reader.readVarInt());
        assertEquals(21, reader.readVarInt()); // smithing（762）
        assertEquals("{\"text\":\"Smithing\"}", reader.readString());
        assertEquals(0, reader.remaining());

        Writer payload762 = new Writer();
        payload762.writeVarInt(1);
        payload762.writeVarInt(20); // legacy_smithing（762）
        payload762.writeString("{\"text\":\"Smithing\"}");
        byte[] back = ChunkTranslator.translateOpenWindow762To763(payload762.bytes());
        Reader reader762 = new Reader(back);
        assertEquals(1, reader762.readVarInt());
        assertEquals(20, reader762.readVarInt()); // smithing（763）
        assertEquals("{\"text\":\"Smithing\"}", reader762.readString());
        assertEquals(0, reader762.remaining());
    }

    @Test
    void blockAction() {
        Writer payload = new Writer();
        payload.writePositionRaw(0x123456789AL);
        payload.writeUnsignedByte(1);
        payload.writeUnsignedByte(2);
        payload.writeVarInt(914); // calibrated_sculk_sensor（763）
        byte[] translated = ChunkTranslator.translateBlockAction763To762(payload.bytes());
        Reader reader = new Reader(translated);
        assertEquals(0x123456789AL, reader.readPositionRaw());
        assertEquals(1, reader.readUnsignedByte());
        assertEquals(2, reader.readUnsignedByte());
        assertEquals(909, reader.readVarInt()); // sculk_sensor に代替
        assertEquals(0, reader.remaining());

        Writer payload762 = new Writer();
        payload762.writePositionRaw(0x123456789AL);
        payload762.writeUnsignedByte(1);
        payload762.writeUnsignedByte(2);
        payload762.writeVarInt(997); // decorated_pot（762）
        byte[] back = ChunkTranslator.translateBlockAction762To763(payload762.bytes());
        Reader reader762 = new Reader(back);
        assertEquals(0x123456789AL, reader762.readPositionRaw());
        assertEquals(1, reader762.readUnsignedByte());
        assertEquals(2, reader762.readUnsignedByte());
        assertEquals(1002, reader762.readVarInt()); // decorated_pot（763）
        assertEquals(0, reader762.remaining());
    }

    // ---- エンティティ・音・パーティクル・エフェクト ----

    @Test
    void spawnEntity() {
        assertArrayEquals(new int[]{72, 0},
                readSpawnEntityTypeAndData(
                        ChunkTranslator.translateSpawnEntity763To762(spawnEntityPacket(72, 0))));
        // falling_block の objectData はブロック状態IDなので一緒に変換する。
        assertArrayEquals(new int[]{36, 23718},
                readSpawnEntityTypeAndData(
                        ChunkTranslator.translateSpawnEntity763To762(
                                spawnEntityPacket(36, 24128))));
        assertArrayEquals(new int[]{36, 24128},
                readSpawnEntityTypeAndData(
                        ChunkTranslator.translateSpawnEntity762To763(
                                spawnEntityPacket(36, 23718))));
        // 763に無い種別は無い（エンティティは124件とも同一並び）。
        assertArrayEquals(new int[]{90, 0},
                readSpawnEntityTypeAndData(
                        ChunkTranslator.translateSpawnEntity763To762(spawnEntityPacket(90, 0))));
    }

    @Test
    void entityMetadataSlotAndBlockStateAndVariantPassThrough() {
        Writer writer = new Writer();
        writer.writeVarInt(7); // entityId
        writer.writeUnsignedByte(0); // index 0
        writer.writeVarInt(7); // item_stack（スロット）
        writeSlot(writer, 1236, 1);
        writer.writeUnsignedByte(1); // index 1
        writer.writeVarInt(14); // block_state
        writer.writeVarInt(24128);
        writer.writeUnsignedByte(2); // index 2
        writer.writeVarInt(22); // frog_variant（762・763とも同じ番号）
        writer.writeVarInt(3);
        writer.writeUnsignedByte(3); // index 3
        writer.writeVarInt(24); // painting_variant
        writer.writeVarInt(5);
        writer.writeUnsignedByte(0xFF); // 終端
        byte[] out = ChunkTranslator.translateEntityMetadata763To762(writer.bytes());
        Reader reader = new Reader(out);
        assertEquals(7, reader.readVarInt());
        assertEquals(0, reader.readUnsignedByte());
        assertEquals(7, reader.readVarInt());
        assertArrayEquals(new int[]{1224, 1}, readSlotHead(reader));
        assertEquals(1, reader.readUnsignedByte());
        assertEquals(14, reader.readVarInt());
        assertEquals(23718, reader.readVarInt());
        // variant 系は落とさずそのまま通る（765→763 との違い）。
        assertEquals(2, reader.readUnsignedByte());
        assertEquals(22, reader.readVarInt());
        assertEquals(3, reader.readVarInt());
        assertEquals(3, reader.readUnsignedByte());
        assertEquals(24, reader.readVarInt());
        assertEquals(5, reader.readVarInt());
        assertEquals(0xFF, reader.readUnsignedByte());
        assertEquals(0, reader.remaining());
    }

    @Test
    void entityMetadataParticle() {
        Writer writer = new Writer();
        writer.writeVarInt(7); // entityId
        writer.writeUnsignedByte(0); // index 0
        writer.writeVarInt(17); // particle
        writer.writeVarInt(31); // sculk_charge（763）
        writer.writeFloat(1.5f); // 回転
        writer.writeUnsignedByte(0xFF); // 終端
        byte[] out = ChunkTranslator.translateEntityMetadata763To762(writer.bytes());
        Reader reader = new Reader(out);
        assertEquals(7, reader.readVarInt());
        assertEquals(0, reader.readUnsignedByte());
        assertEquals(17, reader.readVarInt());
        assertEquals(33, reader.readVarInt()); // sculk_charge（762）
        assertEquals(1.5f, reader.readFloat());
        assertEquals(0xFF, reader.readUnsignedByte());
        assertEquals(0, reader.remaining());
    }

    @Test
    void soundEffect() {
        // 数値 holder：763 の block.stone.break（1264）は holder 1265 として来る。
        Writer writer = new Writer();
        writer.writeVarInt(1265); // holder = id + 1
        writer.writeVarInt(1); // source
        writer.writeInt(8);
        writer.writeInt(64);
        writer.writeInt(8);
        writer.writeFloat(1.0f);
        writer.writeFloat(1.0f);
        writer.writeLong(12345L);
        byte[] out = ChunkTranslator.translateSoundEffect763To762(writer.bytes());
        Reader reader = new Reader(out);
        assertEquals(1246, reader.readVarInt()); // 762 の block.stone.break（1245）+ 1
        assertEquals(1, reader.readVarInt());
        assertEquals(8, reader.readInt());
        assertEquals(64, reader.readInt());
        assertEquals(8, reader.readInt());
        assertEquals(1.0f, reader.readFloat());
        assertEquals(1.0f, reader.readFloat());
        assertEquals(12345L, reader.readLong());
        assertEquals(0, reader.remaining());

        // 文字列 holder：music_disc.relic は music_disc.cat に代替。
        Writer named = new Writer();
        named.writeVarInt(0); // holder = 文字列
        named.writeString("music_disc.relic");
        named.writeBoolean(false); // hasRange なし
        named.writeVarInt(1);
        named.writeInt(8);
        named.writeInt(64);
        named.writeInt(8);
        named.writeFloat(1.0f);
        named.writeFloat(1.0f);
        named.writeLong(12345L);
        byte[] out2 = ChunkTranslator.translateSoundEffect763To762(named.bytes());
        Reader reader2 = new Reader(out2);
        assertEquals(0, reader2.readVarInt());
        assertEquals("music_disc.cat", reader2.readString());
        assertEquals(false, reader2.readBoolean());
    }

    @Test
    void entitySoundEffect() {
        Writer writer = new Writer();
        writer.writeVarInt(1265); // holder = id + 1（block.stone.break 763）
        writer.writeVarInt(1); // source
        writer.writeVarInt(7); // entityId
        writer.writeFloat(1.0f);
        writer.writeFloat(1.0f);
        writer.writeLong(12345L);
        byte[] out = ChunkTranslator.translateEntitySoundEffect763To762(writer.bytes());
        Reader reader = new Reader(out);
        assertEquals(1246, reader.readVarInt());
        assertEquals(1, reader.readVarInt());
        assertEquals(7, reader.readVarInt());
        assertEquals(1.0f, reader.readFloat());
        assertEquals(1.0f, reader.readFloat());
        assertEquals(12345L, reader.readLong());
        assertEquals(0, reader.remaining());
    }

    @Test
    void stopSound() {
        Writer writer = new Writer();
        writer.writeByte(0x02); // 名前だけ
        writer.writeString("music_disc.relic");
        byte[] out = ChunkTranslator.translateStopSound763To762(writer.bytes());
        Reader reader = new Reader(out);
        assertEquals(0x02, reader.readByte());
        assertEquals("music_disc.cat", reader.readString());
        assertEquals(0, reader.remaining());

        Writer writer762 = new Writer();
        writer762.writeByte(0x02);
        writer762.writeString("music.overworld.jungle_and_forest");
        byte[] back = ChunkTranslator.translateStopSound762To763(writer762.bytes());
        Reader reader762 = new Reader(back);
        assertEquals(0x02, reader762.readByte());
        assertEquals("music.overworld.jungle", reader762.readString());
        assertEquals(0, reader762.remaining());
    }

    @Test
    void worldParticles() {
        // sculk_charge：763=31 → 762=33。回転 float 付き。
        Writer sculk = new Writer();
        sculk.writeVarInt(31);
        sculk.writeFloat(2.5f);
        sculk.writeBoolean(true);
        sculk.writeDouble(1.0);
        sculk.writeDouble(2.0);
        sculk.writeDouble(3.0);
        sculk.writeFloat(0.1f);
        sculk.writeFloat(0.2f);
        sculk.writeFloat(0.3f);
        sculk.writeFloat(0.4f);
        sculk.writeInt(5);
        byte[] out = ChunkTranslator.translateWorldParticles763To762(sculk.bytes());
        Reader reader = new Reader(out);
        assertEquals(33, reader.readVarInt());
        assertEquals(2.5f, reader.readFloat());
        assertEquals(true, reader.readBoolean());
        assertEquals(1.0, reader.readDouble());
        assertEquals(2.0, reader.readDouble());
        assertEquals(3.0, reader.readDouble());
        assertEquals(0.1f, reader.readFloat());
        assertEquals(0.2f, reader.readFloat());
        assertEquals(0.3f, reader.readFloat());
        assertEquals(0.4f, reader.readFloat());
        assertEquals(5, reader.readInt());
        assertEquals(0, reader.remaining());

        // block：ブロック状態IDを変換する。
        Writer block = new Writer();
        block.writeVarInt(2);
        block.writeVarInt(24128);
        block.writeBoolean(false);
        block.writeDouble(0.0);
        block.writeDouble(0.0);
        block.writeDouble(0.0);
        block.writeFloat(0.0f);
        block.writeFloat(0.0f);
        block.writeFloat(0.0f);
        block.writeFloat(0.0f);
        block.writeInt(1);
        byte[] out2 = ChunkTranslator.translateWorldParticles763To762(block.bytes());
        Reader reader2 = new Reader(out2);
        assertEquals(2, reader2.readVarInt());
        assertEquals(23718, reader2.readVarInt());

        // item：763=40 → 762=42。スロットを変換する。
        Writer item = new Writer();
        item.writeVarInt(40);
        writeSlot(item, 1236, 1);
        item.writeBoolean(false);
        item.writeDouble(0.0);
        item.writeDouble(0.0);
        item.writeDouble(0.0);
        item.writeFloat(0.0f);
        item.writeFloat(0.0f);
        item.writeFloat(0.0f);
        item.writeFloat(0.0f);
        item.writeInt(1);
        byte[] out3 = ChunkTranslator.translateWorldParticles763To762(item.bytes());
        Reader reader3 = new Reader(out3);
        assertEquals(42, reader3.readVarInt());
        assertArrayEquals(new int[]{1224, 1}, readSlotHead(reader3));

        // 逆向き：shriek 762=95 → 763=93。遅延 tick 数付き。
        Writer shriek = new Writer();
        shriek.writeVarInt(95);
        shriek.writeVarInt(7);
        shriek.writeBoolean(false);
        shriek.writeDouble(0.0);
        shriek.writeDouble(0.0);
        shriek.writeDouble(0.0);
        shriek.writeFloat(0.0f);
        shriek.writeFloat(0.0f);
        shriek.writeFloat(0.0f);
        shriek.writeFloat(0.0f);
        shriek.writeInt(1);
        byte[] out4 = ChunkTranslator.translateWorldParticles762To763(shriek.bytes());
        Reader reader4 = new Reader(out4);
        assertEquals(93, reader4.readVarInt());
        assertEquals(7, reader4.readVarInt());
        assertEquals(false, reader4.readBoolean());
        assertEquals(0.0, reader4.readDouble());
        assertEquals(0.0, reader4.readDouble());
        assertEquals(0.0, reader4.readDouble());
        assertEquals(0.0f, reader4.readFloat());
        assertEquals(0.0f, reader4.readFloat());
        assertEquals(0.0f, reader4.readFloat());
        assertEquals(0.0f, reader4.readFloat());
        assertEquals(1, reader4.readInt());
        assertEquals(0, reader4.remaining());
    }

    @Test
    void entityEffectIdentity() {
        Writer writer = new Writer();
        writer.writeVarInt(7); // entityId
        writer.writeVarInt(5); // effectId（1ベースで同一）
        writer.writeByte(1); // amplifier
        writer.writeVarInt(100); // duration
        writer.writeByte(0); // flags
        writer.writeBoolean(true); // factorCodec あり
        writer.writeNamedNbt("", new Tag.CompoundTag(Map.of()));
        byte[] out = ChunkTranslator.translateEntityEffect763To762(writer.bytes());
        Reader reader = new Reader(out);
        assertEquals(7, reader.readVarInt());
        assertEquals(5, reader.readVarInt());
        assertEquals(1, reader.readByte());
        assertEquals(100, reader.readVarInt());
        assertEquals(0, reader.readByte());
        assertTrue(reader.readBoolean());
        assertTrue(reader.readNamedNbt().tag() instanceof Tag.CompoundTag);
        assertEquals(0, reader.remaining());

        // 逆向きも同じ。
        byte[] back = ChunkTranslator.translateEntityEffect762To763(writer.bytes());
        assertArrayEquals(out, back);
    }

    @Test
    void damageEvent() {
        Writer writer = new Writer();
        writer.writeVarInt(7); // entityId
        writer.writeVarInt(17); // generic_kill（763にしか無い）
        writer.writeVarInt(0); // sourceCauseId
        writer.writeVarInt(0); // sourceDirectId
        writer.writeBoolean(false); // 位置なし
        byte[] out = ChunkTranslator.translateDamageEvent763To762(writer.bytes());
        Reader reader = new Reader(out);
        assertEquals(7, reader.readVarInt());
        assertEquals(16, reader.readVarInt()); // generic に代替（落とさない）
        assertEquals(0, reader.readVarInt());
        assertEquals(0, reader.readVarInt());
        assertEquals(false, reader.readBoolean());
        assertEquals(0, reader.remaining());

        Writer writer762 = new Writer();
        writer762.writeVarInt(7);
        writer762.writeVarInt(17); // hot_floor（762）
        writer762.writeVarInt(0);
        writer762.writeVarInt(0);
        writer762.writeBoolean(false);
        byte[] back = ChunkTranslator.translateDamageEvent762To763(writer762.bytes());
        Reader reader762 = new Reader(back);
        assertEquals(7, reader762.readVarInt());
        assertEquals(18, reader762.readVarInt()); // hot_floor（763）
        assertEquals(0, reader762.readVarInt());
        assertEquals(0, reader762.readVarInt());
        assertEquals(false, reader762.readBoolean());
        assertEquals(0, reader762.remaining());
    }

    @Test
    void openSign() {
        Writer writer = new Writer();
        writer.writePositionRaw(0x123456789AL);
        writer.writeBoolean(false); // isFrontText=false（裏面）
        byte[] out = ChunkTranslator.translateOpenSign763To762(writer.bytes());
        Reader reader = new Reader(out);
        assertEquals(0x123456789AL, reader.readPositionRaw());
        assertEquals(0, reader.remaining()); // isFrontText は落とす

        Writer writer762 = new Writer();
        writer762.writePositionRaw(0x123456789AL);
        byte[] back = ChunkTranslator.translateOpenSign762To763(writer762.bytes());
        Reader reader762 = new Reader(back);
        assertEquals(0x123456789AL, reader762.readPositionRaw());
        assertEquals(true, reader762.readBoolean()); // isFrontText=true を足す
        assertEquals(0, reader762.remaining());
    }

    // ---- サーバー行き ----

    @Test
    void windowClick762To763() {
        Writer writer = new Writer();
        writer.writeUnsignedByte(1); // windowId
        writer.writeVarInt(5); // stateId
        writer.writeShort(3); // slot
        writer.writeByte(0); // button
        writer.writeVarInt(0); // mode
        writer.writeVarInt(1); // 変更スロット数
        writer.writeShort(3);
        writeSlot(writer, 1224, 1); // pottery_shard_archer（762）
        writeSlot(writer, 1, 2); // carried
        byte[] out = ChunkTranslator.translateWindowClick762To763(writer.bytes());
        Reader reader = new Reader(out);
        assertEquals(1, reader.readUnsignedByte());
        assertEquals(5, reader.readVarInt());
        assertEquals(3, reader.readShort());
        assertEquals(0, reader.readByte());
        assertEquals(0, reader.readVarInt());
        assertEquals(1, reader.readVarInt());
        assertEquals(3, reader.readShort());
        assertArrayEquals(new int[]{1236, 1}, readSlotHead(reader)); // archer sherd（763）
        assertArrayEquals(new int[]{1, 2}, readSlotHead(reader));
        assertEquals(0, reader.remaining());
    }

    @Test
    void setCreativeSlot762To763() {
        Writer writer = new Writer();
        writer.writeShort(3); // slot
        writeSlot(writer, 1224, 1);
        byte[] out = ChunkTranslator.translateSetCreativeSlot762To763(writer.bytes());
        Reader reader = new Reader(out);
        assertEquals(3, reader.readShort());
        assertArrayEquals(new int[]{1236, 1}, readSlotHead(reader));
        assertEquals(0, reader.remaining());
    }

    @Test
    void updateSign762To763AndBack() {
        Writer writer = new Writer();
        writer.writePositionRaw(0x123456789AL);
        writer.writeString("one");
        writer.writeString("two");
        writer.writeString("three");
        writer.writeString("four");
        byte[] out = ChunkTranslator.translateUpdateSign762To763(writer.bytes());
        Reader reader = new Reader(out);
        assertEquals(0x123456789AL, reader.readPositionRaw());
        assertEquals(true, reader.readBoolean()); // isFrontText=true を挿す
        assertEquals("one", reader.readString());
        assertEquals("two", reader.readString());
        assertEquals("three", reader.readString());
        assertEquals("four", reader.readString());
        assertEquals(0, reader.remaining());

        byte[] back = ChunkTranslator.translateUpdateSign763To762(out);
        Reader reader762 = new Reader(back);
        assertEquals(0x123456789AL, reader762.readPositionRaw());
        assertEquals("one", reader762.readString());
        assertEquals("two", reader762.readString());
        assertEquals("three", reader762.readString());
        assertEquals("four", reader762.readString());
        assertEquals(0, reader762.remaining());
    }

    @Test
    void brokenPayloadPassesThrough() {
        byte[] broken = {0x00, 0x01, 0x02};
        assertArrayEquals(broken, ChunkTranslator.translateBlockChange763To762(broken));
        assertArrayEquals(broken, ChunkTranslator.translateSetSlot763To762(broken));
        // Update Light は残りをそのまま写すため、短い入力でも変換できてしまう。
        // 空の入力だけが読み取り失敗で素通しになる。
        assertArrayEquals(new byte[0], ChunkTranslator.translateUpdateLight763To762(new byte[0]));
    }
}
