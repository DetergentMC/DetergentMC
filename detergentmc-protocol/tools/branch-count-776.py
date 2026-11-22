#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""ProxySession.java のパッチ前後で、メソッドごとの isXXX() 出現回数を数え比べる。
罠1：生成器の shift() は if-chain の末尾を落とす。数が減っているメソッドは
欠落の疑いがある。

使い方:
  python3 branch-count-776.py          # HEAD(dae7e58) と作業ツリーを比較
  python3 branch-count-776.py <rev>    # <rev> と作業ツリーを比較
"""
import re
import subprocess
import sys
from collections import Counter

PATH = "detergentmc-protocol/src/main/java/io/detergentmc/protocol/ProxySession.java"
IS_RE = re.compile(r"\bis(?:Server)?(?:763|764|765|766|767|768|769|770|771|772|773|774|775|776)\(\)")
METHOD_RE = re.compile(r"^\s{4}(?:private|protected|public|static|final)\b[\w<>\[\], ]*?\s+(\w+)\s*\([^)]*\)\s*(?:throws[\w, ]+)?\s*\{\s*$")


def method_of(lines, pos):
    """pos 行が属するメソッド名を、直前のメソッド宣言から求める。"""
    for i in range(pos, -1, -1):
        m = METHOD_RE.match(lines[i])
        if m:
            return m.group(1)
    return "<top>"


def counts(text):
    lines = text.split("\n")
    per_method = {}
    total = Counter()
    for i, line in enumerate(lines):
        for m in IS_RE.finditer(line):
            name = m.group(0)
            total[name] += 1
            meth = method_of(lines, i)
            key = (meth, name)
            per_method[key] = per_method.get(key, 0) + 1
    return total, per_method


def main():
    rev = sys.argv[1] if len(sys.argv) > 1 else "dae7e58"
    old = subprocess.run(
        ["git", "show", f"{rev}:{PATH}"], capture_output=True, text=True, check=True
    ).stdout
    new = open(PATH, encoding="utf-8").read()
    old_total, old_pm = counts(old)
    new_total, new_pm = counts(new)
    print(f"== total isXXX() : {rev} -> worktree ==")
    all_names = sorted(set(old_total) | set(new_total))
    for name in all_names:
        o, n = old_total.get(name, 0), new_total.get(name, 0)
        flag = "" if n >= o else "  <-- DECREASED"
        print(f"  {name}: {o} -> {n}{flag}")
    print("== per-method decreases (suspected drops) ==")
    drops = 0
    for key in sorted(set(old_pm) | set(new_pm)):
        o, n = old_pm.get(key, 0), new_pm.get(key, 0)
        if n < o:
            drops += 1
            print(f"  {key[0]} {key[1]}: {o} -> {n}")
    print(f"drops: {drops}")
    print("== per-method increases (new is776 branches, top 40) ==")
    inc = sorted(
        ((k, old_pm.get(k, 0), new_pm.get(k, 0)) for k in set(new_pm) if new_pm[k] > old_pm.get(k, 0)),
        key=lambda t: -t[2],
    )
    for k, o, n in inc[:40]:
        print(f"  {k[0]} {k[1]}: {o} -> {n}")
    print(f"increased methods: {len(inc)}")
    return 1 if drops else 0


if __name__ == "__main__":
    sys.exit(main())
