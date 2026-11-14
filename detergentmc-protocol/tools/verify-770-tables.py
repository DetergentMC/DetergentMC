#!/usr/bin/env python3
"""RegistryRemapper.java の 770 の表を検証する。

検証すること（すべて名前ベースの独立した真値と突き合わせる）：
  A. *_770 表（769→770）が生成スクリプトの出力（maps770.json）と
     一致すること（転記ミスの検出）。
  B. *_770 表の反転（Java の invert() と同じ決め方。補正あり）と
     ITEM_REVERSE_770 表が、名前から作った 770→769 の真値と一致
     すること。770 にしか無いものは代替先（石・ブタ・石の破壊音・
     entity_effect・作業台）になる。creaking_heart は
     creaking_heart_state から active への写像を名前の真値に別途
     上書きする。lingering_potion は potion への写像を別途上書きする。
  C. 合成表（763↔770、764↔770、765↔770、766↔770、767↔770、768↔770）が
     生成スクリプトの出力（maps_composed_770.json）と一致すること。
  D. 合成表が、両端のレジストリを名前で直接突き合わせた真値と一致
     すること。上向き（763／764／765／766／767／768→770）は不一致ゼロの
     はず。下向き（770→763／764／765／766／767／768）は (a) 同名があるものは
     その番号と一致すること、(b) 合成表全体が「770→769 の反転 ×
     769→相手の表」と一致すること（オラクル）。
  E. 生成スクリプトの再現性：--target 770 を再実行し、Java ファイル
     内の表と完全一致すること。
  F. ITEM_REVERSE_770 表が生成スクリプトの出力
     （map_items_rev770.json）と一致すること。
  G. 属性・エンチャントの表（ATTRIBUTE_770／ATTRIBUTE_REVERSE_770／
     ENCHANTMENT_770／ENCHANTMENT_REVERSE_770）が生成スクリプトの
     出力（gen770.txt）と一致し、属性の名前表が minecraft-data の
     protocol.json の Update Attributes の key mapper と一致し、
     エンチャントの表が minecraft-data の enchantments-*.json の
     名前の並びと一致すること。エンチャントの並びそのものが正しい
     かは実機の Registry Data での確認（統合テスト）で行う。
  H. 音の名前の表（SOUND_NAME_FALLBACKS_770_TO_*）が数値の表と
     一致すること（770 の音の名前 → 数値で写した先の名前）。

使い方：
  python3 tools/verify-770-tables.py [--data-dir DIR]
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
# 770 の表の実体。RegistryRemapper.java の静的初期化子が 64KB を超えた
# ため、表だけ別クラスへ分離した（tools/insert-770-tables.py を参照）。
TABLES_FILES = [
    (REPO_ROOT / "detergentmc-protocol" / "src" / "main" / "java"
     / "io" / "detergentmc" / "protocol" / name)
    for name in ("RegistryTables770.java", "RegistryTables770ComposedUp.java",
                 "RegistryTables770Down763.java", "RegistryTables770Down764.java",
                 "RegistryTables770Down765.java", "RegistryTables770Down766.java",
                 "RegistryTables770Down767.java", "RegistryTables770Down768.java")
]


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


# 767 以前の音の代替表（D 上向きの真値で使う。767 で削除された
# item.goat_horn.play／entity.goat.screaming.horn_break は 768 で
# item.goat_horn.sound.0／entity.goat.horn_break に改名された）。
# （770 ではさらに entity.wolf.howl が削除されたが、こちらは
# sound_up_rename で扱う。）
SOUND_FALLBACK_OLD_TO_770 = {
    "item.goat_horn.play": "item.goat_horn.sound.0",
    "entity.goat.screaming.horn_break": "entity.goat.horn_break",
}

# 770→相手の音の代替表。下向きの真値では、770 の音の名前のうち相手に
# 無いものは、改名の逆向きか石の破壊音へ。769→770 の改名は無いため、
# 770→765／763／764 では 766→767 の改名の逆向き（ominous→charge）
# と 767→768 の削除音の逆向き（不要：770 側に無い）は出てこない。
# 770 の新音（52件）は石の破壊音へ。
SOUND_FALLBACK_770_TO_TARGET = {
    "block.trial_spawner.ominous_activate": "block.trial_spawner.charge_activate",
    "block.trial_spawner.ambient_ominous": "block.trial_spawner.ambient_charged",
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
    tables770 = "".join(p.read_text(encoding="utf-8") for p in TABLES_FILES)
    tables767 = (REPO_ROOT / "detergentmc-protocol" / "src" / "main" / "java"
                 / "io" / "detergentmc" / "protocol" / "RegistryTables767.java").read_text(
                     encoding="utf-8")
    vv = {v: json.loads((data_dir / f"vv-{v}.json").read_text(encoding="utf-8"))
          for v in ("1.20", "1.20.2", "1.20.3", "1.20.5", "1.21", "1.21.2", "1.21.4", "1.21.5")}
    maps770 = json.loads((data_dir / "maps770.json").read_text(encoding="utf-8"))
    maps_comp = json.loads((data_dir / "maps_composed_770.json").read_text(encoding="utf-8"))
    rev770 = json.loads((data_dir / "map_items_rev770.json").read_text(encoding="utf-8"))
    gen770 = (data_dir / "gen770.txt").read_text(encoding="utf-8")
    # D (b) のオラクル用に 769 の合成表が必要。無ければ生成する。
    maps_comp_769_path = data_dir / "maps_composed_769.json"
    maps769_path = data_dir / "maps769.json"
    if not maps_comp_769_path.exists() or not maps769_path.exists():
        proc = subprocess.run(
            [sys.executable, str(SCRIPT_DIR / "generate-remap-tables.py"),
             "--target", "769", "--out", str(data_dir / "gen769-tmp.txt")],
            capture_output=True, text=True, timeout=300)
        if proc.returncode != 0:
            print("[FAIL] 769 の表の生成に失敗した（D (b) のオラクル用）")
            print(proc.stderr[-2000:])
            return 1
    maps_comp_769 = json.loads(maps_comp_769_path.read_text(encoding="utf-8"))
    maps769 = json.loads(maps769_path.read_text(encoding="utf-8"))
    tables769 = (REPO_ROOT / "detergentmc-protocol" / "src" / "main" / "java"
                 / "io" / "detergentmc" / "protocol" / "RegistryTables769.java").read_text(
                     encoding="utf-8")

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
    # 770 側の代替先（769→770 の表の範囲外用。Java の定数と同じ値）。
    # ブタは 770 では 94、石の破壊音は 770 では 1417。
    fb770 = {"blockstates": 1, "items": 1, "entities": 94, "sounds": 1417,
             "particles": 20, "blocks": 1, "menus": 12}
    # 769 側の代替先（770→769 の反転の代替先。Java の定数と同じ値）。
    # ブタは 769 では 94、石の破壊音は 769 では 1401。
    fb769 = {"blockstates": 1, "items": 1, "entities": 94, "sounds": 1401,
             "particles": 20, "blocks": 1, "menus": 12}
    # 768 側の代替先（769→768 の反転の代替先。Java の定数と同じ値）。
    # ブタは 768 では 95、石の破壊音は 768 では 1386。
    fb768 = {"blockstates": 1, "items": 1, "entities": 95, "sounds": 1386,
             "particles": 20, "blocks": 1, "menus": 12}

    # --- A. *_770 表が生成スクリプトの出力と一致する ---
    for kind in kinds:
        ranges = parse_int3_table(tables770, java_names[kind] + "_770_RANGES")
        overrides = parse_int_table(tables770, java_names[kind] + "_770_OVERRIDES")
        expanded = expand(ranges, overrides, len(vv["1.21.4"][kind]), fb770[kind])
        gen = {int(k): v for k, v in maps770[kind].items()}
        problems = [f"{kind}[{i}] Java={expanded[i]} 生成={gen[i]}"
                    for i in range(len(vv["1.21.4"][kind]))
                    if expanded[i] != gen[i]]
        check(f"A. {kind}_770 表 = 生成出力", problems)

    # --- F. ITEM_REVERSE_770 表が生成スクリプトの出力と一致する ---
    ranges = parse_int3_table(tables770, "ITEM_REVERSE_770_RANGES")
    overrides = parse_int_table(tables770, "ITEM_REVERSE_770_OVERRIDES")
    expanded = expand(ranges, overrides, len(vv["1.21.5"]["items"]), 1)
    gen = {int(k): v for k, v in rev770.items()}
    problems = [f"items[{i}] Java={expanded[i]} 生成={gen[i]}"
                for i in range(len(vv["1.21.5"]["items"]))
                if expanded[i] != gen[i]]
    check("F. ITEM_REVERSE_770 表 = 生成出力", problems)

    # --- B. *_770 表の反転と ITEM_REVERSE_770 が 770→769 の名前の真値と一致する ---
    bs_up_770_corrections = parse_int_table(
        tables770, "BLOCK_STATE_UP_770_CORRECTIONS")
    entity_up_770_corrections = parse_int_table(
        tables770, "ENTITY_UP_770_CORRECTIONS")
    defaults769 = {b["name"]: b["defaultState"]
                   for b in json.loads(
                       (data_dir / "1.21.4_blocks.json").read_text(encoding="utf-8"))}
    for kind in kinds:
        if kind == "items":
            ranges = parse_int3_table(tables770, "ITEM_REVERSE_770_RANGES")
            overrides = parse_int_table(tables770, "ITEM_REVERSE_770_OVERRIDES")
            up = expand(ranges, overrides, len(vv["1.21.5"]["items"]), 1)
        else:
            ranges = parse_int3_table(tables770, java_names[kind] + "_770_RANGES")
            overrides = parse_int_table(tables770, java_names[kind] + "_770_OVERRIDES")
            up = invert_java(ranges, overrides, len(vv["1.21.4"][kind]),
                             len(vv["1.21.5"][kind]), fb769[kind],
                             bs_up_770_corrections
                             if kind == "blockstates"
                             else entity_up_770_corrections
                             if kind == "entities" else [])
        truth = name_truth(
            vv["1.21.5"][kind], vv["1.21.4"][kind], kind, fb769[kind],
            rename=None,
            sound_fallbacks=None, particle_fallbacks=None,
            item_fallbacks=None,
            block_fallbacks=None,
            target_defaults=defaults769 if kind == "blockstates" else None,
            drop_one_prop=(kind == "blockstates"))
        if kind == "blockstates":
            # creaking_heart は 769 と 770 でプロパティが違う
            # （769: active/natural、770: creaking_heart_state/natural）。
            # 名前の真値はプロパティの意味で決める：
            # creaking_heart_state=awake → active=true、
            # dormant／uprooted → active=false（軸と natural はそのまま）。
            idx769 = {n: i for i, n in enumerate(vv["1.21.4"]["blockstates"])}
            for i, name in enumerate(vv["1.21.5"]["blockstates"]):
                if not name.startswith("creaking_heart["):
                    continue
                props = block_props(name)
                active = ("true" if props.get("creaking_heart_state") == "awake"
                          else "false")
                target = (f"creaking_heart[active={active},axis={props['axis']},"
                          f"natural={props['natural']}]")
                truth[i] = idx769[target]
        if kind == "entities":
            # potion の分割：770 の lingering_potion（100）は 769 の
            # potion（99）へ（splash_potion は invert の先行勝ちで
            # 既に potion になるため真値も同じ）。
            idx769 = {n: i for i, n in enumerate(vv["1.21.4"]["entities"])}
            truth[vv["1.21.5"]["entities"].index("lingering_potion")] = idx769["potion"]
            truth[vv["1.21.5"]["entities"].index("splash_potion")] = idx769["potion"]
        problems = []
        for i in range(len(vv["1.21.5"][kind])):
            if up[i] != truth[i]:
                problems.append(
                    f"{kind}[{i}] {vv['1.21.5'][kind][i]}: 反転={up[i]} "
                    f"真値={truth[i]}")
        check(f"B. {kind} 770→769 反転 = 名前の真値", problems)

    # --- C. 合成表が生成スクリプトの出力と一致する ---
    fb_by_target = {"1.20": {"blockstates": 1, "items": 1, "entities": 72,
                             "sounds": 1264, "particles": 0, "blocks": 1, "menus": 11},
                    "1.20.2": {"blockstates": 1, "items": 1, "entities": 72,
                               "sounds": 1270, "particles": 0, "blocks": 1, "menus": 11},
                    "1.20.3": {"blockstates": 1, "items": 1, "entities": 73,
                               "sounds": 1312, "particles": 0, "blocks": 1, "menus": 12},
                    "1.20.5": {"blockstates": 1, "items": 1, "entities": 77,
                               "sounds": 1358, "particles": 20, "blocks": 1, "menus": 12},
                    "1.21": {"blockstates": 1, "items": 1, "entities": 77,
                             "sounds": 1361, "particles": 20, "blocks": 1, "menus": 12},
                    "1.21.2": {"blockstates": 1, "items": 1, "entities": 95,
                               "sounds": 1386, "particles": 20, "blocks": 1, "menus": 12}}
    for tag in ("763_TO_770", "770_TO_763", "764_TO_770", "770_TO_764",
                "765_TO_770", "770_TO_765", "766_TO_770", "770_TO_766",
                "767_TO_770", "770_TO_767", "768_TO_770", "770_TO_768"):
        for kind in kinds:
            ranges = parse_int3_table(
                tables770, java_names[kind] + "_" + tag + "_RANGES")
            overrides = parse_int_table(
                tables770, java_names[kind] + "_" + tag + "_OVERRIDES")
            if tag.endswith("770"):
                src_v = {"763": "1.20", "764": "1.20.2",
                         "765": "1.20.3", "766": "1.20.5",
                         "767": "1.21", "768": "1.21.2"}[tag[:3]]
                generic = fb770[kind]
            else:
                src_v = "1.21.5"
                generic = fb_by_target[{"763": "1.20", "764": "1.20.2",
                                        "765": "1.20.3", "766": "1.20.5",
                                        "767": "1.21", "768": "1.21.2"}[tag[7:10]]][kind]
            size = len(vv[src_v][kind])
            expanded = expand(ranges, overrides, size, generic)
            gen = {int(k): v for k, v in maps_comp[tag][kind].items()}
            problems = [f"{kind}[{i}] Java={expanded[i]} 生成={gen[i]}"
                        for i in range(size) if expanded[i] != gen[i]]
            check(f"C. {kind}_{tag} 合成表 = 生成出力", problems)

    # --- D. 合成表が両端の名前の真値と一致する ---
    defaults = {}
    for v, fname in (("1.20", "1.20_blocks.json"),
                     ("1.20.2", "1.20.2_blocks.json"),
                     ("1.20.5", "1.20.5_blocks.json"),
                     ("1.21.5", "1.21.5_blocks.json")):
        defaults[v] = {b["name"]: b["defaultState"]
                       for b in json.loads(
                           (data_dir / fname).read_text(encoding="utf-8"))}
    # 765（1.20.3）の blocks.json は無い。trial_spawner の既定状態は
    # 766 のものと同じ番号ではないため、765 向けの真値では代替ブロック
    # の既定状態は使わず代替先（石）にする。
    sound_up_rename = {
        "entity.generic.wind_burst": "entity.wind_charge.wind_burst",
        "block.trial_spawner.charge_activate":
        "block.trial_spawner.ominous_activate",
        "block.trial_spawner.ambient_charged":
        "block.trial_spawner.ambient_ominous",
        "entity.wolf.howl": "entity.wolf.ambient"}
    sound_down_rename = {
        "turtle_scute": "scute", "short_grass": "grass",
        "block.trial_spawner.ominous_activate":
        "block.trial_spawner.charge_activate",
        "block.trial_spawner.ambient_ominous":
        "block.trial_spawner.ambient_charged"}
    sound_down_rename_766_767 = {
        "block.trial_spawner.ominous_activate":
        "block.trial_spawner.charge_activate",
        "block.trial_spawner.ambient_ominous":
        "block.trial_spawner.ambient_charged"}
    pairs = [
        ("763_TO_770", "1.20", "1.21.5",
         {"grass": "short_grass", "scute": "turtle_scute"},
         sound_up_rename,
         {"ambient_entity_effect": "entity_effect",
          "gust_emitter": "gust_emitter_large",
          "gust_dust": "small_gust"},
         {"trial_spawner": "spawner"}),
        ("770_TO_763", "1.21.5", "1.20",
         sound_down_rename,
         None, None,
         {"vault": "trial_spawner", "heavy_core": "stone"}),
        ("764_TO_770", "1.20.2", "1.21.5",
         {"grass": "short_grass", "scute": "turtle_scute"},
         sound_up_rename,
         {"ambient_entity_effect": "entity_effect",
          "gust_emitter": "gust_emitter_large",
          "gust_dust": "small_gust"},
         {"trial_spawner": "spawner"}),
        ("770_TO_764", "1.21.5", "1.20.2",
         sound_down_rename,
         None, None,
         {"vault": "trial_spawner", "heavy_core": "stone"}),
        ("765_TO_770", "1.20.3", "1.21.5",
         {"grass": "short_grass", "scute": "turtle_scute"},
         sound_up_rename,
         {"ambient_entity_effect": "entity_effect",
          "gust_emitter": "gust_emitter_large",
          "gust_dust": "small_gust"},
         {"trial_spawner": "spawner"}),
        ("770_TO_765", "1.21.5", "1.20.3",
         sound_down_rename,
         None, None,
         {"vault": "trial_spawner", "heavy_core": "stone"}),
        ("766_TO_770", "1.20.5", "1.21.5",
         {},
         sound_up_rename,
         {},
         {}),
        ("770_TO_766", "1.21.5", "1.20.5",
         sound_down_rename_766_767,
         None, None,
         {"vault": "trial_spawner", "heavy_core": "stone"}),
        ("767_TO_770", "1.21", "1.21.5",
         {},
         sound_up_rename,
         {},
         {}),
        ("770_TO_767", "1.21.5", "1.21",
         sound_down_rename_766_767,
         None, None,
         {"vault": "trial_spawner", "heavy_core": "stone"}),
        ("768_TO_770", "1.21.2", "1.21.5",
         {},
         sound_up_rename,
         {},
         {}),
        ("770_TO_768", "1.21.5", "1.21.2",
         sound_down_rename_766_767,
         None, None,
         {}),
    ]
    # D-down (b) の合成オラクル用：769→相手は生成済みの合成表
    # （maps_composed_769.json）を使う。769→768 だけは合成表が無く
    # （768 が隣接元の版のため）、Java の *_769 表の反転で作る。
    # 769 の表が無ければ生成する（上の --target 769 の再生成と同じ）。
    entity_up_769_corrections = (
        parse_int_table(tables769, "ENTITY_UP_769_CORRECTIONS")
        if "ENTITY_UP_769_CORRECTIONS" in tables769 else [])
    bs_up_769_corrections = parse_int_table(
        tables769, "BLOCK_STATE_UP_769_CORRECTIONS")
    up769 = {}
    for kind in kinds:
        ranges = parse_int3_table(tables769, java_names[kind] + "_769_RANGES")
        overrides = parse_int_table(tables769, java_names[kind] + "_769_OVERRIDES")
        up769[kind] = invert_java(ranges, overrides, len(vv["1.21.2"][kind]),
                                  len(vv["1.21.4"][kind]), fb768[kind],
                                  bs_up_769_corrections
                                  if kind == "blockstates"
                                  else entity_up_769_corrections
                                  if kind == "entities" else [])
    for tag, src_v, dst_v, item_rename, sound_rename, particle_fb, block_fb in pairs:
        down = tag.startswith("770")
        rename = item_rename
        for kind in kinds:
            ranges = parse_int3_table(
                tables770, java_names[kind] + "_" + tag + "_RANGES")
            overrides = parse_int_table(
                tables770, java_names[kind] + "_" + tag + "_OVERRIDES")
            size = len(vv[src_v][kind])
            generic = (fb770[kind] if tag.endswith("770")
                       else fb_by_target[dst_v][kind])
            expanded = expand(ranges, overrides, size, generic)
            if not down:
                if kind in ("items", "blocks", "blockstates"):
                    up_rename = item_rename
                elif kind == "sounds":
                    up_rename = sound_rename
                elif kind == "entities":
                    up_rename = {"boat": "oak_boat",
                                 "chest_boat": "oak_chest_boat",
                                 "potion": "splash_potion"}
                    if src_v == "1.21.2":
                        # 768 の creaking_transient は 769 で削除され、
                        # 生成器は creaking に落とす（generate-remap-tables.py
                        # の 769 の改名表）。770 でも creaking のまま。
                        up_rename["creaking_transient"] = "creaking"
                else:
                    up_rename = None
                truth = name_truth(
                    vv[src_v][kind], vv[dst_v][kind], kind, generic,
                    rename=up_rename,
                    sound_fallbacks=SOUND_FALLBACK_OLD_TO_770
                    if kind == "sounds" else None,
                    particle_fallbacks=particle_fb,
                    item_fallbacks=None,
                    block_fallbacks=block_fb if kind == "blockstates" else None,
                    target_defaults=defaults[dst_v] if kind == "blockstates" else None,
                    add_props=(("ominous", "false"), ("powered", "false"),
                               ("waterlogged", "false")),
                    drop_one_prop=False)
                if kind == "blockstates" and src_v == "1.21.2":
                    # 768 の creaking_heart（creaking＋axis）→ 770 の真値。
                    # active → awake（natural=true）、dormant → dormant
                    # （natural=true）、disabled → dormant（natural=false）。
                    # 768 の creaking_heart は 9 状態しか無く、769→770 の
                    # 合成表は 769 の active＋natural 経由で同じ値になる。
                    idx770 = {n: i for i, n in enumerate(vv["1.21.5"]["blockstates"])}
                    for i, name in enumerate(vv[src_v]["blockstates"]):
                        if not name.startswith("creaking_heart["):
                            continue
                        props = block_props(name)
                        creaking = props.get("creaking")
                        if creaking == "active":
                            target = (f"creaking_heart[axis={props['axis']},"
                                      "creaking_heart_state=awake,natural=true]")
                        elif creaking == "dormant":
                            target = (f"creaking_heart[axis={props['axis']},"
                                      "creaking_heart_state=dormant,natural=true]")
                        else:
                            target = (f"creaking_heart[axis={props['axis']},"
                                      "creaking_heart_state=dormant,natural=false]")
                        truth[i] = idx770[target]
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
                sound_fallbacks=SOUND_FALLBACK_770_TO_TARGET if kind == "sounds" else None,
                particle_fallbacks=PARTICLE_FALLBACK if kind == "particles" else None,
                item_fallbacks=ITEM_FALLBACK if kind == "items" else None,
                block_fallbacks=block_fb if kind == "blockstates" else None,
                target_defaults=defaults.get(dst_v) if kind == "blockstates" else None,
                add_props=(),
                drop_one_prop=(kind == "blockstates"))
            if kind == "blockstates" and dst_v == "1.21.2":
                # 770 の creaking_heart → 768 の真値（768 は creaking
                # ＋axis の旧プロパティ）。awake → active、dormant →
                # dormant、uprooted＋natural=true → dormant、
                # uprooted＋natural=false → disabled。
                idx768 = {n: i for i, n in enumerate(vv["1.21.2"]["blockstates"])}
                for i, name in enumerate(vv[src_v]["blockstates"]):
                    if not name.startswith("creaking_heart["):
                        continue
                    props = block_props(name)
                    state = props.get("creaking_heart_state")
                    if state == "awake":
                        creaking = "active"
                    elif props.get("natural") == "true":
                        creaking = "dormant"
                    else:
                        creaking = "disabled"
                    target = (f"creaking_heart[axis={props['axis']},"
                              f"creaking={creaking}]")
                    truth[i] = idx768[target]
            target_set = set(vv[dst_v][kind])
            target_blocks = {block_name(n) for n in vv[dst_v][kind]}
            problems = []
            for i in range(size):
                if expanded[i] != truth[i]:
                    name = vv[src_v][kind][i]
                    in_target = (name in target_set if kind != "blockstates"
                                 else block_name(name) in target_blocks)
                    if in_target or (rename and name in rename
                                     and rename[name] in target_set):
                        problems.append(
                            f"{kind}[{i}] {name}: 合成={expanded[i]} "
                            f"真値={truth[i]}")
            check(f"D. {kind}_{tag} 同名 = 名前の真値", problems)
            # 下向き (b)：合成表全体が「770→769 の反転 × 769→相手の表」
            # と一致するはず。代替先の決め方は 769→相手の表に委ねる。
            # 769→768 だけ合成表が無いため up769（769→768 の反転）を使う。
            if dst_v == "1.21.2":
                oracle_map = up769[kind]
            else:
                tag2 = "769_TO_" + tag[7:10]
                oracle_raw = {int(k): v for k, v in maps_comp_769[tag2][kind].items()}
                oracle_map = [oracle_raw[i] for i in range(len(vv["1.21.4"][kind]))]
            if kind == "items":
                r = parse_int3_table(tables770, "ITEM_REVERSE_770_RANGES")
                o = parse_int_table(tables770, "ITEM_REVERSE_770_OVERRIDES")
                up770 = expand(r, o, len(vv["1.21.5"]["items"]), 1)
            else:
                r = parse_int3_table(tables770, java_names[kind] + "_770_RANGES")
                o = parse_int_table(tables770, java_names[kind] + "_770_OVERRIDES")
                up770 = invert_java(r, o, len(vv["1.21.4"][kind]),
                                    len(vv["1.21.5"][kind]), fb769[kind],
                                    bs_up_770_corrections
                                    if kind == "blockstates"
                                    else entity_up_770_corrections
                                    if kind == "entities" else [])
            problems = []
            for i in range(size):
                expected = oracle_map[up770[i]]
                if expanded[i] != expected:
                    problems.append(
                        f"{kind}[{i}] {vv[src_v][kind][i]}: 合成={expanded[i]} "
                        f"オラクル={expected}")
            check(f"D. {kind}_{tag} 合成表 = 合成オラクル", problems)

    # --- E. 生成スクリプトの再現性 ---
    tmp = data_dir / "verify-tmp-770.txt"
    proc = subprocess.run(
        [sys.executable, str(SCRIPT_DIR / "generate-remap-tables.py"),
         "--target", "770", "--out", str(tmp)],
        capture_output=True, text=True, timeout=300)
    if proc.returncode != 0:
        print("[FAIL] E. 生成スクリプト --target 770 が失敗した")
        print(proc.stdout[-2000:])
        print(proc.stderr[-2000:])
        failures.append("E-770")
    else:
        fresh = tmp.read_text(encoding="utf-8")
        tmp.unlink(missing_ok=True)
        problems = []
        for m in re.finditer(
                r"private static final (?:int\[\]\[\]|String\[\]\[\]|String\[\]) (\w+) = \{(.*?)\n    \};",
                fresh, re.DOTALL):
            name = m.group(1)
            # ATTRIBUTE_769_NAMES は RegistryTables769.java にあるため
            # 770 側には入れない（insert-770-tables.py と同じ）。
            if name == "ATTRIBUTE_769_NAMES":
                continue
            body = m.group(2)
            jm = re.search(r"(?:int\[\]\[\]|String\[\]\[\]|String\[\]) " + re.escape(name)
                           + r" = \{(.*?)\n    \};", tables770, re.DOTALL)
            if not jm:
                problems.append(f"{name} が RegistryTables770*.java に無い")
            elif jm.group(1).strip() != body.strip():
                problems.append(f"{name} が生成出力と異なる")
        check("E. 生成スクリプト --target 770 の再現性", problems)

    # --- G. 属性・エンチャントの表 ---
    for name, size, fb in (("ATTRIBUTE_770", 32, -1),
                           ("ATTRIBUTE_REVERSE_770", 32, -1),
                           ("ENCHANTMENT_770", 42, -1),
                           ("ENCHANTMENT_REVERSE_770", 42, -1)):
        ranges = parse_int3_table(tables770, name + "_RANGES")
        overrides = parse_int_table(tables770, name + "_OVERRIDES")
        expanded = expand(ranges, overrides, size, fb)
        gm_ranges = re.search(r"int\[\]\[\] " + re.escape(name) + r"_RANGES = \{(.*?)\n    \};",
                              gen770, re.DOTALL)
        gm_overrides = re.search(r"int\[\]\[\] " + re.escape(name) + r"_OVERRIDES = \{(.*?)\n    \};",
                                 gen770, re.DOTALL)
        if not gm_ranges or not gm_overrides:
            check(f"G. {name} 表 = 生成出力", [f"{name} が gen770.txt に無い"])
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
    mc769 = json.loads((data_dir / "mc-1.21.4-protocol.json").read_text(encoding="utf-8"))
    mc770 = json.loads((data_dir / "mc-1.21.5-protocol.json").read_text(encoding="utf-8"))
    for name, proto, source in (("ATTRIBUTE_769_NAMES", mc769, tables769),
                                ("ATTRIBUTE_770_NAMES", mc770, tables770)):
        java_names_list = parse_string_array(source, name)
        truth = attribute_names(proto)
        problems = [f"{name}[{i}] Java={java_names_list[i]} 真値={truth[i]}"
                    for i in range(len(truth))
                    if i >= len(java_names_list) or java_names_list[i] != truth[i]]
        if len(java_names_list) != len(truth):
            problems.append(f"{name} の件数が違う：Java={len(java_names_list)} 真値={len(truth)}")
        check(f"G. {name} = protocol.json", problems)
    # エンチャントの表 = minecraft-data の enchantments-*.json の並び。
    ench770 = [e["name"] for e in json.loads(
        (data_dir / "enchantments-1.21.5.json").read_text(encoding="utf-8"))]
    idx770 = {n: i for i, n in enumerate(ench770)}
    # 1.21.5 の enchantments は dataPaths.json により pc/1.21.5 を使う
    # （1.21.1 の 42 件と同じ名前・同じ並び）。
    for name, src in (("ENCHANTMENT_770", ench770),
                      ("ENCHANTMENT_REVERSE_770", ench770)):
        ranges = parse_int3_table(tables770, name + "_RANGES")
        overrides = parse_int_table(tables770, name + "_OVERRIDES")
        expanded = expand(ranges, overrides, len(src), -1)
        problems = []
        for i, n in enumerate(src):
            expected = idx770.get(n, -1)
            if expanded[i] != expected:
                problems.append(f"{name}[{i}] {n}: Java={expanded[i]} 真値={expected}")
        check(f"G. {name} = enchantments-*.json の並び", problems)

    # --- H. 音の名前の表が数値の表と一致する ---
    sound_name_tables = {
        "SOUND_NAME_FALLBACKS_770_TO_769": ("1.21.5", "1.21.4", "770_TO_769"),
        "SOUND_NAME_FALLBACKS_770_TO_768": ("1.21.5", "1.21.2", "770_TO_768"),
        "SOUND_NAME_FALLBACKS_770_TO_767": ("1.21.5", "1.21", "770_TO_767"),
        "SOUND_NAME_FALLBACKS_770_TO_765": ("1.21.5", "1.20.3", "770_TO_765"),
        "SOUND_NAME_FALLBACKS_770_TO_763": ("1.21.5", "1.20", "770_TO_763"),
        "SOUND_NAME_FALLBACKS_770_TO_764": ("1.21.5", "1.20.2", "770_TO_764"),
        "SOUND_NAME_FALLBACKS_770_TO_766": ("1.21.5", "1.20.5", "770_TO_766"),
    }
    for name, (src_v, dst_v, tag) in sound_name_tables.items():
        pairs = parse_string_table(tables770, name)
        if tag == "770_TO_769":
            # 770→769 は合成表ではなく直接の表の反転。
            ranges = parse_int3_table(tables770, "SOUND_770_RANGES")
            overrides = parse_int_table(tables770, "SOUND_770_OVERRIDES")
            numeric = invert_java(ranges, overrides, len(vv["1.21.4"]["sounds"]),
                                  len(vv["1.21.5"]["sounds"]), 1401, [])
        else:
            ranges = parse_int3_table(tables770, "SOUND_" + tag + "_RANGES")
            overrides = parse_int_table(tables770, "SOUND_" + tag + "_OVERRIDES")
            fb = fb_by_target[dst_v]["sounds"]
            numeric = expand(ranges, overrides, len(vv[src_v]["sounds"]), fb)
        truth = {vv[src_v]["sounds"][i]: vv[dst_v]["sounds"][numeric[i]]
                 for i in range(len(vv[src_v]["sounds"]))}
        problems = []
        for src_name, dst_name in pairs:
            if src_name not in truth:
                problems.append(f"{src_name} が {src_v} の音に無い")
            elif truth[src_name] != dst_name:
                problems.append(f"{src_name}: 表={dst_name} 数値={truth[src_name]}")
        # 逆に、数値の表で代替先になった音はすべて名前の表にもあるはず。
        for src_name, dst_name in truth.items():
            if src_name != dst_name and (src_name, dst_name) not in pairs:
                problems.append(f"{src_name}→{dst_name} が名前の表に無い")
        check(f"H. {name} = 数値の表", problems)

    # --- I. ダメージ種別の表（手書き）が 769 と同じ並びであること ---
    # DAMAGE_TYPE_770_MAP／REVERSE_770_MAP は生成スクリプトの出力では
    # なく insert-770-java.py の手書きのため、ここで独立に検証する。
    # 770 のダメージ種別は 769 と同じ 49 種で、並びも同じはず
    # （実バニラの Registry Data での確認は統合テストで行う）。
    # まだ insert-770-java.py が走っていない場合は検証を飛ばす。
    if "DAMAGE_TYPE_770_MAP" not in java:
        print("[SKIP] I. ダメージ種別の表（insert-770-java.py 未実行）")
    else:
        play = (REPO_ROOT / "detergentmc-protocol" / "src" / "main" / "java"
                / "io" / "detergentmc" / "protocol" / "PlayTranslator.java").read_text(
                    encoding="utf-8")
        m769_names = re.search(
            r"private static final String\[\] DAMAGE_768_NAMES = \{(.*?)\n    \};",
            play, re.DOTALL)
        if not m769_names:
            check("I. ダメージ種別の表", ["DAMAGE_768_NAMES が PlayTranslator.java に無い"])
        else:
            names768 = re.findall(r'"([^"]+)"', m769_names.group(1))

            def parse_int_array(text, name):
                m = re.search(r"int\[\] " + re.escape(name) + r" = \{(.*?)\n    \};",
                              text, re.DOTALL)
                if not m:
                    raise SystemExit(f"Java ファイルに {name} が見つからない")
                return [int(x) for x in re.findall(r"(-?\d+)", m.group(1))]

            fwd = parse_int_array(java, "DAMAGE_TYPE_770_MAP")
            problems = []
            if len(fwd) != len(names768):
                problems.append(f"DAMAGE_TYPE_770_MAP の件数が違う：{len(fwd)} != {len(names768)}")
            for i in range(len(fwd)):
                if fwd[i] != i:
                    problems.append(f"769[{i}] {names768[i]}: Java={fwd[i]} 真値={i}")
            check("I. DAMAGE_TYPE_770_MAP = 769 と同じ並び", problems)
            rev = parse_int_array(java, "DAMAGE_TYPE_REVERSE_770_MAP")
            problems = []
            if len(rev) != len(names768):
                problems.append(f"DAMAGE_TYPE_REVERSE_770_MAP の件数が違う：{len(rev)} != {len(names768)}")
            for i in range(len(rev)):
                if rev[i] != i:
                    problems.append(f"770[{i}] {names768[i]}: Java={rev[i]} 真値={i}")
            check("I. DAMAGE_TYPE_REVERSE_770_MAP = 769 と同じ並び", problems)

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
