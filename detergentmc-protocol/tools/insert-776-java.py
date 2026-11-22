#!/usr/bin/env python3
"""tools/insert-775-java.py で入れた *_775 表の上に、RegistryRemapper.java
へ 776 対応の定数・展開済み表（*_776_MAP）・反転表（*_UP_776_MAP）・
アクセサを挿入する（1回だけの作業用）。

  - 定数：PROTOCOL_776、MAX_*_776、776 側の代替先。
  - 隣接表（775→776）: *_776_MAP と ITEM_REVERSE_776_MAP と
    ATTRIBUTE_776_MAP／ATTRIBUTE_REVERSE_776_MAP／ENCHANTMENT_776_MAP／
    ENCHANTMENT_REVERSE_776_MAP。
  - 合成表（763↔776、764↔776、765↔776、766↔776、767↔776、768↔776、769↔776、770↔776、771↔776、772↔776、773↔776、774↔776）: *_763_TO_776_MAP など168件。
  - 775↔776 は隣接表（*_776_MAP）と反転表（*_UP_776_MAP）で扱うため合成表は作らない（775と同じ方針）。
  - 逆向き（776→775）: *_UP_776_MAP（invert()。アイテムは
    ITEM_REVERSE_776_MAP をそのまま使う。775→776 に補正は無い）。
  - 音の名前の表（String）: SOUND_NAME_FALLBACK_*_776_MAP など23件。
  - アクセサ：775↔776、776→775（上向き）、763／764／765／766／767／768／769／770／771／772／773／774↔776。
    離れた組み合わせのエフェクト・ブロックエンティティ種別・音の名前は
    775 のメソッドへ委譲する（775→776 は隣接のため）。
  - エフェクト：776 は 775 と同一の 40 件（0〜39）のため恒等写像。
    属性は 775 が 35 件、776 が 40 件（新 5 件）、エンチャントは
    43 件同士で並びは同一（26.2 の datapack の enchantment で確認）。
    ブロックエンティティ種別は 49 件だが bed が削除され potent_sulfur
    が追加された（26.2 の jar で確認。実バニラの Registry Data での
    確認は統合テストで行う）。
    バナー・絵画・装飾パターンは 775 と同一と仮定。
    ダメージ種別は 775 が 50 件、776 が 51 件（新 sulfur_cube_hot）。
    バイオームは 775 が 65 件、776 が 66 件（新 sulfur_caves）。
    ジュークボックスの歌は 775 が 21 件、776 が 22 件（新 bounce）。
    （26.2 の datapack で確認。実バニラの Registry Data での確認は統合テストで行う）。
  - パーティクルの代替先は entity_effect=28（776 で 7 件挿入され
    775 の 21 から 28 にずれた。775 の定数は 21 のまま正しい）。
  - 注意：このスクリプトで挿入した展開済み表（隣接12・合成168・上向き6の
    計186件）は、そのままでは RegistryRemapper.java の <clinit> が 64KB を
    超えてコンパイルできない。そのため挿入後に
    tools/move-776-expanded-tables.py で RegistryTables776Expanded.java
    へ移動してある。再実行時はこの移動もやり直すこと。
"""

import re
import sys
from pathlib import Path

SCRIPT_DIR = Path(__file__).resolve().parent
REPO_ROOT = SCRIPT_DIR.parent.parent
JAVA_FILE = (REPO_ROOT / "detergentmc-protocol" / "src" / "main" / "java"
             / "io" / "detergentmc" / "protocol" / "RegistryRemapper.java")
TABLES776_FILE = (REPO_ROOT / "detergentmc-protocol" / "src" / "main" / "java"
                  / "io" / "detergentmc" / "protocol" / "RegistryTables776.java")
TABLES776_COMPOSED_FILE = (REPO_ROOT / "detergentmc-protocol" / "src" / "main" / "java"
                  / "io" / "detergentmc" / "protocol" / "RegistryTables776ComposedUpA.java")
TABLES776_COMPOSED_DOWN_FILES = {
    suffix: (REPO_ROOT / "detergentmc-protocol" / "src" / "main" / "java"
             / "io" / "detergentmc" / "protocol"
             / f"RegistryTables776Down{suffix}.java")
    for suffix in ("763", "764", "765", "766", "767", "768", "769", "770", "771", "772", "773", "774")
}


KINDS = [
    # kind: (Java メソッド名の語尾, RANGES の接頭辞, 日本語名,
    #        763→776 の件数, 776→763 の件数, 764→776 の件数, 776→764 の件数,
    #        765→776 の件数, 776→765 の件数, 766→776 の件数, 776→766 の件数,
    #        767→776 の件数, 776→767 の件数, 768→776 の件数, 776→768 の件数,
    #        769→776 の件数, 776→769 の件数, 770→776 の件数, 776→770 の件数,
    #        771→776 の件数, 776→771 の件数, 772→776 の件数, 776→772 の件数,
    #        773→776 の件数, 776→773 の件数, 774→776 の件数, 776→774 の件数,
    #        763 側の代替先, 764 側の代替先, 765 側の代替先, 766 側の代替先,
    #        767 側の代替先, 768 側の代替先, 769 側の代替先, 770 側の代替先,
    #        771 側の代替先, 772 側の代替先, 773 側の代替先, 774 側の代替先,
    #        775 側の代替先, 776 側の代替先)
    ("BlockState", "BLOCK_STATE", "ブロック状態", 24135, 32366, 24276, 32366, 26644, 32366, 26684, 32366, 26684, 32366, 27318, 32366, 27866, 32366, 27914, 32366, 27946, 32366, 27946, 32366, 29671, 32366, 29671, 32366,
     "STONE_BLOCK_STATE_763", "STONE_BLOCK_STATE_764", "STONE_BLOCK_STATE_765", "STONE_BLOCK_STATE_766", "STONE_BLOCK_STATE_767", "STONE_BLOCK_STATE_768", "STONE_BLOCK_STATE_769", "STONE_BLOCK_STATE_770", "STONE_BLOCK_STATE_771", "STONE_BLOCK_STATE_772", "STONE_BLOCK_STATE_773", "STONE_BLOCK_STATE_774", "STONE_BLOCK_STATE_775", "STONE_BLOCK_STATE_776"),
    ("Item", "ITEM", "アイテム", 1255, 1537, 1255, 1537, 1312, 1537, 1330, 1537, 1333, 1537, 1375, 1537, 1385, 1537, 1396, 1537, 1415, 1537, 1416, 1537, 1488, 1537, 1505, 1537,
     "STONE_ITEM_763", "STONE_ITEM_764", "STONE_ITEM_765", "STONE_ITEM_766", "STONE_ITEM_767", "STONE_ITEM_768", "STONE_ITEM_769", "STONE_ITEM_770", "STONE_ITEM_771", "STONE_ITEM_772", "STONE_ITEM_773", "STONE_ITEM_774", "STONE_ITEM_775", "STONE_ITEM_776"),
    ("EntityType", "ENTITY_TYPE", "エンティティ種別", 124, 158, 124, 158, 126, 158, 130, 158, 130, 158, 150, 158, 149, 158, 150, 158, 151, 158, 151, 158, 153, 158, 157, 158,
     "PIG_ENTITY_TYPE_763", "PIG_ENTITY_TYPE_764", "PIG_ENTITY_TYPE_765", "PIG_ENTITY_TYPE_766", "PIG_ENTITY_TYPE_767", "PIG_ENTITY_TYPE_768", "PIG_ENTITY_TYPE_769", "PIG_ENTITY_TYPE_770", "PIG_ENTITY_TYPE_771", "PIG_ENTITY_TYPE_772", "PIG_ENTITY_TYPE_773", "PIG_ENTITY_TYPE_774", "PIG_ENTITY_TYPE_775", "PIG_ENTITY_TYPE_776"),
    ("Sound", "SOUND", "音", 1474, 1968, 1485, 1968, 1539, 1968, 1607, 1968, 1611, 1968, 1636, 1968, 1651, 1968, 1702, 1968, 1727, 1968, 1728, 1968, 1771, 1968, 1838, 1968,
     "GENERIC_SOUND_763", "GENERIC_SOUND_764", "GENERIC_SOUND_765", "GENERIC_SOUND_766", "GENERIC_SOUND_767", "GENERIC_SOUND_768", "GENERIC_SOUND_769", "GENERIC_SOUND_770", "GENERIC_SOUND_771", "GENERIC_SOUND_772", "GENERIC_SOUND_773", "GENERIC_SOUND_774", "GENERIC_SOUND_775", "GENERIC_SOUND_776"),
    ("Particle", "PARTICLE", "パーティクル", 95, 125, 95, 125, 101, 125, 109, 125, 109, 125, 111, 125, 112, 125, 114, 125, 114, 125, 114, 125, 115, 125, 115, 125,
     "AMBIENT_PARTICLE_763", "AMBIENT_PARTICLE_764", "AMBIENT_PARTICLE_765", "ENTITY_EFFECT_PARTICLE_766", "ENTITY_EFFECT_PARTICLE_767", "ENTITY_EFFECT_PARTICLE_768", "ENTITY_EFFECT_PARTICLE_769", "ENTITY_EFFECT_PARTICLE_770", "ENTITY_EFFECT_PARTICLE_771", "ENTITY_EFFECT_PARTICLE_772", "ENTITY_EFFECT_PARTICLE_773", "ENTITY_EFFECT_PARTICLE_774", "ENTITY_EFFECT_PARTICLE_775", "ENTITY_EFFECT_PARTICLE_776"),
    ("Block", "BLOCK", "ブロック", 1003, 1196, 1003, 1196, 1058, 1196, 1060, 1196, 1060, 1196, 1084, 1196, 1095, 1196, 1104, 1196, 1105, 1196, 1105, 1196, 1166, 1196, 1166, 1196,
     "STONE_BLOCK_763", "STONE_BLOCK_764", "STONE_BLOCK_765", "STONE_BLOCK_766", "STONE_BLOCK_767", "STONE_BLOCK_768", "STONE_BLOCK_769", "STONE_BLOCK_770", "STONE_BLOCK_771", "STONE_BLOCK_772", "STONE_BLOCK_773", "STONE_BLOCK_774", "STONE_BLOCK_775", "STONE_BLOCK_776"),
    ("Menu", "MENU", "メニュー種別", 24, 25, 24, 25, 25, 25, 25, 25, 25, 25, 25, 25, 25, 25, 25, 25, 25, 25, 25, 25, 25, 25, 25, 25,
     "CRAFTING_MENU_763", "CRAFTING_MENU_764", "CRAFTING_MENU_765", "CRAFTING_MENU_766", "CRAFTING_MENU_767", "CRAFTING_MENU_768", "CRAFTING_MENU_769", "CRAFTING_MENU_770", "CRAFTING_MENU_771", "CRAFTING_MENU_772", "CRAFTING_MENU_773", "CRAFTING_MENU_774", "CRAFTING_MENU_775", "CRAFTING_MENU_776"),
]

CONSTANTS_MAX = """
    /** 776側の最大の番号（ViaVersion mapping-26.2.json の件数−1）。 */
    public static final int MAX_BLOCK_STATE_776 = 32365;
    public static final int MAX_ITEM_776 = 1536;
    public static final int MAX_ENTITY_TYPE_776 = 157;
    public static final int MAX_SOUND_776 = 1967;
    public static final int MAX_PARTICLE_776 = 124;
    public static final int MAX_BLOCK_776 = 1195;
    public static final int MAX_MENU_776 = 24;
    /** 776側のエフェクトは 0〜39（Speed=0。775 と同一の 40 件。
        26.2 の jar の BuiltInRegistries.MOB_EFFECT で確認）。 */
    public static final int MAX_EFFECT_776 = 39;
    /** 776側の属性は 0〜39（775 の 35 件に新規 5 件。
        ViaVersion mapping-26.2.json と 26.2 の jar で確認）。 */
    public static final int MAX_ATTRIBUTE_776 = 39;
    /** エンチャントは 776 は 0〜42（43件。775 と同一の並び。
        26.2 の datapack の enchantment で確認。実機の Registry Data
        での確認は統合テストで行う）。 */
    public static final int MAX_ENCHANTMENT_776 = 42;
"""

CONSTANTS_FALLBACK = """
    /** 776側の代替先（mapping-26.2.json の番号。Speed だけ 0ベースで 0）。
     * ブタは 776 では 100（775 と同一。エンティティの並びは同一）、
     * 石の破壊音は 776 では 1596（新音 66 件のうち 4 件が 1592 より
     * 前に挿入され、775 の 1592 から +4）。
     * パーティクルの代替は entity_effect=28（775 では 21。776 で 7 件
     * 挿入され 21→28 にずれた）。 */
    public static final int STONE_BLOCK_STATE_776 = 1;
    public static final int AIR_BLOCK_STATE_776 = 0;
    public static final int STONE_ITEM_776 = 1;
    public static final int PIG_ENTITY_TYPE_776 = 100;
    public static final int GENERIC_SOUND_776 = 1596; // block.stone.break
    public static final int ENTITY_EFFECT_PARTICLE_776 = 28;
    public static final int STONE_BLOCK_776 = 1;
    public static final int CRAFTING_MENU_776 = 12;
    public static final int SPEED_EFFECT_776 = 0;
"""

CONSTANTS_BLOCK_ENTITY = """
    /** 776のブロックエンティティ種別：49 件（775 と件数は同一だが並びが
        違う。bed（775 では 25）が 26.2 で削除され、potent_sulfur が
        末尾（48）に追加された。26.2 の jar の BuiltInRegistries と
        BlockEntityTypes で確認。shelf=39、trial_spawner=43、vault=44、
        test_block=45、test_instance_block=46、copper_golem_statue=47。
        実バニラ 26.2 の Registry Data での確認は統合テストで行う）。 */
    public static final int BLOCK_ENTITY_SHELF_776 = 39;
    public static final int BLOCK_ENTITY_CREAKING_HEART_776 = 10;
    public static final int BLOCK_ENTITY_TRIAL_SPAWNER_776 = 43;
    public static final int BLOCK_ENTITY_VAULT_776 = 44;
    public static final int BLOCK_ENTITY_TEST_BLOCK_776 = 45;
    public static final int BLOCK_ENTITY_TEST_INSTANCE_BLOCK_776 = 46;
    public static final int BLOCK_ENTITY_COPPER_GOLEM_STATUE_776 = 47;
    public static final int BLOCK_ENTITY_POTENT_SULFUR_776 = 48;
    /** 775 の bed の種別ID（25）。776 には無い。775→776 の変換で
        bed は {@link #DROP} になる。 */
    public static final int BLOCK_ENTITY_BED_775 = 25;
"""


def adjacent_maps():
    lines = []
    for kind in KINDS:
        name, prefix, jp = kind[0], kind[1], kind[2]
        lines.append(
            f"    private static final int[] {prefix}_776_MAP =\n"
            f"            expand({prefix}_776_RANGES, {prefix}_776_OVERRIDES, MAX_{prefix}_775 + 1,\n"
            f"                    {kind[37]});")
    lines.append(
        "    private static final int[] ITEM_REVERSE_776_MAP =\n"
        "            expand(ITEM_REVERSE_776_RANGES, ITEM_REVERSE_776_OVERRIDES, 1537, -1);")
    lines.append(
        "    private static final int[] ATTRIBUTE_776_MAP =\n"
        "            expand(ATTRIBUTE_776_RANGES, ATTRIBUTE_776_OVERRIDES, MAX_ATTRIBUTE_775 + 1, DROP);")
    lines.append(
        "    private static final int[] ATTRIBUTE_REVERSE_776_MAP =\n"
        "            expand(ATTRIBUTE_REVERSE_776_RANGES, ATTRIBUTE_REVERSE_776_OVERRIDES,\n"
        "                    MAX_ATTRIBUTE_776 + 1, DROP);")
    lines.append(
        "    private static final int[] ENCHANTMENT_776_MAP =\n"
        "            expand(ENCHANTMENT_776_RANGES, ENCHANTMENT_776_OVERRIDES,\n"
        "                    MAX_ENCHANTMENT_775 + 1, DROP);")
    lines.append(
        "    private static final int[] ENCHANTMENT_REVERSE_776_MAP =\n"
        "            expand(ENCHANTMENT_REVERSE_776_RANGES, ENCHANTMENT_REVERSE_776_OVERRIDES,\n"
        "                    MAX_ENCHANTMENT_776 + 1, DROP);")
    return "\n".join(lines)


def composed_maps():
    # (接尾辞, 件数の位置, 代替先の位置)。件数・代替先は KINDS の
    # タプルから取る（763_TO_776: 件数 index 3, 代替先 776=index 37／
    # 776_TO_763: 件数 index 4, 代替先 763=index 25）。件数は 3〜24、
    # 代替先は 25（763）〜37（776）。775↔776 は隣接表で扱うため含めない。
    pairs = [
        ("763_TO_776", 3, 40), ("776_TO_763", 4, 27),
        ("764_TO_776", 5, 40), ("776_TO_764", 6, 28),
        ("765_TO_776", 7, 40), ("776_TO_765", 8, 29),
        ("766_TO_776", 9, 40), ("776_TO_766", 10, 30),
        ("767_TO_776", 11, 40), ("776_TO_767", 12, 31),
        ("768_TO_776", 13, 40), ("776_TO_768", 14, 32),
        ("769_TO_776", 15, 40), ("776_TO_769", 16, 33),
        ("770_TO_776", 17, 40), ("776_TO_770", 18, 34),
        ("771_TO_776", 19, 40), ("776_TO_771", 20, 35),
        ("772_TO_776", 21, 40), ("776_TO_772", 22, 36),
        ("773_TO_776", 23, 40), ("776_TO_773", 24, 37),
        ("774_TO_776", 25, 40), ("776_TO_774", 26, 38),
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
            continue  # アイテムは ITEM_REVERSE_776_MAP を使う。
        # 775→776 に補正は無い（775→776 は改名・削除が無い）。反転はそのまま使う。
        corrections = "null"
        # 逆向きの代替先は「775 側」の定数（kind[39]）。
        fb775 = kind[39]
        lines.append(
            f"    private static final int[] {prefix}_UP_776_MAP = invert(\n"
            f"            {prefix}_776_RANGES, {prefix}_776_OVERRIDES,\n"
            f"            MAX_{prefix}_775 + 1, MAX_{prefix}_776 + 1,\n"
            f"            {fb775}, {corrections});")
    return "\n".join(lines)


SOUND_NAME_MAPS = """
    private static final Map<String, String> SOUND_NAME_FALLBACK_776_MAP =
            buildSoundNameMap(SOUND_NAME_FALLBACKS_776);
    private static final Map<String, String> SOUND_NAME_FALLBACK_775_TO_776_MAP =
            buildSoundNameMap(SOUND_NAME_FALLBACKS_775_TO_776);
    private static final Map<String, String> SOUND_NAME_FALLBACK_776_TO_775_MAP =
            buildSoundNameMap(SOUND_NAME_FALLBACKS_776_TO_775);
    private static final Map<String, String> SOUND_NAME_FALLBACK_776_TO_773_MAP =
            buildSoundNameMap(SOUND_NAME_FALLBACKS_776_TO_773);
    private static final Map<String, String> SOUND_NAME_FALLBACK_776_TO_772_MAP =
            buildSoundNameMap(SOUND_NAME_FALLBACKS_776_TO_772);
    private static final Map<String, String> SOUND_NAME_FALLBACK_776_TO_771_MAP =
            buildSoundNameMap(SOUND_NAME_FALLBACKS_776_TO_771);
    private static final Map<String, String> SOUND_NAME_FALLBACK_776_TO_770_MAP =
            buildSoundNameMap(SOUND_NAME_FALLBACKS_776_TO_770);
    private static final Map<String, String> SOUND_NAME_FALLBACK_776_TO_769_MAP =
            buildSoundNameMap(SOUND_NAME_FALLBACKS_776_TO_769);
    private static final Map<String, String> SOUND_NAME_FALLBACK_776_TO_768_MAP =
            buildSoundNameMap(SOUND_NAME_FALLBACKS_776_TO_768);
    private static final Map<String, String> SOUND_NAME_FALLBACK_776_TO_767_MAP =
            buildSoundNameMap(SOUND_NAME_FALLBACKS_776_TO_767);
    private static final Map<String, String> SOUND_NAME_FALLBACK_776_TO_766_MAP =
            buildSoundNameMap(SOUND_NAME_FALLBACKS_776_TO_766);
    private static final Map<String, String> SOUND_NAME_FALLBACK_776_TO_765_MAP =
            buildSoundNameMap(SOUND_NAME_FALLBACKS_776_TO_765);
    private static final Map<String, String> SOUND_NAME_FALLBACK_776_TO_764_MAP =
            buildSoundNameMap(SOUND_NAME_FALLBACKS_776_TO_764);
    private static final Map<String, String> SOUND_NAME_FALLBACK_776_TO_763_MAP =
            buildSoundNameMap(SOUND_NAME_FALLBACKS_776_TO_763);
    private static final Map<String, String> SOUND_NAME_FALLBACK_773_TO_776_MAP =
            buildSoundNameMap(SOUND_NAME_FALLBACKS_773_TO_776);
    private static final Map<String, String> SOUND_NAME_FALLBACK_772_TO_776_MAP =
            buildSoundNameMap(SOUND_NAME_FALLBACKS_772_TO_776);
    private static final Map<String, String> SOUND_NAME_FALLBACK_771_TO_776_MAP =
            buildSoundNameMap(SOUND_NAME_FALLBACKS_771_TO_776);
    private static final Map<String, String> SOUND_NAME_FALLBACK_770_TO_776_MAP =
            buildSoundNameMap(SOUND_NAME_FALLBACKS_770_TO_776);
    private static final Map<String, String> SOUND_NAME_FALLBACK_769_TO_776_MAP =
            buildSoundNameMap(SOUND_NAME_FALLBACKS_769_TO_776);
    private static final Map<String, String> SOUND_NAME_FALLBACK_768_TO_776_MAP =
            buildSoundNameMap(SOUND_NAME_FALLBACKS_768_TO_776);
    private static final Map<String, String> SOUND_NAME_FALLBACK_767_TO_776_MAP =
            buildSoundNameMap(SOUND_NAME_FALLBACKS_767_TO_776);
"""

LOG_UP_776 = """
    private static void logUnknownUp776Once(String kind, int id776) {
        if (LOGGED_UNKNOWN.add("up776:" + kind + ":" + id776)) {
            LOG.warning("776に無い" + kind + "の番号が来たため代替に落とします（776 id=" + id776 + "）。");
        }
    }
"""
def adjacent_accessors():
    out = ["    // ------------------------------------------------------------------",
           "    // 775→776。上の *_776 表を使う。ブロック状態・ブロック・",
           "    // パーティクル・メニュー・属性・バイオーム・エフェクト・",
           "    // エンチャントは同一。アイテムは新1件（金のタンポポ）、",
           "    // エンティティは同一、音は新64件の挿入でずれる。",
           "    // ブロックエンティティ種別は 775 と同一と仮定（49 件、",
           "    // 統合テストで確認）。絵画・ジュークボックスの歌は",
           "    // 775 と同一と仮定。",
           "    // ------------------------------------------------------------------",
           ""]
    for kind in KINDS:
        name, prefix, jp = kind[0], kind[1], kind[2]
        fb776 = kind[40]
        out.append(f"    /** 775の{jp}ID → 776の{jp}ID。範囲外は{fb776} に相当する代替。 */")
        out.append(f"    public static int map{name}775To776(int id775) {{")
        out.append(f"        if (id775 < 0 || id775 > MAX_{prefix}_775) {{")
        out.append(f"            logUnknownOnce(PROTOCOL_776, \"{jp}\", id775);")
        out.append(f"            return {fb776};")
        out.append("        }")
        out.append(f"        return {prefix}_776_MAP[id775];")
        out.append("    }")
        out.append("")
    out.append("    /**")
    out.append("     * 776のアイテムID → 775のアイテムID（サーバー行きスロット用）。")
    out.append("     * 776にしか無いアイテム31件（硫黄・辰砂系・music_disc_bounce など）は 775の石へ。改名は無い。")
    out.append("     * 範囲外はそのまま返す。")
    out.append("     */")
    out.append("    public static int mapItemReverse776(int id776) {")
    out.append("        if (id776 < 0 || id776 >= ITEM_REVERSE_776_MAP.length) {")
    out.append("            logUnknownOnce(PROTOCOL_776, \"アイテム（逆変換）\", id776);")
    out.append("            return id776;")
    out.append("        }")
    out.append("        int mapped = ITEM_REVERSE_776_MAP[id776];")
    out.append("        return mapped < 0 ? id776 : mapped;")
    out.append("    }")
    out.append("")
    out.append("    /**")
    out.append("     * 775のエフェクトID → 776のエフェクトID。775も776も0ベース")
    out.append("     * （Speed は両方 0）で 0〜39 は同一のため恒等写像。")
    out.append("     * 範囲外は Speed（0）。")
    out.append("     */")
    out.append("    public static int mapEffect775To776(int id775) {")
    out.append("        if (id775 < 0 || id775 > MAX_EFFECT_775) {")
    out.append("            logUnknownOnce(PROTOCOL_776, \"エフェクト\", id775);")
    out.append("            return SPEED_EFFECT_776;")
    out.append("        }")
    out.append("        return id775;")
    out.append("    }")
    out.append("")
    out.append("    /**")
    out.append("     * 775のブロックエンティティ種別ID → 776の種別ID。")
    out.append("     * bed（775 では 25）は 776 に無いため {@link #DROP}。")
    out.append("     * 26 以降は -1 ずれる（potent_sulfur は 776 の 48）。")
    out.append("     * 範囲外の種別は NBT の中身が種別専用のため安全に代替できず")
    out.append("     * {@link #DROP}。")
    out.append("     */")
    out.append("    public static int mapBlockEntityType775To776(int id775) {")
    out.append("        if (id775 < 0 || id775 > BLOCK_ENTITY_COPPER_GOLEM_STATUE_775) {")
    out.append("            logUnknownOnce(PROTOCOL_776, \"ブロックエンティティ種別\", id775);")
    out.append("            return DROP;")
    out.append("        }")
    out.append("        if (id775 == BLOCK_ENTITY_BED_775) {")
    out.append("            return DROP;")
    out.append("        }")
    out.append("        return id775 > BLOCK_ENTITY_BED_775 ? id775 - 1 : id775;")
    out.append("    }")
    out.append("")
    out.append("    /**")
    out.append("     * 音の名前（直書き・Stop Sound 用）を776向けに変換する。")
    out.append("     * {@code minecraft:} の有無はそのまま保つ。775の音の名前は")
    out.append("     * 776 にすべてあるため代替は無い。")
    out.append("     */")
    out.append("    public static String mapSoundName775To776(String name) {")
    out.append("        return mapSoundNameComposed(name, SOUND_NAME_FALLBACK_775_TO_776_MAP);")
    out.append("    }")
    out.append("")
    out.append("    /** 775の属性ID → 776の属性ID。並びは同一。範囲外は {@link #DROP}。 */")
    out.append("    public static int mapAttribute775To776(int id775) {")
    out.append("        if (id775 < 0 || id775 > MAX_ATTRIBUTE_775) {")
    out.append("            logUnknownOnce(PROTOCOL_776, \"属性\", id775);")
    out.append("            return DROP;")
    out.append("        }")
    out.append("        return ATTRIBUTE_776_MAP[id775];")
    out.append("    }")
    out.append("")
    out.append("    /** 776の属性ID → 775の属性ID。並びは同一。範囲外は {@link #DROP}。 */")
    out.append("    public static int mapAttribute776To775(int id776) {")
    out.append("        if (id776 < 0 || id776 > MAX_ATTRIBUTE_776) {")
    out.append("            logUnknownOnce(PROTOCOL_776, \"属性\", id776);")
    out.append("            return DROP;")
    out.append("        }")
    out.append("        return ATTRIBUTE_REVERSE_776_MAP[id776];")
    out.append("    }")
    out.append("")
    out.append("    /** 776の属性ID → 属性の名前（Update Attributes の 775向け変換用）。 */")
    out.append("    public static String attributeName776(int id776) {")
    out.append("        if (id776 < 0 || id776 >= ATTRIBUTE_776_NAMES.length) {")
    out.append("            return null;")
    out.append("        }")
    out.append("        return ATTRIBUTE_776_NAMES[id776];")
    out.append("    }")
    out.append("")
    out.append("    /**")
    out.append("     * 属性の名前 → 776の属性ID（765／763／764 は属性が名前形式のため、")
    out.append("     * 776向けに変換するときに使う）。無い名前は {@link #DROP}。")
    out.append("     */")
    out.append("    public static int attributeId776(String name) {")
    out.append("        if (name == null) {")
    out.append("            return DROP;")
    out.append("        }")
    out.append("        String plain = stripMinecraftPrefix(name);")
    out.append("        for (int i = 0; i < ATTRIBUTE_776_NAMES.length; i++) {")
    out.append("            if (ATTRIBUTE_776_NAMES[i].equals(plain)) {")
    out.append("                return i;")
    out.append("            }")
    out.append("        }")
    out.append("        return DROP;")
    out.append("    }")
    out.append("")
    out.append("    /** 775のエンチャントID → 776のエンチャントID。並びは同一。範囲外は {@link #DROP}。 */")
    out.append("    public static int mapEnchantment775To776(int id775) {")
    out.append("        if (id775 < 0 || id775 > MAX_ENCHANTMENT_775) {")
    out.append("            logUnknownOnce(PROTOCOL_776, \"エンチャント\", id775);")
    out.append("            return DROP;")
    out.append("        }")
    out.append("        return ENCHANTMENT_776_MAP[id775];")
    out.append("    }")
    out.append("")
    out.append("    /** 776のエンチャントID → 775のエンチャントID。並びは同一。範囲外は {@link #DROP}。 */")
    out.append("    public static int mapEnchantment776To775(int id776) {")
    out.append("        if (id776 < 0 || id776 > MAX_ENCHANTMENT_776) {")
    out.append("            logUnknownOnce(PROTOCOL_776, \"エンチャント\", id776);")
    out.append("            return DROP;")
    out.append("        }")
    out.append("        return ENCHANTMENT_REVERSE_776_MAP[id776];")
    out.append("    }")
    out.append("")
    out.append("    /**")
    out.append("     * 767のエンチャントID → 776のエンチャントID。767→775 の表を")
    out.append("     * 経由して 775→776 の表を引く。範囲外は {@link #DROP}。")
    out.append("     */")
    out.append("    public static int mapEnchantment767To776(int id767) {")
    out.append("        int id775 = mapEnchantment767To775(id767);")
    out.append("        return id775 == DROP ? DROP : mapEnchantment775To776(id775);")
    out.append("    }")
    out.append("")
    out.append("    /**")
    out.append("     * 776のエンチャントID → 767のエンチャントID。776→775 の逆変換の")
    out.append("     * 結果を 775→767 の逆変換で戻す。範囲外は {@link #DROP}。")
    out.append("     */")
    out.append("    public static int mapEnchantment776To767(int id776) {")
    out.append("        int id775 = mapEnchantment776To775(id776);")
    out.append("        return id775 == DROP ? DROP : mapEnchantment775To767(id775);")
    out.append("    }")
    out.append("")
    out.append("    /**")
    out.append("     * 766のエンチャントID → 776のエンチャントID。766→767 の変換")
    out.append("     * （{@link #mapEnchantment766To767}）を経由して 767→776 の")
    out.append("     * 表を引く。範囲外は {@link #DROP}。")
    out.append("     */")
    out.append("    public static int mapEnchantment766To776(int id766) {")
    out.append("        int id767 = mapEnchantment766To767(id766);")
    out.append("        return id767 == DROP ? DROP : mapEnchantment767To776(id767);")
    out.append("    }")
    out.append("")
    out.append("    /**")
    out.append("     * 776のエンチャントID → 766のエンチャントID。776→767 の逆変換の")
    out.append("     * 結果を 767→766 の逆変換で戻す。範囲外は {@link #DROP}。")
    out.append("     */")
    out.append("    public static int mapEnchantment776To766(int id776) {")
    out.append("        int id767 = mapEnchantment776To767(id776);")
    out.append("        return id767 == DROP ? DROP : mapEnchantment767To766(id767);")
    out.append("    }")
    out.append("")
    out.append("    /**")
    out.append("     * 765のエンチャントID → 776のエンチャントID。765→767 の変換")
    out.append("     * （{@link #mapEnchantment765To767}）を経由して 767→776 の")
    out.append("     * 表を引く。範囲外は {@link #DROP}。")
    out.append("     * 763／764 のエンチャントの並びは 765 と同一のため、")
    out.append("     * 763／764 向けも同じ変換を使う。")
    out.append("     */")
    out.append("    public static int mapEnchantment765To776(int id765) {")
    out.append("        int id767 = mapEnchantment765To767(id765);")
    out.append("        return id767 == DROP ? DROP : mapEnchantment767To776(id767);")
    out.append("    }")
    out.append("")
    out.append("    /** 763のエンチャントID → 776のエンチャントID（765と同じ並び）。 */")
    out.append("    public static int mapEnchantment763To776(int id763) {")
    out.append("        return mapEnchantment765To776(id763);")
    out.append("    }")
    out.append("")
    out.append("    /** 764のエンチャントID → 776のエンチャントID（765と同じ並び）。 */")
    out.append("    public static int mapEnchantment764To776(int id764) {")
    out.append("        return mapEnchantment765To776(id764);")
    out.append("    }")
    out.append("")
    out.append("    /**")
    out.append("     * 776のエンチャントID → 765のエンチャントID。776→767 の逆変換の")
    out.append("     * 結果を 767→765 の変換（{@link #mapEnchantment767To765}）で")
    out.append("     * 戻す。density／breach／wind_burst は 765に無いため")
    out.append("     * {@link #DROP}。範囲外も {@link #DROP}。")
    out.append("     */")
    out.append("    public static int mapEnchantment776To765(int id776) {")
    out.append("        int id767 = mapEnchantment776To767(id776);")
    out.append("        return id767 == DROP ? DROP : mapEnchantment767To765(id767);")
    out.append("    }")
    out.append("")
    out.append("    /** 776のエンチャントID → 763のエンチャントID（765と同じ並び）。 */")
    out.append("    public static int mapEnchantment776To763(int id776) {")
    out.append("        return mapEnchantment776To765(id776);")
    out.append("    }")
    out.append("")
    out.append("    /** 776のエンチャントID → 764のエンチャントID（765と同じ並び）。 */")
    out.append("    public static int mapEnchantment776To764(int id776) {")
    out.append("        return mapEnchantment776To765(id776);")
    out.append("    }")
    return "\n".join(out)


def up_accessors():
    out = ["    // ------------------------------------------------------------------",
           "    // 逆向き（776→775）。上の *_776 表を反転して作った表を使う。",
           "    // アイテムだけは生成スクリプトが名前から作った既存の逆変換表",
           "    // （ITEM_REVERSE_776_MAP）をそのまま使う（反転した表と完全一致",
           "    // することはテストで確認している）。属性とエンチャントの逆向きは",
           "    // mapAttribute776To775／mapEnchantment776To775 を参照。",
           "    // ------------------------------------------------------------------",
           ""]
    for kind in KINDS:
        name, prefix, jp = kind[0], kind[1], kind[2]
        # 逆向きの代替先は「775 側」の定数（kind[39]）。
        fb775 = kind[39]
        if prefix == "ITEM":
            out.append("    /**")
            out.append("     * 776のアイテムID → 775のアイテムID。")
            out.append("     * 776にしか無いアイテム31件（硫黄・辰砂系・music_disc_bounce など）は石へ。範囲外は石（1）。")
            out.append("     */")
            out.append("    public static int mapItemUp776(int id776) {")
            out.append("        if (id776 < 0 || id776 >= ITEM_REVERSE_776_MAP.length) {")
            out.append("            logUnknownUp776Once(\"アイテム\", id776);")
            out.append("            return STONE_ITEM_775;")
            out.append("        }")
            out.append("        int mapped = ITEM_REVERSE_776_MAP[id776];")
            out.append("        return mapped < 0 ? STONE_ITEM_775 : mapped;")
            out.append("    }")
            out.append("")
            continue
        out.append(f"    /** 776の{jp}ID → 775の{jp}ID。範囲外は{fb775} に相当する代替。 */")
        out.append(f"    public static int map{name}Up776(int id776) {{")
        out.append(f"        if (id776 < 0 || id776 > MAX_{prefix}_776) {{")
        out.append(f"            logUnknownUp776Once(\"{jp}\", id776);")
        out.append(f"            return {fb775};")
        out.append("        }")
        out.append(f"        return {prefix}_UP_776_MAP[id776];")
        out.append("    }")
        out.append("")
    out.append("    /**")
    out.append("     * 776のエフェクトID → 775のエフェクトID。0〜39 は同一のため恒等写像。")
    out.append("     * 範囲外は Speed（0）。")
    out.append("     */")
    out.append("    public static int mapEffectUp776(int id776) {")
    out.append("        if (id776 < 0 || id776 > MAX_EFFECT_776) {")
    out.append("            logUnknownUp776Once(\"エフェクト\", id776);")
    out.append("            return SPEED_EFFECT_775;")
    out.append("        }")
    out.append("        return id776;")
    out.append("    }")
    out.append("")
    out.append("    /**")
    out.append("     * 776のブロックエンティティ種別ID → 775の種別ID。")
    out.append("     * potent_sulfur（776 では 48）は 775 に無いため {@link #DROP}。")
    out.append("     * 25 以降は +1 ずれる（bed は 775 の 25）。")
    out.append("     * 範囲外の種別は NBT の中身が種別専用のため安全に代替できず")
    out.append("     * {@link #DROP}。")
    out.append("     */")
    out.append("    public static int mapBlockEntityTypeUp776(int id776) {")
    out.append("        if (id776 < 0 || id776 > BLOCK_ENTITY_POTENT_SULFUR_776) {")
    out.append("            logUnknownUp776Once(\"ブロックエンティティ種別\", id776);")
    out.append("            return DROP;")
    out.append("        }")
    out.append("        if (id776 == BLOCK_ENTITY_POTENT_SULFUR_776) {")
    out.append("            return DROP;")
    out.append("        }")
    out.append("        return id776 >= BLOCK_ENTITY_BED_775 ? id776 + 1 : id776;")
    out.append("    }")
    out.append("")
    out.append("    /**")
    out.append("     * 音の名前を775向けに変換する。776の音の名前のうち 775に無いものは")
    out.append("     * 代替の音の名前にする（数値の反転表と一致させる）。776の新音")
    out.append("     * （金のタンポポ・赤ちゃん動物の声・トランペットの音符ブロック関連など64件）は石の破壊音へ。")
    out.append("     * {@code null} は石の破壊音にする。")
    out.append("     */")
    out.append("    public static String mapSoundNameUp776(String name) {")
    out.append("        return mapSoundNameComposed(name, SOUND_NAME_FALLBACK_776_TO_775_MAP);")
    out.append("    }")
    return "\n".join(out)


def composed_accessors():
    out = ["    // ------------------------------------------------------------------",
           "    // 離れた組み合わせの合成表（763↔776、764↔776、765↔776、766↔776、",
           "    // 767↔776、768↔776、769↔776、770↔776、771↔776、772↔776、773↔776）。",
           "    // 上の *_763_TO_776／*_776_TO_763／*_764_TO_776／*_776_TO_764／",
           "    // *_765_TO_776／*_776_TO_765／*_766_TO_776／*_776_TO_766／",
           "    // *_767_TO_776／*_776_TO_767／*_768_TO_776／*_776_TO_768／",
           "    // *_769_TO_776／*_776_TO_769／*_770_TO_776／*_776_TO_770／",
           "    // *_771_TO_776／*_776_TO_771／*_772_TO_776／*_776_TO_772／",
           "    // *_773_TO_776／*_776_TO_773 表（合成済みの1つの表）",
           "    // を直接引く。二段変換はしない。エフェクトとブロックエンティティ",
           "    // 種別と音の名前は 775 のメソッドへ委譲する（775→776 は隣接）。",
           "    // ------------------------------------------------------------------",
           ""]
    pairs = [
        ("763", 27, "763→776", "776→763"),
        ("764", 28, "764→776", "776→764"),
        ("765", 29, "765→776", "776→765"),
        ("766", 30, "766→776", "776→766"),
        ("767", 31, "767→776", "776→767"),
        ("768", 32, "768→776", "776→768"),
        ("769", 33, "769→776", "776→769"),
        ("770", 34, "770→776", "776→770"),
        ("771", 35, "771→776", "776→771"),
        ("772", 36, "772→776", "776→772"),
        ("773", 37, "773→776", "776→773"),
        ("774", 38, "774→776", "776→774"),
    ]
    for src, fb_src_idx, up_label, down_label in pairs:
        for kind in KINDS:
            name, prefix, jp = kind[0], kind[1], kind[2]
            fb_src = kind[fb_src_idx]
            fb_776 = kind[40]
            # src → 776
            out.append(f"    /** {src}の{jp}ID → 776の{jp}ID。範囲外は{fb_776} に相当する代替。 */")
            out.append(f"    public static int map{name}{src}To776(int id{src}) {{")
            out.append(f"        if (id{src} < 0 || id{src} >= {prefix}_{src}_TO_776_MAP.length) {{")
            out.append(f"            logUnknownComposedOnce(\"{up_label}\", \"{jp}\", id{src});")
            out.append(f"            return {fb_776};")
            out.append("        }")
            out.append(f"        return {prefix}_{src}_TO_776_MAP[id{src}];")
            out.append("    }")
            out.append("")
            # 776 → src
            out.append(f"    /** 776の{jp}ID → {src}の{jp}ID。範囲外は{fb_src} に相当する代替。 */")
            out.append(f"    public static int map{name}776To{src}(int id776) {{")
            out.append(f"        if (id776 < 0 || id776 >= {prefix}_776_TO_{src}_MAP.length) {{")
            out.append(f"            logUnknownComposedOnce(\"{down_label}\", \"{jp}\", id776);")
            out.append(f"            return {fb_src};")
            out.append("        }")
            out.append(f"        return {prefix}_776_TO_{src}_MAP[id776];")
            out.append("    }")
            out.append("")
        # エフェクト・ブロックエンティティ種別・音の名前は 775 のメソッドへ委譲。
        # 774 だけは隣接・上向きのメソッド名（mapEffectUp775 等）を使う。
        if src == "774":
            eff_down = "mapEffectUp775"
            be_down = "mapBlockEntityTypeUp775"
            snd_down = "mapSoundNameUp775"
        else:
            eff_down = f"mapEffect775To{src}"
            be_down = f"mapBlockEntityType775To{src}"
            snd_down = f"mapSoundName775To{src}"
        out.append("    /**")
        out.append(f"     * {src}のエフェクトID → 776のエフェクトID（775経由）。")
        out.append("     */")
        out.append(f"    public static int mapEffect{src}To776(int id{src}) {{")
        out.append(f"        return mapEffect775To776(mapEffect{src}To775(id{src}));")
        out.append("    }")
        out.append("")
        out.append("    /**")
        out.append(f"     * 776のエフェクトID → {src}のエフェクトID（775経由）。")
        out.append("     */")
        out.append(f"    public static int mapEffect776To{src}(int id776) {{")
        out.append(f"        return {eff_down}(mapEffectUp776(id776));")
        out.append("    }")
        out.append("")
        out.append("    /**")
        out.append(f"     * {src}のブロックエンティティ種別ID → 776の種別ID（775経由）。")
        out.append("     * 範囲外は {@link #DROP}。")
        out.append("     */")
        out.append(f"    public static int mapBlockEntityType{src}To776(int id{src}) {{")
        out.append(f"        return mapBlockEntityType775To776(mapBlockEntityType{src}To775(id{src}));")
        out.append("    }")
        out.append("")
        out.append("    /**")
        out.append(f"     * 776のブロックエンティティ種別ID → {src}の種別ID（775経由）。")
        out.append("     * 範囲外は {@link #DROP}。")
        out.append("     */")
        out.append(f"    public static int mapBlockEntityType776To{src}(int id776) {{")
        out.append(f"        return {be_down}(mapBlockEntityTypeUp776(id776));")
        out.append("    }")
        out.append("")
        out.append("    /**")
        out.append(f"     * 音の名前を776向けに変換する（775経由。775→776 の改名は無い）。")
        out.append("     * {@code null} は石の破壊音にする。")
        out.append("     */")
        out.append(f"    public static String mapSoundName{src}To776(String name) {{")
        out.append(f"        return mapSoundNameComposed(mapSoundName{src}To775(name), SOUND_NAME_FALLBACK_776_MAP);")
        out.append("    }")
        out.append("")
        out.append("    /**")
        out.append(f"     * 音の名前を{src}向けに変換する（775経由。数値の合成表と一致させる）。")
        out.append("     * {@code null} は石の破壊音にする。")
        out.append("     */")
        out.append(f"    public static String mapSoundName776To{src}(String name) {{")
        out.append(f"        return {snd_down}(mapSoundNameUp776(name));")
        out.append("    }")
    return "\n".join(out)


def small_registry_accessors():
    out = []
    specs = [
        ("BannerPattern", "バナーパターン", "MAX_BANNER_PATTERN_775", "MAX_BANNER_PATTERN_776"),
        ("PaintingVariant", "絵画バリアント", "MAX_PAINTING_VARIANT_775", "MAX_PAINTING_VARIANT_776"),
        ("TrimPattern", "装飾パターン", "MAX_TRIM_PATTERN_775", "MAX_TRIM_PATTERN_776"),
    ]
    for name, jp, max775, max776 in specs:
        out.append(f"    /** 775の{jp}ID → 776の{jp}ID。775と776で同一のため恒等写像。範囲外は {{@link #DROP}}。 */")
        out.append(f"    public static int map{name}775To776(int id775) {{")
        out.append(f"        if (id775 < 0 || id775 > {max775}) {{")
        out.append(f"            logUnknownOnce(PROTOCOL_776, \"{jp}\", id775);")
        out.append("            return DROP;")
        out.append("        }")
        out.append("        return id775;")
        out.append("    }")
        out.append("")
        out.append(f"    /** 776の{jp}ID → 775の{jp}ID。775と776で同一のため恒等写像。範囲外は {{@link #DROP}}。 */")
        out.append(f"    public static int map{name}776To775(int id776) {{")
        out.append(f"        if (id776 < 0 || id776 > {max776}) {{")
        out.append(f"            logUnknownOnce(PROTOCOL_776, \"{jp}\", id776);")
        out.append("            return DROP;")
        out.append("        }")
        out.append("        return id776;")
        out.append("    }")
        out.append("")
        for src in ("763", "764", "765", "766", "767", "768", "769", "770", "771", "772", "773", "774"):
            # 絵画バリアントだけ 771 を経由する（763／764／765／766／767／768／769／770→776 と
            # 776→763／764／765／766／767／768／769／770 は無い。775→776 は隣接で既にある）。
            # 772 経由は使えない（{src}→772 の絵画メソッドが 772 の代で作られていないため）。
            # 771 だけは 772 経由（771→772 が存在する）。772 は 773 経由。773 は 775 経由。
            # バナー・装飾パターンは 773 を経由する（{src}→773 は既にある）。773 は 775 経由。
            if name == "PaintingVariant":
                hub = "772" if src == "771" else ("773" if src == "772" else ("774" if src == "773" else ("775" if src == "774" else "771")))
            else:
                hub = "775" if src == "774" else ("774" if src == "773" else "773")
            out.append(f"    /** {src}の{jp}ID → 776の{jp}ID（{hub}経由）。 */")
            out.append(f"    public static int map{name}{src}To776(int id{src}) {{")
            out.append(f"        return map{name}{hub}To776(map{name}{src}To{hub}(id{src}));")
            out.append("    }")
            out.append("")
            out.append(f"    /** 776の{jp}ID → {src}の{jp}ID（{hub}経由）。 */")
            out.append(f"    public static int map{name}776To{src}(int id776) {{")
            out.append(f"        return map{name}{hub}To{src}(map{name}776To{hub}(id776));")
            out.append("    }")
            out.append("")
    return "\n".join(out)


def jukebox_accessors():
    """ジュークボックスの曲の 776 アクセサ（隣接のみ。775 と同じ作り）。

    775 と同じく合成表は作らない（763〜773→775 の合成メソッドが無いため）。
    曲は 775 が 21 件、776 が 22 件で、新曲 bounce が 4 に挿入される
    （26.2 の datapack で確認。実バニラの Registry Data での確認は
    統合テストで行う）。名前の表は JUKEBOX_SONG_776_NAMES を新しく作る。
    """
    out = []
    out.append("    /** 775のジュークボックスの曲ID → 776の曲ID。bounce が 4 に挿入されるため 4 以降は +1。範囲外は {@link #DROP}。 */")
    out.append("    public static int mapJukeboxSong775To776(int id775) {")
    out.append("        if (id775 < 0 || id775 >= JUKEBOX_SONG_773_NAMES.length) {")
    out.append("            logUnknownOnce(PROTOCOL_776, \"ジュークボックスの曲\", id775);")
    out.append("            return DROP;")
    out.append("        }")
    out.append("        return id775 < 4 ? id775 : id775 + 1;")
    out.append("    }")
    out.append("")
    out.append("    /** 776のジュークボックスの曲ID → 775の曲ID。4 の bounce は 775 に無いため {@link #DROP}。5 以降は -1。範囲外は {@link #DROP}。 */")
    out.append("    public static int mapJukeboxSong776To775(int id776) {")
    out.append("        if (id776 < 0 || id776 >= JUKEBOX_SONG_776_NAMES.length) {")
    out.append("            logUnknownOnce(PROTOCOL_776, \"ジュークボックスの曲\", id776);")
    out.append("            return DROP;")
    out.append("        }")
    out.append("        if (id776 == 4) {")
    out.append("            return DROP;")
    out.append("        }")
    out.append("        return id776 < 4 ? id776 : id776 - 1;")
    out.append("    }")
    out.append("")
    out.append("    /** 776のジュークボックスの曲ID → 曲の名前（jukebox_playable の名前指定用）。 */")
    out.append("    public static String jukeboxSongName776(int id776) {")
    out.append("        if (id776 < 0 || id776 >= JUKEBOX_SONG_776_NAMES.length) {")
    out.append("            return null;")
    out.append("        }")
    out.append("        return JUKEBOX_SONG_776_NAMES[id776];")
    out.append("    }")
    return "\n".join(out)



DAMAGE_TYPE_776 = """
    /** 776のダメージ種別最大ID（51件。775 の 50 件に sulfur_cube_hot
        が 42 に追加。26.2 の datapack で確認。実サーバーの Registry Data
        での確認は統合テストで行う）。 */
    public static final int MAX_DAMAGE_TYPE_776 = 50;

    /** ダメージ種別ID：775 → 776（42 以降が +1 ずれる。sulfur_cube_hot
        は 775 に無い）。 */
    private static final int[] DAMAGE_TYPE_776_MAP = {
        0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19,
        20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32, 33, 34, 35, 36,
        37, 38, 39, 40, 41, 43, 44, 45, 46, 47, 48, 49, 50
    };

    /** ダメージ種別ID：776 → 775（42 の sulfur_cube_hot は 775 に無いため
        {@link #DROP}。43 以降は -1 ずれる）。 */
    private static final int[] DAMAGE_TYPE_REVERSE_776_MAP = {
        0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19,
        20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32, 33, 34, 35, 36,
        37, 38, 39, 40, 41, -1, 42, 43, 44, 45, 46, 47, 48, 49
    };
"""

JUKEBOX_SONG_776_NAMES = """
    /** ジュークボックスの歌の名前（776 は 22 件。775 の 21 曲に bounce
        が 4 に追加。26.2 の datapack で確認。実バニラの Registry Data
        での確認は統合テストで行う）。 */
    public static final String[] JUKEBOX_SONG_776_NAMES = {
            "11", "13", "5", "blocks", "bounce", "cat", "chirp", "creator", "creator_music_box",
            "far", "lava_chicken", "mall", "mellohi", "otherside", "pigstep", "precipice",
            "relic", "stal", "strad", "tears", "wait", "ward",
    };
"""


SMALL_REGISTRY_CONSTANTS = """
    /** 776のバナーパターン最大ID（43件。775と同一と仮定。実バニラの
        Registry Data での確認は統合テストで行う）。 */
    public static final int MAX_BANNER_PATTERN_776 = 42;

    /** 776の絵画バリアント最大ID（51件。775と同一。実バニラの
        Registry Data での確認は統合テストで行う）。 */
    public static final int MAX_PAINTING_VARIANT_776 = 50;

    /** 776の装飾パターン最大ID（18件。775と同一と仮定。実バニラの
        Registry Data での確認は統合テストで行う）。 */
    public static final int MAX_TRIM_PATTERN_776 = 17;
"""


JAVADOC_776 = """ *
 * <p>776（26.2）向けの表も同じ作り方で持つ（{@code *_776}）。
 * 776は775に近く、差分は次の通り（ViaVersion mapping-26.2.json と
 * mapping-26.1.json、26.2 の jar の bootstrap ダンプの突き合わせ）。
 * ブロック状態は新2,493件（29,873→32,366。硫黄・辰砂系）。
 * ブロックは新28件（硫黄・辰砂系）。アイテムは新31件（硫黄・辰砂系・
 * music_disc_bounce・sulfur_cube_bucket・sulfur_cube_spawn_egg）。
 * エンティティは新1件（sulfur_cube）。音は新66件・削除0件。
 * パーティクルは新8件（geyser 系・noxious_gas 系・sulfur_bubbles・
 * sulfur_cube_goo）。データコンポーネントは 110→111（新1件
 * sulfur_cube_content）。エフェクト・エンチャント・メニューは同一。
 * 属性は新5件（air_drag_modifier・below_name_distance・bounciness・
 * friction_modifier・name_tag_distance）。ブロックエンティティ種別は
 * 49件だが bed が削除され potent_sulfur が追加された（並びがずれる）。
 * 絵画バリアント・バナーパターン・装飾パターンは 775 と同一。
 * ジュークボックスの歌は新1件（bounce）。ダメージ種別は新1件
 * （sulfur_cube_hot）。バイオームは新1件（sulfur_caves）。
 * （実バニラの Registry Data での確認は統合テストで行う）。
 * 775→776 の隣接表と 763／764／765／766／767／768／769／770／771／772／773／774→776
 * の合成表を持ち、逆向き 776→775 は反転して作る。
 * パケットIDは 775 と同一（spectate_entity → spectator_action の
 * 改名のみ。docs/PROXY-26.2.md 参照）。
"""

def damage_accessors():
    out = []
    out.append("    /** 775のダメージ種別ID → 776のダメージ種別ID。42 以降は +1 ずれる。範囲外は {@link #DROP}。 */")
    out.append("    public static int mapDamageType775To776(int id775) {")
    out.append("        if (id775 < 0 || id775 > MAX_DAMAGE_TYPE_775) {")
    out.append("            logUnknownOnce(PROTOCOL_776, \"ダメージ種別\", id775);")
    out.append("            return DROP;")
    out.append("        }")
    out.append("        return DAMAGE_TYPE_776_MAP[id775];")
    out.append("    }")
    out.append("")
    out.append("    /** 776のダメージ種別ID → 775のダメージ種別ID。42 の sulfur_cube_hot は 775 に無いため {@link #DROP}。範囲外は {@link #DROP}。 */")
    out.append("    public static int mapDamageType776To775(int id776) {")
    out.append("        if (id776 < 0 || id776 > MAX_DAMAGE_TYPE_776) {")
    out.append("            logUnknownOnce(PROTOCOL_776, \"ダメージ種別\", id776);")
    out.append("            return DROP;")
    out.append("        }")
    out.append("        return DAMAGE_TYPE_REVERSE_776_MAP[id776];")
    out.append("    }")
    out.append("")
    for src in ("763", "764", "765", "766", "767", "768", "769", "770", "771", "772", "773", "774"):
        # ダメージ種別は 775 のメソッドを経由する。
        out.append(f"    /** {src}のダメージ種別ID → 776のダメージ種別ID（775経由）。 */")
        out.append(f"    public static int mapDamageType{src}To776(int id{src}) {{")
        out.append(f"        return mapDamageType775To776(mapDamageType{src}To775(id{src}));")
        out.append("    }")
        out.append("")
        out.append(f"    /** 776のダメージ種別ID → {src}のダメージ種別ID（775経由）。 */")
        out.append(f"    public static int mapDamageType776To{src}(int id776) {{")
        out.append(f"        return mapDamageType775To{src}(mapDamageType776To775(id776));")
        out.append("    }")
        out.append("")
    return "\n".join(out)


def insert_after(java, anchor, block, label):
    if anchor not in java:
        print(f"アンカーが見つからない（{label}）", file=sys.stderr)
        return None
    return java.replace(anchor, anchor + block, 1)


def main():
    if (not TABLES776_FILE.exists() or not TABLES776_COMPOSED_FILE.exists()
            or not all(p.exists() for p in TABLES776_COMPOSED_DOWN_FILES.values())):
        print("RegistryTables776.java / RegistryTables776ComposedUpA.java / "
              "RegistryTables776Down*.java が無い。"
              "先に insert-776-tables.py を実行すること", file=sys.stderr)
        return 1
    java = JAVA_FILE.read_text(encoding="utf-8")
    checks = [
        ("PROTOCOL_776", "776 の定数・表・アクセサは既にある"),
        ("BLOCK_STATE_776_MAP", "776 の展開済み表は既にある"),
        ("mapBlockState775To776", "776 のアクセサは既にある"),
    ]
    for marker, msg in checks:
        if marker in java:
            print(msg, file=sys.stderr)
            return 1

    # import static。775 の Down772 の直後（775 世代の最後）。
    # Expanded の import は move-776-expanded-tables.py が後で足す
    # （775 と同じ段取り）。Down*BlockState／SoundNames の import は
    # どのスクリプトも足さない（775 も同じで、Stage A のコミット時に
    # 手で足した）ため、ここには含めず後で手で追加すること
    # （RegistryRemapper.java に Down774・Down763〜774BlockState・
    #  SoundNames の import を手で追加済み）。
    java = insert_after(java,
                        "import static io.detergentmc.protocol.RegistryTables775Down772.*;\n",
                        "import static io.detergentmc.protocol.RegistryTables776.*;\n"
                        "import static io.detergentmc.protocol.RegistryTables776ComposedUpA.*;\n"
                        "import static io.detergentmc.protocol.RegistryTables776ComposedUpB.*;\n"
                        "import static io.detergentmc.protocol.RegistryTables776Down763.*;\n"
                        "import static io.detergentmc.protocol.RegistryTables776Down764.*;\n"
                        "import static io.detergentmc.protocol.RegistryTables776Down765.*;\n"
                        "import static io.detergentmc.protocol.RegistryTables776Down766.*;\n"
                        "import static io.detergentmc.protocol.RegistryTables776Down767.*;\n"
                        "import static io.detergentmc.protocol.RegistryTables776Down768.*;\n"
                        "import static io.detergentmc.protocol.RegistryTables776Down769.*;\n"
                        "import static io.detergentmc.protocol.RegistryTables776Down770.*;\n"
                        "import static io.detergentmc.protocol.RegistryTables776Down771.*;\n"
                        "import static io.detergentmc.protocol.RegistryTables776Down772.*;\n"
                        "import static io.detergentmc.protocol.RegistryTables776Down773.*;\n"
                        "import static io.detergentmc.protocol.RegistryTables776Down774.*;\n",
                        "import static RegistryTables776")
    if java is None:
        return 1

    # クラスの javadoc に 776 の段落を追加（775 の段落の直後）。
    anchor = (" * 774→775 の隣接表と 763／764／765／766／767／768／769／770／771／772／773→775\n"
              " * の合成表を持ち、逆向き 775→774 は反転して作る。\n")
    java = insert_after(java, anchor, JAVADOC_776, "javadoc")
    if java is None:
        return 1

    # 定数。
    java = insert_after(java,
                        "    public static final int PROTOCOL_775 = 775;\n",
                        "    public static final int PROTOCOL_776 = 776;\n", "PROTOCOL_776")
    if java is None:
        return 1
    java = insert_after(java,
                        "    public static final int MAX_EFFECT_775 = 39;\n",
                        CONSTANTS_MAX, "MAX_776")
    if java is None:
        return 1
    java = insert_after(java,
                        "    public static final int SPEED_EFFECT_775 = 0;\n",
                        CONSTANTS_FALLBACK, "776 の代替先")
    if java is None:
        return 1
    java = insert_after(java,
                        "    public static final int BLOCK_ENTITY_COPPER_GOLEM_STATUE_775 = 48;\n",
                        CONSTANTS_BLOCK_ENTITY, "776 のブロックエンティティ種別")
    if java is None:
        return 1

    # 隣接表（775→776）。MENU_771_MAP の直後。
    anchor = ("    private static final int[] MENU_771_MAP =\n"
              "            expand(MENU_771_RANGES, MENU_771_OVERRIDES, MAX_MENU_770 + 1,\n"
              "                    CRAFTING_MENU_771);\n")
    java = insert_after(java, anchor, adjacent_maps() + "\n", "隣接表 776")
    if java is None:
        return 1

    # 合成表。MENU_771_TO_769_MAP の直後。
    anchor = ("    private static final int[] MENU_771_TO_769_MAP =\n"
              "            expand(MENU_771_TO_769_RANGES, MENU_771_TO_769_OVERRIDES,\n"
              "                    25, CRAFTING_MENU_769);\n")
    java = insert_after(java, anchor, "\n" + composed_maps() + "\n", "合成表 776")
    if java is None:
        return 1

    # 反転表（776→775）。MENU_UP_771_MAP の直後。
    anchor = ("    private static final int[] MENU_UP_771_MAP = invert(\n"
              "            MENU_771_RANGES, MENU_771_OVERRIDES,\n"
              "            MAX_MENU_770 + 1, MAX_MENU_771 + 1,\n"
              "            CRAFTING_MENU_770, null);\n")
    java = insert_after(java, anchor, up_maps() + "\n", "反転表 776")
    if java is None:
        return 1

    # 音の名前の表。
    anchor = ("    private static final Map<String, String> SOUND_NAME_FALLBACK_766_TO_768_MAP =\n"
              "            buildSoundNameMap(SOUND_NAME_FALLBACKS_766_TO_768);\n")
    java = insert_after(java, anchor, SOUND_NAME_MAPS, "音の名前の表 776")
    if java is None:
        return 1

    # ログ。
    anchor = ("    private static void logUnknownUp775Once(String kind, int id775) {\n"
              "        if (LOGGED_UNKNOWN.add(\"up775:\" + kind + \":\" + id775)) {\n"
              "            LOG.warning(\"775に無い\" + kind + \"の番号が来たため代替に落とします（775 id=\" + id775 + \"）。\");\n"
              "        }\n"
              "    }\n")
    java = insert_after(java, anchor, LOG_UP_776, "logUnknownUp776Once")
    if java is None:
        return 1

    # アクセサ（a）775→776。mapEnchantment775To764 の直後（775 の隣接
    # アクセサの最後。mapSoundName767To775 は合成アクセサの途中にあるため
    # アンカーに使えない）。
    anchor = ("    public static int mapEnchantment775To764(int id775) {\n"
              "        return mapEnchantment775To765(id775);\n"
              "    }\n")
    java = insert_after(java, anchor, "\n" + adjacent_accessors() + "\n", "アクセサ 775→776")
    if java is None:
        return 1

    # アクセサ（b）776→775。mapSoundNameUp775 の直後。
    anchor = ("    public static String mapSoundNameUp775(String name) {\n"
              "        return mapSoundNameComposed(name, SOUND_NAME_FALLBACK_775_TO_774_MAP);\n"
              "    }\n")
    java = insert_after(java, anchor, "\n" + up_accessors() + "\n", "アクセサ 776→775")
    if java is None:
        return 1

    # アクセサ（c）763／764／765／766／767／768／769／770／771／772／773／774↔776。mapSoundName775To772 の直後。
    anchor = ("    public static String mapSoundName775To772(String name) {\n"
              "        return mapSoundName774To772(mapSoundNameUp775(name));\n"
              "    }\n")
    java = insert_after(java, anchor, "\n" + composed_accessors() + "\n", "アクセサ 763/764/765/766/767/768/769/770/771/772/773↔776")
    if java is None:
        return 1

    # バナー・絵画・装飾パターン・ダメージ種別の定数。
    java = insert_after(java,
                        "    /** 775のバナーパターン最大ID（43件。774と同一と仮定。実バニラの\n"
                        "        Registry Data での確認は統合テストで行う）。 */\n"
                        "    public static final int MAX_BANNER_PATTERN_775 = 42;\n",
                        SMALL_REGISTRY_CONSTANTS, "バナー等の776定数")
    if java is None:
        return 1
    anchor = ("    private static final int[] DAMAGE_TYPE_REVERSE_775_MAP = {\n"
              "        0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19,\n"
              "        20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32, 33, 34, 35, 36,\n"
              "        37, 38, 39, 40, 41, 42, 43, 44, 45, 46, 47, 48, 49\n"
              "    };\n")
    java = insert_after(java, anchor, DAMAGE_TYPE_776, "ダメージ種別776の表")
    if java is None:
        return 1
    # ジュークボックスの歌の名前の表（DAMAGE_TYPE_776 の直後）。
    anchor = ("        37, 38, 39, 40, 41, -1, 42, 43, 44, 45, 46, 47, 48, 49\n"
              "    };\n")
    java = insert_after(java, anchor, JUKEBOX_SONG_776_NAMES, "ジュークボックス776の名前の表")
    if java is None:
        return 1

    # バナー・絵画・装飾パターン・ダメージ種別のアクセサ。
    anchor = ("    public static int mapBannerPattern775To771(int id775) {\n"
              "        return mapBannerPattern773To771(mapBannerPattern775To773(id775));\n"
              "    }\n")
    banner_painting_trim = small_registry_accessors()
    java = insert_after(java, anchor, "\n" + banner_painting_trim + "\n", "バナー等の776アクセサ")
    if java is None:
        return 1
    anchor = ("    /** 775のジュークボックスの曲ID → 曲の名前（jukebox_playable の名前指定用）。774と同一のため 773 の表を使う。 */\n"
              "    public static String jukeboxSongName775(int id775) {\n"
              "        if (id775 < 0 || id775 >= JUKEBOX_SONG_773_NAMES.length) {\n"
              "            return null;\n"
              "        }\n"
              "        return JUKEBOX_SONG_773_NAMES[id775];\n"
              "    }\n")
    java = insert_after(java, anchor, "\n" + jukebox_accessors() + "\n", "ジュークボックス776のアクセサ")
    if java is None:
        return 1
    java = insert_after(java, anchor, "\n" + damage_accessors() + "\n", "ダメージ種別776のアクセサ")
    if java is None:
        return 1

    # プロトコル番号で切り替える変換（担当2の配線用）に 776 を追加。
    dispatches = [
        ("mapBlockState", "mapBlockState765To775(id765)", "mapBlockState765To776(id765)"),
        ("mapItem", "mapItem765To775(id765)", "mapItem765To776(id765)"),
        ("mapItemReverse", "mapItem775To765(id)", "mapItem776To765(id)"),
        ("mapEntityType", "mapEntityType765To775(id765)", "mapEntityType765To776(id765)"),
        ("mapSound", "mapSound765To775(id765)", "mapSound765To776(id765)"),
        ("mapParticle", "mapParticle765To775(id765)", "mapParticle765To776(id765)"),
        ("mapBlock", "mapBlock765To775(id765)", "mapBlock765To776(id765)"),
        ("mapMenu", "mapMenu765To775(id765)", "mapMenu765To776(id765)"),
        ("mapEffect", "mapEffect765To775(id765)", "mapEffect765To776(id765)"),
        ("mapBlockEntityType", "mapBlockEntityType765To775(id765)", "mapBlockEntityType765To776(id765)"),
        ("mapSoundName", "mapSoundName765To775(name)", "mapSoundName765To776(name)"),
    ]
    for label, old_call, new_call in dispatches:
        if label == "mapItemReverse":
            old = ("        if (clientProtocol == PROTOCOL_775) {\n"
                   "            // 775クライアントのアイテムID → 765サーバーのアイテムID。\n"
                   "            // mapItemReverse775 は 775→774 のため、ここでは合成表を使う。\n"
                   "            return mapItem775To765(id);\n"
                   "        }\n")
            new = (old
                   + "        if (clientProtocol == PROTOCOL_776) {\n"
                   "            // 776クライアントのアイテムID → 765サーバーのアイテムID。\n"
                   "            // mapItemReverse776 は 776→775 のため、ここでは合成表を使う。\n"
                   "            return mapItem776To765(id);\n"
                   "        }\n")
            if old not in java:
                print("ディスパッチのアンカーが見つからない（mapItemReverse）", file=sys.stderr)
                return 1
            java = java.replace(old, new, 1)
            continue
        old = ("        if (clientProtocol == PROTOCOL_775) {\n"
               f"            return {old_call};\n"
               "        }\n")
        if old not in java:
            print(f"ディスパッチのアンカーが見つからない（{label}）", file=sys.stderr)
            return 1
        java = java.replace(old, old
                            + "        if (clientProtocol == PROTOCOL_776) {\n"
                            + f"            return {new_call};\n"
                            + "        }\n", 1)
    java = java.replace(
        "    // 765は素通し、766は766の表、767は767の表、768は768の表、\n"
        "    // 759は760の表を経由する（759↔760は恒等写像）。\n"
        "    // 769は769の表、770は770の表、771は771の表、772は772の表、773は773の表、775は775の表、それより古い未知の番号は763の表を使う。\n"
        "    // 767／768／769／770／771／772／773／775は765以上だが素通しではない（>765 の分岐より\n"
        "    // 先に 767／768／769／770／771／772／773／775 を判定すること）。",
        "    // 765は素通し、766は766の表、767は767の表、768は768の表、\n"
        "    // 759は760の表を経由する（759↔760は恒等写像）。\n"
        "    // 769は769の表、770は770の表、771は771の表、772は772の表、773は773の表、775は775の表、776は776の表、それより古い未知の番号は763の表を使う。\n"
        "    // 767／768／769／770／771／772／773／775／776は765以上だが素通しではない（>765 の分岐より\n"
        "    // 先に 767／768／769／770／771／772／773／775／776 を判定すること）。",
        1)

    JAVA_FILE.write_text(java, encoding="utf-8")
    print("RegistryRemapper.java に 776 の定数・表・アクセサを挿入した")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
