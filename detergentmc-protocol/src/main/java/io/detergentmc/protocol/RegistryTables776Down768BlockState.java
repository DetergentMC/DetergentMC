package io.detergentmc.protocol;

/**
 * 776（26.2）のレジストリ対応表のうち、776→768 の
 * ブロック状態の下向きの合成表（区間＋上書きの生の表）。
 *
 * <p>{@link RegistryTables776Down768} から分離した。理由は
 * バイトコードの制限：ブロック状態の下向き表を Down768 に置くと
 * 静的初期化子（{@code <clinit>}）が 64KB を超えてコンパイルできなくなる
 * （\"code too large\"）ため。776→768 のブロック状態は 32,366 件と件数
 * が多く、上書きも 5,071 件ある。上書きは
 * RegistryTables776Down768BlockStateOverrides*
 * に分けてあり、このクラスの BLOCK_STATE_776_TO_768_OVERRIDES は
 * RegistryRemapper.concatTables() で連結して作る。このクラスの表は
 * {@link RegistryRemapper} と {@link RegistryTables776Expanded}
 * から static import で同じ名前のまま参照する。
 *
 * <p>表の内容と検証は {@code detergentmc-protocol/tools/} を参照：
 * 生成は {@code generate-remap-tables.py --target 776}、
 * 検証は {@code verify-776-tables.py}。
 */
final class RegistryTables776Down768BlockState {

    private RegistryTables776Down768BlockState() {
    }

    static final int[][] BLOCK_STATE_776_TO_768_RANGES = {
            {0, 1380, 0}, {1581, 2250, -200}, {2254, 2321, -203}, {2323, 2599, -204}, {3368, 3888, -972}, {3890, 3891, -973},
            {3896, 3897, -976}, {3902, 3903, -979}, {3906, 6944, -981}, {6946, 7010, -982}, {7016, 7989, -987}, {8246, 8251, -1243},
            {8300, 8517, -1291}, {8646, 8920, -1419}, {9334, 10641, -1832}, {10643, 15104, -1833}, {15137, 20844, -1865}, {20877, 21737, -1897},
            {21743, 24686, -1902}, {27160, 27785, -4375}, {27790, 27791, -4379}, {27808, 27887, -4143}, {27888, 27967, -4303}, {27968, 28047, -4463},
            {28048, 28127, -4623}, {28128, 28207, -4111}, {28208, 28287, -4271}, {28288, 28367, -4431}, {28368, 28447, -4591}, {28448, 28453, -4685},
            {28454, 28459, -4697}, {28460, 28465, -4709}, {28466, 28471, -4721}, {28472, 28477, -4357}, {28478, 28483, -4369}, {28484, 28489, -4381},
            {28490, 28495, -4393}, {28496, 28623, -4375}, {28624, 28687, -4311}, {28688, 28751, -4439}, {28752, 28879, -4375}, {28880, 28943, -4311},
            {28944, 29007, -4439}, {29008, 29135, -4375}, {29136, 29199, -4311}, {29200, 29263, -4439}, {29264, 29391, -4375}, {29392, 29455, -4311},
            {29456, 29519, -4439}, {29520, 29567, -4375}, {30016, 30039, -4823}, {30209, 30228, -4992}, {30249, 30322, -5011}, {30355, 32360, -5043},
    };

    static final int[][] BLOCK_STATE_776_TO_768_OVERRIDES = RegistryRemapper.concatTables(
            RegistryTables776Down768BlockStateOverridesA.BLOCK_STATE_776_TO_768_OVERRIDES_A,
            RegistryTables776Down768BlockStateOverridesB.BLOCK_STATE_776_TO_768_OVERRIDES_B,
            RegistryTables776Down768BlockStateOverridesC.BLOCK_STATE_776_TO_768_OVERRIDES_C);
    // BLOCK_STATE_776_TO_768: entries=32366 ranges=54 overrides=5071

}
