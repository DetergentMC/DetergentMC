#!/usr/bin/env python3
"""RegistryRemapper.java に 477（1.14）の定数・委譲メソッドを追加する。

477 のレジストリは 480 とワイヤ上一致する（実バニラ 1.14／1.14.1 両 jar
の data-generator ダンプで照合済み：blocks.json・commands.json は
バイト単位で同一、registries.json の唯一の差分は変換表に現れない
memory_module_type の1件だけ）。そのため 477⇔758 の写像は
480⇔758 の表へ委譲する。480 が 485 へ委譲したのと同じ流儀。

使い方: python3 tools/patch-477-remapper.py
冪等：477 が既にあれば何もしない。
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
     * 477（1.14）側の最大の番号と代替先。477 のレジストリは 480 と
     * ワイヤ上一致するため（両 jar の data-generator ダンプで照合済み：
     * blocks.json・commands.json はバイト一致、registries.json の差分は
     * 変換表に現れない memory_module_type の1件だけ）、477 の定数は
     * 480 と同値で、477⇔758 の写像も 480⇔758 の表へ委譲する。
     * 将来 477 と 480 の差が見つかった場合はここに専用の表を持たせる。
     */
    public static final int MAX_BLOCK_STATE_477 = MAX_BLOCK_STATE_480;
    public static final int MAX_ITEM_477 = MAX_ITEM_480;
    public static final int MAX_ENTITY_TYPE_477 = MAX_ENTITY_TYPE_480;
    public static final int MAX_SOUND_477 = MAX_SOUND_480;
    public static final int MAX_PARTICLE_477 = MAX_PARTICLE_480;
    public static final int MAX_BLOCK_477 = MAX_BLOCK_480;
    public static final int MAX_MENU_477 = MAX_MENU_480;
    public static final int MAX_ENCHANTMENT_477 = MAX_ENCHANTMENT_480;
    public static final int MAX_BIOME_477 = MAX_BIOME_480;
    public static final int MAX_DAMAGE_TYPE_477 = MAX_DAMAGE_TYPE_480;
    public static final int MAX_BLOCK_ENTITY_477 = MAX_BLOCK_ENTITY_480;
    public static final int MAX_PAINTING_VARIANT_477 = MAX_PAINTING_VARIANT_480;
    public static final int STONE_BLOCK_STATE_477 = STONE_BLOCK_STATE_480;
    public static final int AIR_BLOCK_STATE_477 = AIR_BLOCK_STATE_480;
    public static final int STONE_ITEM_477 = STONE_ITEM_480;
    public static final int PIG_ENTITY_TYPE_477 = PIG_ENTITY_TYPE_480;
    public static final int GENERIC_SOUND_477 = GENERIC_SOUND_480;
    public static final int AMBIENT_PARTICLE_477 = AMBIENT_PARTICLE_480;
    public static final int STONE_BLOCK_477 = STONE_BLOCK_480;
    public static final int CRAFTING_MENU_477 = CRAFTING_MENU_480;
    public static final int PLAINS_BIOME_477 = PLAINS_BIOME_480;
    public static final int GENERIC_DAMAGE_TYPE_477 = GENERIC_DAMAGE_TYPE_480;
"""

METHODS = """
    // 477 ⇔ 758（1.14 ⇔ 1.18.2）
    // 477 のレジストリは 480 とワイヤ上一致するため（両 jar の
    // data-generator ダンプで照合済み）、477⇔758 の写像は 480⇔758 の
    // 表へ委譲する。将来 477 と 480 の差が見つかった場合はここに専用の
    // 表を持たせる。

    /** 758のブロック状態ID → 477のブロック状態ID（480 と同一の表）。 */
    public static int mapBlockState758To477(int id758) {
        return mapBlockState758To480(id758);
    }

    /** 477のブロック状態ID → 758のブロック状態ID（480 と同一の表）。 */
    public static int mapBlockState477To758(int id477) {
        return mapBlockState480To758(id477);
    }

    /** 758のアイテムID → 477のアイテムID（480 と同一の表）。 */
    public static int mapItem758To477(int id758) {
        return mapItem758To480(id758);
    }

    /** 477のアイテムID → 758のアイテムID（480 と同一の表）。 */
    public static int mapItem477To758(int id477) {
        return mapItem480To758(id477);
    }

    /** 758のエンティティ種別ID → 477のエンティティ種別ID（480 と同一の表）。 */
    public static int mapEntityType758To477(int id758) {
        return mapEntityType758To480(id758);
    }

    /** 477のエンティティ種別ID → 758のエンティティ種別ID（480 と同一の表）。 */
    public static int mapEntityType477To758(int id477) {
        return mapEntityType480To758(id477);
    }

    /** 758の音ID → 477の音ID（480 と同一の表）。 */
    public static int mapSound758To477(int id758) {
        return mapSound758To480(id758);
    }

    /** 477の音ID → 758の音ID（480 と同一の表）。 */
    public static int mapSound477To758(int id477) {
        return mapSound480To758(id477);
    }

    /** 758のパーティクルID → 477のパーティクルID（480 と同一の表）。 */
    public static int mapParticle758To477(int id758) {
        return mapParticle758To480(id758);
    }

    /** 477のパーティクルID → 758のパーティクルID（480 と同一の表）。 */
    public static int mapParticle477To758(int id477) {
        return mapParticle480To758(id477);
    }

    /** 758のブロックID → 477のブロックID（480 と同一の表）。 */
    public static int mapBlock758To477(int id758) {
        return mapBlock758To480(id758);
    }

    /** 477のブロックID → 758のブロックID（480 と同一の表）。 */
    public static int mapBlock477To758(int id477) {
        return mapBlock480To758(id477);
    }

    /** 758のメニュー種別ID → 477のメニュー種別ID（480 と同一の表）。 */
    public static int mapMenu758To477(int id758) {
        return mapMenu758To480(id758);
    }

    /** 477のメニュー種別ID → 758のメニュー種別ID（480 と同一の表）。 */
    public static int mapMenu477To758(int id477) {
        return mapMenu480To758(id477);
    }

    /** 758のブロックエンティティ種別ID → 477のブロックエンティティ種別ID（480 と同一の表）。 */
    public static int mapBlockEntityType758To477(int id758) {
        return mapBlockEntityType758To480(id758);
    }

    /** 477のブロックエンティティ種別ID → 758のブロックエンティティ種別ID（480 と同一の表）。 */
    public static int mapBlockEntityType477To758(int id477) {
        return mapBlockEntityType480To758(id477);
    }

    /** 758のバイオームID → 477のバイオームID（480 と同一の表）。 */
    public static int mapBiome758To477(int id758) {
        return mapBiome758To480(id758);
    }

    /** 477のバイオームID → 758のバイオームID（480 と同一の表）。 */
    public static int mapBiome477To758(int id477) {
        return mapBiome480To758(id477);
    }

    /** 477 のバイオーム名（レジストリ順。480 と同一）。Bedrock 側が次元情報に使う。 */
    public static java.util.List<String> biomeNames477() {
        return biomeNames480();
    }

    /** 758のダメージ種別ID → 477のダメージ種別ID（480 と同一の表。恒等写像）。 */
    public static int mapDamageType758To477(int id758) {
        return mapDamageType758To480(id758);
    }

    /** 477のダメージ種別ID → 758のダメージ種別ID（480 と同一の表。恒等写像）。 */
    public static int mapDamageType477To758(int id477) {
        return mapDamageType480To758(id477);
    }

    /** 758の音の名前 → 477の音の名前（480 と同一の表。Stop Sound 用）。 */
    public static String mapSoundName758To477(String name) {
        return mapSoundName758To480(name);
    }

    /** 477の音の名前 → 758の音の名前（480 と同一の表。Stop Sound 用）。 */
    public static String mapSoundName477To758(String name) {
        return mapSoundName480To758(name);
    }
"""


def main():
    text = P.read_text(encoding="utf-8")
    if "PROTOCOL_477" in text:
        print("skip（既存）: 477 はすでにある")
        return 0

    # 1. PROTOCOL_477 定数。
    text = rep(text,
               "    /** 480（1.14.1）のプロトコル番号。 */\n"
               "    public static final int PROTOCOL_480 = 480;",
               "    /** 477（1.14）のプロトコル番号。 */\n"
               "    public static final int PROTOCOL_477 = 477;\n"
               "    /** 480（1.14.1）のプロトコル番号。 */\n"
               "    public static final int PROTOCOL_480 = 480;")

    # 2. MAX_*_477 定数群（480 の定数群の直後）。
    anchor = "    public static final int GENERIC_DAMAGE_TYPE_480 = GENERIC_DAMAGE_TYPE_485;"
    text = rep(text, anchor, anchor + "\n" + CONSTANTS)

    # 3. メソッド群（480 セクションの直前）。
    anchor = "    // 480 ⇔ 758（1.14.1 ⇔ 1.18.2）"
    text = rep(text, anchor, METHODS.strip("\n") + "\n" + anchor)

    P.write_text(text, encoding="utf-8")
    print("RegistryRemapper.java を更新した")
    return 0


if __name__ == "__main__":
    sys.exit(main())
