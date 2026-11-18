#!/usr/bin/env python3
"""RegistryRemapper.java に 755（1.17）対応を差し込む。

雛形は tools/insert-756-remapper.py（隣接連鎖の配線＋書き込み前の
全入力検証・冪等ガード）。アンカー付き挿入のみ行う。アンカーが
見つからなければ失敗して何も書かない。
使い方: python3 insert-755-remapper.py
書き換えた後は必ず git diff で内容を確認してから javac で確認すること。

配線の方針（STAGE-A-755-NOTES.md §11.8 の申し送り）:
- 755 は合成表を作らない。755↔763〜775 は 755→756→759→760→761→
  762→763→合成表の連鎖で到達する（756〜762 と同じ隣接連鎖グループ）。
- 順向き（756→755）の展開済み表は RegistryTables755 の *_755_* 表を
  expand() で展開する。
- 反転（755→756）の表は invert() で作る。755 と 756 はレジストリが同一の
  ため invert() だけでも名前の真値と一致する（補正は 0 件）。756 では
  パーティクル 1 件・バイオーム 26 件の食い違いがあった（first-wins の罠）
  が、755 には当たらない。UP_755 補正（755 id → 756 id の対）は空だが、
  756 と同じ構造を保つために置く。パーティクルの補正は
  tools/maps755-corrections.json から、バイオームの補正は
  RegistryTables755.java の BIOME_755_TO_756_* 表との差分から
  このスクリプトが機械的に作る（gen の corrections JSON は 7 種のみ）。
  アイテムだけは 766／777 の前例にならい、名前から作った既存の逆変換表
  （ITEM_REVERSE_755）を expand() でそのまま使う。
- エフェクト・ブロックエンティティ種別は表を作らずハードコードする
  （mapEffectUp774／mapBlockEntityTypeUp774 の前例）。
  エフェクト：756 と 755 は 32件・同一順序のため恒等写像（DROP なし）。
  ブロックエンティティ：34件・同一順序のため恒等写像（DROP なし）。
- ダメージ種別は 42 件の恒等写像のプレースホルダ（DAMAGE_TYPE_755_MAP／
  DAMAGE_TYPE_REVERSE_755_MAP。756 の DAMAGE_TYPE_756 と同じ扱い。
  1.17／1.17.1 のどちらにも damage_type レジストリは無いため、
  ワイヤ上の呼び出し元は無い）。
- 755 の展開済み表は private static final class Maps755 にまとめる。
  RegistryRemapper の static 初期化子は 64KB 制限に達しているため
  （756 追加後も外側は 64,669 バイト。残り 866 バイト）、755 の表を外側の
  static 初期化子に入れると code too large でコンパイルできない。
  755 以降も同じ方式にすること。
- バイオーム：順向き表（BIOME_755）は 756 側 81 件、反転表
  （BIOME_755_TO_756）は 755 側 176 件（id 0〜175。隙間の id は
  平原（756 id 1）で埋める）。

書き込み前の検証（失敗したら何も書き込まない）:
- maps755-corrections.json の件数（全種 0 件）。
- RegistryTables755.java から読んだ表で、Java の expand()／invert() と
  同じ計算を Python で行い、invert(順向き)＋補正＝名前の真値
  （*_755_TO_756_* 表の展開）と全 7 種で一致することを確認する。
  バイオームは invert() と名前の真値の差分を補正として作り、
  invert(順向き)＋補正＝名前の真値（176 件）を確認する。
  パーティクルの invert() の fallback は wiring と同じ
  AMBIENT_PARTICLE_756（0）を使う（生成スクリプトは entity_effect
  （22）を使っているが、補正を当てた後は -1 が残らないため値は同じ。
  756 の前例と同じ扱い）。
  アイテムは invert()＝ITEM_REVERSE_755 の展開とも一致することを確認する。
- ダメージ種別・エンチャント・属性・バイオームの展開結果の件数と代表値を
  確認する。
"""
from pathlib import Path
import json
import re
import sys

ROOT = Path(__file__).resolve().parent.parent
REMAPPER = ROOT / "src/main/java/io/detergentmc/protocol/RegistryRemapper.java"
TABLES = ROOT / "src/main/java/io/detergentmc/protocol/RegistryTables755.java"
CORRECTIONS_JSON = ROOT / "build/remap-data/maps755-corrections.json"

# 756側の最大ID（RegistryRemapper.java の MAX_*_756 と同じ値）。
MAX_756 = {
    "blockstates": 20341, "items": 1099, "entities": 112, "sounds": 1189,
    "particles": 88, "blocks": 897, "menus": 23, "biomes": 175,
}
# 755側の最大ID（vv-1.17.json 等の実測件数−1。バイオームだけ id の最大値 175）。
MAX_755 = {
    "blockstates": 20341, "items": 1099, "entities": 112, "sounds": 1189,
    "particles": 88, "blocks": 897, "menus": 23, "biomes": 175,
}
# 756側の代替先（invert() の fallback。gen-755-tables.py の UP_755_FALLBACKS
# と同じ。ただしパーティクルだけ wiring では AMBIENT_PARTICLE_756（0）
# を使う。生成スクリプトの entity_effect（22）とは違うが、補正を当てた
# 後は -1 が残らないため結果は同じ。756 の前例と同じ扱い）。
FALLBACK_756 = {
    "blockstates": 1, "items": 1, "entities": 64, "sounds": 1005,
    "particles": 0, "blocks": 1, "menus": 11, "biomes": 1,
}
# 755側の代替先（expand() の fallback）。
FALLBACK_755 = {
    "blockstates": 1, "items": 1, "entities": 64, "sounds": 1005,
    "particles": 0, "blocks": 1, "menus": 11, "biomes": 1,
}
# 表名（RegistryTables755.java）。down は 756→755、up は 755→756。
KINDS = ["blockstates", "items", "entities", "sounds", "particles", "blocks", "menus"]
TABLE_PREFIX = {
    "blockstates": "BLOCK_STATE", "items": "ITEM", "entities": "ENTITY_TYPE",
    "sounds": "SOUND", "particles": "PARTICLE", "blocks": "BLOCK", "menus": "MENU",
}


def expand(ranges, overrides, size, fallback):
    """RegistryRemapper.expand() と同じ計算。"""
    out = [fallback] * size
    for lo, hi, delta in ranges:
        for i in range(lo, hi + 1):
            if i < size:
                out[i] = i + delta
    for x, y in overrides:
        if 0 <= x < size:
            out[x] = y
    return out


def invert(ranges, overrides, from_size, to_size, fallback, corrections):
    """RegistryRemapper.invert() と同じ計算。"""
    inverse = [-1] * to_size
    for lo, hi, delta in ranges:
        for x in range(lo, hi + 1):
            if x < from_size:
                y = x + delta
                if 0 <= y < to_size and inverse[y] < 0:
                    inverse[y] = x
    for x, y in overrides:
        if 0 <= x < from_size and 0 <= y < to_size and inverse[y] < 0:
            inverse[y] = x
    if corrections is not None:
        for to_id, from_id in corrections:
            if 0 <= to_id < to_size and 0 <= from_id < from_size:
                inverse[to_id] = from_id
    for i in range(to_size):
        if inverse[i] < 0:
            inverse[i] = fallback
    return inverse


def parse_int_table2(text, name):
    """{a, b} または {a, b, c} の対・三つ組を読む。"""
    m = re.search(r"static final int\[\]\[\] " + re.escape(name) + r" = \{(.*?)\n    \};",
                  text, re.DOTALL)
    if m is None:
        raise SystemExit(f"表が見つからない: {name}")
    out = []
    for tup in re.findall(r"\{(-?\d+), (-?\d+)(?:, (-?\d+))?\}", m.group(1)):
        vals = [int(v) for v in tup if v != ""]
        out.append(tuple(vals))
    return out


def java_int2_array(pairs, per_line=8):
    """int[][] の補正を Java のリテラルにする（1行に8対まで）。"""
    lines = []
    for i in range(0, len(pairs), per_line):
        chunk = pairs[i:i + per_line]
        lines.append("            " + ", ".join("{%d, %d}" % (a, b) for a, b in chunk) + ",")
    return "\n".join(lines)


def main():
    tables_text = TABLES.read_text(encoding="utf-8")
    corrections = json.loads(CORRECTIONS_JSON.read_text(encoding="utf-8"))

    # --- 検証1：補正 JSON の件数（全種 0 件。1.17 と 1.17.1 の
    # レジストリは同一のため invert() と名前の真値に差分は無い）---
    expected_counts = {"blockstates": 0, "items": 0, "entities": 0, "sounds": 0,
                       "particles": 0, "blocks": 0, "menus": 0}
    for kind, expected in expected_counts.items():
        got = corrections.get(kind)
        if got is None:
            raise SystemExit(f"補正 JSON に {kind} が無い")
        if len(got) != expected:
            raise SystemExit(f"補正 {kind} の件数が違う：{len(got)} != {expected}")
        for to_id, from_id in got:
            if not (0 <= to_id <= MAX_755[kind] and 0 <= from_id <= MAX_756[kind]):
                raise SystemExit(f"補正 {kind} の範囲外の対：[{to_id}, {from_id}]")
    print("補正 JSON の件数を確認：全種 0 件")

    # --- 検証2：invert(順向き)＋補正＝名前の真値（*_755_TO_756_* 表の展開）---
    for kind in KINDS:
        prefix = TABLE_PREFIX[kind]
        down_ranges = parse_int_table2(tables_text, prefix + "_755_RANGES")
        down_overrides = parse_int_table2(tables_text, prefix + "_755_OVERRIDES")
        truth_ranges = parse_int_table2(tables_text, prefix + "_755_TO_756_RANGES")
        truth_overrides = parse_int_table2(tables_text, prefix + "_755_TO_756_OVERRIDES")
        truth = expand(truth_ranges, truth_overrides, MAX_755[kind] + 1, FALLBACK_756[kind])
        corr = corrections[kind] if corrections[kind] else None
        got = invert(down_ranges, down_overrides,
                     MAX_756[kind] + 1, MAX_755[kind] + 1, FALLBACK_756[kind], corr)
        if got != truth:
            diffs = [i for i, (a, b) in enumerate(zip(got, truth)) if a != b]
            raise SystemExit(f"invert＋補正が名前の真値と違う（{kind}、{len(diffs)} 件）")
        print(f"invert＋補正＝名前の真値：{kind}（{len(truth)} 件）")
    # アイテムは invert()＝ITEM_REVERSE_755 の展開とも一致することを確認する。
    rev_ranges = parse_int_table2(tables_text, "ITEM_REVERSE_755_RANGES")
    rev_overrides = parse_int_table2(tables_text, "ITEM_REVERSE_755_OVERRIDES")
    rev = expand(rev_ranges, rev_overrides, MAX_755["items"] + 1, FALLBACK_756["items"])
    item_ranges = parse_int_table2(tables_text, "ITEM_755_RANGES")
    item_overrides = parse_int_table2(tables_text, "ITEM_755_OVERRIDES")
    item_inv = invert(item_ranges, item_overrides,
                      MAX_756["items"] + 1, MAX_755["items"] + 1, FALLBACK_756["items"], None)
    if item_inv != rev:
        raise SystemExit("invert(アイテム) が ITEM_REVERSE_755 と違う")
    print("invert(アイテム)＝ITEM_REVERSE_755 の展開（1100 件）")

    # --- 検証3：バイオームは invert(補正あり)＝名前の真値 ---
    # 755 と 756 はバイオームが同一ファイル（pc/1.17、81 件・id 0〜175）の
    # ため、invert() だけでも名前の真値と一致するはず（差分 0 件の見込み）。
    # 756 では 26 件の食い違いがあった（first-wins の罠。削除された
    # バイオーム 25 件と snowy_tundra 1 件）が、755 には削除・改名が無い
    # ためこの罠は当たらない。補正は差分から機械的に作る（手入力しない）
    # 仕組みだけ残し、BIOME_UP_755_CORRECTIONS は空になる見込み。
    bio_ranges = parse_int_table2(tables_text, "BIOME_755_RANGES")
    bio_overrides = parse_int_table2(tables_text, "BIOME_755_OVERRIDES")
    bio_truth = expand(parse_int_table2(tables_text, "BIOME_755_TO_756_RANGES"),
                       parse_int_table2(tables_text, "BIOME_755_TO_756_OVERRIDES"),
                       MAX_755["biomes"] + 1, FALLBACK_756["biomes"])
    bio_inv = invert(bio_ranges, bio_overrides,
                     MAX_756["biomes"] + 1, MAX_755["biomes"] + 1, FALLBACK_756["biomes"], None)
    bio_diffs = [(i, bio_truth[i]) for i in range(len(bio_truth)) if bio_inv[i] != bio_truth[i]]
    if len(bio_truth) != 176:
        raise SystemExit(f"バイオームの反転表が 176 件でない：{len(bio_truth)}")
    bio_inv_fixed = invert(bio_ranges, bio_overrides,
                           MAX_756["biomes"] + 1, MAX_755["biomes"] + 1,
                           FALLBACK_756["biomes"], bio_diffs)
    if bio_inv_fixed != bio_truth:
        raise SystemExit("invert(バイオーム)＋補正が名前の真値と違う")
    # 代表値：biomes-1.17.json から id を引いて恒等写像を確認する。
    # the_void・snowy_tundra・snowy_mountains・dripstone_caves・
    # lush_caves・plains・mountains は順向き・反転とも恒等写像。隙間の id は平原（1）。
    # （meadow は 1.18 の新規バイオームで 1.17 には無いため代表値に入れない）
    biomes755 = json.loads((ROOT / "build/remap-data/biomes-1.17.json")
                           .read_text(encoding="utf-8"))
    bio_id = {e["name"]: e["id"] for e in biomes755}
    gaps = [i for i in range(176) if i not in set(bio_id.values())]
    bio_down = expand(bio_ranges, bio_overrides, MAX_756["biomes"] + 1, FALLBACK_755["biomes"])
    checks = []
    for name in ("the_void", "snowy_tundra", "snowy_mountains",
                 "dripstone_caves", "lush_caves", "plains", "mountains"):
        bid = bio_id[name]
        checks.append((bio_down[bid], bid))
        checks.append((bio_truth[bid], bid))
    checks.append((bio_truth[gaps[0]], 1))
    if any(got != want for got, want in checks):
        raise SystemExit(f"バイオームの真値がおかしい：{checks}")
    print(f"invert(バイオーム)＋補正＝名前の真値（176 件。補正 {len(bio_diffs)} 件・"
          f"代表値 {len(checks)} 件を確認。隙間の先頭 id {gaps[0]}→平原）")

    # --- 検証4：ダメージ種別・エンチャント・属性の展開 ---
    dmg = expand(parse_int_table2(tables_text, "DAMAGE_TYPE_755_RANGES"),
                 parse_int_table2(tables_text, "DAMAGE_TYPE_755_OVERRIDES"), 42, 16)
    dmg_rev = expand(parse_int_table2(tables_text, "DAMAGE_TYPE_755_TO_756_RANGES"),
                     parse_int_table2(tables_text, "DAMAGE_TYPE_755_TO_756_OVERRIDES"), 42, 16)
    if dmg != list(range(42)) or dmg_rev != list(range(42)):
        raise SystemExit("ダメージ種別が 42 件の恒等写像ではない")
    enc = expand(parse_int_table2(tables_text, "ENCHANTMENT_755_RANGES"),
                 parse_int_table2(tables_text, "ENCHANTMENT_755_OVERRIDES"), 38, -1)
    enc_rev = expand(parse_int_table2(tables_text, "ENCHANTMENT_REVERSE_755_RANGES"),
                     parse_int_table2(tables_text, "ENCHANTMENT_REVERSE_755_OVERRIDES"), 38, -1)
    if len(enc) != 38 or enc != list(range(38)):
        raise SystemExit(f"エンチャントの展開がおかしい：{enc[:10]}")
    if len(enc_rev) != 38 or enc_rev != list(range(38)):
        raise SystemExit(f"エンチャント逆向きの展開がおかしい：{enc_rev[:10]}")
    attr = expand(parse_int_table2(tables_text, "ATTRIBUTE_755_RANGES"),
                  parse_int_table2(tables_text, "ATTRIBUTE_755_OVERRIDES"), 13, -1)
    if attr != list(range(13)):
        raise SystemExit("属性が 13 件の恒等写像ではない")
    print("ダメージ種別 42 件恒等・エンチャント 38/38 件恒等・属性 13 件恒等を確認")

    # --- 補正の Java テキストを生成する ---
    particle_corr_java = java_int2_array([tuple(p) for p in corrections["particles"]])
    # バイオームの補正は上の検証3で差分から作ったもの（手入力しない）。
    biome_corr_java = java_int2_array(bio_diffs)

    src = REMAPPER.read_text(encoding="utf-8")

    def insert_before(anchor, text, what, marker):
        nonlocal src
        if marker in src:
            print(f"skip（既存）: {what}")
            return
        idx = src.find(anchor)
        if idx < 0:
            print(f"アンカーが見つからない: {what}: {anchor[:80]!r}", file=sys.stderr)
            raise SystemExit(1)
        src = src[:idx] + text + src[idx:]

    def insert_after(anchor, text, what, marker):
        nonlocal src
        if marker in src:
            print(f"skip（既存）: {what}")
            return
        idx = src.find(anchor)
        if idx < 0:
            print(f"アンカーが見つからない: {what}: {anchor[:80]!r}", file=sys.stderr)
            raise SystemExit(1)
        idx += len(anchor)
        src = src[:idx] + text + src[idx:]

    # 1. import
    insert_before("import static io.detergentmc.protocol.RegistryTables756.*;",
                  "import static io.detergentmc.protocol.RegistryTables755.*;\n",
                  "import RegistryTables755", "RegistryTables755.*")

    # 2. PROTOCOL_755（PROTOCOL_756 の直前）
    insert_before("    /** 756（1.17.1）のプロトコル番号。 */",
                  "    /** 755（1.17）のプロトコル番号。 */\n"
                  "    public static final int PROTOCOL_755 = 755;\n",
                  "PROTOCOL_755", "PROTOCOL_755 = 755")

    # 3. 755定数（GENERIC_DAMAGE_TYPE_756 の直後）
    constants_755 = """
    /** 755側の最大の番号（ViaVersion mapping-1.17.json の件数−1。バイオームだけ id の最大値）。 */
    public static final int MAX_BLOCK_STATE_755 = 20341;
    public static final int MAX_ITEM_755 = 1099;
    public static final int MAX_ENTITY_TYPE_755 = 112;
    public static final int MAX_SOUND_755 = 1189;
    public static final int MAX_PARTICLE_755 = 88;
    public static final int MAX_BLOCK_755 = 897;
    public static final int MAX_MENU_755 = 23;
    /** 755側のエンチャントは 0〜37（38件。756と同一順序）。 */
    public static final int MAX_ENCHANTMENT_755 = 37;
    /** 755側のバイオームは 0〜175（81件。id は 0〜175 の非連番）。 */
    public static final int MAX_BIOME_755 = 175;
    /** 755側のダメージ種別は 0〜41（42件。762 の順序の恒等写像。実バニラで検証する）。 */
    public static final int MAX_DAMAGE_TYPE_755 = 41;
    /** 755側のブロックエンティティ種別は 0〜33（34件。756と同一順序）。 */
    public static final int MAX_BLOCK_ENTITY_755 = 33;
    /** 755側の属性は 0〜12（13件。756と同一順序）。 */
    public static final int MAX_ATTRIBUTE_755 = 12;
    /** 755側のエフェクトは 1〜32（32件。756と同一順序。1ベース）。 */
    public static final int MAX_EFFECT_755 = 32;

    /** 755側の代替先（mapping-1.17.json の番号。Speed だけ 1ベースで 1）。 */
    public static final int STONE_BLOCK_STATE_755 = 1;
    public static final int AIR_BLOCK_STATE_755 = 0;
    public static final int STONE_ITEM_755 = 1;
    public static final int PIG_ENTITY_TYPE_755 = 64;
    public static final int GENERIC_SOUND_755 = 1005; // block.stone.break
    public static final int AMBIENT_PARTICLE_755 = 0; // ambient_entity_effect
    public static final int STONE_BLOCK_755 = 1;
    public static final int CRAFTING_MENU_755 = 11;
    public static final int SPEED_EFFECT_755 = 1;
    /** 755側の平原バイオーム（minecraft-data pc/1.17/biomes.json）。 */
    public static final int PLAINS_BIOME_755 = 1;
    /** 755側の generic ダメージ種別（762 と同一順序と仮定）。 */
    public static final int GENERIC_DAMAGE_TYPE_755 = 16;
"""
    insert_after("    public static final int GENERIC_DAMAGE_TYPE_756 = 16;\n",
                 constants_755, "755定数", "MAX_BLOCK_STATE_755")

    # 4. 755の展開済み表（757 ⇔ 756 の展開済み表の直前）
    maps_755 = """
    // ------------------------------------------------------------------
    // 756 ⇔ 755（1.17.1 ⇔ 1.17）の展開済み表。RegistryTables755
    // の区間＋上書きの表をクラスロード時に int[] へ展開する。
    // 順向き（756→755）は expand()、反転（755→756）は invert() で作る。
    // 755 と 756 はレジストリが同一のため、invert() だけでも名前の真値と
    // 一致する（補正は 0 件）。756 ではパーティクル 1 件・バイオーム 26 件の
    // 食い違いがあった（first-wins の罠）が、755 には当たらない。下の
    // UP_755 補正は空だが、756 と同じ構造を保つために置く。
    // アイテムだけは 766／777 の前例にならい、名前から作った既存の
    // 逆変換表（ITEM_REVERSE_755）を expand() でそのまま使う。
    // エフェクト・ブロックエンティティ種別は表を作らない（アクセサで
    // ハードコードする。756 と 755 はどちらも 32件・34件で同一順序のため
    // 恒等写像で、DROP は無い）。755 は合成表を作らない。
    // バイオームの反転表は 755 側 176 件（id 0〜175。隙間の id は平原）。
    // 755 の表は private static final class Maps755 にまとめる（RegistryRemapper の
    // static 初期化子は 64KB 制限に達しているため。756 追加後も外側は 64,669 バイト
    // のまま（残り 866 バイト）。Maps756 方式で外側は 0 バイト増）。
    // ------------------------------------------------------------------


    /** 756 ⇔ 755 の展開済み表をまとめたホルダ。RegistryRemapper の static 初期化子は
     * 64KB 制限に達しているため（756 追加後も外側は 64,669 バイト。残り 866 バイト）、755 の表の
     * 初期化はこの別クラスの static 初期化子に分けてある。755 以降も同じ方式にすること。
     * 初回アクセス時に初期化される（クラスロード時の初期化と等価。expand()／invert() は
     * 純粋関数）。アクセサからは Maps755.〜 で参照する。 */
    private static final class Maps755 {
        /** パーティクルの反転の補正（755 id → 756 id）。0 件。755 と 756 は
         * パーティクルが同一順序のため、invert() だけでも名前の真値と一致する。
         * 756 では light（3）に preimage が無く 1 件の補正が必要だったが、
         * 755 には当たらない。756 と同じ構造を保つために空の表を置く。
         * tools/maps755-corrections.json から生成。検証は
         * tools/verify-755-tables.py のチェックDを参照。 */
        private static final int[][] PARTICLE_UP_755_CORRECTIONS = {
""" + particle_corr_java + """
        };

        /** バイオームの反転の補正（755 id → 756 id）。0 件。755 と 756 は
         * バイオームが同一ファイルのため、invert() だけでも名前の真値と一致する。
         * 756 では削除された 25 件と snowy_tundra 1 件の計 26 件の補正が
         * 必要だったが、755 には削除・改名が無いため当たらない。756 と同じ
         * 構造を保つために空の表を置く。
         * RegistryTables755.java の BIOME_755_TO_756_* 表との差分から生成。
         * 検証は tools/verify-755-tables.py のチェックGを参照。 */
        private static final int[][] BIOME_UP_755_CORRECTIONS = {
""" + biome_corr_java + """
        };

        /** 756のブロック状態ID → 755のブロック状態ID（RegistryTables755。恒等写像）。 */
        private static final int[] BLOCK_STATE_756_TO_755_MAP =
                expand(BLOCK_STATE_755_RANGES, BLOCK_STATE_755_OVERRIDES, MAX_BLOCK_STATE_756 + 1,
                        STONE_BLOCK_STATE_755);
        /** 755のブロック状態ID → 756のブロック状態ID（反転。恒等写像）。 */
        private static final int[] BLOCK_STATE_755_TO_756_MAP = invert(
                BLOCK_STATE_755_RANGES, BLOCK_STATE_755_OVERRIDES,
                MAX_BLOCK_STATE_756 + 1, MAX_BLOCK_STATE_755 + 1,
                STONE_BLOCK_STATE_756, null);
        /** 756のアイテムID → 755のアイテムID。 */
        private static final int[] ITEM_756_TO_755_MAP =
                expand(ITEM_755_RANGES, ITEM_755_OVERRIDES, MAX_ITEM_756 + 1, STONE_ITEM_755);
        /** 755のアイテムID → 756のアイテムID（名前から作った逆変換表）。 */
        private static final int[] ITEM_755_TO_756_MAP =
                expand(ITEM_REVERSE_755_RANGES, ITEM_REVERSE_755_OVERRIDES, MAX_ITEM_755 + 1,
                        STONE_ITEM_756);
        /** 756のエンティティ種別ID → 755のエンティティ種別ID（恒等写像）。 */
        private static final int[] ENTITY_TYPE_756_TO_755_MAP =
                expand(ENTITY_TYPE_755_RANGES, ENTITY_TYPE_755_OVERRIDES, MAX_ENTITY_TYPE_756 + 1,
                        PIG_ENTITY_TYPE_755);
        /** 755のエンティティ種別ID → 756のエンティティ種別ID（反転。恒等写像）。 */
        private static final int[] ENTITY_TYPE_755_TO_756_MAP = invert(
                ENTITY_TYPE_755_RANGES, ENTITY_TYPE_755_OVERRIDES,
                MAX_ENTITY_TYPE_756 + 1, MAX_ENTITY_TYPE_755 + 1, PIG_ENTITY_TYPE_756, null);
        /** 756の音ID → 755の音ID。 */
        private static final int[] SOUND_756_TO_755_MAP =
                expand(SOUND_755_RANGES, SOUND_755_OVERRIDES, MAX_SOUND_756 + 1, GENERIC_SOUND_755);
        /** 755の音ID → 756の音ID（反転）。 */
        private static final int[] SOUND_755_TO_756_MAP = invert(
                SOUND_755_RANGES, SOUND_755_OVERRIDES,
                MAX_SOUND_756 + 1, MAX_SOUND_755 + 1,
                GENERIC_SOUND_756, null);
        /** 756のパーティクルID → 755のパーティクルID。 */
        private static final int[] PARTICLE_756_TO_755_MAP =
                expand(PARTICLE_755_RANGES, PARTICLE_755_OVERRIDES, MAX_PARTICLE_756 + 1,
                        AMBIENT_PARTICLE_755);
        /** 755のパーティクルID → 756のパーティクルID（反転＋補正）。 */
        private static final int[] PARTICLE_755_TO_756_MAP = invert(
                PARTICLE_755_RANGES, PARTICLE_755_OVERRIDES,
                MAX_PARTICLE_756 + 1, MAX_PARTICLE_755 + 1,
                AMBIENT_PARTICLE_756, PARTICLE_UP_755_CORRECTIONS);
        /** 756のブロックID → 755のブロックID（恒等写像）。 */
        private static final int[] BLOCK_756_TO_755_MAP =
                expand(BLOCK_755_RANGES, BLOCK_755_OVERRIDES, MAX_BLOCK_756 + 1, STONE_BLOCK_755);
        /** 755のブロックID → 756のブロックID（反転。恒等写像）。 */
        private static final int[] BLOCK_755_TO_756_MAP = invert(
                BLOCK_755_RANGES, BLOCK_755_OVERRIDES,
                MAX_BLOCK_756 + 1, MAX_BLOCK_755 + 1, STONE_BLOCK_756, null);
        /** 756のメニュー種別ID → 755のメニュー種別ID（恒等写像）。 */
        private static final int[] MENU_756_TO_755_MAP =
                expand(MENU_755_RANGES, MENU_755_OVERRIDES, MAX_MENU_756 + 1, CRAFTING_MENU_755);
        /** 755のメニュー種別ID → 756のメニュー種別ID（反転。恒等写像）。 */
        private static final int[] MENU_755_TO_756_MAP = invert(
                MENU_755_RANGES, MENU_755_OVERRIDES,
                MAX_MENU_756 + 1, MAX_MENU_755 + 1, CRAFTING_MENU_756, null);
        /** 756のバイオームID → 755のバイオームID。改名 11件・新規 6件は代替先へ。 */
        private static final int[] BIOME_756_TO_755_MAP =
                expand(BIOME_755_RANGES, BIOME_755_OVERRIDES, MAX_BIOME_756 + 1, PLAINS_BIOME_755);
        /** 755のバイオームID → 756のバイオームID（反転＋補正。176 件）。 */
        private static final int[] BIOME_755_TO_756_MAP = invert(
                BIOME_755_RANGES, BIOME_755_OVERRIDES,
                MAX_BIOME_756 + 1, MAX_BIOME_755 + 1, PLAINS_BIOME_756,
                BIOME_UP_755_CORRECTIONS);
        /** ダメージ種別ID：756 → 755（762 の 42 件の恒等写像。1.17／1.17.1 の
         * どちらにも damage_type レジストリは無いため、ワイヤ上の呼び出し元は無い）。 */
        private static final int[] DAMAGE_TYPE_755_MAP = {
            0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19,
            20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32, 33, 34, 35, 36, 37,
            38, 39, 40, 41
        };
        /** ダメージ種別ID：755 → 756（762 の 42 件の恒等写像。呼び出し元は無い）。 */
        private static final int[] DAMAGE_TYPE_REVERSE_755_MAP = {
            0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19,
            20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32, 33, 34, 35, 36, 37,
            38, 39, 40, 41
        };
        /** 756の属性ID → 755の属性ID。並びは同一。範囲外は {@link #DROP}。 */
        private static final int[] ATTRIBUTE_755_MAP =
                expand(ATTRIBUTE_755_RANGES, ATTRIBUTE_755_OVERRIDES, MAX_ATTRIBUTE_756 + 1, DROP);
        /** 755の属性ID → 756の属性ID。並びは同一。範囲外は {@link #DROP}。 */
        private static final int[] ATTRIBUTE_REVERSE_755_MAP =
                expand(ATTRIBUTE_REVERSE_755_RANGES, ATTRIBUTE_REVERSE_755_OVERRIDES,
                        MAX_ATTRIBUTE_755 + 1, DROP);
        /** 756のエンチャントID → 755のエンチャントID。並びは同一。範囲外は {@link #DROP}。 */
        private static final int[] ENCHANTMENT_755_MAP =
                expand(ENCHANTMENT_755_RANGES, ENCHANTMENT_755_OVERRIDES,
                        MAX_ENCHANTMENT_756 + 1, DROP);
        /** 755のエンチャントID → 756のエンチャントID。並びは同一。範囲外は {@link #DROP}。 */
        private static final int[] ENCHANTMENT_REVERSE_755_MAP =
                expand(ENCHANTMENT_REVERSE_755_RANGES, ENCHANTMENT_REVERSE_755_OVERRIDES,
                        MAX_ENCHANTMENT_755 + 1, DROP);

        /** 756の音の名前 → 755の音の名前（Stop Sound 用）。数値の反転表と一致させる。 */
        private static final Map<String, String> SOUND_NAME_FALLBACK_756_TO_755_MAP =
                buildSoundNameMap(SOUND_NAME_FALLBACKS_756_TO_755);

        /** 755の音の名前 → 756の音の名前（Stop Sound 用）。恒等写像のため空。 */
        private static final Map<String, String> SOUND_NAME_FALLBACK_755_TO_756_MAP =
                buildSoundNameMap(SOUND_NAME_FALLBACKS_755_TO_756);
    }
"""
    insert_before("    // ------------------------------------------------------------------\n"
                  "    // 757 ⇔ 756（1.18／1.18.1 ⇔ 1.17.1）の展開済み表。RegistryTables756\n",
                  maps_755, "755の展開済み表", "BLOCK_STATE_756_TO_755_MAP")

    # 5. アクセサメソッド（757 ⇔ 756 節の直前）
    methods_755 = """
    // ------------------------------------------------------------------
    // 756 ⇔ 755（1.17.1 ⇔ 1.17）
    // パケットIDは 756 と 755 で同一（minecraft-data の protocol.json の
    // 比較では全方向で ID が同一。改名 1 件のみ（destroy_entity→entity_destroy）。
    // packet-ids-755.py の実測も参照）。
    // レジストリは RegistryTables755（ViaVersion mapping-1.17.json
    // から名前で突き合わせたもの。1.17 と 1.17.1 は同じ mapping を使う）。
    // 755↔763〜775 は隣接表の連鎖（755→756→757→759→760→761→762→763→合成表）
    // で到達する。合成表は作らない。
    // Join Game は同一（実バニラ 1.17 と 1.17.1 の生キャプチャで確認。
    // 31,555 バイトで entityId・seed 以外バイト同一。simulationDistance は
    // どちらにも無い）。Respawn も同一。
    // ------------------------------------------------------------------

    /** 756のブロック状態ID → 755のブロック状態ID。範囲外はSTONE_BLOCK_STATE_755 に相当する代替。 */
    public static int mapBlockState756To755(int id756) {
        if (id756 < 0 || id756 >= Maps755.BLOCK_STATE_756_TO_755_MAP.length) {
            logUnknownComposedOnce("756→755", "ブロック状態", id756);
            return STONE_BLOCK_STATE_755;
        }
        return Maps755.BLOCK_STATE_756_TO_755_MAP[id756];
    }

    /** 755のブロック状態ID → 756のブロック状態ID。範囲外はSTONE_BLOCK_STATE_756 に相当する代替。 */
    public static int mapBlockState755To756(int id755) {
        if (id755 < 0 || id755 >= Maps755.BLOCK_STATE_755_TO_756_MAP.length) {
            logUnknownComposedOnce("755→756", "ブロック状態", id755);
            return STONE_BLOCK_STATE_756;
        }
        return Maps755.BLOCK_STATE_755_TO_756_MAP[id755];
    }

    /** 756のアイテムID → 755のアイテムID。範囲外はSTONE_ITEM_755 に相当する代替。 */
    public static int mapItem756To755(int id756) {
        if (id756 < 0 || id756 >= Maps755.ITEM_756_TO_755_MAP.length) {
            logUnknownComposedOnce("756→755", "アイテム", id756);
            return STONE_ITEM_755;
        }
        return Maps755.ITEM_756_TO_755_MAP[id756];
    }

    /** 755のアイテムID → 756のアイテムID。範囲外はSTONE_ITEM_756 に相当する代替。 */
    public static int mapItem755To756(int id755) {
        if (id755 < 0 || id755 >= Maps755.ITEM_755_TO_756_MAP.length) {
            logUnknownComposedOnce("755→756", "アイテム", id755);
            return STONE_ITEM_756;
        }
        return Maps755.ITEM_755_TO_756_MAP[id755];
    }

    /** 756のエンティティ種別ID → 755のエンティティ種別ID。範囲外はPIG_ENTITY_TYPE_755 に相当する代替。 */
    public static int mapEntityType756To755(int id756) {
        if (id756 < 0 || id756 >= Maps755.ENTITY_TYPE_756_TO_755_MAP.length) {
            logUnknownComposedOnce("756→755", "エンティティ種別", id756);
            return PIG_ENTITY_TYPE_755;
        }
        return Maps755.ENTITY_TYPE_756_TO_755_MAP[id756];
    }

    /** 755のエンティティ種別ID → 756のエンティティ種別ID。範囲外はPIG_ENTITY_TYPE_756 に相当する代替。 */
    public static int mapEntityType755To756(int id755) {
        if (id755 < 0 || id755 >= Maps755.ENTITY_TYPE_755_TO_756_MAP.length) {
            logUnknownComposedOnce("755→756", "エンティティ種別", id755);
            return PIG_ENTITY_TYPE_756;
        }
        return Maps755.ENTITY_TYPE_755_TO_756_MAP[id755];
    }

    /** 756の音ID → 755の音ID。範囲外はGENERIC_SOUND_755 に相当する代替。 */
    public static int mapSound756To755(int id756) {
        if (id756 < 0 || id756 >= Maps755.SOUND_756_TO_755_MAP.length) {
            logUnknownComposedOnce("756→755", "音", id756);
            return GENERIC_SOUND_755;
        }
        return Maps755.SOUND_756_TO_755_MAP[id756];
    }

    /** 755の音ID → 756の音ID。範囲外はGENERIC_SOUND_756 に相当する代替。 */
    public static int mapSound755To756(int id755) {
        if (id755 < 0 || id755 >= Maps755.SOUND_755_TO_756_MAP.length) {
            logUnknownComposedOnce("755→756", "音", id755);
            return GENERIC_SOUND_756;
        }
        return Maps755.SOUND_755_TO_756_MAP[id755];
    }

    /** 756のパーティクルID → 755のパーティクルID。範囲外はAMBIENT_PARTICLE_755 に相当する代替。 */
    public static int mapParticle756To755(int id756) {
        if (id756 < 0 || id756 >= Maps755.PARTICLE_756_TO_755_MAP.length) {
            logUnknownComposedOnce("756→755", "パーティクル", id756);
            return AMBIENT_PARTICLE_755;
        }
        return Maps755.PARTICLE_756_TO_755_MAP[id756];
    }

    /** 755のパーティクルID → 756のパーティクルID。範囲外はAMBIENT_PARTICLE_756 に相当する代替。 */
    public static int mapParticle755To756(int id755) {
        if (id755 < 0 || id755 >= Maps755.PARTICLE_755_TO_756_MAP.length) {
            logUnknownComposedOnce("755→756", "パーティクル", id755);
            return AMBIENT_PARTICLE_756;
        }
        return Maps755.PARTICLE_755_TO_756_MAP[id755];
    }

    /** 756のブロックID → 755のブロックID。範囲外はSTONE_BLOCK_755 に相当する代替。 */
    public static int mapBlock756To755(int id756) {
        if (id756 < 0 || id756 >= Maps755.BLOCK_756_TO_755_MAP.length) {
            logUnknownComposedOnce("756→755", "ブロック", id756);
            return STONE_BLOCK_755;
        }
        return Maps755.BLOCK_756_TO_755_MAP[id756];
    }

    /** 755のブロックID → 756のブロックID。範囲外はSTONE_BLOCK_756 に相当する代替。 */
    public static int mapBlock755To756(int id755) {
        if (id755 < 0 || id755 >= Maps755.BLOCK_755_TO_756_MAP.length) {
            logUnknownComposedOnce("755→756", "ブロック", id755);
            return STONE_BLOCK_756;
        }
        return Maps755.BLOCK_755_TO_756_MAP[id755];
    }

    /** 756のメニュー種別ID → 755のメニュー種別ID。範囲外はCRAFTING_MENU_755 に相当する代替。 */
    public static int mapMenu756To755(int id756) {
        if (id756 < 0 || id756 >= Maps755.MENU_756_TO_755_MAP.length) {
            logUnknownComposedOnce("756→755", "メニュー", id756);
            return CRAFTING_MENU_755;
        }
        return Maps755.MENU_756_TO_755_MAP[id756];
    }

    /** 755のメニュー種別ID → 756のメニュー種別ID。範囲外はCRAFTING_MENU_756 に相当する代替。 */
    public static int mapMenu755To756(int id755) {
        if (id755 < 0 || id755 >= Maps755.MENU_755_TO_756_MAP.length) {
            logUnknownComposedOnce("755→756", "メニュー", id755);
            return CRAFTING_MENU_756;
        }
        return Maps755.MENU_755_TO_756_MAP[id755];
    }

    /**
     * 756のブロックエンティティ種別ID → 755のブロックエンティティ種別ID。
     * 756 と 755 は 34件・同一順序のため恒等写像。DROP は無い。
     */
    public static int mapBlockEntityType756To755(int id756) {
        if (id756 < 0 || id756 > MAX_BLOCK_ENTITY_756) {
            logUnknownComposedOnce("756→755", "ブロックエンティティ種別", id756);
            return DROP;
        }
        return id756;
    }

    /**
     * 755のブロックエンティティ種別ID → 756のブロックエンティティ種別ID。
     * 756 と 755 は 34件・同一順序のため恒等写像。DROP は無い。
     */
    public static int mapBlockEntityType755To756(int id755) {
        if (id755 < 0 || id755 > MAX_BLOCK_ENTITY_755) {
            logUnknownComposedOnce("755→756", "ブロックエンティティ種別", id755);
            return DROP;
        }
        return id755;
    }

    /** 756のバイオームID → 755のバイオームID。範囲外はPLAINS_BIOME_755 に相当する代替。 */
    public static int mapBiome756To755(int id756) {
        if (id756 < 0 || id756 >= Maps755.BIOME_756_TO_755_MAP.length) {
            logUnknownComposedOnce("756→755", "バイオーム", id756);
            return PLAINS_BIOME_755;
        }
        return Maps755.BIOME_756_TO_755_MAP[id756];
    }

    /** 755のバイオームID → 756のバイオームID。範囲外はPLAINS_BIOME_756 に相当する代替。 */
    public static int mapBiome755To756(int id755) {
        if (id755 < 0 || id755 >= Maps755.BIOME_755_TO_756_MAP.length) {
            logUnknownComposedOnce("755→756", "バイオーム", id755);
            return PLAINS_BIOME_756;
        }
        return Maps755.BIOME_755_TO_756_MAP[id755];
    }

    /** 756のダメージ種別ID → 755のダメージ種別ID（恒等写像。ただし1.17／1.17.1 の codec に
     * damage_type レジストリは存在しないため、ワイヤ上の呼び出し元は無い）。 */
    public static int mapDamageType756To755(int id756) {
        if (id756 < 0 || id756 > MAX_DAMAGE_TYPE_756) {
            logUnknownOnce(PROTOCOL_755, "ダメージ種別", id756);
            return DROP;
        }
        return Maps755.DAMAGE_TYPE_755_MAP[id756];
    }

    /** 755のダメージ種別ID → 756のダメージ種別ID（恒等写像。ワイヤ上の呼び出し元は無い）。 */
    public static int mapDamageType755To756(int id755) {
        if (id755 < 0 || id755 > MAX_DAMAGE_TYPE_755) {
            logUnknownOnce(PROTOCOL_756, "ダメージ種別", id755);
            return DROP;
        }
        return Maps755.DAMAGE_TYPE_REVERSE_755_MAP[id755];
    }

    /**
     * 756のエフェクトID → 755のエフェクトID。756 と 755 は 32件・同一順序
     * のため恒等写像。範囲外は Speed（1）。
     */
    public static int mapEffect756To755(int id756) {
        if (id756 < 0 || id756 > MAX_EFFECT_756) {
            logUnknownComposedOnce("756→755", "エフェクト", id756);
            return SPEED_EFFECT_755;
        }
        return id756;
    }

    /**
     * 755のエフェクトID → 756のエフェクトID。1〜32 は恒等写像。
     * 範囲外は Speed（1）。
     */
    public static int mapEffect755To756(int id755) {
        if (id755 < 0 || id755 > MAX_EFFECT_755) {
            logUnknownComposedOnce("755→756", "エフェクト", id755);
            return SPEED_EFFECT_756;
        }
        return id755;
    }

    /** 756の属性ID → 755の属性ID。並びは同一。範囲外は {@link #DROP}。 */
    public static int mapAttribute756To755(int id756) {
        if (id756 < 0 || id756 > MAX_ATTRIBUTE_756) {
            logUnknownOnce(PROTOCOL_755, "属性", id756);
            return DROP;
        }
        return Maps755.ATTRIBUTE_755_MAP[id756];
    }

    /** 755の属性ID → 756の属性ID。並びは同一。範囲外は {@link #DROP}。 */
    public static int mapAttribute755To756(int id755) {
        if (id755 < 0 || id755 > MAX_ATTRIBUTE_755) {
            logUnknownOnce(PROTOCOL_756, "属性", id755);
            return DROP;
        }
        return Maps755.ATTRIBUTE_REVERSE_755_MAP[id755];
    }

    /** 755の属性ID → 属性の名前（Update Attributes は 1.17 では名前形式のため）。 */
    public static String attributeName755(int id755) {
        if (id755 < 0 || id755 >= ATTRIBUTE_755_NAMES.length) {
            return null;
        }
        return ATTRIBUTE_755_NAMES[id755];
    }

    /**
     * 属性の名前 → 755の属性ID（765／763／764 は属性が名前形式のため、
     * 755向けに変換するときに使う）。無い名前は {@link #DROP}。
     */
    public static int attributeId755(String name) {
        if (name == null) {
            return DROP;
        }
        String plain = stripMinecraftPrefix(name);
        for (int i = 0; i < ATTRIBUTE_755_NAMES.length; i++) {
            if (ATTRIBUTE_755_NAMES[i].equals(plain)) {
                return i;
            }
        }
        return DROP;
    }

    /** 756のエンチャントID → 755のエンチャントID。並びは同一。範囲外は {@link #DROP}。 */
    public static int mapEnchantment756To755(int id756) {
        if (id756 < 0 || id756 > MAX_ENCHANTMENT_756) {
            logUnknownOnce(PROTOCOL_755, "エンチャント", id756);
            return DROP;
        }
        return Maps755.ENCHANTMENT_755_MAP[id756];
    }

    /** 755のエンチャントID → 756のエンチャントID。並びは同一。範囲外は {@link #DROP}。 */
    public static int mapEnchantment755To756(int id755) {
        if (id755 < 0 || id755 > MAX_ENCHANTMENT_755) {
            logUnknownOnce(PROTOCOL_756, "エンチャント", id755);
            return DROP;
        }
        return Maps755.ENCHANTMENT_REVERSE_755_MAP[id755];
    }

    /** 756の音の名前 → 755の音の名前（Stop Sound 用）。数値の反転表と一致させる。 */
    public static String mapSoundName756To755(String name) {
        return mapSoundNameComposed(name, Maps755.SOUND_NAME_FALLBACK_756_TO_755_MAP);
    }

    /** 755の音の名前 → 756の音の名前（Stop Sound 用）。恒等写像のためそのまま返す。 */
    public static String mapSoundName755To756(String name) {
        return mapSoundNameComposed(name, Maps755.SOUND_NAME_FALLBACK_755_TO_756_MAP);
    }
"""
    insert_before("    // ------------------------------------------------------------------\n"
                  "    // 757 ⇔ 756（1.18／1.18.1 ⇔ 1.17.1）\n",
                  methods_755, "755アクセサ", "mapBlockState756To755")

    # 6. ディスパッチのコメント更新（755の連鎖を追記）
    # 注意：756 のスクリプトは old→new の replace を使っていたが、new が
    # old を部分文字列として含むため再実行のたびに 756 の行が増殖した
    # （RegistryRemapper.java の dispatch コメントに 756 の行が 5 回ある）。
    # 755 ではマーカー付きの insert_after で 1 回だけ追加する。
    dispatch_755_line = ("    // 755は756の表を経由する（755↔763〜775は"
                         "755→756→757→759→760→761→762→763→合成表の連鎖）。\n")
    insert_after("    // 756は757の表を経由する（756↔763〜775は"
                 "756→757→759→760→761→762→763→合成表の連鎖）。\n",
                 dispatch_755_line, "dispatch コメント 755",
                 "755は756の表を経由する")

    # 7. target 分岐への 755 追加（各 dispatch メソッドの 756 分岐の直前。
    # insert_after はメソッド冒頭の直後に入れるため、756 分岐より前に来る。
    # inner は 756 分岐の式そのもの（756→757→759→760→761→762→763→
    # 合成表の連鎖）で、外側に 756→755 の隣接段を 1 つ巻く。
    # 括弧の数：inner は 7 個の開き／7 個の閉じ、巻いた後は 8 個／8 個。
    # 756 のスクリプトを写すときに 1 つ落とした前例があるため数えて書く。）
    chain_down = {
        "mapBlockState": ("mapBlockState756To755",
                          "mapBlockState757To756(mapBlockState759To757(mapBlockState760To759(mapBlockState761To760(mapBlockState762To761(mapBlockState763To762(mapBlockState(id765)))))))"),
        "mapItem": ("mapItem756To755",
                    "mapItem757To756(mapItem759To757(mapItem760To759(mapItem761To760(mapItem762To761(mapItem763To762(mapItem(id765)))))))"),
        "mapEntityType": ("mapEntityType756To755",
                          "mapEntityType757To756(mapEntityType759To757(mapEntityType760To759(mapEntityType761To760(mapEntityType762To761(mapEntityType763To762(mapEntityType(id765)))))))"),
        "mapSound": ("mapSound756To755",
                     "mapSound757To756(mapSound759To757(mapSound760To759(mapSound761To760(mapSound762To761(mapSound763To762(mapSound(id765)))))))"),
        "mapParticle": ("mapParticle756To755",
                        "mapParticle757To756(mapParticle759To757(mapParticle760To759(mapParticle761To760(mapParticle762To761(mapParticle763To762(mapParticle(id765)))))))"),
        "mapBlock": ("mapBlock756To755",
                     "mapBlock757To756(mapBlock759To757(mapBlock760To759(mapBlock761To760(mapBlock762To761(mapBlock763To762(mapBlock(id765)))))))"),
        "mapMenu": ("mapMenu756To755",
                    "mapMenu757To756(mapMenu759To757(mapMenu760To759(mapMenu761To760(mapMenu762To761(mapMenu763To762(mapMenu(id765)))))))"),
        "mapBlockEntityType": ("mapBlockEntityType756To755",
                               "mapBlockEntityType757To756(mapBlockEntityType759To757(mapBlockEntityType760To759(mapBlockEntityType761To760(mapBlockEntityType762To761(mapBlockEntityType763To762(mapBlockEntityType(id765)))))))"),
    }
    for name, (last, inner) in chain_down.items():
        assert inner.count("(") == inner.count(")"), name
        body = (f"        if (clientProtocol == PROTOCOL_755) {{\n"
                f"            return {last}({inner});\n"
                f"        }}\n")
        anchor = f"    public static int {name}(int id765, int clientProtocol) {{\n"
        marker = f"return {last}({inner});"
        insert_after(anchor, body, f"dispatch {name} 755", marker)

    # mapItemReverse（引数名が id）。上向き（クライアント→765）のため、
    # 新しい隣接段（755→756）は最も内側（最初に適用）に入れる。
    # 下向き（mapItem 等）とは逆で、外側に巻くのは誤り。
    # 注意：757／759／760／761／762 の分岐は外側に巻く形で作られており、
    # 隣接表を逆順に合成している（実測：757 分岐は正しい合成と 724/1101 件
    # 食い違う。例：tropical_fish_spawn_egg 757 id 926 の正しい 765 id は
    # 1067 だが 757 分岐は 1066 を返す。§12.5 参照）。756 分岐は正しい順序。
    # 755 分岐だけは正しい順序で合成する。他分岐の修正は統括の判断待ち。
    insert_after("    public static int mapItemReverse(int id, int clientProtocol) {\n",
                 "        if (clientProtocol == PROTOCOL_755) {\n"
                 "            return mapItemReverse(mapItem762To763(mapItem761To762(mapItem760To761(mapItem759To760(mapItem757To759(mapItem756To757(mapItem755To756(id))))))));\n"
                 "        }\n",
                 "dispatch mapItemReverse 755",
                 "mapItemReverse(mapItem762To763(mapItem761To762(mapItem760To761(mapItem759To760(mapItem757To759(mapItem756To757(mapItem755To756(id))))))))")

    # mapEffect（756→755 は恒等写像。756 分岐の外側にそのままつなぐ）
    insert_after("    public static int mapEffect(int id765, int clientProtocol) {\n",
                 "        if (clientProtocol == PROTOCOL_755) {\n"
                 "            return mapEffect756To755(mapEffect757To756(mapEffect759To757(mapEffect(id765))));\n"
                 "        }\n",
                 "dispatch mapEffect 755",
                 "return mapEffect756To755(mapEffect757To756(mapEffect759To757(mapEffect(id765))));")

    # mapSoundName
    insert_after("    public static String mapSoundName(String name, int clientProtocol) {\n",
                 "        if (clientProtocol == PROTOCOL_755) {\n"
                 "            return mapSoundName756To755(mapSoundName757To756(mapSoundName759To757(mapSoundName760To759(mapSoundName761To760(mapSoundName762To761(mapSoundName763To762(mapSoundName(name))))))));\n"
                 "        }\n",
                 "dispatch mapSoundName 755",
                 "mapSoundName756To755(mapSoundName757To756(mapSoundName759To757(mapSoundName760To759(mapSoundName761To760(mapSoundName762To761(mapSoundName763To762(mapSoundName(name)))))))")

    REMAPPER.write_text(src, encoding="utf-8")
    print("RegistryRemapper.java に 755 を差し込んだ。")


if __name__ == "__main__":
    raise SystemExit(main())
