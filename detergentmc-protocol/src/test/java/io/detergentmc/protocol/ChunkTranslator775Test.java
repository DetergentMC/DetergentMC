package io.detergentmc.protocol;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import io.detergentmc.protocol.PacketIO.Reader;
import io.detergentmc.protocol.PacketIO.Writer;
import org.junit.jupiter.api.Test;

/**
 * 774↔775のチャンク変換で、BitStorage の詰め方を検証するテスト。
 *
 * <p>バニラの BitStorage はエントリを long の境界をまたいで連続して詰める
 * ため、Data Array の long 数は (entryCount * bits + 63) / 64 である。
 * 旧式の (entryCount + perLong - 1) / perLong（perLong = 64 / bits）は
 * bits が 64 を割り切らないとき（3・5・6・7・9…）に多めに数え、後続の
 * コンテナがずれて読めなくなる。2026-11-14、Bedrockマトリクス26.1で
 * 3 ビットのバイオーム（正しくは 3 long、旧式は 4 long 読んだ）で発覚し、
 * ChunkTranslator（34箇所の long 数計算＋22個の直接パレット変換）と
 * BedrockChunkTranslator を修正した。このテストはその修正を直接検証する。
 * 旧式のままだと全テストが失敗する（間接は long 数がずれて残りバイトが
 * 合わず、直接はまたぐエントリの上位ビットが落ちて値が変わる）。
 */
class ChunkTranslator775Test {

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

    /** 間接パレットのセクション1個（774／775形式。Data Array に長さ varint が無い）。 */
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

    /** チャンク Data ペイロード（774／775共通の形）。セクションは1個。 */
    private static byte[] chunkDataPayload(byte[] section) {
        Writer payload = new Writer();
        payload.writeInt(3);
        payload.writeInt(-2);
        writeHeightmapArray(payload);
        payload.writeByteArray(section);
        payload.writeVarInt(0); // ブロックエンティティは0件
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

    /** 変換後のペイロード全体を読み、セクション部分を返す。 */
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

    // ---- 間接パレット（ブロック 5 ビット・バイオーム 3 ビット） ----

    @Test
    void chunkData774To775FiveBitBlocksAndThreeBitBiomes() {
        // ブロック状態は 774→775 でずれるものを含む（17種）。ずれる値が入ることで、
        // 変換が素通し（失敗時の passthrough）だった場合にテストが失敗する。
        // 774→775 のブロック状態のずれ（ViaVersion mapping・実バニラ codec で確認）：
        // 775側に挿入されるのはトランペットの音色 200 状態（775側 1381〜1580）、
        // golden_dandelion（775側 2322）、potted_golden_dandelion（775側 10642）。
        // 774側の番号で書くと、1381 未満は同一、1381〜2121 は +200、
        // 2122〜10440 は +201、10441 以上は +202（挿入位置は775側の番号で
        // 比べる。2122→2322 は golden_dandelion の手前ではなく後ろになる）。
        int[] blockPalette = new int[]{0, 1, 2, 1380, 1381, 1400, 2000, 2321,
                2322, 5000, 10641, 10642, 15000, 20000, 25000, 29000, 29670};
        int[] expectedPalette = new int[]{0, 1, 2, 1380, 1581, 1600, 2200, 2522,
                2523, 5201, 10843, 10844, 15202, 20202, 25202, 29202, 29872};
        int[] blockIndices = new int[4096];
        for (int i = 0; i < 4096; i++) {
            blockIndices[i] = i % 17;
        }
        int[] biomePalette = new int[]{0, 1, 2, 3, 4};
        int[] biomeIndices = new int[64];
        for (int i = 0; i < 64; i++) {
            biomeIndices[i] = i % 5;
        }
        byte[] translated = ChunkTranslator.translateChunkData774To775(
                chunkDataPayload(indirectSection(5, blockPalette, blockIndices,
                        3, biomePalette, biomeIndices)));
        ParsedSection out = readSection(readSections(translated));
        assertEquals(5, out.blockBits);
        assertArrayEquals(expectedPalette, out.blockPalette);
        // 間接パレットの Data Array はインデックス列なので中身は変わらない。
        assertArrayEquals(packEntries(blockIndices, 5), out.blockData);
        assertEquals(3, out.biomeBits);
        assertArrayEquals(biomePalette, out.biomePalette);
        assertArrayEquals(packEntries(biomeIndices, 3), out.biomeData);
    }

    @Test
    void chunkData775To774FiveBitBlocksAndThreeBitBiomes() {
        // 775→774 は上記の逆向き。パレットにずれる値を入れて素通しを検出する。
        int[] blockPalette = new int[]{0, 1, 2, 1380, 1581, 1600, 2200, 2522,
                2523, 5201, 10843, 10844, 15202, 20202, 25202, 29202, 29872};
        int[] expectedPalette = new int[]{0, 1, 2, 1380, 1381, 1400, 2000, 2321,
                2322, 5000, 10641, 10642, 15000, 20000, 25000, 29000, 29670};
        int[] blockIndices = new int[4096];
        for (int i = 0; i < 4096; i++) {
            blockIndices[i] = (i * 7) % 17;
        }
        int[] biomePalette = new int[]{0, 1, 2, 3, 4};
        int[] biomeIndices = new int[64];
        for (int i = 0; i < 64; i++) {
            biomeIndices[i] = (i * 3) % 5;
        }
        byte[] translated = ChunkTranslator.translateChunkData775To774(
                chunkDataPayload(indirectSection(5, blockPalette, blockIndices,
                        3, biomePalette, biomeIndices)));
        ParsedSection out = readSection(readSections(translated));
        assertEquals(5, out.blockBits);
        assertArrayEquals(expectedPalette, out.blockPalette);
        assertArrayEquals(packEntries(blockIndices, 5), out.blockData);
        assertEquals(3, out.biomeBits);
        assertArrayEquals(biomePalette, out.biomePalette);
        assertArrayEquals(packEntries(biomeIndices, 3), out.biomeData);
    }

    /** 774のブロック状態を775に写す期待値（テスト側の独立したルール）。 */
    private static int mapBlockState774To775Expected(int value) {
        if (value < 1381) {
            return value;
        }
        if (value < 2122) {
            return value + 200;
        }
        if (value < 10441) {
            return value + 201;
        }
        return value + 202;
    }

    // ---- 直接パレット（ブロック 15 ビット） ----

    @Test
    void chunkData774To775DirectPaletteFifteenBit() {
        // ブロック状態 1000〜28999（1381以上は774→775でずれる）。15 ビットは
        // 64 を割り切らないため、またぐエントリが 4096 個中 767 個ある。
        int[] blockStates = new int[4096];
        for (int i = 0; i < 4096; i++) {
            blockStates[i] = 1000 + (i * 37) % 28000;
        }
        int[] expected = new int[4096];
        for (int i = 0; i < 4096; i++) {
            expected[i] = mapBlockState774To775Expected(blockStates[i]);
        }
        byte[] translated = ChunkTranslator.translateChunkData774To775(
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
    void chunkData775To774DirectPaletteFifteenBit() {
        // 775の状態は774の状態を上記のルールで写したもの（775にしか無い状態は
        // 入れない）。逆向きで元の774の状態に戻るはず。
        int[] blockStates = new int[4096];
        int[] expected = new int[4096];
        for (int i = 0; i < 4096; i++) {
            expected[i] = 1000 + (i * 101) % 28000;
            blockStates[i] = mapBlockState774To775Expected(expected[i]);
        }
        byte[] translated = ChunkTranslator.translateChunkData775To774(
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
}
