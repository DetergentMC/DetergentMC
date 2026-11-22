"""PacketIds.java に 315（1.11）のID列を追加する。

315 の ID は全件 316 と同一（packet-ids-315.py で、315 自身の
protocol.json と 316 の protocolComments.json という独立した2源の
完全一致＋実バニラ 1.11 のワイヤ採取で照合済み）。そのため各エントリ
の先頭引数（id316）の直前に、同値の id315 を挿入する。

フィールド・コンストラクタ・アクセサ・idFor・supportedProtocols・
isSupported・クラス javadoc は手で直す前提で、このスクリプトは
既存エントリ行だけを対象にする。315 にだけあるパケットは無いため、
新規エントリの追加は無い。

使い方：
  python3 tools/insert-315-packet-ids.py
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
    + ", ".join([ARG] * 34) + r"\)[,;]")


def main():
    text = JAVA_FILE.read_text(encoding="utf-8")
    count = 0

    def repl(m):
        nonlocal count
        count += 1
        whole = m.group(0)
        head = f"{m.group(1)}(State.{m.group(2)}, Direction.{m.group(3)}, "
        first_arg = m.group(4)
        return head + first_arg + ", " + whole[len(head):]

    new_text = ENTRY.sub(repl, text)
    print(f"エントリ {count} 件に id315 列（= id316）を挿入した。")
    if count != 283:
        print("件数が 283 でないため書き込まない。", file=sys.stderr)
        return 1
    JAVA_FILE.write_text(new_text, encoding="utf-8")
    print(f"書き込み: {JAVA_FILE}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
