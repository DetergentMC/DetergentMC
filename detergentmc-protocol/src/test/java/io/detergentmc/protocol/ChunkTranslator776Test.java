package io.detergentmc.protocol;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import io.detergentmc.protocol.PacketIO.Reader;
import io.detergentmc.protocol.PacketIO.Writer;
import org.junit.jupiter.api.Test;

/**
 * 775↔776のチャンク変換テスト。
 *
 * <p>775→776のブロック状態は 24687 未満が同一、24687 以降は 26.2 での
 * 並べ替えでずれる（vv-26.2.json／vv-26.1.json の名前照合で全 29,873 件を
 * 独立に確認済み。maps776.json とも 0 件の不一致）。スポット値はその照合
 * から取った（calcite 24687→27160、calibrated_sculk_sensor 25000→27473、
 * waxed_oxidized_cut_copper_slab 26000→28491、
 * waxed_weathered_copper_trapdoor 27000→29409、
 * waxed_oxidized_lightning_rod 27734→30207、pointed_dripstone 27735→30209、
 * cobbled_deepslate_stairs 28000→30493、deepslate_tile_wall 29000→31493、
 * firefly_bush 29872→32365）。
 * バイオームは sulfur_caves（776=53）の挿入で 53 以上が+1ずれる。
 * 776→775 は 53 を dripstone_caves（15）にフォールバックする。
 * エンティティは sulfur_cube（776=130）の挿入で 130 番以降が+1ずれる。
 * ブロックエンティティは bed（775=25）が 776 で削除され、potent_sulfur
 * （776=48）が追加された。パーティクルは 4〜10 に 7 件の挿入と
 * sulfur_cube_goo（124）の追加で 4 番以降が+7ずれる。
 * 776 にだけある 8 種の粒子は 775 へ entity_effect（21）にフォールバックし、
 * 色の i32 として 0 を合成する（色を書かないとパケットが壊れる。
 * 765→766 の translateParticle765To766 と同じ設計）。
 */
class ChunkTranslator776Test {

    /** エントリ列をバニラの BitStorage と同じ連続詰めで long 列にする。 */
    private static long[] packEntries(int[] entries, int bits) {
        long[] longs = new long[(entries.length * bits + 63) / 64];
        long mask = (1L << bits) - 1;
        int bitPos = 0;
        for (int entry : entries) {
            int cellIndex = bitPos >> 6;
            int bitIndex = bitPos & 63;
            longs[cellIndex] |= ((long) entry & mask) << bitIndex;
            if (bitIndex + bits > 64) {
                int lowBits = 64 - bitIndex;
                longs[cellIndex + 1] |= ((long) entry & mask) >>> lowBits;
            }
            bitPos += bits;
        }
        return longs;
    }

    /** long 列をバニラの BitStorage と同じ連続詰めでエントリ列に戻す。 */
    private static int[] unpackEntries(long[] longs, int bits, int count) {
        int[] entries = new int[count];
        long mask = (1L << bits) - 1;
        int bitPos = 0;
        for (int i = 0; i < count; i++) {
            int cellIndex = bitPos >> 6;
            int bitIndex = bitPos & 63;
            long low = longs[cellIndex];
            if (bitIndex + bits <= 64) {
                entries[i] = (int) ((low >>> bitIndex) & mask);
            } else {
                int lowBits = 64 - bitIndex;
                long high = cellIndex + 1 < longs.length ? longs[cellIndex + 1] : 0L;
                entries[i] = (int) (((low >>> bitIndex) | (high << lowBits)) & mask);
            }
            bitPos += bits;
        }
        return entries;
    }

    /** heightmap の直接配列（type 1=WORLD_SURFACE、4=MOTION_BLOCKING、各2要素）。 */
    private static void writeHeightmapArray(Writer writer) {
        writer.writeVarInt(2);
        writer.writeVarInt(1);
        writer.writeVarInt(2);
        writer.writeLong(44L);
        writer.writeLong(55L);
        writer.writeVarInt(4);
        writer.writeVarInt(2);
        writer.writeLong(11L);
        writer.writeLong(22L);
    }

    /** 間接パレットのセクション1個（775／776形式。Data Array に長さ varint が無い）。 */
    private static byte[] indirectSection(int blockBits, int[] blockPalette,
                                          int[] blockIndices,
                                          int biomeBits, int[] biomePalette,
                                          int[] biomeIndices) {
        Writer writer = new Writer();
        writer.writeShort(4096); // ブロック数
        writer.writeUnsignedByte(blockBits);
        writer.writeVarInt(blockPalette.length);
        for (int value : blockPalette) {
            writer.writeVarInt(value);
        }
        for (long value : packEntries(blockIndices, blockBits)) {
            writer.writeLong(value);
        }
        writer.writeUnsignedByte(biomeBits);
        writer.writeVarInt(biomePalette.length);
        for (int value : biomePalette) {
            writer.writeVarInt(value);
        }
        for (long value : packEntries(biomeIndices, biomeBits)) {
            writer.writeLong(value);
        }
        return writer.bytes();
    }

    /** 直接パレットのセクション1個（ブロック BPE 15、バイオームは間接 BPE 1）。 */
    private static byte[] directSection(int[] blockStates) {
        Writer writer = new Writer();
        writer.writeShort(4096);
        writer.writeUnsignedByte(15); // ブロック直接パレット
        for (long value : packEntries(blockStates, 15)) {
            writer.writeLong(value);
        }
        writer.writeUnsignedByte(1); // バイオーム BPE 1（間接、2種）
        writer.writeVarInt(2);
        writer.writeVarInt(0);
        writer.writeVarInt(1);
        int[] biomeIndices = new int[64];
        for (int i = 0; i < 64; i++) {
            biomeIndices[i] = i % 2;
        }
        for (long value : packEntries(biomeIndices, 1)) {
            writer.writeLong(value);
        }
        return writer.bytes();
    }

    /** チャンク Data ペイロード（775／776共通の形）。セクションは1個。ブロックエンティティは0件。 */
    private static byte[] chunkDataPayload(byte[] section) {
        return chunkDataPayloadWithBlockEntities(section, new int[0][]);
    }

    /**
     * チャンク Data ペイロード。ブロックエンティティは [packedXZ, y, type] の
     * 列（NBT は無し＝0 バイト）で与える。
     */
    private static byte[] chunkDataPayloadWithBlockEntities(byte[] section, int[][] blockEntities) {
        Writer payload = new Writer();
        payload.writeInt(3);
        payload.writeInt(-2);
        writeHeightmapArray(payload);
        payload.writeByteArray(section);
        payload.writeVarInt(blockEntities.length);
        for (int[] entry : blockEntities) {
            payload.writeUnsignedByte(entry[0]);
            payload.writeShort(entry[1]);
            payload.writeVarInt(entry[2]);
            payload.writeByte(0); // NBT 無し
        }
        return payload.bytes();
    }

    /** 変換後のセクションを読み、ブロック／バイオームのコンテナを返す。 */
    private static class ParsedSection {
        int blockBits;
        int[] blockPalette;
        long[] blockData;
        int biomeBits;
        int[] biomePalette;
        long[] biomeData;
    }

    private static ParsedSection readSection(byte[] sections) {
        Reader reader = new Reader(sections);
        reader.readShort();
        ParsedSection out = new ParsedSection();
        out.blockBits = reader.readUnsignedByte();
        int blockPaletteSize = reader.readVarInt();
        out.blockPalette = new int[blockPaletteSize];
        for (int i = 0; i < blockPaletteSize; i++) {
            out.blockPalette[i] = reader.readVarInt();
        }
        int blockLongs = (4096 * out.blockBits + 63) / 64;
        out.blockData = new long[blockLongs];
        for (int i = 0; i < blockLongs; i++) {
            out.blockData[i] = reader.readLong();
        }
        out.biomeBits = reader.readUnsignedByte();
        int biomePaletteSize = reader.readVarInt();
        out.biomePalette = new int[biomePaletteSize];
        for (int i = 0; i < biomePaletteSize; i++) {
            out.biomePalette[i] = reader.readVarInt();
        }
        int biomeLongs = (64 * out.biomeBits + 63) / 64;
        out.biomeData = new long[biomeLongs];
        for (int i = 0; i < biomeLongs; i++) {
            out.biomeData[i] = reader.readLong();
        }
        assertEquals(0, reader.remaining(), "セクションの末尾に余りがある");
        return out;
    }

    /** 変換後のペイロード全体を読み、セクション部分を返す（ブロックエンティティは0件と仮定）。 */
    private static byte[] readSections(byte[] payload) {
        Reader reader = new Reader(payload);
        assertEquals(3, reader.readInt());
        assertEquals(-2, reader.readInt());
        assertEquals(2, reader.readVarInt());
        for (int i = 0; i < 2; i++) {
            reader.readVarInt();
            int len = reader.readVarInt();
            for (int j = 0; j < len; j++) {
                reader.readLong();
            }
        }
        byte[] sections = reader.readByteArray();
        assertEquals(0, reader.readVarInt()); // ブロックエンティティは0件
        assertEquals(0, reader.remaining(), "ペイロードの末尾に余りがある");
        return sections;
    }

    /** 変換後のペイロード全体からブロックエンティティの [packedXZ, y, type] 列を返す。 */
    private static int[][] readBlockEntities(byte[] payload) {
        Reader reader = new Reader(payload);
        assertEquals(3, reader.readInt());
        assertEquals(-2, reader.readInt());
        assertEquals(2, reader.readVarInt());
        for (int i = 0; i < 2; i++) {
            reader.readVarInt();
            int len = reader.readVarInt();
            for (int j = 0; j < len; j++) {
                reader.readLong();
            }
        }
        reader.readByteArray(); // セクション
        int count = reader.readVarInt();
        int[][] out = new int[count][];
        for (int i = 0; i < count; i++) {
            int packedXZ = reader.readUnsignedByte();
            int y = reader.readShort();
            int type = reader.readVarInt();
            reader.readAnonymousNbt();
            out[i] = new int[]{packedXZ, y, type};
        }
        assertEquals(0, reader.remaining(), "ペイロードの末尾に余りがある");
        return out;
    }

    /** 775 のブロック状態スポット値（17件。24687 未満は同一、以降は vv 照合の値）。 */
    private static final int[] BLOCK_SPOT_775 = new int[]{0, 1, 2, 1000, 5000, 10000, 20000,
            24686, 24687, 25000, 26000, 27000, 27734, 27735, 28000, 29000, 29872};
    /** 上記の 776 側の期待値。 */
    private static final int[] BLOCK_SPOT_776 = new int[]{0, 1, 2, 1000, 5000, 10000, 20000,
            24686, 27160, 27473, 28491, 29409, 30207, 30209, 30493, 31493, 32365};

    // ---- 間接パレット（ブロック 5 ビット・バイオーム 3 ビット） ----

    @Test
    void chunkData775To776IndirectPalette() {
        int[] blockIndices = new int[4096];
        for (int i = 0; i < 4096; i++) {
            blockIndices[i] = i % 17;
        }
        // バイオームは 53（sulfur_caves の挿入位置）をまたぐ値を入れる。
        int[] biomePalette = new int[]{0, 15, 40, 52, 53, 54, 64};
        int[] expectedBiomePalette = new int[]{0, 15, 40, 52, 54, 55, 65};
        int[] biomeIndices = new int[64];
        for (int i = 0; i < 64; i++) {
            biomeIndices[i] = i % 7;
        }
        byte[] translated = ChunkTranslator.translateChunkData775To776(
                chunkDataPayload(indirectSection(5, BLOCK_SPOT_775, blockIndices,
                        3, biomePalette, biomeIndices)));
        ParsedSection out = readSection(readSections(translated));
        assertEquals(5, out.blockBits);
        assertArrayEquals(BLOCK_SPOT_776, out.blockPalette);
        assertArrayEquals(packEntries(blockIndices, 5), out.blockData);
        assertEquals(3, out.biomeBits);
        assertArrayEquals(expectedBiomePalette, out.biomePalette);
        assertArrayEquals(packEntries(biomeIndices, 3), out.biomeData);
    }

    @Test
    void chunkData776To775IndirectPalette() {
        int[] blockIndices = new int[4096];
        for (int i = 0; i < 4096; i++) {
            blockIndices[i] = (i * 7) % 17;
        }
        // 776 の 53（sulfur_caves）は 775 では dripstone_caves（15）に
        // フォールバックする。54→53、65→64。
        int[] biomePalette = new int[]{0, 15, 40, 52, 53, 54, 65};
        int[] expectedBiomePalette = new int[]{0, 15, 40, 52, 15, 53, 64};
        int[] biomeIndices = new int[64];
        for (int i = 0; i < 64; i++) {
            biomeIndices[i] = (i * 3) % 7;
        }
        byte[] translated = ChunkTranslator.translateChunkData776To775(
                chunkDataPayload(indirectSection(5, BLOCK_SPOT_776, blockIndices,
                        3, biomePalette, biomeIndices)));
        ParsedSection out = readSection(readSections(translated));
        assertEquals(5, out.blockBits);
        assertArrayEquals(BLOCK_SPOT_775, out.blockPalette);
        assertArrayEquals(packEntries(blockIndices, 5), out.blockData);
        assertEquals(3, out.biomeBits);
        assertArrayEquals(expectedBiomePalette, out.biomePalette);
        assertArrayEquals(packEntries(biomeIndices, 3), out.biomeData);
    }

    // ---- 直接パレット（ブロック 15 ビット） ----

    @Test
    void chunkData775To776DirectPaletteFifteenBit() {
        // 17 件のスポット値を繰り返す。15 ビットは 64 を割り切らないため、
        // またぐエントリが 4096 個中 767 個ある。
        int[] blockStates = new int[4096];
        int[] expected = new int[4096];
        for (int i = 0; i < 4096; i++) {
            blockStates[i] = BLOCK_SPOT_775[i % 17];
            expected[i] = BLOCK_SPOT_776[i % 17];
        }
        byte[] translated = ChunkTranslator.translateChunkData775To776(
                chunkDataPayload(directSection(blockStates)));
        Reader reader = new Reader(readSections(translated));
        reader.readShort();
        assertEquals(15, reader.readUnsignedByte());
        // 直接パレットにはパレットが無い。long 数は (4096*15+63)/64 = 960。
        long[] longs = new long[960];
        for (int i = 0; i < 960; i++) {
            longs[i] = reader.readLong();
        }
        assertArrayEquals(expected, unpackEntries(longs, 15, 4096));
        // 後続のバイオーム（BPE 1、パレット [0, 1]、1 long）も読む。
        assertEquals(1, reader.readUnsignedByte());
        assertEquals(2, reader.readVarInt());
        assertEquals(0, reader.readVarInt());
        assertEquals(1, reader.readVarInt());
        reader.readLong();
        assertEquals(0, reader.remaining(), "セクションの末尾に余りがある");
    }

    @Test
    void chunkData776To775DirectPaletteFifteenBit() {
        int[] blockStates = new int[4096];
        int[] expected = new int[4096];
        for (int i = 0; i < 4096; i++) {
            blockStates[i] = BLOCK_SPOT_776[(i * 5) % 17];
            expected[i] = BLOCK_SPOT_775[(i * 5) % 17];
        }
        byte[] translated = ChunkTranslator.translateChunkData776To775(
                chunkDataPayload(directSection(blockStates)));
        Reader reader = new Reader(readSections(translated));
        reader.readShort();
        assertEquals(15, reader.readUnsignedByte());
        long[] longs = new long[960];
        for (int i = 0; i < 960; i++) {
            longs[i] = reader.readLong();
        }
        assertArrayEquals(expected, unpackEntries(longs, 15, 4096));
        assertEquals(1, reader.readUnsignedByte());
        assertEquals(2, reader.readVarInt());
        assertEquals(0, reader.readVarInt());
        assertEquals(1, reader.readVarInt());
        reader.readLong();
        assertEquals(0, reader.remaining(), "セクションの末尾に余りがある");
    }

    // ---- ブロックエンティティ（bed 削除／potent_sulfur 追加） ----

    @Test
    void chunkData775To776BlockEntityBedDropped() {
        byte[] section = indirectSection(5, new int[]{0, 1}, fillIndices(4096, 2),
                1, new int[]{0, 1}, fillIndices(64, 2));
        byte[] translated = ChunkTranslator.translateChunkData775To776(
                chunkDataPayloadWithBlockEntities(section, new int[][]{
                        {0x12, 64, 25}, // bed（775=25）は 776 に無いため落とす
                        {0x34, 65, 0}, // furnace は同一
                }));
        int[][] kept = readBlockEntities(translated);
        assertEquals(1, kept.length);
        assertArrayEquals(new int[]{0x34, 65, 0}, kept[0]);
    }

    @Test
    void chunkData776To775BlockEntityPotentSulfurDropped() {
        byte[] section = indirectSection(5, new int[]{0, 1}, fillIndices(4096, 2),
                1, new int[]{0, 1}, fillIndices(64, 2));
        byte[] translated = ChunkTranslator.translateChunkData776To775(
                chunkDataPayloadWithBlockEntities(section, new int[][]{
                        {0x12, 64, 48}, // potent_sulfur（776=48）は 775 に無いため落とす
                        {0x34, 65, 25}, // 776=25 は 775 の conduit（26）
                        {0x56, 66, 0}, // furnace は同一
                }));
        int[][] kept = readBlockEntities(translated);
        assertEquals(2, kept.length);
        assertArrayEquals(new int[]{0x34, 65, 26}, kept[0]);
        assertArrayEquals(new int[]{0x56, 66, 0}, kept[1]);
    }

    private static int[] fillIndices(int count, int paletteSize) {
        int[] indices = new int[count];
        for (int i = 0; i < count; i++) {
            indices[i] = i % paletteSize;
        }
        return indices;
    }

    // ---- スロット／成分（KIND 表・tooltip_display・sulfur_cube_content） ----

    /** スロット1個（count・itemId・added の成分列・removed の種別列）。 */
    private static byte[] slotPayload(int count, int itemId, int[] addedKinds, byte[][] addedData,
                                      int[] removed) {
        Writer writer = new Writer();
        writer.writeVarInt(count);
        writer.writeVarInt(itemId);
        writer.writeVarInt(addedKinds.length);
        for (int i = 0; i < addedKinds.length; i++) {
            writer.writeVarInt(addedKinds[i]);
            writer.writeBytes(addedData[i]);
        }
        writer.writeVarInt(removed.length);
        for (int kind : removed) {
            writer.writeVarInt(kind);
        }
        return writer.bytes();
    }

    /** tooltip_display のデータ（hideTooltip・隠す種別の列）。 */
    private static byte[] tooltipDisplayData(boolean hideTooltip, int[] hiddenKinds) {
        Writer writer = new Writer();
        writer.writeBoolean(hideTooltip);
        writer.writeVarInt(hiddenKinds.length);
        for (int kind : hiddenKinds) {
            writer.writeVarInt(kind);
        }
        return writer.bytes();
    }

    /** ItemStackTemplate 1個（itemId・count・added・removed）。 */
    private static byte[] templateData(int itemId, int itemCount) {
        Writer writer = new Writer();
        writer.writeVarInt(itemId);
        writer.writeVarInt(itemCount);
        writer.writeVarInt(0); // added は0件
        writer.writeVarInt(0); // removed は0件
        return writer.bytes();
    }

    /** 変換後のスロットから tooltip_display（種別18）の隠す種別列を読む。 */
    private static int[] readSlotTooltip(byte[] payload, boolean[] hideTooltipOut) {
        Reader reader = new Reader(payload);
        assertEquals(1, reader.readVarInt()); // count
        assertEquals(1, reader.readVarInt()); // itemId（stone は同一）
        int added = reader.readVarInt();
        assertEquals(1, added);
        assertEquals(18, reader.readVarInt());
        hideTooltipOut[0] = reader.readBoolean();
        int hidden = reader.readVarInt();
        int[] kinds = new int[hidden];
        for (int i = 0; i < hidden; i++) {
            kinds[i] = reader.readVarInt();
        }
        assertEquals(0, reader.readVarInt()); // removed は0件
        assertEquals(0, reader.remaining(), "スロットの末尾に余りがある");
        return kinds;
    }

    /** 変換後のスロットの added 件数と removed の種別列を読む。 */
    private static int[] readSlotRemoved(byte[] payload) {
        Reader reader = new Reader(payload);
        assertEquals(1, reader.readVarInt()); // count
        assertEquals(1, reader.readVarInt()); // itemId
        assertEquals(0, reader.readVarInt()); // added は0件
        int removed = reader.readVarInt();
        int[] kinds = new int[removed];
        for (int i = 0; i < removed; i++) {
            kinds[i] = reader.readVarInt();
        }
        assertEquals(0, reader.remaining(), "スロットの末尾に余りがある");
        return kinds;
    }

    @Test
    void slotTooltipDisplay775To776KindShift() {
        // tooltip_display（18）の隠す種別は KIND 表で付け替える。
        // 77 は同一、78（lock）→79、79（container_loot）→80、80（break_sound）→81。
        byte[] payload = slotPayload(1, 1,
                new int[]{18},
                new byte[][]{tooltipDisplayData(false, new int[]{77, 78, 79, 80})},
                new int[0]);
        Writer writer = new Writer();
        ChunkTranslator.translateSlot775To776(new Reader(payload), writer);
        boolean[] hide = new boolean[1];
        assertArrayEquals(new int[]{77, 79, 80, 81}, readSlotTooltip(writer.bytes(), hide));
        assertEquals(false, hide[0]);
    }

    @Test
    void slotTooltipDisplay776To775DropsSulfurCubeContent() {
        // 776 の 78（sulfur_cube_content）は 775 に無いため落とす。
        // 79（lock）→78、80（container_loot）→79。
        byte[] payload = slotPayload(1, 1,
                new int[]{18},
                new byte[][]{tooltipDisplayData(true, new int[]{78, 79, 80})},
                new int[0]);
        Writer writer = new Writer();
        ChunkTranslator.translateSlot776To775(new Reader(payload), writer);
        boolean[] hide = new boolean[1];
        assertArrayEquals(new int[]{78, 79}, readSlotTooltip(writer.bytes(), hide));
        assertEquals(true, hide[0]);
    }

    @Test
    void slotRemovedKinds775To776Shift() {
        byte[] payload = slotPayload(1, 1, new int[0], new byte[0][], new int[]{78, 79});
        Writer writer = new Writer();
        ChunkTranslator.translateSlot775To776(new Reader(payload), writer);
        assertArrayEquals(new int[]{79, 80}, readSlotRemoved(writer.bytes()));
    }

    @Test
    void slotRemovedKinds776To775DropSulfurCubeContent() {
        byte[] payload = slotPayload(1, 1, new int[0], new byte[0][], new int[]{78, 79});
        Writer writer = new Writer();
        ChunkTranslator.translateSlot776To775(new Reader(payload), writer);
        assertArrayEquals(new int[]{78}, readSlotRemoved(writer.bytes()));
    }

    @Test
    void slotSulfurCubeContent776To775Dropped() {
        // sulfur_cube_content（776=78）のデータは ItemStackTemplate 1個。
        // 775 に無いため成分ごと落とし、added は0件になる。
        byte[] payload = slotPayload(1, 1,
                new int[]{78},
                new byte[][]{templateData(1, 1)},
                new int[0]);
        Writer writer = new Writer();
        ChunkTranslator.translateSlot776To775(new Reader(payload), writer);
        Reader reader = new Reader(writer.bytes());
        assertEquals(1, reader.readVarInt()); // count
        assertEquals(1, reader.readVarInt()); // itemId（stone は同一）
        assertEquals(0, reader.readVarInt()); // added は0件
        assertEquals(0, reader.readVarInt()); // removed は0件
        assertEquals(0, reader.remaining());
    }

    // ---- World Particles ----

    /** World Particles ペイロード（particleId＋データは呼び出し側で付ける）。 */
    private static byte[] worldParticlesPayload(int particleId, byte[] particleData) {
        Writer writer = new Writer();
        writer.writeBoolean(false); // longDistance
        writer.writeBoolean(true); // alwaysShow
        writer.writeDouble(1.0);
        writer.writeDouble(2.0);
        writer.writeDouble(3.0);
        writer.writeFloat(0.5f);
        writer.writeFloat(0.25f);
        writer.writeFloat(0.125f);
        writer.writeFloat(1.5f);
        writer.writeInt(3); // count
        writer.writeVarInt(particleId);
        writer.writeBytes(particleData);
        return writer.bytes();
    }

    @Test
    void worldParticles775To776BlockCrumble() {
        // block_crumble は 775=115→776=122。データのブロック状態も付け替える。
        Writer data = new Writer();
        data.writeVarInt(24687); // calcite
        byte[] translated = ChunkTranslator.translateWorldParticles775To776(
                worldParticlesPayload(115, data.bytes()));
        Reader reader = new Reader(translated);
        assertEquals(false, reader.readBoolean());
        assertEquals(true, reader.readBoolean());
        reader.readDouble();
        reader.readDouble();
        reader.readDouble();
        reader.readFloat();
        reader.readFloat();
        reader.readFloat();
        reader.readFloat();
        assertEquals(3, reader.readInt());
        assertEquals(122, reader.readVarInt());
        assertEquals(27160, reader.readVarInt());
        assertEquals(0, reader.remaining());
    }

    @Test
    void worldParticles776To775GeyserFallsBackToEntityEffect() {
        // geyser（776=7）は entity_effect（775=21）にフォールバックし、
        // 色の i32 として 0 を合成する。waterBlocks（12345）は落とす。
        Writer data = new Writer();
        data.writeInt(12345);
        byte[] translated = ChunkTranslator.translateWorldParticles776To775(
                worldParticlesPayload(7, data.bytes()));
        Reader reader = new Reader(translated);
        assertEquals(false, reader.readBoolean());
        assertEquals(true, reader.readBoolean());
        reader.readDouble();
        reader.readDouble();
        reader.readDouble();
        reader.readFloat();
        reader.readFloat();
        reader.readFloat();
        reader.readFloat();
        assertEquals(3, reader.readInt());
        assertEquals(21, reader.readVarInt());
        assertEquals(0, reader.readInt());
        assertEquals(0, reader.remaining());
    }

    @Test
    void worldParticles776To775SimpleNewParticlesFallBackToEntityEffect() {
        // データ無しの新規粒子（sulfur_bubbles 4、sulfur_cube_goo 124）も
        // entity_effect（21）にフォールバックし、色 0 を合成する。
        for (int particleId : new int[]{4, 124}) {
            byte[] translated = ChunkTranslator.translateWorldParticles776To775(
                    worldParticlesPayload(particleId, new byte[0]));
            Reader reader = new Reader(translated);
            assertEquals(false, reader.readBoolean());
            assertEquals(true, reader.readBoolean());
            reader.readDouble();
            reader.readDouble();
            reader.readDouble();
            reader.readFloat();
            reader.readFloat();
            reader.readFloat();
            reader.readFloat();
            assertEquals(3, reader.readInt());
            assertEquals(21, reader.readVarInt());
            assertEquals(0, reader.readInt());
            assertEquals(0, reader.remaining(), "particleId=" + particleId);
        }
    }

    @Test
    void worldParticles776To775TrailShift() {
        // trail は 776=56→775=49。データ（target vec3f64＋色 i32＋duration）は同一形式。
        Writer data = new Writer();
        data.writeDouble(1.0);
        data.writeDouble(2.0);
        data.writeDouble(3.0);
        data.writeInt(7);
        data.writeVarInt(9);
        byte[] translated = ChunkTranslator.translateWorldParticles776To775(
                worldParticlesPayload(56, data.bytes()));
        Reader reader = new Reader(translated);
        assertEquals(false, reader.readBoolean());
        assertEquals(true, reader.readBoolean());
        reader.readDouble();
        reader.readDouble();
        reader.readDouble();
        reader.readFloat();
        reader.readFloat();
        reader.readFloat();
        reader.readFloat();
        assertEquals(3, reader.readInt());
        assertEquals(49, reader.readVarInt());
        assertEquals(1.0, reader.readDouble());
        assertEquals(2.0, reader.readDouble());
        assertEquals(3.0, reader.readDouble());
        assertEquals(7, reader.readInt());
        assertEquals(9, reader.readVarInt());
        assertEquals(0, reader.remaining());
    }

    // ---- Spawn Entity ----

    /** Spawn Entity ペイロード（uuid は 16 バイトの 0）。 */
    private static byte[] spawnEntityPayload(int entityId, int type, double x, double y, double z,
                                             int objectData) {
        Writer writer = new Writer();
        writer.writeVarInt(entityId);
        writer.writeBytes(new byte[16]);
        writer.writeVarInt(type);
        writer.writeDouble(x);
        writer.writeDouble(y);
        writer.writeDouble(z);
        writer.writeByte(10); // pitch
        writer.writeByte(20); // yaw
        writer.writeByte(30); // headPitch
        writer.writeVarInt(objectData);
        writer.writeShort(100); // velX
        writer.writeShort(200); // velY
        writer.writeShort(300); // velZ
        return writer.bytes();
    }

    private static void assertSpawnEntity(byte[] payload, int expectedType, int expectedObjectData) {
        Reader reader = new Reader(payload);
        assertEquals(7, reader.readVarInt());
        reader.readBytes(16);
        assertEquals(expectedType, reader.readVarInt());
        assertEquals(10.5, reader.readDouble());
        assertEquals(64.0, reader.readDouble());
        assertEquals(-3.25, reader.readDouble());
        assertEquals(10, reader.readByte());
        assertEquals(20, reader.readByte());
        assertEquals(30, reader.readByte());
        assertEquals(expectedObjectData, reader.readVarInt());
        assertEquals(100, reader.readShort());
        assertEquals(200, reader.readShort());
        assertEquals(300, reader.readShort());
        assertEquals(0, reader.remaining());
    }

    @Test
    void spawnEntity775To776SulfurCubeBoundary() {
        // tadpole は 775=130→776=131。strider（129）は同一。
        assertSpawnEntity(ChunkTranslator.translateSpawnEntity775To776(
                spawnEntityPayload(7, 130, 10.5, 64.0, -3.25, 0)), 131, 0);
        assertSpawnEntity(ChunkTranslator.translateSpawnEntity775To776(
                spawnEntityPayload(7, 129, 10.5, 64.0, -3.25, 0)), 129, 0);
        // falling_block（51）は objectData のブロック状態も付け替える。
        assertSpawnEntity(ChunkTranslator.translateSpawnEntity775To776(
                spawnEntityPayload(7, 51, 10.5, 64.0, -3.25, 24687)), 51, 27160);
    }

    @Test
    void spawnEntity776To775SulfurCubeFallsBackToPig() {
        // sulfur_cube（776=130）は 775 では豚（100）にフォールバックする。
        assertSpawnEntity(ChunkTranslator.translateSpawnEntity776To775(
                spawnEntityPayload(7, 130, 10.5, 64.0, -3.25, 0)), 100, 0);
        // tadpole は 776=131→775=130。
        assertSpawnEntity(ChunkTranslator.translateSpawnEntity776To775(
                spawnEntityPayload(7, 131, 10.5, 64.0, -3.25, 0)), 130, 0);
    }

    // ---- Entity Metadata ----

    /** Entity Metadata ペイロード（entityId・(index, type, 値) の列・0xFF 終端）。 */
    private static byte[] entityMetadataPayload(int entityId, int[][] entries) {
        Writer writer = new Writer();
        writer.writeVarInt(entityId);
        for (int[] entry : entries) {
            writer.writeUnsignedByte(entry[0]); // index
            writer.writeVarInt(entry[1]); // type
            switch (entry[1]) {
                case 0 -> writer.writeByte(entry[2]); // byte
                case 7 -> { // slot（count・itemId・added・removed）
                    writer.writeVarInt(1);
                    writer.writeVarInt(entry[2]);
                    writer.writeVarInt(0);
                    writer.writeVarInt(0);
                }
                case 14 -> writer.writeVarInt(entry[2]); // blockstate
                default -> throw new IllegalArgumentException("type=" + entry[1]);
            }
        }
        writer.writeUnsignedByte(0xFF);
        return writer.bytes();
    }

    private static void assertEntityMetadata(byte[] payload, int[][] expected) {
        Reader reader = new Reader(payload);
        assertEquals(5, reader.readVarInt());
        for (int[] entry : expected) {
            assertEquals(entry[0], reader.readUnsignedByte());
            assertEquals(entry[1], reader.readVarInt());
            switch (entry[1]) {
                case 0 -> assertEquals(entry[2], reader.readByte());
                case 7 -> {
                    assertEquals(1, reader.readVarInt());
                    assertEquals(entry[2], reader.readVarInt());
                    assertEquals(0, reader.readVarInt());
                    assertEquals(0, reader.readVarInt());
                }
                case 14 -> assertEquals(entry[2], reader.readVarInt());
                default -> throw new IllegalArgumentException("type=" + entry[1]);
            }
        }
        assertEquals(0xFF, reader.readUnsignedByte());
        assertEquals(0, reader.remaining());
    }

    @Test
    void entityMetadata775To776SlotAndBlockstate() {
        // 型番号は 775・776 で同一（恒等写像）。スロットの石は同一、
        // ブロック状態 24687→27160。
        byte[] translated = ChunkTranslator.translateEntityMetadata775To776(
                entityMetadataPayload(5, new int[][]{
                        {0, 0, 5}, {1, 7, 1}, {2, 14, 24687}}));
        assertEntityMetadata(translated, new int[][]{
                {0, 0, 5}, {1, 7, 1}, {2, 14, 27160}});
    }

    @Test
    void entityMetadata776To775SlotAndBlockstate() {
        byte[] translated = ChunkTranslator.translateEntityMetadata776To775(
                entityMetadataPayload(5, new int[][]{
                        {0, 0, 5}, {1, 7, 1}, {2, 14, 27160}}));
        assertEntityMetadata(translated, new int[][]{
                {0, 0, 5}, {1, 7, 1}, {2, 14, 24687}});
    }

    /** variant 系・global_pos・painting のメタデータ（775→776）。型番号は同一のため素通し。 */
    private static byte[] variantMetadataPayload() {
        Writer writer = new Writer();
        writer.writeVarInt(5); // entityId
        writer.writeUnsignedByte(3);
        writer.writeVarInt(22); // cat_sound：varint
        writer.writeVarInt(7);
        writer.writeUnsignedByte(4);
        writer.writeVarInt(24); // cow_sound：varint
        writer.writeVarInt(9);
        writer.writeUnsignedByte(5);
        writer.writeVarInt(25); // wolf_variant：Holder（varint）
        writer.writeVarInt(3);
        writer.writeUnsignedByte(6);
        writer.writeVarInt(29); // pig_sound：varint
        writer.writeVarInt(11);
        writer.writeUnsignedByte(7);
        writer.writeVarInt(31); // chicken_sound：varint
        writer.writeVarInt(13);
        writer.writeUnsignedByte(8);
        writer.writeVarInt(32); // zombie_nautilus_variant：varint
        writer.writeVarInt(2);
        writer.writeUnsignedByte(9);
        writer.writeVarInt(33); // optional_global_pos
        writer.writeBoolean(true);
        writer.writeString("minecraft:overworld");
        writer.writePositionRaw(0x123456789ABCDEFL);
        writer.writeUnsignedByte(10);
        writer.writeVarInt(34); // painting_variant：Holder（varint）
        writer.writeVarInt(5);
        writer.writeUnsignedByte(0xFF);
        return writer.bytes();
    }

    private static void assertVariantMetadata(byte[] payload) {
        Reader reader = new Reader(payload);
        assertEquals(5, reader.readVarInt());
        assertEquals(3, reader.readUnsignedByte());
        assertEquals(22, reader.readVarInt());
        assertEquals(7, reader.readVarInt());
        assertEquals(4, reader.readUnsignedByte());
        assertEquals(24, reader.readVarInt());
        assertEquals(9, reader.readVarInt());
        assertEquals(5, reader.readUnsignedByte());
        assertEquals(25, reader.readVarInt());
        assertEquals(3, reader.readVarInt());
        assertEquals(6, reader.readUnsignedByte());
        assertEquals(29, reader.readVarInt());
        assertEquals(11, reader.readVarInt());
        assertEquals(7, reader.readUnsignedByte());
        assertEquals(31, reader.readVarInt());
        assertEquals(13, reader.readVarInt());
        assertEquals(8, reader.readUnsignedByte());
        assertEquals(32, reader.readVarInt());
        assertEquals(2, reader.readVarInt());
        assertEquals(9, reader.readUnsignedByte());
        assertEquals(33, reader.readVarInt());
        assertEquals(true, reader.readBoolean());
        assertEquals("minecraft:overworld", reader.readString());
        assertEquals(0x123456789ABCDEFL, reader.readPositionRaw());
        assertEquals(10, reader.readUnsignedByte());
        assertEquals(34, reader.readVarInt());
        assertEquals(5, reader.readVarInt());
        assertEquals(0xFF, reader.readUnsignedByte());
        assertEquals(0, reader.remaining());
    }

    @Test
    void entityMetadata775To776VariantTypes() {
        // 774 派生では 24 が wolf holder・32 が global_pos・33 が painting と
        // ずれ、22・25・31・34 が欠落してコンパイルも通らなかった
        // （ChunkTranslator.java:45040 の duplicate case label）。
        // 775 派生に書き換えたため、型番号も値もそのまま写る。
        assertVariantMetadata(ChunkTranslator.translateEntityMetadata775To776(
                variantMetadataPayload()));
    }

    @Test
    void entityMetadata776To775VariantTypes() {
        assertVariantMetadata(ChunkTranslator.translateEntityMetadata776To775(
                variantMetadataPayload()));
    }

    /** スロットの added 成分を (kind, varint値) または (kind, Holder値) で読む。 */
    private static class SlotComponent {
        int kind;
        int varint;
        String holderString; // Holder の直書き文字列（無ければ null）
    }

    private static SlotComponent[] readSlotComponents(byte[] payload) {
        Reader reader = new Reader(payload);
        assertEquals(1, reader.readVarInt()); // count
        assertEquals(1, reader.readVarInt()); // itemId（stone は同一）
        int added = reader.readVarInt();
        SlotComponent[] out = new SlotComponent[added];
        for (int i = 0; i < added; i++) {
            SlotComponent component = new SlotComponent();
            component.kind = reader.readVarInt();
            if (component.kind == 98 || component.kind == 99) {
                // chicken の sound_variant（Holder。0 なら直書きの文字列）。
                component.varint = reader.readVarInt();
                if (component.varint == 0) {
                    component.holderString = reader.readString();
                }
            } else {
                component.varint = reader.readVarInt();
            }
            out[i] = component;
        }
        assertEquals(0, reader.readVarInt()); // removed は0件
        assertEquals(0, reader.remaining(), "スロットの末尾に余りがある");
        return out;
    }

    @Test
    void slotNewKinds775To776Shift() {
        // 775 の新規 6 種（41, 43, 94, 96, 98, 106）は 776 でも同じ成分。
        // 78 の挿入で 94→95、96→97、98→99、106→107 にずれる。
        Writer data = new Writer();
        data.writeVarInt(0); // Holder：直書き
        data.writeString("minecraft:chicken_sound_x");
        byte[] payload = slotPayload(1, 1,
                new int[]{41, 43, 94, 96, 98, 106},
                new byte[][]{varintBytes(7), varintBytes(9), varintBytes(11),
                        varintBytes(13), data.bytes(), varintBytes(15)},
                new int[0]);
        SlotComponent[] out = readSlotComponents(
                translateSlotToBytes(payload, true));
        assertEquals(6, out.length);
        assertEquals(41, out[0].kind);
        assertEquals(7, out[0].varint);
        assertEquals(43, out[1].kind);
        assertEquals(9, out[1].varint);
        assertEquals(95, out[2].kind);
        assertEquals(11, out[2].varint);
        assertEquals(97, out[3].kind);
        assertEquals(13, out[3].varint);
        assertEquals(99, out[4].kind);
        assertEquals(0, out[4].varint);
        assertEquals("minecraft:chicken_sound_x", out[4].holderString);
        assertEquals(107, out[5].kind);
        assertEquals(15, out[5].varint);
    }

    @Test
    void slotNewKinds776To775Shift() {
        // 逆向き。774 派生ではこの 6 種が欠落し、default の
        // copyComponentOpaque776 に落ちて例外になっていた。
        Writer data = new Writer();
        data.writeVarInt(0); // Holder：直書き
        data.writeString("minecraft:chicken_sound_x");
        byte[] payload = slotPayload(1, 1,
                new int[]{41, 43, 95, 97, 99, 107},
                new byte[][]{varintBytes(7), varintBytes(9), varintBytes(11),
                        varintBytes(13), data.bytes(), varintBytes(15)},
                new int[0]);
        SlotComponent[] out = readSlotComponents(
                translateSlotToBytes(payload, false));
        assertEquals(6, out.length);
        assertEquals(41, out[0].kind);
        assertEquals(7, out[0].varint);
        assertEquals(43, out[1].kind);
        assertEquals(9, out[1].varint);
        assertEquals(94, out[2].kind);
        assertEquals(11, out[2].varint);
        assertEquals(96, out[3].kind);
        assertEquals(13, out[3].varint);
        assertEquals(98, out[4].kind);
        assertEquals(0, out[4].varint);
        assertEquals("minecraft:chicken_sound_x", out[4].holderString);
        assertEquals(106, out[5].kind);
        assertEquals(15, out[5].varint);
    }

    private static byte[] varintBytes(int value) {
        Writer writer = new Writer();
        writer.writeVarInt(value);
        return writer.bytes();
    }

    private static byte[] translateSlotToBytes(byte[] payload, boolean up) {
        Writer writer = new Writer();
        if (up) {
            ChunkTranslator.translateSlot775To776(new Reader(payload), writer);
        } else {
            ChunkTranslator.translateSlot776To775(new Reader(payload), writer);
        }
        return writer.bytes();
    }

    // ---- RegistryRemapper の抜き打ち ----

    @Test
    void remapperSpotChecks() {
        assertEquals(27160, RegistryRemapper.mapBlockState775To776(24687)); // calcite
        assertEquals(24687, RegistryRemapper.mapBlockStateUp776(27160));
        assertEquals(0, RegistryRemapper.mapBlockState775To776(0)); // air は同一
        assertEquals(32365, RegistryRemapper.mapBlockState775To776(29872)); // firefly_bush
        assertEquals(131, RegistryRemapper.mapEntityType775To776(130)); // tadpole
        assertEquals(129, RegistryRemapper.mapEntityType775To776(129)); // strider は同一
        assertEquals(100, RegistryRemapper.mapEntityTypeUp776(130)); // sulfur_cube は豚へ
        assertEquals(130, RegistryRemapper.mapEntityTypeUp776(131)); // tadpole
        assertEquals(RegistryRemapper.DROP, RegistryRemapper.mapBlockEntityType775To776(25)); // bed
        assertEquals(25, RegistryRemapper.mapBlockEntityType775To776(26)); // conduit（bed=25 の削除で 1 つ前にずれる）
        assertEquals(RegistryRemapper.DROP, RegistryRemapper.mapBlockEntityTypeUp776(48)); // potent_sulfur
        assertEquals(26, RegistryRemapper.mapBlockEntityTypeUp776(25)); // 776=25 は 775 の conduit
        assertEquals(21, RegistryRemapper.mapParticleUp776(7)); // geyser は entity_effect へ
        assertEquals(21, RegistryRemapper.mapParticleUp776(4)); // sulfur_bubbles も同様
        assertEquals(21, RegistryRemapper.mapParticleUp776(124)); // sulfur_cube_goo も同様
        assertEquals(122, RegistryRemapper.mapParticle775To776(115)); // block_crumble
        assertEquals(123, RegistryRemapper.mapParticle775To776(116)); // firefly
        assertEquals(1, RegistryRemapper.mapItemUp776(26)); // sulfur は石へ
        assertEquals(1, RegistryRemapper.mapItem775To776(1)); // stone は同一
        assertEquals(1, RegistryRemapper.mapAttribute775To776(0)); // armor
        assertEquals(7, RegistryRemapper.mapAttribute775To776(5)); // block_break_speed
        assertEquals(39, RegistryRemapper.mapAttribute775To776(34)); // waypoint_receive_range
        assertEquals(RegistryRemapper.DROP, RegistryRemapper.mapAttribute776To775(0)); // air_drag_modifier
        assertEquals(0, RegistryRemapper.mapAttribute776To775(1)); // armor
        assertEquals(5, RegistryRemapper.mapAttribute776To775(7)); // block_break_speed
    }
}
