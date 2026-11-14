#!/usr/bin/env python3
"""RegistryRemapper.java の 767 の表を検証する。

検証すること（すべて名前ベースの独立した真値と突き合わせる）：
  A. *_767 表（766→767）が生成スクリプトの出力（maps767.json）と
     一致すること（転記ミスの検出）。
  B. *_767 表の反転（Java の invert() と同じ決め方。補正は無し）と
     ITEM_REVERSE_767 表が、名前から作った 767→766 の真値と一致
     すること。767 にしか無いものは代替先（石・ブタ・石の破壊音・
     entity_effect・作業台）になる。
  C. 合成表（763↔767、764↔767、765↔767）が生成スクリプトの出力
     （maps_composed_767.json）と一致すること。
  D. 合成表が、両端のレジストリを名前で直接突き合わせた真値と一致
     すること。上向き（763／764／765→767）は不一致ゼロのはず。
     下向き（767→763／764／765）は (a) 同名があるものはその番号と
     一致すること、(b) 合成表全体が「Java の反転 × 766→相手の表」
     と一致すること（オラクル）。vault 32状態だけ真値より良い答え
     になる（ALLOWLIST_DOWN）。
  E. 生成スクリプトの再現性：--target 767 を再実行し、Java ファイル
     内の表と完全一致すること。
  F. ITEM_REVERSE_767 表が生成スクリプトの出力
     （map_items_rev767.json）と一致すること。
  G. 属性・エンチャントの表（ATTRIBUTE_767／ATTRIBUTE_REVERSE_767／
     ENCHANTMENT_767／ENCHANTMENT_REVERSE_767）が生成スクリプトの
     出力（gen767.txt）と一致し、属性の名前表が minecraft-data の
     protocol.json の Update Attributes の key mapper と一致し、
     エンチャントの表が minecraft-data の enchantments-*.json の
     名前の並びと一致すること。エンチャントの並びそのものが正しい
     かは実機の Registry Data での確認（統合テスト）で行う。

使い方：
  python3 tools/verify-767-tables.py [--data-dir DIR]
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
# 767 の表の実体。RegistryRemapper.java の静的初期化子が 64KB を超えた
# ため、表だけ別クラスへ分離した（tools/split-767-tables.py を参照）。
TABLES_FILE = (REPO_ROOT / "detergentmc-protocol" / "src" / "main" / "java"
               / "io" / "detergentmc" / "protocol" / "RegistryTables767.java")


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


def parse_string_array(java, name):
    m = re.search(r"String\[\] " + re.escape(name) + r" = \{(.*?)\n    \};",
                  java, re.DOTALL)
    if not m:
        raise SystemExit(f"Java ファイルに {name} が見つからない")
    return re.findall(r'"([^"]+)"', m.group(1))


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


def block_name(state):
    return state.split("[", 1)[0]


def block_props(state):
    if "[" not in state:
        return {}
    inner = state[state.index("[") + 1:state.rindex("]")]
    return dict(kv.split("=", 1) for kv in inner.split(","))


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

# 767→相手の音の代替表。767 の音の名前のうち相手に無いものは、
# 改名の逆向き（ominous→charge）か、石の破壊音へ。
SOUND_FALLBACK_767 = {
    "block.trial_spawner.ominous_activate": "block.trial_spawner.charge_activate",
    "block.trial_spawner.ambient_ominous": "block.trial_spawner.ambient_charged",
}

# D の ALLOWLIST：合成表が直接の名前突き合わせより良い答えになるもの。
# 767→763／764 のブロック状態：vault 32状態は 765 の trial_spawner
# の既定状態（→ spawner）へ。名前の真値では trial_spawner 自体が
# 763／764 に無いため石（1）になるが、合成表の答えの方が良い。
ALLOWLIST_DOWN = {
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


def attribute_names(protocol):
    """protocol.json の Update Attributes の key mapper から、属性の
    名前を ID 順に並べた list を返す（生成スクリプトと同じ方法）。"""
    pkt = protocol["play"]["toClient"]["types"]["packet_entity_update_attributes"]
    props = pkt[1][1]["type"][1]["type"][1]
    mappings = props[0]["type"][1]["mappings"]
    return [mappings[str(i)] for i in range(len(mappings))]


def main():
    import argparse
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--data-dir", type=Path, default=DATA_DIR)
    args = parser.parse_args()
    data_dir = args.data_dir

    java = JAVA_FILE.read_text(encoding="utf-8")
    tables767 = TABLES_FILE.read_text(encoding="utf-8")
    vv = {v: json.loads((data_dir / f"vv-{v}.json").read_text(encoding="utf-8"))
          for v in ("1.20", "1.20.2", "1.20.3", "1.20.5", "1.21")}
    maps767 = json.loads((data_dir / "maps767.json").read_text(encoding="utf-8"))
    maps_comp = json.loads((data_dir / "maps_composed_767.json").read_text(encoding="utf-8"))
    rev767 = json.loads((data_dir / "map_items_rev767.json").read_text(encoding="utf-8"))
    gen767 = (data_dir / "gen767.txt").read_text(encoding="utf-8")

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
    # 767 側の代替先（766→767 の表の範囲外用。Java の定数と同じ値）。
    fb767 = {"blockstates": 1, "items": 1, "entities": 77, "sounds": 1361,
             "particles": 20, "blocks": 1, "menus": 12}
    # 766 側の代替先（767→766 の反転の代替先。Java の定数と同じ値）。
    fb766 = {"blockstates": 1, "items": 1, "entities": 77, "sounds": 1358,
             "particles": 20, "blocks": 1, "menus": 12}

    # --- A. *_767 表が生成スクリプトの出力と一致する ---
    for kind in kinds:
        ranges = parse_int3_table(tables767, java_names[kind] + "_767_RANGES")
        overrides = parse_int_table(tables767, java_names[kind] + "_767_OVERRIDES")
        expanded = expand(ranges, overrides, len(vv["1.20.5"][kind]), fb767[kind])
        gen = {int(k): v for k, v in maps767[kind].items()}
        problems = [f"{kind}[{i}] Java={expanded[i]} 生成={gen[i]}"
                    for i in range(len(vv["1.20.5"][kind]))
                    if expanded[i] != gen[i]]
        check(f"A. {kind}_767 表 = 生成出力", problems)

    # --- F. ITEM_REVERSE_767 表が生成スクリプトの出力と一致する ---
    ranges = parse_int3_table(tables767, "ITEM_REVERSE_767_RANGES")
    overrides = parse_int_table(tables767, "ITEM_REVERSE_767_OVERRIDES")
    expanded = expand(ranges, overrides, len(vv["1.21"]["items"]), 1)
    gen = {int(k): v for k, v in rev767.items()}
    problems = [f"items[{i}] Java={expanded[i]} 生成={gen[i]}"
                for i in range(len(vv["1.21"]["items"]))
                if expanded[i] != gen[i]]
    check("F. ITEM_REVERSE_767 表 = 生成出力", problems)

    # --- B. *_767 表の反転と ITEM_REVERSE_767 が 767→766 の名前の真値と一致する ---
    defaults766 = {b["name"]: b["defaultState"]
                   for b in json.loads(
                       (data_dir / "1.20.5-blocks.json").read_text(encoding="utf-8"))}
    for kind in kinds:
        if kind == "items":
            ranges = parse_int3_table(tables767, "ITEM_REVERSE_767_RANGES")
            overrides = parse_int_table(tables767, "ITEM_REVERSE_767_OVERRIDES")
            up = expand(ranges, overrides, len(vv["1.21"]["items"]), 1)
        else:
            ranges = parse_int3_table(tables767, java_names[kind] + "_767_RANGES")
            overrides = parse_int_table(tables767, java_names[kind] + "_767_OVERRIDES")
            up = invert_java(ranges, overrides, len(vv["1.20.5"][kind]),
                             len(vv["1.21"][kind]), fb766[kind], [])
        truth = name_truth(
            vv["1.21"][kind], vv["1.20.5"][kind], kind, fb766[kind],
            rename={"block.trial_spawner.ominous_activate":
                    "block.trial_spawner.charge_activate",
                    "block.trial_spawner.ambient_ominous":
                    "block.trial_spawner.ambient_charged"}
            if kind == "sounds" else None,
            sound_fallbacks=None, particle_fallbacks=None,
            item_fallbacks=None,
            block_fallbacks=None,
            target_defaults=defaults766 if kind == "blockstates" else None,
            drop_one_prop=(kind == "blockstates"))
        problems = []
        for i in range(len(vv["1.21"][kind])):
            if up[i] != truth[i]:
                problems.append(
                    f"{kind}[{i}] {vv['1.21'][kind][i]}: 反転={up[i]} "
                    f"真値={truth[i]}")
        check(f"B. {kind} 767→766 反転 = 名前の真値", problems)

    # --- C. 合成表が生成スクリプトの出力と一致する ---
    fb_by_target = {"1.20": {"blockstates": 1, "items": 1, "entities": 72,
                             "sounds": 1264, "particles": 0, "blocks": 1, "menus": 11},
                    "1.20.2": {"blockstates": 1, "items": 1, "entities": 72,
                               "sounds": 1270, "particles": 0, "blocks": 1, "menus": 11},
                    "1.20.3": {"blockstates": 1, "items": 1, "entities": 73,
                               "sounds": 1312, "particles": 0, "blocks": 1, "menus": 12}}
    for tag in ("763_TO_767", "767_TO_763", "764_TO_767", "767_TO_764",
                "765_TO_767", "767_TO_765"):
        for kind in kinds:
            ranges = parse_int3_table(
                tables767, java_names[kind] + "_" + tag + "_RANGES")
            overrides = parse_int_table(
                tables767, java_names[kind] + "_" + tag + "_OVERRIDES")
            if tag.endswith("767"):
                src_v = {"763": "1.20", "764": "1.20.2", "765": "1.20.3"}[tag[:3]]
                generic = fb767[kind]
            else:
                src_v = "1.21"
                generic = fb_by_target[{"763": "1.20", "764": "1.20.2",
                                        "765": "1.20.3"}[tag[7:10]]][kind]
            size = len(vv[src_v][kind])
            expanded = expand(ranges, overrides, size, generic)
            gen = {int(k): v for k, v in maps_comp[tag][kind].items()}
            problems = [f"{kind}[{i}] Java={expanded[i]} 生成={gen[i]}"
                        for i in range(size) if expanded[i] != gen[i]]
            check(f"C. {kind}_{tag} 合成表 = 生成出力", problems)

    # --- D. 合成表が両端の名前の真値と一致する ---
    defaults = {}
    for v, fname in (("1.20", "1.20-blocks.json"),
                     ("1.20.2", "1.20.2-blocks.json"),
                     ("1.20.3", "mc-1.20.3-blocks.json"),
                     ("1.21", "1.21.1-blocks.json")):
        defaults[v] = {b["name"]: b["defaultState"]
                       for b in json.loads(
                           (data_dir / fname).read_text(encoding="utf-8"))}
    pairs = [
        ("763_TO_767", "1.20", "1.21",
         {"grass": "short_grass", "scute": "turtle_scute"}, None, None,
         {"trial_spawner": "spawner"}),
        ("767_TO_763", "1.21", "1.20", {"turtle_scute": "scute",
                                        "short_grass": "grass"}, None, None,
         {"vault": "trial_spawner", "heavy_core": "stone"}),
        ("764_TO_767", "1.20.2", "1.21",
         {"grass": "short_grass", "scute": "turtle_scute"}, None, None,
         {"trial_spawner": "spawner"}),
        ("767_TO_764", "1.21", "1.20.2", {"turtle_scute": "scute",
                                          "short_grass": "grass"}, None, None,
         {"vault": "trial_spawner", "heavy_core": "stone"}),
        # 765→767 は 766 を経由するため、765→766 の改名・代替も真値に
        # 入れる（scute→turtle_scute、generic.wind_burst→
        # wind_charge.wind_burst、gust_emitter→gust_emitter_large、
        # gust_dust→small_gust、ambient_entity_effect→entity_effect）。
        ("765_TO_767", "1.20.3", "1.21",
         {"grass": "short_grass", "scute": "turtle_scute"},
         {"entity.generic.wind_burst": "entity.wind_charge.wind_burst",
          "block.trial_spawner.charge_activate":
          "block.trial_spawner.ominous_activate",
          "block.trial_spawner.ambient_charged":
          "block.trial_spawner.ambient_ominous"},
         {"ambient_entity_effect": "entity_effect",
          "gust_emitter": "gust_emitter_large",
          "gust_dust": "small_gust"},
         {"trial_spawner": "spawner"}),
        ("767_TO_765", "1.21", "1.20.3",
         {"block.trial_spawner.ominous_activate":
          "block.trial_spawner.charge_activate",
          "block.trial_spawner.ambient_ominous":
          "block.trial_spawner.ambient_charged"},
         None, None,
         {"vault": "trial_spawner", "heavy_core": "stone"}),
    ]
    # D-down (b) の合成オラクル用：766→763／764 は生成済みの合成表、
    # 766→765 は Java の *_766 表の反転（補正つき。B で検証済みの方法）。
    # 合成表はこの2つの合成と一致するはず。
    maps_up_766 = json.loads((data_dir / "maps_composed_766.json").read_text(encoding="utf-8"))
    up766_corrections = {
        "sounds": parse_int_table(java, "SOUND_UP_766_CORRECTIONS"),
        "particles": parse_int_table(java, "PARTICLE_UP_766_CORRECTIONS"),
        "blockstates": parse_int_table(java, "BLOCK_STATE_UP_766_CORRECTIONS"),
    }
    up766 = {}
    for kind in kinds:
        ranges = parse_int3_table(java, java_names[kind] + "_766_RANGES")
        overrides = parse_int_table(java, java_names[kind] + "_766_OVERRIDES")
        up766[kind] = invert_java(ranges, overrides, len(vv["1.20.3"][kind]),
                                  len(vv["1.20.5"][kind]), fb_by_target["1.20.3"][kind],
                                  up766_corrections.get(kind, []))
    for tag, src_v, dst_v, item_rename, sound_rename, particle_fb, block_fb in pairs:
        down = tag.startswith("767")
        # 下向きの真値では item_rename（改名の逆向き）を全種に使う
        # （767→765 の ominous→charge もここに入っている）。
        rename = item_rename
        for kind in kinds:
            ranges = parse_int3_table(
                tables767, java_names[kind] + "_" + tag + "_RANGES")
            overrides = parse_int_table(
                tables767, java_names[kind] + "_" + tag + "_OVERRIDES")
            size = len(vv[src_v][kind])
            generic = (fb767[kind] if tag.endswith("767")
                       else fb_by_target[dst_v][kind])
            expanded = expand(ranges, overrides, size, generic)
            if not down:
                # 上向きの真値では種ごとに改名・代替を分ける。765→767 は
                # 766 を経由するため、765→766 の改名・代替も入れる。
                if kind in ("items", "blocks", "blockstates"):
                    up_rename = item_rename
                elif kind == "sounds":
                    up_rename = sound_rename
                else:
                    up_rename = None
                truth = name_truth(
                    vv[src_v][kind], vv[dst_v][kind], kind, generic,
                    rename=up_rename,
                    sound_fallbacks=None, particle_fallbacks=particle_fb,
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
            truth = name_truth(
                vv[src_v][kind], vv[dst_v][kind], kind, generic,
                rename=rename if kind in ("items", "blocks", "blockstates", "sounds") else None,
                sound_fallbacks=SOUND_FALLBACK_767 if kind == "sounds" else None,
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
                    name = vv[src_v][kind][i]
                    if name in target_set or (
                            rename and name in rename
                            and rename[name] in target_set):
                        problems.append(
                            f"{kind}[{i}] {name}: 合成={expanded[i]} "
                            f"真値={truth[i]}")
            check(f"D. {kind}_{tag} 同名 = 名前の真値", problems)
            # 下向き (b)：合成表全体が「Java の反転 × 766→相手の表」と
            # 一致するはず。代替先の決め方は 766→相手の表に委ねる。
            if dst_v == "1.20.3":
                oracle_map = up766[kind]
            else:
                oracle_map = None
                # 766→763／764 は生成済みの合成表を使う。
                tag2 = "766_TO_" + tag[7:10]
                oracle_map = {int(k): v for k, v in maps_up_766[tag2][kind].items()}
                oracle_map = [oracle_map[i] for i in range(len(vv["1.20.5"][kind]))]
            # UP_767 の反転（B で検証済みのものと同じ）を求める。
            if kind == "items":
                r = parse_int3_table(tables767, "ITEM_REVERSE_767_RANGES")
                o = parse_int_table(tables767, "ITEM_REVERSE_767_OVERRIDES")
                up767 = expand(r, o, len(vv["1.21"]["items"]), 1)
            else:
                r = parse_int3_table(tables767, java_names[kind] + "_767_RANGES")
                o = parse_int_table(tables767, java_names[kind] + "_767_OVERRIDES")
                up767 = invert_java(r, o, len(vv["1.20.5"][kind]),
                                    len(vv["1.21"][kind]), fb766[kind], [])
            problems = []
            for i in range(size):
                expected = oracle_map[up767[i]]
                if expanded[i] != expected:
                    problems.append(
                        f"{kind}[{i}] {vv[src_v][kind][i]}: 合成={expanded[i]} "
                        f"オラクル={expected}")
            check(f"D. {kind}_{tag} 合成表 = 合成オラクル", problems)

    # --- E. 生成スクリプトの再現性 ---
    tmp = data_dir / "verify-tmp-767.txt"
    proc = subprocess.run(
        [sys.executable, str(SCRIPT_DIR / "generate-remap-tables.py"),
         "--target", "767", "--out", str(tmp)],
        capture_output=True, text=True, timeout=300)
    if proc.returncode != 0:
        print("[FAIL] E. 生成スクリプト --target 767 が失敗した")
        print(proc.stdout[-2000:])
        print(proc.stderr[-2000:])
        failures.append("E-767")
    else:
        fresh = tmp.read_text(encoding="utf-8")
        tmp.unlink(missing_ok=True)
        problems = []
        for m in re.finditer(
                r"private static final (?:int\[\]\[\]|String\[\]\[\]|String\[\]) (\w+) = \{(.*?)\n    \};",
                fresh, re.DOTALL):
            name = m.group(1)
            body = m.group(2)
            jm = re.search(r"(?:int\[\]\[\]|String\[\]\[\]|String\[\]) " + re.escape(name)
                           + r" = \{(.*?)\n    \};", tables767, re.DOTALL)
            if not jm:
                problems.append(f"{name} が RegistryTables767.java に無い")
            elif jm.group(1).strip() != body.strip():
                problems.append(f"{name} が生成出力と異なる")
        check("E. 生成スクリプト --target 767 の再現性", problems)

    # --- G. 属性・エンチャントの表 ---
    for name, size, fb in (("ATTRIBUTE_767", 22, -1),
                           ("ATTRIBUTE_REVERSE_767", 31, -1),
                           ("ENCHANTMENT_767", 42, -1),
                           ("ENCHANTMENT_REVERSE_767", 42, -1)):
        ranges = parse_int3_table(tables767, name + "_RANGES")
        overrides = parse_int_table(tables767, name + "_OVERRIDES")
        expanded = expand(ranges, overrides, size, fb)
        # gen767.txt の RANGES／OVERRIDES は別々の表のため、それぞれ
        # 抜き出して展開し直して比較する。
        gm_ranges = re.search(r"int\[\]\[\] " + re.escape(name) + r"_RANGES = \{(.*?)\n    \};",
                              gen767, re.DOTALL)
        gm_overrides = re.search(r"int\[\]\[\] " + re.escape(name) + r"_OVERRIDES = \{(.*?)\n    \};",
                                 gen767, re.DOTALL)
        if not gm_ranges or not gm_overrides:
            check(f"G. {name} 表 = 生成出力", [f"{name} が gen767.txt に無い"])
            continue
        gen_ranges = [tuple(map(int, t))
                      for t in re.findall(r"\{(-?\d+), (-?\d+), (-?\d+)\}", gm_ranges.group(1))]
        gen_overrides = [tuple(map(int, t))
                         for t in re.findall(r"\{(-?\d+), (-?\d+)\}", gm_overrides.group(1))]
        gen_expanded = expand(gen_ranges, gen_overrides, size, fb)
        problems = [f"{name}[{i}] Java={expanded[i]} 生成={gen_expanded[i]}"
                    for i in range(size) if expanded[i] != gen_expanded[i]]
        check(f"G. {name} 表 = 生成出力", problems)
    # 属性の名前表 = protocol.json の key mapper。
    mc766 = json.loads((data_dir / "mc-1.20.5-protocol.json").read_text(encoding="utf-8"))
    mc767 = json.loads((data_dir / "mc-1.21.1-protocol.json").read_text(encoding="utf-8"))
    for name, proto in (("ATTRIBUTE_766_NAMES", mc766),
                        ("ATTRIBUTE_767_NAMES", mc767)):
        java_names_list = parse_string_array(tables767, name)
        truth = attribute_names(proto)
        problems = [f"{name}[{i}] Java={java_names_list[i]} 真値={truth[i]}"
                    for i in range(len(truth))
                    if i >= len(java_names_list) or java_names_list[i] != truth[i]]
        if len(java_names_list) != len(truth):
            problems.append(f"{name} の件数が違う：Java={len(java_names_list)} 真値={len(truth)}")
        check(f"G. {name} = protocol.json", problems)
    # エンチャントの表 = minecraft-data の enchantments-*.json の並び。
    ench766 = [e["name"] for e in json.loads(
        (data_dir / "enchantments-1.20.5.json").read_text(encoding="utf-8"))]
    ench767 = [e["name"] for e in json.loads(
        (data_dir / "enchantments-1.21.1.json").read_text(encoding="utf-8"))]
    idx766 = {n: i for i, n in enumerate(ench766)}
    idx767 = {n: i for i, n in enumerate(ench767)}
    for name, src, dst, src_idx, dst_idx in (
            ("ENCHANTMENT_767", ench766, ench767, idx766, idx767),
            ("ENCHANTMENT_REVERSE_767", ench767, ench766, idx767, idx766)):
        ranges = parse_int3_table(tables767, name + "_RANGES")
        overrides = parse_int_table(tables767, name + "_OVERRIDES")
        expanded = expand(ranges, overrides, len(src), -1)
        problems = []
        for i, n in enumerate(src):
            expected = dst_idx.get(n, -1)
            if expanded[i] != expected:
                problems.append(f"{name}[{i}] {n}: Java={expanded[i]} 真値={expected}")
        check(f"G. {name} = enchantments-*.json の並び", problems)

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
