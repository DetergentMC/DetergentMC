#!/usr/bin/env python3
"""tools/gen-735-tables.py の出力を RegistryTables735.java へ書き出す
（1回だけの作業用）。

  - 生成出力（gen735.txt。gen-735-tables.py の出力）に含まれる 735 の
    表すべて（736→735 の隣接表 7種＋ITEM_REVERSE_735、735→736 の
    反転表 7種、SOUND_NAME_FALLBACKS_735*、ATTRIBUTE_735*、
    ENCHANTMENT_735*、BIOME_735*、DAMAGE_TYPE_735*）を
    RegistryTables735.java と RegistryTables735BlockState.java の
    2ファイルに書く。
  - 分割の理由はバイトコードの制限：753 の表は合計 4,753 件で、
    1 ファイルにまとめると静的初期化子（{@code <clinit>}）が 64KB を
    超えて "code too large" でコンパイルできない（実測。753 §12.5 参照）。
    735 の表は合計 50 表・実質の要素は少ない（ブロック状態の下向き表は
    1 区間・上書き 0 件で恒等写像）が、任務文の指示どおり、また 736 と同じ構造を
    保つため、ブロック状態の下向き表（BLOCK_STATE_735_RANGES・
    BLOCK_STATE_735_OVERRIDES）だけを別クラスに分ける。残りは
    RegistryTables735.java に置く。
    なお RegistryTables754BlockState.java の static 初期化子は
    64,678 バイトで残り 857 バイトしかないため、このファイルには
    絶対に追加しない（735 の表は新規ファイルに分ける）。
    表名はどちらのファイルでも同じで、RegistryRemapper.java からは
    両方を static import する。
  - 735 も最古側のため合成表を作らない。
    735↔763〜775 は 735→736→751→753→754→755→756→757→759→760→761→762→763→合成表の
    隣接連鎖で到達する。
  - 表は package-private（static final）で、RegistryRemapper.java
    から import static で使う（RegistryTables736.java と同じ方針）。
  - RegistryTables735.java／RegistryTables735BlockState.java が
    既にある場合は何もせず終了コード 1 で失敗する（二重作成を防ぐ）。
  - 作成後、tools/verify-735-tables.py で表が生成出力と一致する
    ことを確認すること。
"""

import re
import sys
from pathlib import Path

SCRIPT_DIR = Path(__file__).resolve().parent
REPO_ROOT = SCRIPT_DIR.parent.parent
DATA_DIR = REPO_ROOT / "detergentmc-protocol" / "build" / "remap-data"
PKG = (REPO_ROOT / "detergentmc-protocol" / "src" / "main" / "java"
       / "io" / "detergentmc" / "protocol")
TABLES735_FILE = PKG / "RegistryTables735.java"
TABLES735_BLOCKSTATE_FILE = PKG / "RegistryTables735BlockState.java"
# ブロック状態の下向き表だけ別クラスに分ける（<clinit> 64KB 対策。
# 773／774／775／736 の *BlockState.java と同じ方式）。
BLOCKSTATE_TABLES = {"BLOCK_STATE_735_RANGES", "BLOCK_STATE_735_OVERRIDES"}

HEADER = """package io.detergentmc.protocol;

/**
 * 735（1.16）のレジストリ対応表（区間＋上書きの生の表）。
 *
 * <p>736（1.16.1）との隣接表だけを置く。735 は最古バージョンのため
 * 離れた組み合わせの合成表は作らない（763〜775 の組が合成表を持つ
 * のとは違い、759〜762 の組は隣接表の連鎖だけを使う設計のため。
 * 735↔763〜775 は 735→736→751→753→754→755→756→757→759→760→761→762→763→合成表の連鎖で到達する）。
 * 736→735 の順向き表（*_735_*）と 735→736 の反転表（*_735_TO_736_*）
 * を両方置く。反転表は名前の真値（同名のものを優先）で作ってあり、
 * 736 と 735 のレジストリ内容は同一のため（実バニラの reports が
 * バイト同一）、順向き表・反転表とも全種恒等写像。補正は 0 件
 * （tools/maps735-corrections.json）。
 *
 * <p>バイオームの id は 1.16／1.16.1 とも 0〜173（79 件）の
 * 非連番（旧来の数値バイオームID）。0〜173 は同じ id・同じ名前。
 * 順向き表（BIOME_735）は 736 側 174 件（id 0〜173。隙間の id は
 * 平原（735 id 1）で埋める）、反転表
 * （BIOME_735_TO_736）も 735 側 174 件（id 0〜173。隙間の id は
 * 平原（736 id 1）で埋める）。ワイヤ上の id がこの通りであることは
 * Join Game の dimensionCodec との突き合わせで確認する
 * （tools/gen-735-tables.py の §11.6 相当の Join Game codec 確認を参照。
 * 736 では実バニラ 1.16.1 の生キャプチャで確認済み）。
 *
 * <p>{@link RegistryRemapper} から分離した。理由はバイトコードの制限：
 * 735 の表を RegistryRemapper に置くと静的初期化子（{@code <clinit>}）
 * が 64KB を超えてコンパイルできなくなる（"code too large"）ため。
 * このクラスの表は {@link RegistryRemapper} から static import で
 * 同じ名前のまま参照する（展開済み表 {@code *_735_MAP} やアクセサの
 * 書き方は変わらない）。
 *
 * <p>表の内容と検証は {@code detergentmc-protocol/tools/} を参照：
 * 生成は {@code gen-735-tables.py}、検証は {@code verify-735-tables.py}。
 */
final class RegistryTables735 {

    private RegistryTables735() {
    }

"""

BLOCKSTATE_HEADER = """package io.detergentmc.protocol;

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

"""


def extract_tables(gen_text):
    """生成出力から {表名: 宣言ブロック全文} を抜き出す。"""
    tables = {}
    pattern = re.compile(
        r"(    private static final (?:int\[\]\[\]|String\[\]\[\]|String\[\]) (\w+) = \{.*?\n    \};"
        r"(?:\n    // [^\n]*)?)", re.DOTALL)
    for m in pattern.finditer(gen_text):
        tables[m.group(2)] = m.group(1)
    return tables


def main():
    if TABLES735_FILE.exists() or TABLES735_BLOCKSTATE_FILE.exists():
        print("RegistryTables735.java／RegistryTables735BlockState.java が"
              f"既にある。何もしない: {TABLES735_FILE}", file=sys.stderr)
        return 1
    gen_file = DATA_DIR / "gen735.txt"
    if not gen_file.exists():
        print(f"{gen_file} が無い。先に tools/gen-735-tables.py を実行すること",
              file=sys.stderr)
        return 1
    gen_text = gen_file.read_text(encoding="utf-8")
    tables = extract_tables(gen_text)
    print(f"生成出力の表: {len(tables)} 件")
    if not tables:
        print("表が1件も抜き出せなかった", file=sys.stderr)
        return 1
    main_decls = []
    blockstate_decls = []
    for name, block in tables.items():
        # private を外して package-private にする（736 と同じ）。
        decl = block.replace("    private static final ",
                             "    static final ", 1)
        if name in BLOCKSTATE_TABLES:
            blockstate_decls.append(decl)
        else:
            main_decls.append(decl)
    if set(BLOCKSTATE_TABLES) - set(tables):
        print(f"ブロック状態の下向き表が生成出力に無い："
              f"{sorted(set(BLOCKSTATE_TABLES) - set(tables))}", file=sys.stderr)
        return 1
    TABLES735_FILE.write_text(
        HEADER + "\n\n".join(main_decls) + "\n\n}\n", encoding="utf-8")
    TABLES735_BLOCKSTATE_FILE.write_text(
        BLOCKSTATE_HEADER + "\n\n".join(blockstate_decls) + "\n\n}\n",
        encoding="utf-8")
    print(f"RegistryTables735.java を書き出した: {len(main_decls)} 件の表")
    print(f"RegistryTables735BlockState.java を書き出した: "
          f"{len(blockstate_decls)} 件の表")
    for name in tables:
        print(f"  {name}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
