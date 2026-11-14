#!/usr/bin/env python3
"""
DetergentMC 逆向き（764 -> 765）反転表の検証スクリプト。

RegistryRemapper.java の *_UP_764_MAP は、既存の *_764 表
（765 -> 764）を起動時に反転して作る。反転の決め方（区間を先に、
上書きを後から。音だけ4件の補正）は、このスクリプトで名前ベースの
正解（ViaVersion Mappings の順序付きリストを名前で突き合わせたもの）
と照合して検証する。

情報源（推測で埋めない）：
  - https://raw.githubusercontent.com/ViaVersion/Mappings/main/mappings/mapping-1.20.2.json （764）
  - https://raw.githubusercontent.com/ViaVersion/Mappings/main/mappings/mapping-1.20.3.json （765）

使い方：
  python3 tools/verify-reverse-tables.py [--data-dir DIR]
  --data-dir を省略すると detergentmc-protocol/build/remap-data を使う。
  JSON が無ければダウンロードする。照合に失敗すると exit 1。

検証する内容：
  1. 764の全IDに765側の対応がある（反転で欠ける番号が無い）。
  2. 反転した表が名前ベースの正解と一致する（音の4件の補正を含む）。
  3. 往復（764 -> 765 -> 764）で元の番号に戻る。
  4. アイテムの反転が既存の ITEM_REVERSE_764 表（名前から作ったもの）と一致する。
"""

import argparse
import json
import re
import sys
import urllib.request
from pathlib import Path

VV_BASE = "https://raw.githubusercontent.com/ViaVersion/Mappings/main/mappings"
FILES = {
    "vv-1.20.2.json": f"{VV_BASE}/mapping-1.20.2.json",
    "vv-1.20.3.json": f"{VV_BASE}/mapping-1.20.3.json",
}

# Java 側の SOUND_UP_764_CORRECTIONS と同じ値（RegistryRemapper.java から読む）。
SOUND_CORRECTIONS = [(666, 706), (667, 707), (674, 714), (675, 715)]

# (表の接頭辞, ViaVersion のキー, 765側の件数, 764側の件数, フォールバック)
SPECS = [
    ("BLOCK_STATE", "blockstates", 26644, 24276, 1),
    ("ITEM", "items", 1312, 1255, 1),
    ("ENTITY_TYPE", "entities", 126, 124, 73),
    ("SOUND", "sounds", 1539, 1485, 1312),
    ("PARTICLE", "particles", 101, 95, 0),
    ("BLOCK", "blocks", 1058, 1003, 1),
    ("MENU", "menus", 25, 24, 12),
]

RENAME = {"short_grass": "grass"}


def download(url, dest):
    print(f"取得中: {url}")
    req = urllib.request.Request(url, headers={"User-Agent": "DetergentMC-reverse-check"})
    with urllib.request.urlopen(req, timeout=60) as resp, open(dest, "wb") as out:
        out.write(resp.read())


def load(path):
    with open(path, encoding="utf-8") as f:
        return json.load(f)


def get_table(src, name):
    m = re.search(r"int\[\]\[\] " + name + r" = \{(.*?)\};", src, re.S)
    assert m, name
    return [tuple(map(int, t.split(","))) for t in re.findall(r"\{([^}]*)\}", m.group(1))]


def expand(ranges, overrides, size, fallback):
    table = [fallback] * size
    for start, end, delta in ranges:
        for i in range(start, end + 1):
            if i < size:
                table[i] = i + delta
    for x, y in overrides:
        if 0 <= x < size:
            table[x] = y
    return table


def invert_java(ranges, overrides, from_size, to_size, fallback, corrections=None):
    """RegistryRemapper.invert と同じ決め方（区間→上書き→補正）。"""
    inverse = [-1] * to_size
    for start, end, delta in ranges:
        for x in range(start, end + 1):
            y = x + delta
            if 0 <= y < to_size and inverse[y] < 0:
                inverse[y] = x
    for x, y in overrides:
        if 0 <= y < to_size and inverse[y] < 0:
            inverse[y] = x
    if corrections:
        for y, x in corrections:
            inverse[y] = x
    return [v if v >= 0 else fallback for v in inverse]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--data-dir", type=Path, default=None)
    args = parser.parse_args()

    script_dir = Path(__file__).resolve().parent
    repo_root = script_dir.parent.parent
    data_dir = args.data_dir or repo_root / "detergentmc-protocol" / "build" / "remap-data"
    data_dir.mkdir(parents=True, exist_ok=True)
    for name, url in FILES.items():
        dest = data_dir / name
        if not dest.exists():
            download(url, dest)

    src = (repo_root / "detergentmc-protocol" / "src" / "main" / "java"
           / "io" / "detergentmc" / "protocol" / "RegistryRemapper.java").read_text(encoding="utf-8")
    a764 = load(data_dir / "vv-1.20.2.json")  # 764
    b765 = load(data_dir / "vv-1.20.3.json")  # 765

    # Java 側の補正表がスクリプト側の想定と一致すること。
    m = re.search(r"SOUND_UP_764_CORRECTIONS = \{(.*?)\};", src, re.S)
    java_corrections = [tuple(map(int, t.split(",")))
                        for t in re.findall(r"\{([^}]*)\}", m.group(1))]
    if sorted(java_corrections) != sorted(SOUND_CORRECTIONS):
        print("SOUND_UP_764_CORRECTIONS が想定と違います:", java_corrections)
        return 1

    failures = 0
    for prefix, key, from_size, to_size, fallback in SPECS:
        ranges = get_table(src, prefix + "_764_RANGES")
        overrides = get_table(src, prefix + "_764_OVERRIDES")
        forward = expand(ranges, overrides, from_size, fallback)
        corrections = SOUND_CORRECTIONS if prefix == "SOUND" else None
        inverse = invert_java(ranges, overrides, from_size, to_size, fallback, corrections)

        # 1. 764の全IDに765側の対応がある（＝forward の像が764の全IDを覆う）。
        image = set(forward)
        missing = [y for y in range(to_size) if y not in image]
        if missing:
            print(f"[{prefix}] 反転で欠ける764の番号があります: {missing[:10]}（{len(missing)}件）")
            failures += 1

        # 2. 名前ベースの正解と一致する。
        index765 = {n: i for i, n in enumerate(b765[key])}
        mismatches = []
        for y, name in enumerate(a764[key]):
            want = index765.get(name, index765.get(RENAME.get(name, "")))
            if want is not None and inverse[y] != want:
                mismatches.append((y, name, inverse[y], want))
        if mismatches:
            print(f"[{prefix}] 名前との不一致: {len(mismatches)}件 {mismatches[:5]}")
            failures += 1

        # 3. 往復（764 -> 765 -> 764）で元に戻る。
        bad_round_trip = [y for y in range(to_size) if forward[inverse[y]] != y]
        if bad_round_trip:
            print(f"[{prefix}] 往復で戻らない番号があります: {bad_round_trip[:10]}")
            failures += 1
        print(f"[{prefix}] OK（764={to_size}件、名前一致、往復一致）")

    # 4. アイテムの反転が既存の ITEM_REVERSE_764 表と一致する。
    rev = expand(get_table(src, "ITEM_REVERSE_764_RANGES"),
                 get_table(src, "ITEM_REVERSE_764_OVERRIDES"), 1255, -1)
    ranges = get_table(src, "ITEM_764_RANGES")
    overrides = get_table(src, "ITEM_764_OVERRIDES")
    inverse_items = invert_java(ranges, overrides, 1312, 1255, 1)
    diffs = [(y, rev[y], inverse_items[y]) for y in range(1255) if rev[y] != inverse_items[y]]
    if diffs:
        print(f"[ITEM_REVERSE_764] 既存の逆変換表との差分: {len(diffs)}件 {diffs[:5]}")
        failures += 1
    else:
        print("[ITEM_REVERSE_764] OK（既存の逆変換表と一致）")

    if failures:
        print(f"検証失敗：{failures}件の問題があります。")
        return 1
    print("完了：逆向き反転表の検証はすべて成功しました。")
    return 0


if __name__ == "__main__":
    sys.exit(main())
