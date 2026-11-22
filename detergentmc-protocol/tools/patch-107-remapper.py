"""RegistryRemapper に 107（1.9）の写像と PROTOCOL_107 定数を追加する。

107 のレジストリは 108 と完全同一（Stage A 実測：minecraft-data の
pc/1.9 が両版の ground truth で、ブロック 214 種・アイテム 365 種・
エンティティ・音・パーティクル・バイオームに差分なし。1.9 と
1.9.1 の jar の en_US.lang 差分は属性 armorToughness の追加と
文言修正のみで、レジストリ登録名に差分は無い。
tools/packet-ids-107.py で照合）。そのため 107 の写像は 108 の
節を写して 108 の写像へ全面委譲する形で生成する（108 の写像は
109 へ、109 は 110 へ委譲している）。

属性 generic.armorToughness は 108 で追加された名前文字列だが、
この節の写像は数値 ID の対応だけを扱い、属性名は含まない
（属性パケットの名前の扱いは PlayTranslator／ProxySession 側）。

使い方：
  python3 tools/patch-107-remapper.py
  冪等ではない。git diff で確認すること。
"""

import sys
from pathlib import Path

F = (Path(__file__).parent.parent / "src" / "main" / "java"
     / "io" / "detergentmc" / "protocol" / "RegistryRemapper.java")
src = F.read_text(encoding="utf-8")

HEADER_107 = """    // 107 ⇔ 758（1.9 ⇔ 1.18.2）
    // 107 のレジストリは 108 と完全同一（Stage A 実測：minecraft-data
    // の pc/1.9 が 1.9 系共通の ground truth で、ブロック・アイテム・
    // エンティティ・音・パーティクル・バイオームに差分なし。実
    // 1.9／1.9.1 jar の en_US.lang 差分は属性 generic.armorToughness
    // の追加と文言修正のみ。tools/packet-ids-107.py で照合）。
    // そのため 107 の写像は 108 の写像へ全面委譲する。近似
    // （magma→ネザーラック等）も 108 と同じになる。ワイヤ形式の
    // 差分（Join Game の dimension が i8）は変換側で吸収する。
"""


def to107(block):
    # 委譲先（109）をプレースホルダに退避してから節自体の番号
    # （108 → 107）を差し替え、最後に委譲先を 108 に戻す。
    # 順序を間違えると委譲先まで 107 になって自己再帰になる。
    block = block.replace("To109(", "To@@@(")
    block = block.replace("109To758(", "@@@To758(")
    block = block.replace("biomeNames109()", "biomeNames@@@()")
    block = block.replace("108", "107")
    block = block.replace("@@@", "108")
    # 写した節の javadoc に残る旧い言及を 107 の実態に合わせる。
    block = block.replace("（110 と同一のため 110 の表をそのまま返す）",
                          "（108 と同一のため 108 の表をそのまま返す）")
    block = block.replace("（Stop Sound 用）。110 と同一。",
                          "（Stop Sound 用）。108 と同一。")
    return block


def main():
    global src
    if "PROTOCOL_107" in src or "mapBlockState758To107" in src:
        print("既に 107 の節がある。何もしない。")
        return 0

    # 定数
    anchor = """    /** 108（1.9.1）のプロトコル番号。 */
    public static final int PROTOCOL_108 = 108;
"""
    assert src.count(anchor) == 1
    src = src.replace(anchor, anchor + """
    /** 107（1.9）のプロトコル番号。 */
    public static final int PROTOCOL_107 = 107;
""")

    # 108 の節を抽出して 107 の節を生成し、108 の節の直後へ挿入する
    start = src.index("    // 108 ⇔ 758（1.9.1 ⇔ 1.18.2）")
    end = src.index("    // 393 ⇔ 758（1.13 ⇔ 1.18.2）")
    block108 = src[start:end]
    # ヘッダーコメント（最初の空行まで）を 107 用に差し替える
    head_end = block108.index("\n\n") + 2
    block107 = HEADER_107 + "\n" + to107(block108[head_end:])
    src = src[:end] + block107 + src[end:]
    F.write_text(src, encoding="utf-8")
    print("RegistryRemapper に 107 の写像（108へ全面委譲）と "
          "PROTOCOL_107 定数を追加した")
    return 0


if __name__ == "__main__":
    sys.exit(main())
