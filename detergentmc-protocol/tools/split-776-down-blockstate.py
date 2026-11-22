#!/usr/bin/env python3
"""RegistryTables776Down*BlockState.java の OVERRIDES を分割する。

背景：776→763 など下向きのブロック状態表は上書き（OVERRIDES）が
4,400〜5,900 件あり、1クラスの静的初期化子（<clinit>）が 64KB を
超えて "code too large" でコンパイルできない。775 の Down763 は
上書き 3,348 件で <clinit> が 63,036 バイト（残り 2,499 バイト）まで
迫っていたため、776 ではさらに増えて超えた。

対策：OVERRIDES を 2,500 件ごとの塊に分け、各塊を別クラス
RegistryTables776Down{s}BlockStateOverrides{A,B,C...} に置き、
本体の OVERRIDES フィールドは RegistryRemapper.concatTables() で
連結して作る。フィールド名・参照先（RegistryTables776Expanded の
expand() 呼び出し）は変わらない。RANGES は本体に残す。

対象：763〜772（773・774 は上書き 2,716 件で収まるため分割しない）。
"""
import re
import sys
from pathlib import Path

REPO = Path(__file__).resolve().parent.parent.parent
PKG = REPO / "detergentmc-protocol" / "src" / "main" / "java" / "io" / "detergentmc" / "protocol"
REMAPPER = PKG / "RegistryRemapper.java"
CHUNK = 2500
SUFFIXES = [str(s) for s in range(763, 773)]


def format_entries(entries):
    """(a, b) の list を 8 件ごとの行に整形する（生成スクリプトと同じ書式）。"""
    lines = []
    for i in range(0, len(entries), 8):
        chunk = entries[i:i + 8]
        lines.append("            " + ", ".join(f"{{{a}, {b}}}" for a, b in chunk) + ",")
    return "\n".join(lines)


def split_one(suffix):
    main_file = PKG / f"RegistryTables776Down{suffix}BlockState.java"
    text = main_file.read_text(encoding="utf-8")
    name = f"BLOCK_STATE_776_TO_{suffix}_OVERRIDES"
    # 既に分割済みの場合は javadoc の誤記だけ直して終える（再実行の安全策）。
    if f"static final int[][] {name} = concatTables(" in text:
        fixed = text.replace("776（26.1）のレジストリ対応表", "776（26.2）のレジストリ対応表")
        fixed = re.sub(r"776→763 のブロック状態は 32,366 件と件数",
                       f"776→{suffix} のブロック状態は 32,366 件と件数", fixed, count=1)
        if fixed != text:
            main_file.write_text(fixed, encoding="utf-8")
            print(f"{suffix}: 分割済みのため javadoc のみ修正")
        else:
            print(f"{suffix}: 分割済みのため何もしない")
        return
    m = re.search(re.escape(name) + r" = \{(.*?)\n    \};", text, re.DOTALL)
    assert m, f"{suffix}: OVERRIDES が見つからない"
    entries = [tuple(map(int, t))
               for t in re.findall(r"\{(-?\d+), (-?\d+)\}", m.group(1))]
    assert len(entries) > CHUNK, f"{suffix}: {len(entries)} 件しかない（分割不要のはず）"
    # javadoc の誤記を直す。生成スクリプトのテンプレートが 763 の文言
    # （776→763・上書き 5,854 件・26.1）を全ファイルに写しているため。
    text = text.replace("776（26.1）のレジストリ対応表", "776（26.2）のレジストリ対応表")
    text = re.sub(r"776→763 のブロック状態は 32,366 件と件数\n \* が多く、上書きも [\d,]+ 件ある。",
                  f"776→{suffix} のブロック状態は 32,366 件と件数\n"
                  f" * が多く、上書きも {len(entries):,} 件ある。",
                  text, count=1)
    chunks = [entries[i:i + CHUNK] for i in range(0, len(entries), CHUNK)]
    letters = "ABCDEFGHIJ"
    assert len(chunks) <= len(letters)
    part_names = []
    for idx, chunk in enumerate(chunks):
        letter = letters[idx]
        part_class = f"RegistryTables776Down{suffix}BlockStateOverrides{letter}"
        part_field = f"{name}_{letter}"
        part_names.append(f"{part_class}.{part_field}")
        header = f"""package io.detergentmc.protocol;

/**
 * 776→{suffix} のブロック状態の下向き表の上書き（OVERRIDES）の
 * {idx + 1} 番目の塊（{len(chunk)} 件）。
 *
 * <p>本体の {main_file.name} と合わせて {len(entries)} 件。
 * 1クラスに置くと静的初期化子（{{@code <clinit>}}）が 64KB を超えて
 * コンパイルできなくなる（"code too large"）ため分割してある。
 * 本体の {name} は
 * {{@link RegistryRemapper#concatTables(int[][]...)}}
 * で連結して作る。表の内容と検証は
 * {{@code detergentmc-protocol/tools/}} を参照：
 * 生成は {{@code generate-remap-tables.py --target 776}}、
 * 検証は {{@code verify-776-tables.py}}。
 */
final class {part_class} {{

    private {part_class}() {{
    }}

    static final int[][] {part_field} = {{
{format_entries(chunk)}
    }};
}}
"""
        (PKG / f"{part_class}.java").write_text(header, encoding="utf-8")
    # 本体の OVERRIDES を concatTables() に置き換える。
    # m はフィールド名から始まるため、宣言の先頭（static final int[][]）まで遡る。
    decl_start = text.rfind("    static final int[][] ", 0, m.start())
    assert decl_start >= 0, f"{suffix}: OVERRIDES の宣言開始が見つからない"
    concat = (f"    static final int[][] {name} = RegistryRemapper.concatTables(\n"
              + ",\n".join(f"            {p}" for p in part_names)
              + ");\n")
    text = text[:decl_start] + concat.rstrip("\n") + text[m.end():]
    # javadoc に分割の注記を追加。
    anchor = (f" * が多く、上書きも {len(entries):,} 件ある。このクラスの表は\n")
    assert anchor in text, f"{suffix}: javadoc のアンカーが見つからない"
    note = (f" * が多く、上書きも {len(entries):,} 件ある。上書きは\n"
            f" * RegistryTables776Down{suffix}BlockStateOverrides*\n"
            f" * に分けてあり、このクラスの {name} は\n"
            f" * RegistryRemapper.concatTables() で連結して作る。このクラスの表は\n")
    text = text.replace(anchor, note, 1)
    main_file.write_text(text, encoding="utf-8")
    print(f"{suffix}: OVERRIDES {len(entries)} 件を {len(chunks)} 塊に分けた")


def add_concat_tables():
    java = REMAPPER.read_text(encoding="utf-8")
    if "static int[][] concatTables(" in java:
        print("concatTables は既にある")
        return
    anchor = ("        for (int i = 0; i < toSize; i++) {\n"
              "            if (inverse[i] < 0) {\n"
              "                inverse[i] = fallback;\n"
              "            }\n"
              "        }\n"
              "        return inverse;\n"
              "    }\n")
    assert java.count(anchor) == 1, "invert() の末尾が見つからない"
    helper = (anchor
              + "\n"
              + "    /**\n"
              + "     * 複数の int[][] を1つに連結する。\n"
              + "     *\n"
              + "     * <p>Down*BlockState の OVERRIDES 分割用。776→763 など下向きの\n"
              + "     * ブロック状態表の上書きが 4,400 件を超え、1クラスの静的初期化子\n"
              + "     * （{@code <clinit>}）が 64KB を超えてコンパイルできなくなるため、\n"
              + "     * RegistryTables776Down*BlockStateOverrides* に分けてある。\n"
              + "     */\n"
              + "    static int[][] concatTables(int[][]... parts) {\n"
              + "        int total = 0;\n"
              + "        for (int[][] part : parts) {\n"
              + "            total += part.length;\n"
              + "        }\n"
              + "        int[][] result = new int[total][];\n"
              + "        int at = 0;\n"
              + "        for (int[][] part : parts) {\n"
              + "            System.arraycopy(part, 0, result, at, part.length);\n"
              + "            at += part.length;\n"
              + "        }\n"
              + "        return result;\n"
              + "    }\n")
    java = java.replace(anchor, helper, 1)
    REMAPPER.write_text(java, encoding="utf-8")
    print("RegistryRemapper.java に concatTables() を追加した")


def main():
    add_concat_tables()
    for suffix in SUFFIXES:
        split_one(suffix)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
