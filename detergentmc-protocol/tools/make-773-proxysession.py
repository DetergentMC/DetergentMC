#!/usr/bin/env python3
"""gen-772-proxysession.py から gen-773-proxysession.py を生成する。

方針：772 生成器を写し、shift() の「最新版」を 771→772 に進める。
shift() 内の版数の写像は「1段ずらす」のではなく、新しい版の写像を
追加し、古い写像は残す（例：V1_21_6→V1_21_7 は残し、V1_21_7→V1_21_9 を
追加する）。FIX_EDITS／FIX_EDITS_FULL の Java ブロックは版数だけを
ずらした 773 用になる（pack_format 数値と削除版の注記は手で直す）。
"""
import re
from pathlib import Path

SRC = Path("/home/hatch/workspace/detergentmc/detergentmc-protocol/tools/gen-772-proxysession.py")
OUT = Path("/home/hatch/workspace/detergentmc/detergentmc-protocol/tools/gen-773-proxysession.py")

text = SRC.read_text(encoding="utf-8")

def rep(old, new, count=1):
    global text
    assert text.count(old) == count, f"一致しない: {old[:60]!r} ({text.count(old)}件)"
    text = text.replace(old, new, count)

# --- SRC/OUT ---
rep('SRC = Path("/home/hatch/workspace/detergentmc/detergentmc-protocol/tools/patch-proxysession-771.py")',
    'SRC = Path("/home/hatch/workspace/detergentmc/detergentmc-protocol/tools/patch-proxysession-772.py")')
rep('OUT = Path("/home/hatch/workspace/detergentmc/detergentmc-protocol/tools/patch-proxysession-772.py")',
    'OUT = Path("/home/hatch/workspace/detergentmc/detergentmc-protocol/tools/patch-proxysession-773.py")')

# --- docstring の版数（769パッチ→770パッチ、771→772、772→773） ---
rep('"""patch-proxysession-769.py から patch-proxysession-771.py を生成する。',
    '"""patch-proxysession-770.py から patch-proxysession-772.py を生成する。')
# （docstring 全体は生成後に目視で直す。ここでは先頭だけ。）

# --- shift_chain：771 を 772 に、772 を 773 に ---
rep('''    if len(nums) >= 3 and nums[0] == "771":
        # 771から始まる多段チェーンは段が1つ増える（771→770→769 は
        # 772→771→770→769 になる）。末端（765/766/767）は固定。
        return "772" + sep + m.group(0)
    if nums[-1] == "771":
        return m.group(0) + sep + "772"''',
    '''    if len(nums) >= 3 and nums[0] == "772":
        # 772から始まる多段チェーンは段が1つ増える（772→771→770 は
        # 773→772→771→770 になる）。末端（765/766/767）は固定。
        return "773" + sep + m.group(0)
    if nums[-1] == "772":
        return m.group(0) + sep + "773"''')
rep('''    if len(nums) == 2:
        # 2要素のペアは1段ずらす（770→771 は 771→772 になる）。
        return sep.join(str(int(n) + 1) for n in nums)''',
    '''    if len(nums) == 2:
        # 2要素のペアは1段ずらす（771→772 は 772→773 になる）。
        return sep.join(str(int(n) + 1) for n in nums)''')

# --- shift_list：771→772、770→771 ---
rep('''    if nums[-1] == "771":
        return m.group(0) + sep + "772"
    if nums[-1] == "770":
        # 新バージョンの一つ下まで列挙しているリストは 771 を追加する。
        return m.group(0) + sep + "771"''',
    '''    if nums[-1] == "772":
        return m.group(0) + sep + "773"
    if nums[-1] == "771":
        # 新バージョンの一つ下まで列挙しているリストは 772 を追加する。
        return m.group(0) + sep + "772"''')

# --- IS_CHAIN / IS_CHAIN_770：771→772、770→771 ---
rep('IS_CHAIN = re.compile(r"((?:is(?:Server)?\\d{3}\\(\\)\\s*\\|\\|\\s*)+is(?:Server)?771\\(\\))")',
    'IS_CHAIN = re.compile(r"((?:is(?:Server)?\\d{3}\\(\\)\\s*\\|\\|\\s*)+is(?:Server)?772\\(\\))")')
rep('IS_CHAIN_770 = re.compile(r"((?:is(?:Server)?\\d{3}\\(\\)\\s*\\|\\|\\s*)+is(?:Server)?770\\(\\))")',
    'IS_CHAIN_770 = re.compile(r"((?:is(?:Server)?\\d{3}\\(\\)\\s*\\|\\|\\s*)+is(?:Server)?771\\(\\))")')
rep('''    # 1b) isチェーン（is764() || ... || is771()）に is772() を追加し、''',
    '''    # 1b) isチェーン（is764() || ... || is772()）に is773() を追加し、''')
rep('''        last = re.search(r"is(Server)?771\\(\\)$", chain)
        suffix = "isServer772()" if last.group(1) else "is772()"''',
    '''        last = re.search(r"is(Server)?772\\(\\)$", chain)
        suffix = "isServer773()" if last.group(1) else "is773()"''')
rep('''        last = re.search(r"is(Server)?770\\(\\)$", chain)
        suffix = "isServer771()" if last.group(1) else "is771()"''',
    '''        last = re.search(r"is(Server)?771\\(\\)$", chain)
        suffix = "isServer772()" if last.group(1) else "is772()"''')

# --- バージョン文字列：新しい写像を追加（古い写像は残す） ---
rep('''    text = text.replace("1.21.6", "1.21.7")
    text = text.replace("1.21.5", "1.21.6")''',
    '''    text = text.replace("1.21.7／1.21.8", "1.21.9／1.21.10")
    text = text.replace("1.21.7", "1.21.9")
    text = text.replace("1.21.8", "1.21.10")
    text = text.replace("1.21.6", "1.21.7")
    text = text.replace("1.21.5", "1.21.6")''')
rep('''    text = text.replace("V1_21_6", "V1_21_7")
    text = text.replace("V1_21_5", "V1_21_6")''',
    '''    text = text.replace("V1_21_7", "V1_21_9")
    text = text.replace("V1_21_6", "V1_21_7")
    text = text.replace("V1_21_5", "V1_21_6")''')
rep('''    text = text.replace("PROTOCOL_771", "PROTOCOL_772")
    text = text.replace("PROTOCOL_770", "PROTOCOL_771")''',
    '''    text = text.replace("PROTOCOL_772", "PROTOCOL_773")
    text = text.replace("PROTOCOL_771", "PROTOCOL_772")
    text = text.replace("PROTOCOL_770", "PROTOCOL_771")''')
rep('''    text = text.replace("pack771", "pack772")
    text = text.replace("pack770", "pack771")''',
    '''    text = text.replace("pack772", "pack773")
    text = text.replace("pack771", "pack772")
    text = text.replace("pack770", "pack771")''')
rep('''    text = re.sub(r"pack(?:770|771|772)", pack_sub, text)''',
    '''    text = re.sub(r"pack(?:770|771|772|773)", pack_sub, text)''')

# --- 単独の数字：772→773、771→772（770→771 は既存の行で対応） ---
rep('''    text = re.sub(r"(?<!\\d)771(?!\\d)", "\\x01771", text)
    text = re.sub(r"(?<!\\d)770(?!\\d)", "771", text)
    text = text.replace("\\x01771", "772")''',
    '''    text = re.sub(r"(?<!\\d)772(?!\\d)", "\\x01772", text)
    text = re.sub(r"(?<!\\d)771(?!\\d)", "772", text)
    text = text.replace("\\x01772", "773")''')

OUT.write_text(text, encoding="utf-8")
print("gen-773-proxysession.py を書き出した（shift() と SRC/OUT のみ）。")
print("FIX_EDITS／FIX_EDITS_FULL は未調整（772 のまま）。")
print("次の手順：FIX_EDITS／FIX_EDITS_FULL を 773 用に手で直す。")
