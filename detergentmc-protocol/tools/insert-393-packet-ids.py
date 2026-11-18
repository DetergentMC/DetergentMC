"""PacketIds.java に 393（1.13）のID列を追加する。

packet-ids-393.py で「393 のIDは全件 401 と一致する」ことを確認済みの
ため、各エントリの先頭引数（id401）の直前に、その値と同じ id393 を
挿入する。エントリ以外の構造（フィールド・コンストラクタ・アクセサ・
idFor・javadoc）は手で直す前提で、このスクリプトはエントリ行だけを
対象にする。

使い方：
  python3 tools/insert-393-packet-ids.py
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
    r"(\w+\(State\.\w+, Direction\.\w+, )" + ARG
    + "(" + (", " + ARG) * 28 + r"\)[,;])")


def main():
    text = JAVA_FILE.read_text(encoding="utf-8")
    count = 0

    def repl(m):
        nonlocal count
        count += 1
        return m.group(1) + m.group(2) + ", " + m.group(2) + m.group(3)

    new_text = ENTRY.sub(repl, text)
    print(f"エントリ {count} 件に id393 列を挿入した。")
    if count != 282:
        print("件数が 282 でないため書き込まない。", file=sys.stderr)
        return 1
    JAVA_FILE.write_text(new_text, encoding="utf-8")
    print(f"書き込み: {JAVA_FILE}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
