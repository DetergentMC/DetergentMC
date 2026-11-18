#!/usr/bin/env python3
"""RegistryRemapper.java に 757（1.18／1.18.1）対応を差し込む。

雛形は tools/gen-760-remapper.py（隣接連鎖の配線）と
tools/insert-777-remapper.py（書き込み前の全入力検証・冪等ガード）。
アンカー付き挿入のみ行う。アンカーが見つからなければ失敗して何も書かない。
使い方: python3 insert-757-remapper.py
書き換えた後は必ず git diff で内容を確認してから javac で確認すること。

配線の方針（STAGE-A-757-NOTES.md §11.8 の申し送り）:
- 757 は合成表を作らない。757↔763〜775 は 757→759→760→761→762→763→
  合成表の連鎖で到達する（759〜762 と同じ隣接連鎖グループ）。
- 順向き（759→757）の展開済み表は RegistryTables757 の *_757_* 表を
  expand() で展開する。
- 反転（757→759）の表は invert() で作る。invert() だけでは名前の真値と
  食い違う箇所がある（ブロック状態 110 件・音 20 件。first-wins の罠。
  §11.3 の3を参照）ため、tools/maps757-corrections.json の補正
  （UP_757_CORRECTIONS。757 id → 759 id の対）を渡す。777 では補正が
  不要だった（偶然アルファベット順で正解した）が、757 では必須。
  アイテムだけは 766／777 の前例にならい、名前から作った既存の逆変換表
  （ITEM_REVERSE_757）を expand() でそのまま使う。
- エフェクト・ブロックエンティティ種別は表を作らずハードコードする
  （mapEffectUp774／mapBlockEntityTypeUp774 の前例）。
  エフェクト：759 の darkness（33）→DROP、1〜32 は恒等。
  ブロックエンティティ：759 の sculk_catalyst（34）・sculk_shrieker（35）
  →DROP、0〜33 は恒等。
- ダメージ種別は 42 件の恒等写像のプレースホルダ（DAMAGE_TYPE_757_MAP／
  DAMAGE_TYPE_REVERSE_757_MAP。759 の DAMAGE_TYPE_759 と同じ扱い。
  1.18／1.19 のどちらにも damage_type レジストリは無いため、
  ワイヤ上の呼び出し元は無い）。

書き込み前の検証（失敗したら何も書き込まない）:
- maps757-corrections.json の件数（ブロック状態 110 件・音 20 件・他 0 件）。
- RegistryTables757.java から読んだ表で、Java の expand()／invert() と
  同じ計算を Python で行い、invert(順向き)＋補正＝名前の真値
  （*_757_TO_759_* 表の展開）と全 7 種＋バイオームで一致することを確認する。
  アイテムは invert()＝ITEM_REVERSE_757 の展開とも一致することを確認する。
- ダメージ種別・エンチャント・属性・バイオームの展開結果の件数と代表値を
  確認する。
"""
from pathlib import Path
import json
import re
import sys

ROOT = Path(__file__).resolve().parent.parent
REMAPPER = ROOT / "src/main/java/io/detergentmc/protocol/RegistryRemapper.java"
TABLES = ROOT / "src/main/java/io/detergentmc/protocol/RegistryTables757.java"
CORRECTIONS_JSON = ROOT / "build/remap-data/maps757-corrections.json"

# 759側の最大ID（RegistryRemapper.java の MAX_*_759 と同じ値）。
MAX_759 = {
    "blockstates": 21447, "items": 1151, "entities": 117, "sounds": 1320,
    "particles": 92, "blocks": 932, "menus": 23, "biomes": 62,
}
# 757側の最大ID（vv-1.18.json 等の実測件数−1）。
MAX_757 = {
    "blockstates": 20341, "items": 1100, "entities": 112, "sounds": 1202,
    "particles": 87, "blocks": 897, "menus": 23, "biomes": 60,
}
# 759側の代替先（invert() の fallback。gen-757-tables.py の UP_757_FALLBACKS と同じ）。
FALLBACK_759 = {
    "blockstates": 1, "items": 1, "entities": 67, "sounds": 1112,
    "particles": 0, "blocks": 1, "menus": 11, "biomes": 1,
}
# 757側の代替先（expand() の fallback）。
FALLBACK_757 = {
    "blockstates": 1, "items": 1, "entities": 64, "sounds": 1018,
    "particles": 0, "blocks": 1, "menus": 11, "biomes": 1,
}
# 表名（RegistryTables757.java）。down は 759→757、up は 757→759。
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

    # --- 検証1：補正 JSON の件数（ブロック状態 110 件・音 20 件・他 0 件）---
    expected_counts = {"blockstates": 110, "items": 0, "entities": 0, "sounds": 20,
                       "particles": 0, "blocks": 0, "menus": 0}
    for kind, expected in expected_counts.items():
        got = corrections.get(kind)
        if got is None:
            raise SystemExit(f"補正 JSON に {kind} が無い")
        if len(got) != expected:
            raise SystemExit(f"補正 {kind} の件数が違う：{len(got)} != {expected}")
        for to_id, from_id in got:
            if not (0 <= to_id <= MAX_757[kind] and 0 <= from_id <= MAX_759[kind]):
                raise SystemExit(f"補正 {kind} の範囲外の対：[{to_id}, {from_id}]")
    print("補正 JSON の件数を確認：ブロック状態 110 件・音 20 件・他 0 件")

    # --- 検証2：invert(順向き)＋補正＝名前の真値（*_757_TO_759_* 表の展開）---
    for kind in KINDS:
        prefix = TABLE_PREFIX[kind]
        down_ranges = parse_int_table2(tables_text, prefix + "_757_RANGES")
        down_overrides = parse_int_table2(tables_text, prefix + "_757_OVERRIDES")
        truth_ranges = parse_int_table2(tables_text, prefix + "_757_TO_759_RANGES")
        truth_overrides = parse_int_table2(tables_text, prefix + "_757_TO_759_OVERRIDES")
        truth = expand(truth_ranges, truth_overrides, MAX_757[kind] + 1, FALLBACK_759[kind])
        corr = corrections[kind] if corrections[kind] else None
        got = invert(down_ranges, down_overrides,
                     MAX_759[kind] + 1, MAX_757[kind] + 1, FALLBACK_759[kind], corr)
        if got != truth:
            diffs = [i for i, (a, b) in enumerate(zip(got, truth)) if a != b]
            raise SystemExit(f"invert＋補正が名前の真値と違う（{kind}、{len(diffs)} 件）")
        print(f"invert＋補正＝名前の真値：{kind}（{len(truth)} 件）")
    # アイテムは invert()＝ITEM_REVERSE_757 の展開とも一致することを確認する。
    rev_ranges = parse_int_table2(tables_text, "ITEM_REVERSE_757_RANGES")
    rev_overrides = parse_int_table2(tables_text, "ITEM_REVERSE_757_OVERRIDES")
    rev = expand(rev_ranges, rev_overrides, MAX_757["items"] + 1, FALLBACK_759["items"])
    item_ranges = parse_int_table2(tables_text, "ITEM_757_RANGES")
    item_overrides = parse_int_table2(tables_text, "ITEM_757_OVERRIDES")
    item_inv = invert(item_ranges, item_overrides,
                      MAX_759["items"] + 1, MAX_757["items"] + 1, FALLBACK_759["items"], None)
    if item_inv != rev:
        raise SystemExit("invert(アイテム) が ITEM_REVERSE_757 と違う")
    print("invert(アイテム)＝ITEM_REVERSE_757 の展開（1101 件）")

    # --- 検証3：バイオームも invert(補正なし)＝名前の真値 ---
    bio_ranges = parse_int_table2(tables_text, "BIOME_757_RANGES")
    bio_overrides = parse_int_table2(tables_text, "BIOME_757_OVERRIDES")
    bio_truth = expand(parse_int_table2(tables_text, "BIOME_757_TO_759_RANGES"),
                       parse_int_table2(tables_text, "BIOME_757_TO_759_OVERRIDES"),
                       MAX_757["biomes"] + 1, FALLBACK_759["biomes"])
    bio_inv = invert(bio_ranges, bio_overrides,
                     MAX_759["biomes"] + 1, MAX_757["biomes"] + 1, FALLBACK_759["biomes"], None)
    if bio_inv != bio_truth:
        raise SystemExit("invert(バイオーム) が名前の真値と違う")
    if len(bio_truth) != 61 or bio_truth[7] != 8 or bio_truth[49] != 50 or bio_truth[51] != 53:
        raise SystemExit(f"バイオームの真値がおかしい：len={len(bio_truth)}")
    print("invert(バイオーム)＝名前の真値（61 件。757 側 7（forest）→759 側 8、"
          "49（dripstone_caves）→50、51→53 を確認）")

    # --- 検証4：ダメージ種別・エンチャント・属性の展開 ---
    dmg = expand(parse_int_table2(tables_text, "DAMAGE_TYPE_757_RANGES"),
                 parse_int_table2(tables_text, "DAMAGE_TYPE_757_OVERRIDES"), 42, 16)
    dmg_rev = expand(parse_int_table2(tables_text, "DAMAGE_TYPE_757_TO_759_RANGES"),
                     parse_int_table2(tables_text, "DAMAGE_TYPE_757_TO_759_OVERRIDES"), 42, 16)
    if dmg != list(range(42)) or dmg_rev != list(range(42)):
        raise SystemExit("ダメージ種別が 42 件の恒等写像ではない")
    enc = expand(parse_int_table2(tables_text, "ENCHANTMENT_757_RANGES"),
                 parse_int_table2(tables_text, "ENCHANTMENT_757_OVERRIDES"), 39, -1)
    enc_rev = expand(parse_int_table2(tables_text, "ENCHANTMENT_REVERSE_757_RANGES"),
                     parse_int_table2(tables_text, "ENCHANTMENT_REVERSE_757_OVERRIDES"), 38, -1)
    if len(enc) != 39 or enc[12] != -1 or enc[11] != 11 or enc[13] != 12 or enc[38] != 37:
        raise SystemExit(f"エンチャントの展開がおかしい：{enc[:15]}")
    if len(enc_rev) != 38 or enc_rev[12] != 13 or enc_rev[37] != 38:
        raise SystemExit(f"エンチャント逆向きの展開がおかしい：{enc_rev[:15]}")
    attr = expand(parse_int_table2(tables_text, "ATTRIBUTE_757_RANGES"),
                  parse_int_table2(tables_text, "ATTRIBUTE_757_OVERRIDES"), 13, -1)
    if attr != list(range(13)):
        raise SystemExit("属性が 13 件の恒等写像ではない")
    print("ダメージ種別 42 件恒等・エンチャント 39/38 件（swift_sneak→-1）・属性 13 件恒等を確認")

    # --- 補正の Java テキストを生成する ---
    blockstate_corr_java = java_int2_array([tuple(p) for p in corrections["blockstates"]])
    sound_corr_java = java_int2_array([tuple(p) for p in corrections["sounds"]])

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
    insert_before("import static io.detergentmc.protocol.RegistryTables759.*;",
                  "import static io.detergentmc.protocol.RegistryTables757.*;\n",
                  "import RegistryTables757", "RegistryTables757.*")

    # 2. PROTOCOL_757（PROTOCOL_759 の直前）
    insert_before("    /** 759（1.19）のプロトコル番号。 */",
                  "    /** 757（1.18／1.18.1）のプロトコル番号。 */\n"
                  "    public static final int PROTOCOL_757 = 757;\n",
                  "PROTOCOL_757", "PROTOCOL_757 = 757")

    # 3. 757定数（GENERIC_DAMAGE_TYPE_759 の直後）
    constants_757 = """
    /** 757側の最大の番号（ViaVersion mapping-1.18.json の件数−1）。 */
    public static final int MAX_BLOCK_STATE_757 = 20341;
    public static final int MAX_ITEM_757 = 1100;
    public static final int MAX_ENTITY_TYPE_757 = 112;
    public static final int MAX_SOUND_757 = 1202;
    public static final int MAX_PARTICLE_757 = 87;
    public static final int MAX_BLOCK_757 = 897;
    public static final int MAX_MENU_757 = 23;
    /** 757側のエンチャントは 0〜37（38件。swift_sneak が無い）。 */
    public static final int MAX_ENCHANTMENT_757 = 37;
    /** 757側のバイオームは 0〜60（61件。mangrove_swamp・deep_dark が無い）。 */
    public static final int MAX_BIOME_757 = 60;
    /** 757側のダメージ種別は 0〜41（42件。762 の順序の恒等写像。実バニラで検証する）。 */
    public static final int MAX_DAMAGE_TYPE_757 = 41;
    /** 757側のブロックエンティティ種別は 0〜33（34件）。 */
    public static final int MAX_BLOCK_ENTITY_757 = 33;
    /** 757側の属性は 0〜12（13件。759と同一順序）。 */
    public static final int MAX_ATTRIBUTE_757 = 12;
    /** 759側の属性は 0〜12（13件。757と同一順序）。 */
    public static final int MAX_ATTRIBUTE_759 = 12;
    /** 759側のエフェクトは 1〜33（33件。darkness=33。1ベース）。 */
    public static final int MAX_EFFECT_759 = 33;
    /** 757側のエフェクトは 1〜32（32件。darkness が無い。1ベース）。 */
    public static final int MAX_EFFECT_757 = 32;
    /** 759側の darkness エフェクト（757には無いため DROP）。 */
    public static final int DARKNESS_EFFECT_759 = 33;
    /** 759側の sculk_catalyst ブロックエンティティ（757には無いため DROP）。 */
    public static final int BLOCK_ENTITY_SCULK_CATALYST_759 = 34;
    /** 759側の sculk_shrieker ブロックエンティティ（757には無いため DROP）。 */
    public static final int BLOCK_ENTITY_SCULK_SHRIEKER_759 = 35;

    /** 757側の代替先（mapping-1.18.json の番号。Speed だけ 1ベースで 1）。 */
    public static final int STONE_BLOCK_STATE_757 = 1;
    public static final int AIR_BLOCK_STATE_757 = 0;
    public static final int STONE_ITEM_757 = 1;
    public static final int PIG_ENTITY_TYPE_757 = 64;
    public static final int GENERIC_SOUND_757 = 1018; // block.stone.break
    public static final int AMBIENT_PARTICLE_757 = 0; // ambient_entity_effect
    public static final int STONE_BLOCK_757 = 1;
    public static final int CRAFTING_MENU_757 = 11;
    public static final int SPEED_EFFECT_757 = 1;
    /** 757側の平原バイオーム（minecraft-data pc/1.18/biomes.json）。 */
    public static final int PLAINS_BIOME_757 = 1;
    /** 757側の generic ダメージ種別（762 と同一順序と仮定）。 */
    public static final int GENERIC_DAMAGE_TYPE_757 = 16;
"""
    insert_after("    public static final int GENERIC_DAMAGE_TYPE_759 = 16;\n",
                 constants_757, "757定数", "MAX_BLOCK_STATE_757")

    # 4. 757の展開済み表（BLOCK_STATE_760_TO_759_MAP の直前）
    maps_757 = """
    // ------------------------------------------------------------------
    // 759 ⇔ 757（1.19 ⇔ 1.18／1.18.1）の展開済み表。RegistryTables757
    // の区間＋上書きの表をクラスロード時に int[] へ展開する。
    // 順向き（759→757）は expand()、反転（757→759）は invert() で作る。
    // invert() だけでは名前の真値と食い違う箇所がある（ブロック状態
    // 110 件・音 20 件。first-wins の罠）ため、下の UP_757 補正を渡す。
    // アイテムだけは 766／777 の前例にならい、名前から作った既存の
    // 逆変換表（ITEM_REVERSE_757）を expand() でそのまま使う。
    // エフェクト・ブロックエンティティ種別は表を作らない（アクセサで
    // ハードコードする）。757 は合成表を作らない。
    // ------------------------------------------------------------------

    /** ブロック状態の反転の補正（757 id → 759 id）。110 件。invert() の
     * first-wins では、757 の同名の状態よりアルファベット順で先の代替先
     * （例：mud_bricks）が選ばれてしまう箇所を名前の真値に上書きする。
     * tools/maps757-corrections.json から生成。検証は
     * tools/verify-757-tables.py のチェックDを参照。 */
    private static final int[][] BLOCK_STATE_UP_757_CORRECTIONS = {
""" + blockstate_corr_java + """
    };

    /** 音の反転の補正（757 id → 759 id）。20 件。ブロック状態と同じ理由。 */
    private static final int[][] SOUND_UP_757_CORRECTIONS = {
""" + sound_corr_java + """
    };

    /** 759のブロック状態ID → 757のブロック状態ID（RegistryTables757）。 */
    private static final int[] BLOCK_STATE_759_TO_757_MAP =
            expand(BLOCK_STATE_757_RANGES, BLOCK_STATE_757_OVERRIDES, MAX_BLOCK_STATE_759 + 1,
                    STONE_BLOCK_STATE_757);
    /** 757のブロック状態ID → 759のブロック状態ID（反転＋補正）。 */
    private static final int[] BLOCK_STATE_757_TO_759_MAP = invert(
            BLOCK_STATE_757_RANGES, BLOCK_STATE_757_OVERRIDES,
            MAX_BLOCK_STATE_759 + 1, MAX_BLOCK_STATE_757 + 1,
            STONE_BLOCK_STATE_759, BLOCK_STATE_UP_757_CORRECTIONS);
    /** 759のアイテムID → 757のアイテムID。 */
    private static final int[] ITEM_759_TO_757_MAP =
            expand(ITEM_757_RANGES, ITEM_757_OVERRIDES, MAX_ITEM_759 + 1, STONE_ITEM_757);
    /** 757のアイテムID → 759のアイテムID（名前から作った逆変換表）。 */
    private static final int[] ITEM_757_TO_759_MAP =
            expand(ITEM_REVERSE_757_RANGES, ITEM_REVERSE_757_OVERRIDES, MAX_ITEM_757 + 1,
                    STONE_ITEM_759);
    /** 759のエンティティ種別ID → 757のエンティティ種別ID。 */
    private static final int[] ENTITY_TYPE_759_TO_757_MAP =
            expand(ENTITY_TYPE_757_RANGES, ENTITY_TYPE_757_OVERRIDES, MAX_ENTITY_TYPE_759 + 1,
                    PIG_ENTITY_TYPE_757);
    /** 757のエンティティ種別ID → 759のエンティティ種別ID（反転）。 */
    private static final int[] ENTITY_TYPE_757_TO_759_MAP = invert(
            ENTITY_TYPE_757_RANGES, ENTITY_TYPE_757_OVERRIDES,
            MAX_ENTITY_TYPE_759 + 1, MAX_ENTITY_TYPE_757 + 1, PIG_ENTITY_TYPE_759, null);
    /** 759の音ID → 757の音ID。 */
    private static final int[] SOUND_759_TO_757_MAP =
            expand(SOUND_757_RANGES, SOUND_757_OVERRIDES, MAX_SOUND_759 + 1, GENERIC_SOUND_757);
    /** 757の音ID → 759の音ID（反転＋補正）。 */
    private static final int[] SOUND_757_TO_759_MAP = invert(
            SOUND_757_RANGES, SOUND_757_OVERRIDES,
            MAX_SOUND_759 + 1, MAX_SOUND_757 + 1,
            GENERIC_SOUND_759, SOUND_UP_757_CORRECTIONS);
    /** 759のパーティクルID → 757のパーティクルID。 */
    private static final int[] PARTICLE_759_TO_757_MAP =
            expand(PARTICLE_757_RANGES, PARTICLE_757_OVERRIDES, MAX_PARTICLE_759 + 1,
                    AMBIENT_PARTICLE_757);
    /** 757のパーティクルID → 759のパーティクルID（反転）。 */
    private static final int[] PARTICLE_757_TO_759_MAP = invert(
            PARTICLE_757_RANGES, PARTICLE_757_OVERRIDES,
            MAX_PARTICLE_759 + 1, MAX_PARTICLE_757 + 1, AMBIENT_PARTICLE_759, null);
    /** 759のブロックID → 757のブロックID。 */
    private static final int[] BLOCK_759_TO_757_MAP =
            expand(BLOCK_757_RANGES, BLOCK_757_OVERRIDES, MAX_BLOCK_759 + 1, STONE_BLOCK_757);
    /** 757のブロックID → 759のブロックID（反転）。 */
    private static final int[] BLOCK_757_TO_759_MAP = invert(
            BLOCK_757_RANGES, BLOCK_757_OVERRIDES,
            MAX_BLOCK_759 + 1, MAX_BLOCK_757 + 1, STONE_BLOCK_757, null);
    /** 759のメニュー種別ID → 757のメニュー種別ID。 */
    private static final int[] MENU_759_TO_757_MAP =
            expand(MENU_757_RANGES, MENU_757_OVERRIDES, MAX_MENU_759 + 1, CRAFTING_MENU_757);
    /** 757のメニュー種別ID → 759のメニュー種別ID（反転）。 */
    private static final int[] MENU_757_TO_759_MAP = invert(
            MENU_757_RANGES, MENU_757_OVERRIDES,
            MAX_MENU_759 + 1, MAX_MENU_757 + 1, CRAFTING_MENU_759, null);
    /** 759のバイオームID → 757のバイオームID。mangrove_swamp→swamp、deep_dark→dripstone_caves。 */
    private static final int[] BIOME_759_TO_757_MAP =
            expand(BIOME_757_RANGES, BIOME_757_OVERRIDES, MAX_BIOME_759 + 1, PLAINS_BIOME_757);
    /** 757のバイオームID → 759のバイオームID（反転）。 */
    private static final int[] BIOME_757_TO_759_MAP = invert(
            BIOME_757_RANGES, BIOME_757_OVERRIDES,
            MAX_BIOME_759 + 1, MAX_BIOME_757 + 1, PLAINS_BIOME_759, null);
    /** ダメージ種別ID：759 → 757（762 の 42 件の恒等写像。1.18／1.19 の
     * どちらにも damage_type レジストリは無いため、ワイヤ上の呼び出し元は無い）。 */
    private static final int[] DAMAGE_TYPE_757_MAP = {
        0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19,
        20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32, 33, 34, 35, 36, 37,
        38, 39, 40, 41
    };
    /** ダメージ種別ID：757 → 759（762 の 42 件の恒等写像。呼び出し元は無い）。 */
    private static final int[] DAMAGE_TYPE_REVERSE_757_MAP = {
        0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19,
        20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32, 33, 34, 35, 36, 37,
        38, 39, 40, 41
    };
    /** 759の属性ID → 757の属性ID。並びは同一。範囲外は {@link #DROP}。 */
    private static final int[] ATTRIBUTE_757_MAP =
            expand(ATTRIBUTE_757_RANGES, ATTRIBUTE_757_OVERRIDES, MAX_ATTRIBUTE_759 + 1, DROP);
    /** 757の属性ID → 759の属性ID。並びは同一。範囲外は {@link #DROP}。 */
    private static final int[] ATTRIBUTE_REVERSE_757_MAP =
            expand(ATTRIBUTE_REVERSE_757_RANGES, ATTRIBUTE_REVERSE_757_OVERRIDES,
                    MAX_ATTRIBUTE_757 + 1, DROP);
    /** 759のエンチャントID → 757のエンチャントID。swift_sneak（12）は {@link #DROP}。 */
    private static final int[] ENCHANTMENT_757_MAP =
            expand(ENCHANTMENT_757_RANGES, ENCHANTMENT_757_OVERRIDES,
                    MAX_ENCHANTMENT_759 + 1, DROP);
    /** 757のエンチャントID → 759のエンチャントID。12 以降は +1 ずれる。 */
    private static final int[] ENCHANTMENT_REVERSE_757_MAP =
            expand(ENCHANTMENT_REVERSE_757_RANGES, ENCHANTMENT_REVERSE_757_OVERRIDES,
                    MAX_ENCHANTMENT_757 + 1, DROP);
"""
    insert_before("    /** 760のBlockStateID → 759のBlockStateID（RegistryTables759。恒等写像）。 */",
                  maps_757, "757の展開済み表", "BLOCK_STATE_759_TO_757_MAP")

    # 5. 音の名前表（SOUND_NAME_FALLBACK_761_TO_760_MAP の直前）
    sound_name_maps_757 = """
    /** 759の音の名前 → 757の音の名前（Stop Sound 用）。数値の反転表と一致させる。 */
    private static final Map<String, String> SOUND_NAME_FALLBACK_759_TO_757_MAP =
            buildSoundNameMap(SOUND_NAME_FALLBACKS_759_TO_757);

    /** 757の音の名前 → 759の音の名前（Stop Sound 用）。恒等写像のため空。 */
    private static final Map<String, String> SOUND_NAME_FALLBACK_757_TO_759_MAP =
            buildSoundNameMap(SOUND_NAME_FALLBACKS_757_TO_759);
"""
    insert_before("    /** 761の音の名前 → 760の音の名前（Stop Sound 用）。数値の合成表と一致させる。 */",
                  sound_name_maps_757, "音の名前表757",
                  "SOUND_NAME_FALLBACK_759_TO_757_MAP")

    # 6. アクセサメソッド（760 ⇔ 759 節の直前）
    methods_757 = """
    // ------------------------------------------------------------------
    // 759 ⇔ 757（1.19 ⇔ 1.18／1.18.1）
    // パケットIDは 123 件が変わる（PacketIds の 757 列を参照）。
    // レジストリは RegistryTables757（ViaVersion mapping-1.18／1.19.json
    // から名前で突き合わせたもの）を使う。757↔763〜775 は隣接表の連鎖
    // （757→759→760→761→762→763→合成表）で到達する。合成表は作らない。
    // Join Game／Respawn は dimensionType の値→キーと lastDeathLocation
    // の追加が違う（Stage B で PlayTranslator が対応する）。
    // ------------------------------------------------------------------

    /** 759のブロック状態ID → 757のブロック状態ID。範囲外はSTONE_BLOCK_STATE_757 に相当する代替。 */
    public static int mapBlockState759To757(int id759) {
        if (id759 < 0 || id759 >= BLOCK_STATE_759_TO_757_MAP.length) {
            logUnknownComposedOnce("759→757", "ブロック状態", id759);
            return STONE_BLOCK_STATE_757;
        }
        return BLOCK_STATE_759_TO_757_MAP[id759];
    }

    /** 757のブロック状態ID → 759のブロック状態ID。範囲外はSTONE_BLOCK_STATE_759 に相当する代替。 */
    public static int mapBlockState757To759(int id757) {
        if (id757 < 0 || id757 >= BLOCK_STATE_757_TO_759_MAP.length) {
            logUnknownComposedOnce("757→759", "ブロック状態", id757);
            return STONE_BLOCK_STATE_759;
        }
        return BLOCK_STATE_757_TO_759_MAP[id757];
    }

    /** 759のアイテムID → 757のアイテムID。範囲外はSTONE_ITEM_757 に相当する代替。 */
    public static int mapItem759To757(int id759) {
        if (id759 < 0 || id759 >= ITEM_759_TO_757_MAP.length) {
            logUnknownComposedOnce("759→757", "アイテム", id759);
            return STONE_ITEM_757;
        }
        return ITEM_759_TO_757_MAP[id759];
    }

    /** 757のアイテムID → 759のアイテムID。範囲外はSTONE_ITEM_759 に相当する代替。 */
    public static int mapItem757To759(int id757) {
        if (id757 < 0 || id757 >= ITEM_757_TO_759_MAP.length) {
            logUnknownComposedOnce("757→759", "アイテム", id757);
            return STONE_ITEM_759;
        }
        return ITEM_757_TO_759_MAP[id757];
    }

    /** 759のエンティティ種別ID → 757のエンティティ種別ID。範囲外はPIG_ENTITY_TYPE_757 に相当する代替。 */
    public static int mapEntityType759To757(int id759) {
        if (id759 < 0 || id759 >= ENTITY_TYPE_759_TO_757_MAP.length) {
            logUnknownComposedOnce("759→757", "エンティティ種別", id759);
            return PIG_ENTITY_TYPE_757;
        }
        return ENTITY_TYPE_759_TO_757_MAP[id759];
    }

    /** 757のエンティティ種別ID → 759のエンティティ種別ID。範囲外はPIG_ENTITY_TYPE_759 に相当する代替。 */
    public static int mapEntityType757To759(int id757) {
        if (id757 < 0 || id757 >= ENTITY_TYPE_757_TO_759_MAP.length) {
            logUnknownComposedOnce("757→759", "エンティティ種別", id757);
            return PIG_ENTITY_TYPE_759;
        }
        return ENTITY_TYPE_757_TO_759_MAP[id757];
    }

    /** 759の音ID → 757の音ID。範囲外はGENERIC_SOUND_757 に相当する代替。 */
    public static int mapSound759To757(int id759) {
        if (id759 < 0 || id759 >= SOUND_759_TO_757_MAP.length) {
            logUnknownComposedOnce("759→757", "音", id759);
            return GENERIC_SOUND_757;
        }
        return SOUND_759_TO_757_MAP[id759];
    }

    /** 757の音ID → 759の音ID。範囲外はGENERIC_SOUND_759 に相当する代替。 */
    public static int mapSound757To759(int id757) {
        if (id757 < 0 || id757 >= SOUND_757_TO_759_MAP.length) {
            logUnknownComposedOnce("757→759", "音", id757);
            return GENERIC_SOUND_759;
        }
        return SOUND_757_TO_759_MAP[id757];
    }

    /** 759のパーティクルID → 757のパーティクルID。範囲外はAMBIENT_PARTICLE_757 に相当する代替。 */
    public static int mapParticle759To757(int id759) {
        if (id759 < 0 || id759 >= PARTICLE_759_TO_757_MAP.length) {
            logUnknownComposedOnce("759→757", "パーティクル", id759);
            return AMBIENT_PARTICLE_757;
        }
        return PARTICLE_759_TO_757_MAP[id759];
    }

    /** 757のパーティクルID → 759のパーティクルID。範囲外はAMBIENT_PARTICLE_759 に相当する代替。 */
    public static int mapParticle757To759(int id757) {
        if (id757 < 0 || id757 >= PARTICLE_757_TO_759_MAP.length) {
            logUnknownComposedOnce("757→759", "パーティクル", id757);
            return AMBIENT_PARTICLE_759;
        }
        return PARTICLE_757_TO_759_MAP[id757];
    }

    /** 759のブロックID → 757のブロックID。範囲外はSTONE_BLOCK_757 に相当する代替。 */
    public static int mapBlock759To757(int id759) {
        if (id759 < 0 || id759 >= BLOCK_759_TO_757_MAP.length) {
            logUnknownComposedOnce("759→757", "ブロック", id759);
            return STONE_BLOCK_757;
        }
        return BLOCK_759_TO_757_MAP[id759];
    }

    /** 757のブロックID → 759のブロックID。範囲外はSTONE_BLOCK_759 に相当する代替。 */
    public static int mapBlock757To759(int id757) {
        if (id757 < 0 || id757 >= BLOCK_757_TO_759_MAP.length) {
            logUnknownComposedOnce("757→759", "ブロック", id757);
            return STONE_BLOCK_759;
        }
        return BLOCK_757_TO_759_MAP[id757];
    }

    /** 759のメニュー種別ID → 757のメニュー種別ID。範囲外はCRAFTING_MENU_757 に相当する代替。 */
    public static int mapMenu759To757(int id759) {
        if (id759 < 0 || id759 >= MENU_759_TO_757_MAP.length) {
            logUnknownComposedOnce("759→757", "メニュー", id759);
            return CRAFTING_MENU_757;
        }
        return MENU_759_TO_757_MAP[id759];
    }

    /** 757のメニュー種別ID → 759のメニュー種別ID。範囲外はCRAFTING_MENU_759 に相当する代替。 */
    public static int mapMenu757To759(int id757) {
        if (id757 < 0 || id757 >= MENU_757_TO_759_MAP.length) {
            logUnknownComposedOnce("757→759", "メニュー", id757);
            return CRAFTING_MENU_759;
        }
        return MENU_757_TO_759_MAP[id757];
    }

    /**
     * 759のブロックエンティティ種別ID → 757のブロックエンティティ種別ID。
     * 759にしか無い sculk_catalyst（34）・sculk_shrieker（35）は {@link #DROP}。
     */
    public static int mapBlockEntityType759To757(int id759) {
        if (id759 < 0 || id759 > MAX_BLOCK_ENTITY_759) {
            logUnknownComposedOnce("759→757", "ブロックエンティティ種別", id759);
            return DROP;
        }
        if (id759 == BLOCK_ENTITY_SCULK_CATALYST_759
                || id759 == BLOCK_ENTITY_SCULK_SHRIEKER_759) {
            return DROP;
        }
        return id759;
    }

    /**
     * 757のブロックエンティティ種別ID → 759のブロックエンティティ種別ID。
     * 757にしか無い種別は無いため DROP にはならない。
     */
    public static int mapBlockEntityType757To759(int id757) {
        if (id757 < 0 || id757 > MAX_BLOCK_ENTITY_757) {
            logUnknownComposedOnce("757→759", "ブロックエンティティ種別", id757);
            return DROP;
        }
        return id757;
    }

    /** 759のバイオームID → 757のバイオームID。範囲外はPLAINS_BIOME_757 に相当する代替。 */
    public static int mapBiome759To757(int id759) {
        if (id759 < 0 || id759 >= BIOME_759_TO_757_MAP.length) {
            logUnknownComposedOnce("759→757", "バイオーム", id759);
            return PLAINS_BIOME_757;
        }
        return BIOME_759_TO_757_MAP[id759];
    }

    /** 757のバイオームID → 759のバイオームID。範囲外はPLAINS_BIOME_759 に相当する代替。 */
    public static int mapBiome757To759(int id757) {
        if (id757 < 0 || id757 >= BIOME_757_TO_759_MAP.length) {
            logUnknownComposedOnce("757→759", "バイオーム", id757);
            return PLAINS_BIOME_759;
        }
        return BIOME_757_TO_759_MAP[id757];
    }

    /** 759のダメージ種別ID → 757のダメージ種別ID（恒等写像。ただし1.18／1.19 の codec に
     * damage_type レジストリは存在しないため、ワイヤ上の呼び出し元は無い）。 */
    public static int mapDamageType759To757(int id759) {
        if (id759 < 0 || id759 > MAX_DAMAGE_TYPE_759) {
            logUnknownOnce(PROTOCOL_757, "ダメージ種別", id759);
            return DROP;
        }
        return DAMAGE_TYPE_757_MAP[id759];
    }

    /** 757のダメージ種別ID → 759のダメージ種別ID（恒等写像。ワイヤ上の呼び出し元は無い）。 */
    public static int mapDamageType757To759(int id757) {
        if (id757 < 0 || id757 > MAX_DAMAGE_TYPE_757) {
            logUnknownOnce(PROTOCOL_759, "ダメージ種別", id757);
            return DROP;
        }
        return DAMAGE_TYPE_REVERSE_757_MAP[id757];
    }

    /**
     * 759のエフェクトID → 757のエフェクトID。darkness（33）は757に無いため
     * {@link #DROP}、1〜32 は恒等写像。範囲外は Speed（1）。
     */
    public static int mapEffect759To757(int id759) {
        if (id759 < 0 || id759 > MAX_EFFECT_759) {
            logUnknownComposedOnce("759→757", "エフェクト", id759);
            return SPEED_EFFECT_757;
        }
        if (id759 == DARKNESS_EFFECT_759) {
            return DROP;
        }
        return id759;
    }

    /**
     * 757のエフェクトID → 759のエフェクトID。1〜32 は恒等写像。
     * 範囲外は Speed（1）。
     */
    public static int mapEffect757To759(int id757) {
        if (id757 < 0 || id757 > MAX_EFFECT_757) {
            logUnknownComposedOnce("757→759", "エフェクト", id757);
            return SPEED_EFFECT_759;
        }
        return id757;
    }

    /** 759の属性ID → 757の属性ID。並びは同一。範囲外は {@link #DROP}。 */
    public static int mapAttribute759To757(int id759) {
        if (id759 < 0 || id759 > MAX_ATTRIBUTE_759) {
            logUnknownOnce(PROTOCOL_757, "属性", id759);
            return DROP;
        }
        return ATTRIBUTE_757_MAP[id759];
    }

    /** 757の属性ID → 759の属性ID。並びは同一。範囲外は {@link #DROP}。 */
    public static int mapAttribute757To759(int id757) {
        if (id757 < 0 || id757 > MAX_ATTRIBUTE_757) {
            logUnknownOnce(PROTOCOL_759, "属性", id757);
            return DROP;
        }
        return ATTRIBUTE_REVERSE_757_MAP[id757];
    }

    /** 757の属性ID → 属性の名前（Update Attributes は 1.18 では名前形式のため）。 */
    public static String attributeName757(int id757) {
        if (id757 < 0 || id757 >= ATTRIBUTE_757_NAMES.length) {
            return null;
        }
        return ATTRIBUTE_757_NAMES[id757];
    }

    /**
     * 属性の名前 → 757の属性ID（765／763／764 は属性が名前形式のため、
     * 757向けに変換するときに使う）。無い名前は {@link #DROP}。
     */
    public static int attributeId757(String name) {
        if (name == null) {
            return DROP;
        }
        String plain = stripMinecraftPrefix(name);
        for (int i = 0; i < ATTRIBUTE_757_NAMES.length; i++) {
            if (ATTRIBUTE_757_NAMES[i].equals(plain)) {
                return i;
            }
        }
        return DROP;
    }

    /** 759のエンチャントID → 757のエンチャントID。swift_sneak（12）は {@link #DROP}。範囲外も {@link #DROP}。 */
    public static int mapEnchantment759To757(int id759) {
        if (id759 < 0 || id759 > MAX_ENCHANTMENT_759) {
            logUnknownOnce(PROTOCOL_757, "エンチャント", id759);
            return DROP;
        }
        return ENCHANTMENT_757_MAP[id759];
    }

    /** 757のエンチャントID → 759のエンチャントID。12 以降は +1 ずれる。範囲外は {@link #DROP}。 */
    public static int mapEnchantment757To759(int id757) {
        if (id757 < 0 || id757 > MAX_ENCHANTMENT_757) {
            logUnknownOnce(PROTOCOL_759, "エンチャント", id757);
            return DROP;
        }
        return ENCHANTMENT_REVERSE_757_MAP[id757];
    }

    /** 759の音の名前 → 757の音の名前（Stop Sound 用）。数値の反転表と一致させる。 */
    public static String mapSoundName759To757(String name) {
        return mapSoundNameComposed(name, SOUND_NAME_FALLBACK_759_TO_757_MAP);
    }

    /** 757の音の名前 → 759の音の名前（Stop Sound 用）。恒等写像のためそのまま返す。 */
    public static String mapSoundName757To759(String name) {
        return mapSoundNameComposed(name, SOUND_NAME_FALLBACK_757_TO_759_MAP);
    }
"""
    insert_before("    // ------------------------------------------------------------------\n"
                  "    // 760 ⇔ 759（1.19.1／1.19.2 ⇔ 1.19）",
                  methods_757, "757アクセサ", "mapBlockState759To757")

    # 7. ディスパッチのコメント更新（757の連鎖を追記）
    old_dispatch_comment = ("    // 759は760の表を経由する（759↔760は恒等写像）。\n")
    new_dispatch_comment = ("    // 759は760の表を経由する（759↔760は恒等写像）。757は759の表を\n"
                            "    // 経由する（757↔763〜775は757→759→760→761→762→763→合成表の連鎖）。\n")
    if old_dispatch_comment not in src:
        print("アンカーが見つからない: dispatch コメント", file=sys.stderr)
        raise SystemExit(1)
    src = src.replace(old_dispatch_comment, new_dispatch_comment, 1)

    # 8. target 分岐への 757 追加（各 dispatch メソッドの 760 分岐の直前。
    # insert_after はメソッド冒頭の直後に入れるため、760 分岐より前に来る）
    chain_down = {
        "mapBlockState": ("mapBlockState759To757",
                          "mapBlockState760To759(mapBlockState761To760(mapBlockState762To761(mapBlockState763To762(mapBlockState(id765)))))"),
        "mapItem": ("mapItem759To757",
                    "mapItem760To759(mapItem761To760(mapItem762To761(mapItem763To762(mapItem(id765)))))"),
        "mapEntityType": ("mapEntityType759To757",
                          "mapEntityType760To759(mapEntityType761To760(mapEntityType762To761(mapEntityType763To762(mapEntityType(id765)))))"),
        "mapSound": ("mapSound759To757",
                     "mapSound760To759(mapSound761To760(mapSound762To761(mapSound763To762(mapSound(id765)))))"),
        "mapParticle": ("mapParticle759To757",
                        "mapParticle760To759(mapParticle761To760(mapParticle762To761(mapParticle763To762(mapParticle(id765)))))"),
        "mapBlock": ("mapBlock759To757",
                     "mapBlock760To759(mapBlock761To760(mapBlock762To761(mapBlock763To762(mapBlock(id765)))))"),
        "mapMenu": ("mapMenu759To757",
                    "mapMenu760To759(mapMenu761To760(mapMenu762To761(mapMenu763To762(mapMenu(id765)))))"),
        "mapBlockEntityType": ("mapBlockEntityType759To757",
                               "mapBlockEntityType760To759(mapBlockEntityType761To760(mapBlockEntityType762To761(mapBlockEntityType763To762(mapBlockEntityType(id765)))))"),
    }
    for name, (last, inner) in chain_down.items():
        body = (f"        if (clientProtocol == PROTOCOL_757) {{\n"
                f"            return {last}({inner});\n"
                f"        }}\n")
        anchor = f"    public static int {name}(int id765, int clientProtocol) {{\n"
        marker = f"return {last}({inner});"
        insert_after(anchor, body, f"dispatch {name} 757", marker)

    # mapItemReverse（引数名が id）
    insert_after("    public static int mapItemReverse(int id, int clientProtocol) {\n",
                 "        if (clientProtocol == PROTOCOL_757) {\n"
                 "            return mapItemReverse(mapItem757To759(mapItem759To760(mapItem760To761(mapItem761To762(mapItem762To763(id))))));\n"
                 "        }\n",
                 "dispatch mapItemReverse 757",
                 "mapItemReverse(mapItem757To759(mapItem759To760(mapItem760To761(mapItem761To762(mapItem762To763(id))))))")

    # mapEffect（765→763 の +1 を経由し、darkness（33）を DROP する）
    insert_after("    public static int mapEffect(int id765, int clientProtocol) {\n",
                 "        if (clientProtocol == PROTOCOL_757) {\n"
                 "            return mapEffect759To757(mapEffect(id765));\n"
                 "        }\n",
                 "dispatch mapEffect 757",
                 "return mapEffect759To757(mapEffect(id765));")

    # mapSoundName
    insert_after("    public static String mapSoundName(String name, int clientProtocol) {\n",
                 "        if (clientProtocol == PROTOCOL_757) {\n"
                 "            return mapSoundName759To757(mapSoundName760To759(mapSoundName761To760(mapSoundName762To761(mapSoundName763To762(mapSoundName(name))))));\n"
                 "        }\n",
                 "dispatch mapSoundName 757",
                 "mapSoundName759To757(mapSoundName760To759(mapSoundName761To760(mapSoundName762To761(mapSoundName763To762(mapSoundName(name))))))")

    REMAPPER.write_text(src, encoding="utf-8")
    print("RegistryRemapper.java に 757 を差し込んだ。")


if __name__ == "__main__":
    raise SystemExit(main())
