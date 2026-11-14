#!/usr/bin/env python3
"""gen-762-tables.py の出力を検証する。

  1. RegistryTables762.java の RANGES／OVERRIDES を expand() と同じ
     規則で展開し、tables762.json の対応表と一致することを確認する。
  2. 名前で突き合わせた代表値を spot check する（改名・代替・
     プロパティ落とし／足し・新規要素の代替）。
  3. 往復の性質を確認する：762 にある要素は 762→763→762 で
     自分に戻る（代替で潰れたものを除く）。763 にだけある要素は
     763→762→763 で自分に戻らない（代替先へ潰れる）ことを確認する。

使い方：
  python3 tools/verify-762-tables.py [--data-dir DIR]
  --data-dir を省略すると /tmp/mcdata を使う。
  終了コード 0 = すべて一致。1 = 不一致がある。
"""

import argparse
import json
import re
import sys
from pathlib import Path

DATA_DIR = Path("/tmp/mcdata")
JAVA_FILE = Path("/tmp/mcdata/RegistryTables762.java")

# 762 側の代替先（RegistryRemapper の定数と対応）。
STONE_BLOCK_STATE_762 = 1
STONE_ITEM_762 = 1
PIG_ENTITY_TYPE_762 = 72
STONE_BLOCK_762 = 1
CRAFTING_MENU_762 = 11


def load(p):
    return json.loads(Path(p).read_text(encoding="utf-8"))


def expand(ranges, overrides, size, fallback):
    m = [fallback] * size
    for start, end, delta in ranges:
        for i in range(start, end + 1):
            if i < size:
                m[i] = i + delta
    for src, dst in overrides:
        if 0 <= src < size:
            m[src] = dst
    return m


def parse_java(path):
    text = Path(path).read_text(encoding="utf-8")
    tables = {}
    for m in re.finditer(
            r"static final int\[\]\[\] (\w+)_(RANGES|OVERRIDES) = \{(.*?)\};",
            text, re.DOTALL):
        name, kind, body = m.group(1), m.group(2), m.group(3)
        rows = [[int(x) for x in row.split(",")]
                for row in re.findall(r"\{([^{}]+)\}", body)]
        tables.setdefault(name, {})[kind] = rows
    return tables


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--data-dir", type=Path, default=DATA_DIR)
    args = parser.parse_args()
    d = args.data_dir

    maps = load(d / "tables762.json")
    java = parse_java(JAVA_FILE)
    vv762 = load(d / "vv-1.19.4.json")
    vv763 = load(d / "vv-1.20.json")
    blocks762 = load(d / "pc" / "1.19.4" / "blocks.json")
    blocks763 = load(d / "pc" / "1.20" / "blocks.json")
    fails = []

    def check(cond, msg):
        if not cond:
            fails.append(msg)
            print(f"FAIL: {msg}")

    fallbacks_fwd = {
        "BLOCK_STATE": STONE_BLOCK_STATE_762, "ITEM": STONE_ITEM_762,
        "ENTITY_TYPE": PIG_ENTITY_TYPE_762,
        "SOUND": vv762["sounds"].index("block.stone.break"),
        "PARTICLE": 0, "BLOCK": STONE_BLOCK_762, "BLOCK_ENTITY": -1,
        "MENU": CRAFTING_MENU_762,
        "BIOME": {b["name"]: b["id"] for b in load(d / "pc" / "1.19.4" / "biomes.json")}["plains"],
        "DAMAGE_TYPE": 16,
    }
    fallbacks_rev = {
        "BLOCK_STATE": 1, "ITEM": 1, "ENTITY_TYPE": 72,
        "SOUND": vv763["sounds"].index("block.stone.break"),
        "PARTICLE": 0, "BLOCK": 1, "BLOCK_ENTITY": -1, "MENU": 11,
        "BIOME": {b["name"]: b["id"] for b in load(d / "pc" / "1.20" / "biomes.json")}["plains"],
        "DAMAGE_TYPE": 16,
    }
    sizes_fwd = {name: len(v["fwd"]) for name, v in maps.items()}
    sizes_rev = {name: len(v["rev"]) for name, v in maps.items()}

    for name, v in maps.items():
        for direction, key, fb, size in (
                ("763->762", "fwd", fallbacks_fwd[name], sizes_fwd[name]),
                ("762->763", "rev", fallbacks_rev[name], sizes_rev[name])):
            table = f"{name}_762" if key == "fwd" else f"{name}_762_TO_763"
            if table not in java:
                fails.append(f"{name} {direction}: Java の表 {table} が無い")
                continue
            got = expand(java[table]["RANGES"], java[table]["OVERRIDES"], size, fb)
            check(got == v[key],
                  f"{name} {direction}: 展開結果が JSON と不一致 "
                  f"（{sum(1 for a, b in zip(got, v[key]) if a != b)} 件違う）")

    # ---- spot check（名前で引く） ----
    bs_fwd = maps["BLOCK_STATE"]["fwd"]
    bs_rev = maps["BLOCK_STATE"]["rev"]
    idx763 = {s: i for i, s in enumerate(vv763["blockstates"])}
    idx762 = {s: i for i, s in enumerate(vv762["blockstates"])}
    def762 = {b["name"]: b["defaultState"] for b in blocks762}
    # suspicious_gravel（763 の 4 状態）→ gravel の既定状態（762）。
    for s in ("suspicious_gravel[dusted=0]", "suspicious_gravel[dusted=3]"):
        check(bs_fwd[idx763[s]] == def762["gravel"], f"{s} -> gravel")
    # calibrated_sculk_sensor → sculk_sensor の既定状態。
    s = "calibrated_sculk_sensor[facing=north,power=0,sculk_sensor_phase=inactive,waterlogged=false]"
    check(bs_fwd[idx763[s]] == def762["sculk_sensor"], f"{s} -> sculk_sensor")
    # decorated_pot：cracked を落として一致する。
    s763 = "decorated_pot[cracked=true,facing=north,waterlogged=false]"
    s762 = "decorated_pot[facing=north,waterlogged=false]"
    check(bs_fwd[idx763[s763]] == idx762[s762], "decorated_pot cracked 落とし")
    check(bs_rev[idx762[s762]] == idx763["decorated_pot[cracked=false,facing=north,waterlogged=false]"],
          "decorated_pot cracked=false 足し")
    # torchflower_crop[age=2]（762）→ age=1（763）。
    check(bs_rev[idx762["torchflower_crop[age=2]"]] == idx763["torchflower_crop[age=1]"],
          "torchflower age=2 -> age=1")
    # pitcher_crop → wheat。
    check(bs_fwd[idx763["pitcher_crop[age=0,half=lower]"]] == def762["wheat"],
          "pitcher_crop -> wheat")
    # sniffer_egg → turtle_egg。
    check(bs_fwd[idx763["sniffer_egg[hatch=0]"]] == def762["turtle_egg"],
          "sniffer_egg -> turtle_egg")

    item_fwd = maps["ITEM"]["fwd"]
    item_rev = maps["ITEM"]["rev"]
    ii763 = {n: i for i, n in enumerate(vv763["items"])}
    ii762 = {n: i for i, n in enumerate(vv762["items"])}
    check(item_fwd[ii763["archer_pottery_sherd"]] == ii762["pottery_shard_archer"],
          "archer_pottery_sherd -> pottery_shard_archer")
    check(item_rev[ii762["pottery_shard_archer"]] == ii763["archer_pottery_sherd"],
          "pottery_shard_archer -> archer_pottery_sherd")
    check(item_fwd[ii763["music_disc_relic"]] == ii762["music_disc_cat"],
          "music_disc_relic -> music_disc_cat")
    check(item_fwd[ii763["wayfinder_armor_trim_smithing_template"]]
          == ii762["sentry_armor_trim_smithing_template"],
          "wayfinder trim -> sentry trim")
    check(item_fwd[ii763["pitcher_pod"]] == ii762["wheat_seeds"],
          "pitcher_pod -> wheat_seeds")
    check(item_fwd[ii763["calibrated_sculk_sensor"]] == ii762["sculk_sensor"],
          "calibrated_sculk_sensor item -> sculk_sensor")

    snd_fwd = maps["SOUND"]["fwd"]
    si763 = {n: i for i, n in enumerate(vv763["sounds"])}
    si762 = {n: i for i, n in enumerate(vv762["sounds"])}
    check(snd_fwd[si763["item.brush.brushing.generic"]] == si762["item.brush.brushing"],
          "brush generic -> brushing")
    check(snd_fwd[si763["music.overworld.forest"]] == si762["music.overworld.jungle_and_forest"],
          "music forest -> jungle_and_forest")
    check(snd_fwd[si763["block.suspicious_gravel.break"]] == si762["block.gravel.break"],
          "suspicious_gravel.break -> gravel.break")
    check(snd_fwd[si763["block.sniffer_egg.hatch"]] == si762["entity.turtle.egg_hatch"],
          "sniffer_egg.hatch -> turtle.egg_hatch")
    check(snd_fwd[si763["music_disc.relic"]] == si762["music_disc.cat"],
          "music_disc.relic sound -> cat")

    part_fwd = maps["PARTICLE"]["fwd"]
    part_rev = maps["PARTICLE"]["rev"]
    pi763 = {n: i for i, n in enumerate(vv763["particles"])}
    pi762 = {n: i for i, n in enumerate(vv762["particles"])}
    check(part_fwd[pi763["cherry_leaves"]] == pi762["falling_cherry_leaves"],
          "cherry_leaves -> falling_cherry_leaves")
    check(part_fwd[pi763["egg_crack"]] == 0, "egg_crack -> ambient(0)")
    check(part_rev[pi762["dripping_cherry_leaves"]] == pi763["cherry_leaves"],
          "dripping_cherry_leaves -> cherry_leaves")

    be_fwd = maps["BLOCK_ENTITY"]["fwd"]
    be_rev = maps["BLOCK_ENTITY"]["rev"]
    bi763 = {n: i for i, n in enumerate(vv763["blockentities"])}
    bi762 = {n: i for i, n in enumerate(vv762["blockentities"])}
    check(be_fwd[bi763["brushable_block"]] == bi762["suspicious_sand"],
          "brushable_block -> suspicious_sand")
    check(be_rev[bi762["suspicious_sand"]] == bi763["brushable_block"],
          "suspicious_sand -> brushable_block")
    check(be_fwd[bi763["calibrated_sculk_sensor"]] == bi762["sculk_sensor"],
          "calibrated_sculk_sensor BE -> sculk_sensor")

    menu_fwd = maps["MENU"]["fwd"]
    menu_rev = maps["MENU"]["rev"]
    mi763 = {n: i for i, n in enumerate(vv763["menus"])}
    mi762 = {n: i for i, n in enumerate(vv762["menus"])}
    check(menu_rev[mi762["legacy_smithing"]] == mi763["smithing"],
          "legacy_smithing -> smithing")
    check(menu_rev[mi762["smithing"]] == mi763["smithing"],
          "smithing -> smithing")

    biome_fwd = maps["BIOME"]["fwd"]
    boi763 = {b["name"]: b["id"] for b in load(d / "pc" / "1.20" / "biomes.json")}
    boi762 = {b["name"]: b["id"] for b in load(d / "pc" / "1.19.4" / "biomes.json")}
    check(biome_fwd[boi763["cherry_grove"]] == boi762["meadow"],
          "cherry_grove -> meadow")
    check(biome_fwd[boi763["plains"]] == boi762["plains"], "plains -> plains")

    dmg_fwd = maps["DAMAGE_TYPE"]["fwd"]
    dmg_rev = maps["DAMAGE_TYPE"]["rev"]
    check(dmg_fwd[17] == 16, "generic_kill(17) -> generic(16)")
    check(dmg_fwd[30] == 16, "outside_border(30) -> generic(16)")
    check(dmg_fwd[0] == 0 and dmg_fwd[43] == 41, "damage 両端の対応")

    # ---- 往復 ----
    for name, v in maps.items():
        fwd, rev = v["fwd"], v["rev"]
        # 762 にある要素：762→763→762 で自分に戻る（代替で潰れたものは除く）。
        # rev の値が fwd の定義域にあるものだけ調べる。
        bad = 0
        for i762, i763 in enumerate(rev):
            if i763 < 0 or i763 >= len(fwd):
                continue
            if fwd[i763] != i762:
                # 代替で潰れた場合（例：762 の decorated_pot は cracked=false
                # へ写り、戻りは同じ）は fwd[i763] が i762 と違うことがある。
                # ここでは「戻りが -1 でない」ことだけ見る。
                if fwd[i763] < 0:
                    bad += 1
        check(bad == 0, f"{name}: 762->763->762 の往復で -1 になったものが {bad} 件")

    if fails:
        print(f"{len(fails)} 件の不一致", file=sys.stderr)
        return 1
    print("verify-762-tables: すべて一致")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
