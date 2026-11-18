#!/usr/bin/env python3
"""RegistryRemapper.java に 756（1.17.1）対応を差し込む。

雛形は tools/insert-757-remapper.py（隣接連鎖の配線＋書き込み前の
全入力検証・冪等ガード）。アンカー付き挿入のみ行う。アンカーが
見つからなければ失敗して何も書かない。
使い方: python3 insert-756-remapper.py
書き換えた後は必ず git diff で内容を確認してから javac で確認すること。

配線の方針（STAGE-A-756-NOTES.md §11.8 の申し送り）:
- 756 は合成表を作らない。756↔763〜775 は 756→757→759→760→761→
  762→763→合成表の連鎖で到達する（757〜762 と同じ隣接連鎖グループ）。
- 順向き（757→756）の展開済み表は RegistryTables756 の *_756_* 表を
  expand() で展開する。
- 反転（756→757）の表は invert() で作る。invert() だけでは名前の真値と
  食い違う箇所がある（パーティクル 1 件・バイオーム 26 件。first-wins の
  罠。§11.3 の3を参照）ため、UP_756 補正（756 id → 757 id の対）を渡す。
  パーティクルの補正は tools/maps756-corrections.json から、バイオームの
  補正は RegistryTables756.java の BIOME_756_TO_757_* 表との差分から
  このスクリプトが機械的に作る（gen の corrections JSON は 7 種のみ）。
  アイテムだけは 766／777 の前例にならい、名前から作った既存の逆変換表
  （ITEM_REVERSE_756）を expand() でそのまま使う。
- エフェクト・ブロックエンティティ種別は表を作らずハードコードする
  （mapEffectUp774／mapBlockEntityTypeUp774 の前例）。
  エフェクト：757 と 756 は 32件・同一順序のため恒等写像（DROP なし）。
  ブロックエンティティ：34件・同一順序のため恒等写像（DROP なし）。
- ダメージ種別は 42 件の恒等写像のプレースホルダ（DAMAGE_TYPE_756_MAP／
  DAMAGE_TYPE_REVERSE_756_MAP。757 の DAMAGE_TYPE_757 と同じ扱い。
  1.17.1／1.18 のどちらにも damage_type レジストリは無いため、
  ワイヤ上の呼び出し元は無い）。
- 756 の展開済み表は private static final class Maps756 にまとめる。
  RegistryRemapper の static 初期化子は 64KB 制限に達しているため
  （756 追加前で 64,669 バイト。残り 866 バイト）、756 の表を外側の
  static 初期化子に入れると code too large でコンパイルできない。
  755 以降も同じ方式にすること。
- バイオーム：順向き表（BIOME_756）は 757 側 61 件、反転表
  （BIOME_756_TO_757）は 756 側 176 件（id 0〜175。隙間の id は
  平原（757 id 1）で埋める）。

書き込み前の検証（失敗したら何も書き込まない）:
- maps756-corrections.json の件数（パーティクル 1 件・他 0 件）。
- RegistryTables756.java から読んだ表で、Java の expand()／invert() と
  同じ計算を Python で行い、invert(順向き)＋補正＝名前の真値
  （*_756_TO_757_* 表の展開）と全 7 種で一致することを確認する。
  バイオームは invert() と名前の真値の差分を補正として作り、
  invert(順向き)＋補正＝名前の真値（176 件）を確認する。
  パーティクルの invert() の fallback は wiring と同じ
  AMBIENT_PARTICLE_757（0）を使う（生成スクリプトは entity_effect
  （21）を使っているが、補正を当てた後は -1 が残らないため値は同じ。
  757 の前例と同じ扱い）。
  アイテムは invert()＝ITEM_REVERSE_756 の展開とも一致することを確認する。
- ダメージ種別・エンチャント・属性・バイオームの展開結果の件数と代表値を
  確認する。
"""
from pathlib import Path
import json
import re
import sys

ROOT = Path(__file__).resolve().parent.parent
REMAPPER = ROOT / "src/main/java/io/detergentmc/protocol/RegistryRemapper.java"
TABLES = ROOT / "src/main/java/io/detergentmc/protocol/RegistryTables756.java"
CORRECTIONS_JSON = ROOT / "build/remap-data/maps756-corrections.json"

# 757側の最大ID（RegistryRemapper.java の MAX_*_757 と同じ値）。
MAX_757 = {
    "blockstates": 20341, "items": 1100, "entities": 112, "sounds": 1202,
    "particles": 87, "blocks": 897, "menus": 23, "biomes": 60,
}
# 756側の最大ID（vv-1.17.json 等の実測件数−1。バイオームだけ id の最大値 175）。
MAX_756 = {
    "blockstates": 20341, "items": 1099, "entities": 112, "sounds": 1189,
    "particles": 88, "blocks": 897, "menus": 23, "biomes": 175,
}
# 757側の代替先（invert() の fallback。gen-756-tables.py の UP_756_FALLBACKS
# と同じ。ただしパーティクルだけ wiring では AMBIENT_PARTICLE_757（0）
# を使う。生成スクリプトの entity_effect（21）とは違うが、補正を当てた
# 後は -1 が残らないため結果は同じ。757 の前例と同じ扱い）。
FALLBACK_757 = {
    "blockstates": 1, "items": 1, "entities": 64, "sounds": 1018,
    "particles": 0, "blocks": 1, "menus": 11, "biomes": 1,
}
# 756側の代替先（expand() の fallback）。
FALLBACK_756 = {
    "blockstates": 1, "items": 1, "entities": 64, "sounds": 1005,
    "particles": 0, "blocks": 1, "menus": 11, "biomes": 1,
}
# 表名（RegistryTables756.java）。down は 757→756、up は 756→757。
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

    # --- 検証1：補正 JSON の件数（パーティクル 1 件・他 0 件）---
    expected_counts = {"blockstates": 0, "items": 0, "entities": 0, "sounds": 0,
                       "particles": 1, "blocks": 0, "menus": 0}
    for kind, expected in expected_counts.items():
        got = corrections.get(kind)
        if got is None:
            raise SystemExit(f"補正 JSON に {kind} が無い")
        if len(got) != expected:
            raise SystemExit(f"補正 {kind} の件数が違う：{len(got)} != {expected}")
        for to_id, from_id in got:
            if not (0 <= to_id <= MAX_756[kind] and 0 <= from_id <= MAX_757[kind]):
                raise SystemExit(f"補正 {kind} の範囲外の対：[{to_id}, {from_id}]")
    print("補正 JSON の件数を確認：パーティクル 1 件・他 0 件")

    # --- 検証2：invert(順向き)＋補正＝名前の真値（*_756_TO_757_* 表の展開）---
    for kind in KINDS:
        prefix = TABLE_PREFIX[kind]
        down_ranges = parse_int_table2(tables_text, prefix + "_756_RANGES")
        down_overrides = parse_int_table2(tables_text, prefix + "_756_OVERRIDES")
        truth_ranges = parse_int_table2(tables_text, prefix + "_756_TO_757_RANGES")
        truth_overrides = parse_int_table2(tables_text, prefix + "_756_TO_757_OVERRIDES")
        truth = expand(truth_ranges, truth_overrides, MAX_756[kind] + 1, FALLBACK_757[kind])
        corr = corrections[kind] if corrections[kind] else None
        got = invert(down_ranges, down_overrides,
                     MAX_757[kind] + 1, MAX_756[kind] + 1, FALLBACK_757[kind], corr)
        if got != truth:
            diffs = [i for i, (a, b) in enumerate(zip(got, truth)) if a != b]
            raise SystemExit(f"invert＋補正が名前の真値と違う（{kind}、{len(diffs)} 件）")
        print(f"invert＋補正＝名前の真値：{kind}（{len(truth)} 件）")
    # アイテムは invert()＝ITEM_REVERSE_756 の展開とも一致することを確認する。
    rev_ranges = parse_int_table2(tables_text, "ITEM_REVERSE_756_RANGES")
    rev_overrides = parse_int_table2(tables_text, "ITEM_REVERSE_756_OVERRIDES")
    rev = expand(rev_ranges, rev_overrides, MAX_756["items"] + 1, FALLBACK_757["items"])
    item_ranges = parse_int_table2(tables_text, "ITEM_756_RANGES")
    item_overrides = parse_int_table2(tables_text, "ITEM_756_OVERRIDES")
    item_inv = invert(item_ranges, item_overrides,
                      MAX_757["items"] + 1, MAX_756["items"] + 1, FALLBACK_757["items"], None)
    if item_inv != rev:
        raise SystemExit("invert(アイテム) が ITEM_REVERSE_756 と違う")
    print("invert(アイテム)＝ITEM_REVERSE_756 の展開（1100 件）")

    # --- 検証3：バイオームは invert(補正あり)＝名前の真値 ---
    # invert() だけでは 26 件食い違う（first-wins の罠。§11.3 の3を参照）。
    # 25 件は 1.17 で削除されたバイオーム（反転表は REVERSE_BIOME_FALLBACK_756
    # の代替先を指すが、invert() は preimage が無いため平原に落ちる）。
    # 残り 1 件は snowy_tundra（756 id 12）：順向き表では snowy_slopes
    # （757 id 30）の代替先が区間表に、snowy_plains（757 id 3）の改名が
    # 上書き表にある。invert() は区間表を先に処理するため 30 を選ぶが、
    # 名前の真値は改名元の 3。757 のブロック状態 110 件・音 20 件と同じ
    # 補正の仕組み（BIOME_UP_756_CORRECTIONS）で上書きする。
    # 補正はここで差分から機械的に作る（手入力しない）。
    bio_ranges = parse_int_table2(tables_text, "BIOME_756_RANGES")
    bio_overrides = parse_int_table2(tables_text, "BIOME_756_OVERRIDES")
    bio_truth = expand(parse_int_table2(tables_text, "BIOME_756_TO_757_RANGES"),
                       parse_int_table2(tables_text, "BIOME_756_TO_757_OVERRIDES"),
                       MAX_756["biomes"] + 1, FALLBACK_757["biomes"])
    bio_inv = invert(bio_ranges, bio_overrides,
                     MAX_757["biomes"] + 1, MAX_756["biomes"] + 1, FALLBACK_757["biomes"], None)
    bio_diffs = [(i, bio_truth[i]) for i in range(len(bio_truth)) if bio_inv[i] != bio_truth[i]]
    if len(bio_truth) != 176:
        raise SystemExit(f"バイオームの反転表が 176 件でない：{len(bio_truth)}")
    bio_inv_fixed = invert(bio_ranges, bio_overrides,
                           MAX_757["biomes"] + 1, MAX_756["biomes"] + 1,
                           FALLBACK_757["biomes"], bio_diffs)
    if bio_inv_fixed != bio_truth:
        raise SystemExit("invert(バイオーム)＋補正が名前の真値と違う")
    # 代表値：757 the_void(0)→756 the_void(127)、snowy_plains(3)→
    # snowy_tundra(12)、meadow(28)→plains(1)、dripstone_caves(49)→
    # dripstone_caves(174)。反転：756 the_void(127)→757 the_void(0)、
    # snowy_tundra(12)→snowy_plains(3)、mountains(3)→windswept_hills(18)、
    # 隙間の id 51→plains(1)、lush_caves(175)→lush_caves(50)。
    bio_down = expand(bio_ranges, bio_overrides, MAX_757["biomes"] + 1, FALLBACK_756["biomes"])
    checks = [(bio_down[0], 127), (bio_down[3], 12), (bio_down[28], 1),
              (bio_down[49], 174), (bio_truth[127], 0), (bio_truth[12], 3),
              (bio_truth[3], 18), (bio_truth[51], 1), (bio_truth[175], 50)]
    if any(got != want for got, want in checks):
        raise SystemExit(f"バイオームの真値がおかしい：{checks}")
    print(f"invert(バイオーム)＋補正＝名前の真値（176 件。補正 {len(bio_diffs)} 件・代表値 9 件を確認）")

    # --- 検証4：ダメージ種別・エンチャント・属性の展開 ---
    dmg = expand(parse_int_table2(tables_text, "DAMAGE_TYPE_756_RANGES"),
                 parse_int_table2(tables_text, "DAMAGE_TYPE_756_OVERRIDES"), 42, 16)
    dmg_rev = expand(parse_int_table2(tables_text, "DAMAGE_TYPE_756_TO_757_RANGES"),
                     parse_int_table2(tables_text, "DAMAGE_TYPE_756_TO_757_OVERRIDES"), 42, 16)
    if dmg != list(range(42)) or dmg_rev != list(range(42)):
        raise SystemExit("ダメージ種別が 42 件の恒等写像ではない")
    enc = expand(parse_int_table2(tables_text, "ENCHANTMENT_756_RANGES"),
                 parse_int_table2(tables_text, "ENCHANTMENT_756_OVERRIDES"), 38, -1)
    enc_rev = expand(parse_int_table2(tables_text, "ENCHANTMENT_REVERSE_756_RANGES"),
                     parse_int_table2(tables_text, "ENCHANTMENT_REVERSE_756_OVERRIDES"), 38, -1)
    if len(enc) != 38 or enc != list(range(38)):
        raise SystemExit(f"エンチャントの展開がおかしい：{enc[:10]}")
    if len(enc_rev) != 38 or enc_rev != list(range(38)):
        raise SystemExit(f"エンチャント逆向きの展開がおかしい：{enc_rev[:10]}")
    attr = expand(parse_int_table2(tables_text, "ATTRIBUTE_756_RANGES"),
                  parse_int_table2(tables_text, "ATTRIBUTE_756_OVERRIDES"), 13, -1)
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
    insert_before("import static io.detergentmc.protocol.RegistryTables757.*;",
                  "import static io.detergentmc.protocol.RegistryTables756.*;\n",
                  "import RegistryTables756", "RegistryTables756.*")

    # 2. PROTOCOL_756（PROTOCOL_757 の直前）
    insert_before("    /** 757（1.18／1.18.1）のプロトコル番号。 */",
                  "    /** 756（1.17.1）のプロトコル番号。 */\n"
                  "    public static final int PROTOCOL_756 = 756;\n",
                  "PROTOCOL_756", "PROTOCOL_756 = 756")

    # 3. 756定数（GENERIC_DAMAGE_TYPE_757 の直後）
    constants_756 = """
    /** 756側の最大の番号（ViaVersion mapping-1.17.json の件数−1。バイオームだけ id の最大値）。 */
    public static final int MAX_BLOCK_STATE_756 = 20341;
    public static final int MAX_ITEM_756 = 1099;
    public static final int MAX_ENTITY_TYPE_756 = 112;
    public static final int MAX_SOUND_756 = 1189;
    public static final int MAX_PARTICLE_756 = 88;
    public static final int MAX_BLOCK_756 = 897;
    public static final int MAX_MENU_756 = 23;
    /** 756側のエンチャントは 0〜37（38件。757と同一順序）。 */
    public static final int MAX_ENCHANTMENT_756 = 37;
    /** 756側のバイオームは 0〜175（81件。id は 0〜175 の非連番）。 */
    public static final int MAX_BIOME_756 = 175;
    /** 756側のダメージ種別は 0〜41（42件。762 の順序の恒等写像。実バニラで検証する）。 */
    public static final int MAX_DAMAGE_TYPE_756 = 41;
    /** 756側のブロックエンティティ種別は 0〜33（34件。757と同一順序）。 */
    public static final int MAX_BLOCK_ENTITY_756 = 33;
    /** 756側の属性は 0〜12（13件。757と同一順序）。 */
    public static final int MAX_ATTRIBUTE_756 = 12;
    /** 756側のエフェクトは 1〜32（32件。757と同一順序。1ベース）。 */
    public static final int MAX_EFFECT_756 = 32;

    /** 756側の代替先（mapping-1.17.json の番号。Speed だけ 1ベースで 1）。 */
    public static final int STONE_BLOCK_STATE_756 = 1;
    public static final int AIR_BLOCK_STATE_756 = 0;
    public static final int STONE_ITEM_756 = 1;
    public static final int PIG_ENTITY_TYPE_756 = 64;
    public static final int GENERIC_SOUND_756 = 1005; // block.stone.break
    public static final int AMBIENT_PARTICLE_756 = 0; // ambient_entity_effect
    public static final int STONE_BLOCK_756 = 1;
    public static final int CRAFTING_MENU_756 = 11;
    public static final int SPEED_EFFECT_756 = 1;
    /** 756側の平原バイオーム（minecraft-data pc/1.17/biomes.json）。 */
    public static final int PLAINS_BIOME_756 = 1;
    /** 756側の generic ダメージ種別（762 と同一順序と仮定）。 */
    public static final int GENERIC_DAMAGE_TYPE_756 = 16;
"""
    insert_after("    public static final int GENERIC_DAMAGE_TYPE_757 = 16;\n",
                 constants_756, "756定数", "MAX_BLOCK_STATE_756")

    # 4. 756の展開済み表（759 ⇔ 757 の展開済み表の直前）
    maps_756 = """
    // ------------------------------------------------------------------
    // 757 ⇔ 756（1.18／1.18.1 ⇔ 1.17.1）の展開済み表。RegistryTables756
    // の区間＋上書きの表をクラスロード時に int[] へ展開する。
    // 順向き（757→756）は expand()、反転（756→757）は invert() で作る。
    // invert() だけでは名前の真値と食い違う箇所がある（パーティクル
    // 1 件・バイオーム 26 件。first-wins の罠）ため、下の UP_756 補正を渡す。
    // アイテムだけは 766／777 の前例にならい、名前から作った既存の
    // 逆変換表（ITEM_REVERSE_756）を expand() でそのまま使う。
    // エフェクト・ブロックエンティティ種別は表を作らない（アクセサで
    // ハードコードする。757 と 756 はどちらも 32件・34件で同一順序のため
    // 恒等写像で、DROP は無い）。756 は合成表を作らない。
    // バイオームの反転表は 756 側 176 件（id 0〜175。隙間の id は平原）。
    // 756 の表は private static final class Maps756 にまとめる（RegistryRemapper の
    // static 初期化子は 64KB 制限に達しているため。756 追加前で 64,669 バイト）。
    // ------------------------------------------------------------------


    /** 757 ⇔ 756 の展開済み表をまとめたホルダ。RegistryRemapper の static 初期化子は
     * 64KB 制限に達しているため（756 追加前で 64,669 バイト。残り 866 バイト）、756 の表の
     * 初期化はこの別クラスの static 初期化子に分けてある。755 以降も同じ方式にすること。
     * 初回アクセス時に初期化される（クラスロード時の初期化と等価。expand()／invert() は
     * 純粋関数）。アクセサからは Maps756.〜 で参照する。 */
    private static final class Maps756 {
        /** パーティクルの反転の補正（756 id → 757 id）。1 件。invert() の
         * first-wins では、756 の light（3）に preimage が無く fallback
         * （entity_effect）に落ちてしまう。ViaVersion の差分ファイルでは
         * light→block_marker のため、名前の真値に上書きする。
         * tools/maps756-corrections.json から生成。検証は
         * tools/verify-756-tables.py のチェックDを参照。 */
        private static final int[][] PARTICLE_UP_756_CORRECTIONS = {
""" + particle_corr_java + """
        };

        /** バイオームの反転の補正（756 id → 757 id）。26 件。invert() の
         * first-wins では、1.17 で削除された 25 件のバイオームに preimage が
         * 無く平原に落ちてしまう（名前の真値は REVERSE_BIOME_FALLBACK_756
         * の代替先）。残り 1 件は snowy_tundra（12）：順向き表では
         * snowy_slopes（757 id 30）の代替先が区間表に、snowy_plains
         * （757 id 3）の改名が上書き表にあり、invert() は区間表を先に処理
         * するため 30 を選ぶ。名前の真値は改名元の 3。
         * RegistryTables756.java の BIOME_756_TO_757_* 表との差分から生成。
         * 検証は tools/verify-756-tables.py のチェックGを参照。 */
        private static final int[][] BIOME_UP_756_CORRECTIONS = {
""" + biome_corr_java + """
        };

        /** 757のブロック状態ID → 756のブロック状態ID（RegistryTables756。恒等写像）。 */
        private static final int[] BLOCK_STATE_757_TO_756_MAP =
                expand(BLOCK_STATE_756_RANGES, BLOCK_STATE_756_OVERRIDES, MAX_BLOCK_STATE_757 + 1,
                        STONE_BLOCK_STATE_756);
        /** 756のブロック状態ID → 757のブロック状態ID（反転。恒等写像）。 */
        private static final int[] BLOCK_STATE_756_TO_757_MAP = invert(
                BLOCK_STATE_756_RANGES, BLOCK_STATE_756_OVERRIDES,
                MAX_BLOCK_STATE_757 + 1, MAX_BLOCK_STATE_756 + 1,
                STONE_BLOCK_STATE_757, null);
        /** 757のアイテムID → 756のアイテムID。 */
        private static final int[] ITEM_757_TO_756_MAP =
                expand(ITEM_756_RANGES, ITEM_756_OVERRIDES, MAX_ITEM_757 + 1, STONE_ITEM_756);
        /** 756のアイテムID → 757のアイテムID（名前から作った逆変換表）。 */
        private static final int[] ITEM_756_TO_757_MAP =
                expand(ITEM_REVERSE_756_RANGES, ITEM_REVERSE_756_OVERRIDES, MAX_ITEM_756 + 1,
                        STONE_ITEM_757);
        /** 757のエンティティ種別ID → 756のエンティティ種別ID（恒等写像）。 */
        private static final int[] ENTITY_TYPE_757_TO_756_MAP =
                expand(ENTITY_TYPE_756_RANGES, ENTITY_TYPE_756_OVERRIDES, MAX_ENTITY_TYPE_757 + 1,
                        PIG_ENTITY_TYPE_756);
        /** 756のエンティティ種別ID → 757のエンティティ種別ID（反転。恒等写像）。 */
        private static final int[] ENTITY_TYPE_756_TO_757_MAP = invert(
                ENTITY_TYPE_756_RANGES, ENTITY_TYPE_756_OVERRIDES,
                MAX_ENTITY_TYPE_757 + 1, MAX_ENTITY_TYPE_756 + 1, PIG_ENTITY_TYPE_757, null);
        /** 757の音ID → 756の音ID。 */
        private static final int[] SOUND_757_TO_756_MAP =
                expand(SOUND_756_RANGES, SOUND_756_OVERRIDES, MAX_SOUND_757 + 1, GENERIC_SOUND_756);
        /** 756の音ID → 757の音ID（反転）。 */
        private static final int[] SOUND_756_TO_757_MAP = invert(
                SOUND_756_RANGES, SOUND_756_OVERRIDES,
                MAX_SOUND_757 + 1, MAX_SOUND_756 + 1,
                GENERIC_SOUND_757, null);
        /** 757のパーティクルID → 756のパーティクルID。 */
        private static final int[] PARTICLE_757_TO_756_MAP =
                expand(PARTICLE_756_RANGES, PARTICLE_756_OVERRIDES, MAX_PARTICLE_757 + 1,
                        AMBIENT_PARTICLE_756);
        /** 756のパーティクルID → 757のパーティクルID（反転＋補正）。 */
        private static final int[] PARTICLE_756_TO_757_MAP = invert(
                PARTICLE_756_RANGES, PARTICLE_756_OVERRIDES,
                MAX_PARTICLE_757 + 1, MAX_PARTICLE_756 + 1,
                AMBIENT_PARTICLE_757, PARTICLE_UP_756_CORRECTIONS);
        /** 757のブロックID → 756のブロックID（恒等写像）。 */
        private static final int[] BLOCK_757_TO_756_MAP =
                expand(BLOCK_756_RANGES, BLOCK_756_OVERRIDES, MAX_BLOCK_757 + 1, STONE_BLOCK_756);
        /** 756のブロックID → 757のブロックID（反転。恒等写像）。 */
        private static final int[] BLOCK_756_TO_757_MAP = invert(
                BLOCK_756_RANGES, BLOCK_756_OVERRIDES,
                MAX_BLOCK_757 + 1, MAX_BLOCK_756 + 1, STONE_BLOCK_757, null);
        /** 757のメニュー種別ID → 756のメニュー種別ID（恒等写像）。 */
        private static final int[] MENU_757_TO_756_MAP =
                expand(MENU_756_RANGES, MENU_756_OVERRIDES, MAX_MENU_757 + 1, CRAFTING_MENU_756);
        /** 756のメニュー種別ID → 757のメニュー種別ID（反転。恒等写像）。 */
        private static final int[] MENU_756_TO_757_MAP = invert(
                MENU_756_RANGES, MENU_756_OVERRIDES,
                MAX_MENU_757 + 1, MAX_MENU_756 + 1, CRAFTING_MENU_757, null);
        /** 757のバイオームID → 756のバイオームID。改名 11件・新規 6件は代替先へ。 */
        private static final int[] BIOME_757_TO_756_MAP =
                expand(BIOME_756_RANGES, BIOME_756_OVERRIDES, MAX_BIOME_757 + 1, PLAINS_BIOME_756);
        /** 756のバイオームID → 757のバイオームID（反転＋補正。176 件）。 */
        private static final int[] BIOME_756_TO_757_MAP = invert(
                BIOME_756_RANGES, BIOME_756_OVERRIDES,
                MAX_BIOME_757 + 1, MAX_BIOME_756 + 1, PLAINS_BIOME_757,
                BIOME_UP_756_CORRECTIONS);
        /** ダメージ種別ID：757 → 756（762 の 42 件の恒等写像。1.17.1／1.18 の
         * どちらにも damage_type レジストリは無いため、ワイヤ上の呼び出し元は無い）。 */
        private static final int[] DAMAGE_TYPE_756_MAP = {
            0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19,
            20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32, 33, 34, 35, 36, 37,
            38, 39, 40, 41
        };
        /** ダメージ種別ID：756 → 757（762 の 42 件の恒等写像。呼び出し元は無い）。 */
        private static final int[] DAMAGE_TYPE_REVERSE_756_MAP = {
            0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19,
            20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32, 33, 34, 35, 36, 37,
            38, 39, 40, 41
        };
        /** 757の属性ID → 756の属性ID。並びは同一。範囲外は {@link #DROP}。 */
        private static final int[] ATTRIBUTE_756_MAP =
                expand(ATTRIBUTE_756_RANGES, ATTRIBUTE_756_OVERRIDES, MAX_ATTRIBUTE_757 + 1, DROP);
        /** 756の属性ID → 757の属性ID。並びは同一。範囲外は {@link #DROP}。 */
        private static final int[] ATTRIBUTE_REVERSE_756_MAP =
                expand(ATTRIBUTE_REVERSE_756_RANGES, ATTRIBUTE_REVERSE_756_OVERRIDES,
                        MAX_ATTRIBUTE_756 + 1, DROP);
        /** 757のエンチャントID → 756のエンチャントID。並びは同一。範囲外は {@link #DROP}。 */
        private static final int[] ENCHANTMENT_756_MAP =
                expand(ENCHANTMENT_756_RANGES, ENCHANTMENT_756_OVERRIDES,
                        MAX_ENCHANTMENT_757 + 1, DROP);
        /** 756のエンチャントID → 757のエンチャントID。並びは同一。範囲外は {@link #DROP}。 */
        private static final int[] ENCHANTMENT_REVERSE_756_MAP =
                expand(ENCHANTMENT_REVERSE_756_RANGES, ENCHANTMENT_REVERSE_756_OVERRIDES,
                        MAX_ENCHANTMENT_756 + 1, DROP);

        /** 757の音の名前 → 756の音の名前（Stop Sound 用）。数値の反転表と一致させる。 */
        private static final Map<String, String> SOUND_NAME_FALLBACK_757_TO_756_MAP =
                buildSoundNameMap(SOUND_NAME_FALLBACKS_757_TO_756);

        /** 756の音の名前 → 757の音の名前（Stop Sound 用）。恒等写像のため空。 */
        private static final Map<String, String> SOUND_NAME_FALLBACK_756_TO_757_MAP =
                buildSoundNameMap(SOUND_NAME_FALLBACKS_756_TO_757);
    }
"""
    insert_before("    // ------------------------------------------------------------------\n"
                  "    // 759 ⇔ 757（1.19 ⇔ 1.18／1.18.1）の展開済み表。RegistryTables757\n",
                  maps_756, "756の展開済み表", "BLOCK_STATE_757_TO_756_MAP")

    # 5. アクセサメソッド（759 ⇔ 757 節の直前）
    methods_756 = """
    // ------------------------------------------------------------------
    // 757 ⇔ 756（1.18／1.18.1 ⇔ 1.17.1）
    // パケットIDは simulation_distance 1 件が変わる（PacketIds の 756 列を参照。
    // play toClient の差分は simulation_distance のみ。packet-ids-756.py の実測）。
    // レジストリは RegistryTables756（ViaVersion mapping-1.17／1.18.json
    // から名前で突き合わせたもの）を使う。756↔763〜775 は隣接表の連鎖
    // （756→757→759→760→761→762→763→合成表）で到達する。合成表は作らない。
    // Join Game は simulationDistance の有無だけが違う（Stage B で
    // PlayTranslator が対応する）。Respawn は同一。
    // ------------------------------------------------------------------

    /** 757のブロック状態ID → 756のブロック状態ID。範囲外はSTONE_BLOCK_STATE_756 に相当する代替。 */
    public static int mapBlockState757To756(int id757) {
        if (id757 < 0 || id757 >= Maps756.BLOCK_STATE_757_TO_756_MAP.length) {
            logUnknownComposedOnce("757→756", "ブロック状態", id757);
            return STONE_BLOCK_STATE_756;
        }
        return Maps756.BLOCK_STATE_757_TO_756_MAP[id757];
    }

    /** 756のブロック状態ID → 757のブロック状態ID。範囲外はSTONE_BLOCK_STATE_757 に相当する代替。 */
    public static int mapBlockState756To757(int id756) {
        if (id756 < 0 || id756 >= Maps756.BLOCK_STATE_756_TO_757_MAP.length) {
            logUnknownComposedOnce("756→757", "ブロック状態", id756);
            return STONE_BLOCK_STATE_757;
        }
        return Maps756.BLOCK_STATE_756_TO_757_MAP[id756];
    }

    /** 757のアイテムID → 756のアイテムID。範囲外はSTONE_ITEM_756 に相当する代替。 */
    public static int mapItem757To756(int id757) {
        if (id757 < 0 || id757 >= Maps756.ITEM_757_TO_756_MAP.length) {
            logUnknownComposedOnce("757→756", "アイテム", id757);
            return STONE_ITEM_756;
        }
        return Maps756.ITEM_757_TO_756_MAP[id757];
    }

    /** 756のアイテムID → 757のアイテムID。範囲外はSTONE_ITEM_757 に相当する代替。 */
    public static int mapItem756To757(int id756) {
        if (id756 < 0 || id756 >= Maps756.ITEM_756_TO_757_MAP.length) {
            logUnknownComposedOnce("756→757", "アイテム", id756);
            return STONE_ITEM_757;
        }
        return Maps756.ITEM_756_TO_757_MAP[id756];
    }

    /** 757のエンティティ種別ID → 756のエンティティ種別ID。範囲外はPIG_ENTITY_TYPE_756 に相当する代替。 */
    public static int mapEntityType757To756(int id757) {
        if (id757 < 0 || id757 >= Maps756.ENTITY_TYPE_757_TO_756_MAP.length) {
            logUnknownComposedOnce("757→756", "エンティティ種別", id757);
            return PIG_ENTITY_TYPE_756;
        }
        return Maps756.ENTITY_TYPE_757_TO_756_MAP[id757];
    }

    /** 756のエンティティ種別ID → 757のエンティティ種別ID。範囲外はPIG_ENTITY_TYPE_757 に相当する代替。 */
    public static int mapEntityType756To757(int id756) {
        if (id756 < 0 || id756 >= Maps756.ENTITY_TYPE_756_TO_757_MAP.length) {
            logUnknownComposedOnce("756→757", "エンティティ種別", id756);
            return PIG_ENTITY_TYPE_757;
        }
        return Maps756.ENTITY_TYPE_756_TO_757_MAP[id756];
    }

    /** 757の音ID → 756の音ID。範囲外はGENERIC_SOUND_756 に相当する代替。 */
    public static int mapSound757To756(int id757) {
        if (id757 < 0 || id757 >= Maps756.SOUND_757_TO_756_MAP.length) {
            logUnknownComposedOnce("757→756", "音", id757);
            return GENERIC_SOUND_756;
        }
        return Maps756.SOUND_757_TO_756_MAP[id757];
    }

    /** 756の音ID → 757の音ID。範囲外はGENERIC_SOUND_757 に相当する代替。 */
    public static int mapSound756To757(int id756) {
        if (id756 < 0 || id756 >= Maps756.SOUND_756_TO_757_MAP.length) {
            logUnknownComposedOnce("756→757", "音", id756);
            return GENERIC_SOUND_757;
        }
        return Maps756.SOUND_756_TO_757_MAP[id756];
    }

    /** 757のパーティクルID → 756のパーティクルID。範囲外はAMBIENT_PARTICLE_756 に相当する代替。 */
    public static int mapParticle757To756(int id757) {
        if (id757 < 0 || id757 >= Maps756.PARTICLE_757_TO_756_MAP.length) {
            logUnknownComposedOnce("757→756", "パーティクル", id757);
            return AMBIENT_PARTICLE_756;
        }
        return Maps756.PARTICLE_757_TO_756_MAP[id757];
    }

    /** 756のパーティクルID → 757のパーティクルID。範囲外はAMBIENT_PARTICLE_757 に相当する代替。 */
    public static int mapParticle756To757(int id756) {
        if (id756 < 0 || id756 >= Maps756.PARTICLE_756_TO_757_MAP.length) {
            logUnknownComposedOnce("756→757", "パーティクル", id756);
            return AMBIENT_PARTICLE_757;
        }
        return Maps756.PARTICLE_756_TO_757_MAP[id756];
    }

    /** 757のブロックID → 756のブロックID。範囲外はSTONE_BLOCK_756 に相当する代替。 */
    public static int mapBlock757To756(int id757) {
        if (id757 < 0 || id757 >= Maps756.BLOCK_757_TO_756_MAP.length) {
            logUnknownComposedOnce("757→756", "ブロック", id757);
            return STONE_BLOCK_756;
        }
        return Maps756.BLOCK_757_TO_756_MAP[id757];
    }

    /** 756のブロックID → 757のブロックID。範囲外はSTONE_BLOCK_757 に相当する代替。 */
    public static int mapBlock756To757(int id756) {
        if (id756 < 0 || id756 >= Maps756.BLOCK_756_TO_757_MAP.length) {
            logUnknownComposedOnce("756→757", "ブロック", id756);
            return STONE_BLOCK_757;
        }
        return Maps756.BLOCK_756_TO_757_MAP[id756];
    }

    /** 757のメニュー種別ID → 756のメニュー種別ID。範囲外はCRAFTING_MENU_756 に相当する代替。 */
    public static int mapMenu757To756(int id757) {
        if (id757 < 0 || id757 >= Maps756.MENU_757_TO_756_MAP.length) {
            logUnknownComposedOnce("757→756", "メニュー", id757);
            return CRAFTING_MENU_756;
        }
        return Maps756.MENU_757_TO_756_MAP[id757];
    }

    /** 756のメニュー種別ID → 757のメニュー種別ID。範囲外はCRAFTING_MENU_757 に相当する代替。 */
    public static int mapMenu756To757(int id756) {
        if (id756 < 0 || id756 >= Maps756.MENU_756_TO_757_MAP.length) {
            logUnknownComposedOnce("756→757", "メニュー", id756);
            return CRAFTING_MENU_757;
        }
        return Maps756.MENU_756_TO_757_MAP[id756];
    }

    /**
     * 757のブロックエンティティ種別ID → 756のブロックエンティティ種別ID。
     * 757 と 756 は 34件・同一順序のため恒等写像。DROP は無い。
     */
    public static int mapBlockEntityType757To756(int id757) {
        if (id757 < 0 || id757 > MAX_BLOCK_ENTITY_757) {
            logUnknownComposedOnce("757→756", "ブロックエンティティ種別", id757);
            return DROP;
        }
        return id757;
    }

    /**
     * 756のブロックエンティティ種別ID → 757のブロックエンティティ種別ID。
     * 757 と 756 は 34件・同一順序のため恒等写像。DROP は無い。
     */
    public static int mapBlockEntityType756To757(int id756) {
        if (id756 < 0 || id756 > MAX_BLOCK_ENTITY_756) {
            logUnknownComposedOnce("756→757", "ブロックエンティティ種別", id756);
            return DROP;
        }
        return id756;
    }

    /** 757のバイオームID → 756のバイオームID。範囲外はPLAINS_BIOME_756 に相当する代替。 */
    public static int mapBiome757To756(int id757) {
        if (id757 < 0 || id757 >= Maps756.BIOME_757_TO_756_MAP.length) {
            logUnknownComposedOnce("757→756", "バイオーム", id757);
            return PLAINS_BIOME_756;
        }
        return Maps756.BIOME_757_TO_756_MAP[id757];
    }

    /** 756のバイオームID → 757のバイオームID。範囲外はPLAINS_BIOME_757 に相当する代替。 */
    public static int mapBiome756To757(int id756) {
        if (id756 < 0 || id756 >= Maps756.BIOME_756_TO_757_MAP.length) {
            logUnknownComposedOnce("756→757", "バイオーム", id756);
            return PLAINS_BIOME_757;
        }
        return Maps756.BIOME_756_TO_757_MAP[id756];
    }

    /** 757のダメージ種別ID → 756のダメージ種別ID（恒等写像。ただし1.17.1／1.18 の codec に
     * damage_type レジストリは存在しないため、ワイヤ上の呼び出し元は無い）。 */
    public static int mapDamageType757To756(int id757) {
        if (id757 < 0 || id757 > MAX_DAMAGE_TYPE_757) {
            logUnknownOnce(PROTOCOL_756, "ダメージ種別", id757);
            return DROP;
        }
        return Maps756.DAMAGE_TYPE_756_MAP[id757];
    }

    /** 756のダメージ種別ID → 757のダメージ種別ID（恒等写像。ワイヤ上の呼び出し元は無い）。 */
    public static int mapDamageType756To757(int id756) {
        if (id756 < 0 || id756 > MAX_DAMAGE_TYPE_756) {
            logUnknownOnce(PROTOCOL_757, "ダメージ種別", id756);
            return DROP;
        }
        return Maps756.DAMAGE_TYPE_REVERSE_756_MAP[id756];
    }

    /**
     * 757のエフェクトID → 756のエフェクトID。757 と 756 は 32件・同一順序
     * のため恒等写像。範囲外は Speed（1）。
     */
    public static int mapEffect757To756(int id757) {
        if (id757 < 0 || id757 > MAX_EFFECT_757) {
            logUnknownComposedOnce("757→756", "エフェクト", id757);
            return SPEED_EFFECT_756;
        }
        return id757;
    }

    /**
     * 756のエフェクトID → 757のエフェクトID。1〜32 は恒等写像。
     * 範囲外は Speed（1）。
     */
    public static int mapEffect756To757(int id756) {
        if (id756 < 0 || id756 > MAX_EFFECT_756) {
            logUnknownComposedOnce("756→757", "エフェクト", id756);
            return SPEED_EFFECT_757;
        }
        return id756;
    }

    /** 757の属性ID → 756の属性ID。並びは同一。範囲外は {@link #DROP}。 */
    public static int mapAttribute757To756(int id757) {
        if (id757 < 0 || id757 > MAX_ATTRIBUTE_757) {
            logUnknownOnce(PROTOCOL_756, "属性", id757);
            return DROP;
        }
        return Maps756.ATTRIBUTE_756_MAP[id757];
    }

    /** 756の属性ID → 757の属性ID。並びは同一。範囲外は {@link #DROP}。 */
    public static int mapAttribute756To757(int id756) {
        if (id756 < 0 || id756 > MAX_ATTRIBUTE_756) {
            logUnknownOnce(PROTOCOL_757, "属性", id756);
            return DROP;
        }
        return Maps756.ATTRIBUTE_REVERSE_756_MAP[id756];
    }

    /** 756の属性ID → 属性の名前（Update Attributes は 1.17.1 では名前形式のため）。 */
    public static String attributeName756(int id756) {
        if (id756 < 0 || id756 >= ATTRIBUTE_756_NAMES.length) {
            return null;
        }
        return ATTRIBUTE_756_NAMES[id756];
    }

    /**
     * 属性の名前 → 756の属性ID（765／763／764 は属性が名前形式のため、
     * 756向けに変換するときに使う）。無い名前は {@link #DROP}。
     */
    public static int attributeId756(String name) {
        if (name == null) {
            return DROP;
        }
        String plain = stripMinecraftPrefix(name);
        for (int i = 0; i < ATTRIBUTE_756_NAMES.length; i++) {
            if (ATTRIBUTE_756_NAMES[i].equals(plain)) {
                return i;
            }
        }
        return DROP;
    }

    /** 757のエンチャントID → 756のエンチャントID。並びは同一。範囲外は {@link #DROP}。 */
    public static int mapEnchantment757To756(int id757) {
        if (id757 < 0 || id757 > MAX_ENCHANTMENT_757) {
            logUnknownOnce(PROTOCOL_756, "エンチャント", id757);
            return DROP;
        }
        return Maps756.ENCHANTMENT_756_MAP[id757];
    }

    /** 756のエンチャントID → 757のエンチャントID。並びは同一。範囲外は {@link #DROP}。 */
    public static int mapEnchantment756To757(int id756) {
        if (id756 < 0 || id756 > MAX_ENCHANTMENT_756) {
            logUnknownOnce(PROTOCOL_757, "エンチャント", id756);
            return DROP;
        }
        return Maps756.ENCHANTMENT_REVERSE_756_MAP[id756];
    }

    /** 757の音の名前 → 756の音の名前（Stop Sound 用）。数値の反転表と一致させる。 */
    public static String mapSoundName757To756(String name) {
        return mapSoundNameComposed(name, Maps756.SOUND_NAME_FALLBACK_757_TO_756_MAP);
    }

    /** 756の音の名前 → 757の音の名前（Stop Sound 用）。恒等写像のためそのまま返す。 */
    public static String mapSoundName756To757(String name) {
        return mapSoundNameComposed(name, Maps756.SOUND_NAME_FALLBACK_756_TO_757_MAP);
    }
"""
    insert_before("    // ------------------------------------------------------------------\n"
                  "    // 759 ⇔ 757（1.19 ⇔ 1.18／1.18.1）\n",
                  methods_756, "756アクセサ", "mapBlockState757To756")

    # 6. ディスパッチのコメント更新（756の連鎖を追記）
    old_dispatch_comment = ("    // 759は760の表を経由する（759↔760は恒等写像）。757は759の表を\n"
                            "    // 経由する（757↔763〜775は757→759→760→761→762→763→合成表の連鎖）。\n")
    new_dispatch_comment = ("    // 759は760の表を経由する（759↔760は恒等写像）。757は759の表を\n"
                            "    // 経由する（757↔763〜775は757→759→760→761→762→763→合成表の連鎖）。\n"
                            "    // 756は757の表を経由する（756↔763〜775は756→757→759→760→761→762→763→合成表の連鎖）。\n")
    if old_dispatch_comment not in src:
        print("アンカーが見つからない: dispatch コメント", file=sys.stderr)
        raise SystemExit(1)
    src = src.replace(old_dispatch_comment, new_dispatch_comment, 1)

    # 7. target 分岐への 756 追加（各 dispatch メソッドの 757 分岐の直前。
    # insert_after はメソッド冒頭の直後に入れるため、757 分岐より前に来る）
    chain_down = {
        "mapBlockState": ("mapBlockState757To756",
                          "mapBlockState759To757(mapBlockState760To759(mapBlockState761To760(mapBlockState762To761(mapBlockState763To762(mapBlockState(id765))))))"),
        "mapItem": ("mapItem757To756",
                    "mapItem759To757(mapItem760To759(mapItem761To760(mapItem762To761(mapItem763To762(mapItem(id765))))))"),
        "mapEntityType": ("mapEntityType757To756",
                          "mapEntityType759To757(mapEntityType760To759(mapEntityType761To760(mapEntityType762To761(mapEntityType763To762(mapEntityType(id765))))))"),
        "mapSound": ("mapSound757To756",
                     "mapSound759To757(mapSound760To759(mapSound761To760(mapSound762To761(mapSound763To762(mapSound(id765))))))"),
        "mapParticle": ("mapParticle757To756",
                        "mapParticle759To757(mapParticle760To759(mapParticle761To760(mapParticle762To761(mapParticle763To762(mapParticle(id765))))))"),
        "mapBlock": ("mapBlock757To756",
                     "mapBlock759To757(mapBlock760To759(mapBlock761To760(mapBlock762To761(mapBlock763To762(mapBlock(id765))))))"),
        "mapMenu": ("mapMenu757To756",
                    "mapMenu759To757(mapMenu760To759(mapMenu761To760(mapMenu762To761(mapMenu763To762(mapMenu(id765))))))"),
        "mapBlockEntityType": ("mapBlockEntityType757To756",
                               "mapBlockEntityType759To757(mapBlockEntityType760To759(mapBlockEntityType761To760(mapBlockEntityType762To761(mapBlockEntityType763To762(mapBlockEntityType(id765))))))"),
    }
    for name, (last, inner) in chain_down.items():
        body = (f"        if (clientProtocol == PROTOCOL_756) {{\n"
                f"            return {last}({inner});\n"
                f"        }}\n")
        anchor = f"    public static int {name}(int id765, int clientProtocol) {{\n"
        marker = f"return {last}({inner});"
        insert_after(anchor, body, f"dispatch {name} 756", marker)

    # mapItemReverse（引数名が id）。上向き（クライアント→765）のため、
    # 新しい隣接段（756→757）は最も内側（最初に適用）に入れる。
    # 下向き（mapItem 等）とは逆で、外側に巻くのは誤り。
    # 注意：757／759／760／761／762 の分岐は外側に巻く形で作られており、
    # 隣接表を逆順に合成している（実測：757 分岐は正しい合成と 724/1101 件
    # 食い違う。例：tropical_fish_spawn_egg 757 id 926 の正しい 765 id は
    # 1067 だが 757 分岐は 1066 を返す。§12.5 参照）。
    # 756 分岐だけは正しい順序で合成する。他分岐の修正は統括の判断待ち。
    insert_after("    public static int mapItemReverse(int id, int clientProtocol) {\n",
                 "        if (clientProtocol == PROTOCOL_756) {\n"
                 "            return mapItemReverse(mapItem762To763(mapItem761To762(mapItem760To761(mapItem759To760(mapItem757To759(mapItem756To757(id)))))));\n"
                 "        }\n",
                 "dispatch mapItemReverse 756",
                 "mapItemReverse(mapItem762To763(mapItem761To762(mapItem760To761(mapItem759To760(mapItem757To759(mapItem756To757(id)))))))")

    # mapEffect（757→756 は恒等写像。757 分岐の内側にそのままつなぐ）
    insert_after("    public static int mapEffect(int id765, int clientProtocol) {\n",
                 "        if (clientProtocol == PROTOCOL_756) {\n"
                 "            return mapEffect757To756(mapEffect759To757(mapEffect(id765)));\n"
                 "        }\n",
                 "dispatch mapEffect 756",
                 "return mapEffect757To756(mapEffect759To757(mapEffect(id765)));")

    # mapSoundName
    insert_after("    public static String mapSoundName(String name, int clientProtocol) {\n",
                 "        if (clientProtocol == PROTOCOL_756) {\n"
                 "            return mapSoundName757To756(mapSoundName759To757(mapSoundName760To759(mapSoundName761To760(mapSoundName762To761(mapSoundName763To762(mapSoundName(name)))))));\n"
                 "        }\n",
                 "dispatch mapSoundName 756",
                 "mapSoundName757To756(mapSoundName759To757(mapSoundName760To759(mapSoundName761To760(mapSoundName762To761(mapSoundName763To762(mapSoundName(name))))))")

    REMAPPER.write_text(src, encoding="utf-8")
    print("RegistryRemapper.java に 756 を差し込んだ。")


if __name__ == "__main__":
    raise SystemExit(main())
