#!/usr/bin/env python3
"""tools/insert-767-tables.py で入れた *_767 表の上に、RegistryRemapper.java
へ 767 対応の定数・展開済み表（*_767_MAP）・反転表（*_UP_767_MAP）・
アクセサを挿入する（1回だけの作業用）。

  - 定数：PROTOCOL_767、MAX_*_767、767 側の代替先。
  - 隣接表（766→767）: *_767_MAP と ITEM_REVERSE_767_MAP と
    ATTRIBUTE_767_MAP／ATTRIBUTE_REVERSE_767_MAP／ENCHANTMENT_767_MAP／
    ENCHANTMENT_REVERSE_767_MAP。
  - 合成表（763↔767、764↔767、765↔767）: *_763_TO_767_MAP など42件。
  - 逆向き（767→766）: *_UP_767_MAP（invert()。アイテムは
    ITEM_REVERSE_767_MAP をそのまま使う）。
  - 音の名前の表（String）: SOUND_NAME_FALLBACK_*_767_MAP など6件。
  - アクセサ：766↔767、767→766（上向き）、763／764／765↔767。
  - エフェクト：767 は 766 と同一の 39 件（0〜38）のため恒等写像。
    属性は 766 の 22 件 → 767 の 31 件、エンチャントは 42 件同士で
    並びが大きく変わる（minecraft-data の enchantments-1.21.1.json は
    アルファベット順。1.20.5 はコード順。実機の Registry Data での
    確認は統合テストで行う）。
"""

import re
import sys
from pathlib import Path

SCRIPT_DIR = Path(__file__).resolve().parent
REPO_ROOT = SCRIPT_DIR.parent.parent
JAVA_FILE = (REPO_ROOT / "detergentmc-protocol" / "src" / "main" / "java"
             / "io" / "detergentmc" / "protocol" / "RegistryRemapper.java")
TABLES767_FILE = (REPO_ROOT / "detergentmc-protocol" / "src" / "main" / "java"
                  / "io" / "detergentmc" / "protocol" / "RegistryTables767.java")


# kind: (Java メソッド名の語尾, RANGES の接頭辞, 日本語名,
#        763→767 の件数, 767→763 の件数, 764→767 の件数, 767→764 の件数,
#        765→767 の件数, 767→765 の件数,
#        763 側の代替先, 764 側の代替先, 765 側の代替先, 766 側の代替先,
#        767 側の代替先)
KINDS = [
    ("BlockState", "BLOCK_STATE", "ブロック状態", 24135, 26684, 24276, 26684, 26644, 26684,
     "STONE_BLOCK_STATE_763", "STONE_BLOCK_STATE_764", "STONE_BLOCK_STATE_765",
     "STONE_BLOCK_STATE_766", "STONE_BLOCK_STATE_767"),
    ("Item", "ITEM", "アイテム", 1255, 1333, 1255, 1333, 1312, 1333,
     "STONE_ITEM_763", "STONE_ITEM_764", "STONE_ITEM_765", "STONE_ITEM_766", "STONE_ITEM_767"),
    ("EntityType", "ENTITY_TYPE", "エンティティ種別", 124, 130, 124, 130, 126, 130,
     "PIG_ENTITY_TYPE_763", "PIG_ENTITY_TYPE_764", "PIG_ENTITY_TYPE_765",
     "PIG_ENTITY_TYPE_766", "PIG_ENTITY_TYPE_767"),
    ("Sound", "SOUND", "音", 1474, 1611, 1485, 1611, 1539, 1611,
     "GENERIC_SOUND_763", "GENERIC_SOUND_764", "GENERIC_SOUND_765",
     "GENERIC_SOUND_766", "GENERIC_SOUND_767"),
    ("Particle", "PARTICLE", "パーティクル", 95, 109, 95, 109, 101, 109,
     "AMBIENT_PARTICLE_763", "AMBIENT_PARTICLE_764", "AMBIENT_PARTICLE_765",
     "ENTITY_EFFECT_PARTICLE_766", "ENTITY_EFFECT_PARTICLE_767"),
    ("Block", "BLOCK", "ブロック", 1003, 1060, 1003, 1060, 1058, 1060,
     "STONE_BLOCK_763", "STONE_BLOCK_764", "STONE_BLOCK_765",
     "STONE_BLOCK_766", "STONE_BLOCK_767"),
    ("Menu", "MENU", "メニュー種別", 24, 25, 24, 25, 25, 25,
     "CRAFTING_MENU_763", "CRAFTING_MENU_764", "CRAFTING_MENU_765",
     "CRAFTING_MENU_766", "CRAFTING_MENU_767"),
]

CONSTANTS_MAX = """
    /** 767側の最大の番号（ViaVersion mapping-1.21.json の件数−1）。 */
    public static final int MAX_BLOCK_STATE_767 = 26683;
    public static final int MAX_ITEM_767 = 1332;
    public static final int MAX_ENTITY_TYPE_767 = 129;
    public static final int MAX_SOUND_767 = 1610;
    public static final int MAX_PARTICLE_767 = 108;
    public static final int MAX_BLOCK_767 = 1059;
    public static final int MAX_MENU_767 = 24;
    /** 767側のエフェクトも 0〜38（Speed=0。766と同一。minecraft-data の
        dataPaths.json で 1.21.1 の effects は pc/1.20.5 と確認）。 */
    public static final int MAX_EFFECT_767 = 38;
    /** 767側の属性は 0〜30（766の 0〜21 に9件追加）。 */
    public static final int MAX_ATTRIBUTE_767 = 30;
    /** 766側の属性は 0〜21。 */
    public static final int MAX_ATTRIBUTE_766 = 21;
    /** エンチャントは 766・767 とも 0〜41（42件。並びが大きく変わった。
        minecraft-data の enchantments-1.21.1.json はアルファベット順で、
        1.20.5 はコード順。実機の Registry Data での確認は統合テストで行う）。 */
    public static final int MAX_ENCHANTMENT_766 = 41;
    public static final int MAX_ENCHANTMENT_767 = 41;
    /** 765側のエンチャントは 0〜38（39件。766とは 0〜36 だけ同一で、
        766で増えた density／breach／wind_burst が 37〜39 に挿入され、
        mending／vanishing_curse が 40／41 へずれた。763／764 も同じ並び）。 */
    public static final int MAX_ENCHANTMENT_765 = 38;
"""

CONSTANTS_FALLBACK = """
    /** 767側の代替先（mapping-1.21.json の番号。Speed だけ 0ベースで 0）。 */
    public static final int STONE_BLOCK_STATE_767 = 1;
    public static final int AIR_BLOCK_STATE_767 = 0;
    public static final int STONE_ITEM_767 = 1;
    public static final int PIG_ENTITY_TYPE_767 = 77;
    public static final int GENERIC_SOUND_767 = 1361; // block.stone.break
    /** 767でも ambient_entity_effect は無いため、パーティクルの代替は entity_effect。 */
    public static final int ENTITY_EFFECT_PARTICLE_767 = 20;
    public static final int STONE_BLOCK_767 = 1;
    public static final int CRAFTING_MENU_767 = 12;
    public static final int SPEED_EFFECT_767 = 0;
"""


def adjacent_maps():
    lines = []
    for name, prefix, jp, *_ in KINDS:
        lines.append(
            f"    private static final int[] {prefix}_767_MAP =\n"
            f"            expand({prefix}_767_RANGES, {prefix}_767_OVERRIDES, MAX_{prefix}_766 + 1,\n"
            f"                    {KINDS[[k[0] for k in KINDS].index(name)][13]});")
    lines.append(
        "    private static final int[] ITEM_REVERSE_767_MAP =\n"
        "            expand(ITEM_REVERSE_767_RANGES, ITEM_REVERSE_767_OVERRIDES, 1333, -1);")
    lines.append(
        "    private static final int[] ATTRIBUTE_767_MAP =\n"
        "            expand(ATTRIBUTE_767_RANGES, ATTRIBUTE_767_OVERRIDES, MAX_ATTRIBUTE_766 + 1, DROP);")
    lines.append(
        "    private static final int[] ATTRIBUTE_REVERSE_767_MAP =\n"
        "            expand(ATTRIBUTE_REVERSE_767_RANGES, ATTRIBUTE_REVERSE_767_OVERRIDES,\n"
        "                    MAX_ATTRIBUTE_767 + 1, DROP);")
    lines.append(
        "    private static final int[] ENCHANTMENT_767_MAP =\n"
        "            expand(ENCHANTMENT_767_RANGES, ENCHANTMENT_767_OVERRIDES,\n"
        "                    MAX_ENCHANTMENT_766 + 1, DROP);")
    lines.append(
        "    private static final int[] ENCHANTMENT_REVERSE_767_MAP =\n"
        "            expand(ENCHANTMENT_REVERSE_767_RANGES, ENCHANTMENT_REVERSE_767_OVERRIDES,\n"
        "                    MAX_ENCHANTMENT_767 + 1, DROP);")
    return "\n".join(lines)


def composed_maps():
    # (接尾辞, 件数の位置, 代替先の位置)。件数・代替先は KINDS の
    # タプルから取る（763_TO_767: 件数 index 3, 代替先 767=index 13）。
    pairs = [
        ("763_TO_767", 3, 13), ("767_TO_763", 4, 9),
        ("764_TO_767", 5, 13), ("767_TO_764", 6, 10),
        ("765_TO_767", 7, 13), ("767_TO_765", 8, 11),
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
    for name, prefix, jp, *_ in KINDS:
        if prefix == "ITEM":
            continue  # アイテムは ITEM_REVERSE_767_MAP を使う。
        lines.append(
            f"    private static final int[] {prefix}_UP_767_MAP = invert(\n"
            f"            {prefix}_767_RANGES, {prefix}_767_OVERRIDES,\n"
            f"            MAX_{prefix}_766 + 1, MAX_{prefix}_767 + 1,\n"
            f"            {KINDS[[k[0] for k in KINDS].index(name)][12]}, null);")
    return "\n".join(lines)


SOUND_NAME_MAPS = """
    private static final Map<String, String> SOUND_NAME_FALLBACK_767_MAP =
            buildSoundNameMap(SOUND_NAME_FALLBACKS_767);
    private static final Map<String, String> SOUND_NAME_FALLBACK_766_TO_767_MAP =
            buildSoundNameMap(SOUND_NAME_FALLBACKS_766_TO_767);
    private static final Map<String, String> SOUND_NAME_FALLBACK_767_TO_766_MAP =
            buildSoundNameMap(SOUND_NAME_FALLBACKS_767_TO_766);
    private static final Map<String, String> SOUND_NAME_FALLBACK_767_TO_765_MAP =
            buildSoundNameMap(SOUND_NAME_FALLBACKS_767_TO_765);
    private static final Map<String, String> SOUND_NAME_FALLBACK_767_TO_763_MAP =
            buildSoundNameMap(SOUND_NAME_FALLBACKS_767_TO_763);
    private static final Map<String, String> SOUND_NAME_FALLBACK_767_TO_764_MAP =
            buildSoundNameMap(SOUND_NAME_FALLBACKS_767_TO_764);
"""

LOG_UP_767 = """
    private static void logUnknownUp767Once(String kind, int id767) {
        if (LOGGED_UNKNOWN.add("up767:" + kind + ":" + id767)) {
            LOG.warning("767に無い" + kind + "の番号が来たため代替に落とします（767 id=" + id767 + "）。");
        }
    }
"""


def adjacent_accessors():
    out = ["    // ------------------------------------------------------------------",
           "    // 766→767。上の *_767 表を使う。ブロック状態・ブロック・",
           "    // エンティティ・パーティクル・メニューは 766 と 767 で同一の",
           "    // ため恒等写像に近く、アイテムは音楽ディスク3件が挿入で",
           "    // ずれる。音は改名2件と新6件。属性は新9件、エンチャントは",
           "    // 並び替え（40件が移動）。エフェクトは同一39件。",
           "    // ------------------------------------------------------------------",
           ""]
    for name, prefix, jp, *_ in KINDS:
        fb767 = KINDS[[k[0] for k in KINDS].index(name)][13]
        out.append(f"    /** 766の{jp}ID → 767の{jp}ID。範囲外は{fb767} に相当する代替。 */")
        out.append(f"    public static int map{name}766To767(int id766) {{")
        out.append(f"        if (id766 < 0 || id766 > MAX_{prefix}_766) {{")
        out.append(f"            logUnknownOnce(PROTOCOL_767, \"{jp}\", id766);")
        out.append(f"            return {fb767};")
        out.append("        }")
        out.append(f"        return {prefix}_767_MAP[id766];")
        out.append("    }")
        out.append("")
    out.append("    /**")
    out.append("     * 767のアイテムID → 766のアイテムID（サーバー行きスロット用）。")
    out.append("     * 767にしか無いアイテム（音楽ディスク3件）は 766の石へ。")
    out.append("     * 範囲外はそのまま返す。")
    out.append("     */")
    out.append("    public static int mapItemReverse767(int id767) {")
    out.append("        if (id767 < 0 || id767 >= ITEM_REVERSE_767_MAP.length) {")
    out.append("            logUnknownOnce(PROTOCOL_767, \"アイテム（逆変換）\", id767);")
    out.append("            return id767;")
    out.append("        }")
    out.append("        int mapped = ITEM_REVERSE_767_MAP[id767];")
    out.append("        return mapped < 0 ? id767 : mapped;")
    out.append("    }")
    out.append("")
    out.append("    /**")
    out.append("     * 766のエフェクトID → 767のエフェクトID。766も767も0ベース")
    out.append("     * （Speed は両方 0）で 0〜38 は同一のため恒等写像。")
    out.append("     * 範囲外は Speed（0）。")
    out.append("     */")
    out.append("    public static int mapEffect766To767(int id766) {")
    out.append("        if (id766 < 0 || id766 > MAX_EFFECT_766) {")
    out.append("            logUnknownOnce(PROTOCOL_767, \"エフェクト\", id766);")
    out.append("            return SPEED_EFFECT_767;")
    out.append("        }")
    out.append("        return id766;")
    out.append("    }")
    out.append("")
    out.append("    /**")
    out.append("     * 766のブロックエンティティ種別ID → 767の種別ID。")
    out.append("     * vault（43）は767にもあるためそのまま。")
    out.append("     * 範囲外の種別は NBT の中身が種別専用のため安全に代替できず")
    out.append("     * {@link #DROP}。")
    out.append("     */")
    out.append("    public static int mapBlockEntityType766To767(int id766) {")
    out.append("        if (id766 < 0 || id766 > BLOCK_ENTITY_VAULT_766) {")
    out.append("            logUnknownOnce(PROTOCOL_767, \"ブロックエンティティ種別\", id766);")
    out.append("            return DROP;")
    out.append("        }")
    out.append("        return id766;")
    out.append("    }")
    out.append("")
    out.append("    /**")
    out.append("     * 音の名前（直書き・Stop Sound 用）を767向けに変換する。")
    out.append("     * {@code minecraft:} の有無はそのまま保つ。767で改名された")
    out.append("     * trial_spawner の音2件は新しい名前にする。")
    out.append("     * それ以外の音は名前が同じためそのまま通す。")
    out.append("     */")
    out.append("    public static String mapSoundName766To767(String name) {")
    out.append("        return mapSoundNameComposed(name, SOUND_NAME_FALLBACK_766_TO_767_MAP);")
    out.append("    }")
    out.append("")
    # 属性とエンチャント。
    out.append("    /** 766の属性ID → 767の属性ID。範囲外は {@link #DROP}。 */")
    out.append("    public static int mapAttribute766To767(int id766) {")
    out.append("        if (id766 < 0 || id766 > MAX_ATTRIBUTE_766) {")
    out.append("            logUnknownOnce(PROTOCOL_767, \"属性\", id766);")
    out.append("            return DROP;")
    out.append("        }")
    out.append("        return ATTRIBUTE_767_MAP[id766];")
    out.append("    }")
    out.append("")
    out.append("    /**")
    out.append("     * 767の属性ID → 766の属性ID。")
    out.append("     * 767にしか無い属性（新9件）は 766に無いため {@link #DROP}。")
    out.append("     */")
    out.append("    public static int mapAttribute767To766(int id767) {")
    out.append("        if (id767 < 0 || id767 > MAX_ATTRIBUTE_767) {")
    out.append("            logUnknownOnce(PROTOCOL_767, \"属性\", id767);")
    out.append("            return DROP;")
    out.append("        }")
    out.append("        return ATTRIBUTE_REVERSE_767_MAP[id767];")
    out.append("    }")
    out.append("")
    out.append("    /** 766の属性ID → 属性の名前（Update Attributes の 765向け変換用）。 */")
    out.append("    public static String attributeName766(int id766) {")
    out.append("        if (id766 < 0 || id766 >= ATTRIBUTE_766_NAMES.length) {")
    out.append("            return null;")
    out.append("        }")
    out.append("        return ATTRIBUTE_766_NAMES[id766];")
    out.append("    }")
    out.append("")
    out.append("    /** 767の属性ID → 属性の名前（Update Attributes の 765向け変換用）。 */")
    out.append("    public static String attributeName767(int id767) {")
    out.append("        if (id767 < 0 || id767 >= ATTRIBUTE_767_NAMES.length) {")
    out.append("            return null;")
    out.append("        }")
    out.append("        return ATTRIBUTE_767_NAMES[id767];")
    out.append("    }")
    out.append("")
    out.append("    /**")
    out.append("     * 属性の名前 → 767の属性ID（765／763／764 は属性が名前形式のため、")
    out.append("     * 767向けに変換するときに使う）。無い名前は {@link #DROP}。")
    out.append("     */")
    out.append("    public static int attributeId767(String name) {")
    out.append("        if (name == null) {")
    out.append("            return DROP;")
    out.append("        }")
    out.append("        for (int i = 0; i < ATTRIBUTE_767_NAMES.length; i++) {")
    out.append("            if (ATTRIBUTE_767_NAMES[i].equals(name)) {")
    out.append("                return i;")
    out.append("            }")
    out.append("        }")
    out.append("        return DROP;")
    out.append("    }")
    out.append("")
    out.append("    /** 766のエンチャントID → 767のエンチャントID。範囲外は {@link #DROP}。 */")
    out.append("    public static int mapEnchantment766To767(int id766) {")
    out.append("        if (id766 < 0 || id766 > MAX_ENCHANTMENT_766) {")
    out.append("            logUnknownOnce(PROTOCOL_767, \"エンチャント\", id766);")
    out.append("            return DROP;")
    out.append("        }")
    out.append("        return ENCHANTMENT_767_MAP[id766];")
    out.append("    }")
    out.append("")
    out.append("    /** 767のエンチャントID → 766のエンチャントID。範囲外は {@link #DROP}。 */")
    out.append("    public static int mapEnchantment767To766(int id767) {")
    out.append("        if (id767 < 0 || id767 > MAX_ENCHANTMENT_767) {")
    out.append("            logUnknownOnce(PROTOCOL_767, \"エンチャント\", id767);")
    out.append("            return DROP;")
    out.append("        }")
    out.append("        return ENCHANTMENT_REVERSE_767_MAP[id767];")
    out.append("    }")
    out.append("")
    out.append("    /**")
    out.append("     * 765のエンチャントID → 766のエンチャントID。765と766の並びは")
    out.append("     * 0〜36 だけ同一で、766では density（37）・breach（38）・")
    out.append("     * wind_burst（39）が mending（765=37）の前に挿入され、mending と")
    out.append("     * vanishing_curse が 3つ後ろへずれた（765=37→766=40、765=38→766=41）。")
    out.append("     * minecraft-data の enchantments-1.20.2.json（765）と")
    out.append("     * enchantments-1.20.5.json（766）の突き合わせによる。")
    out.append("     * 範囲外は {@link #DROP}。")
    out.append("     */")
    out.append("    public static int mapEnchantment765To766(int id765) {")
    out.append("        if (id765 < 0 || id765 > MAX_ENCHANTMENT_765) {")
    out.append("            logUnknownOnce(PROTOCOL_767, \"エンチャント\", id765);")
    out.append("            return DROP;")
    out.append("        }")
    out.append("        return id765 <= 36 ? id765 : id765 + 3;")
    out.append("    }")
    out.append("")
    out.append("    /**")
    out.append("     * 766のエンチャントID → 765のエンチャントID。density（37）・")
    out.append("     * breach（38）・wind_burst（39）は 765に無いため {@link #DROP}。")
    out.append("     * mending（40）→ 37、vanishing_curse（41）→ 38。")
    out.append("     * 範囲外も {@link #DROP}。")
    out.append("     */")
    out.append("    public static int mapEnchantment766To765(int id766) {")
    out.append("        if (id766 < 0 || id766 > MAX_ENCHANTMENT_766) {")
    out.append("            logUnknownOnce(PROTOCOL_767, \"エンチャント\", id766);")
    out.append("            return DROP;")
    out.append("        }")
    out.append("        if (id766 <= 36) {")
    out.append("            return id766;")
    out.append("        }")
    out.append("        if (id766 == 40) {")
    out.append("            return 37;")
    out.append("        }")
    out.append("        if (id766 == 41) {")
    out.append("            return 38;")
    out.append("        }")
    out.append("        return DROP;")
    out.append("    }")
    out.append("")
    out.append("    /**")
    out.append("     * 765のエンチャントID → 767のエンチャントID。765→766 の")
    out.append("     * 挿入ずれ（{@link #mapEnchantment765To766}）を経由して")
    out.append("     * 766→767 の表を引く。範囲外は {@link #DROP}。")
    out.append("     * 763／764 のエンチャントの並びは 765 と同一（minecraft-data の")
    out.append("     * enchantments-1.20.json＝enchantments-1.20.2.json で確認）のため、")
    out.append("     * 763／764 向けも同じ変換を使う。")
    out.append("     */")
    out.append("    public static int mapEnchantment765To767(int id765) {")
    out.append("        int id766 = mapEnchantment765To766(id765);")
    out.append("        return id766 == DROP ? DROP : mapEnchantment766To767(id766);")
    out.append("    }")
    out.append("")
    out.append("    /** 763のエンチャントID → 767のエンチャントID（765と同じ並び）。 */")
    out.append("    public static int mapEnchantment763To767(int id763) {")
    out.append("        return mapEnchantment765To767(id763);")
    out.append("    }")
    out.append("")
    out.append("    /** 764のエンチャントID → 767のエンチャントID（765と同じ並び）。 */")
    out.append("    public static int mapEnchantment764To767(int id764) {")
    out.append("        return mapEnchantment765To767(id764);")
    out.append("    }")
    out.append("")
    out.append("    /**")
    out.append("     * 767のエンチャントID → 765のエンチャントID。767→766 の逆変換の")
    out.append("     * 結果を 766→765 の挿入ずれ（{@link #mapEnchantment766To765}）で")
    out.append("     * 戻す。density／breach／wind_burst は 765に無いため")
    out.append("     * {@link #DROP}。範囲外も {@link #DROP}。")
    out.append("     */")
    out.append("    public static int mapEnchantment767To765(int id767) {")
    out.append("        int id766 = mapEnchantment767To766(id767);")
    out.append("        return id766 == DROP ? DROP : mapEnchantment766To765(id766);")
    out.append("    }")
    out.append("")
    out.append("    /** 767のエンチャントID → 763のエンチャントID（765と同じ並び）。 */")
    out.append("    public static int mapEnchantment767To763(int id767) {")
    out.append("        return mapEnchantment767To765(id767);")
    out.append("    }")
    out.append("")
    out.append("    /** 767のエンチャントID → 764のエンチャントID（765と同じ並び）。 */")
    out.append("    public static int mapEnchantment767To764(int id767) {")
    out.append("        return mapEnchantment767To765(id767);")
    out.append("    }")
    return "\n".join(out)


def up_accessors():
    out = ["    // ------------------------------------------------------------------",
           "    // 逆向き（767→766）。上の *_767 表を反転して作った表を使う。",
           "    // アイテムだけは生成スクリプトが名前から作った既存の逆変換表",
           "    // （ITEM_REVERSE_767_MAP）をそのまま使う（反転した表と完全一致",
           "    // することはテストで確認している）。属性とエンチャントの逆向きは",
           "    // mapAttribute767To766／mapEnchantment767To766 を参照。",
           "    // ------------------------------------------------------------------",
           ""]
    for name, prefix, jp, *_ in KINDS:
        fb766 = KINDS[[k[0] for k in KINDS].index(name)][12]
        if prefix == "ITEM":
            out.append("    /**")
            out.append("     * 767のアイテムID → 766のアイテムID。")
            out.append("     * 767にしか無いアイテム（音楽ディスク3件）は石へ。範囲外は石（1）。")
            out.append("     */")
            out.append("    public static int mapItemUp767(int id767) {")
            out.append("        if (id767 < 0 || id767 >= ITEM_REVERSE_767_MAP.length) {")
            out.append("            logUnknownUp767Once(\"アイテム\", id767);")
            out.append("            return STONE_ITEM_766;")
            out.append("        }")
            out.append("        int mapped = ITEM_REVERSE_767_MAP[id767];")
            out.append("        return mapped < 0 ? STONE_ITEM_766 : mapped;")
            out.append("    }")
            out.append("")
            continue
        out.append(f"    /** 767の{jp}ID → 766の{jp}ID。範囲外は{fb766} に相当する代替。 */")
        out.append(f"    public static int map{name}Up767(int id767) {{")
        out.append(f"        if (id767 < 0 || id767 > MAX_{prefix}_767) {{")
        out.append(f"            logUnknownUp767Once(\"{jp}\", id767);")
        out.append(f"            return {fb766};")
        out.append("        }")
        out.append(f"        return {prefix}_UP_767_MAP[id767];")
        out.append("    }")
        out.append("")
    out.append("    /**")
    out.append("     * 767のエフェクトID → 766のエフェクトID。0〜38 は同一のため恒等写像。")
    out.append("     * 範囲外は Speed（0）。")
    out.append("     */")
    out.append("    public static int mapEffectUp767(int id767) {")
    out.append("        if (id767 < 0 || id767 > MAX_EFFECT_767) {")
    out.append("            logUnknownUp767Once(\"エフェクト\", id767);")
    out.append("            return SPEED_EFFECT_766;")
    out.append("        }")
    out.append("        return id767;")
    out.append("    }")
    out.append("")
    out.append("    /**")
    out.append("     * 767のブロックエンティティ種別ID → 766の種別ID。")
    out.append("     * vault（43）は766にもあるためそのまま。")
    out.append("     * 範囲外の種別は NBT の中身が種別専用のため安全に代替できず")
    out.append("     * {@link #DROP}。")
    out.append("     */")
    out.append("    public static int mapBlockEntityTypeUp767(int id767) {")
    out.append("        if (id767 < 0 || id767 > BLOCK_ENTITY_VAULT_766) {")
    out.append("            logUnknownUp767Once(\"ブロックエンティティ種別\", id767);")
    out.append("            return DROP;")
    out.append("        }")
    out.append("        return id767;")
    out.append("    }")
    out.append("")
    out.append("    /**")
    out.append("     * 音の名前を766向けに変換する。767の音の名前のうち 766に無いものは")
    out.append("     * 代替の音の名前にする（数値の反転表と一致させる）。767で改名された")
    out.append("     * trial_spawner の音2件は 766の古い名前に戻し、767の新音")
    out.append("     * （音楽ディスク3件・vault の拒否音）は石の破壊音へ。")
    out.append("     * {@code null} は石の破壊音にする。")
    out.append("     */")
    out.append("    public static String mapSoundNameUp767(String name) {")
    out.append("        return mapSoundNameComposed(name, SOUND_NAME_FALLBACK_767_TO_766_MAP);")
    out.append("    }")
    return "\n".join(out)


def composed_accessors():
    out = ["    // ------------------------------------------------------------------",
           "    // 離れた組み合わせの合成表（763↔767、764↔767、765↔767）。上の",
           "    // *_763_TO_767／*_767_TO_763／*_764_TO_767／*_767_TO_764／",
           "    // *_765_TO_767／*_767_TO_765 表（合成済みの1つの表）を直接引く。",
           "    // 二段変換はしない。エフェクトとブロックエンティティ種別は",
           "    // 表を作らず、数値の規則と同一の並びで扱う。",
           "    // ------------------------------------------------------------------",
           ""]
    pairs = [
        ("763", "763", "763", 9, 13, "763→767", "767→763"),
        ("764", "764", "764", 10, 13, "764→767", "767→764"),
        ("765", "765", "765", 11, 13, "765→767", "767→765"),
    ]
    for src, src_short, _label, fb_src_idx, fb_767_idx, up_label, down_label in pairs:
        for kind in KINDS:
            name, prefix, jp = kind[0], kind[1], kind[2]
            fb_src = kind[fb_src_idx]
            fb_767 = kind[fb_767_idx]
            # src → 767
            out.append(f"    /** {src}の{jp}ID → 767の{jp}ID。範囲外は{fb_767} に相当する代替。 */")
            out.append(f"    public static int map{name}{src}To767(int id{src}) {{")
            out.append(f"        if (id{src} < 0 || id{src} >= {prefix}_{src}_TO_767_MAP.length) {{")
            out.append(f"            logUnknownComposedOnce(\"{up_label}\", \"{jp}\", id{src});")
            out.append(f"            return {fb_767};")
            out.append("        }")
            out.append(f"        return {prefix}_{src}_TO_767_MAP[id{src}];")
            out.append("    }")
            out.append("")
            # 767 → src
            out.append(f"    /** 767の{jp}ID → {src}の{jp}ID。範囲外は{fb_src} に相当する代替。 */")
            out.append(f"    public static int map{name}767To{src}(int id767) {{")
            out.append(f"        if (id767 < 0 || id767 >= {prefix}_767_TO_{src}_MAP.length) {{")
            out.append(f"            logUnknownComposedOnce(\"{down_label}\", \"{jp}\", id767);")
            out.append(f"            return {fb_src};")
            out.append("        }")
            out.append(f"        return {prefix}_767_TO_{src}_MAP[id767];")
            out.append("    }")
            out.append("")
        # エフェクトとブロックエンティティ種別と音の名前。
        if src == "763":
            out.append("    /**")
            out.append("     * 763のエフェクトID → 767のエフェクトID。763は1ベース")
            out.append("     * （Speed=1）のため 1 引く。範囲外は Speed（0）。")
            out.append("     */")
            out.append("    public static int mapEffect763To767(int id763) {")
            out.append("        if (id763 < 1 || id763 > MAX_EFFECT_765 + 1) {")
            out.append("            logUnknownComposedOnce(\"763→767\", \"エフェクト\", id763);")
            out.append("            return SPEED_EFFECT_767;")
            out.append("        }")
            out.append("        return id763 - 1;")
            out.append("    }")
            out.append("")
            out.append("    /**")
            out.append("     * 767のエフェクトID → 763のエフェクトID。763は1ベース")
            out.append("     * （Speed=1）のため 1 足す。767で増えた 33〜38 は")
            out.append("     * 763に無いため Speed（1）へ。範囲外も Speed（1）。")
            out.append("     */")
            out.append("    public static int mapEffect767To763(int id767) {")
            out.append("        if (id767 < 0 || id767 > MAX_EFFECT_767) {")
            out.append("            logUnknownComposedOnce(\"767→763\", \"エフェクト\", id767);")
            out.append("            return SPEED_EFFECT_763;")
            out.append("        }")
            out.append("        return id767 <= MAX_EFFECT_765 ? id767 + 1 : SPEED_EFFECT_763;")
            out.append("    }")
            out.append("")
            out.append("    /**")
            out.append("     * 763のブロックエンティティ種別ID → 767の種別ID。")
            out.append("     * 763の種別は 0〜40 で767と同じ並びのためそのまま。")
            out.append("     * 763に無い番号（41以上）は767側の種別に対応付けられないため")
            out.append("     * {@link #DROP}。")
            out.append("     */")
            out.append("    public static int mapBlockEntityType763To767(int id763) {")
            out.append("        if (id763 < 0 || id763 > BLOCK_ENTITY_TRIAL_SPAWNER_765 - 2) {")
            out.append("            logUnknownComposedOnce(\"763→767\", \"ブロックエンティティ種別\", id763);")
            out.append("            return DROP;")
            out.append("        }")
            out.append("        return id763;")
            out.append("    }")
            out.append("")
            out.append("    /**")
            out.append("     * 767のブロックエンティティ種別ID → 763の種別ID。")
            out.append("     * 767の種別は 0〜40 で763と同じ並びのためそのまま。")
            out.append("     * crafter（41）・trial_spawner（42）・vault（43）は763に無い")
            out.append("     * ため {@link #DROP}。")
            out.append("     */")
            out.append("    public static int mapBlockEntityType767To763(int id767) {")
            out.append("        if (id767 < 0 || id767 > BLOCK_ENTITY_TRIAL_SPAWNER_765 - 2) {")
            out.append("            logUnknownComposedOnce(\"767→763\", \"ブロックエンティティ種別\", id767);")
            out.append("            return DROP;")
            out.append("        }")
            out.append("        return id767;")
            out.append("    }")
            out.append("")
            out.append("    /**")
            out.append("     * 音の名前を767向けに変換する。763の音の名前はすべて767にもある")
            out.append("     * （767は763の音をすべて含む）ため、そのまま返す。")
            out.append("     * {@code null} は石の破壊音にする。")
            out.append("     */")
            out.append("    public static String mapSoundName763To767(String name) {")
            out.append("        if (name == null) {")
            out.append("            return \"minecraft:block.stone.break\";")
            out.append("        }")
            out.append("        return name;")
            out.append("    }")
            out.append("")
            out.append("    /**")
            out.append("     * 音の名前を763向けに変換する。767の音の名前のうち 763に無いものは")
            out.append("     * 代替の音の名前にする（数値の合成表と一致させる）。")
            out.append("     * {@code null} は石の破壊音にする。")
            out.append("     */")
            out.append("    public static String mapSoundName767To763(String name) {")
            out.append("        return mapSoundNameComposed(name, SOUND_NAME_FALLBACK_767_TO_763_MAP);")
            out.append("    }")
        elif src == "764":
            out.append("    /**")
            out.append("     * 764のエフェクトID → 767のエフェクトID。764も767も0ベース")
            out.append("     * （Speed は両方 0）で 0〜32 は同一のため恒等写像。")
            out.append("     * 範囲外は Speed（0）。")
            out.append("     */")
            out.append("    public static int mapEffect764To767(int id764) {")
            out.append("        if (id764 < 0 || id764 > MAX_EFFECT_764) {")
            out.append("            logUnknownComposedOnce(\"764→767\", \"エフェクト\", id764);")
            out.append("            return SPEED_EFFECT_767;")
            out.append("        }")
            out.append("        return id764;")
            out.append("    }")
            out.append("")
            out.append("    /**")
            out.append("     * 767のエフェクトID → 764のエフェクトID。0〜32 は同一のため恒等写像。")
            out.append("     * 767で増えた 33〜38 は 764に無いため Speed（0）へ。")
            out.append("     * 範囲外も Speed（0）。")
            out.append("     */")
            out.append("    public static int mapEffect767To764(int id767) {")
            out.append("        if (id767 < 0 || id767 > MAX_EFFECT_767) {")
            out.append("            logUnknownComposedOnce(\"767→764\", \"エフェクト\", id767);")
            out.append("            return SPEED_EFFECT_764;")
            out.append("        }")
            out.append("        return id767 <= MAX_EFFECT_764 ? id767 : SPEED_EFFECT_764;")
            out.append("    }")
            out.append("")
            out.append("    /**")
            out.append("     * 764のブロックエンティティ種別ID → 767の種別ID。")
            out.append("     * 764の種別は 0〜40 で767と同じ並びのためそのまま。")
            out.append("     * 764に無い番号（41以上）は767側の種別に対応付けられないため")
            out.append("     * {@link #DROP}。")
            out.append("     */")
            out.append("    public static int mapBlockEntityType764To767(int id764) {")
            out.append("        if (id764 < 0 || id764 > BLOCK_ENTITY_TRIAL_SPAWNER_765 - 2) {")
            out.append("            logUnknownComposedOnce(\"764→767\", \"ブロックエンティティ種別\", id764);")
            out.append("            return DROP;")
            out.append("        }")
            out.append("        return id764;")
            out.append("    }")
            out.append("")
            out.append("    /**")
            out.append("     * 767のブロックエンティティ種別ID → 764の種別ID。")
            out.append("     * 767の種別は 0〜40 で764と同じ並びのためそのまま。")
            out.append("     * crafter（41）・trial_spawner（42）・vault（43）は764に無い")
            out.append("     * ため {@link #DROP}。")
            out.append("     */")
            out.append("    public static int mapBlockEntityType767To764(int id767) {")
            out.append("        if (id767 < 0 || id767 > BLOCK_ENTITY_TRIAL_SPAWNER_765 - 2) {")
            out.append("            logUnknownComposedOnce(\"767→764\", \"ブロックエンティティ種別\", id767);")
            out.append("            return DROP;")
            out.append("        }")
            out.append("        return id767;")
            out.append("    }")
            out.append("")
            out.append("    /**")
            out.append("     * 音の名前を767向けに変換する。764の音の名前はすべて767にもある")
            out.append("     * （767は764の音をすべて含む）ため、そのまま返す。")
            out.append("     * {@code null} は石の破壊音にする。")
            out.append("     */")
            out.append("    public static String mapSoundName764To767(String name) {")
            out.append("        if (name == null) {")
            out.append("            return \"minecraft:block.stone.break\";")
            out.append("        }")
            out.append("        return name;")
            out.append("    }")
            out.append("")
            out.append("    /**")
            out.append("     * 音の名前を764向けに変換する。767の音の名前のうち 764に無いものは")
            out.append("     * 代替の音の名前にする（数値の合成表と一致させる）。")
            out.append("     * {@code null} は石の破壊音にする。")
            out.append("     */")
            out.append("    public static String mapSoundName767To764(String name) {")
            out.append("        return mapSoundNameComposed(name, SOUND_NAME_FALLBACK_767_TO_764_MAP);")
            out.append("    }")
        else:  # 765
            out.append("    /**")
            out.append("     * 765のエフェクトID → 767のエフェクトID。765も767も0ベース")
            out.append("     * （Speed は両方 0）で 0〜32 は同一のため恒等写像。")
            out.append("     * 範囲外は Speed（0）。")
            out.append("     */")
            out.append("    public static int mapEffect765To767(int id765) {")
            out.append("        if (id765 < 0 || id765 > MAX_EFFECT_765) {")
            out.append("            logUnknownComposedOnce(\"765→767\", \"エフェクト\", id765);")
            out.append("            return SPEED_EFFECT_767;")
            out.append("        }")
            out.append("        return id765;")
            out.append("    }")
            out.append("")
            out.append("    /**")
            out.append("     * 767のエフェクトID → 765のエフェクトID。0〜32 は同一のため恒等写像。")
            out.append("     * 767で増えた 33〜38 は 765に無いため Speed（0）へ。")
            out.append("     * 範囲外も Speed（0）。")
            out.append("     */")
            out.append("    public static int mapEffect767To765(int id767) {")
            out.append("        if (id767 < 0 || id767 > MAX_EFFECT_767) {")
            out.append("            logUnknownComposedOnce(\"767→765\", \"エフェクト\", id767);")
            out.append("            return SPEED_EFFECT_765;")
            out.append("        }")
            out.append("        return id767 <= MAX_EFFECT_765 ? id767 : SPEED_EFFECT_765;")
            out.append("    }")
            out.append("")
            out.append("    /**")
            out.append("     * 765のブロックエンティティ種別ID → 767の種別ID。")
            out.append("     * crafter（41）と trial_spawner（42）は767にもあるためそのまま。")
            out.append("     * 範囲外の種別は NBT の中身が種別専用のため安全に代替できず")
            out.append("     * {@link #DROP}。")
            out.append("     */")
            out.append("    public static int mapBlockEntityType765To767(int id765) {")
            out.append("        if (id765 < 0 || id765 > BLOCK_ENTITY_TRIAL_SPAWNER_765) {")
            out.append("            logUnknownComposedOnce(\"765→767\", \"ブロックエンティティ種別\", id765);")
            out.append("            return DROP;")
            out.append("        }")
            out.append("        return id765;")
            out.append("    }")
            out.append("")
            out.append("    /**")
            out.append("     * 767のブロックエンティティ種別ID → 765の種別ID。")
            out.append("     * 767の種別は 0〜42 で765と同じ並びのためそのまま。")
            out.append("     * vault（43）は765に無いため {@link #DROP}。")
            out.append("     */")
            out.append("    public static int mapBlockEntityType767To765(int id767) {")
            out.append("        if (id767 < 0 || id767 > BLOCK_ENTITY_TRIAL_SPAWNER_765) {")
            out.append("            logUnknownComposedOnce(\"767→765\", \"ブロックエンティティ種別\", id767);")
            out.append("            return DROP;")
            out.append("        }")
            out.append("        return id767;")
            out.append("    }")
            out.append("")
            out.append("    /**")
            out.append("     * 音の名前を767向けに変換する。765の音の名前のうち 767で改名された")
            out.append("     * trial_spawner の音2件は新しい名前にする。")
            out.append("     * {@code null} は石の破壊音にする。")
            out.append("     */")
            out.append("    public static String mapSoundName765To767(String name) {")
            out.append("        return mapSoundNameComposed(name, SOUND_NAME_FALLBACK_767_MAP);")
            out.append("    }")
            out.append("")
            out.append("    /**")
            out.append("     * 音の名前を765向けに変換する。767の音の名前のうち 765に無いものは")
            out.append("     * 代替の音の名前にする（数値の合成表と一致させる）。")
            out.append("     * {@code null} は石の破壊音にする。")
            out.append("     */")
            out.append("    public static String mapSoundName767To765(String name) {")
            out.append("        return mapSoundNameComposed(name, SOUND_NAME_FALLBACK_767_TO_765_MAP);")
            out.append("    }")
    return "\n".join(out)


JAVADOC_767 = """ *
 * <p>767（1.21／1.21.1）向けの表も同じ作り方で持つ（{@code *_767}）。
 * 767は766に近く、差分は次の通り（ViaVersion mapping-1.21.json と
 * mapping-1.20.5.json、minecraft-data 1.21.1 の突き合わせ）。
 * ブロック状態・ブロック・エンティティ・パーティクル・メニューは同一。
 * アイテムは新3件（音楽ディスク creator／creator_music_box／precipice）
 * のみ。音は改名2件（trial_spawner の charge_activate→ominous_activate、
 * ambient_charged→ambient_ominous）と新6件（音楽ディスク3件・
 * block.vault.reject_rewarded_player など）。属性は新9件（挿入で
 * 既存の番号がずれる）。エンチャントは 42件同士で並びが大きく変わった
 * （1.21 でデータパック化されアルファベット順になったため。40件が移動）。
 * 1.21 のアイテムは 766 と同じデータコンポーネント形式のため、スロットの
 * 扱いは {@link ChunkTranslator} の 766 の流儀を引き継ぐ。
 * 766→767 の隣接表と 763／764／765→767 の合成表を持ち、逆向き
 * 767→766 は反転して作る。
"""


def insert_after(java, anchor, block, label):
    if anchor not in java:
        print(f"アンカーが見つからない（{label}）", file=sys.stderr)
        return None
    return java.replace(anchor, anchor + block, 1)


def main():
    if TABLES767_FILE.exists():
        print("RegistryTables767.java が既にある（768への分離済み。何もしない）", file=sys.stderr)
        return 1
    java = JAVA_FILE.read_text(encoding="utf-8")
    checks = [
        ("PROTOCOL_767", "767 の定数・表・アクセサは既にある"),
        ("BLOCK_STATE_767_MAP", "767 の展開済み表は既にある"),
        ("mapBlockState766To767", "767 のアクセサは既にある"),
    ]
    for marker, msg in checks:
        if marker in java:
            print(msg, file=sys.stderr)
            return 1

    # クラスの javadoc に 767 の段落を追加。
    anchor = (" * <p>離れた組み合わせ（763↔766、764↔766）の表は、隣接表を合成して\n")
    java = insert_after(java, anchor, JAVADOC_767, "javadoc")
    if java is None:
        return 1

    # 定数。
    java = insert_after(java,
                        "    public static final int PROTOCOL_766 = 766;\n",
                        "    public static final int PROTOCOL_767 = 767;\n", "PROTOCOL_767")
    if java is None:
        return 1
    java = insert_after(java,
                        "    public static final int MAX_EFFECT_766 = 38;\n",
                        CONSTANTS_MAX, "MAX_767")
    if java is None:
        return 1
    java = insert_after(java,
                        "    public static final int SPEED_EFFECT_766 = 0;\n",
                        CONSTANTS_FALLBACK, "767 の代替先")
    if java is None:
        return 1

    # 隣接表（766→767）。MENU_766_MAP の直後。
    anchor = ("    private static final int[] MENU_766_MAP =\n"
              "            expand(MENU_766_RANGES, MENU_766_OVERRIDES, MAX_MENU_765 + 1, CRAFTING_MENU_766);\n")
    java = insert_after(java, anchor, adjacent_maps() + "\n", "隣接表 767")
    if java is None:
        return 1

    # 合成表。MENU_766_TO_764_MAP の直後。
    anchor = ("    private static final int[] MENU_766_TO_764_MAP =\n"
              "            expand(MENU_766_TO_764_RANGES, MENU_766_TO_764_OVERRIDES,\n"
              "                    MAX_MENU_766 + 1, CRAFTING_MENU_764);\n")
    java = insert_after(java, anchor, "\n" + composed_maps() + "\n", "合成表 767")
    if java is None:
        return 1

    # 反転表（767→766）。MENU_UP_766_MAP の直後。
    anchor = ("    private static final int[] MENU_UP_766_MAP = invert(\n"
              "            MENU_766_RANGES, MENU_766_OVERRIDES,\n"
              "            MAX_MENU_765 + 1, MAX_MENU_766 + 1, CRAFTING_MENU_765, null);\n")
    java = insert_after(java, anchor, up_maps() + "\n", "反転表 767")
    if java is None:
        return 1

    # 音の名前の表。
    anchor = ("    private static final Map<String, String> SOUND_NAME_FALLBACK_766_TO_765_MAP =\n"
              "            buildSoundNameMap(SOUND_NAME_FALLBACKS_766_TO_765);\n")
    java = insert_after(java, anchor, SOUND_NAME_MAPS, "音の名前の表 767")
    if java is None:
        return 1

    # ログ。
    anchor = ("    private static void logUnknownUp766Once(String kind, int id766) {\n"
              "        if (LOGGED_UNKNOWN.add(\"up766:\" + kind + \":\" + id766)) {\n"
              "            LOG.warning(\"766に無い\" + kind + \"の番号が来たため代替に落とします（766 id=\" + id766 + \"）。\");\n"
              "        }\n"
              "    }\n")
    java = insert_after(java, anchor, LOG_UP_767, "logUnknownUp767Once")
    if java is None:
        return 1

    # アクセサ（a）766→767。mapSoundName766 の直後のコメントブロックの前。
    anchor = ("    public static String mapSoundName766(String name) {\n"
              "        if (name == null) {\n"
              "            return \"minecraft:block.stone.break\";\n"
              "        }\n"
              "        String bare = name.startsWith(\"minecraft:\") ? name.substring(\"minecraft:\".length()) : name;\n"
              "        String prefix = name.startsWith(\"minecraft:\") ? \"minecraft:\" : \"\";\n"
              "        String fallback = SOUND_NAME_FALLBACK_766_MAP.get(bare);\n"
              "        if (fallback != null) {\n"
              "            return prefix + fallback;\n"
              "        }\n"
              "        return name;\n"
              "    }\n")
    java = insert_after(java, anchor, "\n" + adjacent_accessors() + "\n", "アクセサ 766→767")
    if java is None:
        return 1

    # アクセサ（b）767→766。mapSoundNameUp766 の直後。
    anchor = ("    public static String mapSoundNameUp766(String name) {\n"
              "        return mapSoundNameComposed(name, SOUND_NAME_FALLBACK_766_TO_765_MAP);\n"
              "    }\n")
    java = insert_after(java, anchor, "\n" + up_accessors() + "\n", "アクセサ 767→766")
    if java is None:
        return 1

    # アクセサ（c）763／764／765↔767。mapSoundName766To764 の直後。
    anchor = ("    public static String mapSoundName766To764(String name) {\n"
              "        return mapSoundNameComposed(name, SOUND_NAME_FALLBACK_766_TO_764_MAP);\n"
              "    }\n")
    java = insert_after(java, anchor, "\n" + composed_accessors() + "\n", "アクセサ 763/764/765↔767")
    if java is None:
        return 1

    JAVA_FILE.write_text(java, encoding="utf-8")
    print("RegistryRemapper.java に 767 の定数・表・アクセサを挿入した")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
