#!/usr/bin/env python3
"""756（1.17.1）のレジストリ対応表（757→756）の生成。

757の前例（gen-757-tables.py）にならう。756は757の1つ下で、757の
隣接として表を直接作る（777のときの776問題と同じ構図で、同じ方針(b)。
758は他人の作業中のため飛ばす）。合成表は作らない。757↔763〜775 は
757→759→760→761→762→763→合成表の隣接連鎖で到達し、756はその連鎖
の先頭に 757→756 の隣接表でつながる。

使い方：
  python3 tools/gen-756-tables.py [--data-dir DIR]

入力（detergentmc-protocol/build/remap-data/。build-1.17-inputs.py
で vv-1.17.json に menus／attributes／blockentities／enchantments
を補完済み）：
  - vv-1.17.json … ViaVersion mapping-1.17.json（menus／attributes／
    blockentities／enchantments 補完済み）
  - vv-1.18.json … ViaVersion mapping-1.18.json（同上）
  - 1.17_blocks.json … minecraft-data pc/1.17 の blocks.json
  - registries-1.17.1.json／registries-1.18.json … 実バニラの
    reports/registries.json のコピー
  - enchantments-1.17.json／enchantments-1.18.json … minecraft-data
  - biomes-1.17.json／biomes-1.18.json … minecraft-data

出力（すべて --data-dir）：
  - gen756.txt … Java の表の宣言全文（insert-756-tables.py が読む）
  - maps756.json … 757→756 の対応表（7種＋reverse_items）
  - map_items_rev756.json … 756→757 のアイテム逆変換表
  - maps756-reverse.json … 756→757 の対応表（名前の真値。
    build_inverse で作る。同名のものを優先するため、invert() の
    first-wins と違う場合がある）
  - maps756-corrections.json … invert() の結果と名前の真値の差分
    （(756 id → 757 id) の対。wiring で invert() に渡す補正）

対応の決め方（generate-remap-tables.py の build_pair と同じ）：
  1. 名前（ブロック状態は名前＋プロパティ全体）がそのままある → その ID。
  2. プロパティを1個だけ落とすと一致する → その ID（756では該当なし。
     ブロック状態は 20,342 件すべて文字列が完全一致する）。
  3. 下の *_756 代替表にある 1.18 の新ブロック／新アイテム／新音など
     → 近い既存のもの。
  4. それでも決まらないものがあれば「未対応」として一覧を出し、
     終了コード 1 で失敗する（黙って推測しない）。

1.17.1→1.18 で確認した差分（名前ベース。ViaVersion mapping-1.17.json
と mapping-1.18.json、minecraft-data、実バニラ jar の報告書で実測）：
  - ブロック状態 20,342→20,342（0。文字列は全件完全一致・順序も同一）
  - ブロック 898→898（0。順序も同一）
  - アイテム 1,100→1,101（新 1件：music_disc_otherside）
  - エンティティ 113→113（0。順序も同一）
  - 音 1,190→1,203（新 13件）
  - パーティクル 89→88（1.17 の barrier・light が 1.18 で block_marker
    に統合。ViaVersion の mapping-1.17to1.18.json の particles 差分で確認）
  - メニュー 24→24（同一順序）、属性 13→13（同一順序）、
    エンチャント 38→38（同一順序）、ブロックエンティティ 34→34
    （同一順序）、エフェクト 32→32（同一順序）、ゲームイベント 45→45
    （同一順序。改名なし）、絵画 26→26、ポーション 43→43、
    レシピシリアライザ 21→21（いずれも同一順序）
  - バイオーム 81→61（1.18 で改名 11件・削除 26件・新規 6件。
    改名と削除の対応は 1.18 の変更履歴による。ワイヤ上の id は
    1.17.1 が 0〜175 の非連番、1.18 が 0〜60 の連番で、実バニラ
    1.17.1 の Join Game dimensionCodec の生キャプチャで確認済み）
  - 改名はバイオーム 11件・パーティクル 2件（統合）のみ。
    他の 7種（ブロック状態・ブロック・アイテム・エンティティ・音・
    メニュー・属性）は改名 0件・削除 0件。
  - damage_type レジストリは 1.17.1／1.18 のどちらにも無い（1.19.4／
    762 で追加）。757 と同じく 762 の 42件の恒等写像を置くが、
    ワイヤ上の呼び出し元は無い。
  - data_component_type・environment_attribute・slot_display は
    1.17.1／1.18 のどちらにも無い（1.20.5 以降で追加）。
"""

import importlib.util
import json
import sys
from collections import Counter
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


# 757→756 は改名・削除が無い（ViaVersion mapping-1.18.json と
# mapping-1.17.json の名前の差分で確認。新規はアイテム 1件・音 13件・
# パーティクル 1件だけ。バイオームの改名は別表 BIOME_RENAME_756）。
RENAME_756 = {}

# 1.18 の新ブロックは無い（898→898 で同一）ため代替表は空。
BLOCK_FALLBACK_756 = {}

# 1.18 の新アイテム 1件 → 1.17 の近いアイテム。otherside は 757 の
# music_disc_5→music_disc_13 の前例にならいレコード13へ。
ITEM_FALLBACK_756 = {
    "music_disc_otherside": "music_disc_13",
}

# 1.18 の新エンティティは無い（113→113 で同一）ため代替表は空。
ENTITY_FALLBACK_756 = {}

# 1.18 の新パーティクル 1件。block_marker は 1.17 の barrier と light
# が統合されたもの（ViaVersion mappings/diff/mapping-1.17to1.18.json
# の particles 差分：{"barrier": "block_marker", "light": "block_marker"}）。
# 順向きは barrier を選ぶ（barrier は 1.8 からある表示用の粒子で、
# light より一般的。light 側は反転表で block_marker へ写す）。
PARTICLE_FALLBACK_756 = {
    "block_marker": "barrier",
}

# 1.18 の新音 13件 → 1.17 の近い音。
SOUND_FALLBACK_756 = {
    # 植物の成長音（1.18 で追加。1.17 の作物の音へ）。
    "block.growing_plant.crop": "block.crop.break",
    # バンドル（1.17 にはアイテムはあるが音は無い。汎用のアイテム音へ）。
    "item.bundle.drop_contents": "entity.item.pickup",
    "item.bundle.insert": "entity.item.pickup",
    "item.bundle.remove_one": "entity.item.pickup",
    # 新しい山・洞窟バイオームの BGM。1.17 には music.overworld.* が
    # 一切無いため、1.17 の地上 BGM（music.game）へ。
    "music.overworld.dripstone_caves": "music.game",
    "music.overworld.frozen_peaks": "music.game",
    "music.overworld.grove": "music.game",
    "music.overworld.jagged_peaks": "music.game",
    "music.overworld.lush_caves": "music.game",
    "music.overworld.meadow": "music.game",
    "music.overworld.snowy_slopes": "music.game",
    "music.overworld.stony_peaks": "music.game",
    # レコード otherside → レコード13（757 の music_disc_5 と同じ扱い）。
    "music_disc.otherside": "music_disc.13",
}

# バイオームの改名（757 の名前 → 756 の名前。1.18 の変更履歴による
# 11件。出典は Java Edition 1.18 の「Some biomes have been renamed」）。
BIOME_RENAME_756 = {
    "snowy_plains": "snowy_tundra",
    "old_growth_birch_forest": "tall_birch_forest",
    "old_growth_pine_taiga": "giant_tree_taiga",
    "old_growth_spruce_taiga": "giant_spruce_taiga",
    "sparse_jungle": "jungle_edge",
    "stony_shore": "stone_shore",
    "windswept_hills": "mountains",
    "windswept_forest": "wooded_mountains",
    "windswept_gravelly_hills": "gravelly_mountains",
    "windswept_savanna": "shattered_savanna",
    "wooded_badlands": "wooded_badlands_plateau",
}

# バイオームの代替（757→756）。1.18 の新しい山バイオーム 6件 → 1.17 の
# 近いバイオーム（地形の類似によるベストエフォート）。
BIOME_FALLBACK_756 = {
    "meadow": "plains",
    "grove": "snowy_taiga",
    "snowy_slopes": "snowy_tundra",
    "frozen_peaks": "snowy_mountains",
    "jagged_peaks": "mountains",
    "stony_peaks": "mountains",
}

# バイオームの反転の代替（756 の名前 → 757 の名前）。1.18 で削除された
# 1.17 のバイオーム 26件 → 1.18 の既定のバイオーム（1.18 の変更履歴に
# 「以前のバージョンで生成されたこれらのバイオームは既定のバイオームに
# 変換される」とある。亜種は親バイオームへ、改名された親は改名後の名前へ）。
REVERSE_BIOME_FALLBACK_756 = {
    "badlands_plateau": "badlands",
    "bamboo_jungle_hills": "bamboo_jungle",
    "birch_forest_hills": "birch_forest",
    "dark_forest_hills": "dark_forest",
    "deep_warm_ocean": "warm_ocean",
    "desert_hills": "desert",
    "desert_lakes": "desert",
    "giant_spruce_taiga_hills": "old_growth_spruce_taiga",
    "giant_tree_taiga_hills": "old_growth_pine_taiga",
    "modified_gravelly_mountains": "windswept_gravelly_hills",
    "jungle_hills": "jungle",
    "modified_badlands_plateau": "badlands",
    "modified_jungle": "jungle",
    "modified_jungle_edge": "sparse_jungle",
    "modified_wooded_badlands_plateau": "wooded_badlands",
    "mountain_edge": "windswept_hills",
    "mushroom_field_shore": "mushroom_fields",
    "shattered_savanna_plateau": "windswept_savanna",
    # snowy_mountains は順向きの frozen_peaks→snowy_mountains と対応を
    # 取る（順向きの逆写像として frozen_peaks。grove は
    # snowy_taiga_mountains の代替として使う）。
    "snowy_mountains": "frozen_peaks",
    "snowy_taiga_hills": "snowy_taiga",
    "snowy_taiga_mountains": "grove",
    "swamp_hills": "swamp",
    "taiga_hills": "taiga",
    "taiga_mountains": "taiga",
    "tall_birch_hills": "old_growth_birch_forest",
    "wooded_hills": "windswept_forest",
}

# パーティクルの反転の代替（756 の名前 → 757 の名前）。barrier・light は
# どちらも 1.18 の block_marker に統合された（ViaVersion の差分ファイル）。
# 順向きは block_marker→barrier のため、build_inverse だけでは light に
# preimage が無く -1 になる。ここで明示して block_marker へ写す。
REVERSE_PARTICLE_FALLBACK_756 = {
    "barrier": "block_marker",
    "light": "block_marker",
}

KINDS = ["blockstates", "items", "entities", "sounds", "particles",
         "blocks", "menus"]
KIND_JAVA = {"blockstates": "BLOCK_STATE", "items": "ITEM",
             "entities": "ENTITY_TYPE", "sounds": "SOUND",
             "particles": "PARTICLE", "blocks": "BLOCK", "menus": "MENU"}


def normalize_blockstates(states):
    """ブロック状態の文字列のプロパティをソートして正規化する。

    757→756 ではブロック状態は全件文字列が完全一致するため正規化は
    変化を起こさないが、757 の前例と同じ段取りで通す（順序だけ違う
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


def emit_map(out, java_name, mapping, size, source_tag="757"):
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

    a756 = load(data_dir / "vv-1.17.json")
    a757 = load(data_dir / "vv-1.18.json")
    blocks117 = load(data_dir / "1.17_blocks.json")
    reg17 = load(data_dir / "registries-1.17.1.json")
    reg18 = load(data_dir / "registries-1.18.json")

    # 757側の代替先（UP_756_FALLBACKS）。名前から引く（757 の前例と同じ）。
    def idx(vv, kind, name):
        return vv[kind].index(name)

    up756_fallbacks = {
        "blockstates": 1,
        "items": 1,
        "entities": idx(a757, "entities", "pig"),
        "sounds": idx(a757, "sounds", "block.stone.break"),
        "particles": idx(a757, "particles", "entity_effect"),
        "blocks": 1,
        "menus": idx(a757, "menus", "crafting"),
    }
    print(f"[756] UP_756_FALLBACKS（757側）: {up756_fallbacks}")

    # ブロック状態のプロパティ順を正規化してから build_pair を呼ぶ。
    a756n = dict(a756)
    a757n = dict(a757)
    a756n["blockstates"] = normalize_blockstates(a756["blockstates"])
    a757n["blockstates"] = normalize_blockstates(a757["blockstates"])
    # build_pair は ITEM_FALLBACK（モジュールグローバル）をアイテムと
    # ブロックの代替に使うため、756版に差し替える。
    grt.ITEM_FALLBACK = ITEM_FALLBACK_756
    maps756, out756, fail756, rev756 = build_pair(
        "756", a756n, a757n, blocks117, data_dir,
        rename=RENAME_756, sound_fallbacks=SOUND_FALLBACK_756,
        particle_fallbacks=PARTICLE_FALLBACK_756,
        reverse_fallback=1, entity_fallbacks=ENTITY_FALLBACK_756,
        block_fallbacks=BLOCK_FALLBACK_756)
    failures += fail756
    if failures:
        print(f"\n未対応があります: {failures}", file=sys.stderr)
        return 1
    # 775版の build_pair はコメントに source バージョンを "765" と
    # ハードコードしている。756 の source は 757 のため書き換える。
    out756 = [line.replace(": 765 entries=", ": 757 entries=")
              for line in out756]

    # --- 代替で解決したブロック状態の監査：state763() は見つからない場合に
    # 黙って既定状態を返すため、代替先の（ブロック名＋プロパティ）が
    # 1.17 に実在することを別途確認する。756 では代替ブロックが無いため
    # 監査は 0 件のはず。---
    by_props17 = {}
    for i, s in enumerate(a756["blockstates"]):
        by_props17[(block_name(s), frozenset(block_props(s).items()))] = i
    audit_fail = []
    for i757, s757 in enumerate(a757["blockstates"]):
        name = block_name(s757)
        if name in BLOCK_FALLBACK_756:
            target, fn = BLOCK_FALLBACK_756[name]
            props = fn(block_props(s757)) if fn else {}
            if (target, frozenset(props.items())) not in by_props17:
                if fn is not None:
                    audit_fail.append((i757, s757, target, props))
    print(f"[756] blockstate fallback audit: {len(audit_fail)} 件の不整合 "
          f"{audit_fail[:5]}")
    if audit_fail:
        failures.append(("blockstate-fallback-audit", audit_fail[:5]))
        return 1

    out = ["    // Generated by tools/gen-756-tables.py. Do not edit by hand."]
    out += out756

    with open(data_dir / "maps756.json", "w", encoding="utf-8") as f:
        json.dump(maps756, f)
    with open(data_dir / "map_items_rev756.json", "w", encoding="utf-8") as f:
        json.dump({str(k): v for k, v in rev756.items()}, f)

    # --- 756→757 の反転表（名前の真値）。build_inverse は同名のものを
    # 優先するため、invert() の first-wins と違う場合がある。
    # ブロック状態は waterlogged=false（既定値）のものを優先する
    # （757→756 では waterlogged の追加は無いため実質 no-op）。---
    reverse_truth = {}
    for kind in ("blockstates", "entities", "sounds", "particles",
                 "blocks", "menus"):
        relaxed = {"waterlogged": "false"} if kind == "blockstates" else None
        inv = build_inverse(maps756[kind], len(a757[kind]), len(a756[kind]),
                            a757[kind], a756[kind], relaxed)
        missing = [i for i, v in enumerate(inv) if v < 0]
        if missing:
            print(f"[756] reverse {kind}: 対応なし {len(missing)} 件 "
                  f"{missing[:5]}（代替先 {up756_fallbacks[kind]} で埋める）")
            inv = [v if v >= 0 else up756_fallbacks[kind] for v in inv]
        reverse_truth[kind] = {i: v for i, v in enumerate(inv)}
    # パーティクル：light（756 id 3）には順向きの preimage が無い。
    # ViaVersion の差分ファイルでは light→block_marker のため、反転表の
    # 真値を明示して block_marker（757 id 3）へ写す。barrier（756 id 2）
    # は build_inverse の初出で block_marker になっていることを確認する。
    light_id = a756["particles"].index("light")
    block_marker_757 = a757["particles"].index("block_marker")
    if reverse_truth["particles"][light_id] != block_marker_757:
        print(f"[756] reverse particles: light({light_id}) を "
              f"block_marker({block_marker_757}) に補正 "
              f"（補正前 {reverse_truth['particles'][light_id]}）")
        reverse_truth["particles"][light_id] = block_marker_757
    barrier_id = a756["particles"].index("barrier")
    if reverse_truth["particles"][barrier_id] != block_marker_757:
        print(f"[756] reverse particles: barrier({barrier_id}) が "
              f"block_marker でない：{reverse_truth['particles'][barrier_id]}",
              file=sys.stderr)
        failures.append(("particles-reverse-barrier", None))
        return 1
    # アイテムの反転は build_reverse_items の結果（名前ベース）を使う。
    reverse_truth["items"] = dict(rev756)
    with open(data_dir / "maps756-reverse.json", "w", encoding="utf-8") as f:
        json.dump({k: {str(i): v for i, v in m.items()}
                   for k, m in reverse_truth.items()}, f)

    # --- invert() の結果と名前の真値の差分（補正）。wiring で
    # RegistryRemapper.invert() に渡す。---
    corrections = {}
    for kind in KINDS:
        ranges, overrides = compress(maps756[kind], len(a757[kind]))
        inv_list = invert_java(ranges, overrides, len(a757[kind]),
                               len(a756[kind]), up756_fallbacks[kind])
        corr = [[y, reverse_truth[kind][y]] for y in range(len(a756[kind]))
                if inv_list[y] != reverse_truth[kind][y]]
        corrections[kind] = corr
        print(f"[756] corrections {kind}: {len(corr)} 件 {corr[:5]}")
    with open(data_dir / "maps756-corrections.json", "w", encoding="utf-8") as f:
        json.dump(corrections, f)

    # 反転表を Java の表として出す（*_756_TO_757_*）。
    for kind in KINDS:
        emit_map(out, f"{KIND_JAVA[kind]}_756_TO_757",
                 reverse_truth[kind], len(a756[kind]), source_tag="756")
    # ITEM_REVERSE_756 と ITEM_756_TO_757 が同じことを確認する。
    if reverse_truth["items"] != {i: v for i, v in enumerate(
            build_inverse(maps756["items"], len(a757["items"]),
                          len(a756["items"]), a757["items"],
                          a756["items"]))}:
        print("[756] ITEM_REVERSE_756 と ITEM_756_TO_757 が違う",
              file=sys.stderr)
        failures.append(("item-reverse-mismatch", None))
        return 1

    # --- 音の名前の表（Stop Sound 用）。757→756 は SOUND_FALLBACK_756
    # そのもの。756→757 は名前の真値の反転表から作る（invert() の
    # first-wins では汎用の音（music.game・entity.item.pickup・
    # block.crop.break）が 1.18 の新音にすり替わるため、
    # sound_name_table_mid_to_target は使わない）。---
    for table_name, table in (
            ("SOUND_NAME_FALLBACKS_756", SOUND_FALLBACK_756),
            ("SOUND_NAME_FALLBACKS_757_TO_756", SOUND_FALLBACK_756)):
        lines = [f"    private static final String[][] {table_name} = {{"]
        for k, v in sorted(table.items()):
            lines.append(f'            {{"{k}", "{v}"}},')
        lines.append("    };")
        out.append("\n".join(lines))
    table_756_to_757 = {}
    for id756, name756 in enumerate(a756["sounds"]):
        id757 = reverse_truth["sounds"][id756]
        name757 = a757["sounds"][id757]
        if name757 != name756:
            table_756_to_757[name756] = name757
    lines = ["    private static final String[][] SOUND_NAME_FALLBACKS_756_TO_757 = {"]
    for k, v in sorted(table_756_to_757.items()):
        lines.append(f'            {{"{k}", "{v}"}},')
    lines.append("    };")
    out.append("\n".join(lines))
    print(f"[756] sound name table 756->757: {len(table_756_to_757)} entries")

    # --- 属性の表（registries.json の protocol_id 順で作る。1.17.1／1.18
    # とも 13件・同一順序のため恒等写像）。1.17.1 の Update Attributes は
    # 属性のキーを文字列で送る（mc-1.17.1-protocol.json の
    # packet_entity_update_attributes は "name": "string"。数値の mapper
    # は後のバージョンで導入）ため、1.17.1 には照合する mapper が無い。
    # 情報源は registries.json の protocol_id 順だけ（1.17.1／1.18 で
    # 同一順序なのは上で確認済み）。---
    attrs756 = registry_order(reg17, "minecraft:attribute")
    attrs757 = registry_order(reg18, "minecraft:attribute")
    if attrs756 != attrs757:
        print("[756] attributes: 1.17.1 と 1.18 の順序が違う", file=sys.stderr)
        failures.append(("attributes-order", (attrs756, attrs757)))
        return 1
    attr_757_to_756 = {i: i for i in range(len(attrs757))}
    attr_756_to_757 = {i: i for i in range(len(attrs756))}
    iv, ov = compress(attr_757_to_756, len(attrs757))
    out.append(java_int3("ATTRIBUTE_756_RANGES", iv))
    out.append(java_int2("ATTRIBUTE_756_OVERRIDES", ov))
    out.append(f"    // ATTRIBUTE_756: 757 entries={len(attrs757)} "
               f"ranges={len(iv)} overrides={len(ov)}")
    iv, ov = compress(attr_756_to_757, len(attrs756))
    out.append(java_int3("ATTRIBUTE_REVERSE_756_RANGES", iv))
    out.append(java_int2("ATTRIBUTE_REVERSE_756_OVERRIDES", ov))
    out.append(f"    // ATTRIBUTE_REVERSE_756: 756 entries={len(attrs756)} "
               f"ranges={len(iv)} overrides={len(ov)}")
    out.append(java_string_array("ATTRIBUTE_756_NAMES", attrs756))
    print(f"[756] attributes: 757={len(attrs757)} 756={len(attrs756)} identity")

    # --- エンチャントの表。1.17.1 と 1.18 は 38件・同一順序のため恒等写像。---
    ench756 = [e["name"] for e in load(data_dir / "enchantments-1.17.json")]
    ench757 = [e["name"] for e in load(data_dir / "enchantments-1.18.json")]
    if ench756 != a756["enchantments"] or ench757 != a757["enchantments"]:
        print("[756] enchantments: minecraft-data と ViaVersion の順序が違う",
              file=sys.stderr)
        failures.append(("enchantments-order", None))
        return 1
    ench_757_to_756 = {i: (ench756.index(n) if n in ench756 else -1)
                       for i, n in enumerate(ench757)}
    ench_756_to_757 = {i: (ench757.index(n) if n in ench757 else -1)
                       for i, n in enumerate(ench756)}
    iv, ov = compress(ench_757_to_756, len(ench757))
    out.append(java_int3("ENCHANTMENT_756_RANGES", iv))
    out.append(java_int2("ENCHANTMENT_756_OVERRIDES", ov))
    out.append(f"    // ENCHANTMENT_756: 757 entries={len(ench757)} "
               f"ranges={len(iv)} overrides={len(ov)}")
    iv, ov = compress(ench_756_to_757, len(ench756))
    out.append(java_int3("ENCHANTMENT_REVERSE_756_RANGES", iv))
    out.append(java_int2("ENCHANTMENT_REVERSE_756_OVERRIDES", ov))
    out.append(f"    // ENCHANTMENT_REVERSE_756: 756 entries={len(ench756)} "
               f"ranges={len(iv)} overrides={len(ov)}")
    print(f"[756] enchantments: 757={len(ench757)} 756={len(ench756)} identity")

    # --- バイオームの表。minecraft-data の biomes.json の id を使う
    # （build/remap-data/biomes-1.17.json／biomes-1.18.json）。
    # 1.17.1 の id は 0〜175 の非連番（旧来の数値バイオームID）で、
    # 1.18 の id は 0〜60 の連番。ワイヤ上の id がこの通りであることは
    # 実バニラ 1.17.1 の Join Game dimensionCodec の生キャプチャで確認済み
    # （codec の worldgen/biome 81件が biomes-1.17.json の (id, 名前) と
    # 全件一致。dimension_type は 4件：overworld、overworld_caves、
    # the_nether、the_end。minecraft:chat_type は無い）。
    # registries.json には minecraft:worldgen/biome が無い（バイオームは
    # Join Game の dimensionCodec NBT で送られるため）。
    # 順向き（757→756）は RANGES＋OVERRIDES で表す（757 側は 0〜60 の
    # 連番で、756 側の値は非連番でも区間＋上書きで表せる）。
    # 反転（756→757）は 756 側の id 域 0〜175 の 176件の表にする
    # （隙間の id は代替先の平原（757 id 1）で埋める）。---
    biomes756_by_id = {}
    for e in load(data_dir / "biomes-1.17.json"):
        biomes756_by_id[e["id"]] = e["name"]
    biomes757 = [e["name"] for e in sorted(
        load(data_dir / "biomes-1.18.json"), key=lambda e: e["id"])]
    max_biome_756 = max(biomes756_by_id)
    biomes756_list = [biomes756_by_id.get(i) for i in range(max_biome_756 + 1)]
    if (len([b for b in biomes756_list if b is not None]),
            len(biomes757)) != (81, 61):
        print(f"[756] biomes: 件数が想定と違う "
              f"756={len([b for b in biomes756_list if b is not None])} "
              f"757={len(biomes757)}", file=sys.stderr)
        failures.append(("biomes-count", None))
        return 1
    m_bio, fb_bio, unm_bio = build_name_map(
        "biomes", {"biomes": biomes756_list}, {"biomes": biomes757},
        BIOME_FALLBACK_756, rename=BIOME_RENAME_756)
    print(f"[756] biomes forward fallbacks: {dict(fb_bio)}")
    if unm_bio:
        print(f"[756] biomes: 未対応 {unm_bio}", file=sys.stderr)
        failures.append(("biomes", unm_bio))
        return 1
    emit_map(out, "BIOME_756", m_bio, len(biomes757), source_tag="757")
    rev_bio = build_inverse(m_bio, len(biomes757), len(biomes756_list),
                            biomes757, biomes756_list)
    # 1.18 で削除された 1.17 のバイオーム 26件は反転表の真値を明示する。
    biomes756_name_to_id = {n: i for i, n in biomes756_by_id.items()}
    biomes757_name_to_id = {n: i for i, n in enumerate(biomes757)}
    patched_bio = 0
    for name756, name757 in REVERSE_BIOME_FALLBACK_756.items():
        id756 = biomes756_name_to_id[name756]
        id757 = biomes757_name_to_id[name757]
        if rev_bio[id756] < 0:
            rev_bio[id756] = id757
            patched_bio += 1
        elif rev_bio[id756] != id757:
            print(f"[756] biomes: 反転表の {name756}({id756}) が想定と違う："
                  f"{rev_bio[id756]} != {id757}", file=sys.stderr)
            failures.append(("biomes-reverse", (name756, rev_bio[id756], id757)))
            return 1
    print(f"[756] biomes reverse: 削除バイオームの明示 {patched_bio} 件")
    gaps = [i for i, b in enumerate(biomes756_list) if b is None]
    missing_bio = [i for i, v in enumerate(rev_bio)
                   if v < 0 and biomes756_list[i] is not None]
    if missing_bio:
        print(f"[756] biomes: 反転表に対応なしの実在バイオーム {missing_bio}",
              file=sys.stderr)
        failures.append(("biomes-reverse-missing", missing_bio))
        return 1
    rev_bio = [v if v >= 0 else 1 for v in rev_bio]
    print(f"[756] biomes reverse: 隙間の id {len(gaps)} 件は平原（757 id 1）で埋める")
    emit_map(out, "BIOME_756_TO_757",
             {i: v for i, v in enumerate(rev_bio)}, len(biomes756_list),
             source_tag="756")
    print(f"[756] biomes: 757={len(biomes757)} 756={len(biomes756_by_id)} "
          f"(id 0〜{max_biome_756}、隙間 {len(gaps)} 件)")

    # --- ダメージ種別（757 と同じ扱い）。damage_type レジストリは
    # 1.17.1／1.18 のどちらにも無い（1.19.4／762 で追加）。757 の前例
    # （RegistryTables757.java）にならい、762 の 42件の恒等写像を置くが
    # ワイヤ上の呼び出し元は無い。---
    dmg = {i: i for i in range(42)}
    emit_map(out, "DAMAGE_TYPE_756", dmg, 42, source_tag="757")
    emit_map(out, "DAMAGE_TYPE_756_TO_757", dmg, 42, source_tag="756")
    print("[756] damage_type: 762 の 42件の恒等写像（プレースホルダ）")

    # エフェクト（32→32、同一順序）とブロックエンティティ（34→34、
    # 同一順序）は数値の表を作らない。エフェクトは 757 の前例と同じく
    # wiring 側で恒等写像にする（新規も削除も無いため DROP は無い）。
    # ブロックエンティティも新規・削除が無いため恒等写像。ここでは件数だけ記録する。
    eff17 = registry_order(reg17, "minecraft:mob_effect")
    eff18 = registry_order(reg18, "minecraft:mob_effect")
    print(f"[756] effects: 757={len(eff18)} 756={len(eff17)} "
          f"identical={eff17 == eff18}")
    be17 = registry_order(reg17, "minecraft:block_entity_type")
    be18 = registry_order(reg18, "minecraft:block_entity_type")
    print(f"[756] block entities: 757={len(be18)} 756={len(be17)} "
          f"identical={be17 == be18}")

    text = "\n".join(out) + "\n"
    (data_dir / "gen756.txt").write_text(text, encoding="utf-8")
    print(f"Java の表を {data_dir / 'gen756.txt'} に書き出しました。")
    print("完了：未対応 0 件。")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
