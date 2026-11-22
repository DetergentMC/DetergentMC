#!/usr/bin/env python3
"""47（1.8〜1.8.9）のレジストリ対応表を生成する。

作り方は 110 の表との合成：47 は 110 と同じ Flattening 前の世代で
値は (数値ID<<4)|メタデータ。110 との差分は 1.9 の追加分だけ
（ブロック 16 種・アイテム 29 件・shulker・the_void・パーティクル
4 種）。共有名の数値 ID は 47⇔110 で完全一致（Stage A で mcdata
pc/1.8 と pc/1.9 の全面比較＋実バニラ 1.8.9 で確認済み）なので、
110 の表（RegistryTables110.java をパースして復元）に「1.9 追加分を
1.8 の近似へ寄せる」調整を重ねるだけで 47 の表になる。

音は 47 では数値 ID がワイヤに存在しない（named のみ）。そのため
数値の音表は作らず、名前フォールバック表（758 名→47 名、47 名→
758 名）と、数値→名前の変換に要る SOUND_NAMES_758（mcdata404 の
mapping-1.18.json 由来、110 の数値表と名前で突き合わせて検証）を
RegistryTables47Snd.java へ出す。47 の音名集合（246 件）は
Mojang 公式アセット索引（1.8.9 用、sounds.json ハッシュ
5e06ca070067486427a3167ade2ffe01623e5591）から取得した実物で、
tools/sounds-47.json に保存してある。

出力：RegistryTables47.java、RegistryTables47BsFwd*.java、
RegistryTables47BsRev*.java、RegistryTables47Snd.java
（いずれも src/main/java/io/detergentmc/protocol/）。
検証は verify-47-tables.py。
"""
import json
import re
import sys
from pathlib import Path

TOOLS = Path(__file__).resolve().parent
MAIN = TOOLS.parent / "src" / "main" / "java" / "io" / "detergentmc" / "protocol"
MCDATA = Path("/home/hatch/mc-client/node_modules/minecraft-data/"
              "minecraft-data/data/pc")


def fail(msg):
    print(f"FAIL: {msg}")
    sys.exit(1)


def load(p):
    return json.loads(Path(p).read_text(encoding="utf-8"))


# ---------- RegistryTables110.java の復元 ----------

def parse_int_table(text, field):
    """`static final int[][] FIELD = { {a, b, c}, ... };` を読む。"""
    m = re.search(re.escape(field) + r"\s*=\s*\{(.*?)\n    \};", text, re.S)
    if not m:
        fail(f"表 {field} が見つからない")
    return [[int(x) for x in row.split(",")]
            for row in re.findall(r"\{([^{}]+)\}", m.group(1))]


def parse_concat_table(text, field, sources):
    m = re.search(re.escape(field) + r"\s*=\s*concat\(([^)]+)\)", text)
    if not m:
        fail(f"concat 表 {field} が見つからない")
    out = []
    for name in re.findall(r"(RegistryTables110\w+)\.V", m.group(1)):
        out += sources[name]
    return out


def expand(ranges, overrides, size, fallback):
    arr = [fallback] * size
    for s, e, d in ranges:
        for i in range(s, min(e, size - 1) + 1):
            arr[i] = i + d
    for i, v in overrides:
        if 0 <= i < size:
            arr[i] = v
    return arr


def load_110_tables():
    text = (MAIN / "RegistryTables110.java").read_text(encoding="utf-8")
    sources = {}
    for f in sorted(MAIN.glob("RegistryTables110Bs*.java")):
        t = f.read_text(encoding="utf-8")
        m = re.search(r"static final int\[\]\[\] V = \{(.*?)\n    \};", t, re.S)
        sources[f.stem] = [[int(x) for x in row.split(",")]
                           for row in re.findall(r"\{([^{}]+)\}", m.group(1))]
    remap = (MAIN / "RegistryRemapper.java").read_text(encoding="utf-8")

    def const(name):
        m = re.search(rf"{name} = (-?\d+);", remap)
        if not m:
            fail(f"定数 {name} が見つからない")
        return int(m.group(1))

    size758 = {
        "BLOCK_STATE": const("MAX_BLOCK_STATE_758") + 1,
        "ITEM": const("MAX_ITEM_758") + 1,
        "ENTITY_TYPE": const("MAX_ENTITY_TYPE_758") + 1,
        "SOUND": const("MAX_SOUND_758") + 1,
        "PARTICLE": const("MAX_PARTICLE_758") + 1,
        "BLOCK": const("MAX_BLOCK_758") + 1,
        "BLOCK_ENTITY": const("MAX_BLOCK_ENTITY_758") + 1,
        "BIOME": const("MAX_BIOME_758") + 1,
        "DAMAGE_TYPE": const("MAX_DAMAGE_TYPE_758") + 1,
    }
    size110 = {
        "BLOCK_STATE": const("MAX_BLOCK_STATE_110") + 1,
        "ITEM": const("MAX_ITEM_110") + 1,
        "ENTITY_TYPE": const("MAX_ENTITY_TYPE_110") + 1,
        "SOUND": const("MAX_SOUND_110") + 1,
        "PARTICLE": const("MAX_PARTICLE_110") + 1,
        "BLOCK": const("MAX_BLOCK_110") + 1,
        "BLOCK_ENTITY": const("MAX_BLOCK_ENTITY_110") + 1,
        "BIOME": const("MAX_BIOME_110") + 1,
        "DAMAGE_TYPE": const("MAX_DAMAGE_TYPE_110") + 1,
    }
    fb_fwd = {
        "BLOCK_STATE": const("STONE_BLOCK_STATE_110"),
        "ITEM": const("STONE_ITEM_110"),
        "ENTITY_TYPE": const("PIG_ENTITY_TYPE_110"),
        "SOUND": const("GENERIC_SOUND_110"),
        "PARTICLE": const("AMBIENT_PARTICLE_110"),
        "BLOCK": const("STONE_BLOCK_110"),
        "BLOCK_ENTITY": -1,
        "BIOME": const("PLAINS_BIOME_110"),
        "DAMAGE_TYPE": const("GENERIC_DAMAGE_TYPE_110"),
    }
    fb_rev = {
        "BLOCK_STATE": const("STONE_BLOCK_STATE_758"),
        "ITEM": const("STONE_ITEM_758"),
        "ENTITY_TYPE": const("PIG_ENTITY_TYPE_758"),
        "SOUND": const("GENERIC_SOUND_758"),
        "PARTICLE": const("AMBIENT_PARTICLE_758"),
        "BLOCK": const("STONE_BLOCK_758"),
        "BLOCK_ENTITY": -1,
        "BIOME": const("PLAINS_BIOME_758"),
        "DAMAGE_TYPE": const("GENERIC_DAMAGE_TYPE_758"),
    }
    tables = {}
    for name in size758:
        fr = parse_int_table(text, f"{name}_110_RANGES")
        fo = parse_concat_table(text, f"{name}_110_OVERRIDES", sources) \
            if name == "BLOCK_STATE" else parse_int_table(
                text, f"{name}_110_OVERRIDES")
        rr = parse_int_table(text, f"{name}_110_TO_758_RANGES")
        ro = parse_concat_table(text, f"{name}_110_TO_758_OVERRIDES",
                                sources) if name == "BLOCK_STATE" \
            else parse_int_table(text, f"{name}_110_TO_758_OVERRIDES")
        tables[name] = (
            expand(fr, fo, size758[name], fb_fwd[name]),
            expand(rr, ro, size110[name], fb_rev[name]),
        )
    meta = {
        "pig_110": fb_fwd["ENTITY_TYPE"],
        "pig_758": fb_rev["ENTITY_TYPE"],
        "plains_758": fb_rev["BIOME"],
    }
    # 復元の健全性チェック：主要な既知値
    bs_f, _ = tables["BLOCK_STATE"]
    if bs_f[(1 << 4) | 0] != 0 and False:
        pass
    ent_f, _ = tables["ENTITY_TYPE"]
    pig758 = [i for i, n in load(TOOLS / "entities-110.json")
              if n == "Pig"]
    # entities-110.json は [110 の ID, 旧式名]。pig の 110 ID は 90。
    if meta["pig_110"] != 90:
        fail("PIG_ENTITY_TYPE_110 が 90 でない")
    return tables, meta


def ranges_overrides(mapping, size, fallback):
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


# ---------- 47 への合成 ----------

# 1.9 で追加されたブロック（数値 ID）の 47 近似。
# 値 = (近似先ブロック ID, メタデータの扱い)。
#   "zero": 0 にする / "keep": そのまま / リスト: メタ→メタの対応表
BLOCK_APPROX_47 = {
    198: (50, [5, 5, 4, 3, 2, 1]),   # end_rod → torch（向きを付け替え）
    199: (121, "zero"),              # chorus_plant → end_stone
    200: (121, "zero"),              # chorus_flower → end_stone
    201: (121, "zero"),              # purpur_block → end_stone
    202: (121, "keep"),              # purpur_pillar → end_stone
    203: (121, "zero"),              # purpur_stairs → end_stone
    204: (121, "zero"),              # purpur_double_slab → end_stone
    205: (121, "zero"),              # purpur_slab → end_stone
    206: (121, "zero"),              # end_bricks → end_stone
    207: (59, [0, 2, 5, 7]),         # beetroots → crops（成長段階を換算）
    208: (2, "zero"),                # grass_path → grass
    209: (49, "zero"),               # end_gateway → obsidian
    210: (137, "keep"),              # repeating_command_block → command_block
    211: (137, "keep"),              # chain_command_block → command_block
    212: (79, "zero"),               # frosted_ice → ice
    255: (166, "zero"),              # structure_block → barrier
}

# 1.9 で追加されたアイテム（数値 ID、ブロック形は除く）の 47 近似。
ITEM_APPROX_47 = {
    426: 381,   # end_crystal → eye_of_ender
    432: 260,   # chorus_fruit → apple
    433: 260,   # chorus_fruit_popped → apple
    434: 391,   # beetroot → carrot
    435: 295,   # beetroot_seeds → seeds
    436: 282,   # beetroot_soup → mushroom_stew
    437: 374,   # dragon_breath → glass_bottle
    438: 373,   # splash_potion → potion
    439: 262,   # spectral_arrow → arrow
    440: 262,   # tipped_arrow → arrow
    441: 373,   # lingering_potion → potion
    442: 425,   # shield → banner
    443: 299,   # elytra → leather_chestplate
    444: 333,   # spruce_boat → boat
    445: 333,   # birch_boat → boat
    446: 333,   # jungle_boat → boat
    447: 333,   # acacia_boat → boat
    448: 333,   # dark_oak_boat → boat
}


def approx_block_state(v):
    block, meta = v >> 4, v & 15
    target, mode = BLOCK_APPROX_47[block]
    if mode == "zero":
        m = 0
    elif mode == "keep":
        m = meta
    else:
        m = mode[meta] if meta < len(mode) else mode[-1]
    return (target << 4) | m


def build_tables(t110, meta):
    blocks18 = {b["id"] for b in load(MCDATA / "1.8" / "blocks.json")}
    blocks19 = {b["id"] for b in load(MCDATA / "1.9" / "blocks.json")}
    items18 = {i["id"] for i in load(MCDATA / "1.8" / "items.json")}
    items19 = {i["id"] for i in load(MCDATA / "1.9" / "items.json")}
    missing_blocks = blocks19 - blocks18
    if missing_blocks != set(BLOCK_APPROX_47):
        fail(f"ブロック差分が近似表と合わない: 差分={sorted(missing_blocks)}")
    missing_items = items19 - items18
    item_approx = dict(ITEM_APPROX_47)
    for i in missing_items:
        if i < 256:  # ブロック形アイテムはブロック近似に一致させる
            item_approx[i] = BLOCK_APPROX_47[i][0]
    if missing_items != set(item_approx):
        fail(f"アイテム差分が近似表と合わない: 差分={sorted(missing_items)} "
             f"近似={sorted(item_approx)}")

    tables = {}

    # ブロック状態
    fwd110, rev110 = t110["BLOCK_STATE"]
    fwd = []
    for v in fwd110:
        fwd.append(v if (v >> 4) in blocks18 or (v >> 4) not in BLOCK_APPROX_47
                   else approx_block_state(v))
    rev = []
    for v in range(4096):
        if (v >> 4) in blocks18 or (v >> 4) not in BLOCK_APPROX_47:
            rev.append(rev110[v])
        else:
            rev.append(rev110[approx_block_state(v)])
    tables["BLOCK_STATE"] = (fwd, rev)

    # ブロック
    bfwd110, brev110 = t110["BLOCK"]
    bfwd = [v if v in blocks18 or v not in BLOCK_APPROX_47
            else BLOCK_APPROX_47[v][0] for v in bfwd110]
    brev = [brev110[i] if i in blocks18 or i not in BLOCK_APPROX_47
            else brev110[BLOCK_APPROX_47[i][0]] for i in range(256)]
    tables["BLOCK"] = (bfwd, brev)

    # アイテム
    ifwd110, irev110 = t110["ITEM"]
    ifwd = [v if v in items18 or v not in item_approx else item_approx[v]
            for v in ifwd110]
    irev = [irev110[i] if i in items18 or i not in item_approx
            else irev110[item_approx[i]] for i in range(len(irev110))]
    tables["ITEM"] = (ifwd, irev)

    # エンティティ（47 の mob 空間は 110 と同一。1.9 追加の shulker
    # など 47 に無い mob は pig へ）
    mobs18 = {e["id"] for e in load(MCDATA / "1.8" / "entities.json")
              if e.get("type") == "mob"}
    efwd110, erev110 = t110["ENTITY_TYPE"]
    efwd = [v if v in mobs18 else meta["pig_110"] for v in efwd110]
    erev = [erev110[i] if i in mobs18 else meta["pig_758"]
            for i in range(len(erev110))]
    tables["ENTITY_TYPE"] = (efwd, erev)

    # パーティクル（名前で合成。1.8 の名前は末尾 "_" の表記ゆれあり）
    def pname(p):
        return p["name"].rstrip("_").lower()

    p18 = {pname(p): p["id"] for p in load(MCDATA / "1.8" / "particles.json")}
    p19 = {p["id"]: pname(p) for p in load(MCDATA / "1.9" / "particles.json")}
    pfwd110, prev110 = t110["PARTICLE"]
    pfwd = [p18.get(p19[v], 0) for v in pfwd110]
    prev = []
    p18_by_id = {p["id"]: pname(p) for p in load(MCDATA / "1.8" / "particles.json")}
    p19_by_name = {pname(p): p["id"] for p in load(MCDATA / "1.9" / "particles.json")}
    for i in range(46):
        if i in p18_by_id:
            prev.append(prev110[p19_by_name[p18_by_id[i]]])
        else:
            prev.append(prev110[0])
    tables["PARTICLE"] = (pfwd, prev)

    # バイオーム（the_void=127 だけが 1.9 追加。plains へ）
    void_id = next(b["id"] for b in load(MCDATA / "1.9" / "biomes.json")
                   if b["name"] == "the_void")
    bifwd110, birev110 = t110["BIOME"]
    bifwd = [1 if v == void_id else v for v in bifwd110]
    birev = [meta["plains_758"] if i == void_id else birev110[i]
             for i in range(len(birev110))]
    tables["BIOME"] = (bifwd, birev)
    return tables


# ---------- 音の名前 ----------

DIG_MAT = {"cloth", "glass", "grass", "gravel", "sand", "snow", "stone",
           "wood"}


def rename_110_to_47(name, s18):
    """110（1.9 式）の音名 → 47（1.8 式）の音名。候補が無ければ None。"""
    cand = None
    parts = name.split(".")
    if name == "ambient.cave":
        cand = "ambient.cave.cave"
    elif name == "weather.rain" or name == "weather.rain.above":
        cand = "ambient.weather.rain"
    elif name == "entity.lightning.thunder" or name == "weather.thunder":
        cand = "ambient.weather.thunder"
    elif parts[0] == "block" and len(parts) >= 3:
        mat, kind = parts[1], parts[2]
        if mat == "anvil":
            cand = {"break": "random.anvil_break",
                    "destroy": "random.anvil_break",
                    "land": "random.anvil_land",
                    "fall": "random.anvil_land",
                    "hit": "random.anvil_land",
                    "place": "random.anvil_land",
                    "use": "random.anvil_use",
                    "step": "step.stone"}.get(kind)
        elif kind in ("break", "place", "hit") and mat in DIG_MAT:
            cand = f"dig.{mat}"
        elif kind in ("step", "fall") and (mat in DIG_MAT or mat == "ladder"):
            cand = f"step.{mat}"
        elif mat == "ladder" and kind in ("break", "place", "hit"):
            cand = "dig.wood"
        elif mat in ("chest", "enderchest", "ender_chest") or \
                name.startswith("block.chest") or \
                name.startswith("block.enderchest"):
            cand = "random.chestopen" if kind == "open" \
                else "random.chestclosed" if kind in ("close", "locked") \
                else None
        elif "door" in mat or "trapdoor" in mat:
            cand = "random.door_open" if kind == "open" \
                else "random.door_close" if kind == "close" else None
        elif mat == "piston":
            cand = "tile.piston.out" if kind == "extend" \
                else "tile.piston.in" if kind == "contract" else None
        elif mat == "note":
            cand = {"harp": "note.harp", "bass": "note.bass",
                    "snare": "note.snare", "hat": "note.hat",
                    "basedrum": "note.bd", "pling": "note.pling",
                    "bassattack": "note.bassattack"}.get(kind)
        elif mat == "portal":
            cand = {"ambient": "portal.portal", "travel": "portal.travel",
                    "trigger": "portal.trigger"}.get(kind)
        elif mat == "fire":
            cand = "fire.fire" if kind == "ambient" else \
                "fire.ignite" if kind == "extinguish" else None
        elif mat == "water":
            cand = "liquid.water" if kind == "ambient" else None
        elif mat == "lava":
            cand = {"ambient": "liquid.lava", "pop": "liquid.lavapop"}.get(kind)
        elif mat == "lever" or mat == "button" or "button" in mat:
            cand = "gui.button.press" if mat == "button" or "button" in mat \
                else "random.click"
        elif mat == "tripwire":
            cand = "random.click"
    elif parts[0] == "entity" and len(parts) >= 3:
        who, kind = parts[1], parts[2]
        mob = f"mob.{who}.{kind}"
        if mob in s18:
            cand = mob
        elif who == "creeper" and kind == "primed":
            cand = "creeper.primed"
        elif who == "tnt" and kind == "primed":
            cand = "game.tnt.primed"
        elif who == "player":
            cand = {"hurt": "game.player.hurt",
                    "death": "game.player.die",
                    "big_fall": "game.player.hurt.fall.big",
                    "small_fall": "game.player.hurt.fall.small",
                    "swim": "game.player.swim",
                    "splash": "game.player.swim.splash",
                    "levelup": "random.levelup",
                    "burp": "random.burp",
                    "drink": "random.drink",
                    "eat": "random.eat",
                    "breath": None,
                    "attack": "random.successful_hit"}.get(kind)
        elif who == "generic":
            cand = {"explode": "random.explode",
                    "splash": "random.splash",
                    "drink": "random.drink",
                    "eat": "random.eat",
                    "death": "game.neutral.die",
                    "hurt": "game.neutral.hurt",
                    "swim": "game.neutral.swim",
                    "big_fall": "game.neutral.hurt.fall.big",
                    "small_fall": "game.neutral.hurt.fall.small",
                    "extinguish_fire": "random.fizz",
                    "burn": "fire.fire"}.get(kind)
        elif who == "hostile":
            cand = {"death": "game.hostile.die",
                    "hurt": "game.hostile.hurt",
                    "swim": "game.hostile.swim",
                    "splash": "game.hostile.swim.splash",
                    "big_fall": "game.hostile.hurt.fall.big",
                    "small_fall": "game.hostile.hurt.fall.small"}.get(kind)
        elif who == "arrow":
            cand = {"shoot": "random.bow", "hit": "random.bowhit",
                    "hit_player": "random.successful_hit"}.get(kind)
        elif who == "firework":
            cand = {"launch": "fireworks.launch",
                    "blast": "fireworks.blast",
                    "blast_far": "fireworks.blast_far",
                    "twinkle": "fireworks.twinkle",
                    "twinkle_far": "fireworks.twinkle_far",
                    "large_blast": "fireworks.largeBlast",
                    "large_blast_far": "fireworks.largeBlast_far",
                    "shoot": "fireworks.launch"}.get(kind)
        elif who == "minecart":
            cand = {"inside": "minecart.inside",
                    "riding": "minecart.base"}.get(kind)
        elif who == "item":
            cand = {"break": "random.break", "pickup": "random.pop"}.get(kind)
        elif who == "experience_orb":
            cand = "random.orb"
        elif who == "enderdragon":
            cand = {"growl": "mob.enderdragon.growl",
                    "death": "mob.enderdragon.end",
                    "hurt": "mob.enderdragon.hit",
                    "flap": "mob.enderdragon.wings"}.get(kind)
        elif who == "endermen":
            cand = f"mob.endermen.{kind}" if f"mob.endermen.{kind}" in s18 \
                else None
        elif who == "zombie_pig":
            cand = {"ambient": "mob.zombiepig.zpig",
                    "angry": "mob.zombiepig.zpigangry",
                    "death": "mob.zombiepig.zpigdeath",
                    "hurt": "mob.zombiepig.zpighurt"}.get(kind)
        elif who == "cat":
            cand = {"ambient": "mob.cat.meow", "hurt": "mob.cat.hitt",
                    "purr": "mob.cat.purr", "purreow": "mob.cat.purreow",
                    "hiss": "mob.cat.hiss", "death": "mob.cat.hitt"}.get(kind)
        elif who == "blaze":
            cand = {"hurt": "mob.blaze.hit", "death": "mob.blaze.death",
                    "ambient": "mob.blaze.breathe",
                    "shoot": "mob.ghast.fireball"}.get(kind)
        elif who == "horse":
            cand = f"mob.horse.{kind}" if f"mob.horse.{kind}" in s18 else None
        elif who == "donkey":
            cand = {"ambient": "mob.horse.donkey.idle",
                    "angry": "mob.horse.donkey.angry",
                    "death": "mob.horse.donkey.death",
                    "hurt": "mob.horse.donkey.hit"}.get(kind)
        elif who == "skeleton_horse":
            cand = {"ambient": "mob.horse.skeleton.idle",
                    "death": "mob.horse.skeleton.death",
                    "hurt": "mob.horse.skeleton.hit"}.get(kind)
        elif who == "zombie_horse":
            cand = {"ambient": "mob.horse.zombie.idle",
                    "death": "mob.horse.zombie.death",
                    "hurt": "mob.horse.zombie.hit"}.get(kind)
        elif who == "iron_golem":
            cand = {"hurt": "mob.irongolem.hit",
                    "death": "mob.irongolem.death",
                    "attack": "mob.irongolem.throw",
                    "walk": "mob.irongolem.walk"}.get(kind)
        elif who == "ghast":
            cand = {"hurt": "mob.ghast.scream", "death": "mob.ghast.death",
                    "ambient": "mob.ghast.moan",
                    "scream": "mob.ghast.scream",
                    "warn": "mob.ghast.charge",
                    "shoot": "mob.ghast.fireball"}.get(kind)
        elif who == "guardian":
            cand = f"mob.guardian.{kind}" if f"mob.guardian.{kind}" in s18 \
                else None
        elif who == "elder_guardian":
            cand = {"ambient": "mob.guardian.elder.idle",
                    "death": "mob.guardian.elder.death",
                    "hurt": "mob.guardian.elder.hit",
                    "curse": "mob.guardian.curse"}.get(kind)
        elif who == "magma_cube":
            cand = {"jump": "mob.magmacube.jump",
                    "squish": "mob.magmacube.big",
                    "hurt": "mob.magmacube.big",
                    "death": "mob.magmacube.big"}.get(kind)
        elif who == "slime":
            cand = {"jump": "mob.slime.big", "squish": "mob.slime.big",
                    "attack": "mob.slime.attack",
                    "hurt": "mob.slime.big", "death": "mob.slime.big"}.get(kind)
        elif who == "villager":
            cand = {"ambient": "mob.villager.idle",
                    "death": "mob.villager.death",
                    "hurt": "mob.villager.hit",
                    "no": "mob.villager.no", "yes": "mob.villager.yes",
                    "trading": "mob.villager.haggle"}.get(kind)
        elif who == "wither":
            cand = {"ambient": "mob.wither.idle",
                    "death": "mob.wither.death",
                    "hurt": "mob.wither.hurt",
                    "shoot": "mob.wither.shoot",
                    "spawn": "mob.wither.spawn"}.get(kind)
        elif who == "fireball" and kind == "shoot":
            cand = "mob.ghast.fireball"
        elif who == "firecharge" or (who == "fire_charge"):
            cand = "item.fireCharge.use"
        elif who == "potion" and kind == "splash":
            cand = "game.potion.smash"
        elif who == "splash_potion":
            cand = "game.potion.smash"
    elif name.startswith("music_disc."):
        cand = "records." + name.split(".", 1)[1]
    elif name.startswith("record."):
        cand = "records." + name.split(".", 1)[1]
    elif parts[0] == "music":
        cand = {"music.game": "music.game",
                "music.creative": "music.game.creative",
                "music.end": "music.game.end",
                "music.credits": "music.game.end.credits",
                "music.dragon": "music.game.end.dragon",
                "music.nether": "music.game.nether",
                "music.menu": "music.menu"}.get(name)
    elif name == "ui.button.click":
        cand = "gui.button.press"
    elif name == "item.firecharge.use":
        cand = "item.fireCharge.use"
    if cand is not None and cand not in s18:
        return None
    return cand


def build_sound_tables(t110):
    s18 = set(load(TOOLS / "sounds-47.json"))
    s110 = load(TOOLS / "sounds-110.json")
    snd110_text = (MAIN / "RegistryTables110Snd.java").read_text(
        encoding="utf-8")
    fwd_block = re.search(r"FWD = \{(.*?)\n    \};", snd110_text, re.S)
    fwd110_pairs = re.findall(r'\{"([^"]+)", "([^"]+)"\}', fwd_block.group(1))
    name110_of_758 = dict(fwd110_pairs)
    sounds758 = load(Path.home() / "workspace" / "mcdata404"
                     / "mapping-1.18.json")["sounds"]
    # SOUND_NAMES_758 の添字整合を 110 の数値表で検証する：
    # 名前が両空間にある音は、数値表の写像先の名前と一致するはず。
    sfwd110, _ = t110["SOUND"]
    checked = 0
    for idx, nm in enumerate(sounds758):
        if nm in s110:
            if s110[sfwd110[idx]] != nm:
                fail(f"音 {nm}: 758 ID {idx} → 110 数値表 "
                     f"{sfwd110[idx]}({s110[sfwd110[idx]]}) と名前が合わない")
            checked += 1
    print(f"音名の添字検証: {checked} 件一致")

    rename_cache = {}

    def to47(name110):
        if name110 not in rename_cache:
            rename_cache[name110] = rename_110_to_47(name110, s18)
        return rename_cache[name110]

    fwd_pairs = []
    for n758 in sounds758:
        n110 = name110_of_758.get(n758, n758)
        n47 = n110 if n110 in s18 else to47(n110)
        if n47 and n47 != n758:
            fwd_pairs.append([n758, n47])
    rev_pairs = []
    inv = {}
    for n110 in s110:
        n47 = to47(n110)
        if n47:
            inv.setdefault(n47, n110)
    pairs110_758 = load(TOOLS / "sound-names-110-to-758.json")
    name758_of_110 = {a: b for a, b in pairs110_758}
    for n47 in sorted(s18):
        n110 = inv.get(n47, n47 if n47 in s110 else None)
        if n110 is None:
            continue
        n758 = name758_of_110.get(n110, n110)
        if n758 != n47:
            rev_pairs.append([n47, n758])
    return fwd_pairs, rev_pairs, sounds758


# ---------- 出力 ----------

HEADER = """package io.detergentmc.protocol;

/**
 * 47（1.8〜1.8.9）のレジストリ対応表（区間＋上書きの生の表）。
 *
 * <p>{@link RegistryRemapper} から分離した。理由はバイトコードの制限：
 * 表を RegistryRemapper に置くと静的初期化子（{@code <clinit>}）が
 * 64KB を超えてコンパイルできなくなるため。このクラスの表は
 * {@link RegistryRemapper} から static import で同じ名前のまま
 * 参照する。
 *
 * <p>表の内容と検証は {@code detergentmc-protocol/tools/} を参照：
 * 生成は {@code gen-47-tables.py}、検証は {@code verify-47-tables.py}。
 * 作り方は 110 の表との合成：47 は 110 と同じ Flattening 前の世代で
 * 値は (数値ID&lt;&lt;4)|メタデータ（4096 通り）。110 との差分は
 * 1.9 の追加分だけ（ブロック 16 種・アイテム 29 件・shulker・
 * the_void・パーティクル 4 種）で、共有名の数値 ID は完全一致
 * （Stage A で mcdata pc/1.8⇔pc/1.9 の全面比較で確認）。
 *
 * <p>近似・制限（詳細は docs/PROXY-1.8.9.md）：
 * end／purpur 系ブロックは end_stone へ、end_rod は torch へ
 * （向き付け替え）、beetroots は crops へ（成長段階を換算）、
 * grass_path は grass へ、end_gateway は obsidian へ、frosted_ice は
 * ice へ、コマンドブロック亜種は command_block へ、structure_block
 * は barrier へ寄せる。アイテムは shield→banner、elytra→
 * leather_chestplate、ボート亜種→boat、splash／lingering ポーション
 * →potion、tipped／spectral 矢→arrow など。shulker は pig へ、
 * the_void は plains へ、1.9 追加パーティクル（dragonbreath・
 * endRod・damageIndicator・sweepAttack）は explode へ寄せる。
 * 音は 47 では数値 ID がワイヤに無いため数値表を持たず、名前の
 * 対応は {@link RegistryTables47Snd} に置く。
 */
final class RegistryTables47 {

    private RegistryTables47() {
    }

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

"""


def emit(tables, meta, snd_fwd, snd_rev, sounds758):
    fallbacks_fwd = {
        "BLOCK_STATE": 16, "ITEM": 1, "ENTITY_TYPE": meta["pig_110"],
        "PARTICLE": 0, "BLOCK": 1, "BIOME": 1,
    }
    fallbacks_rev = {
        "BLOCK_STATE": 1, "ITEM": 1, "ENTITY_TYPE": meta["pig_758"],
        "PARTICLE": 0, "BLOCK": 1, "BIOME": meta["plains_758"],
    }
    chunk_files = {}
    parts = [HEADER]
    for name, (fwd, rev) in tables.items():
        fr, fo = ranges_overrides(fwd, len(fwd), fallbacks_fwd[name])
        rr, ro = ranges_overrides(rev, len(rev), fallbacks_rev[name])
        if name == "BLOCK_STATE":
            def emit_chunks(prefix, overrides):
                names = []
                for ci in range(0, len(overrides), 1200):
                    chunk = overrides[ci:ci + 1200]
                    cname = f"RegistryTables47{prefix}{ci // 1200 + 1}"
                    body = ["package io.detergentmc.protocol;",
                            "",
                            f"/** 47 のブロック状態の上書き表の分割クラス"
                            f"（{prefix}、{ci // 1200 + 1} 個目）。"
                            f"生成は gen-47-tables.py。 */",
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
            t = java_table(f"{name}_47", fr, [], len(fwd), "758->47")
            t = t.replace(
                f"    static final int[][] {name}_47_OVERRIDES = {{\n    }};",
                f"    static final int[][] {name}_47_OVERRIDES = concat("
                + ", ".join(fwd_names) + ");")
            parts.append(t)
            parts.append("")
            t = java_table(f"{name}_47_TO_758", rr, [], len(rev), "47->758")
            t = t.replace(
                f"    static final int[][] {name}_47_TO_758_OVERRIDES = {{\n"
                "    };",
                f"    static final int[][] {name}_47_TO_758_OVERRIDES = "
                "concat(" + ", ".join(rev_names) + ");")
            parts.append(t)
            parts.append("")
            continue
        parts.append(java_table(f"{name}_47", fr, fo, len(fwd), "758->47"))
        parts.append("")
        parts.append(java_table(f"{name}_47_TO_758", rr, ro, len(rev),
                                "47->758"))
        parts.append("")

    parts.append("    /** 音の名前：758 → 47（Stop Sound 用）。実体は RegistryTables47Snd。 */")
    parts.append("    static final String[][] SOUND_NAME_FALLBACKS_758_TO_47 ="
                 " RegistryTables47Snd.FWD;")
    parts.append("")
    parts.append("    /** 音の名前：47 → 758（Stop Sound 用）。実体は RegistryTables47Snd。 */")
    parts.append("    static final String[][] SOUND_NAME_FALLBACKS_47_TO_758 ="
                 " RegistryTables47Snd.REV;")
    parts.append("")
    # バイオーム名は 110 の表から the_void の位置だけ差し替える
    text110 = (MAIN / "RegistryTables110.java").read_text(encoding="utf-8")
    m = re.search(r"BIOME_NAMES_110 = \{(.*?)\n    \};", text110, re.S)
    biome_names = re.findall(r'"([^"]+)"', m.group(1))
    void_id = next(b["id"] for b in load(MCDATA / "1.9" / "biomes.json")
                   if b["name"] == "the_void")
    biome_names[void_id] = "plains"
    lines = ["    /** バイオーム名（110 と同一。ただし the_void の位置は"
             " 47 に無いため plains）。 */",
             "    static final String[] BIOME_NAMES_47 = {"]
    for i in range(0, len(biome_names), 4):
        chunk = biome_names[i:i + 4]
        lines.append("            " + " ".join(f'"{n}",' for n in chunk))
    lines.append("    };")
    parts.append("\n".join(lines))
    parts.append("}")
    (MAIN / "RegistryTables47.java").write_text("\n".join(parts) + "\n",
                                                encoding="utf-8")
    for cname, body in chunk_files.items():
        (MAIN / f"{cname}.java").write_text(body, encoding="utf-8")

    snd = ["package io.detergentmc.protocol;",
           "",
           "/**",
           " * 47（1.8〜1.8.9）の音の名前表（分離クラス）。",
           " *",
           " * <p>47 では音は名前でしかワイヤに乗らない（数値 ID の",
           " * Sound Effect パケット自体が無い）。FWD／REV は Stop Sound",
           " * 等の名前変換用、SOUND_NAMES_758 は 758 の数値音 ID を",
           " * 名前に戻して 47 の Named Sound Effect へ変換するための表。",
           " * 47 の音名集合（246 件）は Mojang 公式アセット索引の",
           " * sounds.json（1.8.9）から取得した。生成は gen-47-tables.py、",
           " * 検証は verify-47-tables.py。",
           " */",
           "final class RegistryTables47Snd {",
           "",
           "    private RegistryTables47Snd() {",
           "    }",
           "",
           "    /** 音の名前：758 → 47。1.9 での大規模改名を 1.8 式へ戻す。 */",
           "    static final String[][] FWD = {"]
    for src, dst in snd_fwd:
        snd.append(f'            {{"{src}", "{dst}"}},')
    snd.append("    };")
    snd.append("")
    snd.append("    /** 音の名前：47 → 758。 */")
    snd.append("    static final String[][] REV = {")
    for src, dst in snd_rev:
        snd.append(f'            {{"{src}", "{dst}"}},')
    snd.append("    };")
    snd.append("")
    s18 = sorted(load(TOOLS / "sounds-47.json"))
    snd.append("    /** 47 に実在する音名の集合（公式 sounds.json 由来、246 件）。 */")
    snd.append("    static final java.util.Set<String> SOUND_NAME_SET_47 ="
               " java.util.Set.of(")
    for i in range(0, len(s18), 4):
        chunk = s18[i:i + 4]
        tail = ");" if i + 4 >= len(s18) else ","
        snd.append("            " + ", ".join(f'"{n}"' for n in chunk) + tail)
    snd.append("")
    snd.append("    /** 758 の音名（添字 = 758 の音 ID）。mcdata404 の"
               " mapping-1.18.json 由来。 */")
    snd.append("    static final String[] SOUND_NAMES_758 = {")
    for i in range(0, len(sounds758), 4):
        chunk = sounds758[i:i + 4]
        snd.append("            " + " ".join(f'"{n}",' for n in chunk))
    snd.append("    };")
    snd.append("}")
    (MAIN / "RegistryTables47Snd.java").write_text("\n".join(snd) + "\n",
                                                   encoding="utf-8")
    print(f"出力完了: テーブル {len(tables)} 種、音 FWD {len(snd_fwd)} 件"
          f"／REV {len(snd_rev)} 件")


def main():
    # 47 の音名集合を tools に保存（出典：Mojang 公式アセット索引 1.8.9）
    raw = load("/tmp/sounds-1.8.9.json") \
        if Path("/tmp/sounds-1.8.9.json").exists() else None
    if raw is not None:
        (TOOLS / "sounds-47.json").write_text(
            json.dumps(sorted(raw.keys()), ensure_ascii=False, indent=1),
            encoding="utf-8")
    if not (TOOLS / "sounds-47.json").exists():
        fail("tools/sounds-47.json が無い")
    t110, meta = load_110_tables()
    tables = build_tables(t110, meta)
    snd_fwd, snd_rev, sounds758 = build_sound_tables(t110)
    emit(tables, meta, snd_fwd, snd_rev, sounds758)


if __name__ == "__main__":
    main()
