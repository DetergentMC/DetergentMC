#!/usr/bin/env python3
"""tools/gen-754-tables.py の出力を RegistryTables754.java へ書き出す
（1回だけの作業用）。

  - 生成出力（gen754.txt。gen-754-tables.py の出力）に含まれる 754 の
    表すべて（755→754 の隣接表 7種＋ITEM_REVERSE_754、754→755 の
    反転表 7種、SOUND_NAME_FALLBACKS_754*、ATTRIBUTE_754*、
    ENCHANTMENT_754*、BIOME_754*、DAMAGE_TYPE_754*）を
    RegistryTables754.java と RegistryTables754BlockState.java の
    2ファイルに書く。
  - 分割の理由はバイトコードの制限：754 の表は合計 4,753 件で、
    1 ファイルにまとめると静的初期化子（{@code <clinit>}）が 64KB を
    超えて "code too large" でコンパイルできない（実測。755 は 227 件で
    収まったが、754 は 1.17 の新規ブロック 136 種分の代替で
    BLOCK_STATE_754_OVERRIDES だけで 3,173 件ある）。
    773／774／775 の前例（RegistryTables775Down763BlockState.java など）
    にならい、ブロック状態の下向き表（BLOCK_STATE_754_RANGES・
    BLOCK_STATE_754_OVERRIDES、計 3,231 件）だけを別クラスに分ける。
    残り 1,522 件は RegistryTables754.java に置く。
    表名はどちらのファイルでも同じで、RegistryRemapper.java からは
    両方を static import する。
  - 754 も最古側のため合成表を作らない。
    754↔763〜775 は 754→755→756→757→759→760→761→762→763→合成表の
    隣接連鎖で到達する。
  - 表は package-private（static final）で、RegistryRemapper.java
    から import static で使う（RegistryTables756.java と同じ方針）。
  - RegistryTables754.java／RegistryTables754BlockState.java が
    既にある場合は何もせず終了コード 1 で失敗する（二重作成を防ぐ）。
  - 作成後、tools/verify-754-tables.py で表が生成出力と一致する
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
TABLES754_FILE = PKG / "RegistryTables754.java"
TABLES754_BLOCKSTATE_FILE = PKG / "RegistryTables754BlockState.java"
# ブロック状態の下向き表だけ別クラスに分ける（<clinit> 64KB 対策。
# 773／774／775 の *BlockState.java と同じ方式）。
BLOCKSTATE_TABLES = {"BLOCK_STATE_754_RANGES", "BLOCK_STATE_754_OVERRIDES"}

HEADER = """package io.detergentmc.protocol;

/**
 * 754（1.16.5）のレジストリ対応表（区間＋上書きの生の表）。
 *
 * <p>755（1.17）との隣接表だけを置く。754 は最古バージョンのため
 * 離れた組み合わせの合成表は作らない（763〜775 の組が合成表を持つ
 * のとは違い、759〜762 の組は隣接表の連鎖だけを使う設計のため。
 * 754↔763〜775 は 754→755→756→757→759→760→761→762→763→合成表の連鎖で到達する）。
 * 755→754 の順向き表（*_754_*）と 754→755 の反転表（*_754_TO_755_*）
 * を両方置く。反転表は名前の真値（同名のものを優先）で作ってあり、
 * {@link RegistryRemapper#invert} の first-wins と違う箇所は
 * tools/maps754-corrections.json の補正（UP_754_CORRECTIONS）で
 * 埋める（補正はブロック状態 46 件・アイテム 5 件・音 35 件。
 * レールの waterlogged・銅系の代替などが first-wins と食い違う）。
 *
 * <p>バイオームの id は 1.16.5 が 0〜173（79 件）、1.17 が 0〜175（81 件）の
 * 非連番（旧来の数値バイオームID）。0〜173 は同じ id・同じ名前。
 * 順向き表（BIOME_754）は 755 側 176 件（id 0〜175。dripstone_caves・
 * lush_caves は平原（754 id 1）へ落とす）、反転表
 * （BIOME_754_TO_755）は 754 側 174 件（id 0〜173。隙間の id は
 * 平原（755 id 1）で埋める）。ワイヤ上の id がこの通りであることは
 * Join Game の dimensionCodec との突き合わせで確認する
 * （tools/gen-754-tables.py の §11.6 相当の Join Game codec 確認を参照。
 * 755 では実バニラ 1.17 の生キャプチャで確認済み）。
 *
 * <p>{@link RegistryRemapper} から分離した。理由はバイトコードの制限：
 * 754 の表を RegistryRemapper に置くと静的初期化子（{@code <clinit>}）
 * が 64KB を超えてコンパイルできなくなる（"code too large"）ため。
 * このクラスの表は {@link RegistryRemapper} から static import で
 * 同じ名前のまま参照する（展開済み表 {@code *_754_MAP} やアクセサの
 * 書き方は変わらない）。
 *
 * <p>表の内容と検証は {@code detergentmc-protocol/tools/} を参照：
 * 生成は {@code gen-754-tables.py}、検証は {@code verify-754-tables.py}。
 */
final class RegistryTables754 {

    private RegistryTables754() {
    }

"""

BLOCKSTATE_HEADER = """package io.detergentmc.protocol;

/**
 * 754（1.16.5）のレジストリ対応表のうち、755→754 の
 * ブロック状態の下向き表（区間＋上書きの生の表）。
 *
 * <p>{@link RegistryTables754} から分離した。理由は
 * バイトコードの制限：ブロック状態の下向き表を RegistryTables754 に置くと
 * 静的初期化子（{@code <clinit>}）が 64KB を超えてコンパイルできなくなる
 * （"code too large"）ため。755→754 のブロック状態は 20,342 件と件数
 * が多く、上書きも 3,173 件ある。このクラスの表は
 * {@link RegistryRemapper} から static import で同じ名前のまま参照する。
 *
 * <p>表の内容と検証は {@code detergentmc-protocol/tools/} を参照：
 * 生成は {@code gen-754-tables.py}、検証は {@code verify-754-tables.py}。
 */
final class RegistryTables754BlockState {

    private RegistryTables754BlockState() {
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
    if TABLES754_FILE.exists() or TABLES754_BLOCKSTATE_FILE.exists():
        print("RegistryTables754.java／RegistryTables754BlockState.java が"
              f"既にある。何もしない: {TABLES754_FILE}", file=sys.stderr)
        return 1
    gen_file = DATA_DIR / "gen754.txt"
    if not gen_file.exists():
        print(f"{gen_file} が無い。先に tools/gen-754-tables.py を実行すること",
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
        # private を外して package-private にする（756 と同じ）。
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
    TABLES754_FILE.write_text(
        HEADER + "\n\n".join(main_decls) + "\n\n}\n", encoding="utf-8")
    TABLES754_BLOCKSTATE_FILE.write_text(
        BLOCKSTATE_HEADER + "\n\n".join(blockstate_decls) + "\n\n}\n",
        encoding="utf-8")
    print(f"RegistryTables754.java を書き出した: {len(main_decls)} 件の表")
    print(f"RegistryTables754BlockState.java を書き出した: "
          f"{len(blockstate_decls)} 件の表")
    for name in tables:
        print(f"  {name}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
