#!/usr/bin/env python3
"""tools/insert-773-java.py で入れた *_773 表の上に、RegistryRemapper.java
へ 774 対応の定数・展開済み表（*_774_MAP）・反転表（*_UP_774_MAP）・
アクセサを挿入する（1回だけの作業用）。

  - 定数：PROTOCOL_774、MAX_*_774、774 側の代替先。
  - 隣接表（773→774）: *_774_MAP と ITEM_REVERSE_774_MAP と
    ATTRIBUTE_774_MAP／ATTRIBUTE_REVERSE_774_MAP／ENCHANTMENT_774_MAP／
    ENCHANTMENT_REVERSE_774_MAP。
  - 合成表（763↔774、764↔774、765↔774、766↔774、767↔774、768↔774、769↔774、770↔774、772↔774）: *_763_TO_774_MAP など126件。
  - 773↔774 は隣接表（*_774_MAP）と反転表（*_UP_774_MAP）で扱うため合成表は作らない（773と同じ方針）。
  - 逆向き（774→773）: *_UP_774_MAP（invert()。アイテムは
    ITEM_REVERSE_774_MAP をそのまま使う。773→774 に補正は無い）。
  - 音の名前の表（String）: SOUND_NAME_FALLBACK_*_774_MAP など18件。
  - アクセサ：773↔774、774→773（上向き）、763／764／765／766／767／768／769／770／771↔774。
  - エフェクト：774 は 773 と同一の 39 件（0〜38）のため恒等写像。
    属性は 771・772・773・774 とも 35 件で同一、エンチャントは 42 件同士で
    並びは同一（minecraft-data の dataPaths.json で 1.21.11 の
    enchantments は pc/1.21.11 と確認。1.21.1 の並びと同一）。
    ブロックエンティティ種別は 774 は 49 件（shelf=40 が挿入され、
    copper_golem_statue=48 を追加。trial_spawner=44、vault=45、
    test_block=46、test_instance_block=47）。
    バナー・絵画・装飾パターンは 773 と同一のため恒等写像。
    ダメージ種別も 773 と同一の 49 件のため恒等写像。
    バイオームは 769・770・771・772・773・774 とも 65 件で同一の並び（実バニラの Registry Data
    での確認は統合テストで行う）。
  - 注意：このスクリプトで挿入した展開済み表（隣接12・合成140・上向き6の
    計144件）は、そのままでは RegistryRemapper.java の <clinit> が 64KB を
    超えてコンパイルできない（773 までがギリギリ収まっていた）。そのため
    挿入後に tools/move-774-expanded-tables.py で RegistryTables774Expanded.java
    へ移動してある。再実行時はこの移動もやり直すこと。
"""

import re
import sys
from pathlib import Path

SCRIPT_DIR = Path(__file__).resolve().parent
REPO_ROOT = SCRIPT_DIR.parent.parent
JAVA_FILE = (REPO_ROOT / "detergentmc-protocol" / "src" / "main" / "java"
             / "io" / "detergentmc" / "protocol" / "RegistryRemapper.java")
TABLES774_FILE = (REPO_ROOT / "detergentmc-protocol" / "src" / "main" / "java"
                  / "io" / "detergentmc" / "protocol" / "RegistryTables774.java")
TABLES774_COMPOSED_FILE = (REPO_ROOT / "detergentmc-protocol" / "src" / "main" / "java"
                  / "io" / "detergentmc" / "protocol" / "RegistryTables774ComposedUp.java")
TABLES774_COMPOSED_DOWN_FILES = {
    suffix: (REPO_ROOT / "detergentmc-protocol" / "src" / "main" / "java"
             / "io" / "detergentmc" / "protocol"
             / f"RegistryTables774Down{suffix}.java")
    for suffix in ("763", "764", "765", "766", "767", "768", "769", "770", "771", "772")
}


# kind: (Java メソッド名の語尾, RANGES の接頭辞, 日本語名,
#        763→774 の件数, 774→763 の件数, 764→774 の件数, 774→764 の件数,
#        765→774 の件数, 774→765 の件数, 766→774 の件数, 774→766 の件数,
#        767→774 の件数, 774→767 の件数, 768→774 の件数, 774→768 の件数,
#        769→774 の件数, 774→769 の件数, 770→774 の件数, 774→770 の件数,
#        771→774 の件数, 774→771 の件数, 772→774 の件数, 774→772 の件数,
#        773→774 の件数, 774→773 の件数,
#        763 側の代替先, 764 側の代替先, 765 側の代替先, 766 側の代替先,
#        767 側の代替先, 768 側の代替先, 769 側の代替先, 770 側の代替先,
#        771 側の代替先, 772 側の代替先, 773 側の代替先, 774 側の代替先)
KINDS = [
    ("BlockState", "BLOCK_STATE", "ブロック状態", 24135, 29671, 24276, 29671, 26644, 29671, 26684, 29671, 26684, 29671, 27318, 29671, 27866, 29671, 27914, 29671, 27946, 29671, 27946, 29671, 29671, 29671,
     "STONE_BLOCK_STATE_763", "STONE_BLOCK_STATE_764", "STONE_BLOCK_STATE_765", "STONE_BLOCK_STATE_766", "STONE_BLOCK_STATE_767", "STONE_BLOCK_STATE_768", "STONE_BLOCK_STATE_769", "STONE_BLOCK_STATE_770", "STONE_BLOCK_STATE_771", "STONE_BLOCK_STATE_772", "STONE_BLOCK_STATE_773", "STONE_BLOCK_STATE_774"),
    ("Item", "ITEM", "アイテム", 1255, 1505, 1255, 1505, 1312, 1505, 1330, 1505, 1333, 1505, 1375, 1505, 1385, 1505, 1396, 1505, 1415, 1505, 1416, 1505, 1488, 1505,
     "STONE_ITEM_763", "STONE_ITEM_764", "STONE_ITEM_765", "STONE_ITEM_766", "STONE_ITEM_767", "STONE_ITEM_768", "STONE_ITEM_769", "STONE_ITEM_770", "STONE_ITEM_771", "STONE_ITEM_772", "STONE_ITEM_773", "STONE_ITEM_774"),
    ("EntityType", "ENTITY_TYPE", "エンティティ種別", 124, 157, 124, 157, 126, 157, 130, 157, 130, 157, 150, 157, 149, 157, 150, 157, 151, 157, 151, 157, 153, 157,
     "PIG_ENTITY_TYPE_763", "PIG_ENTITY_TYPE_764", "PIG_ENTITY_TYPE_765", "PIG_ENTITY_TYPE_766", "PIG_ENTITY_TYPE_767", "PIG_ENTITY_TYPE_768", "PIG_ENTITY_TYPE_769", "PIG_ENTITY_TYPE_770", "PIG_ENTITY_TYPE_771", "PIG_ENTITY_TYPE_772", "PIG_ENTITY_TYPE_773", "PIG_ENTITY_TYPE_774"),
    ("Sound", "SOUND", "音", 1474, 1838, 1485, 1838, 1539, 1838, 1607, 1838, 1611, 1838, 1636, 1838, 1651, 1838, 1702, 1838, 1727, 1838, 1728, 1838, 1771, 1838,
     "GENERIC_SOUND_763", "GENERIC_SOUND_764", "GENERIC_SOUND_765", "GENERIC_SOUND_766", "GENERIC_SOUND_767", "GENERIC_SOUND_768", "GENERIC_SOUND_769", "GENERIC_SOUND_770", "GENERIC_SOUND_771", "GENERIC_SOUND_772", "GENERIC_SOUND_773", "GENERIC_SOUND_774"),
    ("Particle", "PARTICLE", "パーティクル", 95, 115, 95, 115, 101, 115, 109, 115, 109, 115, 111, 115, 112, 115, 114, 115, 114, 115, 114, 115, 115, 115,
     "AMBIENT_PARTICLE_763", "AMBIENT_PARTICLE_764", "AMBIENT_PARTICLE_765", "ENTITY_EFFECT_PARTICLE_766", "ENTITY_EFFECT_PARTICLE_767", "ENTITY_EFFECT_PARTICLE_768", "ENTITY_EFFECT_PARTICLE_769", "ENTITY_EFFECT_PARTICLE_770", "ENTITY_EFFECT_PARTICLE_771", "ENTITY_EFFECT_PARTICLE_772", "ENTITY_EFFECT_PARTICLE_773", "ENTITY_EFFECT_PARTICLE_774"),
    ("Block", "BLOCK", "ブロック", 1003, 1166, 1003, 1166, 1058, 1166, 1060, 1166, 1060, 1166, 1084, 1166, 1095, 1166, 1104, 1166, 1105, 1166, 1105, 1166, 1166, 1166,
     "STONE_BLOCK_763", "STONE_BLOCK_764", "STONE_BLOCK_765", "STONE_BLOCK_766", "STONE_BLOCK_767", "STONE_BLOCK_768", "STONE_BLOCK_769", "STONE_BLOCK_770", "STONE_BLOCK_771", "STONE_BLOCK_772", "STONE_BLOCK_773", "STONE_BLOCK_774"),
    ("Menu", "MENU", "メニュー種別", 24, 25, 24, 25, 25, 25, 25, 25, 25, 25, 25, 25, 25, 25, 25, 25, 25, 25, 25, 25, 25, 25,
     "CRAFTING_MENU_763", "CRAFTING_MENU_764", "CRAFTING_MENU_765", "CRAFTING_MENU_766", "CRAFTING_MENU_767", "CRAFTING_MENU_768", "CRAFTING_MENU_769", "CRAFTING_MENU_770", "CRAFTING_MENU_771", "CRAFTING_MENU_772", "CRAFTING_MENU_773", "CRAFTING_MENU_774"),
]

CONSTANTS_MAX = """
    /** 774側の最大の番号（ViaVersion mapping-1.21.11.json の件数−1）。 */
    public static final int MAX_BLOCK_STATE_774 = 29670;
    public static final int MAX_ITEM_774 = 1504;
    public static final int MAX_ENTITY_TYPE_774 = 156;
    public static final int MAX_SOUND_774 = 1837;
    public static final int MAX_PARTICLE_774 = 114;
    public static final int MAX_BLOCK_774 = 1165;
    public static final int MAX_MENU_774 = 24;
    /** 774側のエフェクトは 0〜39（Speed=0。773 の 0〜38 に
        BreathOfTheNautilus が 39 に追加。minecraft-data の
        dataPaths.json で 1.21.11 の effects は pc/1.21.11 と確認）。 */
    public static final int MAX_EFFECT_774 = 39;
    /** 774側の属性は 0〜34（773 と同じ 35 件。
        ViaVersion mapping-1.21.11.json で確認）。 */
    public static final int MAX_ATTRIBUTE_774 = 34;
    /** エンチャントは 774 は 0〜42（43件。lunge が 21 に新設され
        21 以降は +1 ずれる。minecraft-data の dataPaths.json で
        1.21.11 の enchantments は pc/1.21.11 と確認。実機の Registry Data
        での確認は統合テストで行う）。 */
    public static final int MAX_ENCHANTMENT_774 = 42;
"""

CONSTANTS_FALLBACK = """
    /** 774側の代替先（mapping-1.21.11.json の番号。Speed だけ 0ベースで 0）。
     * ブタは 774 では 100（camel_husk が 20 に、nautilus が 88 に、
     * parched が 97 に挿入され、773 の 97 から +3）、
     * 石の破壊音は 774 では 1535（新音 67 件のうち 51 件が 1484 より
     * 前に挿入され、773 の 1484 から +51）。 */
    public static final int STONE_BLOCK_STATE_774 = 1;
    public static final int AIR_BLOCK_STATE_774 = 0;
    public static final int STONE_ITEM_774 = 1;
    public static final int PIG_ENTITY_TYPE_774 = 100;
    public static final int GENERIC_SOUND_774 = 1535; // block.stone.break
    /** 774でも ambient_entity_effect は無いため、パーティクルの代替は entity_effect。 */
    public static final int ENTITY_EFFECT_PARTICLE_774 = 20;
    public static final int STONE_BLOCK_774 = 1;
    public static final int CRAFTING_MENU_774 = 12;
    public static final int SPEED_EFFECT_774 = 0;
"""

CONSTANTS_BLOCK_ENTITY = """
    /** 774のブロックエンティティ種別：49 件（773 と同一。shelf=40、
        trial_spawner=44、vault=45、test_block=46、test_instance_block=47、
        copper_golem_statue=48）。 */
    public static final int BLOCK_ENTITY_SHELF_774 = 40;
    public static final int BLOCK_ENTITY_CREAKING_HEART_774 = 10;
    public static final int BLOCK_ENTITY_TRIAL_SPAWNER_774 = 44;
    public static final int BLOCK_ENTITY_VAULT_774 = 45;
    public static final int BLOCK_ENTITY_TEST_BLOCK_774 = 46;
    public static final int BLOCK_ENTITY_TEST_INSTANCE_BLOCK_774 = 47;
    public static final int BLOCK_ENTITY_COPPER_GOLEM_STATUE_774 = 48;
"""


def adjacent_maps():
    lines = []
    for kind in KINDS:
        name, prefix, jp = kind[0], kind[1], kind[2]
        lines.append(
            f"    private static final int[] {prefix}_774_MAP =\n"
            f"            expand({prefix}_774_RANGES, {prefix}_774_OVERRIDES, MAX_{prefix}_773 + 1,\n"
            f"                    {kind[36]});")
    lines.append(
        "    private static final int[] ITEM_REVERSE_774_MAP =\n"
        "            expand(ITEM_REVERSE_774_RANGES, ITEM_REVERSE_774_OVERRIDES, 1505, -1);")
    lines.append(
        "    private static final int[] ATTRIBUTE_774_MAP =\n"
        "            expand(ATTRIBUTE_774_RANGES, ATTRIBUTE_774_OVERRIDES, MAX_ATTRIBUTE_773 + 1, DROP);")
    lines.append(
        "    private static final int[] ATTRIBUTE_REVERSE_774_MAP =\n"
        "            expand(ATTRIBUTE_REVERSE_774_RANGES, ATTRIBUTE_REVERSE_774_OVERRIDES,\n"
        "                    MAX_ATTRIBUTE_774 + 1, DROP);")
    lines.append(
        "    private static final int[] ENCHANTMENT_774_MAP =\n"
        "            expand(ENCHANTMENT_774_RANGES, ENCHANTMENT_774_OVERRIDES,\n"
        "                    MAX_ENCHANTMENT_773 + 1, DROP);")
    lines.append(
        "    private static final int[] ENCHANTMENT_REVERSE_774_MAP =\n"
        "            expand(ENCHANTMENT_REVERSE_774_RANGES, ENCHANTMENT_REVERSE_774_OVERRIDES,\n"
        "                    MAX_ENCHANTMENT_774 + 1, DROP);")
    return "\n".join(lines)


def composed_maps():
    # (接尾辞, 件数の位置, 代替先の位置)。件数・代替先は KINDS の
    # タプルから取る（763_TO_774: 件数 index 3, 代替先 774=index 36／
    # 774_TO_763: 件数 index 4, 代替先 763=index 25）。件数は 3〜24、
    # 代替先は 25（763）〜36（774）。773↔774 は隣接表で扱うため含めない。
    pairs = [
        ("763_TO_774", 3, 36), ("774_TO_763", 4, 25),
        ("764_TO_774", 5, 36), ("774_TO_764", 6, 26),
        ("765_TO_774", 7, 36), ("774_TO_765", 8, 27),
        ("766_TO_774", 9, 36), ("774_TO_766", 10, 28),
        ("767_TO_774", 11, 36), ("774_TO_767", 12, 29),
        ("768_TO_774", 13, 36), ("774_TO_768", 14, 30),
        ("769_TO_774", 15, 36), ("774_TO_769", 16, 31),
        ("770_TO_774", 17, 36), ("774_TO_770", 18, 32),
        ("771_TO_774", 19, 36), ("774_TO_771", 20, 33),
        ("772_TO_774", 21, 36), ("774_TO_772", 22, 34),
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
            continue  # アイテムは ITEM_REVERSE_774_MAP を使う。
        # 773→774 に補正は無い（creaking_heart のプロパティ変更と
        # potion の分割は 773 で済んでいる）。反転はそのまま使う。
        corrections = "null"
        # 逆向きの代替先は「773 側」の定数（kind[35]）。
        fb773 = kind[35]
        lines.append(
            f"    private static final int[] {prefix}_UP_774_MAP = invert(\n"
            f"            {prefix}_774_RANGES, {prefix}_774_OVERRIDES,\n"
            f"            MAX_{prefix}_773 + 1, MAX_{prefix}_774 + 1,\n"
            f"            {fb773}, {corrections});")
    return "\n".join(lines)


SOUND_NAME_MAPS = """
    private static final Map<String, String> SOUND_NAME_FALLBACK_774_MAP =
            buildSoundNameMap(SOUND_NAME_FALLBACKS_774);
    private static final Map<String, String> SOUND_NAME_FALLBACK_773_TO_774_MAP =
            buildSoundNameMap(SOUND_NAME_FALLBACKS_773_TO_774);
    private static final Map<String, String> SOUND_NAME_FALLBACK_774_TO_773_MAP =
            buildSoundNameMap(SOUND_NAME_FALLBACKS_774_TO_773);
    private static final Map<String, String> SOUND_NAME_FALLBACK_770_TO_774_MAP =
            buildSoundNameMap(SOUND_NAME_FALLBACKS_770_TO_774);
    private static final Map<String, String> SOUND_NAME_FALLBACK_774_TO_770_MAP =
            buildSoundNameMap(SOUND_NAME_FALLBACKS_774_TO_770);
    private static final Map<String, String> SOUND_NAME_FALLBACK_772_TO_774_MAP =
            buildSoundNameMap(SOUND_NAME_FALLBACKS_772_TO_774);
    private static final Map<String, String> SOUND_NAME_FALLBACK_774_TO_772_MAP =
            buildSoundNameMap(SOUND_NAME_FALLBACKS_774_TO_772);
    private static final Map<String, String> SOUND_NAME_FALLBACK_771_TO_774_MAP =
            buildSoundNameMap(SOUND_NAME_FALLBACKS_771_TO_774);
    private static final Map<String, String> SOUND_NAME_FALLBACK_774_TO_771_MAP =
            buildSoundNameMap(SOUND_NAME_FALLBACKS_774_TO_771);
    private static final Map<String, String> SOUND_NAME_FALLBACK_769_TO_774_MAP =
            buildSoundNameMap(SOUND_NAME_FALLBACKS_769_TO_774);
    private static final Map<String, String> SOUND_NAME_FALLBACK_774_TO_769_MAP =
            buildSoundNameMap(SOUND_NAME_FALLBACKS_774_TO_769);
    private static final Map<String, String> SOUND_NAME_FALLBACK_774_TO_768_MAP =
            buildSoundNameMap(SOUND_NAME_FALLBACKS_774_TO_768);
    private static final Map<String, String> SOUND_NAME_FALLBACK_774_TO_767_MAP =
            buildSoundNameMap(SOUND_NAME_FALLBACKS_774_TO_767);
    private static final Map<String, String> SOUND_NAME_FALLBACK_774_TO_765_MAP =
            buildSoundNameMap(SOUND_NAME_FALLBACKS_774_TO_765);
    private static final Map<String, String> SOUND_NAME_FALLBACK_774_TO_763_MAP =
            buildSoundNameMap(SOUND_NAME_FALLBACKS_774_TO_763);
    private static final Map<String, String> SOUND_NAME_FALLBACK_774_TO_764_MAP =
            buildSoundNameMap(SOUND_NAME_FALLBACKS_774_TO_764);
    private static final Map<String, String> SOUND_NAME_FALLBACK_774_TO_766_MAP =
            buildSoundNameMap(SOUND_NAME_FALLBACKS_774_TO_766);
    private static final Map<String, String> SOUND_NAME_FALLBACK_768_TO_774_MAP =
            buildSoundNameMap(SOUND_NAME_FALLBACKS_768_TO_774);
    private static final Map<String, String> SOUND_NAME_FALLBACK_767_TO_774_MAP =
            buildSoundNameMap(SOUND_NAME_FALLBACKS_767_TO_774);
    private static final Map<String, String> SOUND_NAME_FALLBACK_766_TO_774_MAP =
            buildSoundNameMap(SOUND_NAME_FALLBACKS_766_TO_774);
"""

LOG_UP_774 = """
    private static void logUnknownUp774Once(String kind, int id774) {
        if (LOGGED_UNKNOWN.add("up774:" + kind + ":" + id774)) {
            LOG.warning("774に無い" + kind + "の番号が来たため代替に落とします（774 id=" + id774 + "）。");
        }
    }
"""
def adjacent_accessors():
    out = ["    // ------------------------------------------------------------------",
           "    // 773→774。上の *_774 表を使う。ブロック状態・ブロック・",
           "    // パーティクル・メニュー・属性・バイオームは同一。アイテムは",
           "    // 新17件（スポーンエッグ・槍・ノーチラス防具など）、エンティティ",
           "    // は新4件、音は新67件の挿入でずれる。エフェクトは新1件（39）、",
           "    // エンチャントは lunge（21）の挿入でずれる。ブロックエンティティ",
           "    // 種別は 773 と 774 で同一（49 件）。絵画・ジュークボックスの歌は",
           "    // 773 と同一。",
           "    // ------------------------------------------------------------------",
           ""]
    for kind in KINDS:
        name, prefix, jp = kind[0], kind[1], kind[2]
        fb774 = kind[36]
        out.append(f"    /** 773の{jp}ID → 774の{jp}ID。範囲外は{fb774} に相当する代替。 */")
        out.append(f"    public static int map{name}773To774(int id773) {{")
        out.append(f"        if (id773 < 0 || id773 > MAX_{prefix}_773) {{")
        out.append(f"            logUnknownOnce(PROTOCOL_774, \"{jp}\", id773);")
        out.append(f"            return {fb774};")
        out.append("        }")
        out.append(f"        return {prefix}_774_MAP[id773];")
        out.append("    }")
        out.append("")
    out.append("    /**")
    out.append("     * 774のアイテムID → 773のアイテムID（サーバー行きスロット用）。")
    out.append("     * 774にしか無いアイテム17件（スポーンエッグ4件・ネザライトの")
    out.append("     * 馬鎧1件・槍7件・ノーチラスの防具5件）は 773の石へ。改名は無い。")
    out.append("     * 範囲外はそのまま返す。")
    out.append("     */")
    out.append("    public static int mapItemReverse774(int id774) {")
    out.append("        if (id774 < 0 || id774 >= ITEM_REVERSE_774_MAP.length) {")
    out.append("            logUnknownOnce(PROTOCOL_774, \"アイテム（逆変換）\", id774);")
    out.append("            return id774;")
    out.append("        }")
    out.append("        int mapped = ITEM_REVERSE_774_MAP[id774];")
    out.append("        return mapped < 0 ? id774 : mapped;")
    out.append("    }")
    out.append("")
    out.append("    /**")
    out.append("     * 773のエフェクトID → 774のエフェクトID。773も774も0ベース")
    out.append("     * （Speed は両方 0）で 0〜38 は同一のため恒等写像。")
    out.append("     * 範囲外は Speed（0）。")
    out.append("     */")
    out.append("    public static int mapEffect773To774(int id773) {")
    out.append("        if (id773 < 0 || id773 > MAX_EFFECT_773) {")
    out.append("            logUnknownOnce(PROTOCOL_774, \"エフェクト\", id773);")
    out.append("            return SPEED_EFFECT_774;")
    out.append("        }")
    out.append("        return id773;")
    out.append("    }")
    out.append("")
    out.append("    /**")
    out.append("     * 773のブロックエンティティ種別ID → 774の種別ID。")
    out.append("     * 773 と 774 で同一（49 件）のため恒等写像。")
    out.append("     * 範囲外の種別は NBT の中身が種別専用のため安全に代替できず")
    out.append("     * {@link #DROP}。")
    out.append("     */")
    out.append("    public static int mapBlockEntityType773To774(int id773) {")
    # 上限は銅ゴーレム像（48）。test_instance_block（47）では無い。
    # 773 は像まで 49 件あるため、47 を上限にすると 48 が DROP になる。
    out.append("        if (id773 < 0 || id773 > BLOCK_ENTITY_COPPER_GOLEM_STATUE_773) {")
    out.append("            logUnknownOnce(PROTOCOL_774, \"ブロックエンティティ種別\", id773);")
    out.append("            return DROP;")
    out.append("        }")
    out.append("        return id773;")
    out.append("    }")
    out.append("")
    out.append("    /**")
    out.append("     * 音の名前（直書き・Stop Sound 用）を774向けに変換する。")
    out.append("     * {@code minecraft:} の有無はそのまま保つ。773の音の名前は")
    out.append("     * 774 にすべてあるため代替は無い。")
    out.append("     */")
    out.append("    public static String mapSoundName773To774(String name) {")
    out.append("        return mapSoundNameComposed(name, SOUND_NAME_FALLBACK_773_TO_774_MAP);")
    out.append("    }")
    out.append("")
    # 属性とエンチャント。
    out.append("    /** 773の属性ID → 774の属性ID。並びは同一。範囲外は {@link #DROP}。 */")
    out.append("    public static int mapAttribute773To774(int id773) {")
    out.append("        if (id773 < 0 || id773 > MAX_ATTRIBUTE_773) {")
    out.append("            logUnknownOnce(PROTOCOL_774, \"属性\", id773);")
    out.append("            return DROP;")
    out.append("        }")
    out.append("        return ATTRIBUTE_774_MAP[id773];")
    out.append("    }")
    out.append("")
    out.append("    /** 774の属性ID → 773の属性ID。並びは同一。範囲外は {@link #DROP}。 */")
    out.append("    public static int mapAttribute774To773(int id774) {")
    out.append("        if (id774 < 0 || id774 > MAX_ATTRIBUTE_774) {")
    out.append("            logUnknownOnce(PROTOCOL_774, \"属性\", id774);")
    out.append("            return DROP;")
    out.append("        }")
    out.append("        return ATTRIBUTE_REVERSE_774_MAP[id774];")
    out.append("    }")
    out.append("")
    out.append("    /** 774の属性ID → 属性の名前（Update Attributes の 773向け変換用）。 */")
    out.append("    public static String attributeName774(int id774) {")
    out.append("        if (id774 < 0 || id774 >= ATTRIBUTE_774_NAMES.length) {")
    out.append("            return null;")
    out.append("        }")
    out.append("        return ATTRIBUTE_774_NAMES[id774];")
    out.append("    }")
    out.append("")
    out.append("    /**")
    out.append("     * 属性の名前 → 774の属性ID（765／763／764 は属性が名前形式のため、")
    out.append("     * 774向けに変換するときに使う）。無い名前は {@link #DROP}。")
    out.append("     */")
    out.append("    public static int attributeId774(String name) {")
    out.append("        if (name == null) {")
    out.append("            return DROP;")
    out.append("        }")
    out.append("        String plain = stripMinecraftPrefix(name);")
    out.append("        for (int i = 0; i < ATTRIBUTE_774_NAMES.length; i++) {")
    out.append("            if (ATTRIBUTE_774_NAMES[i].equals(plain)) {")
    out.append("                return i;")
    out.append("            }")
    out.append("        }")
    out.append("        return DROP;")
    out.append("    }")
    out.append("")
    out.append("    /** 773のエンチャントID → 774のエンチャントID。lunge が 21 に挿入され 21 以降は +1 ずれる。範囲外は {@link #DROP}。 */")
    out.append("    public static int mapEnchantment773To774(int id773) {")
    out.append("        if (id773 < 0 || id773 > MAX_ENCHANTMENT_773) {")
    out.append("            logUnknownOnce(PROTOCOL_774, \"エンチャント\", id773);")
    out.append("            return DROP;")
    out.append("        }")
    out.append("        return ENCHANTMENT_774_MAP[id773];")
    out.append("    }")
    out.append("")
    out.append("    /** 774のエンチャントID → 773のエンチャントID。lunge（21）は DROP、22 以降は −1 ずれる。範囲外は {@link #DROP}。 */")
    out.append("    public static int mapEnchantment774To773(int id774) {")
    out.append("        if (id774 < 0 || id774 > MAX_ENCHANTMENT_774) {")
    out.append("            logUnknownOnce(PROTOCOL_774, \"エンチャント\", id774);")
    out.append("            return DROP;")
    out.append("        }")
    out.append("        return ENCHANTMENT_REVERSE_774_MAP[id774];")
    out.append("    }")
    out.append("")
    out.append("    /**")
    out.append("     * 767のエンチャントID → 774のエンチャントID。767→773 の表を")
    out.append("     * 経由して 773→774 の表を引く。範囲外は {@link #DROP}。")
    out.append("     */")
    out.append("    public static int mapEnchantment767To774(int id767) {")
    out.append("        int id773 = mapEnchantment767To773(id767);")
    out.append("        return id773 == DROP ? DROP : mapEnchantment773To774(id773);")
    out.append("    }")
    out.append("")
    out.append("    /**")
    out.append("     * 774のエンチャントID → 767のエンチャントID。774→773 の逆変換の")
    out.append("     * 結果を 773→767 の逆変換で戻す。範囲外は {@link #DROP}。")
    out.append("     */")
    out.append("    public static int mapEnchantment774To767(int id774) {")
    out.append("        int id773 = mapEnchantment774To773(id774);")
    out.append("        return id773 == DROP ? DROP : mapEnchantment773To767(id773);")
    out.append("    }")
    out.append("")
    out.append("    /**")
    out.append("     * 766のエンチャントID → 774のエンチャントID。766→767 の変換")
    out.append("     * （{@link #mapEnchantment766To767}）を経由して 767→774 の")
    out.append("     * 表を引く。範囲外は {@link #DROP}。")
    out.append("     */")
    out.append("    public static int mapEnchantment766To774(int id766) {")
    out.append("        int id767 = mapEnchantment766To767(id766);")
    out.append("        return id767 == DROP ? DROP : mapEnchantment767To774(id767);")
    out.append("    }")
    out.append("")
    out.append("    /**")
    out.append("     * 774のエンチャントID → 766のエンチャントID。774→767 の逆変換の")
    out.append("     * 結果を 767→766 の逆変換で戻す。範囲外は {@link #DROP}。")
    out.append("     */")
    out.append("    public static int mapEnchantment774To766(int id774) {")
    out.append("        int id767 = mapEnchantment774To767(id774);")
    out.append("        return id767 == DROP ? DROP : mapEnchantment767To766(id767);")
    out.append("    }")
    out.append("")
    out.append("    /**")
    out.append("     * 765のエンチャントID → 774のエンチャントID。765→767 の変換")
    out.append("     * （{@link #mapEnchantment765To767}）を経由して 767→774 の")
    out.append("     * 表を引く。範囲外は {@link #DROP}。")
    out.append("     * 763／764 のエンチャントの並びは 765 と同一のため、")
    out.append("     * 763／764 向けも同じ変換を使う。")
    out.append("     */")
    out.append("    public static int mapEnchantment765To774(int id765) {")
    out.append("        int id767 = mapEnchantment765To767(id765);")
    out.append("        return id767 == DROP ? DROP : mapEnchantment767To774(id767);")
    out.append("    }")
    out.append("")
    out.append("    /** 763のエンチャントID → 774のエンチャントID（765と同じ並び）。 */")
    out.append("    public static int mapEnchantment763To774(int id763) {")
    out.append("        return mapEnchantment765To774(id763);")
    out.append("    }")
    out.append("")
    out.append("    /** 764のエンチャントID → 774のエンチャントID（765と同じ並び）。 */")
    out.append("    public static int mapEnchantment764To774(int id764) {")
    out.append("        return mapEnchantment765To774(id764);")
    out.append("    }")
    out.append("")
    out.append("    /**")
    out.append("     * 774のエンチャントID → 765のエンチャントID。774→767 の逆変換の")
    out.append("     * 結果を 767→765 の変換（{@link #mapEnchantment767To765}）で")
    out.append("     * 戻す。density／breach／wind_burst は 765に無いため")
    out.append("     * {@link #DROP}。範囲外も {@link #DROP}。")
    out.append("     */")
    out.append("    public static int mapEnchantment774To765(int id774) {")
    out.append("        int id767 = mapEnchantment774To767(id774);")
    out.append("        return id767 == DROP ? DROP : mapEnchantment767To765(id767);")
    out.append("    }")
    out.append("")
    out.append("    /** 774のエンチャントID → 763のエンチャントID（765と同じ並び）。 */")
    out.append("    public static int mapEnchantment774To763(int id774) {")
    out.append("        return mapEnchantment774To765(id774);")
    out.append("    }")
    out.append("")
    out.append("    /** 774のエンチャントID → 764のエンチャントID（765と同じ並び）。 */")
    out.append("    public static int mapEnchantment774To764(int id774) {")
    out.append("        return mapEnchantment774To765(id774);")
    out.append("    }")
    return "\n".join(out)


def up_accessors():
    out = ["    // ------------------------------------------------------------------",
           "    // 逆向き（774→773）。上の *_774 表を反転して作った表を使う。",
           "    // アイテムだけは生成スクリプトが名前から作った既存の逆変換表",
           "    // （ITEM_REVERSE_774_MAP）をそのまま使う（反転した表と完全一致",
           "    // することはテストで確認している）。属性とエンチャントの逆向きは",
           "    // mapAttribute774To773／mapEnchantment774To773 を参照。",
           "    // ------------------------------------------------------------------",
           ""]
    for kind in KINDS:
        name, prefix, jp = kind[0], kind[1], kind[2]
        # 逆向きの代替先は「773 側」の定数（kind[35]）。
        fb773 = kind[35]
        if prefix == "ITEM":
            out.append("    /**")
            out.append("     * 774のアイテムID → 773のアイテムID。")
            out.append("     * 774にしか無いアイテム17件（スポーンエッグ4件・ネザライトの")
            out.append("     * 馬鎧1件・槍7件・ノーチラスの防具5件）は石へ。範囲外は石（1）。")
            out.append("     */")
            out.append("    public static int mapItemUp774(int id774) {")
            out.append("        if (id774 < 0 || id774 >= ITEM_REVERSE_774_MAP.length) {")
            out.append("            logUnknownUp774Once(\"アイテム\", id774);")
            out.append("            return STONE_ITEM_773;")
            out.append("        }")
            out.append("        int mapped = ITEM_REVERSE_774_MAP[id774];")
            out.append("        return mapped < 0 ? STONE_ITEM_773 : mapped;")
            out.append("    }")
            out.append("")
            continue
        out.append(f"    /** 774の{jp}ID → 773の{jp}ID。範囲外は{fb773} に相当する代替。 */")
        out.append(f"    public static int map{name}Up774(int id774) {{")
        out.append(f"        if (id774 < 0 || id774 > MAX_{prefix}_774) {{")
        out.append(f"            logUnknownUp774Once(\"{jp}\", id774);")
        out.append(f"            return {fb773};")
        out.append("        }")
        out.append(f"        return {prefix}_UP_774_MAP[id774];")
        out.append("    }")
        out.append("")
    out.append("    /**")
    out.append("     * 774のエフェクトID → 773のエフェクトID。0〜38 は同一、39")
    out.append("     * （BreathOfTheNautilus）は Speed（0）へ。範囲外は Speed（0）。")
    out.append("     */")
    out.append("    public static int mapEffectUp774(int id774) {")
    out.append("        if (id774 < 0 || id774 > MAX_EFFECT_774) {")
    out.append("            logUnknownUp774Once(\"エフェクト\", id774);")
    out.append("            return SPEED_EFFECT_773;")
    out.append("        }")
    out.append("        return id774 <= MAX_EFFECT_773 ? id774 : SPEED_EFFECT_773;")
    out.append("    }")
    out.append("")
    out.append("    /**")
    out.append("     * 774のブロックエンティティ種別ID → 773の種別ID。")
    out.append("     * 773 と 774 で同一（49 件）のため恒等写像。")
    out.append("     * 範囲外の種別は NBT の中身が種別専用のため安全に代替できず")
    out.append("     * {@link #DROP}。")
    out.append("     */")
    out.append("    public static int mapBlockEntityTypeUp774(int id774) {")
    # 上限は銅ゴーレム像（48）。773→774 と同じ理由。
    out.append("        if (id774 < 0 || id774 > BLOCK_ENTITY_COPPER_GOLEM_STATUE_774) {")
    out.append("            logUnknownUp774Once(\"ブロックエンティティ種別\", id774);")
    out.append("            return DROP;")
    out.append("        }")
    out.append("        return id774;")
    out.append("    }")
    out.append("")
    out.append("    /**")
    out.append("     * 音の名前を773向けに変換する。774の音の名前のうち 773に無いものは")
    out.append("     * 代替の音の名前にする（数値の反転表と一致させる）。774の新音")
    out.append("     * （ノーチラス・槍・銅の時代関連など67件）は石の破壊音へ。")
    out.append("     * {@code null} は石の破壊音にする。")
    out.append("     */")
    out.append("    public static String mapSoundNameUp774(String name) {")
    out.append("        return mapSoundNameComposed(name, SOUND_NAME_FALLBACK_774_TO_773_MAP);")
    out.append("    }")
    return "\n".join(out)
def composed_accessors():
    out = ["    // ------------------------------------------------------------------",
           "    // 離れた組み合わせの合成表（763↔774、764↔774、765↔774、766↔774、",
           "    // 767↔774、768↔774、769↔774、770↔774、771↔774、772↔774）。",
           "    // 上の *_763_TO_774／*_774_TO_763／*_764_TO_774／*_774_TO_764／",
           "    // *_765_TO_774／*_774_TO_765／*_766_TO_774／*_774_TO_766／",
           "    // *_767_TO_774／*_774_TO_767／*_768_TO_774／*_774_TO_768／",
           "    // *_769_TO_774／*_774_TO_769／*_770_TO_774／*_774_TO_770／",
           "    // *_771_TO_774／*_774_TO_771／*_772_TO_774／*_774_TO_772 表",
           "    // （合成済みの1つの表）",
           "    // を直接引く。二段変換はしない。エフェクトとブロックエンティティ",
           "    // 種別は表を作らず、数値の規則と同一の並びで扱う。",
           "    // ------------------------------------------------------------------",
           ""]
    pairs = [
        ("763", 25, "763→774", "774→763"),
        ("764", 26, "764→774", "774→764"),
        ("765", 27, "765→774", "774→765"),
        ("766", 28, "766→774", "774→766"),
        ("767", 29, "767→774", "774→767"),
        ("768", 30, "768→774", "774→768"),
        ("769", 31, "769→774", "774→769"),
        ("770", 32, "770→774", "774→770"),
        ("771", 33, "771→774", "774→771"),
        ("772", 34, "772→774", "774→772"),
    ]
    # 773 は隣接表（773↔774）で扱うため合成のペアには含めない。
    for src, fb_src_idx, up_label, down_label in pairs:
        for kind in KINDS:
            name, prefix, jp = kind[0], kind[1], kind[2]
            fb_src = kind[fb_src_idx]
            fb_774 = kind[36]
            # src → 774
            out.append(f"    /** {src}の{jp}ID → 774の{jp}ID。範囲外は{fb_774} に相当する代替。 */")
            out.append(f"    public static int map{name}{src}To774(int id{src}) {{")
            out.append(f"        if (id{src} < 0 || id{src} >= {prefix}_{src}_TO_774_MAP.length) {{")
            out.append(f"            logUnknownComposedOnce(\"{up_label}\", \"{jp}\", id{src});")
            out.append(f"            return {fb_774};")
            out.append("        }")
            out.append(f"        return {prefix}_{src}_TO_774_MAP[id{src}];")
            out.append("    }")
            out.append("")
            # 774 → src
            out.append(f"    /** 774の{jp}ID → {src}の{jp}ID。範囲外は{fb_src} に相当する代替。 */")
            out.append(f"    public static int map{name}774To{src}(int id774) {{")
            out.append(f"        if (id774 < 0 || id774 >= {prefix}_774_TO_{src}_MAP.length) {{")
            out.append(f"            logUnknownComposedOnce(\"{down_label}\", \"{jp}\", id774);")
            out.append(f"            return {fb_src};")
            out.append("        }")
            out.append(f"        return {prefix}_774_TO_{src}_MAP[id774];")
            out.append("    }")
            out.append("")
        # エフェクトとブロックエンティティ種別と音の名前。
        if src == "763":
            out.append("    /**")
            out.append("     * 763のエフェクトID → 774のエフェクトID。763は1ベース")
            out.append("     * （Speed=1）のため 1 引く。範囲外は Speed（0）。")
            out.append("     */")
            out.append("    public static int mapEffect763To774(int id763) {")
            out.append("        if (id763 < 1 || id763 > MAX_EFFECT_765 + 1) {")
            out.append("            logUnknownComposedOnce(\"763→774\", \"エフェクト\", id763);")
            out.append("            return SPEED_EFFECT_774;")
            out.append("        }")
            out.append("        return id763 - 1;")
            out.append("    }")
            out.append("")
            out.append("    /**")
            out.append("     * 774のエフェクトID → 763のエフェクトID。763は1ベース")
            out.append("     * （Speed=1）のため 1 足す。774で増えた 33〜39 は")
            out.append("     * 763に無いため Speed（1）へ。範囲外も Speed（1）。")
            out.append("     */")
            out.append("    public static int mapEffect774To763(int id774) {")
            out.append("        if (id774 < 0 || id774 > MAX_EFFECT_774) {")
            out.append("            logUnknownComposedOnce(\"774→763\", \"エフェクト\", id774);")
            out.append("            return SPEED_EFFECT_763;")
            out.append("        }")
            out.append("        return id774 <= MAX_EFFECT_765 ? id774 + 1 : SPEED_EFFECT_763;")
            out.append("    }")
            out.append("")
            out.append("    /**")
            out.append("     * 763のブロックエンティティ種別ID → 774の種別ID。")
            out.append("     * 763→773 と 773→774（恒等）の連鎖。")
            out.append("     * 範囲外は {@link #DROP}。")
            out.append("     */")
            out.append("    public static int mapBlockEntityType763To774(int id763) {")
            out.append("        if (id763 < 0 || id763 > BLOCK_ENTITY_TRIAL_SPAWNER_765 - 2) {")
            out.append("            logUnknownComposedOnce(\"763→774\", \"ブロックエンティティ種別\", id763);")
            out.append("            return DROP;")
            out.append("        }")
            out.append("        return mapBlockEntityType773To774(mapBlockEntityType763To773(id763));")
            out.append("    }")
            out.append("")
            out.append("    /**")
            out.append("     * 774のブロックエンティティ種別ID → 763の種別ID。")
            out.append("     * 774→773（恒等）と 773→763 の連鎖。")
            out.append("     * 範囲外は {@link #DROP}。")
            out.append("     */")
            out.append("    public static int mapBlockEntityType774To763(int id774) {")
            out.append("        if (id774 < 0 || id774 > BLOCK_ENTITY_TRIAL_SPAWNER_774 - 2) {")
            out.append("            logUnknownComposedOnce(\"774→763\", \"ブロックエンティティ種別\", id774);")
            out.append("            return DROP;")
            out.append("        }")
            out.append("        return mapBlockEntityType773To763(mapBlockEntityTypeUp774(id774));")
            out.append("    }")
            out.append("")
            out.append("    /**")
            out.append("     * 音の名前を774向けに変換する。763の音の名前のうち 768で削除された")
            out.append("     * ヤギの角笛の音2件と 770で削除された entity.wolf.howl と")
            out.append("     * 771で削除された leash_knot 系・puffer_fish.ambient・")
            out.append("     * block.sand.wind は代替の名前にする。{@code null} は石の破壊音にする。")
            out.append("     */")
            out.append("    public static String mapSoundName763To774(String name) {")
            out.append("        return mapSoundNameComposed(")
            out.append("                mapSoundNameComposed(")
            out.append("                        mapSoundNameComposed(")
            out.append("                                mapSoundNameComposed(")
            out.append("                                        mapSoundNameComposed(")
            out.append("                                                mapSoundNameComposed(name, SOUND_NAME_FALLBACK_768_MAP),")
            out.append("                                        SOUND_NAME_FALLBACK_770_MAP),")
            out.append("                                SOUND_NAME_FALLBACK_771_MAP),")
            out.append("                        SOUND_NAME_FALLBACK_772_MAP),")
            out.append("                SOUND_NAME_FALLBACK_773_MAP),")
            out.append("        SOUND_NAME_FALLBACK_774_MAP);")
            out.append("    }")
            out.append("")
            out.append("    /**")
            out.append("     * 音の名前を763向けに変換する。774の音の名前のうち 763に無いものは")
            out.append("     * 代替の音の名前にする（数値の合成表と一致させる）。")
            out.append("     * {@code null} は石の破壊音にする。")
            out.append("     */")
            out.append("    public static String mapSoundName774To763(String name) {")
            out.append("        return mapSoundNameComposed(name, SOUND_NAME_FALLBACK_774_TO_763_MAP);")
            out.append("    }")
        elif src == "764":
            out.append("    /**")
            out.append("     * 764のエフェクトID → 774のエフェクトID。764も774も0ベース")
            out.append("     * （Speed は両方 0）で 0〜32 は同一のため恒等写像。")
            out.append("     * 範囲外は Speed（0）。")
            out.append("     */")
            out.append("    public static int mapEffect764To774(int id764) {")
            out.append("        if (id764 < 0 || id764 > MAX_EFFECT_764) {")
            out.append("            logUnknownComposedOnce(\"764→774\", \"エフェクト\", id764);")
            out.append("            return SPEED_EFFECT_774;")
            out.append("        }")
            out.append("        return id764;")
            out.append("    }")
            out.append("")
            out.append("    /**")
            out.append("     * 774のエフェクトID → 764のエフェクトID。0〜32 は同一のため恒等写像。")
            out.append("     * 774で増えた 33〜39 は 764に無いため Speed（0）へ。")
            out.append("     * 範囲外も Speed（0）。")
            out.append("     */")
            out.append("    public static int mapEffect774To764(int id774) {")
            out.append("        if (id774 < 0 || id774 > MAX_EFFECT_774) {")
            out.append("            logUnknownComposedOnce(\"774→764\", \"エフェクト\", id774);")
            out.append("            return SPEED_EFFECT_764;")
            out.append("        }")
            out.append("        return id774 <= MAX_EFFECT_764 ? id774 : SPEED_EFFECT_764;")
            out.append("    }")
            out.append("")
            out.append("    /**")
            out.append("     * 764のブロックエンティティ種別ID → 774の種別ID。")
            out.append("     * 764→773 と 773→774（恒等）の連鎖。")
            out.append("     * 範囲外は {@link #DROP}。")
            out.append("     */")
            out.append("    public static int mapBlockEntityType764To774(int id764) {")
            out.append("        if (id764 < 0 || id764 > BLOCK_ENTITY_TRIAL_SPAWNER_765 - 2) {")
            out.append("            logUnknownComposedOnce(\"764→774\", \"ブロックエンティティ種別\", id764);")
            out.append("            return DROP;")
            out.append("        }")
            out.append("        return mapBlockEntityType773To774(mapBlockEntityType764To773(id764));")
            out.append("    }")
            out.append("")
            out.append("    /**")
            out.append("     * 774のブロックエンティティ種別ID → 764の種別ID。")
            out.append("     * 774→773（恒等）と 773→764 の連鎖。")
            out.append("     * 範囲外は {@link #DROP}。")
            out.append("     */")
            out.append("    public static int mapBlockEntityType774To764(int id774) {")
            out.append("        if (id774 < 0 || id774 > BLOCK_ENTITY_TRIAL_SPAWNER_774 - 2) {")
            out.append("            logUnknownComposedOnce(\"774→764\", \"ブロックエンティティ種別\", id774);")
            out.append("            return DROP;")
            out.append("        }")
            out.append("        return mapBlockEntityType773To764(mapBlockEntityTypeUp774(id774));")
            out.append("    }")
            out.append("")
            out.append("    /**")
            out.append("     * 音の名前を774向けに変換する。764の音の名前のうち 768で削除された")
            out.append("     * ヤギの角笛の音2件と 770で削除された entity.wolf.howl と")
            out.append("     * 771で削除された leash_knot 系・puffer_fish.ambient・")
            out.append("     * block.sand.wind は代替の名前にする。{@code null} は石の破壊音にする。")
            out.append("     */")
            out.append("    public static String mapSoundName764To774(String name) {")
            out.append("        return mapSoundNameComposed(")
            out.append("                mapSoundNameComposed(")
            out.append("                        mapSoundNameComposed(")
            out.append("                                mapSoundNameComposed(")
            out.append("                                        mapSoundNameComposed(")
            out.append("                                                mapSoundNameComposed(name, SOUND_NAME_FALLBACK_768_MAP),")
            out.append("                                        SOUND_NAME_FALLBACK_770_MAP),")
            out.append("                                SOUND_NAME_FALLBACK_771_MAP),")
            out.append("                        SOUND_NAME_FALLBACK_772_MAP),")
            out.append("                SOUND_NAME_FALLBACK_773_MAP),")
            out.append("        SOUND_NAME_FALLBACK_774_MAP);")
            out.append("    }")
            out.append("")
            out.append("    /**")
            out.append("     * 音の名前を764向けに変換する。774の音の名前のうち 764に無いものは")
            out.append("     * 代替の音の名前にする（数値の合成表と一致させる）。")
            out.append("     * {@code null} は石の破壊音にする。")
            out.append("     */")
            out.append("    public static String mapSoundName774To764(String name) {")
            out.append("        return mapSoundNameComposed(name, SOUND_NAME_FALLBACK_774_TO_764_MAP);")
            out.append("    }")
        elif src == "765":
            out.append("    /**")
            out.append("     * 765のエフェクトID → 774のエフェクトID。765も774も0ベース")
            out.append("     * （Speed は両方 0）で 0〜32 は同一のため恒等写像。")
            out.append("     * 範囲外は Speed（0）。")
            out.append("     */")
            out.append("    public static int mapEffect765To774(int id765) {")
            out.append("        if (id765 < 0 || id765 > MAX_EFFECT_765) {")
            out.append("            logUnknownComposedOnce(\"765→774\", \"エフェクト\", id765);")
            out.append("            return SPEED_EFFECT_774;")
            out.append("        }")
            out.append("        return id765;")
            out.append("    }")
            out.append("")
            out.append("    /**")
            out.append("     * 774のエフェクトID → 765のエフェクトID。0〜32 は同一のため恒等写像。")
            out.append("     * 774で増えた 33〜39 は 765に無いため Speed（0）へ。")
            out.append("     * 範囲外も Speed（0）。")
            out.append("     */")
            out.append("    public static int mapEffect774To765(int id774) {")
            out.append("        if (id774 < 0 || id774 > MAX_EFFECT_774) {")
            out.append("            logUnknownComposedOnce(\"774→765\", \"エフェクト\", id774);")
            out.append("            return SPEED_EFFECT_765;")
            out.append("        }")
            out.append("        return id774 <= MAX_EFFECT_765 ? id774 : SPEED_EFFECT_765;")
            out.append("    }")
            out.append("")
            out.append("    /**")
            out.append("     * 765のブロックエンティティ種別ID → 774の種別ID。")
            out.append("     * 765→773 と 773→774（恒等）の連鎖。")
            out.append("     * 範囲外は {@link #DROP}。")
            out.append("     */")
            out.append("    public static int mapBlockEntityType765To774(int id765) {")
            out.append("        if (id765 < 0 || id765 > BLOCK_ENTITY_TRIAL_SPAWNER_765) {")
            out.append("            logUnknownComposedOnce(\"765→774\", \"ブロックエンティティ種別\", id765);")
            out.append("            return DROP;")
            out.append("        }")
            out.append("        return mapBlockEntityType773To774(mapBlockEntityType765To773(id765));")
            out.append("    }")
            out.append("")
            out.append("    /**")
            out.append("     * 774のブロックエンティティ種別ID → 765の種別ID。")
            out.append("     * 774→773（恒等）と 773→765 の連鎖。")
            out.append("     * 範囲外は {@link #DROP}。")
            out.append("     */")
            out.append("    public static int mapBlockEntityType774To765(int id774) {")
            out.append("        if (id774 < 0 || id774 > BLOCK_ENTITY_TRIAL_SPAWNER_774) {")
            out.append("            logUnknownComposedOnce(\"774→765\", \"ブロックエンティティ種別\", id774);")
            out.append("            return DROP;")
            out.append("        }")
            out.append("        return mapBlockEntityType773To765(mapBlockEntityTypeUp774(id774));")
            out.append("    }")
            out.append("")
            out.append("    /**")
            out.append("     * 音の名前を774向けに変換する。765の音の名前のうち 766で改名された")
            out.append("     * entity.generic.wind_burst と 767で改名された trial_spawner の音2件は")
            out.append("     * 新しい名前にし、768で削除されたヤギの角笛の音2件と 770で削除された")
            out.append("     * entity.wolf.howl と 771で削除された leash_knot 系・")
            out.append("     * puffer_fish.ambient・block.sand.wind は代替の名前にする。")
            out.append("     * {@code null} は石の破壊音にする。")
            out.append("     */")
            out.append("    public static String mapSoundName765To774(String name) {")
            out.append("        return mapSoundNameComposed(")
            out.append("                mapSoundNameComposed(")
            out.append("                        mapSoundNameComposed(")
            out.append("                                mapSoundNameComposed(")
            out.append("                                        mapSoundNameComposed(")
            out.append("                                                mapSoundNameComposed(")
            out.append("                                                        mapSoundNameComposed(")
            out.append("                                                                mapSoundNameComposed(name, SOUND_NAME_FALLBACK_766_MAP),")
            out.append("                                                        SOUND_NAME_FALLBACK_767_MAP),")
            out.append("                                                SOUND_NAME_FALLBACK_768_MAP),")
            out.append("                                        SOUND_NAME_FALLBACK_770_MAP),")
            out.append("                                SOUND_NAME_FALLBACK_771_MAP),")
            out.append("                        SOUND_NAME_FALLBACK_772_MAP),")
            out.append("                SOUND_NAME_FALLBACK_773_MAP),")
            out.append("        SOUND_NAME_FALLBACK_774_MAP);")
            out.append("    }")
            out.append("")
            out.append("    /**")
            out.append("     * 音の名前を765向けに変換する。774の音の名前のうち 765に無いものは")
            out.append("     * 代替の音の名前にする（数値の合成表と一致させる）。")
            out.append("     * {@code null} は石の破壊音にする。")
            out.append("     */")
            out.append("    public static String mapSoundName774To765(String name) {")
            out.append("        return mapSoundNameComposed(name, SOUND_NAME_FALLBACK_774_TO_765_MAP);")
            out.append("    }")
        elif src == "766":
            out.append("    /**")
            out.append("     * 766のエフェクトID → 774のエフェクトID。766も774も0ベース")
            out.append("     * （Speed は両方 0）で 0〜38 は同一のため恒等写像。")
            out.append("     * 範囲外は Speed（0）。")
            out.append("     */")
            out.append("    public static int mapEffect766To774(int id766) {")
            out.append("        if (id766 < 0 || id766 > MAX_EFFECT_766) {")
            out.append("            logUnknownComposedOnce(\"766→774\", \"エフェクト\", id766);")
            out.append("            return SPEED_EFFECT_774;")
            out.append("        }")
            out.append("        return id766;")
            out.append("    }")
            out.append("")
            out.append("    /**")
            out.append("     * 774のエフェクトID → 766のエフェクトID。0〜38 は同一、39（BreathOfTheNautilus）は Speed（0）へ。")
            out.append("     * 範囲外は Speed（0）。")
            out.append("     */")
            out.append("    public static int mapEffect774To766(int id774) {")
            out.append("        if (id774 < 0 || id774 > MAX_EFFECT_774) {")
            out.append("            logUnknownComposedOnce(\"774→766\", \"エフェクト\", id774);")
            out.append("            return SPEED_EFFECT_766;")
            out.append("        }")
            out.append("        return id774 <= MAX_EFFECT_766 ? id774 : SPEED_EFFECT_766;")
            out.append("    }")
            out.append("")
            out.append("    /**")
            out.append("     * 766のブロックエンティティ種別ID → 774の種別ID。")
            out.append("     * 766→773 と 773→774（恒等）の連鎖。")
            out.append("     * 範囲外は {@link #DROP}。")
            out.append("     */")
            out.append("    public static int mapBlockEntityType766To774(int id766) {")
            out.append("        if (id766 < 0 || id766 > BLOCK_ENTITY_VAULT_766) {")
            out.append("            logUnknownComposedOnce(\"766→774\", \"ブロックエンティティ種別\", id766);")
            out.append("            return DROP;")
            out.append("        }")
            out.append("        return mapBlockEntityType773To774(mapBlockEntityType766To773(id766));")
            out.append("    }")
            out.append("")
            out.append("    /**")
            out.append("     * 774のブロックエンティティ種別ID → 766の種別ID。")
            out.append("     * 774→773（恒等）と 773→766 の連鎖。")
            out.append("     * 範囲外は {@link #DROP}。")
            out.append("     */")
            out.append("    public static int mapBlockEntityType774To766(int id774) {")
            out.append("        if (id774 < 0 || id774 > BLOCK_ENTITY_VAULT_774) {")
            out.append("            logUnknownComposedOnce(\"774→766\", \"ブロックエンティティ種別\", id774);")
            out.append("            return DROP;")
            out.append("        }")
            out.append("        return mapBlockEntityType773To766(mapBlockEntityTypeUp774(id774));")
            out.append("    }")
            out.append("")
            out.append("    /**")
            out.append("     * 音の名前を774向けに変換する。766の音の名前のうち 767で改名された")
            out.append("     * trial_spawner の音2件は新しい名前にし、768で削除されたヤギの角笛の音2件と")
            out.append("     * 770で削除された entity.wolf.howl と 771で削除された leash_knot 系・")
            out.append("     * puffer_fish.ambient・block.sand.wind は代替の名前にする。")
            out.append("     * {@code null} は石の破壊音にする。")
            out.append("     */")
            out.append("    public static String mapSoundName766To774(String name) {")
            out.append("        return mapSoundNameComposed(name, SOUND_NAME_FALLBACK_766_TO_774_MAP);")
            out.append("    }")
            out.append("")
            out.append("    /**")
            out.append("     * 音の名前を766向けに変換する。774の音の名前のうち 766に無いものは")
            out.append("     * 代替の音の名前にする（数値の合成表と一致させる）。")
            out.append("     * {@code null} は石の破壊音にする。")
            out.append("     */")
            out.append("    public static String mapSoundName774To766(String name) {")
            out.append("        return mapSoundNameComposed(name, SOUND_NAME_FALLBACK_774_TO_766_MAP);")
            out.append("    }")
        elif src == "768":
            out.append("    /**")
            out.append("     * 768のエフェクトID → 774のエフェクトID。768も774も0ベース")
            out.append("     * （Speed は両方 0）で 0〜38 は同一のため恒等写像。")
            out.append("     * 範囲外は Speed（0）。")
            out.append("     */")
            out.append("    public static int mapEffect768To774(int id768) {")
            out.append("        if (id768 < 0 || id768 > MAX_EFFECT_768) {")
            out.append("            logUnknownComposedOnce(\"768→774\", \"エフェクト\", id768);")
            out.append("            return SPEED_EFFECT_774;")
            out.append("        }")
            out.append("        return id768;")
            out.append("    }")
            out.append("")
            out.append("    /**")
            out.append("     * 774のエフェクトID → 768のエフェクトID。0〜38 は同一、39（BreathOfTheNautilus）は Speed（0）へ。")
            out.append("     * 範囲外は Speed（0）。")
            out.append("     */")
            out.append("    public static int mapEffect774To768(int id774) {")
            out.append("        if (id774 < 0 || id774 > MAX_EFFECT_774) {")
            out.append("            logUnknownComposedOnce(\"774→768\", \"エフェクト\", id774);")
            out.append("            return SPEED_EFFECT_768;")
            out.append("        }")
            out.append("        return id774 <= MAX_EFFECT_768 ? id774 : SPEED_EFFECT_768;")
            out.append("    }")
            out.append("")
            out.append("    /**")
            out.append("     * 768のブロックエンティティ種別ID → 774の種別ID。")
            out.append("     * 768→773 と 773→774（恒等）の連鎖。")
            out.append("     * 範囲外は {@link #DROP}。")
            out.append("     */")
            out.append("    public static int mapBlockEntityType768To774(int id768) {")
            out.append("        if (id768 < 0 || id768 > BLOCK_ENTITY_VAULT_768) {")
            out.append("            logUnknownComposedOnce(\"768→774\", \"ブロックエンティティ種別\", id768);")
            out.append("            return DROP;")
            out.append("        }")
            out.append("        return mapBlockEntityType773To774(mapBlockEntityType768To773(id768));")
            out.append("    }")
            out.append("")
            out.append("    /**")
            out.append("     * 774のブロックエンティティ種別ID → 768の種別ID。")
            out.append("     * 774→773（恒等）と 773→768（棚・銅ゴーレム像は {@link #DROP}）の連鎖。")
            out.append("     * 範囲外は {@link #DROP}。")
            out.append("     */")
            out.append("    public static int mapBlockEntityType774To768(int id774) {")
            out.append("        if (id774 < 0 || id774 > BLOCK_ENTITY_VAULT_774) {")
            out.append("            logUnknownComposedOnce(\"774→768\", \"ブロックエンティティ種別\", id774);")
            out.append("            return DROP;")
            out.append("        }")
            out.append("        return mapBlockEntityType773To768(mapBlockEntityTypeUp774(id774));")
            out.append("    }")
            out.append("")
            out.append("    /**")
            out.append("     * 音の名前を774向けに変換する。768の音の名前のうち 770で削除された")
            out.append("     * entity.wolf.howl と 771で削除された leash_knot 系・")
            out.append("     * puffer_fish.ambient・block.sand.wind は代替の名前にする。")
            out.append("     * {@code null} は石の破壊音にする。")
            out.append("     */")
            out.append("    public static String mapSoundName768To774(String name) {")
            out.append("        return mapSoundNameComposed(name, SOUND_NAME_FALLBACK_768_TO_774_MAP);")
            out.append("    }")
            out.append("")
            out.append("    /**")
            out.append("     * 音の名前を768向けに変換する。774の音の名前のうち 768に無いものは")
            out.append("     * 代替の音の名前にする（数値の合成表と一致させる）。")
            out.append("     * {@code null} は石の破壊音にする。")
            out.append("     */")
            out.append("    public static String mapSoundName774To768(String name) {")
            out.append("        return mapSoundNameComposed(name, SOUND_NAME_FALLBACK_774_TO_768_MAP);")
            out.append("    }")
        elif src == "767":
            out.append("    /**")
            out.append("     * 767のエフェクトID → 774のエフェクトID。767も774も0ベース")
            out.append("     * （Speed は両方 0）で 0〜38 は同一のため恒等写像。")
            out.append("     * 範囲外は Speed（0）。")
            out.append("     */")
            out.append("    public static int mapEffect767To774(int id767) {")
            out.append("        if (id767 < 0 || id767 > MAX_EFFECT_767) {")
            out.append("            logUnknownComposedOnce(\"767→774\", \"エフェクト\", id767);")
            out.append("            return SPEED_EFFECT_774;")
            out.append("        }")
            out.append("        return id767;")
            out.append("    }")
            out.append("")
            out.append("    /**")
            out.append("     * 774のエフェクトID → 767のエフェクトID。0〜38 は同一、39（BreathOfTheNautilus）は Speed（0）へ。")
            out.append("     * 範囲外は Speed（0）。")
            out.append("     */")
            out.append("    public static int mapEffect774To767(int id774) {")
            out.append("        if (id774 < 0 || id774 > MAX_EFFECT_774) {")
            out.append("            logUnknownComposedOnce(\"774→767\", \"エフェクト\", id774);")
            out.append("            return SPEED_EFFECT_767;")
            out.append("        }")
            out.append("        return id774 <= MAX_EFFECT_767 ? id774 : SPEED_EFFECT_767;")
            out.append("    }")
            out.append("")
            out.append("    /**")
            out.append("     * 767のブロックエンティティ種別ID → 774の種別ID。")
            out.append("     * 767→773 と 773→774（恒等）の連鎖。")
            out.append("     * 範囲外は {@link #DROP}。")
            out.append("     */")
            out.append("    public static int mapBlockEntityType767To774(int id767) {")
            out.append("        if (id767 < 0 || id767 > BLOCK_ENTITY_VAULT_766) {")
            out.append("            logUnknownComposedOnce(\"767→774\", \"ブロックエンティティ種別\", id767);")
            out.append("            return DROP;")
            out.append("        }")
            out.append("        return mapBlockEntityType773To774(mapBlockEntityType767To773(id767));")
            out.append("    }")
            out.append("")
            out.append("    /**")
            out.append("     * 774のブロックエンティティ種別ID → 767の種別ID。")
            out.append("     * 774→773（恒等）と 773→767 の連鎖。")
            out.append("     * 範囲外は {@link #DROP}。")
            out.append("     */")
            out.append("    public static int mapBlockEntityType774To767(int id774) {")
            out.append("        if (id774 < 0 || id774 > BLOCK_ENTITY_VAULT_774) {")
            out.append("            logUnknownComposedOnce(\"774→767\", \"ブロックエンティティ種別\", id774);")
            out.append("            return DROP;")
            out.append("        }")
            out.append("        return mapBlockEntityType773To767(mapBlockEntityTypeUp774(id774));")
            out.append("    }")
            out.append("")
            out.append("    /**")
            out.append("     * 音の名前を774向けに変換する。767の音の名前のうち 768で削除された")
            out.append("     * ヤギの角笛の音2件と 770で削除された entity.wolf.howl と")
            out.append("     * 771で削除された leash_knot 系・puffer_fish.ambient・")
            out.append("     * block.sand.wind は代替の名前にする。")
            out.append("     * {@code null} は石の破壊音にする。")
            out.append("     */")
            out.append("    public static String mapSoundName767To774(String name) {")
            out.append("        return mapSoundNameComposed(name, SOUND_NAME_FALLBACK_767_TO_774_MAP);")
            out.append("    }")
            out.append("")
            out.append("    /**")
            out.append("     * 音の名前を767向けに変換する。774の音の名前のうち 767に無いものは")
            out.append("     * 代替の音の名前にする（数値の合成表と一致させる）。")
            out.append("     * {@code null} は石の破壊音にする。")
            out.append("     */")
            out.append("    public static String mapSoundName774To767(String name) {")
            out.append("        return mapSoundNameComposed(name, SOUND_NAME_FALLBACK_774_TO_767_MAP);")
            out.append("    }")
        elif src == "769":
            out.append("    /**")
            out.append("     * 769のエフェクトID → 774のエフェクトID。769も774も0ベース")
            out.append("     * （Speed は両方 0）で 0〜38 は同一のため恒等写像。")
            out.append("     * 範囲外は Speed（0）。")
            out.append("     */")
            out.append("    public static int mapEffect769To774(int id769) {")
            out.append("        if (id769 < 0 || id769 > MAX_EFFECT_769) {")
            out.append("            logUnknownComposedOnce(\"769→774\", \"エフェクト\", id769);")
            out.append("            return SPEED_EFFECT_774;")
            out.append("        }")
            out.append("        return id769;")
            out.append("    }")
            out.append("")
            out.append("    /**")
            out.append("     * 774のエフェクトID → 769のエフェクトID。0〜38 は同一、39（BreathOfTheNautilus）は Speed（0）へ。")
            out.append("     * 範囲外は Speed（0）。")
            out.append("     */")
            out.append("    public static int mapEffect774To769(int id774) {")
            out.append("        if (id774 < 0 || id774 > MAX_EFFECT_774) {")
            out.append("            logUnknownComposedOnce(\"774→769\", \"エフェクト\", id774);")
            out.append("            return SPEED_EFFECT_769;")
            out.append("        }")
            out.append("        return id774 <= MAX_EFFECT_769 ? id774 : SPEED_EFFECT_769;")
            out.append("    }")
            out.append("")
            out.append("    /**")
            out.append("     * 769のブロックエンティティ種別ID → 774の種別ID。")
            out.append("     * 769→773 と 773→774（恒等）の連鎖。")
            out.append("     * 範囲外は {@link #DROP}。")
            out.append("     */")
            out.append("    public static int mapBlockEntityType769To774(int id769) {")
            out.append("        if (id769 < 0 || id769 > BLOCK_ENTITY_VAULT_769) {")
            out.append("            logUnknownComposedOnce(\"769→774\", \"ブロックエンティティ種別\", id769);")
            out.append("            return DROP;")
            out.append("        }")
            out.append("        return mapBlockEntityType773To774(mapBlockEntityType769To773(id769));")
            out.append("    }")
            out.append("")
            out.append("    /**")
            out.append("     * 774のブロックエンティティ種別ID → 769の種別ID。")
            out.append("     * 774→773（恒等）と 773→769（棚・銅ゴーレム像は {@link #DROP}）の連鎖。")
            out.append("     * 範囲外は {@link #DROP}。")
            out.append("     */")
            out.append("    public static int mapBlockEntityType774To769(int id774) {")
            out.append("        if (id774 < 0 || id774 > BLOCK_ENTITY_VAULT_774) {")
            out.append("            logUnknownComposedOnce(\"774→769\", \"ブロックエンティティ種別\", id774);")
            out.append("            return DROP;")
            out.append("        }")
            out.append("        return mapBlockEntityType773To769(mapBlockEntityTypeUp774(id774));")
            out.append("    }")
            out.append("")
            out.append("    /**")
            out.append("     * 音の名前を774向けに変換する。769の音の名前のうち 770で削除された")
            out.append("     * entity.wolf.howl と 771で削除された leash_knot 系・")
            out.append("     * puffer_fish.ambient・block.sand.wind は代替の名前にする。")
            out.append("     * {@code null} は石の破壊音にする。")
            out.append("     */")
            out.append("    public static String mapSoundName769To774(String name) {")
            out.append("        return mapSoundNameComposed(name, SOUND_NAME_FALLBACK_769_TO_774_MAP);")
            out.append("    }")
            out.append("")
            out.append("    /**")
            out.append("     * 音の名前を769向けに変換する。774の音の名前のうち 769に無いものは")
            out.append("     * 代替の音の名前にする（数値の合成表と一致させる）。")
            out.append("     * {@code null} は石の破壊音にする。")
            out.append("     */")
            out.append("    public static String mapSoundName774To769(String name) {")
            out.append("        return mapSoundNameComposed(name, SOUND_NAME_FALLBACK_774_TO_769_MAP);")
            out.append("    }")
        elif src == "770":
            out.append("    /**")
            out.append("     * 770のエフェクトID → 774のエフェクトID。770も774も0ベース")
            out.append("     * （Speed は両方 0）で 0〜38 は同一のため恒等写像。")
            out.append("     * 範囲外は Speed（0）。")
            out.append("     */")
            out.append("    public static int mapEffect770To774(int id770) {")
            out.append("        if (id770 < 0 || id770 > MAX_EFFECT_770) {")
            out.append("            logUnknownComposedOnce(\"770→774\", \"エフェクト\", id770);")
            out.append("            return SPEED_EFFECT_774;")
            out.append("        }")
            out.append("        return id770;")
            out.append("    }")
            out.append("")
            out.append("    /**")
            out.append("     * 774のエフェクトID → 770のエフェクトID。0〜38 は同一、39（BreathOfTheNautilus）は Speed（0）へ。")
            out.append("     * 範囲外は Speed（0）。")
            out.append("     */")
            out.append("    public static int mapEffect774To770(int id774) {")
            out.append("        if (id774 < 0 || id774 > MAX_EFFECT_774) {")
            out.append("            logUnknownComposedOnce(\"774→770\", \"エフェクト\", id774);")
            out.append("            return SPEED_EFFECT_770;")
            out.append("        }")
            out.append("        return id774 <= MAX_EFFECT_770 ? id774 : SPEED_EFFECT_770;")
            out.append("    }")
            out.append("")
            out.append("    /**")
            out.append("     * 770のブロックエンティティ種別ID → 774の種別ID。")
            out.append("     * 770→773 と 773→774（恒等）の連鎖。")
            out.append("     * 範囲外は {@link #DROP}。")
            out.append("     */")
            out.append("    public static int mapBlockEntityType770To774(int id770) {")
            out.append("        if (id770 < 0 || id770 > BLOCK_ENTITY_TEST_INSTANCE_BLOCK_770) {")
            out.append("            logUnknownComposedOnce(\"770→774\", \"ブロックエンティティ種別\", id770);")
            out.append("            return DROP;")
            out.append("        }")
            out.append("        return mapBlockEntityType773To774(mapBlockEntityType770To773(id770));")
            out.append("    }")
            out.append("")
            out.append("    /**")
            out.append("     * 774のブロックエンティティ種別ID → 770の種別ID。")
            out.append("     * 774→773（恒等）と 773→770（棚・銅ゴーレム像は {@link #DROP}）の連鎖。")
            out.append("     * 範囲外は {@link #DROP}。")
            out.append("     */")
            out.append("    public static int mapBlockEntityType774To770(int id774) {")
            out.append("        if (id774 < 0 || id774 > BLOCK_ENTITY_TEST_INSTANCE_BLOCK_774) {")
            out.append("            logUnknownComposedOnce(\"774→770\", \"ブロックエンティティ種別\", id774);")
            out.append("            return DROP;")
            out.append("        }")
            out.append("        return mapBlockEntityType773To770(mapBlockEntityTypeUp774(id774));")
            out.append("    }")
            out.append("")
            out.append("    /**")
            out.append("     * 音の名前を774向けに変換する。770の音の名前のうち 771で削除された")
            out.append("     * entity.leash_knot.break／place・entity.puffer_fish.ambient・")
            out.append("     * block.sand.wind は代替の名前にする。")
            out.append("     * {@code null} は石の破壊音にする。")
            out.append("     */")
            out.append("    public static String mapSoundName770To774(String name) {")
            out.append("        return mapSoundNameComposed(name, SOUND_NAME_FALLBACK_770_TO_774_MAP);")
            out.append("    }")
            out.append("")
            out.append("    /**")
            out.append("     * 音の名前を770向けに変換する。774の音の名前のうち 770に無いものは")
            out.append("     * 代替の音の名前にする（数値の合成表と一致させる）。")
            out.append("     * {@code null} は石の破壊音にする。")
            out.append("     */")
            out.append("    public static String mapSoundName774To770(String name) {")
            out.append("        return mapSoundNameComposed(name, SOUND_NAME_FALLBACK_774_TO_770_MAP);")
            out.append("    }")
        elif src == "771":
            out.append("    /**")
            out.append("     * 771のエフェクトID → 774のエフェクトID。771も774も0ベース")
            out.append("     * （Speed は両方 0）で 0〜38 は同一のため恒等写像。")
            out.append("     * 範囲外は Speed（0）。")
            out.append("     */")
            out.append("    public static int mapEffect771To774(int id771) {")
            out.append("        if (id771 < 0 || id771 > MAX_EFFECT_771) {")
            out.append("            logUnknownComposedOnce(\"771→774\", \"エフェクト\", id771);")
            out.append("            return SPEED_EFFECT_774;")
            out.append("        }")
            out.append("        return id771;")
            out.append("    }")
            out.append("")
            out.append("    /**")
            out.append("     * 774のエフェクトID → 771のエフェクトID。0〜38 は同一、39")
            out.append("     * （BreathOfTheNautilus）は Speed（0）へ。範囲外は Speed（0）。")
            out.append("     */")
            out.append("    public static int mapEffect774To771(int id774) {")
            out.append("        if (id774 < 0 || id774 > MAX_EFFECT_774) {")
            out.append("            logUnknownComposedOnce(\"774→771\", \"エフェクト\", id774);")
            out.append("            return SPEED_EFFECT_771;")
            out.append("        }")
            out.append("        return id774 <= MAX_EFFECT_771 ? id774 : SPEED_EFFECT_771;")
            out.append("    }")
            out.append("")
            out.append("    /**")
            out.append("     * 771のブロックエンティティ種別ID → 774の種別ID。")
            out.append("     * 771→773（棚の挿入で 40 以降が +1）と 773→774（恒等）の連鎖。")
            out.append("     * 範囲外は {@link #DROP}。")
            out.append("     */")
            out.append("    public static int mapBlockEntityType771To774(int id771) {")
            out.append("        if (id771 < 0 || id771 > BLOCK_ENTITY_TEST_INSTANCE_BLOCK_771) {")
            out.append("            logUnknownComposedOnce(\"771→774\", \"ブロックエンティティ種別\", id771);")
            out.append("            return DROP;")
            out.append("        }")
            out.append("        return mapBlockEntityType773To774(mapBlockEntityType771To773(id771));")
            out.append("    }")
            out.append("")
            out.append("    /**")
            out.append("     * 774のブロックエンティティ種別ID → 771の種別ID。")
            out.append("     * 774→773（恒等）と 773→771（棚・銅ゴーレム像は {@link #DROP}）の連鎖。")
            out.append("     * 範囲外は {@link #DROP}。")
            out.append("     */")
            out.append("    public static int mapBlockEntityType774To771(int id774) {")
            out.append("        if (id774 < 0 || id774 > BLOCK_ENTITY_COPPER_GOLEM_STATUE_774) {")
            out.append("            logUnknownComposedOnce(\"774→771\", \"ブロックエンティティ種別\", id774);")
            out.append("            return DROP;")
            out.append("        }")
            out.append("        return mapBlockEntityType773To771(mapBlockEntityTypeUp774(id774));")
            out.append("    }")
            out.append("")
            out.append("    /**")
            out.append("     * 音の名前を774向けに変換する。771の音の名前は 774 にすべてあるため")
            out.append("     * 代替は無い。{@code null} は石の破壊音にする。")
            out.append("     */")
            out.append("    public static String mapSoundName771To774(String name) {")
            out.append("        return mapSoundNameComposed(name, SOUND_NAME_FALLBACK_771_TO_774_MAP);")
            out.append("    }")
            out.append("")
            out.append("    /**")
            out.append("     * 音の名前を771向けに変換する。774の音の名前のうち 771に無いものは")
            out.append("     * 代替の音の名前にする（数値の合成表と一致させる）。")
            out.append("     * {@code null} は石の破壊音にする。")
            out.append("     */")
            out.append("    public static String mapSoundName774To771(String name) {")
            out.append("        return mapSoundNameComposed(name, SOUND_NAME_FALLBACK_774_TO_771_MAP);")
            out.append("    }")
        elif src == "772":
            out.append("    /**")
            out.append("     * 772のエフェクトID → 774のエフェクトID。772も774も0ベース")
            out.append("     * （Speed は両方 0）で 0〜38 は同一のため恒等写像。")
            out.append("     * 範囲外は Speed（0）。")
            out.append("     */")
            out.append("    public static int mapEffect772To774(int id772) {")
            out.append("        if (id772 < 0 || id772 > MAX_EFFECT_772) {")
            out.append("            logUnknownComposedOnce(\"772→774\", \"エフェクト\", id772);")
            out.append("            return SPEED_EFFECT_774;")
            out.append("        }")
            out.append("        return id772;")
            out.append("    }")
            out.append("")
            out.append("    /**")
            out.append("     * 774のエフェクトID → 772のエフェクトID。0〜38 は同一、39（BreathOfTheNautilus）は Speed（0）へ。")
            out.append("     * 範囲外は Speed（0）。")
            out.append("     */")
            out.append("    public static int mapEffect774To772(int id774) {")
            out.append("        if (id774 < 0 || id774 > MAX_EFFECT_774) {")
            out.append("            logUnknownComposedOnce(\"774→772\", \"エフェクト\", id774);")
            out.append("            return SPEED_EFFECT_772;")
            out.append("        }")
            out.append("        return id774 <= MAX_EFFECT_772 ? id774 : SPEED_EFFECT_772;")
            out.append("    }")
            out.append("")
            out.append("    /**")
            out.append("     * 772のブロックエンティティ種別ID → 774の種別ID。")
            out.append("     * 772→773（棚の挿入で 40 以降が +1）と 773→774（恒等）の連鎖。")
            out.append("     * 範囲外は {@link #DROP}。")
            out.append("     */")
            out.append("    public static int mapBlockEntityType772To774(int id772) {")
            out.append("        if (id772 < 0 || id772 > BLOCK_ENTITY_TEST_INSTANCE_BLOCK_772) {")
            out.append("            logUnknownComposedOnce(\"772→774\", \"ブロックエンティティ種別\", id772);")
            out.append("            return DROP;")
            out.append("        }")
            out.append("        return mapBlockEntityType773To774(mapBlockEntityType772To773(id772));")
            out.append("    }")
            out.append("")
            out.append("    /**")
            out.append("     * 774のブロックエンティティ種別ID → 772の種別ID。")
            out.append("     * 774→773（恒等）と 773→772（Up773：棚・銅ゴーレム像は {@link #DROP}）の連鎖。")
            out.append("     * 範囲外は {@link #DROP}。")
            out.append("     */")
            out.append("    public static int mapBlockEntityType774To772(int id774) {")
            out.append("        if (id774 < 0 || id774 > BLOCK_ENTITY_COPPER_GOLEM_STATUE_774) {")
            out.append("            logUnknownComposedOnce(\"774→772\", \"ブロックエンティティ種別\", id774);")
            out.append("            return DROP;")
            out.append("        }")
            out.append("        return mapBlockEntityTypeUp773(mapBlockEntityTypeUp774(id774));")
            out.append("    }")
            out.append("")
            out.append("    /**")
            out.append("     * 音の名前を774向けに変換する。772の音の名前は 774 にすべてあるため")
            out.append("     * 代替は無い。{@code null} は石の破壊音にする。")
            out.append("     */")
            out.append("    public static String mapSoundName772To774(String name) {")
            out.append("        return mapSoundNameComposed(name, SOUND_NAME_FALLBACK_772_TO_774_MAP);")
            out.append("    }")
            out.append("")
            out.append("    /**")
            out.append("     * 音の名前を772向けに変換する。774の音の名前のうち 772に無いものは")
            out.append("     * 代替の音の名前にする（数値の合成表と一致させる）。")
            out.append("     * {@code null} は石の破壊音にする。")
            out.append("     */")
            out.append("    public static String mapSoundName774To772(String name) {")
            out.append("        return mapSoundNameComposed(name, SOUND_NAME_FALLBACK_774_TO_772_MAP);")
            out.append("    }")
    return "\n".join(out)

# バナー・絵画・装飾パターン・ダメージ種別の 774 向け（773 と同一のものは
# 恒等写像。ダメージ種別だけ spear（37）の挿入で 50 件）。
DAMAGE_TYPE_774 = """
    /** 774のダメージ種別最大ID（50件。773 の 49 件に spear が 37 に挿入。
        実サーバーの Registry Data での確認は統合テストで行う）。 */
    public static final int MAX_DAMAGE_TYPE_774 = 49;

    /** ダメージ種別ID：773 → 774（spear が 37 に挿入され 37 以降は +1 ずれる）。
     * 実サーバーの Registry Data での確認は統合テストで行う。 */
    private static final int[] DAMAGE_TYPE_774_MAP = {
        0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19,
        20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32, 33, 34, 35, 36,
        38, 39, 40, 41, 42, 43, 44, 45, 46, 47, 48, 49
    };

    /** ダメージ種別ID：774 → 773（spear（37）は DROP、38 以降は −1 ずれる）。 */
    private static final int[] DAMAGE_TYPE_REVERSE_774_MAP = {
        0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19,
        20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32, 33, 34, 35, 36,
        -1, 37, 38, 39, 40, 41, 42, 43, 44, 45, 46, 47, 48
    };
"""

SMALL_REGISTRY_CONSTANTS = """
    /** 774のバナーパターン最大ID（43件。773と同一と仮定。実バニラの
        Registry Data での確認は統合テストで行う）。 */
    public static final int MAX_BANNER_PATTERN_774 = 42;

    /** 774の絵画バリアント最大ID（51件。773と同一。実バニラの
        Registry Data での確認は統合テストで行う）。 */
    public static final int MAX_PAINTING_VARIANT_774 = 50;

    /** 774の装飾パターン最大ID（18件。773と同一と仮定。実バニラの
        Registry Data での確認は統合テストで行う）。 */
    public static final int MAX_TRIM_PATTERN_774 = 17;
"""


def small_registry_accessors():
    out = []
    specs = [
        ("BannerPattern", "バナーパターン", "MAX_BANNER_PATTERN_773", "MAX_BANNER_PATTERN_774"),
        ("PaintingVariant", "絵画バリアント", "MAX_PAINTING_VARIANT_773", "MAX_PAINTING_VARIANT_774"),
        ("TrimPattern", "装飾パターン", "MAX_TRIM_PATTERN_773", "MAX_TRIM_PATTERN_774"),
    ]
    for name, jp, max773, max774 in specs:
        out.append(f"    /** 773の{jp}ID → 774の{jp}ID。773と774で同一のため恒等写像。範囲外は {{@link #DROP}}。 */")
        out.append(f"    public static int map{name}773To774(int id773) {{")
        out.append(f"        if (id773 < 0 || id773 > {max773}) {{")
        out.append(f"            logUnknownOnce(PROTOCOL_774, \"{jp}\", id773);")
        out.append("            return DROP;")
        out.append("        }")
        out.append("        return id773;")
        out.append("    }")
        out.append("")
        out.append(f"    /** 774の{jp}ID → 773の{jp}ID。773と774で同一のため恒等写像。範囲外は {{@link #DROP}}。 */")
        out.append(f"    public static int map{name}774To773(int id774) {{")
        out.append(f"        if (id774 < 0 || id774 > {max774}) {{")
        out.append(f"            logUnknownOnce(PROTOCOL_774, \"{jp}\", id774);")
        out.append("            return DROP;")
        out.append("        }")
        out.append("        return id774;")
        out.append("    }")
        out.append("")
        for src in ("763", "764", "765", "766", "767", "768", "769", "770", "771", "772"):
            # 絵画バリアントだけ 771 を経由する（763／764／765／766／767／768／769／770→774 と
            # 774→763／764／765／766／767／768／769／770 は無い。773→774 は隣接で既にある）。
            # 772 経由は使えない（{src}→772 の絵画メソッドが 772 の代で作られていないため）。
            # 771 だけは 772 経由（771→772 が存在する）。772 は 773 経由。
            # バナー・装飾パターンは 773 を経由する（{src}→773 は既にある）。
            if name == "PaintingVariant":
                hub = "772" if src == "771" else ("773" if src == "772" else "771")
            else:
                hub = "773"
            out.append(f"    /** {src}の{jp}ID → 774の{jp}ID（{hub}経由）。 */")
            out.append(f"    public static int map{name}{src}To774(int id{src}) {{")
            out.append(f"        return map{name}{hub}To774(map{name}{src}To{hub}(id{src}));")
            out.append("    }")
            out.append("")
            out.append(f"    /** 774の{jp}ID → {src}の{jp}ID（{hub}経由）。 */")
            out.append(f"    public static int map{name}774To{src}(int id774) {{")
            out.append(f"        return map{name}{hub}To{src}(map{name}774To{hub}(id774));")
            out.append("    }")
            out.append("")
    return "\n".join(out)


def jukebox_accessors():
    """ジュークボックスの曲の 774 アクセサ（隣接のみ。773 と同じ作り）。

    773 と同じく合成表は作らない（763〜771→773 の合成メソッドが無いため）。
    曲は 773 と 774 で 21 件・同じ並びのため恒等写像。名前の表は
    JUKEBOX_SONG_774_NAMES を作らず 773 の表をそのまま使う（Stage 2c で
    gen774.txt に JUKEBOX_SONG_774_NAMES が無いことを確認済み）。
    """
    out = []
    out.append("    /** 773のジュークボックスの曲ID → 774の曲ID。773と774は21件・同じ並びのため恒等写像。範囲外は {@link #DROP}。 */")
    out.append("    public static int mapJukeboxSong773To774(int id773) {")
    out.append("        if (id773 < 0 || id773 >= JUKEBOX_SONG_773_NAMES.length) {")
    out.append("            logUnknownOnce(PROTOCOL_774, \"ジュークボックスの曲\", id773);")
    out.append("            return DROP;")
    out.append("        }")
    out.append("        return id773;")
    out.append("    }")
    out.append("")
    out.append("    /** 774のジュークボックスの曲ID → 773の曲ID。773と774は21件・同じ並びのため恒等写像。範囲外は {@link #DROP}。 */")
    out.append("    public static int mapJukeboxSong774To773(int id774) {")
    out.append("        if (id774 < 0 || id774 >= JUKEBOX_SONG_773_NAMES.length) {")
    out.append("            logUnknownOnce(PROTOCOL_774, \"ジュークボックスの曲\", id774);")
    out.append("            return DROP;")
    out.append("        }")
    out.append("        return id774;")
    out.append("    }")
    out.append("")
    out.append("    /** 774のジュークボックスの曲ID → 曲の名前（jukebox_playable の名前指定用）。773と同一のため 773 の表を使う。 */")
    out.append("    public static String jukeboxSongName774(int id774) {")
    out.append("        if (id774 < 0 || id774 >= JUKEBOX_SONG_773_NAMES.length) {")
    out.append("            return null;")
    out.append("        }")
    out.append("        return JUKEBOX_SONG_773_NAMES[id774];")
    out.append("    }")
    return "\n".join(out)


def damage_accessors():
    out = []
    out.append("    /** 773のダメージ種別ID → 774のダメージ種別ID。spear（37）が新たに挿入され 37 以降は +1 ずれる。範囲外は {@link #DROP}。 */")
    out.append("    public static int mapDamageType773To774(int id773) {")
    out.append("        if (id773 < 0 || id773 > MAX_DAMAGE_TYPE_773) {")
    out.append("            logUnknownOnce(PROTOCOL_774, \"ダメージ種別\", id773);")
    out.append("            return DROP;")
    out.append("        }")
    out.append("        return DAMAGE_TYPE_774_MAP[id773];")
    out.append("    }")
    out.append("")
    out.append("    /** 774のダメージ種別ID → 773のダメージ種別ID。spear（37）は DROP、38 以降は −1 ずれる。範囲外は {@link #DROP}。 */")
    out.append("    public static int mapDamageType774To773(int id774) {")
    out.append("        if (id774 < 0 || id774 > MAX_DAMAGE_TYPE_774) {")
    out.append("            logUnknownOnce(PROTOCOL_774, \"ダメージ種別\", id774);")
    out.append("            return DROP;")
    out.append("        }")
    out.append("        return DAMAGE_TYPE_REVERSE_774_MAP[id774];")
    out.append("    }")
    out.append("")
    for src in ("763", "764", "765", "766", "767", "768", "769", "770", "771", "772"):
        # ダメージ種別は 773 のメソッドを経由する。
        out.append(f"    /** {src}のダメージ種別ID → 774のダメージ種別ID（773経由）。 */")
        out.append(f"    public static int mapDamageType{src}To774(int id{src}) {{")
        out.append(f"        return mapDamageType773To774(mapDamageType{src}To773(id{src}));")
        out.append("    }")
        out.append("")
        out.append(f"    /** 774のダメージ種別ID → {src}のダメージ種別ID（773経由）。 */")
        out.append(f"    public static int mapDamageType774To{src}(int id774) {{")
        out.append(f"        return mapDamageType773To{src}(mapDamageType774To773(id774));")
        out.append("    }")
        out.append("")
    return "\n".join(out)


JAVADOC_774 = """ *
 * <p>774（1.21.11）向けの表も同じ作り方で持つ（{@code *_774}）。
 * 774は773に近く、差分は次の通り（ViaVersion mapping-1.21.11.json と
 * mapping-1.21.9.json、minecraft-data 1.21.11 の突き合わせ）。
 * ブロック状態は同一（29,671件）。ブロックは同一（1,166件）。
 * アイテムは新17件・削除0件（スポーンエッグ4件・ネザライトの馬鎧・
 * 槍7件・ノーチラスの防具5件）。エンティティは新4件（camel_husk・
 * nautilus・parched・zombie_nautilus）。音は新67件・削除0件。
 * パーティクルは同一（115件）。ブロックエンティティ種別は同一（49件）。
 * エフェクトは新1件（BreathOfTheNautilus が 39 に追加）。
 * エンチャントは新1件（lunge が 21 に挿入）。ダメージ種別は新1件
 * （spear が 37 に挿入）。データコンポーネントは 96→104（新8件）。
 * メニュー・バイオーム・属性は同一。絵画バリアント・ジュークボックスの歌・
 * バナーパターン・装飾パターンも 773 と同一（実バニラの Registry Data
 * での確認は統合テストで行う）。
 * 773→774 の隣接表と 763／764／765／766／767／768／769／770／771／772→774
 * の合成表を持ち、逆向き 774→773 は反転して作る。
"""


def insert_after(java, anchor, block, label):
    if anchor not in java:
        print(f"アンカーが見つからない（{label}）", file=sys.stderr)
        return None
    return java.replace(anchor, anchor + block, 1)


def main():
    if (not TABLES774_FILE.exists() or not TABLES774_COMPOSED_FILE.exists()
            or not all(p.exists() for p in TABLES774_COMPOSED_DOWN_FILES.values())):
        print("RegistryTables774.java / RegistryTables774ComposedUp.java / "
              "RegistryTables774Down*.java が無い。"
              "先に insert-774-tables.py を実行すること", file=sys.stderr)
        return 1
    java = JAVA_FILE.read_text(encoding="utf-8")
    checks = [
        ("PROTOCOL_774", "774 の定数・表・アクセサは既にある"),
        ("BLOCK_STATE_774_MAP", "774 の展開済み表は既にある"),
        ("mapBlockState773To774", "774 のアクセサは既にある"),
    ]
    for marker, msg in checks:
        if marker in java:
            print(msg, file=sys.stderr)
            return 1

    # import static。
    java = insert_after(java,
                        "import static io.detergentmc.protocol.RegistryTables773Down770.*;\n",
                        "import static io.detergentmc.protocol.RegistryTables774.*;\n"
                        "import static io.detergentmc.protocol.RegistryTables774ComposedUp.*;\n"
                        "import static io.detergentmc.protocol.RegistryTables774Down763.*;\n"
                        "import static io.detergentmc.protocol.RegistryTables774Down764.*;\n"
                        "import static io.detergentmc.protocol.RegistryTables774Down765.*;\n"
                        "import static io.detergentmc.protocol.RegistryTables774Down766.*;\n"
                        "import static io.detergentmc.protocol.RegistryTables774Down767.*;\n"
                        "import static io.detergentmc.protocol.RegistryTables774Down768.*;\n"
                        "import static io.detergentmc.protocol.RegistryTables774Down769.*;\n"
                        "import static io.detergentmc.protocol.RegistryTables774Down770.*;\n"
                        "import static io.detergentmc.protocol.RegistryTables774Down771.*;\n"
                        "import static io.detergentmc.protocol.RegistryTables774Down772.*;\n",
                        "import static RegistryTables774")
    if java is None:
        return 1

    # クラスの javadoc に 774 の段落を追加（773 の段落の直後）。
    anchor = (" * 772→773 の隣接表と 763／764／765／766／767／768／769／770／771→773 の合成表を持ち、\n"
              " * 逆向き 773→772 は反転して作る。\n")
    java = insert_after(java, anchor, JAVADOC_774, "javadoc")
    if java is None:
        return 1

    # 定数。
    java = insert_after(java,
                        "    public static final int PROTOCOL_773 = 773;\n",
                        "    public static final int PROTOCOL_774 = 774;\n", "PROTOCOL_774")
    if java is None:
        return 1
    java = insert_after(java,
                        "    public static final int MAX_EFFECT_773 = 38;\n",
                        CONSTANTS_MAX, "MAX_774")
    if java is None:
        return 1
    java = insert_after(java,
                        "    public static final int SPEED_EFFECT_773 = 0;\n",
                        CONSTANTS_FALLBACK, "774 の代替先")
    if java is None:
        return 1
    java = insert_after(java,
                        "    public static final int BLOCK_ENTITY_VAULT_773 = 45;\n",
                        CONSTANTS_BLOCK_ENTITY, "774 のブロックエンティティ種別")
    if java is None:
        return 1

    # 隣接表（773→774）。MENU_773_MAP の直後。
    anchor = ("    private static final int[] MENU_771_MAP =\n"
              "            expand(MENU_771_RANGES, MENU_771_OVERRIDES, MAX_MENU_770 + 1,\n"
              "                    CRAFTING_MENU_771);\n")
    java = insert_after(java, anchor, adjacent_maps() + "\n", "隣接表 774")
    if java is None:
        return 1

    # 合成表。MENU_772_TO_769_MAP の直後。
    anchor = ("    private static final int[] MENU_771_TO_769_MAP =\n"
              "            expand(MENU_771_TO_769_RANGES, MENU_771_TO_769_OVERRIDES,\n"
              "                    25, CRAFTING_MENU_769);\n")
    java = insert_after(java, anchor, "\n" + composed_maps() + "\n", "合成表 774")
    if java is None:
        return 1

    # 反転表（774→773）。MENU_UP_773_MAP の直後。
    anchor = ("    private static final int[] MENU_UP_771_MAP = invert(\n"
              "            MENU_771_RANGES, MENU_771_OVERRIDES,\n"
              "            MAX_MENU_770 + 1, MAX_MENU_771 + 1,\n"
              "            CRAFTING_MENU_770, null);\n")
    java = insert_after(java, anchor, up_maps() + "\n", "反転表 774")
    if java is None:
        return 1

    # 音の名前の表。
    anchor = ("    private static final Map<String, String> SOUND_NAME_FALLBACK_766_TO_768_MAP =\n"
              "            buildSoundNameMap(SOUND_NAME_FALLBACKS_766_TO_768);\n")
    java = insert_after(java, anchor, SOUND_NAME_MAPS, "音の名前の表 774")
    if java is None:
        return 1

    # ログ。
    anchor = ("    private static void logUnknownUp773Once(String kind, int id773) {\n"
              "        if (LOGGED_UNKNOWN.add(\"up773:\" + kind + \":\" + id773)) {\n"
              "            LOG.warning(\"773に無い\" + kind + \"の番号が来たため代替に落とします（773 id=\" + id773 + \"）。\");\n"
              "        }\n"
              "    }\n")
    java = insert_after(java, anchor, LOG_UP_774, "logUnknownUp774Once")
    if java is None:
        return 1

    # アクセサ（a）773→774。mapEnchantment773To764 の直後（773 の隣接
    # アクセサの最後。mapSoundName767To773 は合成アクセサの途中にあるため
    # アンカーに使えない）。
    anchor = ("    public static int mapEnchantment773To764(int id773) {\n"
              "        return mapEnchantment773To765(id773);\n"
              "    }\n")
    java = insert_after(java, anchor, "\n" + adjacent_accessors() + "\n", "アクセサ 773→774")
    if java is None:
        return 1

    # アクセサ（b）774→773。mapSoundNameUp773 の直後。
    anchor = ("    public static String mapSoundNameUp773(String name) {\n"
              "        return mapSoundNameComposed(name, SOUND_NAME_FALLBACK_773_TO_772_MAP);\n"
              "    }\n")
    java = insert_after(java, anchor, "\n" + up_accessors() + "\n", "アクセサ 774→773")
    if java is None:
        return 1

    # アクセサ（c）763／764／765／766／767／768／769／770／771／772↔774。mapSoundName773To766 の直後。
    anchor = ("    public static String mapSoundName773To766(String name) {\n"
              "        return mapSoundNameComposed(name, SOUND_NAME_FALLBACK_773_TO_766_MAP);\n"
              "    }\n")
    java = insert_after(java, anchor, "\n" + composed_accessors() + "\n", "アクセサ 763/764/765/766/767↔774")
    if java is None:
        return 1

    # バナー・絵画・装飾パターン・ダメージ種別の定数。
    java = insert_after(java,
                        "    /** 770のバナーパターン最大ID（43件。769と同一と仮定。実バニラの\n"
                        "        Registry Data での確認は統合テストで行う）。 */\n"
                        "    public static final int MAX_BANNER_PATTERN_770 = 42;\n",
                        SMALL_REGISTRY_CONSTANTS, "バナー等の774定数")
    if java is None:
        return 1
    anchor = ("    private static final int[] DAMAGE_TYPE_REVERSE_773_MAP = {\n"
              "        0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19,\n"
              "        20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32, 33, 34, 35, 36,\n"
              "        37, 38, 39, 40, 41, 42, 43, 44, 45, 46, 47, 48\n"
              "    };\n")
    java = insert_after(java, anchor, DAMAGE_TYPE_774, "ダメージ種別774の表")
    if java is None:
        return 1

    # バナー・絵画・装飾パターン・ダメージ種別のアクセサ。
    anchor = ("    public static int mapBannerPattern773To771(int id773) {\n"
              "        return mapBannerPattern772To771(mapBannerPattern773To772(id773));\n"
              "    }\n")
    banner_painting_trim = small_registry_accessors()
    java = insert_after(java, anchor, "\n" + banner_painting_trim + "\n", "バナー等の774アクセサ")
    if java is None:
        return 1
    anchor = ("    public static int mapDamageType773To771(int id773) {\n"
              "        return mapDamageType772To771(mapDamageType773To772(id773));\n"
              "    }\n")
    anchor = ("    /** 773のジュークボックスの曲ID → 曲の名前（jukebox_playable の名前指定用）。 */\n"
              "    public static String jukeboxSongName773(int id773) {\n"
              "        if (id773 < 0 || id773 >= JUKEBOX_SONG_773_NAMES.length) {\n"
              "            return null;\n"
              "        }\n"
              "        return JUKEBOX_SONG_773_NAMES[id773];\n"
              "    }\n")
    java = insert_after(java, anchor, "\n" + jukebox_accessors() + "\n", "ジュークボックス774のアクセサ")
    if java is None:
        return 1
    java = insert_after(java, anchor, "\n" + damage_accessors() + "\n", "ダメージ種別774のアクセサ")
    if java is None:
        return 1

    # プロトコル番号で切り替える変換（担当2の配線用）に 774 を追加。
    dispatches = [
        ("mapBlockState", "mapBlockState765To773(id765)", "mapBlockState765To774(id765)"),
        ("mapItem", "mapItem765To773(id765)", "mapItem765To774(id765)"),
        ("mapItemReverse", "mapItem773To765(id)", "mapItem774To765(id)"),
        ("mapEntityType", "mapEntityType765To773(id765)", "mapEntityType765To774(id765)"),
        ("mapSound", "mapSound765To773(id765)", "mapSound765To774(id765)"),
        ("mapParticle", "mapParticle765To773(id765)", "mapParticle765To774(id765)"),
        ("mapBlock", "mapBlock765To773(id765)", "mapBlock765To774(id765)"),
        ("mapMenu", "mapMenu765To773(id765)", "mapMenu765To774(id765)"),
        ("mapEffect", "mapEffect765To773(id765)", "mapEffect765To774(id765)"),
        ("mapBlockEntityType", "mapBlockEntityType765To773(id765)", "mapBlockEntityType765To774(id765)"),
        ("mapSoundName", "mapSoundName765To773(name)", "mapSoundName765To774(name)"),
    ]
    for label, old_call, new_call in dispatches:
        if label == "mapItemReverse":
            old = ("        if (clientProtocol == PROTOCOL_773) {\n"
                   "            // 773クライアントのアイテムID → 765サーバーのアイテムID。\n"
                   "            // mapItemReverse773 は 773→772 のため、ここでは合成表を使う。\n"
                   "            return mapItem773To765(id);\n"
                   "        }\n")
            new = (old
                   + "        if (clientProtocol == PROTOCOL_774) {\n"
                   "            // 774クライアントのアイテムID → 765サーバーのアイテムID。\n"
                   "            // mapItemReverse774 は 774→773 のため、ここでは合成表を使う。\n"
                   "            return mapItem774To765(id);\n"
                   "        }\n")
            if old not in java:
                print("ディスパッチのアンカーが見つからない（mapItemReverse）", file=sys.stderr)
                return 1
            java = java.replace(old, new, 1)
            continue
        old = ("        if (clientProtocol == PROTOCOL_773) {\n"
               f"            return {old_call};\n"
               "        }\n")
        if old not in java:
            print(f"ディスパッチのアンカーが見つからない（{label}）", file=sys.stderr)
            return 1
        java = java.replace(old, old
                            + "        if (clientProtocol == PROTOCOL_774) {\n"
                            + f"            return {new_call};\n"
                            + "        }\n", 1)
    java = java.replace(
        "    // 765は素通し、766は766の表、767は767の表、768は768の表、\n"
        "    // 769は769の表、770は770の表、771は771の表、772は772の表、773は773の表、それより古い未知の番号は763の表を使う。\n"
        "    // 767／768／769／770／771／772／773は765以上だが素通しではない（>765 の分岐より\n"
        "    // 先に 767／768／769／770／771／772／773 を判定すること）。",
        "    // 765は素通し、766は766の表、767は767の表、768は768の表、\n"
        "    // 769は769の表、770は770の表、771は771の表、772は772の表、773は773の表、774は774の表、それより古い未知の番号は763の表を使う。\n"
        "    // 767／768／769／770／771／772／773／774は765以上だが素通しではない（>765 の分岐より\n"
        "    // 先に 767／768／769／770／771／772／773／774 を判定すること）。",
        1)

    JAVA_FILE.write_text(java, encoding="utf-8")
    print("RegistryRemapper.java に 774 の定数・表・アクセサを挿入した")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
