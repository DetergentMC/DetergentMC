#!/usr/bin/env python3
"""patch-packet-ids-774.py の 775 版（16引数 → 17引数）。

packet-ids-775.py が作った packet-ids-775.json（257件の写像）
を使い、コンストラクタ呼び出しの末尾に `, 0xXX`（無い場合は `, -1`）
を足す。新規パケットのエントリ追加はしない（別途手で追加する）。
書き換え前後でエントリ数が変わらないこと、既存の16つのIDが
変わらないことを確認してから書き出す。
"""
import ast
import json
import re
import sys
from pathlib import Path

SCRIPT_DIR = Path(__file__).resolve().parent
REPO_ROOT = SCRIPT_DIR.parent.parent
DATA_DIR = REPO_ROOT / "detergentmc-protocol" / "build" / "remap-data"
JAVA_FILE = (REPO_ROOT / "detergentmc-protocol" / "src" / "main" / "java"
             / "io" / "detergentmc" / "protocol" / "PacketIds.java")

ID_PAT = r"(-1|0x[0-9a-fA-F]+)"


def fmt(id775):
    return "-1" if id775 < 0 else f"0x{id775:02x}"


def main():
    mapping = json.loads((DATA_DIR / "packet-ids-775.json").read_text(encoding="utf-8"))
    text = JAVA_FILE.read_text(encoding="utf-8")
    pattern = re.compile(
        r"(\w+)\(State\.(\w+), Direction\.(\w+), "
        + ", ".join([ID_PAT] * 16) + r"\)([,;])")

    def replace(m):
        name = m.group(1)
        if name not in mapping:
            raise SystemExit(f"写像に無いエントリ: {name}")
        ids = list(m.groups()[3:19])
        return (f"{name}(State.{m.group(2)}, Direction.{m.group(3)}, "
                + ", ".join(ids)
                + f", {fmt(mapping[name]['id775'])}){m.group(20)}")
        # 注意：終端子（`,`/`;`）は第20グループ。以前 m.group(19)
        # （16番目の引数＝774のID）を終端子と誤認するバグがあり、
        # 各行末に `)0xXX` が残る破損を出した（/tmp/mc775/fix-packet-ids-775-tail.py で修復済み）。

    new_text, count = pattern.subn(replace, text)
    print(f"書き換えたエントリ: {count} 件（写像: {len(mapping)} 件）")
    if count != len(mapping):
        print("件数が合わないため書き出さない", file=sys.stderr)
        return 1
    # 既存の16つのIDが変わっていないことの確認（17引数版が無いこと）。
    left = re.findall(
        r"\(State\.\w+, Direction\.\w+, "
        + ", ".join([r"(?:-1|0x[0-9a-fA-F]+)"] * 16) + r"\)[,;]", new_text)
    if left:
        print(f"17引数にできていないエントリが残っている: {len(left)}", file=sys.stderr)
        return 1
    JAVA_FILE.write_text(new_text, encoding="utf-8")
    print("PacketIds.java に 775 のIDを追加した。")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
