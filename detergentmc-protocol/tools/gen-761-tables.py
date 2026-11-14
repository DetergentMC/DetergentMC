#!/usr/bin/env python3
"""761（1.19.3）↔762（1.19.4）のレジストリ対応表を生成する。

情報源（推測で埋めない。すべて名前で突き合わせる）：
  - ViaVersion Mappings（Mojang の data-generator レポート由来。
    リストの index がレジストリ ID）：
      https://raw.githubusercontent.com/ViaVersion/Mappings/main/mappings/mapping-1.19.3.json （761）
      https://raw.githubusercontent.com/ViaVersion/Mappings/main/mappings/mapping-1.19.4.json   （762）
  - PrismarineJS minecraft-data（バイオーム・ダメージ種別・
    代替ブロックの既定状態）：
      data/pc/1.19.3/biomes.json、data/pc/1.19.4/biomes.json
      data/pc/1.19.4/loginPacket.json（minecraft:damage_type の順番。
      1.19.3 の loginPacket.json は minecraft-data に無いため、ダメージ
      種別は 762 と同一順序と仮定し、実バニラ 1.19.3 の Registry Data
      取得で検証する）
      data/pc/1.19.3/blocks.json、data/pc/1.19.4/blocks.json（defaultState）

対応の決め方（優先順）：
  1. 名前がそのまま一致する → その ID。
  2. ブロック状態だけ：プロパティを1個だけ落とす／足すと一致する
     （762で decorated_pot に cracked が追加された）→ その ID。
  3. 改名だけ（smithing → legacy_smithing（メニュー）など）
     → 改名後の ID。761 と 762 で名前が変わった音・アイテムは無い。
  4. 下の *_FALLBACK 表にある 1.19.4 の新要素 → 近い既存要素。
  5. それでも決まらないものがあれば「未対応」として一覧を出し、
     exit 1 で失敗する（黙って推測しない）。

使い方：
  python3 tools/gen-761-tables.py [--data-dir DIR] [--out FILE]
  --data-dir を省略すると /tmp/mcdata を使う。
  --out を省略すると RegistryTables761.java を標準出力に出す
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
# フォールバック表（762 にだけあるものを 761 の近い要素へ）
# ----------------------------------------------------------------------
# ブロック状態・ブロック：762 の新ブロック → 761 にある近いブロック。
# サクラ系はオーク系へ。suspicious_sand は砂へ、torchflower 系は
# タンポポ／小麦へ、pink_petals はボタンへ、decorated_pot は植木鉢へ。
BLOCK_FALLBACK_762_TO_761 = {
    "cherry_planks": "oak_planks",
    "cherry_sapling": "oak_sapling",
    "suspicious_sand": "sand",
    "cherry_log": "oak_log",
    "stripped_cherry_log": "stripped_oak_log",
    "cherry_wood": "oak_wood",
    "stripped_cherry_wood": "stripped_oak_wood",
    "cherry_leaves": "oak_leaves",
    "torchflower": "dandelion",
    "cherry_sign": "oak_sign",
    "cherry_wall_sign": "oak_wall_sign",
    "cherry_hanging_sign": "oak_hanging_sign",
    "cherry_wall_hanging_sign": "oak_wall_hanging_sign",
    "cherry_pressure_plate": "oak_pressure_plate",
    "cherry_trapdoor": "oak_trapdoor",
    "potted_torchflower": "potted_dandelion",
    "potted_cherry_sapling": "potted_oak_sapling",
    "cherry_button": "oak_button",
    "cherry_stairs": "oak_stairs",
    "cherry_slab": "oak_slab",
    "cherry_fence_gate": "oak_fence_gate",
    "cherry_fence": "oak_fence",
    "cherry_door": "oak_door",
    "torchflower_crop": "wheat",
    "pink_petals": "peony",
    "decorated_pot": "flower_pot",
}
# アイテム：762 の新アイテム → 761 にある近いアイテム。
# 鍛冶型テンプレート 11 件は素材へ（netherite_upgrade はネザライト
# インゴット、トリム型 10 件は鉄インゴット）。陶片 4 件はレンガへ。
ITEM_FALLBACK_762_TO_761 = {
    "cherry_planks": "oak_planks",
    "cherry_sapling": "oak_sapling",
    "suspicious_sand": "sand",
    "cherry_log": "oak_log",
    "stripped_cherry_log": "stripped_oak_log",
    "stripped_cherry_wood": "stripped_oak_wood",
    "cherry_wood": "oak_wood",
    "cherry_leaves": "oak_leaves",
    "torchflower": "dandelion",
    "pink_petals": "peony",
    "cherry_slab": "oak_slab",
    "decorated_pot": "flower_pot",
    "cherry_fence": "oak_fence",
    "cherry_stairs": "oak_stairs",
    "cherry_button": "oak_button",
    "cherry_pressure_plate": "oak_pressure_plate",
    "cherry_door": "oak_door",
    "cherry_trapdoor": "oak_trapdoor",
    "cherry_fence_gate": "oak_fence_gate",
    "cherry_boat": "oak_boat",
    "cherry_chest_boat": "oak_chest_boat",
    "cherry_sign": "oak_sign",
    "cherry_hanging_sign": "oak_hanging_sign",
    "sniffer_spawn_egg": "turtle_spawn_egg",
    "torchflower_seeds": "wheat_seeds",
    "brush": "stick",
    "netherite_upgrade_smithing_template": "netherite_ingot",
    "sentry_armor_trim_smithing_template": "iron_ingot",
    "dune_armor_trim_smithing_template": "iron_ingot",
    "coast_armor_trim_smithing_template": "iron_ingot",
    "wild_armor_trim_smithing_template": "iron_ingot",
    "ward_armor_trim_smithing_template": "iron_ingot",
    "eye_armor_trim_smithing_template": "iron_ingot",
    "vex_armor_trim_smithing_template": "iron_ingot",
    "tide_armor_trim_smithing_template": "iron_ingot",
    "snout_armor_trim_smithing_template": "iron_ingot",
    "rib_armor_trim_smithing_template": "iron_ingot",
    "spire_armor_trim_smithing_template": "iron_ingot",
    "pottery_shard_archer": "brick",
    "pottery_shard_prize": "brick",
    "pottery_shard_arms_up": "brick",
    "pottery_shard_skull": "brick",
}
# アイテムの改名：761 と 762 で名前が変わったものは無い（761 の音・
# アイテムはすべて 762 に同名で存在する）。空のまま置く。
ITEM_RENAME_761_TO_762 = {}
ITEM_RENAME_762_TO_761 = {}
# 音のフォールバック（762 → 761）。762 にだけある 62 件すべて。
# サクラの木材音は汎用の木の音へ、サクラの苗木・葉・花びらは草の音へ、
# サクラの吊り看板は吊り看板の音へ、ドア類は木製ドア類の音へ、
# decorated_pot は石の音へ（shatter だけガラスの音へ）、
# suspicious_sand は砂の音へ、cherry_grove の音楽は jungle_and_forest へ、
# intentionally_empty は石の破壊音へ、ブラシは砂の音へ、
# スニッファーはブタの音へ（eat だけ generic.eat、drop_seed だけ鶏の卵）。
SOUND_FALLBACK_762_TO_761 = {
    "item.brush.brushing": "block.sand.break",
    "item.brush.brush_sand_completed": "block.sand.break",
    "block.cherry_wood.break": "block.wood.break",
    "block.cherry_wood.fall": "block.wood.fall",
    "block.cherry_wood.hit": "block.wood.hit",
    "block.cherry_wood.place": "block.wood.place",
    "block.cherry_wood.step": "block.wood.step",
    "block.cherry_sapling.break": "block.grass.break",
    "block.cherry_sapling.fall": "block.grass.fall",
    "block.cherry_sapling.hit": "block.grass.hit",
    "block.cherry_sapling.place": "block.grass.place",
    "block.cherry_sapling.step": "block.grass.step",
    "block.cherry_leaves.break": "block.grass.break",
    "block.cherry_leaves.fall": "block.grass.fall",
    "block.cherry_leaves.hit": "block.grass.hit",
    "block.cherry_leaves.place": "block.grass.place",
    "block.cherry_leaves.step": "block.grass.step",
    "block.cherry_wood_hanging_sign.step": "block.hanging_sign.step",
    "block.cherry_wood_hanging_sign.break": "block.hanging_sign.break",
    "block.cherry_wood_hanging_sign.fall": "block.hanging_sign.fall",
    "block.cherry_wood_hanging_sign.hit": "block.hanging_sign.hit",
    "block.cherry_wood_hanging_sign.place": "block.hanging_sign.place",
    "block.cherry_wood_door.close": "block.wooden_door.close",
    "block.cherry_wood_door.open": "block.wooden_door.open",
    "block.cherry_wood_trapdoor.close": "block.wooden_trapdoor.close",
    "block.cherry_wood_trapdoor.open": "block.wooden_trapdoor.open",
    "block.cherry_wood_button.click_off": "block.wooden_button.click_off",
    "block.cherry_wood_button.click_on": "block.wooden_button.click_on",
    "block.cherry_wood_pressure_plate.click_off": "block.wooden_pressure_plate.click_off",
    "block.cherry_wood_pressure_plate.click_on": "block.wooden_pressure_plate.click_on",
    "block.cherry_wood_fence_gate.close": "block.fence_gate.close",
    "block.cherry_wood_fence_gate.open": "block.fence_gate.open",
    "block.decorated_pot.break": "block.stone.break",
    "block.decorated_pot.fall": "block.stone.fall",
    "block.decorated_pot.hit": "block.stone.hit",
    "block.decorated_pot.step": "block.stone.step",
    "block.decorated_pot.place": "block.stone.place",
    "block.decorated_pot.shatter": "block.glass.break",
    "block.suspicious_sand.break": "block.sand.break",
    "block.suspicious_sand.step": "block.sand.step",
    "block.suspicious_sand.place": "block.sand.place",
    "block.suspicious_sand.hit": "block.sand.hit",
    "block.suspicious_sand.fall": "block.sand.fall",
    "block.pink_petals.break": "block.grass.break",
    "block.pink_petals.fall": "block.grass.fall",
    "block.pink_petals.hit": "block.grass.hit",
    "block.pink_petals.place": "block.grass.place",
    "block.pink_petals.step": "block.grass.step",
    "music.overworld.cherry_grove": "music.overworld.jungle_and_forest",
    "intentionally_empty": "block.stone.break",
    "entity.sniffer.step": "entity.pig.step",
    "entity.sniffer.eat": "entity.generic.eat",
    "entity.sniffer.idle": "entity.pig.ambient",
    "entity.sniffer.hurt": "entity.pig.hurt",
    "entity.sniffer.death": "entity.pig.death",
    "entity.sniffer.drop_seed": "entity.chicken.egg",
    "entity.sniffer.scenting": "entity.pig.ambient",
    "entity.sniffer.sniffing": "entity.pig.ambient",
    "entity.sniffer.searching": "entity.pig.ambient",
    "entity.sniffer.digging": "entity.pig.step",
    "entity.sniffer.digging_stop": "entity.pig.step",
    "entity.sniffer.happy": "entity.pig.ambient",
}
# 音の改名：761 と 762 で名前が変わった音は無い。空のまま置く。
SOUND_RENAME_762_TO_761 = {}
SOUND_RENAME_761_TO_762 = {}
# パーティクル（762 → 761）。サクラの葉の粒子 3 件は white_ash へ。
PARTICLE_FALLBACK_762_TO_761 = {
    "dripping_cherry_leaves": "white_ash",
    "falling_cherry_leaves": "white_ash",
    "landing_cherry_leaves": "white_ash",
}
PARTICLE_RENAME_761_TO_762 = {}
# ブロックエンティティ：762 にだけある 2 件は 761 に代替が無いため落とす。
BLOCK_ENTITY_RENAME_761_TO_762 = {}
BLOCK_ENTITY_RENAME_762_TO_761 = {}
BLOCK_ENTITY_FALLBACK_762_TO_761 = {
    "suspicious_sand": None,
    "decorated_pot": None,
}
# エンティティ：762 にだけある 5 件は 761 に代替が無いためブタへ
# （プロジェクトの既定の代替エンティティ。display 系・interaction・
# sniffer は 761 クライアントではブタとして見える）。
ENTITY_FALLBACK_762_TO_761 = {
    "block_display": "pig",
    "interaction": "pig",
    "item_display": "pig",
    "sniffer": "pig",
    "text_display": "pig",
}
# メニューの改名（761 の smithing（旧2スロット）は 762 では
# legacy_smithing。762 の smithing（新3スロット）は 761 には無く、
# 名前が同じ smithing へそのまま写る）。
MENU_RENAME_761_TO_762 = {"smithing": "legacy_smithing"}
MENU_RENAME_762_TO_761 = {"legacy_smithing": "smithing"}
# バイオーム：761 と 762 で 63 件すべて同一（名前・ID とも）。
BIOME_FALLBACK_762_TO_761 = {}
# ダメージ種別：1.19.3 の loginPacket.json が minecraft-data に無いため
# 762 の 42 件と同一順序と仮定する（実バニラ 1.19.3 の Registry Data
# 取得で検証する。RegistryOrderCaptureTest 参照）。フォールバック不要。
DAMAGE_FALLBACK_762_TO_761 = {}


def build_block_state_maps(vv761, vv762, blocks761, blocks762):
    """ブロック状態の対応表（両方向）。"""
    states761 = vv761["blockstates"]
    states762 = vv762["blockstates"]
    idx761 = {s: i for i, s in enumerate(states761)}
    idx762 = {s: i for i, s in enumerate(states762)}
    def761 = {b["name"]: b["defaultState"] for b in blocks761}
    def762 = {b["name"]: b["defaultState"] for b in blocks762}

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

    by_name_762 = {}
    for s, i in idx762.items():
        n, p = parse_state(s)
        by_name_762.setdefault(n, []).append((p, i))

    fwd = []  # 762 -> 761
    unmapped_fwd = []
    for s in states762:
        if s in idx761:
            fwd.append(idx761[s])
            continue
        v = drop_one(s, idx761)
        if v is not None:
            fwd.append(v)
            continue
        name, _ = parse_state(s)
        fb = BLOCK_FALLBACK_762_TO_761.get(name)
        if fb is not None and fb in def761:
            fwd.append(def761[fb])
            continue
        unmapped_fwd.append(s)
        fwd.append(-1)
    rev = []  # 761 -> 762
    unmapped_rev = []
    for s in states761:
        if s in idx762:
            rev.append(idx762[s])
            continue
        v = add_one(s, by_name_762)
        if v is not None:
            rev.append(v)
            continue
        name, _ = parse_state(s)
        fb = BLOCK_FALLBACK_762_TO_761.get(name)
        if fb is not None and fb in def762:
            rev.append(def762[fb])
            continue
        unmapped_rev.append(s)
        rev.append(-1)
    return fwd, rev, unmapped_fwd, unmapped_rev


def build_name_map(list761, list762, rename_761_to_762=None,
                   fallback_762_to_761=None, fallback_id_761=None,
                   rename_762_to_761=None, rename_first_rev=False):
    """名前リストの対応表（両方向）。rename は改名表、fallback は
    762 にだけある要素の代替（761側の名前）。fallback_id_761 は
    代替なしの場合の 761 側の ID（None なら -1）。
    rename_first_rev が真のとき、逆向き（761→762）は同名一致より
    改名を優先する（メニューの smithing→legacy_smithing 用）。"""
    idx761 = {n: i for i, n in enumerate(list761)}
    idx762 = {n: i for i, n in enumerate(list762)}
    rename_761_to_762 = rename_761_to_762 or {}
    rename_762_to_761 = rename_762_to_761 or {}
    fallback_762_to_761 = fallback_762_to_761 or {}
    fwd = []
    unmapped_fwd = []
    for n in list762:
        if n in idx761:
            fwd.append(idx761[n])
        elif n in rename_762_to_761 and rename_762_to_761[n] in idx761:
            fwd.append(idx761[rename_762_to_761[n]])
        elif n in fallback_762_to_761:
            fb = fallback_762_to_761[n]
            if fb is None:
                fwd.append(fallback_id_761 if fallback_id_761 is not None else -1)
            elif fb in idx761:
                fwd.append(idx761[fb])
            else:
                unmapped_fwd.append(n)
                fwd.append(-1)
        else:
            unmapped_fwd.append(n)
            fwd.append(-1)
    rev = []
    unmapped_rev = []
    for n in list761:
        if rename_first_rev and n in rename_761_to_762 \
                and rename_761_to_762[n] in idx762:
            rev.append(idx762[rename_761_to_762[n]])
        elif n in idx762:
            rev.append(idx762[n])
        elif n in rename_761_to_762 and rename_761_to_762[n] in idx762:
            rev.append(idx762[rename_761_to_762[n]])
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

    vv761 = load(d / "vv-1.19.3.json")
    vv762 = load(d / "vv-1.19.4.json")
    # ViaVersion の mapping-1.19.3.json には "menus" キーが無い。
    # 1.19.3 のメニューは 1.20（763）の 24 件と同じ（smithing のみ。
    # 鍛冶台の新旧分離は 1.19.4 で入った）。vv-1.20.json から借りる。
    menus761 = load(d / "vv-1.20.json")["menus"]
    blocks761 = load(d / "pc" / "1.19.3" / "blocks.json")
    blocks762 = load(d / "pc" / "1.19.4" / "blocks.json")
    biomes761 = {b["name"]: b["id"] for b in load(d / "pc" / "1.19.3" / "biomes.json")}
    biomes762 = {b["name"]: b["id"] for b in load(d / "pc" / "1.19.4" / "biomes.json")}

    def damage_names(version):
        login = load(d / "pc" / version / "loginPacket.json")
        entries = login["dimensionCodec"]["value"]["minecraft:damage_type"] \
            ["value"]["value"]["value"]["value"]
        return [e["name"]["value"] for e in entries]

    # 1.19.3 の loginPacket.json は minecraft-data に無い（404）。
    # ダメージ種別は 762 の 42 件と同一順序と仮定する。実バニラ 1.19.3
    # の Registry Data 取得（RegistryOrderCaptureTest）で検証する。
    damage762 = damage_names("1.19.4")
    damage761 = list(damage762)

    problems = []

    def check(name, unmapped_fwd, unmapped_rev):
        if unmapped_fwd or unmapped_rev:
            problems.append((name, unmapped_fwd, unmapped_rev))

    bs_fwd, bs_rev, u, v = build_block_state_maps(vv761, vv762, blocks761, blocks762)
    check("blockstate", u, v)

    item_fwd, item_rev, u, v = build_name_map(
        vv761["items"], vv762["items"],
        rename_761_to_762=ITEM_RENAME_761_TO_762,
        rename_762_to_761=ITEM_RENAME_762_TO_761,
        fallback_762_to_761=ITEM_FALLBACK_762_TO_761,
        fallback_id_761=1)  # 石
    check("item", u, v)
    ent_fwd, ent_rev, u, v = build_name_map(
        vv761["entities"], vv762["entities"],
        fallback_762_to_761=ENTITY_FALLBACK_762_TO_761,
        fallback_id_761=68)  # ブタ
    check("entity", u, v)
    snd_fwd, snd_rev, u, v = build_name_map(
        vv761["sounds"], vv762["sounds"],
        rename_761_to_762=SOUND_RENAME_761_TO_762,
        rename_762_to_761=SOUND_RENAME_762_TO_761,
        fallback_762_to_761=SOUND_FALLBACK_762_TO_761,
        fallback_id_761=None)
    # 音の代替なしは石の破壊音（761 の block.stone.break）へ。
    stone_break_761 = vv761["sounds"].index("block.stone.break")
    snd_fwd = [stone_break_761 if x < 0 else x for x in snd_fwd]
    check("sound", u, v)
    part_fwd, part_rev, u, v = build_name_map(
        vv761["particles"], vv762["particles"],
        rename_761_to_762=PARTICLE_RENAME_761_TO_762,
        fallback_762_to_761=PARTICLE_FALLBACK_762_TO_761,
        fallback_id_761=0)  # ambient_entity_effect
    check("particle", u, v)
    blk_fwd, blk_rev, u, v = build_name_map(
        vv761["blocks"], vv762["blocks"],
        fallback_762_to_761=BLOCK_FALLBACK_762_TO_761,
        fallback_id_761=1)  # 石
    check("block", u, v)
    be_fwd, be_rev, u, v = build_name_map(
        vv761["blockentities"], vv762["blockentities"],
        rename_761_to_762=BLOCK_ENTITY_RENAME_761_TO_762,
        rename_762_to_761=BLOCK_ENTITY_RENAME_762_TO_761,
        fallback_762_to_761=BLOCK_ENTITY_FALLBACK_762_TO_761,
        fallback_id_761=None)
    check("blockentity", u, v)
    menu_fwd, menu_rev, u, v = build_name_map(
        menus761, vv762["menus"],
        rename_761_to_762=MENU_RENAME_761_TO_762,
        rename_762_to_761=MENU_RENAME_762_TO_761,
        fallback_id_761=None, rename_first_rev=True)
    check("menu", u, v)
    biome_fwd, biome_rev, u, v = build_name_map(
        [n for n, _ in sorted(biomes761.items(), key=lambda kv: kv[1])],
        [n for n, _ in sorted(biomes762.items(), key=lambda kv: kv[1])],
        fallback_762_to_761=BIOME_FALLBACK_762_TO_761,
        fallback_id_761=None)
    check("biome", u, v)
    dmg_fwd, dmg_rev, u, v = build_name_map(
        damage761, damage762,
        fallback_762_to_761=DAMAGE_FALLBACK_762_TO_761,
        fallback_id_761=None)
    check("damage", u, v)
    # 絵画・エンチャントは同一順序のはず。違えば失敗させる。
    for key in ("paintings", "enchantments"):
        if vv761[key] != vv762[key]:
            problems.append((key, ["order differs"], []))
    # エフェクトは minecraft-data で同一（1ベース・33件）。
    eff761 = {x["name"]: x["id"] for x in load(d / "pc" / "1.19.3" / "effects.json")}
    eff762 = {x["name"]: x["id"] for x in load(d / "pc" / "1.19.4" / "effects.json")}
    if eff761 != eff762:
        problems.append(("effects", ["order differs"], []))

    if problems:
        for name, uf, ur in problems:
            print(f"未対応 {name}: 762→761 {uf} / 761→762 {ur}", file=sys.stderr)
        return 1

    maps = {
        "BLOCK_STATE": (bs_fwd, bs_rev, len(vv762["blockstates"]), len(vv761["blockstates"])),
        "ITEM": (item_fwd, item_rev, len(vv762["items"]), len(vv761["items"])),
        "ENTITY_TYPE": (ent_fwd, ent_rev, len(vv762["entities"]), len(vv761["entities"])),
        "SOUND": (snd_fwd, snd_rev, len(vv762["sounds"]), len(vv761["sounds"])),
        "PARTICLE": (part_fwd, part_rev, len(vv762["particles"]), len(vv761["particles"])),
        "BLOCK": (blk_fwd, blk_rev, len(vv762["blocks"]), len(vv761["blocks"])),
        "BLOCK_ENTITY": (be_fwd, be_rev, len(vv762["blockentities"]), len(vv761["blockentities"])),
        "MENU": (menu_fwd, menu_rev, len(vv762["menus"]), len(menus761)),
        "BIOME": (biome_fwd, biome_rev, len(biomes762), len(biomes761)),
        "DAMAGE_TYPE": (dmg_fwd, dmg_rev, len(damage762), len(damage761)),
    }
    fallbacks_fwd = {
        "BLOCK_STATE": 1, "ITEM": 1, "ENTITY_TYPE": 68,
        "SOUND": stone_break_761, "PARTICLE": 0, "BLOCK": 1,
        "BLOCK_ENTITY": -1, "MENU": 11, "BIOME": biomes761["plains"],
        "DAMAGE_TYPE": 16,
    }
    stone_break_762 = vv762["sounds"].index("block.stone.break")
    fallbacks_rev = {
        "BLOCK_STATE": 1, "ITEM": 1, "ENTITY_TYPE": 72,
        "SOUND": stone_break_762, "PARTICLE": 0, "BLOCK": 1,
        "BLOCK_ENTITY": -1, "MENU": 11, "BIOME": biomes762["plains"],
        "DAMAGE_TYPE": 16,
    }
    # 761 側の代替先：石のブロック状態 1、石のアイテム 1、ブタ 68、
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
        print(f"{name}: 762→761 entries={n_fwd} ranges={len(fr)} overrides={len(fo)} "
              f"fallback={sum(1 for x in fwd if x == fb)} / "
              f"761→762 entries={n_rev} ranges={len(rr)} overrides={len(ro)} "
              f"fallback={sum(1 for x in rev if x == fallbacks_rev[name])}")

    if args.json:
        out = {name: {"fwd": fwd, "rev": rev}
               for name, (fwd, rev, _, _) in maps.items()}
        args.json.write_text(json.dumps(out, indent=1) + "\n", encoding="utf-8")
        print(f"JSON を {args.json} に書き出した。")

    header = """package io.detergentmc.protocol;

/**
 * 761（1.19.3）のレジストリ対応表（区間＋上書きの生の表）。
 *
 * <p>{@link RegistryRemapper} から分離した。理由はバイトコードの制限：
 * 表を RegistryRemapper に置くと静的初期化子（{@code <clinit>}）が
 * 64KB を超えてコンパイルできなくなる（"code too large"）ため。
 * このクラスの表は {@link RegistryRemapper} から static import で
 * 同じ名前のまま参照する。
 *
 * <p>表の内容と検証は {@code detergentmc-protocol/tools/} を参照：
 * 生成は {@code gen-761-tables.py}、検証は {@code verify-761-tables.py}。
 * 情報源は ViaVersion Mappings の mapping-1.19.3.json（761）と
 * mapping-1.19.4.json（762）で、名前で突き合わせた。ブロック状態は
 * プロパティを1個だけ落とす／足す一致も使う（762 で decorated_pot に
 * cracked が追加された）。1.19.4 の新要素の代替は次の通り：
 * サクラ系→オーク系、suspicious_sand→sand、torchflower→dandelion、
 * torchflower_crop→wheat、pink_petals→peony、decorated_pot→flower_pot、
 * sniffer_spawn_egg→turtle_spawn_egg、torchflower_seeds→wheat_seeds、
 * brush→stick、鍛冶型テンプレート 11 件→netherite_ingot／iron_ingot、
 * 陶片 4 件→brick。音はサクラ系→木／草／吊り看板／木製ドア類の音へ、
 * decorated_pot→石の音（shatter だけガラス）、suspicious_sand→砂の音、
 * cherry_grove の音楽→jungle_and_forest、スニッファー→ブタの音。
 * パーティクルはサクラの葉 3 件→white_ash。ブロックエンティティの
 * suspicious_sand／decorated_pot は落とす（-1）。エンティティは
 * block_display／interaction／item_display／sniffer／text_display→
 * ブタ。メニューは smithing→legacy_smithing。ダメージ種別は 762 と
 * 同一順序と仮定（実バニラで検証する）。絵画・エンチャント・
 * エフェクト・バイオームは 761 と 762 で同一のため恒等写像。
 */
final class RegistryTables761 {

    private RegistryTables761() {
    }

"""
    parts = [header]
    for name, (fwd, rev, n_fwd, n_rev) in maps.items():
        fb = fallbacks_fwd[name]
        fr, fo = ranges_overrides(fwd, n_fwd, fb)
        rr, ro = ranges_overrides(rev, n_rev, fallbacks_rev[name])
        parts.append(java_table(f"{name}_761", fr, fo, n_fwd, "762->761"))
        parts.append("")
        parts.append(java_table(f"{name}_761_TO_762", rr, ro, n_rev, "761->762"))
        parts.append("")
    # 音の名前の代替表（762→761。Stop Sound 用。数値表の代替と一致させる）。
    snd_names = json.loads((Path(__file__).parent / "sound-names-762-to-761.json").read_text(encoding="utf-8"))
    lines = ["    /** 音の名前：762 → 761（Stop Sound 用）。数値の合成表と一致させる。 */",
             "    static final String[][] SOUND_NAME_FALLBACKS_762_TO_761 = {"]
    for src, dst in snd_names:
        lines.append(f'            {{"{src}", "{dst}"}},')
    lines.append("    };")
    parts.append("\n".join(lines))
    parts.append("")
    # 音の名前の代替表（761→762。Stop Sound 用。数値表の代替と一致させる）。
    snd_names_rev = json.loads((Path(__file__).parent / "sound-names-761-to-762.json").read_text(encoding="utf-8"))
    lines = ["    /** 音の名前：761 → 762（Stop Sound 用）。数値の合成表と一致させる。 */",
             "    static final String[][] SOUND_NAME_FALLBACKS_761_TO_762 = {"]
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
