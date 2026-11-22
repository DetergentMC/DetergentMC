#!/usr/bin/env python3
"""RegistryRemapper.java に 393（1.13）の定数・展開済み表・写像メソッドを足す。

表の実体は RegistryTables393（gen-393-tables.py が生成）にあり、
404 の節と同じ形で static import して使う。メニューは 393 に
レジストリが無いため写像メソッドを作らない。効果も表は作らず
（393 は 758 の先頭 30 件と一致）、定数だけ置く。
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
    if "PROTOCOL_393" in text:
        print("skip（既存）: 393 はすでにある")
        return 0

    # 1. static import。
    text = replace_once(
        text,
        "import static io.detergentmc.protocol.RegistryTables404.*;",
        "import static io.detergentmc.protocol.RegistryTables393.*;\n"
        "import static io.detergentmc.protocol.RegistryTables404.*;")

    # 2. プロトコル番号。
    text = replace_once(
        text,
        "    /** 404（1.13.2）のプロトコル番号。 */\n"
        "    public static final int PROTOCOL_404 = 404;",
        "    /** 393（1.13）のプロトコル番号。 */\n"
        "    public static final int PROTOCOL_393 = 393;\n"
        "    /** 404（1.13.2）のプロトコル番号。 */\n"
        "    public static final int PROTOCOL_404 = 404;")

    # 3. 定数（404 の定数ブロックの前）。
    constants = """
    /**
     * 393（1.13）側の最大の番号。ブロック／ブロック状態／アイテムは
     * 実バニラ 1.13 jar の data-generator ダンプ、エンティティと
     * バイオームは minecraft-data（1.13⇔1.13.2 で完全一致を確認）、
     * 音とパーティクルは ViaVersion mapping-1.13 の件数−1。
     */
    public static final int MAX_BLOCK_STATE_393 = 8581;
    public static final int MAX_ITEM_393 = 784;
    public static final int MAX_ENTITY_TYPE_393 = 94;
    public static final int MAX_SOUND_393 = 661;
    public static final int MAX_PARTICLE_393 = 49;
    public static final int MAX_BLOCK_393 = 592;
    /** 393側のバイオームは 0〜72（73件）。 */
    public static final int MAX_BIOME_393 = 72;
    /** 393側のダメージ種別は 0〜41（42件。758 と同じく codec には無いため恒等写像）。 */
    public static final int MAX_DAMAGE_TYPE_393 = 41;
    /** 393側のブロックエンティティ種別は 0〜24（25件）。 */
    public static final int MAX_BLOCK_ENTITY_393 = 24;
    /** 393側の絵画は 0〜25（26件）。実 1.13 jar の定数プール順が 404 と同じため表は作らない。 */
    public static final int MAX_PAINTING_VARIANT_393 = 25;
    /** 393側の効果は 0〜29（30件）。758 の先頭 30 件と同じため表は作らない。 */
    public static final int MAX_EFFECT_393 = 29;

    /** 393側の代替先。 */
    public static final int STONE_BLOCK_STATE_393 = 1;
    public static final int AIR_BLOCK_STATE_393 = 0;
    public static final int STONE_ITEM_393 = 1;
    public static final int PIG_ENTITY_TYPE_393 = 51;
    public static final int GENERIC_SOUND_393 = 135; // block.stone.break
    public static final int AMBIENT_PARTICLE_393 = 0; // ambient_entity_effect
    public static final int STONE_BLOCK_393 = 1;
    /** 393側の平原バイオーム（minecraft-data pc/1.13/biomes.json。1.13.2 と同一）。 */
    public static final int PLAINS_BIOME_393 = 1;
    /** 393側の generic ダメージ種別（762 と同一順序と仮定）。 */
    public static final int GENERIC_DAMAGE_TYPE_393 = 16;
"""
    text = replace_once(
        text,
        "    /**\n     * 404（1.13.2）側の最大の番号。",
        constants + "\n    /**\n     * 404（1.13.2）側の最大の番号。")

    # 4. 展開済み表（404 の表の前）。
    maps = """
    /** 758のブロック状態ID → 393のブロック状態ID（RegistryTables393）。 */
    private static final int[] BLOCK_STATE_758_TO_393_MAP =
            expand(BLOCK_STATE_393_RANGES, BLOCK_STATE_393_OVERRIDES, MAX_BLOCK_STATE_758 + 1,
                    STONE_BLOCK_STATE_393);
    /** 393のブロック状態ID → 758のブロック状態ID（RegistryTables393）。 */
    private static final int[] BLOCK_STATE_393_TO_758_MAP =
            expand(BLOCK_STATE_393_TO_758_RANGES, BLOCK_STATE_393_TO_758_OVERRIDES,
                    MAX_BLOCK_STATE_393 + 1, STONE_BLOCK_STATE_758);
    /** 758のアイテムID → 393のアイテムID。 */
    private static final int[] ITEM_758_TO_393_MAP =
            expand(ITEM_393_RANGES, ITEM_393_OVERRIDES, MAX_ITEM_758 + 1, STONE_ITEM_393);
    /** 393のアイテムID → 758のアイテムID。 */
    private static final int[] ITEM_393_TO_758_MAP =
            expand(ITEM_393_TO_758_RANGES, ITEM_393_TO_758_OVERRIDES, MAX_ITEM_393 + 1,
                    STONE_ITEM_758);
    /** 758のエンティティ種別ID → 393のエンティティ種別ID。 */
    private static final int[] ENTITY_TYPE_758_TO_393_MAP =
            expand(ENTITY_TYPE_393_RANGES, ENTITY_TYPE_393_OVERRIDES, MAX_ENTITY_TYPE_758 + 1,
                    PIG_ENTITY_TYPE_393);
    /** 393のエンティティ種別ID → 758のエンティティ種別ID。 */
    private static final int[] ENTITY_TYPE_393_TO_758_MAP =
            expand(ENTITY_TYPE_393_TO_758_RANGES, ENTITY_TYPE_393_TO_758_OVERRIDES,
                    MAX_ENTITY_TYPE_393 + 1, PIG_ENTITY_TYPE_758);
    /** 758の音ID → 393の音ID。 */
    private static final int[] SOUND_758_TO_393_MAP =
            expand(SOUND_393_RANGES, SOUND_393_OVERRIDES, MAX_SOUND_758 + 1, GENERIC_SOUND_393);
    /** 393の音ID → 758の音ID。 */
    private static final int[] SOUND_393_TO_758_MAP =
            expand(SOUND_393_TO_758_RANGES, SOUND_393_TO_758_OVERRIDES, MAX_SOUND_393 + 1,
                    GENERIC_SOUND_758);
    /** 758のパーティクルID → 393のパーティクルID。 */
    private static final int[] PARTICLE_758_TO_393_MAP =
            expand(PARTICLE_393_RANGES, PARTICLE_393_OVERRIDES, MAX_PARTICLE_758 + 1,
                    AMBIENT_PARTICLE_393);
    /** 393のパーティクルID → 758のパーティクルID。 */
    private static final int[] PARTICLE_393_TO_758_MAP =
            expand(PARTICLE_393_TO_758_RANGES, PARTICLE_393_TO_758_OVERRIDES,
                    MAX_PARTICLE_393 + 1, AMBIENT_PARTICLE_758);
    /** 758のブロックID → 393のブロックID。 */
    private static final int[] BLOCK_758_TO_393_MAP =
            expand(BLOCK_393_RANGES, BLOCK_393_OVERRIDES, MAX_BLOCK_758 + 1, STONE_BLOCK_393);
    /** 393のブロックID → 758のブロックID。 */
    private static final int[] BLOCK_393_TO_758_MAP =
            expand(BLOCK_393_TO_758_RANGES, BLOCK_393_TO_758_OVERRIDES, MAX_BLOCK_393 + 1,
                    STONE_BLOCK_758);
    /** 758のブロックエンティティ種別ID → 393のブロックエンティティ種別ID。無い種別は DROP。 */
    private static final int[] BLOCK_ENTITY_758_TO_393_MAP =
            expand(BLOCK_ENTITY_393_RANGES, BLOCK_ENTITY_393_OVERRIDES, MAX_BLOCK_ENTITY_758 + 1,
                    DROP);
    /** 393のブロックエンティティ種別ID → 758のブロックエンティティ種別ID。 */
    private static final int[] BLOCK_ENTITY_393_TO_758_MAP =
            expand(BLOCK_ENTITY_393_TO_758_RANGES, BLOCK_ENTITY_393_TO_758_OVERRIDES,
                    MAX_BLOCK_ENTITY_393 + 1, DROP);
    /** 758のバイオームID → 393のバイオームID。 */
    private static final int[] BIOME_758_TO_393_MAP =
            expand(BIOME_393_RANGES, BIOME_393_OVERRIDES, MAX_BIOME_758 + 1, PLAINS_BIOME_393);
    /** 393のバイオームID → 758のバイオームID。 */
    private static final int[] BIOME_393_TO_758_MAP =
            expand(BIOME_393_TO_758_RANGES, BIOME_393_TO_758_OVERRIDES, MAX_BIOME_393 + 1,
                    PLAINS_BIOME_758);
    /** 758のダメージ種別ID → 393のダメージ種別ID（恒等写像。codec には無い）。 */
    private static final int[] DAMAGE_TYPE_758_TO_393_MAP =
            expand(DAMAGE_TYPE_393_RANGES, DAMAGE_TYPE_393_OVERRIDES, MAX_DAMAGE_TYPE_758 + 1,
                    GENERIC_DAMAGE_TYPE_393);
    /** 393のダメージ種別ID → 758のダメージ種別ID（恒等写像。codec には無い）。 */
    private static final int[] DAMAGE_TYPE_393_TO_758_MAP =
            expand(DAMAGE_TYPE_393_TO_758_RANGES, DAMAGE_TYPE_393_TO_758_OVERRIDES,
                    MAX_DAMAGE_TYPE_393 + 1, GENERIC_DAMAGE_TYPE_758);
"""
    text = replace_once(
        text,
        "    /** 758のブロック状態ID → 404のブロック状態ID（RegistryTables404）。 */",
        maps + "    /** 758のブロック状態ID → 404のブロック状態ID（RegistryTables404）。 */")

    # 5. 音の名前表の Map 化（404 の前）。
    text = replace_once(
        text,
        "    /** 758の音の名前 → 404の音の名前（Stop Sound 用）。数値の合成表と一致させる。 */",
        "    /** 758の音の名前 → 393の音の名前（Stop Sound 用）。数値の合成表と一致させる。 */\n"
        "    private static final Map<String, String> SOUND_NAME_FALLBACK_758_TO_393_MAP =\n"
        "            buildSoundNameMap(SOUND_NAME_FALLBACKS_758_TO_393);\n\n"
        "    /** 393の音の名前 → 758の音の名前（Stop Sound 用）。数値の合成表と一致させる。 */\n"
        "    private static final Map<String, String> SOUND_NAME_FALLBACK_393_TO_758_MAP =\n"
        "            buildSoundNameMap(SOUND_NAME_FALLBACKS_393_TO_758);\n\n"
        "    /** 758の音の名前 → 404の音の名前（Stop Sound 用）。数値の合成表と一致させる。 */")

    # 6. 写像メソッド（404 の節の前）。
    methods = """
    // 393 ⇔ 758（1.13 ⇔ 1.18.2）
    // 393 のレジストリは 404 とも別物（1.13.1 で追加された dead coral と
    // プロパティ追加分が無い）ため、RegistryTables393 の専用表を使う。
    // 生成と検証は tools/gen-393-tables.py／tools/verify-393-tables.py。
    // メニューは 393 にレジストリが無いため写像を作らない。

    /** 758のブロック状態ID → 393のブロック状態ID。範囲外はSTONE_BLOCK_STATE_393 に相当する代替。 */
    public static int mapBlockState758To393(int id758) {
        if (id758 < 0 || id758 >= BLOCK_STATE_758_TO_393_MAP.length) {
            logUnknownComposedOnce("758→393", "ブロック状態", id758);
            return STONE_BLOCK_STATE_393;
        }
        return BLOCK_STATE_758_TO_393_MAP[id758];
    }

    /** 393のブロック状態ID → 758のブロック状態ID。範囲外はSTONE_BLOCK_STATE_758 に相当する代替。 */
    public static int mapBlockState393To758(int id393) {
        if (id393 < 0 || id393 >= BLOCK_STATE_393_TO_758_MAP.length) {
            logUnknownComposedOnce("393→758", "ブロック状態", id393);
            return STONE_BLOCK_STATE_758;
        }
        return BLOCK_STATE_393_TO_758_MAP[id393];
    }

    /** 758のアイテムID → 393のアイテムID。範囲外はSTONE_ITEM_393 に相当する代替。 */
    public static int mapItem758To393(int id758) {
        if (id758 < 0 || id758 >= ITEM_758_TO_393_MAP.length) {
            logUnknownComposedOnce("758→393", "アイテム", id758);
            return STONE_ITEM_393;
        }
        return ITEM_758_TO_393_MAP[id758];
    }

    /** 393のアイテムID → 758のアイテムID。範囲外はSTONE_ITEM_758 に相当する代替。 */
    public static int mapItem393To758(int id393) {
        if (id393 < 0 || id393 >= ITEM_393_TO_758_MAP.length) {
            logUnknownComposedOnce("393→758", "アイテム", id393);
            return STONE_ITEM_758;
        }
        return ITEM_393_TO_758_MAP[id393];
    }

    /** 758のエンティティ種別ID → 393のエンティティ種別ID。範囲外はPIG_ENTITY_TYPE_393 に相当する代替。 */
    public static int mapEntityType758To393(int id758) {
        if (id758 < 0 || id758 >= ENTITY_TYPE_758_TO_393_MAP.length) {
            logUnknownComposedOnce("758→393", "エンティティ種別", id758);
            return PIG_ENTITY_TYPE_393;
        }
        return ENTITY_TYPE_758_TO_393_MAP[id758];
    }

    /** 393のエンティティ種別ID → 758のエンティティ種別ID。範囲外はPIG_ENTITY_TYPE_758 に相当する代替。 */
    public static int mapEntityType393To758(int id393) {
        if (id393 < 0 || id393 >= ENTITY_TYPE_393_TO_758_MAP.length) {
            logUnknownComposedOnce("393→758", "エンティティ種別", id393);
            return PIG_ENTITY_TYPE_758;
        }
        return ENTITY_TYPE_393_TO_758_MAP[id393];
    }

    /** 758の音ID → 393の音ID。範囲外はGENERIC_SOUND_393 に相当する代替。 */
    public static int mapSound758To393(int id758) {
        if (id758 < 0 || id758 >= SOUND_758_TO_393_MAP.length) {
            logUnknownComposedOnce("758→393", "音", id758);
            return GENERIC_SOUND_393;
        }
        return SOUND_758_TO_393_MAP[id758];
    }

    /** 393の音ID → 758の音ID。範囲外はGENERIC_SOUND_758 に相当する代替。 */
    public static int mapSound393To758(int id393) {
        if (id393 < 0 || id393 >= SOUND_393_TO_758_MAP.length) {
            logUnknownComposedOnce("393→758", "音", id393);
            return GENERIC_SOUND_758;
        }
        return SOUND_393_TO_758_MAP[id393];
    }

    /** 758のパーティクルID → 393のパーティクルID。範囲外はAMBIENT_PARTICLE_393 に相当する代替。 */
    public static int mapParticle758To393(int id758) {
        if (id758 < 0 || id758 >= PARTICLE_758_TO_393_MAP.length) {
            logUnknownComposedOnce("758→393", "パーティクル", id758);
            return AMBIENT_PARTICLE_393;
        }
        return PARTICLE_758_TO_393_MAP[id758];
    }

    /** 393のパーティクルID → 758のパーティクルID。範囲外はAMBIENT_PARTICLE_758 に相当する代替。 */
    public static int mapParticle393To758(int id393) {
        if (id393 < 0 || id393 >= PARTICLE_393_TO_758_MAP.length) {
            logUnknownComposedOnce("393→758", "パーティクル", id393);
            return AMBIENT_PARTICLE_758;
        }
        return PARTICLE_393_TO_758_MAP[id393];
    }

    /** 758のブロックID → 393のブロックID。範囲外はSTONE_BLOCK_393 に相当する代替。 */
    public static int mapBlock758To393(int id758) {
        if (id758 < 0 || id758 >= BLOCK_758_TO_393_MAP.length) {
            logUnknownComposedOnce("758→393", "ブロック", id758);
            return STONE_BLOCK_393;
        }
        return BLOCK_758_TO_393_MAP[id758];
    }

    /** 393のブロックID → 758のブロックID。範囲外はSTONE_BLOCK_758 に相当する代替。 */
    public static int mapBlock393To758(int id393) {
        if (id393 < 0 || id393 >= BLOCK_393_TO_758_MAP.length) {
            logUnknownComposedOnce("393→758", "ブロック", id393);
            return STONE_BLOCK_758;
        }
        return BLOCK_393_TO_758_MAP[id393];
    }

    /**
     * 758のブロックエンティティ種別ID → 393のブロックエンティティ種別ID。
     * 758にしか無い sculk_sensor と beehive、393に無い 1.14 の
     * lectern／bell／jigsaw／campfire は DROP。
     */
    public static int mapBlockEntityType758To393(int id758) {
        if (id758 < 0 || id758 >= BLOCK_ENTITY_758_TO_393_MAP.length) {
            logUnknownComposedOnce("758→393", "ブロックエンティティ種別", id758);
            return DROP;
        }
        return BLOCK_ENTITY_758_TO_393_MAP[id758];
    }

    /** 393のブロックエンティティ種別ID → 758のブロックエンティティ種別ID。 */
    public static int mapBlockEntityType393To758(int id393) {
        if (id393 < 0 || id393 >= BLOCK_ENTITY_393_TO_758_MAP.length) {
            logUnknownComposedOnce("393→758", "ブロックエンティティ種別", id393);
            return DROP;
        }
        return BLOCK_ENTITY_393_TO_758_MAP[id393];
    }

    /** 758のバイオームID → 393のバイオームID。範囲外はPLAINS_BIOME_393 に相当する代替。 */
    public static int mapBiome758To393(int id758) {
        if (id758 < 0 || id758 >= BIOME_758_TO_393_MAP.length) {
            logUnknownComposedOnce("758→393", "バイオーム", id758);
            return PLAINS_BIOME_393;
        }
        return BIOME_758_TO_393_MAP[id758];
    }

    /** 393のバイオームID → 758のバイオームID。範囲外はPLAINS_BIOME_758 に相当する代替。 */
    public static int mapBiome393To758(int id393) {
        if (id393 < 0 || id393 >= BIOME_393_TO_758_MAP.length) {
            logUnknownComposedOnce("393→758", "バイオーム", id393);
            return PLAINS_BIOME_758;
        }
        return BIOME_393_TO_758_MAP[id393];
    }

    /** 393 のバイオーム名（レジストリ順）。Bedrock 側が次元情報に使う。 */
    public static java.util.List<String> biomeNames393() {
        return java.util.List.of(RegistryTables393.BIOME_NAMES_393);
    }

    /**
     * 758のダメージ種別ID → 393のダメージ種別ID（恒等写像。codec には無い）。
     */
    public static int mapDamageType758To393(int id758) {
        if (id758 < 0 || id758 > MAX_DAMAGE_TYPE_758) {
            logUnknownOnce(PROTOCOL_393, "ダメージ種別", id758);
            return DROP;
        }
        return DAMAGE_TYPE_758_TO_393_MAP[id758];
    }

    /** 393のダメージ種別ID → 758のダメージ種別ID（恒等写像。codec には無い）。 */
    public static int mapDamageType393To758(int id393) {
        if (id393 < 0 || id393 > MAX_DAMAGE_TYPE_393) {
            logUnknownOnce(PROTOCOL_758, "ダメージ種別", id393);
            return DROP;
        }
        return DAMAGE_TYPE_393_TO_758_MAP[id393];
    }

    /** 758の音の名前 → 393の音の名前（Stop Sound 用）。数値の合成表と一致させる。 */
    public static String mapSoundName758To393(String name) {
        return mapSoundNameComposed(name, SOUND_NAME_FALLBACK_758_TO_393_MAP);
    }

    /** 393の音の名前 → 758の音の名前（Stop Sound 用）。数値の合成表と一致させる。 */
    public static String mapSoundName393To758(String name) {
        return mapSoundNameComposed(name, SOUND_NAME_FALLBACK_393_TO_758_MAP);
    }

"""
    text = replace_once(
        text,
        "    // 404 ⇔ 758（1.13.2 ⇔ 1.18.2）",
        methods + "    // 404 ⇔ 758（1.13.2 ⇔ 1.18.2）")

    P.write_text(text, encoding="utf-8")
    print("RegistryRemapper.java に 393 を追加した")
    return 0


if __name__ == "__main__":
    sys.exit(main())
