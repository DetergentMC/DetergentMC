"""プロトコル 316（1.11.2）のレジストリ対応表を生成する。

316 は 335（1.12）の一つ前で、同じ Flattening 前の世代。
Stage A の実測（docs/PROXY-1.11.2.md）で確定した 335／340 との差分は
小さく、表は 340 の表（tables-340.json、gen-340-tables.py --json で
再生成し、生成済み Java とバイト一致を確認済み）からの合成で作る：

- ブロック 236 種 ⇔ 254 種。1.12 追加の 18 種（彩釉テラコッタ
  235〜250、コンクリート 251、コンクリートパウダー 252）が 316 に
  無い。共有ブロックの (id, meta) 意味は不変（minecraft-data の
  pc/1.11 と pc/1.12 で共有名の数値 ID が全件同一なことを照合）。
  316 に無いブロックの近似（758→316 方向）：
  コンクリート／パウダー → 羊毛（色＝メタを保持）、
  彩釉テラコッタ → 色付き堅焼き粘土（色＝ID−235 を保持、facing は落とす）。
- アイテム 390 種 ⇔ 410 種。追加 20 件は上記 18 件のアイテム形と
  iron_nugget（452）・knowledge_book（453）。ただし iron_nugget は
  1.11.1（＝316）で追加済みで 316 自身に存在するため近似せず同一に
  通す（315＝1.11 には無い。当初の生成では 1.12 追加分と誤って
  金塊へ寄せていたのを 315 の Stage E で修正）。近似：
  コンクリート系アイテム → 羊毛アイテム（damage＝色）、
  彩釉テラコッタ → 色付き堅焼き粘土（damage＝色）、
  knowledge_book → book。
  totem → totem_of_undying は改名のみ（ID 449 同一）。
- エンティティ 81 件 ⇔ 83 件（実 jar 抽出 entities-316.json）。
  追加は illusion_illager（37）と parrot（105）のみで、758→316 は
  既存規約どおり pig（90）へ寄せる。共有 ID は名前とも完全一致。
- 音 493 件 ⇔ 549 件（実 jar 抽出 sounds-316.json）。316 の名前は
  全て 340 側に存在（改名なし）するため、名前で合成する。
  316 に無い音は汎用フォールバック（block.stone.break、316 では
  添字 397）へ寄せる。
- パーティクル 49・効果 27・バイオーム 62 は ID→名前が完全一致
  するため 340 の表をそのまま使う。
- ブロックエンティティ 23 件 ⇔ 24 件（bed が 1.12 追加で最後尾。
  316 側の添字 0〜22 は不変。758→316 の bed は -1＝落とす）。

使い方:
  python3 gen-316-tables.py [--tables340 FILE] [--mcdata11 DIR]
          [--mcdata12 DIR] [--mcdata DIR] [--out FILE] [--json FILE]
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


def parse_java_names(field):
    """生成済み RegistryTables340.java から名前配列を読む。"""
    src_path = Path(__file__).parent.parent / "src" / "main" / "java" / \
        "io" / "detergentmc" / "protocol" / "RegistryTables340.java"
    text = src_path.read_text(encoding="utf-8")
    m = re.search(field + r" = \{(.*?)\};", text, re.S)
    return re.findall(r'"([^"]+)"', m.group(1))


# 316 に無い 1.12 新規ブロック（数値ID）。Stage A 実測。
GLAZED_TERRACOTTA = range(235, 251)   # 235+色
CONCRETE = 251
CONCRETE_POWDER = 252
WOOL = 35
STAINED_CLAY = 159
NEW_BLOCKS_112 = set(GLAZED_TERRACOTTA) | {CONCRETE, CONCRETE_POWDER}
# 1.11（315）には無い 1.12 新規アイテム（数値ID）。minecraft-data の
# pc/1.11（＝1.11.0）との照合に使う。
NEW_ITEMS_112 = set(range(235, 253)) | {452, 453}
# 316（1.11.2）に無いアイテム。iron_nugget（452）は 1.11.1 で追加され
# 316 に存在するため除く（knowledge_book 453 は 1.12 追加で 316 に無い）。
MISSING_ITEMS_316 = NEW_ITEMS_112 - {452}
# 316 に無い 1.12 新規エンティティ（登録ID）。
NEW_ENTITIES_112 = {37, 105}   # illusion_illager, parrot
PIG = 90


def approx_block_state_316(v340):
    """340 の (id<<4)|meta を 316 の値へ近似する（316 に無いブロック）。"""
    blk, meta = v340 >> 4, v340 & 15
    if blk in GLAZED_TERRACOTTA:
        # 色はブロックID側（235+色）。色を保ち facing を落とす。
        return (STAINED_CLAY << 4) | (blk - 235)
    if blk in (CONCRETE, CONCRETE_POWDER):
        # メタ＝色（羊毛と同順）。色を保って羊毛へ。
        return (WOOL << 4) | meta
    return v340


def approx_item_316(v340):
    """340 のアイテムIDを 316 の値へ近似する（316 に無いアイテム）。"""
    if v340 in GLAZED_TERRACOTTA:
        return STAINED_CLAY
    if v340 in (CONCRETE, CONCRETE_POWDER):
        return WOOL
    # iron_nugget（452）は 1.11.1 で追加され 316 に存在するため同一。
    if v340 == 453:   # knowledge_book
        return 340    # book
    return v340


def approx_block_316(v340):
    if v340 in GLAZED_TERRACOTTA:
        return STAINED_CLAY
    if v340 in (CONCRETE, CONCRETE_POWDER):
        return WOOL
    return v340


def build_all(args):
    t340 = load(args.tables340)
    tools = Path(__file__).parent
    snd316 = load(tools / "sounds-316.json")
    snd340 = load(tools / "sounds-340.json")
    ent316 = load(tools / "entities-316.json")
    ent340 = load(tools / "entities-340.json")
    mc11 = Path(args.mcdata11)
    mc12 = Path(args.mcdata12)

    # ---- Stage A 実測の裏付け照合（食い違えば例外で止める） ----
    b11 = {b["name"]: b["id"] for b in load(mc11 / "blocks.json")}
    b12 = {b["name"]: b["id"] for b in load(mc12 / "blocks.json")}
    assert len(b11) == 236 and len(b12) == 254, (len(b11), len(b12))
    for nm, i in b11.items():
        assert b12[nm] == i, f"ブロックID不一致: {nm}"
    assert {i for nm, i in b12.items() if nm not in b11} == NEW_BLOCKS_112
    i11 = {b["name"]: b["id"] for b in load(mc11 / "items.json")}
    i12 = {b["name"]: b["id"] for b in load(mc12 / "items.json")}
    assert len(i11) == 390 and len(i12) == 410, (len(i11), len(i12))
    for nm, i in i11.items():
        if nm in i12:
            assert i12[nm] == i, f"アイテムID不一致: {nm}"
    new_item_ids = {i for nm, i in i12.items() if nm not in i11}
    assert new_item_ids == NEW_ITEMS_112 | {449}, new_item_ids
    # 449 は totem → totem_of_undying の改名（ID同一）
    assert i11["totem"] == i12["totem_of_undying"] == 449
    e316 = {i: nm for i, nm in ent316}
    e340 = {i: nm for i, nm in ent340}
    assert len(e316) == 81 and len(e340) == 83
    for i, nm in e316.items():
        assert e340[i] == nm, f"エンティティ不一致: {i} {nm} vs {e340.get(i)}"
    assert {i for i in e340 if i not in e316} == NEW_ENTITIES_112
    assert set(snd316) <= set(snd340), "316にだけある音がある"
    assert len(snd316) == 493 and len(snd340) == 549
    be340 = parse_java_names("BLOCK_ENTITY_NAMES_340")
    assert len(be340) == 24 and be340[-1] == "bed", be340
    be316 = be340[:23]
    biome_names = parse_java_names("BIOME_NAMES_340")

    def T(name):
        return t340[name]["fwd"], t340[name]["rev"]

    tables = {}

    f, r = T("BLOCK_STATE")
    # rev の無効組合せ値（340 の表で実在しない (id,meta) に入っている値）
    invalid_bs = r[(1 << 4) | 15]
    assert all(r[(1 << 4) | m] == invalid_bs for m in (13, 14, 15))
    tables["BLOCK_STATE"] = (
        [approx_block_state_316(v) for v in f],
        [invalid_bs if (i >> 4) in NEW_BLOCKS_112 else v
         for i, v in enumerate(r)])

    f, r = T("ITEM")
    tables["ITEM"] = (
        [approx_item_316(v) for v in f],
        [1 if i in MISSING_ITEMS_316 else v for i, v in enumerate(r)])

    f, r = T("ENTITY_TYPE")
    tables["ENTITY_TYPE"] = (
        [PIG if v in NEW_ENTITIES_112 else v for v in f],
        [r[PIG] if i in NEW_ENTITIES_112 else v for i, v in enumerate(r)])

    f, r = T("SOUND")
    idx316 = {nm: i for i, nm in enumerate(snd316)}
    idx340 = {nm: i for i, nm in enumerate(snd340)}
    generic316 = idx316["block.stone.break"]
    tables["SOUND"] = (
        [idx316.get(snd340[v], generic316) for v in f],
        [r[idx340[nm]] for nm in snd316])

    f, r = T("PARTICLE")
    tables["PARTICLE"] = (list(f), list(r))
    f, r = T("BLOCK")
    tables["BLOCK"] = (
        [approx_block_316(v) for v in f],
        [1 if i in NEW_BLOCKS_112 else v for i, v in enumerate(r)])
    f, r = T("BLOCK_ENTITY")
    tables["BLOCK_ENTITY"] = (
        [-1 if v == 23 else v for v in f], list(r[:23]))
    f, r = T("BIOME")
    tables["BIOME"] = (list(f), list(r))
    f, r = T("DAMAGE_TYPE")
    tables["DAMAGE_TYPE"] = (list(f), list(r))

    meta = {
        "generic_sound_316": generic316,
        "pig_316": PIG,
        "pig_758": tables["ENTITY_TYPE"][1][PIG],
        "stone_break_758": tables["SOUND"][1][generic316],
        "plains_758": tables["BIOME"][1][1],
        "be_names": be316,
        "biome_names": biome_names,
        "sounds316": snd316,
    }
    report = {
        "item_missing": [], "entity_missing": [], "sound_missing": [],
        "particle_missing": [], "biome_missing": [], "resolve_errors": [],
        "block_state": ("316: ブロック状態は (id<<4)|meta の 4096 通り。"
                        "1.12 新規ブロック 288 通り（235〜252）は近似、"
                        "316→758 の当該範囲は石へ落とす。"),
    }
    return tables, report, meta


def sound_name_fallbacks(meta, mcdata):
    """音の名前フォールバック対（758 名前 ⇔ 316 名前）を作る。

    316 の音名は全て 340 に存在するため、340 の対（真の改名対と
    近似対）を土台に合成する。316→758 は 340 の改名対を 316 の
    名前に絞ったもの。758→316 は 340 への対の届け先が 316 に
    あるものを使い、届け先が 1.12 追加の音（316 に無い）場合は
    340 世代と同じトークン類似で最も近い 316 の音へ近似する。
    同一名は表に載せない（数値表と名前の素通しが担う）。
    """
    snd316 = meta["sounds316"]
    tools = Path(__file__).parent
    snd340 = load(tools / "sounds-340.json")
    snd340_set = set(snd340)
    snd316_set = set(snd316)
    fwd340 = dict(load(tools / "sound-names-758-to-340.json"))
    rev340 = dict(load(tools / "sound-names-340-to-758.json"))
    snd758 = load(Path(mcdata) / "mapping-1.18.json")["sounds"]
    snd758_set = set(snd758)

    def name758_of_old(nm):
        if nm in rev340:
            return rev340[nm]
        if nm in snd758_set:
            return nm
        return None

    rev_pairs = []
    for nm in snd316:
        n758 = name758_of_old(nm)
        if n758 is not None and n758 != nm:
            rev_pairs.append((nm, n758))

    def approx316(base):
        toks = base.split(".")
        best = None
        best_score = -1
        for cand in snd316:
            ct = cand.split(".")
            score = 0
            for a, b in zip(toks, ct):
                if a == b:
                    score += 1
                else:
                    break
            if score > best_score:
                best_score = score
                best = cand
        return best if best_score >= 1 else None

    fwd_pairs = []
    for nm758 in snd758:
        t = fwd340.get(nm758)
        if t is None and nm758 in snd340_set:
            t = nm758
        if t is not None and t in snd316_set:
            if t != nm758:
                fwd_pairs.append((nm758, t))
            continue
        best = approx316(t if t is not None else nm758)
        if best is not None and best != nm758:
            fwd_pairs.append((nm758, best))
    return fwd_pairs, rev_pairs


def emit_java(tables, meta, snd_names, snd_names_rev):
    header = """package io.detergentmc.protocol;

/**
 * 316（1.11.2）のレジストリ対応表（区間＋上書きの生の表）。
 *
 * <p>{@link RegistryRemapper} から分離した。理由はバイトコードの制限：
 * 表を RegistryRemapper に置くと静的初期化子（{@code <clinit>}）が
 * 64KB を超えてコンパイルできなくなる（"code too large"）ため。
 * このクラスの表は {@link RegistryRemapper} から static import で
 * 同じ名前のまま参照する。
 *
 * <p>表の内容と検証は {@code detergentmc-protocol/tools/} を参照：
 * 生成は {@code gen-316-tables.py}、検証は {@code verify-316-tables.py}。
 * 作り方は 340 の表との合成：316 は 340 と同じ Flattening 前の世代で
 * 値は (数値ID&lt;&lt;4)|メタデータ（4096 通り）。340 との差分は
 * 1.12 追加分だけ（ブロック 18 種・アイテム 20 件・エンティティ
 * 2 種・音 56 件・ブロックエンティティ bed）で、共有分の意味対応は
 * 340 の表をそのまま使う。エンティティ（81 件）と音（493 件）の
 * 登録順は実バニラ 1.11.2 jar のバイトコードから抽出した
 * （extract-316-registries.py）。
 *
 * <p>近似・制限（詳細は docs/PROXY-1.11.2.md）：
 * 316 に無い 1.12 のブロックは色を保って寄せる（コンクリート系は
 * 羊毛へ、彩釉テラコッタは色付き堅焼き粘土へ。facing は落ちる）。
 * knowledge_book は book へ寄せる。iron_nugget は 1.11.1 で
 * 追加され 316 に存在するため同一に通す。
 * parrot と illusion_illager は pig へ寄せる。bed のブロック
 * エンティティは落とす。その他の近似は 340 と同じ（ベッドと
 * バナーの色、頭蓋の種別、スポーンエッグの種別）。
 */
final class RegistryTables316 {

    private RegistryTables316() {
    }

"""
    parts = [header]
    chunk_files = {}
    # 符号化時の代替値（expand が埋める値）。gen-393 と同じ規約
    fallbacks_fwd = {
        "BLOCK_STATE": 16, "ITEM": 1, "ENTITY_TYPE": meta["pig_316"],
        "SOUND": meta["generic_sound_316"], "PARTICLE": 0, "BLOCK": 1,
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
                    cname = f"RegistryTables316{prefix}{ci // 1200 + 1}"
                    body = ["package io.detergentmc.protocol;",
                            "",
                            f"/** 316 のブロック状態の上書き表の分割クラス"
                            f"（{prefix}、{ci // 1200 + 1} 個目）。"
                            f"生成は gen-316-tables.py。 */",
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
            parts.append(java_table(f"{name}_316", fr, [], n_fwd, "758->316")
                         .replace(f"static final int[][] {name}_316_OVERRIDES = {{",
                                  f"static final int[][] {name}_316_OVERRIDES_PLACEHOLDER = {{"))
            parts[-1] = parts[-1].replace(
                f"    static final int[][] {name}_316_OVERRIDES_PLACEHOLDER = {{\n    }};",
                f"    static final int[][] {name}_316_OVERRIDES = concat("
                + ", ".join(fwd_names) + ");")
            parts.append("")
            parts.append(java_table(f"{name}_316_TO_758", rr, [], n_rev, "316->758")
                         .replace(f"static final int[][] {name}_316_TO_758_OVERRIDES = {{",
                                  f"static final int[][] {name}_316_TO_758_OVERRIDES_PLACEHOLDER = {{"))
            parts[-1] = parts[-1].replace(
                f"    static final int[][] {name}_316_TO_758_OVERRIDES_PLACEHOLDER = {{\n    }};",
                f"    static final int[][] {name}_316_TO_758_OVERRIDES = concat("
                + ", ".join(rev_names) + ");")
            parts.append("")
            continue
        parts.append(java_table(f"{name}_316", fr, fo, n_fwd, "758->316"))
        parts.append("")
        parts.append(java_table(f"{name}_316_TO_758", rr, ro, n_rev, "316->758"))
        parts.append("")
    # 音の名前表は RegistryTables316Snd へ分離する（340 の clinit が
    # 64KB 制限を超えるため。ブロック状態の Bs 分割と同じ理由）。
    parts.append("    /** 音の名前：758 → 316（Stop Sound 用）。実体は RegistryTables316Snd。 */")
    parts.append("    static final String[][] SOUND_NAME_FALLBACKS_758_TO_316 ="
                 " RegistryTables316Snd.FWD;")
    parts.append("")
    parts.append("    /** 音の名前：316 → 758（Stop Sound 用）。実体は RegistryTables316Snd。 */")
    parts.append("    static final String[][] SOUND_NAME_FALLBACKS_316_TO_758 ="
                 " RegistryTables316Snd.REV;")
    parts.append("")
    snd_body = ["package io.detergentmc.protocol;",
                "",
                "/**",
                " * 340（1.12.2）の音の名前フォールバック表（分離クラス）。",
                " *",
                " * <p>RegistryTables316 の静的初期化子が 64KB 制限を超えるため",
                " * 音の名前表だけをここへ分けた。生成は tools/gen-316-tables.py、",
                " * 検証は tools/verify-316-tables.py。",
                " */",
                "final class RegistryTables316Snd {",
                "",
                "    private RegistryTables316Snd() {",
                "    }",
                "",
                "    /** 音の名前：758 → 316（Stop Sound 用）。数値表で届かない音の寄せ先。 */",
                "    static final String[][] FWD = {"]
    for src, dst in snd_names:
        snd_body.append(f'            {{"{src}", "{dst}"}},')
    snd_body.append("    };")
    snd_body.append("")
    snd_body.append("    /** 音の名前：316 → 758（Stop Sound 用）。1.13 での改名を戻す。 */")
    snd_body.append("    static final String[][] REV = {")
    for src, dst in snd_names_rev:
        snd_body.append(f'            {{"{src}", "{dst}"}},')
    snd_body.append("    };")
    snd_body.append("}")
    chunk_files["RegistryTables316Snd"] = "\n".join(snd_body) + "\n"

    def names_array(field, names):
        lines = [f"    static final String[] {field} = {{"]
        for i in range(0, len(names), 4):
            chunk = names[i:i + 4]
            lines.append("            " + " ".join(f'"{n}",' for n in chunk))
        lines.append("    };")
        return "\n".join(lines)
    parts.append(names_array("BLOCK_ENTITY_NAMES_316", meta["be_names"]))
    parts.append("")
    parts.append(names_array("BIOME_NAMES_316", meta["biome_names"]))
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
    ap.add_argument("--tables340", default=str(base / "mcdata404" / "tables-340.json"))
    mcdata_pkg = Path("/home/hatch/mc-client/node_modules/minecraft-data/minecraft-data/data/pc")
    ap.add_argument("--mcdata11", default=str(mcdata_pkg / "1.11"))
    ap.add_argument("--mcdata12", default=str(mcdata_pkg / "1.12"))
    ap.add_argument("--mcdata", default=str(base / "mcdata404"))
    ap.add_argument("--out", default=str(Path(__file__).parent.parent / "src" / "main" / "java" / "io" / "detergentmc" / "protocol" / "RegistryTables316.java"))
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
    print("MAX 値: BLOCK_STATE_316=4095, ITEM_316="
          f"{len(tables['ITEM'][1]) - 1}, ENTITY_TYPE_316="
          f"{len(tables['ENTITY_TYPE'][1]) - 1}, SOUND_316="
          f"{len(tables['SOUND'][1]) - 1}, PARTICLE_316="
          f"{len(tables['PARTICLE'][1]) - 1}, BLOCK_316=255, BIOME_316="
          f"{len(tables['BIOME'][1]) - 1}, BLOCK_ENTITY_316="
          f"{len(tables['BLOCK_ENTITY'][1]) - 1}, DAMAGE_TYPE="
          f"{len(tables['DAMAGE_TYPE'][1]) - 1}")
    print(f"代替: GENERIC_SOUND_316={meta['generic_sound_316']}, "
          f"PIG_316={meta['pig_316']}")

    snd_names, snd_names_rev = sound_name_fallbacks(meta, args.mcdata)
    print(f"音の名前フォールバック: 758→316 {len(snd_names)} 対、"
          f"316→758 {len(snd_names_rev)} 対")
    tools_dir = Path(__file__).parent
    (tools_dir / "sound-names-758-to-316.json").write_text(
        json.dumps(snd_names, ensure_ascii=False) + "\n", encoding="utf-8")
    (tools_dir / "sound-names-316-to-758.json").write_text(
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
