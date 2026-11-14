package io.detergentmc.bedrock;

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
