package io.detergentmc.protocol;

/**
 * 753（1.16.3）のレジストリ対応表のうち、754→753 の
 * ブロック状態の下向き表（区間＋上書きの生の表）。
 *
 * <p>{@link RegistryTables753} から分離した。理由は
 * バイトコードの制限：ブロック状態の下向き表を RegistryTables753 に置くと
 * 静的初期化子（{@code <clinit>}）が 64KB を超えてコンパイルできなくなる
 * 可能性があるため（754 の前例。754 は BLOCK_STATE_754_OVERRIDES
 * だけで 3,173 件あった。753 は恒等写像のため {0, 17111, 0} の
 * 1 区間だけで小さいが、754 と同じ構造を保つために分けてある）。
 * このクラスの表は {@link RegistryRemapper} から static import で
 * 同じ名前のまま参照する。
 *
 * <p>表の内容と検証は {@code detergentmc-protocol/tools/} を参照：
 * 生成は {@code gen-753-tables.py}、検証は {@code verify-753-tables.py}。
 */
final class RegistryTables753BlockState {

    private RegistryTables753BlockState() {
    }

    static final int[][] BLOCK_STATE_753_RANGES = {
            {0, 17111, 0},
    };

    static final int[][] BLOCK_STATE_753_OVERRIDES = {
    };
    // BLOCK_STATE_753: 754 entries=17112 ranges=1 overrides=0

}
