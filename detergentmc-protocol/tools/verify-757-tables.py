#!/usr/bin/env python3
"""RegistryTables757.java の検証（チェック A〜H、757 版）。

雛形は verify-777-tables.py。757 は最古バージョンのため合成表を
作らない（757↔763〜775 は 757→759→760→761→762→763→合成表の
隣接連鎖で到達する）。そのため 777 版のチェック C（合成表＝生成出力）
と D（合成表＝名前の真値）は無く、代わりに C は「合成表が無いこと」、
D は「反転表＝invert(順向き)＋補正」の確認になる。

チェック：
  A  隣接表 *_757（759→757）：maps757.json と比較（7 件）
  B  反転表 *_757_TO_759：名前の真値と比較（7 件）
  C  合成表が無いこと：RegistryTables757.java の表の一覧が想定の
     50 件ちょうどで、合成表（*_76X_*／*_75X_* など）が無い（1 件）
  D  反転表＝invert(順向き)＋補正：RegistryRemapper.invert() と
     同じ決め方で順向き表を反転し、maps757-corrections.json の
     補正（ブロック状態 110 件・音 20 件）を当てて名前の真値と
     一致することを確認する（7 件）。これが wiring の契約。
  E  gen-757-tables.py を再実行し、出力の全表が Java ファイルと
     一致することを確認する（1 件）
  F  ITEM_REVERSE_757：map_items_rev757.json と比較（1 件）
  G  属性・エンチャント・バイオーム・ダメージ種別：
     属性は 13 件の恒等写像で名前は registries.json の protocol_id
     順と一致、エンチャントは swift_sneak→-1 で他はシフトし順序は
     minecraft-data と一致、バイオームは biomes.json の id 順と
     一致、ダメージ種別は 762 の 42 件の恒等写像のプレースホルダ
     （11 件）
  H  音名表 SOUND_NAME_FALLBACKS_*：759→757 は SOUND_FALLBACK_757
     そのもの、757→759 は名前の真値の反転表から作ったもの
     （0 件。757 の音はすべて 759 に同名で存在する）、757 は
     759→757 と同じ（3 件）

合計：43 件（A 7＋B 7＋B' 7＋C 1＋D 7＋E 1＋F 1＋G 9＋H 3）。

使い方：リポジトリのルートで
    python3 detergentmc-protocol/tools/verify-757-tables.py

注意：順向き表（759→757）の名前の真値との直接の突き合わせは
しない。理由は代替ブロックのプロパティ関数（例：mangrove_propagule
→ oak_sapling は stage を引き継ぐ）で、名前だけの真値では
既定状態（stage=0）になってしまい、生成器の結果と食い違うため。
代替で解決したブロック状態が 1.18 に実在することは
gen-757-tables.py の監査（blockstate fallback audit：0 件の不整合）
で確認している。反転表（757→759）には代替が絡まないため、
名前の真値との突き合わせがそのまま独立した検証になる。
"""
import importlib.util
import json
import re
import subprocess
import sys
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent.parent
DATA_DIR = REPO_ROOT / "detergentmc-protocol" / "build" / "remap-data"
TOOLS_DIR = REPO_ROOT / "detergentmc-protocol" / "tools"
TABLES_FILE = (REPO_ROOT / "detergentmc-protocol" / "src" / "main" / "java"
               / "io" / "detergentmc" / "protocol" / "RegistryTables757.java")

KINDS = ["blockstates", "items", "entities", "sounds", "particles",
         "blocks", "menus"]
KIND_TO_JAVA = {"blockstates": "BLOCK_STATE", "items": "ITEM",
                "entities": "ENTITY_TYPE", "sounds": "SOUND",
                "particles": "PARTICLE", "blocks": "BLOCK", "menus": "MENU"}

# 759 側の代替先（UP_757_FALLBACKS。gen-757-tables.py と同じ値）。
# ブタは 759 では 67、石の破壊音は 759 では 1112、
# パーティクルは entity_effect=21、メニューは crafting=11。
FB759 = {"blockstates": 1, "items": 1, "entities": 67, "sounds": 1112,
         "particles": 21, "blocks": 1, "menus": 11}


def load_json(path):
    with open(path, encoding="utf-8") as f:
        return json.load(f)


def load_gen_module():
    """gen-757-tables.py をモジュールとして読み、代替表の dict を使う。"""
    spec = importlib.util.spec_from_file_location(
        "gen757", TOOLS_DIR / "gen-757-tables.py")
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    return mod


def normalize_blockstates(states):
    """ブロック状態の文字列のプロパティをソートして正規化する
    （gen-757-tables.py と同じ。chest などの 84 状態は順序だけ違う）。"""
    out = []
    for s in states:
        if "[" not in s:
            out.append(s)
            continue
        name, rest = s.split("[", 1)
        props = sorted(kv.split("=", 1) for kv in rest[:-1].split(","))
        out.append(name + "[" + ",".join(f"{k}={v}" for k, v in props) + "]")
    return out


def block_name(state):
    return state.split("[", 1)[0]


def block_props(state):
    if "[" not in state:
        return {}
    inner = state[state.index("[") + 1:state.rindex("]")]
    return dict(kv.split("=", 1) for kv in inner.split(","))


def parse_int3_table(java, name):
    m = re.search(r"int\[\]\[\] " + re.escape(name) + r" = \{(.*?)\n    \};",
                  java, re.DOTALL)
    if not m:
        raise SystemExit(f"Java ファイルに {name} が見つからない")
    return [tuple(map(int, t))
            for t in re.findall(r"\{(-?\d+), (-?\d+), (-?\d+)\}", m.group(1))]


def parse_int_table(java, name):
    m = re.search(r"int\[\]\[\] " + re.escape(name) + r" = \{(.*?)\n    \};",
                  java, re.DOTALL)
    if not m:
        raise SystemExit(f"Java ファイルに {name} が見つからない")
    return [tuple(map(int, t))
            for t in re.findall(r"\{(-?\d+), (-?\d+)\}", m.group(1))]


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
    """RANGES＋OVERRIDES を [int; size] の表に広げる。"""
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


def name_truth(source_names, target_names, kind, generic_fallback,
               rename=None, sound_fallbacks=None, particle_fallbacks=None,
               item_fallbacks=None, entity_fallbacks=None,
               block_fallbacks=None, target_defaults=None,
               add_props=(), drop_one_prop=False):
    """source の各 id → target の id の真値（名前で突き合わせる）。

    優先順：完全一致 → 改名 → プロパティ1個追加（add_props）→
    プロパティ1個削除（drop_one_prop）→ 代替表 → 代替先。
    757→759 の反転では add_props に waterlogged=false を渡す
    （757 の葉には waterlogged が無く、759 の waterlogged=false
    の葉に写る）。757 には 759 に無いものが無いため、代替表は
    使われないはず（使われたら真値の作り方が間違っている）。
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
                if isinstance(fb_target, tuple):
                    fb_target = fb_target[0]
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
        elif kind == "entities" and entity_fallbacks:
            fb = entity_fallbacks.get(name)
        if fb is not None and fb in target_index:
            out[i] = target_index[fb]
            continue
        out[i] = generic_fallback
    return out


def registry_order(registries, key):
    return [k.split(":", 1)[1] for _, k in sorted(
        (v["protocol_id"], k)
        for k, v in registries[key]["entries"].items())]


def main():
    import argparse
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--data-dir", type=Path, default=DATA_DIR)
    args = parser.parse_args()
    data_dir = args.data_dir

    if not TABLES_FILE.exists():
        print(f"ファイルがない: {TABLES_FILE}", file=sys.stderr)
        return 1
    java = TABLES_FILE.read_text(encoding="utf-8")
    genmod = load_gen_module()
    a757 = load_json(data_dir / "vv-1.18.json")
    a759 = load_json(data_dir / "vv-1.19.json")
    a757n = dict(a757)
    a759n = dict(a759)
    a757n["blockstates"] = normalize_blockstates(a757["blockstates"])
    a759n["blockstates"] = normalize_blockstates(a759["blockstates"])
    maps757 = load_json(data_dir / "maps757.json")
    maps757_rev = load_json(data_dir / "maps757-reverse.json")
    corrections = load_json(data_dir / "maps757-corrections.json")
    rev757 = load_json(data_dir / "map_items_rev757.json")
    gen757 = (data_dir / "gen757.txt").read_text(encoding="utf-8")

    failures = []

    def check(label, problems):
        if problems:
            failures.append(label)
            print(f"[FAIL] {label}: {len(problems)} 件")
            for p in problems[:20]:
                print(f"    {p}")
        else:
            print(f"[OK] {label}")

    # --- A. *_757 表が生成スクリプトの出力と一致する ---
    for kind in KINDS:
        ranges = parse_int3_table(java, KIND_TO_JAVA[kind] + "_757_RANGES")
        overrides = parse_int_table(java, KIND_TO_JAVA[kind] + "_757_OVERRIDES")
        expanded = expand(ranges, overrides, len(a759[kind]), FB759[kind])
        gen = {int(k): v for k, v in maps757[kind].items()}
        problems = [f"{kind}[{i}] Java={expanded[i]} 生成={gen[i]}"
                    for i in range(len(a759[kind]))
                    if expanded[i] != gen[i]]
        check(f"A. {kind}_757 表 = 生成出力", problems)

    # --- F. ITEM_REVERSE_757 表が生成スクリプトの出力と一致する ---
    ranges = parse_int3_table(java, "ITEM_REVERSE_757_RANGES")
    overrides = parse_int_table(java, "ITEM_REVERSE_757_OVERRIDES")
    expanded = expand(ranges, overrides, len(a757["items"]), 1)
    gen = {int(k): v for k, v in rev757.items()}
    problems = [f"items[{i}] Java={expanded[i]} 生成={gen[i]}"
                for i in range(len(a757["items"]))
                if expanded[i] != gen[i]]
    check("F. ITEM_REVERSE_757 表 = 生成出力", problems)

    # --- B. *_757_TO_759 表が 757→759 の名前の真値と一致する ---
    # 757→759 に改名は無い。葉は waterlogged=false を足して突き合わせる。
    for kind in KINDS:
        if kind == "items":
            ranges = parse_int3_table(java, "ITEM_REVERSE_757_RANGES")
            overrides = parse_int_table(java, "ITEM_REVERSE_757_OVERRIDES")
            up = expand(ranges, overrides, len(a757["items"]), 1)
        else:
            ranges = parse_int3_table(
                java, KIND_TO_JAVA[kind] + "_757_TO_759_RANGES")
            overrides = parse_int_table(
                java, KIND_TO_JAVA[kind] + "_757_TO_759_OVERRIDES")
            up = expand(ranges, overrides, len(a757[kind]), FB759[kind])
        truth = name_truth(
            a757n[kind], a759n[kind], kind, FB759[kind],
            rename={},
            add_props=(("waterlogged", "false"),) if kind == "blockstates" else ())
        problems = []
        for i in range(len(a757[kind])):
            if up[i] != truth[i]:
                problems.append(
                    f"{kind}[{i}] {a757[kind][i]}: 反転={up[i]} 真値={truth[i]}")
        check(f"B. {kind} 757→759 反転 = 名前の真値", problems)
        # 生成出力の反転表（maps757-reverse.json）も同じ真値のはず。
        gen_rev = {int(k): v for k, v in maps757_rev[kind].items()}
        problems = [f"{kind}[{i}] 生成={gen_rev[i]} 真値={truth[i]}"
                    for i in range(len(a757[kind]))
                    if gen_rev[i] != truth[i]]
        check(f"B'. {kind} 生成の反転表 = 名前の真値", problems)

    # --- C. 合成表が無いこと（表の一覧が想定の 50 件ちょうど） ---
    found_tables = re.findall(
        r"(?:int\[\]\[\]|String\[\]\[\]|String\[\]) (\w+) = \{", java)
    expected = set()
    for kind in KINDS:
        expected.add(KIND_TO_JAVA[kind] + "_757_RANGES")
        expected.add(KIND_TO_JAVA[kind] + "_757_OVERRIDES")
        expected.add(KIND_TO_JAVA[kind] + "_757_TO_759_RANGES")
        expected.add(KIND_TO_JAVA[kind] + "_757_TO_759_OVERRIDES")
    expected |= {"ITEM_REVERSE_757_RANGES", "ITEM_REVERSE_757_OVERRIDES",
                 "SOUND_NAME_FALLBACKS_757", "SOUND_NAME_FALLBACKS_759_TO_757",
                 "SOUND_NAME_FALLBACKS_757_TO_759",
                 "ATTRIBUTE_757_RANGES", "ATTRIBUTE_757_OVERRIDES",
                 "ATTRIBUTE_REVERSE_757_RANGES",
                 "ATTRIBUTE_REVERSE_757_OVERRIDES", "ATTRIBUTE_757_NAMES",
                 "ENCHANTMENT_757_RANGES", "ENCHANTMENT_757_OVERRIDES",
                 "ENCHANTMENT_REVERSE_757_RANGES",
                 "ENCHANTMENT_REVERSE_757_OVERRIDES",
                 "BIOME_757_RANGES", "BIOME_757_OVERRIDES",
                 "BIOME_757_TO_759_RANGES", "BIOME_757_TO_759_OVERRIDES",
                 "DAMAGE_TYPE_757_RANGES", "DAMAGE_TYPE_757_OVERRIDES",
                 "DAMAGE_TYPE_757_TO_759_RANGES",
                 "DAMAGE_TYPE_757_TO_759_OVERRIDES"}
    problems = []
    if set(found_tables) != expected:
        problems.append(f"表の一覧が違う：余分={sorted(set(found_tables) - expected)} "
                        f"不足={sorted(expected - set(found_tables))}")
    if len(found_tables) != 50:
        problems.append(f"表が {len(found_tables)} 件（想定 50 件）")
    check("C. 合成表が無く表は 50 件ちょうど", problems)

    # --- D. 反転表＝invert(順向き)＋補正 ---
    for kind in KINDS:
        ranges = parse_int3_table(java, KIND_TO_JAVA[kind] + "_757_RANGES")
        overrides = parse_int_table(java, KIND_TO_JAVA[kind] + "_757_OVERRIDES")
        corr = [tuple(c) for c in corrections[kind]]
        inv = invert_java(ranges, overrides, len(a759[kind]),
                          len(a757[kind]), FB759[kind], corr)
        truth = name_truth(
            a757n[kind], a759n[kind], kind, FB759[kind],
            rename={},
            add_props=(("waterlogged", "false"),) if kind == "blockstates" else ())
        problems = []
        for i in range(len(a757[kind])):
            if inv[i] != truth[i]:
                problems.append(
                    f"{kind}[{i}] {a757[kind][i]}: invert+補正={inv[i]} "
                    f"真値={truth[i]}")
        check(f"D. {kind} invert+補正 = 名前の真値", problems)

    # --- E. 生成スクリプトの再現性 ---
    proc = subprocess.run(
        [sys.executable, str(TOOLS_DIR / "gen-757-tables.py")],
        capture_output=True, text=True, timeout=300)
    if proc.returncode != 0:
        print("[FAIL] E. 生成スクリプト gen-757-tables.py が失敗した")
        print(proc.stderr[-2000:])
        failures.append("E-757")
    else:
        gen_re = (data_dir / "gen757.txt").read_text(encoding="utf-8")
        problems = []
        n_int = 0
        for m in re.finditer(
                r"int\[\]\[\] ([A-Z0-9_]+_(?:RANGES|OVERRIDES)) = \{(.*?)\n    \};",
                gen_re, re.DOTALL):
            name = m.group(1)
            n_int += 1
            if name.endswith("_RANGES"):
                gen_table = [tuple(map(int, t)) for t in re.findall(
                    r"\{(-?\d+), (-?\d+), (-?\d+)\}", m.group(2))]
                try:
                    java_table = parse_int3_table(java, name)
                except SystemExit:
                    problems.append(f"{name} が RegistryTables757.java に無い")
                    continue
            else:
                gen_table = [tuple(map(int, t)) for t in re.findall(
                    r"\{(-?\d+), (-?\d+)\}", m.group(2))]
                try:
                    java_table = parse_int_table(java, name)
                except SystemExit:
                    problems.append(f"{name} が RegistryTables757.java に無い")
                    continue
            if gen_table != java_table:
                problems.append(
                    f"{name} が違う：生成={len(gen_table)} 件、Java={len(java_table)} 件")
        for m in re.finditer(
                r"String\[\]\[\] (SOUND_NAME_[A-Z0-9_]+) = \{(.*?)\n    \};",
                gen_re, re.DOTALL):
            name = m.group(1)
            n_int += 1
            gen_table = re.findall(r'\{"([^"]+)", "([^"]+)"\}', m.group(2))
            try:
                java_table = parse_string_table(java, name)
            except SystemExit:
                problems.append(f"{name} が RegistryTables757.java に無い")
                continue
            if gen_table != java_table:
                problems.append(
                    f"{name} が違う：生成={len(gen_table)} 件、Java={len(java_table)} 件")
        m = re.search(r"String\[\] (ATTRIBUTE_757_NAMES) = \{(.*?)\n    \};",
                      gen_re, re.DOTALL)
        if m:
            n_int += 1
            gen_names = re.findall(r'"([^"]+)"', m.group(2))
            try:
                java_names_list = parse_string_array(java, m.group(1))
            except SystemExit:
                problems.append("ATTRIBUTE_757_NAMES が RegistryTables757.java に無い")
                java_names_list = None
            if java_names_list is not None and gen_names != java_names_list:
                problems.append("ATTRIBUTE_757_NAMES が違う")
        print(f"E. 生成出力の表: {n_int} 件")
        check("E. 生成スクリプト gen-757-tables.py の再現性", problems)

    # --- G. 属性・エンチャント・バイオーム・ダメージ種別 ---
    # 属性：13 件の恒等写像。名前は registries.json の protocol_id 順。
    reg18 = load_json(data_dir / "registries-1.18.json")
    reg19 = load_json(data_dir / "registries-1.19.json")
    attrs18 = registry_order(reg18, "minecraft:attribute")
    attrs19 = registry_order(reg19, "minecraft:attribute")
    for name, size in (("ATTRIBUTE_757", len(attrs19)),
                       ("ATTRIBUTE_REVERSE_757", len(attrs18))):
        ranges = parse_int3_table(java, name + "_RANGES")
        overrides = parse_int_table(java, name + "_OVERRIDES")
        expanded = expand(ranges, overrides, size, -1)
        problems = [f"{name}[{i}] Java={expanded[i]} 想定={i}"
                    for i in range(size) if expanded[i] != i]
        check(f"G. {name} 表 = 恒等写像", problems)
    java_attrs = parse_string_array(java, "ATTRIBUTE_757_NAMES")
    problems = []
    if java_attrs != attrs18:
        problems.append(f"ATTRIBUTE_757_NAMES が registries-1.18.json と違う")
    if attrs18 != attrs19:
        problems.append("1.18 と 1.19 の属性の順序が違う")
    check("G. ATTRIBUTE_757_NAMES = registries.json の順序", problems)
    # エンチャント：swift_sneak（759 の 12 番）→-1、他はシフト。
    ench18 = [e["name"] for e in load_json(data_dir / "enchantments-1.18.json")]
    ench19 = [e["name"] for e in load_json(data_dir / "enchantments-1.19.json")]
    truth_ench = {i: (ench18.index(n) if n in ench18 else -1)
                  for i, n in enumerate(ench19)}
    ranges = parse_int3_table(java, "ENCHANTMENT_757_RANGES")
    overrides = parse_int_table(java, "ENCHANTMENT_757_OVERRIDES")
    expanded = expand(ranges, overrides, len(ench19), -1)
    problems = [f"enchantments[{i}] {ench19[i]}: Java={expanded[i]} "
                f"真値={truth_ench[i]}"
                for i in range(len(ench19)) if expanded[i] != truth_ench[i]]
    check("G. ENCHANTMENT_757 表 = minecraft-data の順序", problems)
    truth_ench_rev = {i: (ench19.index(n) if n in ench19 else -1)
                      for i, n in enumerate(ench18)}
    ranges = parse_int3_table(java, "ENCHANTMENT_REVERSE_757_RANGES")
    overrides = parse_int_table(java, "ENCHANTMENT_REVERSE_757_OVERRIDES")
    expanded = expand(ranges, overrides, len(ench18), -1)
    problems = [f"enchantments[{i}] {ench18[i]}: Java={expanded[i]} "
                f"真値={truth_ench_rev[i]}"
                for i in range(len(ench18)) if expanded[i] != truth_ench_rev[i]]
    check("G. ENCHANTMENT_REVERSE_757 表 = minecraft-data の順序", problems)
    # バイオーム：biomes.json の id 順と BIOME_FALLBACK_757。
    biomes18 = [e["name"] for e in sorted(
        load_json(data_dir / "biomes-1.18.json"), key=lambda e: e["id"])]
    biomes19 = [e["name"] for e in sorted(
        load_json(data_dir / "biomes-1.19.json"), key=lambda e: e["id"])]
    truth_bio = name_truth(
        biomes19, biomes18, "biomes", -1,
        rename=genmod.BIOME_FALLBACK_757)
    ranges = parse_int3_table(java, "BIOME_757_RANGES")
    overrides = parse_int_table(java, "BIOME_757_OVERRIDES")
    expanded = expand(ranges, overrides, len(biomes19), -1)
    problems = [f"biomes[{i}] {biomes19[i]}: Java={expanded[i]} 真値={truth_bio[i]}"
                for i in range(len(biomes19)) if expanded[i] != truth_bio[i]]
    check("G. BIOME_757 表 = biomes.json の順序", problems)
    truth_bio_rev = name_truth(biomes18, biomes19, "biomes", -1)
    ranges = parse_int3_table(java, "BIOME_757_TO_759_RANGES")
    overrides = parse_int_table(java, "BIOME_757_TO_759_OVERRIDES")
    expanded = expand(ranges, overrides, len(biomes18), -1)
    problems = [f"biomes[{i}] {biomes18[i]}: Java={expanded[i]} "
                f"真値={truth_bio_rev[i]}"
                for i in range(len(biomes18)) if expanded[i] != truth_bio_rev[i]]
    check("G. BIOME_757_TO_759 表 = biomes.json の順序", problems)
    # ダメージ種別：762 の 42 件の恒等写像のプレースホルダ。
    for name in ("DAMAGE_TYPE_757", "DAMAGE_TYPE_757_TO_759"):
        ranges = parse_int3_table(java, name + "_RANGES")
        overrides = parse_int_table(java, name + "_OVERRIDES")
        expanded = expand(ranges, overrides, 42, -1)
        problems = [f"{name}[{i}] Java={expanded[i]} 想定={i}"
                    for i in range(42) if expanded[i] != i]
        if overrides:
            problems.append(f"{name} の OVERRIDES が空でない：{len(overrides)} 件")
        check(f"G. {name} 表 = 42 件の恒等写像", problems)

    # --- H. 音名表 ---
    # 759→757 は SOUND_FALLBACK_757 そのもの（759 の新音 118 件）。
    expected_759_to_757 = sorted(genmod.SOUND_FALLBACK_757.items())
    java_759_to_757 = parse_string_table(java, "SOUND_NAME_FALLBACKS_759_TO_757")
    problems = []
    if java_759_to_757 != expected_759_to_757:
        problems.append(f"件数が違う：Java={len(java_759_to_757)} "
                        f"想定={len(expected_759_to_757)}")
        for a, b in zip(java_759_to_757, expected_759_to_757):
            if a != b:
                problems.append(f"{a} != {b}")
    check("H. SOUND_NAME_FALLBACKS_759_TO_757 = SOUND_FALLBACK_757", problems)
    java_757 = parse_string_table(java, "SOUND_NAME_FALLBACKS_757")
    problems = []
    if java_757 != expected_759_to_757:
        problems.append(f"SOUND_NAME_FALLBACKS_757 が 759_TO_757 と違う")
    check("H. SOUND_NAME_FALLBACKS_757 = 759_TO_757 と同じ", problems)
    # 757→759 は名前の真値の反転表から作ったもの（0 件）。
    truth_sounds = name_truth(a757n["sounds"], a759n["sounds"], "sounds",
                              FB759["sounds"])
    expected_757_to_759 = sorted(
        (a757["sounds"][i], a759["sounds"][truth_sounds[i]])
        for i in range(len(a757["sounds"]))
        if a759["sounds"][truth_sounds[i]] != a757["sounds"][i])
    java_757_to_759 = parse_string_table(java, "SOUND_NAME_FALLBACKS_757_TO_759")
    problems = []
    if java_757_to_759 != expected_757_to_759:
        problems.append(f"Java={java_757_to_759} 想定={expected_757_to_759}")
    check("H. SOUND_NAME_FALLBACKS_757_TO_759 = 名前の真値の反転", problems)

    print()
    if failures:
        print(f"失敗：{len(failures)} 件のチェック")
        for f in failures:
            print(f"  - {f}")
        return 1
    print("全チェック通過（A 7＋B 7＋B' 7＋C 1＋D 7＋E 1＋F 1＋G 9＋H 3＝43 件）")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
