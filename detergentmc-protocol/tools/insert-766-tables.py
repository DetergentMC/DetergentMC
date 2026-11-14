#!/usr/bin/env python3
"""tools/generate-remap-tables.py --target 766 の出力を
RegistryRemapper.java に挿入する（1回だけの作業用）。

  - *_766 表（765→766）と合成表（763↔766、764↔766）を
    ITEM_REVERSE_764 表の直後に挿入する。
  - SOUND_NAME_FALLBACKS_766／_766_TO_763／_766_TO_764 を
    SOUND_NAME_FALLBACKS 表の直後に挿入する。
  - 挿入する表が既にある場合は何もせず終了コード 1 で失敗する
    （二重挿入を防ぐ）。
  - 挿入後、tools/verify-766-tables.py で表が生成出力と一致する
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


def extract_tables(gen_text):
    """生成出力から {表名: 宣言ブロック全文} を抜き出す。"""
    tables = {}
    pattern = re.compile(
        r"(    private static final (?:int\[\]\[\]|String\[\]\[\]) (\w+) = \{.*?\n    \};"
        r"(?:\n    // [^\n]*)?)", re.DOTALL)
    for m in pattern.finditer(gen_text):
        tables[m.group(2)] = m.group(1)
    return tables


def main():
    gen_text = (DATA_DIR / "gen766.txt").read_text(encoding="utf-8")
    tables = extract_tables(gen_text)
    print(f"生成出力の表: {len(tables)} 件")
    java = JAVA_FILE.read_text(encoding="utf-8")

    already = [n for n in tables if re.search(
        r"(?:int\[\]\[\]|String\[\]\[\]) " + re.escape(n) + r" =", java)]
    if already:
        print(f"既にある表（挿入しない）: {already}", file=sys.stderr)
        return 1

    # *_766 表と合成表（int[][]）を ITEM_REVERSE_764 の直後に挿入。
    # 生成出力の順序（*_766 → 763_TO_766 → 766_TO_763 → 764_TO_766 →
    # 766_TO_764）のまま入れる。
    int_tables = [b for n, b in tables.items() if "SOUND_NAME" not in n]
    anchor = "    // ITEM_REVERSE_764: 764 entries=1255 ranges=8 overrides=0\n"
    if anchor not in java:
        print("アンカー（ITEM_REVERSE_764 のコメント）が見つからない", file=sys.stderr)
        return 1
    block = "\n".join(int_tables) + "\n"
    java = java.replace(anchor, anchor + block, 1)

    # 音の名前の表（String[][]）を SOUND_NAME_FALLBACKS の直後に挿入。
    name_tables = [b for n, b in tables.items() if "SOUND_NAME" in n]
    m = re.search(r"(    private static final String\[\]\[\] SOUND_NAME_FALLBACKS = \{.*?\n    \};\n)",
                  java, re.DOTALL)
    if not m:
        print("アンカー（SOUND_NAME_FALLBACKS）が見つからない", file=sys.stderr)
        return 1
    block = "\n".join(name_tables) + "\n"
    java = java[:m.end(1)] + block + java[m.end(1):]

    JAVA_FILE.write_text(java, encoding="utf-8")
    print(f"挿入した int[][] 表: {len(int_tables)} 件、String[][] 表: {len(name_tables)} 件")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
