#!/usr/bin/env python3
"""tools/generate-remap-tables.py --target 774 の出力を
RegistryTables774.java へ書き出す（1回だけの作業用）。

  - 生成出力（gen774.txt。--target 774 の --out 先）に含まれる
    774 の表すべて（*_774 RANGES／OVERRIDES、合成表 763↔774・
    764↔774・765↔774・766↔774・767↔774・768↔774・769↔774・770↔774・771↔774・772↔774、
    SOUND_NAME_FALLBACKS_774*、ATTRIBUTE_774_NAMES、ENCHANTMENT_774*）
    を RegistryTables774.java（隣接表・属性・エンチャント）、
    RegistryTables774SoundNames.java（音の名前）、
    RegistryTables774ComposedUp.java（上向きの合成表）、
    RegistryTables774Down763／764／765／766／767／768／769／770／771／772.java（下向きの合成表）に分けて書く。
    分ける理由はバイトコードの制限：1つのクラスの静的初期化子が
    64KB を超えるとコンパイルできない（\"code too large\"）。
  - 表は package-private（static final）で、RegistryRemapper.java
    から import static で使う（RegistryTables774.java と同じ方針。
    RegistryRemapper に直接置くと <clinit> が 64KB を超えるため）。
  - RegistryTables774.java が既にある場合は何もせず終了コード 1
    で失敗する（二重作成を防ぐ）。
  - 作成後、tools/verify-774-tables.py で表が生成出力と一致する
    ことを確認すること。
"""

import re
import sys
from pathlib import Path

SCRIPT_DIR = Path(__file__).resolve().parent
REPO_ROOT = SCRIPT_DIR.parent.parent
DATA_DIR = REPO_ROOT / "detergentmc-protocol" / "build" / "remap-data"
TABLES774_FILE = (REPO_ROOT / "detergentmc-protocol" / "src" / "main" / "java"
                  / "io" / "detergentmc" / "protocol" / "RegistryTables774.java")
TABLES774_COMPOSED_FILE = (REPO_ROOT / "detergentmc-protocol" / "src" / "main" / "java"
                  / "io" / "detergentmc" / "protocol" / "RegistryTables774ComposedUp.java")
TABLES774_SOUND_FILE = (REPO_ROOT / "detergentmc-protocol" / "src" / "main" / "java"
                  / "io" / "detergentmc" / "protocol" / "RegistryTables774SoundNames.java")
TABLES774_COMPOSED_DOWN_FILES = {
    suffix: (REPO_ROOT / "detergentmc-protocol" / "src" / "main" / "java"
             / "io" / "detergentmc" / "protocol"
             / f"RegistryTables774Down{suffix}.java")
    for suffix in ("763", "764", "765", "766", "767", "768", "769", "770", "771", "772")
}

TABLES774_COMPOSED_DOWN_BLOCKSTATE_FILES = {
    suffix: (REPO_ROOT / "detergentmc-protocol" / "src" / "main" / "java"
             / "io" / "detergentmc" / "protocol"
             / f"RegistryTables774Down{suffix}BlockState.java")
    for suffix in ("763", "764", "765", "766", "767", "768", "769", "770", "771", "772")
}

HEADER = """package io.detergentmc.protocol;

/**
 * 774（1.21.11）のレジストリ対応表（区間＋上書きの生の表）。
 *
 * <p>{@link RegistryRemapper} から分離した。理由はバイトコードの制限：
 * 774 の表を RegistryRemapper に置くと静的初期化子（{@code <clinit>}）
 * が 64KB を超えてコンパイルできなくなる（\"code too large\"）ため。
 * このクラスの表は {@link RegistryRemapper} から static import で
 * 同じ名前のまま参照する（展開済み表 {@code *_774_MAP} やアクセサの
 * 書き方は変わらない）。
 *
 * <p>表の内容と検証は {@code detergentmc-protocol/tools/} を参照：
 * 生成は {@code generate-remap-tables.py --target 774}、
 * 検証は {@code verify-774-tables.py}。
 */
final class RegistryTables774 {

    private RegistryTables774() {
    }

"""


SOUND_HEADER = """package io.detergentmc.protocol;

/**
 * 774（1.21.11）の音の名前の代替表（SOUND_NAME_FALLBACKS_*）。
 *
 * <p>{@link RegistryTables774} から分離した。理由はバイトコードの制限：
 * 音の名前表（20 件・2,372 対）を RegistryTables774 に置くと静的初期化子
 * （{@code <clinit>}）が 64KB を超えてコンパイルできなくなる
 * （\"code too large\"）ため。このクラスの表は {@link RegistryRemapper}
 * から static import で同じ名前のまま参照する。
 *
 * <p>表の内容と検証は {@code detergentmc-protocol/tools/} を参照：
 * 生成は {@code generate-remap-tables.py --target 774}、
 * 検証は {@code verify-774-tables.py}。
 */
final class RegistryTables774SoundNames {

    private RegistryTables774SoundNames() {
    }

"""


COMPOSED_HEADER = """package io.detergentmc.protocol;

/**
 * 774（1.21.11）のレジストリ対応表のうち、離れた組み合わせ
 * （763↔774、764↔774、765↔774、766↔774、767↔774、768↔774、769↔774、770↔774、771↔774、772↔774）の合成表（区間＋上書きの生の表）。
 *
 * <p>{@link RegistryTables774} から分離した。理由はバイトコードの制限：
 * 上向きの合成表を RegistryTables774 に置くと静的初期化子
 * （{@code <clinit>}）が 64KB を超えてコンパイルできなくなる
 * （\"code too large\"）ため。下向きの合成表はさらに 1 クラスずつ
 * {@link RegistryTables774Down763} などに分けてある。
 * このクラスの表は {@link RegistryRemapper} から static import で
 * 同じ名前のまま参照する。
 *
 * <p>表の内容と検証は {@code detergentmc-protocol/tools/} を参照：
 * 生成は {@code generate-remap-tables.py --target 774}、
 * 検証は {@code verify-774-tables.py}。
 */
final class RegistryTables774ComposedUp {

    private RegistryTables774ComposedUp() {
    }

"""


def composed_down_header(suffix):
    return f"""package io.detergentmc.protocol;

/**
 * 774（1.21.11）のレジストリ対応表のうち、離れた組み合わせ
 * （774→{suffix}）の下向きの合成表（区間＋上書きの生の表）。
 *
 * <p>{{@link RegistryTables774}} から分離した。理由はバイトコードの制限：
 * 下向きの合成表を RegistryTables774 に置くと静的初期化子
 * （{{@code <clinit>}}）が 64KB を超えてコンパイルできなくなる
 * （\\"code too large\\"）ため。上向きの合成表は別クラス
 * {{@link RegistryTables774ComposedUp}} にある。774→{suffix} の表だけを
 * このクラスに置く（他の下向きの表も同様に 1 クラスずつ分けた）。
 * このクラスの表は {{@link RegistryRemapper}} から static import で
 * 同じ名前のまま参照する。
 *
 * <p>表の内容と検証は {{@code detergentmc-protocol/tools/}} を参照：
 * 生成は {{@code generate-remap-tables.py --target 774}}、
 * 検証は {{@code verify-774-tables.py}}。
 */
final class RegistryTables774Down{suffix} {{

    private RegistryTables774Down{suffix}() {{
    }}

"""


def composed_down_blockstate_header(suffix):
    return f"""package io.detergentmc.protocol;

/**
 * 774（1.21.11）のレジストリ対応表のうち、774→{suffix} の
 * ブロック状態の下向きの合成表（区間＋上書きの生の表）。
 *
 * <p>{{@link RegistryTables774Down{suffix}}} から分離した。理由は
 * バイトコードの制限：ブロック状態の下向き表を Down{suffix} に置くと
 * 静的初期化子（{{@code <clinit>}}）が 64KB を超えてコンパイルできなくなる
 * （\\"code too large\\"）ため。774→763 のブロック状態は 29,671 件と件数
 * が多く、上書きも 3,146 件ある。このクラスの表は
 * {{@link RegistryRemapper}} と {{@link RegistryTables774Expanded}}
 * から static import で同じ名前のまま参照する。
 *
 * <p>表の内容と検証は {{@code detergentmc-protocol/tools/}} を参照：
 * 生成は {{@code generate-remap-tables.py --target 774}}、
 * 検証は {{@code verify-774-tables.py}}。
 */
final class RegistryTables774Down{suffix}BlockState {{

    private RegistryTables774Down{suffix}BlockState() {{
    }}

"""


def is_composed_up(name):
    """上向きの合成表か（763／764／765／766／767／768／769／770／771／772→774）。"""
    return re.match(
        r"^(?:BLOCK_STATE|ITEM|ENTITY_TYPE|SOUND|PARTICLE|BLOCK|MENU)_"
        r"(?:763|764|765|766|767|768|769|770|771|772)_TO_774(?:_RANGES|_OVERRIDES)$", name) is not None


def is_composed_down(name, suffix):
    """下向きの合成表か（774→suffix）。"""
    return re.match(
        r"^(?:BLOCK_STATE|ITEM|ENTITY_TYPE|SOUND|PARTICLE|BLOCK|MENU)_"
        r"774_TO_" + suffix + r"(?:_RANGES|_OVERRIDES)$", name) is not None



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
    if (TABLES774_FILE.exists() or TABLES774_COMPOSED_FILE.exists()
            or TABLES774_SOUND_FILE.exists()
            or any(p.exists() for p in TABLES774_COMPOSED_DOWN_FILES.values())
            or any(p.exists()
                   for p in TABLES774_COMPOSED_DOWN_BLOCKSTATE_FILES.values())):
        print("RegistryTables774.java / RegistryTables774ComposedUp.java / "
              "RegistryTables774SoundNames.java / "
              "RegistryTables774Down*.java が既にある。何もしない",
              file=sys.stderr)
        return 1
    gen_file = DATA_DIR / "gen774.txt"
    if not gen_file.exists():
        print(f"{gen_file} が無い。先に generate-remap-tables.py --target 774 "
              f"--out {gen_file} を実行すること", file=sys.stderr)
        return 1
    gen_text = gen_file.read_text(encoding="utf-8")
    tables = extract_tables(gen_text)
    # ATTRIBUTE_773_NAMES は 773 側の名前で、RegistryTables774.java に
    # 入れるのは ATTRIBUTE_774_NAMES だけ。念のため 773 側の同名表が
    # 混ざっていたら落とす（gen774.txt には無いことを確認済み）。
    tables.pop("ATTRIBUTE_773_NAMES", None)
    print(f"生成出力の表: {len(tables)} 件")
    if not tables:
        print("表が1件も抜き出せなかった", file=sys.stderr)
        return 1
    direct = []
    composed_up = []
    sound_names = []
    composed_down = {suffix: [] for suffix in TABLES774_COMPOSED_DOWN_FILES}
    composed_down_bs = {suffix: []
                        for suffix in TABLES774_COMPOSED_DOWN_BLOCKSTATE_FILES}
    for name, block in tables.items():
        # private を外して package-private にする（767 と同じ）。
        decl = block.replace("    private static final ",
                             "    static final ", 1)
        if name.startswith("SOUND_NAME_FALLBACKS"):
            sound_names.append(decl)
        elif is_composed_up(name):
            composed_up.append(decl)
        elif name.startswith("BLOCK_STATE_774_TO_"):
            # ブロック状態の下向き表は対象ごとの別クラスへ（<clinit> の
            # 64KB 制限のため。Down763 だけで 6,595 個の int がある）。
            # どの対象にも当たらない表（774→773 の隣接の逆向き）は
            # 直接表へ入れる（773 のスクリプトではこの分岐で落とす穴が
            # あったため、774 で直す）。
            placed = False
            for suffix in TABLES774_COMPOSED_DOWN_BLOCKSTATE_FILES:
                if is_composed_down(name, suffix):
                    composed_down_bs[suffix].append(decl)
                    placed = True
                    break
            if not placed:
                direct.append(decl)
        elif any(is_composed_down(name, suffix)
                 for suffix in TABLES774_COMPOSED_DOWN_FILES):
            for suffix in TABLES774_COMPOSED_DOWN_FILES:
                if is_composed_down(name, suffix):
                    composed_down[suffix].append(decl)
        else:
            direct.append(decl)
    TABLES774_FILE.write_text(
        HEADER + "\n\n".join(direct) + "\n\n}\n", encoding="utf-8")
    TABLES774_SOUND_FILE.write_text(
        SOUND_HEADER + "\n\n".join(sound_names) + "\n\n}\n", encoding="utf-8")
    TABLES774_COMPOSED_FILE.write_text(
        COMPOSED_HEADER + "\n\n".join(composed_up) + "\n\n}\n", encoding="utf-8")
    for suffix, path in TABLES774_COMPOSED_DOWN_FILES.items():
        path.write_text(
            composed_down_header(suffix) + "\n\n".join(composed_down[suffix])
            + "\n\n}\n", encoding="utf-8")
    for suffix, path in TABLES774_COMPOSED_DOWN_BLOCKSTATE_FILES.items():
        path.write_text(
            composed_down_blockstate_header(suffix)
            + "\n\n".join(composed_down_bs[suffix])
            + "\n\n}\n", encoding="utf-8")
    print(f"RegistryTables774.java を書き出した: {len(direct)} 件の表")
    print(f"RegistryTables774SoundNames.java を書き出した: {len(sound_names)} 件の表")
    print(f"RegistryTables774ComposedUp.java を書き出した: {len(composed_up)} 件の表")
    for suffix in TABLES774_COMPOSED_DOWN_FILES:
        print(f"RegistryTables774Down{suffix}.java を書き出した: "
              f"{len(composed_down[suffix])} 件の表")
    for suffix in TABLES774_COMPOSED_DOWN_BLOCKSTATE_FILES:
        print(f"RegistryTables774Down{suffix}BlockState.java を書き出した: "
              f"{len(composed_down_bs[suffix])} 件の表")
    return 0


if __name__ == "__main__":
    sys.exit(main())
