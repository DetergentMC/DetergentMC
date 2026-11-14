#!/usr/bin/env python3
"""RegistryTables775ComposedUp.java を2クラスに分割する。

理由：上向きの合成表が 154 件（763〜773 の 11 ペア × 7 種 ×
RANGES／OVERRIDES）で 1,073 行になり、1クラスの静的初期化子
（<clinit>）が 64KB を超えて "code too large" でコンパイルできない。
774 の ComposedUp は 140 件・940 行で収まっていたが、775 は
ペアが1つ増えた分だけ超えた。774 以前は ComposedUp の分割は
不要だったため、前例は無い。

分割：
  RegistryTables775ComposedUpA.java … 763〜768（6ペア・84表）
  RegistryTables775ComposedUpB.java … 769〜773（5ペア・70表）
参照の更新：
  RegistryRemapper.java と RegistryTables775Expanded.java の
  static import を A／B の2行にする。Down763〜773 の javadoc の
  {@link RegistryTables775ComposedUp} を A／B に振り分ける。
  verify-775-tables.py の TABLES_FILES も A／B にする。

使い方：リポジトリのルートで python3 detergentmc-protocol/tools/split-775-composed-up.py
"""
import re
import sys
from pathlib import Path

REPO = Path(__file__).resolve().parent.parent.parent
PKG = REPO / "detergentmc-protocol" / "src" / "main" / "java" / "io" / "detergentmc" / "protocol"
TOOLS = REPO / "detergentmc-protocol" / "tools"

SRC = PKG / "RegistryTables775ComposedUp.java"
DST_A = PKG / "RegistryTables775ComposedUpA.java"
DST_B = PKG / "RegistryTables775ComposedUpB.java"


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
    header = text[:text.index("final class RegistryTables775ComposedUp {")]
    tables = split_tables(text)
    print(f"表: {len(tables)} 件")
    assert len(tables) == 154, len(tables)

    group_a, group_b = [], []
    for name, block in tables:
        m = re.search(r"_(\d+)_TO_775_", name)
        assert m, name
        src = int(m.group(1))
        (group_a if src <= 768 else group_b).append((name, block))
    print(f"A (763〜768): {len(group_a)} 件、B (769〜773): {len(group_b)} 件")
    assert len(group_a) == 84 and len(group_b) == 70

    def make_class(class_name, pairs, group):
        doc = header
        old_pairs = (
            " * 775（26.1）のレジストリ対応表のうち、離れた組み合わせ\n"
            " * （763↔775、764↔775、765↔775、766↔775、767↔775、768↔775、769↔775、770↔775、771↔775、772↔775、773↔775）の合成表（区間＋上書きの生の表）。")
        assert old_pairs in doc, "ペア一覧の文面が合わない"
        doc = doc.replace(old_pairs,
                          f" * 775（26.1）のレジストリ対応表のうち、離れた組み合わせ\n"
                          f" * （{pairs}）の合成表（区間＋上書きの生の表）。")
        old_why = (
            " * <p>{@link RegistryTables775} から分離した。理由はバイトコードの制限：\n"
            " * 上向きの合成表を RegistryTables775 に置くと静的初期化子\n"
            " * （{@code <clinit>}）が 64KB を超えてコンパイルできなくなる\n"
            " * （\"code too large\"）ため。下向きの合成表はさらに 1 クラスずつ\n"
            " * {@link RegistryTables775Down763} などに分けてある。")
        assert old_why in doc, "分離理由の文面が合わない"
        doc = doc.replace(old_why,
                          " * <p>{@link RegistryTables775} から分離した。理由はバイトコードの制限：\n"
                          " * 上向きの合成表を RegistryTables775 に置くと静的初期化子\n"
                          " * （{@code <clinit>}）が 64KB を超えてコンパイルできなくなる\n"
                          " * （\"code too large\"）ため。さらに上向きの合成表だけでも 154 件で\n"
                          " * 1 クラスに収まらないため、763〜768 を A、769〜773 を B に分けた。\n"
                          " * 下向きの合成表は 1 クラスずつ\n"
                          " * {@link RegistryTables775Down763} などに分けてある。")
        body = "".join(block for _, block in group)
        return (doc + f"final class {class_name} {{\n\n"
                f"    private {class_name}() {{\n"
                f"    }}\n\n" + body + "}\n")

    DST_A.write_text(make_class(
        "RegistryTables775ComposedUpA",
        "763↔775、764↔775、765↔775、766↔775、767↔775、768↔775",
        group_a), encoding="utf-8")
    DST_B.write_text(make_class(
        "RegistryTables775ComposedUpB",
        "769↔775、770↔775、771↔775、772↔775、773↔775",
        group_b), encoding="utf-8")
    SRC.unlink()
    print("RegistryTables775ComposedUp.java を A／B に分割した")

    # RegistryRemapper.java と RegistryTables775Expanded.java の import。
    for target in (PKG / "RegistryRemapper.java", PKG / "RegistryTables775Expanded.java"):
        t = target.read_text(encoding="utf-8")
        old = "import static io.detergentmc.protocol.RegistryTables775ComposedUp.*;\n"
        assert t.count(old) == 1, (target, t.count(old))
        t = t.replace(old,
                      "import static io.detergentmc.protocol.RegistryTables775ComposedUpA.*;\n"
                      "import static io.detergentmc.protocol.RegistryTables775ComposedUpB.*;\n", 1)
        target.write_text(t, encoding="utf-8")
        print(f"{target.name} の import を A／B にした")

    # Down763〜773 の javadoc のリンクを A／B に振り分ける。
    for suffix in range(763, 774):
        path = PKG / f"RegistryTables775Down{suffix}.java"
        t = path.read_text(encoding="utf-8")
        cls = "RegistryTables775ComposedUpA" if suffix <= 768 else "RegistryTables775ComposedUpB"
        old = "{@link RegistryTables775ComposedUp}"
        assert t.count(old) == 1, (path, t.count(old))
        t = t.replace(old, "{@link " + cls + "}", 1)
        path.write_text(t, encoding="utf-8")
    print("Down763〜773 の javadoc リンクを A／B に振り分けた")

    # verify-775-tables.py の TABLES_FILES。
    verify = TOOLS / "verify-775-tables.py"
    t = verify.read_text(encoding="utf-8")
    old = '    for name in ("RegistryTables775.java", "RegistryTables775ComposedUp.java",'
    assert t.count(old) == 1, t.count(old)
    t = t.replace(old,
                  '    for name in ("RegistryTables775.java", "RegistryTables775ComposedUpA.java",\n'
                  '                 "RegistryTables775ComposedUpB.java",', 1)
    verify.write_text(t, encoding="utf-8")
    print("verify-775-tables.py の TABLES_FILES を A／B にした")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
