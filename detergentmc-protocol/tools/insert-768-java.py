#!/usr/bin/env python3
"""tools/insert-767-java.py で入れた *_767 表の上に、RegistryRemapper.java
へ 768 対応の定数・展開済み表（*_768_MAP）・反転表（*_UP_768_MAP）・
アクセサを挿入する（1回だけの作業用）。

  - 定数：PROTOCOL_768、MAX_*_768、768 側の代替先。
  - 隣接表（767→768）: *_768_MAP と ITEM_REVERSE_768_MAP と
    ATTRIBUTE_768_MAP／ATTRIBUTE_REVERSE_768_MAP／ENCHANTMENT_768_MAP／
    ENCHANTMENT_REVERSE_768_MAP。
  - 合成表（763↔768、764↔768、765↔768、766↔768）: *_763_TO_768_MAP など56件。
  - 逆向き（768→767）: *_UP_768_MAP（invert()。アイテムは
    ITEM_REVERSE_768_MAP をそのまま使う）。
  - 音の名前の表（String）: SOUND_NAME_FALLBACK_*_768_MAP など8件。
  - アクセサ：767↔768、768→767（上向き）、763／764／765／766↔768。
  - エフェクト：768 は 767 と同一の 39 件（0〜38）のため恒等写像。
    属性は 767 の 31 件 → 768 の 32 件（改名9件＋新1件 tempt_range）、
    エンチャントは 42 件同士で並びは同一（minecraft-data の
    dataPaths.json で 1.21.3 の enchantments は pc/1.21.1 と確認）。
    ブロックエンティティ種別は creaking_heart が index 10 に挿入され、
    piston 以降が +1 ずれる（vault は 767=43 → 768=44）。
    バナー・絵画・装飾パターンは 767 と同一のため恒等写像。
    ダメージ種別は ender_pearl・mace_smash の2件が追加。
"""

import re
import sys
from pathlib import Path

SCRIPT_DIR = Path(__file__).resolve().parent
REPO_ROOT = SCRIPT_DIR.parent.parent
JAVA_FILE = (REPO_ROOT / "detergentmc-protocol" / "src" / "main" / "java"
             / "io" / "detergentmc" / "protocol" / "RegistryRemapper.java")
TABLES768_FILE = (REPO_ROOT / "detergentmc-protocol" / "src" / "main" / "java"
                  / "io" / "detergentmc" / "protocol" / "RegistryTables768.java")
TABLES768_COMPOSED_FILE = (REPO_ROOT / "detergentmc-protocol" / "src" / "main" / "java"
                  / "io" / "detergentmc" / "protocol" / "RegistryTables768ComposedUp.java")
TABLES768_COMPOSED_DOWN_FILES = {
    suffix: (REPO_ROOT / "detergentmc-protocol" / "src" / "main" / "java"
             / "io" / "detergentmc" / "protocol"
             / f"RegistryTables768Down{suffix}.java")
    for suffix in ("763", "764", "765", "766")
}


# kind: (Java メソッド名の語尾, RANGES の接頭辞, 日本語名,
#        763→768 の件数, 768→763 の件数, 764→768 の件数, 768→764 の件数,
#        765→768 の件数, 768→765 の件数, 766→768 の件数, 768→766 の件数,
#        763 側の代替先, 764 側の代替先, 765 側の代替先, 766 側の代替先,
#        767 側の代替先, 768 側の代替先)
KINDS = [
    ("BlockState", "BLOCK_STATE", "ブロック状態", 24135, 27318, 24276, 27318, 26644, 27318, 26684, 27318,
     "STONE_BLOCK_STATE_763", "STONE_BLOCK_STATE_764", "STONE_BLOCK_STATE_765",
     "STONE_BLOCK_STATE_766", "STONE_BLOCK_STATE_767", "STONE_BLOCK_STATE_768"),
    ("Item", "ITEM", "アイテム", 1255, 1375, 1255, 1375, 1312, 1375, 1330, 1375,
     "STONE_ITEM_763", "STONE_ITEM_764", "STONE_ITEM_765",
     "STONE_ITEM_766", "STONE_ITEM_767", "STONE_ITEM_768"),
    ("EntityType", "ENTITY_TYPE", "エンティティ種別", 124, 150, 124, 150, 126, 150, 130, 150,
     "PIG_ENTITY_TYPE_763", "PIG_ENTITY_TYPE_764", "PIG_ENTITY_TYPE_765",
     "PIG_ENTITY_TYPE_766", "PIG_ENTITY_TYPE_767", "PIG_ENTITY_TYPE_768"),
    ("Sound", "SOUND", "音", 1474, 1636, 1485, 1636, 1539, 1636, 1607, 1636,
     "GENERIC_SOUND_763", "GENERIC_SOUND_764", "GENERIC_SOUND_765",
     "GENERIC_SOUND_766", "GENERIC_SOUND_767", "GENERIC_SOUND_768"),
    ("Particle", "PARTICLE", "パーティクル", 95, 111, 95, 111, 101, 111, 109, 111,
     "AMBIENT_PARTICLE_763", "AMBIENT_PARTICLE_764", "AMBIENT_PARTICLE_765",
     "ENTITY_EFFECT_PARTICLE_766", "ENTITY_EFFECT_PARTICLE_767", "ENTITY_EFFECT_PARTICLE_768"),
    ("Block", "BLOCK", "ブロック", 1003, 1084, 1003, 1084, 1058, 1084, 1060, 1084,
     "STONE_BLOCK_763", "STONE_BLOCK_764", "STONE_BLOCK_765",
     "STONE_BLOCK_766", "STONE_BLOCK_767", "STONE_BLOCK_768"),
    ("Menu", "MENU", "メニュー種別", 24, 25, 24, 25, 25, 25, 25, 25,
     "CRAFTING_MENU_763", "CRAFTING_MENU_764", "CRAFTING_MENU_765",
     "CRAFTING_MENU_766", "CRAFTING_MENU_767", "CRAFTING_MENU_768"),
]

CONSTANTS_MAX = """
    /** 768側の最大の番号（ViaVersion mapping-1.21.2.json の件数−1）。 */
    public static final int MAX_BLOCK_STATE_768 = 27317;
    public static final int MAX_ITEM_768 = 1374;
    public static final int MAX_ENTITY_TYPE_768 = 149;
    public static final int MAX_SOUND_768 = 1635;
    public static final int MAX_PARTICLE_768 = 110;
    public static final int MAX_BLOCK_768 = 1083;
    public static final int MAX_MENU_768 = 24;
    /** 768側のエフェクトも 0〜38（Speed=0。767と同一。minecraft-data の
        dataPaths.json で 1.21.3 の effects は pc/1.20.5 と確認）。 */
    public static final int MAX_EFFECT_768 = 38;
    /** 768側の属性は 0〜31（767の 0〜30 に tempt_range を追加し、
        9件の名前から generic.／player. の接頭辞が落ちた）。 */
    public static final int MAX_ATTRIBUTE_768 = 31;
    /** エンチャントは 767・768 とも 0〜41（42件。並びは同一。
        minecraft-data の dataPaths.json で 1.21.3 の enchantments は
        pc/1.21.1 と確認。実機の Registry Data での確認は統合テストで行う）。 */
    public static final int MAX_ENCHANTMENT_768 = 41;
"""

CONSTANTS_FALLBACK = """
    /** 768側の代替先（mapping-1.21.2.json の番号。Speed だけ 0ベースで 0）。 */
    public static final int STONE_BLOCK_STATE_768 = 1;
    public static final int AIR_BLOCK_STATE_768 = 0;
    public static final int STONE_ITEM_768 = 1;
    public static final int PIG_ENTITY_TYPE_768 = 95;
    public static final int GENERIC_SOUND_768 = 1386; // block.stone.break
    /** 768でも ambient_entity_effect は無いため、パーティクルの代替は entity_effect。 */
    public static final int ENTITY_EFFECT_PARTICLE_768 = 20;
    public static final int STONE_BLOCK_768 = 1;
    public static final int CRAFTING_MENU_768 = 12;
    public static final int SPEED_EFFECT_768 = 0;
"""

CONSTANTS_BLOCK_ENTITY = """
    /** 768のブロックエンティティ種別：10=creaking_heart（767の piston 以降が
        +1 ずれるため、768の trial_spawner は 43、vault は 44）。 */
    public static final int BLOCK_ENTITY_CREAKING_HEART_768 = 10;
    public static final int BLOCK_ENTITY_TRIAL_SPAWNER_768 = 43;
    public static final int BLOCK_ENTITY_VAULT_768 = 44;
"""


def adjacent_maps():
    lines = []
    for kind in KINDS:
        name, prefix, jp = kind[0], kind[1], kind[2]
        lines.append(
            f"    private static final int[] {prefix}_768_MAP =\n"
            f"            expand({prefix}_768_RANGES, {prefix}_768_OVERRIDES, MAX_{prefix}_767 + 1,\n"
            f"                    {kind[16]});")
    lines.append(
        "    private static final int[] ITEM_REVERSE_768_MAP =\n"
        "            expand(ITEM_REVERSE_768_RANGES, ITEM_REVERSE_768_OVERRIDES, 1375, -1);")
    lines.append(
        "    private static final int[] ATTRIBUTE_768_MAP =\n"
        "            expand(ATTRIBUTE_768_RANGES, ATTRIBUTE_768_OVERRIDES, MAX_ATTRIBUTE_767 + 1, DROP);")
    lines.append(
        "    private static final int[] ATTRIBUTE_REVERSE_768_MAP =\n"
        "            expand(ATTRIBUTE_REVERSE_768_RANGES, ATTRIBUTE_REVERSE_768_OVERRIDES,\n"
        "                    MAX_ATTRIBUTE_768 + 1, DROP);")
    lines.append(
        "    private static final int[] ENCHANTMENT_768_MAP =\n"
        "            expand(ENCHANTMENT_768_RANGES, ENCHANTMENT_768_OVERRIDES,\n"
        "                    MAX_ENCHANTMENT_767 + 1, DROP);")
    lines.append(
        "    private static final int[] ENCHANTMENT_REVERSE_768_MAP =\n"
        "            expand(ENCHANTMENT_REVERSE_768_RANGES, ENCHANTMENT_REVERSE_768_OVERRIDES,\n"
        "                    MAX_ENCHANTMENT_768 + 1, DROP);")
    return "\n".join(lines)


def composed_maps():
    # (接尾辞, 件数の位置, 代替先の位置)。件数・代替先は KINDS の
    # タプルから取る（763_TO_768: 件数 index 3, 代替先 768=index 16）。
    pairs = [
        ("763_TO_768", 3, 16), ("768_TO_763", 4, 11),
        ("764_TO_768", 5, 16), ("768_TO_764", 6, 12),
        ("765_TO_768", 7, 16), ("768_TO_765", 8, 13),
        ("766_TO_768", 9, 16), ("768_TO_766", 10, 14),
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
            continue  # アイテムは ITEM_REVERSE_768_MAP を使う。
        # エンティティだけ補正あり：768 の per-wood boat／raft を
        # 767 の boat／chest_boat へ（ENTITY_UP_768_CORRECTIONS）。
        corrections = ("ENTITY_UP_768_CORRECTIONS"
                       if prefix == "ENTITY_TYPE" else "null")
        lines.append(
            f"    private static final int[] {prefix}_UP_768_MAP = invert(\n"
            f"            {prefix}_768_RANGES, {prefix}_768_OVERRIDES,\n"
            f"            MAX_{prefix}_767 + 1, MAX_{prefix}_768 + 1,\n"
            f"            {kind[15]}, {corrections});")
    return "\n".join(lines)


SOUND_NAME_MAPS = """
    private static final Map<String, String> SOUND_NAME_FALLBACK_768_MAP =
            buildSoundNameMap(SOUND_NAME_FALLBACKS_768);
    private static final Map<String, String> SOUND_NAME_FALLBACK_767_TO_768_MAP =
            buildSoundNameMap(SOUND_NAME_FALLBACKS_767_TO_768);
    private static final Map<String, String> SOUND_NAME_FALLBACK_768_TO_767_MAP =
            buildSoundNameMap(SOUND_NAME_FALLBACKS_768_TO_767);
    private static final Map<String, String> SOUND_NAME_FALLBACK_768_TO_765_MAP =
            buildSoundNameMap(SOUND_NAME_FALLBACKS_768_TO_765);
    private static final Map<String, String> SOUND_NAME_FALLBACK_768_TO_763_MAP =
            buildSoundNameMap(SOUND_NAME_FALLBACKS_768_TO_763);
    private static final Map<String, String> SOUND_NAME_FALLBACK_768_TO_764_MAP =
            buildSoundNameMap(SOUND_NAME_FALLBACKS_768_TO_764);
    private static final Map<String, String> SOUND_NAME_FALLBACK_768_TO_766_MAP =
            buildSoundNameMap(SOUND_NAME_FALLBACKS_768_TO_766);
    private static final Map<String, String> SOUND_NAME_FALLBACK_766_TO_768_MAP =
            buildSoundNameMap(SOUND_NAME_FALLBACKS_766_TO_768);
"""

LOG_UP_768 = """
    private static void logUnknownUp768Once(String kind, int id768) {
        if (LOGGED_UNKNOWN.add("up768:" + kind + ":" + id768)) {
            LOG.warning("768に無い" + kind + "の番号が来たため代替に落とします（768 id=" + id768 + "）。");
        }
    }
"""
def adjacent_accessors():
    out = ["    // ------------------------------------------------------------------",
           "    // 767→768。上の *_768 表を使う。ブロック状態・ブロック・",
           "    // アイテム・エンティティ・音・パーティクルは 767 から 768 で",
           "    // ペールオーク・クリエイキング・バンドル16色などが挿入でずれる。",
           "    // メニュー・エフェクト・バナー・絵画・装飾パターンは同一、",
           "    // エンチャントも同一の並び。属性は改名9件＋新1件、",
           "    // ブロックエンティティ種別は creaking_heart の挿入で +1 ずれる。",
           "    // ------------------------------------------------------------------",
           ""]
    for kind in KINDS:
        name, prefix, jp = kind[0], kind[1], kind[2]
        fb768 = kind[16]
        out.append(f"    /** 767の{jp}ID → 768の{jp}ID。範囲外は{fb768} に相当する代替。 */")
        out.append(f"    public static int map{name}767To768(int id767) {{")
        out.append(f"        if (id767 < 0 || id767 > MAX_{prefix}_767) {{")
        out.append(f"            logUnknownOnce(PROTOCOL_768, \"{jp}\", id767);")
        out.append(f"            return {fb768};")
        out.append("        }")
        out.append(f"        return {prefix}_768_MAP[id767];")
        out.append("    }")
        out.append("")
    out.append("    /**")
    out.append("     * 768のアイテムID → 767のアイテムID（サーバー行きスロット用）。")
    out.append("     * 768にしか無いアイテム（ペールオーク類・バンドル16色など42件）は 767の石へ。")
    out.append("     * 範囲外はそのまま返す。")
    out.append("     */")
    out.append("    public static int mapItemReverse768(int id768) {")
    out.append("        if (id768 < 0 || id768 >= ITEM_REVERSE_768_MAP.length) {")
    out.append("            logUnknownOnce(PROTOCOL_768, \"アイテム（逆変換）\", id768);")
    out.append("            return id768;")
    out.append("        }")
    out.append("        int mapped = ITEM_REVERSE_768_MAP[id768];")
    out.append("        return mapped < 0 ? id768 : mapped;")
    out.append("    }")
    out.append("")
    out.append("    /**")
    out.append("     * 767のエフェクトID → 768のエフェクトID。767も768も0ベース")
    out.append("     * （Speed は両方 0）で 0〜38 は同一のため恒等写像。")
    out.append("     * 範囲外は Speed（0）。")
    out.append("     */")
    out.append("    public static int mapEffect767To768(int id767) {")
    out.append("        if (id767 < 0 || id767 > MAX_EFFECT_767) {")
    out.append("            logUnknownOnce(PROTOCOL_768, \"エフェクト\", id767);")
    out.append("            return SPEED_EFFECT_768;")
    out.append("        }")
    out.append("        return id767;")
    out.append("    }")
    out.append("")
    out.append("    /**")
    out.append("     * 767のブロックエンティティ種別ID → 768の種別ID。")
    out.append("     * creaking_heart（10）が挿入されたため、piston 以降（10〜43）は")
    out.append("     * +1 ずれる。vault は 767=43 → 768=44。")
    out.append("     * 範囲外の種別は NBT の中身が種別専用のため安全に代替できず")
    out.append("     * {@link #DROP}。")
    out.append("     */")
    out.append("    public static int mapBlockEntityType767To768(int id767) {")
    out.append("        if (id767 < 0 || id767 > BLOCK_ENTITY_VAULT_766) {")
    out.append("            logUnknownOnce(PROTOCOL_768, \"ブロックエンティティ種別\", id767);")
    out.append("            return DROP;")
    out.append("        }")
    out.append("        return id767 < 10 ? id767 : id767 + 1;")
    out.append("    }")
    out.append("")
    out.append("    /**")
    out.append("     * 音の名前（直書き・Stop Sound 用）を768向けに変換する。")
    out.append("     * {@code minecraft:} の有無はそのまま保つ。768で削除された")
    out.append("     * ヤギの角笛の音2件は代替の名前にする。")
    out.append("     * それ以外の音は名前が同じためそのまま通す。")
    out.append("     */")
    out.append("    public static String mapSoundName767To768(String name) {")
    out.append("        return mapSoundNameComposed(name, SOUND_NAME_FALLBACK_767_TO_768_MAP);")
    out.append("    }")
    out.append("")
    # 属性とエンチャント。
    out.append("    /** 767の属性ID → 768の属性ID。範囲外は {@link #DROP}。 */")
    out.append("    public static int mapAttribute767To768(int id767) {")
    out.append("        if (id767 < 0 || id767 > MAX_ATTRIBUTE_767) {")
    out.append("            logUnknownOnce(PROTOCOL_768, \"属性\", id767);")
    out.append("            return DROP;")
    out.append("        }")
    out.append("        return ATTRIBUTE_768_MAP[id767];")
    out.append("    }")
    out.append("")
    out.append("    /**")
    out.append("     * 768の属性ID → 767の属性ID。")
    out.append("     * 768にしか無い属性（tempt_range）は 767に無いため {@link #DROP}。")
    out.append("     */")
    out.append("    public static int mapAttribute768To767(int id768) {")
    out.append("        if (id768 < 0 || id768 > MAX_ATTRIBUTE_768) {")
    out.append("            logUnknownOnce(PROTOCOL_768, \"属性\", id768);")
    out.append("            return DROP;")
    out.append("        }")
    out.append("        return ATTRIBUTE_REVERSE_768_MAP[id768];")
    out.append("    }")
    out.append("")
    out.append("    /** 768の属性ID → 属性の名前（Update Attributes の 767向け変換用）。 */")
    out.append("    public static String attributeName768(int id768) {")
    out.append("        if (id768 < 0 || id768 >= ATTRIBUTE_768_NAMES.length) {")
    out.append("            return null;")
    out.append("        }")
    out.append("        return ATTRIBUTE_768_NAMES[id768];")
    out.append("    }")
    out.append("")
    out.append("    /**")
    out.append("     * 属性の名前 → 768の属性ID（765／763／764 は属性が名前形式のため、")
    out.append("     * 768向けに変換するときに使う）。無い名前は {@link #DROP}。")
    out.append("     */")
    out.append("    public static int attributeId768(String name) {")
    out.append("        if (name == null) {")
    out.append("            return DROP;")
    out.append("        }")
    out.append("        String plain = stripMinecraftPrefix(name);")
    out.append("        for (int i = 0; i < ATTRIBUTE_768_NAMES.length; i++) {")
    out.append("            if (ATTRIBUTE_768_NAMES[i].equals(plain)) {")
    out.append("                return i;")
    out.append("            }")
    out.append("        }")
    out.append("        return DROP;")
    out.append("    }")
    out.append("")
    out.append("    /** 767のエンチャントID → 768のエンチャントID。並びは同一。範囲外は {@link #DROP}。 */")
    out.append("    public static int mapEnchantment767To768(int id767) {")
    out.append("        if (id767 < 0 || id767 > MAX_ENCHANTMENT_767) {")
    out.append("            logUnknownOnce(PROTOCOL_768, \"エンチャント\", id767);")
    out.append("            return DROP;")
    out.append("        }")
    out.append("        return ENCHANTMENT_768_MAP[id767];")
    out.append("    }")
    out.append("")
    out.append("    /** 768のエンチャントID → 767のエンチャントID。並びは同一。範囲外は {@link #DROP}。 */")
    out.append("    public static int mapEnchantment768To767(int id768) {")
    out.append("        if (id768 < 0 || id768 > MAX_ENCHANTMENT_768) {")
    out.append("            logUnknownOnce(PROTOCOL_768, \"エンチャント\", id768);")
    out.append("            return DROP;")
    out.append("        }")
    out.append("        return ENCHANTMENT_REVERSE_768_MAP[id768];")
    out.append("    }")
    out.append("")
    out.append("    /**")
    out.append("     * 766のエンチャントID → 768のエンチャントID。766→767 の表を")
    out.append("     * 経由して 767→768 の表を引く。範囲外は {@link #DROP}。")
    out.append("     */")
    out.append("    public static int mapEnchantment766To768(int id766) {")
    out.append("        int id767 = mapEnchantment766To767(id766);")
    out.append("        return id767 == DROP ? DROP : mapEnchantment767To768(id767);")
    out.append("    }")
    out.append("")
    out.append("    /**")
    out.append("     * 768のエンチャントID → 766のエンチャントID。768→767 の逆変換の")
    out.append("     * 結果を 767→766 の逆変換で戻す。範囲外は {@link #DROP}。")
    out.append("     */")
    out.append("    public static int mapEnchantment768To766(int id768) {")
    out.append("        int id767 = mapEnchantment768To767(id768);")
    out.append("        return id767 == DROP ? DROP : mapEnchantment767To766(id767);")
    out.append("    }")
    out.append("")
    out.append("    /**")
    out.append("     * 765のエンチャントID → 768のエンチャントID。765→767 の変換")
    out.append("     * （{@link #mapEnchantment765To767}）を経由して 767→768 の")
    out.append("     * 表を引く。範囲外は {@link #DROP}。")
    out.append("     * 763／764 のエンチャントの並びは 765 と同一のため、")
    out.append("     * 763／764 向けも同じ変換を使う。")
    out.append("     */")
    out.append("    public static int mapEnchantment765To768(int id765) {")
    out.append("        int id767 = mapEnchantment765To767(id765);")
    out.append("        return id767 == DROP ? DROP : mapEnchantment767To768(id767);")
    out.append("    }")
    out.append("")
    out.append("    /** 763のエンチャントID → 768のエンチャントID（765と同じ並び）。 */")
    out.append("    public static int mapEnchantment763To768(int id763) {")
    out.append("        return mapEnchantment765To768(id763);")
    out.append("    }")
    out.append("")
    out.append("    /** 764のエンチャントID → 768のエンチャントID（765と同じ並び）。 */")
    out.append("    public static int mapEnchantment764To768(int id764) {")
    out.append("        return mapEnchantment765To768(id764);")
    out.append("    }")
    out.append("")
    out.append("    /**")
    out.append("     * 768のエンチャントID → 765のエンチャントID。768→767 の逆変換の")
    out.append("     * 結果を 767→765 の変換（{@link #mapEnchantment767To765}）で")
    out.append("     * 戻す。density／breach／wind_burst は 765に無いため")
    out.append("     * {@link #DROP}。範囲外も {@link #DROP}。")
    out.append("     */")
    out.append("    public static int mapEnchantment768To765(int id768) {")
    out.append("        int id767 = mapEnchantment768To767(id768);")
    out.append("        return id767 == DROP ? DROP : mapEnchantment767To765(id767);")
    out.append("    }")
    out.append("")
    out.append("    /** 768のエンチャントID → 763のエンチャントID（765と同じ並び）。 */")
    out.append("    public static int mapEnchantment768To763(int id768) {")
    out.append("        return mapEnchantment768To765(id768);")
    out.append("    }")
    out.append("")
    out.append("    /** 768のエンチャントID → 764のエンチャントID（765と同じ並び）。 */")
    out.append("    public static int mapEnchantment768To764(int id768) {")
    out.append("        return mapEnchantment768To765(id768);")
    out.append("    }")
    return "\n".join(out)


def up_accessors():
    out = ["    // ------------------------------------------------------------------",
           "    // 逆向き（768→767）。上の *_768 表を反転して作った表を使う。",
           "    // アイテムだけは生成スクリプトが名前から作った既存の逆変換表",
           "    // （ITEM_REVERSE_768_MAP）をそのまま使う（反転した表と完全一致",
           "    // することはテストで確認している）。属性とエンチャントの逆向きは",
           "    // mapAttribute768To767／mapEnchantment768To767 を参照。",
           "    // ------------------------------------------------------------------",
           ""]
    for kind in KINDS:
        name, prefix, jp = kind[0], kind[1], kind[2]
        fb767 = kind[15]
        if prefix == "ITEM":
            out.append("    /**")
            out.append("     * 768のアイテムID → 767のアイテムID。")
            out.append("     * 768にしか無いアイテム（ペールオーク類・バンドル16色など42件）は石へ。範囲外は石（1）。")
            out.append("     */")
            out.append("    public static int mapItemUp768(int id768) {")
            out.append("        if (id768 < 0 || id768 >= ITEM_REVERSE_768_MAP.length) {")
            out.append("            logUnknownUp768Once(\"アイテム\", id768);")
            out.append("            return STONE_ITEM_767;")
            out.append("        }")
            out.append("        int mapped = ITEM_REVERSE_768_MAP[id768];")
            out.append("        return mapped < 0 ? STONE_ITEM_767 : mapped;")
            out.append("    }")
            out.append("")
            continue
        out.append(f"    /** 768の{jp}ID → 767の{jp}ID。範囲外は{fb767} に相当する代替。 */")
        out.append(f"    public static int map{name}Up768(int id768) {{")
        out.append(f"        if (id768 < 0 || id768 > MAX_{prefix}_768) {{")
        out.append(f"            logUnknownUp768Once(\"{jp}\", id768);")
        out.append(f"            return {fb767};")
        out.append("        }")
        out.append(f"        return {prefix}_UP_768_MAP[id768];")
        out.append("    }")
        out.append("")
    out.append("    /**")
    out.append("     * 768のエフェクトID → 767のエフェクトID。0〜38 は同一のため恒等写像。")
    out.append("     * 範囲外は Speed（0）。")
    out.append("     */")
    out.append("    public static int mapEffectUp768(int id768) {")
    out.append("        if (id768 < 0 || id768 > MAX_EFFECT_768) {")
    out.append("            logUnknownUp768Once(\"エフェクト\", id768);")
    out.append("            return SPEED_EFFECT_767;")
    out.append("        }")
    out.append("        return id768;")
    out.append("    }")
    out.append("")
    out.append("    /**")
    out.append("     * 768のブロックエンティティ種別ID → 767の種別ID。")
    out.append("     * creaking_heart（10）は767に無いため {@link #DROP}。")
    out.append("     * piston 以降（11〜44）は -1 ずれる。vault は 768=44 → 767=43。")
    out.append("     * 範囲外の種別は NBT の中身が種別専用のため安全に代替できず")
    out.append("     * {@link #DROP}。")
    out.append("     */")
    out.append("    public static int mapBlockEntityTypeUp768(int id768) {")
    out.append("        if (id768 < 0 || id768 > BLOCK_ENTITY_VAULT_768) {")
    out.append("            logUnknownUp768Once(\"ブロックエンティティ種別\", id768);")
    out.append("            return DROP;")
    out.append("        }")
    out.append("        if (id768 == BLOCK_ENTITY_CREAKING_HEART_768) {")
    out.append("            return DROP;")
    out.append("        }")
    out.append("        return id768 < 10 ? id768 : id768 - 1;")
    out.append("    }")
    out.append("")
    out.append("    /**")
    out.append("     * 音の名前を767向けに変換する。768の音の名前のうち 767に無いものは")
    out.append("     * 代替の音の名前にする（数値の反転表と一致させる）。768の新音")
    out.append("     * （クリエイキング・ペールモス・スポナー音など27件）は石の破壊音へ。")
    out.append("     * {@code null} は石の破壊音にする。")
    out.append("     */")
    out.append("    public static String mapSoundNameUp768(String name) {")
    out.append("        return mapSoundNameComposed(name, SOUND_NAME_FALLBACK_768_TO_767_MAP);")
    out.append("    }")
    return "\n".join(out)
def composed_accessors():
    out = ["    // ------------------------------------------------------------------",
           "    // 離れた組み合わせの合成表（763↔768、764↔768、765↔768、766↔768）。上の",
           "    // *_763_TO_768／*_768_TO_763／*_764_TO_768／*_768_TO_764／",
           "    // *_765_TO_768／*_768_TO_765／*_766_TO_768／*_768_TO_766 表",
           "    // （合成済みの1つの表）を直接引く。二段変換はしない。エフェクトと",
           "    // ブロックエンティティ種別は表を作らず、数値の規則と同一の並びで扱う。",
           "    // ------------------------------------------------------------------",
           ""]
    pairs = [
        ("763", 11, "763→768", "768→763"),
        ("764", 12, "764→768", "768→764"),
        ("765", 13, "765→768", "768→765"),
        ("766", 14, "766→768", "768→766"),
    ]
    for src, fb_src_idx, up_label, down_label in pairs:
        for kind in KINDS:
            name, prefix, jp = kind[0], kind[1], kind[2]
            fb_src = kind[fb_src_idx]
            fb_768 = kind[16]
            # src → 768
            out.append(f"    /** {src}の{jp}ID → 768の{jp}ID。範囲外は{fb_768} に相当する代替。 */")
            out.append(f"    public static int map{name}{src}To768(int id{src}) {{")
            out.append(f"        if (id{src} < 0 || id{src} >= {prefix}_{src}_TO_768_MAP.length) {{")
            out.append(f"            logUnknownComposedOnce(\"{up_label}\", \"{jp}\", id{src});")
            out.append(f"            return {fb_768};")
            out.append("        }")
            out.append(f"        return {prefix}_{src}_TO_768_MAP[id{src}];")
            out.append("    }")
            out.append("")
            # 768 → src
            out.append(f"    /** 768の{jp}ID → {src}の{jp}ID。範囲外は{fb_src} に相当する代替。 */")
            out.append(f"    public static int map{name}768To{src}(int id768) {{")
            out.append(f"        if (id768 < 0 || id768 >= {prefix}_768_TO_{src}_MAP.length) {{")
            out.append(f"            logUnknownComposedOnce(\"{down_label}\", \"{jp}\", id768);")
            out.append(f"            return {fb_src};")
            out.append("        }")
            out.append(f"        return {prefix}_768_TO_{src}_MAP[id768];")
            out.append("    }")
            out.append("")
        # エフェクトとブロックエンティティ種別と音の名前。
        if src == "763":
            out.append("    /**")
            out.append("     * 763のエフェクトID → 768のエフェクトID。763は1ベース")
            out.append("     * （Speed=1）のため 1 引く。範囲外は Speed（0）。")
            out.append("     */")
            out.append("    public static int mapEffect763To768(int id763) {")
            out.append("        if (id763 < 1 || id763 > MAX_EFFECT_765 + 1) {")
            out.append("            logUnknownComposedOnce(\"763→768\", \"エフェクト\", id763);")
            out.append("            return SPEED_EFFECT_768;")
            out.append("        }")
            out.append("        return id763 - 1;")
            out.append("    }")
            out.append("")
            out.append("    /**")
            out.append("     * 768のエフェクトID → 763のエフェクトID。763は1ベース")
            out.append("     * （Speed=1）のため 1 足す。768で増えた 33〜38 は")
            out.append("     * 763に無いため Speed（1）へ。範囲外も Speed（1）。")
            out.append("     */")
            out.append("    public static int mapEffect768To763(int id768) {")
            out.append("        if (id768 < 0 || id768 > MAX_EFFECT_768) {")
            out.append("            logUnknownComposedOnce(\"768→763\", \"エフェクト\", id768);")
            out.append("            return SPEED_EFFECT_763;")
            out.append("        }")
            out.append("        return id768 <= MAX_EFFECT_765 ? id768 + 1 : SPEED_EFFECT_763;")
            out.append("    }")
            out.append("")
            out.append("    /**")
            out.append("     * 763のブロックエンティティ種別ID → 768の種別ID。")
            out.append("     * 763の種別は 0〜9 で768と同じ並びのためそのまま。")
            out.append("     * creaking_heart（10）の挿入で piston 以降（10〜40）は +1 ずれる。")
            out.append("     * 763に無い番号（41以上）は768側の種別に対応付けられないため")
            out.append("     * {@link #DROP}。")
            out.append("     */")
            out.append("    public static int mapBlockEntityType763To768(int id763) {")
            out.append("        if (id763 < 0 || id763 > BLOCK_ENTITY_TRIAL_SPAWNER_765 - 2) {")
            out.append("            logUnknownComposedOnce(\"763→768\", \"ブロックエンティティ種別\", id763);")
            out.append("            return DROP;")
            out.append("        }")
            out.append("        return id763 < 10 ? id763 : id763 + 1;")
            out.append("    }")
            out.append("")
            out.append("    /**")
            out.append("     * 768のブロックエンティティ種別ID → 763の種別ID。")
            out.append("     * 768の種別は 0〜9 で763と同じ並びのためそのまま。")
            out.append("     * creaking_heart（10）は763に無いため {@link #DROP}。")
            out.append("     * piston 以降（11〜41）は -1 ずれる。crafter（42）・")
            out.append("     * trial_spawner（43）・vault（44）は763に無いため {@link #DROP}。")
            out.append("     */")
            out.append("    public static int mapBlockEntityType768To763(int id768) {")
            out.append("        if (id768 < 0 || id768 > BLOCK_ENTITY_TRIAL_SPAWNER_768 - 2) {")
            out.append("            logUnknownComposedOnce(\"768→763\", \"ブロックエンティティ種別\", id768);")
            out.append("            return DROP;")
            out.append("        }")
            out.append("        if (id768 == BLOCK_ENTITY_CREAKING_HEART_768) {")
            out.append("            return DROP;")
            out.append("        }")
            out.append("        return id768 < 10 ? id768 : id768 - 1;")
            out.append("    }")
            out.append("")
            out.append("    /**")
            out.append("     * 音の名前を768向けに変換する。763の音の名前はすべて768にもある")
            out.append("     * （768は763の音をすべて含む）ため、そのまま返す。")
            out.append("     * {@code null} は石の破壊音にする。")
            out.append("     */")
            out.append("    public static String mapSoundName763To768(String name) {")
            out.append("        if (name == null) {")
            out.append("            return \"minecraft:block.stone.break\";")
            out.append("        }")
            out.append("        return name;")
            out.append("    }")
            out.append("")
            out.append("    /**")
            out.append("     * 音の名前を763向けに変換する。768の音の名前のうち 763に無いものは")
            out.append("     * 代替の音の名前にする（数値の合成表と一致させる）。")
            out.append("     * {@code null} は石の破壊音にする。")
            out.append("     */")
            out.append("    public static String mapSoundName768To763(String name) {")
            out.append("        return mapSoundNameComposed(name, SOUND_NAME_FALLBACK_768_TO_763_MAP);")
            out.append("    }")
        elif src == "764":
            out.append("    /**")
            out.append("     * 764のエフェクトID → 768のエフェクトID。764も768も0ベース")
            out.append("     * （Speed は両方 0）で 0〜32 は同一のため恒等写像。")
            out.append("     * 範囲外は Speed（0）。")
            out.append("     */")
            out.append("    public static int mapEffect764To768(int id764) {")
            out.append("        if (id764 < 0 || id764 > MAX_EFFECT_764) {")
            out.append("            logUnknownComposedOnce(\"764→768\", \"エフェクト\", id764);")
            out.append("            return SPEED_EFFECT_768;")
            out.append("        }")
            out.append("        return id764;")
            out.append("    }")
            out.append("")
            out.append("    /**")
            out.append("     * 768のエフェクトID → 764のエフェクトID。0〜32 は同一のため恒等写像。")
            out.append("     * 768で増えた 33〜38 は 764に無いため Speed（0）へ。")
            out.append("     * 範囲外も Speed（0）。")
            out.append("     */")
            out.append("    public static int mapEffect768To764(int id768) {")
            out.append("        if (id768 < 0 || id768 > MAX_EFFECT_768) {")
            out.append("            logUnknownComposedOnce(\"768→764\", \"エフェクト\", id768);")
            out.append("            return SPEED_EFFECT_764;")
            out.append("        }")
            out.append("        return id768 <= MAX_EFFECT_764 ? id768 : SPEED_EFFECT_764;")
            out.append("    }")
            out.append("")
            out.append("    /**")
            out.append("     * 764のブロックエンティティ種別ID → 768の種別ID。")
            out.append("     * 764の種別は 0〜9 で768と同じ並びのためそのまま。")
            out.append("     * creaking_heart（10）の挿入で piston 以降（10〜40）は +1 ずれる。")
            out.append("     * 764に無い番号（41以上）は768側の種別に対応付けられないため")
            out.append("     * {@link #DROP}。")
            out.append("     */")
            out.append("    public static int mapBlockEntityType764To768(int id764) {")
            out.append("        if (id764 < 0 || id764 > BLOCK_ENTITY_TRIAL_SPAWNER_765 - 2) {")
            out.append("            logUnknownComposedOnce(\"764→768\", \"ブロックエンティティ種別\", id764);")
            out.append("            return DROP;")
            out.append("        }")
            out.append("        return id764 < 10 ? id764 : id764 + 1;")
            out.append("    }")
            out.append("")
            out.append("    /**")
            out.append("     * 768のブロックエンティティ種別ID → 764の種別ID。")
            out.append("     * 768の種別は 0〜9 で764と同じ並びのためそのまま。")
            out.append("     * creaking_heart（10）は764に無いため {@link #DROP}。")
            out.append("     * piston 以降（11〜41）は -1 ずれる。crafter（42）・")
            out.append("     * trial_spawner（43）・vault（44）は764に無いため {@link #DROP}。")
            out.append("     */")
            out.append("    public static int mapBlockEntityType768To764(int id768) {")
            out.append("        if (id768 < 0 || id768 > BLOCK_ENTITY_TRIAL_SPAWNER_768 - 2) {")
            out.append("            logUnknownComposedOnce(\"768→764\", \"ブロックエンティティ種別\", id768);")
            out.append("            return DROP;")
            out.append("        }")
            out.append("        if (id768 == BLOCK_ENTITY_CREAKING_HEART_768) {")
            out.append("            return DROP;")
            out.append("        }")
            out.append("        return id768 < 10 ? id768 : id768 - 1;")
            out.append("    }")
            out.append("")
            out.append("    /**")
            out.append("     * 音の名前を768向けに変換する。764の音の名前はすべて768にもある")
            out.append("     * （768は764の音をすべて含む）ため、そのまま返す。")
            out.append("     * {@code null} は石の破壊音にする。")
            out.append("     */")
            out.append("    public static String mapSoundName764To768(String name) {")
            out.append("        if (name == null) {")
            out.append("            return \"minecraft:block.stone.break\";")
            out.append("        }")
            out.append("        return name;")
            out.append("    }")
            out.append("")
            out.append("    /**")
            out.append("     * 音の名前を764向けに変換する。768の音の名前のうち 764に無いものは")
            out.append("     * 代替の音の名前にする（数値の合成表と一致させる）。")
            out.append("     * {@code null} は石の破壊音にする。")
            out.append("     */")
            out.append("    public static String mapSoundName768To764(String name) {")
            out.append("        return mapSoundNameComposed(name, SOUND_NAME_FALLBACK_768_TO_764_MAP);")
            out.append("    }")
        elif src == "765":
            out.append("    /**")
            out.append("     * 765のエフェクトID → 768のエフェクトID。765も768も0ベース")
            out.append("     * （Speed は両方 0）で 0〜32 は同一のため恒等写像。")
            out.append("     * 範囲外は Speed（0）。")
            out.append("     */")
            out.append("    public static int mapEffect765To768(int id765) {")
            out.append("        if (id765 < 0 || id765 > MAX_EFFECT_765) {")
            out.append("            logUnknownComposedOnce(\"765→768\", \"エフェクト\", id765);")
            out.append("            return SPEED_EFFECT_768;")
            out.append("        }")
            out.append("        return id765;")
            out.append("    }")
            out.append("")
            out.append("    /**")
            out.append("     * 768のエフェクトID → 765のエフェクトID。0〜32 は同一のため恒等写像。")
            out.append("     * 768で増えた 33〜38 は 765に無いため Speed（0）へ。")
            out.append("     * 範囲外も Speed（0）。")
            out.append("     */")
            out.append("    public static int mapEffect768To765(int id768) {")
            out.append("        if (id768 < 0 || id768 > MAX_EFFECT_768) {")
            out.append("            logUnknownComposedOnce(\"768→765\", \"エフェクト\", id768);")
            out.append("            return SPEED_EFFECT_765;")
            out.append("        }")
            out.append("        return id768 <= MAX_EFFECT_765 ? id768 : SPEED_EFFECT_765;")
            out.append("    }")
            out.append("")
            out.append("    /**")
            out.append("     * 765のブロックエンティティ種別ID → 768の種別ID。")
            out.append("     * crafter（41）と trial_spawner（42）は768にもあるが、")
            out.append("     * creaking_heart（10）の挿入で +1 ずれる。")
            out.append("     * 範囲外の種別は NBT の中身が種別専用のため安全に代替できず")
            out.append("     * {@link #DROP}。")
            out.append("     */")
            out.append("    public static int mapBlockEntityType765To768(int id765) {")
            out.append("        if (id765 < 0 || id765 > BLOCK_ENTITY_TRIAL_SPAWNER_765) {")
            out.append("            logUnknownComposedOnce(\"765→768\", \"ブロックエンティティ種別\", id765);")
            out.append("            return DROP;")
            out.append("        }")
            out.append("        return id765 < 10 ? id765 : id765 + 1;")
            out.append("    }")
            out.append("")
            out.append("    /**")
            out.append("     * 768のブロックエンティティ種別ID → 765の種別ID。")
            out.append("     * creaking_heart（10）は765に無いため {@link #DROP}。")
            out.append("     * piston 以降（11〜43）は -1 ずれる。vault（44）は765に無い")
            out.append("     * ため {@link #DROP}。")
            out.append("     */")
            out.append("    public static int mapBlockEntityType768To765(int id768) {")
            out.append("        if (id768 < 0 || id768 > BLOCK_ENTITY_TRIAL_SPAWNER_768) {")
            out.append("            logUnknownComposedOnce(\"768→765\", \"ブロックエンティティ種別\", id768);")
            out.append("            return DROP;")
            out.append("        }")
            out.append("        if (id768 == BLOCK_ENTITY_CREAKING_HEART_768) {")
            out.append("            return DROP;")
            out.append("        }")
            out.append("        return id768 < 10 ? id768 : id768 - 1;")
            out.append("    }")
            out.append("")
            out.append("    /**")
            out.append("     * 音の名前を768向けに変換する。765の音の名前のうち 767で改名された")
            out.append("     * trial_spawner の音2件は新しい名前にし、768で削除された")
            out.append("     * ヤギの角笛の音2件は代替の名前にする。")
            out.append("     * {@code null} は石の破壊音にする。")
            out.append("     */")
            out.append("    public static String mapSoundName765To768(String name) {")
            out.append("        return mapSoundNameComposed(")
            out.append("                mapSoundNameComposed(name, SOUND_NAME_FALLBACK_767_MAP),")
            out.append("                SOUND_NAME_FALLBACK_768_MAP);")
            out.append("    }")
            out.append("")
            out.append("    /**")
            out.append("     * 音の名前を765向けに変換する。768の音の名前のうち 765に無いものは")
            out.append("     * 代替の音の名前にする（数値の合成表と一致させる）。")
            out.append("     * {@code null} は石の破壊音にする。")
            out.append("     */")
            out.append("    public static String mapSoundName768To765(String name) {")
            out.append("        return mapSoundNameComposed(name, SOUND_NAME_FALLBACK_768_TO_765_MAP);")
            out.append("    }")
        else:  # 766
            out.append("    /**")
            out.append("     * 766のエフェクトID → 768のエフェクトID。766も768も0ベース")
            out.append("     * （Speed は両方 0）で 0〜38 は同一のため恒等写像。")
            out.append("     * 範囲外は Speed（0）。")
            out.append("     */")
            out.append("    public static int mapEffect766To768(int id766) {")
            out.append("        if (id766 < 0 || id766 > MAX_EFFECT_766) {")
            out.append("            logUnknownComposedOnce(\"766→768\", \"エフェクト\", id766);")
            out.append("            return SPEED_EFFECT_768;")
            out.append("        }")
            out.append("        return id766;")
            out.append("    }")
            out.append("")
            out.append("    /**")
            out.append("     * 768のエフェクトID → 766のエフェクトID。0〜38 は同一のため恒等写像。")
            out.append("     * 範囲外は Speed（0）。")
            out.append("     */")
            out.append("    public static int mapEffect768To766(int id768) {")
            out.append("        if (id768 < 0 || id768 > MAX_EFFECT_768) {")
            out.append("            logUnknownComposedOnce(\"768→766\", \"エフェクト\", id768);")
            out.append("            return SPEED_EFFECT_766;")
            out.append("        }")
            out.append("        return id768;")
            out.append("    }")
            out.append("")
            out.append("    /**")
            out.append("     * 766のブロックエンティティ種別ID → 768の種別ID。")
            out.append("     * vault（43）は768にもあるが、creaking_heart（10）の挿入で")
            out.append("     * piston 以降（10〜43）は +1 ずれる。")
            out.append("     * 範囲外の種別は NBT の中身が種別専用のため安全に代替できず")
            out.append("     * {@link #DROP}。")
            out.append("     */")
            out.append("    public static int mapBlockEntityType766To768(int id766) {")
            out.append("        if (id766 < 0 || id766 > BLOCK_ENTITY_VAULT_766) {")
            out.append("            logUnknownComposedOnce(\"766→768\", \"ブロックエンティティ種別\", id766);")
            out.append("            return DROP;")
            out.append("        }")
            out.append("        return id766 < 10 ? id766 : id766 + 1;")
            out.append("    }")
            out.append("")
            out.append("    /**")
            out.append("     * 768のブロックエンティティ種別ID → 766の種別ID。")
            out.append("     * creaking_heart（10）は766に無いため {@link #DROP}。")
            out.append("     * piston 以降（11〜44）は -1 ずれる。vault は 768=44 → 766=43。")
            out.append("     */")
            out.append("    public static int mapBlockEntityType768To766(int id768) {")
            out.append("        if (id768 < 0 || id768 > BLOCK_ENTITY_VAULT_768) {")
            out.append("            logUnknownComposedOnce(\"768→766\", \"ブロックエンティティ種別\", id768);")
            out.append("            return DROP;")
            out.append("        }")
            out.append("        if (id768 == BLOCK_ENTITY_CREAKING_HEART_768) {")
            out.append("            return DROP;")
            out.append("        }")
            out.append("        return id768 < 10 ? id768 : id768 - 1;")
            out.append("    }")
            out.append("")
            out.append("    /**")
            out.append("     * 音の名前を768向けに変換する。766の音の名前のうち 767で改名された")
            out.append("     * trial_spawner の音2件は新しい名前にし、768で削除された")
            out.append("     * ヤギの角笛の音2件は代替の名前にする。")
            out.append("     * {@code null} は石の破壊音にする。")
            out.append("     */")
            out.append("    public static String mapSoundName766To768(String name) {")
            out.append("        return mapSoundNameComposed(name, SOUND_NAME_FALLBACK_766_TO_768_MAP);")
            out.append("    }")
            out.append("")
            out.append("    /**")
            out.append("     * 音の名前を766向けに変換する。768の音の名前のうち 766に無いものは")
            out.append("     * 代替の音の名前にする（数値の合成表と一致させる）。")
            out.append("     * {@code null} は石の破壊音にする。")
            out.append("     */")
            out.append("    public static String mapSoundName768To766(String name) {")
            out.append("        return mapSoundNameComposed(name, SOUND_NAME_FALLBACK_768_TO_766_MAP);")
            out.append("    }")
    return "\n".join(out)


# バナー・絵画・装飾パターン・ダメージ種別の 768 向け（767 と同一のものは
# 恒等写像。ダメージ種別だけ ender_pearl・mace_smash の2件が追加）。
DAMAGE_TYPE_768 = """
    /** 768のダメージ種別最大ID（49件）。 */
    public static final int MAX_DAMAGE_TYPE_768 = 48;

    /** ダメージ種別ID：767 → 768（ender_pearl・mace_smash の位置で後ろがずれる）。
     * アルファベット順で mace_smash は magic より前（768：mace_smash=26、magic=27）。
     * バニラ jar の data/minecraft/damage_type/*.json のファイル名の並びによる。
     * 実サーバーの Registry Data での確認は統合テストで行う。 */
    private static final int[] DAMAGE_TYPE_768_MAP = {
        0, 1, 2, 3, 4, 5, 6, 7, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20,
        21, 22, 23, 24, 25, 27, 28, 29, 30, 31, 32, 33, 34, 35, 36, 37, 38, 39,
        40, 41, 42, 43, 44, 45, 46, 47, 48
    };

    /** ダメージ種別ID：768 → 767（ender_pearl・mace_smash は767に無いため -1）。
     * 768：ender_pearl=8、mace_smash=26、magic=27。 */
    private static final int[] DAMAGE_TYPE_REVERSE_768_MAP = {
        0, 1, 2, 3, 4, 5, 6, 7, -1, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18,
        19, 20, 21, 22, 23, 24, -1, 25, 26, 27, 28, 29, 30, 31, 32, 33, 34, 35,
        36, 37, 38, 39, 40, 41, 42, 43, 44, 45, 46
    };
"""

SMALL_REGISTRY_CONSTANTS = """
    /** 768のバナーパターン最大ID（43件。767と同一）。 */
    public static final int MAX_BANNER_PATTERN_768 = 42;

    /** 768の絵画バリアント最大ID（50件。767と同一）。 */
    public static final int MAX_PAINTING_VARIANT_768 = 49;

    /** 768の装飾パターン最大ID（18件。767と同一）。 */
    public static final int MAX_TRIM_PATTERN_768 = 17;
"""


def small_registry_accessors():
    out = []
    specs = [
        ("BannerPattern", "バナーパターン", "MAX_BANNER_PATTERN_767", "MAX_BANNER_PATTERN_768"),
        ("PaintingVariant", "絵画バリアント", "MAX_PAINTING_VARIANT_767", "MAX_PAINTING_VARIANT_768"),
        ("TrimPattern", "装飾パターン", "MAX_TRIM_PATTERN_767", "MAX_TRIM_PATTERN_768"),
    ]
    for name, jp, max767, max768 in specs:
        out.append(f"    /** 767の{jp}ID → 768の{jp}ID。767と768で同一のため恒等写像。範囲外は {{@link #DROP}}。 */")
        out.append(f"    public static int map{name}767To768(int id767) {{")
        out.append(f"        if (id767 < 0 || id767 > {max767}) {{")
        out.append(f"            logUnknownOnce(PROTOCOL_768, \"{jp}\", id767);")
        out.append("            return DROP;")
        out.append("        }")
        out.append("        return id767;")
        out.append("    }")
        out.append("")
        out.append(f"    /** 768の{jp}ID → 767の{jp}ID。767と768で同一のため恒等写像。範囲外は {{@link #DROP}}。 */")
        out.append(f"    public static int map{name}768To767(int id768) {{")
        out.append(f"        if (id768 < 0 || id768 > {max768}) {{")
        out.append(f"            logUnknownOnce(PROTOCOL_768, \"{jp}\", id768);")
        out.append("            return DROP;")
        out.append("        }")
        out.append("        return id768;")
        out.append("    }")
        out.append("")
        for src in ("763", "764", "765", "766"):
            # バナー・絵画は 763／764 のメソッドがある。トリムは 766／765
            # のメソッドしか無いため、763／764 は両方向とも 766 経由
            # （763／764／765 の並びは 766 と同じ）。
            via_fwd = src if name != "TrimPattern" or src in ("765", "766") else "766"
            via_rev = src if name != "TrimPattern" or src in ("765", "766") else "766"
            out.append(f"    /** {src}の{jp}ID → 768の{jp}ID（{src}の並びは766と同じ）。 */")
            out.append(f"    public static int map{name}{src}To768(int id{src}) {{")
            out.append(f"        return map{name}767To768(map{name}{via_fwd}To767(id{src}));")
            out.append("    }")
            out.append("")
            out.append(f"    /** 768の{jp}ID → {src}の{jp}ID（{src}の並びは766と同じ）。 */")
            out.append(f"    public static int map{name}768To{src}(int id768) {{")
            out.append(f"        return map{name}767To{via_rev}(map{name}768To767(id768));")
            out.append("    }")
            out.append("")
    return "\n".join(out)


def damage_accessors():
    out = []
    out.append("    /** 767のダメージ種別ID → 768のダメージ種別ID。範囲外は {@link #DROP}。 */")
    out.append("    public static int mapDamageType767To768(int id767) {")
    out.append("        if (id767 < 0 || id767 > MAX_DAMAGE_TYPE_767) {")
    out.append("            logUnknownOnce(PROTOCOL_768, \"ダメージ種別\", id767);")
    out.append("            return DROP;")
    out.append("        }")
    out.append("        return DAMAGE_TYPE_768_MAP[id767];")
    out.append("    }")
    out.append("")
    out.append("    /**")
    out.append("     * 768のダメージ種別ID → 767のダメージ種別ID。")
    out.append("     * 768にしか無い ender_pearl・mace_smash は 767に無いため {@link #DROP}。")
    out.append("     */")
    out.append("    public static int mapDamageType768To767(int id768) {")
    out.append("        if (id768 < 0 || id768 > MAX_DAMAGE_TYPE_768) {")
    out.append("            logUnknownOnce(PROTOCOL_768, \"ダメージ種別\", id768);")
    out.append("            return DROP;")
    out.append("        }")
    out.append("        return DAMAGE_TYPE_REVERSE_768_MAP[id768];")
    out.append("    }")
    out.append("")
    for src in ("763", "764", "765", "766"):
        # ダメージ種別は 766／765 のメソッドしか無いため、763／764 は
        # 両方向とも 766 経由（763／764／765 の並びは 766 と同じ）。
        via_fwd = src if src in ("765", "766") else "766"
        via_rev = src if src in ("765", "766") else "766"
        out.append(f"    /** {src}のダメージ種別ID → 768のダメージ種別ID（{src}の並びは766と同じ）。 */")
        out.append(f"    public static int mapDamageType{src}To768(int id{src}) {{")
        out.append(f"        return mapDamageType767To768(mapDamageType{via_fwd}To767(id{src}));")
        out.append("    }")
        out.append("")
        out.append(f"    /** 768のダメージ種別ID → {src}のダメージ種別ID（{src}の並びは766と同じ）。 */")
        out.append(f"    public static int mapDamageType768To{src}(int id768) {{")
        out.append(f"        return mapDamageType767To{via_rev}(mapDamageType768To767(id768));")
        out.append("    }")
        out.append("")
    return "\n".join(out)


JAVADOC_768 = """ *
 * <p>768（1.21.2／1.21.3）向けの表も同じ作り方で持つ（{@code *_768}）。
 * 768は767に近く、差分は次の通り（ViaVersion mapping-1.21.2.json と
 * mapping-1.21.json、minecraft-data 1.21.3 の突き合わせ。
 * minecraft-data に pc/1.21.2 が無いため 768 のデータは 1.21.3 のものを使う）。
 * ブロック状態は新634件（ペールオーク・クリエイキングハートの追加と
 * 挿入によるずれ）・削除0件。ブロックは新24件。アイテムは新42件
 * （バンドル16色・ペールオーク類・クリエイキングのスポーンエッグ・
 * 旗の模様2件）。エンティティは新22件・削除2件（boat／chest_boat が
 * 木材ごとの20種に分裂し、creaking／creaking_transient が追加）。
 * 音は新27件・削除2件（ヤギの角笛の音2件）。パーティクルは新2件
 * （trail／block_crumble）。メニュー・エフェクト・絵画・旗の模様・
 * トリム・ジュークボックスの曲・楽器は同一。属性は新1件（tempt_range）
 * と改名9件（generic.／player. の接頭辞が落ちたもの）。
 * エンチャントは 42件同士で並びは同一。データコンポーネント種別は
 * 新11件・削除1件（fire_resistant）。ブロックエンティティ種別は
 * creaking_heart が index 10 に挿入され、piston 以降が +1 ずれる。
 * バイオームは pale_garden が1件追加（minecraft-data 1.21.3 の
 * biomes.json では old_growth_spruce_taiga の次・plains の前に挿入）。
 * ダメージ種別は ender_pearl・mace_smash の2件が追加。
 * 767→768 の隣接表と 763／764／765／766→768 の合成表を持ち、逆向き
 * 768→767 は反転して作る。
"""
def insert_after(java, anchor, block, label):
    if anchor not in java:
        print(f"アンカーが見つからない（{label}）", file=sys.stderr)
        return None
    return java.replace(anchor, anchor + block, 1)


def main():
    if (not TABLES768_FILE.exists() or not TABLES768_COMPOSED_FILE.exists()
            or not all(p.exists() for p in TABLES768_COMPOSED_DOWN_FILES.values())):
        print("RegistryTables768.java / RegistryTables768ComposedUp.java / "
              "RegistryTables768Down*.java が無い。"
              "先に insert-768-tables.py を実行すること", file=sys.stderr)
        return 1
    java = JAVA_FILE.read_text(encoding="utf-8")
    checks = [
        ("PROTOCOL_768", "768 の定数・表・アクセサは既にある"),
        ("BLOCK_STATE_768_MAP", "768 の展開済み表は既にある"),
        ("mapBlockState767To768", "768 のアクセサは既にある"),
    ]
    for marker, msg in checks:
        if marker in java:
            print(msg, file=sys.stderr)
            return 1

    # import static。
    java = insert_after(java,
                        "import static io.detergentmc.protocol.RegistryTables767.*;\n",
                        "import static io.detergentmc.protocol.RegistryTables768.*;\n"
                        "import static io.detergentmc.protocol.RegistryTables768ComposedUp.*;\n"
                        "import static io.detergentmc.protocol.RegistryTables768Down763.*;\n"
                        "import static io.detergentmc.protocol.RegistryTables768Down764.*;\n"
                        "import static io.detergentmc.protocol.RegistryTables768Down765.*;\n"
                        "import static io.detergentmc.protocol.RegistryTables768Down766.*;\n",
                        "import static RegistryTables768")
    if java is None:
        return 1

    # クラスの javadoc に 768 の段落を追加（767 の段落の直後）。
    anchor = (" * 766→767 の隣接表と 763／764／765→767 の合成表を持ち、逆向き\n"
              " * 767→766 は反転して作る。\n")
    java = insert_after(java, anchor, JAVADOC_768, "javadoc")
    if java is None:
        return 1

    # 定数。
    java = insert_after(java,
                        "    public static final int PROTOCOL_767 = 767;\n",
                        "    public static final int PROTOCOL_768 = 768;\n", "PROTOCOL_768")
    if java is None:
        return 1
    java = insert_after(java,
                        "    public static final int MAX_EFFECT_767 = 38;\n",
                        CONSTANTS_MAX, "MAX_768")
    if java is None:
        return 1
    java = insert_after(java,
                        "    public static final int SPEED_EFFECT_767 = 0;\n",
                        CONSTANTS_FALLBACK, "768 の代替先")
    if java is None:
        return 1
    java = insert_after(java,
                        "    public static final int BLOCK_ENTITY_VAULT_766 = 43;\n",
                        CONSTANTS_BLOCK_ENTITY, "768 のブロックエンティティ種別")
    if java is None:
        return 1

    # 隣接表（767→768）。MENU_767_MAP の直後。
    anchor = ("    private static final int[] MENU_767_MAP =\n"
              "            expand(MENU_767_RANGES, MENU_767_OVERRIDES, MAX_MENU_766 + 1,\n"
              "                    CRAFTING_MENU_767);\n")
    java = insert_after(java, anchor, adjacent_maps() + "\n", "隣接表 768")
    if java is None:
        return 1

    # 合成表。MENU_767_TO_765_MAP の直後。
    anchor = ("    private static final int[] MENU_767_TO_765_MAP =\n"
              "            expand(MENU_767_TO_765_RANGES, MENU_767_TO_765_OVERRIDES,\n"
              "                    25, CRAFTING_MENU_765);\n")
    java = insert_after(java, anchor, "\n" + composed_maps() + "\n", "合成表 768")
    if java is None:
        return 1

    # 反転表（768→767）。MENU_UP_767_MAP の直後。
    anchor = ("    private static final int[] MENU_UP_767_MAP = invert(\n"
              "            MENU_767_RANGES, MENU_767_OVERRIDES,\n"
              "            MAX_MENU_766 + 1, MAX_MENU_767 + 1,\n"
              "            CRAFTING_MENU_766, null);\n")
    java = insert_after(java, anchor, up_maps() + "\n", "反転表 768")
    if java is None:
        return 1

    # 音の名前の表。
    anchor = ("    private static final Map<String, String> SOUND_NAME_FALLBACK_767_TO_764_MAP =\n"
              "            buildSoundNameMap(SOUND_NAME_FALLBACKS_767_TO_764);\n")
    java = insert_after(java, anchor, SOUND_NAME_MAPS, "音の名前の表 768")
    if java is None:
        return 1

    # ログ。
    anchor = ("    private static void logUnknownUp767Once(String kind, int id767) {\n"
              "        if (LOGGED_UNKNOWN.add(\"up767:\" + kind + \":\" + id767)) {\n"
              "            LOG.warning(\"767に無い\" + kind + \"の番号が来たため代替に落とします（767 id=\" + id767 + \"）。\");\n"
              "        }\n"
              "    }\n")
    java = insert_after(java, anchor, LOG_UP_768, "logUnknownUp768Once")
    if java is None:
        return 1

    # アクセサ（a）767→768。mapSoundName766To767 の直後。
    anchor = ("    public static String mapSoundName766To767(String name) {\n"
              "        return mapSoundNameComposed(name, SOUND_NAME_FALLBACK_766_TO_767_MAP);\n"
              "    }\n")
    java = insert_after(java, anchor, "\n" + adjacent_accessors() + "\n", "アクセサ 767→768")
    if java is None:
        return 1

    # アクセサ（b）768→767。mapSoundNameUp767 の直後。
    anchor = ("    public static String mapSoundNameUp767(String name) {\n"
              "        return mapSoundNameComposed(name, SOUND_NAME_FALLBACK_767_TO_766_MAP);\n"
              "    }\n")
    java = insert_after(java, anchor, "\n" + up_accessors() + "\n", "アクセサ 768→767")
    if java is None:
        return 1

    # アクセサ（c）763／764／765／766↔768。mapSoundName767To765 の直後。
    anchor = ("    public static String mapSoundName767To765(String name) {\n"
              "        return mapSoundNameComposed(name, SOUND_NAME_FALLBACK_767_TO_765_MAP);\n"
              "    }\n")
    java = insert_after(java, anchor, "\n" + composed_accessors() + "\n", "アクセサ 763/764/765/766↔768")
    if java is None:
        return 1

    # バナー・絵画・装飾パターン・ダメージ種別の定数。
    java = insert_after(java,
                        "    /** 767のバナーパターン最大ID（43件）。 */\n"
                        "    public static final int MAX_BANNER_PATTERN_767 = 42;\n",
                        SMALL_REGISTRY_CONSTANTS, "バナー等の768定数")
    if java is None:
        return 1
    anchor = ("    /** ダメージ種別ID：767 → 766（campfire・wind_charge は766に無いため -1）。 */\n"
              "    private static final int[] DAMAGE_TYPE_REVERSE_767_MAP = {\n"
              "        0, 1, 2, -1, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18,\n"
              "        19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32, 33, 34, 35, 36,\n"
              "        37, 38, 39, 40, 41, 42, -1, 43, 44\n"
              "    };\n")
    java = insert_after(java, anchor, DAMAGE_TYPE_768, "ダメージ種別768の表")
    if java is None:
        return 1

    # バナー・絵画・装飾パターン・ダメージ種別のアクセサ。
    anchor = ("    /** 767のバナーパターンID → 764のバナーパターンID（764の並びは766と同じ）。 */\n"
              "    public static int mapBannerPattern767To764(int id767) {\n"
              "        return mapBannerPattern767To766(id767);\n"
              "    }\n")
    banner_painting_trim = small_registry_accessors()
    # small_registry_accessors はバナー・絵画・トリムをひとまとめに出す
    # ため、バナーの直後に全部入れる（絵画・トリムの直後ではない）。
    java = insert_after(java, anchor, "\n" + banner_painting_trim + "\n", "バナー等の768アクセサ")
    if java is None:
        return 1
    anchor = ("    /** 767のダメージ種別ID → 765のダメージ種別ID（765の並びは766と同じ）。 */\n"
              "    public static int mapDamageType767To765(int id767) {\n"
              "        return mapDamageType767To766(id767);\n"
              "    }\n")
    java = insert_after(java, anchor, "\n" + damage_accessors() + "\n", "ダメージ種別768のアクセサ")
    if java is None:
        return 1

    # プロトコル番号で切り替える変換（担当2の配線用）に 768 を追加。
    dispatches = [
        ("mapBlockState", "mapBlockState765To767(id765)", "mapBlockState765To768(id765)"),
        ("mapItem", "mapItem765To767(id765)", "mapItem765To768(id765)"),
        ("mapItemReverse", "mapItem767To765(id)", "mapItem768To765(id)"),
        ("mapEntityType", "mapEntityType765To767(id765)", "mapEntityType765To768(id765)"),
        ("mapSound", "mapSound765To767(id765)", "mapSound765To768(id765)"),
        ("mapParticle", "mapParticle765To767(id765)", "mapParticle765To768(id765)"),
        ("mapBlock", "mapBlock765To767(id765)", "mapBlock765To768(id765)"),
        ("mapMenu", "mapMenu765To767(id765)", "mapMenu765To768(id765)"),
        ("mapEffect", "mapEffect765To767(id765)", "mapEffect765To768(id765)"),
        ("mapBlockEntityType", "mapBlockEntityType765To767(id765)", "mapBlockEntityType765To768(id765)"),
        ("mapSoundName", "mapSoundName765To767(name)", "mapSoundName765To768(name)"),
    ]
    for label, old_call, new_call in dispatches:
        if label == "mapItemReverse":
            old = ("        if (clientProtocol == PROTOCOL_767) {\n"
                   "            // 767クライアントのアイテムID → 765サーバーのアイテムID。\n"
                   "            // mapItemReverse767 は 767→766 のため、ここでは合成表を使う。\n"
                   "            return mapItem767To765(id);\n"
                   "        }\n")
            new = (old
                   + "        if (clientProtocol == PROTOCOL_768) {\n"
                   "            // 768クライアントのアイテムID → 765サーバーのアイテムID。\n"
                   "            // mapItemReverse768 は 768→767 のため、ここでは合成表を使う。\n"
                   "            return mapItem768To765(id);\n"
                   "        }\n")
            if old not in java:
                print("ディスパッチのアンカーが見つからない（mapItemReverse）", file=sys.stderr)
                return 1
            java = java.replace(old, new, 1)
            continue
        old = ("        if (clientProtocol == PROTOCOL_767) {\n"
               f"            return {old_call};\n"
               "        }\n")
        if old not in java:
            print(f"ディスパッチのアンカーが見つからない（{label}）", file=sys.stderr)
            return 1
        java = java.replace(old, old
                            + "        if (clientProtocol == PROTOCOL_768) {\n"
                            + f"            return {new_call};\n"
                            + "        }\n", 1)
    java = java.replace(
        "    // 765は素通し、766は766の表、767は767の表、それより古い未知の\n"
        "    // 番号は763の表を使う。767は765以上だが素通しではない（>765 の\n"
        "    // 分岐より先に 767 を判定すること）。",
        "    // 765は素通し、766は766の表、767は767の表、768は768の表、\n"
        "    // それより古い未知の番号は763の表を使う。767／768は765以上だが\n"
        "    // 素通しではない（>765 の分岐より先に 767／768 を判定すること）。",
        1)

    JAVA_FILE.write_text(java, encoding="utf-8")
    print("RegistryRemapper.java に 768 の定数・表・アクセサを挿入した")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
