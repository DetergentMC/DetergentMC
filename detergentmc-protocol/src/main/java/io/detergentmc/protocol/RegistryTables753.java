package io.detergentmc.protocol;

/**
 * 753（1.16.3）のレジストリ対応表（区間＋上書きの生の表）。
 *
 * <p>754（1.16.4／1.16.5）との隣接表だけを置く。753 は最古バージョンのため
 * 離れた組み合わせの合成表は作らない（763〜775 の組が合成表を持つ
 * のとは違い、759〜762 の組は隣接表の連鎖だけを使う設計のため。
 * 753↔763〜775 は 753→754→755→756→757→759→760→761→762→763→合成表の連鎖で到達する）。
 * 754→753 の順向き表（*_753_*）と 753→754 の反転表（*_753_TO_754_*）
 * を両方置く。反転表は名前の真値（同名のものを優先）で作ってあり、
 * 754 と 753 のレジストリ内容はバイト単位で完全一致するため、順向き表・
 * 反転表とも 7 種すべて恒等写像で、補正は 0 件
 * （tools/maps753-corrections.json。invert() の first-wins と
 * 名前の真値の食い違いは無い）。
 *
 * <p>バイオームの id は 1.16.3／1.16.5 とも 0〜173（79 件）の
 * 非連番（旧来の数値バイオームID）。0〜173 は同じ id・同じ名前。
 * 順向き表（BIOME_753）は 754 側 174 件（id 0〜173。隙間の id は
 * 平原（753 id 1）で埋める）、反転表
 * （BIOME_753_TO_754）も 753 側 174 件（id 0〜173。隙間の id は
 * 平原（754 id 1）で埋める）。ワイヤ上の id がこの通りであることは
 * Join Game の dimensionCodec との突き合わせで確認する
 * （tools/gen-753-tables.py の §11.6 相当の Join Game codec 確認を参照。
 * 754 では実バニラ 1.16.5 の生キャプチャで確認済み）。
 *
 * <p>{@link RegistryRemapper} から分離した。理由はバイトコードの制限：
 * 753 の表を RegistryRemapper に置くと静的初期化子（{@code <clinit>}）
 * が 64KB を超えてコンパイルできなくなる（"code too large"）ため。
 * このクラスの表は {@link RegistryRemapper} から static import で
 * 同じ名前のまま参照する（展開済み表 {@code *_753_MAP} やアクセサの
 * 書き方は変わらない）。
 *
 * <p>表の内容と検証は {@code detergentmc-protocol/tools/} を参照：
 * 生成は {@code gen-753-tables.py}、検証は {@code verify-753-tables.py}。
 */
final class RegistryTables753 {

    private RegistryTables753() {
    }

    static final int[][] ITEM_753_RANGES = {
            {0, 975, 0},
    };

    static final int[][] ITEM_753_OVERRIDES = {
    };
    // ITEM_753: 754 entries=976 ranges=1 overrides=0

    static final int[][] ENTITY_TYPE_753_RANGES = {
            {0, 107, 0},
    };

    static final int[][] ENTITY_TYPE_753_OVERRIDES = {
    };
    // ENTITY_TYPE_753: 754 entries=108 ranges=1 overrides=0

    static final int[][] SOUND_753_RANGES = {
            {0, 991, 0},
    };

    static final int[][] SOUND_753_OVERRIDES = {
    };
    // SOUND_753: 754 entries=992 ranges=1 overrides=0

    static final int[][] PARTICLE_753_RANGES = {
            {0, 71, 0},
    };

    static final int[][] PARTICLE_753_OVERRIDES = {
    };
    // PARTICLE_753: 754 entries=72 ranges=1 overrides=0

    static final int[][] BLOCK_753_RANGES = {
            {0, 762, 0},
    };

    static final int[][] BLOCK_753_OVERRIDES = {
    };
    // BLOCK_753: 754 entries=763 ranges=1 overrides=0

    static final int[][] MENU_753_RANGES = {
            {0, 23, 0},
    };

    static final int[][] MENU_753_OVERRIDES = {
    };
    // MENU_753: 754 entries=24 ranges=1 overrides=0

    static final int[][] ITEM_REVERSE_753_RANGES = {
            {0, 975, 0},
    };

    static final int[][] ITEM_REVERSE_753_OVERRIDES = {
    };
    // ITEM_REVERSE_753: 753 entries=976 ranges=1 overrides=0

    static final int[][] BLOCK_STATE_753_TO_754_RANGES = {
            {0, 17111, 0},
    };

    static final int[][] BLOCK_STATE_753_TO_754_OVERRIDES = {
    };
    // BLOCK_STATE_753_TO_754: 753 entries=17112 ranges=1 overrides=0

    static final int[][] ITEM_753_TO_754_RANGES = {
            {0, 975, 0},
    };

    static final int[][] ITEM_753_TO_754_OVERRIDES = {
    };
    // ITEM_753_TO_754: 753 entries=976 ranges=1 overrides=0

    static final int[][] ENTITY_TYPE_753_TO_754_RANGES = {
            {0, 107, 0},
    };

    static final int[][] ENTITY_TYPE_753_TO_754_OVERRIDES = {
    };
    // ENTITY_TYPE_753_TO_754: 753 entries=108 ranges=1 overrides=0

    static final int[][] SOUND_753_TO_754_RANGES = {
            {0, 991, 0},
    };

    static final int[][] SOUND_753_TO_754_OVERRIDES = {
    };
    // SOUND_753_TO_754: 753 entries=992 ranges=1 overrides=0

    static final int[][] PARTICLE_753_TO_754_RANGES = {
            {0, 71, 0},
    };

    static final int[][] PARTICLE_753_TO_754_OVERRIDES = {
    };
    // PARTICLE_753_TO_754: 753 entries=72 ranges=1 overrides=0

    static final int[][] BLOCK_753_TO_754_RANGES = {
            {0, 762, 0},
    };

    static final int[][] BLOCK_753_TO_754_OVERRIDES = {
    };
    // BLOCK_753_TO_754: 753 entries=763 ranges=1 overrides=0

    static final int[][] MENU_753_TO_754_RANGES = {
            {0, 23, 0},
    };

    static final int[][] MENU_753_TO_754_OVERRIDES = {
    };
    // MENU_753_TO_754: 753 entries=24 ranges=1 overrides=0

    static final String[][] SOUND_NAME_FALLBACKS_753 = {
    };

    static final String[][] SOUND_NAME_FALLBACKS_754_TO_753 = {
    };

    static final String[][] SOUND_NAME_FALLBACKS_753_TO_754 = {
    };

    static final int[][] ATTRIBUTE_753_RANGES = {
            {0, 12, 0},
    };

    static final int[][] ATTRIBUTE_753_OVERRIDES = {
    };
    // ATTRIBUTE_753: 754 entries=13 ranges=1 overrides=0

    static final int[][] ATTRIBUTE_REVERSE_753_RANGES = {
            {0, 12, 0},
    };

    static final int[][] ATTRIBUTE_REVERSE_753_OVERRIDES = {
    };
    // ATTRIBUTE_REVERSE_753: 753 entries=13 ranges=1 overrides=0

    static final String[] ATTRIBUTE_753_NAMES = {
            "generic.max_health", "generic.follow_range", "generic.knockback_resistance", "generic.movement_speed",
            "generic.flying_speed", "generic.attack_damage", "generic.attack_knockback", "generic.attack_speed",
            "generic.armor", "generic.armor_toughness", "generic.luck", "zombie.spawn_reinforcements",
            "horse.jump_strength",
    };

    static final int[][] ENCHANTMENT_753_RANGES = {
            {0, 37, 0},
    };

    static final int[][] ENCHANTMENT_753_OVERRIDES = {
    };
    // ENCHANTMENT_753: 754 entries=38 ranges=1 overrides=0

    static final int[][] ENCHANTMENT_REVERSE_753_RANGES = {
            {0, 37, 0},
    };

    static final int[][] ENCHANTMENT_REVERSE_753_OVERRIDES = {
    };
    // ENCHANTMENT_REVERSE_753: 753 entries=38 ranges=1 overrides=0

    static final int[][] BIOME_753_RANGES = {
            {0, 50, 0}, {129, 134, 0}, {155, 158, 0}, {160, 173, 0},
    };

    static final int[][] BIOME_753_OVERRIDES = {
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
    // BIOME_753: 754 entries=174 ranges=4 overrides=99

    static final int[][] BIOME_753_TO_754_RANGES = {
            {0, 50, 0}, {129, 134, 0}, {155, 158, 0}, {160, 173, 0},
    };

    static final int[][] BIOME_753_TO_754_OVERRIDES = {
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
    // BIOME_753_TO_754: 753 entries=174 ranges=4 overrides=99

    static final int[][] DAMAGE_TYPE_753_RANGES = {
            {0, 41, 0},
    };

    static final int[][] DAMAGE_TYPE_753_OVERRIDES = {
    };
    // DAMAGE_TYPE_753: 754 entries=42 ranges=1 overrides=0

    static final int[][] DAMAGE_TYPE_753_TO_754_RANGES = {
            {0, 41, 0},
    };

    static final int[][] DAMAGE_TYPE_753_TO_754_OVERRIDES = {
    };
    // DAMAGE_TYPE_753_TO_754: 753 entries=42 ranges=1 overrides=0

}
