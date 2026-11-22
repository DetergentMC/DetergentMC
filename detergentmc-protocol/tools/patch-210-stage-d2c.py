#!/usr/bin/env python3
"""210 Stage D2 の追補2：ResourcePackServer の委譲コンストラクタへ
210 スロット分の null, null を挿入する。

フルコンストラクタのパラメータは (packFile, sha1, pack315File,
sha1315, pack210File, sha1210, pack335File, ...) の順で、210 の
ペアは 210 の直後に入った。フルへ直接委譲する this(...) は
引数が2つ不足するため、4番目の引数の直後へ "null, null" を挿入
する（210 の D2 で11件に行った手作業と同じ内容を、引数数で
判定して一括適用する）。
"""

import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent.parent
SERVER = ROOT / "detergentmc-protocol/src/main/java/io/detergentmc/protocol/ResourcePackServer.java"

FULL_ARGS = 73  # packFile, sha1 + 変種34×2 + (port, required, promptKey)
TARGET = FULL_ARGS - 2


def split_args(argstr):
    args, depth, cur = [], 0, ""
    for ch in argstr:
        if ch == "(":
            depth += 1
        elif ch == ")":
            depth -= 1
        if ch == "," and depth == 0:
            args.append(cur)
            cur = ""
        else:
            cur += ch
    if cur.strip():
        args.append(cur)
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
        # 対応する ");" を探す（括弧の深さで判定）
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
        if len(args) == TARGET and args[-1].strip() == "promptKey":
            args = args[:4] + [" null", " null"] + args[4:]
            out.append(text[i:start])
            out.append(",".join(args))
            out.append(text[k:k + 2])  # ");"
            i = k + 2
            fixed += 1
        else:
            out.append(text[i:j + 5])
            i = j + 5
    SERVER.write_text("".join(out), encoding="utf-8")
    print(f"委譲コンストラクタ {fixed} 件へ null, null を挿入した")
    return 0 if fixed > 0 else 1


if __name__ == "__main__":
    sys.exit(main())
