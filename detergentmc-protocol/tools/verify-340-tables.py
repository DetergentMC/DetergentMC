#!/usr/bin/env python3
"""RegistryTables340.java（と分割クラス）の検証。

gen-340-tables.py を実行して期待値（JSON）を実データから作り直し、
生成済みの Java ファイル（本体＋ブロック状態の分割クラス）を
読んで展開した結果と全件照合する。意味対応の代表値も確認する
（羊毛の色、原木の軸、石・ブタ・音・剣・バイオーム）。

使い方：
  python3 tools/verify-340-tables.py [--java-dir DIR]
  --java-dir を省略すると src/main/java/io/detergentmc/protocol を使う。
"""

import argparse
import json
import re
import subprocess
import sys
from pathlib import Path

MC = Path("/home/hatch/workspace/mcdata404")
PREP = Path("/home/hatch/workspace/legacy-prep-340/mcdata/1.12")
GEN113 = Path("/home/hatch/workspace/legacy-prep-393/gen-1.13/generated/reports")


def parse_table(text, name):
    m = re.search(re.escape(name) + r"\s*=\s*\{(.*?)\n    \};", text, re.S)
    if not m:
        raise SystemExit(f"表が見つからない: {name}")
    return [tuple(int(x) for x in e.split(","))
            for e in re.findall(r"\{([^{}]+)\}", m.group(1))]


def parse_concat_table(text, name, java_dir):
    m = re.search(re.escape(name) + r"\s*=\s*concat\(([^)]+)\);", text)
    if not m:
        raise SystemExit(f"concat 表が見つからない: {name}")
    out = []
    for cname in re.findall(r"(RegistryTables340\w+)\.V", m.group(1)):
        ctext = (java_dir / f"{cname}.java").read_text(encoding="utf-8")
        out.extend(parse_table(ctext, "V"))
    return out


def parse_string_table(text, name):
    m = re.search(re.escape(name) + r"\s*=\s*\{(.*?)\n    \};", text, re.S)
    if not m:
        raise SystemExit(f"文字列表が見つからない: {name}")
    return [tuple(re.findall(r'"([^"]*)"', e))
            for e in re.findall(r"\{([^{}]+)\}", m.group(1))]


def parse_string_array(text, name):
    m = re.search(re.escape(name) + r"\s*=\s*\{(.*?)\n    \};", text, re.S)
    if not m:
        raise SystemExit(f"文字列配列が見つからない: {name}")
    return re.findall(r'"([^"]*)"', m.group(1))


def expand(size, ranges, overrides, fallback):
    out = [fallback] * size
    for a, b, d in ranges:
        for i in range(a, b + 1):
            out[i] = i + d
    for a, v in overrides:
        out[a] = v
    return out


def load(p):
    return json.loads(Path(p).read_text(encoding="utf-8"))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--java-dir", default=None)
    args = parser.parse_args()
    tools = Path(__file__).parent
    java_dir = Path(args.java_dir) if args.java_dir else \
        tools.parent / "src/main/java/io/detergentmc/protocol"

    # 期待値を実データから作り直す
    exp_path = Path("/tmp/tables-340-expected.json")
    subprocess.run([sys.executable, str(tools / "gen-340-tables.py"),
                    "--json", str(exp_path), "--out", "/tmp/RegistryTables340.java"],
                   check=True, capture_output=True)
    exp = load(exp_path)
    t393 = load(MC / "tables-393.json")

    text = (java_dir / "RegistryTables340.java").read_text(encoding="utf-8")

    # rev 側（758 空間）の代替値を 393 の表から逆引き（生成器と同じ）
    vv113 = load(MC / "mapping-1.13.json")
    snd113 = {n: i for i, n in enumerate(vv113["sounds"])}
    e113 = load(MC / "pc/1.13.2/entities.json")
    mob113 = {re.sub(r"[^a-z0-9_]", "", x["name"]): x["id"]
              for x in e113 if x["type"] == "mob"}
    bio113 = {b["name"]: b["id"] for b in load(MC / "pc/1.13.2/biomes.json")}
    fallbacks_fwd = {
        "BLOCK_STATE": 16, "ITEM": 1, "ENTITY_TYPE": 90,
        "SOUND": 450, "PARTICLE": 0, "BLOCK": 1,
        "BLOCK_ENTITY": -1, "BIOME": 1, "DAMAGE_TYPE": 16,
    }
    fallbacks_rev = {
        "BLOCK_STATE": 1, "ITEM": 1,
        "ENTITY_TYPE": t393["ENTITY_TYPE"]["rev"][mob113["pig"]],
        "SOUND": t393["SOUND"]["rev"][snd113["block.stone.break"]],
        "PARTICLE": 0, "BLOCK": 1, "BLOCK_ENTITY": -1,
        "BIOME": t393["BIOME"]["rev"][bio113["plains"]],
        "DAMAGE_TYPE": 16,
    }

    fails = 0
    for cat, tab in exp.items():
        for suffix, key, fb in (("_340", "fwd", fallbacks_fwd[cat]),
                                ("_340_TO_758", "rev", fallbacks_rev[cat])):
            name = cat + suffix
            ranges = parse_table(text, name + "_RANGES")
            if cat == "BLOCK_STATE":
                overrides = parse_concat_table(text, name + "_OVERRIDES", java_dir)
            else:
                overrides = parse_table(text, name + "_OVERRIDES")
            got = expand(len(tab[key]), ranges, overrides, fb)
            if got != tab[key]:
                diff = [i for i, (a, b) in enumerate(zip(got, tab[key])) if a != b]
                print(f"NG {name}: {len(diff)} 件不一致（先頭 {diff[:5]}）")
                fails += 1
            else:
                print(f"OK {name}: {len(tab[key])} 件一致")

    # 音の名前フォールバックと名前配列（音名表は分離クラス側）
    snd_text = (java_dir / "RegistryTables340Snd.java").read_text(encoding="utf-8")
    snd_f = parse_string_table(snd_text, "FWD")
    snd_r = parse_string_table(snd_text, "REV")
    exp_f = [tuple(p) for p in load(tools / "sound-names-758-to-340.json")]
    exp_r = [tuple(p) for p in load(tools / "sound-names-340-to-758.json")]
    print(("OK" if snd_f == exp_f else "NG") +
          f" SOUND_NAME_FALLBACKS_758_TO_340: {len(snd_f)} 対")
    print(("OK" if snd_r == exp_r else "NG") +
          f" SOUND_NAME_FALLBACKS_340_TO_758: {len(snd_r)} 対")
    if snd_f != exp_f or snd_r != exp_r:
        fails += 1
    be = parse_string_array(text, "BLOCK_ENTITY_NAMES_340")
    biome = parse_string_array(text, "BIOME_NAMES_340")
    print(f"BLOCK_ENTITY_NAMES_340: {len(be)} 件（先頭 {be[0]}）")
    print(f"BIOME_NAMES_340: {len(biome)} 件（[1]={biome[1]}）")
    if len(be) != 24 or len(biome) != 168 or biome[1] != "plains":
        print("NG 名前配列の件数／内容")
        fails += 1

    # ---- 意味対応の代表値 ----
    bs_rev = exp["BLOCK_STATE"]["rev"]   # (id<<4|meta) → 758 状態
    bs_fwd = exp["BLOCK_STATE"]["fwd"]   # 758 状態 → (id<<4|meta)
    # 758 側の状態文字列を作る（gen-1.13 の状態を 393 経由で 758 に載せる）
    dg = load(GEN113 / "blocks.json")
    states393 = [None] * sum(len(b["states"]) for b in dg.values())
    for key, blk in dg.items():
        nm = key.split(":", 1)[1]
        for st in blk["states"]:
            props = ",".join(f"{k}={str(v).lower()}"
                             for k, v in sorted(st.get("properties", {}).items()))
            states393[st["id"]] = nm + (f"[{props}]" if props else "")

    def name758_of_state(s758):
        s393 = t393["BLOCK_STATE"]["fwd"][s758]
        return states393[s393]

    checks = []
    def chk(label, got, want):
        ok = got == want
        checks.append(ok)
        print(("OK " if ok else "NG ") + f"{label}: {got}" +
              ("" if ok else f"（期待 {want}）"))

    chk("空気 (0,0)", name758_of_state(bs_rev[0]), "air")
    chk("石 (1,0)", name758_of_state(bs_rev[(1 << 4) | 0]), "stone")
    chk("白羊毛 (35,0)", name758_of_state(bs_rev[(35 << 4) | 0]),
        "white_wool")
    chk("黒羊毛 (35,15)", name758_of_state(bs_rev[(35 << 4) | 15]),
        "black_wool")
    chk("原木 (17,0) axis=y", name758_of_state(bs_rev[(17 << 4) | 0]),
        "oak_log[axis=y]")
    chk("原木 (17,4) axis=x", name758_of_state(bs_rev[(17 << 4) | 4]),
        "oak_log[axis=x]")
    chk("原木 (17,12) 樹皮", name758_of_state(bs_rev[(17 << 4) | 12]),
        "oak_wood[axis=y]")
    chk("草ブロック (2,0)", name758_of_state(bs_rev[(2 << 4) | 0]),
        "grass_block[snowy=false]")
    # 往復: 758 の white_wool 既定状態 → 340 → (35,0)
    wool758 = t393["BLOCK_STATE"]["rev"][states393.index("white_wool")]
    chk("758 white_wool → 340", bs_fwd[wool758], (35 << 4) | 0)
    # エンティティ・音・アイテム・バイオーム
    chk("ブタ 340→758", exp["ENTITY_TYPE"]["rev"][90],
        t393["ENTITY_TYPE"]["rev"][mob113["pig"]])
    chk("音 450 は block.stone.break の 758 ID", exp["SOUND"]["rev"][450],
        t393["SOUND"]["rev"][snd113["block.stone.break"]])
    items113 = [k.split(":", 1)[1] for k, _ in sorted(
        load(GEN113 / "items.json").items(), key=lambda kv: kv[1]["protocol_id"])]
    sword758 = t393["ITEM"]["rev"][items113.index("diamond_sword")]
    chk("ダイヤ剣 340 ID 276 → 758", exp["ITEM"]["rev"][276], sword758)
    chk("平原 340→758", exp["BIOME"]["rev"][1],
        t393["BIOME"]["rev"][bio113["plains"]])
    if not all(checks):
        fails += 1

    print("検証結果:", "すべて一致" if fails == 0 else f"{fails} 件の不一致")
    return 1 if fails else 0


if __name__ == "__main__":
    sys.exit(main())
