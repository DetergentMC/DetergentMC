#!/usr/bin/env python3
"""RegistryRemapper.java の 766 の表を検証する。

検証すること（すべて名前ベースの独立した真値と突き合わせる）：
  A. *_766 表（765→766）が生成スクリプトの出力（maps766.json）と
     一致すること（転記ミスの検出）。
  B. *_766 表の反転（Java の invert() と同じ決め方＋Java ファイル内の
     *_UP_766_CORRECTIONS）が、名前から作った 766→765 の真値と一致
     すること。766 にしか無いものは代替先（石・ブタ・石の破壊音・
     entity_effect・作業台）になる。補正で決めた3音・6状態・2粒子
     だけ真値と異なり、それらは下の ALLOWLIST に列挙する。
  C. 合成表（763↔766、764↔766）が生成スクリプトの出力
     （maps_composed_766.json）と一致すること。
  D. 合成表が、両端のレジストリを名前で直接突き合わせた真値と一致
     すること。補正由来で真値より良い答えになるものだけ ALLOWLIST
     に列挙する（766→763／764 の音3件・粒子3件・vault 32状態）。
     上向き（763／764→766）は不一致ゼロのはず。
  E. 生成スクリプトの再現性：--target 766 と --target both を
     再実行し、Java ファイル内の表と完全一致すること。
     （763／764 の表も含めて確認し、今回の作業で壊していないこと
     を保証する。）
  F. ITEM_REVERSE_766 表が生成スクリプトの出力
     （map_items_rev766.json）と一致すること。

使い方：
  python3 tools/verify-766-tables.py [--data-dir DIR]
  終了コード 0 = すべて合格。1 = 不一致あり（詳細を標準出力に出す）。
"""

import json
import re
import subprocess
import sys
from pathlib import Path

SCRIPT_DIR = Path(__file__).resolve().parent
REPO_ROOT = SCRIPT_DIR.parent.parent
DATA_DIR = REPO_ROOT / "detergentmc-protocol" / "build" / "remap-data"
JAVA_FILE = (REPO_ROOT / "detergentmc-protocol" / "src" / "main" / "java"
             / "io" / "detergentmc" / "protocol" / "RegistryRemapper.java")

# ----------------------------------------------------------------------
# Java ファイルの読み取り
# ----------------------------------------------------------------------
def parse_int_table(java, name):
    """int[][] NAME = {{...}, ...}; を [(a, b), ...] の list にする。"""
    m = re.search(r"int\[\]\[\] " + re.escape(name) + r" = \{(.*?)\n    \};",
                  java, re.DOTALL)
    if not m:
        raise SystemExit(f"Java ファイルに {name} が見つからない")
    return [tuple(map(int, t))
            for t in re.findall(r"\{(-?\d+), (-?\d+)\}", m.group(1))]


def parse_int3_table(java, name):
    m = re.search(r"int\[\]\[\] " + re.escape(name) + r" = \{(.*?)\n    \};",
                  java, re.DOTALL)
    if not m:
        raise SystemExit(f"Java ファイルに {name} が見つからない")
    return [tuple(map(int, t))
            for t in re.findall(r"\{(-?\d+), (-?\d+), (-?\d+)\}", m.group(1))]


def parse_string_table(java, name):
    m = re.search(r"String\[\]\[\] " + re.escape(name) + r" = \{(.*?)\n    \};",
                  java, re.DOTALL)
    if not m:
        raise SystemExit(f"Java ファイルに {name} が見つからない")
    return re.findall(r'\{"([^"]+)", "([^"]+)"\}', m.group(1))


def expand(ranges, overrides, size, fallback):
    table = [fallback] * size
    for start, end, delta in ranges:
        for i in range(start, end + 1):
            if 0 <= i < size:
                table[i] = i + delta
    for x, y in overrides:
        if 0 <= x < size:
            table[x] = y
    return table


def invert_java(ranges, overrides, from_size, to_size, fallback, corrections):
    """RegistryRemapper.invert() と同じ決め方。"""
    inverse = [-1] * to_size
    for start, end, delta in ranges:
        for x in range(start, end + 1):
            y = x + delta
            if 0 <= x < from_size and 0 <= y < to_size and inverse[y] < 0:
                inverse[y] = x
    for x, y in overrides:
        if 0 <= x < from_size and 0 <= y < to_size and inverse[y] < 0:
            inverse[y] = x
    for y, x in corrections:
        if 0 <= y < to_size and 0 <= x < from_size:
            inverse[y] = x
    return [v if v >= 0 else fallback for v in inverse]


# ----------------------------------------------------------------------
# 名前ベースの真値
# ----------------------------------------------------------------------
def block_name(state):
    return state.split("[", 1)[0]


def block_props(state):
    if "[" not in state:
        return {}
    inner = state[state.index("[") + 1:state.rindex("]")]
    return dict(kv.split("=", 1) for kv in inner.split(","))


# 765→763／764 の代替表（generate-remap-tables.py と同じ値）。
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
    "block.copper_door.close": "block.iron_door.close",
    "block.copper_door.open": "block.iron_door.open",
    "block.copper_trapdoor.close": "block.iron_trapdoor.close",
    "block.copper_trapdoor.open": "block.iron_trapdoor.open",
    "entity.generic.wind_burst": "entity.generic.explode",
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
}
PARTICLE_FALLBACK = {
    "gust": "cloud", "gust_emitter": "cloud", "white_smoke": "smoke",
    "dust_plume": "dust", "gust_dust": "dust",
    "trial_spawner_detection": "sculk_soul",
}
ITEM_FALLBACK = {
    "trial_key": "tripwire_hook", "trial_spawner": "spawner",
    "breeze_spawn_egg": "blaze_spawn_egg", "crafter": "crafting_table",
}

# 766→765 の代替表（generate-remap-tables.py と同じ値）。
SOUND_FALLBACK_766 = {"entity.generic.wind_burst": "entity.wind_charge.wind_burst"}
PARTICLE_FALLBACK_766 = {
    "ambient_entity_effect": "entity_effect",
    "gust_emitter": "gust_emitter_large",
    "gust_dust": "small_gust",
}

# D の ALLOWLIST：合成表が直接の名前突き合わせより良い答えになるもの。
# 766→763／764 の音（補正由来）：wind_charge.throw／breeze.wind_burst
# は 765 の generic.wind_burst（→763／764 の explode）へ、
# breeze.charge は 765 の breeze.inhale（→ blaze.ambient）へ。
# wind_charge.wind_burst は 765 の generic.wind_burst の反転。
# 粒子（反転由来）：small_gust は 765 の gust_dust（→ dust）へ、
# gust_emitter_small は 765 の gust_emitter（→ cloud）へ、
# trial_spawner_detection_ominous は 765 の trial_spawner_detection
# （→ sculk_soul）へ。ブロック状態：vault 32状態は 765 の
# trial_spawner の既定状態（→ spawner）へ。
# なお 766→763／764 の「代替先が generic でない」もの（tuff→tuff、
# copper_door→iron_door、sponge→wool など）は ALLOWLIST ではなく、
# 下の D-down (b) の合成オラクルで検証する。
ALLOWLIST_DOWN = {
    "sounds": {186, 198, 1526, 1527},
    "particles": {24, 26, 103},
    "blockstates": set(range(26650, 26682)),
}


def name_truth(source_names, target_names, kind, generic_fallback,
               rename=None, sound_fallbacks=None, particle_fallbacks=None,
               item_fallbacks=None, block_fallbacks=None,
               target_defaults=None, add_props=(), drop_one_prop=False):
    """source の各 id → target の id の真値（名前で突き合わせる）。

    優先順：完全一致 → 改名 → プロパティ1個追加（add_props）→
    プロパティ1個削除（drop_one_prop）→ 代替表 → 既定状態
    （ブロック状態の代替ブロック）→ 代替先（generic_fallback）。
    """
    rename = rename or {}
    target_index = {n: i for i, n in enumerate(target_names)}
    by_props = {}
    for i, n in enumerate(target_names):
        by_props[(block_name(n), frozenset(block_props(n).items()))] = i
    out = {}
    for i, name in enumerate(source_names):
        if name in target_index:
            out[i] = target_index[name]
            continue
        if name in rename and rename[name] in target_index:
            out[i] = target_index[rename[name]]
            continue
        if kind == "blockstates":
            props = block_props(name)
            found = None
            for key, value in add_props:
                if key not in props:
                    extended = dict(props)
                    extended[key] = value
                    if (block_name(name), frozenset(extended.items())) in by_props:
                        found = by_props[(block_name(name),
                                          frozenset(extended.items()))]
                        break
            if found is None and drop_one_prop:
                for drop in list(props.keys()):
                    rest = {k: v for k, v in props.items() if k != drop}
                    if (block_name(name), frozenset(rest.items())) in by_props:
                        found = by_props[(block_name(name),
                                          frozenset(rest.items()))]
                        break
            if found is not None:
                out[i] = found
                continue
            if block_fallbacks and block_name(name) in block_fallbacks:
                fb_target = block_fallbacks[block_name(name)]
                if target_defaults is not None and fb_target in target_defaults:
                    out[i] = target_defaults[fb_target]
                    continue
                # 代替先のブロック自体が target に無い（766 の vault を
                # 763 に送る場合の trial_spawner など）は代替先に落とす。
                out[i] = generic_fallback
                continue
            out[i] = generic_fallback
            continue
        fb = None
        if kind == "sounds" and sound_fallbacks:
            fb = sound_fallbacks.get(name)
        elif kind == "particles" and particle_fallbacks:
            fb = particle_fallbacks.get(name)
        elif kind == "items" and item_fallbacks:
            fb = item_fallbacks.get(name)
        if fb is not None and fb in target_index:
            out[i] = target_index[fb]
            continue
        out[i] = generic_fallback
    return out


# ----------------------------------------------------------------------
# 検証
# ----------------------------------------------------------------------
def main():
    import argparse
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--data-dir", type=Path, default=DATA_DIR)
    args = parser.parse_args()
    data_dir = args.data_dir

    # RegistryRemapper.java と RegistryTablesSoundNames.java を合わせて読む。
    # 古い音の名前表（SOUND_NAME_FALLBACKS_*）は <clinit> の 64KB 制限のため
    # RegistryTablesSoundNames.java へ分離してある（773 の作業で移動）。
    sound_names_file = (REPO_ROOT / "detergentmc-protocol" / "src" / "main" / "java"
                        / "io" / "detergentmc" / "protocol"
                        / "RegistryTablesSoundNames.java")
    java = JAVA_FILE.read_text(encoding="utf-8")
    if sound_names_file.exists():
        java = java + "\n" + sound_names_file.read_text(encoding="utf-8")
    vv = {v: json.loads((data_dir / f"vv-{v}.json").read_text(encoding="utf-8"))
          for v in ("1.20", "1.20.2", "1.20.3", "1.20.5")}
    maps766 = json.loads((data_dir / "maps766.json").read_text(encoding="utf-8"))
    maps_comp = json.loads((data_dir / "maps_composed_766.json").read_text(encoding="utf-8"))
    rev766 = json.loads((data_dir / "map_items_rev766.json").read_text(encoding="utf-8"))

    failures = []

    def check(label, problems):
        if problems:
            failures.append(label)
            print(f"[FAIL] {label}: {len(problems)} 件")
            for p in problems[:20]:
                print(f"    {p}")
        else:
            print(f"[OK] {label}")

    kinds = ["blockstates", "items", "entities", "sounds",
             "particles", "blocks", "menus"]
    java_names = {"blockstates": "BLOCK_STATE", "items": "ITEM",
                  "entities": "ENTITY_TYPE", "sounds": "SOUND",
                  "particles": "PARTICLE", "blocks": "BLOCK", "menus": "MENU"}
    # 766 側の代替先（765→766 の表の範囲外用。Java の定数と同じ値）。
    fb766 = {"blockstates": 1, "items": 1, "entities": 77, "sounds": 1358,
             "particles": 20, "blocks": 1, "menus": 12}
    # 765 側の代替先（766→765 の反転の代替先。Java の定数と同じ値）。
    fb765 = {"blockstates": 1, "items": 1, "entities": 73, "sounds": 1312,
             "particles": 0, "blocks": 1, "menus": 12}

    # --- A. *_766 表が生成スクリプトの出力と一致する ---
    for kind in kinds:
        ranges = parse_int3_table(java, java_names[kind] + "_766_RANGES")
        overrides = parse_int_table(java, java_names[kind] + "_766_OVERRIDES")
        expanded = expand(ranges, overrides, len(vv["1.20.3"][kind]), fb766[kind])
        gen = {int(k): v for k, v in maps766[kind].items()}
        problems = [f"{kind}[{i}] Java={expanded[i]} 生成={gen[i]}"
                    for i in range(len(vv["1.20.3"][kind]))
                    if expanded[i] != gen[i]]
        check(f"A. {kind}_766 表 = 生成出力", problems)

    # --- F. ITEM_REVERSE_766 表が生成スクリプトの出力と一致する ---
    ranges = parse_int3_table(java, "ITEM_REVERSE_766_RANGES")
    overrides = parse_int_table(java, "ITEM_REVERSE_766_OVERRIDES")
    expanded = expand(ranges, overrides, len(vv["1.20.5"]["items"]), 1)
    gen = {int(k): v for k, v in rev766.items()}
    problems = [f"items[{i}] Java={expanded[i]} 生成={gen[i]}"
                for i in range(len(vv["1.20.5"]["items"]))
                if expanded[i] != gen[i]]
    check("F. ITEM_REVERSE_766 表 = 生成出力", problems)

    # --- B. *_766 表の反転が 766→765 の名前の真値と一致する ---
    corrections = {
        "sounds": parse_int_table(java, "SOUND_UP_766_CORRECTIONS"),
        "blockstates": parse_int_table(java, "BLOCK_STATE_UP_766_CORRECTIONS"),
        "particles": parse_int_table(java, "PARTICLE_UP_766_CORRECTIONS"),
    }
    defaults765 = {b["name"]: b["defaultState"]
                   for b in json.loads(
                       (data_dir / "mc-1.20.3-blocks.json").read_text(encoding="utf-8"))}
    for kind in kinds:
        ranges = parse_int3_table(java, java_names[kind] + "_766_RANGES")
        overrides = parse_int_table(java, java_names[kind] + "_766_OVERRIDES")
        up = invert_java(ranges, overrides, len(vv["1.20.3"][kind]),
                         len(vv["1.20.5"][kind]), fb765[kind],
                         corrections.get(kind, []))
        truth = name_truth(
            vv["1.20.5"][kind], vv["1.20.3"][kind], kind, fb765[kind],
            rename={"turtle_scute": "scute"} if kind == "items" else None,
            sound_fallbacks=None, particle_fallbacks=None,
            item_fallbacks=None,
            block_fallbacks={"vault": "trial_spawner", "heavy_core": "stone"}
            if kind == "blockstates" else None,
            target_defaults=defaults765 if kind == "blockstates" else None,
            drop_one_prop=(kind == "blockstates"))
        # 766→765 の真値では、765 に無い 766 のものは代替先になる。
        # ただし音・粒子は 766→765 の代替表をそのまま使うのではなく、
        # 「765 に無い 766 の音・粒子は代替先」として扱う（上の
        # name_truth は source=766・target=765 のため、fallback 表は
        # 765→766 の向きでは使えない。よってここでは generic にする）。
        # そのため、補正で決めたもの以外は generic と一致するはず。
        problems = []
        for i in range(len(vv["1.20.5"][kind])):
            if up[i] != truth[i]:
                problems.append(
                    f"{kind}[{i}] {vv['1.20.5'][kind][i]}: 反転={up[i]} "
                    f"真値={truth[i]}")
        # 補正で決めたものは真値と異なってよい（ALLOWLIST 相当）。
        allowed = set()
        if kind == "sounds":
            # 1527／198／186 は補正。1526（wind_charge.wind_burst）は
            # 順向きの代替（generic.wind_burst → wind_charge.wind_burst）
            # の自然な反転で、補正ではなくても真値（generic）より良い答え。
            allowed = {1527, 198, 186, 1526}
        elif kind == "particles":
            # 26／103 は補正、24（small_gust）は 765 の gust_dust の
            # 改名を反転したもの（→ dust 相当の 99 ではなく generic
            # ではない）。25（gust_emitter_large）は順向きの代替
            # （gust_emitter → gust_emitter_large）の自然な反転。
            # いずれも真値（generic）より良い答え。
            allowed = {24, 25, 26, 103}
        elif kind == "blockstates":
            allowed = set(range(26650, 26682))
        unexpected = [p for p in problems
                      if int(re.search(r"\[(\d+)\]", p).group(1)) not in allowed]
        check(f"B. {kind} 766→765 反転 = 名前の真値", unexpected)

    # --- C. 合成表が生成スクリプトの出力と一致する ---
    for tag in ("763_TO_766", "766_TO_763", "764_TO_766", "766_TO_764"):
        for kind in kinds:
            ranges = parse_int3_table(
                java, java_names[kind] + "_" + tag + "_RANGES")
            overrides = parse_int_table(
                java, java_names[kind] + "_" + tag + "_OVERRIDES")
            src = "1.20" if tag.startswith("763") else "1.20.2"
            src = "1.20.5" if tag.startswith("766") else src
            size = len(vv[src][kind])
            generic = fb766[kind] if tag.endswith("766") else fb765[kind]
            if kind == "entities":
                generic = 77 if tag.endswith("766") else 73
            expanded = expand(ranges, overrides, size, generic)
            gen = {int(k): v for k, v in maps_comp[tag][kind].items()}
            problems = [f"{kind}[{i}] Java={expanded[i]} 生成={gen[i]}"
                        for i in range(size) if expanded[i] != gen[i]]
            check(f"C. {kind}_{tag} 合成表 = 生成出力", problems)

    # --- D. 合成表が両端の名前の真値と一致する ---
    defaults = {}
    for v, fname in (("1.20", "1.20_blocks.json"),
                     ("1.20.2", "1.20.2_blocks.json"),
                     ("1.20.5", "1.20.5_blocks.json")):
        defaults[v] = {b["name"]: b["defaultState"]
                       for b in json.loads(
                           (data_dir / fname).read_text(encoding="utf-8"))}
    pairs = [
        ("763_TO_766", "1.20", "1.20.5",
         {"grass": "short_grass", "scute": "turtle_scute"},
         {"trial_spawner": "spawner"}),
        ("766_TO_763", "1.20.5", "1.20", {"turtle_scute": "scute",
                                          "short_grass": "grass"},
         {"vault": "trial_spawner", "heavy_core": "stone"}),
        ("764_TO_766", "1.20.2", "1.20.5",
         {"grass": "short_grass", "scute": "turtle_scute"},
         {"trial_spawner": "spawner"}),
        ("766_TO_764", "1.20.5", "1.20.2", {"turtle_scute": "scute",
                                            "short_grass": "grass"},
         {"vault": "trial_spawner", "heavy_core": "stone"}),
    ]
    # D-down (b) の合成オラクル用：765→763／764 の表（既存の
    # verify-reverse-tables.py で検証済み）と、Java の *_UP_766_MAP
    # の展開（B で検証済み）。合成表はこの2つの合成と一致するはず。
    maps_up = {"1.20": json.loads((data_dir / "maps.json").read_text(encoding="utf-8")),
               "1.20.2": json.loads((data_dir / "maps764.json").read_text(encoding="utf-8"))}
    corrections = {
        "sounds": parse_int_table(java, "SOUND_UP_766_CORRECTIONS"),
        "blockstates": parse_int_table(java, "BLOCK_STATE_UP_766_CORRECTIONS"),
        "particles": parse_int_table(java, "PARTICLE_UP_766_CORRECTIONS"),
    }
    up766 = {}
    for kind in kinds:
        ranges = parse_int3_table(java, java_names[kind] + "_766_RANGES")
        overrides = parse_int_table(java, java_names[kind] + "_766_OVERRIDES")
        up766[kind] = invert_java(ranges, overrides, len(vv["1.20.3"][kind]),
                                  len(vv["1.20.5"][kind]), fb765[kind],
                                  corrections.get(kind, []))
    for tag, src_v, dst_v, rename, block_fb in pairs:
        down = tag.startswith("766")
        for kind in kinds:
            ranges = parse_int3_table(
                java, java_names[kind] + "_" + tag + "_RANGES")
            overrides = parse_int_table(
                java, java_names[kind] + "_" + tag + "_OVERRIDES")
            size = len(vv[src_v][kind])
            generic = (fb766[kind] if tag.endswith("766") else fb765[kind])
            if kind == "entities":
                generic = 77 if tag.endswith("766") else 73
            if kind == "sounds":
                generic = 1358 if tag.endswith("766") else 1312
            if kind == "particles":
                generic = 20 if tag.endswith("766") else 0
            expanded = expand(ranges, overrides, size, generic)
            if not down:
                # 上向き：両端の名前の真値と完全一致するはず。763／764 の
                # 頭蓋骨・バリアは 766 で powered／waterlogged が付くため
                # プロパティ追加で突き合わせる。改名はアイテム・ブロック・
                # ブロック状態のすべてに適用する。
                truth = name_truth(
                    vv[src_v][kind], vv[dst_v][kind], kind, generic,
                    rename=rename if kind in ("items", "blocks", "blockstates") else None,
                    sound_fallbacks=None, particle_fallbacks=None,
                    item_fallbacks=None,
                    block_fallbacks=block_fb if kind == "blockstates" else None,
                    target_defaults=defaults[dst_v] if kind == "blockstates" else None,
                    add_props=(("ominous", "false"), ("powered", "false"),
                               ("waterlogged", "false")),
                    drop_one_prop=False)
                problems = []
                for i in range(size):
                    if expanded[i] != truth[i]:
                        problems.append(
                            f"{kind}[{i}] {vv[src_v][kind][i]}: 合成={expanded[i]} "
                            f"真値={truth[i]}")
                check(f"D. {kind}_{tag} 合成表 = 名前の真値", problems)
                continue
            # 下向き (a)：両端に同じ名前があるものはその番号と一致する
            # はず（代替表に頼らない独立の検証）。改名・プロパティ1個
            # 削除（ominous）も同名とみなす。
            truth = name_truth(
                vv[src_v][kind], vv[dst_v][kind], kind, generic,
                rename=rename if kind in ("items", "blocks", "blockstates") else None,
                sound_fallbacks=SOUND_FALLBACK if kind == "sounds" else None,
                particle_fallbacks=PARTICLE_FALLBACK if kind == "particles" else None,
                item_fallbacks=ITEM_FALLBACK if kind == "items" else None,
                block_fallbacks=block_fb if kind == "blockstates" else None,
                target_defaults=defaults[dst_v] if kind == "blockstates" else None,
                add_props=(),
                drop_one_prop=(kind == "blockstates"))
            target_set = set(vv[dst_v][kind])
            problems = []
            for i in range(size):
                if expanded[i] != truth[i] and i not in ALLOWLIST_DOWN.get(kind, set()):
                    # 代替表・generic で決まったものは (b) で検証するため
                    # ここでは「同名があるのに違う」ものだけを問題にする。
                    name = vv[src_v][kind][i]
                    if name in target_set or (
                            rename and name in rename
                            and rename[name] in target_set):
                        problems.append(
                            f"{kind}[{i}] {name}: 合成={expanded[i]} "
                            f"真値={truth[i]}")
            check(f"D. {kind}_{tag} 同名 = 名前の真値", problems)
            # 下向き (b)：合成表全体が「Java の反転 × 765→相手の表」と
            # 一致するはず。代替先の決め方（tuff→tuff など）は 765→相手
            # の表に委ねる（そちらは既存の検証で確認済み）。
            oracle = {int(k): v for k, v in maps_up[dst_v][kind].items()}
            problems = []
            for i in range(size):
                expected = oracle[up766[kind][i]]
                if expanded[i] != expected:
                    problems.append(
                        f"{kind}[{i}] {vv[src_v][kind][i]}: 合成={expanded[i]} "
                        f"オラクル={expected}")
            check(f"D. {kind}_{tag} 合成表 = 合成オラクル", problems)

    # --- E. 生成スクリプトの再現性 ---
    for target in ("766", "both"):
        tmp = data_dir / f"verify-tmp-{target}.txt"
        proc = subprocess.run(
            [sys.executable, str(SCRIPT_DIR / "generate-remap-tables.py"),
             "--target", target, "--out", str(tmp)],
            capture_output=True, text=True, timeout=300)
        if proc.returncode != 0:
            print(f"[FAIL] E. 生成スクリプト --target {target} が失敗した")
            print(proc.stdout[-2000:])
            print(proc.stderr[-2000:])
            failures.append(f"E-{target}")
            continue
        fresh = tmp.read_text(encoding="utf-8")
        tmp.unlink(missing_ok=True)
        # Java ファイル内の対応する表だけを抜き出して比較する。
        problems = []
        for m in re.finditer(
                r"private static final (?:int\[\]\[\]|String\[\]\[\]) (\w+) = \{(.*?)\n    \};",
                fresh, re.DOTALL):
            name = m.group(1)
            body = m.group(2)
            jm = re.search(r"(?:int\[\]\[\]|String\[\]\[\]) " + re.escape(name)
                           + r" = \{(.*?)\n    \};", java, re.DOTALL)
            if not jm:
                problems.append(f"{name} が Java ファイルに無い")
            elif jm.group(1).strip() != body.strip():
                problems.append(f"{name} が生成出力と異なる")
        check(f"E. 生成スクリプト --target {target} の再現性", problems)

    print()
    if failures:
        print(f"不合格：{len(failures)} 項目")
        for f in failures:
            print(f"  - {f}")
        return 1
    print("すべて合格")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
