package io.detergentmc.protocol;

/**
 * 736（1.16.1）のレジストリ対応表のうち、751→736 の
 * ブロック状態の下向き表（区間＋上書きの生の表）。
 *
 * <p>{@link RegistryTables736} から分離した。理由は
 * バイトコードの制限：ブロック状態の下向き表を RegistryTables736 に置くと
 * 静的初期化子（{@code <clinit>}）が 64KB を超えてコンパイルできなくなる
 * 可能性があるため（753 の前例。753 は BLOCK_STATE_753_OVERRIDES
 * だけで 3,173 件あった。736 は 7 区間・上書き 0 件で小さいが、
 * 751 と同じ構造を保つために分けてある）。
 * このクラスの表は {@link RegistryRemapper} から static import で
 * 同じ名前のまま参照する。
 *
 * <p>表の内容と検証は {@code detergentmc-protocol/tools/} を参照：
 * 生成は {@code gen-736-tables.py}、検証は {@code verify-736-tables.py}。
 */
final class RegistryTables736BlockState {

    private RegistryTables736BlockState() {
    }

    static final int[][] BLOCK_STATE_736_RANGES = {
            {0, 4730, 0}, {4731, 4732, -2}, {4733, 14890, -4}, {14891, 14892, -5}, {14893, 14894, -6}, {14895, 14896, -7},
            {14897, 17111, -8},
    };

    static final int[][] BLOCK_STATE_736_OVERRIDES = {
    };
    // BLOCK_STATE_736: 751 entries=17112 ranges=7 overrides=0

}
