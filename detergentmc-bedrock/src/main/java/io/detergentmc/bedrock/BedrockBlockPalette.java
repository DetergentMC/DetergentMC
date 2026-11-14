package io.detergentmc.bedrock;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Bedrock 1.20.50 の正規ブロックパレット（PMMP BedrockData の
 * {@code canonical_block_states.nbt}、CC0 1.0 Universal）。
 *
 * <p>ファイルは varint NBT（ネットワーク NBT）で書かれたルート
 * TAG_Compound の連続である。各エントリは
 * {@code 0x0A（compound）＋名前長 0＋{name: String, states: Compound,
 * version: Int}＋終端 0x00} で、エントリの出現順がそのまま Bedrock の
 * ランタイムIDになる（0 番から始まる）。このクラスはエントリごとに
 * 名前と NBT のバイト範囲（開始位置と長さ）を取り出し、StartGame
 * パケットにそのまま流し込めるようにする。
 *
 * <p>StartGame に全パレットを送るのは、サーバー定義パレット方式と
 * クライアント内蔵パレット方式の両方でランタイムIDの一致が取れる
 * ためである（1.16.100 以降はクライアント側パレットだが、Geyser／
 * PMMP も正規順のパレットを StartGame で送っている）。
 */
public final class BedrockBlockPalette {

    /** リソース中の正規パレットファイル名。 */
    public static final String RESOURCE_NAME = "canonical_block_states_1_20_50.nbt";

    /** パレットのバージョン（CurrentBlockVersion）。 */
    public static final int EXPECTED_VERSION = BedrockBlockRuntimeIds.PALETTE_VERSION;

    private final byte[] bytes;
    private final List<Entry> entries;

    /** 1 エントリ：名前と、NBT バイト列中の範囲。 */
    public record Entry(String name, int offset, int length) {
    }

    private BedrockBlockPalette(byte[] bytes, List<Entry> entries) {
        this.bytes = bytes;
        this.entries = List.copyOf(entries);
    }

    /** バンドルされた正規パレットを読み込む。 */
    public static BedrockBlockPalette load() throws IOException {
        try (InputStream in = BedrockBlockPalette.class.getResourceAsStream(RESOURCE_NAME)) {
            if (in == null) {
                throw new IOException("Bedrock 正規パレットが見つかりません: " + RESOURCE_NAME);
            }
            byte[] bytes = in.readAllBytes();
            return parse(bytes);
        }
    }

    /**
     * バイト列を varint NBT の compound 連続として読み、エントリ一覧を作る。
     * 各エントリのバイト範囲は StartGame の block properties にそのまま
     * 流し込める形（型バイト 0x0A と空名を含む）。
     */
    public static BedrockBlockPalette parse(byte[] bytes) {
        Cursor cursor = new Cursor(bytes);
        List<Entry> entries = new ArrayList<>();
        while (cursor.remaining() > 0) {
            int start = cursor.position();
            int type = cursor.readByte();
            if (type != 10) {
                throw new IllegalArgumentException(
                        "正規パレットのエントリ " + entries.size()
                                + " が TAG_Compound ではありません（" + type + "）。");
            }
            int nameLength = cursor.readUnsignedVarInt();
            if (nameLength != 0) {
                throw new IllegalArgumentException(
                        "正規パレットのエントリ " + entries.size() + " の名前が空ではありません。");
            }
            String name = parseCompoundBody(cursor, entries.size());
            entries.add(new Entry(name, start, cursor.position() - start));
        }
        if (entries.isEmpty()) {
            throw new IllegalArgumentException("正規パレットが空です。");
        }
        return new BedrockBlockPalette(bytes, entries);
    }

    /** compound 本体（型バイトと名前の後）を読み、ブロック名を返す。 */
    private static String parseCompoundBody(Cursor cursor, int index) {
        String name = null;
        int version = -1;
        while (true) {
            int tagType = cursor.readByte();
            if (tagType == 0) {
                break;
            }
            String tagName = cursor.readString();
            switch (tagName) {
                case "name" -> {
                    if (tagType != 8) {
                        throw new IllegalArgumentException(
                                "パレットのエントリ " + index + " の name が文字列ではありません。");
                    }
                    name = cursor.readString();
                }
                case "version" -> {
                    if (tagType != 3) {
                        throw new IllegalArgumentException(
                                "パレットのエントリ " + index + " の version が int ではありません。");
                    }
                    version = cursor.readVarInt();
                }
                default -> cursor.skipTag(tagType);
            }
        }
        if (name == null) {
            throw new IllegalArgumentException("パレットのエントリ " + index + " に name がありません。");
        }
        if (version != EXPECTED_VERSION) {
            throw new IllegalArgumentException(
                    "パレットのエントリ " + index + " の version が " + version
                            + " です（期待値 " + EXPECTED_VERSION + "）。");
        }
        return name;
    }

    /** エントリ数（= ランタイムIDの総数）。 */
    public int count() {
        return entries.size();
    }

    /** ランタイムIDに対応するエントリ。範囲外は例外。 */
    public Entry entryAt(int runtimeId) {
        return entries.get(runtimeId);
    }

    /** ランタイムIDに対応するブロック名（{@code minecraft:} 付き）。 */
    public String nameAt(int runtimeId) {
        return entries.get(runtimeId).name();
    }

    /**
     * StartGame の block properties として送るバイト列。パレット全体を
     * 並べたもので、ファイルの内容そのままである。呼び出し側は
     * {@link #count()} を配列の長さとして先に書き、このバイト列を
     * 後に続ける（各エントリは型バイト 0x0A と空名を含む compound）。
     */
    public byte[] rawPaletteBytes() {
        return Arrays.copyOf(bytes, bytes.length);
    }

    /** compound 本体の検証なしの読み飛ばし（入れ子用）。 */
    private static void skipCompoundBody(Cursor cursor) {
        while (true) {
            int tagType = cursor.readByte();
            if (tagType == 0) {
                break;
            }
            cursor.readString();
            cursor.skipTag(tagType);
        }
    }

    /** varint NBT を読むだけの小さなカーソル。 */
    private static final class Cursor {
        private final byte[] bytes;
        private int position;

        Cursor(byte[] bytes) {
            this.bytes = bytes;
        }

        int position() {
            return position;
        }

        int remaining() {
            return bytes.length - position;
        }

        int readByte() {
            need(1);
            return bytes[position++] & 0xFF;
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

        int readVarInt() {
            int raw = readUnsignedVarInt();
            return (raw >>> 1) ^ -(raw & 1);
        }

        long readVarLong() {
            long raw = readUnsignedVarLong();
            return (raw >>> 1) ^ -(raw & 1L);
        }

        long readUnsignedVarLong() {
            long value = 0;
            int shift = 0;
            for (int i = 0; i < 10; i++) {
                int b = readByte();
                value |= (long) (b & 0x7F) << shift;
                if ((b & 0x80) == 0) {
                    return value;
                }
                shift += 7;
            }
            throw new IllegalArgumentException("varlong が長すぎます。");
        }

        String readString() {
            int length = readUnsignedVarInt();
            need(length);
            String value = new String(bytes, position, length, java.nio.charset.StandardCharsets.UTF_8);
            position += length;
            return value;
        }

        void skipTag(int type) {
            switch (type) {
                case 1 -> readByte();
                case 2, 3 -> readVarInt();
                case 4 -> readVarLong();
                case 5 -> position += 4; // float
                case 6 -> position += 8; // double
                case 7 -> {
                    int length = readVarInt();
                    need(length);
                    position += length;
                }
                case 8 -> readString();
                case 9 -> {
                    int elementType = readByte();
                    int count = readVarInt();
                    for (int i = 0; i < count; i++) {
                        skipTag(elementType);
                    }
                }
                case 10 -> skipCompoundBody(this);
                case 11 -> {
                    int count = readVarInt();
                    need(count * 4);
                    position += count * 4;
                }
                case 12 -> {
                    int count = readVarInt();
                    need(count * 8);
                    position += count * 8;
                }
                default -> throw new IllegalArgumentException("未知の NBT タグ種別: " + type);
            }
            if (position > bytes.length) {
                throw new IllegalArgumentException("NBT を読み切る前に末尾に達しました。");
            }
        }

        private void need(int length) {
            if (length < 0 || bytes.length - position < length) {
                throw new IllegalArgumentException(
                        "varint NBT が途中で切れています（位置=" + position
                                + "、必要=" + length + "バイト）。");
            }
        }
    }
}
