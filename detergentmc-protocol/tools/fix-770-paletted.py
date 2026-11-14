#!/usr/bin/env python3
"""ChunkTranslator.java の770パレット付きコンテナ翻訳子を修正する。

実バニラ1.21.5のチャンクで確認：1.21.5（770）ではパレット付きコンテナの
Data Array に長さの varint 接頭辞が無い。長さは bitsPerEntry とエントリ数から
計算する（BedrockMatrixIntegrationTest.matrixAgainst1_21_5 で確定）。

- translatePalettedContainer769To770：769側の長さ varint を読むが、770側には書かない。
- translatePalettedContainer770To769：770側の長さは読まない（bpe から計算）。769側には書く。
"""
from pathlib import Path

P = Path("/home/hatch/workspace/detergentmc/detergentmc-protocol/src/main/java/io/detergentmc/protocol/ChunkTranslator.java")
text = P.read_text(encoding="utf-8")

NEW_769_TO_770 = '''    private static void translatePalettedContainer769To770(Reader reader, Writer writer, boolean blocks) {
        int bitsPerEntry = reader.readUnsignedByte();
        if (bitsPerEntry > 32) {
            throw new IllegalArgumentException("Bits Per Entry が大きすぎます: " + bitsPerEntry);
        }
        writer.writeUnsignedByte(bitsPerEntry);
        int entryCount = blocks ? 4096 : 64;
        if (bitsPerEntry == 0) {
            int value = reader.readVarInt();
            writer.writeVarInt(blocks
                    ? RegistryRemapper.mapBlockState769To770(value)
                    : mapBiome769To770(value));
            // 769側は長さ varint（0）を読むが、770側には長さ接頭辞が無いため書かない。
            int dataLength = reader.readVarInt();
            if (dataLength != 0) {
                throw new IllegalArgumentException(
                        "シングルパレットの Data Array 長が 0 ではありません: " + dataLength);
            }
            return;
        }
        boolean indirect = blocks ? bitsPerEntry <= 8 : bitsPerEntry <= 3;
        if (indirect) {
            int paletteLength = reader.readVarInt();
            if (paletteLength <= 0 || paletteLength > (1 << bitsPerEntry)) {
                throw new IllegalArgumentException("パレット長が不正です: " + paletteLength);
            }
            writer.writeVarInt(paletteLength);
            for (int i = 0; i < paletteLength; i++) {
                int value = reader.readVarInt();
                writer.writeVarInt(blocks
                        ? RegistryRemapper.mapBlockState769To770(value)
                        : mapBiome769To770(value));
            }
            // 間接パレットの Data Array はインデックス列なので中身は変えない。
            // 769側の長さ varint を読むが、770側には書かない。
            copyLongArrayNoLengthPrefix(reader, writer, entryCount, bitsPerEntry);
            return;
        }
        // 直接パレット：Data Array の中身がレジストリ番号そのもの。
        int dataLength = reader.readVarInt();
        int perLong = 64 / bitsPerEntry;
        int expected = (entryCount + perLong - 1) / perLong;
        if (dataLength != expected) {
            throw new IllegalArgumentException("直接パレットの Data Array 長が想定と違います: "
                    + dataLength + "（想定=" + expected + "）。");
        }
        // 770側には長さ接頭辞が無いため書かない。
        long[] longs = new long[dataLength];
        for (int i = 0; i < dataLength; i++) {
            longs[i] = reader.readLong();
        }
        remapDirectEntries769To770(longs, bitsPerEntry, entryCount, blocks);
        for (long value : longs) {
            writer.writeLong(value);
        }
    }
'''

NEW_770_TO_769 = '''    private static void translatePalettedContainer770To769(Reader reader, Writer writer, boolean blocks) {
        int bitsPerEntry = reader.readUnsignedByte();
        if (bitsPerEntry > 32) {
            throw new IllegalArgumentException("Bits Per Entry が大きすぎます: " + bitsPerEntry);
        }
        writer.writeUnsignedByte(bitsPerEntry);
        int entryCount = blocks ? 4096 : 64;
        if (bitsPerEntry == 0) {
            int value = reader.readVarInt();
            writer.writeVarInt(blocks
                    ? RegistryRemapper.mapBlockStateUp770(value)
                    : mapBiomeUp770(value));
            // 770側には長さ接頭辞が無いため読まない。769側には長さ 0 を書く。
            writer.writeVarInt(0);
            return;
        }
        boolean indirect = blocks ? bitsPerEntry <= 8 : bitsPerEntry <= 3;
        if (indirect) {
            int paletteLength = reader.readVarInt();
            if (paletteLength <= 0 || paletteLength > (1 << bitsPerEntry)) {
                throw new IllegalArgumentException("パレット長が不正です: " + paletteLength);
            }
            writer.writeVarInt(paletteLength);
            for (int i = 0; i < paletteLength; i++) {
                int value = reader.readVarInt();
                writer.writeVarInt(blocks
                        ? RegistryRemapper.mapBlockStateUp770(value)
                        : mapBiomeUp770(value));
            }
            // 770側には長さ接頭辞が無い。長さは bpe から計算し、769側に書く。
            int perLong = 64 / bitsPerEntry;
            int expected = (entryCount + perLong - 1) / perLong;
            writer.writeVarInt(expected);
            for (int i = 0; i < expected; i++) {
                writer.writeLong(reader.readLong());
            }
            return;
        }
        // 直接パレット：770側には長さ接頭辞が無い。長さは bpe から計算し、769側に書く。
        int perLong = 64 / bitsPerEntry;
        int expected = (entryCount + perLong - 1) / perLong;
        writer.writeVarInt(expected);
        long[] longs = new long[expected];
        for (int i = 0; i < expected; i++) {
            longs[i] = reader.readLong();
        }
        remapDirectEntries770To769(longs, bitsPerEntry, entryCount, blocks);
        for (long value : longs) {
            writer.writeLong(value);
        }
    }
'''

NEW_HELPER = '''    /** 間接パレットの Data Array を読む。長さ varint を読んで検証するが、
     *  770側には長さ接頭辞が無いため書かず、long だけを書く（769→770用）。 */
    private static void copyLongArrayNoLengthPrefix(Reader reader, Writer writer,
            int entryCount, int bitsPerEntry) {
        int length = reader.readVarInt();
        int perLong = 64 / bitsPerEntry;
        int expected = (entryCount + perLong - 1) / perLong;
        if (length != expected) {
            throw new IllegalArgumentException("Data Array 長が想定と違います: " + length
                    + "（想定=" + expected + "）。");
        }
        for (int i = 0; i < length; i++) {
            writer.writeLong(reader.readLong());
        }
    }

'''


def replace_method(text, signature, new_text, label):
    idx = text.find(signature)
    assert idx != -1, f"signature not found: {label}"
    assert text.find(signature, idx + 1) == -1, f"signature not unique: {label}"
    # javadoc / コメントをさかのぼる（直前の空行まで）
    start = idx
    while True:
        prev_nl = text.rfind("\n", 0, start - 1)
        line = text[prev_nl + 1:start - 1] if prev_nl != -1 else ""
        stripped = line.strip()
        if stripped.startswith("/**") or stripped.startswith("*") or stripped.startswith("//"):
            start = prev_nl + 1
            if stripped.startswith("/**"):
                break
            continue
        break
    brace = text.find("{", idx)
    assert brace != -1
    depth = 0
    i = brace
    while i < len(text):
        if text[i] == "{":
            depth += 1
        elif text[i] == "}":
            depth -= 1
            if depth == 0:
                break
        i += 1
    assert depth == 0, f"unbalanced braces: {label}"
    end = i + 1
    return text[:start] + new_text + text[end:]


text = replace_method(text,
        "private static void translatePalettedContainer769To770(Reader reader, Writer writer, boolean blocks) {",
        NEW_769_TO_770, "769To770")
text = replace_method(text,
        "private static void translatePalettedContainer770To769(Reader reader, Writer writer, boolean blocks) {",
        NEW_770_TO_769, "770To769")

# ヘルパーを copyLongArray の直前に挿入
anchor = "    private static void copyLongArray(Reader reader, Writer writer) {"
assert text.count(anchor) == 1
text = text.replace(anchor, NEW_HELPER + anchor, 1)

P.write_text(text, encoding="utf-8")
print("patched ChunkTranslator.java paletted containers for 770 (no length prefix)")
