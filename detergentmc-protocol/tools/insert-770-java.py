#!/usr/bin/env python3
"""tools/insert-769-java.py で入れた *_769 表の上に、RegistryRemapper.java
へ 770 対応の定数・展開済み表（*_770_MAP）・反転表（*_UP_770_MAP）・
アクセサを挿入する（1回だけの作業用）。

  - 定数：PROTOCOL_770、MAX_*_770、770 側の代替先。
  - 隣接表（769→770）: *_770_MAP と ITEM_REVERSE_770_MAP と
    ATTRIBUTE_770_MAP／ATTRIBUTE_REVERSE_770_MAP／ENCHANTMENT_770_MAP／
    ENCHANTMENT_REVERSE_770_MAP。
  - 合成表（763↔770、764↔770、765↔770、766↔770、767↔770、768↔770）: *_763_TO_770_MAP など84件。
  - 逆向き（770→769）: *_UP_770_MAP（invert()。アイテムは
    ITEM_REVERSE_770_MAP をそのまま使う。ブロック状態とエンティティに補正あり：
    creaking_heart の uprooted 6状態と lingering_potion→potion）。
  - 音の名前の表（String）: SOUND_NAME_FALLBACK_*_770_MAP など12件。
  - アクセサ：769↔770、770→769（上向き）、763／764／765／766／767／768↔770。
  - エフェクト：770 は 769 と同一の 39 件（0〜38）のため恒等写像。
    属性は 769・770 とも 32 件で同一、エンチャントは 42 件同士で
    並びは同一（minecraft-data の dataPaths.json で 1.21.5 の
    enchantments は pc/1.21.5 と確認。1.21.1 の並びと同一）。
    ブロックエンティティ種別は 769 の 45 件に test_block=45 と
    test_instance_block=46 を追加した 47 件。
    バナー・絵画・装飾パターンは 769 と同一のため恒等写像。
    ダメージ種別も 769 と同一の 49 件のため恒等写像。
    バイオームは 769・770 とも 65 件で同一の並び（実バニラの Registry Data
    での確認は統合テストで行う）。
"""

import re
import sys
from pathlib import Path

SCRIPT_DIR = Path(__file__).resolve().parent
REPO_ROOT = SCRIPT_DIR.parent.parent
JAVA_FILE = (REPO_ROOT / "detergentmc-protocol" / "src" / "main" / "java"
             / "io" / "detergentmc" / "protocol" / "RegistryRemapper.java")
TABLES770_FILE = (REPO_ROOT / "detergentmc-protocol" / "src" / "main" / "java"
                  / "io" / "detergentmc" / "protocol" / "RegistryTables770.java")
TABLES770_COMPOSED_FILE = (REPO_ROOT / "detergentmc-protocol" / "src" / "main" / "java"
                  / "io" / "detergentmc" / "protocol" / "RegistryTables770ComposedUp.java")
TABLES770_COMPOSED_DOWN_FILES = {
    suffix: (REPO_ROOT / "detergentmc-protocol" / "src" / "main" / "java"
             / "io" / "detergentmc" / "protocol"
             / f"RegistryTables770Down{suffix}.java")
    for suffix in ("763", "764", "765", "766", "767")
}


# kind: (Java メソッド名の語尾, RANGES の接頭辞, 日本語名,
#        763→770 の件数, 770→763 の件数, 764→770 の件数, 770→764 の件数,
#        765→770 の件数, 770→765 の件数, 766→770 の件数, 770→766 の件数,
#        767→770 の件数, 770→767 の件数, 768→770 の件数, 770→768 の件数,
#        763 側の代替先, 764 側の代替先, 765 側の代替先, 766 側の代替先,
#        767 側の代替先, 768 側の代替先, 770 側の代替先)
KINDS = [
    ("BlockState", "BLOCK_STATE", "ブロック状態", 24135, 27914, 24276, 27914, 26644, 27914, 26684, 27914, 26684, 27914, 27318, 27914,
     "STONE_BLOCK_STATE_763", "STONE_BLOCK_STATE_764", "STONE_BLOCK_STATE_765",
     "STONE_BLOCK_STATE_766", "STONE_BLOCK_STATE_767", "STONE_BLOCK_STATE_768", "STONE_BLOCK_STATE_770"),
    ("Item", "ITEM", "アイテム", 1255, 1396, 1255, 1396, 1312, 1396, 1330, 1396, 1333, 1396, 1375, 1396,
     "STONE_ITEM_763", "STONE_ITEM_764", "STONE_ITEM_765",
     "STONE_ITEM_766", "STONE_ITEM_767", "STONE_ITEM_768", "STONE_ITEM_770"),
    ("EntityType", "ENTITY_TYPE", "エンティティ種別", 124, 150, 124, 150, 126, 150, 130, 150, 130, 150, 150, 150,
     "PIG_ENTITY_TYPE_763", "PIG_ENTITY_TYPE_764", "PIG_ENTITY_TYPE_765",
     "PIG_ENTITY_TYPE_766", "PIG_ENTITY_TYPE_767", "PIG_ENTITY_TYPE_768", "PIG_ENTITY_TYPE_770"),
    ("Sound", "SOUND", "音", 1474, 1702, 1485, 1702, 1539, 1702, 1607, 1702, 1611, 1702, 1636, 1702,
     "GENERIC_SOUND_763", "GENERIC_SOUND_764", "GENERIC_SOUND_765",
     "GENERIC_SOUND_766", "GENERIC_SOUND_767", "GENERIC_SOUND_768", "GENERIC_SOUND_770"),
    ("Particle", "PARTICLE", "パーティクル", 95, 114, 95, 114, 101, 114, 109, 114, 109, 114, 111, 114,
     "AMBIENT_PARTICLE_763", "AMBIENT_PARTICLE_764", "AMBIENT_PARTICLE_765",
     "ENTITY_EFFECT_PARTICLE_766", "ENTITY_EFFECT_PARTICLE_767", "ENTITY_EFFECT_PARTICLE_768", "ENTITY_EFFECT_PARTICLE_770"),
    ("Block", "BLOCK", "ブロック", 1003, 1104, 1003, 1104, 1058, 1104, 1060, 1104, 1060, 1104, 1084, 1104,
     "STONE_BLOCK_763", "STONE_BLOCK_764", "STONE_BLOCK_765",
     "STONE_BLOCK_766", "STONE_BLOCK_767", "STONE_BLOCK_768", "STONE_BLOCK_770"),
    ("Menu", "MENU", "メニュー種別", 24, 25, 24, 25, 25, 25, 25, 25, 25, 25, 25, 25,
     "CRAFTING_MENU_763", "CRAFTING_MENU_764", "CRAFTING_MENU_765",
     "CRAFTING_MENU_766", "CRAFTING_MENU_767", "CRAFTING_MENU_768", "CRAFTING_MENU_770"),
]

CONSTANTS_MAX = """
    /** 770側の最大の番号（ViaVersion mapping-1.21.5.json の件数−1）。 */
    public static final int MAX_BLOCK_STATE_770 = 27913;
    public static final int MAX_ITEM_770 = 1395;
    public static final int MAX_ENTITY_TYPE_770 = 149;
    public static final int MAX_SOUND_770 = 1701;
    public static final int MAX_PARTICLE_770 = 113;
    public static final int MAX_BLOCK_770 = 1103;
    public static final int MAX_MENU_770 = 24;
    /** 770側のエフェクトも 0〜38（Speed=0。769と同一。minecraft-data の
        dataPaths.json で 1.21.5 の effects は pc/1.21.5 と確認）。 */
    public static final int MAX_EFFECT_770 = 38;
    /** 770側の属性は 0〜31（769と同一の 32 件。名前も並びも同一）。 */
    public static final int MAX_ATTRIBUTE_770 = 31;
    /** エンチャントは 769・770 とも 0〜41（42件。並びは同一。
        minecraft-data の dataPaths.json で 1.21.5 の enchantments は
        pc/1.21.5 と確認（1.21.1 の並びと同一）。実機の Registry Data での
        確認は統合テストで行う）。 */
    public static final int MAX_ENCHANTMENT_770 = 41;
"""

CONSTANTS_FALLBACK = """
    /** 770側の代替先（mapping-1.21.5.json の番号。Speed だけ 0ベースで 0）。
     * ブタは 770 では 94（potion の分割はブタより後ろで起きたため 769 と
     * 同じ）、石の破壊音は 770 では 1417（新音 52 件の挿入と
     * entity.wolf.howl の削除で 769 の 1401 からずれた）。 */
    public static final int STONE_BLOCK_STATE_770 = 1;
    public static final int AIR_BLOCK_STATE_770 = 0;
    public static final int STONE_ITEM_770 = 1;
    public static final int PIG_ENTITY_TYPE_770 = 94;
    public static final int GENERIC_SOUND_770 = 1417; // block.stone.break
    /** 770でも ambient_entity_effect は無いため、パーティクルの代替は entity_effect。 */
    public static final int ENTITY_EFFECT_PARTICLE_770 = 20;
    public static final int STONE_BLOCK_770 = 1;
    public static final int CRAFTING_MENU_770 = 12;
    public static final int SPEED_EFFECT_770 = 0;
"""

CONSTANTS_BLOCK_ENTITY = """
    /** 770のブロックエンティティ種別：769 の 45 件に test_block=45 と
        test_instance_block=46 を追加した 47 件（creaking_heart=10、
        trial_spawner=43、vault=44）。 */
    public static final int BLOCK_ENTITY_CREAKING_HEART_770 = 10;
    public static final int BLOCK_ENTITY_TRIAL_SPAWNER_770 = 43;
    public static final int BLOCK_ENTITY_VAULT_770 = 44;
    public static final int BLOCK_ENTITY_TEST_BLOCK_770 = 45;
    public static final int BLOCK_ENTITY_TEST_INSTANCE_BLOCK_770 = 46;
"""


def adjacent_maps():
    lines = []
    for kind in KINDS:
        name, prefix, jp = kind[0], kind[1], kind[2]
        lines.append(
            f"    private static final int[] {prefix}_770_MAP =\n"
            f"            expand({prefix}_770_RANGES, {prefix}_770_OVERRIDES, MAX_{prefix}_769 + 1,\n"
            f"                    {kind[21]});")
    lines.append(
        "    private static final int[] ITEM_REVERSE_770_MAP =\n"
        "            expand(ITEM_REVERSE_770_RANGES, ITEM_REVERSE_770_OVERRIDES, 1396, -1);")
    lines.append(
        "    private static final int[] ATTRIBUTE_770_MAP =\n"
        "            expand(ATTRIBUTE_770_RANGES, ATTRIBUTE_770_OVERRIDES, MAX_ATTRIBUTE_769 + 1, DROP);")
    lines.append(
        "    private static final int[] ATTRIBUTE_REVERSE_770_MAP =\n"
        "            expand(ATTRIBUTE_REVERSE_770_RANGES, ATTRIBUTE_REVERSE_770_OVERRIDES,\n"
        "                    MAX_ATTRIBUTE_770 + 1, DROP);")
    lines.append(
        "    private static final int[] ENCHANTMENT_770_MAP =\n"
        "            expand(ENCHANTMENT_770_RANGES, ENCHANTMENT_770_OVERRIDES,\n"
        "                    MAX_ENCHANTMENT_769 + 1, DROP);")
    lines.append(
        "    private static final int[] ENCHANTMENT_REVERSE_770_MAP =\n"
        "            expand(ENCHANTMENT_REVERSE_770_RANGES, ENCHANTMENT_REVERSE_770_OVERRIDES,\n"
        "                    MAX_ENCHANTMENT_770 + 1, DROP);")
    return "\n".join(lines)


def composed_maps():
    # (接尾辞, 件数の位置, 代替先の位置)。件数・代替先は KINDS の
    # タプルから取る（763_TO_770: 件数 index 3, 代替先 770=index 21）。
    pairs = [
        ("763_TO_770", 3, 21), ("770_TO_763", 4, 15),
        ("764_TO_770", 5, 21), ("770_TO_764", 6, 16),
        ("765_TO_770", 7, 21), ("770_TO_765", 8, 17),
        ("766_TO_770", 9, 21), ("770_TO_766", 10, 18),
        ("767_TO_770", 11, 21), ("770_TO_767", 12, 19),
        ("768_TO_770", 13, 21), ("770_TO_768", 14, 20),
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
            continue  # アイテムは ITEM_REVERSE_770_MAP を使う。
        # ブロック状態とエンティティに補正あり：770 の creaking_heart
        # のうち creaking_heart_state=uprooted の 6 状態は 769 の
        # active=false の心臓へ（BLOCK_STATE_UP_770_CORRECTIONS）。
        # 770 の lingering_potion（100）は 769 の potion（99）へ
        # （ENTITY_UP_770_CORRECTIONS。splash_potion は invert の先行勝ちで
        # 既に potion になるため補正から外す）。
        corrections = ("BLOCK_STATE_UP_770_CORRECTIONS"
                       if prefix == "BLOCK_STATE"
                       else "ENTITY_UP_770_CORRECTIONS"
                       if prefix == "ENTITY_TYPE" else "null")
        # 逆向きの代替先は「769 側」の定数。kind[19] は 767 側のためそのまま
        # 使うと範囲外の ID が 767 の代替先（769 では別な音・別な種類に
        # なっている）になってしまう。_767 → _769 に直して使う。
        # （up_accessors() の fb769 と同じ理由。）
        fb769 = kind[19].replace("_767", "_769")
        lines.append(
            f"    private static final int[] {prefix}_UP_770_MAP = invert(\n"
            f"            {prefix}_770_RANGES, {prefix}_770_OVERRIDES,\n"
            f"            MAX_{prefix}_769 + 1, MAX_{prefix}_770 + 1,\n"
            f"            {fb769}, {corrections});")
    return "\n".join(lines)


SOUND_NAME_MAPS = """
    private static final Map<String, String> SOUND_NAME_FALLBACK_770_MAP =
            buildSoundNameMap(SOUND_NAME_FALLBACKS_770);
    private static final Map<String, String> SOUND_NAME_FALLBACK_769_TO_770_MAP =
            buildSoundNameMap(SOUND_NAME_FALLBACKS_769_TO_770);
    private static final Map<String, String> SOUND_NAME_FALLBACK_770_TO_769_MAP =
            buildSoundNameMap(SOUND_NAME_FALLBACKS_770_TO_769);
    private static final Map<String, String> SOUND_NAME_FALLBACK_770_TO_768_MAP =
            buildSoundNameMap(SOUND_NAME_FALLBACKS_770_TO_768);
    private static final Map<String, String> SOUND_NAME_FALLBACK_770_TO_767_MAP =
            buildSoundNameMap(SOUND_NAME_FALLBACKS_770_TO_767);
    private static final Map<String, String> SOUND_NAME_FALLBACK_770_TO_765_MAP =
            buildSoundNameMap(SOUND_NAME_FALLBACKS_770_TO_765);
    private static final Map<String, String> SOUND_NAME_FALLBACK_770_TO_763_MAP =
            buildSoundNameMap(SOUND_NAME_FALLBACKS_770_TO_763);
    private static final Map<String, String> SOUND_NAME_FALLBACK_770_TO_764_MAP =
            buildSoundNameMap(SOUND_NAME_FALLBACKS_770_TO_764);
    private static final Map<String, String> SOUND_NAME_FALLBACK_770_TO_766_MAP =
            buildSoundNameMap(SOUND_NAME_FALLBACKS_770_TO_766);
    private static final Map<String, String> SOUND_NAME_FALLBACK_768_TO_770_MAP =
            buildSoundNameMap(SOUND_NAME_FALLBACKS_768_TO_770);
    private static final Map<String, String> SOUND_NAME_FALLBACK_767_TO_770_MAP =
            buildSoundNameMap(SOUND_NAME_FALLBACKS_767_TO_770);
    private static final Map<String, String> SOUND_NAME_FALLBACK_766_TO_770_MAP =
            buildSoundNameMap(SOUND_NAME_FALLBACKS_766_TO_770);
"""

LOG_UP_770 = """
    private static void logUnknownUp770Once(String kind, int id770) {
        if (LOGGED_UNKNOWN.add("up770:" + kind + ":" + id770)) {
            LOG.warning("770に無い" + kind + "の番号が来たため代替に落とします（770 id=" + id770 + "）。");
        }
    }
"""
def adjacent_accessors():
    out = ["    // ------------------------------------------------------------------",
           "    // 769→770。上の *_770 表を使う。ブロック状態・ブロック・",
           "    // アイテム・エンティティ・音・パーティクルは 769 から 770 で",
           "    // 茂み・ホタルブッシュ・枯れ草・ワイルドフラワー・リーフリッター・",
           "    // サボテンの花・テストブロック類・青い卵・茶色い卵が挿入でずれる。",
           "    // creaking_heart のプロパティが変わり、potion が splash／",
           "    // lingering に分かれ、entity.wolf.howl が削除された。",
           "    // メニュー・エフェクト・バナー・絵画・装飾パターン・ダメージ種別は",
           "    // 同一、エンチャントも属性も同一の並び。ブロックエンティティ種別は",
           "    // 45→47 件（test_block・test_instance_block を追加）。",
           "    // ------------------------------------------------------------------",
           ""]
    for kind in KINDS:
        name, prefix, jp = kind[0], kind[1], kind[2]
        fb770 = kind[21]
        out.append(f"    /** 769の{jp}ID → 770の{jp}ID。範囲外は{fb770} に相当する代替。 */")
        out.append(f"    public static int map{name}769To770(int id769) {{")
        out.append(f"        if (id769 < 0 || id769 > MAX_{prefix}_769) {{")
        out.append(f"            logUnknownOnce(PROTOCOL_770, \"{jp}\", id769);")
        out.append(f"            return {fb770};")
        out.append("        }")
        out.append(f"        return {prefix}_770_MAP[id769];")
        out.append("    }")
        out.append("")
    out.append("    /**")
    out.append("     * 770のアイテムID → 769のアイテムID（サーバー行きスロット用）。")
    out.append("     * 770にしか無いアイテム（茂み・ホタルブッシュ・枯れ草・ワイルドフラワー・")
    out.append("     * リーフリッター・サボテンの花・テストブロック類・青い卵・茶色い卵11件）は 769の石へ。")
    out.append("     * 範囲外はそのまま返す。")
    out.append("     */")
    out.append("    public static int mapItemReverse770(int id770) {")
    out.append("        if (id770 < 0 || id770 >= ITEM_REVERSE_770_MAP.length) {")
    out.append("            logUnknownOnce(PROTOCOL_770, \"アイテム（逆変換）\", id770);")
    out.append("            return id770;")
    out.append("        }")
    out.append("        int mapped = ITEM_REVERSE_770_MAP[id770];")
    out.append("        return mapped < 0 ? id770 : mapped;")
    out.append("    }")
    out.append("")
    out.append("    /**")
    out.append("     * 769のエフェクトID → 770のエフェクトID。769も770も0ベース")
    out.append("     * （Speed は両方 0）で 0〜38 は同一のため恒等写像。")
    out.append("     * 範囲外は Speed（0）。")
    out.append("     */")
    out.append("    public static int mapEffect769To770(int id769) {")
    out.append("        if (id769 < 0 || id769 > MAX_EFFECT_769) {")
    out.append("            logUnknownOnce(PROTOCOL_770, \"エフェクト\", id769);")
    out.append("            return SPEED_EFFECT_770;")
    out.append("        }")
    out.append("        return id769;")
    out.append("    }")
    out.append("")
    out.append("    /**")
    out.append("     * 769のブロックエンティティ種別ID → 770の種別ID。")
    out.append("     * 769は45件、770は47件。0〜44 は並びが同一のため恒等写像。")
    out.append("     * 範囲外の種別は NBT の中身が種別専用のため安全に代替できず")
    out.append("     * {@link #DROP}。")
    out.append("     */")
    out.append("    public static int mapBlockEntityType769To770(int id769) {")
    out.append("        if (id769 < 0 || id769 > BLOCK_ENTITY_VAULT_769) {")
    out.append("            logUnknownOnce(PROTOCOL_770, \"ブロックエンティティ種別\", id769);")
    out.append("            return DROP;")
    out.append("        }")
    out.append("        return id769;")
    out.append("    }")
    out.append("")
    out.append("    /**")
    out.append("     * 音の名前（直書き・Stop Sound 用）を770向けに変換する。")
    out.append("     * {@code minecraft:} の有無はそのまま保つ。770で削除された")
    out.append("     * entity.wolf.howl は entity.wolf.ambient にする。")
    out.append("     */")
    out.append("    public static String mapSoundName769To770(String name) {")
    out.append("        return mapSoundNameComposed(name, SOUND_NAME_FALLBACK_769_TO_770_MAP);")
    out.append("    }")
    out.append("")
    # 属性とエンチャント。
    out.append("    /** 769の属性ID → 770の属性ID。並びは同一。範囲外は {@link #DROP}。 */")
    out.append("    public static int mapAttribute769To770(int id769) {")
    out.append("        if (id769 < 0 || id769 > MAX_ATTRIBUTE_769) {")
    out.append("            logUnknownOnce(PROTOCOL_770, \"属性\", id769);")
    out.append("            return DROP;")
    out.append("        }")
    out.append("        return ATTRIBUTE_770_MAP[id769];")
    out.append("    }")
    out.append("")
    out.append("    /** 770の属性ID → 769の属性ID。並びは同一。範囲外は {@link #DROP}。 */")
    out.append("    public static int mapAttribute770To769(int id770) {")
    out.append("        if (id770 < 0 || id770 > MAX_ATTRIBUTE_770) {")
    out.append("            logUnknownOnce(PROTOCOL_770, \"属性\", id770);")
    out.append("            return DROP;")
    out.append("        }")
    out.append("        return ATTRIBUTE_REVERSE_770_MAP[id770];")
    out.append("    }")
    out.append("")
    out.append("    /** 770の属性ID → 属性の名前（Update Attributes の 769向け変換用）。 */")
    out.append("    public static String attributeName770(int id770) {")
    out.append("        if (id770 < 0 || id770 >= ATTRIBUTE_770_NAMES.length) {")
    out.append("            return null;")
    out.append("        }")
    out.append("        return ATTRIBUTE_770_NAMES[id770];")
    out.append("    }")
    out.append("")
    out.append("    /**")
    out.append("     * 属性の名前 → 770の属性ID（765／763／764 は属性が名前形式のため、")
    out.append("     * 770向けに変換するときに使う）。無い名前は {@link #DROP}。")
    out.append("     */")
    out.append("    public static int attributeId770(String name) {")
    out.append("        if (name == null) {")
    out.append("            return DROP;")
    out.append("        }")
    out.append("        String plain = stripMinecraftPrefix(name);")
    out.append("        for (int i = 0; i < ATTRIBUTE_770_NAMES.length; i++) {")
    out.append("            if (ATTRIBUTE_770_NAMES[i].equals(plain)) {")
    out.append("                return i;")
    out.append("            }")
    out.append("        }")
    out.append("        return DROP;")
    out.append("    }")
    out.append("")
    out.append("    /** 769のエンチャントID → 770のエンチャントID。並びは同一。範囲外は {@link #DROP}。 */")
    out.append("    public static int mapEnchantment769To770(int id769) {")
    out.append("        if (id769 < 0 || id769 > MAX_ENCHANTMENT_769) {")
    out.append("            logUnknownOnce(PROTOCOL_770, \"エンチャント\", id769);")
    out.append("            return DROP;")
    out.append("        }")
    out.append("        return ENCHANTMENT_770_MAP[id769];")
    out.append("    }")
    out.append("")
    out.append("    /** 770のエンチャントID → 769のエンチャントID。並びは同一。範囲外は {@link #DROP}。 */")
    out.append("    public static int mapEnchantment770To769(int id770) {")
    out.append("        if (id770 < 0 || id770 > MAX_ENCHANTMENT_770) {")
    out.append("            logUnknownOnce(PROTOCOL_770, \"エンチャント\", id770);")
    out.append("            return DROP;")
    out.append("        }")
    out.append("        return ENCHANTMENT_REVERSE_770_MAP[id770];")
    out.append("    }")
    out.append("")
    out.append("    /**")
    out.append("     * 767のエンチャントID → 770のエンチャントID。767→769 の表を")
    out.append("     * 経由して 769→770 の表を引く。範囲外は {@link #DROP}。")
    out.append("     */")
    out.append("    public static int mapEnchantment767To770(int id767) {")
    out.append("        int id769 = mapEnchantment767To769(id767);")
    out.append("        return id769 == DROP ? DROP : mapEnchantment769To770(id769);")
    out.append("    }")
    out.append("")
    out.append("    /**")
    out.append("     * 770のエンチャントID → 767のエンチャントID。770→769 の逆変換の")
    out.append("     * 結果を 769→767 の逆変換で戻す。範囲外は {@link #DROP}。")
    out.append("     */")
    out.append("    public static int mapEnchantment770To767(int id770) {")
    out.append("        int id769 = mapEnchantment770To769(id770);")
    out.append("        return id769 == DROP ? DROP : mapEnchantment769To767(id769);")
    out.append("    }")
    out.append("")
    out.append("    /**")
    out.append("     * 766のエンチャントID → 770のエンチャントID。766→767 の変換")
    out.append("     * （{@link #mapEnchantment766To767}）を経由して 767→770 の")
    out.append("     * 表を引く。範囲外は {@link #DROP}。")
    out.append("     */")
    out.append("    public static int mapEnchantment766To770(int id766) {")
    out.append("        int id767 = mapEnchantment766To767(id766);")
    out.append("        return id767 == DROP ? DROP : mapEnchantment767To770(id767);")
    out.append("    }")
    out.append("")
    out.append("    /**")
    out.append("     * 770のエンチャントID → 766のエンチャントID。770→767 の逆変換の")
    out.append("     * 結果を 767→766 の逆変換で戻す。範囲外は {@link #DROP}。")
    out.append("     */")
    out.append("    public static int mapEnchantment770To766(int id770) {")
    out.append("        int id767 = mapEnchantment770To767(id770);")
    out.append("        return id767 == DROP ? DROP : mapEnchantment767To766(id767);")
    out.append("    }")
    out.append("")
    out.append("    /**")
    out.append("     * 765のエンチャントID → 770のエンチャントID。765→767 の変換")
    out.append("     * （{@link #mapEnchantment765To767}）を経由して 767→770 の")
    out.append("     * 表を引く。範囲外は {@link #DROP}。")
    out.append("     * 763／764 のエンチャントの並びは 765 と同一のため、")
    out.append("     * 763／764 向けも同じ変換を使う。")
    out.append("     */")
    out.append("    public static int mapEnchantment765To770(int id765) {")
    out.append("        int id767 = mapEnchantment765To767(id765);")
    out.append("        return id767 == DROP ? DROP : mapEnchantment767To770(id767);")
    out.append("    }")
    out.append("")
    out.append("    /** 763のエンチャントID → 770のエンチャントID（765と同じ並び）。 */")
    out.append("    public static int mapEnchantment763To770(int id763) {")
    out.append("        return mapEnchantment765To770(id763);")
    out.append("    }")
    out.append("")
    out.append("    /** 764のエンチャントID → 770のエンチャントID（765と同じ並び）。 */")
    out.append("    public static int mapEnchantment764To770(int id764) {")
    out.append("        return mapEnchantment765To770(id764);")
    out.append("    }")
    out.append("")
    out.append("    /**")
    out.append("     * 770のエンチャントID → 765のエンチャントID。770→767 の逆変換の")
    out.append("     * 結果を 767→765 の変換（{@link #mapEnchantment767To765}）で")
    out.append("     * 戻す。density／breach／wind_burst は 765に無いため")
    out.append("     * {@link #DROP}。範囲外も {@link #DROP}。")
    out.append("     */")
    out.append("    public static int mapEnchantment770To765(int id770) {")
    out.append("        int id767 = mapEnchantment770To767(id770);")
    out.append("        return id767 == DROP ? DROP : mapEnchantment767To765(id767);")
    out.append("    }")
    out.append("")
    out.append("    /** 770のエンチャントID → 763のエンチャントID（765と同じ並び）。 */")
    out.append("    public static int mapEnchantment770To763(int id770) {")
    out.append("        return mapEnchantment770To765(id770);")
    out.append("    }")
    out.append("")
    out.append("    /** 770のエンチャントID → 764のエンチャントID（765と同じ並び）。 */")
    out.append("    public static int mapEnchantment770To764(int id770) {")
    out.append("        return mapEnchantment770To765(id770);")
    out.append("    }")
    return "\n".join(out)


def up_accessors():
    out = ["    // ------------------------------------------------------------------",
           "    // 逆向き（770→769）。上の *_770 表を反転して作った表を使う。",
           "    // アイテムだけは生成スクリプトが名前から作った既存の逆変換表",
           "    // （ITEM_REVERSE_770_MAP）をそのまま使う（反転した表と完全一致",
           "    // することはテストで確認している）。属性とエンチャントの逆向きは",
           "    // mapAttribute770To769／mapEnchantment770To769 を参照。",
           "    // ------------------------------------------------------------------",
           ""]
    for kind in KINDS:
        name, prefix, jp = kind[0], kind[1], kind[2]
        # 逆向きの代替先は「769 側」の定数。kind[19] は 767 側のため
        # そのまま使うと範囲外の ID が 767 の代替先（769 では別な音・別な
        # 種類になっている）になってしまう。_767 → _769 に直して使う。
        fb769 = kind[19].replace("_767", "_769")
        if prefix == "ITEM":
            out.append("    /**")
            out.append("     * 770のアイテムID → 769のアイテムID。")
            out.append("     * 770にしか無いアイテム（茂み・ホタルブッシュ・枯れ草・ワイルドフラワー・")
            out.append("     * リーフリッター・サボテンの花・テストブロック類・青い卵・茶色い卵11件）は石へ。範囲外は石（1）。")
            out.append("     */")
            out.append("    public static int mapItemUp770(int id770) {")
            out.append("        if (id770 < 0 || id770 >= ITEM_REVERSE_770_MAP.length) {")
            out.append("            logUnknownUp770Once(\"アイテム\", id770);")
            out.append("            return STONE_ITEM_769;")
            out.append("        }")
            out.append("        int mapped = ITEM_REVERSE_770_MAP[id770];")
            out.append("        return mapped < 0 ? STONE_ITEM_769 : mapped;")
            out.append("    }")
            out.append("")
            continue
        out.append(f"    /** 770の{jp}ID → 769の{jp}ID。範囲外は{fb769} に相当する代替。 */")
        out.append(f"    public static int map{name}Up770(int id770) {{")
        out.append(f"        if (id770 < 0 || id770 > MAX_{prefix}_770) {{")
        out.append(f"            logUnknownUp770Once(\"{jp}\", id770);")
        out.append(f"            return {fb769};")
        out.append("        }")
        out.append(f"        return {prefix}_UP_770_MAP[id770];")
        out.append("    }")
        out.append("")
    out.append("    /**")
    out.append("     * 770のエフェクトID → 769のエフェクトID。0〜38 は同一のため恒等写像。")
    out.append("     * 範囲外は Speed（0）。")
    out.append("     */")
    out.append("    public static int mapEffectUp770(int id770) {")
    out.append("        if (id770 < 0 || id770 > MAX_EFFECT_770) {")
    out.append("            logUnknownUp770Once(\"エフェクト\", id770);")
    out.append("            return SPEED_EFFECT_769;")
    out.append("        }")
    out.append("        return id770;")
    out.append("    }")
    out.append("")
    out.append("    /**")
    out.append("     * 770のブロックエンティティ種別ID → 769の種別ID。")
    out.append("     * 769は45件、770は47件。0〜44 は並びが同一のため恒等写像。")
    out.append("     * 770で増えた test_block（45）・test_instance_block（46）は")
    out.append("     * 769に無いため {@link #DROP}。")
    out.append("     * 範囲外の種別は NBT の中身が種別専用のため安全に代替できず")
    out.append("     * {@link #DROP}。")
    out.append("     */")
    out.append("    public static int mapBlockEntityTypeUp770(int id770) {")
    out.append("        if (id770 < 0 || id770 > BLOCK_ENTITY_VAULT_770) {")
    out.append("            logUnknownUp770Once(\"ブロックエンティティ種別\", id770);")
    out.append("            return DROP;")
    out.append("        }")
    out.append("        return id770;")
    out.append("    }")
    out.append("")
    out.append("    /**")
    out.append("     * 音の名前を769向けに変換する。770の音の名前のうち 769に無いものは")
    out.append("     * 代替の音の名前にする（数値の反転表と一致させる）。770の新音")
    out.append("     * （オオカミの変種の音36件・鉄の音5件・リーフリッター5件・サボテンの花2件・")
    out.append("     * 砂の音2件・デッドブッシュ／ホタルブッシュの音2件の52件）は石の破壊音へ。")
    out.append("     * {@code null} は石の破壊音にする。")
    out.append("     */")
    out.append("    public static String mapSoundNameUp770(String name) {")
    out.append("        return mapSoundNameComposed(name, SOUND_NAME_FALLBACK_770_TO_769_MAP);")
    out.append("    }")
    return "\n".join(out)
def composed_accessors():
    out = ["    // ------------------------------------------------------------------",
           "    // 離れた組み合わせの合成表（763↔770、764↔770、765↔770、766↔770、",
           "    // 767↔770、768↔770）。上の *_763_TO_770／*_770_TO_763／",
           "    // *_764_TO_770／*_770_TO_764／*_765_TO_770／*_770_TO_765／",
           "    // *_766_TO_770／*_770_TO_766／*_767_TO_770／*_770_TO_767／",
           "    // *_768_TO_770／*_770_TO_768 表（合成済みの1つの表）",
           "    // を直接引く。二段変換はしない。エフェクトとブロックエンティティ",
           "    // 種別は表を作らず、数値の規則と同一の並びで扱う。",
           "    // ------------------------------------------------------------------",
           ""]
    pairs = [
        ("763", 15, "763→770", "770→763"),
        ("764", 16, "764→770", "770→764"),
        ("765", 17, "765→770", "770→765"),
        ("766", 18, "766→770", "770→766"),
        ("767", 19, "767→770", "770→767"),
        ("768", 20, "768→770", "770→768"),
    ]
    for src, fb_src_idx, up_label, down_label in pairs:
        for kind in KINDS:
            name, prefix, jp = kind[0], kind[1], kind[2]
            fb_src = kind[fb_src_idx]
            fb_770 = kind[21]
            # src → 770
            out.append(f"    /** {src}の{jp}ID → 770の{jp}ID。範囲外は{fb_770} に相当する代替。 */")
            out.append(f"    public static int map{name}{src}To770(int id{src}) {{")
            out.append(f"        if (id{src} < 0 || id{src} >= {prefix}_{src}_TO_770_MAP.length) {{")
            out.append(f"            logUnknownComposedOnce(\"{up_label}\", \"{jp}\", id{src});")
            out.append(f"            return {fb_770};")
            out.append("        }")
            out.append(f"        return {prefix}_{src}_TO_770_MAP[id{src}];")
            out.append("    }")
            out.append("")
            # 770 → src
            out.append(f"    /** 770の{jp}ID → {src}の{jp}ID。範囲外は{fb_src} に相当する代替。 */")
            out.append(f"    public static int map{name}770To{src}(int id770) {{")
            out.append(f"        if (id770 < 0 || id770 >= {prefix}_770_TO_{src}_MAP.length) {{")
            out.append(f"            logUnknownComposedOnce(\"{down_label}\", \"{jp}\", id770);")
            out.append(f"            return {fb_src};")
            out.append("        }")
            out.append(f"        return {prefix}_770_TO_{src}_MAP[id770];")
            out.append("    }")
            out.append("")
        # エフェクトとブロックエンティティ種別と音の名前。
        if src == "763":
            out.append("    /**")
            out.append("     * 763のエフェクトID → 770のエフェクトID。763は1ベース")
            out.append("     * （Speed=1）のため 1 引く。範囲外は Speed（0）。")
            out.append("     */")
            out.append("    public static int mapEffect763To770(int id763) {")
            out.append("        if (id763 < 1 || id763 > MAX_EFFECT_765 + 1) {")
            out.append("            logUnknownComposedOnce(\"763→770\", \"エフェクト\", id763);")
            out.append("            return SPEED_EFFECT_770;")
            out.append("        }")
            out.append("        return id763 - 1;")
            out.append("    }")
            out.append("")
            out.append("    /**")
            out.append("     * 770のエフェクトID → 763のエフェクトID。763は1ベース")
            out.append("     * （Speed=1）のため 1 足す。770で増えた 33〜38 は")
            out.append("     * 763に無いため Speed（1）へ。範囲外も Speed（1）。")
            out.append("     */")
            out.append("    public static int mapEffect770To763(int id770) {")
            out.append("        if (id770 < 0 || id770 > MAX_EFFECT_770) {")
            out.append("            logUnknownComposedOnce(\"770→763\", \"エフェクト\", id770);")
            out.append("            return SPEED_EFFECT_763;")
            out.append("        }")
            out.append("        return id770 <= MAX_EFFECT_765 ? id770 + 1 : SPEED_EFFECT_763;")
            out.append("    }")
            out.append("")
            out.append("    /**")
            out.append("     * 763のブロックエンティティ種別ID → 770の種別ID。")
            out.append("     * 763の種別は 0〜9 で770と同じ並びのためそのまま。")
            out.append("     * creaking_heart（10）の挿入で piston 以降（10〜40）は +1 ずれる。")
            out.append("     * 763に無い番号（41以上）は770側の種別に対応付けられないため")
            out.append("     * {@link #DROP}。")
            out.append("     */")
            out.append("    public static int mapBlockEntityType763To770(int id763) {")
            out.append("        if (id763 < 0 || id763 > BLOCK_ENTITY_TRIAL_SPAWNER_765 - 2) {")
            out.append("            logUnknownComposedOnce(\"763→770\", \"ブロックエンティティ種別\", id763);")
            out.append("            return DROP;")
            out.append("        }")
            out.append("        return id763 < 10 ? id763 : id763 + 1;")
            out.append("    }")
            out.append("")
            out.append("    /**")
            out.append("     * 770のブロックエンティティ種別ID → 763の種別ID。")
            out.append("     * 770の種別は 0〜9 で763と同じ並びのためそのまま。")
            out.append("     * creaking_heart（10）は763に無いため {@link #DROP}。")
            out.append("     * piston 以降（11〜41）は -1 ずれる。crafter（42）・")
            out.append("     * trial_spawner（43）・vault（44）・test_block（45）・test_instance_block（46）は763に無いため {@link #DROP}。")
            out.append("     */")
            out.append("    public static int mapBlockEntityType770To763(int id770) {")
            out.append("        if (id770 < 0 || id770 > BLOCK_ENTITY_TRIAL_SPAWNER_770 - 2) {")
            out.append("            logUnknownComposedOnce(\"770→763\", \"ブロックエンティティ種別\", id770);")
            out.append("            return DROP;")
            out.append("        }")
            out.append("        if (id770 == BLOCK_ENTITY_CREAKING_HEART_770) {")
            out.append("            return DROP;")
            out.append("        }")
            out.append("        return id770 < 10 ? id770 : id770 - 1;")
            out.append("    }")
            out.append("")
            out.append("    /**")
            out.append("     * 音の名前を770向けに変換する。763の音の名前のうち 768で削除された")
            out.append("     * ヤギの角笛の音2件と 770で削除された entity.wolf.howl は")
            out.append("     * 代替の名前にする。{@code null} は石の破壊音にする。")
            out.append("     */")
            out.append("    public static String mapSoundName763To770(String name) {")
            out.append("        return mapSoundNameComposed(")
            out.append("                mapSoundNameComposed(name, SOUND_NAME_FALLBACK_768_MAP),")
            out.append("                SOUND_NAME_FALLBACK_770_MAP);")
            out.append("    }")
            out.append("")
            out.append("    /**")
            out.append("     * 音の名前を763向けに変換する。770の音の名前のうち 763に無いものは")
            out.append("     * 代替の音の名前にする（数値の合成表と一致させる）。")
            out.append("     * {@code null} は石の破壊音にする。")
            out.append("     */")
            out.append("    public static String mapSoundName770To763(String name) {")
            out.append("        return mapSoundNameComposed(name, SOUND_NAME_FALLBACK_770_TO_763_MAP);")
            out.append("    }")
        elif src == "764":
            out.append("    /**")
            out.append("     * 764のエフェクトID → 770のエフェクトID。764も770も0ベース")
            out.append("     * （Speed は両方 0）で 0〜32 は同一のため恒等写像。")
            out.append("     * 範囲外は Speed（0）。")
            out.append("     */")
            out.append("    public static int mapEffect764To770(int id764) {")
            out.append("        if (id764 < 0 || id764 > MAX_EFFECT_764) {")
            out.append("            logUnknownComposedOnce(\"764→770\", \"エフェクト\", id764);")
            out.append("            return SPEED_EFFECT_770;")
            out.append("        }")
            out.append("        return id764;")
            out.append("    }")
            out.append("")
            out.append("    /**")
            out.append("     * 770のエフェクトID → 764のエフェクトID。0〜32 は同一のため恒等写像。")
            out.append("     * 770で増えた 33〜38 は 764に無いため Speed（0）へ。")
            out.append("     * 範囲外も Speed（0）。")
            out.append("     */")
            out.append("    public static int mapEffect770To764(int id770) {")
            out.append("        if (id770 < 0 || id770 > MAX_EFFECT_770) {")
            out.append("            logUnknownComposedOnce(\"770→764\", \"エフェクト\", id770);")
            out.append("            return SPEED_EFFECT_764;")
            out.append("        }")
            out.append("        return id770 <= MAX_EFFECT_764 ? id770 : SPEED_EFFECT_764;")
            out.append("    }")
            out.append("")
            out.append("    /**")
            out.append("     * 764のブロックエンティティ種別ID → 770の種別ID。")
            out.append("     * 764の種別は 0〜9 で770と同じ並びのためそのまま。")
            out.append("     * creaking_heart（10）の挿入で piston 以降（10〜40）は +1 ずれる。")
            out.append("     * 764に無い番号（41以上）は770側の種別に対応付けられないため")
            out.append("     * {@link #DROP}。")
            out.append("     */")
            out.append("    public static int mapBlockEntityType764To770(int id764) {")
            out.append("        if (id764 < 0 || id764 > BLOCK_ENTITY_TRIAL_SPAWNER_765 - 2) {")
            out.append("            logUnknownComposedOnce(\"764→770\", \"ブロックエンティティ種別\", id764);")
            out.append("            return DROP;")
            out.append("        }")
            out.append("        return id764 < 10 ? id764 : id764 + 1;")
            out.append("    }")
            out.append("")
            out.append("    /**")
            out.append("     * 770のブロックエンティティ種別ID → 764の種別ID。")
            out.append("     * 770の種別は 0〜9 で764と同じ並びのためそのまま。")
            out.append("     * creaking_heart（10）は764に無いため {@link #DROP}。")
            out.append("     * piston 以降（11〜41）は -1 ずれる。crafter（42）・")
            out.append("     * trial_spawner（43）・vault（44）・test_block（45）・test_instance_block（46）は764に無いため {@link #DROP}。")
            out.append("     */")
            out.append("    public static int mapBlockEntityType770To764(int id770) {")
            out.append("        if (id770 < 0 || id770 > BLOCK_ENTITY_TRIAL_SPAWNER_770 - 2) {")
            out.append("            logUnknownComposedOnce(\"770→764\", \"ブロックエンティティ種別\", id770);")
            out.append("            return DROP;")
            out.append("        }")
            out.append("        if (id770 == BLOCK_ENTITY_CREAKING_HEART_770) {")
            out.append("            return DROP;")
            out.append("        }")
            out.append("        return id770 < 10 ? id770 : id770 - 1;")
            out.append("    }")
            out.append("")
            out.append("    /**")
            out.append("     * 音の名前を770向けに変換する。764の音の名前のうち 768で削除された")
            out.append("     * ヤギの角笛の音2件と 770で削除された entity.wolf.howl は")
            out.append("     * 代替の名前にする。{@code null} は石の破壊音にする。")
            out.append("     */")
            out.append("    public static String mapSoundName764To770(String name) {")
            out.append("        return mapSoundNameComposed(")
            out.append("                mapSoundNameComposed(name, SOUND_NAME_FALLBACK_768_MAP),")
            out.append("                SOUND_NAME_FALLBACK_770_MAP);")
            out.append("    }")
            out.append("")
            out.append("    /**")
            out.append("     * 音の名前を764向けに変換する。770の音の名前のうち 764に無いものは")
            out.append("     * 代替の音の名前にする（数値の合成表と一致させる）。")
            out.append("     * {@code null} は石の破壊音にする。")
            out.append("     */")
            out.append("    public static String mapSoundName770To764(String name) {")
            out.append("        return mapSoundNameComposed(name, SOUND_NAME_FALLBACK_770_TO_764_MAP);")
            out.append("    }")
        elif src == "765":
            out.append("    /**")
            out.append("     * 765のエフェクトID → 770のエフェクトID。765も770も0ベース")
            out.append("     * （Speed は両方 0）で 0〜32 は同一のため恒等写像。")
            out.append("     * 範囲外は Speed（0）。")
            out.append("     */")
            out.append("    public static int mapEffect765To770(int id765) {")
            out.append("        if (id765 < 0 || id765 > MAX_EFFECT_765) {")
            out.append("            logUnknownComposedOnce(\"765→770\", \"エフェクト\", id765);")
            out.append("            return SPEED_EFFECT_770;")
            out.append("        }")
            out.append("        return id765;")
            out.append("    }")
            out.append("")
            out.append("    /**")
            out.append("     * 770のエフェクトID → 765のエフェクトID。0〜32 は同一のため恒等写像。")
            out.append("     * 770で増えた 33〜38 は 765に無いため Speed（0）へ。")
            out.append("     * 範囲外も Speed（0）。")
            out.append("     */")
            out.append("    public static int mapEffect770To765(int id770) {")
            out.append("        if (id770 < 0 || id770 > MAX_EFFECT_770) {")
            out.append("            logUnknownComposedOnce(\"770→765\", \"エフェクト\", id770);")
            out.append("            return SPEED_EFFECT_765;")
            out.append("        }")
            out.append("        return id770 <= MAX_EFFECT_765 ? id770 : SPEED_EFFECT_765;")
            out.append("    }")
            out.append("")
            out.append("    /**")
            out.append("     * 765のブロックエンティティ種別ID → 770の種別ID。")
            out.append("     * crafter（41）と trial_spawner（42）は770にもあるが、")
            out.append("     * creaking_heart（10）の挿入で +1 ずれる。")
            out.append("     * 範囲外の種別は NBT の中身が種別専用のため安全に代替できず")
            out.append("     * {@link #DROP}。")
            out.append("     */")
            out.append("    public static int mapBlockEntityType765To770(int id765) {")
            out.append("        if (id765 < 0 || id765 > BLOCK_ENTITY_TRIAL_SPAWNER_765) {")
            out.append("            logUnknownComposedOnce(\"765→770\", \"ブロックエンティティ種別\", id765);")
            out.append("            return DROP;")
            out.append("        }")
            out.append("        return id765 < 10 ? id765 : id765 + 1;")
            out.append("    }")
            out.append("")
            out.append("    /**")
            out.append("     * 770のブロックエンティティ種別ID → 765の種別ID。")
            out.append("     * creaking_heart（10）は765に無いため {@link #DROP}。")
            out.append("     * piston 以降（11〜43）は -1 ずれる。vault（44）・test_block（45）・")
            out.append("     * test_instance_block（46）は765に無いため {@link #DROP}。")
            out.append("     */")
            out.append("    public static int mapBlockEntityType770To765(int id770) {")
            out.append("        if (id770 < 0 || id770 > BLOCK_ENTITY_TRIAL_SPAWNER_770) {")
            out.append("            logUnknownComposedOnce(\"770→765\", \"ブロックエンティティ種別\", id770);")
            out.append("            return DROP;")
            out.append("        }")
            out.append("        if (id770 == BLOCK_ENTITY_CREAKING_HEART_770) {")
            out.append("            return DROP;")
            out.append("        }")
            out.append("        return id770 < 10 ? id770 : id770 - 1;")
            out.append("    }")
            out.append("")
            out.append("    /**")
            out.append("     * 音の名前を770向けに変換する。765の音の名前のうち 766で改名された")
            out.append("     * entity.generic.wind_burst と 767で改名された trial_spawner の音2件は")
            out.append("     * 新しい名前にし、768で削除されたヤギの角笛の音2件と 770で削除された")
            out.append("     * entity.wolf.howl は代替の名前にする。")
            out.append("     * {@code null} は石の破壊音にする。")
            out.append("     */")
            out.append("    public static String mapSoundName765To770(String name) {")
            out.append("        return mapSoundNameComposed(")
            out.append("                mapSoundNameComposed(")
            out.append("                        mapSoundNameComposed(")
            out.append("                                mapSoundNameComposed(name, SOUND_NAME_FALLBACK_766_MAP),")
            out.append("                                SOUND_NAME_FALLBACK_767_MAP),")
            out.append("                        SOUND_NAME_FALLBACK_768_MAP),")
            out.append("                SOUND_NAME_FALLBACK_770_MAP);")
            out.append("    }")
            out.append("")
            out.append("    /**")
            out.append("     * 音の名前を765向けに変換する。770の音の名前のうち 765に無いものは")
            out.append("     * 代替の音の名前にする（数値の合成表と一致させる）。")
            out.append("     * {@code null} は石の破壊音にする。")
            out.append("     */")
            out.append("    public static String mapSoundName770To765(String name) {")
            out.append("        return mapSoundNameComposed(name, SOUND_NAME_FALLBACK_770_TO_765_MAP);")
            out.append("    }")
        elif src == "766":
            out.append("    /**")
            out.append("     * 766のエフェクトID → 770のエフェクトID。766も770も0ベース")
            out.append("     * （Speed は両方 0）で 0〜38 は同一のため恒等写像。")
            out.append("     * 範囲外は Speed（0）。")
            out.append("     */")
            out.append("    public static int mapEffect766To770(int id766) {")
            out.append("        if (id766 < 0 || id766 > MAX_EFFECT_766) {")
            out.append("            logUnknownComposedOnce(\"766→770\", \"エフェクト\", id766);")
            out.append("            return SPEED_EFFECT_770;")
            out.append("        }")
            out.append("        return id766;")
            out.append("    }")
            out.append("")
            out.append("    /**")
            out.append("     * 770のエフェクトID → 766のエフェクトID。0〜38 は同一のため恒等写像。")
            out.append("     * 範囲外は Speed（0）。")
            out.append("     */")
            out.append("    public static int mapEffect770To766(int id770) {")
            out.append("        if (id770 < 0 || id770 > MAX_EFFECT_770) {")
            out.append("            logUnknownComposedOnce(\"770→766\", \"エフェクト\", id770);")
            out.append("            return SPEED_EFFECT_766;")
            out.append("        }")
            out.append("        return id770;")
            out.append("    }")
            out.append("")
            out.append("    /**")
            out.append("     * 766のブロックエンティティ種別ID → 770の種別ID。")
            out.append("     * vault（43）は770にもあるが、creaking_heart（10）の挿入で")
            out.append("     * piston 以降（10〜43）は +1 ずれる。")
            out.append("     * 範囲外の種別は NBT の中身が種別専用のため安全に代替できず")
            out.append("     * {@link #DROP}。")
            out.append("     */")
            out.append("    public static int mapBlockEntityType766To770(int id766) {")
            out.append("        if (id766 < 0 || id766 > BLOCK_ENTITY_VAULT_766) {")
            out.append("            logUnknownComposedOnce(\"766→770\", \"ブロックエンティティ種別\", id766);")
            out.append("            return DROP;")
            out.append("        }")
            out.append("        return id766 < 10 ? id766 : id766 + 1;")
            out.append("    }")
            out.append("")
            out.append("    /**")
            out.append("     * 770のブロックエンティティ種別ID → 766の種別ID。")
            out.append("     * creaking_heart（10）は766に無いため {@link #DROP}。")
            out.append("     * piston 以降（11〜44）は -1 ずれる。vault は 770=44 → 766=43。")
            out.append("     * test_block（45）・test_instance_block（46）は766に無いため DROP。")
            out.append("     */")
            out.append("    public static int mapBlockEntityType770To766(int id770) {")
            out.append("        if (id770 < 0 || id770 > BLOCK_ENTITY_VAULT_770) {")
            out.append("            logUnknownComposedOnce(\"770→766\", \"ブロックエンティティ種別\", id770);")
            out.append("            return DROP;")
            out.append("        }")
            out.append("        if (id770 == BLOCK_ENTITY_CREAKING_HEART_770) {")
            out.append("            return DROP;")
            out.append("        }")
            out.append("        return id770 < 10 ? id770 : id770 - 1;")
            out.append("    }")
            out.append("")
            out.append("    /**")
            out.append("     * 音の名前を770向けに変換する。766の音の名前のうち 767で改名された")
            out.append("     * trial_spawner の音2件は新しい名前にし、768で削除されたヤギの角笛の音2件と")
            out.append("     * 770で削除された entity.wolf.howl は代替の名前にする。")
            out.append("     * {@code null} は石の破壊音にする。")
            out.append("     */")
            out.append("    public static String mapSoundName766To770(String name) {")
            out.append("        return mapSoundNameComposed(name, SOUND_NAME_FALLBACK_766_TO_770_MAP);")
            out.append("    }")
            out.append("")
            out.append("    /**")
            out.append("     * 音の名前を766向けに変換する。770の音の名前のうち 766に無いものは")
            out.append("     * 代替の音の名前にする（数値の合成表と一致させる）。")
            out.append("     * {@code null} は石の破壊音にする。")
            out.append("     */")
            out.append("    public static String mapSoundName770To766(String name) {")
            out.append("        return mapSoundNameComposed(name, SOUND_NAME_FALLBACK_770_TO_766_MAP);")
            out.append("    }")
        elif src == "768":
            out.append("    /**")
            out.append("     * 768のエフェクトID → 770のエフェクトID。768も770も0ベース")
            out.append("     * （Speed は両方 0）で 0〜38 は同一のため恒等写像。")
            out.append("     * 範囲外は Speed（0）。")
            out.append("     */")
            out.append("    public static int mapEffect768To770(int id768) {")
            out.append("        if (id768 < 0 || id768 > MAX_EFFECT_768) {")
            out.append("            logUnknownComposedOnce(\"768→770\", \"エフェクト\", id768);")
            out.append("            return SPEED_EFFECT_770;")
            out.append("        }")
            out.append("        return id768;")
            out.append("    }")
            out.append("")
            out.append("    /**")
            out.append("     * 770のエフェクトID → 768のエフェクトID。0〜38 は同一のため恒等写像。")
            out.append("     * 範囲外は Speed（0）。")
            out.append("     */")
            out.append("    public static int mapEffect770To768(int id770) {")
            out.append("        if (id770 < 0 || id770 > MAX_EFFECT_770) {")
            out.append("            logUnknownComposedOnce(\"770→768\", \"エフェクト\", id770);")
            out.append("            return SPEED_EFFECT_768;")
            out.append("        }")
            out.append("        return id770;")
            out.append("    }")
            out.append("")
            out.append("    /**")
            out.append("     * 768のブロックエンティティ種別ID → 770の種別ID。")
            out.append("     * 768の種別は 0〜44 で770と同じ並びのためそのまま。")
            out.append("     * 範囲外の種別は NBT の中身が種別専用のため安全に代替できず")
            out.append("     * {@link #DROP}。")
            out.append("     */")
            out.append("    public static int mapBlockEntityType768To770(int id768) {")
            out.append("        if (id768 < 0 || id768 > BLOCK_ENTITY_VAULT_768) {")
            out.append("            logUnknownComposedOnce(\"768→770\", \"ブロックエンティティ種別\", id768);")
            out.append("            return DROP;")
            out.append("        }")
            out.append("        return id768;")
            out.append("    }")
            out.append("")
            out.append("    /**")
            out.append("     * 770のブロックエンティティ種別ID → 768の種別ID。")
            out.append("     * 0〜44 は並びが同一のためそのまま。test_block（45）・")
            out.append("     * test_instance_block（46）は768に無いため {@link #DROP}。")
            out.append("     */")
            out.append("    public static int mapBlockEntityType770To768(int id770) {")
            out.append("        if (id770 < 0 || id770 > BLOCK_ENTITY_VAULT_770) {")
            out.append("            logUnknownComposedOnce(\"770→768\", \"ブロックエンティティ種別\", id770);")
            out.append("            return DROP;")
            out.append("        }")
            out.append("        return id770;")
            out.append("    }")
            out.append("")
            out.append("    /**")
            out.append("     * 音の名前を770向けに変換する。768の音の名前のうち 770で削除された")
            out.append("     * entity.wolf.howl は代替の名前にする。")
            out.append("     * {@code null} は石の破壊音にする。")
            out.append("     */")
            out.append("    public static String mapSoundName768To770(String name) {")
            out.append("        return mapSoundNameComposed(name, SOUND_NAME_FALLBACK_768_TO_770_MAP);")
            out.append("    }")
            out.append("")
            out.append("    /**")
            out.append("     * 音の名前を768向けに変換する。770の音の名前のうち 768に無いものは")
            out.append("     * 代替の音の名前にする（数値の合成表と一致させる）。")
            out.append("     * {@code null} は石の破壊音にする。")
            out.append("     */")
            out.append("    public static String mapSoundName770To768(String name) {")
            out.append("        return mapSoundNameComposed(name, SOUND_NAME_FALLBACK_770_TO_768_MAP);")
            out.append("    }")
        else:  # 767
            out.append("    /**")
            out.append("     * 767のエフェクトID → 770のエフェクトID。767も770も0ベース")
            out.append("     * （Speed は両方 0）で 0〜38 は同一のため恒等写像。")
            out.append("     * 範囲外は Speed（0）。")
            out.append("     */")
            out.append("    public static int mapEffect767To770(int id767) {")
            out.append("        if (id767 < 0 || id767 > MAX_EFFECT_767) {")
            out.append("            logUnknownComposedOnce(\"767→770\", \"エフェクト\", id767);")
            out.append("            return SPEED_EFFECT_770;")
            out.append("        }")
            out.append("        return id767;")
            out.append("    }")
            out.append("")
            out.append("    /**")
            out.append("     * 770のエフェクトID → 767のエフェクトID。0〜38 は同一のため恒等写像。")
            out.append("     * 範囲外は Speed（0）。")
            out.append("     */")
            out.append("    public static int mapEffect770To767(int id770) {")
            out.append("        if (id770 < 0 || id770 > MAX_EFFECT_770) {")
            out.append("            logUnknownComposedOnce(\"770→767\", \"エフェクト\", id770);")
            out.append("            return SPEED_EFFECT_767;")
            out.append("        }")
            out.append("        return id770;")
            out.append("    }")
            out.append("")
            out.append("    /**")
            out.append("     * 767のブロックエンティティ種別ID → 770の種別ID。")
            out.append("     * vault（43）は770にもあるが、creaking_heart（10）の挿入で")
            out.append("     * piston 以降（10〜43）は +1 ずれる。")
            out.append("     * 範囲外の種別は NBT の中身が種別専用のため安全に代替できず")
            out.append("     * {@link #DROP}。")
            out.append("     */")
            out.append("    public static int mapBlockEntityType767To770(int id767) {")
            out.append("        if (id767 < 0 || id767 > BLOCK_ENTITY_VAULT_766) {")
            out.append("            logUnknownComposedOnce(\"767→770\", \"ブロックエンティティ種別\", id767);")
            out.append("            return DROP;")
            out.append("        }")
            out.append("        return id767 < 10 ? id767 : id767 + 1;")
            out.append("    }")
            out.append("")
            out.append("    /**")
            out.append("     * 770のブロックエンティティ種別ID → 767の種別ID。")
            out.append("     * creaking_heart（10）は767に無いため {@link #DROP}。")
            out.append("     * piston 以降（11〜44）は -1 ずれる。vault は 770=44 → 767=43。")
            out.append("     * test_block（45）・test_instance_block（46）は767に無いため DROP。")
            out.append("     */")
            out.append("    public static int mapBlockEntityType770To767(int id770) {")
            out.append("        if (id770 < 0 || id770 > BLOCK_ENTITY_VAULT_770) {")
            out.append("            logUnknownComposedOnce(\"770→767\", \"ブロックエンティティ種別\", id770);")
            out.append("            return DROP;")
            out.append("        }")
            out.append("        if (id770 == BLOCK_ENTITY_CREAKING_HEART_770) {")
            out.append("            return DROP;")
            out.append("        }")
            out.append("        return id770 < 10 ? id770 : id770 - 1;")
            out.append("    }")
            out.append("")
            out.append("    /**")
            out.append("     * 音の名前を770向けに変換する。767の音の名前のうち 768で削除された")
            out.append("     * ヤギの角笛の音2件と 770で削除された entity.wolf.howl は")
            out.append("     * 代替の名前にする。")
            out.append("     * {@code null} は石の破壊音にする。")
            out.append("     */")
            out.append("    public static String mapSoundName767To770(String name) {")
            out.append("        return mapSoundNameComposed(name, SOUND_NAME_FALLBACK_767_TO_770_MAP);")
            out.append("    }")
            out.append("")
            out.append("    /**")
            out.append("     * 音の名前を767向けに変換する。770の音の名前のうち 767に無いものは")
            out.append("     * 代替の音の名前にする（数値の合成表と一致させる）。")
            out.append("     * {@code null} は石の破壊音にする。")
            out.append("     */")
            out.append("    public static String mapSoundName770To767(String name) {")
            out.append("        return mapSoundNameComposed(name, SOUND_NAME_FALLBACK_770_TO_767_MAP);")
            out.append("    }")
    return "\n".join(out)

# バナー・絵画・装飾パターン・ダメージ種別の 770 向け（769 と同一のものは
# 恒等写像。ダメージ種別も 769 と同一の 49 件）。
DAMAGE_TYPE_770 = """
    /** 770のダメージ種別最大ID（49件。769と同一）。 */
    public static final int MAX_DAMAGE_TYPE_770 = 48;

    /** ダメージ種別ID：769 → 770（769と同一の 49 件で並びも同じため恒等写像）。
     * 実サーバーの Registry Data での確認は統合テストで行う。 */
    private static final int[] DAMAGE_TYPE_770_MAP = {
        0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19,
        20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32, 33, 34, 35, 36,
        37, 38, 39, 40, 41, 42, 43, 44, 45, 46, 47, 48
    };

    /** ダメージ種別ID：770 → 769（769と同一の 49 件で並びも同じため恒等写像）。 */
    private static final int[] DAMAGE_TYPE_REVERSE_770_MAP = {
        0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19,
        20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32, 33, 34, 35, 36,
        37, 38, 39, 40, 41, 42, 43, 44, 45, 46, 47, 48
    };
"""

SMALL_REGISTRY_CONSTANTS = """
    /** 770のバナーパターン最大ID（43件。769と同一と仮定。実バニラの
        Registry Data での確認は統合テストで行う）。 */
    public static final int MAX_BANNER_PATTERN_770 = 42;

    /** 770の絵画バリアント最大ID（50件。769と同一と仮定。実バニラの
        Registry Data での確認は統合テストで行う）。 */
    public static final int MAX_PAINTING_VARIANT_770 = 49;

    /** 770の装飾パターン最大ID（18件。769と同一と仮定。実バニラの
        Registry Data での確認は統合テストで行う）。 */
    public static final int MAX_TRIM_PATTERN_770 = 17;
"""


def small_registry_accessors():
    out = []
    specs = [
        ("BannerPattern", "バナーパターン", "MAX_BANNER_PATTERN_769", "MAX_BANNER_PATTERN_770"),
        ("PaintingVariant", "絵画バリアント", "MAX_PAINTING_VARIANT_769", "MAX_PAINTING_VARIANT_770"),
        ("TrimPattern", "装飾パターン", "MAX_TRIM_PATTERN_769", "MAX_TRIM_PATTERN_770"),
    ]
    for name, jp, max769, max770 in specs:
        out.append(f"    /** 769の{jp}ID → 770の{jp}ID。769と770で同一のため恒等写像。範囲外は {{@link #DROP}}。 */")
        out.append(f"    public static int map{name}769To770(int id769) {{")
        out.append(f"        if (id769 < 0 || id769 > {max769}) {{")
        out.append(f"            logUnknownOnce(PROTOCOL_770, \"{jp}\", id769);")
        out.append("            return DROP;")
        out.append("        }")
        out.append("        return id769;")
        out.append("    }")
        out.append("")
        out.append(f"    /** 770の{jp}ID → 769の{jp}ID。769と770で同一のため恒等写像。範囲外は {{@link #DROP}}。 */")
        out.append(f"    public static int map{name}770To769(int id770) {{")
        out.append(f"        if (id770 < 0 || id770 > {max770}) {{")
        out.append(f"            logUnknownOnce(PROTOCOL_770, \"{jp}\", id770);")
        out.append("            return DROP;")
        out.append("        }")
        out.append("        return id770;")
        out.append("    }")
        out.append("")
        for src in ("763", "764", "765", "766", "767", "768"):
            # 769 のメソッドを経由する（763／764／765／766／767→769 と
            # 769→763／764／765／766／767 は既にある）。
            out.append(f"    /** {src}の{jp}ID → 770の{jp}ID（769経由）。 */")
            out.append(f"    public static int map{name}{src}To770(int id{src}) {{")
            out.append(f"        return map{name}769To770(map{name}{src}To769(id{src}));")
            out.append("    }")
            out.append("")
            out.append(f"    /** 770の{jp}ID → {src}の{jp}ID（769経由）。 */")
            out.append(f"    public static int map{name}770To{src}(int id770) {{")
            out.append(f"        return map{name}769To{src}(map{name}770To769(id770));")
            out.append("    }")
            out.append("")
    return "\n".join(out)


def damage_accessors():
    out = []
    out.append("    /** 769のダメージ種別ID → 770のダメージ種別ID。769と同一のため恒等写像。範囲外は {@link #DROP}。 */")
    out.append("    public static int mapDamageType769To770(int id769) {")
    out.append("        if (id769 < 0 || id769 > MAX_DAMAGE_TYPE_769) {")
    out.append("            logUnknownOnce(PROTOCOL_770, \"ダメージ種別\", id769);")
    out.append("            return DROP;")
    out.append("        }")
    out.append("        return DAMAGE_TYPE_770_MAP[id769];")
    out.append("    }")
    out.append("")
    out.append("    /** 770のダメージ種別ID → 769のダメージ種別ID。769と同一のため恒等写像。範囲外は {@link #DROP}。 */")
    out.append("    public static int mapDamageType770To769(int id770) {")
    out.append("        if (id770 < 0 || id770 > MAX_DAMAGE_TYPE_770) {")
    out.append("            logUnknownOnce(PROTOCOL_770, \"ダメージ種別\", id770);")
    out.append("            return DROP;")
    out.append("        }")
    out.append("        return DAMAGE_TYPE_REVERSE_770_MAP[id770];")
    out.append("    }")
    out.append("")
    for src in ("763", "764", "765", "766", "767", "768"):
        # ダメージ種別は 769 のメソッドを経由する。
        out.append(f"    /** {src}のダメージ種別ID → 770のダメージ種別ID（769経由）。 */")
        out.append(f"    public static int mapDamageType{src}To770(int id{src}) {{")
        out.append(f"        return mapDamageType769To770(mapDamageType{src}To769(id{src}));")
        out.append("    }")
        out.append("")
        out.append(f"    /** 770のダメージ種別ID → {src}のダメージ種別ID（769経由）。 */")
        out.append(f"    public static int mapDamageType770To{src}(int id770) {{")
        out.append(f"        return mapDamageType769To{src}(mapDamageType770To769(id770));")
        out.append("    }")
        out.append("")
    return "\n".join(out)


JAVADOC_770 = """ *
 * <p>770（1.21.5）向けの表も同じ作り方で持つ（{@code *_770}）。
 * 770は769に近く、差分は次の通り（ViaVersion mapping-1.21.5.json と
 * mapping-1.21.4.json、minecraft-data 1.21.5 の突き合わせ）。
 * ブロック状態は新60件・削除12件（creaking_heart のプロパティが
 * active＋natural の2ブールから creaking_heart_state（uprooted／
 * dormant／awake）＋natural に変わった）。ブロックは新9件（茂み・
 * ホタルブッシュ・枯れ草・ワイルドフラワー・リーフリッター・サボテン
 * の花・テストブロック類）。アイテムは新11件（同上＋青い卵・茶色い卵）。
 * エンティティは新1件・削除1件（potion が splash_potion と
 * lingering_potion に分かれた）。音は新52件・削除1件
 * （entity.wolf.howl）。パーティクルは新2件（tinted_leaves が 35 に
 * 挿入され、35 以降が +1 ずれる。firefly が 113 に追加）。メニュー・
 * エフェクト・属性・エンチャント・バイオームは同一（属性は 32件同士で
 * 同一、エンチャントは 42件同士で並びは同一、バイオームは 65件同士で
 * 同一）。ブロックエンティティ種別は 45→47 件（test_block=45、
 * test_instance_block=46 を追加）。ダメージ種別・バナーパターン・
 * 絵画バリアント・装飾パターンは 769 と同一（実バニラの Registry Data
 * での確認は統合テストで行う）。
 * 769→770 の隣接表と 763／764／765／766／767／768→770 の合成表を持ち、
 * 逆向き 770→769 は反転して作る。
"""


def insert_after(java, anchor, block, label):
    if anchor not in java:
        print(f"アンカーが見つからない（{label}）", file=sys.stderr)
        return None
    return java.replace(anchor, anchor + block, 1)


def main():
    if (not TABLES770_FILE.exists() or not TABLES770_COMPOSED_FILE.exists()
            or not all(p.exists() for p in TABLES770_COMPOSED_DOWN_FILES.values())):
        print("RegistryTables770.java / RegistryTables770ComposedUp.java / "
              "RegistryTables770Down*.java が無い。"
              "先に insert-770-tables.py を実行すること", file=sys.stderr)
        return 1
    java = JAVA_FILE.read_text(encoding="utf-8")
    checks = [
        ("PROTOCOL_770", "770 の定数・表・アクセサは既にある"),
        ("BLOCK_STATE_770_MAP", "770 の展開済み表は既にある"),
        ("mapBlockState769To770", "770 のアクセサは既にある"),
    ]
    for marker, msg in checks:
        if marker in java:
            print(msg, file=sys.stderr)
            return 1

    # import static。
    java = insert_after(java,
                        "import static io.detergentmc.protocol.RegistryTables769Down767.*;\n",
                        "import static io.detergentmc.protocol.RegistryTables770.*;\n"
                        "import static io.detergentmc.protocol.RegistryTables770ComposedUp.*;\n"
                        "import static io.detergentmc.protocol.RegistryTables770Down763.*;\n"
                        "import static io.detergentmc.protocol.RegistryTables770Down764.*;\n"
                        "import static io.detergentmc.protocol.RegistryTables770Down765.*;\n"
                        "import static io.detergentmc.protocol.RegistryTables770Down766.*;\n"
                        "import static io.detergentmc.protocol.RegistryTables770Down767.*;\n"
                        "import static io.detergentmc.protocol.RegistryTables770Down768.*;\n",
                        "import static RegistryTables770")
    if java is None:
        return 1

    # クラスの javadoc に 770 の段落を追加（769 の段落の直後）。
    anchor = (" * 768→769 の隣接表と 763／764／765／766／767→769 の合成表を持ち、\n"
              " * 逆向き 769→768 は反転して作る。\n")
    java = insert_after(java, anchor, JAVADOC_770, "javadoc")
    if java is None:
        return 1

    # 定数。
    java = insert_after(java,
                        "    public static final int PROTOCOL_769 = 769;\n",
                        "    public static final int PROTOCOL_770 = 770;\n", "PROTOCOL_770")
    if java is None:
        return 1
    java = insert_after(java,
                        "    public static final int MAX_EFFECT_769 = 38;\n",
                        CONSTANTS_MAX, "MAX_770")
    if java is None:
        return 1
    java = insert_after(java,
                        "    public static final int SPEED_EFFECT_769 = 0;\n",
                        CONSTANTS_FALLBACK, "770 の代替先")
    if java is None:
        return 1
    java = insert_after(java,
                        "    public static final int BLOCK_ENTITY_VAULT_769 = 44;\n",
                        CONSTANTS_BLOCK_ENTITY, "770 のブロックエンティティ種別")
    if java is None:
        return 1

    # 隣接表（769→770）。MENU_769_MAP の直後。
    anchor = ("    private static final int[] MENU_769_MAP =\n"
              "            expand(MENU_769_RANGES, MENU_769_OVERRIDES, MAX_MENU_768 + 1,\n"
              "                    CRAFTING_MENU_769);\n")
    java = insert_after(java, anchor, adjacent_maps() + "\n", "隣接表 770")
    if java is None:
        return 1

    # 合成表。MENU_769_TO_767_MAP の直後。
    anchor = ("    private static final int[] MENU_769_TO_767_MAP =\n"
              "            expand(MENU_769_TO_767_RANGES, MENU_769_TO_767_OVERRIDES,\n"
              "                    25, CRAFTING_MENU_767);\n")
    java = insert_after(java, anchor, "\n" + composed_maps() + "\n", "合成表 770")
    if java is None:
        return 1

    # 反転表（770→769）。MENU_UP_769_MAP の直後。
    anchor = ("    private static final int[] MENU_UP_769_MAP = invert(\n"
              "            MENU_769_RANGES, MENU_769_OVERRIDES,\n"
              "            MAX_MENU_768 + 1, MAX_MENU_769 + 1,\n"
              "            CRAFTING_MENU_768, null);\n")
    java = insert_after(java, anchor, up_maps() + "\n", "反転表 770")
    if java is None:
        return 1

    # 音の名前の表。
    anchor = ("    private static final Map<String, String> SOUND_NAME_FALLBACK_769_TO_766_MAP =\n"
              "            buildSoundNameMap(SOUND_NAME_FALLBACKS_769_TO_766);\n")
    java = insert_after(java, anchor, SOUND_NAME_MAPS, "音の名前の表 770")
    if java is None:
        return 1

    # ログ。
    anchor = ("    private static void logUnknownUp769Once(String kind, int id769) {\n"
              "        if (LOGGED_UNKNOWN.add(\"up769:\" + kind + \":\" + id769)) {\n"
              "            LOG.warning(\"769に無い\" + kind + \"の番号が来たため代替に落とします（769 id=\" + id769 + \"）。\");\n"
              "        }\n"
              "    }\n")
    java = insert_after(java, anchor, LOG_UP_770, "logUnknownUp770Once")
    if java is None:
        return 1

    # アクセサ（a）769→770。mapSoundName767To769 の直後。
    anchor = ("    public static String mapSoundName767To769(String name) {\n"
              "        return mapSoundNameComposed(name, SOUND_NAME_FALLBACK_767_TO_769_MAP);\n"
              "    }\n")
    java = insert_after(java, anchor, "\n" + adjacent_accessors() + "\n", "アクセサ 769→770")
    if java is None:
        return 1

    # アクセサ（b）770→769。mapSoundNameUp769 の直後。
    anchor = ("    public static String mapSoundNameUp769(String name) {\n"
              "        return mapSoundNameComposed(name, SOUND_NAME_FALLBACK_769_TO_768_MAP);\n"
              "    }\n")
    java = insert_after(java, anchor, "\n" + up_accessors() + "\n", "アクセサ 770→769")
    if java is None:
        return 1

    # アクセサ（c）763／764／765／766／767↔770。mapSoundName769To766 の直後。
    anchor = ("    public static String mapSoundName769To766(String name) {\n"
              "        return mapSoundNameComposed(name, SOUND_NAME_FALLBACK_769_TO_766_MAP);\n"
              "    }\n")
    java = insert_after(java, anchor, "\n" + composed_accessors() + "\n", "アクセサ 763/764/765/766/767↔770")
    if java is None:
        return 1

    # バナー・絵画・装飾パターン・ダメージ種別の定数。
    java = insert_after(java,
                        "    /** 769のバナーパターン最大ID（43件。768と同一）。 */\n"
                        "    public static final int MAX_BANNER_PATTERN_769 = 42;\n",
                        SMALL_REGISTRY_CONSTANTS, "バナー等の770定数")
    if java is None:
        return 1
    anchor = ("    private static final int[] DAMAGE_TYPE_REVERSE_769_MAP = {\n"
              "        0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19,\n"
              "        20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32, 33, 34, 35, 36,\n"
              "        37, 38, 39, 40, 41, 42, 43, 44, 45, 46, 47, 48\n"
              "    };\n")
    java = insert_after(java, anchor, DAMAGE_TYPE_770, "ダメージ種別770の表")
    if java is None:
        return 1

    # バナー・絵画・装飾パターン・ダメージ種別のアクセサ。
    anchor = ("    /** 769のバナーパターンID → 766のバナーパターンID（768経由）。 */\n"
              "    public static int mapBannerPattern769To766(int id769) {\n"
              "        return mapBannerPattern768To766(mapBannerPattern769To768(id769));\n"
              "    }\n")
    banner_painting_trim = small_registry_accessors()
    java = insert_after(java, anchor, "\n" + banner_painting_trim + "\n", "バナー等の770アクセサ")
    if java is None:
        return 1
    anchor = ("    /** 769のダメージ種別ID → 766のダメージ種別ID（768経由）。 */\n"
              "    public static int mapDamageType769To766(int id769) {\n"
              "        return mapDamageType768To766(mapDamageType769To768(id769));\n"
              "    }\n")
    java = insert_after(java, anchor, "\n" + damage_accessors() + "\n", "ダメージ種別770のアクセサ")
    if java is None:
        return 1

    # プロトコル番号で切り替える変換（担当2の配線用）に 770 を追加。
    dispatches = [
        ("mapBlockState", "mapBlockState765To769(id765)", "mapBlockState765To770(id765)"),
        ("mapItem", "mapItem765To769(id765)", "mapItem765To770(id765)"),
        ("mapItemReverse", "mapItem769To765(id)", "mapItem770To765(id)"),
        ("mapEntityType", "mapEntityType765To769(id765)", "mapEntityType765To770(id765)"),
        ("mapSound", "mapSound765To769(id765)", "mapSound765To770(id765)"),
        ("mapParticle", "mapParticle765To769(id765)", "mapParticle765To770(id765)"),
        ("mapBlock", "mapBlock765To769(id765)", "mapBlock765To770(id765)"),
        ("mapMenu", "mapMenu765To769(id765)", "mapMenu765To770(id765)"),
        ("mapEffect", "mapEffect765To769(id765)", "mapEffect765To770(id765)"),
        ("mapBlockEntityType", "mapBlockEntityType765To769(id765)", "mapBlockEntityType765To770(id765)"),
        ("mapSoundName", "mapSoundName765To769(name)", "mapSoundName765To770(name)"),
    ]
    for label, old_call, new_call in dispatches:
        if label == "mapItemReverse":
            old = ("        if (clientProtocol == PROTOCOL_769) {\n"
                   "            // 769クライアントのアイテムID → 765サーバーのアイテムID。\n"
                   "            // mapItemReverse769 は 769→768 のため、ここでは合成表を使う。\n"
                   "            return mapItem769To765(id);\n"
                   "        }\n")
            new = (old
                   + "        if (clientProtocol == PROTOCOL_770) {\n"
                   "            // 770クライアントのアイテムID → 765サーバーのアイテムID。\n"
                   "            // mapItemReverse770 は 770→769 のため、ここでは合成表を使う。\n"
                   "            return mapItem770To765(id);\n"
                   "        }\n")
            if old not in java:
                print("ディスパッチのアンカーが見つからない（mapItemReverse）", file=sys.stderr)
                return 1
            java = java.replace(old, new, 1)
            continue
        old = ("        if (clientProtocol == PROTOCOL_769) {\n"
               f"            return {old_call};\n"
               "        }\n")
        if old not in java:
            print(f"ディスパッチのアンカーが見つからない（{label}）", file=sys.stderr)
            return 1
        java = java.replace(old, old
                            + "        if (clientProtocol == PROTOCOL_770) {\n"
                            + f"            return {new_call};\n"
                            + "        }\n", 1)
    java = java.replace(
        "    // 765は素通し、766は766の表、767は767の表、768は768の表、\n"
        "    // 769は769の表、それより古い未知の番号は763の表を使う。\n"
        "    // 767／768／769は765以上だが素通しではない（>765 の分岐より\n"
        "    // 先に 767／768／769 を判定すること）。",
        "    // 765は素通し、766は766の表、767は767の表、768は768の表、\n"
        "    // 769は769の表、770は770の表、それより古い未知の番号は763の表を使う。\n"
        "    // 767／768／769／770は765以上だが素通しではない（>765 の分岐より\n"
        "    // 先に 767／768／769／770 を判定すること）。",
        1)

    JAVA_FILE.write_text(java, encoding="utf-8")
    print("RegistryRemapper.java に 770 の定数・表・アクセサを挿入した")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
