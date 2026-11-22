#!/usr/bin/env python3
"""gen-775 の shift() から shift776() を作り、patch-775 の EDITS を
gen-776 の main() と同じ分類・shift で変換して監査するスクリプト。
make-776 の part 3（FIX_776）の材料を作るための一時スクリプト。
"""
import ast
import importlib.util
import re
import sys
from pathlib import Path

TOOLS = Path("/home/hatch/workspace/detergentmc/detergentmc-protocol/tools")

# --- gen-775 の shift() を読み込む ---
spec = importlib.util.spec_from_file_location("gen775", TOOLS / "gen-775-proxysession.py")
gen775 = importlib.util.module_from_spec(spec)
spec.loader.exec_module(gen775)
shift775 = gen775.shift

GEN775_SRC = (TOOLS / "gen-775-proxysession.py").read_text(encoding="utf-8")

# --- shift776() を gen-775 の shift() ソースから作る ---
# protect/restore/正規表現〜shift() までを抜き出し、版数まわりだけ手で書き換える。
m = re.search(r"(def protect\(text, pattern, repl_fn\):.*?return restore\(text, holders\)\n)\n\nFIX_EDITS = \{",
              GEN775_SRC, re.S)
assert m, "shift() 周りが見つからない"
shift_src = m.group(1)

# バージョン文字列ブロック（2)を丸ごと手書きの 776 版に置き換える。
OLD_VER = '''    # 2) バージョン文字列
    text = text.replace("1.21.11", "26.1")'''
i = shift_src.index(OLD_VER)
j = shift_src.index('    # pack769/pack770/pack771 は数字シフトの対象外にする', i)
NEW_VER = '''    # 2) バージョン文字列
    text = text.replace("26.1", "26.2")
    text = text.replace("1.21.11", "26.1")
    text = text.replace("1.21.9／1.21.10", "1.21.11")
    text = text.replace("1.21.10", "1.21.11")
    text = text.replace("1.21.9", "1.21.11")
    text = text.replace("1.21.7／1.21.8", "1.21.9／1.21.10")
    text = text.replace("1.21.8", "1.21.10")
    text = text.replace("1.21.7", "1.21.9")
    text = text.replace("1.21.6", "1.21.7／1.21.8")
    text = text.replace("1.21.5", "1.21.6")
    text = text.replace("1.21.4", "1.21.5")
    text = text.replace("1.21.2／1.21.3", "1.21.4")
    text = text.replace("1.21.2", "1.21.4")
    text = text.replace("1.21／1.21.1", "1.21.2／1.21.3")
    text = text.replace("V26_1", "V26_2")
    text = text.replace("V1_21_11", "V26_1")
    text = text.replace("V1_21_9", "V1_21_11")
    text = text.replace("V1_21_7", "V1_21_9")
    text = text.replace("V1_21_6", "V1_21_7")
    text = text.replace("V1_21_5", "V1_21_6")
    text = text.replace("V1_21_4", "V1_21_5")
    text = text.replace("V1_21_2", "V1_21_4")
    text = text.replace("PROTOCOL_775", "PROTOCOL_776")
    text = text.replace("PROTOCOL_774", "PROTOCOL_775")
    text = text.replace("PROTOCOL_773", "PROTOCOL_774")
    text = text.replace("PROTOCOL_772", "PROTOCOL_773")
    text = text.replace("PROTOCOL_771", "PROTOCOL_772")
    text = text.replace("PROTOCOL_770", "PROTOCOL_771")
    text = text.replace("PROTOCOL_769", "PROTOCOL_770")
    text = text.replace("pack775", "pack776")
    text = text.replace("pack774", "pack775")
    text = text.replace("pack773", "pack774")
    text = text.replace("pack772", "pack773")
    text = text.replace("pack771", "pack772")
    text = text.replace("pack770", "pack771")
    text = text.replace("pack769", "pack770")
'''
shift_src = shift_src[:i] + NEW_VER + shift_src[j:]

# 残りの版数：775→776 を先にプレースホルダへ、774→775、773→774。
shift_src = shift_src.replace('"775"', '"\x01776"')
shift_src = shift_src.replace('"774"', '"775"')
shift_src = shift_src.replace('"773"', '"774"')
shift_src = shift_src.replace('"\x01776"', '"776"')
# 正規表現内の 774/773（引用符なし）もずらす。
shift_src = shift_src.replace("is(?:Server)?774\\(\\)", "is(?:Server)?775\\(\\)")
shift_src = shift_src.replace("is(?:Server)?773\\(\\)", "is(?:Server)?774\\(\\)")
shift_src = shift_src.replace('is(Server)?774\\(\\)$', 'is(Server)?775\\(\\)$')
shift_src = shift_src.replace('is(Server)?773\\(\\)$', 'is(Server)?774\\(\\)$')
# is_chain_sub / is_chain_sub_770 の suffix 文字列（汎用置換の対象外）。
shift_src = shift_src.replace(
    'suffix = "isServer775()" if last.group(1) else "is775()"',
    'suffix = "isServer776()" if last.group(1) else "is776()"')
shift_src = shift_src.replace(
    'suffix = "isServer774()" if last.group(1) else "is774()"',
    'suffix = "isServer775()" if last.group(1) else "is775()"')
shift_src = shift_src.replace("pack(?:770|771|772|773|774|775)",
                              "pack(?:770|771|772|773|774|775|776)")
# 数値シフト部：汎用の置換で壊れた3行を、3段シフト（775→776・774→775・
# 773→774）の5行に書き直す。gen-775 の shift が 774・773 の2段だったのと
# 同じ要領で、プレースホルダで上位2段を保護してから 773→774 をずらす。
lines = shift_src.split("\n")
out_lines = []
for ln in lines:
    s = ln.strip()
    if s.startswith('text = re.sub(r"(?<!\\d)77'):
        if "\\x01" in ln:
            out_lines.append('    text = re.sub(r"(?<!\\d)775(?!\\d)", "\\x01775", text)')
            out_lines.append('    text = re.sub(r"(?<!\\d)774(?!\\d)", "\\x01774", text)')
            out_lines.append('    text = re.sub(r"(?<!\\d)773(?!\\d)", "774", text)')
        else:
            # 773→774 の行は上で既に出したので落とす。
            continue
    elif s.startswith('text = text.replace("\\x01'):
        out_lines.append('    text = text.replace("\\x01774", "775")')
        out_lines.append('    text = text.replace("\\x01775", "776")')
    else:
        out_lines.append(ln)
shift_src = "\n".join(out_lines)
# （注：親の原本にあった残存 774/773 の表示は、import 時の騒音になるため
#  リポジトリ版では削った。内容は自己テスト12件で検証済み。）

ns = {"_counter": [0], "warnings": []}
exec("import re\n" + shift_src, ns)
shift776 = ns["shift"]

__main__ = __name__ == "__main__"
if __name__ == "__main__":
    # --- shift776 の簡易テスト ---
    gen775.warnings.clear()
    tests = [
        ("is775() || is774() を含む鎖", "if (is773() || is774() || is775()) {",
         "if (is773() || is774() || is775() || is776()) {"),
        ("単独 is775()", "if (is775()) {", "if (is776()) {"),
        ("矢印チェーン 775→774→773（先頭に776を足し全部残す）", "// 775→774→773へ変換する", "// 776→775→774→773へ変換する"),
        ("2要素ペア 774→773", "// 774→773へ変換する", "// 775→774へ変換する"),
        ("V26_1", "ProtocolVersion.V26_1", "ProtocolVersion.V26_2"),
        ("26.1 文字列（PROTO_VER で保護され不変）", "775＝26.1", "775＝26.1"),
        ("pack775", "private ResourcePack pack775;", "private ResourcePack pack776;"),
        ("0x31 は不変", "new MinecraftPacket(0x31, packet.payload())",
         "new MinecraftPacket(0x31, packet.payload())"),
        ("is775() ? 0x76 の罠", "orElse(is775() ? 0x76 : is773() || is774() ? 0x74 : 0x69);",
         "orElse(is776() ? 0x76 : is773() || is774() || is775() ? 0x74 : 0x69);"),
        ("翻訳子 773To774→774To775", "translateRegistryData773To774(x)",
         "translateRegistryData774To775(x)"),
        ("翻訳子 772To773→772To774（潰れ・FIXで直す）", "translateRegistryData772To773(x)",
         "translateRegistryData772To774(x)"),
        ("単独 is773()→is774()", "if (is773()) {", "if (is774()) {"),
    ]
    failed = 0
    for label, inp, expected in tests:
        gen775.warnings.clear()
        got = shift776(inp)
        ok = got == expected
        if not ok:
            failed += 1
        print(("OK  " if ok else "NG  ") + label)
        if not ok:
            print("   in:      ", inp)
            print("   expected:", expected)
            print("   got:     ", got)
    print("shift776 テスト失敗:", failed)
