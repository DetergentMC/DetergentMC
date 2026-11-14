#!/usr/bin/env python3
"""RegistryTables773Down{suffix}.java のブロック状態の下向き表
（BLOCK_STATE_773_TO_{suffix}_RANGES／OVERRIDES）を
RegistryTables773Down{suffix}BlockState.java へ移す（1回だけの作業用）。

理由：773→763 の下向き表は 8,670 個の int リテラルがあり、
RegistryTables773Down763.java の静的初期化子（<clinit>）が 64KB を超えて
"code too large" でコンパイルできなくなる。ブロック状態の表が
6,595 個と大半を占める。763 だけでなく 764（8,552 個）も制限に近く、
他の対象も将来の版で同じ問題になるため、ブロック状態の下向き表は
対象ごとに全て分離する（分離後の Down ファイルは 464〜2,075 個）。
表そのものは中身を変えず、置き場所だけ別クラスへ移す。参照は
static import で解決するため、展開側のコードは一切変わらない。

あわせて insert-773-tables.py を直し、再生成時に同じ振り分けになる
ようにする。verify-773-tables.py の TABLES_FILES にも新ファイルを追加する
（A／F／C／E の検証で使うため）。
"""
import re
import sys
from pathlib import Path

SCRIPT_DIR = Path(__file__).resolve().parent
REPO_ROOT = SCRIPT_DIR.parent.parent
PROTO = (REPO_ROOT / "detergentmc-protocol" / "src" / "main" / "java"
         / "io" / "detergentmc" / "protocol")
REMAPPER = PROTO / "RegistryRemapper.java"
EXPANDED = PROTO / "RegistryTables773Expanded.java"
INSERT_TABLES = SCRIPT_DIR / "insert-773-tables.py"
VERIFY = SCRIPT_DIR / "verify-773-tables.py"
SUFFIXES = ("763", "764", "765", "766", "767", "768", "769", "770", "771")


def blockstate_header(suffix):
    return f"""package io.detergentmc.protocol;

/**
 * 773（1.21.9／1.21.10）のレジストリ対応表のうち、773→{suffix} の
 * ブロック状態の下向きの合成表（区間＋上書きの生の表）。
 *
 * <p>{{@link RegistryTables773Down{suffix}}} から分離した。理由は
 * バイトコードの制限：ブロック状態の下向き表を Down{suffix} に置くと
 * 静的初期化子（{{@code <clinit>}}）が 64KB を超えてコンパイルできなくなる
 * （\\"code too large\\"）ため。763→773 のブロック状態は 29,671 件と件数
 * が多く、上書きも 6,000 件を超える。このクラスの表は
 * {{@link RegistryRemapper}} と {{@link RegistryTables773Expanded}}
 * から static import で同じ名前のまま参照する。
 *
 * <p>表の内容と検証は {{@code detergentmc-protocol/tools/}} を参照：
 * 生成は {{@code generate-remap-tables.py --target 773}}、
 * 検証は {{@code verify-773-tables.py}}。
 */
final class RegistryTables773Down{suffix}BlockState {{

    private RegistryTables773Down{suffix}BlockState() {{
    }}

"""


for suffix in SUFFIXES:
    down = PROTO / f"RegistryTables773Down{suffix}.java"
    out = PROTO / f"RegistryTables773Down{suffix}BlockState.java"
    assert not out.exists(), f"{out} が既にある"
    text = down.read_text(encoding="utf-8")
    pattern = re.compile(
        r"    static final int\[\]\[\] (BLOCK_STATE_773_TO_" + suffix +
        r"_(?:RANGES|OVERRIDES)) = \{.*?\n    \};"
        r"(?:\n    // [^\n]*)?", re.DOTALL)
    moves = [(m.group(1), m.group(0)) for m in pattern.finditer(text)]
    assert len(moves) == 2, f"{suffix}: {len(moves)} 件（期待 2 件）"
    new_text = pattern.sub("", text)
    new_text = re.sub(r"\n{3,}", "\n\n", new_text)
    assert f"BLOCK_STATE_773_TO_{suffix}_" not in new_text
    down.write_text(new_text, encoding="utf-8")
    body = "\n\n".join(block for _, block in moves)
    out.write_text(blockstate_header(suffix) + body + "\n\n}\n",
                   encoding="utf-8")
    print(f"RegistryTables773Down{suffix}BlockState.java を書き出した: "
          f"{[n for n, _ in moves]}")

# RegistryRemapper.java と RegistryTables773Expanded.java に static import を追加。
for target in (REMAPPER, EXPANDED):
    java = target.read_text(encoding="utf-8")
    for suffix in SUFFIXES:
        anchor = f"import static io.detergentmc.protocol.RegistryTables773Down{suffix}.*;\n"
        assert java.count(anchor) == 1, (target.name, suffix)
        new_import = (f"import static io.detergentmc.protocol."
                      f"RegistryTables773Down{suffix}BlockState.*;\n")
        assert new_import not in java
        java = java.replace(anchor, anchor + new_import, 1)
    target.write_text(java, encoding="utf-8")
print("RegistryRemapper.java と RegistryTables773Expanded.java に import を追加した")

# insert-773-tables.py を直す（再生成時に同じ振り分けになるように）。
ins = INSERT_TABLES.read_text(encoding="utf-8")
old_files = '''TABLES773_COMPOSED_DOWN_FILES = {
    suffix: (REPO_ROOT / "detergentmc-protocol" / "src" / "main" / "java"
             / "io" / "detergentmc" / "protocol"
             / f"RegistryTables773Down{suffix}.java")
    for suffix in ("763", "764", "765", "766", "767", "768", "769", "770", "771")
}'''
new_files = old_files + '''

TABLES773_COMPOSED_DOWN_BLOCKSTATE_FILES = {
    suffix: (REPO_ROOT / "detergentmc-protocol" / "src" / "main" / "java"
             / "io" / "detergentmc" / "protocol"
             / f"RegistryTables773Down{suffix}BlockState.java")
    for suffix in ("763", "764", "765", "766", "767", "768", "769", "770", "771")
}'''
assert ins.count(old_files) == 1
ins = ins.replace(old_files, new_files, 1)
old_header = '''def is_composed_up(name):'''
new_header = '''def composed_down_blockstate_header(suffix):
    return f"""package io.detergentmc.protocol;

/**
 * 773（1.21.9／1.21.10）のレジストリ対応表のうち、773→{suffix} の
 * ブロック状態の下向きの合成表（区間＋上書きの生の表）。
 *
 * <p>{{@link RegistryTables773Down{suffix}}} から分離した。理由は
 * バイトコードの制限：ブロック状態の下向き表を Down{suffix} に置くと
 * 静的初期化子（{{@code <clinit>}}）が 64KB を超えてコンパイルできなくなる
 * （\\\\"code too large\\\\"）ため。763→773 のブロック状態は 29,671 件と件数
 * が多く、上書きも 6,000 件を超える。このクラスの表は
 * {{@link RegistryRemapper}} と {{@link RegistryTables773Expanded}}
 * から static import で同じ名前のまま参照する。
 *
 * <p>表の内容と検証は {{@code detergentmc-protocol/tools/}} を参照：
 * 生成は {{@code generate-remap-tables.py --target 773}}、
 * 検証は {{@code verify-773-tables.py}}。
 */
final class RegistryTables773Down{suffix}BlockState {{

    private RegistryTables773Down{suffix}BlockState() {{
    }}

"""


def is_composed_up(name):'''
assert ins.count(old_header) == 1
ins = ins.replace(old_header, new_header, 1)
old_guard = '''    if (TABLES773_FILE.exists() or TABLES773_COMPOSED_FILE.exists()
            or TABLES773_SOUND_FILE.exists()
            or any(p.exists() for p in TABLES773_COMPOSED_DOWN_FILES.values())):'''
new_guard = '''    if (TABLES773_FILE.exists() or TABLES773_COMPOSED_FILE.exists()
            or TABLES773_SOUND_FILE.exists()
            or any(p.exists() for p in TABLES773_COMPOSED_DOWN_FILES.values())
            or any(p.exists()
                   for p in TABLES773_COMPOSED_DOWN_BLOCKSTATE_FILES.values())):'''
assert ins.count(old_guard) == 1
ins = ins.replace(old_guard, new_guard, 1)
old_route = '''    direct = []
    composed_up = []
    sound_names = []
    composed_down = {suffix: [] for suffix in TABLES773_COMPOSED_DOWN_FILES}
    for name, block in tables.items():
        # private を外して package-private にする（767 と同じ）。
        decl = block.replace("    private static final ",
                             "    static final ", 1)
        if name.startswith("SOUND_NAME_FALLBACKS"):
            sound_names.append(decl)
        elif is_composed_up(name):
            composed_up.append(decl)
        elif any(is_composed_down(name, suffix)
                 for suffix in TABLES773_COMPOSED_DOWN_FILES):
            for suffix in TABLES773_COMPOSED_DOWN_FILES:
                if is_composed_down(name, suffix):
                    composed_down[suffix].append(decl)
        else:
            direct.append(decl)'''
new_route = '''    direct = []
    composed_up = []
    sound_names = []
    composed_down = {suffix: [] for suffix in TABLES773_COMPOSED_DOWN_FILES}
    composed_down_bs = {suffix: []
                        for suffix in TABLES773_COMPOSED_DOWN_BLOCKSTATE_FILES}
    for name, block in tables.items():
        # private を外して package-private にする（767 と同じ）。
        decl = block.replace("    private static final ",
                             "    static final ", 1)
        if name.startswith("SOUND_NAME_FALLBACKS"):
            sound_names.append(decl)
        elif is_composed_up(name):
            composed_up.append(decl)
        elif name.startswith("BLOCK_STATE_773_TO_"):
            # ブロック状態の下向き表は対象ごとの別クラスへ（<clinit> の
            # 64KB 制限のため。Down763 だけで 8,670 個の int がある）。
            for suffix in TABLES773_COMPOSED_DOWN_BLOCKSTATE_FILES:
                if is_composed_down(name, suffix):
                    composed_down_bs[suffix].append(decl)
                    break
        elif any(is_composed_down(name, suffix)
                 for suffix in TABLES773_COMPOSED_DOWN_FILES):
            for suffix in TABLES773_COMPOSED_DOWN_FILES:
                if is_composed_down(name, suffix):
                    composed_down[suffix].append(decl)
        else:
            direct.append(decl)'''
assert ins.count(old_route) == 1
ins = ins.replace(old_route, new_route, 1)
old_write = '''    for suffix, path in TABLES773_COMPOSED_DOWN_FILES.items():
        path.write_text(
            composed_down_header(suffix) + "\\n\\n".join(composed_down[suffix])
            + "\\n\\n}\\n", encoding="utf-8")'''
new_write = '''    for suffix, path in TABLES773_COMPOSED_DOWN_FILES.items():
        path.write_text(
            composed_down_header(suffix) + "\\n\\n".join(composed_down[suffix])
            + "\\n\\n}\\n", encoding="utf-8")
    for suffix, path in TABLES773_COMPOSED_DOWN_BLOCKSTATE_FILES.items():
        path.write_text(
            composed_down_blockstate_header(suffix)
            + "\\n\\n".join(composed_down_bs[suffix])
            + "\\n\\n}\\n", encoding="utf-8")'''
assert ins.count(old_write) == 1
ins = ins.replace(old_write, new_write, 1)
old_print = '''    for suffix in TABLES773_COMPOSED_DOWN_FILES:
        print(f"RegistryTables773Down{suffix}.java を書き出した: "
              f"{len(composed_down[suffix])} 件の表")'''
new_print = old_print + '''
    for suffix in TABLES773_COMPOSED_DOWN_BLOCKSTATE_FILES:
        print(f"RegistryTables773Down{suffix}BlockState.java を書き出した: "
              f"{len(composed_down_bs[suffix])} 件の表")'''
assert ins.count(old_print) == 1
ins = ins.replace(old_print, new_print, 1)
INSERT_TABLES.write_text(ins, encoding="utf-8")

# verify-773-tables.py の TABLES_FILES に新ファイルを追加。
ver = VERIFY.read_text(encoding="utf-8")
old_list = '''                 "RegistryTables773Down771.java", "RegistryTables773SoundNames.java")'''
new_list = '''                 "RegistryTables773Down771.java", "RegistryTables773SoundNames.java",
                 "RegistryTables773Down763BlockState.java",
                 "RegistryTables773Down764BlockState.java",
                 "RegistryTables773Down765BlockState.java",
                 "RegistryTables773Down766BlockState.java",
                 "RegistryTables773Down767BlockState.java",
                 "RegistryTables773Down768BlockState.java",
                 "RegistryTables773Down769BlockState.java",
                 "RegistryTables773Down770BlockState.java",
                 "RegistryTables773Down771BlockState.java")'''
assert ver.count(old_list) == 1
ver = ver.replace(old_list, new_list, 1)
VERIFY.write_text(ver, encoding="utf-8")

# make-773-verify.py の TABLES_FILES の rep も同じく直す（再生成時のため）。
MAKE_VERIFY = SCRIPT_DIR / "make-773-verify.py"
mk = MAKE_VERIFY.read_text(encoding="utf-8")
old_mk = '''    '                 "RegistryTables773Down771.java", "RegistryTables773SoundNames.java")','''
new_mk = '''    '                 "RegistryTables773Down771.java", "RegistryTables773SoundNames.java",\\n'
    '                 "RegistryTables773Down763BlockState.java",\\n'
    '                 "RegistryTables773Down764BlockState.java",\\n'
    '                 "RegistryTables773Down765BlockState.java",\\n'
    '                 "RegistryTables773Down766BlockState.java",\\n'
    '                 "RegistryTables773Down767BlockState.java",\\n'
    '                 "RegistryTables773Down768BlockState.java",\\n'
    '                 "RegistryTables773Down769BlockState.java",\\n'
    '                 "RegistryTables773Down770BlockState.java",\\n'
    '                 "RegistryTables773Down771BlockState.java")','''
assert mk.count(old_mk) == 1, "make-773-verify.py の TABLES_FILES が見つからない"
mk = mk.replace(old_mk, new_mk, 1)
MAKE_VERIFY.write_text(mk, encoding="utf-8")
print("insert-773-tables.py と verify-773-tables.py を更新した")
