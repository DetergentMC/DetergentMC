#!/usr/bin/env python3
"""404（1.13.2）↔758（1.18.2）のレジストリ対応表を生成する。

情報源（推測で埋めない。すべて名前で突き合わせる）：
  - 404 側のブロック／ブロック状態／アイテムは、実バニラ 1.13.2 jar の
    data-generator ダンプ（~/workspace/mcdata404/datagen1132/reports/）が正。
    ブロック状態は各状態に明示された id（0..8598 連続）で並べる。
    ViaVersion mapping-1.13.json はブロック／状態／アイテムで実データと
    食い違う（死んだサンゴ5種の欠落・sign→oak_sign 等の改名の正規化・
    サンゴとコンデュイットの waterlogged 欠落・bare tnt）ため使わない。
    音（662）とパーティクル（50）だけは datagen にレジストリダンプが
    無いため Via mapping-1.13 を使う。
  - エンティティ（95）は PrismarineJS minecraft-data 1.13.2 の
    entities.json の 'mob' 一覧（＝レジストリ順）。Via mapping-1.14 の
    entities から 1.14 追加7種（cat／fox／panda／pillager／ravager／
    trader_llama／wandering_trader）を引いたものと一致することを確認する
    （mcdata の名前バグ3件は直して比べる）。
  - バイオーム（73）は minecraft-data 1.13.2 biomes.json（id 順）。
  - ブロックエンティティ（25）は 1.14 jar ダンプの 32 種から、末尾に
    追加された 1.14 の 7 種（barrel／smoker／blast_furnace／lectern／
    bell／jigsaw／campfire）を除いたもの（登録順は末尾追加のため）。
  - 758 側は ViaVersion mapping-1.18.json と minecraft-data 1.18
    （498 のときに両 jar ダンプで順序照合済み）。
  - 絵画 26 件は実 1.13.2 jar の定数プールの順序が 1.14 の motive 順と
    完全一致（対応表は作らない）。効果は 404 が 30 件で 758 の先頭
    30 件と一致（bad_omen／hero_of_the_village は 1.14 追加で末尾。
    対応表は作らず、ワイヤ側で 30 以上を落とす）。
  - メニューは 404 にレジストリ自体が無い（Open Window は文字列種別）
    ため対応表は作らない。ダメージ種別は 498 と同じ 42 件の恒等写像。

対応の決め方（優先順）は gen-498-tables.py と同じ。758 の新要素の
代替は gen-498 の代替表を土台にし、その代替先が 404 に無い場合は
1.14 要素向けの代替表（下の *_498_TO_404）でもう一段落とす。
それでも決まらないものがあれば「未対応」として一覧を出し exit 1 で
失敗する（黙って推測しない）。

使い方：
  python3 tools/gen-404-tables.py [--data-dir DIR] [--out FILE] [--json FILE]
  --data-dir を省略すると ~/workspace/mcdata404 を使う。
  --out を省略すると RegistryTables404.java を標準出力に出す。
"""

import argparse
import importlib.util
import json
import sys
from pathlib import Path

# gen-498-tables.py をモジュールとして読み込む（共通部品と代替表の再利用）。
_spec = importlib.util.spec_from_file_location(
    "gen498", Path(__file__).resolve().parent / "gen-498-tables.py")
g = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(g)

parse_state = g.parse_state
fmt_state = g.fmt_state
norm_state = g.norm_state
ranges_overrides = g.ranges_overrides
java_table = g.java_table
build_name_map = g.build_name_map
build_sound_fallback = g.build_sound_fallback

DATA_DIR = Path("/home/hatch/workspace/mcdata404")


def load(p):
    return json.loads(Path(p).read_text(encoding="utf-8"))


# ----------------------------------------------------------------------
# 1.14 要素 → 404 の代替（758 側の代替が 498 で止まった場合の二段目と、
# 1.14 で増えた要素そのものへの代替）
# ----------------------------------------------------------------------
# ブロック：看板は木種別が無く sign／wall_sign へ。石材バリエーションの
# ハーフブロック／階段／壁は 1.14 追加なので近い既存種へ。
BLOCK_FALLBACK_498_TO_404 = {
    "oak_sign": "sign", "oak_wall_sign": "wall_sign",
    "acacia_sign": "sign", "acacia_wall_sign": "wall_sign",
    "birch_sign": "sign", "birch_wall_sign": "wall_sign",
    "dark_oak_sign": "sign", "dark_oak_wall_sign": "wall_sign",
    "jungle_sign": "sign", "jungle_wall_sign": "wall_sign",
    "spruce_sign": "sign", "spruce_wall_sign": "wall_sign",
    "bamboo": "sugar_cane", "bamboo_sapling": "sugar_cane",
    "potted_bamboo": "potted_fern",
    "barrel": "chest", "bell": "iron_block",
    "blast_furnace": "furnace", "smoker": "furnace",
    "campfire": "fire", "soul_campfire": "fire",
    "cartography_table": "crafting_table",
    "fletching_table": "crafting_table", "loom": "crafting_table",
    "smithing_table": "crafting_table", "stonecutter": "crafting_table",
    "composter": "cauldron", "grindstone": "anvil",
    "jigsaw": "structure_block", "lantern": "sea_lantern",
    "soul_lantern": "sea_lantern", "lectern": "bookshelf",
    "scaffolding": "ladder",
    "cornflower": "blue_orchid", "lily_of_the_valley": "white_tulip",
    "wither_rose": "poppy",
    "potted_cornflower": "potted_blue_orchid",
    "potted_lily_of_the_valley": "potted_white_tulip",
    "potted_wither_rose": "potted_poppy",
    "sweet_berry_bush": "grass",
    "smooth_stone_slab": "stone_slab",
    "andesite_slab": "stone_slab", "diorite_slab": "stone_slab",
    "granite_slab": "stone_slab",
    "polished_andesite_slab": "stone_slab",
    "polished_diorite_slab": "stone_slab",
    "polished_granite_slab": "stone_slab",
    "end_stone_brick_slab": "stone_slab",
    "mossy_stone_brick_slab": "stone_brick_slab",
    "mossy_cobblestone_slab": "cobblestone_slab",
    "cut_sandstone_slab": "sandstone_slab",
    "cut_red_sandstone_slab": "red_sandstone_slab",
    "smooth_sandstone_slab": "sandstone_slab",
    "smooth_red_sandstone_slab": "red_sandstone_slab",
    "smooth_quartz_slab": "quartz_slab",
    "red_nether_brick_slab": "nether_brick_slab",
    "andesite_stairs": "stone_brick_stairs",
    "diorite_stairs": "stone_brick_stairs",
    "granite_stairs": "stone_brick_stairs",
    "polished_andesite_stairs": "stone_brick_stairs",
    "polished_diorite_stairs": "stone_brick_stairs",
    "polished_granite_stairs": "stone_brick_stairs",
    "end_stone_brick_stairs": "stone_brick_stairs",
    "mossy_stone_brick_stairs": "stone_brick_stairs",
    "mossy_cobblestone_stairs": "cobblestone_stairs",
    "smooth_sandstone_stairs": "sandstone_stairs",
    "smooth_red_sandstone_stairs": "red_sandstone_stairs",
    "smooth_quartz_stairs": "quartz_stairs",
    "red_nether_brick_stairs": "nether_brick_stairs",
    "stone_stairs": "stone_brick_stairs",
    "andesite_wall": "cobblestone_wall", "diorite_wall": "cobblestone_wall",
    "granite_wall": "cobblestone_wall", "brick_wall": "cobblestone_wall",
    "end_stone_brick_wall": "cobblestone_wall",
    "nether_brick_wall": "cobblestone_wall",
    "prismarine_wall": "cobblestone_wall",
    "red_nether_brick_wall": "cobblestone_wall",
    "red_sandstone_wall": "cobblestone_wall",
    "sandstone_wall": "cobblestone_wall",
    "stone_brick_wall": "cobblestone_wall",
    "mossy_stone_brick_wall": "mossy_cobblestone_wall",
}
# アイテム：ブロックと同名のものはブロック表を使う。それ以外。
ITEM_FALLBACK_498_TO_404 = dict(BLOCK_FALLBACK_498_TO_404)
ITEM_FALLBACK_498_TO_404.update({
    "crossbow": "bow",
    "suspicious_stew": "mushroom_stew",
    "sweet_berries": "apple",
    "campfire": "torch", "soul_campfire": "torch",
    "lantern": "sea_lantern", "soul_lantern": "sea_lantern",
    # 染料：1.13.2 では rose_red／cactus_green／dandelion_yellow の
    # 旧名で、白・黒・青・茶は専用アイテムが無く原料そのものが対応。
    "red_dye": "rose_red", "green_dye": "cactus_green",
    "yellow_dye": "dandelion_yellow",
    "white_dye": "bone_meal", "black_dye": "ink_sac",
    "blue_dye": "lapis_lazuli", "brown_dye": "cocoa_beans",
    # 1.14 モブのスポーンエッグは代替モブのエッグへ。
    "cat_spawn_egg": "ocelot_spawn_egg", "fox_spawn_egg": "wolf_spawn_egg",
    "panda_spawn_egg": "polar_bear_spawn_egg",
    "pillager_spawn_egg": "vindicator_spawn_egg",
    "ravager_spawn_egg": "vindicator_spawn_egg",
    "trader_llama_spawn_egg": "llama_spawn_egg",
    "wandering_trader_spawn_egg": "villager_spawn_egg",
    "leather_horse_armor": "iron_horse_armor",
    # バナーパターンのアイテムは 1.14 追加。404 では模様の元になる
    # アイテム自体へ落とす。
    "flower_banner_pattern": "oxeye_daisy",
    "creeper_banner_pattern": "creeper_head",
    "skull_banner_pattern": "wither_skeleton_skull",
    "mojang_banner_pattern": "enchanted_golden_apple",
    "globe_banner_pattern": "oxeye_daisy",
})
# エンティティ：ネコは 1.14 でオセロットから分かれた。
ENTITY_FALLBACK_498_TO_404 = {
    "cat": "ocelot", "fox": "wolf", "panda": "polar_bear",
    "pillager": "vindicator", "ravager": "vindicator",
    "trader_llama": "llama", "wandering_trader": "villager",
}
# ブロックエンティティ：容器系は近い容器へ、それ以外は落とす（None）。
BLOCK_ENTITY_FALLBACK_498_TO_404 = {
    "barrel": "chest", "smoker": "furnace", "blast_furnace": "furnace",
    "lectern": None, "bell": None, "jigsaw": None, "campfire": None,
}
# バイオーム：タケジャングルは 1.14 追加。
BIOME_FALLBACK_758_TO_404_EXTRA = {
    "bamboo_jungle": "jungle", "bamboo_jungle_hills": "jungle",
}
# パーティクル：1.14 追加分。
PARTICLE_FALLBACK_498_TO_404 = {
    "campfire_cosy_smoke": "large_smoke",
    "campfire_signal_smoke": "large_smoke",
    "composter": "happy_villager",
    "falling_lava": "dripping_lava", "landing_lava": "dripping_lava",
    "falling_water": "dripping_water",
    "falling_honey": "dripping_water",
    "falling_obsidian_tear": "dripping_water",
    "falling_dripstone_lava": "dripping_lava",
    "falling_dripstone_water": "dripping_water",
    "flash": "firework", "sneeze": "item_slime",
}
# 改名（404→758）：看板と石ハーフブロック（1.13.2 の stone_slab は
# ツルツルの方。1.14 で stone_slab は新しい通常の石ハーフになった）。
BLOCK_RENAME_404_TO_758 = dict(g.BLOCK_RENAME_498_TO_758)
BLOCK_RENAME_404_TO_758.update({
    "sign": "oak_sign", "wall_sign": "oak_wall_sign",
    "stone_slab": "smooth_stone_slab",
})
BLOCK_RENAME_758_TO_404 = dict(g.BLOCK_RENAME_758_TO_498)
BLOCK_RENAME_758_TO_404.update({
    "oak_sign": "sign", "oak_wall_sign": "wall_sign",
})
ITEM_RENAME_404_TO_758 = dict(g.ITEM_RENAME_498_TO_758)
ITEM_RENAME_404_TO_758.update({
    "sign": "oak_sign", "stone_slab": "smooth_stone_slab",
    # 染料の旧名（1.14 で *_dye に改名）。
    "rose_red": "red_dye", "cactus_green": "green_dye",
    "dandelion_yellow": "yellow_dye",
})
ITEM_RENAME_758_TO_404 = dict(g.ITEM_RENAME_758_TO_498)
ITEM_RENAME_758_TO_404.update({
    "oak_sign": "sign",
    "red_dye": "rose_red", "green_dye": "cactus_green",
    "yellow_dye": "dandelion_yellow",
})

# ノートブロックの楽器（1.14 追加分）は既存の楽器へ（値の置換）。
NOTE_VALUE_SUBS_758_TO_404 = {
    "iron_xylophone": "xylophone", "cow_bell": "bell",
    "didgeridoo": "flute", "bit": "hat", "banjo": "guitar",
    "pling": "harp",
}


def resolve_fallbacks(fallback_map, target_names, extra):
    """758→498 の代替表を 404 まで解決する。代替先が 404 にあれば
    そのまま、1.14 要素なら extra（498→404 代替）でもう一段落とす。
    解決できないキーは落とす（呼び出し側の未対応検出に任せる）。"""
    out = {}
    targets = set(target_names)
    for src, dst in fallback_map.items():
        if dst is None:
            out[src] = None
        elif dst in targets:
            out[src] = dst
        elif dst in extra and extra[dst] in targets:
            out[src] = extra[dst]
    return out


def build_block_state_maps_404(states404, states758, def404, def758,
                               fallback_758_to_404):
    """ブロック状態の対応表（両方向）。gen-498 の手順に、ノート
    ブロックの楽器の値置換を足したもの。"""
    states404 = [norm_state(s) for s in states404]
    states758 = [norm_state(s) for s in states758]
    idx404 = {s: i for i, s in enumerate(states404)}
    idx758 = {s: i for i, s in enumerate(states758)}

    def drop_one(state, target_idx):
        name, props = parse_state(state)
        for drop in sorted(props):
            rest = {k: v for k, v in props.items() if k != drop}
            cand = fmt_state(name, rest)
            if cand in target_idx:
                return target_idx[cand]
        return None

    def add_one(state, by_name):
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

    def note_sub(state, target_idx):
        name, props = parse_state(state)
        if name != "note_block" or "instrument" not in props:
            return None
        inst = props["instrument"]
        if inst not in NOTE_VALUE_SUBS_758_TO_404:
            return None
        cand = fmt_state(name, {**props,
                                "instrument": NOTE_VALUE_SUBS_758_TO_404[inst]})
        return target_idx.get(cand)

    by_name_758 = {}
    for s, i in idx758.items():
        n, p = parse_state(s)
        by_name_758.setdefault(n, []).append((p, i))

    fwd = []
    unmapped_fwd = []
    for s in states758:
        if s in idx404:
            fwd.append(idx404[s])
            continue
        name, props = parse_state(s)
        special = g.wall_state_to_498(s) or g.jigsaw_state_to_498(s)
        if special is not None and special in idx404:
            fwd.append(idx404[special])
            continue
        if name == "cauldron" and not props:
            fwd.append(idx404["cauldron[level=0]"])
            continue
        if name in g.CAULDRON_758_TO_498:
            cand = fmt_state(g.CAULDRON_758_TO_498[name], props)
            if cand in idx404:
                fwd.append(idx404[cand])
                continue
        if name in BLOCK_RENAME_758_TO_404:
            cand = fmt_state(BLOCK_RENAME_758_TO_404[name], props)
            if cand in idx404:
                fwd.append(idx404[cand])
                continue
        v = note_sub(s, idx404)
        if v is not None:
            fwd.append(v)
            continue
        v = drop_one(s, idx404)
        if v is not None:
            fwd.append(v)
            continue
        fb = fallback_758_to_404.get(name)
        if fb is not None and fb in def404:
            fwd.append(def404[fb])
            continue
        unmapped_fwd.append(s)
        fwd.append(-1)
    rev = []
    unmapped_rev = []
    for s in states404:
        if s in idx758:
            rev.append(idx758[s])
            continue
        special = g.wall_state_to_758(s) or g.cauldron_state_to_758(s)
        if special is not None and special in idx758:
            rev.append(idx758[special])
            continue
        name, _ = parse_state(s)
        if name in BLOCK_RENAME_404_TO_758:
            cand = fmt_state(BLOCK_RENAME_404_TO_758[name], parse_state(s)[1])
            if cand in idx758:
                rev.append(idx758[cand])
                continue
        v = add_one(s, by_name_758)
        if v is not None:
            rev.append(v)
            continue
        unmapped_rev.append(s)
        rev.append(-1)
    return fwd, rev, unmapped_fwd, unmapped_rev


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--data-dir", type=Path, default=DATA_DIR)
    parser.add_argument("--out", type=Path, default=None)
    parser.add_argument("--json", type=Path, default=None)
    args = parser.parse_args()
    d = args.data_dir

    vv758 = load(d / "mapping-1.18.json")
    vv113 = load(d / "mapping-1.13.json")
    vv114 = load(d / "mapping-1.14.json")
    blocks758_mc = load(d / "pc" / "1.18" / "blocks.json")
    biomes404 = {b["name"]: b["id"] for b in load(d / "pc" / "1.13.2" / "biomes.json")}
    biomes758 = {b["name"]: b["id"] for b in load(d / "pc" / "1.18" / "biomes.json")}

    # --- 404 側リストの構築（実データ） ---
    dg_blocks = load(d / "datagen1132" / "reports" / "blocks.json")
    blocks404 = []
    states404 = [None] * 8599
    def404 = {}
    last_max = -1
    for key, blk in dg_blocks.items():
        name = key.split(":", 1)[1]
        blocks404.append(name)
        ids = [st["id"] for st in blk["states"]]
        assert min(ids) > last_max, f"ブロック {name} の状態IDが登録順でない"
        last_max = max(ids)
        for st in blk["states"]:
            s = fmt_state(name, {k: str(v) for k, v in
                                 st.get("properties", {}).items()})
            assert states404[st["id"]] is None
            states404[st["id"]] = s
            if st.get("default"):
                def404[name] = st["id"]
    assert all(s is not None for s in states404)
    assert len(blocks404) == 598 and len(def404) == 598
    print(f"404: ブロック {len(blocks404)} 種・状態 {len(states404)} 件を "
          f"実 jar ダンプから取得（状態ID 0..8598 連続・登録順を確認）。")

    dg_items = load(d / "datagen1132" / "reports" / "items.json")
    items404 = [k.split(":", 1)[1] for k, _ in
                sorted(dg_items.items(), key=lambda kv: kv[1]["protocol_id"])]
    assert len(items404) == 790
    print(f"404: アイテム {len(items404)} 件を実 jar ダンプから取得。")

    mc_ents = load(d / "pc" / "1.13.2" / "entities.json")
    ent_rename = {"fireworks_rocket": "firework_rocket",
                  "commandblock_minecart": "command_block_minecart",
                  "iron_golem}": "iron_golem"}
    entities404 = [ent_rename.get(e["name"], e["name"])
                   for e in mc_ents if e["type"] == "mob"]
    added114 = {"cat", "fox", "panda", "pillager", "ravager",
                "trader_llama", "wandering_trader"}
    expect = [n for n in vv114["entities"] if n not in added114]
    # 顔ぶれは 1.14 から追加7種を引いたものと一致する。順序もほぼ同じ
    # だが、trident だけは 1.13.2 では最後（94）に登録されていて
    # 1.14 で 78 に繰り上がっている（実サーバーのプローブでも
    # zombie=87／villager=79／slime=64／elder_guardian=15 を実測確認）。
    assert set(entities404) == set(expect)
    assert entities404[-1] == "trident"
    assert [n for n in entities404 if n != "trident"] == \
        [n for n in expect if n != "trident"]
    print(f"404: エンティティ {len(entities404)} 件（1.14 の登録順から "
          f"1.14 追加7種を引いたものと一致。trident の位置だけ相違）。")

    sounds404 = vv113["sounds"]
    particles404 = vv113["particles"]
    assert len(sounds404) == 662 and len(particles404) == 50
    print(f"404: 音 {len(sounds404)} 件・パーティクル {len(particles404)} 件"
          f"（Via mapping-1.13。datagen にダンプが無いカテゴリ）。")

    reg114 = load(d / "datagen114" / "reports" / "registries.json")
    be114 = [k.split(":", 1)[1] for k in
             reg114["minecraft:block_entity_type"]["entries"]]
    be_added114 = {"barrel", "smoker", "blast_furnace", "lectern",
                   "bell", "jigsaw", "campfire"}
    assert set(be114[-7:]) == be_added114, be114[-7:]
    be404 = be114[:-7]
    be758 = vv758["blockentities"]
    assert len(be404) == 25 and len(be758) == 34
    print(f"ブロックエンティティ 404={len(be404)}／758={len(be758)}。")

    def758 = {b["name"]: b["defaultState"] for b in blocks758_mc}
    states758 = vv758["blockstates"]
    for b in blocks758_mc:
        assert parse_state(norm_state(states758[b["defaultState"]]))[0] == b["name"]

    problems = []

    def check(name, uf, ur):
        if uf or ur:
            problems.append((name, uf, ur))

    # --- ブロック状態 ---
    fb_bs_758_to_404 = resolve_fallbacks(
        g.BLOCK_FALLBACK_758_TO_498, blocks404, BLOCK_FALLBACK_498_TO_404)
    fb_bs_758_to_404.update(
        {k: v for k, v in BLOCK_FALLBACK_498_TO_404.items()
         if k not in fb_bs_758_to_404})
    bs_fwd, bs_rev, u, v = build_block_state_maps_404(
        states404, states758, def404, def758, fb_bs_758_to_404)
    check("blockstate", u, v)

    # --- アイテム ---
    fb_item = resolve_fallbacks(
        g.ITEM_FALLBACK_758_TO_498, items404, ITEM_FALLBACK_498_TO_404)
    fb_item.update({k: v for k, v in ITEM_FALLBACK_498_TO_404.items()
                    if k in items404 or k not in fb_item})
    item_fwd, item_rev, u, v = build_name_map(
        items404, vv758["items"],
        rename_498_to_758=ITEM_RENAME_404_TO_758,
        rename_758_to_498=ITEM_RENAME_758_TO_404,
        fallback_758_to_498=fb_item,
        fallback_id_498=1)
    check("item", u, v)

    # --- エンティティ ---
    fb_ent = resolve_fallbacks(
        g.ENTITY_FALLBACK_758_TO_498, entities404, ENTITY_FALLBACK_498_TO_404)
    fb_ent.update(ENTITY_FALLBACK_498_TO_404)
    ent_fwd, ent_rev, u, v = build_name_map(
        entities404, vv758["entities"],
        rename_498_to_758=g.ENTITY_RENAME_498_TO_758,
        rename_758_to_498=g.ENTITY_RENAME_758_TO_498,
        fallback_758_to_498=fb_ent,
        fallback_id_498=entities404.index("pig"))
    check("entity", u, v)

    # --- 音 ---
    snd_names_rev_map = {}
    idx758_snd_names = set(vv758["sounds"])
    for n in sounds404:
        if n in idx758_snd_names:
            continue
        cand = None
        if n == "entity.parrot.imitate.zombie_pigman":
            cand = "entity.parrot.imitate.piglin"
        elif "zombie_pigman" in n:
            cand = n.replace("zombie_pigman", "zombified_piglin")
        elif n == "music.nether":
            cand = "music.nether.nether_wastes"
        elif n in ("entity.parrot.imitate.enderman",
                   "entity.parrot.imitate.polar_bear",
                   "entity.parrot.imitate.wolf"):
            cand = "entity.parrot.imitate.zombie"
        assert cand is not None and cand in idx758_snd_names, \
            f"404 専用の音の代替が決まらない: {n}"
        snd_names_rev_map[n] = cand
    snd_names_map, snd_unresolved = build_sound_fallback(sounds404, vv758["sounds"])
    # 1.14 で増えた音への個別規則（規則ベースで届かなかった分の補完）。
    set404_snd = set(sounds404)
    extra_sound_rules = {
        "block.amethyst_block.chime": "block.note_block.bell",
        "item.axe.wax_off": "item.axe.strip",
        "block.bamboo.break": "block.wood.break",
        "block.bamboo.fall": "block.wood.fall",
        "block.bamboo.hit": "block.wood.hit",
        "block.bamboo.place": "block.wood.place",
        "block.bamboo.step": "block.wood.step",
        "block.bamboo_sapling.break": "block.grass.break",
        "block.bamboo_sapling.hit": "block.grass.hit",
        "block.bamboo_sapling.place": "block.grass.place",
        "block.barrel.close": "block.chest.close",
        "block.barrel.open": "block.chest.open",
        "block.beehive.drip": "item.bucket.empty",
        "block.beehive.enter": "entity.bat.ambient",
        "block.beehive.exit": "entity.bat.ambient",
        "block.beehive.shear": "entity.sheep.shear",
        "block.beehive.work": "entity.bat.ambient",
        "block.bell.resonate": "block.note_block.bell",
        "block.bell.use": "block.note_block.bell",
        "block.blastfurnace.fire_crackle": "block.furnace.fire_crackle",
        "item.bone_meal.use": "item.hoe.till",
        "item.book.page_turn": "entity.item.pickup",
        "item.book.put": "entity.item.pickup",
        "item.bucket.empty_powder_snow": "item.bucket.empty",
        "item.bucket.fill_powder_snow": "item.bucket.fill",
        "item.bundle.drop_contents": "entity.item.pickup",
        "item.bundle.insert": "entity.item.pickup",
        "item.bundle.remove_one": "entity.item.pickup",
        "block.cake.add_candle": "block.wool.place",
        "block.campfire.crackle": "block.fire.ambient",
        "block.candle.ambient": "block.fire.ambient",
        "block.candle.break": "block.wool.break",
        "block.candle.extinguish": "block.fire.extinguish",
        "block.candle.fall": "block.wool.fall",
        "block.candle.hit": "block.wool.hit",
        "block.candle.place": "block.wool.place",
        "block.candle.step": "block.wool.step",
        "entity.cat.beg_for_food": "entity.cat.ambient",
        "entity.cat.eat": "entity.generic.eat",
        "entity.cat.stray_ambient": "entity.cat.ambient",
        "block.cave_vines.pick_berries": "block.grass.break",
        "block.composter.empty": "block.wood.place",
        "block.composter.fill": "item.hoe.till",
        "block.composter.fill_success": "item.hoe.till",
        "block.composter.ready": "item.hoe.till",
        "block.crop.break": "block.grass.break",
        "block.grindstone.use": "block.anvil.use",
        "entity.donkey.eat": "entity.horse.eat",
        "block.pointed_dripstone.land": "block.stone.break",
        "block.pointed_dripstone.drip_lava": "item.bucket.empty",
        "block.pointed_dripstone.drip_water": "item.bucket.empty",
        "block.pointed_dripstone.drip_lava_into_cauldron": "item.bucket.empty",
        "block.pointed_dripstone.drip_water_into_cauldron": "item.bucket.empty",
        "block.big_dripleaf.tilt_down": "block.wool.place",
        "block.big_dripleaf.tilt_up": "block.wool.place",
        "item.dye.use": "item.bucket.empty",
        "entity.evoker.celebrate": "entity.evoker.ambient",
        "entity.fox.aggro": "entity.wolf.growl",
        "entity.fox.ambient": "entity.wolf.ambient",
        "entity.fox.bite": "entity.wolf.hurt",
        "entity.fox.death": "entity.wolf.death",
        "entity.fox.eat": "entity.generic.eat",
        "entity.fox.hurt": "entity.wolf.hurt",
        "entity.fox.screech": "entity.wolf.whine",
        "entity.fox.sleep": "entity.wolf.ambient",
        "entity.fox.sniff": "entity.wolf.ambient",
        "entity.fox.spit": "entity.llama.spit",
        "item.glow_ink_sac.use": "item.bucket.empty",
        "block.growing_plant.crop": "block.grass.break",
        "block.honey_block.slide": "entity.slime.squish",
        "item.honeycomb.wax_on": "item.axe.strip",
        "item.ink_sac.use": "item.bucket.empty",
        "block.lantern.break": "block.metal.break",
        "block.lantern.fall": "block.metal.fall",
        "block.lantern.hit": "block.metal.hit",
        "block.lantern.place": "block.metal.place",
        "block.lantern.step": "block.metal.step",
        "item.lodestone_compass.lock": "ui.button.click",
        "entity.minecart.inside.underwater": "entity.minecart.inside",
        "entity.mooshroom.convert": "entity.cow.ambient",
        "entity.mooshroom.eat": "entity.cow.ambient",
        "entity.mooshroom.milk": "entity.cow.milk",
        "entity.mooshroom.suspicious_milk": "entity.cow.milk",
        "entity.mule.angry": "entity.horse.angry",
        "entity.mule.eat": "entity.horse.eat",
        "block.nether_wart.break": "block.grass.break",
        "item.nether_wart.plant": "item.hoe.till",
        "item.crop.plant": "item.hoe.till",
        "item.crossbow.hit": "entity.arrow.hit",
        "item.crossbow.loading_end": "entity.arrow.shoot",
        "item.crossbow.loading_middle": "entity.arrow.shoot",
        "item.crossbow.loading_start": "entity.arrow.shoot",
        "item.crossbow.quick_charge_1": "entity.arrow.shoot",
        "item.crossbow.quick_charge_2": "entity.arrow.shoot",
        "item.crossbow.quick_charge_3": "entity.arrow.shoot",
        "item.crossbow.shoot": "entity.arrow.shoot",
        "block.note_block.banjo": "block.note_block.guitar",
        "block.note_block.bit": "block.note_block.hat",
        "block.note_block.cow_bell": "block.note_block.bell",
        "block.note_block.didgeridoo": "block.note_block.flute",
        "block.note_block.iron_xylophone": "block.note_block.xylophone",
        "entity.ocelot.ambient": "entity.cat.ambient",
        "entity.ocelot.death": "entity.cat.death",
        "entity.ocelot.hurt": "entity.cat.hurt",
        "entity.panda.aggressive_ambient": "entity.polar_bear.warning",
        "entity.panda.ambient": "entity.polar_bear.ambient",
        "entity.panda.bite": "entity.polar_bear.hurt",
        "entity.panda.cant_breed": "entity.polar_bear.ambient",
        "entity.panda.death": "entity.polar_bear.death",
        "entity.panda.eat": "entity.generic.eat",
        "entity.panda.hurt": "entity.polar_bear.hurt",
        "entity.panda.pre_sneeze": "entity.polar_bear.ambient",
        "entity.panda.sneeze": "entity.polar_bear.ambient",
        "entity.panda.step": "entity.polar_bear.step",
        "entity.panda.worried_ambient": "entity.polar_bear.ambient",
        "entity.parrot.imitate.guardian": "entity.parrot.imitate.zombie",
        "entity.parrot.imitate.hoglin": "entity.parrot.imitate.zombie",
        "entity.parrot.imitate.panda": "entity.parrot.imitate.zombie",
        "entity.parrot.imitate.pillager": "entity.parrot.imitate.evoker",
        "entity.parrot.imitate.ravager": "entity.parrot.imitate.evoker",
        "entity.pillager.ambient": "entity.vindicator.ambient",
        "entity.pillager.celebrate": "entity.vindicator.ambient",
        "entity.pillager.death": "entity.vindicator.death",
        "entity.pillager.hurt": "entity.vindicator.hurt",
        "entity.player.hurt_freeze": "entity.player.hurt",
        "entity.player.hurt_sweet_berry_bush": "entity.player.hurt",
        "event.raid.horn": "entity.evoker.cast_spell",
        "entity.ravager.ambient": "entity.vindicator.ambient",
        "entity.ravager.attack": "entity.vindicator.hurt",
        "entity.ravager.celebrate": "entity.vindicator.ambient",
        "entity.ravager.death": "entity.vindicator.death",
        "entity.ravager.hurt": "entity.vindicator.hurt",
        "entity.ravager.roar": "entity.vindicator.ambient",
        "entity.ravager.step": "entity.vindicator.ambient",
        "entity.ravager.stunned": "entity.vindicator.hurt",
        "block.respawn_anchor.ambient": "block.fire.ambient",
        "block.respawn_anchor.charge": "item.flintandsteel.use",
        "block.respawn_anchor.deplete": "block.fire.extinguish",
        "block.respawn_anchor.set_spawn": "block.beacon.activate",
        "block.scaffolding.break": "block.ladder.break",
        "block.scaffolding.fall": "block.ladder.fall",
        "block.scaffolding.hit": "block.ladder.hit",
        "block.scaffolding.place": "block.ladder.place",
        "block.scaffolding.step": "block.ladder.step",
        "block.sculk_sensor.clicking": "block.wooden_pressure_plate.click_on",
        "block.sculk_sensor.clicking_stop": "block.wooden_pressure_plate.click_off",
        "entity.skeleton.converted_to_stray": "entity.stray.ambient",
        "particle.soul_escape": "block.fire.extinguish",
        "block.smithing_table.use": "block.anvil.use",
        "block.smoker.smoke": "block.furnace.fire_crackle",
        "entity.snow_golem.shear": "entity.sheep.shear",
        "item.spyglass.use": "entity.item.pickup",
        "item.spyglass.stop_using": "entity.item.pickup",
        "block.sweet_berry_bush.break": "block.grass.break",
        "block.sweet_berry_bush.place": "block.grass.place",
        "block.sweet_berry_bush.pick_berries": "block.grass.break",
        "item.sweet_berries.pick_from_bush": "block.grass.break",
        "ui.cartography_table.take_result": "ui.button.click",
        "ui.loom.select_pattern": "ui.button.click",
        "ui.loom.take_result": "ui.button.click",
        "ui.stonecutter.select_recipe": "ui.button.click",
        "ui.stonecutter.take_result": "ui.button.click",
        "entity.villager.celebrate": "entity.villager.ambient",
        "entity.villager.work_armorer": "entity.villager.ambient",
        "entity.villager.work_butcher": "entity.villager.ambient",
        "entity.villager.work_cartographer": "entity.villager.ambient",
        "entity.villager.work_cleric": "entity.villager.ambient",
        "entity.villager.work_farmer": "entity.villager.ambient",
        "entity.villager.work_fisherman": "entity.villager.ambient",
        "entity.villager.work_fletcher": "entity.villager.ambient",
        "entity.villager.work_leatherworker": "entity.villager.ambient",
        "entity.villager.work_librarian": "entity.villager.ambient",
        "entity.villager.work_mason": "entity.villager.ambient",
        "entity.villager.work_shepherd": "entity.villager.ambient",
        "entity.villager.work_toolsmith": "entity.villager.ambient",
        "entity.villager.work_weaponsmith": "entity.villager.ambient",
        "entity.vindicator.celebrate": "entity.vindicator.ambient",
        "entity.wandering_trader.ambient": "entity.villager.ambient",
        "entity.wandering_trader.death": "entity.villager.death",
        "entity.wandering_trader.disappeared": "entity.villager.ambient",
        "entity.wandering_trader.drink_milk": "entity.generic.drink",
        "entity.wandering_trader.drink_potion": "entity.generic.drink",
        "entity.wandering_trader.hurt": "entity.villager.hurt",
        "entity.wandering_trader.no": "entity.villager.no",
        "entity.wandering_trader.reappeared": "entity.villager.ambient",
        "entity.wandering_trader.trade": "entity.villager.trade",
        "entity.wandering_trader.yes": "entity.villager.yes",
        "entity.witch.celebrate": "entity.witch.ambient",
    }
    # 注：右辺が 404 に無い規則は使わない（自己参照・1.14 同士の連鎖を除く）。
    for src, dst in list(extra_sound_rules.items()):
        if src in snd_names_map:
            continue
        if dst in set404_snd:
            snd_names_map[src] = dst
    idx404_snd = {n: i for i, n in enumerate(sounds404)}
    snd_fwd = []
    for n in vv758["sounds"]:
        if n in idx404_snd:
            snd_fwd.append(idx404_snd[n])
        elif n in snd_names_map:
            snd_fwd.append(idx404_snd[snd_names_map[n]])
        else:
            snd_fwd.append(-1)
    stone_break_404 = sounds404.index("block.stone.break")
    unresolved_names = [vv758["sounds"][i] for i, x in enumerate(snd_fwd) if x < 0]
    snd_fwd = [stone_break_404 if x < 0 else x for x in snd_fwd]
    snd_rev = []
    unmapped_snd_rev = []
    idx758_snd = {n: i for i, n in enumerate(vv758["sounds"])}
    for n in sounds404:
        if n in idx758_snd:
            snd_rev.append(idx758_snd[n])
        elif n in snd_names_rev_map:
            snd_rev.append(idx758_snd[snd_names_rev_map[n]])
        else:
            unmapped_snd_rev.append(n)
            snd_rev.append(-1)
    check("sound", [], unmapped_snd_rev)
    print(f"音の名前対応：{len(snd_names_map)} 件を規則で代替、"
          f"未対応 {len(unresolved_names)} 件は石の破壊音へ"
          f"（{unresolved_names[:12]}）。")

    # --- パーティクル ---
    fb_part = resolve_fallbacks(
        g.PARTICLE_FALLBACK_758_TO_498, particles404, PARTICLE_FALLBACK_498_TO_404)
    fb_part.update(PARTICLE_FALLBACK_498_TO_404)
    part_fwd, part_rev, u, v = build_name_map(
        particles404, vv758["particles"],
        rename_498_to_758=g.PARTICLE_RENAME_498_TO_758,
        rename_758_to_498=g.PARTICLE_RENAME_758_TO_498,
        fallback_758_to_498=fb_part,
        fallback_id_498=0)
    check("particle", u, v)

    # --- ブロック ---
    fb_blk = resolve_fallbacks(
        g.BLOCK_FALLBACK_758_TO_498, blocks404, BLOCK_FALLBACK_498_TO_404)
    fb_blk.update({k: v for k, v in BLOCK_FALLBACK_498_TO_404.items()
                   if k not in fb_blk})
    blk_fwd, blk_rev, u, v = build_name_map(
        blocks404, vv758["blocks"],
        rename_498_to_758=BLOCK_RENAME_404_TO_758,
        rename_758_to_498=BLOCK_RENAME_758_TO_404,
        fallback_758_to_498=fb_blk,
        fallback_id_498=1)
    check("block", u, v)

    # --- ブロックエンティティ ---
    fb_be = dict(g.BLOCK_ENTITY_FALLBACK_758_TO_498)
    fb_be.update(BLOCK_ENTITY_FALLBACK_498_TO_404)
    be_fwd, be_rev, u, v = build_name_map(
        be404, be758,
        fallback_758_to_498=fb_be,
        fallback_id_498=None)
    check("blockentity", u, v)

    # --- バイオーム ---
    fb_biome = dict(g.BIOME_FALLBACK_758_TO_498)
    fb_biome.update(BIOME_FALLBACK_758_TO_404_EXTRA)
    biome_fwd, biome_rev, u, v = build_name_map(
        [n for n, _ in sorted(biomes404.items(), key=lambda kv: kv[1])],
        [n for n, _ in sorted(biomes758.items(), key=lambda kv: kv[1])],
        rename_498_to_758=g.BIOME_RENAME_498_TO_758,
        rename_758_to_498=g.BIOME_RENAME_758_TO_498,
        fallback_758_to_498=fb_biome,
        fallback_498_to_758=g.BIOME_FALLBACK_498_TO_758,
        fallback_id_498=None)
    check("biome", u, v)

    # --- ダメージ種別（42 件の恒等写像。498 と同じ） ---
    dmg = list(range(42))
    dmg_fwd, dmg_rev = dmg, dmg

    if problems:
        for name, uf, ur in problems:
            print(f"未対応 {name}: 758→404 {uf[:20]}（{len(uf)}件） / "
                  f"404→758 {ur[:20]}（{len(ur)}件）", file=sys.stderr)
        return 1

    maps = {
        "BLOCK_STATE": (bs_fwd, bs_rev, len(states758), len(states404)),
        "ITEM": (item_fwd, item_rev, len(vv758["items"]), len(items404)),
        "ENTITY_TYPE": (ent_fwd, ent_rev, len(vv758["entities"]), len(entities404)),
        "SOUND": (snd_fwd, snd_rev, len(vv758["sounds"]), len(sounds404)),
        "PARTICLE": (part_fwd, part_rev, len(vv758["particles"]), len(particles404)),
        "BLOCK": (blk_fwd, blk_rev, len(vv758["blocks"]), len(blocks404)),
        "BLOCK_ENTITY": (be_fwd, be_rev, len(be758), len(be404)),
        "BIOME": (biome_fwd, biome_rev, len(biomes758), len(biomes404)),
        "DAMAGE_TYPE": (dmg_fwd, dmg_rev, 42, 42),
    }
    fallbacks_fwd = {
        "BLOCK_STATE": 1, "ITEM": 1,
        "ENTITY_TYPE": entities404.index("pig"),
        "SOUND": stone_break_404, "PARTICLE": 0, "BLOCK": 1,
        "BLOCK_ENTITY": -1, "BIOME": biomes404["plains"], "DAMAGE_TYPE": 16,
    }
    stone_break_758 = vv758["sounds"].index("block.stone.break")
    fallbacks_rev = {
        "BLOCK_STATE": 1, "ITEM": 1,
        "ENTITY_TYPE": vv758["entities"].index("pig"),
        "SOUND": stone_break_758, "PARTICLE": 0, "BLOCK": 1,
        "BLOCK_ENTITY": -1, "BIOME": biomes758["plains"], "DAMAGE_TYPE": 16,
    }

    for name, (fwd, rev, n_fwd, n_rev) in maps.items():
        fb = fallbacks_fwd[name]
        fr, fo = ranges_overrides(fwd, n_fwd, fb)
        rr, ro = ranges_overrides(rev, n_rev, fallbacks_rev[name])
        print(f"{name}: 758→404 entries={n_fwd} ranges={len(fr)} overrides={len(fo)} "
              f"fallback={sum(1 for x in fwd if x == fb)} / "
              f"404→758 entries={n_rev} ranges={len(rr)} overrides={len(ro)} "
              f"fallback={sum(1 for x in rev if x == fallbacks_rev[name])}")

    if args.json:
        out = {name: {"fwd": fwd, "rev": rev}
               for name, (fwd, rev, _, _) in maps.items()}
        args.json.write_text(json.dumps(out, indent=1) + "\n", encoding="utf-8")
        print(f"JSON を {args.json} に書き出した。")

    header = """package io.detergentmc.protocol;

/**
 * 404（1.13.2）のレジストリ対応表（区間＋上書きの生の表）。
 *
 * <p>{@link RegistryRemapper} から分離した。理由はバイトコードの制限：
 * 表を RegistryRemapper に置くと静的初期化子（{@code <clinit>}）が
 * 64KB を超えてコンパイルできなくなる（"code too large"）ため。
 * このクラスの表は {@link RegistryRemapper} から static import で
 * 同じ名前のまま参照する。
 *
 * <p>表の内容と検証は {@code detergentmc-protocol/tools/} を参照：
 * 生成は {@code gen-404-tables.py}、検証は {@code verify-404-tables.py}。
 * 情報源は、404 側が実バニラ 1.13.2 jar の data-generator ダンプ
 * （ブロック／ブロック状態／アイテム。状態 ID はダンプの明示 id で
 * 並べた）、minecraft-data 1.13.2（エンティティ／バイオーム）、
 * ViaVersion mapping-1.13（音／パーティクル。ダンプが無いカテゴリ）、
 * 758 側は ViaVersion mapping-1.18 と minecraft-data 1.18
 * （498 のときに jar ダンプで順序照合済み）。
 * ViaVersion mapping-1.13 はブロック／状態／アイテムでは実データと
 * 食い違うため使っていない（死んだサンゴの欠落・改名の正規化など）。
 * ブロック状態はプロパティを1個だけ落とす／足す一致も使うほか、
 * 壁（1.16 で bool→low/tall/none＋up）・大釜（1.17 で分割）は
 * 専用の変換を使い、ノートブロックの 1.14 追加楽器は値の置換で
 * 既存の楽器へ落とす。ジグソーは 404 に無いため structure_block へ。
 * 758 の新要素の代替は 498 と同じ方針（深層岩系→石系、真紅・歪んだ
 * 木材系→オーク系、銅系→鉄系、アメジスト系→クォーツ系、
 * ろうそく→松明など）を土台に、代替先が 1.14 要素の場合は
 * さらにもう一段落とす：看板（木種別）→sign、石材バリエーションの
 * ハーフブロック／階段／壁→既存種、タケ→サトウキビ、バレル→
 * チェスト、ランタン→シーランタン、ネコ→オセロット、
 * 襲撃者系→ヴィンディケーター、タケジャングル→ジャングル。
 * 1.13.2 の stone_slab はツルツルした方なので、404→758 では
 * smooth_stone_slab へ改名として扱う（逆は両方とも stone_slab）。
 * 音は family 置換とモブ置換の規則＋個別規則で代替し、決まらない
 * ものは石の破壊音へ。絵画 26 件は実 jar の定数プール順が 758 と
 * 同じため対応表を作らない。効果 30 件は 758 の先頭 30 件と一致
 * するため対応表は作らず、ワイヤ側で 30 以上を落とす。
 * メニューは 404 にレジストリが無いため対応表を作らない
 * （Open Window は文字列種別で別途変換する）。
 * ダメージ種別は 42 件の恒等写像（codec に無いため呼び出し元は無い）。
 * ブロック状態の上書き表だけは件数が多いため、分割クラス
 * RegistryTables404BsFwd*／BsRev* に分けて {@code concat} で
 * つないでいる。
 */
final class RegistryTables404 {

    private RegistryTables404() {
    }

"""
    parts = [header]
    chunk_files = {}
    for name, (fwd, rev, n_fwd, n_rev) in maps.items():
        fb = fallbacks_fwd[name]
        fr, fo = ranges_overrides(fwd, n_fwd, fb)
        rr, ro = ranges_overrides(rev, n_rev, fallbacks_rev[name])
        if name == "BLOCK_STATE":
            def emit_chunks(prefix, overrides):
                names = []
                for ci in range(0, len(overrides), 1200):
                    chunk = overrides[ci:ci + 1200]
                    cname = f"RegistryTables404{prefix}{ci // 1200 + 1}"
                    body = [f"package io.detergentmc.protocol;",
                            "",
                            f"/** 404 のブロック状態の上書き表の分割クラス"
                            f"（{prefix}、{ci // 1200 + 1} 個目）。"
                            f"生成は gen-404-tables.py。 */",
                            f"final class {cname} {{",
                            "",
                            f"    private {cname}() {{",
                            "    }",
                            "",
                            "    static final int[][] V = {"]
                    for oi, o in enumerate(chunk):
                        comma = "," if oi < len(chunk) - 1 else ""
                        body.append(f"            {{{o[0]}, {o[1]}}}{comma}")
                    body.append("    };")
                    body.append("}")
                    chunk_files[cname] = "\n".join(body) + "\n"
                    names.append(f"{cname}.V")
                return names
            fwd_names = emit_chunks("BsFwd", fo)
            rev_names = emit_chunks("BsRev", ro)
            parts.append(java_table(f"{name}_404", fr, [], n_fwd, "758->404")
                         .replace(f"static final int[][] {name}_404_OVERRIDES = {{",
                                  f"static final int[][] {name}_404_OVERRIDES_PLACEHOLDER = {{"))
            parts[-1] = parts[-1].replace(
                f"    static final int[][] {name}_404_OVERRIDES_PLACEHOLDER = {{\n    }};",
                f"    static final int[][] {name}_404_OVERRIDES = concat("
                + ", ".join(fwd_names) + ");")
            parts.append("")
            parts.append(java_table(f"{name}_404_TO_758", rr, [], n_rev, "404->758")
                         .replace(f"static final int[][] {name}_404_TO_758_OVERRIDES = {{",
                                  f"static final int[][] {name}_404_TO_758_OVERRIDES_PLACEHOLDER = {{"))
            parts[-1] = parts[-1].replace(
                f"    static final int[][] {name}_404_TO_758_OVERRIDES_PLACEHOLDER = {{\n    }};",
                f"    static final int[][] {name}_404_TO_758_OVERRIDES = concat("
                + ", ".join(rev_names) + ");")
            parts.append("")
            continue
        parts.append(java_table(f"{name}_404", fr, fo, n_fwd, "758->404"))
        parts.append("")
        parts.append(java_table(f"{name}_404_TO_758", rr, ro, n_rev, "404->758"))
        parts.append("")
    # 音の名前の代替表（758→404。Stop Sound 用）。
    snd_names = sorted(snd_names_map.items())
    snd_names_rev = sorted(snd_names_rev_map.items())
    tools_dir = Path(__file__).resolve().parent
    (tools_dir / "sound-names-758-to-404.json").write_text(
        json.dumps(snd_names, ensure_ascii=False) + "\n", encoding="utf-8")
    (tools_dir / "sound-names-404-to-758.json").write_text(
        json.dumps(snd_names_rev, ensure_ascii=False) + "\n", encoding="utf-8")
    print(f"音の名前の代替表 JSON を書き出した（758→404 は {len(snd_names)} 対、"
          f"404→758 は {len(snd_names_rev)} 対）。")
    lines = ["    /** 音の名前：758 → 404（Stop Sound 用）。数値の合成表と一致させる。 */",
             "    static final String[][] SOUND_NAME_FALLBACKS_758_TO_404 = {"]
    for src, dst in snd_names:
        lines.append(f'            {{"{src}", "{dst}"}},')
    lines.append("    };")
    parts.append("\n".join(lines))
    parts.append("")
    lines = ["    /** 音の名前：404 → 758（Stop Sound 用）。改名と music.nether の細分化。 */",
             "    static final String[][] SOUND_NAME_FALLBACKS_404_TO_758 = {"]
    for src, dst in snd_names_rev:
        lines.append(f'            {{"{src}", "{dst}"}},')
    lines.append("    };")
    parts.append("\n".join(lines))
    parts.append("")

    def names_array(field, names):
        lines = [f"    static final String[] {field} = {{"]
        for i in range(0, len(names), 4):
            chunk = names[i:i + 4]
            lines.append("            " + " ".join(f'"{n}",' for n in chunk))
        lines.append("    };")
        return "\n".join(lines)
    parts.append(names_array("BLOCK_ENTITY_NAMES_404", be404))
    parts.append("")
    parts.append(names_array("BIOME_NAMES_404",
                             [n for n, _ in sorted(biomes404.items(),
                                                   key=lambda kv: kv[1])]))
    parts.append("")
    parts.append("""    /** 分割した上書き表をつなぐ（ブロック状態用）。 */
    static int[][] concat(int[][]... parts) {
        int total = 0;
        for (int[][] part : parts) {
            total += part.length;
        }
        int[][] out = new int[total][];
        int pos = 0;
        for (int[][] part : parts) {
            System.arraycopy(part, 0, out, pos, part.length);
            pos += part.length;
        }
        return out;
    }
""")
    parts.append("}")
    java = "\n".join(parts)
    if args.out:
        args.out.write_text(java, encoding="utf-8")
        print(f"Java を {args.out} に書き出した。")
        for cname, body in chunk_files.items():
            cpath = args.out.parent / f"{cname}.java"
            cpath.write_text(body, encoding="utf-8")
            print(f"分割クラスを {cpath} に書き出した。")
    else:
        print(java)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
