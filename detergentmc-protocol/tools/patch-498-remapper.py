#!/usr/bin/env python3
"""RegistryRemapper.java に 498（1.14.4）の定数・合成表・メソッドを追加する。

498 のレジストリは 578 とは別物（1.15 の追加要素が無い）ため、委譲では
なく RegistryTables498 の専用表を使う。定数・合成表・メソッドの形は
578 のものを雛形にする。数値はすべて実データから確定済み：
  - 件数：実バニラ 1.14.4 jar の data-generator ダンプ
    （reports/registries.json・reports/blocks.json）。
  - PIG_ENTITY_TYPE_498 = 54、GENERIC_SOUND_498 = 636
    （block.stone.break）、CRAFTING_MENU_498 = 11、PLAINS_BIOME_498 = 1 は
    ViaVersion mapping-1.14.json と minecraft-data pc/1.14.4 の実値。
詳細は docs/PROXY-1.14.4.md と tools/gen-498-tables.py を参照。

使い方: python3 tools/patch-498-remapper.py
冪等：498 が既にあれば何もしない。
書き換えた後は必ず git diff で内容を確認してからコミットすること。
"""
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
P = ROOT / "src/main/java/io/detergentmc/protocol/RegistryRemapper.java"


def fail(msg):
    print(f"NG: {msg}", file=sys.stderr)
    sys.exit(1)


def rep(text, old, new, count=1):
    n = text.count(old)
    if n != count:
        fail(f"置換対象 {old[:60]!r} が {n} 件（期待 {count}）")
    return text.replace(old, new)


CONSTANTS = """
    /**
     * 498（1.14.4）側の最大の番号（ViaVersion mapping-1.14.json の件数−1。
     * 実バニラ 1.14.4 jar の data-generator ダンプでも全件照合済み）。
     */
    public static final int MAX_BLOCK_STATE_498 = 11270;
    public static final int MAX_ITEM_498 = 876;
    public static final int MAX_ENTITY_TYPE_498 = 101;
    public static final int MAX_SOUND_498 = 794;
    public static final int MAX_PARTICLE_498 = 57;
    public static final int MAX_BLOCK_498 = 675;
    public static final int MAX_MENU_498 = 22;
    /** 498側のエンチャントは 0〜36（37件。soul_speed が無いため表は作らない）。 */
    public static final int MAX_ENCHANTMENT_498 = 36;
    /** 498側のバイオームは 0〜74（75件。578 と同じ顔ぶれ・同じ順序）。 */
    public static final int MAX_BIOME_498 = 74;
    /** 498側のダメージ種別は 0〜41（42件。758 と同じく codec には無いため恒等写像）。 */
    public static final int MAX_DAMAGE_TYPE_498 = 41;
    /** 498側のブロックエンティティ種別は 0〜31（32件）。 */
    public static final int MAX_BLOCK_ENTITY_498 = 31;
    /** 498側の絵画は 0〜25（26件）。758 と同じ顔ぶれ・同じ順序のため表は作らない。 */
    public static final int MAX_PAINTING_VARIANT_498 = 25;

    /** 498側の代替先（mapping-1.14.json の番号）。 */
    public static final int STONE_BLOCK_STATE_498 = 1;
    public static final int AIR_BLOCK_STATE_498 = 0;
    public static final int STONE_ITEM_498 = 1;
    public static final int PIG_ENTITY_TYPE_498 = 54;
    public static final int GENERIC_SOUND_498 = 636; // block.stone.break
    public static final int AMBIENT_PARTICLE_498 = 0; // ambient_entity_effect
    public static final int STONE_BLOCK_498 = 1;
    public static final int CRAFTING_MENU_498 = 11;
    /** 498側の平原バイオーム（minecraft-data pc/1.14.4/biomes.json）。 */
    public static final int PLAINS_BIOME_498 = 1;
    /** 498側の generic ダメージ種別（762 と同一順序と仮定）。 */
    public static final int GENERIC_DAMAGE_TYPE_498 = 16;
"""

MAP_FIELDS = """
    /** 758のブロック状態ID → 498のブロック状態ID（RegistryTables498）。 */
    private static final int[] BLOCK_STATE_758_TO_498_MAP =
            expand(BLOCK_STATE_498_RANGES, BLOCK_STATE_498_OVERRIDES, MAX_BLOCK_STATE_758 + 1,
                    STONE_BLOCK_STATE_498);
    /** 498のブロック状態ID → 758のブロック状態ID（RegistryTables498）。 */
    private static final int[] BLOCK_STATE_498_TO_758_MAP =
            expand(BLOCK_STATE_498_TO_758_RANGES, BLOCK_STATE_498_TO_758_OVERRIDES,
                    MAX_BLOCK_STATE_498 + 1, STONE_BLOCK_STATE_758);
    /** 758のアイテムID → 498のアイテムID。 */
    private static final int[] ITEM_758_TO_498_MAP =
            expand(ITEM_498_RANGES, ITEM_498_OVERRIDES, MAX_ITEM_758 + 1, STONE_ITEM_498);
    /** 498のアイテムID → 758のアイテムID。 */
    private static final int[] ITEM_498_TO_758_MAP =
            expand(ITEM_498_TO_758_RANGES, ITEM_498_TO_758_OVERRIDES, MAX_ITEM_498 + 1,
                    STONE_ITEM_758);
    /** 758のエンティティ種別ID → 498のエンティティ種別ID。 */
    private static final int[] ENTITY_TYPE_758_TO_498_MAP =
            expand(ENTITY_TYPE_498_RANGES, ENTITY_TYPE_498_OVERRIDES, MAX_ENTITY_TYPE_758 + 1,
                    PIG_ENTITY_TYPE_498);
    /** 498のエンティティ種別ID → 758のエンティティ種別ID。 */
    private static final int[] ENTITY_TYPE_498_TO_758_MAP =
            expand(ENTITY_TYPE_498_TO_758_RANGES, ENTITY_TYPE_498_TO_758_OVERRIDES,
                    MAX_ENTITY_TYPE_498 + 1, PIG_ENTITY_TYPE_758);
    /** 758の音ID → 498の音ID。 */
    private static final int[] SOUND_758_TO_498_MAP =
            expand(SOUND_498_RANGES, SOUND_498_OVERRIDES, MAX_SOUND_758 + 1, GENERIC_SOUND_498);
    /** 498の音ID → 758の音ID。 */
    private static final int[] SOUND_498_TO_758_MAP =
            expand(SOUND_498_TO_758_RANGES, SOUND_498_TO_758_OVERRIDES, MAX_SOUND_498 + 1,
                    GENERIC_SOUND_758);
    /** 758のパーティクルID → 498のパーティクルID。 */
    private static final int[] PARTICLE_758_TO_498_MAP =
            expand(PARTICLE_498_RANGES, PARTICLE_498_OVERRIDES, MAX_PARTICLE_758 + 1,
                    AMBIENT_PARTICLE_498);
    /** 498のパーティクルID → 758のパーティクルID。 */
    private static final int[] PARTICLE_498_TO_758_MAP =
            expand(PARTICLE_498_TO_758_RANGES, PARTICLE_498_TO_758_OVERRIDES,
                    MAX_PARTICLE_498 + 1, AMBIENT_PARTICLE_758);
    /** 758のブロックID → 498のブロックID。 */
    private static final int[] BLOCK_758_TO_498_MAP =
            expand(BLOCK_498_RANGES, BLOCK_498_OVERRIDES, MAX_BLOCK_758 + 1, STONE_BLOCK_498);
    /** 498のブロックID → 758のブロックID。 */
    private static final int[] BLOCK_498_TO_758_MAP =
            expand(BLOCK_498_TO_758_RANGES, BLOCK_498_TO_758_OVERRIDES, MAX_BLOCK_498 + 1,
                    STONE_BLOCK_758);
    /** 758のメニュー種別ID → 498のメニュー種別ID（smithing は anvil へ）。 */
    private static final int[] MENU_758_TO_498_MAP =
            expand(MENU_498_RANGES, MENU_498_OVERRIDES, MAX_MENU_758 + 1, CRAFTING_MENU_498);
    /** 498のメニュー種別ID → 758のメニュー種別ID。 */
    private static final int[] MENU_498_TO_758_MAP =
            expand(MENU_498_TO_758_RANGES, MENU_498_TO_758_OVERRIDES, MAX_MENU_498 + 1,
                    CRAFTING_MENU_758);
    /** 758のブロックエンティティ種別ID → 498のブロックエンティティ種別ID。無い種別は DROP。 */
    private static final int[] BLOCK_ENTITY_758_TO_498_MAP =
            expand(BLOCK_ENTITY_498_RANGES, BLOCK_ENTITY_498_OVERRIDES, MAX_BLOCK_ENTITY_758 + 1,
                    DROP);
    /** 498のブロックエンティティ種別ID → 758のブロックエンティティ種別ID。無い種別は DROP。 */
    private static final int[] BLOCK_ENTITY_498_TO_758_MAP =
            expand(BLOCK_ENTITY_498_TO_758_RANGES, BLOCK_ENTITY_498_TO_758_OVERRIDES,
                    MAX_BLOCK_ENTITY_498 + 1, DROP);
    /** 758のバイオームID → 498のバイオームID（ネザーの新バイオームは nether_wastes へ）。 */
    private static final int[] BIOME_758_TO_498_MAP =
            expand(BIOME_498_RANGES, BIOME_498_OVERRIDES, MAX_BIOME_758 + 1, PLAINS_BIOME_498);
    /** 498のバイオームID → 758のバイオームID（hills／変種は素のバイオームへ）。 */
    private static final int[] BIOME_498_TO_758_MAP =
            expand(BIOME_498_TO_758_RANGES, BIOME_498_TO_758_OVERRIDES, MAX_BIOME_498 + 1,
                    PLAINS_BIOME_758);
    /** 758のダメージ種別ID → 498のダメージ種別ID（恒等写像。codec には無い）。 */
    private static final int[] DAMAGE_TYPE_758_TO_498_MAP =
            expand(DAMAGE_TYPE_498_RANGES, DAMAGE_TYPE_498_OVERRIDES, MAX_DAMAGE_TYPE_758 + 1,
                    GENERIC_DAMAGE_TYPE_498);
    /** 498のダメージ種別ID → 758のダメージ種別ID（恒等写像。codec には無い）。 */
    private static final int[] DAMAGE_TYPE_498_TO_758_MAP =
            expand(DAMAGE_TYPE_498_TO_758_RANGES, DAMAGE_TYPE_498_TO_758_OVERRIDES,
                    MAX_DAMAGE_TYPE_498 + 1, GENERIC_DAMAGE_TYPE_758);
"""

METHODS = """
    // 498 ⇔ 758（1.14.4 ⇔ 1.18.2）
    // 498 のレジストリは 578 とは別物（1.15 の追加要素が無い）ため、
    // RegistryTables498 の専用表を使う。生成と検証は
    // tools/gen-498-tables.py／tools/verify-498-tables.py。

    /** 758のブロック状態ID → 498のブロック状態ID。範囲外はSTONE_BLOCK_STATE_498 に相当する代替。 */
    public static int mapBlockState758To498(int id758) {
        if (id758 < 0 || id758 >= BLOCK_STATE_758_TO_498_MAP.length) {
            logUnknownComposedOnce("758→498", "ブロック状態", id758);
            return STONE_BLOCK_STATE_498;
        }
        return BLOCK_STATE_758_TO_498_MAP[id758];
    }

    /** 498のブロック状態ID → 758のブロック状態ID。範囲外はSTONE_BLOCK_STATE_758 に相当する代替。 */
    public static int mapBlockState498To758(int id498) {
        if (id498 < 0 || id498 >= BLOCK_STATE_498_TO_758_MAP.length) {
            logUnknownComposedOnce("498→758", "ブロック状態", id498);
            return STONE_BLOCK_STATE_758;
        }
        return BLOCK_STATE_498_TO_758_MAP[id498];
    }

    /** 758のアイテムID → 498のアイテムID。範囲外はSTONE_ITEM_498 に相当する代替。 */
    public static int mapItem758To498(int id758) {
        if (id758 < 0 || id758 >= ITEM_758_TO_498_MAP.length) {
            logUnknownComposedOnce("758→498", "アイテム", id758);
            return STONE_ITEM_498;
        }
        return ITEM_758_TO_498_MAP[id758];
    }

    /** 498のアイテムID → 758のアイテムID。範囲外はSTONE_ITEM_758 に相当する代替。 */
    public static int mapItem498To758(int id498) {
        if (id498 < 0 || id498 >= ITEM_498_TO_758_MAP.length) {
            logUnknownComposedOnce("498→758", "アイテム", id498);
            return STONE_ITEM_758;
        }
        return ITEM_498_TO_758_MAP[id498];
    }

    /** 758のエンティティ種別ID → 498のエンティティ種別ID。範囲外はPIG_ENTITY_TYPE_498 に相当する代替。 */
    public static int mapEntityType758To498(int id758) {
        if (id758 < 0 || id758 >= ENTITY_TYPE_758_TO_498_MAP.length) {
            logUnknownComposedOnce("758→498", "エンティティ種別", id758);
            return PIG_ENTITY_TYPE_498;
        }
        return ENTITY_TYPE_758_TO_498_MAP[id758];
    }

    /** 498のエンティティ種別ID → 758のエンティティ種別ID。範囲外はPIG_ENTITY_TYPE_758 に相当する代替。 */
    public static int mapEntityType498To758(int id498) {
        if (id498 < 0 || id498 >= ENTITY_TYPE_498_TO_758_MAP.length) {
            logUnknownComposedOnce("498→758", "エンティティ種別", id498);
            return PIG_ENTITY_TYPE_758;
        }
        return ENTITY_TYPE_498_TO_758_MAP[id498];
    }

    /** 758の音ID → 498の音ID。範囲外はGENERIC_SOUND_498 に相当する代替。 */
    public static int mapSound758To498(int id758) {
        if (id758 < 0 || id758 >= SOUND_758_TO_498_MAP.length) {
            logUnknownComposedOnce("758→498", "音", id758);
            return GENERIC_SOUND_498;
        }
        return SOUND_758_TO_498_MAP[id758];
    }

    /** 498の音ID → 758の音ID。範囲外はGENERIC_SOUND_758 に相当する代替。 */
    public static int mapSound498To758(int id498) {
        if (id498 < 0 || id498 >= SOUND_498_TO_758_MAP.length) {
            logUnknownComposedOnce("498→758", "音", id498);
            return GENERIC_SOUND_758;
        }
        return SOUND_498_TO_758_MAP[id498];
    }

    /** 758のパーティクルID → 498のパーティクルID。範囲外はAMBIENT_PARTICLE_498 に相当する代替。 */
    public static int mapParticle758To498(int id758) {
        if (id758 < 0 || id758 >= PARTICLE_758_TO_498_MAP.length) {
            logUnknownComposedOnce("758→498", "パーティクル", id758);
            return AMBIENT_PARTICLE_498;
        }
        return PARTICLE_758_TO_498_MAP[id758];
    }

    /** 498のパーティクルID → 758のパーティクルID。範囲外はAMBIENT_PARTICLE_758 に相当する代替。 */
    public static int mapParticle498To758(int id498) {
        if (id498 < 0 || id498 >= PARTICLE_498_TO_758_MAP.length) {
            logUnknownComposedOnce("498→758", "パーティクル", id498);
            return AMBIENT_PARTICLE_758;
        }
        return PARTICLE_498_TO_758_MAP[id498];
    }

    /** 758のブロックID → 498のブロックID。範囲外はSTONE_BLOCK_498 に相当する代替。 */
    public static int mapBlock758To498(int id758) {
        if (id758 < 0 || id758 >= BLOCK_758_TO_498_MAP.length) {
            logUnknownComposedOnce("758→498", "ブロック", id758);
            return STONE_BLOCK_498;
        }
        return BLOCK_758_TO_498_MAP[id758];
    }

    /** 498のブロックID → 758のブロックID。範囲外はSTONE_BLOCK_758 に相当する代替。 */
    public static int mapBlock498To758(int id498) {
        if (id498 < 0 || id498 >= BLOCK_498_TO_758_MAP.length) {
            logUnknownComposedOnce("498→758", "ブロック", id498);
            return STONE_BLOCK_758;
        }
        return BLOCK_498_TO_758_MAP[id498];
    }

    /** 758のメニュー種別ID → 498のメニュー種別ID。範囲外はCRAFTING_MENU_498 に相当する代替。 */
    public static int mapMenu758To498(int id758) {
        if (id758 < 0 || id758 >= MENU_758_TO_498_MAP.length) {
            logUnknownComposedOnce("758→498", "メニュー", id758);
            return CRAFTING_MENU_498;
        }
        return MENU_758_TO_498_MAP[id758];
    }

    /** 498のメニュー種別ID → 758のメニュー種別ID。範囲外はCRAFTING_MENU_758 に相当する代替。 */
    public static int mapMenu498To758(int id498) {
        if (id498 < 0 || id498 >= MENU_498_TO_758_MAP.length) {
            logUnknownComposedOnce("498→758", "メニュー", id498);
            return CRAFTING_MENU_758;
        }
        return MENU_498_TO_758_MAP[id498];
    }

    /**
     * 758のブロックエンティティ種別ID → 498のブロックエンティティ種別ID。
     * 758にしか無い sculk_sensor と beehive は DROP。
     */
    public static int mapBlockEntityType758To498(int id758) {
        if (id758 < 0 || id758 >= BLOCK_ENTITY_758_TO_498_MAP.length) {
            logUnknownComposedOnce("758→498", "ブロックエンティティ種別", id758);
            return DROP;
        }
        return BLOCK_ENTITY_758_TO_498_MAP[id758];
    }

    /**
     * 498のブロックエンティティ種別ID → 758のブロックエンティティ種別ID。
     * 498にしか無い種別は無いため DROP にはならない。
     */
    public static int mapBlockEntityType498To758(int id498) {
        if (id498 < 0 || id498 >= BLOCK_ENTITY_498_TO_758_MAP.length) {
            logUnknownComposedOnce("498→758", "ブロックエンティティ種別", id498);
            return DROP;
        }
        return BLOCK_ENTITY_498_TO_758_MAP[id498];
    }

    /** 758のバイオームID → 498のバイオームID。範囲外はPLAINS_BIOME_498 に相当する代替。 */
    public static int mapBiome758To498(int id758) {
        if (id758 < 0 || id758 >= BIOME_758_TO_498_MAP.length) {
            logUnknownComposedOnce("758→498", "バイオーム", id758);
            return PLAINS_BIOME_498;
        }
        return BIOME_758_TO_498_MAP[id758];
    }

    /** 498のバイオームID → 758のバイオームID。範囲外はPLAINS_BIOME_758 に相当する代替。 */
    public static int mapBiome498To758(int id498) {
        if (id498 < 0 || id498 >= BIOME_498_TO_758_MAP.length) {
            logUnknownComposedOnce("498→758", "バイオーム", id498);
            return PLAINS_BIOME_758;
        }
        return BIOME_498_TO_758_MAP[id498];
    }

    /** 498 のバイオーム名（レジストリ順）。Bedrock 側が次元情報に使う。 */
    public static java.util.List<String> biomeNames498() {
        return java.util.List.of(RegistryTables498.BIOME_NAMES_498);
    }

    /**
     * 758のダメージ種別ID → 498のダメージ種別ID（恒等写像。codec には無い）。
     */
    public static int mapDamageType758To498(int id758) {
        if (id758 < 0 || id758 > MAX_DAMAGE_TYPE_758) {
            logUnknownOnce(PROTOCOL_498, "ダメージ種別", id758);
            return DROP;
        }
        return DAMAGE_TYPE_758_TO_498_MAP[id758];
    }

    /** 498のダメージ種別ID → 758のダメージ種別ID（恒等写像。codec には無い）。 */
    public static int mapDamageType498To758(int id498) {
        if (id498 < 0 || id498 > MAX_DAMAGE_TYPE_498) {
            logUnknownOnce(PROTOCOL_758, "ダメージ種別", id498);
            return DROP;
        }
        return DAMAGE_TYPE_498_TO_758_MAP[id498];
    }

    /** 758の音の名前 → 498の音の名前（Stop Sound 用）。数値の合成表と一致させる。 */
    public static String mapSoundName758To498(String name) {
        return mapSoundNameComposed(name, SOUND_NAME_FALLBACK_758_TO_498_MAP);
    }

    /** 498の音の名前 → 758の音の名前（Stop Sound 用）。数値の合成表と一致させる。 */
    public static String mapSoundName498To758(String name) {
        return mapSoundNameComposed(name, SOUND_NAME_FALLBACK_498_TO_758_MAP);
    }
"""


def main():
    text = P.read_text(encoding="utf-8")
    if "PROTOCOL_498" in text:
        print("skip（既存）: 498 はすでにある")
        return 0

    # 1. static import。
    text = rep(text,
               "import static io.detergentmc.protocol.RegistryTables578.*;",
               "import static io.detergentmc.protocol.RegistryTables498.*;\n"
               "import static io.detergentmc.protocol.RegistryTables578.*;")

    # 2. PROTOCOL_498 定数。
    text = rep(text,
               "    /** 573（1.15）のプロトコル番号。 */\n"
               "    public static final int PROTOCOL_573 = 573;",
               "    /** 498（1.14.4）のプロトコル番号。 */\n"
               "    public static final int PROTOCOL_498 = 498;\n"
               "    /** 573（1.15）のプロトコル番号。 */\n"
               "    public static final int PROTOCOL_573 = 573;")

    # 3. MAX_*_498 定数群（573 の定数群の直後）。
    anchor = "    public static final int GENERIC_DAMAGE_TYPE_573 = GENERIC_DAMAGE_TYPE_578;"
    text = rep(text, anchor, anchor + "\n" + CONSTANTS)

    # 4. 合成表フィールド（578 の DAMAGE_TYPE 表の直後、759 の表の前）。
    anchor = ("    /** 759のブロック状態ID → 758のブロック状態ID（RegistryTables758）。 */")
    text = rep(text, anchor, MAP_FIELDS.strip("\n") + "\n" + anchor)

    # 5. 音の名前の代替マップ（578 の直後）。
    anchor = ("    /** 578の音の名前 → 758の音の名前（Stop Sound 用）。数値の合成表と一致させる。 */\n"
              "    private static final Map<String, String> SOUND_NAME_FALLBACK_578_TO_758_MAP =\n"
              "            buildSoundNameMap(SOUND_NAME_FALLBACKS_578_TO_758);")
    text = rep(text, anchor, anchor + "\n\n"
               "    /** 758の音の名前 → 498の音の名前（Stop Sound 用）。数値の合成表と一致させる。 */\n"
               "    private static final Map<String, String> SOUND_NAME_FALLBACK_758_TO_498_MAP =\n"
               "            buildSoundNameMap(SOUND_NAME_FALLBACKS_758_TO_498);\n\n"
               "    /** 498の音の名前 → 758の音の名前（Stop Sound 用）。数値の合成表と一致させる。 */\n"
               "    private static final Map<String, String> SOUND_NAME_FALLBACK_498_TO_758_MAP =\n"
               "            buildSoundNameMap(SOUND_NAME_FALLBACKS_498_TO_758);")

    # 6. メソッド群（573 セクションの直後、760⇔759 セクションの前）。
    anchor = "    // 760 ⇔ 759（1.19.1／1.19.2 ⇔ 1.19）"
    text = rep(text, anchor, METHODS.strip("\n") + "\n" + anchor)

    P.write_text(text, encoding="utf-8")
    print("RegistryRemapper.java を更新した")
    return 0


if __name__ == "__main__":
    sys.exit(main())
