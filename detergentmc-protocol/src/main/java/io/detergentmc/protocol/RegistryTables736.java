package io.detergentmc.protocol;

/**
 * 736（1.16.1）のレジストリ対応表（区間＋上書きの生の表）。
 *
 * <p>751（1.16.2）との隣接表だけを置く。736 は最古バージョンのため
 * 離れた組み合わせの合成表は作らない（763〜775 の組が合成表を持つ
 * のとは違い、759〜762 の組は隣接表の連鎖だけを使う設計のため。
 * 736↔763〜775 は 736→751→755→756→757→759→760→761→762→763→合成表の連鎖で到達する）。
 * 751→736 の順向き表（*_736_*）と 736→751 の反転表（*_736_TO_751_*）
 * を両方置く。反転表は名前の真値（同名のものを優先）で作ってあり、
 * 751 と 736 のレジストリ内容は違うため（1.16.2 で追加された
 * piglin_brute 系が 1.16.1 には無い）、順向き表はブロック状態
 * 12,381 件・アイテム 249 件・エンティティ 47 件・音 405 件が恒等でなく、
 * 反転表も同じく非恒等。補正はブロック状態 4 件
 * （tools/maps736-corrections.json。invert() の first-wins と
 * 名前の真値の食い違い。lantern／soul_lantern の waterlogged 側）。
 *
 * <p>バイオームの id は 1.16.1／1.16.2 とも 0〜173（79 件）の
 * 非連番（旧来の数値バイオームID）。0〜173 は同じ id・同じ名前。
 * 順向き表（BIOME_736）は 751 側 174 件（id 0〜173。隙間の id は
 * 平原（736 id 1）で埋める）、反転表
 * （BIOME_736_TO_751）も 736 側 174 件（id 0〜173。隙間の id は
 * 平原（751 id 1）で埋める）。ワイヤ上の id がこの通りであることは
 * Join Game の dimensionCodec との突き合わせで確認する
 * （tools/gen-736-tables.py の §11.6 相当の Join Game codec 確認を参照。
 * 751 では実バニラ 1.16.3 の生キャプチャで確認済み）。
 *
 * <p>{@link RegistryRemapper} から分離した。理由はバイトコードの制限：
 * 736 の表を RegistryRemapper に置くと静的初期化子（{@code <clinit>}）
 * が 64KB を超えてコンパイルできなくなる（"code too large"）ため。
 * このクラスの表は {@link RegistryRemapper} から static import で
 * 同じ名前のまま参照する（展開済み表 {@code *_736_MAP} やアクセサの
 * 書き方は変わらない）。
 *
 * <p>表の内容と検証は {@code detergentmc-protocol/tools/} を参照：
 * 生成は {@code gen-736-tables.py}、検証は {@code verify-736-tables.py}。
 */
final class RegistryTables736 {

    private RegistryTables736() {
    }

    static final int[][] ITEM_736_RANGES = {
            {0, 571, 0}, {572, 582, 3}, {583, 586, 4}, {588, 591, 3}, {593, 596, 9}, {599, 601, -27},
            {603, 606, -8}, {608, 611, -2}, {613, 615, -14}, {616, 618, -6}, {619, 693, 0}, {694, 695, 2},
            {707, 708, 2}, {713, 790, 0}, {791, 975, -1},
    };

    static final int[][] ITEM_736_OVERRIDES = {
            {587, 613}, {592, 614}, {597, 617}, {598, 586}, {602, 615}, {607, 616}, {612, 618}, {696, 712},
            {697, 707}, {698, 706}, {699, 705}, {700, 704}, {701, 703}, {702, 702}, {703, 701}, {704, 700},
            {705, 699}, {706, 698}, {709, 695}, {710, 694}, {711, 711}, {712, 708},
    };
    // ITEM_736: 751 entries=976 ranges=15 overrides=22

    static final int[][] ENTITY_TYPE_736_RANGES = {
            {0, 60, 0}, {61, 107, -1},
    };

    static final int[][] ENTITY_TYPE_736_OVERRIDES = {
    };
    // ENTITY_TYPE_736: 751 entries=108 ranges=2 overrides=0

    static final int[][] SOUND_736_RANGES = {
            {0, 586, 0}, {587, 625, -1}, {626, 627, -10}, {630, 991, -7},
    };

    static final int[][] SOUND_736_OVERRIDES = {
            {628, 619}, {629, 621},
    };
    // SOUND_736: 751 entries=992 ranges=4 overrides=2

    static final int[][] PARTICLE_736_RANGES = {
            {0, 71, 0},
    };

    static final int[][] PARTICLE_736_OVERRIDES = {
    };
    // PARTICLE_736: 751 entries=72 ranges=1 overrides=0

    static final int[][] BLOCK_736_RANGES = {
            {0, 762, 0},
    };

    static final int[][] BLOCK_736_OVERRIDES = {
    };
    // BLOCK_736: 751 entries=763 ranges=1 overrides=0

    static final int[][] MENU_736_RANGES = {
            {0, 23, 0},
    };

    static final int[][] MENU_736_OVERRIDES = {
    };
    // MENU_736: 751 entries=24 ranges=1 overrides=0

    static final int[][] ITEM_REVERSE_736_RANGES = {
            {0, 571, 0}, {572, 574, 27}, {575, 585, -3}, {587, 590, -4}, {591, 594, -3}, {595, 598, 8},
            {599, 601, 14}, {602, 605, -9}, {606, 609, 2}, {610, 612, 6}, {619, 693, 0}, {696, 697, -2},
            {709, 710, -2}, {713, 790, 0}, {791, 974, 1},
    };

    static final int[][] ITEM_REVERSE_736_OVERRIDES = {
            {586, 598}, {613, 587}, {614, 592}, {615, 602}, {616, 607}, {617, 597}, {618, 612}, {694, 710},
            {695, 709}, {698, 706}, {699, 705}, {700, 704}, {701, 703}, {702, 702}, {703, 701}, {704, 700},
            {705, 699}, {706, 698}, {707, 697}, {708, 712}, {711, 711}, {712, 696},
    };
    // ITEM_REVERSE_736: 736 entries=975 ranges=15 overrides=22

    static final int[][] BLOCK_STATE_736_TO_751_RANGES = {
            {0, 4730, 0}, {4731, 14885, 4}, {14889, 17103, 8},
    };

    static final int[][] BLOCK_STATE_736_TO_751_OVERRIDES = {
            {14886, 14891}, {14887, 14893}, {14888, 14895},
    };
    // BLOCK_STATE_736_TO_751: 736 entries=17104 ranges=3 overrides=3

    static final int[][] ITEM_736_TO_751_RANGES = {
            {0, 571, 0}, {572, 574, 27}, {575, 585, -3}, {587, 590, -4}, {591, 594, -3}, {595, 598, 8},
            {599, 601, 14}, {602, 605, -9}, {606, 609, 2}, {610, 612, 6}, {619, 693, 0}, {696, 697, -2},
            {709, 710, -2}, {713, 790, 0}, {791, 974, 1},
    };

    static final int[][] ITEM_736_TO_751_OVERRIDES = {
            {586, 598}, {613, 587}, {614, 592}, {615, 602}, {616, 607}, {617, 597}, {618, 612}, {694, 710},
            {695, 709}, {698, 706}, {699, 705}, {700, 704}, {701, 703}, {702, 702}, {703, 701}, {704, 700},
            {705, 699}, {706, 698}, {707, 697}, {708, 712}, {711, 711}, {712, 696},
    };
    // ITEM_736_TO_751: 736 entries=975 ranges=15 overrides=22

    static final int[][] ENTITY_TYPE_736_TO_751_RANGES = {
            {0, 60, 0}, {61, 106, 1},
    };

    static final int[][] ENTITY_TYPE_736_TO_751_OVERRIDES = {
    };
    // ENTITY_TYPE_736_TO_751: 736 entries=107 ranges=2 overrides=0

    static final int[][] SOUND_736_TO_751_RANGES = {
            {0, 586, 0}, {587, 624, 1}, {625, 984, 7},
    };

    static final int[][] SOUND_736_TO_751_OVERRIDES = {
    };
    // SOUND_736_TO_751: 736 entries=985 ranges=3 overrides=0

    static final int[][] PARTICLE_736_TO_751_RANGES = {
            {0, 71, 0},
    };

    static final int[][] PARTICLE_736_TO_751_OVERRIDES = {
    };
    // PARTICLE_736_TO_751: 736 entries=72 ranges=1 overrides=0

    static final int[][] BLOCK_736_TO_751_RANGES = {
            {0, 762, 0},
    };

    static final int[][] BLOCK_736_TO_751_OVERRIDES = {
    };
    // BLOCK_736_TO_751: 736 entries=763 ranges=1 overrides=0

    static final int[][] MENU_736_TO_751_RANGES = {
            {0, 23, 0},
    };

    static final int[][] MENU_736_TO_751_OVERRIDES = {
    };
    // MENU_736_TO_751: 736 entries=24 ranges=1 overrides=0

    static final String[][] SOUND_NAME_FALLBACKS_736 = {
            {"entity.parrot.imitate.piglin_brute", "entity.parrot.imitate.piglin"},
            {"entity.piglin_brute.ambient", "entity.piglin.ambient"},
            {"entity.piglin_brute.angry", "entity.piglin.angry"},
            {"entity.piglin_brute.converted_to_zombified", "entity.piglin.converted_to_zombified"},
            {"entity.piglin_brute.death", "entity.piglin.death"},
            {"entity.piglin_brute.hurt", "entity.piglin.hurt"},
            {"entity.piglin_brute.step", "entity.piglin.step"},
    };

    static final String[][] SOUND_NAME_FALLBACKS_751_TO_736 = {
            {"entity.parrot.imitate.piglin_brute", "entity.parrot.imitate.piglin"},
            {"entity.piglin_brute.ambient", "entity.piglin.ambient"},
            {"entity.piglin_brute.angry", "entity.piglin.angry"},
            {"entity.piglin_brute.converted_to_zombified", "entity.piglin.converted_to_zombified"},
            {"entity.piglin_brute.death", "entity.piglin.death"},
            {"entity.piglin_brute.hurt", "entity.piglin.hurt"},
            {"entity.piglin_brute.step", "entity.piglin.step"},
    };

    static final String[][] SOUND_NAME_FALLBACKS_736_TO_751 = {
    };

    static final int[][] ATTRIBUTE_736_RANGES = {
            {0, 12, 0},
    };

    static final int[][] ATTRIBUTE_736_OVERRIDES = {
    };
    // ATTRIBUTE_736: 751 entries=13 ranges=1 overrides=0

    static final int[][] ATTRIBUTE_REVERSE_736_RANGES = {
            {0, 12, 0},
    };

    static final int[][] ATTRIBUTE_REVERSE_736_OVERRIDES = {
    };
    // ATTRIBUTE_REVERSE_736: 736 entries=13 ranges=1 overrides=0

    static final String[] ATTRIBUTE_736_NAMES = {
            "generic.max_health", "generic.follow_range", "generic.knockback_resistance", "generic.movement_speed",
            "generic.flying_speed", "generic.attack_damage", "generic.attack_knockback", "generic.attack_speed",
            "generic.armor", "generic.armor_toughness", "generic.luck", "zombie.spawn_reinforcements",
            "horse.jump_strength",
    };

    static final int[][] ENCHANTMENT_736_RANGES = {
            {0, 37, 0},
    };

    static final int[][] ENCHANTMENT_736_OVERRIDES = {
    };
    // ENCHANTMENT_736: 751 entries=38 ranges=1 overrides=0

    static final int[][] ENCHANTMENT_REVERSE_736_RANGES = {
            {0, 37, 0},
    };

    static final int[][] ENCHANTMENT_REVERSE_736_OVERRIDES = {
    };
    // ENCHANTMENT_REVERSE_736: 736 entries=38 ranges=1 overrides=0

    static final int[][] BIOME_736_RANGES = {
            {0, 50, 0}, {129, 134, 0}, {155, 158, 0}, {160, 173, 0},
    };

    static final int[][] BIOME_736_OVERRIDES = {
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
    // BIOME_736: 751 entries=174 ranges=4 overrides=99

    static final int[][] BIOME_736_TO_751_RANGES = {
            {0, 50, 0}, {129, 134, 0}, {155, 158, 0}, {160, 173, 0},
    };

    static final int[][] BIOME_736_TO_751_OVERRIDES = {
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
    // BIOME_736_TO_751: 736 entries=174 ranges=4 overrides=99

    static final int[][] DAMAGE_TYPE_736_RANGES = {
            {0, 41, 0},
    };

    static final int[][] DAMAGE_TYPE_736_OVERRIDES = {
    };
    // DAMAGE_TYPE_736: 751 entries=42 ranges=1 overrides=0

    static final int[][] DAMAGE_TYPE_736_TO_751_RANGES = {
            {0, 41, 0},
    };

    static final int[][] DAMAGE_TYPE_736_TO_751_OVERRIDES = {
    };
    // DAMAGE_TYPE_736_TO_751: 736 entries=42 ranges=1 overrides=0

}
