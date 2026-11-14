#!/usr/bin/env python3
"""RegistryRemapper.java の 773 展開済み表を RegistryTables773Expanded.java へ移す。

move-772-expanded-tables.py の 773 版。理由は同じ：773 の展開済み表
（adjacent 12＋composed 126＋up 6＝144 フィールド）を RegistryRemapper.java
に入れると <clinit> が 64KB 制限を超えて "code too large" でコンパイル
できなくなる。表そのものは RegistryTables773*.java の RANGES／OVERRIDES から
expand()／invert() で作る設計のまま、置き場所だけ別クラスへ移す。
アクセサ側の参照は static import で解決するため、メソッド本体は一切変わらない。

移動するブロック（行番号は実行時に内容で確認する）：
  A. 隣接表 *_773_MAP 12件（BLOCK_STATE_773_MAP〜ENCHANTMENT_REVERSE_773_MAP）
  B. 合成表 126件（BLOCK_STATE_763_TO_773_MAP〜MENU_773_TO_771_MAP）
  C. 反転表 *_UP_773_MAP 6件（BLOCK_STATE_UP_773_MAP〜MENU_UP_773_MAP）
expand()／invert() は 772 の移動時に既に package-private になっているため、
ここでは変えない（private のままなら失敗させる）。

注意：773 の各ブロックは 771 のブロックの直後に挿入されている（772 は
RegistryTables772Expanded.java へ移動済みのため RegistryRemapper.java には
無い）。そのため境界の確認は 771 ベースで行う。
"""
import sys
from pathlib import Path

PROTO = Path(__file__).resolve().parent.parent / "src" / "main" / "java" \
    / "io" / "detergentmc" / "protocol"
REMAPPER = PROTO / "RegistryRemapper.java"
EXPANDED = PROTO / "RegistryTables773Expanded.java"

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
a_start = find("private static final int[] BLOCK_STATE_773_MAP =")
a_end = field_end(find("private static final int[] ENCHANTMENT_REVERSE_773_MAP ="))
b_start = find("private static final int[] BLOCK_STATE_763_TO_773_MAP =")
b_end = field_end(find("private static final int[] MENU_773_TO_771_MAP ="))
c_start = find("private static final int[] BLOCK_STATE_UP_773_MAP = invert(")
c_end = field_end(find("private static final int[] MENU_UP_773_MAP = invert("))
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
anchor = "import static io.detergentmc.protocol.RegistryTables773Down771.*;\n"
assert java.count(anchor) == 1
java = java.replace(
    anchor,
    anchor + "import static io.detergentmc.protocol.RegistryTables773Expanded.*;\n",
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
header = """package io.detergentmc.protocol;

import static io.detergentmc.protocol.RegistryRemapper.*;
import static io.detergentmc.protocol.RegistryTables773.*;
import static io.detergentmc.protocol.RegistryTables773ComposedUp.*;
import static io.detergentmc.protocol.RegistryTables773Down763.*;
import static io.detergentmc.protocol.RegistryTables773Down764.*;
import static io.detergentmc.protocol.RegistryTables773Down765.*;
import static io.detergentmc.protocol.RegistryTables773Down766.*;
import static io.detergentmc.protocol.RegistryTables773Down767.*;
import static io.detergentmc.protocol.RegistryTables773Down768.*;
import static io.detergentmc.protocol.RegistryTables773Down769.*;
import static io.detergentmc.protocol.RegistryTables773Down770.*;
import static io.detergentmc.protocol.RegistryTables773Down771.*;

/**
 * 773 の展開済み表（隣接表 {@code *_773_MAP}・合成表 {@code *_X_TO_773_MAP}／
 * {@code *_773_TO_X_MAP}・反転表 {@code *_UP_773_MAP}）。
 *
 * <p>中身は RegistryTables773*.java の RANGES／OVERRIDES を
 * {@link RegistryRemapper} の expand()／invert() で展開したもので、771 までと
 * 同じ作り方。置き場所だけ別クラスにしているのは、RegistryRemapper.java の
 * {@code <clinit>} が 64KB 制限を超えてコンパイルできなくなったため
 * （773 の 144 フィールド分で制限を越えた）。
 */
final class RegistryTables773Expanded {

"""
EXPANDED.write_text(header + "".join(fields) + "}\n", encoding="utf-8")
print("RegistryTables773Expanded.java を書き出した（"
      f"{sum(len(b) for b in blocks)} 行）")
