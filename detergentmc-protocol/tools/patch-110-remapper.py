"""RegistryRemapper.java に 110（1.9.4／1.9.3）の定数・
対応表・変換メソッドを足す。

110 のレジストリは 210 と別（1.10 追加分の差分がある）ため、
210 のような別名流用ではなく RegistryTables110 の専用表を使う。
生成と照合は tools/gen-110-tables.py（実バニラ 1.9.4 jar からの
抽出を ground truth に、210 の表と合成し全件照合済み）。

使い方：
  python3 tools/patch-110-remapper.py
  冪等（110 の節が既にあれば何もしない）。
"""

import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
FILE = (ROOT / "src" / "main" / "java" / "io" / "detergentmc"
        / "protocol" / "RegistryRemapper.java")
DONE = "PROTOCOL_110 = 110"


def insert_after(text, anchor, addition):
    if anchor not in text:
        raise SystemExit(f"アンカーが見つからない: {anchor[:60]}")
    return text.replace(anchor, anchor + addition, 1)


def insert_before(text, anchor, addition):
    if anchor not in text:
        raise SystemExit(f"アンカーが見つからない: {anchor[:60]}")
    return text.replace(anchor, addition + anchor, 1)


def main():
    text = FILE.read_text(encoding="utf-8")
    if DONE in text:
        print("110 の節は既にある。何もしない。")
        return 0

    # 0) RegistryTables110 の static import
    text = insert_before(
        text,
        "import static io.detergentmc.protocol.RegistryTables210.*;\n",
        "import static io.detergentmc.protocol.RegistryTables110.*;\n")

    # 1) プロトコル定数
    text = insert_after(
        text,
        """    /** 210（1.10／1.10.1／1.10.2）のプロトコル番号。 */
    public static final int PROTOCOL_210 = 210;
""",
        """
    /** 110（1.9.4／1.9.3）のプロトコル番号。 */
    public static final int PROTOCOL_110 = 110;
""")

    # 2) 最大値・代表値の定数（値は gen-110-tables.py の出力で固定）
    text = insert_after(
        text,
        """    /** 210側の generic ダメージ種別。 */
    public static final int GENERIC_DAMAGE_TYPE_210 = 16;
""",
        """
    /** 110側のブロック状態最大値。 */
    public static final int MAX_BLOCK_STATE_110 = 4095;
    /** 110側のアイテム最大ID（最大ID 2267 の疎な番号。210 と同一）。 */
    public static final int MAX_ITEM_110 = 2267;
    /** 110側のエンティティ種別最大ID（210 と同一）。 */
    public static final int MAX_ENTITY_TYPE_110 = 200;
    /** 110側の音最大ID。 */
    public static final int MAX_SOUND_110 = 443;
    /** 110側のパーティクル最大ID。 */
    public static final int MAX_PARTICLE_110 = 45;
    /** 110側のブロック最大ID。 */
    public static final int MAX_BLOCK_110 = 255;
    /** 110側のバイオーム最大ID（210／316 と同一）。 */
    public static final int MAX_BIOME_110 = 167;
    /** 110側のダメージ種別最大ID（210／316 と同一）。 */
    public static final int MAX_DAMAGE_TYPE_110 = 41;
    /** 110側のブロックエンティティ種別最大ID（210 と同一の23件）。 */
    public static final int MAX_BLOCK_ENTITY_110 = 22;
    /** 110側の状態異常最大ID（210 と同一。実測で 1.9 と 1.10 が一致）。 */
    public static final int MAX_EFFECT_110 = 27;
    /** 110側の石ブロック状態。 */
    public static final int STONE_BLOCK_STATE_110 = 16; // 石 (1<<4)|0
    /** 110側の空気ブロック状態。 */
    public static final int AIR_BLOCK_STATE_110 = 0;
    /** 110側の石アイテムID。 */
    public static final int STONE_ITEM_110 = 1;
    /** 110側のブタのエンティティ種別ID。 */
    public static final int PIG_ENTITY_TYPE_110 = 90;
    /** 110側の汎用音ID（block.stone.break の 110 番号）。 */
    public static final int GENERIC_SOUND_110 = 364; // block.stone.break
    /** 110側の ambient パーティクル代替（explode）。 */
    public static final int AMBIENT_PARTICLE_110 = 0; // explode
    /** 110側の石ブロックID。 */
    public static final int STONE_BLOCK_110 = 1;
    /** 110側の平原バイオームID。 */
    public static final int PLAINS_BIOME_110 = 1;
    /** 110側の generic ダメージ種別。 */
    public static final int GENERIC_DAMAGE_TYPE_110 = 16;
""")

    # 3) 対応表（RegistryTables110 から expand。210 の表の後）
    text = insert_after(
        text,
        """    /** 210のダメージ種別ID → 758のダメージ種別ID（316 と同じ表。codec には無い）。 */
    private static final int[] DAMAGE_TYPE_210_TO_758_MAP =
            expand(DAMAGE_TYPE_210_TO_758_RANGES, DAMAGE_TYPE_210_TO_758_OVERRIDES,
                    MAX_DAMAGE_TYPE_210 + 1, GENERIC_DAMAGE_TYPE_758);
""",
        """    // ---- 110（1.9.4／1.9.3）：RegistryTables110 の専用表 ----
    /** 758のブロック状態ID → 110の (数値ID<<4)|メタデータ（RegistryTables110）。 */
    private static final int[] BLOCK_STATE_758_TO_110_MAP =
            expand(BLOCK_STATE_110_RANGES, BLOCK_STATE_110_OVERRIDES, MAX_BLOCK_STATE_758 + 1,
                    STONE_BLOCK_STATE_110);
    /** 110の (数値ID<<4)|メタデータ → 758のブロック状態ID（RegistryTables110）。 */
    private static final int[] BLOCK_STATE_110_TO_758_MAP =
            expand(BLOCK_STATE_110_TO_758_RANGES, BLOCK_STATE_110_TO_758_OVERRIDES,
                    MAX_BLOCK_STATE_110 + 1, STONE_BLOCK_STATE_758);
    /** 758のアイテムID → 110のアイテムID。 */
    private static final int[] ITEM_758_TO_110_MAP =
            expand(ITEM_110_RANGES, ITEM_110_OVERRIDES, MAX_ITEM_758 + 1, STONE_ITEM_110);
    /** 110のアイテムID → 758のアイテムID。 */
    private static final int[] ITEM_110_TO_758_MAP =
            expand(ITEM_110_TO_758_RANGES, ITEM_110_TO_758_OVERRIDES, MAX_ITEM_110 + 1,
                    STONE_ITEM_758);
    /** 758のエンティティ種別ID → 110のエンティティ種別ID。 */
    private static final int[] ENTITY_TYPE_758_TO_110_MAP =
            expand(ENTITY_TYPE_110_RANGES, ENTITY_TYPE_110_OVERRIDES, MAX_ENTITY_TYPE_758 + 1,
                    PIG_ENTITY_TYPE_110);
    /** 110のエンティティ種別ID → 758のエンティティ種別ID。 */
    private static final int[] ENTITY_TYPE_110_TO_758_MAP =
            expand(ENTITY_TYPE_110_TO_758_RANGES, ENTITY_TYPE_110_TO_758_OVERRIDES,
                    MAX_ENTITY_TYPE_110 + 1, PIG_ENTITY_TYPE_758);
    /** 758の音ID → 110の音ID。 */
    private static final int[] SOUND_758_TO_110_MAP =
            expand(SOUND_110_RANGES, SOUND_110_OVERRIDES, MAX_SOUND_758 + 1, GENERIC_SOUND_110);
    /** 110の音ID → 758の音ID。 */
    private static final int[] SOUND_110_TO_758_MAP =
            expand(SOUND_110_TO_758_RANGES, SOUND_110_TO_758_OVERRIDES, MAX_SOUND_110 + 1,
                    GENERIC_SOUND_758);
    /** 758のパーティクルID → 110のパーティクルID。 */
    private static final int[] PARTICLE_758_TO_110_MAP =
            expand(PARTICLE_110_RANGES, PARTICLE_110_OVERRIDES, MAX_PARTICLE_758 + 1,
                    AMBIENT_PARTICLE_110);
    /** 110のパーティクルID → 758のパーティクルID。 */
    private static final int[] PARTICLE_110_TO_758_MAP =
            expand(PARTICLE_110_TO_758_RANGES, PARTICLE_110_TO_758_OVERRIDES,
                    MAX_PARTICLE_110 + 1, AMBIENT_PARTICLE_758);
    /** 758のブロックID → 110のブロック数値ID。 */
    private static final int[] BLOCK_758_TO_110_MAP =
            expand(BLOCK_110_RANGES, BLOCK_110_OVERRIDES, MAX_BLOCK_758 + 1, STONE_BLOCK_110);
    /** 110のブロック数値ID → 758のブロックID。 */
    private static final int[] BLOCK_110_TO_758_MAP =
            expand(BLOCK_110_TO_758_RANGES, BLOCK_110_TO_758_OVERRIDES, MAX_BLOCK_110 + 1,
                    STONE_BLOCK_758);
    /** 758のブロックエンティティ種別ID → 110のブロックエンティティ種別ID。無い種別は DROP。 */
    private static final int[] BLOCK_ENTITY_758_TO_110_MAP =
            expand(BLOCK_ENTITY_110_RANGES, BLOCK_ENTITY_110_OVERRIDES, MAX_BLOCK_ENTITY_758 + 1,
                    DROP);
    /** 110のブロックエンティティ種別ID → 758のブロックエンティティ種別ID。 */
    private static final int[] BLOCK_ENTITY_110_TO_758_MAP =
            expand(BLOCK_ENTITY_110_TO_758_RANGES, BLOCK_ENTITY_110_TO_758_OVERRIDES,
                    MAX_BLOCK_ENTITY_110 + 1, DROP);
    /** 758のバイオームID → 110のバイオームID。 */
    private static final int[] BIOME_758_TO_110_MAP =
            expand(BIOME_110_RANGES, BIOME_110_OVERRIDES, MAX_BIOME_758 + 1, PLAINS_BIOME_110);
    /** 110のバイオームID → 758のバイオームID。 */
    private static final int[] BIOME_110_TO_758_MAP =
            expand(BIOME_110_TO_758_RANGES, BIOME_110_TO_758_OVERRIDES, MAX_BIOME_110 + 1,
                    PLAINS_BIOME_758);
    /** 758のダメージ種別ID → 110のダメージ種別ID（210 と同じ表。codec には無い）。 */
    private static final int[] DAMAGE_TYPE_758_TO_110_MAP =
            expand(DAMAGE_TYPE_110_RANGES, DAMAGE_TYPE_110_OVERRIDES, MAX_DAMAGE_TYPE_758 + 1,
                    GENERIC_DAMAGE_TYPE_110);
    /** 110のダメージ種別ID → 758のダメージ種別ID（210 と同じ表。codec には無い）。 */
    private static final int[] DAMAGE_TYPE_110_TO_758_MAP =
            expand(DAMAGE_TYPE_110_TO_758_RANGES, DAMAGE_TYPE_110_TO_758_OVERRIDES,
                    MAX_DAMAGE_TYPE_110 + 1, GENERIC_DAMAGE_TYPE_758);
""")

    # 4) 音の名前フォールバック表
    text = insert_after(
        text,
        """    /** 210の音の名前 → 758の音の名前（Stop Sound 用）。数値の合成表と一致させる。 */
    private static final Map<String, String> SOUND_NAME_FALLBACK_210_TO_758_MAP =
            buildSoundNameMap(SOUND_NAME_FALLBACKS_210_TO_758);
""",
        """
    /** 758の音の名前 → 110の音の名前（Stop Sound 用）。数値の合成表と一致させる。 */
    private static final Map<String, String> SOUND_NAME_FALLBACK_758_TO_110_MAP =
            buildSoundNameMap(SOUND_NAME_FALLBACKS_758_TO_110);

    /** 110の音の名前 → 758の音の名前（Stop Sound 用）。数値の合成表と一致させる。 */
    private static final Map<String, String> SOUND_NAME_FALLBACK_110_TO_758_MAP =
            buildSoundNameMap(SOUND_NAME_FALLBACKS_110_TO_758);
""")

    # 5) 変換メソッド（210 の節の後）
    text = insert_after(
        text,
        """    /** 210の音の名前 → 758の音の名前（Stop Sound 用）。数値の合成表と一致させる。 */
    public static String mapSoundName210To758(String name) {
        return mapSoundNameComposed(name, SOUND_NAME_FALLBACK_210_TO_758_MAP);
    }
""",
        """
    // 110 ⇔ 758（1.9.4／1.9.3 ⇔ 1.18.2）
    // 110 は 210 と同じ Flattening 前の世代で、210 との差分は
    // 1.10 追加分だけ（ブロック 5 種・アイテム 6 件・polar_bear・
    // 音 19 件・fallingdust）。意味対応表は RegistryTables110。
    // 生成と検証は tools/gen-110-tables.py。
    // メニューは 110 にレジストリが無いため写像を作らない。

    /** 758のブロック状態ID → 110の (数値ID<<4)|メタデータ。範囲外は石。 */
    public static int mapBlockState758To110(int id758) {
        if (id758 < 0 || id758 >= BLOCK_STATE_758_TO_110_MAP.length) {
            logUnknownComposedOnce("758→110", "ブロック状態", id758);
            return STONE_BLOCK_STATE_110;
        }
        return BLOCK_STATE_758_TO_110_MAP[id758];
    }

    /** 110の (数値ID<<4)|メタデータ → 758のブロック状態ID。範囲外は石。 */
    public static int mapBlockState110To758(int value110) {
        if (value110 < 0 || value110 >= BLOCK_STATE_110_TO_758_MAP.length) {
            logUnknownComposedOnce("110→758", "ブロック状態", value110);
            return STONE_BLOCK_STATE_758;
        }
        return BLOCK_STATE_110_TO_758_MAP[value110];
    }

    /** 758のアイテムID → 110のアイテムID。範囲外は石アイテム。 */
    public static int mapItem758To110(int id758) {
        if (id758 < 0 || id758 >= ITEM_758_TO_110_MAP.length) {
            logUnknownComposedOnce("758→110", "アイテム", id758);
            return STONE_ITEM_110;
        }
        return ITEM_758_TO_110_MAP[id758];
    }

    /** 110のアイテムID → 758のアイテムID。範囲外は石アイテム。 */
    public static int mapItem110To758(int id110) {
        if (id110 < 0 || id110 >= ITEM_110_TO_758_MAP.length) {
            logUnknownComposedOnce("110→758", "アイテム", id110);
            return STONE_ITEM_758;
        }
        return ITEM_110_TO_758_MAP[id110];
    }

    /** 758のエンティティ種別ID → 110のエンティティ種別ID。範囲外はブタ。 */
    public static int mapEntityType758To110(int id758) {
        if (id758 < 0 || id758 >= ENTITY_TYPE_758_TO_110_MAP.length) {
            logUnknownComposedOnce("758→110", "エンティティ種別", id758);
            return PIG_ENTITY_TYPE_110;
        }
        return ENTITY_TYPE_758_TO_110_MAP[id758];
    }

    /** 110のエンティティ種別ID → 758のエンティティ種別ID。範囲外はブタ。 */
    public static int mapEntityType110To758(int id110) {
        if (id110 < 0 || id110 >= ENTITY_TYPE_110_TO_758_MAP.length) {
            logUnknownComposedOnce("110→758", "エンティティ種別", id110);
            return PIG_ENTITY_TYPE_758;
        }
        return ENTITY_TYPE_110_TO_758_MAP[id110];
    }

    /** 758の音ID → 110の音ID。範囲外は汎用音。 */
    public static int mapSound758To110(int id758) {
        if (id758 < 0 || id758 >= SOUND_758_TO_110_MAP.length) {
            logUnknownComposedOnce("758→110", "音", id758);
            return GENERIC_SOUND_110;
        }
        return SOUND_758_TO_110_MAP[id758];
    }

    /** 110の音ID → 758の音ID。範囲外は汎用音。 */
    public static int mapSound110To758(int id110) {
        if (id110 < 0 || id110 >= SOUND_110_TO_758_MAP.length) {
            logUnknownComposedOnce("110→758", "音", id110);
            return GENERIC_SOUND_758;
        }
        return SOUND_110_TO_758_MAP[id110];
    }

    /** 758のパーティクルID → 110のパーティクルID。範囲外は explode。 */
    public static int mapParticle758To110(int id758) {
        if (id758 < 0 || id758 >= PARTICLE_758_TO_110_MAP.length) {
            logUnknownComposedOnce("758→110", "パーティクル", id758);
            return AMBIENT_PARTICLE_110;
        }
        return PARTICLE_758_TO_110_MAP[id758];
    }

    /** 110のパーティクルID → 758のパーティクルID。範囲外は ambient 代替。 */
    public static int mapParticle110To758(int id110) {
        if (id110 < 0 || id110 >= PARTICLE_110_TO_758_MAP.length) {
            logUnknownComposedOnce("110→758", "パーティクル", id110);
            return AMBIENT_PARTICLE_758;
        }
        return PARTICLE_110_TO_758_MAP[id110];
    }

    /** 758のブロックID → 110のブロック数値ID。範囲外は石。 */
    public static int mapBlock758To110(int id758) {
        if (id758 < 0 || id758 >= BLOCK_758_TO_110_MAP.length) {
            logUnknownComposedOnce("758→110", "ブロック", id758);
            return STONE_BLOCK_110;
        }
        return BLOCK_758_TO_110_MAP[id758];
    }

    /** 110のブロック数値ID → 758のブロックID。範囲外は石。 */
    public static int mapBlock110To758(int id110) {
        if (id110 < 0 || id110 >= BLOCK_110_TO_758_MAP.length) {
            logUnknownComposedOnce("110→758", "ブロック", id110);
            return STONE_BLOCK_758;
        }
        return BLOCK_110_TO_758_MAP[id110];
    }

    /** 758のブロックエンティティ種別ID → 110の種別ID。無い種別は DROP（-1）。 */
    public static int mapBlockEntityType758To110(int id758) {
        if (id758 < 0 || id758 >= BLOCK_ENTITY_758_TO_110_MAP.length) {
            logUnknownComposedOnce("758→110", "ブロックエンティティ", id758);
            return DROP;
        }
        return BLOCK_ENTITY_758_TO_110_MAP[id758];
    }

    /** 110のブロックエンティティ種別ID → 758の種別ID。範囲外は DROP（-1）。 */
    public static int mapBlockEntityType110To758(int id110) {
        if (id110 < 0 || id110 >= BLOCK_ENTITY_110_TO_758_MAP.length) {
            logUnknownComposedOnce("110→758", "ブロックエンティティ", id110);
            return DROP;
        }
        return BLOCK_ENTITY_110_TO_758_MAP[id110];
    }

    /** 758のバイオームID → 110のバイオームID。範囲外は平原。 */
    public static int mapBiome758To110(int id758) {
        if (id758 < 0 || id758 >= BIOME_758_TO_110_MAP.length) {
            logUnknownComposedOnce("758→110", "バイオーム", id758);
            return PLAINS_BIOME_110;
        }
        return BIOME_758_TO_110_MAP[id758];
    }

    /** 110のバイオームID → 758のバイオームID。範囲外は平原。 */
    public static int mapBiome110To758(int id110) {
        if (id110 < 0 || id110 >= BIOME_110_TO_758_MAP.length) {
            logUnknownComposedOnce("110→758", "バイオーム", id110);
            return PLAINS_BIOME_758;
        }
        return BIOME_110_TO_758_MAP[id110];
    }

    /** 110 のバイオーム名（レジストリ順、疎な ID は plains で埋めた配列）。 */
    public static java.util.List<String> biomeNames110() {
        return java.util.List.of(RegistryTables110.BIOME_NAMES_110);
    }

    /** 758のダメージ種別ID → 110のダメージ種別ID。 */
    public static int mapDamageType758To110(int id758) {
        if (id758 < 0 || id758 >= DAMAGE_TYPE_758_TO_110_MAP.length) {
            logUnknownComposedOnce("758→110", "ダメージ種別", id758);
            return GENERIC_DAMAGE_TYPE_110;
        }
        return DAMAGE_TYPE_758_TO_110_MAP[id758];
    }

    /** 110のダメージ種別ID → 758のダメージ種別ID。 */
    public static int mapDamageType110To758(int id110) {
        if (id110 < 0 || id110 >= DAMAGE_TYPE_110_TO_758_MAP.length) {
            logUnknownComposedOnce("110→758", "ダメージ種別", id110);
            return GENERIC_DAMAGE_TYPE_758;
        }
        return DAMAGE_TYPE_110_TO_758_MAP[id110];
    }

    /** 758の音の名前 → 110の音の名前（Stop Sound 用）。数値の合成表と一致させる。 */
    public static String mapSoundName758To110(String name) {
        return mapSoundNameComposed(name, SOUND_NAME_FALLBACK_758_TO_110_MAP);
    }

    /** 110の音の名前 → 758の音の名前（Stop Sound 用）。数値の合成表と一致させる。 */
    public static String mapSoundName110To758(String name) {
        return mapSoundNameComposed(name, SOUND_NAME_FALLBACK_110_TO_758_MAP);
    }
""")

    FILE.write_text(text, encoding="utf-8")
    print("RegistryRemapper に 110 の定数・対応表・変換メソッドを追加した。")
    return 0


if __name__ == "__main__":
    sys.exit(main())
