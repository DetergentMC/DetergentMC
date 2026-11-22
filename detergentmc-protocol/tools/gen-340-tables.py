#!/usr/bin/env python3
"""プロトコル 340（1.12.2）のレジストリ対応表を生成する。

340 は Flattening 前の世代で、393（1.13）との差は小差分ではない:

- ブロックに「状態」が無い。ワイヤ値は (数値ID << 4) | メタデータ
  （254 種 × 16、実在組合せ 1,328 通り）。wool のメタが色別ブロックに
  分裂するような意味対応表が必須で、本スクリプトの中核は
  (id, meta) → 393 ブロック状態 の意味解決（resolve_block）である。
  メタの意味は minecraft-data 1.12 の variations の description
  （"Lower half, facing east" 等、体系的）を主に使い、torch のように
  Burger 側がずれている箇所は古典的なメタ仕様で上書きする。
- アイテム ID は全面振り直し（410 種、最大 ID 2267 の疎な番号）。
  itemDamage は SlotTranslator 側で付け替える（本表は ID のみ）。
- エンティティ登録（83 件）と音（549 件）は minecraft-data に
  完全な一覧が無いため、実バニラ jar のバイトコードから抽出した
  tools/entities-340.json / tools/sounds-340.json を ground truth とする
  （抽出方法は extract-340-registries.py。エンティティは Burger の
  type=mob 83 件と完全一致を確認済み）。
- パーティクル 49 件は旧名（explode 等）なので 1.13 名への改名表を持つ。
- バイオームは数値 ID が疎（最大 167）。

合成方法は 393 と同じ: 340⇔393 を名前で確定し、393⇔758 の表
（tables-393.json、gen-393-tables.py --json で再生成）をつなぐ。

使い方:
  python3 gen-340-tables.py [--data340 DIR] [--gen113 DIR]
          [--tables393 FILE] [--mcdata DIR] [--out FILE] [--json FILE]
"""
import argparse
import importlib.util
import json
import re
import sys
from pathlib import Path

_spec = importlib.util.spec_from_file_location(
    "gen498", str(Path(__file__).with_name("gen-498-tables.py")))
g498 = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(g498)
ranges_overrides = g498.ranges_overrides
java_table = g498.java_table
parse_state = g498.parse_state
fmt_state = g498.fmt_state

COLORS = ["white", "orange", "magenta", "light_blue", "yellow", "lime",
          "pink", "gray", "light_gray", "cyan", "purple", "blue", "brown",
          "green", "red", "black"]  # メタ 0..15 の色順（羊毛順）


def load(p):
    return json.loads(Path(p).read_text(encoding="utf-8"))


def snake(display):
    """表示名 → スネークケース名。'Block of Iron' は 'iron_block' に直す。"""
    s = display.strip().lower().replace("'", "")
    s = re.sub(r"\([^)]*\)\s*", "", s)  # '(Stone) Wooden Slab' 等の注記を除く
    s = s.replace(" ", "_")
    if s.endswith("_wood_planks"):
        s = s[: -len("_wood_planks")] + "_planks"
    if s.startswith("block_of_"):
        s = s[len("block_of_"):] + "_block"
    if s.endswith("_stained_hardened_clay"):
        s = s[: -len("_stained_hardened_clay")] + "_terracotta"
    elif s.endswith("_hardened_clay"):
        s = s[: -len("_hardened_clay")] + "_terracotta"
    return s


# ---------------------------------------------------------------------------
# 340 → 393 の改名表（レジストリ名ベース。型バリアントは displayName で解決）
# ---------------------------------------------------------------------------

BLOCK_RENAME_340_TO_393 = {
    "grass": "grass_block",
    "stone_stairs": "cobblestone_stairs",
    "brick_block": "bricks",
    "wooden_door": "oak_door",
    "wooden_slab": "oak_slab",
    "wooden_button": "oak_button",
    "wooden_pressure_plate": "oak_pressure_plate",
    "wooden_trapdoor": "oak_trapdoor",
    "trapdoor": "oak_trapdoor",
    "fence": "oak_fence",
    "fence_gate": "oak_fence_gate",
    "standing_sign": "sign",
    "wall_sign": "wall_sign",
    "reeds": "sugar_cane",
    "waterlily": "lily_pad",
    "web": "cobweb",
    "deadbush": "dead_bush",
    "stonebrick": "stone_bricks",
    "mob_spawner": "spawner",
    "noteblock": "note_block",
    "quartz_ore": "nether_quartz_ore",
    "melon_block": "melon",
    "end_bricks": "end_stone_bricks",
    "magma": "magma_block",
    "red_nether_brick": "red_nether_bricks",
    "nether_brick": "nether_bricks",
    "snow_layer": "snow",
    "snow": "snow_block",
    "golden_rail": "powered_rail",
    "slime": "slime_block",
    "hardened_clay": "terracotta",
    "lit_furnace": "furnace",
    "lit_redstone_lamp": "redstone_lamp",
    "lit_redstone_ore": "redstone_ore",
    "lit_pumpkin": "jack_o_lantern",
    "flowing_water": "water",
    "flowing_lava": "lava",
    "portal": "nether_portal",
    "powered_repeater": "repeater",
    "unpowered_repeater": "repeater",
    "powered_comparator": "comparator",
    "unpowered_comparator": "comparator",
    "daylight_detector_inverted": "daylight_detector",
}

# displayName で型を決めるブロック（メタ = 型バリアント）
TYPE_VARIANT_BLOCKS = {
    "wool", "carpet", "stone", "dirt", "sand", "sandstone", "red_sandstone",
    "stonebrick", "prismarine", "sponge", "leaves", "leaves2", "planks",
    "stone_slab", "wooden_slab", "stone_slab2", "purpur_slab",
    "double_stone_slab", "double_wooden_slab", "double_stone_slab2",
    "purpur_double_slab", "stained_glass", "stained_glass_pane",
    "stained_hardened_clay", "concrete", "concrete_powder",
    "cobblestone_wall",
}

# displayName → 393 名の例外（snake で直らないもの）
DISPLAY_NAME_OVERRIDES = {
    "Upper Red Sandstone Slab": "red_sandstone_slab",
    "Bricks Slab": "brick_slab",
    "Stone brick": "stone_bricks",
    "Mossy stone brick": "mossy_stone_bricks",
    "Cracked stone brick": "cracked_stone_bricks",
    "Chiseled stone brick": "chiseled_stone_bricks",
    "(Stone) Wooden Slab": "petrified_oak_slab",
    "Oak Wood Slab": "oak_slab",
    "Spruce Wood Slab": "spruce_slab",
    "Birch Wood Slab": "birch_slab",
    "Jungle Wood Slab": "jungle_slab",
    "Acacia Wood Slab": "acacia_slab",
    "Dark Oak Wood Slab": "dark_oak_slab",
    "Stone Monster Egg": "infested_stone",
    "Cobblestone Monster Egg": "infested_cobblestone",
    "Stone Brick Monster Egg": "infested_stone_bricks",
    "Mossy Stone Brick Monster Egg": "infested_mossy_stone_bricks",
    "Cracked Stone Brick Monster Egg": "infested_cracked_stone_bricks",
    "Chiseled Stone Brick Monster Egg": "infested_chiseled_stone_bricks",
    "Double Tallgrass": "tall_grass",
}

# アイテムの改名（ブロック改名は別途そのまま適用する）
ITEM_RENAME_340_TO_393 = {
    "speckled_melon": "glistering_melon_slice",
    "melon": "melon_slice",
    "fish": "cod",
    "cooked_fish": "cooked_cod",
    "spawn_egg": "pig_spawn_egg",  # 実体は NBT の EntityTag。本表では代表値
    "banner": "white_banner",      # 色は itemDamage（SlotTranslator で付け替え）
    "bed": "white_bed",            # 色は itemDamage（同上）
    "skull": "skeleton_skull",     # 種別は itemDamage（同上）
    "boat": "oak_boat",
    "chorus_fruit_popped": "popped_chorus_fruit",
    # ブロックアイテム（型バリアント／特殊ブロックの代表値）
    "planks": "oak_planks",
    "sapling": "oak_sapling",
    "log": "oak_log",
    "log2": "acacia_log",
    "leaves": "oak_leaves",
    "leaves2": "acacia_leaves",
    "tallgrass": "grass",
    "wool": "white_wool",
    "carpet": "white_carpet",
    "yellow_flower": "dandelion",
    "red_flower": "poppy",
    "stained_glass": "white_stained_glass",
    "stained_glass_pane": "white_stained_glass_pane",
    "stained_hardened_clay": "white_terracotta",
    "monster_egg": "infested_stone",
    "double_plant": "sunflower",
    "stone_slab2": "red_sandstone_slab",
    "concrete": "white_concrete",
    "concrete_powder": "white_concrete_powder",
    "quartz_ore": "nether_quartz_ore",
    "silver_shulker_box": "light_gray_shulker_box",
    "silver_glazed_terracotta": "light_gray_glazed_terracotta",
    "dye": "ink_sac",              # 色は itemDamage（SlotTranslator で付け替え）
    "netherbrick": "nether_brick",
    "fireworks": "firework_rocket",
    "firework_charge": "firework_star",
}
for _r in ["13", "cat", "blocks", "chirp", "far", "mall", "mellohi",
           "stal", "strad", "ward", "11", "wait"]:
    ITEM_RENAME_340_TO_393[f"record_{_r}"] = f"music_disc_{_r}"

# 393 側のアイテム名 → 340 の基本アイテム名（758/393 で分かれたものの寄せ先）。
# 色・種別の違いは itemDamage 側で表すため、ID のみの表では基本アイテムに寄せる。
# スロット内の正確な付け替えは ChunkTranslator.translateSlot340 と
# SlotTranslator の 340 コーデックが Damage タグ経由で行う。
ITEM_NAME393_TO_BASE340 = {
    "salmon": "fish", "tropical_fish": "fish", "pufferfish": "fish",
    "cooked_salmon": "cooked_fish",
    "skeleton_skull": "skull", "wither_skeleton_skull": "skull",
    "zombie_head": "skull", "player_head": "skull",
    "creeper_head": "skull", "dragon_head": "skull",
    "ink_sac": "dye", "red_dye": "dye", "green_dye": "dye",
    "cocoa_beans": "dye", "lapis_lazuli": "dye", "purple_dye": "dye",
    "cyan_dye": "dye", "light_gray_dye": "dye", "gray_dye": "dye",
    "pink_dye": "dye", "lime_dye": "dye", "yellow_dye": "dye",
    "light_blue_dye": "dye", "magenta_dye": "dye", "orange_dye": "dye",
    "bone_meal": "dye",
}
for _c in COLORS:
    ITEM_NAME393_TO_BASE340[f"{_c}_banner"] = "banner"
    ITEM_NAME393_TO_BASE340[f"{_c}_bed"] = "bed"


# エンティティの改名（340 登録名 → 1.13 名）
ENTITY_RENAME_340_TO_393 = {
    "snowman": "snow_golem",
    "villager_golem": "iron_golem",
    "eye_of_ender_signal": "eye_of_ender",
    "evocation_illager": "evoker",
    "vindication_illager": "vindicator",
    "illusion_illager": "illusioner",
    "xp_orb": "experience_orb",
    "xp_bottle": "experience_bottle",
    "evocation_fangs": "evoker_fangs",
    "ender_crystal": "end_crystal",
}

# パーティクルの改名（340 の旧名 → 1.13 名）
PARTICLE_RENAME_340_TO_393 = {
    "explode": "explosion",
    "largeexplode": "explosion_emitter",
    "hugeexplosion": "explosion_emitter",
    "fireworksSpark": "firework",
    "splash": "splash",
    "wake": "fishing",
    "suspended": "underwater",
    "depthsuspend": "mycelium",
    "crit": "crit",
    "magicCrit": "enchanted_hit",
    "smoke": "smoke",
    "largesmoke": "large_smoke",
    "spell": "effect",
    "instantSpell": "instant_effect",
    "mobSpell": "entity_effect",
    "mobSpellAmbient": "ambient_entity_effect",
    "witchMagic": "witch",
    "dripWater": "dripping_water",
    "dripLava": "dripping_lava",
    "angryVillager": "angry_villager",
    "happyVillager": "happy_villager",
    "townAura": "mycelium",
    "townaura": "mycelium",
    "note": "note",
    "portal": "portal",
    "enchantmenttable": "enchant",
    "flame": "flame",
    "lava": "lava",
    "footstep": "poof",      # 1.13 で廃止。近似
    "cloud": "cloud",
    "reddust": "dust",
    "snowballpoof": "item_snowball",
    "snowshovel": "poof",    # 1.13 で廃止。近似
    "slime": "item_slime",
    "heart": "heart",
    "barrier": "barrier",
    "iconcrack": "item",
    "blockcrack": "block",
    "blockdust": "block",
    "droplet": "rain",
    "take": "poof",          # 1.13 で廃止。近似
    "mobappearance": "elder_guardian",
    "dragonbreath": "dragon_breath",
    "endRod": "end_rod",
    "damageIndicator": "damage_indicator",
    "sweepAttack": "sweep_attack",
    "fallingdust": "falling_dust",
    "totem": "totem_of_undying",
    "spit": "spit",
}

# 音の改名（340 名 → 1.13 名）。トークン単位の置換で組み立てる
SOUND_TOKEN_RENAMES = [
    ("armorstand", "armor_stand"),
    ("enderchest", "ender_chest"),
    ("enderdragon", "ender_dragon"),
    ("enderdragon_fireball", "dragon_fireball"),
    ("enderpearl", "ender_pearl"),
    ("endereye", "ender_eye"),
    ("endermen", "enderman"),
    ("magmacube", "magma_cube"),
    ("snowman", "snow_golem"),
    ("bobber", "fishing_bobber"),
    ("cloth", "wool"),
    ("evocation_fangs", "evoker_fangs"),
    ("evocation_illager", "evoker"),
    ("illusion_illager", "illusioner"),
    ("vindication_illager", "vindicator"),
    ("irongolem", "iron_golem"),
    ("itemframe", "item_frame"),
    ("firework", "firework_rocket"),
    ("leashknot", "leash_knot"),
    ("lightning", "lightning_bolt"),
    ("lingeringpotion", "lingering_potion"),
    ("metal_pressureplate", "metal_pressure_plate"),
    ("stone_pressureplate", "stone_pressure_plate"),
    ("wood_pressureplate", "wooden_pressure_plate"),
    ("wood_button", "wooden_button"),
    ("waterlily", "lily_pad"),
    ("zombie_pig", "zombie_pigman"),
    ("attack_door_wood", "attack_wooden_door"),
    ("break_door_wood", "break_wooden_door"),
]


def sound_name_340_to_393(name):
    if name.startswith("block.slime."):
        name = "block.slime_block." + name[len("block.slime."):]
    if name.startswith("block.note."):
        name = "block.note_block." + name[len("block.note."):]
    if name.startswith("record."):
        name = "music_disc." + name[len("record."):]
    if name == "entity.polar_bear.baby_ambient":
        name = "entity.polar_bear.ambient_baby"
    if name == "entity.villager.trading":
        name = "entity.villager.trade"
    if name.startswith("entity.small_magmacube."):
        name = "entity.magma_cube." + name[len("entity.small_magmacube."):] + "_small"
    if name.startswith("entity.small_slime."):
        name = "entity.slime." + name[len("entity.small_slime."):] + "_small"
    parts = name.split(".")
    out = []
    for p in parts:
        for src, dst in SOUND_TOKEN_RENAMES:
            if p == src:
                p = dst
                break
        out.append(p)
    return ".".join(out)


# バイオームの改名（340 名 → 1.13 名）。
# minecraft-data 1.12 の biomes.json は名前が現行式に直して収録されており、
# 62 件すべて 1.13.2 の名前集合に含まれることを確認済み（1.13 新規の
# 海洋・ジ・エンド系 11 件が 340 に無いだけ）。よって改名は不要。
BIOME_RENAME_340_TO_393 = {}


# ---------------------------------------------------------------------------
# ブロック意味解決: (340 ブロック, メタ) → (393 ブロック名, プロパティ)
# ---------------------------------------------------------------------------

FACING_25 = {2: "north", 3: "south", 4: "west", 5: "east"}   # かまど・チェスト系
HORIZ = {0: "south", 1: "west", 2: "north", 3: "east"}       # 水平インデックス順
PISTON_FACING = {0: "down", 1: "up", 2: "north", 3: "south", 4: "west", 5: "east"}
DOOR_FACING = {0: "east", 1: "south", 2: "west", 3: "north"}
TRAP_FACING = {0: "north", 1: "south", 2: "west", 3: "east"}
STAIR_FACING = {0: "east", 1: "west", 2: "south", 3: "north"}
RAIL_SHAPES = {0: "north_south", 1: "east_west", 2: "ascending_east",
               3: "ascending_west", 4: "ascending_north", 5: "ascending_south",
               6: "south_east", 7: "south_west", 8: "north_west",
               9: "north_east"}
# キノコブロックのメタ → 見えている面（古典的なメタ仕様）
MUSHROOM_FACES = {
    0: (), 1: ("north", "west", "up"), 2: ("north", "up"),
    3: ("north", "east", "up"), 4: ("west", "up"), 5: ("up",),
    6: ("east", "up"), 7: ("south", "west", "up"), 8: ("south", "up"),
    9: ("south", "east", "up"),
    11: ("north", "south", "east", "west", "up", "down"),
    12: ("north", "south", "east", "west", "up", "down"),
    13: ("north", "south", "east", "west", "up", "down"),
    14: ("north", "south", "east", "west", "up", "down"),
    15: ("north", "south", "east", "west", "up", "down"),
}
SAPLING_TYPES = ["oak_sapling", "spruce_sapling", "birch_sapling",
                 "jungle_sapling", "acacia_sapling", "dark_oak_sapling"]
FLOWER_TYPES = ["poppy", "blue_orchid", "allium", "azure_bluet", "red_tulip",
                "orange_tulip", "white_tulip", "pink_tulip", "oxeye_daisy"]
DOUBLE_PLANT_TYPES = ["sunflower", "lilac", "tall_grass", "large_fern",
                      "rose_bush", "peony"]
LOG_TYPES = {  # log / log2 のメタ下位 2 ビット → 樹種
    "log": ["oak", "spruce", "birch", "jungle"],
    "log2": ["acacia", "dark_oak", "acacia", "dark_oak"],
}
# たいまつ系の古典メタ: 1=東の壁, 2=西, 3=南, 4=北, 5=床, 0=床（不正値の寄せ）
# ※ minecraft-data の variations は 0 始まりで 1 つずれているので使わない
TORCH_META = {0: (None, None), 1: ("wall", "east"), 2: ("wall", "west"),
              3: ("wall", "south"), 4: ("wall", "north"), 5: (None, None)}


def axis_from_meta12(meta):
    return {0: "y", 4: "x", 8: "z", 12: "y"}[meta & 12]


class BlockResolver:
    """(id340, meta) → 393 状態 ID を引くための意味解決器。"""

    def __init__(self, blocks340, states393):
        # states393: name -> list of (state_id, props dict), default_id dict
        self.blocks340 = {b["id"]: b for b in blocks340}
        self.by_name340 = {b["name"]: b for b in blocks340}
        self.by_id393 = {}
        self.default393 = {}
        self.state_id = {}
        for name, lst in states393.items():
            for sid, props, is_default in lst:
                self.by_id393[sid] = (name, props)
                self.state_id[(name, tuple(sorted(props.items())))] = sid
                if is_default:
                    self.default393[name] = sid
        self.warnings = []

    def state(self, name, props):
        """(名前, プロパティ) → 状態 ID。無い場合は既定状態に寄せて警告。"""
        key = (name, tuple(sorted(props.items())))
        if key in self.state_id:
            return self.state_id[key]
        if name in self.default393:
            self.warnings.append(f"状態なし {name} {props} → 既定状態")
            return self.default393[name]
        self.warnings.append(f"ブロックなし {name} → 空気")
        return 0

    def default_props(self, name):
        sid = self.default393[name]
        return dict(self.by_id393[sid][1])

    def variation_name(self, block, meta):
        """型バリアントブロックの表示名解決。実在しないメタは下位ビットで寄せる。"""
        variations = {v["metadata"]: v for v in block.get("variations", [])}
        for cand in (meta, meta & 7, meta & 3, meta & 1, 0):
            v = variations.get(cand)
            if v is not None:
                dn = v["displayName"]
                if dn in DISPLAY_NAME_OVERRIDES:
                    return DISPLAY_NAME_OVERRIDES[dn]
                return snake(dn)
        return None

    def resolve(self, block, meta):
        """(393 名, プロパティ上書き) を返す。"""
        bname = block["name"]
        # ---- 特殊ブロック（名前そのものがメタで変わる） ----
        if bname in ("log", "log2"):
            wood = LOG_TYPES[bname][meta & 3]
            axis_bits = meta & 12
            if axis_bits == 12:
                return f"{wood}_wood", {"axis": "y"}
            return f"{wood}_log", {"axis": {0: "y", 4: "x", 8: "z"}[axis_bits]}
        if bname == "quartz_block":
            if meta == 1:
                return "chiseled_quartz_block", {}
            if meta in (2, 3, 4):
                return "quartz_pillar", {"axis": {2: "y", 3: "z", 4: "x"}[meta]}
            return "quartz_block", {}
        if bname in ("hay_block", "bone_block", "purpur_pillar"):
            return bname, {"axis": axis_from_meta12(meta)}
        if bname == "anvil":
            dmg = min(meta >> 2, 2)
            name = ["anvil", "chipped_anvil", "damaged_anvil"][dmg]
            return name, {"facing": HORIZ[meta & 3]}
        if bname == "bed":
            return "red_bed", {}  # 色はブロックエンティティ側（ワイヤに無い）
        if bname == "standing_banner":
            return "white_banner", {"rotation": str(meta)}
        if bname == "wall_banner":
            return "white_wall_banner", {"facing": FACING_25.get(meta, "north")}
        if bname == "skull":
            if meta in FACING_25:
                return "skeleton_wall_skull", {"facing": FACING_25[meta]}
            return "skeleton_skull", {"rotation": str(meta)}
        if bname == "tallgrass":
            return {0: "grass", 1: "grass", 2: "fern"}.get(meta & 3, "grass"), {}
        if bname == "sapling":
            return SAPLING_TYPES[min(meta & 7, 5)], {"stage": "1" if meta & 8 else "0"}
        if bname == "red_flower":
            return FLOWER_TYPES[meta] if meta < len(FLOWER_TYPES) else "poppy", {}
        if bname == "yellow_flower":
            return "dandelion", {}
        if bname == "double_plant":
            name = DOUBLE_PLANT_TYPES[min(meta & 7, 5)]
            return name, {"half": "upper" if meta & 8 else "lower"}
        if bname in ("brown_mushroom_block", "red_mushroom_block"):
            if meta == 10:
                return "mushroom_stem", {}
            props = {d: "true" if d in MUSHROOM_FACES.get(meta, ()) else "false"
                     for d in ("north", "east", "south", "west", "up", "down")}
            return bname, props
        if bname == "monster_egg":
            name = self.variation_name(block, meta)
            return name or "infested_stone", {}
        if bname in ("torch", "redstone_torch", "unlit_redstone_torch"):
            wall, facing = TORCH_META.get(meta, (None, None))
            lit = "false" if bname == "unlit_redstone_torch" else "true"
            if bname == "torch":
                return ("wall_torch", {"facing": facing}) if wall else ("torch", {})
            if wall:
                return "redstone_wall_torch", {"facing": facing, "lit": lit}
            return "redstone_torch", {"lit": lit}
        if bname == "pumpkin":
            return "carved_pumpkin", {"facing": HORIZ[meta & 3]}
        # 二重ハーフブロック: 名前は単体スラブ側の型を引き、type=double にする。
        # double_stone_slab のメタ 8/9/15 は「封印」バリアントで全面ブロック。
        double_base = {"double_stone_slab": "stone_slab",
                       "double_wooden_slab": "wooden_slab",
                       "double_stone_slab2": "stone_slab2",
                       "purpur_double_slab": "purpur_slab"}
        if bname in double_base:
            if bname == "double_stone_slab":
                if meta == 8:
                    return "smooth_stone", {}
                if meta == 9:
                    return "smooth_sandstone", {}
                if meta == 15:
                    return "smooth_quartz", {}
            base = self.by_name340[double_base[bname]]
            name = self.variation_name(base, meta & 7) or "stone_slab"
            return name, {"type": "double"}

        # ---- 名前解決 ----
        if bname in TYPE_VARIANT_BLOCKS:
            name = self.variation_name(block, meta)
            if name is None:
                name = BLOCK_RENAME_340_TO_393.get(bname, bname)
        else:
            name = BLOCK_RENAME_340_TO_393.get(bname, bname)

        # ---- プロパティ規則 ----
        p = {}
        if bname.endswith("_stairs"):
            p = {"facing": STAIR_FACING[meta & 3],
                 "half": "top" if meta & 4 else "bottom"}
        elif bname.endswith("_door") and not bname.endswith("_trapdoor"):
            if meta & 8:
                p = {"half": "upper",
                     "hinge": "right" if meta & 1 else "left",
                     "powered": "true" if meta & 2 else "false"}
            else:
                p = {"half": "lower", "facing": DOOR_FACING[meta & 3],
                     "open": "true" if meta & 4 else "false"}
        elif bname.endswith("_trapdoor"):
            p = {"facing": TRAP_FACING[meta & 3],
                 "open": "true" if meta & 4 else "false",
                 "half": "top" if meta & 8 else "bottom"}
        elif "slab" in bname:
            if "double" in bname:
                p = {"type": "double"}
            else:
                p = {"type": "top" if meta & 8 else "bottom"}
        elif bname in ("piston", "sticky_piston"):
            p = {"facing": PISTON_FACING.get(meta & 7, "north"),
                 "extended": "true" if meta & 8 else "false"}
        elif bname == "piston_head":
            p = {"facing": PISTON_FACING.get(meta & 7, "north"),
                 "type": "sticky" if meta & 8 else "normal"}
        elif bname == "piston_extension":
            name = "moving_piston"
            p = {"facing": PISTON_FACING.get(meta & 7, "north"),
                 "type": "sticky" if meta & 8 else "normal"}
        elif bname in ("dispenser", "dropper"):
            p = {"facing": PISTON_FACING.get(meta & 7, "north"),
                 "triggered": "true" if meta & 8 else "false"}
        elif bname in ("command_block", "chain_command_block",
                       "repeating_command_block"):
            p = {"facing": PISTON_FACING.get(meta & 7, "north"),
                 "conditional": "true" if meta & 8 else "false"}
        elif bname == "observer":
            p = {"facing": PISTON_FACING.get(meta & 7, "north"),
                 "powered": "true" if meta & 8 else "false"}
        elif bname == "end_rod":
            p = {"facing": PISTON_FACING.get(meta & 7, "north")}
        elif bname in ("furnace", "lit_furnace", "chest", "trapped_chest",
                       "ender_chest", "ladder", "wall_sign"):
            p = {"facing": FACING_25.get(meta, "north")}
            if bname == "lit_furnace":
                p["lit"] = "true"
            elif bname == "furnace":
                p["lit"] = "false"
        elif bname == "hopper":
            p = {"facing": {0: "down", 2: "north", 3: "south", 4: "west",
                            5: "east"}.get(meta & 7, "down"),
                 "enabled": "false" if meta & 8 else "true"}
        elif bname == "standing_sign":
            p = {"rotation": str(meta)}
        elif bname == "end_portal_frame":
            p = {"facing": HORIZ[meta & 3], "eye": "true" if meta & 4 else "false"}
        elif bname.endswith("_fence_gate"):
            p = {"facing": HORIZ[meta & 3],
                 "open": "true" if meta & 4 else "false",
                 "powered": "true" if meta & 8 else "false"}
        elif bname == "tripwire_hook":
            p = {"facing": HORIZ[meta & 3],
                 "attached": "true" if meta & 4 else "false",
                 "powered": "true" if meta & 8 else "false"}
        elif bname in ("powered_repeater", "unpowered_repeater"):
            p = {"facing": HORIZ[meta & 3], "delay": str((meta >> 2) + 1),
                 "powered": "true" if bname.startswith("powered_") else "false"}
        elif bname in ("powered_comparator", "unpowered_comparator"):
            p = {"facing": HORIZ[meta & 3],
                 "mode": "subtract" if meta & 4 else "compare",
                 "powered": "true" if bname.startswith("powered_") else "false"}
        elif bname == "lever":
            face, facing = {0: ("floor", "east"), 1: ("wall", "east"),
                            2: ("wall", "west"), 3: ("wall", "south"),
                            4: ("wall", "north"), 5: ("ceiling", "north"),
                            6: ("ceiling", "east"), 7: ("floor", "north")}[meta & 7]
            p = {"face": face, "facing": facing,
                 "powered": "true" if meta & 8 else "false"}
        elif bname in ("stone_button", "wooden_button"):
            face, facing = {0: ("floor", "north"), 1: ("wall", "east"),
                            2: ("wall", "west"), 3: ("wall", "south"),
                            4: ("wall", "north"), 5: ("ceiling", "north")
                            }.get(meta & 7, ("floor", "north"))
            p = {"face": face, "facing": facing,
                 "powered": "true" if meta & 8 else "false"}
        elif bname in ("wooden_pressure_plate", "stone_pressure_plate"):
            p = {"powered": "true" if meta & 1 else "false"}
        elif bname in ("light_weighted_pressure_plate",
                       "heavy_weighted_pressure_plate"):
            p = {"power": str(meta)}
        elif bname in ("daylight_detector", "daylight_detector_inverted"):
            p = {"power": str(meta),
                 "inverted": "true" if bname.endswith("inverted") else "false"}
        elif bname == "redstone_wire":
            p = {"power": str(meta)}
        elif bname == "farmland":
            p = {"moisture": str(min(meta, 7))}
        elif bname == "snow_layer":
            p = {"layers": str(min(meta + 1, 8))}
        elif bname == "cake":
            p = {"bites": str(min(meta, 6))}
        elif bname == "cauldron":
            p = {"level": str(min(meta, 3))}
        elif bname == "brewing_stand":
            p = {"has_bottle_0": "true" if meta & 1 else "false",
                 "has_bottle_1": "true" if meta & 2 else "false",
                 "has_bottle_2": "true" if meta & 4 else "false"}
        elif bname in ("wheat", "carrots", "potatoes", "melon_stem",
                       "pumpkin_stem"):
            p = {"age": str(min(meta, 7))}
        elif bname in ("cactus", "reeds"):
            p = {"age": str(meta)}
        elif bname in ("nether_wart", "frosted_ice"):
            p = {"age": str(min(meta, 3))}
        elif bname == "beetroots":
            p = {"age": str(min(meta, 3))}
        elif bname == "chorus_flower":
            p = {"age": str(min(meta, 5))}
        elif bname == "cocoa":
            p = {"age": str(min(meta >> 2, 2)), "facing": HORIZ[meta & 3]}
        elif bname == "fire":
            p = {"age": str(meta)}
        elif bname in ("flowing_water", "water", "flowing_lava", "lava"):
            p = {"level": str(meta)}
        elif bname == "vine":
            p = {"north": "true" if meta & 1 else "false",
                 "east": "true" if meta & 2 else "false",
                 "south": "true" if meta & 4 else "false",
                 "west": "true" if meta & 8 else "false",
                 "up": "true" if meta == 0 else "false"}
        elif bname == "rail":
            p = {"shape": RAIL_SHAPES.get(meta, "north_south")}
        elif bname in ("golden_rail", "detector_rail", "activator_rail"):
            p = {"shape": RAIL_SHAPES[min(meta & 7, 5)],
                 "powered": "true" if meta & 8 else "false"}
        elif bname in ("leaves", "leaves2"):
            p = {"persistent": "true" if meta & 4 else "false"}
        elif bname == "tripwire":
            p = {"powered": "true" if meta & 1 else "false",
                 "attached": "true" if meta & 4 else "false",
                 "disarmed": "true" if meta & 8 else "false"}
        elif bname == "portal":
            p = {"axis": "x" if meta == 1 else "z"}
        elif bname == "lit_pumpkin":
            p = {"facing": HORIZ[meta & 3]}
        return name, p

    def resolve_state(self, block, meta):
        name, over = self.resolve(block, meta)
        props = self.default_props(name)
        props.update(over)
        return self.state(name, props)


# ---------------------------------------------------------------------------
# 合成と Java 出力
# ---------------------------------------------------------------------------

def load_blocks393(reports_dir):
    """gen-393 と同じ読み方で 1.13 の状態一覧を作る。"""
    spec = importlib.util.spec_from_file_location(
        "gen393", str(Path(__file__).with_name("gen-393-tables.py")))
    g393 = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(g393)
    blocks, states, defaults = g393.load_blocks(reports_dir)
    items = g393.load_items(reports_dir)
    by_name = {}
    default_ids = {}
    for sid, s in enumerate(states):
        name, props = parse_state(s)
        is_def = defaults.get(name) == s
        by_name.setdefault(name, []).append((sid, props, is_def))
        if is_def:
            default_ids[name] = sid
    return blocks, by_name, default_ids, items


def compose(fwd393, rev393, id393_of_name340, fallback340, fallback393,
            size340):
    """340⇔393 の名前対応（340 id → 393 id）と 393 の表から 340 の表を作る。

    fwd （758→340）: 758 →393→ 名前 →340。rev（340→758）: 340 →393→758。
    340 側に実在しない ID（疎な番号の隙間）は代替値の 758 ID で埋める。
    """
    name_of_393 = {}
    for id340, id393 in id393_of_name340.items():
        name_of_393.setdefault(id393, id340)
    fwd = []
    for i758 in range(len(fwd393)):
        id393 = fwd393[i758]
        fwd.append(name_of_393.get(id393, fallback340))
    rev = []
    for id340 in range(size340):
        id393 = id393_of_name340.get(id340)
        if id393 is None or id393 >= len(rev393):
            rev.append(rev393[fallback393])
        else:
            rev.append(rev393[id393])
    return fwd, rev


def build_all(args):
    tools_dir = Path(__file__).parent
    data340 = Path(args.data340)
    t393 = load(args.tables393)
    blocks393_names, states_by_name, default_ids393, items393 = \
        load_blocks393(args.gen113)
    vv113 = load(Path(args.mcdata) / "mapping-1.13.json")

    blocks340 = load(data340 / "blocks.json")
    items340 = load(data340 / "items.json")
    particles340 = load(data340 / "particles.json")
    biomes340 = load(data340 / "biomes.json")
    sounds340 = load(tools_dir / "sounds-340.json")
    entities340 = load(tools_dir / "entities-340.json")  # [[id, name], ...]

    report = {}

    # ---- ブロック状態（意味対応表が中核） ----
    resolver = BlockResolver(blocks340, states_by_name)
    computed = [0] * 4096  # (id<<4|meta) → 393 状態
    valid = 0
    resolve_errors = []
    for block in blocks340:
        variations = {v["metadata"] for v in block.get("variations", [])}
        for meta in range(16):
            v = (block["id"] << 4) | meta
            try:
                computed[v] = resolver.resolve_state(block, meta)
            except KeyError as e:
                resolve_errors.append((block["name"], meta, str(e)))
                computed[v] = 0
            if meta in variations or (not variations and meta == 0):
                valid += 1
    report["resolve_errors"] = resolve_errors
    # 実在しない (id, meta) はそのブロックのメタ 0 に寄せる（computed 済み）。
    # id がブロックでない範囲（255）は空気 0 のまま。
    inv = {}
    for v in range(4096):
        inv.setdefault(computed[v], v)
    bs_fwd = []
    bs_fb_count = 0
    for i758 in range(20342):
        s393 = t393["BLOCK_STATE"]["fwd"][i758]
        if s393 in inv:
            bs_fwd.append(inv[s393])
        else:
            name393 = resolver.by_id393[s393][0]
            d = default_ids393[name393]
            if d in inv:
                bs_fwd.append(inv[d])
            else:
                bs_fwd.append(16)  # 石 (1<<4|0)
            bs_fb_count += 1
    bs_rev = []
    for v in range(4096):
        bs_rev.append(t393["BLOCK_STATE"]["rev"][computed[v]])
    report["block_state"] = {
        "valid_combos": valid, "fallback758": bs_fb_count,
        "warnings": len(resolver.warnings)}
    for w in resolver.warnings[:20]:
        print("  警告:", w)

    # ---- ブロック（ID のみ） ----
    name340_of_block393 = {}
    for block in blocks340:
        name0, _ = resolver.resolve(block, 0)
        name340_of_block393.setdefault(name0, block["id"])
    blk_map = {}  # 340 id → 393 block id
    block_id393 = {n: i for i, n in enumerate(blocks393_names)}
    for block in blocks340:
        name0, _ = resolver.resolve(block, 0)
        if name0 in block_id393:
            blk_map[block["id"]] = block_id393[name0]
    blk_fwd, blk_rev = compose(t393["BLOCK"]["fwd"], t393["BLOCK"]["rev"],
                               blk_map, 1, block_id393["stone"], 256)

    # ---- アイテム ----
    item_id393 = {n: i for i, n in enumerate(items393)}
    item_id340_by_name = {it["name"]: it["id"] for it in items340}
    item_map = {}
    item_missing = []
    for it in items340:
        nm = ITEM_RENAME_340_TO_393.get(it["name"])
        if nm is None:
            nm = BLOCK_RENAME_340_TO_393.get(it["name"], it["name"])
        if nm in item_id393:
            item_map[it["id"]] = item_id393[nm]
        else:
            item_missing.append((it["id"], it["name"], nm))
    # 分かれたアイテム（393 側）→ 340 の基本アイテムへの追加対応。
    # compose の fwd（758→393→名前→340）では届かないため、生成後に
    # 758 添字へ直接差し込む。
    extra_fwd393 = {}
    for name393, base340 in ITEM_NAME393_TO_BASE340.items():
        if name393 in item_id393 and base340 in item_id340_by_name:
            extra_fwd393[item_id393[name393]] = item_id340_by_name[base340]
    for n393 in items393:
        if n393.endswith("_spawn_egg"):
            extra_fwd393[item_id393[n393]] = item_id340_by_name["spawn_egg"]
    report["item_missing"] = item_missing
    item_size340 = max(it["id"] for it in items340) + 1
    item_fwd, item_rev = compose(t393["ITEM"]["fwd"], t393["ITEM"]["rev"],
                                 item_map, 1, item_id393["stone"], item_size340)
    for i758 in range(len(item_fwd)):
        id393 = t393["ITEM"]["fwd"][i758]
        if id393 in extra_fwd393:
            item_fwd[i758] = extra_fwd393[id393]

    # ---- エンティティ ----
    e113 = load(Path(args.mcdata) / "pc" / "1.13.2" / "entities.json")
    # 1.13.2 データには 'iron_golem}' のような末尾記号の混入があるため正規化する
    mob113 = {re.sub(r"[^a-z0-9_]", "", x["name"]): x["id"]
              for x in e113 if x["type"] == "mob"}
    ent_map = {}
    ent_missing = []
    for eid, ename in entities340:
        nm = ENTITY_RENAME_340_TO_393.get(ename, ename)
        if nm in mob113:
            ent_map[eid] = mob113[nm]
        else:
            ent_missing.append((eid, ename, nm))
    report["entity_missing"] = ent_missing
    ent_size340 = max(e for e, _ in entities340) + 1
    pig340 = next(e for e, n in entities340 if n == "pig")
    ent_fwd, ent_rev = compose(t393["ENTITY_TYPE"]["fwd"],
                               t393["ENTITY_TYPE"]["rev"],
                               ent_map, pig340, mob113["pig"], ent_size340)

    # ---- 音 ----
    snd113 = vv113["sounds"]
    snd_id113 = {n: i for i, n in enumerate(snd113)}
    snd_map = {}
    snd_missing = []
    for i, nm in enumerate(sounds340):
        n113 = sound_name_340_to_393(nm)
        if n113 in snd_id113:
            snd_map[i] = snd_id113[n113]
        else:
            snd_missing.append((i, nm, n113))
    report["sound_missing"] = snd_missing
    generic340 = sounds340.index("block.stone.break")
    snd_fwd, snd_rev = compose(t393["SOUND"]["fwd"], t393["SOUND"]["rev"],
                               snd_map, generic340,
                               snd_id113["block.stone.break"], len(sounds340))

    # ---- パーティクル ----
    par113 = vv113["particles"]
    par_id113 = {n: i for i, n in enumerate(par113)}
    par_map = {}
    par_missing = []
    for p in particles340:
        n113 = PARTICLE_RENAME_340_TO_393.get(p["name"], p["name"])
        if n113 in par_id113:
            par_map[p["id"]] = par_id113[n113]
        else:
            par_missing.append((p["id"], p["name"], n113))
    report["particle_missing"] = par_missing
    par_size340 = max(p["id"] for p in particles340) + 1
    par_fwd, par_rev = compose(t393["PARTICLE"]["fwd"], t393["PARTICLE"]["rev"],
                               par_map, 0, par_id113["poof"], par_size340)

    # ---- バイオーム ----
    bio113 = load(Path(args.mcdata) / "pc" / "1.13.2" / "biomes.json")
    bio_id113 = {b["name"]: b["id"] for b in bio113}
    bio_map = {}
    bio_missing = []
    for b in biomes340:
        nm = BIOME_RENAME_340_TO_393.get(b["name"], b["name"])
        if nm in bio_id113:
            bio_map[b["id"]] = bio_id113[nm]
        else:
            bio_missing.append((b["id"], b["name"]))
    report["biome_missing"] = bio_missing
    bio_size340 = max(b["id"] for b in biomes340) + 1
    bio_fwd, bio_rev = compose(t393["BIOME"]["fwd"], t393["BIOME"]["rev"],
                               bio_map, 1, bio_id113["plains"], bio_size340)
    biome_names340 = [None] * bio_size340
    for b in biomes340:
        biome_names340[b["id"]] = b["name"]
    biome_names340 = [n if n is not None else "plains"
                      for n in biome_names340]

    # ---- ブロックエンティティは名前で合成（340 は conduit が無い 24 件） ----
    be_names393 = be_names_from_java(args)
    be_names = [n for n in be_names393 if n != "conduit"]
    be_map = {i: be_names393.index(n) for i, n in enumerate(be_names)}
    be_fwd, be_rev = compose(t393["BLOCK_ENTITY"]["fwd"],
                             t393["BLOCK_ENTITY"]["rev"],
                             be_map, 0, 0, len(be_names))
    report["be_names"] = be_names

    tables = {
        "BLOCK_STATE": (bs_fwd, bs_rev),
        "ITEM": (item_fwd, item_rev),
        "ENTITY_TYPE": (ent_fwd, ent_rev),
        "SOUND": (snd_fwd, snd_rev),
        "PARTICLE": (par_fwd, par_rev),
        "BLOCK": (blk_fwd, blk_rev),
        "BLOCK_ENTITY": (be_fwd, be_rev),
        "BIOME": (bio_fwd, bio_rev),
        # ダメージ種別はワイヤに無い概念。393 と同じく 404 由来の表をそのまま使う
        "DAMAGE_TYPE": (list(t393["DAMAGE_TYPE"]["fwd"]),
                        list(t393["DAMAGE_TYPE"]["rev"])),
    }
    meta = {
        "generic_sound_340": generic340,
        "pig_340": pig340,
        "be_names": be_names,
        "biome_names": biome_names340,
        "sounds340": sounds340,
        "snd_id113": snd_id113,
        # rev 側（758 空間）の代替値。393 の表から逆引きする
        "pig_758": t393["ENTITY_TYPE"]["rev"][mob113["pig"]],
        "stone_break_758": t393["SOUND"]["rev"][snd_id113["block.stone.break"]],
        "plains_758": t393["BIOME"]["rev"][bio_id113["plains"]],
    }
    return tables, report, meta


def be_names_from_java(args):
    """生成済み RegistryTables393.java から BLOCK_ENTITY_NAMES_393 を読む。"""
    src_path = Path(__file__).parent.parent / "src" / "main" / "java" / \
        "io" / "detergentmc" / "protocol" / "RegistryTables393.java"
    src = src_path.read_text(encoding="utf-8")
    m = re.search(r"BLOCK_ENTITY_NAMES_393 = \{(.*?)\};", src, re.S)
    return re.findall(r'"([^"]+)"', m.group(1))


def sound_name_fallbacks(meta, mcdata):
    """音の名前フォールバック対（758 名前 ⇔ 340 名前）を作る。

    原則は 404 世代の表と同じ：758→340 は「758 にしか無い名前」の
    近似と「改名対」、340→758 は「真の改名対」だけ。同一名は表に
    載せない（数値表と名前の素通しが担う）。近似対を逆向きに
    反転してはいけない（block.stone.break が block.tuff.break に
    化けるような誤対ができるため）。
    """
    snd340 = meta["sounds340"]
    by113 = {}
    for nm in snd340:
        by113.setdefault(sound_name_340_to_393(nm), nm)
    snd758 = load(Path(mcdata) / "mapping-1.18.json")["sounds"]
    snd758_set = set(snd758)
    snd393_set = set(load(Path(mcdata) / "mapping-1.13.json")["sounds"])
    fwd393 = {}
    rev393 = {}
    p = Path(__file__).with_name("sound-names-758-to-393.json")
    if p.exists():
        for a, b in load(p):
            fwd393[a] = b
    p = Path(__file__).with_name("sound-names-393-to-758.json")
    if p.exists():
        for a, b in load(p):
            rev393[a] = b
    # 340→758：改名で 758 の実在名へ届くものだけを対にする。
    rev_pairs = []
    for nm in snd340:
        n113 = sound_name_340_to_393(nm)
        if n113 in snd758_set:
            n758 = n113
        elif n113 in rev393:
            n758 = rev393[n113]
        else:
            continue
        if n758 != nm:
            rev_pairs.append((nm, n758))
    # 758→340：対応（同一・改名）があるものは改名対だけ、
    # 対応が無い 758 の音はトークン類似で最も近い 340 の音へ近似。
    fwd_pairs = []
    for nm758 in snd758:
        n113 = nm758 if nm758 in snd393_set else fwd393.get(nm758)
        if n113 is not None and n113 in by113:
            nm340 = by113[n113]
            if nm340 != nm758:
                fwd_pairs.append((nm758, nm340))
            continue
        toks = (n113 or nm758).split(".")
        best = None
        best_score = -1
        for cand113, cand340 in by113.items():
            ct = cand113.split(".")
            score = 0
            for a, b in zip(toks, ct):
                if a == b:
                    score += 1
                else:
                    break
            if score > best_score:
                best_score = score
                best = cand340
        if best is not None and best_score >= 1:
            fwd_pairs.append((nm758, best))
    return fwd_pairs, rev_pairs


def emit_java(tables, meta, snd_names, snd_names_rev):
    header = """package io.detergentmc.protocol;

/**
 * 340（1.12.2）のレジストリ対応表（区間＋上書きの生の表）。
 *
 * <p>{@link RegistryRemapper} から分離した。理由はバイトコードの制限：
 * 表を RegistryRemapper に置くと静的初期化子（{@code <clinit>}）が
 * 64KB を超えてコンパイルできなくなる（"code too large"）ため。
 * このクラスの表は {@link RegistryRemapper} から static import で
 * 同じ名前のまま参照する。
 *
 * <p>表の内容と検証は {@code detergentmc-protocol/tools/} を参照：
 * 生成は {@code gen-340-tables.py}、検証は {@code verify-340-tables.py}。
 * 作り方は 393 の表との合成：340⇔393 の対応を名前と意味で確定し、
 * 393⇔758 の表（gen-393-tables.py）をつないだ。340 は Flattening 前で
 * ブロックに状態が無く、値は (数値ID&lt;&lt;4)|メタデータ（4096 通り）。
 * (id, meta) → 393 状態の意味対応は minecraft-data 1.12 の variations
 * （description の体系的な記述）を主資料に、torch 等のずれている箇所は
 * 古典メタ仕様で上書きした。エンティティ（83 件）と音（549 件）の
 * 登録順は実バニラ 1.12.2 jar のバイトコードから抽出した
 * （extract-340-registries.py）。
 *
 * <p>近似・制限（詳細は docs/PROXY-1.12.2.md）：
 * ベッドとバナーの色は 1.12 ではブロックエンティティ側にあり
 * ワイヤのメタに無いため、ベッドは red_bed、バナーは white に寄せる。
 * 頭蓋は種別がブロックエンティティ側なので skeleton に寄せる。
 * スポーンエッグは種別が NBT 側なので pig_spawn_egg に寄せる
 * （アイテムの itemDamage 由来の種別は SlotTranslator が付け替える）。
 */
final class RegistryTables340 {

    private RegistryTables340() {
    }

"""
    parts = [header]
    chunk_files = {}
    # 符号化時の代替値（expand が埋める値）。gen-393 と同じ規約
    fallbacks_fwd = {
        "BLOCK_STATE": 16, "ITEM": 1, "ENTITY_TYPE": meta["pig_340"],
        "SOUND": meta["generic_sound_340"], "PARTICLE": 0, "BLOCK": 1,
        "BLOCK_ENTITY": -1, "BIOME": 1, "DAMAGE_TYPE": 16,
    }
    fallbacks_rev = {
        "BLOCK_STATE": 1, "ITEM": 1, "ENTITY_TYPE": meta["pig_758"],
        "SOUND": meta["stone_break_758"], "PARTICLE": 0, "BLOCK": 1,
        "BLOCK_ENTITY": -1, "BIOME": meta["plains_758"], "DAMAGE_TYPE": 16,
    }
    for name, (fwd, rev) in tables.items():
        fr, fo = ranges_overrides(fwd, len(fwd), fallbacks_fwd[name])
        rr, ro = ranges_overrides(rev, len(rev), fallbacks_rev[name])
        n_fwd, n_rev = len(fwd), len(rev)
        if name == "BLOCK_STATE":
            def emit_chunks(prefix, overrides):
                names = []
                for ci in range(0, len(overrides), 1200):
                    chunk = overrides[ci:ci + 1200]
                    cname = f"RegistryTables340{prefix}{ci // 1200 + 1}"
                    body = ["package io.detergentmc.protocol;",
                            "",
                            f"/** 340 のブロック状態の上書き表の分割クラス"
                            f"（{prefix}、{ci // 1200 + 1} 個目）。"
                            f"生成は gen-340-tables.py。 */",
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
            parts.append(java_table(f"{name}_340", fr, [], n_fwd, "758->340")
                         .replace(f"static final int[][] {name}_340_OVERRIDES = {{",
                                  f"static final int[][] {name}_340_OVERRIDES_PLACEHOLDER = {{"))
            parts[-1] = parts[-1].replace(
                f"    static final int[][] {name}_340_OVERRIDES_PLACEHOLDER = {{\n    }};",
                f"    static final int[][] {name}_340_OVERRIDES = concat("
                + ", ".join(fwd_names) + ");")
            parts.append("")
            parts.append(java_table(f"{name}_340_TO_758", rr, [], n_rev, "340->758")
                         .replace(f"static final int[][] {name}_340_TO_758_OVERRIDES = {{",
                                  f"static final int[][] {name}_340_TO_758_OVERRIDES_PLACEHOLDER = {{"))
            parts[-1] = parts[-1].replace(
                f"    static final int[][] {name}_340_TO_758_OVERRIDES_PLACEHOLDER = {{\n    }};",
                f"    static final int[][] {name}_340_TO_758_OVERRIDES = concat("
                + ", ".join(rev_names) + ");")
            parts.append("")
            continue
        parts.append(java_table(f"{name}_340", fr, fo, n_fwd, "758->340"))
        parts.append("")
        parts.append(java_table(f"{name}_340_TO_758", rr, ro, n_rev, "340->758"))
        parts.append("")
    # 音の名前表は RegistryTables340Snd へ分離する（340 の clinit が
    # 64KB 制限を超えるため。ブロック状態の Bs 分割と同じ理由）。
    parts.append("    /** 音の名前：758 → 340（Stop Sound 用）。実体は RegistryTables340Snd。 */")
    parts.append("    static final String[][] SOUND_NAME_FALLBACKS_758_TO_340 ="
                 " RegistryTables340Snd.FWD;")
    parts.append("")
    parts.append("    /** 音の名前：340 → 758（Stop Sound 用）。実体は RegistryTables340Snd。 */")
    parts.append("    static final String[][] SOUND_NAME_FALLBACKS_340_TO_758 ="
                 " RegistryTables340Snd.REV;")
    parts.append("")
    snd_body = ["package io.detergentmc.protocol;",
                "",
                "/**",
                " * 340（1.12.2）の音の名前フォールバック表（分離クラス）。",
                " *",
                " * <p>RegistryTables340 の静的初期化子が 64KB 制限を超えるため",
                " * 音の名前表だけをここへ分けた。生成は tools/gen-340-tables.py、",
                " * 検証は tools/verify-340-tables.py。",
                " */",
                "final class RegistryTables340Snd {",
                "",
                "    private RegistryTables340Snd() {",
                "    }",
                "",
                "    /** 音の名前：758 → 340（Stop Sound 用）。数値表で届かない音の寄せ先。 */",
                "    static final String[][] FWD = {"]
    for src, dst in snd_names:
        snd_body.append(f'            {{"{src}", "{dst}"}},')
    snd_body.append("    };")
    snd_body.append("")
    snd_body.append("    /** 音の名前：340 → 758（Stop Sound 用）。1.13 での改名を戻す。 */")
    snd_body.append("    static final String[][] REV = {")
    for src, dst in snd_names_rev:
        snd_body.append(f'            {{"{src}", "{dst}"}},')
    snd_body.append("    };")
    snd_body.append("}")
    chunk_files["RegistryTables340Snd"] = "\n".join(snd_body) + "\n"

    def names_array(field, names):
        lines = [f"    static final String[] {field} = {{"]
        for i in range(0, len(names), 4):
            chunk = names[i:i + 4]
            lines.append("            " + " ".join(f'"{n}",' for n in chunk))
        lines.append("    };")
        return "\n".join(lines)
    parts.append(names_array("BLOCK_ENTITY_NAMES_340", meta["be_names"]))
    parts.append("")
    parts.append(names_array("BIOME_NAMES_340", meta["biome_names"]))
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
    return "\n".join(parts), chunk_files


def main():
    ap = argparse.ArgumentParser()
    base = Path.home() / "workspace"
    ap.add_argument("--data340", default=str(base / "legacy-prep-340" / "mcdata" / "1.12"))
    ap.add_argument("--gen113", default=str(base / "legacy-prep-393" / "gen-1.13" / "generated" / "reports"))
    ap.add_argument("--tables393", default=str(base / "mcdata404" / "tables-393.json"))
    ap.add_argument("--mcdata", default=str(base / "mcdata404"))
    ap.add_argument("--out", default=str(Path(__file__).parent.parent / "src" / "main" / "java" / "io" / "detergentmc" / "protocol" / "RegistryTables340.java"))
    ap.add_argument("--json", default=None)
    args = ap.parse_args()

    tables, report, meta = build_all(args)
    for key in ("item_missing", "entity_missing", "sound_missing",
                "particle_missing", "biome_missing", "resolve_errors"):
        miss = report.get(key, [])
        print(f"{key}: {len(miss)} 件")
        for m in miss[:40]:
            print("   ", m)
    print("block_state:", report["block_state"])
    print("MAX 値: BLOCK_STATE_340=4095, ITEM_340="
          f"{len(tables['ITEM'][1]) - 1}, ENTITY_TYPE_340="
          f"{len(tables['ENTITY_TYPE'][1]) - 1}, SOUND_340="
          f"{len(tables['SOUND'][1]) - 1}, PARTICLE_340="
          f"{len(tables['PARTICLE'][1]) - 1}, BLOCK_340=255, BIOME_340="
          f"{len(tables['BIOME'][1]) - 1}, BLOCK_ENTITY_340="
          f"{len(tables['BLOCK_ENTITY'][1]) - 1}, DAMAGE_TYPE="
          f"{len(tables['DAMAGE_TYPE'][1]) - 1}")
    print(f"代替: GENERIC_SOUND_340={meta['generic_sound_340']}, "
          f"PIG_340={meta['pig_340']}")

    snd_names, snd_names_rev = sound_name_fallbacks(meta, args.mcdata)
    print(f"音の名前フォールバック: 758→340 {len(snd_names)} 対、"
          f"340→758 {len(snd_names_rev)} 対")
    tools_dir = Path(__file__).parent
    (tools_dir / "sound-names-758-to-340.json").write_text(
        json.dumps(snd_names, ensure_ascii=False) + "\n", encoding="utf-8")
    (tools_dir / "sound-names-340-to-758.json").write_text(
        json.dumps(snd_names_rev, ensure_ascii=False) + "\n", encoding="utf-8")

    if args.json:
        dump = {k: {"fwd": v[0], "rev": v[1]} for k, v in tables.items()}
        Path(args.json).write_text(json.dumps(dump), encoding="utf-8")
        print(f"JSON を書き出した: {args.json}")

    java, chunk_files = emit_java(tables, meta, snd_names, snd_names_rev)
    out = Path(args.out)
    out.write_text(java, encoding="utf-8")
    print(f"Java を書き出した: {out}（{len(java)} バイト）")
    for cname, body in chunk_files.items():
        p = out.with_name(cname + ".java")
        p.write_text(body, encoding="utf-8")
        print(f"  分割クラス: {p.name}（{len(body)} バイト）")


if __name__ == "__main__":
    main()
