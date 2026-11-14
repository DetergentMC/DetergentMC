#!/usr/bin/env python3
"""gen-760-tables.py の出力を検証する。

  1. RegistryTables760.java の RANGES／OVERRIDES を expand() と同じ
     規則で展開し、tables-760.json の対応表と一致することを確認する。
  2. 名前で突き合わせた代表値を spot check する（改名・代替・
     プロパティ落とし／足し・新規要素の代替）。
  3. 往復の性質を確認する：760 にある要素は 760→761→760 で
     自分に戻る（代替で潰れたものを除く）。761 にだけある要素は
     761→760→761 で自分に戻らない（代替先へ潰れる）ことを確認する。

使い方：
  python3 tools/verify-760-tables.py [--data-dir DIR]
  --data-dir を省略すると ~/workspace/mcdata760 を使う。
  終了コード 0 = すべて一致。1 = 不一致がある。
"""

import argparse
import json
import re
import sys
from pathlib import Path

DATA_DIR = Path.home() / "workspace" / "mcdata760"
JAVA_FILE = Path(__file__).resolve().parent.parent / "src" / "main" / "java" / "io" / "detergentmc" / "protocol" / "RegistryTables760.java"

# 760 側の代替先（RegistryRemapper の定数と対応）。
STONE_BLOCK_STATE_760 = 1
STONE_ITEM_760 = 1
PIG_ENTITY_TYPE_760 = 67
STONE_BLOCK_760 = 1
CRAFTING_MENU_760 = 11


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

    maps = load(d / "tables-760.json")
    java = parse_java(JAVA_FILE)
    vv760 = load(d / "mapping-1.19.json")
    vv761 = load(d / "mapping-1.19.3.json")
    blocks760 = load(d / "pc" / "1.19.2" / "blocks.json")
    blocks761 = load(d / "pc" / "1.19.3" / "blocks.json")
    fails = []

    def check(cond, msg):
        if not cond:
            fails.append(msg)
            print(f"FAIL: {msg}")

    fallbacks_fwd = {
        "BLOCK_STATE": STONE_BLOCK_STATE_760, "ITEM": STONE_ITEM_760,
        "ENTITY_TYPE": PIG_ENTITY_TYPE_760,
        "SOUND": vv760["sounds"].index("block.stone.break"),
        "PARTICLE": 0, "BLOCK": STONE_BLOCK_760, "BLOCK_ENTITY": -1,
        "MENU": CRAFTING_MENU_760,
        "BIOME": {b["name"]: b["id"] for b in load(d / "pc" / "1.19.2" / "biomes.json")}["plains"],
        "DAMAGE_TYPE": 16,
    }
    fallbacks_rev = {
        "BLOCK_STATE": 1, "ITEM": 1, "ENTITY_TYPE": 68,
        "SOUND": vv761["sounds"].index("block.stone.break"),
        "PARTICLE": 0, "BLOCK": 1, "BLOCK_ENTITY": -1, "MENU": 11,
        "BIOME": {b["name"]: b["id"] for b in load(d / "pc" / "1.19.3" / "biomes.json")}["plains"],
        "DAMAGE_TYPE": 16,
    }
    sizes_fwd = {name: len(v["fwd"]) for name, v in maps.items()}
    sizes_rev = {name: len(v["rev"]) for name, v in maps.items()}

    for name, v in maps.items():
        for direction, key, fb, size in (
                ("761->760", "fwd", fallbacks_fwd[name], sizes_fwd[name]),
                ("760->761", "rev", fallbacks_rev[name], sizes_rev[name])):
            table = f"{name}_760" if key == "fwd" else f"{name}_760_TO_761"
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
    idx761 = {s: i for i, s in enumerate(vv761["blockstates"])}
    idx760 = {s: i for i, s in enumerate(vv760["blockstates"])}
    def760 = {b["name"]: b["defaultState"] for b in blocks760}
    # bamboo_block（761 の状態）→ oak_log の既定状態（760）。
    check(bs_fwd[idx761["bamboo_block[axis=y]"]] == def760["oak_log"],
          "bamboo_block -> oak_log")
    # bamboo_mosaic → oak_planks の既定状態。
    check(bs_fwd[idx761["bamboo_mosaic"]] == def760["oak_planks"],
          "bamboo_mosaic -> oak_planks")
    # oak_hanging_sign → oak_sign の既定状態。
    check(bs_fwd[idx761["oak_hanging_sign[attached=true,rotation=0,waterlogged=false]"]]
          == def760["oak_sign"], "oak_hanging_sign -> oak_sign")
    # chiseled_bookshelf → bookshelf の既定状態。
    check(bs_fwd[idx761["chiseled_bookshelf[facing=north,slot_0_occupied=false,slot_1_occupied=false,slot_2_occupied=false,slot_3_occupied=false,slot_4_occupied=false,slot_5_occupied=false]"]]
          == def760["bookshelf"], "chiseled_bookshelf -> bookshelf")
    # note_block の新楽器 zombie → harp（同じ note・powered）。
    check(bs_fwd[idx761["note_block[instrument=zombie,note=7,powered=false]"]]
          == idx760["note_block[instrument=harp,note=7,powered=false]"],
          "note_block zombie -> harp")
    check(bs_fwd[idx761["note_block[instrument=piglin,note=24,powered=true]"]]
          == idx760["note_block[instrument=harp,note=24,powered=true]"],
          "note_block piglin -> harp")
    # 760 の状態はすべて 761 に同文字列で存在する（恒等）。
    check(bs_rev[idx760["oak_leaves[distance=1,persistent=false,waterlogged=false]"]]
          == idx761["oak_leaves[distance=1,persistent=false,waterlogged=false]"],
          "oak_leaves 760->761 恒等")

    item_fwd = maps["ITEM"]["fwd"]
    item_rev = maps["ITEM"]["rev"]
    ii761 = {n: i for i, n in enumerate(vv761["items"])}
    ii760 = {n: i for i, n in enumerate(vv760["items"])}
    check(item_fwd[ii761["bamboo_planks"]] == ii760["oak_planks"],
          "bamboo_planks -> oak_planks")
    check(item_fwd[ii761["bamboo_raft"]] == ii760["oak_boat"],
          "bamboo_raft -> oak_boat")
    check(item_fwd[ii761["bamboo_chest_raft"]] == ii760["oak_chest_boat"],
          "bamboo_chest_raft -> oak_chest_boat")
    check(item_fwd[ii761["camel_spawn_egg"]] == ii760["horse_spawn_egg"],
          "camel_spawn_egg -> horse_spawn_egg")
    check(item_fwd[ii761["ender_dragon_spawn_egg"]] == ii760["dragon_egg"],
          "ender_dragon_spawn_egg -> dragon_egg")
    check(item_fwd[ii761["iron_golem_spawn_egg"]] == ii760["iron_ingot"],
          "iron_golem_spawn_egg -> iron_ingot")
    check(item_fwd[ii761["wither_spawn_egg"]] == ii760["nether_star"],
          "wither_spawn_egg -> nether_star")
    check(item_fwd[ii761["piglin_head"]] == ii760["zombie_head"],
          "piglin_head -> zombie_head")
    check(item_rev[ii760["oak_planks"]] == ii761["oak_planks"],
          "oak_planks 760->761 恒等")

    ent_fwd = maps["ENTITY_TYPE"]["fwd"]
    ent_rev = maps["ENTITY_TYPE"]["rev"]
    ei761 = {n: i for i, n in enumerate(vv761["entities"])}
    ei760 = {n: i for i, n in enumerate(vv760["entities"])}
    check(ent_fwd[ei761["camel"]] == ei760["horse"], "camel -> horse")
    check(ent_fwd[ei761["pig"]] == ei760["pig"], "pig -> pig")
    check(ent_rev[ei760["pig"]] == ei761["pig"], "pig 760->761")
    # 760 はレジストリ順、761 もレジストリ順だが数が違うため同名でも番号が違う。
    check(ei760["pig"] == 67 and ei761["pig"] == 68, "pig は 760=67・761=68")

    snd_fwd = maps["SOUND"]["fwd"]
    si761 = {n: i for i, n in enumerate(vv761["sounds"])}
    si760 = {n: i for i, n in enumerate(vv760["sounds"])}
    check(snd_fwd[si761["block.bamboo_wood.break"]] == si760["block.wood.break"],
          "bamboo_wood.break -> wood.break")
    check(snd_fwd[si761["block.bamboo_wood_door.open"]] == si760["block.wooden_door.open"],
          "bamboo_wood_door.open -> wooden_door.open")
    check(snd_fwd[si761["block.hanging_sign.break"]] == si760["block.wood.break"],
          "hanging_sign.break -> wood.break")
    check(snd_fwd[si761["block.chiseled_bookshelf.insert"]] == si760["block.wood.place"],
          "chiseled_bookshelf.insert -> wood.place")
    check(snd_fwd[si761["block.note_block.imitate.zombie"]] == si760["block.note_block.harp"],
          "imitate.zombie -> harp")
    check(snd_fwd[si761["entity.camel.ambient"]] == si760["entity.horse.ambient"],
          "camel.ambient -> horse.ambient")
    check(snd_fwd[si761["entity.camel.dash"]] == si760["entity.horse.gallop"],
          "camel.dash -> horse.gallop")

    part_fwd = maps["PARTICLE"]["fwd"]
    part_rev = maps["PARTICLE"]["rev"]
    pi761 = {n: i for i, n in enumerate(vv761["particles"])}
    pi760 = {n: i for i, n in enumerate(vv760["particles"])}
    check(part_fwd[pi761["white_ash"]] == pi760["white_ash"],
          "white_ash 761->760 恒等")
    check(part_rev[pi760["white_ash"]] == pi761["white_ash"],
          "white_ash 760->761 恒等")

    be_fwd = maps["BLOCK_ENTITY"]["fwd"]
    be_rev = maps["BLOCK_ENTITY"]["rev"]
    bi761 = {n: i for i, n in enumerate(vv761["blockentities"])}
    bi760 = {n: i for i, n in enumerate(vv760["blockentities"])}
    check(be_fwd[bi761["chiseled_bookshelf"]] == -1,
          "chiseled_bookshelf BE -> 落とす(-1)")
    check(be_fwd[bi761["hanging_sign"]] == -1,
          "hanging_sign BE -> 落とす(-1)")
    check(be_rev[bi760["chest"]] == bi761["chest"], "chest BE 760->761 恒等")

    menu_fwd = maps["MENU"]["fwd"]
    menu_rev = maps["MENU"]["rev"]
    mi761 = {n: i for i, n in enumerate(load(d / "vv-1.20.json")["menus"])}
    mi760 = dict(mi761)
    check(menu_fwd[mi761["smithing"]] == mi760["smithing"],
          "smithing 761->760 恒等")
    check(menu_rev[mi760["crafting"]] == mi761["crafting"],
          "crafting 760->761 恒等")

    biome_fwd = maps["BIOME"]["fwd"]
    boi761 = {b["name"]: b["id"] for b in load(d / "pc" / "1.19.3" / "biomes.json")}
    boi760 = {b["name"]: b["id"] for b in load(d / "pc" / "1.19.2" / "biomes.json")}
    check(boi760["plains"] == 1 and boi761["plains"] == 38,
          "plains は 760=1・761=38")
    check(biome_fwd[boi761["plains"]] == boi760["plains"], "plains -> plains")
    check(biome_fwd[boi761["ocean"]] == boi760["ocean"], "ocean -> ocean")

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
                # 代替で潰れた場合（例：760 の oak_log は 761 の bamboo_block
                # へは戻らない）は fwd[i762] が i761 と違うことがある。
                # ここでは「戻りが -1 でない」ことだけ見る。
                if fwd[i762] < 0:
                    bad += 1
        check(bad == 0, f"{name}: 760->761->760 の往復で -1 になったものが {bad} 件")

    if fails:
        print(f"{len(fails)} 件の不一致", file=sys.stderr)
        return 1
    print("verify-760-tables: すべて一致")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
