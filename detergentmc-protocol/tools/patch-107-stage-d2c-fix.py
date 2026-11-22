#!/usr/bin/env python3
"""107 Stage D2 の追補2・修正：ResourcePackServer のフルコンストラクタ
委譲の引数を 107 スロット込みで正す。

経緯：最初の d2c は FULL_ARGS を 85 と誤って（実際は 83）、
BuiltPack フル委譲に余分な null, null を挿入してしまった。
本スクリプトは冪等な形で全委譲を正規化する：フルコンストラクタ
（83 引数）への this(...) 委譲について、315 スロットの直前に
ある 107 スロット分の引数がちょうど2つになるよう調整する。
- Path 形式の便利コンストラクタ（81 引数）→ null, null を挿入
- BuiltPack フル委譲（d2 で built107 を追加済み＋d2c の余分な
  null で 85 引数）→ 余分な null, null を除去
- d2d が追加した「107 無し」BuiltPack 委譲（null が4つで 85
  引数）→ null, null の1組に正規化
"""

import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent.parent
SERVER = ROOT / "detergentmc-protocol/src/main/java/io/detergentmc/protocol/ResourcePackServer.java"


def split_args(argstr):
    args, depth, cur = [], 0, ""
    for ch in argstr:
        if ch == "(": depth += 1
        elif ch == ")": depth -= 1
        if ch == "," and depth == 0:
            args.append(cur); cur = ""
        else: cur += ch
    if cur.strip(): args.append(cur)
    return args


def main():
    text = SERVER.read_text(encoding="utf-8")
    out = []
    i = 0
    fixed = 0
    while True:
        j = text.find("this(", i)
        if j == -1:
            out.append(text[i:])
            break
        depth = 0
        k = j + len("this(")
        start = k
        while True:
            ch = text[k]
            if ch == "(":
                depth += 1
            elif ch == ")":
                if depth == 0:
                    break
                depth -= 1
            k += 1
        argstr = text[start:k]
        args = split_args(argstr)
        handled = False
        if len(args) >= 81 and args[-1].strip() == "promptKey":
            # 315 スロットの開始位置を探す（107 スロットはその直前2つ）。
            t = None
            for idx in range(12, len(args)):
                if "315" in args[idx]:
                    t = idx
                    break
            if t is not None:
                filler = args[12:t]
                if len(filler) != 2:
                    keep = [a for a in filler if "built107" in a]
                    if len(keep) == 2:
                        new_filler = keep
                    else:
                        new_filler = [" null", " null"]
                    args = args[:12] + new_filler + args[t:]
                    handled = True
                elif len(args) != 83:
                    handled = False
        if handled:
            out.append(text[i:start])
            out.append(",".join(args))
            out.append(text[k:k + 2])
            i = k + 2
            fixed += 1
        else:
            out.append(text[i:j + 5])
            i = j + 5
    text = "".join(out)

    # 第2段：81 引数のまま残った委譲（315 自体も null で 315 アンカーが
    # 利かない「except 系」）は、107 スロット位置（12番目）へ一律に
    # null, null を挿入する。81 引数の this(...) は全てフルコンスト
    # ラクタへの委譲であることを 108 ツリーで確認済み。
    out = []
    i = 0
    fixed2 = 0
    while True:
        j = text.find("this(", i)
        if j == -1:
            out.append(text[i:])
            break
        depth = 0
        k = j + len("this(")
        start = k
        while True:
            ch = text[k]
            if ch == "(":
                depth += 1
            elif ch == ")":
                if depth == 0:
                    break
                depth -= 1
            k += 1
        args = split_args(text[start:k])
        if len(args) == 81 and args[-1].strip() == "promptKey":
            args = args[:12] + [" null", " null"] + args[12:]
            out.append(text[i:start])
            out.append(",".join(args))
            out.append(text[k:k + 2])
            i = k + 2
            fixed2 += 1
        else:
            out.append(text[i:j + 5])
            i = j + 5
    SERVER.write_text("".join(out), encoding="utf-8")
    print(f"委譲 {fixed} 件を正規化し、{fixed2} 件へ null, null を挿入した")
    return 0


if __name__ == "__main__":
    sys.exit(main())
