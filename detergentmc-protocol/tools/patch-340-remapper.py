#!/usr/bin/env python3
"""RegistryRemapper.java に 340（1.12.2）の定数・展開済み表・写像メソッドを足す。

表の実体は RegistryTables340（gen-340-tables.py が生成）にあり、
393 の節と同じ形で static import して使う。メニューは 340 に
レジストリが無いため写像メソッドを作らない。絵画は 340 では
タイトル文字列で送るため数値表は作らない（ProxySession 側で変換）。
効果は 340 では ID 1〜27（1 始まり）で、定数だけ置く。
"""
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
P = ROOT / "src/main/java/io/detergentmc/protocol/RegistryRemapper.java"


def replace_once(text, old, new):
    if old not in text:
        print(f"NG: 置換対象が見つからない: {old[:70]}...", file=sys.stderr)
        sys.exit(1)
    return text.replace(old, new, 1)


def main():
    text = P.read_text(encoding="utf-8")
    if "PROTOCOL_340" in text:
        print("skip（既存）: 340 はすでにある")
        return 0

    # 1. static import。
    text = replace_once(
        text,
        "import static io.detergentmc.protocol.RegistryTables393.*;",
        "import static io.detergentmc.protocol.RegistryTables340.*;\n"
        "import static io.detergentmc.protocol.RegistryTables393.*;")

    # 2. プロトコル番号。
    text = replace_once(
        text,
        "    /** 393（1.13）のプロトコル番号。 */\n"
        "    public static final int PROTOCOL_393 = 393;",
        "    /** 340（1.12.2）のプロトコル番号。 */\n"
        "    public static final int PROTOCOL_340 = 340;\n"
        "    /** 393（1.13）のプロトコル番号。 */\n"
        "    public static final int PROTOCOL_393 = 393;")

    # 3. 定数（393 の定数ブロックの前）。
    constants = """
    /**
     * 340（1.12.2）側の最大の番号。ブロック状態は (数値ID&lt;&lt;4)|メタデータ
     * の 4096 通り。アイテムは minecraft-data 1.12 の最大 ID（2267、疎）。
     * エンティティ（登録 ID 1〜200、83 件）と音（549 件）は実バニラ
     * 1.12.2 jar のバイトコードから抽出した登録順（tools/entities-340.json、
     * sounds-340.json）。パーティクルは minecraft-data 1.12 の 49 件。
     * バイオームは数値 ID 0〜167（62 件、疎）。
     */
    public static final int MAX_BLOCK_STATE_340 = 4095;
    public static final int MAX_ITEM_340 = 2267;
    public static final int MAX_ENTITY_TYPE_340 = 200;
    public static final int MAX_SOUND_340 = 548;
    public static final int MAX_PARTICLE_340 = 48;
    public static final int MAX_BLOCK_340 = 255;
    /** 340側のバイオームは 0〜167（62件、疎）。 */
    public static final int MAX_BIOME_340 = 167;
    /** 340側のダメージ種別は 0〜41（42件。393 と同じ表を使う）。 */
    public static final int MAX_DAMAGE_TYPE_340 = 41;
    /** 340側のブロックエンティティ種別は 0〜23（24件。conduit が無い）。 */
    public static final int MAX_BLOCK_ENTITY_340 = 23;
    /** 340側の効果は ID 1〜27（1始まり。ワイヤ値の最大）。 */
    public static final int MAX_EFFECT_340 = 27;

    /** 340側の代替先。ブロック状態は (数値ID<<4)|メタデータ形式。 */
    public static final int STONE_BLOCK_STATE_340 = 16; // 石 (1<<4)|0
    public static final int AIR_BLOCK_STATE_340 = 0;
    public static final int STONE_ITEM_340 = 1;
    public static final int PIG_ENTITY_TYPE_340 = 90;
    public static final int GENERIC_SOUND_340 = 450; // block.stone.break
    public static final int AMBIENT_PARTICLE_340 = 0; // explode（340 に ambient 系が無い）
    public static final int STONE_BLOCK_340 = 1;
    /** 340側の平原バイオーム。 */
    public static final int PLAINS_BIOME_340 = 1;
    /** 340側の generic ダメージ種別（393 と同一）。 */
    public static final int GENERIC_DAMAGE_TYPE_340 = 16;
"""
    text = replace_once(
        text,
        "    /**\n     * 393（1.13）側の最大の番号。",
        constants + "\n    /**\n     * 393（1.13）側の最大の番号。")

    # 4. 展開済み表（393 の表の前）。
    maps = """
    /** 758のブロック状態ID → 340の (数値ID<<4)|メタデータ（RegistryTables340）。 */
    private static final int[] BLOCK_STATE_758_TO_340_MAP =
            expand(BLOCK_STATE_340_RANGES, BLOCK_STATE_340_OVERRIDES, MAX_BLOCK_STATE_758 + 1,
                    STONE_BLOCK_STATE_340);
    /** 340の (数値ID<<4)|メタデータ → 758のブロック状態ID（RegistryTables340）。 */
    private static final int[] BLOCK_STATE_340_TO_758_MAP =
            expand(BLOCK_STATE_340_TO_758_RANGES, BLOCK_STATE_340_TO_758_OVERRIDES,
                    MAX_BLOCK_STATE_340 + 1, STONE_BLOCK_STATE_758);
    /** 758のアイテムID → 340のアイテムID。 */
    private static final int[] ITEM_758_TO_340_MAP =
            expand(ITEM_340_RANGES, ITEM_340_OVERRIDES, MAX_ITEM_758 + 1, STONE_ITEM_340);
    /** 340のアイテムID → 758のアイテムID。 */
    private static final int[] ITEM_340_TO_758_MAP =
            expand(ITEM_340_TO_758_RANGES, ITEM_340_TO_758_OVERRIDES, MAX_ITEM_340 + 1,
                    STONE_ITEM_758);
    /** 758のエンティティ種別ID → 340のエンティティ種別ID。 */
    private static final int[] ENTITY_TYPE_758_TO_340_MAP =
            expand(ENTITY_TYPE_340_RANGES, ENTITY_TYPE_340_OVERRIDES, MAX_ENTITY_TYPE_758 + 1,
                    PIG_ENTITY_TYPE_340);
    /** 340のエンティティ種別ID → 758のエンティティ種別ID。 */
    private static final int[] ENTITY_TYPE_340_TO_758_MAP =
            expand(ENTITY_TYPE_340_TO_758_RANGES, ENTITY_TYPE_340_TO_758_OVERRIDES,
                    MAX_ENTITY_TYPE_340 + 1, PIG_ENTITY_TYPE_758);
    /** 758の音ID → 340の音ID。 */
    private static final int[] SOUND_758_TO_340_MAP =
            expand(SOUND_340_RANGES, SOUND_340_OVERRIDES, MAX_SOUND_758 + 1, GENERIC_SOUND_340);
    /** 340の音ID → 758の音ID。 */
    private static final int[] SOUND_340_TO_758_MAP =
            expand(SOUND_340_TO_758_RANGES, SOUND_340_TO_758_OVERRIDES, MAX_SOUND_340 + 1,
                    GENERIC_SOUND_758);
    /** 758のパーティクルID → 340のパーティクルID。 */
    private static final int[] PARTICLE_758_TO_340_MAP =
            expand(PARTICLE_340_RANGES, PARTICLE_340_OVERRIDES, MAX_PARTICLE_758 + 1,
                    AMBIENT_PARTICLE_340);
    /** 340のパーティクルID → 758のパーティクルID。 */
    private static final int[] PARTICLE_340_TO_758_MAP =
            expand(PARTICLE_340_TO_758_RANGES, PARTICLE_340_TO_758_OVERRIDES,
                    MAX_PARTICLE_340 + 1, AMBIENT_PARTICLE_758);
    /** 758のブロックID → 340のブロック数値ID。 */
    private static final int[] BLOCK_758_TO_340_MAP =
            expand(BLOCK_340_RANGES, BLOCK_340_OVERRIDES, MAX_BLOCK_758 + 1, STONE_BLOCK_340);
    /** 340のブロック数値ID → 758のブロックID。 */
    private static final int[] BLOCK_340_TO_758_MAP =
            expand(BLOCK_340_TO_758_RANGES, BLOCK_340_TO_758_OVERRIDES, MAX_BLOCK_340 + 1,
                    STONE_BLOCK_758);
    /** 758のブロックエンティティ種別ID → 340のブロックエンティティ種別ID。無い種別は DROP。 */
    private static final int[] BLOCK_ENTITY_758_TO_340_MAP =
            expand(BLOCK_ENTITY_340_RANGES, BLOCK_ENTITY_340_OVERRIDES, MAX_BLOCK_ENTITY_758 + 1,
                    DROP);
    /** 340のブロックエンティティ種別ID → 758のブロックエンティティ種別ID。 */
    private static final int[] BLOCK_ENTITY_340_TO_758_MAP =
            expand(BLOCK_ENTITY_340_TO_758_RANGES, BLOCK_ENTITY_340_TO_758_OVERRIDES,
                    MAX_BLOCK_ENTITY_340 + 1, DROP);
    /** 758のバイオームID → 340のバイオームID。 */
    private static final int[] BIOME_758_TO_340_MAP =
            expand(BIOME_340_RANGES, BIOME_340_OVERRIDES, MAX_BIOME_758 + 1, PLAINS_BIOME_340);
    /** 340のバイオームID → 758のバイオームID。 */
    private static final int[] BIOME_340_TO_758_MAP =
            expand(BIOME_340_TO_758_RANGES, BIOME_340_TO_758_OVERRIDES, MAX_BIOME_340 + 1,
                    PLAINS_BIOME_758);
    /** 758のダメージ種別ID → 340のダメージ種別ID（393 と同じ表。codec には無い）。 */
    private static final int[] DAMAGE_TYPE_758_TO_340_MAP =
            expand(DAMAGE_TYPE_340_RANGES, DAMAGE_TYPE_340_OVERRIDES, MAX_DAMAGE_TYPE_758 + 1,
                    GENERIC_DAMAGE_TYPE_340);
    /** 340のダメージ種別ID → 758のダメージ種別ID（393 と同じ表。codec には無い）。 */
    private static final int[] DAMAGE_TYPE_340_TO_758_MAP =
            expand(DAMAGE_TYPE_340_TO_758_RANGES, DAMAGE_TYPE_340_TO_758_OVERRIDES,
                    MAX_DAMAGE_TYPE_340 + 1, GENERIC_DAMAGE_TYPE_758);
"""
    text = replace_once(
        text,
        "    /** 758のブロック状態ID → 393のブロック状態ID（RegistryTables393）。 */",
        maps + "    /** 758のブロック状態ID → 393のブロック状態ID（RegistryTables393）。 */")

    # 5. 音の名前表の Map 化（393 の前）。
    text = replace_once(
        text,
        "    /** 758の音の名前 → 393の音の名前（Stop Sound 用）。数値の合成表と一致させる。 */",
        "    /** 758の音の名前 → 340の音の名前（Stop Sound 用）。数値の合成表と一致させる。 */\n"
        "    private static final Map<String, String> SOUND_NAME_FALLBACK_758_TO_340_MAP =\n"
        "            buildSoundNameMap(SOUND_NAME_FALLBACKS_758_TO_340);\n\n"
        "    /** 340の音の名前 → 758の音の名前（Stop Sound 用）。数値の合成表と一致させる。 */\n"
        "    private static final Map<String, String> SOUND_NAME_FALLBACK_340_TO_758_MAP =\n"
        "            buildSoundNameMap(SOUND_NAME_FALLBACKS_340_TO_758);\n\n"
        "    /** 758の音の名前 → 393の音の名前（Stop Sound 用）。数値の合成表と一致させる。 */")

    # 6. 写像メソッド（393 の節の前）。
    methods = """
    // 340 ⇔ 758（1.12.2 ⇔ 1.18.2）
    // 340 は Flattening 前で、ブロック状態は (数値ID<<4)|メタデータの
    // 意味対応表（RegistryTables340）を使う。生成と検証は
    // tools/gen-340-tables.py／tools/verify-340-tables.py。
    // メニューは 340 にレジストリが無いため写像を作らない。

    /** 758のブロック状態ID → 340の (数値ID<<4)|メタデータ。範囲外は石。 */
    public static int mapBlockState758To340(int id758) {
        if (id758 < 0 || id758 >= BLOCK_STATE_758_TO_340_MAP.length) {
            logUnknownComposedOnce("758→340", "ブロック状態", id758);
            return STONE_BLOCK_STATE_340;
        }
        return BLOCK_STATE_758_TO_340_MAP[id758];
    }

    /** 340の (数値ID<<4)|メタデータ → 758のブロック状態ID。範囲外は石。 */
    public static int mapBlockState340To758(int value340) {
        if (value340 < 0 || value340 >= BLOCK_STATE_340_TO_758_MAP.length) {
            logUnknownComposedOnce("340→758", "ブロック状態", value340);
            return STONE_BLOCK_STATE_758;
        }
        return BLOCK_STATE_340_TO_758_MAP[value340];
    }

    /** 758のアイテムID → 340のアイテムID。範囲外は石。 */
    public static int mapItem758To340(int id758) {
        if (id758 < 0 || id758 >= ITEM_758_TO_340_MAP.length) {
            logUnknownComposedOnce("758→340", "アイテム", id758);
            return STONE_ITEM_340;
        }
        return ITEM_758_TO_340_MAP[id758];
    }

    /** 340のアイテムID → 758のアイテムID。範囲外は石。 */
    public static int mapItem340To758(int id340) {
        if (id340 < 0 || id340 >= ITEM_340_TO_758_MAP.length) {
            logUnknownComposedOnce("340→758", "アイテム", id340);
            return STONE_ITEM_758;
        }
        return ITEM_340_TO_758_MAP[id340];
    }

    /** 758のエンティティ種別ID → 340のエンティティ種別ID。範囲外はブタ。 */
    public static int mapEntityType758To340(int id758) {
        if (id758 < 0 || id758 >= ENTITY_TYPE_758_TO_340_MAP.length) {
            logUnknownComposedOnce("758→340", "エンティティ種別", id758);
            return PIG_ENTITY_TYPE_340;
        }
        return ENTITY_TYPE_758_TO_340_MAP[id758];
    }

    /** 340のエンティティ種別ID → 758のエンティティ種別ID。範囲外はブタ。 */
    public static int mapEntityType340To758(int id340) {
        if (id340 < 0 || id340 >= ENTITY_TYPE_340_TO_758_MAP.length) {
            logUnknownComposedOnce("340→758", "エンティティ種別", id340);
            return PIG_ENTITY_TYPE_758;
        }
        return ENTITY_TYPE_340_TO_758_MAP[id340];
    }

    /** 758の音ID → 340の音ID。範囲外は汎用の音。 */
    public static int mapSound758To340(int id758) {
        if (id758 < 0 || id758 >= SOUND_758_TO_340_MAP.length) {
            logUnknownComposedOnce("758→340", "音", id758);
            return GENERIC_SOUND_340;
        }
        return SOUND_758_TO_340_MAP[id758];
    }

    /** 340の音ID → 758の音ID。範囲外は汎用の音。 */
    public static int mapSound340To758(int id340) {
        if (id340 < 0 || id340 >= SOUND_340_TO_758_MAP.length) {
            logUnknownComposedOnce("340→758", "音", id340);
            return GENERIC_SOUND_758;
        }
        return SOUND_340_TO_758_MAP[id340];
    }

    /** 758のパーティクルID → 340のパーティクルID。範囲外は代替パーティクル。 */
    public static int mapParticle758To340(int id758) {
        if (id758 < 0 || id758 >= PARTICLE_758_TO_340_MAP.length) {
            logUnknownComposedOnce("758→340", "パーティクル", id758);
            return AMBIENT_PARTICLE_340;
        }
        return PARTICLE_758_TO_340_MAP[id758];
    }

    /** 340のパーティクルID → 758のパーティクルID。範囲外は代替パーティクル。 */
    public static int mapParticle340To758(int id340) {
        if (id340 < 0 || id340 >= PARTICLE_340_TO_758_MAP.length) {
            logUnknownComposedOnce("340→758", "パーティクル", id340);
            return AMBIENT_PARTICLE_758;
        }
        return PARTICLE_340_TO_758_MAP[id340];
    }

    /** 758のブロックID → 340のブロック数値ID。範囲外は石。 */
    public static int mapBlock758To340(int id758) {
        if (id758 < 0 || id758 >= BLOCK_758_TO_340_MAP.length) {
            logUnknownComposedOnce("758→340", "ブロック", id758);
            return STONE_BLOCK_340;
        }
        return BLOCK_758_TO_340_MAP[id758];
    }

    /** 340のブロック数値ID → 758のブロックID。範囲外は石。 */
    public static int mapBlock340To758(int id340) {
        if (id340 < 0 || id340 >= BLOCK_340_TO_758_MAP.length) {
            logUnknownComposedOnce("340→758", "ブロック", id340);
            return STONE_BLOCK_758;
        }
        return BLOCK_340_TO_758_MAP[id340];
    }

    /**
     * 758のブロックエンティティ種別ID → 340のブロックエンティティ種別ID。
     * 340に無い conduit 等は DROP。
     */
    public static int mapBlockEntityType758To340(int id758) {
        if (id758 < 0 || id758 >= BLOCK_ENTITY_758_TO_340_MAP.length) {
            logUnknownComposedOnce("758→340", "ブロックエンティティ種別", id758);
            return DROP;
        }
        return BLOCK_ENTITY_758_TO_340_MAP[id758];
    }

    /** 340のブロックエンティティ種別ID → 758のブロックエンティティ種別ID。 */
    public static int mapBlockEntityType340To758(int id340) {
        if (id340 < 0 || id340 >= BLOCK_ENTITY_340_TO_758_MAP.length) {
            logUnknownComposedOnce("340→758", "ブロックエンティティ種別", id340);
            return DROP;
        }
        return BLOCK_ENTITY_340_TO_758_MAP[id340];
    }

    /** 758のバイオームID → 340のバイオームID。範囲外は平原。 */
    public static int mapBiome758To340(int id758) {
        if (id758 < 0 || id758 >= BIOME_758_TO_340_MAP.length) {
            logUnknownComposedOnce("758→340", "バイオーム", id758);
            return PLAINS_BIOME_340;
        }
        return BIOME_758_TO_340_MAP[id758];
    }

    /** 340のバイオームID → 758のバイオームID。範囲外は平原。 */
    public static int mapBiome340To758(int id340) {
        if (id340 < 0 || id340 >= BIOME_340_TO_758_MAP.length) {
            logUnknownComposedOnce("340→758", "バイオーム", id340);
            return PLAINS_BIOME_758;
        }
        return BIOME_340_TO_758_MAP[id340];
    }

    /** 340 のバイオーム名（レジストリ順、疎な ID は plains で埋めた配列）。 */
    public static java.util.List<String> biomeNames340() {
        return java.util.List.of(RegistryTables340.BIOME_NAMES_340);
    }

    /** 758のダメージ種別ID → 340のダメージ種別ID（393 と同じ表。codec には無い）。 */
    public static int mapDamageType758To340(int id758) {
        if (id758 < 0 || id758 > MAX_DAMAGE_TYPE_758) {
            logUnknownOnce(PROTOCOL_340, "ダメージ種別", id758);
            return DROP;
        }
        return DAMAGE_TYPE_758_TO_340_MAP[id758];
    }

    /** 340のダメージ種別ID → 758のダメージ種別ID（393 と同じ表。codec には無い）。 */
    public static int mapDamageType340To758(int id340) {
        if (id340 < 0 || id340 > MAX_DAMAGE_TYPE_340) {
            logUnknownOnce(PROTOCOL_758, "ダメージ種別", id340);
            return DROP;
        }
        return DAMAGE_TYPE_340_TO_758_MAP[id340];
    }

    /** 758の音の名前 → 340の音の名前（Stop Sound 用）。数値の合成表と一致させる。 */
    public static String mapSoundName758To340(String name) {
        return mapSoundNameComposed(name, SOUND_NAME_FALLBACK_758_TO_340_MAP);
    }

    /** 340の音の名前 → 758の音の名前（Stop Sound 用）。数値の合成表と一致させる。 */
    public static String mapSoundName340To758(String name) {
        return mapSoundNameComposed(name, SOUND_NAME_FALLBACK_340_TO_758_MAP);
    }

"""
    text = replace_once(
        text,
        "    // 393 ⇔ 758（1.13 ⇔ 1.18.2）",
        methods + "    // 393 ⇔ 758（1.13 ⇔ 1.18.2）")

    P.write_text(text, encoding="utf-8")
    print("RegistryRemapper.java に 340 を追加した")
    return 0


if __name__ == "__main__":
    sys.exit(main())
