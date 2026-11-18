#!/usr/bin/env python3
"""RegistryRemapper.java に 485（1.14.2）の定数・委譲メソッドを追加する。

485 のレジストリは、変換表の対象カテゴリでは 490 と完全一致する
（実バニラ 1.14.2／1.14.3 両 jar の data-generator ダンプで照合済み：
blocks.json はバイト単位で同一、registries.json の差は
memory_module_type（24→26件）・recipe_serializer（19→20件）・
sensor_type（9→10件）の3カテゴリだけで、いずれも変換表に現れない）。
そのため 485⇔758 の写像は
490⇔758 の表へ委譲する。573 が 578 へ委譲したのと同じ流儀。

使い方: python3 tools/patch-485-remapper.py
冪等：485 が既にあれば何もしない。
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
     * 485（1.14.2）側の最大の番号と代替先。485 のレジストリは変換表の
     * 対象カテゴリでは 490 と完全一致するため（両 jar の data-generator
     * ダンプで全件照合済み）、485 の定数は 490 と同値で、485⇔758 の写像も
     * 490⇔758 の表へ委譲する。将来 485 と 490 の差が見つかった場合は
     * ここに専用の表を持たせる。
     */
    public static final int MAX_BLOCK_STATE_485 = MAX_BLOCK_STATE_490;
    public static final int MAX_ITEM_485 = MAX_ITEM_490;
    public static final int MAX_ENTITY_TYPE_485 = MAX_ENTITY_TYPE_490;
    public static final int MAX_SOUND_485 = MAX_SOUND_490;
    public static final int MAX_PARTICLE_485 = MAX_PARTICLE_490;
    public static final int MAX_BLOCK_485 = MAX_BLOCK_490;
    public static final int MAX_MENU_485 = MAX_MENU_490;
    public static final int MAX_ENCHANTMENT_485 = MAX_ENCHANTMENT_490;
    public static final int MAX_BIOME_485 = MAX_BIOME_490;
    public static final int MAX_DAMAGE_TYPE_485 = MAX_DAMAGE_TYPE_490;
    public static final int MAX_BLOCK_ENTITY_485 = MAX_BLOCK_ENTITY_490;
    public static final int MAX_PAINTING_VARIANT_485 = MAX_PAINTING_VARIANT_490;
    public static final int STONE_BLOCK_STATE_485 = STONE_BLOCK_STATE_490;
    public static final int AIR_BLOCK_STATE_485 = AIR_BLOCK_STATE_490;
    public static final int STONE_ITEM_485 = STONE_ITEM_490;
    public static final int PIG_ENTITY_TYPE_485 = PIG_ENTITY_TYPE_490;
    public static final int GENERIC_SOUND_485 = GENERIC_SOUND_490;
    public static final int AMBIENT_PARTICLE_485 = AMBIENT_PARTICLE_490;
    public static final int STONE_BLOCK_485 = STONE_BLOCK_490;
    public static final int CRAFTING_MENU_485 = CRAFTING_MENU_490;
    public static final int PLAINS_BIOME_485 = PLAINS_BIOME_490;
    public static final int GENERIC_DAMAGE_TYPE_485 = GENERIC_DAMAGE_TYPE_490;
"""

METHODS = """
    // 485 ⇔ 758（1.14.2 ⇔ 1.18.2）
    // 485 のレジストリは変換表の対象カテゴリでは 490 と完全一致する
    // ため（両 jar の data-generator ダンプで全件照合済み）、485⇔758 の写像は
    // 490⇔758 の表へ委譲する。将来 485 と 490 の差が見つかった場合は
    // ここに専用の表を持たせる。

    /** 758のブロック状態ID → 485のブロック状態ID（490 と同一の表）。 */
    public static int mapBlockState758To485(int id758) {
        return mapBlockState758To490(id758);
    }

    /** 485のブロック状態ID → 758のブロック状態ID（490 と同一の表）。 */
    public static int mapBlockState485To758(int id485) {
        return mapBlockState490To758(id485);
    }

    /** 758のアイテムID → 485のアイテムID（490 と同一の表）。 */
    public static int mapItem758To485(int id758) {
        return mapItem758To490(id758);
    }

    /** 485のアイテムID → 758のアイテムID（490 と同一の表）。 */
    public static int mapItem485To758(int id485) {
        return mapItem490To758(id485);
    }

    /** 758のエンティティ種別ID → 485のエンティティ種別ID（490 と同一の表）。 */
    public static int mapEntityType758To485(int id758) {
        return mapEntityType758To490(id758);
    }

    /** 485のエンティティ種別ID → 758のエンティティ種別ID（490 と同一の表）。 */
    public static int mapEntityType485To758(int id485) {
        return mapEntityType490To758(id485);
    }

    /** 758の音ID → 485の音ID（490 と同一の表）。 */
    public static int mapSound758To485(int id758) {
        return mapSound758To490(id758);
    }

    /** 485の音ID → 758の音ID（490 と同一の表）。 */
    public static int mapSound485To758(int id485) {
        return mapSound490To758(id485);
    }

    /** 758のパーティクルID → 485のパーティクルID（490 と同一の表）。 */
    public static int mapParticle758To485(int id758) {
        return mapParticle758To490(id758);
    }

    /** 485のパーティクルID → 758のパーティクルID（490 と同一の表）。 */
    public static int mapParticle485To758(int id485) {
        return mapParticle490To758(id485);
    }

    /** 758のブロックID → 485のブロックID（490 と同一の表）。 */
    public static int mapBlock758To485(int id758) {
        return mapBlock758To490(id758);
    }

    /** 485のブロックID → 758のブロックID（490 と同一の表）。 */
    public static int mapBlock485To758(int id485) {
        return mapBlock490To758(id485);
    }

    /** 758のメニュー種別ID → 485のメニュー種別ID（490 と同一の表）。 */
    public static int mapMenu758To485(int id758) {
        return mapMenu758To490(id758);
    }

    /** 485のメニュー種別ID → 758のメニュー種別ID（490 と同一の表）。 */
    public static int mapMenu485To758(int id485) {
        return mapMenu490To758(id485);
    }

    /** 758のブロックエンティティ種別ID → 485のブロックエンティティ種別ID（490 と同一の表）。 */
    public static int mapBlockEntityType758To485(int id758) {
        return mapBlockEntityType758To490(id758);
    }

    /** 485のブロックエンティティ種別ID → 758のブロックエンティティ種別ID（490 と同一の表）。 */
    public static int mapBlockEntityType485To758(int id485) {
        return mapBlockEntityType490To758(id485);
    }

    /** 758のバイオームID → 485のバイオームID（490 と同一の表）。 */
    public static int mapBiome758To485(int id758) {
        return mapBiome758To490(id758);
    }

    /** 485のバイオームID → 758のバイオームID（490 と同一の表）。 */
    public static int mapBiome485To758(int id485) {
        return mapBiome490To758(id485);
    }

    /** 485 のバイオーム名（レジストリ順。490 と同一）。Bedrock 側が次元情報に使う。 */
    public static java.util.List<String> biomeNames485() {
        return biomeNames490();
    }

    /** 758のダメージ種別ID → 485のダメージ種別ID（490 と同一の表。恒等写像）。 */
    public static int mapDamageType758To485(int id758) {
        return mapDamageType758To490(id758);
    }

    /** 485のダメージ種別ID → 758のダメージ種別ID（490 と同一の表。恒等写像）。 */
    public static int mapDamageType485To758(int id485) {
        return mapDamageType490To758(id485);
    }

    /** 758の音の名前 → 485の音の名前（490 と同一の表。Stop Sound 用）。 */
    public static String mapSoundName758To485(String name) {
        return mapSoundName758To490(name);
    }

    /** 485の音の名前 → 758の音の名前（490 と同一の表。Stop Sound 用）。 */
    public static String mapSoundName485To758(String name) {
        return mapSoundName490To758(name);
    }
"""


def main():
    text = P.read_text(encoding="utf-8")
    if "PROTOCOL_485" in text:
        print("skip（既存）: 485 はすでにある")
        return 0

    # 1. PROTOCOL_485 定数。
    text = rep(text,
               "    /** 490（1.14.3）のプロトコル番号。 */\n"
               "    public static final int PROTOCOL_490 = 490;",
               "    /** 485（1.14.2）のプロトコル番号。 */\n"
               "    public static final int PROTOCOL_485 = 485;\n"
               "    /** 490（1.14.3）のプロトコル番号。 */\n"
               "    public static final int PROTOCOL_490 = 490;")

    # 2. MAX_*_485 定数群（490 の定数群の直後）。
    anchor = "    public static final int GENERIC_DAMAGE_TYPE_490 = GENERIC_DAMAGE_TYPE_498;"
    text = rep(text, anchor, anchor + "\n" + CONSTANTS)

    # 3. メソッド群（490 セクションの直後、760⇔759 セクションの前）。
    anchor = "    // 760 ⇔ 759（1.19.1／1.19.2 ⇔ 1.19）"
    text = rep(text, anchor, METHODS.strip("\n") + "\n" + anchor)

    P.write_text(text, encoding="utf-8")
    print("RegistryRemapper.java を更新した")
    return 0


if __name__ == "__main__":
    sys.exit(main())
