#!/usr/bin/env python3
"""RegistryRemapper.java に 761（1.19.3）対応を差し込む。

アンカー付き挿入のみ行う。アンカーが見つからなければ失敗して何も書かない。
使い方: python3 gen-761-remapper.py
書き換えた後は必ず git diff で内容を確認してからコミットすること。
"""
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parent.parent
P = ROOT / "src/main/java/io/detergentmc/protocol/RegistryRemapper.java"

IMPORT = "import static io.detergentmc.protocol.RegistryTables761.*;\n"

PROTOCOL_CONST = "    /** 761（1.19.3）のプロトコル番号。 */\n    public static final int PROTOCOL_761 = 761;\n"

CONSTANTS_761 = """
    /** 761側の最大の番号（ViaVersion mapping-1.19.3.json の件数−1）。 */
    public static final int MAX_BLOCK_STATE_761 = 23231;
    public static final int MAX_ITEM_761 = 1185;
    public static final int MAX_ENTITY_TYPE_761 = 118;
    public static final int MAX_SOUND_761 = 1391;
    public static final int MAX_PARTICLE_761 = 92;
    public static final int MAX_BLOCK_761 = 971;
    public static final int MAX_MENU_761 = 23;
    /** 761側のエンチャントは 0〜38（39件。762と同一順序のため表は作らない）。 */
    public static final int MAX_ENCHANTMENT_761 = 38;
    /** 761側のバイオームは 0〜62（63件。762と同一）。 */
    public static final int MAX_BIOME_761 = 62;
    /** 761側のダメージ種別は 0〜41（42件。762と同一順序と仮定。実バニラで検証する）。 */
    public static final int MAX_DAMAGE_TYPE_761 = 41;
    /** 761側のブロックエンティティ種別は 0〜37（38件）。 */
    public static final int MAX_BLOCK_ENTITY_761 = 37;

    /** 761側の代替先（mapping-1.19.3.json の番号。Speed だけ 1ベースで 1）。 */
    public static final int STONE_BLOCK_STATE_761 = 1;
    public static final int AIR_BLOCK_STATE_761 = 0;
    public static final int STONE_ITEM_761 = 1;
    public static final int PIG_ENTITY_TYPE_761 = 68;
    public static final int GENERIC_SOUND_761 = 1183; // block.stone.break
    public static final int AMBIENT_PARTICLE_761 = 0; // ambient_entity_effect
    public static final int STONE_BLOCK_761 = 1;
    public static final int CRAFTING_MENU_761 = 11;
    public static final int SPEED_EFFECT_761 = 1;
    /** 761側の平原バイオーム（minecraft-data pc/1.19.3/biomes.json）。 */
    public static final int PLAINS_BIOME_761 = 38;
    /** 761側の generic ダメージ種別（762 と同一順序と仮定）。 */
    public static final int GENERIC_DAMAGE_TYPE_761 = 16;
"""

MAPS_761 = """
    /** 762のブロック状態ID → 761のブロック状態ID（RegistryTables761）。 */
    private static final int[] BLOCK_STATE_762_TO_761_MAP =
            expand(BLOCK_STATE_761_RANGES, BLOCK_STATE_761_OVERRIDES, MAX_BLOCK_STATE_762 + 1,
                    STONE_BLOCK_STATE_761);
    /** 761のブロック状態ID → 762のブロック状態ID（RegistryTables761）。 */
    private static final int[] BLOCK_STATE_761_TO_762_MAP =
            expand(BLOCK_STATE_761_TO_762_RANGES, BLOCK_STATE_761_TO_762_OVERRIDES,
                    MAX_BLOCK_STATE_761 + 1, STONE_BLOCK_STATE_762);
    /** 762のアイテムID → 761のアイテムID。 */
    private static final int[] ITEM_762_TO_761_MAP =
            expand(ITEM_761_RANGES, ITEM_761_OVERRIDES, MAX_ITEM_762 + 1, STONE_ITEM_761);
    /** 761のアイテムID → 762のアイテムID。 */
    private static final int[] ITEM_761_TO_762_MAP =
            expand(ITEM_761_TO_762_RANGES, ITEM_761_TO_762_OVERRIDES, MAX_ITEM_761 + 1,
                    STONE_ITEM_762);
    /** 762のエンティティ種別ID → 761のエンティティ種別ID。 */
    private static final int[] ENTITY_TYPE_762_TO_761_MAP =
            expand(ENTITY_TYPE_761_RANGES, ENTITY_TYPE_761_OVERRIDES, MAX_ENTITY_TYPE_762 + 1,
                    PIG_ENTITY_TYPE_761);
    /** 761のエンティティ種別ID → 762のエンティティ種別ID。 */
    private static final int[] ENTITY_TYPE_761_TO_762_MAP =
            expand(ENTITY_TYPE_761_TO_762_RANGES, ENTITY_TYPE_761_TO_762_OVERRIDES,
                    MAX_ENTITY_TYPE_761 + 1, PIG_ENTITY_TYPE_762);
    /** 762の音ID → 761の音ID。 */
    private static final int[] SOUND_762_TO_761_MAP =
            expand(SOUND_761_RANGES, SOUND_761_OVERRIDES, MAX_SOUND_762 + 1, GENERIC_SOUND_761);
    /** 761の音ID → 762の音ID。 */
    private static final int[] SOUND_761_TO_762_MAP =
            expand(SOUND_761_TO_762_RANGES, SOUND_761_TO_762_OVERRIDES, MAX_SOUND_761 + 1,
                    GENERIC_SOUND_762);
    /** 762のパーティクルID → 761のパーティクルID。 */
    private static final int[] PARTICLE_762_TO_761_MAP =
            expand(PARTICLE_761_RANGES, PARTICLE_761_OVERRIDES, MAX_PARTICLE_762 + 1,
                    AMBIENT_PARTICLE_761);
    /** 761のパーティクルID → 762のパーティクルID。 */
    private static final int[] PARTICLE_761_TO_762_MAP =
            expand(PARTICLE_761_TO_762_RANGES, PARTICLE_761_TO_762_OVERRIDES,
                    MAX_PARTICLE_761 + 1, AMBIENT_PARTICLE_762);
    /** 762のブロックID → 761のブロックID。 */
    private static final int[] BLOCK_762_TO_761_MAP =
            expand(BLOCK_761_RANGES, BLOCK_761_OVERRIDES, MAX_BLOCK_762 + 1, STONE_BLOCK_761);
    /** 761のブロックID → 762のブロックID。 */
    private static final int[] BLOCK_761_TO_762_MAP =
            expand(BLOCK_761_TO_762_RANGES, BLOCK_761_TO_762_OVERRIDES, MAX_BLOCK_761 + 1,
                    STONE_BLOCK_762);
    /** 762のメニュー種別ID → 761のメニュー種別ID（smithing は旧式へ）。 */
    private static final int[] MENU_762_TO_761_MAP =
            expand(MENU_761_RANGES, MENU_761_OVERRIDES, MAX_MENU_762 + 1, CRAFTING_MENU_761);
    /** 761のメニュー種別ID → 762のメニュー種別ID（smithing は legacy_smithing へ）。 */
    private static final int[] MENU_761_TO_762_MAP =
            expand(MENU_761_TO_762_RANGES, MENU_761_TO_762_OVERRIDES, MAX_MENU_761 + 1,
                    CRAFTING_MENU_762);
    /** 762のブロックエンティティ種別ID → 761のブロックエンティティ種別ID。無い種別は DROP。 */
    private static final int[] BLOCK_ENTITY_762_TO_761_MAP =
            expand(BLOCK_ENTITY_761_RANGES, BLOCK_ENTITY_761_OVERRIDES, MAX_BLOCK_ENTITY_762 + 1,
                    DROP);
    /** 761のブロックエンティティ種別ID → 762のブロックエンティティ種別ID。無い種別は DROP。 */
    private static final int[] BLOCK_ENTITY_761_TO_762_MAP =
            expand(BLOCK_ENTITY_761_TO_762_RANGES, BLOCK_ENTITY_761_TO_762_OVERRIDES,
                    MAX_BLOCK_ENTITY_761 + 1, DROP);
    /** 762のバイオームID → 761のバイオームID（63件すべて同一）。 */
    private static final int[] BIOME_762_TO_761_MAP =
            expand(BIOME_761_RANGES, BIOME_761_OVERRIDES, MAX_BIOME_762 + 1, PLAINS_BIOME_761);
    /** 761のバイオームID → 762のバイオームID。 */
    private static final int[] BIOME_761_TO_762_MAP =
            expand(BIOME_761_TO_762_RANGES, BIOME_761_TO_762_OVERRIDES, MAX_BIOME_761 + 1,
                    PLAINS_BIOME_762);
    /** 762のダメージ種別ID → 761のダメージ種別ID。無い種別は generic へ。 */
    private static final int[] DAMAGE_TYPE_762_TO_761_MAP =
            expand(DAMAGE_TYPE_761_RANGES, DAMAGE_TYPE_761_OVERRIDES, MAX_DAMAGE_TYPE_762 + 1,
                    GENERIC_DAMAGE_TYPE_761);
    /** 761のダメージ種別ID → 762のダメージ種別ID。 */
    private static final int[] DAMAGE_TYPE_761_TO_762_MAP =
            expand(DAMAGE_TYPE_761_TO_762_RANGES, DAMAGE_TYPE_761_TO_762_OVERRIDES,
                    MAX_DAMAGE_TYPE_761 + 1, GENERIC_DAMAGE_TYPE_762);
"""

SOUND_NAME_MAP_761 = """
    /** 762の音の名前 → 761の音の名前（Stop Sound 用）。数値の合成表と一致させる。 */
    private static final Map<String, String> SOUND_NAME_FALLBACK_762_TO_761_MAP =
            buildSoundNameMap(SOUND_NAME_FALLBACKS_762_TO_761);
"""

SOUND_NAME_MAP_761_REV = """
    /** 761の音の名前 → 762の音の名前（Stop Sound 用）。数値の合成表と一致させる。 */
    private static final Map<String, String> SOUND_NAME_FALLBACK_761_TO_762_MAP =
            buildSoundNameMap(SOUND_NAME_FALLBACKS_761_TO_762);
"""

METHODS_761 = """
    // ------------------------------------------------------------------
    // 762 ⇔ 761（1.19.4 ⇔ 1.19.3）
    // パケットIDは 132 件が変わる（PacketIds の 761 列を参照）。
    // ペイロードの差は Chat Session Update の位置、Named Entity Spawn／
    // Feature Flags の有無、bundle_delimiter／chunk_biomes／
    // damage_event／hurt_animation の有無など。レジストリは
    // RegistryTables761（ViaVersion mapping-1.19.3／1.19.4.json
    // から名前で突き合わせたもの）を使う。ダメージ種別は 762 と
    // 同一順序と仮定（実バニラで検証する）。
    // ------------------------------------------------------------------

    /** 762のブロック状態ID → 761のブロック状態ID。範囲外はSTONE_BLOCK_STATE_761 に相当する代替。 */
    public static int mapBlockState762To761(int id762) {
        if (id762 < 0 || id762 >= BLOCK_STATE_762_TO_761_MAP.length) {
            logUnknownComposedOnce("762→761", "ブロック状態", id762);
            return STONE_BLOCK_STATE_761;
        }
        return BLOCK_STATE_762_TO_761_MAP[id762];
    }

    /** 761のブロック状態ID → 762のブロック状態ID。範囲外はSTONE_BLOCK_STATE_762 に相当する代替。 */
    public static int mapBlockState761To762(int id761) {
        if (id761 < 0 || id761 >= BLOCK_STATE_761_TO_762_MAP.length) {
            logUnknownComposedOnce("761→762", "ブロック状態", id761);
            return STONE_BLOCK_STATE_762;
        }
        return BLOCK_STATE_761_TO_762_MAP[id761];
    }

    /** 762のアイテムID → 761のアイテムID。範囲外はSTONE_ITEM_761 に相当する代替。 */
    public static int mapItem762To761(int id762) {
        if (id762 < 0 || id762 >= ITEM_762_TO_761_MAP.length) {
            logUnknownComposedOnce("762→761", "アイテム", id762);
            return STONE_ITEM_761;
        }
        return ITEM_762_TO_761_MAP[id762];
    }

    /** 761のアイテムID → 762のアイテムID。範囲外はSTONE_ITEM_762 に相当する代替。 */
    public static int mapItem761To762(int id761) {
        if (id761 < 0 || id761 >= ITEM_761_TO_762_MAP.length) {
            logUnknownComposedOnce("761→762", "アイテム", id761);
            return STONE_ITEM_762;
        }
        return ITEM_761_TO_762_MAP[id761];
    }

    /** 762のエンティティ種別ID → 761のエンティティ種別ID。範囲外はPIG_ENTITY_TYPE_761 に相当する代替。 */
    public static int mapEntityType762To761(int id762) {
        if (id762 < 0 || id762 >= ENTITY_TYPE_762_TO_761_MAP.length) {
            logUnknownComposedOnce("762→761", "エンティティ種別", id762);
            return PIG_ENTITY_TYPE_761;
        }
        return ENTITY_TYPE_762_TO_761_MAP[id762];
    }

    /** 761のエンティティ種別ID → 762のエンティティ種別ID。範囲外はPIG_ENTITY_TYPE_762 に相当する代替。 */
    public static int mapEntityType761To762(int id761) {
        if (id761 < 0 || id761 >= ENTITY_TYPE_761_TO_762_MAP.length) {
            logUnknownComposedOnce("761→762", "エンティティ種別", id761);
            return PIG_ENTITY_TYPE_762;
        }
        return ENTITY_TYPE_761_TO_762_MAP[id761];
    }

    /** 762の音ID → 761の音ID。範囲外はGENERIC_SOUND_761 に相当する代替。 */
    public static int mapSound762To761(int id762) {
        if (id762 < 0 || id762 >= SOUND_762_TO_761_MAP.length) {
            logUnknownComposedOnce("762→761", "音", id762);
            return GENERIC_SOUND_761;
        }
        return SOUND_762_TO_761_MAP[id762];
    }

    /** 761の音ID → 762の音ID。範囲外はGENERIC_SOUND_762 に相当する代替。 */
    public static int mapSound761To762(int id761) {
        if (id761 < 0 || id761 >= SOUND_761_TO_762_MAP.length) {
            logUnknownComposedOnce("761→762", "音", id761);
            return GENERIC_SOUND_762;
        }
        return SOUND_761_TO_762_MAP[id761];
    }

    /** 762のパーティクルID → 761のパーティクルID。範囲外はAMBIENT_PARTICLE_761 に相当する代替。 */
    public static int mapParticle762To761(int id762) {
        if (id762 < 0 || id762 >= PARTICLE_762_TO_761_MAP.length) {
            logUnknownComposedOnce("762→761", "パーティクル", id762);
            return AMBIENT_PARTICLE_761;
        }
        return PARTICLE_762_TO_761_MAP[id762];
    }

    /** 761のパーティクルID → 762のパーティクルID。範囲外はAMBIENT_PARTICLE_762 に相当する代替。 */
    public static int mapParticle761To762(int id761) {
        if (id761 < 0 || id761 >= PARTICLE_761_TO_762_MAP.length) {
            logUnknownComposedOnce("761→762", "パーティクル", id761);
            return AMBIENT_PARTICLE_762;
        }
        return PARTICLE_761_TO_762_MAP[id761];
    }

    /** 762のブロックID → 761のブロックID。範囲外はSTONE_BLOCK_761 に相当する代替。 */
    public static int mapBlock762To761(int id762) {
        if (id762 < 0 || id762 >= BLOCK_762_TO_761_MAP.length) {
            logUnknownComposedOnce("762→761", "ブロック", id762);
            return STONE_BLOCK_761;
        }
        return BLOCK_762_TO_761_MAP[id762];
    }

    /** 761のブロックID → 762のブロックID。範囲外はSTONE_BLOCK_762 に相当する代替。 */
    public static int mapBlock761To762(int id761) {
        if (id761 < 0 || id761 >= BLOCK_761_TO_762_MAP.length) {
            logUnknownComposedOnce("761→762", "ブロック", id761);
            return STONE_BLOCK_762;
        }
        return BLOCK_761_TO_762_MAP[id761];
    }

    /** 762のメニュー種別ID → 761のメニュー種別ID。範囲外はCRAFTING_MENU_761 に相当する代替。 */
    public static int mapMenu762To761(int id762) {
        if (id762 < 0 || id762 >= MENU_762_TO_761_MAP.length) {
            logUnknownComposedOnce("762→761", "メニュー", id762);
            return CRAFTING_MENU_761;
        }
        return MENU_762_TO_761_MAP[id762];
    }

    /** 761のメニュー種別ID → 762のメニュー種別ID。範囲外はCRAFTING_MENU_762 に相当する代替。 */
    public static int mapMenu761To762(int id761) {
        if (id761 < 0 || id761 >= MENU_761_TO_762_MAP.length) {
            logUnknownComposedOnce("761→762", "メニュー", id761);
            return CRAFTING_MENU_762;
        }
        return MENU_761_TO_762_MAP[id761];
    }

    /**
     * 762のブロックエンティティ種別ID → 761のブロックエンティティ種別ID。
     * 762にしか無い suspicious_sand・decorated_pot は {@link #DROP}。
     */
    public static int mapBlockEntityType762To761(int id762) {
        if (id762 < 0 || id762 >= BLOCK_ENTITY_762_TO_761_MAP.length) {
            logUnknownComposedOnce("762→761", "ブロックエンティティ種別", id762);
            return DROP;
        }
        return BLOCK_ENTITY_762_TO_761_MAP[id762];
    }

    /**
     * 761のブロックエンティティ種別ID → 762のブロックエンティティ種別ID。
     * 761にしか無い種別は無いため DROP にはならない。
     */
    public static int mapBlockEntityType761To762(int id761) {
        if (id761 < 0 || id761 >= BLOCK_ENTITY_761_TO_762_MAP.length) {
            logUnknownComposedOnce("761→762", "ブロックエンティティ種別", id761);
            return DROP;
        }
        return BLOCK_ENTITY_761_TO_762_MAP[id761];
    }

    /** 762のバイオームID → 761のバイオームID。範囲外はPLAINS_BIOME_761 に相当する代替。 */
    public static int mapBiome762To761(int id762) {
        if (id762 < 0 || id762 >= BIOME_762_TO_761_MAP.length) {
            logUnknownComposedOnce("762→761", "バイオーム", id762);
            return PLAINS_BIOME_761;
        }
        return BIOME_762_TO_761_MAP[id762];
    }

    /** 761のバイオームID → 762のバイオームID。範囲外はPLAINS_BIOME_762 に相当する代替。 */
    public static int mapBiome761To762(int id761) {
        if (id761 < 0 || id761 >= BIOME_761_TO_762_MAP.length) {
            logUnknownComposedOnce("761→762", "バイオーム", id761);
            return PLAINS_BIOME_762;
        }
        return BIOME_761_TO_762_MAP[id761];
    }

    /** 762のダメージ種別ID → 761のダメージ種別ID（同一順序と仮定）。 */
    public static int mapDamageType762To761(int id762) {
        if (id762 < 0 || id762 > MAX_DAMAGE_TYPE_762) {
            logUnknownOnce(PROTOCOL_761, "ダメージ種別", id762);
            return DROP;
        }
        return DAMAGE_TYPE_762_TO_761_MAP[id762];
    }

    /** 761のダメージ種別ID → 762のダメージ種別ID（同一順序と仮定）。 */
    public static int mapDamageType761To762(int id761) {
        if (id761 < 0 || id761 > MAX_DAMAGE_TYPE_761) {
            logUnknownOnce(PROTOCOL_762, "ダメージ種別", id761);
            return DROP;
        }
        return DAMAGE_TYPE_761_TO_762_MAP[id761];
    }

    /** 762の音の名前 → 761の音の名前（Stop Sound 用）。数値の合成表と一致させる。 */
    public static String mapSoundName762To761(String name) {
        return mapSoundNameComposed(name, SOUND_NAME_FALLBACK_762_TO_761_MAP);
    }

    /** 761の音の名前 → 762の音の名前（Stop Sound 用）。数値の合成表と一致させる。 */
    public static String mapSoundName761To762(String name) {
        return mapSoundNameComposed(name, SOUND_NAME_FALLBACK_761_TO_762_MAP);
    }
"""

# 765→761 の合成（target 分岐用）。(メソッド名, 765→762 の式, 762→761 の式)
DISPATCH_761 = [
    ("mapBlockState", "mapBlockState763To762(mapBlockState(id765))", "mapBlockState762To761"),
    ("mapItem", "mapItem763To762(mapItem(id765))", "mapItem762To761"),
    ("mapItemReverse", "mapItemReverse(mapItem762To763(id))", None),
    ("mapEntityType", "mapEntityType763To762(mapEntityType(id765))", "mapEntityType762To761"),
    ("mapSound", "mapSound763To762(mapSound(id765))", "mapSound762To761"),
    ("mapParticle", "mapParticle763To762(mapParticle(id765))", "mapParticle762To761"),
    ("mapBlock", "mapBlock763To762(mapBlock(id765))", "mapBlock762To761"),
    ("mapMenu", "mapMenu763To762(mapMenu(id765))", "mapMenu762To761"),
    ("mapEffect", "mapEffect(id765)", None),
    ("mapBlockEntityType", "mapBlockEntityType763To762(mapBlockEntityType(id765))",
     "mapBlockEntityType762To761"),
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
    insert_before("import static io.detergentmc.protocol.RegistryTables762.*;",
                  IMPORT, "import RegistryTables761", "RegistryTables761.*")
    # 2. PROTOCOL_761（PROTOCOL_762 の直前）
    insert_before("    public static final int PROTOCOL_762 = 762;",
                  PROTOCOL_CONST, "PROTOCOL_761", "PROTOCOL_761 = 761")
    # 3. 761定数（SPEED_EFFECT_762 の直後）
    insert_after("    public static final int SPEED_EFFECT_762 = 1;\n",
                 CONSTANTS_761, "761定数", "MAX_BLOCK_STATE_761")
    # 4. 761の表（BLOCK_STATE_763_TO_762_MAP の直前）
    insert_before("    private static final int[] BLOCK_STATE_763_TO_762_MAP =",
                  MAPS_761, "761の表", "BLOCK_STATE_762_TO_761_MAP")
    # 5. 音の名前表（SOUND_NAME_FALLBACK_763_TO_762_MAP の直前）
    insert_before("    private static final Map<String, String> SOUND_NAME_FALLBACK_763_TO_762_MAP =",
                  SOUND_NAME_MAP_761, "音の名前表761", "SOUND_NAME_FALLBACK_762_TO_761_MAP")
    insert_before("    private static final Map<String, String> SOUND_NAME_FALLBACK_763_TO_762_MAP =",
                  SOUND_NAME_MAP_761_REV, "音の名前表761逆向き", "SOUND_NAME_FALLBACK_761_TO_762_MAP")
    # 6. アクセサメソッド（mapBlockState763To762 の javadoc の直前）
    insert_before("    /** 763のブロック状態ID → 762のブロック状態ID。",
                  METHODS_761, "761アクセサ", "mapBlockState762To761")
    # 7. target 分岐への 761 追加（各 dispatch メソッドの 762 分岐の直前。
    # insert_after はメソッド冒頭の直後に入れるため、762 分岐より前に来る）
    for name, expr762, f761 in DISPATCH_761:
        if name == "mapItemReverse":
            body = (f"        if (clientProtocol == PROTOCOL_761) {{\n"
                    f"            return mapItemReverse(mapItem761To762(mapItem762To763(id)));\n"
                    f"        }}\n")
            anchor = f"    public static int {name}(int id, int clientProtocol) {{\n"
            marker = "mapItemReverse(mapItem761To762(mapItem762To763(id)))"
        elif name == "mapEffect":
            body = (f"        if (clientProtocol == PROTOCOL_761) {{\n"
                    f"            return mapEffect(id765);\n"
                    f"        }}\n")
            anchor = f"    public static int {name}(int id765, int clientProtocol) {{\n"
            marker = ("if (clientProtocol == PROTOCOL_761) {\n"
                      "            return mapEffect(id765);\n"
                      "        }\n"
                      "        if (clientProtocol == PROTOCOL_762)")
        else:
            body = (f"        if (clientProtocol == PROTOCOL_761) {{\n"
                    f"            return {f761}({expr762});\n"
                    f"        }}\n")
            anchor = f"    public static int {name}(int id765, int clientProtocol) {{\n"
            marker = f"return {f761}({expr762});"
        insert_after(anchor, body, f"dispatch {name} 761", marker)
    # 8. mapSoundName 分岐への 761 追加
    insert_after("    public static String mapSoundName(String name, int clientProtocol) {\n",
                 "        if (clientProtocol == PROTOCOL_761) {\n"
                 "            return mapSoundName762To761(mapSoundName763To762(mapSoundName(name)));\n"
                 "        }\n",
                 "dispatch mapSoundName 761", "mapSoundName762To761(mapSoundName763To762(mapSoundName(name)))")
    P.write_text(src, encoding="utf-8")
    print("RegistryRemapper.java に 761 を差し込んだ。")


if __name__ == "__main__":
    raise SystemExit(main())
