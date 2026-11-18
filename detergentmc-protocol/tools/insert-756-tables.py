#!/usr/bin/env python3
"""tools/gen-756-tables.py の出力を RegistryTables756.java へ書き出す
（1回だけの作業用）。

  - 生成出力（gen756.txt。gen-756-tables.py の出力）に含まれる 756 の
    表すべて（757→756 の隣接表 7種＋ITEM_REVERSE_756、756→757 の
    反転表 7種、SOUND_NAME_FALLBACKS_756*、ATTRIBUTE_756*、
    ENCHANTMENT_756*、BIOME_756*、DAMAGE_TYPE_756*）を
    RegistryTables756.java 1ファイルに書く。
  - 757 と同じく分割はしない。756 の表は合計で 757 と同程度と小さく、
    1 クラスの静的初期化子が 64KB を超える心配が無いため。
    （757 は合成表を作らないが、756 も最古側のため合成表を作らない。
    756↔763〜775 は 756→757→759→760→761→762→763→合成表の
    隣接連鎖で到達する。）
  - 表は package-private（static final）で、RegistryRemapper.java
    から import static で使う（RegistryTables757.java と同じ方針）。
  - RegistryTables756.java が既にある場合は何もせず終了コード 1
    で失敗する（二重作成を防ぐ）。
  - 作成後、tools/verify-756-tables.py で表が生成出力と一致する
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
TABLES756_FILE = PKG / "RegistryTables756.java"

HEADER = """package io.detergentmc.protocol;

/**
 * 756（1.17.1）のレジストリ対応表（区間＋上書きの生の表）。
 *
 * <p>757（1.18／1.18.1）との隣接表だけを置く。756 は最古バージョンのため
 * 離れた組み合わせの合成表は作らない（763〜775 の組が合成表を持つ
 * のとは違い、759〜762 の組は隣接表の連鎖だけを使う設計のため。
 * 756↔763〜775 は 756→757→759→760→761→762→763→合成表の連鎖で到達する）。
 * 757→756 の順向き表（*_756_*）と 756→757 の反転表（*_756_TO_757_*）
 * を両方置く。反転表は名前の真値（同名のものを優先）で作ってあり、
 * {@link RegistryRemapper#invert} の first-wins と違う箇所は
 * tools/maps756-corrections.json の補正（UP_756_CORRECTIONS）で
 * 埋める（補正はパーティクル 1 件のみ）。
 *
 * <p>バイオームの id は 1.17.1 が 0〜175 の非連番（旧来の数値バイオームID）、
 * 1.18 が 0〜60 の連番。順向き表（BIOME_756）は 757 側 61 件、反転表
 * （BIOME_756_TO_757）は 756 側 176 件（id 0〜175。隙間の id は
 * 平原（757 id 1）で埋める）。ワイヤ上の id がこの通りであることは
 * 実バニラ 1.17.1 の Join Game dimensionCodec の生キャプチャで確認済み。
 *
 * <p>{@link RegistryRemapper} から分離した。理由はバイトコードの制限：
 * 756 の表を RegistryRemapper に置くと静的初期化子（{@code <clinit>}）
 * が 64KB を超えてコンパイルできなくなる（"code too large"）ため。
 * このクラスの表は {@link RegistryRemapper} から static import で
 * 同じ名前のまま参照する（展開済み表 {@code *_756_MAP} やアクセサの
 * 書き方は変わらない）。
 *
 * <p>表の内容と検証は {@code detergentmc-protocol/tools/} を参照：
 * 生成は {@code gen-756-tables.py}、検証は {@code verify-756-tables.py}。
 */
final class RegistryTables756 {

    private RegistryTables756() {
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
    if TABLES756_FILE.exists():
        print("RegistryTables756.java が既にある。何もしない: "
              f"{TABLES756_FILE}", file=sys.stderr)
        return 1
    gen_file = DATA_DIR / "gen756.txt"
    if not gen_file.exists():
        print(f"{gen_file} が無い。先に tools/gen-756-tables.py を実行すること",
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
        # private を外して package-private にする（757 と同じ）。
        decls.append(block.replace("    private static final ",
                                   "    static final ", 1))
    TABLES756_FILE.write_text(
        HEADER + "\n\n".join(decls) + "\n\n}\n", encoding="utf-8")
    print(f"RegistryTables756.java を書き出した: {len(decls)} 件の表")
    for name in tables:
        print(f"  {name}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
