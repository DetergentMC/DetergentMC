#!/usr/bin/env python3
"""tools/gen-755-tables.py の出力を RegistryTables755.java へ書き出す
（1回だけの作業用）。

  - 生成出力（gen755.txt。gen-755-tables.py の出力）に含まれる 755 の
    表すべて（756→755 の隣接表 7種＋ITEM_REVERSE_755、755→756 の
    反転表 7種、SOUND_NAME_FALLBACKS_755*、ATTRIBUTE_755*、
    ENCHANTMENT_755*、BIOME_755*、DAMAGE_TYPE_755*）を
    RegistryTables755.java 1ファイルに書く。
  - 756 と同じく分割はしない。755 の表は合計で 756 と同程度と小さく、
    1 クラスの静的初期化子が 64KB を超える心配が無いため。
    （756 は合成表を作らないが、755 も最古側のため合成表を作らない。
    755↔763〜775 は 755→756→759→760→761→762→763→合成表の
    隣接連鎖で到達する。）
  - 表は package-private（static final）で、RegistryRemapper.java
    から import static で使う（RegistryTables756.java と同じ方針）。
  - RegistryTables755.java が既にある場合は何もせず終了コード 1
    で失敗する（二重作成を防ぐ）。
  - 作成後、tools/verify-755-tables.py で表が生成出力と一致する
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
TABLES755_FILE = PKG / "RegistryTables755.java"

HEADER = """package io.detergentmc.protocol;

/**
 * 755（1.17）のレジストリ対応表（区間＋上書きの生の表）。
 *
 * <p>756（1.17.1）との隣接表だけを置く。755 は最古バージョンのため
 * 離れた組み合わせの合成表は作らない（763〜775 の組が合成表を持つ
 * のとは違い、759〜762 の組は隣接表の連鎖だけを使う設計のため。
 * 755↔763〜775 は 755→756→759→760→761→762→763→合成表の連鎖で到達する）。
 * 756→755 の順向き表（*_755_*）と 755→756 の反転表（*_755_TO_756_*）
 * を両方置く。反転表は名前の真値（同名のものを優先）で作ってあり、
 * {@link RegistryRemapper#invert} の first-wins と違う箇所は
 * tools/maps755-corrections.json の補正（UP_755_CORRECTIONS）で
 * 埋める（補正は 0 件。1.17 と 1.17.1 のレジストリは同一のため）。
 *
 * <p>バイオームの id は 1.17 と 1.17.1 がどちらも 0〜175 の非連番
 * （旧来の数値バイオームID）。順向き表（BIOME_755）は 756 側 81 件、反転表
 * （BIOME_755_TO_756）は 755 側 176 件（id 0〜175。隙間の id は
 * 平原（756 id 1）で埋める）。ワイヤ上の id がこの通りであることは
 * Join Game の dimensionCodec との突き合わせで確認する
 * （tools/gen-755-tables.py の §11.6 相当の Join Game codec 確認を参照。
 * 756 では実バニラ 1.17.1 の生キャプチャで確認済み）。
 *
 * <p>{@link RegistryRemapper} から分離した。理由はバイトコードの制限：
 * 755 の表を RegistryRemapper に置くと静的初期化子（{@code <clinit>}）
 * が 64KB を超えてコンパイルできなくなる（"code too large"）ため。
 * このクラスの表は {@link RegistryRemapper} から static import で
 * 同じ名前のまま参照する（展開済み表 {@code *_755_MAP} やアクセサの
 * 書き方は変わらない）。
 *
 * <p>表の内容と検証は {@code detergentmc-protocol/tools/} を参照：
 * 生成は {@code gen-755-tables.py}、検証は {@code verify-755-tables.py}。
 */
final class RegistryTables755 {

    private RegistryTables755() {
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
    if TABLES755_FILE.exists():
        print("RegistryTables755.java が既にある。何もしない: "
              f"{TABLES755_FILE}", file=sys.stderr)
        return 1
    gen_file = DATA_DIR / "gen755.txt"
    if not gen_file.exists():
        print(f"{gen_file} が無い。先に tools/gen-755-tables.py を実行すること",
              file=sys.stderr)
        return 1
    gen_text = gen_file.read_text(encoding="utf-8")
    tables = extract_tables(gen_text)
    print(f"生成出力の表: {len(tables)} 件")
    if not tables:
        print("表が1件も抜き出せなかった", file=sys.stderr)
        return 1
    decls = []
    for name, block in tables.items():
        # private を外して package-private にする（756 と同じ）。
        decls.append(block.replace("    private static final ",
                                   "    static final ", 1))
    TABLES755_FILE.write_text(
        HEADER + "\n\n".join(decls) + "\n\n}\n", encoding="utf-8")
    print(f"RegistryTables755.java を書き出した: {len(decls)} 件の表")
    for name in tables:
        print(f"  {name}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
