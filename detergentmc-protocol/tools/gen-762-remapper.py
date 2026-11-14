#!/usr/bin/env python3
"""RegistryRemapper.java に 762（1.19.4）対応を差し込む。

アンカー付き挿入のみ行う。アンカーが見つからなければ失敗して何も書かない。
使い方: python3 gen-762-remapper.py
書き換えた後は必ず git diff で内容を確認してからコミットすること。
"""
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parent.parent
P = ROOT / "src/main/java/io/detergentmc/protocol/RegistryRemapper.java"

IMPORT = "import static io.detergentmc.protocol.RegistryTables762.*;\n"

PROTOCOL_CONST = "    /** 762（1.19.4）のプロトコル番号。 */\n    public static final int PROTOCOL_762 = 762;\n"

CONSTANTS_762 = """
    /** 762側の最大の番号（ViaVersion mapping-1.19.4.json の件数−1）。 */
    public static final int MAX_BLOCK_STATE_762 = 23724;
    public static final int MAX_ITEM_762 = 1227;
    public static final int MAX_ENTITY_TYPE_762 = 123;
    public static final int MAX_SOUND_762 = 1453;
    public static final int MAX_PARTICLE_762 = 95;
    public static final int MAX_BLOCK_762 = 997;
    public static final int MAX_MENU_762 = 24;
    /** 762側のエンチャントは 0〜38（39件。763と同一順序のため表は作らない）。 */
    public static final int MAX_ENCHANTMENT_762 = 38;
    /** 762側のバイオームは 0〜62（63件。cherry_grove が無い）。 */
    public static final int MAX_BIOME_762 = 62;
    /** 763側のバイオームは 0〜63（64件。cherry_grove を含む）。 */
    public static final int MAX_BIOME_763 = 63;
    /** 762側のダメージ種別は 0〜41（42件）。 */
    public static final int MAX_DAMAGE_TYPE_762 = 41;
    /** 763側のダメージ種別は 0〜43（44件）。 */
    public static final int MAX_DAMAGE_TYPE_763 = 43;
    /** 763側の最大の番号（762の表の入力サイズ用。ViaVersion mapping-1.20.json）。 */
    public static final int MAX_ENTITY_TYPE_763 = 123;
    public static final int MAX_SOUND_763 = 1473;
    public static final int MAX_PARTICLE_763 = 94;
    public static final int MAX_BLOCK_763 = 1002;
    public static final int MAX_MENU_763 = 23;
    public static final int MAX_BLOCK_ENTITY_763 = 40;
    /** 762側のブロックエンティティ種別は 0〜39（40件）。 */
    public static final int MAX_BLOCK_ENTITY_762 = 39;

    /** 762側の代替先（mapping-1.19.4.json の番号。Speed だけ 1ベースで 1）。 */
    public static final int STONE_BLOCK_STATE_762 = 1;
    public static final int AIR_BLOCK_STATE_762 = 0;
    public static final int STONE_ITEM_762 = 1;
    public static final int PIG_ENTITY_TYPE_762 = 72;
    public static final int GENERIC_SOUND_762 = 1245; // block.stone.break
    public static final int AMBIENT_PARTICLE_762 = 0; // ambient_entity_effect
    public static final int STONE_BLOCK_762 = 1;
    public static final int CRAFTING_MENU_762 = 11;
    public static final int SPEED_EFFECT_762 = 1;
    /** 762側の平原バイオーム（minecraft-data pc/1.19.4/biomes.json）。 */
    public static final int PLAINS_BIOME_762 = 38;
    /** 763側の平原バイオーム（minecraft-data pc/1.20/biomes.json）。 */
    public static final int PLAINS_BIOME_763 = 39;
    /** 762側の generic ダメージ種別（loginPacket の codec）。 */
    public static final int GENERIC_DAMAGE_TYPE_762 = 16;
    /** 763側の generic ダメージ種別（loginPacket の codec）。 */
    public static final int GENERIC_DAMAGE_TYPE_763 = 16;
"""

MAPS_762 = """
    /** 763のブロック状態ID → 762のブロック状態ID（RegistryTables762）。 */
    private static final int[] BLOCK_STATE_763_TO_762_MAP =
            expand(BLOCK_STATE_762_RANGES, BLOCK_STATE_762_OVERRIDES, MAX_BLOCK_STATE_763 + 1,
                    STONE_BLOCK_STATE_762);
    /** 762のブロック状態ID → 763のブロック状態ID（RegistryTables762）。 */
    private static final int[] BLOCK_STATE_762_TO_763_MAP =
            expand(BLOCK_STATE_762_TO_763_RANGES, BLOCK_STATE_762_TO_763_OVERRIDES,
                    MAX_BLOCK_STATE_762 + 1, STONE_BLOCK_STATE_763);
    /** 763のアイテムID → 762のアイテムID。 */
    private static final int[] ITEM_763_TO_762_MAP =
            expand(ITEM_762_RANGES, ITEM_762_OVERRIDES, MAX_ITEM_763 + 1, STONE_ITEM_762);
    /** 762のアイテムID → 763のアイテムID。 */
    private static final int[] ITEM_762_TO_763_MAP =
            expand(ITEM_762_TO_763_RANGES, ITEM_762_TO_763_OVERRIDES, MAX_ITEM_762 + 1,
                    STONE_ITEM_763);
    /** 763のエンティティ種別ID → 762のエンティティ種別ID（同一順序の恒等写像）。 */
    private static final int[] ENTITY_TYPE_763_TO_762_MAP =
            expand(ENTITY_TYPE_762_RANGES, ENTITY_TYPE_762_OVERRIDES, MAX_ENTITY_TYPE_763 + 1,
                    PIG_ENTITY_TYPE_762);
    /** 762のエンティティ種別ID → 763のエンティティ種別ID（同一順序の恒等写像）。 */
    private static final int[] ENTITY_TYPE_762_TO_763_MAP =
            expand(ENTITY_TYPE_762_TO_763_RANGES, ENTITY_TYPE_762_TO_763_OVERRIDES,
                    MAX_ENTITY_TYPE_762 + 1, PIG_ENTITY_TYPE_763);
    /** 763の音ID → 762の音ID。 */
    private static final int[] SOUND_763_TO_762_MAP =
            expand(SOUND_762_RANGES, SOUND_762_OVERRIDES, MAX_SOUND_763 + 1, GENERIC_SOUND_762);
    /** 762の音ID → 763の音ID。 */
    private static final int[] SOUND_762_TO_763_MAP =
            expand(SOUND_762_TO_763_RANGES, SOUND_762_TO_763_OVERRIDES, MAX_SOUND_762 + 1,
                    GENERIC_SOUND_763);
    /** 763のパーティクルID → 762のパーティクルID。 */
    private static final int[] PARTICLE_763_TO_762_MAP =
            expand(PARTICLE_762_RANGES, PARTICLE_762_OVERRIDES, MAX_PARTICLE_763 + 1,
                    AMBIENT_PARTICLE_762);
    /** 762のパーティクルID → 763のパーティクルID。 */
    private static final int[] PARTICLE_762_TO_763_MAP =
            expand(PARTICLE_762_TO_763_RANGES, PARTICLE_762_TO_763_OVERRIDES,
                    MAX_PARTICLE_762 + 1, AMBIENT_PARTICLE_763);
    /** 763のブロックID → 762のブロックID。 */
    private static final int[] BLOCK_763_TO_762_MAP =
            expand(BLOCK_762_RANGES, BLOCK_762_OVERRIDES, MAX_BLOCK_763 + 1, STONE_BLOCK_762);
    /** 762のブロックID → 763のブロックID。 */
    private static final int[] BLOCK_762_TO_763_MAP =
            expand(BLOCK_762_TO_763_RANGES, BLOCK_762_TO_763_OVERRIDES, MAX_BLOCK_762 + 1,
                    STONE_BLOCK_763);
    /** 763のメニュー種別ID → 762のメニュー種別ID（legacy_smithing は smithing へ）。 */
    private static final int[] MENU_763_TO_762_MAP =
            expand(MENU_762_RANGES, MENU_762_OVERRIDES, MAX_MENU_763 + 1, CRAFTING_MENU_762);
    /** 762のメニュー種別ID → 763のメニュー種別ID。 */
    private static final int[] MENU_762_TO_763_MAP =
            expand(MENU_762_TO_763_RANGES, MENU_762_TO_763_OVERRIDES, MAX_MENU_762 + 1,
                    CRAFTING_MENU_763);
    /** 763のブロックエンティティ種別ID → 762のブロックエンティティ種別ID。無い種別は DROP。 */
    private static final int[] BLOCK_ENTITY_763_TO_762_MAP =
            expand(BLOCK_ENTITY_762_RANGES, BLOCK_ENTITY_762_OVERRIDES, MAX_BLOCK_ENTITY_763 + 1,
                    DROP);
    /** 762のブロックエンティティ種別ID → 763のブロックエンティティ種別ID。無い種別は DROP。 */
    private static final int[] BLOCK_ENTITY_762_TO_763_MAP =
            expand(BLOCK_ENTITY_762_TO_763_RANGES, BLOCK_ENTITY_762_TO_763_OVERRIDES,
                    MAX_BLOCK_ENTITY_762 + 1, DROP);
    /** 763のバイオームID → 762のバイオームID（cherry_grove は meadow へ）。 */
    private static final int[] BIOME_763_TO_762_MAP =
            expand(BIOME_762_RANGES, BIOME_762_OVERRIDES, MAX_BIOME_763 + 1, PLAINS_BIOME_762);
    /** 762のバイオームID → 763のバイオームID。 */
    private static final int[] BIOME_762_TO_763_MAP =
            expand(BIOME_762_TO_763_RANGES, BIOME_762_TO_763_OVERRIDES, MAX_BIOME_762 + 1,
                    PLAINS_BIOME_763);
    /** 763のダメージ種別ID → 762のダメージ種別ID。無い種別は generic へ。 */
    private static final int[] DAMAGE_TYPE_763_TO_762_MAP =
            expand(DAMAGE_TYPE_762_RANGES, DAMAGE_TYPE_762_OVERRIDES, MAX_DAMAGE_TYPE_763 + 1,
                    GENERIC_DAMAGE_TYPE_762);
    /** 762のダメージ種別ID → 763のダメージ種別ID。 */
    private static final int[] DAMAGE_TYPE_762_TO_763_MAP =
            expand(DAMAGE_TYPE_762_TO_763_RANGES, DAMAGE_TYPE_762_TO_763_OVERRIDES,
                    MAX_DAMAGE_TYPE_762 + 1, GENERIC_DAMAGE_TYPE_763);
"""

SOUND_NAME_MAP_762 = """
    /** 763の音の名前 → 762の音の名前（Stop Sound 用）。数値の合成表と一致させる。 */
    private static final Map<String, String> SOUND_NAME_FALLBACK_763_TO_762_MAP =
            buildSoundNameMap(SOUND_NAME_FALLBACKS_763_TO_762);
"""

SOUND_NAME_MAP_762_REV = """
    /** 762の音の名前 → 763の音の名前（Stop Sound 用）。数値の合成表と一致させる。 */
    private static final Map<String, String> SOUND_NAME_FALLBACK_762_TO_763_MAP =
            buildSoundNameMap(SOUND_NAME_FALLBACKS_762_TO_763);
"""

METHODS_762 = """
    // ------------------------------------------------------------------
    // 762 ⇔ 763（1.19.4 ⇔ 1.20／1.20.1）
    // パケットIDは同一、ペイロードの差は Join Game／Respawn 末尾の
    // portalCooldown、Chunk Data／Update Light の trustEdges、
    // Multi Block Change の suppressLightUpdates、戦闘イベントの entityId、
    // 看板の isFrontText、Advancements の sendsTelemetryData のみ。
    // レジストリは RegistryTables762（ViaVersion mapping-1.19.4／1.20.json
    // から名前で突き合わせたもの）を使う。
    // ------------------------------------------------------------------

    /** 763のブロック状態ID → 762のブロック状態ID。範囲外はSTONE_BLOCK_STATE_762 に相当する代替。 */
    public static int mapBlockState763To762(int id763) {
        if (id763 < 0 || id763 >= BLOCK_STATE_763_TO_762_MAP.length) {
            logUnknownComposedOnce("763→762", "ブロック状態", id763);
            return STONE_BLOCK_STATE_762;
        }
        return BLOCK_STATE_763_TO_762_MAP[id763];
    }

    /** 762のブロック状態ID → 763のブロック状態ID。範囲外はSTONE_BLOCK_STATE_763 に相当する代替。 */
    public static int mapBlockState762To763(int id762) {
        if (id762 < 0 || id762 >= BLOCK_STATE_762_TO_763_MAP.length) {
            logUnknownComposedOnce("762→763", "ブロック状態", id762);
            return STONE_BLOCK_STATE_763;
        }
        return BLOCK_STATE_762_TO_763_MAP[id762];
    }

    /** 763のアイテムID → 762のアイテムID。範囲外はSTONE_ITEM_762 に相当する代替。 */
    public static int mapItem763To762(int id763) {
        if (id763 < 0 || id763 >= ITEM_763_TO_762_MAP.length) {
            logUnknownComposedOnce("763→762", "アイテム", id763);
            return STONE_ITEM_762;
        }
        return ITEM_763_TO_762_MAP[id763];
    }

    /** 762のアイテムID → 763のアイテムID。範囲外はSTONE_ITEM_763 に相当する代替。 */
    public static int mapItem762To763(int id762) {
        if (id762 < 0 || id762 >= ITEM_762_TO_763_MAP.length) {
            logUnknownComposedOnce("762→763", "アイテム", id762);
            return STONE_ITEM_763;
        }
        return ITEM_762_TO_763_MAP[id762];
    }

    /** 763のエンティティ種別ID → 762のエンティティ種別ID（同一順序）。範囲外はPIG_ENTITY_TYPE_762 に相当する代替。 */
    public static int mapEntityType763To762(int id763) {
        if (id763 < 0 || id763 >= ENTITY_TYPE_763_TO_762_MAP.length) {
            logUnknownComposedOnce("763→762", "エンティティ種別", id763);
            return PIG_ENTITY_TYPE_762;
        }
        return ENTITY_TYPE_763_TO_762_MAP[id763];
    }

    /** 762のエンティティ種別ID → 763のエンティティ種別ID（同一順序）。範囲外はPIG_ENTITY_TYPE_763 に相当する代替。 */
    public static int mapEntityType762To763(int id762) {
        if (id762 < 0 || id762 >= ENTITY_TYPE_762_TO_763_MAP.length) {
            logUnknownComposedOnce("762→763", "エンティティ種別", id762);
            return PIG_ENTITY_TYPE_763;
        }
        return ENTITY_TYPE_762_TO_763_MAP[id762];
    }

    /** 763の音ID → 762の音ID。範囲外はGENERIC_SOUND_762 に相当する代替。 */
    public static int mapSound763To762(int id763) {
        if (id763 < 0 || id763 >= SOUND_763_TO_762_MAP.length) {
            logUnknownComposedOnce("763→762", "音", id763);
            return GENERIC_SOUND_762;
        }
        return SOUND_763_TO_762_MAP[id763];
    }

    /** 762の音ID → 763の音ID。範囲外はGENERIC_SOUND_763 に相当する代替。 */
    public static int mapSound762To763(int id762) {
        if (id762 < 0 || id762 >= SOUND_762_TO_763_MAP.length) {
            logUnknownComposedOnce("762→763", "音", id762);
            return GENERIC_SOUND_763;
        }
        return SOUND_762_TO_763_MAP[id762];
    }

    /** 763のパーティクルID → 762のパーティクルID。範囲外はAMBIENT_PARTICLE_762 に相当する代替。 */
    public static int mapParticle763To762(int id763) {
        if (id763 < 0 || id763 >= PARTICLE_763_TO_762_MAP.length) {
            logUnknownComposedOnce("763→762", "パーティクル", id763);
            return AMBIENT_PARTICLE_762;
        }
        return PARTICLE_763_TO_762_MAP[id763];
    }

    /** 762のパーティクルID → 763のパーティクルID。範囲外はAMBIENT_PARTICLE_763 に相当する代替。 */
    public static int mapParticle762To763(int id762) {
        if (id762 < 0 || id762 >= PARTICLE_762_TO_763_MAP.length) {
            logUnknownComposedOnce("762→763", "パーティクル", id762);
            return AMBIENT_PARTICLE_763;
        }
        return PARTICLE_762_TO_763_MAP[id762];
    }

    /** 763のブロックID → 762のブロックID。範囲外はSTONE_BLOCK_762 に相当する代替。 */
    public static int mapBlock763To762(int id763) {
        if (id763 < 0 || id763 >= BLOCK_763_TO_762_MAP.length) {
            logUnknownComposedOnce("763→762", "ブロック", id763);
            return STONE_BLOCK_762;
        }
        return BLOCK_763_TO_762_MAP[id763];
    }

    /** 762のブロックID → 763のブロックID。範囲外はSTONE_BLOCK_763 に相当する代替。 */
    public static int mapBlock762To763(int id762) {
        if (id762 < 0 || id762 >= BLOCK_762_TO_763_MAP.length) {
            logUnknownComposedOnce("762→763", "ブロック", id762);
            return STONE_BLOCK_763;
        }
        return BLOCK_762_TO_763_MAP[id762];
    }

    /** 763のメニュー種別ID → 762のメニュー種別ID。範囲外はCRAFTING_MENU_762 に相当する代替。 */
    public static int mapMenu763To762(int id763) {
        if (id763 < 0 || id763 >= MENU_763_TO_762_MAP.length) {
            logUnknownComposedOnce("763→762", "メニュー", id763);
            return CRAFTING_MENU_762;
        }
        return MENU_763_TO_762_MAP[id763];
    }

    /** 762のメニュー種別ID → 763のメニュー種別ID。範囲外はCRAFTING_MENU_763 に相当する代替。 */
    public static int mapMenu762To763(int id762) {
        if (id762 < 0 || id762 >= MENU_762_TO_763_MAP.length) {
            logUnknownComposedOnce("762→763", "メニュー", id762);
            return CRAFTING_MENU_763;
        }
        return MENU_762_TO_763_MAP[id762];
    }

    /**
     * 763のブロックエンティティ種別ID → 762のブロックエンティティ種別ID。
     * 763にしか無い calibrated_sculk_sensor は {@link #DROP}。
     */
    public static int mapBlockEntityType763To762(int id763) {
        if (id763 < 0 || id763 >= BLOCK_ENTITY_763_TO_762_MAP.length) {
            logUnknownComposedOnce("763→762", "ブロックエンティティ種別", id763);
            return DROP;
        }
        return BLOCK_ENTITY_763_TO_762_MAP[id763];
    }

    /**
     * 762のブロックエンティティ種別ID → 763のブロックエンティティ種別ID。
     * 762にしか無い suspicious_sand は brushable_block へ写すため DROP にはならない。
     */
    public static int mapBlockEntityType762To763(int id762) {
        if (id762 < 0 || id762 >= BLOCK_ENTITY_762_TO_763_MAP.length) {
            logUnknownComposedOnce("762→763", "ブロックエンティティ種別", id762);
            return DROP;
        }
        return BLOCK_ENTITY_762_TO_763_MAP[id762];
    }

    /** 763のバイオームID → 762のバイオームID。範囲外はPLAINS_BIOME_762 に相当する代替。 */
    public static int mapBiome763To762(int id763) {
        if (id763 < 0 || id763 >= BIOME_763_TO_762_MAP.length) {
            logUnknownComposedOnce("763→762", "バイオーム", id763);
            return PLAINS_BIOME_762;
        }
        return BIOME_763_TO_762_MAP[id763];
    }

    /** 762のバイオームID → 763のバイオームID。範囲外はPLAINS_BIOME_763 に相当する代替。 */
    public static int mapBiome762To763(int id762) {
        if (id762 < 0 || id762 >= BIOME_762_TO_763_MAP.length) {
            logUnknownComposedOnce("762→763", "バイオーム", id762);
            return PLAINS_BIOME_763;
        }
        return BIOME_762_TO_763_MAP[id762];
    }

    /**
     * 763のダメージ種別ID → 762のダメージ種別ID。
     * 763にしか無い generic_kill・outside_border は generic へ。
     */
    public static int mapDamageType763To762(int id763) {
        if (id763 < 0 || id763 > MAX_DAMAGE_TYPE_763) {
            logUnknownOnce(PROTOCOL_762, "ダメージ種別", id763);
            return DROP;
        }
        return DAMAGE_TYPE_763_TO_762_MAP[id763];
    }

    /** 762のダメージ種別ID → 763のダメージ種別ID。 */
    public static int mapDamageType762To763(int id762) {
        if (id762 < 0 || id762 > MAX_DAMAGE_TYPE_762) {
            logUnknownOnce(PROTOCOL_763, "ダメージ種別", id762);
            return DROP;
        }
        return DAMAGE_TYPE_762_TO_763_MAP[id762];
    }

    /** 763の音の名前 → 762の音の名前（Stop Sound 用）。数値の合成表と一致させる。 */
    public static String mapSoundName763To762(String name) {
        return mapSoundNameComposed(name, SOUND_NAME_FALLBACK_763_TO_762_MAP);
    }

    /** 762の音の名前 → 763の音の名前（Stop Sound 用）。数値の合成表と一致させる。 */
    public static String mapSoundName762To763(String name) {
        return mapSoundNameComposed(name, SOUND_NAME_FALLBACK_762_TO_763_MAP);
    }
"""

# 765→762 の合成（target 分岐用）。(メソッド名, 765→763 の式, 763→762 の式)
DISPATCH_762 = [
    ("mapBlockState", "mapBlockState(id765)", "mapBlockState763To762"),
    ("mapItem", "mapItem(id765)", "mapItem763To762"),
    ("mapItemReverse", "mapItemReverse(mapItem762To763(id))", None),
    ("mapEntityType", "mapEntityType(id765)", "mapEntityType763To762"),
    ("mapSound", "mapSound(id765)", "mapSound763To762"),
    ("mapParticle", "mapParticle(id765)", "mapParticle763To762"),
    ("mapBlock", "mapBlock(id765)", "mapBlock763To762"),
    ("mapMenu", "mapMenu(id765)", "mapMenu763To762"),
    ("mapEffect", "mapEffect(id765)", None),
    ("mapBlockEntityType", "mapBlockEntityType(id765)", "mapBlockEntityType763To762"),
]


def main():
    src = P.read_text(encoding="utf-8")

    def insert_before(anchor, text, what, marker):
        nonlocal src
        if marker in src:
            print(f"skip（既存）: {what}")
            return
        idx = src.find(anchor)
        if idx < 0:
            print(f"アンカーが見つからない: {what}: {anchor[:80]!r}", file=sys.stderr)
            raise SystemExit(1)
        src = src[:idx] + text + src[idx:]

    def insert_after(anchor, text, what, marker):
        nonlocal src
        if marker in src:
            print(f"skip（既存）: {what}")
            return
        idx = src.find(anchor)
        if idx < 0:
            print(f"アンカーが見つからない: {what}: {anchor[:80]!r}", file=sys.stderr)
            raise SystemExit(1)
        idx += len(anchor)
        src = src[:idx] + text + src[idx:]

    # 1. import
    insert_before("import static io.detergentmc.protocol.RegistryTables773Down767.*;",
                  IMPORT, "import RegistryTables762", "RegistryTables762.*")
    # 2. PROTOCOL_762（PROTOCOL_763 の直前）
    insert_before("    public static final int PROTOCOL_763 = 763;",
                  PROTOCOL_CONST, "PROTOCOL_762", "PROTOCOL_762 = 762")
    # 3. 762定数（SPEED_EFFECT_763 の直後）
    insert_after("    public static final int SPEED_EFFECT_763 = 1;\n",
                 CONSTANTS_762, "762定数", "MAX_BLOCK_STATE_762")
    # 4. 762の表（BLOCK_STATE_767_MAP の直前）
    insert_before("    private static final int[] BLOCK_STATE_767_MAP =",
                  MAPS_762, "762の表", "BLOCK_STATE_763_TO_762_MAP")
    # 5. 音の名前表（SOUND_NAME_FALLBACK_767_MAP の直前）
    insert_before("    private static final Map<String, String> SOUND_NAME_FALLBACK_767_MAP =",
                  SOUND_NAME_MAP_762, "音の名前表762", "SOUND_NAME_FALLBACK_763_TO_762_MAP")
    insert_before("    private static final Map<String, String> SOUND_NAME_FALLBACK_767_MAP =",
                  SOUND_NAME_MAP_762_REV, "音の名前表762逆向き", "SOUND_NAME_FALLBACK_762_TO_763_MAP")
    # 6. アクセサメソッド（mapBlockState766To763 の javadoc の直前）
    insert_before("    /** 766のブロック状態ID → 763のブロック状態ID。",
                  METHODS_762, "762アクセサ", "mapBlockState763To762")
    # 6b. 音の名前の逆向きメソッド（mapSoundName763To762 の直後）
    insert_after("    public static String mapSoundName763To762(String name) {\n"
                 "        return mapSoundNameComposed(name, SOUND_NAME_FALLBACK_763_TO_762_MAP);\n"
                 "    }\n",
                 "\n"
                 "    /** 762の音の名前 → 763の音の名前（Stop Sound 用）。数値の合成表と一致させる。 */\n"
                 "    public static String mapSoundName762To763(String name) {\n"
                 "        return mapSoundNameComposed(name, SOUND_NAME_FALLBACK_762_TO_763_MAP);\n"
                 "    }\n",
                 "音の名前逆向きメソッド", "mapSoundName762To763(String name)")
    # 7. target 分岐への 762 追加（各 dispatch メソッドの 766 分岐の直前）
    for name, expr765, f762 in DISPATCH_762:
        if name == "mapItemReverse":
            body = (f"        if (clientProtocol == PROTOCOL_762) {{\n"
                    f"            return mapItemReverse(mapItem762To763(id));\n"
                    f"        }}\n")
            anchor = f"    public static int {name}(int id, int clientProtocol) {{\n"
            marker = "mapItemReverse(mapItem762To763(id))"
        elif name == "mapEffect":
            body = (f"        if (clientProtocol == PROTOCOL_762) {{\n"
                    f"            return mapEffect(id765);\n"
                    f"        }}\n")
            anchor = f"    public static int {name}(int id765, int clientProtocol) {{\n"
            marker = "mapEffect(id765);\n        }\n        if (clientProtocol == PROTOCOL_766)"
        else:
            body = (f"        if (clientProtocol == PROTOCOL_762) {{\n"
                    f"            return {f762}({expr765});\n"
                    f"        }}\n")
            anchor = f"    public static int {name}(int id765, int clientProtocol) {{\n"
            marker = f"return {f762}({expr765});"
        insert_after(anchor, body, f"dispatch {name} 762", marker)
    # 8. mapSoundName 分岐への 762 追加
    insert_after("    public static String mapSoundName(String name, int clientProtocol) {\n",
                 "        if (clientProtocol == PROTOCOL_762) {\n"
                 "            return mapSoundName763To762(mapSoundName(name));\n"
                 "        }\n",
                 "dispatch mapSoundName 762", "mapSoundName763To762(mapSoundName(name))")
    P.write_text(src, encoding="utf-8")
    print("RegistryRemapper.java に 762 を差し込んだ。")


if __name__ == "__main__":
    raise SystemExit(main())
