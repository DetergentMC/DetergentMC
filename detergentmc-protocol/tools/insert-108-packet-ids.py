"""PacketIds.java に 108（1.9.1）のID列を追加する。

108 と 109 のパケット ID 表は完全一致する（Stage A 実測：
packet-ids-108.py）。根拠は次のとおり：
  1. 実バニラ 1.9.1／1.9.2 の両 jar のエントリ一覧が完全一致し、
     非クラスファイルは CRC まで全て一致。差分のあるクラスは
     11 件だけで、その文字列定数の差分は版数文字列と
     パケットバッファのエラーメッセージ文言1件だけ。
  2. 実バニラ 1.9.1 へのワイヤ採取で Join Game（20 バイト、
     dimension は i32 で過不足なく消費）、Chunk Data（末尾に
     blockEntities 無し）、Keep Alive（varint 読み切り）が
     1.9.2 の採取と同形だった。
  3. minecraft-data に 1.9.1 の項は無く（dataPaths に無い）、
     レジストリ・プロトコルとも 1.9 系は pc/1.9 系に解決する。

そのため各エントリの先頭引数（id109）の直前に、その値と同値の
id108 を挿入するだけにする。新規エントリは無い。

フィールド・コンストラクタ・アクセサ・idFor・supportedProtocols・
isSupported・クラス javadoc は手で直す前提で、このスクリプトは
既存エントリ行だけを対象にする。

使い方：
  python3 tools/insert-108-packet-ids.py
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
    + ", ".join([ARG] * 38) + r"\)[,;]")


def main():
    text = JAVA_FILE.read_text(encoding="utf-8")
    count = 0

    def repl(m):
        nonlocal count
        count += 1
        name, state, direction = m.group(1), m.group(2), m.group(3)
        id109 = m.group(4)
        whole = m.group(0)
        head = f"{name}(State.{state}, Direction.{direction}, "
        return head + id109 + ", " + whole[len(head):]

    new_text = ENTRY.sub(repl, text)
    print(f"エントリ {count} 件に id108 列（id109 と同値）を挿入した。")
    if count != 284:
        print("件数が 284 でないため書き込まない。", file=sys.stderr)
        return 1
    JAVA_FILE.write_text(new_text, encoding="utf-8")
    print(f"書き込み: {JAVA_FILE}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
