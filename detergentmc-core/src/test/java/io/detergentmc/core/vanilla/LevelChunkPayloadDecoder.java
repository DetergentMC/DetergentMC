package io.detergentmc.core.vanilla;

import io.detergentmc.bedrock.BedrockVarInt;

/**
 * LevelChunk ペイロードの最小デコーダ（テスト用ヘルパー。bedrock モジュールの
 * BedrockLevelChunkDecoder の簡易版で、コアのテストから使えるようにここに置く）。
 * サブチャンクを歩き、使われている Bedrock ランタイム ID を集め、位置指定で
 * ブロックを読めるようにする。
 */
final class LevelChunkPayloadDecoder {
    final java.util.Set<Integer> runtimeIds = new java.util.HashSet<>();
    /** サブチャンクごとの層 0 のブロック（Bedrock 順序 y | z<<4 | x<<8）。 */
    private final java.util.Map<Integer, int[]> sections = new java.util.HashMap<>();
    final int subChunkCount;

    private LevelChunkPayloadDecoder(int subChunkCount) {
        this.subChunkCount = subChunkCount;
    }

    static LevelChunkPayloadDecoder decode(byte[] payload, int subChunkCount) {
        LevelChunkPayloadDecoder result = new LevelChunkPayloadDecoder(subChunkCount);
        BedrockVarInt.ByteReader in = new BedrockVarInt.ByteReader(payload);
        for (int s = 0; s < subChunkCount; s++) {
            int version = in.readUnsignedByte();
            if (version != 9) {
                throw new IllegalArgumentException(
                        "サブチャンクのバージョンが 9 ではありません: " + version);
            }
            int layerCount = in.readUnsignedByte();
            int yIndex = in.readUnsignedByte();
            if (yIndex >= 128) {
                yIndex -= 256; // 1 バイト符号付き（オーバーワールドは -4..19）
            }
            for (int layer = 0; layer < layerCount; layer++) {
                int[] blocks = readStorage(in, 4096, result);
                if (layer == 0) {
                    result.sections.put(yIndex, blocks);
                }
            }
        }
        // バイオーム（値は見ないが、末尾まで歩くために読む）。
        for (int s = 0; s < subChunkCount; s++) {
            readStorage(in, 64, null);
        }
        long borderBlocks = BedrockVarInt.readUnsignedVarInt(in);
        long blockEntities = BedrockVarInt.readUnsignedVarInt(in);
        if (borderBlocks != 0 || blockEntities != 0) {
            throw new IllegalArgumentException(
                    "border/blockEntity が 0 ではありません: " + borderBlocks
                            + "/" + blockEntities);
        }
        if (in.remaining() != 0) {
            throw new IllegalArgumentException(
                    "ペイロードに余分なバイトがあります: " + in.remaining());
        }
        return result;
    }

    /**
     * 指定したサブチャンク Y インデックス・ローカル座標の層 0 ブロックの
     * ランタイム ID を返す。サブチャンクが無ければ空気（11881）。
     */
    int blockAt(int sectionY, int x, int yLocal, int z) {
        int[] blocks = sections.get(sectionY);
        if (blocks == null) {
            return 11881;
        }
        return blocks[yLocal | (z << 4) | (x << 8)];
    }

    /** 1 つのパレット付きストレージを読み、ブロック ID の配列を返す（バイオームは null）。 */
    private static int[] readStorage(BedrockVarInt.ByteReader in, int entries,
            LevelChunkPayloadDecoder collect) {
        int flags = in.readUnsignedByte();
        int bits = flags >> 1;
        boolean persistent = (flags & 1) == 1;
        if (persistent) {
            throw new IllegalArgumentException("永続パレットはテストでは非対応です。");
        }
        int[] palette;
        // パレットは常に「件数＋ID列」で書かれる（bits==0 でも件数 1 が先頭にある）。
        int paletteSize = (int) BedrockVarInt.readUnsignedVarInt(in);
        palette = new int[paletteSize];
        for (int i = 0; i < paletteSize; i++) {
            palette[i] = (int) BedrockVarInt.readUnsignedVarInt(in);
        }
        if (bits == 0) {
            int[] values = new int[entries];
            java.util.Arrays.fill(values, palette[0]);
            if (collect != null) {
                collect.runtimeIds.add(palette[0]);
            }
            return values;
        }
        int indicesPerWord = 32 / bits;
        int mask = (1 << bits) - 1;
        int wordCount = (int) BedrockVarInt.readUnsignedVarInt(in);
        int[] words = new int[wordCount];
        for (int i = 0; i < wordCount; i++) {
            words[i] = in.readLittleInt();
        }
        int[] values = new int[entries];
        for (int j = 0; j < entries; j++) {
            int word = words[j / indicesPerWord];
            int index = (word >> ((j % indicesPerWord) * bits)) & mask;
            values[j] = palette[index];
            if (collect != null) {
                collect.runtimeIds.add(palette[index]);
            }
        }
        return values;
    }
}
