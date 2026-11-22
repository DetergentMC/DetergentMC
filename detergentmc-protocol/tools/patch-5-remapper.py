"""RegistryRemapper.java に 5（1.7.6〜1.7.10）の節を追加する。

表は gen-5-tables.py が生成した RegistryTables5 を使う。
挿入位置は 47 の節の直後（定数・表フィールド・音フォールバック・
写像関数の4箇所＋ static import と PROTOCOL_5 定数）。
冪等ではない。git diff で確認すること。
"""

import sys
from pathlib import Path

SCRIPT_DIR = Path(__file__).resolve().parent
REMAPPER = (SCRIPT_DIR.parent / "src" / "main" / "java" / "io"
            / "detergentmc" / "protocol" / "RegistryRemapper.java")


def main():
    src = REMAPPER.read_text(encoding="utf-8")
    assert "PROTOCOL_5 " not in src and "RegistryTables5." not in src, \
        "既に 5 の節がある"

    # 1) static import
    src = src.replace(
        "import static io.detergentmc.protocol.RegistryTables47.*;",
        "import static io.detergentmc.protocol.RegistryTables47.*;\n"
        "import static io.detergentmc.protocol.RegistryTables5.*;", 1)

    # 2) PROTOCOL_5 定数
    src = src.replace(
        """    /** 47（1.8〜1.8.9）のプロトコル番号。 */
    public static final int PROTOCOL_47 = 47;""",
        """    /** 47（1.8〜1.8.9）のプロトコル番号。 */
    public static final int PROTOCOL_47 = 47;

    /** 5（1.7.6〜1.7.10）のプロトコル番号。 */
    public static final int PROTOCOL_5 = 5;""", 1)

    # 3) 定数ブロック（393 の定数の直前＝47 の定数の直後）
    constants = """    /** 5側のブロック状態最大値（47 と同一の (数値ID<<4)|メタデータ空間）。 */
    public static final int MAX_BLOCK_STATE_5 = 4095;
    /** 5側のアイテム最大ID（47 と同一の疎な番号。最大は 2267）。 */
    public static final int MAX_ITEM_5 = 2267;
    /** 5側のエンティティ種別最大ID（表の大きさは 47 と同一）。 */
    public static final int MAX_ENTITY_TYPE_5 = 200;
    /** 5側のパーティクル最大ID（番号空間は 47 と同一。実在は 41 まで）。 */
    public static final int MAX_PARTICLE_5 = 45;
    /** 5側のブロック最大ID（実在は 175 まで）。 */
    public static final int MAX_BLOCK_5 = 255;
    /** 5側のバイオーム最大ID（47 と同一）。 */
    public static final int MAX_BIOME_5 = 167;
    /** 5側の石ブロック状態。 */
    public static final int STONE_BLOCK_STATE_5 = 16; // 石 (1<<4)|0
    /** 5側の空気ブロック状態。 */
    public static final int AIR_BLOCK_STATE_5 = 0;
    /** 5側の石アイテムID。 */
    public static final int STONE_ITEM_5 = 1;
    /** 5側のブタのエンティティ種別ID。 */
    public static final int PIG_ENTITY_TYPE_5 = 90;
    /** 5側の ambient パーティクル代替（explode）。 */
    public static final int AMBIENT_PARTICLE_5 = 0; // explode
    /** 5側の石ブロックID。 */
    public static final int STONE_BLOCK_5 = 1;
    /** 5側の平原バイオームID。 */
    public static final int PLAINS_BIOME_5 = 1;

"""
    anchor = """    /**
     * 393（1.13）側の最大の番号。"""
    assert anchor in src
    src = src.replace(anchor, constants + anchor, 1)

    # 4) 表フィールド（47 のバイオーム逆表の直後、393 の表の直前）
    fields = """    // ---- 5（1.7.6〜1.7.10）：RegistryTables5 の専用表 ----
    // 表の初期化はビルダーメソッド経由にする（RegistryRemapper の
    // <clinit> が 64KB 上限に近いため、expand 呼び出しを逃がす）。
    /** 758のブロック状態ID → 5の (数値ID<<4)|メタデータ（RegistryTables5）。 */
    private static final int[] BLOCK_STATE_758_TO_5_MAP = buildBlockState758To5Map();
    /** 5の (数値ID<<4)|メタデータ → 758のブロック状態ID（RegistryTables5）。 */
    private static final int[] BLOCK_STATE_5_TO_758_MAP = buildBlockState5To758Map();
    /** 758のアイテムID → 5のアイテムID。 */
    private static final int[] ITEM_758_TO_5_MAP = buildItem758To5Map();
    /** 5のアイテムID → 758のアイテムID。 */
    private static final int[] ITEM_5_TO_758_MAP = buildItem5To758Map();
    /** 758のエンティティ種別ID → 5のエンティティ種別ID。 */
    private static final int[] ENTITY_TYPE_758_TO_5_MAP = buildEntityType758To5Map();
    /** 5のエンティティ種別ID → 758のエンティティ種別ID。 */
    private static final int[] ENTITY_TYPE_5_TO_758_MAP = buildEntityType5To758Map();
    /** 758のパーティクルID → 5のパーティクルID（番号は 47 と共通）。 */
    private static final int[] PARTICLE_758_TO_5_MAP = buildParticle758To5Map();
    /** 5のパーティクルID → 758のパーティクルID。 */
    private static final int[] PARTICLE_5_TO_758_MAP = buildParticle5To758Map();
    /** 758のブロックID → 5のブロック数値ID。 */
    private static final int[] BLOCK_758_TO_5_MAP = buildBlock758To5Map();
    /** 5のブロック数値ID → 758のブロックID。 */
    private static final int[] BLOCK_5_TO_758_MAP = buildBlock5To758Map();
    /** 758のバイオームID → 5のバイオームID。 */
    private static final int[] BIOME_758_TO_5_MAP = buildBiome758To5Map();
    /** 5のバイオームID → 758のバイオームID。 */
    private static final int[] BIOME_5_TO_758_MAP = buildBiome5To758Map();

    private static int[] buildBlockState758To5Map() {
        return expand(BLOCK_STATE_5_RANGES, BLOCK_STATE_5_OVERRIDES, MAX_BLOCK_STATE_758 + 1,
                STONE_BLOCK_STATE_5);
    }

    private static int[] buildBlockState5To758Map() {
        return expand(BLOCK_STATE_5_TO_758_RANGES, BLOCK_STATE_5_TO_758_OVERRIDES,
                MAX_BLOCK_STATE_5 + 1, STONE_BLOCK_STATE_758);
    }

    private static int[] buildItem758To5Map() {
        return expand(ITEM_5_RANGES, ITEM_5_OVERRIDES, MAX_ITEM_758 + 1, STONE_ITEM_5);
    }

    private static int[] buildItem5To758Map() {
        return expand(ITEM_5_TO_758_RANGES, ITEM_5_TO_758_OVERRIDES, MAX_ITEM_5 + 1,
                STONE_ITEM_758);
    }

    private static int[] buildEntityType758To5Map() {
        return expand(ENTITY_TYPE_5_RANGES, ENTITY_TYPE_5_OVERRIDES, MAX_ENTITY_TYPE_758 + 1,
                PIG_ENTITY_TYPE_5);
    }

    private static int[] buildEntityType5To758Map() {
        return expand(ENTITY_TYPE_5_TO_758_RANGES, ENTITY_TYPE_5_TO_758_OVERRIDES,
                MAX_ENTITY_TYPE_5 + 1, PIG_ENTITY_TYPE_758);
    }

    private static int[] buildParticle758To5Map() {
        return expand(PARTICLE_5_RANGES, PARTICLE_5_OVERRIDES, MAX_PARTICLE_758 + 1,
                AMBIENT_PARTICLE_5);
    }

    private static int[] buildParticle5To758Map() {
        return expand(PARTICLE_5_TO_758_RANGES, PARTICLE_5_TO_758_OVERRIDES,
                MAX_PARTICLE_5 + 1, AMBIENT_PARTICLE_758);
    }

    private static int[] buildBlock758To5Map() {
        return expand(BLOCK_5_RANGES, BLOCK_5_OVERRIDES, MAX_BLOCK_758 + 1, STONE_BLOCK_5);
    }

    private static int[] buildBlock5To758Map() {
        return expand(BLOCK_5_TO_758_RANGES, BLOCK_5_TO_758_OVERRIDES, MAX_BLOCK_5 + 1,
                STONE_BLOCK_758);
    }

    private static int[] buildBiome758To5Map() {
        return expand(BIOME_5_RANGES, BIOME_5_OVERRIDES, MAX_BIOME_758 + 1, PLAINS_BIOME_5);
    }

    private static int[] buildBiome5To758Map() {
        return expand(BIOME_5_TO_758_RANGES, BIOME_5_TO_758_OVERRIDES, MAX_BIOME_5 + 1,
                PLAINS_BIOME_758);
    }
"""
    anchor2 = """    /** 758のブロック状態ID → 393のブロック状態ID（RegistryTables393）。 */"""
    assert anchor2 in src
    src = src.replace(anchor2, fields + anchor2, 1)

    # 5) 音フォールバックの Map フィールド
    snd_fields = """
    /** 758の音の名前 → 5の音の名前（Stop Sound 用）。 */
    private static final Map<String, String> SOUND_NAME_FALLBACK_758_TO_5_MAP =
            buildSoundNameMap(SOUND_NAME_FALLBACKS_758_TO_5);

    /** 5の音の名前 → 758の音の名前（Stop Sound 用）。 */
    private static final Map<String, String> SOUND_NAME_FALLBACK_5_TO_758_MAP =
            buildSoundNameMap(SOUND_NAME_FALLBACKS_5_TO_758);
"""
    anchor3 = """    /** 47の音の名前 → 758の音の名前（Stop Sound 用）。 */
    private static final Map<String, String> SOUND_NAME_FALLBACK_47_TO_758_MAP =
            buildSoundNameMap(SOUND_NAME_FALLBACKS_47_TO_758);
"""
    assert anchor3 in src
    src = src.replace(anchor3, anchor3 + snd_fields, 1)

    # 6) 写像関数（47 の関数の直後、393 の節の直前）
    functions = """
    // 5 ⇔ 758（1.7.6〜1.7.10 ⇔ 1.18.2）
    // 5 のレジストリは 47 の完成済み表を土台に、minecraft-data
    // pc/1.7 ⇔ pc/1.8 の名前突き合わせで合成した（共有名の数値 ID
    // 差分は 0、1.8 追加分は近似）。生成と検証は
    // tools/gen-5-tables.py／tools/verify-5-tables.py。
    // 音は 47 と同じく名前だけ（数値の Sound Effect が無い）。
    // ブロックエンティティとダメージ種別は 5 のワイヤに数値 ID が
    // 現れないため 110 の写像へ委譲する。メニューは 5 に
    // レジストリが無いため写像を作らない（47 と同じ）。

    /** 758のブロック状態ID → 5の (数値ID<<4)|メタデータ。範囲外は石。 */
    public static int mapBlockState758To5(int id758) {
        if (id758 < 0 || id758 >= BLOCK_STATE_758_TO_5_MAP.length) {
            logUnknownComposedOnce("758→5", "ブロック状態", id758);
            return STONE_BLOCK_STATE_5;
        }
        return BLOCK_STATE_758_TO_5_MAP[id758];
    }

    /** 5の (数値ID<<4)|メタデータ → 758のブロック状態ID。範囲外は石。 */
    public static int mapBlockState5To758(int value5) {
        if (value5 < 0 || value5 >= BLOCK_STATE_5_TO_758_MAP.length) {
            logUnknownComposedOnce("5→758", "ブロック状態", value5);
            return STONE_BLOCK_STATE_758;
        }
        return BLOCK_STATE_5_TO_758_MAP[value5];
    }

    /** 758のアイテムID → 5のアイテムID。範囲外は石アイテム。 */
    public static int mapItem758To5(int id758) {
        if (id758 < 0 || id758 >= ITEM_758_TO_5_MAP.length) {
            logUnknownComposedOnce("758→5", "アイテム", id758);
            return STONE_ITEM_5;
        }
        return ITEM_758_TO_5_MAP[id758];
    }

    /** 5のアイテムID → 758のアイテムID。範囲外は石アイテム。 */
    public static int mapItem5To758(int id5) {
        if (id5 < 0 || id5 >= ITEM_5_TO_758_MAP.length) {
            logUnknownComposedOnce("5→758", "アイテム", id5);
            return STONE_ITEM_758;
        }
        return ITEM_5_TO_758_MAP[id5];
    }

    /** 758のエンティティ種別ID → 5のエンティティ種別ID。範囲外はブタ。 */
    public static int mapEntityType758To5(int id758) {
        if (id758 < 0 || id758 >= ENTITY_TYPE_758_TO_5_MAP.length) {
            logUnknownComposedOnce("758→5", "エンティティ種別", id758);
            return PIG_ENTITY_TYPE_5;
        }
        return ENTITY_TYPE_758_TO_5_MAP[id758];
    }

    /** 5のエンティティ種別ID → 758のエンティティ種別ID。範囲外はブタ。 */
    public static int mapEntityType5To758(int id5) {
        if (id5 < 0 || id5 >= ENTITY_TYPE_5_TO_758_MAP.length) {
            logUnknownComposedOnce("5→758", "エンティティ種別", id5);
            return PIG_ENTITY_TYPE_758;
        }
        return ENTITY_TYPE_5_TO_758_MAP[id5];
    }

    /** 758のパーティクルID → 5のパーティクルID（番号は 47 と共通）。範囲外は explode。 */
    public static int mapParticle758To5(int id758) {
        if (id758 < 0 || id758 >= PARTICLE_758_TO_5_MAP.length) {
            logUnknownComposedOnce("758→5", "パーティクル", id758);
            return AMBIENT_PARTICLE_5;
        }
        return PARTICLE_758_TO_5_MAP[id758];
    }

    /** 5のパーティクルID → 758のパーティクルID。範囲外は ambient 代替。 */
    public static int mapParticle5To758(int id5) {
        if (id5 < 0 || id5 >= PARTICLE_5_TO_758_MAP.length) {
            logUnknownComposedOnce("5→758", "パーティクル", id5);
            return AMBIENT_PARTICLE_758;
        }
        return PARTICLE_5_TO_758_MAP[id5];
    }

    /** 5のパーティクルID（47 と共通の番号）→ 1.7 のパーティクル名。無い場合は null。 */
    public static String particleName5(int id5) {
        if (id5 < 0 || id5 >= RegistryTables5.PARTICLE_NAMES_5.length) {
            return null;
        }
        String name = RegistryTables5.PARTICLE_NAMES_5[id5];
        return name.isEmpty() ? null : name;
    }

    /** 1.7 のパーティクル名 → パーティクルID（47 と共通の番号）。無い場合は -1。 */
    public static int particleId5ForName(String name) {
        String[] names = RegistryTables5.PARTICLE_NAMES_5;
        for (int i = 0; i < names.length; i++) {
            if (names[i].equals(name)) {
                return i;
            }
        }
        return -1;
    }

    /** 758のブロックID → 5のブロック数値ID。範囲外は石。 */
    public static int mapBlock758To5(int id758) {
        if (id758 < 0 || id758 >= BLOCK_758_TO_5_MAP.length) {
            logUnknownComposedOnce("758→5", "ブロック", id758);
            return STONE_BLOCK_5;
        }
        return BLOCK_758_TO_5_MAP[id758];
    }

    /** 5のブロック数値ID → 758のブロックID。範囲外は石。 */
    public static int mapBlock5To758(int id5) {
        if (id5 < 0 || id5 >= BLOCK_5_TO_758_MAP.length) {
            logUnknownComposedOnce("5→758", "ブロック", id5);
            return STONE_BLOCK_758;
        }
        return BLOCK_5_TO_758_MAP[id5];
    }

    /** 758のブロックエンティティ種別ID → 5の種別ID。5 のワイヤに数値 ID は現れないため 110 へ委譲。 */
    public static int mapBlockEntityType758To5(int id758) {
        return mapBlockEntityType758To110(id758);
    }

    /** 5のブロックエンティティ種別ID → 758の種別ID。110 へ委譲。 */
    public static int mapBlockEntityType5To758(int id5) {
        return mapBlockEntityType110To758(id5);
    }

    /** 758のバイオームID → 5のバイオームID。範囲外は平原。 */
    public static int mapBiome758To5(int id758) {
        if (id758 < 0 || id758 >= BIOME_758_TO_5_MAP.length) {
            logUnknownComposedOnce("758→5", "バイオーム", id758);
            return PLAINS_BIOME_5;
        }
        return BIOME_758_TO_5_MAP[id758];
    }

    /** 5のバイオームID → 758のバイオームID。範囲外は平原。 */
    public static int mapBiome5To758(int id5) {
        if (id5 < 0 || id5 >= BIOME_5_TO_758_MAP.length) {
            logUnknownComposedOnce("5→758", "バイオーム", id5);
            return PLAINS_BIOME_758;
        }
        return BIOME_5_TO_758_MAP[id5];
    }

    /** 5 のバイオーム名（レジストリ順、疎な ID は plains で埋めた配列）。 */
    public static java.util.List<String> biomeNames5() {
        return java.util.List.of(RegistryTables5.BIOME_NAMES_5);
    }

    /** 758のダメージ種別ID → 5のダメージ種別ID。5 のワイヤに現れないため 110 へ委譲。 */
    public static int mapDamageType758To5(int id758) {
        return mapDamageType758To110(id758);
    }

    /** 5のダメージ種別ID → 758のダメージ種別ID。110 へ委譲。 */
    public static int mapDamageType5To758(int id5) {
        return mapDamageType110To758(id5);
    }

    /** 758の音の名前 → 5の音の名前（Stop Sound 用）。 */
    public static String mapSoundName758To5(String name) {
        return mapSoundNameComposed(name, SOUND_NAME_FALLBACK_758_TO_5_MAP);
    }

    /** 5の音の名前 → 758の音の名前（Stop Sound 用）。 */
    public static String mapSoundName5To758(String name) {
        return mapSoundNameComposed(name, SOUND_NAME_FALLBACK_5_TO_758_MAP);
    }

    /** 758の音 ID → 5の音の名前。5 に無い音は null（落とす）。 */
    public static String mapSound758To5Name(int id758) {
        if (id758 < 0 || id758 >= RegistryTables5Snd.SOUND_NAMES_758.length) {
            return null;
        }
        String name758 = RegistryTables5Snd.SOUND_NAMES_758[id758];
        String name5 = mapSoundName758To5(name758);
        return RegistryTables5Snd.SOUND_NAME_SET_5.contains(name5)
                ? name5 : null;
    }

"""
    anchor4 = """    // 393 ⇔ 758（1.13 ⇔ 1.18.2）"""
    assert anchor4 in src
    src = src.replace(anchor4, functions + anchor4, 1)

    REMAPPER.write_text(src, encoding="utf-8")
    print("RegistryRemapper.java: 5 の節を追加")
    return 0


if __name__ == "__main__":
    sys.exit(main())
