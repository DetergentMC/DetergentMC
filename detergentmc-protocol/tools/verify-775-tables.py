#!/usr/bin/env python3
"""RegistryRemapper.java の 775 の表を検証する。

検証すること（すべて名前ベースの独立した真値と突き合わせる）：
  A. *_775 表（774→775）が生成スクリプトの出力（maps775.json）と
     一致すること（転記ミスの検出）。
  B. *_775 表の反転（Java の invert() と同じ決め方。補正なし）と
     ITEM_REVERSE_775 表が、名前から作った 775→774 の真値と一致
     すること。775 にしか無いものは代替先（石・ブタ・石の破壊音・
     entity_effect・作業台）になる。774→775 に改名は無い。
  C. 合成表（763↔775、764↔775、765↔775、766↔775、767↔775、768↔775、
     769↔775、770↔775、771↔775、772↔775、773↔775）が生成スクリプトの出力
     （maps_composed_775.json）と一致すること。
  D. 合成表が、両端のレジストリを名前で直接突き合わせた真値と一致
     すること。上向き（763／764／765／766／767／768／769／770／771／772／773→775）は不一致ゼロの
     はず。下向き（775→763／764／765／766／767／768／769／770／771／772／773）は (a) 同名があるものは
     その番号と一致すること、(b) 合成表全体が「775→774 の反転 ×
     774→相手の表」と一致すること（オラクル）。
  E. 生成スクリプトの再現性：--target 775 を再実行し、Java ファイル
     内の表と完全一致すること。
  F. ITEM_REVERSE_775 表が生成スクリプトの出力
     （map_items_rev775.json）と一致すること。
  G. 属性・エンチャントの表（ATTRIBUTE_775／ATTRIBUTE_REVERSE_775／
     ENCHANTMENT_775／ENCHANTMENT_REVERSE_775）が生成スクリプトの
     出力（gen775.txt）と一致し、属性の名前表が minecraft-data の
     protocol.json の Update Attributes の key mapper と一致し、
     エンチャントの表が minecraft-data の enchantments-*.json の
     名前の並びと一致すること。エンチャントの並びそのものが正しい
     かは実機の Registry Data での確認（統合テスト）で行う。
     属性は 774・775 とも 35 件で同一。ATTRIBUTE_775_MAP の入力は
     774 の 35 件、逆向きの入力は 775 の 35 件。
     エンチャントは 774・775 とも 43 件で同一の並び。
  H. 音の名前の表（SOUND_NAME_FALLBACKS_775_TO_*）が数値の表と
     一致すること（775 の音の名前 → 数値で写した先の名前）。

使い方：
  python3 tools/verify-775-tables.py [--data-dir DIR]
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
# 775 の表の実体。RegistryRemapper.java の静的初期化子が 64KB を超えた
# ため、表だけ別クラスへ分離した（tools/insert-775-tables.py を参照）。
TABLES_FILES = [
    (REPO_ROOT / "detergentmc-protocol" / "src" / "main" / "java"
     / "io" / "detergentmc" / "protocol" / name)
    for name in ("RegistryTables775.java", "RegistryTables775ComposedUpA.java",
                 "RegistryTables775ComposedUpB.java",
                 "RegistryTables775Down763.java", "RegistryTables775Down764.java",
                 "RegistryTables775Down765.java", "RegistryTables775Down766.java",
                 "RegistryTables775Down767.java", "RegistryTables775Down768.java",
                 "RegistryTables775Down769.java", "RegistryTables775Down770.java",
                 "RegistryTables775Down771.java", "RegistryTables775Down772.java",
                 "RegistryTables775Down773.java", "RegistryTables775SoundNames.java",
                 "RegistryTables775Down763BlockState.java",
                 "RegistryTables775Down764BlockState.java",
                 "RegistryTables775Down765BlockState.java",
                 "RegistryTables775Down766BlockState.java",
                 "RegistryTables775Down767BlockState.java",
                 "RegistryTables775Down768BlockState.java",
                 "RegistryTables775Down769BlockState.java",
                 "RegistryTables775Down770BlockState.java",
                 "RegistryTables775Down771BlockState.java",
                 "RegistryTables775Down772BlockState.java",
                 "RegistryTables775Down773BlockState.java")
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
# puffer_fish.ambient）は 775 の対応する音へ写す。775 で削除された
# 音は無い（775 の新音は金のタンポポ・赤ちゃん動物の声・トランペットの
# 音符ブロック関連など 64 件）。
SOUND_FALLBACK_OLD_TO_775 = {
    "item.goat_horn.play": "item.goat_horn.sound.0",
    "entity.goat.screaming.horn_break": "entity.goat.horn_break",
    "entity.leash_knot.break": "item.lead.break",
    "entity.leash_knot.place": "item.lead.tied",
    "entity.puffer_fish.ambient": "entity.puffer_fish.blow_out",
    # block.sand.wind は 770 だけにあり、771 で dry_grass.ambient
    # へ写った。770→775 は合成表（D の対象）のため、ここで扱う。
    "block.sand.wind": "block.dry_grass.ambient",
}

# 775→相手の音の代替表。下向きの真値では、775 の音の名前のうち相手に
# 無いものは、改名の逆向きか石の破壊音へ。774→775 の改名は無いため、
# 775→765／763／764 では 766→767 の改名の逆向き（ominous→charge）
# と 767→768 の削除音の逆向き（不要：775 側に無い）は出てこない。
# 775 の新音（64件：金のタンポポ・赤ちゃん動物の声・トランペットの
# 音符ブロック関連など）は石の破壊音へ（代替表に無いものは既定の石の破壊音へ）。
SOUND_FALLBACK_775_TO_TARGET = {
    "block.trial_spawner.ominous_activate": "block.trial_spawner.charge_activate",
    "block.trial_spawner.ambient_ominous": "block.trial_spawner.ambient_charged",
}

# 775→774 の音の代替表（B の真値で使う。775 の新音 64 件。
# 774 に対応する音は無く石の破壊音へ）。
SOUND_FALLBACK_775_TO_774 = {
    "entity.baby_cat.ambient": "block.stone.break",
    "entity.baby_cat.stray_ambient": "block.stone.break",
    "entity.baby_cat.death": "block.stone.break",
    "entity.baby_cat.eat": "block.stone.break",
    "entity.baby_cat.hiss": "block.stone.break",
    "entity.baby_cat.beg_for_food": "block.stone.break",
    "entity.baby_cat.hurt": "block.stone.break",
    "entity.baby_cat.purr": "block.stone.break",
    "entity.baby_cat.purreow": "block.stone.break",
    "entity.cat_royal.ambient": "block.stone.break",
    "entity.cat_royal.stray_ambient": "block.stone.break",
    "entity.cat_royal.hiss": "block.stone.break",
    "entity.cat_royal.hurt": "block.stone.break",
    "entity.cat_royal.death": "block.stone.break",
    "entity.cat_royal.eat": "block.stone.break",
    "entity.cat_royal.beg_for_food": "block.stone.break",
    "entity.cat_royal.purr": "block.stone.break",
    "entity.cat_royal.purreow": "block.stone.break",
    "entity.baby_chicken.ambient": "block.stone.break",
    "entity.baby_chicken.death": "block.stone.break",
    "entity.baby_chicken.hurt": "block.stone.break",
    "entity.baby_chicken.step": "block.stone.break",
    "entity.chicken_picky.ambient": "block.stone.break",
    "entity.chicken_picky.hurt": "block.stone.break",
    "entity.chicken_picky.death": "block.stone.break",
    "entity.cow_moody.ambient": "block.stone.break",
    "entity.cow_moody.hurt": "block.stone.break",
    "entity.cow_moody.death": "block.stone.break",
    "entity.cow_moody.step": "block.stone.break",
    "item.golden_dandelion.use": "block.stone.break",
    "item.golden_dandelion.unuse": "block.stone.break",
    "entity.baby_horse.ambient": "block.stone.break",
    "entity.baby_horse.angry": "block.stone.break",
    "entity.baby_horse.breathe": "block.stone.break",
    "entity.baby_horse.death": "block.stone.break",
    "entity.baby_horse.eat": "block.stone.break",
    "entity.baby_horse.hurt": "block.stone.break",
    "entity.baby_horse.land": "block.stone.break",
    "entity.baby_horse.step": "block.stone.break",
    "block.note_block.trumpet": "block.stone.break",
    "block.note_block.trumpet_exposed": "block.stone.break",
    "block.note_block.trumpet_oxidized": "block.stone.break",
    "block.note_block.trumpet_weathered": "block.stone.break",
    "entity.baby_pig.step": "block.stone.break",
    "entity.baby_pig.ambient": "block.stone.break",
    "entity.baby_pig.eat": "block.stone.break",
    "entity.baby_pig.hurt": "block.stone.break",
    "entity.baby_pig.death": "block.stone.break",
    "entity.pig.eat": "block.stone.break",
    "entity.pig_mini.ambient": "block.stone.break",
    "entity.pig_mini.hurt": "block.stone.break",
    "entity.pig_mini.death": "block.stone.break",
    "entity.pig_mini.eat": "block.stone.break",
    "entity.pig_big.ambient": "block.stone.break",
    "entity.pig_big.hurt": "block.stone.break",
    "entity.pig_big.death": "block.stone.break",
    "entity.pig_big.eat": "block.stone.break",
    "entity.baby_wolf.ambient": "block.stone.break",
    "entity.baby_wolf.death": "block.stone.break",
    "entity.baby_wolf.growl": "block.stone.break",
    "entity.baby_wolf.hurt": "block.stone.break",
    "entity.baby_wolf.pant": "block.stone.break",
    "entity.baby_wolf.step": "block.stone.break",
    "entity.baby_wolf.whine": "block.stone.break",
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
    tables775 = "".join(p.read_text(encoding="utf-8") for p in TABLES_FILES)
    vv = {v: json.loads((data_dir / f"vv-{v}.json").read_text(encoding="utf-8"))
          for v in ("1.20", "1.20.2", "1.20.3", "1.20.5", "1.21", "1.21.2", "1.21.4", "1.21.5",
                    "1.21.6", "1.21.7", "1.21.9", "1.21.11", "26.1")}
    maps775 = json.loads((data_dir / "maps775.json").read_text(encoding="utf-8"))
    maps_comp = json.loads((data_dir / "maps_composed_775.json").read_text(encoding="utf-8"))
    rev775 = json.loads((data_dir / "map_items_rev775.json").read_text(encoding="utf-8"))
    gen775 = (data_dir / "gen775.txt").read_text(encoding="utf-8")
    # D (b) のオラクル用に 774 の合成表が必要。無ければ生成する。
    maps_comp_774_path = data_dir / "maps_composed_774.json"
    maps774_path = data_dir / "maps774.json"
    if not maps_comp_774_path.exists() or not maps774_path.exists():
        proc = subprocess.run(
            [sys.executable, str(SCRIPT_DIR / "generate-remap-tables.py"),
             "--target", "774", "--out", str(data_dir / "gen774-tmp.txt")],
            capture_output=True, text=True, timeout=300)
        if proc.returncode != 0:
            print("[FAIL] 774 の表の生成に失敗した（D (b) のオラクル用）")
            print(proc.stderr[-2000:])
            return 1
    maps_comp_774 = json.loads(maps_comp_774_path.read_text(encoding="utf-8"))
    maps774 = json.loads(maps774_path.read_text(encoding="utf-8"))
    tables774 = (REPO_ROOT / "detergentmc-protocol" / "src" / "main" / "java"
                 / "io" / "detergentmc" / "protocol" / "RegistryTables774.java").read_text(
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
    # 775 側の代替先（774→775 の表の範囲外用。Java の定数と同じ値）。
    # ブタは 775 では 100、石の破壊音は 775 では 1592。パーティクルは entity_effect=21。
    fb775 = {"blockstates": 1, "items": 1, "entities": 100, "sounds": 1592,
             "particles": 21, "blocks": 1, "menus": 12}
    # 774 側の代替先（775→774 の反転の代替先。Java の定数と同じ値）。
    # ブタは 774 では 100、石の破壊音は 774 では 1535。
    fb774 = {"blockstates": 1, "items": 1, "entities": 100, "sounds": 1535,
             "particles": 21, "blocks": 1, "menus": 12}
    # 773 側の代替先（774→773 の反転の代替先。Java の定数と同じ値）。
    # ブタは 773 では 97、石の破壊音は 773 では 1484。
    fb773 = {"blockstates": 1, "items": 1, "entities": 97, "sounds": 1484,
             "particles": 20, "blocks": 1, "menus": 12}
    # --- A. *_775 表が生成スクリプトの出力と一致する ---
    for kind in kinds:
        ranges = parse_int3_table(tables775, java_names[kind] + "_775_RANGES")
        overrides = parse_int_table(tables775, java_names[kind] + "_775_OVERRIDES")
        expanded = expand(ranges, overrides, len(vv["1.21.11"][kind]), fb775[kind])
        gen = {int(k): v for k, v in maps775[kind].items()}
        problems = [f"{kind}[{i}] Java={expanded[i]} 生成={gen[i]}"
                    for i in range(len(vv["1.21.11"][kind]))
                    if expanded[i] != gen[i]]
        check(f"A. {kind}_775 表 = 生成出力", problems)

    # --- F. ITEM_REVERSE_775 表が生成スクリプトの出力と一致する ---
    ranges = parse_int3_table(tables775, "ITEM_REVERSE_775_RANGES")
    overrides = parse_int_table(tables775, "ITEM_REVERSE_775_OVERRIDES")
    expanded = expand(ranges, overrides, len(vv["26.1"]["items"]), 1)
    gen = {int(k): v for k, v in rev775.items()}
    problems = [f"items[{i}] Java={expanded[i]} 生成={gen[i]}"
                for i in range(len(vv["26.1"]["items"]))
                if expanded[i] != gen[i]]
    check("F. ITEM_REVERSE_775 表 = 生成出力", problems)

    # --- B. *_775 表の反転と ITEM_REVERSE_775 が 775→774 の名前の真値と一致する ---
    # 775→774 に補正は無い。774→775 に改名は無いため rename は空。
    defaults774 = {b["name"]: b["defaultState"]
                   for b in json.loads(
                       (data_dir / "1.21.11_blocks.json").read_text(encoding="utf-8"))}
    for kind in kinds:
        if kind == "items":
            ranges = parse_int3_table(tables775, "ITEM_REVERSE_775_RANGES")
            overrides = parse_int_table(tables775, "ITEM_REVERSE_775_OVERRIDES")
            up = expand(ranges, overrides, len(vv["26.1"]["items"]), 1)
        else:
            ranges = parse_int3_table(tables775, java_names[kind] + "_775_RANGES")
            overrides = parse_int_table(tables775, java_names[kind] + "_775_OVERRIDES")
            up = invert_java(ranges, overrides, len(vv["1.21.11"][kind]),
                             len(vv["26.1"][kind]), fb774[kind], [])
        truth = name_truth(
            vv["26.1"][kind], vv["1.21.11"][kind], kind, fb774[kind],
            rename={},
            sound_fallbacks=SOUND_FALLBACK_775_TO_774 if kind == "sounds" else None,
            particle_fallbacks=None,
            item_fallbacks=None,
            block_fallbacks=None,
            target_defaults=defaults774 if kind == "blockstates" else None,
            drop_one_prop=(kind == "blockstates"))
        problems = []
        for i in range(len(vv["26.1"][kind])):
            if up[i] != truth[i]:
                problems.append(
                    f"{kind}[{i}] {vv['26.1'][kind][i]}: 反転={up[i]} "
                    f"真値={truth[i]}")
        check(f"B. {kind} 775→774 反転 = 名前の真値", problems)

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
                               "sounds": 1484, "particles": 20, "blocks": 1, "menus": 12},
                    "1.21.11": {"blockstates": 1, "items": 1, "entities": 100,
                               "sounds": 1535, "particles": 20, "blocks": 1, "menus": 12}}
    for tag in ("763_TO_775", "775_TO_763", "764_TO_775", "775_TO_764",
                "765_TO_775", "775_TO_765", "766_TO_775", "775_TO_766",
                "767_TO_775", "775_TO_767", "768_TO_775", "775_TO_768",
                "769_TO_775", "775_TO_769", "770_TO_775", "775_TO_770",
                "771_TO_775", "775_TO_771", "772_TO_775", "775_TO_772",
                "773_TO_775", "775_TO_773"):
        for kind in kinds:
            ranges = parse_int3_table(
                tables775, java_names[kind] + "_" + tag + "_RANGES")
            overrides = parse_int_table(
                tables775, java_names[kind] + "_" + tag + "_OVERRIDES")
            if tag.endswith("775"):
                src_v = {"763": "1.20", "764": "1.20.2",
                         "765": "1.20.3", "766": "1.20.5",
                         "767": "1.21", "768": "1.21.2",
                         "769": "1.21.4", "770": "1.21.5",
                         "771": "1.21.6", "772": "1.21.7",
                         "773": "1.21.9"}[tag[:3]]
                generic = fb775[kind]
            else:
                src_v = "26.1"
                generic = fb_by_target[{"763": "1.20", "764": "1.20.2",
                                        "765": "1.20.3", "766": "1.20.5",
                                        "767": "1.21", "768": "1.21.2",
                                        "769": "1.21.4", "770": "1.21.5",
                                        "771": "1.21.6", "772": "1.21.7",
                                        "773": "1.21.9"}[tag[7:10]]][kind]
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
                     ("1.21.11", "1.21.11_blocks.json"),
                     ("26.1", "26.1_blocks.json")):
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
        ("763_TO_775", "1.20", "26.1",
         {"grass": "short_grass", "scute": "turtle_scute",
          "chain": "iron_chain"},
         sound_up_rename,
         {"ambient_entity_effect": "entity_effect",
          "gust_emitter": "gust_emitter_large",
          "gust_dust": "small_gust"},
         {"trial_spawner": "spawner"}),
        ("775_TO_763", "26.1", "1.20",
         sound_down_rename,
         None, None,
         {"vault": "trial_spawner", "heavy_core": "stone"}),
        ("764_TO_775", "1.20.2", "26.1",
         {"grass": "short_grass", "scute": "turtle_scute",
          "chain": "iron_chain"},
         sound_up_rename,
         {"ambient_entity_effect": "entity_effect",
          "gust_emitter": "gust_emitter_large",
          "gust_dust": "small_gust"},
         {"trial_spawner": "spawner"}),
        ("775_TO_764", "26.1", "1.20.2",
         sound_down_rename,
         None, None,
         {"vault": "trial_spawner", "heavy_core": "stone"}),
        ("765_TO_775", "1.20.3", "26.1",
         {"grass": "short_grass", "scute": "turtle_scute",
          "chain": "iron_chain"},
         sound_up_rename,
         {"ambient_entity_effect": "entity_effect",
          "gust_emitter": "gust_emitter_large",
          "gust_dust": "small_gust"},
         {"trial_spawner": "spawner"}),
        ("775_TO_765", "26.1", "1.20.3",
         sound_down_rename,
         None, None,
         {"vault": "trial_spawner", "heavy_core": "stone"}),
        ("766_TO_775", "1.20.5", "26.1",
         {"chain": "iron_chain"}, sound_up_rename, {}, {}),
        ("775_TO_766", "26.1", "1.20.5",
         sound_down_rename_766_767,
         None, None,
         {"vault": "trial_spawner", "heavy_core": "stone"}),
        ("767_TO_775", "1.21", "26.1",
         {"chain": "iron_chain"}, sound_up_rename, {}, {}),
        ("775_TO_767", "26.1", "1.21",
         sound_down_rename_766_767,
         None, None,
         {"vault": "trial_spawner", "heavy_core": "stone"}),
        ("768_TO_775", "1.21.2", "26.1",
         {"chain": "iron_chain"}, sound_up_rename, {}, {}),
        ("775_TO_768", "26.1", "1.21.2",
         sound_down_rename_766_767,
         None, None, {}),
        ("769_TO_775", "1.21.4", "26.1",
         {"chain": "iron_chain"}, sound_up_rename, {}, {}),
        ("775_TO_769", "26.1", "1.21.4",
         sound_down_rename_766_767,
         None, None, {}),
        ("770_TO_775", "1.21.5", "26.1",
         {"chain": "iron_chain"}, sound_up_rename, {}, {}),
        ("775_TO_770", "26.1", "1.21.5",
         sound_down_rename_766_767,
         None, None, {}),
        ("771_TO_775", "1.21.6", "26.1",
         {"chain": "iron_chain"}, sound_up_rename, {}, {}),
        ("775_TO_771", "26.1", "1.21.6",
         sound_down_rename_766_767,
         None, None, {}),
        ("772_TO_775", "1.21.7", "26.1",
         {"chain": "iron_chain"}, sound_up_rename, {}, {}),
        ("775_TO_772", "26.1", "1.21.7",
         sound_down_rename_766_767,
         None, None, {}),
        ("773_TO_775", "1.21.9", "26.1",
         {"chain": "iron_chain"}, sound_up_rename, {}, {}),
        ("775_TO_773", "26.1", "1.21.9",
         sound_down_rename_766_767,
         None, None, {}),
    ]

    # D-down (b) の合成オラクル用：774→相手は生成済みの合成表
    # （maps_composed_774.json）を使う。774→773 だけは合成表が無く
    # （773 が隣接元の版のため）、Java の *_774 表の反転で作る。
    up774 = {}
    for kind in kinds:
        ranges = parse_int3_table(tables774, java_names[kind] + "_774_RANGES")
        overrides = parse_int_table(tables774, java_names[kind] + "_774_OVERRIDES")
        up774[kind] = invert_java(ranges, overrides, len(vv["1.21.9"][kind]),
                                  len(vv["1.21.11"][kind]), fb773[kind], [])

    for tag, src_v, dst_v, item_rename, sound_rename, particle_fb, block_fb in pairs:
        down = tag.startswith("775")
        rename = item_rename
        for kind in kinds:
            ranges = parse_int3_table(
                tables775, java_names[kind] + "_" + tag + "_RANGES")
            overrides = parse_int_table(
                tables775, java_names[kind] + "_" + tag + "_OVERRIDES")
            size = len(vv[src_v][kind])
            generic = (fb775[kind] if tag.endswith("775")
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
                        # の 769 の改名表）。775 でも creaking のまま。
                        up_rename["creaking_transient"] = "creaking"
                else:
                    up_rename = None
                truth = name_truth(
                    vv[src_v][kind], vv[dst_v][kind], kind, generic,
                    rename=up_rename,
                    sound_fallbacks=SOUND_FALLBACK_OLD_TO_775
                    if kind == "sounds" else None,
                    particle_fallbacks=particle_fb,
                    item_fallbacks=None,
                    block_fallbacks=block_fb if kind == "blockstates" else None,
                    target_defaults=defaults[dst_v] if kind == "blockstates" else None,
                    add_props=(("ominous", "false"), ("powered", "false"),
                               ("waterlogged", "false")),
                    drop_one_prop=False)
                if kind == "blockstates" and src_v == "1.21.2":
                    # 768 の creaking_heart（creaking＋axis）→ 775 の真値。
                    # active → awake（natural=true）、dormant → dormant
                    # （natural=true）、disabled → dormant（natural=false）。
                    # 768 の creaking_heart は 9 状態しか無く、770→771 の
                    # 合成表は 770 の creaking_heart_state 経由で同じ値になる。
                    idx775 = {n: i for i, n in enumerate(vv["26.1"]["blockstates"])}
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
                        truth[i] = idx775[target]
                if kind == "blockstates" and src_v == "1.21.4":
                    # 769 の creaking_heart（active＋axis＋natural）→ 775 の真値。
                    # active=true → awake、active=false → dormant
                    # （774 の反転と同じ決め方）。
                    idx775 = {n: i for i, n in enumerate(vv["26.1"]["blockstates"])}
                    for i, name in enumerate(vv[src_v]["blockstates"]):
                        if not name.startswith("creaking_heart["):
                            continue
                        props = block_props(name)
                        state = ("awake" if props.get("active") == "true"
                                 else "dormant")
                        target = (f"creaking_heart[axis={props['axis']},"
                                  f"creaking_heart_state={state},natural={props['natural']}]")
                        truth[i] = idx775[target]
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
                sound_fallbacks=SOUND_FALLBACK_775_TO_TARGET if kind == "sounds" else None,
                particle_fallbacks=PARTICLE_FALLBACK if kind == "particles" else None,
                item_fallbacks=ITEM_FALLBACK if kind == "items" else None,
                block_fallbacks=block_fb if kind == "blockstates" else None,
                target_defaults=defaults.get(dst_v) if kind == "blockstates" else None,
                add_props=(),
                drop_one_prop=(kind == "blockstates"))
            if kind == "blockstates" and dst_v == "1.21.2":
                # 775 の creaking_heart → 768 の真値（768 は creaking
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
                # 775 の creaking_heart → 769 の真値（769 は active＋axis＋
                # natural）。awake → active=true、dormant／uprooted →
                # active=false（774 の反転と同じ決め方）。
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
                # 775 の blow_out は 770 の ambient と blow_out の両方から
                # 写る（衝突）。invert() は先行勝ちで ambient を選ぶため、
                # 合成表も相手の ambient になる。771・772 には ambient が無いため
                # 775→771／775→772 では blow_out のまま（上書きしない）。
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
            # 下向き (b)：合成表全体が「775→774 の反転 × 774→相手の表」
            # と一致するはず。代替先の決め方は 774→相手の表に委ねる。
            # 774→773 だけ合成表が無いため up774（774→773 の反転）を使う。
            if dst_v == "1.21.9":
                oracle_map = up774[kind]
            else:
                tag2 = "774_TO_" + tag[7:10]
                oracle_raw = {int(k): v for k, v in maps_comp_774[tag2][kind].items()}
                oracle_map = [oracle_raw[i] for i in range(len(vv["1.21.11"][kind]))]
            if kind == "items":
                r = parse_int3_table(tables775, "ITEM_REVERSE_775_RANGES")
                o = parse_int_table(tables775, "ITEM_REVERSE_775_OVERRIDES")
                up775 = expand(r, o, len(vv["26.1"]["items"]), 1)
            else:
                r = parse_int3_table(tables775, java_names[kind] + "_775_RANGES")
                o = parse_int_table(tables775, java_names[kind] + "_775_OVERRIDES")
                up775 = invert_java(r, o, len(vv["1.21.11"][kind]),
                                    len(vv["26.1"][kind]), fb774[kind], [])
            problems = []
            for i in range(size):
                expected = oracle_map[up775[i]]
                if expanded[i] != expected:
                    problems.append(
                        f"{kind}[{i}] {vv[src_v][kind][i]}: 合成={expanded[i]} "
                        f"オラクル={expected}")
            check(f"D. {kind}_{tag} 合成表 = 合成オラクル", problems)

    # --- E. 生成スクリプトの再現性 ---
    # --target 775 を再実行し、Java ファイル内の表と完全一致する
    # （生成スクリプトをコピーした直後の転記ミスの検出）。
    tmp = data_dir / "verify-tmp-775.txt"
    proc = subprocess.run(
        [sys.executable, str(SCRIPT_DIR / "generate-remap-tables.py"),
         "--target", "775", "--out", str(tmp)],
        capture_output=True, text=True, timeout=300)
    if proc.returncode != 0:
        print("[FAIL] E. 生成スクリプト --target 775 が失敗した")
        print(proc.stderr[-2000:])
        failures.append("E-775")
    else:
        gen_re = tmp.read_text(encoding="utf-8")
        problems = []
        for m in re.finditer(
                r"^(?:public static final int\[\]\[\] )?([A-Z0-9_]+) = "
                r"compress\(new int\[\]\[\]\{(.*?)\}\);",
                gen_re, re.MULTILINE | re.DOTALL):
            name = m.group(1)
            # ATTRIBUTE_774_NAMES は RegistryTables774.java にあるため
            # 775 側には入れない（insert-775-tables.py と同じ）。
            if name == "ATTRIBUTE_774_NAMES":
                continue
            m2 = re.search(re.escape(name)
                           + r" = \{(.*?)\n    \};", tables775, re.DOTALL)
            if not m2:
                problems.append(f"{name} が RegistryTables775*.java に無い")
        check("E. 生成スクリプト --target 775 の再現性", problems)

    # --- G. 属性・エンチャントの表 ---
    for name, size, fb in (("ATTRIBUTE_775", 35, -1),
                           ("ATTRIBUTE_REVERSE_775", 35, -1),
                           ("ENCHANTMENT_775", 43, -1),
                           ("ENCHANTMENT_REVERSE_775", 43, -1)):
        ranges = parse_int3_table(tables775, name + "_RANGES")
        overrides = parse_int_table(tables775, name + "_OVERRIDES")
        expanded = expand(ranges, overrides, size, fb)
        gm_ranges = re.search(r"int\[\]\[\] " + re.escape(name) + r"_RANGES = \{(.*?)\n    \};",
                              gen775, re.DOTALL)
        gm_overrides = re.search(r"int\[\]\[\] " + re.escape(name) + r"_OVERRIDES = \{(.*?)\n    \};",
                                 gen775, re.DOTALL)
        if not gm_ranges or not gm_overrides:
            check(f"G. {name} 表 = 生成出力", [f"{name} が gen775.txt に無い"])
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
    mc774 = json.loads((data_dir / "mc-1.21.11-protocol.json").read_text(encoding="utf-8"))
    mc775 = json.loads((data_dir / "mc-26.1-protocol.json").read_text(encoding="utf-8"))
    for name, proto, source in (("ATTRIBUTE_774_NAMES", mc774, tables774),
                                ("ATTRIBUTE_775_NAMES", mc775, tables775)):
        java_names_list = parse_string_array(source, name)
        truth = attribute_names(proto)
        problems = [f"{name}[{i}] Java={java_names_list[i]} 真値={truth[i]}"
                    for i in range(len(truth))
                    if i >= len(java_names_list) or java_names_list[i] != truth[i]]
        if len(java_names_list) != len(truth):
            problems.append(f"{name} の件数が違う：Java={len(java_names_list)} 真値={len(truth)}")
        check(f"G. {name} = protocol.json", problems)
    # エンチャントの表 = minecraft-data の enchantments-*.json の並び。
    ench774 = [e["name"] for e in json.loads(
        (data_dir / "enchantments-1.21.11.json").read_text(encoding="utf-8"))]
    ench775 = [e["name"] for e in json.loads(
        (data_dir / "enchantments-26.1.json").read_text(encoding="utf-8"))]
    idx774 = {n: i for i, n in enumerate(ench774)}
    idx775 = {n: i for i, n in enumerate(ench775)}
    # 順向き（774→775）は 774 の名前が 775 の並びのどこにあるか。
    # 逆向き（775→774）は 775 の名前が 774 の並びのどこにあるか
    # （774・775 とも 43 件で同一の並びのため -1 は出ない）。
    for name, src, idx in (("ENCHANTMENT_775", ench774, idx775),
                           ("ENCHANTMENT_REVERSE_775", ench775, idx774)):
        ranges = parse_int3_table(tables775, name + "_RANGES")
        overrides = parse_int_table(tables775, name + "_OVERRIDES")
        expanded = expand(ranges, overrides, len(src), -1)
        problems = []
        for i, n in enumerate(src):
            expected = idx.get(n, -1)
            if expanded[i] != expected:
                problems.append(f"{name}[{i}] {n}: Java={expanded[i]} 真値={expected}")
        check(f"G. {name} = enchantments-*.json の並び", problems)

    # --- H. 音の名前の表が数値の表と一致する ---
    sound_name_tables = {
        "SOUND_NAME_FALLBACKS_775_TO_772": ("26.1", "1.21.7", "775_TO_772"),
        "SOUND_NAME_FALLBACKS_775_TO_773": ("26.1", "1.21.9", "775_TO_773"),
        "SOUND_NAME_FALLBACKS_775_TO_771": ("26.1", "1.21.6", "775_TO_771"),
        "SOUND_NAME_FALLBACKS_775_TO_770": ("26.1", "1.21.5", "775_TO_770"),
        "SOUND_NAME_FALLBACKS_775_TO_769": ("26.1", "1.21.4", "775_TO_769"),
        "SOUND_NAME_FALLBACKS_775_TO_768": ("26.1", "1.21.2", "775_TO_768"),
        "SOUND_NAME_FALLBACKS_775_TO_767": ("26.1", "1.21", "775_TO_767"),
        "SOUND_NAME_FALLBACKS_775_TO_765": ("26.1", "1.20.3", "775_TO_765"),
        "SOUND_NAME_FALLBACKS_775_TO_763": ("26.1", "1.20", "775_TO_763"),
        "SOUND_NAME_FALLBACKS_775_TO_764": ("26.1", "1.20.2", "775_TO_764"),
        "SOUND_NAME_FALLBACKS_775_TO_766": ("26.1", "1.20.5", "775_TO_766"),
    }
    for name, (src_v, dst_v, tag) in sound_name_tables.items():
        pairs = parse_string_table(tables775, name)
        # 775→773 も合成表がある（773 は隣接元ではない）。774 のときの
        # 直接反転の特別処理は要らない。
        ranges = parse_int3_table(tables775, "SOUND_" + tag + "_RANGES")
        overrides = parse_int_table(tables775, "SOUND_" + tag + "_OVERRIDES")
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

    # --- I. ダメージ種別の表（手書き）が 774 と同一（恒等写像）であること ---
    # DAMAGE_TYPE_775_MAP／REVERSE_775_MAP は生成スクリプトの出力では
    # なく insert-775-java.py の手書きのため、ここで独立に検証する。
    # 775 のダメージ種別は 774 と同一の 50 種と仮定（minecraft-data の
    # 26.1 にはダメージ種別の並びが無いため、名前での突き合わせはできない。
    # 実バニラの Registry Data での確認は統合テストで行う）。
    # まだ insert-775-java.py が走っていない場合は検証を飛ばす。
    if "DAMAGE_TYPE_775_MAP" not in java:
        print("[SKIP] I. ダメージ種別の表（insert-775-java.py 未実行）")
    else:
        def parse_int_array(text, name):
            m = re.search(r"int\[\] " + re.escape(name) + r" = \{(.*?)\n    \};",
                          text, re.DOTALL)
            if not m:
                raise SystemExit(f"Java ファイルに {name} が見つからない")
            return [int(x) for x in re.findall(r"(-?\d+)", m.group(1))]

        fwd = parse_int_array(java, "DAMAGE_TYPE_775_MAP")
        problems = []
        if len(fwd) != 50:
            problems.append(f"DAMAGE_TYPE_775_MAP の件数が違う：{len(fwd)} != 50")
        for i in range(len(fwd)):
            if fwd[i] != i:
                problems.append(f"774[{i}]: Java={fwd[i]} 真値={i}")
        check("I. DAMAGE_TYPE_775_MAP = 恒等写像（50 件）", problems)
        rev = parse_int_array(java, "DAMAGE_TYPE_REVERSE_775_MAP")
        problems = []
        if len(rev) != 50:
            problems.append(f"DAMAGE_TYPE_REVERSE_775_MAP の件数が違う：{len(rev)} != 50")
        for j in range(len(rev)):
            if rev[j] != j:
                problems.append(f"775[{j}]: Java={rev[j]} 真値={j}")
        check("I. DAMAGE_TYPE_REVERSE_775_MAP = 恒等写像（50 件）", problems)

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
