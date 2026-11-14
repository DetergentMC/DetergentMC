#!/usr/bin/env python3
"""ResourcePackMode759Test / 760Test の decide() 呼び出しに
url-775 / sha1-775（空文字 2 個）を挿入する。

775 追加で decide() のオーバーロードが 2 引数ずつ伸びたため、
旧テストの呼び出しは 760・759 の値が 775・760 の枠にずれて
束縛される（コンパイルは通るが意味が変わる罠）。
各呼び出しで、763 ペアの直後から数えて 20 個目（＝774 ペアの末尾）
の "" の直後に "", "" を挿入する。
"""
import re
import sys

for path, expected in [
    ("detergentmc-protocol/src/test/java/io/detergentmc/protocol/ResourcePackMode759Test.java",
     {34: 36, 4: 4, 32: 34}),
    ("detergentmc-protocol/src/test/java/io/detergentmc/protocol/ResourcePackMode760Test.java",
     {32: 34, 4: 4}),
]:
    src = open(path, encoding="utf-8").read()
    out = []
    pos = 0
    changed = 0
    for m in re.finditer(r"ResourcePackMode\.decide\(", src):
        start = m.start()
        # 呼び出しの終わり（対応する ");"）を探す
        end = src.find(");", m.end())
        assert end > 0
        call = src[m.end():end]
        args = [a.strip() for a in call.split(",")]
        total = len(args)
        assert total in expected, f"{path}: unexpected arg count {total} at {start}"
        if total == 4:
            continue  # 4 引数の短いオーバーロードは変更不要
        # VALID_SHA1_763 の直後から "" を数える
        idx763 = args.index("VALID_SHA1_763")
        empties = [i for i in range(idx763 + 1, len(args)) if args[i] == '""']
        # 先頭 20 個が 764〜774 ペア（10 ペア）。その直後に 775 ペアを入れる。
        assert len(empties) >= 20, f"{path}: only {len(empties)} empties after 763 at {start}"
        target = empties[19]
        # target 番目の引数の文字オフセットを求める
        off = m.end()
        count = -1
        for tm in re.finditer(r"[^,\s][^,]*", call):
            count += 1
            if count == target:
                insert_at = off + tm.end()
                break
        else:
            raise AssertionError("token not found")
        out.append(src[pos:insert_at])
        out.append(', ""')
        out.append(', ""')
        pos = insert_at
        changed += 1
    out.append(src[pos:])
    new_src = "".join(out)
    # 検証：各呼び出しの引数が期待通り増えていること
    for m in re.finditer(r"ResourcePackMode\.decide\((.*?)\);", new_src, re.S):
        args = [a.strip() for a in m.group(1).split(",")]
        assert len(args) in expected.values(), f"{path}: bad arg count {len(args)} after patch"
    open(path, "w", encoding="utf-8").write(new_src)
    print(f"{path}: {changed} calls updated")
