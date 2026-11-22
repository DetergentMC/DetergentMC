#!/usr/bin/env python3
"""RegistryRemapper.java に 758（1.18.2）対応を差し込む。

アンカー付き挿入のみ行う。アンカーが見つからなければ失敗して何も書かない。
使い方: python3 gen-758-remapper.py
書き換えた後は必ず git diff で内容を確認してからコミットすること。
"""
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parent.parent
P = ROOT / "src/main/java/io/detergentmc/protocol/RegistryRemapper.java"

IMPORT = "import static io.detergentmc.protocol.RegistryTables758.*;\n"

PROTOCOL_CONST = "    /** 758（1.18.2）のプロトコル番号。 */\n    public static final int PROTOCOL_758 = 758;\n"

CONSTANTS_758 = """
    /** 758側の最大の番号（ViaVersion mapping-1.18.json の件数−1）。 */
    public static final int MAX_BLOCK_STATE_758 = 20341;
    public static final int MAX_ITEM_758 = 1100;
    public static final int MAX_ENTITY_TYPE_758 = 112;
    public static final int MAX_SOUND_758 = 1202;
    public static final int MAX_PARTICLE_758 = 87;
    public static final int MAX_BLOCK_758 = 897;
    public static final int MAX_MENU_758 = 23;
    /** 758側のエンチャントは 0〜37（38件。759 と順序は同じで swift_sneak が無いため表は作らない）。 */
    public static final int MAX_ENCHANTMENT_758 = 37;
    /** 758側のバイオームは 0〜60（61件。mangrove_swamp・deep_dark が無い）。 */
    public static final int MAX_BIOME_758 = 60;
    /** 758側のダメージ種別は 0〜41（42件。759 と同じく codec には無いため恒等写像）。 */
    public static final int MAX_DAMAGE_TYPE_758 = 41;
    /** 758側のブロックエンティティ種別は 0〜33（34件）。 */
    public static final int MAX_BLOCK_ENTITY_758 = 33;
    /** 758側の絵画は 0〜25（26件）。759 は 0〜29（30件）。
     * 758〜765 の絵画は対応表を作らない（766 以降だけ mapping がある）。 */
    public static final int MAX_PAINTING_VARIANT_758 = 25;
    public static final int MAX_PAINTING_VARIANT_759 = 29;

    /** 758側の代替先（mapping-1.18.json の番号。Speed だけ 1ベースで 1）。 */
    public static final int STONE_BLOCK_STATE_758 = 1;
    public static final int AIR_BLOCK_STATE_758 = 0;
    public static final int STONE_ITEM_758 = 1;
    public static final int PIG_ENTITY_TYPE_758 = 64;
    public static final int GENERIC_SOUND_758 = 1018; // block.stone.break
    public static final int AMBIENT_PARTICLE_758 = 0; // ambient_entity_effect
    public static final int STONE_BLOCK_758 = 1;
    public static final int CRAFTING_MENU_758 = 11;
    public static final int SPEED_EFFECT_758 = 1;
    /** 758側の平原バイオーム（minecraft-data pc/1.18/biomes.json）。 */
    public static final int PLAINS_BIOME_758 = 1;
    /** 758側の generic ダメージ種別（762 と同一順序と仮定）。 */
    public static final int GENERIC_DAMAGE_TYPE_758 = 16;
"""

MAPS_758 = """
    /** 759のブロック状態ID → 758のブロック状態ID（RegistryTables758）。 */
    private static final int[] BLOCK_STATE_759_TO_758_MAP =
            expand(BLOCK_STATE_758_RANGES, BLOCK_STATE_758_OVERRIDES, MAX_BLOCK_STATE_759 + 1,
                    STONE_BLOCK_STATE_758);
    /** 758のブロック状態ID → 759のブロック状態ID（RegistryTables758）。 */
    private static final int[] BLOCK_STATE_758_TO_759_MAP =
            expand(BLOCK_STATE_758_TO_759_RANGES, BLOCK_STATE_758_TO_759_OVERRIDES,
                    MAX_BLOCK_STATE_758 + 1, STONE_BLOCK_STATE_759);
    /** 759のアイテムID → 758のアイテムID。 */
    private static final int[] ITEM_759_TO_758_MAP =
            expand(ITEM_758_RANGES, ITEM_758_OVERRIDES, MAX_ITEM_759 + 1, STONE_ITEM_758);
    /** 758のアイテムID → 759のアイテムID。 */
    private static final int[] ITEM_758_TO_759_MAP =
            expand(ITEM_758_TO_759_RANGES, ITEM_758_TO_759_OVERRIDES, MAX_ITEM_758 + 1,
                    STONE_ITEM_759);
    /** 759のエンティティ種別ID → 758のエンティティ種別ID。 */
    private static final int[] ENTITY_TYPE_759_TO_758_MAP =
            expand(ENTITY_TYPE_758_RANGES, ENTITY_TYPE_758_OVERRIDES, MAX_ENTITY_TYPE_759 + 1,
                    PIG_ENTITY_TYPE_758);
    /** 758のエンティティ種別ID → 759のエンティティ種別ID。 */
    private static final int[] ENTITY_TYPE_758_TO_759_MAP =
            expand(ENTITY_TYPE_758_TO_759_RANGES, ENTITY_TYPE_758_TO_759_OVERRIDES,
                    MAX_ENTITY_TYPE_758 + 1, PIG_ENTITY_TYPE_759);
    /** 759の音ID → 758の音ID。 */
    private static final int[] SOUND_759_TO_758_MAP =
            expand(SOUND_758_RANGES, SOUND_758_OVERRIDES, MAX_SOUND_759 + 1, GENERIC_SOUND_758);
    /** 758の音ID → 759の音ID。 */
    private static final int[] SOUND_758_TO_759_MAP =
            expand(SOUND_758_TO_759_RANGES, SOUND_758_TO_759_OVERRIDES, MAX_SOUND_758 + 1,
                    GENERIC_SOUND_759);
    /** 759のパーティクルID → 758のパーティクルID。 */
    private static final int[] PARTICLE_759_TO_758_MAP =
            expand(PARTICLE_758_RANGES, PARTICLE_758_OVERRIDES, MAX_PARTICLE_759 + 1,
                    AMBIENT_PARTICLE_758);
    /** 758のパーティクルID → 759のパーティクルID。 */
    private static final int[] PARTICLE_758_TO_759_MAP =
            expand(PARTICLE_758_TO_759_RANGES, PARTICLE_758_TO_759_OVERRIDES,
                    MAX_PARTICLE_758 + 1, AMBIENT_PARTICLE_759);
    /** 759のブロックID → 758のブロックID。 */
    private static final int[] BLOCK_759_TO_758_MAP =
            expand(BLOCK_758_RANGES, BLOCK_758_OVERRIDES, MAX_BLOCK_759 + 1, STONE_BLOCK_758);
    /** 758のブロックID → 759のブロックID。 */
    private static final int[] BLOCK_758_TO_759_MAP =
            expand(BLOCK_758_TO_759_RANGES, BLOCK_758_TO_759_OVERRIDES, MAX_BLOCK_758 + 1,
                    STONE_BLOCK_759);
    /** 759のメニュー種別ID → 758のメニュー種別ID（恒等写像）。 */
    private static final int[] MENU_759_TO_758_MAP =
            expand(MENU_758_RANGES, MENU_758_OVERRIDES, MAX_MENU_759 + 1, CRAFTING_MENU_758);
    /** 758のメニュー種別ID → 759のメニュー種別ID（恒等写像）。 */
    private static final int[] MENU_758_TO_759_MAP =
            expand(MENU_758_TO_759_RANGES, MENU_758_TO_759_OVERRIDES, MAX_MENU_758 + 1,
                    CRAFTING_MENU_759);
    /** 759のブロックエンティティ種別ID → 758のブロックエンティティ種別ID。無い種別は DROP。 */
    private static final int[] BLOCK_ENTITY_759_TO_758_MAP =
            expand(BLOCK_ENTITY_758_RANGES, BLOCK_ENTITY_758_OVERRIDES, MAX_BLOCK_ENTITY_759 + 1,
                    DROP);
    /** 758のブロックエンティティ種別ID → 759のブロックエンティティ種別ID。無い種別は DROP。 */
    private static final int[] BLOCK_ENTITY_758_TO_759_MAP =
            expand(BLOCK_ENTITY_758_TO_759_RANGES, BLOCK_ENTITY_758_TO_759_OVERRIDES,
                    MAX_BLOCK_ENTITY_758 + 1, DROP);
    /** 759のバイオームID → 758のバイオームID（mangrove_swamp は swamp、deep_dark は dripstone_caves へ）。 */
    private static final int[] BIOME_759_TO_758_MAP =
            expand(BIOME_758_RANGES, BIOME_758_OVERRIDES, MAX_BIOME_759 + 1, PLAINS_BIOME_758);
    /** 758のバイオームID → 759のバイオームID。 */
    private static final int[] BIOME_758_TO_759_MAP =
            expand(BIOME_758_TO_759_RANGES, BIOME_758_TO_759_OVERRIDES, MAX_BIOME_758 + 1,
                    PLAINS_BIOME_759);
    /** 759のダメージ種別ID → 758のダメージ種別ID（恒等写像。codec には無い）。 */
    private static final int[] DAMAGE_TYPE_759_TO_758_MAP =
            expand(DAMAGE_TYPE_758_RANGES, DAMAGE_TYPE_758_OVERRIDES, MAX_DAMAGE_TYPE_759 + 1,
                    GENERIC_DAMAGE_TYPE_758);
    /** 758のダメージ種別ID → 759のダメージ種別ID（恒等写像。codec には無い）。 */
    private static final int[] DAMAGE_TYPE_758_TO_759_MAP =
            expand(DAMAGE_TYPE_758_TO_759_RANGES, DAMAGE_TYPE_758_TO_759_OVERRIDES,
                    MAX_DAMAGE_TYPE_758 + 1, GENERIC_DAMAGE_TYPE_759);
"""

SOUND_NAME_MAP_758 = """
    /** 759の音の名前 → 758の音の名前（Stop Sound 用）。数値の合成表と一致させる。 */
    private static final Map<String, String> SOUND_NAME_FALLBACK_759_TO_758_MAP =
            buildSoundNameMap(SOUND_NAME_FALLBACKS_759_TO_758);
"""

SOUND_NAME_MAP_758_REV = """
    /** 758の音の名前 → 759の音の名前（Stop Sound 用）。758 の音はすべて 759 に同じ名前のため空。 */
    private static final Map<String, String> SOUND_NAME_FALLBACK_758_TO_759_MAP =
            buildSoundNameMap(SOUND_NAME_FALLBACKS_758_TO_759);
"""

METHODS_758 = """
    // ------------------------------------------------------------------
    // 758 ⇔ 759（1.18.2 ⇔ 1.19）
    // パケットIDは 123 件が変わる（PacketIds の 758 列を参照）。
    // レジストリは RegistryTables758（ViaVersion mapping-1.18／1.19.json
    // から名前で突き合わせたもの）を使う。順序は本物のバニラ 1.18.2 の
    // jar ダンプで全カテゴリ確認済み。
    // ペイロードの差は設定フェーズの有無、チャット署名の有無、出現
    // パケットの分割、スカルク振動パケット、葉の waterlogged など。
    // ------------------------------------------------------------------

    /** 759のブロック状態ID → 758のブロック状態ID。範囲外はSTONE_BLOCK_STATE_758 に相当する代替。 */
    public static int mapBlockState759To758(int id759) {
        if (id759 < 0 || id759 >= BLOCK_STATE_759_TO_758_MAP.length) {
            logUnknownComposedOnce("759→758", "ブロック状態", id759);
            return STONE_BLOCK_STATE_758;
        }
        return BLOCK_STATE_759_TO_758_MAP[id759];
    }

    /** 758のブロック状態ID → 759のブロック状態ID。範囲外はSTONE_BLOCK_STATE_759 に相当する代替。 */
    public static int mapBlockState758To759(int id758) {
        if (id758 < 0 || id758 >= BLOCK_STATE_758_TO_759_MAP.length) {
            logUnknownComposedOnce("758→759", "ブロック状態", id758);
            return STONE_BLOCK_STATE_759;
        }
        return BLOCK_STATE_758_TO_759_MAP[id758];
    }

    /** 759のアイテムID → 758のアイテムID。範囲外はSTONE_ITEM_758 に相当する代替。 */
    public static int mapItem759To758(int id759) {
        if (id759 < 0 || id759 >= ITEM_759_TO_758_MAP.length) {
            logUnknownComposedOnce("759→758", "アイテム", id759);
            return STONE_ITEM_758;
        }
        return ITEM_759_TO_758_MAP[id759];
    }

    /** 758のアイテムID → 759のアイテムID。範囲外はSTONE_ITEM_759 に相当する代替。 */
    public static int mapItem758To759(int id758) {
        if (id758 < 0 || id758 >= ITEM_758_TO_759_MAP.length) {
            logUnknownComposedOnce("758→759", "アイテム", id758);
            return STONE_ITEM_759;
        }
        return ITEM_758_TO_759_MAP[id758];
    }

    /** 759のエンティティ種別ID → 758のエンティティ種別ID。範囲外はPIG_ENTITY_TYPE_758 に相当する代替。 */
    public static int mapEntityType759To758(int id759) {
        if (id759 < 0 || id759 >= ENTITY_TYPE_759_TO_758_MAP.length) {
            logUnknownComposedOnce("759→758", "エンティティ種別", id759);
            return PIG_ENTITY_TYPE_758;
        }
        return ENTITY_TYPE_759_TO_758_MAP[id759];
    }

    /** 758のエンティティ種別ID → 759のエンティティ種別ID。範囲外はPIG_ENTITY_TYPE_759 に相当する代替。 */
    public static int mapEntityType758To759(int id758) {
        if (id758 < 0 || id758 >= ENTITY_TYPE_758_TO_759_MAP.length) {
            logUnknownComposedOnce("758→759", "エンティティ種別", id758);
            return PIG_ENTITY_TYPE_759;
        }
        return ENTITY_TYPE_758_TO_759_MAP[id758];
    }

    /** 759の音ID → 758の音ID。範囲外はGENERIC_SOUND_758 に相当する代替。 */
    public static int mapSound759To758(int id759) {
        if (id759 < 0 || id759 >= SOUND_759_TO_758_MAP.length) {
            logUnknownComposedOnce("759→758", "音", id759);
            return GENERIC_SOUND_758;
        }
        return SOUND_759_TO_758_MAP[id759];
    }

    /** 758の音ID → 759の音ID。範囲外はGENERIC_SOUND_759 に相当する代替。 */
    public static int mapSound758To759(int id758) {
        if (id758 < 0 || id758 >= SOUND_758_TO_759_MAP.length) {
            logUnknownComposedOnce("758→759", "音", id758);
            return GENERIC_SOUND_759;
        }
        return SOUND_758_TO_759_MAP[id758];
    }

    /** 759のパーティクルID → 758のパーティクルID。範囲外はAMBIENT_PARTICLE_758 に相当する代替。 */
    public static int mapParticle759To758(int id759) {
        if (id759 < 0 || id759 >= PARTICLE_759_TO_758_MAP.length) {
            logUnknownComposedOnce("759→758", "パーティクル", id759);
            return AMBIENT_PARTICLE_758;
        }
        return PARTICLE_759_TO_758_MAP[id759];
    }

    /** 758のパーティクルID → 759のパーティクルID。範囲外はAMBIENT_PARTICLE_759 に相当する代替。 */
    public static int mapParticle758To759(int id758) {
        if (id758 < 0 || id758 >= PARTICLE_758_TO_759_MAP.length) {
            logUnknownComposedOnce("758→759", "パーティクル", id758);
            return AMBIENT_PARTICLE_759;
        }
        return PARTICLE_758_TO_759_MAP[id758];
    }

    /** 759のブロックID → 758のブロックID。範囲外はSTONE_BLOCK_758 に相当する代替。 */
    public static int mapBlock759To758(int id759) {
        if (id759 < 0 || id759 >= BLOCK_759_TO_758_MAP.length) {
            logUnknownComposedOnce("759→758", "ブロック", id759);
            return STONE_BLOCK_758;
        }
        return BLOCK_759_TO_758_MAP[id759];
    }

    /** 758のブロックID → 759のブロックID。範囲外はSTONE_BLOCK_759 に相当する代替。 */
    public static int mapBlock758To759(int id758) {
        if (id758 < 0 || id758 >= BLOCK_758_TO_759_MAP.length) {
            logUnknownComposedOnce("758→759", "ブロック", id758);
            return STONE_BLOCK_759;
        }
        return BLOCK_758_TO_759_MAP[id758];
    }

    /** 759のメニュー種別ID → 758のメニュー種別ID。範囲外はCRAFTING_MENU_758 に相当する代替。 */
    public static int mapMenu759To758(int id759) {
        if (id759 < 0 || id759 >= MENU_759_TO_758_MAP.length) {
            logUnknownComposedOnce("759→758", "メニュー", id759);
            return CRAFTING_MENU_758;
        }
        return MENU_759_TO_758_MAP[id759];
    }

    /** 758のメニュー種別ID → 759のメニュー種別ID。範囲外はCRAFTING_MENU_759 に相当する代替。 */
    public static int mapMenu758To759(int id758) {
        if (id758 < 0 || id758 >= MENU_758_TO_759_MAP.length) {
            logUnknownComposedOnce("758→759", "メニュー", id758);
            return CRAFTING_MENU_759;
        }
        return MENU_758_TO_759_MAP[id758];
    }

    /**
     * 759のブロックエンティティ種別ID → 758のブロックエンティティ種別ID。
     * 759にしか無い sculk_catalyst・sculk_shrieker は sculk_sensor（33）へ。
     */
    public static int mapBlockEntityType759To758(int id759) {
        if (id759 < 0 || id759 >= BLOCK_ENTITY_759_TO_758_MAP.length) {
            logUnknownComposedOnce("759→758", "ブロックエンティティ種別", id759);
            return DROP;
        }
        return BLOCK_ENTITY_759_TO_758_MAP[id759];
    }

    /**
     * 758のブロックエンティティ種別ID → 759のブロックエンティティ種別ID。
     * 758にしか無い種別は無いため DROP にはならない。
     */
    public static int mapBlockEntityType758To759(int id758) {
        if (id758 < 0 || id758 >= BLOCK_ENTITY_758_TO_759_MAP.length) {
            logUnknownComposedOnce("758→759", "ブロックエンティティ種別", id758);
            return DROP;
        }
        return BLOCK_ENTITY_758_TO_759_MAP[id758];
    }

    /** 759のバイオームID → 758のバイオームID。範囲外はPLAINS_BIOME_758 に相当する代替。 */
    public static int mapBiome759To758(int id759) {
        if (id759 < 0 || id759 >= BIOME_759_TO_758_MAP.length) {
            logUnknownComposedOnce("759→758", "バイオーム", id759);
            return PLAINS_BIOME_758;
        }
        return BIOME_759_TO_758_MAP[id759];
    }

    /** 758のバイオームID → 759のバイオームID。範囲外はPLAINS_BIOME_759 に相当する代替。 */
    public static int mapBiome758To759(int id758) {
        if (id758 < 0 || id758 >= BIOME_758_TO_759_MAP.length) {
            logUnknownComposedOnce("758→759", "バイオーム", id758);
            return PLAINS_BIOME_759;
        }
        return BIOME_758_TO_759_MAP[id758];
    }

    /**
     * 759のダメージ種別ID → 758のダメージ種別ID（恒等写像。codec には無い）。
     */
    public static int mapDamageType759To758(int id759) {
        if (id759 < 0 || id759 > MAX_DAMAGE_TYPE_759) {
            logUnknownOnce(PROTOCOL_758, "ダメージ種別", id759);
            return DROP;
        }
        return DAMAGE_TYPE_759_TO_758_MAP[id759];
    }

    /** 758のダメージ種別ID → 759のダメージ種別ID（恒等写像。codec には無い）。 */
    public static int mapDamageType758To759(int id758) {
        if (id758 < 0 || id758 > MAX_DAMAGE_TYPE_758) {
            logUnknownOnce(PROTOCOL_759, "ダメージ種別", id758);
            return DROP;
        }
        return DAMAGE_TYPE_758_TO_759_MAP[id758];
    }

    /** 759の音の名前 → 758の音の名前（Stop Sound 用）。数値の合成表と一致させる。 */
    public static String mapSoundName759To758(String name) {
        return mapSoundNameComposed(name, SOUND_NAME_FALLBACK_759_TO_758_MAP);
    }

    /** 758の音の名前 → 759の音の名前（Stop Sound 用）。数値の合成表と一致させる。 */
    public static String mapSoundName758To759(String name) {
        return mapSoundNameComposed(name, SOUND_NAME_FALLBACK_758_TO_759_MAP);
    }
"""

# 765→758 の合成（target 分岐用）。dispatch は 765→763→762→761→760→759→758 の連鎖。
DISPATCH_758 = [
    ("mapBlockState",
     "mapBlockState759To758(mapBlockState760To759(mapBlockState761To760(mapBlockState762To761(mapBlockState763To762(mapBlockState(id765))))))"),
    ("mapItem",
     "mapItem759To758(mapItem760To759(mapItem761To760(mapItem762To761(mapItem763To762(mapItem(id765))))))"),
    ("mapEntityType",
     "mapEntityType759To758(mapEntityType760To759(mapEntityType761To760(mapEntityType762To761(mapEntityType763To762(mapEntityType(id765))))))"),
    ("mapSound",
     "mapSound759To758(mapSound760To759(mapSound761To760(mapSound762To761(mapSound763To762(mapSound(id765))))))"),
    ("mapParticle",
     "mapParticle759To758(mapParticle760To759(mapParticle761To760(mapParticle762To761(mapParticle763To762(mapParticle(id765))))))"),
    ("mapBlock",
     "mapBlock759To758(mapBlock760To759(mapBlock761To760(mapBlock762To761(mapBlock763To762(mapBlock(id765))))))"),
    ("mapMenu",
     "mapMenu759To758(mapMenu760To759(mapMenu761To760(mapMenu762To761(mapMenu763To762(mapMenu(id765))))))"),
    ("mapBlockEntityType",
     "mapBlockEntityType759To758(mapBlockEntityType760To759(mapBlockEntityType761To760(mapBlockEntityType762To761(mapBlockEntityType763To762(mapBlockEntityType(id765))))))"),
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
    insert_before("import static io.detergentmc.protocol.RegistryTables759.*;",
                  IMPORT, "import RegistryTables758", "RegistryTables758.*")
    # 2. PROTOCOL_758（PROTOCOL_759 の直前）
    insert_before("    public static final int PROTOCOL_759 = 759;",
                  PROTOCOL_CONST, "PROTOCOL_758", "PROTOCOL_758 = 758")
    # 3. 758定数（759定数ブロックの直後）
    insert_after("    public static final int GENERIC_DAMAGE_TYPE_759 = 16;\n",
                 CONSTANTS_758, "758定数", "MAX_BLOCK_STATE_758")
    # 4. 758の表（759の表の直前）
    insert_before("    /** 760のBlockStateID → 759のBlockStateID（RegistryTables759。恒等写像）。 */",
                  MAPS_758, "758の表", "BLOCK_STATE_759_TO_758_MAP")
    # 5. 音の名前表（SOUND_NAME_FALLBACK_760_TO_759_MAP の直前）
    insert_before("    private static final Map<String, String> SOUND_NAME_FALLBACK_760_TO_759_MAP =",
                  SOUND_NAME_MAP_758, "音の名前表758", "SOUND_NAME_FALLBACK_759_TO_758_MAP")
    insert_before("    private static final Map<String, String> SOUND_NAME_FALLBACK_760_TO_759_MAP =",
                  SOUND_NAME_MAP_758_REV, "音の名前表758逆向き", "SOUND_NAME_FALLBACK_758_TO_759_MAP")
    # 6. アクセサメソッド（760⇔759 セクションの直前）
    insert_before("    // 760 ⇔ 759（1.19.1／1.19.2 ⇔ 1.19）",
                  METHODS_758, "758アクセサ", "mapBlockState759To758")
    # 7. target 分岐への 758 追加（各 dispatch メソッドの先頭）
    for name, expr in DISPATCH_758:
        body = (f"        if (clientProtocol == PROTOCOL_758) {{\n"
                f"            return {expr};\n"
                f"        }}\n")
        anchor = f"    public static int {name}(int id765, int clientProtocol) {{\n"
        marker = expr
        insert_after(anchor, body, f"dispatch {name} 758", marker)
    # 7b. mapItemReverse 分岐
    insert_after("    public static int mapItemReverse(int id, int clientProtocol) {\n",
                 "        if (clientProtocol == PROTOCOL_758) {\n"
                 "            return mapItemReverse(mapItem758To759(mapItem759To760(mapItem760To761(mapItem761To762(mapItem762To763(id))))));\n"
                 "        }\n",
                 "dispatch mapItemReverse 758",
                 "mapItemReverse(mapItem758To759(")
    # 7c. mapEffect 分岐
    insert_after("    public static int mapEffect(int id765, int clientProtocol) {\n",
                 "        if (clientProtocol == PROTOCOL_758) {\n"
                 "            return mapEffect(id765);\n"
                 "        }\n",
                 "dispatch mapEffect 758",
                 "clientProtocol == PROTOCOL_758) {\n            return mapEffect(id765);")
    # 8. mapSoundName 分岐への 758 追加
    insert_after("    public static String mapSoundName(String name, int clientProtocol) {\n",
                 "        if (clientProtocol == PROTOCOL_758) {\n"
                 "            return mapSoundName759To758(mapSoundName760To759(mapSoundName761To760(mapSoundName762To761(mapSoundName763To762(mapSoundName(name))))));\n"
                 "        }\n",
                 "dispatch mapSoundName 758",
                 "mapSoundName759To758(mapSoundName760To759(")
    P.write_text(src, encoding="utf-8")
    print("RegistryRemapper.java に 758 を差し込んだ。")


if __name__ == "__main__":
    raise SystemExit(main())
