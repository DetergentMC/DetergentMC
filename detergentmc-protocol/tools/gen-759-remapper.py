#!/usr/bin/env python3
"""RegistryRemapper.java に 759（1.19）対応を差し込む。

759 と 760 のレジストリは全カテゴリで同一（恒等写像。gen-759-tables.py
で検証済み）のため、759 の表は恒等写像のみ。759 の dispatch 分岐は
760 の合成の外側に mapX760To759 をもう一段かぶせる。

アンカー付き挿入のみ行う。アンカーが見つからなければ失敗して何も書かない。
使い方: python3 gen-759-remapper.py
書き換えた後は必ず git diff で内容を確認してからコミットすること。
"""
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parent.parent
P = ROOT / "src/main/java/io/detergentmc/protocol/RegistryRemapper.java"

IMPORT = "import static io.detergentmc.protocol.RegistryTables759.*;\n"

PROTOCOL_CONST = "    /** 759（1.19）のプロトコル番号。 */\n    public static final int PROTOCOL_759 = 759;\n"

CONSTANTS_759 = """
    /** 759側の最大の番号（759と760は全カテゴリ同一のため760と同じ値）。 */
    public static final int MAX_BLOCK_STATE_759 = 21447;
    public static final int MAX_ITEM_759 = 1151;
    public static final int MAX_ENTITY_TYPE_759 = 117;
    public static final int MAX_SOUND_759 = 1320;
    public static final int MAX_PARTICLE_759 = 92;
    public static final int MAX_BLOCK_759 = 932;
    public static final int MAX_MENU_759 = 23;
    /** 759側のエンチャントは 0〜38（39件。760と同一順序のため表は作らない）。 */
    public static final int MAX_ENCHANTMENT_759 = 38;
    /** 759側のバイオームは 0〜62（63件。760と同一順序・同一内容）。 */
    public static final int MAX_BIOME_759 = 62;
    /** 759側のダメージ種別は 0〜41（42件。762 の順序の恒等写像。実バニラで検証する）。 */
    public static final int MAX_DAMAGE_TYPE_759 = 41;
    /** 759側のブロックエンティティ種別は 0〜35（36件）。 */
    public static final int MAX_BLOCK_ENTITY_759 = 35;

    /** 759側の代替先（760と同じ番号。Speed だけ 1ベースで 1）。 */
    public static final int STONE_BLOCK_STATE_759 = 1;
    public static final int AIR_BLOCK_STATE_759 = 0;
    public static final int STONE_ITEM_759 = 1;
    public static final int PIG_ENTITY_TYPE_759 = 67;
    public static final int GENERIC_SOUND_759 = 1112; // block.stone.break
    public static final int AMBIENT_PARTICLE_759 = 0; // ambient_entity_effect
    public static final int STONE_BLOCK_759 = 1;
    public static final int CRAFTING_MENU_759 = 11;
    public static final int SPEED_EFFECT_759 = 1;
    /** 759側の平原バイオーム（minecraft-data pc/1.19/biomes.json）。 */
    public static final int PLAINS_BIOME_759 = 1;
    /** 759側の generic ダメージ種別（762 と同一順序と仮定）。 */
    public static final int GENERIC_DAMAGE_TYPE_759 = 16;
"""

# (メソッド名の接頭辞, 日本語名, 代替先759, 代替先760)
PAIRS = [
    ("BlockState", "ブロック状態", "STONE_BLOCK_STATE_759", "STONE_BLOCK_STATE_760"),
    ("Item", "アイテム", "STONE_ITEM_759", "STONE_ITEM_760"),
    ("EntityType", "エンティティ種別", "PIG_ENTITY_TYPE_759", "PIG_ENTITY_TYPE_760"),
    ("Sound", "音", "GENERIC_SOUND_759", "GENERIC_SOUND_760"),
    ("Particle", "パーティクル", "AMBIENT_PARTICLE_759", "AMBIENT_PARTICLE_760"),
    ("Block", "ブロック", "STONE_BLOCK_759", "STONE_BLOCK_760"),
    ("Menu", "メニュー種別", "CRAFTING_MENU_759", "CRAFTING_MENU_760"),
    ("BlockEntityType", "ブロックエンティティ種別", "DROP", "DROP"),
    ("Biome", "バイオーム", "PLAINS_BIOME_759", "PLAINS_BIOME_760"),
]


def build_maps():
    out = []
    for prefix, _jp, fb759, fb760 in PAIRS:
        upper = {"BlockState": "BLOCK_STATE", "Item": "ITEM", "EntityType": "ENTITY_TYPE",
                 "Sound": "SOUND", "Particle": "PARTICLE", "Block": "BLOCK",
                 "Menu": "MENU", "BlockEntityType": "BLOCK_ENTITY", "Biome": "BIOME"}[prefix]
        max759 = f"MAX_{upper}_759"
        max760 = f"MAX_{upper}_760"
        out.append(f"    /** 760の{prefix}ID → 759の{prefix}ID（RegistryTables759。恒等写像）。 */\n"
                   f"    private static final int[] {upper}_760_TO_759_MAP =\n"
                   f"            expand({upper}_759_RANGES, {upper}_759_OVERRIDES, {max760} + 1,\n"
                   f"                    {fb759});\n")
        out.append(f"    /** 759の{prefix}ID → 760の{prefix}ID（RegistryTables759。恒等写像）。 */\n"
                   f"    private static final int[] {upper}_759_TO_760_MAP =\n"
                   f"            expand({upper}_759_TO_760_RANGES, {upper}_759_TO_760_OVERRIDES,\n"
                   f"                    {max759} + 1, {fb760});\n")
    out.append("    /** 760のダメージ種別ID → 759のダメージ種別ID。無い種別は generic へ。 */\n"
               "    private static final int[] DAMAGE_TYPE_760_TO_759_MAP =\n"
               "            expand(DAMAGE_TYPE_759_RANGES, DAMAGE_TYPE_759_OVERRIDES, MAX_DAMAGE_TYPE_760 + 1,\n"
               "                    GENERIC_DAMAGE_TYPE_759);\n")
    out.append("    /** 759のダメージ種別ID → 760のダメージ種別ID。 */\n"
               "    private static final int[] DAMAGE_TYPE_759_TO_760_MAP =\n"
               "            expand(DAMAGE_TYPE_759_TO_760_RANGES, DAMAGE_TYPE_759_TO_760_OVERRIDES,\n"
               "                    MAX_DAMAGE_TYPE_759 + 1, GENERIC_DAMAGE_TYPE_760);\n")
    return "\n" + "\n".join(out)


SOUND_NAME_MAP_759 = """
    /** 760の音の名前 → 759の音の名前（Stop Sound 用）。恒等写像のため空。 */
    private static final Map<String, String> SOUND_NAME_FALLBACK_760_TO_759_MAP =
            buildSoundNameMap(SOUND_NAME_FALLBACKS_760_TO_759);
"""

SOUND_NAME_MAP_759_REV = """
    /** 759の音の名前 → 760の音の名前（Stop Sound 用）。恒等写像のため空。 */
    private static final Map<String, String> SOUND_NAME_FALLBACK_759_TO_760_MAP =
            buildSoundNameMap(SOUND_NAME_FALLBACKS_759_TO_760);
"""


# メソッド生成は可読性のため明示的に書く（下の METHODS_759）。
METHODS_759 = """
    // ------------------------------------------------------------------
    // 760 ⇔ 759（1.19.1／1.19.2 ⇔ 1.19）
    // パケットIDは 131 件が変わる（PacketIds の 759 列を参照）。
    // レジストリは全カテゴリ同一のため RegistryTables759 は恒等写像のみ。
    // ペイロードの差は Player Chat／Chat Message／Chat Command の署名形式、
    // System Chat の type varint、Server Data の enforcesSecureChat の有無、
    // Trade List の件数の型（u8⇔varint）、Login Start の playerUUID の有無。
    // 署名付きチャットは 759（1.19）で導入された。760 は連鎖ハッシュ＋
    // Message Header 付きの別形式（依頼文の「760で導入」は誤り。759が初出）。
    // ------------------------------------------------------------------

    /** 760のブロック状態ID → 759のブロック状態ID。範囲外はSTONE_BLOCK_STATE_759 に相当する代替。 */
    public static int mapBlockState760To759(int id760) {
        if (id760 < 0 || id760 >= BLOCK_STATE_760_TO_759_MAP.length) {
            logUnknownComposedOnce("760→759", "ブロック状態", id760);
            return STONE_BLOCK_STATE_759;
        }
        return BLOCK_STATE_760_TO_759_MAP[id760];
    }

    /** 759のブロック状態ID → 760のブロック状態ID。範囲外はSTONE_BLOCK_STATE_760 に相当する代替。 */
    public static int mapBlockState759To760(int id759) {
        if (id759 < 0 || id759 >= BLOCK_STATE_759_TO_760_MAP.length) {
            logUnknownComposedOnce("759→760", "ブロック状態", id759);
            return STONE_BLOCK_STATE_760;
        }
        return BLOCK_STATE_759_TO_760_MAP[id759];
    }

    /** 760のアイテムID → 759のアイテムID。範囲外はSTONE_ITEM_759 に相当する代替。 */
    public static int mapItem760To759(int id760) {
        if (id760 < 0 || id760 >= ITEM_760_TO_759_MAP.length) {
            logUnknownComposedOnce("760→759", "アイテム", id760);
            return STONE_ITEM_759;
        }
        return ITEM_760_TO_759_MAP[id760];
    }

    /** 759のアイテムID → 760のアイテムID。範囲外はSTONE_ITEM_760 に相当する代替。 */
    public static int mapItem759To760(int id759) {
        if (id759 < 0 || id759 >= ITEM_759_TO_760_MAP.length) {
            logUnknownComposedOnce("759→760", "アイテム", id759);
            return STONE_ITEM_760;
        }
        return ITEM_759_TO_760_MAP[id759];
    }

    /** 760のエンティティ種別ID → 759のエンティティ種別ID。範囲外はPIG_ENTITY_TYPE_759 に相当する代替。 */
    public static int mapEntityType760To759(int id760) {
        if (id760 < 0 || id760 >= ENTITY_TYPE_760_TO_759_MAP.length) {
            logUnknownComposedOnce("760→759", "エンティティ種別", id760);
            return PIG_ENTITY_TYPE_759;
        }
        return ENTITY_TYPE_760_TO_759_MAP[id760];
    }

    /** 759のエンティティ種別ID → 760のエンティティ種別ID。範囲外はPIG_ENTITY_TYPE_760 に相当する代替。 */
    public static int mapEntityType759To760(int id759) {
        if (id759 < 0 || id759 >= ENTITY_TYPE_759_TO_760_MAP.length) {
            logUnknownComposedOnce("759→760", "エンティティ種別", id759);
            return PIG_ENTITY_TYPE_760;
        }
        return ENTITY_TYPE_759_TO_760_MAP[id759];
    }

    /** 760の音ID → 759の音ID。範囲外はGENERIC_SOUND_759 に相当する代替。 */
    public static int mapSound760To759(int id760) {
        if (id760 < 0 || id760 >= SOUND_760_TO_759_MAP.length) {
            logUnknownComposedOnce("760→759", "音", id760);
            return GENERIC_SOUND_759;
        }
        return SOUND_760_TO_759_MAP[id760];
    }

    /** 759の音ID → 760の音ID。範囲外はGENERIC_SOUND_760 に相当する代替。 */
    public static int mapSound759To760(int id759) {
        if (id759 < 0 || id759 >= SOUND_759_TO_760_MAP.length) {
            logUnknownComposedOnce("759→760", "音", id759);
            return GENERIC_SOUND_760;
        }
        return SOUND_759_TO_760_MAP[id759];
    }

    /** 760のパーティクルID → 759のパーティクルID。範囲外はAMBIENT_PARTICLE_759 に相当する代替。 */
    public static int mapParticle760To759(int id760) {
        if (id760 < 0 || id760 >= PARTICLE_760_TO_759_MAP.length) {
            logUnknownComposedOnce("760→759", "パーティクル", id760);
            return AMBIENT_PARTICLE_759;
        }
        return PARTICLE_760_TO_759_MAP[id760];
    }

    /** 759のパーティクルID → 760のパーティクルID。範囲外はAMBIENT_PARTICLE_760 に相当する代替。 */
    public static int mapParticle759To760(int id759) {
        if (id759 < 0 || id759 >= PARTICLE_759_TO_760_MAP.length) {
            logUnknownComposedOnce("759→760", "パーティクル", id759);
            return AMBIENT_PARTICLE_760;
        }
        return PARTICLE_759_TO_760_MAP[id759];
    }

    /** 760のブロックID → 759のブロックID。範囲外はSTONE_BLOCK_759 に相当する代替。 */
    public static int mapBlock760To759(int id760) {
        if (id760 < 0 || id760 >= BLOCK_760_TO_759_MAP.length) {
            logUnknownComposedOnce("760→759", "ブロック", id760);
            return STONE_BLOCK_759;
        }
        return BLOCK_760_TO_759_MAP[id760];
    }

    /** 759のブロックID → 760のブロックID。範囲外はSTONE_BLOCK_760 に相当する代替。 */
    public static int mapBlock759To760(int id759) {
        if (id759 < 0 || id759 >= BLOCK_759_TO_760_MAP.length) {
            logUnknownComposedOnce("759→760", "ブロック", id759);
            return STONE_BLOCK_760;
        }
        return BLOCK_759_TO_760_MAP[id759];
    }

    /** 760のメニュー種別ID → 759のメニュー種別ID。範囲外はCRAFTING_MENU_759 に相当する代替。 */
    public static int mapMenu760To759(int id760) {
        if (id760 < 0 || id760 >= MENU_760_TO_759_MAP.length) {
            logUnknownComposedOnce("760→759", "メニュー", id760);
            return CRAFTING_MENU_759;
        }
        return MENU_760_TO_759_MAP[id760];
    }

    /** 759のメニュー種別ID → 760のメニュー種別ID。範囲外はCRAFTING_MENU_760 に相当する代替。 */
    public static int mapMenu759To760(int id759) {
        if (id759 < 0 || id759 >= MENU_759_TO_760_MAP.length) {
            logUnknownComposedOnce("759→760", "メニュー", id759);
            return CRAFTING_MENU_760;
        }
        return MENU_759_TO_760_MAP[id759];
    }

    /**
     * 760のブロックエンティティ種別ID → 759のブロックエンティティ種別ID。
     * 759にしか無い種別は無いため DROP にはならない。
     */
    public static int mapBlockEntityType760To759(int id760) {
        if (id760 < 0 || id760 >= BLOCK_ENTITY_760_TO_759_MAP.length) {
            logUnknownComposedOnce("760→759", "ブロックエンティティ種別", id760);
            return DROP;
        }
        return BLOCK_ENTITY_760_TO_759_MAP[id760];
    }

    /**
     * 759のブロックエンティティ種別ID → 760のブロックエンティティ種別ID。
     * 759にしか無い種別は無いため DROP にはならない。
     */
    public static int mapBlockEntityType759To760(int id759) {
        if (id759 < 0 || id759 >= BLOCK_ENTITY_759_TO_760_MAP.length) {
            logUnknownComposedOnce("759→760", "ブロックエンティティ種別", id759);
            return DROP;
        }
        return BLOCK_ENTITY_759_TO_760_MAP[id759];
    }

    /** 760のバイオームID → 759のバイオームID。範囲外はPLAINS_BIOME_759 に相当する代替。 */
    public static int mapBiome760To759(int id760) {
        if (id760 < 0 || id760 >= BIOME_760_TO_759_MAP.length) {
            logUnknownComposedOnce("760→759", "バイオーム", id760);
            return PLAINS_BIOME_759;
        }
        return BIOME_760_TO_759_MAP[id760];
    }

    /** 759のバイオームID → 760のバイオームID。範囲外はPLAINS_BIOME_760 に相当する代替。 */
    public static int mapBiome759To760(int id759) {
        if (id759 < 0 || id759 >= BIOME_759_TO_760_MAP.length) {
            logUnknownComposedOnce("759→760", "バイオーム", id759);
            return PLAINS_BIOME_760;
        }
        return BIOME_759_TO_760_MAP[id759];
    }

    /** 760のダメージ種別ID → 759のダメージ種別ID（同一順序と仮定）。 */
    public static int mapDamageType760To759(int id760) {
        if (id760 < 0 || id760 > MAX_DAMAGE_TYPE_760) {
            logUnknownOnce(PROTOCOL_759, "ダメージ種別", id760);
            return DROP;
        }
        return DAMAGE_TYPE_760_TO_759_MAP[id760];
    }

    /** 759のダメージ種別ID → 760のダメージ種別ID（同一順序と仮定）。 */
    public static int mapDamageType759To760(int id759) {
        if (id759 < 0 || id759 > MAX_DAMAGE_TYPE_759) {
            logUnknownOnce(PROTOCOL_760, "ダメージ種別", id759);
            return DROP;
        }
        return DAMAGE_TYPE_759_TO_760_MAP[id759];
    }

    /** 760の音の名前 → 759の音の名前（Stop Sound 用）。恒等写像のためそのまま返す。 */
    public static String mapSoundName760To759(String name) {
        return mapSoundNameComposed(name, SOUND_NAME_FALLBACK_760_TO_759_MAP);
    }

    /** 759の音の名前 → 760の音の名前（Stop Sound 用）。恒等写像のためそのまま返す。 */
    public static String mapSoundName759To760(String name) {
        return mapSoundNameComposed(name, SOUND_NAME_FALLBACK_759_TO_760_MAP);
    }
"""

# 765→759 の合成（dispatch 分岐用）。(メソッド名, 765→760 の式, 760→759 の式)
DISPATCH_759 = [
    ("mapBlockState", "mapBlockState761To760(mapBlockState762To761(mapBlockState763To762(mapBlockState(id765))))", "mapBlockState760To759"),
    ("mapItem", "mapItem761To760(mapItem762To761(mapItem763To762(mapItem(id765))))", "mapItem760To759"),
    ("mapItemReverse", "mapItemReverse(mapItem760To761(mapItem761To762(mapItem762To763(id))))", None),
    ("mapEntityType", "mapEntityType761To760(mapEntityType762To761(mapEntityType763To762(mapEntityType(id765))))", "mapEntityType760To759"),
    ("mapSound", "mapSound761To760(mapSound762To761(mapSound763To762(mapSound(id765))))", "mapSound760To759"),
    ("mapParticle", "mapParticle761To760(mapParticle762To761(mapParticle763To762(mapParticle(id765))))", "mapParticle760To759"),
    ("mapBlock", "mapBlock761To760(mapBlock762To761(mapBlock763To762(mapBlock(id765))))", "mapBlock760To759"),
    ("mapMenu", "mapMenu761To760(mapMenu762To761(mapMenu763To762(mapMenu(id765))))", "mapMenu760To759"),
    ("mapEffect", "mapEffect(id765)", None),
    ("mapBlockEntityType", "mapBlockEntityType761To760(mapBlockEntityType762To761(mapBlockEntityType763To762(mapBlockEntityType(id765))))",
     "mapBlockEntityType760To759"),
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
    insert_before("import static io.detergentmc.protocol.RegistryTables760.*;",
                  IMPORT, "import RegistryTables759", "RegistryTables759.*")
    # 2. PROTOCOL_759（PROTOCOL_760 の直前）
    insert_before("    public static final int PROTOCOL_760 = 760;",
                  PROTOCOL_CONST, "PROTOCOL_759", "PROTOCOL_759 = 759")
    # 3. 759定数（GENERIC_DAMAGE_TYPE_760 の直後）
    insert_after("    public static final int GENERIC_DAMAGE_TYPE_760 = 16;\n",
                 CONSTANTS_759, "759定数", "MAX_BLOCK_STATE_759")
    # 4. 759の表（761→760 の表の直前）
    insert_before("    private static final int[] BLOCK_STATE_761_TO_760_MAP =",
                  build_maps(), "759の表", "BLOCK_STATE_760_TO_759_MAP")
    # 5. 音の名前表（SOUND_NAME_FALLBACK_763_TO_762_MAP の直前）
    insert_before("    private static final Map<String, String> SOUND_NAME_FALLBACK_763_TO_762_MAP =",
                  SOUND_NAME_MAP_759, "音の名前表759", "SOUND_NAME_FALLBACK_760_TO_759_MAP")
    insert_before("    private static final Map<String, String> SOUND_NAME_FALLBACK_763_TO_762_MAP =",
                  SOUND_NAME_MAP_759_REV, "音の名前表759逆向き", "SOUND_NAME_FALLBACK_759_TO_760_MAP")
    # 6. アクセサメソッド（761⇔760 セクションの直前）
    insert_before("    // ------------------------------------------------------------------\n"
                  "    // 761 ⇔ 760（1.19.3 ⇔ 1.19.1／1.19.2）\n",
                  METHODS_759, "759アクセサ", "mapBlockState760To759")
    # 7. dispatch コメントに 759 を追記
    old_comment = ("    // プロトコル番号で切り替える変換（担当2の配線用）。\n"
                   "    // 765は素通し、766は766の表、767は767の表、768は768の表、\n")
    if old_comment in src:
        src = src.replace(old_comment,
                          "    // プロトコル番号で切り替える変換（担当2の配線用）。\n"
                          "    // 765は素通し、766は766の表、767は767の表、768は768の表、\n"
                          "    // 759は760の表を経由する（759↔760は恒等写像）。\n", 1)
    # 8. target 分岐への 759 追加（各 dispatch メソッドの 760 分岐の直後）
    for name, expr760, f759 in DISPATCH_759:
        if name == "mapItemReverse":
            body = ("        if (clientProtocol == PROTOCOL_759) {\n"
                    "            return mapItemReverse(mapItem759To760(mapItem760To761(mapItem761To762(mapItem762To763(id)))));\n"
                    "        }\n")
            anchor = ("        if (clientProtocol == PROTOCOL_760) {\n"
                      "            return mapItemReverse(mapItem760To761(mapItem761To762(mapItem762To763(id))));\n"
                      "        }\n")
            marker = "mapItemReverse(mapItem759To760(mapItem760To761(mapItem761To762(mapItem762To763(id)))))"
        elif name == "mapEffect":
            body = ("        if (clientProtocol == PROTOCOL_759) {\n"
                    "            return mapEffect(id765);\n"
                    "        }\n")
            anchor = ("        if (clientProtocol == PROTOCOL_760) {\n"
                      "            return mapEffect(id765);\n"
                      "        }\n")
            marker = ("if (clientProtocol == PROTOCOL_759) {\n"
                      "            return mapEffect(id765);\n"
                      "        }\n"
                      "        if (clientProtocol == PROTOCOL_761)")
        else:
            body = (f"        if (clientProtocol == PROTOCOL_759) {{\n"
                    f"            return {f759}({expr760});\n"
                    f"        }}\n")
            # 760 分岐の本文（expr760 は 765→760 の完全な式）をアンカーにする。
            anchor = (f"        if (clientProtocol == PROTOCOL_760) {{\n"
                      f"            return {expr760};\n"
                      f"        }}\n")
            marker = f"return {f759}({expr760});"
        insert_after(anchor, body, f"dispatch {name} 759", marker)
    # 9. mapSoundName 分岐への 759 追加
    insert_after("        if (clientProtocol == PROTOCOL_760) {\n"
                 "            return mapSoundName761To760(mapSoundName762To761(mapSoundName763To762(mapSoundName(name))));\n"
                 "        }\n",
                 "        if (clientProtocol == PROTOCOL_759) {\n"
                 "            return mapSoundName760To759(mapSoundName761To760(mapSoundName762To761(mapSoundName763To762(mapSoundName(name)))));\n"
                 "        }\n",
                 "dispatch mapSoundName 759",
                 "mapSoundName760To759(mapSoundName761To760(mapSoundName762To761(mapSoundName763To762(mapSoundName(name)))))")
    P.write_text(src, encoding="utf-8")
    print("RegistryRemapper.java に 759 を差し込んだ。")


if __name__ == "__main__":
    raise SystemExit(main())
