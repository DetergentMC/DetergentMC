#!/usr/bin/env python3
"""RegistryRemapper.java に 490（1.14.3）の定数・委譲メソッドを追加する。

490 のレジストリは 498 と全カテゴリ完全一致する（実バニラ 1.14.3／1.14.4
両 jar の data-generator ダンプで照合済み：blocks.json はバイト単位で
同一、registries.json のワイヤ変換に関わる全カテゴリが同値。唯一の差は
memory_module_type に 1.14.4 で opened_doors が追加されたことで、
このレジストリは変換表の対象外）。そのため 490⇔758 の写像は
498⇔758 の表へ委譲する。573 が 578 へ委譲したのと同じ流儀。

使い方: python3 tools/patch-490-remapper.py
冪等：490 が既にあれば何もしない。
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
     * 490（1.14.3）側の最大の番号と代替先。490 のレジストリは 498 と
     * 全カテゴリ完全一致するため（両 jar の data-generator ダンプで
     * 全件照合済み）、490 の定数は 498 と同値で、490⇔758 の写像も
     * 498⇔758 の表へ委譲する。将来 490 と 498 の差が見つかった場合は
     * ここに専用の表を持たせる。
     */
    public static final int MAX_BLOCK_STATE_490 = MAX_BLOCK_STATE_498;
    public static final int MAX_ITEM_490 = MAX_ITEM_498;
    public static final int MAX_ENTITY_TYPE_490 = MAX_ENTITY_TYPE_498;
    public static final int MAX_SOUND_490 = MAX_SOUND_498;
    public static final int MAX_PARTICLE_490 = MAX_PARTICLE_498;
    public static final int MAX_BLOCK_490 = MAX_BLOCK_498;
    public static final int MAX_MENU_490 = MAX_MENU_498;
    public static final int MAX_ENCHANTMENT_490 = MAX_ENCHANTMENT_498;
    public static final int MAX_BIOME_490 = MAX_BIOME_498;
    public static final int MAX_DAMAGE_TYPE_490 = MAX_DAMAGE_TYPE_498;
    public static final int MAX_BLOCK_ENTITY_490 = MAX_BLOCK_ENTITY_498;
    public static final int MAX_PAINTING_VARIANT_490 = MAX_PAINTING_VARIANT_498;
    public static final int STONE_BLOCK_STATE_490 = STONE_BLOCK_STATE_498;
    public static final int AIR_BLOCK_STATE_490 = AIR_BLOCK_STATE_498;
    public static final int STONE_ITEM_490 = STONE_ITEM_498;
    public static final int PIG_ENTITY_TYPE_490 = PIG_ENTITY_TYPE_498;
    public static final int GENERIC_SOUND_490 = GENERIC_SOUND_498;
    public static final int AMBIENT_PARTICLE_490 = AMBIENT_PARTICLE_498;
    public static final int STONE_BLOCK_490 = STONE_BLOCK_498;
    public static final int CRAFTING_MENU_490 = CRAFTING_MENU_498;
    public static final int PLAINS_BIOME_490 = PLAINS_BIOME_498;
    public static final int GENERIC_DAMAGE_TYPE_490 = GENERIC_DAMAGE_TYPE_498;
"""

METHODS = """
    // 490 ⇔ 758（1.14.3 ⇔ 1.18.2）
    // 490 のレジストリは 498 と全カテゴリ完全一致するため（両 jar の
    // data-generator ダンプで全件照合済み）、490⇔758 の写像は
    // 498⇔758 の表へ委譲する。将来 490 と 498 の差が見つかった場合は
    // ここに専用の表を持たせる。

    /** 758のブロック状態ID → 490のブロック状態ID（498 と同一の表）。 */
    public static int mapBlockState758To490(int id758) {
        return mapBlockState758To498(id758);
    }

    /** 490のブロック状態ID → 758のブロック状態ID（498 と同一の表）。 */
    public static int mapBlockState490To758(int id490) {
        return mapBlockState498To758(id490);
    }

    /** 758のアイテムID → 490のアイテムID（498 と同一の表）。 */
    public static int mapItem758To490(int id758) {
        return mapItem758To498(id758);
    }

    /** 490のアイテムID → 758のアイテムID（498 と同一の表）。 */
    public static int mapItem490To758(int id490) {
        return mapItem498To758(id490);
    }

    /** 758のエンティティ種別ID → 490のエンティティ種別ID（498 と同一の表）。 */
    public static int mapEntityType758To490(int id758) {
        return mapEntityType758To498(id758);
    }

    /** 490のエンティティ種別ID → 758のエンティティ種別ID（498 と同一の表）。 */
    public static int mapEntityType490To758(int id490) {
        return mapEntityType498To758(id490);
    }

    /** 758の音ID → 490の音ID（498 と同一の表）。 */
    public static int mapSound758To490(int id758) {
        return mapSound758To498(id758);
    }

    /** 490の音ID → 758の音ID（498 と同一の表）。 */
    public static int mapSound490To758(int id490) {
        return mapSound498To758(id490);
    }

    /** 758のパーティクルID → 490のパーティクルID（498 と同一の表）。 */
    public static int mapParticle758To490(int id758) {
        return mapParticle758To498(id758);
    }

    /** 490のパーティクルID → 758のパーティクルID（498 と同一の表）。 */
    public static int mapParticle490To758(int id490) {
        return mapParticle498To758(id490);
    }

    /** 758のブロックID → 490のブロックID（498 と同一の表）。 */
    public static int mapBlock758To490(int id758) {
        return mapBlock758To498(id758);
    }

    /** 490のブロックID → 758のブロックID（498 と同一の表）。 */
    public static int mapBlock490To758(int id490) {
        return mapBlock498To758(id490);
    }

    /** 758のメニュー種別ID → 490のメニュー種別ID（498 と同一の表）。 */
    public static int mapMenu758To490(int id758) {
        return mapMenu758To498(id758);
    }

    /** 490のメニュー種別ID → 758のメニュー種別ID（498 と同一の表）。 */
    public static int mapMenu490To758(int id490) {
        return mapMenu498To758(id490);
    }

    /** 758のブロックエンティティ種別ID → 490のブロックエンティティ種別ID（498 と同一の表）。 */
    public static int mapBlockEntityType758To490(int id758) {
        return mapBlockEntityType758To498(id758);
    }

    /** 490のブロックエンティティ種別ID → 758のブロックエンティティ種別ID（498 と同一の表）。 */
    public static int mapBlockEntityType490To758(int id490) {
        return mapBlockEntityType498To758(id490);
    }

    /** 758のバイオームID → 490のバイオームID（498 と同一の表）。 */
    public static int mapBiome758To490(int id758) {
        return mapBiome758To498(id758);
    }

    /** 490のバイオームID → 758のバイオームID（498 と同一の表）。 */
    public static int mapBiome490To758(int id490) {
        return mapBiome498To758(id490);
    }

    /** 490 のバイオーム名（レジストリ順。498 と同一）。Bedrock 側が次元情報に使う。 */
    public static java.util.List<String> biomeNames490() {
        return biomeNames498();
    }

    /** 758のダメージ種別ID → 490のダメージ種別ID（498 と同一の表。恒等写像）。 */
    public static int mapDamageType758To490(int id758) {
        return mapDamageType758To498(id758);
    }

    /** 490のダメージ種別ID → 758のダメージ種別ID（498 と同一の表。恒等写像）。 */
    public static int mapDamageType490To758(int id490) {
        return mapDamageType498To758(id490);
    }

    /** 758の音の名前 → 490の音の名前（498 と同一の表。Stop Sound 用）。 */
    public static String mapSoundName758To490(String name) {
        return mapSoundName758To498(name);
    }

    /** 490の音の名前 → 758の音の名前（498 と同一の表。Stop Sound 用）。 */
    public static String mapSoundName490To758(String name) {
        return mapSoundName498To758(name);
    }
"""


def main():
    text = P.read_text(encoding="utf-8")
    if "PROTOCOL_490" in text:
        print("skip（既存）: 490 はすでにある")
        return 0

    # 1. PROTOCOL_490 定数。
    text = rep(text,
               "    /** 498（1.14.4）のプロトコル番号。 */\n"
               "    public static final int PROTOCOL_498 = 498;",
               "    /** 490（1.14.3）のプロトコル番号。 */\n"
               "    public static final int PROTOCOL_490 = 490;\n"
               "    /** 498（1.14.4）のプロトコル番号。 */\n"
               "    public static final int PROTOCOL_498 = 498;")

    # 2. MAX_*_490 定数群（498 の代替先定数群の直後）。
    anchor = "    public static final int GENERIC_DAMAGE_TYPE_498 = 16;"
    text = rep(text, anchor, anchor + "\n" + CONSTANTS)

    # 3. メソッド群（498 セクションの直後、760⇔759 セクションの前）。
    anchor = "    // 760 ⇔ 759（1.19.1／1.19.2 ⇔ 1.19）"
    text = rep(text, anchor, METHODS.strip("\n") + "\n" + anchor)

    P.write_text(text, encoding="utf-8")
    print("RegistryRemapper.java を更新した")
    return 0


if __name__ == "__main__":
    sys.exit(main())
