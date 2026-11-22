#!/usr/bin/env python3
"""gen-775-proxysession.py から gen-776-proxysession.py を生成する。

方針：775 生成器を写し、shift() の「最新版」を 774→775 に進める。
shift() 内の版数の写像は「1段ずらす」のではなく、新しい版の写像を
追加し、古い写像は残す（例：V1_21_11→V26_1 は残し、V26_1→V26_2 を
追加する）。FIX_EDITS／FIX_EDITS_FULL の Java ブロックは版数だけを
ずらした 776 用になる（pack_format 数値とパケットIDは手で直す）。

776 固有の注意（775 と違う点）：
- パケットIDは 775→776 で 0 件変わる。唯一の違いは play toServer 0x3e の
  改名（spectate_entity → spectator_action、同じ位置）。
  shift() は 16進リテラル (0x..) をずらさないため、From776／To776 の
  ディスパッチIDは 775 のまま合う。ただし 0x3e を spectate_entity と
  して扱ってはいけない（776 では spectator_action）。
- ペイロード差分は 4 件だけ（本物のバニラ 26.2 の javap で確認済み）。
  Join Game の末尾 boolean onlineMode、Login Success の末尾 UUID sessionId、
  Teams の並び替え＋color の Optional 化、SpectatorAction の varint 形式。
  これらは生成器では作らず、パッチ適用後に ProxySession.java へ手で入れる。
- pack_format は 775 の 84 から 776 はリソース 88・データ 107 になる
  （本物のバニラ 26.2 の version.json より）。
- 設定フェーズは 775 と同形（同じ番号・同じ形式）で中継する。
  Registry Data の並び替えだけ 775→776 が要る。

776 で見つかった新しい罠（docs/PROXY-26.2.md 用）：
- コンストラクタの版チェック：shift が V1_21_2 を落とす（V1_21_2→V1_21_4
  の改名が既存の V1_21_4 と衝突する）。FIX_EDITS_FULL で live から書き直す。
- Respawn／JoinGame の 770〜772・769／768 編集：775 ステージで is773 分岐が
  live に手で復旧されており、patch-775 の edit-new と live が食い違う。
  shift 後の old が live に無いため FIX_EDITS_FULL で live から書き直す。
- 編集名の衝突：handleServerPlayFrom774 774 branch と
  handleServerPlayFrom774 775 branch がどちらも handleServerPlayFrom775 775 branch
  になる。main() に DEDUP を入れ、後者に " (add)" を付けて SKIP する。
- From773 の is776 分岐：呼びが 773To775→774To776 と両端ずれるため
  773To776 に直す（段数も二段→三段）。
- From776 の Respawn／JoinGame776：is775 分岐の ID が 0x50／0x30・0x4F の
  まま残る（775 は 0x52／0x31・0x51）。FIX_BLOCKS で直す。
  From776 の Respawn は is773 分岐も落ちるため復旧する。
- Registry Data の多段連鎖の潰れ（772段・773段の欠落）は CONFIG_776
  ブロックに 6 箇所（順方向）＋5 箇所（逆方向）ある。775 と同じく
  FIX_BLOCKS_ALL（全置換・件数指定）で直す。
"""
import re
import sys
from pathlib import Path

SRC = Path("/home/hatch/workspace/detergentmc/detergentmc-protocol/tools/gen-775-proxysession.py")
OUT = Path("/home/hatch/workspace/detergentmc/detergentmc-protocol/tools/gen-776-proxysession.py")

text = SRC.read_text(encoding="utf-8")
failed = []


def rep(old, new, label="", count=1):
    global text
    n = text.count(old)
    if n != count:
        failed.append(f"{label}: 一致 {n} 件 (期待 {count} 件): {old[:60]!r}")
        return
    text = text.replace(old, new, count)


# --- SRC/OUT ---
rep('SRC = Path("/home/hatch/workspace/detergentmc/detergentmc-protocol/tools/patch-proxysession-774.py")',
    'SRC = Path("/home/hatch/workspace/detergentmc/detergentmc-protocol/tools/patch-proxysession-775.py")')
rep('OUT = Path("/home/hatch/workspace/detergentmc/detergentmc-protocol/tools/patch-proxysession-775.py")',
    'OUT = Path("/home/hatch/workspace/detergentmc/detergentmc-protocol/tools/patch-proxysession-776.py")')

# --- docstring の版数 ---
rep('"""patch-proxysession-774.py から patch-proxysession-775.py を生成する。',
    '"""patch-proxysession-775.py から patch-proxysession-776.py を生成する。')
# （docstring 全体は生成後に目視で直す。ここでは先頭だけ。775 と同じ扱い。）

# --- shift_chain：774 を 775 に、775 を 776 に ---
rep('''    if len(nums) >= 3 and nums[0] == "774":
        # 774から始まる多段チェーンは段が1つ増える（774→773→772 は
        # 775→774→773→772 になる）。末端（765/766/767）は固定。
        return "775" + sep + m.group(0)
    if nums[-1] == "774":
        return m.group(0) + sep + "775"''',
    '''    if len(nums) >= 3 and nums[0] == "775":
        # 775から始まる多段チェーンは段が1つ増える（775→774→773 は
        # 776→775→774→773 になる）。末端（765/766/767）は固定。
        return "776" + sep + m.group(0)
    if nums[-1] == "775":
        return m.group(0) + sep + "776"''')
rep('''    if len(nums) == 2:
        # 2要素のペアは1段ずらす（773→774 は 774→775 になる）。
        return sep.join(str(int(n) + 1) for n in nums)''',
    '''    if len(nums) == 2:
        # 2要素のペアは1段ずらす（774→775 は 775→776 になる）。
        return sep.join(str(int(n) + 1) for n in nums)''')

# --- shift_list：774→775、773→774 ---
rep('''    if nums[-1] == "774":
        return m.group(0) + sep + "775"
    if nums[-1] == "773":
        # 新バージョンの一つ下まで列挙しているリストは 774 を追加する。
        return m.group(0) + sep + "774"''',
    '''    if nums[-1] == "775":
        return m.group(0) + sep + "776"
    if nums[-1] == "774":
        # 新バージョンの一つ下まで列挙しているリストは 775 を追加する。
        return m.group(0) + sep + "775"''')

# --- IS_CHAIN / IS_CHAIN_770：774→775、773→774 ---
rep('IS_CHAIN = re.compile(r"((?:is(?:Server)?\\d{3}\\(\\)\\s*\\|\\|\\s*)+is(?:Server)?774\\(\\))")',
    'IS_CHAIN = re.compile(r"((?:is(?:Server)?\\d{3}\\(\\)\\s*\\|\\|\\s*)+is(?:Server)?775\\(\\))")')
rep('IS_CHAIN_770 = re.compile(r"((?:is(?:Server)?\\d{3}\\(\\)\\s*\\|\\|\\s*)+is(?:Server)?773\\(\\))")',
    'IS_CHAIN_770 = re.compile(r"((?:is(?:Server)?\\d{3}\\(\\)\\s*\\|\\|\\s*)+is(?:Server)?774\\(\\))")')
rep('''    # 1b) isチェーン（is764() || ... || is774()）に is775() を追加し、''',
    '''    # 1b) isチェーン（is764() || ... || is775()）に is776() を追加し、''')
rep('''        last = re.search(r"is(Server)?774\\(\\)$", chain)
        suffix = "isServer775()" if last.group(1) else "is775()"''',
    '''        last = re.search(r"is(Server)?775\\(\\)$", chain)
        suffix = "isServer776()" if last.group(1) else "is776()"''')
rep('''        last = re.search(r"is(Server)?773\\(\\)$", chain)
        suffix = "isServer774()" if last.group(1) else "is774()"''',
    '''        last = re.search(r"is(Server)?774\\(\\)$", chain)
        suffix = "isServer775()" if last.group(1) else "is775()"''')

# --- バージョン文字列：新しい写像を追加（古い写像は残す）。順序が重要：
# 「26.1」→「26.2」を先に、次に「1.21.11」→「26.1」を済ませてから、
# 1.21.9／1.21.10→1.21.11、1.21.10→1.21.11、1.21.9→1.21.11 を行う
# （後から作る 1.21.11／26.1 を二重にずらさないため。「26.1」を先に
# 26.2 にしないと、1.21.11→26.1 で作った 26.1 が 26.2 へ二重シフトする）。
rep('''    # 2) バージョン文字列
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
    text = text.replace("V1_21_11", "V26_1")
    text = text.replace("V1_21_9", "V1_21_11")
    text = text.replace("V1_21_7", "V1_21_9")
    text = text.replace("V1_21_6", "V1_21_7")
    text = text.replace("V1_21_5", "V1_21_6")
    text = text.replace("V1_21_4", "V1_21_5")
    text = text.replace("V1_21_2", "V1_21_4")
    text = text.replace("PROTOCOL_774", "PROTOCOL_775")
    text = text.replace("PROTOCOL_773", "PROTOCOL_774")
    text = text.replace("PROTOCOL_772", "PROTOCOL_773")
    text = text.replace("PROTOCOL_771", "PROTOCOL_772")
    text = text.replace("PROTOCOL_770", "PROTOCOL_771")
    text = text.replace("PROTOCOL_769", "PROTOCOL_770")
    text = text.replace("pack774", "pack775")
    text = text.replace("pack773", "pack774")
    text = text.replace("pack772", "pack773")
    text = text.replace("pack771", "pack772")
    text = text.replace("pack770", "pack771")
    text = text.replace("pack769", "pack770")''',
    '''    # 2) バージョン文字列
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
    text = text.replace("pack769", "pack770")''')
rep('''    text = re.sub(r"pack(?:770|771|772|773|774|775)", pack_sub, text)''',
    '''    text = re.sub(r"pack(?:770|771|772|773|774|775|776)", pack_sub, text)''')

# --- 単独の数字：775→776、774→775、773→774（3段） ---
rep('''    text = re.sub(r"(?<!\\d)774(?!\\d)", "\\x01774", text)
    text = re.sub(r"(?<!\\d)773(?!\\d)", "774", text)
    text = text.replace("\\x01774", "775")''',
    '''    text = re.sub(r"(?<!\\d)775(?!\\d)", "\\x01775", text)
    text = re.sub(r"(?<!\\d)774(?!\\d)", "\\x01774", text)
    text = re.sub(r"(?<!\\d)773(?!\\d)", "774", text)
    text = text.replace("\\x01774", "775")
    text = text.replace("\\x01775", "776")''')

if failed:
    print("失敗:", file=sys.stderr)
    for f in failed:
        print(" -", f, file=sys.stderr)
    sys.exit(1)
print("part 1 OK (SRC/OUT / shift() の写像）")


# --- part 2：手書き定数ブロックと main() の版名 ---
q = chr(39) * 3  # r-string の三連引用符
def replace_section(start, end, new, label):
    """text 中の start から end まで（end を含む）を new で置き換える（1回だけ）。"""
    global text
    i = text.find(start)
    j = text.find(end, i + len(start)) if i >= 0 else -1
    if i < 0 or j < 0:
        failed.append(f"{label}: 開始または終了が見つからない: {start[:50]!r}")
        return
    j += len(end)
    if text.count(start) != 1:
        failed.append(f"{label}: 開始が {text.count(start)} 件")
        return
    text = text[:i] + new + text[j:]

# --- part 3：FIX 類の書き換え ---
# gen-775 の FIX_EDITS／FIX_EDITS_FULL／SKIP_EDITS／ADD_EDITS／FIX_BLOCKS を
# 776 用に丸ごと書き換える（shift では直せない箇所が多いため、部分的な
# rep() ではなく replace_section で辞書全体を置き換える）。
# 各エントリは shift 後のテキスト（~/workspace/tmp776/shifted-edits.txt で計算済み）と
# live の ProxySession.java を突き合わせて確認した。
# キーは shift 後の編集名（例：pack774 field → pack775 field）。
# なお FIX_EDITS_FULL へ移したもの（constructor javadoc／constructor
# validation／serverProtocol() javadoc／Respawn／JoinGame／config 類）は
# FIX_EDITS から落とす（shift 後の old が live と合わないため）。

FIX_EDITS_776 = r'''FIX_EDITS = {'constructor javadoc': [('775＝26.1）', '775＝26.1、776＝26.2）')], 'serverProtocol() javadoc': [('775＝26.1）。 */', '775＝26.1、776＝26.2）。 */')], 'pack775 field': [('776クライアントに送るリソースパック（pack_format 84）', '776クライアントに送るリソースパック（pack_format 88）')], 'packOverrideForClient javadoc': [('出し分けの基準はクライアントのプロトコルである：776→pack776\n     * （pack_format 84）、775→pack775\n     * （pack_format 75）、774→pack774\n     * （pack_format 69）、772→pack773\n     * （pack_format 64）、771→pack772\n     * （pack_format 63）、770→pack771\n     * （pack_format 55）、769→pack770\n     * （pack_format 46）、768→pack768\n     * （pack_format 42）、767→pack767', '出し分けの基準はクライアントのプロトコルである：776→pack776\n     * （pack_format 88）、775→pack775\n     * （pack_format 84）、774→pack774\n     * （pack_format 75）、773→pack773\n     * （pack_format 69）、772→pack772\n     * （pack_format 64）、771→pack771\n     * （pack_format 63）、770→pack770\n     * （pack_format 55）、769→pack769\n     * （pack_format 46）、768→pack768\n     * （pack_format 42）、767→pack767')], 'packOverrideForClient branch': [(': pack770 != null ? pack770\n                    : pack768 != null ? pack768', ': pack770 != null ? pack770\n                    : pack769 != null ? pack769\n                    : pack768 != null ? pack768')], 'startConfigurationClientId': [('is776() ? 0x76 : is773() || is774() || is775() ? 0x74', 'is775() || is776() ? 0x76 : is773() || is774() ? 0x74')], 'spawnPositionClientId': [('is776() ? 0x61 : is773() || is774() || is775() ? 0x5F', 'is775() || is776() ? 0x61 : is773() || is774() ? 0x5F')], 'keepAliveClientId': [('is776() ? 0x2C : is773() || is774() || is775() ? 0x2B', 'is775() || is776() ? 0x2C : is773() || is774() ? 0x2B')], 'pingClientId': [('is776() ? 0x3D : is773() || is774() || is775() ? 0x3B', 'is775() || is776() ? 0x3D : is773() || is774() ? 0x3B')], 'synthesized add-pack': [('is776() ? 0x51 : is773() || is774() || is775() ? 0x4F', 'is775() || is776() ? 0x51 : is773() || is774() ? 0x4F')], 'handleServerPlayFrom763 ResourcePack 775': [('is776() ? 0x51 : is773() || is774() || is775() ? 0x4F', 'is775() || is776() ? 0x51 : is773() || is774() ? 0x4F')], 'handleServerPlayFrom772 775 branch': [('// 776クライアント：773→776へ変換する。', '// 776クライアント：772→776へ変換する。')], 'handleServerPlayFrom770 775 branch': [('// 776クライアント：770→771→772→773→774→775→776へ五段で変換する。', '// 776クライアント：770→771→772→773→774→775→776へ六段で変換する。')], 'handleServerPlayFrom771 775 branch': [('// 776クライアント：771→772→773→774→775→776へ四段で変換する。', '// 776クライアント：771→772→773→774→775→776へ五段で変換する。')], 'handleServerPlayFrom766 775 branch': [('// 内部766は 766→767→768→769→770→771→772→773→774→775→776 の九段、内部767は 767→768→769→770→771→772→773→774→775→776 の八段で送る。', '// 内部766は 766→767→768→769→770→771→772→773→774→775→776 の十段、内部767は 767→768→769→770→771→772→773→774→775→776 の九段で送る。')], 'handleServerPlayFrom775 775 branch': [('// 776クライアント：773→774→775→776へ二段で変換する。', '// 776クライアント：773→774→775→776へ三段で変換する。'), ('            return handleServerPlay774To776(packet);', '            return handleServerPlay773To776(packet);')]}'''

replace_section(
    "FIX_EDITS = {",
    "# (old, new) を丸ごと置き換える編集。shift した (old, new) では\n# アンカーが live に無かったり、意味が変わったりするもの。\nFIX_EDITS_FULL = {",
    FIX_EDITS_776 + "\n\n\n# (old, new) を丸ごと置き換える編集。shift した (old, new) では\n# アンカーが live に無かったり、意味が変わったりするもの。\nFIX_EDITS_FULL = {",
    "FIX_EDITS の書き換え")

FIX_EDITS_FULL_776 = r'''FIX_EDITS_FULL = {'constructor validation': ('if (serverProtocol != ProtocolVersion.V1_19\n                && serverProtocol != ProtocolVersion.V1_19_1\n                && serverProtocol != ProtocolVersion.V1_19_3\n                && serverProtocol != ProtocolVersion.V1_19_4\n                && serverProtocol != ProtocolVersion.V1_20_1\n                && serverProtocol != ProtocolVersion.V1_20_2\n                && serverProtocol != ProtocolVersion.V1_20_4\n                && serverProtocol != ProtocolVersion.V1_20_5\n                && serverProtocol != ProtocolVersion.V1_21\n                && serverProtocol != ProtocolVersion.V1_21_2\n                && serverProtocol != ProtocolVersion.V1_21_4\n                && serverProtocol != ProtocolVersion.V1_21_5\n                && serverProtocol != ProtocolVersion.V1_21_6\n                && serverProtocol != ProtocolVersion.V1_21_7\n                && serverProtocol != ProtocolVersion.V1_21_9\n                && serverProtocol != ProtocolVersion.V1_21_11\n                && serverProtocol != ProtocolVersion.V26_1) {\n            throw new IllegalArgumentException(\n                    "serverProtocol must be 759 (1.19), 760 (1.19.1/1.19.2), 761 (1.19.3), 762 (1.19.4), 763 (1.20.1), 764 (1.20.2), "\n                            + "765 (1.20.4), 766 (1.20.5/1.20.6), 767 (1.21/1.21.1), "\n                            + "768 (1.21.2/1.21.3), 769 (1.21.4), 770 (1.21.5), 771 (1.21.6), 772 (1.21.7), 773 (1.21.9/1.21.10), 774 (1.21.11) or 775 (26.1): "\n                            + serverProtocol);\n        }', 'if (serverProtocol != ProtocolVersion.V1_19\n                && serverProtocol != ProtocolVersion.V1_19_1\n                && serverProtocol != ProtocolVersion.V1_19_3\n                && serverProtocol != ProtocolVersion.V1_19_4\n                && serverProtocol != ProtocolVersion.V1_20_1\n                && serverProtocol != ProtocolVersion.V1_20_2\n                && serverProtocol != ProtocolVersion.V1_20_4\n                && serverProtocol != ProtocolVersion.V1_20_5\n                && serverProtocol != ProtocolVersion.V1_21\n                && serverProtocol != ProtocolVersion.V1_21_2\n                && serverProtocol != ProtocolVersion.V1_21_4\n                && serverProtocol != ProtocolVersion.V1_21_5\n                && serverProtocol != ProtocolVersion.V1_21_6\n                && serverProtocol != ProtocolVersion.V1_21_7\n                && serverProtocol != ProtocolVersion.V1_21_9\n                && serverProtocol != ProtocolVersion.V1_21_11\n                && serverProtocol != ProtocolVersion.V26_1\n                && serverProtocol != ProtocolVersion.V26_2) {\n            throw new IllegalArgumentException(\n                    "serverProtocol must be 759 (1.19), 760 (1.19.1/1.19.2), 761 (1.19.3), 762 (1.19.4), 763 (1.20.1), 764 (1.20.2), "\n                            + "765 (1.20.4), 766 (1.20.5/1.20.6), 767 (1.21/1.21.1), "\n                            + "768 (1.21.2/1.21.3), 769 (1.21.4), 770 (1.21.5), 771 (1.21.6), 772 (1.21.7), 773 (1.21.9/1.21.10), 774 (1.21.11), 775 (26.1) or 776 (26.2): "\n                            + serverProtocol);\n        }'), 'handleServerPlayFrom769 Respawn 775': ('        // Respawn（770：0x4B。769（0x4C）と同形式のため769の変換を使い回す）。\n        if (packet.id() == 0x4B) {\n            if (is775()) {\n                // 775クライアント：Respawn は 0x52（同形式）のまま。\n                return Outcome.toClient(new MinecraftPacket(0x52, packet.payload()));\n            }\n            if (is774()) {\n                // 774クライアント：Respawn は 0x50（同形式）のまま。\n                return Outcome.toClient(new MinecraftPacket(0x50, packet.payload()));\n            }\n', '        // Respawn（770：0x4B。769（0x4C）と同形式のため769の変換を使い回す）。\n        if (packet.id() == 0x4B) {\n            if (is776()) {\n                // 776クライアント：Respawn は 0x52（同形式）のまま。\n                return Outcome.toClient(new MinecraftPacket(0x52, packet.payload()));\n            }\n            if (is775()) {\n                // 775クライアント：Respawn は 0x52（同形式）のまま。\n                return Outcome.toClient(new MinecraftPacket(0x52, packet.payload()));\n            }\n            if (is774()) {\n                // 774クライアント：Respawn は 0x50（同形式）のまま。\n                return Outcome.toClient(new MinecraftPacket(0x50, packet.payload()));\n            }\n'), 'handleServerPlayFrom768 Respawn 775': ('        // Respawn（769：0x4C。768と同形式のため768の変換を使い回す）。\n        if (packet.id() == 0x4C) {\n            if (is775()) {\n                // 775クライアント：Respawn は 0x52（同形式）のまま。\n                return Outcome.toClient(new MinecraftPacket(0x52, packet.payload()));\n            }\n            if (is774()) {\n                // 774クライアント：Respawn は 0x50（同形式）のまま。\n                return Outcome.toClient(new MinecraftPacket(0x50, packet.payload()));\n            }\n', '        // Respawn（769：0x4C。768と同形式のため768の変換を使い回す）。\n        if (packet.id() == 0x4C) {\n            if (is776()) {\n                // 776クライアント：Respawn は 0x52（同形式）のまま。\n                return Outcome.toClient(new MinecraftPacket(0x52, packet.payload()));\n            }\n            if (is775()) {\n                // 775クライアント：Respawn は 0x52（同形式）のまま。\n                return Outcome.toClient(new MinecraftPacket(0x52, packet.payload()));\n            }\n            if (is774()) {\n                // 774クライアント：Respawn は 0x50（同形式）のまま。\n                return Outcome.toClient(new MinecraftPacket(0x50, packet.payload()));\n            }\n'), 'handleServerPlayFrom770 Respawn 775': ('        // Respawn（771：0x4B。770（0x4B）と同形式のため770の変換を使い回す）。\n        if (packet.id() == 0x4B) {\n            if (is775()) {\n                // 775クライアント：Respawn は 0x52（同形式）のまま。\n                return Outcome.toClient(new MinecraftPacket(0x52, packet.payload()));\n            }\n            if (is774()) {\n                // 774クライアント：Respawn は 0x50（同形式）のまま。\n                return Outcome.toClient(new MinecraftPacket(0x50, packet.payload()));\n            }\n', '        // Respawn（771：0x4B。770（0x4B）と同形式のため770の変換を使い回す）。\n        if (packet.id() == 0x4B) {\n            if (is776()) {\n                // 776クライアント：Respawn は 0x52（同形式）のまま。\n                return Outcome.toClient(new MinecraftPacket(0x52, packet.payload()));\n            }\n            if (is775()) {\n                // 775クライアント：Respawn は 0x52（同形式）のまま。\n                return Outcome.toClient(new MinecraftPacket(0x52, packet.payload()));\n            }\n            if (is774()) {\n                // 774クライアント：Respawn は 0x50（同形式）のまま。\n                return Outcome.toClient(new MinecraftPacket(0x50, packet.payload()));\n            }\n'), 'handleServerPlayFrom771 Respawn 775': ('        // Respawn（772：0x4B。771（0x4B）と同形式のためそのまま送る）。\n        if (packet.id() == 0x4B) {\n            if (is771()) {\n                // 771クライアント：Respawn は 0x4B（同形式）のまま。\n                return Outcome.toClient(new MinecraftPacket(0x4B, packet.payload()));\n            }\n            if (is775()) {\n                // 775クライアント：Respawn は 0x52（同形式）のまま。\n                return Outcome.toClient(new MinecraftPacket(0x52, packet.payload()));\n            }\n            if (is774()) {\n                // 774クライアント：Respawn は 0x50（同形式）のまま。\n                return Outcome.toClient(new MinecraftPacket(0x50, packet.payload()));\n            }\n', '        // Respawn（772：0x4B。771（0x4B）と同形式のためそのまま送る）。\n        if (packet.id() == 0x4B) {\n            if (is776()) {\n                // 776クライアント：Respawn は 0x52（同形式）のまま。\n                return Outcome.toClient(new MinecraftPacket(0x52, packet.payload()));\n            }\n            if (is771()) {\n                // 771クライアント：Respawn は 0x4B（同形式）のまま。\n                return Outcome.toClient(new MinecraftPacket(0x4B, packet.payload()));\n            }\n            if (is775()) {\n                // 775クライアント：Respawn は 0x52（同形式）のまま。\n                return Outcome.toClient(new MinecraftPacket(0x52, packet.payload()));\n            }\n            if (is774()) {\n                // 774クライアント：Respawn は 0x50（同形式）のまま。\n                return Outcome.toClient(new MinecraftPacket(0x50, packet.payload()));\n            }\n'), 'handleServerPlayFrom772 Respawn 775': ('        // Respawn（774：0x50。772（0x4B）と同形式のためそのまま送る）。\n        if (packet.id() == 0x50) {\n            if (is775()) {\n                // 775クライアント：Respawn は 0x52（同形式）のまま。\n                return Outcome.toClient(new MinecraftPacket(0x52, packet.payload()));\n            }\n            if (is774()) {\n                // 774クライアント：Respawn は 0x50（同形式）のまま。\n                return Outcome.toClient(new MinecraftPacket(0x50, packet.payload()));\n            }\n            if (is773()) {\n                // 773クライアント：Respawn は 0x50（同形式）のまま。\n                return Outcome.toClient(new MinecraftPacket(0x50, packet.payload()));\n            }\n', '        // Respawn（774：0x50。772（0x4B）と同形式のためそのまま送る）。\n        if (packet.id() == 0x50) {\n            if (is776()) {\n                // 776クライアント：Respawn は 0x52（同形式）のまま。\n                return Outcome.toClient(new MinecraftPacket(0x52, packet.payload()));\n            }\n            if (is775()) {\n                // 775クライアント：Respawn は 0x52（同形式）のまま。\n                return Outcome.toClient(new MinecraftPacket(0x52, packet.payload()));\n            }\n            if (is774()) {\n                // 774クライアント：Respawn は 0x50（同形式）のまま。\n                return Outcome.toClient(new MinecraftPacket(0x50, packet.payload()));\n            }\n            if (is773()) {\n                // 773クライアント：Respawn は 0x50（同形式）のまま。\n                return Outcome.toClient(new MinecraftPacket(0x50, packet.payload()));\n            }\n'), 'handleServerPlayFrom775 Respawn 775': ('        // Respawn（775：0x52。774（0x50）と同形式のためそのまま送る）。\n        if (packet.id() == 0x52) {\n            if (is774()) {\n                // 774クライアント：Respawn は 0x50（同形式）のまま。\n                return Outcome.toClient(new MinecraftPacket(0x50, packet.payload()));\n            }\n            if (is773()) {\n                // 773クライアント：Respawn は 0x50（同形式）のまま。\n                return Outcome.toClient(new MinecraftPacket(0x50, packet.payload()));\n            }\n', '        // Respawn（775：0x52。774（0x50）と同形式のためそのまま送る）。\n        if (packet.id() == 0x52) {\n            if (is776()) {\n                // 776クライアント：Respawn は 0x52（同形式）のまま。\n                return Outcome.toClient(new MinecraftPacket(0x52, packet.payload()));\n            }\n            if (is774()) {\n                // 774クライアント：Respawn は 0x50（同形式）のまま。\n                return Outcome.toClient(new MinecraftPacket(0x50, packet.payload()));\n            }\n            if (is773()) {\n                // 773クライアント：Respawn は 0x50（同形式）のまま。\n                return Outcome.toClient(new MinecraftPacket(0x50, packet.payload()));\n            }\n'), 'handleServerJoinGame770 775 branch': ('    private Outcome handleServerJoinGame770(MinecraftPacket packet) {\n        if (is775()) {\n            advanceFlowSafely(() -> joinFlow.joinGameReceived());\n            List<MinecraftPacket> toClient = new ArrayList<>();\n            toClient.add(new MinecraftPacket(0x31, packet.payload()));\n            while (!pendingPacks.isEmpty()) {\n                PendingPack pack = pendingPacks.removeFirst();\n                lastSentPack = pack;\n                toClient.add(new MinecraftPacket(0x51,\n                        PlayTranslator.translateConfigAddResourcePack764To765(\n                                pack, packOverrideForClient())));\n            }\n            return new Outcome(List.of(), toClient, false, false);\n        }\n        if (is774()) {\n            advanceFlowSafely(() -> joinFlow.joinGameReceived());\n            List<MinecraftPacket> toClient = new ArrayList<>();\n            toClient.add(new MinecraftPacket(0x30, packet.payload()));\n            while (!pendingPacks.isEmpty()) {\n                PendingPack pack = pendingPacks.removeFirst();\n                lastSentPack = pack;\n                toClient.add(new MinecraftPacket(0x4F,\n                        PlayTranslator.translateConfigAddResourcePack764To765(\n                                pack, packOverrideForClient())));\n            }\n            return new Outcome(List.of(), toClient, false, false);\n        }\n        if (is773()) {\n            advanceFlowSafely(() -> joinFlow.joinGameReceived());\n            List<MinecraftPacket> toClient = new ArrayList<>();\n            toClient.add(new MinecraftPacket(0x30, packet.payload()));\n            while (!pendingPacks.isEmpty()) {\n                PendingPack pack = pendingPacks.removeFirst();\n                lastSentPack = pack;\n                toClient.add(new MinecraftPacket(0x4F,\n                        PlayTranslator.translateConfigAddResourcePack764To765(\n                                pack, packOverrideForClient())));\n            }\n            return new Outcome(List.of(), toClient, false, false);\n        }\n        if (is772()) {\n            advanceFlowSafely(() -> joinFlow.joinGameReceived());\n            List<MinecraftPacket> toClient = new ArrayList<>();\n            toClient.add(new MinecraftPacket(0x2B, packet.payload()));\n            while (!pendingPacks.isEmpty()) {\n                PendingPack pack = pendingPacks.removeFirst();\n                lastSentPack = pack;\n                toClient.add(new MinecraftPacket(0x4A,\n                        PlayTranslator.translateConfigAddResourcePack764To765(\n                                pack, packOverrideForClient())));\n            }\n            return new Outcome(List.of(), toClient, false, false);\n        }\n        if (is771()) {\n            advanceFlowSafely(() -> joinFlow.joinGameReceived());\n            List<MinecraftPacket> toClient = new ArrayList<>();\n            toClient.add(new MinecraftPacket(0x2B, packet.payload()));\n            while (!pendingPacks.isEmpty()) {\n                PendingPack pack = pendingPacks.removeFirst();\n                lastSentPack = pack;\n                toClient.add(new MinecraftPacket(0x4A,\n                        PlayTranslator.translateConfigAddResourcePack764To765(\n                                pack, packOverrideForClient())));\n            }\n            return new Outcome(List.of(), toClient, false, false);\n        }\n        if (is769()) {\n            advanceFlowSafely(() -> joinFlow.joinGameReceived());\n            List<MinecraftPacket> toClient = new ArrayList<>();\n            toClient.add(new MinecraftPacket(0x2C, packet.payload()));\n            while (!pendingPacks.isEmpty()) {\n                PendingPack pack = pendingPacks.removeFirst();\n                lastSentPack = pack;\n                toClient.add(new MinecraftPacket(0x4B,\n                        PlayTranslator.translateConfigAddResourcePack764To765(\n                                pack, packOverrideForClient())));\n            }\n            return new Outcome(List.of(), toClient, false, false);\n        }\n        return handleServerJoinGame769(packet);\n    }\n', '    private Outcome handleServerJoinGame770(MinecraftPacket packet) {\n        if (is776()) {\n            advanceFlowSafely(() -> joinFlow.joinGameReceived());\n            List<MinecraftPacket> toClient = new ArrayList<>();\n            toClient.add(new MinecraftPacket(0x31, packet.payload()));\n            while (!pendingPacks.isEmpty()) {\n                PendingPack pack = pendingPacks.removeFirst();\n                lastSentPack = pack;\n                toClient.add(new MinecraftPacket(0x51,\n                        PlayTranslator.translateConfigAddResourcePack764To765(\n                                pack, packOverrideForClient())));\n            }\n            return new Outcome(List.of(), toClient, false, false);\n        }\n        if (is775()) {\n            advanceFlowSafely(() -> joinFlow.joinGameReceived());\n            List<MinecraftPacket> toClient = new ArrayList<>();\n            toClient.add(new MinecraftPacket(0x31, packet.payload()));\n            while (!pendingPacks.isEmpty()) {\n                PendingPack pack = pendingPacks.removeFirst();\n                lastSentPack = pack;\n                toClient.add(new MinecraftPacket(0x51,\n                        PlayTranslator.translateConfigAddResourcePack764To765(\n                                pack, packOverrideForClient())));\n            }\n            return new Outcome(List.of(), toClient, false, false);\n        }\n        if (is774()) {\n            advanceFlowSafely(() -> joinFlow.joinGameReceived());\n            List<MinecraftPacket> toClient = new ArrayList<>();\n            toClient.add(new MinecraftPacket(0x30, packet.payload()));\n            while (!pendingPacks.isEmpty()) {\n                PendingPack pack = pendingPacks.removeFirst();\n                lastSentPack = pack;\n                toClient.add(new MinecraftPacket(0x4F,\n                        PlayTranslator.translateConfigAddResourcePack764To765(\n                                pack, packOverrideForClient())));\n            }\n            return new Outcome(List.of(), toClient, false, false);\n        }\n        if (is773()) {\n            advanceFlowSafely(() -> joinFlow.joinGameReceived());\n            List<MinecraftPacket> toClient = new ArrayList<>();\n            toClient.add(new MinecraftPacket(0x30, packet.payload()));\n            while (!pendingPacks.isEmpty()) {\n                PendingPack pack = pendingPacks.removeFirst();\n                lastSentPack = pack;\n                toClient.add(new MinecraftPacket(0x4F,\n                        PlayTranslator.translateConfigAddResourcePack764To765(\n                                pack, packOverrideForClient())));\n            }\n            return new Outcome(List.of(), toClient, false, false);\n        }\n        if (is772()) {\n            advanceFlowSafely(() -> joinFlow.joinGameReceived());\n            List<MinecraftPacket> toClient = new ArrayList<>();\n            toClient.add(new MinecraftPacket(0x2B, packet.payload()));\n            while (!pendingPacks.isEmpty()) {\n                PendingPack pack = pendingPacks.removeFirst();\n                lastSentPack = pack;\n                toClient.add(new MinecraftPacket(0x4A,\n                        PlayTranslator.translateConfigAddResourcePack764To765(\n                                pack, packOverrideForClient())));\n            }\n            return new Outcome(List.of(), toClient, false, false);\n        }\n        if (is771()) {\n            advanceFlowSafely(() -> joinFlow.joinGameReceived());\n            List<MinecraftPacket> toClient = new ArrayList<>();\n            toClient.add(new MinecraftPacket(0x2B, packet.payload()));\n            while (!pendingPacks.isEmpty()) {\n                PendingPack pack = pendingPacks.removeFirst();\n                lastSentPack = pack;\n                toClient.add(new MinecraftPacket(0x4A,\n                        PlayTranslator.translateConfigAddResourcePack764To765(\n                                pack, packOverrideForClient())));\n            }\n            return new Outcome(List.of(), toClient, false, false);\n        }\n        if (is769()) {\n            advanceFlowSafely(() -> joinFlow.joinGameReceived());\n            List<MinecraftPacket> toClient = new ArrayList<>();\n            toClient.add(new MinecraftPacket(0x2C, packet.payload()));\n            while (!pendingPacks.isEmpty()) {\n                PendingPack pack = pendingPacks.removeFirst();\n                lastSentPack = pack;\n                toClient.add(new MinecraftPacket(0x4B,\n                        PlayTranslator.translateConfigAddResourcePack764To765(\n                                pack, packOverrideForClient())));\n            }\n            return new Outcome(List.of(), toClient, false, false);\n        }\n        return handleServerJoinGame769(packet);\n    }\n'), 'handleServerJoinGame771 775 branch': ('    private Outcome handleServerJoinGame771(MinecraftPacket packet) {\n        if (is775()) {\n            advanceFlowSafely(() -> joinFlow.joinGameReceived());\n            List<MinecraftPacket> toClient = new ArrayList<>();\n            toClient.add(new MinecraftPacket(0x31, packet.payload()));\n            while (!pendingPacks.isEmpty()) {\n                PendingPack pack = pendingPacks.removeFirst();\n                lastSentPack = pack;\n                toClient.add(new MinecraftPacket(0x51,\n                        PlayTranslator.translateConfigAddResourcePack764To765(\n                                pack, packOverrideForClient())));\n            }\n            return new Outcome(List.of(), toClient, false, false);\n        }\n        if (is774()) {\n            advanceFlowSafely(() -> joinFlow.joinGameReceived());\n            List<MinecraftPacket> toClient = new ArrayList<>();\n            toClient.add(new MinecraftPacket(0x30, packet.payload()));\n            while (!pendingPacks.isEmpty()) {\n                PendingPack pack = pendingPacks.removeFirst();\n                lastSentPack = pack;\n                toClient.add(new MinecraftPacket(0x4F,\n                        PlayTranslator.translateConfigAddResourcePack764To765(\n                                pack, packOverrideForClient())));\n            }\n            return new Outcome(List.of(), toClient, false, false);\n        }\n        if (is773()) {\n            advanceFlowSafely(() -> joinFlow.joinGameReceived());\n            List<MinecraftPacket> toClient = new ArrayList<>();\n            toClient.add(new MinecraftPacket(0x30, packet.payload()));\n            while (!pendingPacks.isEmpty()) {\n                PendingPack pack = pendingPacks.removeFirst();\n                lastSentPack = pack;\n                toClient.add(new MinecraftPacket(0x4F,\n                        PlayTranslator.translateConfigAddResourcePack764To765(\n                                pack, packOverrideForClient())));\n            }\n            return new Outcome(List.of(), toClient, false, false);\n        }\n        if (is772()) {\n            advanceFlowSafely(() -> joinFlow.joinGameReceived());\n            List<MinecraftPacket> toClient = new ArrayList<>();\n            toClient.add(new MinecraftPacket(0x2B, packet.payload()));\n            while (!pendingPacks.isEmpty()) {\n                PendingPack pack = pendingPacks.removeFirst();\n                lastSentPack = pack;\n                toClient.add(new MinecraftPacket(0x4A,\n                        PlayTranslator.translateConfigAddResourcePack764To765(\n                                pack, packOverrideForClient())));\n            }\n            return new Outcome(List.of(), toClient, false, false);\n        }\n        if (is770()) {\n            advanceFlowSafely(() -> joinFlow.joinGameReceived());\n            List<MinecraftPacket> toClient = new ArrayList<>();\n            toClient.add(new MinecraftPacket(0x2B, packet.payload()));\n            while (!pendingPacks.isEmpty()) {\n                PendingPack pack = pendingPacks.removeFirst();\n                lastSentPack = pack;\n                toClient.add(new MinecraftPacket(0x4A,\n                        PlayTranslator.translateConfigAddResourcePack764To765(\n                                pack, packOverrideForClient())));\n            }\n            return new Outcome(List.of(), toClient, false, false);\n        }\n        return handleServerJoinGame770(packet);\n    }\n', '    private Outcome handleServerJoinGame771(MinecraftPacket packet) {\n        if (is776()) {\n            advanceFlowSafely(() -> joinFlow.joinGameReceived());\n            List<MinecraftPacket> toClient = new ArrayList<>();\n            toClient.add(new MinecraftPacket(0x31, packet.payload()));\n            while (!pendingPacks.isEmpty()) {\n                PendingPack pack = pendingPacks.removeFirst();\n                lastSentPack = pack;\n                toClient.add(new MinecraftPacket(0x51,\n                        PlayTranslator.translateConfigAddResourcePack764To765(\n                                pack, packOverrideForClient())));\n            }\n            return new Outcome(List.of(), toClient, false, false);\n        }\n        if (is775()) {\n            advanceFlowSafely(() -> joinFlow.joinGameReceived());\n            List<MinecraftPacket> toClient = new ArrayList<>();\n            toClient.add(new MinecraftPacket(0x31, packet.payload()));\n            while (!pendingPacks.isEmpty()) {\n                PendingPack pack = pendingPacks.removeFirst();\n                lastSentPack = pack;\n                toClient.add(new MinecraftPacket(0x51,\n                        PlayTranslator.translateConfigAddResourcePack764To765(\n                                pack, packOverrideForClient())));\n            }\n            return new Outcome(List.of(), toClient, false, false);\n        }\n        if (is774()) {\n            advanceFlowSafely(() -> joinFlow.joinGameReceived());\n            List<MinecraftPacket> toClient = new ArrayList<>();\n            toClient.add(new MinecraftPacket(0x30, packet.payload()));\n            while (!pendingPacks.isEmpty()) {\n                PendingPack pack = pendingPacks.removeFirst();\n                lastSentPack = pack;\n                toClient.add(new MinecraftPacket(0x4F,\n                        PlayTranslator.translateConfigAddResourcePack764To765(\n                                pack, packOverrideForClient())));\n            }\n            return new Outcome(List.of(), toClient, false, false);\n        }\n        if (is773()) {\n            advanceFlowSafely(() -> joinFlow.joinGameReceived());\n            List<MinecraftPacket> toClient = new ArrayList<>();\n            toClient.add(new MinecraftPacket(0x30, packet.payload()));\n            while (!pendingPacks.isEmpty()) {\n                PendingPack pack = pendingPacks.removeFirst();\n                lastSentPack = pack;\n                toClient.add(new MinecraftPacket(0x4F,\n                        PlayTranslator.translateConfigAddResourcePack764To765(\n                                pack, packOverrideForClient())));\n            }\n            return new Outcome(List.of(), toClient, false, false);\n        }\n        if (is772()) {\n            advanceFlowSafely(() -> joinFlow.joinGameReceived());\n            List<MinecraftPacket> toClient = new ArrayList<>();\n            toClient.add(new MinecraftPacket(0x2B, packet.payload()));\n            while (!pendingPacks.isEmpty()) {\n                PendingPack pack = pendingPacks.removeFirst();\n                lastSentPack = pack;\n                toClient.add(new MinecraftPacket(0x4A,\n                        PlayTranslator.translateConfigAddResourcePack764To765(\n                                pack, packOverrideForClient())));\n            }\n            return new Outcome(List.of(), toClient, false, false);\n        }\n        if (is770()) {\n            advanceFlowSafely(() -> joinFlow.joinGameReceived());\n            List<MinecraftPacket> toClient = new ArrayList<>();\n            toClient.add(new MinecraftPacket(0x2B, packet.payload()));\n            while (!pendingPacks.isEmpty()) {\n                PendingPack pack = pendingPacks.removeFirst();\n                lastSentPack = pack;\n                toClient.add(new MinecraftPacket(0x4A,\n                        PlayTranslator.translateConfigAddResourcePack764To765(\n                                pack, packOverrideForClient())));\n            }\n            return new Outcome(List.of(), toClient, false, false);\n        }\n        return handleServerJoinGame770(packet);\n    }\n'), 'handleServerJoinGame772 775 branch': ('    private Outcome handleServerJoinGame772(MinecraftPacket packet) {\n        if (is775()) {\n            advanceFlowSafely(() -> joinFlow.joinGameReceived());\n            List<MinecraftPacket> toClient = new ArrayList<>();\n            toClient.add(new MinecraftPacket(0x31, packet.payload()));\n            while (!pendingPacks.isEmpty()) {\n                PendingPack pack = pendingPacks.removeFirst();\n                lastSentPack = pack;\n                toClient.add(new MinecraftPacket(0x51,\n                        PlayTranslator.translateConfigAddResourcePack764To765(\n                                pack, packOverrideForClient())));\n            }\n            return new Outcome(List.of(), toClient, false, false);\n        }\n        if (is774()) {\n            advanceFlowSafely(() -> joinFlow.joinGameReceived());\n            List<MinecraftPacket> toClient = new ArrayList<>();\n            toClient.add(new MinecraftPacket(0x30, packet.payload()));\n            while (!pendingPacks.isEmpty()) {\n                PendingPack pack = pendingPacks.removeFirst();\n                lastSentPack = pack;\n                toClient.add(new MinecraftPacket(0x4F,\n                        PlayTranslator.translateConfigAddResourcePack764To765(\n                                pack, packOverrideForClient())));\n            }\n            return new Outcome(List.of(), toClient, false, false);\n        }\n        if (is773()) {\n            advanceFlowSafely(() -> joinFlow.joinGameReceived());\n            List<MinecraftPacket> toClient = new ArrayList<>();\n            toClient.add(new MinecraftPacket(0x30, packet.payload()));\n            while (!pendingPacks.isEmpty()) {\n                PendingPack pack = pendingPacks.removeFirst();\n                lastSentPack = pack;\n                toClient.add(new MinecraftPacket(0x4F,\n                        PlayTranslator.translateConfigAddResourcePack764To765(\n                                pack, packOverrideForClient())));\n            }\n            return new Outcome(List.of(), toClient, false, false);\n        }\n        if (is771()) {\n            advanceFlowSafely(() -> joinFlow.joinGameReceived());\n            List<MinecraftPacket> toClient = new ArrayList<>();\n            toClient.add(new MinecraftPacket(0x2B, packet.payload()));\n            while (!pendingPacks.isEmpty()) {\n                PendingPack pack = pendingPacks.removeFirst();\n                lastSentPack = pack;\n                toClient.add(new MinecraftPacket(0x4A,\n                        PlayTranslator.translateConfigAddResourcePack764To765(\n                                pack, packOverrideForClient())));\n            }\n            return new Outcome(List.of(), toClient, false, false);\n        }\n        return handleServerJoinGame771(packet);\n    }\n', '    private Outcome handleServerJoinGame772(MinecraftPacket packet) {\n        if (is776()) {\n            advanceFlowSafely(() -> joinFlow.joinGameReceived());\n            List<MinecraftPacket> toClient = new ArrayList<>();\n            toClient.add(new MinecraftPacket(0x31, packet.payload()));\n            while (!pendingPacks.isEmpty()) {\n                PendingPack pack = pendingPacks.removeFirst();\n                lastSentPack = pack;\n                toClient.add(new MinecraftPacket(0x51,\n                        PlayTranslator.translateConfigAddResourcePack764To765(\n                                pack, packOverrideForClient())));\n            }\n            return new Outcome(List.of(), toClient, false, false);\n        }\n        if (is775()) {\n            advanceFlowSafely(() -> joinFlow.joinGameReceived());\n            List<MinecraftPacket> toClient = new ArrayList<>();\n            toClient.add(new MinecraftPacket(0x31, packet.payload()));\n            while (!pendingPacks.isEmpty()) {\n                PendingPack pack = pendingPacks.removeFirst();\n                lastSentPack = pack;\n                toClient.add(new MinecraftPacket(0x51,\n                        PlayTranslator.translateConfigAddResourcePack764To765(\n                                pack, packOverrideForClient())));\n            }\n            return new Outcome(List.of(), toClient, false, false);\n        }\n        if (is774()) {\n            advanceFlowSafely(() -> joinFlow.joinGameReceived());\n            List<MinecraftPacket> toClient = new ArrayList<>();\n            toClient.add(new MinecraftPacket(0x30, packet.payload()));\n            while (!pendingPacks.isEmpty()) {\n                PendingPack pack = pendingPacks.removeFirst();\n                lastSentPack = pack;\n                toClient.add(new MinecraftPacket(0x4F,\n                        PlayTranslator.translateConfigAddResourcePack764To765(\n                                pack, packOverrideForClient())));\n            }\n            return new Outcome(List.of(), toClient, false, false);\n        }\n        if (is773()) {\n            advanceFlowSafely(() -> joinFlow.joinGameReceived());\n            List<MinecraftPacket> toClient = new ArrayList<>();\n            toClient.add(new MinecraftPacket(0x30, packet.payload()));\n            while (!pendingPacks.isEmpty()) {\n                PendingPack pack = pendingPacks.removeFirst();\n                lastSentPack = pack;\n                toClient.add(new MinecraftPacket(0x4F,\n                        PlayTranslator.translateConfigAddResourcePack764To765(\n                                pack, packOverrideForClient())));\n            }\n            return new Outcome(List.of(), toClient, false, false);\n        }\n        if (is771()) {\n            advanceFlowSafely(() -> joinFlow.joinGameReceived());\n            List<MinecraftPacket> toClient = new ArrayList<>();\n            toClient.add(new MinecraftPacket(0x2B, packet.payload()));\n            while (!pendingPacks.isEmpty()) {\n                PendingPack pack = pendingPacks.removeFirst();\n                lastSentPack = pack;\n                toClient.add(new MinecraftPacket(0x4A,\n                        PlayTranslator.translateConfigAddResourcePack764To765(\n                                pack, packOverrideForClient())));\n            }\n            return new Outcome(List.of(), toClient, false, false);\n        }\n        return handleServerJoinGame771(packet);\n    }\n'), 'synthesized config registry loop': ('        if (is766() || is767() || is768() || is769() || is770() || is771() || is772() || is773() || is774() || is775()) {\n            // 766／767／768／769／770／771／772／773／774／775：レジストリごとに1パケット（エントリ配列）。\n            for (byte[] registryPayload : PlayTranslator.buildRegistryData766(registries)) {\n                if (is773()) {\n                    // 773：766→767→768→769→770→771→772→773へ七段で並べ替える。\n                    byte[] payload766to767 =\n                            PlayTranslator.translateRegistryData766To767(registryPayload);\n                    byte[] payload767to768 =\n                            PlayTranslator.translateRegistryData767To768(payload766to767);\n                    byte[] payload768to769 =\n                            PlayTranslator.translateRegistryData768To769(payload767to768);\n                    byte[] payload769to770 =\n                            PlayTranslator.translateRegistryData769To770(payload768to769);\n                    byte[] payload770to771 =\n                            PlayTranslator.translateRegistryData770To771(payload769to770);\n                    byte[] payload771to772 =\n                            PlayTranslator.translateRegistryData771To772(payload770to771);\n                    registryPayload =\n                            PlayTranslator.translateRegistryData772To773(payload771to772);\n                }\n                if (is774()) {\n                    // 774：766→767→768→769→770→771→772→773→774へ八段で並べ替える。\n                    byte[] payload766to767 =\n                            PlayTranslator.translateRegistryData766To767(registryPayload);\n                    byte[] payload767to768 =\n                            PlayTranslator.translateRegistryData767To768(payload766to767);\n                    byte[] payload768to769 =\n                            PlayTranslator.translateRegistryData768To769(payload767to768);\n                    byte[] payload769to770 =\n                            PlayTranslator.translateRegistryData769To770(payload768to769);\n                    byte[] payload770to771 =\n                            PlayTranslator.translateRegistryData770To771(payload769to770);\n                    byte[] payload771to772 =\n                            PlayTranslator.translateRegistryData771To772(payload770to771);\n                    byte[] payload772to773 =\n                            PlayTranslator.translateRegistryData772To773(payload771to772);\n                    registryPayload =\n                            PlayTranslator.translateRegistryData773To774(payload772to773);\n                }\n                if (is775()) {\n                    // 775：766→767→768→769→770→771→772→773→774→775へ九段で並べ替える。\n                    byte[] payload766to767 =\n                            PlayTranslator.translateRegistryData766To767(registryPayload);\n                    byte[] payload767to768 =\n                            PlayTranslator.translateRegistryData767To768(payload766to767);\n                    byte[] payload768to769 =\n                            PlayTranslator.translateRegistryData768To769(payload767to768);\n                    byte[] payload769to770 =\n                            PlayTranslator.translateRegistryData769To770(payload768to769);\n                    byte[] payload770to771 =\n                            PlayTranslator.translateRegistryData770To771(payload769to770);\n                    byte[] payload771to772 =\n                            PlayTranslator.translateRegistryData771To772(payload770to771);\n                    byte[] payload772to773 =\n                            PlayTranslator.translateRegistryData772To773(payload771to772);\n                    byte[] payload773to774 =\n                            PlayTranslator.translateRegistryData773To774(payload772to773);\n                    registryPayload =\n                            PlayTranslator.translateRegistryData774To775(payload773to774);\n                }\n', '        if (is766() || is767() || is768() || is769() || is770() || is771() || is772() || is773() || is774() || is775() || is776()) {\n            // 766／767／768／769／770／771／772／773／774／775／776：レジストリごとに1パケット（エントリ配列）。\n            for (byte[] registryPayload : PlayTranslator.buildRegistryData766(registries)) {\n                if (is773()) {\n                    // 773：766→767→768→769→770→771→772→773へ七段で並べ替える。\n                    byte[] payload766to767 =\n                            PlayTranslator.translateRegistryData766To767(registryPayload);\n                    byte[] payload767to768 =\n                            PlayTranslator.translateRegistryData767To768(payload766to767);\n                    byte[] payload768to769 =\n                            PlayTranslator.translateRegistryData768To769(payload767to768);\n                    byte[] payload769to770 =\n                            PlayTranslator.translateRegistryData769To770(payload768to769);\n                    byte[] payload770to771 =\n                            PlayTranslator.translateRegistryData770To771(payload769to770);\n                    byte[] payload771to772 =\n                            PlayTranslator.translateRegistryData771To772(payload770to771);\n                    registryPayload =\n                            PlayTranslator.translateRegistryData772To773(payload771to772);\n                }\n                if (is774()) {\n                    // 774：766→767→768→769→770→771→772→773→774へ八段で並べ替える。\n                    byte[] payload766to767 =\n                            PlayTranslator.translateRegistryData766To767(registryPayload);\n                    byte[] payload767to768 =\n                            PlayTranslator.translateRegistryData767To768(payload766to767);\n                    byte[] payload768to769 =\n                            PlayTranslator.translateRegistryData768To769(payload767to768);\n                    byte[] payload769to770 =\n                            PlayTranslator.translateRegistryData769To770(payload768to769);\n                    byte[] payload770to771 =\n                            PlayTranslator.translateRegistryData770To771(payload769to770);\n                    byte[] payload771to772 =\n                            PlayTranslator.translateRegistryData771To772(payload770to771);\n                    byte[] payload772to773 =\n                            PlayTranslator.translateRegistryData772To773(payload771to772);\n                    registryPayload =\n                            PlayTranslator.translateRegistryData773To774(payload772to773);\n                }\n                if (is775()) {\n                    // 775：766→767→768→769→770→771→772→773→774→775へ九段で並べ替える。\n                    byte[] payload766to767 =\n                            PlayTranslator.translateRegistryData766To767(registryPayload);\n                    byte[] payload767to768 =\n                            PlayTranslator.translateRegistryData767To768(payload766to767);\n                    byte[] payload768to769 =\n                            PlayTranslator.translateRegistryData768To769(payload767to768);\n                    byte[] payload769to770 =\n                            PlayTranslator.translateRegistryData769To770(payload768to769);\n                    byte[] payload770to771 =\n                            PlayTranslator.translateRegistryData770To771(payload769to770);\n                    byte[] payload771to772 =\n                            PlayTranslator.translateRegistryData771To772(payload770to771);\n                    byte[] payload772to773 =\n                            PlayTranslator.translateRegistryData772To773(payload771to772);\n                    byte[] payload773to774 =\n                            PlayTranslator.translateRegistryData773To774(payload772to773);\n                    registryPayload =\n                            PlayTranslator.translateRegistryData774To775(payload773to774);\n                }\n                if (is776()) {\n                    // 776：766→767→768→769→770→771→772→773→774→775→776へ十段で並べ替える。\n                    byte[] payload766to767 =\n                            PlayTranslator.translateRegistryData766To767(registryPayload);\n                    byte[] payload767to768 =\n                            PlayTranslator.translateRegistryData767To768(payload766to767);\n                    byte[] payload768to769 =\n                            PlayTranslator.translateRegistryData768To769(payload767to768);\n                    byte[] payload769to770 =\n                            PlayTranslator.translateRegistryData769To770(payload768to769);\n                    byte[] payload770to771 =\n                            PlayTranslator.translateRegistryData770To771(payload769to770);\n                    byte[] payload771to772 =\n                            PlayTranslator.translateRegistryData771To772(payload770to771);\n                    byte[] payload772to773 =\n                            PlayTranslator.translateRegistryData772To773(payload771to772);\n                    byte[] payload773to774 =\n                            PlayTranslator.translateRegistryData773To774(payload772to773);\n                    byte[] payload774to775 =\n                            PlayTranslator.translateRegistryData774To775(payload773to774);\n                    registryPayload =\n                            PlayTranslator.translateRegistryData775To776(payload774to775);\n                }\n'), 'handleServerConfiguration 763 branch': ('        if (is763Like()) {\n            if (isServer775()) {\n                return handleServerConfiguration763From775(packet);\n            }\n            if (isServer774()) {\n                return handleServerConfiguration763From774(packet);\n            }\n            if (isServer773()) {\n                return handleServerConfiguration763From773(packet);\n            }\n            if (isServer772()) {\n                return handleServerConfiguration763From772(packet);\n            }\n            if (isServer771()) {\n                return handleServerConfiguration763From771(packet);\n            }\n            if (isServer770()) {\n                return handleServerConfiguration763From770(packet);\n            }\n            if (isServer769()) {\n                return handleServerConfiguration763From769(packet);\n            }\n            if (isServer768()) {\n                return handleServerConfiguration763From768(packet);\n            }\n            if (isServer767()) {\n                return handleServerConfiguration763From767(packet);\n            }\n            if (isServer766()) {\n                return handleServerConfiguration763From766(packet);\n            }\n', '        if (is763Like()) {\n            if (isServer776()) {\n                return handleServerConfiguration763From776(packet);\n            }\n            if (isServer775()) {\n                return handleServerConfiguration763From775(packet);\n            }\n            if (isServer774()) {\n                return handleServerConfiguration763From774(packet);\n            }\n            if (isServer773()) {\n                return handleServerConfiguration763From773(packet);\n            }\n            if (isServer772()) {\n                return handleServerConfiguration763From772(packet);\n            }\n            if (isServer771()) {\n                return handleServerConfiguration763From771(packet);\n            }\n            if (isServer770()) {\n                return handleServerConfiguration763From770(packet);\n            }\n            if (isServer769()) {\n                return handleServerConfiguration763From769(packet);\n            }\n            if (isServer768()) {\n                return handleServerConfiguration763From768(packet);\n            }\n            if (isServer767()) {\n                return handleServerConfiguration763From767(packet);\n            }\n            if (isServer766()) {\n                return handleServerConfiguration763From766(packet);\n            }\n'), 'handleServerConfiguration 775 branch': ('        // 775クライアントは設定フェーズを中継する（番号と Registry Data\n        // の形式だけ変換する）。\n        if (is775()) {\n            if (isServer775()) {\n                // 内部775＋775クライアントはネイティブのためここには来ない。\n                // 念のためそのまま通す。\n                return Outcome.toClient(packet);\n            }\n            if (isServer774()) {\n                return handleServerConfiguration775From774(packet);\n            }\n            if (isServer773()) {\n                return handleServerConfiguration775From773(packet);\n            }\n            if (isServer772()) {\n                return handleServerConfiguration775From772(packet);\n            }\n            if (isServer771()) {\n                return handleServerConfiguration775From771(packet);\n            }\n            if (isServer770()) {\n                return handleServerConfiguration775From770(packet);\n            }\n            if (isServer769()) {\n                return handleServerConfiguration775From769(packet);\n            }\n            if (isServer768()) {\n                return handleServerConfiguration775From768(packet);\n            }\n            if (isServer767()) {\n                return handleServerConfiguration775From767(packet);\n            }\n            if (isServer766()) {\n                return handleServerConfiguration775From766(packet);\n            }\n            return handleServerConfiguration775(packet, isServer764());\n        }\n        // 774クライアントは設定フェーズを中継する（番号と Registry Data\n        // の形式だけ変換する）。\n        if (is774()) {\n            if (isServer774()) {\n                // 内部774＋774クライアントはネイティブのためここには来ない。\n                // 念のためそのまま通す。\n                return Outcome.toClient(packet);\n            }\n            if (isServer775()) {\n                return handleServerConfiguration774From775(packet);\n            }\n            if (isServer773()) {\n                return handleServerConfiguration774From773(packet);\n            }\n            if (isServer772()) {\n                return handleServerConfiguration774From772(packet);\n            }\n            if (isServer771()) {\n                return handleServerConfiguration774From771(packet);\n            }\n            if (isServer770()) {\n                return handleServerConfiguration774From770(packet);\n            }\n            if (isServer769()) {\n                return handleServerConfiguration774From769(packet);\n            }\n            if (isServer768()) {\n                return handleServerConfiguration774From768(packet);\n            }\n            if (isServer767()) {\n                return handleServerConfiguration774From767(packet);\n            }\n            if (isServer766()) {\n                return handleServerConfiguration774From766(packet);\n            }\n            return handleServerConfiguration774(packet, isServer764());\n        }\n        // 773クライアントは設定フェーズを中継する（番号と Registry Data\n        // の形式だけ変換する）。\n        if (is773()) {\n            if (isServer773()) {\n                // 内部773＋773クライアントはネイティブのためここには来ない。\n                // 念のためそのまま通す。\n                return Outcome.toClient(packet);\n            }\n            if (isServer774()) {\n                return handleServerConfiguration773From774(packet);\n            }\n            if (isServer772()) {\n                return handleServerConfiguration773From772(packet);\n            }\n            if (isServer771()) {\n                return handleServerConfiguration773From771(packet);\n            }\n            if (isServer770()) {\n                return handleServerConfiguration773From770(packet);\n            }\n            if (isServer769()) {\n                return handleServerConfiguration773From769(packet);\n            }\n            if (isServer768()) {\n                return handleServerConfiguration773From768(packet);\n            }\n            if (isServer767()) {\n                return handleServerConfiguration773From767(packet);\n            }\n            if (isServer766()) {\n                return handleServerConfiguration773From766(packet);\n            }\n            return handleServerConfiguration773(packet, isServer764());\n        }\n', '        // 776クライアントは設定フェーズを中継する（番号と Registry Data\n        // の形式だけ変換する）。\n        if (is776()) {\n            if (isServer776()) {\n                // 内部776＋776クライアントはネイティブのためここには来ない。\n                // 念のためそのまま通す。\n                return Outcome.toClient(packet);\n            }\n            if (isServer775()) {\n                return handleServerConfiguration776From775(packet);\n            }\n            if (isServer774()) {\n                return handleServerConfiguration776From774(packet);\n            }\n            if (isServer773()) {\n                return handleServerConfiguration776From773(packet);\n            }\n            if (isServer772()) {\n                return handleServerConfiguration776From772(packet);\n            }\n            if (isServer771()) {\n                return handleServerConfiguration776From771(packet);\n            }\n            if (isServer770()) {\n                return handleServerConfiguration776From770(packet);\n            }\n            if (isServer769()) {\n                return handleServerConfiguration776From769(packet);\n            }\n            if (isServer768()) {\n                return handleServerConfiguration776From768(packet);\n            }\n            if (isServer767()) {\n                return handleServerConfiguration776From767(packet);\n            }\n            if (isServer766()) {\n                return handleServerConfiguration776From766(packet);\n            }\n            return handleServerConfiguration776(packet, isServer764());\n        }\n        // 775クライアントは設定フェーズを中継する（番号と Registry Data\n        // の形式だけ変換する）。\n        if (is775()) {\n            if (isServer775()) {\n                // 内部775＋775クライアントはネイティブのためここには来ない。\n                // 念のためそのまま通す。\n                return Outcome.toClient(packet);\n            }\n            if (isServer776()) {\n                return handleServerConfiguration775From776(packet);\n            }\n            if (isServer774()) {\n                return handleServerConfiguration775From774(packet);\n            }\n            if (isServer773()) {\n                return handleServerConfiguration775From773(packet);\n            }\n            if (isServer772()) {\n                return handleServerConfiguration775From772(packet);\n            }\n            if (isServer771()) {\n                return handleServerConfiguration775From771(packet);\n            }\n            if (isServer770()) {\n                return handleServerConfiguration775From770(packet);\n            }\n            if (isServer769()) {\n                return handleServerConfiguration775From769(packet);\n            }\n            if (isServer768()) {\n                return handleServerConfiguration775From768(packet);\n            }\n            if (isServer767()) {\n                return handleServerConfiguration775From767(packet);\n            }\n            if (isServer766()) {\n                return handleServerConfiguration775From766(packet);\n            }\n            return handleServerConfiguration775(packet, isServer764());\n        }\n        // 774クライアントは設定フェーズを中継する（番号と Registry Data\n        // の形式だけ変換する）。\n        if (is774()) {\n            if (isServer774()) {\n                // 内部774＋774クライアントはネイティブのためここには来ない。\n                // 念のためそのまま通す。\n                return Outcome.toClient(packet);\n            }\n            if (isServer776()) {\n                return handleServerConfiguration774From776(packet);\n            }\n            if (isServer775()) {\n                return handleServerConfiguration774From775(packet);\n            }\n            if (isServer773()) {\n                return handleServerConfiguration774From773(packet);\n            }\n            if (isServer772()) {\n                return handleServerConfiguration774From772(packet);\n            }\n            if (isServer771()) {\n                return handleServerConfiguration774From771(packet);\n            }\n            if (isServer770()) {\n                return handleServerConfiguration774From770(packet);\n            }\n            if (isServer769()) {\n                return handleServerConfiguration774From769(packet);\n            }\n            if (isServer768()) {\n                return handleServerConfiguration774From768(packet);\n            }\n            if (isServer767()) {\n                return handleServerConfiguration774From767(packet);\n            }\n            if (isServer766()) {\n                return handleServerConfiguration774From766(packet);\n            }\n            return handleServerConfiguration774(packet, isServer764());\n        }\n        // 773クライアントは設定フェーズを中継する（番号と Registry Data\n        // の形式だけ変換する）。\n        if (is773()) {\n            if (isServer773()) {\n                // 内部773＋773クライアントはネイティブのためここには来ない。\n                // 念のためそのまま通す。\n                return Outcome.toClient(packet);\n            }\n            if (isServer774()) {\n                return handleServerConfiguration773From774(packet);\n            }\n            if (isServer772()) {\n                return handleServerConfiguration773From772(packet);\n            }\n            if (isServer771()) {\n                return handleServerConfiguration773From771(packet);\n            }\n            if (isServer770()) {\n                return handleServerConfiguration773From770(packet);\n            }\n            if (isServer769()) {\n                return handleServerConfiguration773From769(packet);\n            }\n            if (isServer768()) {\n                return handleServerConfiguration773From768(packet);\n            }\n            if (isServer767()) {\n                return handleServerConfiguration773From767(packet);\n            }\n            if (isServer766()) {\n                return handleServerConfiguration773From766(packet);\n            }\n            return handleServerConfiguration773(packet, isServer764());\n        }\n'), 'handleServerConfiguration 775 branch isServer775': ('            if (isServer771()) {\n                return handleServerConfiguration772From771(packet);\n            }\n            if (isServer770()) {\n                return handleServerConfiguration772From770(packet);\n            }\n            if (isServer775()) {\n                return handleServerConfiguration772From775(packet);\n            }\n            if (isServer774()) {\n                return handleServerConfiguration772From774(packet);\n            }\n            if (isServer773()) {\n                return handleServerConfiguration772From773(packet);\n            }\n            if (isServer769()) {\n                return handleServerConfiguration772From769(packet);\n            }', '            if (isServer771()) {\n                return handleServerConfiguration772From771(packet);\n            }\n            if (isServer770()) {\n                return handleServerConfiguration772From770(packet);\n            }\n            if (isServer776()) {\n                return handleServerConfiguration772From776(packet);\n            }\n            if (isServer775()) {\n                return handleServerConfiguration772From775(packet);\n            }\n            if (isServer774()) {\n                return handleServerConfiguration772From774(packet);\n            }\n            if (isServer773()) {\n                return handleServerConfiguration772From773(packet);\n            }\n            if (isServer769()) {\n                return handleServerConfiguration772From769(packet);\n            }'), 'handleClientConfiguration 775-server guard': ('        // 771サーバーへは Custom Report Details（0x08）／Server Links\n        // （0x09）を送らない（771で削除された）。771クライアントの\n        // 0x08 は Custom Click Action のためこの限りではない。772クライアントの\n        // 0x08 も Custom Click Action のためこの限りではない。774クライアントの\n        // 0x08 も Custom Click Action のためこの限りではない。775クライアントの\n        // 0x08 も Custom Click Action のためこの限りではない。\n        if (isServer771() && !is771() && !is772() && !is773() && !is774() && !is775()\n                && (packet.id() == 0x08 || packet.id() == 0x09)) {\n            return Outcome.drop();\n        }\n        // 772サーバーへは Custom Report Details（0x08）／Server Links\n        // （0x09）を送らない（771で削除された。772も同じ）。772クライアントの\n        // 0x08 は Custom Click Action のためこの限りではない。771クライアントの\n        // 0x08 も Custom Click Action のためこの限りではない。774クライアントの\n        // 0x08 も Custom Click Action のためこの限りではない。775クライアントの\n        // 0x08 も Custom Click Action のためこの限りではない。\n        if (isServer772() && !is772() && !is771() && !is773() && !is774() && !is775()\n                && (packet.id() == 0x08 || packet.id() == 0x09)) {\n            return Outcome.drop();\n        }\n        // 774サーバーへは Custom Report Details（0x08）／Server Links\n        // （0x09）を送らない（771で削除された。774も同じ）。774クライアントの\n        // 0x08 は Custom Click Action のためこの限りではない。772クライアントの\n        // 0x08 も Custom Click Action のためこの限りではない。771クライアントの\n        // 0x08 も Custom Click Action のためこの限りではない。775クライアントの\n        // 0x08 も Custom Click Action のためこの限りではない。\n        if (isServer774() && !is774() && !is773() && !is772() && !is771() && !is775()\n                && (packet.id() == 0x08 || packet.id() == 0x09)) {\n            return Outcome.drop();\n        }\n        // 775サーバーへは Custom Report Details（0x08）／Server Links\n        // （0x09）を送らない（771で削除された。775も同じ）。775クライアントの\n        // 0x08 は Custom Click Action のためこの限りではない。774クライアントの\n        // 0x08 も Custom Click Action のためこの限りではない。772クライアントの\n        // 0x08 も Custom Click Action のためこの限りではない。771クライアントの\n        // 0x08 も Custom Click Action のためこの限りではない。\n        if (isServer775() && !is775() && !is774() && !is773() && !is772() && !is771()\n                && (packet.id() == 0x08 || packet.id() == 0x09)) {\n            return Outcome.drop();\n        }\n', '        // 771サーバーへは Custom Report Details（0x08）／Server Links\n        // （0x09）を送らない（771で削除された。771も同じ）。771クライアントの\n        // 0x08 は Custom Click Action のためこの限りではない。772クライアントの\n        // 0x08 も Custom Click Action のためこの限りではない。773クライアントの\n        // 0x08 も Custom Click Action のためこの限りではない。774クライアントの\n        // 0x08 も Custom Click Action のためこの限りではない。775クライアントの\n        // 0x08 も Custom Click Action のためこの限りではない。776クライアントの\n        // 0x08 も Custom Click Action のためこの限りではない。\n        if (isServer771() && !is771() && !is772() && !is773() && !is774() && !is775() && !is776()\n                && (packet.id() == 0x08 || packet.id() == 0x09)) {\n            return Outcome.drop();\n        }\n        // 772サーバーへは Custom Report Details（0x08）／Server Links\n        // （0x09）を送らない（771で削除された。772も同じ）。772クライアントの\n        // 0x08 は Custom Click Action のためこの限りではない。771クライアントの\n        // 0x08 も Custom Click Action のためこの限りではない。773クライアントの\n        // 0x08 も Custom Click Action のためこの限りではない。774クライアントの\n        // 0x08 も Custom Click Action のためこの限りではない。775クライアントの\n        // 0x08 も Custom Click Action のためこの限りではない。776クライアントの\n        // 0x08 も Custom Click Action のためこの限りではない。\n        if (isServer772() && !is772() && !is771() && !is773() && !is774() && !is775() && !is776()\n                && (packet.id() == 0x08 || packet.id() == 0x09)) {\n            return Outcome.drop();\n        }\n        // 774サーバーへは Custom Report Details（0x08）／Server Links\n        // （0x09）を送らない（771で削除された。774も同じ）。774クライアントの\n        // 0x08 は Custom Click Action のためこの限りではない。773クライアントの\n        // 0x08 も Custom Click Action のためこの限りではない。772クライアントの\n        // 0x08 も Custom Click Action のためこの限りではない。771クライアントの\n        // 0x08 も Custom Click Action のためこの限りではない。775クライアントの\n        // 0x08 も Custom Click Action のためこの限りではない。776クライアントの\n        // 0x08 も Custom Click Action のためこの限りではない。\n        if (isServer774() && !is774() && !is773() && !is772() && !is771() && !is775() && !is776()\n                && (packet.id() == 0x08 || packet.id() == 0x09)) {\n            return Outcome.drop();\n        }\n        // 775サーバーへは Custom Report Details（0x08）／Server Links\n        // （0x09）を送らない（771で削除された。775も同じ）。775クライアントの\n        // 0x08 は Custom Click Action のためこの限りではない。774クライアントの\n        // 0x08 も Custom Click Action のためこの限りではない。773クライアントの\n        // 0x08 も Custom Click Action のためこの限りではない。772クライアントの\n        // 0x08 も Custom Click Action のためこの限りではない。771クライアントの\n        // 0x08 も Custom Click Action のためこの限りではない。776クライアントの\n        // 0x08 も Custom Click Action のためこの限りではない。\n        if (isServer775() && !is775() && !is774() && !is773() && !is772() && !is771() && !is776()\n                && (packet.id() == 0x08 || packet.id() == 0x09)) {\n            return Outcome.drop();\n        }\n        // 776サーバーへは Custom Report Details（0x08）／Server Links\n        // （0x09）を送らない（771で削除された。776も同じ）。776クライアントの\n        // 0x08 は Custom Click Action のためこの限りではない。775クライアントの\n        // 0x08 も Custom Click Action のためこの限りではない。774クライアントの\n        // 0x08 も Custom Click Action のためこの限りではない。773クライアントの\n        // 0x08 も Custom Click Action のためこの限りではない。772クライアントの\n        // 0x08 も Custom Click Action のためこの限りではない。771クライアントの\n        // 0x08 も Custom Click Action のためこの限りではない。\n        if (isServer776() && !is776() && !is775() && !is774() && !is773() && !is772() && !is771()\n                && (packet.id() == 0x08 || packet.id() == 0x09)) {\n            return Outcome.drop();\n        }\n'), 'chatGlobalIndex field': ('    /**\n     * 773クライアントに送るリソースパック（pack_format 69）。\n     * 内部が773でない場合に使う差し替え用。内部が773の場合はバニラが\n     * 送ってきたパックをそのまま出すため null のままでよい。\n     * 出し分けの基準はクライアントのプロトコルである\n     * （{@link #packOverrideForClient()} 参照）。\n     */\n    private ResourcePack pack773;\n\n    /**\n     * 774クライアントに送るリソースパック（pack_format 75）。\n     * 内部が774でない場合に使う差し替え用。内部が774の場合はバニラが\n     * 送ってきたパックをそのまま出すため null のままでよい。\n     * 出し分けの基準はクライアントのプロトコルである\n     * （{@link #packOverrideForClient()} 参照）。\n     */\n    private ResourcePack pack774;\n\n    /**\n     * 775クライアントに送るリソースパック（pack_format 84）。\n     * 内部が775でない場合に使う差し替え用。内部が775の場合はバニラが\n     * 送ってきたパックをそのまま出すため null のままでよい。\n     * 出し分けの基準はクライアントのプロトコルである\n     * （{@link #packOverrideForClient()} 参照）。\n     */\n    private ResourcePack pack775;\n\n    /**\n     * 776クライアントに送るリソースパック（pack_format 88）。\n     * 内部が776でない場合に使う差し替え用。内部が776の場合はバニラが\n     * 送ってきたパックをそのまま出すため null のままでよい。\n     * 出し分けの基準はクライアントのプロトコルである\n     * （{@link #packOverrideForClient()} 参照）。\n     */\n    private ResourcePack pack776;\n\n    /**\n     * 770／771／772／773／774／775クライアントへ送る player_chat に付ける globalIndex。\n     * 769サーバーは globalIndex を送ってこないため、プロキシが\n     * セッションごとに 0 から数える。\n     */\n    private int chatGlobalIndex = 0;', '    /**\n     * 773クライアントに送るリソースパック（pack_format 69）。\n     * 内部が773でない場合に使う差し替え用。内部が773の場合はバニラが\n     * 送ってきたパックをそのまま出すため null のままでよい。\n     * 出し分けの基準はクライアントのプロトコルである\n     * （{@link #packOverrideForClient()} 参照）。\n     */\n    private ResourcePack pack773;\n\n    /**\n     * 774クライアントに送るリソースパック（pack_format 75）。\n     * 内部が774でない場合に使う差し替え用。内部が774の場合はバニラが\n     * 送ってきたパックをそのまま出すため null のままでよい。\n     * 出し分けの基準はクライアントのプロトコルである\n     * （{@link #packOverrideForClient()} 参照）。\n     */\n    private ResourcePack pack774;\n\n    /**\n     * 775クライアントに送るリソースパック（pack_format 84）。\n     * 内部が775でない場合に使う差し替え用。内部が775の場合はバニラが\n     * 送ってきたパックをそのまま出すため null のままでよい。\n     * 出し分けの基準はクライアントのプロトコルである\n     * （{@link #packOverrideForClient()} 参照）。\n     */\n    private ResourcePack pack775;\n\n    /**\n     * 776クライアントに送るリソースパック（pack_format 88）。\n     * 内部が776でない場合に使う差し替え用。内部が776の場合はバニラが\n     * 送ってきたパックをそのまま出すため null のままでよい。\n     * 出し分けの基準はクライアントのプロトコルである\n     * （{@link #packOverrideForClient()} 参照）。\n     */\n    private ResourcePack pack776;\n\n    /**\n     * 770／771／772／773／774／775／776クライアントへ送る player_chat に付ける globalIndex。\n     * 769サーバーは globalIndex を送ってこないため、プロキシが\n     * セッションごとに 0 から数える。\n     */\n    private int chatGlobalIndex = 0;')}'''

replace_section(
    "FIX_EDITS_FULL = {",
    "# 774パッチから外す編集。773ステージで既に live へ適用済みの修正で、\n# shift すると存在しないメソッド（translatePlayerInfo773To768 等）を\n# 呼ぶ壊れた編集になってしまうため。\nSKIP_EDITS = {",
    FIX_EDITS_FULL_776 + "\n\n\n# 775パッチから外す編集。774ステージで既に live へ適用済みの修正で、\n# shift すると存在しないメソッド（translatePlayerInfo774To768 等）を\n# 呼ぶ壊れた編集になってしまうため。\nSKIP_EDITS = {",
    "FIX_EDITS_FULL の書き換え")

SKIP_EDITS_776 = r'''SKIP_EDITS = {'handleServerPlayFrom775 775 branch (add)', 'handleServerJoinGame775 775 branch'}'''

replace_section(
    "SKIP_EDITS = {",
    "ADD_EDITS = [",
    SKIP_EDITS_776 + "\n\n\nADD_EDITS = [",
    "SKIP_EDITS の書き換え")

ADD_EDITS_776 = r'''ADD_EDITS = [('handleServerPlayFrom774 776 branch', '        // 773クライアント：774→773へ変換する。\n        if (is773()) {\n            return handleServerPlay774To773(packet);', '        // 776クライアント：774→775→776へ二段で変換する。\n        if (is776()) {\n            return handleServerPlay774To776(packet);\n        }\n        // 773クライアント：774→773へ変換する。\n        if (is773()) {\n            return handleServerPlay774To773(packet);'), ('handleServerPlayFrom775 776 branch', '        // 774クライアント：775→774へ変換する。\n        if (is774()) {\n            return handleServerPlay775To774(packet);', '        // 776クライアント：775→776へ変換する。\n        if (is776()) {\n            return handleServerPlay775To776(packet);\n        }\n        // 774クライアント：775→774へ変換する。\n        if (is774()) {\n            return handleServerPlay775To774(packet);'), ('handleServerJoinGame774 776 branch', '    private Outcome handleServerJoinGame774(MinecraftPacket packet) {\n        if (is775()) {', '    private Outcome handleServerJoinGame774(MinecraftPacket packet) {\n        if (is776()) {\n            advanceFlowSafely(() -> joinFlow.joinGameReceived());\n            List<MinecraftPacket> toClient = new ArrayList<>();\n            toClient.add(new MinecraftPacket(0x31, packet.payload()));\n            while (!pendingPacks.isEmpty()) {\n                PendingPack pack = pendingPacks.removeFirst();\n                lastSentPack = pack;\n                toClient.add(new MinecraftPacket(0x51,\n                        PlayTranslator.translateConfigAddResourcePack764To765(\n                                pack, packOverrideForClient())));\n            }\n            return new Outcome(List.of(), toClient, false, false);\n        }\n        if (is775()) {'), ('handleServerJoinGame775 776 branch', '    private Outcome handleServerJoinGame775(MinecraftPacket packet) {\n        if (is774()) {', '    private Outcome handleServerJoinGame775(MinecraftPacket packet) {\n        if (is776()) {\n            advanceFlowSafely(() -> joinFlow.joinGameReceived());\n            List<MinecraftPacket> toClient = new ArrayList<>();\n            toClient.add(new MinecraftPacket(0x31, packet.payload()));\n            while (!pendingPacks.isEmpty()) {\n                PendingPack pack = pendingPacks.removeFirst();\n                lastSentPack = pack;\n                toClient.add(new MinecraftPacket(0x51,\n                        PlayTranslator.translateConfigAddResourcePack764To765(\n                                pack, packOverrideForClient())));\n            }\n            return new Outcome(List.of(), toClient, false, false);\n        }\n        if (is774()) {')]'''

replace_section(
    "ADD_EDITS = [",
    "# CONFIG_774 に追加する、旧クライアント＋内部774の設定フェーズ変換器。\n# shift では作れない（773ステージにも同型の手書きメソッドがある）。",
    ADD_EDITS_776 + "\n\n\n# CONFIG_775 に追加する、旧クライアント＋内部775の設定フェーズ変換器。\n# shift では作れない（774ステージにも同型の手書きメソッドがある）。",
    "ADD_EDITS の書き換え")


FIX_BLOCKS_776 = r'''# FIX_BLOCKS：775版の修正は shift 後のブロックに既に含まれているため、
# 776では段数・連鎖の潰れ（772段の欠落）だけを直す。キーは読む側の版名
#（CLIENT_PLAY_776 等）のまま。
FIX_BLOCKS = {'CLIENT_PLAY_776': [('                                    ChunkTranslator.translateSetCreativeSlot772To774(\n                                            ChunkTranslator.translateSetCreativeSlot771To772(', '                                    ChunkTranslator.translateSetCreativeSlot773To774(\n                                            ChunkTranslator.translateSetCreativeSlot772To773(\n                                            ChunkTranslator.translateSetCreativeSlot771To772(')], 'SERVER_PLAY_776': [('        if (is775()) {\n            advanceFlowSafely(() -> joinFlow.joinGameReceived());\n            List<MinecraftPacket> toClient = new ArrayList<>();\n            toClient.add(new MinecraftPacket(0x30, packet.payload()));\n            while (!pendingPacks.isEmpty()) {\n                PendingPack pack = pendingPacks.removeFirst();\n                lastSentPack = pack;\n                toClient.add(new MinecraftPacket(0x4F,\n                        PlayTranslator.translateConfigAddResourcePack764To765(\n                                pack, packOverrideForClient())));\n            }\n            return new Outcome(List.of(), toClient, false, false);\n        }\n', '        if (is775()) {\n            advanceFlowSafely(() -> joinFlow.joinGameReceived());\n            List<MinecraftPacket> toClient = new ArrayList<>();\n            toClient.add(new MinecraftPacket(0x31, packet.payload()));\n            while (!pendingPacks.isEmpty()) {\n                PendingPack pack = pendingPacks.removeFirst();\n                lastSentPack = pack;\n                toClient.add(new MinecraftPacket(0x51,\n                        PlayTranslator.translateConfigAddResourcePack764To765(\n                                pack, packOverrideForClient())));\n            }\n            return new Outcome(List.of(), toClient, false, false);\n        }\n'), ('        // Respawn（776：0x52。775（0x50）と同形式のためそのまま送る）。', '        // Respawn（776：0x52。775（0x52）と同形式のためそのまま送る）。'), ('            if (is775()) {\n                // 775クライアント：Respawn は 0x50（同形式）のまま。\n                return Outcome.toClient(new MinecraftPacket(0x50, packet.payload()));', '            if (is775()) {\n                // 775クライアント：Respawn は 0x52（同形式）のまま。\n                return Outcome.toClient(new MinecraftPacket(0x52, packet.payload()));'), ('            if (is774()) {\n                // 774クライアント：Respawn は 0x50（同形式）のまま。\n                return Outcome.toClient(new MinecraftPacket(0x50, packet.payload()));\n            }\n            if (is770() || is771() || is772()) {', '            if (is774()) {\n                // 774クライアント：Respawn は 0x50（同形式）のまま。\n                return Outcome.toClient(new MinecraftPacket(0x50, packet.payload()));\n            }\n            if (is773()) {\n                // 773クライアント：Respawn は 0x50（同形式）のまま。\n                return Outcome.toClient(new MinecraftPacket(0x50, packet.payload()));\n            }\n            if (is770() || is771() || is772()) {'), ('767クライアント：776→775→774→773→772→771→770→769→768へ変換してから770→769へ。', '767クライアント：776→775→774→773→772→771→770→769→768へ変換してから769→768へ。'), ('766クライアント：776→775→774→773→772→771→770→769→768へ変換してから769→767へ。', '766クライアント：776→775→774→773→772→771→770→769→768へ変換してから768→766へ。')]}


FIX_BLOCKS_ALL = {'CONFIG_776': [('                byte[] payload772to774 =\n                        PlayTranslator.translateRegistryData772To774(payload771to772);\n                byte[] payload774to775 =\n                        PlayTranslator.translateRegistryData774To775(payload772to774);', '                byte[] payload772to773 =\n                        PlayTranslator.translateRegistryData772To773(payload771to772);\n                byte[] payload773to774 =\n                        PlayTranslator.translateRegistryData773To774(payload772to773);\n                byte[] payload774to775 =\n                        PlayTranslator.translateRegistryData774To775(payload773to774);', 4), ('                    byte[] payload772to774 =\n                            PlayTranslator.translateRegistryData772To774(payload771to772);\n                    byte[] payload774to775 =\n                            PlayTranslator.translateRegistryData774To775(payload772to774);', '                    byte[] payload772to773 =\n                            PlayTranslator.translateRegistryData772To773(payload771to772);\n                    byte[] payload773to774 =\n                            PlayTranslator.translateRegistryData773To774(payload772to773);\n                    byte[] payload774to775 =\n                            PlayTranslator.translateRegistryData774To775(payload773to774);', 1), ('                byte[] payload774to772 =\n                        PlayTranslator.translateRegistryData774To772(payload775to774);\n                byte[] payload772to771 =\n                        PlayTranslator.translateRegistryData772To771(payload774to772);', '                byte[] payload774to773 =\n                        PlayTranslator.translateRegistryData774To773(payload775to774);\n                byte[] payload773to772 =\n                        PlayTranslator.translateRegistryData773To772(payload774to773);\n                byte[] payload772to771 =\n                        PlayTranslator.translateRegistryData772To771(payload773to772);', 1), ('                                PlayTranslator.translateRegistryData774To775(\n                                        PlayTranslator.translateRegistryData772To774(\n                                                PlayTranslator.translateRegistryData771To772(\n                                                        PlayTranslator.translateRegistryData770To771(\n                                                                PlayTranslator.translateRegistryData769To770(\n                                                                        PlayTranslator.translateRegistryData768To769(packet.payload())))))))));', '                                PlayTranslator.translateRegistryData774To775(\n                                        PlayTranslator.translateRegistryData773To774(\n                                                PlayTranslator.translateRegistryData772To773(\n                                                        PlayTranslator.translateRegistryData771To772(\n                                                                PlayTranslator.translateRegistryData770To771(\n                                                                        PlayTranslator.translateRegistryData769To770(\n                                                                                PlayTranslator.translateRegistryData768To769(packet.payload()))))))))));', 1), ('                byte[] payload774to772 =\n                        PlayTranslator.translateRegistryData774To772(payload775to774);\n                if (payload774to772 == null) {\n                    yield Outcome.drop();\n                }\n                byte[] payload772to771 =', '                byte[] payload774to773 =\n                        PlayTranslator.translateRegistryData774To773(payload775to774);\n                if (payload774to773 == null) {\n                    yield Outcome.drop();\n                }\n                byte[] payload773to772 =\n                        PlayTranslator.translateRegistryData773To772(payload774to773);\n                if (payload773to772 == null) {\n                    yield Outcome.drop();\n                }\n                byte[] payload772to771 =', 4)]}'''

replace_section(
    "# FIX_BLOCKS：774版の修正は shift 後のブロックに既に含まれているため、\n# 775では段数・連鎖の潰れ（773段の欠落）とパケットIDだけを直す。キーは\n# 読む側の版名（CLIENT_PLAY_775 等）のまま。\nFIX_BLOCKS = {",
    "SERVER_PLAY_775_TO = r'''",
    FIX_BLOCKS_776 + "\n\n\nSERVER_PLAY_775_TO = r'''",
    "FIX_BLOCKS／FIX_BLOCKS_ALL の書き換え")


SERVER_PLAY_776_TO = r"""SERVER_PLAY_776_TO = r'''
    /**
     * 775サーバーのプレイ状態パケットを776形式にする。776に相当が無い／
     * 変換できないものは null を返す（ログ済み）。
     * 776クライアントへの送出と、774／773／772／771／770／769／768／767／766クライアントへの
     * 多段変換の最終段で共用する。経験値オーブは770・771・772・773・774・775・776とも通常の
     * Spawn Entity（type experience_orb）のため、専用の分岐は要らない。
     */
    private MinecraftPacket to776ServerPacket(MinecraftPacket packet) {
        PacketName name = PacketIds.nameFor(packet.id(), State.PLAY,
                Direction.CLIENTBOUND, ProtocolVersion.V26_1).orElse(null);
        if (name == null) {
            logUntranslatedOnce("PLAY_UNKNOWN_0x" + Integer.toHexString(packet.id()));
            return null;
        }
        byte[] translated = switch (name) {
            case PLAY_ENTITY_METADATA ->
                    ChunkTranslator.translateEntityMetadata775To776(packet.payload());
            case PLAY_SET_SLOT ->
                    ChunkTranslator.translateSetSlot775To776(packet.payload());
            case PLAY_SET_CURSOR_ITEM ->
                    ChunkTranslator.translateSetCursorItem775To776(packet.payload());
            case PLAY_SET_PLAYER_INVENTORY ->
                    ChunkTranslator.translateSetPlayerInventory775To776(packet.payload());
            case PLAY_WINDOW_ITEMS ->
                    ChunkTranslator.translateWindowItems775To776(packet.payload());
            case PLAY_SET_COOLDOWN ->
                    ChunkTranslator.translateSetCooldown775To776(packet.payload());
            case PLAY_ENTITY_EQUIPMENT ->
                    ChunkTranslator.translateEntityEquipment775To776(packet.payload());
            case PLAY_TRADE_LIST ->
                    ChunkTranslator.translateTradeList775To776(packet.payload());
            case PLAY_OPEN_WINDOW ->
                    ChunkTranslator.translateOpenWindow775To776(packet.payload());
            case PLAY_BLOCK_ACTION ->
                    ChunkTranslator.translateBlockAction775To776(packet.payload());
            case PLAY_SPAWN_ENTITY ->
                    ChunkTranslator.translateSpawnEntity775To776(packet.payload());
            case PLAY_SOUND_EFFECT ->
                    ChunkTranslator.translateSoundEffect775To776(packet.payload());
            case PLAY_ENTITY_SOUND_EFFECT ->
                    ChunkTranslator.translateEntitySoundEffect775To776(packet.payload());
            case PLAY_STOP_SOUND ->
                    ChunkTranslator.translateStopSound775To776(packet.payload());
            case PLAY_WORLD_PARTICLES ->
                    ChunkTranslator.translateWorldParticles775To776(packet.payload());
            case PLAY_ENTITY_EFFECT ->
                    ChunkTranslator.translateEntityEffect775To776(packet.payload());
            case PLAY_REMOVE_ENTITY_EFFECT ->
                    ChunkTranslator.translateRemoveEntityEffect775To776(packet.payload());
            case PLAY_DAMAGE_EVENT ->
                    ChunkTranslator.translateDamageEvent775To776(packet.payload());
            case PLAY_ENTITY_UPDATE_ATTRIBUTES ->
                    ChunkTranslator.translateUpdateAttributes775To776(packet.payload());
            case PLAY_CHUNK_DATA ->
                    ChunkTranslator.translateChunkData775To776(packet.payload());
            case PLAY_BLOCK_CHANGE ->
                    ChunkTranslator.translateBlockChange775To776(packet.payload());
            case PLAY_MULTI_BLOCK_CHANGE ->
                    ChunkTranslator.translateMultiBlockChange775To776(packet.payload());
            case PLAY_BLOCK_ENTITY_DATA ->
                    ChunkTranslator.translateBlockEntityData775To776(packet.payload());
            case PLAY_PLAYER_INFO_UPDATE ->
                    PlayTranslator.translatePlayerInfo775To776(packet.payload());
            case PLAY_PLAYER_CHAT ->
                    PlayTranslator.translatePlayerChat775To776(packet.payload());
            case PLAY_SYSTEM_CHAT, PLAY_PROFILELESS_CHAT ->
                    packet.payload(); // 同形式
            case PLAY_SYNCHRONIZE_PLAYER_POSITION, PLAY_UPDATE_TIME, PLAY_SET_SLOT_STATE ->
                    packet.payload(); // 同形式
            case PLAY_HELD_ITEM_SLOT ->
                    PlayTranslator.translateSetHeldItem775To776(packet.payload());
            case PLAY_EXPLOSION ->
                    ChunkTranslator.translateExplosion775To776(packet.payload());
            // ロケーターバー／ダイアログは775にもあるためそのまま通す（同形式）。
            case PLAY_TRACKED_WAYPOINT, PLAY_SHOW_DIALOG, PLAY_CLEAR_DIALOG ->
                    packet.payload();
            default -> null;
        };
        if (translated == null) {
            logUntranslatedOnce(name);
            return null;
        }
        OptionalInt id776 = PacketTranslator.translateId(name,
                ProtocolVersion.V26_1, ProtocolVersion.V26_2);
        if (id776.isEmpty()) {
            logUntranslatedOnce(name);
            return null;
        }
        return new MinecraftPacket(id776.getAsInt(), translated);
    }

    /**
     * 内部775→776クライアントのプレイ状態（サーバー→クライアント）。
     * 775→776へ変換してそのまま送る。
     */
    private Outcome handleServerPlay775To776(MinecraftPacket packet) {
        MinecraftPacket as776 = to776ServerPacket(packet);
        if (as776 == null) {
            return Outcome.drop();
        }
        return Outcome.toClient(as776);
    }

    /**
     * 内部774→776クライアントのプレイ状態（サーバー→クライアント）。
     * 773→774→775→776へ二段で変換する。
     */
    private Outcome handleServerPlay774To776(MinecraftPacket packet) {
        Outcome to775 = handleServerPlay774To775(packet);
        if (to775.toClient().isEmpty()) {
            return to775;
        }
        return handleServerPlay775To776(to775.toClient().get(0));
    }

    /**
     * 内部773→776クライアントのプレイ状態（サーバー→クライアント）。
     * 772→774→775→776へ二段で変換する。
     */
    private Outcome handleServerPlay772To776(MinecraftPacket packet) {
        Outcome to775 = handleServerPlay772To775(packet);
        if (to775.toClient().isEmpty()) {
            return to775;
        }
        return handleServerPlay775To776(to775.toClient().get(0));
    }

    /**
     * 内部772→776クライアントのプレイ状態（サーバー→クライアント）。
     * 771→774→775→776へ二段で変換する。
     */
    private Outcome handleServerPlay771To776(MinecraftPacket packet) {
        Outcome to775 = handleServerPlay771To775(packet);
        if (to775.toClient().isEmpty()) {
            return to775;
        }
        return handleServerPlay775To776(to775.toClient().get(0));
    }

    /**
     * 内部771→776クライアントのプレイ状態（サーバー→クライアント）。
     * 770→774→775→776へ二段で変換する。
     */
    private Outcome handleServerPlay770To776(MinecraftPacket packet) {
        Outcome to775 = handleServerPlay770To775(packet);
        if (to775.toClient().isEmpty()) {
            return to775;
        }
        return handleServerPlay775To776(to775.toClient().get(0));
    }

    /**
     * 内部770→776クライアントのプレイ状態（サーバー→クライアント）。
     * 769→774→775→776へ二段で変換する。
     */
    private Outcome handleServerPlay769To776(MinecraftPacket packet) {
        Outcome to775 = handleServerPlay769To775(packet);
        if (to775.toClient().isEmpty()) {
            return to775;
        }
        return handleServerPlay775To776(to775.toClient().get(0));
    }

    /**
     * 内部769→776クライアントのプレイ状態（サーバー→クライアント）。
     * 768→774→775→776へ二段で変換する。
     */
    private Outcome handleServerPlay768To776(MinecraftPacket packet) {
        Outcome to775 = handleServerPlay768To775(packet);
        if (to775.toClient().isEmpty()) {
            return to775;
        }
        return handleServerPlay775To776(to775.toClient().get(0));
    }

    /**
     * 内部768→776クライアントのプレイ状態（サーバー→クライアント）。
     * 767→774→775→776へ二段で変換する。
     */
    private Outcome handleServerPlay767To776(MinecraftPacket packet) {
        Outcome to775 = handleServerPlay767To775(packet);
        if (to775.toClient().isEmpty()) {
            return to775;
        }
        return handleServerPlay775To776(to775.toClient().get(0));
    }

    /**
     * 内部767→776クライアントのプレイ状態（サーバー→クライアント）。
     * 766→774→775→776へ二段で変換する。
     */
    private Outcome handleServerPlay766To776(MinecraftPacket packet) {
        Outcome to775 = handleServerPlay766To775(packet);
        if (to775.toClient().isEmpty()) {
            return to775;
        }
        return handleServerPlay775To776(to775.toClient().get(0));
    }
'''"""

replace_section(
    "}\n\n\nSERVER_PLAY_775_TO = r'''",
    "'''",
    "}\n\n\n" + SERVER_PLAY_776_TO,
    "SERVER_PLAY_776_TO の書き換え")

# --- part 2：main() の版名・コメント ---
rep('    for name in ("CLIENT_PLAY_774", "SERVER_PLAY_774", "CONFIG_774"):',
    '    for name in ("CLIENT_PLAY_775", "SERVER_PLAY_775", "CONFIG_775"):',
    "main() のブロック名 774→775", count=2)
rep('        new_name = name.replace("774", "775")',
    '        new_name = name.replace("775", "776")',
    "main() の new_name 置換")
rep('    for name in ("CLIENT_PLAY_775", "SERVER_PLAY_775", "CONFIG_775"):\n'
    '        out_lines.append(f"{name} = r\'\'\'{new_blocks[name]}\'\'\'")',
    '    for name in ("CLIENT_PLAY_776", "SERVER_PLAY_776", "CONFIG_776"):\n'
    '        out_lines.append(f"{name} = r\'\'\'{new_blocks[name]}\'\'\'")',
    "main() の出力ブロック名 775→776")
rep('    out_lines.append(f"SERVER_PLAY_775_TO = r\'\'\'{SERVER_PLAY_775_TO}\'\'\'")',
    '    out_lines.append(f"SERVER_PLAY_776_TO = r\'\'\'{SERVER_PLAY_776_TO}\'\'\'")',
    "main() の SERVER_PLAY_776_TO 出力")
rep('name.replace("773", "774")',
    'name.replace("774", "775")',
    "main() の edit 名置換 773→774", count=3)
rep('（773で適用済みのため774パッチから外す）',
    '（774で適用済みのため775パッチから外す）',
    "main() の SKIP 表示")
rep('# 773ステージと違い、774ではメソッド名の衝突は無い（shift 後の\n'
    '    # to774ClientPacket／to773ClientPacketFrom774／to773ServerPacketFrom774\n'
    '    # はすべて新規名のため、改名の手直しは要らない）。',
    '# 774ステージと違い、775ではメソッド名の衝突は無い（shift 後の\n'
    '    # to775ClientPacket／to774ClientPacketFrom775／to774ServerPacketFrom775\n'
    '    # はすべて新規名のため、改名の手直しは要らない）。',
    "main() のブロック手直しコメント")
rep('# INSERTS の後ろ（CONFIG_ANCHOR／CONFIG_REPLACEMENT／apply／\n'
    '    # insert_before_method）もずらして載せる。',
    '# INSERTS の後ろ（CONFIG_ANCHOR／CONFIG_REPLACEMENT／apply／\n'
    '    # insert_before_method）もずらして載せる。',
    "main() の INSERTS コメント")
rep('# CONFIG_ANCHOR：773パッチの rest は既に置換済みの anchor（default -> drop）\n'
    '    # で、shift が警告文の 772→773 と CONFIG_773→CONFIG_774 を済ませている\n'
    '    # ため、ここでの手直しは要らない。',
    '# CONFIG_ANCHOR：774パッチの rest は既に置換済みの anchor（default -> drop）\n'
    '    # で、shift が警告文の 773→774 と CONFIG_774→CONFIG_775 を済ませている\n'
    '    # ため、ここでの手直しは要らない。',
    "main() の CONFIG_ANCHOR コメント")

# --- DEDUP：編集名の衝突を解消する（776で初出） ---
# shift 後の編集名が衝突する場合がある（handleServerPlayFrom774 774 branch と
# handleServerPlayFrom774 775 branch がどちらも handleServerPlayFrom775 775 branch
# になる）。後から来た方に " (add)" を付け、SKIP_EDITS で外す側を指定する。
rep('    # ---- 手直し（FIX_EDITS_FULL / FIX_EDITS / SKIP_EDITS / ADD_EDITS / FIX_BLOCKS）----',
    '    # ---- 編集名の重複を解消する（776で初出）。shift 後の編集名が衝突する\n'
    '    # 場合がある（例：handleServerPlayFrom774 774 branch と handleServerPlayFrom774 775 branch\n'
    '    # がどちらも handleServerPlayFrom775 775 branch になる）。後から来た方に\n'
    '    # " (add)" を付けて区別し、SKIP_EDITS で外す側を指定する。\n'
    '    _seen_names = {}\n'
    '    _deduped = []\n'
    '    for _name, _old, _new in new_edits:\n'
    '        if _name in _seen_names:\n'
    '            _name = _name + " (add)"\n'
    '            print(f"DEDUP: {_name}（重複のため (add) を付けた）", file=sys.stderr)\n'
    '        _seen_names[_name] = True\n'
    '        _deduped.append((_name, _old, _new))\n'
    '    new_edits = _deduped\n'
    '\n'
    '    # ---- 手直し（FIX_EDITS_FULL / FIX_EDITS / SKIP_EDITS / ADD_EDITS / FIX_BLOCKS）----',
    "main() の DEDUP 追加")

# --- part 2：パッチヘッダ（docstring）の書き換え ---
# 775 のヘッダは 774 のままだった（生成元の版名も 774 のまま）ため、
# 776 では docstring 全体を 776 用に書き直す。
HEADER_776_DOC = '''ProxySession.java へ Minecraft 26.2（プロトコル776）の対応を入れる。

775パッチ（patch-proxysession-775.py）を1段ずらしたもの。生成元は
tools/gen-776-proxysession.py で、EDITS・3ブロック・INSERTS を機械的に
ずらし、776特有の箇所だけ手で直した。

776の要点（775からの差分）:
- パケットIDは 775→776 で 0 件変わる。唯一の違いは play toServer 0x3e の
  改名（spectate_entity → spectator_action、同じ位置）。
  Join Game 0x31、Spawn Position 0x61、Chunk Data 0x2D、Keep Alive 0x2C、
  Ping 0x3D、Start Configuration 0x76、Disconnect 0x20、Respawn 0x52、
  Add Resource Pack 0x51 はすべて 775 と同じ。
- ペイロード差分は 4 件だけ（本物のバニラ 26.2 の javap で確認済み）。
  Join Game（ClientboundLoginPacket）：末尾に boolean onlineMode が増える。
  776→775以下では落とし、775以下→776では ProxySession.onlineMode を付ける。
  Login Success（ClientboundLoginFinishedPacket）：末尾に UUID sessionId が増える。
  776→775以下では落とし、775以下→776ではランダムな UUID を合成して付ける。
  Teams（ClientboundSetPlayerTeamPacket$Parameters）：ワイヤ順が変わる。
  775 は displayName, options, nametagVisibility, collisionRule, color,
  playerPrefix, playerSuffix。776 は displayName, playerPrefix, playerSuffix,
  nametagVisibility, collisionRule, color（Optional<TeamColor>）, options。
  775 には Teams 専用の翻訳子が無かったため、ProxySession.java 内に
  private static メソッドとして新規実装する。
  SpectatorAction（ServerboundSpectatorActionPacket、play toServer 0x3e）：
  776 は varint 1つで 0＝観戦停止、それ以外は entityId+1。
  776→775：0 なら落とし、0以外は entityId=v-1 の spectate_entity として送る。
  775→776：spectate_entity の entityId を varint entityId+1 にする。
- 設定フェーズは番号が変わらない。新規パケットは無い。
- pack_format はリソース 88・データ 107（775 はリソース 84・データ 101）。
- チャットの checksum（u8）は770・771・772・773・774・775・776ともあるため、776⇔775では
  付け足しも剥がしもししない（番号だけ付け替える）。770⇔771の変換器は
  そのまま（769には checksum が無い）。
- Vehicle Move の onGround は769・770・771・772・773・774・775・776ともある（768に無い）。
  776⇔775では素通しする。
- Window Click は771・772・773・774・775・776とも HashedSlot のため落とす。Set Creative Slot は
  775・776とも UntrustedSlot のため、アイテム／成分を付け替えるだけ。
- Player Chat は先頭に globalIndex が770・771・772・773・774・775・776ともあるため素通しする。
  769サーバーからの player_chat にはプロキシが 0 から数えて付ける
  （770／771／772／773／774／775／776クライアント共用の chatGlobalIndex）。
- 設定フェーズは775と同形（同じ番号・同じ形式）で中継する。
  Registry Data の並び替えだけ 775→776 が要る（minecraft:dialog は
  775・776ともある。jukebox_song・絵画は同一。銅ゴーレム像などの
  ブロックエンティティは Registry Data には出ない）。
  Show Dialog（config 0x12）／Clear Dialog（0x11）は旧クライアント向けに
  落とす。Custom Click Action（config 0x08）は776サーバーへ通し、
  旧サーバー向けに落とす。Custom Report Details／Server Links
  （config サーバー行き）は772で削除されたため、776サーバー向けも落とす。
- 経験値オーブは770・771・772・773・774・775・776とも通常の Spawn Entity（type experience_orb）。
- 内部776サーバー＋776クライアントはネイティブ（素通し）。

使い方: repo ルートで `python3 detergentmc-protocol/tools/patch-proxysession-776.py`
（--check で差分確認）。全アンカーは一意でなければならない。
'''

replace_section(
    '    out_lines.append(\'\'\'#!/usr/bin/env python3\n"""ProxySession.java へ Minecraft 26.1（プロトコル775）の対応を入れる。',
    '全アンカーは一意でなければならない。\n"""',
    '    out_lines.append(\'\'\'#!/usr/bin/env python3\n"""' + HEADER_776_DOC + '"""',
    "パッチヘッダ docstring の書き換え")

if failed:
    print("失敗:", file=sys.stderr)
    for f in failed:
        print(" -", f, file=sys.stderr)
    sys.exit(1)

OUT.write_text(text, encoding="utf-8")
print("gen-776-proxysession.py を書き出した（shift() と SRC/OUT と FIX 類・ブロック・ヘッダ）。")
