package io.detergentmc.protocol;

import io.detergentmc.protocol.Nbt.Tag;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * JSON 文字列で書かれたテキストコンポーネントを NBT タグに変換する。
 * 逆向き対応（764サーバー→765クライアント）用。764ではテキスト
 * コンポーネントが JSON 文字列、765では名前なし NBT なので、
 * クライアント行きのパケット（System Chat・Boss Bar・Teams など）の
 * テキスト部分をこのクラスで NBT 化する。
 * {@link Nbt#toJsonString(Tag)} の逆変換にあたる。
 *
 * <p>対応する JSON の形（テキストコンポーネントとして現れるもの）：
 * <ul>
 *   <li>文字列 → {@link Tag.StringTag}（素の文字列コンポーネント）。</li>
 *   <li>オブジェクト → {@link Tag.CompoundTag}（キーの順序を保つ）。
 *       {@code "text"}・{@code "translate"}・{@code "extra"} など。
 *       真偽値は NBT の ByteTag（0／1）にする（{@link Nbt#toJsonString(Tag)}
 *       が ByteTag を {@code true}／{@code false} に写すことの逆）。</li>
 *   <li>配列 → {@link Tag.ListTag}。NBT のリストは要素の型が1種類で
 *       なければならないため、文字列とコンパウンドが混ざる配列では
 *       文字列要素を {@code {"text": "..."}} のコンパウンドに包んで
 *       コンパウンドのリストにする（バニラの NBT コンポーネントも
 *       {@code extra} をコンパウンドのリストとして持つ）。</li>
 *   <li>数値 → 整数なら {@link Tag.IntTag}（long に収まらない
 *       ものは {@link Tag.LongTag}）、小数なら {@link Tag.DoubleTag}。</li>
 *   <li>{@code null} → NBT には null が無いため、オブジェクトでは
 *       そのキーを落とし、配列ではその要素を落とす（764サーバーが
 *       コンポーネントに null を送ることは通常無い）。</li>
 * </ul>
 *
 * <p>壊れた JSON（途中で切れている、閉じていない、余分な文字がある）が
 * 来た場合は {@link IllegalArgumentException} を投げる。呼び出し側は
 * そのパケットを落とすか素通しするかを決めること（無言で別な文字に
 * 置き換えることはしない）。
 */
public final class JsonToNbt {

    private JsonToNbt() {
    }

    /**
     * JSON 文字列を NBT タグに変換する。
     * {@code null} や空文字は空の文字列コンポーネント（{@code ""}）にする。
     */
    public static Tag fromJson(String json) {
        if (json == null || json.isBlank()) {
            return new Tag.StringTag("");
        }
        Parser parser = new Parser(json);
        Tag tag = parser.parseValue();
        parser.skipWhitespace();
        if (!parser.atEnd()) {
            throw new IllegalArgumentException(
                    "JSON の末尾に余分な文字があります（位置=" + parser.position() + "）。");
        }
        return tag == null ? new Tag.StringTag("") : tag;
    }

    /** JSON を1回だけ読む再帰下降パーサ。 */
    private static final class Parser {
        private final String text;
        private int pos;

        Parser(String text) {
            this.text = text;
            this.pos = 0;
        }

        int position() {
            return pos;
        }

        boolean atEnd() {
            return pos >= text.length();
        }

        void skipWhitespace() {
            while (pos < text.length()) {
                char c = text.charAt(pos);
                if (c == ' ' || c == '\t' || c == '\n' || c == '\r') {
                    pos++;
                } else {
                    break;
                }
            }
        }

        /** 値を1個読む。JSON の {@code null} の場合は {@code null} を返す。 */
        Tag parseValue() {
            skipWhitespace();
            if (atEnd()) {
                throw new IllegalArgumentException("JSON が途中で切れています。");
            }
            char c = text.charAt(pos);
            return switch (c) {
                case '{' -> parseObject();
                case '[' -> parseArray();
                case '"' -> new Tag.StringTag(parseString());
                case 't' -> {
                    expect("true");
                    yield new Tag.ByteTag((byte) 1);
                }
                case 'f' -> {
                    expect("false");
                    yield new Tag.ByteTag((byte) 0);
                }
                case 'n' -> {
                    expect("null");
                    yield null;
                }
                default -> parseNumber();
            };
        }

        private void expect(String literal) {
            if (!text.startsWith(literal, pos)) {
                throw new IllegalArgumentException(
                        "JSON のリテラルが不正です（位置=" + pos + "、期待=" + literal + "）。");
            }
            pos += literal.length();
        }

        private Tag parseObject() {
            pos++; // '{'
            Map<String, Tag> map = new LinkedHashMap<>();
            skipWhitespace();
            if (!atEnd() && text.charAt(pos) == '}') {
                pos++;
                return new Tag.CompoundTag(map);
            }
            while (true) {
                skipWhitespace();
                if (atEnd() || text.charAt(pos) != '"') {
                    throw new IllegalArgumentException(
                            "JSON オブジェクトのキーが文字列ではありません（位置=" + pos + "）。");
                }
                String key = parseString();
                skipWhitespace();
                if (atEnd() || text.charAt(pos) != ':') {
                    throw new IllegalArgumentException(
                            "JSON オブジェクトのキーと値の間に ':' がありません（位置=" + pos + "）。");
                }
                pos++; // ':'
                Tag value = parseValue();
                if (value != null) {
                    map.put(key, value);
                }
                skipWhitespace();
                if (atEnd()) {
                    throw new IllegalArgumentException("JSON オブジェクトが閉じていません。");
                }
                char next = text.charAt(pos);
                if (next == ',') {
                    pos++;
                    continue;
                }
                if (next == '}') {
                    pos++;
                    return new Tag.CompoundTag(map);
                }
                throw new IllegalArgumentException(
                        "JSON オブジェクトの区切りが不正です（位置=" + pos + "）。");
            }
        }

        private Tag parseArray() {
            pos++; // '['
            List<Tag> elements = new ArrayList<>();
            skipWhitespace();
            if (!atEnd() && text.charAt(pos) == ']') {
                pos++;
                // 空の配列：要素型は STRING としておく（toJsonString は空リストを [] にする）。
                return new Tag.ListTag(Nbt.STRING, elements);
            }
            while (true) {
                Tag value = parseValue();
                if (value != null) {
                    elements.add(value);
                }
                skipWhitespace();
                if (atEnd()) {
                    throw new IllegalArgumentException("JSON 配列が閉じていません。");
                }
                char next = text.charAt(pos);
                if (next == ',') {
                    pos++;
                    continue;
                }
                if (next == ']') {
                    pos++;
                    return toListTag(elements);
                }
                throw new IllegalArgumentException(
                        "JSON 配列の区切りが不正です（位置=" + pos + "）。");
            }
        }

        /** 要素の型をそろえて ListTag にする（混ざる場合は文字列を包む）。 */
        private static Tag toListTag(List<Tag> elements) {
            boolean hasCompound = false;
            boolean hasOther = false;
            int elementType = -1;
            for (Tag element : elements) {
                if (element instanceof Tag.CompoundTag) {
                    hasCompound = true;
                } else if (elementType < 0) {
                    elementType = element.type();
                } else if (element.type() != elementType) {
                    hasOther = true;
                }
            }
            if (hasCompound && (hasOther || elementType >= 0)) {
                // コンパウンドとそれ以外が混ざる場合：文字列は {"text": ...} に包み、
                // 数値・真偽値も文字列化して包む（コンポーネントの extra と同じ形）。
                List<Tag> wrapped = new ArrayList<>(elements.size());
                for (Tag element : elements) {
                    if (element instanceof Tag.CompoundTag) {
                        wrapped.add(element);
                    } else if (element instanceof Tag.StringTag s) {
                        wrapped.add(new Tag.CompoundTag(Map.of("text", new Tag.StringTag(s.value()))));
                    } else {
                        wrapped.add(new Tag.CompoundTag(
                                Map.of("text", new Tag.StringTag(Nbt.toJsonString(element)))));
                    }
                }
                return new Tag.ListTag(Nbt.COMPOUND, wrapped);
            }
            if (elementType < 0) {
                if (hasCompound) {
                    // 要素がすべてコンパウンドの場合（例：転送JSONの
                    // properties 配列）。要素を落とさずコンパウンドの
                    // リストとして返す。
                    return new Tag.ListTag(Nbt.COMPOUND, elements);
                }
                // 要素がすべて null だった場合：空の文字列リスト。
                return new Tag.ListTag(Nbt.STRING, List.of());
            }
            return new Tag.ListTag(elementType, elements);
        }

        private String parseString() {
            // 呼び出し側が '"' を確認済み。
            pos++; // '"'
            StringBuilder out = new StringBuilder();
            while (pos < text.length()) {
                char c = text.charAt(pos++);
                if (c == '"') {
                    return out.toString();
                }
                if (c == '\\') {
                    if (pos >= text.length()) {
                        break;
                    }
                    char escaped = text.charAt(pos++);
                    switch (escaped) {
                        case '"' -> out.append('"');
                        case '\\' -> out.append('\\');
                        case '/' -> out.append('/');
                        case 'b' -> out.append('\b');
                        case 'f' -> out.append('\f');
                        case 'n' -> out.append('\n');
                        case 'r' -> out.append('\r');
                        case 't' -> out.append('\t');
                        case 'u' -> {
                            if (pos + 4 > text.length()) {
                                throw new IllegalArgumentException(
                                        "JSON の \\u エスケープが途中で切れています。");
                            }
                            int code = Integer.parseInt(text.substring(pos, pos + 4), 16);
                            pos += 4;
                            out.append((char) code);
                        }
                        default -> throw new IllegalArgumentException(
                                "JSON のエスケープが不正です（\\" + escaped + "）。");
                    }
                    continue;
                }
                out.append(c);
            }
            throw new IllegalArgumentException("JSON の文字列が閉じていません。");
        }

        private Tag parseNumber() {
            int start = pos;
            if (pos < text.length() && text.charAt(pos) == '-') {
                pos++;
            }
            boolean isDouble = false;
            while (pos < text.length()) {
                char c = text.charAt(pos);
                if (c >= '0' && c <= '9') {
                    pos++;
                } else if (c == '.' || c == 'e' || c == 'E' || c == '+' || c == '-') {
                    isDouble = true;
                    pos++;
                } else {
                    break;
                }
            }
            if (pos == start) {
                throw new IllegalArgumentException(
                        "JSON の値が不正です（位置=" + start + "）。");
            }
            String number = text.substring(start, pos);
            try {
                if (isDouble) {
                    return new Tag.DoubleTag(Double.parseDouble(number));
                }
                long value = Long.parseLong(number);
                if (value >= Integer.MIN_VALUE && value <= Integer.MAX_VALUE) {
                    return new Tag.IntTag((int) value);
                }
                return new Tag.LongTag(value);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("JSON の数値が不正です: " + number, e);
            }
        }
    }
}
