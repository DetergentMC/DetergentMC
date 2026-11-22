"""RegistryRemapper.java に 210（1.10／1.10.1／1.10.2）の定数・
対応表・変換メソッドを足す。

210 のレジストリは 315／316 と別（1.11 追加分の差分がある）ため、
315 のような別名流用ではなく RegistryTables210 の専用表を使う。
生成と照合は tools/gen-210-tables.py（実バニラ 1.10.2 jar からの
抽出を ground truth に、316 の表と合成し全件照合済み）。

使い方：
  python3 tools/patch-210-remapper.py
  冪等（210 の節が既にあれば何もしない）。
"""

import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
FILE = (ROOT / "src" / "main" / "java" / "io" / "detergentmc"
        / "protocol" / "RegistryRemapper.java")
DONE = "PROTOCOL_210 = 210"


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
        print("210 の節は既にある。何もしない。")
        return 0

    # 0) RegistryTables210 の static import
    text = insert_before(
        text,
        "import static io.detergentmc.protocol.RegistryTables316.*;\n",
        "import static io.detergentmc.protocol.RegistryTables210.*;\n")

    # 1) プロトコル定数
    text = insert_after(
        text,
        """    /** 315（1.11）のプロトコル番号。 */
    public static final int PROTOCOL_315 = 315;
""",
        """
    /** 210（1.10／1.10.1／1.10.2）のプロトコル番号。 */
    public static final int PROTOCOL_210 = 210;
""")

    # 2) 最大値・代表値の定数（値は gen-210-tables.py の出力で固定）
    text = insert_after(
        text,
        """    /** 315側の generic ダメージ種別（316 と同一）。 */
    public static final int GENERIC_DAMAGE_TYPE_315 = 16;
""",
        """
    /** 210側のブロック状態最大値。 */
    public static final int MAX_BLOCK_STATE_210 = 4095;
    /** 210側のアイテム最大ID（最大ID 2267 の疎な番号）。 */
    public static final int MAX_ITEM_210 = 2267;
    /** 210側のエンティティ種別最大ID。 */
    public static final int MAX_ENTITY_TYPE_210 = 200;
    /** 210側の音最大ID。 */
    public static final int MAX_SOUND_210 = 462;
    /** 210側のパーティクル最大ID。 */
    public static final int MAX_PARTICLE_210 = 46;
    /** 210側のブロック最大ID。 */
    public static final int MAX_BLOCK_210 = 255;
    /** 210側のバイオーム最大ID（316 と同一）。 */
    public static final int MAX_BIOME_210 = 167;
    /** 210側のダメージ種別最大ID（316 と同一）。 */
    public static final int MAX_DAMAGE_TYPE_210 = 41;
    /** 210側のブロックエンティティ種別最大ID（shulker_box は無い）。 */
    public static final int MAX_BLOCK_ENTITY_210 = 22;
    /** 210側の状態異常最大ID（316 と同一。実測で 1.10 と 1.11 が一致）。 */
    public static final int MAX_EFFECT_210 = 27;
    /** 210側の石ブロック状態。 */
    public static final int STONE_BLOCK_STATE_210 = 16; // 石 (1<<4)|0
    /** 210側の空気ブロック状態。 */
    public static final int AIR_BLOCK_STATE_210 = 0;
    /** 210側の石アイテムID。 */
    public static final int STONE_ITEM_210 = 1;
    /** 210側のブタのエンティティ種別ID。 */
    public static final int PIG_ENTITY_TYPE_210 = 90;
    /** 210側の汎用音ID（block.stone.break の 210 番号）。 */
    public static final int GENERIC_SOUND_210 = 375; // block.stone.break
    /** 210側の ambient パーティクル代替（explode）。 */
    public static final int AMBIENT_PARTICLE_210 = 0; // explode
    /** 210側の石ブロックID。 */
    public static final int STONE_BLOCK_210 = 1;
    /** 210側の平原バイオームID。 */
    public static final int PLAINS_BIOME_210 = 1;
    /** 210側の generic ダメージ種別。 */
    public static final int GENERIC_DAMAGE_TYPE_210 = 16;
""")

    # 3) 対応表（RegistryTables210 から expand）
    text = insert_after(
        text,
        """    private static int[] buildItem758To315Map() {
        int[] map = ITEM_758_TO_316_MAP.clone();
        for (int i = 0; i < map.length; i++) {
            if (map[i] == 452) {
                map[i] = 371; // gold_nugget（315 に iron_nugget は無い）
            }
        }
        return map;
    }
""",
        """    // ---- 210（1.10／1.10.1／1.10.2）：RegistryTables210 の専用表 ----
    /** 758のブロック状態ID → 210の (数値ID<<4)|メタデータ（RegistryTables210）。 */
    private static final int[] BLOCK_STATE_758_TO_210_MAP =
            expand(BLOCK_STATE_210_RANGES, BLOCK_STATE_210_OVERRIDES, MAX_BLOCK_STATE_758 + 1,
                    STONE_BLOCK_STATE_210);
    /** 210の (数値ID<<4)|メタデータ → 758のブロック状態ID（RegistryTables210）。 */
    private static final int[] BLOCK_STATE_210_TO_758_MAP =
            expand(BLOCK_STATE_210_TO_758_RANGES, BLOCK_STATE_210_TO_758_OVERRIDES,
                    MAX_BLOCK_STATE_210 + 1, STONE_BLOCK_STATE_758);
    /** 758のアイテムID → 210のアイテムID。 */
    private static final int[] ITEM_758_TO_210_MAP =
            expand(ITEM_210_RANGES, ITEM_210_OVERRIDES, MAX_ITEM_758 + 1, STONE_ITEM_210);
    /** 210のアイテムID → 758のアイテムID。 */
    private static final int[] ITEM_210_TO_758_MAP =
            expand(ITEM_210_TO_758_RANGES, ITEM_210_TO_758_OVERRIDES, MAX_ITEM_210 + 1,
                    STONE_ITEM_758);
    /** 758のエンティティ種別ID → 210のエンティティ種別ID。 */
    private static final int[] ENTITY_TYPE_758_TO_210_MAP =
            expand(ENTITY_TYPE_210_RANGES, ENTITY_TYPE_210_OVERRIDES, MAX_ENTITY_TYPE_758 + 1,
                    PIG_ENTITY_TYPE_210);
    /** 210のエンティティ種別ID → 758のエンティティ種別ID。 */
    private static final int[] ENTITY_TYPE_210_TO_758_MAP =
            expand(ENTITY_TYPE_210_TO_758_RANGES, ENTITY_TYPE_210_TO_758_OVERRIDES,
                    MAX_ENTITY_TYPE_210 + 1, PIG_ENTITY_TYPE_758);
    /** 758の音ID → 210の音ID。 */
    private static final int[] SOUND_758_TO_210_MAP =
            expand(SOUND_210_RANGES, SOUND_210_OVERRIDES, MAX_SOUND_758 + 1, GENERIC_SOUND_210);
    /** 210の音ID → 758の音ID。 */
    private static final int[] SOUND_210_TO_758_MAP =
            expand(SOUND_210_TO_758_RANGES, SOUND_210_TO_758_OVERRIDES, MAX_SOUND_210 + 1,
                    GENERIC_SOUND_758);
    /** 758のパーティクルID → 210のパーティクルID。 */
    private static final int[] PARTICLE_758_TO_210_MAP =
            expand(PARTICLE_210_RANGES, PARTICLE_210_OVERRIDES, MAX_PARTICLE_758 + 1,
                    AMBIENT_PARTICLE_210);
    /** 210のパーティクルID → 758のパーティクルID。 */
    private static final int[] PARTICLE_210_TO_758_MAP =
            expand(PARTICLE_210_TO_758_RANGES, PARTICLE_210_TO_758_OVERRIDES,
                    MAX_PARTICLE_210 + 1, AMBIENT_PARTICLE_758);
    /** 758のブロックID → 210のブロック数値ID。 */
    private static final int[] BLOCK_758_TO_210_MAP =
            expand(BLOCK_210_RANGES, BLOCK_210_OVERRIDES, MAX_BLOCK_758 + 1, STONE_BLOCK_210);
    /** 210のブロック数値ID → 758のブロックID。 */
    private static final int[] BLOCK_210_TO_758_MAP =
            expand(BLOCK_210_TO_758_RANGES, BLOCK_210_TO_758_OVERRIDES, MAX_BLOCK_210 + 1,
                    STONE_BLOCK_758);
    /** 758のブロックエンティティ種別ID → 210のブロックエンティティ種別ID。無い種別は DROP。 */
    private static final int[] BLOCK_ENTITY_758_TO_210_MAP =
            expand(BLOCK_ENTITY_210_RANGES, BLOCK_ENTITY_210_OVERRIDES, MAX_BLOCK_ENTITY_758 + 1,
                    DROP);
    /** 210のブロックエンティティ種別ID → 758のブロックエンティティ種別ID。 */
    private static final int[] BLOCK_ENTITY_210_TO_758_MAP =
            expand(BLOCK_ENTITY_210_TO_758_RANGES, BLOCK_ENTITY_210_TO_758_OVERRIDES,
                    MAX_BLOCK_ENTITY_210 + 1, DROP);
    /** 758のバイオームID → 210のバイオームID。 */
    private static final int[] BIOME_758_TO_210_MAP =
            expand(BIOME_210_RANGES, BIOME_210_OVERRIDES, MAX_BIOME_758 + 1, PLAINS_BIOME_210);
    /** 210のバイオームID → 758のバイオームID。 */
    private static final int[] BIOME_210_TO_758_MAP =
            expand(BIOME_210_TO_758_RANGES, BIOME_210_TO_758_OVERRIDES, MAX_BIOME_210 + 1,
                    PLAINS_BIOME_758);
    /** 758のダメージ種別ID → 210のダメージ種別ID（316 と同じ表。codec には無い）。 */
    private static final int[] DAMAGE_TYPE_758_TO_210_MAP =
            expand(DAMAGE_TYPE_210_RANGES, DAMAGE_TYPE_210_OVERRIDES, MAX_DAMAGE_TYPE_758 + 1,
                    GENERIC_DAMAGE_TYPE_210);
    /** 210のダメージ種別ID → 758のダメージ種別ID（316 と同じ表。codec には無い）。 */
    private static final int[] DAMAGE_TYPE_210_TO_758_MAP =
            expand(DAMAGE_TYPE_210_TO_758_RANGES, DAMAGE_TYPE_210_TO_758_OVERRIDES,
                    MAX_DAMAGE_TYPE_210 + 1, GENERIC_DAMAGE_TYPE_758);
""")

    # 4) 音の名前フォールバック表
    text = insert_after(
        text,
        """    /** 316の音の名前 → 758の音の名前（Stop Sound 用）。数値の合成表と一致させる。 */
    private static final Map<String, String> SOUND_NAME_FALLBACK_316_TO_758_MAP =
            buildSoundNameMap(SOUND_NAME_FALLBACKS_316_TO_758);
""",
        """
    /** 758の音の名前 → 210の音の名前（Stop Sound 用）。数値の合成表と一致させる。 */
    private static final Map<String, String> SOUND_NAME_FALLBACK_758_TO_210_MAP =
            buildSoundNameMap(SOUND_NAME_FALLBACKS_758_TO_210);

    /** 210の音の名前 → 758の音の名前（Stop Sound 用）。数値の合成表と一致させる。 */
    private static final Map<String, String> SOUND_NAME_FALLBACK_210_TO_758_MAP =
            buildSoundNameMap(SOUND_NAME_FALLBACKS_210_TO_758);
""")

    # 5) 変換メソッド（315 の節の後）
    text = insert_after(
        text,
        """    /** 315の音の名前 → 758の音の名前（Stop Sound 用）。316 と同一。 */
    public static String mapSoundName315To758(String name) {
        return mapSoundName316To758(name);
    }
""",
        """
    // 210 ⇔ 758（1.10／1.10.1／1.10.2 ⇔ 1.18.2）
    // 210 は 315／316 と同じ Flattening 前の世代で、315／316 との
    // 差分は 1.11 追加分だけ（ブロック 17 種・アイテム 21 件・
    // エンティティ新規 4 種と亜種 9 種・音 31 件・パーティクル
    // 2 件・ブロックエンティティ shulker_box）。意味対応表は
    // RegistryTables210。生成と検証は tools/gen-210-tables.py。
    // メニューは 210 にレジストリが無いため写像を作らない。

    /** 758のブロック状態ID → 210の (数値ID<<4)|メタデータ。範囲外は石。 */
    public static int mapBlockState758To210(int id758) {
        if (id758 < 0 || id758 >= BLOCK_STATE_758_TO_210_MAP.length) {
            logUnknownComposedOnce("758→210", "ブロック状態", id758);
            return STONE_BLOCK_STATE_210;
        }
        return BLOCK_STATE_758_TO_210_MAP[id758];
    }

    /** 210の (数値ID<<4)|メタデータ → 758のブロック状態ID。範囲外は石。 */
    public static int mapBlockState210To758(int value210) {
        if (value210 < 0 || value210 >= BLOCK_STATE_210_TO_758_MAP.length) {
            logUnknownComposedOnce("210→758", "ブロック状態", value210);
            return STONE_BLOCK_STATE_758;
        }
        return BLOCK_STATE_210_TO_758_MAP[value210];
    }

    /** 758のアイテムID → 210のアイテムID。範囲外は石アイテム。 */
    public static int mapItem758To210(int id758) {
        if (id758 < 0 || id758 >= ITEM_758_TO_210_MAP.length) {
            logUnknownComposedOnce("758→210", "アイテム", id758);
            return STONE_ITEM_210;
        }
        return ITEM_758_TO_210_MAP[id758];
    }

    /** 210のアイテムID → 758のアイテムID。範囲外は石アイテム。 */
    public static int mapItem210To758(int id210) {
        if (id210 < 0 || id210 >= ITEM_210_TO_758_MAP.length) {
            logUnknownComposedOnce("210→758", "アイテム", id210);
            return STONE_ITEM_758;
        }
        return ITEM_210_TO_758_MAP[id210];
    }

    /** 758のエンティティ種別ID → 210のエンティティ種別ID。範囲外はブタ。 */
    public static int mapEntityType758To210(int id758) {
        if (id758 < 0 || id758 >= ENTITY_TYPE_758_TO_210_MAP.length) {
            logUnknownComposedOnce("758→210", "エンティティ種別", id758);
            return PIG_ENTITY_TYPE_210;
        }
        return ENTITY_TYPE_758_TO_210_MAP[id758];
    }

    /** 210のエンティティ種別ID → 758のエンティティ種別ID。範囲外はブタ。 */
    public static int mapEntityType210To758(int id210) {
        if (id210 < 0 || id210 >= ENTITY_TYPE_210_TO_758_MAP.length) {
            logUnknownComposedOnce("210→758", "エンティティ種別", id210);
            return PIG_ENTITY_TYPE_758;
        }
        return ENTITY_TYPE_210_TO_758_MAP[id210];
    }

    /** 758の音ID → 210の音ID。範囲外は汎用音。 */
    public static int mapSound758To210(int id758) {
        if (id758 < 0 || id758 >= SOUND_758_TO_210_MAP.length) {
            logUnknownComposedOnce("758→210", "音", id758);
            return GENERIC_SOUND_210;
        }
        return SOUND_758_TO_210_MAP[id758];
    }

    /** 210の音ID → 758の音ID。範囲外は汎用音。 */
    public static int mapSound210To758(int id210) {
        if (id210 < 0 || id210 >= SOUND_210_TO_758_MAP.length) {
            logUnknownComposedOnce("210→758", "音", id210);
            return GENERIC_SOUND_758;
        }
        return SOUND_210_TO_758_MAP[id210];
    }

    /** 758のパーティクルID → 210のパーティクルID。範囲外は explode。 */
    public static int mapParticle758To210(int id758) {
        if (id758 < 0 || id758 >= PARTICLE_758_TO_210_MAP.length) {
            logUnknownComposedOnce("758→210", "パーティクル", id758);
            return AMBIENT_PARTICLE_210;
        }
        return PARTICLE_758_TO_210_MAP[id758];
    }

    /** 210のパーティクルID → 758のパーティクルID。範囲外は ambient 代替。 */
    public static int mapParticle210To758(int id210) {
        if (id210 < 0 || id210 >= PARTICLE_210_TO_758_MAP.length) {
            logUnknownComposedOnce("210→758", "パーティクル", id210);
            return AMBIENT_PARTICLE_758;
        }
        return PARTICLE_210_TO_758_MAP[id210];
    }

    /** 758のブロックID → 210のブロック数値ID。範囲外は石。 */
    public static int mapBlock758To210(int id758) {
        if (id758 < 0 || id758 >= BLOCK_758_TO_210_MAP.length) {
            logUnknownComposedOnce("758→210", "ブロック", id758);
            return STONE_BLOCK_210;
        }
        return BLOCK_758_TO_210_MAP[id758];
    }

    /** 210のブロック数値ID → 758のブロックID。範囲外は石。 */
    public static int mapBlock210To758(int id210) {
        if (id210 < 0 || id210 >= BLOCK_210_TO_758_MAP.length) {
            logUnknownComposedOnce("210→758", "ブロック", id210);
            return STONE_BLOCK_758;
        }
        return BLOCK_210_TO_758_MAP[id210];
    }

    /** 758のブロックエンティティ種別ID → 210の種別ID。無い種別は DROP（-1）。 */
    public static int mapBlockEntityType758To210(int id758) {
        if (id758 < 0 || id758 >= BLOCK_ENTITY_758_TO_210_MAP.length) {
            logUnknownComposedOnce("758→210", "ブロックエンティティ", id758);
            return DROP;
        }
        return BLOCK_ENTITY_758_TO_210_MAP[id758];
    }

    /** 210のブロックエンティティ種別ID → 758の種別ID。範囲外は DROP（-1）。 */
    public static int mapBlockEntityType210To758(int id210) {
        if (id210 < 0 || id210 >= BLOCK_ENTITY_210_TO_758_MAP.length) {
            logUnknownComposedOnce("210→758", "ブロックエンティティ", id210);
            return DROP;
        }
        return BLOCK_ENTITY_210_TO_758_MAP[id210];
    }

    /** 758のバイオームID → 210のバイオームID。範囲外は平原。 */
    public static int mapBiome758To210(int id758) {
        if (id758 < 0 || id758 >= BIOME_758_TO_210_MAP.length) {
            logUnknownComposedOnce("758→210", "バイオーム", id758);
            return PLAINS_BIOME_210;
        }
        return BIOME_758_TO_210_MAP[id758];
    }

    /** 210のバイオームID → 758のバイオームID。範囲外は平原。 */
    public static int mapBiome210To758(int id210) {
        if (id210 < 0 || id210 >= BIOME_210_TO_758_MAP.length) {
            logUnknownComposedOnce("210→758", "バイオーム", id210);
            return PLAINS_BIOME_758;
        }
        return BIOME_210_TO_758_MAP[id210];
    }

    /** 210 のバイオーム名（レジストリ順、疎な ID は plains で埋めた配列）。 */
    public static java.util.List<String> biomeNames210() {
        return java.util.List.of(RegistryTables210.BIOME_NAMES_210);
    }

    /** 758のダメージ種別ID → 210のダメージ種別ID。 */
    public static int mapDamageType758To210(int id758) {
        if (id758 < 0 || id758 >= DAMAGE_TYPE_758_TO_210_MAP.length) {
            logUnknownComposedOnce("758→210", "ダメージ種別", id758);
            return GENERIC_DAMAGE_TYPE_210;
        }
        return DAMAGE_TYPE_758_TO_210_MAP[id758];
    }

    /** 210のダメージ種別ID → 758のダメージ種別ID。 */
    public static int mapDamageType210To758(int id210) {
        if (id210 < 0 || id210 >= DAMAGE_TYPE_210_TO_758_MAP.length) {
            logUnknownComposedOnce("210→758", "ダメージ種別", id210);
            return GENERIC_DAMAGE_TYPE_758;
        }
        return DAMAGE_TYPE_210_TO_758_MAP[id210];
    }

    /** 758の音の名前 → 210の音の名前（Stop Sound 用）。数値の合成表と一致させる。 */
    public static String mapSoundName758To210(String name) {
        return mapSoundNameComposed(name, SOUND_NAME_FALLBACK_758_TO_210_MAP);
    }

    /** 210の音の名前 → 758の音の名前（Stop Sound 用）。数値の合成表と一致させる。 */
    public static String mapSoundName210To758(String name) {
        return mapSoundNameComposed(name, SOUND_NAME_FALLBACK_210_TO_758_MAP);
    }
""")

    FILE.write_text(text, encoding="utf-8")
    print("RegistryRemapper に 210 の定数・対応表・変換メソッドを追加した。")
    return 0


if __name__ == "__main__":
    sys.exit(main())
