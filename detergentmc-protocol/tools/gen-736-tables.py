#!/usr/bin/env python3
"""736（1.16.1）のレジストリ対応表（751→736）の生成。

751の前例（gen-751-tables.py）にならう。736は751の1つ下で、751の
隣接として表を直接作る（777のときの776問題と同じ構図で、同じ方針(b)。
758は他人の作業中のため飛ばす）。合成表は作らない。736↔763〜775 は
736→751→755→756→757→759→760→761→762→763→合成表の隣接連鎖で到達する。

結論（実測）：1.16.1 と 1.16.2 のレジストリは**違う**（1.16.2 で追加された
piglin_brute 系が 1.16.1 には無い。build-1.16.1-inputs.py で確認。
registries-1.16.1.json ≠ registries-1.16.2.json、blocks-1.16.1-reports.json ≠
blocks-1.16.2-reports.json）。そのため 751→736 の写像は恒等写像ではなく、
削除されたものの代替が必要になる。推測で済ませず、このスクリプトで
パイプライン（build_pair／build_inverse／invert＋補正）を全て実行して
確認する。代替表は下記の通り（代替使用はアイテム1件・エンティティ1件・
音7件。ブロック状態はプロパティ落としで解決し代替表は使わない）。

使い方：
  python3 tools/gen-736-tables.py [--data-dir DIR]

入力（detergentmc-protocol/build/remap-data/。build-1.16.1-inputs.py
で vv-1.16.1.json を実バニラの registries／reports の順序で確認済み）：
  - vv-1.16.1.json … 補正済み ViaVersion mapping-1.16.json（生 vv は
    1.16.1 の registries／reports と全件一致し補正は不要だった。
    blockstates 17104・blocks 763・items 975・entities 107・
    sounds 985・particles 72・menus 24・attributes 13・
    blockentities 33・enchantments 38）
  - vv-1.16.2.json … 751 側（build-1.16.2-inputs.py で確認済み。
    blockstates 17112・items 976・entities 108・sounds 992）
  - 1.16.1_blocks.json … minecraft-data pc/1.16.1 の blocks.json
    （1.16.1 の dataPaths でも blocks は pc/1.16.1 を指す。実測）
  - registries-1.16.1.json／registries-1.16.2.json … 実バニラの
    reports/registries.json（**バイト同一ではない**。piglin_brute 系の差分あり）
  - enchantments-1.16.1.json … pc/1.16.4 順（38 件。dataPaths の
    pc/1.13.2 は 34 件で stale のため使わない。build-1.16.1-inputs.py 参照）
  - enchantments-1.16.2.json … minecraft-data pc/1.16.4（751 側）
  - biomes-1.16.1.json … minecraft-data pc/1.16.1（79 件・id 0〜173。
    1.16.1 の dataPaths でも biomes は pc/1.16.1 を指す。751 側は
    biomes-1.16.json＝pc/1.16.2 で同じ 79 件・id 0〜173）

出力（すべて --data-dir）：
  - gen736.txt … Java の表の宣言全文（insert-736-tables.py が読む）
  - maps736.json … 751→736 の対応表（7種＋reverse_items）
  - map_items_rev736.json … 736→751 のアイテム逆変換表
  - maps736-reverse.json … 736→751 の対応表（名前の真値。
    build_inverse で作る。751→736 が非恒等のため invert() とは
    一致しない箇所があり、差分は maps736-corrections.json に記録する）
  - maps736-corrections.json … invert() の結果と名前の真値の差分
    （wiring で invert() に渡す補正。ブロック状態と音で発生する見込み）

対応の決め方（generate-remap-tables.py の build_pair と同じ）：
  1. 名前（ブロック状態は名前＋プロパティ全体）がそのままある → その ID。
  2. 751 にあって 736 に無いものは代替表で解決する：
     - アイテム：piglin_brute_spawn_egg → piglin_spawn_egg（1件）
     - エンティティ：piglin_brute → piglin（1件）
     - 音：piglin_brute 系 7件 → piglin 系（parrot.imitate は
       entity.parrot.imitate.piglin、converted_to_zombified は
       entity.piglin.converted_to_zombified）
     - ブロック状態：751 の chain[axis=*]（6状態）→ 736 の
       chain[waterlogged=*]（axis を落とす。build_blockstates の
       プロパティ落としで解決）。lantern／soul_lantern の
       [hanging=*,waterlogged=*]（各4状態）→ [hanging=*]（waterlogged
       を落とす）。代替表は使わない。
  3. それでも決まらないものがあれば「未対応」として一覧を出し、
     終了コード 1 で失敗する（黙って推測しない）。

1.16.2→1.16.1 で確認した差分（名前ベース。実バニラ jar の data-generator
レポート（registries-1.16.1.json／blocks-1.16.1-reports.json）と
registries-1.16.2.json／blocks-1.16.2-reports.json、ViaVersion mapping、
minecraft-data で実測）：
  - ブロック状態 17,112→17,104（差し引き 8件。751 のみに 14状態
    （chain axis付き 6・lantern 4・soul_lantern 4）、736 のみに 6状態
    （chain waterloggedのみ 2・lantern hangingのみ 2・soul_lantern
    hangingのみ 2）。プロパティ落としで全件対応）
  - ブロック 763→763（同一。改名なし）
  - アイテム 976→975（piglin_brute_spawn_egg が 1.16.1 に無い。
    791番以降の順序が 1つずれる）
  - エンティティ 108→107（piglin_brute が 1.16.1 に無い。61番以降の
    順序が 1つずれる）
  - 音 992→985（piglin_brute 系 7件が 1.16.1 に無い。587番以降の
    順序が最大 7つずれる）
  - パーティクル 72→72（同一）
  - メニュー 24→24（同一順序）、属性 13→13（同一順序）、
    エンチャント 38→38（同一順序）、エフェクト 32→32（同一順序）
  - ブロックエンティティ 33→33（同一順序）
  - バイオーム 79→79（同一。0〜173 は同じ id・同じ名前。隙間 95 件。
    両側ともワイヤ id 空間は 0〜173 の 174 件）
  - ゲームイベント：1.16.1／1.16.2 とも無し。
    カスタム統計 74→74。
  - damage_type レジストリは 1.16.1／1.16.2 のどちらにも無い（1.19.4／
    762 で追加）。751 と同じく 762 の 42件の恒等写像を置くが、
    ワイヤ上の呼び出し元は無い。
  - data_component_type・environment_attribute・slot_display は
    1.16.1／1.16.2 のどちらにも無い（1.20.5 以降で追加）。
"""

import importlib.util
import json
import sys
from pathlib import Path

SCRIPT_DIR = Path(__file__).resolve().parent
REPO_ROOT = SCRIPT_DIR.parent.parent
DATA_DIR = REPO_ROOT / "detergentmc-protocol" / "build" / "remap-data"

_spec = importlib.util.spec_from_file_location(
    "grt", SCRIPT_DIR / "generate-remap-tables.py")
grt = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(grt)

load = grt.load
compress = grt.compress
java_int3 = grt.java_int3
java_int2 = grt.java_int2
java_string_array = grt.java_string_array
build_pair = grt.build_pair
build_name_map = grt.build_name_map
build_reverse_items = grt.build_reverse_items
build_inverse = grt.build_inverse
invert_java = grt.invert_java
block_name = grt.block_name
block_props = grt.block_props
same_props = grt.same_props


# 改名は無い（751 と 736 のレジストリ内容の差分は piglin_brute 系の
# 追加／削除だけで、改名は無い）。
RENAME_736 = {}

# 代替表。751 にあって 736 に無いものだけ（実バニラ registries で実測）。
# ブロック状態はプロパティ落としで解決するため BLOCK_FALLBACK_736 は空。
ITEM_FALLBACK_736 = {"piglin_brute_spawn_egg": "piglin_spawn_egg"}
BLOCK_FALLBACK_736 = {}
ENTITY_FALLBACK_736 = {"piglin_brute": "piglin"}
SOUND_FALLBACK_736 = {
    "entity.parrot.imitate.piglin_brute": "entity.parrot.imitate.piglin",
    "entity.piglin_brute.ambient": "entity.piglin.ambient",
    "entity.piglin_brute.angry": "entity.piglin.angry",
    "entity.piglin_brute.death": "entity.piglin.death",
    "entity.piglin_brute.hurt": "entity.piglin.hurt",
    "entity.piglin_brute.step": "entity.piglin.step",
    "entity.piglin_brute.converted_to_zombified": "entity.piglin.converted_to_zombified",
}
PARTICLE_FALLBACK_736 = {}

# バイオームの改名・代替は無い（両側とも 79 件で同じ id・同じ名前）。
BIOME_RENAME_736 = {}
BIOME_FALLBACK_736 = {}
REVERSE_BIOME_FALLBACK_736 = {}

# パーティクルの反転の代替は無い（736 の 72 件はすべて 751 に
# 同名で存在するため）。
REVERSE_PARTICLE_FALLBACK_736 = {}

KINDS = ["blockstates", "items", "entities", "sounds", "particles",
         "blocks", "menus"]
KIND_JAVA = {"blockstates": "BLOCK_STATE", "items": "ITEM",
             "entities": "ENTITY_TYPE", "sounds": "SOUND",
             "particles": "PARTICLE", "blocks": "BLOCK", "menus": "MENU"}


def normalize_blockstates(states):
    """ブロック状態の文字列のプロパティをソートして正規化する。

    751→736 ではブロック状態はプロパティ落としで対応するため正規化は
    変化を起こさないが、751 の前例と同じ段取りで通す（順序だけ違う
    状態が将来の隣接で混ざっても写像の index は変わらない）。
    """
    out = []
    for s in states:
        if "[" not in s:
            out.append(s)
            continue
        name, rest = s.split("[", 1)
        props = sorted(kv.split("=", 1) for kv in rest[:-1].split(","))
        out.append(name + "[" + ",".join(f"{k}={v}" for k, v in props) + "]")
    return out


def emit_map(out, java_name, mapping, size, source_tag="751"):
    iv, ov = compress(mapping, size)
    out.append(java_int3(java_name + "_RANGES", iv))
    out.append(java_int2(java_name + "_OVERRIDES", ov))
    out.append(f"    // {java_name}: {source_tag} entries={size} ranges={len(iv)} "
               f"overrides={len(ov)}")
    return iv, ov


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
    failures = []

    # ---  headline の確認：1.16.1 と 1.16.2 のレポートは違う（想定内）。
    # piglin_brute 系（アイテム1・エンティティ1・音7・ブロック状態の
    # 差し引き8）が 1.16.2 で追加されたため、バイト同一にはならない。---
    reg_bytes_736 = (data_dir / "registries-1.16.1.json").read_bytes()
    reg_bytes_751 = (data_dir / "registries-1.16.2.json").read_bytes()
    rep_bytes_736 = (data_dir / "blocks-1.16.1-reports.json").read_bytes()
    rep_bytes_751 = (data_dir / "blocks-1.16.2-reports.json").read_bytes()
    print(f"[736] registries-1.16.1 == registries-1.16.2（バイト同一）: "
          f"{reg_bytes_736 == reg_bytes_751}（違うのが想定内）")
    print(f"[736] blocks-1.16.1 == blocks-1.16.2（バイト同一）: "
          f"{rep_bytes_736 == rep_bytes_751}（違うのが想定内）")

    a736 = load(data_dir / "vv-1.16.1.json")
    a751 = load(data_dir / "vv-1.16.2.json")
    blocks116 = load(data_dir / "1.16.1_blocks.json")
    reg736 = load(data_dir / "registries-1.16.1.json")
    reg751 = load(data_dir / "registries-1.16.2.json")

    # 736 側と 751 側の ViaVersion mapping の各リストの差分を記録する。
    # 同一のはずの種（blocks・particles・menus・attributes・
    # blockentities・enchantments）は全件一致することを確認する。
    for key in ("blockstates", "blocks", "items", "entities", "sounds",
                "particles", "menus", "attributes", "blockentities",
                "enchantments"):
        if a736[key] == a751[key]:
            print(f"[736] vv {key}: 736 側と 751 側で {len(a736[key])} 件一致")
        else:
            only736 = sorted(set(a736[key]) - set(a751[key]))
            only751 = sorted(set(a751[key]) - set(a736[key]))
            print(f"[736] vv {key}: 736 側 {len(a736[key])} 件 vs 751 側 "
                  f"{len(a751[key])} 件、736のみ={only736}、751のみ={only751}")
            if key in ("blocks", "particles", "menus", "attributes",
                       "blockentities", "enchantments"):
                failures.append((f"vv-{key}-differs", None))
    if failures:
        return 1

    # 751側の代替先（UP_736_FALLBACKS）。名前から引く（751 の前例と同じ）。
    def idx(vv, kind, name):
        return vv[kind].index(name)

    up736_fallbacks = {
        "blockstates": 1,
        "items": 1,
        "entities": idx(a751, "entities", "pig"),
        "sounds": idx(a751, "sounds", "block.stone.break"),
        "particles": idx(a751, "particles", "entity_effect"),
        "blocks": 1,
        "menus": idx(a751, "menus", "crafting"),
    }
    print(f"[736] UP_736_FALLBACKS（751側）: {up736_fallbacks}")

    # ブロック状態のプロパティ順を正規化してから build_pair を呼ぶ。
    a736n = dict(a736)
    a751n = dict(a751)
    a736n["blockstates"] = normalize_blockstates(a736["blockstates"])
    a751n["blockstates"] = normalize_blockstates(a751["blockstates"])
    # build_pair は ITEM_FALLBACK（モジュールグローバル）をアイテムと
    # ブロックの代替に使うため、736版に差し替える。
    grt.ITEM_FALLBACK = ITEM_FALLBACK_736
    maps736, out736, fail736, rev736 = build_pair(
        "736", a736n, a751n, blocks116, data_dir,
        rename=RENAME_736, sound_fallbacks=SOUND_FALLBACK_736,
        particle_fallbacks=PARTICLE_FALLBACK_736,
        reverse_fallback=1, entity_fallbacks=ENTITY_FALLBACK_736,
        block_fallbacks=BLOCK_FALLBACK_736)
    failures += fail736
    if failures:
        print(f"\n未対応があります: {failures}", file=sys.stderr)
        return 1
    # build_pair はコメントに source バージョンを "765" と
    # ハードコードしている。736 の source は 751 のため書き換える。
    out736 = [line.replace(": 765 entries=", ": 751 entries=")
              for line in out736]

    # --- 順向き表の非恒等ぶりを確認する：blocks・particles・menus は
    # 恒等写像、blockstates・items・entities・sounds は piglin_brute 系の
    # 削除と順序ずれで非恒等のはず。---
    for kind in KINDS:
        diffs = [i for i in range(len(a751[kind]))
                 if maps736[kind].get(i) != i]
        print(f"[736] {kind}: 751 側 {len(a751[kind])} 件中、恒等でない "
              f"{len(diffs)} 件 {diffs[:5]}")
        if kind in ("blocks", "particles", "menus") and diffs:
            print(f"[736] {kind}: 恒等写像のはずが {len(diffs)} 件違う",
                  file=sys.stderr)
            failures.append((f"{kind}-not-identity", diffs[:5]))
        if kind in ("items", "entities", "sounds", "blockstates") and not diffs:
            print(f"[736] {kind}: 非恒等のはずが恒等写像になった",
                  file=sys.stderr)
            failures.append((f"{kind}-unexpected-identity", None))
    if failures:
        return 1

    # --- 代替で解決したブロック状態の監査：代替表が空のため 0 件 ---
    by_props16 = {}
    for i, s in enumerate(a736["blockstates"]):
        by_props16[(block_name(s), frozenset(block_props(s).items()))] = i
    audit_fail = []
    for i751, s751 in enumerate(a751["blockstates"]):
        name = block_name(s751)
        if name in BLOCK_FALLBACK_736:
            target, fn = BLOCK_FALLBACK_736[name]
            props = fn(block_props(s751)) if fn else {}
            if (target, frozenset(props.items())) not in by_props16:
                if fn is not None:
                    audit_fail.append((i751, s751, target, props))
    print(f"[736] blockstate fallback audit: {len(audit_fail)} 件の不整合 "
          f"{audit_fail[:5]}")
    if audit_fail:
        failures.append(("blockstate-fallback-audit", audit_fail[:5]))
        return 1

    out = ["    // Generated by tools/gen-736-tables.py. Do not edit by hand."]
    out += out736

    with open(data_dir / "maps736.json", "w", encoding="utf-8") as f:
        json.dump(maps736, f)
    with open(data_dir / "map_items_rev736.json", "w", encoding="utf-8") as f:
        json.dump({str(k): v for k, v in rev736.items()}, f)

    # --- 736→751 の反転表（名前の真値）。build_inverse は同名のものを
    # 優先するため、invert() の first-wins と違う場合がある。
    # 751→736 が非恒等のため両者は一致しない箇所がある。ブロック状態は
    # waterlogged=false（既定値）のものを優先する（751 と同じ段取り）。---
    reverse_truth = {}
    for kind in ("blockstates", "entities", "sounds", "particles",
                 "blocks", "menus"):
        relaxed = {"waterlogged": "false"} if kind == "blockstates" else None
        inv = build_inverse(maps736[kind], len(a751[kind]), len(a736[kind]),
                            a751[kind], a736[kind], relaxed)
        missing = [i for i, v in enumerate(inv) if v < 0]
        if missing:
            print(f"[736] reverse {kind}: 対応なし {len(missing)} 件 "
                  f"{missing[:5]}（代替先 {up736_fallbacks[kind]} で埋める）")
            inv = [v if v >= 0 else up736_fallbacks[kind] for v in inv]
        reverse_truth[kind] = {i: v for i, v in enumerate(inv)}
    # パーティクル：736 の 72 件はすべて 751 に同名で存在するため、
    # 反転表は名前の真値と一致するはず。barrier（id 2）は両方とも
    # そのまま写る。
    barrier_id = a736["particles"].index("barrier")
    if reverse_truth["particles"][barrier_id] != a751["particles"].index("barrier"):
        print(f"[736] reverse particles: barrier({barrier_id})="
              f"{reverse_truth['particles'][barrier_id]}（同名写像でない）",
              file=sys.stderr)
        failures.append(("particles-reverse-identity", None))
        return 1
    # アイテムの反転は build_reverse_items の結果（名前ベース）を使う。
    reverse_truth["items"] = dict(rev736)
    # 反転表が名前ベースの真値であることを確認する：736 側の各 id について、
    # 反転先の 751 側の名前が 736 側の名前と一致すること（ブロック状態は
    # ブロック名が一致し、736 側のプロパティが 751 側のプロパティの部分集合
    # であること）。blocks・particles・menus は恒等写像のはず。
    for kind in KINDS:
        bad = []
        for i736, i751 in reverse_truth[kind].items():
            if kind == "blockstates":
                if block_name(a751[kind][i751]) != block_name(a736[kind][i736]):
                    bad.append((i736, i751))
                elif not set(block_props(a736[kind][i736]).items()) <= set(block_props(a751[kind][i751]).items()):
                    bad.append((i736, i751))
            elif a751[kind][i751] != a736[kind][i736]:
                bad.append((i736, i751))
        if bad:
            print(f"[736] reverse {kind}: 名前の真値でない {len(bad)} 件 {bad[:5]}",
                  file=sys.stderr)
            failures.append((f"reverse-{kind}-name-mismatch", bad[:5]))
        if kind in ("blocks", "particles", "menus"):
            ident = {i: i for i in range(len(a736[kind]))}
            if reverse_truth[kind] != ident:
                diffs = [i for i in range(len(a736[kind]))
                         if reverse_truth[kind].get(i) != i]
                print(f"[736] reverse {kind}: 恒等写像のはずが {len(diffs)} 件違う",
                      file=sys.stderr)
                failures.append((f"reverse-{kind}-not-identity", diffs[:5]))
    if failures:
        return 1
    print("[736] 反転表は名前の真値と一致（blocks・particles・menus は恒等写像）")
    with open(data_dir / "maps736-reverse.json", "w", encoding="utf-8") as f:
        json.dump({k: {str(i): v for i, v in m.items()}
                   for k, m in reverse_truth.items()}, f)

    # --- invert() の結果と名前の真値の差分（補正）。wiring で
    # RegistryRemapper.invert() に渡す。751→736 が非恒等のため、
    # ブロック状態・アイテム・エンティティ・音で補正が発生する見込み。---
    corrections = {}
    for kind in KINDS:
        ranges, overrides = compress(maps736[kind], len(a751[kind]))
        inv_list = invert_java(ranges, overrides, len(a751[kind]),
                               len(a736[kind]), up736_fallbacks[kind])
        corr = [[y, reverse_truth[kind][y]] for y in range(len(a736[kind]))
                if inv_list[y] != reverse_truth[kind][y]]
        corrections[kind] = corr
        print(f"[736] corrections {kind}: {len(corr)} 件 {corr[:5]}")
    with open(data_dir / "maps736-corrections.json", "w", encoding="utf-8") as f:
        json.dump(corrections, f)

    # 反転表を Java の表として出す（*_736_TO_751_*）。
    for kind in KINDS:
        emit_map(out, f"{KIND_JAVA[kind]}_736_TO_751",
                 reverse_truth[kind], len(a736[kind]), source_tag="736")
    # ITEM_REVERSE_736 と ITEM_736_TO_751 が同じことを確認する。
    if reverse_truth["items"] != {i: v for i, v in enumerate(
            build_inverse(maps736["items"], len(a751["items"]),
                          len(a736["items"]), a751["items"],
                          a736["items"]))}:
        print("[736] ITEM_REVERSE_736 と ITEM_736_TO_751 が違う",
              file=sys.stderr)
        failures.append(("item-reverse-mismatch", None))
        return 1

    # --- 音の名前の表（Stop Sound 用）。751→736 は SOUND_FALLBACK_736
    # そのもの（piglin_brute 系 7件）。736→751 も名前の真値の反転表
    # から作る（改名が無いため空のはず）。---
    for table_name, table in (
            ("SOUND_NAME_FALLBACKS_736", SOUND_FALLBACK_736),
            ("SOUND_NAME_FALLBACKS_751_TO_736", SOUND_FALLBACK_736)):
        lines = [f"    private static final String[][] {table_name} = {{"]
        for k, v in sorted(table.items()):
            lines.append(f'            {{"{k}", "{v}"}},')
        lines.append("    };")
        out.append("\n".join(lines))
    table_736_to_751 = {}
    for id736, name736 in enumerate(a736["sounds"]):
        id751 = reverse_truth["sounds"][id736]
        name751 = a751["sounds"][id751]
        if name751 != name736:
            table_736_to_751[name736] = name751
    lines = ["    private static final String[][] SOUND_NAME_FALLBACKS_736_TO_751 = {"]
    for k, v in sorted(table_736_to_751.items()):
        lines.append(f'            {{"{k}", "{v}"}},')
    lines.append("    };")
    out.append("\n".join(lines))
    print(f"[736] sound name table 736->751: {len(table_736_to_751)} entries")

    # --- 属性の表（registries.json の protocol_id 順で作る。1.16.1／1.16.2
    # とも 13件・同一順序のため恒等写像）。1.16.1 の Update Attributes は
    # 属性のキーを文字列で送る（mc-1.16-protocol.json の
    # packet_entity_update_attributes は "name": "string"。数値の mapper
    # は後のバージョンで導入）ため、1.16.1 には照合する mapper が無い。
    # 情報源は registries.json の protocol_id 順だけ（1.16.1／1.16.2 で
    # 同一順序なのは上で確認済み）。---
    attrs736 = registry_order(reg736, "minecraft:attribute")
    attrs751 = registry_order(reg751, "minecraft:attribute")
    if attrs736 != attrs751:
        print("[736] attributes: 1.16.1 と 1.16.2 の順序が違う", file=sys.stderr)
        failures.append(("attributes-order", (attrs736, attrs751)))
        return 1
    attr_751_to_736 = {i: i for i in range(len(attrs751))}
    attr_736_to_751 = {i: i for i in range(len(attrs736))}
    iv, ov = compress(attr_751_to_736, len(attrs751))
    out.append(java_int3("ATTRIBUTE_736_RANGES", iv))
    out.append(java_int2("ATTRIBUTE_736_OVERRIDES", ov))
    out.append(f"    // ATTRIBUTE_736: 751 entries={len(attrs751)} "
               f"ranges={len(iv)} overrides={len(ov)}")
    iv, ov = compress(attr_736_to_751, len(attrs736))
    out.append(java_int3("ATTRIBUTE_REVERSE_736_RANGES", iv))
    out.append(java_int2("ATTRIBUTE_REVERSE_736_OVERRIDES", ov))
    out.append(f"    // ATTRIBUTE_REVERSE_736: 736 entries={len(attrs736)} "
               f"ranges={len(iv)} overrides={len(ov)}")
    out.append(java_string_array("ATTRIBUTE_736_NAMES", attrs736))
    print(f"[736] attributes: 751={len(attrs751)} 736={len(attrs736)} identity")

    # --- エンチャントの表。1.16.1 と 1.16.2 は 38件・同一順序のため恒等写像。
    # 736 側は enchantments-1.16.1.json（pc/1.16.4 順）を使う。dataPaths の
    # pc/1.13.2 は 34 件で stale のため使わない（build-1.16.1-inputs.py 参照）。---
    ench736 = [e["name"] for e in load(data_dir / "enchantments-1.16.1.json")]
    ench751 = [e["name"] for e in load(data_dir / "enchantments-1.16.2.json")]
    if ench736 != a736["enchantments"] or ench751 != a751["enchantments"]:
        print("[736] enchantments: minecraft-data と ViaVersion の順序が違う",
              file=sys.stderr)
        failures.append(("enchantments-order", None))
        return 1
    ench_751_to_736 = {i: (ench736.index(n) if n in ench736 else -1)
                       for i, n in enumerate(ench751)}
    ench_736_to_751 = {i: (ench751.index(n) if n in ench751 else -1)
                       for i, n in enumerate(ench736)}
    iv, ov = compress(ench_751_to_736, len(ench751))
    out.append(java_int3("ENCHANTMENT_736_RANGES", iv))
    out.append(java_int2("ENCHANTMENT_736_OVERRIDES", ov))
    out.append(f"    // ENCHANTMENT_736: 751 entries={len(ench751)} "
               f"ranges={len(iv)} overrides={len(ov)}")
    iv, ov = compress(ench_736_to_751, len(ench736))
    out.append(java_int3("ENCHANTMENT_REVERSE_736_RANGES", iv))
    out.append(java_int2("ENCHANTMENT_REVERSE_736_OVERRIDES", ov))
    out.append(f"    // ENCHANTMENT_REVERSE_736: 736 entries={len(ench736)} "
               f"ranges={len(iv)} overrides={len(ov)}")
    print(f"[736] enchantments: 751={len(ench751)} 736={len(ench736)} identity")

    # --- バイオームの表。minecraft-data の biomes.json の id を使う
    # （736 側は biomes-1.16.1.json＝pc/1.16.1、751 側は
    # biomes-1.16.json＝pc/1.16.2。両側とも 79 件・id 0〜173。0〜173 は同じ id・
    # 同じ名前。順向き・反転ともワイヤ id 空間で作る（751 の前例と同じ罠に注意。
    # build_name_map に None を含むリストを渡してはいけない）。
    # 順向き（751→736）は 751 側 174 件（id 0〜173）、反転（736→751）も
    # 736 側 174 件（id 0〜173）。隙間の id は平原（1）で埋める。---
    biomes736_by_id = {}
    for e in load(data_dir / "biomes-1.16.1.json"):
        biomes736_by_id[e["id"]] = e["name"]
    biomes751_by_id = {}
    for e in load(data_dir / "biomes-1.16.json"):
        biomes751_by_id[e["id"]] = e["name"]
    max_biome_751 = max(biomes751_by_id)
    max_biome_736 = max(biomes736_by_id)
    biomes751_list = [biomes751_by_id.get(i) for i in range(max_biome_751 + 1)]
    biomes736_list = [biomes736_by_id.get(i) for i in range(max_biome_736 + 1)]
    if (len([b for b in biomes736_list if b is not None]),
            len([b for b in biomes751_list if b is not None])) != (79, 79):
        print(f"[736] biomes: 件数が想定と違う "
              f"736={len([b for b in biomes736_list if b is not None])} "
              f"751={len([b for b in biomes751_list if b is not None])}",
              file=sys.stderr)
        failures.append(("biomes-count", None))
        return 1
    # 0〜173 は同じ id・同じ名前であることを確認する。
    shared_mismatch = [(i, biomes751_by_id.get(i), biomes736_by_id.get(i))
                       for i in range(max_biome_736 + 1)
                       if biomes751_by_id.get(i) != biomes736_by_id.get(i)]
    if shared_mismatch:
        print(f"[736] biomes: 0〜173 で id と名前が違うものがある：{shared_mismatch[:5]}",
              file=sys.stderr)
        failures.append(("biomes-shared", shared_mismatch[:5]))
        return 1
    m_bio = {w: (w if biomes736_by_id.get(w) is not None else 1)
             for w in range(max_biome_751 + 1)}
    emit_map(out, "BIOME_736", m_bio, len(biomes751_list), source_tag="751")
    rev_bio = build_inverse(m_bio, len(biomes751_list), len(biomes736_list),
                            biomes751_list, biomes736_list)
    biomes736_name_to_id = {n: i for i, n in biomes736_by_id.items()}
    biomes751_name_to_id = {n: i for i, n in biomes751_by_id.items()}
    patched_bio = 0
    for name736, name751 in REVERSE_BIOME_FALLBACK_736.items():
        id736 = biomes736_name_to_id[name736]
        id751 = biomes751_name_to_id[name751]
        if rev_bio[id736] < 0:
            rev_bio[id736] = id751
            patched_bio += 1
        elif rev_bio[id736] != id751:
            print(f"[736] biomes: 反転表の {name736}({id736}) が想定と違う："
                  f"{rev_bio[id736]} != {id751}", file=sys.stderr)
            failures.append(("biomes-reverse", (name736, rev_bio[id736], id751)))
            return 1
    print(f"[736] biomes reverse: 削除バイオームの明示 {patched_bio} 件")
    gaps = [i for i, b in enumerate(biomes736_list) if b is None]
    missing_bio = [i for i, v in enumerate(rev_bio)
                   if v < 0 and biomes736_list[i] is not None]
    if missing_bio:
        print(f"[736] biomes: 反転表に対応なしの実在バイオーム {missing_bio}",
              file=sys.stderr)
        failures.append(("biomes-reverse-missing", missing_bio))
        return 1
    rev_bio = [v if v >= 0 else 1 for v in rev_bio]
    print(f"[736] biomes reverse: 隙間の id {len(gaps)} 件は平原（751 id 1）で埋める")
    emit_map(out, "BIOME_736_TO_751",
             {i: v for i, v in enumerate(rev_bio)}, len(biomes736_list),
             source_tag="736")
    print(f"[736] biomes: 751={len([b for b in biomes751_list if b is not None])} "
          f"736={len(biomes736_by_id)} "
          f"(751 id 0〜{max_biome_751}、736 id 0〜{max_biome_736}、"
          f"736 側の隙間 {len(gaps)} 件)")

    # --- ダメージ種別（751 と同じ扱い）。damage_type レジストリは
    # 1.16.1／1.16.2 のどちらにも無い（1.19.4／762 で追加）。751 の前例
    # （RegistryTables751.java）にならい、762 の 42件の恒等写像を置くが
    # ワイヤ上の呼び出し元は無い。---
    dmg = {i: i for i in range(42)}
    emit_map(out, "DAMAGE_TYPE_736", dmg, 42, source_tag="751")
    emit_map(out, "DAMAGE_TYPE_736_TO_751", dmg, 42, source_tag="736")
    print("[736] damage_type: 762 の 42件の恒等写像（プレースホルダ）")

    # エフェクト（32→32、同一順序）は数値の表を作らない。エフェクトは
    # 751 の前例と同じく wiring 側で恒等写像にする（新規も削除も無いため
    # DROP は無い）。ブロックエンティティも 33→33 で同一順序のため、
    # 表は作らず wiring 側で恒等写像にする（751 と違い DROP は無い）。
    # ここでは件数だけ記録する。
    eff736 = registry_order(reg736, "minecraft:mob_effect")
    eff751 = registry_order(reg751, "minecraft:mob_effect")
    print(f"[736] effects: 751={len(eff751)} 736={len(eff736)} "
          f"identical={eff736 == eff751}")
    be736 = registry_order(reg736, "minecraft:block_entity_type")
    be751 = registry_order(reg751, "minecraft:block_entity_type")
    print(f"[736] block entities: 751={len(be751)} 736={len(be736)} "
          f"736-is-prefix={be736 == be751[:len(be736)]} "
          f"identical={be736 == be751} "
          f"751-only={sorted(set(be751) - set(be736))}")

    text = "\n".join(out) + "\n"
    (data_dir / "gen736.txt").write_text(text, encoding="utf-8")
    print(f"Java の表を {data_dir / 'gen736.txt'} に書き出しました。")
    print("完了：未対応 0 件。")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
