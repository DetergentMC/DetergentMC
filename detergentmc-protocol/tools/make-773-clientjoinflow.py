#!/usr/bin/env python3
"""patch-clientjoinflow-772.py から patch-clientjoinflow-773.py を生成する。

772の edit の new（＝773の old）をそのまま流用し、772→773の差分だけを
shift() で写す。class javadoc の773行だけ手書きする（773は設定フェーズに
Code of Conduct が新規で、「新規パケットは無い」は誤り）。
"""
import ast, difflib, importlib.util
from pathlib import Path

TOOLS = Path(__file__).resolve().parent
spec = importlib.util.spec_from_file_location("gen773ps", TOOLS / "gen-773-proxysession.py")
g = importlib.util.module_from_spec(spec); spec.loader.exec_module(g)
shift = g.shift


def load_edits(fn):
    src = open(TOOLS / fn, encoding="utf-8").read()
    tree = ast.parse(src)
    assigns = {n.targets[0].id: n.value for n in tree.body
               if isinstance(n, ast.Assign) and len(n.targets)==1 and isinstance(n.targets[0], ast.Name)}
    return ast.literal_eval(assigns["EDITS"])


e772 = {name: (old, new) for name, old, new in load_edits("patch-clientjoinflow-772.py")}
live = open(TOOLS.parent / "src" / "main" / "java" / "io" / "detergentmc" / "protocol" / "ClientJoinFlow.java",
            encoding="utf-8").read()

ENTRY773 = ''' *   <li>1.21.9／1.21.10（773）：772と同形。ハンドシェイク → ログイン
 *       → <b>設定フェーズ</b> → プレイ → 参加 → ゲーム内。設定フェーズの
 *       パケット番号は772から変わらず、新規は Code of Conduct（0x13）／
 *       Accept Code of Conduct（0x09）。プレイ状態の新規パケットは
 *       デバッグ系が8件。</li>'''


def apply_delta(old773, old772, new772):
    a = old772.split("\n")
    b = new772.split("\n")
    sm = difflib.SequenceMatcher(None, a, b, autojunk=False)
    out = old773.split("\n")
    for tag, i1, i2, j1, j2 in reversed(sm.get_opcodes()):
        if tag == "equal" or tag == "delete":
            continue
        new_lines = [shift(x) for x in b[j1:j2]]
        if tag == "insert":
            out[j1:j1] = new_lines
        elif tag == "replace":
            out[j1:j2] = new_lines
    return "\n".join(out)


new_edits = []
for name772, (old772, new772) in e772.items():
    name773 = name772.replace("772", "773")
    old773 = new772
    assert live.count(old773) == 1, f"{name773}: old_773 が live に {live.count(old773)} 回"
    if name773 == "class javadoc 773 entry":
        new773 = old773 + "\n" + ENTRY773
    elif name773 == "constructor javadoc":
        # 「か」の鎖は shift() が771を落とすため、末尾に「か773」を足すだけ。
        new773 = old773.replace("770か771か772", "770か771か772か773")
    elif name773 == "hasConfigurationPhase javadoc":
        # 「と」の鎖は shift() が771を落とすため、末尾に「と773」を足すだけ。
        new773 = old773.replace("770と771と772はあり", "770と771と772と773はあり")
    else:
        new773 = apply_delta(old773, old772, new772)
    new_edits.append((name773, old773, new773))

doc = '"""ClientJoinFlow.java へ Java 1.21.9／1.21.10（プロトコル773）の受け入れを入れる。\n\n段階の遷移自体は汎用（763以外は設定フェーズあり）で、773も772と同形の\nため、文書の更新だけ。ClientAdapter.supportedClientProtocols() が773を\n含むようになったため、773の ClientJoinFlow 生成はそのまま通る。\n各アンカーはファイル中にちょうど1回だけ現れること。\n"""'
out_lines = [doc, "import sys", "from pathlib import Path", ""]
out_lines.append("PATH = Path(__file__).resolve().parent.parent / \"src\" / \"main\" / \"java\" \\")
out_lines.append("    / \"io\" / \"detergentmc\" / \"protocol\" / \"ClientJoinFlow.java\"")
out_lines.append("")
out_lines.append("EDITS = [")
for name, old, new in new_edits:
    out_lines.append(f"    ({name!r}, {old!r},")
    out_lines.append(f"     {new!r}),")
out_lines.append("]")
out_lines.append("")
out_lines.append("")
out_lines.append("def main():")
out_lines.append("    src = PATH.read_text(encoding=\"utf-8\")")
out_lines.append("    for name, old, new in EDITS:")
out_lines.append("        n = src.count(old)")
out_lines.append("        if n != 1:")
out_lines.append("            print(f\"NG: {name}: anchor が {n} 回見つかった（1回のはず）\", file=sys.stderr)")
out_lines.append("            sys.exit(1)")
out_lines.append("        src = src.replace(old, new, 1)")
out_lines.append("        print(f\"OK: {name}\")")
out_lines.append("    PATH.write_text(src, encoding=\"utf-8\")")
out_lines.append("    print(\"ClientJoinFlow.java に 773 の対応を挿入した\")")
out_lines.append("")
out_lines.append("")
out_lines.append("if __name__ == \"__main__\":")
out_lines.append("    main()")
open(TOOLS / "patch-clientjoinflow-773.py", "w", encoding="utf-8").write("\n".join(out_lines) + "\n")
print(f"patch-clientjoinflow-773.py を生成した（{len(new_edits)} edits）")
