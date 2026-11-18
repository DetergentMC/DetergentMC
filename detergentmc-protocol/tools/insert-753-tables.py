#!/usr/bin/env python3
"""tools/gen-753-tables.py の出力を RegistryTables753.java へ書き出す
（1回だけの作業用）。

  - 生成出力（gen753.txt。gen-753-tables.py の出力）に含まれる 753 の
    表すべて（754→753 の隣接表 7種＋ITEM_REVERSE_753、753→754 の
    反転表 7種、SOUND_NAME_FALLBACKS_753*、ATTRIBUTE_753*、
    ENCHANTMENT_753*、BIOME_753*、DAMAGE_TYPE_753*）を
    RegistryTables753.java と RegistryTables753BlockState.java の
    2ファイルに書く。
  - 分割の理由はバイトコードの制限：754 の表は合計 4,753 件で、
    1 ファイルにまとめると静的初期化子（{@code <clinit>}）が 64KB を
    超えて "code too large" でコンパイルできない（実測。754 §12.5 参照）。
    753 の表は恒等写像が中心で合計は 50 表・実質の要素は少ない
    （ブロック状態の下向き表も {0, 17111, 0} の 1 区間だけ）が、
    任務文の指示どおり、また 754 と同じ構造を保つため、ブロック状態の
    下向き表（BLOCK_STATE_753_RANGES・BLOCK_STATE_753_OVERRIDES）だけを
    別クラスに分ける。残りは RegistryTables753.java に置く。
    なお RegistryTables754BlockState.java の static 初期化子は
    64,678 バイトで残り 857 バイトしかないため、このファイルには
    絶対に追加しない（753 の表は新規ファイルに分ける）。
    表名はどちらのファイルでも同じで、RegistryRemapper.java からは
    両方を static import する。
  - 753 も最古側のため合成表を作らない。
    753↔763〜775 は 753→754→755→756→757→759→760→761→762→763→合成表の
    隣接連鎖で到達する。
  - 表は package-private（static final）で、RegistryRemapper.java
    から import static で使う（RegistryTables754.java と同じ方針）。
  - RegistryTables753.java／RegistryTables753BlockState.java が
    既にある場合は何もせず終了コード 1 で失敗する（二重作成を防ぐ）。
  - 作成後、tools/verify-753-tables.py で表が生成出力と一致する
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
TABLES753_FILE = PKG / "RegistryTables753.java"
TABLES753_BLOCKSTATE_FILE = PKG / "RegistryTables753BlockState.java"
# ブロック状態の下向き表だけ別クラスに分ける（<clinit> 64KB 対策。
# 773／774／775／754 の *BlockState.java と同じ方式）。
BLOCKSTATE_TABLES = {"BLOCK_STATE_753_RANGES", "BLOCK_STATE_753_OVERRIDES"}

HEADER = """package io.detergentmc.protocol;

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

"""

BLOCKSTATE_HEADER = """package io.detergentmc.protocol;

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
    if TABLES753_FILE.exists() or TABLES753_BLOCKSTATE_FILE.exists():
        print("RegistryTables753.java／RegistryTables753BlockState.java が"
              f"既にある。何もしない: {TABLES753_FILE}", file=sys.stderr)
        return 1
    gen_file = DATA_DIR / "gen753.txt"
    if not gen_file.exists():
        print(f"{gen_file} が無い。先に tools/gen-753-tables.py を実行すること",
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
        # private を外して package-private にする（754 と同じ）。
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
    TABLES753_FILE.write_text(
        HEADER + "\n\n".join(main_decls) + "\n\n}\n", encoding="utf-8")
    TABLES753_BLOCKSTATE_FILE.write_text(
        BLOCKSTATE_HEADER + "\n\n".join(blockstate_decls) + "\n\n}\n",
        encoding="utf-8")
    print(f"RegistryTables753.java を書き出した: {len(main_decls)} 件の表")
    print(f"RegistryTables753BlockState.java を書き出した: "
          f"{len(blockstate_decls)} 件の表")
    for name in tables:
        print(f"  {name}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
