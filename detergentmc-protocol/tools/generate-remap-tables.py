#!/usr/bin/env python3
"""
DetergentMC 765 -> 763 / 764 レジストリ変換表の生成スクリプト。

RegistryRemapper.java に埋め込まれている対応表（区間＋上書き）の元になる
スクリプト。表を作り直すときはこのスクリプトだけ直して再生成し、
出力を RegistryRemapper.java の「// __TABLES__」相当の場所に貼る。

対応する組は2つある。765（1.20.4／1.20.3）→763（1.20.1）と
765→764（1.20.2）で、どちらも同じ代替表（*_FALLBACK）を使う。
764は763より765にずっと近い（ブロック状態は改名1件＋新ブロック55種
のみの差。エフェクトは764も765も0ベースなので変換表は要らない）。
両方の組を一度に生成し、763側の表が変わっていないことも確認する。

情報源（推測で埋めない。すべて名前で突き合わせる）：
  - ViaVersion Mappings（Mojang の data-generator レポート由来。
    リストの index がレジストリ ID）：
      https://raw.githubusercontent.com/ViaVersion/Mappings/main/mappings/mapping-1.20.json   （763）
      https://raw.githubusercontent.com/ViaVersion/Mappings/main/mappings/mapping-1.20.3.json （765）
      https://raw.githubusercontent.com/ViaVersion/Mappings/main/mappings/mapping-1.20.5.json （766）
    1.20.4 はブロック・アイテム・エンティティ・プロトコルが 1.20.3 と
    同じ（minecraft-data の dataPaths.json による）ため 1.20.3 を使う。
    1.20.6 は 1.20.5 とプロトコル 766 を共有するため 1.20.5 を使う。
  - PrismarineJS minecraft-data（1.20 の blocks.json の defaultState だけ
    使う。代替ブロックの既定状態を引くため）：
      https://raw.githubusercontent.com/PrismarineJS/minecraft-data/master/data/pc/1.20/blocks.json

対応の決め方（優先順）：
  1. 765側の名前（ブロック状態は名前＋プロパティ全体）が 763側に
     そのままある → その ID。
  2. 名前変更だけ（short_grass -> grass）→ 改名前の ID。
  3. プロパティを1個だけ落とすと 763側に一致する（1.20.2 で
     skulls に powered、barrier に waterlogged が追加された）→ その ID。
  4. 下の *_FALLBACK 表にある 1.20.2／1.20.3 の新ブロック → 近い既存ブロック。
  5. それでも決まらないものがあれば「未対応」として一覧を出し、
     exit 1 で失敗する（黙って推測しない）。

766（1.20.5／1.20.6）向けの組（765→766）も同じ作り方で作る。
766 は 765 に近く、差分は小さい（ViaVersion mapping-1.20.5.json と
mapping-1.20.3.json の突き合わせ）。ブロック状態は trial_spawner に
ominous が追加された6状態と新ブロック vault（32状態）・heavy_core
（2状態）のみ。アイテムは scute→turtle_scute の改名1件と新19件、
エンティティは armadillo／bogged／breeze_wind_charge／
ominous_item_spawner の4件、音は新69件・削除1件、パーティクルは
新11件・削除3件、ブロックは vault／heavy_core の2件、
ブロックエンティティは vault の1件。エフェクトは末尾に6件追加
（TrialOmen／RaidOmen／WindCharged／Weaving／Oozing／Infested）のみで
0〜32 は同一のため表は要らない（恒等写像）。バイオームも同一順序。
765→766 では 766 に無いものだけ代替が必要で、代替表は
*_766（SOUND_FALLBACK_766／PARTICLE_FALLBACK_766／RENAME_766／
ADD_PROPS_766）を使う。766→765 の逆変換表は名前から作り、新規の
766アイテム（mace など 18件）は石（1）へ落とす（黙って推測せず、
一覧を出してから落とす）。766→763／766→764 の音の名前の表も、
数値の表（766→765 の反転＋765→763／764）から機械的に作る。

使い方：
  python3 tools/generate-remap-tables.py [--data-dir DIR] [--out FILE] [--target 763|764|766|767|768|both]
  --data-dir を省略すると detergentmc-protocol/build/remap-data を使う。
  --out を省略すると Java の表を標準出力に出す。
  --target は生成する組（既定は両方）。763側の表だけ作り直すときは
  --target 763 を使う。--target 766 は 766→763／766→764 の音の
  名前の表を作るために 763／764 の対応表も内部で作る（出力に
  含まれるのは 766 の表だけ）。--target 767 は 766→767 の表と
  763／764／765↔767 の合成表・音の名前の表・属性とエンチャントの
  表を作る（763／764／765→766 の表も内部で作るが、出力に含まれる
  のは 767 の表だけ）。--target 768 は 767→768 の表と
  763／764／765／766↔768 の合成表・音の名前の表・属性とエンチャントの
  表を作る（763／764／765→767 の表も内部で作るが、出力に含まれる
  のは 768 の表だけ）。1.21.2 と 1.21.3 はプロトコル 768 を共有し、
  minecraft-data に pc/1.21.2 が無いため 768 のデータはすべて
  1.21.3 のもの（vv-1.21.2.json／1.21.3_blocks.json／
  mc-1.21.3-protocol.json）を使う。--target 772 は 771→772 の表と
  763〜771↔772 の合成表・音の名前の表・属性とエンチャントの表を作る
  （--target 771 の実行結果が要る）。1.21.7 と 1.21.8 はプロトコル
  772 を共有し、minecraft-data に pc/1.21.7 が無いため 772 のデータ
  はすべて 1.21.8 のもの（vv-1.21.7.json／1.21.8_blocks.json／
  mc-1.21.8-protocol.json）を使う。--target 773 は 772→773 の表と
  763〜772↔773 の合成表・音の名前の表・属性とエンチャントの表を作る
  （--target 772 の実行結果が要る）。1.21.9 と 1.21.10 はプロトコル
  773 を共有し、minecraft-data に pc/1.21.10 が無いため 773 のデータ
  はすべて 1.21.9 のもの（vv-1.21.9.json／1.21.9_blocks.json／
  mc-1.21.9-protocol.json）を使う。

生成物：maps.json（全対応表）と tables（Java の int[][] テーブル＋
SOUND_NAME_FALLBACKS）。maps.json は --data-dir に保存される。
"""

import argparse
import json
import re
import sys
import urllib.request
from collections import Counter
from pathlib import Path

VV_BASE = "https://raw.githubusercontent.com/ViaVersion/Mappings/main/mappings"
MC_BASE = "https://raw.githubusercontent.com/PrismarineJS/minecraft-data/master/data/pc"

FILES = {
    "vv-1.20.json": f"{VV_BASE}/mapping-1.20.json",
    "vv-1.20.2.json": f"{VV_BASE}/mapping-1.20.2.json",
    "vv-1.20.3.json": f"{VV_BASE}/mapping-1.20.3.json",
    "vv-1.20.5.json": f"{VV_BASE}/mapping-1.20.5.json",
    "vv-1.21.json": f"{VV_BASE}/mapping-1.21.json",
    "vv-1.21.2.json": f"{VV_BASE}/mapping-1.21.2.json",
    "1.20_blocks.json": f"{MC_BASE}/1.20/blocks.json",
    "1.20_items.json": f"{MC_BASE}/1.20/items.json",
    "1.20.2_blocks.json": f"{MC_BASE}/1.20.2/blocks.json",
    "1.20.2_items.json": f"{MC_BASE}/1.20.2/items.json",
    "1.20.5_blocks.json": f"{MC_BASE}/1.20.5/blocks.json",
    "1.21.1_blocks.json": f"{MC_BASE}/1.21.1/blocks.json",
    "1.21.3_blocks.json": f"{MC_BASE}/1.21.3/blocks.json",
    "enchantments-1.20.5.json": f"{MC_BASE}/1.20.5/enchantments.json",
    "enchantments-1.21.1.json": f"{MC_BASE}/1.21.1/enchantments.json",
    "mc-1.21.1-protocol.json": f"{MC_BASE}/1.21.1/protocol.json",
    "mc-1.21.3-protocol.json": f"{MC_BASE}/1.21.3/protocol.json",
    "vv-1.21.4.json": f"{VV_BASE}/mapping-1.21.4.json",
    "1.21.4_blocks.json": f"{MC_BASE}/1.21.4/blocks.json",
    "1.21.4_items.json": f"{MC_BASE}/1.21.4/items.json",
    "mc-1.21.4-protocol.json": f"{MC_BASE}/1.21.4/protocol.json",
    "vv-1.21.5.json": f"{VV_BASE}/mapping-1.21.5.json",
    "1.21.5_blocks.json": f"{MC_BASE}/1.21.5/blocks.json",
    "1.21.5_items.json": f"{MC_BASE}/1.21.5/items.json",
    "mc-1.21.5-protocol.json": f"{MC_BASE}/1.21.5/protocol.json",
    "vv-1.21.6.json": f"{VV_BASE}/mapping-1.21.6.json",
    "1.21.6_blocks.json": f"{MC_BASE}/1.21.6/blocks.json",
    "1.21.6_items.json": f"{MC_BASE}/1.21.6/items.json",
    "mc-1.21.6-protocol.json": f"{MC_BASE}/1.21.6/protocol.json",
    "enchantments-1.21.5.json": f"{MC_BASE}/1.21.5/enchantments.json",
    "enchantments-1.21.6.json": f"{MC_BASE}/1.21.6/enchantments.json",
    "vv-1.21.7.json": f"{VV_BASE}/mapping-1.21.7.json",
    "1.21.8_blocks.json": f"{MC_BASE}/1.21.8/blocks.json",
    "1.21.8_items.json": f"{MC_BASE}/1.21.8/items.json",
    "mc-1.21.8-protocol.json": f"{MC_BASE}/1.21.8/protocol.json",
    "enchantments-1.21.8.json": f"{MC_BASE}/1.21.8/enchantments.json",
    # --target 767 は mc-1.20.5-protocol.json（766 の属性表用）を読むが、
    # 2026-11-12 まで FILES に無く、--target 767 がファイル不足で失敗
    # していた。773 の作業で追加する（packet-ids-*.py が読む
    # mc-1.20／1.20.2／1.20.3-protocol.json も同じ流儀で追加し、
    # generate-remap-tables.py の再実行で揃うようにする）。
    "mc-1.20-protocol.json": f"{MC_BASE}/1.20/protocol.json",
    "mc-1.20.2-protocol.json": f"{MC_BASE}/1.20.2/protocol.json",
    "mc-1.20.3-protocol.json": f"{MC_BASE}/1.20.3/protocol.json",
    "mc-1.20.5-protocol.json": f"{MC_BASE}/1.20.5/protocol.json",
    "vv-1.21.9.json": f"{VV_BASE}/mapping-1.21.9.json",
    "1.21.9_blocks.json": f"{MC_BASE}/1.21.9/blocks.json",
    "1.21.9_items.json": f"{MC_BASE}/1.21.9/items.json",
    "mc-1.21.9-protocol.json": f"{MC_BASE}/1.21.9/protocol.json",
    "enchantments-1.21.9.json": f"{MC_BASE}/1.21.9/enchantments.json",
    # --target 774（1.21.11）はすべて 1.21.11 のものを使う
    # （minecraft-data に pc/1.21.11 があり、dataPaths.json で確認済み）。
    "vv-1.21.11.json": f"{VV_BASE}/mapping-1.21.11.json",
    "1.21.11_blocks.json": f"{MC_BASE}/1.21.11/blocks.json",
    "1.21.11_items.json": f"{MC_BASE}/1.21.11/items.json",
    "mc-1.21.11-protocol.json": f"{MC_BASE}/1.21.11/protocol.json",
    "enchantments-1.21.11.json": f"{MC_BASE}/1.21.11/enchantments.json",
    # --target 775（26.1）はすべて 26.1 のものを使う。
    "vv-26.1.json": f"{VV_BASE}/mapping-26.1.json",
    "26.1_blocks.json": f"{MC_BASE}/26.1/blocks.json",
    "26.1_items.json": f"{MC_BASE}/26.1/items.json",
    "mc-26.1-protocol.json": f"{MC_BASE}/26.1/protocol.json",
    "enchantments-26.1.json": f"{MC_BASE}/26.1/enchantments.json",
}


def download(url, dest):
    print(f"取得中: {url}")
    req = urllib.request.Request(url, headers={"User-Agent": "DetergentMC-remap-generator"})
    with urllib.request.urlopen(req, timeout=60) as resp, open(dest, "wb") as out:
        out.write(resp.read())


def load(path):
    with open(path, encoding="utf-8") as f:
        return json.load(f)


def block_name(state):
    return state.split("[")[0]


def block_props(state):
    m = re.match(r"^[^\[]+\[(.*)\]$", state)
    if not m:
        return {}
    props = {}
    for kv in m.group(1).split(","):
        k, v = kv.split("=", 1)
        props[k] = v
    return props


# ----------------------------------------------------------------------
# 代替表（1.20.2／1.20.3 の新ブロック -> 763の近いブロック）
# ----------------------------------------------------------------------
def same_props(p):
    return dict(p)


def grate_props(p):
    # copper_grate は鉄格子に。向きは鉄格子に無いので落とし、waterlogged だけ残す。
    return {"east": "false", "north": "false", "south": "false",
            "waterlogged": p.get("waterlogged", "false"), "west": "false"}


def creaking_heart_props(p):
    # 768 の creaking_heart[axis, creaking=disabled／dormant／active] →
    # 769 の creaking_heart[axis, active, natural]。1.21.4 で
    # creaking プロパティが active＋natural の2ブールに変わった。
    # active／dormant は自然生成の心臓（夜にクリーキングを呼ぶもの）
    # として natural=true、disabled は呼ばないものとして
    # natural=false にする（ベストエフォートの写像。768 には natural
    # の区別が無いため、プレイヤーが置いた心臓と自然生成の心臓の
    # 区別は 768 側では分からない）。
    creaking = p.get("creaking", "disabled")
    return {"axis": p.get("axis", "y"),
            "active": "true" if creaking == "active" else "false",
            "natural": "false" if creaking == "disabled" else "true"}


BLOCK_FALLBACK = {
    "tuff_slab": ("stone_slab", same_props),
    "tuff_stairs": ("stone_stairs", same_props),
    "tuff_wall": ("stone_brick_wall", same_props),
    "polished_tuff_slab": ("stone_slab", same_props),
    "polished_tuff_stairs": ("stone_stairs", same_props),
    "polished_tuff_wall": ("stone_brick_wall", same_props),
    "tuff_brick_slab": ("stone_brick_slab", same_props),
    "tuff_brick_stairs": ("stone_brick_stairs", same_props),
    "tuff_brick_wall": ("stone_brick_wall", same_props),
    "polished_tuff": ("tuff", None),
    "chiseled_tuff": ("chiseled_stone_bricks", None),
    "tuff_bricks": ("stone_bricks", None),
    "chiseled_tuff_bricks": ("chiseled_stone_bricks", None),
    "chiseled_copper": ("copper_block", None),
    "exposed_chiseled_copper": ("copper_block", None),
    "weathered_chiseled_copper": ("copper_block", None),
    "oxidized_chiseled_copper": ("copper_block", None),
    "waxed_chiseled_copper": ("copper_block", None),
    "waxed_exposed_chiseled_copper": ("copper_block", None),
    "waxed_weathered_chiseled_copper": ("copper_block", None),
    "waxed_oxidized_chiseled_copper": ("copper_block", None),
    "copper_door": ("iron_door", same_props),
    "exposed_copper_door": ("iron_door", same_props),
    "weathered_copper_door": ("iron_door", same_props),
    "oxidized_copper_door": ("iron_door", same_props),
    "waxed_copper_door": ("iron_door", same_props),
    "waxed_exposed_copper_door": ("iron_door", same_props),
    "waxed_weathered_copper_door": ("iron_door", same_props),
    "waxed_oxidized_copper_door": ("iron_door", same_props),
    "copper_trapdoor": ("iron_trapdoor", same_props),
    "exposed_copper_trapdoor": ("iron_trapdoor", same_props),
    "weathered_copper_trapdoor": ("iron_trapdoor", same_props),
    "oxidized_copper_trapdoor": ("iron_trapdoor", same_props),
    "waxed_copper_trapdoor": ("iron_trapdoor", same_props),
    "waxed_exposed_copper_trapdoor": ("iron_trapdoor", same_props),
    "waxed_weathered_copper_trapdoor": ("iron_trapdoor", same_props),
    "waxed_oxidized_copper_trapdoor": ("iron_trapdoor", same_props),
    "copper_grate": ("iron_bars", grate_props),
    "exposed_copper_grate": ("iron_bars", grate_props),
    "weathered_copper_grate": ("iron_bars", grate_props),
    "oxidized_copper_grate": ("iron_bars", grate_props),
    "waxed_copper_grate": ("iron_bars", grate_props),
    "waxed_exposed_copper_grate": ("iron_bars", grate_props),
    "waxed_weathered_copper_grate": ("iron_bars", grate_props),
    "waxed_oxidized_copper_grate": ("iron_bars", grate_props),
    "copper_bulb": ("glowstone", None),
    "exposed_copper_bulb": ("glowstone", None),
    "weathered_copper_bulb": ("glowstone", None),
    "oxidized_copper_bulb": ("glowstone", None),
    "waxed_copper_bulb": ("glowstone", None),
    "waxed_exposed_copper_bulb": ("glowstone", None),
    "waxed_weathered_copper_bulb": ("glowstone", None),
    "waxed_oxidized_copper_bulb": ("glowstone", None),
    "crafter": ("crafting_table", None),
    "trial_spawner": ("spawner", None),
    "creaking_heart": ("creaking_heart", creaking_heart_props),
}

RENAME = {"short_grass": "grass"}

ITEM_FALLBACK = {
    "tuff_slab": "tuff", "tuff_stairs": "tuff", "tuff_wall": "tuff",
    "chiseled_tuff": "tuff", "polished_tuff": "tuff",
    "polished_tuff_slab": "tuff", "polished_tuff_stairs": "tuff",
    "polished_tuff_wall": "tuff",
    "tuff_bricks": "stone_bricks", "tuff_brick_slab": "stone_bricks",
    "tuff_brick_stairs": "stone_bricks", "tuff_brick_wall": "stone_bricks",
    "chiseled_tuff_bricks": "chiseled_stone_bricks",
    "chiseled_copper": "copper_block", "exposed_chiseled_copper": "copper_block",
    "weathered_chiseled_copper": "copper_block", "oxidized_chiseled_copper": "copper_block",
    "waxed_chiseled_copper": "copper_block", "waxed_exposed_chiseled_copper": "copper_block",
    "waxed_weathered_chiseled_copper": "copper_block",
    "waxed_oxidized_chiseled_copper": "copper_block",
    "copper_door": "iron_door", "exposed_copper_door": "iron_door",
    "weathered_copper_door": "iron_door", "oxidized_copper_door": "iron_door",
    "waxed_copper_door": "iron_door", "waxed_exposed_copper_door": "iron_door",
    "waxed_weathered_copper_door": "iron_door", "waxed_oxidized_copper_door": "iron_door",
    "copper_trapdoor": "iron_trapdoor", "exposed_copper_trapdoor": "iron_trapdoor",
    "weathered_copper_trapdoor": "iron_trapdoor", "oxidized_copper_trapdoor": "iron_trapdoor",
    "waxed_copper_trapdoor": "iron_trapdoor",
    "waxed_exposed_copper_trapdoor": "iron_trapdoor",
    "waxed_weathered_copper_trapdoor": "iron_trapdoor",
    "waxed_oxidized_copper_trapdoor": "iron_trapdoor",
    "copper_grate": "iron_bars", "exposed_copper_grate": "iron_bars",
    "weathered_copper_grate": "iron_bars", "oxidized_copper_grate": "iron_bars",
    "waxed_copper_grate": "iron_bars", "waxed_exposed_copper_grate": "iron_bars",
    "waxed_weathered_copper_grate": "iron_bars", "waxed_oxidized_copper_grate": "iron_bars",
    "copper_bulb": "glowstone", "exposed_copper_bulb": "glowstone",
    "weathered_copper_bulb": "glowstone", "oxidized_copper_bulb": "glowstone",
    "waxed_copper_bulb": "glowstone", "waxed_exposed_copper_bulb": "glowstone",
    "waxed_weathered_copper_bulb": "glowstone", "waxed_oxidized_copper_bulb": "glowstone",
    "crafter": "crafting_table", "trial_spawner": "spawner",
    "trial_key": "tripwire_hook", "breeze_spawn_egg": "blaze_spawn_egg",
}

SOUND_FALLBACK = {
    "entity.breeze.inhale": "entity.blaze.ambient",
    "entity.breeze.idle_ground": "entity.blaze.ambient",
    "entity.breeze.idle_air": "entity.blaze.ambient",
    "entity.breeze.shoot": "entity.blaze.shoot",
    "entity.breeze.jump": "entity.blaze.ambient",
    "entity.breeze.land": "entity.blaze.ambient",
    "entity.breeze.slide": "entity.blaze.ambient",
    "entity.breeze.death": "entity.blaze.death",
    "entity.breeze.hurt": "entity.blaze.hurt",
    "block.copper_bulb.break": "block.glass.break",
    "block.copper_bulb.step": "block.glass.step",
    "block.copper_bulb.place": "block.glass.place",
    "block.copper_bulb.hit": "block.glass.hit",
    "block.copper_bulb.fall": "block.glass.fall",
    "block.copper_bulb.turn_on": "block.lever.click",
    "block.copper_bulb.turn_off": "block.lever.click",
    "block.copper_door.close": "block.iron_door.close",
    "block.copper_door.open": "block.iron_door.open",
    "block.copper_grate.break": "block.metal.break",
    "block.copper_grate.step": "block.metal.step",
    "block.copper_grate.place": "block.metal.place",
    "block.copper_grate.hit": "block.metal.hit",
    "block.copper_grate.fall": "block.metal.fall",
    "block.copper_trapdoor.close": "block.iron_trapdoor.close",
    "block.copper_trapdoor.open": "block.iron_trapdoor.open",
    "block.crafter.craft": "block.wooden_button.click_on",
    "block.crafter.fail": "block.wooden_button.click_off",
    "block.decorated_pot.insert": "block.glass.place",
    "block.decorated_pot.insert_fail": "block.glass.break",
    "block.trial_spawner.break": "block.stone.break",
    "block.trial_spawner.step": "block.stone.step",
    "block.trial_spawner.place": "block.stone.place",
    "block.trial_spawner.hit": "block.stone.hit",
    "block.trial_spawner.fall": "block.stone.fall",
    "block.trial_spawner.spawn_mob": "block.beacon.activate",
    "block.trial_spawner.detect_player": "block.note_block.pling",
    "block.trial_spawner.ambient": "block.bubble_column.upwards_ambient",
    "block.trial_spawner.open_shutter": "block.iron_door.open",
    "block.trial_spawner.close_shutter": "block.iron_door.close",
    "block.trial_spawner.eject_item": "block.dispenser.dispense",
    "entity.parrot.imitate.breeze": "entity.parrot.imitate.blaze",
    "entity.player.teleport": "entity.enderman.teleport",
    "block.sponge.break": "block.wool.break",
    "block.sponge.fall": "block.wool.fall",
    "block.sponge.hit": "block.wool.hit",
    "block.sponge.place": "block.wool.place",
    "block.sponge.step": "block.wool.step",
    "block.sponge.absorb": "block.water.ambient",
    "block.tuff_bricks.break": "block.stone.break",
    "block.tuff_bricks.fall": "block.stone.fall",
    "block.tuff_bricks.hit": "block.stone.hit",
    "block.tuff_bricks.place": "block.stone.place",
    "block.tuff_bricks.step": "block.stone.step",
    "block.polished_tuff.break": "block.stone.break",
    "block.polished_tuff.fall": "block.stone.fall",
    "block.polished_tuff.hit": "block.stone.hit",
    "block.polished_tuff.place": "block.stone.place",
    "block.polished_tuff.step": "block.stone.step",
    "block.hanging_sign.waxed_interact_fail": "block.wooden_button.click_off",
    "block.wet_sponge.break": "block.wool.break",
    "block.wet_sponge.fall": "block.wool.fall",
    "block.wet_sponge.hit": "block.wool.hit",
    "block.wet_sponge.place": "block.wool.place",
    "block.wet_sponge.step": "block.wool.step",
    "entity.generic.wind_burst": "entity.generic.explode",
}

PARTICLE_FALLBACK = {
    "gust": "cloud", "gust_emitter": "cloud", "white_smoke": "smoke",
    "dust_plume": "dust", "gust_dust": "dust",
    "trial_spawner_detection": "sculk_soul",
}

# ----------------------------------------------------------------------
# 代替表（765→766。766 に無い 765 側のものだけ使う）
# ----------------------------------------------------------------------
# 765 で削除されたもの（ViaVersion mapping-1.20.3.json と
# mapping-1.20.5.json の差分で確認）：
#   アイテム：scute → turtle_scute（改名）
#   音：entity.generic.wind_burst → entity.wind_charge.wind_burst
#     （1.20.5 で wind_charge 系の音に分割された。generic は
#     wind_charge の音だったため、wind_charge 側を選ぶ）
#   パーティクル：ambient_entity_effect → entity_effect（削除され、
#     色付きの entity_effect に統合された）、gust_emitter →
#     gust_emitter_large（24w06a で gust_emitter が small／large に
#     分割された。765 の wind_charge はブリーズのものが主で、
#     ブリーズの風の玉は large を使う）、gust_dust → small_gust
#     （風の玉の軌跡の小さな渦。1.20.5 の small_gust が相当する）
# ブロック状態：trial_spawner に ominous が追加されたため、
# 765 の trial_spawner[ominous無し] は 766 の ominous=false の状態へ
# （プロパティを1個足して突き合わせる）。
RENAME_766 = {"scute": "turtle_scute"}
SOUND_FALLBACK_766 = {"entity.generic.wind_burst": "entity.wind_charge.wind_burst"}
PARTICLE_FALLBACK_766 = {
    "ambient_entity_effect": "entity_effect",
    "gust_emitter": "gust_emitter_large",
    "gust_dust": "small_gust",
}
ADD_PROPS_766 = (("ominous", "false"),)

# 766→765 の反転で、区間の代替が本物の対応を奪う・像に無い番号の
# 補正（RegistryRemapper.java の SOUND_UP_766_CORRECTIONS／
# BLOCK_STATE_UP_766_CORRECTIONS／PARTICLE_UP_766_CORRECTIONS と
# 同じ値。根拠は RegistryRemapper.java のコメント参照）。
# 音：766 で分割された wind_charge／breeze の音のうち、765 の
# entity.generic.wind_burst が元だったもの。breeze.charge は
# 765 の breeze.inhale（攻撃前の溜めの音）が最も近い。
SOUND_UP_766_CORRECTIONS = [(1527, 1466), (198, 1466), (186, 166)]
# ブロック状態：766 の ominous=true の trial_spawner（26638〜26643）は
# 765 の ominous 無しの trial_spawner（26638〜26643）へ。vault
# （26650〜26681）は 765 の trial_spawner の既定状態（26638）へ。
BLOCK_STATE_UP_766_CORRECTIONS = (
    [(s766, s765) for s766, s765 in
     zip(range(26638, 26644), range(26638, 26644))]
    + [(s, 26638) for s in range(26650, 26682)]
)
# パーティクル：gust_emitter_small（26）→ gust_emitter（25）、
# trial_spawner_detection_ominous（103）→ trial_spawner_detection（100）。
PARTICLE_UP_766_CORRECTIONS = [(26, 25), (103, 100)]


# ----------------------------------------------------------------------
# 対応表の計算
# ----------------------------------------------------------------------
def build_blockstates(a, b, blocks_120, rename=None, add_props=(),
                      block_fallbacks=None):
    rename = rename or RENAME
    block_fallbacks = block_fallbacks if block_fallbacks is not None else BLOCK_FALLBACK
    idx763 = {n: i for i, n in enumerate(a["blockstates"])}
    by_props_763 = {}
    for i, n in enumerate(a["blockstates"]):
        by_props_763[(block_name(n), frozenset(block_props(n).items()))] = i
    defaults763 = {blk["name"]: blk["defaultState"] for blk in blocks_120}

    def state763(block, props):
        return by_props_763.get((block, frozenset(props.items())),
                                defaults763.get(block, 1))

    mapping = {}
    unmapped = []
    for i, n in enumerate(b["blockstates"]):
        if n in idx763:
            mapping[i] = idx763[n]
            continue
        name = block_name(n)
        props = block_props(n)
        if name in rename:
            # 改名はブロック名だけ置き換え、プロパティはそのままにする
            # （chain[axis=x,waterlogged=false] → iron_chain[...]）。
            # 以前は改名後の裸の名前（"iron_chain"）をそのまま引いており、
            # プロパティ付きのブロックの改名では KeyError になっていた。
            # 772→773 の chain→iron_chain が最初の該当例。
            renamed = rename[name] + n[len(name):]
            if renamed in idx763:
                mapping[i] = idx763[renamed]
                continue
        found = None
        for drop in list(props.keys()):
            rest = {k: v for k, v in props.items() if k != drop}
            if (name, frozenset(rest.items())) in by_props_763:
                found = by_props_763[(name, frozenset(rest.items()))]
                break
        if found is not None:
            mapping[i] = found
            continue
        # 766向け：プロパティを1個足すと一致する（trial_spawner の ominous）。
        for key, value in add_props:
            if key not in props:
                extended = dict(props)
                extended[key] = value
                if (name, frozenset(extended.items())) in by_props_763:
                    found = by_props_763[(name, frozenset(extended.items()))]
                    break
        if found is not None:
            mapping[i] = found
            continue
        if name in block_fallbacks:
            target, fn = block_fallbacks[name]
            mapping[i] = state763(target, fn(props)) if fn else defaults763.get(target, 1)
            continue
        unmapped.append((i, n))
    return mapping, unmapped


def build_name_map(key, a, b, fallbacks, rename=None):
    index763 = {n: i for i, n in enumerate(a[key])}
    mapping = {}
    fallback_used = Counter()
    unmapped = []
    for i, n in enumerate(b[key]):
        if n in index763:
            mapping[i] = index763[n]
        elif rename and n in rename:
            mapping[i] = index763[rename[n]]
        elif n in fallbacks:
            target = fallbacks[n]
            if target in index763:
                mapping[i] = index763[target]
                fallback_used[n] = target
            else:
                mapping[i] = index763.get("block.stone.break", 0)
                fallback_used[n] = target + " (MISSING -> generic)"
                unmapped.append((i, n, "fallback-missing:" + target))
        else:
            unmapped.append((i, n, "no-fallback"))
    return mapping, fallback_used, unmapped


def build_reverse_items(a, b, rename=None, fallback=None):
    """target 側（a）のアイテム → source 側（b）のアイテムの逆変換表。

    rename は改名（target 側の名前 → source 側の名前）。fallback が
    None でない場合、source 側に無い target 側のアイテム（766 の
    mace などの新規アイテム）は fallback（石=1）へ落とし、一覧を
    出す。fallback が None の場合は未対応として返す（763／764用。
    763／764 のアイテムはすべて 765 にあるはずのため）。
    """
    rename = rename or {}
    index765 = {n: i for i, n in enumerate(b["items"])}
    reverse = {}
    unmapped = []
    fell_back = []
    for i763, n in enumerate(a["items"]):
        if n in index765:
            reverse[i763] = index765[n]
        elif n in rename:
            reverse[i763] = index765[rename[n]]
        elif n == "grass":
            reverse[i763] = index765["short_grass"]
        elif fallback is not None:
            reverse[i763] = fallback
            fell_back.append(n)
        else:
            unmapped.append(n)
    return reverse, unmapped, fell_back


def compress(mapping, size):
    """{765 id: 763 id} を区間（等差）と上書きに分ける。"""
    intervals = []
    overrides = []
    i = 0
    while i < size:
        d = mapping[i] - i
        j = i
        while j + 1 < size and mapping[j + 1] - (j + 1) == d:
            j += 1
        if j - i + 1 >= 2:
            intervals.append((i, j, d))
        else:
            overrides.append((i, mapping[i]))
        i = j + 1
    return intervals, overrides


def java_int3(name, triples):
    lines = [f"    private static final int[][] {name} = {{"]
    for chunk in [triples[i:i + 6] for i in range(0, len(triples), 6)]:
        lines.append("            " + ", ".join("{%d, %d, %d}" % t for t in chunk) + ",")
    lines.append("    };")
    return "\n".join(lines)


def java_int2(name, pairs):
    lines = [f"    private static final int[][] {name} = {{"]
    for chunk in [pairs[i:i + 8] for i in range(0, len(pairs), 8)]:
        lines.append("            " + ", ".join("{%d, %d}" % p for p in chunk) + ",")
    lines.append("    };")
    return "\n".join(lines)


def build_pair(tag, a, b, blocks_target, data_dir,
               rename=None, sound_fallbacks=None, particle_fallbacks=None,
               reverse_fallback=None, add_props=(), entity_fallbacks=None,
               block_fallbacks=None):
    """source（b）→ target（a）の対応表を1組作る。

    tag は "763" / "764" / "766"。a は target 側の ViaVersion mapping、
    b は source 側の mapping、blocks_target は target 側の
    minecraft-data blocks.json（代替ブロックの既定状態を引くため）。
    763／764 の組では b は 765、766 の組では b は 765 で a は 766。
    戻り値は (maps dict, Java の表の行リスト, failures リスト, 逆変換表)。
    エフェクトは 763だけ +1 シフト、764は 765と同じ0ベースなので
    表は作らない（RegistryRemapper.mapEffect764 は恒等写像）。
    766 も 765と同じ0ベース（0〜32 は同一、33〜38 は新規）なので
    表は作らない。
    rename／sound_fallbacks／particle_fallbacks／reverse_fallback／
    add_props／entity_fallbacks は 766／767／768 の組だけ別の値を
    使う（既定は 763／764 用）。
    """
    rename = rename if rename is not None else RENAME
    sound_fallbacks = sound_fallbacks if sound_fallbacks is not None else SOUND_FALLBACK
    particle_fallbacks = (particle_fallbacks if particle_fallbacks is not None
                          else PARTICLE_FALLBACK)
    entity_fallbacks = (entity_fallbacks if entity_fallbacks is not None
                        else {"breeze": "blaze", "wind_charge": "small_fireball"})
    block_fallbacks = block_fallbacks if block_fallbacks is not None else BLOCK_FALLBACK
    failures = []
    out = []

    m_bs, unm_bs = build_blockstates(a, b, blocks_target, rename=rename,
                                    add_props=add_props,
                                    block_fallbacks=block_fallbacks)
    print(f"[{tag}] blockstates: total {len(b['blockstates'])} mapped {len(m_bs)} "
          f"unmapped {len(unm_bs)} {unm_bs[:10]}")
    failures += [("blockstates", u) for u in unm_bs]
    fb = Counter()
    states763 = set(a["blockstates"])
    for i, n in enumerate(b["blockstates"]):
        if n not in states763 and block_name(n) in block_fallbacks:
            fb[block_name(n)] += 1
    print(f"  [{tag}] fallback states by block:", dict(fb))

    specs = []
    m_items, fb_items, unm_items = build_name_map(
        "items", a, b, ITEM_FALLBACK, rename=rename)
    specs.append(("ITEM", m_items, len(b["items"])))
    print(f"[{tag}] items: total {len(b['items'])} unmapped {len(unm_items)} {unm_items[:5]}")
    print(f"  [{tag}] fallbacks:", dict(fb_items))
    failures += [("items", u) for u in unm_items]

    m_ent, fb_ent, unm_ent = build_name_map(
        "entities", a, b, entity_fallbacks)
    specs.append(("ENTITY_TYPE", m_ent, len(b["entities"])))
    print(f"[{tag}] entities: total {len(b['entities'])} unmapped {len(unm_ent)} {unm_ent[:5]}")
    failures += [("entities", u) for u in unm_ent]

    missing_targets = [(k, v) for k, v in sound_fallbacks.items() if v not in set(a["sounds"])]
    if missing_targets:
        print(f"[{tag}] sound fallback targets missing in target:", missing_targets)
        failures += [("sounds-fallback-target", t) for t in missing_targets]
    m_sounds, fb_sounds, unm_sounds = build_name_map("sounds", a, b, sound_fallbacks)
    specs.append(("SOUND", m_sounds, len(b["sounds"])))
    print(f"[{tag}] sounds: total {len(b['sounds'])} unmapped {len(unm_sounds)} {unm_sounds[:5]}")
    print(f"  [{tag}] fallbacks:", dict(fb_sounds))
    failures += [("sounds", u) for u in unm_sounds]

    m_part, fb_part, unm_part = build_name_map("particles", a, b, particle_fallbacks)
    specs.append(("PARTICLE", m_part, len(b["particles"])))
    print(f"[{tag}] particles: total {len(b['particles'])} unmapped {len(unm_part)} {unm_part[:5]}")
    failures += [("particles", u) for u in unm_part]

    blocks_fb = {n: ITEM_FALLBACK[n] for n in b["blocks"] if n in ITEM_FALLBACK}
    blocks_fb["trial_spawner"] = "spawner"
    m_blocks, _, unm_blocks = build_name_map("blocks", a, b, blocks_fb, rename=rename)
    specs.append(("BLOCK", m_blocks, len(b["blocks"])))
    print(f"[{tag}] blocks: total {len(b['blocks'])} unmapped {len(unm_blocks)} {unm_blocks[:5]}")
    failures += [("blocks", u) for u in unm_blocks]

    m_menus, _, unm_menus = build_name_map("menus", a, b, {"crafter_3x3": "crafting"})
    specs.append(("MENU", m_menus, len(b["menus"])))
    print(f"[{tag}] menus: total {len(b['menus'])} unmapped {len(unm_menus)} {unm_menus[:5]}")
    failures += [("menus", u) for u in unm_menus]

    # 逆変換表の改名は source→target の向きに直す（766組：turtle_scute→scute）。
    rev_rename = {v: k for k, v in rename.items()}
    m_rev, unm_rev, fell_back = build_reverse_items(
        a, b, rename=rev_rename, fallback=reverse_fallback)
    print(f"[{tag}] reverse items: {len(m_rev)} of {len(a['items'])} "
          f"unmapped {unm_rev[:5]} fell back to stone: {fell_back}")
    failures += [("items-reverse", n) for n in unm_rev]

    # blockentities: 数値の表は作らない。source にあって target に無い
    # 種別（765 の crafter=41、trial_spawner=42 は 763／764 に無い）と
    # target にあって source に無い種別（766 の vault=43）は DROP（-1）。
    new_be = [(i, n) for i, n in enumerate(b["blockentities"])
              if i >= len(a["blockentities"])]
    print(f"[{tag}] blockentities source new:", new_be)
    new_be_target = [(i, n) for i, n in enumerate(a["blockentities"])
                     if i >= len(b["blockentities"])]
    print(f"[{tag}] blockentities target new:", new_be_target)

    suffix = "" if tag == "763" else "_" + tag
    for name, mapping, size in [("BLOCK_STATE", m_bs, len(b["blockstates"]))] + specs:
        iv, ov = compress(mapping, size)
        out.append(java_int3(name + suffix + "_RANGES", iv))
        out.append(java_int2(name + suffix + "_OVERRIDES", ov))
        out.append(f"    // {name}{suffix}: 765 entries={size} ranges={len(iv)} overrides={len(ov)}")
    iv, ov = compress(m_rev, len(a["items"]))
    out.append(java_int3("ITEM_REVERSE" + suffix + "_RANGES", iv))
    out.append(java_int2("ITEM_REVERSE" + suffix + "_OVERRIDES", ov))
    out.append(f"    // ITEM_REVERSE{suffix}: {tag} entries={len(a['items'])} "
               f"ranges={len(iv)} overrides={len(ov)}")

    maps = {
        "blockstates": m_bs, "items": m_items, "entities": m_ent,
        "sounds": m_sounds, "particles": m_part, "blocks": m_blocks,
        "menus": m_menus, "sound_fallback_names": sound_fallbacks,
        "reverse_items": m_rev,
    }
    return maps, out, failures, m_rev


def invert_java(ranges, overrides, from_size, to_size, fallback, corrections=None):
    """RegistryRemapper.invert と同じ決め方（区間→上書き→補正）で
    反転表を作る。766→763／766→764 の音の名前の表を作るために使う。"""
    inverse = [-1] * to_size
    for start, end, delta in ranges:
        for x in range(start, end + 1):
            y = x + delta
            if 0 <= y < to_size and inverse[y] < 0:
                inverse[y] = x
    for x, y in overrides:
        if 0 <= y < to_size and inverse[y] < 0:
            inverse[y] = x
    if corrections:
        for y, x in corrections:
            inverse[y] = x
    return [v if v >= 0 else fallback for v in inverse]


def _relaxed_match(src, dst, relaxed_props):
    """src（765側の状態名）が dst（763／764側の状態名）と「落とした
    プロパティを除いて」一致するかどうか。src が dst のプロパティに加えて
    持つプロパティは、relaxed_props にある既定値のものだけ許す。
    例：765 の skeleton_skull[powered=false,rotation=0] は 763 の
    skeleton_skull[rotation=0] と一致するが、powered=true の方は一致
    しない（既定値 false ではないため）。"""
    if block_name(src) != block_name(dst):
        return False
    sp = block_props(src)
    dp = block_props(dst)
    if set(dp) - set(sp):
        return False
    for k, v in sp.items():
        if k in dp:
            if dp[k] != v:
                return False
        elif k in relaxed_props and v == relaxed_props[k]:
            continue
        else:
            return False
    return True


def build_inverse(forward, from_size, to_size, names_from=None, names_to=None,
                  relaxed_props=None):
    """forward（source id → target id）の逆引き表（target id → source id）。
    複数の source id が同じ target id に写る場合（新しいブロックの
    代替など）は、名前が同じもの（例：765 の iron_door.close が
    763 の iron_door.close に写る）を優先する。名前が同じものが
    無ければ、落としたプロパティが既定値のもの（例：765 の
    skeleton_skull[powered=false,rotation=0] が 763 の
    skeleton_skull[rotation=0] に写る。powered=true の方は既定値では
    ないため選ばない）を優先する。それも無ければ初出（最小の
    source id）を採用する。名前で優先しないと、765 の
    copper_door.close（763 の iron_door.close への代替）が
    765 の iron_door.close（本物）より先に拾われてしまう。
    初出だけだと 763 の頭蓋骨が 765／766 の powered=true 側に繋がって
    しまう（minecraft-data の defaultState は powered=false 側）。
    ClientAdapter.buildInverse は名前を見ないため 763↔764 の合成表
    にはこの既知の不一致があるが、こちらは名前で優先して避ける。
    対応する source id が無い target id は -1。
    """
    inverse = [-1] * to_size
    if names_from is not None and names_to is not None:
        for x in range(from_size):
            y = forward.get(x)
            if (y is not None and 0 <= y < to_size and inverse[y] < 0
                    and names_from[x] == names_to[y]):
                inverse[y] = x
    if relaxed_props is not None and names_from is not None and names_to is not None:
        for x in range(from_size):
            y = forward.get(x)
            if (y is not None and 0 <= y < to_size and inverse[y] < 0
                    and _relaxed_match(names_from[x], names_to[y], relaxed_props)):
                inverse[y] = x
    for x in range(from_size):
        y = forward.get(x)
        if y is not None and 0 <= y < to_size and inverse[y] < 0:
            inverse[y] = x
    return inverse


def compose_maps(first, second, fallback):
    """first（A→B）と second（B→C）を合成して A→C の表を作る。
    first[i] が -1（対応なし）の場合は fallback（C 側の代替先）。
    実行時は合成済みの1つの表を直接引くため、ここで1つの表にする。
    """
    out = {}
    for i, b in first.items():
        if b is None or b < 0:
            out[i] = fallback
        else:
            out[i] = second.get(b, fallback)
    return out


def compose_list(first, second, fallback):
    """compose_maps の list 版（first／second は list、-1 は対応なし）。"""
    out = []
    for b in first:
        if b is None or b < 0:
            out.append(fallback)
        else:
            out.append(second[b] if 0 <= b < len(second) else fallback)
    return out


# 766→765 の反転で使う代替先（765側）と補正（RegistryRemapper.java と同じ値）。
UP_766_FALLBACKS = {
    "blockstates": 1, "items": 1, "entities": 73, "sounds": 1312,
    "particles": 0, "blocks": 1, "menus": 12,
}
UP_766_CORRECTIONS = {
    "blockstates": BLOCK_STATE_UP_766_CORRECTIONS,
    "sounds": SOUND_UP_766_CORRECTIONS,
    "particles": PARTICLE_UP_766_CORRECTIONS,
}


# ----------------------------------------------------------------------
# 代替表（766→767。767 に無い 766 側のものだけ使う）
# ----------------------------------------------------------------------
# ViaVersion mapping-1.21.json と mapping-1.20.5.json の差分で確認：
#   ブロック状態：同一（26,684件）。ブロック：同一（1,060件）。
#   エンティティ：同一（130件）。メニュー：同一（25件）。
#   ブロックエンティティ：同一（44件）。パーティクル：同一（109件）。
#   アイテム：新3件（music_disc_creator／music_disc_creator_music_box／
#     music_disc_precipice）。改名なし。
#   音：改名2件（block.trial_spawner.charge_activate →
#     block.trial_spawner.ominous_activate、block.trial_spawner.
#     ambient_charged → block.trial_spawner.ambient_ominous。
#     同じ番号のまま名前だけ変わる）と新6件（ominous_activate／
#     ambient_ominous を除く music_disc.creator／
#     music_disc.creator_music_box／music_disc.precipice／
#     block.vault.reject_rewarded_player）。
#   属性：新9件（generic.burning_time ほか。名前順に挿入されるため
#     既存の番号がずれる。属性の表は別途 protocol.json から作る）。
#   データコンポーネント種別：新1件（jukebox_playable。種別ID 42 に
#     挿入され、42以降の種別IDが+1ずれる）。
# 766→767 の順向きは改名だけが代替で、削除された音・パーティクルは
# 無い。改名は SOUND_FALLBACK_767 に入れる（766 の wind_burst と
# 同じ決め方。build_pair は音の改名を rename ではなく
# sound_fallbacks から引くため）。767→766 の逆向きは新規の3アイテムを
# 石（1）へ落とす（reverse_fallback=1）。
RENAME_767 = {}
SOUND_FALLBACK_767 = {
    "block.trial_spawner.charge_activate": "block.trial_spawner.ominous_activate",
    "block.trial_spawner.ambient_charged": "block.trial_spawner.ambient_ominous",
}
PARTICLE_FALLBACK_767 = {}

# 767→766 の反転で使う代替先（766側）。766→767 の順向きが単射
# （改名は1対1、挿入は末尾・途中への追加のみ）のため、反転で
# 決まらないのは 767 の新規分（アイテム3件・音4件）だけで、補正は
# 要らない。代替先は 766 の石・ブタ・石の破壊音・entity_effect・
# 作業台（SOUND_NAME_FALLBACKS_766_TO_765 と同じ決め方）。
UP_767_FALLBACKS = {
    "blockstates": 1, "items": 1, "entities": 77, "sounds": 1358,
    "particles": 20, "blocks": 1, "menus": 12,
}
UP_767_CORRECTIONS = {}


# ----------------------------------------------------------------------
# 代替表（767→768。768 に無い 767 側のものだけ使う）
# ----------------------------------------------------------------------
# ViaVersion mapping-1.21.2.json と mapping-1.21.json の差分で確認：
#   ブロック状態：新634件・削除0件（ペールオーク・クリエイキングハート
#     の追加と、挿入による番号のずれ。名前が同じものはそのまま写る）。
#   ブロック：新24件・削除0件（ペールオーク類・クリエイキングハート・
#     ペールモス類）。アイテム：新42件・削除0件（バンドル16色・
#     ペールオーク類・クリエイキングのスポーンエッグ・旗の模様2件）。
#   エンティティ：新22件・削除2件（boat／chest_boat が木材ごとの
#     20種に分裂し、creaking／creaking_transient が追加）。
#     767 の boat／chest_boat は oak_boat／oak_chest_boat へ落とす
#     （767 のボートの木材 variant は ChunkTranslator で種別に写す。
#     ここでは既定のオークに落とす）。
#   音：新25件・削除2件（item.goat_horn.play → item.goat_horn.sound.0、
#     entity.goat.screaming.horn_break → entity.goat.horn_break。
#     767 には既に item.goat_horn.sound.0〜.7 がある）。
#   パーティクル：新2件（trail／block_crumble）・削除0件。
#   メニュー：同一（25件）。エフェクト：同一（39件）。エンチャント：
#     同一（42件・同じ名前順。minecraft-data の dataPaths.json による
#     と 1.21.3 の enchantments は pc/1.21.1 を使う）。
#   バイオーム：新1件（pale_garden）。ダメージ種別：新2件
#     （ender_pearl／mace_smash）。ブロックエンティティ：新1件
#     （creaking_heart が index 10 に挿入され、piston 以降が +1
#     ずれる。数値の表は作らない。RegistryRemapper の手書きの
#     mapBlockEntityType* で扱う）。
#   属性：新1件（tempt_range）と改名9件（generic.／player. の
#     接頭辞が落ちたもの。ATTRIBUTE_RENAME_768 参照）。
#   データコンポーネント種別：新11件・削除1件（fire_resistant）。
#     種別IDのずれは ChunkTranslator の kind 対応表で扱う。
# 767→768 の順向きは改名なし・削除された音2件だけが代替で、他は
# 名前でそのまま写る。768→767 の逆向きは新規のものを石（1）・ブタ
# （767 の77）・石の破壊音（767 の1361）・entity_effect（20）・
# 作業台（12）へ落とす（reverse_fallback=1 と UP_768_FALLBACKS）。
RENAME_768 = {}
SOUND_FALLBACK_768 = {
    "item.goat_horn.play": "item.goat_horn.sound.0",
    "entity.goat.screaming.horn_break": "entity.goat.horn_break",
}
PARTICLE_FALLBACK_768 = {}
ENTITY_FALLBACK_768 = {
    "boat": "oak_boat",
    "chest_boat": "oak_chest_boat",
}

# 属性の改名（767 の名前 → 768 の名前。protocol.json の Update
# Attributes の key mapper で確認。768 で接頭辞が落ちた9件。
# tempt_range は768の新規で、逆向きは -1（DROP）になる）。
ATTRIBUTE_RENAME_771 = {}

# 属性の改名（771 の名前 → 772 の名前。protocol.json の Update
# Attributes の key mapper で確認。772 で改名は無く、新規も無い）。
ATTRIBUTE_RENAME_772 = {}

# 属性の改名（772 の名前 → 773 の名前。protocol.json の Update
# Attributes の key mapper で確認。773 で改名は無く、新規も無い。
# 35件すべて名前も順も同一）。
ATTRIBUTE_RENAME_773 = {}


ATTRIBUTE_RENAME_768 = {
    "generic.burning_time": "burning_time",
    "generic.explosion_knockback_resistance": "explosion_knockback_resistance",
    "player.mining_efficiency": "mining_efficiency",
    "generic.movement_efficiency": "movement_efficiency",
    "generic.oxygen_bonus": "oxygen_bonus",
    "player.sneaking_speed": "sneaking_speed",
    "player.submerged_mining_speed": "submerged_mining_speed",
    "player.sweeping_damage_ratio": "sweeping_damage_ratio",
    "generic.water_movement_efficiency": "water_movement_efficiency",
}

# 768→767 の反転で使う代替先（767側）。767→768 の順向きが単射
# （改名なし、挿入のみ）のため、反転で決まらないのは 768 の新規分
# だけで、補正は要らない……ただし boat だけは例外。768 で木材ごとに
# 分かれた boat／raft は、invert の先行勝ちでは oak_boat だけが
# 767 の boat になり、他の18件はブタ（77）になってしまう。767 の
# boat はメタデータの variant で木材を選ぶため、全 boat／raft を
# boat／chest_boat へ写す補正を入れる（ENTITY_UP_768_CORRECTIONS。
# main() の 768 処理で名前から計算する）。
UP_768_FALLBACKS = {
    "blockstates": 1, "items": 1, "entities": 77, "sounds": 1361,
    "particles": 20, "blocks": 1, "menus": 12,
}
UP_768_CORRECTIONS = {}


# ----------------------------------------------------------------------
# 代替表（768→769。769 に無い 768 側のものだけ使う）
# ----------------------------------------------------------------------
# ViaVersion mapping-1.21.4.json と mapping-1.21.2.json の差分で確認：
#   ブロック状態：新557件・削除9件（creaking_heart のプロパティが
#     creaking（disabled/dormant/active）から active＋natural の2ブールに
#     変わったため旧9状態は消え、新12状態になった。レジン・
#     アイブロッサム類の追加と挿入による番号のずれ）。
#   ブロック：新11件・削除0件（レジン類・アイブロッサム）。
#   アイテム：新10件・削除0件（レジン類・アイブロッサム）。
#   エンティティ：新0件・削除1件（creaking_transient が削除。
#     creaking に落とす）。
#   音：新15件・削除0件（クリーキングの twitch、アイブロッサム6件、
#     レジン8件）。
#   パーティクル：新1件（pale_oak_leaves が 34 に挿入され、34以降が
#     +1 ずれる）。
#   メニュー：同一（25件）。エフェクト：同一（39件）。エンチャント：
#     同一（42件・同じ名前順。minecraft-data の dataPaths.json による
#     と 1.21.4 の enchantments は pc/1.21.1 を使う）。
#   バイオーム：新1件（pale_garden が既定のレジストリに入る。
#     minecraft-data では 39。実バニラの Registry Data での順序は
#     統合テストで確認すること）。
#   ダメージ種別：同一（49件）。ブロックエンティティ：同一（45件）。
#   属性：同一（32件・同じ名前順。protocol.json の Update Attributes
#     の key mapper で確認）。
#   データコンポーネント種別：同一（67件・同じ並び。protocol.json の
#     SlotComponentType mapper で確認）。
# 768→769 の順向きは改名なし・削除されたエンティティ1件だけが代替で、
# 他は名前でそのまま写る。769→768 の逆向きは新規のものを石（1）・ブタ
# （768 の95）・石の破壊音（768 の1386）・entity_effect（20）・
# 作業台（12）へ落とす（reverse_fallback=1 と UP_769_FALLBACKS）。
RENAME_769 = {}
SOUND_FALLBACK_769 = {}
PARTICLE_FALLBACK_769 = {}
ENTITY_FALLBACK_769 = {
    "creaking_transient": "creaking",
}
def creaking_heart_props_770(p):
    # 769 の creaking_heart[active,axis,natural] →
    # 770 の creaking_heart[axis,creaking_heart_state,natural]。
    # 1.21.5 で active（bool）が creaking_heart_state
    # （uprooted／dormant／awake）に変わった。active=true は
    # awake（クリーキングを呼ぶ状態）、active=false は dormant
    # （呼ばないが起きうる状態）にする。uprooted（根こそぎにされた
    # 心臓）は 769 には無いため順向きでは現れない（逆向きは
    # BLOCK_STATE_UP_770_CORRECTIONS で active=false へ落とす）。
    return {"axis": p.get("axis", "y"),
            "creaking_heart_state":
                "awake" if p.get("active") == "true" else "dormant",
            "natural": p.get("natural", "false")}


BLOCK_FALLBACK_770 = dict(BLOCK_FALLBACK)
BLOCK_FALLBACK_770["creaking_heart"] = ("creaking_heart", creaking_heart_props_770)


# ----------------------------------------------------------------------
# 代替表（769→770。770 に無い 769 側のものだけ使う）
# ----------------------------------------------------------------------
# ViaVersion mapping-1.21.5.json と mapping-1.21.4.json の差分で確認：
#   ブロック状態：新60件・削除12件（creaking_heart のプロパティが
#     active（bool）から creaking_heart_state（uprooted／dormant／
#     awake）に変わったため旧12状態は消え、新18状態になった。
#     茂み・枯れ草・ワイルドフラワー・リーフリッター・ホタルブッシュ・
#     サボテンの花・テストブロック類の追加と挿入による番号のずれ）。
#   ブロック：新9件・削除0件（茂み・枯れ草・ワイルドフラワー・
#     リーフリッター・ホタルブッシュ・サボテンの花・テストブロック類）。
#   アイテム：新11件・削除0件（新ブロック9件＋青い卵・茶色の卵）。
#   エンティティ：新2件・削除1件（potion が splash_potion／
#     lingering_potion に分裂。769 の potion は splash_potion へ）。
#   音：新52件・削除1件（entity.wolf.howl が削除され、オオカミの
#     変種ごとの音36件・鉄の音5件・リーフリッター5件などが追加）。
#   パーティクル：新2件（tinted_leaves が 35 に挿入され、firefly が
#     113 に追加）。
#   メニュー：同一（25件）。エフェクト：同一（39件）。エンチャント：
#     同一（42件・同じ名前順。minecraft-data の dataPaths.json による
#     と 1.21.5 の enchantments は pc/1.21.5 を使い、1.21.1 のものと
#     名前も並びも同一）。
#   バイオーム：同一（65件・同じ並び。minecraft-data の biomes.json
#     で 1.21.4 と 1.21.5 が同一であることを確認）。
#   ダメージ種別：同一（49件）。ブロックエンティティ：新2件
#     （test_block／test_instance_block が末尾に追加）。
#   属性：同一（32件・同じ名前順。protocol.json の Update Attributes
#     の key mapper で確認）。
#   データコンポーネント種別：新31件・削除2件（hide_additional_tooltip／
#     hide_tooltip が tooltip_display に統合され、weapon／
#     blocks_attacks／変種まわりの成分が追加。種別IDのずれは
#     ChunkTranslator の kind 対応表で扱う）。
# 769→770 の順向きは改名なし・削除されたエンティティ1件と音1件だけが
# 代替で、他は名前でそのまま写る。770→769 の逆向きは新規のものを
# 石（1）・ブタ（769 の94）・石の破壊音（769 の1401）・entity_effect
# （20）・作業台（12）へ落とす（reverse_fallback=1 と
# UP_770_FALLBACKS）。
RENAME_770 = {}
SOUND_FALLBACK_770 = {
    "entity.wolf.howl": "entity.wolf.ambient",
}
PARTICLE_FALLBACK_770 = {}
ENTITY_FALLBACK_770 = {
    "potion": "splash_potion",
}
# 770→769 の反転表の代替先（769 側の値。up770_map() の invert() で
# 使う）。770 側の代替先（ブタ 94・石の破壊音 1417）とは別物なので
# 注意：770 側は emit_composed_770() の fallbacks(vv770) が名前から
# 引く。
UP_770_FALLBACKS = {
    "blockstates": 1, "items": 1, "entities": 94, "sounds": 1401,
    "particles": 20, "blocks": 1, "menus": 12,
}
UP_770_CORRECTIONS = {}


# ----------------------------------------------------------------------
# 代替表（770→771。771 に無い 770 側のものだけ使う）
# ----------------------------------------------------------------------
# ViaVersion mapping-1.21.6.json と mapping-1.21.5.json の差分で確認：
#   ブロック状態：新32件・削除0件（dried_ghast 1ブロックの
#     facing 4 × hydration 4 × waterlogged 2 = 32状態が中ほどに挿入
#     され、それ以降の番号がずれる）。
#   ブロック：新1件・削除0件（dried_ghast）。
#   アイテム：新19件・削除0件（dried_ghast・16色のハーネス・
#     happy_ghast_spawn_egg・music_disc_tears）。
#   エンティティ：新1件・削除0件（happy_ghast）。
#   音：新29件・削除4件（entity.leash_knot.break／place は
#     item.lead.break／tied に改名、entity.puffer_fish.ambient と
#     block.sand.wind は削除。leash の改名は 1.21.6 の変更
#     （リードの仕組み変更・リードが切れる音の追加）による。
#     block.sand.wind は 1.21.6 で砂が環境の風音を鳴らさなくなり、
#     代わりに乾燥草（dry grass）が鳴らすようになったため、
#     代替は block.dry_grass.ambient。puffer_fish.ambient は
#     1.21.6 でフグの環境音そのものが無くなったため、残るフグの音の
#     うち最も環境音に近い blow_out へ）。
#   パーティクル：同一（114件）。ブロックエンティティ：同一（47件）。
#   メニュー：同一（25件）。エフェクト：同一（39件・minecraft-data
#     effects.json で 1.21.5 と 1.21.6 が同一であることを確認）。
#   エンチャント：同一（42件・同じ名前順）。
#   バイオーム：同一（65件・同じ並び。minecraft-data の biomes.json
#     で 1.21.5 と 1.21.6 が同一であることを確認）。
#   属性：新3件・削除0件（camera_distance が 8 番に挿入され、
#     waypoint_transmit_range・waypoint_receive_range が末尾に追加。
#     ロケーターバー関連）。
#   データコンポーネント種別：同一（96件・同じ並び。minecraft-data
#     protocol.json の SlotComponentType mapper で確認）。
# 770→771 の順向きは改名2件・削除2件だけが代替で、他は名前で
# そのまま写る。771→770 の逆向きは新規のものを石（1）・ブタ
# （770 の94）・石の破壊音（770 の1417）・entity_effect（20）・
# 作業台（12）へ落とす（reverse_fallback=1 と UP_771_FALLBACKS）。
RENAME_771 = {}
SOUND_FALLBACK_771 = {
    "entity.leash_knot.break": "item.lead.break",
    "entity.leash_knot.place": "item.lead.tied",
    "entity.puffer_fish.ambient": "entity.puffer_fish.blow_out",
    "block.sand.wind": "block.dry_grass.ambient",
}
PARTICLE_FALLBACK_771 = {}
ENTITY_FALLBACK_771 = {}
BLOCK_FALLBACK_771 = dict(BLOCK_FALLBACK)
# 771→770 の反転表の代替先（770 側の値。up771_map() の invert() で
# 使う）。771 側の代替先（ブタ 95・石の破壊音 1441）とは別物なので
# 注意：771 側は emit_composed_771() の fallbacks(vv771) が名前から
# 引く。ブタは 771 では 95（happy_ghast の挿入で 1 つ後ろにずれた）、
# 石の破壊音は 771 では 1441（新音 29 件の挿入と削除 4 件で
# 770 の 1417 からずれた）。
UP_771_FALLBACKS = {
    "blockstates": 1, "items": 1, "entities": 94, "sounds": 1417,
    "particles": 20, "blocks": 1, "menus": 12,
}
UP_771_CORRECTIONS = {}


# ----------------------------------------------------------------------
# 代替表（771→772。772 に無い 771 側のものだけ使う）
# ----------------------------------------------------------------------
# ViaVersion mapping-1.21.7.json と mapping-1.21.6.json の差分で確認：
#   ブロック状態：同一（27,946件）。
#   ブロック：同一（1,105件。drops はアイテムの挿入で番号がずれるだけ）。
#   アイテム：新1件（music_disc_lava_chicken が 1256 に挿入され、
#     1256以降が +1 ずれる）。
#   エンティティ：同一（151件）。
#   音：新1件（music_disc.lava_chicken が 939 に挿入され、939以降が
#     +1 ずれる）。
#   パーティクル：同一（114件）。ブロックエンティティ：同一（47件）。
#   メニュー：同一（25件）。エフェクト：同一（39件・minecraft-data の
#     effects.json では id 26 の名前だけ Unluck→BadLuck に変わっているが
#     順番も件数も同じ）。
#   エンチャント：同一（42件・同じ名前順。minCost／maxCost の値だけ
#     minecraft-data で変わっているが順番は同じ）。
#   バイオーム：同一（65件・同じ並び。minecraft-data の biomes.json
#     で 1.21.6 と 1.21.8 が同一であることを確認）。
#   属性：同一（35件・同じ名前順。protocol.json の Update Attributes
#     の key mapper で確認）。
#   データコンポーネント種別：同一（96件・同じ並び。protocol.json の
#     SlotComponentType mapper で確認）。
# 771→772 の順向きは改名・削除が無く、新規2件だけ。772→771 の逆向きは
# 新規のものを石（1）・ブタ（771 の95）・石の破壊音（771 の1441）・
# entity_effect（20）・作業台（12）へ落とす（reverse_fallback=1 と
# UP_772_FALLBACKS）。
RENAME_772 = {}
SOUND_FALLBACK_772 = {}
PARTICLE_FALLBACK_772 = {}
ENTITY_FALLBACK_772 = {}
BLOCK_FALLBACK_772 = dict(BLOCK_FALLBACK)
# 772→771 の反転表の代替先（771 側の値。up772_map() の invert() で
# 使う）。772 側の代替先（ブタ 95・石の破壊音 1442）とは別物なので
# 注意：772 側は emit_composed_772() の fallbacks(vv772) が名前から
# 引く。ブタは 772 でも 95（エンティティに変化なし）、石の破壊音は
# 771 では 1441（新音 1 件の挿入で 772 では 1442 になる）。
UP_772_FALLBACKS = {
    "blockstates": 1, "items": 1, "entities": 95, "sounds": 1441,
    "particles": 20, "blocks": 1, "menus": 12,
}
UP_772_CORRECTIONS = {}


# ----------------------------------------------------------------------
# 代替表（772→773。773 に無い 772 側のものだけ使う）
# ----------------------------------------------------------------------
# ViaVersion mapping-1.21.9.json と mapping-1.21.7.json の差分で確認：
#   ブロック状態：27,946→29,671 件（+1,725。銅の追加が大半。chain は
#     iron_chain に改名され、状態の並びは同じ）。
#   ブロック：1,105→1,166 件（新62件・改名1件。棚12種、銅の松明・
#     銅格子8種・銅チェーン8種（鉄チェーンも iron_chain に改名）・
#     銅ランタン8種・銅チェスト8種・銅ゴーレム像8種、避雷針の
#     exposed／weathered／oxidized／waxed 7種、鉄チェーンの改名）。
#   アイテム：1,416→1,488 件（新73件・改名1件。銅の道具5種・防具4種・
#     銅ゴーレムのスポーンエッグ・銅の馬鎧・銅ナゲットと上記ブロック類）。
#   エンティティ：151→153 件（新2件：copper_golem・mannequin）。
#   音：1,728→1,771 件（新43件。銅ゴーレム・銅チェスト・棚・銅ゴーレム像
#     の音と weather.end_flash）。
#   パーティクル：114→115 件（新1件：copper_fire_flame）。
#   ブロックエンティティ：47→49 件（新2件：shelf（40 に挿入）、
#     copper_golem_statue（末尾 48））。チャンクの Block Entity Data
#     の種別番号は shelf の挿入で 40 以降が +1 ずれる（レジストリ表
#     ではなく RegistryRemapper の mapBlockEntityType* で扱う）。
#   メニュー：同一（25件）。エフェクト：同一（minecraft-data の
#     effects.json は 1.21.8 と 1.21.9 で同一ファイル）。
#   エンチャント：同一（42件・同じ名前順。minecraft-data の
#     enchantments.json で確認）。
#   バイオーム：同一（65件・同じ並び。minecraft-data の biomes.json
#     で 1.21.8 と 1.21.9 が同一であることを確認。バニラ jar 内の
#     data/minecraft/worldgen/biome も 65 件で同一）。
#   属性：同一（35件・同じ名前順。protocol.json の Update Attributes
#     の key mapper で確認）。
#   データコンポーネント種別：同一（96件・同じ並び。ViaVersion
#     mapping-1.21.9.json の data_component_type で確認）。
#   小レジストリ（絵画・ジュークボックスの歌・ダメージ種別・狼の変種・
#     旗・トリム・楽器・チャット種別）：バニラ jar 内の data/minecraft
#     で 1.21.8 と 1.21.9 が同一（絵画 51・歌 21・ダメージ 49・狼 9・
#     旗 43・トリム模様 18・トリム素材 11・楽器 8・チャット 7）。
#   772→773 の順向きは改名1件（chain→iron_chain）だけで、削除は無い。
#   773→772 の逆向きは新規のものを石（1）・ブタ（772 の95）・石の破壊音
#   （772 の1442）・entity_effect（772 の20）・作業台（12）へ落とす
#   （reverse_fallback=1 と UP_773_FALLBACKS）。銅の道具・防具など
#   新規アイテムも石へ落とす（既存方針）。
RENAME_773 = {"chain": "iron_chain"}
SOUND_FALLBACK_773 = {}
PARTICLE_FALLBACK_773 = {}
ENTITY_FALLBACK_773 = {}
BLOCK_FALLBACK_773 = dict(BLOCK_FALLBACK)
# 773→772 の反転表の代替先（772 側の値。up773_map() の invert() で
# 使う）。773 側の代替先（ブタ 97・石の破壊音 1484）とは別物なので
# 注意：773 側は emit_composed_773() の fallbacks(vv773) が名前から
# 引く。ブタは 772 では 95（copper_golem・mannequin の挿入で 773
# では 97 になる）、石の破壊音は 772 では 1442（新音 43 件の挿入で
# 773 では 1484 になる）。
UP_773_FALLBACKS = {
    "blockstates": 1, "items": 1, "entities": 95, "sounds": 1442,
    "particles": 20, "blocks": 1, "menus": 12,
}
UP_773_CORRECTIONS = {}

RENAME_774 = {}
SOUND_FALLBACK_774 = {}
PARTICLE_FALLBACK_774 = {}
ENTITY_FALLBACK_774 = {}
BLOCK_FALLBACK_774 = dict(BLOCK_FALLBACK)
# 774→773 の反転表の代替先（773 側の値。up774_map() の invert() で
# 使う）。774 側の代替先（ブタ 100・石の破壊音 1535）とは別物なので
# 注意：774 側は emit_composed_774() の fallbacks(vv774) が名前から
# 引く。ブタは 773 では 97（camel_husk・nautilus・parched の挿入で
# 774 では 100 になる）、石の破壊音は 773 では 1484（新音 67 件の
# 挿入で 774 では 1535 になる）。
UP_774_FALLBACKS = {
    "blockstates": 1, "items": 1, "entities": 97, "sounds": 1484,
    "particles": 20, "blocks": 1, "menus": 12,
}
UP_774_CORRECTIONS = {}
ATTRIBUTE_RENAME_774 = {}

# 774→775 は改名・削除が無い（ViaVersion mapping-26.1.json で確認。
# 新規は金のタンポポ・トランペットの音符ブロック・赤ちゃん動物の声など）。
RENAME_775 = {}
SOUND_FALLBACK_775 = {}
PARTICLE_FALLBACK_775 = {}
ENTITY_FALLBACK_775 = {}
BLOCK_FALLBACK_775 = dict(BLOCK_FALLBACK)
# 775→774 の反転表の代替先（774 側の値。up775_map() の invert() で
# 使う）。ブタは 774 では 100、石の破壊音は 774 では 1535。
# パーティクルは 774 の entity_effect=21（773 で 1 件挿入され 20→21
# にずれた。UP_774_FALLBACKS の 20 は end_rod を指しており誤りだが、
# 774 の表は生成済みのため 775 では正しい 21 を使う）。
UP_775_FALLBACKS = {
    "blockstates": 1, "items": 1, "entities": 100, "sounds": 1535,
    "particles": 21, "blocks": 1, "menus": 12,
}
UP_775_CORRECTIONS = {}
ATTRIBUTE_RENAME_775 = {}


# 769→768 の反転表の代替先（768 側の値。up769_map() の invert() で
# 使う）。769 側の代替先（ブタ 94・石の破壊音 1401）とは別物なので
# 注意：769 側は emit_composed_769() の fallbacks(vv769) が名前から
# 引く。ブタは 769 では 94（creaking_transient の削除で 1 つ前に
# ずれた）、石の破壊音は 769 では 1401 だが、ここは 768 側の値。
UP_769_FALLBACKS = {
    "blockstates": 1, "items": 1, "entities": 95, "sounds": 1386,
    "particles": 20, "blocks": 1, "menus": 12,
}
UP_769_CORRECTIONS = {}


def up766_map(maps766, kind, vv766):
    """766→765 の反転表（invert＋補正＋代替先埋め）。"""
    forward = maps766[kind]
    ranges, overrides = compress(forward, len(forward))
    return invert_java(ranges, overrides, len(forward), len(vv766[kind]),
                       UP_766_FALLBACKS[kind], UP_766_CORRECTIONS.get(kind))


def up767_map(maps767, kind, vv767):
    """767→766 の反転表（invert＋代替先埋め。補正は無い）。"""
    forward = maps767[kind]
    ranges, overrides = compress(forward, len(forward))
    return invert_java(ranges, overrides, len(forward), len(vv767[kind]),
                       UP_767_FALLBACKS[kind], UP_767_CORRECTIONS.get(kind))


def up768_map(maps768, kind, vv768):
    """768→767 の反転表（invert＋代替先埋め。boat の補正あり）。"""
    forward = maps768[kind]
    ranges, overrides = compress(forward, len(forward))
    return invert_java(ranges, overrides, len(forward), len(vv768[kind]),
                       UP_768_FALLBACKS[kind], UP_768_CORRECTIONS.get(kind))


def up769_map(maps769, kind, vv769):
    """769→768 の反転表（invert＋代替先埋め。creaking_heart の補正あり）。"""
    forward = maps769[kind]
    ranges, overrides = compress(forward, len(forward))
    return invert_java(ranges, overrides, len(forward), len(vv769[kind]),
                       UP_769_FALLBACKS[kind], UP_769_CORRECTIONS.get(kind))


def up770_map(maps770, kind, vv770):
    """770→769 の反転表（invert＋代替先埋め。creaking_heart の
    uprooted と lingering_potion の補正あり）。"""
    forward = maps770[kind]
    ranges, overrides = compress(forward, len(forward))
    return invert_java(ranges, overrides, len(forward), len(vv770[kind]),
                       UP_770_FALLBACKS[kind], UP_770_CORRECTIONS.get(kind))

def up771_map(maps771, kind, vv771):
    """771→770 の反転表（invert＋代替先埋め。補正は無い）。"""
    forward = maps771[kind]
    ranges, overrides = compress(forward, len(forward))
    return invert_java(ranges, overrides, len(forward), len(vv771[kind]),
                       UP_771_FALLBACKS[kind], UP_771_CORRECTIONS.get(kind))


def up772_map(maps772, kind, vv772):
    """772→771 の反転表（invert＋代替先埋め。補正は無い）。"""
    forward = maps772[kind]
    ranges, overrides = compress(forward, len(forward))
    return invert_java(ranges, overrides, len(forward), len(vv772[kind]),
                       UP_772_FALLBACKS[kind], UP_772_CORRECTIONS.get(kind))


def up773_map(maps773, kind, vv773):
    """773→772 の反転表（invert＋代替先埋め。補正は無い）。"""
    forward = maps773[kind]
    ranges, overrides = compress(forward, len(forward))
    return invert_java(ranges, overrides, len(forward), len(vv773[kind]),
                       UP_773_FALLBACKS[kind], UP_773_CORRECTIONS.get(kind))


def up774_map(maps774, kind, vv774):
    """774→773 の反転表（invert＋代替先埋め。補正は無い）。"""
    forward = maps774[kind]
    ranges, overrides = compress(forward, len(forward))
    return invert_java(ranges, overrides, len(forward), len(vv774[kind]),
                       UP_774_FALLBACKS[kind], UP_774_CORRECTIONS.get(kind))


def up775_map(maps775, kind, vv775):
    """775→774 の反転表（invert＋代替先埋め。補正は無い）。"""
    forward = maps775[kind]
    ranges, overrides = compress(forward, len(forward))
    return invert_java(ranges, overrides, len(forward), len(vv775[kind]),
                       UP_775_FALLBACKS[kind], UP_775_CORRECTIONS.get(kind))


def emit_composed_769(out, tag, maps_a_to_768, maps_768_to_a, vv_a,
                      maps769, vv769, vv768, direction):
    """768→769 の合成で作る、離れた組み合わせ（763↔769、764↔769、
    765↔769、766↔769、767↔769）の合成表を Java の表として出す。
    emit_composed_768 と同じ方針で、中間を 768 に置き換えたもの。
    実行時は合成済みの1つの表を直接引く（二段変換はしない）。

    tag は "763_TO_769"／"769_TO_763"／"764_TO_769"／"769_TO_764"／
    "765_TO_769"／"769_TO_765"／"766_TO_769"／"769_TO_766"／
    "767_TO_769"／"769_TO_767"。
    maps_a_to_768 は A→768 の表（763／764／765／766 は
    emit_composed_768 で作った合成表、767 は build_pair の 767→768、
    768 は恒等写像）、maps_768_to_a は 768→A の表、vv_a は A 側の
    ViaVersion mapping、maps769 は 768→769 の表、vv769 は 769 側の
    mapping、vv768 は 768 側の mapping。direction が "up"（A→769）の
    場合：A→768→769。"down"（769→A）の場合：769→768（反転）→A。
    アイテムだけは名前から作った逆変換表（maps769 の "reverse_items"）
    で合成する。戻り値は合成表の dict（kind → {source id: target id}）。
    """
    kinds = ["blockstates", "items", "entities", "sounds",
             "particles", "blocks", "menus"]
    java_names = {"blockstates": "BLOCK_STATE", "items": "ITEM",
                  "entities": "ENTITY_TYPE", "sounds": "SOUND",
                  "particles": "PARTICLE", "blocks": "BLOCK", "menus": "MENU"}
    # 代替先は名前から引く（769 の block.stone.break は 768 の 1386
    # から +15 ずれて 1401 になる、といったずれを手計算しないため）。
    def fallbacks(vv):
        return {"blockstates": 1, "items": 1,
                "entities": vv["entities"].index("pig"),
                "sounds": vv["sounds"].index("block.stone.break"),
                "particles": vv["particles"].index("entity_effect"),
                "blocks": 1,
                "menus": vv["menus"].index("crafting")}
    fb769 = fallbacks(vv769)
    fb768 = fallbacks(vv768)
    composed = {}
    for kind in kinds:
        if kind == "items":
            if direction == "up":
                first = maps_a_to_768[kind]
                second = maps769[kind]
                table = compose_maps(first, second, fb769[kind])
                size = len(vv_a[kind])
            else:
                first = maps769["reverse_items"]
                second = maps_768_to_a[kind]
                table = compose_maps(first, second, 1)
                size = len(vv769[kind])
        elif direction == "up":
            first = maps_a_to_768[kind]
            second = maps769[kind]
            table = compose_maps(first, second, fb769[kind])
            size = len(vv_a[kind])
        else:
            first_list = up769_map(maps769, kind, vv769)
            second_list = [maps_768_to_a[kind].get(i, fb768[kind])
                           for i in range(len(vv768[kind]))]
            table = {i: v for i, v in enumerate(
                compose_list(first_list, second_list, fb768[kind]))}
            size = len(vv769[kind])
        iv, ov = compress(table, size)
        name = java_names[kind] + "_" + tag
        out.append(java_int3(name + "_RANGES", iv))
        out.append(java_int2(name + "_OVERRIDES", ov))
        out.append(f"    // {name}: entries={size} ranges={len(iv)} overrides={len(ov)}")
        composed[kind] = table
    return composed


def emit_composed_770(out, tag, maps_a_to_769, maps_769_to_a, vv_a,
                      maps770, vv770, vv769, direction):
    """769→770 の合成で作る、離れた組み合わせ（763↔770、764↔770、
    765↔770、766↔770、767↔770、768↔770）の合成表を Java の表として出す。
    emit_composed_769 と同じ方針で、中間を 769 に置き換えたもの。
    実行時は合成済みの1つの表を直接引く（二段変換はしない）。

    tag は "763_TO_770"／"770_TO_763"／"764_TO_770"／"770_TO_764"／
    "765_TO_770"／"770_TO_765"／"766_TO_770"／"770_TO_766"／
    "767_TO_770"／"770_TO_767"／"768_TO_770"／"770_TO_768"。
    maps_a_to_769 は A→769 の表（763／764／765／766／767 は
    emit_composed_769 で作った合成表、768 は build_pair の 768→769、
    769 は恒等写像）、maps_769_to_a は 769→A の表、vv_a は A 側の
    ViaVersion mapping、maps770 は 769→770 の表、vv770 は 770 側の
    mapping、vv769 は 769 側の mapping。direction が "up"（A→770）の
    場合：A→769→770。"down"（770→A）の場合：770→769（反転）→A。
    アイテムだけは名前から作った逆変換表（maps770 の "reverse_items"）
    で合成する。戻り値は合成表の dict（kind → {source id: target id}）。
    """
    kinds = ["blockstates", "items", "entities", "sounds",
             "particles", "blocks", "menus"]
    java_names = {"blockstates": "BLOCK_STATE", "items": "ITEM",
                  "entities": "ENTITY_TYPE", "sounds": "SOUND",
                  "particles": "PARTICLE", "blocks": "BLOCK", "menus": "MENU"}
    # 代替先は名前から引く（770 の block.stone.break は 769 の 1401
    # から +51 ずれて 1417 になる、といったずれを手計算しないため）。
    def fallbacks(vv):
        return {"blockstates": 1, "items": 1,
                "entities": vv["entities"].index("pig"),
                "sounds": vv["sounds"].index("block.stone.break"),
                "particles": vv["particles"].index("entity_effect"),
                "blocks": 1,
                "menus": vv["menus"].index("crafting")}
    fb770 = fallbacks(vv770)
    fb769 = fallbacks(vv769)
    composed = {}
    for kind in kinds:
        if kind == "items":
            if direction == "up":
                first = maps_a_to_769[kind]
                second = maps770[kind]
                table = compose_maps(first, second, fb770[kind])
                size = len(vv_a[kind])
            else:
                first = maps770["reverse_items"]
                second = maps_769_to_a[kind]
                table = compose_maps(first, second, 1)
                size = len(vv770[kind])
        elif direction == "up":
            first = maps_a_to_769[kind]
            second = maps770[kind]
            table = compose_maps(first, second, fb770[kind])
            size = len(vv_a[kind])
        else:
            first_list = up770_map(maps770, kind, vv770)
            second_list = [maps_769_to_a[kind].get(i, fb769[kind])
                           for i in range(len(vv769[kind]))]
            table = {i: v for i, v in enumerate(
                compose_list(first_list, second_list, fb769[kind]))}
            size = len(vv770[kind])
        iv, ov = compress(table, size)
        name = java_names[kind] + "_" + tag
        out.append(java_int3(name + "_RANGES", iv))
        out.append(java_int2(name + "_OVERRIDES", ov))
        out.append(f"    // {name}: entries={size} ranges={len(iv)} overrides={len(ov)}")
        composed[kind] = table
    return composed


def emit_composed_771(out, tag, maps_a_to_770, maps_770_to_a, vv_a,
                      maps771, vv771, vv770, direction):
    """770→771 の合成で作る、離れた組み合わせ（763↔771、764↔771、
    765↔771、766↔771、767↔771、768↔771、769↔771、770↔771）の合成表を Java の表として出す。
    emit_composed_770 と同じ方針で、中間を 770 に置き換えたもの。
    実行時は合成済みの1つの表を直接引く（二段変換はしない）。

    tag は "763_TO_771"／"771_TO_763"／"764_TO_771"／"771_TO_764"／
    "765_TO_771"／"771_TO_765"／"766_TO_771"／"771_TO_766"／
    "767_TO_771"／"771_TO_767"／"768_TO_771"／"771_TO_768"／
    "769_TO_771"／"771_TO_769"／"770_TO_771"／"771_TO_770"。
    maps_a_to_770 は A→770 の表（763／764／765／766／767／768 は
    emit_composed_770 で作った合成表、769 は build_pair の 769→770、
    770 は恒等写像）、maps_770_to_a は 770→A の表、vv_a は A 側の
    ViaVersion mapping、maps771 は 770→771 の表、vv771 は 771 側の
    mapping、vv770 は 770 側の mapping。direction が "up"（A→771）の
    場合：A→770→771。"down"（771→A）の場合：771→770（反転）→A。
    アイテムだけは名前から作った逆変換表（maps771 の "reverse_items"）
    で合成する。戻り値は合成表の dict（kind → {source id: target id}）。
    """
    kinds = ["blockstates", "items", "entities", "sounds",
             "particles", "blocks", "menus"]
    java_names = {"blockstates": "BLOCK_STATE", "items": "ITEM",
                  "entities": "ENTITY_TYPE", "sounds": "SOUND",
                  "particles": "PARTICLE", "blocks": "BLOCK", "menus": "MENU"}
    # 代替先は名前から引く（771 の block.stone.break は 770 の 1417
    # から +24 ずれて 1441 になる、といったずれを手計算しないため）。
    def fallbacks(vv):
        return {"blockstates": 1, "items": 1,
                "entities": vv["entities"].index("pig"),
                "sounds": vv["sounds"].index("block.stone.break"),
                "particles": vv["particles"].index("entity_effect"),
                "blocks": 1,
                "menus": vv["menus"].index("crafting")}
    fb771 = fallbacks(vv771)
    fb770 = fallbacks(vv770)
    composed = {}
    for kind in kinds:
        if kind == "items":
            if direction == "up":
                first = maps_a_to_770[kind]
                second = maps771[kind]
                table = compose_maps(first, second, fb771[kind])
                size = len(vv_a[kind])
            else:
                first = maps771["reverse_items"]
                second = maps_770_to_a[kind]
                table = compose_maps(first, second, 1)
                size = len(vv771[kind])
        elif direction == "up":
            first = maps_a_to_770[kind]
            second = maps771[kind]
            table = compose_maps(first, second, fb771[kind])
            size = len(vv_a[kind])
        else:
            first_list = up771_map(maps771, kind, vv771)
            second_list = [maps_770_to_a[kind].get(i, fb770[kind])
                           for i in range(len(vv770[kind]))]
            table = {i: v for i, v in enumerate(
                compose_list(first_list, second_list, fb770[kind]))}
            size = len(vv771[kind])
        iv, ov = compress(table, size)
        name = java_names[kind] + "_" + tag
        out.append(java_int3(name + "_RANGES", iv))
        out.append(java_int2(name + "_OVERRIDES", ov))
        out.append(f"    // {name}: entries={size} ranges={len(iv)} overrides={len(ov)}")
        composed[kind] = table
    return composed


def emit_composed_772(out, tag, maps_a_to_771, maps_771_to_a, vv_a,
                      maps772, vv772, vv771, direction):
    """771→772 の合成で作る、離れた組み合わせ（763↔772、764↔772、
    765↔772、766↔772、767↔772、768↔772、769↔772、770↔772、771↔772）の
    合成表を Java の表として出す。emit_composed_771 と同じ方針で、
    中間を 771 に置き換えたもの。実行時は合成済みの1つの表を直接引く
    （二段変換はしない）。

    tag は "763_TO_772"／"772_TO_763"／"764_TO_772"／"772_TO_764"／
    "765_TO_772"／"772_TO_765"／"766_TO_772"／"772_TO_766"／
    "767_TO_772"／"772_TO_767"／"768_TO_772"／"772_TO_768"／
    "769_TO_772"／"772_TO_769"／"770_TO_772"／"772_TO_770"／
    "771_TO_772"／"772_TO_771"。
    maps_a_to_771 は A→771 の表（763／764／765／766／767／768／769／
    770 は emit_composed_771 で作った合成表、771 は恒等写像）、
    maps_771_to_a は 771→A の表、vv_a は A 側の ViaVersion mapping、
    maps772 は 771→772 の表、vv772 は 772 側の mapping、vv771 は
    771 側の mapping。direction が "up"（A→772）の場合：A→771→772。
    "down"（772→A）の場合：772→771（反転）→A。アイテムだけは名前から
    作った逆変換表（maps772 の "reverse_items"）で合成する。戻り値は
    合成表の dict（kind → {source id: target id}）。
    """
    kinds = ["blockstates", "items", "entities", "sounds",
             "particles", "blocks", "menus"]
    java_names = {"blockstates": "BLOCK_STATE", "items": "ITEM",
                  "entities": "ENTITY_TYPE", "sounds": "SOUND",
                  "particles": "PARTICLE", "blocks": "BLOCK", "menus": "MENU"}
    # 代替先は名前から引く（772 の block.stone.break は 771 の 1441
    # から +1 ずれて 1442 になる、といったずれを手計算しないため）。
    def fallbacks(vv):
        return {"blockstates": 1, "items": 1,
                "entities": vv["entities"].index("pig"),
                "sounds": vv["sounds"].index("block.stone.break"),
                "particles": vv["particles"].index("entity_effect"),
                "blocks": 1,
                "menus": vv["menus"].index("crafting")}
    fb772 = fallbacks(vv772)
    fb771 = fallbacks(vv771)
    composed = {}
    for kind in kinds:
        if kind == "items":
            if direction == "up":
                first = maps_a_to_771[kind]
                second = maps772[kind]
                table = compose_maps(first, second, fb772[kind])
                size = len(vv_a[kind])
            else:
                first = maps772["reverse_items"]
                second = maps_771_to_a[kind]
                table = compose_maps(first, second, 1)
                size = len(vv772[kind])
        elif direction == "up":
            first = maps_a_to_771[kind]
            second = maps772[kind]
            table = compose_maps(first, second, fb772[kind])
            size = len(vv_a[kind])
        else:
            first_list = up772_map(maps772, kind, vv772)
            second_list = [maps_771_to_a[kind].get(i, fb771[kind])
                           for i in range(len(vv771[kind]))]
            table = {i: v for i, v in enumerate(
                compose_list(first_list, second_list, fb771[kind]))}
            size = len(vv772[kind])
        iv, ov = compress(table, size)
        name = java_names[kind] + "_" + tag
        out.append(java_int3(name + "_RANGES", iv))
        out.append(java_int2(name + "_OVERRIDES", ov))
        out.append(f"    // {name}: entries={size} ranges={len(iv)} overrides={len(ov)}")
        composed[kind] = table
    return composed



def emit_composed_773(out, tag, maps_a_to_772, maps_772_to_a, vv_a,
                      maps773, vv773, vv772, direction):
    """772→773 の合成で作る、離れた組み合わせ（763↔773、764↔773、
    765↔773、766↔773、767↔773、768↔773、769↔773、770↔773、771↔773、
    772↔773）の
    合成表を Java の表として出す。emit_composed_772 と同じ方針で、
    中間を 772 に置き換えたもの。実行時は合成済みの1つの表を直接引く
    （二段変換はしない）。

    tag は "763_TO_773"／"773_TO_763"／"764_TO_773"／"773_TO_764"／
    "765_TO_773"／"773_TO_765"／"766_TO_773"／"773_TO_766"／
    "767_TO_773"／"773_TO_767"／"768_TO_773"／"773_TO_768"／
    "769_TO_773"／"773_TO_769"／"770_TO_773"／"773_TO_770"／
    "771_TO_773"／"773_TO_771"／"772_TO_773"／"773_TO_772"。
    maps_a_to_772 は A→772 の表（763／764／765／766／767／768／769／
    770／771 は emit_composed_772 で作った合成表、772 は恒等写像）、
    maps_772_to_a は 772→A の表、vv_a は A 側の ViaVersion mapping、
    maps773 は 772→773 の表、vv773 は 773 側の mapping、vv772 は
    772 側の mapping。direction が "up"（A→773）の場合：A→772→773。
    "down"（773→A）の場合：773→772（反転）→A。アイテムだけは名前から
    作った逆変換表（maps773 の "reverse_items"）で合成する。戻り値は
    合成表の dict（kind → {source id: target id}）。
    """
    kinds = ["blockstates", "items", "entities", "sounds",
             "particles", "blocks", "menus"]
    java_names = {"blockstates": "BLOCK_STATE", "items": "ITEM",
                  "entities": "ENTITY_TYPE", "sounds": "SOUND",
                  "particles": "PARTICLE", "blocks": "BLOCK", "menus": "MENU"}
    # 代替先は名前から引く（773 の block.stone.break は 772 の 1442
    # から +42 ずれて 1484 になる、といったずれを手計算しないため）。
    def fallbacks(vv):
        return {"blockstates": 1, "items": 1,
                "entities": vv["entities"].index("pig"),
                "sounds": vv["sounds"].index("block.stone.break"),
                "particles": vv["particles"].index("entity_effect"),
                "blocks": 1,
                "menus": vv["menus"].index("crafting")}
    fb773 = fallbacks(vv773)
    fb772 = fallbacks(vv772)
    composed = {}
    for kind in kinds:
        if kind == "items":
            if direction == "up":
                first = maps_a_to_772[kind]
                second = maps773[kind]
                table = compose_maps(first, second, fb773[kind])
                size = len(vv_a[kind])
            else:
                first = maps773["reverse_items"]
                second = maps_772_to_a[kind]
                table = compose_maps(first, second, 1)
                size = len(vv773[kind])
        elif direction == "up":
            first = maps_a_to_772[kind]
            second = maps773[kind]
            table = compose_maps(first, second, fb773[kind])
            size = len(vv_a[kind])
        else:
            first_list = up773_map(maps773, kind, vv773)
            second_list = [maps_772_to_a[kind].get(i, fb772[kind])
                           for i in range(len(vv772[kind]))]
            table = {i: v for i, v in enumerate(
                compose_list(first_list, second_list, fb772[kind]))}
            size = len(vv773[kind])
        iv, ov = compress(table, size)
        name = java_names[kind] + "_" + tag
        out.append(java_int3(name + "_RANGES", iv))
        out.append(java_int2(name + "_OVERRIDES", ov))
        out.append(f"    // {name}: entries={size} ranges={len(iv)} overrides={len(ov)}")
        composed[kind] = table
    return composed


def emit_composed_774(out, tag, maps_a_to_773, maps_773_to_a, vv_a,
                      maps774, vv774, vv773, direction):
    """773→774 の合成で作る、離れた組み合わせ（763↔774、764↔774、
    765↔774、766↔774、767↔774、768↔774、769↔774、770↔774、771↔774、772↔774、
    773↔774）の
    合成表を Java の表として出す。emit_composed_773 と同じ方針で、
    中間を 773 に置き換えたもの。実行時は合成済みの1つの表を直接引く
    （二段変換はしない）。

    tag は "763_TO_774"／"774_TO_763"／"764_TO_774"／"774_TO_764"／
    "765_TO_774"／"774_TO_765"／"766_TO_774"／"774_TO_766"／
    "767_TO_774"／"774_TO_767"／"768_TO_774"／"774_TO_768"／
    "769_TO_774"／"774_TO_769"／"770_TO_774"／"774_TO_770"／
    "771_TO_774"／"774_TO_771"／"772_TO_774"／"774_TO_772"／"773_TO_774"／"774_TO_773"。
    maps_a_to_773 は A→773 の表（763／764／765／766／767／768／769／
    770／771／772 は emit_composed_773 で作った合成表、773 は恒等写像）、
    maps_773_to_a は 773→A の表、vv_a は A 側の ViaVersion mapping、
    maps774 は 773→774 の表、vv774 は 774 側の mapping、vv773 は
    773 側の mapping。direction が "up"（A→774）の場合：A→773→774。
    "down"（774→A）の場合：774→773（反転）→A。アイテムだけは名前から
    作った逆変換表（maps774 の "reverse_items"）で合成する。戻り値は
    合成表の dict（kind → {source id: target id}）。
    """
    kinds = ["blockstates", "items", "entities", "sounds",
             "particles", "blocks", "menus"]
    java_names = {"blockstates": "BLOCK_STATE", "items": "ITEM",
                  "entities": "ENTITY_TYPE", "sounds": "SOUND",
                  "particles": "PARTICLE", "blocks": "BLOCK", "menus": "MENU"}
    # 代替先は名前から引く（774 の block.stone.break は 773 の 1484
    # から +67 ずれて 1535 になる、といったずれを手計算しないため）。
    def fallbacks(vv):
        return {"blockstates": 1, "items": 1,
                "entities": vv["entities"].index("pig"),
                "sounds": vv["sounds"].index("block.stone.break"),
                "particles": vv["particles"].index("entity_effect"),
                "blocks": 1,
                "menus": vv["menus"].index("crafting")}
    fb774 = fallbacks(vv774)
    fb773 = fallbacks(vv773)
    composed = {}
    for kind in kinds:
        if kind == "items":
            if direction == "up":
                first = maps_a_to_773[kind]
                second = maps774[kind]
                table = compose_maps(first, second, fb774[kind])
                size = len(vv_a[kind])
            else:
                first = maps774["reverse_items"]
                second = maps_773_to_a[kind]
                table = compose_maps(first, second, 1)
                size = len(vv774[kind])
        elif direction == "up":
            first = maps_a_to_773[kind]
            second = maps774[kind]
            table = compose_maps(first, second, fb774[kind])
            size = len(vv_a[kind])
        else:
            first_list = up774_map(maps774, kind, vv774)
            second_list = [maps_773_to_a[kind].get(i, fb773[kind])
                           for i in range(len(vv773[kind]))]
            table = {i: v for i, v in enumerate(
                compose_list(first_list, second_list, fb773[kind]))}
            size = len(vv774[kind])
        iv, ov = compress(table, size)
        name = java_names[kind] + "_" + tag
        out.append(java_int3(name + "_RANGES", iv))
        out.append(java_int2(name + "_OVERRIDES", ov))
        out.append(f"    // {name}: entries={size} ranges={len(iv)} overrides={len(ov)}")
        composed[kind] = table
    return composed



def emit_composed_775(out, tag, maps_a_to_774, maps_774_to_a, vv_a,
                      maps775, vv775, vv774, direction):
    """774→775 の合成で作る、離れた組み合わせ（763↔775、764↔775、
    765↔775、766↔775、767↔775、768↔775、769↔775、770↔775、771↔775、772↔775、
    773↔775、774↔775）の
    合成表を Java の表として出す。emit_composed_774 と同じ方針で、
    中間を 774 に置き換えたもの。実行時は合成済みの1つの表を直接引く
    （二段変換はしない）。

    tag は "763_TO_775"／"775_TO_763"／"764_TO_775"／"775_TO_764"／
    "765_TO_775"／"775_TO_765"／"766_TO_775"／"775_TO_766"／
    "767_TO_775"／"775_TO_767"／"768_TO_775"／"775_TO_768"／
    "769_TO_775"／"775_TO_769"／"770_TO_775"／"775_TO_770"／
    "771_TO_775"／"775_TO_771"／"772_TO_775"／"775_TO_772"／"773_TO_775"／"775_TO_773"／
    "774_TO_775"／"775_TO_774"。
    maps_a_to_774 は A→774 の表（763／764／765／766／767／768／769／
    770／771／772／773 は emit_composed_774 で作った合成表、774 は恒等写像）、
    maps_774_to_a は 774→A の表、vv_a は A 側の ViaVersion mapping、
    maps775 は 774→775 の表、vv775 は 775 側の mapping、vv774 は
    774 側の mapping。direction が "up"（A→775）の場合：A→774→775。
    "down"（775→A）の場合：775→774（反転）→A。アイテムだけは名前から
    作った逆変換表（maps775 の "reverse_items"）で合成する。戻り値は
    合成表の dict（kind → {source id: target id}）。
    """
    kinds = ["blockstates", "items", "entities", "sounds",
             "particles", "blocks", "menus"]
    java_names = {"blockstates": "BLOCK_STATE", "items": "ITEM",
                  "entities": "ENTITY_TYPE", "sounds": "SOUND",
                  "particles": "PARTICLE", "blocks": "BLOCK", "menus": "MENU"}
    # 代替先は名前から引く（775 の block.stone.break は 774 の 1535
    # から +67 ずれて 1535 になる、といったずれを手計算しないため）。
    def fallbacks(vv):
        return {"blockstates": 1, "items": 1,
                "entities": vv["entities"].index("pig"),
                "sounds": vv["sounds"].index("block.stone.break"),
                "particles": vv["particles"].index("entity_effect"),
                "blocks": 1,
                "menus": vv["menus"].index("crafting")}
    fb775 = fallbacks(vv775)
    fb774 = fallbacks(vv774)
    composed = {}
    for kind in kinds:
        if kind == "items":
            if direction == "up":
                first = maps_a_to_774[kind]
                second = maps775[kind]
                table = compose_maps(first, second, fb775[kind])
                size = len(vv_a[kind])
            else:
                first = maps775["reverse_items"]
                second = maps_774_to_a[kind]
                table = compose_maps(first, second, 1)
                size = len(vv775[kind])
        elif direction == "up":
            first = maps_a_to_774[kind]
            second = maps775[kind]
            table = compose_maps(first, second, fb775[kind])
            size = len(vv_a[kind])
        else:
            first_list = up775_map(maps775, kind, vv775)
            second_list = [maps_774_to_a[kind].get(i, fb774[kind])
                           for i in range(len(vv774[kind]))]
            table = {i: v for i, v in enumerate(
                compose_list(first_list, second_list, fb774[kind]))}
            size = len(vv775[kind])
        iv, ov = compress(table, size)
        name = java_names[kind] + "_" + tag
        out.append(java_int3(name + "_RANGES", iv))
        out.append(java_int2(name + "_OVERRIDES", ov))
        out.append(f"    // {name}: entries={size} ranges={len(iv)} overrides={len(ov)}")
        composed[kind] = table
    return composed


def emit_composed_767(out, tag, maps_a_to_766, maps_766_to_a, vv_a,
                      maps767, vv767, vv766, direction):
    """766→767 の合成で作る、離れた組み合わせ（763↔767、764↔767、
    765↔767）の合成表を Java の表として出す。1.20系相互連携の
    emit_composed と同じ方針で、実行時は合成済みの1つの表を直接引く
    （二段変換はしない）。

    tag は "763_TO_767"／"767_TO_763"／"764_TO_767"／"767_TO_764"／
    "765_TO_767"／"767_TO_765"。maps_a_to_766 は A→766 の表
    （kind → {A id: 766 id}。763／764 は emit_composed で作った
    合成表、765 は build_pair の 765→766、766 は恒等写像）、
    maps_766_to_a は 766→A の表、vv_a は A 側の ViaVersion mapping、
    maps767 は 766→767 の表、vv767 は 767 側の mapping、vv766 は
    766 側の mapping。direction が "up"（A→767）の場合：A→766→767。
    "down"（767→A）の場合：767→766（反転）→A。アイテムだけは
    名前から作った逆変換表（maps767 の "reverse_items"）で合成する。
    戻り値は合成表の dict（kind → {source id: target id}）。
    """
    kinds = ["blockstates", "items", "entities", "sounds",
             "particles", "blocks", "menus"]
    java_names = {"blockstates": "BLOCK_STATE", "items": "ITEM",
                  "entities": "ENTITY_TYPE", "sounds": "SOUND",
                  "particles": "PARTICLE", "blocks": "BLOCK", "menus": "MENU"}
    # 代替先は名前から引く（767 の block.stone.break は 766 の 1358
    # から +3 ずれて 1361 になる、といったずれを手計算しないため）。
    def fallbacks(vv):
        return {"blockstates": 1, "items": 1,
                "entities": vv["entities"].index("pig"),
                "sounds": vv["sounds"].index("block.stone.break"),
                "particles": vv["particles"].index("entity_effect"),
                "blocks": 1,
                "menus": vv["menus"].index("crafting")}
    fb767 = fallbacks(vv767)
    fb766 = fallbacks(vv766)
    composed = {}
    for kind in kinds:
        if kind == "items":
            if direction == "up":
                first = maps_a_to_766[kind]
                second = maps767[kind]
                table = compose_maps(first, second, fb767[kind])
                size = len(vv_a[kind])
            else:
                first = maps767["reverse_items"]
                second = maps_766_to_a[kind]
                table = compose_maps(first, second, 1)
                size = len(vv767[kind])
        elif direction == "up":
            first = maps_a_to_766[kind]
            second = maps767[kind]
            table = compose_maps(first, second, fb767[kind])
            size = len(vv_a[kind])
        else:
            first_list = up767_map(maps767, kind, vv767)
            second_list = [maps_766_to_a[kind].get(i, fb766[kind])
                           for i in range(len(vv766[kind]))]
            table = {i: v for i, v in enumerate(
                compose_list(first_list, second_list, fb766[kind]))}
            size = len(vv767[kind])
        iv, ov = compress(table, size)
        name = java_names[kind] + "_" + tag
        out.append(java_int3(name + "_RANGES", iv))
        out.append(java_int2(name + "_OVERRIDES", ov))
        out.append(f"    // {name}: entries={size} ranges={len(iv)} overrides={len(ov)}")
        composed[kind] = table
    return composed


def emit_composed_768(out, tag, maps_a_to_767, maps_767_to_a, vv_a,
                      maps768, vv768, vv767, direction):
    """767→768 の合成で作る、離れた組み合わせ（763↔768、764↔768、
    765↔768、766↔768）の合成表を Java の表として出す。emit_composed_767
    と同じ方針で、中間を 767 に置き換えたもの。実行時は合成済みの
    1つの表を直接引く（二段変換はしない）。

    tag は "763_TO_768"／"768_TO_763"／"764_TO_768"／"768_TO_764"／
    "765_TO_768"／"768_TO_765"／"766_TO_768"／"768_TO_766"。
    maps_a_to_767 は A→767 の表（763／764／765 は emit_composed_767
    で作った合成表、766 は build_pair の 766→767、767 は恒等写像）、
    maps_767_to_a は 767→A の表、vv_a は A 側の ViaVersion mapping、
    maps768 は 767→768 の表、vv768 は 768 側の mapping、vv767 は
    767 側の mapping。direction が "up"（A→768）の場合：A→767→768。
    "down"（768→A）の場合：768→767（反転）→A。アイテムだけは
    名前から作った逆変換表（maps768 の "reverse_items"）で合成する。
    戻り値は合成表の dict（kind → {source id: target id}）。
    """
    kinds = ["blockstates", "items", "entities", "sounds",
             "particles", "blocks", "menus"]
    java_names = {"blockstates": "BLOCK_STATE", "items": "ITEM",
                  "entities": "ENTITY_TYPE", "sounds": "SOUND",
                  "particles": "PARTICLE", "blocks": "BLOCK", "menus": "MENU"}
    # 代替先は名前から引く（768 の block.stone.break は 767 の 1361
    # から +25 ずれて 1386 になる、といったずれを手計算しないため）。
    def fallbacks(vv):
        return {"blockstates": 1, "items": 1,
                "entities": vv["entities"].index("pig"),
                "sounds": vv["sounds"].index("block.stone.break"),
                "particles": vv["particles"].index("entity_effect"),
                "blocks": 1,
                "menus": vv["menus"].index("crafting")}
    fb768 = fallbacks(vv768)
    fb767 = fallbacks(vv767)
    composed = {}
    for kind in kinds:
        if kind == "items":
            if direction == "up":
                first = maps_a_to_767[kind]
                second = maps768[kind]
                table = compose_maps(first, second, fb768[kind])
                size = len(vv_a[kind])
            else:
                first = maps768["reverse_items"]
                second = maps_767_to_a[kind]
                table = compose_maps(first, second, 1)
                size = len(vv768[kind])
        elif direction == "up":
            first = maps_a_to_767[kind]
            second = maps768[kind]
            table = compose_maps(first, second, fb768[kind])
            size = len(vv_a[kind])
        else:
            first_list = up768_map(maps768, kind, vv768)
            second_list = [maps_767_to_a[kind].get(i, fb767[kind])
                           for i in range(len(vv767[kind]))]
            table = {i: v for i, v in enumerate(
                compose_list(first_list, second_list, fb767[kind]))}
            size = len(vv768[kind])
        iv, ov = compress(table, size)
        name = java_names[kind] + "_" + tag
        out.append(java_int3(name + "_RANGES", iv))
        out.append(java_int2(name + "_OVERRIDES", ov))
        out.append(f"    // {name}: entries={size} ranges={len(iv)} overrides={len(ov)}")
        composed[kind] = table
    return composed


def sound_name_table_mid_to_target(maps_mid_forward, maps_mid_to_target,
                                   vv_mid, vv_target, up_corrections, fallback):
    """mid（767）の音の名前 → target（763／764／765）の音の名前の表を作る。

    数値の表と一致させる：mid の音 → mid→766 の反転（補正つき。
    fallback は 766 の石の破壊音=1358）→ 766→target の数値の表 →
    target の音の名前。名前が同じものは表に入れない（そのまま通す）。
    mid の新音は数値の表の代替先（石の破壊音など）の名前になる。
    sound_name_table_766_to_target の mid=766 版。
    """
    forward = maps_mid_forward["sounds"]  # {766 id: mid id}
    ranges, overrides = compress(forward, len(forward))
    up = invert_java(ranges, overrides, len(forward),
                     len(vv_mid["sounds"]), fallback, up_corrections)
    table = {}
    for id_mid, name_mid in enumerate(vv_mid["sounds"]):
        id766 = up[id_mid]
        id_target = maps_mid_to_target["sounds"][id766]
        name_target = vv_target["sounds"][id_target]
        if name_target != name_mid:
            table[name_mid] = name_target
    return table


def attribute_names(protocol):
    """protocol.json の Update Attributes の key mapper から、属性の
    名前を ID 順に並べた list を返す。"""
    pkt = protocol["play"]["toClient"]["types"]["packet_entity_update_attributes"]
    # ["container", [{"name": "entityId", ...}, {"name": "properties",
    #   "type": ["array", {"countType": "varint", "type": ["container",
    #   [{"name": "key", "type": ["mapper", {"type": "varint",
    #   "mappings": {"0": name, ...}}]}, ...]}]]}]
    props = pkt[1][1]["type"][1]["type"][1]
    mappings = props[0]["type"][1]["mappings"]
    return [mappings[str(i)] for i in range(len(mappings))]


def java_string_array(name, values):
    """String[] NAME = {"a", "b", ...}; の Java 宣言を作る。"""
    lines = [f"    private static final String[] {name} = {{"]
    for chunk in [values[i:i + 4] for i in range(0, len(values), 4)]:
        lines.append("            " + ", ".join('"%s"' % v for v in chunk) + ",")
    lines.append("    };")
    return "\n".join(lines)


def emit_composed(out, tag, maps_a, vv_a, maps766, vv766, vv765, direction):
    """離れた組み合わせの合成表を Java の表として出す。

    tag は "763_TO_766"／"766_TO_763"／"764_TO_766"／"766_TO_764"。
    maps_a は A 側（763／764）の 765→A の表、vv_a は A 側の
    ViaVersion mapping、maps766 は 765→766 の表、vv766 は 766 側の
    mapping、vv765 は 765 側の mapping。direction が "up"（A→766）の
    場合：A→765（逆引き。同名を優先、無ければ初出）→766。direction が
    "down"（766→A）の場合：
    766→765（反転＋補正）→A。アイテムだけは名前から作った
    逆変換表（maps_a／maps766 の "reverse_items"）で合成する。
    実行時は合成済みの1つの表を直接引くため、ここで1つの表にする。
    戻り値は合成表の dict（kind → {source id: target id}）。
    """
    kinds = ["blockstates", "items", "entities", "sounds",
             "particles", "blocks", "menus"]
    java_names = {"blockstates": "BLOCK_STATE", "items": "ITEM",
                  "entities": "ENTITY_TYPE", "sounds": "SOUND",
                  "particles": "PARTICLE", "blocks": "BLOCK", "menus": "MENU"}
    # 766 側の代替先（766 の石・ブタ・石の破壊音・entity_effect・作業台）。
    fb766 = {"blockstates": 1, "items": 1, "entities": 77, "sounds": 1358,
             "particles": 20, "blocks": 1, "menus": 12}
    # 765 側の代替先（766→765 の反転の代替先と同じ）。
    fb765 = {"blockstates": 1, "items": 1, "entities": 73, "sounds": 1312,
             "particles": 0, "blocks": 1, "menus": 12}
    composed = {}
    for kind in kinds:
        if kind == "items":
            first = (maps_a["reverse_items"] if direction == "up"
                     else maps766["reverse_items"])
            second = maps766[kind] if direction == "up" else maps_a[kind]
            table = compose_maps(first, second, 1)
            size = len(vv_a[kind]) if direction == "up" else len(vv766[kind])
        elif direction == "up":
            # A→765 は 765→A の逆引き（同名を優先、次に落とした
            # プロパティが既定値のもの、無ければ初出を採用）。
            first_list = build_inverse(
                maps_a[kind], len(maps_a[kind]),
                len(vv_a[kind]), vv765[kind], vv_a[kind],
                relaxed_props=({"powered": "false", "waterlogged": "false"}
                               if kind == "blockstates" else None))
            second_list = [maps766[kind].get(i, fb766[kind])
                           for i in range(len(maps766[kind]))]
            table = {i: v for i, v in enumerate(
                compose_list(first_list, second_list, fb766[kind]))}
            size = len(vv_a[kind])
        else:
            first_list = up766_map(maps766, kind, vv766)
            second_list = [maps_a[kind].get(i, fb765[kind])
                           for i in range(len(maps_a[kind]))]
            table = {i: v for i, v in enumerate(
                compose_list(first_list, second_list, fb765[kind]))}
            size = len(vv766[kind])
        iv, ov = compress(table, size)
        name = java_names[kind] + "_" + tag
        out.append(java_int3(name + "_RANGES", iv))
        out.append(java_int2(name + "_OVERRIDES", ov))
        out.append(f"    // {name}: entries={size} ranges={len(iv)} overrides={len(ov)}")
        composed[kind] = table
    return composed


def sound_name_table_766_to_target(maps766, maps_target, vv766, vv_target,
                                   sound_up_corrections):
    """766 の音の名前 → target（763／764）の音の名前の表を作る。

    数値の表と一致させる：766 の音 → 766→765 の反転（補正つき。
    fallback は 765 の石の破壊音=1312）→ 765→target の数値の表 →
    target の音の名前。名前が同じものは表に入れない（そのまま通す）。
    766 の新音は数値の表の代替先（石の破壊音など）の名前になる。
    """
    forward = maps766["sounds"]  # {765 id: 766 id}
    ranges, overrides = compress(forward, len(forward))
    up766 = invert_java(ranges, overrides, len(forward),
                        len(vv766["sounds"]), 1312, sound_up_corrections)
    table = {}
    for id766, name766 in enumerate(vv766["sounds"]):
        id765 = up766[id766]
        id_target = maps_target["sounds"][id765]
        name_target = vv_target["sounds"][id_target]
        if name_target != name766:
            table[name766] = name_target
    return table


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--data-dir", type=Path, default=None,
                        help="ダウンロードした JSON の置き場（既定: detergentmc-protocol/build/remap-data）")
    parser.add_argument("--out", type=Path, default=None,
                        help="Java の表の出力先（既定: 標準出力）")
    parser.add_argument("--target", choices=["763", "764", "766", "767", "768", "769", "770", "771", "772", "773", "774", "775", "both"], default="both",
                        help="生成する対応表（既定: 両方。766 は 763／764 の表も"
                             "内部で作るが、出力に含まれるのは 766 の表だけ。"
                             "767 は 763／764／765→766 の表も内部で作るが、"
                             "出力に含まれるのは 767 の表だけ。768 は 763／764／"
                             "765→767 の表も内部で作るが、出力に含まれるのは "
                             "768 の表だけ）")
    args = parser.parse_args()

    script_dir = Path(__file__).resolve().parent
    repo_root = script_dir.parent.parent
    data_dir = args.data_dir or repo_root / "detergentmc-protocol" / "build" / "remap-data"
    data_dir.mkdir(parents=True, exist_ok=True)
    for name, url in FILES.items():
        dest = data_dir / name
        if not dest.exists():
            download(url, dest)

    b = load(data_dir / "vv-1.20.3.json")    # 765

    failures = []
    out = []
    out.append("    // Generated by tools/generate-remap-tables.py. Do not edit by hand.")

    maps763 = maps764 = maps766 = None
    if args.target in ("763", "both", "766", "767", "768", "769", "770"):
        a763 = load(data_dir / "vv-1.20.json")      # 763
        blocks_120 = load(data_dir / "1.20_blocks.json")
        maps763, out763, fail763, rev763 = build_pair("763", a763, b, blocks_120, data_dir)
        failures += fail763
        if args.target in ("763", "both"):
            out += out763
            with open(data_dir / "maps.json", "w", encoding="utf-8") as f:
                json.dump(maps763, f)
            with open(data_dir / "map_items_rev.json", "w", encoding="utf-8") as f:
                json.dump({str(k): v for k, v in rev763.items()}, f)

    if args.target in ("764", "both", "766", "767", "768", "769", "770"):
        a764 = load(data_dir / "vv-1.20.2.json")    # 764
        blocks_1202 = load(data_dir / "1.20.2_blocks.json")
        maps764, out764, fail764, rev764 = build_pair("764", a764, b, blocks_1202, data_dir)
        failures += fail764
        if args.target in ("764", "both"):
            out += out764
            with open(data_dir / "maps764.json", "w", encoding="utf-8") as f:
                json.dump(maps764, f)
            with open(data_dir / "map_items_rev764.json", "w", encoding="utf-8") as f:
                json.dump({str(k): v for k, v in rev764.items()}, f)

    if args.target == "766":
        a766 = load(data_dir / "vv-1.20.5.json")    # 766
        blocks_1205 = load(data_dir / "1.20.5_blocks.json")
        maps766, out766, fail766, rev766 = build_pair(
            "766", a766, b, blocks_1205, data_dir,
            rename=RENAME_766, sound_fallbacks=SOUND_FALLBACK_766,
            particle_fallbacks=PARTICLE_FALLBACK_766,
            reverse_fallback=1, add_props=ADD_PROPS_766)
        failures += fail766
        out += out766
        with open(data_dir / "maps766.json", "w", encoding="utf-8") as f:
            json.dump(maps766, f)
        with open(data_dir / "map_items_rev766.json", "w", encoding="utf-8") as f:
            json.dump({str(k): v for k, v in rev766.items()}, f)

        # 765→766 の音の名前の表（Stop Sound 用。763／764 用の表と同じ形）。
        lines = ["    private static final String[][] SOUND_NAME_FALLBACKS_766 = {"]
        for k, v in sorted(SOUND_FALLBACK_766.items()):
            lines.append(f'            {{"{k}", "{v}"}},')
        lines.append("    };")
        out.append("\n".join(lines))
        # 766→763／766→764 の音の名前の表。数値の表と一致させる
        # （766→765 の反転＋765→763／764）。名前が同じものは含めない。
        for tag, maps_target, vv_target in (
                ("763", maps763, a763), ("764", maps764, a764)):
            table = sound_name_table_766_to_target(
                maps766, maps_target, a766, vv_target, SOUND_UP_766_CORRECTIONS)
            lines = [f"    private static final String[][] SOUND_NAME_FALLBACKS_766_TO_{tag} = {{"]
            for k, v in sorted(table.items()):
                lines.append(f'            {{"{k}", "{v}"}},')
            lines.append("    };")
            out.append("\n".join(lines))
            print(f"[766] sound name table 766->{tag}: {len(table)} entries")
        # 766→765 の音の名前の表。766の新音は 765の名前（補正つきの
        # 反転で決まる代替先。例：wind_charge.throw → generic.wind_burst、
        # breeze.charge → breeze.inhale、それ以外は石の破壊音）にする。
        # 名前が同じものは含めない。
        up766_sounds = up766_map(maps766, "sounds", a766)
        table765 = {}
        for id766, name766 in enumerate(a766["sounds"]):
            name765 = b["sounds"][up766_sounds[id766]]
            if name765 != name766:
                table765[name766] = name765
        lines = ["    private static final String[][] SOUND_NAME_FALLBACKS_766_TO_765 = {"]
        for k, v in sorted(table765.items()):
            lines.append(f'            {{"{k}", "{v}"}},')
        lines.append("    };")
        out.append("\n".join(lines))
        print(f"[766] sound name table 766->765: {len(table765)} entries")

        # 離れた組み合わせの合成表（763↔766、764↔766）。実行時は
        # 合成済みの1つの表を直接引く。検証用の JSON も残す。
        composed_all = {}
        for tag, maps_a, vv_a, direction in (
                ("763_TO_766", maps763, a763, "up"),
                ("766_TO_763", maps763, a763, "down"),
                ("764_TO_766", maps764, a764, "up"),
                ("766_TO_764", maps764, a764, "down")):
            composed_all[tag] = emit_composed(
                out, tag, maps_a, vv_a, maps766, a766, b, direction)
            print(f"[766] composed {tag}: "
                  + ", ".join(f"{k}={len(v)}" for k, v in composed_all[tag].items()))
        with open(data_dir / "maps_composed_766.json", "w", encoding="utf-8") as f:
            json.dump({tag: {kind: {str(k): v for k, v in table.items()}
                             for kind, table in comp.items()}
                       for tag, comp in composed_all.items()}, f)

    if args.target == "767":
        a766 = load(data_dir / "vv-1.20.5.json")    # 766
        blocks_1205 = load(data_dir / "1.20.5_blocks.json")
        maps766, _, fail766, rev766 = build_pair(
            "766", a766, b, blocks_1205, data_dir,
            rename=RENAME_766, sound_fallbacks=SOUND_FALLBACK_766,
            particle_fallbacks=PARTICLE_FALLBACK_766,
            reverse_fallback=1, add_props=ADD_PROPS_766)
        failures += fail766

        a767 = load(data_dir / "vv-1.21.json")      # 767
        blocks_1201 = load(data_dir / "1.21.1_blocks.json")
        maps767, out767, fail767, rev767 = build_pair(
            "767", a767, a766, blocks_1201, data_dir,
            rename=RENAME_767, sound_fallbacks=SOUND_FALLBACK_767,
            particle_fallbacks=PARTICLE_FALLBACK_767,
            reverse_fallback=1)
        failures += fail767
        out += out767
        with open(data_dir / "maps767.json", "w", encoding="utf-8") as f:
            json.dump(maps767, f)
        with open(data_dir / "map_items_rev767.json", "w", encoding="utf-8") as f:
            json.dump({str(k): v for k, v in rev767.items()}, f)

        # 766↔763／764 の合成表（763↔767／764↔767 の合成の材料。
        # Java の出力は捨て、dict だけ使う）。
        tmp = []
        comp_763_to_766 = emit_composed(
            tmp, "763_TO_766", maps763, a763, maps766, a766, b, "up")
        comp_766_to_763 = emit_composed(
            tmp, "766_TO_763", maps763, a763, maps766, a766, b, "down")
        comp_764_to_766 = emit_composed(
            tmp, "764_TO_766", maps764, a764, maps766, a766, b, "up")
        comp_766_to_764 = emit_composed(
            tmp, "766_TO_764", maps764, a764, maps766, a766, b, "down")

        # 766→765 の反転表（765↔767 の合成の材料。アイテムは
        # 逆変換表、他は invert＋補正＋代替先埋め）。
        up766 = {}
        for kind in ("blockstates", "entities", "sounds", "particles",
                     "blocks", "menus"):
            up766[kind] = {i: v for i, v in enumerate(
                up766_map(maps766, kind, a766))}
        up766["items"] = maps766["reverse_items"]

        kinds = ["blockstates", "items", "entities", "sounds",
                 "particles", "blocks", "menus"]
        identity766 = {kind: {i: i for i in range(len(a766[kind]))}
                       for kind in kinds}
        comp_765_to_766 = {kind: maps766[kind] for kind in kinds}
        comp_765_to_766["items"] = maps766["items"]
        # 765→766 は build_pair の dict そのもの（source id → target id）。

        # 767 の音の名前の表（Stop Sound 用）。
        # 765→767 と 766→767 は改名2件だけ。
        lines = ["    private static final String[][] SOUND_NAME_FALLBACKS_767 = {"]
        for k, v in sorted(SOUND_FALLBACK_767.items()):
            lines.append(f'            {{"{k}", "{v}"}},')
        lines.append("    };")
        out.append("\n".join(lines))
        lines = ["    private static final String[][] SOUND_NAME_FALLBACKS_766_TO_767 = {"]
        for k, v in sorted(SOUND_FALLBACK_767.items()):
            lines.append(f'            {{"{k}", "{v}"}},')
        lines.append("    };")
        out.append("\n".join(lines))
        # 767→766：改名の逆向きと、767の新音（音楽ディスク3件・
        # vault の拒否音）→ 石の破壊音。
        rev_rename_767 = {v: k for k, v in SOUND_FALLBACK_767.items()}
        table_767_to_766 = {}
        for name767 in a767["sounds"]:
            if name767 in rev_rename_767:
                table_767_to_766[name767] = rev_rename_767[name767]
            elif name767 not in set(a766["sounds"]):
                table_767_to_766[name767] = "block.stone.break"
        lines = ["    private static final String[][] SOUND_NAME_FALLBACKS_767_TO_766 = {"]
        for k, v in sorted(table_767_to_766.items()):
            lines.append(f'            {{"{k}", "{v}"}},')
        lines.append("    };")
        out.append("\n".join(lines))
        print(f"[767] sound name table 767->766: {len(table_767_to_766)} entries")
        # 767→765／763／764：数値の表と一致させる
        # （767→766 の反転＋766→target）。
        for tag, maps_766_to_a, vv_target in (
                ("765", up766, b),
                ("763", comp_766_to_763, a763),
                ("764", comp_766_to_764, a764)):
            table = sound_name_table_mid_to_target(
                maps767, {"sounds": maps_766_to_a["sounds"]}, a767,
                vv_target, UP_767_CORRECTIONS.get("sounds"), 1358)
            lines = [f"    private static final String[][] SOUND_NAME_FALLBACKS_767_TO_{tag} = {{"]
            for k, v in sorted(table.items()):
                lines.append(f'            {{"{k}", "{v}"}},')
            lines.append("    };")
            out.append("\n".join(lines))
            print(f"[767] sound name table 767->{tag}: {len(table)} entries")

        # 離れた組み合わせの合成表（763↔767、764↔767、765↔767）。
        composed_all = {}
        for tag, maps_a_to_766, maps_766_to_a, vv_a, direction in (
                ("763_TO_767", comp_763_to_766, comp_766_to_763, a763, "up"),
                ("767_TO_763", comp_763_to_766, comp_766_to_763, a763, "down"),
                ("764_TO_767", comp_764_to_766, comp_766_to_764, a764, "up"),
                ("767_TO_764", comp_764_to_766, comp_766_to_764, a764, "down"),
                ("765_TO_767", comp_765_to_766, up766, b, "up"),
                ("767_TO_765", comp_765_to_766, up766, b, "down")):
            composed_all[tag] = emit_composed_767(
                out, tag, maps_a_to_766, maps_766_to_a, vv_a,
                maps767, a767, a766, direction)
            print(f"[767] composed {tag}: "
                  + ", ".join(f"{k}={len(v)}" for k, v in composed_all[tag].items()))
        with open(data_dir / "maps_composed_767.json", "w", encoding="utf-8") as f:
            json.dump({tag: {kind: {str(k): v for k, v in table.items()}
                             for kind, table in comp.items()}
                       for tag, comp in composed_all.items()}, f)

        # 属性の表（protocol.json の Update Attributes の key mapper
        # から名前順で作る。766 は22件、767 は31件。新規9件は名前順に
        # 挿入されるため既存の番号がずれる。逆向き（767→766）は新規を
        # -1（DROP）にする）。
        proto766 = load(data_dir / "mc-1.20.5-protocol.json")
        proto767 = load(data_dir / "mc-1.21.1-protocol.json")
        attrs766 = attribute_names(proto766)
        attrs767 = attribute_names(proto767)
        attr_766_to_767 = {i: attrs767.index(n) for i, n in enumerate(attrs766)}
        attr_767_to_766 = {i: (attrs766.index(n) if n in attrs766 else -1)
                           for i, n in enumerate(attrs767)}
        iv, ov = compress(attr_766_to_767, len(attrs766))
        out.append(java_int3("ATTRIBUTE_767_RANGES", iv))
        out.append(java_int2("ATTRIBUTE_767_OVERRIDES", ov))
        out.append(f"    // ATTRIBUTE_767: 766 entries={len(attrs766)} "
                   f"ranges={len(iv)} overrides={len(ov)}")
        iv, ov = compress(attr_767_to_766, len(attrs767))
        out.append(java_int3("ATTRIBUTE_REVERSE_767_RANGES", iv))
        out.append(java_int2("ATTRIBUTE_REVERSE_767_OVERRIDES", ov))
        out.append(f"    // ATTRIBUTE_REVERSE_767: 767 entries={len(attrs767)} "
                   f"ranges={len(iv)} overrides={len(ov)}")
        out.append(java_string_array("ATTRIBUTE_766_NAMES", attrs766))
        out.append(java_string_array("ATTRIBUTE_767_NAMES", attrs767))
        print(f"[767] attributes: 766={len(attrs766)} 767={len(attrs767)} "
              f"new={[n for n in attrs767 if n not in attrs766]}")

        # エンチャントの表（minecraft-data の enchantments.json の
        # 名前順で作る。766 は登録順、767 は名前順に並んでいるため
        # 番号が大きくずれる。両方に42件あり、改名・増減は無い。
        # 実機の Registry Data での順序は統合テストで確認すること）。
        ench766 = [e["name"] for e in load(data_dir / "enchantments-1.20.5.json")]
        ench767 = [e["name"] for e in load(data_dir / "enchantments-1.21.1.json")]
        ench_766_to_767 = {i: ench767.index(n) for i, n in enumerate(ench766)}
        ench_767_to_766 = {i: ench766.index(n) for i, n in enumerate(ench767)}
        iv, ov = compress(ench_766_to_767, len(ench766))
        out.append(java_int3("ENCHANTMENT_767_RANGES", iv))
        out.append(java_int2("ENCHANTMENT_767_OVERRIDES", ov))
        out.append(f"    // ENCHANTMENT_767: 766 entries={len(ench766)} "
                   f"ranges={len(iv)} overrides={len(ov)}")
        iv, ov = compress(ench_767_to_766, len(ench767))
        out.append(java_int3("ENCHANTMENT_REVERSE_767_RANGES", iv))
        out.append(java_int2("ENCHANTMENT_REVERSE_767_OVERRIDES", ov))
        out.append(f"    // ENCHANTMENT_REVERSE_767: 767 entries={len(ench767)} "
                   f"ranges={len(iv)} overrides={len(ov)}")
        print(f"[767] enchantments: 766={len(ench766)} 767={len(ench767)} "
              f"moved={sum(1 for i, n in enumerate(ench766) if ench767.index(n) != i)}")

    if args.target == "768":
        a766 = load(data_dir / "vv-1.20.5.json")    # 766
        blocks_1205 = load(data_dir / "1.20.5_blocks.json")
        maps766, _, fail766, rev766 = build_pair(
            "766", a766, b, blocks_1205, data_dir,
            rename=RENAME_766, sound_fallbacks=SOUND_FALLBACK_766,
            particle_fallbacks=PARTICLE_FALLBACK_766,
            reverse_fallback=1, add_props=ADD_PROPS_766)
        failures += fail766

        a767 = load(data_dir / "vv-1.21.json")      # 767
        blocks_1201 = load(data_dir / "1.21.1_blocks.json")
        maps767, _, fail767, rev767 = build_pair(
            "767", a767, a766, blocks_1201, data_dir,
            rename=RENAME_767, sound_fallbacks=SOUND_FALLBACK_767,
            particle_fallbacks=PARTICLE_FALLBACK_767,
            reverse_fallback=1)
        failures += fail767

        a768 = load(data_dir / "vv-1.21.2.json")    # 768
        blocks_1213 = load(data_dir / "1.21.3_blocks.json")
        maps768, out768, fail768, rev768 = build_pair(
            "768", a768, a767, blocks_1213, data_dir,
            rename=RENAME_768, sound_fallbacks=SOUND_FALLBACK_768,
            particle_fallbacks=PARTICLE_FALLBACK_768,
            reverse_fallback=1, entity_fallbacks=ENTITY_FALLBACK_768)
        failures += fail768
        out += out768
        with open(data_dir / "maps768.json", "w", encoding="utf-8") as f:
            json.dump(maps768, f)
        with open(data_dir / "map_items_rev768.json", "w", encoding="utf-8") as f:
            json.dump({str(k): v for k, v in rev768.items()}, f)

        # 768→767 の反転の補正：768 で木材ごとに分かれた boat／raft は
        # 767 の boat／chest_boat へ（767 の boat はメタデータの variant
        # で木材を選ぶ）。oak の2件は invert の先行勝ちで既に正しい
        # ため補正から外す。合成の下向き表（768→763／764／765／766）
        # と Java の *_UP_768_MAP の両方で使う。
        boat = a767["entities"].index("boat")
        chest_boat = a767["entities"].index("chest_boat")
        entity_up_768_corrections = []
        for i, name in enumerate(a768["entities"]):
            if name in ("oak_boat", "oak_chest_boat"):
                continue
            if name.endswith("_chest_boat") or name.endswith("_chest_raft"):
                entity_up_768_corrections.append((i, chest_boat))
            elif name.endswith("_boat") or name.endswith("_raft"):
                entity_up_768_corrections.append((i, boat))
        UP_768_CORRECTIONS["entities"] = entity_up_768_corrections
        lines = ["    private static final int[][] ENTITY_UP_768_CORRECTIONS = {"]
        for y, x in entity_up_768_corrections:
            lines.append(f"            {{{y}, {x}}},")
        lines.append("    };")
        out.append("\n".join(lines))
        print(f"[768] ENTITY_UP_768_CORRECTIONS: "
              f"{len(entity_up_768_corrections)} entries")

        # 766↔763／764 の合成表（763↔767／764↔767 の合成の材料。
        # Java の出力は捨て、dict だけ使う）。
        tmp = []
        comp_763_to_766 = emit_composed(
            tmp, "763_TO_766", maps763, a763, maps766, a766, b, "up")
        comp_766_to_763 = emit_composed(
            tmp, "766_TO_763", maps763, a763, maps766, a766, b, "down")
        comp_764_to_766 = emit_composed(
            tmp, "764_TO_766", maps764, a764, maps766, a766, b, "up")
        comp_766_to_764 = emit_composed(
            tmp, "766_TO_764", maps764, a764, maps766, a766, b, "down")

        # 766→765 の反転表（765↔767 の合成の材料。アイテムは
        # 逆変換表、他は invert＋補正＋代替先埋め）。
        up766 = {}
        for kind in ("blockstates", "entities", "sounds", "particles",
                     "blocks", "menus"):
            up766[kind] = {i: v for i, v in enumerate(
                up766_map(maps766, kind, a766))}
        up766["items"] = maps766["reverse_items"]

        kinds = ["blockstates", "items", "entities", "sounds",
                 "particles", "blocks", "menus"]
        identity766 = {kind: {i: i for i in range(len(a766[kind]))}
                       for kind in kinds}
        comp_765_to_766 = {kind: maps766[kind] for kind in kinds}
        comp_765_to_766["items"] = maps766["items"]

        # 763／764／765↔767 の合成表（763↔768／764↔768／765↔768 の
        # 合成の材料。Java の出力は捨て、dict だけ使う）。
        tmp767 = []
        comp_763_to_767 = emit_composed_767(
            tmp767, "763_TO_767", comp_763_to_766, comp_766_to_763, a763,
            maps767, a767, a766, "up")
        comp_767_to_763 = emit_composed_767(
            tmp767, "767_TO_763", comp_763_to_766, comp_766_to_763, a763,
            maps767, a767, a766, "down")
        comp_764_to_767 = emit_composed_767(
            tmp767, "764_TO_767", comp_764_to_766, comp_766_to_764, a764,
            maps767, a767, a766, "up")
        comp_767_to_764 = emit_composed_767(
            tmp767, "767_TO_764", comp_764_to_766, comp_766_to_764, a764,
            maps767, a767, a766, "down")
        comp_765_to_767 = emit_composed_767(
            tmp767, "765_TO_767", comp_765_to_766, up766, b,
            maps767, a767, a766, "up")
        comp_767_to_765 = emit_composed_767(
            tmp767, "767_TO_765", comp_765_to_766, up766, b,
            maps767, a767, a766, "down")

        # 767→766 の反転表（766↔768 の合成の材料。アイテムは
        # 逆変換表、他は invert＋代替先埋め）。
        up767 = {}
        for kind in ("blockstates", "entities", "sounds", "particles",
                     "blocks", "menus"):
            up767[kind] = {i: v for i, v in enumerate(
                up767_map(maps767, kind, a767))}
        up767["items"] = maps767["reverse_items"]

        identity767 = {kind: {i: i for i in range(len(a767[kind]))}
                       for kind in kinds}

        # 768 の音の名前の表（Stop Sound 用）。
        # 767→768 と 768→767 は削除されたヤギの角笛の音2件だけ。
        lines = ["    private static final String[][] SOUND_NAME_FALLBACKS_768 = {"]
        for k, v in sorted(SOUND_FALLBACK_768.items()):
            lines.append(f'            {{"{k}", "{v}"}},')
        lines.append("    };")
        out.append("\n".join(lines))
        lines = ["    private static final String[][] SOUND_NAME_FALLBACKS_767_TO_768 = {"]
        for k, v in sorted(SOUND_FALLBACK_768.items()):
            lines.append(f'            {{"{k}", "{v}"}},')
        lines.append("    };")
        out.append("\n".join(lines))
        # 768→767：768の新音（クリエイキング・ペールモス・スポナー音など
        # 25件）→ 石の破壊音。改名は無い（削除された2件は768側に無い）。
        table_768_to_767 = {}
        for name768 in a768["sounds"]:
            if name768 not in set(a767["sounds"]):
                table_768_to_767[name768] = "block.stone.break"
        lines = ["    private static final String[][] SOUND_NAME_FALLBACKS_768_TO_767 = {"]
        for k, v in sorted(table_768_to_767.items()):
            lines.append(f'            {{"{k}", "{v}"}},')
        lines.append("    };")
        out.append("\n".join(lines))
        print(f"[768] sound name table 768->767: {len(table_768_to_767)} entries")
        # 768→765／763／764：数値の表と一致させる
        # （768→767 の反転＋767→target）。
        for tag, maps_767_to_a, vv_target in (
                ("765", comp_767_to_765, b),
                ("763", comp_767_to_763, a763),
                ("764", comp_767_to_764, a764)):
            table = sound_name_table_mid_to_target(
                maps768, {"sounds": maps_767_to_a["sounds"]}, a768,
                vv_target, UP_768_CORRECTIONS.get("sounds"), 1361)
            lines = [f"    private static final String[][] SOUND_NAME_FALLBACKS_768_TO_{tag} = {{"]
            for k, v in sorted(table.items()):
                lines.append(f'            {{"{k}", "{v}"}},')
            lines.append("    };")
            out.append("\n".join(lines))
            print(f"[768] sound name table 768->{tag}: {len(table)} entries")
        # 768→766：数値の表と一致させる（768→767 の反転＋767→766 の反転）。
        table_768_to_766 = sound_name_table_mid_to_target(
            maps768, {"sounds": up767["sounds"]}, a768,
            a766, UP_768_CORRECTIONS.get("sounds"), 1361)
        lines = ["    private static final String[][] SOUND_NAME_FALLBACKS_768_TO_766 = {"]
        for k, v in sorted(table_768_to_766.items()):
            lines.append(f'            {{"{k}", "{v}"}},')
        lines.append("    };")
        out.append("\n".join(lines))
        print(f"[768] sound name table 768->766: {len(table_768_to_766)} entries")
        # 766→768：766→767 の改名（trial_spawner の音2件）と 767→768
        # の代替（ヤギの角笛の音2件）の合成。両方の改名・代替の対象は
        # 重ならないため和集合でよい。
        table_766_to_768 = dict(SOUND_FALLBACK_767)
        table_766_to_768.update(SOUND_FALLBACK_768)
        lines = ["    private static final String[][] SOUND_NAME_FALLBACKS_766_TO_768 = {"]
        for k, v in sorted(table_766_to_768.items()):
            lines.append(f'            {{"{k}", "{v}"}},')
        lines.append("    };")
        out.append("\n".join(lines))
        print(f"[768] sound name table 766->768: {len(table_766_to_768)} entries")

        # 離れた組み合わせの合成表（763↔768、764↔768、765↔768、766↔768）。
        composed_all = {}
        for tag, maps_a_to_767, maps_767_to_a, vv_a, direction in (
                ("763_TO_768", comp_763_to_767, comp_767_to_763, a763, "up"),
                ("768_TO_763", comp_763_to_767, comp_767_to_763, a763, "down"),
                ("764_TO_768", comp_764_to_767, comp_767_to_764, a764, "up"),
                ("768_TO_764", comp_764_to_767, comp_767_to_764, a764, "down"),
                ("765_TO_768", comp_765_to_767, comp_767_to_765, b, "up"),
                ("768_TO_765", comp_765_to_767, comp_767_to_765, b, "down"),
                ("766_TO_768", maps767, up767, a766, "up"),
                ("768_TO_766", maps767, up767, a766, "down")):
            composed_all[tag] = emit_composed_768(
                out, tag, maps_a_to_767, maps_767_to_a, vv_a,
                maps768, a768, a767, direction)
            print(f"[768] composed {tag}: "
                  + ", ".join(f"{k}={len(v)}" for k, v in composed_all[tag].items()))
        with open(data_dir / "maps_composed_768.json", "w", encoding="utf-8") as f:
            json.dump({tag: {kind: {str(k): v for k, v in table.items()}
                             for kind, table in comp.items()}
                       for tag, comp in composed_all.items()}, f)

        # 属性の表（protocol.json の Update Attributes の key mapper
        # から名前順で作る。767 は31件、768 は32件。改名9件は
        # ATTRIBUTE_RENAME_768 で写し、新規1件（tempt_range）は
        # 逆向きで -1（DROP）にする）。
        proto767 = load(data_dir / "mc-1.21.1-protocol.json")
        proto768 = load(data_dir / "mc-1.21.3-protocol.json")
        attrs767 = attribute_names(proto767)
        attrs768 = attribute_names(proto768)
        renamed = {n for n in attrs767 if n not in attrs768
                   and ATTRIBUTE_RENAME_768.get(n) not in attrs768}
        if renamed:
            print(f"[768] attributes: unexpected renames: {sorted(renamed)}",
                  file=sys.stderr)
            failures.append(("attributes-rename", sorted(renamed)))
        attr_767_to_768 = {}
        for i, n in enumerate(attrs767):
            target = ATTRIBUTE_RENAME_768.get(n, n)
            attr_767_to_768[i] = attrs768.index(target) if target in attrs768 else -1
        attr_768_to_767 = {}
        for i, n in enumerate(attrs768):
            src = None
            for k, v in ATTRIBUTE_RENAME_768.items():
                if v == n:
                    src = k
                    break
            if src is None:
                src = n
            attr_768_to_767[i] = attrs767.index(src) if src in attrs767 else -1
        iv, ov = compress(attr_767_to_768, len(attrs767))
        out.append(java_int3("ATTRIBUTE_768_RANGES", iv))
        out.append(java_int2("ATTRIBUTE_768_OVERRIDES", ov))
        out.append(f"    // ATTRIBUTE_768: 767 entries={len(attrs767)} "
                   f"ranges={len(iv)} overrides={len(ov)}")
        iv, ov = compress(attr_768_to_767, len(attrs768))
        out.append(java_int3("ATTRIBUTE_REVERSE_768_RANGES", iv))
        out.append(java_int2("ATTRIBUTE_REVERSE_768_OVERRIDES", ov))
        out.append(f"    // ATTRIBUTE_REVERSE_768: 768 entries={len(attrs768)} "
                   f"ranges={len(iv)} overrides={len(ov)}")
        out.append(java_string_array("ATTRIBUTE_767_NAMES", attrs767))
        out.append(java_string_array("ATTRIBUTE_768_NAMES", attrs768))
        print(f"[768] attributes: 767={len(attrs767)} 768={len(attrs768)} "
              f"renamed={sorted(ATTRIBUTE_RENAME_768.items())} "
              f"new={[n for n in attrs768 if n not in attrs767
                      and n not in ATTRIBUTE_RENAME_768.values()]}")

        # エンチャントの表（minecraft-data の dataPaths.json によると
        # 1.21.3 の enchantments は pc/1.21.1 を使うため、767 と同じ
        # 42件・同じ名前順で恒等写像。実機の Registry Data での順序は
        # 統合テストで確認すること）。
        ench767 = [e["name"] for e in load(data_dir / "enchantments-1.21.1.json")]
        ench768 = list(ench767)
        ench_767_to_768 = {i: i for i in range(len(ench767))}
        ench_768_to_767 = {i: i for i in range(len(ench768))}
        iv, ov = compress(ench_767_to_768, len(ench767))
        out.append(java_int3("ENCHANTMENT_768_RANGES", iv))
        out.append(java_int2("ENCHANTMENT_768_OVERRIDES", ov))
        out.append(f"    // ENCHANTMENT_768: 767 entries={len(ench767)} "
                   f"ranges={len(iv)} overrides={len(ov)}")
        iv, ov = compress(ench_768_to_767, len(ench768))
        out.append(java_int3("ENCHANTMENT_REVERSE_768_RANGES", iv))
        out.append(java_int2("ENCHANTMENT_REVERSE_768_OVERRIDES", ov))
        out.append(f"    // ENCHANTMENT_REVERSE_768: 768 entries={len(ench768)} "
                   f"ranges={len(iv)} overrides={len(ov)}")
        print(f"[768] enchantments: 767={len(ench767)} 768={len(ench768)} identity")

    if args.target == "769":
        a766 = load(data_dir / "vv-1.20.5.json")    # 766
        blocks_1205 = load(data_dir / "1.20.5_blocks.json")
        maps766, _, fail766, rev766 = build_pair(
            "766", a766, b, blocks_1205, data_dir,
            rename=RENAME_766, sound_fallbacks=SOUND_FALLBACK_766,
            particle_fallbacks=PARTICLE_FALLBACK_766,
            reverse_fallback=1, add_props=ADD_PROPS_766)
        failures += fail766

        a767 = load(data_dir / "vv-1.21.json")      # 767
        blocks_1201 = load(data_dir / "1.21.1_blocks.json")
        maps767, _, fail767, rev767 = build_pair(
            "767", a767, a766, blocks_1201, data_dir,
            rename=RENAME_767, sound_fallbacks=SOUND_FALLBACK_767,
            particle_fallbacks=PARTICLE_FALLBACK_767,
            reverse_fallback=1)
        failures += fail767

        a768 = load(data_dir / "vv-1.21.2.json")    # 768
        blocks_1213 = load(data_dir / "1.21.3_blocks.json")
        maps768, _, fail768, rev768 = build_pair(
            "768", a768, a767, blocks_1213, data_dir,
            rename=RENAME_768, sound_fallbacks=SOUND_FALLBACK_768,
            particle_fallbacks=PARTICLE_FALLBACK_768,
            reverse_fallback=1, entity_fallbacks=ENTITY_FALLBACK_768)
        failures += fail768

        a769 = load(data_dir / "vv-1.21.4.json")    # 769
        blocks_1214 = load(data_dir / "1.21.4_blocks.json")
        maps769, out769, fail769, rev769 = build_pair(
            "769", a769, a768, blocks_1214, data_dir,
            rename=RENAME_769, sound_fallbacks=SOUND_FALLBACK_769,
            particle_fallbacks=PARTICLE_FALLBACK_769,
            reverse_fallback=1, entity_fallbacks=ENTITY_FALLBACK_769)
        failures += fail769
        out += out769
        with open(data_dir / "maps769.json", "w", encoding="utf-8") as f:
            json.dump(maps769, f)
        with open(data_dir / "map_items_rev769.json", "w", encoding="utf-8") as f:
            json.dump({str(k): v for k, v in rev769.items()}, f)

        # 768→767 の反転の補正（768 処理と同じ。769 の合成で
        # up768_map を使うため、ここでも計算する）：768 で木材ごとに
        # 分かれた boat／raft は 767 の boat／chest_boat へ。oak の2件は
        # invert の先行勝ちで既に正しいため補正から外す。
        boat = a767["entities"].index("boat")
        chest_boat = a767["entities"].index("chest_boat")
        entity_up_768_corrections = []
        for i, name in enumerate(a768["entities"]):
            if name in ("oak_boat", "oak_chest_boat"):
                continue
            if name.endswith("_chest_boat") or name.endswith("_chest_raft"):
                entity_up_768_corrections.append((i, chest_boat))
            elif name.endswith("_boat") or name.endswith("_raft"):
                entity_up_768_corrections.append((i, boat))
        UP_768_CORRECTIONS["entities"] = entity_up_768_corrections
        print(f"[769] ENTITY_UP_768_CORRECTIONS: "
              f"{len(entity_up_768_corrections)} entries")

        # 769→768 の反転の補正：769 の natural=false・active=true の
        # creaking_heart（768 の forward には無い）は 768 の
        # creaking=active の心臓へ（invert だけだと石に落ちてしまう）。
        # natural=true の心臓は invert の先行勝ちで dormant／active が
        # 既に正しいため補正から外す。合成の下向き表（769→763／764／
        # 765／766／767）と Java の *_UP_769_MAP の両方で使う。
        block_up_769_corrections = []
        for i, name in enumerate(a769["blockstates"]):
            if not name.startswith("creaking_heart["):
                continue
            props = block_props(name)
            if props.get("natural") == "false" and props.get("active") == "true":
                target768 = ("creaking_heart[axis=%s,creaking=active]"
                             % props["axis"])
                block_up_769_corrections.append(
                    (i, a768["blockstates"].index(target768)))
        UP_769_CORRECTIONS["blockstates"] = block_up_769_corrections
        lines = ["    private static final int[][] BLOCK_STATE_UP_769_CORRECTIONS = {"]
        for y, x in block_up_769_corrections:
            lines.append(f"            {{{y}, {x}}},")
        lines.append("    };")
        out.append("\n".join(lines))
        print(f"[769] BLOCK_STATE_UP_769_CORRECTIONS: "
              f"{len(block_up_769_corrections)} entries")

        # 766↔763／764 の合成表（763↔767／764↔767 の合成の材料。
        # Java の出力は捨て、dict だけ使う）。
        tmp = []
        comp_763_to_766 = emit_composed(
            tmp, "763_TO_766", maps763, a763, maps766, a766, b, "up")
        comp_766_to_763 = emit_composed(
            tmp, "766_TO_763", maps763, a763, maps766, a766, b, "down")
        comp_764_to_766 = emit_composed(
            tmp, "764_TO_766", maps764, a764, maps766, a766, b, "up")
        comp_766_to_764 = emit_composed(
            tmp, "766_TO_764", maps764, a764, maps766, a766, b, "down")

        # 766→765 の反転表（765↔767 の合成の材料。アイテムは
        # 逆変換表、他は invert＋補正＋代替先埋め）。
        up766 = {}
        for kind in ("blockstates", "entities", "sounds", "particles",
                     "blocks", "menus"):
            up766[kind] = {i: v for i, v in enumerate(
                up766_map(maps766, kind, a766))}
        up766["items"] = maps766["reverse_items"]

        kinds = ["blockstates", "items", "entities", "sounds",
                 "particles", "blocks", "menus"]
        comp_765_to_766 = {kind: maps766[kind] for kind in kinds}
        comp_765_to_766["items"] = maps766["items"]

        # 763／764／765↔767 の合成表（763↔768／764↔768／765↔768 の
        # 合成の材料。Java の出力は捨て、dict だけ使う）。
        tmp767 = []
        comp_763_to_767 = emit_composed_767(
            tmp767, "763_TO_767", comp_763_to_766, comp_766_to_763, a763,
            maps767, a767, a766, "up")
        comp_767_to_763 = emit_composed_767(
            tmp767, "767_TO_763", comp_763_to_766, comp_766_to_763, a763,
            maps767, a767, a766, "down")
        comp_764_to_767 = emit_composed_767(
            tmp767, "764_TO_767", comp_764_to_766, comp_766_to_764, a764,
            maps767, a767, a766, "up")
        comp_767_to_764 = emit_composed_767(
            tmp767, "767_TO_764", comp_764_to_766, comp_766_to_764, a764,
            maps767, a767, a766, "down")
        comp_765_to_767 = emit_composed_767(
            tmp767, "765_TO_767", comp_765_to_766, up766, b,
            maps767, a767, a766, "up")
        comp_767_to_765 = emit_composed_767(
            tmp767, "767_TO_765", comp_765_to_766, up766, b,
            maps767, a767, a766, "down")

        # 767→766 の反転表（766↔768 の合成の材料。アイテムは
        # 逆変換表、他は invert＋代替先埋め）。
        up767 = {}
        for kind in ("blockstates", "entities", "sounds", "particles",
                     "blocks", "menus"):
            up767[kind] = {i: v for i, v in enumerate(
                up767_map(maps767, kind, a767))}
        up767["items"] = maps767["reverse_items"]

        identity767 = {kind: {i: i for i in range(len(a767[kind]))}
                       for kind in kinds}

        # 763／764／765／766↔768 の合成表（763↔769／764↔769／765↔769／
        # 766↔769 の合成の材料。Java の出力は捨て、dict だけ使う）。
        tmp768 = []
        comp_763_to_768 = emit_composed_768(
            tmp768, "763_TO_768", comp_763_to_767, comp_767_to_763, a763,
            maps768, a768, a767, "up")
        comp_768_to_763 = emit_composed_768(
            tmp768, "768_TO_763", comp_763_to_767, comp_767_to_763, a763,
            maps768, a768, a767, "down")
        comp_764_to_768 = emit_composed_768(
            tmp768, "764_TO_768", comp_764_to_767, comp_767_to_764, a764,
            maps768, a768, a767, "up")
        comp_768_to_764 = emit_composed_768(
            tmp768, "768_TO_764", comp_764_to_767, comp_767_to_764, a764,
            maps768, a768, a767, "down")
        comp_765_to_768 = emit_composed_768(
            tmp768, "765_TO_768", comp_765_to_767, comp_767_to_765, b,
            maps768, a768, a767, "up")
        comp_768_to_765 = emit_composed_768(
            tmp768, "768_TO_765", comp_765_to_767, comp_767_to_765, b,
            maps768, a768, a767, "down")
        comp_766_to_768 = emit_composed_768(
            tmp768, "766_TO_768", maps767, up767, a766,
            maps768, a768, a767, "up")
        comp_768_to_766 = emit_composed_768(
            tmp768, "768_TO_766", maps767, up767, a766,
            maps768, a768, a767, "down")

        # 768→767 の反転表（767↔769 の合成の材料。アイテムは
        # 逆変換表、他は invert＋補正＋代替先埋め）。
        up768 = {}
        for kind in ("blockstates", "entities", "sounds", "particles",
                     "blocks", "menus"):
            up768[kind] = {i: v for i, v in enumerate(
                up768_map(maps768, kind, a768))}
        up768["items"] = maps768["reverse_items"]

        identity768 = {kind: {i: i for i in range(len(a768[kind]))}
                       for kind in kinds}

        # 769 の音の名前の表（Stop Sound 用）。
        # 768→769 は改名なし（SOUND_FALLBACK_769 は空）。
        lines = ["    private static final String[][] SOUND_NAME_FALLBACKS_769 = {"]
        for k, v in sorted(SOUND_FALLBACK_769.items()):
            lines.append(f'            {{"{k}", "{v}"}},')
        lines.append("    };")
        out.append("\n".join(lines))
        lines = ["    private static final String[][] SOUND_NAME_FALLBACKS_768_TO_769 = {"]
        for k, v in sorted(SOUND_FALLBACK_769.items()):
            lines.append(f'            {{"{k}", "{v}"}},')
        lines.append("    };")
        out.append("\n".join(lines))
        # 769→768：769の新音（クリーキングの twitch、アイブロッサム6件、
        # レジン8件の15件）→ 石の破壊音。改名は無い。
        table_769_to_768 = {}
        for name769 in a769["sounds"]:
            if name769 not in set(a768["sounds"]):
                table_769_to_768[name769] = "block.stone.break"
        lines = ["    private static final String[][] SOUND_NAME_FALLBACKS_769_TO_768 = {"]
        for k, v in sorted(table_769_to_768.items()):
            lines.append(f'            {{"{k}", "{v}"}},')
        lines.append("    };")
        out.append("\n".join(lines))
        print(f"[769] sound name table 769->768: {len(table_769_to_768)} entries")
        # 769→767／766／765／764／763：数値の表と一致させる
        # （769→768 の反転＋768→target）。
        for tag, maps_768_to_a, vv_target in (
                ("767", up768, a767),
                ("766", comp_768_to_766, a766),
                ("765", comp_768_to_765, b),
                ("763", comp_768_to_763, a763),
                ("764", comp_768_to_764, a764)):
            table = sound_name_table_mid_to_target(
                maps769, {"sounds": maps_768_to_a["sounds"]}, a769,
                vv_target, UP_769_CORRECTIONS.get("sounds"), 1386)
            lines = [f"    private static final String[][] SOUND_NAME_FALLBACKS_769_TO_{tag} = {{"]
            for k, v in sorted(table.items()):
                lines.append(f'            {{"{k}", "{v}"}},')
            lines.append("    };")
            out.append("\n".join(lines))
            print(f"[769] sound name table 769->{tag}: {len(table)} entries")
        # 767→769：768で削除されたヤギの角笛の音2件だけ（SOUND_FALLBACK_768）。
        lines = ["    private static final String[][] SOUND_NAME_FALLBACKS_767_TO_769 = {"]
        for k, v in sorted(SOUND_FALLBACK_768.items()):
            lines.append(f'            {{"{k}", "{v}"}},')
        lines.append("    };")
        out.append("\n".join(lines))
        # 766→769：766→767 の改名（trial_spawner の音2件）と 767→769
        # の代替（ヤギの角笛の音2件）の合成。両方の改名・代替の対象は
        # 重ならないため和集合でよい。
        table_766_to_769 = dict(SOUND_FALLBACK_767)
        table_766_to_769.update(SOUND_FALLBACK_768)
        lines = ["    private static final String[][] SOUND_NAME_FALLBACKS_766_TO_769 = {"]
        for k, v in sorted(table_766_to_769.items()):
            lines.append(f'            {{"{k}", "{v}"}},')
        lines.append("    };")
        out.append("\n".join(lines))
        print(f"[769] sound name table 766->769: {len(table_766_to_769)} entries")

        # 離れた組み合わせの合成表（763↔769、764↔769、765↔769、766↔769、767↔769）。
        composed_all = {}
        for tag, maps_a_to_768, maps_768_to_a, vv_a, direction in (
                ("763_TO_769", comp_763_to_768, comp_768_to_763, a763, "up"),
                ("769_TO_763", comp_763_to_768, comp_768_to_763, a763, "down"),
                ("764_TO_769", comp_764_to_768, comp_768_to_764, a764, "up"),
                ("769_TO_764", comp_764_to_768, comp_768_to_764, a764, "down"),
                ("765_TO_769", comp_765_to_768, comp_768_to_765, b, "up"),
                ("769_TO_765", comp_765_to_768, comp_768_to_765, b, "down"),
                ("766_TO_769", comp_766_to_768, comp_768_to_766, a766, "up"),
                ("769_TO_766", comp_766_to_768, comp_768_to_766, a766, "down"),
                ("767_TO_769", maps768, up768, a767, "up"),
                ("769_TO_767", maps768, up768, a767, "down")):
            composed_all[tag] = emit_composed_769(
                out, tag, maps_a_to_768, maps_768_to_a, vv_a,
                maps769, a769, a768, direction)
            print(f"[769] composed {tag}: "
                  + ", ".join(f"{k}={len(v)}" for k, v in composed_all[tag].items()))
        with open(data_dir / "maps_composed_769.json", "w", encoding="utf-8") as f:
            json.dump({tag: {kind: {str(k): v for k, v in table.items()}
                             for kind, table in comp.items()}
                       for tag, comp in composed_all.items()}, f)

        # 属性の表（protocol.json の Update Attributes の key mapper
        # から名前順で作る。768 は32件、769 は32件。名前も並びも同一の
        # ため恒等写像。違う場合は失敗させる）。
        proto768 = load(data_dir / "mc-1.21.3-protocol.json")
        proto769 = load(data_dir / "mc-1.21.4-protocol.json")
        attrs768 = attribute_names(proto768)
        attrs769 = attribute_names(proto769)
        if attrs768 != attrs769:
            print(f"[769] attributes: 768 と 769 で名前が違う: "
                  f"{[n for n in attrs768 if n not in attrs769]} / "
                  f"{[n for n in attrs769 if n not in attrs768]}", file=sys.stderr)
            failures.append(("attributes", (attrs768, attrs769)))
        attr_768_to_769 = {i: i for i in range(len(attrs768))}
        attr_769_to_768 = {i: i for i in range(len(attrs769))}
        iv, ov = compress(attr_768_to_769, len(attrs768))
        out.append(java_int3("ATTRIBUTE_769_RANGES", iv))
        out.append(java_int2("ATTRIBUTE_769_OVERRIDES", ov))
        out.append(f"    // ATTRIBUTE_769: 768 entries={len(attrs768)} "
                   f"ranges={len(iv)} overrides={len(ov)}")
        iv, ov = compress(attr_769_to_768, len(attrs769))
        out.append(java_int3("ATTRIBUTE_REVERSE_769_RANGES", iv))
        out.append(java_int2("ATTRIBUTE_REVERSE_769_OVERRIDES", ov))
        out.append(f"    // ATTRIBUTE_REVERSE_769: 769 entries={len(attrs769)} "
                   f"ranges={len(iv)} overrides={len(ov)}")
        out.append(java_string_array("ATTRIBUTE_769_NAMES", attrs769))
        print(f"[769] attributes: 768={len(attrs768)} 769={len(attrs769)} identity")

        # エンチャントの表（minecraft-data の dataPaths.json によると
        # 1.21.4 の enchantments は pc/1.21.1 を使うため、768 と同じ
        # 42件・同じ名前順で恒等写像。実機の Registry Data での順序は
        # 統合テストで確認すること）。
        ench768 = [e["name"] for e in load(data_dir / "enchantments-1.21.1.json")]
        ench769 = list(ench768)
        ench_768_to_769 = {i: i for i in range(len(ench768))}
        ench_769_to_768 = {i: i for i in range(len(ench769))}
        iv, ov = compress(ench_768_to_769, len(ench768))
        out.append(java_int3("ENCHANTMENT_769_RANGES", iv))
        out.append(java_int2("ENCHANTMENT_769_OVERRIDES", ov))
        out.append(f"    // ENCHANTMENT_769: 768 entries={len(ench768)} "
                   f"ranges={len(iv)} overrides={len(ov)}")
        iv, ov = compress(ench_769_to_768, len(ench769))
        out.append(java_int3("ENCHANTMENT_REVERSE_769_RANGES", iv))
        out.append(java_int2("ENCHANTMENT_REVERSE_769_OVERRIDES", ov))
        out.append(f"    // ENCHANTMENT_REVERSE_769: 769 entries={len(ench769)} "
                   f"ranges={len(iv)} overrides={len(ov)}")
        print(f"[769] enchantments: 768={len(ench768)} 769={len(ench769)} identity")

    if args.target == "770":
        a766 = load(data_dir / "vv-1.20.5.json")    # 766
        blocks_1205 = load(data_dir / "1.20.5_blocks.json")
        maps766, _, fail766, rev766 = build_pair(
            "766", a766, b, blocks_1205, data_dir,
            rename=RENAME_766, sound_fallbacks=SOUND_FALLBACK_766,
            particle_fallbacks=PARTICLE_FALLBACK_766,
            reverse_fallback=1, add_props=ADD_PROPS_766)
        failures += fail766

        a767 = load(data_dir / "vv-1.21.json")      # 767
        blocks_1201 = load(data_dir / "1.21.1_blocks.json")
        maps767, _, fail767, rev767 = build_pair(
            "767", a767, a766, blocks_1201, data_dir,
            rename=RENAME_767, sound_fallbacks=SOUND_FALLBACK_767,
            particle_fallbacks=PARTICLE_FALLBACK_767,
            reverse_fallback=1)
        failures += fail767

        a768 = load(data_dir / "vv-1.21.2.json")    # 768
        blocks_1213 = load(data_dir / "1.21.3_blocks.json")
        maps768, _, fail768, rev768 = build_pair(
            "768", a768, a767, blocks_1213, data_dir,
            rename=RENAME_768, sound_fallbacks=SOUND_FALLBACK_768,
            particle_fallbacks=PARTICLE_FALLBACK_768,
            reverse_fallback=1, entity_fallbacks=ENTITY_FALLBACK_768)
        failures += fail768

        a769 = load(data_dir / "vv-1.21.4.json")    # 769
        blocks_1214 = load(data_dir / "1.21.4_blocks.json")
        maps769, _, fail769, rev769 = build_pair(
            "769", a769, a768, blocks_1214, data_dir,
            rename=RENAME_769, sound_fallbacks=SOUND_FALLBACK_769,
            particle_fallbacks=PARTICLE_FALLBACK_769,
            reverse_fallback=1, entity_fallbacks=ENTITY_FALLBACK_769)
        failures += fail769

        a770 = load(data_dir / "vv-1.21.5.json")    # 770
        blocks_1215 = load(data_dir / "1.21.5_blocks.json")
        maps770, out770, fail770, rev770 = build_pair(
            "770", a770, a769, blocks_1215, data_dir,
            rename=RENAME_770, sound_fallbacks=SOUND_FALLBACK_770,
            particle_fallbacks=PARTICLE_FALLBACK_770,
            reverse_fallback=1, entity_fallbacks=ENTITY_FALLBACK_770,
            block_fallbacks=BLOCK_FALLBACK_770)
        failures += fail770
        out += out770
        with open(data_dir / "maps770.json", "w", encoding="utf-8") as f:
            json.dump(maps770, f)
        with open(data_dir / "map_items_rev770.json", "w", encoding="utf-8") as f:
            json.dump({str(k): v for k, v in rev770.items()}, f)

        # 768→767 の反転の補正（768 処理と同じ。769 の合成で
        # up768_map を使うため、ここでも計算する）：768 で木材ごとに
        # 分かれた boat／raft は 767 の boat／chest_boat へ。oak の2件は
        # invert の先行勝ちで既に正しいため補正から外す。
        boat = a767["entities"].index("boat")
        chest_boat = a767["entities"].index("chest_boat")
        entity_up_768_corrections = []
        for i, name in enumerate(a768["entities"]):
            if name in ("oak_boat", "oak_chest_boat"):
                continue
            if name.endswith("_chest_boat") or name.endswith("_chest_raft"):
                entity_up_768_corrections.append((i, chest_boat))
            elif name.endswith("_boat") or name.endswith("_raft"):
                entity_up_768_corrections.append((i, boat))
        UP_768_CORRECTIONS["entities"] = entity_up_768_corrections
        print(f"[769] ENTITY_UP_768_CORRECTIONS: "
              f"{len(entity_up_768_corrections)} entries")

        # 769→768 の反転の補正：769 の natural=false・active=true の
        # creaking_heart（768 の forward には無い）は 768 の
        # creaking=active の心臓へ（invert だけだと石に落ちてしまう）。
        # natural=true の心臓は invert の先行勝ちで dormant／active が
        # 既に正しいため補正から外す。合成の下向き表（769→763／764／
        # 765／766／767）と Java の *_UP_769_MAP の両方で使う。
        block_up_769_corrections = []
        for i, name in enumerate(a769["blockstates"]):
            if not name.startswith("creaking_heart["):
                continue
            props = block_props(name)
            if props.get("natural") == "false" and props.get("active") == "true":
                target768 = ("creaking_heart[axis=%s,creaking=active]"
                             % props["axis"])
                block_up_769_corrections.append(
                    (i, a768["blockstates"].index(target768)))
        UP_769_CORRECTIONS["blockstates"] = block_up_769_corrections
        # 注：BLOCK_STATE_UP_769_CORRECTIONS の Java の表は
        # RegistryTables769.java に既にあるため、770 の出力には
        # 入れない（両方にあると RegistryRemapper.java の static
        # import が曖昧になってコンパイルできない）。合成表の計算には
        # この dict だけ使う。
        print(f"[769] BLOCK_STATE_UP_769_CORRECTIONS: "
              f"{len(block_up_769_corrections)} entries (Java には出さない)")

        # 770→769 の反転の補正：770 の creaking_heart_state=uprooted の
        # 心臓（769 の forward には無い）は 769 の active=false の
        # 心臓へ（invert だけだと石に落ちてしまう）。dormant は invert
        # の先行勝ちで active=false が既に正しく、awake は active=true
        # が既に正しいため補正から外す。合成の下向き表（770→763／764／
        # 765／766／767／768）と Java の *_UP_770_MAP の両方で使う。
        block_up_770_corrections = []
        for i, name in enumerate(a770["blockstates"]):
            if not name.startswith("creaking_heart["):
                continue
            props = block_props(name)
            if props.get("creaking_heart_state") == "uprooted":
                target769 = ("creaking_heart[active=false,axis=%s,natural=%s]"
                             % (props["axis"], props["natural"]))
                block_up_770_corrections.append(
                    (i, a769["blockstates"].index(target769)))
        UP_770_CORRECTIONS["blockstates"] = block_up_770_corrections
        lines = ["    private static final int[][] BLOCK_STATE_UP_770_CORRECTIONS = {"]
        for y, x in block_up_770_corrections:
            lines.append(f"            {{{y}, {x}}},")
        lines.append("    };")
        out.append("\n".join(lines))
        print(f"[770] BLOCK_STATE_UP_770_CORRECTIONS: "
              f"{len(block_up_770_corrections)} entries")

        # 770→769 の反転の補正：770 の lingering_potion（100）は 769 の
        # potion（99）へ。splash_potion（99）は invert の先行勝ちで
        # 既に potion になるため補正から外す。
        entity_up_770_corrections = [
            (a770["entities"].index("lingering_potion"),
             a769["entities"].index("potion"))]
        UP_770_CORRECTIONS["entities"] = entity_up_770_corrections
        lines = ["    private static final int[][] ENTITY_UP_770_CORRECTIONS = {"]
        for y, x in entity_up_770_corrections:
            lines.append(f"            {{{y}, {x}}},")
        lines.append("    };")
        out.append("\n".join(lines))
        print(f"[770] ENTITY_UP_770_CORRECTIONS: "
              f"{len(entity_up_770_corrections)} entries")

        # 766↔763／764 の合成表（763↔767／764↔767 の合成の材料。
        # Java の出力は捨て、dict だけ使う）。
        tmp = []
        comp_763_to_766 = emit_composed(
            tmp, "763_TO_766", maps763, a763, maps766, a766, b, "up")
        comp_766_to_763 = emit_composed(
            tmp, "766_TO_763", maps763, a763, maps766, a766, b, "down")
        comp_764_to_766 = emit_composed(
            tmp, "764_TO_766", maps764, a764, maps766, a766, b, "up")
        comp_766_to_764 = emit_composed(
            tmp, "766_TO_764", maps764, a764, maps766, a766, b, "down")

        # 766→765 の反転表（765↔767 の合成の材料。アイテムは
        # 逆変換表、他は invert＋補正＋代替先埋め）。
        up766 = {}
        for kind in ("blockstates", "entities", "sounds", "particles",
                     "blocks", "menus"):
            up766[kind] = {i: v for i, v in enumerate(
                up766_map(maps766, kind, a766))}
        up766["items"] = maps766["reverse_items"]

        kinds = ["blockstates", "items", "entities", "sounds",
                 "particles", "blocks", "menus"]
        comp_765_to_766 = {kind: maps766[kind] for kind in kinds}
        comp_765_to_766["items"] = maps766["items"]

        # 763／764／765↔767 の合成表（763↔768／764↔768／765↔768 の
        # 合成の材料。Java の出力は捨て、dict だけ使う）。
        tmp767 = []
        comp_763_to_767 = emit_composed_767(
            tmp767, "763_TO_767", comp_763_to_766, comp_766_to_763, a763,
            maps767, a767, a766, "up")
        comp_767_to_763 = emit_composed_767(
            tmp767, "767_TO_763", comp_763_to_766, comp_766_to_763, a763,
            maps767, a767, a766, "down")
        comp_764_to_767 = emit_composed_767(
            tmp767, "764_TO_767", comp_764_to_766, comp_766_to_764, a764,
            maps767, a767, a766, "up")
        comp_767_to_764 = emit_composed_767(
            tmp767, "767_TO_764", comp_764_to_766, comp_766_to_764, a764,
            maps767, a767, a766, "down")
        comp_765_to_767 = emit_composed_767(
            tmp767, "765_TO_767", comp_765_to_766, up766, b,
            maps767, a767, a766, "up")
        comp_767_to_765 = emit_composed_767(
            tmp767, "767_TO_765", comp_765_to_766, up766, b,
            maps767, a767, a766, "down")

        # 767→766 の反転表（766↔768 の合成の材料。アイテムは
        # 逆変換表、他は invert＋代替先埋め）。
        up767 = {}
        for kind in ("blockstates", "entities", "sounds", "particles",
                     "blocks", "menus"):
            up767[kind] = {i: v for i, v in enumerate(
                up767_map(maps767, kind, a767))}
        up767["items"] = maps767["reverse_items"]

        identity767 = {kind: {i: i for i in range(len(a767[kind]))}
                       for kind in kinds}

        # 763／764／765／766↔768 の合成表（763↔769／764↔769／765↔769／
        # 766↔769 の合成の材料。Java の出力は捨て、dict だけ使う）。
        tmp768 = []
        comp_763_to_768 = emit_composed_768(
            tmp768, "763_TO_768", comp_763_to_767, comp_767_to_763, a763,
            maps768, a768, a767, "up")
        comp_768_to_763 = emit_composed_768(
            tmp768, "768_TO_763", comp_763_to_767, comp_767_to_763, a763,
            maps768, a768, a767, "down")
        comp_764_to_768 = emit_composed_768(
            tmp768, "764_TO_768", comp_764_to_767, comp_767_to_764, a764,
            maps768, a768, a767, "up")
        comp_768_to_764 = emit_composed_768(
            tmp768, "768_TO_764", comp_764_to_767, comp_767_to_764, a764,
            maps768, a768, a767, "down")
        comp_765_to_768 = emit_composed_768(
            tmp768, "765_TO_768", comp_765_to_767, comp_767_to_765, b,
            maps768, a768, a767, "up")
        comp_768_to_765 = emit_composed_768(
            tmp768, "768_TO_765", comp_765_to_767, comp_767_to_765, b,
            maps768, a768, a767, "down")
        comp_766_to_768 = emit_composed_768(
            tmp768, "766_TO_768", maps767, up767, a766,
            maps768, a768, a767, "up")
        comp_768_to_766 = emit_composed_768(
            tmp768, "768_TO_766", maps767, up767, a766,
            maps768, a768, a767, "down")

        # 768→767 の反転表（767↔769 の合成の材料。アイテムは
        # 逆変換表、他は invert＋補正＋代替先埋め）。
        up768 = {}
        for kind in ("blockstates", "entities", "sounds", "particles",
                     "blocks", "menus"):
            up768[kind] = {i: v for i, v in enumerate(
                up768_map(maps768, kind, a768))}
        up768["items"] = maps768["reverse_items"]

        identity768 = {kind: {i: i for i in range(len(a768[kind]))}
                       for kind in kinds}

        # 763／764／765／766／767↔769 の合成表（763↔770／764↔770／
        # 765↔770／766↔770／767↔770 の合成の材料。Java の出力は捨て、
        # dict だけ使う）。
        tmp769 = []
        comp_763_to_769 = emit_composed_769(
            tmp769, "763_TO_769", comp_763_to_768, comp_768_to_763, a763,
            maps769, a769, a768, "up")
        comp_769_to_763 = emit_composed_769(
            tmp769, "769_TO_763", comp_763_to_768, comp_768_to_763, a763,
            maps769, a769, a768, "down")
        comp_764_to_769 = emit_composed_769(
            tmp769, "764_TO_769", comp_764_to_768, comp_768_to_764, a764,
            maps769, a769, a768, "up")
        comp_769_to_764 = emit_composed_769(
            tmp769, "769_TO_764", comp_764_to_768, comp_768_to_764, a764,
            maps769, a769, a768, "down")
        comp_765_to_769 = emit_composed_769(
            tmp769, "765_TO_769", comp_765_to_768, comp_768_to_765, b,
            maps769, a769, a768, "up")
        comp_769_to_765 = emit_composed_769(
            tmp769, "769_TO_765", comp_765_to_768, comp_768_to_765, b,
            maps769, a769, a768, "down")
        comp_766_to_769 = emit_composed_769(
            tmp769, "766_TO_769", comp_766_to_768, comp_768_to_766, a766,
            maps769, a769, a768, "up")
        comp_769_to_766 = emit_composed_769(
            tmp769, "769_TO_766", comp_766_to_768, comp_768_to_766, a766,
            maps769, a769, a768, "down")
        comp_767_to_769 = emit_composed_769(
            tmp769, "767_TO_769", maps768, up768, a767,
            maps769, a769, a768, "up")
        comp_769_to_767 = emit_composed_769(
            tmp769, "769_TO_767", maps768, up768, a767,
            maps769, a769, a768, "down")

        # 769→768 の反転表（768↔770 の合成の材料。アイテムは
        # 逆変換表、他は invert＋補正＋代替先埋め）。
        up769 = {}
        for kind in ("blockstates", "entities", "sounds", "particles",
                     "blocks", "menus"):
            up769[kind] = {i: v for i, v in enumerate(
                up769_map(maps769, kind, a769))}
        up769["items"] = maps769["reverse_items"]

        identity769 = {kind: {i: i for i in range(len(a769[kind]))}
                       for kind in kinds}

        # 770 の音の名前の表（Stop Sound 用）。
        # 769→770 は entity.wolf.howl（削除）の代替だけ（SOUND_FALLBACK_770）。
        lines = ["    private static final String[][] SOUND_NAME_FALLBACKS_770 = {"]
        for k, v in sorted(SOUND_FALLBACK_770.items()):
            lines.append(f'            {{"{k}", "{v}"}},')
        lines.append("    };")
        out.append("\n".join(lines))
        lines = ["    private static final String[][] SOUND_NAME_FALLBACKS_769_TO_770 = {"]
        for k, v in sorted(SOUND_FALLBACK_770.items()):
            lines.append(f'            {{"{k}", "{v}"}},')
        lines.append("    };")
        out.append("\n".join(lines))
        # 770→769：770の新音（オオカミの変種の音36件・鉄の音5件・
        # リーフリッター5件・サボテンの花2件・砂の音2件・デッドブッシュ／
        # ホタルブッシュの音2件の52件）→ 石の破壊音。改名は無い
        # （削除された wolf.howl は770側に無い）。
        table_770_to_769 = {}
        for name770 in a770["sounds"]:
            if name770 not in set(a769["sounds"]):
                table_770_to_769[name770] = "block.stone.break"
        lines = ["    private static final String[][] SOUND_NAME_FALLBACKS_770_TO_769 = {"]
        for k, v in sorted(table_770_to_769.items()):
            lines.append(f'            {{"{k}", "{v}"}},')
        lines.append("    };")
        out.append("\n".join(lines))
        print(f"[770] sound name table 770->769: {len(table_770_to_769)} entries")
        # 770→768／767／766／765／764／763：数値の表と一致させる
        # （770→769 の反転＋769→target）。
        for tag, maps_769_to_a, vv_target in (
                ("768", up769, a768),
                ("767", comp_769_to_767, a767),
                ("766", comp_769_to_766, a766),
                ("765", comp_769_to_765, b),
                ("763", comp_769_to_763, a763),
                ("764", comp_769_to_764, a764)):
            table = sound_name_table_mid_to_target(
                maps770, {"sounds": maps_769_to_a["sounds"]}, a770,
                vv_target, UP_770_CORRECTIONS.get("sounds"), 1401)
            lines = [f"    private static final String[][] SOUND_NAME_FALLBACKS_770_TO_{tag} = {{"]
            for k, v in sorted(table.items()):
                lines.append(f'            {{"{k}", "{v}"}},')
            lines.append("    };")
            out.append("\n".join(lines))
            print(f"[770] sound name table 770->{tag}: {len(table)} entries")
        # 768→770：769で削除されたヤギの角笛の音2件と 770で削除された
        # wolf.howl の代替の合成（SOUND_FALLBACK_768 と SOUND_FALLBACK_770）。
        # 両方の改名・代替の対象は重ならないため和集合でよい。
        table_768_to_770 = dict(SOUND_FALLBACK_768)
        table_768_to_770.update(SOUND_FALLBACK_770)
        lines = ["    private static final String[][] SOUND_NAME_FALLBACKS_768_TO_770 = {"]
        for k, v in sorted(table_768_to_770.items()):
            lines.append(f'            {{"{k}", "{v}"}},')
        lines.append("    };")
        out.append("\n".join(lines))
        # 767→770：768で削除されたヤギの角笛の音2件と 770で削除された
        # wolf.howl の代替の合成（上と同じ和集合）。
        lines = ["    private static final String[][] SOUND_NAME_FALLBACKS_767_TO_770 = {"]
        for k, v in sorted(table_768_to_770.items()):
            lines.append(f'            {{"{k}", "{v}"}},')
        lines.append("    };")
        out.append("\n".join(lines))
        # 766→770：766→767 の改名（trial_spawner の音2件）と 767→770
        # の代替（ヤギの角笛の音2件＋wolf.howl）の合成。対象は
        # 重ならないため和集合でよい。
        table_766_to_770 = dict(SOUND_FALLBACK_767)
        table_766_to_770.update(table_768_to_770)
        lines = ["    private static final String[][] SOUND_NAME_FALLBACKS_766_TO_770 = {"]
        for k, v in sorted(table_766_to_770.items()):
            lines.append(f'            {{"{k}", "{v}"}},')
        lines.append("    };")
        out.append("\n".join(lines))
        print(f"[770] sound name table 766->770: {len(table_766_to_770)} entries")

        # 離れた組み合わせの合成表（763↔770、764↔770、765↔770、766↔770、767↔770、768↔770）。
        composed_all = {}
        for tag, maps_a_to_769, maps_769_to_a, vv_a, direction in (
                ("763_TO_770", comp_763_to_769, comp_769_to_763, a763, "up"),
                ("770_TO_763", comp_763_to_769, comp_769_to_763, a763, "down"),
                ("764_TO_770", comp_764_to_769, comp_769_to_764, a764, "up"),
                ("770_TO_764", comp_764_to_769, comp_769_to_764, a764, "down"),
                ("765_TO_770", comp_765_to_769, comp_769_to_765, b, "up"),
                ("770_TO_765", comp_765_to_769, comp_769_to_765, b, "down"),
                ("766_TO_770", comp_766_to_769, comp_769_to_766, a766, "up"),
                ("770_TO_766", comp_766_to_769, comp_769_to_766, a766, "down"),
                ("767_TO_770", comp_767_to_769, comp_769_to_767, a767, "up"),
                ("770_TO_767", comp_767_to_769, comp_769_to_767, a767, "down"),
                ("768_TO_770", maps769, up769, a768, "up"),
                ("770_TO_768", maps769, up769, a768, "down")):
            composed_all[tag] = emit_composed_770(
                out, tag, maps_a_to_769, maps_769_to_a, vv_a,
                maps770, a770, a769, direction)
            print(f"[770] composed {tag}: "
                  + ", ".join(f"{k}={len(v)}" for k, v in composed_all[tag].items()))
        with open(data_dir / "maps_composed_770.json", "w", encoding="utf-8") as f:
            json.dump({tag: {kind: {str(k): v for k, v in table.items()}
                             for kind, table in comp.items()}
                       for tag, comp in composed_all.items()}, f)

        # 属性の表（protocol.json の Update Attributes の key mapper
        # から名前順で作る。769 は32件、770 は32件。名前も並びも同一の
        # ため恒等写像。違う場合は失敗させる）。
        proto769 = load(data_dir / "mc-1.21.4-protocol.json")
        proto770 = load(data_dir / "mc-1.21.5-protocol.json")
        attrs769 = attribute_names(proto769)
        attrs770 = attribute_names(proto770)
        if attrs769 != attrs770:
            print(f"[770] attributes: 769 と 770 で名前が違う: "
                  f"{[n for n in attrs769 if n not in attrs770]} / "
                  f"{[n for n in attrs770 if n not in attrs769]}", file=sys.stderr)
            failures.append(("attributes", (attrs769, attrs770)))
        attr_769_to_770 = {i: i for i in range(len(attrs769))}
        attr_770_to_769 = {i: i for i in range(len(attrs770))}
        iv, ov = compress(attr_769_to_770, len(attrs769))
        out.append(java_int3("ATTRIBUTE_770_RANGES", iv))
        out.append(java_int2("ATTRIBUTE_770_OVERRIDES", ov))
        out.append(f"    // ATTRIBUTE_770: 769 entries={len(attrs769)} "
                   f"ranges={len(iv)} overrides={len(ov)}")
        iv, ov = compress(attr_770_to_769, len(attrs770))
        out.append(java_int3("ATTRIBUTE_REVERSE_770_RANGES", iv))
        out.append(java_int2("ATTRIBUTE_REVERSE_770_OVERRIDES", ov))
        out.append(f"    // ATTRIBUTE_REVERSE_770: 770 entries={len(attrs770)} "
                   f"ranges={len(iv)} overrides={len(ov)}")
        out.append(java_string_array("ATTRIBUTE_770_NAMES", attrs770))
        print(f"[770] attributes: 769={len(attrs769)} 770={len(attrs770)} identity")

        # エンチャントの表（minecraft-data の dataPaths.json によると
        # 1.21.5 の enchantments は pc/1.21.5 を使う。1.21.1 のものと
        # 名前も並びも同一の 42件のため、769 と同じ 42件・同じ名前順で
        # 恒等写像。違う場合は失敗させる。実機の Registry Data での
        # 順序は統合テストで確認すること）。
        ench769 = [e["name"] for e in load(data_dir / "enchantments-1.21.1.json")]
        ench770 = [e["name"] for e in load(data_dir / "enchantments-1.21.5.json")]
        if ench769 != ench770:
            print(f"[770] enchantments: 769 と 770 で名前が違う: "
                  f"{[n for n in ench769 if n not in ench770]} / "
                  f"{[n for n in ench770 if n not in ench769]}", file=sys.stderr)
            failures.append(("enchantments", (ench769, ench770)))
        ench_769_to_770 = {i: i for i in range(len(ench769))}
        ench_770_to_769 = {i: i for i in range(len(ench770))}
        iv, ov = compress(ench_769_to_770, len(ench769))
        out.append(java_int3("ENCHANTMENT_770_RANGES", iv))
        out.append(java_int2("ENCHANTMENT_770_OVERRIDES", ov))
        out.append(f"    // ENCHANTMENT_770: 769 entries={len(ench769)} "
                   f"ranges={len(iv)} overrides={len(ov)}")
        iv, ov = compress(ench_770_to_769, len(ench770))
        out.append(java_int3("ENCHANTMENT_REVERSE_770_RANGES", iv))
        out.append(java_int2("ENCHANTMENT_REVERSE_770_OVERRIDES", ov))
        out.append(f"    // ENCHANTMENT_REVERSE_770: 770 entries={len(ench770)} "
                   f"ranges={len(iv)} overrides={len(ov)}")
        print(f"[770] enchantments: 769={len(ench769)} 770={len(ench770)} identity")



    if args.target == "771":
        # 770 の成果物（--target 770 の実行結果。maps770.json・
        # map_items_rev770.json・maps_composed_770.json）を読み、
        # 770→771 の隣接表と 763〜770↔771 の合成表を作る。
        # これらが無い場合は --target 770 を先に実行すること
        # （--target 770 の再実行は gen770.txt を再生成するだけ）。
        for fname in ("maps770.json", "map_items_rev770.json",
                      "maps_composed_770.json"):
            if not (data_dir / fname).exists():
                print(f"無い: {data_dir / fname}（先に --target 770 を実行すること）",
                      file=sys.stderr)
                failures.append(("missing-770-artifact", fname))
        if not failures:
            def load_int_map(raw):
                return {int(k): v for k, v in raw.items()}

            kinds = ["blockstates", "items", "entities", "sounds",
                     "particles", "blocks", "menus"]
            a770 = load(data_dir / "vv-1.21.5.json")    # 770
            a769 = load(data_dir / "vv-1.21.4.json")    # 769
            a771 = load(data_dir / "vv-1.21.6.json")    # 771
            blocks_1216 = load(data_dir / "1.21.6_blocks.json")
            maps770_raw = load(data_dir / "maps770.json")
            maps770 = {k: (load_int_map(v) if isinstance(v, dict)
                           and k != "sound_fallback_names" else v)
                       for k, v in maps770_raw.items()}
            rev770 = load_int_map(load(data_dir / "map_items_rev770.json"))
            comp770_raw = load(data_dir / "maps_composed_770.json")
            comp770 = {tag: {kind: load_int_map(table)
                             for kind, table in kind_tables.items()}
                       for tag, kind_tables in comp770_raw.items()}

            maps771, out771, fail771, rev771 = build_pair(
                "771", a771, a770, blocks_1216, data_dir,
                rename=RENAME_771, sound_fallbacks=SOUND_FALLBACK_771,
                particle_fallbacks=PARTICLE_FALLBACK_771,
                reverse_fallback=1, entity_fallbacks=ENTITY_FALLBACK_771,
                block_fallbacks=BLOCK_FALLBACK_771)
            failures += fail771
            out += out771
            with open(data_dir / "maps771.json", "w", encoding="utf-8") as f:
                json.dump(maps771, f)
            with open(data_dir / "map_items_rev771.json", "w", encoding="utf-8") as f:
                json.dump({str(k): v for k, v in rev771.items()}, f)

            # 770→769 の反転表（769↔771 の合成の材料）。up770_map は
            # UP_770_CORRECTIONS（creaking_heart の uprooted 6状態・
            # lingering_potion）を使うため、ここで同じ計算をする
            # （--target 770 のブロックと同じ）。
            block_up_770_corrections = []
            for i, name in enumerate(a770["blockstates"]):
                if not name.startswith("creaking_heart["):
                    continue
                props = block_props(name)
                if props.get("creaking_heart_state") == "uprooted":
                    target769 = ("creaking_heart[active=false,axis=%s,natural=%s]"
                                 % (props["axis"], props["natural"]))
                    block_up_770_corrections.append(
                        (i, a769["blockstates"].index(target769)))
            UP_770_CORRECTIONS["blockstates"] = block_up_770_corrections
            entity_up_770_corrections = [
                (a770["entities"].index("lingering_potion"),
                 a769["entities"].index("potion"))]
            UP_770_CORRECTIONS["entities"] = entity_up_770_corrections
            up770 = {}
            for kind in ("blockstates", "entities", "sounds", "particles",
                         "blocks", "menus"):
                up770[kind] = {i: v for i, v in enumerate(
                    up770_map(maps770, kind, a770))}
            up770["items"] = rev770

            identity770 = {kind: {i: i for i in range(len(a770[kind]))}
                           for kind in kinds}
            # A→770／770→A（A は 763〜769）。763〜768 は合成表、
            # 769 は build_pair の表（上向き）／反転表（下向き）。
            maps_to_770 = {}
            maps_from_770 = {}
            vv_by_tag = {}
            for tag, vv_a, up_tag, down_tag in (
                    ("763", load(data_dir / "vv-1.20.json"),
                     "763_TO_770", "770_TO_763"),
                    ("764", load(data_dir / "vv-1.20.2.json"),
                     "764_TO_770", "770_TO_764"),
                    ("765", b, "765_TO_770", "770_TO_765"),
                    ("766", load(data_dir / "vv-1.20.5.json"),
                     "766_TO_770", "770_TO_766"),
                    ("767", load(data_dir / "vv-1.21.json"),
                     "767_TO_770", "770_TO_767"),
                    ("768", load(data_dir / "vv-1.21.2.json"),
                     "768_TO_770", "770_TO_768")):
                maps_to_770[tag] = comp770[up_tag]
                maps_from_770[tag] = comp770[down_tag]
                vv_by_tag[tag] = vv_a
            maps_to_770["769"] = maps770
            maps_from_770["769"] = up770
            vv_by_tag["769"] = a769
            maps_to_770["770"] = identity770
            maps_from_770["770"] = identity770
            vv_by_tag["770"] = a770

            # 771 の音の名前の表（Stop Sound 用）。
            # 770→771 は entity.leash_knot.break／place の改名と
            # entity.puffer_fish.ambient／block.sand.wind の削除の
            # 代替だけ（SOUND_FALLBACK_771）。
            lines = ["    private static final String[][] SOUND_NAME_FALLBACKS_771 = {"]
            for k, v in sorted(SOUND_FALLBACK_771.items()):
                lines.append(f'            {{"{k}", "{v}"}},')
            lines.append("    };")
            out.append("\n".join(lines))
            lines = ["    private static final String[][] SOUND_NAME_FALLBACKS_770_TO_771 = {"]
            for k, v in sorted(SOUND_FALLBACK_771.items()):
                lines.append(f'            {{"{k}", "{v}"}},')
            lines.append("    };")
            out.append("\n".join(lines))
            # 771→770：数値の表（771→770 の反転）と一致させる。
            # 771 の新音のうち 770 の削除音から写ったものは、その 770 の
            # 音へ戻す（dry_grass.ambient → sand.wind、lead.tied／
            # lead.break → leash_knot.place／break、blow_out → ambient）。
            # それ以外の新音は石の破壊音。770 の wolf.howl の場合と違い、
            # 衝突の先行勝ちが同名にならない（blow_out は ambient の方が
            # 番号が小さい）ため、単純に新音→石の破壊音としてはならない。
            table_771_to_770 = sound_name_table_mid_to_target(
                maps771, {"sounds": {i: i for i in range(len(a770["sounds"]))}},
                a771, a770, UP_771_CORRECTIONS.get("sounds"), 1417)
            lines = ["    private static final String[][] SOUND_NAME_FALLBACKS_771_TO_770 = {"]
            for k, v in sorted(table_771_to_770.items()):
                lines.append(f'            {{"{k}", "{v}"}},')
            lines.append("    };")
            out.append("\n".join(lines))
            print(f"[771] sound name table 771->770: {len(table_771_to_770)} entries")
            # 771→769／768／767／766／765／764／763：数値の表と一致させる
            # （771→770 の反転＋770→target）。
            for tag, maps_770_to_a, vv_target in (
                    ("769", up770, a769),
                    ("768", maps_from_770["768"], vv_by_tag["768"]),
                    ("767", maps_from_770["767"], vv_by_tag["767"]),
                    ("766", maps_from_770["766"], vv_by_tag["766"]),
                    ("765", maps_from_770["765"], b),
                    ("763", maps_from_770["763"], vv_by_tag["763"]),
                    ("764", maps_from_770["764"], vv_by_tag["764"])):
                table = sound_name_table_mid_to_target(
                    maps771, {"sounds": maps_770_to_a["sounds"]}, a771,
                    vv_target, UP_771_CORRECTIONS.get("sounds"), 1417)
                lines = [f"    private static final String[][] SOUND_NAME_FALLBACKS_771_TO_{tag} = {{"]
                for k, v in sorted(table.items()):
                    lines.append(f'            {{"{k}", "{v}"}},')
                lines.append("    };")
                out.append("\n".join(lines))
                print(f"[771] sound name table 771->{tag}: {len(table)} entries")
            # 769→771：770で削除された wolf.howl の代替と 771で削除された
            # 4件の代替の合成（SOUND_FALLBACK_770 と SOUND_FALLBACK_771）。
            # 両方の改名・代替の対象は重ならないため和集合でよい。
            table_769_to_771 = dict(SOUND_FALLBACK_770)
            table_769_to_771.update(SOUND_FALLBACK_771)
            lines = ["    private static final String[][] SOUND_NAME_FALLBACKS_769_TO_771 = {"]
            for k, v in sorted(table_769_to_771.items()):
                lines.append(f'            {{"{k}", "{v}"}},')
            lines.append("    };")
            out.append("\n".join(lines))
            # 768→771：767→768 の改名（ヤギの角笛の音2件）も合成。
            table_768_to_771 = dict(SOUND_FALLBACK_768)
            table_768_to_771.update(table_769_to_771)
            lines = ["    private static final String[][] SOUND_NAME_FALLBACKS_768_TO_771 = {"]
            for k, v in sorted(table_768_to_771.items()):
                lines.append(f'            {{"{k}", "{v}"}},')
            lines.append("    };")
            out.append("\n".join(lines))
            # 767→771：768→771 と同じ和集合。
            lines = ["    private static final String[][] SOUND_NAME_FALLBACKS_767_TO_771 = {"]
            for k, v in sorted(table_768_to_771.items()):
                lines.append(f'            {{"{k}", "{v}"}},')
            lines.append("    };")
            out.append("\n".join(lines))
            # 766→771：766→767 の改名（trial_spawner の音2件）も合成。
            table_766_to_771 = dict(SOUND_FALLBACK_767)
            table_766_to_771.update(table_768_to_771)
            lines = ["    private static final String[][] SOUND_NAME_FALLBACKS_766_TO_771 = {"]
            for k, v in sorted(table_766_to_771.items()):
                lines.append(f'            {{"{k}", "{v}"}},')
            lines.append("    };")
            out.append("\n".join(lines))
            print(f"[771] sound name table 766->771: {len(table_766_to_771)} entries")

            # 離れた組み合わせの合成表（763↔771、764↔771、765↔771、766↔771、767↔771、768↔771、769↔771、770↔771）。
            composed_all = {}
            for tag, maps_a_to_770, maps_770_to_a, vv_a, direction in (
                    ("763_TO_771", maps_to_770["763"], maps_from_770["763"], vv_by_tag["763"], "up"),
                    ("771_TO_763", maps_to_770["763"], maps_from_770["763"], vv_by_tag["763"], "down"),
                    ("764_TO_771", maps_to_770["764"], maps_from_770["764"], vv_by_tag["764"], "up"),
                    ("771_TO_764", maps_to_770["764"], maps_from_770["764"], vv_by_tag["764"], "down"),
                    ("765_TO_771", maps_to_770["765"], maps_from_770["765"], b, "up"),
                    ("771_TO_765", maps_to_770["765"], maps_from_770["765"], b, "down"),
                    ("766_TO_771", maps_to_770["766"], maps_from_770["766"], vv_by_tag["766"], "up"),
                    ("771_TO_766", maps_to_770["766"], maps_from_770["766"], vv_by_tag["766"], "down"),
                    ("767_TO_771", maps_to_770["767"], maps_from_770["767"], vv_by_tag["767"], "up"),
                    ("771_TO_767", maps_to_770["767"], maps_from_770["767"], vv_by_tag["767"], "down"),
                    ("768_TO_771", maps_to_770["768"], maps_from_770["768"], vv_by_tag["768"], "up"),
                    ("771_TO_768", maps_to_770["768"], maps_from_770["768"], vv_by_tag["768"], "down"),
                    ("769_TO_771", maps_to_770["769"], maps_from_770["769"], a769, "up"),
                    ("771_TO_769", maps_to_770["769"], maps_from_770["769"], a769, "down"),
                    ("770_TO_771", identity770, identity770, a770, "up"),
                    ("771_TO_770", identity770, identity770, a770, "down")):
                composed_all[tag] = emit_composed_771(
                    out, tag, maps_a_to_770, maps_770_to_a, vv_a,
                    maps771, a771, a770, direction)
                print(f"[771] composed {tag}: "
                      + ", ".join(f"{k}={len(v)}" for k, v in composed_all[tag].items()))
            with open(data_dir / "maps_composed_771.json", "w", encoding="utf-8") as f:
                json.dump({tag: {kind: {str(k): v for k, v in table.items()}
                                 for kind, table in comp.items()}
                           for tag, comp in composed_all.items()}, f)

            # 属性の表（protocol.json の Update Attributes の key mapper
            # から名前順で作る。770 は32件、771 は35件。改名は無く、
            # 新規3件（camera_distance・waypoint_transmit_range・
            # waypoint_receive_range）は逆向きで -1（DROP）にする）。
            proto770 = load(data_dir / "mc-1.21.5-protocol.json")
            proto771 = load(data_dir / "mc-1.21.6-protocol.json")
            attrs770 = attribute_names(proto770)
            attrs771 = attribute_names(proto771)
            renamed = {n for n in attrs770 if n not in attrs771
                       and ATTRIBUTE_RENAME_771.get(n) not in attrs771}
            if renamed:
                print(f"[771] attributes: unexpected renames: {sorted(renamed)}",
                      file=sys.stderr)
                failures.append(("attributes-rename", sorted(renamed)))
            attr_770_to_771 = {}
            for i, n in enumerate(attrs770):
                target = ATTRIBUTE_RENAME_771.get(n, n)
                attr_770_to_771[i] = attrs771.index(target) if target in attrs771 else -1
            attr_771_to_770 = {}
            for i, n in enumerate(attrs771):
                src = None
                for k, v in ATTRIBUTE_RENAME_771.items():
                    if v == n:
                        src = k
                        break
                if src is None:
                    src = n
                attr_771_to_770[i] = attrs770.index(src) if src in attrs770 else -1
            iv, ov = compress(attr_770_to_771, len(attrs770))
            out.append(java_int3("ATTRIBUTE_771_RANGES", iv))
            out.append(java_int2("ATTRIBUTE_771_OVERRIDES", ov))
            out.append(f"    // ATTRIBUTE_771: 770 entries={len(attrs770)} "
                       f"ranges={len(iv)} overrides={len(ov)}")
            iv, ov = compress(attr_771_to_770, len(attrs771))
            out.append(java_int3("ATTRIBUTE_REVERSE_771_RANGES", iv))
            out.append(java_int2("ATTRIBUTE_REVERSE_771_OVERRIDES", ov))
            out.append(f"    // ATTRIBUTE_REVERSE_771: 771 entries={len(attrs771)} "
                       f"ranges={len(iv)} overrides={len(ov)}")
            out.append(java_string_array("ATTRIBUTE_771_NAMES", attrs771))
            print(f"[771] attributes: 770={len(attrs770)} 771={len(attrs771)} "
                  f"new={[n for n in attrs771 if n not in attrs770]}")

            # エンチャントの表（minecraft-data の enchantments.json。
            # 1.21.5 と 1.21.6 は同じ 42件・同じ名前順で恒等写像。
            # 違う場合は失敗させる。実機の Registry Data での順序は
            # 統合テストで確認すること）。
            ench770 = [e["name"] for e in load(data_dir / "enchantments-1.21.5.json")]
            ench771 = [e["name"] for e in load(data_dir / "enchantments-1.21.6.json")]
            if ench770 != ench771:
                print(f"[771] enchantments: 770 と 771 で名前が違う: "
                      f"{[n for n in ench770 if n not in ench771]} / "
                      f"{[n for n in ench771 if n not in ench770]}", file=sys.stderr)
                failures.append(("enchantments", (ench770, ench771)))
            ench_770_to_771 = {i: i for i in range(len(ench770))}
            ench_771_to_770 = {i: i for i in range(len(ench771))}
            iv, ov = compress(ench_770_to_771, len(ench770))
            out.append(java_int3("ENCHANTMENT_771_RANGES", iv))
            out.append(java_int2("ENCHANTMENT_771_OVERRIDES", ov))
            out.append(f"    // ENCHANTMENT_771: 770 entries={len(ench770)} "
                       f"ranges={len(iv)} overrides={len(ov)}")
            iv, ov = compress(ench_771_to_770, len(ench771))
            out.append(java_int3("ENCHANTMENT_REVERSE_771_RANGES", iv))
            out.append(java_int2("ENCHANTMENT_REVERSE_771_OVERRIDES", ov))
            out.append(f"    // ENCHANTMENT_REVERSE_771: 771 entries={len(ench771)} "
                       f"ranges={len(iv)} overrides={len(ov)}")
            print(f"[771] enchantments: 770={len(ench770)} 771={len(ench771)} identity")

    if args.target == "772":
        # 771 の成果物（--target 771 の実行結果。maps771.json・
        # map_items_rev771.json・maps_composed_771.json）を読み、
        # 771→772 の隣接表と 763〜771↔772 の合成表を作る。
        # これらが無い場合は --target 771 を先に実行すること
        # （--target 771 の再実行は gen771.txt を再生成するだけ）。
        for fname in ("maps771.json", "map_items_rev771.json",
                      "maps_composed_771.json"):
            if not (data_dir / fname).exists():
                print(f"無い: {data_dir / fname}（先に --target 771 を実行すること）",
                      file=sys.stderr)
                failures.append(("missing-771-artifact", fname))
        if not failures:
            def load_int_map(raw):
                return {int(k): v for k, v in raw.items()}

            kinds = ["blockstates", "items", "entities", "sounds",
                     "particles", "blocks", "menus"]
            a771 = load(data_dir / "vv-1.21.6.json")    # 771
            a770 = load(data_dir / "vv-1.21.5.json")    # 770
            a772 = load(data_dir / "vv-1.21.7.json")    # 772
            blocks_1218 = load(data_dir / "1.21.8_blocks.json")
            maps771_raw = load(data_dir / "maps771.json")
            maps771 = {k: (load_int_map(v) if isinstance(v, dict)
                           and k != "sound_fallback_names" else v)
                       for k, v in maps771_raw.items()}
            rev771 = load_int_map(load(data_dir / "map_items_rev771.json"))
            comp771_raw = load(data_dir / "maps_composed_771.json")
            comp771 = {tag: {kind: load_int_map(table)
                             for kind, table in kind_tables.items()}
                       for tag, kind_tables in comp771_raw.items()}

            maps772, out772, fail772, rev772 = build_pair(
                "772", a772, a771, blocks_1218, data_dir,
                rename=RENAME_772, sound_fallbacks=SOUND_FALLBACK_772,
                particle_fallbacks=PARTICLE_FALLBACK_772,
                reverse_fallback=1, entity_fallbacks=ENTITY_FALLBACK_772,
                block_fallbacks=BLOCK_FALLBACK_772)
            failures += fail772
            out += out772
            with open(data_dir / "maps772.json", "w", encoding="utf-8") as f:
                json.dump(maps772, f)
            with open(data_dir / "map_items_rev772.json", "w", encoding="utf-8") as f:
                json.dump({str(k): v for k, v in rev772.items()}, f)

            # 771→770 の反転表（770↔772 の合成の材料）。771→770 は
            # 補正が無い（UP_771_CORRECTIONS は空）ため、ここで同じ計算を
            # するだけでよい（--target 771 のブロックと同じ）。
            up771 = {}
            for kind in ("blockstates", "entities", "sounds", "particles",
                         "blocks", "menus"):
                up771[kind] = {i: v for i, v in enumerate(
                    up771_map(maps771, kind, a771))}
            up771["items"] = rev771

            identity771 = {kind: {i: i for i in range(len(a771[kind]))}
                           for kind in kinds}
            # A→771／771→A（A は 763〜770）。763〜769 は合成表、
            # 770 は build_pair の表（上向き）／反転表（下向き）。
            maps_to_771 = {}
            maps_from_771 = {}
            vv_by_tag = {}
            for tag, vv_a, up_tag, down_tag in (
                    ("763", load(data_dir / "vv-1.20.json"),
                     "763_TO_771", "771_TO_763"),
                    ("764", load(data_dir / "vv-1.20.2.json"),
                     "764_TO_771", "771_TO_764"),
                    ("765", b, "765_TO_771", "771_TO_765"),
                    ("766", load(data_dir / "vv-1.20.5.json"),
                     "766_TO_771", "771_TO_766"),
                    ("767", load(data_dir / "vv-1.21.json"),
                     "767_TO_771", "771_TO_767"),
                    ("768", load(data_dir / "vv-1.21.2.json"),
                     "768_TO_771", "771_TO_768"),
                    ("769", load(data_dir / "vv-1.21.4.json"),
                     "769_TO_771", "771_TO_769"),
                    ("770", a770, "770_TO_771", "771_TO_770")):
                maps_to_771[tag] = comp771[up_tag]
                maps_from_771[tag] = comp771[down_tag]
                vv_by_tag[tag] = vv_a
            maps_to_771["771"] = identity771
            maps_from_771["771"] = identity771
            vv_by_tag["771"] = a771

            # 772 の音の名前の表（Stop Sound 用）。
            # 771→772 は改名・削除が無く、新規は music_disc.lava_chicken
            # だけのため、順向きの代替表は空である。
            lines = ["    private static final String[][] SOUND_NAME_FALLBACKS_772 = {"]
            for k, v in sorted(SOUND_FALLBACK_772.items()):
                lines.append(f'            {{"{k}", "{v}"}},')
            lines.append("    };")
            out.append("\n".join(lines))
            lines = ["    private static final String[][] SOUND_NAME_FALLBACKS_771_TO_772 = {"]
            for k, v in sorted(SOUND_FALLBACK_772.items()):
                lines.append(f'            {{"{k}", "{v}"}},')
            lines.append("    };")
            out.append("\n".join(lines))
            # 772→771：数値の表（772→771 の反転）と一致させる。
            # 772 の新音 music_disc.lava_chicken は 771 に無いため、
            # 石の破壊音（771 の 1441）へ落とす。
            table_772_to_771 = sound_name_table_mid_to_target(
                maps772, {"sounds": {i: i for i in range(len(a771["sounds"]))}},
                a772, a771, UP_772_CORRECTIONS.get("sounds"), 1441)
            lines = ["    private static final String[][] SOUND_NAME_FALLBACKS_772_TO_771 = {"]
            for k, v in sorted(table_772_to_771.items()):
                lines.append(f'            {{"{k}", "{v}"}},')
            lines.append("    };")
            out.append("\n".join(lines))
            print(f"[772] sound name table 772->771: {len(table_772_to_771)} entries")
            # 772→770／769／768／767／766／765／764／763：数値の表と一致させる
            # （772→771 の反転＋771→target）。
            for tag, maps_771_to_a, vv_target in (
                    ("770", up771, a770),
                    ("769", maps_from_771["769"], vv_by_tag["769"]),
                    ("768", maps_from_771["768"], vv_by_tag["768"]),
                    ("767", maps_from_771["767"], vv_by_tag["767"]),
                    ("766", maps_from_771["766"], vv_by_tag["766"]),
                    ("765", maps_from_771["765"], b),
                    ("763", maps_from_771["763"], vv_by_tag["763"]),
                    ("764", maps_from_771["764"], vv_by_tag["764"])):
                table = sound_name_table_mid_to_target(
                    maps772, {"sounds": maps_771_to_a["sounds"]}, a772,
                    vv_target, UP_772_CORRECTIONS.get("sounds"), 1441)
                lines = [f"    private static final String[][] SOUND_NAME_FALLBACKS_772_TO_{tag} = {{"]
                for k, v in sorted(table.items()):
                    lines.append(f'            {{"{k}", "{v}"}},')
                lines.append("    };")
                out.append("\n".join(lines))
                print(f"[772] sound name table 772->{tag}: {len(table)} entries")
            # 770→772：771で削除された 4件の代替の合成
            # （SOUND_FALLBACK_771 と SOUND_FALLBACK_772）。
            # 両方の改名・代替の対象は重ならないため和集合でよい。
            table_770_to_772 = dict(SOUND_FALLBACK_771)
            table_770_to_772.update(SOUND_FALLBACK_772)
            lines = ["    private static final String[][] SOUND_NAME_FALLBACKS_770_TO_772 = {"]
            for k, v in sorted(table_770_to_772.items()):
                lines.append(f'            {{"{k}", "{v}"}},')
            lines.append("    };")
            out.append("\n".join(lines))
            # 769→772：770で削除された wolf.howl の代替と 771で削除された
            # 4件の代替の合成（SOUND_FALLBACK_770 と SOUND_FALLBACK_771）。
            table_769_to_772 = dict(SOUND_FALLBACK_770)
            table_769_to_772.update(table_770_to_772)
            lines = ["    private static final String[][] SOUND_NAME_FALLBACKS_769_TO_772 = {"]
            for k, v in sorted(table_769_to_772.items()):
                lines.append(f'            {{"{k}", "{v}"}},')
            lines.append("    };")
            out.append("\n".join(lines))
            # 768→772：767→768 の改名（ヤギの角笛の音2件）も合成。
            table_768_to_772 = dict(SOUND_FALLBACK_768)
            table_768_to_772.update(table_769_to_772)
            lines = ["    private static final String[][] SOUND_NAME_FALLBACKS_768_TO_772 = {"]
            for k, v in sorted(table_768_to_772.items()):
                lines.append(f'            {{"{k}", "{v}"}},')
            lines.append("    };")
            out.append("\n".join(lines))
            # 767→772：768→772 と同じ和集合。
            lines = ["    private static final String[][] SOUND_NAME_FALLBACKS_767_TO_772 = {"]
            for k, v in sorted(table_768_to_772.items()):
                lines.append(f'            {{"{k}", "{v}"}},')
            lines.append("    };")
            out.append("\n".join(lines))
            # 766→772：766→767 の改名（trial_spawner の音2件）も合成。
            table_766_to_772 = dict(SOUND_FALLBACK_767)
            table_766_to_772.update(table_768_to_772)
            lines = ["    private static final String[][] SOUND_NAME_FALLBACKS_766_TO_772 = {"]
            for k, v in sorted(table_766_to_772.items()):
                lines.append(f'            {{"{k}", "{v}"}},')
            lines.append("    };")
            out.append("\n".join(lines))
            print(f"[772] sound name table 766->772: {len(table_766_to_772)} entries")

            # 離れた組み合わせの合成表（763↔772、764↔772、765↔772、766↔772、767↔772、768↔772、769↔772、770↔772、771↔772）。
            composed_all = {}
            for tag, maps_a_to_771, maps_771_to_a, vv_a, direction in (
                    ("763_TO_772", maps_to_771["763"], maps_from_771["763"], vv_by_tag["763"], "up"),
                    ("772_TO_763", maps_to_771["763"], maps_from_771["763"], vv_by_tag["763"], "down"),
                    ("764_TO_772", maps_to_771["764"], maps_from_771["764"], vv_by_tag["764"], "up"),
                    ("772_TO_764", maps_to_771["764"], maps_from_771["764"], vv_by_tag["764"], "down"),
                    ("765_TO_772", maps_to_771["765"], maps_from_771["765"], b, "up"),
                    ("772_TO_765", maps_to_771["765"], maps_from_771["765"], b, "down"),
                    ("766_TO_772", maps_to_771["766"], maps_from_771["766"], vv_by_tag["766"], "up"),
                    ("772_TO_766", maps_to_771["766"], maps_from_771["766"], vv_by_tag["766"], "down"),
                    ("767_TO_772", maps_to_771["767"], maps_from_771["767"], vv_by_tag["767"], "up"),
                    ("772_TO_767", maps_to_771["767"], maps_from_771["767"], vv_by_tag["767"], "down"),
                    ("768_TO_772", maps_to_771["768"], maps_from_771["768"], vv_by_tag["768"], "up"),
                    ("772_TO_768", maps_to_771["768"], maps_from_771["768"], vv_by_tag["768"], "down"),
                    ("769_TO_772", maps_to_771["769"], maps_from_771["769"], vv_by_tag["769"], "up"),
                    ("772_TO_769", maps_to_771["769"], maps_from_771["769"], vv_by_tag["769"], "down"),
                    ("770_TO_772", maps_to_771["770"], maps_from_771["770"], a770, "up"),
                    ("772_TO_770", maps_to_771["770"], maps_from_771["770"], a770, "down"),
                    ("771_TO_772", identity771, identity771, a771, "up"),
                    ("772_TO_771", identity771, identity771, a771, "down")):
                composed_all[tag] = emit_composed_772(
                    out, tag, maps_a_to_771, maps_771_to_a, vv_a,
                    maps772, a772, a771, direction)
                print(f"[772] composed {tag}: "
                      + ", ".join(f"{k}={len(v)}" for k, v in composed_all[tag].items()))
            with open(data_dir / "maps_composed_772.json", "w", encoding="utf-8") as f:
                json.dump({tag: {kind: {str(k): v for k, v in table.items()}
                                 for kind, table in comp.items()}
                           for tag, comp in composed_all.items()}, f)

            # 属性の表（protocol.json の Update Attributes の key mapper
            # から名前順で作る。771 は35件、772 は35件。改名は無く、
            # 新規も削除も無いため両向きとも恒等写像）。
            proto771 = load(data_dir / "mc-1.21.6-protocol.json")
            proto772 = load(data_dir / "mc-1.21.8-protocol.json")
            attrs771 = attribute_names(proto771)
            attrs772 = attribute_names(proto772)
            renamed = {n for n in attrs771 if n not in attrs772
                       and ATTRIBUTE_RENAME_772.get(n) not in attrs772}
            if renamed:
                print(f"[772] attributes: unexpected renames: {sorted(renamed)}",
                      file=sys.stderr)
                failures.append(("attributes-rename", sorted(renamed)))
            attr_771_to_772 = {}
            for i, n in enumerate(attrs771):
                target = ATTRIBUTE_RENAME_772.get(n, n)
                attr_771_to_772[i] = attrs772.index(target) if target in attrs772 else -1
            attr_772_to_771 = {}
            for i, n in enumerate(attrs772):
                src = None
                for k, v in ATTRIBUTE_RENAME_772.items():
                    if v == n:
                        src = k
                        break
                if src is None:
                    src = n
                attr_772_to_771[i] = attrs771.index(src) if src in attrs771 else -1
            iv, ov = compress(attr_771_to_772, len(attrs771))
            out.append(java_int3("ATTRIBUTE_772_RANGES", iv))
            out.append(java_int2("ATTRIBUTE_772_OVERRIDES", ov))
            out.append(f"    // ATTRIBUTE_772: 771 entries={len(attrs771)} "
                       f"ranges={len(iv)} overrides={len(ov)}")
            iv, ov = compress(attr_772_to_771, len(attrs772))
            out.append(java_int3("ATTRIBUTE_REVERSE_772_RANGES", iv))
            out.append(java_int2("ATTRIBUTE_REVERSE_772_OVERRIDES", ov))
            out.append(f"    // ATTRIBUTE_REVERSE_772: 772 entries={len(attrs772)} "
                       f"ranges={len(iv)} overrides={len(ov)}")
            out.append(java_string_array("ATTRIBUTE_772_NAMES", attrs772))
            print(f"[772] attributes: 771={len(attrs771)} 772={len(attrs772)} "
                  f"new={[n for n in attrs772 if n not in attrs771]}")

            # エンチャントの表（minecraft-data の enchantments.json。
            # 1.21.6 と 1.21.8 は同じ 42件・同じ名前順で恒等写像。
            # 違う場合は失敗させる。実機の Registry Data での順序は
            # 統合テストで確認すること）。
            ench771 = [e["name"] for e in load(data_dir / "enchantments-1.21.6.json")]
            ench772 = [e["name"] for e in load(data_dir / "enchantments-1.21.8.json")]
            if ench771 != ench772:
                print(f"[772] enchantments: 771 と 772 で名前が違う: "
                      f"{[n for n in ench771 if n not in ench772]} / "
                      f"{[n for n in ench772 if n not in ench771]}", file=sys.stderr)
                failures.append(("enchantments", (ench771, ench772)))
            ench_771_to_772 = {i: i for i in range(len(ench771))}
            ench_772_to_771 = {i: i for i in range(len(ench772))}
            iv, ov = compress(ench_771_to_772, len(ench771))
            out.append(java_int3("ENCHANTMENT_772_RANGES", iv))
            out.append(java_int2("ENCHANTMENT_772_OVERRIDES", ov))
            out.append(f"    // ENCHANTMENT_772: 771 entries={len(ench771)} "
                       f"ranges={len(iv)} overrides={len(ov)}")
            iv, ov = compress(ench_772_to_771, len(ench772))
            out.append(java_int3("ENCHANTMENT_REVERSE_772_RANGES", iv))
            out.append(java_int2("ENCHANTMENT_REVERSE_772_OVERRIDES", ov))
            out.append(f"    // ENCHANTMENT_REVERSE_772: 772 entries={len(ench772)} "
                       f"ranges={len(iv)} overrides={len(ov)}")
            print(f"[772] enchantments: 771={len(ench771)} 772={len(ench772)} identity")


    if args.target == "773":
        # 772 の成果物（--target 772 の実行結果。maps772.json・
        # map_items_rev772.json・maps_composed_772.json）を読み、
        # 772→773 の隣接表と 763〜772↔773 の合成表を作る。
        # これらが無い場合は --target 772 を先に実行すること
        # （--target 772 の再実行は gen772.txt を再生成するだけ）。
        for fname in ("maps772.json", "map_items_rev772.json",
                      "maps_composed_772.json"):
            if not (data_dir / fname).exists():
                print(f"無い: {data_dir / fname}（先に --target 772 を実行すること）",
                      file=sys.stderr)
                failures.append(("missing-772-artifact", fname))
        if not failures:
            def load_int_map(raw):
                return {int(k): v for k, v in raw.items()}

            kinds = ["blockstates", "items", "entities", "sounds",
                     "particles", "blocks", "menus"]
            a772 = load(data_dir / "vv-1.21.7.json")    # 772
            a771 = load(data_dir / "vv-1.21.6.json")    # 771
            a770 = load(data_dir / "vv-1.21.5.json")    # 770
            a773 = load(data_dir / "vv-1.21.9.json")    # 773
            blocks_1219 = load(data_dir / "1.21.9_blocks.json")
            maps772_raw = load(data_dir / "maps772.json")
            maps772 = {k: (load_int_map(v) if isinstance(v, dict)
                           and k != "sound_fallback_names" else v)
                       for k, v in maps772_raw.items()}
            rev772 = load_int_map(load(data_dir / "map_items_rev772.json"))
            comp772_raw = load(data_dir / "maps_composed_772.json")
            comp772 = {tag: {kind: load_int_map(table)
                             for kind, table in kind_tables.items()}
                       for tag, kind_tables in comp772_raw.items()}

            maps773, out773, fail773, rev773 = build_pair(
                "773", a773, a772, blocks_1219, data_dir,
                rename=RENAME_773, sound_fallbacks=SOUND_FALLBACK_773,
                particle_fallbacks=PARTICLE_FALLBACK_773,
                reverse_fallback=1, entity_fallbacks=ENTITY_FALLBACK_773,
                block_fallbacks=BLOCK_FALLBACK_773)
            failures += fail773
            out += out773
            with open(data_dir / "maps773.json", "w", encoding="utf-8") as f:
                json.dump(maps773, f)
            with open(data_dir / "map_items_rev773.json", "w", encoding="utf-8") as f:
                json.dump({str(k): v for k, v in rev773.items()}, f)

            # 772→771 の反転表（771↔773 の合成の材料）。772→771 は
            # 補正が無い（UP_772_CORRECTIONS は空）ため、ここで同じ計算を
            # するだけでよい（--target 772 のブロックと同じ）。
            up772 = {}
            for kind in ("blockstates", "entities", "sounds", "particles",
                         "blocks", "menus"):
                up772[kind] = {i: v for i, v in enumerate(
                    up772_map(maps772, kind, a772))}
            up772["items"] = rev772

            identity772 = {kind: {i: i for i in range(len(a772[kind]))}
                           for kind in kinds}
            # A→772／772→A（A は 763〜772）。763〜771 は合成表
            # （maps_composed_772.json)、772 は恒等写像。
            maps_to_772 = {}
            maps_from_772 = {}
            vv_by_tag = {}
            for tag, vv_a, up_tag, down_tag in (
                    ("763", load(data_dir / "vv-1.20.json"),
                     "763_TO_772", "772_TO_763"),
                    ("764", load(data_dir / "vv-1.20.2.json"),
                     "764_TO_772", "772_TO_764"),
                    ("765", b, "765_TO_772", "772_TO_765"),
                    ("766", load(data_dir / "vv-1.20.5.json"),
                     "766_TO_772", "772_TO_766"),
                    ("767", load(data_dir / "vv-1.21.json"),
                     "767_TO_772", "772_TO_767"),
                    ("768", load(data_dir / "vv-1.21.2.json"),
                     "768_TO_772", "772_TO_768"),
                    ("769", load(data_dir / "vv-1.21.4.json"),
                     "769_TO_772", "772_TO_769"),
                    ("770", load(data_dir / "vv-1.21.5.json"),
                     "770_TO_772", "772_TO_770"),
                    ("771", a771, "771_TO_772", "772_TO_771")):
                maps_to_772[tag] = comp772[up_tag]
                maps_from_772[tag] = comp772[down_tag]
                vv_by_tag[tag] = vv_a
            maps_to_772["772"] = identity772
            maps_from_772["772"] = identity772
            vv_by_tag["772"] = a772

            # 773 の音の名前の表（Stop Sound 用）。
            # 772→773 は改名1件（chain→iron_chain は音ではないため音の
            # 代替表には出ない）・削除無し、新規は銅関連の 43 件のため、
            # 順向きの代替表は空である。
            lines = ["    private static final String[][] SOUND_NAME_FALLBACKS_773 = {"]
            for k, v in sorted(SOUND_FALLBACK_773.items()):
                lines.append(f'            {{"{k}", "{v}"}},')
            lines.append("    };")
            out.append("\n".join(lines))
            lines = ["    private static final String[][] SOUND_NAME_FALLBACKS_772_TO_773 = {"]
            for k, v in sorted(SOUND_FALLBACK_773.items()):
                lines.append(f'            {{"{k}", "{v}"}},')
            lines.append("    };")
            out.append("\n".join(lines))
            # 773→772：数値の表（773→772 の反転）と一致させる。
            # 773 の新音 43 件は 772 に無いため、石の破壊音
            # （772 の 1442）へ落とす。
            table_773_to_772 = sound_name_table_mid_to_target(
                maps773, {"sounds": {i: i for i in range(len(a772["sounds"]))}},
                a773, a772, UP_773_CORRECTIONS.get("sounds"), 1442)
            lines = ["    private static final String[][] SOUND_NAME_FALLBACKS_773_TO_772 = {"]
            for k, v in sorted(table_773_to_772.items()):
                lines.append(f'            {{"{k}", "{v}"}},')
            lines.append("    };")
            out.append("\n".join(lines))
            print(f"[773] sound name table 773->772: {len(table_773_to_772)} entries")
            # 773→771／769／768／767／766／765／764／763：数値の表と一致させる
            # （773→772 の反転＋772→target）。
            for tag, maps_772_to_a, vv_target in (
                    ("771", up772, a771),
                    ("770", maps_from_772["770"], vv_by_tag["770"]),
                    ("769", maps_from_772["769"], vv_by_tag["769"]),
                    ("768", maps_from_772["768"], vv_by_tag["768"]),
                    ("767", maps_from_772["767"], vv_by_tag["767"]),
                    ("766", maps_from_772["766"], vv_by_tag["766"]),
                    ("765", maps_from_772["765"], b),
                    ("763", maps_from_772["763"], vv_by_tag["763"]),
                    ("764", maps_from_772["764"], vv_by_tag["764"])):
                table = sound_name_table_mid_to_target(
                    maps773, {"sounds": maps_772_to_a["sounds"]}, a773,
                    vv_target, UP_773_CORRECTIONS.get("sounds"), 1442)
                lines = [f"    private static final String[][] SOUND_NAME_FALLBACKS_773_TO_{tag} = {{"]
                for k, v in sorted(table.items()):
                    lines.append(f'            {{"{k}", "{v}"}},')
                lines.append("    };")
                out.append("\n".join(lines))
                print(f"[773] sound name table 773->{tag}: {len(table)} entries")
            # 771→773：772・773 とも改名・削除が無いため代替は空。
            # （SOUND_FALLBACK_772 と SOUND_FALLBACK_773）。
            table_771_to_773 = dict(SOUND_FALLBACK_772)
            table_771_to_773.update(SOUND_FALLBACK_773)
            lines = ["    private static final String[][] SOUND_NAME_FALLBACKS_771_TO_773 = {"]
            for k, v in sorted(table_771_to_773.items()):
                lines.append(f'            {{"{k}", "{v}"}},')
            lines.append("    };")
            out.append("\n".join(lines))
            # 770→773：771で削除された 4件の代替の合成
            # （SOUND_FALLBACK_771 と SOUND_FALLBACK_772 と SOUND_FALLBACK_773）。
            table_770_to_773 = dict(SOUND_FALLBACK_771)
            table_770_to_773.update(table_771_to_773)
            lines = ["    private static final String[][] SOUND_NAME_FALLBACKS_770_TO_773 = {"]
            for k, v in sorted(table_770_to_773.items()):
                lines.append(f'            {{"{k}", "{v}"}},')
            lines.append("    };")
            out.append("\n".join(lines))
            # 769→773：770で削除された wolf.howl の代替と 771で削除された
            # 4件の代替の合成（SOUND_FALLBACK_770 と table_770_to_773）。
            table_769_to_773 = dict(SOUND_FALLBACK_770)
            table_769_to_773.update(table_770_to_773)
            lines = ["    private static final String[][] SOUND_NAME_FALLBACKS_769_TO_773 = {"]
            for k, v in sorted(table_769_to_773.items()):
                lines.append(f'            {{"{k}", "{v}"}},')
            lines.append("    };")
            out.append("\n".join(lines))
            # 768→773：767→768 の改名（ヤギの角笛の音2件）も合成。
            table_768_to_773 = dict(SOUND_FALLBACK_768)
            table_768_to_773.update(table_769_to_773)
            lines = ["    private static final String[][] SOUND_NAME_FALLBACKS_768_TO_773 = {"]
            for k, v in sorted(table_768_to_773.items()):
                lines.append(f'            {{"{k}", "{v}"}},')
            lines.append("    };")
            out.append("\n".join(lines))
            # 767→773：768→773 と同じ和集合。
            lines = ["    private static final String[][] SOUND_NAME_FALLBACKS_767_TO_773 = {"]
            for k, v in sorted(table_768_to_773.items()):
                lines.append(f'            {{"{k}", "{v}"}},')
            lines.append("    };")
            out.append("\n".join(lines))
            # 766→773：766→767 の改名（trial_spawner の音2件）も合成。
            table_766_to_773 = dict(SOUND_FALLBACK_767)
            table_766_to_773.update(table_768_to_773)
            lines = ["    private static final String[][] SOUND_NAME_FALLBACKS_766_TO_773 = {"]
            for k, v in sorted(table_766_to_773.items()):
                lines.append(f'            {{"{k}", "{v}"}},')
            lines.append("    };")
            out.append("\n".join(lines))
            print(f"[773] sound name table 766->773: {len(table_766_to_773)} entries")

            # 離れた組み合わせの合成表（763↔773、764↔773、765↔773、766↔773、767↔773、768↔773、769↔773、770↔773、771↔773、772↔773）。
            composed_all = {}
            for tag, maps_a_to_772, maps_772_to_a, vv_a, direction in (
                    ("763_TO_773", maps_to_772["763"], maps_from_772["763"], vv_by_tag["763"], "up"),
                    ("773_TO_763", maps_to_772["763"], maps_from_772["763"], vv_by_tag["763"], "down"),
                    ("764_TO_773", maps_to_772["764"], maps_from_772["764"], vv_by_tag["764"], "up"),
                    ("773_TO_764", maps_to_772["764"], maps_from_772["764"], vv_by_tag["764"], "down"),
                    ("765_TO_773", maps_to_772["765"], maps_from_772["765"], b, "up"),
                    ("773_TO_765", maps_to_772["765"], maps_from_772["765"], b, "down"),
                    ("766_TO_773", maps_to_772["766"], maps_from_772["766"], vv_by_tag["766"], "up"),
                    ("773_TO_766", maps_to_772["766"], maps_from_772["766"], vv_by_tag["766"], "down"),
                    ("767_TO_773", maps_to_772["767"], maps_from_772["767"], vv_by_tag["767"], "up"),
                    ("773_TO_767", maps_to_772["767"], maps_from_772["767"], vv_by_tag["767"], "down"),
                    ("768_TO_773", maps_to_772["768"], maps_from_772["768"], vv_by_tag["768"], "up"),
                    ("773_TO_768", maps_to_772["768"], maps_from_772["768"], vv_by_tag["768"], "down"),
                    ("769_TO_773", maps_to_772["769"], maps_from_772["769"], vv_by_tag["769"], "up"),
                    ("773_TO_769", maps_to_772["769"], maps_from_772["769"], vv_by_tag["769"], "down"),
                    ("770_TO_773", maps_to_772["770"], maps_from_772["770"], a770, "up"),
                    ("773_TO_770", maps_to_772["770"], maps_from_772["770"], a770, "down"),
                    ("771_TO_773", maps_to_772["771"], maps_from_772["771"], a771, "up"),
                    ("773_TO_771", maps_to_772["771"], maps_from_772["771"], a771, "down"),
                    ("772_TO_773", identity772, identity772, a772, "up"),
                    ("773_TO_772", identity772, identity772, a772, "down")):
                composed_all[tag] = emit_composed_773(
                    out, tag, maps_a_to_772, maps_772_to_a, vv_a,
                    maps773, a773, a772, direction)
                print(f"[773] composed {tag}: "
                      + ", ".join(f"{k}={len(v)}" for k, v in composed_all[tag].items()))
            with open(data_dir / "maps_composed_773.json", "w", encoding="utf-8") as f:
                json.dump({tag: {kind: {str(k): v for k, v in table.items()}
                                 for kind, table in comp.items()}
                           for tag, comp in composed_all.items()}, f)

            # 属性の表（protocol.json の Update Attributes の key mapper
            # から名前順で作る。772 は35件、773 は35件。改名は無く、
            # 新規も削除も無いため両向きとも恒等写像）。
            proto772 = load(data_dir / "mc-1.21.8-protocol.json")
            proto773 = load(data_dir / "mc-1.21.9-protocol.json")
            attrs772 = attribute_names(proto772)
            attrs773 = attribute_names(proto773)
            renamed = {n for n in attrs772 if n not in attrs773
                       and ATTRIBUTE_RENAME_773.get(n) not in attrs773}
            if renamed:
                print(f"[773] attributes: unexpected renames: {sorted(renamed)}",
                      file=sys.stderr)
                failures.append(("attributes-rename", sorted(renamed)))
            attr_772_to_773 = {}
            for i, n in enumerate(attrs772):
                target = ATTRIBUTE_RENAME_773.get(n, n)
                attr_772_to_773[i] = attrs773.index(target) if target in attrs773 else -1
            attr_773_to_772 = {}
            for i, n in enumerate(attrs773):
                src = None
                for k, v in ATTRIBUTE_RENAME_773.items():
                    if v == n:
                        src = k
                        break
                if src is None:
                    src = n
                attr_773_to_772[i] = attrs772.index(src) if src in attrs772 else -1
            iv, ov = compress(attr_772_to_773, len(attrs772))
            out.append(java_int3("ATTRIBUTE_773_RANGES", iv))
            out.append(java_int2("ATTRIBUTE_773_OVERRIDES", ov))
            out.append(f"    // ATTRIBUTE_773: 772 entries={len(attrs772)} "
                       f"ranges={len(iv)} overrides={len(ov)}")
            iv, ov = compress(attr_773_to_772, len(attrs773))
            out.append(java_int3("ATTRIBUTE_REVERSE_773_RANGES", iv))
            out.append(java_int2("ATTRIBUTE_REVERSE_773_OVERRIDES", ov))
            out.append(f"    // ATTRIBUTE_REVERSE_773: 773 entries={len(attrs773)} "
                       f"ranges={len(iv)} overrides={len(ov)}")
            out.append(java_string_array("ATTRIBUTE_773_NAMES", attrs773))
            print(f"[773] attributes: 772={len(attrs772)} 773={len(attrs773)} "
                  f"new={[n for n in attrs773 if n not in attrs772]}")

            # エンチャントの表（minecraft-data の enchantments.json。
            # 1.21.6 と 1.21.8 は同じ 42件・同じ名前順で恒等写像。
            # 違う場合は失敗させる。実機の Registry Data での順序は
            # 統合テストで確認すること）。
            ench772 = [e["name"] for e in load(data_dir / "enchantments-1.21.8.json")]
            ench773 = [e["name"] for e in load(data_dir / "enchantments-1.21.9.json")]
            if ench772 != ench773:
                print(f"[773] enchantments: 772 と 773 で名前が違う: "
                      f"{[n for n in ench772 if n not in ench773]} / "
                      f"{[n for n in ench773 if n not in ench772]}", file=sys.stderr)
                failures.append(("enchantments", (ench772, ench773)))
            ench_772_to_773 = {i: i for i in range(len(ench772))}
            ench_773_to_772 = {i: i for i in range(len(ench773))}
            iv, ov = compress(ench_772_to_773, len(ench772))
            out.append(java_int3("ENCHANTMENT_773_RANGES", iv))
            out.append(java_int2("ENCHANTMENT_773_OVERRIDES", ov))
            out.append(f"    // ENCHANTMENT_773: 772 entries={len(ench772)} "
                       f"ranges={len(iv)} overrides={len(ov)}")
            iv, ov = compress(ench_773_to_772, len(ench773))
            out.append(java_int3("ENCHANTMENT_REVERSE_773_RANGES", iv))
            out.append(java_int2("ENCHANTMENT_REVERSE_773_OVERRIDES", ov))
            out.append(f"    // ENCHANTMENT_REVERSE_773: 773 entries={len(ench773)} "
                       f"ranges={len(iv)} overrides={len(ov)}")
            print(f"[773] enchantments: 772={len(ench772)} 773={len(ench773)} identity")


    if args.target == "774":
        # 773 の成果物（--target 773 の実行結果。maps773.json・
        # map_items_rev773.json・maps_composed_773.json）を読み、
        # 773→774 の隣接表と 763〜773↔774 の合成表を作る。
        # これらが無い場合は --target 773 を先に実行すること
        # （--target 773 の再実行は gen773.txt を再生成するだけ）。
        for fname in ("maps773.json", "map_items_rev773.json",
                      "maps_composed_773.json"):
            if not (data_dir / fname).exists():
                print(f"無い: {data_dir / fname}（先に --target 773 を実行すること）",
                      file=sys.stderr)
                failures.append(("missing-773-artifact", fname))
        if not failures:
            def load_int_map(raw):
                return {int(k): v for k, v in raw.items()}

            kinds = ["blockstates", "items", "entities", "sounds",
                     "particles", "blocks", "menus"]
            a773 = load(data_dir / "vv-1.21.9.json")    # 773
            a772 = load(data_dir / "vv-1.21.7.json")    # 772
            a771 = load(data_dir / "vv-1.21.6.json")    # 771
            a770 = load(data_dir / "vv-1.21.5.json")    # 770
            a774 = load(data_dir / "vv-1.21.11.json")    # 774
            blocks_12111 = load(data_dir / "1.21.11_blocks.json")
            maps773_raw = load(data_dir / "maps773.json")
            maps773 = {k: (load_int_map(v) if isinstance(v, dict)
                           and k != "sound_fallback_names" else v)
                       for k, v in maps773_raw.items()}
            rev773 = load_int_map(load(data_dir / "map_items_rev773.json"))
            comp773_raw = load(data_dir / "maps_composed_773.json")
            comp773 = {tag: {kind: load_int_map(table)
                             for kind, table in kind_tables.items()}
                       for tag, kind_tables in comp773_raw.items()}

            maps774, out774, fail774, rev774 = build_pair(
                "774", a774, a773, blocks_12111, data_dir,
                rename=RENAME_774, sound_fallbacks=SOUND_FALLBACK_774,
                particle_fallbacks=PARTICLE_FALLBACK_774,
                reverse_fallback=1, entity_fallbacks=ENTITY_FALLBACK_774,
                block_fallbacks=BLOCK_FALLBACK_774)
            failures += fail774
            out += out774
            with open(data_dir / "maps774.json", "w", encoding="utf-8") as f:
                json.dump(maps774, f)
            with open(data_dir / "map_items_rev774.json", "w", encoding="utf-8") as f:
                json.dump({str(k): v for k, v in rev774.items()}, f)

            # 774→773 の反転表（773↔774 の合成の材料）。774→773 は
            # 補正が無い（UP_774_CORRECTIONS は空）ため、ここで同じ計算を
            # するだけでよい（--target 774 のブロックと同じ）。
            up773 = {}
            for kind in ("blockstates", "entities", "sounds", "particles",
                         "blocks", "menus"):
                up773[kind] = {i: v for i, v in enumerate(
                    up773_map(maps773, kind, a773))}
            up773["items"] = rev773

            identity773 = {kind: {i: i for i in range(len(a773[kind]))}
                           for kind in kinds}
            # A→773／773→A（A は 763〜773）。763〜772 は合成表
            # （maps_composed_773.json)、773 は恒等写像。
            maps_to_773 = {}
            maps_from_773 = {}
            vv_by_tag = {}
            for tag, vv_a, up_tag, down_tag in (
                    ("763", load(data_dir / "vv-1.20.json"),
                     "763_TO_773", "773_TO_763"),
                    ("764", load(data_dir / "vv-1.20.2.json"),
                     "764_TO_773", "773_TO_764"),
                    ("765", b, "765_TO_773", "773_TO_765"),
                    ("766", load(data_dir / "vv-1.20.5.json"),
                     "766_TO_773", "773_TO_766"),
                    ("767", load(data_dir / "vv-1.21.json"),
                     "767_TO_773", "773_TO_767"),
                    ("768", load(data_dir / "vv-1.21.2.json"),
                     "768_TO_773", "773_TO_768"),
                    ("769", load(data_dir / "vv-1.21.4.json"),
                     "769_TO_773", "773_TO_769"),
                    ("770", load(data_dir / "vv-1.21.5.json"),
                     "770_TO_773", "773_TO_770"),
                    ("771", a771, "771_TO_773", "773_TO_771"),
                    ("772", a772, "772_TO_773", "773_TO_772")):
                maps_to_773[tag] = comp773[up_tag]
                maps_from_773[tag] = comp773[down_tag]
                vv_by_tag[tag] = vv_a
            maps_to_773["773"] = identity773
            maps_from_773["773"] = identity773
            vv_by_tag["773"] = a773

            # 774 の音の名前の表（Stop Sound 用）。
            # 773→774 は改名・削除無し（RENAME_774 は空）、
            # 新規はノーチラス・カメルハスク・パーチド・ゾンビノーチラス・
            # 槍関連の 67 件のため、順向きの代替表は空である。
            lines = ["    private static final String[][] SOUND_NAME_FALLBACKS_774 = {"]
            for k, v in sorted(SOUND_FALLBACK_774.items()):
                lines.append(f'            {{"{k}", "{v}"}},')
            lines.append("    };")
            out.append("\n".join(lines))
            lines = ["    private static final String[][] SOUND_NAME_FALLBACKS_773_TO_774 = {"]
            for k, v in sorted(SOUND_FALLBACK_774.items()):
                lines.append(f'            {{"{k}", "{v}"}},')
            lines.append("    };")
            out.append("\n".join(lines))
            # 774→773：数値の表（774→773 の反転）と一致させる。
            # 774 の新音 67 件は 773 に無いため、石の破壊音
            # （773 の 1484）へ落とす。
            table_774_to_773 = sound_name_table_mid_to_target(
                maps774, {"sounds": {i: i for i in range(len(a773["sounds"]))}},
                a774, a773, UP_774_CORRECTIONS.get("sounds"), 1484)
            lines = ["    private static final String[][] SOUND_NAME_FALLBACKS_774_TO_773 = {"]
            for k, v in sorted(table_774_to_773.items()):
                lines.append(f'            {{"{k}", "{v}"}},')
            lines.append("    };")
            out.append("\n".join(lines))
            print(f"[774] sound name table 774->773: {len(table_774_to_773)} entries")
            # 774→772／771／769／768／767／766／765／764／763：数値の表と一致させる
            # （774→773 の反転＋773→target）。
            for tag, maps_773_to_a, vv_target in (
                    ("772", up773, a772),
                    ("771", maps_from_773["771"], vv_by_tag["771"]),
                    ("770", maps_from_773["770"], vv_by_tag["770"]),
                    ("769", maps_from_773["769"], vv_by_tag["769"]),
                    ("768", maps_from_773["768"], vv_by_tag["768"]),
                    ("767", maps_from_773["767"], vv_by_tag["767"]),
                    ("766", maps_from_773["766"], vv_by_tag["766"]),
                    ("765", maps_from_773["765"], b),
                    ("763", maps_from_773["763"], vv_by_tag["763"]),
                    ("764", maps_from_773["764"], vv_by_tag["764"])):
                table = sound_name_table_mid_to_target(
                    maps774, {"sounds": maps_773_to_a["sounds"]}, a774,
                    vv_target, UP_774_CORRECTIONS.get("sounds"), 1484)
                lines = [f"    private static final String[][] SOUND_NAME_FALLBACKS_774_TO_{tag} = {{"]
                for k, v in sorted(table.items()):
                    lines.append(f'            {{"{k}", "{v}"}},')
                lines.append("    };")
                out.append("\n".join(lines))
                print(f"[774] sound name table 774->{tag}: {len(table)} entries")
            # 772→774：773・774 とも改名・削除が無いため代替は空。
            # （SOUND_FALLBACK_773 と SOUND_FALLBACK_774）。
            table_772_to_774 = dict(SOUND_FALLBACK_773)
            table_772_to_774.update(SOUND_FALLBACK_774)
            lines = ["    private static final String[][] SOUND_NAME_FALLBACKS_772_TO_774 = {"]
            for k, v in sorted(table_772_to_774.items()):
                lines.append(f'            {{"{k}", "{v}"}},')
            lines.append("    };")
            out.append("\n".join(lines))
            # 771→774：772・773・774 とも改名・削除が無いため代替は空。
            # （SOUND_FALLBACK_772 と SOUND_FALLBACK_773 と SOUND_FALLBACK_774）。
            table_771_to_774 = dict(SOUND_FALLBACK_772)
            table_771_to_774.update(table_772_to_774)
            lines = ["    private static final String[][] SOUND_NAME_FALLBACKS_771_TO_774 = {"]
            for k, v in sorted(table_771_to_774.items()):
                lines.append(f'            {{"{k}", "{v}"}},')
            lines.append("    };")
            out.append("\n".join(lines))
            # 770→774：771で削除された 4件の代替の合成
            # （SOUND_FALLBACK_771 と table_771_to_774）。
            table_770_to_774 = dict(SOUND_FALLBACK_771)
            table_770_to_774.update(table_771_to_774)
            lines = ["    private static final String[][] SOUND_NAME_FALLBACKS_770_TO_774 = {"]
            for k, v in sorted(table_770_to_774.items()):
                lines.append(f'            {{"{k}", "{v}"}},')
            lines.append("    };")
            out.append("\n".join(lines))
            # 769→774：770で削除された wolf.howl の代替と 772で削除された
            # 4件の代替の合成（SOUND_FALLBACK_770 と table_770_to_774）。
            table_769_to_774 = dict(SOUND_FALLBACK_770)
            table_769_to_774.update(table_770_to_774)
            lines = ["    private static final String[][] SOUND_NAME_FALLBACKS_769_TO_774 = {"]
            for k, v in sorted(table_769_to_774.items()):
                lines.append(f'            {{"{k}", "{v}"}},')
            lines.append("    };")
            out.append("\n".join(lines))
            # 768→774：767→768 の改名（ヤギの角笛の音2件）も合成。
            table_768_to_774 = dict(SOUND_FALLBACK_768)
            table_768_to_774.update(table_769_to_774)
            lines = ["    private static final String[][] SOUND_NAME_FALLBACKS_768_TO_774 = {"]
            for k, v in sorted(table_768_to_774.items()):
                lines.append(f'            {{"{k}", "{v}"}},')
            lines.append("    };")
            out.append("\n".join(lines))
            # 767→774：768→774 と同じ和集合。
            lines = ["    private static final String[][] SOUND_NAME_FALLBACKS_767_TO_774 = {"]
            for k, v in sorted(table_768_to_774.items()):
                lines.append(f'            {{"{k}", "{v}"}},')
            lines.append("    };")
            out.append("\n".join(lines))
            # 766→774：766→767 の改名（trial_spawner の音2件）も合成。
            table_766_to_774 = dict(SOUND_FALLBACK_767)
            table_766_to_774.update(table_768_to_774)
            lines = ["    private static final String[][] SOUND_NAME_FALLBACKS_766_TO_774 = {"]
            for k, v in sorted(table_766_to_774.items()):
                lines.append(f'            {{"{k}", "{v}"}},')
            lines.append("    };")
            out.append("\n".join(lines))
            print(f"[774] sound name table 766->774: {len(table_766_to_774)} entries")

            # 離れた組み合わせの合成表（763↔774、764↔774、765↔774、766↔774、767↔774、768↔774、769↔774、770↔774、772↔774、773↔774）。
            composed_all = {}
            for tag, maps_a_to_773, maps_773_to_a, vv_a, direction in (
                    ("763_TO_774", maps_to_773["763"], maps_from_773["763"], vv_by_tag["763"], "up"),
                    ("774_TO_763", maps_to_773["763"], maps_from_773["763"], vv_by_tag["763"], "down"),
                    ("764_TO_774", maps_to_773["764"], maps_from_773["764"], vv_by_tag["764"], "up"),
                    ("774_TO_764", maps_to_773["764"], maps_from_773["764"], vv_by_tag["764"], "down"),
                    ("765_TO_774", maps_to_773["765"], maps_from_773["765"], b, "up"),
                    ("774_TO_765", maps_to_773["765"], maps_from_773["765"], b, "down"),
                    ("766_TO_774", maps_to_773["766"], maps_from_773["766"], vv_by_tag["766"], "up"),
                    ("774_TO_766", maps_to_773["766"], maps_from_773["766"], vv_by_tag["766"], "down"),
                    ("767_TO_774", maps_to_773["767"], maps_from_773["767"], vv_by_tag["767"], "up"),
                    ("774_TO_767", maps_to_773["767"], maps_from_773["767"], vv_by_tag["767"], "down"),
                    ("768_TO_774", maps_to_773["768"], maps_from_773["768"], vv_by_tag["768"], "up"),
                    ("774_TO_768", maps_to_773["768"], maps_from_773["768"], vv_by_tag["768"], "down"),
                    ("769_TO_774", maps_to_773["769"], maps_from_773["769"], vv_by_tag["769"], "up"),
                    ("774_TO_769", maps_to_773["769"], maps_from_773["769"], vv_by_tag["769"], "down"),
                    ("770_TO_774", maps_to_773["770"], maps_from_773["770"], a770, "up"),
                    ("774_TO_770", maps_to_773["770"], maps_from_773["770"], a770, "down"),
                    ("771_TO_774", maps_to_773["771"], maps_from_773["771"], a771, "up"),
                    ("774_TO_771", maps_to_773["771"], maps_from_773["771"], a771, "down"),
                    ("772_TO_774", maps_to_773["772"], maps_from_773["772"], a772, "up"),
                    ("774_TO_772", maps_to_773["772"], maps_from_773["772"], a772, "down"),
                    ("773_TO_774", identity773, identity773, a773, "up"),
                    ("774_TO_773", identity773, identity773, a773, "down")):
                composed_all[tag] = emit_composed_774(
                    out, tag, maps_a_to_773, maps_773_to_a, vv_a,
                    maps774, a774, a773, direction)
                print(f"[774] composed {tag}: "
                      + ", ".join(f"{k}={len(v)}" for k, v in composed_all[tag].items()))
            with open(data_dir / "maps_composed_774.json", "w", encoding="utf-8") as f:
                json.dump({tag: {kind: {str(k): v for k, v in table.items()}
                                 for kind, table in comp.items()}
                           for tag, comp in composed_all.items()}, f)

            # 属性の表（protocol.json の Update Attributes の key mapper
            # から名前順で作る。773 は35件、774 は35件。改名は無く、
            # 新規も削除も無いため両向きとも恒等写像）。
            proto773 = load(data_dir / "mc-1.21.9-protocol.json")
            proto774 = load(data_dir / "mc-1.21.11-protocol.json")
            attrs773 = attribute_names(proto773)
            attrs774 = attribute_names(proto774)
            renamed = {n for n in attrs773 if n not in attrs774
                       and ATTRIBUTE_RENAME_774.get(n) not in attrs774}
            if renamed:
                print(f"[774] attributes: unexpected renames: {sorted(renamed)}",
                      file=sys.stderr)
                failures.append(("attributes-rename", sorted(renamed)))
            attr_773_to_774 = {}
            for i, n in enumerate(attrs773):
                target = ATTRIBUTE_RENAME_774.get(n, n)
                attr_773_to_774[i] = attrs774.index(target) if target in attrs774 else -1
            attr_774_to_773 = {}
            for i, n in enumerate(attrs774):
                src = None
                for k, v in ATTRIBUTE_RENAME_774.items():
                    if v == n:
                        src = k
                        break
                if src is None:
                    src = n
                attr_774_to_773[i] = attrs773.index(src) if src in attrs773 else -1
            iv, ov = compress(attr_773_to_774, len(attrs773))
            out.append(java_int3("ATTRIBUTE_774_RANGES", iv))
            out.append(java_int2("ATTRIBUTE_774_OVERRIDES", ov))
            out.append(f"    // ATTRIBUTE_774: 773 entries={len(attrs773)} "
                       f"ranges={len(iv)} overrides={len(ov)}")
            iv, ov = compress(attr_774_to_773, len(attrs774))
            out.append(java_int3("ATTRIBUTE_REVERSE_774_RANGES", iv))
            out.append(java_int2("ATTRIBUTE_REVERSE_774_OVERRIDES", ov))
            out.append(f"    // ATTRIBUTE_REVERSE_774: 774 entries={len(attrs774)} "
                       f"ranges={len(iv)} overrides={len(ov)}")
            out.append(java_string_array("ATTRIBUTE_774_NAMES", attrs774))
            print(f"[774] attributes: 773={len(attrs773)} 774={len(attrs774)} "
                  f"new={[n for n in attrs774 if n not in attrs773]}")

            # エンチャントの表（minecraft-data の enchantments.json。
            # 773 は 42件、774 は 43件。lunge が 21 に挿入され
            # lure 以降が +1 ずれる。lunge 以外の差分（削除・改名・
            # 別位置の挿入）があれば失敗させる。実機の Registry Data
            # での順序は統合テストで確認すること）。
            ench773 = [e["name"] for e in load(data_dir / "enchantments-1.21.9.json")]
            ench774 = [e["name"] for e in load(data_dir / "enchantments-1.21.11.json")]
            unexpected = ([n for n in ench773 if n not in ench774]
                          + [n for n in ench774 if n not in ench773 and n != "lunge"])
            if unexpected:
                print(f"[774] enchantments: 773 と 774 で想定外の差分: {unexpected}",
                      file=sys.stderr)
                failures.append(("enchantments", (ench773, ench774)))
            ench_773_to_774 = {i: ench774.index(n) for i, n in enumerate(ench773)}
            # lunge（774 の 21）は 773 に無いため DROP（-1）。
            ench_774_to_773 = {i: (ench773.index(n) if n in ench773 else -1)
                               for i, n in enumerate(ench774)}
            iv, ov = compress(ench_773_to_774, len(ench773))
            out.append(java_int3("ENCHANTMENT_774_RANGES", iv))
            out.append(java_int2("ENCHANTMENT_774_OVERRIDES", ov))
            out.append(f"    // ENCHANTMENT_774: 773 entries={len(ench773)} "
                       f"ranges={len(iv)} overrides={len(ov)}")
            iv, ov = compress(ench_774_to_773, len(ench774))
            out.append(java_int3("ENCHANTMENT_REVERSE_774_RANGES", iv))
            out.append(java_int2("ENCHANTMENT_REVERSE_774_OVERRIDES", ov))
            out.append(f"    // ENCHANTMENT_REVERSE_774: 774 entries={len(ench774)} "
                       f"ranges={len(iv)} overrides={len(ov)}")
            print(f"[774] enchantments: 773={len(ench773)} 774={len(ench774)} "
                  f"lunge=21 new, 21+ shifted +1")


    if args.target == "775":
        # 774 の成果物（--target 774 の実行結果。maps774.json・
        # map_items_rev774.json・maps_composed_774.json）を読み、
        # 774→775 の隣接表と 763〜774↔775 の合成表を作る。
        # これらが無い場合は --target 774 を先に実行すること
        # （--target 774 の再実行は gen774.txt を再生成するだけ）。
        for fname in ("maps774.json", "map_items_rev774.json",
                      "maps_composed_774.json"):
            if not (data_dir / fname).exists():
                print(f"無い: {data_dir / fname}（先に --target 774 を実行すること）",
                      file=sys.stderr)
                failures.append(("missing-774-artifact", fname))
        if not failures:
            def load_int_map(raw):
                return {int(k): v for k, v in raw.items()}

            kinds = ["blockstates", "items", "entities", "sounds",
                     "particles", "blocks", "menus"]
            a774 = load(data_dir / "vv-1.21.11.json")    # 774
            a773 = load(data_dir / "vv-1.21.9.json")    # 773
            a772 = load(data_dir / "vv-1.21.7.json")    # 772
            a771 = load(data_dir / "vv-1.21.6.json")    # 771
            a770 = load(data_dir / "vv-1.21.5.json")    # 770
            a775 = load(data_dir / "vv-26.1.json")    # 775
            blocks_261 = load(data_dir / "26.1_blocks.json")
            maps774_raw = load(data_dir / "maps774.json")
            maps774 = {k: (load_int_map(v) if isinstance(v, dict)
                           and k != "sound_fallback_names" else v)
                       for k, v in maps774_raw.items()}
            rev774 = load_int_map(load(data_dir / "map_items_rev774.json"))
            comp774_raw = load(data_dir / "maps_composed_774.json")
            comp774 = {tag: {kind: load_int_map(table)
                             for kind, table in kind_tables.items()}
                       for tag, kind_tables in comp774_raw.items()}

            maps775, out775, fail775, rev775 = build_pair(
                "775", a775, a774, blocks_261, data_dir,
                rename=RENAME_775, sound_fallbacks=SOUND_FALLBACK_775,
                particle_fallbacks=PARTICLE_FALLBACK_775,
                reverse_fallback=1, entity_fallbacks=ENTITY_FALLBACK_775,
                block_fallbacks=BLOCK_FALLBACK_775)
            failures += fail775
            out += out775
            with open(data_dir / "maps775.json", "w", encoding="utf-8") as f:
                json.dump(maps775, f)
            with open(data_dir / "map_items_rev775.json", "w", encoding="utf-8") as f:
                json.dump({str(k): v for k, v in rev775.items()}, f)

            # 775→774 の反転表（774↔775 の合成の材料）。775→774 は
            # 補正が無い（UP_775_CORRECTIONS は空）ため、ここで同じ計算を
            # するだけでよい（--target 775 のブロックと同じ）。
            up774 = {}
            for kind in ("blockstates", "entities", "sounds", "particles",
                         "blocks", "menus"):
                up774[kind] = {i: v for i, v in enumerate(
                    up774_map(maps774, kind, a774))}
            up774["items"] = rev774

            identity774 = {kind: {i: i for i in range(len(a774[kind]))}
                           for kind in kinds}
            # A→774／774→A（A は 763〜774）。763〜773 は合成表
            # （maps_composed_774.json)、774 は恒等写像。
            maps_to_774 = {}
            maps_from_774 = {}
            vv_by_tag = {}
            for tag, vv_a, up_tag, down_tag in (
                    ("763", load(data_dir / "vv-1.20.json"),
                     "763_TO_774", "774_TO_763"),
                    ("764", load(data_dir / "vv-1.20.2.json"),
                     "764_TO_774", "774_TO_764"),
                    ("765", b, "765_TO_774", "774_TO_765"),
                    ("766", load(data_dir / "vv-1.20.5.json"),
                     "766_TO_774", "774_TO_766"),
                    ("767", load(data_dir / "vv-1.21.json"),
                     "767_TO_774", "774_TO_767"),
                    ("768", load(data_dir / "vv-1.21.2.json"),
                     "768_TO_774", "774_TO_768"),
                    ("769", load(data_dir / "vv-1.21.4.json"),
                     "769_TO_774", "774_TO_769"),
                    ("770", a770,
                     "770_TO_774", "774_TO_770"),
                    ("771", a771, "771_TO_774", "774_TO_771"),
                    ("772", a772, "772_TO_774", "774_TO_772")):
                maps_to_774[tag] = comp774[up_tag]
                maps_from_774[tag] = comp774[down_tag]
                vv_by_tag[tag] = vv_a
            maps_to_774["774"] = identity774
            maps_from_774["774"] = identity774
            vv_by_tag["774"] = a774
            # 773→774 は隣接表（maps774）、774→773 はその反転（up774）。
            maps_to_774["773"] = maps774
            maps_from_774["773"] = up774
            vv_by_tag["773"] = a773

            # 775 の音の名前の表（Stop Sound 用）。
            # 774→775 は改名・削除無し（RENAME_775 は空）、
            # 新規は金のタンポポ・赤ちゃん動物の声・トランペットの音符ブロック
            # 関連の 64 件のため、順向きの代替表は空である。
            lines = ["    private static final String[][] SOUND_NAME_FALLBACKS_775 = {"]
            for k, v in sorted(SOUND_FALLBACK_775.items()):
                lines.append(f'            {{"{k}", "{v}"}},')
            lines.append("    };")
            out.append("\n".join(lines))
            lines = ["    private static final String[][] SOUND_NAME_FALLBACKS_774_TO_775 = {"]
            for k, v in sorted(SOUND_FALLBACK_775.items()):
                lines.append(f'            {{"{k}", "{v}"}},')
            lines.append("    };")
            out.append("\n".join(lines))
            # 775→774：数値の表（775→774 の反転）と一致させる。
            # 775 の新音 64 件は 774 に無いため、石の破壊音
            # （774 の 1535）へ落とす。
            table_775_to_774 = sound_name_table_mid_to_target(
                maps775, {"sounds": {i: i for i in range(len(a774["sounds"]))}},
                a775, a774, UP_775_CORRECTIONS.get("sounds"), 1535)
            lines = ["    private static final String[][] SOUND_NAME_FALLBACKS_775_TO_774 = {"]
            for k, v in sorted(table_775_to_774.items()):
                lines.append(f'            {{"{k}", "{v}"}},')
            lines.append("    };")
            out.append("\n".join(lines))
            print(f"[775] sound name table 775->774: {len(table_775_to_774)} entries")
            # 775→773／772／771／770／769／768／767／766／765／764／763：数値の表と一致させる
            # （775→774 の反転＋774→target）。
            for tag, maps_774_to_a, vv_target in (
                    ("773", up774, a773),
                    ("772", maps_from_774["772"], vv_by_tag["772"]),
                    ("771", maps_from_774["771"], vv_by_tag["771"]),
                    ("770", maps_from_774["770"], vv_by_tag["770"]),
                    ("769", maps_from_774["769"], vv_by_tag["769"]),
                    ("768", maps_from_774["768"], vv_by_tag["768"]),
                    ("767", maps_from_774["767"], vv_by_tag["767"]),
                    ("766", maps_from_774["766"], vv_by_tag["766"]),
                    ("765", maps_from_774["765"], b),
                    ("763", maps_from_774["763"], vv_by_tag["763"]),
                    ("764", maps_from_774["764"], vv_by_tag["764"])):
                table = sound_name_table_mid_to_target(
                    maps775, {"sounds": maps_774_to_a["sounds"]}, a775,
                    vv_target, UP_775_CORRECTIONS.get("sounds"), 1535)
                lines = [f"    private static final String[][] SOUND_NAME_FALLBACKS_775_TO_{tag} = {{"]
                for k, v in sorted(table.items()):
                    lines.append(f'            {{"{k}", "{v}"}},')
                lines.append("    };")
                out.append("\n".join(lines))
                print(f"[775] sound name table 775->{tag}: {len(table)} entries")
            # 773→775：774・775 とも改名・削除が無いため代替は空。
            # （SOUND_FALLBACK_774 と SOUND_FALLBACK_775）。
            table_773_to_775 = dict(SOUND_FALLBACK_774)
            table_773_to_775.update(SOUND_FALLBACK_775)
            lines = ["    private static final String[][] SOUND_NAME_FALLBACKS_773_TO_775 = {"]
            for k, v in sorted(table_773_to_775.items()):
                lines.append(f'            {{"{k}", "{v}"}},')
            lines.append("    };")
            out.append("\n".join(lines))
            # 772→775：773・774・775 とも改名・削除が無いため代替は空。
            # （SOUND_FALLBACK_773 と SOUND_FALLBACK_774 と SOUND_FALLBACK_775）。
            table_772_to_775 = dict(SOUND_FALLBACK_773)
            table_772_to_775.update(table_773_to_775)
            lines = ["    private static final String[][] SOUND_NAME_FALLBACKS_772_TO_775 = {"]
            for k, v in sorted(table_772_to_775.items()):
                lines.append(f'            {{"{k}", "{v}"}},')
            lines.append("    };")
            out.append("\n".join(lines))
            # 771→775：772で削除されたものは無く、代替は空
            # （SOUND_FALLBACK_772 は空）。
            table_771_to_775 = dict(SOUND_FALLBACK_772)
            table_771_to_775.update(table_772_to_775)
            lines = ["    private static final String[][] SOUND_NAME_FALLBACKS_771_TO_775 = {"]
            for k, v in sorted(table_771_to_775.items()):
                lines.append(f'            {{"{k}", "{v}"}},')
            lines.append("    };")
            out.append("\n".join(lines))
            # 770→775：771で改名された 4件の代替の合成
            # （SOUND_FALLBACK_771 と table_771_to_775）。
            table_770_to_775 = dict(SOUND_FALLBACK_771)
            table_770_to_775.update(table_771_to_775)
            lines = ["    private static final String[][] SOUND_NAME_FALLBACKS_770_TO_775 = {"]
            for k, v in sorted(table_770_to_775.items()):
                lines.append(f'            {{"{k}", "{v}"}},')
            lines.append("    };")
            out.append("\n".join(lines))
            # 769→775：770で削除された wolf.howl の代替と table_770_to_775 の合成
            # （SOUND_FALLBACK_770 と table_770_to_775）。
            table_769_to_775 = dict(SOUND_FALLBACK_770)
            table_769_to_775.update(table_770_to_775)
            lines = ["    private static final String[][] SOUND_NAME_FALLBACKS_769_TO_775 = {"]
            for k, v in sorted(table_769_to_775.items()):
                lines.append(f'            {{"{k}", "{v}"}},')
            lines.append("    };")
            out.append("\n".join(lines))
            # 768→775：767→768 の改名（ヤギの角笛の音2件）も合成。
            table_768_to_775 = dict(SOUND_FALLBACK_768)
            table_768_to_775.update(table_769_to_775)
            lines = ["    private static final String[][] SOUND_NAME_FALLBACKS_768_TO_775 = {"]
            for k, v in sorted(table_768_to_775.items()):
                lines.append(f'            {{"{k}", "{v}"}},')
            lines.append("    };")
            out.append("\n".join(lines))
            # 767→775：768→775 と同じ和集合。
            lines = ["    private static final String[][] SOUND_NAME_FALLBACKS_767_TO_775 = {"]
            for k, v in sorted(table_768_to_775.items()):
                lines.append(f'            {{"{k}", "{v}"}},')
            lines.append("    };")
            out.append("\n".join(lines))

            # 離れた組み合わせの合成表（763↔775、764↔775、765↔775、766↔775、767↔775、768↔775、769↔775、770↔775、771↔775、772↔775、773↔775、774↔775）。
            composed_all = {}
            for tag, maps_a_to_774, maps_774_to_a, vv_a, direction in (
                    ("763_TO_775", maps_to_774["763"], maps_from_774["763"], vv_by_tag["763"], "up"),
                    ("775_TO_763", maps_to_774["763"], maps_from_774["763"], vv_by_tag["763"], "down"),
                    ("764_TO_775", maps_to_774["764"], maps_from_774["764"], vv_by_tag["764"], "up"),
                    ("775_TO_764", maps_to_774["764"], maps_from_774["764"], vv_by_tag["764"], "down"),
                    ("765_TO_775", maps_to_774["765"], maps_from_774["765"], b, "up"),
                    ("775_TO_765", maps_to_774["765"], maps_from_774["765"], b, "down"),
                    ("766_TO_775", maps_to_774["766"], maps_from_774["766"], vv_by_tag["766"], "up"),
                    ("775_TO_766", maps_to_774["766"], maps_from_774["766"], vv_by_tag["766"], "down"),
                    ("767_TO_775", maps_to_774["767"], maps_from_774["767"], vv_by_tag["767"], "up"),
                    ("775_TO_767", maps_to_774["767"], maps_from_774["767"], vv_by_tag["767"], "down"),
                    ("768_TO_775", maps_to_774["768"], maps_from_774["768"], vv_by_tag["768"], "up"),
                    ("775_TO_768", maps_to_774["768"], maps_from_774["768"], vv_by_tag["768"], "down"),
                    ("769_TO_775", maps_to_774["769"], maps_from_774["769"], vv_by_tag["769"], "up"),
                    ("775_TO_769", maps_to_774["769"], maps_from_774["769"], vv_by_tag["769"], "down"),
                    ("770_TO_775", maps_to_774["770"], maps_from_774["770"], a770, "up"),
                    ("775_TO_770", maps_to_774["770"], maps_from_774["770"], a770, "down"),
                    ("771_TO_775", maps_to_774["771"], maps_from_774["771"], a771, "up"),
                    ("775_TO_771", maps_to_774["771"], maps_from_774["771"], a771, "down"),
                    ("772_TO_775", maps_to_774["772"], maps_from_774["772"], a772, "up"),
                    ("775_TO_772", maps_to_774["772"], maps_from_774["772"], a772, "down"),
                    ("773_TO_775", maps_to_774["773"], maps_from_774["773"], vv_by_tag["773"], "up"),
                    ("775_TO_773", maps_to_774["773"], maps_from_774["773"], vv_by_tag["773"], "down"),
                    ("774_TO_775", identity774, identity774, a774, "up"),
                    ("775_TO_774", identity774, identity774, a774, "down")):
                composed_all[tag] = emit_composed_775(
                    out, tag, maps_a_to_774, maps_774_to_a, vv_a,
                    maps775, a775, a774, direction)
                print(f"[775] composed {tag}: "
                      + ", ".join(f"{k}={len(v)}" for k, v in composed_all[tag].items()))
            with open(data_dir / "maps_composed_775.json", "w", encoding="utf-8") as f:
                json.dump({tag: {kind: {str(k): v for k, v in table.items()}
                                 for kind, table in comp.items()}
                           for tag, comp in composed_all.items()}, f)

            # 属性の表（protocol.json の Update Attributes の key mapper
            # から名前順で作る。774 は35件、775 は35件。改名は無く、
            # 新規も削除も無いため両向きとも恒等写像）。
            proto774 = load(data_dir / "mc-1.21.11-protocol.json")
            proto775 = load(data_dir / "mc-26.1-protocol.json")
            attrs774 = attribute_names(proto774)
            attrs775 = attribute_names(proto775)
            renamed = {n for n in attrs774 if n not in attrs775
                       and ATTRIBUTE_RENAME_775.get(n) not in attrs775}
            if renamed:
                print(f"[775] attributes: unexpected renames: {sorted(renamed)}",
                      file=sys.stderr)
                failures.append(("attributes-rename", sorted(renamed)))
            attr_774_to_775 = {}
            for i, n in enumerate(attrs774):
                target = ATTRIBUTE_RENAME_775.get(n, n)
                attr_774_to_775[i] = attrs775.index(target) if target in attrs775 else -1
            attr_775_to_774 = {}
            for i, n in enumerate(attrs775):
                src = None
                for k, v in ATTRIBUTE_RENAME_775.items():
                    if v == n:
                        src = k
                        break
                if src is None:
                    src = n
                attr_775_to_774[i] = attrs774.index(src) if src in attrs774 else -1
            iv, ov = compress(attr_774_to_775, len(attrs774))
            out.append(java_int3("ATTRIBUTE_775_RANGES", iv))
            out.append(java_int2("ATTRIBUTE_775_OVERRIDES", ov))
            out.append(f"    // ATTRIBUTE_775: 774 entries={len(attrs774)} "
                       f"ranges={len(iv)} overrides={len(ov)}")
            iv, ov = compress(attr_775_to_774, len(attrs775))
            out.append(java_int3("ATTRIBUTE_REVERSE_775_RANGES", iv))
            out.append(java_int2("ATTRIBUTE_REVERSE_775_OVERRIDES", ov))
            out.append(f"    // ATTRIBUTE_REVERSE_775: 775 entries={len(attrs775)} "
                       f"ranges={len(iv)} overrides={len(ov)}")
            out.append(java_string_array("ATTRIBUTE_775_NAMES", attrs775))
            print(f"[775] attributes: 774={len(attrs774)} 775={len(attrs775)} "
                  f"new={[n for n in attrs775 if n not in attrs774]}")

            # エンチャントの表（minecraft-data の enchantments.json。
            # 774 は 43件、775 は 43件で並びは同一。差分（削除・改名・
            # 別位置の挿入）があれば失敗させる。実機の Registry Data
            # での順序は統合テストで確認すること）。
            ench774 = [e["name"] for e in load(data_dir / "enchantments-1.21.11.json")]
            ench775 = [e["name"] for e in load(data_dir / "enchantments-26.1.json")]
            unexpected = ([n for n in ench774 if n not in ench775]
                          + [n for n in ench775 if n not in ench774])
            if unexpected:
                print(f"[775] enchantments: 774 と 775 で想定外の差分: {unexpected}",
                      file=sys.stderr)
                failures.append(("enchantments", (ench774, ench775)))
            ench_774_to_775 = {i: ench775.index(n) for i, n in enumerate(ench774)}
            # 774 に無いエンチャントは無いため DROP は発生しない。
            ench_775_to_774 = {i: (ench774.index(n) if n in ench774 else -1)
                               for i, n in enumerate(ench775)}
            iv, ov = compress(ench_774_to_775, len(ench774))
            out.append(java_int3("ENCHANTMENT_775_RANGES", iv))
            out.append(java_int2("ENCHANTMENT_775_OVERRIDES", ov))
            out.append(f"    // ENCHANTMENT_775: 774 entries={len(ench774)} "
                       f"ranges={len(iv)} overrides={len(ov)}")
            iv, ov = compress(ench_775_to_774, len(ench775))
            out.append(java_int3("ENCHANTMENT_REVERSE_775_RANGES", iv))
            out.append(java_int2("ENCHANTMENT_REVERSE_775_OVERRIDES", ov))
            out.append(f"    // ENCHANTMENT_REVERSE_775: 775 entries={len(ench775)} "
                       f"ranges={len(iv)} overrides={len(ov)}")
            print(f"[775] enchantments: 774={len(ench774)} 775={len(ench775)} identity")


    if failures:
        print("\n未対応があります。代替表を追加してから再実行してください:", file=sys.stderr)
        for kind, item in failures:
            print(f"  {kind}: {item}", file=sys.stderr)
        sys.exit(1)

    if args.target in ("763", "764", "both"):
        lines = ["    private static final String[][] SOUND_NAME_FALLBACKS = {"]
        for k, v in sorted(SOUND_FALLBACK.items()):
            lines.append(f'            {{"{k}", "{v}"}},')
        lines.append("    };")
        out.append("\n".join(lines))

    text = "\n".join(out) + "\n"
    if args.out:
        args.out.write_text(text, encoding="utf-8")
        print(f"Java の表を {args.out} に書き出しました。")
    else:
        sys.stdout.write(text)
    print("完了：未対応 0 件。", file=sys.stderr)


if __name__ == "__main__":
    main()
