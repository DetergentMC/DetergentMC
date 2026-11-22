#!/usr/bin/env python3
"""RegistryRemapper.java へ 47（1.8〜1.8.9）の節を追加する。

追加内容：
- RegistryTables47 の static import
- PROTOCOL_47 定数
- 47 の最大値・代替値の定数群
- 合成済み写像フィールド（BLOCK_STATE／ITEM／ENTITY_TYPE／
  PARTICLE／BLOCK／BIOME）
- 音名フォールバックの Map フィールド
- 47 ⇔ 758 の写像関数群（音は数値表を持たず名前のみ。
  ブロックエンティティとダメージ種別は 110 へ委譲）

一度だけ実行する（冪等ではない）。
"""
import sys
from pathlib import Path

P = Path(__file__).resolve().parent.parent / "src" / "main" / "java" / \
    "io" / "detergentmc" / "protocol" / "RegistryRemapper.java"
text = P.read_text(encoding="utf-8")


def sub_once(old, new):
    global text
    if text.count(old) != 1:
        print(f"FAIL: 置換対象が一意でない ({text.count(old)} 件): {old[:60]}")
        sys.exit(1)
    text = text.replace(old, new)


# 1. static import
sub_once("import static io.detergentmc.protocol.RegistryTables110.*;",
         "import static io.detergentmc.protocol.RegistryTables110.*;\n"
         "import static io.detergentmc.protocol.RegistryTables47.*;")

# 2. PROTOCOL_47
sub_once("""    /** 107（1.9）のプロトコル番号。 */
    public static final int PROTOCOL_107 = 107;""",
         """    /** 107（1.9）のプロトコル番号。 */
    public static final int PROTOCOL_107 = 107;

    /** 47（1.8〜1.8.9）のプロトコル番号。 */
    public static final int PROTOCOL_47 = 47;""")

# 3. 定数群（110 の定数群の直後、393 のコメントの前）
sub_once("""    /** 110側の generic ダメージ種別。 */
    public static final int GENERIC_DAMAGE_TYPE_110 = 16;
""",
         """    /** 110側の generic ダメージ種別。 */
    public static final int GENERIC_DAMAGE_TYPE_110 = 16;

    /** 47側のブロック状態最大値（110 と同一の (数値ID<<4)|メタデータ空間）。 */
    public static final int MAX_BLOCK_STATE_47 = 4095;
    /** 47側のアイテム最大ID（110 と同一の疎な番号）。 */
    public static final int MAX_ITEM_47 = 2267;
    /** 47側のエンティティ種別最大ID（110 と同一）。 */
    public static final int MAX_ENTITY_TYPE_47 = 200;
    /** 47側のパーティクル最大ID（表の大きさは 110 と同一。実在は 41 まで）。 */
    public static final int MAX_PARTICLE_47 = 45;
    /** 47側のブロック最大ID。 */
    public static final int MAX_BLOCK_47 = 255;
    /** 47側のバイオーム最大ID（110 と同一）。 */
    public static final int MAX_BIOME_47 = 167;
    /** 47側の石ブロック状態。 */
    public static final int STONE_BLOCK_STATE_47 = 16; // 石 (1<<4)|0
    /** 47側の空気ブロック状態。 */
    public static final int AIR_BLOCK_STATE_47 = 0;
    /** 47側の石アイテムID。 */
    public static final int STONE_ITEM_47 = 1;
    /** 47側のブタのエンティティ種別ID。 */
    public static final int PIG_ENTITY_TYPE_47 = 90;
    /** 47側の ambient パーティクル代替（explode）。 */
    public static final int AMBIENT_PARTICLE_47 = 0; // explode
    /** 47側の石ブロックID。 */
    public static final int STONE_BLOCK_47 = 1;
    /** 47側の平原バイオームID。 */
    public static final int PLAINS_BIOME_47 = 1;
""")

# 4. 合成済み写像フィールド（110 の最後のフィールドの直後）
sub_once("""    private static final int[] DAMAGE_TYPE_110_TO_758_MAP =
            expand(DAMAGE_TYPE_110_TO_758_RANGES, DAMAGE_TYPE_110_TO_758_OVERRIDES,
                    MAX_DAMAGE_TYPE_110 + 1, GENERIC_DAMAGE_TYPE_758);
""",
         """    private static final int[] DAMAGE_TYPE_110_TO_758_MAP =
            expand(DAMAGE_TYPE_110_TO_758_RANGES, DAMAGE_TYPE_110_TO_758_OVERRIDES,
                    MAX_DAMAGE_TYPE_110 + 1, GENERIC_DAMAGE_TYPE_758);
    // ---- 47（1.8〜1.8.9）：RegistryTables47 の専用表 ----
    /** 758のブロック状態ID → 47の (数値ID<<4)|メタデータ（RegistryTables47）。 */
    private static final int[] BLOCK_STATE_758_TO_47_MAP =
            expand(BLOCK_STATE_47_RANGES, BLOCK_STATE_47_OVERRIDES, MAX_BLOCK_STATE_758 + 1,
                    STONE_BLOCK_STATE_47);
    /** 47の (数値ID<<4)|メタデータ → 758のブロック状態ID（RegistryTables47）。 */
    private static final int[] BLOCK_STATE_47_TO_758_MAP =
            expand(BLOCK_STATE_47_TO_758_RANGES, BLOCK_STATE_47_TO_758_OVERRIDES,
                    MAX_BLOCK_STATE_47 + 1, STONE_BLOCK_STATE_758);
    /** 758のアイテムID → 47のアイテムID。 */
    private static final int[] ITEM_758_TO_47_MAP =
            expand(ITEM_47_RANGES, ITEM_47_OVERRIDES, MAX_ITEM_758 + 1, STONE_ITEM_47);
    /** 47のアイテムID → 758のアイテムID。 */
    private static final int[] ITEM_47_TO_758_MAP =
            expand(ITEM_47_TO_758_RANGES, ITEM_47_TO_758_OVERRIDES, MAX_ITEM_47 + 1,
                    STONE_ITEM_758);
    /** 758のエンティティ種別ID → 47のエンティティ種別ID。 */
    private static final int[] ENTITY_TYPE_758_TO_47_MAP =
            expand(ENTITY_TYPE_47_RANGES, ENTITY_TYPE_47_OVERRIDES, MAX_ENTITY_TYPE_758 + 1,
                    PIG_ENTITY_TYPE_47);
    /** 47のエンティティ種別ID → 758のエンティティ種別ID。 */
    private static final int[] ENTITY_TYPE_47_TO_758_MAP =
            expand(ENTITY_TYPE_47_TO_758_RANGES, ENTITY_TYPE_47_TO_758_OVERRIDES,
                    MAX_ENTITY_TYPE_47 + 1, PIG_ENTITY_TYPE_758);
    /** 758のパーティクルID → 47のパーティクルID。 */
    private static final int[] PARTICLE_758_TO_47_MAP =
            expand(PARTICLE_47_RANGES, PARTICLE_47_OVERRIDES, MAX_PARTICLE_758 + 1,
                    AMBIENT_PARTICLE_47);
    /** 47のパーティクルID → 758のパーティクルID。 */
    private static final int[] PARTICLE_47_TO_758_MAP =
            expand(PARTICLE_47_TO_758_RANGES, PARTICLE_47_TO_758_OVERRIDES,
                    MAX_PARTICLE_47 + 1, AMBIENT_PARTICLE_758);
    /** 758のブロックID → 47のブロック数値ID。 */
    private static final int[] BLOCK_758_TO_47_MAP =
            expand(BLOCK_47_RANGES, BLOCK_47_OVERRIDES, MAX_BLOCK_758 + 1, STONE_BLOCK_47);
    /** 47のブロック数値ID → 758のブロックID。 */
    private static final int[] BLOCK_47_TO_758_MAP =
            expand(BLOCK_47_TO_758_RANGES, BLOCK_47_TO_758_OVERRIDES, MAX_BLOCK_47 + 1,
                    STONE_BLOCK_758);
    /** 758のバイオームID → 47のバイオームID。 */
    private static final int[] BIOME_758_TO_47_MAP =
            expand(BIOME_47_RANGES, BIOME_47_OVERRIDES, MAX_BIOME_758 + 1, PLAINS_BIOME_47);
    /** 47のバイオームID → 758のバイオームID。 */
    private static final int[] BIOME_47_TO_758_MAP =
            expand(BIOME_47_TO_758_RANGES, BIOME_47_TO_758_OVERRIDES, MAX_BIOME_47 + 1,
                    PLAINS_BIOME_758);
""")

# 5. 音名フォールバック Map（110 の直後）
sub_once("""    /** 110の音の名前 → 758の音の名前（Stop Sound 用）。数値の合成表と一致させる。 */
    private static final Map<String, String> SOUND_NAME_FALLBACK_110_TO_758_MAP =
            buildSoundNameMap(SOUND_NAME_FALLBACKS_110_TO_758);
""",
         """    /** 110の音の名前 → 758の音の名前（Stop Sound 用）。数値の合成表と一致させる。 */
    private static final Map<String, String> SOUND_NAME_FALLBACK_110_TO_758_MAP =
            buildSoundNameMap(SOUND_NAME_FALLBACKS_110_TO_758);

    /** 758の音の名前 → 47の音の名前（Stop Sound 用）。 */
    private static final Map<String, String> SOUND_NAME_FALLBACK_758_TO_47_MAP =
            buildSoundNameMap(SOUND_NAME_FALLBACKS_758_TO_47);

    /** 47の音の名前 → 758の音の名前（Stop Sound 用）。 */
    private static final Map<String, String> SOUND_NAME_FALLBACK_47_TO_758_MAP =
            buildSoundNameMap(SOUND_NAME_FALLBACKS_47_TO_758);
""")

# 6. 写像関数群（107 節の直後、393 節の前）
SECTION = '''
    // 47 ⇔ 758（1.8〜1.8.9 ⇔ 1.18.2）
    // 47 は 110 と同じ Flattening 前の世代で、110 との差分は
    // 1.9 の追加分だけ（ブロック 16 種・アイテム 29 件・shulker・
    // the_void・パーティクル 4 種）。意味対応表は RegistryTables47
    // （110 の表との合成）。生成と検証は tools/gen-47-tables.py。
    // 音は 47 では数値 ID がワイヤに無い（Named Sound Effect の
    // 名前だけ）ため数値の音写像は作らず、名前写像のみ。
    // ブロックエンティティとダメージ種別は 47 のワイヤに数値 ID が
    // 現れないため 110 の写像へ委譲する。メニューは 47 に
    // レジストリが無いため写像を作らない（110 と同じ）。

    /** 758のブロック状態ID → 47の (数値ID<<4)|メタデータ。範囲外は石。 */
    public static int mapBlockState758To47(int id758) {
        if (id758 < 0 || id758 >= BLOCK_STATE_758_TO_47_MAP.length) {
            logUnknownComposedOnce("758→47", "ブロック状態", id758);
            return STONE_BLOCK_STATE_47;
        }
        return BLOCK_STATE_758_TO_47_MAP[id758];
    }

    /** 47の (数値ID<<4)|メタデータ → 758のブロック状態ID。範囲外は石。 */
    public static int mapBlockState47To758(int value47) {
        if (value47 < 0 || value47 >= BLOCK_STATE_47_TO_758_MAP.length) {
            logUnknownComposedOnce("47→758", "ブロック状態", value47);
            return STONE_BLOCK_STATE_758;
        }
        return BLOCK_STATE_47_TO_758_MAP[value47];
    }

    /** 758のアイテムID → 47のアイテムID。範囲外は石アイテム。 */
    public static int mapItem758To47(int id758) {
        if (id758 < 0 || id758 >= ITEM_758_TO_47_MAP.length) {
            logUnknownComposedOnce("758→47", "アイテム", id758);
            return STONE_ITEM_47;
        }
        return ITEM_758_TO_47_MAP[id758];
    }

    /** 47のアイテムID → 758のアイテムID。範囲外は石アイテム。 */
    public static int mapItem47To758(int id47) {
        if (id47 < 0 || id47 >= ITEM_47_TO_758_MAP.length) {
            logUnknownComposedOnce("47→758", "アイテム", id47);
            return STONE_ITEM_758;
        }
        return ITEM_47_TO_758_MAP[id47];
    }

    /** 758のエンティティ種別ID → 47のエンティティ種別ID。範囲外はブタ。 */
    public static int mapEntityType758To47(int id758) {
        if (id758 < 0 || id758 >= ENTITY_TYPE_758_TO_47_MAP.length) {
            logUnknownComposedOnce("758→47", "エンティティ種別", id758);
            return PIG_ENTITY_TYPE_47;
        }
        return ENTITY_TYPE_758_TO_47_MAP[id758];
    }

    /** 47のエンティティ種別ID → 758のエンティティ種別ID。範囲外はブタ。 */
    public static int mapEntityType47To758(int id47) {
        if (id47 < 0 || id47 >= ENTITY_TYPE_47_TO_758_MAP.length) {
            logUnknownComposedOnce("47→758", "エンティティ種別", id47);
            return PIG_ENTITY_TYPE_758;
        }
        return ENTITY_TYPE_47_TO_758_MAP[id47];
    }

    /** 758のパーティクルID → 47のパーティクルID。範囲外は explode。 */
    public static int mapParticle758To47(int id758) {
        if (id758 < 0 || id758 >= PARTICLE_758_TO_47_MAP.length) {
            logUnknownComposedOnce("758→47", "パーティクル", id758);
            return AMBIENT_PARTICLE_47;
        }
        return PARTICLE_758_TO_47_MAP[id758];
    }

    /** 47のパーティクルID → 758のパーティクルID。範囲外は ambient 代替。 */
    public static int mapParticle47To758(int id47) {
        if (id47 < 0 || id47 >= PARTICLE_47_TO_758_MAP.length) {
            logUnknownComposedOnce("47→758", "パーティクル", id47);
            return AMBIENT_PARTICLE_758;
        }
        return PARTICLE_47_TO_758_MAP[id47];
    }

    /** 758のブロックID → 47のブロック数値ID。範囲外は石。 */
    public static int mapBlock758To47(int id758) {
        if (id758 < 0 || id758 >= BLOCK_758_TO_47_MAP.length) {
            logUnknownComposedOnce("758→47", "ブロック", id758);
            return STONE_BLOCK_47;
        }
        return BLOCK_758_TO_47_MAP[id758];
    }

    /** 47のブロック数値ID → 758のブロックID。範囲外は石。 */
    public static int mapBlock47To758(int id47) {
        if (id47 < 0 || id47 >= BLOCK_47_TO_758_MAP.length) {
            logUnknownComposedOnce("47→758", "ブロック", id47);
            return STONE_BLOCK_758;
        }
        return BLOCK_47_TO_758_MAP[id47];
    }

    /** 758のブロックエンティティ種別ID → 47の種別ID。47 のワイヤに数値 ID は現れないため 110 へ委譲。 */
    public static int mapBlockEntityType758To47(int id758) {
        return mapBlockEntityType758To110(id758);
    }

    /** 47のブロックエンティティ種別ID → 758の種別ID。110 へ委譲。 */
    public static int mapBlockEntityType47To758(int id47) {
        return mapBlockEntityType110To758(id47);
    }

    /** 758のバイオームID → 47のバイオームID。範囲外は平原。 */
    public static int mapBiome758To47(int id758) {
        if (id758 < 0 || id758 >= BIOME_758_TO_47_MAP.length) {
            logUnknownComposedOnce("758→47", "バイオーム", id758);
            return PLAINS_BIOME_47;
        }
        return BIOME_758_TO_47_MAP[id758];
    }

    /** 47のバイオームID → 758のバイオームID。範囲外は平原。 */
    public static int mapBiome47To758(int id47) {
        if (id47 < 0 || id47 >= BIOME_47_TO_758_MAP.length) {
            logUnknownComposedOnce("47→758", "バイオーム", id47);
            return PLAINS_BIOME_758;
        }
        return BIOME_47_TO_758_MAP[id47];
    }

    /** 47 のバイオーム名（レジストリ順、疎な ID は plains で埋めた配列）。 */
    public static java.util.List<String> biomeNames47() {
        return java.util.List.of(RegistryTables47.BIOME_NAMES_47);
    }

    /** 758のダメージ種別ID → 47のダメージ種別ID。47 のワイヤに現れないため 110 へ委譲。 */
    public static int mapDamageType758To47(int id758) {
        return mapDamageType758To110(id758);
    }

    /** 47のダメージ種別ID → 758のダメージ種別ID。110 へ委譲。 */
    public static int mapDamageType47To758(int id47) {
        return mapDamageType110To758(id47);
    }

    /** 758の音の名前 → 47の音の名前（Stop Sound 用）。 */
    public static String mapSoundName758To47(String name) {
        return mapSoundNameComposed(name, SOUND_NAME_FALLBACK_758_TO_47_MAP);
    }

    /** 47の音の名前 → 758の音の名前（Stop Sound 用）。 */
    public static String mapSoundName47To758(String name) {
        return mapSoundNameComposed(name, SOUND_NAME_FALLBACK_47_TO_758_MAP);
    }

    /** 758の音 ID → 47の音の名前。47 に無い音は null（落とす）。 */
    public static String mapSound758To47Name(int id758) {
        if (id758 < 0 || id758 >= RegistryTables47Snd.SOUND_NAMES_758.length) {
            return null;
        }
        String name758 = RegistryTables47Snd.SOUND_NAMES_758[id758];
        String name47 = mapSoundName758To47(name758);
        return RegistryTables47Snd.SOUND_NAME_SET_47.contains(name47)
                ? name47 : null;
    }
'''
sub_once("""
    // 393 ⇔ 758（1.13 ⇔ 1.18.2）""",
         SECTION + """
    // 393 ⇔ 758（1.13 ⇔ 1.18.2）""")

P.write_text(text, encoding="utf-8")
print("RegistryRemapper へ 47 節を追加した")
