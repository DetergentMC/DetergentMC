#!/usr/bin/env python3
"""109 Stage D2 の追補2：ResourcePackServer の委譲コンストラクタへ
109 スロット分の null, null を挿入する。

フルコンストラクタのパラメータは (packFile, sha1, pack316File,
sha1316, pack210File, sha1210, pack110File, sha1110, pack109File,
sha1109, pack315File, ...) の順で、109 のペアは 110 のペアの直後
に入る。フルへ直接委譲する this(...) は引数が2つ不足するため、
8番目の引数の直後へ "null, null" を挿入する（110 の D2c と同じ
手順で、変種が37になる分だけ定数をずらす）。
"""

import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent.parent
SERVER = ROOT / "detergentmc-protocol/src/main/java/io/detergentmc/protocol/ResourcePackServer.java"

FULL_ARGS = 79  # packFile, sha1 + 変種37×2 + (port, required, promptKey)
TARGET = FULL_ARGS - 2
INSERT_AFTER = 8  # packFile, sha1, pack316File, sha1316, pack210File, sha1210, pack110File, sha1110


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
            args = args[:INSERT_AFTER] + [" null", " null"] + args[INSERT_AFTER:]
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
