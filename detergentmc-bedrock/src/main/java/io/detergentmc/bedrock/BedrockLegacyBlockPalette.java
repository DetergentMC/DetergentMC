package io.detergentmc.bedrock;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Bedrock 1.11.0／1.12.0 世代の StartGame 用レガシーブロックパレット
 * （プロトコル 354・361 の配列形式）。
 *
 * <p>361 の StartGame（CloudburstMC Protocol の
 * StartGameSerializer_v361）はブロックパレットを単一 NBT タグではなく
 * 配列で書く：件数（VarInt Unsigned）＋要素ごとに name（String）／
 * meta（ShortLE）／id（ShortLE）。388 で単一 NBT タグ形式に変わった
 * （BE-NOTES.md §31）。354 の実効シリアライザ
 * （StartGameSerializer_v291 がトップレベルを担う）は同じ配列形式だが
 * 要素に id が無く、name（String）／meta（ShortLE）のみである
 * （BE-NOTES.md §32）。
 *
 * <p>中身は PMMP BedrockData の bedrock-1.12.0 タグの実測データである：
 * {@code required_block_states.json}（名前空間 "minecraft" 配下の
 * ブロック名 → メタ値リスト。460 名・(名前, メタ) 合計 3,311 件、
 * SHA-1 ef61df82…）と {@code block_id_map.json}（"minecraft:名前" →
 * レガシーブロック ID 0〜469、全 460 名、SHA-1 95f9de11…）から
 * 生成した。並びは出典ファイルの格納順（名前はファイル順、メタは
 * リスト順）をそのまま使っている。リソース
 * {@code bedrock_block_palette_1_12_0.bin}（92,944 バイト、
 * SHA-1 3553c5c9…）はこの配列形式のバイト列そのもの（件数を含む）。
 *
 * <p>354 用のパレットは PMMP BedrockData の bedrock-1.11.0 タグの
 * 実測データである：{@code required_block_states.json}（460 名・
 * (名前, メタ) 合計 3,183 件、SHA-1 425f624d…）と
 * {@code block_id_map.json}（460 名、SHA-1 95f9de11…＝1.12.0 と
 * バイト同一）から生成した。リソース
 * {@code bedrock_block_palette_1_11_0.bin}（83,378 バイト、
 * SHA-1 15ded0d9…）は 354 の配列形式（id 無し）のバイト列そのもの
 * （件数を含む）。1.12.0 のパレットとは中身が違うため共用できない。
 * 354 のエントリはワイヤ上に id を持たないため、{@link Entry#id()} は
 * -1 になる。
 *
 * <p>340（1.10.1）も形式は同じ（name／meta のみの配列）だが、
 * PMMP BedrockData の bedrock-1.10.0 タグには
 * {@code required_block_states.json}／{@code block_id_map.json} 自体が
 * 無く、1.10 系のパレット実データは存在しない。そのため 340 では
 * この 1.11.0 世代のパレットを代用する（340 自体の実測ではない。
 * BE-NOTES.md §33）。
 *
 * <p>332（1.9.0）も形式は同じ（name／meta のみの配列）だが、
 * PMMP BedrockData の bedrock-1.9.0 タグにも
 * {@code required_block_states.json}／{@code block_id_map.json} 自体が
 * 無く（ツリー 5 ファイルのみで bedrock-1.10.0 タグと同じ構成）、
 * 1.9 系のパレット実データは存在しない。そのため 332 でも
 * この 1.11.0 世代のパレットを代用する（332 自体の実測ではない。
 * BE-NOTES.md §34）。
 *
 * <p>313（1.8.1）も形式は同じ（name／meta のみの配列）だが、
 * PMMP BedrockData の bedrock-1.8.0 タグにも
 * {@code required_block_states.json}／{@code block_id_map.json} 自体が
 * 無く（ツリー 4 ファイルのみ）、
 * 1.8 系のパレット実データは存在しない。そのため 313 でも
 * この 1.11.0 世代のパレットを代用する（313 自体の実測ではない。
 * BE-NOTES.md §35）。
 *
 * <p>ランタイム ID の割り当て（この並びの出現順がそのまま ID になる
 * 前提）と、チャンク翻訳側（正規パレット基準）との整合は未確認である
 * （388 世代と同じ注意。BE-NOTES.md §31）。
 */
public final class BedrockLegacyBlockPalette {

    /** リソース中のレガシーパレットファイル名（1.12.0 世代）。 */
    public static final String RESOURCE_NAME = "bedrock_block_palette_1_12_0.bin";

    /** リソース中のレガシーパレットファイル名（1.11.0 世代）。 */
    public static final String RESOURCE_NAME_1_11_0 = "bedrock_block_palette_1_11_0.bin";

    private static BedrockLegacyBlockPalette cached;
    private static BedrockLegacyBlockPalette cached1_11_0;

    private final byte[] bytes;
    private final List<Entry> entries;

    /** 1 エントリ：ブロック名とレガシー meta／id。 */
    public record Entry(String name, int meta, int id) {
    }

    private BedrockLegacyBlockPalette(byte[] bytes, List<Entry> entries) {
        this.bytes = bytes;
        this.entries = List.copyOf(entries);
    }

    /** バンドルされたレガシーパレットを読み込む（初回のみ解析し、以後はキャッシュを返す）。 */
    public static synchronized BedrockLegacyBlockPalette load() {
        if (cached != null) {
            return cached;
        }
        cached = loadResource(RESOURCE_NAME, true);
        return cached;
    }

    /**
     * 1.11.0 世代（354）のレガシーパレットを読み込む。形式は 1.12.0 と
     * 同じ配列形式だが、要素に id が無い（BE-NOTES.md §32）。
     * 340（1.10.1）もこのパレットを代用する（BE-NOTES.md §33）。
     * 332（1.9.0）も同じく代用する（BE-NOTES.md §34）。
     * 313（1.8.1）も同じく代用する（BE-NOTES.md §35）。
     */
    public static synchronized BedrockLegacyBlockPalette load1_11_0() {
        if (cached1_11_0 != null) {
            return cached1_11_0;
        }
        cached1_11_0 = loadResource(RESOURCE_NAME_1_11_0, false);
        return cached1_11_0;
    }

    private static BedrockLegacyBlockPalette loadResource(String resourceName,
            boolean hasEntryId) {
        try (InputStream in = BedrockLegacyBlockPalette.class.getResourceAsStream(resourceName)) {
            if (in == null) {
                throw new IllegalStateException(
                        "Bedrock レガシーパレットが見つかりません: " + resourceName);
            }
            return parse(in.readAllBytes(), hasEntryId);
        } catch (IOException e) {
            throw new IllegalStateException("Bedrock レガシーパレットを読めません", e);
        }
    }

    /**
     * バイト列を配列形式（件数＋name／meta／id の並び）として読み、
     * エントリ一覧を作る。末尾までちょうど読み切れなければ例外。
     */
    public static BedrockLegacyBlockPalette parse(byte[] bytes) {
        return parse(bytes, true);
    }

    /**
     * バイト列を配列形式として読む。{@code hasEntryId} が偽の場合は
     * 要素が name／meta のみ（354 の形式）で、エントリの id は -1 に
     * なる。末尾までちょうど読み切れなければ例外。
     */
    public static BedrockLegacyBlockPalette parse(byte[] bytes, boolean hasEntryId) {
        Cursor cursor = new Cursor(bytes);
        int count = cursor.readUnsignedVarInt();
        List<Entry> entries = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            String name = cursor.readString();
            int meta = cursor.readLittleShort();
            int id = hasEntryId ? cursor.readLittleShort() : -1;
            entries.add(new Entry(name, meta, id));
        }
        if (cursor.remaining() != 0) {
            throw new IllegalArgumentException(
                    "レガシーパレットの末尾に " + cursor.remaining() + " バイト余っています。");
        }
        if (entries.isEmpty()) {
            throw new IllegalArgumentException("レガシーパレットが空です。");
        }
        return new BedrockLegacyBlockPalette(bytes, entries);
    }

    /** エントリ数。 */
    public int count() {
        return entries.size();
    }

    /** インデックスに対応するエントリ。範囲外は例外。 */
    public Entry entryAt(int index) {
        return entries.get(index);
    }

    /**
     * StartGame のブロックパレットとして送るバイト列（件数の VarInt を
     * 含む配列形式そのまま）。呼び出し側はこのバイト列をそのまま書く。
     */
    public byte[] rawArrayBytes() {
        return java.util.Arrays.copyOf(bytes, bytes.length);
    }

    /** 配列形式を読むだけの小さなカーソル。 */
    private static final class Cursor {
        private final byte[] bytes;
        private int position;

        Cursor(byte[] bytes) {
            this.bytes = bytes;
        }

        int remaining() {
            return bytes.length - position;
        }

        int readUnsignedVarInt() {
            int value = 0;
            int shift = 0;
            for (int i = 0; i < 5; i++) {
                int b = readByte();
                value |= (b & 0x7F) << shift;
                if ((b & 0x80) == 0) {
                    return value;
                }
                shift += 7;
            }
            throw new IllegalArgumentException("varint が長すぎます。");
        }

        String readString() {
            int length = readUnsignedVarInt();
            need(length);
            String value = new String(bytes, position, length, StandardCharsets.UTF_8);
            position += length;
            return value;
        }

        int readLittleShort() {
            need(2);
            int value = (bytes[position] & 0xFF) | ((bytes[position + 1] & 0xFF) << 8);
            position += 2;
            return (short) value;
        }

        private int readByte() {
            need(1);
            return bytes[position++] & 0xFF;
        }

        private void need(int length) {
            if (length < 0 || bytes.length - position < length) {
                throw new IllegalArgumentException(
                        "レガシーパレットが途中で切れています（位置=" + position
                                + "、必要=" + length + "バイト）。");
            }
        }
    }
}
