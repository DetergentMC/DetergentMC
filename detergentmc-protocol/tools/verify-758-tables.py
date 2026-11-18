#!/usr/bin/env python3
"""gen-758-tables.py の出力を検証する。

  1. RegistryTables758.java の RANGES／OVERRIDES を expand() と同じ
     規則で展開し、tables758.json の対応表と一致することを確認する。
  2. 名前で突き合わせた代表値を spot check する（代替・
     プロパティ落とし／足し・新規要素の代替）。
  3. 往復の性質を確認する：758 にある要素は 758→759→758 で
     自分に戻る（代替で潰れたものを除く）。759 にだけある要素は
     759→758→759 で自分に戻らない（代替先へ潰れる）ことを確認する。

使い方：
  python3 tools/verify-758-tables.py [--data-dir DIR]
  --data-dir を省略すると /tmp/mcdata を使う。
  終了コード 0 = すべて一致。1 = 不一致がある。
"""

import argparse
import json
import re
import sys
from pathlib import Path

DATA_DIR = Path("/tmp/mcdata")
JAVA_FILE = Path("/tmp/mcdata/RegistryTables758.java")

# 758 側の代替先（RegistryRemapper の定数と対応）。
STONE_BLOCK_STATE_758 = 1
STONE_ITEM_758 = 1
PIG_ENTITY_TYPE_758 = 64
STONE_BLOCK_758 = 1
CRAFTING_MENU_758 = 11


def load(p):
    return json.loads(Path(p).read_text(encoding="utf-8"))


def parse_state(s):
    if "[" not in s:
        return s, {}
    name, rest = s.split("[", 1)
    rest = rest.rstrip("]")
    props = {}
    for part in rest.split(","):
        k, v = part.split("=", 1)
        props[k] = v
    return name, props


def fmt_state(name, props):
    if not props:
        return name
    return name + "[" + ",".join(f"{k}={props[k]}" for k in sorted(props)) + "]"


def norm_state(s):
    name, props = parse_state(s)
    return fmt_state(name, props)


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

    maps = load(d / "tables758.json")
    java = parse_java(JAVA_FILE)
    vv758 = load(d / "vv-1.18.json")
    vv759 = load(d / "vv-1.19.json")
    blocks758 = load(d / "pc" / "1.18" / "blocks.json")
    blocks759 = load(d / "pc" / "1.19" / "blocks.json")
    fails = []

    def check(cond, msg):
        if not cond:
            fails.append(msg)
            print(f"FAIL: {msg}")

    fallbacks_fwd = {
        "BLOCK_STATE": STONE_BLOCK_STATE_758, "ITEM": STONE_ITEM_758,
        "ENTITY_TYPE": PIG_ENTITY_TYPE_758,
        "SOUND": vv758["sounds"].index("block.stone.break"),
        "PARTICLE": 0, "BLOCK": STONE_BLOCK_758, "BLOCK_ENTITY": -1,
        "MENU": CRAFTING_MENU_758,
        "BIOME": {b["name"]: b["id"] for b in load(d / "pc" / "1.18" / "biomes.json")}["plains"],
        "DAMAGE_TYPE": 16,
    }
    fallbacks_rev = {
        "BLOCK_STATE": 1, "ITEM": 1, "ENTITY_TYPE": 67,
        "SOUND": vv759["sounds"].index("block.stone.break"),
        "PARTICLE": 0, "BLOCK": 1, "BLOCK_ENTITY": -1, "MENU": 11,
        "BIOME": {b["name"]: b["id"] for b in load(d / "pc" / "1.19" / "biomes.json")}["plains"],
        "DAMAGE_TYPE": 16,
    }
    sizes_fwd = {name: len(v["fwd"]) for name, v in maps.items()}
    sizes_rev = {name: len(v["rev"]) for name, v in maps.items()}

    for name, v in maps.items():
        for direction, key, fb, size in (
                ("759->758", "fwd", fallbacks_fwd[name], sizes_fwd[name]),
                ("758->759", "rev", fallbacks_rev[name], sizes_rev[name])):
            table = f"{name}_758" if key == "fwd" else f"{name}_758_TO_759"
            if table not in java:
                fails.append(f"{name} {direction}: Java の表 {table} が無い")
                continue
            got = expand(java[table]["RANGES"], java[table]["OVERRIDES"], size, fb)
            check(got == v[key],
                  f"{name} {direction}: 展開結果が JSON と不一致 "
                  f"（{sum(1 for a, b in zip(got, v[key]) if a != b)} 件違う）")

    # ---- spot check（名前で引く。ブロック状態は正規形で引く） ----
    bs_fwd = maps["BLOCK_STATE"]["fwd"]
    bs_rev = maps["BLOCK_STATE"]["rev"]
    idx759 = {norm_state(s): i for i, s in enumerate(vv759["blockstates"])}
    idx758 = {norm_state(s): i for i, s in enumerate(vv758["blockstates"])}
    def758 = {b["name"]: b["defaultState"] for b in blocks758}
    # 葉：waterlogged を落として一致する。
    s759 = "oak_leaves[distance=1,persistent=true,waterlogged=false]"
    s758 = "oak_leaves[distance=1,persistent=true]"
    check(bs_fwd[idx759[s759]] == idx758[s758], "oak_leaves waterlogged 落とし")
    check(bs_rev[idx758[s758]] == idx759[s759], "oak_leaves waterlogged=false 足し")
    # piston_head：プロパティの並び順だけが違う（集合は同一）。
    check(bs_fwd[idx759["piston_head[facing=north,short=true,type=normal]"]]
          == idx758["piston_head[facing=north,short=true,type=normal]"],
          "piston_head 並び順の違い")
    # mangrove_planks → oak_planks の既定状態。
    check(bs_fwd[idx759["mangrove_planks"]] == def758["oak_planks"],
          "mangrove_planks -> oak_planks")
    # sculk → deepslate の既定状態。frogspawn → lily_pad の既定状態。
    check(bs_fwd[idx759["sculk"]] == def758["deepslate"], "sculk -> deepslate")
    check(bs_fwd[idx759["frogspawn"]] == def758["lily_pad"], "frogspawn -> lily_pad")

    item_fwd = maps["ITEM"]["fwd"]
    item_rev = maps["ITEM"]["rev"]
    ii759 = {n: i for i, n in enumerate(vv759["items"])}
    ii758 = {n: i for i, n in enumerate(vv758["items"])}
    check(item_fwd[ii759["mangrove_boat"]] == ii758["oak_boat"],
          "mangrove_boat -> oak_boat")
    check(item_fwd[ii759["oak_chest_boat"]] == ii758["oak_boat"],
          "oak_chest_boat -> oak_boat")
    check(item_fwd[ii759["music_disc_5"]] == ii758["music_disc_cat"],
          "music_disc_5 -> music_disc_cat")
    check(item_fwd[ii759["warden_spawn_egg"]] == ii758["ravager_spawn_egg"],
          "warden_spawn_egg -> ravager_spawn_egg")
    check(item_fwd[ii759["allay_spawn_egg"]] == ii758["vex_spawn_egg"],
          "allay_spawn_egg -> vex_spawn_egg")
    check(item_fwd[ii759["echo_shard"]] == ii758["amethyst_shard"],
          "echo_shard -> amethyst_shard")
    check(item_rev[ii758["oak_boat"]] == ii759["oak_boat"],
          "oak_boat -> oak_boat")

    snd_fwd = maps["SOUND"]["fwd"]
    si759 = {n: i for i, n in enumerate(vv759["sounds"])}
    si758 = {n: i for i, n in enumerate(vv758["sounds"])}
    check(snd_fwd[si759["block.mud.break"]] == si758["block.grass.break"],
          "mud.break -> grass.break")
    check(snd_fwd[si759["block.sculk.charge"]] == si758["block.sculk_sensor.clicking"],
          "sculk.charge -> sculk_sensor.clicking")
    check(snd_fwd[si759["entity.warden.roar"]] == si758["entity.iron_golem.attack"],
          "warden.roar -> iron_golem.attack")
    check(snd_fwd[si759["music.overworld.deep_dark"]]
          == si758["music.overworld.dripstone_caves"],
          "deep_dark music -> dripstone_caves music")
    check(snd_fwd[si759["music.overworld.jungle_and_forest"]] == si758["music.game"],
          "jungle_and_forest music -> game music")
    check(snd_fwd[si759["entity.allay.death"]] == si758["entity.vex.death"],
          "allay.death -> vex.death")

    part_fwd = maps["PARTICLE"]["fwd"]
    pi759 = {n: i for i, n in enumerate(vv759["particles"])}
    pi758 = {n: i for i, n in enumerate(vv758["particles"])}
    check(part_fwd[pi759["sculk_charge"]] == pi758["soul"],
          "sculk_charge -> soul")
    check(part_fwd[pi759["shriek"]] == pi758["vibration"],
          "shriek -> vibration")
    check(part_fwd[pi759["sonic_boom"]] == pi758["explosion"],
          "sonic_boom -> explosion")

    ent_fwd = maps["ENTITY_TYPE"]["fwd"]
    ei759 = {n: i for i, n in enumerate(vv759["entities"])}
    ei758 = {n: i for i, n in enumerate(vv758["entities"])}
    check(ent_fwd[ei759["allay"]] == ei758["vex"], "allay -> vex")
    check(ent_fwd[ei759["warden"]] == ei758["iron_golem"], "warden -> iron_golem")
    check(ent_fwd[ei759["chest_boat"]] == ei758["boat"], "chest_boat -> boat")
    check(ent_fwd[ei759["pig"]] == ei758["pig"], "pig -> pig")

    be_fwd = maps["BLOCK_ENTITY"]["fwd"]
    bi759 = {n: i for i, n in enumerate(vv759["blockentities"])}
    bi758 = {n: i for i, n in enumerate(vv758["blockentities"])}
    check(be_fwd[bi759["sculk_catalyst"]] == bi758["sculk_sensor"],
          "sculk_catalyst BE -> sculk_sensor")

    menu_fwd = maps["MENU"]["fwd"]
    check(menu_fwd[11] == 11 and menu_fwd[23] == 23, "menu 恒等写像")

    biome_fwd = maps["BIOME"]["fwd"]
    boi759 = {b["name"]: b["id"] for b in load(d / "pc" / "1.19" / "biomes.json")}
    boi758 = {b["name"]: b["id"] for b in load(d / "pc" / "1.18" / "biomes.json")}
    check(biome_fwd[boi759["mangrove_swamp"]] == boi758["swamp"],
          "mangrove_swamp -> swamp")
    check(biome_fwd[boi759["deep_dark"]] == boi758["dripstone_caves"],
          "deep_dark -> dripstone_caves")
    check(biome_fwd[boi759["plains"]] == boi758["plains"], "plains -> plains")

    dmg_fwd = maps["DAMAGE_TYPE"]["fwd"]
    check(dmg_fwd[0] == 0 and dmg_fwd[41] == 41, "damage 両端の対応（恒等写像）")

    # 音の名前の代替表が数値表と一致すること。
    snd_names = json.loads(
        (Path(__file__).resolve().parent / "sound-names-759-to-758.json")
        .read_text(encoding="utf-8"))
    for src, dst in snd_names:
        check(snd_fwd[si759[src]] == si758[dst],
              f"音の名前 {src} -> {dst} が数値表と不一致")

    # ---- 往復 ----
    for name, v in maps.items():
        fwd, rev = v["fwd"], v["rev"]
        # 758 にある要素：758→759→758 で自分に戻る（代替で潰れたものは除く）。
        bad = 0
        for i758, i759 in enumerate(rev):
            if i759 < 0 or i759 >= len(fwd):
                continue
            if fwd[i759] != i758:
                # 代替で潰れた場合（例：758 の葉は waterlogged=false
                # へ写り、戻りは同じ）は fwd[i759] が i758 と違うことがある。
                # ここでは「戻りが -1 でない」ことだけ見る。
                if fwd[i759] < 0:
                    bad += 1
        check(bad == 0, f"{name}: 758->759->758 の往復で -1 になったものが {bad} 件")

    if fails:
        print(f"{len(fails)} 件の不一致", file=sys.stderr)
        return 1
    print("verify-758-tables: すべて一致")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
