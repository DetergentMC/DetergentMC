#!/usr/bin/env python3
"""tools/insert-772-java.py で入れた *_772 表の上に、RegistryRemapper.java
へ 773 対応の定数・展開済み表（*_773_MAP）・反転表（*_UP_773_MAP）・
アクセサを挿入する（1回だけの作業用）。

  - 定数：PROTOCOL_773、MAX_*_773、773 側の代替先。
  - 隣接表（772→773）: *_773_MAP と ITEM_REVERSE_773_MAP と
    ATTRIBUTE_773_MAP／ATTRIBUTE_REVERSE_773_MAP／ENCHANTMENT_773_MAP／
    ENCHANTMENT_REVERSE_773_MAP。
  - 合成表（763↔773、764↔773、765↔773、766↔773、767↔773、768↔773、769↔773、770↔773、771↔773）: *_763_TO_773_MAP など126件。
  - 772↔773 は隣接表（*_773_MAP）と反転表（*_UP_773_MAP）で扱うため合成表は作らない（772と同じ方針）。
  - 逆向き（773→772）: *_UP_773_MAP（invert()。アイテムは
    ITEM_REVERSE_773_MAP をそのまま使う。772→773 に補正は無い）。
  - 音の名前の表（String）: SOUND_NAME_FALLBACK_*_773_MAP など18件。
  - アクセサ：772↔773、773→772（上向き）、763／764／765／766／767／768／769／770／771↔773。
  - エフェクト：773 は 772 と同一の 39 件（0〜38）のため恒等写像。
    属性は 771・772・773 とも 35 件で同一、エンチャントは 42 件同士で
    並びは同一（minecraft-data の dataPaths.json で 1.21.9 の
    enchantments は pc/1.21.9 と確認。1.21.1 の並びと同一）。
    ブロックエンティティ種別は 773 は 49 件（shelf=40 が挿入され、
    copper_golem_statue=48 を追加。trial_spawner=44、vault=45、
    test_block=46、test_instance_block=47）。
    バナー・絵画・装飾パターンは 772 と同一のため恒等写像。
    ダメージ種別も 772 と同一の 49 件のため恒等写像。
    バイオームは 769・770・771・772・773 とも 65 件で同一の並び（実バニラの Registry Data
    での確認は統合テストで行う）。
  - 注意：このスクリプトで挿入した展開済み表（隣接12・合成140・上向き6の
    計144件）は、そのままでは RegistryRemapper.java の <clinit> が 64KB を
    超えてコンパイルできない（772 までがギリギリ収まっていた）。そのため
    挿入後に tools/move-773-expanded-tables.py で RegistryTables773Expanded.java
    へ移動してある。再実行時はこの移動もやり直すこと。
"""

import re
import sys
from pathlib import Path

SCRIPT_DIR = Path(__file__).resolve().parent
REPO_ROOT = SCRIPT_DIR.parent.parent
JAVA_FILE = (REPO_ROOT / "detergentmc-protocol" / "src" / "main" / "java"
             / "io" / "detergentmc" / "protocol" / "RegistryRemapper.java")
TABLES773_FILE = (REPO_ROOT / "detergentmc-protocol" / "src" / "main" / "java"
                  / "io" / "detergentmc" / "protocol" / "RegistryTables773.java")
TABLES773_COMPOSED_FILE = (REPO_ROOT / "detergentmc-protocol" / "src" / "main" / "java"
                  / "io" / "detergentmc" / "protocol" / "RegistryTables773ComposedUp.java")
TABLES773_COMPOSED_DOWN_FILES = {
    suffix: (REPO_ROOT / "detergentmc-protocol" / "src" / "main" / "java"
             / "io" / "detergentmc" / "protocol"
             / f"RegistryTables773Down{suffix}.java")
    for suffix in ("763", "764", "765", "766", "767", "768", "769", "770", "771")
}


# kind: (Java メソッド名の語尾, RANGES の接頭辞, 日本語名,
#        763→773 の件数, 773→763 の件数, 764→773 の件数, 773→764 の件数,
#        765→773 の件数, 773→765 の件数, 766→773 の件数, 773→766 の件数,
#        767→773 の件数, 773→767 の件数, 768→773 の件数, 773→768 の件数,
#        769→773 の件数, 773→769 の件数, 770→773 の件数, 773→770 の件数,
#        771→773 の件数, 773→771 の件数, 772→773 の件数, 773→772 の件数,
#        763 側の代替先, 764 側の代替先, 765 側の代替先, 766 側の代替先,
#        767 側の代替先, 768 側の代替先, 769 側の代替先, 770 側の代替先,
#        771 側の代替先, 772 側の代替先, 773 側の代替先)
KINDS = [
    ("BlockState", "BLOCK_STATE", "ブロック状態", 24135, 29671, 24276, 29671, 26644, 29671, 26684, 29671, 26684, 29671, 27318, 29671, 27866, 29671, 27914, 29671, 27946, 29671, 27946, 29671,
     "STONE_BLOCK_STATE_763", "STONE_BLOCK_STATE_764", "STONE_BLOCK_STATE_765", "STONE_BLOCK_STATE_766", "STONE_BLOCK_STATE_767", "STONE_BLOCK_STATE_768", "STONE_BLOCK_STATE_769", "STONE_BLOCK_STATE_770", "STONE_BLOCK_STATE_771", "STONE_BLOCK_STATE_772", "STONE_BLOCK_STATE_773"),
    ("Item", "ITEM", "アイテム", 1255, 1488, 1255, 1488, 1312, 1488, 1330, 1488, 1333, 1488, 1375, 1488, 1385, 1488, 1396, 1488, 1415, 1488, 1416, 1488,
     "STONE_ITEM_763", "STONE_ITEM_764", "STONE_ITEM_765", "STONE_ITEM_766", "STONE_ITEM_767", "STONE_ITEM_768", "STONE_ITEM_769", "STONE_ITEM_770", "STONE_ITEM_771", "STONE_ITEM_772", "STONE_ITEM_773"),
    ("EntityType", "ENTITY_TYPE", "エンティティ種別", 124, 153, 124, 153, 126, 153, 130, 153, 130, 153, 150, 153, 149, 153, 150, 153, 151, 153, 151, 153,
     "PIG_ENTITY_TYPE_763", "PIG_ENTITY_TYPE_764", "PIG_ENTITY_TYPE_765", "PIG_ENTITY_TYPE_766", "PIG_ENTITY_TYPE_767", "PIG_ENTITY_TYPE_768", "PIG_ENTITY_TYPE_769", "PIG_ENTITY_TYPE_770", "PIG_ENTITY_TYPE_771", "PIG_ENTITY_TYPE_772", "PIG_ENTITY_TYPE_773"),
    ("Sound", "SOUND", "音", 1474, 1771, 1485, 1771, 1539, 1771, 1607, 1771, 1611, 1771, 1636, 1771, 1651, 1771, 1702, 1771, 1727, 1771, 1728, 1771,
     "GENERIC_SOUND_763", "GENERIC_SOUND_764", "GENERIC_SOUND_765", "GENERIC_SOUND_766", "GENERIC_SOUND_767", "GENERIC_SOUND_768", "GENERIC_SOUND_769", "GENERIC_SOUND_770", "GENERIC_SOUND_771", "GENERIC_SOUND_772", "GENERIC_SOUND_773"),
    ("Particle", "PARTICLE", "パーティクル", 95, 115, 95, 115, 101, 115, 109, 115, 109, 115, 111, 115, 112, 115, 114, 115, 114, 115, 114, 115,
     "AMBIENT_PARTICLE_763", "AMBIENT_PARTICLE_764", "AMBIENT_PARTICLE_765", "ENTITY_EFFECT_PARTICLE_766", "ENTITY_EFFECT_PARTICLE_767", "ENTITY_EFFECT_PARTICLE_768", "ENTITY_EFFECT_PARTICLE_769", "ENTITY_EFFECT_PARTICLE_770", "ENTITY_EFFECT_PARTICLE_771", "ENTITY_EFFECT_PARTICLE_772", "ENTITY_EFFECT_PARTICLE_773"),
    ("Block", "BLOCK", "ブロック", 1003, 1166, 1003, 1166, 1058, 1166, 1060, 1166, 1060, 1166, 1084, 1166, 1095, 1166, 1104, 1166, 1105, 1166, 1105, 1166,
     "STONE_BLOCK_763", "STONE_BLOCK_764", "STONE_BLOCK_765", "STONE_BLOCK_766", "STONE_BLOCK_767", "STONE_BLOCK_768", "STONE_BLOCK_769", "STONE_BLOCK_770", "STONE_BLOCK_771", "STONE_BLOCK_772", "STONE_BLOCK_773"),
    ("Menu", "MENU", "メニュー種別", 24, 25, 24, 25, 25, 25, 25, 25, 25, 25, 25, 25, 25, 25, 25, 25, 25, 25, 25, 25,
     "CRAFTING_MENU_763", "CRAFTING_MENU_764", "CRAFTING_MENU_765", "CRAFTING_MENU_766", "CRAFTING_MENU_767", "CRAFTING_MENU_768", "CRAFTING_MENU_769", "CRAFTING_MENU_770", "CRAFTING_MENU_771", "CRAFTING_MENU_772", "CRAFTING_MENU_773"),
]

CONSTANTS_MAX = """
    /** 773側の最大の番号（ViaVersion mapping-1.21.9.json の件数−1）。 */
    public static final int MAX_BLOCK_STATE_773 = 29670;
    public static final int MAX_ITEM_773 = 1487;
    public static final int MAX_ENTITY_TYPE_773 = 152;
    public static final int MAX_SOUND_773 = 1770;
    public static final int MAX_PARTICLE_773 = 114;
    public static final int MAX_BLOCK_773 = 1165;
    public static final int MAX_MENU_773 = 24;
    /** 773側のエフェクトも 0〜38（Speed=0。772と同一。minecraft-data の
        dataPaths.json で 1.21.9 の effects は pc/1.21.9 と確認）。 */
    public static final int MAX_EFFECT_773 = 38;
    /** 773側の属性は 0〜34（772 と同じ 35 件。
        ViaVersion mapping-1.21.9.json で確認）。 */
    public static final int MAX_ATTRIBUTE_773 = 34;
    /** エンチャントは 769・770・771・772・773 とも 0〜41（42件。並びは同一。
        minecraft-data の dataPaths.json で 1.21.9 の enchantments は
        pc/1.21.9 と確認（1.21.1 の並びと同一）。実機の Registry Data での
        確認は統合テストで行う）。 */
    public static final int MAX_ENCHANTMENT_773 = 41;
"""

CONSTANTS_FALLBACK = """
    /** 773側の代替先（mapping-1.21.9.json の番号。Speed だけ 0ベースで 0）。
     * ブタは 773 では 97（copper_golem が 27 に、mannequin が 82 に
     * 挿入され、772 の 95 から +2）、
     * 石の破壊音は 773 では 1484（新音 43 件の挿入で 772 の 1442 から
     * +42）。 */
    public static final int STONE_BLOCK_STATE_773 = 1;
    public static final int AIR_BLOCK_STATE_773 = 0;
    public static final int STONE_ITEM_773 = 1;
    public static final int PIG_ENTITY_TYPE_773 = 97;
    public static final int GENERIC_SOUND_773 = 1484; // block.stone.break
    /** 773でも ambient_entity_effect は無いため、パーティクルの代替は entity_effect。 */
    public static final int ENTITY_EFFECT_PARTICLE_773 = 20;
    public static final int STONE_BLOCK_773 = 1;
    public static final int CRAFTING_MENU_773 = 12;
    public static final int SPEED_EFFECT_773 = 0;
"""

CONSTANTS_BLOCK_ENTITY = """
    /** 773のブロックエンティティ種別：49 件（772 の 47 件に shelf が
        40 に挿入され、copper_golem_statue を 48 に追加。trial_spawner=44、
        vault=45、test_block=46、test_instance_block=47）。 */
    public static final int BLOCK_ENTITY_SHELF_773 = 40;
    public static final int BLOCK_ENTITY_CREAKING_HEART_773 = 10;
    public static final int BLOCK_ENTITY_TRIAL_SPAWNER_773 = 44;
    public static final int BLOCK_ENTITY_VAULT_773 = 45;
    public static final int BLOCK_ENTITY_TEST_BLOCK_773 = 46;
    public static final int BLOCK_ENTITY_TEST_INSTANCE_BLOCK_773 = 47;
    public static final int BLOCK_ENTITY_COPPER_GOLEM_STATUE_773 = 48;
"""


def adjacent_maps():
    lines = []
    for kind in KINDS:
        name, prefix, jp = kind[0], kind[1], kind[2]
        lines.append(
            f"    private static final int[] {prefix}_773_MAP =\n"
            f"            expand({prefix}_773_RANGES, {prefix}_773_OVERRIDES, MAX_{prefix}_772 + 1,\n"
            f"                    {kind[33]});")
    lines.append(
        "    private static final int[] ITEM_REVERSE_773_MAP =\n"
        "            expand(ITEM_REVERSE_773_RANGES, ITEM_REVERSE_773_OVERRIDES, 1488, -1);")
    lines.append(
        "    private static final int[] ATTRIBUTE_773_MAP =\n"
        "            expand(ATTRIBUTE_773_RANGES, ATTRIBUTE_773_OVERRIDES, MAX_ATTRIBUTE_772 + 1, DROP);")
    lines.append(
        "    private static final int[] ATTRIBUTE_REVERSE_773_MAP =\n"
        "            expand(ATTRIBUTE_REVERSE_773_RANGES, ATTRIBUTE_REVERSE_773_OVERRIDES,\n"
        "                    MAX_ATTRIBUTE_773 + 1, DROP);")
    lines.append(
        "    private static final int[] ENCHANTMENT_773_MAP =\n"
        "            expand(ENCHANTMENT_773_RANGES, ENCHANTMENT_773_OVERRIDES,\n"
        "                    MAX_ENCHANTMENT_772 + 1, DROP);")
    lines.append(
        "    private static final int[] ENCHANTMENT_REVERSE_773_MAP =\n"
        "            expand(ENCHANTMENT_REVERSE_773_RANGES, ENCHANTMENT_REVERSE_773_OVERRIDES,\n"
        "                    MAX_ENCHANTMENT_773 + 1, DROP);")
    return "\n".join(lines)


def composed_maps():
    # (接尾辞, 件数の位置, 代替先の位置)。件数・代替先は KINDS の
    # タプルから取る（763_TO_773: 件数 index 3, 代替先 773=index 33／
    # 773_TO_763: 件数 index 4, 代替先 763=index 23）。件数は 3〜22、
    # 代替先は 23（763）〜33（773）。772↔773 は隣接表で扱うため含めない。
    pairs = [
        ("763_TO_773", 3, 33), ("773_TO_763", 4, 23),
        ("764_TO_773", 5, 33), ("773_TO_764", 6, 24),
        ("765_TO_773", 7, 33), ("773_TO_765", 8, 25),
        ("766_TO_773", 9, 33), ("773_TO_766", 10, 26),
        ("767_TO_773", 11, 33), ("773_TO_767", 12, 27),
        ("768_TO_773", 13, 33), ("773_TO_768", 14, 28),
        ("769_TO_773", 15, 33), ("773_TO_769", 16, 29),
        ("770_TO_773", 17, 33), ("773_TO_770", 18, 30),
        ("771_TO_773", 19, 33), ("773_TO_771", 20, 31),
    ]
    lines = []
    for suffix, size_idx, fb_idx in pairs:
        for kind in KINDS:
            name, prefix, jp = kind[0], kind[1], kind[2]
            size = kind[size_idx]
            fb = kind[fb_idx]
            lines.append(
                f"    private static final int[] {prefix}_{suffix}_MAP =\n"
                f"            expand({prefix}_{suffix}_RANGES, {prefix}_{suffix}_OVERRIDES,\n"
                f"                    {size}, {fb});")
    return "\n".join(lines)


def up_maps():
    lines = []
    for kind in KINDS:
        name, prefix, jp = kind[0], kind[1], kind[2]
        if prefix == "ITEM":
            continue  # アイテムは ITEM_REVERSE_773_MAP を使う。
        # 772→773 に補正は無い（creaking_heart のプロパティ変更と
        # potion の分割は 772 で済んでいる）。反転はそのまま使う。
        corrections = "null"
        # 逆向きの代替先は「772 側」の定数（kind[26]）。
        fb772 = kind[32]
        lines.append(
            f"    private static final int[] {prefix}_UP_773_MAP = invert(\n"
            f"            {prefix}_773_RANGES, {prefix}_773_OVERRIDES,\n"
            f"            MAX_{prefix}_772 + 1, MAX_{prefix}_773 + 1,\n"
            f"            {fb772}, {corrections});")
    return "\n".join(lines)


SOUND_NAME_MAPS = """
    private static final Map<String, String> SOUND_NAME_FALLBACK_773_MAP =
            buildSoundNameMap(SOUND_NAME_FALLBACKS_773);
    private static final Map<String, String> SOUND_NAME_FALLBACK_772_TO_773_MAP =
            buildSoundNameMap(SOUND_NAME_FALLBACKS_772_TO_773);
    private static final Map<String, String> SOUND_NAME_FALLBACK_773_TO_772_MAP =
            buildSoundNameMap(SOUND_NAME_FALLBACKS_773_TO_772);
    private static final Map<String, String> SOUND_NAME_FALLBACK_770_TO_773_MAP =
            buildSoundNameMap(SOUND_NAME_FALLBACKS_770_TO_773);
    private static final Map<String, String> SOUND_NAME_FALLBACK_773_TO_770_MAP =
            buildSoundNameMap(SOUND_NAME_FALLBACKS_773_TO_770);
    private static final Map<String, String> SOUND_NAME_FALLBACK_771_TO_773_MAP =
            buildSoundNameMap(SOUND_NAME_FALLBACKS_771_TO_773);
    private static final Map<String, String> SOUND_NAME_FALLBACK_773_TO_771_MAP =
            buildSoundNameMap(SOUND_NAME_FALLBACKS_773_TO_771);
    private static final Map<String, String> SOUND_NAME_FALLBACK_769_TO_773_MAP =
            buildSoundNameMap(SOUND_NAME_FALLBACKS_769_TO_773);
    private static final Map<String, String> SOUND_NAME_FALLBACK_773_TO_769_MAP =
            buildSoundNameMap(SOUND_NAME_FALLBACKS_773_TO_769);
    private static final Map<String, String> SOUND_NAME_FALLBACK_773_TO_768_MAP =
            buildSoundNameMap(SOUND_NAME_FALLBACKS_773_TO_768);
    private static final Map<String, String> SOUND_NAME_FALLBACK_773_TO_767_MAP =
            buildSoundNameMap(SOUND_NAME_FALLBACKS_773_TO_767);
    private static final Map<String, String> SOUND_NAME_FALLBACK_773_TO_765_MAP =
            buildSoundNameMap(SOUND_NAME_FALLBACKS_773_TO_765);
    private static final Map<String, String> SOUND_NAME_FALLBACK_773_TO_763_MAP =
            buildSoundNameMap(SOUND_NAME_FALLBACKS_773_TO_763);
    private static final Map<String, String> SOUND_NAME_FALLBACK_773_TO_764_MAP =
            buildSoundNameMap(SOUND_NAME_FALLBACKS_773_TO_764);
    private static final Map<String, String> SOUND_NAME_FALLBACK_773_TO_766_MAP =
            buildSoundNameMap(SOUND_NAME_FALLBACKS_773_TO_766);
    private static final Map<String, String> SOUND_NAME_FALLBACK_768_TO_773_MAP =
            buildSoundNameMap(SOUND_NAME_FALLBACKS_768_TO_773);
    private static final Map<String, String> SOUND_NAME_FALLBACK_767_TO_773_MAP =
            buildSoundNameMap(SOUND_NAME_FALLBACKS_767_TO_773);
    private static final Map<String, String> SOUND_NAME_FALLBACK_766_TO_773_MAP =
            buildSoundNameMap(SOUND_NAME_FALLBACKS_766_TO_773);
"""

LOG_UP_773 = """
    private static void logUnknownUp773Once(String kind, int id773) {
        if (LOGGED_UNKNOWN.add("up773:" + kind + ":" + id773)) {
            LOG.warning("773に無い" + kind + "の番号が来たため代替に落とします（773 id=" + id773 + "）。");
        }
    }
"""
def adjacent_accessors():
    out = ["    // ------------------------------------------------------------------",
           "    // 772→773。上の *_773 表を使う。ブロック状態・ブロック・",
           "    // アイテム・エンティティ・音は 772 から 773 で銅の棚・銅の道具・",
           "    // 防具・銅ゴーレムなどの挿入でずれる。パーティクル・",
           "    // メニュー・エフェクト・エンチャント・属性・バイオームは同一。",
           "    // ブロックエンティティ種別は 772 の 47 件から 773 の 49 件へ",
           "    // （shelf が 40 に挿入され 40 以降が +1、copper_golem_statue",
           "    // を 48 に追加）。絵画・ジュークボックスの歌は 772 と同一。",
           "    // ------------------------------------------------------------------",
           ""]
    for kind in KINDS:
        name, prefix, jp = kind[0], kind[1], kind[2]
        fb773 = kind[33]
        out.append(f"    /** 772の{jp}ID → 773の{jp}ID。範囲外は{fb773} に相当する代替。 */")
        out.append(f"    public static int map{name}772To773(int id772) {{")
        out.append(f"        if (id772 < 0 || id772 > MAX_{prefix}_772) {{")
        out.append(f"            logUnknownOnce(PROTOCOL_773, \"{jp}\", id772);")
        out.append(f"            return {fb773};")
        out.append("        }")
        out.append(f"        return {prefix}_773_MAP[id772];")
        out.append("    }")
        out.append("")
    out.append("    /**")
    out.append("     * 773のアイテムID → 772のアイテムID（サーバー行きスロット用）。")
    out.append("     * 773にしか無いアイテム72件（銅の道具5件・銅の防具4件・")
    out.append("     * 棚12件・銅の松明1件・銅の棒／鎖／ランタン／チェスト／ゴーレム像")
    out.append("     * 各8件・避雷針7件・銅の延べ棒・銅ゴーレムのスポーンエッグ・銅の")
    out.append("     * 馬鎧）は 772の石へ。chain→iron_chain は改名で写る。")
    out.append("     * 範囲外はそのまま返す。")
    out.append("     */")
    out.append("    public static int mapItemReverse773(int id773) {")
    out.append("        if (id773 < 0 || id773 >= ITEM_REVERSE_773_MAP.length) {")
    out.append("            logUnknownOnce(PROTOCOL_773, \"アイテム（逆変換）\", id773);")
    out.append("            return id773;")
    out.append("        }")
    out.append("        int mapped = ITEM_REVERSE_773_MAP[id773];")
    out.append("        return mapped < 0 ? id773 : mapped;")
    out.append("    }")
    out.append("")
    out.append("    /**")
    out.append("     * 772のエフェクトID → 773のエフェクトID。772も773も0ベース")
    out.append("     * （Speed は両方 0）で 0〜38 は同一のため恒等写像。")
    out.append("     * 範囲外は Speed（0）。")
    out.append("     */")
    out.append("    public static int mapEffect772To773(int id772) {")
    out.append("        if (id772 < 0 || id772 > MAX_EFFECT_772) {")
    out.append("            logUnknownOnce(PROTOCOL_773, \"エフェクト\", id772);")
    out.append("            return SPEED_EFFECT_773;")
    out.append("        }")
    out.append("        return id772;")
    out.append("    }")
    out.append("")
    out.append("    /**")
    out.append("     * 772のブロックエンティティ種別ID → 773の種別ID。")
    out.append("     * shelf（40）の挿入で 40 以降は +1 ずれる。773は 49 件。")
    out.append("     * 範囲外の種別は NBT の中身が種別専用のため安全に代替できず")
    out.append("     * {@link #DROP}。")
    out.append("     */")
    out.append("    public static int mapBlockEntityType772To773(int id772) {")
    out.append("        if (id772 < 0 || id772 > BLOCK_ENTITY_TEST_INSTANCE_BLOCK_772) {")
    out.append("            logUnknownOnce(PROTOCOL_773, \"ブロックエンティティ種別\", id772);")
    out.append("            return DROP;")
    out.append("        }")
    out.append("        return id772 < BLOCK_ENTITY_SHELF_773 ? id772 : id772 + 1;")
    out.append("    }")
    out.append("")
    out.append("    /**")
    out.append("     * 音の名前（直書き・Stop Sound 用）を773向けに変換する。")
    out.append("     * {@code minecraft:} の有無はそのまま保つ。771で削除された")
    out.append("     * entity.leash_knot.break／place・entity.puffer_fish.ambient・")
    out.append("     * block.sand.wind は代替の名前にする。773で削除された音は無い。")
    out.append("     */")
    out.append("    public static String mapSoundName772To773(String name) {")
    out.append("        return mapSoundNameComposed(name, SOUND_NAME_FALLBACK_772_TO_773_MAP);")
    out.append("    }")
    out.append("")
    # 属性とエンチャント。
    out.append("    /** 772の属性ID → 773の属性ID。並びは同一。範囲外は {@link #DROP}。 */")
    out.append("    public static int mapAttribute772To773(int id772) {")
    out.append("        if (id772 < 0 || id772 > MAX_ATTRIBUTE_772) {")
    out.append("            logUnknownOnce(PROTOCOL_773, \"属性\", id772);")
    out.append("            return DROP;")
    out.append("        }")
    out.append("        return ATTRIBUTE_773_MAP[id772];")
    out.append("    }")
    out.append("")
    out.append("    /** 773の属性ID → 772の属性ID。並びは同一。範囲外は {@link #DROP}。 */")
    out.append("    public static int mapAttribute773To772(int id773) {")
    out.append("        if (id773 < 0 || id773 > MAX_ATTRIBUTE_773) {")
    out.append("            logUnknownOnce(PROTOCOL_773, \"属性\", id773);")
    out.append("            return DROP;")
    out.append("        }")
    out.append("        return ATTRIBUTE_REVERSE_773_MAP[id773];")
    out.append("    }")
    out.append("")
    out.append("    /** 773の属性ID → 属性の名前（Update Attributes の 772向け変換用）。 */")
    out.append("    public static String attributeName773(int id773) {")
    out.append("        if (id773 < 0 || id773 >= ATTRIBUTE_773_NAMES.length) {")
    out.append("            return null;")
    out.append("        }")
    out.append("        return ATTRIBUTE_773_NAMES[id773];")
    out.append("    }")
    out.append("")
    out.append("    /**")
    out.append("     * 属性の名前 → 773の属性ID（765／763／764 は属性が名前形式のため、")
    out.append("     * 773向けに変換するときに使う）。無い名前は {@link #DROP}。")
    out.append("     */")
    out.append("    public static int attributeId773(String name) {")
    out.append("        if (name == null) {")
    out.append("            return DROP;")
    out.append("        }")
    out.append("        String plain = stripMinecraftPrefix(name);")
    out.append("        for (int i = 0; i < ATTRIBUTE_773_NAMES.length; i++) {")
    out.append("            if (ATTRIBUTE_773_NAMES[i].equals(plain)) {")
    out.append("                return i;")
    out.append("            }")
    out.append("        }")
    out.append("        return DROP;")
    out.append("    }")
    out.append("")
    out.append("    /** 772のエンチャントID → 773のエンチャントID。並びは同一。範囲外は {@link #DROP}。 */")
    out.append("    public static int mapEnchantment772To773(int id772) {")
    out.append("        if (id772 < 0 || id772 > MAX_ENCHANTMENT_772) {")
    out.append("            logUnknownOnce(PROTOCOL_773, \"エンチャント\", id772);")
    out.append("            return DROP;")
    out.append("        }")
    out.append("        return ENCHANTMENT_773_MAP[id772];")
    out.append("    }")
    out.append("")
    out.append("    /** 773のエンチャントID → 772のエンチャントID。並びは同一。範囲外は {@link #DROP}。 */")
    out.append("    public static int mapEnchantment773To772(int id773) {")
    out.append("        if (id773 < 0 || id773 > MAX_ENCHANTMENT_773) {")
    out.append("            logUnknownOnce(PROTOCOL_773, \"エンチャント\", id773);")
    out.append("            return DROP;")
    out.append("        }")
    out.append("        return ENCHANTMENT_REVERSE_773_MAP[id773];")
    out.append("    }")
    out.append("")
    out.append("    /**")
    out.append("     * 767のエンチャントID → 773のエンチャントID。767→772 の表を")
    out.append("     * 経由して 772→773 の表を引く。範囲外は {@link #DROP}。")
    out.append("     */")
    out.append("    public static int mapEnchantment767To773(int id767) {")
    out.append("        int id772 = mapEnchantment767To772(id767);")
    out.append("        return id772 == DROP ? DROP : mapEnchantment772To773(id772);")
    out.append("    }")
    out.append("")
    out.append("    /**")
    out.append("     * 773のエンチャントID → 767のエンチャントID。773→772 の逆変換の")
    out.append("     * 結果を 772→767 の逆変換で戻す。範囲外は {@link #DROP}。")
    out.append("     */")
    out.append("    public static int mapEnchantment773To767(int id773) {")
    out.append("        int id772 = mapEnchantment773To772(id773);")
    out.append("        return id772 == DROP ? DROP : mapEnchantment772To767(id772);")
    out.append("    }")
    out.append("")
    out.append("    /**")
    out.append("     * 766のエンチャントID → 773のエンチャントID。766→767 の変換")
    out.append("     * （{@link #mapEnchantment766To767}）を経由して 767→773 の")
    out.append("     * 表を引く。範囲外は {@link #DROP}。")
    out.append("     */")
    out.append("    public static int mapEnchantment766To773(int id766) {")
    out.append("        int id767 = mapEnchantment766To767(id766);")
    out.append("        return id767 == DROP ? DROP : mapEnchantment767To773(id767);")
    out.append("    }")
    out.append("")
    out.append("    /**")
    out.append("     * 773のエンチャントID → 766のエンチャントID。773→767 の逆変換の")
    out.append("     * 結果を 767→766 の逆変換で戻す。範囲外は {@link #DROP}。")
    out.append("     */")
    out.append("    public static int mapEnchantment773To766(int id773) {")
    out.append("        int id767 = mapEnchantment773To767(id773);")
    out.append("        return id767 == DROP ? DROP : mapEnchantment767To766(id767);")
    out.append("    }")
    out.append("")
    out.append("    /**")
    out.append("     * 765のエンチャントID → 773のエンチャントID。765→767 の変換")
    out.append("     * （{@link #mapEnchantment765To767}）を経由して 767→773 の")
    out.append("     * 表を引く。範囲外は {@link #DROP}。")
    out.append("     * 763／764 のエンチャントの並びは 765 と同一のため、")
    out.append("     * 763／764 向けも同じ変換を使う。")
    out.append("     */")
    out.append("    public static int mapEnchantment765To773(int id765) {")
    out.append("        int id767 = mapEnchantment765To767(id765);")
    out.append("        return id767 == DROP ? DROP : mapEnchantment767To773(id767);")
    out.append("    }")
    out.append("")
    out.append("    /** 763のエンチャントID → 773のエンチャントID（765と同じ並び）。 */")
    out.append("    public static int mapEnchantment763To773(int id763) {")
    out.append("        return mapEnchantment765To773(id763);")
    out.append("    }")
    out.append("")
    out.append("    /** 764のエンチャントID → 773のエンチャントID（765と同じ並び）。 */")
    out.append("    public static int mapEnchantment764To773(int id764) {")
    out.append("        return mapEnchantment765To773(id764);")
    out.append("    }")
    out.append("")
    out.append("    /**")
    out.append("     * 773のエンチャントID → 765のエンチャントID。773→767 の逆変換の")
    out.append("     * 結果を 767→765 の変換（{@link #mapEnchantment767To765}）で")
    out.append("     * 戻す。density／breach／wind_burst は 765に無いため")
    out.append("     * {@link #DROP}。範囲外も {@link #DROP}。")
    out.append("     */")
    out.append("    public static int mapEnchantment773To765(int id773) {")
    out.append("        int id767 = mapEnchantment773To767(id773);")
    out.append("        return id767 == DROP ? DROP : mapEnchantment767To765(id767);")
    out.append("    }")
    out.append("")
    out.append("    /** 773のエンチャントID → 763のエンチャントID（765と同じ並び）。 */")
    out.append("    public static int mapEnchantment773To763(int id773) {")
    out.append("        return mapEnchantment773To765(id773);")
    out.append("    }")
    out.append("")
    out.append("    /** 773のエンチャントID → 764のエンチャントID（765と同じ並び）。 */")
    out.append("    public static int mapEnchantment773To764(int id773) {")
    out.append("        return mapEnchantment773To765(id773);")
    out.append("    }")
    return "\n".join(out)


def up_accessors():
    out = ["    // ------------------------------------------------------------------",
           "    // 逆向き（773→772）。上の *_773 表を反転して作った表を使う。",
           "    // アイテムだけは生成スクリプトが名前から作った既存の逆変換表",
           "    // （ITEM_REVERSE_773_MAP）をそのまま使う（反転した表と完全一致",
           "    // することはテストで確認している）。属性とエンチャントの逆向きは",
           "    // mapAttribute773To772／mapEnchantment773To772 を参照。",
           "    // ------------------------------------------------------------------",
           ""]
    for kind in KINDS:
        name, prefix, jp = kind[0], kind[1], kind[2]
        # 逆向きの代替先は「772 側」の定数（kind[26]）。
        fb772 = kind[32]
        if prefix == "ITEM":
            out.append("    /**")
            out.append("     * 773のアイテムID → 772のアイテムID。")
            out.append("     * 773にしか無いアイテム72件（銅の道具5件・銅の防具4件・")
            out.append("     * 棚12件・銅の松明1件・銅の棒／鎖／ランタン／チェスト／ゴーレム像")
            out.append("     * 各8件・避雷針7件・銅の延べ棒・銅ゴーレムのスポーンエッグ・銅の")
            out.append("     * 馬鎧）は石へ。範囲外は石（1）。")
            out.append("     */")
            out.append("    public static int mapItemUp773(int id773) {")
            out.append("        if (id773 < 0 || id773 >= ITEM_REVERSE_773_MAP.length) {")
            out.append("            logUnknownUp773Once(\"アイテム\", id773);")
            out.append("            return STONE_ITEM_772;")
            out.append("        }")
            out.append("        int mapped = ITEM_REVERSE_773_MAP[id773];")
            out.append("        return mapped < 0 ? STONE_ITEM_772 : mapped;")
            out.append("    }")
            out.append("")
            continue
        out.append(f"    /** 773の{jp}ID → 772の{jp}ID。範囲外は{fb772} に相当する代替。 */")
        out.append(f"    public static int map{name}Up773(int id773) {{")
        out.append(f"        if (id773 < 0 || id773 > MAX_{prefix}_773) {{")
        out.append(f"            logUnknownUp773Once(\"{jp}\", id773);")
        out.append(f"            return {fb772};")
        out.append("        }")
        out.append(f"        return {prefix}_UP_773_MAP[id773];")
        out.append("    }")
        out.append("")
    out.append("    /**")
    out.append("     * 773のエフェクトID → 772のエフェクトID。0〜38 は同一のため恒等写像。")
    out.append("     * 範囲外は Speed（0）。")
    out.append("     */")
    out.append("    public static int mapEffectUp773(int id773) {")
    out.append("        if (id773 < 0 || id773 > MAX_EFFECT_773) {")
    out.append("            logUnknownUp773Once(\"エフェクト\", id773);")
    out.append("            return SPEED_EFFECT_772;")
    out.append("        }")
    out.append("        return id773;")
    out.append("    }")
    out.append("")
    out.append("    /**")
    out.append("     * 773のブロックエンティティ種別ID → 772の種別ID。")
    out.append("     * shelf（40）と copper_golem_statue（48）は 772 に無いため {@link #DROP}。")
    out.append("     * 41〜47 は -1 ずれる。40 より前はそのまま。")
    out.append("     * 範囲外の種別は NBT の中身が種別専用のため安全に代替できず")
    out.append("     * {@link #DROP}。")
    out.append("     */")
    out.append("    public static int mapBlockEntityTypeUp773(int id773) {")
    out.append("        if (id773 < 0 || id773 > BLOCK_ENTITY_COPPER_GOLEM_STATUE_773) {")
    out.append("            logUnknownUp773Once(\"ブロックエンティティ種別\", id773);")
    out.append("            return DROP;")
    out.append("        }")
    out.append("        if (id773 == BLOCK_ENTITY_SHELF_773 || id773 == BLOCK_ENTITY_COPPER_GOLEM_STATUE_773) {")
    out.append("            return DROP;")
    out.append("        }")
    out.append("        return id773 < BLOCK_ENTITY_SHELF_773 ? id773 : id773 - 1;")
    out.append("    }")
    out.append("")
    out.append("    /**")
    out.append("     * 音の名前を772向けに変換する。773の音の名前のうち 772に無いものは")
    out.append("     * 代替の音の名前にする（数値の反転表と一致させる）。773の新音")
    out.append("     * （銅ゴーレム・棚・銅のチェスト関連など43件）は石の破壊音へ。")
    out.append("     * {@code null} は石の破壊音にする。")
    out.append("     */")
    out.append("    public static String mapSoundNameUp773(String name) {")
    out.append("        return mapSoundNameComposed(name, SOUND_NAME_FALLBACK_773_TO_772_MAP);")
    out.append("    }")
    return "\n".join(out)
def composed_accessors():
    out = ["    // ------------------------------------------------------------------",
           "    // 離れた組み合わせの合成表（763↔773、764↔773、765↔773、766↔773、",
           "    // 767↔773、768↔773、769↔773、770↔773、771↔773）。上の *_763_TO_773／",
           "    // *_773_TO_763／*_764_TO_773／*_773_TO_764／*_765_TO_773／",
           "    // *_773_TO_765／*_766_TO_773／*_773_TO_766／*_767_TO_773／",
           "    // *_773_TO_767／*_768_TO_773／*_773_TO_768／*_769_TO_773／",
           "    // *_773_TO_769／*_770_TO_773／*_773_TO_770／*_771_TO_773／",
           "    // *_773_TO_771 表",
           "    // （合成済みの1つの表）",
           "    // を直接引く。二段変換はしない。エフェクトとブロックエンティティ",
           "    // 種別は表を作らず、数値の規則と同一の並びで扱う。",
           "    // ------------------------------------------------------------------",
           ""]
    pairs = [
        ("763", 23, "763→773", "773→763"),
        ("764", 24, "764→773", "773→764"),
        ("765", 25, "765→773", "773→765"),
        ("766", 26, "766→773", "773→766"),
        ("767", 27, "767→773", "773→767"),
        ("768", 28, "768→773", "773→768"),
        ("769", 29, "769→773", "773→769"),
        ("770", 30, "770→773", "773→770"),
        ("771", 31, "771→773", "773→771"),
    ]
    # 772 は隣接表（772↔773）で扱うため合成のペアには含めない。
    for src, fb_src_idx, up_label, down_label in pairs:
        for kind in KINDS:
            name, prefix, jp = kind[0], kind[1], kind[2]
            fb_src = kind[fb_src_idx]
            fb_773 = kind[33]
            # src → 773
            out.append(f"    /** {src}の{jp}ID → 773の{jp}ID。範囲外は{fb_773} に相当する代替。 */")
            out.append(f"    public static int map{name}{src}To773(int id{src}) {{")
            out.append(f"        if (id{src} < 0 || id{src} >= {prefix}_{src}_TO_773_MAP.length) {{")
            out.append(f"            logUnknownComposedOnce(\"{up_label}\", \"{jp}\", id{src});")
            out.append(f"            return {fb_773};")
            out.append("        }")
            out.append(f"        return {prefix}_{src}_TO_773_MAP[id{src}];")
            out.append("    }")
            out.append("")
            # 773 → src
            out.append(f"    /** 773の{jp}ID → {src}の{jp}ID。範囲外は{fb_src} に相当する代替。 */")
            out.append(f"    public static int map{name}773To{src}(int id773) {{")
            out.append(f"        if (id773 < 0 || id773 >= {prefix}_773_TO_{src}_MAP.length) {{")
            out.append(f"            logUnknownComposedOnce(\"{down_label}\", \"{jp}\", id773);")
            out.append(f"            return {fb_src};")
            out.append("        }")
            out.append(f"        return {prefix}_773_TO_{src}_MAP[id773];")
            out.append("    }")
            out.append("")
        # エフェクトとブロックエンティティ種別と音の名前。
        if src == "763":
            out.append("    /**")
            out.append("     * 763のエフェクトID → 773のエフェクトID。763は1ベース")
            out.append("     * （Speed=1）のため 1 引く。範囲外は Speed（0）。")
            out.append("     */")
            out.append("    public static int mapEffect763To773(int id763) {")
            out.append("        if (id763 < 1 || id763 > MAX_EFFECT_765 + 1) {")
            out.append("            logUnknownComposedOnce(\"763→773\", \"エフェクト\", id763);")
            out.append("            return SPEED_EFFECT_773;")
            out.append("        }")
            out.append("        return id763 - 1;")
            out.append("    }")
            out.append("")
            out.append("    /**")
            out.append("     * 773のエフェクトID → 763のエフェクトID。763は1ベース")
            out.append("     * （Speed=1）のため 1 足す。773で増えた 33〜38 は")
            out.append("     * 763に無いため Speed（1）へ。範囲外も Speed（1）。")
            out.append("     */")
            out.append("    public static int mapEffect773To763(int id773) {")
            out.append("        if (id773 < 0 || id773 > MAX_EFFECT_773) {")
            out.append("            logUnknownComposedOnce(\"773→763\", \"エフェクト\", id773);")
            out.append("            return SPEED_EFFECT_763;")
            out.append("        }")
            out.append("        return id773 <= MAX_EFFECT_765 ? id773 + 1 : SPEED_EFFECT_763;")
            out.append("    }")
            out.append("")
            out.append("    /**")
            out.append("     * 763のブロックエンティティ種別ID → 773の種別ID。")
            out.append("     * 763→772 と 772→773（棚の挿入で 40 以降が +1）の連鎖。")
            out.append("     * 範囲外は {@link #DROP}。")
            out.append("     */")
            out.append("    public static int mapBlockEntityType763To773(int id763) {")
            out.append("        if (id763 < 0 || id763 > BLOCK_ENTITY_TRIAL_SPAWNER_765 - 2) {")
            out.append("            logUnknownComposedOnce(\"763→773\", \"ブロックエンティティ種別\", id763);")
            out.append("            return DROP;")
            out.append("        }")
            out.append("        return id763 < 10 ? id763 : id763 + 1;")
            out.append("    }")
            out.append("")
            out.append("    /**")
            out.append("     * 773のブロックエンティティ種別ID → 763の種別ID。")
            out.append("     * 773→772（棚・銅ゴーレム像は {@link #DROP}）と 772→763 の連鎖。")
            out.append("     * 範囲外は {@link #DROP}。")
            out.append("     */")
            out.append("    public static int mapBlockEntityType773To763(int id773) {")
            out.append("        if (id773 < 0 || id773 > BLOCK_ENTITY_TRIAL_SPAWNER_773 - 2) {")
            out.append("            logUnknownComposedOnce(\"773→763\", \"ブロックエンティティ種別\", id773);")
            out.append("            return DROP;")
            out.append("        }")
            out.append("        if (id773 == BLOCK_ENTITY_CREAKING_HEART_773) {")
            out.append("            return DROP;")
            out.append("        }")
            out.append("        return id773 < 10 ? id773 : id773 - 1;")
            out.append("    }")
            out.append("")
            out.append("    /**")
            out.append("     * 音の名前を773向けに変換する。763の音の名前のうち 768で削除された")
            out.append("     * ヤギの角笛の音2件と 770で削除された entity.wolf.howl と")
            out.append("     * 771で削除された leash_knot 系・puffer_fish.ambient・")
            out.append("     * block.sand.wind は代替の名前にする。{@code null} は石の破壊音にする。")
            out.append("     */")
            out.append("    public static String mapSoundName763To773(String name) {")
            out.append("        return mapSoundNameComposed(")
            out.append("                mapSoundNameComposed(")
            out.append("                        mapSoundNameComposed(")
            out.append("                                mapSoundNameComposed(")
            out.append("                                        mapSoundNameComposed(name, SOUND_NAME_FALLBACK_768_MAP),")
            out.append("                                SOUND_NAME_FALLBACK_770_MAP),")
            out.append("                        SOUND_NAME_FALLBACK_771_MAP),")
            out.append("                SOUND_NAME_FALLBACK_772_MAP),")
            out.append("        SOUND_NAME_FALLBACK_773_MAP);")
            out.append("    }")
            out.append("")
            out.append("    /**")
            out.append("     * 音の名前を763向けに変換する。773の音の名前のうち 763に無いものは")
            out.append("     * 代替の音の名前にする（数値の合成表と一致させる）。")
            out.append("     * {@code null} は石の破壊音にする。")
            out.append("     */")
            out.append("    public static String mapSoundName773To763(String name) {")
            out.append("        return mapSoundNameComposed(name, SOUND_NAME_FALLBACK_773_TO_763_MAP);")
            out.append("    }")
        elif src == "764":
            out.append("    /**")
            out.append("     * 764のエフェクトID → 773のエフェクトID。764も773も0ベース")
            out.append("     * （Speed は両方 0）で 0〜32 は同一のため恒等写像。")
            out.append("     * 範囲外は Speed（0）。")
            out.append("     */")
            out.append("    public static int mapEffect764To773(int id764) {")
            out.append("        if (id764 < 0 || id764 > MAX_EFFECT_764) {")
            out.append("            logUnknownComposedOnce(\"764→773\", \"エフェクト\", id764);")
            out.append("            return SPEED_EFFECT_773;")
            out.append("        }")
            out.append("        return id764;")
            out.append("    }")
            out.append("")
            out.append("    /**")
            out.append("     * 773のエフェクトID → 764のエフェクトID。0〜32 は同一のため恒等写像。")
            out.append("     * 773で増えた 33〜38 は 764に無いため Speed（0）へ。")
            out.append("     * 範囲外も Speed（0）。")
            out.append("     */")
            out.append("    public static int mapEffect773To764(int id773) {")
            out.append("        if (id773 < 0 || id773 > MAX_EFFECT_773) {")
            out.append("            logUnknownComposedOnce(\"773→764\", \"エフェクト\", id773);")
            out.append("            return SPEED_EFFECT_764;")
            out.append("        }")
            out.append("        return id773 <= MAX_EFFECT_764 ? id773 : SPEED_EFFECT_764;")
            out.append("    }")
            out.append("")
            out.append("    /**")
            out.append("     * 764のブロックエンティティ種別ID → 773の種別ID。")
            out.append("     * 764→772 と 772→773（棚の挿入で 40 以降が +1）の連鎖。")
            out.append("     * 範囲外は {@link #DROP}。")
            out.append("     */")
            out.append("    public static int mapBlockEntityType764To773(int id764) {")
            out.append("        if (id764 < 0 || id764 > BLOCK_ENTITY_TRIAL_SPAWNER_765 - 2) {")
            out.append("            logUnknownComposedOnce(\"764→773\", \"ブロックエンティティ種別\", id764);")
            out.append("            return DROP;")
            out.append("        }")
            out.append("        return id764 < 10 ? id764 : id764 + 1;")
            out.append("    }")
            out.append("")
            out.append("    /**")
            out.append("     * 773のブロックエンティティ種別ID → 764の種別ID。")
            out.append("     * 773→772（棚・銅ゴーレム像は {@link #DROP}）と 772→764 の連鎖。")
            out.append("     * 範囲外は {@link #DROP}。")
            out.append("     */")
            out.append("    public static int mapBlockEntityType773To764(int id773) {")
            out.append("        if (id773 < 0 || id773 > BLOCK_ENTITY_TRIAL_SPAWNER_773 - 2) {")
            out.append("            logUnknownComposedOnce(\"773→764\", \"ブロックエンティティ種別\", id773);")
            out.append("            return DROP;")
            out.append("        }")
            out.append("        if (id773 == BLOCK_ENTITY_CREAKING_HEART_773) {")
            out.append("            return DROP;")
            out.append("        }")
            out.append("        return id773 < 10 ? id773 : id773 - 1;")
            out.append("    }")
            out.append("")
            out.append("    /**")
            out.append("     * 音の名前を773向けに変換する。764の音の名前のうち 768で削除された")
            out.append("     * ヤギの角笛の音2件と 770で削除された entity.wolf.howl と")
            out.append("     * 771で削除された leash_knot 系・puffer_fish.ambient・")
            out.append("     * block.sand.wind は代替の名前にする。{@code null} は石の破壊音にする。")
            out.append("     */")
            out.append("    public static String mapSoundName764To773(String name) {")
            out.append("        return mapSoundNameComposed(")
            out.append("                mapSoundNameComposed(")
            out.append("                        mapSoundNameComposed(")
            out.append("                                mapSoundNameComposed(")
            out.append("                                        mapSoundNameComposed(name, SOUND_NAME_FALLBACK_768_MAP),")
            out.append("                                SOUND_NAME_FALLBACK_770_MAP),")
            out.append("                        SOUND_NAME_FALLBACK_771_MAP),")
            out.append("                SOUND_NAME_FALLBACK_772_MAP),")
            out.append("        SOUND_NAME_FALLBACK_773_MAP);")
            out.append("    }")
            out.append("")
            out.append("    /**")
            out.append("     * 音の名前を764向けに変換する。773の音の名前のうち 764に無いものは")
            out.append("     * 代替の音の名前にする（数値の合成表と一致させる）。")
            out.append("     * {@code null} は石の破壊音にする。")
            out.append("     */")
            out.append("    public static String mapSoundName773To764(String name) {")
            out.append("        return mapSoundNameComposed(name, SOUND_NAME_FALLBACK_773_TO_764_MAP);")
            out.append("    }")
        elif src == "765":
            out.append("    /**")
            out.append("     * 765のエフェクトID → 773のエフェクトID。765も773も0ベース")
            out.append("     * （Speed は両方 0）で 0〜32 は同一のため恒等写像。")
            out.append("     * 範囲外は Speed（0）。")
            out.append("     */")
            out.append("    public static int mapEffect765To773(int id765) {")
            out.append("        if (id765 < 0 || id765 > MAX_EFFECT_765) {")
            out.append("            logUnknownComposedOnce(\"765→773\", \"エフェクト\", id765);")
            out.append("            return SPEED_EFFECT_773;")
            out.append("        }")
            out.append("        return id765;")
            out.append("    }")
            out.append("")
            out.append("    /**")
            out.append("     * 773のエフェクトID → 765のエフェクトID。0〜32 は同一のため恒等写像。")
            out.append("     * 773で増えた 33〜38 は 765に無いため Speed（0）へ。")
            out.append("     * 範囲外も Speed（0）。")
            out.append("     */")
            out.append("    public static int mapEffect773To765(int id773) {")
            out.append("        if (id773 < 0 || id773 > MAX_EFFECT_773) {")
            out.append("            logUnknownComposedOnce(\"773→765\", \"エフェクト\", id773);")
            out.append("            return SPEED_EFFECT_765;")
            out.append("        }")
            out.append("        return id773 <= MAX_EFFECT_765 ? id773 : SPEED_EFFECT_765;")
            out.append("    }")
            out.append("")
            out.append("    /**")
            out.append("     * 765のブロックエンティティ種別ID → 773の種別ID。")
            out.append("     * 765→772 と 772→773（棚の挿入で 40 以降が +1）の連鎖。")
            out.append("     * 範囲外は {@link #DROP}。")
            out.append("     */")
            out.append("    public static int mapBlockEntityType765To773(int id765) {")
            out.append("        if (id765 < 0 || id765 > BLOCK_ENTITY_TRIAL_SPAWNER_765) {")
            out.append("            logUnknownComposedOnce(\"765→773\", \"ブロックエンティティ種別\", id765);")
            out.append("            return DROP;")
            out.append("        }")
            out.append("        return id765 < 10 ? id765 : id765 + 1;")
            out.append("    }")
            out.append("")
            out.append("    /**")
            out.append("     * 773のブロックエンティティ種別ID → 765の種別ID。")
            out.append("     * 773→772（棚・銅ゴーレム像は {@link #DROP}）と 772→765 の連鎖。")
            out.append("     * 範囲外は {@link #DROP}。")
            out.append("     */")
            out.append("    public static int mapBlockEntityType773To765(int id773) {")
            out.append("        if (id773 < 0 || id773 > BLOCK_ENTITY_TRIAL_SPAWNER_773) {")
            out.append("            logUnknownComposedOnce(\"773→765\", \"ブロックエンティティ種別\", id773);")
            out.append("            return DROP;")
            out.append("        }")
            out.append("        if (id773 == BLOCK_ENTITY_CREAKING_HEART_773) {")
            out.append("            return DROP;")
            out.append("        }")
            out.append("        return id773 < 10 ? id773 : id773 - 1;")
            out.append("    }")
            out.append("")
            out.append("    /**")
            out.append("     * 音の名前を773向けに変換する。765の音の名前のうち 766で改名された")
            out.append("     * entity.generic.wind_burst と 767で改名された trial_spawner の音2件は")
            out.append("     * 新しい名前にし、768で削除されたヤギの角笛の音2件と 770で削除された")
            out.append("     * entity.wolf.howl と 771で削除された leash_knot 系・")
            out.append("     * puffer_fish.ambient・block.sand.wind は代替の名前にする。")
            out.append("     * {@code null} は石の破壊音にする。")
            out.append("     */")
            out.append("    public static String mapSoundName765To773(String name) {")
            out.append("        return mapSoundNameComposed(")
            out.append("                mapSoundNameComposed(")
            out.append("                        mapSoundNameComposed(")
            out.append("                                mapSoundNameComposed(")
            out.append("                                        mapSoundNameComposed(")
            out.append("                                                mapSoundNameComposed(")
            out.append("                                                        mapSoundNameComposed(name, SOUND_NAME_FALLBACK_766_MAP),")
            out.append("                                                SOUND_NAME_FALLBACK_767_MAP),")
            out.append("                                        SOUND_NAME_FALLBACK_768_MAP),")
            out.append("                                SOUND_NAME_FALLBACK_770_MAP),")
            out.append("                        SOUND_NAME_FALLBACK_771_MAP),")
            out.append("                SOUND_NAME_FALLBACK_772_MAP),")
            out.append("        SOUND_NAME_FALLBACK_773_MAP);")
            out.append("    }")
            out.append("")
            out.append("    /**")
            out.append("     * 音の名前を765向けに変換する。773の音の名前のうち 765に無いものは")
            out.append("     * 代替の音の名前にする（数値の合成表と一致させる）。")
            out.append("     * {@code null} は石の破壊音にする。")
            out.append("     */")
            out.append("    public static String mapSoundName773To765(String name) {")
            out.append("        return mapSoundNameComposed(name, SOUND_NAME_FALLBACK_773_TO_765_MAP);")
            out.append("    }")
        elif src == "766":
            out.append("    /**")
            out.append("     * 766のエフェクトID → 773のエフェクトID。766も773も0ベース")
            out.append("     * （Speed は両方 0）で 0〜38 は同一のため恒等写像。")
            out.append("     * 範囲外は Speed（0）。")
            out.append("     */")
            out.append("    public static int mapEffect766To773(int id766) {")
            out.append("        if (id766 < 0 || id766 > MAX_EFFECT_766) {")
            out.append("            logUnknownComposedOnce(\"766→773\", \"エフェクト\", id766);")
            out.append("            return SPEED_EFFECT_773;")
            out.append("        }")
            out.append("        return id766;")
            out.append("    }")
            out.append("")
            out.append("    /**")
            out.append("     * 773のエフェクトID → 766のエフェクトID。0〜38 は同一のため恒等写像。")
            out.append("     * 範囲外は Speed（0）。")
            out.append("     */")
            out.append("    public static int mapEffect773To766(int id773) {")
            out.append("        if (id773 < 0 || id773 > MAX_EFFECT_773) {")
            out.append("            logUnknownComposedOnce(\"773→766\", \"エフェクト\", id773);")
            out.append("            return SPEED_EFFECT_766;")
            out.append("        }")
            out.append("        return id773;")
            out.append("    }")
            out.append("")
            out.append("    /**")
            out.append("     * 766のブロックエンティティ種別ID → 773の種別ID。")
            out.append("     * 766→772 と 772→773（棚の挿入で 40 以降が +1）の連鎖。")
            out.append("     * 範囲外は {@link #DROP}。")
            out.append("     */")
            out.append("    public static int mapBlockEntityType766To773(int id766) {")
            out.append("        if (id766 < 0 || id766 > BLOCK_ENTITY_VAULT_766) {")
            out.append("            logUnknownComposedOnce(\"766→773\", \"ブロックエンティティ種別\", id766);")
            out.append("            return DROP;")
            out.append("        }")
            out.append("        return id766 < 10 ? id766 : id766 + 1;")
            out.append("    }")
            out.append("")
            out.append("    /**")
            out.append("     * 773のブロックエンティティ種別ID → 766の種別ID。")
            out.append("     * 773→772（棚・銅ゴーレム像は {@link #DROP}）と 772→766 の連鎖。")
            out.append("     * 範囲外は {@link #DROP}。")
            out.append("     */")
            out.append("    public static int mapBlockEntityType773To766(int id773) {")
            out.append("        if (id773 < 0 || id773 > BLOCK_ENTITY_VAULT_773) {")
            out.append("            logUnknownComposedOnce(\"773→766\", \"ブロックエンティティ種別\", id773);")
            out.append("            return DROP;")
            out.append("        }")
            out.append("        if (id773 == BLOCK_ENTITY_CREAKING_HEART_773) {")
            out.append("            return DROP;")
            out.append("        }")
            out.append("        return id773 < 10 ? id773 : id773 - 1;")
            out.append("    }")
            out.append("")
            out.append("    /**")
            out.append("     * 音の名前を773向けに変換する。766の音の名前のうち 767で改名された")
            out.append("     * trial_spawner の音2件は新しい名前にし、768で削除されたヤギの角笛の音2件と")
            out.append("     * 770で削除された entity.wolf.howl と 771で削除された leash_knot 系・")
            out.append("     * puffer_fish.ambient・block.sand.wind は代替の名前にする。")
            out.append("     * {@code null} は石の破壊音にする。")
            out.append("     */")
            out.append("    public static String mapSoundName766To773(String name) {")
            out.append("        return mapSoundNameComposed(name, SOUND_NAME_FALLBACK_766_TO_773_MAP);")
            out.append("    }")
            out.append("")
            out.append("    /**")
            out.append("     * 音の名前を766向けに変換する。773の音の名前のうち 766に無いものは")
            out.append("     * 代替の音の名前にする（数値の合成表と一致させる）。")
            out.append("     * {@code null} は石の破壊音にする。")
            out.append("     */")
            out.append("    public static String mapSoundName773To766(String name) {")
            out.append("        return mapSoundNameComposed(name, SOUND_NAME_FALLBACK_773_TO_766_MAP);")
            out.append("    }")
        elif src == "768":
            out.append("    /**")
            out.append("     * 768のエフェクトID → 773のエフェクトID。768も773も0ベース")
            out.append("     * （Speed は両方 0）で 0〜38 は同一のため恒等写像。")
            out.append("     * 範囲外は Speed（0）。")
            out.append("     */")
            out.append("    public static int mapEffect768To773(int id768) {")
            out.append("        if (id768 < 0 || id768 > MAX_EFFECT_768) {")
            out.append("            logUnknownComposedOnce(\"768→773\", \"エフェクト\", id768);")
            out.append("            return SPEED_EFFECT_773;")
            out.append("        }")
            out.append("        return id768;")
            out.append("    }")
            out.append("")
            out.append("    /**")
            out.append("     * 773のエフェクトID → 768のエフェクトID。0〜38 は同一のため恒等写像。")
            out.append("     * 範囲外は Speed（0）。")
            out.append("     */")
            out.append("    public static int mapEffect773To768(int id773) {")
            out.append("        if (id773 < 0 || id773 > MAX_EFFECT_773) {")
            out.append("            logUnknownComposedOnce(\"773→768\", \"エフェクト\", id773);")
            out.append("            return SPEED_EFFECT_768;")
            out.append("        }")
            out.append("        return id773;")
            out.append("    }")
            out.append("")
            out.append("    /**")
            out.append("     * 768のブロックエンティティ種別ID → 773の種別ID。")
            out.append("     * 768→772 と 772→773（棚の挿入で 40 以降が +1）の連鎖。")
            out.append("     * 範囲外は {@link #DROP}。")
            out.append("     */")
            out.append("    public static int mapBlockEntityType768To773(int id768) {")
            out.append("        if (id768 < 0 || id768 > BLOCK_ENTITY_VAULT_768) {")
            out.append("            logUnknownComposedOnce(\"768→773\", \"ブロックエンティティ種別\", id768);")
            out.append("            return DROP;")
            out.append("        }")
            out.append("        return id768;")
            out.append("    }")
            out.append("")
            out.append("    /**")
            out.append("     * 773のブロックエンティティ種別ID → 768の種別ID。")
            out.append("     * 773→772（棚・銅ゴーレム像は {@link #DROP}）と 772→768 の連鎖。")
            out.append("     * 範囲外は {@link #DROP}。")
            out.append("     */")
            out.append("    public static int mapBlockEntityType773To768(int id773) {")
            out.append("        if (id773 < 0 || id773 > BLOCK_ENTITY_VAULT_773) {")
            out.append("            logUnknownComposedOnce(\"773→768\", \"ブロックエンティティ種別\", id773);")
            out.append("            return DROP;")
            out.append("        }")
            out.append("        return id773;")
            out.append("    }")
            out.append("")
            out.append("    /**")
            out.append("     * 音の名前を773向けに変換する。768の音の名前のうち 770で削除された")
            out.append("     * entity.wolf.howl と 771で削除された leash_knot 系・")
            out.append("     * puffer_fish.ambient・block.sand.wind は代替の名前にする。")
            out.append("     * {@code null} は石の破壊音にする。")
            out.append("     */")
            out.append("    public static String mapSoundName768To773(String name) {")
            out.append("        return mapSoundNameComposed(name, SOUND_NAME_FALLBACK_768_TO_773_MAP);")
            out.append("    }")
            out.append("")
            out.append("    /**")
            out.append("     * 音の名前を768向けに変換する。773の音の名前のうち 768に無いものは")
            out.append("     * 代替の音の名前にする（数値の合成表と一致させる）。")
            out.append("     * {@code null} は石の破壊音にする。")
            out.append("     */")
            out.append("    public static String mapSoundName773To768(String name) {")
            out.append("        return mapSoundNameComposed(name, SOUND_NAME_FALLBACK_773_TO_768_MAP);")
            out.append("    }")
        elif src == "767":
            out.append("    /**")
            out.append("     * 767のエフェクトID → 773のエフェクトID。767も773も0ベース")
            out.append("     * （Speed は両方 0）で 0〜38 は同一のため恒等写像。")
            out.append("     * 範囲外は Speed（0）。")
            out.append("     */")
            out.append("    public static int mapEffect767To773(int id767) {")
            out.append("        if (id767 < 0 || id767 > MAX_EFFECT_767) {")
            out.append("            logUnknownComposedOnce(\"767→773\", \"エフェクト\", id767);")
            out.append("            return SPEED_EFFECT_773;")
            out.append("        }")
            out.append("        return id767;")
            out.append("    }")
            out.append("")
            out.append("    /**")
            out.append("     * 773のエフェクトID → 767のエフェクトID。0〜38 は同一のため恒等写像。")
            out.append("     * 範囲外は Speed（0）。")
            out.append("     */")
            out.append("    public static int mapEffect773To767(int id773) {")
            out.append("        if (id773 < 0 || id773 > MAX_EFFECT_773) {")
            out.append("            logUnknownComposedOnce(\"773→767\", \"エフェクト\", id773);")
            out.append("            return SPEED_EFFECT_767;")
            out.append("        }")
            out.append("        return id773;")
            out.append("    }")
            out.append("")
            out.append("    /**")
            out.append("     * 767のブロックエンティティ種別ID → 773の種別ID。")
            out.append("     * 767→772 と 772→773（棚の挿入で 40 以降が +1）の連鎖。")
            out.append("     * 範囲外は {@link #DROP}。")
            out.append("     */")
            out.append("    public static int mapBlockEntityType767To773(int id767) {")
            out.append("        if (id767 < 0 || id767 > BLOCK_ENTITY_VAULT_766) {")
            out.append("            logUnknownComposedOnce(\"767→773\", \"ブロックエンティティ種別\", id767);")
            out.append("            return DROP;")
            out.append("        }")
            out.append("        return id767 < 10 ? id767 : id767 + 1;")
            out.append("    }")
            out.append("")
            out.append("    /**")
            out.append("     * 773のブロックエンティティ種別ID → 767の種別ID。")
            out.append("     * 773→772（棚・銅ゴーレム像は {@link #DROP}）と 772→767 の連鎖。")
            out.append("     * 範囲外は {@link #DROP}。")
            out.append("     */")
            out.append("    public static int mapBlockEntityType773To767(int id773) {")
            out.append("        if (id773 < 0 || id773 > BLOCK_ENTITY_VAULT_773) {")
            out.append("            logUnknownComposedOnce(\"773→767\", \"ブロックエンティティ種別\", id773);")
            out.append("            return DROP;")
            out.append("        }")
            out.append("        if (id773 == BLOCK_ENTITY_CREAKING_HEART_773) {")
            out.append("            return DROP;")
            out.append("        }")
            out.append("        return id773 < 10 ? id773 : id773 - 1;")
            out.append("    }")
            out.append("")
            out.append("    /**")
            out.append("     * 音の名前を773向けに変換する。767の音の名前のうち 768で削除された")
            out.append("     * ヤギの角笛の音2件と 770で削除された entity.wolf.howl と")
            out.append("     * 771で削除された leash_knot 系・puffer_fish.ambient・")
            out.append("     * block.sand.wind は代替の名前にする。")
            out.append("     * {@code null} は石の破壊音にする。")
            out.append("     */")
            out.append("    public static String mapSoundName767To773(String name) {")
            out.append("        return mapSoundNameComposed(name, SOUND_NAME_FALLBACK_767_TO_773_MAP);")
            out.append("    }")
            out.append("")
            out.append("    /**")
            out.append("     * 音の名前を767向けに変換する。773の音の名前のうち 767に無いものは")
            out.append("     * 代替の音の名前にする（数値の合成表と一致させる）。")
            out.append("     * {@code null} は石の破壊音にする。")
            out.append("     */")
            out.append("    public static String mapSoundName773To767(String name) {")
            out.append("        return mapSoundNameComposed(name, SOUND_NAME_FALLBACK_773_TO_767_MAP);")
            out.append("    }")
        elif src == "769":
            out.append("    /**")
            out.append("     * 769のエフェクトID → 773のエフェクトID。769も773も0ベース")
            out.append("     * （Speed は両方 0）で 0〜38 は同一のため恒等写像。")
            out.append("     * 範囲外は Speed（0）。")
            out.append("     */")
            out.append("    public static int mapEffect769To773(int id769) {")
            out.append("        if (id769 < 0 || id769 > MAX_EFFECT_769) {")
            out.append("            logUnknownComposedOnce(\"769→773\", \"エフェクト\", id769);")
            out.append("            return SPEED_EFFECT_773;")
            out.append("        }")
            out.append("        return id769;")
            out.append("    }")
            out.append("")
            out.append("    /**")
            out.append("     * 773のエフェクトID → 769のエフェクトID。0〜38 は同一のため恒等写像。")
            out.append("     * 範囲外は Speed（0）。")
            out.append("     */")
            out.append("    public static int mapEffect773To769(int id773) {")
            out.append("        if (id773 < 0 || id773 > MAX_EFFECT_773) {")
            out.append("            logUnknownComposedOnce(\"773→769\", \"エフェクト\", id773);")
            out.append("            return SPEED_EFFECT_769;")
            out.append("        }")
            out.append("        return id773;")
            out.append("    }")
            out.append("")
            out.append("    /**")
            out.append("     * 769のブロックエンティティ種別ID → 773の種別ID。")
            out.append("     * 769→772 と 772→773（棚の挿入で 40 以降が +1）の連鎖。")
            out.append("     * 範囲外は {@link #DROP}。")
            out.append("     */")
            out.append("    public static int mapBlockEntityType769To773(int id769) {")
            out.append("        if (id769 < 0 || id769 > BLOCK_ENTITY_VAULT_769) {")
            out.append("            logUnknownComposedOnce(\"769→773\", \"ブロックエンティティ種別\", id769);")
            out.append("            return DROP;")
            out.append("        }")
            out.append("        return id769;")
            out.append("    }")
            out.append("")
            out.append("    /**")
            out.append("     * 773のブロックエンティティ種別ID → 769の種別ID。")
            out.append("     * 773→772（棚・銅ゴーレム像は {@link #DROP}）と 772→769 の連鎖。")
            out.append("     * 範囲外は {@link #DROP}。")
            out.append("     */")
            out.append("    public static int mapBlockEntityType773To769(int id773) {")
            out.append("        if (id773 < 0 || id773 > BLOCK_ENTITY_VAULT_773) {")
            out.append("            logUnknownComposedOnce(\"773→769\", \"ブロックエンティティ種別\", id773);")
            out.append("            return DROP;")
            out.append("        }")
            out.append("        return id773;")
            out.append("    }")
            out.append("")
            out.append("    /**")
            out.append("     * 音の名前を773向けに変換する。769の音の名前のうち 770で削除された")
            out.append("     * entity.wolf.howl と 771で削除された leash_knot 系・")
            out.append("     * puffer_fish.ambient・block.sand.wind は代替の名前にする。")
            out.append("     * {@code null} は石の破壊音にする。")
            out.append("     */")
            out.append("    public static String mapSoundName769To773(String name) {")
            out.append("        return mapSoundNameComposed(name, SOUND_NAME_FALLBACK_769_TO_773_MAP);")
            out.append("    }")
            out.append("")
            out.append("    /**")
            out.append("     * 音の名前を769向けに変換する。773の音の名前のうち 769に無いものは")
            out.append("     * 代替の音の名前にする（数値の合成表と一致させる）。")
            out.append("     * {@code null} は石の破壊音にする。")
            out.append("     */")
            out.append("    public static String mapSoundName773To769(String name) {")
            out.append("        return mapSoundNameComposed(name, SOUND_NAME_FALLBACK_773_TO_769_MAP);")
            out.append("    }")
        elif src == "770":
            out.append("    /**")
            out.append("     * 770のエフェクトID → 773のエフェクトID。770も773も0ベース")
            out.append("     * （Speed は両方 0）で 0〜38 は同一のため恒等写像。")
            out.append("     * 範囲外は Speed（0）。")
            out.append("     */")
            out.append("    public static int mapEffect770To773(int id770) {")
            out.append("        if (id770 < 0 || id770 > MAX_EFFECT_770) {")
            out.append("            logUnknownComposedOnce(\"770→773\", \"エフェクト\", id770);")
            out.append("            return SPEED_EFFECT_773;")
            out.append("        }")
            out.append("        return id770;")
            out.append("    }")
            out.append("")
            out.append("    /**")
            out.append("     * 773のエフェクトID → 770のエフェクトID。0〜38 は同一のため恒等写像。")
            out.append("     * 範囲外は Speed（0）。")
            out.append("     */")
            out.append("    public static int mapEffect773To770(int id773) {")
            out.append("        if (id773 < 0 || id773 > MAX_EFFECT_773) {")
            out.append("            logUnknownComposedOnce(\"773→770\", \"エフェクト\", id773);")
            out.append("            return SPEED_EFFECT_770;")
            out.append("        }")
            out.append("        return id773;")
            out.append("    }")
            out.append("")
            out.append("    /**")
            out.append("     * 770のブロックエンティティ種別ID → 773の種別ID。")
            out.append("     * 770→772 と 772→773（棚の挿入で 40 以降が +1）の連鎖。")
            out.append("     * 範囲外は {@link #DROP}。")
            out.append("     */")
            out.append("    public static int mapBlockEntityType770To773(int id770) {")
            out.append("        if (id770 < 0 || id770 > BLOCK_ENTITY_TEST_INSTANCE_BLOCK_770) {")
            out.append("            logUnknownComposedOnce(\"770→773\", \"ブロックエンティティ種別\", id770);")
            out.append("            return DROP;")
            out.append("        }")
            out.append("        return id770;")
            out.append("    }")
            out.append("")
            out.append("    /**")
            out.append("     * 773のブロックエンティティ種別ID → 770の種別ID。")
            out.append("     * 773→772（棚・銅ゴーレム像は {@link #DROP}）と 772→770 の連鎖。")
            out.append("     * 範囲外は {@link #DROP}。")
            out.append("     */")
            out.append("    public static int mapBlockEntityType773To770(int id773) {")
            out.append("        if (id773 < 0 || id773 > BLOCK_ENTITY_TEST_INSTANCE_BLOCK_773) {")
            out.append("            logUnknownComposedOnce(\"773→770\", \"ブロックエンティティ種別\", id773);")
            out.append("            return DROP;")
            out.append("        }")
            out.append("        return id773;")
            out.append("    }")
            out.append("")
            out.append("    /**")
            out.append("     * 音の名前を773向けに変換する。770の音の名前のうち 771で削除された")
            out.append("     * entity.leash_knot.break／place・entity.puffer_fish.ambient・")
            out.append("     * block.sand.wind は代替の名前にする。")
            out.append("     * {@code null} は石の破壊音にする。")
            out.append("     */")
            out.append("    public static String mapSoundName770To773(String name) {")
            out.append("        return mapSoundNameComposed(name, SOUND_NAME_FALLBACK_770_TO_773_MAP);")
            out.append("    }")
            out.append("")
            out.append("    /**")
            out.append("     * 音の名前を770向けに変換する。773の音の名前のうち 770に無いものは")
            out.append("     * 代替の音の名前にする（数値の合成表と一致させる）。")
            out.append("     * {@code null} は石の破壊音にする。")
            out.append("     */")
            out.append("    public static String mapSoundName773To770(String name) {")
            out.append("        return mapSoundNameComposed(name, SOUND_NAME_FALLBACK_773_TO_770_MAP);")
            out.append("    }")
        elif src == "771":
            out.append("    /**")
            out.append("     * 771のエフェクトID → 773のエフェクトID。771も773も0ベース")
            out.append("     * （Speed は両方 0）で 0〜38 は同一のため恒等写像。")
            out.append("     * 範囲外は Speed（0）。")
            out.append("     */")
            out.append("    public static int mapEffect771To773(int id771) {")
            out.append("        if (id771 < 0 || id771 > MAX_EFFECT_771) {")
            out.append("            logUnknownComposedOnce(\"771→773\", \"エフェクト\", id771);")
            out.append("            return SPEED_EFFECT_773;")
            out.append("        }")
            out.append("        return id771;")
            out.append("    }")
            out.append("")
            out.append("    /**")
            out.append("     * 773のエフェクトID → 771のエフェクトID。0〜38 は同一のため恒等写像。")
            out.append("     * 範囲外は Speed（0）。")
            out.append("     */")
            out.append("    public static int mapEffect773To771(int id773) {")
            out.append("        if (id773 < 0 || id773 > MAX_EFFECT_773) {")
            out.append("            logUnknownComposedOnce(\"773→771\", \"エフェクト\", id773);")
            out.append("            return SPEED_EFFECT_771;")
            out.append("        }")
            out.append("        return id773;")
            out.append("    }")
            out.append("")
            out.append("    /**")
            out.append("     * 771のブロックエンティティ種別ID → 773の種別ID。")
            out.append("     * 771→772（恒等）と 772→773（棚の挿入で 40 以降が +1）の連鎖。")
            out.append("     * 範囲外は {@link #DROP}。")
            out.append("     */")
            out.append("    public static int mapBlockEntityType771To773(int id771) {")
            out.append("        if (id771 < 0 || id771 > BLOCK_ENTITY_TEST_INSTANCE_BLOCK_771) {")
            out.append("            logUnknownComposedOnce(\"771→773\", \"ブロックエンティティ種別\", id771);")
            out.append("            return DROP;")
            out.append("        }")
            out.append("        return mapBlockEntityType772To773(mapBlockEntityType771To772(id771));")
            out.append("    }")
            out.append("")
            out.append("    /**")
            out.append("     * 773のブロックエンティティ種別ID → 771の種別ID。")
            out.append("     * 773→772（棚・銅ゴーレム像は {@link #DROP}）と 772→771（恒等）の連鎖。")
            out.append("     * 範囲外は {@link #DROP}。")
            out.append("     */")
            out.append("    public static int mapBlockEntityType773To771(int id773) {")
            out.append("        if (id773 < 0 || id773 > BLOCK_ENTITY_COPPER_GOLEM_STATUE_773) {")
            out.append("            logUnknownComposedOnce(\"773→771\", \"ブロックエンティティ種別\", id773);")
            out.append("            return DROP;")
            out.append("        }")
            out.append("        return mapBlockEntityType772To771(mapBlockEntityType773To772(id773));")
            out.append("    }")
            out.append("")
            out.append("    /**")
            out.append("     * 音の名前を773向けに変換する。771の音の名前は 773 にすべてあるため")
            out.append("     * 代替は無い。{@code null} は石の破壊音にする。")
            out.append("     */")
            out.append("    public static String mapSoundName771To773(String name) {")
            out.append("        return mapSoundNameComposed(name, SOUND_NAME_FALLBACK_771_TO_773_MAP);")
            out.append("    }")
            out.append("")
            out.append("    /**")
            out.append("     * 音の名前を771向けに変換する。773の音の名前のうち 771に無いものは")
            out.append("     * 代替の音の名前にする（数値の合成表と一致させる）。")
            out.append("     * {@code null} は石の破壊音にする。")
            out.append("     */")
            out.append("    public static String mapSoundName773To771(String name) {")
            out.append("        return mapSoundNameComposed(name, SOUND_NAME_FALLBACK_773_TO_771_MAP);")
            out.append("    }")
    return "\n".join(out)

# バナー・絵画・装飾パターン・ダメージ種別の 773 向け（772 と同一のものは
# 恒等写像。ダメージ種別も 772 と同一の 49 件）。
DAMAGE_TYPE_773 = """
    /** 773のダメージ種別最大ID（49件。772と同一）。 */
    public static final int MAX_DAMAGE_TYPE_773 = 48;

    /** ダメージ種別ID：772 → 773（772と同一の 49 件で並びも同じため恒等写像）。
     * 実サーバーの Registry Data での確認は統合テストで行う。 */
    private static final int[] DAMAGE_TYPE_773_MAP = {
        0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19,
        20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32, 33, 34, 35, 36,
        37, 38, 39, 40, 41, 42, 43, 44, 45, 46, 47, 48
    };

    /** ダメージ種別ID：773 → 772（772と同一の 49 件で並びも同じため恒等写像）。 */
    private static final int[] DAMAGE_TYPE_REVERSE_773_MAP = {
        0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19,
        20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32, 33, 34, 35, 36,
        37, 38, 39, 40, 41, 42, 43, 44, 45, 46, 47, 48
    };
"""

SMALL_REGISTRY_CONSTANTS = """
    /** 773のバナーパターン最大ID（43件。772と同一と仮定。実バニラの
        Registry Data での確認は統合テストで行う）。 */
    public static final int MAX_BANNER_PATTERN_773 = 42;

    /** 773の絵画バリアント最大ID（51件。772と同一。実バニラの
        Registry Data での確認は統合テストで行う）。 */
    public static final int MAX_PAINTING_VARIANT_773 = 50;

    /** 773の装飾パターン最大ID（18件。772と同一と仮定。実バニラの
        Registry Data での確認は統合テストで行う）。 */
    public static final int MAX_TRIM_PATTERN_773 = 17;
"""


def small_registry_accessors():
    out = []
    specs = [
        ("BannerPattern", "バナーパターン", "MAX_BANNER_PATTERN_772", "MAX_BANNER_PATTERN_773"),
        ("PaintingVariant", "絵画バリアント", "MAX_PAINTING_VARIANT_772", "MAX_PAINTING_VARIANT_773"),
        ("TrimPattern", "装飾パターン", "MAX_TRIM_PATTERN_772", "MAX_TRIM_PATTERN_773"),
    ]
    for name, jp, max772, max773 in specs:
        out.append(f"    /** 772の{jp}ID → 773の{jp}ID。772と773で同一のため恒等写像。範囲外は {{@link #DROP}}。 */")
        out.append(f"    public static int map{name}772To773(int id772) {{")
        out.append(f"        if (id772 < 0 || id772 > {max772}) {{")
        out.append(f"            logUnknownOnce(PROTOCOL_773, \"{jp}\", id772);")
        out.append("            return DROP;")
        out.append("        }")
        out.append("        return id772;")
        out.append("    }")
        out.append("")
        out.append(f"    /** 773の{jp}ID → 772の{jp}ID。772と773で同一のため恒等写像。範囲外は {{@link #DROP}}。 */")
        out.append(f"    public static int map{name}773To772(int id773) {{")
        out.append(f"        if (id773 < 0 || id773 > {max773}) {{")
        out.append(f"            logUnknownOnce(PROTOCOL_773, \"{jp}\", id773);")
        out.append("            return DROP;")
        out.append("        }")
        out.append("        return id773;")
        out.append("    }")
        out.append("")
        for src in ("763", "764", "765", "766", "767", "768", "769", "770", "771"):
            # 絵画バリアントだけ 771 を経由する（763／764／765／766／767／768／769／770→772 と
            # 772→763／764／765／766／767／768／769／770 は無い。771→772 は隣接で既にある）。
            # バナー・装飾パターンは 772 を経由する（{src}→772 は既にある）。
            hub = "771" if name == "PaintingVariant" and src != "771" else "772"
            out.append(f"    /** {src}の{jp}ID → 773の{jp}ID（{hub}経由）。 */")
            out.append(f"    public static int map{name}{src}To773(int id{src}) {{")
            out.append(f"        return map{name}{hub}To773(map{name}{src}To{hub}(id{src}));")
            out.append("    }")
            out.append("")
            out.append(f"    /** 773の{jp}ID → {src}の{jp}ID（{hub}経由）。 */")
            out.append(f"    public static int map{name}773To{src}(int id773) {{")
            out.append(f"        return map{name}{hub}To{src}(map{name}773To{hub}(id773));")
            out.append("    }")
            out.append("")
    return "\n".join(out)


def damage_accessors():
    out = []
    out.append("    /** 772のダメージ種別ID → 773のダメージ種別ID。772と同一のため恒等写像。範囲外は {@link #DROP}。 */")
    out.append("    public static int mapDamageType772To773(int id772) {")
    out.append("        if (id772 < 0 || id772 > MAX_DAMAGE_TYPE_772) {")
    out.append("            logUnknownOnce(PROTOCOL_773, \"ダメージ種別\", id772);")
    out.append("            return DROP;")
    out.append("        }")
    out.append("        return DAMAGE_TYPE_773_MAP[id772];")
    out.append("    }")
    out.append("")
    out.append("    /** 773のダメージ種別ID → 772のダメージ種別ID。772と同一のため恒等写像。範囲外は {@link #DROP}。 */")
    out.append("    public static int mapDamageType773To772(int id773) {")
    out.append("        if (id773 < 0 || id773 > MAX_DAMAGE_TYPE_773) {")
    out.append("            logUnknownOnce(PROTOCOL_773, \"ダメージ種別\", id773);")
    out.append("            return DROP;")
    out.append("        }")
    out.append("        return DAMAGE_TYPE_REVERSE_773_MAP[id773];")
    out.append("    }")
    out.append("")
    for src in ("763", "764", "765", "766", "767", "768", "769", "770", "771"):
        # ダメージ種別は 772 のメソッドを経由する。
        out.append(f"    /** {src}のダメージ種別ID → 773のダメージ種別ID（772経由）。 */")
        out.append(f"    public static int mapDamageType{src}To773(int id{src}) {{")
        out.append(f"        return mapDamageType772To773(mapDamageType{src}To772(id{src}));")
        out.append("    }")
        out.append("")
        out.append(f"    /** 773のダメージ種別ID → {src}のダメージ種別ID（772経由）。 */")
        out.append(f"    public static int mapDamageType773To{src}(int id773) {{")
        out.append(f"        return mapDamageType772To{src}(mapDamageType773To772(id773));")
        out.append("    }")
        out.append("")
    return "\n".join(out)


JAVADOC_773 = """ *
 * <p>773（1.21.9／1.21.10）向けの表も同じ作り方で持つ（{@code *_773}）。
 * 773は772に近く、差分は次の通り（ViaVersion mapping-1.21.9.json と
 * mapping-1.21.7.json、minecraft-data 1.21.9 の突き合わせ）。
 * ブロック状態は新1725件・削除0件（銅の棚・銅の松明・銅の棒・銅の鎖・
 * 銅のランタン・銅のチェスト・銅ゴーレム像などの追加と、挿入による番号の
 * ずれ）。ブロックは新61件・削除0件（chain→iron_chain の改名1件あり）。
 * アイテムは新73件・削除0件（銅の道具・防具・棚・銅の松明類・銅の延べ棒・
 * 銅ゴーレムのスポーンエッグ・銅の馬鎧など。chain→iron_chain の改名あり）。
 * エンティティは新2件（copper_golem・mannequin）。音は新43件・削除0件
 * （銅ゴーレム・棚・銅のチェスト関連など）。パーティクルは新1件
 * （copper_fire_flame）。ブロックエンティティ種別は新2件（shelf が 40 に
 * 挿入され、copper_golem_statue を 48 に追加して 49 件）。
 * メニュー・バイオーム・エフェクト・エンチャント・属性・データコンポーネントは
 * 同一。絵画バリアント・ジュークボックスの歌・ダメージ種別・バナーパターン・
 * 装飾パターンも 772 と同一（実バニラの Registry Data での確認は統合テストで
 * 行う）。
 * 772→773 の隣接表と 763／764／765／766／767／768／769／770／771→773 の合成表を持ち、
 * 逆向き 773→772 は反転して作る。
"""


def insert_after(java, anchor, block, label):
    if anchor not in java:
        print(f"アンカーが見つからない（{label}）", file=sys.stderr)
        return None
    return java.replace(anchor, anchor + block, 1)


def main():
    if (not TABLES773_FILE.exists() or not TABLES773_COMPOSED_FILE.exists()
            or not all(p.exists() for p in TABLES773_COMPOSED_DOWN_FILES.values())):
        print("RegistryTables773.java / RegistryTables773ComposedUp.java / "
              "RegistryTables773Down*.java が無い。"
              "先に insert-773-tables.py を実行すること", file=sys.stderr)
        return 1
    java = JAVA_FILE.read_text(encoding="utf-8")
    checks = [
        ("PROTOCOL_773", "773 の定数・表・アクセサは既にある"),
        ("BLOCK_STATE_773_MAP", "773 の展開済み表は既にある"),
        ("mapBlockState772To773", "773 のアクセサは既にある"),
    ]
    for marker, msg in checks:
        if marker in java:
            print(msg, file=sys.stderr)
            return 1

    # import static。
    java = insert_after(java,
                        "import static io.detergentmc.protocol.RegistryTables772Down770.*;\n",
                        "import static io.detergentmc.protocol.RegistryTables773.*;\n"
                        "import static io.detergentmc.protocol.RegistryTables773ComposedUp.*;\n"
                        "import static io.detergentmc.protocol.RegistryTables773Down763.*;\n"
                        "import static io.detergentmc.protocol.RegistryTables773Down764.*;\n"
                        "import static io.detergentmc.protocol.RegistryTables773Down765.*;\n"
                        "import static io.detergentmc.protocol.RegistryTables773Down766.*;\n"
                        "import static io.detergentmc.protocol.RegistryTables773Down767.*;\n"
                        "import static io.detergentmc.protocol.RegistryTables773Down768.*;\n"
                        "import static io.detergentmc.protocol.RegistryTables773Down769.*;\n"
                        "import static io.detergentmc.protocol.RegistryTables773Down770.*;\n"
                        "import static io.detergentmc.protocol.RegistryTables773Down771.*;\n",
                        "import static RegistryTables773")
    if java is None:
        return 1

    # クラスの javadoc に 773 の段落を追加（772 の段落の直後）。
    anchor = (" * 771→772 の隣接表と 763／764／765／766／767／768／769／770→772 の合成表を持ち、\n"
              " * 逆向き 772→771 は反転して作る。\n")
    java = insert_after(java, anchor, JAVADOC_773, "javadoc")
    if java is None:
        return 1

    # 定数。
    java = insert_after(java,
                        "    public static final int PROTOCOL_772 = 772;\n",
                        "    public static final int PROTOCOL_773 = 773;\n", "PROTOCOL_773")
    if java is None:
        return 1
    java = insert_after(java,
                        "    public static final int MAX_EFFECT_772 = 38;\n",
                        CONSTANTS_MAX, "MAX_773")
    if java is None:
        return 1
    java = insert_after(java,
                        "    public static final int SPEED_EFFECT_772 = 0;\n",
                        CONSTANTS_FALLBACK, "773 の代替先")
    if java is None:
        return 1
    java = insert_after(java,
                        "    public static final int BLOCK_ENTITY_VAULT_772 = 44;\n",
                        CONSTANTS_BLOCK_ENTITY, "773 のブロックエンティティ種別")
    if java is None:
        return 1

    # 隣接表（772→773）。MENU_772_MAP の直後。
    anchor = ("    private static final int[] MENU_771_MAP =\n"
              "            expand(MENU_771_RANGES, MENU_771_OVERRIDES, MAX_MENU_770 + 1,\n"
              "                    CRAFTING_MENU_771);\n")
    java = insert_after(java, anchor, adjacent_maps() + "\n", "隣接表 773")
    if java is None:
        return 1

    # 合成表。MENU_771_TO_769_MAP の直後。
    anchor = ("    private static final int[] MENU_771_TO_769_MAP =\n"
              "            expand(MENU_771_TO_769_RANGES, MENU_771_TO_769_OVERRIDES,\n"
              "                    25, CRAFTING_MENU_769);\n")
    java = insert_after(java, anchor, "\n" + composed_maps() + "\n", "合成表 773")
    if java is None:
        return 1

    # 反転表（773→772）。MENU_UP_772_MAP の直後。
    anchor = ("    private static final int[] MENU_UP_771_MAP = invert(\n"
              "            MENU_771_RANGES, MENU_771_OVERRIDES,\n"
              "            MAX_MENU_770 + 1, MAX_MENU_771 + 1,\n"
              "            CRAFTING_MENU_770, null);\n")
    java = insert_after(java, anchor, up_maps() + "\n", "反転表 773")
    if java is None:
        return 1

    # 音の名前の表。
    anchor = ("    private static final Map<String, String> SOUND_NAME_FALLBACK_772_TO_766_MAP =\n"
              "            buildSoundNameMap(SOUND_NAME_FALLBACKS_772_TO_766);\n")
    java = insert_after(java, anchor, SOUND_NAME_MAPS, "音の名前の表 773")
    if java is None:
        return 1

    # ログ。
    anchor = ("    private static void logUnknownUp772Once(String kind, int id772) {\n"
              "        if (LOGGED_UNKNOWN.add(\"up772:\" + kind + \":\" + id772)) {\n"
              "            LOG.warning(\"772に無い\" + kind + \"の番号が来たため代替に落とします（772 id=\" + id772 + \"）。\");\n"
              "        }\n"
              "    }\n")
    java = insert_after(java, anchor, LOG_UP_773, "logUnknownUp773Once")
    if java is None:
        return 1

    # アクセサ（a）772→773。mapSoundName767To772 の直後。
    anchor = ("    public static String mapSoundName767To772(String name) {\n"
              "        return mapSoundNameComposed(name, SOUND_NAME_FALLBACK_767_TO_772_MAP);\n"
              "    }\n")
    java = insert_after(java, anchor, "\n" + adjacent_accessors() + "\n", "アクセサ 772→773")
    if java is None:
        return 1

    # アクセサ（b）773→772。mapSoundNameUp772 の直後。
    anchor = ("    public static String mapSoundNameUp772(String name) {\n"
              "        return mapSoundNameComposed(name, SOUND_NAME_FALLBACK_772_TO_771_MAP);\n"
              "    }\n")
    java = insert_after(java, anchor, "\n" + up_accessors() + "\n", "アクセサ 773→772")
    if java is None:
        return 1

    # アクセサ（c）763／764／765／766／767／768／769／770／771↔773。mapSoundName772To766 の直後。
    anchor = ("    public static String mapSoundName772To766(String name) {\n"
              "        return mapSoundNameComposed(name, SOUND_NAME_FALLBACK_772_TO_766_MAP);\n"
              "    }\n")
    java = insert_after(java, anchor, "\n" + composed_accessors() + "\n", "アクセサ 763/764/765/766/767↔773")
    if java is None:
        return 1

    # バナー・絵画・装飾パターン・ダメージ種別の定数。
    java = insert_after(java,
                        "    /** 770のバナーパターン最大ID（43件。769と同一と仮定。実バニラの\n"
                        "        Registry Data での確認は統合テストで行う）。 */\n"
                        "    public static final int MAX_BANNER_PATTERN_770 = 42;\n",
                        SMALL_REGISTRY_CONSTANTS, "バナー等の773定数")
    if java is None:
        return 1
    anchor = ("    private static final int[] DAMAGE_TYPE_REVERSE_772_MAP = {\n"
              "        0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19,\n"
              "        20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32, 33, 34, 35, 36,\n"
              "        37, 38, 39, 40, 41, 42, 43, 44, 45, 46, 47, 48\n"
              "    };\n")
    java = insert_after(java, anchor, DAMAGE_TYPE_773, "ダメージ種別773の表")
    if java is None:
        return 1

    # バナー・絵画・装飾パターン・ダメージ種別のアクセサ。
    anchor = ("    /** 770のバナーパターンID → 766のバナーパターンID（769経由）。 */\n"
              "    public static int mapBannerPattern770To766(int id770) {\n"
              "        return mapBannerPattern769To766(mapBannerPattern770To769(id770));\n"
              "    }\n")
    banner_painting_trim = small_registry_accessors()
    java = insert_after(java, anchor, "\n" + banner_painting_trim + "\n", "バナー等の773アクセサ")
    if java is None:
        return 1
    anchor = ("    /** 770のダメージ種別ID → 766のダメージ種別ID（769経由）。 */\n"
              "    public static int mapDamageType770To766(int id770) {\n"
              "        return mapDamageType769To766(mapDamageType770To769(id770));\n"
              "    }\n")
    java = insert_after(java, anchor, "\n" + damage_accessors() + "\n", "ダメージ種別773のアクセサ")
    if java is None:
        return 1

    # プロトコル番号で切り替える変換（担当2の配線用）に 773 を追加。
    dispatches = [
        ("mapBlockState", "mapBlockState765To772(id765)", "mapBlockState765To773(id765)"),
        ("mapItem", "mapItem765To772(id765)", "mapItem765To773(id765)"),
        ("mapItemReverse", "mapItem772To765(id)", "mapItem773To765(id)"),
        ("mapEntityType", "mapEntityType765To772(id765)", "mapEntityType765To773(id765)"),
        ("mapSound", "mapSound765To772(id765)", "mapSound765To773(id765)"),
        ("mapParticle", "mapParticle765To772(id765)", "mapParticle765To773(id765)"),
        ("mapBlock", "mapBlock765To772(id765)", "mapBlock765To773(id765)"),
        ("mapMenu", "mapMenu765To772(id765)", "mapMenu765To773(id765)"),
        ("mapEffect", "mapEffect765To772(id765)", "mapEffect765To773(id765)"),
        ("mapBlockEntityType", "mapBlockEntityType765To772(id765)", "mapBlockEntityType765To773(id765)"),
        ("mapSoundName", "mapSoundName765To772(name)", "mapSoundName765To773(name)"),
    ]
    for label, old_call, new_call in dispatches:
        if label == "mapItemReverse":
            old = ("        if (clientProtocol == PROTOCOL_772) {\n"
                   "            // 772クライアントのアイテムID → 765サーバーのアイテムID。\n"
                   "            // mapItemReverse772 は 772→771 のため、ここでは合成表を使う。\n"
                   "            return mapItem772To765(id);\n"
                   "        }\n")
            new = (old
                   + "        if (clientProtocol == PROTOCOL_773) {\n"
                   "            // 773クライアントのアイテムID → 765サーバーのアイテムID。\n"
                   "            // mapItemReverse773 は 773→772 のため、ここでは合成表を使う。\n"
                   "            return mapItem773To765(id);\n"
                   "        }\n")
            if old not in java:
                print("ディスパッチのアンカーが見つからない（mapItemReverse）", file=sys.stderr)
                return 1
            java = java.replace(old, new, 1)
            continue
        old = ("        if (clientProtocol == PROTOCOL_772) {\n"
               f"            return {old_call};\n"
               "        }\n")
        if old not in java:
            print(f"ディスパッチのアンカーが見つからない（{label}）", file=sys.stderr)
            return 1
        java = java.replace(old, old
                            + "        if (clientProtocol == PROTOCOL_773) {\n"
                            + f"            return {new_call};\n"
                            + "        }\n", 1)
    java = java.replace(
        "    // 765は素通し、766は766の表、767は767の表、768は768の表、\n"
        "    // 769は769の表、770は770の表、771は771の表、772は772の表、それより古い未知の番号は763の表を使う。\n"
        "    // 767／768／769／770／771／772は765以上だが素通しではない（>765 の分岐より\n"
        "    // 先に 767／768／769／770／771／772 を判定すること）。",
        "    // 765は素通し、766は766の表、767は767の表、768は768の表、\n"
        "    // 769は769の表、770は770の表、771は771の表、772は772の表、773は773の表、それより古い未知の番号は763の表を使う。\n"
        "    // 767／768／769／770／771／772／773は765以上だが素通しではない（>765 の分岐より\n"
        "    // 先に 767／768／769／770／771／772／773 を判定すること）。",
        1)

    JAVA_FILE.write_text(java, encoding="utf-8")
    print("RegistryRemapper.java に 773 の定数・表・アクセサを挿入した")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
