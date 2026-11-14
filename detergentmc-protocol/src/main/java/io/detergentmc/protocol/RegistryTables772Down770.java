package io.detergentmc.protocol;

/**
 * 772（1.21.7／1.21.8）のレジストリ対応表のうち、離れた組み合わせ
 * （772→770）の下向きの合成表（区間＋上書きの生の表）。
 *
 * <p>{@link RegistryTables772} から分離した。理由はバイトコードの制限：
 * 下向きの合成表を RegistryTables772 に置くと静的初期化子
 * （{@code <clinit>}）が 64KB を超えてコンパイルできなくなる
 * （\"code too large\"）ため。上向きの合成表は別クラス
 * {@link RegistryTables772ComposedUp} にある。772→770 の表だけを
 * このクラスに置く（他の下向きの表も同様に 1 クラスずつ分けた）。
 * このクラスの表は {@link RegistryRemapper} から static import で
 * 同じ名前のまま参照する。
 *
 * <p>表の内容と検証は {@code detergentmc-protocol/tools/} を参照：
 * 生成は {@code generate-remap-tables.py --target 772}、
 * 検証は {@code verify-772-tables.py}。
 */
final class RegistryTables772Down770 {

    private RegistryTables772Down770() {
    }

    static final int[][] BLOCK_STATE_772_TO_770_RANGES = {
            {0, 13825, 0}, {13858, 27945, -32},
    };

    static final int[][] BLOCK_STATE_772_TO_770_OVERRIDES = {
            {13826, 1}, {13827, 1}, {13828, 1}, {13829, 1}, {13830, 1}, {13831, 1}, {13832, 1}, {13833, 1},
            {13834, 1}, {13835, 1}, {13836, 1}, {13837, 1}, {13838, 1}, {13839, 1}, {13840, 1}, {13841, 1},
            {13842, 1}, {13843, 1}, {13844, 1}, {13845, 1}, {13846, 1}, {13847, 1}, {13848, 1}, {13849, 1},
            {13850, 1}, {13851, 1}, {13852, 1}, {13853, 1}, {13854, 1}, {13855, 1}, {13856, 1}, {13857, 1},
    };
    // BLOCK_STATE_772_TO_770: entries=27946 ranges=2 overrides=32

    static final int[][] ITEM_772_TO_770_RANGES = {
            {0, 618, 0}, {620, 801, -1}, {818, 1110, -17}, {1112, 1255, -18}, {1257, 1268, -19}, {1270, 1415, -20},
    };

    static final int[][] ITEM_772_TO_770_OVERRIDES = {
            {619, 1}, {802, 1}, {803, 1}, {804, 1}, {805, 1}, {806, 1}, {807, 1}, {808, 1},
            {809, 1}, {810, 1}, {811, 1}, {812, 1}, {813, 1}, {814, 1}, {815, 1}, {816, 1},
            {817, 1}, {1111, 1}, {1256, 1}, {1269, 1},
    };
    // ITEM_772_TO_770: entries=1416 ranges=6 overrides=20

    static final int[][] ENTITY_TYPE_772_TO_770_RANGES = {
            {0, 55, 0}, {57, 150, -1},
    };

    static final int[][] ENTITY_TYPE_772_TO_770_OVERRIDES = {
            {56, 94},
    };
    // ENTITY_TYPE_772_TO_770: entries=151 ranges=2 overrides=1

    static final int[][] SOUND_772_TO_770_RANGES = {
            {0, 451, 0}, {460, 464, -8}, {466, 626, -9}, {631, 698, -13}, {703, 733, -17}, {738, 765, -21},
            {767, 837, -22}, {841, 853, -23}, {855, 938, -24}, {940, 951, -25}, {953, 1205, -26}, {1206, 1255, -25},
            {1256, 1296, -24}, {1298, 1726, -25},
    };

    static final int[][] SOUND_772_TO_770_OVERRIDES = {
            {452, 1417}, {453, 1417}, {454, 1417}, {455, 1417}, {456, 1417}, {457, 1417}, {458, 1417}, {459, 1417},
            {465, 1231}, {627, 1417}, {628, 1417}, {629, 1417}, {630, 1417}, {699, 1417}, {700, 1417}, {701, 1417},
            {702, 1417}, {734, 1417}, {735, 1417}, {736, 1417}, {737, 1417}, {766, 1417}, {838, 1417}, {839, 817},
            {840, 816}, {854, 1417}, {939, 1417}, {952, 1417}, {1297, 1417}, {1727, 1417},
    };
    // SOUND_772_TO_770: entries=1728 ranges=14 overrides=30

    static final int[][] PARTICLE_772_TO_770_RANGES = {
            {0, 113, 0},
    };

    static final int[][] PARTICLE_772_TO_770_OVERRIDES = {
    };
    // PARTICLE_772_TO_770: entries=114 ranges=1 overrides=0

    static final int[][] BLOCK_772_TO_770_RANGES = {
            {0, 714, 0}, {716, 1104, -1},
    };

    static final int[][] BLOCK_772_TO_770_OVERRIDES = {
            {715, 1},
    };
    // BLOCK_772_TO_770: entries=1105 ranges=2 overrides=1

    static final int[][] MENU_772_TO_770_RANGES = {
            {0, 24, 0},
    };

    static final int[][] MENU_772_TO_770_OVERRIDES = {
    };
    // MENU_772_TO_770: entries=25 ranges=1 overrides=0

}
