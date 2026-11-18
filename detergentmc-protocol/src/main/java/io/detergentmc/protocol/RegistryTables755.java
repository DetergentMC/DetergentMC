package io.detergentmc.protocol;

/**
 * 755（1.17）のレジストリ対応表（区間＋上書きの生の表）。
 *
 * <p>756（1.17.1）との隣接表だけを置く。755 は最古バージョンのため
 * 離れた組み合わせの合成表は作らない（763〜775 の組が合成表を持つ
 * のとは違い、759〜762 の組は隣接表の連鎖だけを使う設計のため。
 * 755↔763〜775 は 755→756→759→760→761→762→763→合成表の連鎖で到達する）。
 * 756→755 の順向き表（*_755_*）と 755→756 の反転表（*_755_TO_756_*）
 * を両方置く。反転表は名前の真値（同名のものを優先）で作ってあり、
 * {@link RegistryRemapper#invert} の first-wins と違う箇所は
 * tools/maps755-corrections.json の補正（UP_755_CORRECTIONS）で
 * 埋める（補正は 0 件。1.17 と 1.17.1 のレジストリは同一のため）。
 *
 * <p>バイオームの id は 1.17 と 1.17.1 がどちらも 0〜175 の非連番
 * （旧来の数値バイオームID）。順向き表（BIOME_755）は 756 側 81 件、反転表
 * （BIOME_755_TO_756）は 755 側 176 件（id 0〜175。隙間の id は
 * 平原（756 id 1）で埋める）。ワイヤ上の id がこの通りであることは
 * Join Game の dimensionCodec との突き合わせで確認する
 * （tools/gen-755-tables.py の §11.6 相当の Join Game codec 確認を参照。
 * 756 では実バニラ 1.17.1 の生キャプチャで確認済み）。
 *
 * <p>{@link RegistryRemapper} から分離した。理由はバイトコードの制限：
 * 755 の表を RegistryRemapper に置くと静的初期化子（{@code <clinit>}）
 * が 64KB を超えてコンパイルできなくなる（"code too large"）ため。
 * このクラスの表は {@link RegistryRemapper} から static import で
 * 同じ名前のまま参照する（展開済み表 {@code *_755_MAP} やアクセサの
 * 書き方は変わらない）。
 *
 * <p>表の内容と検証は {@code detergentmc-protocol/tools/} を参照：
 * 生成は {@code gen-755-tables.py}、検証は {@code verify-755-tables.py}。
 */
final class RegistryTables755 {

    private RegistryTables755() {
    }

    static final int[][] BLOCK_STATE_755_RANGES = {
            {0, 20341, 0},
    };

    static final int[][] BLOCK_STATE_755_OVERRIDES = {
    };
    // BLOCK_STATE_755: 756 entries=20342 ranges=1 overrides=0

    static final int[][] ITEM_755_RANGES = {
            {0, 1099, 0},
    };

    static final int[][] ITEM_755_OVERRIDES = {
    };
    // ITEM_755: 756 entries=1100 ranges=1 overrides=0

    static final int[][] ENTITY_TYPE_755_RANGES = {
            {0, 112, 0},
    };

    static final int[][] ENTITY_TYPE_755_OVERRIDES = {
    };
    // ENTITY_TYPE_755: 756 entries=113 ranges=1 overrides=0

    static final int[][] SOUND_755_RANGES = {
            {0, 1189, 0},
    };

    static final int[][] SOUND_755_OVERRIDES = {
    };
    // SOUND_755: 756 entries=1190 ranges=1 overrides=0

    static final int[][] PARTICLE_755_RANGES = {
            {0, 88, 0},
    };

    static final int[][] PARTICLE_755_OVERRIDES = {
    };
    // PARTICLE_755: 756 entries=89 ranges=1 overrides=0

    static final int[][] BLOCK_755_RANGES = {
            {0, 897, 0},
    };

    static final int[][] BLOCK_755_OVERRIDES = {
    };
    // BLOCK_755: 756 entries=898 ranges=1 overrides=0

    static final int[][] MENU_755_RANGES = {
            {0, 23, 0},
    };

    static final int[][] MENU_755_OVERRIDES = {
    };
    // MENU_755: 756 entries=24 ranges=1 overrides=0

    static final int[][] ITEM_REVERSE_755_RANGES = {
            {0, 1099, 0},
    };

    static final int[][] ITEM_REVERSE_755_OVERRIDES = {
    };
    // ITEM_REVERSE_755: 755 entries=1100 ranges=1 overrides=0

    static final int[][] BLOCK_STATE_755_TO_756_RANGES = {
            {0, 20341, 0},
    };

    static final int[][] BLOCK_STATE_755_TO_756_OVERRIDES = {
    };
    // BLOCK_STATE_755_TO_756: 755 entries=20342 ranges=1 overrides=0

    static final int[][] ITEM_755_TO_756_RANGES = {
            {0, 1099, 0},
    };

    static final int[][] ITEM_755_TO_756_OVERRIDES = {
    };
    // ITEM_755_TO_756: 755 entries=1100 ranges=1 overrides=0

    static final int[][] ENTITY_TYPE_755_TO_756_RANGES = {
            {0, 112, 0},
    };

    static final int[][] ENTITY_TYPE_755_TO_756_OVERRIDES = {
    };
    // ENTITY_TYPE_755_TO_756: 755 entries=113 ranges=1 overrides=0

    static final int[][] SOUND_755_TO_756_RANGES = {
            {0, 1189, 0},
    };

    static final int[][] SOUND_755_TO_756_OVERRIDES = {
    };
    // SOUND_755_TO_756: 755 entries=1190 ranges=1 overrides=0

    static final int[][] PARTICLE_755_TO_756_RANGES = {
            {0, 88, 0},
    };

    static final int[][] PARTICLE_755_TO_756_OVERRIDES = {
    };
    // PARTICLE_755_TO_756: 755 entries=89 ranges=1 overrides=0

    static final int[][] BLOCK_755_TO_756_RANGES = {
            {0, 897, 0},
    };

    static final int[][] BLOCK_755_TO_756_OVERRIDES = {
    };
    // BLOCK_755_TO_756: 755 entries=898 ranges=1 overrides=0

    static final int[][] MENU_755_TO_756_RANGES = {
            {0, 23, 0},
    };

    static final int[][] MENU_755_TO_756_OVERRIDES = {
    };
    // MENU_755_TO_756: 755 entries=24 ranges=1 overrides=0

    static final String[][] SOUND_NAME_FALLBACKS_755 = {
    };

    static final String[][] SOUND_NAME_FALLBACKS_756_TO_755 = {
    };

    static final String[][] SOUND_NAME_FALLBACKS_755_TO_756 = {
    };

    static final int[][] ATTRIBUTE_755_RANGES = {
            {0, 12, 0},
    };

    static final int[][] ATTRIBUTE_755_OVERRIDES = {
    };
    // ATTRIBUTE_755: 756 entries=13 ranges=1 overrides=0

    static final int[][] ATTRIBUTE_REVERSE_755_RANGES = {
            {0, 12, 0},
    };

    static final int[][] ATTRIBUTE_REVERSE_755_OVERRIDES = {
    };
    // ATTRIBUTE_REVERSE_755: 755 entries=13 ranges=1 overrides=0

    static final String[] ATTRIBUTE_755_NAMES = {
            "generic.max_health", "generic.follow_range", "generic.knockback_resistance", "generic.movement_speed",
            "generic.flying_speed", "generic.attack_damage", "generic.attack_knockback", "generic.attack_speed",
            "generic.armor", "generic.armor_toughness", "generic.luck", "zombie.spawn_reinforcements",
            "horse.jump_strength",
    };

    static final int[][] ENCHANTMENT_755_RANGES = {
            {0, 37, 0},
    };

    static final int[][] ENCHANTMENT_755_OVERRIDES = {
    };
    // ENCHANTMENT_755: 756 entries=38 ranges=1 overrides=0

    static final int[][] ENCHANTMENT_REVERSE_755_RANGES = {
            {0, 37, 0},
    };

    static final int[][] ENCHANTMENT_REVERSE_755_OVERRIDES = {
    };
    // ENCHANTMENT_REVERSE_755: 755 entries=38 ranges=1 overrides=0

    static final int[][] BIOME_755_RANGES = {
            {0, 50, 0}, {129, 134, 0}, {155, 158, 0}, {160, 175, 0},
    };

    static final int[][] BIOME_755_OVERRIDES = {
            {51, 1}, {52, 1}, {53, 1}, {54, 1}, {55, 1}, {56, 1}, {57, 1}, {58, 1},
            {59, 1}, {60, 1}, {61, 1}, {62, 1}, {63, 1}, {64, 1}, {65, 1}, {66, 1},
            {67, 1}, {68, 1}, {69, 1}, {70, 1}, {71, 1}, {72, 1}, {73, 1}, {74, 1},
            {75, 1}, {76, 1}, {77, 1}, {78, 1}, {79, 1}, {80, 1}, {81, 1}, {82, 1},
            {83, 1}, {84, 1}, {85, 1}, {86, 1}, {87, 1}, {88, 1}, {89, 1}, {90, 1},
            {91, 1}, {92, 1}, {93, 1}, {94, 1}, {95, 1}, {96, 1}, {97, 1}, {98, 1},
            {99, 1}, {100, 1}, {101, 1}, {102, 1}, {103, 1}, {104, 1}, {105, 1}, {106, 1},
            {107, 1}, {108, 1}, {109, 1}, {110, 1}, {111, 1}, {112, 1}, {113, 1}, {114, 1},
            {115, 1}, {116, 1}, {117, 1}, {118, 1}, {119, 1}, {120, 1}, {121, 1}, {122, 1},
            {123, 1}, {124, 1}, {125, 1}, {126, 1}, {127, 127}, {128, 1}, {135, 1}, {136, 1},
            {137, 1}, {138, 1}, {139, 1}, {140, 140}, {141, 1}, {142, 1}, {143, 1}, {144, 1},
            {145, 1}, {146, 1}, {147, 1}, {148, 1}, {149, 149}, {150, 1}, {151, 151}, {152, 1},
            {153, 1}, {154, 1}, {159, 1},
    };
    // BIOME_755: 756 entries=176 ranges=4 overrides=99

    static final int[][] BIOME_755_TO_756_RANGES = {
            {0, 50, 0}, {129, 134, 0}, {155, 158, 0}, {160, 175, 0},
    };

    static final int[][] BIOME_755_TO_756_OVERRIDES = {
            {51, 1}, {52, 1}, {53, 1}, {54, 1}, {55, 1}, {56, 1}, {57, 1}, {58, 1},
            {59, 1}, {60, 1}, {61, 1}, {62, 1}, {63, 1}, {64, 1}, {65, 1}, {66, 1},
            {67, 1}, {68, 1}, {69, 1}, {70, 1}, {71, 1}, {72, 1}, {73, 1}, {74, 1},
            {75, 1}, {76, 1}, {77, 1}, {78, 1}, {79, 1}, {80, 1}, {81, 1}, {82, 1},
            {83, 1}, {84, 1}, {85, 1}, {86, 1}, {87, 1}, {88, 1}, {89, 1}, {90, 1},
            {91, 1}, {92, 1}, {93, 1}, {94, 1}, {95, 1}, {96, 1}, {97, 1}, {98, 1},
            {99, 1}, {100, 1}, {101, 1}, {102, 1}, {103, 1}, {104, 1}, {105, 1}, {106, 1},
            {107, 1}, {108, 1}, {109, 1}, {110, 1}, {111, 1}, {112, 1}, {113, 1}, {114, 1},
            {115, 1}, {116, 1}, {117, 1}, {118, 1}, {119, 1}, {120, 1}, {121, 1}, {122, 1},
            {123, 1}, {124, 1}, {125, 1}, {126, 1}, {127, 127}, {128, 1}, {135, 1}, {136, 1},
            {137, 1}, {138, 1}, {139, 1}, {140, 140}, {141, 1}, {142, 1}, {143, 1}, {144, 1},
            {145, 1}, {146, 1}, {147, 1}, {148, 1}, {149, 149}, {150, 1}, {151, 151}, {152, 1},
            {153, 1}, {154, 1}, {159, 1},
    };
    // BIOME_755_TO_756: 755 entries=176 ranges=4 overrides=99

    static final int[][] DAMAGE_TYPE_755_RANGES = {
            {0, 41, 0},
    };

    static final int[][] DAMAGE_TYPE_755_OVERRIDES = {
    };
    // DAMAGE_TYPE_755: 756 entries=42 ranges=1 overrides=0

    static final int[][] DAMAGE_TYPE_755_TO_756_RANGES = {
            {0, 41, 0},
    };

    static final int[][] DAMAGE_TYPE_755_TO_756_OVERRIDES = {
    };
    // DAMAGE_TYPE_755_TO_756: 755 entries=42 ranges=1 overrides=0

}
