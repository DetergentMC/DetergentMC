#!/usr/bin/env python3
"""RegistryTables756.java の検証（チェック A〜H、756 版）。

雛形は verify-757-tables.py。756 は 757 の 1つ下で、757→756 の
隣接表だけを作る（756↔763〜775 は 756→757→759→760→761→762→763→
合成表の隣接連鎖で到達する）。そのため 757 版と同じくチェック C は
「合成表が無いこと」、D は「反転表＝invert(順向き)＋補正」の確認になる。

チェック：
  A  隣接表 *_756（757→756）：maps756.json と比較（7 件）
  B  反転表 *_756_TO_757：名前の真値と比較（7 件）
  C  合成表が無いこと：RegistryTables756.java の表の一覧が想定の
     50 件ちょうどで、合成表（*_76X_*／*_75X_* など）が無い（1 件）
  D  反転表＝invert(順向き)＋補正：RegistryRemapper.invert() と
     同じ決め方で順向き表を反転し、maps756-corrections.json の
     補正（パーティクル 1 件）を当てて名前の真値と
     一致することを確認する（7 件）。これが wiring の契約。
  E  gen-756-tables.py を再実行し、出力の全表が Java ファイルと
     一致することを確認する（1 件）
  F  ITEM_REVERSE_756：map_items_rev756.json と比較（1 件）
  G  属性・エンチャント・バイオーム・ダメージ種別：
     属性は 13 件の恒等写像で名前は registries.json の protocol_id
     順と一致、エンチャントは 38 件の恒等写像で順序は
     minecraft-data と一致、バイオームは biomes.json の id と
     改名 11件・代替 6件（順向き）／削除 26件（反転）と一致、
     ダメージ種別は 762 の 42 件の恒等写像のプレースホルダ
     （11 件）
  H  音名表 SOUND_NAME_FALLBACKS_*：757→756 は SOUND_FALLBACK_756
     そのもの、756→757 は名前の真値の反転表から作ったもの
     （0 件。756 の音はすべて 757 に同名で存在する）、756 は
     757→756 と同じ（3 件）

合計：43 件（A 7＋B 7＋B' 7＋C 1＋D 7＋E 1＋F 1＋G 9＋H 3）。

使い方：リポジトリのルートで
    python3 detergentmc-protocol/tools/verify-756-tables.py

注意：順向き表（757→756）の名前の真値との直接の突き合わせは
しない。理由は代替の音（例：music.overworld.* 8件→music.game）で、
名前だけの真値では代替先が決まらないため。反転表（756→757）には
代替が絡まないため、名前の真値との突き合わせがそのまま独立した
検証になる（パーティクルの light→block_marker だけは ViaVersion
の差分ファイルによる明示の統合で、真値に含める）。
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
               / "io" / "detergentmc" / "protocol" / "RegistryTables756.java")

KINDS = ["blockstates", "items", "entities", "sounds", "particles",
         "blocks", "menus"]
KIND_TO_JAVA = {"blockstates": "BLOCK_STATE", "items": "ITEM",
                "entities": "ENTITY_TYPE", "sounds": "SOUND",
                "particles": "PARTICLE", "blocks": "BLOCK", "menus": "MENU"}

# 757 側の代替先（UP_756_FALLBACKS。gen-756-tables.py と同じ値）。
# ブタは 757 では 64、石の破壊音は 757 では 1018、
# パーティクルは entity_effect=21、メニューは crafting=11。
FB757 = {"blockstates": 1, "items": 1, "entities": 64, "sounds": 1018,
         "particles": 21, "blocks": 1, "menus": 11}

# パーティクルの反転の真値（756→757）。barrier・light はどちらも
# 1.18 の block_marker に統合された（ViaVersion の差分ファイル）。
REVERSE_PARTICLE_FALLBACKS = {"barrier": "block_marker",
                              "light": "block_marker"}


def load_json(path):
    with open(path, encoding="utf-8") as f:
        return json.load(f)


def load_gen_module():
    """gen-756-tables.py をモジュールとして読み、代替表の dict を使う。"""
    spec = importlib.util.spec_from_file_location(
        "gen756", TOOLS_DIR / "gen-756-tables.py")
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    return mod


def normalize_blockstates(states):
    """ブロック状態の文字列のプロパティをソートして正規化する
    （gen-756-tables.py と同じ。756 では順序の違いは無い）。"""
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
    756→757 の反転ではパーティクルの barrier／light を block_marker
    へ写す（particle_fallbacks で渡す。ViaVersion の差分ファイルによる
    統合）。バイオームの代替（新規 6件・削除 26件）はこの関数では扱わず、
    呼び出し側で明示して埋める。
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
            # バイオーム表の隙間の id（1.17.1 に無い番号）。
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
    a756 = load_json(data_dir / "vv-1.17.json")
    a757 = load_json(data_dir / "vv-1.18.json")
    a756n = dict(a756)
    a757n = dict(a757)
    a756n["blockstates"] = normalize_blockstates(a756["blockstates"])
    a757n["blockstates"] = normalize_blockstates(a757["blockstates"])
    maps756 = load_json(data_dir / "maps756.json")
    maps756_rev = load_json(data_dir / "maps756-reverse.json")
    corrections = load_json(data_dir / "maps756-corrections.json")
    rev756 = load_json(data_dir / "map_items_rev756.json")
    gen756 = (data_dir / "gen756.txt").read_text(encoding="utf-8")

    failures = []

    def check(label, problems):
        if problems:
            failures.append(label)
            print(f"[FAIL] {label}: {len(problems)} 件")
            for p in problems[:20]:
                print(f"    {p}")
        else:
            print(f"[OK] {label}")

    # --- A. *_756 表が生成スクリプトの出力と一致する ---
    for kind in KINDS:
        ranges = parse_int3_table(java, KIND_TO_JAVA[kind] + "_756_RANGES")
        overrides = parse_int_table(java, KIND_TO_JAVA[kind] + "_756_OVERRIDES")
        expanded = expand(ranges, overrides, len(a757[kind]), FB757[kind])
        gen = {int(k): v for k, v in maps756[kind].items()}
        problems = [f"{kind}[{i}] Java={expanded[i]} 生成={gen[i]}"
                    for i in range(len(a757[kind]))
                    if expanded[i] != gen[i]]
        check(f"A. {kind}_756 表 = 生成出力", problems)

    # --- F. ITEM_REVERSE_756 表が生成スクリプトの出力と一致する ---
    ranges = parse_int3_table(java, "ITEM_REVERSE_756_RANGES")
    overrides = parse_int_table(java, "ITEM_REVERSE_756_OVERRIDES")
    expanded = expand(ranges, overrides, len(a756["items"]), 1)
    gen = {int(k): v for k, v in rev756.items()}
    problems = [f"items[{i}] Java={expanded[i]} 生成={gen[i]}"
                for i in range(len(a756["items"]))
                if expanded[i] != gen[i]]
    check("F. ITEM_REVERSE_756 表 = 生成出力", problems)

    # --- B. *_756_TO_757 表が 756→757 の名前の真値と一致する ---
    # 756→757 に改名は無い（バイオームを除く）。パーティクルは
    # barrier／light→block_marker の統合を真値に含める。
    for kind in KINDS:
        if kind == "items":
            ranges = parse_int3_table(java, "ITEM_REVERSE_756_RANGES")
            overrides = parse_int_table(java, "ITEM_REVERSE_756_OVERRIDES")
            up = expand(ranges, overrides, len(a756["items"]), 1)
        else:
            ranges = parse_int3_table(
                java, KIND_TO_JAVA[kind] + "_756_TO_757_RANGES")
            overrides = parse_int_table(
                java, KIND_TO_JAVA[kind] + "_756_TO_757_OVERRIDES")
            up = expand(ranges, overrides, len(a756[kind]), FB757[kind])
        truth = name_truth(
            a756n[kind], a757n[kind], kind, FB757[kind],
            rename={},
            particle_fallbacks=REVERSE_PARTICLE_FALLBACKS
            if kind == "particles" else None,
            add_props=(("waterlogged", "false"),) if kind == "blockstates" else ())
        problems = []
        for i in range(len(a756[kind])):
            if up[i] != truth[i]:
                problems.append(
                    f"{kind}[{i}] {a756[kind][i]}: 反転={up[i]} 真値={truth[i]}")
        check(f"B. {kind} 756→757 反転 = 名前の真値", problems)
        # 生成出力の反転表（maps756-reverse.json）も同じ真値のはず。
        gen_rev = {int(k): v for k, v in maps756_rev[kind].items()}
        problems = [f"{kind}[{i}] 生成={gen_rev[i]} 真値={truth[i]}"
                    for i in range(len(a756[kind]))
                    if gen_rev[i] != truth[i]]
        check(f"B'. {kind} 生成の反転表 = 名前の真値", problems)

    # --- C. 合成表が無いこと（表の一覧が想定の 50 件ちょうど） ---
    found_tables = re.findall(
        r"(?:int\[\]\[\]|String\[\]\[\]|String\[\]) (\w+) = \{", java)
    expected = set()
    for kind in KINDS:
        expected.add(KIND_TO_JAVA[kind] + "_756_RANGES")
        expected.add(KIND_TO_JAVA[kind] + "_756_OVERRIDES")
        expected.add(KIND_TO_JAVA[kind] + "_756_TO_757_RANGES")
        expected.add(KIND_TO_JAVA[kind] + "_756_TO_757_OVERRIDES")
    expected |= {"ITEM_REVERSE_756_RANGES", "ITEM_REVERSE_756_OVERRIDES",
                 "SOUND_NAME_FALLBACKS_756", "SOUND_NAME_FALLBACKS_757_TO_756",
                 "SOUND_NAME_FALLBACKS_756_TO_757",
                 "ATTRIBUTE_756_RANGES", "ATTRIBUTE_756_OVERRIDES",
                 "ATTRIBUTE_REVERSE_756_RANGES",
                 "ATTRIBUTE_REVERSE_756_OVERRIDES", "ATTRIBUTE_756_NAMES",
                 "ENCHANTMENT_756_RANGES", "ENCHANTMENT_756_OVERRIDES",
                 "ENCHANTMENT_REVERSE_756_RANGES",
                 "ENCHANTMENT_REVERSE_756_OVERRIDES",
                 "BIOME_756_RANGES", "BIOME_756_OVERRIDES",
                 "BIOME_756_TO_757_RANGES", "BIOME_756_TO_757_OVERRIDES",
                 "DAMAGE_TYPE_756_RANGES", "DAMAGE_TYPE_756_OVERRIDES",
                 "DAMAGE_TYPE_756_TO_757_RANGES",
                 "DAMAGE_TYPE_756_TO_757_OVERRIDES"}
    problems = []
    if set(found_tables) != expected:
        problems.append(f"表の一覧が違う：余分={sorted(set(found_tables) - expected)} "
                        f"不足={sorted(expected - set(found_tables))}")
    if len(found_tables) != 50:
        problems.append(f"表が {len(found_tables)} 件（想定 50 件）")
    check("C. 合成表が無く表は 50 件ちょうど", problems)

    # --- D. 反転表＝invert(順向き)＋補正 ---
    for kind in KINDS:
        ranges = parse_int3_table(java, KIND_TO_JAVA[kind] + "_756_RANGES")
        overrides = parse_int_table(java, KIND_TO_JAVA[kind] + "_756_OVERRIDES")
        corr = [tuple(c) for c in corrections[kind]]
        inv = invert_java(ranges, overrides, len(a757[kind]),
                          len(a756[kind]), FB757[kind], corr)
        truth = name_truth(
            a756n[kind], a757n[kind], kind, FB757[kind],
            rename={},
            particle_fallbacks=REVERSE_PARTICLE_FALLBACKS
            if kind == "particles" else None,
            add_props=(("waterlogged", "false"),) if kind == "blockstates" else ())
        problems = []
        for i in range(len(a756[kind])):
            if inv[i] != truth[i]:
                problems.append(
                    f"{kind}[{i}] {a756[kind][i]}: invert+補正={inv[i]} "
                    f"真値={truth[i]}")
        check(f"D. {kind} invert+補正 = 名前の真値", problems)

    # --- E. 生成スクリプトの再現性 ---
    proc = subprocess.run(
        [sys.executable, str(TOOLS_DIR / "gen-756-tables.py")],
        capture_output=True, text=True, timeout=300)
    if proc.returncode != 0:
        print("[FAIL] E. 生成スクリプト gen-756-tables.py が失敗した")
        print(proc.stderr[-2000:])
        failures.append("E-756")
    else:
        gen_re = (data_dir / "gen756.txt").read_text(encoding="utf-8")
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
                    problems.append(f"{name} が RegistryTables756.java に無い")
                    continue
            else:
                gen_table = [tuple(map(int, t)) for t in re.findall(
                    r"\{(-?\d+), (-?\d+)\}", m.group(2))]
                try:
                    java_table = parse_int_table(java, name)
                except SystemExit:
                    problems.append(f"{name} が RegistryTables756.java に無い")
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
                problems.append(f"{name} が RegistryTables756.java に無い")
                continue
            if gen_table != java_table:
                problems.append(
                    f"{name} が違う：生成={len(gen_table)} 件、Java={len(java_table)} 件")
        m = re.search(r"String\[\] (ATTRIBUTE_756_NAMES) = \{(.*?)\n    \};",
                      gen_re, re.DOTALL)
        if m:
            n_int += 1
            gen_names = re.findall(r'"([^"]+)"', m.group(2))
            try:
                java_names_list = parse_string_array(java, m.group(1))
            except SystemExit:
                problems.append("ATTRIBUTE_756_NAMES が RegistryTables756.java に無い")
                java_names_list = None
            if java_names_list is not None and gen_names != java_names_list:
                problems.append("ATTRIBUTE_756_NAMES が違う")
        print(f"E. 生成出力の表: {n_int} 件")
        check("E. 生成スクリプト gen-756-tables.py の再現性", problems)

    # --- G. 属性・エンチャント・バイオーム・ダメージ種別 ---
    # 属性：13 件の恒等写像。名前は registries.json の protocol_id 順。
    reg17 = load_json(data_dir / "registries-1.17.1.json")
    reg18 = load_json(data_dir / "registries-1.18.json")
    attrs17 = registry_order(reg17, "minecraft:attribute")
    attrs18 = registry_order(reg18, "minecraft:attribute")
    for name, size in (("ATTRIBUTE_756", len(attrs18)),
                       ("ATTRIBUTE_REVERSE_756", len(attrs17))):
        ranges = parse_int3_table(java, name + "_RANGES")
        overrides = parse_int_table(java, name + "_OVERRIDES")
        expanded = expand(ranges, overrides, size, -1)
        problems = [f"{name}[{i}] Java={expanded[i]} 想定={i}"
                    for i in range(size) if expanded[i] != i]
        check(f"G. {name} 表 = 恒等写像", problems)
    java_attrs = parse_string_array(java, "ATTRIBUTE_756_NAMES")
    problems = []
    if java_attrs != attrs17:
        problems.append("ATTRIBUTE_756_NAMES が registries-1.17.1.json と違う")
    if attrs17 != attrs18:
        problems.append("1.17.1 と 1.18 の属性の順序が違う")
    check("G. ATTRIBUTE_756_NAMES = registries.json の順序", problems)
    # エンチャント：1.17.1 と 1.18 は 38 件・同一順序のため恒等写像。
    ench17 = [e["name"] for e in load_json(data_dir / "enchantments-1.17.json")]
    ench18 = [e["name"] for e in load_json(data_dir / "enchantments-1.18.json")]
    truth_ench = {i: (ench17.index(n) if n in ench17 else -1)
                  for i, n in enumerate(ench18)}
    ranges = parse_int3_table(java, "ENCHANTMENT_756_RANGES")
    overrides = parse_int_table(java, "ENCHANTMENT_756_OVERRIDES")
    expanded = expand(ranges, overrides, len(ench18), -1)
    problems = [f"enchantments[{i}] {ench18[i]}: Java={expanded[i]} "
                f"真値={truth_ench[i]}"
                for i in range(len(ench18)) if expanded[i] != truth_ench[i]]
    check("G. ENCHANTMENT_756 表 = minecraft-data の順序", problems)
    truth_ench_rev = {i: (ench18.index(n) if n in ench18 else -1)
                      for i, n in enumerate(ench17)}
    ranges = parse_int3_table(java, "ENCHANTMENT_REVERSE_756_RANGES")
    overrides = parse_int_table(java, "ENCHANTMENT_REVERSE_756_OVERRIDES")
    expanded = expand(ranges, overrides, len(ench17), -1)
    problems = [f"enchantments[{i}] {ench17[i]}: Java={expanded[i]} "
                f"真値={truth_ench_rev[i]}"
                for i in range(len(ench17)) if expanded[i] != truth_ench_rev[i]]
    check("G. ENCHANTMENT_REVERSE_756 表 = minecraft-data の順序", problems)
    # バイオーム：biomes.json の id と改名 11件・代替 6件（順向き）、
    # 削除 26件（反転）と一致。反転表は 756 側 176 件（id 0〜175）。
    biomes17_by_id = {e["id"]: e["name"]
                      for e in load_json(data_dir / "biomes-1.17.json")}
    biomes18 = [e["name"] for e in sorted(
        load_json(data_dir / "biomes-1.18.json"), key=lambda e: e["id"])]
    max_bio17 = max(biomes17_by_id)
    biomes17_list = [biomes17_by_id.get(i) for i in range(max_bio17 + 1)]
    biomes17_name_to_id = {n: i for i, n in biomes17_by_id.items()}
    biomes18_name_to_id = {n: i for i, n in enumerate(biomes18)}
    truth_bio = name_truth(
        biomes18, biomes17_list, "biomes", -1,
        rename=genmod.BIOME_RENAME_756)
    unmatched = [i for i, v in truth_bio.items() if v < 0]
    expected_new = sorted(biomes18_name_to_id[n]
                          for n in genmod.BIOME_FALLBACK_756)
    if unmatched != expected_new:
        print(f"[FAIL] G. バイオーム順向きの未対応が想定と違う：{unmatched} "
              f"!= {expected_new}")
        failures.append("G-biome-forward-unmatched")
    else:
        for i in unmatched:
            truth_bio[i] = biomes17_name_to_id[
                genmod.BIOME_FALLBACK_756[biomes18[i]]]
    ranges = parse_int3_table(java, "BIOME_756_RANGES")
    overrides = parse_int_table(java, "BIOME_756_OVERRIDES")
    expanded = expand(ranges, overrides, len(biomes18), -1)
    problems = [f"biomes[{i}] {biomes18[i]}: Java={expanded[i]} 真値={truth_bio[i]}"
                for i in range(len(biomes18)) if expanded[i] != truth_bio[i]]
    check("G. BIOME_756 表 = biomes.json の順序", problems)
    reverse_rename = {v: k for k, v in genmod.BIOME_RENAME_756.items()}
    truth_bio_rev = name_truth(
        biomes17_list, biomes18, "biomes", 1,
        rename=reverse_rename)
    removed_ids = sorted(biomes17_name_to_id[n]
                         for n in genmod.REVERSE_BIOME_FALLBACK_756)
    still_default = sorted(i for i, name in enumerate(biomes17_list)
                           if name is not None and truth_bio_rev[i] == 1)
    # 平原（756 id 1）は 757 の平原（id 1）への恒等写像で、値が既定値の
    # 1 と一致する。既定値のままなのは削除 26件＋平原のはず。
    if still_default != sorted(set(removed_ids) | {1}):
        print(f"[FAIL] G. バイオーム反転の既定値が想定と違う：{still_default} "
              f"!= {removed_ids}")
        failures.append("G-biome-reverse-default")
    else:
        for i in removed_ids:
            truth_bio_rev[i] = biomes18_name_to_id[
                genmod.REVERSE_BIOME_FALLBACK_756[biomes17_list[i]]]
    ranges = parse_int3_table(java, "BIOME_756_TO_757_RANGES")
    overrides = parse_int_table(java, "BIOME_756_TO_757_OVERRIDES")
    expanded = expand(ranges, overrides, len(biomes17_list), 1)
    problems = [f"biomes[{i}] {biomes17_list[i]}: Java={expanded[i]} "
                f"真値={truth_bio_rev[i]}"
                for i in range(len(biomes17_list))
                if expanded[i] != truth_bio_rev[i]]
    check("G. BIOME_756_TO_757 表 = biomes.json の順序", problems)
    # ダメージ種別：762 の 42 件の恒等写像のプレースホルダ。
    for name in ("DAMAGE_TYPE_756", "DAMAGE_TYPE_756_TO_757"):
        ranges = parse_int3_table(java, name + "_RANGES")
        overrides = parse_int_table(java, name + "_OVERRIDES")
        expanded = expand(ranges, overrides, 42, -1)
        problems = [f"{name}[{i}] Java={expanded[i]} 想定={i}"
                    for i in range(42) if expanded[i] != i]
        if overrides:
            problems.append(f"{name} の OVERRIDES が空でない：{len(overrides)} 件")
        check(f"G. {name} 表 = 42 件の恒等写像", problems)

    # --- H. 音名表 ---
    # 757→756 は SOUND_FALLBACK_756 そのもの（757 の新音 13 件）。
    expected_757_to_756 = sorted(genmod.SOUND_FALLBACK_756.items())
    java_757_to_756 = parse_string_table(java, "SOUND_NAME_FALLBACKS_757_TO_756")
    problems = []
    if java_757_to_756 != expected_757_to_756:
        problems.append(f"件数が違う：Java={len(java_757_to_756)} "
                        f"想定={len(expected_757_to_756)}")
        for a, b in zip(java_757_to_756, expected_757_to_756):
            if a != b:
                problems.append(f"{a} != {b}")
    check("H. SOUND_NAME_FALLBACKS_757_TO_756 = SOUND_FALLBACK_756", problems)
    java_756 = parse_string_table(java, "SOUND_NAME_FALLBACKS_756")
    problems = []
    if java_756 != expected_757_to_756:
        problems.append("SOUND_NAME_FALLBACKS_756 が 757_TO_756 と違う")
    check("H. SOUND_NAME_FALLBACKS_756 = 757_TO_756 と同じ", problems)
    # 756→757 は名前の真値の反転表から作ったもの（0 件）。
    truth_sounds = name_truth(a756n["sounds"], a757n["sounds"], "sounds",
                              FB757["sounds"])
    expected_756_to_757 = sorted(
        (a756["sounds"][i], a757["sounds"][truth_sounds[i]])
        for i in range(len(a756["sounds"]))
        if a757["sounds"][truth_sounds[i]] != a756["sounds"][i])
    java_756_to_757 = parse_string_table(java, "SOUND_NAME_FALLBACKS_756_TO_757")
    problems = []
    if java_756_to_757 != expected_756_to_757:
        problems.append(f"Java={java_756_to_757} 想定={expected_756_to_757}")
    check("H. SOUND_NAME_FALLBACKS_756_TO_757 = 名前の真値の反転", problems)

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
