#!/usr/bin/env python3
"""ChunkTranslator.java に 393（1.13）の節を 404 の節から生成する。

393 と 404 はワイヤ形式が同一世代（チャンクの旧式 crossing 詰め・
セクション内蔵の光・末尾バイオーム 256×i32・座標 x26|y12|z26・
Spawn Entity の object 分離など。protocol.json 平坦化比較でも
フィールド構成は edit_book を除き一致）で、違うのはレジストリの
番号付けだけである。そのため 404 の節を丸ごと写し、識別子の
404→393 とコメントの 1.13.2→1.13 を付け替える。付け替え先の
RegistryRemapper.map*393*／MAX_*_393／RegistryTables393 は
gen-393-tables.py と patch-393-remapper.py で用意済みの前提。

使い方：
  python3 tools/gen-393-translators.py
  冪等（393 の節があれば何もしない）。
"""

import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
P = ROOT / "src/main/java/io/detergentmc/protocol/ChunkTranslator.java"
MARKER = "// 404（1.13.2）対応"
DONE_MARKER = "// 393（1.13）対応"


def main():
    text = P.read_text(encoding="utf-8")
    if DONE_MARKER in text:
        print("skip（既存）: 393 の節はすでにある")
        return 0
    lines = text.split("\n")
    marker_idx = next(i for i, ln in enumerate(lines) if MARKER in ln)
    start = marker_idx - 1  # 直前の // ---- 行から
    assert lines[start].strip().startswith("// ---"), lines[start]
    # ファイル末尾はクラスを閉じる "}" の行。節はその直前まで。
    assert lines[-1] == "}" or (lines[-1] == "" and lines[-2] == "}"), lines[-3:]
    end = len(lines) - 1 if lines[-1] == "}" else len(lines) - 2
    section = "\n".join(lines[start:end])
    # 付け替え（1.13.2→1.13 を先に。404→393 で RegistryTables404 や
    # map*404*、MAX_*_404、Section404 などがすべて 393 版になる）。
    section = section.replace("1.13.2", "1.13").replace("404", "393")
    assert DONE_MARKER in section
    new_text = "\n".join(lines[:end]) + "\n" + section + "\n}\n" \
        if lines[-1] == "}" else "\n".join(lines[:end]) + "\n" + section + "\n}"
    P.write_text(new_text, encoding="utf-8")
    print(f"ChunkTranslator.java に 393 の節を追加した"
          f"（{section.count(chr(10)) + 1} 行）")
    return 0


if __name__ == "__main__":
    sys.exit(main())
