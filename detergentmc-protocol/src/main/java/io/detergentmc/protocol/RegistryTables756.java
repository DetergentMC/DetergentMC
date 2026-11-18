package io.detergentmc.protocol;

/**
 * 756（1.17.1）のレジストリ対応表（区間＋上書きの生の表）。
 *
 * <p>757（1.18／1.18.1）との隣接表だけを置く。756 は最古バージョンのため
 * 離れた組み合わせの合成表は作らない（763〜775 の組が合成表を持つ
 * のとは違い、759〜762 の組は隣接表の連鎖だけを使う設計のため。
 * 756↔763〜775 は 756→757→759→760→761→762→763→合成表の連鎖で到達する）。
 * 757→756 の順向き表（*_756_*）と 756→757 の反転表（*_756_TO_757_*）
 * を両方置く。反転表は名前の真値（同名のものを優先）で作ってあり、
 * {@link RegistryRemapper#invert} の first-wins と違う箇所は
 * tools/maps756-corrections.json の補正（UP_756_CORRECTIONS）で
 * 埋める（補正はパーティクル 1 件のみ）。
 *
 * <p>バイオームの id は 1.17.1 が 0〜175 の非連番（旧来の数値バイオームID）、
 * 1.18 が 0〜60 の連番。順向き表（BIOME_756）は 757 側 61 件、反転表
 * （BIOME_756_TO_757）は 756 側 176 件（id 0〜175。隙間の id は
 * 平原（757 id 1）で埋める）。ワイヤ上の id がこの通りであることは
 * 実バニラ 1.17.1 の Join Game dimensionCodec の生キャプチャで確認済み。
 *
 * <p>{@link RegistryRemapper} から分離した。理由はバイトコードの制限：
 * 756 の表を RegistryRemapper に置くと静的初期化子（{@code <clinit>}）
 * が 64KB を超えてコンパイルできなくなる（"code too large"）ため。
 * このクラスの表は {@link RegistryRemapper} から static import で
 * 同じ名前のまま参照する（展開済み表 {@code *_756_MAP} やアクセサの
 * 書き方は変わらない）。
 *
 * <p>表の内容と検証は {@code detergentmc-protocol/tools/} を参照：
 * 生成は {@code gen-756-tables.py}、検証は {@code verify-756-tables.py}。
 */
final class RegistryTables756 {

    private RegistryTables756() {
    }

    static final int[][] BLOCK_STATE_756_RANGES = {
            {0, 20341, 0},
    };

    static final int[][] BLOCK_STATE_756_OVERRIDES = {
    };
    // BLOCK_STATE_756: 757 entries=20342 ranges=1 overrides=0

    static final int[][] ITEM_756_RANGES = {
            {0, 1026, 0}, {1028, 1100, -1},
    };

    static final int[][] ITEM_756_OVERRIDES = {
            {1027, 1015},
    };
    // ITEM_756: 757 entries=1101 ranges=2 overrides=1

    static final int[][] ENTITY_TYPE_756_RANGES = {
            {0, 112, 0},
    };

    static final int[][] ENTITY_TYPE_756_OVERRIDES = {
    };
    // ENTITY_TYPE_756: 757 entries=113 ranges=1 overrides=0

    static final int[][] SOUND_756_RANGES = {
            {0, 158, 0}, {162, 461, -3}, {463, 632, -4}, {634, 638, -5}, {650, 1202, -13},
    };

    static final int[][] SOUND_756_OVERRIDES = {
            {159, 537}, {160, 537}, {161, 537}, {462, 236}, {633, 617}, {639, 636}, {640, 631}, {641, 631},
            {642, 631}, {643, 631}, {644, 631}, {645, 634}, {646, 631}, {647, 631}, {648, 635}, {649, 631},
    };
    // SOUND_756: 757 entries=1203 ranges=5 overrides=16

    static final int[][] PARTICLE_756_RANGES = {
            {0, 1, 0}, {4, 87, 1},
    };

    static final int[][] PARTICLE_756_OVERRIDES = {
            {2, 4}, {3, 2},
    };
    // PARTICLE_756: 757 entries=88 ranges=2 overrides=2

    static final int[][] BLOCK_756_RANGES = {
            {0, 897, 0},
    };

    static final int[][] BLOCK_756_OVERRIDES = {
    };
    // BLOCK_756: 757 entries=898 ranges=1 overrides=0

    static final int[][] MENU_756_RANGES = {
            {0, 23, 0},
    };

    static final int[][] MENU_756_OVERRIDES = {
    };
    // MENU_756: 757 entries=24 ranges=1 overrides=0

    static final int[][] ITEM_REVERSE_756_RANGES = {
            {0, 1026, 0}, {1027, 1099, 1},
    };

    static final int[][] ITEM_REVERSE_756_OVERRIDES = {
    };
    // ITEM_REVERSE_756: 756 entries=1100 ranges=2 overrides=0

    static final int[][] BLOCK_STATE_756_TO_757_RANGES = {
            {0, 20341, 0},
    };

    static final int[][] BLOCK_STATE_756_TO_757_OVERRIDES = {
    };
    // BLOCK_STATE_756_TO_757: 756 entries=20342 ranges=1 overrides=0

    static final int[][] ITEM_756_TO_757_RANGES = {
            {0, 1026, 0}, {1027, 1099, 1},
    };

    static final int[][] ITEM_756_TO_757_OVERRIDES = {
    };
    // ITEM_756_TO_757: 756 entries=1100 ranges=2 overrides=0

    static final int[][] ENTITY_TYPE_756_TO_757_RANGES = {
            {0, 112, 0},
    };

    static final int[][] ENTITY_TYPE_756_TO_757_OVERRIDES = {
    };
    // ENTITY_TYPE_756_TO_757: 756 entries=113 ranges=1 overrides=0

    static final int[][] SOUND_756_TO_757_RANGES = {
            {0, 158, 0}, {159, 458, 3}, {459, 628, 4}, {629, 633, 5}, {637, 1189, 13},
    };

    static final int[][] SOUND_756_TO_757_OVERRIDES = {
            {634, 645}, {635, 648}, {636, 639},
    };
    // SOUND_756_TO_757: 756 entries=1190 ranges=5 overrides=3

    static final int[][] PARTICLE_756_TO_757_RANGES = {
            {0, 1, 0}, {5, 88, -1},
    };

    static final int[][] PARTICLE_756_TO_757_OVERRIDES = {
            {2, 3}, {3, 3}, {4, 2},
    };
    // PARTICLE_756_TO_757: 756 entries=89 ranges=2 overrides=3

    static final int[][] BLOCK_756_TO_757_RANGES = {
            {0, 897, 0},
    };

    static final int[][] BLOCK_756_TO_757_OVERRIDES = {
    };
    // BLOCK_756_TO_757: 756 entries=898 ranges=1 overrides=0

    static final int[][] MENU_756_TO_757_RANGES = {
            {0, 23, 0},
    };

    static final int[][] MENU_756_TO_757_OVERRIDES = {
    };
    // MENU_756_TO_757: 756 entries=24 ranges=1 overrides=0

    static final String[][] SOUND_NAME_FALLBACKS_756 = {
            {"block.growing_plant.crop", "block.crop.break"},
            {"item.bundle.drop_contents", "entity.item.pickup"},
            {"item.bundle.insert", "entity.item.pickup"},
            {"item.bundle.remove_one", "entity.item.pickup"},
            {"music.overworld.dripstone_caves", "music.game"},
            {"music.overworld.frozen_peaks", "music.game"},
            {"music.overworld.grove", "music.game"},
            {"music.overworld.jagged_peaks", "music.game"},
            {"music.overworld.lush_caves", "music.game"},
            {"music.overworld.meadow", "music.game"},
            {"music.overworld.snowy_slopes", "music.game"},
            {"music.overworld.stony_peaks", "music.game"},
            {"music_disc.otherside", "music_disc.13"},
    };

    static final String[][] SOUND_NAME_FALLBACKS_757_TO_756 = {
            {"block.growing_plant.crop", "block.crop.break"},
            {"item.bundle.drop_contents", "entity.item.pickup"},
            {"item.bundle.insert", "entity.item.pickup"},
            {"item.bundle.remove_one", "entity.item.pickup"},
            {"music.overworld.dripstone_caves", "music.game"},
            {"music.overworld.frozen_peaks", "music.game"},
            {"music.overworld.grove", "music.game"},
            {"music.overworld.jagged_peaks", "music.game"},
            {"music.overworld.lush_caves", "music.game"},
            {"music.overworld.meadow", "music.game"},
            {"music.overworld.snowy_slopes", "music.game"},
            {"music.overworld.stony_peaks", "music.game"},
            {"music_disc.otherside", "music_disc.13"},
    };

    static final String[][] SOUND_NAME_FALLBACKS_756_TO_757 = {
    };

    static final int[][] ATTRIBUTE_756_RANGES = {
            {0, 12, 0},
    };

    static final int[][] ATTRIBUTE_756_OVERRIDES = {
    };
    // ATTRIBUTE_756: 757 entries=13 ranges=1 overrides=0

    static final int[][] ATTRIBUTE_REVERSE_756_RANGES = {
            {0, 12, 0},
    };

    static final int[][] ATTRIBUTE_REVERSE_756_OVERRIDES = {
    };
    // ATTRIBUTE_REVERSE_756: 756 entries=13 ranges=1 overrides=0

    static final String[] ATTRIBUTE_756_NAMES = {
            "generic.max_health", "generic.follow_range", "generic.knockback_resistance", "generic.movement_speed",
            "generic.flying_speed", "generic.attack_damage", "generic.attack_knockback", "generic.attack_speed",
            "generic.armor", "generic.armor_toughness", "generic.luck", "zombie.spawn_reinforcements",
            "horse.jump_strength",
    };

    static final int[][] ENCHANTMENT_756_RANGES = {
            {0, 37, 0},
    };

    static final int[][] ENCHANTMENT_756_OVERRIDES = {
    };
    // ENCHANTMENT_756: 757 entries=38 ranges=1 overrides=0

    static final int[][] ENCHANTMENT_REVERSE_756_RANGES = {
            {0, 37, 0},
    };

    static final int[][] ENCHANTMENT_REVERSE_756_OVERRIDES = {
    };
    // ENCHANTMENT_REVERSE_756: 756 entries=38 ranges=1 overrides=0

    static final int[][] BIOME_756_RANGES = {
            {16, 17, 19}, {30, 31, -18}, {39, 40, 5}, {49, 50, 125},
    };

    static final int[][] BIOME_756_OVERRIDES = {
            {0, 127}, {1, 1}, {2, 129}, {3, 12}, {4, 140}, {5, 2}, {6, 6}, {7, 4},
            {8, 132}, {9, 27}, {10, 29}, {11, 155}, {12, 32}, {13, 160}, {14, 5}, {15, 30},
            {18, 3}, {19, 131}, {20, 34}, {21, 163}, {22, 21}, {23, 23}, {24, 168}, {25, 37},
            {26, 165}, {27, 38}, {28, 1}, {29, 30}, {32, 3}, {33, 3}, {34, 7}, {35, 11},
            {36, 16}, {37, 26}, {38, 25}, {41, 48}, {42, 0}, {43, 24}, {44, 46}, {45, 49},
            {46, 10}, {47, 50}, {48, 14}, {51, 8}, {52, 172}, {53, 171}, {54, 170}, {55, 173},
            {56, 9}, {57, 42}, {58, 41}, {59, 40}, {60, 43},
    };
    // BIOME_756: 757 entries=61 ranges=4 overrides=53

    static final int[][] BIOME_756_TO_757_RANGES = {
            {22, 23, 0}, {28, 29, -19}, {35, 36, -19}, {44, 45, -5}, {127, 129, -127}, {165, 166, -139},
            {174, 175, -125},
    };

    static final int[][] BIOME_756_TO_757_OVERRIDES = {
            {0, 42}, {1, 1}, {2, 5}, {3, 18}, {4, 7}, {5, 14}, {6, 6}, {7, 34},
            {8, 51}, {9, 56}, {10, 46}, {11, 35}, {12, 3}, {13, 31}, {14, 48}, {15, 48},
            {16, 36}, {17, 5}, {18, 20}, {19, 14}, {20, 18}, {21, 22}, {24, 43}, {25, 38},
            {26, 37}, {27, 9}, {30, 15}, {31, 15}, {32, 12}, {33, 12}, {34, 20}, {37, 25},
            {38, 27}, {39, 25}, {40, 59}, {41, 58}, {42, 57}, {43, 60}, {46, 44}, {47, 39},
            {48, 41}, {49, 45}, {50, 47}, {51, 1}, {52, 1}, {53, 1}, {54, 1}, {55, 1},
            {56, 1}, {57, 1}, {58, 1}, {59, 1}, {60, 1}, {61, 1}, {62, 1}, {63, 1},
            {64, 1}, {65, 1}, {66, 1}, {67, 1}, {68, 1}, {69, 1}, {70, 1}, {71, 1},
            {72, 1}, {73, 1}, {74, 1}, {75, 1}, {76, 1}, {77, 1}, {78, 1}, {79, 1},
            {80, 1}, {81, 1}, {82, 1}, {83, 1}, {84, 1}, {85, 1}, {86, 1}, {87, 1},
            {88, 1}, {89, 1}, {90, 1}, {91, 1}, {92, 1}, {93, 1}, {94, 1}, {95, 1},
            {96, 1}, {97, 1}, {98, 1}, {99, 1}, {100, 1}, {101, 1}, {102, 1}, {103, 1},
            {104, 1}, {105, 1}, {106, 1}, {107, 1}, {108, 1}, {109, 1}, {110, 1}, {111, 1},
            {112, 1}, {113, 1}, {114, 1}, {115, 1}, {116, 1}, {117, 1}, {118, 1}, {119, 1},
            {120, 1}, {121, 1}, {122, 1}, {123, 1}, {124, 1}, {125, 1}, {126, 1}, {130, 5},
            {131, 19}, {132, 8}, {133, 14}, {134, 6}, {135, 1}, {136, 1}, {137, 1}, {138, 1},
            {139, 1}, {140, 4}, {141, 1}, {142, 1}, {143, 1}, {144, 1}, {145, 1}, {146, 1},
            {147, 1}, {148, 1}, {149, 22}, {150, 1}, {151, 23}, {152, 1}, {153, 1}, {154, 1},
            {155, 11}, {156, 11}, {157, 10}, {158, 29}, {159, 1}, {160, 13}, {161, 13}, {162, 19},
            {163, 21}, {164, 21}, {167, 25}, {168, 24}, {169, 24}, {170, 54}, {171, 53}, {172, 52},
            {173, 55},
    };
    // BIOME_756_TO_757: 756 entries=176 ranges=7 overrides=161

    static final int[][] DAMAGE_TYPE_756_RANGES = {
            {0, 41, 0},
    };

    static final int[][] DAMAGE_TYPE_756_OVERRIDES = {
    };
    // DAMAGE_TYPE_756: 757 entries=42 ranges=1 overrides=0

    static final int[][] DAMAGE_TYPE_756_TO_757_RANGES = {
            {0, 41, 0},
    };

    static final int[][] DAMAGE_TYPE_756_TO_757_OVERRIDES = {
    };
    // DAMAGE_TYPE_756_TO_757: 756 entries=42 ranges=1 overrides=0

}
