#!/usr/bin/env python3
"""754（1.16.5）のレジストリ対応表（755→754）の生成。

755の前例（gen-755-tables.py）にならう。754は755の1つ下で、755の
隣接として表を直接作る（777のときの776問題と同じ構図で、同じ方針(b)。
758は他人の作業中のため飛ばす）。合成表は作らない。754↔763〜775 は
754→755→756→757→759→760→761→762→763→合成表の隣接連鎖で到達する。

使い方：
  python3 tools/gen-754-tables.py [--data-dir DIR]

入力（detergentmc-protocol/build/remap-data/。build-1.16-inputs.py
で vv-1.16.json を実バニラの registries／reports の順序に補正済み）：
  - vv-1.16.json … ViaVersion mapping-1.16.json を補正したもの
    （blockstates 17112・blocks 763・items 976・entities 108・
    sounds 992・particles 72・menus 24・attributes 13・
    blockentities 33・enchantments 38。生の mapping-1.16.json は
    1.16.0 時点のもので piglin_brute 系が無く、道具・染料の順序も
    違うため、registries-1.16.5.json／blocks-1.16.5-reports.json の
    protocol_id 順で上書きしてある。生ファイルは vv-1.16-raw.json）
  - vv-1.17.json … ViaVersion mapping-1.17.json（755 側）
  - 1.16_blocks.json … minecraft-data pc/1.16.2 の blocks.json
    （代替ブロックの既定状態を引くため）
  - registries-1.16.5.json／registries-1.17.json … 実バニラの
    reports/registries.json
  - enchantments-1.16.json … minecraft-data pc/1.16.4（754 側）
  - enchantments-1.17.json … minecraft-data pc/1.17（755 側）
  - biomes-1.16.json … minecraft-data pc/1.16.2（754 側。79 件・
    id 0〜173）／biomes-1.17.json（755 側。81 件・id 0〜175）

出力（すべて --data-dir）：
  - gen754.txt … Java の表の宣言全文（insert-754-tables.py が読む）
  - maps754.json … 755→754 の対応表（7種＋reverse_items）
  - map_items_rev754.json … 754→755 のアイテム逆変換表
  - maps754-reverse.json … 754→755 の対応表（名前の真値。
    build_inverse で作る。同名のものを優先するため、invert() の
    first-wins と違う場合がある）
  - maps754-corrections.json … invert() の結果と名前の真値の差分
    （(754 id → 755 id) の対。wiring で invert() に渡す補正）

対応の決め方（generate-remap-tables.py の build_pair と同じ）：
  1. 名前（ブロック状態は名前＋プロパティ全体）がそのままある → その ID。
  2. プロパティを1個だけ落とすと一致する → その ID（755 の rail 系の
     waterlogged など。activator_rail／detector_rail／powered_rail／
     rail が該当する）。
  3. 改名（dirt_path → grass_path、block.sweet_berry_bush.pick_berries
     → item.sweet_berries.pick_from_bush）→ 改名先の ID。
  4. 下の *_754 代替表にある 1.17 の新ブロック／新アイテム／新音など
     → 近い既存のもの（代替先の根拠は各表のコメント参照。代替先は
     すべて 1.16.5 に実在するものを実測で確認してある）。
  5. それでも決まらないものがあれば「未対応」として一覧を出し、
     終了コード 1 で失敗する（黙って推測しない）。

1.17→1.16.5 で確認した差分（名前ベース。実バニラ jar の data-generator
レポート（registries-1.16.5.json／blocks-1.16.5-reports.json）と
registries-1.17.json／blocks-1.17-reports.json、ViaVersion mapping、
minecraft-data で実測）：
  - ブロック状態 20,342→17,112（新ブロック 136 種・約 3,281 状態と
    rail 系の waterlogged・cauldron の分割が差分。文字列が完全一致する
    状態は 17,061 件）
  - ブロック 898→763（新 136・改名 1（grass_path→dirt_path））
  - アイテム 1,100→976（新 125・改名 1）
  - エンティティ 113→108（新 5：axolotl・glow_item_frame・glow_squid・
    goat・marker）
  - 音 1,190→992（新 199・改名 1（block.sweet_berry_bush.pick_berries
    → item.sweet_berries.pick_from_bush）。1.16 には block.vine.* が
    step しか無いことも実測）
  - パーティクル 89→72（新 17）
  - メニュー 24→24（同一順序）、属性 13→13（同一順序）、
    エンチャント 38→38（同一順序）、エフェクト 32→32（同一順序）
  - ブロックエンティティ 34→33（1.17 の新規 sculk_sensor（id 33）が
    1.16.5 には無い。754 側は 0〜32 で同一順序）
  - バイオーム 81→79（1.17 の新規 dripstone_caves（174）・lush_caves
    （175）が 1.16.5 には無い。0〜173 は同じ id・同じ名前。隙間 95 件）
  - ゲームイベント：1.16.5 には無し（1.17 で 45 件追加）。
    カスタム統計 75→74。
  - damage_type レジストリは 1.16.5／1.17 のどちらにも無い（1.19.4／
    762 で追加）。755 と同じく 762 の 42件の恒等写像を置くが、
    ワイヤ上の呼び出し元は無い。
  - data_component_type・environment_attribute・slot_display は
    1.16.5／1.17 のどちらにも無い（1.20.5 以降で追加）。
"""

import importlib.util
import json
import sys
from collections import Counter
from pathlib import Path

SCRIPT_DIR = Path(__file__).resolve().parent
REPO_ROOT = SCRIPT_DIR.parent.parent
DATA_DIR = REPO_ROOT / "detergentmc-protocol" / "build" / "remap-data"

_spec = importlib.util.spec_from_file_location(
    "grt", SCRIPT_DIR / "generate-remap-tables.py")
grt = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(grt)

load = grt.load
compress = grt.compress
java_int3 = grt.java_int3
java_int2 = grt.java_int2
java_string_array = grt.java_string_array
build_pair = grt.build_pair
build_name_map = grt.build_name_map
build_reverse_items = grt.build_reverse_items
build_inverse = grt.build_inverse
invert_java = grt.invert_java
block_name = grt.block_name
block_props = grt.block_props
same_props = grt.same_props


# 改名（755 側の名前 → 754 側の名前）。1.17 で grass_path が dirt_path
# に改名された（ブロック＋アイテム）。
# 音の改名（block.sweet_berry_bush.pick_berries（1.17）→
# item.sweet_berries.pick_from_bush（1.16.5））は build_pair が音に
# rename を渡さないため、SOUND_FALLBACK_754 に入れる。
RENAME_754 = {
    "dirt_path": "grass_path",
}

# 1.17 の新ブロック／新アイテムの代替先（754 側の名前）。近い既存の
# ものを選んである（代替先はすべて 1.16.5 に実在する。build_pair が
# 存在しない代替先を失敗として出すため、実行時に全件確認される）。
# 方針：石系は石系へ（deepslate→stone、銅→iron_block、calcite→
# quartz_block）、植物は植物へ（azalea→oak_sapling、cave_vines→vine）、
# 光るものは光るものへ（candle→torch、light→torch）、動物は近い動物へ。
# waxed／exposed／weathered／oxidized の銅はすべて iron_block。
# アイテムだけのもの（amethyst_shard→quartz、bundle→chest、
# spyglass→stick など）も同じ dict に入れる（build_pair はこの dict を
# アイテムとブロックの両方に使うため）。
ITEM_FALLBACK_754 = {
    # --- アメジスト・方解石・凝灰岩・玄武岩 ---
    "amethyst_block": "quartz_block", "amethyst_cluster": "quartz_block",
    "small_amethyst_bud": "quartz_block", "medium_amethyst_bud": "quartz_block",
    "large_amethyst_bud": "quartz_block", "budding_amethyst": "quartz_block",
    "amethyst_shard": "quartz", "calcite": "quartz_block",
    "tuff": "stone", "smooth_basalt": "basalt",
    # --- 深層岩系 ---
    "deepslate": "stone", "cobbled_deepslate": "cobblestone",
    "chiseled_deepslate": "chiseled_stone_bricks",
    "polished_deepslate": "polished_andesite",
    "deepslate_bricks": "stone_bricks",
    "cracked_deepslate_bricks": "cracked_stone_bricks",
    "deepslate_tiles": "stone_bricks",
    "cracked_deepslate_tiles": "cracked_stone_bricks",
    "infested_deepslate": "infested_stone",
    "deepslate_coal_ore": "coal_ore", "deepslate_copper_ore": "iron_ore",
    "deepslate_diamond_ore": "diamond_ore", "deepslate_emerald_ore": "emerald_ore",
    "deepslate_gold_ore": "gold_ore", "deepslate_iron_ore": "iron_ore",
    "deepslate_lapis_ore": "lapis_ore", "deepslate_redstone_ore": "redstone_ore",
    "cobbled_deepslate_slab": "cobblestone_slab",
    "cobbled_deepslate_stairs": "cobblestone_stairs",
    "cobbled_deepslate_wall": "cobblestone_wall",
    "polished_deepslate_slab": "polished_andesite_slab",
    "polished_deepslate_stairs": "polished_andesite_stairs",
    # polished_andesite_wall は 1.16.5 に無い（polished_andesite には
    # slab／stairs しかない）ため stone_brick_wall へ。
    "polished_deepslate_wall": "stone_brick_wall",
    "deepslate_brick_slab": "stone_brick_slab",
    "deepslate_brick_stairs": "stone_brick_stairs",
    "deepslate_brick_wall": "stone_brick_wall",
    "deepslate_tile_slab": "stone_brick_slab",
    "deepslate_tile_stairs": "stone_brick_stairs",
    "deepslate_tile_wall": "stone_brick_wall",
    # --- 銅系（すべて iron_block。銅鉱石は iron_ore） ---
    "copper_ore": "iron_ore",
    "copper_block": "iron_block", "cut_copper": "iron_block",
    "exposed_copper": "iron_block", "weathered_copper": "iron_block",
    "oxidized_copper": "iron_block", "waxed_copper_block": "iron_block",
    "waxed_exposed_copper": "iron_block", "waxed_weathered_copper": "iron_block",
    "waxed_oxidized_copper": "iron_block", "exposed_cut_copper": "iron_block",
    "weathered_cut_copper": "iron_block", "oxidized_cut_copper": "iron_block",
    "waxed_cut_copper": "iron_block", "waxed_exposed_cut_copper": "iron_block",
    "waxed_weathered_cut_copper": "iron_block", "waxed_oxidized_cut_copper": "iron_block",
    "cut_copper_slab": "iron_block", "cut_copper_stairs": "iron_block",
    "exposed_cut_copper_slab": "iron_block", "exposed_cut_copper_stairs": "iron_block",
    "weathered_cut_copper_slab": "iron_block", "weathered_cut_copper_stairs": "iron_block",
    "oxidized_cut_copper_slab": "iron_block", "oxidized_cut_copper_stairs": "iron_block",
    "waxed_cut_copper_slab": "iron_block", "waxed_cut_copper_stairs": "iron_block",
    "waxed_exposed_cut_copper_slab": "iron_block",
    "waxed_exposed_cut_copper_stairs": "iron_block",
    "waxed_weathered_cut_copper_slab": "iron_block",
    "waxed_weathered_cut_copper_stairs": "iron_block",
    "waxed_oxidized_cut_copper_slab": "iron_block",
    "waxed_oxidized_cut_copper_stairs": "iron_block",
    "copper_ingot": "iron_ingot", "raw_copper": "iron_ingot",
    "raw_copper_block": "iron_block", "raw_gold": "gold_ingot",
    "raw_gold_block": "gold_block", "raw_iron": "iron_ingot",
    "raw_iron_block": "iron_block",
    # --- 植物 ---
    "azalea": "oak_sapling", "flowering_azalea": "oak_sapling",
    "azalea_leaves": "oak_leaves", "flowering_azalea_leaves": "oak_leaves",
    "potted_azalea_bush": "potted_oak_sapling",
    "potted_flowering_azalea_bush": "potted_oak_sapling",
    "big_dripleaf": "lily_pad", "big_dripleaf_stem": "lily_pad",
    "small_dripleaf": "lily_pad", "cave_vines": "vine",
    "cave_vines_plant": "vine", "glow_lichen": "vine",
    "hanging_roots": "dead_bush", "moss_block": "grass_block",
    "moss_carpet": "green_carpet", "rooted_dirt": "dirt",
    "spore_blossom": "sunflower", "glow_berries": "sweet_berries",
    # --- ろうそく（すべて torch／cake） ---
    "candle": "torch", "white_candle": "torch", "orange_candle": "torch",
    "magenta_candle": "torch", "light_blue_candle": "torch",
    "yellow_candle": "torch", "lime_candle": "torch", "pink_candle": "torch",
    "gray_candle": "torch", "light_gray_candle": "torch", "cyan_candle": "torch",
    "purple_candle": "torch", "blue_candle": "torch", "brown_candle": "torch",
    "green_candle": "torch", "red_candle": "torch", "black_candle": "torch",
    "candle_cake": "cake", "white_candle_cake": "cake",
    "orange_candle_cake": "cake", "magenta_candle_cake": "cake",
    "light_blue_candle_cake": "cake", "yellow_candle_cake": "cake",
    "lime_candle_cake": "cake", "pink_candle_cake": "cake",
    "gray_candle_cake": "cake", "light_gray_candle_cake": "cake",
    "cyan_candle_cake": "cake", "purple_candle_cake": "cake",
    "blue_candle_cake": "cake", "brown_candle_cake": "cake",
    "green_candle_cake": "cake", "red_candle_cake": "cake",
    "black_candle_cake": "cake",
    # --- その他 ---
    "dripstone_block": "stone", "pointed_dripstone": "stone",
    "powder_snow": "snow_block", "light": "torch",
    "lightning_rod": "end_rod", "sculk_sensor": "daylight_detector",
    "tinted_glass": "glass",
    "lava_cauldron": "cauldron", "water_cauldron": "cauldron",
    "powder_snow_cauldron": "cauldron",
    # --- アイテムだけのもの ---
    "axolotl_bucket": "water_bucket", "axolotl_spawn_egg": "tropical_fish_spawn_egg",
    "bundle": "chest", "glow_ink_sac": "ink_sac",
    "glow_item_frame": "item_frame", "glow_squid_spawn_egg": "squid_spawn_egg",
    "goat_spawn_egg": "sheep_spawn_egg", "powder_snow_bucket": "water_bucket",
    "spyglass": "stick",
}

# ブロック状態の代替表（755 側のブロック名 → (754 側のブロック名, props 関数)）。
# ITEM_FALLBACK_754 と同じ代替先で、fn が None なら代替ブロックの既定状態。
# 大釜だけ level を引き継ぐ。1.17 の裸の cauldron は「空の大釜」なので
# 1.16.5 の cauldron[level=0] へ（level=3＝満杯は誤り）。water_cauldron
# と powder_snow_cauldron は level をそのまま引き継ぎ、lava_cauldron は
# level 3（満杯）へ。
# cauldron（1.17 の裸の cauldron）は 1.16.5 の cauldron[level=0〜3] に
# そのままでは一致しないため、代替表で level 0 を指定する。
# dirt_path は改名（RENAME_754）で処理するためここには入れない。
BLOCK_FALLBACK_754 = {}
for _name, _target in ITEM_FALLBACK_754.items():
    if _name in ("amethyst_shard", "axolotl_bucket", "axolotl_spawn_egg",
                 "bundle", "copper_ingot", "glow_berries", "glow_ink_sac",
                 "glow_item_frame", "glow_squid_spawn_egg", "goat_spawn_egg",
                 "powder_snow_bucket", "raw_copper", "raw_gold", "raw_iron",
                 "spyglass"):
        continue
    BLOCK_FALLBACK_754[_name] = (_target, None)
BLOCK_FALLBACK_754["cauldron"] = ("cauldron", lambda p: {"level": "0"})
BLOCK_FALLBACK_754["water_cauldron"] = (
    "cauldron", lambda p: {"level": p.get("level", "3")})
BLOCK_FALLBACK_754["lava_cauldron"] = ("cauldron", lambda p: {"level": "3"})
BLOCK_FALLBACK_754["powder_snow_cauldron"] = (
    "cauldron", lambda p: {"level": p.get("level", "3")})

# 1.17 の新エンティティの代替先（近い既存のモブ）。
ENTITY_FALLBACK_754 = {
    "axolotl": "tropical_fish", "glow_item_frame": "item_frame",
    "glow_squid": "squid", "goat": "sheep", "marker": "armor_stand",
}

# 1.17 の新音の代替先（近い既存の音。block.<新ブロック>.<動詞> は
# 同じ動詞の近いブロックの音へ。動詞が無いもの（chime・tilt・
# drip_*_into_cauldron など）は近い音へ。代替先はすべて 1.16.5 に
# 実在する。build_pair が存在しない代替先を失敗として出す）。
# なお 1.16.5 には block.vine.* が step しか無いため、vine 系は
# block.grass.* へ落とす。block.cake.add_candle は 1.16.5 に無く、
# block.wood.place へ落とす。
def _verb(family, verbs):
    return {f"block.{family}.{v}": f"block.{_VERB_FAMILY[family]}.{v}" for v in verbs}


_VERB_FAMILY = {
    "amethyst_block": "glass", "amethyst_cluster": "glass",
    "azalea": "grass", "azalea_leaves": "grass",
    "big_dripleaf": "grass", "calcite": "stone",
    "cave_vines": "grass", "copper": "metal",
    "deepslate": "stone", "deepslate_bricks": "stone",
    "deepslate_tiles": "stone", "dripstone_block": "stone",
    "flowering_azalea": "grass", "hanging_roots": "grass",
    "moss": "grass", "moss_carpet": "grass",
    "polished_deepslate": "stone", "powder_snow": "snow",
    "rooted_dirt": "grass", "small_dripleaf": "grass",
    "spore_blossom": "grass", "tuff": "stone",
    "vine": "grass",
}
SOUND_FALLBACK_754 = {}
for _fam in ("amethyst_block", "amethyst_cluster", "azalea", "azalea_leaves",
             "big_dripleaf", "calcite", "cave_vines", "copper", "deepslate",
             "deepslate_bricks", "deepslate_tiles", "dripstone_block",
             "flowering_azalea", "hanging_roots", "moss", "moss_carpet",
             "polished_deepslate", "powder_snow", "rooted_dirt",
             "small_dripleaf", "spore_blossom", "tuff", "vine"):
    SOUND_FALLBACK_754.update(_verb(_fam, ("break", "fall", "hit", "place", "step")))
# block.vine.step は 1.16.5 にも 1.17 にも存在する（恒等写像）ため、
# 代替表から外す。入れっぱなしにすると音の名前表（Stop Sound 用）が
# vine.step を grass.step にすり替えてしまう。
del SOUND_FALLBACK_754["block.vine.step"]
SOUND_FALLBACK_754.update({
    "block.amethyst_block.chime": "block.note_block.pling",
    # block.sweet_berry_bush.pick_berries（1.17）は
    # item.sweet_berries.pick_from_bush（1.16.5）への改名。
    # build_pair は音に rename を渡さないため代替表に入れる。
    "block.sweet_berry_bush.pick_berries": "item.sweet_berries.pick_from_bush",
    "block.big_dripleaf.tilt_down": "block.grass.step",
    "block.big_dripleaf.tilt_up": "block.grass.step",
    "block.cake.add_candle": "block.wood.place",
    "block.candle.ambient": "block.fire.ambient",
    "block.candle.break": "block.wool.break",
    "block.candle.extinguish": "block.fire.extinguish",
    "block.candle.fall": "block.wool.fall",
    "block.candle.hit": "block.wool.hit",
    "block.candle.place": "block.wool.place",
    "block.candle.step": "block.wool.step",
    "block.cave_vines.pick_berries": "item.sweet_berries.pick_from_bush",
    "block.large_amethyst_bud.break": "block.glass.break",
    "block.large_amethyst_bud.place": "block.glass.place",
    "block.medium_amethyst_bud.break": "block.glass.break",
    "block.medium_amethyst_bud.place": "block.glass.place",
    "block.pointed_dripstone.break": "block.stone.break",
    "block.pointed_dripstone.drip_lava": "block.lava.ambient",
    "block.pointed_dripstone.drip_lava_into_cauldron": "block.lava.ambient",
    "block.pointed_dripstone.drip_water": "block.water.ambient",
    "block.pointed_dripstone.drip_water_into_cauldron": "block.water.ambient",
    "block.pointed_dripstone.fall": "block.stone.fall",
    "block.pointed_dripstone.hit": "block.stone.hit",
    "block.pointed_dripstone.land": "block.stone.fall",
    "block.pointed_dripstone.place": "block.stone.place",
    "block.pointed_dripstone.step": "block.stone.step",
    "block.sculk_sensor.break": "block.stone.break",
    "block.sculk_sensor.clicking": "block.wooden_button.click_on",
    "block.sculk_sensor.clicking_stop": "block.wooden_button.click_off",
    "block.sculk_sensor.fall": "block.stone.fall",
    "block.sculk_sensor.hit": "block.stone.hit",
    "block.sculk_sensor.place": "block.stone.place",
    "block.sculk_sensor.step": "block.stone.step",
    "block.small_amethyst_bud.break": "block.glass.break",
    "block.small_amethyst_bud.place": "block.glass.place",
    "entity.axolotl.attack": "entity.tropical_fish.flop",
    "entity.axolotl.death": "entity.tropical_fish.death",
    "entity.axolotl.hurt": "entity.tropical_fish.hurt",
    "entity.axolotl.idle_air": "entity.tropical_fish.ambient",
    "entity.axolotl.idle_water": "entity.tropical_fish.ambient",
    "entity.axolotl.splash": "entity.fish.swim",
    "entity.axolotl.swim": "entity.fish.swim",
    "entity.glow_item_frame.add_item": "entity.item_frame.add_item",
    "entity.glow_item_frame.break": "entity.item_frame.break",
    "entity.glow_item_frame.place": "entity.item_frame.place",
    "entity.glow_item_frame.remove_item": "entity.item_frame.remove_item",
    "entity.glow_item_frame.rotate_item": "entity.item_frame.rotate_item",
    "entity.glow_squid.ambient": "entity.squid.ambient",
    "entity.glow_squid.death": "entity.squid.death",
    "entity.glow_squid.hurt": "entity.squid.hurt",
    "entity.glow_squid.squirt": "entity.squid.squirt",
    "entity.goat.ambient": "entity.sheep.ambient",
    "entity.goat.death": "entity.sheep.death",
    "entity.goat.eat": "entity.sheep.ambient",
    "entity.goat.hurt": "entity.sheep.hurt",
    "entity.goat.long_jump": "entity.sheep.ambient",
    "entity.goat.milk": "entity.cow.milk",
    "entity.goat.prepare_ram": "entity.sheep.ambient",
    "entity.goat.ram_impact": "entity.sheep.hurt",
    "entity.goat.screaming.ambient": "entity.sheep.ambient",
    "entity.goat.screaming.death": "entity.sheep.death",
    "entity.goat.screaming.eat": "entity.sheep.ambient",
    "entity.goat.screaming.hurt": "entity.sheep.hurt",
    "entity.goat.screaming.long_jump": "entity.sheep.ambient",
    "entity.goat.screaming.milk": "entity.cow.milk",
    "entity.goat.screaming.prepare_ram": "entity.sheep.ambient",
    "entity.goat.screaming.ram_impact": "entity.sheep.hurt",
    "entity.goat.step": "entity.sheep.step",
    "entity.minecart.inside.underwater": "entity.minecart.inside",
    "entity.player.hurt_freeze": "entity.player.hurt",
    "entity.skeleton.converted_to_stray": "entity.skeleton.ambient",
    "item.axe.scrape": "item.axe.strip",
    "item.axe.wax_off": "item.axe.strip",
    "item.bone_meal.use": "block.grass.place",
    "item.bucket.empty_axolotl": "item.bucket.empty",
    "item.bucket.empty_powder_snow": "item.bucket.empty",
    "item.bucket.fill_axolotl": "item.bucket.fill",
    "item.bucket.fill_powder_snow": "item.bucket.fill",
    "item.dye.use": "block.grass.place",
    "item.glow_ink_sac.use": "block.grass.place",
    "item.honeycomb.wax_on": "entity.experience_orb.pickup",
    "item.ink_sac.use": "block.grass.place",
    "item.spyglass.stop_using": "entity.experience_orb.pickup",
    "item.spyglass.use": "entity.experience_orb.pickup",
})

# 1.17 の新パーティクルの代替先（近い既存のパーティクル）。
PARTICLE_FALLBACK_754 = {
    "dripping_dripstone_lava": "dripping_lava",
    "dripping_dripstone_water": "dripping_water",
    "falling_dripstone_lava": "falling_lava",
    "falling_dripstone_water": "falling_water",
    "dust_color_transition": "dust",
    "electric_spark": "crit",
    "glow": "enchant",
    "glow_squid_ink": "squid_ink",
    "light": "end_rod",
    "scrape": "crit",
    "small_flame": "flame",
    "snowflake": "white_ash",
    "falling_spore_blossom": "happy_villager",
    "spore_blossom_air": "happy_villager",
    "vibration": "note",
    "wax_off": "smoke",
    "wax_on": "happy_villager",
}

# バイオームの改名は無い（1.16.5 の 79 件はすべて 1.17 と同じ id・同じ名前）。
BIOME_RENAME_754 = {}

# バイオームの代替は無い（1.17 の新規バイオーム dripstone_caves・
# lush_caves は下のバイオーム節で平原（1）へ落とす）。
BIOME_FALLBACK_754 = {}

# バイオームの反転の代替は無い（1.16.5 で削除されたバイオームは無いため）。
REVERSE_BIOME_FALLBACK_754 = {}

# パーティクルの反転の代替は無い（1.16.5 の 72 件はすべて 1.17 に
# 同名で存在するため）。
REVERSE_PARTICLE_FALLBACK_754 = {}

KINDS = ["blockstates", "items", "entities", "sounds", "particles",
         "blocks", "menus"]
KIND_JAVA = {"blockstates": "BLOCK_STATE", "items": "ITEM",
             "entities": "ENTITY_TYPE", "sounds": "SOUND",
             "particles": "PARTICLE", "blocks": "BLOCK", "menus": "MENU"}


def normalize_blockstates(states):
    """ブロック状態の文字列のプロパティをソートして正規化する。

    755→754 ではブロック状態は順序の違いだけのため正規化は
    変化を起こさないが、755 の前例と同じ段取りで通す（順序だけ違う
    状態が将来の隣接で混ざっても写像の index は変わらない）。
    """
    out = []
    for s in states:
        if "[" not in s:
            out.append(s)
            continue
        name, rest = s.split("[", 1)
        props = sorted(kv.split("=", 1) for kv in rest[:-1].split(","))
        out.append(name + "[" + ",".join(f"{k}={v}" for k, v in props) + "]")
    return out


def emit_map(out, java_name, mapping, size, source_tag="755"):
    iv, ov = compress(mapping, size)
    out.append(java_int3(java_name + "_RANGES", iv))
    out.append(java_int2(java_name + "_OVERRIDES", ov))
    out.append(f"    // {java_name}: {source_tag} entries={size} ranges={len(iv)} "
               f"overrides={len(ov)}")
    return iv, ov


def registry_order(registries, key):
    return [k.split(":", 1)[1] for _, k in sorted(
        (v["protocol_id"], k)
        for k, v in registries[key]["entries"].items())]


def main():
    import argparse
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--data-dir", type=Path, default=DATA_DIR)
    args = parser.parse_args()
    data_dir = args.data_dir
    failures = []

    a754 = load(data_dir / "vv-1.16.json")
    a755 = load(data_dir / "vv-1.17.json")
    blocks116 = load(data_dir / "1.16_blocks.json")
    reg754 = load(data_dir / "registries-1.16.5.json")
    reg755 = load(data_dir / "registries-1.17.json")

    # 755側の代替先（UP_754_FALLBACKS）。名前から引く（755 の前例と同じ）。
    def idx(vv, kind, name):
        return vv[kind].index(name)

    up754_fallbacks = {
        "blockstates": 1,
        "items": 1,
        "entities": idx(a755, "entities", "pig"),
        "sounds": idx(a755, "sounds", "block.stone.break"),
        "particles": idx(a755, "particles", "entity_effect"),
        "blocks": 1,
        "menus": idx(a755, "menus", "crafting"),
    }
    print(f"[754] UP_754_FALLBACKS（755側）: {up754_fallbacks}")

    # ブロック状態のプロパティ順を正規化してから build_pair を呼ぶ。
    a754n = dict(a754)
    a755n = dict(a755)
    a754n["blockstates"] = normalize_blockstates(a754["blockstates"])
    a755n["blockstates"] = normalize_blockstates(a755["blockstates"])
    # build_pair は ITEM_FALLBACK（モジュールグローバル）をアイテムと
    # ブロックの代替に使うため、754版に差し替える。
    grt.ITEM_FALLBACK = ITEM_FALLBACK_754
    maps754, out754, fail754, rev754 = build_pair(
        "754", a754n, a755n, blocks116, data_dir,
        rename=RENAME_754, sound_fallbacks=SOUND_FALLBACK_754,
        particle_fallbacks=PARTICLE_FALLBACK_754,
        reverse_fallback=1, entity_fallbacks=ENTITY_FALLBACK_754,
        block_fallbacks=BLOCK_FALLBACK_754)
    failures += fail754
    if failures:
        print(f"\n未対応があります: {failures}", file=sys.stderr)
        return 1
    # build_pair はコメントに source バージョンを "765" と
    # ハードコードしている。754 の source は 755 のため書き換える。
    out754 = [line.replace(": 765 entries=", ": 755 entries=")
              for line in out754]

    # --- 代替で解決したブロック状態の監査：state763() は見つからない場合に
    # 黙って既定状態を返すため、代替先の（ブロック名＋プロパティ）が
    # 1.16.5 に実在することを別途確認する。---
    by_props16 = {}
    for i, s in enumerate(a754["blockstates"]):
        by_props16[(block_name(s), frozenset(block_props(s).items()))] = i
    audit_fail = []
    for i755, s755 in enumerate(a755["blockstates"]):
        name = block_name(s755)
        if name in BLOCK_FALLBACK_754:
            target, fn = BLOCK_FALLBACK_754[name]
            props = fn(block_props(s755)) if fn else {}
            if (target, frozenset(props.items())) not in by_props16:
                if fn is not None:
                    audit_fail.append((i755, s755, target, props))
    print(f"[754] blockstate fallback audit: {len(audit_fail)} 件の不整合 "
          f"{audit_fail[:5]}")
    if audit_fail:
        failures.append(("blockstate-fallback-audit", audit_fail[:5]))
        return 1

    out = ["    // Generated by tools/gen-754-tables.py. Do not edit by hand."]
    out += out754

    with open(data_dir / "maps754.json", "w", encoding="utf-8") as f:
        json.dump(maps754, f)
    with open(data_dir / "map_items_rev754.json", "w", encoding="utf-8") as f:
        json.dump({str(k): v for k, v in rev754.items()}, f)

    # --- 754→755 の反転表（名前の真値）。build_inverse は同名のものを
    # 優先するため、invert() の first-wins と違う場合がある。
    # ブロック状態は waterlogged=false（既定値）のものを優先する。---
    reverse_truth = {}
    for kind in ("blockstates", "entities", "sounds", "particles",
                 "blocks", "menus"):
        relaxed = {"waterlogged": "false"} if kind == "blockstates" else None
        inv = build_inverse(maps754[kind], len(a755[kind]), len(a754[kind]),
                            a755[kind], a754[kind], relaxed)
        missing = [i for i, v in enumerate(inv) if v < 0]
        if missing:
            print(f"[754] reverse {kind}: 対応なし {len(missing)} 件 "
                  f"{missing[:5]}（代替先 {up754_fallbacks[kind]} で埋める）")
            inv = [v if v >= 0 else up754_fallbacks[kind] for v in inv]
        reverse_truth[kind] = {i: v for i, v in enumerate(inv)}
    # パーティクル：754 の 72 件はすべて 755 に同名で存在するため、
    # 反転表は名前の真値と一致するはず。barrier（id 2）は両方とも
    # そのまま写る。
    barrier_id = a754["particles"].index("barrier")
    if reverse_truth["particles"][barrier_id] != a755["particles"].index("barrier"):
        print(f"[754] reverse particles: barrier({barrier_id})="
              f"{reverse_truth['particles'][barrier_id]}（同名写像でない）",
              file=sys.stderr)
        failures.append(("particles-reverse-identity", None))
        return 1
    # アイテムの反転は build_reverse_items の結果（名前ベース）を使う。
    reverse_truth["items"] = dict(rev754)
    # 音の改名の補正：build_inverse は名前が違うため改名を拾えない。
    # block.cave_vines.pick_berries（755）と block.sweet_berry_bush.
    # pick_berries（755）はどちらも 754 の item.sweet_berries.
    # pick_from_bush に写るため、first-wins では cave_vines 側が選ばれるが、
    # 名前の真値は改名先の block.sweet_berry_bush.pick_berries である。
    sweet754 = a754["sounds"].index("item.sweet_berries.pick_from_bush")
    sweet755 = a755["sounds"].index("block.sweet_berry_bush.pick_berries")
    if reverse_truth["sounds"][sweet754] != sweet755:
        print(f"[754] reverse sounds: sweet_berries の反転を改名先に修正 "
              f"({reverse_truth['sounds'][sweet754]} → {sweet755})")
        reverse_truth["sounds"][sweet754] = sweet755
    with open(data_dir / "maps754-reverse.json", "w", encoding="utf-8") as f:
        json.dump({k: {str(i): v for i, v in m.items()}
                   for k, m in reverse_truth.items()}, f)

    # --- invert() の結果と名前の真値の差分（補正）。wiring で
    # RegistryRemapper.invert() に渡す。---
    corrections = {}
    for kind in KINDS:
        ranges, overrides = compress(maps754[kind], len(a755[kind]))
        inv_list = invert_java(ranges, overrides, len(a755[kind]),
                               len(a754[kind]), up754_fallbacks[kind])
        corr = [[y, reverse_truth[kind][y]] for y in range(len(a754[kind]))
                if inv_list[y] != reverse_truth[kind][y]]
        corrections[kind] = corr
        print(f"[754] corrections {kind}: {len(corr)} 件 {corr[:5]}")
    with open(data_dir / "maps754-corrections.json", "w", encoding="utf-8") as f:
        json.dump(corrections, f)

    # 反転表を Java の表として出す（*_754_TO_755_*）。
    for kind in KINDS:
        emit_map(out, f"{KIND_JAVA[kind]}_754_TO_755",
                 reverse_truth[kind], len(a754[kind]), source_tag="754")
    # ITEM_REVERSE_754 と ITEM_754_TO_755 が同じことを確認する。
    if reverse_truth["items"] != {i: v for i, v in enumerate(
            build_inverse(maps754["items"], len(a755["items"]),
                          len(a754["items"]), a755["items"],
                          a754["items"]))}:
        print("[754] ITEM_REVERSE_754 と ITEM_754_TO_755 が違う",
              file=sys.stderr)
        failures.append(("item-reverse-mismatch", None))
        return 1

    # --- 音の名前の表（Stop Sound 用）。755→754 は SOUND_FALLBACK_754
    # そのもの。754→755 は名前の真値の反転表から作る（invert() の
    # first-wins では汎用の音が 1.17 の新音にすり替わるため、
    # sound_name_table_mid_to_target は使わない）。---
    for table_name, table in (
            ("SOUND_NAME_FALLBACKS_754", SOUND_FALLBACK_754),
            ("SOUND_NAME_FALLBACKS_755_TO_754", SOUND_FALLBACK_754)):
        lines = [f"    private static final String[][] {table_name} = {{"]
        for k, v in sorted(table.items()):
            lines.append(f'            {{"{k}", "{v}"}},')
        lines.append("    };")
        out.append("\n".join(lines))
    table_754_to_755 = {}
    for id754, name754 in enumerate(a754["sounds"]):
        id755 = reverse_truth["sounds"][id754]
        name755 = a755["sounds"][id755]
        if name755 != name754:
            table_754_to_755[name754] = name755
    lines = ["    private static final String[][] SOUND_NAME_FALLBACKS_754_TO_755 = {"]
    for k, v in sorted(table_754_to_755.items()):
        lines.append(f'            {{"{k}", "{v}"}},')
    lines.append("    };")
    out.append("\n".join(lines))
    print(f"[754] sound name table 754->755: {len(table_754_to_755)} entries")

    # --- 属性の表（registries.json の protocol_id 順で作る。1.16.5／1.17
    # とも 13件・同一順序のため恒等写像）。1.16.5 の Update Attributes は
    # 属性のキーを文字列で送る（mc-1.16-protocol.json の
    # packet_entity_update_attributes は "name": "string"。数値の mapper
    # は後のバージョンで導入）ため、1.16.5 には照合する mapper が無い。
    # 情報源は registries.json の protocol_id 順だけ（1.16.5／1.17 で
    # 同一順序なのは上で確認済み）。---
    attrs754 = registry_order(reg754, "minecraft:attribute")
    attrs755 = registry_order(reg755, "minecraft:attribute")
    if attrs754 != attrs755:
        print("[754] attributes: 1.16.5 と 1.17 の順序が違う", file=sys.stderr)
        failures.append(("attributes-order", (attrs754, attrs755)))
        return 1
    attr_755_to_754 = {i: i for i in range(len(attrs755))}
    attr_754_to_755 = {i: i for i in range(len(attrs754))}
    iv, ov = compress(attr_755_to_754, len(attrs755))
    out.append(java_int3("ATTRIBUTE_754_RANGES", iv))
    out.append(java_int2("ATTRIBUTE_754_OVERRIDES", ov))
    out.append(f"    // ATTRIBUTE_754: 755 entries={len(attrs755)} "
               f"ranges={len(iv)} overrides={len(ov)}")
    iv, ov = compress(attr_754_to_755, len(attrs754))
    out.append(java_int3("ATTRIBUTE_REVERSE_754_RANGES", iv))
    out.append(java_int2("ATTRIBUTE_REVERSE_754_OVERRIDES", ov))
    out.append(f"    // ATTRIBUTE_REVERSE_754: 754 entries={len(attrs754)} "
               f"ranges={len(iv)} overrides={len(ov)}")
    out.append(java_string_array("ATTRIBUTE_754_NAMES", attrs754))
    print(f"[754] attributes: 755={len(attrs755)} 754={len(attrs754)} identity")

    # --- エンチャントの表。1.16.5 と 1.17 は 38件・同一順序のため恒等写像。---
    ench754 = [e["name"] for e in load(data_dir / "enchantments-1.16.json")]
    ench755 = [e["name"] for e in load(data_dir / "enchantments-1.17.json")]
    if ench754 != a754["enchantments"] or ench755 != a755["enchantments"]:
        print("[754] enchantments: minecraft-data と ViaVersion の順序が違う",
              file=sys.stderr)
        failures.append(("enchantments-order", None))
        return 1
    ench_755_to_754 = {i: (ench754.index(n) if n in ench754 else -1)
                       for i, n in enumerate(ench755)}
    ench_754_to_755 = {i: (ench755.index(n) if n in ench755 else -1)
                       for i, n in enumerate(ench754)}
    iv, ov = compress(ench_755_to_754, len(ench755))
    out.append(java_int3("ENCHANTMENT_754_RANGES", iv))
    out.append(java_int2("ENCHANTMENT_754_OVERRIDES", ov))
    out.append(f"    // ENCHANTMENT_754: 755 entries={len(ench755)} "
               f"ranges={len(iv)} overrides={len(ov)}")
    iv, ov = compress(ench_754_to_755, len(ench754))
    out.append(java_int3("ENCHANTMENT_REVERSE_754_RANGES", iv))
    out.append(java_int2("ENCHANTMENT_REVERSE_754_OVERRIDES", ov))
    out.append(f"    // ENCHANTMENT_REVERSE_754: 754 entries={len(ench754)} "
               f"ranges={len(iv)} overrides={len(ov)}")
    print(f"[754] enchantments: 755={len(ench755)} 754={len(ench754)} identity")

    # --- バイオームの表。minecraft-data の biomes.json の id を使う
    # （biomes-1.16.json／biomes-1.17.json。1.16.5 は 79 件・id 0〜173、
    # 1.17 は 81 件・id 0〜175。0〜173 は同じ id・同じ名前。1.17 の新規
    # dripstone_caves（174）・lush_caves（175）は平原（1）へ落とす）。
    # registries.json には minecraft:worldgen/biome が無い（バイオームは
    # Join Game の dimensionCodec NBT で送られるため）。
    # 順向き・反転ともワイヤ id 空間で作る（755 の前例と同じ罠に注意。
    # build_name_map に None を含むリストを渡してはいけない）。
    # 順向き（755→754）は 755 側 176 件（id 0〜175）、反転（754→755）は
    # 754 側 174 件（id 0〜173）。隙間の id は平原（1）で埋める。---
    biomes754_by_id = {}
    for e in load(data_dir / "biomes-1.16.json"):
        biomes754_by_id[e["id"]] = e["name"]
    biomes755_by_id = {}
    for e in load(data_dir / "biomes-1.17.json"):
        biomes755_by_id[e["id"]] = e["name"]
    max_biome_755 = max(biomes755_by_id)
    max_biome_754 = max(biomes754_by_id)
    biomes755_list = [biomes755_by_id.get(i) for i in range(max_biome_755 + 1)]
    biomes754_list = [biomes754_by_id.get(i) for i in range(max_biome_754 + 1)]
    if (len([b for b in biomes754_list if b is not None]),
            len([b for b in biomes755_list if b is not None])) != (79, 81):
        print(f"[754] biomes: 件数が想定と違う "
              f"754={len([b for b in biomes754_list if b is not None])} "
              f"755={len([b for b in biomes755_list if b is not None])}",
              file=sys.stderr)
        failures.append(("biomes-count", None))
        return 1
    # 0〜173 は同じ id・同じ名前であることを確認する。
    shared_mismatch = [(i, biomes755_by_id.get(i), biomes754_by_id.get(i))
                       for i in range(max_biome_754 + 1)
                       if biomes755_by_id.get(i) != biomes754_by_id.get(i)]
    if shared_mismatch:
        print(f"[754] biomes: 0〜173 で id と名前が違うものがある：{shared_mismatch[:5]}",
              file=sys.stderr)
        failures.append(("biomes-shared", shared_mismatch[:5]))
        return 1
    m_bio = {w: (w if biomes754_by_id.get(w) is not None else 1)
             for w in range(max_biome_755 + 1)}
    emit_map(out, "BIOME_754", m_bio, len(biomes755_list), source_tag="755")
    rev_bio = build_inverse(m_bio, len(biomes755_list), len(biomes754_list),
                            biomes755_list, biomes754_list)
    biomes754_name_to_id = {n: i for i, n in biomes754_by_id.items()}
    biomes755_name_to_id = {n: i for i, n in biomes755_by_id.items()}
    patched_bio = 0
    for name754, name755 in REVERSE_BIOME_FALLBACK_754.items():
        id754 = biomes754_name_to_id[name754]
        id755 = biomes755_name_to_id[name755]
        if rev_bio[id754] < 0:
            rev_bio[id754] = id755
            patched_bio += 1
        elif rev_bio[id754] != id755:
            print(f"[754] biomes: 反転表の {name754}({id754}) が想定と違う："
                  f"{rev_bio[id754]} != {id755}", file=sys.stderr)
            failures.append(("biomes-reverse", (name754, rev_bio[id754], id755)))
            return 1
    print(f"[754] biomes reverse: 削除バイオームの明示 {patched_bio} 件")
    gaps = [i for i, b in enumerate(biomes754_list) if b is None]
    missing_bio = [i for i, v in enumerate(rev_bio)
                   if v < 0 and biomes754_list[i] is not None]
    if missing_bio:
        print(f"[754] biomes: 反転表に対応なしの実在バイオーム {missing_bio}",
              file=sys.stderr)
        failures.append(("biomes-reverse-missing", missing_bio))
        return 1
    rev_bio = [v if v >= 0 else 1 for v in rev_bio]
    print(f"[754] biomes reverse: 隙間の id {len(gaps)} 件は平原（755 id 1）で埋める")
    emit_map(out, "BIOME_754_TO_755",
             {i: v for i, v in enumerate(rev_bio)}, len(biomes754_list),
             source_tag="754")
    print(f"[754] biomes: 755={len([b for b in biomes755_list if b is not None])} "
          f"754={len(biomes754_by_id)} "
          f"(755 id 0〜{max_biome_755}、754 id 0〜{max_biome_754}、"
          f"754 側の隙間 {len(gaps)} 件)")

    # --- ダメージ種別（755 と同じ扱い）。damage_type レジストリは
    # 1.16.5／1.17 のどちらにも無い（1.19.4／762 で追加）。755 の前例
    # （RegistryTables755.java）にならい、762 の 42件の恒等写像を置くが
    # ワイヤ上の呼び出し元は無い。---
    dmg = {i: i for i in range(42)}
    emit_map(out, "DAMAGE_TYPE_754", dmg, 42, source_tag="755")
    emit_map(out, "DAMAGE_TYPE_754_TO_755", dmg, 42, source_tag="754")
    print("[754] damage_type: 762 の 42件の恒等写像（プレースホルダ）")

    # エフェクト（32→32、同一順序）は数値の表を作らない。エフェクトは
    # 755 の前例と同じく wiring 側で恒等写像にする（新規も削除も無いため
    # DROP は無い）。ブロックエンティティは 34→33 で 1.17 の新規
    # sculk_sensor（id 33）が 1.16.5 には無いため、表は作らず wiring 側で
    # 0〜32 は恒等写像・33 は DROP にする。ここでは件数だけ記録する。
    eff754 = registry_order(reg754, "minecraft:mob_effect")
    eff755 = registry_order(reg755, "minecraft:mob_effect")
    print(f"[754] effects: 755={len(eff755)} 754={len(eff754)} "
          f"identical={eff754 == eff755}")
    be754 = registry_order(reg754, "minecraft:block_entity_type")
    be755 = registry_order(reg755, "minecraft:block_entity_type")
    print(f"[754] block entities: 755={len(be755)} 754={len(be754)} "
          f"754-is-prefix={be754 == be755[:len(be754)]} "
          f"755-only={sorted(set(be755) - set(be754))}")

    text = "\n".join(out) + "\n"
    (data_dir / "gen754.txt").write_text(text, encoding="utf-8")
    print(f"Java の表を {data_dir / 'gen754.txt'} に書き出しました。")
    print("完了：未対応 0 件。")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
