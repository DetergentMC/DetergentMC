#!/usr/bin/env python3
"""RegistryTables755.java の検証（チェック A〜H、755 版）。

雛形は verify-756-tables.py。755 は 756 の 1つ下で、756→755 の
隣接表だけを作る（755↔763〜775 は 755→756→759→760→761→762→763→
合成表の隣接連鎖で到達する）。そのため 756 版と同じくチェック C は
「合成表が無いこと」、D は「反転表＝invert(順向き)＋補正」の確認になる。

チェック：
  A  隣接表 *_755（756→755）：maps755.json と比較（7 件）
  B  反転表 *_755_TO_756：名前の真値と比較（7 件）
  C  合成表が無いこと：RegistryTables755.java の表の一覧が想定の
     50 件ちょうどで、合成表（*_76X_*／*_75X_* など）が無い（1 件）
  D  反転表＝invert(順向き)＋補正：RegistryRemapper.invert() と
     同じ決め方で順向き表を反転し、maps755-corrections.json の
     補正（0 件）を当てて名前の真値と
     一致することを確認する（7 件）。これが wiring の契約。
  E  gen-755-tables.py を再実行し、出力の全表が Java ファイルと
     一致することを確認する（1 件）
  F  ITEM_REVERSE_755：map_items_rev755.json と比較（1 件）
  G  属性・エンチャント・バイオーム・ダメージ種別：
     属性は 13 件の恒等写像で名前は registries.json の protocol_id
     順と一致、エンチャントは 38 件の恒等写像で順序は
     minecraft-data と一致、バイオームは biomes.json の id と
     改名・代替・削除は 0 件で両側 81 件・id 0〜175 と一致、
     ダメージ種別は 762 の 42 件の恒等写像のプレースホルダ
     （11 件）
  H  音名表 SOUND_NAME_FALLBACKS_*：756→755 は SOUND_FALLBACK_755
     そのもの、755→756 は名前の真値の反転表から作ったもの
     （0 件。755 の音はすべて 756 に同名で存在する）、755 は
     756→755 と同じ（3 件）

合計：43 件（A 7＋B 7＋B' 7＋C 1＋D 7＋E 1＋F 1＋G 9＋H 3）。

使い方：リポジトリのルートで
    python3 detergentmc-protocol/tools/verify-755-tables.py

注意：順向き表（756→755）の名前の真値との直接の突き合わせは
しない（756 版と同じ設計。反転表側での独立検証に任せる。755 では
代替が無いため順向き表も名前の真値と一致するはずだが、チェック A で
生成出力との一致を確認している）。反転表（755→756）には代替が
絡まないため、名前の真値との突き合わせがそのまま独立した検証になる。
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
               / "io" / "detergentmc" / "protocol" / "RegistryTables755.java")

KINDS = ["blockstates", "items", "entities", "sounds", "particles",
         "blocks", "menus"]
KIND_TO_JAVA = {"blockstates": "BLOCK_STATE", "items": "ITEM",
                "entities": "ENTITY_TYPE", "sounds": "SOUND",
                "particles": "PARTICLE", "blocks": "BLOCK", "menus": "MENU"}

# 756 側の代替先（UP_755_FALLBACKS。gen-755-tables.py と同じ値）。
# ブタは 756 では 64、石の破壊音は 756 では 1005、
# パーティクルは entity_effect=22、メニューは crafting=11。
FB756 = {"blockstates": 1, "items": 1, "entities": 64, "sounds": 1005,
         "particles": 22, "blocks": 1, "menus": 11}

# パーティクルの反転の真値（755→756）。755 と 756 は同一順序のため
# 代替は無い（barrier・light→block_marker の統合は 1.18 の話）。
REVERSE_PARTICLE_FALLBACKS = {}


def load_json(path):
    with open(path, encoding="utf-8") as f:
        return json.load(f)


def load_gen_module():
    """gen-755-tables.py をモジュールとして読み、代替表の dict を使う。"""
    spec = importlib.util.spec_from_file_location(
        "gen755", TOOLS_DIR / "gen-755-tables.py")
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    return mod


def normalize_blockstates(states):
    """ブロック状態の文字列のプロパティをソートして正規化する
    （gen-755-tables.py と同じ。755 では順序の違いは無い）。"""
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
    755→756 の反転ではパーティクルの代替は無い（755 と 756 は
    同一順序のため）。バイオームの代替はこの関数では扱わず、呼び出し側で
    明示して埋める（755 では代替は 0 件）。
    """
    rename = rename or {}
    target_index = {n: i for i, n in enumerate(target_names)
                    if n is not None}
    by_props = {}
    for i, n in enumerate(target_names):
        if n is None:
            continue
        by_props[(block_name(n), frozenset(block_props(n).items()))] = i
    out = {}
    for i, name in enumerate(source_names):
        if name is None:
            # バイオーム表の隙間の id（1.17 に無い番号）。
            out[i] = generic_fallback
            continue
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
    a755 = load_json(data_dir / "vv-1.17.json")
    a756 = load_json(data_dir / "vv-1.17.json")
    a755n = dict(a755)
    a756n = dict(a756)
    a755n["blockstates"] = normalize_blockstates(a755["blockstates"])
    a756n["blockstates"] = normalize_blockstates(a756["blockstates"])
    maps755 = load_json(data_dir / "maps755.json")
    maps755_rev = load_json(data_dir / "maps755-reverse.json")
    corrections = load_json(data_dir / "maps755-corrections.json")
    rev755 = load_json(data_dir / "map_items_rev755.json")
    gen755 = (data_dir / "gen755.txt").read_text(encoding="utf-8")

    failures = []

    def check(label, problems):
        if problems:
            failures.append(label)
            print(f"[FAIL] {label}: {len(problems)} 件")
            for p in problems[:20]:
                print(f"    {p}")
        else:
            print(f"[OK] {label}")

    # --- A. *_755 表が生成スクリプトの出力と一致する ---
    for kind in KINDS:
        ranges = parse_int3_table(java, KIND_TO_JAVA[kind] + "_755_RANGES")
        overrides = parse_int_table(java, KIND_TO_JAVA[kind] + "_755_OVERRIDES")
        expanded = expand(ranges, overrides, len(a756[kind]), FB756[kind])
        gen = {int(k): v for k, v in maps755[kind].items()}
        problems = [f"{kind}[{i}] Java={expanded[i]} 生成={gen[i]}"
                    for i in range(len(a756[kind]))
                    if expanded[i] != gen[i]]
        check(f"A. {kind}_755 表 = 生成出力", problems)

    # --- F. ITEM_REVERSE_755 表が生成スクリプトの出力と一致する ---
    ranges = parse_int3_table(java, "ITEM_REVERSE_755_RANGES")
    overrides = parse_int_table(java, "ITEM_REVERSE_755_OVERRIDES")
    expanded = expand(ranges, overrides, len(a755["items"]), 1)
    gen = {int(k): v for k, v in rev755.items()}
    problems = [f"items[{i}] Java={expanded[i]} 生成={gen[i]}"
                for i in range(len(a755["items"]))
                if expanded[i] != gen[i]]
    check("F. ITEM_REVERSE_755 表 = 生成出力", problems)

    # --- B. *_755_TO_756 表が 755→756 の名前の真値と一致する ---
    # 755→756 に改名は無い。パーティクルの代替も無い（同一順序）。
    for kind in KINDS:
        if kind == "items":
            ranges = parse_int3_table(java, "ITEM_REVERSE_755_RANGES")
            overrides = parse_int_table(java, "ITEM_REVERSE_755_OVERRIDES")
            up = expand(ranges, overrides, len(a755["items"]), 1)
        else:
            ranges = parse_int3_table(
                java, KIND_TO_JAVA[kind] + "_755_TO_756_RANGES")
            overrides = parse_int_table(
                java, KIND_TO_JAVA[kind] + "_755_TO_756_OVERRIDES")
            up = expand(ranges, overrides, len(a755[kind]), FB756[kind])
        truth = name_truth(
            a755n[kind], a756n[kind], kind, FB756[kind],
            rename={},
            particle_fallbacks=REVERSE_PARTICLE_FALLBACKS
            if kind == "particles" else None,
            add_props=(("waterlogged", "false"),) if kind == "blockstates" else ())
        problems = []
        for i in range(len(a755[kind])):
            if up[i] != truth[i]:
                problems.append(
                    f"{kind}[{i}] {a755[kind][i]}: 反転={up[i]} 真値={truth[i]}")
        check(f"B. {kind} 755→756 反転 = 名前の真値", problems)
        # 生成出力の反転表（maps755-reverse.json）も同じ真値のはず。
        gen_rev = {int(k): v for k, v in maps755_rev[kind].items()}
        problems = [f"{kind}[{i}] 生成={gen_rev[i]} 真値={truth[i]}"
                    for i in range(len(a755[kind]))
                    if gen_rev[i] != truth[i]]
        check(f"B'. {kind} 生成の反転表 = 名前の真値", problems)

    # --- C. 合成表が無いこと（表の一覧が想定の 50 件ちょうど） ---
    found_tables = re.findall(
        r"(?:int\[\]\[\]|String\[\]\[\]|String\[\]) (\w+) = \{", java)
    expected = set()
    for kind in KINDS:
        expected.add(KIND_TO_JAVA[kind] + "_755_RANGES")
        expected.add(KIND_TO_JAVA[kind] + "_755_OVERRIDES")
        expected.add(KIND_TO_JAVA[kind] + "_755_TO_756_RANGES")
        expected.add(KIND_TO_JAVA[kind] + "_755_TO_756_OVERRIDES")
    expected |= {"ITEM_REVERSE_755_RANGES", "ITEM_REVERSE_755_OVERRIDES",
                 "SOUND_NAME_FALLBACKS_755", "SOUND_NAME_FALLBACKS_756_TO_755",
                 "SOUND_NAME_FALLBACKS_755_TO_756",
                 "ATTRIBUTE_755_RANGES", "ATTRIBUTE_755_OVERRIDES",
                 "ATTRIBUTE_REVERSE_755_RANGES",
                 "ATTRIBUTE_REVERSE_755_OVERRIDES", "ATTRIBUTE_755_NAMES",
                 "ENCHANTMENT_755_RANGES", "ENCHANTMENT_755_OVERRIDES",
                 "ENCHANTMENT_REVERSE_755_RANGES",
                 "ENCHANTMENT_REVERSE_755_OVERRIDES",
                 "BIOME_755_RANGES", "BIOME_755_OVERRIDES",
                 "BIOME_755_TO_756_RANGES", "BIOME_755_TO_756_OVERRIDES",
                 "DAMAGE_TYPE_755_RANGES", "DAMAGE_TYPE_755_OVERRIDES",
                 "DAMAGE_TYPE_755_TO_756_RANGES",
                 "DAMAGE_TYPE_755_TO_756_OVERRIDES"}
    problems = []
    if set(found_tables) != expected:
        problems.append(f"表の一覧が違う：余分={sorted(set(found_tables) - expected)} "
                        f"不足={sorted(expected - set(found_tables))}")
    if len(found_tables) != 50:
        problems.append(f"表が {len(found_tables)} 件（想定 50 件）")
    check("C. 合成表が無く表は 50 件ちょうど", problems)

    # --- D. 反転表＝invert(順向き)＋補正 ---
    for kind in KINDS:
        ranges = parse_int3_table(java, KIND_TO_JAVA[kind] + "_755_RANGES")
        overrides = parse_int_table(java, KIND_TO_JAVA[kind] + "_755_OVERRIDES")
        corr = [tuple(c) for c in corrections[kind]]
        inv = invert_java(ranges, overrides, len(a756[kind]),
                          len(a755[kind]), FB756[kind], corr)
        truth = name_truth(
            a755n[kind], a756n[kind], kind, FB756[kind],
            rename={},
            particle_fallbacks=REVERSE_PARTICLE_FALLBACKS
            if kind == "particles" else None,
            add_props=(("waterlogged", "false"),) if kind == "blockstates" else ())
        problems = []
        for i in range(len(a755[kind])):
            if inv[i] != truth[i]:
                problems.append(
                    f"{kind}[{i}] {a755[kind][i]}: invert+補正={inv[i]} "
                    f"真値={truth[i]}")
        check(f"D. {kind} invert+補正 = 名前の真値", problems)

    # --- E. 生成スクリプトの再現性 ---
    proc = subprocess.run(
        [sys.executable, str(TOOLS_DIR / "gen-755-tables.py")],
        capture_output=True, text=True, timeout=300)
    if proc.returncode != 0:
        print("[FAIL] E. 生成スクリプト gen-755-tables.py が失敗した")
        print(proc.stderr[-2000:])
        failures.append("E-755")
    else:
        gen_re = (data_dir / "gen755.txt").read_text(encoding="utf-8")
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
                    problems.append(f"{name} が RegistryTables755.java に無い")
                    continue
            else:
                gen_table = [tuple(map(int, t)) for t in re.findall(
                    r"\{(-?\d+), (-?\d+)\}", m.group(2))]
                try:
                    java_table = parse_int_table(java, name)
                except SystemExit:
                    problems.append(f"{name} が RegistryTables755.java に無い")
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
                problems.append(f"{name} が RegistryTables755.java に無い")
                continue
            if gen_table != java_table:
                problems.append(
                    f"{name} が違う：生成={len(gen_table)} 件、Java={len(java_table)} 件")
        m = re.search(r"String\[\] (ATTRIBUTE_755_NAMES) = \{(.*?)\n    \};",
                      gen_re, re.DOTALL)
        if m:
            n_int += 1
            gen_names = re.findall(r'"([^"]+)"', m.group(2))
            try:
                java_names_list = parse_string_array(java, m.group(1))
            except SystemExit:
                problems.append("ATTRIBUTE_755_NAMES が RegistryTables755.java に無い")
                java_names_list = None
            if java_names_list is not None and gen_names != java_names_list:
                problems.append("ATTRIBUTE_755_NAMES が違う")
        print(f"E. 生成出力の表: {n_int} 件")
        check("E. 生成スクリプト gen-755-tables.py の再現性", problems)

    # --- G. 属性・エンチャント・バイオーム・ダメージ種別 ---
    # 属性：13 件の恒等写像。名前は registries.json の protocol_id 順。
    reg755 = load_json(data_dir / "registries-1.17.json")
    reg756 = load_json(data_dir / "registries-1.17.1.json")
    attrs17 = registry_order(reg755, "minecraft:attribute")
    attrs18 = registry_order(reg756, "minecraft:attribute")
    for name, size in (("ATTRIBUTE_755", len(attrs18)),
                       ("ATTRIBUTE_REVERSE_755", len(attrs17))):
        ranges = parse_int3_table(java, name + "_RANGES")
        overrides = parse_int_table(java, name + "_OVERRIDES")
        expanded = expand(ranges, overrides, size, -1)
        problems = [f"{name}[{i}] Java={expanded[i]} 想定={i}"
                    for i in range(size) if expanded[i] != i]
        check(f"G. {name} 表 = 恒等写像", problems)
    java_attrs = parse_string_array(java, "ATTRIBUTE_755_NAMES")
    problems = []
    if java_attrs != attrs17:
        problems.append("ATTRIBUTE_755_NAMES が registries-1.17.json と違う")
    if attrs17 != attrs18:
        problems.append("1.17 と 1.17.1 の属性の順序が違う")
    check("G. ATTRIBUTE_755_NAMES = registries.json の順序", problems)
    # エンチャント：1.17 と 1.17.1 は 38 件・同一順序のため恒等写像。
    ench755 = [e["name"] for e in load_json(data_dir / "enchantments-1.17.json")]
    ench756 = [e["name"] for e in load_json(data_dir / "enchantments-1.17.json")]
    truth_ench = {i: (ench755.index(n) if n in ench755 else -1)
                  for i, n in enumerate(ench756)}
    ranges = parse_int3_table(java, "ENCHANTMENT_755_RANGES")
    overrides = parse_int_table(java, "ENCHANTMENT_755_OVERRIDES")
    expanded = expand(ranges, overrides, len(ench756), -1)
    problems = [f"enchantments[{i}] {ench756[i]}: Java={expanded[i]} "
                f"真値={truth_ench[i]}"
                for i in range(len(ench756)) if expanded[i] != truth_ench[i]]
    check("G. ENCHANTMENT_755 表 = minecraft-data の順序", problems)
    truth_ench_rev = {i: (ench756.index(n) if n in ench756 else -1)
                      for i, n in enumerate(ench755)}
    ranges = parse_int3_table(java, "ENCHANTMENT_REVERSE_755_RANGES")
    overrides = parse_int_table(java, "ENCHANTMENT_REVERSE_755_OVERRIDES")
    expanded = expand(ranges, overrides, len(ench755), -1)
    problems = [f"enchantments[{i}] {ench755[i]}: Java={expanded[i]} "
                f"真値={truth_ench_rev[i]}"
                for i in range(len(ench755)) if expanded[i] != truth_ench_rev[i]]
    check("G. ENCHANTMENT_REVERSE_755 表 = minecraft-data の順序", problems)
    # バイオーム：biomes.json の id と一致（改名・代替・削除は 0 件）。
    # 順向き・反転ともワイヤ id 空間（176 件。id 0〜175）の表。
    # 756 のときは 757 側が 0〜60 の連番で位置 == ワイヤ id だったため
    # 81 件の位置リストが使えたが、756 側は非連番のため 176 件リストを使う。
    biomes755_by_id = {e["id"]: e["name"]
                      for e in load_json(data_dir / "biomes-1.17.json")}
    max_bio755 = max(biomes755_by_id)
    biomes755_list = [biomes755_by_id.get(i) for i in range(max_bio755 + 1)]
    # 756 側も同じファイル（id→名前は同一）なので同じ 176 件リスト。
    biomes756 = list(biomes755_list)
    biomes755_name_to_id = {n: i for i, n in biomes755_by_id.items()}
    biomes756_name_to_id = dict(biomes755_name_to_id)
    truth_bio = name_truth(
        biomes756, biomes755_list, "biomes", -1,
        rename=genmod.BIOME_RENAME_755)
    # 隙間の id は名前の真値が -1 になるが、Java 表では平原（1）で埋める
    # （gen-755-tables.py と同じ規則。BIOME_FALLBACK_755 は空で改名・
    # 新規は 0 件のため、-1 になるのは隙間だけ）。
    for i, v in truth_bio.items():
        if v < 0:
            truth_bio[i] = 1
    ranges = parse_int3_table(java, "BIOME_755_RANGES")
    overrides = parse_int_table(java, "BIOME_755_OVERRIDES")
    expanded = expand(ranges, overrides, len(biomes756), -1)
    problems = [f"biomes[{i}] {biomes756[i]}: Java={expanded[i]} 真値={truth_bio[i]}"
                for i in range(len(biomes756)) if expanded[i] != truth_bio[i]]
    check("G. BIOME_755 表 = biomes.json の順序", problems)
    reverse_rename = {v: k for k, v in genmod.BIOME_RENAME_755.items()}
    truth_bio_rev = name_truth(
        biomes755_list, biomes756, "biomes", 1,
        rename=reverse_rename)
    removed_ids = sorted(biomes755_name_to_id[n]
                         for n in genmod.REVERSE_BIOME_FALLBACK_755)
    still_default = sorted(i for i, name in enumerate(biomes755_list)
                           if name is not None and truth_bio_rev[i] == 1)
    # 平原（755 id 1）は 756 の平原（id 1）への恒等写像で、値が既定値の
    # 1 と一致する。既定値のままなのは平原だけのはず。
    if still_default != sorted(set(removed_ids) | {1}):
        print(f"[FAIL] G. バイオーム反転の既定値が想定と違う：{still_default} "
              f"!= {removed_ids}")
        failures.append("G-biome-reverse-default")
    else:
        for i in removed_ids:
            truth_bio_rev[i] = biomes756_name_to_id[
                genmod.REVERSE_BIOME_FALLBACK_755[biomes755_list[i]]]
    ranges = parse_int3_table(java, "BIOME_755_TO_756_RANGES")
    overrides = parse_int_table(java, "BIOME_755_TO_756_OVERRIDES")
    expanded = expand(ranges, overrides, len(biomes755_list), 1)
    problems = [f"biomes[{i}] {biomes755_list[i]}: Java={expanded[i]} "
                f"真値={truth_bio_rev[i]}"
                for i in range(len(biomes755_list))
                if expanded[i] != truth_bio_rev[i]]
    check("G. BIOME_755_TO_756 表 = biomes.json の順序", problems)
    # ダメージ種別：762 の 42 件の恒等写像のプレースホルダ。
    for name in ("DAMAGE_TYPE_755", "DAMAGE_TYPE_755_TO_756"):
        ranges = parse_int3_table(java, name + "_RANGES")
        overrides = parse_int_table(java, name + "_OVERRIDES")
        expanded = expand(ranges, overrides, 42, -1)
        problems = [f"{name}[{i}] Java={expanded[i]} 想定={i}"
                    for i in range(42) if expanded[i] != i]
        if overrides:
            problems.append(f"{name} の OVERRIDES が空でない：{len(overrides)} 件")
        check(f"G. {name} 表 = 42 件の恒等写像", problems)

    # --- H. 音名表 ---
    # 756→755 は SOUND_FALLBACK_755 そのもの（756 の新音 13 件）。
    expected_756_to_755 = sorted(genmod.SOUND_FALLBACK_755.items())
    java_756_to_755 = parse_string_table(java, "SOUND_NAME_FALLBACKS_756_TO_755")
    problems = []
    if java_756_to_755 != expected_756_to_755:
        problems.append(f"件数が違う：Java={len(java_756_to_755)} "
                        f"想定={len(expected_756_to_755)}")
        for a, b in zip(java_756_to_755, expected_756_to_755):
            if a != b:
                problems.append(f"{a} != {b}")
    check("H. SOUND_NAME_FALLBACKS_756_TO_755 = SOUND_FALLBACK_755", problems)
    java_755 = parse_string_table(java, "SOUND_NAME_FALLBACKS_755")
    problems = []
    if java_755 != expected_756_to_755:
        problems.append("SOUND_NAME_FALLBACKS_755 が 756_TO_755 と違う")
    check("H. SOUND_NAME_FALLBACKS_755 = 756_TO_755 と同じ", problems)
    # 755→756 は名前の真値の反転表から作ったもの（0 件）。
    truth_sounds = name_truth(a755n["sounds"], a756n["sounds"], "sounds",
                              FB756["sounds"])
    expected_755_to_756 = sorted(
        (a755["sounds"][i], a756["sounds"][truth_sounds[i]])
        for i in range(len(a755["sounds"]))
        if a756["sounds"][truth_sounds[i]] != a755["sounds"][i])
    java_755_to_756 = parse_string_table(java, "SOUND_NAME_FALLBACKS_755_TO_756")
    problems = []
    if java_755_to_756 != expected_755_to_756:
        problems.append(f"Java={java_755_to_756} 想定={expected_755_to_756}")
    check("H. SOUND_NAME_FALLBACKS_755_TO_756 = 名前の真値の反転", problems)

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
