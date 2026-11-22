#!/usr/bin/env python3
"""758（1.18.2）↔759（1.19）のレジストリ対応表を生成する。

情報源（推測で埋めない。すべて名前で突き合わせる）：
  - ViaVersion Mappings（Mojang の data-generator レポート由来。
    リストの index がレジストリ ID）：
      https://raw.githubusercontent.com/ViaVersion/Mappings/main/mappings/mapping-1.18.json （758）
      https://raw.githubusercontent.com/ViaVersion/Mappings/main/mappings/mapping-1.19.json （759）
    mapping-1.18.2.json は存在しない（1.18／1.18.1／1.18.2 は同じ
    mapping-1.18.json）。順序が本物のバニラ 1.18.2 と一致することは
    jar の data-generator ダンプ（reports/registries.json と
    reports/blocks.json）で全カテゴリ確認済み。
  - PrismarineJS minecraft-data（バイオーム・代替ブロックの既定状態）：
      data/pc/1.18/biomes.json、data/pc/1.19/biomes.json
      data/pc/1.18/blocks.json、data/pc/1.19/blocks.json（defaultState）
  - メニューは mapping-1.18.json／mapping-1.19.json に "menus" キーが
    無いため vv-1.20.json の 24 件を使う。順序は jar ダンプの
    minecraft:menu と一致することを確認済み。

対応の決め方（優先順）：
  1. 名前がそのまま一致する → その ID。
  2. ブロック状態だけ：プロパティを1個だけ落とす／足すと一致する
     （759 で葉に waterlogged が追加された）→ その ID。
     注意：mapping-1.18.json と mapping-1.19.json はプロパティの
     並び順が違う（piston_head など。集合としては同一）ため、
     比較はプロパティをソートした正規形で行う。
  3. 下の *_FALLBACK 表にある 1.19 の新要素 → 近い既存要素。
  4. それでも決まらないものがあれば「未対応」として一覧を出し、
     exit 1 で失敗する（黙って推測しない）。

使い方：
  python3 tools/gen-758-tables.py [--data-dir DIR] [--out FILE]
  --data-dir を省略すると /tmp/mcdata を使う。
  --out を省略すると RegistryTables758.java を標準出力に出す
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
# フォールバック表（759 にだけあるものを 758 の近い要素へ）
# ----------------------------------------------------------------------
# ブロック状態・ブロック：新ブロック → 758 にある近いブロック。
BLOCK_FALLBACK_759_TO_758 = {
    # マングローブ系 → オーク系。
    "mangrove_log": "oak_log",
    "stripped_mangrove_log": "stripped_oak_log",
    "mangrove_wood": "oak_wood",
    "stripped_mangrove_wood": "stripped_oak_wood",
    "mangrove_planks": "oak_planks",
    "mangrove_leaves": "oak_leaves",
    "mangrove_slab": "oak_slab",
    "mangrove_stairs": "oak_stairs",
    "mangrove_button": "oak_button",
    "mangrove_door": "oak_door",
    "mangrove_fence": "oak_fence",
    "mangrove_fence_gate": "oak_fence_gate",
    "mangrove_pressure_plate": "oak_pressure_plate",
    "mangrove_trapdoor": "oak_trapdoor",
    "mangrove_sign": "oak_sign",
    "mangrove_wall_sign": "oak_wall_sign",
    "mangrove_propagule": "oak_sapling",
    "potted_mangrove_propagule": "potted_oak_sapling",
    "mangrove_roots": "oak_log",
    "muddy_mangrove_roots": "dirt",
    # 泥系 → 土・レンガ系。
    "mud": "dirt",
    "packed_mud": "dirt",
    "mud_bricks": "bricks",
    "mud_brick_slab": "brick_slab",
    "mud_brick_stairs": "brick_stairs",
    "mud_brick_wall": "brick_wall",
    # スカルク系 → 深層岩・スカルクセンサー系。
    "sculk": "deepslate",
    "sculk_vein": "glow_lichen",
    "sculk_catalyst": "sculk_sensor",
    "sculk_shrieker": "sculk_sensor",
    "reinforced_deepslate": "deepslate",
    # フロッグライト → グロウストーン。カエルの卵 → スイレンの葉。
    "ochre_froglight": "glowstone",
    "verdant_froglight": "glowstone",
    "pearlescent_froglight": "glowstone",
    "frogspawn": "lily_pad",
}
# アイテム：新アイテム → 758 にある近いアイテム。
# ブロックの代替を流用するが、壁掛け看板と植木鉢はアイテムが無い
# （ブロックだけ）ため除く。
ITEM_FALLBACK_759_TO_758 = {k: v for k, v in BLOCK_FALLBACK_759_TO_758.items()
                            if k not in ("mangrove_wall_sign",
                                         "potted_mangrove_propagule")}
ITEM_FALLBACK_759_TO_758.update({
    # チェスト付きボート → 同じ木のボート。
    "oak_chest_boat": "oak_boat",
    "spruce_chest_boat": "spruce_boat",
    "birch_chest_boat": "birch_boat",
    "jungle_chest_boat": "jungle_boat",
    "acacia_chest_boat": "acacia_boat",
    "dark_oak_chest_boat": "dark_oak_boat",
    "mangrove_boat": "oak_boat",
    "mangrove_chest_boat": "oak_boat",
    # スポーンエッグ：アレイ→ヴェックス（小さく飛ぶ）、カエル→ウサギ
    # （跳ねる）、オタマジャクシ→ウーパールーパー（水棲の両生類に近い）、
    # ウォーデン→ラヴェジャー（大きな敵対モブ。アイアンゴーレムの
    # スポーンエッグは 758 には無いため使えない）。
    "allay_spawn_egg": "vex_spawn_egg",
    "frog_spawn_egg": "rabbit_spawn_egg",
    "tadpole_spawn_egg": "axolotl_spawn_egg",
    "warden_spawn_egg": "ravager_spawn_egg",
    "disc_fragment_5": "music_disc_cat",
    "music_disc_5": "music_disc_cat",
    "echo_shard": "amethyst_shard",
    "goat_horn": "music_disc_cat",
    "recovery_compass": "compass",
    "tadpole_bucket": "axolotl_bucket",
})
# エンティティ：新エンティティ → 758 にある近いエンティティ。
ENTITY_FALLBACK_759_TO_758 = {
    "allay": "vex",
    "chest_boat": "boat",
    "frog": "rabbit",
    "tadpole": "axolotl",
    "warden": "iron_golem",
}
# 音のフォールバック（759 → 758）。接頭辞で決まるものは下の関数で作る。
SOUND_FALLBACK_759_TO_758_EXPLICIT = {
    # フロッグライト → ガラス系の音。
    "block.froglight.break": "block.glass.break",
    "block.froglight.fall": "block.glass.fall",
    "block.froglight.hit": "block.glass.hit",
    "block.froglight.place": "block.glass.place",
    "block.froglight.step": "block.glass.step",
    # カエルの卵 → スライムブロック系の音。
    "block.frogspawn.break": "block.slime_block.break",
    "block.frogspawn.fall": "block.slime_block.fall",
    "block.frogspawn.hatch": "block.slime_block.break",
    "block.frogspawn.hit": "block.slime_block.hit",
    "block.frogspawn.place": "block.slime_block.place",
    "block.frogspawn.step": "block.slime_block.step",
    # マングローブの根 → 木材系の音。
    "block.mangrove_roots.break": "block.wood.break",
    "block.mangrove_roots.fall": "block.wood.fall",
    "block.mangrove_roots.hit": "block.wood.hit",
    "block.mangrove_roots.place": "block.wood.place",
    "block.mangrove_roots.step": "block.wood.step",
    # 泥・泥レンガ・詰まった泥 → 土（block.grass.*。土は草の音を使う）・
    # 石系の音。block.dirt.* と block.stone_bricks.* は 758 には無い。
    "block.mud.break": "block.grass.break",
    "block.mud.fall": "block.grass.fall",
    "block.mud.hit": "block.grass.hit",
    "block.mud.place": "block.grass.place",
    "block.mud.step": "block.grass.step",
    "block.mud_bricks.break": "block.stone.break",
    "block.mud_bricks.fall": "block.stone.fall",
    "block.mud_bricks.hit": "block.stone.hit",
    "block.mud_bricks.place": "block.stone.place",
    "block.mud_bricks.step": "block.stone.step",
    "block.muddy_mangrove_roots.break": "block.grass.break",
    "block.muddy_mangrove_roots.fall": "block.grass.fall",
    "block.muddy_mangrove_roots.hit": "block.grass.hit",
    "block.muddy_mangrove_roots.place": "block.grass.place",
    "block.muddy_mangrove_roots.step": "block.grass.step",
    "block.packed_mud.break": "block.grass.break",
    "block.packed_mud.fall": "block.grass.fall",
    "block.packed_mud.hit": "block.grass.hit",
    "block.packed_mud.place": "block.grass.place",
    "block.packed_mud.step": "block.grass.step",
    # スカルク系 → スカルクセンサー系の音。
    "block.sculk.break": "block.sculk_sensor.break",
    "block.sculk.charge": "block.sculk_sensor.clicking",
    "block.sculk.fall": "block.sculk_sensor.fall",
    "block.sculk.hit": "block.sculk_sensor.hit",
    "block.sculk.place": "block.sculk_sensor.place",
    "block.sculk.spread": "block.sculk_sensor.clicking_stop",
    "block.sculk.step": "block.sculk_sensor.step",
    "block.sculk_catalyst.bloom": "block.sculk_sensor.clicking",
    "block.sculk_catalyst.break": "block.sculk_sensor.break",
    "block.sculk_catalyst.fall": "block.sculk_sensor.fall",
    "block.sculk_catalyst.hit": "block.sculk_sensor.hit",
    "block.sculk_catalyst.place": "block.sculk_sensor.place",
    "block.sculk_catalyst.step": "block.sculk_sensor.step",
    "block.sculk_shrieker.break": "block.sculk_sensor.break",
    "block.sculk_shrieker.fall": "block.sculk_sensor.fall",
    "block.sculk_shrieker.hit": "block.sculk_sensor.hit",
    "block.sculk_shrieker.place": "block.sculk_sensor.place",
    "block.sculk_shrieker.shriek": "block.sculk_sensor.clicking",
    "block.sculk_shrieker.step": "block.sculk_sensor.step",
    "block.sculk_vein.break": "block.sculk_sensor.break",
    "block.sculk_vein.fall": "block.sculk_sensor.fall",
    "block.sculk_vein.hit": "block.sculk_sensor.hit",
    "block.sculk_vein.place": "block.sculk_sensor.place",
    "block.sculk_vein.step": "block.sculk_sensor.step",
    # アレイ → ヴェックス系の音。
    "entity.allay.ambient_with_item": "entity.vex.ambient",
    "entity.allay.ambient_without_item": "entity.vex.ambient",
    "entity.allay.death": "entity.vex.death",
    "entity.allay.hurt": "entity.vex.hurt",
    "entity.allay.item_given": "entity.vex.ambient",
    "entity.allay.item_taken": "entity.vex.ambient",
    "entity.allay.item_thrown": "entity.vex.ambient",
    # カエル → ウサギ系の音。
    "entity.frog.ambient": "entity.rabbit.ambient",
    "entity.frog.death": "entity.rabbit.death",
    "entity.frog.eat": "entity.rabbit.ambient",
    "entity.frog.hurt": "entity.rabbit.hurt",
    "entity.frog.lay_spawn": "entity.rabbit.ambient",
    "entity.frog.long_jump": "entity.rabbit.jump",
    "entity.frog.step": "entity.rabbit.ambient",
    "entity.frog.tongue": "entity.rabbit.ambient",
    # ヤギの角笛の破損 → ヤギのダメージ音。
    "entity.goat.horn_break": "entity.goat.hurt",
    "entity.goat.screaming.horn_break": "entity.goat.screaming.hurt",
    # オウムのウォーデンの物真似 → ゾンビの物真似。
    "entity.parrot.imitate.warden": "entity.parrot.imitate.zombie",
    # オタマジャクシ → ウーパールーパー系の音。
    "entity.tadpole.death": "entity.axolotl.death",
    "entity.tadpole.flop": "entity.axolotl.splash",
    "entity.tadpole.grow_up": "entity.axolotl.idle_air",
    "entity.tadpole.hurt": "entity.axolotl.hurt",
    # ウォーデン → アイアンゴーレム系の音。
    "entity.warden.agitated": "entity.iron_golem.attack",
    "entity.warden.ambient": "entity.iron_golem.hurt",
    "entity.warden.angry": "entity.iron_golem.attack",
    "entity.warden.attack_impact": "entity.iron_golem.attack",
    "entity.warden.death": "entity.iron_golem.death",
    "entity.warden.dig": "entity.iron_golem.step",
    "entity.warden.emerge": "entity.iron_golem.hurt",
    "entity.warden.heartbeat": "entity.iron_golem.hurt",
    "entity.warden.hurt": "entity.iron_golem.hurt",
    "entity.warden.listening": "entity.iron_golem.hurt",
    "entity.warden.listening_angry": "entity.iron_golem.attack",
    "entity.warden.nearby_close": "entity.iron_golem.hurt",
    "entity.warden.nearby_closer": "entity.iron_golem.hurt",
    "entity.warden.nearby_closest": "entity.iron_golem.hurt",
    "entity.warden.roar": "entity.iron_golem.attack",
    "entity.warden.sniff": "entity.iron_golem.hurt",
    "entity.warden.sonic_boom": "entity.iron_golem.attack",
    "entity.warden.sonic_charge": "entity.iron_golem.attack",
    "entity.warden.step": "entity.iron_golem.step",
    "entity.warden.tendril_clicks": "entity.iron_golem.hurt",
    # オタマジャクシ入りバケツ → ウーパールーパー入りバケツ。
    "item.bucket.empty_tadpole": "item.bucket.empty_axolotl",
    "item.bucket.fill_tadpole": "item.bucket.fill_axolotl",
    # ヤギの角笛 → ヤギの鳴き声。
    "item.goat_horn.play": "entity.goat.ambient",
    "item.goat_horn.sound.0": "entity.goat.ambient",
    "item.goat_horn.sound.1": "entity.goat.ambient",
    "item.goat_horn.sound.2": "entity.goat.ambient",
    "item.goat_horn.sound.3": "entity.goat.ambient",
    "item.goat_horn.sound.4": "entity.goat.ambient",
    "item.goat_horn.sound.5": "entity.goat.ambient",
    "item.goat_horn.sound.6": "entity.goat.ambient",
    "item.goat_horn.sound.7": "entity.goat.ambient",
    # 新しいバイオームの音楽 → 汎用・洞窟の音楽。
    "music.overworld.deep_dark": "music.overworld.dripstone_caves",
    "music.overworld.jungle_and_forest": "music.game",
    "music.overworld.old_growth_taiga": "music.game",
    "music.overworld.swamp": "music.game",
    "music_disc.5": "music_disc.cat",
}
# パーティクル（759 → 758）。
PARTICLE_FALLBACK_759_TO_758 = {
    "sculk_charge": "soul",
    "sculk_charge_pop": "soul",
    "sculk_soul": "soul",
    "shriek": "vibration",
    "sonic_boom": "explosion",
}
# ブロックエンティティのフォールバック。
BLOCK_ENTITY_FALLBACK_759_TO_758 = {
    "sculk_catalyst": "sculk_sensor",
    "sculk_shrieker": "sculk_sensor",
}
# バイオームのフォールバック（mangrove_swamp／deep_dark は 758 には無い）。
BIOME_FALLBACK_759_TO_758 = {
    "mangrove_swamp": "swamp",
    "deep_dark": "dripstone_caves",
}


def build_block_state_maps(vv758, vv759, blocks758, blocks759):
    """ブロック状態の対応表（両方向）。プロパティ順の違いを正規化して比べる。"""
    states758 = [norm_state(s) for s in vv758["blockstates"]]
    states759 = [norm_state(s) for s in vv759["blockstates"]]
    idx758 = {s: i for i, s in enumerate(states758)}
    idx759 = {s: i for i, s in enumerate(states759)}
    def758 = {b["name"]: b["defaultState"] for b in blocks758}
    def759 = {b["name"]: b["defaultState"] for b in blocks759}

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

    by_name_759 = {}
    for s, i in idx759.items():
        n, p = parse_state(s)
        by_name_759.setdefault(n, []).append((p, i))

    fwd = []  # 759 -> 758
    unmapped_fwd = []
    for s in states759:
        if s in idx758:
            fwd.append(idx758[s])
            continue
        v = drop_one(s, idx758)
        if v is not None:
            fwd.append(v)
            continue
        name, _ = parse_state(s)
        fb = BLOCK_FALLBACK_759_TO_758.get(name)
        if fb is not None and fb in def758:
            fwd.append(def758[fb])
            continue
        unmapped_fwd.append(s)
        fwd.append(-1)
    rev = []  # 758 -> 759
    unmapped_rev = []
    for s in states758:
        if s in idx759:
            rev.append(idx759[s])
            continue
        v = add_one(s, by_name_759)
        if v is not None:
            rev.append(v)
            continue
        name, _ = parse_state(s)
        fb = BLOCK_FALLBACK_759_TO_758.get(name)
        if fb is not None and fb in def759:
            rev.append(def759[fb])
            continue
        unmapped_rev.append(s)
        rev.append(-1)
    return fwd, rev, unmapped_fwd, unmapped_rev


def build_name_map(list758, list759, rename_758_to_759=None,
                   fallback_759_to_758=None, fallback_id_758=None,
                   rename_759_to_758=None):
    """名前リストの対応表（両方向）。rename は改名表、fallback は
    759 にだけある要素の代替（758側の名前）。fallback_id_758 は
    代替なしの場合の 758 側の ID（None なら -1）。"""
    idx758 = {n: i for i, n in enumerate(list758)}
    idx759 = {n: i for i, n in enumerate(list759)}
    rename_758_to_759 = rename_758_to_759 or {}
    rename_759_to_758 = rename_759_to_758 or {}
    fallback_759_to_758 = fallback_759_to_758 or {}
    fwd = []
    unmapped_fwd = []
    for n in list759:
        if n in idx758:
            fwd.append(idx758[n])
        elif n in rename_759_to_758 and rename_759_to_758[n] in idx758:
            fwd.append(idx758[rename_759_to_758[n]])
        elif n in fallback_759_to_758:
            fb = fallback_759_to_758[n]
            if fb is None:
                fwd.append(fallback_id_758 if fallback_id_758 is not None else -1)
            elif fb in idx758:
                fwd.append(idx758[fb])
            else:
                unmapped_fwd.append(n)
                fwd.append(-1)
        else:
            unmapped_fwd.append(n)
            fwd.append(-1)
    rev = []
    unmapped_rev = []
    for n in list758:
        if n in idx759:
            rev.append(idx759[n])
        elif n in rename_758_to_759 and rename_758_to_759[n] in idx759:
            rev.append(idx759[rename_758_to_759[n]])
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

    vv758 = load(d / "vv-1.18.json")
    vv759 = load(d / "vv-1.19.json")
    # ViaVersion の mapping-1.18.json／mapping-1.19.json には "menus"
    # キーが無い。758・759 のメニューは 1.20（763）の 24 件と同じ
    # （鍛冶台の新旧分離は 1.19.4 で入った）。vv-1.20.json から借りる。
    menus758 = load(d / "vv-1.20.json")["menus"]
    menus759 = list(menus758)
    blocks758 = load(d / "pc" / "1.18" / "blocks.json")
    blocks759 = load(d / "pc" / "1.19" / "blocks.json")
    biomes758 = {b["name"]: b["id"] for b in load(d / "pc" / "1.18" / "biomes.json")}
    biomes759 = {b["name"]: b["id"] for b in load(d / "pc" / "1.19" / "biomes.json")}

    # 758・759 の codec には damage_type レジストリが無い（1.19.4 で追加）。
    # 対応表は 762（1.19.4）の 42 件の恒等写像として作る。ワイヤ上の
    # 呼び出し元は無い。件数は minecraft-data pc/1.19.4/loginPacket.json
    # の codec で確認済み（42 件）。
    login_762 = load(d / "pc" / "1.19.4" / "loginPacket.json")
    entries = login_762["dimensionCodec"]["value"]["minecraft:damage_type"] \
        ["value"]["value"]["value"]["value"]
    damage762 = [e["name"]["value"] for e in entries]
    assert len(damage762) == 42, f"ダメージ種別が 42 件ではない: {len(damage762)}"
    damage759 = list(damage762)
    damage758 = list(damage762)

    # メニューの順序が jar ダンプと一致することを確認する（あれば）。
    jar_reg = d / "vanilla" / "datagen" / "reports" / "registries.json"
    if jar_reg.exists():
        reg = load(jar_reg)
        jar_menus = [k.split(":", 1)[1] for k, v in sorted(
            reg["minecraft:menu"]["entries"].items(),
            key=lambda kv: kv[1]["protocol_id"])]
        assert jar_menus == menus758, f"メニューの順序が jar ダンプと違う: {jar_menus}"
        print(f"メニュー 24 件：jar ダンプと一致を確認。")
        for vv_key, jar_key in (("blocks", "minecraft:block"),
                                ("items", "minecraft:item"),
                                ("entities", "minecraft:entity_type"),
                                ("sounds", "minecraft:sound_event"),
                                ("particles", "minecraft:particle_type"),
                                ("blockentities", "minecraft:block_entity_type"),
                                ("enchantments", "minecraft:enchantment"),
                                ("paintings", "minecraft:motive")):
            jar_names = [k.split(":", 1)[1] for k, v in sorted(
                reg[jar_key]["entries"].items(),
                key=lambda kv: kv[1]["protocol_id"])]
            assert jar_names == vv758[vv_key], f"{vv_key} の順序が jar ダンプと違う"
            print(f"{vv_key} {len(jar_names)} 件：jar ダンプと一致を確認。")

    problems = []

    def check(name, unmapped_fwd, unmapped_rev):
        if unmapped_fwd or unmapped_rev:
            problems.append((name, unmapped_fwd, unmapped_rev))

    bs_fwd, bs_rev, u, v = build_block_state_maps(vv758, vv759, blocks758, blocks759)
    check("blockstate", u, v)

    item_fwd, item_rev, u, v = build_name_map(
        vv758["items"], vv759["items"],
        fallback_759_to_758=ITEM_FALLBACK_759_TO_758,
        fallback_id_758=1)  # 石
    check("item", u, v)
    ent_fwd, ent_rev, u, v = build_name_map(
        vv758["entities"], vv759["entities"],
        fallback_759_to_758=ENTITY_FALLBACK_759_TO_758,
        fallback_id_758=64)  # ブタ
    check("entity", u, v)
    snd_fwd, snd_rev, u, v = build_name_map(
        vv758["sounds"], vv759["sounds"],
        fallback_759_to_758=SOUND_FALLBACK_759_TO_758_EXPLICIT,
        fallback_id_758=None)
    # 音の代替なしは石の破壊音（758 の block.stone.break）へ。
    stone_break_758 = vv758["sounds"].index("block.stone.break")
    snd_fwd = [stone_break_758 if x < 0 else x for x in snd_fwd]
    check("sound", u, v)
    part_fwd, part_rev, u, v = build_name_map(
        vv758["particles"], vv759["particles"],
        fallback_759_to_758=PARTICLE_FALLBACK_759_TO_758,
        fallback_id_758=0)  # ambient_entity_effect
    check("particle", u, v)
    blk_fwd, blk_rev, u, v = build_name_map(
        vv758["blocks"], vv759["blocks"],
        fallback_759_to_758=BLOCK_FALLBACK_759_TO_758,
        fallback_id_758=1)  # 石
    check("block", u, v)
    be_fwd, be_rev, u, v = build_name_map(
        vv758["blockentities"], vv759["blockentities"],
        fallback_759_to_758=BLOCK_ENTITY_FALLBACK_759_TO_758,
        fallback_id_758=None)
    check("blockentity", u, v)
    menu_fwd, menu_rev, u, v = build_name_map(
        menus758, menus759,
        fallback_id_758=None)
    check("menu", u, v)
    biome_fwd, biome_rev, u, v = build_name_map(
        [n for n, _ in sorted(biomes758.items(), key=lambda kv: kv[1])],
        [n for n, _ in sorted(biomes759.items(), key=lambda kv: kv[1])],
        fallback_759_to_758=BIOME_FALLBACK_759_TO_758,
        fallback_id_758=None)
    check("biome", u, v)
    dmg_fwd, dmg_rev, u, v = build_name_map(
        damage758, damage759,
        fallback_id_758=None)
    check("damage", u, v)
    # 絵画・エンチャントは件数だけ確認する（対応表は作らない。766 以降の
    # painting-variant 対応とは別で、758〜765 の絵画は変換しない）。
    assert len(vv758["paintings"]) == 26, len(vv758["paintings"])
    assert len(vv759["paintings"]) == 30, len(vv759["paintings"])
    assert len(vv758["enchantments"]) == 38, len(vv758["enchantments"])
    assert len(vv759["enchantments"]) == 39, len(vv759["enchantments"])
    # エフェクトは 758 が 32 件（Darkness は 759 で追加）。
    eff758 = {x["name"]: x["id"] for x in load(d / "pc" / "1.17" / "effects.json")}
    eff759 = {x["name"]: x["id"] for x in load(d / "pc" / "1.19" / "effects.json")}
    assert len(eff758) == 32 and len(eff759) == 33, (len(eff758), len(eff759))
    assert set(eff759) - set(eff758) == {"Darkness"}, set(eff759) - set(eff758)
    print("絵画 26→30（earth／fire／water／wind を追加）、"
          "エンチャント 38→39（swift_sneak を追加）、"
          "エフェクト 32→33（Darkness を追加）を確認。")

    if problems:
        for name, uf, ur in problems:
            print(f"未対応 {name}: 759→758 {uf} / 758→759 {ur}", file=sys.stderr)
        return 1

    maps = {
        "BLOCK_STATE": (bs_fwd, bs_rev, len(vv759["blockstates"]), len(vv758["blockstates"])),
        "ITEM": (item_fwd, item_rev, len(vv759["items"]), len(vv758["items"])),
        "ENTITY_TYPE": (ent_fwd, ent_rev, len(vv759["entities"]), len(vv758["entities"])),
        "SOUND": (snd_fwd, snd_rev, len(vv759["sounds"]), len(vv758["sounds"])),
        "PARTICLE": (part_fwd, part_rev, len(vv759["particles"]), len(vv758["particles"])),
        "BLOCK": (blk_fwd, blk_rev, len(vv759["blocks"]), len(vv758["blocks"])),
        "BLOCK_ENTITY": (be_fwd, be_rev, len(vv759["blockentities"]), len(vv758["blockentities"])),
        "MENU": (menu_fwd, menu_rev, len(menus759), len(menus758)),
        "BIOME": (biome_fwd, biome_rev, len(biomes759), len(biomes758)),
        "DAMAGE_TYPE": (dmg_fwd, dmg_rev, len(damage759), len(damage758)),
    }
    fallbacks_fwd = {
        "BLOCK_STATE": 1, "ITEM": 1, "ENTITY_TYPE": 64,
        "SOUND": stone_break_758, "PARTICLE": 0, "BLOCK": 1,
        "BLOCK_ENTITY": -1, "MENU": 11, "BIOME": biomes758["plains"],
        "DAMAGE_TYPE": 16,
    }
    stone_break_759 = vv759["sounds"].index("block.stone.break")
    fallbacks_rev = {
        "BLOCK_STATE": 1, "ITEM": 1, "ENTITY_TYPE": 67,
        "SOUND": stone_break_759, "PARTICLE": 0, "BLOCK": 1,
        "BLOCK_ENTITY": -1, "MENU": 11, "BIOME": biomes759["plains"],
        "DAMAGE_TYPE": 16,
    }
    # 758 側の代替先：石のブロック状態 1、石のアイテム 1、ブタ 64、
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
        print(f"{name}: 759→758 entries={n_fwd} ranges={len(fr)} overrides={len(fo)} "
              f"fallback={sum(1 for x in fwd if x == fb)} / "
              f"758→759 entries={n_rev} ranges={len(rr)} overrides={len(ro)} "
              f"fallback={sum(1 for x in rev if x == fallbacks_rev[name])}")

    if args.json:
        out = {name: {"fwd": fwd, "rev": rev}
               for name, (fwd, rev, _, _) in maps.items()}
        args.json.write_text(json.dumps(out, indent=1) + "\n", encoding="utf-8")
        print(f"JSON を {args.json} に書き出した。")

    header = """package io.detergentmc.protocol;

/**
 * 758（1.18.2）のレジストリ対応表（区間＋上書きの生の表）。
 *
 * <p>{@link RegistryRemapper} から分離した。理由はバイトコードの制限：
 * 表を RegistryRemapper に置くと静的初期化子（{@code <clinit>}）が
 * 64KB を超えてコンパイルできなくなる（"code too large"）ため。
 * このクラスの表は {@link RegistryRemapper} から static import で
 * 同じ名前のまま参照する。
 *
 * <p>表の内容と検証は {@code detergentmc-protocol/tools/} を参照：
 * 生成は {@code gen-758-tables.py}、検証は {@code verify-758-tables.py}。
 * 情報源は ViaVersion Mappings の mapping-1.18.json（758）と
 * mapping-1.19.json（759）で、名前で突き合わせた。順序が本物の
 * バニラ 1.18.2 と一致することは jar の data-generator ダンプ
 * （reports/registries.json・reports/blocks.json）で全カテゴリ確認済み。
 * ブロック状態はプロパティを1個だけ落とす／足す一致も使う（759 で葉に
 * waterlogged が追加された）。mapping-1.18.json と mapping-1.19.json
 * はプロパティの並び順が違うため、比較はソートした正規形で行った。
 * 1.19 の新要素の代替は次の通り：
 * マングローブ系→オーク系、泥→土、泥レンガ→レンガ、スカルク→深層岩、
 * スカルク系ブロックエンティティ→スカルクセンサー、フロッグライト→
 * グロウストーン、カエルの卵→スイレンの葉、チェスト付きボート→同じ木の
 * ボート、アレイ→ヴェックス、カエル→ウサギ、オタマジャクシ→
 * ウーパールーパー、ウォーデン→アイアンゴーレム、music_disc_5／
 * disc_fragment_5→music_disc_cat、echo_shard→amethyst_shard、
 * goat_horn→music_disc_cat、recovery_compass→compass、
 * tadpole_bucket→axolotl_bucket、mangrove_swamp→swamp、
 * deep_dark→dripstone_caves。
 * 音はスカルク系→スカルクセンサー系、泥系→土・石レンガ系、
 * フロッグライト→ガラス系、カエルの卵→スライムブロック系、
 * アレイ→ヴェックス、カエル→ウサギ、オタマジャクシ→ウーパールーパー、
 * ウォーデン→アイアンゴーレム、新しいバイオームの音楽→汎用・洞窟の音楽へ。
 * パーティクルは sculk_charge 系→soul、shriek→vibration、
 * sonic_boom→explosion へ。エンティティ・絵画・エンチャント・
 * エフェクト・ダメージ種別は対応表を作らない（絵画 26→30・
 * エンチャント 38→39・エフェクト 32→33・ダメージ種別は 42 件の恒等写像）。
 */
final class RegistryTables758 {

    private RegistryTables758() {
    }

"""
    parts = [header]
    for name, (fwd, rev, n_fwd, n_rev) in maps.items():
        fb = fallbacks_fwd[name]
        fr, fo = ranges_overrides(fwd, n_fwd, fb)
        rr, ro = ranges_overrides(rev, n_rev, fallbacks_rev[name])
        parts.append(java_table(f"{name}_758", fr, fo, n_fwd, "759->758"))
        parts.append("")
        parts.append(java_table(f"{name}_758_TO_759", rr, ro, n_rev, "758->759"))
        parts.append("")
    # 音の名前の代替表（759→758。Stop Sound 用。数値表の代替と一致させる）。
    # 762 の流儀に合わせて JSON ファイルにも書き出す。
    snd_names = sorted(SOUND_FALLBACK_759_TO_758_EXPLICIT.items())
    tools_dir = Path(__file__).resolve().parent
    (tools_dir / "sound-names-759-to-758.json").write_text(
        json.dumps(snd_names, ensure_ascii=False) + "\n", encoding="utf-8")
    (tools_dir / "sound-names-758-to-759.json").write_text(
        "[]\n", encoding="utf-8")
    print("音の名前の代替表 JSON を書き出した（759→758 は "
          f"{len(snd_names)} 対、758→759 は 0 対）。")
    lines = ["    /** 音の名前：759 → 758（Stop Sound 用）。数値の合成表と一致させる。 */",
             "    static final String[][] SOUND_NAME_FALLBACKS_759_TO_758 = {"]
    for src, dst in snd_names:
        lines.append(f'            {{"{src}", "{dst}"}},')
    lines.append("    };")
    parts.append("\n".join(lines))
    parts.append("")
    # 音の名前の代替表（758→759。758 の音はすべて 759 に同じ名前であるため空）。
    lines = ["    /** 音の名前：758 → 759（Stop Sound 用）。758 の音はすべて 759 に同じ名前のため空。 */",
             "    static final String[][] SOUND_NAME_FALLBACKS_758_TO_759 = {"]
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
