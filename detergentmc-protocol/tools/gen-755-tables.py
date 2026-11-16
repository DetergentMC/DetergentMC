#!/usr/bin/env python3
"""755（1.17）のレジストリ対応表（756→755）の生成。

756の前例（gen-756-tables.py）にならう。755は756の1つ下で、756の
隣接として表を直接作る（777のときの776問題と同じ構図で、同じ方針(b)。
758は他人の作業中のため飛ばす）。合成表は作らない。755↔763〜775 は
755→756→757→759→760→761→762→763→合成表の隣接連鎖で到達する。

使い方：
  python3 tools/gen-755-tables.py [--data-dir DIR]

入力（detergentmc-protocol/build/remap-data/。build-1.17-inputs.py
で vv-1.17.json に menus／attributes／blockentities／enchantments
を補完済み）：
  - vv-1.17.json … ViaVersion mapping-1.17.json（menus／attributes／
    blockentities／enchantments 補完済み。756 側・755 側の両方の入力。
    1.17 と 1.17.1 は同じ mapping を使う。ViaVersion に
    mapping-1.17.1.json は無い）
  - 1.17_blocks.json … minecraft-data pc/1.17 の blocks.json
  - registries-1.17.json／registries-1.17.1.json … 実バニラの
    reports/registries.json のコピー（バイト単位で完全一致）
  - enchantments-1.17.json … minecraft-data（両側とも同じファイル）
  - biomes-1.17.json … minecraft-data（両側とも同じファイル）

出力（すべて --data-dir）：
  - gen755.txt … Java の表の宣言全文（insert-755-tables.py が読む）
  - maps755.json … 756→755 の対応表（7種＋reverse_items）
  - map_items_rev755.json … 755→756 のアイテム逆変換表
  - maps755-reverse.json … 755→756 の対応表（名前の真値。
    build_inverse で作る。同名のものを優先するため、invert() の
    first-wins と違う場合がある）
  - maps755-corrections.json … invert() の結果と名前の真値の差分
    （(755 id → 756 id) の対。wiring で invert() に渡す補正。
    差分 0 件の見込み）

対応の決め方（generate-remap-tables.py の build_pair と同じ）：
  1. 名前（ブロック状態は名前＋プロパティ全体）がそのままある → その ID。
  2. プロパティを1個だけ落とすと一致する → その ID（755では該当なし。
     ブロック状態は 20,342 件すべて文字列が完全一致する）。
  3. 下の *_755 代替表にある 1.17.1 の新ブロック／新アイテム／新音など
     → 近い既存のもの（755 では代替表はすべて空）。
  4. それでも決まらないものがあれば「未対応」として一覧を出し、
     終了コード 1 で失敗する（黙って推測しない）。

1.17.1→1.17 で確認した差分（名前ベース。実バニラ jar の data-generator
レポート（registries-1.17.json／blocks-1.17-reports.json）と
registries-1.17.1.json／blocks-1.17.1-reports.json のバイト比較、
ViaVersion mapping-1.17.json、minecraft-data で実測）：
  - ブロック状態 20,342→20,342（0。文字列は全件完全一致・順序も同一）
  - ブロック 898→898（0。順序も同一）
  - アイテム 1,100→1,100（0。順序も同一）
  - エンティティ 113→113（0。順序も同一）
  - 音 1,190→1,190（0。順序も同一）
  - パーティクル 89→89（0。順序も同一）
  - メニュー 24→24（同一順序）、属性 13→13（同一順序）、
    エンチャント 38→38（同一順序）、ブロックエンティティ 34→34
    （同一順序）、エフェクト 32→32（同一順序）、ゲームイベント 45→45
    （同一順序。改名なし）、絵画 26→26、ポーション 43→43、
    レシピシリアライザ 21→21（いずれも同一順序）
  - バイオーム 81→81（0。1.17 と 1.17.1 で同一ファイル（pc/1.17）。
    ワイヤ上の id はどちらも 0〜175 の非連番。dimensionCodec との
    突き合わせは Join Game codec の確認で行う）
  - 改名・削除・新規は全種で 0 件。7 種はすべて恒等写像になるが、
    756 と同じファイル構造（50 表）で RegistryTables755.java を生成し、
    配線が一様にできるようにする。
  - damage_type レジストリは 1.17／1.17.1 のどちらにも無い（1.19.4／
    762 で追加）。756 と同じく 762 の 42件の恒等写像を置くが、
    ワイヤ上の呼び出し元は無い。
  - data_component_type・environment_attribute・slot_display は
    1.17／1.17.1 のどちらにも無い（1.20.5 以降で追加）。
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


# 756→755 は改名・削除が無い。registries-1.17.json と
# registries-1.17.1.json はバイト単位で完全一致し（cmp 確認済み）、
# vv-1.17.json（mapping-1.17.json）は 1.17 と 1.17.1 で共通のため、
# 7 種はすべて恒等写像になる。バイオームも 1.17 と 1.17.1 で同一ファイル
# （pc/1.17 の biomes.json、81 件・id 0〜175 の非連番）のため恒等写像。
# 代替表はすべて空。差分が 0 件であることはこのスクリプトの実行結果
# （未対応 0 件・補正 0 件）と verify-755-tables.py で確認する。
RENAME_755 = {}

# 1.17.1 の新ブロックは無い（898→898 で同一）ため代替表は空。
BLOCK_FALLBACK_755 = {}

# 1.17.1 の新アイテムは無い（1,100→1,100 で同一）ため代替表は空。
ITEM_FALLBACK_755 = {}

# 1.17.1 の新エンティティは無い（113→113 で同一）ため代替表は空。
ENTITY_FALLBACK_755 = {}

# 1.17.1 の新パーティクルは無い（89→89 で同一）ため代替表は空。
PARTICLE_FALLBACK_755 = {}

# 1.17.1 の新音は無い（1,190→1,190 で同一）ため代替表は空。
SOUND_FALLBACK_755 = {}

# バイオームの改名は無い（1.17 と 1.17.1 で同一ファイルのため）。
BIOME_RENAME_755 = {}

# バイオームの代替は無い（1.17.1 の新規バイオームは無いため）。
BIOME_FALLBACK_755 = {}

# バイオームの反転の代替は無い（1.17 で削除されたバイオームは無いため）。
REVERSE_BIOME_FALLBACK_755 = {}

# パーティクルの反転の代替は無い（756 の barrier・light は 755 でも
# barrier・light のままで、block_marker への統合は 1.18 の話のため）。
REVERSE_PARTICLE_FALLBACK_755 = {}

KINDS = ["blockstates", "items", "entities", "sounds", "particles",
         "blocks", "menus"]
KIND_JAVA = {"blockstates": "BLOCK_STATE", "items": "ITEM",
             "entities": "ENTITY_TYPE", "sounds": "SOUND",
             "particles": "PARTICLE", "blocks": "BLOCK", "menus": "MENU"}


def normalize_blockstates(states):
    """ブロック状態の文字列のプロパティをソートして正規化する。

    756→755 ではブロック状態は全件文字列が完全一致するため正規化は
    変化を起こさないが、756 の前例と同じ段取りで通す（順序だけ違う
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


def emit_map(out, java_name, mapping, size, source_tag="756"):
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

    a755 = load(data_dir / "vv-1.17.json")
    a756 = load(data_dir / "vv-1.17.json")
    blocks117 = load(data_dir / "1.17_blocks.json")
    reg755 = load(data_dir / "registries-1.17.json")
    reg756 = load(data_dir / "registries-1.17.1.json")

    # 756側の代替先（UP_755_FALLBACKS）。名前から引く（756 の前例と同じ）。
    def idx(vv, kind, name):
        return vv[kind].index(name)

    up755_fallbacks = {
        "blockstates": 1,
        "items": 1,
        "entities": idx(a756, "entities", "pig"),
        "sounds": idx(a756, "sounds", "block.stone.break"),
        "particles": idx(a756, "particles", "entity_effect"),
        "blocks": 1,
        "menus": idx(a756, "menus", "crafting"),
    }
    print(f"[755] UP_755_FALLBACKS（756側）: {up755_fallbacks}")

    # ブロック状態のプロパティ順を正規化してから build_pair を呼ぶ。
    a755n = dict(a755)
    a756n = dict(a756)
    a755n["blockstates"] = normalize_blockstates(a755["blockstates"])
    a756n["blockstates"] = normalize_blockstates(a756["blockstates"])
    # build_pair は ITEM_FALLBACK（モジュールグローバル）をアイテムと
    # ブロックの代替に使うため、755版に差し替える。
    grt.ITEM_FALLBACK = ITEM_FALLBACK_755
    maps755, out755, fail755, rev755 = build_pair(
        "755", a755n, a756n, blocks117, data_dir,
        rename=RENAME_755, sound_fallbacks=SOUND_FALLBACK_755,
        particle_fallbacks=PARTICLE_FALLBACK_755,
        reverse_fallback=1, entity_fallbacks=ENTITY_FALLBACK_755,
        block_fallbacks=BLOCK_FALLBACK_755)
    failures += fail755
    if failures:
        print(f"\n未対応があります: {failures}", file=sys.stderr)
        return 1
    # 775版の build_pair はコメントに source バージョンを "765" と
    # ハードコードしている。755 の source は 756 のため書き換える。
    out755 = [line.replace(": 765 entries=", ": 756 entries=")
              for line in out755]

    # --- 代替で解決したブロック状態の監査：state763() は見つからない場合に
    # 黙って既定状態を返すため、代替先の（ブロック名＋プロパティ）が
    # 1.17 に実在することを別途確認する。755 では代替ブロックが無いため
    # 監査は 0 件のはず。---
    by_props17 = {}
    for i, s in enumerate(a755["blockstates"]):
        by_props17[(block_name(s), frozenset(block_props(s).items()))] = i
    audit_fail = []
    for i756, s756 in enumerate(a756["blockstates"]):
        name = block_name(s756)
        if name in BLOCK_FALLBACK_755:
            target, fn = BLOCK_FALLBACK_755[name]
            props = fn(block_props(s756)) if fn else {}
            if (target, frozenset(props.items())) not in by_props17:
                if fn is not None:
                    audit_fail.append((i756, s756, target, props))
    print(f"[755] blockstate fallback audit: {len(audit_fail)} 件の不整合 "
          f"{audit_fail[:5]}")
    if audit_fail:
        failures.append(("blockstate-fallback-audit", audit_fail[:5]))
        return 1

    out = ["    // Generated by tools/gen-755-tables.py. Do not edit by hand."]
    out += out755

    with open(data_dir / "maps755.json", "w", encoding="utf-8") as f:
        json.dump(maps755, f)
    with open(data_dir / "map_items_rev755.json", "w", encoding="utf-8") as f:
        json.dump({str(k): v for k, v in rev755.items()}, f)

    # --- 755→756 の反転表（名前の真値）。build_inverse は同名のものを
    # 優先するため、invert() の first-wins と違う場合がある。
    # ブロック状態は waterlogged=false（既定値）のものを優先する
    # （756→755 では waterlogged の追加は無いため実質 no-op）。---
    reverse_truth = {}
    for kind in ("blockstates", "entities", "sounds", "particles",
                 "blocks", "menus"):
        relaxed = {"waterlogged": "false"} if kind == "blockstates" else None
        inv = build_inverse(maps755[kind], len(a756[kind]), len(a755[kind]),
                            a756[kind], a755[kind], relaxed)
        missing = [i for i, v in enumerate(inv) if v < 0]
        if missing:
            print(f"[755] reverse {kind}: 対応なし {len(missing)} 件 "
                  f"{missing[:5]}（代替先 {up755_fallbacks[kind]} で埋める）")
            inv = [v if v >= 0 else up755_fallbacks[kind] for v in inv]
        reverse_truth[kind] = {i: v for i, v in enumerate(inv)}
    # パーティクル：755 と 756 は同一順序のため恒等写像。barrier（id 2）・
    # light（id 3）は両方ともそのまま写る（block_marker への統合は
    # 1.18 の話で、755↔756 には関係ない）。build_inverse の結果が
    # 恒等写像であることを確認する。
    light_id = a755["particles"].index("light")
    barrier_id = a755["particles"].index("barrier")
    if (reverse_truth["particles"][light_id] != light_id
            or reverse_truth["particles"][barrier_id] != barrier_id):
        print(f"[755] reverse particles: light({light_id})="
              f"{reverse_truth['particles'][light_id]} barrier({barrier_id})="
              f"{reverse_truth['particles'][barrier_id]}（恒等写像でない）",
              file=sys.stderr)
        failures.append(("particles-reverse-identity", None))
        return 1
    # アイテムの反転は build_reverse_items の結果（名前ベース）を使う。
    reverse_truth["items"] = dict(rev755)
    with open(data_dir / "maps755-reverse.json", "w", encoding="utf-8") as f:
        json.dump({k: {str(i): v for i, v in m.items()}
                   for k, m in reverse_truth.items()}, f)

    # --- invert() の結果と名前の真値の差分（補正）。wiring で
    # RegistryRemapper.invert() に渡す。---
    corrections = {}
    for kind in KINDS:
        ranges, overrides = compress(maps755[kind], len(a756[kind]))
        inv_list = invert_java(ranges, overrides, len(a756[kind]),
                               len(a755[kind]), up755_fallbacks[kind])
        corr = [[y, reverse_truth[kind][y]] for y in range(len(a755[kind]))
                if inv_list[y] != reverse_truth[kind][y]]
        corrections[kind] = corr
        print(f"[755] corrections {kind}: {len(corr)} 件 {corr[:5]}")
    with open(data_dir / "maps755-corrections.json", "w", encoding="utf-8") as f:
        json.dump(corrections, f)

    # 反転表を Java の表として出す（*_755_TO_756_*）。
    for kind in KINDS:
        emit_map(out, f"{KIND_JAVA[kind]}_755_TO_756",
                 reverse_truth[kind], len(a755[kind]), source_tag="755")
    # ITEM_REVERSE_755 と ITEM_755_TO_756 が同じことを確認する。
    if reverse_truth["items"] != {i: v for i, v in enumerate(
            build_inverse(maps755["items"], len(a756["items"]),
                          len(a755["items"]), a756["items"],
                          a755["items"]))}:
        print("[755] ITEM_REVERSE_755 と ITEM_755_TO_756 が違う",
              file=sys.stderr)
        failures.append(("item-reverse-mismatch", None))
        return 1

    # --- 音の名前の表（Stop Sound 用）。756→755 は SOUND_FALLBACK_755
    # そのもの。755→756 は名前の真値の反転表から作る（invert() の
    # first-wins では汎用の音（music.game・entity.item.pickup・
    # block.crop.break）が 1.17.1 の新音にすり替わるため、
    # sound_name_table_mid_to_target は使わない）。---
    for table_name, table in (
            ("SOUND_NAME_FALLBACKS_755", SOUND_FALLBACK_755),
            ("SOUND_NAME_FALLBACKS_756_TO_755", SOUND_FALLBACK_755)):
        lines = [f"    private static final String[][] {table_name} = {{"]
        for k, v in sorted(table.items()):
            lines.append(f'            {{"{k}", "{v}"}},')
        lines.append("    };")
        out.append("\n".join(lines))
    table_755_to_756 = {}
    for id755, name755 in enumerate(a755["sounds"]):
        id756 = reverse_truth["sounds"][id755]
        name756 = a756["sounds"][id756]
        if name756 != name755:
            table_755_to_756[name755] = name756
    lines = ["    private static final String[][] SOUND_NAME_FALLBACKS_755_TO_756 = {"]
    for k, v in sorted(table_755_to_756.items()):
        lines.append(f'            {{"{k}", "{v}"}},')
    lines.append("    };")
    out.append("\n".join(lines))
    print(f"[755] sound name table 755->756: {len(table_755_to_756)} entries")

    # --- 属性の表（registries.json の protocol_id 順で作る。1.17／1.17.1
    # とも 13件・同一順序のため恒等写像）。1.17 の Update Attributes は
    # 属性のキーを文字列で送る（mc-1.17-protocol.json の
    # packet_entity_update_attributes は "name": "string"。数値の mapper
    # は後のバージョンで導入）ため、1.17 には照合する mapper が無い。
    # 情報源は registries.json の protocol_id 順だけ（1.17／1.17.1 で
    # 同一順序なのは上で確認済み）。---
    attrs755 = registry_order(reg755, "minecraft:attribute")
    attrs756 = registry_order(reg756, "minecraft:attribute")
    if attrs755 != attrs756:
        print("[755] attributes: 1.17 と 1.17.1 の順序が違う", file=sys.stderr)
        failures.append(("attributes-order", (attrs755, attrs756)))
        return 1
    attr_756_to_755 = {i: i for i in range(len(attrs756))}
    attr_755_to_756 = {i: i for i in range(len(attrs755))}
    iv, ov = compress(attr_756_to_755, len(attrs756))
    out.append(java_int3("ATTRIBUTE_755_RANGES", iv))
    out.append(java_int2("ATTRIBUTE_755_OVERRIDES", ov))
    out.append(f"    // ATTRIBUTE_755: 756 entries={len(attrs756)} "
               f"ranges={len(iv)} overrides={len(ov)}")
    iv, ov = compress(attr_755_to_756, len(attrs755))
    out.append(java_int3("ATTRIBUTE_REVERSE_755_RANGES", iv))
    out.append(java_int2("ATTRIBUTE_REVERSE_755_OVERRIDES", ov))
    out.append(f"    // ATTRIBUTE_REVERSE_755: 755 entries={len(attrs755)} "
               f"ranges={len(iv)} overrides={len(ov)}")
    out.append(java_string_array("ATTRIBUTE_755_NAMES", attrs755))
    print(f"[755] attributes: 756={len(attrs756)} 755={len(attrs755)} identity")

    # --- エンチャントの表。1.17 と 1.17.1 は 38件・同一順序のため恒等写像。---
    ench755 = [e["name"] for e in load(data_dir / "enchantments-1.17.json")]
    ench756 = [e["name"] for e in load(data_dir / "enchantments-1.17.json")]
    if ench755 != a755["enchantments"] or ench756 != a756["enchantments"]:
        print("[755] enchantments: minecraft-data と ViaVersion の順序が違う",
              file=sys.stderr)
        failures.append(("enchantments-order", None))
        return 1
    ench_756_to_755 = {i: (ench755.index(n) if n in ench755 else -1)
                       for i, n in enumerate(ench756)}
    ench_755_to_756 = {i: (ench756.index(n) if n in ench756 else -1)
                       for i, n in enumerate(ench755)}
    iv, ov = compress(ench_756_to_755, len(ench756))
    out.append(java_int3("ENCHANTMENT_755_RANGES", iv))
    out.append(java_int2("ENCHANTMENT_755_OVERRIDES", ov))
    out.append(f"    // ENCHANTMENT_755: 756 entries={len(ench756)} "
               f"ranges={len(iv)} overrides={len(ov)}")
    iv, ov = compress(ench_755_to_756, len(ench755))
    out.append(java_int3("ENCHANTMENT_REVERSE_755_RANGES", iv))
    out.append(java_int2("ENCHANTMENT_REVERSE_755_OVERRIDES", ov))
    out.append(f"    // ENCHANTMENT_REVERSE_755: 755 entries={len(ench755)} "
               f"ranges={len(iv)} overrides={len(ov)}")
    print(f"[755] enchantments: 756={len(ench756)} 755={len(ench755)} identity")

    # --- バイオームの表。minecraft-data の biomes.json の id を使う
    # （build/remap-data/biomes-1.17.json。1.17 と 1.17.1 で同一ファイル）。
    # 1.17 と 1.17.1 の id はどちらも 0〜175 の非連番（旧来の数値バイオームID）
    # で 81 件。ワイヤ上の id がこの通りであることは Join Game の
    # dimensionCodec との突き合わせ（§11.6 の Join Game codec 確認）で
    # 確認する（この時点では未確認。biomes.json の id を使うのは 756 の
    # 前例と同じで、756 では実バニラ 1.17.1 の生キャプチャで確認済み）。
    # registries.json には minecraft:worldgen/biome が無い（バイオームは
    # Join Game の dimensionCodec NBT で送られるため）。
    # 順向き（756→755）は RANGES＋OVERRIDES で表す（756 側も 81 件・
    # id 0〜175 の非連番で、755 側の値も非連番だが区間＋上書きで表せる）。
    # 反転（755→756）は 755 側の id 域 0〜175 の 176件の表にする
    # （隙間の id は代替先の平原（756 id 1）で埋める）。---
    # 重要：順向き表もワイヤ id 空間（0〜175 の 176 件）で作る。
    # 756 のときは 757 側が 0〜60 の連番で「リストの位置 == ワイヤ id」
    # だったため build_name_map（位置で index する）が使えたが、756 側
    # は id 0〜175 の非連番で位置 != ワイヤ id になるため、build_name_map
    # は使えない（None を含むリストを渡すと None がキーとして扱われて
    # 誤写像になる）。756 と 755 は同じ biomes.json を使うため、ワイヤ id
    # 空間での恒等写像（実在 id は同 id、隙間は平原（1））を直接作る。
    # アクセサ（mapBiome756To755／mapBiome755To756）はワイヤ id で引く。
    biomes755_by_id = {}
    for e in load(data_dir / "biomes-1.17.json"):
        biomes755_by_id[e["id"]] = e["name"]
    max_biome_755 = max(biomes755_by_id)
    biomes755_list = [biomes755_by_id.get(i) for i in range(max_biome_755 + 1)]
    # 756 側も同じファイル（id→名前は同一）なので同じ 176 件リスト。
    biomes756_list = list(biomes755_list)
    if (len([b for b in biomes755_list if b is not None]),
            len([b for b in biomes756_list if b is not None])) != (81, 81):
        print(f"[755] biomes: 件数が想定と違う "
              f"755={len([b for b in biomes755_list if b is not None])} "
              f"756={len([b for b in biomes756_list if b is not None])}",
              file=sys.stderr)
        failures.append(("biomes-count", None))
        return 1
    m_bio = {w: (w if biomes756_list[w] is not None else 1)
             for w in range(max_biome_755 + 1)}
    print("[755] biomes forward fallbacks: {}（同一ファイルのため恒等写像。隙間は平原（1））")
    emit_map(out, "BIOME_755", m_bio, len(biomes756_list), source_tag="756")
    rev_bio = build_inverse(m_bio, len(biomes756_list), len(biomes755_list),
                            biomes756_list, biomes755_list)
    # 削除されたバイオームは無い（REVERSE_BIOME_FALLBACK_755 は空）ため
    # パッチは 0 件のはず。ループは空のまま回る。
    biomes755_name_to_id = {n: i for i, n in biomes755_by_id.items()}
    biomes756_name_to_id = dict(biomes755_name_to_id)
    patched_bio = 0
    for name755, name756 in REVERSE_BIOME_FALLBACK_755.items():
        id755 = biomes755_name_to_id[name755]
        id756 = biomes756_name_to_id[name756]
        if rev_bio[id755] < 0:
            rev_bio[id755] = id756
            patched_bio += 1
        elif rev_bio[id755] != id756:
            print(f"[755] biomes: 反転表の {name755}({id755}) が想定と違う："
                  f"{rev_bio[id755]} != {id756}", file=sys.stderr)
            failures.append(("biomes-reverse", (name755, rev_bio[id755], id756)))
            return 1
    print(f"[755] biomes reverse: 削除バイオームの明示 {patched_bio} 件")
    gaps = [i for i, b in enumerate(biomes755_list) if b is None]
    missing_bio = [i for i, v in enumerate(rev_bio)
                   if v < 0 and biomes755_list[i] is not None]
    if missing_bio:
        print(f"[755] biomes: 反転表に対応なしの実在バイオーム {missing_bio}",
              file=sys.stderr)
        failures.append(("biomes-reverse-missing", missing_bio))
        return 1
    rev_bio = [v if v >= 0 else 1 for v in rev_bio]
    print(f"[755] biomes reverse: 隙間の id {len(gaps)} 件は平原（756 id 1）で埋める")
    emit_map(out, "BIOME_755_TO_756",
             {i: v for i, v in enumerate(rev_bio)}, len(biomes755_list),
             source_tag="755")
    print(f"[755] biomes: 756={len([b for b in biomes756_list if b is not None])} "
          f"755={len(biomes755_by_id)} "
          f"(id 0〜{max_biome_755}、隙間 {len(gaps)} 件)")

    # --- ダメージ種別（756 と同じ扱い）。damage_type レジストリは
    # 1.17／1.17.1 のどちらにも無い（1.19.4／762 で追加）。756 の前例
    # （RegistryTables756.java）にならい、762 の 42件の恒等写像を置くが
    # ワイヤ上の呼び出し元は無い。---
    dmg = {i: i for i in range(42)}
    emit_map(out, "DAMAGE_TYPE_755", dmg, 42, source_tag="756")
    emit_map(out, "DAMAGE_TYPE_755_TO_756", dmg, 42, source_tag="755")
    print("[755] damage_type: 762 の 42件の恒等写像（プレースホルダ）")

    # エフェクト（32→32、同一順序）とブロックエンティティ（34→34、
    # 同一順序）は数値の表を作らない。エフェクトは 756 の前例と同じく
    # wiring 側で恒等写像にする（新規も削除も無いため DROP は無い）。
    # ブロックエンティティも新規・削除が無いため恒等写像。ここでは件数だけ記録する。
    eff755 = registry_order(reg755, "minecraft:mob_effect")
    eff756 = registry_order(reg756, "minecraft:mob_effect")
    print(f"[755] effects: 756={len(eff756)} 755={len(eff755)} "
          f"identical={eff755 == eff756}")
    be755 = registry_order(reg755, "minecraft:block_entity_type")
    be756 = registry_order(reg756, "minecraft:block_entity_type")
    print(f"[755] block entities: 756={len(be756)} 755={len(be755)} "
          f"identical={be755 == be756}")

    text = "\n".join(out) + "\n"
    (data_dir / "gen755.txt").write_text(text, encoding="utf-8")
    print(f"Java の表を {data_dir / 'gen755.txt'} に書き出しました。")
    print("完了：未対応 0 件。")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
