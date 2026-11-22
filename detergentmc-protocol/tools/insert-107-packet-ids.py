"""PacketIds.java に 107（1.9）のID列を追加する。

107 と 108 のパケット ID 表は完全一致する（Stage A 実測：
packet-ids-107.py）。根拠は次のとおり：
  1. minecraft-data の pc/1.9（107 自身の protocol.json）と
     pc/1.9.2（109）の全面比較で、差分は Join Game
     （packet_login）の dimension の型（i8 ⇔ i32）ただ1件。
     パケット集合・ID・他の全フィールド型は完全一致する。
     108 は 109 と差分ゼロ（108 Stage A で証明済み）なので、
     107 の ID 表は 108 とも完全一致する。
  2. 実バニラ 1.9 へのワイヤ採取（Stage A で再採取）：
     ステータス応答 protocol=107、Join Game は 17 バイトで
     dimension を i8 として過不足なく消費、Chunk Data は
     dataLen 内に末尾ゼロ埋めを含み blockEntities は無い、
     Keep Alive は varint で読み切り＋応答 0x0b＋応答後の
     チャットがバニラログに到達。

そのため各エントリの先頭引数（id108）の直前に、その値と同値の
id107 を挿入するだけにする。新規エントリは無い。

フィールド・コンストラクタ・アクセサ・idFor・supportedProtocols・
isSupported・クラス javadoc は手で直す前提で、このスクリプトは
既存エントリ行だけを対象にする。

使い方：
  python3 tools/insert-107-packet-ids.py
  冪等ではない（2回実行すると列が二重になる）。git diff で確認すること。
"""

import re
import sys
from pathlib import Path

SCRIPT_DIR = Path(__file__).resolve().parent
JAVA_FILE = (SCRIPT_DIR.parent / "src" / "main" / "java"
             / "io" / "detergentmc" / "protocol" / "PacketIds.java")

ARG = r"(-1|0x[0-9a-fA-F]+)"
ENTRY = re.compile(
    r"(\w+)\(State\.(\w+), Direction\.(\w+), "
    + ", ".join([ARG] * 39) + r"\)[,;]")


def main():
    text = JAVA_FILE.read_text(encoding="utf-8")
    count = 0

    def repl(m):
        nonlocal count
        count += 1
        name, state, direction = m.group(1), m.group(2), m.group(3)
        id108 = m.group(4)
        whole = m.group(0)
        head = f"{name}(State.{state}, Direction.{direction}, "
        return head + id108 + ", " + whole[len(head):]

    new_text = ENTRY.sub(repl, text)
    print(f"エントリ {count} 件に id107 列（id108 と同値）を挿入した。")
    if count != 284:
        print("件数が 284 でないため書き込まない。", file=sys.stderr)
        return 1
    JAVA_FILE.write_text(new_text, encoding="utf-8")
    print(f"書き込み: {JAVA_FILE}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
