#!/usr/bin/env python3
"""tools/insert-768-java.py で入れた *_768 表の上に、RegistryRemapper.java
へ 769 対応の定数・展開済み表（*_769_MAP）・反転表（*_UP_769_MAP）・
アクセサを挿入する（1回だけの作業用）。

  - 定数：PROTOCOL_769、MAX_*_769、769 側の代替先。
  - 隣接表（768→769）: *_769_MAP と ITEM_REVERSE_769_MAP と
    ATTRIBUTE_769_MAP／ATTRIBUTE_REVERSE_769_MAP／ENCHANTMENT_769_MAP／
    ENCHANTMENT_REVERSE_769_MAP。
  - 合成表（763↔769、764↔769、765↔769、766↔769、767↔769）: *_763_TO_769_MAP など70件。
  - 逆向き（769→768）: *_UP_769_MAP（invert()。アイテムは
    ITEM_REVERSE_769_MAP をそのまま使う。ブロック状態だけ補正あり：
    creaking_heart の active=true／natural=false の3状態）。
  - 音の名前の表（String）: SOUND_NAME_FALLBACK_*_769_MAP など10件。
  - アクセサ：768↔769、769→768（上向き）、763／764／765／766／767↔769。
  - エフェクト：769 は 768 と同一の 39 件（0〜38）のため恒等写像。
    属性は 768・769 とも 32 件で同一、エンチャントは 42 件同士で
    並びは同一（minecraft-data の dataPaths.json で 1.21.4 の
    enchantments は pc/1.21.1 と確認）。
    ブロックエンティティ種別は 768・769 とも 45 件で同一の並び。
    バナー・絵画・装飾パターンは 768 と同一のため恒等写像。
    ダメージ種別も 768 と同一の 49 件のため恒等写像。
    バイオームは pale_garden が 39 に入る（768 では winter_drop
    データパックが無効のため既定では現れなかったものが、1.21.4 で
    は通常のバイオームになる。実バニラの Registry Data での確認は
    統合テストで行う）。
"""

import re
import sys
from pathlib import Path

SCRIPT_DIR = Path(__file__).resolve().parent
REPO_ROOT = SCRIPT_DIR.parent.parent
JAVA_FILE = (REPO_ROOT / "detergentmc-protocol" / "src" / "main" / "java"
             / "io" / "detergentmc" / "protocol" / "RegistryRemapper.java")
TABLES769_FILE = (REPO_ROOT / "detergentmc-protocol" / "src" / "main" / "java"
                  / "io" / "detergentmc" / "protocol" / "RegistryTables769.java")
TABLES769_COMPOSED_FILE = (REPO_ROOT / "detergentmc-protocol" / "src" / "main" / "java"
                  / "io" / "detergentmc" / "protocol" / "RegistryTables769ComposedUp.java")
TABLES769_COMPOSED_DOWN_FILES = {
    suffix: (REPO_ROOT / "detergentmc-protocol" / "src" / "main" / "java"
             / "io" / "detergentmc" / "protocol"
             / f"RegistryTables769Down{suffix}.java")
    for suffix in ("763", "764", "765", "766", "767")
}


# kind: (Java メソッド名の語尾, RANGES の接頭辞, 日本語名,
#        763→769 の件数, 769→763 の件数, 764→769 の件数, 769→764 の件数,
#        765→769 の件数, 769→765 の件数, 766→769 の件数, 769→766 の件数,
#        767→769 の件数, 769→767 の件数,
#        763 側の代替先, 764 側の代替先, 765 側の代替先, 766 側の代替先,
#        767 側の代替先, 769 側の代替先)
KINDS = [
    ("BlockState", "BLOCK_STATE", "ブロック状態", 24135, 27866, 24276, 27866, 26644, 27866, 26684, 27866, 26684, 27866,
     "STONE_BLOCK_STATE_763", "STONE_BLOCK_STATE_764", "STONE_BLOCK_STATE_765",
     "STONE_BLOCK_STATE_766", "STONE_BLOCK_STATE_767", "STONE_BLOCK_STATE_769"),
    ("Item", "ITEM", "アイテム", 1255, 1385, 1255, 1385, 1312, 1385, 1330, 1385, 1333, 1385,
     "STONE_ITEM_763", "STONE_ITEM_764", "STONE_ITEM_765",
     "STONE_ITEM_766", "STONE_ITEM_767", "STONE_ITEM_769"),
    ("EntityType", "ENTITY_TYPE", "エンティティ種別", 124, 149, 124, 149, 126, 149, 130, 149, 130, 149,
     "PIG_ENTITY_TYPE_763", "PIG_ENTITY_TYPE_764", "PIG_ENTITY_TYPE_765",
     "PIG_ENTITY_TYPE_766", "PIG_ENTITY_TYPE_767", "PIG_ENTITY_TYPE_769"),
    ("Sound", "SOUND", "音", 1474, 1651, 1485, 1651, 1539, 1651, 1607, 1651, 1611, 1651,
     "GENERIC_SOUND_763", "GENERIC_SOUND_764", "GENERIC_SOUND_765",
     "GENERIC_SOUND_766", "GENERIC_SOUND_767", "GENERIC_SOUND_769"),
    ("Particle", "PARTICLE", "パーティクル", 95, 112, 95, 112, 101, 112, 109, 112, 109, 112,
     "AMBIENT_PARTICLE_763", "AMBIENT_PARTICLE_764", "AMBIENT_PARTICLE_765",
     "ENTITY_EFFECT_PARTICLE_766", "ENTITY_EFFECT_PARTICLE_767", "ENTITY_EFFECT_PARTICLE_769"),
    ("Block", "BLOCK", "ブロック", 1003, 1095, 1003, 1095, 1058, 1095, 1060, 1095, 1060, 1095,
     "STONE_BLOCK_763", "STONE_BLOCK_764", "STONE_BLOCK_765",
     "STONE_BLOCK_766", "STONE_BLOCK_767", "STONE_BLOCK_769"),
    ("Menu", "MENU", "メニュー種別", 24, 25, 24, 25, 25, 25, 25, 25, 25, 25,
     "CRAFTING_MENU_763", "CRAFTING_MENU_764", "CRAFTING_MENU_765",
     "CRAFTING_MENU_766", "CRAFTING_MENU_767", "CRAFTING_MENU_769"),
]

CONSTANTS_MAX = """
    /** 769側の最大の番号（ViaVersion mapping-1.21.4.json の件数−1）。 */
    public static final int MAX_BLOCK_STATE_769 = 27865;
    public static final int MAX_ITEM_769 = 1384;
    public static final int MAX_ENTITY_TYPE_769 = 148;
    public static final int MAX_SOUND_769 = 1650;
    public static final int MAX_PARTICLE_769 = 111;
    public static final int MAX_BLOCK_769 = 1094;
    public static final int MAX_MENU_769 = 24;
    /** 769側のエフェクトも 0〜38（Speed=0。768と同一。minecraft-data の
        dataPaths.json で 1.21.4 の effects は pc/1.21.4 と確認）。 */
    public static final int MAX_EFFECT_769 = 38;
    /** 769側の属性は 0〜31（768と同一の 32 件。名前も並びも同一）。 */
    public static final int MAX_ATTRIBUTE_769 = 31;
    /** エンチャントは 768・769 とも 0〜41（42件。並びは同一。
        minecraft-data の dataPaths.json で 1.21.4 の enchantments は
        pc/1.21.1 と確認。実機の Registry Data での確認は統合テストで行う）。 */
    public static final int MAX_ENCHANTMENT_769 = 41;
"""

CONSTANTS_FALLBACK = """
    /** 769側の代替先（mapping-1.21.4.json の番号。Speed だけ 0ベースで 0）。
     * ブタは 769 では 94（creaking_transient の削除で 1 つ前にずれた）、
     * 石の破壊音は 769 では 1401（新音 15 件のうち 14 件と
     * entity.creaking.twitch が block.stone.break より前に来るため）。 */
    public static final int STONE_BLOCK_STATE_769 = 1;
    public static final int AIR_BLOCK_STATE_769 = 0;
    public static final int STONE_ITEM_769 = 1;
    public static final int PIG_ENTITY_TYPE_769 = 94;
    public static final int GENERIC_SOUND_769 = 1401; // block.stone.break
    /** 769でも ambient_entity_effect は無いため、パーティクルの代替は entity_effect。 */
    public static final int ENTITY_EFFECT_PARTICLE_769 = 20;
    public static final int STONE_BLOCK_769 = 1;
    public static final int CRAFTING_MENU_769 = 12;
    public static final int SPEED_EFFECT_769 = 0;
"""

CONSTANTS_BLOCK_ENTITY = """
    /** 769のブロックエンティティ種別：768と同一の 45 件で並びも同じ
        （creaking_heart=10、trial_spawner=43、vault=44）。 */
    public static final int BLOCK_ENTITY_CREAKING_HEART_769 = 10;
    public static final int BLOCK_ENTITY_TRIAL_SPAWNER_769 = 43;
    public static final int BLOCK_ENTITY_VAULT_769 = 44;
"""


def adjacent_maps():
    lines = []
    for kind in KINDS:
        name, prefix, jp = kind[0], kind[1], kind[2]
        lines.append(
            f"    private static final int[] {prefix}_769_MAP =\n"
            f"            expand({prefix}_769_RANGES, {prefix}_769_OVERRIDES, MAX_{prefix}_768 + 1,\n"
            f"                    {kind[18]});")
    lines.append(
        "    private static final int[] ITEM_REVERSE_769_MAP =\n"
        "            expand(ITEM_REVERSE_769_RANGES, ITEM_REVERSE_769_OVERRIDES, 1385, -1);")
    lines.append(
        "    private static final int[] ATTRIBUTE_769_MAP =\n"
        "            expand(ATTRIBUTE_769_RANGES, ATTRIBUTE_769_OVERRIDES, MAX_ATTRIBUTE_768 + 1, DROP);")
    lines.append(
        "    private static final int[] ATTRIBUTE_REVERSE_769_MAP =\n"
        "            expand(ATTRIBUTE_REVERSE_769_RANGES, ATTRIBUTE_REVERSE_769_OVERRIDES,\n"
        "                    MAX_ATTRIBUTE_769 + 1, DROP);")
    lines.append(
        "    private static final int[] ENCHANTMENT_769_MAP =\n"
        "            expand(ENCHANTMENT_769_RANGES, ENCHANTMENT_769_OVERRIDES,\n"
        "                    MAX_ENCHANTMENT_768 + 1, DROP);")
    lines.append(
        "    private static final int[] ENCHANTMENT_REVERSE_769_MAP =\n"
        "            expand(ENCHANTMENT_REVERSE_769_RANGES, ENCHANTMENT_REVERSE_769_OVERRIDES,\n"
        "                    MAX_ENCHANTMENT_769 + 1, DROP);")
    return "\n".join(lines)


def composed_maps():
    # (接尾辞, 件数の位置, 代替先の位置)。件数・代替先は KINDS の
    # タプルから取る（763_TO_769: 件数 index 3, 代替先 769=index 18）。
    pairs = [
        ("763_TO_769", 3, 18), ("769_TO_763", 4, 13),
        ("764_TO_769", 5, 18), ("769_TO_764", 6, 14),
        ("765_TO_769", 7, 18), ("769_TO_765", 8, 15),
        ("766_TO_769", 9, 18), ("769_TO_766", 10, 16),
        ("767_TO_769", 11, 18), ("769_TO_767", 12, 17),
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
            continue  # アイテムは ITEM_REVERSE_769_MAP を使う。
        # ブロック状態だけ補正あり：769 の creaking_heart のうち
        # active=true／natural=false の 3 状態は 768 の creaking=active
        # へ（BLOCK_STATE_UP_769_CORRECTIONS）。エンティティの補正は
        # 無い（769 のエンティティはすべて 768 にある）。
        corrections = ("BLOCK_STATE_UP_769_CORRECTIONS"
                       if prefix == "BLOCK_STATE" else "null")
        # 逆向きの代替先は「768 側」の定数。kind[17] は 767 側のためそのまま
        # 使うと 769 の新音などが 767 の石の破壊音（1361＝768 の別な音）や
        # 767 のブタ（77＝768 の llama_spit）になってしまう。
        # （up_accessors() の fb768 と同じ理由。）
        fb768 = kind[17].replace("_767", "_768")
        lines.append(
            f"    private static final int[] {prefix}_UP_769_MAP = invert(\n"
            f"            {prefix}_769_RANGES, {prefix}_769_OVERRIDES,\n"
            f"            MAX_{prefix}_768 + 1, MAX_{prefix}_769 + 1,\n"
            f"            {fb768}, {corrections});")
    return "\n".join(lines)


SOUND_NAME_MAPS = """
    private static final Map<String, String> SOUND_NAME_FALLBACK_769_MAP =
            buildSoundNameMap(SOUND_NAME_FALLBACKS_769);
    private static final Map<String, String> SOUND_NAME_FALLBACK_768_TO_769_MAP =
            buildSoundNameMap(SOUND_NAME_FALLBACKS_768_TO_769);
    private static final Map<String, String> SOUND_NAME_FALLBACK_769_TO_768_MAP =
            buildSoundNameMap(SOUND_NAME_FALLBACKS_769_TO_768);
    private static final Map<String, String> SOUND_NAME_FALLBACK_769_TO_767_MAP =
            buildSoundNameMap(SOUND_NAME_FALLBACKS_769_TO_767);
    private static final Map<String, String> SOUND_NAME_FALLBACK_769_TO_765_MAP =
            buildSoundNameMap(SOUND_NAME_FALLBACKS_769_TO_765);
    private static final Map<String, String> SOUND_NAME_FALLBACK_769_TO_763_MAP =
            buildSoundNameMap(SOUND_NAME_FALLBACKS_769_TO_763);
    private static final Map<String, String> SOUND_NAME_FALLBACK_769_TO_764_MAP =
            buildSoundNameMap(SOUND_NAME_FALLBACKS_769_TO_764);
    private static final Map<String, String> SOUND_NAME_FALLBACK_769_TO_766_MAP =
            buildSoundNameMap(SOUND_NAME_FALLBACKS_769_TO_766);
    private static final Map<String, String> SOUND_NAME_FALLBACK_767_TO_769_MAP =
            buildSoundNameMap(SOUND_NAME_FALLBACKS_767_TO_769);
    private static final Map<String, String> SOUND_NAME_FALLBACK_766_TO_769_MAP =
            buildSoundNameMap(SOUND_NAME_FALLBACKS_766_TO_769);
"""

LOG_UP_769 = """
    private static void logUnknownUp769Once(String kind, int id769) {
        if (LOGGED_UNKNOWN.add("up769:" + kind + ":" + id769)) {
            LOG.warning("769に無い" + kind + "の番号が来たため代替に落とします（769 id=" + id769 + "）。");
        }
    }
"""
def adjacent_accessors():
    out = ["    // ------------------------------------------------------------------",
           "    // 768→769。上の *_769 表を使う。ブロック状態・ブロック・",
           "    // アイテム・エンティティ・音・パーティクルは 768 から 769 で",
           "    // 樹脂・アイブロッサムなどが挿入でずれる。creaking_transient",
           "    // は 769 で削除された。メニュー・エフェクト・バナー・絵画・",
           "    // 装飾パターン・ダメージ種別は同一、エンチャントも属性も同一",
           "    // の並び。ブロックエンティティ種別も同一の 45 件。",
           "    // ------------------------------------------------------------------",
           ""]
    for kind in KINDS:
        name, prefix, jp = kind[0], kind[1], kind[2]
        fb769 = kind[18]
        out.append(f"    /** 768の{jp}ID → 769の{jp}ID。範囲外は{fb769} に相当する代替。 */")
        out.append(f"    public static int map{name}768To769(int id768) {{")
        out.append(f"        if (id768 < 0 || id768 > MAX_{prefix}_768) {{")
        out.append(f"            logUnknownOnce(PROTOCOL_769, \"{jp}\", id768);")
        out.append(f"            return {fb769};")
        out.append("        }")
        out.append(f"        return {prefix}_769_MAP[id768];")
        out.append("    }")
        out.append("")
    out.append("    /**")
    out.append("     * 769のアイテムID → 768のアイテムID（サーバー行きスロット用）。")
    out.append("     * 769にしか無いアイテム（樹脂・アイブロッサム類10件）は 768の石へ。")
    out.append("     * 範囲外はそのまま返す。")
    out.append("     */")
    out.append("    public static int mapItemReverse769(int id769) {")
    out.append("        if (id769 < 0 || id769 >= ITEM_REVERSE_769_MAP.length) {")
    out.append("            logUnknownOnce(PROTOCOL_769, \"アイテム（逆変換）\", id769);")
    out.append("            return id769;")
    out.append("        }")
    out.append("        int mapped = ITEM_REVERSE_769_MAP[id769];")
    out.append("        return mapped < 0 ? id769 : mapped;")
    out.append("    }")
    out.append("")
    out.append("    /**")
    out.append("     * 768のエフェクトID → 769のエフェクトID。768も769も0ベース")
    out.append("     * （Speed は両方 0）で 0〜38 は同一のため恒等写像。")
    out.append("     * 範囲外は Speed（0）。")
    out.append("     */")
    out.append("    public static int mapEffect768To769(int id768) {")
    out.append("        if (id768 < 0 || id768 > MAX_EFFECT_768) {")
    out.append("            logUnknownOnce(PROTOCOL_769, \"エフェクト\", id768);")
    out.append("            return SPEED_EFFECT_769;")
    out.append("        }")
    out.append("        return id768;")
    out.append("    }")
    out.append("")
    out.append("    /**")
    out.append("     * 768のブロックエンティティ種別ID → 769の種別ID。")
    out.append("     * 768・769 とも 45 件で並びは同一のため恒等写像。")
    out.append("     * 範囲外の種別は NBT の中身が種別専用のため安全に代替できず")
    out.append("     * {@link #DROP}。")
    out.append("     */")
    out.append("    public static int mapBlockEntityType768To769(int id768) {")
    out.append("        if (id768 < 0 || id768 > BLOCK_ENTITY_VAULT_768) {")
    out.append("            logUnknownOnce(PROTOCOL_769, \"ブロックエンティティ種別\", id768);")
    out.append("            return DROP;")
    out.append("        }")
    out.append("        return id768;")
    out.append("    }")
    out.append("")
    out.append("    /**")
    out.append("     * 音の名前（直書き・Stop Sound 用）を769向けに変換する。")
    out.append("     * {@code minecraft:} の有無はそのまま保つ。769で削除・改名された")
    out.append("     * 音は無いため、そのまま通す。")
    out.append("     */")
    out.append("    public static String mapSoundName768To769(String name) {")
    out.append("        return mapSoundNameComposed(name, SOUND_NAME_FALLBACK_768_TO_769_MAP);")
    out.append("    }")
    out.append("")
    # 属性とエンチャント。
    out.append("    /** 768の属性ID → 769の属性ID。並びは同一。範囲外は {@link #DROP}。 */")
    out.append("    public static int mapAttribute768To769(int id768) {")
    out.append("        if (id768 < 0 || id768 > MAX_ATTRIBUTE_768) {")
    out.append("            logUnknownOnce(PROTOCOL_769, \"属性\", id768);")
    out.append("            return DROP;")
    out.append("        }")
    out.append("        return ATTRIBUTE_769_MAP[id768];")
    out.append("    }")
    out.append("")
    out.append("    /** 769の属性ID → 768の属性ID。並びは同一。範囲外は {@link #DROP}。 */")
    out.append("    public static int mapAttribute769To768(int id769) {")
    out.append("        if (id769 < 0 || id769 > MAX_ATTRIBUTE_769) {")
    out.append("            logUnknownOnce(PROTOCOL_769, \"属性\", id769);")
    out.append("            return DROP;")
    out.append("        }")
    out.append("        return ATTRIBUTE_REVERSE_769_MAP[id769];")
    out.append("    }")
    out.append("")
    out.append("    /** 769の属性ID → 属性の名前（Update Attributes の 768向け変換用）。 */")
    out.append("    public static String attributeName769(int id769) {")
    out.append("        if (id769 < 0 || id769 >= ATTRIBUTE_769_NAMES.length) {")
    out.append("            return null;")
    out.append("        }")
    out.append("        return ATTRIBUTE_769_NAMES[id769];")
    out.append("    }")
    out.append("")
    out.append("    /**")
    out.append("     * 属性の名前 → 769の属性ID（765／763／764 は属性が名前形式のため、")
    out.append("     * 769向けに変換するときに使う）。無い名前は {@link #DROP}。")
    out.append("     */")
    out.append("    public static int attributeId769(String name) {")
    out.append("        if (name == null) {")
    out.append("            return DROP;")
    out.append("        }")
    out.append("        String plain = stripMinecraftPrefix(name);")
    out.append("        for (int i = 0; i < ATTRIBUTE_769_NAMES.length; i++) {")
    out.append("            if (ATTRIBUTE_769_NAMES[i].equals(plain)) {")
    out.append("                return i;")
    out.append("            }")
    out.append("        }")
    out.append("        return DROP;")
    out.append("    }")
    out.append("")
    out.append("    /** 768のエンチャントID → 769のエンチャントID。並びは同一。範囲外は {@link #DROP}。 */")
    out.append("    public static int mapEnchantment768To769(int id768) {")
    out.append("        if (id768 < 0 || id768 > MAX_ENCHANTMENT_768) {")
    out.append("            logUnknownOnce(PROTOCOL_769, \"エンチャント\", id768);")
    out.append("            return DROP;")
    out.append("        }")
    out.append("        return ENCHANTMENT_769_MAP[id768];")
    out.append("    }")
    out.append("")
    out.append("    /** 769のエンチャントID → 768のエンチャントID。並びは同一。範囲外は {@link #DROP}。 */")
    out.append("    public static int mapEnchantment769To768(int id769) {")
    out.append("        if (id769 < 0 || id769 > MAX_ENCHANTMENT_769) {")
    out.append("            logUnknownOnce(PROTOCOL_769, \"エンチャント\", id769);")
    out.append("            return DROP;")
    out.append("        }")
    out.append("        return ENCHANTMENT_REVERSE_769_MAP[id769];")
    out.append("    }")
    out.append("")
    out.append("    /**")
    out.append("     * 767のエンチャントID → 769のエンチャントID。767→768 の表を")
    out.append("     * 経由して 768→769 の表を引く。範囲外は {@link #DROP}。")
    out.append("     */")
    out.append("    public static int mapEnchantment767To769(int id767) {")
    out.append("        int id768 = mapEnchantment767To768(id767);")
    out.append("        return id768 == DROP ? DROP : mapEnchantment768To769(id768);")
    out.append("    }")
    out.append("")
    out.append("    /**")
    out.append("     * 769のエンチャントID → 767のエンチャントID。769→768 の逆変換の")
    out.append("     * 結果を 768→767 の逆変換で戻す。範囲外は {@link #DROP}。")
    out.append("     */")
    out.append("    public static int mapEnchantment769To767(int id769) {")
    out.append("        int id768 = mapEnchantment769To768(id769);")
    out.append("        return id768 == DROP ? DROP : mapEnchantment768To767(id768);")
    out.append("    }")
    out.append("")
    out.append("    /**")
    out.append("     * 766のエンチャントID → 769のエンチャントID。766→767 の変換")
    out.append("     * （{@link #mapEnchantment766To767}）を経由して 767→769 の")
    out.append("     * 表を引く。範囲外は {@link #DROP}。")
    out.append("     */")
    out.append("    public static int mapEnchantment766To769(int id766) {")
    out.append("        int id767 = mapEnchantment766To767(id766);")
    out.append("        return id767 == DROP ? DROP : mapEnchantment767To769(id767);")
    out.append("    }")
    out.append("")
    out.append("    /**")
    out.append("     * 769のエンチャントID → 766のエンチャントID。769→767 の逆変換の")
    out.append("     * 結果を 767→766 の逆変換で戻す。範囲外は {@link #DROP}。")
    out.append("     */")
    out.append("    public static int mapEnchantment769To766(int id769) {")
    out.append("        int id767 = mapEnchantment769To767(id769);")
    out.append("        return id767 == DROP ? DROP : mapEnchantment767To766(id767);")
    out.append("    }")
    out.append("")
    out.append("    /**")
    out.append("     * 765のエンチャントID → 769のエンチャントID。765→767 の変換")
    out.append("     * （{@link #mapEnchantment765To767}）を経由して 767→769 の")
    out.append("     * 表を引く。範囲外は {@link #DROP}。")
    out.append("     * 763／764 のエンチャントの並びは 765 と同一のため、")
    out.append("     * 763／764 向けも同じ変換を使う。")
    out.append("     */")
    out.append("    public static int mapEnchantment765To769(int id765) {")
    out.append("        int id767 = mapEnchantment765To767(id765);")
    out.append("        return id767 == DROP ? DROP : mapEnchantment767To769(id767);")
    out.append("    }")
    out.append("")
    out.append("    /** 763のエンチャントID → 769のエンチャントID（765と同じ並び）。 */")
    out.append("    public static int mapEnchantment763To769(int id763) {")
    out.append("        return mapEnchantment765To769(id763);")
    out.append("    }")
    out.append("")
    out.append("    /** 764のエンチャントID → 769のエンチャントID（765と同じ並び）。 */")
    out.append("    public static int mapEnchantment764To769(int id764) {")
    out.append("        return mapEnchantment765To769(id764);")
    out.append("    }")
    out.append("")
    out.append("    /**")
    out.append("     * 769のエンチャントID → 765のエンチャントID。769→767 の逆変換の")
    out.append("     * 結果を 767→765 の変換（{@link #mapEnchantment767To765}）で")
    out.append("     * 戻す。density／breach／wind_burst は 765に無いため")
    out.append("     * {@link #DROP}。範囲外も {@link #DROP}。")
    out.append("     */")
    out.append("    public static int mapEnchantment769To765(int id769) {")
    out.append("        int id767 = mapEnchantment769To767(id769);")
    out.append("        return id767 == DROP ? DROP : mapEnchantment767To765(id767);")
    out.append("    }")
    out.append("")
    out.append("    /** 769のエンチャントID → 763のエンチャントID（765と同じ並び）。 */")
    out.append("    public static int mapEnchantment769To763(int id769) {")
    out.append("        return mapEnchantment769To765(id769);")
    out.append("    }")
    out.append("")
    out.append("    /** 769のエンチャントID → 764のエンチャントID（765と同じ並び）。 */")
    out.append("    public static int mapEnchantment769To764(int id769) {")
    out.append("        return mapEnchantment769To765(id769);")
    out.append("    }")
    return "\n".join(out)


def up_accessors():
    out = ["    // ------------------------------------------------------------------",
           "    // 逆向き（769→768）。上の *_769 表を反転して作った表を使う。",
           "    // アイテムだけは生成スクリプトが名前から作った既存の逆変換表",
           "    // （ITEM_REVERSE_769_MAP）をそのまま使う（反転した表と完全一致",
           "    // することはテストで確認している）。属性とエンチャントの逆向きは",
           "    // mapAttribute769To768／mapEnchantment769To768 を参照。",
           "    // ------------------------------------------------------------------",
           ""]
    for kind in KINDS:
        name, prefix, jp = kind[0], kind[1], kind[2]
        # 逆向きの代替先は「768 側」の定数。kind[17] は 767 側のため
        # そのまま使うと範囲外の ID が 767 のブタ（77＝768 の llama_spit）や
        # 767 の石の破壊音（1361＝768 の別な音）になってしまう。
        # 768 の生成スクリプトでは kind[16]（767 側）が正しかったが、
        # 769 では対象が 768 なので _767 → _768 に直す。
        fb768 = kind[17].replace("_767", "_768")
        if prefix == "ITEM":
            out.append("    /**")
            out.append("     * 769のアイテムID → 768のアイテムID。")
            out.append("     * 769にしか無いアイテム（樹脂・アイブロッサム類10件）は石へ。範囲外は石（1）。")
            out.append("     */")
            out.append("    public static int mapItemUp769(int id769) {")
            out.append("        if (id769 < 0 || id769 >= ITEM_REVERSE_769_MAP.length) {")
            out.append("            logUnknownUp769Once(\"アイテム\", id769);")
            out.append("            return STONE_ITEM_768;")
            out.append("        }")
            out.append("        int mapped = ITEM_REVERSE_769_MAP[id769];")
            out.append("        return mapped < 0 ? STONE_ITEM_768 : mapped;")
            out.append("    }")
            out.append("")
            continue
        out.append(f"    /** 769の{jp}ID → 768の{jp}ID。範囲外は{fb768} に相当する代替。 */")
        out.append(f"    public static int map{name}Up769(int id769) {{")
        out.append(f"        if (id769 < 0 || id769 > MAX_{prefix}_769) {{")
        out.append(f"            logUnknownUp769Once(\"{jp}\", id769);")
        out.append(f"            return {fb768};")
        out.append("        }")
        out.append(f"        return {prefix}_UP_769_MAP[id769];")
        out.append("    }")
        out.append("")
    out.append("    /**")
    out.append("     * 769のエフェクトID → 768のエフェクトID。0〜38 は同一のため恒等写像。")
    out.append("     * 範囲外は Speed（0）。")
    out.append("     */")
    out.append("    public static int mapEffectUp769(int id769) {")
    out.append("        if (id769 < 0 || id769 > MAX_EFFECT_769) {")
    out.append("            logUnknownUp769Once(\"エフェクト\", id769);")
    out.append("            return SPEED_EFFECT_768;")
    out.append("        }")
    out.append("        return id769;")
    out.append("    }")
    out.append("")
    out.append("    /**")
    out.append("     * 769のブロックエンティティ種別ID → 768の種別ID。")
    out.append("     * 768・769 とも 45 件で並びは同一のため恒等写像。")
    out.append("     * 範囲外の種別は NBT の中身が種別専用のため安全に代替できず")
    out.append("     * {@link #DROP}。")
    out.append("     */")
    out.append("    public static int mapBlockEntityTypeUp769(int id769) {")
    out.append("        if (id769 < 0 || id769 > BLOCK_ENTITY_VAULT_769) {")
    out.append("            logUnknownUp769Once(\"ブロックエンティティ種別\", id769);")
    out.append("            return DROP;")
    out.append("        }")
    out.append("        return id769;")
    out.append("    }")
    out.append("")
    out.append("    /**")
    out.append("     * 音の名前を768向けに変換する。769の音の名前のうち 768に無いものは")
    out.append("     * 代替の音の名前にする（数値の反転表と一致させる）。769の新音")
    out.append("     * （アイブロッサム・樹脂・クリーキングの身震いなど15件）は石の破壊音へ。")
    out.append("     * {@code null} は石の破壊音にする。")
    out.append("     */")
    out.append("    public static String mapSoundNameUp769(String name) {")
    out.append("        return mapSoundNameComposed(name, SOUND_NAME_FALLBACK_769_TO_768_MAP);")
    out.append("    }")
    return "\n".join(out)
def composed_accessors():
    out = ["    // ------------------------------------------------------------------",
           "    // 離れた組み合わせの合成表（763↔769、764↔769、765↔769、766↔769、",
           "    // 767↔769）。上の *_763_TO_769／*_769_TO_763／*_764_TO_769／",
           "    // *_769_TO_764／*_765_TO_769／*_769_TO_765／*_766_TO_769／",
           "    // *_769_TO_766／*_767_TO_769／*_769_TO_767 表（合成済みの1つの表）",
           "    // を直接引く。二段変換はしない。エフェクトとブロックエンティティ",
           "    // 種別は表を作らず、数値の規則と同一の並びで扱う。",
           "    // ------------------------------------------------------------------",
           ""]
    pairs = [
        ("763", 13, "763→769", "769→763"),
        ("764", 14, "764→769", "769→764"),
        ("765", 15, "765→769", "769→765"),
        ("766", 16, "766→769", "769→766"),
        ("767", 17, "767→769", "769→767"),
    ]
    for src, fb_src_idx, up_label, down_label in pairs:
        for kind in KINDS:
            name, prefix, jp = kind[0], kind[1], kind[2]
            fb_src = kind[fb_src_idx]
            fb_769 = kind[18]
            # src → 769
            out.append(f"    /** {src}の{jp}ID → 769の{jp}ID。範囲外は{fb_769} に相当する代替。 */")
            out.append(f"    public static int map{name}{src}To769(int id{src}) {{")
            out.append(f"        if (id{src} < 0 || id{src} >= {prefix}_{src}_TO_769_MAP.length) {{")
            out.append(f"            logUnknownComposedOnce(\"{up_label}\", \"{jp}\", id{src});")
            out.append(f"            return {fb_769};")
            out.append("        }")
            out.append(f"        return {prefix}_{src}_TO_769_MAP[id{src}];")
            out.append("    }")
            out.append("")
            # 769 → src
            out.append(f"    /** 769の{jp}ID → {src}の{jp}ID。範囲外は{fb_src} に相当する代替。 */")
            out.append(f"    public static int map{name}769To{src}(int id769) {{")
            out.append(f"        if (id769 < 0 || id769 >= {prefix}_769_TO_{src}_MAP.length) {{")
            out.append(f"            logUnknownComposedOnce(\"{down_label}\", \"{jp}\", id769);")
            out.append(f"            return {fb_src};")
            out.append("        }")
            out.append(f"        return {prefix}_769_TO_{src}_MAP[id769];")
            out.append("    }")
            out.append("")
        # エフェクトとブロックエンティティ種別と音の名前。
        if src == "763":
            out.append("    /**")
            out.append("     * 763のエフェクトID → 769のエフェクトID。763は1ベース")
            out.append("     * （Speed=1）のため 1 引く。範囲外は Speed（0）。")
            out.append("     */")
            out.append("    public static int mapEffect763To769(int id763) {")
            out.append("        if (id763 < 1 || id763 > MAX_EFFECT_765 + 1) {")
            out.append("            logUnknownComposedOnce(\"763→769\", \"エフェクト\", id763);")
            out.append("            return SPEED_EFFECT_769;")
            out.append("        }")
            out.append("        return id763 - 1;")
            out.append("    }")
            out.append("")
            out.append("    /**")
            out.append("     * 769のエフェクトID → 763のエフェクトID。763は1ベース")
            out.append("     * （Speed=1）のため 1 足す。769で増えた 33〜38 は")
            out.append("     * 763に無いため Speed（1）へ。範囲外も Speed（1）。")
            out.append("     */")
            out.append("    public static int mapEffect769To763(int id769) {")
            out.append("        if (id769 < 0 || id769 > MAX_EFFECT_769) {")
            out.append("            logUnknownComposedOnce(\"769→763\", \"エフェクト\", id769);")
            out.append("            return SPEED_EFFECT_763;")
            out.append("        }")
            out.append("        return id769 <= MAX_EFFECT_765 ? id769 + 1 : SPEED_EFFECT_763;")
            out.append("    }")
            out.append("")
            out.append("    /**")
            out.append("     * 763のブロックエンティティ種別ID → 769の種別ID。")
            out.append("     * 763の種別は 0〜9 で769と同じ並びのためそのまま。")
            out.append("     * creaking_heart（10）の挿入で piston 以降（10〜40）は +1 ずれる。")
            out.append("     * 763に無い番号（41以上）は769側の種別に対応付けられないため")
            out.append("     * {@link #DROP}。")
            out.append("     */")
            out.append("    public static int mapBlockEntityType763To769(int id763) {")
            out.append("        if (id763 < 0 || id763 > BLOCK_ENTITY_TRIAL_SPAWNER_765 - 2) {")
            out.append("            logUnknownComposedOnce(\"763→769\", \"ブロックエンティティ種別\", id763);")
            out.append("            return DROP;")
            out.append("        }")
            out.append("        return id763 < 10 ? id763 : id763 + 1;")
            out.append("    }")
            out.append("")
            out.append("    /**")
            out.append("     * 769のブロックエンティティ種別ID → 763の種別ID。")
            out.append("     * 769の種別は 0〜9 で763と同じ並びのためそのまま。")
            out.append("     * creaking_heart（10）は763に無いため {@link #DROP}。")
            out.append("     * piston 以降（11〜41）は -1 ずれる。crafter（42）・")
            out.append("     * trial_spawner（43）・vault（44）は763に無いため {@link #DROP}。")
            out.append("     */")
            out.append("    public static int mapBlockEntityType769To763(int id769) {")
            out.append("        if (id769 < 0 || id769 > BLOCK_ENTITY_TRIAL_SPAWNER_769 - 2) {")
            out.append("            logUnknownComposedOnce(\"769→763\", \"ブロックエンティティ種別\", id769);")
            out.append("            return DROP;")
            out.append("        }")
            out.append("        if (id769 == BLOCK_ENTITY_CREAKING_HEART_769) {")
            out.append("            return DROP;")
            out.append("        }")
            out.append("        return id769 < 10 ? id769 : id769 - 1;")
            out.append("    }")
            out.append("")
            out.append("    /**")
            out.append("     * 音の名前を769向けに変換する。763の音の名前はすべて769にもある")
            out.append("     * （769は763の音をすべて含む）ため、そのまま返す。")
            out.append("     * {@code null} は石の破壊音にする。")
            out.append("     */")
            out.append("    public static String mapSoundName763To769(String name) {")
            out.append("        if (name == null) {")
            out.append("            return \"minecraft:block.stone.break\";")
            out.append("        }")
            out.append("        return name;")
            out.append("    }")
            out.append("")
            out.append("    /**")
            out.append("     * 音の名前を763向けに変換する。769の音の名前のうち 763に無いものは")
            out.append("     * 代替の音の名前にする（数値の合成表と一致させる）。")
            out.append("     * {@code null} は石の破壊音にする。")
            out.append("     */")
            out.append("    public static String mapSoundName769To763(String name) {")
            out.append("        return mapSoundNameComposed(name, SOUND_NAME_FALLBACK_769_TO_763_MAP);")
            out.append("    }")
        elif src == "764":
            out.append("    /**")
            out.append("     * 764のエフェクトID → 769のエフェクトID。764も769も0ベース")
            out.append("     * （Speed は両方 0）で 0〜32 は同一のため恒等写像。")
            out.append("     * 範囲外は Speed（0）。")
            out.append("     */")
            out.append("    public static int mapEffect764To769(int id764) {")
            out.append("        if (id764 < 0 || id764 > MAX_EFFECT_764) {")
            out.append("            logUnknownComposedOnce(\"764→769\", \"エフェクト\", id764);")
            out.append("            return SPEED_EFFECT_769;")
            out.append("        }")
            out.append("        return id764;")
            out.append("    }")
            out.append("")
            out.append("    /**")
            out.append("     * 769のエフェクトID → 764のエフェクトID。0〜32 は同一のため恒等写像。")
            out.append("     * 769で増えた 33〜38 は 764に無いため Speed（0）へ。")
            out.append("     * 範囲外も Speed（0）。")
            out.append("     */")
            out.append("    public static int mapEffect769To764(int id769) {")
            out.append("        if (id769 < 0 || id769 > MAX_EFFECT_769) {")
            out.append("            logUnknownComposedOnce(\"769→764\", \"エフェクト\", id769);")
            out.append("            return SPEED_EFFECT_764;")
            out.append("        }")
            out.append("        return id769 <= MAX_EFFECT_764 ? id769 : SPEED_EFFECT_764;")
            out.append("    }")
            out.append("")
            out.append("    /**")
            out.append("     * 764のブロックエンティティ種別ID → 769の種別ID。")
            out.append("     * 764の種別は 0〜9 で769と同じ並びのためそのまま。")
            out.append("     * creaking_heart（10）の挿入で piston 以降（10〜40）は +1 ずれる。")
            out.append("     * 764に無い番号（41以上）は769側の種別に対応付けられないため")
            out.append("     * {@link #DROP}。")
            out.append("     */")
            out.append("    public static int mapBlockEntityType764To769(int id764) {")
            out.append("        if (id764 < 0 || id764 > BLOCK_ENTITY_TRIAL_SPAWNER_765 - 2) {")
            out.append("            logUnknownComposedOnce(\"764→769\", \"ブロックエンティティ種別\", id764);")
            out.append("            return DROP;")
            out.append("        }")
            out.append("        return id764 < 10 ? id764 : id764 + 1;")
            out.append("    }")
            out.append("")
            out.append("    /**")
            out.append("     * 769のブロックエンティティ種別ID → 764の種別ID。")
            out.append("     * 769の種別は 0〜9 で764と同じ並びのためそのまま。")
            out.append("     * creaking_heart（10）は764に無いため {@link #DROP}。")
            out.append("     * piston 以降（11〜41）は -1 ずれる。crafter（42）・")
            out.append("     * trial_spawner（43）・vault（44）は764に無いため {@link #DROP}。")
            out.append("     */")
            out.append("    public static int mapBlockEntityType769To764(int id769) {")
            out.append("        if (id769 < 0 || id769 > BLOCK_ENTITY_TRIAL_SPAWNER_769 - 2) {")
            out.append("            logUnknownComposedOnce(\"769→764\", \"ブロックエンティティ種別\", id769);")
            out.append("            return DROP;")
            out.append("        }")
            out.append("        if (id769 == BLOCK_ENTITY_CREAKING_HEART_769) {")
            out.append("            return DROP;")
            out.append("        }")
            out.append("        return id769 < 10 ? id769 : id769 - 1;")
            out.append("    }")
            out.append("")
            out.append("    /**")
            out.append("     * 音の名前を769向けに変換する。764の音の名前はすべて769にもある")
            out.append("     * （769は764の音をすべて含む）ため、そのまま返す。")
            out.append("     * {@code null} は石の破壊音にする。")
            out.append("     */")
            out.append("    public static String mapSoundName764To769(String name) {")
            out.append("        if (name == null) {")
            out.append("            return \"minecraft:block.stone.break\";")
            out.append("        }")
            out.append("        return name;")
            out.append("    }")
            out.append("")
            out.append("    /**")
            out.append("     * 音の名前を764向けに変換する。769の音の名前のうち 764に無いものは")
            out.append("     * 代替の音の名前にする（数値の合成表と一致させる）。")
            out.append("     * {@code null} は石の破壊音にする。")
            out.append("     */")
            out.append("    public static String mapSoundName769To764(String name) {")
            out.append("        return mapSoundNameComposed(name, SOUND_NAME_FALLBACK_769_TO_764_MAP);")
            out.append("    }")
        elif src == "765":
            out.append("    /**")
            out.append("     * 765のエフェクトID → 769のエフェクトID。765も769も0ベース")
            out.append("     * （Speed は両方 0）で 0〜32 は同一のため恒等写像。")
            out.append("     * 範囲外は Speed（0）。")
            out.append("     */")
            out.append("    public static int mapEffect765To769(int id765) {")
            out.append("        if (id765 < 0 || id765 > MAX_EFFECT_765) {")
            out.append("            logUnknownComposedOnce(\"765→769\", \"エフェクト\", id765);")
            out.append("            return SPEED_EFFECT_769;")
            out.append("        }")
            out.append("        return id765;")
            out.append("    }")
            out.append("")
            out.append("    /**")
            out.append("     * 769のエフェクトID → 765のエフェクトID。0〜32 は同一のため恒等写像。")
            out.append("     * 769で増えた 33〜38 は 765に無いため Speed（0）へ。")
            out.append("     * 範囲外も Speed（0）。")
            out.append("     */")
            out.append("    public static int mapEffect769To765(int id769) {")
            out.append("        if (id769 < 0 || id769 > MAX_EFFECT_769) {")
            out.append("            logUnknownComposedOnce(\"769→765\", \"エフェクト\", id769);")
            out.append("            return SPEED_EFFECT_765;")
            out.append("        }")
            out.append("        return id769 <= MAX_EFFECT_765 ? id769 : SPEED_EFFECT_765;")
            out.append("    }")
            out.append("")
            out.append("    /**")
            out.append("     * 765のブロックエンティティ種別ID → 769の種別ID。")
            out.append("     * crafter（41）と trial_spawner（42）は769にもあるが、")
            out.append("     * creaking_heart（10）の挿入で +1 ずれる。")
            out.append("     * 範囲外の種別は NBT の中身が種別専用のため安全に代替できず")
            out.append("     * {@link #DROP}。")
            out.append("     */")
            out.append("    public static int mapBlockEntityType765To769(int id765) {")
            out.append("        if (id765 < 0 || id765 > BLOCK_ENTITY_TRIAL_SPAWNER_765) {")
            out.append("            logUnknownComposedOnce(\"765→769\", \"ブロックエンティティ種別\", id765);")
            out.append("            return DROP;")
            out.append("        }")
            out.append("        return id765 < 10 ? id765 : id765 + 1;")
            out.append("    }")
            out.append("")
            out.append("    /**")
            out.append("     * 769のブロックエンティティ種別ID → 765の種別ID。")
            out.append("     * creaking_heart（10）は765に無いため {@link #DROP}。")
            out.append("     * piston 以降（11〜43）は -1 ずれる。vault（44）は765に無い")
            out.append("     * ため {@link #DROP}。")
            out.append("     */")
            out.append("    public static int mapBlockEntityType769To765(int id769) {")
            out.append("        if (id769 < 0 || id769 > BLOCK_ENTITY_TRIAL_SPAWNER_769) {")
            out.append("            logUnknownComposedOnce(\"769→765\", \"ブロックエンティティ種別\", id769);")
            out.append("            return DROP;")
            out.append("        }")
            out.append("        if (id769 == BLOCK_ENTITY_CREAKING_HEART_769) {")
            out.append("            return DROP;")
            out.append("        }")
            out.append("        return id769 < 10 ? id769 : id769 - 1;")
            out.append("    }")
            out.append("")
            out.append("    /**")
            out.append("     * 音の名前を769向けに変換する。765の音の名前のうち 767で改名された")
            out.append("     * trial_spawner の音2件は新しい名前にし、768で削除された")
            out.append("     * ヤギの角笛の音2件は代替の名前にする。769での改名・削除は無い。")
            out.append("     * {@code null} は石の破壊音にする。")
            out.append("     */")
            out.append("    public static String mapSoundName765To769(String name) {")
            out.append("        return mapSoundNameComposed(")
            out.append("                mapSoundNameComposed(")
            out.append("                        mapSoundNameComposed(name, SOUND_NAME_FALLBACK_767_MAP),")
            out.append("                        SOUND_NAME_FALLBACK_768_MAP),")
            out.append("                SOUND_NAME_FALLBACK_769_MAP);")
            out.append("    }")
            out.append("")
            out.append("    /**")
            out.append("     * 音の名前を765向けに変換する。769の音の名前のうち 765に無いものは")
            out.append("     * 代替の音の名前にする（数値の合成表と一致させる）。")
            out.append("     * {@code null} は石の破壊音にする。")
            out.append("     */")
            out.append("    public static String mapSoundName769To765(String name) {")
            out.append("        return mapSoundNameComposed(name, SOUND_NAME_FALLBACK_769_TO_765_MAP);")
            out.append("    }")
        elif src == "766":
            out.append("    /**")
            out.append("     * 766のエフェクトID → 769のエフェクトID。766も769も0ベース")
            out.append("     * （Speed は両方 0）で 0〜38 は同一のため恒等写像。")
            out.append("     * 範囲外は Speed（0）。")
            out.append("     */")
            out.append("    public static int mapEffect766To769(int id766) {")
            out.append("        if (id766 < 0 || id766 > MAX_EFFECT_766) {")
            out.append("            logUnknownComposedOnce(\"766→769\", \"エフェクト\", id766);")
            out.append("            return SPEED_EFFECT_769;")
            out.append("        }")
            out.append("        return id766;")
            out.append("    }")
            out.append("")
            out.append("    /**")
            out.append("     * 769のエフェクトID → 766のエフェクトID。0〜38 は同一のため恒等写像。")
            out.append("     * 範囲外は Speed（0）。")
            out.append("     */")
            out.append("    public static int mapEffect769To766(int id769) {")
            out.append("        if (id769 < 0 || id769 > MAX_EFFECT_769) {")
            out.append("            logUnknownComposedOnce(\"769→766\", \"エフェクト\", id769);")
            out.append("            return SPEED_EFFECT_766;")
            out.append("        }")
            out.append("        return id769;")
            out.append("    }")
            out.append("")
            out.append("    /**")
            out.append("     * 766のブロックエンティティ種別ID → 769の種別ID。")
            out.append("     * vault（43）は769にもあるが、creaking_heart（10）の挿入で")
            out.append("     * piston 以降（10〜43）は +1 ずれる。")
            out.append("     * 範囲外の種別は NBT の中身が種別専用のため安全に代替できず")
            out.append("     * {@link #DROP}。")
            out.append("     */")
            out.append("    public static int mapBlockEntityType766To769(int id766) {")
            out.append("        if (id766 < 0 || id766 > BLOCK_ENTITY_VAULT_766) {")
            out.append("            logUnknownComposedOnce(\"766→769\", \"ブロックエンティティ種別\", id766);")
            out.append("            return DROP;")
            out.append("        }")
            out.append("        return id766 < 10 ? id766 : id766 + 1;")
            out.append("    }")
            out.append("")
            out.append("    /**")
            out.append("     * 769のブロックエンティティ種別ID → 766の種別ID。")
            out.append("     * creaking_heart（10）は766に無いため {@link #DROP}。")
            out.append("     * piston 以降（11〜44）は -1 ずれる。vault は 769=44 → 766=43。")
            out.append("     */")
            out.append("    public static int mapBlockEntityType769To766(int id769) {")
            out.append("        if (id769 < 0 || id769 > BLOCK_ENTITY_VAULT_769) {")
            out.append("            logUnknownComposedOnce(\"769→766\", \"ブロックエンティティ種別\", id769);")
            out.append("            return DROP;")
            out.append("        }")
            out.append("        if (id769 == BLOCK_ENTITY_CREAKING_HEART_769) {")
            out.append("            return DROP;")
            out.append("        }")
            out.append("        return id769 < 10 ? id769 : id769 - 1;")
            out.append("    }")
            out.append("")
            out.append("    /**")
            out.append("     * 音の名前を769向けに変換する。766の音の名前のうち 767で改名された")
            out.append("     * trial_spawner の音2件は新しい名前にし、768で削除された")
            out.append("     * ヤギの角笛の音2件は代替の名前にする。769での改名・削除は無い。")
            out.append("     * {@code null} は石の破壊音にする。")
            out.append("     */")
            out.append("    public static String mapSoundName766To769(String name) {")
            out.append("        return mapSoundNameComposed(name, SOUND_NAME_FALLBACK_766_TO_769_MAP);")
            out.append("    }")
            out.append("")
            out.append("    /**")
            out.append("     * 音の名前を766向けに変換する。769の音の名前のうち 766に無いものは")
            out.append("     * 代替の音の名前にする（数値の合成表と一致させる）。")
            out.append("     * {@code null} は石の破壊音にする。")
            out.append("     */")
            out.append("    public static String mapSoundName769To766(String name) {")
            out.append("        return mapSoundNameComposed(name, SOUND_NAME_FALLBACK_769_TO_766_MAP);")
            out.append("    }")
        else:  # 767
            out.append("    /**")
            out.append("     * 767のエフェクトID → 769のエフェクトID。767も769も0ベース")
            out.append("     * （Speed は両方 0）で 0〜38 は同一のため恒等写像。")
            out.append("     * 範囲外は Speed（0）。")
            out.append("     */")
            out.append("    public static int mapEffect767To769(int id767) {")
            out.append("        if (id767 < 0 || id767 > MAX_EFFECT_767) {")
            out.append("            logUnknownComposedOnce(\"767→769\", \"エフェクト\", id767);")
            out.append("            return SPEED_EFFECT_769;")
            out.append("        }")
            out.append("        return id767;")
            out.append("    }")
            out.append("")
            out.append("    /**")
            out.append("     * 769のエフェクトID → 767のエフェクトID。0〜38 は同一のため恒等写像。")
            out.append("     * 範囲外は Speed（0）。")
            out.append("     */")
            out.append("    public static int mapEffect769To767(int id769) {")
            out.append("        if (id769 < 0 || id769 > MAX_EFFECT_769) {")
            out.append("            logUnknownComposedOnce(\"769→767\", \"エフェクト\", id769);")
            out.append("            return SPEED_EFFECT_767;")
            out.append("        }")
            out.append("        return id769;")
            out.append("    }")
            out.append("")
            out.append("    /**")
            out.append("     * 767のブロックエンティティ種別ID → 769の種別ID。")
            out.append("     * vault（43）は769にもあるが、creaking_heart（10）の挿入で")
            out.append("     * piston 以降（10〜43）は +1 ずれる。")
            out.append("     * 範囲外の種別は NBT の中身が種別専用のため安全に代替できず")
            out.append("     * {@link #DROP}。")
            out.append("     */")
            out.append("    public static int mapBlockEntityType767To769(int id767) {")
            out.append("        if (id767 < 0 || id767 > BLOCK_ENTITY_VAULT_766) {")
            out.append("            logUnknownComposedOnce(\"767→769\", \"ブロックエンティティ種別\", id767);")
            out.append("            return DROP;")
            out.append("        }")
            out.append("        return id767 < 10 ? id767 : id767 + 1;")
            out.append("    }")
            out.append("")
            out.append("    /**")
            out.append("     * 769のブロックエンティティ種別ID → 767の種別ID。")
            out.append("     * creaking_heart（10）は767に無いため {@link #DROP}。")
            out.append("     * piston 以降（11〜44）は -1 ずれる。vault は 769=44 → 767=43。")
            out.append("     */")
            out.append("    public static int mapBlockEntityType769To767(int id769) {")
            out.append("        if (id769 < 0 || id769 > BLOCK_ENTITY_VAULT_769) {")
            out.append("            logUnknownComposedOnce(\"769→767\", \"ブロックエンティティ種別\", id769);")
            out.append("            return DROP;")
            out.append("        }")
            out.append("        if (id769 == BLOCK_ENTITY_CREAKING_HEART_769) {")
            out.append("            return DROP;")
            out.append("        }")
            out.append("        return id769 < 10 ? id769 : id769 - 1;")
            out.append("    }")
            out.append("")
            out.append("    /**")
            out.append("     * 音の名前を769向けに変換する。767の音の名前のうち 768で削除された")
            out.append("     * ヤギの角笛の音2件は代替の名前にする。769での改名・削除は無い。")
            out.append("     * {@code null} は石の破壊音にする。")
            out.append("     */")
            out.append("    public static String mapSoundName767To769(String name) {")
            out.append("        return mapSoundNameComposed(name, SOUND_NAME_FALLBACK_767_TO_769_MAP);")
            out.append("    }")
            out.append("")
            out.append("    /**")
            out.append("     * 音の名前を767向けに変換する。769の音の名前のうち 767に無いものは")
            out.append("     * 代替の音の名前にする（数値の合成表と一致させる）。")
            out.append("     * {@code null} は石の破壊音にする。")
            out.append("     */")
            out.append("    public static String mapSoundName769To767(String name) {")
            out.append("        return mapSoundNameComposed(name, SOUND_NAME_FALLBACK_769_TO_767_MAP);")
            out.append("    }")
    return "\n".join(out)

# バナー・絵画・装飾パターン・ダメージ種別の 769 向け（768 と同一のものは
# 恒等写像。ダメージ種別も 768 と同一の 49 件）。
DAMAGE_TYPE_769 = """
    /** 769のダメージ種別最大ID（49件。768と同一）。 */
    public static final int MAX_DAMAGE_TYPE_769 = 48;

    /** ダメージ種別ID：768 → 769（768と同一の 49 件で並びも同じため恒等写像）。
     * 実サーバーの Registry Data での確認は統合テストで行う。 */
    private static final int[] DAMAGE_TYPE_769_MAP = {
        0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19,
        20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32, 33, 34, 35, 36,
        37, 38, 39, 40, 41, 42, 43, 44, 45, 46, 47, 48
    };

    /** ダメージ種別ID：769 → 768（768と同一の 49 件で並びも同じため恒等写像）。 */
    private static final int[] DAMAGE_TYPE_REVERSE_769_MAP = {
        0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19,
        20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32, 33, 34, 35, 36,
        37, 38, 39, 40, 41, 42, 43, 44, 45, 46, 47, 48
    };
"""

SMALL_REGISTRY_CONSTANTS = """
    /** 769のバナーパターン最大ID（43件。768と同一）。 */
    public static final int MAX_BANNER_PATTERN_769 = 42;

    /** 769の絵画バリアント最大ID（50件。768と同一）。 */
    public static final int MAX_PAINTING_VARIANT_769 = 49;

    /** 769の装飾パターン最大ID（18件。768と同一）。 */
    public static final int MAX_TRIM_PATTERN_769 = 17;
"""


def small_registry_accessors():
    out = []
    specs = [
        ("BannerPattern", "バナーパターン", "MAX_BANNER_PATTERN_768", "MAX_BANNER_PATTERN_769"),
        ("PaintingVariant", "絵画バリアント", "MAX_PAINTING_VARIANT_768", "MAX_PAINTING_VARIANT_769"),
        ("TrimPattern", "装飾パターン", "MAX_TRIM_PATTERN_768", "MAX_TRIM_PATTERN_769"),
    ]
    for name, jp, max768, max769 in specs:
        out.append(f"    /** 768の{jp}ID → 769の{jp}ID。768と769で同一のため恒等写像。範囲外は {{@link #DROP}}。 */")
        out.append(f"    public static int map{name}768To769(int id768) {{")
        out.append(f"        if (id768 < 0 || id768 > {max768}) {{")
        out.append(f"            logUnknownOnce(PROTOCOL_769, \"{jp}\", id768);")
        out.append("            return DROP;")
        out.append("        }")
        out.append("        return id768;")
        out.append("    }")
        out.append("")
        out.append(f"    /** 769の{jp}ID → 768の{jp}ID。768と769で同一のため恒等写像。範囲外は {{@link #DROP}}。 */")
        out.append(f"    public static int map{name}769To768(int id769) {{")
        out.append(f"        if (id769 < 0 || id769 > {max769}) {{")
        out.append(f"            logUnknownOnce(PROTOCOL_769, \"{jp}\", id769);")
        out.append("            return DROP;")
        out.append("        }")
        out.append("        return id769;")
        out.append("    }")
        out.append("")
        for src in ("763", "764", "765", "766", "767"):
            # 768 のメソッドを経由する（763／764／765／766／767→768 と
            # 768→763／764／765／766／767 は既にある）。
            out.append(f"    /** {src}の{jp}ID → 769の{jp}ID（768経由）。 */")
            out.append(f"    public static int map{name}{src}To769(int id{src}) {{")
            out.append(f"        return map{name}768To769(map{name}{src}To768(id{src}));")
            out.append("    }")
            out.append("")
            out.append(f"    /** 769の{jp}ID → {src}の{jp}ID（768経由）。 */")
            out.append(f"    public static int map{name}769To{src}(int id769) {{")
            out.append(f"        return map{name}768To{src}(map{name}769To768(id769));")
            out.append("    }")
            out.append("")
    return "\n".join(out)


def damage_accessors():
    out = []
    out.append("    /** 768のダメージ種別ID → 769のダメージ種別ID。768と同一のため恒等写像。範囲外は {@link #DROP}。 */")
    out.append("    public static int mapDamageType768To769(int id768) {")
    out.append("        if (id768 < 0 || id768 > MAX_DAMAGE_TYPE_768) {")
    out.append("            logUnknownOnce(PROTOCOL_769, \"ダメージ種別\", id768);")
    out.append("            return DROP;")
    out.append("        }")
    out.append("        return DAMAGE_TYPE_769_MAP[id768];")
    out.append("    }")
    out.append("")
    out.append("    /** 769のダメージ種別ID → 768のダメージ種別ID。768と同一のため恒等写像。範囲外は {@link #DROP}。 */")
    out.append("    public static int mapDamageType769To768(int id769) {")
    out.append("        if (id769 < 0 || id769 > MAX_DAMAGE_TYPE_769) {")
    out.append("            logUnknownOnce(PROTOCOL_769, \"ダメージ種別\", id769);")
    out.append("            return DROP;")
    out.append("        }")
    out.append("        return DAMAGE_TYPE_REVERSE_769_MAP[id769];")
    out.append("    }")
    out.append("")
    for src in ("763", "764", "765", "766", "767"):
        # ダメージ種別は 768 のメソッドを経由する。
        out.append(f"    /** {src}のダメージ種別ID → 769のダメージ種別ID（768経由）。 */")
        out.append(f"    public static int mapDamageType{src}To769(int id{src}) {{")
        out.append(f"        return mapDamageType768To769(mapDamageType{src}To768(id{src}));")
        out.append("    }")
        out.append("")
        out.append(f"    /** 769のダメージ種別ID → {src}のダメージ種別ID（768経由）。 */")
        out.append(f"    public static int mapDamageType769To{src}(int id769) {{")
        out.append(f"        return mapDamageType768To{src}(mapDamageType769To768(id769));")
        out.append("    }")
        out.append("")
    return "\n".join(out)


JAVADOC_769 = """ *
 * <p>769（1.21.4）向けの表も同じ作り方で持つ（{@code *_769}）。
 * 769は768に近く、差分は次の通り（ViaVersion mapping-1.21.4.json と
 * mapping-1.21.2.json、minecraft-data 1.21.4 の突き合わせ）。
 * ブロック状態は新557件・削除9件（creaking_heart のプロパティが
 * creaking=disabled／dormant／active から active＋natural の2ブールに
 * 変わった）。ブロックは新11件（樹脂・アイブロッサム類）。
 * アイテムは新10件（同上＋樹脂レンガ）。エンティティは新0件・削除1件
 * （creaking_transient）。音は新15件・削除0件（アイブロッサム・樹脂・
 * クリーキングの身震い）。パーティクルは新1件（pale_oak_leaves が
 * 34 に挿入され、34 以降が +1 ずれる）。メニュー・エフェクト・絵画・
 * 旗の模様・トリム・ジュークボックスの曲・楽器・データコンポーネント
 * 種別・ブロックエンティティ種別・ダメージ種別は同一。属性は 32件
 * 同士で同一、エンチャントは 42件同士で並びは同一。バイオームは
 * pale_garden が 39 に入る（768 では winter_drop データパックが
 * 無効のため既定では現れなかったものが、1.21.4 では通常のバイオーム
 * になる。実バニラの Registry Data での確認は統合テストで行う）。
 * 768→769 の隣接表と 763／764／765／766／767→769 の合成表を持ち、
 * 逆向き 769→768 は反転して作る。
"""
def insert_after(java, anchor, block, label):
    if anchor not in java:
        print(f"アンカーが見つからない（{label}）", file=sys.stderr)
        return None
    return java.replace(anchor, anchor + block, 1)


def main():
    if (not TABLES769_FILE.exists() or not TABLES769_COMPOSED_FILE.exists()
            or not all(p.exists() for p in TABLES769_COMPOSED_DOWN_FILES.values())):
        print("RegistryTables769.java / RegistryTables769ComposedUp.java / "
              "RegistryTables769Down*.java が無い。"
              "先に insert-769-tables.py を実行すること", file=sys.stderr)
        return 1
    java = JAVA_FILE.read_text(encoding="utf-8")
    checks = [
        ("PROTOCOL_769", "769 の定数・表・アクセサは既にある"),
        ("BLOCK_STATE_769_MAP", "769 の展開済み表は既にある"),
        ("mapBlockState768To769", "769 のアクセサは既にある"),
    ]
    for marker, msg in checks:
        if marker in java:
            print(msg, file=sys.stderr)
            return 1

    # import static。
    java = insert_after(java,
                        "import static io.detergentmc.protocol.RegistryTables768Down766.*;\n",
                        "import static io.detergentmc.protocol.RegistryTables769.*;\n"
                        "import static io.detergentmc.protocol.RegistryTables769ComposedUp.*;\n"
                        "import static io.detergentmc.protocol.RegistryTables769Down763.*;\n"
                        "import static io.detergentmc.protocol.RegistryTables769Down764.*;\n"
                        "import static io.detergentmc.protocol.RegistryTables769Down765.*;\n"
                        "import static io.detergentmc.protocol.RegistryTables769Down766.*;\n"
                        "import static io.detergentmc.protocol.RegistryTables769Down767.*;\n",
                        "import static RegistryTables769")
    if java is None:
        return 1

    # クラスの javadoc に 769 の段落を追加（768 の段落の直後）。
    anchor = (" * 767→768 の隣接表と 763／764／765／766→768 の合成表を持ち、逆向き\n"
              " * 768→767 は反転して作る。\n")
    java = insert_after(java, anchor, JAVADOC_769, "javadoc")
    if java is None:
        return 1

    # 定数。
    java = insert_after(java,
                        "    public static final int PROTOCOL_768 = 768;\n",
                        "    public static final int PROTOCOL_769 = 769;\n", "PROTOCOL_769")
    if java is None:
        return 1
    java = insert_after(java,
                        "    public static final int MAX_EFFECT_768 = 38;\n",
                        CONSTANTS_MAX, "MAX_769")
    if java is None:
        return 1
    java = insert_after(java,
                        "    public static final int SPEED_EFFECT_768 = 0;\n",
                        CONSTANTS_FALLBACK, "769 の代替先")
    if java is None:
        return 1
    java = insert_after(java,
                        "    public static final int BLOCK_ENTITY_VAULT_768 = 44;\n",
                        CONSTANTS_BLOCK_ENTITY, "769 のブロックエンティティ種別")
    if java is None:
        return 1

    # 隣接表（768→769）。MENU_768_MAP の直後。
    anchor = ("    private static final int[] MENU_768_MAP =\n"
              "            expand(MENU_768_RANGES, MENU_768_OVERRIDES, MAX_MENU_767 + 1,\n"
              "                    CRAFTING_MENU_768);\n")
    java = insert_after(java, anchor, adjacent_maps() + "\n", "隣接表 769")
    if java is None:
        return 1

    # 合成表。MENU_768_TO_766_MAP の直後。
    anchor = ("    private static final int[] MENU_768_TO_766_MAP =\n"
              "            expand(MENU_768_TO_766_RANGES, MENU_768_TO_766_OVERRIDES,\n"
              "                    25, CRAFTING_MENU_766);\n")
    java = insert_after(java, anchor, "\n" + composed_maps() + "\n", "合成表 769")
    if java is None:
        return 1

    # 反転表（769→768）。MENU_UP_768_MAP の直後。
    anchor = ("    private static final int[] MENU_UP_768_MAP = invert(\n"
              "            MENU_768_RANGES, MENU_768_OVERRIDES,\n"
              "            MAX_MENU_767 + 1, MAX_MENU_768 + 1,\n"
              "            CRAFTING_MENU_767, null);\n")
    java = insert_after(java, anchor, up_maps() + "\n", "反転表 769")
    if java is None:
        return 1

    # 音の名前の表。
    anchor = ("    private static final Map<String, String> SOUND_NAME_FALLBACK_768_TO_766_MAP =\n"
              "            buildSoundNameMap(SOUND_NAME_FALLBACKS_768_TO_766);\n")
    java = insert_after(java, anchor, SOUND_NAME_MAPS, "音の名前の表 769")
    if java is None:
        return 1

    # ログ。
    anchor = ("    private static void logUnknownUp768Once(String kind, int id768) {\n"
              "        if (LOGGED_UNKNOWN.add(\"up768:\" + kind + \":\" + id768)) {\n"
              "            LOG.warning(\"768に無い\" + kind + \"の番号が来たため代替に落とします（768 id=\" + id768 + \"）。\");\n"
              "        }\n"
              "    }\n")
    java = insert_after(java, anchor, LOG_UP_769, "logUnknownUp769Once")
    if java is None:
        return 1

    # アクセサ（a）768→769。mapSoundName767To768 の直後。
    anchor = ("    public static String mapSoundName767To768(String name) {\n"
              "        return mapSoundNameComposed(name, SOUND_NAME_FALLBACK_767_TO_768_MAP);\n"
              "    }\n")
    java = insert_after(java, anchor, "\n" + adjacent_accessors() + "\n", "アクセサ 768→769")
    if java is None:
        return 1

    # アクセサ（b）769→768。mapSoundNameUp768 の直後。
    anchor = ("    public static String mapSoundNameUp768(String name) {\n"
              "        return mapSoundNameComposed(name, SOUND_NAME_FALLBACK_768_TO_767_MAP);\n"
              "    }\n")
    java = insert_after(java, anchor, "\n" + up_accessors() + "\n", "アクセサ 769→768")
    if java is None:
        return 1

    # アクセサ（c）763／764／765／766／767↔769。mapSoundName768To766 の直後。
    anchor = ("    public static String mapSoundName768To766(String name) {\n"
              "        return mapSoundNameComposed(name, SOUND_NAME_FALLBACK_768_TO_766_MAP);\n"
              "    }\n")
    java = insert_after(java, anchor, "\n" + composed_accessors() + "\n", "アクセサ 763/764/765/766/767↔769")
    if java is None:
        return 1

    # バナー・絵画・装飾パターン・ダメージ種別の定数。
    java = insert_after(java,
                        "    /** 768のバナーパターン最大ID（43件。767と同一）。 */\n"
                        "    public static final int MAX_BANNER_PATTERN_768 = 42;\n",
                        SMALL_REGISTRY_CONSTANTS, "バナー等の769定数")
    if java is None:
        return 1
    anchor = ("    private static final int[] DAMAGE_TYPE_REVERSE_768_MAP = {\n"
              "        0, 1, 2, 3, 4, 5, 6, 7, -1, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18,\n"
              "        19, 20, 21, 22, 23, 24, -1, 25, 26, 27, 28, 29, 30, 31, 32, 33, 34, 35,\n"
              "        36, 37, 38, 39, 40, 41, 42, 43, 44, 45, 46\n"
              "    };\n")
    java = insert_after(java, anchor, DAMAGE_TYPE_769, "ダメージ種別769の表")
    if java is None:
        return 1

    # バナー・絵画・装飾パターン・ダメージ種別のアクセサ。
    anchor = ("    /** 768のバナーパターンID → 766のバナーパターンID（766の並びは766と同じ）。 */\n"
              "    public static int mapBannerPattern768To766(int id768) {\n"
              "        return mapBannerPattern767To766(mapBannerPattern768To767(id768));\n"
              "    }\n")
    banner_painting_trim = small_registry_accessors()
    java = insert_after(java, anchor, "\n" + banner_painting_trim + "\n", "バナー等の769アクセサ")
    if java is None:
        return 1
    anchor = ("    /** 768のダメージ種別ID → 766のダメージ種別ID（766の並びは766と同じ）。 */\n"
              "    public static int mapDamageType768To766(int id768) {\n"
              "        return mapDamageType767To766(mapDamageType768To767(id768));\n"
              "    }\n")
    java = insert_after(java, anchor, "\n" + damage_accessors() + "\n", "ダメージ種別769のアクセサ")
    if java is None:
        return 1

    # プロトコル番号で切り替える変換（担当2の配線用）に 769 を追加。
    dispatches = [
        ("mapBlockState", "mapBlockState765To768(id765)", "mapBlockState765To769(id765)"),
        ("mapItem", "mapItem765To768(id765)", "mapItem765To769(id765)"),
        ("mapItemReverse", "mapItem768To765(id)", "mapItem769To765(id)"),
        ("mapEntityType", "mapEntityType765To768(id765)", "mapEntityType765To769(id765)"),
        ("mapSound", "mapSound765To768(id765)", "mapSound765To769(id765)"),
        ("mapParticle", "mapParticle765To768(id765)", "mapParticle765To769(id765)"),
        ("mapBlock", "mapBlock765To768(id765)", "mapBlock765To769(id765)"),
        ("mapMenu", "mapMenu765To768(id765)", "mapMenu765To769(id765)"),
        ("mapEffect", "mapEffect765To768(id765)", "mapEffect765To769(id765)"),
        ("mapBlockEntityType", "mapBlockEntityType765To768(id765)", "mapBlockEntityType765To769(id765)"),
        ("mapSoundName", "mapSoundName765To768(name)", "mapSoundName765To769(name)"),
    ]
    for label, old_call, new_call in dispatches:
        if label == "mapItemReverse":
            old = ("        if (clientProtocol == PROTOCOL_768) {\n"
                   "            // 768クライアントのアイテムID → 765サーバーのアイテムID。\n"
                   "            // mapItemReverse768 は 768→767 のため、ここでは合成表を使う。\n"
                   "            return mapItem768To765(id);\n"
                   "        }\n")
            new = (old
                   + "        if (clientProtocol == PROTOCOL_769) {\n"
                   "            // 769クライアントのアイテムID → 765サーバーのアイテムID。\n"
                   "            // mapItemReverse769 は 769→768 のため、ここでは合成表を使う。\n"
                   "            return mapItem769To765(id);\n"
                   "        }\n")
            if old not in java:
                print("ディスパッチのアンカーが見つからない（mapItemReverse）", file=sys.stderr)
                return 1
            java = java.replace(old, new, 1)
            continue
        old = ("        if (clientProtocol == PROTOCOL_768) {\n"
               f"            return {old_call};\n"
               "        }\n")
        if old not in java:
            print(f"ディスパッチのアンカーが見つからない（{label}）", file=sys.stderr)
            return 1
        java = java.replace(old, old
                            + "        if (clientProtocol == PROTOCOL_769) {\n"
                            + f"            return {new_call};\n"
                            + "        }\n", 1)
    java = java.replace(
        "    // 765は素通し、766は766の表、767は767の表、768は768の表、\n"
        "    // それより古い未知の番号は763の表を使う。767／768は765以上だが\n"
        "    // 素通しではない（>765 の分岐より先に 767／768 を判定すること）。",
        "    // 765は素通し、766は766の表、767は767の表、768は768の表、\n"
        "    // 769は769の表、それより古い未知の番号は763の表を使う。\n"
        "    // 767／768／769は765以上だが素通しではない（>765 の分岐より\n"
        "    // 先に 767／768／769 を判定すること）。",
        1)

    JAVA_FILE.write_text(java, encoding="utf-8")
    print("RegistryRemapper.java に 769 の定数・表・アクセサを挿入した")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
