package io.detergentmc.protocol;

/**
 * 735（1.16）のレジストリ対応表（区間＋上書きの生の表）。
 *
 * <p>736（1.16.1）との隣接表だけを置く。735 は最古バージョンのため
 * 離れた組み合わせの合成表は作らない（763〜775 の組が合成表を持つ
 * のとは違い、759〜762 の組は隣接表の連鎖だけを使う設計のため。
 * 735↔763〜775 は 735→736→751→753→754→755→756→757→759→760→761→762→763→合成表の連鎖で到達する）。
 * 736→735 の順向き表（*_735_*）と 735→736 の反転表（*_735_TO_736_*）
 * を両方置く。反転表は名前の真値（同名のものを優先）で作ってあり、
 * 736 と 735 のレジストリ内容は同一のため（実バニラの reports が
 * バイト同一）、順向き表・反転表とも全種恒等写像。補正は 0 件
 * （tools/maps735-corrections.json）。
 *
 * <p>バイオームの id は 1.16／1.16.1 とも 0〜173（79 件）の
 * 非連番（旧来の数値バイオームID）。0〜173 は同じ id・同じ名前。
 * 順向き表（BIOME_735）は 736 側 174 件（id 0〜173。隙間の id は
 * 平原（735 id 1）で埋める）、反転表
 * （BIOME_735_TO_736）も 735 側 174 件（id 0〜173。隙間の id は
 * 平原（736 id 1）で埋める）。ワイヤ上の id がこの通りであることは
 * Join Game の dimensionCodec との突き合わせで確認する
 * （tools/gen-735-tables.py の §11.6 相当の Join Game codec 確認を参照。
 * 736 では実バニラ 1.16.1 の生キャプチャで確認済み）。
 *
 * <p>{@link RegistryRemapper} から分離した。理由はバイトコードの制限：
 * 735 の表を RegistryRemapper に置くと静的初期化子（{@code <clinit>}）
 * が 64KB を超えてコンパイルできなくなる（"code too large"）ため。
 * このクラスの表は {@link RegistryRemapper} から static import で
 * 同じ名前のまま参照する（展開済み表 {@code *_735_MAP} やアクセサの
 * 書き方は変わらない）。
 *
 * <p>表の内容と検証は {@code detergentmc-protocol/tools/} を参照：
 * 生成は {@code gen-735-tables.py}、検証は {@code verify-735-tables.py}。
 */
final class RegistryTables735 {

    private RegistryTables735() {
    }

    static final int[][] ITEM_735_RANGES = {
            {0, 974, 0},
    };

    static final int[][] ITEM_735_OVERRIDES = {
    };
    // ITEM_735: 736 entries=975 ranges=1 overrides=0

    static final int[][] ENTITY_TYPE_735_RANGES = {
            {0, 106, 0},
    };

    static final int[][] ENTITY_TYPE_735_OVERRIDES = {
    };
    // ENTITY_TYPE_735: 736 entries=107 ranges=1 overrides=0

    static final int[][] SOUND_735_RANGES = {
            {0, 984, 0},
    };

    static final int[][] SOUND_735_OVERRIDES = {
    };
    // SOUND_735: 736 entries=985 ranges=1 overrides=0

    static final int[][] PARTICLE_735_RANGES = {
            {0, 71, 0},
    };

    static final int[][] PARTICLE_735_OVERRIDES = {
    };
    // PARTICLE_735: 736 entries=72 ranges=1 overrides=0

    static final int[][] BLOCK_735_RANGES = {
            {0, 762, 0},
    };

    static final int[][] BLOCK_735_OVERRIDES = {
    };
    // BLOCK_735: 736 entries=763 ranges=1 overrides=0

    static final int[][] MENU_735_RANGES = {
            {0, 23, 0},
    };

    static final int[][] MENU_735_OVERRIDES = {
    };
    // MENU_735: 736 entries=24 ranges=1 overrides=0

    static final int[][] ITEM_REVERSE_735_RANGES = {
            {0, 974, 0},
    };

    static final int[][] ITEM_REVERSE_735_OVERRIDES = {
    };
    // ITEM_REVERSE_735: 735 entries=975 ranges=1 overrides=0

    static final int[][] BLOCK_STATE_735_TO_736_RANGES = {
            {0, 17103, 0},
    };

    static final int[][] BLOCK_STATE_735_TO_736_OVERRIDES = {
    };
    // BLOCK_STATE_735_TO_736: 735 entries=17104 ranges=1 overrides=0

    static final int[][] ITEM_735_TO_736_RANGES = {
            {0, 974, 0},
    };

    static final int[][] ITEM_735_TO_736_OVERRIDES = {
    };
    // ITEM_735_TO_736: 735 entries=975 ranges=1 overrides=0

    static final int[][] ENTITY_TYPE_735_TO_736_RANGES = {
            {0, 106, 0},
    };

    static final int[][] ENTITY_TYPE_735_TO_736_OVERRIDES = {
    };
    // ENTITY_TYPE_735_TO_736: 735 entries=107 ranges=1 overrides=0

    static final int[][] SOUND_735_TO_736_RANGES = {
            {0, 984, 0},
    };

    static final int[][] SOUND_735_TO_736_OVERRIDES = {
    };
    // SOUND_735_TO_736: 735 entries=985 ranges=1 overrides=0

    static final int[][] PARTICLE_735_TO_736_RANGES = {
            {0, 71, 0},
    };

    static final int[][] PARTICLE_735_TO_736_OVERRIDES = {
    };
    // PARTICLE_735_TO_736: 735 entries=72 ranges=1 overrides=0

    static final int[][] BLOCK_735_TO_736_RANGES = {
            {0, 762, 0},
    };

    static final int[][] BLOCK_735_TO_736_OVERRIDES = {
    };
    // BLOCK_735_TO_736: 735 entries=763 ranges=1 overrides=0

    static final int[][] MENU_735_TO_736_RANGES = {
            {0, 23, 0},
    };

    static final int[][] MENU_735_TO_736_OVERRIDES = {
    };
    // MENU_735_TO_736: 735 entries=24 ranges=1 overrides=0

    static final String[][] SOUND_NAME_FALLBACKS_735 = {
    };

    static final String[][] SOUND_NAME_FALLBACKS_736_TO_735 = {
    };

    static final String[][] SOUND_NAME_FALLBACKS_735_TO_736 = {
    };

    static final int[][] ATTRIBUTE_735_RANGES = {
            {0, 12, 0},
    };

    static final int[][] ATTRIBUTE_735_OVERRIDES = {
    };
    // ATTRIBUTE_735: 736 entries=13 ranges=1 overrides=0

    static final int[][] ATTRIBUTE_REVERSE_735_RANGES = {
            {0, 12, 0},
    };

    static final int[][] ATTRIBUTE_REVERSE_735_OVERRIDES = {
    };
    // ATTRIBUTE_REVERSE_735: 735 entries=13 ranges=1 overrides=0

    static final String[] ATTRIBUTE_735_NAMES = {
            "generic.max_health", "generic.follow_range", "generic.knockback_resistance", "generic.movement_speed",
            "generic.flying_speed", "generic.attack_damage", "generic.attack_knockback", "generic.attack_speed",
            "generic.armor", "generic.armor_toughness", "generic.luck", "zombie.spawn_reinforcements",
            "horse.jump_strength",
    };

    static final int[][] ENCHANTMENT_735_RANGES = {
            {0, 37, 0},
    };

    static final int[][] ENCHANTMENT_735_OVERRIDES = {
    };
    // ENCHANTMENT_735: 736 entries=38 ranges=1 overrides=0

    static final int[][] ENCHANTMENT_REVERSE_735_RANGES = {
            {0, 37, 0},
    };

    static final int[][] ENCHANTMENT_REVERSE_735_OVERRIDES = {
    };
    // ENCHANTMENT_REVERSE_735: 735 entries=38 ranges=1 overrides=0

    static final int[][] BIOME_735_RANGES = {
            {0, 50, 0}, {129, 134, 0}, {155, 158, 0}, {160, 173, 0},
    };

    static final int[][] BIOME_735_OVERRIDES = {
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
    // BIOME_735: 736 entries=174 ranges=4 overrides=99

    static final int[][] BIOME_735_TO_736_RANGES = {
            {0, 50, 0}, {129, 134, 0}, {155, 158, 0}, {160, 173, 0},
    };

    static final int[][] BIOME_735_TO_736_OVERRIDES = {
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
    // BIOME_735_TO_736: 735 entries=174 ranges=4 overrides=99

    static final int[][] DAMAGE_TYPE_735_RANGES = {
            {0, 41, 0},
    };

    static final int[][] DAMAGE_TYPE_735_OVERRIDES = {
    };
    // DAMAGE_TYPE_735: 736 entries=42 ranges=1 overrides=0

    static final int[][] DAMAGE_TYPE_735_TO_736_RANGES = {
            {0, 41, 0},
    };

    static final int[][] DAMAGE_TYPE_735_TO_736_OVERRIDES = {
    };
    // DAMAGE_TYPE_735_TO_736: 735 entries=42 ranges=1 overrides=0

}
