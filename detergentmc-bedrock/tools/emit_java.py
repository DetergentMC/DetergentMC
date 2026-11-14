#!/usr/bin/env python3
"""対応表（out-table.json, out-biomes.json）から Java の表を生成する。

出力：
  - BedrockBlockRuntimeIds.java（Java 765 状態ID -> Bedrock ランタイムID の読み手）
  - bedrock_block_runtime_ids_1_20_4.bin（表そのもの：uint16 LE 26,644 件＋
    waterlogged ビットセット 417 個の uint64 LE。Java の int[] リテラルは
    64KB のバイトコード制限を超えるためバイナリ化している）
  - BedrockBiomeIds.java（Java バイオーム -> Bedrock バイオームID）

使い方：python3 emit_java.py <BedrockBlockRuntimeIds.java の出力ディレクトリ>
（.bin は <出力ディレクトリ>/../../resources/io/detergentmc/bedrock/ へ出す）
"""
import json
import struct
import sys

OUT_TABLE = '/tmp/gen/out-table.json'
OUT_BIOMES = '/tmp/gen/out-biomes.json'


def emit_block_ids(out_dir):
    data = json.load(open(OUT_TABLE))
    table = data['table']
    waterlogged = set(data['waterlogged'])
    binary = bytearray()
    for v in table:
        binary += struct.pack('<H', v)
    nlongs = (len(table) + 63) // 64
    for i in range(nlongs):
        v = 0
        for bit in range(64):
            sid = i * 64 + bit
            if sid < len(table) and sid in waterlogged:
                v |= 1 << bit
        binary += struct.pack('<Q', v)
    bin_path = (out_dir
                + '/../../resources/io/detergentmc/bedrock/bedrock_block_runtime_ids_1_20_4.bin')
    open(bin_path, 'wb').write(bytes(binary))
    print('wrote', bin_path, f'({len(binary)} bytes)')
    java = '''package io.detergentmc.bedrock;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Java 765（1.20.4）のブロック状態ID -> Bedrock 1.20.50 のランタイムID の対応表。
 *
 * <p>生成物。表そのものはリソース
 * {@code bedrock_block_runtime_ids_1_20_4.bin}（Java 765 状態ID 26,644 件分の
 * uint16 LE ランタイムID＋waterlogged のビットセット 417 個の uint64 LE）に
 * 入っており、このクラスは起動時に読んで配列化する。生成元は
 * {@code detergentmc-bedrock/tools/generate.py}（PMMP BedrockData の
 * canonical_block_states.nbt と PrismarineJS minecraft-data の
 * pc 1.20.3 blocks.json から作る）。直接編集しないこと。再生成は
 * {@code detergentmc-bedrock/tools/emit_java.py} を参照。
 *
 * <p>網羅率：Java 765 の全 26,644 状態が Bedrock ランタイムIDに写る。
 * 対応表に無いブロックは生成時に近いブロックへ代替済み（石への代替は 0 件）。
 * waterlogged の状態は別途 {@link #isWaterlogged(int)} で引ける。
 */
public final class BedrockBlockRuntimeIds {

    private BedrockBlockRuntimeIds() {
    }

    /** Java 765 の最大ブロック状態ID。 */
    public static final int MAX_JAVA_BLOCK_STATE_765 = 26643;

    /** Bedrock 1.20.50 の正規パレットのエントリ数。 */
    public static final int PALETTE_COUNT = 14127;

    /** 正規パレットのバージョン（CurrentBlockVersion）。 */
    public static final int PALETTE_VERSION = 18100737;

    /** 空気の Bedrock ランタイムID。 */
    public static final int AIR_RUNTIME_ID = 11881;

    /** 石の Bedrock ランタイムID（代替の既定値）。 */
    public static final int STONE_RUNTIME_ID = 1774;

    /** 表のリソース名。 */
    public static final String RESOURCE_NAME = "bedrock_block_runtime_ids_1_20_4.bin";

    /** 表のエントリ数（Java 765 の状態数）。 */
    public static final int TABLE_SIZE = 26644;

    /** waterlogged ビットセットの long 数。 */
    private static final int WATERLOGGED_LONGS = (TABLE_SIZE + 63) / 64;

    private static final int[] TABLE = loadTable();
    private static final long[] WATERLOGGED_BITS = loadWaterloggedBits();

    private static byte[] loadResource() {
        try (InputStream in = BedrockBlockRuntimeIds.class.getResourceAsStream(RESOURCE_NAME)) {
            if (in == null) {
                throw new IllegalStateException(
                        "Bedrock ブロック対応表が見つかりません: " + RESOURCE_NAME);
            }
            return in.readAllBytes();
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Bedrock ブロック対応表が読めません: " + RESOURCE_NAME, e);
        }
    }

    private static int[] loadTable() {
        byte[] bytes = loadResource();
        int expected = TABLE_SIZE * 2 + WATERLOGGED_LONGS * 8;
        if (bytes.length != expected) {
            throw new IllegalStateException(
                    "Bedrock ブロック対応表の大きさが異常です: " + bytes.length
                            + "（期待値 " + expected + "）。");
        }
        ByteBuffer buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        int[] table = new int[TABLE_SIZE];
        for (int i = 0; i < TABLE_SIZE; i++) {
            table[i] = buffer.getShort() & 0xFFFF;
        }
        return table;
    }

    private static long[] loadWaterloggedBits() {
        byte[] bytes = loadResource();
        ByteBuffer buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        buffer.position(TABLE_SIZE * 2);
        long[] bits = new long[WATERLOGGED_LONGS];
        for (int i = 0; i < WATERLOGGED_LONGS; i++) {
            bits[i] = buffer.getLong();
        }
        return bits;
    }

    /** 対応表のエントリ数。 */
    public static int tableSize() {
        return TABLE.length;
    }

    /** Java 765 のブロック状態IDに対応する Bedrock ランタイムIDを返す。範囲外は石。 */
    public static int runtimeId(int javaState765) {
        if (javaState765 < 0 || javaState765 > MAX_JAVA_BLOCK_STATE_765) {
            return STONE_RUNTIME_ID;
        }
        return TABLE[javaState765];
    }

    /** Java 765 のブロック状態が waterlogged かどうか。 */
    public static boolean isWaterlogged(int javaState765) {
        if (javaState765 < 0 || javaState765 > MAX_JAVA_BLOCK_STATE_765) {
            return false;
        }
        return (WATERLOGGED_BITS[javaState765 >> 6] >>> (javaState765 & 63) & 1L) != 0;
    }
}
'''
    path = out_dir + '/BedrockBlockRuntimeIds.java'
    open(path, 'w').write(java)
    print('wrote', path, f'(table={len(table)}, waterlogged={len(waterlogged)})')


def emit_biome_ids(out_dir):
    data = json.load(open(OUT_BIOMES))
    biome_table = data['biome_table']
    biome_names = data['biome_names']
    lines = []
    lines.append('package io.detergentmc.bedrock;')
    lines.append('')
    lines.append('/**')
    lines.append(' * Java のバイオーム -> Bedrock 1.20.50 のバイオームID。')
    lines.append(' *')
    lines.append(' * <p>生成物。生成元は {@code detergentmc-bedrock/tools/generate.py}')
    lines.append(' * （PMMP BedrockData の biome_id_map.json と PrismarineJS minecraft-data の')
    lines.append(' * pc 1.20.2 biomes.json から作る）。直接編集しないこと。')
    lines.append(' *')
    lines.append(' * <p>Java のバイオームは名前で引く（{@link #idForName(String)}）。')
    lines.append(' * チャンクのバイオームパレットが持つのはレジストリの番号（インデックス）で、')
    lines.append(' * その番号が指す名前は設定フェーズの Registry Data（{@code minecraft:worldgen/biome}）')
    lines.append(' * で Java 側から届く。名前が分からない場合の代替は平原（{@link #FALLBACK_ID}）。')
    lines.append(' */')
    lines.append('public final class BedrockBiomeIds {')
    lines.append('')
    lines.append('    private BedrockBiomeIds() {')
    lines.append('    }')
    lines.append('')
    lines.append('    /** 代替のバイオームID：平原。 */')
    lines.append('    public static final int FALLBACK_ID = 1;')
    lines.append('')
    lines.append('    /** Java のバイオームレジストリ番号（0〜63、1.20.3 の既定の順序）-> Bedrock バイオームID。 */')
    lines.append('    public static final int[] BY_JAVA_ID = {')
    for i in range(0, len(biome_table), 16):
        chunk = biome_table[i:i + 16]
        lines.append('            ' + ', '.join(str(v) for v in chunk) + ',')
    lines.append('    };')
    lines.append('')
    lines.append('    /** Java のバイオーム名（{@code minecraft:} 付きでも無しでも可）-> Bedrock バイオームID。 */')
    lines.append('    public static int idForName(String javaBiomeName) {')
    lines.append('        if (javaBiomeName == null) {')
    lines.append('            return FALLBACK_ID;')
    lines.append('        }')
    lines.append('        String name = javaBiomeName.startsWith("minecraft:")')
    lines.append('                ? javaBiomeName.substring("minecraft:".length()) : javaBiomeName;')
    lines.append('        return switch (name) {')
    for java_name, _be_name, be_id in biome_names:
        lines.append(f'            case "{java_name}" -> {be_id};')
    lines.append('            default -> FALLBACK_ID;')
    lines.append('        };')
    lines.append('    }')
    lines.append('')
    lines.append('    /** Java のバイオームレジストリ番号 -> Bedrock バイオームID（範囲外は平原）。 */')
    lines.append('    public static int idForJavaId(int javaBiomeId) {')
    lines.append('        if (javaBiomeId < 0 || javaBiomeId >= BY_JAVA_ID.length) {')
    lines.append('            return FALLBACK_ID;')
    lines.append('        }')
    lines.append('        return BY_JAVA_ID[javaBiomeId];')
    lines.append('    }')
    lines.append('}')
    path = out_dir + '/BedrockBiomeIds.java'
    open(path, 'w').write('\n'.join(lines) + '\n')
    print('wrote', path, f'(biomes={len(biome_table)})')


if __name__ == '__main__':
    out_dir = sys.argv[1]
    emit_block_ids(out_dir)
    emit_biome_ids(out_dir)
