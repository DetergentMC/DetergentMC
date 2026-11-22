#!/usr/bin/env python3
"""498（1.14.4）↔758（1.18.2）のレジストリ対応表を生成する。

情報源（推測で埋めない。すべて名前で突き合わせる）：
  - ViaVersion Mappings（Mojang の data-generator レポート由来。
    リストの index がレジストリ ID）：
      mapping-1.14.json（498）、mapping-1.18.json（758）。
    順序が本物のバニラ 1.14.4／1.18.2 と一致することは、両 jar の
    data-generator ダンプ（reports/registries.json・reports/blocks.json）
    で全カテゴリ確認済み（このスクリプト内でも照合する）。
  - PrismarineJS minecraft-data：data/pc/1.14.4/blocks.json・biomes.json、
    data/pc/1.18/blocks.json・biomes.json（既定状態とバイオーム）。
  - ブロックエンティティ・メニューは jar ダンプの registries.json から取る
    （mapping-1.14.json には blockentities キーが無いため）。
  - 498 と 578（1.15.2）の違いは実 jar 同士の data-generator ダンプ
    比較で確定済み：1.15 の追加要素（ハチ・ハチミツ系のブロック4種／
    アイテム7種／ハチ／音20種／パーティクル4種／beehive ブロック
    エンティティ）は 498 に無いため、758→498 では近い 1.14 要素へ
    代替する。逆に 498 にだけある音（オウムの物真似4種など）は
    498→758 で代替する。メニューは 498 の "cartography" が 758 の
    "cartography_table" に改名されている。
  - ダメージ種別は 498・758 とも codec に無いため、762（1.19.4）の
    42件の恒等写像として作る（758 のときと同じ扱い。ワイヤ上の
    呼び出し元は無い）。

対応の決め方（優先順）：
  1. 名前がそのまま一致する → その ID。
  2. 改名表（grass_path→dirt_path、zombie_pigman→zombified_piglin、
     バイオームの 1.18 改名群、barrier→block_marker など）。
  3. ブロック状態だけ：プロパティを1個だけ落とす／足すと一致する
     → その ID。壁（1.16 で bool→low/tall/none＋up に変更）と
     ジグソー（facing→orientation）と大釜（1.17 で分割）は専用の
     変換を先に試す。
  4. 下の *_FALLBACK 表にある 758 の新要素 → 近い既存要素。
  5. それでも決まらないものがあれば「未対応」として一覧を出し、
     exit 1 で失敗する（黙って推測しない）。

使い方：
  python3 tools/gen-498-tables.py [--data-dir DIR] [--out FILE]
  --data-dir を省略すると /tmp/mcdata を使う（mapping-1.14.json、
  mapping-1.18.json、pc/1.14.4/、pc/1.18/、datagen1144/、datagen1182/、
  pc/1.19.4-loginPacket.json を置くこと）。
  --out を省略すると RegistryTables498.java を標準出力に出す。
"""

import argparse
import json
import sys
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


def norm_state(s):
    """プロパティ順を正規化した状態文字列。"""
    name, props = parse_state(s)
    return fmt_state(name, props)


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
# 改名表
# ----------------------------------------------------------------------
# ブロック：草の道は 1.17 で dirt_path に改名。
BLOCK_RENAME_498_TO_758 = {"grass_path": "dirt_path"}
BLOCK_RENAME_758_TO_498 = {"dirt_path": "grass_path"}
# アイテム：草の道とゾンビピグマンのスポーンエッグ（1.16 で改名）。
ITEM_RENAME_498_TO_758 = {"grass_path": "dirt_path",
                          "zombie_pigman_spawn_egg": "zombified_piglin_spawn_egg"}
ITEM_RENAME_758_TO_498 = {"dirt_path": "grass_path",
                          "zombified_piglin_spawn_egg": "zombie_pigman_spawn_egg"}
# エンティティ：ゾンビピグマンは 1.16 で zombified_piglin に改名。
ENTITY_RENAME_498_TO_758 = {"zombie_pigman": "zombified_piglin"}
ENTITY_RENAME_758_TO_498 = {"zombified_piglin": "zombie_pigman"}
# パーティクル：barrier は 1.18 で block_marker に改名。
PARTICLE_RENAME_498_TO_758 = {"barrier": "block_marker"}
PARTICLE_RENAME_758_TO_498 = {"block_marker": "barrier"}
# バイオーム：1.18 の地形刷新での改名群。
BIOME_RENAME_498_TO_758 = {
    "mountains": "windswept_hills",
    "snowy_tundra": "snowy_plains",
    "gravelly_mountains": "windswept_gravelly_hills",
    "wooded_mountains": "windswept_forest",
    "tall_birch_forest": "old_growth_birch_forest",
    "giant_tree_taiga": "old_growth_pine_taiga",
    "giant_spruce_taiga": "old_growth_spruce_taiga",
    "jungle_edge": "sparse_jungle",
    "shattered_savanna": "windswept_savanna",
    "stone_shore": "stony_shore",
}
BIOME_RENAME_758_TO_498 = {v: k for k, v in BIOME_RENAME_498_TO_758.items()}

# ----------------------------------------------------------------------
# フォールバック表（758 にだけあるものを 498 の近い要素へ）
# ----------------------------------------------------------------------
# ブロック状態・ブロック：1.16（ネザー更新）と 1.17（洞窟と崖）の新要素。
BLOCK_FALLBACK_758_TO_498 = {
    # 深層岩系 → 石系。
    "deepslate": "stone",
    "cobbled_deepslate": "cobblestone",
    "cobbled_deepslate_stairs": "cobblestone_stairs",
    "cobbled_deepslate_slab": "cobblestone_slab",
    "cobbled_deepslate_wall": "cobblestone_wall",
    "polished_deepslate": "stone_bricks",
    "polished_deepslate_stairs": "stone_brick_stairs",
    "polished_deepslate_slab": "stone_brick_slab",
    "polished_deepslate_wall": "stone_brick_wall",
    "deepslate_bricks": "stone_bricks",
    "deepslate_brick_stairs": "stone_brick_stairs",
    "deepslate_brick_slab": "stone_brick_slab",
    "deepslate_brick_wall": "stone_brick_wall",
    "deepslate_tiles": "stone_bricks",
    "deepslate_tile_stairs": "stone_brick_stairs",
    "deepslate_tile_slab": "stone_brick_slab",
    "deepslate_tile_wall": "stone_brick_wall",
    "chiseled_deepslate": "chiseled_stone_bricks",
    "cracked_deepslate_bricks": "stone_bricks",
    "cracked_deepslate_tiles": "stone_bricks",
    "infested_deepslate": "infested_stone",
    "deepslate_coal_ore": "coal_ore",
    "deepslate_iron_ore": "iron_ore",
    "deepslate_copper_ore": "iron_ore",
    "deepslate_gold_ore": "gold_ore",
    "deepslate_redstone_ore": "redstone_ore",
    "deepslate_emerald_ore": "emerald_ore",
    "deepslate_lapis_ore": "lapis_ore",
    "deepslate_diamond_ore": "diamond_ore",
    # 真紅・歪んだ木材系 → オーク系（マングローブのときと同じ方針）。
    "crimson_stem": "oak_log",
    "stripped_crimson_stem": "stripped_oak_log",
    "crimson_hyphae": "oak_wood",
    "stripped_crimson_hyphae": "stripped_oak_wood",
    "warped_stem": "oak_log",
    "stripped_warped_stem": "stripped_oak_log",
    "warped_hyphae": "oak_wood",
    "stripped_warped_hyphae": "stripped_oak_wood",
    "crimson_planks": "oak_planks",
    "warped_planks": "oak_planks",
    "crimson_slab": "oak_slab",
    "warped_slab": "oak_slab",
    "crimson_pressure_plate": "oak_pressure_plate",
    "warped_pressure_plate": "oak_pressure_plate",
    "crimson_fence": "oak_fence",
    "warped_fence": "oak_fence",
    "crimson_trapdoor": "oak_trapdoor",
    "warped_trapdoor": "oak_trapdoor",
    "crimson_fence_gate": "oak_fence_gate",
    "warped_fence_gate": "oak_fence_gate",
    "crimson_stairs": "oak_stairs",
    "warped_stairs": "oak_stairs",
    "crimson_button": "oak_button",
    "warped_button": "oak_button",
    "crimson_door": "oak_door",
    "warped_door": "oak_door",
    "crimson_sign": "oak_sign",
    "warped_sign": "oak_sign",
    "crimson_wall_sign": "oak_wall_sign",
    "warped_wall_sign": "oak_wall_sign",
    # ネザーの地面・植物系。
    "crimson_nylium": "netherrack",
    "warped_nylium": "netherrack",
    "crimson_fungus": "red_mushroom",
    "warped_fungus": "brown_mushroom",
    "potted_crimson_fungus": "potted_red_mushroom",
    "potted_warped_fungus": "potted_brown_mushroom",
    "crimson_roots": "grass",
    "warped_roots": "grass",
    "potted_crimson_roots": "potted_fern",
    "potted_warped_roots": "potted_fern",
    "nether_sprouts": "grass",
    "weeping_vines": "vine",
    "weeping_vines_plant": "vine",
    "twisting_vines": "vine",
    "twisting_vines_plant": "vine",
    "warped_wart_block": "nether_wart_block",
    "shroomlight": "glowstone",
    "soul_soil": "soul_sand",
    "soul_fire": "fire",
    "soul_torch": "torch",
    "soul_wall_torch": "wall_torch",
    "soul_lantern": "lantern",
    "soul_campfire": "campfire",
    "basalt": "stone",
    "polished_basalt": "stone",
    "smooth_basalt": "stone",
    "chain": "iron_bars",
    "target": "hay_block",
    "netherite_block": "diamond_block",
    "ancient_debris": "nether_quartz_ore",
    "crying_obsidian": "obsidian",
    "respawn_anchor": "obsidian",
    "lodestone": "iron_block",
    "nether_gold_ore": "nether_quartz_ore",
    # ブラックストーン系 → 石系。
    "blackstone": "stone",
    "blackstone_stairs": "stone_stairs",
    "blackstone_wall": "cobblestone_wall",
    "blackstone_slab": "stone_slab",
    "polished_blackstone": "stone_bricks",
    "polished_blackstone_bricks": "stone_bricks",
    "cracked_polished_blackstone_bricks": "stone_bricks",
    "chiseled_polished_blackstone": "chiseled_stone_bricks",
    "polished_blackstone_brick_slab": "stone_brick_slab",
    "polished_blackstone_brick_stairs": "stone_brick_stairs",
    "polished_blackstone_brick_wall": "stone_brick_wall",
    "gilded_blackstone": "stone",
    "polished_blackstone_stairs": "stone_brick_stairs",
    "polished_blackstone_slab": "stone_brick_slab",
    "polished_blackstone_pressure_plate": "stone_pressure_plate",
    "polished_blackstone_button": "stone_button",
    "polished_blackstone_wall": "stone_brick_wall",
    "chiseled_nether_bricks": "nether_bricks",
    "cracked_nether_bricks": "nether_bricks",
    "quartz_bricks": "quartz_block",
    # ろうそく → 松明。ろうそくケーキ → ケーキ。
    "candle": "torch",
    "white_candle": "torch",
    "orange_candle": "torch",
    "magenta_candle": "torch",
    "light_blue_candle": "torch",
    "yellow_candle": "torch",
    "lime_candle": "torch",
    "pink_candle": "torch",
    "gray_candle": "torch",
    "light_gray_candle": "torch",
    "cyan_candle": "torch",
    "purple_candle": "torch",
    "blue_candle": "torch",
    "brown_candle": "torch",
    "green_candle": "torch",
    "red_candle": "torch",
    "black_candle": "torch",
    "candle_cake": "cake",
    "white_candle_cake": "cake",
    "orange_candle_cake": "cake",
    "magenta_candle_cake": "cake",
    "light_blue_candle_cake": "cake",
    "yellow_candle_cake": "cake",
    "lime_candle_cake": "cake",
    "pink_candle_cake": "cake",
    "gray_candle_cake": "cake",
    "light_gray_candle_cake": "cake",
    "cyan_candle_cake": "cake",
    "purple_candle_cake": "cake",
    "blue_candle_cake": "cake",
    "brown_candle_cake": "cake",
    "green_candle_cake": "cake",
    "red_candle_cake": "cake",
    "black_candle_cake": "cake",
    # アメジスト系 → クォーツ系。
    "amethyst_block": "quartz_block",
    "budding_amethyst": "quartz_block",
    "amethyst_cluster": "quartz_block",
    "large_amethyst_bud": "quartz_block",
    "medium_amethyst_bud": "quartz_block",
    "small_amethyst_bud": "quartz_block",
    # 凝灰岩・方解石・その他の 1.17 要素。
    "tuff": "andesite",
    "calcite": "diorite",
    "tinted_glass": "glass",
    "powder_snow": "snow_block",
    "powder_snow_cauldron": "cauldron",
    "lava_cauldron": "cauldron",
    "water_cauldron": "cauldron",
    "sculk_sensor": "observer",
    "glow_lichen": "vine",
    "light": "air",
    "azalea_leaves": "oak_leaves",
    "flowering_azalea_leaves": "oak_leaves",
    "azalea": "oak_sapling",
    "flowering_azalea": "oak_sapling",
    "potted_azalea_bush": "potted_oak_sapling",
    "potted_flowering_azalea_bush": "potted_oak_sapling",
    "moss_carpet": "lime_carpet",
    "moss_block": "grass_block",
    "big_dripleaf": "oak_leaves",
    "big_dripleaf_stem": "oak_leaves",
    "small_dripleaf": "fern",
    "hanging_roots": "grass",
    "rooted_dirt": "dirt",
    "spore_blossom": "oak_leaves",
    "cave_vines": "vine",
    "cave_vines_plant": "vine",
    # 銅系 → 鉄系。
    "copper_block": "iron_block",
    "exposed_copper": "iron_block",
    "weathered_copper": "iron_block",
    "oxidized_copper": "iron_block",
    "cut_copper": "iron_block",
    "exposed_cut_copper": "iron_block",
    "weathered_cut_copper": "iron_block",
    "oxidized_cut_copper": "iron_block",
    "cut_copper_stairs": "stone_stairs",
    "exposed_cut_copper_stairs": "stone_stairs",
    "weathered_cut_copper_stairs": "stone_stairs",
    "oxidized_cut_copper_stairs": "stone_stairs",
    "cut_copper_slab": "stone_slab",
    "exposed_cut_copper_slab": "stone_slab",
    "weathered_cut_copper_slab": "stone_slab",
    "oxidized_cut_copper_slab": "stone_slab",
    "waxed_copper_block": "iron_block",
    "waxed_exposed_copper": "iron_block",
    "waxed_weathered_copper": "iron_block",
    "waxed_oxidized_copper": "iron_block",
    "waxed_cut_copper": "iron_block",
    "waxed_exposed_cut_copper": "iron_block",
    "waxed_weathered_cut_copper": "iron_block",
    "waxed_oxidized_cut_copper": "iron_block",
    "waxed_cut_copper_stairs": "stone_stairs",
    "waxed_exposed_cut_copper_stairs": "stone_stairs",
    "waxed_weathered_cut_copper_stairs": "stone_stairs",
    "waxed_oxidized_cut_copper_stairs": "stone_stairs",
    "waxed_cut_copper_slab": "stone_slab",
    "waxed_exposed_cut_copper_slab": "stone_slab",
    "waxed_weathered_cut_copper_slab": "stone_slab",
    "waxed_oxidized_cut_copper_slab": "stone_slab",
    "copper_ore": "iron_ore",
    "lightning_rod": "end_rod",
    "pointed_dripstone": "end_rod",
    "dripstone_block": "stone",
    # 原鉱ブロック → 金属ブロック。
    "raw_iron_block": "iron_block",
    "raw_copper_block": "iron_block",
    "raw_gold_block": "gold_block",
    # 1.15（ハチ）の追加ブロック → 1.14 の近い要素。
    "bee_nest": "oak_log",
    "beehive": "oak_planks",
    "honey_block": "slime_block",
    "honeycomb_block": "orange_terracotta",
}
# アイテム：ブロックの代替を流用するが、壁掛け看板と植木鉢と
# ろうそくケーキはアイテムが無い（ブロックだけ）ため除く。
_ITEM_BLOCK_ONLY = {k for k in BLOCK_FALLBACK_758_TO_498
                    if k.endswith("_wall_sign") or k.startswith("potted_")
                    or k.endswith("_candle_cake") or k == "candle_cake"}
ITEM_FALLBACK_758_TO_498 = {k: v for k, v in BLOCK_FALLBACK_758_TO_498.items()
                            if k not in _ITEM_BLOCK_ONLY}
ITEM_FALLBACK_758_TO_498.update({
    # ネザライト → ダイヤ系。
    "netherite_sword": "diamond_sword",
    "netherite_shovel": "diamond_shovel",
    "netherite_pickaxe": "diamond_pickaxe",
    "netherite_axe": "diamond_axe",
    "netherite_hoe": "diamond_hoe",
    "netherite_helmet": "diamond_helmet",
    "netherite_chestplate": "diamond_chestplate",
    "netherite_leggings": "diamond_leggings",
    "netherite_boots": "diamond_boots",
    "netherite_ingot": "diamond",
    "netherite_scrap": "iron_ingot",
    # 原鉱・インゴット類。
    "raw_iron": "iron_ore",
    "raw_copper": "iron_ore",
    "raw_gold": "gold_ore",
    "copper_ingot": "iron_ingot",
    "amethyst_shard": "quartz",
    "glow_ink_sac": "ink_sac",
    "glow_berries": "sweet_berries",
    "powder_snow_bucket": "bucket",
    "bundle": "chest",
    "spyglass": "compass",
    "warped_fungus_on_a_stick": "carrot_on_a_stick",
    "axolotl_bucket": "cod_bucket",
    # スポーンエッグ。
    "axolotl_spawn_egg": "tropical_fish_spawn_egg",
    "glow_squid_spawn_egg": "squid_spawn_egg",
    "goat_spawn_egg": "sheep_spawn_egg",
    "hoglin_spawn_egg": "pig_spawn_egg",
    "piglin_spawn_egg": "zombie_pigman_spawn_egg",
    "piglin_brute_spawn_egg": "zombie_pigman_spawn_egg",
    "strider_spawn_egg": "pig_spawn_egg",
    "zoglin_spawn_egg": "zombie_spawn_egg",
    "glow_item_frame": "item_frame",
    "music_disc_otherside": "music_disc_cat",
    "music_disc_pigstep": "music_disc_cat",
    "piglin_banner_pattern": "creeper_banner_pattern",
    "light": "torch",
    # 1.15（ハチ）の追加アイテム（ブロック系はブロック代替を流用）。
    "bee_spawn_egg": "parrot_spawn_egg",
    "honeycomb": "sugar",
    "honey_bottle": "potion",
})
# エンティティ：新エンティティ → 498 にある近いエンティティ。
ENTITY_FALLBACK_758_TO_498 = {
    "bee": "parrot",
    "axolotl": "tropical_fish",
    "glow_item_frame": "item_frame",
    "glow_squid": "squid",
    "goat": "sheep",
    "hoglin": "pig",
    "marker": "area_effect_cloud",
    "piglin": "zombie_pigman",
    "piglin_brute": "zombie_pigman",
    "strider": "pig",
    "zoglin": "zombie",
}
# パーティクル（758 → 498）。
PARTICLE_FALLBACK_758_TO_498 = {
    "dust_color_transition": "dust",
    "soul_fire_flame": "flame",
    "soul": "end_rod",
    "vibration": "enchant",
    "falling_spore_blossom": "mycelium",
    "spore_blossom_air": "mycelium",
    "ash": "smoke",
    "crimson_spore": "mycelium",
    "warped_spore": "mycelium",
    "dripping_obsidian_tear": "dripping_water",
    "falling_obsidian_tear": "falling_water",
    "landing_obsidian_tear": "splash",
    "reverse_portal": "portal",
    "white_ash": "smoke",
    "small_flame": "flame",
    "snowflake": "poof",
    "dripping_dripstone_lava": "dripping_lava",
    "falling_dripstone_lava": "falling_lava",
    "dripping_dripstone_water": "dripping_water",
    "falling_dripstone_water": "falling_water",
    "glow_squid_ink": "squid_ink",
    "glow": "end_rod",
    "wax_on": "happy_villager",
    "wax_off": "happy_villager",
    "electric_spark": "firework",
    "scrape": "crit",
    # 1.15（ハチミツ）の追加パーティクル。
    "dripping_honey": "dripping_water",
    "falling_honey": "falling_water",
    "landing_honey": "splash",
    "falling_nectar": "dripping_water",
}
# ブロックエンティティ：sculk_sensor と beehive（1.15 追加）は代替なし
# （-1 = パケットごと落とす）。
BLOCK_ENTITY_FALLBACK_758_TO_498 = {"sculk_sensor": None, "beehive": None}
# メニュー：鍛冶台（1.16 で専用メニュー追加）→ 金床。
MENU_FALLBACK_758_TO_498 = {"smithing": "anvil"}
# メニュー：地図台は 1.15 で cartography → cartography_table に改名。
MENU_RENAME_498_TO_758 = {"cartography": "cartography_table"}
MENU_RENAME_758_TO_498 = {"cartography_table": "cartography"}
# バイオーム（758 → 498）。改名で済むものは改名表にある。
BIOME_FALLBACK_758_TO_498 = {
    "meadow": "plains",
    "grove": "snowy_taiga",
    "snowy_slopes": "snowy_mountains",
    "frozen_peaks": "snowy_mountains",
    "jagged_peaks": "mountains",
    "stony_peaks": "mountains",
    "dripstone_caves": "plains",
    "lush_caves": "jungle",
    "warped_forest": "nether_wastes",
    "crimson_forest": "nether_wastes",
    "soul_sand_valley": "nether_wastes",
    "basalt_deltas": "nether_wastes",
    "wooded_badlands": "wooded_badlands_plateau",
}
# バイオーム（498 → 758）。1.18 で消えた hills／変種は素のバイオームへ。
BIOME_FALLBACK_498_TO_758 = {
    "snowy_mountains": "snowy_plains",
    "mushroom_field_shore": "mushroom_fields",
    "desert_hills": "desert",
    "wooded_hills": "forest",
    "taiga_hills": "taiga",
    "mountain_edge": "windswept_hills",
    "jungle_hills": "jungle",
    "birch_forest_hills": "birch_forest",
    "snowy_taiga_hills": "snowy_taiga",
    "giant_tree_taiga_hills": "old_growth_pine_taiga",
    "wooded_badlands_plateau": "wooded_badlands",
    "badlands_plateau": "badlands",
    "deep_warm_ocean": "warm_ocean",
    "desert_lakes": "desert",
    "taiga_mountains": "taiga",
    "swamp_hills": "swamp",
    "modified_jungle": "jungle",
    "modified_jungle_edge": "sparse_jungle",
    "tall_birch_hills": "birch_forest",
    "dark_forest_hills": "dark_forest",
    "snowy_taiga_mountains": "snowy_taiga",
    "giant_spruce_taiga_hills": "old_growth_spruce_taiga",
    "modified_gravelly_mountains": "windswept_gravelly_hills",
    "shattered_savanna_plateau": "windswept_savanna",
    "modified_wooded_badlands_plateau": "wooded_badlands",
    "modified_badlands_plateau": "badlands",
    "bamboo_jungle_hills": "bamboo_jungle",
}
# 音のファミリ置換（758 → 498）。block.<family>.* の family を
# 498 にある音の family に置き換えて、同じ接尾辞で存在を確認する。
SOUND_FAMILY_758_TO_498 = {
    "deepslate": ["stone"], "deepslate_bricks": ["stone"],
    "deepslate_tiles": ["stone"], "calcite": ["stone"], "tuff": ["stone"],
    "basalt": ["stone"], "smooth_basalt": ["stone"],
    "blackstone": ["stone"], "polished_blackstone": ["stone"],
    "polished_blackstone_bricks": ["stone"], "gilded_blackstone": ["stone"],
    "dripstone_block": ["stone"], "pointed_dripstone": ["stone"],
    "netherite_block": ["metal"], "ancient_debris": ["metal"],
    "lodestone": ["stone"], "crying_obsidian": ["stone"],
    "respawn_anchor": ["stone"], "quartz_bricks": ["stone"],
    "nether_gold_ore": ["stone"], "copper": ["metal"],
    "cut_copper": ["metal"], "amethyst_block": ["glass"],
    "amethyst_cluster": ["glass"], "candle": ["lantern"],
    "sculk_sensor": ["stone"], "powder_snow": ["snow"],
    "honey_block": ["slime_block"],
    "tinted_glass": ["glass"], "moss": ["grass"], "moss_carpet": ["grass"],
    "azalea": ["grass"], "azalea_leaves": ["grass"],
    "flowering_azalea_leaves": ["grass"], "cave_vines": ["grass"],
    "spore_blossom": ["grass"], "big_dripleaf": ["grass"],
    "small_dripleaf": ["grass"], "hanging_roots": ["grass"],
    "rooted_dirt": ["grass"], "glow_lichen": ["grass"],
    "stem": ["wood"], "nylium": ["grass"], "fungus": ["wood"],
    "roots": ["grass"], "nether_sprouts": ["grass"],
    "shroomlight": ["wood"], "weeping_vines": ["grass"],
    "twisting_vines": ["grass"], "wart_block": ["nether_wart", "wood"],
    "soul_soil": ["sand"], "chain": ["metal"],
    "lightning_rod": ["metal"], "target": ["grass"],
    # 498 ではまだ専用の音が無い／別 family で鳴っていたもの。
    "bone_block": ["stone"], "nether_bricks": ["stone"],
    "netherrack": ["stone"], "nether_ore": ["stone"],
    "soul_sand": ["sand"], "vine": ["grass"],
    "flowering_azalea": ["grass"], "polished_deepslate": ["stone"],
    "large_amethyst_bud": ["glass"], "medium_amethyst_bud": ["glass"],
    "small_amethyst_bud": ["glass"],
}


# ----------------------------------------------------------------------
# ブロック状態の専用変換（プロパティの形自体が変わったもの）
# ----------------------------------------------------------------------
def wall_state_to_758(state):
    """498 の壁（bool の東西南北）→ 758 の壁（low/tall/none）。
    498 の壁は「低さ」の概念が無く、接続は low 相当として扱う。
    up は両方とも bool でそのまま。"""
    name, props = parse_state(state)
    if not name.endswith("_wall"):
        return None
    if not {"east", "north", "south", "west"} <= set(props):
        return None
    out = {}
    for k in ("east", "north", "south", "west"):
        out[k] = "low" if props[k] == "true" else "none"
    if "up" in props:
        out["up"] = props["up"]
    if "waterlogged" in props:
        out["waterlogged"] = props["waterlogged"]
    return fmt_state(name, out)


def wall_state_to_498(state):
    """758 の壁（low/tall/none）→ 498 の壁（bool）。
    low も tall も「接続あり」として true にする。up は両方とも
    bool でそのまま。"""
    name, props = parse_state(state)
    if not name.endswith("_wall"):
        return None
    if not {"east", "north", "south", "west", "up"} <= set(props):
        return None
    out = {}
    for k in ("east", "north", "south", "west"):
        out[k] = "false" if props[k] == "none" else "true"
    out["up"] = props["up"]
    if "waterlogged" in props:
        out["waterlogged"] = props["waterlogged"]
    return fmt_state(name, out)


def jigsaw_state_to_758(state):
    """498 のジグソー（facing）→ 758 のジグソー（orientation）。
    水平はそのまま、上下は south 向きの接合として扱う。"""
    name, props = parse_state(state)
    if name != "jigsaw" or set(props) != {"facing"}:
        return None
    f = props["facing"]
    ori = {"north": "north_up", "south": "south_up", "east": "east_up",
           "west": "west_up", "up": "up_south", "down": "down_south"}[f]
    return fmt_state("jigsaw", {"orientation": ori})


def cauldron_state_to_758(state):
    """498 の大釜（level）→ 758 の大釜系。level=0 は空の大釜、
    level>0 は水の大釜（同じ level）。"""
    name, props = parse_state(state)
    if name != "cauldron":
        return None
    level = props.get("level", "0")
    if level == "0":
        return "cauldron"
    return fmt_state("water_cauldron", {"level": level})


def jigsaw_state_to_498(state):
    """758 のジグソー（orientation）→ 498 のジグソー（facing）。
    *_up は水平の向き、up_*／down_* は上下に倒す。"""
    name, props = parse_state(state)
    if name != "jigsaw" or set(props) != {"orientation"}:
        return None
    ori = props["orientation"]
    if ori.startswith("up_"):
        facing = "up"
    elif ori.startswith("down_"):
        facing = "down"
    elif ori.endswith("_up"):
        facing = ori[:-3]
    else:
        facing = ori
    return fmt_state("jigsaw", {"facing": facing})


# 大釜系（758）→ 498 の大釜。プロパティはそのまま（lava／粉雪の
# level は水の level として残る。内容の違いは制限として明記）。
CAULDRON_758_TO_498 = {"water_cauldron": "cauldron",
                       "lava_cauldron": "cauldron",
                       "powder_snow_cauldron": "cauldron"}


def build_block_state_maps(vv498, vv758, blocks498, blocks758):
    """ブロック状態の対応表（両方向）。プロパティ順の違いを正規化して比べる。"""
    states498 = [norm_state(s) for s in vv498["blockstates"]]
    states758 = [norm_state(s) for s in vv758["blockstates"]]
    idx498 = {s: i for i, s in enumerate(states498)}
    idx758 = {s: i for i, s in enumerate(states758)}
    def498 = {b["name"]: b["defaultState"] for b in blocks498}
    def758 = {b["name"]: b["defaultState"] for b in blocks758}
    # 既定状態の番号が、この表の並びと一致することを確認する。
    for b in blocks498:
        s = states498[b["defaultState"]]
        assert parse_state(s)[0] == b["name"], (b["name"], s)
    for b in blocks758:
        s = states758[b["defaultState"]]
        assert parse_state(s)[0] == b["name"], (b["name"], s)

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
        候補が複数あるときは bool の false を優先する。"""
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

    by_name_758 = {}
    for s, i in idx758.items():
        n, p = parse_state(s)
        by_name_758.setdefault(n, []).append((p, i))

    fwd = []  # 758 -> 498
    unmapped_fwd = []
    for s in states758:
        if s in idx498:
            fwd.append(idx498[s])
            continue
        name, props = parse_state(s)
        # 壁とジグソーはプロパティの形が変わったため専用変換を先に試す。
        special = wall_state_to_498(s) or jigsaw_state_to_498(s)
        if special is not None and special in idx498:
            fwd.append(idx498[special])
            continue
        # 758 の素の大釜はプロパティ無し。498 では level=0 が対応する。
        if name == "cauldron" and not props:
            fwd.append(idx498["cauldron[level=0]"])
            continue
        # 大釜系は名前を変えて同じプロパティで探す。
        if name in CAULDRON_758_TO_498:
            cand = fmt_state(CAULDRON_758_TO_498[name], props)
            if cand in idx498:
                fwd.append(idx498[cand])
                continue
        # 改名（dirt_path → grass_path）。
        if name in BLOCK_RENAME_758_TO_498:
            cand = fmt_state(BLOCK_RENAME_758_TO_498[name], props)
            if cand in idx498:
                fwd.append(idx498[cand])
                continue
        v = drop_one(s, idx498)
        if v is not None:
            fwd.append(v)
            continue
        fb = BLOCK_FALLBACK_758_TO_498.get(name)
        if fb is not None and fb in def498:
            fwd.append(def498[fb])
            continue
        unmapped_fwd.append(s)
        fwd.append(-1)
    rev = []  # 498 -> 758
    unmapped_rev = []
    for s in states498:
        if s in idx758:
            rev.append(idx758[s])
            continue
        special = wall_state_to_758(s) or jigsaw_state_to_758(s) \
            or cauldron_state_to_758(s)
        if special is not None and special in idx758:
            rev.append(idx758[special])
            continue
        name, _ = parse_state(s)
        if name in BLOCK_RENAME_498_TO_758:
            cand = fmt_state(BLOCK_RENAME_498_TO_758[name], parse_state(s)[1])
            if cand in idx758:
                rev.append(idx758[cand])
                continue
        v = add_one(s, by_name_758)
        if v is not None:
            rev.append(v)
            continue
        fb = BLOCK_FALLBACK_758_TO_498.get(name)
        if fb is not None and fb in def758:
            rev.append(def758[fb])
            continue
        unmapped_rev.append(s)
        rev.append(-1)
    return fwd, rev, unmapped_fwd, unmapped_rev


def build_name_map(list498, list758, rename_498_to_758=None,
                   fallback_758_to_498=None, fallback_id_498=None,
                   rename_758_to_498=None, fallback_498_to_758=None):
    """名前リストの対応表（両方向）。fwd は 758→498、rev は 498→758。"""
    idx498 = {n: i for i, n in enumerate(list498)}
    idx758 = {n: i for i, n in enumerate(list758)}
    rename_498_to_758 = rename_498_to_758 or {}
    rename_758_to_498 = rename_758_to_498 or {}
    fallback_758_to_498 = fallback_758_to_498 or {}
    fallback_498_to_758 = fallback_498_to_758 or {}
    fwd = []
    unmapped_fwd = []
    for n in list758:
        if n in idx498:
            fwd.append(idx498[n])
        elif n in rename_758_to_498 and rename_758_to_498[n] in idx498:
            fwd.append(idx498[rename_758_to_498[n]])
        elif n in fallback_758_to_498:
            fb = fallback_758_to_498[n]
            if fb is None:
                fwd.append(fallback_id_498 if fallback_id_498 is not None else -1)
            elif fb in idx498:
                fwd.append(idx498[fb])
            else:
                unmapped_fwd.append(n)
                fwd.append(-1)
        else:
            unmapped_fwd.append(n)
            fwd.append(-1)
    rev = []
    unmapped_rev = []
    for n in list498:
        if n in idx758:
            rev.append(idx758[n])
        elif n in rename_498_to_758 and rename_498_to_758[n] in idx758:
            rev.append(idx758[rename_498_to_758[n]])
        elif n in fallback_498_to_758:
            fb = fallback_498_to_758[n]
            if fb is not None and fb in idx758:
                rev.append(idx758[fb])
            else:
                unmapped_rev.append(n)
                rev.append(-1)
        else:
            unmapped_rev.append(n)
            rev.append(-1)
    return fwd, rev, unmapped_fwd, unmapped_rev


def build_sound_fallback(list498, list758):
    """音の名前対応（758→498）を規則で作る。戻り値は
    (名前→名前の dict（非恒等のみ）, 未対応のまま石の破壊音へ行く件数)。
    手順：改名（zombified_piglin→zombie_pigman）→ block family 置換 →
    entity の代替モブ置換 → 個別規則 → それでも無ければ未対応。"""
    set498 = set(list498)
    out = {}

    def fam_sub(name):
        """block.<family>.<rest> の family を置換して存在する候補を返す。"""
        parts = name.split(".")
        if len(parts) < 3 or parts[0] != "block":
            return None
        fam, rest = parts[1], ".".join(parts[2:])
        for cand in SOUND_FAMILY_758_TO_498.get(fam, []):
            s = f"block.{cand}.{rest}"
            if s in set498:
                return s
        return None

    def entity_sub(name):
        """entity.<mob>.<rest> の mob を代替モブに置換する。"""
        parts = name.split(".")
        if len(parts) < 3 or parts[0] != "entity":
            return None
        mob, rest = parts[1], parts[2:]
        targets = []
        if mob in ENTITY_RENAME_758_TO_498:
            targets.append(ENTITY_RENAME_758_TO_498[mob])
        if mob in ENTITY_FALLBACK_758_TO_498:
            targets.append(ENTITY_FALLBACK_758_TO_498[mob])
        for t in targets:
            s = "entity." + t + "." + ".".join(rest)
            if s in set498:
                return s
            # entity.goat.screaming.ambient のような亜種は screaming を落とす。
            if len(rest) >= 2:
                s2 = "entity." + t + "." + ".".join(rest[1:])
                if s2 in set498:
                    return s2
            # 接尾辞が無ければ hurt／ambient で受ける。
            for suf in ("hurt", "ambient"):
                s3 = f"entity.{t}.{suf}"
                if s3 in set498:
                    return s3
        return None

    explicit = {
        "item.armor.equip_netherite": "item.armor.equip_diamond",
        "item.bucket.fill_axolotl": "item.bucket.fill_fish",
        "item.bucket.empty_axolotl": "item.bucket.empty_fish",
        "music_disc.pigstep": "music_disc.cat",
        "music_disc.otherside": "music_disc.cat",
        "entity.fox.teleport": "entity.enderman.teleport",
        "block.cave_vines.pick_berries": "item.sweet_berries.pick_from_bush",
        "item.honey_bottle.drink": "entity.generic.drink",
        "entity.iron_golem.damage": "entity.iron_golem.hurt",
        "entity.iron_golem.repair": "entity.iron_golem.hurt",
        "item.axe.scrape": "item.axe.strip",
    }
    unresolved = 0
    for n in list758:
        if n in set498:
            continue
        cand = None
        if "zombified_piglin" in n:
            r = n.replace("zombified_piglin", "zombie_pigman")
            if r in set498:
                cand = r
        if cand is None:
            cand = fam_sub(n)
        if cand is None:
            cand = entity_sub(n)
        if cand is None and n in explicit and explicit[n] in set498:
            cand = explicit[n]
        if cand is None and n.startswith("ambient.") :
            cand = "ambient.cave" if "ambient.cave" in set498 else None
        if cand is None and n.startswith("music.overworld."):
            cand = "music.game" if "music.game" in set498 else None
        if cand is None and n.startswith("music.nether."):
            # 498 ではバイオーム別に分かれていない。
            cand = "music.nether" if "music.nether" in set498 else None
        if cand is None and n.startswith("entity.parrot.imitate."):
            # オウムの物真似は、真似先のモブ名で498の名前を探す。
            mob = n.rsplit(".", 1)[1]
            t = ENTITY_RENAME_758_TO_498.get(
                mob, ENTITY_FALLBACK_758_TO_498.get(mob))
            if t:
                r = f"entity.parrot.imitate.{t}"
                cand = r if r in set498 else None
        if cand is None:
            unresolved += 1
            continue
        out[n] = cand
    return out, unresolved


def reg_names(path, key):
    """jar ダンプの registries.json から protocol_id 順の名前リストを取る。"""
    r = load(path)
    ent = r[key]["entries"]
    return [k.split(":", 1)[1] for k, _ in
            sorted(ent.items(), key=lambda kv: kv[1]["protocol_id"])]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--data-dir", type=Path, default=DATA_DIR)
    parser.add_argument("--out", type=Path, default=None)
    parser.add_argument("--json", type=Path, default=None)
    args = parser.parse_args()
    d = args.data_dir

    vv498 = load(d / "mapping-1.14.json")
    vv758 = load(d / "mapping-1.18.json")
    blocks498 = load(d / "pc" / "1.14.4" / "blocks.json")
    blocks758 = load(d / "pc" / "1.18" / "blocks.json")
    biomes498 = {b["name"]: b["id"] for b in load(d / "pc" / "1.14.4" / "biomes.json")}
    biomes758 = {b["name"]: b["id"] for b in load(d / "pc" / "1.18" / "biomes.json")}
    reg498 = d / "datagen1144" / "generated" / "reports" / "registries.json"
    reg758 = d / "datagen1182" / "generated" / "reports" / "registries.json"
    be498 = reg_names(reg498, "minecraft:block_entity_type")
    be758 = reg_names(reg758, "minecraft:block_entity_type")
    menus498 = reg_names(reg498, "minecraft:menu")
    menus758 = reg_names(reg758, "minecraft:menu")

    # jar ダンプとの照合（順序が実物と一致することの確認）。
    for vv, reg, label in ((vv498, reg498, "498"), (vv758, reg758, "758")):
        for vv_key, jar_key in (("blocks", "minecraft:block"),
                                ("items", "minecraft:item"),
                                ("entities", "minecraft:entity_type"),
                                ("sounds", "minecraft:sound_event"),
                                ("particles", "minecraft:particle_type")):
            jar_names = reg_names(reg, jar_key)
            assert jar_names == vv[vv_key], f"{label} {vv_key} の順序が jar ダンプと違う"
        print(f"{label}: blocks／items／entities／sounds／particles の順序が jar ダンプと一致を確認。")
    assert be758 == vv758["blockentities"], "758 blockentities が jar ダンプと違う"
    assert reg_names(reg498, "minecraft:motive") == reg_names(reg758, "minecraft:motive"), \
        "絵画の順序が 498 と 758 で違う"
    print(f"ブロックエンティティ 498={len(be498)}／758={len(be758)}、"
          f"メニュー 498={len(menus498)}／758={len(menus758)} を jar ダンプから取得。")
    print("絵画 26 件は 498・758 で同じ顔ぶれ・同じ順序（対応表は作らない）。")
    eff498 = reg_names(reg498, "minecraft:mob_effect")
    eff758 = reg_names(reg758, "minecraft:mob_effect")
    assert eff498 == eff758, "エフェクトの順序が 498 と 758 で違う"
    print("エフェクト 32 件は 498・758 で同じ顔ぶれ・同じ順序（対応表は作らない）。")
    enc498 = reg_names(reg498, "minecraft:enchantment")
    enc758 = reg_names(reg758, "minecraft:enchantment")
    assert set(enc758) - set(enc498) == {"soul_speed"} and len(enc498) == 37
    print("エンチャント 498=37／758=38（soul_speed が 758 で追加。対応表は作らない）。")

    # ダメージ種別は 762 の 42 件の恒等写像（758 のときと同じ）。
    login_762 = load(d / "pc" / "1.19.4-loginPacket.json")
    entries = login_762["dimensionCodec"]["value"]["minecraft:damage_type"] \
        ["value"]["value"]["value"]["value"]
    damage = [e["name"]["value"].split(":", 1)[1] for e in entries]
    assert len(damage) == 42, f"ダメージ種別が 42 件ではない: {len(damage)}"

    problems = []

    def check(name, unmapped_fwd, unmapped_rev):
        if unmapped_fwd or unmapped_rev:
            problems.append((name, unmapped_fwd, unmapped_rev))

    bs_fwd, bs_rev, u, v = build_block_state_maps(vv498, vv758, blocks498, blocks758)
    check("blockstate", u, v)

    item_fwd, item_rev, u, v = build_name_map(
        vv498["items"], vv758["items"],
        rename_498_to_758=ITEM_RENAME_498_TO_758,
        rename_758_to_498=ITEM_RENAME_758_TO_498,
        fallback_758_to_498=ITEM_FALLBACK_758_TO_498,
        fallback_id_498=1)  # 石
    check("item", u, v)
    ent_fwd, ent_rev, u, v = build_name_map(
        vv498["entities"], vv758["entities"],
        rename_498_to_758=ENTITY_RENAME_498_TO_758,
        rename_758_to_498=ENTITY_RENAME_758_TO_498,
        fallback_758_to_498=ENTITY_FALLBACK_758_TO_498,
        fallback_id_498=vv498["entities"].index("pig"))
    check("entity", u, v)
    # 音の名前の代替表（498→758）。498 にだけある音は11件：
    # ゾンビピグマンの改名5件（うち物真似は piglin の物真似へ）、
    # music.nether の細分化、スイートベリーの改名、1.15 で消えた
    # オウムの物真似4件（enderman／panda／polar_bear／wolf。
    # ゾンビの物真似で受ける）。
    # （数値表の逆方向でも使うため、ここで先に作る。）
    snd_names_rev_map = {}
    for n in vv498["sounds"]:
        if n in vv758["sounds"]:
            continue
        cand = None
        if n == "entity.parrot.imitate.zombie_pigman":
            # 物真似だけは 758 に zombified_piglin の物真似が無く、
            # piglin の物真似が後継になる（1.15 で imitate.zombie_pigman
            # 自体が削除されていることを mapping-1.15.json で確認済み）。
            cand = "entity.parrot.imitate.piglin"
        elif "zombie_pigman" in n:
            cand = n.replace("zombie_pigman", "zombified_piglin")
        elif n == "music.nether":
            cand = "music.nether.nether_wastes"
        elif n == "item.sweet_berries.pick_from_bush":
            cand = "block.sweet_berry_bush.pick_berries"
        elif n in ("entity.parrot.imitate.enderman",
                   "entity.parrot.imitate.panda",
                   "entity.parrot.imitate.polar_bear",
                   "entity.parrot.imitate.wolf"):
            cand = "entity.parrot.imitate.zombie"
        assert cand is not None and cand in vv758["sounds"], \
            f"498 専用の音の代替が決まらない: {n}"
        snd_names_rev_map[n] = cand
    # 音は名前対応を規則で作り、数値表はそれを ID に直して作る。
    snd_names_map, snd_unresolved = build_sound_fallback(vv498["sounds"], vv758["sounds"])
    idx498_snd = {n: i for i, n in enumerate(vv498["sounds"])}
    snd_fwd = [idx498_snd[snd_names_map[n]] if n in snd_names_map
               else (idx498_snd[n] if n in idx498_snd else -1)
               for n in vv758["sounds"]]
    stone_break_498 = vv498["sounds"].index("block.stone.break")
    snd_fwd = [stone_break_498 if x < 0 else x for x in snd_fwd]
    snd_rev = []
    unmapped_snd_rev = []
    idx758_snd = {n: i for i, n in enumerate(vv758["sounds"])}
    for n in vv498["sounds"]:
        if n in idx758_snd:
            snd_rev.append(idx758_snd[n])
        elif n in snd_names_rev_map:
            snd_rev.append(idx758_snd[snd_names_rev_map[n]])
        else:
            unmapped_snd_rev.append(n)
            snd_rev.append(-1)
    check("sound", [], unmapped_snd_rev)
    print(f"音の名前対応：{len(snd_names_map)} 件を規則で代替、"
          f"{snd_unresolved} 件は代替先が決まらず石の破壊音へ。")
    part_fwd, part_rev, u, v = build_name_map(
        vv498["particles"], vv758["particles"],
        rename_498_to_758=PARTICLE_RENAME_498_TO_758,
        rename_758_to_498=PARTICLE_RENAME_758_TO_498,
        fallback_758_to_498=PARTICLE_FALLBACK_758_TO_498,
        fallback_id_498=0)  # ambient_entity_effect
    check("particle", u, v)
    blk_fwd, blk_rev, u, v = build_name_map(
        vv498["blocks"], vv758["blocks"],
        rename_498_to_758=BLOCK_RENAME_498_TO_758,
        rename_758_to_498=BLOCK_RENAME_758_TO_498,
        fallback_758_to_498=BLOCK_FALLBACK_758_TO_498,
        fallback_id_498=1)  # 石
    check("block", u, v)
    be_fwd, be_rev, u, v = build_name_map(
        be498, be758,
        fallback_758_to_498=BLOCK_ENTITY_FALLBACK_758_TO_498,
        fallback_id_498=None)
    check("blockentity", u, v)
    menu_fwd, menu_rev, u, v = build_name_map(
        menus498, menus758,
        rename_498_to_758=MENU_RENAME_498_TO_758,
        rename_758_to_498=MENU_RENAME_758_TO_498,
        fallback_758_to_498=MENU_FALLBACK_758_TO_498,
        fallback_id_498=menus498.index("crafting"))
    check("menu", u, v)
    biome_fwd, biome_rev, u, v = build_name_map(
        [n for n, _ in sorted(biomes498.items(), key=lambda kv: kv[1])],
        [n for n, _ in sorted(biomes758.items(), key=lambda kv: kv[1])],
        rename_498_to_758=BIOME_RENAME_498_TO_758,
        rename_758_to_498=BIOME_RENAME_758_TO_498,
        fallback_758_to_498=BIOME_FALLBACK_758_TO_498,
        fallback_498_to_758=BIOME_FALLBACK_498_TO_758,
        fallback_id_498=None)
    check("biome", u, v)
    dmg_fwd, dmg_rev, u, v = build_name_map(
        damage, damage,
        fallback_id_498=None)
    check("damage", u, v)

    if problems:
        for name, uf, ur in problems:
            print(f"未対応 {name}: 758→498 {uf} / 498→758 {ur}", file=sys.stderr)
        return 1

    maps = {
        "BLOCK_STATE": (bs_fwd, bs_rev, len(vv758["blockstates"]), len(vv498["blockstates"])),
        "ITEM": (item_fwd, item_rev, len(vv758["items"]), len(vv498["items"])),
        "ENTITY_TYPE": (ent_fwd, ent_rev, len(vv758["entities"]), len(vv498["entities"])),
        "SOUND": (snd_fwd, snd_rev, len(vv758["sounds"]), len(vv498["sounds"])),
        "PARTICLE": (part_fwd, part_rev, len(vv758["particles"]), len(vv498["particles"])),
        "BLOCK": (blk_fwd, blk_rev, len(vv758["blocks"]), len(vv498["blocks"])),
        "BLOCK_ENTITY": (be_fwd, be_rev, len(be758), len(be498)),
        "MENU": (menu_fwd, menu_rev, len(menus758), len(menus498)),
        "BIOME": (biome_fwd, biome_rev, len(biomes758), len(biomes498)),
        "DAMAGE_TYPE": (dmg_fwd, dmg_rev, len(damage), len(damage)),
    }
    fallbacks_fwd = {
        "BLOCK_STATE": 1, "ITEM": 1,
        "ENTITY_TYPE": vv498["entities"].index("pig"),
        "SOUND": stone_break_498, "PARTICLE": 0, "BLOCK": 1,
        "BLOCK_ENTITY": -1, "MENU": menus498.index("crafting"),
        "BIOME": biomes498["plains"], "DAMAGE_TYPE": 16,
    }
    stone_break_758 = vv758["sounds"].index("block.stone.break")
    fallbacks_rev = {
        "BLOCK_STATE": 1, "ITEM": 1,
        "ENTITY_TYPE": vv758["entities"].index("pig"),
        "SOUND": stone_break_758, "PARTICLE": 0, "BLOCK": 1,
        "BLOCK_ENTITY": -1, "MENU": menus758.index("crafting"),
        "BIOME": biomes758["plains"], "DAMAGE_TYPE": 16,
    }

    for name, (fwd, rev, n_fwd, n_rev) in maps.items():
        fb = fallbacks_fwd[name]
        fr, fo = ranges_overrides(fwd, n_fwd, fb)
        rr, ro = ranges_overrides(rev, n_rev, fallbacks_rev[name])
        print(f"{name}: 758→498 entries={n_fwd} ranges={len(fr)} overrides={len(fo)} "
              f"fallback={sum(1 for x in fwd if x == fb)} / "
              f"498→758 entries={n_rev} ranges={len(rr)} overrides={len(ro)} "
              f"fallback={sum(1 for x in rev if x == fallbacks_rev[name])}")

    if args.json:
        out = {name: {"fwd": fwd, "rev": rev}
               for name, (fwd, rev, _, _) in maps.items()}
        args.json.write_text(json.dumps(out, indent=1) + "\n", encoding="utf-8")
        print(f"JSON を {args.json} に書き出した。")

    header = """package io.detergentmc.protocol;

/**
 * 498（1.14.4）のレジストリ対応表（区間＋上書きの生の表）。
 *
 * <p>{@link RegistryRemapper} から分離した。理由はバイトコードの制限：
 * 表を RegistryRemapper に置くと静的初期化子（{@code <clinit>}）が
 * 64KB を超えてコンパイルできなくなる（"code too large"）ため。
 * このクラスの表は {@link RegistryRemapper} から static import で
 * 同じ名前のまま参照する。
 *
 * <p>表の内容と検証は {@code detergentmc-protocol/tools/} を参照：
 * 生成は {@code gen-498-tables.py}、検証は {@code verify-498-tables.py}。
 * 情報源は ViaVersion Mappings の mapping-1.14.json（498）と
 * mapping-1.18.json（758）で、名前で突き合わせた。順序が本物の
 * バニラ 1.14.4／1.18.2 と一致することは両 jar の data-generator
 * ダンプ（reports/registries.json・reports/blocks.json）で
 * 全カテゴリ確認済み。ブロックエンティティとメニューは mapping に
 * 無いため jar ダンプから取った。
 * ブロック状態はプロパティを1個だけ落とす／足す一致も使うほか、
 * 壁（1.16 で bool→low/tall/none＋up）・ジグソー（facing→orientation）・
 * 大釜（1.17 で水／溶岩／粉雪に分割）は専用の変換を使う。
 * 1.16・1.17 の新要素の代替は次の通り：
 * 深層岩系→石系、真紅・歪んだ木材系→オーク系、ブラックストーン系→石系、
 * 銅系→鉄系、アメジスト系→クォーツ系、ろうそく→松明、
 * ネザライト→ダイヤ系（アイテム）、大釜系→大釜、スカルクセンサー→
 * オブザーバー、スポーンエッグは近いモブへ。1.15 のハチ・ハチミツ系は
 * オーク材／スライムブロック／オウム／水しぶき系パーティクルへ代替し、
 * beehive ブロックエンティティは落とす。メニューの地図台は
 * cartography ⇔ cartography_table の改名で対応する。
 * 音は family 置換とモブ置換の規則で代替し、決まらないものは
 * 石の破壊音へ。パーティクルは soul→end_rod、vibration→enchant など。
 * 絵画 26 件とエフェクト 32 件は 498・758 で同じ顔ぶれ・同じ順序の
 * ため対応表を作らない。エンチャントは soul_speed（758）が増えたが
 * アイテム NBT では名前で扱うため対応表は作らない。
 * ダメージ種別は 42 件の恒等写像（codec に無いため呼び出し元は無い）。
 * ブロック状態の上書き表だけは件数が約1万件あり、静的初期化子の
 * 64KB制限に収まらないため、RegistryTables498BsFwd*／BsRev* の
 * 分割クラスに分けて {@code concat} でつないでいる。
 */
final class RegistryTables498 {

    private RegistryTables498() {
    }

"""
    parts = [header]
    chunk_files = {}
    for name, (fwd, rev, n_fwd, n_rev) in maps.items():
        fb = fallbacks_fwd[name]
        fr, fo = ranges_overrides(fwd, n_fwd, fb)
        rr, ro = ranges_overrides(rev, n_rev, fallbacks_rev[name])
        if name == "BLOCK_STATE":
            # ブロック状態の上書きは約1万件あり、1クラスの静的初期化子
            # （64KB制限）に収まらない。そのため上書きだけを
            # 1200件ごとの別クラスに分け、concat でつなぐ。
            def emit_chunks(prefix, overrides):
                names = []
                for ci in range(0, len(overrides), 1200):
                    chunk = overrides[ci:ci + 1200]
                    cname = f"RegistryTables498{prefix}{ci // 1200 + 1}"
                    body = [f"package io.detergentmc.protocol;",
                            "",
                            f"/** 498 のブロック状態の上書き表の分割クラス"
                            f"（{prefix}、{ci // 1200 + 1} 個目）。"
                            f"生成は gen-498-tables.py。 */",
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
            parts.append(java_table(f"{name}_498", fr, [], n_fwd, "758->498")
                         .replace(f"static final int[][] {name}_498_OVERRIDES = {{",
                                  f"static final int[][] {name}_498_OVERRIDES_PLACEHOLDER = {{"))
            parts[-1] = parts[-1].replace(
                f"    static final int[][] {name}_498_OVERRIDES_PLACEHOLDER = {{\n    }};",
                f"    static final int[][] {name}_498_OVERRIDES = concat("
                + ", ".join(fwd_names) + ");")
            parts.append("")
            parts.append(java_table(f"{name}_498_TO_758", rr, [], n_rev, "498->758")
                         .replace(f"static final int[][] {name}_498_TO_758_OVERRIDES = {{",
                                  f"static final int[][] {name}_498_TO_758_OVERRIDES_PLACEHOLDER = {{"))
            parts[-1] = parts[-1].replace(
                f"    static final int[][] {name}_498_TO_758_OVERRIDES_PLACEHOLDER = {{\n    }};",
                f"    static final int[][] {name}_498_TO_758_OVERRIDES = concat("
                + ", ".join(rev_names) + ");")
            parts.append("")
            continue
        parts.append(java_table(f"{name}_498", fr, fo, n_fwd, "758->498"))
        parts.append("")
        parts.append(java_table(f"{name}_498_TO_758", rr, ro, n_rev, "498->758"))
        parts.append("")
    # 音の名前の代替表（758→498。Stop Sound 用。数値表の代替と一致させる）。
    snd_names = sorted(snd_names_map.items())
    snd_names_rev = sorted(snd_names_rev_map.items())
    tools_dir = Path(__file__).resolve().parent
    (tools_dir / "sound-names-758-to-498.json").write_text(
        json.dumps(snd_names, ensure_ascii=False) + "\n", encoding="utf-8")
    (tools_dir / "sound-names-498-to-758.json").write_text(
        json.dumps(snd_names_rev, ensure_ascii=False) + "\n", encoding="utf-8")
    print(f"音の名前の代替表 JSON を書き出した（758→498 は {len(snd_names)} 対、"
          f"498→758 は {len(snd_names_rev)} 対）。")
    lines = ["    /** 音の名前：758 → 498（Stop Sound 用）。数値の合成表と一致させる。 */",
             "    static final String[][] SOUND_NAME_FALLBACKS_758_TO_498 = {"]
    for src, dst in snd_names:
        lines.append(f'            {{"{src}", "{dst}"}},')
    lines.append("    };")
    parts.append("\n".join(lines))
    parts.append("")
    # 音の名前の代替表（498→758）。
    lines = ["    /** 音の名前：498 → 758（Stop Sound 用）。改名と music.nether の細分化。 */",
             "    static final String[][] SOUND_NAME_FALLBACKS_498_TO_758 = {"]
    for src, dst in snd_names_rev:
        lines.append(f'            {{"{src}", "{dst}"}},')
    lines.append("    };")
    parts.append("\n".join(lines))
    parts.append("")
    # ブロックエンティティの名前表（チャンク内のブロックエンティティと
    # Block Entity Data の変換で、498 の「id 文字列」⇔758 の種別番号を
    # 引くために使う）。
    def names_array(field, names):
        lines = [f"    static final String[] {field} = {{"]
        for i in range(0, len(names), 4):
            chunk = names[i:i + 4]
            lines.append("            " + " ".join(f'"{n}",' for n in chunk))
        lines.append("    };")
        return "\n".join(lines)
    parts.append(names_array("BLOCK_ENTITY_NAMES_498", be498))
    parts.append("")
    parts.append(names_array("BLOCK_ENTITY_NAMES_758", be758))
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
