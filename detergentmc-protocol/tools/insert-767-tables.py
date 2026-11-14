#!/usr/bin/env python3
"""tools/generate-remap-tables.py --target 767 の出力を
RegistryRemapper.java に挿入する（1回だけの作業用）。

  - *_767 表（766→767）と合成表（763↔767、764↔767、765↔767）と
    属性・エンチャントの表（int[][]）を MENU_766_TO_764 表の直後に挿入する。
  - SOUND_NAME_FALLBACKS_767／_766_TO_767／_767_TO_766／_767_TO_765／
    _767_TO_763／_767_TO_764（String[][]）と ATTRIBUTE_766_NAMES／
    ATTRIBUTE_767_NAMES（String[]）を
    SOUND_NAME_FALLBACKS_766_TO_765 表の直後に挿入する。
  - 挿入する表が既にある場合は何もせず終了コード 1 で失敗する
    （二重挿入を防ぐ）。
  - 挿入後、tools/verify-767-tables.py で表が生成出力と一致する
    ことを確認すること。
"""

import re
import sys
from pathlib import Path

SCRIPT_DIR = Path(__file__).resolve().parent
REPO_ROOT = SCRIPT_DIR.parent.parent
DATA_DIR = REPO_ROOT / "detergentmc-protocol" / "build" / "remap-data"
JAVA_FILE = (REPO_ROOT / "detergentmc-protocol" / "src" / "main" / "java"
             / "io" / "detergentmc" / "protocol" / "RegistryRemapper.java")
TABLES767_FILE = (REPO_ROOT / "detergentmc-protocol" / "src" / "main" / "java"
                  / "io" / "detergentmc" / "protocol" / "RegistryTables767.java")



def extract_tables(gen_text):
    """生成出力から {表名: 宣言ブロック全文} を抜き出す。"""
    tables = {}
    pattern = re.compile(
        r"(    private static final (?:int\[\]\[\]|String\[\]\[\]|String\[\]) (\w+) = \{.*?\n    \};"
        r"(?:\n    // [^\n]*)?)", re.DOTALL)
    for m in pattern.finditer(gen_text):
        tables[m.group(2)] = m.group(1)
    return tables


def main():
    if TABLES767_FILE.exists():
        print("RegistryTables767.java が既にある（768への分離済み。何もしない）", file=sys.stderr)
        return 1
    gen_text = (DATA_DIR / "gen767.txt").read_text(encoding="utf-8")
    tables = extract_tables(gen_text)
    print(f"生成出力の表: {len(tables)} 件")
    java = JAVA_FILE.read_text(encoding="utf-8")

    already = [n for n in tables if re.search(
        r"(?:int\[\]\[\]|String\[\]\[\]|String\[\]) " + re.escape(n) + r" =", java)]
    if already:
        print(f"既にある表（挿入しない）: {already}", file=sys.stderr)
        return 1

    # int[][] の表を MENU_766_TO_764 の直後に挿入。
    # 生成出力の順序（*_767 → 763_TO_767 → 767_TO_763 → 764_TO_767 →
    # 767_TO_764 → 765_TO_767 → 767_TO_765 → 属性 → エンチャント）の
    # まま入れる。
    int_tables = [b for n, b in tables.items()
                  if b.startswith("    private static final int[][]")]
    anchor = "    // MENU_766_TO_764: entries=25 ranges=2 overrides=1\n"
    if anchor not in java:
        print("アンカー（MENU_766_TO_764 のコメント）が見つからない", file=sys.stderr)
        return 1
    block = "\n".join(int_tables) + "\n"
    java = java.replace(anchor, anchor + block, 1)

    # 音の名前の表（String[][]）と属性の名前の表（String[]）を
    # SOUND_NAME_FALLBACKS_766_TO_765 の直後に挿入。
    name_tables = [b for n, b in tables.items()
                   if not b.startswith("    private static final int[][]")]
    m = re.search(r"(    private static final String\[\]\[\] SOUND_NAME_FALLBACKS_766_TO_765 = \{.*?\n    \};\n)",
                  java, re.DOTALL)
    if not m:
        print("アンカー（SOUND_NAME_FALLBACKS_766_TO_765）が見つからない", file=sys.stderr)
        return 1
    block = "\n".join(name_tables) + "\n"
    java = java[:m.end(1)] + block + java[m.end(1):]

    JAVA_FILE.write_text(java, encoding="utf-8")
    print(f"挿入した int[][] 表: {len(int_tables)} 件、String 表: {len(name_tables)} 件")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
