#!/usr/bin/env python3
"""RegistryTables776ComposedUp.java を2クラスに分割する。

理由：上向きの合成表が 168 件（763〜774 の 12 ペア × 7 種 ×
RANGES／OVERRIDES）で 1,200 行近くになり、1クラスの静的初期化子
（<clinit>）が 64KB を超えて "code too large" でコンパイルできない。
775 の ComposedUp は 154 件で A／B に分割した前例がある。
776 はペアが1つ増えて 168 件のため、同じく A／B に分割する。

分割：
  RegistryTables776ComposedUpA.java … 763〜768（6ペア・84表）
  RegistryTables776ComposedUpB.java … 769〜774（6ペア・84表）
参照の更新：
  Down763〜774 の javadoc の
  {@link RegistryTables776ComposedUp} を A／B に振り分ける。
  RegistryRemapper.java の import は insert-776-java.py が A／B を
  直接入れる。verify-776-tables.py は最初から A／B を見る。

使い方：リポジトリのルートで python3 detergentmc-protocol/tools/split-776-composed-up.py
"""
import re
import sys
from pathlib import Path

REPO = Path(__file__).resolve().parent.parent.parent
PKG = REPO / "detergentmc-protocol" / "src" / "main" / "java" / "io" / "detergentmc" / "protocol"
TOOLS = REPO / "detergentmc-protocol" / "tools"

SRC = PKG / "RegistryTables776ComposedUp.java"
DST_A = PKG / "RegistryTables776ComposedUpA.java"
DST_B = PKG / "RegistryTables776ComposedUpB.java"


def split_tables(text):
    """static final int[][] NAME = {...}; のブロックを抜き出す。"""
    pattern = re.compile(
        r"    static final int\[\]\[?\]? (\w+) = \{(.*?)\n    \};\n",
        re.DOTALL)
    tables = []
    for m in pattern.finditer(text):
        tables.append((m.group(1), m.group(0)))
    return tables


def main():
    if DST_A.exists() or DST_B.exists():
        print("分割先が既にあるため何もしない", file=sys.stderr)
        return 1
    text = SRC.read_text(encoding="utf-8")
    header = text[:text.index("final class RegistryTables776ComposedUp {")]
    tables = split_tables(text)
    print(f"表: {len(tables)} 件")
    assert len(tables) == 168, len(tables)

    group_a, group_b = [], []
    for name, block in tables:
        m = re.search(r"_(\d+)_TO_776_", name)
        assert m, name
        src = int(m.group(1))
        (group_a if src <= 768 else group_b).append((name, block))
    print(f"A (763〜768): {len(group_a)} 件、B (769〜774): {len(group_b)} 件")
    assert len(group_a) == 84 and len(group_b) == 84

    def make_class(class_name, pairs, group):
        doc = header
        old_pairs = (
            " * 776（26.1）のレジストリ対応表のうち、離れた組み合わせ\n"
            " * （763↔776、764↔776、765↔776、766↔776、767↔776、768↔776、769↔776、770↔776、771↔776、772↔776、773↔776、774↔776）の合成表（区間＋上書きの生の表）。")
        assert old_pairs in doc, "ペア一覧の文面が合わない"
        doc = doc.replace(old_pairs,
                          f" * 776（26.1）のレジストリ対応表のうち、離れた組み合わせ\n"
                          f" * （{pairs}）の合成表（区間＋上書きの生の表）。")
        old_why = (
            " * <p>{@link RegistryTables776} から分離した。理由はバイトコードの制限：\n"
            " * 上向きの合成表を RegistryTables776 に置くと静的初期化子\n"
            " * （{@code <clinit>}）が 64KB を超えてコンパイルできなくなる\n"
            " * （\"code too large\"）ため。下向きの合成表はさらに 1 クラスずつ\n"
            " * {@link RegistryTables776Down763} などに分けてある。")
        assert old_why in doc, "分離理由の文面が合わない"
        doc = doc.replace(old_why,
                          " * <p>{@link RegistryTables776} から分離した。理由はバイトコードの制限：\n"
                          " * 上向きの合成表を RegistryTables776 に置くと静的初期化子\n"
                          " * （{@code <clinit>}）が 64KB を超えてコンパイルできなくなる\n"
                          " * （\"code too large\"）ため。さらに上向きの合成表だけでも 168 件で\n"
                          " * 1 クラスに収まらないため、763〜768 を A、769〜774 を B に分けた。\n"
                          " * 下向きの合成表は 1 クラスずつ\n"
                          " * {@link RegistryTables776Down763} などに分けてある。")
        body = "".join(block for _, block in group)
        return (doc + f"final class {class_name} {{\n\n"
                f"    private {class_name}() {{\n"
                f"    }}\n\n" + body + "}\n")

    DST_A.write_text(make_class(
        "RegistryTables776ComposedUpA",
        "763↔776、764↔776、765↔776、766↔776、767↔776、768↔776",
        group_a), encoding="utf-8")
    DST_B.write_text(make_class(
        "RegistryTables776ComposedUpB",
        "769↔776、770↔776、771↔776、772↔776、773↔776",
        group_b), encoding="utf-8")
    SRC.unlink()
    print("RegistryTables776ComposedUp.java を A／B に分割した")

    # RegistryRemapper.java の import は insert-776-java.py が A／B を
    # 直接入れるため、ここでは更新しない（775 と同じ段取り）。

    # Down763〜774 の javadoc のリンクを A／B に振り分ける。
    for suffix in range(763, 775):
        path = PKG / f"RegistryTables776Down{suffix}.java"
        t = path.read_text(encoding="utf-8")
        cls = "RegistryTables776ComposedUpA" if suffix <= 768 else "RegistryTables776ComposedUpB"
        old = "{@link RegistryTables776ComposedUp}"
        assert t.count(old) == 1, (path, t.count(old))
        t = t.replace(old, "{@link " + cls + "}", 1)
        path.write_text(t, encoding="utf-8")
    print("Down763〜774 の javadoc リンクを A／B に振り分けた")

    # verify-776-tables.py は最初から A／B を TABLES_FILES に入れて
    # 作るため、ここでは更新しない。
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
