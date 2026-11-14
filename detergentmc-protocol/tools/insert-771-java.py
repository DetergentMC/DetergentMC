#!/usr/bin/env python3
"""tools/insert-770-java.py で入れた *_770 表の上に、RegistryRemapper.java
へ 771 対応の定数・展開済み表（*_771_MAP）・反転表（*_UP_771_MAP）・
アクセサを挿入する（1回だけの作業用）。

  - 定数：PROTOCOL_771、MAX_*_771、771 側の代替先。
  - 隣接表（770→771）: *_771_MAP と ITEM_REVERSE_771_MAP と
    ATTRIBUTE_771_MAP／ATTRIBUTE_REVERSE_771_MAP／ENCHANTMENT_771_MAP／
    ENCHANTMENT_REVERSE_771_MAP。
  - 合成表（763↔771、764↔771、765↔771、766↔771、767↔771、768↔771、769↔771）: *_763_TO_771_MAP など98件。
  - 770↔771 は隣接表（*_771_MAP）と反転表（*_UP_771_MAP）で扱うため合成表は作らない（770と同じ方針）。
  - 逆向き（771→770）: *_UP_771_MAP（invert()。アイテムは
    ITEM_REVERSE_771_MAP をそのまま使う。770→771 に補正は無い）。
  - 音の名前の表（String）: SOUND_NAME_FALLBACK_*_771_MAP など14件。
  - アクセサ：770↔771、771→770（上向き）、763／764／765／766／767／768／769↔771。
  - エフェクト：771 は 770 と同一の 39 件（0〜38）のため恒等写像。
    属性は 770 の 32 件から 771 の 35 件へ（camera_distance が 8 に挿入、
    waypoint_transmit_range・waypoint_receive_range を末尾に追加。逆向きは
    新3件を DROP）、エンチャントは 42 件同士で
    並びは同一（minecraft-data の dataPaths.json で 1.21.6 の
    enchantments は pc/1.21.6 と確認。1.21.1 の並びと同一）。
    ブロックエンティティ種別は 770 と同じ 47 件（creaking_heart=10、
    trial_spawner=43、vault=44、test_block=45、test_instance_block=46）。
    バナー・絵画・装飾パターンは 770 と同一のため恒等写像。
    ダメージ種別も 770 と同一の 49 件のため恒等写像。
    バイオームは 769・770・771 とも 65 件で同一の並び（実バニラの Registry Data
    での確認は統合テストで行う）。
"""

import re
import sys
from pathlib import Path

SCRIPT_DIR = Path(__file__).resolve().parent
REPO_ROOT = SCRIPT_DIR.parent.parent
JAVA_FILE = (REPO_ROOT / "detergentmc-protocol" / "src" / "main" / "java"
             / "io" / "detergentmc" / "protocol" / "RegistryRemapper.java")
TABLES771_FILE = (REPO_ROOT / "detergentmc-protocol" / "src" / "main" / "java"
                  / "io" / "detergentmc" / "protocol" / "RegistryTables771.java")
TABLES771_COMPOSED_FILE = (REPO_ROOT / "detergentmc-protocol" / "src" / "main" / "java"
                  / "io" / "detergentmc" / "protocol" / "RegistryTables771ComposedUp.java")
TABLES771_COMPOSED_DOWN_FILES = {
    suffix: (REPO_ROOT / "detergentmc-protocol" / "src" / "main" / "java"
             / "io" / "detergentmc" / "protocol"
             / f"RegistryTables771Down{suffix}.java")
    for suffix in ("763", "764", "765", "766", "767", "768", "769")
}


# kind: (Java メソッド名の語尾, RANGES の接頭辞, 日本語名,
#        763→771 の件数, 771→763 の件数, 764→771 の件数, 771→764 の件数,
#        765→771 の件数, 771→765 の件数, 766→771 の件数, 771→766 の件数,
#        767→771 の件数, 771→767 の件数, 768→771 の件数, 771→768 の件数,
#        769→771 の件数, 771→769 の件数, 770→771 の件数, 771→770 の件数,
#        763 側の代替先, 764 側の代替先, 765 側の代替先, 766 側の代替先,
#        767 側の代替先, 768 側の代替先, 769 側の代替先, 770 側の代替先,
#        771 側の代替先)
KINDS = [
    ("BlockState", "BLOCK_STATE", "ブロック状態", 24135, 27946, 24276, 27946, 26644, 27946, 26684, 27946, 26684, 27946, 27318, 27946, 27866, 27946, 27914, 27946,
     "STONE_BLOCK_STATE_763", "STONE_BLOCK_STATE_764", "STONE_BLOCK_STATE_765", "STONE_BLOCK_STATE_766", "STONE_BLOCK_STATE_767", "STONE_BLOCK_STATE_768", "STONE_BLOCK_STATE_769", "STONE_BLOCK_STATE_770", "STONE_BLOCK_STATE_771"),
    ("Item", "ITEM", "アイテム", 1255, 1415, 1255, 1415, 1312, 1415, 1330, 1415, 1333, 1415, 1375, 1415, 1385, 1415, 1396, 1415,
     "STONE_ITEM_763", "STONE_ITEM_764", "STONE_ITEM_765", "STONE_ITEM_766", "STONE_ITEM_767", "STONE_ITEM_768", "STONE_ITEM_769", "STONE_ITEM_770", "STONE_ITEM_771"),
    ("EntityType", "ENTITY_TYPE", "エンティティ種別", 124, 151, 124, 151, 126, 151, 130, 151, 130, 151, 150, 151, 149, 151, 150, 151,
     "PIG_ENTITY_TYPE_763", "PIG_ENTITY_TYPE_764", "PIG_ENTITY_TYPE_765", "PIG_ENTITY_TYPE_766", "PIG_ENTITY_TYPE_767", "PIG_ENTITY_TYPE_768", "PIG_ENTITY_TYPE_769", "PIG_ENTITY_TYPE_770", "PIG_ENTITY_TYPE_771"),
    ("Sound", "SOUND", "音", 1474, 1727, 1485, 1727, 1539, 1727, 1607, 1727, 1611, 1727, 1636, 1727, 1651, 1727, 1702, 1727,
     "GENERIC_SOUND_763", "GENERIC_SOUND_764", "GENERIC_SOUND_765", "GENERIC_SOUND_766", "GENERIC_SOUND_767", "GENERIC_SOUND_768", "GENERIC_SOUND_769", "GENERIC_SOUND_770", "GENERIC_SOUND_771"),
    ("Particle", "PARTICLE", "パーティクル", 95, 114, 95, 114, 101, 114, 109, 114, 109, 114, 111, 114, 112, 114, 114, 114,
     "AMBIENT_PARTICLE_763", "AMBIENT_PARTICLE_764", "AMBIENT_PARTICLE_765", "ENTITY_EFFECT_PARTICLE_766", "ENTITY_EFFECT_PARTICLE_767", "ENTITY_EFFECT_PARTICLE_768", "ENTITY_EFFECT_PARTICLE_769", "ENTITY_EFFECT_PARTICLE_770", "ENTITY_EFFECT_PARTICLE_771"),
    ("Block", "BLOCK", "ブロック", 1003, 1105, 1003, 1105, 1058, 1105, 1060, 1105, 1060, 1105, 1084, 1105, 1095, 1105, 1104, 1105,
     "STONE_BLOCK_763", "STONE_BLOCK_764", "STONE_BLOCK_765", "STONE_BLOCK_766", "STONE_BLOCK_767", "STONE_BLOCK_768", "STONE_BLOCK_769", "STONE_BLOCK_770", "STONE_BLOCK_771"),
    ("Menu", "MENU", "メニュー種別", 24, 25, 24, 25, 25, 25, 25, 25, 25, 25, 25, 25, 25, 25, 25, 25,
     "CRAFTING_MENU_763", "CRAFTING_MENU_764", "CRAFTING_MENU_765", "CRAFTING_MENU_766", "CRAFTING_MENU_767", "CRAFTING_MENU_768", "CRAFTING_MENU_769", "CRAFTING_MENU_770", "CRAFTING_MENU_771"),
]

CONSTANTS_MAX = """
    /** 771側の最大の番号（ViaVersion mapping-1.21.6.json の件数−1）。 */
    public static final int MAX_BLOCK_STATE_771 = 27945;
    public static final int MAX_ITEM_771 = 1414;
    public static final int MAX_ENTITY_TYPE_771 = 150;
    public static final int MAX_SOUND_771 = 1726;
    public static final int MAX_PARTICLE_771 = 113;
    public static final int MAX_BLOCK_771 = 1104;
    public static final int MAX_MENU_771 = 24;
    /** 771側のエフェクトも 0〜38（Speed=0。770と同一。minecraft-data の
        dataPaths.json で 1.21.6 の effects は pc/1.21.6 と確認）。 */
    public static final int MAX_EFFECT_771 = 38;
    /** 771側の属性は 0〜34（770 の 32 件に camera_distance（8）・
        waypoint_transmit_range（33）・waypoint_receive_range（34）を
        追加した 35 件。ViaVersion mapping-1.21.6.json で確認）。 */
    public static final int MAX_ATTRIBUTE_771 = 34;
    /** エンチャントは 769・770・771 とも 0〜41（42件。並びは同一。
        minecraft-data の dataPaths.json で 1.21.6 の enchantments は
        pc/1.21.6 と確認（1.21.1 の並びと同一）。実機の Registry Data での
        確認は統合テストで行う）。 */
    public static final int MAX_ENCHANTMENT_771 = 41;
"""

CONSTANTS_FALLBACK = """
    /** 771側の代替先（mapping-1.21.6.json の番号。Speed だけ 0ベースで 0）。
     * ブタは 771 では 95（happy_ghast の挿入で 770 の 94 から +1）、
     * 石の破壊音は 771 では 1441（新音 29 件の挿入と entity.leash_knot
     * 2件・entity.puffer_fish.ambient・block.sand.wind の削除で
     * 770 の 1417 からずれた）。 */
    public static final int STONE_BLOCK_STATE_771 = 1;
    public static final int AIR_BLOCK_STATE_771 = 0;
    public static final int STONE_ITEM_771 = 1;
    public static final int PIG_ENTITY_TYPE_771 = 95;
    public static final int GENERIC_SOUND_771 = 1441; // block.stone.break
    /** 771でも ambient_entity_effect は無いため、パーティクルの代替は entity_effect。 */
    public static final int ENTITY_EFFECT_PARTICLE_771 = 20;
    public static final int STONE_BLOCK_771 = 1;
    public static final int CRAFTING_MENU_771 = 12;
    public static final int SPEED_EFFECT_771 = 0;
"""

CONSTANTS_BLOCK_ENTITY = """
    /** 771のブロックエンティティ種別：770 と同じ 47 件
        （creaking_heart=10、trial_spawner=43、vault=44、test_block=45、
        test_instance_block=46）。 */
    public static final int BLOCK_ENTITY_CREAKING_HEART_771 = 10;
    public static final int BLOCK_ENTITY_TRIAL_SPAWNER_771 = 43;
    public static final int BLOCK_ENTITY_VAULT_771 = 44;
    public static final int BLOCK_ENTITY_TEST_BLOCK_771 = 45;
    public static final int BLOCK_ENTITY_TEST_INSTANCE_BLOCK_771 = 46;
"""


def adjacent_maps():
    lines = []
    for kind in KINDS:
        name, prefix, jp = kind[0], kind[1], kind[2]
        lines.append(
            f"    private static final int[] {prefix}_771_MAP =\n"
            f"            expand({prefix}_771_RANGES, {prefix}_771_OVERRIDES, MAX_{prefix}_770 + 1,\n"
            f"                    {kind[27]});")
    lines.append(
        "    private static final int[] ITEM_REVERSE_771_MAP =\n"
        "            expand(ITEM_REVERSE_771_RANGES, ITEM_REVERSE_771_OVERRIDES, 1415, -1);")
    lines.append(
        "    private static final int[] ATTRIBUTE_771_MAP =\n"
        "            expand(ATTRIBUTE_771_RANGES, ATTRIBUTE_771_OVERRIDES, MAX_ATTRIBUTE_770 + 1, DROP);")
    lines.append(
        "    private static final int[] ATTRIBUTE_REVERSE_771_MAP =\n"
        "            expand(ATTRIBUTE_REVERSE_771_RANGES, ATTRIBUTE_REVERSE_771_OVERRIDES,\n"
        "                    MAX_ATTRIBUTE_771 + 1, DROP);")
    lines.append(
        "    private static final int[] ENCHANTMENT_771_MAP =\n"
        "            expand(ENCHANTMENT_771_RANGES, ENCHANTMENT_771_OVERRIDES,\n"
        "                    MAX_ENCHANTMENT_770 + 1, DROP);")
    lines.append(
        "    private static final int[] ENCHANTMENT_REVERSE_771_MAP =\n"
        "            expand(ENCHANTMENT_REVERSE_771_RANGES, ENCHANTMENT_REVERSE_771_OVERRIDES,\n"
        "                    MAX_ENCHANTMENT_771 + 1, DROP);")
    return "\n".join(lines)


def composed_maps():
    # (接尾辞, 件数の位置, 代替先の位置)。件数・代替先は KINDS の
    # タプルから取る（763_TO_771: 件数 index 3, 代替先 771=index 27／
    # 771_TO_763: 件数 index 4, 代替先 763=index 19）。件数は 3〜18、
    # 代替先は 19（763）〜27（771）。770↔771 は隣接表で扱うため含めない。
    pairs = [
        ("763_TO_771", 3, 27), ("771_TO_763", 4, 19),
        ("764_TO_771", 5, 27), ("771_TO_764", 6, 20),
        ("765_TO_771", 7, 27), ("771_TO_765", 8, 21),
        ("766_TO_771", 9, 27), ("771_TO_766", 10, 22),
        ("767_TO_771", 11, 27), ("771_TO_767", 12, 23),
        ("768_TO_771", 13, 27), ("771_TO_768", 14, 24),
        ("769_TO_771", 15, 27), ("771_TO_769", 16, 25),
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
            continue  # アイテムは ITEM_REVERSE_771_MAP を使う。
        # 770→771 に補正は無い（creaking_heart のプロパティ変更と
        # potion の分割は 770 で済んでいる）。反転はそのまま使う。
        corrections = "null"
        # 逆向きの代替先は「770 側」の定数（kind[26]）。
        fb770 = kind[26]
        lines.append(
            f"    private static final int[] {prefix}_UP_771_MAP = invert(\n"
            f"            {prefix}_771_RANGES, {prefix}_771_OVERRIDES,\n"
            f"            MAX_{prefix}_770 + 1, MAX_{prefix}_771 + 1,\n"
            f"            {fb770}, {corrections});")
    return "\n".join(lines)


SOUND_NAME_MAPS = """
    private static final Map<String, String> SOUND_NAME_FALLBACK_771_MAP =
            buildSoundNameMap(SOUND_NAME_FALLBACKS_771);
    private static final Map<String, String> SOUND_NAME_FALLBACK_770_TO_771_MAP =
            buildSoundNameMap(SOUND_NAME_FALLBACKS_770_TO_771);
    private static final Map<String, String> SOUND_NAME_FALLBACK_771_TO_770_MAP =
            buildSoundNameMap(SOUND_NAME_FALLBACKS_771_TO_770);
    private static final Map<String, String> SOUND_NAME_FALLBACK_769_TO_771_MAP =
            buildSoundNameMap(SOUND_NAME_FALLBACKS_769_TO_771);
    private static final Map<String, String> SOUND_NAME_FALLBACK_771_TO_769_MAP =
            buildSoundNameMap(SOUND_NAME_FALLBACKS_771_TO_769);
    private static final Map<String, String> SOUND_NAME_FALLBACK_771_TO_768_MAP =
            buildSoundNameMap(SOUND_NAME_FALLBACKS_771_TO_768);
    private static final Map<String, String> SOUND_NAME_FALLBACK_771_TO_767_MAP =
            buildSoundNameMap(SOUND_NAME_FALLBACKS_771_TO_767);
    private static final Map<String, String> SOUND_NAME_FALLBACK_771_TO_765_MAP =
            buildSoundNameMap(SOUND_NAME_FALLBACKS_771_TO_765);
    private static final Map<String, String> SOUND_NAME_FALLBACK_771_TO_763_MAP =
            buildSoundNameMap(SOUND_NAME_FALLBACKS_771_TO_763);
    private static final Map<String, String> SOUND_NAME_FALLBACK_771_TO_764_MAP =
            buildSoundNameMap(SOUND_NAME_FALLBACKS_771_TO_764);
    private static final Map<String, String> SOUND_NAME_FALLBACK_771_TO_766_MAP =
            buildSoundNameMap(SOUND_NAME_FALLBACKS_771_TO_766);
    private static final Map<String, String> SOUND_NAME_FALLBACK_768_TO_771_MAP =
            buildSoundNameMap(SOUND_NAME_FALLBACKS_768_TO_771);
    private static final Map<String, String> SOUND_NAME_FALLBACK_767_TO_771_MAP =
            buildSoundNameMap(SOUND_NAME_FALLBACKS_767_TO_771);
    private static final Map<String, String> SOUND_NAME_FALLBACK_766_TO_771_MAP =
            buildSoundNameMap(SOUND_NAME_FALLBACKS_766_TO_771);
"""

LOG_UP_771 = """
    private static void logUnknownUp771Once(String kind, int id771) {
        if (LOGGED_UNKNOWN.add("up771:" + kind + ":" + id771)) {
            LOG.warning("771に無い" + kind + "の番号が来たため代替に落とします（771 id=" + id771 + "）。");
        }
    }
"""
def adjacent_accessors():
    out = ["    // ------------------------------------------------------------------",
           "    // 770→771。上の *_771 表を使う。ブロック状態・ブロック・",
           "    // アイテム・エンティティ・音は 770 から 771 で dried_ghast・",
           "    // ハーネス・ハッピーガスト関連の挿入でずれる。パーティクル・",
           "    // メニュー・エフェクト・エンチャント・バイオームは同一。",
           "    // 属性は camera_distance（8）の挿入と waypoint 系2件の追加で",
           "    // 32→35 件になる。ブロックエンティティ種別は 770 と同じ 47 件。",
           "    // ------------------------------------------------------------------",
           ""]
    for kind in KINDS:
        name, prefix, jp = kind[0], kind[1], kind[2]
        fb771 = kind[27]
        out.append(f"    /** 770の{jp}ID → 771の{jp}ID。範囲外は{fb771} に相当する代替。 */")
        out.append(f"    public static int map{name}770To771(int id770) {{")
        out.append(f"        if (id770 < 0 || id770 > MAX_{prefix}_770) {{")
        out.append(f"            logUnknownOnce(PROTOCOL_771, \"{jp}\", id770);")
        out.append(f"            return {fb771};")
        out.append("        }")
        out.append(f"        return {prefix}_771_MAP[id770];")
        out.append("    }")
        out.append("")
    out.append("    /**")
    out.append("     * 771のアイテムID → 770のアイテムID（サーバー行きスロット用）。")
    out.append("     * 771にしか無いアイテム（乾燥ガスト・ハーネス16色・")
    out.append("     * ハッピーガストのスポーンエッグ・レコード「涙」19件）は 770の石へ。")
    out.append("     * 範囲外はそのまま返す。")
    out.append("     */")
    out.append("    public static int mapItemReverse771(int id771) {")
    out.append("        if (id771 < 0 || id771 >= ITEM_REVERSE_771_MAP.length) {")
    out.append("            logUnknownOnce(PROTOCOL_771, \"アイテム（逆変換）\", id771);")
    out.append("            return id771;")
    out.append("        }")
    out.append("        int mapped = ITEM_REVERSE_771_MAP[id771];")
    out.append("        return mapped < 0 ? id771 : mapped;")
    out.append("    }")
    out.append("")
    out.append("    /**")
    out.append("     * 770のエフェクトID → 771のエフェクトID。770も771も0ベース")
    out.append("     * （Speed は両方 0）で 0〜38 は同一のため恒等写像。")
    out.append("     * 範囲外は Speed（0）。")
    out.append("     */")
    out.append("    public static int mapEffect770To771(int id770) {")
    out.append("        if (id770 < 0 || id770 > MAX_EFFECT_770) {")
    out.append("            logUnknownOnce(PROTOCOL_771, \"エフェクト\", id770);")
    out.append("            return SPEED_EFFECT_771;")
    out.append("        }")
    out.append("        return id770;")
    out.append("    }")
    out.append("")
    out.append("    /**")
    out.append("     * 770のブロックエンティティ種別ID → 771の種別ID。")
    out.append("     * 770は47件、771は47件。並びが同一のため恒等写像。")
    out.append("     * 範囲外の種別は NBT の中身が種別専用のため安全に代替できず")
    out.append("     * {@link #DROP}。")
    out.append("     */")
    out.append("    public static int mapBlockEntityType770To771(int id770) {")
    out.append("        if (id770 < 0 || id770 > BLOCK_ENTITY_TEST_INSTANCE_BLOCK_770) {")
    out.append("            logUnknownOnce(PROTOCOL_771, \"ブロックエンティティ種別\", id770);")
    out.append("            return DROP;")
    out.append("        }")
    out.append("        return id770;")
    out.append("    }")
    out.append("")
    out.append("    /**")
    out.append("     * 音の名前（直書き・Stop Sound 用）を771向けに変換する。")
    out.append("     * {@code minecraft:} の有無はそのまま保つ。771で削除された")
    out.append("     * entity.leash_knot.break／place・entity.puffer_fish.ambient・")
    out.append("     * block.sand.wind は代替の名前にする。")
    out.append("     */")
    out.append("    public static String mapSoundName770To771(String name) {")
    out.append("        return mapSoundNameComposed(name, SOUND_NAME_FALLBACK_770_TO_771_MAP);")
    out.append("    }")
    out.append("")
    # 属性とエンチャント。
    out.append("    /** 770の属性ID → 771の属性ID。camera_distance（8）の挿入と waypoint 系2件の追加でずれる。範囲外は {@link #DROP}。 */")
    out.append("    public static int mapAttribute770To771(int id770) {")
    out.append("        if (id770 < 0 || id770 > MAX_ATTRIBUTE_770) {")
    out.append("            logUnknownOnce(PROTOCOL_771, \"属性\", id770);")
    out.append("            return DROP;")
    out.append("        }")
    out.append("        return ATTRIBUTE_771_MAP[id770];")
    out.append("    }")
    out.append("")
    out.append("    /** 771の属性ID → 770の属性ID。新3件（camera_distance・waypoint_transmit_range・waypoint_receive_range）は {@link #DROP}。範囲外も {@link #DROP}。 */")
    out.append("    public static int mapAttribute771To770(int id771) {")
    out.append("        if (id771 < 0 || id771 > MAX_ATTRIBUTE_771) {")
    out.append("            logUnknownOnce(PROTOCOL_771, \"属性\", id771);")
    out.append("            return DROP;")
    out.append("        }")
    out.append("        return ATTRIBUTE_REVERSE_771_MAP[id771];")
    out.append("    }")
    out.append("")
    out.append("    /** 771の属性ID → 属性の名前（Update Attributes の 770向け変換用）。 */")
    out.append("    public static String attributeName771(int id771) {")
    out.append("        if (id771 < 0 || id771 >= ATTRIBUTE_771_NAMES.length) {")
    out.append("            return null;")
    out.append("        }")
    out.append("        return ATTRIBUTE_771_NAMES[id771];")
    out.append("    }")
    out.append("")
    out.append("    /**")
    out.append("     * 属性の名前 → 771の属性ID（765／763／764 は属性が名前形式のため、")
    out.append("     * 771向けに変換するときに使う）。無い名前は {@link #DROP}。")
    out.append("     */")
    out.append("    public static int attributeId771(String name) {")
    out.append("        if (name == null) {")
    out.append("            return DROP;")
    out.append("        }")
    out.append("        String plain = stripMinecraftPrefix(name);")
    out.append("        for (int i = 0; i < ATTRIBUTE_771_NAMES.length; i++) {")
    out.append("            if (ATTRIBUTE_771_NAMES[i].equals(plain)) {")
    out.append("                return i;")
    out.append("            }")
    out.append("        }")
    out.append("        return DROP;")
    out.append("    }")
    out.append("")
    out.append("    /** 770のエンチャントID → 771のエンチャントID。並びは同一。範囲外は {@link #DROP}。 */")
    out.append("    public static int mapEnchantment770To771(int id770) {")
    out.append("        if (id770 < 0 || id770 > MAX_ENCHANTMENT_770) {")
    out.append("            logUnknownOnce(PROTOCOL_771, \"エンチャント\", id770);")
    out.append("            return DROP;")
    out.append("        }")
    out.append("        return ENCHANTMENT_771_MAP[id770];")
    out.append("    }")
    out.append("")
    out.append("    /** 771のエンチャントID → 770のエンチャントID。並びは同一。範囲外は {@link #DROP}。 */")
    out.append("    public static int mapEnchantment771To770(int id771) {")
    out.append("        if (id771 < 0 || id771 > MAX_ENCHANTMENT_771) {")
    out.append("            logUnknownOnce(PROTOCOL_771, \"エンチャント\", id771);")
    out.append("            return DROP;")
    out.append("        }")
    out.append("        return ENCHANTMENT_REVERSE_771_MAP[id771];")
    out.append("    }")
    out.append("")
    out.append("    /**")
    out.append("     * 767のエンチャントID → 771のエンチャントID。767→770 の表を")
    out.append("     * 経由して 770→771 の表を引く。範囲外は {@link #DROP}。")
    out.append("     */")
    out.append("    public static int mapEnchantment767To771(int id767) {")
    out.append("        int id770 = mapEnchantment767To770(id767);")
    out.append("        return id770 == DROP ? DROP : mapEnchantment770To771(id770);")
    out.append("    }")
    out.append("")
    out.append("    /**")
    out.append("     * 771のエンチャントID → 767のエンチャントID。771→770 の逆変換の")
    out.append("     * 結果を 770→767 の逆変換で戻す。範囲外は {@link #DROP}。")
    out.append("     */")
    out.append("    public static int mapEnchantment771To767(int id771) {")
    out.append("        int id770 = mapEnchantment771To770(id771);")
    out.append("        return id770 == DROP ? DROP : mapEnchantment770To767(id770);")
    out.append("    }")
    out.append("")
    out.append("    /**")
    out.append("     * 766のエンチャントID → 771のエンチャントID。766→767 の変換")
    out.append("     * （{@link #mapEnchantment766To767}）を経由して 767→771 の")
    out.append("     * 表を引く。範囲外は {@link #DROP}。")
    out.append("     */")
    out.append("    public static int mapEnchantment766To771(int id766) {")
    out.append("        int id767 = mapEnchantment766To767(id766);")
    out.append("        return id767 == DROP ? DROP : mapEnchantment767To771(id767);")
    out.append("    }")
    out.append("")
    out.append("    /**")
    out.append("     * 771のエンチャントID → 766のエンチャントID。771→767 の逆変換の")
    out.append("     * 結果を 767→766 の逆変換で戻す。範囲外は {@link #DROP}。")
    out.append("     */")
    out.append("    public static int mapEnchantment771To766(int id771) {")
    out.append("        int id767 = mapEnchantment771To767(id771);")
    out.append("        return id767 == DROP ? DROP : mapEnchantment767To766(id767);")
    out.append("    }")
    out.append("")
    out.append("    /**")
    out.append("     * 765のエンチャントID → 771のエンチャントID。765→767 の変換")
    out.append("     * （{@link #mapEnchantment765To767}）を経由して 767→771 の")
    out.append("     * 表を引く。範囲外は {@link #DROP}。")
    out.append("     * 763／764 のエンチャントの並びは 765 と同一のため、")
    out.append("     * 763／764 向けも同じ変換を使う。")
    out.append("     */")
    out.append("    public static int mapEnchantment765To771(int id765) {")
    out.append("        int id767 = mapEnchantment765To767(id765);")
    out.append("        return id767 == DROP ? DROP : mapEnchantment767To771(id767);")
    out.append("    }")
    out.append("")
    out.append("    /** 763のエンチャントID → 771のエンチャントID（765と同じ並び）。 */")
    out.append("    public static int mapEnchantment763To771(int id763) {")
    out.append("        return mapEnchantment765To771(id763);")
    out.append("    }")
    out.append("")
    out.append("    /** 764のエンチャントID → 771のエンチャントID（765と同じ並び）。 */")
    out.append("    public static int mapEnchantment764To771(int id764) {")
    out.append("        return mapEnchantment765To771(id764);")
    out.append("    }")
    out.append("")
    out.append("    /**")
    out.append("     * 771のエンチャントID → 765のエンチャントID。771→767 の逆変換の")
    out.append("     * 結果を 767→765 の変換（{@link #mapEnchantment767To765}）で")
    out.append("     * 戻す。density／breach／wind_burst は 765に無いため")
    out.append("     * {@link #DROP}。範囲外も {@link #DROP}。")
    out.append("     */")
    out.append("    public static int mapEnchantment771To765(int id771) {")
    out.append("        int id767 = mapEnchantment771To767(id771);")
    out.append("        return id767 == DROP ? DROP : mapEnchantment767To765(id767);")
    out.append("    }")
    out.append("")
    out.append("    /** 771のエンチャントID → 763のエンチャントID（765と同じ並び）。 */")
    out.append("    public static int mapEnchantment771To763(int id771) {")
    out.append("        return mapEnchantment771To765(id771);")
    out.append("    }")
    out.append("")
    out.append("    /** 771のエンチャントID → 764のエンチャントID（765と同じ並び）。 */")
    out.append("    public static int mapEnchantment771To764(int id771) {")
    out.append("        return mapEnchantment771To765(id771);")
    out.append("    }")
    return "\n".join(out)


def up_accessors():
    out = ["    // ------------------------------------------------------------------",
           "    // 逆向き（771→770）。上の *_771 表を反転して作った表を使う。",
           "    // アイテムだけは生成スクリプトが名前から作った既存の逆変換表",
           "    // （ITEM_REVERSE_771_MAP）をそのまま使う（反転した表と完全一致",
           "    // することはテストで確認している）。属性とエンチャントの逆向きは",
           "    // mapAttribute771To770／mapEnchantment771To770 を参照。",
           "    // ------------------------------------------------------------------",
           ""]
    for kind in KINDS:
        name, prefix, jp = kind[0], kind[1], kind[2]
        # 逆向きの代替先は「770 側」の定数（kind[26]）。
        fb770 = kind[26]
        if prefix == "ITEM":
            out.append("    /**")
            out.append("     * 771のアイテムID → 770のアイテムID。")
            out.append("     * 771にしか無いアイテム（乾燥ガスト・ハーネス16色・")
            out.append("     * ハッピーガストのスポーンエッグ・レコード「涙」19件）は石へ。範囲外は石（1）。")
            out.append("     */")
            out.append("    public static int mapItemUp771(int id771) {")
            out.append("        if (id771 < 0 || id771 >= ITEM_REVERSE_771_MAP.length) {")
            out.append("            logUnknownUp771Once(\"アイテム\", id771);")
            out.append("            return STONE_ITEM_770;")
            out.append("        }")
            out.append("        int mapped = ITEM_REVERSE_771_MAP[id771];")
            out.append("        return mapped < 0 ? STONE_ITEM_770 : mapped;")
            out.append("    }")
            out.append("")
            continue
        out.append(f"    /** 771の{jp}ID → 770の{jp}ID。範囲外は{fb770} に相当する代替。 */")
        out.append(f"    public static int map{name}Up771(int id771) {{")
        out.append(f"        if (id771 < 0 || id771 > MAX_{prefix}_771) {{")
        out.append(f"            logUnknownUp771Once(\"{jp}\", id771);")
        out.append(f"            return {fb770};")
        out.append("        }")
        out.append(f"        return {prefix}_UP_771_MAP[id771];")
        out.append("    }")
        out.append("")
    out.append("    /**")
    out.append("     * 771のエフェクトID → 770のエフェクトID。0〜38 は同一のため恒等写像。")
    out.append("     * 範囲外は Speed（0）。")
    out.append("     */")
    out.append("    public static int mapEffectUp771(int id771) {")
    out.append("        if (id771 < 0 || id771 > MAX_EFFECT_771) {")
    out.append("            logUnknownUp771Once(\"エフェクト\", id771);")
    out.append("            return SPEED_EFFECT_770;")
    out.append("        }")
    out.append("        return id771;")
    out.append("    }")
    out.append("")
    out.append("    /**")
    out.append("     * 771のブロックエンティティ種別ID → 770の種別ID。")
    out.append("     * 770は47件、771は47件。並びが同一のため恒等写像。")
    out.append("     * 範囲外の種別は NBT の中身が種別専用のため安全に代替できず")
    out.append("     * {@link #DROP}。")
    out.append("     */")
    out.append("    public static int mapBlockEntityTypeUp771(int id771) {")
    out.append("        if (id771 < 0 || id771 > BLOCK_ENTITY_TEST_INSTANCE_BLOCK_771) {")
    out.append("            logUnknownUp771Once(\"ブロックエンティティ種別\", id771);")
    out.append("            return DROP;")
    out.append("        }")
    out.append("        return id771;")
    out.append("    }")
    out.append("")
    out.append("    /**")
    out.append("     * 音の名前を770向けに変換する。771の音の名前のうち 770に無いものは")
    out.append("     * 代替の音の名前にする（数値の反転表と一致させる）。771の新音")
    out.append("     * （乾燥ガスト8件・ガストリング4件・ハッピーガスト8件・リード3件・")
    out.append("     * 乾いた草・レコード「涙」・装備外し4件の29件）は石の破壊音へ。")
    out.append("     * {@code null} は石の破壊音にする。")
    out.append("     */")
    out.append("    public static String mapSoundNameUp771(String name) {")
    out.append("        return mapSoundNameComposed(name, SOUND_NAME_FALLBACK_771_TO_770_MAP);")
    out.append("    }")
    return "\n".join(out)
def composed_accessors():
    out = ["    // ------------------------------------------------------------------",
           "    // 離れた組み合わせの合成表（763↔771、764↔771、765↔771、766↔771、",
           "    // 767↔771、768↔771、769↔771）。上の *_763_TO_771／*_771_TO_763／",
           "    // *_764_TO_771／*_771_TO_764／*_765_TO_771／*_771_TO_765／",
           "    // *_766_TO_771／*_771_TO_766／*_767_TO_771／*_771_TO_767／",
           "    // *_768_TO_771／*_771_TO_768／*_769_TO_771／*_771_TO_769 表",
           "    // （合成済みの1つの表）",
           "    // を直接引く。二段変換はしない。エフェクトとブロックエンティティ",
           "    // 種別は表を作らず、数値の規則と同一の並びで扱う。",
           "    // ------------------------------------------------------------------",
           ""]
    pairs = [
        ("763", 19, "763→771", "771→763"),
        ("764", 20, "764→771", "771→764"),
        ("765", 21, "765→771", "771→765"),
        ("766", 22, "766→771", "771→766"),
        ("767", 23, "767→771", "771→767"),
        ("768", 24, "768→771", "771→768"),
        ("769", 25, "769→771", "771→769"),
    ]
    # 770 は隣接表（770↔771）で扱うため合成のペアには含めない。
    for src, fb_src_idx, up_label, down_label in pairs:
        for kind in KINDS:
            name, prefix, jp = kind[0], kind[1], kind[2]
            fb_src = kind[fb_src_idx]
            fb_771 = kind[27]
            # src → 771
            out.append(f"    /** {src}の{jp}ID → 771の{jp}ID。範囲外は{fb_771} に相当する代替。 */")
            out.append(f"    public static int map{name}{src}To771(int id{src}) {{")
            out.append(f"        if (id{src} < 0 || id{src} >= {prefix}_{src}_TO_771_MAP.length) {{")
            out.append(f"            logUnknownComposedOnce(\"{up_label}\", \"{jp}\", id{src});")
            out.append(f"            return {fb_771};")
            out.append("        }")
            out.append(f"        return {prefix}_{src}_TO_771_MAP[id{src}];")
            out.append("    }")
            out.append("")
            # 771 → src
            out.append(f"    /** 771の{jp}ID → {src}の{jp}ID。範囲外は{fb_src} に相当する代替。 */")
            out.append(f"    public static int map{name}771To{src}(int id771) {{")
            out.append(f"        if (id771 < 0 || id771 >= {prefix}_771_TO_{src}_MAP.length) {{")
            out.append(f"            logUnknownComposedOnce(\"{down_label}\", \"{jp}\", id771);")
            out.append(f"            return {fb_src};")
            out.append("        }")
            out.append(f"        return {prefix}_771_TO_{src}_MAP[id771];")
            out.append("    }")
            out.append("")
        # エフェクトとブロックエンティティ種別と音の名前。
        if src == "763":
            out.append("    /**")
            out.append("     * 763のエフェクトID → 771のエフェクトID。763は1ベース")
            out.append("     * （Speed=1）のため 1 引く。範囲外は Speed（0）。")
            out.append("     */")
            out.append("    public static int mapEffect763To771(int id763) {")
            out.append("        if (id763 < 1 || id763 > MAX_EFFECT_765 + 1) {")
            out.append("            logUnknownComposedOnce(\"763→771\", \"エフェクト\", id763);")
            out.append("            return SPEED_EFFECT_771;")
            out.append("        }")
            out.append("        return id763 - 1;")
            out.append("    }")
            out.append("")
            out.append("    /**")
            out.append("     * 771のエフェクトID → 763のエフェクトID。763は1ベース")
            out.append("     * （Speed=1）のため 1 足す。771で増えた 33〜38 は")
            out.append("     * 763に無いため Speed（1）へ。範囲外も Speed（1）。")
            out.append("     */")
            out.append("    public static int mapEffect771To763(int id771) {")
            out.append("        if (id771 < 0 || id771 > MAX_EFFECT_771) {")
            out.append("            logUnknownComposedOnce(\"771→763\", \"エフェクト\", id771);")
            out.append("            return SPEED_EFFECT_763;")
            out.append("        }")
            out.append("        return id771 <= MAX_EFFECT_765 ? id771 + 1 : SPEED_EFFECT_763;")
            out.append("    }")
            out.append("")
            out.append("    /**")
            out.append("     * 763のブロックエンティティ種別ID → 771の種別ID。")
            out.append("     * 763の種別は 0〜9 で771と同じ並びのためそのまま。")
            out.append("     * creaking_heart（10）の挿入で piston 以降（10〜40）は +1 ずれる。")
            out.append("     * 763に無い番号（41以上）は771側の種別に対応付けられないため")
            out.append("     * {@link #DROP}。")
            out.append("     */")
            out.append("    public static int mapBlockEntityType763To771(int id763) {")
            out.append("        if (id763 < 0 || id763 > BLOCK_ENTITY_TRIAL_SPAWNER_765 - 2) {")
            out.append("            logUnknownComposedOnce(\"763→771\", \"ブロックエンティティ種別\", id763);")
            out.append("            return DROP;")
            out.append("        }")
            out.append("        return id763 < 10 ? id763 : id763 + 1;")
            out.append("    }")
            out.append("")
            out.append("    /**")
            out.append("     * 771のブロックエンティティ種別ID → 763の種別ID。")
            out.append("     * 771の種別は 0〜9 で763と同じ並びのためそのまま。")
            out.append("     * creaking_heart（10）は763に無いため {@link #DROP}。")
            out.append("     * piston 以降（11〜41）は -1 ずれる。crafter（42）・")
            out.append("     * trial_spawner（43）・vault（44）・test_block（45）・test_instance_block（46）は763に無いため {@link #DROP}。")
            out.append("     */")
            out.append("    public static int mapBlockEntityType771To763(int id771) {")
            out.append("        if (id771 < 0 || id771 > BLOCK_ENTITY_TRIAL_SPAWNER_771 - 2) {")
            out.append("            logUnknownComposedOnce(\"771→763\", \"ブロックエンティティ種別\", id771);")
            out.append("            return DROP;")
            out.append("        }")
            out.append("        if (id771 == BLOCK_ENTITY_CREAKING_HEART_771) {")
            out.append("            return DROP;")
            out.append("        }")
            out.append("        return id771 < 10 ? id771 : id771 - 1;")
            out.append("    }")
            out.append("")
            out.append("    /**")
            out.append("     * 音の名前を771向けに変換する。763の音の名前のうち 768で削除された")
            out.append("     * ヤギの角笛の音2件と 770で削除された entity.wolf.howl と")
            out.append("     * 771で削除された leash_knot 系・puffer_fish.ambient・")
            out.append("     * block.sand.wind は代替の名前にする。{@code null} は石の破壊音にする。")
            out.append("     */")
            out.append("    public static String mapSoundName763To771(String name) {")
            out.append("        return mapSoundNameComposed(")
            out.append("                mapSoundNameComposed(")
            out.append("                        mapSoundNameComposed(name, SOUND_NAME_FALLBACK_768_MAP),")
            out.append("                        SOUND_NAME_FALLBACK_770_MAP),")
            out.append("                SOUND_NAME_FALLBACK_771_MAP);")
            out.append("    }")
            out.append("")
            out.append("    /**")
            out.append("     * 音の名前を763向けに変換する。771の音の名前のうち 763に無いものは")
            out.append("     * 代替の音の名前にする（数値の合成表と一致させる）。")
            out.append("     * {@code null} は石の破壊音にする。")
            out.append("     */")
            out.append("    public static String mapSoundName771To763(String name) {")
            out.append("        return mapSoundNameComposed(name, SOUND_NAME_FALLBACK_771_TO_763_MAP);")
            out.append("    }")
        elif src == "764":
            out.append("    /**")
            out.append("     * 764のエフェクトID → 771のエフェクトID。764も771も0ベース")
            out.append("     * （Speed は両方 0）で 0〜32 は同一のため恒等写像。")
            out.append("     * 範囲外は Speed（0）。")
            out.append("     */")
            out.append("    public static int mapEffect764To771(int id764) {")
            out.append("        if (id764 < 0 || id764 > MAX_EFFECT_764) {")
            out.append("            logUnknownComposedOnce(\"764→771\", \"エフェクト\", id764);")
            out.append("            return SPEED_EFFECT_771;")
            out.append("        }")
            out.append("        return id764;")
            out.append("    }")
            out.append("")
            out.append("    /**")
            out.append("     * 771のエフェクトID → 764のエフェクトID。0〜32 は同一のため恒等写像。")
            out.append("     * 771で増えた 33〜38 は 764に無いため Speed（0）へ。")
            out.append("     * 範囲外も Speed（0）。")
            out.append("     */")
            out.append("    public static int mapEffect771To764(int id771) {")
            out.append("        if (id771 < 0 || id771 > MAX_EFFECT_771) {")
            out.append("            logUnknownComposedOnce(\"771→764\", \"エフェクト\", id771);")
            out.append("            return SPEED_EFFECT_764;")
            out.append("        }")
            out.append("        return id771 <= MAX_EFFECT_764 ? id771 : SPEED_EFFECT_764;")
            out.append("    }")
            out.append("")
            out.append("    /**")
            out.append("     * 764のブロックエンティティ種別ID → 771の種別ID。")
            out.append("     * 764の種別は 0〜9 で771と同じ並びのためそのまま。")
            out.append("     * creaking_heart（10）の挿入で piston 以降（10〜40）は +1 ずれる。")
            out.append("     * 764に無い番号（41以上）は771側の種別に対応付けられないため")
            out.append("     * {@link #DROP}。")
            out.append("     */")
            out.append("    public static int mapBlockEntityType764To771(int id764) {")
            out.append("        if (id764 < 0 || id764 > BLOCK_ENTITY_TRIAL_SPAWNER_765 - 2) {")
            out.append("            logUnknownComposedOnce(\"764→771\", \"ブロックエンティティ種別\", id764);")
            out.append("            return DROP;")
            out.append("        }")
            out.append("        return id764 < 10 ? id764 : id764 + 1;")
            out.append("    }")
            out.append("")
            out.append("    /**")
            out.append("     * 771のブロックエンティティ種別ID → 764の種別ID。")
            out.append("     * 771の種別は 0〜9 で764と同じ並びのためそのまま。")
            out.append("     * creaking_heart（10）は764に無いため {@link #DROP}。")
            out.append("     * piston 以降（11〜41）は -1 ずれる。crafter（42）・")
            out.append("     * trial_spawner（43）・vault（44）・test_block（45）・test_instance_block（46）は764に無いため {@link #DROP}。")
            out.append("     */")
            out.append("    public static int mapBlockEntityType771To764(int id771) {")
            out.append("        if (id771 < 0 || id771 > BLOCK_ENTITY_TRIAL_SPAWNER_771 - 2) {")
            out.append("            logUnknownComposedOnce(\"771→764\", \"ブロックエンティティ種別\", id771);")
            out.append("            return DROP;")
            out.append("        }")
            out.append("        if (id771 == BLOCK_ENTITY_CREAKING_HEART_771) {")
            out.append("            return DROP;")
            out.append("        }")
            out.append("        return id771 < 10 ? id771 : id771 - 1;")
            out.append("    }")
            out.append("")
            out.append("    /**")
            out.append("     * 音の名前を771向けに変換する。764の音の名前のうち 768で削除された")
            out.append("     * ヤギの角笛の音2件と 770で削除された entity.wolf.howl と")
            out.append("     * 771で削除された leash_knot 系・puffer_fish.ambient・")
            out.append("     * block.sand.wind は代替の名前にする。{@code null} は石の破壊音にする。")
            out.append("     */")
            out.append("    public static String mapSoundName764To771(String name) {")
            out.append("        return mapSoundNameComposed(")
            out.append("                mapSoundNameComposed(")
            out.append("                        mapSoundNameComposed(name, SOUND_NAME_FALLBACK_768_MAP),")
            out.append("                        SOUND_NAME_FALLBACK_770_MAP),")
            out.append("                SOUND_NAME_FALLBACK_771_MAP);")
            out.append("    }")
            out.append("")
            out.append("    /**")
            out.append("     * 音の名前を764向けに変換する。771の音の名前のうち 764に無いものは")
            out.append("     * 代替の音の名前にする（数値の合成表と一致させる）。")
            out.append("     * {@code null} は石の破壊音にする。")
            out.append("     */")
            out.append("    public static String mapSoundName771To764(String name) {")
            out.append("        return mapSoundNameComposed(name, SOUND_NAME_FALLBACK_771_TO_764_MAP);")
            out.append("    }")
        elif src == "765":
            out.append("    /**")
            out.append("     * 765のエフェクトID → 771のエフェクトID。765も771も0ベース")
            out.append("     * （Speed は両方 0）で 0〜32 は同一のため恒等写像。")
            out.append("     * 範囲外は Speed（0）。")
            out.append("     */")
            out.append("    public static int mapEffect765To771(int id765) {")
            out.append("        if (id765 < 0 || id765 > MAX_EFFECT_765) {")
            out.append("            logUnknownComposedOnce(\"765→771\", \"エフェクト\", id765);")
            out.append("            return SPEED_EFFECT_771;")
            out.append("        }")
            out.append("        return id765;")
            out.append("    }")
            out.append("")
            out.append("    /**")
            out.append("     * 771のエフェクトID → 765のエフェクトID。0〜32 は同一のため恒等写像。")
            out.append("     * 771で増えた 33〜38 は 765に無いため Speed（0）へ。")
            out.append("     * 範囲外も Speed（0）。")
            out.append("     */")
            out.append("    public static int mapEffect771To765(int id771) {")
            out.append("        if (id771 < 0 || id771 > MAX_EFFECT_771) {")
            out.append("            logUnknownComposedOnce(\"771→765\", \"エフェクト\", id771);")
            out.append("            return SPEED_EFFECT_765;")
            out.append("        }")
            out.append("        return id771 <= MAX_EFFECT_765 ? id771 : SPEED_EFFECT_765;")
            out.append("    }")
            out.append("")
            out.append("    /**")
            out.append("     * 765のブロックエンティティ種別ID → 771の種別ID。")
            out.append("     * crafter（41）と trial_spawner（42）は771にもあるが、")
            out.append("     * creaking_heart（10）の挿入で +1 ずれる。")
            out.append("     * 範囲外の種別は NBT の中身が種別専用のため安全に代替できず")
            out.append("     * {@link #DROP}。")
            out.append("     */")
            out.append("    public static int mapBlockEntityType765To771(int id765) {")
            out.append("        if (id765 < 0 || id765 > BLOCK_ENTITY_TRIAL_SPAWNER_765) {")
            out.append("            logUnknownComposedOnce(\"765→771\", \"ブロックエンティティ種別\", id765);")
            out.append("            return DROP;")
            out.append("        }")
            out.append("        return id765 < 10 ? id765 : id765 + 1;")
            out.append("    }")
            out.append("")
            out.append("    /**")
            out.append("     * 771のブロックエンティティ種別ID → 765の種別ID。")
            out.append("     * creaking_heart（10）は765に無いため {@link #DROP}。")
            out.append("     * piston 以降（11〜43）は -1 ずれる。vault（44）・test_block（45）・")
            out.append("     * test_instance_block（46）は765に無いため {@link #DROP}。")
            out.append("     */")
            out.append("    public static int mapBlockEntityType771To765(int id771) {")
            out.append("        if (id771 < 0 || id771 > BLOCK_ENTITY_TRIAL_SPAWNER_771) {")
            out.append("            logUnknownComposedOnce(\"771→765\", \"ブロックエンティティ種別\", id771);")
            out.append("            return DROP;")
            out.append("        }")
            out.append("        if (id771 == BLOCK_ENTITY_CREAKING_HEART_771) {")
            out.append("            return DROP;")
            out.append("        }")
            out.append("        return id771 < 10 ? id771 : id771 - 1;")
            out.append("    }")
            out.append("")
            out.append("    /**")
            out.append("     * 音の名前を771向けに変換する。765の音の名前のうち 766で改名された")
            out.append("     * entity.generic.wind_burst と 767で改名された trial_spawner の音2件は")
            out.append("     * 新しい名前にし、768で削除されたヤギの角笛の音2件と 770で削除された")
            out.append("     * entity.wolf.howl と 771で削除された leash_knot 系・")
            out.append("     * puffer_fish.ambient・block.sand.wind は代替の名前にする。")
            out.append("     * {@code null} は石の破壊音にする。")
            out.append("     */")
            out.append("    public static String mapSoundName765To771(String name) {")
            out.append("        return mapSoundNameComposed(")
            out.append("                mapSoundNameComposed(")
            out.append("                        mapSoundNameComposed(")
            out.append("                                mapSoundNameComposed(")
            out.append("                                        mapSoundNameComposed(name, SOUND_NAME_FALLBACK_766_MAP),")
            out.append("                                        SOUND_NAME_FALLBACK_767_MAP),")
            out.append("                                SOUND_NAME_FALLBACK_768_MAP),")
            out.append("                        SOUND_NAME_FALLBACK_770_MAP),")
            out.append("                SOUND_NAME_FALLBACK_771_MAP);")
            out.append("    }")
            out.append("")
            out.append("    /**")
            out.append("     * 音の名前を765向けに変換する。771の音の名前のうち 765に無いものは")
            out.append("     * 代替の音の名前にする（数値の合成表と一致させる）。")
            out.append("     * {@code null} は石の破壊音にする。")
            out.append("     */")
            out.append("    public static String mapSoundName771To765(String name) {")
            out.append("        return mapSoundNameComposed(name, SOUND_NAME_FALLBACK_771_TO_765_MAP);")
            out.append("    }")
        elif src == "766":
            out.append("    /**")
            out.append("     * 766のエフェクトID → 771のエフェクトID。766も771も0ベース")
            out.append("     * （Speed は両方 0）で 0〜38 は同一のため恒等写像。")
            out.append("     * 範囲外は Speed（0）。")
            out.append("     */")
            out.append("    public static int mapEffect766To771(int id766) {")
            out.append("        if (id766 < 0 || id766 > MAX_EFFECT_766) {")
            out.append("            logUnknownComposedOnce(\"766→771\", \"エフェクト\", id766);")
            out.append("            return SPEED_EFFECT_771;")
            out.append("        }")
            out.append("        return id766;")
            out.append("    }")
            out.append("")
            out.append("    /**")
            out.append("     * 771のエフェクトID → 766のエフェクトID。0〜38 は同一のため恒等写像。")
            out.append("     * 範囲外は Speed（0）。")
            out.append("     */")
            out.append("    public static int mapEffect771To766(int id771) {")
            out.append("        if (id771 < 0 || id771 > MAX_EFFECT_771) {")
            out.append("            logUnknownComposedOnce(\"771→766\", \"エフェクト\", id771);")
            out.append("            return SPEED_EFFECT_766;")
            out.append("        }")
            out.append("        return id771;")
            out.append("    }")
            out.append("")
            out.append("    /**")
            out.append("     * 766のブロックエンティティ種別ID → 771の種別ID。")
            out.append("     * vault（43）は771にもあるが、creaking_heart（10）の挿入で")
            out.append("     * piston 以降（10〜43）は +1 ずれる。")
            out.append("     * 範囲外の種別は NBT の中身が種別専用のため安全に代替できず")
            out.append("     * {@link #DROP}。")
            out.append("     */")
            out.append("    public static int mapBlockEntityType766To771(int id766) {")
            out.append("        if (id766 < 0 || id766 > BLOCK_ENTITY_VAULT_766) {")
            out.append("            logUnknownComposedOnce(\"766→771\", \"ブロックエンティティ種別\", id766);")
            out.append("            return DROP;")
            out.append("        }")
            out.append("        return id766 < 10 ? id766 : id766 + 1;")
            out.append("    }")
            out.append("")
            out.append("    /**")
            out.append("     * 771のブロックエンティティ種別ID → 766の種別ID。")
            out.append("     * creaking_heart（10）は766に無いため {@link #DROP}。")
            out.append("     * piston 以降（11〜44）は -1 ずれる。vault は 771=44 → 766=43。")
            out.append("     * test_block（45）・test_instance_block（46）は766に無いため DROP。")
            out.append("     */")
            out.append("    public static int mapBlockEntityType771To766(int id771) {")
            out.append("        if (id771 < 0 || id771 > BLOCK_ENTITY_VAULT_771) {")
            out.append("            logUnknownComposedOnce(\"771→766\", \"ブロックエンティティ種別\", id771);")
            out.append("            return DROP;")
            out.append("        }")
            out.append("        if (id771 == BLOCK_ENTITY_CREAKING_HEART_771) {")
            out.append("            return DROP;")
            out.append("        }")
            out.append("        return id771 < 10 ? id771 : id771 - 1;")
            out.append("    }")
            out.append("")
            out.append("    /**")
            out.append("     * 音の名前を771向けに変換する。766の音の名前のうち 767で改名された")
            out.append("     * trial_spawner の音2件は新しい名前にし、768で削除されたヤギの角笛の音2件と")
            out.append("     * 770で削除された entity.wolf.howl と 771で削除された leash_knot 系・")
            out.append("     * puffer_fish.ambient・block.sand.wind は代替の名前にする。")
            out.append("     * {@code null} は石の破壊音にする。")
            out.append("     */")
            out.append("    public static String mapSoundName766To771(String name) {")
            out.append("        return mapSoundNameComposed(name, SOUND_NAME_FALLBACK_766_TO_771_MAP);")
            out.append("    }")
            out.append("")
            out.append("    /**")
            out.append("     * 音の名前を766向けに変換する。771の音の名前のうち 766に無いものは")
            out.append("     * 代替の音の名前にする（数値の合成表と一致させる）。")
            out.append("     * {@code null} は石の破壊音にする。")
            out.append("     */")
            out.append("    public static String mapSoundName771To766(String name) {")
            out.append("        return mapSoundNameComposed(name, SOUND_NAME_FALLBACK_771_TO_766_MAP);")
            out.append("    }")
        elif src == "768":
            out.append("    /**")
            out.append("     * 768のエフェクトID → 771のエフェクトID。768も771も0ベース")
            out.append("     * （Speed は両方 0）で 0〜38 は同一のため恒等写像。")
            out.append("     * 範囲外は Speed（0）。")
            out.append("     */")
            out.append("    public static int mapEffect768To771(int id768) {")
            out.append("        if (id768 < 0 || id768 > MAX_EFFECT_768) {")
            out.append("            logUnknownComposedOnce(\"768→771\", \"エフェクト\", id768);")
            out.append("            return SPEED_EFFECT_771;")
            out.append("        }")
            out.append("        return id768;")
            out.append("    }")
            out.append("")
            out.append("    /**")
            out.append("     * 771のエフェクトID → 768のエフェクトID。0〜38 は同一のため恒等写像。")
            out.append("     * 範囲外は Speed（0）。")
            out.append("     */")
            out.append("    public static int mapEffect771To768(int id771) {")
            out.append("        if (id771 < 0 || id771 > MAX_EFFECT_771) {")
            out.append("            logUnknownComposedOnce(\"771→768\", \"エフェクト\", id771);")
            out.append("            return SPEED_EFFECT_768;")
            out.append("        }")
            out.append("        return id771;")
            out.append("    }")
            out.append("")
            out.append("    /**")
            out.append("     * 768のブロックエンティティ種別ID → 771の種別ID。")
            out.append("     * 768の種別は 0〜44 で771と同じ並びのためそのまま。")
            out.append("     * 範囲外の種別は NBT の中身が種別専用のため安全に代替できず")
            out.append("     * {@link #DROP}。")
            out.append("     */")
            out.append("    public static int mapBlockEntityType768To771(int id768) {")
            out.append("        if (id768 < 0 || id768 > BLOCK_ENTITY_VAULT_768) {")
            out.append("            logUnknownComposedOnce(\"768→771\", \"ブロックエンティティ種別\", id768);")
            out.append("            return DROP;")
            out.append("        }")
            out.append("        return id768;")
            out.append("    }")
            out.append("")
            out.append("    /**")
            out.append("     * 771のブロックエンティティ種別ID → 768の種別ID。")
            out.append("     * 0〜44 は並びが同一のためそのまま。test_block（45）・")
            out.append("     * test_instance_block（46）は768に無いため {@link #DROP}。")
            out.append("     */")
            out.append("    public static int mapBlockEntityType771To768(int id771) {")
            out.append("        if (id771 < 0 || id771 > BLOCK_ENTITY_VAULT_771) {")
            out.append("            logUnknownComposedOnce(\"771→768\", \"ブロックエンティティ種別\", id771);")
            out.append("            return DROP;")
            out.append("        }")
            out.append("        return id771;")
            out.append("    }")
            out.append("")
            out.append("    /**")
            out.append("     * 音の名前を771向けに変換する。768の音の名前のうち 770で削除された")
            out.append("     * entity.wolf.howl と 771で削除された leash_knot 系・")
            out.append("     * puffer_fish.ambient・block.sand.wind は代替の名前にする。")
            out.append("     * {@code null} は石の破壊音にする。")
            out.append("     */")
            out.append("    public static String mapSoundName768To771(String name) {")
            out.append("        return mapSoundNameComposed(name, SOUND_NAME_FALLBACK_768_TO_771_MAP);")
            out.append("    }")
            out.append("")
            out.append("    /**")
            out.append("     * 音の名前を768向けに変換する。771の音の名前のうち 768に無いものは")
            out.append("     * 代替の音の名前にする（数値の合成表と一致させる）。")
            out.append("     * {@code null} は石の破壊音にする。")
            out.append("     */")
            out.append("    public static String mapSoundName771To768(String name) {")
            out.append("        return mapSoundNameComposed(name, SOUND_NAME_FALLBACK_771_TO_768_MAP);")
            out.append("    }")
        elif src == "767":
            out.append("    /**")
            out.append("     * 767のエフェクトID → 771のエフェクトID。767も771も0ベース")
            out.append("     * （Speed は両方 0）で 0〜38 は同一のため恒等写像。")
            out.append("     * 範囲外は Speed（0）。")
            out.append("     */")
            out.append("    public static int mapEffect767To771(int id767) {")
            out.append("        if (id767 < 0 || id767 > MAX_EFFECT_767) {")
            out.append("            logUnknownComposedOnce(\"767→771\", \"エフェクト\", id767);")
            out.append("            return SPEED_EFFECT_771;")
            out.append("        }")
            out.append("        return id767;")
            out.append("    }")
            out.append("")
            out.append("    /**")
            out.append("     * 771のエフェクトID → 767のエフェクトID。0〜38 は同一のため恒等写像。")
            out.append("     * 範囲外は Speed（0）。")
            out.append("     */")
            out.append("    public static int mapEffect771To767(int id771) {")
            out.append("        if (id771 < 0 || id771 > MAX_EFFECT_771) {")
            out.append("            logUnknownComposedOnce(\"771→767\", \"エフェクト\", id771);")
            out.append("            return SPEED_EFFECT_767;")
            out.append("        }")
            out.append("        return id771;")
            out.append("    }")
            out.append("")
            out.append("    /**")
            out.append("     * 767のブロックエンティティ種別ID → 771の種別ID。")
            out.append("     * vault（43）は771にもあるが、creaking_heart（10）の挿入で")
            out.append("     * piston 以降（10〜43）は +1 ずれる。")
            out.append("     * 範囲外の種別は NBT の中身が種別専用のため安全に代替できず")
            out.append("     * {@link #DROP}。")
            out.append("     */")
            out.append("    public static int mapBlockEntityType767To771(int id767) {")
            out.append("        if (id767 < 0 || id767 > BLOCK_ENTITY_VAULT_766) {")
            out.append("            logUnknownComposedOnce(\"767→771\", \"ブロックエンティティ種別\", id767);")
            out.append("            return DROP;")
            out.append("        }")
            out.append("        return id767 < 10 ? id767 : id767 + 1;")
            out.append("    }")
            out.append("")
            out.append("    /**")
            out.append("     * 771のブロックエンティティ種別ID → 767の種別ID。")
            out.append("     * creaking_heart（10）は767に無いため {@link #DROP}。")
            out.append("     * piston 以降（11〜44）は -1 ずれる。vault は 771=44 → 767=43。")
            out.append("     * test_block（45）・test_instance_block（46）は767に無いため DROP。")
            out.append("     */")
            out.append("    public static int mapBlockEntityType771To767(int id771) {")
            out.append("        if (id771 < 0 || id771 > BLOCK_ENTITY_VAULT_771) {")
            out.append("            logUnknownComposedOnce(\"771→767\", \"ブロックエンティティ種別\", id771);")
            out.append("            return DROP;")
            out.append("        }")
            out.append("        if (id771 == BLOCK_ENTITY_CREAKING_HEART_771) {")
            out.append("            return DROP;")
            out.append("        }")
            out.append("        return id771 < 10 ? id771 : id771 - 1;")
            out.append("    }")
            out.append("")
            out.append("    /**")
            out.append("     * 音の名前を771向けに変換する。767の音の名前のうち 768で削除された")
            out.append("     * ヤギの角笛の音2件と 770で削除された entity.wolf.howl と")
            out.append("     * 771で削除された leash_knot 系・puffer_fish.ambient・")
            out.append("     * block.sand.wind は代替の名前にする。")
            out.append("     * {@code null} は石の破壊音にする。")
            out.append("     */")
            out.append("    public static String mapSoundName767To771(String name) {")
            out.append("        return mapSoundNameComposed(name, SOUND_NAME_FALLBACK_767_TO_771_MAP);")
            out.append("    }")
            out.append("")
            out.append("    /**")
            out.append("     * 音の名前を767向けに変換する。771の音の名前のうち 767に無いものは")
            out.append("     * 代替の音の名前にする（数値の合成表と一致させる）。")
            out.append("     * {@code null} は石の破壊音にする。")
            out.append("     */")
            out.append("    public static String mapSoundName771To767(String name) {")
            out.append("        return mapSoundNameComposed(name, SOUND_NAME_FALLBACK_771_TO_767_MAP);")
            out.append("    }")
        elif src == "769":
            out.append("    /**")
            out.append("     * 769のエフェクトID → 771のエフェクトID。769も771も0ベース")
            out.append("     * （Speed は両方 0）で 0〜38 は同一のため恒等写像。")
            out.append("     * 範囲外は Speed（0）。")
            out.append("     */")
            out.append("    public static int mapEffect769To771(int id769) {")
            out.append("        if (id769 < 0 || id769 > MAX_EFFECT_769) {")
            out.append("            logUnknownComposedOnce(\"769→771\", \"エフェクト\", id769);")
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
            out.append("            logUnknownComposedOnce(\"771→769\", \"エフェクト\", id771);")
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
            out.append("            logUnknownComposedOnce(\"769→771\", \"ブロックエンティティ種別\", id769);")
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
            out.append("            logUnknownComposedOnce(\"771→769\", \"ブロックエンティティ種別\", id771);")
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
    return "\n".join(out)

# バナー・絵画・装飾パターン・ダメージ種別の 771 向け（770 と同一のものは
# 恒等写像。ダメージ種別も 770 と同一の 49 件）。
DAMAGE_TYPE_771 = """
    /** 771のダメージ種別最大ID（49件。770と同一）。 */
    public static final int MAX_DAMAGE_TYPE_771 = 48;

    /** ダメージ種別ID：770 → 771（770と同一の 49 件で並びも同じため恒等写像）。
     * 実サーバーの Registry Data での確認は統合テストで行う。 */
    private static final int[] DAMAGE_TYPE_771_MAP = {
        0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19,
        20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32, 33, 34, 35, 36,
        37, 38, 39, 40, 41, 42, 43, 44, 45, 46, 47, 48
    };

    /** ダメージ種別ID：771 → 770（770と同一の 49 件で並びも同じため恒等写像）。 */
    private static final int[] DAMAGE_TYPE_REVERSE_771_MAP = {
        0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19,
        20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32, 33, 34, 35, 36,
        37, 38, 39, 40, 41, 42, 43, 44, 45, 46, 47, 48
    };
"""

SMALL_REGISTRY_CONSTANTS = """
    /** 771のバナーパターン最大ID（43件。770と同一と仮定。実バニラの
        Registry Data での確認は統合テストで行う）。 */
    public static final int MAX_BANNER_PATTERN_771 = 42;

    /** 771の絵画バリアント最大ID（50件。770と同一と仮定。実バニラの
        Registry Data での確認は統合テストで行う）。 */
    public static final int MAX_PAINTING_VARIANT_771 = 49;

    /** 771の装飾パターン最大ID（18件。770と同一と仮定。実バニラの
        Registry Data での確認は統合テストで行う）。 */
    public static final int MAX_TRIM_PATTERN_771 = 17;
"""


def small_registry_accessors():
    out = []
    specs = [
        ("BannerPattern", "バナーパターン", "MAX_BANNER_PATTERN_770", "MAX_BANNER_PATTERN_771"),
        ("PaintingVariant", "絵画バリアント", "MAX_PAINTING_VARIANT_770", "MAX_PAINTING_VARIANT_771"),
        ("TrimPattern", "装飾パターン", "MAX_TRIM_PATTERN_770", "MAX_TRIM_PATTERN_771"),
    ]
    for name, jp, max770, max771 in specs:
        out.append(f"    /** 770の{jp}ID → 771の{jp}ID。770と771で同一のため恒等写像。範囲外は {{@link #DROP}}。 */")
        out.append(f"    public static int map{name}770To771(int id770) {{")
        out.append(f"        if (id770 < 0 || id770 > {max770}) {{")
        out.append(f"            logUnknownOnce(PROTOCOL_771, \"{jp}\", id770);")
        out.append("            return DROP;")
        out.append("        }")
        out.append("        return id770;")
        out.append("    }")
        out.append("")
        out.append(f"    /** 771の{jp}ID → 770の{jp}ID。770と771で同一のため恒等写像。範囲外は {{@link #DROP}}。 */")
        out.append(f"    public static int map{name}771To770(int id771) {{")
        out.append(f"        if (id771 < 0 || id771 > {max771}) {{")
        out.append(f"            logUnknownOnce(PROTOCOL_771, \"{jp}\", id771);")
        out.append("            return DROP;")
        out.append("        }")
        out.append("        return id771;")
        out.append("    }")
        out.append("")
        for src in ("763", "764", "765", "766", "767", "768", "769"):
            # 770 のメソッドを経由する（763／764／765／766／767／768／769→770 と
            # 770→763／764／765／766／767／768／769 は既にある）。
            out.append(f"    /** {src}の{jp}ID → 771の{jp}ID（770経由）。 */")
            out.append(f"    public static int map{name}{src}To771(int id{src}) {{")
            out.append(f"        return map{name}770To771(map{name}{src}To770(id{src}));")
            out.append("    }")
            out.append("")
            out.append(f"    /** 771の{jp}ID → {src}の{jp}ID（770経由）。 */")
            out.append(f"    public static int map{name}771To{src}(int id771) {{")
            out.append(f"        return map{name}770To{src}(map{name}771To770(id771));")
            out.append("    }")
            out.append("")
    return "\n".join(out)


def damage_accessors():
    out = []
    out.append("    /** 770のダメージ種別ID → 771のダメージ種別ID。770と同一のため恒等写像。範囲外は {@link #DROP}。 */")
    out.append("    public static int mapDamageType770To771(int id770) {")
    out.append("        if (id770 < 0 || id770 > MAX_DAMAGE_TYPE_770) {")
    out.append("            logUnknownOnce(PROTOCOL_771, \"ダメージ種別\", id770);")
    out.append("            return DROP;")
    out.append("        }")
    out.append("        return DAMAGE_TYPE_771_MAP[id770];")
    out.append("    }")
    out.append("")
    out.append("    /** 771のダメージ種別ID → 770のダメージ種別ID。770と同一のため恒等写像。範囲外は {@link #DROP}。 */")
    out.append("    public static int mapDamageType771To770(int id771) {")
    out.append("        if (id771 < 0 || id771 > MAX_DAMAGE_TYPE_771) {")
    out.append("            logUnknownOnce(PROTOCOL_771, \"ダメージ種別\", id771);")
    out.append("            return DROP;")
    out.append("        }")
    out.append("        return DAMAGE_TYPE_REVERSE_771_MAP[id771];")
    out.append("    }")
    out.append("")
    for src in ("763", "764", "765", "766", "767", "768", "769"):
        # ダメージ種別は 770 のメソッドを経由する。
        out.append(f"    /** {src}のダメージ種別ID → 771のダメージ種別ID（770経由）。 */")
        out.append(f"    public static int mapDamageType{src}To771(int id{src}) {{")
        out.append(f"        return mapDamageType770To771(mapDamageType{src}To770(id{src}));")
        out.append("    }")
        out.append("")
        out.append(f"    /** 771のダメージ種別ID → {src}のダメージ種別ID（770経由）。 */")
        out.append(f"    public static int mapDamageType771To{src}(int id771) {{")
        out.append(f"        return mapDamageType770To{src}(mapDamageType771To770(id771));")
        out.append("    }")
        out.append("")
    return "\n".join(out)


JAVADOC_771 = """ *
 * <p>771（1.21.6）向けの表も同じ作り方で持つ（{@code *_771}）。
 * 771は770に近く、差分は次の通り（ViaVersion mapping-1.21.6.json と
 * mapping-1.21.5.json、minecraft-data 1.21.6 の突き合わせ）。
 * ブロック状態は新32件（dried_ghast：向き4×hydration 4×水没2）・
 * 削除0件。ブロックは新1件（dried_ghast）。アイテムは新19件
 * （dried_ghast・ハーネス16色・happy_ghast_spawn_egg・
 * music_disc_tears）。エンティティは新1件（happy_ghast）。
 * 音は新29件・削除4件（entity.leash_knot.break／place・
 * entity.puffer_fish.ambient・block.sand.wind）。パーティクル・
 * メニュー・バイオーム・エフェクト・エンチャント・データコンポーネントは
 * 同一。属性は新3件（camera_distance が 8 に挿入、
 * waypoint_transmit_range・waypoint_receive_range を末尾に追加）。
 * ブロックエンティティ種別は 770 と同じ 47 件。ダメージ種別・
 * バナーパターン・絵画バリアント・装飾パターンは 770 と同一（実バニラの
 * Registry Data での確認は統合テストで行う）。
 * 770→771 の隣接表と 763／764／765／766／767／768／769→771 の合成表を持ち、
 * 逆向き 771→770 は反転して作る。
"""


def insert_after(java, anchor, block, label):
    if anchor not in java:
        print(f"アンカーが見つからない（{label}）", file=sys.stderr)
        return None
    return java.replace(anchor, anchor + block, 1)


def main():
    if (not TABLES771_FILE.exists() or not TABLES771_COMPOSED_FILE.exists()
            or not all(p.exists() for p in TABLES771_COMPOSED_DOWN_FILES.values())):
        print("RegistryTables771.java / RegistryTables771ComposedUp.java / "
              "RegistryTables771Down*.java が無い。"
              "先に insert-771-tables.py を実行すること", file=sys.stderr)
        return 1
    java = JAVA_FILE.read_text(encoding="utf-8")
    checks = [
        ("PROTOCOL_771", "771 の定数・表・アクセサは既にある"),
        ("BLOCK_STATE_771_MAP", "771 の展開済み表は既にある"),
        ("mapBlockState770To771", "771 のアクセサは既にある"),
    ]
    for marker, msg in checks:
        if marker in java:
            print(msg, file=sys.stderr)
            return 1

    # import static。
    java = insert_after(java,
                        "import static io.detergentmc.protocol.RegistryTables770Down768.*;\n",
                        "import static io.detergentmc.protocol.RegistryTables771.*;\n"
                        "import static io.detergentmc.protocol.RegistryTables771ComposedUp.*;\n"
                        "import static io.detergentmc.protocol.RegistryTables771Down763.*;\n"
                        "import static io.detergentmc.protocol.RegistryTables771Down764.*;\n"
                        "import static io.detergentmc.protocol.RegistryTables771Down765.*;\n"
                        "import static io.detergentmc.protocol.RegistryTables771Down766.*;\n"
                        "import static io.detergentmc.protocol.RegistryTables771Down767.*;\n"
                        "import static io.detergentmc.protocol.RegistryTables771Down768.*;\n"
                        "import static io.detergentmc.protocol.RegistryTables771Down769.*;\n",
                        "import static RegistryTables771")
    if java is None:
        return 1

    # クラスの javadoc に 771 の段落を追加（770 の段落の直後）。
    anchor = (" * 769→770 の隣接表と 763／764／765／766／767／768→770 の合成表を持ち、\n"
              " * 逆向き 770→769 は反転して作る。\n")
    java = insert_after(java, anchor, JAVADOC_771, "javadoc")
    if java is None:
        return 1

    # 定数。
    java = insert_after(java,
                        "    public static final int PROTOCOL_770 = 770;\n",
                        "    public static final int PROTOCOL_771 = 771;\n", "PROTOCOL_771")
    if java is None:
        return 1
    java = insert_after(java,
                        "    public static final int MAX_EFFECT_770 = 38;\n",
                        CONSTANTS_MAX, "MAX_771")
    if java is None:
        return 1
    java = insert_after(java,
                        "    public static final int SPEED_EFFECT_770 = 0;\n",
                        CONSTANTS_FALLBACK, "771 の代替先")
    if java is None:
        return 1
    java = insert_after(java,
                        "    public static final int BLOCK_ENTITY_VAULT_770 = 44;\n",
                        CONSTANTS_BLOCK_ENTITY, "771 のブロックエンティティ種別")
    if java is None:
        return 1

    # 隣接表（770→771）。MENU_770_MAP の直後。
    anchor = ("    private static final int[] MENU_770_MAP =\n"
              "            expand(MENU_770_RANGES, MENU_770_OVERRIDES, MAX_MENU_769 + 1,\n"
              "                    CRAFTING_MENU_770);\n")
    java = insert_after(java, anchor, adjacent_maps() + "\n", "隣接表 771")
    if java is None:
        return 1

    # 合成表。MENU_770_TO_768_MAP の直後。
    anchor = ("    private static final int[] MENU_770_TO_768_MAP =\n"
              "            expand(MENU_770_TO_768_RANGES, MENU_770_TO_768_OVERRIDES,\n"
              "                    25, CRAFTING_MENU_768);\n")
    java = insert_after(java, anchor, "\n" + composed_maps() + "\n", "合成表 771")
    if java is None:
        return 1

    # 反転表（771→770）。MENU_UP_770_MAP の直後。
    anchor = ("    private static final int[] MENU_UP_770_MAP = invert(\n"
              "            MENU_770_RANGES, MENU_770_OVERRIDES,\n"
              "            MAX_MENU_769 + 1, MAX_MENU_770 + 1,\n"
              "            CRAFTING_MENU_769, null);\n")
    java = insert_after(java, anchor, up_maps() + "\n", "反転表 771")
    if java is None:
        return 1

    # 音の名前の表。
    anchor = ("    private static final Map<String, String> SOUND_NAME_FALLBACK_770_TO_766_MAP =\n"
              "            buildSoundNameMap(SOUND_NAME_FALLBACKS_770_TO_766);\n")
    java = insert_after(java, anchor, SOUND_NAME_MAPS, "音の名前の表 771")
    if java is None:
        return 1

    # ログ。
    anchor = ("    private static void logUnknownUp770Once(String kind, int id770) {\n"
              "        if (LOGGED_UNKNOWN.add(\"up770:\" + kind + \":\" + id770)) {\n"
              "            LOG.warning(\"770に無い\" + kind + \"の番号が来たため代替に落とします（770 id=\" + id770 + \"）。\");\n"
              "        }\n"
              "    }\n")
    java = insert_after(java, anchor, LOG_UP_771, "logUnknownUp771Once")
    if java is None:
        return 1

    # アクセサ（a）770→771。mapSoundName767To770 の直後。
    anchor = ("    public static String mapSoundName767To770(String name) {\n"
              "        return mapSoundNameComposed(name, SOUND_NAME_FALLBACK_767_TO_770_MAP);\n"
              "    }\n")
    java = insert_after(java, anchor, "\n" + adjacent_accessors() + "\n", "アクセサ 770→771")
    if java is None:
        return 1

    # アクセサ（b）771→770。mapSoundNameUp770 の直後。
    anchor = ("    public static String mapSoundNameUp770(String name) {\n"
              "        return mapSoundNameComposed(name, SOUND_NAME_FALLBACK_770_TO_769_MAP);\n"
              "    }\n")
    java = insert_after(java, anchor, "\n" + up_accessors() + "\n", "アクセサ 771→770")
    if java is None:
        return 1

    # アクセサ（c）763／764／765／766／767／768／769↔771。mapSoundName770To766 の直後。
    anchor = ("    public static String mapSoundName770To766(String name) {\n"
              "        return mapSoundNameComposed(name, SOUND_NAME_FALLBACK_770_TO_766_MAP);\n"
              "    }\n")
    java = insert_after(java, anchor, "\n" + composed_accessors() + "\n", "アクセサ 763/764/765/766/767↔771")
    if java is None:
        return 1

    # バナー・絵画・装飾パターン・ダメージ種別の定数。
    java = insert_after(java,
                        "    /** 770のバナーパターン最大ID（43件。769と同一と仮定。実バニラの\n"
                        "        Registry Data での確認は統合テストで行う）。 */\n"
                        "    public static final int MAX_BANNER_PATTERN_770 = 42;\n",
                        SMALL_REGISTRY_CONSTANTS, "バナー等の771定数")
    if java is None:
        return 1
    anchor = ("    private static final int[] DAMAGE_TYPE_REVERSE_770_MAP = {\n"
              "        0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19,\n"
              "        20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32, 33, 34, 35, 36,\n"
              "        37, 38, 39, 40, 41, 42, 43, 44, 45, 46, 47, 48\n"
              "    };\n")
    java = insert_after(java, anchor, DAMAGE_TYPE_771, "ダメージ種別771の表")
    if java is None:
        return 1

    # バナー・絵画・装飾パターン・ダメージ種別のアクセサ。
    anchor = ("    /** 770のバナーパターンID → 766のバナーパターンID（769経由）。 */\n"
              "    public static int mapBannerPattern770To766(int id770) {\n"
              "        return mapBannerPattern769To766(mapBannerPattern770To769(id770));\n"
              "    }\n")
    banner_painting_trim = small_registry_accessors()
    java = insert_after(java, anchor, "\n" + banner_painting_trim + "\n", "バナー等の771アクセサ")
    if java is None:
        return 1
    anchor = ("    /** 770のダメージ種別ID → 766のダメージ種別ID（769経由）。 */\n"
              "    public static int mapDamageType770To766(int id770) {\n"
              "        return mapDamageType769To766(mapDamageType770To769(id770));\n"
              "    }\n")
    java = insert_after(java, anchor, "\n" + damage_accessors() + "\n", "ダメージ種別771のアクセサ")
    if java is None:
        return 1

    # プロトコル番号で切り替える変換（担当2の配線用）に 771 を追加。
    dispatches = [
        ("mapBlockState", "mapBlockState765To770(id765)", "mapBlockState765To771(id765)"),
        ("mapItem", "mapItem765To770(id765)", "mapItem765To771(id765)"),
        ("mapItemReverse", "mapItem770To765(id)", "mapItem771To765(id)"),
        ("mapEntityType", "mapEntityType765To770(id765)", "mapEntityType765To771(id765)"),
        ("mapSound", "mapSound765To770(id765)", "mapSound765To771(id765)"),
        ("mapParticle", "mapParticle765To770(id765)", "mapParticle765To771(id765)"),
        ("mapBlock", "mapBlock765To770(id765)", "mapBlock765To771(id765)"),
        ("mapMenu", "mapMenu765To770(id765)", "mapMenu765To771(id765)"),
        ("mapEffect", "mapEffect765To770(id765)", "mapEffect765To771(id765)"),
        ("mapBlockEntityType", "mapBlockEntityType765To770(id765)", "mapBlockEntityType765To771(id765)"),
        ("mapSoundName", "mapSoundName765To770(name)", "mapSoundName765To771(name)"),
    ]
    for label, old_call, new_call in dispatches:
        if label == "mapItemReverse":
            old = ("        if (clientProtocol == PROTOCOL_770) {\n"
                   "            // 770クライアントのアイテムID → 765サーバーのアイテムID。\n"
                   "            // mapItemReverse770 は 770→769 のため、ここでは合成表を使う。\n"
                   "            return mapItem770To765(id);\n"
                   "        }\n")
            new = (old
                   + "        if (clientProtocol == PROTOCOL_771) {\n"
                   "            // 771クライアントのアイテムID → 765サーバーのアイテムID。\n"
                   "            // mapItemReverse771 は 771→770 のため、ここでは合成表を使う。\n"
                   "            return mapItem771To765(id);\n"
                   "        }\n")
            if old not in java:
                print("ディスパッチのアンカーが見つからない（mapItemReverse）", file=sys.stderr)
                return 1
            java = java.replace(old, new, 1)
            continue
        old = ("        if (clientProtocol == PROTOCOL_770) {\n"
               f"            return {old_call};\n"
               "        }\n")
        if old not in java:
            print(f"ディスパッチのアンカーが見つからない（{label}）", file=sys.stderr)
            return 1
        java = java.replace(old, old
                            + "        if (clientProtocol == PROTOCOL_771) {\n"
                            + f"            return {new_call};\n"
                            + "        }\n", 1)
    java = java.replace(
        "    // 765は素通し、766は766の表、767は767の表、768は768の表、\n"
        "    // 769は769の表、770は770の表、それより古い未知の番号は763の表を使う。\n"
        "    // 767／768／769／770は765以上だが素通しではない（>765 の分岐より\n"
        "    // 先に 767／768／769／770 を判定すること）。",
        "    // 765は素通し、766は766の表、767は767の表、768は768の表、\n"
        "    // 769は769の表、770は770の表、771は771の表、それより古い未知の番号は763の表を使う。\n"
        "    // 767／768／769／770／771は765以上だが素通しではない（>765 の分岐より\n"
        "    // 先に 767／768／769／770／771 を判定すること）。",
        1)

    JAVA_FILE.write_text(java, encoding="utf-8")
    print("RegistryRemapper.java に 771 の定数・表・アクセサを挿入した")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
