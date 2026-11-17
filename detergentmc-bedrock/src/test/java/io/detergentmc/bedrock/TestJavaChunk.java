package io.detergentmc.bedrock;

import io.detergentmc.protocol.PacketIO;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * テスト用の Java チャンクビルダー。既知の地形パターンを持つ
 * Chunk Data ペイロード（765 形式、763 は heightmaps が名前付き）を作る。
 * Bedrock 側のデコーダー（{@link BedrockLevelChunkDecoder}）もここに置く。
 */
final class TestJavaChunk {

    private TestJavaChunk() {
    }

    /** ブロック位置 (x, y, z) -> Java のブロック状態ID。 */
    @FunctionalInterface
    interface BlockAt {
        int state(int x, int y, int z);
    }

    /** バイオーム位置 (x, y, z) -> Java のバイオーム登録番号。 */
    @FunctionalInterface
    interface BiomeAt {
        int biome(int x, int y, int z);
    }

    /** 765 形式の Chunk Data を作る（オーバーワールド、minY -64、高さ 384）。 */
    static byte[] build765(int chunkX, int chunkZ, BlockAt blocks, BiomeAt biomes) {
        return build765(chunkX, chunkZ, blocks, biomes, -64, 384, -1);
    }

    /**
     * 765 形式の Chunk Data を作る。{@code directSection} が 0 以上なら
     * そのセクションだけ直接パレット（BPE 15）にする（それ以外は間接）。
     */
    static byte[] build765(int chunkX, int chunkZ, BlockAt blocks, BiomeAt biomes,
                           int minY, int height, int directSection) {
        PacketIO.Writer out = new PacketIO.Writer();
        out.writeInt(chunkX);
        out.writeInt(chunkZ);
        // heightmaps：名前なし NBT の空 compound（0x0A＋0x00）。
        out.writeByte(0x0A);
        out.writeByte(0x00);
        ByteArrayOutputStream sections = new ByteArrayOutputStream();
        int sectionCount = height / 16;
        for (int s = 0; s < sectionCount; s++) {
            int yBase = minY + s * 16;
            int[] blockValues = new int[4096];
            for (int j = 0; j < 4096; j++) {
                int x = j & 15;
                int z = (j >> 4) & 15;
                int yLocal = (j >> 8) & 15;
                blockValues[j] = blocks.state(x, yBase + yLocal, z);
            }
            int nonAir = 0;
            for (int value : blockValues) {
                if (value != 0) {
                    nonAir++;
                }
            }
            writeShort(sections, nonAir);
            boolean direct = s == directSection;
            writePalettedContainer(sections, blockValues, true, direct);

            int[] biomeValues = new int[64];
            for (int j = 0; j < 64; j++) {
                int x = j & 3;
                int z = (j >> 2) & 3;
                int yLocal = (j >> 4) & 3;
                biomeValues[j] = biomes.biome(x * 4, yBase + yLocal * 4, z * 4);
            }
            writePalettedContainer(sections, biomeValues, false, false);
        }
        byte[] sectionBytes = sections.toByteArray();
        out.writeVarInt(sectionBytes.length);
        out.writeBytes(sectionBytes);
        out.writeVarInt(0); // block entity: なし
        return out.bytes();
    }

    /** 763 形式の Chunk Data を作る（heightmaps が名前付き NBT）。 */
    static byte[] build763(int chunkX, int chunkZ, BlockAt blocks, BiomeAt biomes) {
        PacketIO.Writer out = new PacketIO.Writer();
        out.writeInt(chunkX);
        out.writeInt(chunkZ);
        // heightmaps：名前付き NBT の空 compound（型 0x0A＋名前長 2バイト 0＋0x00）。
        out.writeByte(0x0A);
        out.writeByte(0x00);
        out.writeByte(0x00);
        out.writeByte(0x00);
        ByteArrayOutputStream sections = new ByteArrayOutputStream();
        for (int s = 0; s < 24; s++) {
            int yBase = -64 + s * 16;
            int[] blockValues = new int[4096];
            for (int j = 0; j < 4096; j++) {
                int x = j & 15;
                int z = (j >> 4) & 15;
                int yLocal = (j >> 8) & 15;
                blockValues[j] = blocks.state(x, yBase + yLocal, z);
            }
            int nonAir = 0;
            for (int value : blockValues) {
                if (value != 0) {
                    nonAir++;
                }
            }
            writeShort(sections, nonAir);
            writePalettedContainer(sections, blockValues, true, false);
            int[] biomeValues = new int[64];
            for (int j = 0; j < 64; j++) {
                int x = j & 3;
                int z = (j >> 2) & 3;
                int yLocal = (j >> 4) & 3;
                biomeValues[j] = biomes.biome(x * 4, yBase + yLocal * 4, z * 4);
            }
            writePalettedContainer(sections, biomeValues, false, false);
        }
        byte[] sectionBytes = sections.toByteArray();
        out.writeVarInt(sectionBytes.length);
        out.writeBytes(sectionBytes);
        out.writeVarInt(0); // block entity: なし
        return out.bytes();
    }

    private static void writeShort(ByteArrayOutputStream out, int value) {
        out.write((value >> 8) & 0xFF);
        out.write(value & 0xFF);
    }

    private static void writeVarInt(ByteArrayOutputStream out, int value) {
        int remaining = value;
        while (true) {
            if ((remaining & ~0x7F) == 0) {
                out.write(remaining);
                return;
            }
            out.write((remaining & 0x7F) | 0x80);
            remaining >>>= 7;
        }
    }

    private static void writeLong(ByteArrayOutputStream out, long value) {
        for (int i = 7; i >= 0; i--) {
            out.write((int) (value >> (i * 8)) & 0xFF);
        }
    }

    /**
     * エントリ列をバニラの BitStorage と同じ詰め方で long 列にする。
     * 1 long あたり 64 / bits 個を詰め、long の境界をまたがない。
     * 2026-11-14 に「連続詰め」に変えたが、実バニラ 1.20.1 のワイヤ
     * （b=5 で 342 long）が旧式であることを示したため旧式に戻す
     * （2026-11-15、Bedrock マトリクス 9 件失敗の調査）。
     */
    private static long[] packPerLong(int[] entries, int bits) {
        int perLong = 64 / bits;
        long[] longs = new long[(entries.length + perLong - 1) / perLong];
        long mask = (1L << bits) - 1;
        for (int i = 0; i < entries.length; i++) {
            int cellIndex = i / perLong;
            int bitIndex = (i % perLong) * bits;
            longs[cellIndex] |= ((long) entries[i] & mask) << bitIndex;
        }
        return longs;
    }

    /** Java のパレット付きコンテナ 1 個を書く。 */
    private static void writePalettedContainer(ByteArrayOutputStream out, int[] values,
                                               boolean blocks, boolean direct) {
        Map<Integer, Integer> palette = new LinkedHashMap<>();
        for (int value : values) {
            palette.putIfAbsent(value, palette.size());
        }
        int entryCount = values.length;
        if (palette.size() == 1) {
            out.write(0); // BPE 0（シングル）
            writeVarInt(out, palette.keySet().iterator().next());
            writeVarInt(out, 0); // data array 長 0
            return;
        }
        if (direct) {
            int bits = blocks ? 15 : 4;
            out.write(bits);
            long[] packed = packPerLong(values, bits);
            writeVarInt(out, packed.length);
            for (long value : packed) {
                writeLong(out, value);
            }
            return;
        }
        int bits = 1;
        while ((1 << bits) < palette.size()) {
            bits++;
        }
        out.write(bits);
        writeVarInt(out, palette.size());
        List<Integer> paletteList = new ArrayList<>(palette.keySet());
        for (int value : paletteList) {
            writeVarInt(out, value);
        }
        int[] indices = new int[entryCount];
        for (int i = 0; i < entryCount; i++) {
            indices[i] = palette.get(values[i]);
        }
        long[] packed = packPerLong(indices, bits);
        writeVarInt(out, packed.length);
        for (long value : packed) {
            writeLong(out, value);
        }
    }
}
