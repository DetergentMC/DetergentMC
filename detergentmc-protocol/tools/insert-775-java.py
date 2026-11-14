#!/usr/bin/env python3
"""tools/insert-774-java.py で入れた *_774 表の上に、RegistryRemapper.java
へ 775 対応の定数・展開済み表（*_775_MAP）・反転表（*_UP_775_MAP）・
アクセサを挿入する（1回だけの作業用）。

  - 定数：PROTOCOL_775、MAX_*_775、775 側の代替先。
  - 隣接表（774→775）: *_775_MAP と ITEM_REVERSE_775_MAP と
    ATTRIBUTE_775_MAP／ATTRIBUTE_REVERSE_775_MAP／ENCHANTMENT_775_MAP／
    ENCHANTMENT_REVERSE_775_MAP。
  - 合成表（763↔775、764↔775、765↔775、766↔775、767↔775、768↔775、769↔775、770↔775、771↔775、772↔775、773↔775）: *_763_TO_775_MAP など154件。
  - 774↔775 は隣接表（*_775_MAP）と反転表（*_UP_775_MAP）で扱うため合成表は作らない（774と同じ方針）。
  - 逆向き（775→774）: *_UP_775_MAP（invert()。アイテムは
    ITEM_REVERSE_775_MAP をそのまま使う。774→775 に補正は無い）。
  - 音の名前の表（String）: SOUND_NAME_FALLBACK_*_775_MAP など21件。
  - アクセサ：774↔775、775→774（上向き）、763／764／765／766／767／768／769／770／771／772／773↔775。
    離れた組み合わせのエフェクト・ブロックエンティティ種別・音の名前は
    774 のメソッドへ委譲する（774→775 は隣接のため）。
  - エフェクト：775 は 774 と同一の 40 件（0〜39）のため恒等写像。
    属性は 774・775 とも 35 件で同一、エンチャントは 43 件同士で
    並びは同一（minecraft-data の 26.1 の enchantments で確認）。
    ブロックエンティティ種別は 775 は 49 件と仮定（774 と同一。
    実バニラの Registry Data での確認は統合テストで行う）。
    バナー・絵画・装飾パターンは 774 と同一と仮定。
    ダメージ種別も 774 と同一の 50 件と仮定。
    バイオームは 774・775 とも 65 件で同一の並び（minecraft-data の
    26.1 の biomes で確認。実バニラの Registry Data での確認は統合テストで行う）。
  - パーティクルの代替先は entity_effect=21（773 で 1 件挿入され
    20→21 にずれた。773 の定数は 20 のままで end_rod を指して
    おり誤りの疑いがある（775 の作業では直さない）。774 の定数は
    775 の作業で 21 に直した。生成器の UP_775_FALLBACKS も 21 に
    直してある）。
  - 注意：このスクリプトで挿入した展開済み表（隣接12・合成154・上向き6の
    計172件）は、そのままでは RegistryRemapper.java の <clinit> が 64KB を
    超えてコンパイルできない。そのため挿入後に
    tools/move-775-expanded-tables.py で RegistryTables775Expanded.java
    へ移動してある。再実行時はこの移動もやり直すこと。
"""

import re
import sys
from pathlib import Path

SCRIPT_DIR = Path(__file__).resolve().parent
REPO_ROOT = SCRIPT_DIR.parent.parent
JAVA_FILE = (REPO_ROOT / "detergentmc-protocol" / "src" / "main" / "java"
             / "io" / "detergentmc" / "protocol" / "RegistryRemapper.java")
TABLES775_FILE = (REPO_ROOT / "detergentmc-protocol" / "src" / "main" / "java"
                  / "io" / "detergentmc" / "protocol" / "RegistryTables775.java")
TABLES775_COMPOSED_FILE = (REPO_ROOT / "detergentmc-protocol" / "src" / "main" / "java"
                  / "io" / "detergentmc" / "protocol" / "RegistryTables775ComposedUpA.java")
TABLES775_COMPOSED_DOWN_FILES = {
    suffix: (REPO_ROOT / "detergentmc-protocol" / "src" / "main" / "java"
             / "io" / "detergentmc" / "protocol"
             / f"RegistryTables775Down{suffix}.java")
    for suffix in ("763", "764", "765", "766", "767", "768", "769", "770", "771", "772", "773")
}


KINDS = [
    # kind: (Java メソッド名の語尾, RANGES の接頭辞, 日本語名,
    #        763→775 の件数, 775→763 の件数, 764→775 の件数, 775→764 の件数,
    #        765→775 の件数, 775→765 の件数, 766→775 の件数, 775→766 の件数,
    #        767→775 の件数, 775→767 の件数, 768→775 の件数, 775→768 の件数,
    #        769→775 の件数, 775→769 の件数, 770→775 の件数, 775→770 の件数,
    #        771→775 の件数, 775→771 の件数, 772→775 の件数, 775→772 の件数,
    #        773→775 の件数, 775→773 の件数,
    #        763 側の代替先, 764 側の代替先, 765 側の代替先, 766 側の代替先,
    #        767 側の代替先, 768 側の代替先, 769 側の代替先, 770 側の代替先,
    #        771 側の代替先, 772 側の代替先, 773 側の代替先, 774 側の代替先, 775 側の代替先)
    ("BlockState", "BLOCK_STATE", "ブロック状態", 24135, 29873, 24276, 29873, 26644, 29873, 26684, 29873, 26684, 29873, 27318, 29873, 27866, 29873, 27914, 29873, 27946, 29873, 27946, 29873, 29671, 29873,
     "STONE_BLOCK_STATE_763", "STONE_BLOCK_STATE_764", "STONE_BLOCK_STATE_765", "STONE_BLOCK_STATE_766", "STONE_BLOCK_STATE_767", "STONE_BLOCK_STATE_768", "STONE_BLOCK_STATE_769", "STONE_BLOCK_STATE_770", "STONE_BLOCK_STATE_771", "STONE_BLOCK_STATE_772", "STONE_BLOCK_STATE_773", "STONE_BLOCK_STATE_774", "STONE_BLOCK_STATE_775"),
    ("Item", "ITEM", "アイテム", 1255, 1506, 1255, 1506, 1312, 1506, 1330, 1506, 1333, 1506, 1375, 1506, 1385, 1506, 1396, 1506, 1415, 1506, 1416, 1506, 1488, 1506,
     "STONE_ITEM_763", "STONE_ITEM_764", "STONE_ITEM_765", "STONE_ITEM_766", "STONE_ITEM_767", "STONE_ITEM_768", "STONE_ITEM_769", "STONE_ITEM_770", "STONE_ITEM_771", "STONE_ITEM_772", "STONE_ITEM_773", "STONE_ITEM_774", "STONE_ITEM_775"),
    ("EntityType", "ENTITY_TYPE", "エンティティ種別", 124, 157, 124, 157, 126, 157, 130, 157, 130, 157, 150, 157, 149, 157, 150, 157, 151, 157, 151, 157, 153, 157,
     "PIG_ENTITY_TYPE_763", "PIG_ENTITY_TYPE_764", "PIG_ENTITY_TYPE_765", "PIG_ENTITY_TYPE_766", "PIG_ENTITY_TYPE_767", "PIG_ENTITY_TYPE_768", "PIG_ENTITY_TYPE_769", "PIG_ENTITY_TYPE_770", "PIG_ENTITY_TYPE_771", "PIG_ENTITY_TYPE_772", "PIG_ENTITY_TYPE_773", "PIG_ENTITY_TYPE_774", "PIG_ENTITY_TYPE_775"),
    ("Sound", "SOUND", "音", 1474, 1902, 1485, 1902, 1539, 1902, 1607, 1902, 1611, 1902, 1636, 1902, 1651, 1902, 1702, 1902, 1727, 1902, 1728, 1902, 1771, 1902,
     "GENERIC_SOUND_763", "GENERIC_SOUND_764", "GENERIC_SOUND_765", "GENERIC_SOUND_766", "GENERIC_SOUND_767", "GENERIC_SOUND_768", "GENERIC_SOUND_769", "GENERIC_SOUND_770", "GENERIC_SOUND_771", "GENERIC_SOUND_772", "GENERIC_SOUND_773", "GENERIC_SOUND_774", "GENERIC_SOUND_775"),
    ("Particle", "PARTICLE", "パーティクル", 95, 117, 95, 117, 101, 117, 109, 117, 109, 117, 111, 117, 112, 117, 114, 117, 114, 117, 114, 117, 115, 117,
     "AMBIENT_PARTICLE_763", "AMBIENT_PARTICLE_764", "AMBIENT_PARTICLE_765", "ENTITY_EFFECT_PARTICLE_766", "ENTITY_EFFECT_PARTICLE_767", "ENTITY_EFFECT_PARTICLE_768", "ENTITY_EFFECT_PARTICLE_769", "ENTITY_EFFECT_PARTICLE_770", "ENTITY_EFFECT_PARTICLE_771", "ENTITY_EFFECT_PARTICLE_772", "ENTITY_EFFECT_PARTICLE_773", "ENTITY_EFFECT_PARTICLE_774", "ENTITY_EFFECT_PARTICLE_775"),
    ("Block", "BLOCK", "ブロック", 1003, 1168, 1003, 1168, 1058, 1168, 1060, 1168, 1060, 1168, 1084, 1168, 1095, 1168, 1104, 1168, 1105, 1168, 1105, 1168, 1166, 1168,
     "STONE_BLOCK_763", "STONE_BLOCK_764", "STONE_BLOCK_765", "STONE_BLOCK_766", "STONE_BLOCK_767", "STONE_BLOCK_768", "STONE_BLOCK_769", "STONE_BLOCK_770", "STONE_BLOCK_771", "STONE_BLOCK_772", "STONE_BLOCK_773", "STONE_BLOCK_774", "STONE_BLOCK_775"),
    ("Menu", "MENU", "メニュー種別", 24, 25, 24, 25, 25, 25, 25, 25, 25, 25, 25, 25, 25, 25, 25, 25, 25, 25, 25, 25, 25, 25,
     "CRAFTING_MENU_763", "CRAFTING_MENU_764", "CRAFTING_MENU_765", "CRAFTING_MENU_766", "CRAFTING_MENU_767", "CRAFTING_MENU_768", "CRAFTING_MENU_769", "CRAFTING_MENU_770", "CRAFTING_MENU_771", "CRAFTING_MENU_772", "CRAFTING_MENU_773", "CRAFTING_MENU_774", "CRAFTING_MENU_775"),
]

CONSTANTS_MAX = """
    /** 775側の最大の番号（ViaVersion mapping-26.1.json の件数−1）。 */
    public static final int MAX_BLOCK_STATE_775 = 29872;
    public static final int MAX_ITEM_775 = 1505;
    public static final int MAX_ENTITY_TYPE_775 = 156;
    public static final int MAX_SOUND_775 = 1901;
    public static final int MAX_PARTICLE_775 = 116;
    public static final int MAX_BLOCK_775 = 1167;
    public static final int MAX_MENU_775 = 24;
    /** 775側のエフェクトは 0〜39（Speed=0。774 と同一の 40 件。
        minecraft-data の 26.1 の effects で確認）。 */
    public static final int MAX_EFFECT_775 = 39;
    /** 775側の属性は 0〜34（774 と同じ 35 件。
        ViaVersion mapping-26.1.json で確認）。 */
    public static final int MAX_ATTRIBUTE_775 = 34;
    /** エンチャントは 775 は 0〜42（43件。774 と同一の並び。
        minecraft-data の 26.1 の enchantments で確認。実機の Registry Data
        での確認は統合テストで行う）。 */
    public static final int MAX_ENCHANTMENT_775 = 42;
"""

CONSTANTS_FALLBACK = """
    /** 775側の代替先（mapping-26.1.json の番号。Speed だけ 0ベースで 0）。
     * ブタは 775 では 100（774 と同一。エンティティの並びは同一）、
     * 石の破壊音は 775 では 1592（新音 64 件のうち 57 件が 1535 より
     * 前に挿入され、774 の 1535 から +57）。
     * パーティクルの代替は entity_effect=21（773 で 1 件挿入され
     * 20→21 にずれた。773 の定数は 20 のままで end_rod を指して
     * おり誤りの疑いがある（775 の作業では直さない）。774 の定数は
     * 775 の作業で 21 に直した。775 では正しい 21 を使う）。 */
    public static final int STONE_BLOCK_STATE_775 = 1;
    public static final int AIR_BLOCK_STATE_775 = 0;
    public static final int STONE_ITEM_775 = 1;
    public static final int PIG_ENTITY_TYPE_775 = 100;
    public static final int GENERIC_SOUND_775 = 1592; // block.stone.break
    public static final int ENTITY_EFFECT_PARTICLE_775 = 21;
    public static final int STONE_BLOCK_775 = 1;
    public static final int CRAFTING_MENU_775 = 12;
    public static final int SPEED_EFFECT_775 = 0;
"""

CONSTANTS_BLOCK_ENTITY = """
    /** 775のブロックエンティティ種別：49 件（774 と同一と仮定。shelf=40、
        trial_spawner=44、vault=45、test_block=46、test_instance_block=47、
        copper_golem_statue=48。実バニラ 26.1 の Registry Data での確認は
        統合テストで行う）。 */
    public static final int BLOCK_ENTITY_SHELF_775 = 40;
    public static final int BLOCK_ENTITY_CREAKING_HEART_775 = 10;
    public static final int BLOCK_ENTITY_TRIAL_SPAWNER_775 = 44;
    public static final int BLOCK_ENTITY_VAULT_775 = 45;
    public static final int BLOCK_ENTITY_TEST_BLOCK_775 = 46;
    public static final int BLOCK_ENTITY_TEST_INSTANCE_BLOCK_775 = 47;
    public static final int BLOCK_ENTITY_COPPER_GOLEM_STATUE_775 = 48;
"""


def adjacent_maps():
    lines = []
    for kind in KINDS:
        name, prefix, jp = kind[0], kind[1], kind[2]
        lines.append(
            f"    private static final int[] {prefix}_775_MAP =\n"
            f"            expand({prefix}_775_RANGES, {prefix}_775_OVERRIDES, MAX_{prefix}_774 + 1,\n"
            f"                    {kind[37]});")
    lines.append(
        "    private static final int[] ITEM_REVERSE_775_MAP =\n"
        "            expand(ITEM_REVERSE_775_RANGES, ITEM_REVERSE_775_OVERRIDES, 1506, -1);")
    lines.append(
        "    private static final int[] ATTRIBUTE_775_MAP =\n"
        "            expand(ATTRIBUTE_775_RANGES, ATTRIBUTE_775_OVERRIDES, MAX_ATTRIBUTE_774 + 1, DROP);")
    lines.append(
        "    private static final int[] ATTRIBUTE_REVERSE_775_MAP =\n"
        "            expand(ATTRIBUTE_REVERSE_775_RANGES, ATTRIBUTE_REVERSE_775_OVERRIDES,\n"
        "                    MAX_ATTRIBUTE_775 + 1, DROP);")
    lines.append(
        "    private static final int[] ENCHANTMENT_775_MAP =\n"
        "            expand(ENCHANTMENT_775_RANGES, ENCHANTMENT_775_OVERRIDES,\n"
        "                    MAX_ENCHANTMENT_774 + 1, DROP);")
    lines.append(
        "    private static final int[] ENCHANTMENT_REVERSE_775_MAP =\n"
        "            expand(ENCHANTMENT_REVERSE_775_RANGES, ENCHANTMENT_REVERSE_775_OVERRIDES,\n"
        "                    MAX_ENCHANTMENT_775 + 1, DROP);")
    return "\n".join(lines)


def composed_maps():
    # (接尾辞, 件数の位置, 代替先の位置)。件数・代替先は KINDS の
    # タプルから取る（763_TO_775: 件数 index 3, 代替先 775=index 37／
    # 775_TO_763: 件数 index 4, 代替先 763=index 25）。件数は 3〜24、
    # 代替先は 25（763）〜37（775）。774↔775 は隣接表で扱うため含めない。
    pairs = [
        ("763_TO_775", 3, 37), ("775_TO_763", 4, 25),
        ("764_TO_775", 5, 37), ("775_TO_764", 6, 26),
        ("765_TO_775", 7, 37), ("775_TO_765", 8, 27),
        ("766_TO_775", 9, 37), ("775_TO_766", 10, 28),
        ("767_TO_775", 11, 37), ("775_TO_767", 12, 29),
        ("768_TO_775", 13, 37), ("775_TO_768", 14, 30),
        ("769_TO_775", 15, 37), ("775_TO_769", 16, 31),
        ("770_TO_775", 17, 37), ("775_TO_770", 18, 32),
        ("771_TO_775", 19, 37), ("775_TO_771", 20, 33),
        ("772_TO_775", 21, 37), ("775_TO_772", 22, 34),
        ("773_TO_775", 23, 37), ("775_TO_773", 24, 35),
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
            continue  # アイテムは ITEM_REVERSE_775_MAP を使う。
        # 774→775 に補正は無い（774→775 は改名・削除が無い）。反転はそのまま使う。
        corrections = "null"
        # 逆向きの代替先は「774 側」の定数（kind[36]）。
        fb774 = kind[36]
        lines.append(
            f"    private static final int[] {prefix}_UP_775_MAP = invert(\n"
            f"            {prefix}_775_RANGES, {prefix}_775_OVERRIDES,\n"
            f"            MAX_{prefix}_774 + 1, MAX_{prefix}_775 + 1,\n"
            f"            {fb774}, {corrections});")
    return "\n".join(lines)


SOUND_NAME_MAPS = """
    private static final Map<String, String> SOUND_NAME_FALLBACK_775_MAP =
            buildSoundNameMap(SOUND_NAME_FALLBACKS_775);
    private static final Map<String, String> SOUND_NAME_FALLBACK_774_TO_775_MAP =
            buildSoundNameMap(SOUND_NAME_FALLBACKS_774_TO_775);
    private static final Map<String, String> SOUND_NAME_FALLBACK_775_TO_774_MAP =
            buildSoundNameMap(SOUND_NAME_FALLBACKS_775_TO_774);
    private static final Map<String, String> SOUND_NAME_FALLBACK_775_TO_773_MAP =
            buildSoundNameMap(SOUND_NAME_FALLBACKS_775_TO_773);
    private static final Map<String, String> SOUND_NAME_FALLBACK_775_TO_772_MAP =
            buildSoundNameMap(SOUND_NAME_FALLBACKS_775_TO_772);
    private static final Map<String, String> SOUND_NAME_FALLBACK_775_TO_771_MAP =
            buildSoundNameMap(SOUND_NAME_FALLBACKS_775_TO_771);
    private static final Map<String, String> SOUND_NAME_FALLBACK_775_TO_770_MAP =
            buildSoundNameMap(SOUND_NAME_FALLBACKS_775_TO_770);
    private static final Map<String, String> SOUND_NAME_FALLBACK_775_TO_769_MAP =
            buildSoundNameMap(SOUND_NAME_FALLBACKS_775_TO_769);
    private static final Map<String, String> SOUND_NAME_FALLBACK_775_TO_768_MAP =
            buildSoundNameMap(SOUND_NAME_FALLBACKS_775_TO_768);
    private static final Map<String, String> SOUND_NAME_FALLBACK_775_TO_767_MAP =
            buildSoundNameMap(SOUND_NAME_FALLBACKS_775_TO_767);
    private static final Map<String, String> SOUND_NAME_FALLBACK_775_TO_766_MAP =
            buildSoundNameMap(SOUND_NAME_FALLBACKS_775_TO_766);
    private static final Map<String, String> SOUND_NAME_FALLBACK_775_TO_765_MAP =
            buildSoundNameMap(SOUND_NAME_FALLBACKS_775_TO_765);
    private static final Map<String, String> SOUND_NAME_FALLBACK_775_TO_764_MAP =
            buildSoundNameMap(SOUND_NAME_FALLBACKS_775_TO_764);
    private static final Map<String, String> SOUND_NAME_FALLBACK_775_TO_763_MAP =
            buildSoundNameMap(SOUND_NAME_FALLBACKS_775_TO_763);
    private static final Map<String, String> SOUND_NAME_FALLBACK_773_TO_775_MAP =
            buildSoundNameMap(SOUND_NAME_FALLBACKS_773_TO_775);
    private static final Map<String, String> SOUND_NAME_FALLBACK_772_TO_775_MAP =
            buildSoundNameMap(SOUND_NAME_FALLBACKS_772_TO_775);
    private static final Map<String, String> SOUND_NAME_FALLBACK_771_TO_775_MAP =
            buildSoundNameMap(SOUND_NAME_FALLBACKS_771_TO_775);
    private static final Map<String, String> SOUND_NAME_FALLBACK_770_TO_775_MAP =
            buildSoundNameMap(SOUND_NAME_FALLBACKS_770_TO_775);
    private static final Map<String, String> SOUND_NAME_FALLBACK_769_TO_775_MAP =
            buildSoundNameMap(SOUND_NAME_FALLBACKS_769_TO_775);
    private static final Map<String, String> SOUND_NAME_FALLBACK_768_TO_775_MAP =
            buildSoundNameMap(SOUND_NAME_FALLBACKS_768_TO_775);
    private static final Map<String, String> SOUND_NAME_FALLBACK_767_TO_775_MAP =
            buildSoundNameMap(SOUND_NAME_FALLBACKS_767_TO_775);
"""

LOG_UP_775 = """
    private static void logUnknownUp775Once(String kind, int id775) {
        if (LOGGED_UNKNOWN.add("up775:" + kind + ":" + id775)) {
            LOG.warning("775に無い" + kind + "の番号が来たため代替に落とします（775 id=" + id775 + "）。");
        }
    }
"""
def adjacent_accessors():
    out = ["    // ------------------------------------------------------------------",
           "    // 774→775。上の *_775 表を使う。ブロック状態・ブロック・",
           "    // パーティクル・メニュー・属性・バイオーム・エフェクト・",
           "    // エンチャントは同一。アイテムは新1件（金のタンポポ）、",
           "    // エンティティは同一、音は新64件の挿入でずれる。",
           "    // ブロックエンティティ種別は 774 と同一と仮定（49 件、",
           "    // 統合テストで確認）。絵画・ジュークボックスの歌は",
           "    // 774 と同一と仮定。",
           "    // ------------------------------------------------------------------",
           ""]
    for kind in KINDS:
        name, prefix, jp = kind[0], kind[1], kind[2]
        fb775 = kind[37]
        out.append(f"    /** 774の{jp}ID → 775の{jp}ID。範囲外は{fb775} に相当する代替。 */")
        out.append(f"    public static int map{name}774To775(int id774) {{")
        out.append(f"        if (id774 < 0 || id774 > MAX_{prefix}_774) {{")
        out.append(f"            logUnknownOnce(PROTOCOL_775, \"{jp}\", id774);")
        out.append(f"            return {fb775};")
        out.append("        }")
        out.append(f"        return {prefix}_775_MAP[id774];")
        out.append("    }")
        out.append("")
    out.append("    /**")
    out.append("     * 775のアイテムID → 774のアイテムID（サーバー行きスロット用）。")
    out.append("     * 775にしか無いアイテム1件（golden_dandelion）は 774の石へ。改名は無い。")
    out.append("     * 範囲外はそのまま返す。")
    out.append("     */")
    out.append("    public static int mapItemReverse775(int id775) {")
    out.append("        if (id775 < 0 || id775 >= ITEM_REVERSE_775_MAP.length) {")
    out.append("            logUnknownOnce(PROTOCOL_775, \"アイテム（逆変換）\", id775);")
    out.append("            return id775;")
    out.append("        }")
    out.append("        int mapped = ITEM_REVERSE_775_MAP[id775];")
    out.append("        return mapped < 0 ? id775 : mapped;")
    out.append("    }")
    out.append("")
    out.append("    /**")
    out.append("     * 774のエフェクトID → 775のエフェクトID。774も775も0ベース")
    out.append("     * （Speed は両方 0）で 0〜39 は同一のため恒等写像。")
    out.append("     * 範囲外は Speed（0）。")
    out.append("     */")
    out.append("    public static int mapEffect774To775(int id774) {")
    out.append("        if (id774 < 0 || id774 > MAX_EFFECT_774) {")
    out.append("            logUnknownOnce(PROTOCOL_775, \"エフェクト\", id774);")
    out.append("            return SPEED_EFFECT_775;")
    out.append("        }")
    out.append("        return id774;")
    out.append("    }")
    out.append("")
    out.append("    /**")
    out.append("     * 774のブロックエンティティ種別ID → 775の種別ID。")
    out.append("     * 774 と 775 で同一と仮定（49 件。統合テストで確認）。")
    out.append("     * 範囲外の種別は NBT の中身が種別専用のため安全に代替できず")
    out.append("     * {@link #DROP}。")
    out.append("     */")
    out.append("    public static int mapBlockEntityType774To775(int id774) {")
    out.append("        if (id774 < 0 || id774 > BLOCK_ENTITY_COPPER_GOLEM_STATUE_774) {")
    out.append("            logUnknownOnce(PROTOCOL_775, \"ブロックエンティティ種別\", id774);")
    out.append("            return DROP;")
    out.append("        }")
    out.append("        return id774;")
    out.append("    }")
    out.append("")
    out.append("    /**")
    out.append("     * 音の名前（直書き・Stop Sound 用）を775向けに変換する。")
    out.append("     * {@code minecraft:} の有無はそのまま保つ。774の音の名前は")
    out.append("     * 775 にすべてあるため代替は無い。")
    out.append("     */")
    out.append("    public static String mapSoundName774To775(String name) {")
    out.append("        return mapSoundNameComposed(name, SOUND_NAME_FALLBACK_774_TO_775_MAP);")
    out.append("    }")
    out.append("")
    out.append("    /** 774の属性ID → 775の属性ID。並びは同一。範囲外は {@link #DROP}。 */")
    out.append("    public static int mapAttribute774To775(int id774) {")
    out.append("        if (id774 < 0 || id774 > MAX_ATTRIBUTE_774) {")
    out.append("            logUnknownOnce(PROTOCOL_775, \"属性\", id774);")
    out.append("            return DROP;")
    out.append("        }")
    out.append("        return ATTRIBUTE_775_MAP[id774];")
    out.append("    }")
    out.append("")
    out.append("    /** 775の属性ID → 774の属性ID。並びは同一。範囲外は {@link #DROP}。 */")
    out.append("    public static int mapAttribute775To774(int id775) {")
    out.append("        if (id775 < 0 || id775 > MAX_ATTRIBUTE_775) {")
    out.append("            logUnknownOnce(PROTOCOL_775, \"属性\", id775);")
    out.append("            return DROP;")
    out.append("        }")
    out.append("        return ATTRIBUTE_REVERSE_775_MAP[id775];")
    out.append("    }")
    out.append("")
    out.append("    /** 775の属性ID → 属性の名前（Update Attributes の 774向け変換用）。 */")
    out.append("    public static String attributeName775(int id775) {")
    out.append("        if (id775 < 0 || id775 >= ATTRIBUTE_775_NAMES.length) {")
    out.append("            return null;")
    out.append("        }")
    out.append("        return ATTRIBUTE_775_NAMES[id775];")
    out.append("    }")
    out.append("")
    out.append("    /**")
    out.append("     * 属性の名前 → 775の属性ID（765／763／764 は属性が名前形式のため、")
    out.append("     * 775向けに変換するときに使う）。無い名前は {@link #DROP}。")
    out.append("     */")
    out.append("    public static int attributeId775(String name) {")
    out.append("        if (name == null) {")
    out.append("            return DROP;")
    out.append("        }")
    out.append("        String plain = stripMinecraftPrefix(name);")
    out.append("        for (int i = 0; i < ATTRIBUTE_775_NAMES.length; i++) {")
    out.append("            if (ATTRIBUTE_775_NAMES[i].equals(plain)) {")
    out.append("                return i;")
    out.append("            }")
    out.append("        }")
    out.append("        return DROP;")
    out.append("    }")
    out.append("")
    out.append("    /** 774のエンチャントID → 775のエンチャントID。並びは同一。範囲外は {@link #DROP}。 */")
    out.append("    public static int mapEnchantment774To775(int id774) {")
    out.append("        if (id774 < 0 || id774 > MAX_ENCHANTMENT_774) {")
    out.append("            logUnknownOnce(PROTOCOL_775, \"エンチャント\", id774);")
    out.append("            return DROP;")
    out.append("        }")
    out.append("        return ENCHANTMENT_775_MAP[id774];")
    out.append("    }")
    out.append("")
    out.append("    /** 775のエンチャントID → 774のエンチャントID。並びは同一。範囲外は {@link #DROP}。 */")
    out.append("    public static int mapEnchantment775To774(int id775) {")
    out.append("        if (id775 < 0 || id775 > MAX_ENCHANTMENT_775) {")
    out.append("            logUnknownOnce(PROTOCOL_775, \"エンチャント\", id775);")
    out.append("            return DROP;")
    out.append("        }")
    out.append("        return ENCHANTMENT_REVERSE_775_MAP[id775];")
    out.append("    }")
    out.append("")
    out.append("    /**")
    out.append("     * 767のエンチャントID → 775のエンチャントID。767→774 の表を")
    out.append("     * 経由して 774→775 の表を引く。範囲外は {@link #DROP}。")
    out.append("     */")
    out.append("    public static int mapEnchantment767To775(int id767) {")
    out.append("        int id774 = mapEnchantment767To774(id767);")
    out.append("        return id774 == DROP ? DROP : mapEnchantment774To775(id774);")
    out.append("    }")
    out.append("")
    out.append("    /**")
    out.append("     * 775のエンチャントID → 767のエンチャントID。775→774 の逆変換の")
    out.append("     * 結果を 774→767 の逆変換で戻す。範囲外は {@link #DROP}。")
    out.append("     */")
    out.append("    public static int mapEnchantment775To767(int id775) {")
    out.append("        int id774 = mapEnchantment775To774(id775);")
    out.append("        return id774 == DROP ? DROP : mapEnchantment774To767(id774);")
    out.append("    }")
    out.append("")
    out.append("    /**")
    out.append("     * 766のエンチャントID → 775のエンチャントID。766→767 の変換")
    out.append("     * （{@link #mapEnchantment766To767}）を経由して 767→775 の")
    out.append("     * 表を引く。範囲外は {@link #DROP}。")
    out.append("     */")
    out.append("    public static int mapEnchantment766To775(int id766) {")
    out.append("        int id767 = mapEnchantment766To767(id766);")
    out.append("        return id767 == DROP ? DROP : mapEnchantment767To775(id767);")
    out.append("    }")
    out.append("")
    out.append("    /**")
    out.append("     * 775のエンチャントID → 766のエンチャントID。775→767 の逆変換の")
    out.append("     * 結果を 767→766 の逆変換で戻す。範囲外は {@link #DROP}。")
    out.append("     */")
    out.append("    public static int mapEnchantment775To766(int id775) {")
    out.append("        int id767 = mapEnchantment775To767(id775);")
    out.append("        return id767 == DROP ? DROP : mapEnchantment767To766(id767);")
    out.append("    }")
    out.append("")
    out.append("    /**")
    out.append("     * 765のエンチャントID → 775のエンチャントID。765→767 の変換")
    out.append("     * （{@link #mapEnchantment765To767}）を経由して 767→775 の")
    out.append("     * 表を引く。範囲外は {@link #DROP}。")
    out.append("     * 763／764 のエンチャントの並びは 765 と同一のため、")
    out.append("     * 763／764 向けも同じ変換を使う。")
    out.append("     */")
    out.append("    public static int mapEnchantment765To775(int id765) {")
    out.append("        int id767 = mapEnchantment765To767(id765);")
    out.append("        return id767 == DROP ? DROP : mapEnchantment767To775(id767);")
    out.append("    }")
    out.append("")
    out.append("    /** 763のエンチャントID → 775のエンチャントID（765と同じ並び）。 */")
    out.append("    public static int mapEnchantment763To775(int id763) {")
    out.append("        return mapEnchantment765To775(id763);")
    out.append("    }")
    out.append("")
    out.append("    /** 764のエンチャントID → 775のエンチャントID（765と同じ並び）。 */")
    out.append("    public static int mapEnchantment764To775(int id764) {")
    out.append("        return mapEnchantment765To775(id764);")
    out.append("    }")
    out.append("")
    out.append("    /**")
    out.append("     * 775のエンチャントID → 765のエンチャントID。775→767 の逆変換の")
    out.append("     * 結果を 767→765 の変換（{@link #mapEnchantment767To765}）で")
    out.append("     * 戻す。density／breach／wind_burst は 765に無いため")
    out.append("     * {@link #DROP}。範囲外も {@link #DROP}。")
    out.append("     */")
    out.append("    public static int mapEnchantment775To765(int id775) {")
    out.append("        int id767 = mapEnchantment775To767(id775);")
    out.append("        return id767 == DROP ? DROP : mapEnchantment767To765(id767);")
    out.append("    }")
    out.append("")
    out.append("    /** 775のエンチャントID → 763のエンチャントID（765と同じ並び）。 */")
    out.append("    public static int mapEnchantment775To763(int id775) {")
    out.append("        return mapEnchantment775To765(id775);")
    out.append("    }")
    out.append("")
    out.append("    /** 775のエンチャントID → 764のエンチャントID（765と同じ並び）。 */")
    out.append("    public static int mapEnchantment775To764(int id775) {")
    out.append("        return mapEnchantment775To765(id775);")
    out.append("    }")
    return "\n".join(out)


def up_accessors():
    out = ["    // ------------------------------------------------------------------",
           "    // 逆向き（775→774）。上の *_775 表を反転して作った表を使う。",
           "    // アイテムだけは生成スクリプトが名前から作った既存の逆変換表",
           "    // （ITEM_REVERSE_775_MAP）をそのまま使う（反転した表と完全一致",
           "    // することはテストで確認している）。属性とエンチャントの逆向きは",
           "    // mapAttribute775To774／mapEnchantment775To774 を参照。",
           "    // ------------------------------------------------------------------",
           ""]
    for kind in KINDS:
        name, prefix, jp = kind[0], kind[1], kind[2]
        # 逆向きの代替先は「774 側」の定数（kind[36]）。
        fb774 = kind[36]
        if prefix == "ITEM":
            out.append("    /**")
            out.append("     * 775のアイテムID → 774のアイテムID。")
            out.append("     * 775にしか無いアイテム1件（golden_dandelion）は石へ。範囲外は石（1）。")
            out.append("     */")
            out.append("    public static int mapItemUp775(int id775) {")
            out.append("        if (id775 < 0 || id775 >= ITEM_REVERSE_775_MAP.length) {")
            out.append("            logUnknownUp775Once(\"アイテム\", id775);")
            out.append("            return STONE_ITEM_774;")
            out.append("        }")
            out.append("        int mapped = ITEM_REVERSE_775_MAP[id775];")
            out.append("        return mapped < 0 ? STONE_ITEM_774 : mapped;")
            out.append("    }")
            out.append("")
            continue
        out.append(f"    /** 775の{jp}ID → 774の{jp}ID。範囲外は{fb774} に相当する代替。 */")
        out.append(f"    public static int map{name}Up775(int id775) {{")
        out.append(f"        if (id775 < 0 || id775 > MAX_{prefix}_775) {{")
        out.append(f"            logUnknownUp775Once(\"{jp}\", id775);")
        out.append(f"            return {fb774};")
        out.append("        }")
        out.append(f"        return {prefix}_UP_775_MAP[id775];")
        out.append("    }")
        out.append("")
    out.append("    /**")
    out.append("     * 775のエフェクトID → 774のエフェクトID。0〜39 は同一のため恒等写像。")
    out.append("     * 範囲外は Speed（0）。")
    out.append("     */")
    out.append("    public static int mapEffectUp775(int id775) {")
    out.append("        if (id775 < 0 || id775 > MAX_EFFECT_775) {")
    out.append("            logUnknownUp775Once(\"エフェクト\", id775);")
    out.append("            return SPEED_EFFECT_774;")
    out.append("        }")
    out.append("        return id775;")
    out.append("    }")
    out.append("")
    out.append("    /**")
    out.append("     * 775のブロックエンティティ種別ID → 774の種別ID。")
    out.append("     * 774 と 775 で同一と仮定（49 件。統合テストで確認）。")
    out.append("     * 範囲外の種別は NBT の中身が種別専用のため安全に代替できず")
    out.append("     * {@link #DROP}。")
    out.append("     */")
    out.append("    public static int mapBlockEntityTypeUp775(int id775) {")
    out.append("        if (id775 < 0 || id775 > BLOCK_ENTITY_COPPER_GOLEM_STATUE_775) {")
    out.append("            logUnknownUp775Once(\"ブロックエンティティ種別\", id775);")
    out.append("            return DROP;")
    out.append("        }")
    out.append("        return id775;")
    out.append("    }")
    out.append("")
    out.append("    /**")
    out.append("     * 音の名前を774向けに変換する。775の音の名前のうち 774に無いものは")
    out.append("     * 代替の音の名前にする（数値の反転表と一致させる）。775の新音")
    out.append("     * （金のタンポポ・赤ちゃん動物の声・トランペットの音符ブロック関連など64件）は石の破壊音へ。")
    out.append("     * {@code null} は石の破壊音にする。")
    out.append("     */")
    out.append("    public static String mapSoundNameUp775(String name) {")
    out.append("        return mapSoundNameComposed(name, SOUND_NAME_FALLBACK_775_TO_774_MAP);")
    out.append("    }")
    return "\n".join(out)


def composed_accessors():
    out = ["    // ------------------------------------------------------------------",
           "    // 離れた組み合わせの合成表（763↔775、764↔775、765↔775、766↔775、",
           "    // 767↔775、768↔775、769↔775、770↔775、771↔775、772↔775、773↔775）。",
           "    // 上の *_763_TO_775／*_775_TO_763／*_764_TO_775／*_775_TO_764／",
           "    // *_765_TO_775／*_775_TO_765／*_766_TO_775／*_775_TO_766／",
           "    // *_767_TO_775／*_775_TO_767／*_768_TO_775／*_775_TO_768／",
           "    // *_769_TO_775／*_775_TO_769／*_770_TO_775／*_775_TO_770／",
           "    // *_771_TO_775／*_775_TO_771／*_772_TO_775／*_775_TO_772／",
           "    // *_773_TO_775／*_775_TO_773 表（合成済みの1つの表）",
           "    // を直接引く。二段変換はしない。エフェクトとブロックエンティティ",
           "    // 種別と音の名前は 774 のメソッドへ委譲する（774→775 は隣接）。",
           "    // ------------------------------------------------------------------",
           ""]
    pairs = [
        ("763", 25, "763→775", "775→763"),
        ("764", 26, "764→775", "775→764"),
        ("765", 27, "765→775", "775→765"),
        ("766", 28, "766→775", "775→766"),
        ("767", 29, "767→775", "775→767"),
        ("768", 30, "768→775", "775→768"),
        ("769", 31, "769→775", "775→769"),
        ("770", 32, "770→775", "775→770"),
        ("771", 33, "771→775", "775→771"),
        ("772", 34, "772→775", "775→772"),
        ("773", 35, "773→775", "775→773"),
    ]
    for src, fb_src_idx, up_label, down_label in pairs:
        for kind in KINDS:
            name, prefix, jp = kind[0], kind[1], kind[2]
            fb_src = kind[fb_src_idx]
            fb_775 = kind[37]
            # src → 775
            out.append(f"    /** {src}の{jp}ID → 775の{jp}ID。範囲外は{fb_775} に相当する代替。 */")
            out.append(f"    public static int map{name}{src}To775(int id{src}) {{")
            out.append(f"        if (id{src} < 0 || id{src} >= {prefix}_{src}_TO_775_MAP.length) {{")
            out.append(f"            logUnknownComposedOnce(\"{up_label}\", \"{jp}\", id{src});")
            out.append(f"            return {fb_775};")
            out.append("        }")
            out.append(f"        return {prefix}_{src}_TO_775_MAP[id{src}];")
            out.append("    }")
            out.append("")
            # 775 → src
            out.append(f"    /** 775の{jp}ID → {src}の{jp}ID。範囲外は{fb_src} に相当する代替。 */")
            out.append(f"    public static int map{name}775To{src}(int id775) {{")
            out.append(f"        if (id775 < 0 || id775 >= {prefix}_775_TO_{src}_MAP.length) {{")
            out.append(f"            logUnknownComposedOnce(\"{down_label}\", \"{jp}\", id775);")
            out.append(f"            return {fb_src};")
            out.append("        }")
            out.append(f"        return {prefix}_775_TO_{src}_MAP[id775];")
            out.append("    }")
            out.append("")
        # エフェクト・ブロックエンティティ種別・音の名前は 774 のメソッドへ委譲。
        # 773 だけは隣接・上向きのメソッド名（mapEffectUp774 等）を使う。
        if src == "773":
            eff_down = "mapEffectUp774"
            be_down = "mapBlockEntityTypeUp774"
            snd_down = "mapSoundNameUp774"
        else:
            eff_down = f"mapEffect774To{src}"
            be_down = f"mapBlockEntityType774To{src}"
            snd_down = f"mapSoundName774To{src}"
        out.append("    /**")
        out.append(f"     * {src}のエフェクトID → 775のエフェクトID（774経由）。")
        out.append("     */")
        out.append(f"    public static int mapEffect{src}To775(int id{src}) {{")
        out.append(f"        return mapEffect774To775(mapEffect{src}To774(id{src}));")
        out.append("    }")
        out.append("")
        out.append("    /**")
        out.append(f"     * 775のエフェクトID → {src}のエフェクトID（774経由）。")
        out.append("     */")
        out.append(f"    public static int mapEffect775To{src}(int id775) {{")
        out.append(f"        return {eff_down}(mapEffectUp775(id775));")
        out.append("    }")
        out.append("")
        out.append("    /**")
        out.append(f"     * {src}のブロックエンティティ種別ID → 775の種別ID（774経由）。")
        out.append("     * 範囲外は {@link #DROP}。")
        out.append("     */")
        out.append(f"    public static int mapBlockEntityType{src}To775(int id{src}) {{")
        out.append(f"        return mapBlockEntityType774To775(mapBlockEntityType{src}To774(id{src}));")
        out.append("    }")
        out.append("")
        out.append("    /**")
        out.append(f"     * 775のブロックエンティティ種別ID → {src}の種別ID（774経由）。")
        out.append("     * 範囲外は {@link #DROP}。")
        out.append("     */")
        out.append(f"    public static int mapBlockEntityType775To{src}(int id775) {{")
        out.append(f"        return {be_down}(mapBlockEntityTypeUp775(id775));")
        out.append("    }")
        out.append("")
        out.append("    /**")
        out.append(f"     * 音の名前を775向けに変換する（774経由。774→775 の改名は無い）。")
        out.append("     * {@code null} は石の破壊音にする。")
        out.append("     */")
        out.append(f"    public static String mapSoundName{src}To775(String name) {{")
        out.append(f"        return mapSoundNameComposed(mapSoundName{src}To774(name), SOUND_NAME_FALLBACK_775_MAP);")
        out.append("    }")
        out.append("")
        out.append("    /**")
        out.append(f"     * 音の名前を{src}向けに変換する（774経由。数値の合成表と一致させる）。")
        out.append("     * {@code null} は石の破壊音にする。")
        out.append("     */")
        out.append(f"    public static String mapSoundName775To{src}(String name) {{")
        out.append(f"        return {snd_down}(mapSoundNameUp775(name));")
        out.append("    }")
    return "\n".join(out)


def small_registry_accessors():
    out = []
    specs = [
        ("BannerPattern", "バナーパターン", "MAX_BANNER_PATTERN_774", "MAX_BANNER_PATTERN_775"),
        ("PaintingVariant", "絵画バリアント", "MAX_PAINTING_VARIANT_774", "MAX_PAINTING_VARIANT_775"),
        ("TrimPattern", "装飾パターン", "MAX_TRIM_PATTERN_774", "MAX_TRIM_PATTERN_775"),
    ]
    for name, jp, max774, max775 in specs:
        out.append(f"    /** 774の{jp}ID → 775の{jp}ID。774と775で同一のため恒等写像。範囲外は {{@link #DROP}}。 */")
        out.append(f"    public static int map{name}774To775(int id774) {{")
        out.append(f"        if (id774 < 0 || id774 > {max774}) {{")
        out.append(f"            logUnknownOnce(PROTOCOL_775, \"{jp}\", id774);")
        out.append("            return DROP;")
        out.append("        }")
        out.append("        return id774;")
        out.append("    }")
        out.append("")
        out.append(f"    /** 775の{jp}ID → 774の{jp}ID。774と775で同一のため恒等写像。範囲外は {{@link #DROP}}。 */")
        out.append(f"    public static int map{name}775To774(int id775) {{")
        out.append(f"        if (id775 < 0 || id775 > {max775}) {{")
        out.append(f"            logUnknownOnce(PROTOCOL_775, \"{jp}\", id775);")
        out.append("            return DROP;")
        out.append("        }")
        out.append("        return id775;")
        out.append("    }")
        out.append("")
        for src in ("763", "764", "765", "766", "767", "768", "769", "770", "771", "772", "773"):
            # 絵画バリアントだけ 771 を経由する（763／764／765／766／767／768／769／770→775 と
            # 775→763／764／765／766／767／768／769／770 は無い。774→775 は隣接で既にある）。
            # 772 経由は使えない（{src}→772 の絵画メソッドが 772 の代で作られていないため）。
            # 771 だけは 772 経由（771→772 が存在する）。772 は 773 経由。773 は 774 経由。
            # バナー・装飾パターンは 773 を経由する（{src}→773 は既にある）。773 は 774 経由。
            if name == "PaintingVariant":
                hub = "772" if src == "771" else ("773" if src == "772" else ("774" if src == "773" else "771"))
            else:
                hub = "774" if src == "773" else "773"
            out.append(f"    /** {src}の{jp}ID → 775の{jp}ID（{hub}経由）。 */")
            out.append(f"    public static int map{name}{src}To775(int id{src}) {{")
            out.append(f"        return map{name}{hub}To775(map{name}{src}To{hub}(id{src}));")
            out.append("    }")
            out.append("")
            out.append(f"    /** 775の{jp}ID → {src}の{jp}ID（{hub}経由）。 */")
            out.append(f"    public static int map{name}775To{src}(int id775) {{")
            out.append(f"        return map{name}{hub}To{src}(map{name}775To{hub}(id775));")
            out.append("    }")
            out.append("")
    return "\n".join(out)


def jukebox_accessors():
    """ジュークボックスの曲の 775 アクセサ（隣接のみ。774 と同じ作り）。

    774 と同じく合成表は作らない（763〜772→774 の合成メソッドが無いため）。
    曲は 774 と 775 で 21 件・同じ並びと仮定（実バニラの Registry Data
    での確認は統合テストで行う）。名前の表は JUKEBOX_SONG_775_NAMES を
    作らず 773 の表をそのまま使う（774 と同じ方針）。
    """
    out = []
    out.append("    /** 774のジュークボックスの曲ID → 775の曲ID。774と775は21件・同じ並びと仮定のため恒等写像。範囲外は {@link #DROP}。 */")
    out.append("    public static int mapJukeboxSong774To775(int id774) {")
    out.append("        if (id774 < 0 || id774 >= JUKEBOX_SONG_773_NAMES.length) {")
    out.append("            logUnknownOnce(PROTOCOL_775, \"ジュークボックスの曲\", id774);")
    out.append("            return DROP;")
    out.append("        }")
    out.append("        return id774;")
    out.append("    }")
    out.append("")
    out.append("    /** 775のジュークボックスの曲ID → 774の曲ID。774と775は21件・同じ並びと仮定のため恒等写像。範囲外は {@link #DROP}。 */")
    out.append("    public static int mapJukeboxSong775To774(int id775) {")
    out.append("        if (id775 < 0 || id775 >= JUKEBOX_SONG_773_NAMES.length) {")
    out.append("            logUnknownOnce(PROTOCOL_775, \"ジュークボックスの曲\", id775);")
    out.append("            return DROP;")
    out.append("        }")
    out.append("        return id775;")
    out.append("    }")
    out.append("")
    out.append("    /** 775のジュークボックスの曲ID → 曲の名前（jukebox_playable の名前指定用）。774と同一のため 773 の表を使う。 */")
    out.append("    public static String jukeboxSongName775(int id775) {")
    out.append("        if (id775 < 0 || id775 >= JUKEBOX_SONG_773_NAMES.length) {")
    out.append("            return null;")
    out.append("        }")
    out.append("        return JUKEBOX_SONG_773_NAMES[id775];")
    out.append("    }")
    return "\n".join(out)



DAMAGE_TYPE_775 = """
    /** 775のダメージ種別最大ID（50件。774 と同一と仮定。
        実サーバーの Registry Data での確認は統合テストで行う）。 */
    public static final int MAX_DAMAGE_TYPE_775 = 49;

    /** ダメージ種別ID：774 → 775（774 と同一と仮定のため恒等写像）。
     * 実サーバーの Registry Data での確認は統合テストで行う。 */
    private static final int[] DAMAGE_TYPE_775_MAP = {
        0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19,
        20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32, 33, 34, 35, 36,
        37, 38, 39, 40, 41, 42, 43, 44, 45, 46, 47, 48, 49
    };

    /** ダメージ種別ID：775 → 774（774 と同一と仮定のため恒等写像）。 */
    private static final int[] DAMAGE_TYPE_REVERSE_775_MAP = {
        0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19,
        20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32, 33, 34, 35, 36,
        37, 38, 39, 40, 41, 42, 43, 44, 45, 46, 47, 48, 49
    };
"""

SMALL_REGISTRY_CONSTANTS = """
    /** 775のバナーパターン最大ID（43件。774と同一と仮定。実バニラの
        Registry Data での確認は統合テストで行う）。 */
    public static final int MAX_BANNER_PATTERN_775 = 42;

    /** 775の絵画バリアント最大ID（51件。774と同一。実バニラの
        Registry Data での確認は統合テストで行う）。 */
    public static final int MAX_PAINTING_VARIANT_775 = 50;

    /** 775の装飾パターン最大ID（18件。774と同一と仮定。実バニラの
        Registry Data での確認は統合テストで行う）。 */
    public static final int MAX_TRIM_PATTERN_775 = 17;
"""


JAVADOC_775 = """ *
 * <p>775（26.1）向けの表も同じ作り方で持つ（{@code *_775}）。
 * 775は774に近く、差分は次の通り（ViaVersion mapping-26.1.json と
 * mapping-1.21.11.json、minecraft-data 26.1 の突き合わせ）。
 * ブロック状態は新202件（29,671→29,873。トランペットの音符ブロック
 * 200件・金のタンポポ2件）。ブロックは新2件（金のタンポポ・
 * 金のタンポポの植木鉢）。アイテムは新1件（金のタンポポが 230 に挿入）。
 * エンティティは同一（157件）。音は新64件・削除0件（赤ちゃん動物の声・
 * トランペットの音符ブロック関連など）。パーティクルは新2件
 * （pause_mob_growth・reset_mob_growth が 50・51 に追加）。
 * データコンポーネントは 104→110（新6件）。エフェクト・エンチャント・
 * バイオーム・メニュー・属性は同一。ブロックエンティティ種別は同一
 * （49件）と仮定。絵画バリアント・ジュークボックスの歌・
 * バナーパターン・装飾パターン・ダメージ種別も 774 と同一と仮定
 * （実バニラの Registry Data での確認は統合テストで行う）。
 * 774→775 の隣接表と 763／764／765／766／767／768／769／770／771／772／773→775
 * の合成表を持ち、逆向き 775→774 は反転して作る。
"""

def damage_accessors():
    out = []
    out.append("    /** 774のダメージ種別ID → 775のダメージ種別ID。774 と同一と仮定のため恒等写像。範囲外は {@link #DROP}。 */")
    out.append("    public static int mapDamageType774To775(int id774) {")
    out.append("        if (id774 < 0 || id774 > MAX_DAMAGE_TYPE_774) {")
    out.append("            logUnknownOnce(PROTOCOL_775, \"ダメージ種別\", id774);")
    out.append("            return DROP;")
    out.append("        }")
    out.append("        return DAMAGE_TYPE_775_MAP[id774];")
    out.append("    }")
    out.append("")
    out.append("    /** 775のダメージ種別ID → 774のダメージ種別ID。774 と同一と仮定のため恒等写像。範囲外は {@link #DROP}。 */")
    out.append("    public static int mapDamageType775To774(int id775) {")
    out.append("        if (id775 < 0 || id775 > MAX_DAMAGE_TYPE_775) {")
    out.append("            logUnknownOnce(PROTOCOL_775, \"ダメージ種別\", id775);")
    out.append("            return DROP;")
    out.append("        }")
    out.append("        return DAMAGE_TYPE_REVERSE_775_MAP[id775];")
    out.append("    }")
    out.append("")
    for src in ("763", "764", "765", "766", "767", "768", "769", "770", "771", "772", "773"):
        # ダメージ種別は 774 のメソッドを経由する。
        out.append(f"    /** {src}のダメージ種別ID → 775のダメージ種別ID（774経由）。 */")
        out.append(f"    public static int mapDamageType{src}To775(int id{src}) {{")
        out.append(f"        return mapDamageType774To775(mapDamageType{src}To774(id{src}));")
        out.append("    }")
        out.append("")
        out.append(f"    /** 775のダメージ種別ID → {src}のダメージ種別ID（774経由）。 */")
        out.append(f"    public static int mapDamageType775To{src}(int id775) {{")
        out.append(f"        return mapDamageType774To{src}(mapDamageType775To774(id775));")
        out.append("    }")
        out.append("")
    return "\n".join(out)


def insert_after(java, anchor, block, label):
    if anchor not in java:
        print(f"アンカーが見つからない（{label}）", file=sys.stderr)
        return None
    return java.replace(anchor, anchor + block, 1)


def main():
    if (not TABLES775_FILE.exists() or not TABLES775_COMPOSED_FILE.exists()
            or not all(p.exists() for p in TABLES775_COMPOSED_DOWN_FILES.values())):
        print("RegistryTables775.java / RegistryTables775ComposedUpA.java / "
              "RegistryTables775Down*.java が無い。"
              "先に insert-775-tables.py を実行すること", file=sys.stderr)
        return 1
    java = JAVA_FILE.read_text(encoding="utf-8")
    checks = [
        ("PROTOCOL_775", "775 の定数・表・アクセサは既にある"),
        ("BLOCK_STATE_775_MAP", "775 の展開済み表は既にある"),
        ("mapBlockState774To775", "775 のアクセサは既にある"),
    ]
    for marker, msg in checks:
        if marker in java:
            print(msg, file=sys.stderr)
            return 1

    # import static。774 の Down772 の直後（774 世代の最後）。
    # Expanded の import は move-775-expanded-tables.py が後で足す
    # （774 と同じ段取り）。Down*BlockState／SoundNames の import は
    # どのスクリプトも足さない（774 も同じで、Stage A のコミット時に
    # 手で足した）ため、ここには含めず後で手で追加すること。
    java = insert_after(java,
                        "import static io.detergentmc.protocol.RegistryTables774Down772.*;\n",
                        "import static io.detergentmc.protocol.RegistryTables775.*;\n"
                        "import static io.detergentmc.protocol.RegistryTables775ComposedUpA.*;\n"
                        "import static io.detergentmc.protocol.RegistryTables775ComposedUpB.*;\n"
                        "import static io.detergentmc.protocol.RegistryTables775Down763.*;\n"
                        "import static io.detergentmc.protocol.RegistryTables775Down764.*;\n"
                        "import static io.detergentmc.protocol.RegistryTables775Down765.*;\n"
                        "import static io.detergentmc.protocol.RegistryTables775Down766.*;\n"
                        "import static io.detergentmc.protocol.RegistryTables775Down767.*;\n"
                        "import static io.detergentmc.protocol.RegistryTables775Down768.*;\n"
                        "import static io.detergentmc.protocol.RegistryTables775Down769.*;\n"
                        "import static io.detergentmc.protocol.RegistryTables775Down770.*;\n"
                        "import static io.detergentmc.protocol.RegistryTables775Down771.*;\n"
                        "import static io.detergentmc.protocol.RegistryTables775Down772.*;\n"
                        "import static io.detergentmc.protocol.RegistryTables775Down773.*;\n",
                        "import static RegistryTables775")
    if java is None:
        return 1

    # クラスの javadoc に 775 の段落を追加（774 の段落の直後）。
    anchor = (" * 773→774 の隣接表と 763／764／765／766／767／768／769／770／771／772→774\n"
              " * の合成表を持ち、逆向き 774→773 は反転して作る。\n")
    java = insert_after(java, anchor, JAVADOC_775, "javadoc")
    if java is None:
        return 1

    # 定数。
    java = insert_after(java,
                        "    public static final int PROTOCOL_774 = 774;\n",
                        "    public static final int PROTOCOL_775 = 775;\n", "PROTOCOL_775")
    if java is None:
        return 1
    java = insert_after(java,
                        "    public static final int MAX_EFFECT_774 = 39;\n",
                        CONSTANTS_MAX, "MAX_775")
    if java is None:
        return 1
    java = insert_after(java,
                        "    public static final int SPEED_EFFECT_774 = 0;\n",
                        CONSTANTS_FALLBACK, "775 の代替先")
    if java is None:
        return 1
    java = insert_after(java,
                        "    public static final int BLOCK_ENTITY_COPPER_GOLEM_STATUE_774 = 48;\n",
                        CONSTANTS_BLOCK_ENTITY, "775 のブロックエンティティ種別")
    if java is None:
        return 1

    # 隣接表（774→775）。MENU_771_MAP の直後。
    anchor = ("    private static final int[] MENU_771_MAP =\n"
              "            expand(MENU_771_RANGES, MENU_771_OVERRIDES, MAX_MENU_770 + 1,\n"
              "                    CRAFTING_MENU_771);\n")
    java = insert_after(java, anchor, adjacent_maps() + "\n", "隣接表 775")
    if java is None:
        return 1

    # 合成表。MENU_771_TO_769_MAP の直後。
    anchor = ("    private static final int[] MENU_771_TO_769_MAP =\n"
              "            expand(MENU_771_TO_769_RANGES, MENU_771_TO_769_OVERRIDES,\n"
              "                    25, CRAFTING_MENU_769);\n")
    java = insert_after(java, anchor, "\n" + composed_maps() + "\n", "合成表 775")
    if java is None:
        return 1

    # 反転表（775→774）。MENU_UP_771_MAP の直後。
    anchor = ("    private static final int[] MENU_UP_771_MAP = invert(\n"
              "            MENU_771_RANGES, MENU_771_OVERRIDES,\n"
              "            MAX_MENU_770 + 1, MAX_MENU_771 + 1,\n"
              "            CRAFTING_MENU_770, null);\n")
    java = insert_after(java, anchor, up_maps() + "\n", "反転表 775")
    if java is None:
        return 1

    # 音の名前の表。
    anchor = ("    private static final Map<String, String> SOUND_NAME_FALLBACK_766_TO_768_MAP =\n"
              "            buildSoundNameMap(SOUND_NAME_FALLBACKS_766_TO_768);\n")
    java = insert_after(java, anchor, SOUND_NAME_MAPS, "音の名前の表 775")
    if java is None:
        return 1

    # ログ。
    anchor = ("    private static void logUnknownUp774Once(String kind, int id774) {\n"
              "        if (LOGGED_UNKNOWN.add(\"up774:\" + kind + \":\" + id774)) {\n"
              "            LOG.warning(\"774に無い\" + kind + \"の番号が来たため代替に落とします（774 id=\" + id774 + \"）。\");\n"
              "        }\n"
              "    }\n")
    java = insert_after(java, anchor, LOG_UP_775, "logUnknownUp775Once")
    if java is None:
        return 1

    # アクセサ（a）774→775。mapEnchantment774To764 の直後（774 の隣接
    # アクセサの最後。mapSoundName767To774 は合成アクセサの途中にあるため
    # アンカーに使えない）。
    anchor = ("    public static int mapEnchantment774To764(int id774) {\n"
              "        return mapEnchantment774To765(id774);\n"
              "    }\n")
    java = insert_after(java, anchor, "\n" + adjacent_accessors() + "\n", "アクセサ 774→775")
    if java is None:
        return 1

    # アクセサ（b）775→774。mapSoundNameUp774 の直後。
    anchor = ("    public static String mapSoundNameUp774(String name) {\n"
              "        return mapSoundNameComposed(name, SOUND_NAME_FALLBACK_774_TO_773_MAP);\n"
              "    }\n")
    java = insert_after(java, anchor, "\n" + up_accessors() + "\n", "アクセサ 775→774")
    if java is None:
        return 1

    # アクセサ（c）763／764／765／766／767／768／769／770／771／772／773↔775。mapSoundName774To772 の直後。
    anchor = ("    public static String mapSoundName774To772(String name) {\n"
              "        return mapSoundNameComposed(name, SOUND_NAME_FALLBACK_774_TO_772_MAP);\n"
              "    }\n")
    java = insert_after(java, anchor, "\n" + composed_accessors() + "\n", "アクセサ 763/764/765/766/767/768/769/770/771/772/773↔775")
    if java is None:
        return 1

    # バナー・絵画・装飾パターン・ダメージ種別の定数。
    java = insert_after(java,
                        "    /** 774のバナーパターン最大ID（43件。773と同一と仮定。実バニラの\n"
                        "        Registry Data での確認は統合テストで行う）。 */\n"
                        "    public static final int MAX_BANNER_PATTERN_774 = 42;\n",
                        SMALL_REGISTRY_CONSTANTS, "バナー等の775定数")
    if java is None:
        return 1
    anchor = ("    private static final int[] DAMAGE_TYPE_REVERSE_774_MAP = {\n"
              "        0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19,\n"
              "        20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32, 33, 34, 35, 36,\n"
              "        -1, 37, 38, 39, 40, 41, 42, 43, 44, 45, 46, 47, 48\n"
              "    };\n")
    java = insert_after(java, anchor, DAMAGE_TYPE_775, "ダメージ種別775の表")
    if java is None:
        return 1

    # バナー・絵画・装飾パターン・ダメージ種別のアクセサ。
    anchor = ("    public static int mapBannerPattern774To771(int id774) {\n"
              "        return mapBannerPattern773To771(mapBannerPattern774To773(id774));\n"
              "    }\n")
    banner_painting_trim = small_registry_accessors()
    java = insert_after(java, anchor, "\n" + banner_painting_trim + "\n", "バナー等の775アクセサ")
    if java is None:
        return 1
    anchor = ("    /** 774のジュークボックスの曲ID → 曲の名前（jukebox_playable の名前指定用）。773と同一のため 773 の表を使う。 */\n"
              "    public static String jukeboxSongName774(int id774) {\n"
              "        if (id774 < 0 || id774 >= JUKEBOX_SONG_773_NAMES.length) {\n"
              "            return null;\n"
              "        }\n"
              "        return JUKEBOX_SONG_773_NAMES[id774];\n"
              "    }\n")
    java = insert_after(java, anchor, "\n" + jukebox_accessors() + "\n", "ジュークボックス775のアクセサ")
    if java is None:
        return 1
    java = insert_after(java, anchor, "\n" + damage_accessors() + "\n", "ダメージ種別775のアクセサ")
    if java is None:
        return 1

    # プロトコル番号で切り替える変換（担当2の配線用）に 775 を追加。
    dispatches = [
        ("mapBlockState", "mapBlockState765To774(id765)", "mapBlockState765To775(id765)"),
        ("mapItem", "mapItem765To774(id765)", "mapItem765To775(id765)"),
        ("mapItemReverse", "mapItem774To765(id)", "mapItem775To765(id)"),
        ("mapEntityType", "mapEntityType765To774(id765)", "mapEntityType765To775(id765)"),
        ("mapSound", "mapSound765To774(id765)", "mapSound765To775(id765)"),
        ("mapParticle", "mapParticle765To774(id765)", "mapParticle765To775(id765)"),
        ("mapBlock", "mapBlock765To774(id765)", "mapBlock765To775(id765)"),
        ("mapMenu", "mapMenu765To774(id765)", "mapMenu765To775(id765)"),
        ("mapEffect", "mapEffect765To774(id765)", "mapEffect765To775(id765)"),
        ("mapBlockEntityType", "mapBlockEntityType765To774(id765)", "mapBlockEntityType765To775(id765)"),
        ("mapSoundName", "mapSoundName765To774(name)", "mapSoundName765To775(name)"),
    ]
    for label, old_call, new_call in dispatches:
        if label == "mapItemReverse":
            old = ("        if (clientProtocol == PROTOCOL_774) {\n"
                   "            // 774クライアントのアイテムID → 765サーバーのアイテムID。\n"
                   "            // mapItemReverse774 は 774→773 のため、ここでは合成表を使う。\n"
                   "            return mapItem774To765(id);\n"
                   "        }\n")
            new = (old
                   + "        if (clientProtocol == PROTOCOL_775) {\n"
                   "            // 775クライアントのアイテムID → 765サーバーのアイテムID。\n"
                   "            // mapItemReverse775 は 775→774 のため、ここでは合成表を使う。\n"
                   "            return mapItem775To765(id);\n"
                   "        }\n")
            if old not in java:
                print("ディスパッチのアンカーが見つからない（mapItemReverse）", file=sys.stderr)
                return 1
            java = java.replace(old, new, 1)
            continue
        old = ("        if (clientProtocol == PROTOCOL_774) {\n"
               f"            return {old_call};\n"
               "        }\n")
        if old not in java:
            print(f"ディスパッチのアンカーが見つからない（{label}）", file=sys.stderr)
            return 1
        java = java.replace(old, old
                            + "        if (clientProtocol == PROTOCOL_775) {\n"
                            + f"            return {new_call};\n"
                            + "        }\n", 1)
    java = java.replace(
        "    // 765は素通し、766は766の表、767は767の表、768は768の表、\n"
        "    // 759は760の表を経由する（759↔760は恒等写像）。\n"
        "    // 769は769の表、770は770の表、771は771の表、772は772の表、773は773の表、774は774の表、それより古い未知の番号は763の表を使う。\n"
        "    // 767／768／769／770／771／772／773／774は765以上だが素通しではない（>765 の分岐より\n"
        "    // 先に 767／768／769／770／771／772／773／774 を判定すること）。",
        "    // 765は素通し、766は766の表、767は767の表、768は768の表、\n"
        "    // 759は760の表を経由する（759↔760は恒等写像）。\n"
        "    // 769は769の表、770は770の表、771は771の表、772は772の表、773は773の表、774は774の表、775は775の表、それより古い未知の番号は763の表を使う。\n"
        "    // 767／768／769／770／771／772／773／774／775は765以上だが素通しではない（>765 の分岐より\n"
        "    // 先に 767／768／769／770／771／772／773／774／775 を判定すること）。",
        1)

    JAVA_FILE.write_text(java, encoding="utf-8")
    print("RegistryRemapper.java に 775 の定数・表・アクセサを挿入した")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
