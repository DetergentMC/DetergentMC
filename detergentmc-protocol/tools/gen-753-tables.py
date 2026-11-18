#!/usr/bin/env python3
"""753（1.16.3）のレジストリ対応表（754→753）の生成。

754の前例（gen-754-tables.py）にならう。753は754の1つ下で、754の
隣接として表を直接作る（777のときの776問題と同じ構図で、同じ方針(b)。
758は他人の作業中のため飛ばす）。合成表は作らない。753↔763〜775 は
753→754→755→756→757→759→760→761→762→763→合成表の隣接連鎖で到達する。

結論（実測）：1.16.3 と 1.16.5 のレジストリはバイト単位で完全一致する
（build-1.16.3-inputs.py で確認。registries-1.16.3.json＝
registries-1.16.5.json、blocks-1.16.3-reports.json＝
blocks-1.16.5-reports.json）。そのため 754→753 の写像は 7 種すべて
恒等写像になる。ただし推測で済ませず、このスクリプトでパイプライン
（build_pair／build_inverse／invert＋補正）を全て実行して確認する。
代替表は空（代替使用 0 件・補正 0 件）。代替表の選択が要らないため、
設計上の判断も無い。

使い方：
  python3 tools/gen-753-tables.py [--data-dir DIR]

入力（detergentmc-protocol/build/remap-data/。build-1.16.3-inputs.py
で vv-1.16.3.json を実バニラの registries／reports の順序で確認済み）：
  - vv-1.16.3.json … vv-1.16.json とバイト同一（sha1
    a2ab1b7e72ad8ea83f3212dce8db9e9af7010beb）。補正済み ViaVersion
    mapping-1.16.json（blockstates 17112・blocks 763・items 976・
    entities 108・sounds 992・particles 72・menus 24・attributes 13・
    blockentities 33・enchantments 38）
  - vv-1.16.json … 754 側（同じ内容。バイト同一であることを確認済み）
  - 1.16_blocks.json … minecraft-data pc/1.16.2 の blocks.json
    （1.16.3 の dataPaths でも blocks は pc/1.16.2 を指す。実測）
  - registries-1.16.3.json／registries-1.16.5.json … 実バニラの
    reports/registries.json（バイト同一）
  - enchantments-1.16.3.json … pc/1.16.4 順（38 件。dataPaths の
    pc/1.13.2 は 34 件で stale のため使わない。build-1.16.3-inputs.py 参照）
  - enchantments-1.16.json … minecraft-data pc/1.16.4（754 側）
  - biomes-1.16.json … minecraft-data pc/1.16.2（両側とも 79 件・
    id 0〜173。1.16.3 の dataPaths でも biomes は pc/1.16.2 を指す）

出力（すべて --data-dir）：
  - gen753.txt … Java の表の宣言全文（insert-753-tables.py が読む）
  - maps753.json … 754→753 の対応表（7種＋reverse_items）
  - map_items_rev753.json … 753→754 のアイテム逆変換表
  - maps753-reverse.json … 753→754 の対応表（名前の真値。
    build_inverse で作る。恒等写像のため invert() と一致する）
  - maps753-corrections.json … invert() の結果と名前の真値の差分
    （全種 0 件の見込み。wiring で invert() に渡す補正）

対応の決め方（generate-remap-tables.py の build_pair と同じ）：
  1. 名前（ブロック状態は名前＋プロパティ全体）がそのままある → その ID。
  2. 改名・代替は無い（RENAME_753・*_FALLBACK_753 はすべて空）。
     754 と 753 のレジストリ内容が同一のため、1 対 1 の同名写像になる。
  3. それでも決まらないものがあれば「未対応」として一覧を出し、
     終了コード 1 で失敗する（黙って推測しない）。

1.16.5→1.16.3 で確認した差分（名前ベース。実バニラ jar の data-generator
レポート（registries-1.16.3.json／blocks-1.16.3-reports.json）と
registries-1.16.5.json／blocks-1.16.5-reports.json、ViaVersion mapping、
minecraft-data で実測）：
  - ブロック状態 17,112→17,112（同一。文字列が完全一致する状態は 17,112 件）
  - ブロック 763→763（同一。改名なし）
  - アイテム 976→976（同一。改名なし）
  - エンティティ 108→108（同一）
  - 音 992→992（同一。改名なし）
  - パーティクル 72→72（同一）
  - メニュー 24→24（同一順序）、属性 13→13（同一順序）、
    エンチャント 38→38（同一順序）、エフェクト 32→32（同一順序）
  - ブロックエンティティ 33→33（同一順序。754 と違い恒等写像）
  - バイオーム 79→79（同一。0〜173 は同じ id・同じ名前。隙間 95 件。
    両側ともワイヤ id 空間は 0〜173 の 174 件）
  - ゲームイベント：1.16.3／1.16.5 とも無し。
    カスタム統計 74→74。
  - damage_type レジストリは 1.16.3／1.16.5 のどちらにも無い（1.19.4／
    762 で追加）。754 と同じく 762 の 42件の恒等写像を置くが、
    ワイヤ上の呼び出し元は無い。
  - data_component_type・environment_attribute・slot_display は
    1.16.3／1.16.5 のどちらにも無い（1.20.5 以降で追加）。
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


# 改名は無い（754 と 753 のレジストリ内容は同一）。
RENAME_753 = {}

# 代替表はすべて空。754 の新ブロック／新アイテム／新音などは 753 にも
# そのままあるため、代替で解決するものは 1 件も無い。
ITEM_FALLBACK_753 = {}
BLOCK_FALLBACK_753 = {}
ENTITY_FALLBACK_753 = {}
SOUND_FALLBACK_753 = {}
PARTICLE_FALLBACK_753 = {}

# バイオームの改名・代替は無い（両側とも 79 件で同じ id・同じ名前）。
BIOME_RENAME_753 = {}
BIOME_FALLBACK_753 = {}
REVERSE_BIOME_FALLBACK_753 = {}

# パーティクルの反転の代替は無い（753 の 72 件はすべて 754 に
# 同名で存在するため）。
REVERSE_PARTICLE_FALLBACK_753 = {}

KINDS = ["blockstates", "items", "entities", "sounds", "particles",
         "blocks", "menus"]
KIND_JAVA = {"blockstates": "BLOCK_STATE", "items": "ITEM",
             "entities": "ENTITY_TYPE", "sounds": "SOUND",
             "particles": "PARTICLE", "blocks": "BLOCK", "menus": "MENU"}


def normalize_blockstates(states):
    """ブロック状態の文字列のプロパティをソートして正規化する。

    754→753 ではブロック状態は順序の違いだけのため正規化は
    変化を起こさないが、754 の前例と同じ段取りで通す（順序だけ違う
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


def emit_map(out, java_name, mapping, size, source_tag="754"):
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

    # ---  headline の確認：1.16.3 と 1.16.5 のレポートがバイト同一 ---
    reg_bytes_753 = (data_dir / "registries-1.16.3.json").read_bytes()
    reg_bytes_754 = (data_dir / "registries-1.16.5.json").read_bytes()
    rep_bytes_753 = (data_dir / "blocks-1.16.3-reports.json").read_bytes()
    rep_bytes_754 = (data_dir / "blocks-1.16.5-reports.json").read_bytes()
    print(f"[753] registries-1.16.3 == registries-1.16.5（バイト同一）: "
          f"{reg_bytes_753 == reg_bytes_754}")
    print(f"[753] blocks-1.16.3 == blocks-1.16.5（バイト同一）: "
          f"{rep_bytes_753 == rep_bytes_754}")
    if reg_bytes_753 != reg_bytes_754 or rep_bytes_753 != rep_bytes_754:
        print("[753] レポートがバイト同一でない。恒等写像の前提が崩れた",
              file=sys.stderr)
        return 1

    a753 = load(data_dir / "vv-1.16.3.json")
    a754 = load(data_dir / "vv-1.16.json")
    blocks116 = load(data_dir / "1.16_blocks.json")
    reg753 = load(data_dir / "registries-1.16.3.json")
    reg754 = load(data_dir / "registries-1.16.5.json")

    # 753 側と 754 側の ViaVersion mapping の各リストが全件一致すること。
    for key in ("blockstates", "blocks", "items", "entities", "sounds",
                "particles", "menus", "attributes", "blockentities",
                "enchantments"):
        if a753[key] != a754[key]:
            print(f"[753] vv の {key} が 753 側と 754 側で違う", file=sys.stderr)
            failures.append((f"vv-{key}-differs", None))
    if failures:
        return 1
    print("[753] vv-1.16.3.json と vv-1.16.json の各リストは全件一致")

    # 754側の代替先（UP_753_FALLBACKS）。名前から引く（754 の前例と同じ）。
    def idx(vv, kind, name):
        return vv[kind].index(name)

    up753_fallbacks = {
        "blockstates": 1,
        "items": 1,
        "entities": idx(a754, "entities", "pig"),
        "sounds": idx(a754, "sounds", "block.stone.break"),
        "particles": idx(a754, "particles", "entity_effect"),
        "blocks": 1,
        "menus": idx(a754, "menus", "crafting"),
    }
    print(f"[753] UP_753_FALLBACKS（754側）: {up753_fallbacks}")

    # ブロック状態のプロパティ順を正規化してから build_pair を呼ぶ。
    a753n = dict(a753)
    a754n = dict(a754)
    a753n["blockstates"] = normalize_blockstates(a753["blockstates"])
    a754n["blockstates"] = normalize_blockstates(a754["blockstates"])
    # build_pair は ITEM_FALLBACK（モジュールグローバル）をアイテムと
    # ブロックの代替に使うため、753版（空）に差し替える。
    grt.ITEM_FALLBACK = ITEM_FALLBACK_753
    maps753, out753, fail753, rev753 = build_pair(
        "753", a753n, a754n, blocks116, data_dir,
        rename=RENAME_753, sound_fallbacks=SOUND_FALLBACK_753,
        particle_fallbacks=PARTICLE_FALLBACK_753,
        reverse_fallback=1, entity_fallbacks=ENTITY_FALLBACK_753,
        block_fallbacks=BLOCK_FALLBACK_753)
    failures += fail753
    if failures:
        print(f"\n未対応があります: {failures}", file=sys.stderr)
        return 1
    # build_pair はコメントに source バージョンを "765" と
    # ハードコードしている。753 の source は 754 のため書き換える。
    out753 = [line.replace(": 765 entries=", ": 754 entries=")
              for line in out753]

    # --- 恒等写像であることの確認：順向き表は全種 identity ---
    for kind in KINDS:
        ident = {i: i for i in range(len(a754[kind]))}
        if maps753[kind] != ident:
            diffs = [i for i in range(len(a754[kind]))
                     if maps753[kind].get(i) != i]
            print(f"[753] {kind}: 恒等写像でない（{len(diffs)} 件）{diffs[:5]}",
                  file=sys.stderr)
            failures.append((f"{kind}-not-identity", diffs[:5]))
    if failures:
        return 1
    print("[753] 順向き表は 7 種すべて恒等写像（代替使用 0 件）")

    # --- 代替で解決したブロック状態の監査：代替表が空のため 0 件 ---
    by_props16 = {}
    for i, s in enumerate(a753["blockstates"]):
        by_props16[(block_name(s), frozenset(block_props(s).items()))] = i
    audit_fail = []
    for i754, s754 in enumerate(a754["blockstates"]):
        name = block_name(s754)
        if name in BLOCK_FALLBACK_753:
            target, fn = BLOCK_FALLBACK_753[name]
            props = fn(block_props(s754)) if fn else {}
            if (target, frozenset(props.items())) not in by_props16:
                if fn is not None:
                    audit_fail.append((i754, s754, target, props))
    print(f"[753] blockstate fallback audit: {len(audit_fail)} 件の不整合 "
          f"{audit_fail[:5]}")
    if audit_fail:
        failures.append(("blockstate-fallback-audit", audit_fail[:5]))
        return 1

    out = ["    // Generated by tools/gen-753-tables.py. Do not edit by hand."]
    out += out753

    with open(data_dir / "maps753.json", "w", encoding="utf-8") as f:
        json.dump(maps753, f)
    with open(data_dir / "map_items_rev753.json", "w", encoding="utf-8") as f:
        json.dump({str(k): v for k, v in rev753.items()}, f)

    # --- 753→754 の反転表（名前の真値）。build_inverse は同名のものを
    # 優先するため、invert() の first-wins と違う場合がある。
    # 恒等写像のため両者は一致するはず。ブロック状態は
    # waterlogged=false（既定値）のものを優先する（754 と同じ段取り）。---
    reverse_truth = {}
    for kind in ("blockstates", "entities", "sounds", "particles",
                 "blocks", "menus"):
        relaxed = {"waterlogged": "false"} if kind == "blockstates" else None
        inv = build_inverse(maps753[kind], len(a754[kind]), len(a753[kind]),
                            a754[kind], a753[kind], relaxed)
        missing = [i for i, v in enumerate(inv) if v < 0]
        if missing:
            print(f"[753] reverse {kind}: 対応なし {len(missing)} 件 "
                  f"{missing[:5]}（代替先 {up753_fallbacks[kind]} で埋める）")
            inv = [v if v >= 0 else up753_fallbacks[kind] for v in inv]
        reverse_truth[kind] = {i: v for i, v in enumerate(inv)}
    # パーティクル：753 の 72 件はすべて 754 に同名で存在するため、
    # 反転表は名前の真値と一致するはず。barrier（id 2）は両方とも
    # そのまま写る。
    barrier_id = a753["particles"].index("barrier")
    if reverse_truth["particles"][barrier_id] != a754["particles"].index("barrier"):
        print(f"[753] reverse particles: barrier({barrier_id})="
              f"{reverse_truth['particles'][barrier_id]}（同名写像でない）",
              file=sys.stderr)
        failures.append(("particles-reverse-identity", None))
        return 1
    # アイテムの反転は build_reverse_items の結果（名前ベース）を使う。
    reverse_truth["items"] = dict(rev753)
    # 音・ブロック・アイテムに改名は無いため、反転表は恒等写像のはず。
    for kind in KINDS:
        ident = {i: i for i in range(len(a753[kind]))}
        if reverse_truth[kind] != ident:
            diffs = [i for i in range(len(a753[kind]))
                     if reverse_truth[kind].get(i) != i]
            print(f"[753] reverse {kind}: 恒等写像でない（{len(diffs)} 件）"
                  f"{diffs[:5]}", file=sys.stderr)
            failures.append((f"reverse-{kind}-not-identity", diffs[:5]))
    if failures:
        return 1
    print("[753] 反転表は 7 種すべて恒等写像")
    with open(data_dir / "maps753-reverse.json", "w", encoding="utf-8") as f:
        json.dump({k: {str(i): v for i, v in m.items()}
                   for k, m in reverse_truth.items()}, f)

    # --- invert() の結果と名前の真値の差分（補正）。wiring で
    # RegistryRemapper.invert() に渡す。恒等写像のため 0 件の見込み。---
    corrections = {}
    for kind in KINDS:
        ranges, overrides = compress(maps753[kind], len(a754[kind]))
        inv_list = invert_java(ranges, overrides, len(a754[kind]),
                               len(a753[kind]), up753_fallbacks[kind])
        corr = [[y, reverse_truth[kind][y]] for y in range(len(a753[kind]))
                if inv_list[y] != reverse_truth[kind][y]]
        corrections[kind] = corr
        print(f"[753] corrections {kind}: {len(corr)} 件 {corr[:5]}")
    with open(data_dir / "maps753-corrections.json", "w", encoding="utf-8") as f:
        json.dump(corrections, f)

    # 反転表を Java の表として出す（*_753_TO_754_*）。
    for kind in KINDS:
        emit_map(out, f"{KIND_JAVA[kind]}_753_TO_754",
                 reverse_truth[kind], len(a753[kind]), source_tag="753")
    # ITEM_REVERSE_753 と ITEM_753_TO_754 が同じことを確認する。
    if reverse_truth["items"] != {i: v for i, v in enumerate(
            build_inverse(maps753["items"], len(a754["items"]),
                          len(a753["items"]), a754["items"],
                          a753["items"]))}:
        print("[753] ITEM_REVERSE_753 と ITEM_753_TO_754 が違う",
              file=sys.stderr)
        failures.append(("item-reverse-mismatch", None))
        return 1

    # --- 音の名前の表（Stop Sound 用）。754→753 は SOUND_FALLBACK_753
    # そのもの（空。改名・新規音は無い）。753→754 も名前の真値の反転表
    # から作る（改名が無いため空）。---
    for table_name, table in (
            ("SOUND_NAME_FALLBACKS_753", SOUND_FALLBACK_753),
            ("SOUND_NAME_FALLBACKS_754_TO_753", SOUND_FALLBACK_753)):
        lines = [f"    private static final String[][] {table_name} = {{"]
        for k, v in sorted(table.items()):
            lines.append(f'            {{"{k}", "{v}"}},')
        lines.append("    };")
        out.append("\n".join(lines))
    table_753_to_754 = {}
    for id753, name753 in enumerate(a753["sounds"]):
        id754 = reverse_truth["sounds"][id753]
        name754 = a754["sounds"][id754]
        if name754 != name753:
            table_753_to_754[name753] = name754
    lines = ["    private static final String[][] SOUND_NAME_FALLBACKS_753_TO_754 = {"]
    for k, v in sorted(table_753_to_754.items()):
        lines.append(f'            {{"{k}", "{v}"}},')
    lines.append("    };")
    out.append("\n".join(lines))
    print(f"[753] sound name table 753->754: {len(table_753_to_754)} entries")

    # --- 属性の表（registries.json の protocol_id 順で作る。1.16.3／1.16.5
    # とも 13件・同一順序のため恒等写像）。1.16.3 の Update Attributes は
    # 属性のキーを文字列で送る（mc-1.16-protocol.json の
    # packet_entity_update_attributes は "name": "string"。数値の mapper
    # は後のバージョンで導入）ため、1.16.3 には照合する mapper が無い。
    # 情報源は registries.json の protocol_id 順だけ（1.16.3／1.16.5 で
    # 同一順序なのは上で確認済み）。---
    attrs753 = registry_order(reg753, "minecraft:attribute")
    attrs754 = registry_order(reg754, "minecraft:attribute")
    if attrs753 != attrs754:
        print("[753] attributes: 1.16.3 と 1.16.5 の順序が違う", file=sys.stderr)
        failures.append(("attributes-order", (attrs753, attrs754)))
        return 1
    attr_754_to_753 = {i: i for i in range(len(attrs754))}
    attr_753_to_754 = {i: i for i in range(len(attrs753))}
    iv, ov = compress(attr_754_to_753, len(attrs754))
    out.append(java_int3("ATTRIBUTE_753_RANGES", iv))
    out.append(java_int2("ATTRIBUTE_753_OVERRIDES", ov))
    out.append(f"    // ATTRIBUTE_753: 754 entries={len(attrs754)} "
               f"ranges={len(iv)} overrides={len(ov)}")
    iv, ov = compress(attr_753_to_754, len(attrs753))
    out.append(java_int3("ATTRIBUTE_REVERSE_753_RANGES", iv))
    out.append(java_int2("ATTRIBUTE_REVERSE_753_OVERRIDES", ov))
    out.append(f"    // ATTRIBUTE_REVERSE_753: 753 entries={len(attrs753)} "
               f"ranges={len(iv)} overrides={len(ov)}")
    out.append(java_string_array("ATTRIBUTE_753_NAMES", attrs753))
    print(f"[753] attributes: 754={len(attrs754)} 753={len(attrs753)} identity")

    # --- エンチャントの表。1.16.3 と 1.16.5 は 38件・同一順序のため恒等写像。
    # 753 側は enchantments-1.16.3.json（pc/1.16.4 順）を使う。dataPaths の
    # pc/1.13.2 は 34 件で stale のため使わない（build-1.16.3-inputs.py 参照）。---
    ench753 = [e["name"] for e in load(data_dir / "enchantments-1.16.3.json")]
    ench754 = [e["name"] for e in load(data_dir / "enchantments-1.16.json")]
    if ench753 != a753["enchantments"] or ench754 != a754["enchantments"]:
        print("[753] enchantments: minecraft-data と ViaVersion の順序が違う",
              file=sys.stderr)
        failures.append(("enchantments-order", None))
        return 1
    ench_754_to_753 = {i: (ench753.index(n) if n in ench753 else -1)
                       for i, n in enumerate(ench754)}
    ench_753_to_754 = {i: (ench754.index(n) if n in ench754 else -1)
                       for i, n in enumerate(ench753)}
    iv, ov = compress(ench_754_to_753, len(ench754))
    out.append(java_int3("ENCHANTMENT_753_RANGES", iv))
    out.append(java_int2("ENCHANTMENT_753_OVERRIDES", ov))
    out.append(f"    // ENCHANTMENT_753: 754 entries={len(ench754)} "
               f"ranges={len(iv)} overrides={len(ov)}")
    iv, ov = compress(ench_753_to_754, len(ench753))
    out.append(java_int3("ENCHANTMENT_REVERSE_753_RANGES", iv))
    out.append(java_int2("ENCHANTMENT_REVERSE_753_OVERRIDES", ov))
    out.append(f"    // ENCHANTMENT_REVERSE_753: 753 entries={len(ench753)} "
               f"ranges={len(iv)} overrides={len(ov)}")
    print(f"[753] enchantments: 754={len(ench754)} 753={len(ench753)} identity")

    # --- バイオームの表。minecraft-data の biomes.json の id を使う
    # （biomes-1.16.json。両側とも 79 件・id 0〜173。0〜173 は同じ id・
    # 同じ名前。順向き・反転ともワイヤ id 空間で作る（754 の前例と同じ罠に注意。
    # build_name_map に None を含むリストを渡してはいけない）。
    # 順向き（754→753）は 754 側 174 件（id 0〜173）、反転（753→754）も
    # 753 側 174 件（id 0〜173）。隙間の id は平原（1）で埋める。---
    biomes753_by_id = {}
    for e in load(data_dir / "biomes-1.16.json"):
        biomes753_by_id[e["id"]] = e["name"]
    biomes754_by_id = {}
    for e in load(data_dir / "biomes-1.16.json"):
        biomes754_by_id[e["id"]] = e["name"]
    max_biome_754 = max(biomes754_by_id)
    max_biome_753 = max(biomes753_by_id)
    biomes754_list = [biomes754_by_id.get(i) for i in range(max_biome_754 + 1)]
    biomes753_list = [biomes753_by_id.get(i) for i in range(max_biome_753 + 1)]
    if (len([b for b in biomes753_list if b is not None]),
            len([b for b in biomes754_list if b is not None])) != (79, 79):
        print(f"[753] biomes: 件数が想定と違う "
              f"753={len([b for b in biomes753_list if b is not None])} "
              f"754={len([b for b in biomes754_list if b is not None])}",
              file=sys.stderr)
        failures.append(("biomes-count", None))
        return 1
    # 0〜173 は同じ id・同じ名前であることを確認する。
    shared_mismatch = [(i, biomes754_by_id.get(i), biomes753_by_id.get(i))
                       for i in range(max_biome_753 + 1)
                       if biomes754_by_id.get(i) != biomes753_by_id.get(i)]
    if shared_mismatch:
        print(f"[753] biomes: 0〜173 で id と名前が違うものがある：{shared_mismatch[:5]}",
              file=sys.stderr)
        failures.append(("biomes-shared", shared_mismatch[:5]))
        return 1
    m_bio = {w: (w if biomes753_by_id.get(w) is not None else 1)
             for w in range(max_biome_754 + 1)}
    emit_map(out, "BIOME_753", m_bio, len(biomes754_list), source_tag="754")
    rev_bio = build_inverse(m_bio, len(biomes754_list), len(biomes753_list),
                            biomes754_list, biomes753_list)
    biomes753_name_to_id = {n: i for i, n in biomes753_by_id.items()}
    biomes754_name_to_id = {n: i for i, n in biomes754_by_id.items()}
    patched_bio = 0
    for name753, name754 in REVERSE_BIOME_FALLBACK_753.items():
        id753 = biomes753_name_to_id[name753]
        id754 = biomes754_name_to_id[name754]
        if rev_bio[id753] < 0:
            rev_bio[id753] = id754
            patched_bio += 1
        elif rev_bio[id753] != id754:
            print(f"[753] biomes: 反転表の {name753}({id753}) が想定と違う："
                  f"{rev_bio[id753]} != {id754}", file=sys.stderr)
            failures.append(("biomes-reverse", (name753, rev_bio[id753], id754)))
            return 1
    print(f"[753] biomes reverse: 削除バイオームの明示 {patched_bio} 件")
    gaps = [i for i, b in enumerate(biomes753_list) if b is None]
    missing_bio = [i for i, v in enumerate(rev_bio)
                   if v < 0 and biomes753_list[i] is not None]
    if missing_bio:
        print(f"[753] biomes: 反転表に対応なしの実在バイオーム {missing_bio}",
              file=sys.stderr)
        failures.append(("biomes-reverse-missing", missing_bio))
        return 1
    rev_bio = [v if v >= 0 else 1 for v in rev_bio]
    print(f"[753] biomes reverse: 隙間の id {len(gaps)} 件は平原（754 id 1）で埋める")
    emit_map(out, "BIOME_753_TO_754",
             {i: v for i, v in enumerate(rev_bio)}, len(biomes753_list),
             source_tag="753")
    print(f"[753] biomes: 754={len([b for b in biomes754_list if b is not None])} "
          f"753={len(biomes753_by_id)} "
          f"(754 id 0〜{max_biome_754}、753 id 0〜{max_biome_753}、"
          f"753 側の隙間 {len(gaps)} 件)")

    # --- ダメージ種別（754 と同じ扱い）。damage_type レジストリは
    # 1.16.3／1.16.5 のどちらにも無い（1.19.4／762 で追加）。754 の前例
    # （RegistryTables754.java）にならい、762 の 42件の恒等写像を置くが
    # ワイヤ上の呼び出し元は無い。---
    dmg = {i: i for i in range(42)}
    emit_map(out, "DAMAGE_TYPE_753", dmg, 42, source_tag="754")
    emit_map(out, "DAMAGE_TYPE_753_TO_754", dmg, 42, source_tag="753")
    print("[753] damage_type: 762 の 42件の恒等写像（プレースホルダ）")

    # エフェクト（32→32、同一順序）は数値の表を作らない。エフェクトは
    # 754 の前例と同じく wiring 側で恒等写像にする（新規も削除も無いため
    # DROP は無い）。ブロックエンティティも 33→33 で同一順序のため、
    # 表は作らず wiring 側で恒等写像にする（754 と違い DROP は無い）。
    # ここでは件数だけ記録する。
    eff753 = registry_order(reg753, "minecraft:mob_effect")
    eff754 = registry_order(reg754, "minecraft:mob_effect")
    print(f"[753] effects: 754={len(eff754)} 753={len(eff753)} "
          f"identical={eff753 == eff754}")
    be753 = registry_order(reg753, "minecraft:block_entity_type")
    be754 = registry_order(reg754, "minecraft:block_entity_type")
    print(f"[753] block entities: 754={len(be754)} 753={len(be753)} "
          f"753-is-prefix={be753 == be754[:len(be753)]} "
          f"identical={be753 == be754} "
          f"754-only={sorted(set(be754) - set(be753))}")

    text = "\n".join(out) + "\n"
    (data_dir / "gen753.txt").write_text(text, encoding="utf-8")
    print(f"Java の表を {data_dir / 'gen753.txt'} に書き出しました。")
    print("完了：未対応 0 件。")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
