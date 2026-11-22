"""PacketIds.java に 316（1.11.2）のID列を追加する。

packet-ids-316.py で全283エントリの内部名が一意に確定し、316のIDを
引けたこと（かつ Stage A 実測の不変条件が全件成立したこと）を
確認済みのため、その JSON（既定は ~/workspace/mcdata404/
packet-ids-316.json）の id316 を各エントリの先頭引数（id335）の
直前に挿入する。エントリ以外の構造（フィールド・コンストラクタ・
アクセサ・idFor・javadoc）は手で行う前提で、このスクリプトは
既存エントリ行だけを対象にする。316 にだけあるパケットは無い
（Stage A 実測）ため、新規エントリの追加は無い。

使い方：
  python3 tools/insert-316-packet-ids.py [--ids FILE]
  冪等ではない（2回実行すると列が二重になる）。git diff で確認すること。
"""

import argparse
import json
import re
import sys
from pathlib import Path

SCRIPT_DIR = Path(__file__).resolve().parent
JAVA_FILE = (SCRIPT_DIR.parent / "src" / "main" / "java"
             / "io" / "detergentmc" / "protocol" / "PacketIds.java")
IDS_DEFAULT = (Path.home() / "workspace" / "mcdata404"
               / "packet-ids-316.json")

ARG = r"(-1|0x[0-9a-fA-F]+)"
ENTRY = re.compile(
    r"(\w+)\(State\.(\w+), Direction\.(\w+), "
    + ", ".join([ARG] * 33) + r"\)[,;]")


def fmt(v):
    return "-1" if v < 0 else f"0x{v:02x}"


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--ids", type=Path, default=IDS_DEFAULT)
    args = parser.parse_args()
    ids = json.loads(args.ids.read_text(encoding="utf-8"))

    text = JAVA_FILE.read_text(encoding="utf-8")
    count = 0
    missing = []

    def repl(m):
        nonlocal count
        name = m.group(1)
        if name not in ids:
            missing.append(name)
            return m.group(0)
        count += 1
        whole = m.group(0)
        # 先頭引数（id335）の直前に id316 を挿入する。
        head = f"{name}(State.{m.group(2)}, Direction.{m.group(3)}, "
        return head + fmt(ids[name]["id316"]) + ", " + whole[len(head):]

    new_text = ENTRY.sub(repl, text)
    print(f"エントリ {count} 件に id316 列を挿入した。")
    if missing:
        print(f"JSONに無いエントリ: {missing}", file=sys.stderr)
    if count != 283:
        print("件数が 283 でないため書き込まない。", file=sys.stderr)
        return 1
    JAVA_FILE.write_text(new_text, encoding="utf-8")
    print(f"書き込み: {JAVA_FILE}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
