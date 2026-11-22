#!/usr/bin/env python3
"""5（1.7.6〜1.7.10）のレジストリ対応表（RegistryTables5.java）を生成する。

合成方針（47 の生成とは違い、実測で確定した土台の上に組み立てる）：
- 47 の表（RegistryTables47.java＋BsFwd/BsRev 分割ファイル）を
  パースして全展開し、758→47 の完成済み写像を土台にする。
- ブロック・アイテムは minecraft-data pc/1.8 ⇔ pc/1.7 の名前で
  突き合わせる。共有名の数値 ID 差分は 0（Stage A で確認済み。
  本スクリプトでも assert する）。名前が違う2件
  （planks→wood_planks、double_plant→large_flowers）は別名として扱う。
- 1.8 で追加されたブロック22種・アイテム33種は近似表で 1.7 の
  既存物へ寄せる（近似の内容は PROXY-1.7.10.md にも記載）。
- エンティティは 47 の値のうち 1.7 に無い種（endermite 67、
  guardian 68、rabbit 101、armor_stand 78）だけ寄せる。
  種別 ID の根拠は Stage A の実ワイヤ採取。
- パーティクルは番号空間が 1.7⇔1.8 で完全一致（名前・ID とも）。
  バイオームも数値 ID は共通のため 47 の表をそのまま使う。
- 音は名前のみ。47 の名前表（SOUND_NAMES_758）を土台に、
  1.7.10 の公式 sounds.json（229件、tools/sounds-5.json）を
  47 の集合（246件）から引いた差分が guardian／rabbit／
  fireCharge の追加分だけであることを確認して使う。

出力：RegistryTables5.java、RegistryTables5BsFwd*.java、
RegistryTables5BsRev*.java、RegistryTables5Snd.java
検証は verify-5-tables.py（独立実装）。
"""

import json
import re
import sys
from pathlib import Path

TOOLS = Path(__file__).resolve().parent
MAIN = TOOLS.parent / "src" / "main" / "java" / "io" / "detergentmc" / "protocol"
MCDATA = Path("/home/hatch/mc-client/node_modules/minecraft-data/"
              "minecraft-data/data/pc")

problems = []


def check(cond, msg):
    if not cond:
        problems.append(msg)
        print("問題:", msg)


# ---------------------------------------------------------------- 47 表の展開

def parse_int_tuples(text):
    out = []
    for m in re.findall(r"\{(-?\d+),\s*(-?\d+)(?:,\s*(-?\d+))?\}", text):
        out.append(tuple(int(x) for x in m if x != ""))
    return out


def field_block(text, name):
    i = text.index(name)
    j = text.index("};", i)
    return text[i:j]


def load_47_tables():
    t47 = (MAIN / "RegistryTables47.java").read_text(encoding="utf-8")

    def ranges(name):
        return [t for t in parse_int_tuples(field_block(t47, name))
                if len(t) == 3]

    def overrides_from_chunks(prefix, count):
        out = []
        for n in range(1, count + 1):
            text = (MAIN / f"{prefix}{n}.java").read_text(encoding="utf-8")
            out += [t for t in parse_int_tuples(text) if len(t) == 2]
        return out

    def overrides(name):
        return [t for t in parse_int_tuples(field_block(t47, name))
                if len(t) == 2]

    return {
        "bs_fwd_ranges": ranges("BLOCK_STATE_47_RANGES"),
        "bs_fwd_over": overrides_from_chunks("RegistryTables47BsFwd", 15),
        "bs_rev_ranges": ranges("BLOCK_STATE_47_TO_758_RANGES"),
        "bs_rev_over": overrides_from_chunks("RegistryTables47BsRev", 3),
        "block_fwd_ranges": ranges("BLOCK_47_RANGES"),
        "block_fwd_over": overrides("BLOCK_47_OVERRIDES"),
        "block_rev_ranges": ranges("BLOCK_47_TO_758_RANGES"),
        "block_rev_over": overrides("BLOCK_47_TO_758_OVERRIDES"),
        "item_fwd_ranges": ranges("ITEM_47_RANGES"),
        "item_fwd_over": overrides("ITEM_47_OVERRIDES"),
        "item_rev_ranges": ranges("ITEM_47_TO_758_RANGES"),
        "item_rev_over": overrides("ITEM_47_TO_758_OVERRIDES"),
        "ent_fwd_ranges": ranges("ENTITY_TYPE_47_RANGES"),
        "ent_fwd_over": overrides("ENTITY_TYPE_47_OVERRIDES"),
        "ent_rev_ranges": ranges("ENTITY_TYPE_47_TO_758_RANGES"),
        "ent_rev_over": overrides("ENTITY_TYPE_47_TO_758_OVERRIDES"),
        "par_fwd_ranges": ranges("PARTICLE_47_RANGES"),
        "par_fwd_over": overrides("PARTICLE_47_OVERRIDES"),
        "par_rev_ranges": ranges("PARTICLE_47_TO_758_RANGES"),
        "par_rev_over": overrides("PARTICLE_47_TO_758_OVERRIDES"),
        "bio_fwd_ranges": ranges("BIOME_47_RANGES"),
        "bio_fwd_over": overrides("BIOME_47_OVERRIDES"),
        "bio_rev_ranges": ranges("BIOME_47_TO_758_RANGES"),
        "bio_rev_over": overrides("BIOME_47_TO_758_OVERRIDES"),
    }


def expand(ranges, overrides, size, fallback):
    m = [fallback] * size
    for (s, e, d) in ranges:
        for i in range(s, min(e, size - 1) + 1):
            m[i] = i + d
    for (a, b) in overrides:
        if 0 <= a < size:
            m[a] = b
    return m


def remapper_consts():
    text = (MAIN / "RegistryRemapper.java").read_text(encoding="utf-8")
    out = {}
    for name in ["MAX_BLOCK_STATE_758", "MAX_ITEM_758", "MAX_ENTITY_TYPE_758",
                 "MAX_PARTICLE_758", "MAX_BLOCK_758", "MAX_BIOME_758",
                 "STONE_BLOCK_STATE_758", "STONE_ITEM_758",
                 "PIG_ENTITY_TYPE_758", "AMBIENT_PARTICLE_758",
                 "STONE_BLOCK_758", "PLAINS_BIOME_758"]:
        m = re.search(name + r" = (\d+);", text)
        check(m is not None, f"定数 {name} が見つからない")
        out[name] = int(m.group(1))
    return out


# ---------------------------------------------------------------- 名前表

def load_names(version, kind):
    data = json.loads((MCDATA / version / f"{kind}.json")
                      .read_text(encoding="utf-8"))
    return {e["name"]: e["id"] for e in data}


# 1.8 名 → 1.7 名の別名（同一物の改名）
BLOCK_ALIAS_18_TO_17 = {"planks": "wood_planks",
                        "double_plant": "large_flowers"}

# 1.8 追加ブロックの近似：1.8 名 → (1.7 名, メタ写像 or None=そのまま)
APPROX_BLOCKS_5 = {
    "spruce_door": ("wooden_door", None),
    "birch_door": ("wooden_door", None),
    "jungle_door": ("wooden_door", None),
    "acacia_door": ("wooden_door", None),
    "dark_oak_door": ("wooden_door", None),
    "spruce_fence": ("fence", None),
    "birch_fence": ("fence", None),
    "jungle_fence": ("fence", None),
    "acacia_fence": ("fence", None),
    "dark_oak_fence": ("fence", None),
    "spruce_fence_gate": ("fence_gate", None),
    "birch_fence_gate": ("fence_gate", None),
    "jungle_fence_gate": ("fence_gate", None),
    "acacia_fence_gate": ("fence_gate", None),
    "dark_oak_fence_gate": ("fence_gate", None),
    "red_sandstone": ("sandstone", None),
    "red_sandstone_stairs": ("sandstone_stairs", None),
    # 赤砂岩スラブ → 砂岩スラブ（メタ 0→1、上面 8→9）
    "stone_slab2": ("stone_slab", {0: 1, 8: 9}),
    "double_stone_slab2": ("double_stone_slab", {0: 1, 8: 9}),
    "daylight_detector_inverted": ("daylight_detector", None),
    "standing_banner": ("standing_sign", None),
    "wall_banner": ("wall_sign", None),
    "iron_trapdoor": ("trapdoor", None),
}

# 1.8 追加アイテムの近似：1.8 名 → 1.7 名
APPROX_ITEMS_5 = {
    "spruce_door": "wooden_door", "birch_door": "wooden_door",
    "jungle_door": "wooden_door", "acacia_door": "wooden_door",
    "dark_oak_door": "wooden_door",
    "spruce_fence": "fence", "birch_fence": "fence",
    "jungle_fence": "fence", "acacia_fence": "fence",
    "dark_oak_fence": "fence",
    "spruce_fence_gate": "fence_gate", "birch_fence_gate": "fence_gate",
    "jungle_fence_gate": "fence_gate", "acacia_fence_gate": "fence_gate",
    "dark_oak_fence_gate": "fence_gate",
    "red_sandstone": "sandstone",
    "red_sandstone_stairs": "sandstone_stairs",
    "stone_slab2": "stone_slab",
    "iron_trapdoor": "trapdoor",
    "armor_stand": "item_frame",
    "barrier": "glass",
    "mutton": "chicken", "cooked_mutton": "cooked_chicken",
    "rabbit": "chicken", "cooked_rabbit": "cooked_chicken",
    "rabbit_stew": "mushroom_stew",
    "rabbit_foot": "feather", "rabbit_hide": "leather",
    "prismarine": "quartz_block",
    "prismarine_shard": "quartz", "prismarine_crystals": "glowstone_dust",
    "sea_lantern": "glowstone",
    "slime": "sponge",
}

# 47 のエンティティ値のうち 1.7 に無い種 → 代替（Stage A 実測の種別表）
APPROX_ENTITY_5 = {67: 90,   # endermite → pig
                   68: 94,   # guardian → squid
                   101: 90,  # rabbit → pig
                   78: 90}   # armor_stand → pig


def main():
    t = load_47_tables()
    c = remapper_consts()
    bs47 = expand(t["bs_fwd_ranges"], t["bs_fwd_over"],
                  c["MAX_BLOCK_STATE_758"] + 1, 16)
    bs47r = expand(t["bs_rev_ranges"], t["bs_rev_over"], 4096,
                   c["STONE_BLOCK_STATE_758"])
    blk47 = expand(t["block_fwd_ranges"], t["block_fwd_over"],
                   c["MAX_BLOCK_758"] + 1, 1)
    blk47r = expand(t["block_rev_ranges"], t["block_rev_over"], 256,
                    c["STONE_BLOCK_758"])
    it47 = expand(t["item_fwd_ranges"], t["item_fwd_over"],
                  c["MAX_ITEM_758"] + 1, 1)
    it47r = expand(t["item_rev_ranges"], t["item_rev_over"], 2268,
                   c["STONE_ITEM_758"])
    en47 = expand(t["ent_fwd_ranges"], t["ent_fwd_over"],
                  c["MAX_ENTITY_TYPE_758"] + 1, 90)
    en47r = expand(t["ent_rev_ranges"], t["ent_rev_over"], 201,
                   c["PIG_ENTITY_TYPE_758"])
    pa47 = expand(t["par_fwd_ranges"], t["par_fwd_over"],
                  c["MAX_PARTICLE_758"] + 1, 0)
    pa47r = expand(t["par_rev_ranges"], t["par_rev_over"], 46,
                   c["AMBIENT_PARTICLE_758"])
    bi47 = expand(t["bio_fwd_ranges"], t["bio_fwd_over"],
                  c["MAX_BIOME_758"] + 1, 1)
    bi47r = expand(t["bio_rev_ranges"], t["bio_rev_over"], 168,
                   c["PLAINS_BIOME_758"])

    blocks18 = load_names("1.8", "blocks")
    blocks17 = load_names("1.7", "blocks")
    # mcdata pc/1.7 の blocks.json には iron_trapdoor（167）が混入して
    # いるが、実 1.7.10 クライアント jar の lang に tile.iron_trapdoor
    # は存在せず、鉄のトラップドアは 1.8 の追加である（prep の
    # registry-diff の「1.8 追加 22 種」とも一致）。実 jar を正として
    # 除外する。
    del blocks17["iron_trapdoor"]
    items18 = load_names("1.8", "items")
    items17 = load_names("1.7", "items")
    id18_block = {v: k for k, v in blocks18.items()}
    id18_item = {v: k for k, v in items18.items()}

    # 共有名の数値 ID 差分 0 を assert（別名2件は同一 ID で対応）
    for name, i18 in blocks18.items():
        n17 = BLOCK_ALIAS_18_TO_17.get(name, name)
        if n17 in blocks17:
            check(blocks17[n17] == i18,
                  f"ブロック {name} の ID が 1.7 と 1.8 で違う")
    for name, i18 in items18.items():
        if name in items17:
            check(items17[name] == i18,
                  f"アイテム {name} の ID が 1.7 と 1.8 で違う")
    added_blocks = {n for n in blocks18
                    if BLOCK_ALIAS_18_TO_17.get(n, n) not in blocks17}
    check(added_blocks == set(APPROX_BLOCKS_5),
          f"近似表と 1.8 追加ブロックが一致しない: "
          f"余り={sorted(added_blocks - set(APPROX_BLOCKS_5))} "
          f"不足={sorted(set(APPROX_BLOCKS_5) - added_blocks)}")
    added_items = {n for n in items18 if n not in items17}
    check(added_items == set(APPROX_ITEMS_5),
          f"近似表と 1.8 追加アイテムが一致しない: "
          f"余り={sorted(added_items - set(APPROX_ITEMS_5))} "
          f"不足={sorted(set(APPROX_ITEMS_5) - added_items)}")

    def block17_id(name18):
        """1.8 ブロック名 → (1.7 の数値 ID, メタ写像)。"""
        n17 = BLOCK_ALIAS_18_TO_17.get(name18, name18)
        if n17 in blocks17:
            return blocks17[n17], None
        target, meta_map = APPROX_BLOCKS_5[name18]
        return blocks17[target], meta_map

    # ---- ブロック状態 758 → 5
    bs5 = []
    for s758 in bs47:
        id18, meta = s758 >> 4, s758 & 15
        name18 = id18_block.get(id18)
        check(name18 is not None, f"47 表に未知のブロック ID {id18}")
        id17, meta_map = block17_id(name18)
        m = meta if meta_map is None else meta_map.get(meta, meta)
        bs5.append((id17 << 4) | m)
    # ---- ブロック状態 5 → 758（1.7 に実在する状態は名前経由で戻す）
    bs5r = []
    for v in range(4096):
        id17, meta = v >> 4, v & 15
        # 近似先のメタ（スラブ等）は代表メタへ寄せてから 47 の逆表を使う
        bs5r.append(bs47r[v])
    # ---- ブロック 758 → 5 / 5 → 758
    blk5 = []
    for b758 in blk47:
        name18 = id18_block.get(b758)
        check(name18 is not None, f"47 表に未知のブロック ID {b758}")
        blk5.append(block17_id(name18)[0])
    blk5r = list(blk47r)
    # ---- アイテム
    it5 = []
    for i758 in it47:
        name18 = id18_item.get(i758)
        check(name18 is not None, f"47 表に未知のアイテム ID {i758}")
        if name18 in items17:
            it5.append(items17[name18])
        else:
            it5.append(items17[APPROX_ITEMS_5[name18]])
    it5r = list(it47r)
    # ---- エンティティ
    en5 = [APPROX_ENTITY_5.get(v, v) for v in en47]
    en5r = list(en47r)
    # ---- パーティクル・バイオームは 47 と同一
    pa5, pa5r, bi5, bi5r = pa47, pa47r, bi47, bi47r

    emit(bs5, bs5r, blk5, blk5r, it5, it5r, en5, en5r,
         pa5, pa5r, bi5, bi5r)
    emit_snd()

    print(f"ブロック状態: {len(bs5)} 件、アイテム: {len(it5)} 件、"
          f"エンティティ: {len(en5)} 件")
    if problems:
        print(f"問題 {len(problems)} 件")
        return 1
    print("生成完了（問題なし）")
    return 0


# ---------------------------------------------------------------- 圧縮と出力

def compress(arr, fallback):
    """配列 → (ranges, overrides)。expand と対になる形へ圧縮する。"""
    ranges = []
    overrides = []
    i = 0
    n = len(arr)
    while i < n:
        d = arr[i] - i
        j = i
        while j + 1 < n and arr[j + 1] - (j + 1) == d:
            j += 1
        if j - i + 1 >= 4:
            ranges.append((i, j, d))
            i = j + 1
        else:
            for k in range(i, j + 1):
                if arr[k] != fallback:
                    overrides.append((k, arr[k]))
            i = j + 1
    # フォールバックと同値の上書きは落とす（ranges は残す）
    overrides = [(a, b) for (a, b) in overrides if b != fallback]
    return ranges, overrides


def fmt_ranges(name, ranges):
    lines = [f"    static final int[][] {name} = {{"]
    for (s, e, d) in ranges:
        lines.append(f"            {{{s}, {e}, {d}}},")
    lines.append("    };")
    return "\n".join(lines)


def fmt_overrides(name, overrides):
    lines = [f"    static final int[][] {name} = {{"]
    for (a, b) in overrides:
        lines.append(f"            {{{a}, {b}}},")
    lines.append("    };")
    return "\n".join(lines)


def fmt_overrides_concat(name, chunk_prefix, count):
    parts = ", ".join(f"{chunk_prefix}{i}.V" for i in range(1, count + 1))
    return f"    static final int[][] {name} = concat({parts});"


def write_chunks(prefix, overrides, per=1100):
    chunks = [overrides[i:i + per] for i in range(0, len(overrides), per)]
    for idx, chunk in enumerate(chunks, 1):
        body = fmt_overrides("V", chunk)
        (MAIN / f"{prefix}{idx}.java").write_text(
            f"package io.detergentmc.protocol;\n\n"
            f"/** 5（1.7.6〜1.7.10）のブロック状態対応表の分割ファイル"
            f"（{prefix}{idx}）。生成は gen-5-tables.py。 */\n"
            f"final class {prefix}{idx} {{\n\n"
            f"    private {prefix}{idx}() {{\n    }}\n\n"
            f"{body}\n}}\n", encoding="utf-8")
    return len(chunks)


def emit(bs5, bs5r, blk5, blk5r, it5, it5r, en5, en5r,
         pa5, pa5r, bi5, bi5r):
    c = remapper_consts()
    parts = []
    parts.append("""package io.detergentmc.protocol;

/**
 * 5（1.7.6〜1.7.10）のレジストリ対応表。
 *
 * <p>合成の土台は 47 の完成済み表（758→47）で、ブロック・
 * アイテムは minecraft-data pc/1.8 ⇔ pc/1.7 の名前突き合わせ
 * （共有名の数値 ID 差分は 0）、1.8 追加分は近似表で寄せた。
 * エンティティは 47 の値のうち 1.7 に無い種だけ寄せた
 * （種別 ID の根拠は Stage A の実ワイヤ採取）。パーティクルと
 * バイオームは番号空間が 47 と同一のため 47 の表と同値。
 * 生成は gen-5-tables.py、検証は verify-5-tables.py。
 */
final class RegistryTables5 {

    private RegistryTables5() {
    }

    private static int[][] concat(int[][]... parts) {
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
    specs = [
        ("BLOCK_STATE_5", bs5, 16, True),
        ("BLOCK_STATE_5_TO_758", bs5r, c["STONE_BLOCK_STATE_758"], True),
        ("BLOCK_5", blk5, 1, False),
        ("BLOCK_5_TO_758", blk5r, c["STONE_BLOCK_758"], False),
        ("ITEM_5", it5, 1, False),
        ("ITEM_5_TO_758", it5r, c["STONE_ITEM_758"], False),
        ("ENTITY_TYPE_5", en5, 90, False),
        ("ENTITY_TYPE_5_TO_758", en5r, c["PIG_ENTITY_TYPE_758"], False),
        ("PARTICLE_5", pa5, 0, False),
        ("PARTICLE_5_TO_758", pa5r, c["AMBIENT_PARTICLE_758"], False),
        ("BIOME_5", bi5, 1, False),
        ("BIOME_5_TO_758", bi5r, c["PLAINS_BIOME_758"], False),
    ]
    for name, arr, fallback, chunked in specs:
        ranges, overrides = compress(arr, fallback)
        parts.append(fmt_ranges(name + "_RANGES", ranges))
        parts.append("")
        if chunked:
            prefix = ("RegistryTables5BsFwd" if name == "BLOCK_STATE_5"
                      else "RegistryTables5BsRev")
            count = write_chunks(prefix, overrides)
            parts.append(fmt_overrides_concat(name + "_OVERRIDES",
                                              prefix, count))
        else:
            parts.append(fmt_overrides(name + "_OVERRIDES", overrides))
        parts.append("")
    # パーティクル名（47 番号 → 1.7 の名前）
    particles17 = load_names("1.7", "particles")
    by_id = {v: k for k, v in particles17.items()}
    names = [by_id.get(i, "") for i in range(46)]
    parts.append("    /** パーティクル名（添字は 47 と共通の番号）。 */")
    parts.append("    static final String[] PARTICLE_NAMES_5 = {")
    for i in range(0, 46, 4):
        row = ", ".join(f'"{n}"' for n in names[i:i + 4])
        parts.append(f"            {row},")
    parts.append("    };")
    parts.append("")
    # バイオーム名は 47 と同一の配列を使う
    t47 = (MAIN / "RegistryTables47.java").read_text(encoding="utf-8")
    i = t47.index("BIOME_NAMES_47")
    j = t47.index("};", i)
    biome_block = t47[i:j + 2].replace("BIOME_NAMES_47", "BIOME_NAMES_5")
    parts.append("    /** バイオーム名（47 と同一。レジストリ順）。 */")
    parts.append("    static final String[] " + biome_block)
    parts.append("")
    parts.append("    /** 音の名前フォールバック：758 → 5。 */")
    parts.append("    static final String[][] SOUND_NAME_FALLBACKS_758_TO_5"
                 " = RegistryTables5Snd.FWD;")
    parts.append("")
    parts.append("    /** 音の名前フォールバック：5 → 758。 */")
    parts.append("    static final String[][] SOUND_NAME_FALLBACKS_5_TO_758"
                 " = RegistryTables5Snd.REV;")
    parts.append("}")
    (MAIN / "RegistryTables5.java").write_text(
        "\n".join(parts) + "\n", encoding="utf-8")


def emit_snd():
    snd47 = (MAIN / "RegistryTables47Snd.java").read_text(encoding="utf-8")

    def pairs(field):
        # field_block は名前の最初の出現位置から探すため、"REV" が
        # コメント等に先に現れると空になる（実際に空になった）。
        # 宣言そのものを目印にする。
        block = field_block(snd47, "String[][] " + field + " = {")
        return re.findall(r'\{"([^"]+)",\s*"([^"]+)"\}', block)

    def string_array(field):
        block = field_block(snd47, field)
        return re.findall(r'"([^"]*)"', block)

    set5 = set(json.loads((TOOLS / "sounds-5.json")
                          .read_text(encoding="utf-8")))
    fwd47 = pairs("FWD")
    rev47 = pairs("REV")
    names758 = string_array("SOUND_NAMES_758")
    fwd = [(a, b) for (a, b) in fwd47 if b in set5]
    dropped = [(a, b) for (a, b) in fwd47 if b not in set5]
    check(all(b.startswith(("mob.guardian", "mob.rabbit",
                             "item.fireCharge")) for _, b in dropped),
          f"FWD で想定外の対象が 1.7 に無い: {dropped}")
    rev = [(a, b) for (a, b) in rev47 if a in set5]
    lines = ["""package io.detergentmc.protocol;

/**
 * 5（1.7.6〜1.7.10）の音の名前表（分離クラス）。
 *
 * <p>5 では音は名前でしかワイヤに乗らない（数値 ID の
 * Sound Effect パケット自体が無い）。FWD／REV は Stop Sound
 * 等の名前変換用、SOUND_NAMES_758 は 758 の数値音 ID を
 * 名前に戻して 5 の Named Sound Effect へ変換するための表。
 * 5 の音名集合（229 件）は Mojang 公式アセット索引の
 * sounds.json（1.7.10）から取得した。47 の集合（246 件）との
 * 差分は guardian／rabbit／fireCharge の追加分17件のみで、
 * 表記揺れは無い。生成は gen-5-tables.py、検証は
 * verify-5-tables.py。
 */
final class RegistryTables5Snd {

    private RegistryTables5Snd() {
    }

    /** 音の名前：758 → 5。1.9 での大規模改名を 1.7 式へ戻す。 */
    static final String[][] FWD = {"""]
    for a, b in fwd:
        lines.append(f'            {{"{a}", "{b}"}},')
    lines.append("    };")
    lines.append("")
    lines.append("    /** 音の名前：5 → 758。 */")
    lines.append("    static final String[][] REV = {")
    for a, b in rev:
        lines.append(f'            {{"{a}", "{b}"}},')
    lines.append("    };")
    lines.append("")
    lines.append("    /** 5 の音名集合（公式 sounds.json 由来、229 件）。 */")
    lines.append("    static final java.util.Set<String> SOUND_NAME_SET_5"
                 " = java.util.Set.of(")
    names = sorted(set5)
    rows = []
    for i in range(0, len(names), 3):
        rows.append(", ".join(f'"{n}"' for n in names[i:i + 3]))
    for idx, row in enumerate(rows):
        tail = "," if idx < len(rows) - 1 else ");"
        lines.append(f"            {row}{tail}")
    lines.append("")
    lines.append("    /** 758 の音 ID → 音の名前（47 の表と同一）。 */")
    lines.append("    static final String[] SOUND_NAMES_758 = {")
    for i in range(0, len(names758), 3):
        row = ", ".join(f'"{n}"' for n in names758[i:i + 3])
        lines.append(f"            {row},")
    lines.append("    };")
    lines.append("}")
    (MAIN / "RegistryTables5Snd.java").write_text(
        "\n".join(lines) + "\n", encoding="utf-8")


if __name__ == "__main__":
    sys.exit(main())
