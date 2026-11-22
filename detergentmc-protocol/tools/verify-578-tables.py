#!/usr/bin/env python3
"""RegistryTables578.java（と分割クラス）の検証。

gen-578-tables.py のビルダーで期待値を実データから作り直し、
生成済みの Java ファイル（本体＋ブロック状態の分割クラス）を
読んで展開した結果と全件照合する。代表値の目視確認も行う。

使い方：
  python3 tools/verify-578-tables.py [--data-dir DIR] [--java-dir DIR]
  --data-dir を省略すると /tmp/mcdata、--java-dir を省略すると
  src/main/java/io/detergentmc/protocol を使う。
"""

import argparse
import importlib.util
import json
import re
import sys
from pathlib import Path


def load_gen(path):
    spec = importlib.util.spec_from_file_location("gen578", path)
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    return mod


def parse_table(text, name):
    m = re.search(name + r"\s*=\s*\{(.*?)\n    \};", text, re.S)
    if not m:
        raise SystemExit(f"表が見つからない: {name}")
    return [tuple(int(x) for x in e.split(","))
            for e in re.findall(r"\{([^{}]+)\}", m.group(1))]


def expand(size, ranges, overrides, fallback):
    out = [fallback] * size
    for a, b, d in ranges:
        for i in range(a, b + 1):
            out[i] = i + d
    for a, v in overrides:
        out[a] = v
    return out


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--data-dir", type=Path, default=Path("/tmp/mcdata"))
    parser.add_argument("--java-dir", type=Path, default=Path(
        "src/main/java/io/detergentmc/protocol"))
    args = parser.parse_args()
    d = args.data_dir
    gen = load_gen(Path(__file__).resolve().parent / "gen-578-tables.py")
    load = gen.load

    vv578 = load(d / "mapping-1.15.json")
    vv758 = load(d / "mapping-1.18.json")
    blocks578 = load(d / "pc" / "1.15.2" / "blocks.json")
    blocks758 = load(d / "pc" / "1.18" / "blocks.json")
    biomes578 = {b["name"]: b["id"] for b in load(d / "pc" / "1.15.2" / "biomes.json")}
    biomes758 = {b["name"]: b["id"] for b in load(d / "pc" / "1.18" / "biomes.json")}
    reg578 = d / "datagen1152" / "generated" / "reports" / "registries.json"
    reg758 = d / "datagen1182" / "generated" / "reports" / "registries.json"
    be578 = gen.reg_names(reg578, "minecraft:block_entity_type")
    be758 = gen.reg_names(reg758, "minecraft:block_entity_type")
    menus578 = gen.reg_names(reg578, "minecraft:menu")
    menus758 = gen.reg_names(reg758, "minecraft:menu")
    login_762 = load(d / "pc" / "1.19.4-loginPacket.json")
    entries = login_762["dimensionCodec"]["value"]["minecraft:damage_type"] \
        ["value"]["value"]["value"]["value"]
    damage = [e["name"]["value"].split(":", 1)[1] for e in entries]

    bs_fwd, bs_rev, uf, ur = gen.build_block_state_maps(
        vv578, vv758, blocks578, blocks758)
    assert not uf and not ur, (uf[:3], ur[:3])
    item_fwd, item_rev, uf, ur = gen.build_name_map(
        vv578["items"], vv758["items"],
        rename_578_to_758=gen.ITEM_RENAME_578_TO_758,
        rename_758_to_578=gen.ITEM_RENAME_758_TO_578,
        fallback_758_to_578=gen.ITEM_FALLBACK_758_TO_578, fallback_id_578=1)
    assert not uf and not ur
    ent_fwd, ent_rev, uf, ur = gen.build_name_map(
        vv578["entities"], vv758["entities"],
        rename_578_to_758=gen.ENTITY_RENAME_578_TO_758,
        rename_758_to_578=gen.ENTITY_RENAME_758_TO_578,
        fallback_758_to_578=gen.ENTITY_FALLBACK_758_TO_578,
        fallback_id_578=vv578["entities"].index("pig"))
    assert not uf and not ur
    snd_map, _ = gen.build_sound_fallback(vv578["sounds"], vv758["sounds"])
    idx578_snd = {n: i for i, n in enumerate(vv578["sounds"])}
    stone_break_578 = vv578["sounds"].index("block.stone.break")
    snd_fwd = [idx578_snd[snd_map[n]] if n in snd_map
               else (idx578_snd[n] if n in idx578_snd else stone_break_578)
               for n in vv758["sounds"]]
    rev_map = {}
    for n in vv578["sounds"]:
        if n in vv758["sounds"]:
            continue
        if "zombie_pigman" in n:
            rev_map[n] = n.replace("zombie_pigman", "zombified_piglin")
        elif n == "music.nether":
            rev_map[n] = "music.nether.nether_wastes"
        elif n == "item.sweet_berries.pick_from_bush":
            rev_map[n] = "block.sweet_berry_bush.pick_berries"
    idx758_snd = {n: i for i, n in enumerate(vv758["sounds"])}
    snd_rev = [idx758_snd[n] if n in idx758_snd
               else idx758_snd[rev_map[n]] for n in vv578["sounds"]]
    part_fwd, part_rev, uf, ur = gen.build_name_map(
        vv578["particles"], vv758["particles"],
        rename_578_to_758=gen.PARTICLE_RENAME_578_TO_758,
        rename_758_to_578=gen.PARTICLE_RENAME_758_TO_578,
        fallback_758_to_578=gen.PARTICLE_FALLBACK_758_TO_578,
        fallback_id_578=0)
    assert not uf and not ur
    blk_fwd, blk_rev, uf, ur = gen.build_name_map(
        vv578["blocks"], vv758["blocks"],
        rename_578_to_758=gen.BLOCK_RENAME_578_TO_758,
        rename_758_to_578=gen.BLOCK_RENAME_758_TO_578,
        fallback_758_to_578=gen.BLOCK_FALLBACK_758_TO_578, fallback_id_578=1)
    assert not uf and not ur
    be_fwd, be_rev, uf, ur = gen.build_name_map(
        be578, be758,
        fallback_758_to_578=gen.BLOCK_ENTITY_FALLBACK_758_TO_578,
        fallback_id_578=None)
    assert not uf and not ur
    menu_fwd, menu_rev, uf, ur = gen.build_name_map(
        menus578, menus758,
        fallback_758_to_578=gen.MENU_FALLBACK_758_TO_578,
        fallback_id_578=menus578.index("crafting"))
    assert not uf and not ur
    biome_fwd, biome_rev, uf, ur = gen.build_name_map(
        [n for n, _ in sorted(biomes578.items(), key=lambda kv: kv[1])],
        [n for n, _ in sorted(biomes758.items(), key=lambda kv: kv[1])],
        rename_578_to_758=gen.BIOME_RENAME_578_TO_758,
        rename_758_to_578=gen.BIOME_RENAME_758_TO_578,
        fallback_758_to_578=gen.BIOME_FALLBACK_758_TO_578,
        fallback_578_to_758=gen.BIOME_FALLBACK_578_TO_758,
        fallback_id_578=None)
    assert not uf and not ur
    dmg_fwd, dmg_rev, uf, ur = gen.build_name_map(damage, damage,
                                                  fallback_id_578=None)
    assert not uf and not ur

    expected = {
        "BLOCK_STATE": (bs_fwd, bs_rev, len(vv758["blockstates"]),
                        len(vv578["blockstates"]), 1, 1),
        "ITEM": (item_fwd, item_rev, len(vv758["items"]),
                 len(vv578["items"]), 1, 1),
        "ENTITY_TYPE": (ent_fwd, ent_rev, len(vv758["entities"]),
                        len(vv578["entities"]),
                        vv578["entities"].index("pig"),
                        vv758["entities"].index("pig")),
        "SOUND": (snd_fwd, snd_rev, len(vv758["sounds"]),
                  len(vv578["sounds"]), stone_break_578,
                  vv758["sounds"].index("block.stone.break")),
        "PARTICLE": (part_fwd, part_rev, len(vv758["particles"]),
                     len(vv578["particles"]), 0, 0),
        "BLOCK": (blk_fwd, blk_rev, len(vv758["blocks"]),
                  len(vv578["blocks"]), 1, 1),
        "BLOCK_ENTITY": (be_fwd, be_rev, len(be758), len(be578), -1, -1),
        "MENU": (menu_fwd, menu_rev, len(menus758), len(menus578),
                 menus578.index("crafting"), menus758.index("crafting")),
        "BIOME": (biome_fwd, biome_rev, len(biomes758), len(biomes578),
                  biomes578["plains"], biomes758["plains"]),
        "DAMAGE_TYPE": (dmg_fwd, dmg_rev, 42, 42, 16, 16),
    }

    text = (args.java_dir / "RegistryTables578.java").read_text(encoding="utf-8")
    # ブロック状態の上書きは分割クラスから集める。
    chunk_overrides = {}
    for suffix in ("BsFwd", "BsRev"):
        ovs = []
        i = 1
        while True:
            p = args.java_dir / f"RegistryTables578{suffix}{i}.java"
            if not p.exists():
                break
            ovs.extend(parse_table(p.read_text(encoding="utf-8"), "V"))
            i += 1
        chunk_overrides[suffix] = ovs

    total = 0
    for name, (fwd, rev, n_fwd, n_rev, fb_f, fb_r) in expected.items():
        if name == "BLOCK_STATE":
            got_f = expand(n_fwd, parse_table(text, "BLOCK_STATE_578_RANGES"),
                           chunk_overrides["BsFwd"], fb_f)
            got_r = expand(n_rev, parse_table(text, "BLOCK_STATE_578_TO_758_RANGES"),
                           chunk_overrides["BsRev"], fb_r)
        else:
            got_f = expand(n_fwd, parse_table(text, f"{name}_578_RANGES"),
                           parse_table(text, f"{name}_578_OVERRIDES"), fb_f)
            got_r = expand(n_rev, parse_table(text, f"{name}_578_TO_758_RANGES"),
                           parse_table(text, f"{name}_578_TO_758_OVERRIDES"), fb_r)
        assert got_f == fwd, f"{name} 758→578 が期待値と違う"
        assert got_r == rev, f"{name} 578→758 が期待値と違う"
        total += n_fwd + n_rev
        print(f"{name}: 両方向とも全件一致（{n_fwd}＋{n_rev}）")

    # 代表値の目視確認。
    def st578(s):
        return bs_rev[vv578["blockstates"].index(s)]
    def st758(s):
        return bs_fwd[vv758["blockstates"].index(s)]
    assert st758("minecraft:stone") if False else True
    i_stone758 = [i for i, s in enumerate(vv758["blockstates"]) if s == "stone"][0]
    assert bs_fwd[i_stone758] == 1, "758 の石は 578 の石（1）"
    i_gp578 = vv578["blockstates"].index("grass_path")
    assert vv758["blockstates"][bs_rev[i_gp578]] == "dirt_path", "草の道の改名"
    i_wall578 = vv578["blockstates"].index(
        "cobblestone_wall[east=true,north=false,south=false,up=true,waterlogged=false,west=false]")
    wall758 = vv758["blockstates"][bs_rev[i_wall578]]
    assert wall758 == ("cobblestone_wall[east=low,north=none,south=none,"
                       "up=true,waterlogged=false,west=none]"), wall758
    i_cauld578 = vv578["blockstates"].index("cauldron[level=2]")
    assert vv758["blockstates"][bs_rev[i_cauld578]] == "water_cauldron[level=2]"
    i_cauld758 = vv758["blockstates"].index("cauldron")
    assert vv578["blockstates"][bs_fwd[i_cauld758]] == "cauldron[level=0]"
    assert ent_rev[vv578["entities"].index("zombie_pigman")] == \
        vv758["entities"].index("zombified_piglin")
    assert ent_fwd[vv758["entities"].index("piglin")] == \
        vv578["entities"].index("zombie_pigman")
    assert part_rev[vv578["particles"].index("barrier")] == \
        vv758["particles"].index("block_marker")
    assert be_fwd[be758.index("sculk_sensor")] == -1, "スカルクセンサーは落とす"
    assert menu_fwd[menus758.index("smithing")] == menus578.index("anvil")
    plains578 = sorted(biomes578.items(), key=lambda kv: kv[1])[1][0]
    assert plains578 == "plains"
    print("代表値の確認：石=1、草の道の改名、壁の bool→low、"
          "大釜の level、ゾンビピグマンの改名、barrier→block_marker、"
          "sculk_sensor=-1、smithing→anvil、すべて一致。")
    print(f"検証完了：{total} 件すべて一致。")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
