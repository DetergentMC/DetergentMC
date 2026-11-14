#!/usr/bin/env python3
"""RegistryRemapper.java の 772 展開済み表を RegistryTables772Expanded.java へ移す。

理由：772 の展開済み表（adjacent 12＋composed 112＋up 6＝130 フィールド）を
RegistryRemapper.java に入れると <clinit> が 64KB 制限を超えて
"code too large" でコンパイルできなくなる（771 までは制限内だった）。
表そのものは RegistryTables772*.java の RANGES／OVERRIDES から expand()／
invert() で作る設計のまま、置き場所だけ別クラスへ移す。アクセサ側の参照は
static import で解決するため、メソッド本体は一切変わらない。

移動するブロック（行番号は実行時に内容で確認する）：
  A. 隣接表 *_772_MAP 12件（BLOCK_STATE_772_MAP〜ENCHANTMENT_REVERSE_772_MAP）
  B. 合成表 112件（BLOCK_STATE_763_TO_772_MAP〜MENU_772_TO_770_MAP）
  C. 反転表 *_UP_772_MAP 6件（BLOCK_STATE_UP_772_MAP〜MENU_UP_772_MAP）
expand()／invert() は RegistryRemapper 内の private を package-private に
変えて、別クラスから呼べるようにする（同一パッケージ）。
"""
import sys
from pathlib import Path

PROTO = Path(__file__).resolve().parent.parent / "src" / "main" / "java" \
    / "io" / "detergentmc" / "protocol"
REMAPPER = PROTO / "RegistryRemapper.java"
EXPANDED = PROTO / "RegistryTables772Expanded.java"

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
a_start = find("private static final int[] BLOCK_STATE_772_MAP =")
a_end = field_end(find("private static final int[] ENCHANTMENT_REVERSE_772_MAP ="))
b_start = find("private static final int[] BLOCK_STATE_763_TO_772_MAP =")
b_end = field_end(find("private static final int[] MENU_772_TO_770_MAP ="))
c_start = find("private static final int[] BLOCK_STATE_UP_772_MAP = invert(")
c_end = field_end(find("private static final int[] MENU_UP_772_MAP = invert("))
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
anchor = "import static io.detergentmc.protocol.RegistryTables772Down770.*;\n"
assert java.count(anchor) == 1
java = java.replace(
    anchor,
    anchor + "import static io.detergentmc.protocol.RegistryTables772Expanded.*;\n",
    1)

# expand()／invert() を package-private にする（同じパッケージの
# RegistryTables772Expanded から呼ぶため）。
assert java.count("    private static int[] expand(") == 1
assert java.count("    private static int[] invert(") == 1
java = java.replace("    private static int[] expand(", "    static int[] expand(", 1)
java = java.replace("    private static int[] invert(", "    static int[] invert(", 1)

REMAPPER.write_text(java, encoding="utf-8")

# 別クラスへ移す（private を外して package-private にする）。
fields = []
for block in blocks:
    for line in block:
        fields.append(line.replace("    private static final int[]",
                                   "    static final int[]", 1))
header = """package io.detergentmc.protocol;

import static io.detergentmc.protocol.RegistryRemapper.*;
import static io.detergentmc.protocol.RegistryTables772.*;
import static io.detergentmc.protocol.RegistryTables772ComposedUp.*;
import static io.detergentmc.protocol.RegistryTables772Down763.*;
import static io.detergentmc.protocol.RegistryTables772Down764.*;
import static io.detergentmc.protocol.RegistryTables772Down765.*;
import static io.detergentmc.protocol.RegistryTables772Down766.*;
import static io.detergentmc.protocol.RegistryTables772Down767.*;
import static io.detergentmc.protocol.RegistryTables772Down768.*;
import static io.detergentmc.protocol.RegistryTables772Down769.*;
import static io.detergentmc.protocol.RegistryTables772Down770.*;

/**
 * 772 の展開済み表（隣接表 {@code *_772_MAP}・合成表 {@code *_X_TO_772_MAP}／
 * {@code *_772_TO_X_MAP}・反転表 {@code *_UP_772_MAP}）。
 *
 * <p>中身は RegistryTables772*.java の RANGES／OVERRIDES を
 * {@link RegistryRemapper} の expand()／invert() で展開したもので、771 までと
 * 同じ作り方。置き場所だけ別クラスにしているのは、RegistryRemapper.java の
 * {@code <clinit>} が 64KB 制限を超えてコンパイルできなくなったため
 * （772 の 130 フィールド分で制限を越えた）。
 */
final class RegistryTables772Expanded {

"""
EXPANDED.write_text(header + "".join(fields) + "}\n", encoding="utf-8")
print("RegistryTables772Expanded.java を書き出した（"
      f"{sum(len(b) for b in blocks)} 行）")
