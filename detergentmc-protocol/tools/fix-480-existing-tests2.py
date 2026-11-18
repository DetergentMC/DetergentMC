#!/usr/bin/env python3
"""480 追加で変わった既存テストの期待値を直す（対応リスト・OLDEST 系）。

480 が最古の対応プロトコルになったため、対応プロトコル一覧や
OLDEST を固定していた既存テストを新しい実態（先頭 480、次が 485、
全26件）に合わせる。

使い方: python3 tools/fix-480-existing-tests2.py
"""
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
TEST = ROOT / "src/test/java/io/detergentmc/protocol"
BTEST = ROOT.parent / "detergentmc-bedrock/src/test/java/io/detergentmc/bedrock"
CTEST = ROOT.parent / "detergentmc-core/src/test/java/io/detergentmc/core"

OLD_LIST = ("List.of(485, 490, 498, 573, 575, 578, 758, 759, 760, 761, 762, 763, "
            "764, 765, 766, 767, 768, 769, 770, 771, 772, 773, 774, 775, 776)")
NEW_LIST = ("List.of(480, 485, 490, 498, 573, 575, 578, 758, 759, 760, 761, 762, "
            "763, 764, 765, 766, 767, 768, 769, 770, 771, 772, 773, 774, 775, 776)")


def fix_lists():
    changed = 0
    for base in (TEST, BTEST, CTEST):
        for path in sorted(base.rglob("*.java")):
            text = path.read_text(encoding="utf-8")
            if OLD_LIST in text:
                path.write_text(text.replace(OLD_LIST, NEW_LIST), encoding="utf-8")
                changed += 1
                print(f"一覧を更新: {path.name}")
    print(f"一覧の更新は {changed} ファイル")


def sub(path, old, new, count, where):
    text = path.read_text(encoding="utf-8")
    found = text.count(old)
    if found != count:
        print(f"NG[{where}]: {found} 件（期待 {count}）: {old[:70]!r}")
        sys.exit(1)
    path.write_text(text.replace(old, new), encoding="utf-8")
    print(f"{where} を更新した")


GET_LINE = re.compile(r"^(\s*)assertEquals\(\d+, supported\.get\((\d+)\), supported\.toString\(\)\);$")


def fix_oldest_block(name, comment_old=None, comment_new=None, rename=False):
    path = TEST / name
    lines = path.read_text(encoding="utf-8").split("\n")
    out = []
    i = 0
    shifted = False
    while i < len(lines):
        line = lines[i]
        if "assertEquals(485, ClientAdapter.OLDEST_SUPPORTED_CLIENT_PROTOCOL);" in line:
            out.append(line.replace("assertEquals(485,", "assertEquals(480,"))
            i += 1
            continue
        if ("assertEquals(ProtocolVersion.V1_14_2,"
                " ClientAdapter.OLDEST_SUPPORTED_CLIENT_PROTOCOL);") in line:
            out.append(line.replace("ProtocolVersion.V1_14_2", "ProtocolVersion.V1_14_1"))
            i += 1
            continue
        m = GET_LINE.match(line)
        if m and not shifted:
            # 連続する get 断言の塊をまとめて1つずつずらす。
            run = []
            j = i
            while j < len(lines) and GET_LINE.match(lines[j]):
                run.append(lines[j])
                j += 1
            out.append(f"{m.group(1)}assertEquals(480, supported.get(0), supported.toString());")
            for entry in run:
                idx = int(GET_LINE.match(entry).group(2))
                out.append(entry.replace(f"supported.get({idx})", f"supported.get({idx + 1})"))
            i = j
            shifted = True
            continue
        if "assertEquals(25, supported.size(), supported.toString());" in line:
            out.append(line.replace("assertEquals(25,", "assertEquals(26,"))
            i += 1
            continue
        out.append(line)
        i += 1
    if not shifted:
        print(f"NG[{name}]: get 断言の塊が見つからない")
        sys.exit(1)
    text = "\n".join(out)
    if rename:
        if text.count("void oldestSupportedIs485() {") != 1:
            print(f"NG[{name}]: oldestSupportedIs485 が一意でない")
            sys.exit(1)
        text = text.replace("void oldestSupportedIs485() {", "void oldestSupportedIs480() {")
    if comment_old is not None:
        if text.count(comment_old) != 1:
            print(f"NG[{name}]: コメントが一意でない")
            sys.exit(1)
        text = text.replace(comment_old, comment_new)
    path.write_text(text, encoding="utf-8")
    print(f"{name} の OLDEST 断言を更新した")


def main():
    fix_lists()
    # 全サーバー列挙に 480 を足す（485／490 のテストだけが全列挙している）。
    for name in ("ClientAdapter485Test.java", "ClientAdapter490Test.java"):
        path = TEST / name
        if "for (int server : new int[]{480, 485, 490, 498," in path.read_text(encoding="utf-8"):
            print(f"skip（適用済み）: {name}")
            continue
        sub(path,
            "for (int server : new int[]{485, 490, 498,",
            "for (int server : new int[]{480, 485, 490, 498,", 1, name)
    # OLDEST ブロック。
    fix_oldest_block("ClientAdapter485Test.java", rename=True)
    fix_oldest_block("ClientAdapter490Test.java",
                     "// 485（1.14.2）対応で最古は 485 になった。490 は2番目。",
                     "// 480（1.14.1）対応で最古は 480 になった。485 は2番目、490 は3番目。",
                     rename=True)
    fix_oldest_block("ClientAdapter498Test.java",
                     "// 485（1.14.2）対応で最古は 485 になった。490 は2番目、498 は3番目。",
                     "// 480（1.14.1）対応で最古は 480 になった。485 は2番目、490 は3番目、498 は4番目。",
                     rename=True)
    fix_oldest_block("ClientAdapter573Test.java")
    fix_oldest_block("ClientAdapter575Test.java")
    fix_oldest_block("ClientAdapter578Test.java",
                     "// 485（1.14.2）対応で最古は 485 になった。並びは 485・490・498・573・575・578…。",
                     "// 480（1.14.1）対応で最古は 480 になった。並びは 480・485・490・498・573・575・578…。")
    fix_oldest_block("ClientAdapter758Test.java",
                     "// 485（1.14.2）対応で最古は 485 になった。並びは 485・490・498・573・575・578・758…。",
                     "// 480（1.14.1）対応で最古は 480 になった。並びは 480・485・490・498・573・575・578・758…。")


if __name__ == "__main__":
    main()
