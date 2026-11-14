package io.detergentmc.bedrock;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

/**
 * Bedrock の LevelChunk ペイロード（サブチャンク列＋バイオーム列＋末尾）の
 * テスト用デコーダー。Bedrock の公開形式（Minecraft Wiki「Pocket Edition
 * level format」／wiki.vg「Bedrock Edition level format」）を翻訳コードとは
 * 別に実装し、翻訳の正しさを独立に確認するためのもの。
 */
final class BedrockLevelChunkDecoder {

    private BedrockLevelChunkDecoder() {
    }

    /** デコード結果。ブロックは Bedrock の XZY 順（Y が最も速い）で保持する。 */
    static final class Decoded {
        /** サブチャンク数。 */
        int subChunkCount;
        /** サブチャンクごとの Y インデックス。 */
        final List<Integer> subChunkY = new ArrayList<>();
        /** サブチャンクごとの層数。 */
        final List<Integer> layerCounts = new ArrayList<>();
        /** [section][layer][4096] ランタイムID（Bedrock インデックス順）。 */
        final List<List<int[]>> blocks = new ArrayList<>();
        /** [section][64] バイオームID（Bedrock インデックス順）。 */
        final List<int[]> biomes = new ArrayList<>();
        /** ボーダーブロック数。 */
        int borderBlockCount;
        /** ブロックエンティティ数。 */
        int blockEntityCount;
        /** ペイロードを読み切ったかどうか。 */
        boolean fullyConsumed;

        /** ブロック（x, yLocal, z はセクション内の 0〜15）。 */
        int blockAt(int section, int x, int yLocal, int z, int layer) {
            int bedrockIndex = yLocal | (z << 4) | (x << 8);
            return blocks.get(section).get(layer)[bedrockIndex];
        }

        /** バイオーム（bx, by, bz は 0〜3 のセル座標）。 */
        int biomeAt(int section, int bx, int by, int bz) {
            int bedrockIndex = by | (bz << 2) | (bx << 4);
            return biomes.get(section)[bedrockIndex];
        }
    }

    /** TranslatedChunk の payload をデコードする。 */
    static Decoded decode(byte[] payload, int subChunkCount) {
        ByteBuffer in = ByteBuffer.wrap(payload).order(ByteOrder.LITTLE_ENDIAN);
        Decoded out = new Decoded();
        out.subChunkCount = subChunkCount;
        for (int s = 0; s < subChunkCount; s++) {
            int version = readByte(in);
            if (version != BedrockChunkTranslator.SUB_CHUNK_VERSION) {
                throw new IllegalArgumentException(
                        "サブチャンクのバージョンが 9 ではありません: " + version);
            }
            int layerCount = readByte(in);
            int yIndex = (byte) readByte(in);
            out.subChunkY.add(yIndex);
            out.layerCounts.add(layerCount);
            List<int[]> layers = new ArrayList<>(layerCount);
            for (int layer = 0; layer < layerCount; layer++) {
                layers.add(readStorage(in, 4096));
            }
            out.blocks.add(layers);
        }
        for (int s = 0; s < subChunkCount; s++) {
            out.biomes.add(readStorage(in, 64));
        }
        out.borderBlockCount = (int) readUnsignedVarInt(in);
        out.blockEntityCount = (int) readUnsignedVarInt(in);
        out.fullyConsumed = !in.hasRemaining();
        return out;
    }

    /** パレット付きストレージ 1 個を読む（エントリ数は 4096 か 64）。 */
    private static int[] readStorage(ByteBuffer in, int entryCount) {
        int header = readByte(in);
        int bits = header >> 1;
        boolean persistent = (header & 1) != 0;
        if (persistent) {
            throw new IllegalArgumentException("永続パレット（NBT）は未対応です。");
        }
        int paletteSize = (int) readUnsignedVarInt(in);
        int[] palette = new int[paletteSize];
        for (int i = 0; i < paletteSize; i++) {
            palette[i] = (int) readUnsignedVarInt(in);
        }
        int[] values = new int[entryCount];
        if (bits == 0) {
            for (int i = 0; i < entryCount; i++) {
                values[i] = palette[0];
            }
            return values;
        }
        int perWord = 32 / bits;
        int wordCount = (int) readUnsignedVarInt(in);
        long mask = (1L << bits) - 1;
        int index = 0;
        for (int word = 0; word < wordCount && index < entryCount; word++) {
            long packed = in.getInt() & 0xFFFFFFFFL;
            for (int slot = 0; slot < perWord && index < entryCount; slot++, index++) {
                int paletteIndex = (int) ((packed >>> (slot * bits)) & mask);
                values[index] = palette[paletteIndex];
            }
        }
        if (index != entryCount) {
            throw new IllegalArgumentException(
                    "ストレージのエントリ数が合いません（読めた=" + index + "、想定=" + entryCount + "）。");
        }
        return values;
    }

    private static int readByte(ByteBuffer in) {
        if (!in.hasRemaining()) {
            throw new IllegalArgumentException("Bedrock ペイロードが途中で切れています。");
        }
        return in.get() & 0xFF;
    }

    private static long readUnsignedVarInt(ByteBuffer in) {
        long value = 0;
        int shift = 0;
        while (true) {
            int b = readByte(in);
            value |= (long) (b & 0x7F) << shift;
            if ((b & 0x80) == 0) {
                return value;
            }
            shift += 7;
            if (shift >= 70) {
                throw new IllegalArgumentException("Bedrock VarInt が長すぎます。");
            }
        }
    }
}
