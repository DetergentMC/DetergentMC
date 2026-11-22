#!/usr/bin/env python3
"""480 追加で変わった既存テストの期待値を直す。

- ResourcePackMode の最大オーバーロードに url-480／sha1-480 の
  ペアが増えたため、485／490／498／573／575／578 の各テストの
  decideFull に「480（未設定）」のペアを足す。

使い方: python3 tools/fix-480-existing-tests.py
"""
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
TEST = ROOT / "src/test/java/io/detergentmc/protocol"


def sub(path, old, new, count):
    text = path.read_text(encoding="utf-8")
    found = text.count(old)
    if found != count:
        print(f"NG[{path.name}]: {found} 件（期待 {count}）: {old[:60]!r}")
        sys.exit(1)
    path.write_text(text.replace(old, new), encoding="utf-8")
    print(f"{path.name} を更新した")


def main():
    for name in ("ResourcePackMode490Test", "ResourcePackMode498Test",
                 "ResourcePackMode573Test", "ResourcePackMode575Test",
                 "ResourcePackMode578Test"):
        sub(TEST / f"{name}.java",
            '                // 485（未設定）\n'
            '                "", "",\n'
            '                false, "");',
            '                // 485（未設定）\n'
            '                "", "",\n'
            '                // 480（未設定）\n'
            '                "", "",\n'
            '                false, "");', 1)
    sub(TEST / "ResourcePackMode485Test.java",
        '                url485, sha1485,\n'
        '                false, "");',
        '                url485, sha1485,\n'
        '                // 480（未設定）\n'
        '                "", "",\n'
        '                false, "");', 1)
    return 0


if __name__ == "__main__":
    sys.exit(main())
