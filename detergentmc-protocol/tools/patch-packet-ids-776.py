#!/usr/bin/env python3
"""patch-packet-ids-775.py の 776 版（17引数 → 18引数）。

packet-ids-776.py が作った packet-ids-776.json（267件の写像）
を使い、コンストラクタ呼び出しの末尾に `, 0xXX`（無い場合は `, -1`）
を足す。新規パケットのエントリ追加はしない（別途手で追加する）。
書き換え前後でエントリ数が変わらないこと、既存の17つのIDが
変わらないことを確認してから書き出す。

罠 #5（グループ番号のずれ）：17 引数の場合、ID は第4〜第20グループ、
終端子（`,`/`;`）は第21グループ。以前 m.group(19) を終端子と誤認する
バグがあり、各行末に `)0xXX` が残る破損を出した（775 で修復済み）。
ここでは m.group(21) が終端子であることを明記する。
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

ID_PAT = r"(-1|0x[0-9a-fA-F]+)"


def fmt(id776):
    return "-1" if id776 < 0 else f"0x{id776:02x}"


def main():
    mapping = json.loads((DATA_DIR / "packet-ids-776.json").read_text(encoding="utf-8"))
    text = JAVA_FILE.read_text(encoding="utf-8")
    pattern = re.compile(
        r"(\w+)\(State\.(\w+), Direction\.(\w+), "
        + ", ".join([ID_PAT] * 17) + r"\)([,;])")

    def replace(m):
        name = m.group(1)
        if name not in mapping:
            raise SystemExit(f"写像に無いエントリ: {name}")
        ids = list(m.groups()[3:20])
        return (f"{name}(State.{m.group(2)}, Direction.{m.group(3)}, "
                + ", ".join(ids)
                + f", {fmt(mapping[name]['id776'])}){m.group(21)}")
        # 注意：終端子（`,`/`;`）は第21グループ。ID は第4〜第20グループ
        # （17 個）。m.group(20)（17番目の引数＝775のID）を終端子と
        # 誤認すると各行末に `)0xXX` が残る破損になる（罠 #5）。

    new_text, count = pattern.subn(replace, text)
    print(f"書き換えたエントリ: {count} 件（写像: {len(mapping)} 件）")
    if count != len(mapping):
        print("件数が合わないため書き出さない", file=sys.stderr)
        return 1
    # 既存の17つのIDが変わっていないことの確認（18引数版が無いこと）。
    left = re.findall(
        r"\(State\.\w+, Direction\.\w+, "
        + ", ".join([r"(?:-1|0x[0-9a-fA-F]+)"] * 17) + r"\)[,;]", new_text)
    if left:
        print(f"18引数にできていないエントリが残っている: {len(left)}", file=sys.stderr)
        return 1
    JAVA_FILE.write_text(new_text, encoding="utf-8")
    print("PacketIds.java に 776 のIDを追加した。")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
