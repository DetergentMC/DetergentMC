#!/usr/bin/env python3
"""210（1.10／1.10.1／1.10.2）のレジストリ対応表を生成する。

作り方は 316 の表との合成。210 は 315／316 と同じ Flattening 前の
世代で、値は (数値ID<<4)|メタデータ（4096 通り）。315／316 との
差分は 1.11 の追加分だけ：

- ブロック: observer (218) とシュルカーボックス16種 (219〜234) が
  210 に無い。共有ブロックの数値IDは同一（実 jar の登録呼び出し
  219 件で確認。blocks-210.json）。
  ※ 316 の表は 340 との合成の都合で observer／シュルカーを
  (218<<4 等) へ素通ししているが、実 1.11 でも未登録 ID で、
  210 では近似に置き換える（observer → dropper 158。メタの
  facing+powered の並びが同じ。シュルカー → chest 54。facing は
  0/1（下／上）を 2（北）へ寄せ、色は落ちる）。
- アイテム: シュルカー16種のアイテム形＋observer (218)＋
  shulker_shell (450)＋totem (449)＋iron_nugget (452) が 210 に
  無い（mcdata pc/1.10 items.json 371 件で照合）。近似は
  observer → dropper (158)、シュルカー → chest (54)、
  totem → golden_apple (322)、shulker_shell → prismarine_shard
  (409)、iron_nugget → gold_nugget (371)。
- エンティティ: 1.11 の新規モブ（evocation_illager／vex／
  vindication_illager／llama）は pig (90) へ。1.11 で独立 ID 化
  した亜種9種（elder_guardian／wither_skeleton／stray／husk／
  zombie_villager／skeleton_horse／zombie_horse／donkey／mule）
  は 210 の基本種へ畳む（210 では基本種のメタデータ亜種で、
  スポーンでは区別できない）。登録順は実バニラ 1.10.2 jar の
  バイトコードから抽出（entities-210.json。旧式名は
  ENTITY_RENAME_210 で正規化名へ読み替える）。共有名の数値 ID は
  316 と全件一致することを assert で固定する。
- 音: 登録順は実 jar 抽出（sounds-210.json、463 件）。1.11 追加の
  31 件は汎用音（block.stone.break の 210 番号）へ寄せる。
  210 にだけある entity.experience_orb.touch は 1.11 で
  entity.experience_orb.pickup へ改名されたものとして扱う。
- パーティクル: 実 jar 抽出（particles-210.json、47 件。
  minecraft-data pc/1.10 と完全一致）。1.11 追加の totem／spit は
  explode (0) へ寄せる。
- ブロックエンティティ: 実 jar 抽出の登録順 23 件
  （block-entities-210.json、旧式名）。210 には note_block と
  flower_pot があり（316 の表には無い）、shulker_box と
  trapped_chest が無い（トラップチェストは chest と同じ
  TileEntity を使う）。NBT の id は旧式名（"Furnace" 等、
  名前空間なし）で読み書きするため、旧式名の表も併せて出す。
- バイオーム: 1.10 と 1.11 で名前⇔ID が完全一致（mcdata 照合）
  のため 316 の表をそのまま使う。ダメージ種別も 316 と同じ。

土台の 316 の表は gen-316-tables.py の build_all を import して
得る（チェックイン済み RegistryTables316.java とバイト一致する
ことを別途確認済み）。

使い方：
  python3 gen-210-tables.py [--out PATH] [--json PATH]
"""

import argparse
import importlib.util
import json
import sys
from pathlib import Path

TOOLS = Path(__file__).resolve().parent
JAVA_DIR = TOOLS.parent / "src" / "main" / "java" / "io" / "detergentmc" / "protocol"
MCDATA = Path("/home/hatch/mc-client/node_modules/minecraft-data"
              "/minecraft-data/data/pc")


def load_module(name, path):
    spec = importlib.util.spec_from_file_location(name, path)
    mod = importlib.util.module_from_spec(spec)
    sys.path.insert(0, str(TOOLS))
    spec.loader.exec_module(mod)
    return mod


gen316 = load_module("gen316", TOOLS / "gen-316-tables.py")
ranges_overrides = gen316.ranges_overrides
java_table = gen316.java_table


def load(p):
    return json.loads(Path(p).read_text(encoding="utf-8"))


# --- 210 の ground truth（extract-210-registries.py の出力） ---
BLOCKS_210 = load(TOOLS / "blocks-210.json")          # [[id, name], ...]
ENTITIES_210_RAW = load(TOOLS / "entities-210.json")  # [[id, 旧式名], ...]
SOUNDS_210 = load(TOOLS / "sounds-210.json")          # [name, ...]（添字=ID）
PARTICLES_210 = load(TOOLS / "particles-210.json")    # [[id, name], ...]
BLOCK_ENTITIES_210_RAW = load(TOOLS / "block-entities-210.json")  # [旧式名]

# 1.10 のエンティティ登録名（PascalCase 等）→ 正規化名（316 の名前空間）
ENTITY_RENAME_210 = {
    "Item": "item", "XPOrb": "experience_orb",
    "AreaEffectCloud": "area_effect_cloud", "ThrownEgg": "egg",
    "LeashKnot": "leash_knot", "Painting": "painting", "Arrow": "arrow",
    "Snowball": "snowball", "Fireball": "fireball",
    "SmallFireball": "small_fireball", "ThrownEnderpearl": "ender_pearl",
    "EyeOfEnderSignal": "eye_of_ender", "ThrownPotion": "potion",
    "ThrownExpBottle": "experience_bottle", "ItemFrame": "item_frame",
    "WitherSkull": "wither_skull", "PrimedTnt": "tnt",
    "FallingSand": "falling_block",
    "FireworksRocketEntity": "firework_rocket",
    "SpectralArrow": "spectral_arrow", "ShulkerBullet": "shulker_bullet",
    "DragonFireball": "dragon_fireball", "ArmorStand": "armor_stand",
    "Boat": "boat", "Creeper": "creeper", "Skeleton": "skeleton",
    "Spider": "spider", "Giant": "giant", "Zombie": "zombie",
    "Slime": "slime", "Ghast": "ghast", "PigZombie": "zombie_pigman",
    "Enderman": "enderman", "CaveSpider": "cave_spider",
    "Silverfish": "silverfish", "Blaze": "blaze",
    "LavaSlime": "magma_cube", "EnderDragon": "ender_dragon",
    "WitherBoss": "wither", "Bat": "bat", "Witch": "witch",
    "Endermite": "endermite", "Guardian": "guardian",
    "Shulker": "shulker", "Pig": "pig", "Sheep": "sheep", "Cow": "cow",
    "Chicken": "chicken", "Squid": "squid", "Wolf": "wolf",
    "MushroomCow": "mooshroom", "SnowMan": "snow_golem",
    "Ozelot": "ocelot", "VillagerGolem": "iron_golem",
    "EntityHorse": "horse", "Rabbit": "rabbit",
    "PolarBear": "polar_bear", "Villager": "villager",
    "EnderCrystal": "end_crystal",
    # Mob／Monster は抽象基底の登録で 758 側に対応が無い（rev の
    # フォールバック先になるだけ）。None = 対応なし。
    "Mob": None, "Monster": None,
}

# ブロックエンティティの旧式名 → 正規化名
BLOCK_ENTITY_RENAME_210 = {
    "Furnace": "furnace", "Chest": "chest", "EnderChest": "ender_chest",
    "RecordPlayer": "jukebox", "Trap": "dispenser", "Dropper": "dropper",
    "Sign": "sign", "MobSpawner": "mob_spawner", "Music": "note_block",
    "Piston": "piston", "Cauldron": "brewing_stand",
    "EnchantTable": "enchanting_table", "Airportal": "end_portal",
    "Beacon": "beacon", "Skull": "skull", "DLDetector": "daylight_detector",
    "Hopper": "hopper", "Comparator": "comparator",
    "FlowerPot": "flower_pot", "Banner": "banner",
    "Structure": "structure_block", "EndGateway": "end_gateway",
    "Control": "command_block",
}

# 316 で独立 ID の亜種 → 210 の基本種（スポーンでは区別できない）
ENTITY_FOLD_316_TO_210 = {
    "elder_guardian": "guardian", "wither_skeleton": "skeleton",
    "stray": "skeleton", "husk": "zombie", "zombie_villager": "zombie",
    "skeleton_horse": "horse", "zombie_horse": "horse",
    "donkey": "horse", "mule": "horse",
}

# 音の改名（210 → 316／758）
SOUND_RENAME_210 = {"entity.experience_orb.touch":
                    "entity.experience_orb.pickup"}

MISSING_BLOCKS_210 = {218} | set(range(219, 235))  # observer＋シュルカー16種
MISSING_ITEMS_210 = {218, 449, 450, 452} | set(range(219, 235))
ITEM_APPROX_210 = {218: 158, 449: 322, 450: 409, 452: 371}
ITEM_APPROX_210.update({i: 54 for i in range(219, 235)})


def approx_block_state_210(v316):
    block = v316 >> 4
    meta = v316 & 15
    if block == 218:  # observer → dropper（メタの並びが同じ）
        return (158 << 4) | meta
    if 219 <= block <= 234:  # シュルカー → chest（facing 0/1 は北へ）
        return (54 << 4) | (2 if meta < 2 else meta)
    return v316


def build_tables(t316, meta316):
    problems = []

    def fail(msg):
        problems.append(msg)

    # ---------- ブロック（ground truth の照合） ----------
    blocks210 = {i: n for i, n in BLOCKS_210}
    mc11_blocks = {b["name"]: b["id"] for b in load(MCDATA / "1.11" / "blocks.json")}
    only11 = set(mc11_blocks) - set(blocks210.values())
    if len(blocks210) != 219:
        fail(f"blocks210 が 219 件でない: {len(blocks210)}")
    if {mc11_blocks[n] for n in only11} != MISSING_BLOCKS_210:
        fail(f"1.11 のみのブロックの ID 集合が想定と違う: "
             f"{sorted((n, mc11_blocks[n]) for n in only11)}")
    for i, n in BLOCKS_210:
        if n in mc11_blocks and mc11_blocks[n] != i:
            fail(f"ブロック {n} の ID が 1.11 と違う: 210={i} 1.11={mc11_blocks[n]}")

    # ---------- ブロック状態 ----------
    fwd316, rev316 = t316["BLOCK_STATE"]
    fwd = [approx_block_state_210(v) for v in fwd316]
    stone758 = rev316[(1 << 4) | 0]
    rev = [stone758 if (i >> 4) in MISSING_BLOCKS_210 else rev316[i]
           for i in range(4096)]

    # ---------- ブロック ----------
    bfwd316, brev316 = t316["BLOCK"]
    bfwd = [158 if v == 218 else 54 if 219 <= v <= 234 else v
            for v in bfwd316]
    brev = [1 if i in MISSING_BLOCKS_210 else brev316[i] for i in range(256)]

    # 無色シュルカーボックスの補正：Flattening 前の無色シュルカーは
    # purple_shulker_box (229) そのものだが、316 の表では名前対応に
    # 失敗して石へ落ちている（316 側の既存ギャップ。210 では
    # 229 と同じ近似＝chest へ寄せる）。土台が石フォールバック
    # (16／1) でなくなったら、この補正の前提が崩れた合図。
    m18 = load(Path.home() / "workspace" / "mcdata404" / "mapping-1.18.json")
    for i, name in enumerate(m18["blockstates"]):
        if name.startswith("shulker_box["):
            if fwd[i] != 16:
                fail(f"無色シュルカー状態 {i} の土台が石でない: {fwd[i]}")
            fwd[i] = (54 << 4) | 2  # chest、facing は 229 経由でも 0 に潰れる
    bi_shulker = m18["blocks"].index("shulker_box")
    if bfwd[bi_shulker] != 1:
        fail(f"無色シュルカーブロックの土台が石でない: {bfwd[bi_shulker]}")
    bfwd[bi_shulker] = 54

    # ---------- アイテム ----------
    ifwd316, irev316 = t316["ITEM"]
    ifwd = [ITEM_APPROX_210.get(v, v) for v in ifwd316]
    # 無色シュルカーボックスのアイテム形も同根（229 のアイテム形と
    # 同一視されず 316 の表で石へ落ちている）→ chest アイテムへ。
    ii_shulker = m18["items"].index("shulker_box")
    if ifwd[ii_shulker] != 1:
        fail(f"無色シュルカーアイテムの土台が石でない: {ifwd[ii_shulker]}")
    ifwd[ii_shulker] = 54
    irev = [1 if i in MISSING_ITEMS_210 else irev316[i]
            for i in range(len(irev316))]
    # mcdata pc/1.10 items.json（371件）との端末照合。アイテムは
    # Flattening で名前が変わる（planks → oak_planks 等）ため名前で
    # なく数値 ID で照合する：210 と 316 で共有するアイテムは数値 ID
    # が同一（PREP-210 で名前ベースに確認済み）なので、mcdata 1.10
    # にある ID は rev が 316 と一致し、往復で戻るはず。
    items210 = load(MCDATA / "1.10" / "items.json")
    if len(items210) != 371:
        fail(f"mcdata 1.10 items が 371 件でない: {len(items210)}")
    for it in items210:
        i = it["id"]
        if i in MISSING_ITEMS_210:
            fail(f"アイテム {it['name']}({i}) は 210 に無い想定なのに "
                 f"mcdata 1.10 にある")
        if irev[i] != irev316[i]:
            fail(f"アイテム {it['name']}: rev210[{i}]={irev[i]} "
                 f"!= rev316 {irev316[i]}")
        if ifwd[irev316[i]] != i:
            fail(f"アイテム {it['name']}: 往復が戻らない "
                 f"210={i} → 758={irev316[i]} → 210={ifwd[irev316[i]]}")

    # ---------- エンティティ ----------
    efwd316, erev316 = t316["ENTITY_TYPE"]
    e316 = {i: n for i, n in load(TOOLS / "entities-316.json")}
    e316_by_name = {n: i for i, n in e316.items()}
    e210 = {}
    for i, raw in ENTITIES_210_RAW:
        if raw not in ENTITY_RENAME_210:
            fail(f"エンティティ旧式名 {raw} が rename 表に無い")
            continue
        e210[i] = ENTITY_RENAME_210[raw]
    e210_by_name = {n: i for i, n in e210.items() if n}
    # 共有名の数値 ID は 316 と一致するはず（亜種・新規を除く）
    for n, i210 in e210_by_name.items():
        if n in e316_by_name and e316_by_name[n] != i210:
            fail(f"エンティティ {n} の ID が 316 と違う: 210={i210} "
                 f"316={e316_by_name[n]}")
    pig758 = erev316[e316_by_name["pig"]]

    def entity_fwd(t758):
        id316 = efwd316[t758]
        name = e316.get(id316)
        if name is None:
            return 90
        if name in e210_by_name:
            return e210_by_name[name]
        if name in ENTITY_FOLD_316_TO_210:
            return e210_by_name[ENTITY_FOLD_316_TO_210[name]]
        return 90  # 1.11 の新規モブは pig へ

    efwd = [entity_fwd(t) for t in range(len(efwd316))]
    erev = []
    for i in range(len(erev316)):
        name = e210.get(i)
        if name and name in e316_by_name:
            erev.append(erev316[e316_by_name[name]])
        else:
            erev.append(pig758)
    # 往復照合：210 の実体は 758 経由で自分の名前へ戻るはず
    for i210, name in e210.items():
        if name and name in e316_by_name:
            t758 = erev[i210]
            if e316.get(efwd316[t758]) != name:
                fail(f"エンティティ {name}: 往復が戻らない "
                     f"210={i210} → 758={t758} → 316={efwd316[t758]}")

    # ---------- 音 ----------
    sfwd316, srev316 = t316["SOUND"]
    s316 = load(TOOLS / "sounds-316.json")
    s316_idx = {n: i for i, n in enumerate(s316)}
    s210_idx = {n: i for i, n in enumerate(SOUNDS_210)}
    generic210 = s210_idx["block.stone.break"]
    for n in SOUNDS_210:
        if n not in s316_idx and n not in SOUND_RENAME_210:
            fail(f"音 {n} が 316 にも改名表にも無い")
    sfwd = []
    for t in range(len(sfwd316)):
        name = s316[sfwd316[t]]
        sfwd.append(s210_idx.get(name, generic210))
    srev = []
    for n in SOUNDS_210:
        n316 = SOUND_RENAME_210.get(n, n)
        srev.append(srev316[s316_idx[n316]])

    # ---------- パーティクル ----------
    pfwd316, prev316 = t316["PARTICLE"]
    p12 = {p["name"]: p["id"] for p in load(MCDATA / "1.12" / "particles.json")}
    p12_by_id = {v: k for k, v in p12.items()}
    p210_by_name = {n: i for i, n in PARTICLES_210}
    for n in p210_by_name:
        if n not in p12:
            fail(f"パーティクル {n} が 1.12 の名前に無い")
    # 共有パーティクルは 210 と 316 で数値 ID が同一（実 jar 抽出の
    # 210 と mcdata 1.12 が名前・ID とも一致）なので、表は 316 の
    # 数値同値になるはず。1.11 追加の totem(47)／spit(48) だけが
    # explode(0) へ落ちる。なお 316 の表自体に非可逆な寄せがある
    # （hugeexplosion と largeexplode が 758 の explosion_emitter
    # を共有、townaura ⇔ mycelium 等）ため往復照合はしない。
    pfwd = []
    for t in range(len(pfwd316)):
        name = p12_by_id.get(pfwd316[t])
        pfwd.append(p210_by_name.get(name, 0))
    prev = []
    for i in range(0, 47):
        name = dict(PARTICLES_210).get(i)
        prev.append(prev316[p12[name]] if name in p12 else 0)
    for t in range(len(pfwd316)):
        want = pfwd316[t] if pfwd316[t] not in (47, 48) else 0
        if pfwd[t] != want:
            fail(f"パーティクル fwd[{t}]={pfwd[t]} が 316 同値 {want} でない")
    for i in range(47):
        if prev[i] != prev316[i]:
            fail(f"パーティクル rev[{i}]={prev[i]} が 316 同値 "
                 f"{prev316[i]} でない")

    # ---------- ブロックエンティティ ----------
    befwd316, berev316 = t316["BLOCK_ENTITY"]
    be_names316 = gen316_names316()
    be210_legacy = list(BLOCK_ENTITIES_210_RAW)
    be210 = []
    for raw in be210_legacy:
        if raw not in BLOCK_ENTITY_RENAME_210:
            fail(f"ブロックエンティティ旧式名 {raw} が rename 表に無い")
        be210.append(BLOCK_ENTITY_RENAME_210[raw])
    be210_idx = {n: i for i, n in enumerate(be210)}
    befwd = []
    for t in range(len(befwd316)):
        v = befwd316[t]
        if v < 0:
            befwd.append(-1)  # bed は 210 に無い（316 と同じく落とす）
        else:
            befwd.append(be210_idx.get(be_names316[v], -1))
    berev = []
    for n in be210:
        if n in be_names316:
            berev.append(berev316[be_names316.index(n)])
        else:
            # note_block／flower_pot は 758 のレジストリに無い
            # （Flattening で消えた）。フォールバックは 0。
            berev.append(0)

    # ---------- バイオーム／ダメージ種別（316 と同一） ----------
    bifwd, birev = t316["BIOME"]
    dfwd, drev = t316["DAMAGE_TYPE"]

    tables = {
        "BLOCK_STATE": (fwd, rev),
        "ITEM": (ifwd, irev),
        "ENTITY_TYPE": (efwd, erev),
        "SOUND": (sfwd, srev),
        "PARTICLE": (pfwd, prev),
        "BLOCK": (bfwd, brev),
        "BLOCK_ENTITY": (befwd, berev),
        "BIOME": (bifwd, birev),
        "DAMAGE_TYPE": (dfwd, drev),
    }
    meta = {
        "pig_210": 90,
        "pig_758": pig758,
        "generic_sound_210": generic210,
        "stone_break_758": meta316["stone_break_758"],
        "plains_758": meta316["plains_758"],
        "be_names": be210,
        "be_legacy_names": be210_legacy,
        "biome_names": meta316["biome_names"],
    }
    return tables, meta, problems


def gen316_names316():
    """RegistryTables316 の BLOCK_ENTITY_NAMES_316 を読む。"""
    import re
    text = (JAVA_DIR / "RegistryTables316.java").read_text(encoding="utf-8")
    m = re.search(r"BLOCK_ENTITY_NAMES_316 = \{(.*?)\};", text, re.S)
    return re.findall(r'"([a-z0-9_]+)"', m.group(1))


def sound_name_fallbacks(tables, meta):
    """Stop Sound 用の名前フォールバック対（316 の流儀で 210 版を作る）。

    土台は sound-names-758-to-316.json／sound-names-316-to-758.json
    （[758名, 316名] の対のリスト）。316 名が 210 にあればそれを、
    無ければ（1.11 追加音）316 側の近似と同じ考え方で 210 の
    名前空間から接頭辞の近いものを探す。
    """
    snd210 = set(SOUNDS_210)
    s316 = load(TOOLS / "sounds-316.json")
    pairs758_316 = load(TOOLS / "sound-names-758-to-316.json")
    pairs316_758 = load(TOOLS / "sound-names-316-to-758.json")
    name316_of_758 = {a: b for a, b in pairs758_316}
    name758_of_316 = {a: b for a, b in pairs316_758}
    sounds758 = load(Path.home() / "workspace" / "mcdata404"
                     / "mapping-1.18.json")["sounds"]

    def approx(name, space):
        toks = name.split(".")
        for k in range(len(toks) - 1, 0, -1):
            pref = ".".join(toks[:k]) + "."
            cand = sorted(s for s in space if s.startswith(pref))
            if cand:
                return cand[0]
        return None

    fwd_pairs = []
    for n758 in sounds758:
        n316 = name316_of_758.get(n758, n758)
        if n316 in snd210:
            n210 = n316
        else:
            n210 = approx(n316, snd210)
        if n210 and n210 != n758:
            fwd_pairs.append([n758, n210])
    rev_pairs = []
    for n210 in SOUNDS_210:
        n316 = SOUND_RENAME_210.get(n210, n210)
        n758 = name758_of_316.get(n316, n316)
        if n758 != n210:
            rev_pairs.append([n210, n758])
    return fwd_pairs, rev_pairs


def emit_java(tables, meta, snd_names, snd_names_rev):
    header = """package io.detergentmc.protocol;

/**
 * 210（1.10／1.10.1／1.10.2）のレジストリ対応表（区間＋上書きの生の表）。
 *
 * <p>{@link RegistryRemapper} から分離した。理由はバイトコードの制限：
 * 表を RegistryRemapper に置くと静的初期化子（{@code <clinit>}）が
 * 64KB を超えてコンパイルできなくなる（"code too large"）ため。
 * このクラスの表は {@link RegistryRemapper} から static import で
 * 同じ名前のまま参照する。
 *
 * <p>表の内容と検証は {@code detergentmc-protocol/tools/} を参照：
 * 生成は {@code gen-210-tables.py}、検証は {@code verify-210-tables.py}。
 * 作り方は 316 の表との合成：210 は 315／316 と同じ Flattening 前の
 * 世代で値は (数値ID&lt;&lt;4)|メタデータ（4096 通り）。315／316 との
 * 差分は 1.11 追加分だけ（ブロック 17 種・アイテム 21 件・
 * エンティティ新規 4 種と亜種 9 種・音 31 件・パーティクル 2 件・
 * ブロックエンティティ shulker_box）。ブロック（219 種）・
 * エンティティ（61 登録）・音（463 件）・ブロックエンティティ
 * （23 件）の登録順は実バニラ 1.10.2 jar のバイトコードから
 * 抽出した（extract-210-registries.py）。
 *
 * <p>近似・制限（詳細は docs/PROXY-1.10.2.md）：
 * observer は dropper へ、シュルカーボックスは chest へ寄せる
 * （色は落ちる）。totem は golden_apple、shulker_shell は
 * prismarine_shard、iron_nugget は gold_nugget へ寄せる。
 * 1.11 の新規モブは pig へ、独立 ID 化した亜種は基本種へ畳む。
 * ブロックエンティティの NBT id は 210 の旧式名（"Furnace" 等）
 * で読み書きする（{@code BLOCK_ENTITY_LEGACY_NAMES_210}）。
 * bed のブロックエンティティは落とす（316 と同じ）。
 */
final class RegistryTables210 {

    private RegistryTables210() {
    }

"""
    parts = [header]
    chunk_files = {}
    fallbacks_fwd = {
        "BLOCK_STATE": 16, "ITEM": 1, "ENTITY_TYPE": meta["pig_210"],
        "SOUND": meta["generic_sound_210"], "PARTICLE": 0, "BLOCK": 1,
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
                    cname = f"RegistryTables210{prefix}{ci // 1200 + 1}"
                    body = ["package io.detergentmc.protocol;",
                            "",
                            f"/** 210 のブロック状態の上書き表の分割クラス"
                            f"（{prefix}、{ci // 1200 + 1} 個目）。"
                            f"生成は gen-210-tables.py。 */",
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
            parts.append(java_table(f"{name}_210", fr, [], n_fwd, "758->210")
                         .replace(f"static final int[][] {name}_210_OVERRIDES = {{",
                                  f"static final int[][] {name}_210_OVERRIDES_PLACEHOLDER = {{"))
            parts[-1] = parts[-1].replace(
                f"    static final int[][] {name}_210_OVERRIDES_PLACEHOLDER = {{\n    }};",
                f"    static final int[][] {name}_210_OVERRIDES = concat("
                + ", ".join(fwd_names) + ");")
            parts.append("")
            parts.append(java_table(f"{name}_210_TO_758", rr, [], n_rev, "210->758")
                         .replace(f"static final int[][] {name}_210_TO_758_OVERRIDES = {{",
                                  f"static final int[][] {name}_210_TO_758_OVERRIDES_PLACEHOLDER = {{"))
            parts[-1] = parts[-1].replace(
                f"    static final int[][] {name}_210_TO_758_OVERRIDES_PLACEHOLDER = {{\n    }};",
                f"    static final int[][] {name}_210_TO_758_OVERRIDES = concat("
                + ", ".join(rev_names) + ");")
            parts.append("")
            continue
        parts.append(java_table(f"{name}_210", fr, fo, n_fwd, "758->210"))
        parts.append("")
        parts.append(java_table(f"{name}_210_TO_758", rr, ro, n_rev, "210->758"))
        parts.append("")
    parts.append("    /** 音の名前：758 → 210（Stop Sound 用）。実体は RegistryTables210Snd。 */")
    parts.append("    static final String[][] SOUND_NAME_FALLBACKS_758_TO_210 ="
                 " RegistryTables210Snd.FWD;")
    parts.append("")
    parts.append("    /** 音の名前：210 → 758（Stop Sound 用）。実体は RegistryTables210Snd。 */")
    parts.append("    static final String[][] SOUND_NAME_FALLBACKS_210_TO_758 ="
                 " RegistryTables210Snd.REV;")
    parts.append("")
    snd_body = ["package io.detergentmc.protocol;",
                "",
                "/**",
                " * 210（1.10／1.10.1／1.10.2）の音の名前フォールバック表（分離クラス）。",
                " *",
                " * <p>RegistryTables210 の静的初期化子が 64KB 制限を超えるため",
                " * 音の名前表だけをここへ分けた。生成は tools/gen-210-tables.py、",
                " * 検証は tools/verify-210-tables.py。",
                " */",
                "final class RegistryTables210Snd {",
                "",
                "    private RegistryTables210Snd() {",
                "    }",
                "",
                "    /** 音の名前：758 → 210（Stop Sound 用）。数値表で届かない音の寄せ先。 */",
                "    static final String[][] FWD = {"]
    for src, dst in snd_names:
        snd_body.append(f'            {{"{src}", "{dst}"}},')
    snd_body.append("    };")
    snd_body.append("")
    snd_body.append("    /** 音の名前：210 → 758（Stop Sound 用）。1.11 での改名を戻す。 */")
    snd_body.append("    static final String[][] REV = {")
    for src, dst in snd_names_rev:
        snd_body.append(f'            {{"{src}", "{dst}"}},')
    snd_body.append("    };")
    snd_body.append("}")
    chunk_files["RegistryTables210Snd"] = "\n".join(snd_body) + "\n"

    def names_array(field, names, comment):
        lines = [f"    /** {comment} */",
                 f"    static final String[] {field} = {{"]
        for i in range(0, len(names), 4):
            chunk = names[i:i + 4]
            lines.append("            " + " ".join(f'"{n}",' for n in chunk))
        lines.append("    };")
        return "\n".join(lines)
    parts.append(names_array(
        "BLOCK_ENTITY_NAMES_210", meta["be_names"],
        "ブロックエンティティの正規化名（210 の登録順、添字=210 の種別）。"))
    parts.append("")
    parts.append(names_array(
        "BLOCK_ENTITY_LEGACY_NAMES_210", meta["be_legacy_names"],
        "ブロックエンティティの旧式 NBT id（210 の登録順、名前空間なし）。"
        "1.10.2 の実バニラはこの名前で NBT の id を読み書きする。"))
    parts.append("")
    parts.append(names_array("BIOME_NAMES_210", meta["biome_names"],
                             "バイオーム名（316 と同一）。"))
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
    ap.add_argument("--out", default=str(JAVA_DIR / "RegistryTables210.java"))
    ap.add_argument("--json", default=None)
    args = ap.parse_args()

    # 土台の 316 の表（gen-316-tables.py の build_all を再利用）
    class A:  # gen316.build_all が読む引数
        tables340 = str(base / "mcdata404" / "tables-340.json")
        mcdata11 = str(MCDATA / "1.11")
        mcdata12 = str(MCDATA / "1.12")
        mcdata = str(base / "mcdata404")
    tables316, _report, meta316 = gen316.build_all(A())
    t316 = {k: (v[0], v[1]) for k, v in tables316.items()}

    tables, meta, problems = build_tables(t316, meta316)
    if problems:
        for p in problems:
            print("NG:", p, file=sys.stderr)
        return 1
    print("合成の照合は全件成立。")
    print(f"MAX 値: BLOCK_STATE_210=4095, ITEM_210={len(tables['ITEM'][1]) - 1}, "
          f"ENTITY_TYPE_210={len(tables['ENTITY_TYPE'][1]) - 1}, "
          f"SOUND_210={len(tables['SOUND'][1]) - 1}, "
          f"PARTICLE_210={len(tables['PARTICLE'][1]) - 1}, BLOCK_210=255, "
          f"BIOME_210={len(tables['BIOME'][1]) - 1}, "
          f"BLOCK_ENTITY_210={len(tables['BLOCK_ENTITY'][1]) - 1}")
    print(f"代替: GENERIC_SOUND_210={meta['generic_sound_210']}, "
          f"PIG_210={meta['pig_210']}")

    snd_names, snd_names_rev = sound_name_fallbacks(tables, meta)
    print(f"音の名前フォールバック: 758→210 {len(snd_names)} 対、"
          f"210→758 {len(snd_names_rev)} 対")
    (TOOLS / "sound-names-758-to-210.json").write_text(
        json.dumps(snd_names, ensure_ascii=False) + "\n", encoding="utf-8")
    (TOOLS / "sound-names-210-to-758.json").write_text(
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
    return 0


if __name__ == "__main__":
    sys.exit(main())
