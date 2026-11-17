package io.detergentmc.protocol;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.detergentmc.protocol.Nbt.Tag;
import io.detergentmc.protocol.PacketIO.Reader;
import io.detergentmc.protocol.PacketIO.Writer;
import org.junit.jupiter.api.Test;

/**
 * {@link ChunkTranslator} の759（1.19）⇔758（1.18.2）向け変換テスト。
 *
 * <p>758↔759の差分（実測）：ブロック状態20,342⇔21,448、ブロック898⇔933、
 * アイテム1,101⇔1,152、音1,203⇔1,321、パーティクル88⇔93、エンティティ
 * 113⇔118、バイオーム61⇔63。パケットIDは161件中123件が違う。
 * 音は758・759とも varint 形式（Holder は761から）。スロット形式は両方同一。
 * パーティクルは759にしか無い5件を ViaVersion の表の通り近い粒子に代替する
 * （sonic_boom→explosion、sculk_soul／sculk_charge／sculk_charge_pop→soul、
 * shriek→vibration）。絵画は759が Spawn Entity（type=63）＋メタデータで
 * 送り、758は専用の Spawn Entity Painting（0x03）で送る。
 */
class ChunkTranslator758Test {

    /** スロット1個（758／759形式：bool＋アイテムID＋個数＋名前付き NBT 無し）。 */
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

    /** 間接パレットのセクション1個（ブロック＋バイオーム）。758／759形式。 */
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

    /** 高さマップ（空の Compound）。 */
    private static Tag.CompoundTag heightmaps() {
        return new Tag.CompoundTag(java.util.Map.of());
    }

    /** チャンク Data ペイロード（758／759形式は同一。trustEdges は引数通り）。 */
    private static byte[] chunkDataPayload(int chunkX, int chunkZ,
            int[] blockPalette, int[] biomePalette, int[][] blockEntities, boolean trustEdges) {
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
        payload.writeBoolean(trustEdges);
        payload.writeBytes(new byte[]{0x0A, 0x0B, 0x0C}); // 光データ（そのまま写る想定）
        return payload.bytes();
    }

    /** Spawn Entity ペイロード（759形式：pitch・yaw・headPitch・objectData varint・速度）。 */
    private static byte[] spawnEntity759Packet(int type, int objectData) {
        Writer writer = new Writer();
        writer.writeVarInt(7); // entityId
        writer.writeBytes(new byte[16]); // uuid
        writer.writeVarInt(type);
        writer.writeDouble(1.0);
        writer.writeDouble(2.0);
        writer.writeDouble(3.0);
        writer.writeByte(10); // pitch
        writer.writeByte(20); // yaw
        writer.writeByte(30); // headPitch
        writer.writeVarInt(objectData);
        writer.writeShort(100); // velocityX
        writer.writeShort(200); // velocityY
        writer.writeShort(300); // velocityZ
        return writer.bytes();
    }

    /** World Particles ペイロード（粒子ID varint＋データ＋共通の末尾）。 */
    private static byte[] worldParticlesPayload(Writer particle) {
        Writer writer = new Writer();
        writer.writeBytes(particle.bytes());
        writer.writeBoolean(true); // longDistance
        writer.writeDouble(1.0); // x
        writer.writeDouble(2.0); // y
        writer.writeDouble(3.0); // z
        writer.writeFloat(0.5f); // offsetX
        writer.writeFloat(0.25f); // offsetY
        writer.writeFloat(0.125f); // offsetZ
        writer.writeFloat(1.0f); // particleData
        writer.writeInt(5); // particleCount
        return writer.bytes();
    }

    /** World Particles の変換結果の末尾（粒子データの後ろ）を読む。 */
    private static void assertWorldParticlesTail(Reader reader) {
        assertTrue(reader.readBoolean()); // longDistance
        assertEquals(1.0, reader.readDouble());
        assertEquals(2.0, reader.readDouble());
        assertEquals(3.0, reader.readDouble());
        assertEquals(0.5f, reader.readFloat());
        assertEquals(0.25f, reader.readFloat());
        assertEquals(0.125f, reader.readFloat());
        assertEquals(1.0f, reader.readFloat());
        assertEquals(5, reader.readInt());
        assertEquals(0, reader.remaining());
    }

    // ---- Chunk Data ----

    @Test
    void chunkData759To758RemapsBlockStatesAndBiomes() {
        byte[] translated = ChunkTranslator.translateChunkData759To758(
                chunkDataPayload(3, -2,
                        new int[]{0, 1, 22, 74}, new int[]{7, 53},
                        new int[][]{{0x11, 64, 1}, {0x22, 65, 34}}, true));
        Reader reader = new Reader(translated);
        assertEquals(3, reader.readInt());
        assertEquals(-2, reader.readInt());
        assertTrue(reader.readNamedNbt().tag() instanceof Tag.CompoundTag);
        byte[] sections = reader.readByteArray();
        // ブロック状態：22→21、74→33。
        assertArrayEquals(new int[]{0, 1, 21, 33}, readBlockPalette(sections));
        // バイオーム：mangrove_swamp 7→swamp 6、53→51。
        assertArrayEquals(new int[]{6, 51}, readBiomePalette(sections));
        // ブロックエンティティはチェスト（type 1）だけ残り、sculk_catalyst
        // （759 type 34）は 758 の sculk_sensor（33）に写る。
        assertEquals(2, reader.readVarInt());
        assertEquals(0x11, reader.readUnsignedByte());
        assertEquals(64, reader.readShort());
        assertEquals(1, reader.readVarInt());
        assertEquals(0, reader.readByte()); // NBT 無し
        assertEquals(0x22, reader.readUnsignedByte());
        assertEquals(65, reader.readShort());
        assertEquals(33, reader.readVarInt());
        assertEquals(0, reader.readByte()); // NBT 無し
        // trustEdges（true）はそのまま写る。
        assertTrue(reader.readBoolean());
        assertArrayEquals(new byte[]{0x0A, 0x0B, 0x0C}, reader.rest());
    }

    @Test
    void chunkData758To759RemapsBack() {
        byte[] translated = ChunkTranslator.translateChunkData758To759(
                chunkDataPayload(3, -2,
                        new int[]{0, 1, 21, 33}, new int[]{6, 51},
                        new int[][]{{0x11, 64, 1}}, true));
        Reader reader = new Reader(translated);
        assertEquals(3, reader.readInt());
        assertEquals(-2, reader.readInt());
        assertTrue(reader.readNamedNbt().tag() instanceof Tag.CompoundTag);
        byte[] sections = reader.readByteArray();
        // ブロック状態：21→22、33→74。
        assertArrayEquals(new int[]{0, 1, 22, 74}, readBlockPalette(sections));
        // バイオーム：swamp 6→6（同番号）、51→53。
        assertArrayEquals(new int[]{6, 53}, readBiomePalette(sections));
        assertEquals(1, reader.readVarInt());
        assertEquals(0x11, reader.readUnsignedByte());
        assertEquals(64, reader.readShort());
        assertEquals(1, reader.readVarInt());
        assertEquals(0, reader.readByte()); // NBT 無し
        assertTrue(reader.readBoolean());
        assertArrayEquals(new byte[]{0x0A, 0x0B, 0x0C}, reader.rest());
    }

    // ---- Block Change / Multi Block Change ----

    @Test
    void blockChange759To758RemapsState() {
        Writer payload = new Writer();
        payload.writePositionRaw(0x123456789L);
        payload.writeVarInt(22); // 759 のブロック状態
        byte[] translated = ChunkTranslator.translateBlockChange759To758(payload.bytes());
        Reader reader = new Reader(translated);
        assertEquals(0x123456789L, reader.readPositionRaw());
        assertEquals(21, reader.readVarInt()); // 758 のブロック状態
        assertEquals(0, reader.remaining());
    }

    @Test
    void blockChange758To759RemapsState() {
        Writer payload = new Writer();
        payload.writePositionRaw(0x123456789L);
        payload.writeVarInt(21); // 758 のブロック状態
        byte[] translated = ChunkTranslator.translateBlockChange758To759(payload.bytes());
        Reader reader = new Reader(translated);
        assertEquals(0x123456789L, reader.readPositionRaw());
        assertEquals(22, reader.readVarInt()); // 759 のブロック状態
        assertEquals(0, reader.remaining());
    }

    @Test
    void multiBlockChange759To758ReencodesRecordsAsVarLong() {
        Writer payload = new Writer();
        payload.writeLong(0x1111222233334444L); // chunkSectionPosition
        payload.writeBoolean(false); // notTrustEdges
        payload.writeVarInt(2); // 件数
        payload.writeVarInt(100); // record 1（759 は varint）
        payload.writeVarInt(200); // record 2
        byte[] translated = ChunkTranslator.translateMultiBlockChange759To758(payload.bytes());
        Reader reader = new Reader(translated);
        assertEquals(0x1111222233334444L, reader.readLong());
        assertEquals(false, reader.readBoolean());
        assertEquals(2, reader.readVarInt());
        assertEquals(100L, reader.readVarLong()); // 758 は varlong
        assertEquals(200L, reader.readVarLong());
        assertEquals(0, reader.remaining());
    }

    @Test
    void multiBlockChange758To759ReencodesRecordsAsVarInt() {
        Writer payload = new Writer();
        payload.writeLong(0x1111222233334444L);
        payload.writeBoolean(true); // notTrustEdges
        payload.writeVarInt(1); // 件数
        payload.writeVarLong(300L); // record（758 は varlong）
        byte[] translated = ChunkTranslator.translateMultiBlockChange758To759(payload.bytes());
        Reader reader = new Reader(translated);
        assertEquals(0x1111222233334444L, reader.readLong());
        assertEquals(true, reader.readBoolean());
        assertEquals(1, reader.readVarInt());
        assertEquals(300, reader.readVarInt()); // 759 は varint
        assertEquals(0, reader.remaining());
    }

    // ---- Block Entity Data ----

    @Test
    void blockEntityData759To758SculkCatalystBecomesSensor() {
        Writer payload = new Writer();
        payload.writePositionRaw(0x123456789L);
        payload.writeVarInt(34); // sculk_catalyst（759）
        payload.writeByte(0); // NBT 無し
        byte[] translated = ChunkTranslator.translateBlockEntityData759To758(payload.bytes());
        Reader reader = new Reader(translated);
        assertEquals(0x123456789L, reader.readPositionRaw());
        assertEquals(33, reader.readVarInt()); // sculk_sensor（758）
        assertEquals(0, reader.readByte());
        assertEquals(0, reader.remaining());
    }

    // ---- Trade List ----

    @Test
    void tradeList759To758SecondInputBecomesOption() {
        Writer payload = new Writer();
        payload.writeVarInt(7); // windowId
        payload.writeUnsignedByte(1); // count（u8）
        writeSlot(payload, 1, 2); // inputItem1
        writeSlot(payload, 1, 1); // outputItem
        writeSlot(payload, 20, 1); // inputItem2（759 は必須スロット）
        payload.writeBoolean(false); // tradeDisabled
        payload.writeInt(1); // numToolUses
        payload.writeInt(2); // maxUses
        payload.writeInt(3); // xp
        payload.writeInt(4); // specialPrice
        payload.writeFloat(0.5f); // priceMultiplier
        payload.writeInt(5); // demand
        payload.writeVarInt(0); // villagerLevel
        payload.writeVarInt(0); // experience
        payload.writeBoolean(false); // isRegularVillager
        payload.writeBoolean(true); // canRestock
        byte[] translated = ChunkTranslator.translateTradeList759To758(payload.bytes());
        Reader reader = new Reader(translated);
        assertEquals(7, reader.readVarInt());
        assertEquals(1, reader.readUnsignedByte());
        assertArrayEquals(new int[]{1, 2}, readSlotHead(reader));
        assertArrayEquals(new int[]{1, 1}, readSlotHead(reader)); // outputItem
        // inputItem2 は 758 では option スロット。あり（option=true＋slot）。
        assertTrue(reader.readBoolean());
        assertArrayEquals(new int[]{19, 1}, readSlotHead(reader)); // 759 の 20→758 の 19
        assertEquals(false, reader.readBoolean());
        assertEquals(1, reader.readInt());
        assertEquals(2, reader.readInt());
        assertEquals(3, reader.readInt());
        assertEquals(4, reader.readInt());
        assertEquals(0.5f, reader.readFloat());
        assertEquals(5, reader.readInt());
        assertEquals(0, reader.readVarInt());
        assertEquals(0, reader.readVarInt());
        assertEquals(false, reader.readBoolean());
        assertEquals(true, reader.readBoolean());
        assertEquals(0, reader.remaining());
    }

    @Test
    void tradeList758To759AbsentSecondInputBecomesEmptySlot() {
        Writer payload = new Writer();
        payload.writeVarInt(7); // windowId
        payload.writeUnsignedByte(1); // count（u8）
        writeSlot(payload, 1, 2); // inputItem1
        writeSlot(payload, 1, 1); // outputItem
        payload.writeBoolean(false); // inputItem2（758 は option スロット。無し）
        payload.writeBoolean(false); // tradeDisabled
        payload.writeInt(1); // numToolUses
        payload.writeInt(2); // maxUses
        payload.writeInt(3); // xp
        payload.writeInt(4); // specialPrice
        payload.writeFloat(0.5f); // priceMultiplier
        payload.writeInt(5); // demand
        payload.writeVarInt(0); // villagerLevel
        payload.writeVarInt(0); // experience
        payload.writeBoolean(false); // isRegularVillager
        payload.writeBoolean(true); // canRestock
        byte[] translated = ChunkTranslator.translateTradeList758To759(payload.bytes());
        Reader reader = new Reader(translated);
        assertEquals(7, reader.readVarInt());
        assertEquals(1, reader.readUnsignedByte());
        assertArrayEquals(new int[]{1, 2}, readSlotHead(reader));
        assertArrayEquals(new int[]{1, 1}, readSlotHead(reader)); // outputItem
        // inputItem2 は 759 では必須スロット。無しは present=false のスロットになる。
        assertFalse(reader.readBoolean());
        assertEquals(false, reader.readBoolean());
        assertEquals(1, reader.readInt());
        assertEquals(2, reader.readInt());
        assertEquals(3, reader.readInt());
        assertEquals(4, reader.readInt());
        assertEquals(0.5f, reader.readFloat());
        assertEquals(5, reader.readInt());
        assertEquals(0, reader.readVarInt());
        assertEquals(0, reader.readVarInt());
        assertEquals(false, reader.readBoolean());
        assertEquals(true, reader.readBoolean());
        assertEquals(0, reader.remaining());
    }

    // ---- 出現 ----

    @Test
    void spawnEntity759FallingBlockRemapsObjectData() {
        byte[] translated = ChunkTranslator.translateSpawnEntity759To758(
                spawnEntity759Packet(29, 22)); // falling_block、objectData はブロック状態
        Reader reader = new Reader(translated);
        assertEquals(7, reader.readVarInt()); // entityId
        reader.readBytes(16); // uuid
        assertEquals(27, reader.readVarInt()); // 758 の falling_block
        assertEquals(1.0, reader.readDouble());
        assertEquals(2.0, reader.readDouble());
        assertEquals(3.0, reader.readDouble());
        assertEquals(10, reader.readByte()); // pitch
        assertEquals(20, reader.readByte()); // yaw
        assertEquals(21, reader.readInt()); // objectData（ブロック状態。759 の 22→758 の 21）
        assertEquals(100, reader.readShort());
        assertEquals(200, reader.readShort());
        assertEquals(300, reader.readShort());
        assertEquals(0, reader.remaining());
    }

    @Test
    void spawnEntity759LivingBecomesSpawnLivingEntity758() {
        byte[] translated = ChunkTranslator.spawnEntity759ToSpawnLivingEntity758(
                spawnEntity759Packet(2, 0)); // armor_stand（生き物）
        Reader reader = new Reader(translated);
        assertEquals(7, reader.readVarInt());
        reader.readBytes(16); // uuid
        assertEquals(1, reader.readVarInt()); // 758 の armor_stand
        assertEquals(1.0, reader.readDouble());
        assertEquals(2.0, reader.readDouble());
        assertEquals(3.0, reader.readDouble());
        assertEquals(20, reader.readByte()); // yaw（759 の pitch・yaw と入れ替わる）
        assertEquals(10, reader.readByte()); // pitch
        assertEquals(30, reader.readByte()); // headPitch
        assertEquals(100, reader.readShort());
        assertEquals(200, reader.readShort());
        assertEquals(300, reader.readShort());
        assertEquals(0, reader.remaining());
    }

    @Test
    void spawnEntity759PlayerBecomesNamedEntitySpawn758() {
        byte[] translated = ChunkTranslator.spawnEntity759ToNamedEntitySpawn758(
                spawnEntity759Packet(116, 0)); // player
        Reader reader = new Reader(translated);
        assertEquals(7, reader.readVarInt());
        reader.readBytes(16); // uuid
        assertEquals(1.0, reader.readDouble());
        assertEquals(2.0, reader.readDouble());
        assertEquals(3.0, reader.readDouble());
        assertEquals(20, reader.readByte()); // yaw
        assertEquals(10, reader.readByte()); // pitch
        assertEquals(0, reader.remaining());
    }

    @Test
    void spawnEntity759PaintingBecomesSpawnPainting758() {
        byte[] translated = ChunkTranslator.spawnEntity759ToSpawnPainting758(
                spawnEntity759Packet(63, 2), 25); // painting、方向 2、バリアント 25
        Reader reader = new Reader(translated);
        assertEquals(7, reader.readVarInt());
        reader.readBytes(16); // uuid
        assertEquals(25, reader.readVarInt()); // title（絵画バリアント）
        long position = reader.readPositionRaw();
        assertEquals(1, (int) (position >> 38)); // x
        assertEquals(2, (int) (position & 0xFFF)); // y
        assertEquals(3, (int) (position << 26 >> 38)); // z
        assertEquals(2, reader.readByte()); // direction
        assertEquals(0, reader.remaining());
    }

    @Test
    void spawnEntity759PaintingNonPaintingReturnsNull() {
        assertNull(ChunkTranslator.spawnEntity759ToSpawnPainting758(
                spawnEntity759Packet(2, 0), 0)); // armor_stand は絵画ではない
        assertNull(ChunkTranslator.spawnEntity759ToSpawnLivingEntity758(
                spawnEntity759Packet(29, 0))); // falling_block は生き物ではない
    }

    @Test
    void paintingVariantFromMetadata759ReadsIndex8() {
        Writer payload = new Writer();
        payload.writeVarInt(7); // entityId
        payload.writeUnsignedByte(8); // index
        payload.writeVarInt(1); // type varint
        payload.writeVarInt(29); // variant（759 の donkey_kong）
        payload.writeUnsignedByte(0xFF); // 終端
        int[] result = ChunkTranslator.paintingVariantFromMetadata759(payload.bytes());
        assertArrayEquals(new int[]{7, 29}, result);
    }

    @Test
    void paintingVariantFromMetadata759WithoutVariantReturnsMinusOne() {
        Writer payload = new Writer();
        payload.writeVarInt(7); // entityId
        payload.writeUnsignedByte(0); // index 0（byte 型）
        payload.writeVarInt(0); // type byte
        payload.writeByte(1);
        payload.writeUnsignedByte(0xFF); // 終端
        int[] result = ChunkTranslator.paintingVariantFromMetadata759(payload.bytes());
        assertArrayEquals(new int[]{7, -1}, result);
    }

    @Test
    void paintingVariant759To758DropsNewVariants() {
        // 759 の earth／wind／water／fire（25〜28）は 758 に無いため DROP。
        assertEquals(RegistryRemapper.DROP, RegistryRemapper.mapPaintingVariant759To758(25));
        assertEquals(RegistryRemapper.DROP, RegistryRemapper.mapPaintingVariant759To758(28));
        // donkey_kong は 759 の 29→758 の 25。
        assertEquals(25, RegistryRemapper.mapPaintingVariant759To758(29));
        // 0〜24 は恒等。
        assertEquals(0, RegistryRemapper.mapPaintingVariant759To758(0));
        assertEquals(24, RegistryRemapper.mapPaintingVariant759To758(24));
        // 逆方向：758 の 25→759 の 29。
        assertEquals(29, RegistryRemapper.mapPaintingVariant758To759(25));
        assertEquals(0, RegistryRemapper.mapPaintingVariant758To759(0));
    }

    // ---- 音 ----

    @Test
    void soundEffect759To758DropsSeedAndRemapsId() {
        Writer payload = new Writer();
        payload.writeVarInt(10); // 音ID（759）
        payload.writeVarInt(1); // category
        payload.writeInt(100); // x
        payload.writeInt(200); // y
        payload.writeInt(300); // z
        payload.writeFloat(1.0f); // volume
        payload.writeFloat(0.5f); // pitch
        payload.writeLong(123456789L); // seed（759 にだけある）
        byte[] translated = ChunkTranslator.translateSoundEffect759To758(payload.bytes());
        Reader reader = new Reader(translated);
        assertEquals(3, reader.readVarInt()); // 758 の音ID（10→3）
        assertEquals(1, reader.readVarInt());
        assertEquals(100, reader.readInt());
        assertEquals(200, reader.readInt());
        assertEquals(300, reader.readInt());
        assertEquals(1.0f, reader.readFloat());
        assertEquals(0.5f, reader.readFloat());
        assertEquals(0, reader.remaining()); // seed は落とされる
    }

    @Test
    void soundEffect758To759AddsZeroSeedAndRemapsId() {
        Writer payload = new Writer();
        payload.writeVarInt(3); // 音ID（758）
        payload.writeVarInt(1); // category
        payload.writeInt(100); // x
        payload.writeInt(200); // y
        payload.writeInt(300); // z
        payload.writeFloat(1.0f); // volume
        payload.writeFloat(0.5f); // pitch
        byte[] translated = ChunkTranslator.translateSoundEffect758To759(payload.bytes());
        Reader reader = new Reader(translated);
        assertEquals(10, reader.readVarInt()); // 759 の音ID（3→10）
        assertEquals(1, reader.readVarInt());
        assertEquals(100, reader.readInt());
        assertEquals(200, reader.readInt());
        assertEquals(300, reader.readInt());
        assertEquals(1.0f, reader.readFloat());
        assertEquals(0.5f, reader.readFloat());
        assertEquals(0L, reader.readLong()); // seed は 0 を補う
        assertEquals(0, reader.remaining());
    }

    // ---- パーティクル ----

    @Test
    void worldParticles759To758FallingDustRemapsIdAndState() {
        Writer particle = new Writer();
        particle.writeVarInt(25); // falling_dust（759）
        particle.writeVarInt(22); // ブロック状態（759）
        byte[] translated = ChunkTranslator.translateWorldParticles759To758(
                worldParticlesPayload(particle));
        Reader reader = new Reader(translated);
        assertEquals(24, reader.readVarInt()); // falling_dust（758）
        assertEquals(21, reader.readVarInt()); // ブロック状態（758）
        assertWorldParticlesTail(reader);
    }

    @Test
    void worldParticles759To758ShriekBecomesVibration() {
        Writer particle = new Writer();
        particle.writeVarInt(92); // shriek（759）
        particle.writeVarInt(7); // 遅延 tick 数
        byte[] translated = ChunkTranslator.translateWorldParticles759To758(
                worldParticlesPayload(particle));
        Reader reader = new Reader(translated);
        assertEquals(36, reader.readVarInt()); // vibration（758）
        assertEquals(0L, reader.readPositionRaw()); // origin（(0,0,0) で補完）
        assertEquals("minecraft:block", reader.readString()); // positionType
        assertEquals(0L, reader.readPositionRaw()); // destination（(0,0,0) で補完）
        assertEquals(7, reader.readVarInt()); // ticks（遅延）
        assertWorldParticlesTail(reader);
    }

    @Test
    void worldParticles759To758SculkChargeBecomesSoulAndDropsRotation() {
        Writer particle = new Writer();
        particle.writeVarInt(30); // sculk_charge（759）
        particle.writeFloat(1.5f); // 回転
        byte[] translated = ChunkTranslator.translateWorldParticles759To758(
                worldParticlesPayload(particle));
        Reader reader = new Reader(translated);
        assertEquals(29, reader.readVarInt()); // soul（758。回転は落とされる）
        assertWorldParticlesTail(reader);
    }

    @Test
    void worldParticles758To759FireworkPassesThroughWithoutReadingData() {
        // 758 の firework（25）はデータ無し。759 では 26。
        // 旧実装は写像後 ID で switch していたため、759 の falling_dust（25）と
        // 取り違えてブロック状態を読もうとして壊れていた。回帰テスト。
        Writer particle = new Writer();
        particle.writeVarInt(25); // firework（758）
        byte[] translated = ChunkTranslator.translateWorldParticles758To759(
                worldParticlesPayload(particle));
        Reader reader = new Reader(translated);
        assertEquals(26, reader.readVarInt()); // firework（759）
        assertWorldParticlesTail(reader);
    }

    @Test
    void worldParticles758To759VibrationDropsOrigin() {
        Writer particle = new Writer();
        particle.writeVarInt(36); // vibration（758）
        particle.writePositionRaw(0x123456789L); // origin
        particle.writeString("minecraft:block");
        particle.writePositionRaw(0x987654321L); // destination
        particle.writeVarInt(9); // ticks
        byte[] translated = ChunkTranslator.translateWorldParticles758To759(
                worldParticlesPayload(particle));
        Reader reader = new Reader(translated);
        assertEquals(40, reader.readVarInt()); // vibration（759）
        assertEquals("minecraft:block", reader.readString());
        assertEquals(0x987654321L, reader.readPositionRaw());
        assertEquals(9, reader.readVarInt());
        assertWorldParticlesTail(reader);
    }

    // ---- Entity Effect ----

    @Test
    void entityEffect759To758DropsFactorCodec() {
        Writer payload = new Writer();
        payload.writeVarInt(7); // entityId
        payload.writeByte(1); // effectId
        payload.writeByte(2); // amplifier
        payload.writeVarInt(100); // duration
        payload.writeByte(0); // flags
        payload.writeBoolean(true); // factorCodec あり
        payload.writeByte(10); // 名前なし NBT：compound（空）
        payload.writeByte(0); // 終端
        byte[] translated = ChunkTranslator.translateEntityEffect759To758(payload.bytes());
        Reader reader = new Reader(translated);
        assertEquals(7, reader.readVarInt());
        assertEquals(1, reader.readByte());
        assertEquals(2, reader.readByte());
        assertEquals(100, reader.readVarInt());
        assertEquals(0, reader.readByte());
        assertEquals(0, reader.remaining()); // factorCodec は落とされる
    }

    @Test
    void entityEffect758To759AddsAbsentFactorCodec() {
        Writer payload = new Writer();
        payload.writeVarInt(7); // entityId
        payload.writeByte(1); // effectId
        payload.writeByte(2); // amplifier
        payload.writeVarInt(100); // duration
        payload.writeByte(0); // flags
        byte[] translated = ChunkTranslator.translateEntityEffect758To759(payload.bytes());
        Reader reader = new Reader(translated);
        assertEquals(7, reader.readVarInt());
        assertEquals(1, reader.readByte());
        assertEquals(2, reader.readByte());
        assertEquals(100, reader.readVarInt());
        assertEquals(0, reader.readByte());
        assertEquals(false, reader.readBoolean()); // factorCodec: absent
        assertEquals(0, reader.remaining());
    }

    // ---- Window Click ----

    @Test
    void windowClick758To759KeepsSlots() {
        Writer payload = new Writer();
        payload.writeUnsignedByte(1); // windowId
        payload.writeVarInt(0); // stateId
        payload.writeShort(5); // slot
        payload.writeByte(0); // button
        payload.writeVarInt(0); // mode
        payload.writeVarInt(1); // 変更スロット数
        payload.writeShort(5); // slot
        writeSlot(payload, 20, 1); // 758 のアイテム 20
        writeSlot(payload, 1, 2); // カーソル
        byte[] translated = ChunkTranslator.translateWindowClick758To759(payload.bytes());
        Reader reader = new Reader(translated);
        assertEquals(1, reader.readUnsignedByte());
        assertEquals(0, reader.readVarInt());
        assertEquals(5, reader.readShort());
        assertEquals(0, reader.readByte());
        assertEquals(0, reader.readVarInt());
        assertEquals(1, reader.readVarInt());
        assertEquals(5, reader.readShort());
        assertArrayEquals(new int[]{21, 1}, readSlotHead(reader)); // 758 の 20→759 の 21
        assertArrayEquals(new int[]{1, 2}, readSlotHead(reader));
        assertEquals(0, reader.remaining());
    }
}
