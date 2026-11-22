package io.detergentmc.protocol;

/**
 * 776（26.2）のレジストリ対応表のうち、776→770 の
 * ブロック状態の下向きの合成表（区間＋上書きの生の表）。
 *
 * <p>{@link RegistryTables776Down770} から分離した。理由は
 * バイトコードの制限：ブロック状態の下向き表を Down770 に置くと
 * 静的初期化子（{@code <clinit>}）が 64KB を超えてコンパイルできなくなる
 * （\"code too large\"）ため。776→770 のブロック状態は 32,366 件と件数
 * が多く、上書きも 4,473 件ある。上書きは
 * RegistryTables776Down770BlockStateOverrides*
 * に分けてあり、このクラスの BLOCK_STATE_776_TO_770_OVERRIDES は
 * RegistryRemapper.concatTables() で連結して作る。このクラスの表は
 * {@link RegistryRemapper} と {@link RegistryTables776Expanded}
 * から static import で同じ名前のまま参照する。
 *
 * <p>表の内容と検証は {@code detergentmc-protocol/tools/} を参照：
 * 生成は {@code generate-remap-tables.py --target 776}、
 * 検証は {@code verify-776-tables.py}。
 */
final class RegistryTables776Down770BlockState {

    private RegistryTables776Down770BlockState() {
    }

    static final int[][] BLOCK_STATE_776_TO_770_RANGES = {
            {0, 1380, 0}, {1581, 2321, -200}, {2323, 2599, -201}, {3368, 7010, -969}, {7016, 7989, -974}, {8246, 8251, -1230},
            {8300, 10641, -1278}, {10643, 15104, -1279}, {15137, 20844, -1311}, {20877, 24686, -1343}, {27160, 27785, -3816}, {27790, 27791, -3820},
            {27808, 27887, -3584}, {27888, 27967, -3744}, {27968, 28047, -3904}, {28048, 28127, -4064}, {28128, 28207, -3552}, {28208, 28287, -3712},
            {28288, 28367, -3872}, {28368, 28447, -4032}, {28448, 28453, -4126}, {28454, 28459, -4138}, {28460, 28465, -4150}, {28466, 28471, -4162},
            {28472, 28477, -3798}, {28478, 28483, -3810}, {28484, 28489, -3822}, {28490, 28495, -3834}, {28496, 28623, -3816}, {28624, 28687, -3752},
            {28688, 28751, -3880}, {28752, 28879, -3816}, {28880, 28943, -3752}, {28944, 29007, -3880}, {29008, 29135, -3816}, {29136, 29199, -3752},
            {29200, 29263, -3880}, {29264, 29391, -3816}, {29392, 29455, -3752}, {29456, 29519, -3880}, {29520, 29567, -3816}, {30016, 30039, -4264},
            {30209, 30228, -4433}, {30249, 32365, -4452},
    };

    static final int[][] BLOCK_STATE_776_TO_770_OVERRIDES = RegistryRemapper.concatTables(
            RegistryTables776Down770BlockStateOverridesA.BLOCK_STATE_776_TO_770_OVERRIDES_A,
            RegistryTables776Down770BlockStateOverridesB.BLOCK_STATE_776_TO_770_OVERRIDES_B);
    // BLOCK_STATE_776_TO_770: entries=32366 ranges=44 overrides=4473

}
