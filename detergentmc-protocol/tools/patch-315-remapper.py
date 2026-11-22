"""RegistryRemapper.java に 315（1.11）の定数・対応表・変換メソッドを足す。

315 のレジストリは 316 と同一（Stage A 実測：ブロック236種・音493件・
エンティティ登録81件が実 jar 抽出で完全一致）ため、対応表は 316 の
配列をそのまま使う。唯一の差分はアイテム：iron_nugget（数値ID 452）は
316（1.11.1）で追加されたため 315 には無く、758→315 の写像でのみ
金塊（371）へ寄せる（ITEM_758_TO_315_MAP を 316 の表の複製＋
452→371 の上書きで構築する。現行の 316 の表も iron_nugget を金塊へ
寄せているため結果は同値だが、315 側の規則として独立に固定する）。
315→758 は 316 の表と同一（315 のアイテム集合は 316 の部分集合で、
番号の振り直しは無い。totem→totem_of_undying の改名は数値ID 449
不変のためワイヤ無関係）。

使い方：
  python3 tools/patch-315-remapper.py
  冪等（315 の節が既にあれば何もしない）。
"""

import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
FILE = (ROOT / "src" / "main" / "java" / "io" / "detergentmc"
        / "protocol" / "RegistryRemapper.java")
DONE = "PROTOCOL_315 = 315"


def insert_after(text, anchor, addition):
    if anchor not in text:
        raise SystemExit(f"アンカーが見つからない: {anchor[:60]}")
    return text.replace(anchor, anchor + addition, 1)


def main():
    text = FILE.read_text(encoding="utf-8")
    if DONE in text:
        print("315 の節は既にある。何もしない。")
        return 0

    # 1) プロトコル定数
    text = insert_after(
        text,
        """    /** 316（1.11.2）のプロトコル番号。 */
    public static final int PROTOCOL_316 = 316;
""",
        """
    /** 315（1.11）のプロトコル番号。 */
    public static final int PROTOCOL_315 = 315;
""")

    # 2) 最大値・代表値の定数（値は 316 と同一）
    text = insert_after(
        text,
        """    /** 316側の generic ダメージ種別（340 と同一）。 */
    public static final int GENERIC_DAMAGE_TYPE_316 = 16;
""",
        """
    /** 315側のブロック状態最大値（316 と同一）。 */
    public static final int MAX_BLOCK_STATE_315 = 4095;
    /** 315側のアイテム最大ID（316 と同一。最大ID 2267 の疎な番号）。 */
    public static final int MAX_ITEM_315 = 2267;
    /** 315側のエンティティ種別最大ID（316 と同一）。 */
    public static final int MAX_ENTITY_TYPE_315 = 200;
    /** 315側の音最大ID（316 と同一）。 */
    public static final int MAX_SOUND_315 = 492;
    /** 315側のパーティクル最大ID（316 と同一）。 */
    public static final int MAX_PARTICLE_315 = 48;
    /** 315側のブロック最大ID（316 と同一）。 */
    public static final int MAX_BLOCK_315 = 255;
    /** 315側のバイオーム最大ID（316 と同一）。 */
    public static final int MAX_BIOME_315 = 167;
    /** 315側のダメージ種別最大ID（316 と同一）。 */
    public static final int MAX_DAMAGE_TYPE_315 = 41;
    /** 315側のブロックエンティティ種別最大ID（316 と同一。bed は無い）。 */
    public static final int MAX_BLOCK_ENTITY_315 = 22;
    /** 315側の状態異常最大ID（316 と同一）。 */
    public static final int MAX_EFFECT_315 = 27;
    /** 315側の石ブロック状態（316 と同一）。 */
    public static final int STONE_BLOCK_STATE_315 = 16; // 石 (1<<4)|0
    /** 315側の空気ブロック状態（316 と同一）。 */
    public static final int AIR_BLOCK_STATE_315 = 0;
    /** 315側の石アイテムID（316 と同一）。 */
    public static final int STONE_ITEM_315 = 1;
    /** 315側のブタのエンティティ種別ID（316 と同一）。 */
    public static final int PIG_ENTITY_TYPE_315 = 90;
    /** 315側の汎用音ID（316 と同一。block.stone.break）。 */
    public static final int GENERIC_SOUND_315 = 397; // block.stone.break
    /** 315側の ambient パーティクル代替（316 と同じ explode）。 */
    public static final int AMBIENT_PARTICLE_315 = 0; // explode
    /** 315側の石ブロックID（316 と同一）。 */
    public static final int STONE_BLOCK_315 = 1;
    /** 315側の平原バイオームID（316 と同一）。 */
    public static final int PLAINS_BIOME_315 = 1;
    /** 315側の generic ダメージ種別（316 と同一）。 */
    public static final int GENERIC_DAMAGE_TYPE_315 = 16;
""")

    # 3) 対応表（316 の表への別名＋アイテムだけ専用構築）
    anchor_maps = """    /** 316のダメージ種別ID → 758のダメージ種別ID（340 と同じ表。codec には無い）。 */
    private static final int[] DAMAGE_TYPE_316_TO_758_MAP =
            expand(DAMAGE_TYPE_316_TO_758_RANGES, DAMAGE_TYPE_316_TO_758_OVERRIDES,
                    MAX_DAMAGE_TYPE_316 + 1, GENERIC_DAMAGE_TYPE_758);
"""
    text = insert_after(
        text, anchor_maps,
        """    // ---- 315（1.11）：316 と同一の表を使う。差分はアイテム1件だけ ----
    /** 758のブロック状態ID → 315の (数値ID<<4)|メタデータ（316 と同一の表）。 */
    private static final int[] BLOCK_STATE_758_TO_315_MAP = BLOCK_STATE_758_TO_316_MAP;
    /** 315の (数値ID<<4)|メタデータ → 758のブロック状態ID（316 と同一の表）。 */
    private static final int[] BLOCK_STATE_315_TO_758_MAP = BLOCK_STATE_316_TO_758_MAP;
    /** 758のアイテムID → 315のアイテムID（iron_nugget だけ金塊へ寄せる）。 */
    private static final int[] ITEM_758_TO_315_MAP = buildItem758To315Map();
    /** 315のアイテムID → 758のアイテムID（316 と同一の表）。 */
    private static final int[] ITEM_315_TO_758_MAP = ITEM_316_TO_758_MAP;
    /** 758のエンティティ種別ID → 315のエンティティ種別ID（316 と同一の表）。 */
    private static final int[] ENTITY_TYPE_758_TO_315_MAP = ENTITY_TYPE_758_TO_316_MAP;
    /** 315のエンティティ種別ID → 758のエンティティ種別ID（316 と同一の表）。 */
    private static final int[] ENTITY_TYPE_315_TO_758_MAP = ENTITY_TYPE_316_TO_758_MAP;
    /** 758の音ID → 315の音ID（316 と同一の表）。 */
    private static final int[] SOUND_758_TO_315_MAP = SOUND_758_TO_316_MAP;
    /** 315の音ID → 758の音ID（316 と同一の表）。 */
    private static final int[] SOUND_315_TO_758_MAP = SOUND_316_TO_758_MAP;
    /** 758のパーティクルID → 315のパーティクルID（316 と同一の表）。 */
    private static final int[] PARTICLE_758_TO_315_MAP = PARTICLE_758_TO_316_MAP;
    /** 315のパーティクルID → 758のパーティクルID（316 と同一の表）。 */
    private static final int[] PARTICLE_315_TO_758_MAP = PARTICLE_316_TO_758_MAP;
    /** 758のブロックID → 315のブロック数値ID（316 と同一の表）。 */
    private static final int[] BLOCK_758_TO_315_MAP = BLOCK_758_TO_316_MAP;
    /** 315のブロック数値ID → 758のブロックID（316 と同一の表）。 */
    private static final int[] BLOCK_315_TO_758_MAP = BLOCK_316_TO_758_MAP;
    /** 758のブロックエンティティ種別ID → 315の種別ID（316 と同一の表）。 */
    private static final int[] BLOCK_ENTITY_758_TO_315_MAP = BLOCK_ENTITY_758_TO_316_MAP;
    /** 315のブロックエンティティ種別ID → 758の種別ID（316 と同一の表）。 */
    private static final int[] BLOCK_ENTITY_315_TO_758_MAP = BLOCK_ENTITY_316_TO_758_MAP;
    /** 758のバイオームID → 315のバイオームID（316 と同一の表）。 */
    private static final int[] BIOME_758_TO_315_MAP = BIOME_758_TO_316_MAP;
    /** 315のバイオームID → 758のバイオームID（316 と同一の表）。 */
    private static final int[] BIOME_315_TO_758_MAP = BIOME_316_TO_758_MAP;
    /** 758のダメージ種別ID → 315のダメージ種別ID（316 と同一の表）。 */
    private static final int[] DAMAGE_TYPE_758_TO_315_MAP = DAMAGE_TYPE_758_TO_316_MAP;
    /** 315のダメージ種別ID → 758のダメージ種別ID（316 と同一の表）。 */
    private static final int[] DAMAGE_TYPE_315_TO_758_MAP = DAMAGE_TYPE_316_TO_758_MAP;

    /**
     * 758→315 のアイテム表を作る。316 の表を土台に、値が iron_nugget
     * （452）になるエントリだけ金塊（371）へ寄せる。iron_nugget は
     * 316（1.11.1）で追加されたアイテムで 315 には存在しない（451 は
     * 欠番）。現行の 316 の表も iron_nugget を金塊へ寄せているため
     * 通常は同値になるが、315 側の規則として独立に固定する。
     */
    private static int[] buildItem758To315Map() {
        int[] map = ITEM_758_TO_316_MAP.clone();
        for (int i = 0; i < map.length; i++) {
            if (map[i] == 452) {
                map[i] = 371; // gold_nugget（315 に iron_nugget は無い）
            }
        }
        return map;
    }
""")

    # 4) 変換メソッド（316 の節の直後）
    anchor_methods = """    /** 316の音の名前 → 758の音の名前（Stop Sound 用）。数値の合成表と一致させる。 */
    public static String mapSoundName316To758(String name) {
        return mapSoundNameComposed(name, SOUND_NAME_FALLBACK_316_TO_758_MAP);
    }
"""
    text = insert_after(
        text, anchor_methods,
        """
    // 315 ⇔ 758（1.11 ⇔ 1.18.2）
    // 315 のレジストリは 316 と同一（Stage A 実測）ため、アイテム以外は
    // 316 の変換へ委譲する。アイテムだけは iron_nugget（452、316 で
    // 追加）の扱いが違うため専用の表を使う。メニューは 316 と同じく
    // レジストリが無いため写像を作らない。

    /** 758のブロック状態ID → 315の (数値ID<<4)|メタデータ。範囲外は石。 */
    public static int mapBlockState758To315(int id758) {
        return mapBlockState758To316(id758);
    }

    /** 315の (数値ID<<4)|メタデータ → 758のブロック状態ID。範囲外は石。 */
    public static int mapBlockState315To758(int value315) {
        return mapBlockState316To758(value315);
    }

    /** 758のアイテムID → 315のアイテムID。範囲外は石アイテム。iron_nugget は金塊（371）へ寄せる。 */
    public static int mapItem758To315(int id758) {
        if (id758 < 0 || id758 >= ITEM_758_TO_315_MAP.length) {
            logUnknownComposedOnce("758→315", "アイテム", id758);
            return STONE_ITEM_315;
        }
        return ITEM_758_TO_315_MAP[id758];
    }

    /** 315のアイテムID → 758のアイテムID。範囲外は石アイテム。 */
    public static int mapItem315To758(int id315) {
        if (id315 < 0 || id315 >= ITEM_315_TO_758_MAP.length) {
            logUnknownComposedOnce("315→758", "アイテム", id315);
            return STONE_ITEM_758;
        }
        return ITEM_315_TO_758_MAP[id315];
    }

    /** 758のエンティティ種別ID → 315のエンティティ種別ID。範囲外はブタ。 */
    public static int mapEntityType758To315(int id758) {
        return mapEntityType758To316(id758);
    }

    /** 315のエンティティ種別ID → 758のエンティティ種別ID。範囲外はブタ。 */
    public static int mapEntityType315To758(int id315) {
        return mapEntityType316To758(id315);
    }

    /** 758の音ID → 315の音ID。範囲外は汎用音。 */
    public static int mapSound758To315(int id758) {
        return mapSound758To316(id758);
    }

    /** 315の音ID → 758の音ID。範囲外は汎用音。 */
    public static int mapSound315To758(int id315) {
        return mapSound316To758(id315);
    }

    /** 758のパーティクルID → 315のパーティクルID。範囲外は explode。 */
    public static int mapParticle758To315(int id758) {
        return mapParticle758To316(id758);
    }

    /** 315のパーティクルID → 758のパーティクルID。範囲外は ambient 代替。 */
    public static int mapParticle315To758(int id315) {
        return mapParticle316To758(id315);
    }

    /** 758のブロックID → 315のブロック数値ID。範囲外は石。 */
    public static int mapBlock758To315(int id758) {
        return mapBlock758To316(id758);
    }

    /** 315のブロック数値ID → 758のブロックID。範囲外は石。 */
    public static int mapBlock315To758(int id315) {
        return mapBlock316To758(id315);
    }

    /** 758のブロックエンティティ種別ID → 315の種別ID。無い種別は DROP（-1）。 */
    public static int mapBlockEntityType758To315(int id758) {
        return mapBlockEntityType758To316(id758);
    }

    /** 315のブロックエンティティ種別ID → 758の種別ID。範囲外は DROP（-1）。 */
    public static int mapBlockEntityType315To758(int id315) {
        return mapBlockEntityType316To758(id315);
    }

    /** 758のバイオームID → 315のバイオームID。範囲外は平原。 */
    public static int mapBiome758To315(int id758) {
        return mapBiome758To316(id758);
    }

    /** 315のバイオームID → 758のバイオームID。範囲外は平原。 */
    public static int mapBiome315To758(int id315) {
        return mapBiome316To758(id315);
    }

    /** 315 のバイオーム名（316 と同一）。 */
    public static java.util.List<String> biomeNames315() {
        return biomeNames316();
    }

    /** 758のダメージ種別ID → 315のダメージ種別ID。 */
    public static int mapDamageType758To315(int id758) {
        return mapDamageType758To316(id758);
    }

    /** 315のダメージ種別ID → 758のダメージ種別ID。 */
    public static int mapDamageType315To758(int id315) {
        return mapDamageType316To758(id315);
    }

    /** 758の音の名前 → 315の音の名前（Stop Sound 用）。316 と同一。 */
    public static String mapSoundName758To315(String name) {
        return mapSoundName758To316(name);
    }

    /** 315の音の名前 → 758の音の名前（Stop Sound 用）。316 と同一。 */
    public static String mapSoundName315To758(String name) {
        return mapSoundName316To758(name);
    }
""")

    FILE.write_text(text, encoding="utf-8")
    print("RegistryRemapper に 315 の定数・対応表・変換メソッドを追加した。")
    return 0


if __name__ == "__main__":
    sys.exit(main())
