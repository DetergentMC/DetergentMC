#!/usr/bin/env python3
"""RegistryRemapper.java から 767 の表（*_767 RANGES／OVERRIDES、
SOUND_NAME_FALLBACKS_767*、ATTRIBUTE_*_NAMES）を抜き出し、別クラス
RegistryTables767.java へ移す（1回だけの作業用）。

理由：RegistryRemapper.java の静的初期化子（<clinit>）が 64KB の
バイトコード制限を超えた（767 の表を追加したため "code too large"）。
表の実体を別クラスへ移し、RegistryRemapper 側は static import で
同じ名前のまま参照する（展開済み表 *_767_MAP やアクセサの書き方は
変わらない）。

  - 移動する表は tools/generate-remap-tables.py --target 767 の出力
    （gen767.txt）に含まれる 116 件すべて。
  - RegistryTables767.java の表は package-private（static final）で、
    RegistryRemapper.java から import static で使う。
  - 検証は tools/verify-767-tables.py が RegistryTables767.java を
    読んで行う。
"""

import re
import sys
from pathlib import Path

SCRIPT_DIR = Path(__file__).resolve().parent
REPO_ROOT = SCRIPT_DIR.parent.parent
DATA_DIR = REPO_ROOT / "detergentmc-protocol" / "build" / "remap-data"
JAVA_FILE = (REPO_ROOT / "detergentmc-protocol" / "src" / "main" / "java"
             / "io" / "detergentmc" / "protocol" / "RegistryRemapper.java")
TABLES_FILE = (REPO_ROOT / "detergentmc-protocol" / "src" / "main" / "java"
               / "io" / "detergentmc" / "protocol" / "RegistryTables767.java")

HEADER = """package io.detergentmc.protocol;

/**
 * 767（1.21／1.21.1）のレジストリ対応表（区間＋上書きの生の表）。
 *
 * <p>{@link RegistryRemapper} から分離した。理由はバイトコードの制限：
 * 767 の表を RegistryRemapper に置くと静的初期化子（{@code <clinit>}）
 * が 64KB を超えてコンパイルできなくなる（"code too large"）ため。
 * このクラスの表は {@link RegistryRemapper} から static import で
 * 同じ名前のまま参照する（展開済み表 {@code *_767_MAP} やアクセサの
 * 書き方は変わらない）。
 *
 * <p>表の内容と検証は {@code detergentmc-protocol/tools/} を参照：
 * 生成は {@code generate-remap-tables.py --target 767}、
 * 検証は {@code verify-767-tables.py}。
 */
final class RegistryTables767 {

    private RegistryTables767() {
    }

"""


def main():
    gen_text = (DATA_DIR / "gen767.txt").read_text(encoding="utf-8")
    pattern = re.compile(
        r"(    private static final (?:int\[\]\[\]|String\[\]\[\]|String\[\]) (\w+) = \{.*?\n    \};"
        r"(?:\n    // [^\n]*)?)", re.DOTALL)
    tables = {m.group(2): m.group(1) for m in pattern.finditer(gen_text)}
    print(f"生成出力の表: {len(tables)} 件")
    if TABLES_FILE.exists():
        print("RegistryTables767.java は既にある", file=sys.stderr)
        return 1

    java = JAVA_FILE.read_text(encoding="utf-8")
    missing = [n for n in tables
               if not re.search(r"(?:int\[\]\[\]|String\[\]\[\]|String\[\]) "
                                + re.escape(n) + r" =", java)]
    if missing:
        print(f"RegistryRemapper.java に無い表: {missing}", file=sys.stderr)
        return 1

    # RegistryTables767.java を作る。private static final → static final
    # （RegistryRemapper から読むため）に変え、順序は生成出力のまま。
    out = [HEADER]
    for name, block in tables.items():
        out.append(block.replace("    private static final ",
                                 "    static final ", 1))
        out.append("")
    out.append("}\n")
    TABLES_FILE.write_text("\n".join(out), encoding="utf-8")

    # RegistryRemapper.java から表の宣言を消す。宣言の直後の
    # 「// NAME: ...」コメント行も一緒に消す。
    removed = 0
    for name, block in tables.items():
        java2 = java.replace(block + "\n", "", 1)
        if java2 == java:
            print(f"削除できなかった表: {name}", file=sys.stderr)
            return 1
        java = java2
        removed += 1

    # static import を追加する（同じパッケージだが static フィールドは
    # 修飾なしでは読めないため）。
    anchor = "import java.util.logging.Logger;\n"
    if anchor not in java:
        print("import のアンカーが見つからない", file=sys.stderr)
        return 1
    java = java.replace(anchor,
                        anchor + "\nimport static io.detergentmc.protocol.RegistryTables767.*;\n",
                        1)
    JAVA_FILE.write_text(java, encoding="utf-8")
    print(f"RegistryTables767.java へ {removed} 件の表を移動した")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
