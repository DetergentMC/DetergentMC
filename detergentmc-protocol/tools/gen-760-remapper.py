#!/usr/bin/env python3
"""RegistryRemapper.java に 760（1.19.1／1.19.2）対応を差し込む。

アンカー付き挿入のみ行う。アンカーが見つからなければ失敗して何も書かない。
使い方: python3 gen-760-remapper.py
書き換えた後は必ず git diff で内容を確認してからコミットすること。
"""
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parent.parent
P = ROOT / "src/main/java/io/detergentmc/protocol/RegistryRemapper.java"

IMPORT = "import static io.detergentmc.protocol.RegistryTables760.*;\n"

PROTOCOL_CONST = "    /** 760（1.19.1／1.19.2）のプロトコル番号。 */\n    public static final int PROTOCOL_760 = 760;\n"

CONSTANTS_760 = """
    /** 760側の最大の番号（ViaVersion mapping-1.19.json の件数−1）。 */
    public static final int MAX_BLOCK_STATE_760 = 21447;
    public static final int MAX_ITEM_760 = 1151;
    public static final int MAX_ENTITY_TYPE_760 = 117;
    public static final int MAX_SOUND_760 = 1320;
    public static final int MAX_PARTICLE_760 = 92;
    public static final int MAX_BLOCK_760 = 932;
    public static final int MAX_MENU_760 = 23;
    /** 760側のエンチャントは 0〜38（39件。761と同一順序のため表は作らない）。 */
    public static final int MAX_ENCHANTMENT_760 = 38;
    /** 760側のバイオームは 0〜62（63件。名前は761と同一だが順序が違う）。 */
    public static final int MAX_BIOME_760 = 62;
    /** 760側のダメージ種別は 0〜41（42件。762 の順序の恒等写像。実バニラで検証する）。 */
    public static final int MAX_DAMAGE_TYPE_760 = 41;
    /** 760側のブロックエンティティ種別は 0〜35（36件）。 */
    public static final int MAX_BLOCK_ENTITY_760 = 35;

    /** 760側の代替先（mapping-1.19.json の番号。Speed だけ 1ベースで 1）。 */
    public static final int STONE_BLOCK_STATE_760 = 1;
    public static final int AIR_BLOCK_STATE_760 = 0;
    public static final int STONE_ITEM_760 = 1;
    public static final int PIG_ENTITY_TYPE_760 = 67;
    public static final int GENERIC_SOUND_760 = 1112; // block.stone.break
    public static final int AMBIENT_PARTICLE_760 = 0; // ambient_entity_effect
    public static final int STONE_BLOCK_760 = 1;
    public static final int CRAFTING_MENU_760 = 11;
    public static final int SPEED_EFFECT_760 = 1;
    /** 760側の平原バイオーム（minecraft-data pc/1.19.2/biomes.json）。 */
    public static final int PLAINS_BIOME_760 = 1;
    /** 760側の generic ダメージ種別（762 と同一順序と仮定）。 */
    public static final int GENERIC_DAMAGE_TYPE_760 = 16;
"""

MAPS_760 = """
    /** 761のブロック状態ID → 760のブロック状態ID（RegistryTables760）。 */
    private static final int[] BLOCK_STATE_761_TO_760_MAP =
            expand(BLOCK_STATE_760_RANGES, BLOCK_STATE_760_OVERRIDES, MAX_BLOCK_STATE_761 + 1,
                    STONE_BLOCK_STATE_760);
    /** 760のブロック状態ID → 761のブロック状態ID（RegistryTables760）。 */
    private static final int[] BLOCK_STATE_760_TO_761_MAP =
            expand(BLOCK_STATE_760_TO_761_RANGES, BLOCK_STATE_760_TO_761_OVERRIDES,
                    MAX_BLOCK_STATE_760 + 1, STONE_BLOCK_STATE_761);
    /** 761のアイテムID → 760のアイテムID。 */
    private static final int[] ITEM_761_TO_760_MAP =
            expand(ITEM_760_RANGES, ITEM_760_OVERRIDES, MAX_ITEM_761 + 1, STONE_ITEM_760);
    /** 760のアイテムID → 761のアイテムID。 */
    private static final int[] ITEM_760_TO_761_MAP =
            expand(ITEM_760_TO_761_RANGES, ITEM_760_TO_761_OVERRIDES, MAX_ITEM_760 + 1,
                    STONE_ITEM_761);
    /** 761のエンティティ種別ID → 760のエンティティ種別ID。 */
    private static final int[] ENTITY_TYPE_761_TO_760_MAP =
            expand(ENTITY_TYPE_760_RANGES, ENTITY_TYPE_760_OVERRIDES, MAX_ENTITY_TYPE_761 + 1,
                    PIG_ENTITY_TYPE_760);
    /** 760のエンティティ種別ID → 761のエンティティ種別ID。 */
    private static final int[] ENTITY_TYPE_760_TO_761_MAP =
            expand(ENTITY_TYPE_760_TO_761_RANGES, ENTITY_TYPE_760_TO_761_OVERRIDES,
                    MAX_ENTITY_TYPE_760 + 1, PIG_ENTITY_TYPE_761);
    /** 761の音ID → 760の音ID。 */
    private static final int[] SOUND_761_TO_760_MAP =
            expand(SOUND_760_RANGES, SOUND_760_OVERRIDES, MAX_SOUND_761 + 1, GENERIC_SOUND_760);
    /** 760の音ID → 761の音ID。 */
    private static final int[] SOUND_760_TO_761_MAP =
            expand(SOUND_760_TO_761_RANGES, SOUND_760_TO_761_OVERRIDES, MAX_SOUND_760 + 1,
                    GENERIC_SOUND_761);
    /** 761のパーティクルID → 760のパーティクルID。 */
    private static final int[] PARTICLE_761_TO_760_MAP =
            expand(PARTICLE_760_RANGES, PARTICLE_760_OVERRIDES, MAX_PARTICLE_761 + 1,
                    AMBIENT_PARTICLE_760);
    /** 760のパーティクルID → 761のパーティクルID。 */
    private static final int[] PARTICLE_760_TO_761_MAP =
            expand(PARTICLE_760_TO_761_RANGES, PARTICLE_760_TO_761_OVERRIDES,
                    MAX_PARTICLE_760 + 1, AMBIENT_PARTICLE_761);
    /** 761のブロックID → 760のブロックID。 */
    private static final int[] BLOCK_761_TO_760_MAP =
            expand(BLOCK_760_RANGES, BLOCK_760_OVERRIDES, MAX_BLOCK_761 + 1, STONE_BLOCK_760);
    /** 760のブロックID → 761のブロックID。 */
    private static final int[] BLOCK_760_TO_761_MAP =
            expand(BLOCK_760_TO_761_RANGES, BLOCK_760_TO_761_OVERRIDES, MAX_BLOCK_760 + 1,
                    STONE_BLOCK_761);
    /** 761のメニュー種別ID → 760のメニュー種別ID（smithing は旧式へ）。 */
    private static final int[] MENU_761_TO_760_MAP =
            expand(MENU_760_RANGES, MENU_760_OVERRIDES, MAX_MENU_761 + 1, CRAFTING_MENU_760);
    /** 760のメニュー種別ID → 761のメニュー種別ID（smithing は legacy_smithing へ）。 */
    private static final int[] MENU_760_TO_761_MAP =
            expand(MENU_760_TO_761_RANGES, MENU_760_TO_761_OVERRIDES, MAX_MENU_760 + 1,
                    CRAFTING_MENU_761);
    /** 761のブロックエンティティ種別ID → 760のブロックエンティティ種別ID。無い種別は DROP。 */
    private static final int[] BLOCK_ENTITY_761_TO_760_MAP =
            expand(BLOCK_ENTITY_760_RANGES, BLOCK_ENTITY_760_OVERRIDES, MAX_BLOCK_ENTITY_761 + 1,
                    DROP);
    /** 760のブロックエンティティ種別ID → 761のブロックエンティティ種別ID。無い種別は DROP。 */
    private static final int[] BLOCK_ENTITY_760_TO_761_MAP =
            expand(BLOCK_ENTITY_760_TO_761_RANGES, BLOCK_ENTITY_760_TO_761_OVERRIDES,
                    MAX_BLOCK_ENTITY_760 + 1, DROP);
    /** 761のバイオームID → 760のバイオームID（63件すべて同一）。 */
    private static final int[] BIOME_761_TO_760_MAP =
            expand(BIOME_760_RANGES, BIOME_760_OVERRIDES, MAX_BIOME_761 + 1, PLAINS_BIOME_760);
    /** 760のバイオームID → 761のバイオームID。 */
    private static final int[] BIOME_760_TO_761_MAP =
            expand(BIOME_760_TO_761_RANGES, BIOME_760_TO_761_OVERRIDES, MAX_BIOME_760 + 1,
                    PLAINS_BIOME_761);
    /** 761のダメージ種別ID → 760のダメージ種別ID。無い種別は generic へ。 */
    private static final int[] DAMAGE_TYPE_761_TO_760_MAP =
            expand(DAMAGE_TYPE_760_RANGES, DAMAGE_TYPE_760_OVERRIDES, MAX_DAMAGE_TYPE_761 + 1,
                    GENERIC_DAMAGE_TYPE_760);
    /** 760のダメージ種別ID → 761のダメージ種別ID。 */
    private static final int[] DAMAGE_TYPE_760_TO_761_MAP =
            expand(DAMAGE_TYPE_760_TO_761_RANGES, DAMAGE_TYPE_760_TO_761_OVERRIDES,
                    MAX_DAMAGE_TYPE_760 + 1, GENERIC_DAMAGE_TYPE_761);
"""

SOUND_NAME_MAP_760 = """
    /** 761の音の名前 → 760の音の名前（Stop Sound 用）。数値の合成表と一致させる。 */
    private static final Map<String, String> SOUND_NAME_FALLBACK_761_TO_760_MAP =
            buildSoundNameMap(SOUND_NAME_FALLBACKS_761_TO_760);
"""

SOUND_NAME_MAP_760_REV = """
    /** 760の音の名前 → 761の音の名前（Stop Sound 用）。数値の合成表と一致させる。 */
    private static final Map<String, String> SOUND_NAME_FALLBACK_760_TO_761_MAP =
            buildSoundNameMap(SOUND_NAME_FALLBACKS_760_TO_761);
"""

METHODS_760 = """
    // ------------------------------------------------------------------
    // 761 ⇔ 760（1.19.3 ⇔ 1.19.1／1.19.2）
    // パケットIDは 118 件が変わる（PacketIds の 760 列を参照）。
    // ペイロードの差は Player Chat／Chat Command の形式、Message Header
    // の有無、Player Info の形式、Server Data の previewsChat、Sound
    // Effect のホルダー形式など。レジストリは
    // RegistryTables760（ViaVersion mapping-1.19／1.19.3.json
    // から名前で突き合わせたもの）を使う。ダメージ種別は 762 の
    // 42 件の恒等写像（実バニラで検証する）。
    // ------------------------------------------------------------------

    /** 761のブロック状態ID → 760のブロック状態ID。範囲外はSTONE_BLOCK_STATE_760 に相当する代替。 */
    public static int mapBlockState761To760(int id761) {
        if (id761 < 0 || id761 >= BLOCK_STATE_761_TO_760_MAP.length) {
            logUnknownComposedOnce("761→760", "ブロック状態", id761);
            return STONE_BLOCK_STATE_760;
        }
        return BLOCK_STATE_761_TO_760_MAP[id761];
    }

    /** 760のブロック状態ID → 761のブロック状態ID。範囲外はSTONE_BLOCK_STATE_761 に相当する代替。 */
    public static int mapBlockState760To761(int id760) {
        if (id760 < 0 || id760 >= BLOCK_STATE_760_TO_761_MAP.length) {
            logUnknownComposedOnce("760→761", "ブロック状態", id760);
            return STONE_BLOCK_STATE_761;
        }
        return BLOCK_STATE_760_TO_761_MAP[id760];
    }

    /** 761のアイテムID → 760のアイテムID。範囲外はSTONE_ITEM_760 に相当する代替。 */
    public static int mapItem761To760(int id761) {
        if (id761 < 0 || id761 >= ITEM_761_TO_760_MAP.length) {
            logUnknownComposedOnce("761→760", "アイテム", id761);
            return STONE_ITEM_760;
        }
        return ITEM_761_TO_760_MAP[id761];
    }

    /** 760のアイテムID → 761のアイテムID。範囲外はSTONE_ITEM_761 に相当する代替。 */
    public static int mapItem760To761(int id760) {
        if (id760 < 0 || id760 >= ITEM_760_TO_761_MAP.length) {
            logUnknownComposedOnce("760→761", "アイテム", id760);
            return STONE_ITEM_761;
        }
        return ITEM_760_TO_761_MAP[id760];
    }

    /** 761のエンティティ種別ID → 760のエンティティ種別ID。範囲外はPIG_ENTITY_TYPE_760 に相当する代替。 */
    public static int mapEntityType761To760(int id761) {
        if (id761 < 0 || id761 >= ENTITY_TYPE_761_TO_760_MAP.length) {
            logUnknownComposedOnce("761→760", "エンティティ種別", id761);
            return PIG_ENTITY_TYPE_760;
        }
        return ENTITY_TYPE_761_TO_760_MAP[id761];
    }

    /** 760のエンティティ種別ID → 761のエンティティ種別ID。範囲外はPIG_ENTITY_TYPE_761 に相当する代替。 */
    public static int mapEntityType760To761(int id760) {
        if (id760 < 0 || id760 >= ENTITY_TYPE_760_TO_761_MAP.length) {
            logUnknownComposedOnce("760→761", "エンティティ種別", id760);
            return PIG_ENTITY_TYPE_761;
        }
        return ENTITY_TYPE_760_TO_761_MAP[id760];
    }

    /** 761の音ID → 760の音ID。範囲外はGENERIC_SOUND_760 に相当する代替。 */
    public static int mapSound761To760(int id761) {
        if (id761 < 0 || id761 >= SOUND_761_TO_760_MAP.length) {
            logUnknownComposedOnce("761→760", "音", id761);
            return GENERIC_SOUND_760;
        }
        return SOUND_761_TO_760_MAP[id761];
    }

    /** 760の音ID → 761の音ID。範囲外はGENERIC_SOUND_761 に相当する代替。 */
    public static int mapSound760To761(int id760) {
        if (id760 < 0 || id760 >= SOUND_760_TO_761_MAP.length) {
            logUnknownComposedOnce("760→761", "音", id760);
            return GENERIC_SOUND_761;
        }
        return SOUND_760_TO_761_MAP[id760];
    }

    /** 761のパーティクルID → 760のパーティクルID。範囲外はAMBIENT_PARTICLE_760 に相当する代替。 */
    public static int mapParticle761To760(int id761) {
        if (id761 < 0 || id761 >= PARTICLE_761_TO_760_MAP.length) {
            logUnknownComposedOnce("761→760", "パーティクル", id761);
            return AMBIENT_PARTICLE_760;
        }
        return PARTICLE_761_TO_760_MAP[id761];
    }

    /** 760のパーティクルID → 761のパーティクルID。範囲外はAMBIENT_PARTICLE_761 に相当する代替。 */
    public static int mapParticle760To761(int id760) {
        if (id760 < 0 || id760 >= PARTICLE_760_TO_761_MAP.length) {
            logUnknownComposedOnce("760→761", "パーティクル", id760);
            return AMBIENT_PARTICLE_761;
        }
        return PARTICLE_760_TO_761_MAP[id760];
    }

    /** 761のブロックID → 760のブロックID。範囲外はSTONE_BLOCK_760 に相当する代替。 */
    public static int mapBlock761To760(int id761) {
        if (id761 < 0 || id761 >= BLOCK_761_TO_760_MAP.length) {
            logUnknownComposedOnce("761→760", "ブロック", id761);
            return STONE_BLOCK_760;
        }
        return BLOCK_761_TO_760_MAP[id761];
    }

    /** 760のブロックID → 761のブロックID。範囲外はSTONE_BLOCK_761 に相当する代替。 */
    public static int mapBlock760To761(int id760) {
        if (id760 < 0 || id760 >= BLOCK_760_TO_761_MAP.length) {
            logUnknownComposedOnce("760→761", "ブロック", id760);
            return STONE_BLOCK_761;
        }
        return BLOCK_760_TO_761_MAP[id760];
    }

    /** 761のメニュー種別ID → 760のメニュー種別ID。範囲外はCRAFTING_MENU_760 に相当する代替。 */
    public static int mapMenu761To760(int id761) {
        if (id761 < 0 || id761 >= MENU_761_TO_760_MAP.length) {
            logUnknownComposedOnce("761→760", "メニュー", id761);
            return CRAFTING_MENU_760;
        }
        return MENU_761_TO_760_MAP[id761];
    }

    /** 760のメニュー種別ID → 761のメニュー種別ID。範囲外はCRAFTING_MENU_761 に相当する代替。 */
    public static int mapMenu760To761(int id760) {
        if (id760 < 0 || id760 >= MENU_760_TO_761_MAP.length) {
            logUnknownComposedOnce("760→761", "メニュー", id760);
            return CRAFTING_MENU_761;
        }
        return MENU_760_TO_761_MAP[id760];
    }

    /**
     * 761のブロックエンティティ種別ID → 760のブロックエンティティ種別ID。
     * 761にしか無い suspicious_sand・decorated_pot は {@link #DROP}。
     */
    public static int mapBlockEntityType761To760(int id761) {
        if (id761 < 0 || id761 >= BLOCK_ENTITY_761_TO_760_MAP.length) {
            logUnknownComposedOnce("761→760", "ブロックエンティティ種別", id761);
            return DROP;
        }
        return BLOCK_ENTITY_761_TO_760_MAP[id761];
    }

    /**
     * 760のブロックエンティティ種別ID → 761のブロックエンティティ種別ID。
     * 760にしか無い種別は無いため DROP にはならない。
     */
    public static int mapBlockEntityType760To761(int id760) {
        if (id760 < 0 || id760 >= BLOCK_ENTITY_760_TO_761_MAP.length) {
            logUnknownComposedOnce("760→761", "ブロックエンティティ種別", id760);
            return DROP;
        }
        return BLOCK_ENTITY_760_TO_761_MAP[id760];
    }

    /** 761のバイオームID → 760のバイオームID。範囲外はPLAINS_BIOME_760 に相当する代替。 */
    public static int mapBiome761To760(int id761) {
        if (id761 < 0 || id761 >= BIOME_761_TO_760_MAP.length) {
            logUnknownComposedOnce("761→760", "バイオーム", id761);
            return PLAINS_BIOME_760;
        }
        return BIOME_761_TO_760_MAP[id761];
    }

    /** 760のバイオームID → 761のバイオームID。範囲外はPLAINS_BIOME_761 に相当する代替。 */
    public static int mapBiome760To761(int id760) {
        if (id760 < 0 || id760 >= BIOME_760_TO_761_MAP.length) {
            logUnknownComposedOnce("760→761", "バイオーム", id760);
            return PLAINS_BIOME_761;
        }
        return BIOME_760_TO_761_MAP[id760];
    }

    /** 761のダメージ種別ID → 760のダメージ種別ID（同一順序と仮定）。 */
    public static int mapDamageType761To760(int id761) {
        if (id761 < 0 || id761 > MAX_DAMAGE_TYPE_761) {
            logUnknownOnce(PROTOCOL_760, "ダメージ種別", id761);
            return DROP;
        }
        return DAMAGE_TYPE_761_TO_760_MAP[id761];
    }

    /** 760のダメージ種別ID → 761のダメージ種別ID（同一順序と仮定）。 */
    public static int mapDamageType760To761(int id760) {
        if (id760 < 0 || id760 > MAX_DAMAGE_TYPE_760) {
            logUnknownOnce(PROTOCOL_761, "ダメージ種別", id760);
            return DROP;
        }
        return DAMAGE_TYPE_760_TO_761_MAP[id760];
    }

    /** 761の音の名前 → 760の音の名前（Stop Sound 用）。数値の合成表と一致させる。 */
    public static String mapSoundName761To760(String name) {
        return mapSoundNameComposed(name, SOUND_NAME_FALLBACK_761_TO_760_MAP);
    }

    /** 760の音の名前 → 761の音の名前（Stop Sound 用）。数値の合成表と一致させる。 */
    public static String mapSoundName760To761(String name) {
        return mapSoundNameComposed(name, SOUND_NAME_FALLBACK_760_TO_761_MAP);
    }
"""

# 765→760 の合成（target 分岐用）。(メソッド名, 765→761 の式, 761→760 の式)
DISPATCH_760 = [
    ("mapBlockState", "mapBlockState762To761(mapBlockState763To762(mapBlockState(id765)))", "mapBlockState761To760"),
    ("mapItem", "mapItem762To761(mapItem763To762(mapItem(id765)))", "mapItem761To760"),
    ("mapItemReverse", "mapItemReverse(mapItem761To763(id))", None),
    ("mapEntityType", "mapEntityType762To761(mapEntityType763To762(mapEntityType(id765)))", "mapEntityType761To760"),
    ("mapSound", "mapSound762To761(mapSound763To762(mapSound(id765)))", "mapSound761To760"),
    ("mapParticle", "mapParticle762To761(mapParticle763To762(mapParticle(id765)))", "mapParticle761To760"),
    ("mapBlock", "mapBlock762To761(mapBlock763To762(mapBlock(id765)))", "mapBlock761To760"),
    ("mapMenu", "mapMenu762To761(mapMenu763To762(mapMenu(id765)))", "mapMenu761To760"),
    ("mapEffect", "mapEffect(id765)", None),
    ("mapBlockEntityType", "mapBlockEntityType762To761(mapBlockEntityType763To762(mapBlockEntityType(id765)))",
     "mapBlockEntityType761To760"),
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
    insert_before("import static io.detergentmc.protocol.RegistryTables761.*;",
                  IMPORT, "import RegistryTables760", "RegistryTables760.*")
    # 2. PROTOCOL_760（PROTOCOL_761 の直前）
    insert_before("    public static final int PROTOCOL_761 = 761;",
                  PROTOCOL_CONST, "PROTOCOL_760", "PROTOCOL_760 = 760")
    # 3. 760定数（SPEED_EFFECT_761 の直後）
    insert_after("    public static final int SPEED_EFFECT_761 = 1;\n",
                 CONSTANTS_760, "760定数", "MAX_BLOCK_STATE_760")
    # 4. 760の表（BLOCK_STATE_763_TO_762_MAP の直前）
    insert_before("    private static final int[] BLOCK_STATE_763_TO_762_MAP =",
                  MAPS_760, "760の表", "BLOCK_STATE_761_TO_760_MAP")
    # 5. 音の名前表（SOUND_NAME_FALLBACK_763_TO_762_MAP の直前）
    insert_before("    private static final Map<String, String> SOUND_NAME_FALLBACK_763_TO_762_MAP =",
                  SOUND_NAME_MAP_760, "音の名前表760", "SOUND_NAME_FALLBACK_761_TO_760_MAP")
    insert_before("    private static final Map<String, String> SOUND_NAME_FALLBACK_763_TO_762_MAP =",
                  SOUND_NAME_MAP_760_REV, "音の名前表760逆向き", "SOUND_NAME_FALLBACK_760_TO_761_MAP")
    # 6. アクセサメソッド（mapBlockState763To762 の javadoc の直前）
    insert_before("    /** 763のブロック状態ID → 762のブロック状態ID。",
                  METHODS_760, "760アクセサ", "mapBlockState761To760")
    # 7. target 分岐への 760 追加（各 dispatch メソッドの 761 分岐の直前。
    # insert_after はメソッド冒頭の直後に入れるため、761 分岐より前に来る）
    for name, expr761, f760 in DISPATCH_760:
        if name == "mapItemReverse":
            body = (f"        if (clientProtocol == PROTOCOL_760) {{\n"
                    f"            return mapItemReverse(mapItem760To761(mapItem761To762(mapItem762To763(id))));\n"
                    f"        }}\n")
            anchor = f"    public static int {name}(int id, int clientProtocol) {{\n"
            marker = "mapItemReverse(mapItem760To761(mapItem761To762(mapItem762To763(id))))"
        elif name == "mapEffect":
            body = (f"        if (clientProtocol == PROTOCOL_760) {{\n"
                    f"            return mapEffect(id765);\n"
                    f"        }}\n")
            anchor = f"    public static int {name}(int id765, int clientProtocol) {{\n"
            marker = ("if (clientProtocol == PROTOCOL_760) {\n"
                      "            return mapEffect(id765);\n"
                      "        }\n"
                      "        if (clientProtocol == PROTOCOL_761)")
        else:
            body = (f"        if (clientProtocol == PROTOCOL_760) {{\n"
                    f"            return {f760}({expr761});\n"
                    f"        }}\n")
            anchor = f"    public static int {name}(int id765, int clientProtocol) {{\n"
            marker = f"return {f760}({expr761});"
        insert_after(anchor, body, f"dispatch {name} 760", marker)
    # 8. mapSoundName 分岐への 760 追加
    insert_after("    public static String mapSoundName(String name, int clientProtocol) {\n",
                 "        if (clientProtocol == PROTOCOL_760) {\n"
                 "            return mapSoundName761To760(mapSoundName762To761(mapSoundName763To762(mapSoundName(name))));\n"
                 "        }\n",
                 "dispatch mapSoundName 760", "mapSoundName761To760(mapSoundName762To761(mapSoundName763To762(mapSoundName(name))))")
    P.write_text(src, encoding="utf-8")
    print("RegistryRemapper.java に 760 を差し込んだ。")


if __name__ == "__main__":
    raise SystemExit(main())
