"""316 のテスト6本を 315 用に複製する（機械的な番号差し替え）。

315 は 316 とワイヤ形式・パケットIDが完全同一で、レジストリ差分は
アイテム1件だけのため、変換器・セッション・参加フローのテストは
316 のテストを写して番号を差し替えたものがそのまま正しい。
ClientAdapter315Test と ProtocolVersion315Test は断言の意味が
変わるため手書き、PacketIds315Test と RegistryRemapper315Test は
複製後に 315 固有の断言（id315==id316 の全件一致、iron_nugget の
不在）を足す。

使い方：
  python3 tools/gen-315-tests.py
  冪等（315 のファイルが既にあれば何もしない）。
"""

import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
TEST = ROOT / "src/test/java/io/detergentmc/protocol"

FILES = [
    "ChunkTranslator316Test.java",
    "SlotTranslator316Test.java",
    "PlayTranslator316Test.java",
    "ProxySession316Test.java",
    "ClientJoinFlow316Test.java",
    "PacketIds316Test.java",
    "RegistryRemapper316Test.java",
]


def rename(text):
    text = text.replace("V1_11_2", "V1_11")
    text = text.replace("1.11.2", "1.11")
    text = text.replace("316", "315")
    return text


def main():
    for name in FILES:
        src_path = TEST / name
        dst_path = TEST / name.replace("316", "315")
        if dst_path.exists():
            print(f"skip（既存）: {dst_path.name}")
            continue
        dst_path.write_text(rename(src_path.read_text(encoding="utf-8")),
                            encoding="utf-8")
        print(f"{dst_path.name} を作成した")
    return 0


if __name__ == "__main__":
    sys.exit(main())
