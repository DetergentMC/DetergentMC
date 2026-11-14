#!/usr/bin/env python3
"""tools/insert-771-java.py で入れた *_771 表の上に、RegistryRemapper.java
へ 772 対応の定数・展開済み表（*_772_MAP）・反転表（*_UP_772_MAP）・
アクセサを挿入する（1回だけの作業用）。

  - 定数：PROTOCOL_772、MAX_*_772、772 側の代替先。
  - 隣接表（771→772）: *_772_MAP と ITEM_REVERSE_772_MAP と
    ATTRIBUTE_772_MAP／ATTRIBUTE_REVERSE_772_MAP／ENCHANTMENT_772_MAP／
    ENCHANTMENT_REVERSE_772_MAP。
  - 合成表（763↔772、764↔772、765↔772、766↔772、767↔772、768↔772、769↔772、770↔772）: *_763_TO_772_MAP など112件。
  - 771↔772 は隣接表（*_772_MAP）と反転表（*_UP_772_MAP）で扱うため合成表は作らない（771と同じ方針）。
  - 逆向き（772→771）: *_UP_772_MAP（invert()。アイテムは
    ITEM_REVERSE_772_MAP をそのまま使う。771→772 に補正は無い）。
  - 音の名前の表（String）: SOUND_NAME_FALLBACK_*_772_MAP など16件。
  - アクセサ：771↔772、772→771（上向き）、763／764／765／766／767／768／769／770↔772。
  - エフェクト：772 は 771 と同一の 39 件（0〜38）のため恒等写像。
    属性は 771・772 とも 35 件で同一、エンチャントは 42 件同士で
    並びは同一（minecraft-data の dataPaths.json で 1.21.8 の
    enchantments は pc/1.21.8 と確認。1.21.1 の並びと同一）。
    ブロックエンティティ種別は 771 と同じ 47 件（creaking_heart=10、
    trial_spawner=43、vault=44、test_block=45、test_instance_block=46）。
    バナー・装飾パターンは 771 と同一のため恒等写像。絵画だけが
    変わる（dennis が 14 に挿入され 51 件。恒等写像ではない）。
    ダメージ種別も 771 と同一の 49 件のため恒等写像。
    バイオームは 769・770・771・772 とも 65 件で同一の並び（実バニラの Registry Data
    での確認は統合テストで行う）。
  - 注意：このスクリプトで挿入した展開済み表（隣接12・合成112・上向き6の
    計130件）は、そのままでは RegistryRemapper.java の <clinit> が 64KB を
    超えてコンパイルできない（771 までがギリギリ収まっていた）。そのため
    挿入後に tools/move-772-expanded-tables.py で RegistryTables772Expanded.java
    へ移動してある。再実行時はこの移動もやり直すこと。
"""

import re
import sys
from pathlib import Path

SCRIPT_DIR = Path(__file__).resolve().parent
REPO_ROOT = SCRIPT_DIR.parent.parent
JAVA_FILE = (REPO_ROOT / "detergentmc-protocol" / "src" / "main" / "java"
             / "io" / "detergentmc" / "protocol" / "RegistryRemapper.java")
TABLES772_FILE = (REPO_ROOT / "detergentmc-protocol" / "src" / "main" / "java"
                  / "io" / "detergentmc" / "protocol" / "RegistryTables772.java")
TABLES772_COMPOSED_FILE = (REPO_ROOT / "detergentmc-protocol" / "src" / "main" / "java"
                  / "io" / "detergentmc" / "protocol" / "RegistryTables772ComposedUp.java")
TABLES772_COMPOSED_DOWN_FILES = {
    suffix: (REPO_ROOT / "detergentmc-protocol" / "src" / "main" / "java"
             / "io" / "detergentmc" / "protocol"
             / f"RegistryTables772Down{suffix}.java")
    for suffix in ("763", "764", "765", "766", "767", "768", "769", "770")
}


# kind: (Java メソッド名の語尾, RANGES の接頭辞, 日本語名,
#        763→772 の件数, 772→763 の件数, 764→772 の件数, 772→764 の件数,
#        765→772 の件数, 772→765 の件数, 766→772 の件数, 772→766 の件数,
#        767→772 の件数, 772→767 の件数, 768→772 の件数, 772→768 の件数,
#        769→772 の件数, 772→769 の件数, 770→772 の件数, 772→770 の件数,
#        771→772 の件数, 772→771 の件数,
#        763 側の代替先, 764 側の代替先, 765 側の代替先, 766 側の代替先,
#        767 側の代替先, 768 側の代替先, 769 側の代替先, 770 側の代替先,
#        771 側の代替先, 772 側の代替先)
KINDS = [
    ("BlockState", "BLOCK_STATE", "ブロック状態", 24135, 27946, 24276, 27946, 26644, 27946, 26684, 27946, 26684, 27946, 27318, 27946, 27866, 27946, 27914, 27946, 27946, 27946,
     "STONE_BLOCK_STATE_763", "STONE_BLOCK_STATE_764", "STONE_BLOCK_STATE_765", "STONE_BLOCK_STATE_766", "STONE_BLOCK_STATE_767", "STONE_BLOCK_STATE_768", "STONE_BLOCK_STATE_769", "STONE_BLOCK_STATE_770", "STONE_BLOCK_STATE_771", "STONE_BLOCK_STATE_772"),
    ("Item", "ITEM", "アイテム", 1255, 1416, 1255, 1416, 1312, 1416, 1330, 1416, 1333, 1416, 1375, 1416, 1385, 1416, 1396, 1416, 1415, 1416,
     "STONE_ITEM_763", "STONE_ITEM_764", "STONE_ITEM_765", "STONE_ITEM_766", "STONE_ITEM_767", "STONE_ITEM_768", "STONE_ITEM_769", "STONE_ITEM_770", "STONE_ITEM_771", "STONE_ITEM_772"),
    ("EntityType", "ENTITY_TYPE", "エンティティ種別", 124, 151, 124, 151, 126, 151, 130, 151, 130, 151, 150, 151, 149, 151, 150, 151, 151, 151,
     "PIG_ENTITY_TYPE_763", "PIG_ENTITY_TYPE_764", "PIG_ENTITY_TYPE_765", "PIG_ENTITY_TYPE_766", "PIG_ENTITY_TYPE_767", "PIG_ENTITY_TYPE_768", "PIG_ENTITY_TYPE_769", "PIG_ENTITY_TYPE_770", "PIG_ENTITY_TYPE_771", "PIG_ENTITY_TYPE_772"),
    ("Sound", "SOUND", "音", 1474, 1728, 1485, 1728, 1539, 1728, 1607, 1728, 1611, 1728, 1636, 1728, 1651, 1728, 1702, 1728, 1727, 1728,
     "GENERIC_SOUND_763", "GENERIC_SOUND_764", "GENERIC_SOUND_765", "GENERIC_SOUND_766", "GENERIC_SOUND_767", "GENERIC_SOUND_768", "GENERIC_SOUND_769", "GENERIC_SOUND_770", "GENERIC_SOUND_771", "GENERIC_SOUND_772"),
    ("Particle", "PARTICLE", "パーティクル", 95, 114, 95, 114, 101, 114, 109, 114, 109, 114, 111, 114, 112, 114, 114, 114, 114, 114,
     "AMBIENT_PARTICLE_763", "AMBIENT_PARTICLE_764", "AMBIENT_PARTICLE_765", "ENTITY_EFFECT_PARTICLE_766", "ENTITY_EFFECT_PARTICLE_767", "ENTITY_EFFECT_PARTICLE_768", "ENTITY_EFFECT_PARTICLE_769", "ENTITY_EFFECT_PARTICLE_770", "ENTITY_EFFECT_PARTICLE_771", "ENTITY_EFFECT_PARTICLE_772"),
    ("Block", "BLOCK", "ブロック", 1003, 1105, 1003, 1105, 1058, 1105, 1060, 1105, 1060, 1105, 1084, 1105, 1095, 1105, 1104, 1105, 1105, 1105,
     "STONE_BLOCK_763", "STONE_BLOCK_764", "STONE_BLOCK_765", "STONE_BLOCK_766", "STONE_BLOCK_767", "STONE_BLOCK_768", "STONE_BLOCK_769", "STONE_BLOCK_770", "STONE_BLOCK_771", "STONE_BLOCK_772"),
    ("Menu", "MENU", "メニュー種別", 24, 25, 24, 25, 25, 25, 25, 25, 25, 25, 25, 25, 25, 25, 25, 25, 25, 25,
     "CRAFTING_MENU_763", "CRAFTING_MENU_764", "CRAFTING_MENU_765", "CRAFTING_MENU_766", "CRAFTING_MENU_767", "CRAFTING_MENU_768", "CRAFTING_MENU_769", "CRAFTING_MENU_770", "CRAFTING_MENU_771", "CRAFTING_MENU_772"),
]

CONSTANTS_MAX = """
    /** 772側の最大の番号（ViaVersion mapping-1.21.7.json の件数−1）。 */
    public static final int MAX_BLOCK_STATE_772 = 27945;
    public static final int MAX_ITEM_772 = 1415;
    public static final int MAX_ENTITY_TYPE_772 = 150;
    public static final int MAX_SOUND_772 = 1727;
    public static final int MAX_PARTICLE_772 = 113;
    public static final int MAX_BLOCK_772 = 1104;
    public static final int MAX_MENU_772 = 24;
    /** 772側のエフェクトも 0〜38（Speed=0。771と同一。minecraft-data の
        dataPaths.json で 1.21.8 の effects は pc/1.21.8 と確認）。 */
    public static final int MAX_EFFECT_772 = 38;
    /** 772側の属性は 0〜34（771 と同じ 35 件。
        ViaVersion mapping-1.21.7.json で確認）。 */
    public static final int MAX_ATTRIBUTE_772 = 34;
    /** エンチャントは 769・770・771・772 とも 0〜41（42件。並びは同一。
        minecraft-data の dataPaths.json で 1.21.8 の enchantments は
        pc/1.21.8 と確認（1.21.1 の並びと同一）。実機の Registry Data での
        確認は統合テストで行う）。 */
    public static final int MAX_ENCHANTMENT_772 = 41;
"""

CONSTANTS_FALLBACK = """
    /** 772側の代替先（mapping-1.21.7.json の番号。Speed だけ 0ベースで 0）。
     * ブタは 772 でも 95（エンティティに変化なし）、
     * 石の破壊音は 772 では 1442（新音 music_disc.lava_chicken が
     * 939 に挿入され、771 の 1441 から +1）。 */
    public static final int STONE_BLOCK_STATE_772 = 1;
    public static final int AIR_BLOCK_STATE_772 = 0;
    public static final int STONE_ITEM_772 = 1;
    public static final int PIG_ENTITY_TYPE_772 = 95;
    public static final int GENERIC_SOUND_772 = 1442; // block.stone.break
    /** 772でも ambient_entity_effect は無いため、パーティクルの代替は entity_effect。 */
    public static final int ENTITY_EFFECT_PARTICLE_772 = 20;
    public static final int STONE_BLOCK_772 = 1;
    public static final int CRAFTING_MENU_772 = 12;
    public static final int SPEED_EFFECT_772 = 0;
"""

CONSTANTS_BLOCK_ENTITY = """
    /** 772のブロックエンティティ種別：771 と同じ 47 件
        （creaking_heart=10、trial_spawner=43、vault=44、test_block=45、
        test_instance_block=46）。 */
    public static final int BLOCK_ENTITY_CREAKING_HEART_772 = 10;
    public static final int BLOCK_ENTITY_TRIAL_SPAWNER_772 = 43;
    public static final int BLOCK_ENTITY_VAULT_772 = 44;
    public static final int BLOCK_ENTITY_TEST_BLOCK_772 = 45;
    public static final int BLOCK_ENTITY_TEST_INSTANCE_BLOCK_772 = 46;
"""


def adjacent_maps():
    lines = []
    for kind in KINDS:
        name, prefix, jp = kind[0], kind[1], kind[2]
        lines.append(
            f"    private static final int[] {prefix}_772_MAP =\n"
            f"            expand({prefix}_772_RANGES, {prefix}_772_OVERRIDES, MAX_{prefix}_771 + 1,\n"
            f"                    {kind[30]});")
    lines.append(
        "    private static final int[] ITEM_REVERSE_772_MAP =\n"
        "            expand(ITEM_REVERSE_772_RANGES, ITEM_REVERSE_772_OVERRIDES, 1416, -1);")
    lines.append(
        "    private static final int[] ATTRIBUTE_772_MAP =\n"
        "            expand(ATTRIBUTE_772_RANGES, ATTRIBUTE_772_OVERRIDES, MAX_ATTRIBUTE_771 + 1, DROP);")
    lines.append(
        "    private static final int[] ATTRIBUTE_REVERSE_772_MAP =\n"
        "            expand(ATTRIBUTE_REVERSE_772_RANGES, ATTRIBUTE_REVERSE_772_OVERRIDES,\n"
        "                    MAX_ATTRIBUTE_772 + 1, DROP);")
    lines.append(
        "    private static final int[] ENCHANTMENT_772_MAP =\n"
        "            expand(ENCHANTMENT_772_RANGES, ENCHANTMENT_772_OVERRIDES,\n"
        "                    MAX_ENCHANTMENT_771 + 1, DROP);")
    lines.append(
        "    private static final int[] ENCHANTMENT_REVERSE_772_MAP =\n"
        "            expand(ENCHANTMENT_REVERSE_772_RANGES, ENCHANTMENT_REVERSE_772_OVERRIDES,\n"
        "                    MAX_ENCHANTMENT_772 + 1, DROP);")
    return "\n".join(lines)


def composed_maps():
    # (接尾辞, 件数の位置, 代替先の位置)。件数・代替先は KINDS の
    # タプルから取る（763_TO_772: 件数 index 3, 代替先 772=index 30／
    # 772_TO_763: 件数 index 4, 代替先 763=index 21）。件数は 3〜20、
    # 代替先は 21（763）〜30（772）。771↔772 は隣接表で扱うため含めない。
    pairs = [
        ("763_TO_772", 3, 30), ("772_TO_763", 4, 21),
        ("764_TO_772", 5, 30), ("772_TO_764", 6, 22),
        ("765_TO_772", 7, 30), ("772_TO_765", 8, 23),
        ("766_TO_772", 9, 30), ("772_TO_766", 10, 24),
        ("767_TO_772", 11, 30), ("772_TO_767", 12, 25),
        ("768_TO_772", 13, 30), ("772_TO_768", 14, 26),
        ("769_TO_772", 15, 30), ("772_TO_769", 16, 27),
        ("770_TO_772", 17, 30), ("772_TO_770", 18, 28),
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
            continue  # アイテムは ITEM_REVERSE_772_MAP を使う。
        # 771→772 に補正は無い（creaking_heart のプロパティ変更と
        # potion の分割は 771 で済んでいる）。反転はそのまま使う。
        corrections = "null"
        # 逆向きの代替先は「771 側」の定数（kind[26]）。
        fb771 = kind[29]
        lines.append(
            f"    private static final int[] {prefix}_UP_772_MAP = invert(\n"
            f"            {prefix}_772_RANGES, {prefix}_772_OVERRIDES,\n"
            f"            MAX_{prefix}_771 + 1, MAX_{prefix}_772 + 1,\n"
            f"            {fb771}, {corrections});")
    return "\n".join(lines)


SOUND_NAME_MAPS = """
    private static final Map<String, String> SOUND_NAME_FALLBACK_772_MAP =
            buildSoundNameMap(SOUND_NAME_FALLBACKS_772);
    private static final Map<String, String> SOUND_NAME_FALLBACK_771_TO_772_MAP =
            buildSoundNameMap(SOUND_NAME_FALLBACKS_771_TO_772);
    private static final Map<String, String> SOUND_NAME_FALLBACK_772_TO_771_MAP =
            buildSoundNameMap(SOUND_NAME_FALLBACKS_772_TO_771);
    private static final Map<String, String> SOUND_NAME_FALLBACK_770_TO_772_MAP =
            buildSoundNameMap(SOUND_NAME_FALLBACKS_770_TO_772);
    private static final Map<String, String> SOUND_NAME_FALLBACK_772_TO_770_MAP =
            buildSoundNameMap(SOUND_NAME_FALLBACKS_772_TO_770);
    private static final Map<String, String> SOUND_NAME_FALLBACK_769_TO_772_MAP =
            buildSoundNameMap(SOUND_NAME_FALLBACKS_769_TO_772);
    private static final Map<String, String> SOUND_NAME_FALLBACK_772_TO_769_MAP =
            buildSoundNameMap(SOUND_NAME_FALLBACKS_772_TO_769);
    private static final Map<String, String> SOUND_NAME_FALLBACK_772_TO_768_MAP =
            buildSoundNameMap(SOUND_NAME_FALLBACKS_772_TO_768);
    private static final Map<String, String> SOUND_NAME_FALLBACK_772_TO_767_MAP =
            buildSoundNameMap(SOUND_NAME_FALLBACKS_772_TO_767);
    private static final Map<String, String> SOUND_NAME_FALLBACK_772_TO_765_MAP =
            buildSoundNameMap(SOUND_NAME_FALLBACKS_772_TO_765);
    private static final Map<String, String> SOUND_NAME_FALLBACK_772_TO_763_MAP =
            buildSoundNameMap(SOUND_NAME_FALLBACKS_772_TO_763);
    private static final Map<String, String> SOUND_NAME_FALLBACK_772_TO_764_MAP =
            buildSoundNameMap(SOUND_NAME_FALLBACKS_772_TO_764);
    private static final Map<String, String> SOUND_NAME_FALLBACK_772_TO_766_MAP =
            buildSoundNameMap(SOUND_NAME_FALLBACKS_772_TO_766);
    private static final Map<String, String> SOUND_NAME_FALLBACK_768_TO_772_MAP =
            buildSoundNameMap(SOUND_NAME_FALLBACKS_768_TO_772);
    private static final Map<String, String> SOUND_NAME_FALLBACK_767_TO_772_MAP =
            buildSoundNameMap(SOUND_NAME_FALLBACKS_767_TO_772);
    private static final Map<String, String> SOUND_NAME_FALLBACK_766_TO_772_MAP =
            buildSoundNameMap(SOUND_NAME_FALLBACKS_766_TO_772);
"""

LOG_UP_772 = """
    private static void logUnknownUp772Once(String kind, int id772) {
        if (LOGGED_UNKNOWN.add("up772:" + kind + ":" + id772)) {
            LOG.warning("772に無い" + kind + "の番号が来たため代替に落とします（772 id=" + id772 + "）。");
        }
    }
"""
def adjacent_accessors():
    out = ["    // ------------------------------------------------------------------",
           "    // 771→772。上の *_772 表を使う。アイテム・音は 771 から 772 で",
           "    // music_disc_lava_chicken（アイテム 1256・音 939）の挿入で",
           "    // ずれる。ブロック状態・ブロック・エンティティ・パーティクル・",
           "    // メニュー・エフェクト・エンチャント・属性・バイオームは同一。",
           "    // ブロックエンティティ種別は 771 と同じ 47 件。絵画だけが",
           "    // 変わる（dennis が 14 に挿入され 51 件。恒等写像ではない）。",
           "    // ------------------------------------------------------------------",
           ""]
    for kind in KINDS:
        name, prefix, jp = kind[0], kind[1], kind[2]
        fb772 = kind[30]
        out.append(f"    /** 771の{jp}ID → 772の{jp}ID。範囲外は{fb772} に相当する代替。 */")
        out.append(f"    public static int map{name}771To772(int id771) {{")
        out.append(f"        if (id771 < 0 || id771 > MAX_{prefix}_771) {{")
        out.append(f"            logUnknownOnce(PROTOCOL_772, \"{jp}\", id771);")
        out.append(f"            return {fb772};")
        out.append("        }")
        out.append(f"        return {prefix}_772_MAP[id771];")
        out.append("    }")
        out.append("")
    out.append("    /**")
    out.append("     * 772のアイテムID → 771のアイテムID（サーバー行きスロット用）。")
    out.append("     * 772にしか無いアイテム（レコード「ラヴァ・チキン」1件）は 771の石へ。")
    out.append("     * 範囲外はそのまま返す。")
    out.append("     */")
    out.append("    public static int mapItemReverse772(int id772) {")
    out.append("        if (id772 < 0 || id772 >= ITEM_REVERSE_772_MAP.length) {")
    out.append("            logUnknownOnce(PROTOCOL_772, \"アイテム（逆変換）\", id772);")
    out.append("            return id772;")
    out.append("        }")
    out.append("        int mapped = ITEM_REVERSE_772_MAP[id772];")
    out.append("        return mapped < 0 ? id772 : mapped;")
    out.append("    }")
    out.append("")
    out.append("    /**")
    out.append("     * 771のエフェクトID → 772のエフェクトID。771も772も0ベース")
    out.append("     * （Speed は両方 0）で 0〜38 は同一のため恒等写像。")
    out.append("     * 範囲外は Speed（0）。")
    out.append("     */")
    out.append("    public static int mapEffect771To772(int id771) {")
    out.append("        if (id771 < 0 || id771 > MAX_EFFECT_771) {")
    out.append("            logUnknownOnce(PROTOCOL_772, \"エフェクト\", id771);")
    out.append("            return SPEED_EFFECT_772;")
    out.append("        }")
    out.append("        return id771;")
    out.append("    }")
    out.append("")
    out.append("    /**")
    out.append("     * 771のブロックエンティティ種別ID → 772の種別ID。")
    out.append("     * 771は47件、772は47件。並びが同一のため恒等写像。")
    out.append("     * 範囲外の種別は NBT の中身が種別専用のため安全に代替できず")
    out.append("     * {@link #DROP}。")
    out.append("     */")
    out.append("    public static int mapBlockEntityType771To772(int id771) {")
    out.append("        if (id771 < 0 || id771 > BLOCK_ENTITY_TEST_INSTANCE_BLOCK_771) {")
    out.append("            logUnknownOnce(PROTOCOL_772, \"ブロックエンティティ種別\", id771);")
    out.append("            return DROP;")
    out.append("        }")
    out.append("        return id771;")
    out.append("    }")
    out.append("")
    out.append("    /**")
    out.append("     * 音の名前（直書き・Stop Sound 用）を772向けに変換する。")
    out.append("     * {@code minecraft:} の有無はそのまま保つ。771で削除された")
    out.append("     * entity.leash_knot.break／place・entity.puffer_fish.ambient・")
    out.append("     * block.sand.wind は代替の名前にする。772で削除された音は無い。")
    out.append("     */")
    out.append("    public static String mapSoundName771To772(String name) {")
    out.append("        return mapSoundNameComposed(name, SOUND_NAME_FALLBACK_771_TO_772_MAP);")
    out.append("    }")
    out.append("")
    # 属性とエンチャント。
    out.append("    /** 771の属性ID → 772の属性ID。並びは同一。範囲外は {@link #DROP}。 */")
    out.append("    public static int mapAttribute771To772(int id771) {")
    out.append("        if (id771 < 0 || id771 > MAX_ATTRIBUTE_771) {")
    out.append("            logUnknownOnce(PROTOCOL_772, \"属性\", id771);")
    out.append("            return DROP;")
    out.append("        }")
    out.append("        return ATTRIBUTE_772_MAP[id771];")
    out.append("    }")
    out.append("")
    out.append("    /** 772の属性ID → 771の属性ID。並びは同一。範囲外は {@link #DROP}。 */")
    out.append("    public static int mapAttribute772To771(int id772) {")
    out.append("        if (id772 < 0 || id772 > MAX_ATTRIBUTE_772) {")
    out.append("            logUnknownOnce(PROTOCOL_772, \"属性\", id772);")
    out.append("            return DROP;")
    out.append("        }")
    out.append("        return ATTRIBUTE_REVERSE_772_MAP[id772];")
    out.append("    }")
    out.append("")
    out.append("    /** 772の属性ID → 属性の名前（Update Attributes の 771向け変換用）。 */")
    out.append("    public static String attributeName772(int id772) {")
    out.append("        if (id772 < 0 || id772 >= ATTRIBUTE_772_NAMES.length) {")
    out.append("            return null;")
    out.append("        }")
    out.append("        return ATTRIBUTE_772_NAMES[id772];")
    out.append("    }")
    out.append("")
    out.append("    /**")
    out.append("     * 属性の名前 → 772の属性ID（765／763／764 は属性が名前形式のため、")
    out.append("     * 772向けに変換するときに使う）。無い名前は {@link #DROP}。")
    out.append("     */")
    out.append("    public static int attributeId772(String name) {")
    out.append("        if (name == null) {")
    out.append("            return DROP;")
    out.append("        }")
    out.append("        String plain = stripMinecraftPrefix(name);")
    out.append("        for (int i = 0; i < ATTRIBUTE_772_NAMES.length; i++) {")
    out.append("            if (ATTRIBUTE_772_NAMES[i].equals(plain)) {")
    out.append("                return i;")
    out.append("            }")
    out.append("        }")
    out.append("        return DROP;")
    out.append("    }")
    out.append("")
    out.append("    /** 771のエンチャントID → 772のエンチャントID。並びは同一。範囲外は {@link #DROP}。 */")
    out.append("    public static int mapEnchantment771To772(int id771) {")
    out.append("        if (id771 < 0 || id771 > MAX_ENCHANTMENT_771) {")
    out.append("            logUnknownOnce(PROTOCOL_772, \"エンチャント\", id771);")
    out.append("            return DROP;")
    out.append("        }")
    out.append("        return ENCHANTMENT_772_MAP[id771];")
    out.append("    }")
    out.append("")
    out.append("    /** 772のエンチャントID → 771のエンチャントID。並びは同一。範囲外は {@link #DROP}。 */")
    out.append("    public static int mapEnchantment772To771(int id772) {")
    out.append("        if (id772 < 0 || id772 > MAX_ENCHANTMENT_772) {")
    out.append("            logUnknownOnce(PROTOCOL_772, \"エンチャント\", id772);")
    out.append("            return DROP;")
    out.append("        }")
    out.append("        return ENCHANTMENT_REVERSE_772_MAP[id772];")
    out.append("    }")
    out.append("")
    out.append("    /**")
    out.append("     * 767のエンチャントID → 772のエンチャントID。767→771 の表を")
    out.append("     * 経由して 771→772 の表を引く。範囲外は {@link #DROP}。")
    out.append("     */")
    out.append("    public static int mapEnchantment767To772(int id767) {")
    out.append("        int id771 = mapEnchantment767To771(id767);")
    out.append("        return id771 == DROP ? DROP : mapEnchantment771To772(id771);")
    out.append("    }")
    out.append("")
    out.append("    /**")
    out.append("     * 772のエンチャントID → 767のエンチャントID。772→771 の逆変換の")
    out.append("     * 結果を 771→767 の逆変換で戻す。範囲外は {@link #DROP}。")
    out.append("     */")
    out.append("    public static int mapEnchantment772To767(int id772) {")
    out.append("        int id771 = mapEnchantment772To771(id772);")
    out.append("        return id771 == DROP ? DROP : mapEnchantment771To767(id771);")
    out.append("    }")
    out.append("")
    out.append("    /**")
    out.append("     * 766のエンチャントID → 772のエンチャントID。766→767 の変換")
    out.append("     * （{@link #mapEnchantment766To767}）を経由して 767→772 の")
    out.append("     * 表を引く。範囲外は {@link #DROP}。")
    out.append("     */")
    out.append("    public static int mapEnchantment766To772(int id766) {")
    out.append("        int id767 = mapEnchantment766To767(id766);")
    out.append("        return id767 == DROP ? DROP : mapEnchantment767To772(id767);")
    out.append("    }")
    out.append("")
    out.append("    /**")
    out.append("     * 772のエンチャントID → 766のエンチャントID。772→767 の逆変換の")
    out.append("     * 結果を 767→766 の逆変換で戻す。範囲外は {@link #DROP}。")
    out.append("     */")
    out.append("    public static int mapEnchantment772To766(int id772) {")
    out.append("        int id767 = mapEnchantment772To767(id772);")
    out.append("        return id767 == DROP ? DROP : mapEnchantment767To766(id767);")
    out.append("    }")
    out.append("")
    out.append("    /**")
    out.append("     * 765のエンチャントID → 772のエンチャントID。765→767 の変換")
    out.append("     * （{@link #mapEnchantment765To767}）を経由して 767→772 の")
    out.append("     * 表を引く。範囲外は {@link #DROP}。")
    out.append("     * 763／764 のエンチャントの並びは 765 と同一のため、")
    out.append("     * 763／764 向けも同じ変換を使う。")
    out.append("     */")
    out.append("    public static int mapEnchantment765To772(int id765) {")
    out.append("        int id767 = mapEnchantment765To767(id765);")
    out.append("        return id767 == DROP ? DROP : mapEnchantment767To772(id767);")
    out.append("    }")
    out.append("")
    out.append("    /** 763のエンチャントID → 772のエンチャントID（765と同じ並び）。 */")
    out.append("    public static int mapEnchantment763To772(int id763) {")
    out.append("        return mapEnchantment765To772(id763);")
    out.append("    }")
    out.append("")
    out.append("    /** 764のエンチャントID → 772のエンチャントID（765と同じ並び）。 */")
    out.append("    public static int mapEnchantment764To772(int id764) {")
    out.append("        return mapEnchantment765To772(id764);")
    out.append("    }")
    out.append("")
    out.append("    /**")
    out.append("     * 772のエンチャントID → 765のエンチャントID。772→767 の逆変換の")
    out.append("     * 結果を 767→765 の変換（{@link #mapEnchantment767To765}）で")
    out.append("     * 戻す。density／breach／wind_burst は 765に無いため")
    out.append("     * {@link #DROP}。範囲外も {@link #DROP}。")
    out.append("     */")
    out.append("    public static int mapEnchantment772To765(int id772) {")
    out.append("        int id767 = mapEnchantment772To767(id772);")
    out.append("        return id767 == DROP ? DROP : mapEnchantment767To765(id767);")
    out.append("    }")
    out.append("")
    out.append("    /** 772のエンチャントID → 763のエンチャントID（765と同じ並び）。 */")
    out.append("    public static int mapEnchantment772To763(int id772) {")
    out.append("        return mapEnchantment772To765(id772);")
    out.append("    }")
    out.append("")
    out.append("    /** 772のエンチャントID → 764のエンチャントID（765と同じ並び）。 */")
    out.append("    public static int mapEnchantment772To764(int id772) {")
    out.append("        return mapEnchantment772To765(id772);")
    out.append("    }")
    return "\n".join(out)


def up_accessors():
    out = ["    // ------------------------------------------------------------------",
           "    // 逆向き（772→771）。上の *_772 表を反転して作った表を使う。",
           "    // アイテムだけは生成スクリプトが名前から作った既存の逆変換表",
           "    // （ITEM_REVERSE_772_MAP）をそのまま使う（反転した表と完全一致",
           "    // することはテストで確認している）。属性とエンチャントの逆向きは",
           "    // mapAttribute772To771／mapEnchantment772To771 を参照。",
           "    // ------------------------------------------------------------------",
           ""]
    for kind in KINDS:
        name, prefix, jp = kind[0], kind[1], kind[2]
        # 逆向きの代替先は「771 側」の定数（kind[26]）。
        fb771 = kind[29]
        if prefix == "ITEM":
            out.append("    /**")
            out.append("     * 772のアイテムID → 771のアイテムID。")
            out.append("     * 772にしか無いアイテム（レコード「ラヴァ・チキン」1件）は石へ。範囲外は石（1）。")
            out.append("     */")
            out.append("    public static int mapItemUp772(int id772) {")
            out.append("        if (id772 < 0 || id772 >= ITEM_REVERSE_772_MAP.length) {")
            out.append("            logUnknownUp772Once(\"アイテム\", id772);")
            out.append("            return STONE_ITEM_771;")
            out.append("        }")
            out.append("        int mapped = ITEM_REVERSE_772_MAP[id772];")
            out.append("        return mapped < 0 ? STONE_ITEM_771 : mapped;")
            out.append("    }")
            out.append("")
            continue
        out.append(f"    /** 772の{jp}ID → 771の{jp}ID。範囲外は{fb771} に相当する代替。 */")
        out.append(f"    public static int map{name}Up772(int id772) {{")
        out.append(f"        if (id772 < 0 || id772 > MAX_{prefix}_772) {{")
        out.append(f"            logUnknownUp772Once(\"{jp}\", id772);")
        out.append(f"            return {fb771};")
        out.append("        }")
        out.append(f"        return {prefix}_UP_772_MAP[id772];")
        out.append("    }")
        out.append("")
    out.append("    /**")
    out.append("     * 772のエフェクトID → 771のエフェクトID。0〜38 は同一のため恒等写像。")
    out.append("     * 範囲外は Speed（0）。")
    out.append("     */")
    out.append("    public static int mapEffectUp772(int id772) {")
    out.append("        if (id772 < 0 || id772 > MAX_EFFECT_772) {")
    out.append("            logUnknownUp772Once(\"エフェクト\", id772);")
    out.append("            return SPEED_EFFECT_771;")
    out.append("        }")
    out.append("        return id772;")
    out.append("    }")
    out.append("")
    out.append("    /**")
    out.append("     * 772のブロックエンティティ種別ID → 771の種別ID。")
    out.append("     * 771は47件、772は47件。並びが同一のため恒等写像。")
    out.append("     * 範囲外の種別は NBT の中身が種別専用のため安全に代替できず")
    out.append("     * {@link #DROP}。")
    out.append("     */")
    out.append("    public static int mapBlockEntityTypeUp772(int id772) {")
    out.append("        if (id772 < 0 || id772 > BLOCK_ENTITY_TEST_INSTANCE_BLOCK_772) {")
    out.append("            logUnknownUp772Once(\"ブロックエンティティ種別\", id772);")
    out.append("            return DROP;")
    out.append("        }")
    out.append("        return id772;")
    out.append("    }")
    out.append("")
    out.append("    /**")
    out.append("     * 音の名前を771向けに変換する。772の音の名前のうち 771に無いものは")
    out.append("     * 代替の音の名前にする（数値の反転表と一致させる）。772の新音")
    out.append("     * （レコード「ラヴァ・チキン」1件）は石の破壊音へ。")
    out.append("     * {@code null} は石の破壊音にする。")
    out.append("     */")
    out.append("    public static String mapSoundNameUp772(String name) {")
    out.append("        return mapSoundNameComposed(name, SOUND_NAME_FALLBACK_772_TO_771_MAP);")
    out.append("    }")
    return "\n".join(out)
def composed_accessors():
    out = ["    // ------------------------------------------------------------------",
           "    // 離れた組み合わせの合成表（763↔772、764↔772、765↔772、766↔772、",
           "    // 767↔772、768↔772、769↔772、770↔772）。上の *_763_TO_772／",
           "    // *_772_TO_763／*_764_TO_772／*_772_TO_764／*_765_TO_772／",
           "    // *_772_TO_765／*_766_TO_772／*_772_TO_766／*_767_TO_772／",
           "    // *_772_TO_767／*_768_TO_772／*_772_TO_768／*_769_TO_772／",
           "    // *_772_TO_769／*_770_TO_772／*_772_TO_770 表",
           "    // （合成済みの1つの表）",
           "    // を直接引く。二段変換はしない。エフェクトとブロックエンティティ",
           "    // 種別は表を作らず、数値の規則と同一の並びで扱う。",
           "    // ------------------------------------------------------------------",
           ""]
    pairs = [
        ("763", 21, "763→772", "772→763"),
        ("764", 22, "764→772", "772→764"),
        ("765", 23, "765→772", "772→765"),
        ("766", 24, "766→772", "772→766"),
        ("767", 25, "767→772", "772→767"),
        ("768", 26, "768→772", "772→768"),
        ("769", 27, "769→772", "772→769"),
        ("770", 28, "770→772", "772→770"),
    ]
    # 771 は隣接表（771↔772）で扱うため合成のペアには含めない。
    for src, fb_src_idx, up_label, down_label in pairs:
        for kind in KINDS:
            name, prefix, jp = kind[0], kind[1], kind[2]
            fb_src = kind[fb_src_idx]
            fb_772 = kind[30]
            # src → 772
            out.append(f"    /** {src}の{jp}ID → 772の{jp}ID。範囲外は{fb_772} に相当する代替。 */")
            out.append(f"    public static int map{name}{src}To772(int id{src}) {{")
            out.append(f"        if (id{src} < 0 || id{src} >= {prefix}_{src}_TO_772_MAP.length) {{")
            out.append(f"            logUnknownComposedOnce(\"{up_label}\", \"{jp}\", id{src});")
            out.append(f"            return {fb_772};")
            out.append("        }")
            out.append(f"        return {prefix}_{src}_TO_772_MAP[id{src}];")
            out.append("    }")
            out.append("")
            # 772 → src
            out.append(f"    /** 772の{jp}ID → {src}の{jp}ID。範囲外は{fb_src} に相当する代替。 */")
            out.append(f"    public static int map{name}772To{src}(int id772) {{")
            out.append(f"        if (id772 < 0 || id772 >= {prefix}_772_TO_{src}_MAP.length) {{")
            out.append(f"            logUnknownComposedOnce(\"{down_label}\", \"{jp}\", id772);")
            out.append(f"            return {fb_src};")
            out.append("        }")
            out.append(f"        return {prefix}_772_TO_{src}_MAP[id772];")
            out.append("    }")
            out.append("")
        # エフェクトとブロックエンティティ種別と音の名前。
        if src == "763":
            out.append("    /**")
            out.append("     * 763のエフェクトID → 772のエフェクトID。763は1ベース")
            out.append("     * （Speed=1）のため 1 引く。範囲外は Speed（0）。")
            out.append("     */")
            out.append("    public static int mapEffect763To772(int id763) {")
            out.append("        if (id763 < 1 || id763 > MAX_EFFECT_765 + 1) {")
            out.append("            logUnknownComposedOnce(\"763→772\", \"エフェクト\", id763);")
            out.append("            return SPEED_EFFECT_772;")
            out.append("        }")
            out.append("        return id763 - 1;")
            out.append("    }")
            out.append("")
            out.append("    /**")
            out.append("     * 772のエフェクトID → 763のエフェクトID。763は1ベース")
            out.append("     * （Speed=1）のため 1 足す。772で増えた 33〜38 は")
            out.append("     * 763に無いため Speed（1）へ。範囲外も Speed（1）。")
            out.append("     */")
            out.append("    public static int mapEffect772To763(int id772) {")
            out.append("        if (id772 < 0 || id772 > MAX_EFFECT_772) {")
            out.append("            logUnknownComposedOnce(\"772→763\", \"エフェクト\", id772);")
            out.append("            return SPEED_EFFECT_763;")
            out.append("        }")
            out.append("        return id772 <= MAX_EFFECT_765 ? id772 + 1 : SPEED_EFFECT_763;")
            out.append("    }")
            out.append("")
            out.append("    /**")
            out.append("     * 763のブロックエンティティ種別ID → 772の種別ID。")
            out.append("     * 763の種別は 0〜9 で772と同じ並びのためそのまま。")
            out.append("     * creaking_heart（10）の挿入で piston 以降（10〜40）は +1 ずれる。")
            out.append("     * 763に無い番号（41以上）は772側の種別に対応付けられないため")
            out.append("     * {@link #DROP}。")
            out.append("     */")
            out.append("    public static int mapBlockEntityType763To772(int id763) {")
            out.append("        if (id763 < 0 || id763 > BLOCK_ENTITY_TRIAL_SPAWNER_765 - 2) {")
            out.append("            logUnknownComposedOnce(\"763→772\", \"ブロックエンティティ種別\", id763);")
            out.append("            return DROP;")
            out.append("        }")
            out.append("        return id763 < 10 ? id763 : id763 + 1;")
            out.append("    }")
            out.append("")
            out.append("    /**")
            out.append("     * 772のブロックエンティティ種別ID → 763の種別ID。")
            out.append("     * 772の種別は 0〜9 で763と同じ並びのためそのまま。")
            out.append("     * creaking_heart（10）は763に無いため {@link #DROP}。")
            out.append("     * piston 以降（11〜41）は -1 ずれる。crafter（42）・")
            out.append("     * trial_spawner（43）・vault（44）・test_block（45）・test_instance_block（46）は763に無いため {@link #DROP}。")
            out.append("     */")
            out.append("    public static int mapBlockEntityType772To763(int id772) {")
            out.append("        if (id772 < 0 || id772 > BLOCK_ENTITY_TRIAL_SPAWNER_772 - 2) {")
            out.append("            logUnknownComposedOnce(\"772→763\", \"ブロックエンティティ種別\", id772);")
            out.append("            return DROP;")
            out.append("        }")
            out.append("        if (id772 == BLOCK_ENTITY_CREAKING_HEART_772) {")
            out.append("            return DROP;")
            out.append("        }")
            out.append("        return id772 < 10 ? id772 : id772 - 1;")
            out.append("    }")
            out.append("")
            out.append("    /**")
            out.append("     * 音の名前を772向けに変換する。763の音の名前のうち 768で削除された")
            out.append("     * ヤギの角笛の音2件と 770で削除された entity.wolf.howl と")
            out.append("     * 771で削除された leash_knot 系・puffer_fish.ambient・")
            out.append("     * block.sand.wind は代替の名前にする。{@code null} は石の破壊音にする。")
            out.append("     */")
            out.append("    public static String mapSoundName763To772(String name) {")
            out.append("        return mapSoundNameComposed(")
            out.append("                mapSoundNameComposed(")
            out.append("                        mapSoundNameComposed(")
            out.append("                                mapSoundNameComposed(name, SOUND_NAME_FALLBACK_768_MAP),")
            out.append("                                SOUND_NAME_FALLBACK_770_MAP),")
            out.append("                        SOUND_NAME_FALLBACK_771_MAP),")
            out.append("                SOUND_NAME_FALLBACK_772_MAP);")
            out.append("    }")
            out.append("")
            out.append("    /**")
            out.append("     * 音の名前を763向けに変換する。772の音の名前のうち 763に無いものは")
            out.append("     * 代替の音の名前にする（数値の合成表と一致させる）。")
            out.append("     * {@code null} は石の破壊音にする。")
            out.append("     */")
            out.append("    public static String mapSoundName772To763(String name) {")
            out.append("        return mapSoundNameComposed(name, SOUND_NAME_FALLBACK_772_TO_763_MAP);")
            out.append("    }")
        elif src == "764":
            out.append("    /**")
            out.append("     * 764のエフェクトID → 772のエフェクトID。764も772も0ベース")
            out.append("     * （Speed は両方 0）で 0〜32 は同一のため恒等写像。")
            out.append("     * 範囲外は Speed（0）。")
            out.append("     */")
            out.append("    public static int mapEffect764To772(int id764) {")
            out.append("        if (id764 < 0 || id764 > MAX_EFFECT_764) {")
            out.append("            logUnknownComposedOnce(\"764→772\", \"エフェクト\", id764);")
            out.append("            return SPEED_EFFECT_772;")
            out.append("        }")
            out.append("        return id764;")
            out.append("    }")
            out.append("")
            out.append("    /**")
            out.append("     * 772のエフェクトID → 764のエフェクトID。0〜32 は同一のため恒等写像。")
            out.append("     * 772で増えた 33〜38 は 764に無いため Speed（0）へ。")
            out.append("     * 範囲外も Speed（0）。")
            out.append("     */")
            out.append("    public static int mapEffect772To764(int id772) {")
            out.append("        if (id772 < 0 || id772 > MAX_EFFECT_772) {")
            out.append("            logUnknownComposedOnce(\"772→764\", \"エフェクト\", id772);")
            out.append("            return SPEED_EFFECT_764;")
            out.append("        }")
            out.append("        return id772 <= MAX_EFFECT_764 ? id772 : SPEED_EFFECT_764;")
            out.append("    }")
            out.append("")
            out.append("    /**")
            out.append("     * 764のブロックエンティティ種別ID → 772の種別ID。")
            out.append("     * 764の種別は 0〜9 で772と同じ並びのためそのまま。")
            out.append("     * creaking_heart（10）の挿入で piston 以降（10〜40）は +1 ずれる。")
            out.append("     * 764に無い番号（41以上）は772側の種別に対応付けられないため")
            out.append("     * {@link #DROP}。")
            out.append("     */")
            out.append("    public static int mapBlockEntityType764To772(int id764) {")
            out.append("        if (id764 < 0 || id764 > BLOCK_ENTITY_TRIAL_SPAWNER_765 - 2) {")
            out.append("            logUnknownComposedOnce(\"764→772\", \"ブロックエンティティ種別\", id764);")
            out.append("            return DROP;")
            out.append("        }")
            out.append("        return id764 < 10 ? id764 : id764 + 1;")
            out.append("    }")
            out.append("")
            out.append("    /**")
            out.append("     * 772のブロックエンティティ種別ID → 764の種別ID。")
            out.append("     * 772の種別は 0〜9 で764と同じ並びのためそのまま。")
            out.append("     * creaking_heart（10）は764に無いため {@link #DROP}。")
            out.append("     * piston 以降（11〜41）は -1 ずれる。crafter（42）・")
            out.append("     * trial_spawner（43）・vault（44）・test_block（45）・test_instance_block（46）は764に無いため {@link #DROP}。")
            out.append("     */")
            out.append("    public static int mapBlockEntityType772To764(int id772) {")
            out.append("        if (id772 < 0 || id772 > BLOCK_ENTITY_TRIAL_SPAWNER_772 - 2) {")
            out.append("            logUnknownComposedOnce(\"772→764\", \"ブロックエンティティ種別\", id772);")
            out.append("            return DROP;")
            out.append("        }")
            out.append("        if (id772 == BLOCK_ENTITY_CREAKING_HEART_772) {")
            out.append("            return DROP;")
            out.append("        }")
            out.append("        return id772 < 10 ? id772 : id772 - 1;")
            out.append("    }")
            out.append("")
            out.append("    /**")
            out.append("     * 音の名前を772向けに変換する。764の音の名前のうち 768で削除された")
            out.append("     * ヤギの角笛の音2件と 770で削除された entity.wolf.howl と")
            out.append("     * 771で削除された leash_knot 系・puffer_fish.ambient・")
            out.append("     * block.sand.wind は代替の名前にする。{@code null} は石の破壊音にする。")
            out.append("     */")
            out.append("    public static String mapSoundName764To772(String name) {")
            out.append("        return mapSoundNameComposed(")
            out.append("                mapSoundNameComposed(")
            out.append("                        mapSoundNameComposed(")
            out.append("                                mapSoundNameComposed(name, SOUND_NAME_FALLBACK_768_MAP),")
            out.append("                                SOUND_NAME_FALLBACK_770_MAP),")
            out.append("                        SOUND_NAME_FALLBACK_771_MAP),")
            out.append("                SOUND_NAME_FALLBACK_772_MAP);")
            out.append("    }")
            out.append("")
            out.append("    /**")
            out.append("     * 音の名前を764向けに変換する。772の音の名前のうち 764に無いものは")
            out.append("     * 代替の音の名前にする（数値の合成表と一致させる）。")
            out.append("     * {@code null} は石の破壊音にする。")
            out.append("     */")
            out.append("    public static String mapSoundName772To764(String name) {")
            out.append("        return mapSoundNameComposed(name, SOUND_NAME_FALLBACK_772_TO_764_MAP);")
            out.append("    }")
        elif src == "765":
            out.append("    /**")
            out.append("     * 765のエフェクトID → 772のエフェクトID。765も772も0ベース")
            out.append("     * （Speed は両方 0）で 0〜32 は同一のため恒等写像。")
            out.append("     * 範囲外は Speed（0）。")
            out.append("     */")
            out.append("    public static int mapEffect765To772(int id765) {")
            out.append("        if (id765 < 0 || id765 > MAX_EFFECT_765) {")
            out.append("            logUnknownComposedOnce(\"765→772\", \"エフェクト\", id765);")
            out.append("            return SPEED_EFFECT_772;")
            out.append("        }")
            out.append("        return id765;")
            out.append("    }")
            out.append("")
            out.append("    /**")
            out.append("     * 772のエフェクトID → 765のエフェクトID。0〜32 は同一のため恒等写像。")
            out.append("     * 772で増えた 33〜38 は 765に無いため Speed（0）へ。")
            out.append("     * 範囲外も Speed（0）。")
            out.append("     */")
            out.append("    public static int mapEffect772To765(int id772) {")
            out.append("        if (id772 < 0 || id772 > MAX_EFFECT_772) {")
            out.append("            logUnknownComposedOnce(\"772→765\", \"エフェクト\", id772);")
            out.append("            return SPEED_EFFECT_765;")
            out.append("        }")
            out.append("        return id772 <= MAX_EFFECT_765 ? id772 : SPEED_EFFECT_765;")
            out.append("    }")
            out.append("")
            out.append("    /**")
            out.append("     * 765のブロックエンティティ種別ID → 772の種別ID。")
            out.append("     * crafter（41）と trial_spawner（42）は772にもあるが、")
            out.append("     * creaking_heart（10）の挿入で +1 ずれる。")
            out.append("     * 範囲外の種別は NBT の中身が種別専用のため安全に代替できず")
            out.append("     * {@link #DROP}。")
            out.append("     */")
            out.append("    public static int mapBlockEntityType765To772(int id765) {")
            out.append("        if (id765 < 0 || id765 > BLOCK_ENTITY_TRIAL_SPAWNER_765) {")
            out.append("            logUnknownComposedOnce(\"765→772\", \"ブロックエンティティ種別\", id765);")
            out.append("            return DROP;")
            out.append("        }")
            out.append("        return id765 < 10 ? id765 : id765 + 1;")
            out.append("    }")
            out.append("")
            out.append("    /**")
            out.append("     * 772のブロックエンティティ種別ID → 765の種別ID。")
            out.append("     * creaking_heart（10）は765に無いため {@link #DROP}。")
            out.append("     * piston 以降（11〜43）は -1 ずれる。vault（44）・test_block（45）・")
            out.append("     * test_instance_block（46）は765に無いため {@link #DROP}。")
            out.append("     */")
            out.append("    public static int mapBlockEntityType772To765(int id772) {")
            out.append("        if (id772 < 0 || id772 > BLOCK_ENTITY_TRIAL_SPAWNER_772) {")
            out.append("            logUnknownComposedOnce(\"772→765\", \"ブロックエンティティ種別\", id772);")
            out.append("            return DROP;")
            out.append("        }")
            out.append("        if (id772 == BLOCK_ENTITY_CREAKING_HEART_772) {")
            out.append("            return DROP;")
            out.append("        }")
            out.append("        return id772 < 10 ? id772 : id772 - 1;")
            out.append("    }")
            out.append("")
            out.append("    /**")
            out.append("     * 音の名前を772向けに変換する。765の音の名前のうち 766で改名された")
            out.append("     * entity.generic.wind_burst と 767で改名された trial_spawner の音2件は")
            out.append("     * 新しい名前にし、768で削除されたヤギの角笛の音2件と 770で削除された")
            out.append("     * entity.wolf.howl と 771で削除された leash_knot 系・")
            out.append("     * puffer_fish.ambient・block.sand.wind は代替の名前にする。")
            out.append("     * {@code null} は石の破壊音にする。")
            out.append("     */")
            out.append("    public static String mapSoundName765To772(String name) {")
            out.append("        return mapSoundNameComposed(")
            out.append("                mapSoundNameComposed(")
            out.append("                        mapSoundNameComposed(")
            out.append("                                mapSoundNameComposed(")
            out.append("                                        mapSoundNameComposed(")
            out.append("                                                mapSoundNameComposed(name, SOUND_NAME_FALLBACK_766_MAP),")
            out.append("                                                SOUND_NAME_FALLBACK_767_MAP),")
            out.append("                                        SOUND_NAME_FALLBACK_768_MAP),")
            out.append("                                SOUND_NAME_FALLBACK_770_MAP),")
            out.append("                        SOUND_NAME_FALLBACK_771_MAP),")
            out.append("                SOUND_NAME_FALLBACK_772_MAP);")
            out.append("    }")
            out.append("")
            out.append("    /**")
            out.append("     * 音の名前を765向けに変換する。772の音の名前のうち 765に無いものは")
            out.append("     * 代替の音の名前にする（数値の合成表と一致させる）。")
            out.append("     * {@code null} は石の破壊音にする。")
            out.append("     */")
            out.append("    public static String mapSoundName772To765(String name) {")
            out.append("        return mapSoundNameComposed(name, SOUND_NAME_FALLBACK_772_TO_765_MAP);")
            out.append("    }")
        elif src == "766":
            out.append("    /**")
            out.append("     * 766のエフェクトID → 772のエフェクトID。766も772も0ベース")
            out.append("     * （Speed は両方 0）で 0〜38 は同一のため恒等写像。")
            out.append("     * 範囲外は Speed（0）。")
            out.append("     */")
            out.append("    public static int mapEffect766To772(int id766) {")
            out.append("        if (id766 < 0 || id766 > MAX_EFFECT_766) {")
            out.append("            logUnknownComposedOnce(\"766→772\", \"エフェクト\", id766);")
            out.append("            return SPEED_EFFECT_772;")
            out.append("        }")
            out.append("        return id766;")
            out.append("    }")
            out.append("")
            out.append("    /**")
            out.append("     * 772のエフェクトID → 766のエフェクトID。0〜38 は同一のため恒等写像。")
            out.append("     * 範囲外は Speed（0）。")
            out.append("     */")
            out.append("    public static int mapEffect772To766(int id772) {")
            out.append("        if (id772 < 0 || id772 > MAX_EFFECT_772) {")
            out.append("            logUnknownComposedOnce(\"772→766\", \"エフェクト\", id772);")
            out.append("            return SPEED_EFFECT_766;")
            out.append("        }")
            out.append("        return id772;")
            out.append("    }")
            out.append("")
            out.append("    /**")
            out.append("     * 766のブロックエンティティ種別ID → 772の種別ID。")
            out.append("     * vault（43）は772にもあるが、creaking_heart（10）の挿入で")
            out.append("     * piston 以降（10〜43）は +1 ずれる。")
            out.append("     * 範囲外の種別は NBT の中身が種別専用のため安全に代替できず")
            out.append("     * {@link #DROP}。")
            out.append("     */")
            out.append("    public static int mapBlockEntityType766To772(int id766) {")
            out.append("        if (id766 < 0 || id766 > BLOCK_ENTITY_VAULT_766) {")
            out.append("            logUnknownComposedOnce(\"766→772\", \"ブロックエンティティ種別\", id766);")
            out.append("            return DROP;")
            out.append("        }")
            out.append("        return id766 < 10 ? id766 : id766 + 1;")
            out.append("    }")
            out.append("")
            out.append("    /**")
            out.append("     * 772のブロックエンティティ種別ID → 766の種別ID。")
            out.append("     * creaking_heart（10）は766に無いため {@link #DROP}。")
            out.append("     * piston 以降（11〜44）は -1 ずれる。vault は 772=44 → 766=43。")
            out.append("     * test_block（45）・test_instance_block（46）は766に無いため DROP。")
            out.append("     */")
            out.append("    public static int mapBlockEntityType772To766(int id772) {")
            out.append("        if (id772 < 0 || id772 > BLOCK_ENTITY_VAULT_772) {")
            out.append("            logUnknownComposedOnce(\"772→766\", \"ブロックエンティティ種別\", id772);")
            out.append("            return DROP;")
            out.append("        }")
            out.append("        if (id772 == BLOCK_ENTITY_CREAKING_HEART_772) {")
            out.append("            return DROP;")
            out.append("        }")
            out.append("        return id772 < 10 ? id772 : id772 - 1;")
            out.append("    }")
            out.append("")
            out.append("    /**")
            out.append("     * 音の名前を772向けに変換する。766の音の名前のうち 767で改名された")
            out.append("     * trial_spawner の音2件は新しい名前にし、768で削除されたヤギの角笛の音2件と")
            out.append("     * 770で削除された entity.wolf.howl と 771で削除された leash_knot 系・")
            out.append("     * puffer_fish.ambient・block.sand.wind は代替の名前にする。")
            out.append("     * {@code null} は石の破壊音にする。")
            out.append("     */")
            out.append("    public static String mapSoundName766To772(String name) {")
            out.append("        return mapSoundNameComposed(name, SOUND_NAME_FALLBACK_766_TO_772_MAP);")
            out.append("    }")
            out.append("")
            out.append("    /**")
            out.append("     * 音の名前を766向けに変換する。772の音の名前のうち 766に無いものは")
            out.append("     * 代替の音の名前にする（数値の合成表と一致させる）。")
            out.append("     * {@code null} は石の破壊音にする。")
            out.append("     */")
            out.append("    public static String mapSoundName772To766(String name) {")
            out.append("        return mapSoundNameComposed(name, SOUND_NAME_FALLBACK_772_TO_766_MAP);")
            out.append("    }")
        elif src == "768":
            out.append("    /**")
            out.append("     * 768のエフェクトID → 772のエフェクトID。768も772も0ベース")
            out.append("     * （Speed は両方 0）で 0〜38 は同一のため恒等写像。")
            out.append("     * 範囲外は Speed（0）。")
            out.append("     */")
            out.append("    public static int mapEffect768To772(int id768) {")
            out.append("        if (id768 < 0 || id768 > MAX_EFFECT_768) {")
            out.append("            logUnknownComposedOnce(\"768→772\", \"エフェクト\", id768);")
            out.append("            return SPEED_EFFECT_772;")
            out.append("        }")
            out.append("        return id768;")
            out.append("    }")
            out.append("")
            out.append("    /**")
            out.append("     * 772のエフェクトID → 768のエフェクトID。0〜38 は同一のため恒等写像。")
            out.append("     * 範囲外は Speed（0）。")
            out.append("     */")
            out.append("    public static int mapEffect772To768(int id772) {")
            out.append("        if (id772 < 0 || id772 > MAX_EFFECT_772) {")
            out.append("            logUnknownComposedOnce(\"772→768\", \"エフェクト\", id772);")
            out.append("            return SPEED_EFFECT_768;")
            out.append("        }")
            out.append("        return id772;")
            out.append("    }")
            out.append("")
            out.append("    /**")
            out.append("     * 768のブロックエンティティ種別ID → 772の種別ID。")
            out.append("     * 768の種別は 0〜44 で772と同じ並びのためそのまま。")
            out.append("     * 範囲外の種別は NBT の中身が種別専用のため安全に代替できず")
            out.append("     * {@link #DROP}。")
            out.append("     */")
            out.append("    public static int mapBlockEntityType768To772(int id768) {")
            out.append("        if (id768 < 0 || id768 > BLOCK_ENTITY_VAULT_768) {")
            out.append("            logUnknownComposedOnce(\"768→772\", \"ブロックエンティティ種別\", id768);")
            out.append("            return DROP;")
            out.append("        }")
            out.append("        return id768;")
            out.append("    }")
            out.append("")
            out.append("    /**")
            out.append("     * 772のブロックエンティティ種別ID → 768の種別ID。")
            out.append("     * 0〜44 は並びが同一のためそのまま。test_block（45）・")
            out.append("     * test_instance_block（46）は768に無いため {@link #DROP}。")
            out.append("     */")
            out.append("    public static int mapBlockEntityType772To768(int id772) {")
            out.append("        if (id772 < 0 || id772 > BLOCK_ENTITY_VAULT_772) {")
            out.append("            logUnknownComposedOnce(\"772→768\", \"ブロックエンティティ種別\", id772);")
            out.append("            return DROP;")
            out.append("        }")
            out.append("        return id772;")
            out.append("    }")
            out.append("")
            out.append("    /**")
            out.append("     * 音の名前を772向けに変換する。768の音の名前のうち 770で削除された")
            out.append("     * entity.wolf.howl と 771で削除された leash_knot 系・")
            out.append("     * puffer_fish.ambient・block.sand.wind は代替の名前にする。")
            out.append("     * {@code null} は石の破壊音にする。")
            out.append("     */")
            out.append("    public static String mapSoundName768To772(String name) {")
            out.append("        return mapSoundNameComposed(name, SOUND_NAME_FALLBACK_768_TO_772_MAP);")
            out.append("    }")
            out.append("")
            out.append("    /**")
            out.append("     * 音の名前を768向けに変換する。772の音の名前のうち 768に無いものは")
            out.append("     * 代替の音の名前にする（数値の合成表と一致させる）。")
            out.append("     * {@code null} は石の破壊音にする。")
            out.append("     */")
            out.append("    public static String mapSoundName772To768(String name) {")
            out.append("        return mapSoundNameComposed(name, SOUND_NAME_FALLBACK_772_TO_768_MAP);")
            out.append("    }")
        elif src == "767":
            out.append("    /**")
            out.append("     * 767のエフェクトID → 772のエフェクトID。767も772も0ベース")
            out.append("     * （Speed は両方 0）で 0〜38 は同一のため恒等写像。")
            out.append("     * 範囲外は Speed（0）。")
            out.append("     */")
            out.append("    public static int mapEffect767To772(int id767) {")
            out.append("        if (id767 < 0 || id767 > MAX_EFFECT_767) {")
            out.append("            logUnknownComposedOnce(\"767→772\", \"エフェクト\", id767);")
            out.append("            return SPEED_EFFECT_772;")
            out.append("        }")
            out.append("        return id767;")
            out.append("    }")
            out.append("")
            out.append("    /**")
            out.append("     * 772のエフェクトID → 767のエフェクトID。0〜38 は同一のため恒等写像。")
            out.append("     * 範囲外は Speed（0）。")
            out.append("     */")
            out.append("    public static int mapEffect772To767(int id772) {")
            out.append("        if (id772 < 0 || id772 > MAX_EFFECT_772) {")
            out.append("            logUnknownComposedOnce(\"772→767\", \"エフェクト\", id772);")
            out.append("            return SPEED_EFFECT_767;")
            out.append("        }")
            out.append("        return id772;")
            out.append("    }")
            out.append("")
            out.append("    /**")
            out.append("     * 767のブロックエンティティ種別ID → 772の種別ID。")
            out.append("     * vault（43）は772にもあるが、creaking_heart（10）の挿入で")
            out.append("     * piston 以降（10〜43）は +1 ずれる。")
            out.append("     * 範囲外の種別は NBT の中身が種別専用のため安全に代替できず")
            out.append("     * {@link #DROP}。")
            out.append("     */")
            out.append("    public static int mapBlockEntityType767To772(int id767) {")
            out.append("        if (id767 < 0 || id767 > BLOCK_ENTITY_VAULT_766) {")
            out.append("            logUnknownComposedOnce(\"767→772\", \"ブロックエンティティ種別\", id767);")
            out.append("            return DROP;")
            out.append("        }")
            out.append("        return id767 < 10 ? id767 : id767 + 1;")
            out.append("    }")
            out.append("")
            out.append("    /**")
            out.append("     * 772のブロックエンティティ種別ID → 767の種別ID。")
            out.append("     * creaking_heart（10）は767に無いため {@link #DROP}。")
            out.append("     * piston 以降（11〜44）は -1 ずれる。vault は 772=44 → 767=43。")
            out.append("     * test_block（45）・test_instance_block（46）は767に無いため DROP。")
            out.append("     */")
            out.append("    public static int mapBlockEntityType772To767(int id772) {")
            out.append("        if (id772 < 0 || id772 > BLOCK_ENTITY_VAULT_772) {")
            out.append("            logUnknownComposedOnce(\"772→767\", \"ブロックエンティティ種別\", id772);")
            out.append("            return DROP;")
            out.append("        }")
            out.append("        if (id772 == BLOCK_ENTITY_CREAKING_HEART_772) {")
            out.append("            return DROP;")
            out.append("        }")
            out.append("        return id772 < 10 ? id772 : id772 - 1;")
            out.append("    }")
            out.append("")
            out.append("    /**")
            out.append("     * 音の名前を772向けに変換する。767の音の名前のうち 768で削除された")
            out.append("     * ヤギの角笛の音2件と 770で削除された entity.wolf.howl と")
            out.append("     * 771で削除された leash_knot 系・puffer_fish.ambient・")
            out.append("     * block.sand.wind は代替の名前にする。")
            out.append("     * {@code null} は石の破壊音にする。")
            out.append("     */")
            out.append("    public static String mapSoundName767To772(String name) {")
            out.append("        return mapSoundNameComposed(name, SOUND_NAME_FALLBACK_767_TO_772_MAP);")
            out.append("    }")
            out.append("")
            out.append("    /**")
            out.append("     * 音の名前を767向けに変換する。772の音の名前のうち 767に無いものは")
            out.append("     * 代替の音の名前にする（数値の合成表と一致させる）。")
            out.append("     * {@code null} は石の破壊音にする。")
            out.append("     */")
            out.append("    public static String mapSoundName772To767(String name) {")
            out.append("        return mapSoundNameComposed(name, SOUND_NAME_FALLBACK_772_TO_767_MAP);")
            out.append("    }")
        elif src == "769":
            out.append("    /**")
            out.append("     * 769のエフェクトID → 772のエフェクトID。769も772も0ベース")
            out.append("     * （Speed は両方 0）で 0〜38 は同一のため恒等写像。")
            out.append("     * 範囲外は Speed（0）。")
            out.append("     */")
            out.append("    public static int mapEffect769To772(int id769) {")
            out.append("        if (id769 < 0 || id769 > MAX_EFFECT_769) {")
            out.append("            logUnknownComposedOnce(\"769→772\", \"エフェクト\", id769);")
            out.append("            return SPEED_EFFECT_772;")
            out.append("        }")
            out.append("        return id769;")
            out.append("    }")
            out.append("")
            out.append("    /**")
            out.append("     * 772のエフェクトID → 769のエフェクトID。0〜38 は同一のため恒等写像。")
            out.append("     * 範囲外は Speed（0）。")
            out.append("     */")
            out.append("    public static int mapEffect772To769(int id772) {")
            out.append("        if (id772 < 0 || id772 > MAX_EFFECT_772) {")
            out.append("            logUnknownComposedOnce(\"772→769\", \"エフェクト\", id772);")
            out.append("            return SPEED_EFFECT_769;")
            out.append("        }")
            out.append("        return id772;")
            out.append("    }")
            out.append("")
            out.append("    /**")
            out.append("     * 769のブロックエンティティ種別ID → 772の種別ID。")
            out.append("     * 769の種別は 0〜44 で772と同じ並びのためそのまま。")
            out.append("     * 範囲外の種別は NBT の中身が種別専用のため安全に代替できず")
            out.append("     * {@link #DROP}。")
            out.append("     */")
            out.append("    public static int mapBlockEntityType769To772(int id769) {")
            out.append("        if (id769 < 0 || id769 > BLOCK_ENTITY_VAULT_769) {")
            out.append("            logUnknownComposedOnce(\"769→772\", \"ブロックエンティティ種別\", id769);")
            out.append("            return DROP;")
            out.append("        }")
            out.append("        return id769;")
            out.append("    }")
            out.append("")
            out.append("    /**")
            out.append("     * 772のブロックエンティティ種別ID → 769の種別ID。")
            out.append("     * 0〜44 は並びが同一のためそのまま。test_block（45）・")
            out.append("     * test_instance_block（46）は769に無いため {@link #DROP}。")
            out.append("     */")
            out.append("    public static int mapBlockEntityType772To769(int id772) {")
            out.append("        if (id772 < 0 || id772 > BLOCK_ENTITY_VAULT_772) {")
            out.append("            logUnknownComposedOnce(\"772→769\", \"ブロックエンティティ種別\", id772);")
            out.append("            return DROP;")
            out.append("        }")
            out.append("        return id772;")
            out.append("    }")
            out.append("")
            out.append("    /**")
            out.append("     * 音の名前を772向けに変換する。769の音の名前のうち 770で削除された")
            out.append("     * entity.wolf.howl と 771で削除された leash_knot 系・")
            out.append("     * puffer_fish.ambient・block.sand.wind は代替の名前にする。")
            out.append("     * {@code null} は石の破壊音にする。")
            out.append("     */")
            out.append("    public static String mapSoundName769To772(String name) {")
            out.append("        return mapSoundNameComposed(name, SOUND_NAME_FALLBACK_769_TO_772_MAP);")
            out.append("    }")
            out.append("")
            out.append("    /**")
            out.append("     * 音の名前を769向けに変換する。772の音の名前のうち 769に無いものは")
            out.append("     * 代替の音の名前にする（数値の合成表と一致させる）。")
            out.append("     * {@code null} は石の破壊音にする。")
            out.append("     */")
            out.append("    public static String mapSoundName772To769(String name) {")
            out.append("        return mapSoundNameComposed(name, SOUND_NAME_FALLBACK_772_TO_769_MAP);")
            out.append("    }")
        elif src == "770":
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
    return "\n".join(out)

# バナー・絵画・装飾パターン・ダメージ種別の 772 向け（771 と同一のものは
# 恒等写像。ダメージ種別も 771 と同一の 49 件）。
DAMAGE_TYPE_772 = """
    /** 772のダメージ種別最大ID（49件。771と同一）。 */
    public static final int MAX_DAMAGE_TYPE_772 = 48;

    /** ダメージ種別ID：771 → 772（771と同一の 49 件で並びも同じため恒等写像）。
     * 実サーバーの Registry Data での確認は統合テストで行う。 */
    private static final int[] DAMAGE_TYPE_772_MAP = {
        0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19,
        20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32, 33, 34, 35, 36,
        37, 38, 39, 40, 41, 42, 43, 44, 45, 46, 47, 48
    };

    /** ダメージ種別ID：772 → 771（771と同一の 49 件で並びも同じため恒等写像）。 */
    private static final int[] DAMAGE_TYPE_REVERSE_772_MAP = {
        0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19,
        20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32, 33, 34, 35, 36,
        37, 38, 39, 40, 41, 42, 43, 44, 45, 46, 47, 48
    };
"""

SMALL_REGISTRY_CONSTANTS = """
    /** 772のバナーパターン最大ID（43件。771と同一と仮定。実バニラの
        Registry Data での確認は統合テストで行う）。 */
    public static final int MAX_BANNER_PATTERN_772 = 42;

    /** 772の絵画バリアント最大ID（51件。dennis を 14 に追加。実バニラの
        Registry Data での確認は統合テストで行う）。 */
    public static final int MAX_PAINTING_VARIANT_772 = 50;

    /** 772の装飾パターン最大ID（18件。771と同一と仮定。実バニラの
        Registry Data での確認は統合テストで行う）。 */
    public static final int MAX_TRIM_PATTERN_772 = 17;
"""


def small_registry_accessors():
    out = []
    specs = [
        ("BannerPattern", "バナーパターン", "MAX_BANNER_PATTERN_771", "MAX_BANNER_PATTERN_772"),
        ("PaintingVariant", "絵画バリアント", "MAX_PAINTING_VARIANT_771", "MAX_PAINTING_VARIANT_772"),
        ("TrimPattern", "装飾パターン", "MAX_TRIM_PATTERN_771", "MAX_TRIM_PATTERN_772"),
    ]
    for name, jp, max771, max772 in specs:
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
        out.append("")
        for src in ("763", "764", "765", "766", "767", "768", "769", "770"):
            # 771 のメソッドを経由する（763／764／765／766／767／768／769／770→771 と
            # 771→763／764／765／766／767／768／769／770 は既にある）。
            out.append(f"    /** {src}の{jp}ID → 772の{jp}ID（771経由）。 */")
            out.append(f"    public static int map{name}{src}To772(int id{src}) {{")
            out.append(f"        return map{name}771To772(map{name}{src}To771(id{src}));")
            out.append("    }")
            out.append("")
            out.append(f"    /** 772の{jp}ID → {src}の{jp}ID（771経由）。 */")
            out.append(f"    public static int map{name}772To{src}(int id772) {{")
            out.append(f"        return map{name}771To{src}(map{name}772To771(id772));")
            out.append("    }")
            out.append("")
    return "\n".join(out)


def damage_accessors():
    out = []
    out.append("    /** 771のダメージ種別ID → 772のダメージ種別ID。771と同一のため恒等写像。範囲外は {@link #DROP}。 */")
    out.append("    public static int mapDamageType771To772(int id771) {")
    out.append("        if (id771 < 0 || id771 > MAX_DAMAGE_TYPE_771) {")
    out.append("            logUnknownOnce(PROTOCOL_772, \"ダメージ種別\", id771);")
    out.append("            return DROP;")
    out.append("        }")
    out.append("        return DAMAGE_TYPE_772_MAP[id771];")
    out.append("    }")
    out.append("")
    out.append("    /** 772のダメージ種別ID → 771のダメージ種別ID。771と同一のため恒等写像。範囲外は {@link #DROP}。 */")
    out.append("    public static int mapDamageType772To771(int id772) {")
    out.append("        if (id772 < 0 || id772 > MAX_DAMAGE_TYPE_772) {")
    out.append("            logUnknownOnce(PROTOCOL_772, \"ダメージ種別\", id772);")
    out.append("            return DROP;")
    out.append("        }")
    out.append("        return DAMAGE_TYPE_REVERSE_772_MAP[id772];")
    out.append("    }")
    out.append("")
    for src in ("763", "764", "765", "766", "767", "768", "769", "770"):
        # ダメージ種別は 771 のメソッドを経由する。
        out.append(f"    /** {src}のダメージ種別ID → 772のダメージ種別ID（771経由）。 */")
        out.append(f"    public static int mapDamageType{src}To772(int id{src}) {{")
        out.append(f"        return mapDamageType771To772(mapDamageType{src}To771(id{src}));")
        out.append("    }")
        out.append("")
        out.append(f"    /** 772のダメージ種別ID → {src}のダメージ種別ID（771経由）。 */")
        out.append(f"    public static int mapDamageType772To{src}(int id772) {{")
        out.append(f"        return mapDamageType771To{src}(mapDamageType772To771(id772));")
        out.append("    }")
        out.append("")
    return "\n".join(out)


JAVADOC_772 = """ *
 * <p>772（1.21.7／1.21.8）向けの表も同じ作り方で持つ（{@code *_772}）。
 * 772は771に近く、差分は次の通り（ViaVersion mapping-1.21.7.json と
 * mapping-1.21.6.json、minecraft-data 1.21.8 の突き合わせ）。
 * ブロック状態・ブロック・エンティティ・パーティクル・メニュー・
 * バイオーム・エフェクト・エンチャント・属性・データコンポーネントは
 * 同一。アイテムは新1件（music_disc_lava_chicken が 1256 に挿入）。
 * 音は新1件（music_disc.lava_chicken が 939 に挿入）。
 * 絵画バリアントは新1件（dennis が 14 に挿入され 51 件。実バニラの
 * Registry Data での確認は統合テストで行う）。ジュークボックスの歌も
 * 新1件（lava_chicken が 9 に挿入され 21 件。同上）。
 * ブロックエンティティ種別は 771 と同じ 47 件。ダメージ種別・
 * バナーパターン・装飾パターンは 771 と同一（実バニラの Registry Data
 * での確認は統合テストで行う）。
 * 771→772 の隣接表と 763／764／765／766／767／768／769／770→772 の合成表を持ち、
 * 逆向き 772→771 は反転して作る。
"""


def insert_after(java, anchor, block, label):
    if anchor not in java:
        print(f"アンカーが見つからない（{label}）", file=sys.stderr)
        return None
    return java.replace(anchor, anchor + block, 1)


def main():
    if (not TABLES772_FILE.exists() or not TABLES772_COMPOSED_FILE.exists()
            or not all(p.exists() for p in TABLES772_COMPOSED_DOWN_FILES.values())):
        print("RegistryTables772.java / RegistryTables772ComposedUp.java / "
              "RegistryTables772Down*.java が無い。"
              "先に insert-772-tables.py を実行すること", file=sys.stderr)
        return 1
    java = JAVA_FILE.read_text(encoding="utf-8")
    checks = [
        ("PROTOCOL_772", "772 の定数・表・アクセサは既にある"),
        ("BLOCK_STATE_772_MAP", "772 の展開済み表は既にある"),
        ("mapBlockState771To772", "772 のアクセサは既にある"),
    ]
    for marker, msg in checks:
        if marker in java:
            print(msg, file=sys.stderr)
            return 1

    # import static。
    java = insert_after(java,
                        "import static io.detergentmc.protocol.RegistryTables771Down769.*;\n",
                        "import static io.detergentmc.protocol.RegistryTables772.*;\n"
                        "import static io.detergentmc.protocol.RegistryTables772ComposedUp.*;\n"
                        "import static io.detergentmc.protocol.RegistryTables772Down763.*;\n"
                        "import static io.detergentmc.protocol.RegistryTables772Down764.*;\n"
                        "import static io.detergentmc.protocol.RegistryTables772Down765.*;\n"
                        "import static io.detergentmc.protocol.RegistryTables772Down766.*;\n"
                        "import static io.detergentmc.protocol.RegistryTables772Down767.*;\n"
                        "import static io.detergentmc.protocol.RegistryTables772Down768.*;\n"
                        "import static io.detergentmc.protocol.RegistryTables772Down769.*;\n"
                        "import static io.detergentmc.protocol.RegistryTables772Down770.*;\n",
                        "import static RegistryTables772")
    if java is None:
        return 1

    # クラスの javadoc に 772 の段落を追加（771 の段落の直後）。
    anchor = (" * 770→771 の隣接表と 763／764／765／766／767／768／769→771 の合成表を持ち、\n"
              " * 逆向き 771→770 は反転して作る。\n")
    java = insert_after(java, anchor, JAVADOC_772, "javadoc")
    if java is None:
        return 1

    # 定数。
    java = insert_after(java,
                        "    public static final int PROTOCOL_771 = 771;\n",
                        "    public static final int PROTOCOL_772 = 772;\n", "PROTOCOL_772")
    if java is None:
        return 1
    java = insert_after(java,
                        "    public static final int MAX_EFFECT_771 = 38;\n",
                        CONSTANTS_MAX, "MAX_772")
    if java is None:
        return 1
    java = insert_after(java,
                        "    public static final int SPEED_EFFECT_771 = 0;\n",
                        CONSTANTS_FALLBACK, "772 の代替先")
    if java is None:
        return 1
    java = insert_after(java,
                        "    public static final int BLOCK_ENTITY_VAULT_771 = 44;\n",
                        CONSTANTS_BLOCK_ENTITY, "772 のブロックエンティティ種別")
    if java is None:
        return 1

    # 隣接表（771→772）。MENU_771_MAP の直後。
    anchor = ("    private static final int[] MENU_771_MAP =\n"
              "            expand(MENU_771_RANGES, MENU_771_OVERRIDES, MAX_MENU_770 + 1,\n"
              "                    CRAFTING_MENU_771);\n")
    java = insert_after(java, anchor, adjacent_maps() + "\n", "隣接表 772")
    if java is None:
        return 1

    # 合成表。MENU_771_TO_769_MAP の直後。
    anchor = ("    private static final int[] MENU_771_TO_769_MAP =\n"
              "            expand(MENU_771_TO_769_RANGES, MENU_771_TO_769_OVERRIDES,\n"
              "                    25, CRAFTING_MENU_769);\n")
    java = insert_after(java, anchor, "\n" + composed_maps() + "\n", "合成表 772")
    if java is None:
        return 1

    # 反転表（772→771）。MENU_UP_771_MAP の直後。
    anchor = ("    private static final int[] MENU_UP_771_MAP = invert(\n"
              "            MENU_771_RANGES, MENU_771_OVERRIDES,\n"
              "            MAX_MENU_770 + 1, MAX_MENU_771 + 1,\n"
              "            CRAFTING_MENU_770, null);\n")
    java = insert_after(java, anchor, up_maps() + "\n", "反転表 772")
    if java is None:
        return 1

    # 音の名前の表。
    anchor = ("    private static final Map<String, String> SOUND_NAME_FALLBACK_771_TO_766_MAP =\n"
              "            buildSoundNameMap(SOUND_NAME_FALLBACKS_771_TO_766);\n")
    java = insert_after(java, anchor, SOUND_NAME_MAPS, "音の名前の表 772")
    if java is None:
        return 1

    # ログ。
    anchor = ("    private static void logUnknownUp771Once(String kind, int id771) {\n"
              "        if (LOGGED_UNKNOWN.add(\"up771:\" + kind + \":\" + id771)) {\n"
              "            LOG.warning(\"771に無い\" + kind + \"の番号が来たため代替に落とします（771 id=\" + id771 + \"）。\");\n"
              "        }\n"
              "    }\n")
    java = insert_after(java, anchor, LOG_UP_772, "logUnknownUp772Once")
    if java is None:
        return 1

    # アクセサ（a）771→772。mapSoundName767To771 の直後。
    anchor = ("    public static String mapSoundName767To771(String name) {\n"
              "        return mapSoundNameComposed(name, SOUND_NAME_FALLBACK_767_TO_771_MAP);\n"
              "    }\n")
    java = insert_after(java, anchor, "\n" + adjacent_accessors() + "\n", "アクセサ 771→772")
    if java is None:
        return 1

    # アクセサ（b）772→771。mapSoundNameUp771 の直後。
    anchor = ("    public static String mapSoundNameUp771(String name) {\n"
              "        return mapSoundNameComposed(name, SOUND_NAME_FALLBACK_771_TO_770_MAP);\n"
              "    }\n")
    java = insert_after(java, anchor, "\n" + up_accessors() + "\n", "アクセサ 772→771")
    if java is None:
        return 1

    # アクセサ（c）763／764／765／766／767／768／769／770↔772。mapSoundName771To766 の直後。
    anchor = ("    public static String mapSoundName771To766(String name) {\n"
              "        return mapSoundNameComposed(name, SOUND_NAME_FALLBACK_771_TO_766_MAP);\n"
              "    }\n")
    java = insert_after(java, anchor, "\n" + composed_accessors() + "\n", "アクセサ 763/764/765/766/767↔772")
    if java is None:
        return 1

    # バナー・絵画・装飾パターン・ダメージ種別の定数。
    java = insert_after(java,
                        "    /** 770のバナーパターン最大ID（43件。769と同一と仮定。実バニラの\n"
                        "        Registry Data での確認は統合テストで行う）。 */\n"
                        "    public static final int MAX_BANNER_PATTERN_770 = 42;\n",
                        SMALL_REGISTRY_CONSTANTS, "バナー等の772定数")
    if java is None:
        return 1
    anchor = ("    private static final int[] DAMAGE_TYPE_REVERSE_771_MAP = {\n"
              "        0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19,\n"
              "        20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32, 33, 34, 35, 36,\n"
              "        37, 38, 39, 40, 41, 42, 43, 44, 45, 46, 47, 48\n"
              "    };\n")
    java = insert_after(java, anchor, DAMAGE_TYPE_772, "ダメージ種別772の表")
    if java is None:
        return 1

    # バナー・絵画・装飾パターン・ダメージ種別のアクセサ。
    anchor = ("    /** 770のバナーパターンID → 766のバナーパターンID（769経由）。 */\n"
              "    public static int mapBannerPattern770To766(int id770) {\n"
              "        return mapBannerPattern769To766(mapBannerPattern770To769(id770));\n"
              "    }\n")
    banner_painting_trim = small_registry_accessors()
    java = insert_after(java, anchor, "\n" + banner_painting_trim + "\n", "バナー等の772アクセサ")
    if java is None:
        return 1
    anchor = ("    /** 770のダメージ種別ID → 766のダメージ種別ID（769経由）。 */\n"
              "    public static int mapDamageType770To766(int id770) {\n"
              "        return mapDamageType769To766(mapDamageType770To769(id770));\n"
              "    }\n")
    java = insert_after(java, anchor, "\n" + damage_accessors() + "\n", "ダメージ種別772のアクセサ")
    if java is None:
        return 1

    # プロトコル番号で切り替える変換（担当2の配線用）に 772 を追加。
    dispatches = [
        ("mapBlockState", "mapBlockState765To771(id765)", "mapBlockState765To772(id765)"),
        ("mapItem", "mapItem765To771(id765)", "mapItem765To772(id765)"),
        ("mapItemReverse", "mapItem771To765(id)", "mapItem772To765(id)"),
        ("mapEntityType", "mapEntityType765To771(id765)", "mapEntityType765To772(id765)"),
        ("mapSound", "mapSound765To771(id765)", "mapSound765To772(id765)"),
        ("mapParticle", "mapParticle765To771(id765)", "mapParticle765To772(id765)"),
        ("mapBlock", "mapBlock765To771(id765)", "mapBlock765To772(id765)"),
        ("mapMenu", "mapMenu765To771(id765)", "mapMenu765To772(id765)"),
        ("mapEffect", "mapEffect765To771(id765)", "mapEffect765To772(id765)"),
        ("mapBlockEntityType", "mapBlockEntityType765To771(id765)", "mapBlockEntityType765To772(id765)"),
        ("mapSoundName", "mapSoundName765To771(name)", "mapSoundName765To772(name)"),
    ]
    for label, old_call, new_call in dispatches:
        if label == "mapItemReverse":
            old = ("        if (clientProtocol == PROTOCOL_771) {\n"
                   "            // 771クライアントのアイテムID → 765サーバーのアイテムID。\n"
                   "            // mapItemReverse771 は 771→770 のため、ここでは合成表を使う。\n"
                   "            return mapItem771To765(id);\n"
                   "        }\n")
            new = (old
                   + "        if (clientProtocol == PROTOCOL_772) {\n"
                   "            // 772クライアントのアイテムID → 765サーバーのアイテムID。\n"
                   "            // mapItemReverse772 は 772→771 のため、ここでは合成表を使う。\n"
                   "            return mapItem772To765(id);\n"
                   "        }\n")
            if old not in java:
                print("ディスパッチのアンカーが見つからない（mapItemReverse）", file=sys.stderr)
                return 1
            java = java.replace(old, new, 1)
            continue
        old = ("        if (clientProtocol == PROTOCOL_771) {\n"
               f"            return {old_call};\n"
               "        }\n")
        if old not in java:
            print(f"ディスパッチのアンカーが見つからない（{label}）", file=sys.stderr)
            return 1
        java = java.replace(old, old
                            + "        if (clientProtocol == PROTOCOL_772) {\n"
                            + f"            return {new_call};\n"
                            + "        }\n", 1)
    java = java.replace(
        "    // 765は素通し、766は766の表、767は767の表、768は768の表、\n"
        "    // 769は769の表、770は770の表、771は771の表、それより古い未知の番号は763の表を使う。\n"
        "    // 767／768／769／770／771は765以上だが素通しではない（>765 の分岐より\n"
        "    // 先に 767／768／769／770／771 を判定すること）。",
        "    // 765は素通し、766は766の表、767は767の表、768は768の表、\n"
        "    // 769は769の表、770は770の表、771は771の表、772は772の表、それより古い未知の番号は763の表を使う。\n"
        "    // 767／768／769／770／771／772は765以上だが素通しではない（>765 の分岐より\n"
        "    // 先に 767／768／769／770／771／772 を判定すること）。",
        1)

    JAVA_FILE.write_text(java, encoding="utf-8")
    print("RegistryRemapper.java に 772 の定数・表・アクセサを挿入した")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
