"""RegistryRemapper.java に 316（1.11.2）の変換を追加する。

340 のブロックを雛形に、定数・マップ・メソッドを 316 用に足す。
表の実体は RegistryTables316（gen-316-tables.py が生成）。
冪等ではない（2回実行すると二重定義でコンパイルエラーになる）。
"""

from pathlib import Path

F = (Path(__file__).parent.parent / "src" / "main" / "java"
     / "io" / "detergentmc" / "protocol" / "RegistryRemapper.java")
src = F.read_text(encoding="utf-8")


def insert_before(anchor, text):
    global src
    assert src.count(anchor) == 1, f"アンカーが一意でない: {anchor[:60]}"
    src = src.replace(anchor, text + anchor)


def insert_after(anchor, text):
    global src
    assert src.count(anchor) == 1, f"アンカーが一意でない: {anchor[:60]}"
    src = src.replace(anchor, anchor + text)


# 1. import
insert_after(
    "import static io.detergentmc.protocol.RegistryTables340.*;\n",
    "import static io.detergentmc.protocol.RegistryTables316.*;\n")

# 2. PROTOCOL_316
insert_before(
    "    /** 340（1.12.2）のプロトコル番号。 */\n"
    "    public static final int PROTOCOL_338 = 338;",
    "    /** 316（1.11.2）のプロトコル番号。 */\n"
    "    public static final int PROTOCOL_316 = 316;\n\n")

# 3. 定数
insert_after(
    "    /** 340側の generic ダメージ種別（393 と同一）。 */\n"
    "    public static final int GENERIC_DAMAGE_TYPE_340 = 16;\n",
    """
    /** 316側のブロック状態最大値（(数値ID<<4)|メタデータの全空間）。 */
    public static final int MAX_BLOCK_STATE_316 = 4095;
    /** 316側のアイテム最大ID（340 と同一。最大ID 2267 の疎な番号）。 */
    public static final int MAX_ITEM_316 = 2267;
    /** 316側のエンティティ種別最大ID。 */
    public static final int MAX_ENTITY_TYPE_316 = 200;
    /** 316側の音最大ID（340 の 548 より 56 小さい）。 */
    public static final int MAX_SOUND_316 = 492;
    /** 316側のパーティクル最大ID（340 と同一）。 */
    public static final int MAX_PARTICLE_316 = 48;
    /** 316側のブロック最大ID。 */
    public static final int MAX_BLOCK_316 = 255;
    /** 316側のバイオーム最大ID（340 と同一）。 */
    public static final int MAX_BIOME_316 = 167;
    /** 316側のダメージ種別最大ID（340 と同一）。 */
    public static final int MAX_DAMAGE_TYPE_316 = 41;
    /** 316側のブロックエンティティ種別最大ID（bed が無いため 340 より1小さい）。 */
    public static final int MAX_BLOCK_ENTITY_316 = 22;
    /** 316側の状態異常最大ID（340 と同一。ID→名前が完全一致）。 */
    public static final int MAX_EFFECT_316 = 27;

    /** 316側の石ブロック状態（(1<<4)|0）。 */
    public static final int STONE_BLOCK_STATE_316 = 16; // 石 (1<<4)|0
    /** 316側の空気ブロック状態。 */
    public static final int AIR_BLOCK_STATE_316 = 0;
    /** 316側の石アイテムID。 */
    public static final int STONE_ITEM_316 = 1;
    /** 316側のブタのエンティティ種別ID。 */
    public static final int PIG_ENTITY_TYPE_316 = 90;
    /** 316側の汎用音ID（block.stone.break。340 の 450 とは別添字）。 */
    public static final int GENERIC_SOUND_316 = 397; // block.stone.break
    /** 316側の ambient パーティクル代替（340 と同じ explode）。 */
    public static final int AMBIENT_PARTICLE_316 = 0; // explode（316 に ambient 系が無い）
    /** 316側の石ブロックID。 */
    public static final int STONE_BLOCK_316 = 1;
    /** 316側の平原バイオームID。 */
    public static final int PLAINS_BIOME_316 = 1;
    /** 316側の generic ダメージ種別（340 と同一）。 */
    public static final int GENERIC_DAMAGE_TYPE_316 = 16;
""")

# 4. マップ（340 のマップ群の直後、393 のマップ群の直前）
insert_before(
    "    /** 758のブロック状態ID → 393のブロック状態ID（RegistryTables393）。 */",
    """    /** 758のブロック状態ID → 316の (数値ID<<4)|メタデータ（RegistryTables316）。 */
    private static final int[] BLOCK_STATE_758_TO_316_MAP =
            expand(BLOCK_STATE_316_RANGES, BLOCK_STATE_316_OVERRIDES, MAX_BLOCK_STATE_758 + 1,
                    STONE_BLOCK_STATE_316);
    /** 316の (数値ID<<4)|メタデータ → 758のブロック状態ID（RegistryTables316）。 */
    private static final int[] BLOCK_STATE_316_TO_758_MAP =
            expand(BLOCK_STATE_316_TO_758_RANGES, BLOCK_STATE_316_TO_758_OVERRIDES,
                    MAX_BLOCK_STATE_316 + 1, STONE_BLOCK_STATE_758);
    /** 758のアイテムID → 316のアイテムID。 */
    private static final int[] ITEM_758_TO_316_MAP =
            expand(ITEM_316_RANGES, ITEM_316_OVERRIDES, MAX_ITEM_758 + 1, STONE_ITEM_316);
    /** 316のアイテムID → 758のアイテムID。 */
    private static final int[] ITEM_316_TO_758_MAP =
            expand(ITEM_316_TO_758_RANGES, ITEM_316_TO_758_OVERRIDES, MAX_ITEM_316 + 1,
                    STONE_ITEM_758);
    /** 758のエンティティ種別ID → 316のエンティティ種別ID。 */
    private static final int[] ENTITY_TYPE_758_TO_316_MAP =
            expand(ENTITY_TYPE_316_RANGES, ENTITY_TYPE_316_OVERRIDES, MAX_ENTITY_TYPE_758 + 1,
                    PIG_ENTITY_TYPE_316);
    /** 316のエンティティ種別ID → 758のエンティティ種別ID。 */
    private static final int[] ENTITY_TYPE_316_TO_758_MAP =
            expand(ENTITY_TYPE_316_TO_758_RANGES, ENTITY_TYPE_316_TO_758_OVERRIDES,
                    MAX_ENTITY_TYPE_316 + 1, PIG_ENTITY_TYPE_758);
    /** 758の音ID → 316の音ID。 */
    private static final int[] SOUND_758_TO_316_MAP =
            expand(SOUND_316_RANGES, SOUND_316_OVERRIDES, MAX_SOUND_758 + 1, GENERIC_SOUND_316);
    /** 316の音ID → 758の音ID。 */
    private static final int[] SOUND_316_TO_758_MAP =
            expand(SOUND_316_TO_758_RANGES, SOUND_316_TO_758_OVERRIDES, MAX_SOUND_316 + 1,
                    GENERIC_SOUND_758);
    /** 758のパーティクルID → 316のパーティクルID。 */
    private static final int[] PARTICLE_758_TO_316_MAP =
            expand(PARTICLE_316_RANGES, PARTICLE_316_OVERRIDES, MAX_PARTICLE_758 + 1,
                    AMBIENT_PARTICLE_316);
    /** 316のパーティクルID → 758のパーティクルID。 */
    private static final int[] PARTICLE_316_TO_758_MAP =
            expand(PARTICLE_316_TO_758_RANGES, PARTICLE_316_TO_758_OVERRIDES,
                    MAX_PARTICLE_316 + 1, AMBIENT_PARTICLE_758);
    /** 758のブロックID → 316のブロック数値ID。 */
    private static final int[] BLOCK_758_TO_316_MAP =
            expand(BLOCK_316_RANGES, BLOCK_316_OVERRIDES, MAX_BLOCK_758 + 1, STONE_BLOCK_316);
    /** 316のブロック数値ID → 758のブロックID。 */
    private static final int[] BLOCK_316_TO_758_MAP =
            expand(BLOCK_316_TO_758_RANGES, BLOCK_316_TO_758_OVERRIDES, MAX_BLOCK_316 + 1,
                    STONE_BLOCK_758);
    /** 758のブロックエンティティ種別ID → 316のブロックエンティティ種別ID。無い種別は DROP。 */
    private static final int[] BLOCK_ENTITY_758_TO_316_MAP =
            expand(BLOCK_ENTITY_316_RANGES, BLOCK_ENTITY_316_OVERRIDES, MAX_BLOCK_ENTITY_758 + 1,
                    DROP);
    /** 316のブロックエンティティ種別ID → 758のブロックエンティティ種別ID。 */
    private static final int[] BLOCK_ENTITY_316_TO_758_MAP =
            expand(BLOCK_ENTITY_316_TO_758_RANGES, BLOCK_ENTITY_316_TO_758_OVERRIDES,
                    MAX_BLOCK_ENTITY_316 + 1, DROP);
    /** 758のバイオームID → 316のバイオームID。 */
    private static final int[] BIOME_758_TO_316_MAP =
            expand(BIOME_316_RANGES, BIOME_316_OVERRIDES, MAX_BIOME_758 + 1, PLAINS_BIOME_316);
    /** 316のバイオームID → 758のバイオームID。 */
    private static final int[] BIOME_316_TO_758_MAP =
            expand(BIOME_316_TO_758_RANGES, BIOME_316_TO_758_OVERRIDES, MAX_BIOME_316 + 1,
                    PLAINS_BIOME_758);
    /** 758のダメージ種別ID → 316のダメージ種別ID（340 と同じ表。codec には無い）。 */
    private static final int[] DAMAGE_TYPE_758_TO_316_MAP =
            expand(DAMAGE_TYPE_316_RANGES, DAMAGE_TYPE_316_OVERRIDES, MAX_DAMAGE_TYPE_758 + 1,
                    GENERIC_DAMAGE_TYPE_316);
    /** 316のダメージ種別ID → 758のダメージ種別ID（340 と同じ表。codec には無い）。 */
    private static final int[] DAMAGE_TYPE_316_TO_758_MAP =
            expand(DAMAGE_TYPE_316_TO_758_RANGES, DAMAGE_TYPE_316_TO_758_OVERRIDES,
                    MAX_DAMAGE_TYPE_316 + 1, GENERIC_DAMAGE_TYPE_758);
""")

# 5. 音の名前マップ
insert_after(
    """    /** 340の音の名前 → 758の音の名前（Stop Sound 用）。数値の合成表と一致させる。 */
    private static final Map<String, String> SOUND_NAME_FALLBACK_340_TO_758_MAP =
            buildSoundNameMap(SOUND_NAME_FALLBACKS_340_TO_758);
""",
    """
    /** 758の音の名前 → 316の音の名前（Stop Sound 用）。数値の合成表と一致させる。 */
    private static final Map<String, String> SOUND_NAME_FALLBACK_758_TO_316_MAP =
            buildSoundNameMap(SOUND_NAME_FALLBACKS_758_TO_316);

    /** 316の音の名前 → 758の音の名前（Stop Sound 用）。数値の合成表と一致させる。 */
    private static final Map<String, String> SOUND_NAME_FALLBACK_316_TO_758_MAP =
            buildSoundNameMap(SOUND_NAME_FALLBACKS_316_TO_758);
""")

# 6. メソッド（393 のメソッド節の直前）
METHODS = """    // 316 ⇔ 758（1.11.2 ⇔ 1.18.2）
    // 316 は 340 と同じ Flattening 前の世代で、340 との差分は
    // 1.12 追加分だけ（ブロック 18 種・アイテム 20 件・エンティティ
    // 2 種・音 56 件・ブロックエンティティ bed）。意味対応表は
    // RegistryTables316。生成と検証は tools/gen-316-tables.py／
    // tools/verify-316-tables.py。メニューは 316 にレジストリが
    // 無いため写像を作らない。

    /** 758のブロック状態ID → 316の (数値ID<<4)|メタデータ。範囲外は石。 */
    public static int mapBlockState758To316(int id758) {
        if (id758 < 0 || id758 >= BLOCK_STATE_758_TO_316_MAP.length) {
            logUnknownComposedOnce("758→316", "ブロック状態", id758);
            return STONE_BLOCK_STATE_316;
        }
        return BLOCK_STATE_758_TO_316_MAP[id758];
    }

    /** 316の (数値ID<<4)|メタデータ → 758のブロック状態ID。範囲外は石。 */
    public static int mapBlockState316To758(int value316) {
        if (value316 < 0 || value316 >= BLOCK_STATE_316_TO_758_MAP.length) {
            logUnknownComposedOnce("316→758", "ブロック状態", value316);
            return STONE_BLOCK_STATE_758;
        }
        return BLOCK_STATE_316_TO_758_MAP[value316];
    }

    /** 758のアイテムID → 316のアイテムID。範囲外は石アイテム。 */
    public static int mapItem758To316(int id758) {
        if (id758 < 0 || id758 >= ITEM_758_TO_316_MAP.length) {
            logUnknownComposedOnce("758→316", "アイテム", id758);
            return STONE_ITEM_316;
        }
        return ITEM_758_TO_316_MAP[id758];
    }

    /** 316のアイテムID → 758のアイテムID。範囲外は石アイテム。 */
    public static int mapItem316To758(int id316) {
        if (id316 < 0 || id316 >= ITEM_316_TO_758_MAP.length) {
            logUnknownComposedOnce("316→758", "アイテム", id316);
            return STONE_ITEM_758;
        }
        return ITEM_316_TO_758_MAP[id316];
    }

    /** 758のエンティティ種別ID → 316のエンティティ種別ID。範囲外はブタ。 */
    public static int mapEntityType758To316(int id758) {
        if (id758 < 0 || id758 >= ENTITY_TYPE_758_TO_316_MAP.length) {
            logUnknownComposedOnce("758→316", "エンティティ種別", id758);
            return PIG_ENTITY_TYPE_316;
        }
        return ENTITY_TYPE_758_TO_316_MAP[id758];
    }

    /** 316のエンティティ種別ID → 758のエンティティ種別ID。範囲外はブタ。 */
    public static int mapEntityType316To758(int id316) {
        if (id316 < 0 || id316 >= ENTITY_TYPE_316_TO_758_MAP.length) {
            logUnknownComposedOnce("316→758", "エンティティ種別", id316);
            return PIG_ENTITY_TYPE_758;
        }
        return ENTITY_TYPE_316_TO_758_MAP[id316];
    }

    /** 758の音ID → 316の音ID。範囲外はGENERIC_SOUND_316 に相当する代替。 */
    public static int mapSound758To316(int id758) {
        if (id758 < 0 || id758 >= SOUND_758_TO_316_MAP.length) {
            logUnknownComposedOnce("758→316", "音", id758);
            return GENERIC_SOUND_316;
        }
        return SOUND_758_TO_316_MAP[id758];
    }

    /** 316の音ID → 758の音ID。範囲外はGENERIC_SOUND_758 に相当する代替。 */
    public static int mapSound316To758(int id316) {
        if (id316 < 0 || id316 >= SOUND_316_TO_758_MAP.length) {
            logUnknownComposedOnce("316→758", "音", id316);
            return GENERIC_SOUND_758;
        }
        return SOUND_316_TO_758_MAP[id316];
    }

    /** 758のパーティクルID → 316のパーティクルID。範囲外は explode。 */
    public static int mapParticle758To316(int id758) {
        if (id758 < 0 || id758 >= PARTICLE_758_TO_316_MAP.length) {
            logUnknownComposedOnce("758→316", "パーティクル", id758);
            return AMBIENT_PARTICLE_316;
        }
        return PARTICLE_758_TO_316_MAP[id758];
    }

    /** 316のパーティクルID → 758のパーティクルID。範囲外は ambient 代替。 */
    public static int mapParticle316To758(int id316) {
        if (id316 < 0 || id316 >= PARTICLE_316_TO_758_MAP.length) {
            logUnknownComposedOnce("316→758", "パーティクル", id316);
            return AMBIENT_PARTICLE_758;
        }
        return PARTICLE_316_TO_758_MAP[id316];
    }

    /** 758のブロックID → 316のブロック数値ID。範囲外は石。 */
    public static int mapBlock758To316(int id758) {
        if (id758 < 0 || id758 >= BLOCK_758_TO_316_MAP.length) {
            logUnknownComposedOnce("758→316", "ブロック", id758);
            return STONE_BLOCK_316;
        }
        return BLOCK_758_TO_316_MAP[id758];
    }

    /** 316のブロック数値ID → 758のブロックID。範囲外は石。 */
    public static int mapBlock316To758(int id316) {
        if (id316 < 0 || id316 >= BLOCK_316_TO_758_MAP.length) {
            logUnknownComposedOnce("316→758", "ブロック", id316);
            return STONE_BLOCK_758;
        }
        return BLOCK_316_TO_758_MAP[id316];
    }

    /** 758のブロックエンティティ種別ID → 316の種別ID。無い種別は DROP（-1）。 */
    public static int mapBlockEntity758To316(int id758) {
        if (id758 < 0 || id758 >= BLOCK_ENTITY_758_TO_316_MAP.length) {
            logUnknownComposedOnce("758→316", "ブロックエンティティ", id758);
            return DROP;
        }
        return BLOCK_ENTITY_758_TO_316_MAP[id758];
    }

    /** 316のブロックエンティティ種別ID → 758の種別ID。範囲外は DROP（-1）。 */
    public static int mapBlockEntity316To758(int id316) {
        if (id316 < 0 || id316 >= BLOCK_ENTITY_316_TO_758_MAP.length) {
            logUnknownComposedOnce("316→758", "ブロックエンティティ", id316);
            return DROP;
        }
        return BLOCK_ENTITY_316_TO_758_MAP[id316];
    }

    /** 758のバイオームID → 316のバイオームID。範囲外は平原。 */
    public static int mapBiome758To316(int id758) {
        if (id758 < 0 || id758 >= BIOME_758_TO_316_MAP.length) {
            logUnknownComposedOnce("758→316", "バイオーム", id758);
            return PLAINS_BIOME_316;
        }
        return BIOME_758_TO_316_MAP[id758];
    }

    /** 316のバイオームID → 758のバイオームID。範囲外は平原。 */
    public static int mapBiome316To758(int id316) {
        if (id316 < 0 || id316 >= BIOME_316_TO_758_MAP.length) {
            logUnknownComposedOnce("316→758", "バイオーム", id316);
            return PLAINS_BIOME_758;
        }
        return BIOME_316_TO_758_MAP[id316];
    }

    /** 758のダメージ種別ID → 316のダメージ種別ID（340 と同じ恒等寄り写像）。 */
    public static int mapDamageType758To316(int id758) {
        if (id758 < 0 || id758 >= DAMAGE_TYPE_758_TO_316_MAP.length) {
            logUnknownComposedOnce("758→316", "ダメージ種別", id758);
            return GENERIC_DAMAGE_TYPE_316;
        }
        return DAMAGE_TYPE_758_TO_316_MAP[id758];
    }

    /** 316のダメージ種別ID → 758のダメージ種別ID。 */
    public static int mapDamageType316To758(int id316) {
        if (id316 < 0 || id316 >= DAMAGE_TYPE_316_TO_758_MAP.length) {
            logUnknownComposedOnce("316→758", "ダメージ種別", id316);
            return GENERIC_DAMAGE_TYPE_758;
        }
        return DAMAGE_TYPE_316_TO_758_MAP[id316];
    }

    /** 758の音の名前 → 316の音の名前（Stop Sound 用）。数値の合成表と一致させる。 */
    public static String mapSoundName758To316(String name) {
        return mapSoundNameComposed(name, SOUND_NAME_FALLBACK_758_TO_316_MAP);
    }

    /** 316の音の名前 → 758の音の名前（Stop Sound 用）。数値の合成表と一致させる。 */
    public static String mapSoundName316To758(String name) {
        return mapSoundNameComposed(name, SOUND_NAME_FALLBACK_316_TO_758_MAP);
    }

"""
insert_before("    // 393 ⇔ 758（1.13 ⇔ 1.18.2）", METHODS)

F.write_text(src, encoding="utf-8")
print("RegistryRemapper に 316 を追加した")
