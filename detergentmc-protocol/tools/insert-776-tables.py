#!/usr/bin/env python3
"""tools/generate-remap-tables.py --target 776 の出力を
RegistryTables776.java へ書き出す（1回だけの作業用）。

  - 生成出力（gen776.txt。--target 776 の --out 先）に含まれる
    776 の表すべて（*_776 RANGES／OVERRIDES、合成表 763↔776・
    764↔776・765↔776・766↔776・767↔776・768↔776・769↔776・770↔776・771↔776・772↔776・773↔776・774↔776、
    SOUND_NAME_FALLBACKS_776*、ATTRIBUTE_776_NAMES、ENCHANTMENT_776*）
    を RegistryTables776.java（隣接表・属性・エンチャント）、
    RegistryTables776SoundNames.java（音の名前）、
    RegistryTables776ComposedUpA.java／RegistryTables776ComposedUpB.java
    （上向きの合成表。1クラスに 168 件を置くと <clinit> が 64KB を超えるため
    split-776-composed-up.py で A（763〜768）／B（769〜774）に分割する）、
    RegistryTables776Down763／764／765／766／767／768／769／770／771／772／773／774.java（下向きの合成表）に分けて書く。
    分ける理由はバイトコードの制限：1つのクラスの静的初期化子が
    64KB を超えるとコンパイルできない（\"code too large\"）。
  - 表は package-private（static final）で、RegistryRemapper.java
    から import static で使う（RegistryTables776.java と同じ方針。
    RegistryRemapper に直接置くと <clinit> が 64KB を超えるため）。
  - RegistryTables776.java が既にある場合は何もせず終了コード 1
    で失敗する（二重作成を防ぐ）。
  - 作成後、tools/verify-776-tables.py で表が生成出力と一致する
    ことを確認すること。
"""

import re
import sys
from pathlib import Path

SCRIPT_DIR = Path(__file__).resolve().parent
REPO_ROOT = SCRIPT_DIR.parent.parent
DATA_DIR = REPO_ROOT / "detergentmc-protocol" / "build" / "remap-data"
TABLES776_FILE = (REPO_ROOT / "detergentmc-protocol" / "src" / "main" / "java"
                  / "io" / "detergentmc" / "protocol" / "RegistryTables776.java")
TABLES776_COMPOSED_FILE = (REPO_ROOT / "detergentmc-protocol" / "src" / "main" / "java"
                  / "io" / "detergentmc" / "protocol" / "RegistryTables776ComposedUp.java")
TABLES776_SOUND_FILE = (REPO_ROOT / "detergentmc-protocol" / "src" / "main" / "java"
                  / "io" / "detergentmc" / "protocol" / "RegistryTables776SoundNames.java")
TABLES776_COMPOSED_DOWN_FILES = {
    suffix: (REPO_ROOT / "detergentmc-protocol" / "src" / "main" / "java"
             / "io" / "detergentmc" / "protocol"
             / f"RegistryTables776Down{suffix}.java")
    for suffix in ("763", "764", "765", "766", "767", "768", "769", "770", "771", "772", "773", "774")
}

TABLES776_COMPOSED_DOWN_BLOCKSTATE_FILES = {
    suffix: (REPO_ROOT / "detergentmc-protocol" / "src" / "main" / "java"
             / "io" / "detergentmc" / "protocol"
             / f"RegistryTables776Down{suffix}BlockState.java")
    for suffix in ("763", "764", "765", "766", "767", "768", "769", "770", "771", "772", "773", "774")
}

HEADER = """package io.detergentmc.protocol;

/**
 * 776（26.2）のレジストリ対応表（区間＋上書きの生の表）。
 *
 * <p>{@link RegistryRemapper} から分離した。理由はバイトコードの制限：
 * 776 の表を RegistryRemapper に置くと静的初期化子（{@code <clinit>}）
 * が 64KB を超えてコンパイルできなくなる（\"code too large\"）ため。
 * このクラスの表は {@link RegistryRemapper} から static import で
 * 同じ名前のまま参照する（展開済み表 {@code *_776_MAP} やアクセサの
 * 書き方は変わらない）。
 *
 * <p>表の内容と検証は {@code detergentmc-protocol/tools/} を参照：
 * 生成は {@code generate-remap-tables.py --target 776}、
 * 検証は {@code verify-776-tables.py}。
 */
final class RegistryTables776 {

    private RegistryTables776() {
    }

"""


SOUND_HEADER = """package io.detergentmc.protocol;

/**
 * 776（26.2）の音の名前の代替表（SOUND_NAME_FALLBACKS_*）。
 *
 * <p>{@link RegistryTables776} から分離した。理由はバイトコードの制限：
 * 音の名前表（23 件・3,989 対）を RegistryTables776 に置くと静的初期化子
 * （{@code <clinit>}）が 64KB を超えてコンパイルできなくなる
 * （\"code too large\"）ため。このクラスの表は {@link RegistryRemapper}
 * から static import で同じ名前のまま参照する。
 *
 * <p>表の内容と検証は {@code detergentmc-protocol/tools/} を参照：
 * 生成は {@code generate-remap-tables.py --target 776}、
 * 検証は {@code verify-776-tables.py}。
 */
final class RegistryTables776SoundNames {

    private RegistryTables776SoundNames() {
    }

"""


COMPOSED_HEADER = """package io.detergentmc.protocol;

/**
 * 776（26.2）のレジストリ対応表のうち、離れた組み合わせ
 * （763↔776、764↔776、765↔776、766↔776、767↔776、768↔776、769↔776、770↔776、771↔776、772↔776、773↔776、774↔776）の合成表（区間＋上書きの生の表）。
 *
 * <p>{@link RegistryTables776} から分離した。理由はバイトコードの制限：
 * 上向きの合成表を RegistryTables776 に置くと静的初期化子
 * （{@code <clinit>}）が 64KB を超えてコンパイルできなくなる
 * （\"code too large\"）ため。下向きの合成表はさらに 1 クラスずつ
 * {@link RegistryTables776Down763} などに分けてある。
 * このクラスの表は {@link RegistryRemapper} から static import で
 * 同じ名前のまま参照する。
 *
 * <p>表の内容と検証は {@code detergentmc-protocol/tools/} を参照：
 * 生成は {@code generate-remap-tables.py --target 776}、
 * 検証は {@code verify-776-tables.py}。
 */
final class RegistryTables776ComposedUp {

    private RegistryTables776ComposedUp() {
    }

"""


def composed_down_header(suffix):
    return f"""package io.detergentmc.protocol;

/**
 * 776（26.2）のレジストリ対応表のうち、離れた組み合わせ
 * （776→{suffix}）の下向きの合成表（区間＋上書きの生の表）。
 *
 * <p>{{@link RegistryTables776}} から分離した。理由はバイトコードの制限：
 * 下向きの合成表を RegistryTables776 に置くと静的初期化子
 * （{{@code <clinit>}}）が 64KB を超えてコンパイルできなくなる
 * （\\"code too large\\"）ため。上向きの合成表は別クラス
 * {{@link RegistryTables776ComposedUp}} にある。776→{suffix} の表だけを
 * このクラスに置く（他の下向きの表も同様に 1 クラスずつ分けた）。
 * このクラスの表は {{@link RegistryRemapper}} から static import で
 * 同じ名前のまま参照する。
 *
 * <p>表の内容と検証は {{@code detergentmc-protocol/tools/}} を参照：
 * 生成は {{@code generate-remap-tables.py --target 776}}、
 * 検証は {{@code verify-776-tables.py}}。
 */
final class RegistryTables776Down{suffix} {{

    private RegistryTables776Down{suffix}() {{
    }}

"""


def composed_down_blockstate_header(suffix):
    return f"""package io.detergentmc.protocol;

/**
 * 776（26.2）のレジストリ対応表のうち、776→{suffix} の
 * ブロック状態の下向きの合成表（区間＋上書きの生の表）。
 *
 * <p>{{@link RegistryTables776Down{suffix}}} から分離した。理由は
 * バイトコードの制限：ブロック状態の下向き表を Down{suffix} に置くと
 * 静的初期化子（{{@code <clinit>}}）が 64KB を超えてコンパイルできなくなる
 * （\\"code too large\\"）ため。776→{suffix} のブロック状態は 32,366 件と件数が多い（上書きの件数は末尾のコメントを参照）。このクラスの表は
 * {{@link RegistryRemapper}} と {{@link RegistryTables776Expanded}}
 * から static import で同じ名前のまま参照する。
 *
 * <p>表の内容と検証は {{@code detergentmc-protocol/tools/}} を参照：
 * 生成は {{@code generate-remap-tables.py --target 776}}、
 * 検証は {{@code verify-776-tables.py}}。
 */
final class RegistryTables776Down{suffix}BlockState {{

    private RegistryTables776Down{suffix}BlockState() {{
    }}

"""


def is_composed_up(name):
    """上向きの合成表か（763／764／765／766／767／768／769／770／771／772→776）。"""
    return re.match(
        r"^(?:BLOCK_STATE|ITEM|ENTITY_TYPE|SOUND|PARTICLE|BLOCK|MENU)_"
        r"(?:763|764|765|766|767|768|769|770|771|772|773|774)_TO_776(?:_RANGES|_OVERRIDES)$", name) is not None


def is_composed_down(name, suffix):
    """下向きの合成表か（776→suffix）。"""
    return re.match(
        r"^(?:BLOCK_STATE|ITEM|ENTITY_TYPE|SOUND|PARTICLE|BLOCK|MENU)_"
        r"776_TO_" + suffix + r"(?:_RANGES|_OVERRIDES)$", name) is not None



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
    if (TABLES776_FILE.exists() or TABLES776_COMPOSED_FILE.exists()
            or TABLES776_SOUND_FILE.exists()
            or any(p.exists() for p in TABLES776_COMPOSED_DOWN_FILES.values())
            or any(p.exists()
                   for p in TABLES776_COMPOSED_DOWN_BLOCKSTATE_FILES.values())):
        print("RegistryTables776.java / RegistryTables776ComposedUp.java / "
              "RegistryTables776SoundNames.java / "
              "RegistryTables776Down*.java が既にある。何もしない",
              file=sys.stderr)
        return 1
    gen_file = DATA_DIR / "gen776.txt"
    if not gen_file.exists():
        print(f"{gen_file} が無い。先に generate-remap-tables.py --target 776 "
              f"--out {gen_file} を実行すること", file=sys.stderr)
        return 1
    gen_text = gen_file.read_text(encoding="utf-8")
    tables = extract_tables(gen_text)
    # ATTRIBUTE_775_NAMES は 775 側の名前で、RegistryTables776.java に
    # 入れるのは ATTRIBUTE_776_NAMES だけ。念のため 775 側の同名表が
    # 混ざっていたら落とす（gen776.txt には無いことを確認済み）。
    tables.pop("ATTRIBUTE_775_NAMES", None)
    print(f"生成出力の表: {len(tables)} 件")
    if not tables:
        print("表が1件も抜き出せなかった", file=sys.stderr)
        return 1
    direct = []
    composed_up = []
    sound_names = []
    composed_down = {suffix: [] for suffix in TABLES776_COMPOSED_DOWN_FILES}
    composed_down_bs = {suffix: []
                        for suffix in TABLES776_COMPOSED_DOWN_BLOCKSTATE_FILES}
    for name, block in tables.items():
        # private を外して package-private にする（767 と同じ）。
        decl = block.replace("    private static final ",
                             "    static final ", 1)
        if name.startswith("SOUND_NAME_FALLBACKS"):
            sound_names.append(decl)
        elif is_composed_up(name):
            composed_up.append(decl)
        elif name.startswith("BLOCK_STATE_776_TO_"):
            # ブロック状態の下向き表は対象ごとの別クラスへ（<clinit> の
            # 64KB 制限のため。Down763 だけで 12,068 個の int がある）。
            # どの対象にも当たらない表（776→775 の隣接の逆向き）は
            # 直接表へ入れる（775 のスクリプトで直した分岐）。
            placed = False
            for suffix in TABLES776_COMPOSED_DOWN_BLOCKSTATE_FILES:
                if is_composed_down(name, suffix):
                    composed_down_bs[suffix].append(decl)
                    placed = True
                    break
            if not placed:
                direct.append(decl)
        elif any(is_composed_down(name, suffix)
                 for suffix in TABLES776_COMPOSED_DOWN_FILES):
            for suffix in TABLES776_COMPOSED_DOWN_FILES:
                if is_composed_down(name, suffix):
                    composed_down[suffix].append(decl)
        else:
            direct.append(decl)
    TABLES776_FILE.write_text(
        HEADER + "\n\n".join(direct) + "\n\n}\n", encoding="utf-8")
    TABLES776_SOUND_FILE.write_text(
        SOUND_HEADER + "\n\n".join(sound_names) + "\n\n}\n", encoding="utf-8")
    TABLES776_COMPOSED_FILE.write_text(
        COMPOSED_HEADER + "\n\n".join(composed_up) + "\n\n}\n", encoding="utf-8")
    for suffix, path in TABLES776_COMPOSED_DOWN_FILES.items():
        path.write_text(
            composed_down_header(suffix) + "\n\n".join(composed_down[suffix])
            + "\n\n}\n", encoding="utf-8")
    for suffix, path in TABLES776_COMPOSED_DOWN_BLOCKSTATE_FILES.items():
        path.write_text(
            composed_down_blockstate_header(suffix)
            + "\n\n".join(composed_down_bs[suffix])
            + "\n\n}\n", encoding="utf-8")
    print(f"RegistryTables776.java を書き出した: {len(direct)} 件の表")
    print(f"RegistryTables776SoundNames.java を書き出した: {len(sound_names)} 件の表")
    print(f"RegistryTables776ComposedUp.java を書き出した: {len(composed_up)} 件の表")
    for suffix in TABLES776_COMPOSED_DOWN_FILES:
        print(f"RegistryTables776Down{suffix}.java を書き出した: "
              f"{len(composed_down[suffix])} 件の表")
    for suffix in TABLES776_COMPOSED_DOWN_BLOCKSTATE_FILES:
        print(f"RegistryTables776Down{suffix}BlockState.java を書き出した: "
              f"{len(composed_down_bs[suffix])} 件の表")
    return 0


if __name__ == "__main__":
    sys.exit(main())
