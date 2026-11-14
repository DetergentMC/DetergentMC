#!/usr/bin/env python3
"""RegistryTables773.java の音の名前表（SOUND_NAME_FALLBACKS_*）を
RegistryTables773SoundNames.java へ移す（1回だけの作業用）。

理由：773 の音の名前表は 18 件・1,635 対あり、RegistryTables773.java の
静的初期化子（<clinit>）が 64KB を超えて "code too large" でコンパイル
できなくなる。RegistryTables772.java（16 件・1,205 対）は制限内だったが、
773 はブロック状態の下向き上書き（3,450 件）も増えて制限を越えた。
表そのものは中身を変えず、置き場所だけ別クラスへ移す。RegistryRemapper
側の参照は static import で解決するため、メソッド本体は一切変わらない。

あわせて insert-773-tables.py を直し、再生成時に同じ振り分けになる
ようにする（音の名前表は RegistryTables773SoundNames.java へ）。
verify-773-tables.py の TABLES_FILES にも新ファイルを追加する
（H の音の名前の検証で使うため）。
"""
import re
import sys
from pathlib import Path

SCRIPT_DIR = Path(__file__).resolve().parent
REPO_ROOT = SCRIPT_DIR.parent.parent
PROTO = (REPO_ROOT / "detergentmc-protocol" / "src" / "main" / "java"
         / "io" / "detergentmc" / "protocol")
TABLES = PROTO / "RegistryTables773.java"
SOUND_NAMES = PROTO / "RegistryTables773SoundNames.java"
REMAPPER = PROTO / "RegistryRemapper.java"
INSERT_TABLES = SCRIPT_DIR / "insert-773-tables.py"
VERIFY = SCRIPT_DIR / "verify-773-tables.py"

text = TABLES.read_text(encoding="utf-8")
pattern = re.compile(
    r"    static final String\[\]\[\] (SOUND_NAME_FALLBACKS_\w+) = \{.*?\n    \};"
    r"(?:\n    // [^\n]*)?", re.DOTALL)
moves = [(m.group(1), m.group(0)) for m in pattern.finditer(text)]
assert len(moves) == 18, f"音の名前表が {len(moves)} 件（期待 18 件）"
names = [n for n, _ in moves]
print("移動する音の名前表:", len(moves), "件")
for n in names:
    print(" -", n)

new_text = pattern.sub("", text)
# 削除でできた空行 3 連以上を 2 つ（空行 1 つ分）にそろえる。
new_text = re.sub(r"\n{3,}", "\n\n", new_text)
assert "SOUND_NAME_FALLBACKS" not in new_text
TABLES.write_text(new_text, encoding="utf-8")

header = """package io.detergentmc.protocol;

/**
 * 773（1.21.9／1.21.10）の音の名前の代替表（SOUND_NAME_FALLBACKS_*）。
 *
 * <p>{@link RegistryTables773} から分離した。理由はバイトコードの制限：
 * 音の名前表（18 件・1,635 対）を RegistryTables773 に置くと静的初期化子
 * （{@code <clinit>}）が 64KB を超えてコンパイルできなくなる
 * （\"code too large\"）ため。このクラスの表は {@link RegistryRemapper}
 * から static import で同じ名前のまま参照する。
 *
 * <p>表の内容と検証は {@code detergentmc-protocol/tools/} を参照：
 * 生成は {@code generate-remap-tables.py --target 773}、
 * 検証は {@code verify-773-tables.py}。
 */
final class RegistryTables773SoundNames {

    private RegistryTables773SoundNames() {
    }

"""
body = "\n\n".join(block for _, block in moves)
SOUND_NAMES.write_text(header + body + "\n\n}\n", encoding="utf-8")

# RegistryRemapper.java に static import を追加。
java = REMAPPER.read_text(encoding="utf-8")
anchor = "import static io.detergentmc.protocol.RegistryTables773Down771.*;\n"
assert java.count(anchor) == 1
assert "RegistryTables773SoundNames.*" not in java
java = java.replace(
    anchor,
    anchor + "import static io.detergentmc.protocol.RegistryTables773SoundNames.*;\n",
    1)
REMAPPER.write_text(java, encoding="utf-8")

# insert-773-tables.py を直す（再生成時に同じ振り分けになるように）。
ins = INSERT_TABLES.read_text(encoding="utf-8")
old_files = '''TABLES773_COMPOSED_FILE = (REPO_ROOT / "detergentmc-protocol" / "src" / "main" / "java"
                  / "io" / "detergentmc" / "protocol" / "RegistryTables773ComposedUp.java")'''
new_files = old_files + '''
TABLES773_SOUND_FILE = (REPO_ROOT / "detergentmc-protocol" / "src" / "main" / "java"
                  / "io" / "detergentmc" / "protocol" / "RegistryTables773SoundNames.java")'''
assert ins.count(old_files) == 1
ins = ins.replace(old_files, new_files, 1)
old_header = '''COMPOSED_HEADER = """package io.detergentmc.protocol;'''
new_header = '''SOUND_HEADER = """package io.detergentmc.protocol;

/**
 * 773（1.21.9／1.21.10）の音の名前の代替表（SOUND_NAME_FALLBACKS_*）。
 *
 * <p>{@link RegistryTables773} から分離した。理由はバイトコードの制限：
 * 音の名前表（18 件・1,635 対）を RegistryTables773 に置くと静的初期化子
 * （{@code <clinit>}）が 64KB を超えてコンパイルできなくなる
 * （\\"code too large\\"）ため。このクラスの表は {@link RegistryRemapper}
 * から static import で同じ名前のまま参照する。
 *
 * <p>表の内容と検証は {@code detergentmc-protocol/tools/} を参照：
 * 生成は {@code generate-remap-tables.py --target 773}、
 * 検証は {@code verify-773-tables.py}。
 */
final class RegistryTables773SoundNames {

    private RegistryTables773SoundNames() {
    }

"""


COMPOSED_HEADER = """package io.detergentmc.protocol;'''
assert ins.count(old_header) == 1
ins = ins.replace(old_header, new_header, 1)
old_guard = '''    if (TABLES773_FILE.exists() or TABLES773_COMPOSED_FILE.exists()
            or any(p.exists() for p in TABLES773_COMPOSED_DOWN_FILES.values())):
        print("RegistryTables773.java / RegistryTables773ComposedUp.java / "
              "RegistryTables773Down*.java が既にある。何もしない",
              file=sys.stderr)
        return 1'''
new_guard = '''    if (TABLES773_FILE.exists() or TABLES773_COMPOSED_FILE.exists()
            or TABLES773_SOUND_FILE.exists()
            or any(p.exists() for p in TABLES773_COMPOSED_DOWN_FILES.values())):
        print("RegistryTables773.java / RegistryTables773ComposedUp.java / "
              "RegistryTables773SoundNames.java / "
              "RegistryTables773Down*.java が既にある。何もしない",
              file=sys.stderr)
        return 1'''
assert ins.count(old_guard) == 1
ins = ins.replace(old_guard, new_guard, 1)
old_route = '''    direct = []
    composed_up = []
    composed_down = {suffix: [] for suffix in TABLES773_COMPOSED_DOWN_FILES}
    for name, block in tables.items():
        # private を外して package-private にする（767 と同じ）。
        decl = block.replace("    private static final ",
                             "    static final ", 1)
        if is_composed_up(name):
            composed_up.append(decl)'''
new_route = '''    direct = []
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
            composed_up.append(decl)'''
assert ins.count(old_route) == 1
ins = ins.replace(old_route, new_route, 1)
old_write = '''    TABLES773_FILE.write_text(
        HEADER + "\\n\\n".join(direct) + "\\n\\n}\\n", encoding="utf-8")'''
new_write = '''    TABLES773_FILE.write_text(
        HEADER + "\\n\\n".join(direct) + "\\n\\n}\\n", encoding="utf-8")
    TABLES773_SOUND_FILE.write_text(
        SOUND_HEADER + "\\n\\n".join(sound_names) + "\\n\\n}\\n", encoding="utf-8")'''
assert ins.count(old_write) == 1
ins = ins.replace(old_write, new_write, 1)
old_print = '''    print(f"RegistryTables773.java を書き出した: {len(direct)} 件の表")'''
new_print = (old_print + '''
    print(f"RegistryTables773SoundNames.java を書き出した: {len(sound_names)} 件の表")''')
assert ins.count(old_print) == 1
ins = ins.replace(old_print, new_print, 1)
# docstring の振り分け説明を直す。
old_doc = '''    を RegistryTables773.java（隣接表・音の名前・属性・エンチャント）、
    RegistryTables773ComposedUp.java（上向きの合成表）、'''
new_doc = '''    を RegistryTables773.java（隣接表・属性・エンチャント）、
    RegistryTables773SoundNames.java（音の名前）、
    RegistryTables773ComposedUp.java（上向きの合成表）、'''
assert ins.count(old_doc) == 1
ins = ins.replace(old_doc, new_doc, 1)
INSERT_TABLES.write_text(ins, encoding="utf-8")

# verify-773-tables.py の TABLES_FILES に新ファイルを追加。
ver = VERIFY.read_text(encoding="utf-8")
old_list = '''                 "RegistryTables773Down769.java", "RegistryTables773Down770.java",
                 "RegistryTables773Down771.java")'''
new_list = '''                 "RegistryTables773Down769.java", "RegistryTables773Down770.java",
                 "RegistryTables773Down771.java", "RegistryTables773SoundNames.java")'''
assert ver.count(old_list) == 1
ver = ver.replace(old_list, new_list, 1)
VERIFY.write_text(ver, encoding="utf-8")

# make-773-verify.py の TABLES_FILES の rep も同じく直す（再生成時のため）。
MAKE_VERIFY = SCRIPT_DIR / "make-773-verify.py"
mk = MAKE_VERIFY.read_text(encoding="utf-8")
old_mk = '''    '                 "RegistryTables773Down769.java", "RegistryTables773Down770.java",\\n'
    '                 "RegistryTables773Down771.java")','''
new_mk = '''    '                 "RegistryTables773Down769.java", "RegistryTables773Down770.java",\\n'
    '                 "RegistryTables773Down771.java", "RegistryTables773SoundNames.java")','''
assert mk.count(old_mk) == 1, "make-773-verify.py の TABLES_FILES が見つからない"
mk = mk.replace(old_mk, new_mk, 1)
MAKE_VERIFY.write_text(mk, encoding="utf-8")

print("RegistryTables773SoundNames.java を書き出した")
print("insert-773-tables.py と verify-773-tables.py を更新した")
