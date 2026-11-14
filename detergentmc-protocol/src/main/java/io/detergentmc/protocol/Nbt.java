package io.detergentmc.protocol;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 必要最小限の NBT（Named Binary Tag）読み書き。
 *
 * <p>汎用 NBT ライブラリは追加しない。プロキシが本当に要るのは
 * 次の3つだけである：
 * <ul>
 *   <li>設定フェーズの Registry Data（765）の NBT codec を読んで、
 *       {@code minecraft:dimension_type} と
 *       {@code minecraft:worldgen/biome} を取り出すこと。</li>
 *   <li>763 向け Join Game の dimension codec（NBT）を組み立てること。</li>
 *   <li>765 のテキストコンポーネント（NBT）を763 の JSON 文字列に
 *       落とすこと（System Chat / Player Chat / Disconnect など）。</li>
 * </ul>
 *
 * <p>ワイヤ上の NBT には2種類ある。名前付き（{@code nbt}：型バイト＋
 * 名前（u16長＋UTF-8）＋中身。Join Game の codec がこれで、ルート名は
 * 空文字）と、名前なし（{@code anonymousNbt}：型バイト＋中身のみ。
 * チャットなどのテキストコンポーネントがこれ）。読み書きの両方を
 * 区別して扱う。NBT 文字列の長さは VarInt ではなく u16 である点に注意。
 *
 * <p>情報源：wiki.vg の NBT 仕様と PrismarineJS minecraft-data の
 * protocol.json（1.20 / 1.20.3）の {@code nbt} / {@code anonymousNbt}。
 */
public final class Nbt {

    private Nbt() {
    }

    /** NBT タグの種類。 */
    public static final int END = 0;
    public static final int BYTE = 1;
    public static final int SHORT = 2;
    public static final int INT = 3;
    public static final int LONG = 4;
    public static final int FLOAT = 5;
    public static final int DOUBLE = 6;
    public static final int BYTE_ARRAY = 7;
    public static final int STRING = 8;
    public static final int LIST = 9;
    public static final int COMPOUND = 10;
    public static final int INT_ARRAY = 11;
    public static final int LONG_ARRAY = 12;

    /** NBT タグ1個。型と値だけを持つ（名前は読み書き時に別扱い）。 */
    public sealed interface Tag permits Tag.ByteTag, Tag.ShortTag, Tag.IntTag,
            Tag.LongTag, Tag.FloatTag, Tag.DoubleTag, Tag.ByteArrayTag,
            Tag.StringTag, Tag.ListTag, Tag.CompoundTag, Tag.IntArrayTag,
            Tag.LongArrayTag {

        /** タグの種類（上の定数）。 */
        int type();

        record ByteTag(byte value) implements Tag {
            @Override public int type() { return BYTE; }
        }

        record ShortTag(short value) implements Tag {
            @Override public int type() { return SHORT; }
        }

        record IntTag(int value) implements Tag {
            @Override public int type() { return INT; }
        }

        record LongTag(long value) implements Tag {
            @Override public int type() { return LONG; }
        }

        record FloatTag(float value) implements Tag {
            @Override public int type() { return FLOAT; }
        }

        record DoubleTag(double value) implements Tag {
            @Override public int type() { return DOUBLE; }
        }

        record ByteArrayTag(byte[] value) implements Tag {
            @Override public int type() { return BYTE_ARRAY; }

            @Override public boolean equals(Object other) {
                return other instanceof ByteArrayTag that
                        && java.util.Arrays.equals(value, that.value);
            }

            @Override public int hashCode() {
                return java.util.Arrays.hashCode(value);
            }
        }

        record StringTag(String value) implements Tag {
            @Override public int type() { return STRING; }
        }

        record ListTag(int elementType, List<Tag> value) implements Tag {
            @Override public int type() { return LIST; }
            public ListTag {
                value = List.copyOf(value);
            }
        }

        record CompoundTag(Map<String, Tag> value) implements Tag {
            @Override public int type() { return COMPOUND; }
            public CompoundTag {
                // 挿入順を保つ（Map.copyOf は順序を保証しない）。
                // Join Game の codec など、エントリの順序が意味を持つ場面がある。
                value = java.util.Collections.unmodifiableMap(
                        new LinkedHashMap<>(value));
            }

            /** 子タグを名前で引く。無ければ空。 */
            public java.util.Optional<Tag> get(String name) {
                return java.util.Optional.ofNullable(value.get(name));
            }

            /** 子タグを文字列として引く。文字列タグでなければ空。 */
            public java.util.Optional<String> getString(String name) {
                return get(name)
                        .filter(t -> t instanceof StringTag)
                        .map(t -> ((StringTag) t).value());
            }

            /** 子タグをコンパウンドとして引く。 */
            public java.util.Optional<CompoundTag> getCompound(String name) {
                return get(name)
                        .filter(t -> t instanceof CompoundTag)
                        .map(t -> (CompoundTag) t);
            }

            /** 子タグをリストとして引く。 */
            public java.util.Optional<ListTag> getList(String name) {
                return get(name)
                        .filter(t -> t instanceof ListTag)
                        .map(t -> (ListTag) t);
            }
        }

        record IntArrayTag(int[] value) implements Tag {
            @Override public int type() { return INT_ARRAY; }

            @Override public boolean equals(Object other) {
                return other instanceof IntArrayTag that
                        && java.util.Arrays.equals(value, that.value);
            }

            @Override public int hashCode() {
                return java.util.Arrays.hashCode(value);
            }
        }

        record LongArrayTag(long[] value) implements Tag {
            @Override public int type() { return LONG_ARRAY; }

            @Override public boolean equals(Object other) {
                return other instanceof LongArrayTag that
                        && java.util.Arrays.equals(value, that.value);
            }

            @Override public int hashCode() {
                return java.util.Arrays.hashCode(value);
            }
        }
    }

    /** 読み取り結果：タグと消費したバイト数。 */
    public record ReadResult(Tag tag, int bytesRead) {
    }

    /** 名前付きタグの読み取り結果：名前・タグ・消費バイト数。 */
    public record NamedResult(String name, Tag tag, int bytesRead) {
    }

    /** 名前付きタグ（型＋名前＋中身）を1個読む。 */
    public static NamedResult readNamed(byte[] bytes, int offset) {
        if (offset >= bytes.length) {
            throw new IllegalArgumentException("NBTが途中で切れています（offset=" + offset + "）。");
        }
        int type = bytes[offset] & 0xFF;
        if (type == END) {
            return new NamedResult("", null, 1);
        }
        String name = readStringPayload(bytes, offset + 1);
        int nameBytes = 2 + name.getBytes(StandardCharsets.UTF_8).length;
        ReadResult payload = readPayload(bytes, offset + 1 + nameBytes, type);
        return new NamedResult(name, payload.tag(), 1 + nameBytes + payload.bytesRead());
    }

    /** 名前なしタグ（型＋中身のみ）を1個読む。 */
    public static ReadResult readAnonymous(byte[] bytes, int offset) {
        if (offset >= bytes.length) {
            throw new IllegalArgumentException("NBTが途中で切れています（offset=" + offset + "）。");
        }
        int type = bytes[offset] & 0xFF;
        if (type == END) {
            return new ReadResult(null, 1);
        }
        ReadResult payload = readPayload(bytes, offset + 1, type);
        // bytesRead には型バイト1個分も含める（readNamed と同じ数え方）。
        return new ReadResult(payload.tag(), 1 + payload.bytesRead());
    }

    /** {@code offset} から中身だけを読む（型は {@code type} で与える）。 */
    private static ReadResult readPayload(byte[] bytes, int offset, int type) {
        return switch (type) {
            case BYTE -> {
                need(bytes, offset, 1);
                yield new ReadResult(new Tag.ByteTag(bytes[offset]), 1);
            }
            case SHORT -> {
                need(bytes, offset, 2);
                yield new ReadResult(new Tag.ShortTag(
                        (short) ((bytes[offset] << 8) | (bytes[offset + 1] & 0xFF))), 2);
            }
            case INT -> {
                need(bytes, offset, 4);
                yield new ReadResult(new Tag.IntTag(readInt(bytes, offset)), 4);
            }
            case LONG -> {
                need(bytes, offset, 8);
                yield new ReadResult(new Tag.LongTag(readLong(bytes, offset)), 8);
            }
            case FLOAT -> {
                need(bytes, offset, 4);
                yield new ReadResult(new Tag.FloatTag(
                        Float.intBitsToFloat(readInt(bytes, offset))), 4);
            }
            case DOUBLE -> {
                need(bytes, offset, 8);
                yield new ReadResult(new Tag.DoubleTag(
                        Double.longBitsToDouble(readLong(bytes, offset))), 8);
            }
            case BYTE_ARRAY -> {
                need(bytes, offset, 4);
                int length = readInt(bytes, offset);
                if (length < 0) {
                    throw new IllegalArgumentException("NBTのバイト配列の長さが負です: " + length);
                }
                need(bytes, offset + 4, length);
                byte[] value = new byte[length];
                System.arraycopy(bytes, offset + 4, value, 0, length);
                yield new ReadResult(new Tag.ByteArrayTag(value), 4 + length);
            }
            case STRING -> {
                String value = readStringPayload(bytes, offset);
                yield new ReadResult(new Tag.StringTag(value),
                        2 + value.getBytes(StandardCharsets.UTF_8).length);
            }
            case LIST -> {
                need(bytes, offset, 5);
                int elementType = bytes[offset] & 0xFF;
                int length = readInt(bytes, offset + 1);
                if (length < 0) {
                    throw new IllegalArgumentException("NBTのリストの長さが負です: " + length);
                }
                int cursor = offset + 5;
                List<Tag> elements = new ArrayList<>(length);
                for (int i = 0; i < length; i++) {
                    ReadResult element = readPayload(bytes, cursor, elementType);
                    elements.add(element.tag());
                    cursor += element.bytesRead();
                }
                yield new ReadResult(new Tag.ListTag(elementType, elements), cursor - offset);
            }
            case COMPOUND -> {
                Map<String, Tag> entries = new LinkedHashMap<>();
                int cursor = offset;
                while (true) {
                    NamedResult entry = readNamed(bytes, cursor);
                    cursor += entry.bytesRead();
                    if (entry.tag() == null) {
                        break;
                    }
                    entries.put(entry.name(), entry.tag());
                }
                yield new ReadResult(new Tag.CompoundTag(entries), cursor - offset);
            }
            case INT_ARRAY -> {
                need(bytes, offset, 4);
                int length = readInt(bytes, offset);
                if (length < 0) {
                    throw new IllegalArgumentException("NBTのint配列の長さが負です: " + length);
                }
                need(bytes, offset + 4, length * 4);
                int[] value = new int[length];
                for (int i = 0; i < length; i++) {
                    value[i] = readInt(bytes, offset + 4 + i * 4);
                }
                yield new ReadResult(new Tag.IntArrayTag(value), 4 + length * 4);
            }
            case LONG_ARRAY -> {
                need(bytes, offset, 4);
                int length = readInt(bytes, offset);
                if (length < 0) {
                    throw new IllegalArgumentException("NBTのlong配列の長さが負です: " + length);
                }
                need(bytes, offset + 4, length * 8);
                long[] value = new long[length];
                for (int i = 0; i < length; i++) {
                    value[i] = readLong(bytes, offset + 4 + i * 8);
                }
                yield new ReadResult(new Tag.LongArrayTag(value), 4 + length * 8);
            }
            default -> throw new IllegalArgumentException("未知のNBTタグ種別です: " + type);
        };
    }

    /** 名前付きタグ（型＋名前＋中身）を書く。 */
    public static byte[] writeNamed(String name, Tag tag) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(tag.type());
        writeStringPayload(out, name);
        writePayload(out, tag);
        return out.toByteArray();
    }

    /** 名前なしタグ（型＋中身のみ）を書く。 */
    public static byte[] writeAnonymous(Tag tag) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(tag.type());
        writePayload(out, tag);
        return out.toByteArray();
    }

    private static void writePayload(ByteArrayOutputStream out, Tag tag) {
        if (tag instanceof Tag.ByteTag t) {
            out.write(t.value());
        } else if (tag instanceof Tag.ShortTag t) {
            out.write((t.value() >> 8) & 0xFF);
            out.write(t.value() & 0xFF);
        } else if (tag instanceof Tag.IntTag t) {
            writeInt(out, t.value());
        } else if (tag instanceof Tag.LongTag t) {
            writeLong(out, t.value());
        } else if (tag instanceof Tag.FloatTag t) {
            writeInt(out, Float.floatToIntBits(t.value()));
        } else if (tag instanceof Tag.DoubleTag t) {
            writeLong(out, Double.doubleToLongBits(t.value()));
        } else if (tag instanceof Tag.ByteArrayTag t) {
            writeInt(out, t.value().length);
            out.write(t.value(), 0, t.value().length);
        } else if (tag instanceof Tag.StringTag t) {
            writeStringPayload(out, t.value());
        } else if (tag instanceof Tag.ListTag t) {
            out.write(t.elementType());
            writeInt(out, t.value().size());
            for (Tag element : t.value()) {
                writePayload(out, element);
            }
        } else if (tag instanceof Tag.CompoundTag t) {
            for (Map.Entry<String, Tag> entry : t.value().entrySet()) {
                out.write(entry.getValue().type());
                writeStringPayload(out, entry.getKey());
                writePayload(out, entry.getValue());
            }
            out.write(END);
        } else if (tag instanceof Tag.IntArrayTag t) {
            writeInt(out, t.value().length);
            for (int value : t.value()) {
                writeInt(out, value);
            }
        } else if (tag instanceof Tag.LongArrayTag t) {
            writeInt(out, t.value().length);
            for (long value : t.value()) {
                writeLong(out, value);
            }
        } else {
            throw new IllegalArgumentException("未知のNBTタグです: " + tag);
        }
    }

    /** NBT 文字列（u16長＋UTF-8）を1個読む。 */
    public static String readStringPayload(byte[] bytes, int offset) {
        need(bytes, offset, 2);
        int length = ((bytes[offset] & 0xFF) << 8) | (bytes[offset + 1] & 0xFF);
        need(bytes, offset + 2, length);
        return new String(bytes, offset + 2, length, StandardCharsets.UTF_8);
    }

    private static void writeStringPayload(ByteArrayOutputStream out, String value) {
        byte[] utf8 = value.getBytes(StandardCharsets.UTF_8);
        if (utf8.length > 65535) {
            throw new IllegalArgumentException("NBT文字列が長すぎます: " + utf8.length + "バイト");
        }
        out.write((utf8.length >> 8) & 0xFF);
        out.write(utf8.length & 0xFF);
        out.write(utf8, 0, utf8.length);
    }

    private static int readInt(byte[] bytes, int offset) {
        return ((bytes[offset] & 0xFF) << 24)
                | ((bytes[offset + 1] & 0xFF) << 16)
                | ((bytes[offset + 2] & 0xFF) << 8)
                | (bytes[offset + 3] & 0xFF);
    }

    private static long readLong(byte[] bytes, int offset) {
        long value = 0;
        for (int i = 0; i < 8; i++) {
            value = (value << 8) | (bytes[offset + i] & 0xFFL);
        }
        return value;
    }

    private static void writeInt(ByteArrayOutputStream out, int value) {
        out.write((value >> 24) & 0xFF);
        out.write((value >> 16) & 0xFF);
        out.write((value >> 8) & 0xFF);
        out.write(value & 0xFF);
    }

    private static void writeLong(ByteArrayOutputStream out, long value) {
        for (int i = 7; i >= 0; i--) {
            out.write((int) ((value >> (i * 8)) & 0xFF));
        }
    }

    private static void need(byte[] bytes, int offset, int length) {
        if (offset < 0 || length < 0 || bytes.length - offset < length) {
            throw new IllegalArgumentException(
                    "NBTが途中で切れています（offset=" + offset + "、必要=" + length + "バイト）。");
        }
    }

    /**
     * テキストコンポーネントの NBT を、763 が期待する JSON 文字列に変換する。
     *
     * <p>765 ではテキストコンポーネントは NBT で送られる（文字列タグだけの
     * 単純なものも、{@code {"text": "...", "extra": [...]}} のような
     * コンパウンドもある）。763 では同じものが JSON 文字列で送られる。
     * この変換は NBT の構造を JSON の構造にそのまま写す：文字列→文字列、
     * コンパウンド→オブジェクト、リスト→配列、数値→数値、バイト 0/1 は
     * 真偽値（bold などの装飾フラグ）として扱う。未知の形は
     * {@link #plainText(Tag)} にフォールバックするのではなく、
     * そのまま JSON に写す（情報を落とさないため）。
     */
    public static String toJsonString(Tag tag) {
        StringBuilder out = new StringBuilder();
        appendJson(out, tag);
        return out.toString();
    }

    private static void appendJson(StringBuilder out, Tag tag) {
        if (tag == null) {
            out.append("null");
            return;
        }
        if (tag instanceof Tag.ByteTag t) {
            out.append(t.value() == 0 ? "false" : "true");
        } else if (tag instanceof Tag.ShortTag t) {
            out.append(t.value());
        } else if (tag instanceof Tag.IntTag t) {
            out.append(t.value());
        } else if (tag instanceof Tag.LongTag t) {
            out.append(t.value());
        } else if (tag instanceof Tag.FloatTag t) {
            out.append(Float.toString(t.value()));
        } else if (tag instanceof Tag.DoubleTag t) {
            out.append(Double.toString(t.value()));
        } else if (tag instanceof Tag.StringTag t) {
            appendJsonString(out, t.value());
        } else if (tag instanceof Tag.ByteArrayTag t) {
            out.append('[');
            for (int i = 0; i < t.value().length; i++) {
                if (i > 0) {
                    out.append(',');
                }
                out.append(t.value()[i]);
            }
            out.append(']');
        } else if (tag instanceof Tag.IntArrayTag t) {
            out.append('[');
            for (int i = 0; i < t.value().length; i++) {
                if (i > 0) {
                    out.append(',');
                }
                out.append(t.value()[i]);
            }
            out.append(']');
        } else if (tag instanceof Tag.LongArrayTag t) {
            out.append('[');
            for (int i = 0; i < t.value().length; i++) {
                if (i > 0) {
                    out.append(',');
                }
                out.append(t.value()[i]);
            }
            out.append(']');
        } else if (tag instanceof Tag.ListTag t) {
            out.append('[');
            for (int i = 0; i < t.value().size(); i++) {
                if (i > 0) {
                    out.append(',');
                }
                appendJson(out, t.value().get(i));
            }
            out.append(']');
        } else if (tag instanceof Tag.CompoundTag t) {
            out.append('{');
            boolean first = true;
            for (Map.Entry<String, Tag> entry : t.value().entrySet()) {
                if (!first) {
                    out.append(',');
                }
                first = false;
                appendJsonString(out, entry.getKey());
                out.append(':');
                appendJson(out, entry.getValue());
            }
            out.append('}');
        }
    }

    /** JSON 文字列リテラル（ダブルクォート＋エスケープ）を追加する。 */
    public static void appendJsonString(StringBuilder out, String value) {
        out.append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                case '\b' -> out.append("\\b");
                case '\f' -> out.append("\\f");
                default -> {
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        out.append('"');
    }

    /** JSON 文字列リテラルを返す。 */
    public static String jsonString(String value) {
        StringBuilder out = new StringBuilder();
        appendJsonString(out, value);
        return out.toString();
    }

    /**
     * テキストコンポーネントの NBT から表示テキストだけを抜く
     * （フォールバック用）。{@code text} と {@code extra} を連結し、
     * {@code translate} の場合はキーと引数を連結する。文字列タグは
     * そのまま返す。
     */
    public static String plainText(Tag tag) {
        if (tag == null) {
            return "";
        }
        if (tag instanceof Tag.StringTag t) {
            return t.value();
        }
        if (tag instanceof Tag.CompoundTag t) {
            StringBuilder out = new StringBuilder();
            t.getString("text").ifPresent(out::append);
            t.getString("translate").ifPresent(key -> {
                if (out.length() == 0) {
                    out.append(key);
                }
            });
            t.getList("extra").ifPresent(extra -> {
                for (Tag element : extra.value()) {
                    out.append(plainText(element));
                }
            });
            t.getList("with").ifPresent(args -> {
                for (Tag element : args.value()) {
                    out.append(plainText(element));
                }
            });
            return out.toString();
        }
        if (tag instanceof Tag.ListTag t) {
            StringBuilder out = new StringBuilder();
            for (Tag element : t.value()) {
                out.append(plainText(element));
            }
            return out.toString();
        }
        return "";
    }
}
