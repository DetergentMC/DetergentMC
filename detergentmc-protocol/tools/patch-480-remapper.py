#!/usr/bin/env python3
"""RegistryRemapper.java に 480（1.14.1）の定数・委譲メソッドを追加する。

480 のレジストリは 485 と完全一致する（実バニラ 1.14.1／1.14.2 両 jar の
data-generator ダンプで照合済み：blocks.json・commands.json はバイト単位で
同一、registries.json は全36カテゴリが件数・順序まで一致）。そのため
480⇔758 の写像は 485⇔758 の表へ委譲する。485 が 490 へ委譲したのと
同じ流儀。

使い方: python3 tools/patch-480-remapper.py
冪等：480 が既にあれば何もしない。
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
     * 480（1.14.1）側の最大の番号と代替先。480 のレジストリは 485 と
     * 完全一致するため（両 jar の data-generator ダンプで全カテゴリ
     * 照合済み）、480 の定数は 485 と同値で、480⇔758 の写像も
     * 485⇔758 の表へ委譲する。将来 480 と 485 の差が見つかった場合は
     * ここに専用の表を持たせる。
     */
    public static final int MAX_BLOCK_STATE_480 = MAX_BLOCK_STATE_485;
    public static final int MAX_ITEM_480 = MAX_ITEM_485;
    public static final int MAX_ENTITY_TYPE_480 = MAX_ENTITY_TYPE_485;
    public static final int MAX_SOUND_480 = MAX_SOUND_485;
    public static final int MAX_PARTICLE_480 = MAX_PARTICLE_485;
    public static final int MAX_BLOCK_480 = MAX_BLOCK_485;
    public static final int MAX_MENU_480 = MAX_MENU_485;
    public static final int MAX_ENCHANTMENT_480 = MAX_ENCHANTMENT_485;
    public static final int MAX_BIOME_480 = MAX_BIOME_485;
    public static final int MAX_DAMAGE_TYPE_480 = MAX_DAMAGE_TYPE_485;
    public static final int MAX_BLOCK_ENTITY_480 = MAX_BLOCK_ENTITY_485;
    public static final int MAX_PAINTING_VARIANT_480 = MAX_PAINTING_VARIANT_485;
    public static final int STONE_BLOCK_STATE_480 = STONE_BLOCK_STATE_485;
    public static final int AIR_BLOCK_STATE_480 = AIR_BLOCK_STATE_485;
    public static final int STONE_ITEM_480 = STONE_ITEM_485;
    public static final int PIG_ENTITY_TYPE_480 = PIG_ENTITY_TYPE_485;
    public static final int GENERIC_SOUND_480 = GENERIC_SOUND_485;
    public static final int AMBIENT_PARTICLE_480 = AMBIENT_PARTICLE_485;
    public static final int STONE_BLOCK_480 = STONE_BLOCK_485;
    public static final int CRAFTING_MENU_480 = CRAFTING_MENU_485;
    public static final int PLAINS_BIOME_480 = PLAINS_BIOME_485;
    public static final int GENERIC_DAMAGE_TYPE_480 = GENERIC_DAMAGE_TYPE_485;
"""

METHODS = """
    // 480 ⇔ 758（1.14.1 ⇔ 1.18.2）
    // 480 のレジストリは 485 と完全一致するため（両 jar の data-generator
    // ダンプで全カテゴリ照合済み）、480⇔758 の写像は 485⇔758 の表へ
    // 委譲する。将来 480 と 485 の差が見つかった場合はここに専用の表を
    // 持たせる。

    /** 758のブロック状態ID → 480のブロック状態ID（485 と同一の表）。 */
    public static int mapBlockState758To480(int id758) {
        return mapBlockState758To485(id758);
    }

    /** 480のブロック状態ID → 758のブロック状態ID（485 と同一の表）。 */
    public static int mapBlockState480To758(int id480) {
        return mapBlockState485To758(id480);
    }

    /** 758のアイテムID → 480のアイテムID（485 と同一の表）。 */
    public static int mapItem758To480(int id758) {
        return mapItem758To485(id758);
    }

    /** 480のアイテムID → 758のアイテムID（485 と同一の表）。 */
    public static int mapItem480To758(int id480) {
        return mapItem485To758(id480);
    }

    /** 758のエンティティ種別ID → 480のエンティティ種別ID（485 と同一の表）。 */
    public static int mapEntityType758To480(int id758) {
        return mapEntityType758To485(id758);
    }

    /** 480のエンティティ種別ID → 758のエンティティ種別ID（485 と同一の表）。 */
    public static int mapEntityType480To758(int id480) {
        return mapEntityType485To758(id480);
    }

    /** 758の音ID → 480の音ID（485 と同一の表）。 */
    public static int mapSound758To480(int id758) {
        return mapSound758To485(id758);
    }

    /** 480の音ID → 758の音ID（485 と同一の表）。 */
    public static int mapSound480To758(int id480) {
        return mapSound485To758(id480);
    }

    /** 758のパーティクルID → 480のパーティクルID（485 と同一の表）。 */
    public static int mapParticle758To480(int id758) {
        return mapParticle758To485(id758);
    }

    /** 480のパーティクルID → 758のパーティクルID（485 と同一の表）。 */
    public static int mapParticle480To758(int id480) {
        return mapParticle485To758(id480);
    }

    /** 758のブロックID → 480のブロックID（485 と同一の表）。 */
    public static int mapBlock758To480(int id758) {
        return mapBlock758To485(id758);
    }

    /** 480のブロックID → 758のブロックID（485 と同一の表）。 */
    public static int mapBlock480To758(int id480) {
        return mapBlock485To758(id480);
    }

    /** 758のメニュー種別ID → 480のメニュー種別ID（485 と同一の表）。 */
    public static int mapMenu758To480(int id758) {
        return mapMenu758To485(id758);
    }

    /** 480のメニュー種別ID → 758のメニュー種別ID（485 と同一の表）。 */
    public static int mapMenu480To758(int id480) {
        return mapMenu485To758(id480);
    }

    /** 758のブロックエンティティ種別ID → 480のブロックエンティティ種別ID（485 と同一の表）。 */
    public static int mapBlockEntityType758To480(int id758) {
        return mapBlockEntityType758To485(id758);
    }

    /** 480のブロックエンティティ種別ID → 758のブロックエンティティ種別ID（485 と同一の表）。 */
    public static int mapBlockEntityType480To758(int id480) {
        return mapBlockEntityType485To758(id480);
    }

    /** 758のバイオームID → 480のバイオームID（485 と同一の表）。 */
    public static int mapBiome758To480(int id758) {
        return mapBiome758To485(id758);
    }

    /** 480のバイオームID → 758のバイオームID（485 と同一の表）。 */
    public static int mapBiome480To758(int id480) {
        return mapBiome485To758(id480);
    }

    /** 480 のバイオーム名（レジストリ順。485 と同一）。Bedrock 側が次元情報に使う。 */
    public static java.util.List<String> biomeNames480() {
        return biomeNames485();
    }

    /** 758のダメージ種別ID → 480のダメージ種別ID（485 と同一の表。恒等写像）。 */
    public static int mapDamageType758To480(int id758) {
        return mapDamageType758To485(id758);
    }

    /** 480のダメージ種別ID → 758のダメージ種別ID（485 と同一の表。恒等写像）。 */
    public static int mapDamageType480To758(int id480) {
        return mapDamageType485To758(id480);
    }

    /** 758の音の名前 → 480の音の名前（485 と同一の表。Stop Sound 用）。 */
    public static String mapSoundName758To480(String name) {
        return mapSoundName758To485(name);
    }

    /** 480の音の名前 → 758の音の名前（485 と同一の表。Stop Sound 用）。 */
    public static String mapSoundName480To758(String name) {
        return mapSoundName485To758(name);
    }
"""


def main():
    text = P.read_text(encoding="utf-8")
    if "PROTOCOL_480" in text:
        print("skip（既存）: 480 はすでにある")
        return 0

    # 1. PROTOCOL_480 定数。
    text = rep(text,
               "    /** 485（1.14.2）のプロトコル番号。 */\n"
               "    public static final int PROTOCOL_485 = 485;",
               "    /** 480（1.14.1）のプロトコル番号。 */\n"
               "    public static final int PROTOCOL_480 = 480;\n"
               "    /** 485（1.14.2）のプロトコル番号。 */\n"
               "    public static final int PROTOCOL_485 = 485;")

    # 2. MAX_*_480 定数群（485 の定数群の直後）。
    anchor = "    public static final int GENERIC_DAMAGE_TYPE_485 = GENERIC_DAMAGE_TYPE_490;"
    text = rep(text, anchor, anchor + "\n" + CONSTANTS)

    # 3. メソッド群（485 セクションの直前）。
    anchor = "    // 485 ⇔ 758（1.14.2 ⇔ 1.18.2）"
    text = rep(text, anchor, METHODS.strip("\n") + "\n" + anchor)

    P.write_text(text, encoding="utf-8")
    print("RegistryRemapper.java を更新した")
    return 0


if __name__ == "__main__":
    sys.exit(main())
