#!/usr/bin/env python3
"""760（1.19.1／1.19.2）↔760（1.19.3）のレジストリ対応表を生成する。

情報源（推測で埋めない。すべて名前で突き合わせる）：
  - ViaVersion Mappings（Mojang の data-generator レポート由来。
    リストの index がレジストリ ID）：
      https://raw.githubusercontent.com/ViaVersion/Mappings/main/mappings/mapping-1.19.json   （760。
      ViaVersion には 1.19.1／1.19.2 の mapping ファイルが無い。
      minecraft-data で 1.19 と 1.19.2 の blocks／items／entities／
      biomes が同一順序・同一内容であることを確認済みのため、
      mapping-1.19.json を 760 の情報源として使う）
      https://raw.githubusercontent.com/ViaVersion/Mappings/main/mappings/mapping-1.19.3.json （760）
  - PrismarineJS minecraft-data（バイオーム・ダメージ種別・
    代替ブロックの既定状態）：
      data/pc/1.19.2/biomes.json、data/pc/1.19.3/biomes.json
      data/pc/1.19.4/loginPacket.json（minecraft:damage_type の順番。
      760・760 の codec には damage_type レジストリが無い
      （1.19.4 で追加）。対応表は 761 の 42 件の恒等写像として作るが
      ワイヤ上の呼び出し元は無い）
      data/pc/1.19.2/blocks.json、data/pc/1.19.3/blocks.json（defaultState）

対応の決め方（優先順）：
  1. 名前がそのまま一致する → その ID。
  2. ブロック状態だけ：プロパティを1個だけ落とす／足すと一致する
     （761で decorated_pot に cracked が追加された）→ その ID。
  3. 改名だけ（smithing → legacy_smithing（メニュー）など）
     → 改名後の ID。760 と 761 で名前が変わった音・アイテムは無い。
  4. 下の *_FALLBACK 表にある 1.19.4 の新要素 → 近い既存要素。
  5. それでも決まらないものがあれば「未対応」として一覧を出し、
     exit 1 で失敗する（黙って推測しない）。

使い方：
  python3 tools/gen-760-tables.py [--data-dir DIR] [--out FILE]
  --data-dir を省略すると ~/workspace/mcdata760 を使う。
  --out を省略すると RegistryTables760.java を標準出力に出す
  （--out FILE でファイルに書く）。--json FILE で対応表の JSON も出す。
"""

import argparse
import json
import sys
from collections import Counter
from pathlib import Path

DATA_DIR = Path.home() / "workspace" / "mcdata760"


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
# フォールバック表（760 にだけあるものを 760 の近い要素へ）
# ----------------------------------------------------------------------
# ブロック状態・ブロック：760 の新ブロック → 760 にある近いブロック。
# 竹系はオーク系へ。吊り看板は同じ木の通常の看板へ。
# chiseled_bookshelf は bookshelf へ。piglin_head は zombie_head へ。
BLOCK_FALLBACK_761_TO_760 = {
    "acacia_hanging_sign": "acacia_sign",
    "acacia_wall_hanging_sign": "acacia_wall_sign",
    "bamboo_block": "oak_log",
    "bamboo_button": "oak_button",
    "bamboo_door": "oak_door",
    "bamboo_fence": "oak_fence",
    "bamboo_fence_gate": "oak_fence_gate",
    "bamboo_hanging_sign": "oak_sign",
    "bamboo_mosaic": "oak_planks",
    "bamboo_mosaic_slab": "oak_slab",
    "bamboo_mosaic_stairs": "oak_stairs",
    "bamboo_planks": "oak_planks",
    "bamboo_pressure_plate": "oak_pressure_plate",
    "bamboo_sign": "oak_sign",
    "bamboo_slab": "oak_slab",
    "bamboo_stairs": "oak_stairs",
    "bamboo_trapdoor": "oak_trapdoor",
    "bamboo_wall_hanging_sign": "oak_wall_sign",
    "bamboo_wall_sign": "oak_wall_sign",
    "birch_hanging_sign": "birch_sign",
    "birch_wall_hanging_sign": "birch_wall_sign",
    "chiseled_bookshelf": "bookshelf",
    "crimson_hanging_sign": "crimson_sign",
    "crimson_wall_hanging_sign": "crimson_wall_sign",
    "dark_oak_hanging_sign": "dark_oak_sign",
    "dark_oak_wall_hanging_sign": "dark_oak_wall_sign",
    "jungle_hanging_sign": "jungle_sign",
    "jungle_wall_hanging_sign": "jungle_wall_sign",
    "mangrove_hanging_sign": "mangrove_sign",
    "mangrove_wall_hanging_sign": "mangrove_wall_sign",
    "oak_hanging_sign": "oak_sign",
    "oak_wall_hanging_sign": "oak_wall_sign",
    "piglin_head": "zombie_head",
    "piglin_wall_head": "zombie_wall_head",
    "spruce_hanging_sign": "spruce_sign",
    "spruce_wall_hanging_sign": "spruce_wall_sign",
    "stripped_bamboo_block": "stripped_oak_log",
    "warped_hanging_sign": "warped_sign",
    "warped_wall_hanging_sign": "warped_wall_sign",
}
# アイテム：760 の新アイテム → 760 にある近いアイテム。
# 竹のいかだはオークのボートへ。ラクダのスポーンエッグは馬へ。
# ボス／ゴーレム系のスポーンエッグは 760 に無いため近いアイテムへ
# （エンダードラゴン→ドラゴンの卵、アイアンゴーレム→鉄インゴット、
# スノーゴーレム→雪玉、ウィザー→ネザースター）。
ITEM_FALLBACK_761_TO_760 = {
    "acacia_hanging_sign": "acacia_sign",
    "bamboo_block": "oak_log",
    "bamboo_button": "oak_button",
    "bamboo_chest_raft": "oak_chest_boat",
    "bamboo_door": "oak_door",
    "bamboo_fence": "oak_fence",
    "bamboo_fence_gate": "oak_fence_gate",
    "bamboo_hanging_sign": "oak_sign",
    "bamboo_mosaic": "oak_planks",
    "bamboo_mosaic_slab": "oak_slab",
    "bamboo_mosaic_stairs": "oak_stairs",
    "bamboo_planks": "oak_planks",
    "bamboo_pressure_plate": "oak_pressure_plate",
    "bamboo_raft": "oak_boat",
    "bamboo_sign": "oak_sign",
    "bamboo_slab": "oak_slab",
    "bamboo_stairs": "oak_stairs",
    "bamboo_trapdoor": "oak_trapdoor",
    "birch_hanging_sign": "birch_sign",
    "camel_spawn_egg": "horse_spawn_egg",
    "chiseled_bookshelf": "bookshelf",
    "crimson_hanging_sign": "crimson_sign",
    "dark_oak_hanging_sign": "dark_oak_sign",
    "ender_dragon_spawn_egg": "dragon_egg",
    "iron_golem_spawn_egg": "iron_ingot",
    "jungle_hanging_sign": "jungle_sign",
    "mangrove_hanging_sign": "mangrove_sign",
    "oak_hanging_sign": "oak_sign",
    "piglin_head": "zombie_head",
    "snow_golem_spawn_egg": "snowball",
    "spruce_hanging_sign": "spruce_sign",
    "stripped_bamboo_block": "stripped_oak_log",
    "warped_hanging_sign": "warped_sign",
    "wither_spawn_egg": "nether_star",
}
# アイテムの改名：760 と 760 で名前が変わったアイテムは無い。空のまま置く。
ITEM_RENAME_760_TO_761 = {}
ITEM_RENAME_761_TO_760 = {}
# 音のフォールバック（761 → 760）。761 にだけある 71 件すべて。
# 竹の木材音は汎用の木の音へ、竹の吊り看板・ネザーの木材の吊り看板は
# 木の音へ、ドア類は木製ドア類の音へ、chiseled_bookshelf は木の音へ
# （出し入れは木の置く／壊す音へ）、hanging_sign は木の音へ、
# note_block の imitate 6 件は harp へ（760 には imitate 音が無い）、
# ラクダは馬の音へ（dash→gallop、dash_ready／sit／stand→ambient、
# step_sand→step）。
SOUND_FALLBACK_761_TO_760 = {
    "block.bamboo_wood.break": "block.wood.break",
    "block.bamboo_wood.fall": "block.wood.fall",
    "block.bamboo_wood.hit": "block.wood.hit",
    "block.bamboo_wood.place": "block.wood.place",
    "block.bamboo_wood.step": "block.wood.step",
    "block.bamboo_wood_button.click_off": "block.wooden_button.click_off",
    "block.bamboo_wood_button.click_on": "block.wooden_button.click_on",
    "block.bamboo_wood_door.close": "block.wooden_door.close",
    "block.bamboo_wood_door.open": "block.wooden_door.open",
    "block.bamboo_wood_fence_gate.close": "block.fence_gate.close",
    "block.bamboo_wood_fence_gate.open": "block.fence_gate.open",
    "block.bamboo_wood_hanging_sign.break": "block.wood.break",
    "block.bamboo_wood_hanging_sign.fall": "block.wood.fall",
    "block.bamboo_wood_hanging_sign.hit": "block.wood.hit",
    "block.bamboo_wood_hanging_sign.place": "block.wood.place",
    "block.bamboo_wood_hanging_sign.step": "block.wood.step",
    "block.bamboo_wood_pressure_plate.click_off": "block.wooden_pressure_plate.click_off",
    "block.bamboo_wood_pressure_plate.click_on": "block.wooden_pressure_plate.click_on",
    "block.bamboo_wood_trapdoor.close": "block.wooden_trapdoor.close",
    "block.bamboo_wood_trapdoor.open": "block.wooden_trapdoor.open",
    "block.chiseled_bookshelf.break": "block.wood.break",
    "block.chiseled_bookshelf.fall": "block.wood.fall",
    "block.chiseled_bookshelf.hit": "block.wood.hit",
    "block.chiseled_bookshelf.insert": "block.wood.place",
    "block.chiseled_bookshelf.insert.enchanted": "block.wood.place",
    "block.chiseled_bookshelf.pickup": "block.wood.break",
    "block.chiseled_bookshelf.pickup.enchanted": "block.wood.break",
    "block.chiseled_bookshelf.place": "block.wood.place",
    "block.chiseled_bookshelf.step": "block.wood.step",
    "block.hanging_sign.break": "block.wood.break",
    "block.hanging_sign.fall": "block.wood.fall",
    "block.hanging_sign.hit": "block.wood.hit",
    "block.hanging_sign.place": "block.wood.place",
    "block.hanging_sign.step": "block.wood.step",
    "block.nether_wood.break": "block.wood.break",
    "block.nether_wood.fall": "block.wood.fall",
    "block.nether_wood.hit": "block.wood.hit",
    "block.nether_wood.place": "block.wood.place",
    "block.nether_wood.step": "block.wood.step",
    "block.nether_wood_button.click_off": "block.wooden_button.click_off",
    "block.nether_wood_button.click_on": "block.wooden_button.click_on",
    "block.nether_wood_door.close": "block.wooden_door.close",
    "block.nether_wood_door.open": "block.wooden_door.open",
    "block.nether_wood_fence_gate.close": "block.fence_gate.close",
    "block.nether_wood_fence_gate.open": "block.fence_gate.open",
    "block.nether_wood_hanging_sign.break": "block.wood.break",
    "block.nether_wood_hanging_sign.fall": "block.wood.fall",
    "block.nether_wood_hanging_sign.hit": "block.wood.hit",
    "block.nether_wood_hanging_sign.place": "block.wood.place",
    "block.nether_wood_hanging_sign.step": "block.wood.step",
    "block.nether_wood_pressure_plate.click_off": "block.wooden_pressure_plate.click_off",
    "block.nether_wood_pressure_plate.click_on": "block.wooden_pressure_plate.click_on",
    "block.nether_wood_trapdoor.close": "block.wooden_trapdoor.close",
    "block.nether_wood_trapdoor.open": "block.wooden_trapdoor.open",
    "block.note_block.imitate.creeper": "block.note_block.harp",
    "block.note_block.imitate.ender_dragon": "block.note_block.harp",
    "block.note_block.imitate.piglin": "block.note_block.harp",
    "block.note_block.imitate.skeleton": "block.note_block.harp",
    "block.note_block.imitate.wither_skeleton": "block.note_block.harp",
    "block.note_block.imitate.zombie": "block.note_block.harp",
    "entity.camel.ambient": "entity.horse.ambient",
    "entity.camel.dash": "entity.horse.gallop",
    "entity.camel.dash_ready": "entity.horse.breathe",
    "entity.camel.death": "entity.horse.death",
    "entity.camel.eat": "entity.horse.eat",
    "entity.camel.hurt": "entity.horse.hurt",
    "entity.camel.saddle": "entity.horse.saddle",
    "entity.camel.sit": "entity.horse.ambient",
    "entity.camel.stand": "entity.horse.ambient",
    "entity.camel.step": "entity.horse.step",
    "entity.camel.step_sand": "entity.horse.step",
}
# 音の改名：760 と 760 で名前が変わった音は無い。空のまま置く。
SOUND_RENAME_761_TO_760 = {}
SOUND_RENAME_760_TO_761 = {}
# ブロック状態のプロパティ値フォールバック（761 → 760）。
# note_block の新楽器 7 種（zombie／skeleton／creeper／dragon／
# wither_skeleton／piglin／custom_head）は 760 に無い。同じ note・
# powered で instrument=harp（既定の楽器）へ置き換える。
# （ブロック名単位のフォールバックでは拾えないため、ここで扱う）
STATE_PROP_FALLBACK_761_TO_760 = {
    "note_block": {"instrument": {
        "zombie": "harp", "skeleton": "harp", "creeper": "harp",
        "dragon": "harp", "wither_skeleton": "harp", "piglin": "harp",
        "custom_head": "harp",
    }},
}
# パーティクル（761 → 760）。93 件すべて同一（名前・順序とも）。フォールバック不要。
PARTICLE_FALLBACK_761_TO_760 = {}
PARTICLE_RENAME_760_TO_761 = {}
# ブロックエンティティ：760 にだけある 2 件は 760 に代替が無いため落とす。
BLOCK_ENTITY_RENAME_760_TO_761 = {}
BLOCK_ENTITY_RENAME_761_TO_760 = {}
BLOCK_ENTITY_FALLBACK_761_TO_760 = {
    "chiseled_bookshelf": None,
    "hanging_sign": None,
}
# エンティティ：760 にだけあるラクダは 760 では馬に見える。
ENTITY_FALLBACK_761_TO_760 = {
    "camel": "horse",
}
# メニュー：760 と 760 で 24 件すべて同一（smithing の分離は 1.19.4 で入った）。
MENU_RENAME_760_TO_761 = {}
MENU_RENAME_761_TO_760 = {}
# バイオーム：760 と 760 で 63 件すべて同一（名前は同じ。順序は違うため
# 名前で突き合わせる。plains は 760=1、760=38）。
BIOME_FALLBACK_761_TO_760 = {}
# ダメージ種別：760・760 どちらの codec にも damage_type レジストリは無い
# （1.19.4 で追加）。対応表は 761 の 42 件の恒等写像として作るが、
# ワイヤ上の呼び出し元は無い。実バニラ 1.19.2 の Registry Data 取得で
# codec の中身を検証する（RegistryOrderCaptureTest 参照）。
DAMAGE_FALLBACK_761_TO_760 = {}


def build_block_state_maps(vv760, vv761, blocks760, blocks761):
    """ブロック状態の対応表（両方向）。"""
    states760 = vv760["blockstates"]
    states761 = vv761["blockstates"]
    idx760 = {s: i for i, s in enumerate(states760)}
    idx761 = {s: i for i, s in enumerate(states761)}
    def760 = {b["name"]: b["defaultState"] for b in blocks760}
    def761 = {b["name"]: b["defaultState"] for b in blocks761}

    def drop_one(state, target_idx):
        """プロパティを1個だけ落として target に一致するものを探す。"""
        name, props = parse_state(state)
        for drop in sorted(props):
            rest = {k: v for k, v in props.items() if k != drop}
            cand = fmt_state(name, rest)
            if cand in target_idx:
                return target_idx[cand]
        return None

    def subst_one(state, target_idx):
        """プロパティの値を1個だけ置き換えて target に一致するものを探す。
        STATE_PROP_FALLBACK_761_TO_760 の表を使う。"""
        name, props = parse_state(state)
        for prop, subs in STATE_PROP_FALLBACK_761_TO_760.get(name, {}).items():
            if prop in props and props[prop] in subs:
                rest = dict(props)
                rest[prop] = subs[props[prop]]
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

    by_name_761 = {}
    for s, i in idx761.items():
        n, p = parse_state(s)
        by_name_761.setdefault(n, []).append((p, i))

    fwd = []  # 761 -> 760
    unmapped_fwd = []
    for s in states761:
        if s in idx760:
            fwd.append(idx760[s])
            continue
        v = drop_one(s, idx760)
        if v is not None:
            fwd.append(v)
            continue
        name, props = parse_state(s)
        v = subst_one(s, idx760)
        if v is not None:
            fwd.append(v)
            continue
        fb = BLOCK_FALLBACK_761_TO_760.get(name)
        if fb is not None and fb in def760:
            fwd.append(def760[fb])
            continue
        unmapped_fwd.append(s)
        fwd.append(-1)
    rev = []  # 760 -> 761
    unmapped_rev = []
    for s in states760:
        if s in idx761:
            rev.append(idx761[s])
            continue
        v = add_one(s, by_name_761)
        if v is not None:
            rev.append(v)
            continue
        name, _ = parse_state(s)
        fb = BLOCK_FALLBACK_761_TO_760.get(name)
        if fb is not None and fb in def761:
            rev.append(def761[fb])
            continue
        unmapped_rev.append(s)
        rev.append(-1)
    return fwd, rev, unmapped_fwd, unmapped_rev


def build_name_map(list760, list761, rename_760_to_761=None,
                   fallback_761_to_760=None, fallback_id_760=None,
                   rename_761_to_760=None, rename_first_rev=False):
    """名前リストの対応表（両方向）。rename は改名表、fallback は
    761 にだけある要素の代替（760側の名前）。fallback_id_760 は
    代替なしの場合の 760 側の ID（None なら -1）。
    rename_first_rev が真のとき、逆向き（760→761）は同名一致より
    改名を優先する（メニューの smithing→legacy_smithing 用）。"""
    idx760 = {n: i for i, n in enumerate(list760)}
    idx761 = {n: i for i, n in enumerate(list761)}
    rename_760_to_761 = rename_760_to_761 or {}
    rename_761_to_760 = rename_761_to_760 or {}
    fallback_761_to_760 = fallback_761_to_760 or {}
    fwd = []
    unmapped_fwd = []
    for n in list761:
        if n in idx760:
            fwd.append(idx760[n])
        elif n in rename_761_to_760 and rename_761_to_760[n] in idx760:
            fwd.append(idx760[rename_761_to_760[n]])
        elif n in fallback_761_to_760:
            fb = fallback_761_to_760[n]
            if fb is None:
                fwd.append(fallback_id_760 if fallback_id_760 is not None else -1)
            elif fb in idx760:
                fwd.append(idx760[fb])
            else:
                unmapped_fwd.append(n)
                fwd.append(-1)
        else:
            unmapped_fwd.append(n)
            fwd.append(-1)
    rev = []
    unmapped_rev = []
    for n in list760:
        if rename_first_rev and n in rename_760_to_761 \
                and rename_760_to_761[n] in idx761:
            rev.append(idx761[rename_760_to_761[n]])
        elif n in idx761:
            rev.append(idx761[n])
        elif n in rename_760_to_761 and rename_760_to_761[n] in idx761:
            rev.append(idx761[rename_760_to_761[n]])
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

    vv760 = load(d / "mapping-1.19.json")
    vv761 = load(d / "mapping-1.19.3.json")
    # ViaVersion の mapping-1.19.json／mapping-1.19.3.json には "menus"
    # キーが無い。760・761 のメニューは 1.20（763）の 24 件と同じ
    # （smithing のみ。鍛冶台の新旧分離は 1.19.4 で入った）。
    # vv-1.20.json から借りる。
    menus760 = load(d / "vv-1.20.json")["menus"]
    menus761 = list(menus760)
    blocks760 = load(d / "pc" / "1.19.2" / "blocks.json")
    blocks761 = load(d / "pc" / "1.19.3" / "blocks.json")
    biomes760 = {b["name"]: b["id"] for b in load(d / "pc" / "1.19.2" / "biomes.json")}
    biomes761 = {b["name"]: b["id"] for b in load(d / "pc" / "1.19.3" / "biomes.json")}

    # 760・761 の codec には damage_type レジストリが無い（1.19.4 で追加）。
    # 対応表は 762（1.19.4）の 42 件の恒等写像として作る。ワイヤ上の
    # 呼び出し元は無い。実バニラ 1.19.2 の Registry Data 取得
    # （RegistryOrderCaptureTest）で codec の中身を検証する。
    login_762 = load(d / "pc" / "1.19.4-loginPacket.json")
    entries = login_762["dimensionCodec"]["value"]["minecraft:damage_type"] \
        ["value"]["value"]["value"]["value"]
    damage761 = [e["name"]["value"] for e in entries]
    damage760 = list(damage761)

    problems = []

    def check(name, unmapped_fwd, unmapped_rev):
        if unmapped_fwd or unmapped_rev:
            problems.append((name, unmapped_fwd, unmapped_rev))

    bs_fwd, bs_rev, u, v = build_block_state_maps(vv760, vv761, blocks760, blocks761)
    check("blockstate", u, v)

    item_fwd, item_rev, u, v = build_name_map(
        vv760["items"], vv761["items"],
        rename_760_to_761=ITEM_RENAME_760_TO_761,
        rename_761_to_760=ITEM_RENAME_761_TO_760,
        fallback_761_to_760=ITEM_FALLBACK_761_TO_760,
        fallback_id_760=1)  # 石
    check("item", u, v)
    ent_fwd, ent_rev, u, v = build_name_map(
        vv760["entities"], vv761["entities"],
        fallback_761_to_760=ENTITY_FALLBACK_761_TO_760,
        fallback_id_760=68)  # ブタ
    check("entity", u, v)
    snd_fwd, snd_rev, u, v = build_name_map(
        vv760["sounds"], vv761["sounds"],
        rename_760_to_761=SOUND_RENAME_760_TO_761,
        rename_761_to_760=SOUND_RENAME_761_TO_760,
        fallback_761_to_760=SOUND_FALLBACK_761_TO_760,
        fallback_id_760=None)
    # 音の代替なしは石の破壊音（760 の block.stone.break）へ。
    stone_break_760 = vv760["sounds"].index("block.stone.break")
    snd_fwd = [stone_break_760 if x < 0 else x for x in snd_fwd]
    check("sound", u, v)
    part_fwd, part_rev, u, v = build_name_map(
        vv760["particles"], vv761["particles"],
        rename_760_to_761=PARTICLE_RENAME_760_TO_761,
        fallback_761_to_760=PARTICLE_FALLBACK_761_TO_760,
        fallback_id_760=0)  # ambient_entity_effect
    check("particle", u, v)
    blk_fwd, blk_rev, u, v = build_name_map(
        vv760["blocks"], vv761["blocks"],
        fallback_761_to_760=BLOCK_FALLBACK_761_TO_760,
        fallback_id_760=1)  # 石
    check("block", u, v)
    be_fwd, be_rev, u, v = build_name_map(
        vv760["blockentities"], vv761["blockentities"],
        rename_760_to_761=BLOCK_ENTITY_RENAME_760_TO_761,
        rename_761_to_760=BLOCK_ENTITY_RENAME_761_TO_760,
        fallback_761_to_760=BLOCK_ENTITY_FALLBACK_761_TO_760,
        fallback_id_760=None)
    check("blockentity", u, v)
    menu_fwd, menu_rev, u, v = build_name_map(
        menus760, menus761,
        rename_760_to_761=MENU_RENAME_760_TO_761,
        rename_761_to_760=MENU_RENAME_761_TO_760,
        fallback_id_760=None, rename_first_rev=True)
    check("menu", u, v)
    biome_fwd, biome_rev, u, v = build_name_map(
        [n for n, _ in sorted(biomes760.items(), key=lambda kv: kv[1])],
        [n for n, _ in sorted(biomes761.items(), key=lambda kv: kv[1])],
        fallback_761_to_760=BIOME_FALLBACK_761_TO_760,
        fallback_id_760=None)
    check("biome", u, v)
    dmg_fwd, dmg_rev, u, v = build_name_map(
        damage760, damage761,
        fallback_761_to_760=DAMAGE_FALLBACK_761_TO_760,
        fallback_id_760=None)
    check("damage", u, v)
    # 絵画・エンチャントは同一順序のはず。違えば失敗させる。
    for key in ("paintings", "enchantments"):
        if vv760[key] != vv761[key]:
            problems.append((key, ["order differs"], []))
    # エフェクトは minecraft-data で同一（1ベース・33件）。
    eff760 = {x["name"]: x["id"] for x in load(d / "pc" / "1.19.2" / "effects.json")}
    eff761 = {x["name"]: x["id"] for x in load(d / "pc" / "1.19.3" / "effects.json")}
    if eff760 != eff761:
        problems.append(("effects", ["order differs"], []))

    if problems:
        for name, uf, ur in problems:
            print(f"未対応 {name}: 761→760 {uf} / 760→761 {ur}", file=sys.stderr)
        return 1

    maps = {
        "BLOCK_STATE": (bs_fwd, bs_rev, len(vv761["blockstates"]), len(vv760["blockstates"])),
        "ITEM": (item_fwd, item_rev, len(vv761["items"]), len(vv760["items"])),
        "ENTITY_TYPE": (ent_fwd, ent_rev, len(vv761["entities"]), len(vv760["entities"])),
        "SOUND": (snd_fwd, snd_rev, len(vv761["sounds"]), len(vv760["sounds"])),
        "PARTICLE": (part_fwd, part_rev, len(vv761["particles"]), len(vv760["particles"])),
        "BLOCK": (blk_fwd, blk_rev, len(vv761["blocks"]), len(vv760["blocks"])),
        "BLOCK_ENTITY": (be_fwd, be_rev, len(vv761["blockentities"]), len(vv760["blockentities"])),
        "MENU": (menu_fwd, menu_rev, len(menus761), len(menus760)),
        "BIOME": (biome_fwd, biome_rev, len(biomes761), len(biomes760)),
        "DAMAGE_TYPE": (dmg_fwd, dmg_rev, len(damage761), len(damage760)),
    }
    fallbacks_fwd = {
        "BLOCK_STATE": 1, "ITEM": 1, "ENTITY_TYPE": 67,
        "SOUND": stone_break_760, "PARTICLE": 0, "BLOCK": 1,
        "BLOCK_ENTITY": -1, "MENU": 11, "BIOME": biomes760["plains"],
        "DAMAGE_TYPE": 16,
    }
    stone_break_761 = vv761["sounds"].index("block.stone.break")
    fallbacks_rev = {
        "BLOCK_STATE": 1, "ITEM": 1, "ENTITY_TYPE": 68,
        "SOUND": stone_break_761, "PARTICLE": 0, "BLOCK": 1,
        "BLOCK_ENTITY": -1, "MENU": 11, "BIOME": biomes761["plains"],
        "DAMAGE_TYPE": 16,
    }
    # 760 側の代替先：石のブロック状態 1、石のアイテム 1、ブタ 68、
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
        print(f"{name}: 761→760 entries={n_fwd} ranges={len(fr)} overrides={len(fo)} "
              f"fallback={sum(1 for x in fwd if x == fb)} / "
              f"760→761 entries={n_rev} ranges={len(rr)} overrides={len(ro)} "
              f"fallback={sum(1 for x in rev if x == fallbacks_rev[name])}")

    if args.json:
        out = {name: {"fwd": fwd, "rev": rev}
               for name, (fwd, rev, _, _) in maps.items()}
        args.json.write_text(json.dumps(out, indent=1) + "\n", encoding="utf-8")
        print(f"JSON を {args.json} に書き出した。")

    header = """package io.detergentmc.protocol;

/**
 * 760（1.19.1／1.19.2）のレジストリ対応表（区間＋上書きの生の表）。
 *
 * <p>{@link RegistryRemapper} から分離した。理由はバイトコードの制限：
 * 表を RegistryRemapper に置くと静的初期化子（{@code <clinit>}）が
 * 64KB を超えてコンパイルできなくなる（"code too large"）ため。
 * このクラスの表は {@link RegistryRemapper} から static import で
 * 同じ名前のまま参照する。
 *
 * <p>表の内容と検証は {@code detergentmc-protocol/tools/} を参照：
 * 生成は {@code gen-760-tables.py}、検証は {@code verify-760-tables.py}。
 * 情報源は ViaVersion Mappings の mapping-1.19.json（760）と
 * mapping-1.19.3.json（761）で、名前で突き合わせた。ViaVersion には
 * 1.19.1／1.19.2 の mapping ファイルが無いが、minecraft-data で
 * 1.19 と 1.19.2 の blocks／items／entities／biomes が同一順序・
 * 同一内容であることを確認済みのため、mapping-1.19.json を 760 の
 * 情報源として使う。ブロック状態はプロパティを1個だけ落とす／足す
 * 一致も使う。1.19.3 の新要素の代替は次の通り：竹系→オーク系、
 * 吊り看板→同じ木の通常の看板、chiseled_bookshelf→bookshelf、
 * piglin_head→zombie_head、ラクダのスポーンエッグ→馬、
 * エンダードラゴン→ドラゴンの卵、アイアンゴーレム→鉄インゴット、
 * スノーゴーレム→雪玉、ウィザー→ネザースター、竹のいかだ→
 * オークのボート。音は竹系→木／木製ドア類の音へ、chiseled_bookshelf・
 * hanging_sign→木の音へ、note_block の imitate 6 件→harp へ、
 * ラクダ→馬の音へ。ブロックエンティティの chiseled_bookshelf／
 * hanging_sign は落とす（-1）。エンティティのラクダ→馬。メニューは
 * 760 と 761 で同一。ダメージ種別は 760・761 どちらの codec にも
 * 存在しない（1.19.4 で追加）。対応表は 762 の 42 件の恒等写像として
 * 作るがワイヤ上の呼び出し元は無い。絵画・エンチャント・エフェクト・
 * パーティクルは 760 と 761 で同一のため恒等写像。バイオームは 63 件
 * すべて同名だが順序が違う（plains は 760=1、761=38）ため名前で
 * 突き合わせた。
 */
final class RegistryTables760 {

    private RegistryTables760() {
    }

"""
    parts = [header]
    for name, (fwd, rev, n_fwd, n_rev) in maps.items():
        fb = fallbacks_fwd[name]
        fr, fo = ranges_overrides(fwd, n_fwd, fb)
        rr, ro = ranges_overrides(rev, n_rev, fallbacks_rev[name])
        parts.append(java_table(f"{name}_760", fr, fo, n_fwd, "761->760"))
        parts.append("")
        parts.append(java_table(f"{name}_760_TO_761", rr, ro, n_rev, "760->761"))
        parts.append("")
    # 音の名前の代替表（761→760。Stop Sound 用。数値表の代替と一致させる）。
    snd_names = json.loads((Path(__file__).parent / "sound-names-761-to-760.json").read_text(encoding="utf-8"))
    lines = ["    /** 音の名前：761 → 760（Stop Sound 用）。数値の合成表と一致させる。 */",
             "    static final String[][] SOUND_NAME_FALLBACKS_761_TO_760 = {"]
    for src, dst in snd_names:
        lines.append(f'            {{"{src}", "{dst}"}},')
    lines.append("    };")
    parts.append("\n".join(lines))
    parts.append("")
    # 音の名前の代替表（760→761。Stop Sound 用。数値表の代替と一致させる）。
    snd_names_rev = json.loads((Path(__file__).parent / "sound-names-760-to-761.json").read_text(encoding="utf-8"))
    lines = ["    /** 音の名前：760 → 761（Stop Sound 用）。数値の合成表と一致させる。 */",
             "    static final String[][] SOUND_NAME_FALLBACKS_760_TO_761 = {"]
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
