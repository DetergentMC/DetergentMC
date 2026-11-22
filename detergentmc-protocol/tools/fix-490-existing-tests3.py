#!/usr/bin/env python3
"""490 追加で変わった既存テストの期待値を直す（第3弾：PacketIds の対応一覧）。

PacketIds.supportedProtocols() の先頭に 490 が増えたため、一覧を
固定していた21件のテスト（PacketIdsTest と各バージョンの
tableKnowsXXX）を新しい実態に合わせる。

使い方: python3 tools/fix-490-existing-tests3.py
"""
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
TEST = ROOT / "src/test/java/io/detergentmc/protocol"

OLD = "List.of(498, 573, 575, 578, 758,"
NEW = "List.of(490, 498, 573, 575, 578, 758,"


def main():
    files = sorted(TEST.glob("PacketIds*Test.java"))
    changed = 0
    for path in files:
        text = path.read_text(encoding="utf-8")
        n = text.count(OLD)
        if n == 0:
            continue
        if n != 1:
            print(f"NG[{path.name}]: {n} 件（期待 0 か 1）")
            return 1
        path.write_text(text.replace(OLD, NEW), encoding="utf-8")
        changed += 1
        print(f"{path.name} を更新した")
    if changed != 21:
        print(f"NG: 更新は {changed} 件（期待 21）")
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
