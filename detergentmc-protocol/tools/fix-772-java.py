#!/usr/bin/env python3
"""insert-772-java.py の手直し（shift でずれた箇所の修正＋770対応の追加）。

gen-772-java.py を再実行したらこのスクリプトも再実行すること。
各置換は出現回数がちょうど1件（rep_all は指定件数）のときだけ適用し、
違えば失敗を報告する。
"""
import sys
from pathlib import Path

P = Path(__file__).resolve().parent / "insert-772-java.py"
s = P.read_text(encoding="utf-8")
failed = []


def rep(old, new, label):
    global s
    n = s.count(old)
    if n != 1:
        failed.append(f"{label}: 一致 {n} 件 (期待 1 件)")
        return
    s = s.replace(old, new)


def rep_all(old, new, label, expected):
    global s
    n = s.count(old)
    if n != expected:
        failed.append(f"{label}: 一致 {n} 件 (期待 {expected} 件)")
        return
    s = s.replace(old, new)


# --- A. docstring ---
rep(
    "  - 合成表（763↔772、764↔772、765↔772、766↔772、767↔772、768↔772、769↔772）: *_763_TO_772_MAP など98件。",
    "  - 合成表（763↔772、764↔772、765↔772、766↔772、767↔772、768↔772、769↔772、770↔772）: *_763_TO_772_MAP など112件。",
    "docstring 合成表")
rep(
    "  - 音の名前の表（String）: SOUND_NAME_FALLBACK_*_772_MAP など14件。",
    "  - 音の名前の表（String）: SOUND_NAME_FALLBACK_*_772_MAP など16件。",
    "docstring 音の表")
rep(
    "  - アクセサ：771↔772、772→771（上向き）、763／764／765／766／767／768／769↔772。",
    "  - アクセサ：771↔772、772→771（上向き）、763／764／765／766／767／768／769／770↔772。",
    "docstring アクセサ")
rep(
    "    属性は 771 の 32 件から 772 の 35 件へ（camera_distance が 8 に挿入、\n"
    "    waypoint_transmit_range・waypoint_receive_range を末尾に追加。逆向きは\n"
    "    新3件を DROP）、エンチャントは 42 件同士で",
    "    属性は 771・772 とも 35 件で同一、エンチャントは 42 件同士で",
    "docstring 属性")
rep(
    "    並びは同一（minecraft-data の dataPaths.json で 1.21.7 の\n"
    "    enchantments は pc/1.21.7 と確認。1.21.1 の並びと同一）。",
    "    並びは同一（minecraft-data の dataPaths.json で 1.21.8 の\n"
    "    enchantments は pc/1.21.8 と確認。1.21.1 の並びと同一）。",
    "docstring エンチャント")
rep(
    "    バナー・絵画・装飾パターンは 771 と同一のため恒等写像。",
    "    バナー・装飾パターンは 771 と同一のため恒等写像。絵画だけが\n"
    "    変わる（dennis が 14 に挿入され 51 件。恒等写像ではない）。",
    "docstring 絵画")

# --- B. 合成表の下向きファイルの接尾辞（763〜770 の 8 件） ---
rep(
    '    for suffix in ("763", "764", "765", "766", "767", "768", "769")',
    '    for suffix in ("763", "764", "765", "766", "767", "768", "769", "770")',
    "Down ファイル接尾辞")

# --- C. CONSTANTS_MAX（772 の実数値） ---
rep(
    '    public static final int MAX_ITEM_772 = 1414;',
    '    public static final int MAX_ITEM_772 = 1415;',
    "MAX_ITEM 値")
rep(
    '    public static final int MAX_SOUND_772 = 1726;',
    '    public static final int MAX_SOUND_772 = 1727;',
    "MAX_SOUND 値")
rep(
    '    /** 772側のエフェクトも 0〜38（Speed=0。771と同一。minecraft-data の\n'
    '        dataPaths.json で 1.21.7 の effects は pc/1.21.7 と確認）。 */',
    '    /** 772側のエフェクトも 0〜38（Speed=0。771と同一。minecraft-data の\n'
    '        dataPaths.json で 1.21.8 の effects は pc/1.21.8 と確認）。 */',
    "MAX_EFFECT コメント")
rep(
    '    /** 772側の属性は 0〜34（771 の 32 件に camera_distance（8）・\n'
    '        waypoint_transmit_range（33）・waypoint_receive_range（34）を\n'
    '        追加した 35 件。ViaVersion mapping-1.21.7.json で確認）。 */',
    '    /** 772側の属性は 0〜34（771 と同じ 35 件。\n'
    '        ViaVersion mapping-1.21.7.json で確認）。 */',
    "MAX_ATTRIBUTE コメント")
rep(
    '        minecraft-data の dataPaths.json で 1.21.7 の enchantments は\n'
    '        pc/1.21.7 と確認（1.21.1 の並びと同一）。実機の Registry Data での',
    '        minecraft-data の dataPaths.json で 1.21.8 の enchantments は\n'
    '        pc/1.21.8 と確認（1.21.1 の並びと同一）。実機の Registry Data での',
    "MAX_ENCHANTMENT コメント")

# --- D. CONSTANTS_FALLBACK（772 の実数値） ---
rep(
    '     * ブタは 772 では 95（happy_ghast の挿入で 771 の 94 から +1）、\n'
    '     * 石の破壊音は 772 では 1441（新音 29 件の挿入と entity.leash_knot\n'
    '     * 2件・entity.puffer_fish.ambient・block.sand.wind の削除で\n'
    '     * 771 の 1417 からずれた）。 */\n'
    '    public static final int STONE_BLOCK_STATE_772 = 1;\n'
    '    public static final int AIR_BLOCK_STATE_772 = 0;\n'
    '    public static final int STONE_ITEM_772 = 1;\n'
    '    public static final int PIG_ENTITY_TYPE_772 = 95;\n'
    '    public static final int GENERIC_SOUND_772 = 1441; // block.stone.break',
    '     * ブタは 772 でも 95（エンティティに変化なし）、\n'
    '     * 石の破壊音は 772 では 1442（新音 music_disc.lava_chicken が\n'
    '     * 939 に挿入され、771 の 1441 から +1）。 */\n'
    '    public static final int STONE_BLOCK_STATE_772 = 1;\n'
    '    public static final int AIR_BLOCK_STATE_772 = 0;\n'
    '    public static final int STONE_ITEM_772 = 1;\n'
    '    public static final int PIG_ENTITY_TYPE_772 = 95;\n'
    '    public static final int GENERIC_SOUND_772 = 1442; // block.stone.break',
    "代替先の値")

# --- E2. KINDS：765 のパーティクル代替先は AMBIENT_PARTICLE_765 ---
# shift が AMBIENT_PARTICLE_765 を ENTITY_EFFECT_PARTICLE_765 に
# 書き換えてしまった（ENTITY_EFFECT_PARTICLE_765 は存在しない定数。
# ambient_entity_effect は 765 にあって 766 で消えた）。
rep(
    '     "AMBIENT_PARTICLE_763", "AMBIENT_PARTICLE_764", "ENTITY_EFFECT_PARTICLE_765", "ENTITY_EFFECT_PARTICLE_766",',
    '     "AMBIENT_PARTICLE_763", "AMBIENT_PARTICLE_764", "AMBIENT_PARTICLE_765", "ENTITY_EFFECT_PARTICLE_766",',
    "KINDS の 765 パーティクル代替先")

# --- F. adjacent_maps()：代替先の index と ITEM_REVERSE の件数 ---
rep(
    '            f"                    {kind[27]});")',
    '            f"                    {kind[30]});")',
    "adjacent_maps 代替先 index")
rep(
    '        "            expand(ITEM_REVERSE_772_RANGES, ITEM_REVERSE_772_OVERRIDES, 1415, -1);")',
    '        "            expand(ITEM_REVERSE_772_RANGES, ITEM_REVERSE_772_OVERRIDES, 1416, -1);")',
    "ITEM_REVERSE の件数")

# --- G. composed_maps()：770 までの 16 ペア（771 は隣接表で扱う） ---
rep(
    '    # タプルから取る（763_TO_772: 件数 index 3, 代替先 772=index 27／\n'
    '    # 772_TO_763: 件数 index 4, 代替先 763=index 19）。件数は 3〜18、\n'
    '    # 代替先は 19（763）〜27（772）。771↔772 は隣接表で扱うため含めない。\n'
    '    pairs = [\n'
    '        ("763_TO_772", 3, 27), ("772_TO_763", 4, 19),\n'
    '        ("764_TO_772", 5, 27), ("772_TO_764", 6, 20),\n'
    '        ("765_TO_772", 7, 27), ("772_TO_765", 8, 21),\n'
    '        ("766_TO_772", 9, 27), ("772_TO_766", 10, 22),\n'
    '        ("767_TO_772", 11, 27), ("772_TO_767", 12, 23),\n'
    '        ("768_TO_772", 13, 27), ("772_TO_768", 14, 24),\n'
    '        ("769_TO_772", 15, 27), ("772_TO_769", 16, 25),\n'
    '    ]',
    '    # タプルから取る（763_TO_772: 件数 index 3, 代替先 772=index 30／\n'
    '    # 772_TO_763: 件数 index 4, 代替先 763=index 21）。件数は 3〜20、\n'
    '    # 代替先は 21（763）〜30（772）。771↔772 は隣接表で扱うため含めない。\n'
    '    pairs = [\n'
    '        ("763_TO_772", 3, 30), ("772_TO_763", 4, 21),\n'
    '        ("764_TO_772", 5, 30), ("772_TO_764", 6, 22),\n'
    '        ("765_TO_772", 7, 30), ("772_TO_765", 8, 23),\n'
    '        ("766_TO_772", 9, 30), ("772_TO_766", 10, 24),\n'
    '        ("767_TO_772", 11, 30), ("772_TO_767", 12, 25),\n'
    '        ("768_TO_772", 13, 30), ("772_TO_768", 14, 26),\n'
    '        ("769_TO_772", 15, 30), ("772_TO_769", 16, 27),\n'
    '        ("770_TO_772", 17, 30), ("772_TO_770", 18, 28),\n'
    '    ]',
    "composed_maps のペア")

# --- H/K. 逆向きの代替先は「771 側」の定数（kind[29]）。2 箇所 ---
rep_all(
    'fb771 = kind[26]',
    'fb771 = kind[29]',
    "up の代替先 index",
    2)
rep(
    '        fb772 = kind[27]',
    '        fb772 = kind[30]',
    "adjacent_accessors の代替先 index")
rep(
    '            fb_772 = kind[27]',
    '            fb_772 = kind[30]',
    "composed_accessors の代替先 index")

# --- I. SOUND_NAME_MAPS：770 の 2 件を追加 ---
rep(
    '    private static final Map<String, String> SOUND_NAME_FALLBACK_772_TO_771_MAP =\n'
    '            buildSoundNameMap(SOUND_NAME_FALLBACKS_772_TO_771);\n',
    '    private static final Map<String, String> SOUND_NAME_FALLBACK_772_TO_771_MAP =\n'
    '            buildSoundNameMap(SOUND_NAME_FALLBACKS_772_TO_771);\n'
    '    private static final Map<String, String> SOUND_NAME_FALLBACK_770_TO_772_MAP =\n'
    '            buildSoundNameMap(SOUND_NAME_FALLBACKS_770_TO_772);\n'
    '    private static final Map<String, String> SOUND_NAME_FALLBACK_772_TO_770_MAP =\n'
    '            buildSoundNameMap(SOUND_NAME_FALLBACKS_772_TO_770);\n',
    "SOUND_NAME_MAPS に 770 を追加")

# --- J. adjacent_accessors() ---
rep(
    '           "    // 771→772。上の *_772 表を使う。ブロック状態・ブロック・",\n'
    '           "    // アイテム・エンティティ・音は 771 から 772 で dried_ghast・",\n'
    '           "    // ハーネス・ハッピーガスト関連の挿入でずれる。パーティクル・",\n'
    '           "    // メニュー・エフェクト・エンチャント・バイオームは同一。",\n'
    '           "    // 属性は camera_distance（8）の挿入と waypoint 系2件の追加で",\n'
    '           "    // 32→35 件になる。ブロックエンティティ種別は 771 と同じ 47 件。",',
    '           "    // 771→772。上の *_772 表を使う。アイテム・音は 771 から 772 で",\n'
    '           "    // music_disc_lava_chicken（アイテム 1256・音 939）の挿入で",\n'
    '           "    // ずれる。ブロック状態・ブロック・エンティティ・パーティクル・",\n'
    '           "    // メニュー・エフェクト・エンチャント・属性・バイオームは同一。",\n'
    '           "    // ブロックエンティティ種別は 771 と同じ 47 件。絵画だけが",\n'
    '           "    // 変わる（dennis が 14 に挿入され 51 件。恒等写像ではない）。",',
    "adjacent_accessors の頭コメント")
rep(
    '    out.append("     * 772にしか無いアイテム（乾燥ガスト・ハーネス16色・")\n'
    '    out.append("     * ハッピーガストのスポーンエッグ・レコード「涙」19件）は 771の石へ。")',
    '    out.append("     * 772にしか無いアイテム（レコード「ラヴァ・チキン」1件）は 771の石へ。")',
    "adjacent のアイテム逆変換コメント")
rep(
    '    out.append("     * {@code minecraft:} の有無はそのまま保つ。772で削除された")\n'
    '    out.append("     * entity.leash_knot.break／place・entity.puffer_fish.ambient・")\n'
    '    out.append("     * block.sand.wind は代替の名前にする。")',
    '    out.append("     * {@code minecraft:} の有無はそのまま保つ。771で削除された")\n'
    '    out.append("     * entity.leash_knot.break／place・entity.puffer_fish.ambient・")\n'
    '    out.append("     * block.sand.wind は代替の名前にする。772で削除された音は無い。")',
    "adjacent の音の名前コメント")
rep(
    '    out.append("    /** 771の属性ID → 772の属性ID。camera_distance（8）の挿入と waypoint 系2件の追加でずれる。範囲外は {@link #DROP}。 */")',
    '    out.append("    /** 771の属性ID → 772の属性ID。並びは同一。範囲外は {@link #DROP}。 */")',
    "adjacent の属性コメント（上向き）")
rep(
    '    out.append("    /** 772の属性ID → 771の属性ID。新3件（camera_distance・waypoint_transmit_range・waypoint_receive_range）は {@link #DROP}。範囲外も {@link #DROP}。 */")',
    '    out.append("    /** 772の属性ID → 771の属性ID。並びは同一。範囲外は {@link #DROP}。 */")',
    "adjacent の属性コメント（逆向き）")

# --- K. up_accessors() ---
rep(
    '            out.append("     * 772にしか無いアイテム（乾燥ガスト・ハーネス16色・")\n'
    '            out.append("     * ハッピーガストのスポーンエッグ・レコード「涙」19件）は石へ。範囲外は石（1）。")',
    '            out.append("     * 772にしか無いアイテム（レコード「ラヴァ・チキン」1件）は石へ。範囲外は石（1）。")',
    "up のアイテムコメント")
rep(
    '    out.append("     * 代替の音の名前にする（数値の反転表と一致させる）。772の新音")\n'
    '    out.append("     * （乾燥ガスト8件・ガストリング4件・ハッピーガスト8件・リード3件・")\n'
    '    out.append("     * 乾いた草・レコード「涙」・装備外し4件の29件）は石の破壊音へ。")',
    '    out.append("     * 代替の音の名前にする（数値の反転表と一致させる）。772の新音")\n'
    '    out.append("     * （レコード「ラヴァ・チキン」1件）は石の破壊音へ。")',
    "up の音の名前コメント")

# --- L. composed_accessors() ---
rep(
    '           "    // 離れた組み合わせの合成表（763↔772、764↔772、765↔772、766↔772、",\n'
    '           "    // 767↔772、768↔772、769↔772）。上の *_763_TO_772／*_772_TO_763／",\n'
    '           "    // *_764_TO_772／*_772_TO_764／*_765_TO_772／*_772_TO_765／",\n'
    '           "    // *_766_TO_772／*_772_TO_766／*_767_TO_772／*_772_TO_767／",\n'
    '           "    // *_768_TO_772／*_772_TO_768／*_769_TO_772／*_772_TO_769 表",\n'
    '           "    // （合成済みの1つの表）",',
    '           "    // 離れた組み合わせの合成表（763↔772、764↔772、765↔772、766↔772、",\n'
    '           "    // 767↔772、768↔772、769↔772、770↔772）。上の *_763_TO_772／",\n'
    '           "    // *_772_TO_763／*_764_TO_772／*_772_TO_764／*_765_TO_772／",\n'
    '           "    // *_772_TO_765／*_766_TO_772／*_772_TO_766／*_767_TO_772／",\n'
    '           "    // *_772_TO_767／*_768_TO_772／*_772_TO_768／*_769_TO_772／",\n'
    '           "    // *_772_TO_769／*_770_TO_772／*_772_TO_770 表",\n'
    '           "    // （合成済みの1つの表）",',
    "composed_accessors の頭コメント")
rep(
    '    pairs = [\n'
    '        ("763", 19, "763→772", "772→763"),\n'
    '        ("764", 20, "764→772", "772→764"),\n'
    '        ("765", 21, "765→772", "772→765"),\n'
    '        ("766", 22, "766→772", "772→766"),\n'
    '        ("767", 23, "767→772", "772→767"),\n'
    '        ("768", 24, "768→772", "772→768"),\n'
    '        ("769", 25, "769→772", "772→769"),\n'
    '    ]',
    '    pairs = [\n'
    '        ("763", 21, "763→772", "772→763"),\n'
    '        ("764", 22, "764→772", "772→764"),\n'
    '        ("765", 23, "765→772", "772→765"),\n'
    '        ("766", 24, "766→772", "772→766"),\n'
    '        ("767", 25, "767→772", "772→767"),\n'
    '        ("768", 26, "768→772", "772→768"),\n'
    '        ("769", 27, "769→772", "772→769"),\n'
    '        ("770", 28, "770→772", "772→770"),\n'
    '    ]',
    "composed_accessors のペア")

# 音の名前のコメント：「771で削除された entity.wolf.howl」は誤り
# （wolf.howl は 770 で削除）。「772で削除された leash_knot 系」も誤り
# （leash_knot 系は 771 で削除。772で削除された音は無い）。
# 763／764／766／767 は1行、765／768／769 は2行に分かれている。
rep_all(
    '771で削除された entity.wolf.howl',
    '770で削除された entity.wolf.howl',
    "音の名前コメント（wolf.howl 1行）",
    4)
rep_all(
    '771で削除された")\n            out.append("     * entity.wolf.howl',
    '770で削除された")\n            out.append("     * entity.wolf.howl',
    "音の名前コメント（wolf.howl 2行）",
    3)
rep_all(
    '772で削除された leash_knot 系',
    '771で削除された leash_knot 系',
    "音の名前コメント（leash_knot）",
    7)

# 音の名前の連鎖：771 の段階（leash_knot 系の削除）が shift で
# 772_MAP に置き換わって消えているため、770_MAP の段階を戻す。
rep_all(
    '            out.append("        return mapSoundNameComposed(")\n'
    '            out.append("                mapSoundNameComposed(")\n'
    '            out.append("                        mapSoundNameComposed(name, SOUND_NAME_FALLBACK_768_MAP),")\n'
    '            out.append("                        SOUND_NAME_FALLBACK_771_MAP),")\n'
    '            out.append("                SOUND_NAME_FALLBACK_772_MAP);")',
    '            out.append("        return mapSoundNameComposed(")\n'
    '            out.append("                mapSoundNameComposed(")\n'
    '            out.append("                        mapSoundNameComposed(")\n'
    '            out.append("                                mapSoundNameComposed(name, SOUND_NAME_FALLBACK_768_MAP),")\n'
    '            out.append("                                SOUND_NAME_FALLBACK_770_MAP),")\n'
    '            out.append("                        SOUND_NAME_FALLBACK_771_MAP),")\n'
    '            out.append("                SOUND_NAME_FALLBACK_772_MAP);")',
    "763／764 の音の名前の連鎖",
    2)
rep(
    '            out.append("        return mapSoundNameComposed(")\n'
    '            out.append("                mapSoundNameComposed(")\n'
    '            out.append("                        mapSoundNameComposed(")\n'
    '            out.append("                                mapSoundNameComposed(")\n'
    '            out.append("                                        mapSoundNameComposed(name, SOUND_NAME_FALLBACK_766_MAP),")\n'
    '            out.append("                                        SOUND_NAME_FALLBACK_767_MAP),")\n'
    '            out.append("                                SOUND_NAME_FALLBACK_768_MAP),")\n'
    '            out.append("                        SOUND_NAME_FALLBACK_771_MAP),")\n'
    '            out.append("                SOUND_NAME_FALLBACK_772_MAP);")',
    '            out.append("        return mapSoundNameComposed(")\n'
    '            out.append("                mapSoundNameComposed(")\n'
    '            out.append("                        mapSoundNameComposed(")\n'
    '            out.append("                                mapSoundNameComposed(")\n'
    '            out.append("                                        mapSoundNameComposed(")\n'
    '            out.append("                                                mapSoundNameComposed(name, SOUND_NAME_FALLBACK_766_MAP),")\n'
    '            out.append("                                                SOUND_NAME_FALLBACK_767_MAP),")\n'
    '            out.append("                                        SOUND_NAME_FALLBACK_768_MAP),")\n'
    '            out.append("                                SOUND_NAME_FALLBACK_770_MAP),")\n'
    '            out.append("                        SOUND_NAME_FALLBACK_771_MAP),")\n'
    '            out.append("                SOUND_NAME_FALLBACK_772_MAP);")',
    "765 の音の名前の連鎖")

# 769 のブロックの後ろに 770 のブロックを追加する。
BLOCK_770 = r'''        elif src == "770":
            out.append("    /**")
            out.append("     * 770のエフェクトID → 772のエフェクトID。770も772も0ベース")
            out.append("     * （Speed は両方 0）で 0〜38 は同一のため恒等写像。")
            out.append("     * 範囲外は Speed（0）。")
            out.append("     */")
            out.append("    public static int mapEffect770To772(int id770) {")
            out.append("        if (id770 < 0 || id770 > MAX_EFFECT_770) {")
            out.append("            logUnknownComposedOnce(\"770→772\", \"エフェクト\", id770);")
            out.append("            return SPEED_EFFECT_772;")
            out.append("        }")
            out.append("        return id770;")
            out.append("    }")
            out.append("")
            out.append("    /**")
            out.append("     * 772のエフェクトID → 770のエフェクトID。0〜38 は同一のため恒等写像。")
            out.append("     * 範囲外は Speed（0）。")
            out.append("     */")
            out.append("    public static int mapEffect772To770(int id772) {")
            out.append("        if (id772 < 0 || id772 > MAX_EFFECT_772) {")
            out.append("            logUnknownComposedOnce(\"772→770\", \"エフェクト\", id772);")
            out.append("            return SPEED_EFFECT_770;")
            out.append("        }")
            out.append("        return id772;")
            out.append("    }")
            out.append("")
            out.append("    /**")
            out.append("     * 770のブロックエンティティ種別ID → 772の種別ID。")
            out.append("     * 770も772も 0〜46 で並びが同一のため恒等写像。")
            out.append("     * 範囲外の種別は NBT の中身が種別専用のため安全に代替できず")
            out.append("     * {@link #DROP}。")
            out.append("     */")
            out.append("    public static int mapBlockEntityType770To772(int id770) {")
            out.append("        if (id770 < 0 || id770 > BLOCK_ENTITY_TEST_INSTANCE_BLOCK_770) {")
            out.append("            logUnknownComposedOnce(\"770→772\", \"ブロックエンティティ種別\", id770);")
            out.append("            return DROP;")
            out.append("        }")
            out.append("        return id770;")
            out.append("    }")
            out.append("")
            out.append("    /**")
            out.append("     * 772のブロックエンティティ種別ID → 770の種別ID。")
            out.append("     * 0〜46 は並びが同一のため恒等写像。")
            out.append("     */")
            out.append("    public static int mapBlockEntityType772To770(int id772) {")
            out.append("        if (id772 < 0 || id772 > BLOCK_ENTITY_TEST_INSTANCE_BLOCK_772) {")
            out.append("            logUnknownComposedOnce(\"772→770\", \"ブロックエンティティ種別\", id772);")
            out.append("            return DROP;")
            out.append("        }")
            out.append("        return id772;")
            out.append("    }")
            out.append("")
            out.append("    /**")
            out.append("     * 音の名前を772向けに変換する。770の音の名前のうち 771で削除された")
            out.append("     * entity.leash_knot.break／place・entity.puffer_fish.ambient・")
            out.append("     * block.sand.wind は代替の名前にする。")
            out.append("     * {@code null} は石の破壊音にする。")
            out.append("     */")
            out.append("    public static String mapSoundName770To772(String name) {")
            out.append("        return mapSoundNameComposed(name, SOUND_NAME_FALLBACK_770_TO_772_MAP);")
            out.append("    }")
            out.append("")
            out.append("    /**")
            out.append("     * 音の名前を770向けに変換する。772の音の名前のうち 770に無いものは")
            out.append("     * 代替の音の名前にする（数値の合成表と一致させる）。")
            out.append("     * {@code null} は石の破壊音にする。")
            out.append("     */")
            out.append("    public static String mapSoundName772To770(String name) {")
            out.append("        return mapSoundNameComposed(name, SOUND_NAME_FALLBACK_772_TO_770_MAP);")
            out.append("    }")
'''
rep(
    '            out.append("    public static String mapSoundName772To769(String name) {")\n'
    '            out.append("        return mapSoundNameComposed(name, SOUND_NAME_FALLBACK_772_TO_769_MAP);")\n'
    '            out.append("    }")\n'
    '    return "\\n".join(out)',
    '            out.append("    public static String mapSoundName772To769(String name) {")\n'
    '            out.append("        return mapSoundNameComposed(name, SOUND_NAME_FALLBACK_772_TO_769_MAP);")\n'
    '            out.append("    }")\n'
    + BLOCK_770 +
    '    return "\\n".join(out)',
    "770 のブロックを追加")

# --- M. SMALL_REGISTRY_CONSTANTS：絵画は 51 件 ---
rep(
    '    /** 772の絵画バリアント最大ID（50件。771と同一と仮定。実バニラの\n'
    '        Registry Data での確認は統合テストで行う）。 */\n'
    '    public static final int MAX_PAINTING_VARIANT_772 = 49;',
    '    /** 772の絵画バリアント最大ID（51件。dennis を 14 に追加。実バニラの\n'
    '        Registry Data での確認は統合テストで行う）。 */\n'
    '    public static final int MAX_PAINTING_VARIANT_772 = 50;',
    "絵画の最大ID")

# small_registry_accessors()：絵画だけ隣接変換が恒等写像ではない
# （dennis が 14 に挿入）。バナー・装飾パターンは恒等写像のまま。
PAINTING_OLD = r'''    for name, jp, max771, max772 in specs:
        out.append(f"    /** 771の{jp}ID → 772の{jp}ID。771と772で同一のため恒等写像。範囲外は {{@link #DROP}}。 */")
        out.append(f"    public static int map{name}771To772(int id771) {{")
        out.append(f"        if (id771 < 0 || id771 > {max771}) {{")
        out.append(f"            logUnknownOnce(PROTOCOL_772, \"{jp}\", id771);")
        out.append("            return DROP;")
        out.append("        }")
        out.append("        return id771;")
        out.append("    }")
        out.append("")
        out.append(f"    /** 772の{jp}ID → 771の{jp}ID。771と772で同一のため恒等写像。範囲外は {{@link #DROP}}。 */")
        out.append(f"    public static int map{name}772To771(int id772) {{")
        out.append(f"        if (id772 < 0 || id772 > {max772}) {{")
        out.append(f"            logUnknownOnce(PROTOCOL_772, \"{jp}\", id772);")
        out.append("            return DROP;")
        out.append("        }")
        out.append("        return id772;")
        out.append("    }")
        out.append("")'''
PAINTING_NEW = r'''    for name, jp, max771, max772 in specs:
        if name == "PaintingVariant":
            # 絵画だけが変わる：dennis が 14 に挿入され 51 件。14 以降は +1、
            # 逆向きは dennis（14）を DROP、14 より後は -1。
            out.append("    /** 771の絵画バリアントID → 772の絵画バリアントID。dennis（14）の挿入で 14 以降は +1。範囲外は {@link #DROP}。 */")
            out.append("    public static int mapPaintingVariant771To772(int id771) {")
            out.append("        if (id771 < 0 || id771 > MAX_PAINTING_VARIANT_771) {")
            out.append("            logUnknownOnce(PROTOCOL_772, \"絵画バリアント\", id771);")
            out.append("            return DROP;")
            out.append("        }")
            out.append("        return id771 < 14 ? id771 : id771 + 1;")
            out.append("    }")
            out.append("")
            out.append("    /** 772の絵画バリアントID → 771の絵画バリアントID。772にしかない dennis（14）は {@link #DROP}。14 より後は -1。範囲外も {@link #DROP}。 */")
            out.append("    public static int mapPaintingVariant772To771(int id772) {")
            out.append("        if (id772 < 0 || id772 > MAX_PAINTING_VARIANT_772) {")
            out.append("            logUnknownOnce(PROTOCOL_772, \"絵画バリアント\", id772);")
            out.append("            return DROP;")
            out.append("        }")
            out.append("        if (id772 == 14) {")
            out.append("            return DROP;")
            out.append("        }")
            out.append("        return id772 < 14 ? id772 : id772 - 1;")
            out.append("    }")
            out.append("")
            continue
        out.append(f"    /** 771の{jp}ID → 772の{jp}ID。771と772で同一のため恒等写像。範囲外は {{@link #DROP}}。 */")
        out.append(f"    public static int map{name}771To772(int id771) {{")
        out.append(f"        if (id771 < 0 || id771 > {max771}) {{")
        out.append(f"            logUnknownOnce(PROTOCOL_772, \"{jp}\", id771);")
        out.append("            return DROP;")
        out.append("        }")
        out.append("        return id771;")
        out.append("    }")
        out.append("")
        out.append(f"    /** 772の{jp}ID → 771の{jp}ID。771と772で同一のため恒等写像。範囲外は {{@link #DROP}}。 */")
        out.append(f"    public static int map{name}772To771(int id772) {{")
        out.append(f"        if (id772 < 0 || id772 > {max772}) {{")
        out.append(f"            logUnknownOnce(PROTOCOL_772, \"{jp}\", id772);")
        out.append("            return DROP;")
        out.append("        }")
        out.append("        return id772;")
        out.append("    }")
        out.append("")'''
rep(PAINTING_OLD, PAINTING_NEW, "絵画の隣接変換")

# --- M2. small_registry_accessors()：770 まで（771 は既に直上のメソッド） ---
rep(
    '        for src in ("763", "764", "765", "766", "767", "768", "769"):\n'
    '            # 771 のメソッドを経由する（763／764／765／766／767／768／770→771 と\n'
    '            # 771→763／764／765／766／767／768／769 は既にある）。',
    '        for src in ("763", "764", "765", "766", "767", "768", "769", "770"):\n'
    '            # 771 のメソッドを経由する（763／764／765／766／767／768／769／770→771 と\n'
    '            # 771→763／764／765／766／767／768／769／770 は既にある）。',
    "small_registry のループ")

# --- N. damage_accessors()：770 まで ---
rep(
    '    for src in ("763", "764", "765", "766", "767", "768", "769"):\n'
    '        # ダメージ種別は 771 のメソッドを経由する。',
    '    for src in ("763", "764", "765", "766", "767", "768", "769", "770"):\n'
    '        # ダメージ種別は 771 のメソッドを経由する。',
    "damage のループ")

# --- O. JAVADOC_772 の書き直し ---
rep(
    ' * <p>772（1.21.7）向けの表も同じ作り方で持つ（{@code *_772}）。\n'
    ' * 772は771に近く、差分は次の通り（ViaVersion mapping-1.21.7.json と\n'
    ' * mapping-1.21.6.json、minecraft-data 1.21.7 の突き合わせ）。\n'
    ' * ブロック状態は新32件（dried_ghast：向き4×hydration 4×水没2）・\n'
    ' * 削除0件。ブロックは新1件（dried_ghast）。アイテムは新19件\n'
    ' * （dried_ghast・ハーネス16色・happy_ghast_spawn_egg・\n'
    ' * music_disc_tears）。エンティティは新1件（happy_ghast）。\n'
    ' * 音は新29件・削除4件（entity.leash_knot.break／place・\n'
    ' * entity.puffer_fish.ambient・block.sand.wind）。パーティクル・\n'
    ' * メニュー・バイオーム・エフェクト・エンチャント・データコンポーネントは\n'
    ' * 同一。属性は新3件（camera_distance が 8 に挿入、\n'
    ' * waypoint_transmit_range・waypoint_receive_range を末尾に追加）。\n'
    ' * ブロックエンティティ種別は 771 と同じ 47 件。ダメージ種別・\n'
    ' * バナーパターン・絵画バリアント・装飾パターンは 771 と同一（実バニラの\n'
    ' * Registry Data での確認は統合テストで行う）。\n'
    ' * 771→772 の隣接表と 763／764／765／766／767／768／769→772 の合成表を持ち、\n'
    ' * 逆向き 772→771 は反転して作る。',
    ' * <p>772（1.21.7／1.21.8）向けの表も同じ作り方で持つ（{@code *_772}）。\n'
    ' * 772は771に近く、差分は次の通り（ViaVersion mapping-1.21.7.json と\n'
    ' * mapping-1.21.6.json、minecraft-data 1.21.8 の突き合わせ）。\n'
    ' * ブロック状態・ブロック・エンティティ・パーティクル・メニュー・\n'
    ' * バイオーム・エフェクト・エンチャント・属性・データコンポーネントは\n'
    ' * 同一。アイテムは新1件（music_disc_lava_chicken が 1256 に挿入）。\n'
    ' * 音は新1件（music_disc.lava_chicken が 939 に挿入）。\n'
    ' * 絵画バリアントは新1件（dennis が 14 に挿入され 51 件。実バニラの\n'
    ' * Registry Data での確認は統合テストで行う）。ジュークボックスの歌も\n'
    ' * 新1件（lava_chicken が 9 に挿入され 21 件。同上）。\n'
    ' * ブロックエンティティ種別は 771 と同じ 47 件。ダメージ種別・\n'
    ' * バナーパターン・装飾パターンは 771 と同一（実バニラの Registry Data\n'
    ' * での確認は統合テストで行う）。\n'
    ' * 771→772 の隣接表と 763／764／765／766／767／768／769／770→772 の合成表を持ち、\n'
    ' * 逆向き 772→771 は反転して作る。',
    "JAVADOC_772")

# --- P. main() のアンカー ---
rep(
    '                        "import static io.detergentmc.protocol.RegistryTables771Down768.*;\\n",',
    '                        "import static io.detergentmc.protocol.RegistryTables771Down769.*;\\n",',
    "import のアンカー")
rep(
    '                        "import static io.detergentmc.protocol.RegistryTables772Down768.*;\\n"\n'
    '                        "import static io.detergentmc.protocol.RegistryTables772Down769.*;\\n",',
    '                        "import static io.detergentmc.protocol.RegistryTables772Down768.*;\\n"\n'
    '                        "import static io.detergentmc.protocol.RegistryTables772Down769.*;\\n"\n'
    '                        "import static io.detergentmc.protocol.RegistryTables772Down770.*;\\n",',
    "import に Down770 を追加")
rep(
    '    anchor = (" * 770→771 の隣接表と 763／764／765／766／767／768→771 の合成表を持ち、\\n"\n'
    '              " * 逆向き 771→770 は反転して作る。\\n")',
    '    anchor = (" * 770→771 の隣接表と 763／764／765／766／767／768／769→771 の合成表を持ち、\\n"\n'
    '              " * 逆向き 771→770 は反転して作る。\\n")',
    "javadoc のアンカー")
rep(
    '    anchor = ("    private static final int[] MENU_771_MAP =\\n"\n'
    '              "            expand(MENU_771_RANGES, MENU_771_OVERRIDES, MAX_MENU_769 + 1,\\n"\n'
    '              "                    CRAFTING_MENU_771);\\n")',
    '    anchor = ("    private static final int[] MENU_771_MAP =\\n"\n'
    '              "            expand(MENU_771_RANGES, MENU_771_OVERRIDES, MAX_MENU_770 + 1,\\n"\n'
    '              "                    CRAFTING_MENU_771);\\n")',
    "隣接表のアンカー")
rep(
    '    # 合成表。MENU_771_TO_768_MAP の直後。\n'
    '    anchor = ("    private static final int[] MENU_771_TO_768_MAP =\\n"\n'
    '              "            expand(MENU_771_TO_768_RANGES, MENU_771_TO_768_OVERRIDES,\\n"\n'
    '              "                    25, CRAFTING_MENU_768);\\n")',
    '    # 合成表。MENU_771_TO_769_MAP の直後。\n'
    '    anchor = ("    private static final int[] MENU_771_TO_769_MAP =\\n"\n'
    '              "            expand(MENU_771_TO_769_RANGES, MENU_771_TO_769_OVERRIDES,\\n"\n'
    '              "                    25, CRAFTING_MENU_769);\\n")',
    "合成表のアンカー")
rep(
    '    anchor = ("    private static final int[] MENU_UP_771_MAP = invert(\\n"\n'
    '              "            MENU_771_RANGES, MENU_771_OVERRIDES,\\n"\n'
    '              "            MAX_MENU_769 + 1, MAX_MENU_771 + 1,\\n"\n'
    '              "            CRAFTING_MENU_769, null);\\n")',
    '    anchor = ("    private static final int[] MENU_UP_771_MAP = invert(\\n"\n'
    '              "            MENU_771_RANGES, MENU_771_OVERRIDES,\\n"\n'
    '              "            MAX_MENU_770 + 1, MAX_MENU_771 + 1,\\n"\n'
    '              "            CRAFTING_MENU_770, null);\\n")',
    "反転表のアンカー")
rep(
    '    anchor = ("    public static String mapSoundNameUp771(String name) {\\n"\n'
    '              "        return mapSoundNameComposed(name, SOUND_NAME_FALLBACK_771_TO_769_MAP);\\n"\n'
    '              "    }\\n")',
    '    anchor = ("    public static String mapSoundNameUp771(String name) {\\n"\n'
    '              "        return mapSoundNameComposed(name, SOUND_NAME_FALLBACK_771_TO_770_MAP);\\n"\n'
    '              "    }\\n")',
    "アクセサ（b）のアンカー（Up771 は 771→770）")
rep(
    '    # アクセサ（c）763／764／765／766／767／768／769↔772。mapSoundName771To766 の直後。',
    '    # アクセサ（c）763／764／765／766／767／768／769／770↔772。mapSoundName771To766 の直後。',
    "アクセサ（c）のコメント")
rep(
    '                        "    /** 771のバナーパターン最大ID（43件。769と同一と仮定。実バニラの\\n"\n'
    '                        "        Registry Data での確認は統合テストで行う）。 */\\n"\n'
    '                        "    public static final int MAX_BANNER_PATTERN_771 = 42;\\n",',
    '                        "    /** 770のバナーパターン最大ID（43件。769と同一と仮定。実バニラの\\n"\n'
    '                        "        Registry Data での確認は統合テストで行う）。 */\\n"\n'
    '                        "    public static final int MAX_BANNER_PATTERN_770 = 42;\\n",',
    "バナー定数のアンカー")
rep(
    '    anchor = ("    /** 771のバナーパターンID → 766のバナーパターンID（769経由）。 */\\n"\n'
    '              "    public static int mapBannerPattern771To766(int id771) {\\n"\n'
    '              "        return mapBannerPattern769To766(mapBannerPattern771To769(id771));\\n"',
    '    anchor = ("    /** 770のバナーパターンID → 766のバナーパターンID（769経由）。 */\\n"\n'
    '              "    public static int mapBannerPattern770To766(int id770) {\\n"\n'
    '              "        return mapBannerPattern769To766(mapBannerPattern770To769(id770));\\n"',
    "バナーアクセサのアンカー")
rep(
    '    anchor = ("    /** 771のダメージ種別ID → 766のダメージ種別ID（769経由）。 */\\n"\n'
    '              "    public static int mapDamageType771To766(int id771) {\\n"\n'
    '              "        return mapDamageType769To766(mapDamageType771To769(id771));\\n"',
    '    anchor = ("    /** 770のダメージ種別ID → 766のダメージ種別ID（769経由）。 */\\n"\n'
    '              "    public static int mapDamageType770To766(int id770) {\\n"\n'
    '              "        return mapDamageType769To766(mapDamageType770To769(id770));\\n"',
    "ダメージアクセサのアンカー")
rep(
    '        "    // 769は769の表、771は771の表、それより古い未知の番号は763の表を使う。\\n"',
    '        "    // 769は769の表、770は770の表、771は771の表、それより古い未知の番号は763の表を使う。\\n"',
    "ディスパッチコメントの旧文")
rep(
    '        "    // 769は769の表、771は771の表、772は772の表、それより古い未知の番号は763の表を使う。\\n"',
    '        "    // 769は769の表、770は770の表、771は771の表、772は772の表、それより古い未知の番号は763の表を使う。\\n"',
    "ディスパッチコメントの新文")

if failed:
    print("失敗:", file=sys.stderr)
    for f in failed:
        print(" -", f, file=sys.stderr)
    sys.exit(1)
P.write_text(s, encoding="utf-8")
print("fix-772-java.py: 全ての修正を適用した")
