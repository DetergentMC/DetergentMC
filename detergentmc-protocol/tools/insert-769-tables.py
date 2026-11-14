#!/usr/bin/env python3
"""tools/generate-remap-tables.py --target 769 の出力を
RegistryTables769.java へ書き出す（1回だけの作業用）。

  - 生成出力（gen769.txt。--target 769 の --out 先）に含まれる
    769 の表すべて（*_769 RANGES／OVERRIDES、合成表 763↔769・
    764↔769・765↔769・766↔769・767↔769、SOUND_NAME_FALLBACKS_769*、
    ATTRIBUTE_769_NAMES、ENCHANTMENT_769*）
    を RegistryTables769.java（隣接表・音の名前・属性・エンチャント）、
    RegistryTables769ComposedUp.java（上向きの合成表）、
    RegistryTables769Down763／764／765／766／767.java（下向きの合成表）に分けて書く。
    分ける理由はバイトコードの制限：1つのクラスの静的初期化子が
    64KB を超えるとコンパイルできない（\"code too large\"）。
  - 表は package-private（static final）で、RegistryRemapper.java
    から import static で使う（RegistryTables767.java と同じ方針。
    RegistryRemapper に直接置くと <clinit> が 64KB を超えるため）。
  - RegistryTables769.java が既にある場合は何もせず終了コード 1
    で失敗する（二重作成を防ぐ）。
  - 作成後、tools/verify-769-tables.py で表が生成出力と一致する
    ことを確認すること。
"""

import re
import sys
from pathlib import Path

SCRIPT_DIR = Path(__file__).resolve().parent
REPO_ROOT = SCRIPT_DIR.parent.parent
DATA_DIR = REPO_ROOT / "detergentmc-protocol" / "build" / "remap-data"
TABLES769_FILE = (REPO_ROOT / "detergentmc-protocol" / "src" / "main" / "java"
                  / "io" / "detergentmc" / "protocol" / "RegistryTables769.java")
TABLES769_COMPOSED_FILE = (REPO_ROOT / "detergentmc-protocol" / "src" / "main" / "java"
                  / "io" / "detergentmc" / "protocol" / "RegistryTables769ComposedUp.java")
TABLES769_COMPOSED_DOWN_FILES = {
    suffix: (REPO_ROOT / "detergentmc-protocol" / "src" / "main" / "java"
             / "io" / "detergentmc" / "protocol"
             / f"RegistryTables769Down{suffix}.java")
    for suffix in ("763", "764", "765", "766", "767")
}

HEADER = """package io.detergentmc.protocol;

/**
 * 769（1.21.4）のレジストリ対応表（区間＋上書きの生の表）。
 *
 * <p>{@link RegistryRemapper} から分離した。理由はバイトコードの制限：
 * 769 の表を RegistryRemapper に置くと静的初期化子（{@code <clinit>}）
 * が 64KB を超えてコンパイルできなくなる（\"code too large\"）ため。
 * このクラスの表は {@link RegistryRemapper} から static import で
 * 同じ名前のまま参照する（展開済み表 {@code *_769_MAP} やアクセサの
 * 書き方は変わらない）。
 *
 * <p>表の内容と検証は {@code detergentmc-protocol/tools/} を参照：
 * 生成は {@code generate-remap-tables.py --target 769}、
 * 検証は {@code verify-769-tables.py}。
 */
final class RegistryTables769 {

    private RegistryTables769() {
    }

"""


COMPOSED_HEADER = """package io.detergentmc.protocol;

/**
 * 769（1.21.4）のレジストリ対応表のうち、離れた組み合わせ
 * （763↔769、764↔769、765↔769、766↔769、767↔769）の合成表（区間＋上書きの生の表）。
 *
 * <p>{@link RegistryTables769} から分離した。理由はバイトコードの制限：
 * 上向きの合成表を RegistryTables769 に置くと静的初期化子
 * （{@code <clinit>}）が 64KB を超えてコンパイルできなくなる
 * （\"code too large\"）ため。下向きの合成表はさらに 1 クラスずつ
 * {@link RegistryTables769Down763} などに分けてある。
 * このクラスの表は {@link RegistryRemapper} から static import で
 * 同じ名前のまま参照する。
 *
 * <p>表の内容と検証は {@code detergentmc-protocol/tools/} を参照：
 * 生成は {@code generate-remap-tables.py --target 769}、
 * 検証は {@code verify-769-tables.py}。
 */
final class RegistryTables769ComposedUp {

    private RegistryTables769ComposedUp() {
    }

"""


def composed_down_header(suffix):
    return f"""package io.detergentmc.protocol;

/**
 * 769（1.21.4）のレジストリ対応表のうち、離れた組み合わせ
 * （769→{suffix}）の下向きの合成表（区間＋上書きの生の表）。
 *
 * <p>{{@link RegistryTables769}} から分離した。理由はバイトコードの制限：
 * 下向きの合成表を RegistryTables769 に置くと静的初期化子
 * （{{@code <clinit>}}）が 64KB を超えてコンパイルできなくなる
 * （\\"code too large\\"）ため。上向きの合成表は別クラス
 * {{@link RegistryTables769ComposedUp}} にある。769→{suffix} の表だけを
 * このクラスに置く（他の下向きの表も同様に 1 クラスずつ分けた）。
 * このクラスの表は {{@link RegistryRemapper}} から static import で
 * 同じ名前のまま参照する。
 *
 * <p>表の内容と検証は {{@code detergentmc-protocol/tools/}} を参照：
 * 生成は {{@code generate-remap-tables.py --target 769}}、
 * 検証は {{@code verify-769-tables.py}}。
 */
final class RegistryTables769Down{suffix} {{

    private RegistryTables769Down{suffix}() {{
    }}

"""


def is_composed_up(name):
    """上向きの合成表か（763／764／765／766→769）。"""
    return re.match(
        r"^(?:BLOCK_STATE|ITEM|ENTITY_TYPE|SOUND|PARTICLE|BLOCK|MENU)_"
        r"(?:763|764|765|766|767)_TO_769(?:_RANGES|_OVERRIDES)$", name) is not None


def is_composed_down(name, suffix):
    """下向きの合成表か（769→suffix）。"""
    return re.match(
        r"^(?:BLOCK_STATE|ITEM|ENTITY_TYPE|SOUND|PARTICLE|BLOCK|MENU)_"
        r"769_TO_" + suffix + r"(?:_RANGES|_OVERRIDES)$", name) is not None



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
    if (TABLES769_FILE.exists() or TABLES769_COMPOSED_FILE.exists()
            or any(p.exists() for p in TABLES769_COMPOSED_DOWN_FILES.values())):
        print("RegistryTables769.java / RegistryTables769ComposedUp.java / "
              "RegistryTables769Down*.java が既にある。何もしない",
              file=sys.stderr)
        return 1
    gen_file = DATA_DIR / "gen769.txt"
    if not gen_file.exists():
        print(f"{gen_file} が無い。先に generate-remap-tables.py --target 769 "
              f"--out {gen_file} を実行すること", file=sys.stderr)
        return 1
    gen_text = gen_file.read_text(encoding="utf-8")
    tables = extract_tables(gen_text)
    # ATTRIBUTE_768_NAMES は RegistryTables768.java に既にあるため
    # 769 側には入れない（両方を static import すると曖昧になる）。
    tables.pop("ATTRIBUTE_768_NAMES", None)
    print(f"生成出力の表: {len(tables)} 件")
    if not tables:
        print("表が1件も抜き出せなかった", file=sys.stderr)
        return 1
    direct = []
    composed_up = []
    composed_down = {suffix: [] for suffix in TABLES769_COMPOSED_DOWN_FILES}
    for name, block in tables.items():
        # private を外して package-private にする（767 と同じ）。
        decl = block.replace("    private static final ",
                             "    static final ", 1)
        if is_composed_up(name):
            composed_up.append(decl)
        elif any(is_composed_down(name, suffix)
                 for suffix in TABLES769_COMPOSED_DOWN_FILES):
            for suffix in TABLES769_COMPOSED_DOWN_FILES:
                if is_composed_down(name, suffix):
                    composed_down[suffix].append(decl)
        else:
            direct.append(decl)
    TABLES769_FILE.write_text(
        HEADER + "\n\n".join(direct) + "\n\n}\n", encoding="utf-8")
    TABLES769_COMPOSED_FILE.write_text(
        COMPOSED_HEADER + "\n\n".join(composed_up) + "\n\n}\n", encoding="utf-8")
    for suffix, path in TABLES769_COMPOSED_DOWN_FILES.items():
        path.write_text(
            composed_down_header(suffix) + "\n\n".join(composed_down[suffix])
            + "\n\n}\n", encoding="utf-8")
    print(f"RegistryTables769.java を書き出した: {len(direct)} 件の表")
    print(f"RegistryTables769ComposedUp.java を書き出した: {len(composed_up)} 件の表")
    for suffix in TABLES769_COMPOSED_DOWN_FILES:
        print(f"RegistryTables769Down{suffix}.java を書き出した: "
              f"{len(composed_down[suffix])} 件の表")
    return 0


if __name__ == "__main__":
    sys.exit(main())
