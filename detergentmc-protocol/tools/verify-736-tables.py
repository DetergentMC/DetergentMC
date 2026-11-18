#!/usr/bin/env python3
"""RegistryTables736.java の検証（チェック A〜H、736 版）。

雛形は verify-751-tables.py。736 は 751 の 1つ下で、751→736 の
隣接表だけを作る（736↔763〜775 は 736→751→753→754→755→756→757→759→760→
761→762→763→合成表の隣接連鎖で到達する）。そのため 751 版と同じくチェック C は
「合成表が無いこと」、D は「反転表＝invert(順向き)＋補正」の確認になる。

チェック：
  A  隣接表 *_736（751→736）：maps736.json と比較（7 件）
  B  反転表 *_736_TO_751：名前の真値と比較（7 件）
  C  合成表が無いこと：RegistryTables736.java の表の一覧が想定の
     50 件ちょうどで、合成表（*_76X_*／*_75X_* など）が無い（1 件）
  D  反転表＝invert(順向き)＋補正：RegistryRemapper.invert() と
     同じ決め方で順向き表を反転し、maps736-corrections.json の
     補正（ブロック状態 4 件・他は 0 件）を当てて名前の真値と
     一致することを確認する（7 件）。これが wiring の契約。
  E  gen-736-tables.py を再実行し、出力の全表が Java ファイルと
     一致することを確認する（1 件）
  F  ITEM_REVERSE_736：map_items_rev736.json と比較（1 件）
  G  属性・エンチャント・バイオーム・ダメージ種別：
     属性は 13 件の恒等写像で名前は registries.json の protocol_id
     順と一致、エンチャントは 38 件の恒等写像で順序は
     minecraft-data と一致、バイオームは biomes.json の id と
     751 側 79 件・id 0〜173、736 側 79 件・id 0〜173 と一致
     （両側とも同じ id・同じ名前。新規バイオームは無い。隙間 95 件）、
     ダメージ種別は 762 の 42 件の恒等写像のプレースホルダ
     （11 件）
  H  音名表 SOUND_NAME_FALLBACKS_*：751→736 は SOUND_FALLBACK_736
     そのもの、736→751 は名前の真値の反転表から作ったもの
     （0 件。改名は無い）、736 は
     751→736 と同じ（3 件）

合計：43 件（A 7＋B 7＋B' 7＋C 1＋D 7＋E 1＋F 1＋G 9＋H 3）。

使い方：リポジトリのルートで
    python3 detergentmc-protocol/tools/verify-736-tables.py

注意：順向き表（751→736）の名前の真値との直接の突き合わせは
しない（751 版と同じ設計。反転表側での独立検証に任せる。736 では
順向き表は非恒等（ブロック状態 12,381 件・アイテム 249 件・
エンティティ 47 件・音 405 件）だが、チェック A で
生成出力との一致を確認している）。反転表（736→751）に改名は
無い。ブロック状態の反転の真値は完全一致→既定値のプロパティを
足したもの→最小の id の優先順で決める（blockstate_reverse_truth）。
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
               / "io" / "detergentmc" / "protocol" / "RegistryTables736.java")
# ブロック状態の下向き表だけ別クラスに分けてある（<clinit> 64KB 対策。
# 773／774／775 の *BlockState.java と同じ方式）。検証は両ファイルの
# 表を合わせて読む。
TABLES_BLOCKSTATE_FILE = (REPO_ROOT / "detergentmc-protocol" / "src" / "main" / "java"
               / "io" / "detergentmc" / "protocol" / "RegistryTables736BlockState.java")

KINDS = ["blockstates", "items", "entities", "sounds", "particles",
         "blocks", "menus"]
KIND_TO_JAVA = {"blockstates": "BLOCK_STATE", "items": "ITEM",
                "entities": "ENTITY_TYPE", "sounds": "SOUND",
                "particles": "PARTICLE", "blocks": "BLOCK", "menus": "MENU"}

# 751 側の代替先（UP_736_FALLBACKS。gen-736-tables.py と同じ値）。
# ブタは 751 では 59、石の破壊音は 751 では 816、
# パーティクルは entity_effect=20、メニューは crafting=11。
FB751 = {"blockstates": 1, "items": 1, "entities": 59, "sounds": 816,
         "particles": 20, "blocks": 1, "menus": 11}

# パーティクルの反転の真値（736→751）。736 の 72 件はすべて 751 に
# 同名で存在するため代替は無い。
REVERSE_PARTICLE_FALLBACKS = {}

# 736→751 の改名は無い（751 と 736 のレジストリ内容は同一）。
REVERSE_RENAME_736 = {}


def load_json(path):
    with open(path, encoding="utf-8") as f:
        return json.load(f)


def load_gen_module():
    """gen-736-tables.py をモジュールとして読み、代替表の dict を使う。"""
    spec = importlib.util.spec_from_file_location(
        "gen736", TOOLS_DIR / "gen-736-tables.py")
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    return mod


def normalize_blockstates(states):
    """ブロック状態の文字列のプロパティをソートして正規化する
    （gen-736-tables.py と同じ。736 では順序の違いは無い）。"""
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
    736→751 の反転ではパーティクルの代替は無い（736 と 751 は
    同一順序のため）。バイオームの代替はこの関数では扱わず、呼び出し側で
    明示して埋める（736 では代替は 0 件）。
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
            # バイオーム表の隙間の id（1.16.1 に無い番号）。
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


def blockstate_reverse_truth(source_states, target_states):
    """736→751 のブロック状態の反転の真値（名前ベース）。

    751 の状態は 736 の状態にプロパティを 1 個足したもの（chain の axis、
    lantern／soul_lantern の waterlogged）か、完全一致のどちらか。
    優先順：完全一致 → 足したプロパティが既定値（waterlogged=false）のもの
    → 同名でプロパティが部分集合のもののうち最小の id。
    これは generate-remap-tables.py の build_inverse と同じ優先順
    （同名優先 → relaxed 優先 → 初出）で、実装は独立に書いてある。
    """
    relaxed = {"waterlogged": "false"}
    by_exact = {}
    for i, n in enumerate(target_states):
        by_exact.setdefault(n, i)
    cands = {}
    for i, n in enumerate(target_states):
        cands.setdefault(block_name(n), []).append(i)
    out = {}
    for i, s in enumerate(source_states):
        if s in by_exact:
            out[i] = by_exact[s]
            continue
        props = block_props(s)
        best_relaxed = None
        best_any = None
        for j in cands.get(block_name(s), []):
            tp = block_props(target_states[j])
            if not set(props.items()) <= set(tp.items()):
                continue
            extra = {k: v for k, v in tp.items() if k not in props}
            if best_any is None:
                best_any = j
            if all(k in relaxed and v == relaxed[k] for k, v in extra.items()):
                if best_relaxed is None:
                    best_relaxed = j
        if best_relaxed is not None:
            out[i] = best_relaxed
        elif best_any is not None:
            out[i] = best_any
        else:
            out[i] = 1  # stone。ここに来る 736 の状態は無いはず
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
    if not TABLES_BLOCKSTATE_FILE.exists():
        print(f"ファイルがない: {TABLES_BLOCKSTATE_FILE}", file=sys.stderr)
        return 1
    java = (TABLES_FILE.read_text(encoding="utf-8") + "\n"
            + TABLES_BLOCKSTATE_FILE.read_text(encoding="utf-8"))
    genmod = load_gen_module()
    a736 = load_json(data_dir / "vv-1.16.1.json")
    a751 = load_json(data_dir / "vv-1.16.2.json")
    a736n = dict(a736)
    a751n = dict(a751)
    a736n["blockstates"] = normalize_blockstates(a736["blockstates"])
    a751n["blockstates"] = normalize_blockstates(a751["blockstates"])
    maps736 = load_json(data_dir / "maps736.json")
    maps736_rev = load_json(data_dir / "maps736-reverse.json")
    corrections = load_json(data_dir / "maps736-corrections.json")
    rev736 = load_json(data_dir / "map_items_rev736.json")
    gen736 = (data_dir / "gen736.txt").read_text(encoding="utf-8")

    failures = []

    def check(label, problems):
        if problems:
            failures.append(label)
            print(f"[FAIL] {label}: {len(problems)} 件")
            for p in problems[:20]:
                print(f"    {p}")
        else:
            print(f"[OK] {label}")

    # --- A. *_736 表が生成スクリプトの出力と一致する ---
    for kind in KINDS:
        ranges = parse_int3_table(java, KIND_TO_JAVA[kind] + "_736_RANGES")
        overrides = parse_int_table(java, KIND_TO_JAVA[kind] + "_736_OVERRIDES")
        expanded = expand(ranges, overrides, len(a751[kind]), FB751[kind])
        gen = {int(k): v for k, v in maps736[kind].items()}
        problems = [f"{kind}[{i}] Java={expanded[i]} 生成={gen[i]}"
                    for i in range(len(a751[kind]))
                    if expanded[i] != gen[i]]
        check(f"A. {kind}_736 表 = 生成出力", problems)

    # --- F. ITEM_REVERSE_736 表が生成スクリプトの出力と一致する ---
    ranges = parse_int3_table(java, "ITEM_REVERSE_736_RANGES")
    overrides = parse_int_table(java, "ITEM_REVERSE_736_OVERRIDES")
    expanded = expand(ranges, overrides, len(a736["items"]), 1)
    gen = {int(k): v for k, v in rev736.items()}
    problems = [f"items[{i}] Java={expanded[i]} 生成={gen[i]}"
                for i in range(len(a736["items"]))
                if expanded[i] != gen[i]]
    check("F. ITEM_REVERSE_736 表 = 生成出力", problems)

    # --- B. *_736_TO_751 表が 736→751 の名前の真値と一致する ---
    # 736→751 に改名は無い。パーティクルの代替は無い。ブロック状態だけ
    # 専用の真値関数を使う（751 側はプロパティを 1 個多く持つため）。
    for kind in KINDS:
        if kind == "items":
            ranges = parse_int3_table(java, "ITEM_REVERSE_736_RANGES")
            overrides = parse_int_table(java, "ITEM_REVERSE_736_OVERRIDES")
            up = expand(ranges, overrides, len(a736["items"]), 1)
        else:
            ranges = parse_int3_table(
                java, KIND_TO_JAVA[kind] + "_736_TO_751_RANGES")
            overrides = parse_int_table(
                java, KIND_TO_JAVA[kind] + "_736_TO_751_OVERRIDES")
            up = expand(ranges, overrides, len(a736[kind]), FB751[kind])
        if kind == "blockstates":
            truth = blockstate_reverse_truth(a736n[kind], a751n[kind])
        else:
            truth = name_truth(
                a736n[kind], a751n[kind], kind, FB751[kind],
                rename=REVERSE_RENAME_736,
                particle_fallbacks=REVERSE_PARTICLE_FALLBACKS
                if kind == "particles" else None)
        problems = []
        for i in range(len(a736[kind])):
            if up[i] != truth[i]:
                problems.append(
                    f"{kind}[{i}] {a736[kind][i]}: 反転={up[i]} 真値={truth[i]}")
        check(f"B. {kind} 736→751 反転 = 名前の真値", problems)
        # 生成出力の反転表（maps736-reverse.json）も同じ真値のはず。
        gen_rev = {int(k): v for k, v in maps736_rev[kind].items()}
        problems = [f"{kind}[{i}] 生成={gen_rev[i]} 真値={truth[i]}"
                    for i in range(len(a736[kind]))
                    if gen_rev[i] != truth[i]]
        check(f"B'. {kind} 生成の反転表 = 名前の真値", problems)

    # --- C. 合成表が無いこと（表の一覧が想定の 50 件ちょうど） ---
    found_tables = re.findall(
        r"(?:int\[\]\[\]|String\[\]\[\]|String\[\]) (\w+) = \{", java)
    expected = set()
    for kind in KINDS:
        expected.add(KIND_TO_JAVA[kind] + "_736_RANGES")
        expected.add(KIND_TO_JAVA[kind] + "_736_OVERRIDES")
        expected.add(KIND_TO_JAVA[kind] + "_736_TO_751_RANGES")
        expected.add(KIND_TO_JAVA[kind] + "_736_TO_751_OVERRIDES")
    expected |= {"ITEM_REVERSE_736_RANGES", "ITEM_REVERSE_736_OVERRIDES",
                 "SOUND_NAME_FALLBACKS_736", "SOUND_NAME_FALLBACKS_751_TO_736",
                 "SOUND_NAME_FALLBACKS_736_TO_751",
                 "ATTRIBUTE_736_RANGES", "ATTRIBUTE_736_OVERRIDES",
                 "ATTRIBUTE_REVERSE_736_RANGES",
                 "ATTRIBUTE_REVERSE_736_OVERRIDES", "ATTRIBUTE_736_NAMES",
                 "ENCHANTMENT_736_RANGES", "ENCHANTMENT_736_OVERRIDES",
                 "ENCHANTMENT_REVERSE_736_RANGES",
                 "ENCHANTMENT_REVERSE_736_OVERRIDES",
                 "BIOME_736_RANGES", "BIOME_736_OVERRIDES",
                 "BIOME_736_TO_751_RANGES", "BIOME_736_TO_751_OVERRIDES",
                 "DAMAGE_TYPE_736_RANGES", "DAMAGE_TYPE_736_OVERRIDES",
                 "DAMAGE_TYPE_736_TO_751_RANGES",
                 "DAMAGE_TYPE_736_TO_751_OVERRIDES"}
    problems = []
    if set(found_tables) != expected:
        problems.append(f"表の一覧が違う：余分={sorted(set(found_tables) - expected)} "
                        f"不足={sorted(expected - set(found_tables))}")
    if len(found_tables) != 50:
        problems.append(f"表が {len(found_tables)} 件（想定 50 件）")
    check("C. 合成表が無く表は 50 件ちょうど", problems)

    # --- D. 反転表＝invert(順向き)＋補正 ---
    for kind in KINDS:
        ranges = parse_int3_table(java, KIND_TO_JAVA[kind] + "_736_RANGES")
        overrides = parse_int_table(java, KIND_TO_JAVA[kind] + "_736_OVERRIDES")
        corr = [tuple(c) for c in corrections[kind]]
        inv = invert_java(ranges, overrides, len(a751[kind]),
                          len(a736[kind]), FB751[kind], corr)
        if kind == "blockstates":
            truth = blockstate_reverse_truth(a736n[kind], a751n[kind])
        else:
            truth = name_truth(
                a736n[kind], a751n[kind], kind, FB751[kind],
                rename=REVERSE_RENAME_736,
                particle_fallbacks=REVERSE_PARTICLE_FALLBACKS
                if kind == "particles" else None)
        problems = []
        for i in range(len(a736[kind])):
            if inv[i] != truth[i]:
                problems.append(
                    f"{kind}[{i}] {a736[kind][i]}: invert+補正={inv[i]} "
                    f"真値={truth[i]}")
        check(f"D. {kind} invert+補正 = 名前の真値", problems)

    # --- E. 生成スクリプトの再現性 ---
    proc = subprocess.run(
        [sys.executable, str(TOOLS_DIR / "gen-736-tables.py")],
        capture_output=True, text=True, timeout=300)
    if proc.returncode != 0:
        print("[FAIL] E. 生成スクリプト gen-736-tables.py が失敗した")
        print(proc.stderr[-2000:])
        failures.append("E-736")
    else:
        gen_re = (data_dir / "gen736.txt").read_text(encoding="utf-8")
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
                    problems.append(f"{name} が RegistryTables736.java に無い")
                    continue
            else:
                gen_table = [tuple(map(int, t)) for t in re.findall(
                    r"\{(-?\d+), (-?\d+)\}", m.group(2))]
                try:
                    java_table = parse_int_table(java, name)
                except SystemExit:
                    problems.append(f"{name} が RegistryTables736.java に無い")
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
                problems.append(f"{name} が RegistryTables736.java に無い")
                continue
            if gen_table != java_table:
                problems.append(
                    f"{name} が違う：生成={len(gen_table)} 件、Java={len(java_table)} 件")
        m = re.search(r"String\[\] (ATTRIBUTE_736_NAMES) = \{(.*?)\n    \};",
                      gen_re, re.DOTALL)
        if m:
            n_int += 1
            gen_names = re.findall(r'"([^"]+)"', m.group(2))
            try:
                java_names_list = parse_string_array(java, m.group(1))
            except SystemExit:
                problems.append("ATTRIBUTE_736_NAMES が RegistryTables736.java に無い")
                java_names_list = None
            if java_names_list is not None and gen_names != java_names_list:
                problems.append("ATTRIBUTE_736_NAMES が違う")
        print(f"E. 生成出力の表: {n_int} 件")
        check("E. 生成スクリプト gen-736-tables.py の再現性", problems)

    # --- G. 属性・エンチャント・バイオーム・ダメージ種別 ---
    # 属性：13 件の恒等写像。名前は registries.json の protocol_id 順。
    reg736 = load_json(data_dir / "registries-1.16.1.json")
    reg751 = load_json(data_dir / "registries-1.16.2.json")
    attrs17 = registry_order(reg736, "minecraft:attribute")
    attrs18 = registry_order(reg751, "minecraft:attribute")
    for name, size in (("ATTRIBUTE_736", len(attrs18)),
                       ("ATTRIBUTE_REVERSE_736", len(attrs17))):
        ranges = parse_int3_table(java, name + "_RANGES")
        overrides = parse_int_table(java, name + "_OVERRIDES")
        expanded = expand(ranges, overrides, size, -1)
        problems = [f"{name}[{i}] Java={expanded[i]} 想定={i}"
                    for i in range(size) if expanded[i] != i]
        check(f"G. {name} 表 = 恒等写像", problems)
    java_attrs = parse_string_array(java, "ATTRIBUTE_736_NAMES")
    problems = []
    if java_attrs != attrs17:
        problems.append("ATTRIBUTE_736_NAMES が registries-1.16.1.json と違う")
    if attrs17 != attrs18:
        problems.append("1.16.1 と 1.16.2 の属性の順序が違う")
    check("G. ATTRIBUTE_736_NAMES = registries.json の順序", problems)
    # エンチャント：1.16.1 と 1.16.2 は 38 件・同一順序のため恒等写像。
    ench736 = [e["name"] for e in load_json(data_dir / "enchantments-1.16.1.json")]
    ench751 = [e["name"] for e in load_json(data_dir / "enchantments-1.16.2.json")]
    truth_ench = {i: (ench736.index(n) if n in ench736 else -1)
                  for i, n in enumerate(ench751)}
    ranges = parse_int3_table(java, "ENCHANTMENT_736_RANGES")
    overrides = parse_int_table(java, "ENCHANTMENT_736_OVERRIDES")
    expanded = expand(ranges, overrides, len(ench751), -1)
    problems = [f"enchantments[{i}] {ench751[i]}: Java={expanded[i]} "
                f"真値={truth_ench[i]}"
                for i in range(len(ench751)) if expanded[i] != truth_ench[i]]
    check("G. ENCHANTMENT_736 表 = minecraft-data の順序", problems)
    truth_ench_rev = {i: (ench751.index(n) if n in ench751 else -1)
                      for i, n in enumerate(ench736)}
    ranges = parse_int3_table(java, "ENCHANTMENT_REVERSE_736_RANGES")
    overrides = parse_int_table(java, "ENCHANTMENT_REVERSE_736_OVERRIDES")
    expanded = expand(ranges, overrides, len(ench736), -1)
    problems = [f"enchantments[{i}] {ench736[i]}: Java={expanded[i]} "
                f"真値={truth_ench_rev[i]}"
                for i in range(len(ench736)) if expanded[i] != truth_ench_rev[i]]
    check("G. ENCHANTMENT_REVERSE_736 表 = minecraft-data の順序", problems)
    # バイオーム：biomes.json の id と一致。順向きは 751 側 174 件
    # （id 0〜173）、反転は 736 側 174 件（id 0〜173）のワイヤ id 空間の表。
    # 0〜173 は同じ id・同じ名前。両側とも同じ 79 件で新規バイオームは無い。
    # 隙間 95 件も平原で埋める。
    biomes736_by_id = {e["id"]: e["name"]
                       for e in load_json(data_dir / "biomes-1.16.1.json")}
    max_bio736 = max(biomes736_by_id)
    biomes736_list = [biomes736_by_id.get(i) for i in range(max_bio736 + 1)]
    biomes751_by_id = {e["id"]: e["name"]
                       for e in load_json(data_dir / "biomes-1.16.json")}
    max_bio751 = max(biomes751_by_id)
    biomes751_list = [biomes751_by_id.get(i) for i in range(max_bio751 + 1)]
    biomes736_name_to_id = {n: i for i, n in biomes736_by_id.items()}
    biomes751_name_to_id = {n: i for i, n in biomes751_by_id.items()}
    truth_bio = name_truth(
        biomes751_list, biomes736_list, "biomes", -1,
        rename=genmod.BIOME_RENAME_736)
    # 隙間の id は名前の真値が -1 になるが、Java 表では平原（1）で埋める
    # （gen-736-tables.py と同じ規則）。
    for i, v in truth_bio.items():
        if v < 0:
            truth_bio[i] = 1
    ranges = parse_int3_table(java, "BIOME_736_RANGES")
    overrides = parse_int_table(java, "BIOME_736_OVERRIDES")
    expanded = expand(ranges, overrides, len(biomes751_list), -1)
    problems = [f"biomes[{i}] {biomes751_list[i]}: Java={expanded[i]} 真値={truth_bio[i]}"
                for i in range(len(biomes751_list)) if expanded[i] != truth_bio[i]]
    check("G. BIOME_736 表 = biomes.json の順序", problems)
    reverse_rename = {v: k for k, v in genmod.BIOME_RENAME_736.items()}
    truth_bio_rev = name_truth(
        biomes736_list, biomes751_list, "biomes", 1,
        rename=reverse_rename)
    removed_ids = sorted(biomes736_name_to_id[n]
                         for n in genmod.REVERSE_BIOME_FALLBACK_736)
    still_default = sorted(i for i, name in enumerate(biomes736_list)
                           if name is not None and truth_bio_rev[i] == 1)
    # 平原（736 id 1）は 751 の平原（id 1）への恒等写像で、値が既定値の
    # 1 と一致する。既定値のままなのは平原だけのはず。
    if still_default != sorted(set(removed_ids) | {1}):
        print(f"[FAIL] G. バイオーム反転の既定値が想定と違う：{still_default} "
              f"!= {removed_ids}")
        failures.append("G-biome-reverse-default")
    else:
        for i in removed_ids:
            truth_bio_rev[i] = biomes751_name_to_id[
                genmod.REVERSE_BIOME_FALLBACK_736[biomes736_list[i]]]
    ranges = parse_int3_table(java, "BIOME_736_TO_751_RANGES")
    overrides = parse_int_table(java, "BIOME_736_TO_751_OVERRIDES")
    expanded = expand(ranges, overrides, len(biomes736_list), 1)
    problems = [f"biomes[{i}] {biomes736_list[i]}: Java={expanded[i]} "
                f"真値={truth_bio_rev[i]}"
                for i in range(len(biomes736_list))
                if expanded[i] != truth_bio_rev[i]]
    check("G. BIOME_736_TO_751 表 = biomes.json の順序", problems)
    # ダメージ種別：762 の 42 件の恒等写像のプレースホルダ。
    for name in ("DAMAGE_TYPE_736", "DAMAGE_TYPE_736_TO_751"):
        ranges = parse_int3_table(java, name + "_RANGES")
        overrides = parse_int_table(java, name + "_OVERRIDES")
        expanded = expand(ranges, overrides, 42, -1)
        problems = [f"{name}[{i}] Java={expanded[i]} 想定={i}"
                    for i in range(42) if expanded[i] != i]
        if overrides:
            problems.append(f"{name} の OVERRIDES が空でない：{len(overrides)} 件")
        check(f"G. {name} 表 = 42 件の恒等写像", problems)

    # --- H. 音名表 ---
    # 751→736 は SOUND_FALLBACK_736 そのもの（piglin_brute 系 7件。改名は無い）。
    expected_751_to_736 = sorted(genmod.SOUND_FALLBACK_736.items())
    java_751_to_736 = parse_string_table(java, "SOUND_NAME_FALLBACKS_751_TO_736")
    problems = []
    if java_751_to_736 != expected_751_to_736:
        problems.append(f"件数が違う：Java={len(java_751_to_736)} "
                        f"想定={len(expected_751_to_736)}")
        for a, b in zip(java_751_to_736, expected_751_to_736):
            if a != b:
                problems.append(f"{a} != {b}")
    check("H. SOUND_NAME_FALLBACKS_751_TO_736 = SOUND_FALLBACK_736", problems)
    java_736 = parse_string_table(java, "SOUND_NAME_FALLBACKS_736")
    problems = []
    if java_736 != expected_751_to_736:
        problems.append("SOUND_NAME_FALLBACKS_736 が 751_TO_736 と違う")
    check("H. SOUND_NAME_FALLBACKS_736 = 751_TO_736 と同じ", problems)
    # 736→751 は名前の真値の反転表から作ったもの（0 件。改名は無い）。
    truth_sounds = name_truth(a736n["sounds"], a751n["sounds"], "sounds",
                              FB751["sounds"], rename=REVERSE_RENAME_736)
    expected_736_to_751 = sorted(
        (a736["sounds"][i], a751["sounds"][truth_sounds[i]])
        for i in range(len(a736["sounds"]))
        if a751["sounds"][truth_sounds[i]] != a736["sounds"][i])
    java_736_to_751 = parse_string_table(java, "SOUND_NAME_FALLBACKS_736_TO_751")
    problems = []
    if java_736_to_751 != expected_736_to_751:
        problems.append(f"Java={java_736_to_751} 想定={expected_736_to_751}")
    check("H. SOUND_NAME_FALLBACKS_736_TO_751 = 名前の真値の反転", problems)

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
