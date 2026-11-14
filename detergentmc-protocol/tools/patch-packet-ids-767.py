#!/usr/bin/env python3
"""PacketIds.java の各エントリに 767 のID（第5引数）を追加する。

tools/packet-ids-767.py が作った packet-ids-767.json（216件の写像）
を使い、コンストラクタ呼び出しの末尾に `, 0xXX`（無い場合は `, -1`）
を足す。新規パケットのエントリ追加はしない（別途手で追加する）。
書き換え前後でエントリ数が変わらないこと、既存の4つのIDが
変わらないことを確認してから書き出す。
"""

import json
import re
import sys
from pathlib import Path

SCRIPT_DIR = Path(__file__).resolve().parent
REPO_ROOT = SCRIPT_DIR.parent.parent
DATA_DIR = REPO_ROOT / "detergentmc-protocol" / "build" / "remap-data"
JAVA_FILE = (REPO_ROOT / "detergentmc-protocol" / "src" / "main" / "java"
             / "io" / "detergentmc" / "protocol" / "PacketIds.java")


def fmt(id767):
    return "-1" if id767 < 0 else f"0x{id767:02x}"


def main():
    mapping = json.loads((DATA_DIR / "packet-ids-767.json").read_text(encoding="utf-8"))
    text = JAVA_FILE.read_text(encoding="utf-8")
    pattern = re.compile(
        r"(\w+)\(State\.(\w+), Direction\.(\w+), "
        r"(-1|0x[0-9a-fA-F]+), (-1|0x[0-9a-fA-F]+), (-1|0x[0-9a-fA-F]+), "
        r"(-1|0x[0-9a-fA-F]+)\)([,;])")

    def replace(m):
        name = m.group(1)
        if name not in mapping:
            raise SystemExit(f"写像に無いエントリ: {name}")
        return (f"{name}(State.{m.group(2)}, Direction.{m.group(3)}, "
                f"{m.group(4)}, {m.group(5)}, {m.group(6)}, {m.group(7)}, "
                f"{fmt(mapping[name]['id767'])}){m.group(8)}")

    new_text, count = pattern.subn(replace, text)
    print(f"書き換えたエントリ: {count} 件（写像: {len(mapping)} 件）")
    if count != len(mapping):
        print("件数が合わないため書き出さない", file=sys.stderr)
        return 1
    # 既存の4つのIDが変わっていないことの確認（5引数版が無いこと）。
    left = re.findall(
        r"\(State\.\w+, Direction\.\w+, (?:-1|0x[0-9a-fA-F]+), "
        r"(?:-1|0x[0-9a-fA-F]+), (?:-1|0x[0-9a-fA-F]+), "
        r"(?:-1|0x[0-9a-fA-F]+)\)[,;]", new_text)
    if left:
        print(f"5引数のままのエントリが残っている: {len(left)}", file=sys.stderr)
        return 1
    JAVA_FILE.write_text(new_text, encoding="utf-8")
    print("PacketIds.java に 767 のIDを追加した。")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
