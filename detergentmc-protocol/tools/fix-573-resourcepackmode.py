#!/usr/bin/env python3
"""patch-573-resourcepackmode.py の後始末。

patch-573 は Decision レコードに pack573 を足し、全委譲コンストラクタへ
一律に null を足したが、正規コンストラクタへ委譲する4件以外は
一段短いコンストラクタへの委譲なので、足した null を取り除く
（575 のときは生成側の手当てで吸収されていた差分）。
あわせて、本体 decide() を直接呼ぶオーバーロード2件に
url-573／sha1-573 分の null, null を足す。

使い方: python3 tools/fix-573-resourcepackmode.py
"""
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
P = ROOT / "src/main/java/io/detergentmc/protocol/ResourcePackMode.java"


def main():
    text = P.read_text(encoding="utf-8")

    # 1. Decision の委譲：正規（24引数）以外は末尾の ", null" を1つ戻す。
    deleg = re.compile(r"this\(kind, pack, problem,(.*?)\);", re.S)
    fixed = 0

    def revert(m):
        nonlocal fixed
        group = m.group(1)
        total = 3 + group.count(",") + 1
        if total == 24:
            return m.group(0)
        if not group.rstrip().endswith("null"):
            print(f"NG: 末尾が null でない委譲: ...{group[-40:]!r}")
            sys.exit(1)
        fixed += 1
        trimmed = group.rstrip()
        trimmed = trimmed[: -len("null")].rstrip()
        if trimmed.endswith(","):
            trimmed = trimmed[:-1]
        return "this(kind, pack, problem," + trimmed + ");"

    text = deleg.sub(revert, text)
    print(f"委譲コンストラクタ {fixed} 件の null を戻した")

    # 2. 本体 decide（旧44引数）を直接呼ぶオーバーロードに null, null を足す。
    calls = re.compile(r"return decide\((.*?)\);", re.S)
    added = 0

    def add(m):
        nonlocal added
        group = m.group(1)
        total = group.count(",") + 1
        if total != 44:
            return m.group(0)
        if not group.rstrip().endswith("required, promptKey"):
            print("NG: 44引数だが末尾が required, promptKey でない")
            sys.exit(1)
        added += 1
        return m.group(0).replace("required, promptKey);",
                                  "null, null, required, promptKey);")

    text = calls.sub(add, text)
    print(f"decide オーバーロード {added} 件に null, null を追加した")
    if added != 2:
        print(f"NG: 追加は {added} 件（期待 2）")
        return 1

    P.write_text(text, encoding="utf-8")
    print("ResourcePackMode.java を修正した")
    return 0


if __name__ == "__main__":
    sys.exit(main())
