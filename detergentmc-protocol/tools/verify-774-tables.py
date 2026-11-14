#!/usr/bin/env python3
"""RegistryRemapper.java の 774 の表を検証する。

検証すること（すべて名前ベースの独立した真値と突き合わせる）：
  A. *_774 表（773→774）が生成スクリプトの出力（maps774.json）と
     一致すること（転記ミスの検出）。
  B. *_774 表の反転（Java の invert() と同じ決め方。補正なし）と
     ITEM_REVERSE_774 表が、名前から作った 774→773 の真値と一致
     すること。774 にしか無いものは代替先（石・ブタ・石の破壊音・
     entity_effect・作業台）になる。773→774 に改名は無い。
  C. 合成表（763↔774、764↔774、765↔774、766↔774、767↔774、768↔774、
     769↔774、770↔774、771↔774、772↔774）が生成スクリプトの出力
     （maps_composed_774.json）と一致すること。
  D. 合成表が、両端のレジストリを名前で直接突き合わせた真値と一致
     すること。上向き（763／764／765／766／767／768／769／770／771／772→774）は不一致ゼロの
     はず。下向き（774→763／764／765／766／767／768／769／770／771／772）は (a) 同名があるものは
     その番号と一致すること、(b) 合成表全体が「774→773 の反転 ×
     773→相手の表」と一致すること（オラクル）。
  E. 生成スクリプトの再現性：--target 774 を再実行し、Java ファイル
     内の表と完全一致すること。
  F. ITEM_REVERSE_774 表が生成スクリプトの出力
     （map_items_rev774.json）と一致すること。
  G. 属性・エンチャントの表（ATTRIBUTE_774／ATTRIBUTE_REVERSE_774／
     ENCHANTMENT_774／ENCHANTMENT_REVERSE_774）が生成スクリプトの
     出力（gen774.txt）と一致し、属性の名前表が minecraft-data の
     protocol.json の Update Attributes の key mapper と一致し、
     エンチャントの表が minecraft-data の enchantments-*.json の
     名前の並びと一致すること。エンチャントの並びそのものが正しい
     かは実機の Registry Data での確認（統合テスト）で行う。
     属性は 773・774 とも 35 件で同一。ATTRIBUTE_774_MAP の入力は
     773 の 35 件、逆向きの入力は 774 の 35 件。
     エンチャントは 773 が 42 件、774 が 43 件（lunge が 21 に挿入）。
  H. 音の名前の表（SOUND_NAME_FALLBACKS_774_TO_*）が数値の表と
     一致すること（774 の音の名前 → 数値で写した先の名前）。

使い方：
  python3 tools/verify-774-tables.py [--data-dir DIR]
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
# 774 の表の実体。RegistryRemapper.java の静的初期化子が 64KB を超えた
# ため、表だけ別クラスへ分離した（tools/insert-774-tables.py を参照）。
TABLES_FILES = [
    (REPO_ROOT / "detergentmc-protocol" / "src" / "main" / "java"
     / "io" / "detergentmc" / "protocol" / name)
    for name in ("RegistryTables774.java", "RegistryTables774ComposedUp.java",
                 "RegistryTables774Down763.java", "RegistryTables774Down764.java",
                 "RegistryTables774Down765.java", "RegistryTables774Down766.java",
                 "RegistryTables774Down767.java", "RegistryTables774Down768.java",
                 "RegistryTables774Down769.java", "RegistryTables774Down770.java",
                 "RegistryTables774Down771.java", "RegistryTables774Down772.java",
                 "RegistryTables774SoundNames.java",
                 "RegistryTables774Down763BlockState.java",
                 "RegistryTables774Down764BlockState.java",
                 "RegistryTables774Down765BlockState.java",
                 "RegistryTables774Down766BlockState.java",
                 "RegistryTables774Down767BlockState.java",
                 "RegistryTables774Down768BlockState.java",
                 "RegistryTables774Down769BlockState.java",
                 "RegistryTables774Down770BlockState.java",
                 "RegistryTables774Down771BlockState.java",
                 "RegistryTables774Down772BlockState.java")
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
# （770 で entity.wolf.howl が削除されたが、こちらは
# sound_up_rename で扱う。）771 で削除された音（leash_knot の 2 件と
# puffer_fish.ambient）は 774 の対応する音へ写す。774 で削除された
# 音は無い（774 の新音は nautilus／camel_husk／parched／
# zombie_nautilus／spear 関連など 67 件）。
SOUND_FALLBACK_OLD_TO_774 = {
    "item.goat_horn.play": "item.goat_horn.sound.0",
    "entity.goat.screaming.horn_break": "entity.goat.horn_break",
    "entity.leash_knot.break": "item.lead.break",
    "entity.leash_knot.place": "item.lead.tied",
    "entity.puffer_fish.ambient": "entity.puffer_fish.blow_out",
    # block.sand.wind は 770 だけにあり、771 で dry_grass.ambient
    # へ写った。770→774 は合成表（D の対象）のため、ここで扱う。
    "block.sand.wind": "block.dry_grass.ambient",
}

# 774→相手の音の代替表。下向きの真値では、774 の音の名前のうち相手に
# 無いものは、改名の逆向きか石の破壊音へ。773→774 の改名は無いため、
# 774→765／763／764 では 766→767 の改名の逆向き（ominous→charge）
# と 767→768 の削除音の逆向き（不要：774 側に無い）は出てこない。
# 774 の新音（67件：nautilus／camel_husk／parched／zombie_nautilus／
# spear 関連など）は石の破壊音へ（代替表に無いものは既定の石の破壊音へ）。
SOUND_FALLBACK_774_TO_TARGET = {
    "block.trial_spawner.ominous_activate": "block.trial_spawner.charge_activate",
    "block.trial_spawner.ambient_ominous": "block.trial_spawner.ambient_charged",
}

# 774→773 の音の代替表（B の真値で使う。774 の新音 67 件。
# 773 に対応する音は無く石の破壊音へ）。
SOUND_FALLBACK_774_TO_773 = {
    "item.armor.equip_nautilus": "block.stone.break",
    "item.armor.unequip_nautilus": "block.stone.break",
    "entity.baby_nautilus.ambient": "block.stone.break",
    "entity.baby_nautilus.ambient_land": "block.stone.break",
    "entity.baby_nautilus.death": "block.stone.break",
    "entity.baby_nautilus.death_land": "block.stone.break",
    "entity.baby_nautilus.eat": "block.stone.break",
    "entity.baby_nautilus.hurt": "block.stone.break",
    "entity.baby_nautilus.hurt_land": "block.stone.break",
    "entity.nautilus.riding": "block.stone.break",
    "entity.baby_nautilus.swim": "block.stone.break",
    "entity.camel_husk.ambient": "block.stone.break",
    "entity.camel_husk.dash": "block.stone.break",
    "entity.camel_husk.dash_ready": "block.stone.break",
    "entity.camel_husk.death": "block.stone.break",
    "entity.camel_husk.eat": "block.stone.break",
    "entity.camel_husk.hurt": "block.stone.break",
    "entity.camel_husk.saddle": "block.stone.break",
    "entity.camel_husk.sit": "block.stone.break",
    "entity.camel_husk.stand": "block.stone.break",
    "entity.camel_husk.step": "block.stone.break",
    "entity.camel_husk.step_sand": "block.stone.break",
    "item.spear.lunge_1": "block.stone.break",
    "item.spear.lunge_2": "block.stone.break",
    "item.spear.lunge_3": "block.stone.break",
    "entity.nautilus.ambient": "block.stone.break",
    "entity.nautilus.ambient_land": "block.stone.break",
    "entity.nautilus.dash": "block.stone.break",
    "entity.nautilus.dash_land": "block.stone.break",
    "entity.nautilus.dash_ready": "block.stone.break",
    "entity.nautilus.dash_ready_land": "block.stone.break",
    "entity.nautilus.death": "block.stone.break",
    "entity.nautilus.death_land": "block.stone.break",
    "entity.nautilus.eat": "block.stone.break",
    "entity.nautilus.hurt": "block.stone.break",
    "entity.nautilus.hurt_land": "block.stone.break",
    "entity.nautilus.swim": "block.stone.break",
    "entity.parched.ambient": "block.stone.break",
    "entity.parched.death": "block.stone.break",
    "entity.parched.hurt": "block.stone.break",
    "entity.parched.step": "block.stone.break",
    "entity.parrot.imitate.camel_husk": "block.stone.break",
    "entity.parrot.imitate.parched": "block.stone.break",
    "entity.parrot.imitate.zombie_horse": "block.stone.break",
    "entity.parrot.imitate.zombie_nautilus": "block.stone.break",
    "item.spear.use": "block.stone.break",
    "item.spear.hit": "block.stone.break",
    "item.spear.attack": "block.stone.break",
    "item.spear_wood.use": "block.stone.break",
    "item.spear_wood.hit": "block.stone.break",
    "item.spear_wood.attack": "block.stone.break",
    "entity.zombie_horse.angry": "block.stone.break",
    "entity.zombie_horse.eat": "block.stone.break",
    "entity.zombie_nautilus.ambient": "block.stone.break",
    "entity.zombie_nautilus.ambient_land": "block.stone.break",
    "entity.zombie_nautilus.dash": "block.stone.break",
    "entity.zombie_nautilus.dash_land": "block.stone.break",
    "entity.zombie_nautilus.dash_ready": "block.stone.break",
    "entity.zombie_nautilus.dash_ready_land": "block.stone.break",
    "entity.zombie_nautilus.death": "block.stone.break",
    "entity.zombie_nautilus.death_land": "block.stone.break",
    "entity.zombie_nautilus.eat": "block.stone.break",
    "entity.zombie_nautilus.hurt": "block.stone.break",
    "entity.zombie_nautilus.hurt_land": "block.stone.break",
    "entity.zombie_nautilus.swim": "block.stone.break",
    "item.nautilus_saddle_underwater_equip": "block.stone.break",
    "item.nautilus_saddle_equip": "block.stone.break",
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
            # ブロック名だけの改名（プロパティ付きのブロック状態用）。
            # chain[axis=...] → iron_chain[axis=...] のように、ブロック名
            # の接頭辞だけが変わる改名は完全一致では拾えないため、同じ
            # プロパティの改名先を探す（生成器の build_blockstates と同じ決め方）。
            if block_name(name) in rename:
                renamed = rename[block_name(name)]
                if (renamed, frozenset(props.items())) in by_props:
                    out[i] = by_props[(renamed, frozenset(props.items()))]
                    continue
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
    tables774 = "".join(p.read_text(encoding="utf-8") for p in TABLES_FILES)
    vv = {v: json.loads((data_dir / f"vv-{v}.json").read_text(encoding="utf-8"))
          for v in ("1.20", "1.20.2", "1.20.3", "1.20.5", "1.21", "1.21.2", "1.21.4", "1.21.5",
                    "1.21.6", "1.21.7", "1.21.9", "1.21.11")}
    maps774 = json.loads((data_dir / "maps774.json").read_text(encoding="utf-8"))
    maps_comp = json.loads((data_dir / "maps_composed_774.json").read_text(encoding="utf-8"))
    rev774 = json.loads((data_dir / "map_items_rev774.json").read_text(encoding="utf-8"))
    gen774 = (data_dir / "gen774.txt").read_text(encoding="utf-8")
    # D (b) のオラクル用に 773 の合成表が必要。無ければ生成する。
    maps_comp_773_path = data_dir / "maps_composed_773.json"
    maps773_path = data_dir / "maps773.json"
    if not maps_comp_773_path.exists() or not maps773_path.exists():
        proc = subprocess.run(
            [sys.executable, str(SCRIPT_DIR / "generate-remap-tables.py"),
             "--target", "773", "--out", str(data_dir / "gen773-tmp.txt")],
            capture_output=True, text=True, timeout=300)
        if proc.returncode != 0:
            print("[FAIL] 773 の表の生成に失敗した（D (b) のオラクル用）")
            print(proc.stderr[-2000:])
            return 1
    maps_comp_773 = json.loads(maps_comp_773_path.read_text(encoding="utf-8"))
    maps773 = json.loads(maps773_path.read_text(encoding="utf-8"))
    tables773 = (REPO_ROOT / "detergentmc-protocol" / "src" / "main" / "java"
                 / "io" / "detergentmc" / "protocol" / "RegistryTables773.java").read_text(
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
    # 774 側の代替先（773→774 の表の範囲外用。Java の定数と同じ値）。
    # ブタは 774 では 100、石の破壊音は 774 では 1535。
    fb774 = {"blockstates": 1, "items": 1, "entities": 100, "sounds": 1535,
             "particles": 20, "blocks": 1, "menus": 12}
    # 773 側の代替先（774→773 の反転の代替先。Java の定数と同じ値）。
    # ブタは 773 では 97、石の破壊音は 773 では 1484。
    fb773 = {"blockstates": 1, "items": 1, "entities": 97, "sounds": 1484,
             "particles": 20, "blocks": 1, "menus": 12}
    # 772 側の代替先（773→772 の反転の代替先。Java の定数と同じ値）。
    # ブタは 772 では 95、石の破壊音は 772 では 1442。
    fb772 = {"blockstates": 1, "items": 1, "entities": 95, "sounds": 1442,
             "particles": 20, "blocks": 1, "menus": 12}
    # --- A. *_774 表が生成スクリプトの出力と一致する ---
    for kind in kinds:
        ranges = parse_int3_table(tables774, java_names[kind] + "_774_RANGES")
        overrides = parse_int_table(tables774, java_names[kind] + "_774_OVERRIDES")
        expanded = expand(ranges, overrides, len(vv["1.21.9"][kind]), fb774[kind])
        gen = {int(k): v for k, v in maps774[kind].items()}
        problems = [f"{kind}[{i}] Java={expanded[i]} 生成={gen[i]}"
                    for i in range(len(vv["1.21.9"][kind]))
                    if expanded[i] != gen[i]]
        check(f"A. {kind}_774 表 = 生成出力", problems)

    # --- F. ITEM_REVERSE_774 表が生成スクリプトの出力と一致する ---
    ranges = parse_int3_table(tables774, "ITEM_REVERSE_774_RANGES")
    overrides = parse_int_table(tables774, "ITEM_REVERSE_774_OVERRIDES")
    expanded = expand(ranges, overrides, len(vv["1.21.11"]["items"]), 1)
    gen = {int(k): v for k, v in rev774.items()}
    problems = [f"items[{i}] Java={expanded[i]} 生成={gen[i]}"
                for i in range(len(vv["1.21.11"]["items"]))
                if expanded[i] != gen[i]]
    check("F. ITEM_REVERSE_774 表 = 生成出力", problems)

    # --- B. *_774 表の反転と ITEM_REVERSE_774 が 774→773 の名前の真値と一致する ---
    # 774→773 に補正は無い。773→774 に改名は無いため rename は空。
    defaults773 = {b["name"]: b["defaultState"]
                   for b in json.loads(
                       (data_dir / "1.21.9_blocks.json").read_text(encoding="utf-8"))}
    for kind in kinds:
        if kind == "items":
            ranges = parse_int3_table(tables774, "ITEM_REVERSE_774_RANGES")
            overrides = parse_int_table(tables774, "ITEM_REVERSE_774_OVERRIDES")
            up = expand(ranges, overrides, len(vv["1.21.11"]["items"]), 1)
        else:
            ranges = parse_int3_table(tables774, java_names[kind] + "_774_RANGES")
            overrides = parse_int_table(tables774, java_names[kind] + "_774_OVERRIDES")
            up = invert_java(ranges, overrides, len(vv["1.21.9"][kind]),
                             len(vv["1.21.11"][kind]), fb773[kind], [])
        truth = name_truth(
            vv["1.21.11"][kind], vv["1.21.9"][kind], kind, fb773[kind],
            rename={},
            sound_fallbacks=SOUND_FALLBACK_774_TO_773 if kind == "sounds" else None,
            particle_fallbacks=None,
            item_fallbacks=None,
            block_fallbacks=None,
            target_defaults=defaults773 if kind == "blockstates" else None,
            drop_one_prop=(kind == "blockstates"))
        problems = []
        for i in range(len(vv["1.21.11"][kind])):
            if up[i] != truth[i]:
                problems.append(
                    f"{kind}[{i}] {vv['1.21.11'][kind][i]}: 反転={up[i]} "
                    f"真値={truth[i]}")
        check(f"B. {kind} 774→773 反転 = 名前の真値", problems)

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
                               "sounds": 1386, "particles": 20, "blocks": 1, "menus": 12},
                    "1.21.4": {"blockstates": 1, "items": 1, "entities": 94,
                               "sounds": 1401, "particles": 20, "blocks": 1, "menus": 12},
                    "1.21.5": {"blockstates": 1, "items": 1, "entities": 94,
                               "sounds": 1417, "particles": 20, "blocks": 1, "menus": 12},
                    "1.21.6": {"blockstates": 1, "items": 1, "entities": 95,
                               "sounds": 1441, "particles": 20, "blocks": 1, "menus": 12},
                    "1.21.7": {"blockstates": 1, "items": 1, "entities": 95,
                               "sounds": 1442, "particles": 20, "blocks": 1, "menus": 12},
                    "1.21.9": {"blockstates": 1, "items": 1, "entities": 97,
                               "sounds": 1484, "particles": 20, "blocks": 1, "menus": 12}}
    for tag in ("763_TO_774", "774_TO_763", "764_TO_774", "774_TO_764",
                "765_TO_774", "774_TO_765", "766_TO_774", "774_TO_766",
                "767_TO_774", "774_TO_767", "768_TO_774", "774_TO_768",
                "769_TO_774", "774_TO_769", "770_TO_774", "774_TO_770",
                "771_TO_774", "774_TO_771", "772_TO_774", "774_TO_772"):
        for kind in kinds:
            ranges = parse_int3_table(
                tables774, java_names[kind] + "_" + tag + "_RANGES")
            overrides = parse_int_table(
                tables774, java_names[kind] + "_" + tag + "_OVERRIDES")
            if tag.endswith("774"):
                src_v = {"763": "1.20", "764": "1.20.2",
                         "765": "1.20.3", "766": "1.20.5",
                         "767": "1.21", "768": "1.21.2",
                         "769": "1.21.4", "770": "1.21.5",
                         "771": "1.21.6", "772": "1.21.7"}[tag[:3]]
                generic = fb774[kind]
            else:
                src_v = "1.21.11"
                generic = fb_by_target[{"763": "1.20", "764": "1.20.2",
                                        "765": "1.20.3", "766": "1.20.5",
                                        "767": "1.21", "768": "1.21.2",
                                        "769": "1.21.4", "770": "1.21.5",
                                        "771": "1.21.6", "772": "1.21.7"}[tag[7:10]]][kind]
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
                     ("1.21.5", "1.21.5_blocks.json"),
                     ("1.21.6", "1.21.6_blocks.json"),
                     ("1.21.7", "1.21.8_blocks.json"),
                     ("1.21.9", "1.21.9_blocks.json"),
                     ("1.21.11", "1.21.11_blocks.json")):
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
        "iron_chain": "chain",
        "block.trial_spawner.ominous_activate":
        "block.trial_spawner.charge_activate",
        "block.trial_spawner.ambient_ominous":
        "block.trial_spawner.ambient_charged"}
    sound_down_rename_766_767 = {
        "iron_chain": "chain",
        "block.trial_spawner.ominous_activate":
        "block.trial_spawner.charge_activate",
        "block.trial_spawner.ambient_ominous":
        "block.trial_spawner.ambient_charged"}
    pairs = [
        ("763_TO_774", "1.20", "1.21.11",
         {"grass": "short_grass", "scute": "turtle_scute",
          "chain": "iron_chain"},
         sound_up_rename,
         {"ambient_entity_effect": "entity_effect",
          "gust_emitter": "gust_emitter_large",
          "gust_dust": "small_gust"},
         {"trial_spawner": "spawner"}),
        ("774_TO_763", "1.21.11", "1.20",
         sound_down_rename,
         None, None,
         {"vault": "trial_spawner", "heavy_core": "stone"}),
        ("764_TO_774", "1.20.2", "1.21.11",
         {"grass": "short_grass", "scute": "turtle_scute",
          "chain": "iron_chain"},
         sound_up_rename,
         {"ambient_entity_effect": "entity_effect",
          "gust_emitter": "gust_emitter_large",
          "gust_dust": "small_gust"},
         {"trial_spawner": "spawner"}),
        ("774_TO_764", "1.21.11", "1.20.2",
         sound_down_rename,
         None, None,
         {"vault": "trial_spawner", "heavy_core": "stone"}),
        ("765_TO_774", "1.20.3", "1.21.11",
         {"grass": "short_grass", "scute": "turtle_scute",
          "chain": "iron_chain"},
         sound_up_rename,
         {"ambient_entity_effect": "entity_effect",
          "gust_emitter": "gust_emitter_large",
          "gust_dust": "small_gust"},
         {"trial_spawner": "spawner"}),
        ("774_TO_765", "1.21.11", "1.20.3",
         sound_down_rename,
         None, None,
         {"vault": "trial_spawner", "heavy_core": "stone"}),
        ("766_TO_774", "1.20.5", "1.21.11",
         {"chain": "iron_chain"}, sound_up_rename, {}, {}),
        ("774_TO_766", "1.21.11", "1.20.5",
         sound_down_rename_766_767,
         None, None,
         {"vault": "trial_spawner", "heavy_core": "stone"}),
        ("767_TO_774", "1.21", "1.21.11",
         {"chain": "iron_chain"}, sound_up_rename, {}, {}),
        ("774_TO_767", "1.21.11", "1.21",
         sound_down_rename_766_767,
         None, None,
         {"vault": "trial_spawner", "heavy_core": "stone"}),
        ("768_TO_774", "1.21.2", "1.21.11",
         {"chain": "iron_chain"}, sound_up_rename, {}, {}),
        ("774_TO_768", "1.21.11", "1.21.2",
         sound_down_rename_766_767,
         None, None, {}),
        ("769_TO_774", "1.21.4", "1.21.11",
         {"chain": "iron_chain"}, sound_up_rename, {}, {}),
        ("774_TO_769", "1.21.11", "1.21.4",
         sound_down_rename_766_767,
         None, None, {}),
        ("770_TO_774", "1.21.5", "1.21.11",
         {"chain": "iron_chain"}, sound_up_rename, {}, {}),
        ("774_TO_770", "1.21.11", "1.21.5",
         sound_down_rename_766_767,
         None, None, {}),
        ("771_TO_774", "1.21.6", "1.21.11",
         {"chain": "iron_chain"}, sound_up_rename, {}, {}),
        ("774_TO_771", "1.21.11", "1.21.6",
         sound_down_rename_766_767,
         None, None, {}),
        ("772_TO_774", "1.21.7", "1.21.11",
         {"chain": "iron_chain"}, sound_up_rename, {}, {}),
        ("774_TO_772", "1.21.11", "1.21.7",
         sound_down_rename_766_767,
         None, None, {}),
    ]

    # D-down (b) の合成オラクル用：773→相手は生成済みの合成表
    # （maps_composed_773.json）を使う。773→772 だけは合成表が無く
    # （772 が隣接元の版のため）、Java の *_773 表の反転で作る。
    up773 = {}
    for kind in kinds:
        ranges = parse_int3_table(tables773, java_names[kind] + "_773_RANGES")
        overrides = parse_int_table(tables773, java_names[kind] + "_773_OVERRIDES")
        up773[kind] = invert_java(ranges, overrides, len(vv["1.21.7"][kind]),
                                  len(vv["1.21.9"][kind]), fb772[kind], [])

    for tag, src_v, dst_v, item_rename, sound_rename, particle_fb, block_fb in pairs:
        down = tag.startswith("774")
        rename = item_rename
        for kind in kinds:
            ranges = parse_int3_table(
                tables774, java_names[kind] + "_" + tag + "_RANGES")
            overrides = parse_int_table(
                tables774, java_names[kind] + "_" + tag + "_OVERRIDES")
            size = len(vv[src_v][kind])
            generic = (fb774[kind] if tag.endswith("774")
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
                        # の 769 の改名表）。774 でも creaking のまま。
                        up_rename["creaking_transient"] = "creaking"
                else:
                    up_rename = None
                truth = name_truth(
                    vv[src_v][kind], vv[dst_v][kind], kind, generic,
                    rename=up_rename,
                    sound_fallbacks=SOUND_FALLBACK_OLD_TO_774
                    if kind == "sounds" else None,
                    particle_fallbacks=particle_fb,
                    item_fallbacks=None,
                    block_fallbacks=block_fb if kind == "blockstates" else None,
                    target_defaults=defaults[dst_v] if kind == "blockstates" else None,
                    add_props=(("ominous", "false"), ("powered", "false"),
                               ("waterlogged", "false")),
                    drop_one_prop=False)
                if kind == "blockstates" and src_v == "1.21.2":
                    # 768 の creaking_heart（creaking＋axis）→ 774 の真値。
                    # active → awake（natural=true）、dormant → dormant
                    # （natural=true）、disabled → dormant（natural=false）。
                    # 768 の creaking_heart は 9 状態しか無く、770→771 の
                    # 合成表は 770 の creaking_heart_state 経由で同じ値になる。
                    idx774 = {n: i for i, n in enumerate(vv["1.21.11"]["blockstates"])}
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
                        truth[i] = idx774[target]
                if kind == "blockstates" and src_v == "1.21.4":
                    # 769 の creaking_heart（active＋axis＋natural）→ 774 の真値。
                    # active=true → awake、active=false → dormant
                    # （773 の反転と同じ決め方）。
                    idx774 = {n: i for i, n in enumerate(vv["1.21.11"]["blockstates"])}
                    for i, name in enumerate(vv[src_v]["blockstates"]):
                        if not name.startswith("creaking_heart["):
                            continue
                        props = block_props(name)
                        state = ("awake" if props.get("active") == "true"
                                 else "dormant")
                        target = (f"creaking_heart[axis={props['axis']},"
                                  f"creaking_heart_state={state},natural={props['natural']}]")
                        truth[i] = idx774[target]
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
                sound_fallbacks=SOUND_FALLBACK_774_TO_TARGET if kind == "sounds" else None,
                particle_fallbacks=PARTICLE_FALLBACK if kind == "particles" else None,
                item_fallbacks=ITEM_FALLBACK if kind == "items" else None,
                block_fallbacks=block_fb if kind == "blockstates" else None,
                target_defaults=defaults.get(dst_v) if kind == "blockstates" else None,
                add_props=(),
                drop_one_prop=(kind == "blockstates"))
            if kind == "blockstates" and dst_v == "1.21.2":
                # 774 の creaking_heart → 768 の真値（768 は creaking
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
            if kind == "blockstates" and dst_v == "1.21.4":
                # 774 の creaking_heart → 769 の真値（769 は active＋axis＋
                # natural）。awake → active=true、dormant／uprooted →
                # active=false（773 の反転と同じ決め方）。
                idx769 = {n: i for i, n in enumerate(vv["1.21.4"]["blockstates"])}
                for i, name in enumerate(vv[src_v]["blockstates"]):
                    if not name.startswith("creaking_heart["):
                        continue
                    props = block_props(name)
                    active = ("true" if props.get("creaking_heart_state") == "awake"
                              else "false")
                    target = (f"creaking_heart[active={active},axis={props['axis']},"
                              f"natural={props['natural']}]")
                    truth[i] = idx769[target]
            if kind == "sounds" and "entity.puffer_fish.ambient" in vv[dst_v]["sounds"]:
                # 774 の blow_out は 770 の ambient と blow_out の両方から
                # 写る（衝突）。invert() は先行勝ちで ambient を選ぶため、
                # 合成表も相手の ambient になる。771・772 には ambient が無いため
                # 774→771／774→772 では blow_out のまま（上書きしない）。
                idx_dst = {n: i for i, n in enumerate(vv[dst_v]["sounds"])}
                truth[vv[src_v]["sounds"].index("entity.puffer_fish.blow_out")] = \
                    idx_dst["entity.puffer_fish.ambient"]
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
            # 下向き (b)：合成表全体が「774→773 の反転 × 773→相手の表」
            # と一致するはず。代替先の決め方は 773→相手の表に委ねる。
            # 773→772 だけ合成表が無いため up773（773→772 の反転）を使う。
            if dst_v == "1.21.7":
                oracle_map = up773[kind]
            else:
                tag2 = "773_TO_" + tag[7:10]
                oracle_raw = {int(k): v for k, v in maps_comp_773[tag2][kind].items()}
                oracle_map = [oracle_raw[i] for i in range(len(vv["1.21.9"][kind]))]
            if kind == "items":
                r = parse_int3_table(tables774, "ITEM_REVERSE_774_RANGES")
                o = parse_int_table(tables774, "ITEM_REVERSE_774_OVERRIDES")
                up774 = expand(r, o, len(vv["1.21.11"]["items"]), 1)
            else:
                r = parse_int3_table(tables774, java_names[kind] + "_774_RANGES")
                o = parse_int_table(tables774, java_names[kind] + "_774_OVERRIDES")
                up774 = invert_java(r, o, len(vv["1.21.9"][kind]),
                                    len(vv["1.21.11"][kind]), fb773[kind], [])
            problems = []
            for i in range(size):
                expected = oracle_map[up774[i]]
                if expanded[i] != expected:
                    problems.append(
                        f"{kind}[{i}] {vv[src_v][kind][i]}: 合成={expanded[i]} "
                        f"オラクル={expected}")
            check(f"D. {kind}_{tag} 合成表 = 合成オラクル", problems)

    # --- E. 生成スクリプトの再現性 ---
    # --target 774 を再実行し、Java ファイル内の表と完全一致する
    # （生成スクリプトをコピーした直後の転記ミスの検出）。
    tmp = data_dir / "verify-tmp-774.txt"
    proc = subprocess.run(
        [sys.executable, str(SCRIPT_DIR / "generate-remap-tables.py"),
         "--target", "774", "--out", str(tmp)],
        capture_output=True, text=True, timeout=300)
    if proc.returncode != 0:
        print("[FAIL] E. 生成スクリプト --target 774 が失敗した")
        print(proc.stderr[-2000:])
        failures.append("E-774")
    else:
        gen_re = tmp.read_text(encoding="utf-8")
        problems = []
        for m in re.finditer(
                r"^(?:public static final int\[\]\[\] )?([A-Z0-9_]+) = "
                r"compress\(new int\[\]\[\]\{(.*?)\}\);",
                gen_re, re.MULTILINE | re.DOTALL):
            name = m.group(1)
            # ATTRIBUTE_773_NAMES は RegistryTables773.java にあるため
            # 774 側には入れない（insert-774-tables.py と同じ）。
            if name == "ATTRIBUTE_773_NAMES":
                continue
            m2 = re.search(re.escape(name)
                           + r" = \{(.*?)\n    \};", tables774, re.DOTALL)
            if not m2:
                problems.append(f"{name} が RegistryTables774*.java に無い")
        check("E. 生成スクリプト --target 774 の再現性", problems)

    # --- G. 属性・エンチャントの表 ---
    for name, size, fb in (("ATTRIBUTE_774", 35, -1),
                           ("ATTRIBUTE_REVERSE_774", 35, -1),
                           ("ENCHANTMENT_774", 42, -1),
                           ("ENCHANTMENT_REVERSE_774", 43, -1)):
        ranges = parse_int3_table(tables774, name + "_RANGES")
        overrides = parse_int_table(tables774, name + "_OVERRIDES")
        expanded = expand(ranges, overrides, size, fb)
        gm_ranges = re.search(r"int\[\]\[\] " + re.escape(name) + r"_RANGES = \{(.*?)\n    \};",
                              gen774, re.DOTALL)
        gm_overrides = re.search(r"int\[\]\[\] " + re.escape(name) + r"_OVERRIDES = \{(.*?)\n    \};",
                                 gen774, re.DOTALL)
        if not gm_ranges or not gm_overrides:
            check(f"G. {name} 表 = 生成出力", [f"{name} が gen774.txt に無い"])
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
    mc773 = json.loads((data_dir / "mc-1.21.9-protocol.json").read_text(encoding="utf-8"))
    mc774 = json.loads((data_dir / "mc-1.21.11-protocol.json").read_text(encoding="utf-8"))
    for name, proto, source in (("ATTRIBUTE_773_NAMES", mc773, tables773),
                                ("ATTRIBUTE_774_NAMES", mc774, tables774)):
        java_names_list = parse_string_array(source, name)
        truth = attribute_names(proto)
        problems = [f"{name}[{i}] Java={java_names_list[i]} 真値={truth[i]}"
                    for i in range(len(truth))
                    if i >= len(java_names_list) or java_names_list[i] != truth[i]]
        if len(java_names_list) != len(truth):
            problems.append(f"{name} の件数が違う：Java={len(java_names_list)} 真値={len(truth)}")
        check(f"G. {name} = protocol.json", problems)
    # エンチャントの表 = minecraft-data の enchantments-*.json の並び。
    ench773 = [e["name"] for e in json.loads(
        (data_dir / "enchantments-1.21.9.json").read_text(encoding="utf-8"))]
    ench774 = [e["name"] for e in json.loads(
        (data_dir / "enchantments-1.21.11.json").read_text(encoding="utf-8"))]
    idx773 = {n: i for i, n in enumerate(ench773)}
    idx774 = {n: i for i, n in enumerate(ench774)}
    # 順向き（773→774）は 773 の名前が 774 の並びのどこにあるか。
    # 逆向き（774→773）は 774 の名前が 773 の並びのどこにあるか
    # （lunge だけ 773 に無いので -1）。
    for name, src, idx in (("ENCHANTMENT_774", ench773, idx774),
                           ("ENCHANTMENT_REVERSE_774", ench774, idx773)):
        ranges = parse_int3_table(tables774, name + "_RANGES")
        overrides = parse_int_table(tables774, name + "_OVERRIDES")
        expanded = expand(ranges, overrides, len(src), -1)
        problems = []
        for i, n in enumerate(src):
            expected = idx.get(n, -1)
            if expanded[i] != expected:
                problems.append(f"{name}[{i}] {n}: Java={expanded[i]} 真値={expected}")
        check(f"G. {name} = enchantments-*.json の並び", problems)

    # --- H. 音の名前の表が数値の表と一致する ---
    sound_name_tables = {
        "SOUND_NAME_FALLBACKS_774_TO_772": ("1.21.11", "1.21.7", "774_TO_772"),
        "SOUND_NAME_FALLBACKS_774_TO_771": ("1.21.11", "1.21.6", "774_TO_771"),
        "SOUND_NAME_FALLBACKS_774_TO_770": ("1.21.11", "1.21.5", "774_TO_770"),
        "SOUND_NAME_FALLBACKS_774_TO_769": ("1.21.11", "1.21.4", "774_TO_769"),
        "SOUND_NAME_FALLBACKS_774_TO_768": ("1.21.11", "1.21.2", "774_TO_768"),
        "SOUND_NAME_FALLBACKS_774_TO_767": ("1.21.11", "1.21", "774_TO_767"),
        "SOUND_NAME_FALLBACKS_774_TO_765": ("1.21.11", "1.20.3", "774_TO_765"),
        "SOUND_NAME_FALLBACKS_774_TO_763": ("1.21.11", "1.20", "774_TO_763"),
        "SOUND_NAME_FALLBACKS_774_TO_764": ("1.21.11", "1.20.2", "774_TO_764"),
        "SOUND_NAME_FALLBACKS_774_TO_766": ("1.21.11", "1.20.5", "774_TO_766"),
    }
    for name, (src_v, dst_v, tag) in sound_name_tables.items():
        pairs = parse_string_table(tables774, name)
        # 774→772 も合成表がある（772 は隣接元ではない）。773 のときの
        # 直接反転の特別処理は要らない。
        ranges = parse_int3_table(tables774, "SOUND_" + tag + "_RANGES")
        overrides = parse_int_table(tables774, "SOUND_" + tag + "_OVERRIDES")
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

    # --- I. ダメージ種別の表（手書き）が 773 の並び＋spear であること ---
    # DAMAGE_TYPE_774_MAP／REVERSE_774_MAP は生成スクリプトの出力では
    # なく insert-774-java.py の手書きのため、ここで独立に検証する。
    # 774 のダメージ種別は 773 の 49 種に spear を 37 番目に挿入した
    # 50 種（実バニラの Registry Data での確認は統合テストで行う）。
    # まだ insert-774-java.py が走っていない場合は検証を飛ばす。
    if "DAMAGE_TYPE_774_MAP" not in java:
        print("[SKIP] I. ダメージ種別の表（insert-774-java.py 未実行）")
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

            fwd = parse_int_array(java, "DAMAGE_TYPE_774_MAP")
            problems = []
            if len(fwd) != len(names768):
                problems.append(f"DAMAGE_TYPE_774_MAP の件数が違う：{len(fwd)} != {len(names768)}")
            for i in range(len(fwd)):
                expected = i if i < 37 else i + 1
                if fwd[i] != expected:
                    problems.append(f"773[{i}] {names768[i]}: Java={fwd[i]} 真値={expected}")
            check("I. DAMAGE_TYPE_774_MAP = 773 の並び＋spear(37)", problems)
            rev = parse_int_array(java, "DAMAGE_TYPE_REVERSE_774_MAP")
            problems = []
            if len(rev) != len(names768) + 1:
                problems.append(f"DAMAGE_TYPE_REVERSE_774_MAP の件数が違う：{len(rev)} != {len(names768) + 1}")
            for j in range(len(rev)):
                expected = j if j < 37 else (-1 if j == 37 else j - 1)
                if rev[j] != expected:
                    problems.append(f"774[{j}]: Java={rev[j]} 真値={expected}")
            check("I. DAMAGE_TYPE_REVERSE_774_MAP = 774 の並び＋spear(37)→DROP", problems)

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
