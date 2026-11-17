package io.detergentmc.protocol;

/**
 * 776（26.2）のレジストリ対応表のうち、776→771 の
 * ブロック状態の下向きの合成表（区間＋上書きの生の表）。
 *
 * <p>{@link RegistryTables776Down771} から分離した。理由は
 * バイトコードの制限：ブロック状態の下向き表を Down771 に置くと
 * 静的初期化子（{@code <clinit>}）が 64KB を超えてコンパイルできなくなる
 * （\"code too large\"）ため。776→771 のブロック状態は 32,366 件と件数
 * が多く、上書きも 4,441 件ある。上書きは
 * RegistryTables776Down771BlockStateOverrides*
 * に分けてあり、このクラスの BLOCK_STATE_776_TO_771_OVERRIDES は
 * RegistryRemapper.concatTables() で連結して作る。このクラスの表は
 * {@link RegistryRemapper} と {@link RegistryTables776Expanded}
 * から static import で同じ名前のまま参照する。
 *
 * <p>表の内容と検証は {@code detergentmc-protocol/tools/} を参照：
 * 生成は {@code generate-remap-tables.py --target 776}、
 * 検証は {@code verify-776-tables.py}。
 */
final class RegistryTables776Down771BlockState {

    private RegistryTables776Down771BlockState() {
    }

    static final int[][] BLOCK_STATE_776_TO_771_RANGES = {
            {0, 1380, 0}, {1581, 2321, -200}, {2323, 2599, -201}, {3368, 7010, -969}, {7016, 7989, -974}, {8246, 8251, -1230},
            {8300, 10641, -1278}, {10643, 20844, -1279}, {20877, 24686, -1311}, {27160, 27785, -3784}, {27790, 27791, -3788}, {27808, 27887, -3552},
            {27888, 27967, -3712}, {27968, 28047, -3872}, {28048, 28127, -4032}, {28128, 28207, -3520}, {28208, 28287, -3680}, {28288, 28367, -3840},
            {28368, 28447, -4000}, {28448, 28453, -4094}, {28454, 28459, -4106}, {28460, 28465, -4118}, {28466, 28471, -4130}, {28472, 28477, -3766},
            {28478, 28483, -3778}, {28484, 28489, -3790}, {28490, 28495, -3802}, {28496, 28623, -3784}, {28624, 28687, -3720}, {28688, 28751, -3848},
            {28752, 28879, -3784}, {28880, 28943, -3720}, {28944, 29007, -3848}, {29008, 29135, -3784}, {29136, 29199, -3720}, {29200, 29263, -3848},
            {29264, 29391, -3784}, {29392, 29455, -3720}, {29456, 29519, -3848}, {29520, 29567, -3784}, {30016, 30039, -4232}, {30209, 30228, -4401},
            {30249, 32365, -4420},
    };

    static final int[][] BLOCK_STATE_776_TO_771_OVERRIDES = RegistryRemapper.concatTables(
            RegistryTables776Down771BlockStateOverridesA.BLOCK_STATE_776_TO_771_OVERRIDES_A,
            RegistryTables776Down771BlockStateOverridesB.BLOCK_STATE_776_TO_771_OVERRIDES_B);
    // BLOCK_STATE_776_TO_771: entries=32366 ranges=43 overrides=4441

}
