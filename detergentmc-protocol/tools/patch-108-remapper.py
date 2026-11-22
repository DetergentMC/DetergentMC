"""RegistryRemapper に 108（1.9.1）の写像と PROTOCOL_108 定数を追加する。

108 のレジストリは 109 と完全同一（Stage A 実測：両 jar の
エントリ一覧・非クラスファイル CRC が完全一致し、レジストリ
登録名の文字列に差分なし。tools/packet-ids-108.py で照合）。
そのため 108 の写像は 109 の節を写して 109 の写像へ全面委譲
する形で生成する（109 の写像は 110 へ委譲している）。

使い方：
  python3 tools/patch-108-remapper.py
  冪等ではない。git diff で確認すること。
"""

import sys
from pathlib import Path

F = (Path(__file__).parent.parent / "src" / "main" / "java"
     / "io" / "detergentmc" / "protocol" / "RegistryRemapper.java")
src = F.read_text(encoding="utf-8")

HEADER_108 = """    // 108 ⇔ 758（1.9.1 ⇔ 1.18.2）
    // 108 のレジストリは 109 と完全同一（Stage A 実測：実バニラ
    // 1.9.1／1.9.2 の両 jar のエントリ一覧が完全一致し、非クラス
    // ファイルは CRC まで全て一致。差分のあるクラス 11 件の
    // 文字列定数の差分は版数文字列とエラーメッセージ文言1件
    // だけ。tools/packet-ids-108.py で照合）。そのため 108 の
    // 写像は 109 の写像へ全面委譲する。108 と 109 はワイヤ形式
    // も完全同一で、近似（magma→ネザーラック等）も 109 と
    // 同じになる。
"""


def to108(block):
    # 委譲先（110）をプレースホルダに退避してから節自体の番号
    # （109 → 108）を差し替え、最後に委譲先を 109 に戻す。
    # 順序を間違えると委譲先まで 108 になって自己再帰になる。
    block = block.replace("To110(", "To@@@(")
    block = block.replace("110To758(", "@@@To758(")
    block = block.replace("biomeNames110()", "biomeNames@@@()")
    block = block.replace("109", "108")
    block = block.replace("@@@", "109")
    return block


def main():
    global src
    if "PROTOCOL_108" in src or "mapBlockState758To108" in src:
        print("既に 108 の節がある。何もしない。")
        return 0

    # 定数
    anchor = """    /** 109（1.9.2）のプロトコル番号。 */
    public static final int PROTOCOL_109 = 109;
"""
    assert src.count(anchor) == 1
    src = src.replace(anchor, anchor + """
    /** 108（1.9.1）のプロトコル番号。 */
    public static final int PROTOCOL_108 = 108;
""")

    # 109 の節を抽出して 108 の節を生成し、109 の節の直後へ挿入する
    start = src.index("    // 109 ⇔ 758（1.9.2 ⇔ 1.18.2）")
    end = src.index("    // 393 ⇔ 758（1.13 ⇔ 1.18.2）")
    block109 = src[start:end]
    # ヘッダーコメント（最初の空行まで）を 108 用に差し替える
    head_end = block109.index("\n\n") + 2
    block108 = HEADER_108 + "\n" + to108(block109[head_end:])
    src = src[:end] + block108 + src[end:]
    F.write_text(src, encoding="utf-8")
    print("RegistryRemapper に 108 の写像（109へ全面委譲）と "
          "PROTOCOL_108 定数を追加した")
    return 0


if __name__ == "__main__":
    sys.exit(main())
