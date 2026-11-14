#!/usr/bin/env python3
"""RegistryRemapper.java の 774 展開済み表を RegistryTables774Expanded.java へ移す。

move-773-expanded-tables.py の 774 版。理由は同じ：774 の展開済み表
（adjacent 12＋composed 140＋up 6＝158 フィールド）を RegistryRemapper.java
に入れた時点で <clinit> は 61,817 バイトまで膨らみ、64KB 制限まで残り
3,718 バイトしかない。次の版（775）を足すと制限を越えて "code too large"
でコンパイルできなくなるため、774 分を先に別クラスへ移す。
表そのものは RegistryTables774*.java の RANGES／OVERRIDES から
expand()／invert() で作る設計のまま、置き場所だけ別クラスへ移す。
アクセサ側の参照は static import で解決するため、メソッド本体は一切変わらない。

移動するブロック（行番号は実行時に内容で確認する）：
  A. 隣接表 *_774_MAP 12件（BLOCK_STATE_774_MAP〜ENCHANTMENT_REVERSE_774_MAP）
  B. 合成表 140件（BLOCK_STATE_763_TO_774_MAP〜MENU_774_TO_772_MAP）
  C. 反転表 *_UP_774_MAP 6件（BLOCK_STATE_UP_774_MAP〜MENU_UP_774_MAP）
expand()／invert() は 772 の移動時に既に package-private になっているため、
ここでは変えない（private のままなら失敗させる）。

注意：774 の各ブロックは 771 のブロックの直後に挿入されている（772・773 は
RegistryTables772Expanded.java／RegistryTables773Expanded.java へ移動済みのため
RegistryRemapper.java には無い）。そのため境界の確認は 771 ベースで行う。
"""
import sys
from pathlib import Path

PROTO = Path(__file__).resolve().parent.parent / "src" / "main" / "java" \
    / "io" / "detergentmc" / "protocol"
REMAPPER = PROTO / "RegistryRemapper.java"
EXPANDED = PROTO / "RegistryTables774Expanded.java"

lines = REMAPPER.read_text(encoding="utf-8").splitlines(keepends=True)


def find(marker):
    hits = [i for i, line in enumerate(lines) if marker in line]
    assert len(hits) == 1, f"{marker}: {len(hits)} 件"
    return hits[0]


def field_end(start):
    """フィールド開始行から、閉じの `);` の行まで進む。"""
    i = start
    while not lines[i].rstrip().endswith(");"):
        i += 1
        assert i - start <= 5, "フィールドが長すぎる"
    return i


# ブロック境界（内容で確認）。
a_start = find("private static final int[] BLOCK_STATE_774_MAP =")
a_end = field_end(find("private static final int[] ENCHANTMENT_REVERSE_774_MAP ="))
b_start = find("private static final int[] BLOCK_STATE_763_TO_774_MAP =")
b_end = field_end(find("private static final int[] MENU_774_TO_772_MAP ="))
c_start = find("private static final int[] BLOCK_STATE_UP_774_MAP = invert(")
c_end = field_end(find("private static final int[] MENU_UP_774_MAP = invert("))
assert a_start < a_end < b_start < b_end < c_start < c_end
# 境界の直前・直後が想定通りであること。
assert "MENU_771_MAP" in lines[a_start - 3] or "CRAFTING_MENU_771);" in lines[a_start - 1]
assert "ITEM_REVERSE_771_MAP" in lines[a_end + 1]
assert "MENU_771_TO_769_MAP" in lines[b_start - 4]
assert "逆向き（764→765）" in lines[b_end + 3]
assert "MENU_UP_771_MAP = invert(" in lines[c_start - 4]
assert "SOUND_NAME_FALLBACK_MAP" in lines[c_end + 2]

blocks = [
    lines[a_start:a_end + 1],
    lines[b_start:b_end + 1],
    lines[c_start:c_end + 1],
]

# RegistryRemapper.java から 3 ブロックを除く（後ろから消す）。
for start, end in sorted(
        [(a_start, a_end), (b_start, b_end), (c_start, c_end)],
        reverse=True):
    del lines[start:end + 1]
java = "".join(lines)

# B の直後に残る空行 2 連を 1 つにする（B の前後に空行が 1 つずつあったため）。
java = java.replace(
    "                    25, CRAFTING_MENU_769);\n\n\n    // ------------------------------------------------------------------\n"
    "    // 逆向き（764→765）の表。",
    "                    25, CRAFTING_MENU_769);\n\n    // ------------------------------------------------------------------\n"
    "    // 逆向き（764→765）の表。")

# 展開済み表のための static import を追加。
anchor = "import static io.detergentmc.protocol.RegistryTables774Down772.*;\n"
assert java.count(anchor) == 1
java = java.replace(
    anchor,
    anchor + "import static io.detergentmc.protocol.RegistryTables774Expanded.*;\n",
    1)

# expand()／invert() は 772 の移動時に既に package-private になっていること。
# private のままだったら、ここで失敗させて原因を調べる。
assert java.count("    static int[] expand(") == 1, "expand() が package-private でない"
assert java.count("    static int[] invert(") == 1, "invert() が package-private でない"
assert "    private static int[] expand(" not in java
assert "    private static int[] invert(" not in java

REMAPPER.write_text(java, encoding="utf-8")

# 別クラスへ移す（private を外して package-private にする）。
fields = []
for block in blocks:
    for line in block:
        fields.append(line.replace("    private static final int[]",
                                   "    static final int[]", 1))
imports = "\n".join(
    "import static io.detergentmc.protocol.RegistryTables774Down%s.*;\n"
    "import static io.detergentmc.protocol.RegistryTables774Down%sBlockState.*;" % (s, s)
    for s in ("763", "764", "765", "766", "767", "768", "769", "770", "771", "772"))
header = """package io.detergentmc.protocol;

import static io.detergentmc.protocol.RegistryRemapper.*;
import static io.detergentmc.protocol.RegistryTables774.*;
import static io.detergentmc.protocol.RegistryTables774ComposedUp.*;
""" + imports + """

/**
 * 774 の展開済み表（隣接表 {@code *_774_MAP}・合成表 {@code *_X_TO_774_MAP}／
 * {@code *_774_TO_X_MAP}・反転表 {@code *_UP_774_MAP}）。
 *
 * <p>中身は RegistryTables774*.java の RANGES／OVERRIDES を
 * {@link RegistryRemapper} の expand()／invert() で展開したもので、773 までと
 * 同じ作り方。置き場所だけ別クラスにしているのは、RegistryRemapper.java の
 * {@code <clinit>} が 64KB 制限に迫ったため。774 の 158 フィールド分を入れた
 * 時点で 61,817 バイト（残り 3,718 バイト）まで膨らんでいたので、次の版で
 * 制限を越える前に移してある。
 */
final class RegistryTables774Expanded {

"""
EXPANDED.write_text(header + "".join(fields) + "}\n", encoding="utf-8")
print("RegistryTables774Expanded.java を書き出した（"
      f"{sum(len(b) for b in blocks)} 行）")
