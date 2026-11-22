#!/usr/bin/env python3
"""RegistryTables776SoundNames.java を2クラスに分割する。

理由：音の名前表が 23 件・3,989 対になり、1クラスの静的初期化子
（<clinit>）が 64KB を超えて "code too large" でコンパイルできない。
775 の SoundNames は 3,131 対で <clinit> が 60,038 バイトまで迫って
いたため、776 では超えた（1 対あたり約 19.2 バイト）。

分割（対数で均等に分ける）：
  RegistryTables776SoundNamesA.java … SOUND_NAME_FALLBACKS_776・
    775_TO_776・776_TO_775〜776_TO_769・774_TO_776〜767_TO_776
    （約 1,490 対）
  RegistryTables776SoundNamesB.java … 776_TO_768〜776_TO_764
    （2,499 対）
参照の更新：
  RegistryRemapper.java の static import を A／B の2行にする。
  verify-776-tables.py の TABLES_FILES も A／B にする。
"""
import re
import sys
from pathlib import Path

REPO = Path(__file__).resolve().parent.parent.parent
PKG = REPO / "detergentmc-protocol" / "src" / "main" / "java" / "io" / "detergentmc" / "protocol"
TOOLS = REPO / "detergentmc-protocol" / "tools"

SRC = PKG / "RegistryTables776SoundNames.java"
DST_A = PKG / "RegistryTables776SoundNamesA.java"
DST_B = PKG / "RegistryTables776SoundNamesB.java"

# A に入れる表名（776_TO_775〜776_TO_769 と小さい表すべて）。
A_NAMES = {
    "SOUND_NAME_FALLBACKS_776",
    "SOUND_NAME_FALLBACKS_775_TO_776",
    "SOUND_NAME_FALLBACKS_776_TO_775",
    "SOUND_NAME_FALLBACKS_776_TO_774",
    "SOUND_NAME_FALLBACKS_776_TO_773",
    "SOUND_NAME_FALLBACKS_776_TO_772",
    "SOUND_NAME_FALLBACKS_776_TO_771",
    "SOUND_NAME_FALLBACKS_776_TO_770",
    "SOUND_NAME_FALLBACKS_776_TO_769",
    "SOUND_NAME_FALLBACKS_774_TO_776",
    "SOUND_NAME_FALLBACKS_773_TO_776",
    "SOUND_NAME_FALLBACKS_772_TO_776",
    "SOUND_NAME_FALLBACKS_771_TO_776",
    "SOUND_NAME_FALLBACKS_770_TO_776",
    "SOUND_NAME_FALLBACKS_769_TO_776",
    "SOUND_NAME_FALLBACKS_768_TO_776",
    "SOUND_NAME_FALLBACKS_767_TO_776",
}


def split_table_blocks(text):
    """各 String[][] 表を (名前, ブロック全文) の list にする。"""
    pattern = re.compile(
        r"(    static final String\[\]\[\] (SOUND_NAME_\w+) = \{(?:.*?)\n    \};\n)",
        re.DOTALL)
    blocks = []
    for m in pattern.finditer(text):
        blocks.append((m.group(2), m.group(1)))
    return blocks


def make_class(class_name, table_blocks, note):
    header = f"""package io.detergentmc.protocol;

/**
 * 776（26.2）の音の名前の代替表（SOUND_NAME_FALLBACKS_*）のうち
 * {note}。
 *
 * <p>RegistryTables776SoundNames.java を2つに分けたもの。1クラスに置くと
 * 静的初期化子（{{@code <clinit>}}）が 64KB を超えてコンパイルできなくなる
 * （"code too large"）ため。表は {{@link RegistryRemapper}}
 * から static import で同じ名前のまま参照する。
 *
 * <p>表の内容と検証は {{@code detergentmc-protocol/tools/}} を参照：
 * 生成は {{@code generate-remap-tables.py --target 776}}、
 * 検証は {{@code verify-776-tables.py}}。
 */
final class {class_name} {{

    private {class_name}() {{
    }}

"""
    body = "\n".join(block.rstrip("\n") for _, block in table_blocks)
    return header + body + "\n\n}\n"


def main():
    text = SRC.read_text(encoding="utf-8")
    blocks = split_table_blocks(text)
    assert len(blocks) == 23, len(blocks)
    group_a = [(n, b) for n, b in blocks if n in A_NAMES]
    group_b = [(n, b) for n, b in blocks if n not in A_NAMES]
    assert len(group_a) == 17 and len(group_b) == 6, (len(group_a), len(group_b))
    count_a = sum(len(re.findall(r'\{"[^"]+", "[^"]+"\}', b)) for _, b in group_a)
    count_b = sum(len(re.findall(r'\{"[^"]+", "[^"]+"\}', b)) for _, b in group_b)
    print(f"A: {len(group_a)} 表・{count_a} 対、B: {len(group_b)} 表・{count_b} 対")
    assert count_a <= 2500 and count_b <= 2500, (count_a, count_b)
    DST_A.write_text(make_class(
        "RegistryTables776SoundNamesA", group_a,
        "776_TO_775〜776_TO_769 と小さい表"), encoding="utf-8")
    DST_B.write_text(make_class(
        "RegistryTables776SoundNamesB", group_b,
        "776_TO_768〜776_TO_764"), encoding="utf-8")
    SRC.unlink()
    print("RegistryTables776SoundNames.java を A／B に分割した")

    # RegistryRemapper.java の import を A／B にする。
    remapper = PKG / "RegistryRemapper.java"
    java = remapper.read_text(encoding="utf-8")
    old = "import static io.detergentmc.protocol.RegistryTables776SoundNames.*;\n"
    assert java.count(old) == 1, java.count(old)
    java = java.replace(old,
                        "import static io.detergentmc.protocol.RegistryTables776SoundNamesA.*;\n"
                        "import static io.detergentmc.protocol.RegistryTables776SoundNamesB.*;\n",
                        1)
    remapper.write_text(java, encoding="utf-8")
    print("RegistryRemapper.java の import を A／B にした")

    # verify-776-tables.py の TABLES_FILES を A／B にする。
    verify = TOOLS / "verify-776-tables.py"
    v = verify.read_text(encoding="utf-8")
    old_v = '"RegistryTables776SoundNames.java",'
    assert v.count(old_v) == 1
    v = v.replace(old_v,
                  '"RegistryTables776SoundNamesA.java",\n'
                  '                 "RegistryTables776SoundNamesB.java",', 1)
    verify.write_text(v, encoding="utf-8")
    print("verify-776-tables.py の TABLES_FILES を A／B にした")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
