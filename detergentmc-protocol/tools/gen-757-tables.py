#!/usr/bin/env python3
"""757（1.18／1.18.1）のレジストリ対応表（759→757）の生成。

777の前例（generate-remap-tables.py --target 777）にならうが、757は
最古バージョンのため合成表は作らない。理由は RegistryRemapper の
設計：763〜775 の組には直接の合成表があるが、759〜762 の組は
隣接表の連鎖だけ（mapBlockState759To775 は無く、759→760→761→
762→763 と繋ぐ）。757 は 759 の隣に加わり、757↔759 の隣接表だけを
作り、757↔763〜775 は 757→759→760→761→762→763→合成表の連鎖で
到達する。759 の前例（RegistryTables759.java 1ファイル、759↔760
の隣接表だけ）と同じ段取り。

使い方：
  python3 tools/gen-757-tables.py [--data-dir DIR]

入力（detergentmc-protocol/build/remap-data/。build-1.18-inputs.py
で作る）：
  - vv-1.18.json … ViaVersion mapping-1.18.json（menus／attributes 補完済み）
  - vv-1.19.json … ViaVersion mapping-1.19.json（同上）
  - 1.18_blocks.json … minecraft-data pc/1.18 の blocks.json
  - registries-1.18.json／registries-1.19.json … 実バニラの
    reports/registries.json のコピー
  - enchantments-1.18.json／enchantments-1.19.json … minecraft-data
  - mc-1.18-protocol.json … 属性名の照合用

出力（すべて --data-dir）：
  - gen757.txt … Java の表の宣言全文（insert-757-tables.py が読む）
  - maps757.json … 759→757 の対応表（7種＋reverse_items）
  - map_items_rev757.json … 757→759 のアイテム逆変換表
  - maps757-reverse.json … 757→759 の対応表（名前の真値。
    build_inverse で作る。同名のものを優先するため、invert() の
    first-wins と違う場合がある）
  - maps757-corrections.json … invert() の結果と名前の真値の差分
    （(757 id → 759 id) の対。wiring で invert() に渡す補正）

対応の決め方（generate-remap-tables.py の build_pair と同じ）：
  1. 名前（ブロック状態は名前＋プロパティ全体）がそのままある → その ID。
  2. プロパティを1個だけ落とすと一致する（1.19 で葉に waterlogged
     が追加された。8種×28状態＝224件）→ その ID。
  3. 下の *_757 代替表にある 1.19 の新ブロック／新アイテム／新音など
     → 近い既存のもの。
  4. それでも決まらないものがあれば「未対応」として一覧を出し、
     終了コード 1 で失敗する（黙って推測しない）。

1.18→1.19 で確認した差分（名前ベース。削除は 7種すべて 0件）：
  - ブロック状態 20,342→21,448（+1,106。新ブロック 35種の 994状態＋
    葉の waterlogged 224状態−112状態の重なり）
  - ブロック 898→933（新 35件）、アイテム 1,101→1,152（新 51件）、
    エンティティ 113→118（新 5件）、音 1,203→1,321（新 118件）、
    パーティクル 88→93（新 5件）、メニュー 24→24（同一順序）
  - 属性 13→13（同一順序）、エンチャント 38→39（swift_sneak を
    12番に挿入）、ブロックエンティティ 34→36（sculk_catalyst・
    sculk_shrieker）、バイオーム 61→63（mangrove_swamp を 7番・
    deep_dark を 52番に挿入）、エフェクト 32→33（darkness を末尾に追加）
  - 改名は 7種すべて 0件。ただしブロック状態の文字列のプロパティ順が
    1.19 で変わったものがある（chest／trapped_chest／piston_head／
    moving_piston の 84状態。例：chest[facing=…,type=…] →
    chest[type=…,facing=…]）。状態の同一性は（ブロック名＋プロパティ
    の集合）で決まるため、比較前にプロパティをソートして正規化する
    （normalize_blockstates）。順序だけの違いは改名ではない。
  - damage_type レジストリは 1.18／1.19 のどちらにも無い（1.19.4／
    762 で追加）。759 と同じく 762 の 42件の恒等写像を置くが、
    ワイヤ上の呼び出し元は無い。
  - data_component_type・environment_attribute・slot_display は
    1.18／1.19 のどちらにも無い（1.20.5 以降で追加）。
"""

import importlib.util
import json
import re
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


def leaves_props(p):
    """葉：1.19 で waterlogged が追加された。1.18 の葉には無いため落とす。"""
    return {"distance": p["distance"], "persistent": p["persistent"]}


def propagule_props(p):
    """mangrove_propagule → oak_sapling：苗木にあるのは stage だけ。"""
    return {"stage": p["stage"]}


# 759→757 は改名・削除が無い（ViaVersion mapping-1.19.json と
# mapping-1.18.json の名前の差分で確認。新規だけ）。
RENAME_757 = {}

# 1.19 の新ブロック 35種 → 1.18 の近いブロック。木材系は同種のオーク、
# 泥系は土・レンガ、スカルク系はディープスレート／スカルクセンサー、
# フロッグライトは発光ブロックのグロウストーン。
BLOCK_FALLBACK_757 = {
    "mangrove_planks": ("oak_planks", None),
    "mangrove_log": ("oak_log", same_props),
    "stripped_mangrove_log": ("stripped_oak_log", same_props),
    "mangrove_wood": ("oak_wood", same_props),
    "stripped_mangrove_wood": ("stripped_oak_wood", same_props),
    "mangrove_leaves": ("oak_leaves", leaves_props),
    "mangrove_propagule": ("oak_sapling", propagule_props),
    "potted_mangrove_propagule": ("potted_oak_sapling", None),
    "mangrove_roots": ("oak_planks", None),
    "muddy_mangrove_roots": ("dirt", None),
    "mud": ("dirt", None),
    "packed_mud": ("dirt", None),
    "mud_bricks": ("bricks", None),
    "mud_brick_stairs": ("brick_stairs", same_props),
    "mud_brick_slab": ("brick_slab", same_props),
    "mud_brick_wall": ("brick_wall", same_props),
    "sculk": ("deepslate", None),
    "sculk_vein": ("glow_lichen", same_props),
    "sculk_catalyst": ("deepslate", None),
    "sculk_shrieker": ("sculk_sensor", None),
    "mangrove_sign": ("oak_sign", same_props),
    "mangrove_wall_sign": ("oak_wall_sign", same_props),
    "mangrove_door": ("oak_door", same_props),
    "mangrove_trapdoor": ("oak_trapdoor", same_props),
    "mangrove_fence": ("oak_fence", same_props),
    "mangrove_fence_gate": ("oak_fence_gate", same_props),
    "mangrove_button": ("oak_button", same_props),
    "mangrove_pressure_plate": ("oak_pressure_plate", same_props),
    "mangrove_slab": ("oak_slab", same_props),
    "mangrove_stairs": ("oak_stairs", same_props),
    "ochre_froglight": ("glowstone", None),
    "verdant_froglight": ("glowstone", None),
    "pearlescent_froglight": ("glowstone", None),
    "frogspawn": ("lily_pad", None),
    "reinforced_deepslate": ("deepslate", None),
}

# 1.19 の新アイテム 51件 → 1.18 の近いアイテム。木材・泥・スカルク系は
# ブロックと同じ対応。チェスト付きボートは普通のボートへ。スポーンエッグは
# 同系統のモブへ（アレイ→ヴェックス、カエル・オタマジャクシ→ウーパールーパー、
# ウォーデン→ラヴェジャー）。回復のコンパス→コンパス、エコーシャード→
# アメジストシャード、ヤギの角笛・ディスク5・ディスクの破片→レコード13
# （1.18 で音を鳴らすアイテムの代替。ベストエフォート）。
ITEM_FALLBACK_757 = {
    "mud": "dirt",
    "mangrove_planks": "oak_planks",
    "mangrove_propagule": "oak_sapling",
    "mangrove_log": "oak_log",
    "mangrove_roots": "oak_planks",
    "muddy_mangrove_roots": "dirt",
    "stripped_mangrove_log": "stripped_oak_log",
    "stripped_mangrove_wood": "stripped_oak_wood",
    "mangrove_wood": "oak_wood",
    "mangrove_leaves": "oak_leaves",
    "mangrove_slab": "oak_slab",
    "mud_brick_slab": "brick_slab",
    "mangrove_fence": "oak_fence",
    "packed_mud": "dirt",
    "mud_bricks": "bricks",
    "reinforced_deepslate": "deepslate",
    "mud_brick_stairs": "brick_stairs",
    "sculk": "deepslate",
    "sculk_vein": "glow_lichen",
    "sculk_catalyst": "deepslate",
    "sculk_shrieker": "sculk_sensor",
    "mangrove_stairs": "oak_stairs",
    "mud_brick_wall": "brick_wall",
    "mangrove_button": "oak_button",
    "mangrove_pressure_plate": "oak_pressure_plate",
    "mangrove_door": "oak_door",
    "mangrove_trapdoor": "oak_trapdoor",
    "mangrove_fence_gate": "oak_fence_gate",
    "oak_chest_boat": "oak_boat",
    "spruce_chest_boat": "spruce_boat",
    "birch_chest_boat": "birch_boat",
    "jungle_chest_boat": "jungle_boat",
    "acacia_chest_boat": "acacia_boat",
    "dark_oak_chest_boat": "dark_oak_boat",
    "mangrove_boat": "oak_boat",
    "mangrove_chest_boat": "oak_boat",
    "mangrove_sign": "oak_sign",
    # 壁付きの看板と植木鉢入りの苗木はアイテムでは無いが、build_pair の
    # ブロックの代替は ITEM_FALLBACK から作るためここにも書く。
    "mangrove_wall_sign": "oak_wall_sign",
    "potted_mangrove_propagule": "potted_oak_sapling",
    "tadpole_bucket": "water_bucket",
    "recovery_compass": "compass",
    "allay_spawn_egg": "vex_spawn_egg",
    "frog_spawn_egg": "axolotl_spawn_egg",
    "tadpole_spawn_egg": "axolotl_spawn_egg",
    "warden_spawn_egg": "ravager_spawn_egg",
    "music_disc_5": "music_disc_13",
    "disc_fragment_5": "music_disc_13",
    "goat_horn": "music_disc_13",
    "ochre_froglight": "glowstone",
    "verdant_froglight": "glowstone",
    "pearlescent_froglight": "glowstone",
    "frogspawn": "lily_pad",
    "echo_shard": "amethyst_shard",
}

# 1.19 の新エンティティ 5件 → 1.18 の近いエンティティ。アレイは小さな
# 飛行モブのヴェックスへ、カエル・オタマジャクシは両生類のウーパールーパーへ、
# ウォーデンは大型の敵対モブのラヴェジャーへ、チェスト付きボートはボートへ。
ENTITY_FALLBACK_757 = {
    "allay": "vex",
    "chest_boat": "boat",
    "frog": "axolotl",
    "tadpole": "axolotl",
    "warden": "ravager",
}

# 1.19 の新パーティクル 5件。ソニックブームと叫びは爆発系へ、スカルク系は
# ソウル（魂）の粒子へ。
PARTICLE_FALLBACK_757 = {
    "sonic_boom": "explosion",
    "sculk_soul": "soul",
    "sculk_charge": "soul",
    "sculk_charge_pop": "soul",
    "shriek": "sweep_attack",
}

# 1.19 の新音 118件 → 1.18 の近い音。対応の考え方は 777 の
# SOUND_FALLBACK と同じ（同系統のモブ／ブロックの音へ）。
SOUND_FALLBACK_757 = {
    # アレイ（小さな飛行モブ）→ ヴェックス。アイテムの受け渡しは小さいチャイム音へ。
    "entity.allay.ambient_with_item": "entity.vex.ambient",
    "entity.allay.ambient_without_item": "entity.vex.ambient",
    "entity.allay.death": "entity.vex.death",
    "entity.allay.hurt": "entity.vex.hurt",
    "entity.allay.item_given": "entity.experience_orb.pickup",
    "entity.allay.item_taken": "entity.experience_orb.pickup",
    "entity.allay.item_thrown": "entity.vex.charge",
    # オタマジャクシのバケツ → ウーパールーパーのバケツ。
    "item.bucket.empty_tadpole": "item.bucket.empty_axolotl",
    "item.bucket.fill_tadpole": "item.bucket.fill_axolotl",
    # フロッグライト（発光ブロック）→ ガラス。
    "block.froglight.break": "block.glass.break",
    "block.froglight.fall": "block.glass.fall",
    "block.froglight.hit": "block.glass.hit",
    "block.froglight.place": "block.glass.place",
    "block.froglight.step": "block.glass.step",
    # カエルの卵（ぬるぬるした卵塊）→ スライムブロック。
    "block.frogspawn.step": "block.slime_block.step",
    "block.frogspawn.break": "block.slime_block.break",
    "block.frogspawn.fall": "block.slime_block.fall",
    "block.frogspawn.hatch": "block.slime_block.break",
    "block.frogspawn.hit": "block.slime_block.hit",
    "block.frogspawn.place": "block.slime_block.place",
    # カエル（両生類）→ ウーパールーパー。
    "entity.frog.ambient": "entity.axolotl.idle_water",
    "entity.frog.death": "entity.axolotl.death",
    "entity.frog.eat": "entity.axolotl.attack",
    "entity.frog.hurt": "entity.axolotl.hurt",
    "entity.frog.lay_spawn": "entity.axolotl.splash",
    "entity.frog.long_jump": "entity.axolotl.swim",
    "entity.frog.step": "entity.axolotl.swim",
    "entity.frog.tongue": "entity.axolotl.attack",
    # ヤギの角笛。角が折れる音 → アイテム破壊音。角笛を吹く音 → 低いノートブロックの音。
    "entity.goat.horn_break": "entity.item.break",
    "entity.goat.screaming.horn_break": "entity.item.break",
    "item.goat_horn.play": "block.note_block.bass",
    "item.goat_horn.sound.0": "block.note_block.bass",
    "item.goat_horn.sound.1": "block.note_block.bass",
    "item.goat_horn.sound.2": "block.note_block.bass",
    "item.goat_horn.sound.3": "block.note_block.bass",
    "item.goat_horn.sound.4": "block.note_block.bass",
    "item.goat_horn.sound.5": "block.note_block.bass",
    "item.goat_horn.sound.6": "block.note_block.bass",
    "item.goat_horn.sound.7": "block.note_block.bass",
    # マングローブの根（木材）→ 木。
    "block.mangrove_roots.break": "block.wood.break",
    "block.mangrove_roots.fall": "block.wood.fall",
    "block.mangrove_roots.hit": "block.wood.hit",
    "block.mangrove_roots.place": "block.wood.place",
    "block.mangrove_roots.step": "block.wood.step",
    # 泥・固めた泥（土系。1.18 の土は砂利の音）→ 砂利。
    "block.mud.break": "block.gravel.break",
    "block.mud.fall": "block.gravel.fall",
    "block.mud.hit": "block.gravel.hit",
    "block.mud.place": "block.gravel.place",
    "block.mud.step": "block.gravel.step",
    "block.packed_mud.break": "block.gravel.break",
    "block.packed_mud.fall": "block.gravel.fall",
    "block.packed_mud.hit": "block.gravel.hit",
    "block.packed_mud.place": "block.gravel.place",
    "block.packed_mud.step": "block.gravel.step",
    # 泥レンガ（レンガ系。1.18 のレンガは石の音）→ 石。
    "block.mud_bricks.break": "block.stone.break",
    "block.mud_bricks.fall": "block.stone.fall",
    "block.mud_bricks.hit": "block.stone.hit",
    "block.mud_bricks.place": "block.stone.place",
    "block.mud_bricks.step": "block.stone.step",
    # 泥だらけのマングローブの根 → 木。
    "block.muddy_mangrove_roots.break": "block.wood.break",
    "block.muddy_mangrove_roots.fall": "block.wood.fall",
    "block.muddy_mangrove_roots.hit": "block.wood.hit",
    "block.muddy_mangrove_roots.place": "block.wood.place",
    "block.muddy_mangrove_roots.step": "block.wood.step",
    # レコード5 → レコード13。新しいBGM → 1.18 の近いBGM（ベストエフォート）。
    "music_disc.5": "music_disc.13",
    "music.overworld.deep_dark": "music.overworld.dripstone_caves",
    "music.overworld.swamp": "music.overworld.lush_caves",
    "music.overworld.jungle_and_forest": "music.overworld.grove",
    "music.overworld.old_growth_taiga": "music.overworld.snowy_slopes",
    # オウムのウォーデンの真似 → ラヴェジャーの真似。
    "entity.parrot.imitate.warden": "entity.parrot.imitate.ravager",
    # スカルク（深層岩系）。拡散・蓄積はスカルクセンサーのクリック音へ。
    "block.sculk.spread": "block.sculk_sensor.clicking",
    "block.sculk.charge": "block.sculk_sensor.clicking_stop",
    "block.sculk.break": "block.deepslate.break",
    "block.sculk.fall": "block.deepslate.fall",
    "block.sculk.hit": "block.deepslate.hit",
    "block.sculk.place": "block.deepslate.place",
    "block.sculk.step": "block.deepslate.step",
    "block.sculk_catalyst.bloom": "block.sculk_sensor.clicking",
    "block.sculk_catalyst.break": "block.deepslate.break",
    "block.sculk_catalyst.fall": "block.deepslate.fall",
    "block.sculk_catalyst.hit": "block.deepslate.hit",
    "block.sculk_catalyst.place": "block.deepslate.place",
    "block.sculk_catalyst.step": "block.deepslate.step",
    "block.sculk_shrieker.break": "block.deepslate.break",
    "block.sculk_shrieker.fall": "block.deepslate.fall",
    "block.sculk_shrieker.hit": "block.deepslate.hit",
    "block.sculk_shrieker.place": "block.deepslate.place",
    "block.sculk_shrieker.shriek": "entity.ghast.scream",
    "block.sculk_shrieker.step": "block.deepslate.step",
    # スカルクヴェイン（壁の成長物）→ 洞窟のツタ。
    "block.sculk_vein.break": "block.cave_vines.break",
    "block.sculk_vein.fall": "block.cave_vines.fall",
    "block.sculk_vein.hit": "block.cave_vines.hit",
    "block.sculk_vein.place": "block.cave_vines.place",
    "block.sculk_vein.step": "block.cave_vines.step",
    # オタマジャクシ → ウーパールーパー（バシャバシャはタラの音）。
    "entity.tadpole.death": "entity.axolotl.death",
    "entity.tadpole.flop": "entity.cod.flop",
    "entity.tadpole.grow_up": "entity.axolotl.splash",
    "entity.tadpole.hurt": "entity.axolotl.hurt",
    # ウォーデン（大型の敵対モブ）→ ラヴェジャー。
    "entity.warden.agitated": "entity.ravager.ambient",
    "entity.warden.ambient": "entity.ravager.ambient",
    "entity.warden.angry": "entity.ravager.roar",
    "entity.warden.attack_impact": "entity.ravager.attack",
    "entity.warden.death": "entity.ravager.death",
    "entity.warden.dig": "entity.ravager.step",
    "entity.warden.emerge": "entity.ravager.roar",
    "entity.warden.heartbeat": "entity.ravager.ambient",
    "entity.warden.hurt": "entity.ravager.hurt",
    "entity.warden.listening": "entity.ravager.ambient",
    "entity.warden.listening_angry": "entity.ravager.ambient",
    "entity.warden.nearby_close": "entity.ravager.ambient",
    "entity.warden.nearby_closer": "entity.ravager.ambient",
    "entity.warden.nearby_closest": "entity.ravager.ambient",
    "entity.warden.roar": "entity.ravager.roar",
    "entity.warden.sniff": "entity.ravager.ambient",
    "entity.warden.sonic_boom": "entity.ravager.attack",
    "entity.warden.sonic_charge": "entity.ravager.ambient",
    "entity.warden.step": "entity.ravager.step",
    "entity.warden.tendril_clicks": "entity.ravager.ambient",
}

# バイオームの代替（759→757）。マングローブの沼 → 沼、深層暗黒 → 滴石洞窟。
BIOME_FALLBACK_757 = {
    "mangrove_swamp": "swamp",
    "deep_dark": "dripstone_caves",
}

KINDS = ["blockstates", "items", "entities", "sounds", "particles",
         "blocks", "menus"]
KIND_JAVA = {"blockstates": "BLOCK_STATE", "items": "ITEM",
             "entities": "ENTITY_TYPE", "sounds": "SOUND",
             "particles": "PARTICLE", "blocks": "BLOCK", "menus": "MENU"}


def normalize_blockstates(states):
    """ブロック状態の文字列のプロパティをソートして正規化する。

    1.18→1.19 で chest／trapped_chest／piston_head／moving_piston の
    プロパティ順だけが変わった（84状態）。状態の同一性は（ブロック名＋
    プロパティの集合）で決まるため、順序をソートしても写像の index は
    変わらない。build_blockstates の完全一致の比較だけが順序に依存する。
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


def emit_map(out, java_name, mapping, size):
    iv, ov = compress(mapping, size)
    out.append(java_int3(java_name + "_RANGES", iv))
    out.append(java_int2(java_name + "_OVERRIDES", ov))
    out.append(f"    // {java_name}: 759 entries={size} ranges={len(iv)} "
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

    a757 = load(data_dir / "vv-1.18.json")
    a759 = load(data_dir / "vv-1.19.json")
    blocks118 = load(data_dir / "1.18_blocks.json")
    reg18 = load(data_dir / "registries-1.18.json")
    reg19 = load(data_dir / "registries-1.19.json")

    # 759側の代替先（UP_757_FALLBACKS）。名前から引く（777 の前例と同じ）。
    def idx(vv, kind, name):
        return vv[kind].index(name)

    up757_fallbacks = {
        "blockstates": 1,
        "items": 1,
        "entities": idx(a759, "entities", "pig"),
        "sounds": idx(a759, "sounds", "block.stone.break"),
        "particles": idx(a759, "particles", "entity_effect"),
        "blocks": 1,
        "menus": idx(a759, "menus", "crafting"),
    }
    print(f"[757] UP_757_FALLBACKS（759側）: {up757_fallbacks}")

    # ブロック状態のプロパティ順を正規化してから build_pair を呼ぶ。
    a757n = dict(a757)
    a759n = dict(a759)
    a757n["blockstates"] = normalize_blockstates(a757["blockstates"])
    a759n["blockstates"] = normalize_blockstates(a759["blockstates"])
    # build_pair は ITEM_FALLBACK（モジュールグローバル）をアイテムと
    # ブロックの代替に使うため、757版に差し替える。
    grt.ITEM_FALLBACK = ITEM_FALLBACK_757
    maps757, out757, fail757, rev757 = build_pair(
        "757", a757n, a759n, blocks118, data_dir,
        rename=RENAME_757, sound_fallbacks=SOUND_FALLBACK_757,
        particle_fallbacks=PARTICLE_FALLBACK_757,
        reverse_fallback=1, entity_fallbacks=ENTITY_FALLBACK_757,
        block_fallbacks=BLOCK_FALLBACK_757)
    failures += fail757
    if failures:
        print(f"\n未対応があります: {failures}", file=sys.stderr)
        return 1
    # 775版の build_pair はコメントに source バージョンを "765" と
    # ハードコードしている。757 の source は 759 のため書き換える。
    out757 = [line.replace(": 765 entries=", ": 759 entries=")
              for line in out757]

    # --- 代替で解決したブロック状態の監査：state763() は見つからない場合に
    # 黙って既定状態を返すため、代替先の（ブロック名＋プロパティ）が
    # 1.18 に実在することを別途確認する。---
    by_props18 = {}
    for i, s in enumerate(a757["blockstates"]):
        by_props18[(block_name(s), frozenset(block_props(s).items()))] = i
    audit_fail = []
    for i759, s759 in enumerate(a759["blockstates"]):
        name = block_name(s759)
        if name in BLOCK_FALLBACK_757:
            target, fn = BLOCK_FALLBACK_757[name]
            props = fn(block_props(s759)) if fn else {}
            if (target, frozenset(props.items())) not in by_props18:
                # fn が None の場合は既定状態を使う（正しい動作）。
                if fn is not None:
                    audit_fail.append((i759, s759, target, props))
    print(f"[757] blockstate fallback audit: {len(audit_fail)} 件の不整合 "
          f"{audit_fail[:5]}")
    if audit_fail:
        failures.append(("blockstate-fallback-audit", audit_fail[:5]))
        return 1

    out = ["    // Generated by tools/gen-757-tables.py. Do not edit by hand."]
    out += out757

    with open(data_dir / "maps757.json", "w", encoding="utf-8") as f:
        json.dump(maps757, f)
    with open(data_dir / "map_items_rev757.json", "w", encoding="utf-8") as f:
        json.dump({str(k): v for k, v in rev757.items()}, f)

    # --- 757→759 の反転表（名前の真値）。build_inverse は同名のものを
    # 優先するため、invert() の first-wins と違う場合がある（例：757 の
    # block.stone.break には 759 の block.mud_bricks.break が先に
    # 拾われる。アルファベット順で mud_bricks < stone のため）。
    # ブロック状態は waterlogged=false（既定値）のものを優先する。---
    reverse_truth = {}
    for kind in ("blockstates", "entities", "sounds", "particles",
                 "blocks", "menus"):
        relaxed = {"waterlogged": "false"} if kind == "blockstates" else None
        inv = build_inverse(maps757[kind], len(a759[kind]), len(a757[kind]),
                            a759[kind], a757[kind], relaxed)
        missing = [i for i, v in enumerate(inv) if v < 0]
        if missing:
            print(f"[757] reverse {kind}: 対応なし {len(missing)} 件 "
                  f"{missing[:5]}（代替先 {up757_fallbacks[kind]} で埋める）")
            inv = [v if v >= 0 else up757_fallbacks[kind] for v in inv]
        reverse_truth[kind] = {i: v for i, v in enumerate(inv)}
    # アイテムの反転は build_reverse_items の結果（名前ベース）を使う。
    reverse_truth["items"] = dict(rev757)
    with open(data_dir / "maps757-reverse.json", "w", encoding="utf-8") as f:
        json.dump({k: {str(i): v for i, v in m.items()}
                   for k, m in reverse_truth.items()}, f)

    # --- invert() の結果と名前の真値の差分（補正）。wiring で
    # RegistryRemapper.invert() に渡す。---
    corrections = {}
    for kind in KINDS:
        ranges, overrides = compress(maps757[kind], len(a759[kind]))
        inv_list = invert_java(ranges, overrides, len(a759[kind]),
                               len(a757[kind]), up757_fallbacks[kind])
        corr = [[y, reverse_truth[kind][y]] for y in range(len(a757[kind]))
                if inv_list[y] != reverse_truth[kind][y]]
        corrections[kind] = corr
        print(f"[757] corrections {kind}: {len(corr)} 件 {corr[:5]}")
    with open(data_dir / "maps757-corrections.json", "w", encoding="utf-8") as f:
        json.dump(corrections, f)

    # 反転表を Java の表として出す（*_757_TO_759_*）。
    for kind in KINDS:
        emit_map(out, f"{KIND_JAVA[kind]}_757_TO_759",
                 reverse_truth[kind], len(a757[kind]))
    # ITEM_REVERSE_757 と ITEM_757_TO_759 が同じことを確認する。
    if reverse_truth["items"] != {i: v for i, v in enumerate(
            build_inverse(maps757["items"], len(a759["items"]),
                          len(a757["items"]), a759["items"],
                          a757["items"]))}:
        print("[757] ITEM_REVERSE_757 と ITEM_757_TO_759 が違う",
              file=sys.stderr)
        failures.append(("item-reverse-mismatch", None))
        return 1

    # --- 音の名前の表（Stop Sound 用）。759→757 は SOUND_FALLBACK_757
    # そのもの。757→759 は名前の真値の反転表から作る（invert() の
    # first-wins では石の破壊音が泥レンガの音にすり替わるため、
    # sound_name_table_mid_to_target は使わない）。---
    for table_name, table in (
            ("SOUND_NAME_FALLBACKS_757", SOUND_FALLBACK_757),
            ("SOUND_NAME_FALLBACKS_759_TO_757", SOUND_FALLBACK_757)):
        lines = [f"    private static final String[][] {table_name} = {{"]
        for k, v in sorted(table.items()):
            lines.append(f'            {{"{k}", "{v}"}},')
        lines.append("    };")
        out.append("\n".join(lines))
    table_757_to_759 = {}
    for id757, name757 in enumerate(a757["sounds"]):
        id759 = reverse_truth["sounds"][id757]
        name759 = a759["sounds"][id759]
        if name759 != name757:
            table_757_to_759[name757] = name759
    lines = ["    private static final String[][] SOUND_NAME_FALLBACKS_757_TO_759 = {"]
    for k, v in sorted(table_757_to_759.items()):
        lines.append(f'            {{"{k}", "{v}"}},')
    lines.append("    };")
    out.append("\n".join(lines))
    print(f"[757] sound name table 757->759: {len(table_757_to_759)} entries")

    # --- 属性の表（759 は mc-1.19-protocol.json が無いため registries.json
    # の protocol_id 順で作る。1.18／1.19 とも 13件・同一順序のため恒等写像）。
    # 775 の前例では ATTRIBUTE_775_NAMES を protocol.json の key mapper と
    # 照合したが、1.18 の Update Attributes は属性のキーを文字列で送る
    # （mc-1.18-protocol.json の packet_entity_update_attributes は
    # "name": "string"。数値の mapper は後のバージョンで導入）ため、
    # 1.18 には照合する mapper が無い。情報源は registries.json の
    # protocol_id 順だけ（1.18／1.19 で同一順序なのは上で確認済み）。
    attrs757 = registry_order(reg18, "minecraft:attribute")
    attrs759 = registry_order(reg19, "minecraft:attribute")
    if attrs757 != attrs759:
        print("[757] attributes: 1.18 と 1.19 の順序が違う", file=sys.stderr)
        failures.append(("attributes-order", (attrs757, attrs759)))
        return 1
    attr_759_to_757 = {i: i for i in range(len(attrs759))}
    attr_757_to_759 = {i: i for i in range(len(attrs757))}
    iv, ov = compress(attr_759_to_757, len(attrs759))
    out.append(java_int3("ATTRIBUTE_757_RANGES", iv))
    out.append(java_int2("ATTRIBUTE_757_OVERRIDES", ov))
    out.append(f"    // ATTRIBUTE_757: 759 entries={len(attrs759)} "
               f"ranges={len(iv)} overrides={len(ov)}")
    iv, ov = compress(attr_757_to_759, len(attrs757))
    out.append(java_int3("ATTRIBUTE_REVERSE_757_RANGES", iv))
    out.append(java_int2("ATTRIBUTE_REVERSE_757_OVERRIDES", ov))
    out.append(f"    // ATTRIBUTE_REVERSE_757: 757 entries={len(attrs757)} "
               f"ranges={len(iv)} overrides={len(ov)}")
    out.append(java_string_array("ATTRIBUTE_757_NAMES", attrs757))
    print(f"[757] attributes: 759={len(attrs759)} 757={len(attrs757)} identity")

    # --- エンチャントの表。1.19 で swift_sneak が 12番に挿入されただけ。---
    ench757 = [e["name"] for e in load(data_dir / "enchantments-1.18.json")]
    ench759 = [e["name"] for e in load(data_dir / "enchantments-1.19.json")]
    if ench757 != a757["enchantments"] or ench759 != a759["enchantments"]:
        print("[757] enchantments: minecraft-data と ViaVersion の順序が違う",
              file=sys.stderr)
        failures.append(("enchantments-order", None))
        return 1
    ench_759_to_757 = {i: (ench757.index(n) if n in ench757 else -1)
                       for i, n in enumerate(ench759)}
    ench_757_to_759 = {i: (ench759.index(n) if n in ench759 else -1)
                       for i, n in enumerate(ench757)}
    iv, ov = compress(ench_759_to_757, len(ench759))
    out.append(java_int3("ENCHANTMENT_757_RANGES", iv))
    out.append(java_int2("ENCHANTMENT_757_OVERRIDES", ov))
    out.append(f"    // ENCHANTMENT_757: 759 entries={len(ench759)} "
               f"ranges={len(iv)} overrides={len(ov)}")
    iv, ov = compress(ench_757_to_759, len(ench757))
    out.append(java_int3("ENCHANTMENT_REVERSE_757_RANGES", iv))
    out.append(java_int2("ENCHANTMENT_REVERSE_757_OVERRIDES", ov))
    out.append(f"    // ENCHANTMENT_REVERSE_757: 757 entries={len(ench757)} "
               f"ranges={len(iv)} overrides={len(ov)}")
    print(f"[757] enchantments: 759={len(ench759)} 757={len(ench757)} "
          f"swift_sneak->{ench_759_to_757[ench759.index('swift_sneak')]}")

    # --- バイオームの表。minecraft-data の biomes.json の id 順を使う
    # （build/remap-data/biomes-1.18.json／biomes-1.19.json）。
    # registries.json には minecraft:worldgen/biome が無い（バイオームは
    # Join Game の dimensionCodec NBT で送られるため）。
    biomes757 = [e["name"] for e in sorted(
        load(data_dir / "biomes-1.18.json"), key=lambda e: e["id"])]
    biomes759 = [e["name"] for e in sorted(
        load(data_dir / "biomes-1.19.json"), key=lambda e: e["id"])]
    if (len(biomes757), len(biomes759)) != (61, 63):
        print(f"[757] biomes: 件数が想定と違う "
              f"757={len(biomes757)} 759={len(biomes759)}", file=sys.stderr)
        failures.append(("biomes-count", None))
        return 1
    m_bio, _, unm_bio = build_name_map(
        "biomes", {"biomes": biomes757}, {"biomes": biomes759},
        BIOME_FALLBACK_757)
    if unm_bio:
        print(f"[757] biomes: 未対応 {unm_bio}", file=sys.stderr)
        failures.append(("biomes", unm_bio))
        return 1
    emit_map(out, "BIOME_757", m_bio, len(biomes759))
    rev_bio = build_inverse(m_bio, len(biomes759), len(biomes757),
                            biomes759, biomes757)
    if any(v < 0 for v in rev_bio):
        print("[757] biomes: 反転表に対応なしがある", file=sys.stderr)
        failures.append(("biomes-reverse", None))
        return 1
    emit_map(out, "BIOME_757_TO_759", {i: v for i, v in enumerate(rev_bio)},
             len(biomes757))
    print(f"[757] biomes: 759={len(biomes759)} 757={len(biomes757)}")

    # --- ダメージ種別（759 と同じ扱い）。damage_type レジストリは
    # 1.18／1.19 のどちらにも無い（1.19.4／762 で追加）。759 の前例
    # （RegistryTables759.java）にならい、762 の 42件の恒等写像を置くが
    # ワイヤ上の呼び出し元は無い。---
    dmg = {i: i for i in range(42)}
    emit_map(out, "DAMAGE_TYPE_757", dmg, 42)
    emit_map(out, "DAMAGE_TYPE_757_TO_759", dmg, 42)
    print("[757] damage_type: 762 の 42件の恒等写像（プレースホルダ）")

    # エフェクト（32→33、darkness を末尾に追加）とブロックエンティティ
    # （34→36）は数値の表を作らない。エフェクトは 775 の前例と同じく
    # wiring 側で恒等＋DROP（darkness→-1）にする。ブロックエンティティも
    # 新 2件は DROP。ここでは件数だけ記録する。
    eff18 = registry_order(reg18, "minecraft:mob_effect")
    eff19 = registry_order(reg19, "minecraft:mob_effect")
    print(f"[757] effects: 759={len(eff19)} 757={len(eff18)} "
          f"new={[n for n in eff19 if n not in eff18]}")
    be18 = registry_order(reg18, "minecraft:block_entity_type")
    be19 = registry_order(reg19, "minecraft:block_entity_type")
    print(f"[757] block entities: 759={len(be19)} 757={len(be18)} "
          f"new={[n for n in be19 if n not in be18]}")

    text = "\n".join(out) + "\n"
    (data_dir / "gen757.txt").write_text(text, encoding="utf-8")
    print(f"Java の表を {data_dir / 'gen757.txt'} に書き出しました。")
    print("完了：未対応 0 件。")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
