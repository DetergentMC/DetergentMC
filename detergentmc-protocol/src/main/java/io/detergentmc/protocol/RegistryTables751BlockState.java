package io.detergentmc.protocol;

/**
 * 751（1.16.2）のレジストリ対応表のうち、753→751 の
 * ブロック状態の下向き表（区間＋上書きの生の表）。
 *
 * <p>{@link RegistryTables751} から分離した。理由は
 * バイトコードの制限：ブロック状態の下向き表を RegistryTables751 に置くと
 * 静的初期化子（{@code <clinit>}）が 64KB を超えてコンパイルできなくなる
 * 可能性があるため（753 の前例。753 は BLOCK_STATE_753_OVERRIDES
 * だけで 3,173 件あった。751 は恒等写像のため {0, 17111, 0} の
 * 1 区間だけで小さいが、753 と同じ構造を保つために分けてある）。
 * このクラスの表は {@link RegistryRemapper} から static import で
 * 同じ名前のまま参照する。
 *
 * <p>表の内容と検証は {@code detergentmc-protocol/tools/} を参照：
 * 生成は {@code gen-751-tables.py}、検証は {@code verify-751-tables.py}。
 */
final class RegistryTables751BlockState {

    private RegistryTables751BlockState() {
    }

    static final int[][] BLOCK_STATE_751_RANGES = {
            {0, 17111, 0},
    };

    static final int[][] BLOCK_STATE_751_OVERRIDES = {
    };
    // BLOCK_STATE_751: 753 entries=17112 ranges=1 overrides=0

}
