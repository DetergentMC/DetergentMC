#!/usr/bin/env python3
"""ChunkTranslator.java の BitStorage 不具合を修正する。

不具合（2026-11-14、Bedrockマトリクス26.1で発覚）：
バニラの BitStorage はエントリを long の境界をまたいで連続して詰める。
旧式の「1 long あたり perLong = 64 / bits 個」という読み方は、bits が 64 を
割り切らないとき（3・5・6・7・9…）に (a) long 数を多めに数え、(b) またぐ
エントリの上位ビットを落とす。間接パレットでは (a) だけでも後続のコンテナが
ずれて読めなくなる（3 ビットのバイオームで 4 long 読んで 1 long 余計に進む）。

修正は2つ：
1. 全34箇所の expected 計算を (entryCount * bitsPerEntry + 63) / 64 にする。
   perLong 行も併せて削除する（expected 以外に使われていないことを確認済み）。
2. 全22個の remapDirectEntries* の unpack/repack ループを、境界をまたぐ
   エントリに対応したものに書き換える。mapped 式は元のものを流用する
   （変数 entry は entries[index] に置き換える）。
"""
import re
import sys

PATH = "/home/hatch/workspace/detergentmc/detergentmc-protocol/src/main/java/io/detergentmc/protocol/ChunkTranslator.java"

src = open(PATH, encoding="utf-8").read()

# ---- 1. expected の修正（34箇所） ----
COMMENT = (
    "// バニラの BitStorage はエントリを long の境界をまたいで連続して詰めるため、\n"
    "{I}// long 数は (entryCount * bitsPerEntry + 63) / 64。旧式の\n"
    "{I}// (entryCount + perLong - 1) / perLong（perLong = 64 / bits）は bits が 64 を\n"
    "{I}// 割り切らないとき（3・5・6・7・9…）に多めに数え、後続のコンテナがずれて\n"
    "{I}// 読めなくなる（2026-11-14、26.1のBedrockマトリクスで発覚）。"
)


def fix_expected(m):
    indent = m.group(1)
    comment = COMMENT.replace("{I}", indent)
    return (
        comment + "\n"
        + indent + "int expected = (entryCount * bitsPerEntry + 63) / 64;"
    )


src2, n_expected = re.subn(
    r"(\s*)int perLong = 64 / bitsPerEntry;\n\s*int expected = \(entryCount \+ perLong - 1\) / perLong;",
    fix_expected,
    src,
)
print(f"expected fixed: {n_expected}")
assert n_expected == 34, f"expected {n_expected} != 34"

# ---- 2. remapDirectEntries* のループ書き換え（22個） ----
LOOP_RE = re.compile(
    r"(?P<ind>[ ]*)int perLong = 64 / bitsPerEntry;\n"
    r"(?P=ind)long mask = \(1L << bitsPerEntry\) - 1;\n"
    r"(?P=ind)int index = 0;\n"
    r"(?P=ind)for \(int i = 0; i < longs\.length && index < entryCount; i\+\+\) \{\n"
    r"(?P=ind)    long value = longs\[i\];\n"
    r"(?P=ind)    long rewritten = 0;\n"
    r"(?P=ind)    for \(int slot = 0; slot < perLong && index < entryCount; slot\+\+, index\+\+\) \{\n"
    r"(?P=ind)        int shift = slot \* bitsPerEntry;\n"
    r"(?P=ind)        int entry = \(int\) \(\(value >>> shift\) & mask\);\n"
    r"(?P<mapped>.*?)\n"
    r"(?P=ind)        rewritten \|= \(\(long\) mapped & mask\) << shift;\n"
    r"(?P=ind)    \}\n"
    r"(?P=ind)    longs\[i\] = rewritten;\n"
    r"(?P=ind)\}\n"
    r"(?P=ind)if \(index != entryCount\) \{\n"
    r"(?P=ind)    throw new IllegalArgumentException\(\n"
    r"(?P=ind)            \"直接パレットのエントリ数が合いません（読めた=\" \+ index \+ \"、想定=\" \+ entryCount \+ \"）。\"\);\n"
    r"(?P=ind)\}",
    re.DOTALL,
)


def build_mapped_lines(mapped_text, ind1, ind2):
    """mapped 計算部分を新しい repack ループ用に整形する。

    2つの形がある：
    (a) 1つの式 `int mapped = <expr>;`（19個）
    (b) `int mapped;` + if/else 文（762/761/760の3個）
    どちらも変数 entry を entries[index] に置き換え、インデントを揃える。
    """
    text = re.sub(r"\bentry\b", "entries[index]", mapped_text)
    lines = text.split("\n")
    # 先頭行のインデントを基準に、相対インデントを保って付け直す。
    base = len(lines[0]) - len(lines[0].lstrip())
    out = []
    for line in lines:
        stripped = line.strip()
        if not stripped:
            continue
        rel = (len(line) - len(line.lstrip())) - base
        out.append(" " * (len(ind1) + rel) + stripped)
    return "\n".join(out)


def build_new_loop(m):
    ind = m.group("ind")
    ind1 = ind + "    "
    ind2 = ind + "        "
    mapped_block = build_mapped_lines(m.group("mapped"), ind1, ind2)
    return (
        ind + "long mask = (1L << bitsPerEntry) - 1;\n"
        + ind + "// エントリは long の境界をまたぐことがある（bits が 64 を割り切らないとき）。\n"
        + ind + "// BitStorage.get と同じく、またぐ分は次の long の下位ビットから取る。\n"
        + ind + "// 旧式の「1 long あたり perLong 個を先頭から読む」は、またぐエントリの\n"
        + ind + "// 上位ビットを落として誤った値を返していた（2026-11-14）。\n"
        + ind + "int needed = (entryCount * bitsPerEntry + 63) / 64;\n"
        + ind + "if (longs.length < needed) {\n"
        + ind1 + "throw new IllegalArgumentException(\n"
        + ind1 + "        \"直接パレットの Data Array 長が想定より短いです（渡された=\" + longs.length + \"、想定=\" + needed + \"）。\");\n"
        + ind + "}\n"
        + ind + "int[] entries = new int[entryCount];\n"
        + ind + "int bitPos = 0;\n"
        + ind + "for (int index = 0; index < entryCount; index++) {\n"
        + ind1 + "int cellIndex = bitPos >> 6;\n"
        + ind1 + "int bitIndex = bitPos & 63;\n"
        + ind1 + "long low = longs[cellIndex];\n"
        + ind1 + "int entry;\n"
        + ind1 + "if (bitIndex + bitsPerEntry <= 64) {\n"
        + ind2 + "entry = (int) ((low >>> bitIndex) & mask);\n"
        + ind1 + "} else {\n"
        + ind2 + "int lowBits = 64 - bitIndex;\n"
        + ind2 + "long high = cellIndex + 1 < longs.length ? longs[cellIndex + 1] : 0L;\n"
        + ind2 + "entry = (int) (((low >>> bitIndex) | (high << lowBits)) & mask);\n"
        + ind1 + "}\n"
        + ind1 + "entries[index] = entry;\n"
        + ind1 + "bitPos += bitsPerEntry;\n"
        + ind + "}\n"
        + ind + "for (int i = 0; i < longs.length; i++) {\n"
        + ind1 + "longs[i] = 0L;\n"
        + ind + "}\n"
        + ind + "bitPos = 0;\n"
        + ind + "for (int index = 0; index < entryCount; index++) {\n"
        + mapped_block + "\n"
        + ind1 + "int cellIndex = bitPos >> 6;\n"
        + ind1 + "int bitIndex = bitPos & 63;\n"
        + ind1 + "longs[cellIndex] |= ((long) mapped & mask) << bitIndex;\n"
        + ind1 + "if (bitIndex + bitsPerEntry > 64) {\n"
        + ind2 + "int lowBits = 64 - bitIndex;\n"
        + ind2 + "longs[cellIndex + 1] |= ((long) mapped & mask) >>> lowBits;\n"
        + ind1 + "}\n"
        + ind1 + "bitPos += bitsPerEntry;\n"
        + ind + "}"
    )


src3, n_loops = LOOP_RE.subn(build_new_loop, src2)
print(f"remap loops fixed: {n_loops}")
assert n_loops == 22, f"loops {n_loops} != 22"

# 残っている旧式がないか確認する。
left_expected = len(re.findall(r"int expected = \(entryCount \+ perLong - 1\) / perLong;", src3))
left_perlong = len(re.findall(r"int perLong = 64 / bitsPerEntry;", src3))
left_shift = len(re.findall(r"int shift = slot \* bitsPerEntry;", src3))
print(f"remaining old expected: {left_expected}, perLong: {left_perlong}, shift-loop: {left_shift}")
assert left_expected == 0 and left_perlong == 0 and left_shift == 0

open(PATH, "w", encoding="utf-8").write(src3)
print("OK, wrote", PATH)
