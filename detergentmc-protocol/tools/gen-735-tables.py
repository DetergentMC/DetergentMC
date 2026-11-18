#!/usr/bin/env python3
"""735（1.16）のレジストリ対応表（736→735）の生成。

736の前例（gen-736-tables.py）にならう。735は736の1つ下で、736の
隣接として表を直接作る（777のときの776問題と同じ構図で、同じ方針(b)。
758は他人の作業中のため飛ばす）。合成表は作らない。735↔763〜775 は
735→736→751→753→754→755→756→757→759→760→761→762→763→合成表の隣接連鎖で到達する。

結論（実測）：1.16 と 1.16.1 のレジストリは**同一**（build-1.16-inputs.py で確認。
registries-1.16.json と registries-1.16.1.json はバイト同一、
blocks-1.16-reports.json と blocks-1.16.1-reports.json もバイト同一。
1.16.1 はバグ修正版でレジストリ内容は変わっていない）。そのため
736→735 の写像は**全種恒等写像**で、代替は不要。推測で済ませず、
このスクリプトでパイプライン（build_pair／build_inverse／invert＋補正）を
全て実行して確認する。

使い方：
  python3 tools/gen-735-tables.py [--data-dir DIR]

入力（detergentmc-protocol/build/remap-data/。build-1.16-inputs.py
で vv-1.16.json を実バニラの registries／reports の順序で確認済み）：
  - vv-1.16.json … 補正済み ViaVersion mapping-1.16.json（生 vv は
    1.16 の registries／reports と全件一致し補正は不要だった。
    blockstates 17104・blocks 763・items 975・entities 107・
    sounds 985・particles 72・menus 24・attributes 13・
    blockentities 33・enchantments 38）
  - vv-1.16.1.json … 736 側（build-1.16.1-inputs.py で確認済み。
    blockstates 17104・items 975・entities 107・sounds 985。
    vv-1.16.json と内容同一）
  - 1.16.1_blocks.json … minecraft-data pc/1.16.1 の blocks.json
    （1.16 の dataPaths でも blocks は pc/1.16.1 を指す。実測。
    fresh ダウンロードとバイト同一を確認済み）
  - registries-1.16.json／registries-1.16.1.json … 実バニラの
    reports/registries.json（**バイト同一**。実測）
  - enchantments-1.16.json … pc/1.16.4 順（38 件。dataPaths の
    pc/1.13.2 は 34 件で stale のため使わない。build-1.16-inputs.py 参照）
  - enchantments-1.16.1.json … minecraft-data pc/1.16.4（736 側。
    enchantments-1.16.json とバイト同一）
  - biomes-1.16.json … minecraft-data pc/1.16.1（79 件・id 0〜173。
    1.16 の dataPaths でも biomes は pc/1.16.1 を指す。736 側は
    biomes-1.16.1.json＝pc/1.16.1 で同じ 79 件・id 0〜173。
    両ファイルはバイト同一）

出力（すべて --data-dir）：
  - gen735.txt … Java の表の宣言全文（insert-735-tables.py が読む）
  - maps735.json … 736→735 の対応表（7種＋reverse_items）
  - map_items_rev735.json … 735→736 のアイテム逆変換表
  - maps735-reverse.json … 735→736 の対応表（名前の真値。
    build_inverse で作る。736→735 が恒等写像のため invert() とも
    一致し、差分は maps735-corrections.json に記録する（0 件の見込み））
  - maps735-corrections.json … invert() の結果と名前の真値の差分
    （wiring で invert() に渡す補正。恒等写像のため 0 件の見込み）

対応の決め方（generate-remap-tables.py の build_pair と同じ）：
  1. 名前（ブロック状態は名前＋プロパティ全体）がそのままある → その ID。
  2. 736 にあって 735 に無いものは無い（レジストリ同一のため代替表は
     すべて空）。ブロック状態のプロパティ落としも不要。
  3. それでも決まらないものがあれば「未対応」として一覧を出し、
     終了コード 1 で失敗する（黙って推測しない）。

1.16.1→1.16 で確認した差分（名前ベース。実バニラ jar の data-generator
レポート（registries-1.16.json／blocks-1.16-reports.json）と
registries-1.16.1.json／blocks-1.16.1-reports.json、ViaVersion mapping、
minecraft-data で実測。両レポートはバイト同一）：
  - ブロック状態 17,104→17,104（同一）
  - ブロック 763→763（同一。改名なし）
  - アイテム 975→975（同一）
  - エンティティ 107→107（同一）
  - 音 985→985（同一）
  - パーティクル 72→72（同一）
  - メニュー 24→24（同一順序）、属性 13→13（同一順序）、
    エンチャント 38→38（同一順序）、エフェクト 32→32（同一順序）
  - ブロックエンティティ 33→33（同一順序）
  - バイオーム 79→79（同一。0〜173 は同じ id・同じ名前。隙間 95 件。
    両側ともワイヤ id 空間は 0〜173 の 174 件）
  - ゲームイベント：1.16／1.16.1 とも無し。
    カスタム統計 74→74。
  - damage_type レジストリは 1.16／1.16.1 のどちらにも無い（1.19.4／
    762 で追加）。736 と同じく 762 の 42件の恒等写像を置くが、
    ワイヤ上の呼び出し元は無い。
  - data_component_type・environment_attribute・slot_display は
    1.16／1.16.1 のどちらにも無い（1.20.5 以降で追加）。
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


# 改名は無い（736 と 735 のレジストリは同一で、改名は無い）。
RENAME_735 = {}

# 代替表。736 と 735 のレジストリは同一のためすべて空。
ITEM_FALLBACK_735 = {}
BLOCK_FALLBACK_735 = {}
ENTITY_FALLBACK_735 = {}
SOUND_FALLBACK_735 = {}
PARTICLE_FALLBACK_735 = {}

# バイオームの改名・代替は無い（両側とも 79 件で同じ id・同じ名前）。
BIOME_RENAME_735 = {}
BIOME_FALLBACK_735 = {}
REVERSE_BIOME_FALLBACK_735 = {}

# パーティクルの反転の代替は無い（735 の 72 件はすべて 736 に
# 同名で存在するため）。
REVERSE_PARTICLE_FALLBACK_735 = {}

KINDS = ["blockstates", "items", "entities", "sounds", "particles",
         "blocks", "menus"]
KIND_JAVA = {"blockstates": "BLOCK_STATE", "items": "ITEM",
             "entities": "ENTITY_TYPE", "sounds": "SOUND",
             "particles": "PARTICLE", "blocks": "BLOCK", "menus": "MENU"}


def normalize_blockstates(states):
    """ブロック状態の文字列のプロパティをソートして正規化する。

    736→735 ではブロック状態はプロパティ落としで対応するため正規化は
    変化を起こさないが、736 の前例と同じ段取りで通す（順序だけ違う
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


def emit_map(out, java_name, mapping, size, source_tag="736"):
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

    # --- headline の確認：1.16 と 1.16.1 のレポートはバイト同一（実測）。
    # 1.16.1 はバグ修正版でレジストリ内容は変わっていないため、
    # 同一でない場合は想定外で失敗する。---
    reg_bytes_735 = (data_dir / "registries-1.16.json").read_bytes()
    reg_bytes_736 = (data_dir / "registries-1.16.1.json").read_bytes()
    rep_bytes_735 = (data_dir / "blocks-1.16-reports.json").read_bytes()
    rep_bytes_736 = (data_dir / "blocks-1.16.1-reports.json").read_bytes()
    print(f"[735] registries-1.16 == registries-1.16.1（バイト同一）: "
          f"{reg_bytes_735 == reg_bytes_736}（同一が想定）")
    print(f"[735] blocks-1.16 == blocks-1.16.1（バイト同一）: "
          f"{rep_bytes_735 == rep_bytes_736}（同一が想定）")
    if reg_bytes_735 != reg_bytes_736 or rep_bytes_735 != rep_bytes_736:
        print("[735] レポートが 1.16.1 と違う（想定外）", file=sys.stderr)
        failures.append(("reports-differ-from-1.16.1", None))
        return 1

    a735 = load(data_dir / "vv-1.16.json")
    a736 = load(data_dir / "vv-1.16.1.json")
    blocks116 = load(data_dir / "1.16.1_blocks.json")
    reg735 = load(data_dir / "registries-1.16.json")
    reg736 = load(data_dir / "registries-1.16.1.json")

    # 735 側と 736 側の ViaVersion mapping の各リストの差分を記録する。
    # 同一のはずの種（blocks・particles・menus・attributes・
    # blockentities・enchantments）は全件一致することを確認する。
    for key in ("blockstates", "blocks", "items", "entities", "sounds",
                "particles", "menus", "attributes", "blockentities",
                "enchantments"):
        if a735[key] == a736[key]:
            print(f"[735] vv {key}: 735 側と 736 側で {len(a735[key])} 件一致")
        else:
            only735 = sorted(set(a735[key]) - set(a736[key]))
            only736 = sorted(set(a736[key]) - set(a735[key]))
            print(f"[735] vv {key}: 735 側 {len(a735[key])} 件 vs 736 側 "
                  f"{len(a736[key])} 件、735のみ={only735}、736のみ={only736}")
            if key in ("blocks", "particles", "menus", "attributes",
                       "blockentities", "enchantments"):
                failures.append((f"vv-{key}-differs", None))
    if failures:
        return 1

    # 736側の代替先（UP_735_FALLBACKS）。名前から引く（736 の前例と同じ）。
    def idx(vv, kind, name):
        return vv[kind].index(name)

    up735_fallbacks = {
        "blockstates": 1,
        "items": 1,
        "entities": idx(a736, "entities", "pig"),
        "sounds": idx(a736, "sounds", "block.stone.break"),
        "particles": idx(a736, "particles", "entity_effect"),
        "blocks": 1,
        "menus": idx(a736, "menus", "crafting"),
    }
    print(f"[735] UP_735_FALLBACKS（736側）: {up735_fallbacks}")

    # ブロック状態のプロパティ順を正規化してから build_pair を呼ぶ。
    a735n = dict(a735)
    a736n = dict(a736)
    a735n["blockstates"] = normalize_blockstates(a735["blockstates"])
    a736n["blockstates"] = normalize_blockstates(a736["blockstates"])
    # build_pair は ITEM_FALLBACK（モジュールグローバル）をアイテムと
    # ブロックの代替に使うため、735版に差し替える。
    grt.ITEM_FALLBACK = ITEM_FALLBACK_735
    maps735, out735, fail735, rev735 = build_pair(
        "735", a735n, a736n, blocks116, data_dir,
        rename=RENAME_735, sound_fallbacks=SOUND_FALLBACK_735,
        particle_fallbacks=PARTICLE_FALLBACK_735,
        reverse_fallback=1, entity_fallbacks=ENTITY_FALLBACK_735,
        block_fallbacks=BLOCK_FALLBACK_735)
    failures += fail735
    if failures:
        print(f"\n未対応があります: {failures}", file=sys.stderr)
        return 1
    # build_pair はコメントに source バージョンを "765" と
    # ハードコードしている。735 の source は 736 のため書き換える。
    out735 = [line.replace(": 765 entries=", ": 736 entries=")
              for line in out735]

    # --- 順向き表の確認：736→735 は全種恒等写像のはず（レジストリ同一）。---
    for kind in KINDS:
        diffs = [i for i in range(len(a736[kind]))
                 if maps735[kind].get(i) != i]
        print(f"[735] {kind}: 736 側 {len(a736[kind])} 件中、恒等でない "
              f"{len(diffs)} 件 {diffs[:5]}")
        if diffs:
            print(f"[735] {kind}: 恒等写像のはずが {len(diffs)} 件違う",
                  file=sys.stderr)
            failures.append((f"{kind}-not-identity", diffs[:5]))
    if failures:
        return 1

    # --- 代替で解決したブロック状態の監査：代替表が空のため 0 件 ---
    by_props16 = {}
    for i, s in enumerate(a735["blockstates"]):
        by_props16[(block_name(s), frozenset(block_props(s).items()))] = i
    audit_fail = []
    for i736, s736 in enumerate(a736["blockstates"]):
        name = block_name(s736)
        if name in BLOCK_FALLBACK_735:
            target, fn = BLOCK_FALLBACK_735[name]
            props = fn(block_props(s736)) if fn else {}
            if (target, frozenset(props.items())) not in by_props16:
                if fn is not None:
                    audit_fail.append((i736, s736, target, props))
    print(f"[735] blockstate fallback audit: {len(audit_fail)} 件の不整合 "
          f"{audit_fail[:5]}")
    if audit_fail:
        failures.append(("blockstate-fallback-audit", audit_fail[:5]))
        return 1

    out = ["    // Generated by tools/gen-735-tables.py. Do not edit by hand."]
    out += out735

    with open(data_dir / "maps735.json", "w", encoding="utf-8") as f:
        json.dump(maps735, f)
    with open(data_dir / "map_items_rev735.json", "w", encoding="utf-8") as f:
        json.dump({str(k): v for k, v in rev735.items()}, f)

    # --- 735→736 の反転表（名前の真値）。build_inverse は同名のものを
    # 優先するため、invert() の first-wins と違う場合がある。
    # 736→735 が恒等写像のため両者は一致するはず。ブロック状態は
    # waterlogged=false（既定値）のものを優先する（736 と同じ段取り）。---
    reverse_truth = {}
    for kind in ("blockstates", "entities", "sounds", "particles",
                 "blocks", "menus"):
        relaxed = {"waterlogged": "false"} if kind == "blockstates" else None
        inv = build_inverse(maps735[kind], len(a736[kind]), len(a735[kind]),
                            a736[kind], a735[kind], relaxed)
        missing = [i for i, v in enumerate(inv) if v < 0]
        if missing:
            print(f"[735] reverse {kind}: 対応なし {len(missing)} 件 "
                  f"{missing[:5]}（代替先 {up735_fallbacks[kind]} で埋める）")
            inv = [v if v >= 0 else up735_fallbacks[kind] for v in inv]
        reverse_truth[kind] = {i: v for i, v in enumerate(inv)}
    # パーティクル：735 の 72 件はすべて 736 に同名で存在するため、
    # 反転表は名前の真値と一致するはず。barrier（id 2）は両方とも
    # そのまま写る。
    barrier_id = a735["particles"].index("barrier")
    if reverse_truth["particles"][barrier_id] != a736["particles"].index("barrier"):
        print(f"[735] reverse particles: barrier({barrier_id})="
              f"{reverse_truth['particles'][barrier_id]}（同名写像でない）",
              file=sys.stderr)
        failures.append(("particles-reverse-identity", None))
        return 1
    # アイテムの反転は build_reverse_items の結果（名前ベース）を使う。
    reverse_truth["items"] = dict(rev735)
    # 反転表が名前ベースの真値であることを確認する：735 側の各 id について、
    # 反転先の 736 側の名前が 735 側の名前と一致すること（ブロック状態は
    # ブロック名が一致し、735 側のプロパティが 736 側のプロパティの部分集合
    # であること）。blocks・particles・menus は恒等写像のはず。
    for kind in KINDS:
        bad = []
        for i735, i736 in reverse_truth[kind].items():
            if kind == "blockstates":
                if block_name(a736[kind][i736]) != block_name(a735[kind][i735]):
                    bad.append((i735, i736))
                elif not set(block_props(a735[kind][i735]).items()) <= set(block_props(a736[kind][i736]).items()):
                    bad.append((i735, i736))
            elif a736[kind][i736] != a735[kind][i735]:
                bad.append((i735, i736))
        if bad:
            print(f"[735] reverse {kind}: 名前の真値でない {len(bad)} 件 {bad[:5]}",
                  file=sys.stderr)
            failures.append((f"reverse-{kind}-name-mismatch", bad[:5]))
        if kind in ("blocks", "particles", "menus"):
            ident = {i: i for i in range(len(a735[kind]))}
            if reverse_truth[kind] != ident:
                diffs = [i for i in range(len(a735[kind]))
                         if reverse_truth[kind].get(i) != i]
                print(f"[735] reverse {kind}: 恒等写像のはずが {len(diffs)} 件違う",
                      file=sys.stderr)
                failures.append((f"reverse-{kind}-not-identity", diffs[:5]))
    if failures:
        return 1
    print("[735] 反転表は名前の真値と一致（blocks・particles・menus は恒等写像）")
    with open(data_dir / "maps735-reverse.json", "w", encoding="utf-8") as f:
        json.dump({k: {str(i): v for i, v in m.items()}
                   for k, m in reverse_truth.items()}, f)

    # --- invert() の結果と名前の真値の差分（補正）。wiring で
    # RegistryRemapper.invert() に渡す。736→735 が非恒等のため、
    # ブロック状態・アイテム・エンティティ・音で補正が発生する見込み。---
    corrections = {}
    for kind in KINDS:
        ranges, overrides = compress(maps735[kind], len(a736[kind]))
        inv_list = invert_java(ranges, overrides, len(a736[kind]),
                               len(a735[kind]), up735_fallbacks[kind])
        corr = [[y, reverse_truth[kind][y]] for y in range(len(a735[kind]))
                if inv_list[y] != reverse_truth[kind][y]]
        corrections[kind] = corr
        print(f"[735] corrections {kind}: {len(corr)} 件 {corr[:5]}")
    with open(data_dir / "maps735-corrections.json", "w", encoding="utf-8") as f:
        json.dump(corrections, f)

    # 反転表を Java の表として出す（*_735_TO_736_*）。
    for kind in KINDS:
        emit_map(out, f"{KIND_JAVA[kind]}_735_TO_736",
                 reverse_truth[kind], len(a735[kind]), source_tag="735")
    # ITEM_REVERSE_735 と ITEM_735_TO_736 が同じことを確認する。
    if reverse_truth["items"] != {i: v for i, v in enumerate(
            build_inverse(maps735["items"], len(a736["items"]),
                          len(a735["items"]), a736["items"],
                          a735["items"]))}:
        print("[735] ITEM_REVERSE_735 と ITEM_735_TO_736 が違う",
              file=sys.stderr)
        failures.append(("item-reverse-mismatch", None))
        return 1

    # --- 音の名前の表（Stop Sound 用）。736→735 は SOUND_FALLBACK_735
    # そのもの（レジストリ同一のため空）。735→736 も名前の真値の反転表
    # から作る（改名が無いため空のはず）。---
    for table_name, table in (
            ("SOUND_NAME_FALLBACKS_735", SOUND_FALLBACK_735),
            ("SOUND_NAME_FALLBACKS_736_TO_735", SOUND_FALLBACK_735)):
        lines = [f"    private static final String[][] {table_name} = {{"]
        for k, v in sorted(table.items()):
            lines.append(f'            {{"{k}", "{v}"}},')
        lines.append("    };")
        out.append("\n".join(lines))
    table_735_to_736 = {}
    for id735, name735 in enumerate(a735["sounds"]):
        id736 = reverse_truth["sounds"][id735]
        name736 = a736["sounds"][id736]
        if name736 != name735:
            table_735_to_736[name735] = name736
    lines = ["    private static final String[][] SOUND_NAME_FALLBACKS_735_TO_736 = {"]
    for k, v in sorted(table_735_to_736.items()):
        lines.append(f'            {{"{k}", "{v}"}},')
    lines.append("    };")
    out.append("\n".join(lines))
    print(f"[735] sound name table 735->736: {len(table_735_to_736)} entries")

    # --- 属性の表（registries.json の protocol_id 順で作る。1.16／1.16.1
    # とも 13件・同一順序のため恒等写像）。1.16 の Update Attributes は
    # 属性のキーを文字列で送る（mc-1.16-protocol.json の
    # packet_entity_update_attributes は "name": "string"。数値の mapper
    # は後のバージョンで導入）ため、1.16 には照合する mapper が無い。
    # 情報源は registries.json の protocol_id 順だけ（1.16／1.16.1 で
    # 同一順序なのは上で確認済み）。---
    attrs735 = registry_order(reg735, "minecraft:attribute")
    attrs736 = registry_order(reg736, "minecraft:attribute")
    if attrs735 != attrs736:
        print("[735] attributes: 1.16 と 1.16.1 の順序が違う", file=sys.stderr)
        failures.append(("attributes-order", (attrs735, attrs736)))
        return 1
    attr_736_to_735 = {i: i for i in range(len(attrs736))}
    attr_735_to_736 = {i: i for i in range(len(attrs735))}
    iv, ov = compress(attr_736_to_735, len(attrs736))
    out.append(java_int3("ATTRIBUTE_735_RANGES", iv))
    out.append(java_int2("ATTRIBUTE_735_OVERRIDES", ov))
    out.append(f"    // ATTRIBUTE_735: 736 entries={len(attrs736)} "
               f"ranges={len(iv)} overrides={len(ov)}")
    iv, ov = compress(attr_735_to_736, len(attrs735))
    out.append(java_int3("ATTRIBUTE_REVERSE_735_RANGES", iv))
    out.append(java_int2("ATTRIBUTE_REVERSE_735_OVERRIDES", ov))
    out.append(f"    // ATTRIBUTE_REVERSE_735: 735 entries={len(attrs735)} "
               f"ranges={len(iv)} overrides={len(ov)}")
    out.append(java_string_array("ATTRIBUTE_735_NAMES", attrs735))
    print(f"[735] attributes: 736={len(attrs736)} 735={len(attrs735)} identity")

    # --- エンチャントの表。1.16 と 1.16.1 は 38件・同一順序のため恒等写像。
    # 735 側は enchantments-1.16.json（pc/1.16.4 順）を使う。dataPaths の
    # pc/1.13.2 は 34 件で stale のため使わない（build-1.16-inputs.py 参照）。---
    ench735 = [e["name"] for e in load(data_dir / "enchantments-1.16.json")]
    ench736 = [e["name"] for e in load(data_dir / "enchantments-1.16.1.json")]
    if ench735 != a735["enchantments"] or ench736 != a736["enchantments"]:
        print("[735] enchantments: minecraft-data と ViaVersion の順序が違う",
              file=sys.stderr)
        failures.append(("enchantments-order", None))
        return 1
    ench_736_to_735 = {i: (ench735.index(n) if n in ench735 else -1)
                       for i, n in enumerate(ench736)}
    ench_735_to_736 = {i: (ench736.index(n) if n in ench736 else -1)
                       for i, n in enumerate(ench735)}
    iv, ov = compress(ench_736_to_735, len(ench736))
    out.append(java_int3("ENCHANTMENT_735_RANGES", iv))
    out.append(java_int2("ENCHANTMENT_735_OVERRIDES", ov))
    out.append(f"    // ENCHANTMENT_735: 736 entries={len(ench736)} "
               f"ranges={len(iv)} overrides={len(ov)}")
    iv, ov = compress(ench_735_to_736, len(ench735))
    out.append(java_int3("ENCHANTMENT_REVERSE_735_RANGES", iv))
    out.append(java_int2("ENCHANTMENT_REVERSE_735_OVERRIDES", ov))
    out.append(f"    // ENCHANTMENT_REVERSE_735: 735 entries={len(ench735)} "
               f"ranges={len(iv)} overrides={len(ov)}")
    print(f"[735] enchantments: 736={len(ench736)} 735={len(ench735)} identity")

    # --- バイオームの表。minecraft-data の biomes.json の id を使う
    # （735 側は biomes-1.16.json＝pc/1.16.1、736 側は
    # biomes-1.16.1.json＝pc/1.16.1。両側とも 79 件・id 0〜173。0〜173 は同じ id・
    # 同じ名前。順向き・反転ともワイヤ id 空間で作る（736 の前例と同じ罠に注意。
    # build_name_map に None を含むリストを渡してはいけない）。
    # 順向き（736→735）は 736 側 174 件（id 0〜173）、反転（735→736）も
    # 735 側 174 件（id 0〜173）。隙間の id は平原（1）で埋める。---
    biomes735_by_id = {}
    for e in load(data_dir / "biomes-1.16.json"):
        biomes735_by_id[e["id"]] = e["name"]
    biomes736_by_id = {}
    for e in load(data_dir / "biomes-1.16.1.json"):
        biomes736_by_id[e["id"]] = e["name"]
    max_biome_736 = max(biomes736_by_id)
    max_biome_735 = max(biomes735_by_id)
    biomes736_list = [biomes736_by_id.get(i) for i in range(max_biome_736 + 1)]
    biomes735_list = [biomes735_by_id.get(i) for i in range(max_biome_735 + 1)]
    if (len([b for b in biomes735_list if b is not None]),
            len([b for b in biomes736_list if b is not None])) != (79, 79):
        print(f"[735] biomes: 件数が想定と違う "
              f"735={len([b for b in biomes735_list if b is not None])} "
              f"736={len([b for b in biomes736_list if b is not None])}",
              file=sys.stderr)
        failures.append(("biomes-count", None))
        return 1
    # 0〜173 は同じ id・同じ名前であることを確認する。
    shared_mismatch = [(i, biomes736_by_id.get(i), biomes735_by_id.get(i))
                       for i in range(max_biome_735 + 1)
                       if biomes736_by_id.get(i) != biomes735_by_id.get(i)]
    if shared_mismatch:
        print(f"[735] biomes: 0〜173 で id と名前が違うものがある：{shared_mismatch[:5]}",
              file=sys.stderr)
        failures.append(("biomes-shared", shared_mismatch[:5]))
        return 1
    m_bio = {w: (w if biomes735_by_id.get(w) is not None else 1)
             for w in range(max_biome_736 + 1)}
    emit_map(out, "BIOME_735", m_bio, len(biomes736_list), source_tag="736")
    rev_bio = build_inverse(m_bio, len(biomes736_list), len(biomes735_list),
                            biomes736_list, biomes735_list)
    biomes735_name_to_id = {n: i for i, n in biomes735_by_id.items()}
    biomes736_name_to_id = {n: i for i, n in biomes736_by_id.items()}
    patched_bio = 0
    for name735, name736 in REVERSE_BIOME_FALLBACK_735.items():
        id735 = biomes735_name_to_id[name735]
        id736 = biomes736_name_to_id[name736]
        if rev_bio[id735] < 0:
            rev_bio[id735] = id736
            patched_bio += 1
        elif rev_bio[id735] != id736:
            print(f"[735] biomes: 反転表の {name735}({id735}) が想定と違う："
                  f"{rev_bio[id735]} != {id736}", file=sys.stderr)
            failures.append(("biomes-reverse", (name735, rev_bio[id735], id736)))
            return 1
    print(f"[735] biomes reverse: 削除バイオームの明示 {patched_bio} 件")
    gaps = [i for i, b in enumerate(biomes735_list) if b is None]
    missing_bio = [i for i, v in enumerate(rev_bio)
                   if v < 0 and biomes735_list[i] is not None]
    if missing_bio:
        print(f"[735] biomes: 反転表に対応なしの実在バイオーム {missing_bio}",
              file=sys.stderr)
        failures.append(("biomes-reverse-missing", missing_bio))
        return 1
    rev_bio = [v if v >= 0 else 1 for v in rev_bio]
    print(f"[735] biomes reverse: 隙間の id {len(gaps)} 件は平原（736 id 1）で埋める")
    emit_map(out, "BIOME_735_TO_736",
             {i: v for i, v in enumerate(rev_bio)}, len(biomes735_list),
             source_tag="735")
    print(f"[735] biomes: 736={len([b for b in biomes736_list if b is not None])} "
          f"735={len(biomes735_by_id)} "
          f"(736 id 0〜{max_biome_736}、735 id 0〜{max_biome_735}、"
          f"735 側の隙間 {len(gaps)} 件)")

    # --- ダメージ種別（736 と同じ扱い）。damage_type レジストリは
    # 1.16／1.16.1 のどちらにも無い（1.19.4／762 で追加）。736 の前例
    # （RegistryTables736.java）にならい、762 の 42件の恒等写像を置くが
    # ワイヤ上の呼び出し元は無い。---
    dmg = {i: i for i in range(42)}
    emit_map(out, "DAMAGE_TYPE_735", dmg, 42, source_tag="736")
    emit_map(out, "DAMAGE_TYPE_735_TO_736", dmg, 42, source_tag="735")
    print("[735] damage_type: 762 の 42件の恒等写像（プレースホルダ）")

    # エフェクト（32→32、同一順序）は数値の表を作らない。エフェクトは
    # 736 の前例と同じく wiring 側で恒等写像にする（新規も削除も無いため
    # DROP は無い）。ブロックエンティティも 33→33 で同一順序のため、
    # 表は作らず wiring 側で恒等写像にする（736 と違い DROP は無い）。
    # ここでは件数だけ記録する。
    eff735 = registry_order(reg735, "minecraft:mob_effect")
    eff736 = registry_order(reg736, "minecraft:mob_effect")
    print(f"[735] effects: 736={len(eff736)} 735={len(eff735)} "
          f"identical={eff735 == eff736}")
    be735 = registry_order(reg735, "minecraft:block_entity_type")
    be736 = registry_order(reg736, "minecraft:block_entity_type")
    print(f"[735] block entities: 736={len(be736)} 735={len(be735)} "
          f"735-is-prefix={be735 == be736[:len(be735)]} "
          f"identical={be735 == be736} "
          f"736-only={sorted(set(be736) - set(be735))}")

    text = "\n".join(out) + "\n"
    (data_dir / "gen735.txt").write_text(text, encoding="utf-8")
    print(f"Java の表を {data_dir / 'gen735.txt'} に書き出しました。")
    print("完了：未対応 0 件。")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
