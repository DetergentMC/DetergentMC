#!/usr/bin/env python3
"""762（1.19.4）↔763（1.20.1）のレジストリ対応表を生成する。

情報源（推測で埋めない。すべて名前で突き合わせる）：
  - ViaVersion Mappings（Mojang の data-generator レポート由来。
    リストの index がレジストリ ID）：
      https://raw.githubusercontent.com/ViaVersion/Mappings/main/mappings/mapping-1.19.4.json （762）
      https://raw.githubusercontent.com/ViaVersion/Mappings/main/mappings/mapping-1.20.json   （763）
  - PrismarineJS minecraft-data（バイオーム・ダメージ種別・
    代替ブロックの既定状態）：
      data/pc/1.19.4/biomes.json、data/pc/1.20/biomes.json
      data/pc/1.19.4/loginPacket.json（minecraft:damage_type の順番）、
      data/pc/1.20/loginPacket.json
      data/pc/1.19.4/blocks.json、data/pc/1.20/blocks.json（defaultState）

対応の決め方（優先順）：
  1. 名前がそのまま一致する → その ID。
  2. ブロック状態だけ：プロパティを1個だけ落とす／足すと一致する
     （763で decorated_pot に cracked が追加された）→ その ID。
  3. 改名だけ（pottery_shard_archer → archer_pottery_sherd、
     suspicious_sand → brushable_block（ブロックエンティティ）、
     item.brush.brushing → item.brush.brushing.generic（音）など）
     → 改名後の ID。
  4. 下の *_FALLBACK 表にある 1.20 の新要素 → 近い既存要素。
  5. それでも決まらないものがあれば「未対応」として一覧を出し、
     exit 1 で失敗する（黙って推測しない）。

使い方：
  python3 tools/gen-762-tables.py [--data-dir DIR] [--out FILE]
  --data-dir を省略すると /tmp/mcdata を使う。
  --out を省略すると RegistryTables762.java を標準出力に出す
  （--out FILE でファイルに書く）。--json FILE で対応表の JSON も出す。
"""

import argparse
import json
import sys
from collections import Counter
from pathlib import Path

DATA_DIR = Path("/tmp/mcdata")


def load(p):
    return json.loads(Path(p).read_text(encoding="utf-8"))


def parse_state(s):
    """'name[p=v,q=w]' -> ('name', {'p': 'v', 'q': 'w'})。プロパティ無しは {}。"""
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


def ranges_overrides(mapping, size, fallback):
    """mapping: list[int]（source id -> target id、-1 は未対応）から
    ranges（連続して delta 一定の区間）と overrides（それ以外）を作る。
    fallback 値の箇所は ranges にも overrides にも入れない
    （expand() が fallback で埋める）。"""
    ranges = []
    overrides = []
    i = 0
    while i < size:
        v = mapping[i]
        if v == fallback:
            i += 1
            continue
        # delta 一定の最長区間を探す。
        delta = v - i
        j = i
        while j + 1 < size and mapping[j + 1] != fallback \
                and mapping[j + 1] - (j + 1) == delta:
            j += 1
        if j > i:
            ranges.append([i, j, delta])
        else:
            overrides.append([i, v])
        i = j + 1
    return ranges, overrides


def java_table(name, ranges, overrides, entries, kind):
    out = [f"    static final int[][] {name}_RANGES = {{"]
    if ranges:
        for idx, r in enumerate(ranges):
            comma = "," if idx < len(ranges) - 1 else ""
            out.append(f"            {{{r[0]}, {r[1]}, {r[2]}}}{comma}")
    out.append("    };")
    out.append("")
    out.append(f"    static final int[][] {name}_OVERRIDES = {{")
    if overrides:
        for idx, o in enumerate(overrides):
            comma = "," if idx < len(overrides) - 1 else ""
            out.append(f"            {{{o[0]}, {o[1]}}}{comma}")
    out.append("    };")
    out.append(f"    // {name}: {kind} entries={entries} ranges={len(ranges)} "
               f"overrides={len(overrides)}")
    return "\n".join(out)


# ----------------------------------------------------------------------
# フォールバック表（763 にだけあるものを 762 の近い要素へ）
# ----------------------------------------------------------------------
# ブロック状態・ブロック：新ブロック → 762 にある近いブロック。
BLOCK_FALLBACK_763_TO_762 = {
    "suspicious_gravel": "gravel",
    "pitcher_crop": "wheat",
    "pitcher_plant": "peony",
    "sniffer_egg": "turtle_egg",
    "calibrated_sculk_sensor": "sculk_sensor",
}
# アイテム：新アイテム → 762 にある近いアイテム。
ITEM_FALLBACK_763_TO_762 = {
    "suspicious_gravel": "gravel",
    "pitcher_plant": "peony",
    "sniffer_egg": "turtle_egg",
    "calibrated_sculk_sensor": "sculk_sensor",
    "pitcher_pod": "wheat_seeds",
    "music_disc_relic": "music_disc_cat",
    "wayfinder_armor_trim_smithing_template": "sentry_armor_trim_smithing_template",
    "shaper_armor_trim_smithing_template": "sentry_armor_trim_smithing_template",
    "silence_armor_trim_smithing_template": "sentry_armor_trim_smithing_template",
    "raiser_armor_trim_smithing_template": "sentry_armor_trim_smithing_template",
    "host_armor_trim_smithing_template": "sentry_armor_trim_smithing_template",
}
# 1.20 で追加された陶片（16件）は 762 の陶片のうち archer へ。
for _sherd in ("angler", "blade", "brewer", "burn", "danger", "explorer",
               "friend", "heart", "heartbreak", "howl", "miner", "mourner",
               "plenty", "sheaf", "shelter", "snort"):
    ITEM_FALLBACK_763_TO_762[f"{_sherd}_pottery_sherd"] = "pottery_shard_archer"
# アイテムの改名（1.20 で pottery_shard_* → *_pottery_sherd）。
ITEM_RENAME_762_TO_763 = {
    "pottery_shard_archer": "archer_pottery_sherd",
    "pottery_shard_prize": "prize_pottery_sherd",
    "pottery_shard_arms_up": "arms_up_pottery_sherd",
    "pottery_shard_skull": "skull_pottery_sherd",
}
ITEM_RENAME_763_TO_762 = {v: k for k, v in ITEM_RENAME_762_TO_763.items()}
# 音の改名・フォールバック（763 → 762）。
SOUND_RENAME_763_TO_762 = {
    "item.brush.brushing.generic": "item.brush.brushing",
    "item.brush.brushing.sand": "item.brush.brushing",
    "item.brush.brushing.gravel": "item.brush.brushing",
    "item.brush.brushing.sand.complete": "item.brush.brush_sand_completed",
    "item.brush.brushing.gravel.complete": "item.brush.brush_sand_completed",
    "music.overworld.forest": "music.overworld.jungle_and_forest",
    "music.overworld.flower_forest": "music.overworld.jungle_and_forest",
    "music.overworld.desert": "music.overworld.jungle_and_forest",
    "music.overworld.badlands": "music.overworld.jungle_and_forest",
    "music.overworld.jungle": "music.overworld.jungle_and_forest",
    "music.overworld.sparse_jungle": "music.overworld.jungle_and_forest",
    "music.overworld.bamboo_jungle": "music.overworld.jungle_and_forest",
    "block.amethyst_block.resonate": "block.amethyst_block.chime",
    "music_disc.relic": "music_disc.cat",
    "block.sign.waxed_interact_fail": "block.stone.break",
    "block.suspicious_gravel.break": "block.gravel.break",
    "block.suspicious_gravel.step": "block.gravel.step",
    "block.suspicious_gravel.place": "block.gravel.place",
    "block.suspicious_gravel.hit": "block.gravel.hit",
    "block.suspicious_gravel.fall": "block.gravel.fall",
    "block.sniffer_egg.plop": "entity.turtle.lay_egg",
    "block.sniffer_egg.crack": "entity.turtle.egg_crack",
    "block.sniffer_egg.hatch": "entity.turtle.egg_hatch",
}
# 音の改名（762 → 763。763 にだけある音は 762 には無いので逆向きは改名だけ）。
SOUND_RENAME_762_TO_763 = {
    "item.brush.brushing": "item.brush.brushing.generic",
    "item.brush.brush_sand_completed": "item.brush.brushing.sand.complete",
    "music.overworld.jungle_and_forest": "music.overworld.jungle",
}
# パーティクル（763 → 762）。cherry_leaves は 762 では3種に分かれていた。
PARTICLE_FALLBACK_763_TO_762 = {
    "cherry_leaves": "falling_cherry_leaves",
    "egg_crack": None,  # 代替なし → 0（ambient_entity_effect）
}
PARTICLE_RENAME_762_TO_763 = {
    "dripping_cherry_leaves": "cherry_leaves",
    "falling_cherry_leaves": "cherry_leaves",
    "landing_cherry_leaves": "cherry_leaves",
}
# ブロックエンティティの改名・フォールバック。
BLOCK_ENTITY_RENAME_762_TO_763 = {"suspicious_sand": "brushable_block"}
BLOCK_ENTITY_RENAME_763_TO_762 = {"brushable_block": "suspicious_sand"}
BLOCK_ENTITY_FALLBACK_763_TO_762 = {"calibrated_sculk_sensor": "sculk_sensor"}
# メニューの改名（762 の legacy_smithing は 763 では smithing）。
MENU_RENAME_762_TO_763 = {"legacy_smithing": "smithing"}
# バイオームのフォールバック（cherry_grove は 762 には無い）。
BIOME_FALLBACK_763_TO_762 = {"cherry_grove": "meadow"}
# ダメージ種別のフォールバック（763 で追加された2件）。
DAMAGE_FALLBACK_763_TO_762 = {
    "minecraft:generic_kill": "minecraft:generic",
    "minecraft:outside_border": "minecraft:generic",
}


def build_block_state_maps(vv762, vv763, blocks762, blocks763):
    """ブロック状態の対応表（両方向）。"""
    states762 = vv762["blockstates"]
    states763 = vv763["blockstates"]
    idx762 = {s: i for i, s in enumerate(states762)}
    idx763 = {s: i for i, s in enumerate(states763)}
    def762 = {b["name"]: b["defaultState"] for b in blocks762}
    def763 = {b["name"]: b["defaultState"] for b in blocks763}

    def drop_one(state, target_idx):
        """プロパティを1個だけ落として target に一致するものを探す。"""
        name, props = parse_state(state)
        for drop in sorted(props):
            rest = {k: v for k, v in props.items() if k != drop}
            cand = fmt_state(name, rest)
            if cand in target_idx:
                return target_idx[cand]
        return None

    def add_one(state, by_name):
        """プロパティを1個だけ足して target に一致するものを探す。
        候補が複数あるときは bool の false を優先する。
        by_name: ブロック名 → [(props, id)] の索引。"""
        name, props = parse_state(state)
        cands = []
        for cprops, i in by_name.get(name, []):
            if set(cprops) > set(props) and len(cprops) == len(props) + 1 \
                    and all(cprops[k] == v for k, v in props.items()):
                extra = [k for k in cprops if k not in props][0]
                cands.append((0 if cprops[extra] == "false" else 1, i))
        if not cands:
            return None
        cands.sort()
        return cands[0][1]

    by_name_763 = {}
    for s, i in idx763.items():
        n, p = parse_state(s)
        by_name_763.setdefault(n, []).append((p, i))

    fwd = []  # 763 -> 762
    unmapped_fwd = []
    for s in states763:
        if s in idx762:
            fwd.append(idx762[s])
            continue
        v = drop_one(s, idx762)
        if v is not None:
            fwd.append(v)
            continue
        name, _ = parse_state(s)
        fb = BLOCK_FALLBACK_763_TO_762.get(name)
        if fb is not None and fb in def762:
            fwd.append(def762[fb])
            continue
        unmapped_fwd.append(s)
        fwd.append(-1)
    rev = []  # 762 -> 763
    unmapped_rev = []
    for s in states762:
        if s in idx763:
            rev.append(idx763[s])
            continue
        v = add_one(s, by_name_763)
        if v is not None:
            rev.append(v)
            continue
        # torchflower_crop[age=2]：763 には age=2 が無いため age=1 へ落とす。
        if s == "torchflower_crop[age=2]":
            rev.append(idx763["torchflower_crop[age=1]"])
            continue
        name, _ = parse_state(s)
        fb = BLOCK_FALLBACK_763_TO_762.get(name)
        if fb is not None and fb in def763:
            rev.append(def763[fb])
            continue
        unmapped_rev.append(s)
        rev.append(-1)
    return fwd, rev, unmapped_fwd, unmapped_rev


def build_name_map(list762, list763, rename_762_to_763=None,
                   fallback_763_to_762=None, fallback_id_762=None,
                   rename_763_to_762=None):
    """名前リストの対応表（両方向）。rename は改名表、fallback は
    763 にだけある要素の代替（762側の名前）。fallback_id_762 は
    代替なしの場合の 762 側の ID（None なら -1）。"""
    idx762 = {n: i for i, n in enumerate(list762)}
    idx763 = {n: i for i, n in enumerate(list763)}
    rename_762_to_763 = rename_762_to_763 or {}
    rename_763_to_762 = rename_763_to_762 or {}
    fallback_763_to_762 = fallback_763_to_762 or {}
    fwd = []
    unmapped_fwd = []
    for n in list763:
        if n in idx762:
            fwd.append(idx762[n])
        elif n in rename_763_to_762 and rename_763_to_762[n] in idx762:
            fwd.append(idx762[rename_763_to_762[n]])
        elif n in fallback_763_to_762:
            fb = fallback_763_to_762[n]
            if fb is None:
                fwd.append(fallback_id_762 if fallback_id_762 is not None else -1)
            elif fb in idx762:
                fwd.append(idx762[fb])
            else:
                unmapped_fwd.append(n)
                fwd.append(-1)
        else:
            unmapped_fwd.append(n)
            fwd.append(-1)
    rev = []
    unmapped_rev = []
    for n in list762:
        if n in idx763:
            rev.append(idx763[n])
        elif n in rename_762_to_763 and rename_762_to_763[n] in idx763:
            rev.append(idx763[rename_762_to_763[n]])
        else:
            unmapped_rev.append(n)
            rev.append(-1)
    return fwd, rev, unmapped_fwd, unmapped_rev


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--data-dir", type=Path, default=DATA_DIR)
    parser.add_argument("--out", type=Path, default=None)
    parser.add_argument("--json", type=Path, default=None)
    args = parser.parse_args()
    d = args.data_dir

    vv762 = load(d / "vv-1.19.4.json")
    vv763 = load(d / "vv-1.20.json")
    blocks762 = load(d / "pc" / "1.19.4" / "blocks.json")
    blocks763 = load(d / "pc" / "1.20" / "blocks.json")
    biomes762 = {b["name"]: b["id"] for b in load(d / "pc" / "1.19.4" / "biomes.json")}
    biomes763 = {b["name"]: b["id"] for b in load(d / "pc" / "1.20" / "biomes.json")}

    def damage_names(version):
        login = load(d / "pc" / version / "loginPacket.json")
        entries = login["dimensionCodec"]["value"]["minecraft:damage_type"] \
            ["value"]["value"]["value"]["value"]
        return [e["name"]["value"] for e in entries]

    damage762 = damage_names("1.19.4")
    damage763 = damage_names("1.20")

    problems = []

    def check(name, unmapped_fwd, unmapped_rev):
        if unmapped_fwd or unmapped_rev:
            problems.append((name, unmapped_fwd, unmapped_rev))

    bs_fwd, bs_rev, u, v = build_block_state_maps(vv762, vv763, blocks762, blocks763)
    check("blockstate", u, v)

    item_fwd, item_rev, u, v = build_name_map(
        vv762["items"], vv763["items"],
        rename_762_to_763=ITEM_RENAME_762_TO_763,
        rename_763_to_762=ITEM_RENAME_763_TO_762,
        fallback_763_to_762=ITEM_FALLBACK_763_TO_762,
        fallback_id_762=1)  # 石
    check("item", u, v)
    ent_fwd, ent_rev, u, v = build_name_map(vv762["entities"], vv763["entities"])
    check("entity", u, v)
    snd_fwd, snd_rev, u, v = build_name_map(
        vv762["sounds"], vv763["sounds"],
        rename_762_to_763=SOUND_RENAME_762_TO_763,
        rename_763_to_762=SOUND_RENAME_763_TO_762,
        fallback_id_762=None)
    # 音の代替なしは石の破壊音（762 の block.stone.break）へ。
    stone_break_762 = vv762["sounds"].index("block.stone.break")
    snd_fwd = [stone_break_762 if x < 0 else x for x in snd_fwd]
    check("sound", u, v)
    part_fwd, part_rev, u, v = build_name_map(
        vv762["particles"], vv763["particles"],
        rename_762_to_763=PARTICLE_RENAME_762_TO_763,
        fallback_763_to_762=PARTICLE_FALLBACK_763_TO_762,
        fallback_id_762=0)  # ambient_entity_effect
    check("particle", u, v)
    blk_fwd, blk_rev, u, v = build_name_map(
        vv762["blocks"], vv763["blocks"],
        fallback_763_to_762=BLOCK_FALLBACK_763_TO_762,
        fallback_id_762=1)  # 石
    check("block", u, v)
    be_fwd, be_rev, u, v = build_name_map(
        vv762["blockentities"], vv763["blockentities"],
        rename_762_to_763=BLOCK_ENTITY_RENAME_762_TO_763,
        rename_763_to_762=BLOCK_ENTITY_RENAME_763_TO_762,
        fallback_763_to_762=BLOCK_ENTITY_FALLBACK_763_TO_762,
        fallback_id_762=None)
    check("blockentity", u, v)
    menu_fwd, menu_rev, u, v = build_name_map(
        vv762["menus"], vv763["menus"],
        rename_762_to_763=MENU_RENAME_762_TO_763,
        fallback_id_762=None)
    check("menu", u, v)
    biome_fwd, biome_rev, u, v = build_name_map(
        [n for n, _ in sorted(biomes762.items(), key=lambda kv: kv[1])],
        [n for n, _ in sorted(biomes763.items(), key=lambda kv: kv[1])],
        fallback_763_to_762=BIOME_FALLBACK_763_TO_762,
        fallback_id_762=None)
    check("biome", u, v)
    dmg_fwd, dmg_rev, u, v = build_name_map(
        damage762, damage763,
        fallback_763_to_762=DAMAGE_FALLBACK_763_TO_762,
        fallback_id_762=None)
    check("damage", u, v)
    # 絵画・エンチャントは同一順序のはず。違えば失敗させる。
    for key in ("paintings", "enchantments"):
        if vv762[key] != vv763[key]:
            problems.append((key, ["order differs"], []))
    # エフェクトは minecraft-data で同一（1ベース・33件）。
    eff762 = {x["name"]: x["id"] for x in load(d / "pc" / "1.19.4" / "effects.json")}
    eff763 = {x["name"]: x["id"] for x in load(d / "pc" / "1.20" / "effects.json")}
    if eff762 != eff763:
        problems.append(("effects", ["order differs"], []))

    if problems:
        for name, uf, ur in problems:
            print(f"未対応 {name}: 763→762 {uf} / 762→763 {ur}", file=sys.stderr)
        return 1

    maps = {
        "BLOCK_STATE": (bs_fwd, bs_rev, len(vv763["blockstates"]), len(vv762["blockstates"])),
        "ITEM": (item_fwd, item_rev, len(vv763["items"]), len(vv762["items"])),
        "ENTITY_TYPE": (ent_fwd, ent_rev, len(vv763["entities"]), len(vv762["entities"])),
        "SOUND": (snd_fwd, snd_rev, len(vv763["sounds"]), len(vv762["sounds"])),
        "PARTICLE": (part_fwd, part_rev, len(vv763["particles"]), len(vv762["particles"])),
        "BLOCK": (blk_fwd, blk_rev, len(vv763["blocks"]), len(vv762["blocks"])),
        "BLOCK_ENTITY": (be_fwd, be_rev, len(vv763["blockentities"]), len(vv762["blockentities"])),
        "MENU": (menu_fwd, menu_rev, len(vv763["menus"]), len(vv762["menus"])),
        "BIOME": (biome_fwd, biome_rev, len(biomes763), len(biomes762)),
        "DAMAGE_TYPE": (dmg_fwd, dmg_rev, len(damage763), len(damage762)),
    }
    fallbacks_fwd = {
        "BLOCK_STATE": 1, "ITEM": 1, "ENTITY_TYPE": 72,
        "SOUND": stone_break_762, "PARTICLE": 0, "BLOCK": 1,
        "BLOCK_ENTITY": -1, "MENU": 11, "BIOME": biomes762["plains"],
        "DAMAGE_TYPE": 16,
    }
    stone_break_763 = vv763["sounds"].index("block.stone.break")
    fallbacks_rev = {
        "BLOCK_STATE": 1, "ITEM": 1, "ENTITY_TYPE": 72,
        "SOUND": stone_break_763, "PARTICLE": 0, "BLOCK": 1,
        "BLOCK_ENTITY": -1, "MENU": 11, "BIOME": biomes763["plains"],
        "DAMAGE_TYPE": 16,
    }
    # 762 側の代替先：石のブロック状態 1、石のアイテム 1、ブタ 72、
    # 石の破壊音、ambient パーティクル 0、石ブロック 1、作業台メニュー 11、
    # 平原バイオーム、generic ダメージ種別。
    # ブロックエンティティだけ代替なし（-1 = パケットごと落とす）。

    stats = {}
    for name, (fwd, rev, n_fwd, n_rev) in maps.items():
        fb = fallbacks_fwd[name]
        fr, fo = ranges_overrides(fwd, n_fwd, fb)
        rr, ro = ranges_overrides(rev, n_rev, fallbacks_rev[name])
        stats[name] = {
            "fwd_entries": n_fwd, "fwd_ranges": len(fr), "fwd_overrides": len(fo),
            "rev_entries": n_rev, "rev_ranges": len(rr), "rev_overrides": len(ro),
            "fwd_fallback_count": sum(1 for x in fwd if x == fb),
            "rev_fallback_count": sum(1 for x in rev if x == fallbacks_rev[name]),
        }
        print(f"{name}: 763→762 entries={n_fwd} ranges={len(fr)} overrides={len(fo)} "
              f"fallback={sum(1 for x in fwd if x == fb)} / "
              f"762→763 entries={n_rev} ranges={len(rr)} overrides={len(ro)} "
              f"fallback={sum(1 for x in rev if x == fallbacks_rev[name])}")

    if args.json:
        out = {name: {"fwd": fwd, "rev": rev}
               for name, (fwd, rev, _, _) in maps.items()}
        args.json.write_text(json.dumps(out, indent=1) + "\n", encoding="utf-8")
        print(f"JSON を {args.json} に書き出した。")

    header = """package io.detergentmc.protocol;

/**
 * 762（1.19.4）のレジストリ対応表（区間＋上書きの生の表）。
 *
 * <p>{@link RegistryRemapper} から分離した。理由はバイトコードの制限：
 * 表を RegistryRemapper に置くと静的初期化子（{@code <clinit>}）が
 * 64KB を超えてコンパイルできなくなる（"code too large"）ため。
 * このクラスの表は {@link RegistryRemapper} から static import で
 * 同じ名前のまま参照する。
 *
 * <p>表の内容と検証は {@code detergentmc-protocol/tools/} を参照：
 * 生成は {@code gen-762-tables.py}、検証は {@code verify-762-tables.py}。
 * 情報源は ViaVersion Mappings の mapping-1.19.4.json（762）と
 * mapping-1.20.json（763）で、名前で突き合わせた。ブロック状態は
 * プロパティを1個だけ落とす／足す一致も使う（decorated_pot の
 * cracked）。1.20 の新要素の代替は次の通り：
 * suspicious_gravel→gravel、pitcher_crop→wheat、pitcher_plant→peony、
 * sniffer_egg→turtle_egg、calibrated_sculk_sensor→sculk_sensor、
 * pitcher_pod→wheat_seeds、music_disc_relic→music_disc_cat、
 * 新トリム型5件→sentry のトリム型、新陶片16件→pottery_shard_archer、
 * cherry_grove→meadow、generic_kill／outside_border→generic。
 * 音は brush 系の改名と jungle_and_forest の分割、sniffer_egg の音は
 * カメの卵の音へ。パーティクルは cherry_leaves→falling_cherry_leaves、
 * egg_crack→ambient（0）。エンティティ・絵画・エンチャント・
 * エフェクトは 762 と 763 で同一順序のため恒等写像（表は作らない）。
 */
final class RegistryTables762 {

    private RegistryTables762() {
    }

"""
    parts = [header]
    for name, (fwd, rev, n_fwd, n_rev) in maps.items():
        fb = fallbacks_fwd[name]
        fr, fo = ranges_overrides(fwd, n_fwd, fb)
        rr, ro = ranges_overrides(rev, n_rev, fallbacks_rev[name])
        parts.append(java_table(f"{name}_762", fr, fo, n_fwd, "763->762"))
        parts.append("")
        parts.append(java_table(f"{name}_762_TO_763", rr, ro, n_rev, "762->763"))
        parts.append("")
    # 音の名前の代替表（763→762。Stop Sound 用。数値表の代替と一致させる）。
    snd_names = json.loads((Path(__file__).parent / "sound-names-763-to-762.json").read_text(encoding="utf-8"))
    lines = ["    /** 音の名前：763 → 762（Stop Sound 用）。数値の合成表と一致させる。 */",
             "    static final String[][] SOUND_NAME_FALLBACKS_763_TO_762 = {"]
    for src, dst in snd_names:
        lines.append(f'            {{"{src}", "{dst}"}},')
    lines.append("    };")
    parts.append("\n".join(lines))
    parts.append("")
    # 音の名前の代替表（762→763。Stop Sound 用。数値表の代替と一致させる）。
    snd_names_rev = json.loads((Path(__file__).parent / "sound-names-762-to-763.json").read_text(encoding="utf-8"))
    lines = ["    /** 音の名前：762 → 763（Stop Sound 用）。数値の合成表と一致させる。 */",
             "    static final String[][] SOUND_NAME_FALLBACKS_762_TO_763 = {"]
    for src, dst in snd_names_rev:
        lines.append(f'            {{"{src}", "{dst}"}},')
    lines.append("    };")
    parts.append("\n".join(lines))
    parts.append("")
    parts.append("}")
    java = "\n".join(parts)
    if args.out:
        args.out.write_text(java, encoding="utf-8")
        print(f"Java を {args.out} に書き出した。")
    else:
        print(java)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
