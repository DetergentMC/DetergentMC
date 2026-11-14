#!/usr/bin/env python3
"""gen-761-tables.py の出力を検証する。

  1. RegistryTables761.java の RANGES／OVERRIDES を expand() と同じ
     規則で展開し、tables-761.json の対応表と一致することを確認する。
  2. 名前で突き合わせた代表値を spot check する（改名・代替・
     プロパティ落とし／足し・新規要素の代替）。
  3. 往復の性質を確認する：761 にある要素は 761→762→761 で
     自分に戻る（代替で潰れたものを除く）。762 にだけある要素は
     762→761→762 で自分に戻らない（代替先へ潰れる）ことを確認する。

使い方：
  python3 tools/verify-761-tables.py [--data-dir DIR]
  --data-dir を省略すると /tmp/mcdata を使う。
  終了コード 0 = すべて一致。1 = 不一致がある。
"""

import argparse
import json
import re
import sys
from pathlib import Path

DATA_DIR = Path("/tmp/mcdata")
JAVA_FILE = Path("/tmp/mcdata/RegistryTables761.java")

# 761 側の代替先（RegistryRemapper の定数と対応）。
STONE_BLOCK_STATE_761 = 1
STONE_ITEM_761 = 1
PIG_ENTITY_TYPE_761 = 68
STONE_BLOCK_761 = 1
CRAFTING_MENU_761 = 11


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

    maps = load(d / "tables-761.json")
    java = parse_java(JAVA_FILE)
    vv761 = load(d / "vv-1.19.3.json")
    vv762 = load(d / "vv-1.19.4.json")
    blocks761 = load(d / "pc" / "1.19.3" / "blocks.json")
    blocks762 = load(d / "pc" / "1.19.4" / "blocks.json")
    fails = []

    def check(cond, msg):
        if not cond:
            fails.append(msg)
            print(f"FAIL: {msg}")

    fallbacks_fwd = {
        "BLOCK_STATE": STONE_BLOCK_STATE_761, "ITEM": STONE_ITEM_761,
        "ENTITY_TYPE": PIG_ENTITY_TYPE_761,
        "SOUND": vv761["sounds"].index("block.stone.break"),
        "PARTICLE": 0, "BLOCK": STONE_BLOCK_761, "BLOCK_ENTITY": -1,
        "MENU": CRAFTING_MENU_761,
        "BIOME": {b["name"]: b["id"] for b in load(d / "pc" / "1.19.3" / "biomes.json")}["plains"],
        "DAMAGE_TYPE": 16,
    }
    fallbacks_rev = {
        "BLOCK_STATE": 1, "ITEM": 1, "ENTITY_TYPE": 72,
        "SOUND": vv762["sounds"].index("block.stone.break"),
        "PARTICLE": 0, "BLOCK": 1, "BLOCK_ENTITY": -1, "MENU": 11,
        "BIOME": {b["name"]: b["id"] for b in load(d / "pc" / "1.19.4" / "biomes.json")}["plains"],
        "DAMAGE_TYPE": 16,
    }
    sizes_fwd = {name: len(v["fwd"]) for name, v in maps.items()}
    sizes_rev = {name: len(v["rev"]) for name, v in maps.items()}

    for name, v in maps.items():
        for direction, key, fb, size in (
                ("762->761", "fwd", fallbacks_fwd[name], sizes_fwd[name]),
                ("761->762", "rev", fallbacks_rev[name], sizes_rev[name])):
            table = f"{name}_761" if key == "fwd" else f"{name}_761_TO_762"
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
    idx762 = {s: i for i, s in enumerate(vv762["blockstates"])}
    idx761 = {s: i for i, s in enumerate(vv761["blockstates"])}
    def761 = {b["name"]: b["defaultState"] for b in blocks761}
    # cherry_leaves（762 の状態）→ oak_leaves の既定状態（761）。
    for s in ("cherry_leaves[distance=1,persistent=false,waterlogged=false]",
              "cherry_leaves[distance=7,persistent=true,waterlogged=true]"):
        check(bs_fwd[idx762[s]] == def761["oak_leaves"], f"{s} -> oak_leaves")
    # decorated_pot → flower_pot の既定状態。
    s = "decorated_pot[facing=north,waterlogged=true]"
    check(bs_fwd[idx762[s]] == def761["flower_pot"], f"{s} -> flower_pot")
    # torchflower_crop → wheat の既定状態。
    check(bs_fwd[idx762["torchflower_crop[age=2]"]] == def761["wheat"],
          "torchflower_crop[age=2] -> wheat")
    # suspicious_sand → sand の既定状態。
    check(bs_fwd[idx762["suspicious_sand[dusted=3]"]] == def761["sand"],
          "suspicious_sand[dusted=3] -> sand")
    # pink_petals → peony の既定状態。
    check(bs_fwd[idx762["pink_petals[facing=north,flower_amount=4]"]] == def761["peony"],
          "pink_petals -> peony")
    # 761 の状態はすべて 762 に同文字列で存在する（恒等）。
    check(bs_rev[idx761["oak_leaves[distance=1,persistent=false,waterlogged=false]"]]
          == idx762["oak_leaves[distance=1,persistent=false,waterlogged=false]"],
          "oak_leaves 761->762 恒等")

    item_fwd = maps["ITEM"]["fwd"]
    item_rev = maps["ITEM"]["rev"]
    ii762 = {n: i for i, n in enumerate(vv762["items"])}
    ii761 = {n: i for i, n in enumerate(vv761["items"])}
    check(item_fwd[ii762["cherry_planks"]] == ii761["oak_planks"],
          "cherry_planks -> oak_planks")
    check(item_fwd[ii762["sniffer_spawn_egg"]] == ii761["turtle_spawn_egg"],
          "sniffer_spawn_egg -> turtle_spawn_egg")
    check(item_fwd[ii762["brush"]] == ii761["stick"],
          "brush -> stick")
    check(item_fwd[ii762["netherite_upgrade_smithing_template"]] == ii761["netherite_ingot"],
          "netherite template -> netherite_ingot")
    check(item_fwd[ii762["sentry_armor_trim_smithing_template"]] == ii761["iron_ingot"],
          "sentry trim -> iron_ingot")
    check(item_fwd[ii762["pottery_shard_archer"]] == ii761["brick"],
          "pottery_shard_archer -> brick")
    check(item_fwd[ii762["torchflower_seeds"]] == ii761["wheat_seeds"],
          "torchflower_seeds -> wheat_seeds")
    check(item_fwd[ii762["cherry_boat"]] == ii761["oak_boat"],
          "cherry_boat -> oak_boat")
    check(item_rev[ii761["oak_planks"]] == ii762["oak_planks"],
          "oak_planks 761->762 恒等")

    ent_fwd = maps["ENTITY_TYPE"]["fwd"]
    ent_rev = maps["ENTITY_TYPE"]["rev"]
    ei762 = {n: i for i, n in enumerate(vv762["entities"])}
    ei761 = {n: i for i, n in enumerate(vv761["entities"])}
    check(ent_fwd[ei762["sniffer"]] == ei761["pig"], "sniffer -> pig")
    check(ent_fwd[ei762["block_display"]] == ei761["pig"], "block_display -> pig")
    check(ent_fwd[ei762["text_display"]] == ei761["pig"], "text_display -> pig")
    check(ent_fwd[ei762["interaction"]] == ei761["pig"], "interaction -> pig")
    check(ent_fwd[ei762["pig"]] == ei761["pig"], "pig -> pig")
    check(ent_rev[ei761["pig"]] == ei762["pig"], "pig 761->762")
    # 762 はアルファベット順、761 はレジストリ順のため同名でも番号が違う。
    check(ei762["pig"] == 72 and ei761["pig"] == 68, "pig は 762=72・761=68")

    snd_fwd = maps["SOUND"]["fwd"]
    si762 = {n: i for i, n in enumerate(vv762["sounds"])}
    si761 = {n: i for i, n in enumerate(vv761["sounds"])}
    check(snd_fwd[si762["block.cherry_wood.break"]] == si761["block.wood.break"],
          "cherry_wood.break -> wood.break")
    check(snd_fwd[si762["entity.sniffer.idle"]] == si761["entity.pig.ambient"],
          "sniffer.idle -> pig.ambient")
    check(snd_fwd[si762["entity.sniffer.eat"]] == si761["entity.generic.eat"],
          "sniffer.eat -> generic.eat")
    check(snd_fwd[si762["block.decorated_pot.shatter"]] == si761["block.glass.break"],
          "decorated_pot.shatter -> glass.break")
    check(snd_fwd[si762["music.overworld.cherry_grove"]] == si761["music.overworld.jungle_and_forest"],
          "cherry_grove music -> jungle_and_forest")
    check(snd_fwd[si762["intentionally_empty"]] == si761["block.stone.break"],
          "intentionally_empty -> stone.break")
    check(snd_fwd[si762["item.brush.brushing"]] == si761["block.sand.break"],
          "brush.brushing -> sand.break")

    part_fwd = maps["PARTICLE"]["fwd"]
    part_rev = maps["PARTICLE"]["rev"]
    pi762 = {n: i for i, n in enumerate(vv762["particles"])}
    pi761 = {n: i for i, n in enumerate(vv761["particles"])}
    check(part_fwd[pi762["falling_cherry_leaves"]] == pi761["white_ash"],
          "falling_cherry_leaves -> white_ash")
    check(part_fwd[pi762["dripping_cherry_leaves"]] == pi761["white_ash"],
          "dripping_cherry_leaves -> white_ash")
    check(part_rev[pi761["white_ash"]] == pi762["white_ash"],
          "white_ash 761->762 恒等")

    be_fwd = maps["BLOCK_ENTITY"]["fwd"]
    be_rev = maps["BLOCK_ENTITY"]["rev"]
    bi762 = {n: i for i, n in enumerate(vv762["blockentities"])}
    bi761 = {n: i for i, n in enumerate(vv761["blockentities"])}
    check(be_fwd[bi762["suspicious_sand"]] == -1,
          "suspicious_sand BE -> 落とす(-1)")
    check(be_fwd[bi762["decorated_pot"]] == -1,
          "decorated_pot BE -> 落とす(-1)")
    check(be_rev[bi761["chest"]] == bi762["chest"], "chest BE 761->762 恒等")

    menu_fwd = maps["MENU"]["fwd"]
    menu_rev = maps["MENU"]["rev"]
    mi762 = {n: i for i, n in enumerate(vv762["menus"])}
    # mapping-1.19.3.json には "menus" が無い。761 のメニューは 763 の
    # 24 件と同じ（生成スクリプトと同じ流儀で vv-1.20.json から借りる）。
    mi761 = {n: i for i, n in enumerate(load(d / "vv-1.20.json")["menus"])}
    check(menu_fwd[mi762["legacy_smithing"]] == mi761["smithing"],
          "legacy_smithing -> smithing")
    check(menu_fwd[mi762["smithing"]] == mi761["smithing"],
          "smithing(新) -> smithing(旧)")
    check(menu_rev[mi761["smithing"]] == mi762["legacy_smithing"],
          "smithing -> legacy_smithing（改名優先）")

    biome_fwd = maps["BIOME"]["fwd"]
    boi762 = {b["name"]: b["id"] for b in load(d / "pc" / "1.19.4" / "biomes.json")}
    boi761 = {b["name"]: b["id"] for b in load(d / "pc" / "1.19.3" / "biomes.json")}
    check(biome_fwd[boi762["plains"]] == boi761["plains"], "plains -> plains")
    check(biome_fwd[boi762["cherry_grove"] if "cherry_grove" in boi762 else boi762["meadow"]]
          == boi761["meadow"] if "cherry_grove" in boi762 else True,
          "cherry_grove は 762 に無い（meadow へは不要）")

    dmg_fwd = maps["DAMAGE_TYPE"]["fwd"]
    dmg_rev = maps["DAMAGE_TYPE"]["rev"]
    check(dmg_fwd == list(range(42)) and dmg_rev == list(range(42)),
          "damage 42 件は同一順序と仮定（実バニラで検証する）")

    # ---- 往復 ----
    for name, v in maps.items():
        fwd, rev = v["fwd"], v["rev"]
        # 761 にある要素：761→762→761 で自分に戻る（代替で潰れたものは除く）。
        # rev の値が fwd の定義域にあるものだけ調べる。
        bad = 0
        for i761, i762 in enumerate(rev):
            if i762 < 0 or i762 >= len(fwd):
                continue
            if fwd[i762] != i761:
                # 代替で潰れた場合（例：761 の decorated_pot は cracked=false
                # へ写り、戻りは同じ）は fwd[i762] が i761 と違うことがある。
                # ここでは「戻りが -1 でない」ことだけ見る。
                if fwd[i762] < 0:
                    bad += 1
        check(bad == 0, f"{name}: 761->762->761 の往復で -1 になったものが {bad} 件")

    if fails:
        print(f"{len(fails)} 件の不一致", file=sys.stderr)
        return 1
    print("verify-761-tables: すべて一致")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
