"""4（1.7.2〜1.7.5）のパケット ID 表を作る（4 Stage A）。

番号対応（Stage A 実測）：**4 は 1.7.2 / 1.7.3 / 1.7.4 / 1.7.5 の
全4リリース共通プロトコル**。先行調査（PREP-5）で全9版（1.7.2〜
1.7.10）の実バニラを起動し Status 応答が 4 ないし 5 を申告する
ことを確認済みで、Stage A でも代表 1.7.2 を再起動して再確認した
（Status は `{"name":"1.7.2","protocol":4}`、Login Success の
UUID はダッシュ無し32文字で 43 バイト過不足なく消費、Join Game
は 16 バイトで過不足なく消費、Keep Alive は素の i32（4バイト）、
Chunk Bulk はバイト数完全一致、level.dat に DataVersion 無し）。
jar の SHA-1 は piston-meta のライブ manifest／version json と
照合済み（1.7.2 サーバー jar =
3716cac82982e7c2eb09f83028b555e9ea606002、9,163,955 バイト、
javaVersion は majorVersion 8、pack.mcmeta／version.json／data/
は jar 内に存在しない、DataVersion の概念自体が無い）。

4⇔5 の ID は同一である（根拠）：
- minecraft-data pc/1.7 は 1.7.10（プロトコル 5）専用データで、
  4 用の個別データは存在しない（dataPaths に 1.7.2〜1.7.9 の
  個別項が無い）。レジストリは 1.7.2⇔1.7.10 のクライアント jar の
  lang キー集合が完全一致（527キー、差分0）で裏付け済み。
- Stage A の実ワイヤ採取（1.7.2）で観測したクライアント行き
  パケット ID 集合は 5 の列の ID 集合の部分集合だった。
- 二人採取で named_entity_spawn の形式差分（プロパティ配列が
  4 には無い）を座標の妥当性で弁別済み。ID 自体（0x0c）は一致。

よって 4 列の値は 5 列の複写とし、本スクリプトは PacketIds.java の
現在の 5 列を読み出して JSON 化する。patch-4-packet-ids.py が
この JSON を使って 4 列を差し込む。

使い方：
  python3 tools/packet-ids-4.py [--out FILE]
  終了コード 0 = 全287エントリの 5 列を読み出せた。
"""

import argparse
import json
import re
import sys
from pathlib import Path

SCRIPT_DIR = Path(__file__).resolve().parent
JAVA_FILE = (SCRIPT_DIR.parent / "src" / "main" / "java"
             / "io" / "detergentmc" / "protocol" / "PacketIds.java")

ARG = r"(-1|0x[0-9a-fA-F]+)"
ENTRY = re.compile(
    r"(\w+)\(State\.(\w+), Direction\.(\w+), "
    + ", ".join([ARG] * 42) + r"\)([,;])")


def parse_value(s):
    return -1 if s == "-1" else int(s, 16)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--out", default=None)
    args = ap.parse_args()

    src = JAVA_FILE.read_text(encoding="utf-8")
    assert "int id4;" not in src, "既に 4 列がある"
    out = {}
    for m in ENTRY.finditer(src):
        name = m.group(1)
        id5 = parse_value(m.group(4))
        out[name] = {"id4": id5}
    assert len(out) == 287, f"エントリ数が想定と違う: {len(out)}"
    text = json.dumps(out, indent=1, sort_keys=True)
    if args.out:
        Path(args.out).write_text(text, encoding="utf-8")
        print(f"{len(out)} エントリを {args.out} へ書いた")
    else:
        print(text)
    return 0


if __name__ == "__main__":
    sys.exit(main())
