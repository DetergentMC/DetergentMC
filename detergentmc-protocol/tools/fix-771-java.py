#!/usr/bin/env python3
"""insert-771-java.py の手直し（shift でずれた箇所の修正＋769対応の追加）。

gen-771-java.py を再実行したらこのスクリプトも再実行すること。
各置換は出現回数がちょうど1件のときだけ適用し、違えば失敗を報告する。
"""
import sys
from pathlib import Path

P = Path(__file__).resolve().parent / "insert-771-java.py"
s = P.read_text(encoding="utf-8")
failed = []


def rep(old, new, label):
    global s
    n = s.count(old)
    if n != 1:
        failed.append(f"{label}: 一致 {n} 件 (期待 1 件)")
        return
    s = s.replace(old, new)


# --- A. docstring ---
rep(
    "  - 合成表（763↔771、764↔771、765↔771、766↔771、767↔771、768↔771）: *_763_TO_771_MAP など84件。",
    "  - 合成表（763↔771、764↔771、765↔771、766↔771、767↔771、768↔771、769↔771）: *_763_TO_771_MAP など98件。\n  - 770↔771 は隣接表（*_771_MAP）と反転表（*_UP_771_MAP）で扱うため合成表は作らない（770と同じ方針）。",
    "docstring 合成表")
rep(
    "  - 逆向き（771→770）: *_UP_771_MAP（invert()。アイテムは\n"
    "    ITEM_REVERSE_771_MAP をそのまま使う。ブロック状態とエンティティに補正あり：\n"
    "    creaking_heart の uprooted 6状態と lingering_potion→potion）。",
    "  - 逆向き（771→770）: *_UP_771_MAP（invert()。アイテムは\n"
    "    ITEM_REVERSE_771_MAP をそのまま使う。770→771 に補正は無い）。",
    "docstring 逆向き")
rep(
    "  - 音の名前の表（String）: SOUND_NAME_FALLBACK_*_771_MAP など12件。",
    "  - 音の名前の表（String）: SOUND_NAME_FALLBACK_*_771_MAP など14件。",
    "docstring 音の表")
rep(
    "  - アクセサ：770↔771、771→770（上向き）、763／764／765／766／767／768↔771。",
    "  - アクセサ：770↔771、771→770（上向き）、763／764／765／766／767／768／769↔771。",
    "docstring アクセサ")
rep(
    "    属性は 769・770・771 とも 32 件で同一、エンチャントは 42 件同士で",
    "    属性は 770 の 32 件から 771 の 35 件へ（camera_distance が 8 に挿入、\n"
    "    waypoint_transmit_range・waypoint_receive_range を末尾に追加。逆向きは\n"
    "    新3件を DROP）、エンチャントは 42 件同士で",
    "docstring 属性")
rep(
    "    ブロックエンティティ種別は 770 の 45 件に test_block=45 と\n"
    "    test_instance_block=46 を追加した 47 件。",
    "    ブロックエンティティ種別は 770 と同じ 47 件（creaking_heart=10、\n"
    "    trial_spawner=43、vault=44、test_block=45、test_instance_block=46）。",
    "docstring ブロックエンティティ")

# --- B. 合成表の下向きファイルの接尾辞 ---
rep(
    '    for suffix in ("763", "764", "765", "766", "767")',
    '    for suffix in ("763", "764", "765", "766", "767", "768", "769")',
    "Down ファイル接尾辞")

# --- C. CONSTANTS_MAX（771 の実数値） ---
rep(
    '    public static final int MAX_BLOCK_STATE_771 = 27913;\n'
    '    public static final int MAX_ITEM_771 = 1395;\n'
    '    public static final int MAX_ENTITY_TYPE_771 = 149;\n'
    '    public static final int MAX_SOUND_771 = 1701;\n'
    '    public static final int MAX_PARTICLE_771 = 113;\n'
    '    public static final int MAX_BLOCK_771 = 1103;',
    '    public static final int MAX_BLOCK_STATE_771 = 27945;\n'
    '    public static final int MAX_ITEM_771 = 1414;\n'
    '    public static final int MAX_ENTITY_TYPE_771 = 150;\n'
    '    public static final int MAX_SOUND_771 = 1726;\n'
    '    public static final int MAX_PARTICLE_771 = 113;\n'
    '    public static final int MAX_BLOCK_771 = 1104;',
    "MAX 値")
rep(
    '    /** 771側の属性は 0〜31（770と同一の 32 件。名前も並びも同一）。 */\n'
    '    public static final int MAX_ATTRIBUTE_771 = 31;',
    '    /** 771側の属性は 0〜34（770 の 32 件に camera_distance（8）・\n'
    '        waypoint_transmit_range（33）・waypoint_receive_range（34）を\n'
    '        追加した 35 件。ViaVersion mapping-1.21.6.json で確認）。 */\n'
    '    public static final int MAX_ATTRIBUTE_771 = 34;',
    "MAX_ATTRIBUTE 値")

# --- D. CONSTANTS_FALLBACK（771 の実数値） ---
rep(
    '     * ブタは 771 では 94（potion の分割はブタより後ろで起きたため 770 と\n'
    '     * 同じ）、石の破壊音は 771 では 1417（新音 52 件の挿入と\n'
    '     * entity.wolf.howl の削除で 770 の 1401 からずれた）。 */\n'
    '    public static final int STONE_BLOCK_STATE_771 = 1;\n'
    '    public static final int AIR_BLOCK_STATE_771 = 0;\n'
    '    public static final int STONE_ITEM_771 = 1;\n'
    '    public static final int PIG_ENTITY_TYPE_771 = 94;\n'
    '    public static final int GENERIC_SOUND_771 = 1417; // block.stone.break',
    '     * ブタは 771 では 95（happy_ghast の挿入で 770 の 94 から +1）、\n'
    '     * 石の破壊音は 771 では 1441（新音 29 件の挿入と entity.leash_knot\n'
    '     * 2件・entity.puffer_fish.ambient・block.sand.wind の削除で\n'
    '     * 770 の 1417 からずれた）。 */\n'
    '    public static final int STONE_BLOCK_STATE_771 = 1;\n'
    '    public static final int AIR_BLOCK_STATE_771 = 0;\n'
    '    public static final int STONE_ITEM_771 = 1;\n'
    '    public static final int PIG_ENTITY_TYPE_771 = 95;\n'
    '    public static final int GENERIC_SOUND_771 = 1441; // block.stone.break',
    "代替先の値")

# --- E. CONSTANTS_BLOCK_ENTITY ---
rep(
    '    /** 771のブロックエンティティ種別：770 の 45 件に test_block=45 と\n'
    '        test_instance_block=46 を追加した 47 件（creaking_heart=10、\n'
    '        trial_spawner=43、vault=44）。 */',
    '    /** 771のブロックエンティティ種別：770 と同じ 47 件\n'
    '        （creaking_heart=10、trial_spawner=43、vault=44、test_block=45、\n'
    '        test_instance_block=46）。 */',
    "ブロックエンティティ定数のコメント")

# --- F. adjacent_maps()：代替先の index と ITEM_REVERSE の件数 ---
rep(
    '            f"                    {kind[21]});")',
    '            f"                    {kind[27]});")',
    "adjacent_maps 代替先 index")
rep(
    '        "            expand(ITEM_REVERSE_771_RANGES, ITEM_REVERSE_771_OVERRIDES, 1396, -1);")',
    '        "            expand(ITEM_REVERSE_771_RANGES, ITEM_REVERSE_771_OVERRIDES, 1415, -1);")',
    "ITEM_REVERSE の件数")

# --- G. composed_maps()：769 までの 14 ペア（770 は隣接表で扱う） ---
rep(
    '    # タプルから取る（763_TO_771: 件数 index 3, 代替先 771=index 21）。\n'
    '    pairs = [\n'
    '        ("763_TO_771", 3, 21), ("771_TO_763", 4, 15),\n'
    '        ("764_TO_771", 5, 21), ("771_TO_764", 6, 16),\n'
    '        ("765_TO_771", 7, 21), ("771_TO_765", 8, 17),\n'
    '        ("766_TO_771", 9, 21), ("771_TO_766", 10, 18),\n'
    '        ("767_TO_771", 11, 21), ("771_TO_767", 12, 19),\n'
    '        ("768_TO_771", 13, 21), ("771_TO_768", 14, 20),\n'
    '    ]',
    '    # タプルから取る（763_TO_771: 件数 index 3, 代替先 771=index 27／\n'
    '    # 771_TO_763: 件数 index 4, 代替先 763=index 19）。件数は 3〜18、\n'
    '    # 代替先は 19（763）〜27（771）。770↔771 は隣接表で扱うため含めない。\n'
    '    pairs = [\n'
    '        ("763_TO_771", 3, 27), ("771_TO_763", 4, 19),\n'
    '        ("764_TO_771", 5, 27), ("771_TO_764", 6, 20),\n'
    '        ("765_TO_771", 7, 27), ("771_TO_765", 8, 21),\n'
    '        ("766_TO_771", 9, 27), ("771_TO_766", 10, 22),\n'
    '        ("767_TO_771", 11, 27), ("771_TO_767", 12, 23),\n'
    '        ("768_TO_771", 13, 27), ("771_TO_768", 14, 24),\n'
    '        ("769_TO_771", 15, 27), ("771_TO_769", 16, 25),\n'
    '    ]',
    "composed_maps のペア")

# --- H. up_maps()：補正なし、代替先は kind[26]（770 側） ---
rep(
    '        # ブロック状態とエンティティに補正あり：771 の creaking_heart\n'
    '        # のうち creaking_heart_state=uprooted の 6 状態は 770 の\n'
    '        # active=false の心臓へ（BLOCK_STATE_UP_771_CORRECTIONS）。\n'
    '        # 771 の lingering_potion（100）は 770 の potion（99）へ\n'
    '        # （ENTITY_UP_771_CORRECTIONS。splash_potion は invert の先行勝ちで\n'
    '        # 既に potion になるため補正から外す）。\n'
    '        corrections = ("BLOCK_STATE_UP_771_CORRECTIONS"\n'
    '                       if prefix == "BLOCK_STATE"\n'
    '                       else "ENTITY_UP_771_CORRECTIONS"\n'
    '                       if prefix == "ENTITY_TYPE" else "null")\n'
    '        # 逆向きの代替先は「770 側」の定数。kind[19] は 767 側のためそのまま\n'
    '        # 使うと範囲外の ID が 767 の代替先（770 では別な音・別な種類に\n'
    '        # なっている）になってしまう。_767 → _770 に直して使う。\n'
    '        # （up_accessors() の fb770 と同じ理由。）\n'
    '        fb770 = kind[19].replace("_767", "_770")',
    '        # 770→771 に補正は無い（creaking_heart のプロパティ変更と\n'
    '        # potion の分割は 770 で済んでいる）。反転はそのまま使う。\n'
    '        corrections = "null"\n'
    '        # 逆向きの代替先は「770 側」の定数（kind[26]）。\n'
    '        fb770 = kind[26]',
    "up_maps の補正と代替先")

# --- I. SOUND_NAME_MAPS：769 の 2 件を追加 ---
rep(
    '    private static final Map<String, String> SOUND_NAME_FALLBACK_771_TO_770_MAP =\n'
    '            buildSoundNameMap(SOUND_NAME_FALLBACKS_771_TO_770);\n',
    '    private static final Map<String, String> SOUND_NAME_FALLBACK_771_TO_770_MAP =\n'
    '            buildSoundNameMap(SOUND_NAME_FALLBACKS_771_TO_770);\n'
    '    private static final Map<String, String> SOUND_NAME_FALLBACK_769_TO_771_MAP =\n'
    '            buildSoundNameMap(SOUND_NAME_FALLBACKS_769_TO_771);\n'
    '    private static final Map<String, String> SOUND_NAME_FALLBACK_771_TO_769_MAP =\n'
    '            buildSoundNameMap(SOUND_NAME_FALLBACKS_771_TO_769);\n',
    "SOUND_NAME_MAPS に 769 を追加")

# --- J. adjacent_accessors() ---
rep(
    '           "    // 770→771。上の *_771 表を使う。ブロック状態・ブロック・",\n'
    '           "    // アイテム・エンティティ・音・パーティクルは 770 から 771 で",\n'
    '           "    // 茂み・ホタルブッシュ・枯れ草・ワイルドフラワー・リーフリッター・",\n'
    '           "    // サボテンの花・テストブロック類・青い卵・茶色い卵が挿入でずれる。",\n'
    '           "    // creaking_heart のプロパティが変わり、potion が splash／",\n'
    '           "    // lingering に分かれ、entity.wolf.howl が削除された。",\n'
    '           "    // メニュー・エフェクト・バナー・絵画・装飾パターン・ダメージ種別は",\n'
    '           "    // 同一、エンチャントも属性も同一の並び。ブロックエンティティ種別は",\n'
    '           "    // 45→47 件（test_block・test_instance_block を追加）。",',
    '           "    // 770→771。上の *_771 表を使う。ブロック状態・ブロック・",\n'
    '           "    // アイテム・エンティティ・音は 770 から 771 で dried_ghast・",\n'
    '           "    // ハーネス・ハッピーガスト関連の挿入でずれる。パーティクル・",\n'
    '           "    // メニュー・エフェクト・エンチャント・バイオームは同一。",\n'
    '           "    // 属性は camera_distance（8）の挿入と waypoint 系2件の追加で",\n'
    '           "    // 32→35 件になる。ブロックエンティティ種別は 770 と同じ 47 件。",',
    "adjacent_accessors の頭コメント")
rep(
    "        fb771 = kind[21]",
    "        fb771 = kind[27]",
    "adjacent_accessors の代替先 index")
rep(
    '    out.append("     * 771にしか無いアイテム（茂み・ホタルブッシュ・枯れ草・ワイルドフラワー・")\n'
    '    out.append("     * リーフリッター・サボテンの花・テストブロック類・青い卵・茶色い卵11件）は 770の石へ。")',
    '    out.append("     * 771にしか無いアイテム（乾燥ガスト・ハーネス16色・")\n'
    '    out.append("     * ハッピーガストのスポーンエッグ・レコード「涙」19件）は 770の石へ。")',
    "adjacent のアイテム逆変換コメント")
rep(
    '    out.append("     * 770のブロックエンティティ種別ID → 771の種別ID。")\n'
    '    out.append("     * 770は45件、771は47件。0〜44 は並びが同一のため恒等写像。")',
    '    out.append("     * 770のブロックエンティティ種別ID → 771の種別ID。")\n'
    '    out.append("     * 770は47件、771は47件。並びが同一のため恒等写像。")',
    "adjacent のブロックエンティティコメント")
rep(
    '    out.append("    public static int mapBlockEntityType770To771(int id770) {")\n'
    '    out.append("        if (id770 < 0 || id770 > BLOCK_ENTITY_VAULT_770) {")',
    '    out.append("    public static int mapBlockEntityType770To771(int id770) {")\n'
    '    out.append("        if (id770 < 0 || id770 > BLOCK_ENTITY_TEST_INSTANCE_BLOCK_770) {")',
    "adjacent のブロックエンティティ判定")
rep(
    '    out.append("     * {@code minecraft:} の有無はそのまま保つ。771で削除された")\n'
    '    out.append("     * entity.wolf.howl は entity.wolf.ambient にする。")',
    '    out.append("     * {@code minecraft:} の有無はそのまま保つ。771で削除された")\n'
    '    out.append("     * entity.leash_knot.break／place・entity.puffer_fish.ambient・")\n'
    '    out.append("     * block.sand.wind は代替の名前にする。")',
    "adjacent の音の名前コメント")
rep(
    '    out.append("    /** 770の属性ID → 771の属性ID。並びは同一。範囲外は {@link #DROP}。 */")',
    '    out.append("    /** 770の属性ID → 771の属性ID。camera_distance（8）の挿入と waypoint 系2件の追加でずれる。範囲外は {@link #DROP}。 */")',
    "adjacent の属性コメント（上向き）")
rep(
    '    out.append("    /** 771の属性ID → 770の属性ID。並びは同一。範囲外は {@link #DROP}。 */")',
    '    out.append("    /** 771の属性ID → 770の属性ID。新3件（camera_distance・waypoint_transmit_range・waypoint_receive_range）は {@link #DROP}。範囲外も {@link #DROP}。 */")',
    "adjacent の属性コメント（逆向き）")

# --- K. up_accessors() ---
rep(
    '        # 逆向きの代替先は「770 側」の定数。kind[19] は 767 側のため\n'
    '        # そのまま使うと範囲外の ID が 767 の代替先（770 では別な音・別な\n'
    '        # 種類になっている）になってしまう。_767 → _770 に直して使う。\n'
    '        fb770 = kind[19].replace("_767", "_770")',
    '        # 逆向きの代替先は「770 側」の定数（kind[26]）。\n'
    '        fb770 = kind[26]',
    "up_accessors の代替先")
rep(
            '            out.append("     * 771にしか無いアイテム（茂み・ホタルブッシュ・枯れ草・ワイルドフラワー・")\n'
            '            out.append("     * リーフリッター・サボテンの花・テストブロック類・青い卵・茶色い卵11件）は石へ。範囲外は石（1）。")',
            '            out.append("     * 771にしか無いアイテム（乾燥ガスト・ハーネス16色・")\n'
            '            out.append("     * ハッピーガストのスポーンエッグ・レコード「涙」19件）は石へ。範囲外は石（1）。")',
    "up のアイテムコメント")
rep(
    '    out.append("     * 771のブロックエンティティ種別ID → 770の種別ID。")\n'
    '    out.append("     * 770は45件、771は47件。0〜44 は並びが同一のため恒等写像。")\n'
    '    out.append("     * 771で増えた test_block（45）・test_instance_block（46）は")\n'
    '    out.append("     * 770に無いため {@link #DROP}。")',
    '    out.append("     * 771のブロックエンティティ種別ID → 770の種別ID。")\n'
    '    out.append("     * 770は47件、771は47件。並びが同一のため恒等写像。")',
    "up のブロックエンティティコメント")
rep(
    '    out.append("    public static int mapBlockEntityTypeUp771(int id771) {")\n'
    '    out.append("        if (id771 < 0 || id771 > BLOCK_ENTITY_VAULT_771) {")',
    '    out.append("    public static int mapBlockEntityTypeUp771(int id771) {")\n'
    '    out.append("        if (id771 < 0 || id771 > BLOCK_ENTITY_TEST_INSTANCE_BLOCK_771) {")',
    "up のブロックエンティティ判定")
rep(
    '    out.append("     * 代替の音の名前にする（数値の反転表と一致させる）。771の新音")\n'
    '    out.append("     * （オオカミの変種の音36件・鉄の音5件・リーフリッター5件・サボテンの花2件・")\n'
    '    out.append("     * 砂の音2件・デッドブッシュ／ホタルブッシュの音2件の52件）は石の破壊音へ。")',
    '    out.append("     * 代替の音の名前にする（数値の反転表と一致させる）。771の新音")\n'
    '    out.append("     * （乾燥ガスト8件・ガストリング4件・ハッピーガスト8件・リード3件・")\n'
    '    out.append("     * 乾いた草・レコード「涙」・装備外し4件の29件）は石の破壊音へ。")',
    "up の音の名前コメント")

# --- L. composed_accessors() ---
rep(
    '           "    // 離れた組み合わせの合成表（763↔771、764↔771、765↔771、766↔771、",\n'
    '           "    // 767↔771、768↔771）。上の *_763_TO_771／*_771_TO_763／",\n'
    '           "    // *_764_TO_771／*_771_TO_764／*_765_TO_771／*_771_TO_765／",\n'
    '           "    // *_766_TO_771／*_771_TO_766／*_767_TO_771／*_771_TO_767／",\n'
    '           "    // *_768_TO_771／*_771_TO_768 表（合成済みの1つの表）",',
    '           "    // 離れた組み合わせの合成表（763↔771、764↔771、765↔771、766↔771、",\n'
    '           "    // 767↔771、768↔771、769↔771）。上の *_763_TO_771／*_771_TO_763／",\n'
    '           "    // *_764_TO_771／*_771_TO_764／*_765_TO_771／*_771_TO_765／",\n'
    '           "    // *_766_TO_771／*_771_TO_766／*_767_TO_771／*_771_TO_767／",\n'
    '           "    // *_768_TO_771／*_771_TO_768／*_769_TO_771／*_771_TO_769 表",\n'
    '           "    // （合成済みの1つの表）",',
    "composed_accessors の頭コメント")
rep(
    '    pairs = [\n'
    '        ("763", 15, "763→771", "771→763"),\n'
    '        ("764", 16, "764→771", "771→764"),\n'
    '        ("765", 17, "765→771", "771→765"),\n'
    '        ("766", 18, "766→771", "771→766"),\n'
    '        ("767", 19, "767→771", "771→767"),\n'
    '        ("768", 20, "768→771", "771→768"),\n'
    '    ]',
    '    pairs = [\n'
    '        ("763", 19, "763→771", "771→763"),\n'
    '        ("764", 20, "764→771", "771→764"),\n'
    '        ("765", 21, "765→771", "771→765"),\n'
    '        ("766", 22, "766→771", "771→766"),\n'
    '        ("767", 23, "767→771", "771→767"),\n'
    '        ("768", 24, "768→771", "771→768"),\n'
    '        ("769", 25, "769→771", "771→769"),\n'
    '    ]\n'
    '    # 770 は隣接表（770↔771）で扱うため合成のペアには含めない。',
    "composed_accessors のペア")
rep(
    '            fb_771 = kind[21]',
    '            fb_771 = kind[27]',
    "composed_accessors の代替先 index")

# 音の名前の連鎖：770 の段階（entity.wolf.howl の削除）が shift で
# 771_MAP に置き換わって消えているため、770_MAP の段階を戻す。
for src in ("763", "764"):
    rep(
        f'            out.append("    public static String mapSoundName{src}To771(String name) {{")\n'
        '            out.append("        return mapSoundNameComposed(")\n'
        '            out.append("                mapSoundNameComposed(name, SOUND_NAME_FALLBACK_768_MAP),")\n'
        '            out.append("                SOUND_NAME_FALLBACK_771_MAP);")',
        f'            out.append("    public static String mapSoundName{src}To771(String name) {{")\n'
        '            out.append("        return mapSoundNameComposed(")\n'
        '            out.append("                mapSoundNameComposed(")\n'
        '            out.append("                        mapSoundNameComposed(name, SOUND_NAME_FALLBACK_768_MAP),")\n'
        '            out.append("                        SOUND_NAME_FALLBACK_770_MAP),")\n'
        '            out.append("                SOUND_NAME_FALLBACK_771_MAP);")',
        f"{src} の音の名前の連鎖")
rep(
    '            out.append("    public static String mapSoundName765To771(String name) {")\n'
    '            out.append("        return mapSoundNameComposed(")\n'
    '            out.append("                mapSoundNameComposed(")\n'
    '            out.append("                        mapSoundNameComposed(")\n'
    '            out.append("                                mapSoundNameComposed(name, SOUND_NAME_FALLBACK_766_MAP),")\n'
    '            out.append("                                SOUND_NAME_FALLBACK_767_MAP),")\n'
    '            out.append("                        SOUND_NAME_FALLBACK_768_MAP),")\n'
    '            out.append("                SOUND_NAME_FALLBACK_771_MAP);")',
    '            out.append("    public static String mapSoundName765To771(String name) {")\n'
    '            out.append("        return mapSoundNameComposed(")\n'
    '            out.append("                mapSoundNameComposed(")\n'
    '            out.append("                        mapSoundNameComposed(")\n'
    '            out.append("                                mapSoundNameComposed(")\n'
    '            out.append("                                        mapSoundNameComposed(name, SOUND_NAME_FALLBACK_766_MAP),")\n'
    '            out.append("                                        SOUND_NAME_FALLBACK_767_MAP),")\n'
    '            out.append("                                SOUND_NAME_FALLBACK_768_MAP),")\n'
    '            out.append("                        SOUND_NAME_FALLBACK_770_MAP),")\n'
    '            out.append("                SOUND_NAME_FALLBACK_771_MAP);")',
    "765 の音の名前の連鎖")

# 音の名前のコメント：「771で削除された entity.wolf.howl」は誤り
# （wolf.howl は 770 で削除）。771 の削除 4 件を書き加える。
for src in ("763", "764"):
    rep(
        f'            out.append("     * 音の名前を771向けに変換する。{src}の音の名前のうち 768で削除された")\n'
        '            out.append("     * ヤギの角笛の音2件と 771で削除された entity.wolf.howl は")\n'
        '            out.append("     * 代替の名前にする。{@code null} は石の破壊音にする。")',
        f'            out.append("     * 音の名前を771向けに変換する。{src}の音の名前のうち 768で削除された")\n'
        '            out.append("     * ヤギの角笛の音2件と 770で削除された entity.wolf.howl と")\n'
        '            out.append("     * 771で削除された leash_knot 系・puffer_fish.ambient・")\n'
        '            out.append("     * block.sand.wind は代替の名前にする。{@code null} は石の破壊音にする。")',
        f"{src} の音の名前コメント")
rep(
    '            out.append("     * 新しい名前にし、768で削除されたヤギの角笛の音2件と 771で削除された")\n'
    '            out.append("     * entity.wolf.howl は代替の名前にする。")',
    '            out.append("     * 新しい名前にし、768で削除されたヤギの角笛の音2件と 770で削除された")\n'
    '            out.append("     * entity.wolf.howl と 771で削除された leash_knot 系・")\n'
    '            out.append("     * puffer_fish.ambient・block.sand.wind は代替の名前にする。")',
    "765 の音の名前コメント")
rep(
    '            out.append("     * trial_spawner の音2件は新しい名前にし、768で削除されたヤギの角笛の音2件と")\n'
    '            out.append("     * 771で削除された entity.wolf.howl は代替の名前にする。")',
    '            out.append("     * trial_spawner の音2件は新しい名前にし、768で削除されたヤギの角笛の音2件と")\n'
    '            out.append("     * 770で削除された entity.wolf.howl と 771で削除された leash_knot 系・")\n'
    '            out.append("     * puffer_fish.ambient・block.sand.wind は代替の名前にする。")',
    "766 の音の名前コメント")
rep(
    '            out.append("     * 音の名前を771向けに変換する。768の音の名前のうち 771で削除された")\n'
    '            out.append("     * entity.wolf.howl は代替の名前にする。")',
    '            out.append("     * 音の名前を771向けに変換する。768の音の名前のうち 770で削除された")\n'
    '            out.append("     * entity.wolf.howl と 771で削除された leash_knot 系・")\n'
    '            out.append("     * puffer_fish.ambient・block.sand.wind は代替の名前にする。")',
    "768 の音の名前コメント")
rep(
    '            out.append("     * 音の名前を771向けに変換する。767の音の名前のうち 768で削除された")\n'
    '            out.append("     * ヤギの角笛の音2件と 771で削除された entity.wolf.howl は")\n'
    '            out.append("     * 代替の名前にする。")',
    '            out.append("     * 音の名前を771向けに変換する。767の音の名前のうち 768で削除された")\n'
    '            out.append("     * ヤギの角笛の音2件と 770で削除された entity.wolf.howl と")\n'
    '            out.append("     * 771で削除された leash_knot 系・puffer_fish.ambient・")\n'
    '            out.append("     * block.sand.wind は代替の名前にする。")',
    "767 の音の名前コメント")

# else（767）→ elif にし、769 のブロックを後ろに追加する。
rep(
    '        else:  # 767\n',
    '        elif src == "767":\n',
    "767 の else→elif")
BLOCK_769 = '''        elif src == "769":
            out.append("    /**")
            out.append("     * 769のエフェクトID → 771のエフェクトID。769も771も0ベース")
            out.append("     * （Speed は両方 0）で 0〜38 は同一のため恒等写像。")
            out.append("     * 範囲外は Speed（0）。")
            out.append("     */")
            out.append("    public static int mapEffect769To771(int id769) {")
            out.append("        if (id769 < 0 || id769 > MAX_EFFECT_769) {")
            out.append("            logUnknownComposedOnce(\\"769→771\\", \\"エフェクト\\", id769);")
            out.append("            return SPEED_EFFECT_771;")
            out.append("        }")
            out.append("        return id769;")
            out.append("    }")
            out.append("")
            out.append("    /**")
            out.append("     * 771のエフェクトID → 769のエフェクトID。0〜38 は同一のため恒等写像。")
            out.append("     * 範囲外は Speed（0）。")
            out.append("     */")
            out.append("    public static int mapEffect771To769(int id771) {")
            out.append("        if (id771 < 0 || id771 > MAX_EFFECT_771) {")
            out.append("            logUnknownComposedOnce(\\"771→769\\", \\"エフェクト\\", id771);")
            out.append("            return SPEED_EFFECT_769;")
            out.append("        }")
            out.append("        return id771;")
            out.append("    }")
            out.append("")
            out.append("    /**")
            out.append("     * 769のブロックエンティティ種別ID → 771の種別ID。")
            out.append("     * 769の種別は 0〜44 で771と同じ並びのためそのまま。")
            out.append("     * 範囲外の種別は NBT の中身が種別専用のため安全に代替できず")
            out.append("     * {@link #DROP}。")
            out.append("     */")
            out.append("    public static int mapBlockEntityType769To771(int id769) {")
            out.append("        if (id769 < 0 || id769 > BLOCK_ENTITY_VAULT_769) {")
            out.append("            logUnknownComposedOnce(\\"769→771\\", \\"ブロックエンティティ種別\\", id769);")
            out.append("            return DROP;")
            out.append("        }")
            out.append("        return id769;")
            out.append("    }")
            out.append("")
            out.append("    /**")
            out.append("     * 771のブロックエンティティ種別ID → 769の種別ID。")
            out.append("     * 0〜44 は並びが同一のためそのまま。test_block（45）・")
            out.append("     * test_instance_block（46）は769に無いため {@link #DROP}。")
            out.append("     */")
            out.append("    public static int mapBlockEntityType771To769(int id771) {")
            out.append("        if (id771 < 0 || id771 > BLOCK_ENTITY_VAULT_771) {")
            out.append("            logUnknownComposedOnce(\\"771→769\\", \\"ブロックエンティティ種別\\", id771);")
            out.append("            return DROP;")
            out.append("        }")
            out.append("        return id771;")
            out.append("    }")
            out.append("")
            out.append("    /**")
            out.append("     * 音の名前を771向けに変換する。769の音の名前のうち 770で削除された")
            out.append("     * entity.wolf.howl と 771で削除された leash_knot 系・")
            out.append("     * puffer_fish.ambient・block.sand.wind は代替の名前にする。")
            out.append("     * {@code null} は石の破壊音にする。")
            out.append("     */")
            out.append("    public static String mapSoundName769To771(String name) {")
            out.append("        return mapSoundNameComposed(name, SOUND_NAME_FALLBACK_769_TO_771_MAP);")
            out.append("    }")
            out.append("")
            out.append("    /**")
            out.append("     * 音の名前を769向けに変換する。771の音の名前のうち 769に無いものは")
            out.append("     * 代替の音の名前にする（数値の合成表と一致させる）。")
            out.append("     * {@code null} は石の破壊音にする。")
            out.append("     */")
            out.append("    public static String mapSoundName771To769(String name) {")
            out.append("        return mapSoundNameComposed(name, SOUND_NAME_FALLBACK_771_TO_769_MAP);")
            out.append("    }")
'''
rep(
    '            out.append("    public static String mapSoundName771To767(String name) {")\n'
    '            out.append("        return mapSoundNameComposed(name, SOUND_NAME_FALLBACK_771_TO_767_MAP);")\n'
    '            out.append("    }")\n'
    '    return "\\n".join(out)',
    '            out.append("    public static String mapSoundName771To767(String name) {")\n'
    '            out.append("        return mapSoundNameComposed(name, SOUND_NAME_FALLBACK_771_TO_767_MAP);")\n'
    '            out.append("    }")\n'
    + BLOCK_769 +
    '    return "\\n".join(out)',
    "769 のブロックを追加")

# --- M. small_registry_accessors()：769 まで（770 は既に直上のメソッド） ---
rep(
    '        for src in ("763", "764", "765", "766", "767", "768"):\n'
    '            # 770 のメソッドを経由する（763／764／765／766／767→770 と\n'
    '            # 770→763／764／765／766／767 は既にある）。',
    '        for src in ("763", "764", "765", "766", "767", "768", "769"):\n'
    '            # 770 のメソッドを経由する（763／764／765／766／767／768／769→770 と\n'
    '            # 770→763／764／765／766／767／768／769 は既にある）。',
    "small_registry のループ")

# --- N. damage_accessors()：769 まで ---
rep(
    '    for src in ("763", "764", "765", "766", "767", "768"):\n'
    '        # ダメージ種別は 770 のメソッドを経由する。',
    '    for src in ("763", "764", "765", "766", "767", "768", "769"):\n'
    '        # ダメージ種別は 770 のメソッドを経由する。',
    "damage のループ")

# --- O. JAVADOC_771 の書き直し ---
rep(
    ' * <p>771（1.21.6）向けの表も同じ作り方で持つ（{@code *_771}）。\n'
    ' * 771は770に近く、差分は次の通り（ViaVersion mapping-1.21.6.json と\n'
    ' * mapping-1.21.5.json、minecraft-data 1.21.6 の突き合わせ）。\n'
    ' * ブロック状態は新60件・削除12件（creaking_heart のプロパティが\n'
    ' * active＋natural の2ブールから creaking_heart_state（uprooted／\n'
    ' * dormant／awake）＋natural に変わった）。ブロックは新9件（茂み・\n'
    ' * ホタルブッシュ・枯れ草・ワイルドフラワー・リーフリッター・サボテン\n'
    ' * の花・テストブロック類）。アイテムは新11件（同上＋青い卵・茶色い卵）。\n'
    ' * エンティティは新1件・削除1件（potion が splash_potion と\n'
    ' * lingering_potion に分かれた）。音は新52件・削除1件\n'
    ' * （entity.wolf.howl）。パーティクルは新2件（tinted_leaves が 35 に\n'
    ' * 挿入され、35 以降が +1 ずれる。firefly が 113 に追加）。メニュー・\n'
    ' * エフェクト・属性・エンチャント・バイオームは同一（属性は 32件同士で\n'
    ' * 同一、エンチャントは 42件同士で並びは同一、バイオームは 65件同士で\n'
    ' * 同一）。ブロックエンティティ種別は 45→47 件（test_block=45、\n'
    ' * test_instance_block=46 を追加）。ダメージ種別・バナーパターン・\n'
    ' * 絵画バリアント・装飾パターンは 770 と同一（実バニラの Registry Data\n'
    ' * での確認は統合テストで行う）。\n'
    ' * 770→771 の隣接表と 763／764／765／766／767／768→771 の合成表を持ち、\n'
    ' * 逆向き 771→770 は反転して作る。',
    ' * <p>771（1.21.6）向けの表も同じ作り方で持つ（{@code *_771}）。\n'
    ' * 771は770に近く、差分は次の通り（ViaVersion mapping-1.21.6.json と\n'
    ' * mapping-1.21.5.json、minecraft-data 1.21.6 の突き合わせ）。\n'
    ' * ブロック状態は新32件（dried_ghast：向き4×hydration 4×水没2）・\n'
    ' * 削除0件。ブロックは新1件（dried_ghast）。アイテムは新19件\n'
    ' * （dried_ghast・ハーネス16色・happy_ghast_spawn_egg・\n'
    ' * music_disc_tears）。エンティティは新1件（happy_ghast）。\n'
    ' * 音は新29件・削除4件（entity.leash_knot.break／place・\n'
    ' * entity.puffer_fish.ambient・block.sand.wind）。パーティクル・\n'
    ' * メニュー・バイオーム・エフェクト・エンチャント・データコンポーネントは\n'
    ' * 同一。属性は新3件（camera_distance が 8 に挿入、\n'
    ' * waypoint_transmit_range・waypoint_receive_range を末尾に追加）。\n'
    ' * ブロックエンティティ種別は 770 と同じ 47 件。ダメージ種別・\n'
    ' * バナーパターン・絵画バリアント・装飾パターンは 770 と同一（実バニラの\n'
    ' * Registry Data での確認は統合テストで行う）。\n'
    ' * 770→771 の隣接表と 763／764／765／766／767／768／769→771 の合成表を持ち、\n'
    ' * 逆向き 771→770 は反転して作る。',
    "JAVADOC_771")

# --- P. main() のアンカー ---
rep(
    '                        "import static io.detergentmc.protocol.RegistryTables770Down767.*;\\n",',
    '                        "import static io.detergentmc.protocol.RegistryTables770Down768.*;\\n",',
    "import のアンカー")
rep(
    '                        "import static io.detergentmc.protocol.RegistryTables771Down767.*;\\n"\n'
    '                        "import static io.detergentmc.protocol.RegistryTables771Down768.*;\\n",',
    '                        "import static io.detergentmc.protocol.RegistryTables771Down767.*;\\n"\n'
    '                        "import static io.detergentmc.protocol.RegistryTables771Down768.*;\\n"\n'
    '                        "import static io.detergentmc.protocol.RegistryTables771Down769.*;\\n",',
    "import に Down769 を追加")
rep(
    '    anchor = (" * 768→770 の隣接表と 763／764／765／766／767→770 の合成表を持ち、\\n"\n'
    '              " * 逆向き 770→768 は反転して作る。\\n")',
    '    anchor = (" * 769→770 の隣接表と 763／764／765／766／767／768→770 の合成表を持ち、\\n"\n'
    '              " * 逆向き 770→769 は反転して作る。\\n")',
    "javadoc のアンカー")
rep(
    '    anchor = ("    private static final int[] MENU_770_MAP =\\n"\n'
    '              "            expand(MENU_770_RANGES, MENU_770_OVERRIDES, MAX_MENU_768 + 1,\\n"\n'
    '              "                    CRAFTING_MENU_770);\\n")',
    '    anchor = ("    private static final int[] MENU_770_MAP =\\n"\n'
    '              "            expand(MENU_770_RANGES, MENU_770_OVERRIDES, MAX_MENU_769 + 1,\\n"\n'
    '              "                    CRAFTING_MENU_770);\\n")',
    "隣接表のアンカー")
rep(
    '    # 合成表。MENU_770_TO_767_MAP の直後。\n'
    '    anchor = ("    private static final int[] MENU_770_TO_767_MAP =\\n"\n'
    '              "            expand(MENU_770_TO_767_RANGES, MENU_770_TO_767_OVERRIDES,\\n"\n'
    '              "                    25, CRAFTING_MENU_767);\\n")',
    '    # 合成表。MENU_770_TO_768_MAP の直後。\n'
    '    anchor = ("    private static final int[] MENU_770_TO_768_MAP =\\n"\n'
    '              "            expand(MENU_770_TO_768_RANGES, MENU_770_TO_768_OVERRIDES,\\n"\n'
    '              "                    25, CRAFTING_MENU_768);\\n")',
    "合成表のアンカー")
rep(
    '    anchor = ("    private static final int[] MENU_UP_770_MAP = invert(\\n"\n'
    '              "            MENU_770_RANGES, MENU_770_OVERRIDES,\\n"\n'
    '              "            MAX_MENU_768 + 1, MAX_MENU_770 + 1,\\n"\n'
    '              "            CRAFTING_MENU_768, null);\\n")',
    '    anchor = ("    private static final int[] MENU_UP_770_MAP = invert(\\n"\n'
    '              "            MENU_770_RANGES, MENU_770_OVERRIDES,\\n"\n'
    '              "            MAX_MENU_769 + 1, MAX_MENU_770 + 1,\\n"\n'
    '              "            CRAFTING_MENU_769, null);\\n")',
    "反転表のアンカー")
rep(
    '    anchor = ("    public static String mapSoundNameUp770(String name) {\\n"\n'
    '              "        return mapSoundNameComposed(name, SOUND_NAME_FALLBACK_770_TO_768_MAP);\\n"\n'
    '              "    }\\n")',
    '    anchor = ("    public static String mapSoundNameUp770(String name) {\\n"\n'
    '              "        return mapSoundNameComposed(name, SOUND_NAME_FALLBACK_770_TO_769_MAP);\\n"\n'
    '              "    }\\n")',
    "アクセサ（b）のアンカー")
rep(
    '    # アクセサ（c）763／764／765／766／767↔771。mapSoundName770To766 の直後。',
    '    # アクセサ（c）763／764／765／766／767／768／769↔771。mapSoundName770To766 の直後。',
    "アクセサ（c）のコメント")
rep(
    '                        "    /** 770のバナーパターン最大ID（43件。768と同一）。 */\\n"\n'
    '                        "    public static final int MAX_BANNER_PATTERN_770 = 42;\\n",',
    '                        "    /** 770のバナーパターン最大ID（43件。769と同一と仮定。実バニラの\\n"\n'
    '                        "        Registry Data での確認は統合テストで行う）。 */\\n"\n'
    '                        "    public static final int MAX_BANNER_PATTERN_770 = 42;\\n",',
    "バナー定数のアンカー")
rep(
    "    /** 770のバナーパターンID → 766のバナーパターンID（768経由）。 */\\n",
    "    /** 770のバナーパターンID → 766のバナーパターンID（769経由）。 */\\n",
    "バナーアクセサのアンカー")
rep(
    "        return mapBannerPattern768To766(mapBannerPattern770To768(id770));\\n",
    "        return mapBannerPattern769To766(mapBannerPattern770To769(id770));\\n",
    "バナーアクセサのアンカー（本体）")
rep(
    "    /** 770のダメージ種別ID → 766のダメージ種別ID（768経由）。 */\\n",
    "    /** 770のダメージ種別ID → 766のダメージ種別ID（769経由）。 */\\n",
    "ダメージアクセサのアンカー")
rep(
    "        return mapDamageType768To766(mapDamageType770To768(id770));\\n",
    "        return mapDamageType769To766(mapDamageType770To769(id770));\\n",
    "ダメージアクセサのアンカー（本体）")
rep(
    '    java = java.replace(\n'
    '        "    // 765は素通し、766は766の表、767は767の表、768は768の表、\\n"\n'
    '        "    // 770は770の表、それより古い未知の番号は763の表を使う。\\n"\n'
    '        "    // 767／768／769／770は765以上だが素通しではない（>765 の分岐より\\n"\n'
    '        "    // 先に 767／768／769／770 を判定すること）。",',
    '    java = java.replace(\n'
    '        "    // 765は素通し、766は766の表、767は767の表、768は768の表、\\n"\n'
    '        "    // 769は769の表、770は770の表、それより古い未知の番号は763の表を使う。\\n"\n'
    '        "    // 767／768／769／770は765以上だが素通しではない（>765 の分岐より\\n"\n'
    '        "    // 先に 767／768／769／770 を判定すること）。",',
    "ディスパッチコメントの旧文")
rep(
    "mapItemReverse770 は 770→768",
    "mapItemReverse770 は 770→769",
    "ディスパッチのアンカー（mapItemReverse）")
rep(
    '        "    // 765は素通し、766は766の表、767は767の表、768は768の表、\\n"\n'
    '        "    // 770は770の表、771は771の表、それより古い未知の番号は763の表を使う。\\n"',
    '        "    // 765は素通し、766は766の表、767は767の表、768は768の表、\\n"\n'
    '        "    // 769は769の表、770は770の表、771は771の表、それより古い未知の番号は763の表を使う。\\n"',
    "ディスパッチコメントの新文")

if failed:
    print("失敗:", file=sys.stderr)
    for f in failed:
        print(" -", f, file=sys.stderr)
    sys.exit(1)
P.write_text(s, encoding="utf-8")
print("fix-771-java.py: 全ての修正を適用した")
