package io.detergentmc.protocol;

/**
 * 735（1.16）のレジストリ対応表のうち、736→735 の
 * ブロック状態の下向き表（区間＋上書きの生の表）。
 *
 * <p>{@link RegistryTables735} から分離した。理由は
 * バイトコードの制限：ブロック状態の下向き表を RegistryTables735 に置くと
 * 静的初期化子（{@code <clinit>}）が 64KB を超えてコンパイルできなくなる
 * 可能性があるため（753 の前例。753 は BLOCK_STATE_753_OVERRIDES
 * だけで 3,173 件あった。735 は 1 区間・上書き 0 件で小さいが、
 * 736 と同じ構造を保つために分けてある）。
 * このクラスの表は {@link RegistryRemapper} から static import で
 * 同じ名前のまま参照する。
 *
 * <p>表の内容と検証は {@code detergentmc-protocol/tools/} を参照：
 * 生成は {@code gen-735-tables.py}、検証は {@code verify-735-tables.py}。
 */
final class RegistryTables735BlockState {

    private RegistryTables735BlockState() {
    }

    static final int[][] BLOCK_STATE_735_RANGES = {
            {0, 17103, 0},
    };

    static final int[][] BLOCK_STATE_735_OVERRIDES = {
    };
    // BLOCK_STATE_735: 736 entries=17104 ranges=1 overrides=0

}
