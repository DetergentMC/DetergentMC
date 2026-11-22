#!/usr/bin/env python3
"""477 追加で変わった既存テストの期待値を直す。

- ResourcePackMode の最大オーバーロードに url-477／sha1-477 の
  ペアが増えたため、480／485／490／498／573／575／578 の各テストの
  decideFull に「477（未設定）」のペアを足す。

使い方: python3 tools/fix-477-existing-tests.py
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
    for name in ("ResourcePackMode485Test", "ResourcePackMode490Test",
                 "ResourcePackMode498Test", "ResourcePackMode573Test",
                 "ResourcePackMode575Test", "ResourcePackMode578Test"):
        sub(TEST / f"{name}.java",
            '                // 480（未設定）\n'
            '                "", "",\n'
            '                false, "");',
            '                // 480（未設定）\n'
            '                "", "",\n'
            '                // 477（未設定）\n'
            '                "", "",\n'
            '                false, "");', 1)
    sub(TEST / "ResourcePackMode480Test.java",
        '                url480, sha1480,\n'
        '                false, "");',
        '                url480, sha1480,\n'
        '                // 477（未設定）\n'
        '                "", "",\n'
        '                false, "");', 1)
    return 0


if __name__ == "__main__":
    sys.exit(main())
