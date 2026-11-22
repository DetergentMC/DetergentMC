#!/usr/bin/env python3
"""RegistryRemapper.java に 404（1.13.2）の定数・展開済み表・写像メソッドを足す。

表の実体は RegistryTables404（gen-404-tables.py が生成）にあり、
498 の節と同じ形で static import して使う。メニューは 404 に
レジストリが無いため写像メソッドを作らない。効果も表は作らず
（404 は 758 の先頭 30 件と一致）、定数だけ置く。
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
    if "PROTOCOL_404" in text:
        print("skip（既存）: 404 はすでにある")
        return 0

    # 1. static import。
    text = replace_once(
        text,
        "import static io.detergentmc.protocol.RegistryTables498.*;",
        "import static io.detergentmc.protocol.RegistryTables404.*;\n"
        "import static io.detergentmc.protocol.RegistryTables498.*;")

    # 2. プロトコル番号。
    text = replace_once(
        text,
        "    /** 477（1.14）のプロトコル番号。 */\n"
        "    public static final int PROTOCOL_477 = 477;",
        "    /** 404（1.13.2）のプロトコル番号。 */\n"
        "    public static final int PROTOCOL_404 = 404;\n"
        "    /** 477（1.14）のプロトコル番号。 */\n"
        "    public static final int PROTOCOL_477 = 477;")

    # 3. 定数（498 の定数ブロックの後、490 の前）。
    constants = """
    /**
     * 404（1.13.2）側の最大の番号。ブロック／ブロック状態／アイテムは
     * 実バニラ 1.13.2 jar の data-generator ダンプ、エンティティと
     * バイオームは minecraft-data 1.13.2、音とパーティクルは
     * ViaVersion mapping-1.13 の件数−1。
     */
    public static final int MAX_BLOCK_STATE_404 = 8598;
    public static final int MAX_ITEM_404 = 789;
    public static final int MAX_ENTITY_TYPE_404 = 94;
    public static final int MAX_SOUND_404 = 661;
    public static final int MAX_PARTICLE_404 = 49;
    public static final int MAX_BLOCK_404 = 597;
    /** 404側のバイオームは 0〜72（73件）。 */
    public static final int MAX_BIOME_404 = 72;
    /** 404側のダメージ種別は 0〜41（42件。758 と同じく codec には無いため恒等写像）。 */
    public static final int MAX_DAMAGE_TYPE_404 = 41;
    /** 404側のブロックエンティティ種別は 0〜24（25件）。 */
    public static final int MAX_BLOCK_ENTITY_404 = 24;
    /** 404側の絵画は 0〜25（26件）。758 と同じ顔ぶれ・同じ順序のため表は作らない。 */
    public static final int MAX_PAINTING_VARIANT_404 = 25;
    /** 404側の効果は 0〜29（30件）。758 の先頭 30 件と同じため表は作らない。 */
    public static final int MAX_EFFECT_404 = 29;

    /** 404側の代替先。 */
    public static final int STONE_BLOCK_STATE_404 = 1;
    public static final int AIR_BLOCK_STATE_404 = 0;
    public static final int STONE_ITEM_404 = 1;
    public static final int PIG_ENTITY_TYPE_404 = 51;
    public static final int GENERIC_SOUND_404 = 135; // block.stone.break
    public static final int AMBIENT_PARTICLE_404 = 0; // ambient_entity_effect
    public static final int STONE_BLOCK_404 = 1;
    /** 404側の平原バイオーム（minecraft-data pc/1.13.2/biomes.json）。 */
    public static final int PLAINS_BIOME_404 = 1;
    /** 404側の generic ダメージ種別（762 と同一順序と仮定）。 */
    public static final int GENERIC_DAMAGE_TYPE_404 = 16;
"""
    text = replace_once(
        text,
        "    /**\n     * 490（1.14.3）側の最大の番号と代替先。",
        constants + "\n    /**\n     * 490（1.14.3）側の最大の番号と代替先。")

    # 4. 展開済み表（498 の表の後、759 の表の前）。
    maps = """
    /** 758のブロック状態ID → 404のブロック状態ID（RegistryTables404）。 */
    private static final int[] BLOCK_STATE_758_TO_404_MAP =
            expand(BLOCK_STATE_404_RANGES, BLOCK_STATE_404_OVERRIDES, MAX_BLOCK_STATE_758 + 1,
                    STONE_BLOCK_STATE_404);
    /** 404のブロック状態ID → 758のブロック状態ID（RegistryTables404）。 */
    private static final int[] BLOCK_STATE_404_TO_758_MAP =
            expand(BLOCK_STATE_404_TO_758_RANGES, BLOCK_STATE_404_TO_758_OVERRIDES,
                    MAX_BLOCK_STATE_404 + 1, STONE_BLOCK_STATE_758);
    /** 758のアイテムID → 404のアイテムID。 */
    private static final int[] ITEM_758_TO_404_MAP =
            expand(ITEM_404_RANGES, ITEM_404_OVERRIDES, MAX_ITEM_758 + 1, STONE_ITEM_404);
    /** 404のアイテムID → 758のアイテムID。 */
    private static final int[] ITEM_404_TO_758_MAP =
            expand(ITEM_404_TO_758_RANGES, ITEM_404_TO_758_OVERRIDES, MAX_ITEM_404 + 1,
                    STONE_ITEM_758);
    /** 758のエンティティ種別ID → 404のエンティティ種別ID。 */
    private static final int[] ENTITY_TYPE_758_TO_404_MAP =
            expand(ENTITY_TYPE_404_RANGES, ENTITY_TYPE_404_OVERRIDES, MAX_ENTITY_TYPE_758 + 1,
                    PIG_ENTITY_TYPE_404);
    /** 404のエンティティ種別ID → 758のエンティティ種別ID。 */
    private static final int[] ENTITY_TYPE_404_TO_758_MAP =
            expand(ENTITY_TYPE_404_TO_758_RANGES, ENTITY_TYPE_404_TO_758_OVERRIDES,
                    MAX_ENTITY_TYPE_404 + 1, PIG_ENTITY_TYPE_758);
    /** 758の音ID → 404の音ID。 */
    private static final int[] SOUND_758_TO_404_MAP =
            expand(SOUND_404_RANGES, SOUND_404_OVERRIDES, MAX_SOUND_758 + 1, GENERIC_SOUND_404);
    /** 404の音ID → 758の音ID。 */
    private static final int[] SOUND_404_TO_758_MAP =
            expand(SOUND_404_TO_758_RANGES, SOUND_404_TO_758_OVERRIDES, MAX_SOUND_404 + 1,
                    GENERIC_SOUND_758);
    /** 758のパーティクルID → 404のパーティクルID。 */
    private static final int[] PARTICLE_758_TO_404_MAP =
            expand(PARTICLE_404_RANGES, PARTICLE_404_OVERRIDES, MAX_PARTICLE_758 + 1,
                    AMBIENT_PARTICLE_404);
    /** 404のパーティクルID → 758のパーティクルID。 */
    private static final int[] PARTICLE_404_TO_758_MAP =
            expand(PARTICLE_404_TO_758_RANGES, PARTICLE_404_TO_758_OVERRIDES,
                    MAX_PARTICLE_404 + 1, AMBIENT_PARTICLE_758);
    /** 758のブロックID → 404のブロックID。 */
    private static final int[] BLOCK_758_TO_404_MAP =
            expand(BLOCK_404_RANGES, BLOCK_404_OVERRIDES, MAX_BLOCK_758 + 1, STONE_BLOCK_404);
    /** 404のブロックID → 758のブロックID。 */
    private static final int[] BLOCK_404_TO_758_MAP =
            expand(BLOCK_404_TO_758_RANGES, BLOCK_404_TO_758_OVERRIDES, MAX_BLOCK_404 + 1,
                    STONE_BLOCK_758);
    /** 758のブロックエンティティ種別ID → 404のブロックエンティティ種別ID。無い種別は DROP。 */
    private static final int[] BLOCK_ENTITY_758_TO_404_MAP =
            expand(BLOCK_ENTITY_404_RANGES, BLOCK_ENTITY_404_OVERRIDES, MAX_BLOCK_ENTITY_758 + 1,
                    DROP);
    /** 404のブロックエンティティ種別ID → 758のブロックエンティティ種別ID。 */
    private static final int[] BLOCK_ENTITY_404_TO_758_MAP =
            expand(BLOCK_ENTITY_404_TO_758_RANGES, BLOCK_ENTITY_404_TO_758_OVERRIDES,
                    MAX_BLOCK_ENTITY_404 + 1, DROP);
    /** 758のバイオームID → 404のバイオームID。 */
    private static final int[] BIOME_758_TO_404_MAP =
            expand(BIOME_404_RANGES, BIOME_404_OVERRIDES, MAX_BIOME_758 + 1, PLAINS_BIOME_404);
    /** 404のバイオームID → 758のバイオームID。 */
    private static final int[] BIOME_404_TO_758_MAP =
            expand(BIOME_404_TO_758_RANGES, BIOME_404_TO_758_OVERRIDES, MAX_BIOME_404 + 1,
                    PLAINS_BIOME_758);
    /** 758のダメージ種別ID → 404のダメージ種別ID（恒等写像。codec には無い）。 */
    private static final int[] DAMAGE_TYPE_758_TO_404_MAP =
            expand(DAMAGE_TYPE_404_RANGES, DAMAGE_TYPE_404_OVERRIDES, MAX_DAMAGE_TYPE_758 + 1,
                    GENERIC_DAMAGE_TYPE_404);
    /** 404のダメージ種別ID → 758のダメージ種別ID（恒等写像。codec には無い）。 */
    private static final int[] DAMAGE_TYPE_404_TO_758_MAP =
            expand(DAMAGE_TYPE_404_TO_758_RANGES, DAMAGE_TYPE_404_TO_758_OVERRIDES,
                    MAX_DAMAGE_TYPE_404 + 1, GENERIC_DAMAGE_TYPE_758);
"""
    text = replace_once(
        text,
        "    /** 759のブロック状態ID → 758のブロック状態ID（RegistryTables758）。 */",
        maps + "    /** 759のブロック状態ID → 758のブロック状態ID（RegistryTables758）。 */")

    # 5. 音の名前表の Map 化（498 の後）。
    text = replace_once(
        text,
        "    /** 498の音の名前 → 758の音の名前（Stop Sound 用）。数値の合成表と一致させる。 */\n"
        "    private static final Map<String, String> SOUND_NAME_FALLBACK_498_TO_758_MAP =\n"
        "            buildSoundNameMap(SOUND_NAME_FALLBACKS_498_TO_758);",
        "    /** 498の音の名前 → 758の音の名前（Stop Sound 用）。数値の合成表と一致させる。 */\n"
        "    private static final Map<String, String> SOUND_NAME_FALLBACK_498_TO_758_MAP =\n"
        "            buildSoundNameMap(SOUND_NAME_FALLBACKS_498_TO_758);\n\n"
        "    /** 758の音の名前 → 404の音の名前（Stop Sound 用）。数値の合成表と一致させる。 */\n"
        "    private static final Map<String, String> SOUND_NAME_FALLBACK_758_TO_404_MAP =\n"
        "            buildSoundNameMap(SOUND_NAME_FALLBACKS_758_TO_404);\n\n"
        "    /** 404の音の名前 → 758の音の名前（Stop Sound 用）。数値の合成表と一致させる。 */\n"
        "    private static final Map<String, String> SOUND_NAME_FALLBACK_404_TO_758_MAP =\n"
        "            buildSoundNameMap(SOUND_NAME_FALLBACKS_404_TO_758);")

    # 6. 写像メソッド（498 の節の後、490 の節の前）。
    methods = """
    // 404 ⇔ 758（1.13.2 ⇔ 1.18.2）
    // 404 のレジストリは 498 とも別物（1.14 の追加要素が無く、看板や
    // 染料の改名もある）ため、RegistryTables404 の専用表を使う。
    // 生成と検証は tools/gen-404-tables.py／tools/verify-404-tables.py。
    // メニューは 404 にレジストリが無いため写像を作らない。

    /** 758のブロック状態ID → 404のブロック状態ID。範囲外はSTONE_BLOCK_STATE_404 に相当する代替。 */
    public static int mapBlockState758To404(int id758) {
        if (id758 < 0 || id758 >= BLOCK_STATE_758_TO_404_MAP.length) {
            logUnknownComposedOnce("758→404", "ブロック状態", id758);
            return STONE_BLOCK_STATE_404;
        }
        return BLOCK_STATE_758_TO_404_MAP[id758];
    }

    /** 404のブロック状態ID → 758のブロック状態ID。範囲外はSTONE_BLOCK_STATE_758 に相当する代替。 */
    public static int mapBlockState404To758(int id404) {
        if (id404 < 0 || id404 >= BLOCK_STATE_404_TO_758_MAP.length) {
            logUnknownComposedOnce("404→758", "ブロック状態", id404);
            return STONE_BLOCK_STATE_758;
        }
        return BLOCK_STATE_404_TO_758_MAP[id404];
    }

    /** 758のアイテムID → 404のアイテムID。範囲外はSTONE_ITEM_404 に相当する代替。 */
    public static int mapItem758To404(int id758) {
        if (id758 < 0 || id758 >= ITEM_758_TO_404_MAP.length) {
            logUnknownComposedOnce("758→404", "アイテム", id758);
            return STONE_ITEM_404;
        }
        return ITEM_758_TO_404_MAP[id758];
    }

    /** 404のアイテムID → 758のアイテムID。範囲外はSTONE_ITEM_758 に相当する代替。 */
    public static int mapItem404To758(int id404) {
        if (id404 < 0 || id404 >= ITEM_404_TO_758_MAP.length) {
            logUnknownComposedOnce("404→758", "アイテム", id404);
            return STONE_ITEM_758;
        }
        return ITEM_404_TO_758_MAP[id404];
    }

    /** 758のエンティティ種別ID → 404のエンティティ種別ID。範囲外はPIG_ENTITY_TYPE_404 に相当する代替。 */
    public static int mapEntityType758To404(int id758) {
        if (id758 < 0 || id758 >= ENTITY_TYPE_758_TO_404_MAP.length) {
            logUnknownComposedOnce("758→404", "エンティティ種別", id758);
            return PIG_ENTITY_TYPE_404;
        }
        return ENTITY_TYPE_758_TO_404_MAP[id758];
    }

    /** 404のエンティティ種別ID → 758のエンティティ種別ID。範囲外はPIG_ENTITY_TYPE_758 に相当する代替。 */
    public static int mapEntityType404To758(int id404) {
        if (id404 < 0 || id404 >= ENTITY_TYPE_404_TO_758_MAP.length) {
            logUnknownComposedOnce("404→758", "エンティティ種別", id404);
            return PIG_ENTITY_TYPE_758;
        }
        return ENTITY_TYPE_404_TO_758_MAP[id404];
    }

    /** 758の音ID → 404の音ID。範囲外はGENERIC_SOUND_404 に相当する代替。 */
    public static int mapSound758To404(int id758) {
        if (id758 < 0 || id758 >= SOUND_758_TO_404_MAP.length) {
            logUnknownComposedOnce("758→404", "音", id758);
            return GENERIC_SOUND_404;
        }
        return SOUND_758_TO_404_MAP[id758];
    }

    /** 404の音ID → 758の音ID。範囲外はGENERIC_SOUND_758 に相当する代替。 */
    public static int mapSound404To758(int id404) {
        if (id404 < 0 || id404 >= SOUND_404_TO_758_MAP.length) {
            logUnknownComposedOnce("404→758", "音", id404);
            return GENERIC_SOUND_758;
        }
        return SOUND_404_TO_758_MAP[id404];
    }

    /** 758のパーティクルID → 404のパーティクルID。範囲外はAMBIENT_PARTICLE_404 に相当する代替。 */
    public static int mapParticle758To404(int id758) {
        if (id758 < 0 || id758 >= PARTICLE_758_TO_404_MAP.length) {
            logUnknownComposedOnce("758→404", "パーティクル", id758);
            return AMBIENT_PARTICLE_404;
        }
        return PARTICLE_758_TO_404_MAP[id758];
    }

    /** 404のパーティクルID → 758のパーティクルID。範囲外はAMBIENT_PARTICLE_758 に相当する代替。 */
    public static int mapParticle404To758(int id404) {
        if (id404 < 0 || id404 >= PARTICLE_404_TO_758_MAP.length) {
            logUnknownComposedOnce("404→758", "パーティクル", id404);
            return AMBIENT_PARTICLE_758;
        }
        return PARTICLE_404_TO_758_MAP[id404];
    }

    /** 758のブロックID → 404のブロックID。範囲外はSTONE_BLOCK_404 に相当する代替。 */
    public static int mapBlock758To404(int id758) {
        if (id758 < 0 || id758 >= BLOCK_758_TO_404_MAP.length) {
            logUnknownComposedOnce("758→404", "ブロック", id758);
            return STONE_BLOCK_404;
        }
        return BLOCK_758_TO_404_MAP[id758];
    }

    /** 404のブロックID → 758のブロックID。範囲外はSTONE_BLOCK_758 に相当する代替。 */
    public static int mapBlock404To758(int id404) {
        if (id404 < 0 || id404 >= BLOCK_404_TO_758_MAP.length) {
            logUnknownComposedOnce("404→758", "ブロック", id404);
            return STONE_BLOCK_758;
        }
        return BLOCK_404_TO_758_MAP[id404];
    }

    /**
     * 758のブロックエンティティ種別ID → 404のブロックエンティティ種別ID。
     * 758にしか無い sculk_sensor と beehive、404に無い 1.14 の
     * lectern／bell／jigsaw／campfire は DROP。
     */
    public static int mapBlockEntityType758To404(int id758) {
        if (id758 < 0 || id758 >= BLOCK_ENTITY_758_TO_404_MAP.length) {
            logUnknownComposedOnce("758→404", "ブロックエンティティ種別", id758);
            return DROP;
        }
        return BLOCK_ENTITY_758_TO_404_MAP[id758];
    }

    /** 404のブロックエンティティ種別ID → 758のブロックエンティティ種別ID。 */
    public static int mapBlockEntityType404To758(int id404) {
        if (id404 < 0 || id404 >= BLOCK_ENTITY_404_TO_758_MAP.length) {
            logUnknownComposedOnce("404→758", "ブロックエンティティ種別", id404);
            return DROP;
        }
        return BLOCK_ENTITY_404_TO_758_MAP[id404];
    }

    /** 758のバイオームID → 404のバイオームID。範囲外はPLAINS_BIOME_404 に相当する代替。 */
    public static int mapBiome758To404(int id758) {
        if (id758 < 0 || id758 >= BIOME_758_TO_404_MAP.length) {
            logUnknownComposedOnce("758→404", "バイオーム", id758);
            return PLAINS_BIOME_404;
        }
        return BIOME_758_TO_404_MAP[id758];
    }

    /** 404のバイオームID → 758のバイオームID。範囲外はPLAINS_BIOME_758 に相当する代替。 */
    public static int mapBiome404To758(int id404) {
        if (id404 < 0 || id404 >= BIOME_404_TO_758_MAP.length) {
            logUnknownComposedOnce("404→758", "バイオーム", id404);
            return PLAINS_BIOME_758;
        }
        return BIOME_404_TO_758_MAP[id404];
    }

    /** 404 のバイオーム名（レジストリ順）。Bedrock 側が次元情報に使う。 */
    public static java.util.List<String> biomeNames404() {
        return java.util.List.of(RegistryTables404.BIOME_NAMES_404);
    }

    /**
     * 758のダメージ種別ID → 404のダメージ種別ID（恒等写像。codec には無い）。
     */
    public static int mapDamageType758To404(int id758) {
        if (id758 < 0 || id758 > MAX_DAMAGE_TYPE_758) {
            logUnknownOnce(PROTOCOL_404, "ダメージ種別", id758);
            return DROP;
        }
        return DAMAGE_TYPE_758_TO_404_MAP[id758];
    }

    /** 404のダメージ種別ID → 758のダメージ種別ID（恒等写像。codec には無い）。 */
    public static int mapDamageType404To758(int id404) {
        if (id404 < 0 || id404 > MAX_DAMAGE_TYPE_404) {
            logUnknownOnce(PROTOCOL_758, "ダメージ種別", id404);
            return DROP;
        }
        return DAMAGE_TYPE_404_TO_758_MAP[id404];
    }

    /** 758の音の名前 → 404の音の名前（Stop Sound 用）。数値の合成表と一致させる。 */
    public static String mapSoundName758To404(String name) {
        return mapSoundNameComposed(name, SOUND_NAME_FALLBACK_758_TO_404_MAP);
    }

    /** 404の音の名前 → 758の音の名前（Stop Sound 用）。数値の合成表と一致させる。 */
    public static String mapSoundName404To758(String name) {
        return mapSoundNameComposed(name, SOUND_NAME_FALLBACK_404_TO_758_MAP);
    }
"""
    text = replace_once(
        text,
        "    // 490 ⇔ 758（1.14.3 ⇔ 1.18.2）",
        methods + "    // 490 ⇔ 758（1.14.3 ⇔ 1.18.2）")

    P.write_text(text, encoding="utf-8")
    print("RegistryRemapper.java に 404 を追加した")
    return 0


if __name__ == "__main__":
    sys.exit(main())
