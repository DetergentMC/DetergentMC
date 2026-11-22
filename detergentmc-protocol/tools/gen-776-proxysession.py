#!/usr/bin/env python3
"""patch-proxysession-775.py から patch-proxysession-776.py を生成する。

方針: 772パッチの EDITS を「1段ずつらす」（old773＝new772 の原則）。
- is チェーン（`is770() || is771() || is772()`）：末尾が772なら 773 を追加する。
  先頭が772の鎖は先頭に 773 を付ける（`shift_chain`）。「か」「と」の鎖は
  771を落とすため、鎖の edit は手書きで末尾に773行を足すこと（下の罠を参照）。
- ⇔チェーン・／リスト・・リスト: 末尾が772なら 773 を追加し、末尾が771なら
  772 を追加する（`shift_list`）。「・」「／」「⇔」の鎖は末尾追加が効く。
- 単独の 771→772、770→771（V1_21_6→V1_21_7、V1_21_5→V1_21_6、
  1.21.6→1.21.7、1.21.5→1.21.6、pack770→pack771、pack769→pack770）。
- 16進リテラル (0x..) と pack_format の数値は触らない（後で手で直す）。
  特にプレイのクライアント行きIDは773で108件変わるため、生成器の写しでは
  版の条件だけ変わってIDが772のまま残る（下の罠を参照）。
- CONTAINS-OLD 編集は new = PREFIX + old の形なので、
  new_773 = shift(PREFIX) + new_772、old_773 = new_772 とする。
- APPEND 編集は old_773 = new_772、new_773 = new_772 + shift(追加分)。
- REPLACE 編集は old_773 = new_772、new_773 = shift(new_772)。
- FIX_EDITS は `new` にだけ適用され、FIX_EDITS_FULL が `elif` で優先される。
- `CONFIG_772_EXTRA`／`CONFIG_773_EXTRA` はデッドコード（定義されるが
  main() から参照されない）。直さないこと。

【注意】この生成器で作ったパッチ（patch-proxysession-773.py）を適用した
後、ProxySession773Test で見つかった773漏れを手で直してある
（`tools/fix-773-proxysession-ids.py`／`-2.py`／`-3.py`、計43件）。
この生成器を再実行して作り直したパッチは、下記の手直しを含まないため
live と一致しない。再実行する場合は下記も取り込むこと。
- プレイ・クライアント行きIDの773化：`handleServerPlayFrom773` の
  ディスパッチID全7件（0x6F→0x74、0x2B→0x30、0x5A→0x5F、0x26→0x2B、
  0x36→0x3B、0x1C→0x20、0x4B→0x50）と Disconnect 出力の773分離（0x20）、
  Respawn に is773() 分岐追加（0x50）、Join Game に無かった is773() 分岐を
  763／766／汎用に新設、Disconnect／Respawn の is773() 分岐5本ずつ、
  Client Information は773サーバー向けも particleStatus を落とさない、
  Custom Click Action の `isServer772() || isServer771() || isServer772()` の
  重複を `isServer773() || isServer772() || isServer771()` に修正、設定フェーズの
  ダイアログ case 0x11, 0x12 を `handleServerConfiguration773From772` に追加、
  Add Resource Pack の773は0x4F、clientId フォールバック4件、From766 の
  Disconnect／Respawn に is773() 分岐追加（0x20／0x50）。
- `handleSynthesizedJoinGame` に is773() 分岐新設（預かった Join Game の
  番号が0x29に落ちていた）、From773 Respawn に is770()／is768()／is769() 分岐追加（0x4C）、
  `handleServerPlayUp`（764／765サーバー→上位クライアント）の gate に is773() を
  追加し Keep Alive 0x2B／Ping 0x3B／Disconnect 0x20／Respawn 0x50／
  Add Resource Pack 0x4F、`handleServerPlayFrom773` の末尾に 771／770／769／768
  クライアント向けの変換チェーンを追加（773サーバーからの Set Slot 等が
  落とされていた）。
- `handleServerConfiguration773From771` の新設（2026-11-12の監査で発見。
  773パッチで771分岐が欠けており、771サーバーが764／765用の汎用ハンドラに
  落ちて Finish Configuration が Keep Alive と誤読されていた）。
  `ProxySession773Test.join773Against771TranslatesSlots` で回帰を防ぐ。
- テストで見つかった2件の失敗の修正：`ProxySession772Test.join772Against771TranslatesSlots`
  （From771 Respawn の is772() 分岐が is773() に置き換えられていた）、
  `ProxySession771Test.join763Against771ConsumesConfigAs771Client`
  （`handleServerConfiguration` 763分岐で isServer771() が失われ、isServer770() が
  重複していた）。同種の監査で `handleServerPlayFrom771` Disconnect の is771() 分岐の
  消失も修正。監査方法：各EDITSの old/new に含まれる `isXXX()`／
  `handleServerXXXFromNNN` の集合を比較し、oldにあってnewに無いものを列挙する。

【罠】difflib の replace が鎖の末尾を置き換えて版を落とす。
鎖の末尾に1行足す差分（old772の末尾行 `... V1_21_6) {` → new772の
`... V1_21_6` ＋ `... V1_21_7) {`）を difflib は replace（1行→2行）とみなし、
shift（771→772、772→773）はその2行を `V1_21_7`／`V1_21_9` に写すため、
old773（＝new772）の `V1_21_6`／`V1_21_7` の2行が `V1_21_7`／`V1_21_9` に
置き換わって **V1_21_6 が落ちる**。ClientAdapter の decide()／differences()、
ClientProxy の internalProtocol validation で実際に起きた。
鎖の edit は手書きで「末尾に773行を足す」こと。

【罠】shift() が写さないもの。pack_format数値（64→69、81→88）は明示的に直す。
`PACK_FORMAT_1_21_7`（アンダースコア形）と `sha1772`／`configuredSha1772`
（大文字Sも）は shift 対象外。`NNN＝X.Y.Z`（＝形）は保護されるが、
「1.21.4（769）」のような `X.Y.Z（NNN）` 形は版だけずれて番号が残り壊れる。
「か」「と」の鎖は771を落とす。数値IDの中の版番号も素の replace では壊す
（ブロック状態ID 27730 の中の "773" を "774" にして 27740 にする）。
版番号の置換は正規表現 `(?<!\d)773(?!\d)` 等の lookaround で
「数字列の一部でない」ときだけ行うこと。
"""
import ast
import re
import sys
from pathlib import Path

SRC = Path("/home/hatch/workspace/detergentmc/detergentmc-protocol/tools/patch-proxysession-775.py")
OUT = Path("/home/hatch/workspace/detergentmc/detergentmc-protocol/tools/patch-proxysession-776.py")

warnings = []
_counter = [0]


def protect(text, pattern, repl_fn):
    """pattern に一致する部分をプレースホルダに置き換え、後で復元する。
    repl_fn(m) -> 置き換え後の文字列。戻り値 (new_text, placeholders)。
    プレースホルダの番号は全体で一意（衝突防止）。"""
    holders = {}

    def sub(m):
        key = f"\x02{_counter[0]}\x03"
        _counter[0] += 1
        holders[key] = repl_fn(m)
        return key

    return pattern.sub(sub, text), holders


def restore(text, holders):
    for k, v in holders.items():
        text = text.replace(k, v)
    return text


ARROW = re.compile(r"\d{3}(?:→\d{3})+")
DBL = re.compile(r"\d{3}(?:⇔\d{3})+")
SLASH = re.compile(r"\d{3}(?:／\d{3})+")
DOT = re.compile(r"\d{3}(?:・\d{3})+")
# 「768＝1.21.2／1.21.3」のようなプロトコル＝バージョン表記と
# 「768 (1.21.2/1.21.3)」のような括弧付き表記は
# ずらさず原文のまま保護する（768のエントリは768のまま残る）。
PROTO_VER = re.compile(r"\d{3}＝\d+\.\d+(?:\.\d+)?(?:／\d+\.\d+(?:\.\d+)?)?"
                       r"|\d{3} \(1\.\d+(?:\.\d+)?(?:/1\.\d+(?:\.\d+)?)?\)")
# is764() || is765() || ... || is771() のような条件チェーン。
# 末尾が is771()/isServer771() なら is772()/isServer772() を、
# is770()/isServer770() なら is771()/isServer771() を追加する。
IS_CHAIN = re.compile(r"((?:is(?:Server)?\d{3}\(\)\s*\|\|\s*)+is(?:Server)?775\(\))")
IS_CHAIN_770 = re.compile(r"((?:is(?:Server)?\d{3}\(\)\s*\|\|\s*)+is(?:Server)?774\(\))")


def shift_chain(m, kind):
    sep = "→" if kind == "arrow" else "⇔"
    nums = m.group(0).split(sep)
    if len(nums) == 2:
        # 2要素のペアは1段ずらす（774→775 は 775→776 になる）。
        return sep.join(str(int(n) + 1) for n in nums)
    if len(nums) >= 3 and nums[0] == "775":
        # 775から始まる多段チェーンは段が1つ増える（775→774→773 は
        # 776→775→774→773 になる）。末端（765/766/767）は固定。
        return "776" + sep + m.group(0)
    if nums[-1] == "775":
        return m.group(0) + sep + "776"
    warnings.append(f"arrow chain {m.group(0)} -> +1 each")
    return sep.join(str(int(n) + 1) for n in nums)


def shift_list(m, sep):
    nums = m.group(0).split(sep)
    if nums[-1] == "775":
        return m.group(0) + sep + "776"
    if nums[-1] == "774":
        # 新バージョンの一つ下まで列挙しているリストは 775 を追加する。
        return m.group(0) + sep + "775"
    warnings.append(f"list ends {nums[-1]}: {m.group(0)}")
    return m.group(0)


def shift(text):
    # 0) プロトコル＝バージョン表記を保護する（ずらさない）。
    text, h0 = protect(text, PROTO_VER, lambda m: m.group(0))
    # 1) チェーン・リストを先に処理してプレースホルダで保護する。
    text, h1 = protect(text, ARROW, lambda m: shift_chain(m, "arrow"))
    text, h2 = protect(text, DBL, lambda m: shift_chain(m, "dbl"))
    text, h3 = protect(text, SLASH, lambda m: shift_list(m, "／"))
    text, h4 = protect(text, DOT, lambda m: shift_list(m, "・"))
    # 1b) isチェーン（is764() || ... || is775()）に is776() を追加し、
    # そのまま保護する（後の数字シフトで二重にずれないように）。
    def is_chain_sub(m):
        chain = m.group(1)
        last = re.search(r"is(Server)?775\(\)$", chain)
        suffix = "isServer776()" if last.group(1) else "is776()"
        key = f"\x02{_counter[0]}\x03"
        _counter[0] += 1
        is_chain_holders[key] = chain + " || " + suffix
        return key

    is_chain_holders = {}
    text = IS_CHAIN.sub(is_chain_sub, text)

    def is_chain_sub_770(m):
        chain = m.group(1)
        last = re.search(r"is(Server)?774\(\)$", chain)
        suffix = "isServer775()" if last.group(1) else "is775()"
        key = f"\x02{_counter[0]}\x03"
        _counter[0] += 1
        is_chain_holders[key] = chain + " || " + suffix
        return key

    text = IS_CHAIN_770.sub(is_chain_sub_770, text)
    holders = {**h0, **h1, **h2, **h3, **h4, **is_chain_holders}
    # 2) バージョン文字列
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
    # pack769/pack770/pack771 は数字シフトの対象外にする（pack769→pack770 と
    # 数字シフトが二重にかかると pack770→pack771 のような誤りになる）。
    def pack_sub(m):
        key = f"\x02{_counter[0]}\x03"
        _counter[0] += 1
        pack_holders[key] = m.group(0)
        return key

    pack_holders = {}
    text = re.sub(r"pack(?:770|771|772|773|774|775|776)", pack_sub, text)
    holders.update(pack_holders)
    # PROTOCOL_77x も数字シフトから保護する（バージョン置き換えで既に
    # ずらし済みのため。二重にかかると PROTOCOL_770→772 になる）。
    text = re.sub(r"PROTOCOL_77[0-9]", pack_sub, text)
    holders.update(pack_holders)
    # 3) 単独の数字。\b は日本語文字を \w と見なすため使えないので、
    # 数字の前後を見る lookaround を使う（識別子内の 769 も対象にする）。
    # 16進リテラル (0x..）は数字続きなので対象外になる。
    text = re.sub(r"(?<!\d)775(?!\d)", "\x01775", text)
    text = re.sub(r"(?<!\d)774(?!\d)", "\x01774", text)
    text = re.sub(r"(?<!\d)773(?!\d)", "774", text)
    text = text.replace("\x01774", "775")
    text = text.replace("\x01775", "776")
    # 4) 復元
    return restore(text, holders)


FIX_EDITS = {'constructor javadoc': [('775＝26.1）', '775＝26.1、776＝26.2）')], 'serverProtocol() javadoc': [('775＝26.1）。 */', '775＝26.1、776＝26.2）。 */')], 'pack775 field': [('776クライアントに送るリソースパック（pack_format 84）', '776クライアントに送るリソースパック（pack_format 88）')], 'packOverrideForClient javadoc': [('出し分けの基準はクライアントのプロトコルである：776→pack776\n     * （pack_format 84）、775→pack775\n     * （pack_format 75）、774→pack774\n     * （pack_format 69）、772→pack773\n     * （pack_format 64）、771→pack772\n     * （pack_format 63）、770→pack771\n     * （pack_format 55）、769→pack770\n     * （pack_format 46）、768→pack768\n     * （pack_format 42）、767→pack767', '出し分けの基準はクライアントのプロトコルである：776→pack776\n     * （pack_format 88）、775→pack775\n     * （pack_format 84）、774→pack774\n     * （pack_format 75）、773→pack773\n     * （pack_format 69）、772→pack772\n     * （pack_format 64）、771→pack771\n     * （pack_format 63）、770→pack770\n     * （pack_format 55）、769→pack769\n     * （pack_format 46）、768→pack768\n     * （pack_format 42）、767→pack767')], 'packOverrideForClient branch': [(': pack770 != null ? pack770\n                    : pack768 != null ? pack768', ': pack770 != null ? pack770\n                    : pack769 != null ? pack769\n                    : pack768 != null ? pack768')], 'startConfigurationClientId': [('is776() ? 0x76 : is773() || is774() || is775() ? 0x74', 'is775() || is776() ? 0x76 : is773() || is774() ? 0x74')], 'spawnPositionClientId': [('is776() ? 0x61 : is773() || is774() || is775() ? 0x5F', 'is775() || is776() ? 0x61 : is773() || is774() ? 0x5F')], 'keepAliveClientId': [('is776() ? 0x2C : is773() || is774() || is775() ? 0x2B', 'is775() || is776() ? 0x2C : is773() || is774() ? 0x2B')], 'pingClientId': [('is776() ? 0x3D : is773() || is774() || is775() ? 0x3B', 'is775() || is776() ? 0x3D : is773() || is774() ? 0x3B')], 'synthesized add-pack': [('is776() ? 0x51 : is773() || is774() || is775() ? 0x4F', 'is775() || is776() ? 0x51 : is773() || is774() ? 0x4F')], 'handleServerPlayFrom763 ResourcePack 775': [('is776() ? 0x51 : is773() || is774() || is775() ? 0x4F', 'is775() || is776() ? 0x51 : is773() || is774() ? 0x4F')], 'handleServerPlayFrom772 775 branch': [('// 776クライアント：773→776へ変換する。', '// 776クライアント：772→776へ変換する。')], 'handleServerPlayFrom770 775 branch': [('// 776クライアント：770→771→772→773→774→775→776へ五段で変換する。', '// 776クライアント：770→771→772→773→774→775→776へ六段で変換する。')], 'handleServerPlayFrom771 775 branch': [('// 776クライアント：771→772→773→774→775→776へ四段で変換する。', '// 776クライアント：771→772→773→774→775→776へ五段で変換する。')], 'handleServerPlayFrom766 775 branch': [('// 内部766は 766→767→768→769→770→771→772→773→774→775→776 の九段、内部767は 767→768→769→770→771→772→773→774→775→776 の八段で送る。', '// 内部766は 766→767→768→769→770→771→772→773→774→775→776 の十段、内部767は 767→768→769→770→771→772→773→774→775→776 の九段で送る。')], 'handleServerPlayFrom775 775 branch': [('// 776クライアント：773→774→775→776へ二段で変換する。', '// 776クライアント：773→774→775→776へ三段で変換する。'), ('            return handleServerPlay774To776(packet);', '            return handleServerPlay773To776(packet);')]}


# (old, new) を丸ごと置き換える編集。shift した (old, new) では
# アンカーが live に無かったり、意味が変わったりするもの。
FIX_EDITS_FULL = {'constructor validation': ('if (serverProtocol != ProtocolVersion.V1_19\n                && serverProtocol != ProtocolVersion.V1_19_1\n                && serverProtocol != ProtocolVersion.V1_19_3\n                && serverProtocol != ProtocolVersion.V1_19_4\n                && serverProtocol != ProtocolVersion.V1_20_1\n                && serverProtocol != ProtocolVersion.V1_20_2\n                && serverProtocol != ProtocolVersion.V1_20_4\n                && serverProtocol != ProtocolVersion.V1_20_5\n                && serverProtocol != ProtocolVersion.V1_21\n                && serverProtocol != ProtocolVersion.V1_21_2\n                && serverProtocol != ProtocolVersion.V1_21_4\n                && serverProtocol != ProtocolVersion.V1_21_5\n                && serverProtocol != ProtocolVersion.V1_21_6\n                && serverProtocol != ProtocolVersion.V1_21_7\n                && serverProtocol != ProtocolVersion.V1_21_9\n                && serverProtocol != ProtocolVersion.V1_21_11\n                && serverProtocol != ProtocolVersion.V26_1) {\n            throw new IllegalArgumentException(\n                    "serverProtocol must be 759 (1.19), 760 (1.19.1/1.19.2), 761 (1.19.3), 762 (1.19.4), 763 (1.20.1), 764 (1.20.2), "\n                            + "765 (1.20.4), 766 (1.20.5/1.20.6), 767 (1.21/1.21.1), "\n                            + "768 (1.21.2/1.21.3), 769 (1.21.4), 770 (1.21.5), 771 (1.21.6), 772 (1.21.7), 773 (1.21.9/1.21.10), 774 (1.21.11) or 775 (26.1): "\n                            + serverProtocol);\n        }', 'if (serverProtocol != ProtocolVersion.V1_19\n                && serverProtocol != ProtocolVersion.V1_19_1\n                && serverProtocol != ProtocolVersion.V1_19_3\n                && serverProtocol != ProtocolVersion.V1_19_4\n                && serverProtocol != ProtocolVersion.V1_20_1\n                && serverProtocol != ProtocolVersion.V1_20_2\n                && serverProtocol != ProtocolVersion.V1_20_4\n                && serverProtocol != ProtocolVersion.V1_20_5\n                && serverProtocol != ProtocolVersion.V1_21\n                && serverProtocol != ProtocolVersion.V1_21_2\n                && serverProtocol != ProtocolVersion.V1_21_4\n                && serverProtocol != ProtocolVersion.V1_21_5\n                && serverProtocol != ProtocolVersion.V1_21_6\n                && serverProtocol != ProtocolVersion.V1_21_7\n                && serverProtocol != ProtocolVersion.V1_21_9\n                && serverProtocol != ProtocolVersion.V1_21_11\n                && serverProtocol != ProtocolVersion.V26_1\n                && serverProtocol != ProtocolVersion.V26_2) {\n            throw new IllegalArgumentException(\n                    "serverProtocol must be 759 (1.19), 760 (1.19.1/1.19.2), 761 (1.19.3), 762 (1.19.4), 763 (1.20.1), 764 (1.20.2), "\n                            + "765 (1.20.4), 766 (1.20.5/1.20.6), 767 (1.21/1.21.1), "\n                            + "768 (1.21.2/1.21.3), 769 (1.21.4), 770 (1.21.5), 771 (1.21.6), 772 (1.21.7), 773 (1.21.9/1.21.10), 774 (1.21.11), 775 (26.1) or 776 (26.2): "\n                            + serverProtocol);\n        }'), 'handleServerPlayFrom769 Respawn 775': ('        // Respawn（770：0x4B。769（0x4C）と同形式のため769の変換を使い回す）。\n        if (packet.id() == 0x4B) {\n            if (is775()) {\n                // 775クライアント：Respawn は 0x52（同形式）のまま。\n                return Outcome.toClient(new MinecraftPacket(0x52, packet.payload()));\n            }\n            if (is774()) {\n                // 774クライアント：Respawn は 0x50（同形式）のまま。\n                return Outcome.toClient(new MinecraftPacket(0x50, packet.payload()));\n            }\n', '        // Respawn（770：0x4B。769（0x4C）と同形式のため769の変換を使い回す）。\n        if (packet.id() == 0x4B) {\n            if (is776()) {\n                // 776クライアント：Respawn は 0x52（同形式）のまま。\n                return Outcome.toClient(new MinecraftPacket(0x52, packet.payload()));\n            }\n            if (is775()) {\n                // 775クライアント：Respawn は 0x52（同形式）のまま。\n                return Outcome.toClient(new MinecraftPacket(0x52, packet.payload()));\n            }\n            if (is774()) {\n                // 774クライアント：Respawn は 0x50（同形式）のまま。\n                return Outcome.toClient(new MinecraftPacket(0x50, packet.payload()));\n            }\n'), 'handleServerPlayFrom768 Respawn 775': ('        // Respawn（769：0x4C。768と同形式のため768の変換を使い回す）。\n        if (packet.id() == 0x4C) {\n            if (is775()) {\n                // 775クライアント：Respawn は 0x52（同形式）のまま。\n                return Outcome.toClient(new MinecraftPacket(0x52, packet.payload()));\n            }\n            if (is774()) {\n                // 774クライアント：Respawn は 0x50（同形式）のまま。\n                return Outcome.toClient(new MinecraftPacket(0x50, packet.payload()));\n            }\n', '        // Respawn（769：0x4C。768と同形式のため768の変換を使い回す）。\n        if (packet.id() == 0x4C) {\n            if (is776()) {\n                // 776クライアント：Respawn は 0x52（同形式）のまま。\n                return Outcome.toClient(new MinecraftPacket(0x52, packet.payload()));\n            }\n            if (is775()) {\n                // 775クライアント：Respawn は 0x52（同形式）のまま。\n                return Outcome.toClient(new MinecraftPacket(0x52, packet.payload()));\n            }\n            if (is774()) {\n                // 774クライアント：Respawn は 0x50（同形式）のまま。\n                return Outcome.toClient(new MinecraftPacket(0x50, packet.payload()));\n            }\n'), 'handleServerPlayFrom770 Respawn 775': ('        // Respawn（771：0x4B。770（0x4B）と同形式のため770の変換を使い回す）。\n        if (packet.id() == 0x4B) {\n            if (is775()) {\n                // 775クライアント：Respawn は 0x52（同形式）のまま。\n                return Outcome.toClient(new MinecraftPacket(0x52, packet.payload()));\n            }\n            if (is774()) {\n                // 774クライアント：Respawn は 0x50（同形式）のまま。\n                return Outcome.toClient(new MinecraftPacket(0x50, packet.payload()));\n            }\n', '        // Respawn（771：0x4B。770（0x4B）と同形式のため770の変換を使い回す）。\n        if (packet.id() == 0x4B) {\n            if (is776()) {\n                // 776クライアント：Respawn は 0x52（同形式）のまま。\n                return Outcome.toClient(new MinecraftPacket(0x52, packet.payload()));\n            }\n            if (is775()) {\n                // 775クライアント：Respawn は 0x52（同形式）のまま。\n                return Outcome.toClient(new MinecraftPacket(0x52, packet.payload()));\n            }\n            if (is774()) {\n                // 774クライアント：Respawn は 0x50（同形式）のまま。\n                return Outcome.toClient(new MinecraftPacket(0x50, packet.payload()));\n            }\n'), 'handleServerPlayFrom771 Respawn 775': ('        // Respawn（772：0x4B。771（0x4B）と同形式のためそのまま送る）。\n        if (packet.id() == 0x4B) {\n            if (is771()) {\n                // 771クライアント：Respawn は 0x4B（同形式）のまま。\n                return Outcome.toClient(new MinecraftPacket(0x4B, packet.payload()));\n            }\n            if (is775()) {\n                // 775クライアント：Respawn は 0x52（同形式）のまま。\n                return Outcome.toClient(new MinecraftPacket(0x52, packet.payload()));\n            }\n            if (is774()) {\n                // 774クライアント：Respawn は 0x50（同形式）のまま。\n                return Outcome.toClient(new MinecraftPacket(0x50, packet.payload()));\n            }\n', '        // Respawn（772：0x4B。771（0x4B）と同形式のためそのまま送る）。\n        if (packet.id() == 0x4B) {\n            if (is776()) {\n                // 776クライアント：Respawn は 0x52（同形式）のまま。\n                return Outcome.toClient(new MinecraftPacket(0x52, packet.payload()));\n            }\n            if (is771()) {\n                // 771クライアント：Respawn は 0x4B（同形式）のまま。\n                return Outcome.toClient(new MinecraftPacket(0x4B, packet.payload()));\n            }\n            if (is775()) {\n                // 775クライアント：Respawn は 0x52（同形式）のまま。\n                return Outcome.toClient(new MinecraftPacket(0x52, packet.payload()));\n            }\n            if (is774()) {\n                // 774クライアント：Respawn は 0x50（同形式）のまま。\n                return Outcome.toClient(new MinecraftPacket(0x50, packet.payload()));\n            }\n'), 'handleServerPlayFrom772 Respawn 775': ('        // Respawn（774：0x50。772（0x4B）と同形式のためそのまま送る）。\n        if (packet.id() == 0x50) {\n            if (is775()) {\n                // 775クライアント：Respawn は 0x52（同形式）のまま。\n                return Outcome.toClient(new MinecraftPacket(0x52, packet.payload()));\n            }\n            if (is774()) {\n                // 774クライアント：Respawn は 0x50（同形式）のまま。\n                return Outcome.toClient(new MinecraftPacket(0x50, packet.payload()));\n            }\n            if (is773()) {\n                // 773クライアント：Respawn は 0x50（同形式）のまま。\n                return Outcome.toClient(new MinecraftPacket(0x50, packet.payload()));\n            }\n', '        // Respawn（774：0x50。772（0x4B）と同形式のためそのまま送る）。\n        if (packet.id() == 0x50) {\n            if (is776()) {\n                // 776クライアント：Respawn は 0x52（同形式）のまま。\n                return Outcome.toClient(new MinecraftPacket(0x52, packet.payload()));\n            }\n            if (is775()) {\n                // 775クライアント：Respawn は 0x52（同形式）のまま。\n                return Outcome.toClient(new MinecraftPacket(0x52, packet.payload()));\n            }\n            if (is774()) {\n                // 774クライアント：Respawn は 0x50（同形式）のまま。\n                return Outcome.toClient(new MinecraftPacket(0x50, packet.payload()));\n            }\n            if (is773()) {\n                // 773クライアント：Respawn は 0x50（同形式）のまま。\n                return Outcome.toClient(new MinecraftPacket(0x50, packet.payload()));\n            }\n'), 'handleServerPlayFrom775 Respawn 775': ('        // Respawn（775：0x52。774（0x50）と同形式のためそのまま送る）。\n        if (packet.id() == 0x52) {\n            if (is774()) {\n                // 774クライアント：Respawn は 0x50（同形式）のまま。\n                return Outcome.toClient(new MinecraftPacket(0x50, packet.payload()));\n            }\n            if (is773()) {\n                // 773クライアント：Respawn は 0x50（同形式）のまま。\n                return Outcome.toClient(new MinecraftPacket(0x50, packet.payload()));\n            }\n', '        // Respawn（775：0x52。774（0x50）と同形式のためそのまま送る）。\n        if (packet.id() == 0x52) {\n            if (is776()) {\n                // 776クライアント：Respawn は 0x52（同形式）のまま。\n                return Outcome.toClient(new MinecraftPacket(0x52, packet.payload()));\n            }\n            if (is774()) {\n                // 774クライアント：Respawn は 0x50（同形式）のまま。\n                return Outcome.toClient(new MinecraftPacket(0x50, packet.payload()));\n            }\n            if (is773()) {\n                // 773クライアント：Respawn は 0x50（同形式）のまま。\n                return Outcome.toClient(new MinecraftPacket(0x50, packet.payload()));\n            }\n'), 'handleServerJoinGame770 775 branch': ('    private Outcome handleServerJoinGame770(MinecraftPacket packet) {\n        if (is775()) {\n            advanceFlowSafely(() -> joinFlow.joinGameReceived());\n            List<MinecraftPacket> toClient = new ArrayList<>();\n            toClient.add(new MinecraftPacket(0x31, packet.payload()));\n            while (!pendingPacks.isEmpty()) {\n                PendingPack pack = pendingPacks.removeFirst();\n                lastSentPack = pack;\n                toClient.add(new MinecraftPacket(0x51,\n                        PlayTranslator.translateConfigAddResourcePack764To765(\n                                pack, packOverrideForClient())));\n            }\n            return new Outcome(List.of(), toClient, false, false);\n        }\n        if (is774()) {\n            advanceFlowSafely(() -> joinFlow.joinGameReceived());\n            List<MinecraftPacket> toClient = new ArrayList<>();\n            toClient.add(new MinecraftPacket(0x30, packet.payload()));\n            while (!pendingPacks.isEmpty()) {\n                PendingPack pack = pendingPacks.removeFirst();\n                lastSentPack = pack;\n                toClient.add(new MinecraftPacket(0x4F,\n                        PlayTranslator.translateConfigAddResourcePack764To765(\n                                pack, packOverrideForClient())));\n            }\n            return new Outcome(List.of(), toClient, false, false);\n        }\n        if (is773()) {\n            advanceFlowSafely(() -> joinFlow.joinGameReceived());\n            List<MinecraftPacket> toClient = new ArrayList<>();\n            toClient.add(new MinecraftPacket(0x30, packet.payload()));\n            while (!pendingPacks.isEmpty()) {\n                PendingPack pack = pendingPacks.removeFirst();\n                lastSentPack = pack;\n                toClient.add(new MinecraftPacket(0x4F,\n                        PlayTranslator.translateConfigAddResourcePack764To765(\n                                pack, packOverrideForClient())));\n            }\n            return new Outcome(List.of(), toClient, false, false);\n        }\n        if (is772()) {\n            advanceFlowSafely(() -> joinFlow.joinGameReceived());\n            List<MinecraftPacket> toClient = new ArrayList<>();\n            toClient.add(new MinecraftPacket(0x2B, packet.payload()));\n            while (!pendingPacks.isEmpty()) {\n                PendingPack pack = pendingPacks.removeFirst();\n                lastSentPack = pack;\n                toClient.add(new MinecraftPacket(0x4A,\n                        PlayTranslator.translateConfigAddResourcePack764To765(\n                                pack, packOverrideForClient())));\n            }\n            return new Outcome(List.of(), toClient, false, false);\n        }\n        if (is771()) {\n            advanceFlowSafely(() -> joinFlow.joinGameReceived());\n            List<MinecraftPacket> toClient = new ArrayList<>();\n            toClient.add(new MinecraftPacket(0x2B, packet.payload()));\n            while (!pendingPacks.isEmpty()) {\n                PendingPack pack = pendingPacks.removeFirst();\n                lastSentPack = pack;\n                toClient.add(new MinecraftPacket(0x4A,\n                        PlayTranslator.translateConfigAddResourcePack764To765(\n                                pack, packOverrideForClient())));\n            }\n            return new Outcome(List.of(), toClient, false, false);\n        }\n        if (is769()) {\n            advanceFlowSafely(() -> joinFlow.joinGameReceived());\n            List<MinecraftPacket> toClient = new ArrayList<>();\n            toClient.add(new MinecraftPacket(0x2C, packet.payload()));\n            while (!pendingPacks.isEmpty()) {\n                PendingPack pack = pendingPacks.removeFirst();\n                lastSentPack = pack;\n                toClient.add(new MinecraftPacket(0x4B,\n                        PlayTranslator.translateConfigAddResourcePack764To765(\n                                pack, packOverrideForClient())));\n            }\n            return new Outcome(List.of(), toClient, false, false);\n        }\n        return handleServerJoinGame769(packet);\n    }\n', '    private Outcome handleServerJoinGame770(MinecraftPacket packet) {\n        if (is776()) {\n            advanceFlowSafely(() -> joinFlow.joinGameReceived());\n            List<MinecraftPacket> toClient = new ArrayList<>();\n            toClient.add(new MinecraftPacket(0x31, packet.payload()));\n            while (!pendingPacks.isEmpty()) {\n                PendingPack pack = pendingPacks.removeFirst();\n                lastSentPack = pack;\n                toClient.add(new MinecraftPacket(0x51,\n                        PlayTranslator.translateConfigAddResourcePack764To765(\n                                pack, packOverrideForClient())));\n            }\n            return new Outcome(List.of(), toClient, false, false);\n        }\n        if (is775()) {\n            advanceFlowSafely(() -> joinFlow.joinGameReceived());\n            List<MinecraftPacket> toClient = new ArrayList<>();\n            toClient.add(new MinecraftPacket(0x31, packet.payload()));\n            while (!pendingPacks.isEmpty()) {\n                PendingPack pack = pendingPacks.removeFirst();\n                lastSentPack = pack;\n                toClient.add(new MinecraftPacket(0x51,\n                        PlayTranslator.translateConfigAddResourcePack764To765(\n                                pack, packOverrideForClient())));\n            }\n            return new Outcome(List.of(), toClient, false, false);\n        }\n        if (is774()) {\n            advanceFlowSafely(() -> joinFlow.joinGameReceived());\n            List<MinecraftPacket> toClient = new ArrayList<>();\n            toClient.add(new MinecraftPacket(0x30, packet.payload()));\n            while (!pendingPacks.isEmpty()) {\n                PendingPack pack = pendingPacks.removeFirst();\n                lastSentPack = pack;\n                toClient.add(new MinecraftPacket(0x4F,\n                        PlayTranslator.translateConfigAddResourcePack764To765(\n                                pack, packOverrideForClient())));\n            }\n            return new Outcome(List.of(), toClient, false, false);\n        }\n        if (is773()) {\n            advanceFlowSafely(() -> joinFlow.joinGameReceived());\n            List<MinecraftPacket> toClient = new ArrayList<>();\n            toClient.add(new MinecraftPacket(0x30, packet.payload()));\n            while (!pendingPacks.isEmpty()) {\n                PendingPack pack = pendingPacks.removeFirst();\n                lastSentPack = pack;\n                toClient.add(new MinecraftPacket(0x4F,\n                        PlayTranslator.translateConfigAddResourcePack764To765(\n                                pack, packOverrideForClient())));\n            }\n            return new Outcome(List.of(), toClient, false, false);\n        }\n        if (is772()) {\n            advanceFlowSafely(() -> joinFlow.joinGameReceived());\n            List<MinecraftPacket> toClient = new ArrayList<>();\n            toClient.add(new MinecraftPacket(0x2B, packet.payload()));\n            while (!pendingPacks.isEmpty()) {\n                PendingPack pack = pendingPacks.removeFirst();\n                lastSentPack = pack;\n                toClient.add(new MinecraftPacket(0x4A,\n                        PlayTranslator.translateConfigAddResourcePack764To765(\n                                pack, packOverrideForClient())));\n            }\n            return new Outcome(List.of(), toClient, false, false);\n        }\n        if (is771()) {\n            advanceFlowSafely(() -> joinFlow.joinGameReceived());\n            List<MinecraftPacket> toClient = new ArrayList<>();\n            toClient.add(new MinecraftPacket(0x2B, packet.payload()));\n            while (!pendingPacks.isEmpty()) {\n                PendingPack pack = pendingPacks.removeFirst();\n                lastSentPack = pack;\n                toClient.add(new MinecraftPacket(0x4A,\n                        PlayTranslator.translateConfigAddResourcePack764To765(\n                                pack, packOverrideForClient())));\n            }\n            return new Outcome(List.of(), toClient, false, false);\n        }\n        if (is769()) {\n            advanceFlowSafely(() -> joinFlow.joinGameReceived());\n            List<MinecraftPacket> toClient = new ArrayList<>();\n            toClient.add(new MinecraftPacket(0x2C, packet.payload()));\n            while (!pendingPacks.isEmpty()) {\n                PendingPack pack = pendingPacks.removeFirst();\n                lastSentPack = pack;\n                toClient.add(new MinecraftPacket(0x4B,\n                        PlayTranslator.translateConfigAddResourcePack764To765(\n                                pack, packOverrideForClient())));\n            }\n            return new Outcome(List.of(), toClient, false, false);\n        }\n        return handleServerJoinGame769(packet);\n    }\n'), 'handleServerJoinGame771 775 branch': ('    private Outcome handleServerJoinGame771(MinecraftPacket packet) {\n        if (is775()) {\n            advanceFlowSafely(() -> joinFlow.joinGameReceived());\n            List<MinecraftPacket> toClient = new ArrayList<>();\n            toClient.add(new MinecraftPacket(0x31, packet.payload()));\n            while (!pendingPacks.isEmpty()) {\n                PendingPack pack = pendingPacks.removeFirst();\n                lastSentPack = pack;\n                toClient.add(new MinecraftPacket(0x51,\n                        PlayTranslator.translateConfigAddResourcePack764To765(\n                                pack, packOverrideForClient())));\n            }\n            return new Outcome(List.of(), toClient, false, false);\n        }\n        if (is774()) {\n            advanceFlowSafely(() -> joinFlow.joinGameReceived());\n            List<MinecraftPacket> toClient = new ArrayList<>();\n            toClient.add(new MinecraftPacket(0x30, packet.payload()));\n            while (!pendingPacks.isEmpty()) {\n                PendingPack pack = pendingPacks.removeFirst();\n                lastSentPack = pack;\n                toClient.add(new MinecraftPacket(0x4F,\n                        PlayTranslator.translateConfigAddResourcePack764To765(\n                                pack, packOverrideForClient())));\n            }\n            return new Outcome(List.of(), toClient, false, false);\n        }\n        if (is773()) {\n            advanceFlowSafely(() -> joinFlow.joinGameReceived());\n            List<MinecraftPacket> toClient = new ArrayList<>();\n            toClient.add(new MinecraftPacket(0x30, packet.payload()));\n            while (!pendingPacks.isEmpty()) {\n                PendingPack pack = pendingPacks.removeFirst();\n                lastSentPack = pack;\n                toClient.add(new MinecraftPacket(0x4F,\n                        PlayTranslator.translateConfigAddResourcePack764To765(\n                                pack, packOverrideForClient())));\n            }\n            return new Outcome(List.of(), toClient, false, false);\n        }\n        if (is772()) {\n            advanceFlowSafely(() -> joinFlow.joinGameReceived());\n            List<MinecraftPacket> toClient = new ArrayList<>();\n            toClient.add(new MinecraftPacket(0x2B, packet.payload()));\n            while (!pendingPacks.isEmpty()) {\n                PendingPack pack = pendingPacks.removeFirst();\n                lastSentPack = pack;\n                toClient.add(new MinecraftPacket(0x4A,\n                        PlayTranslator.translateConfigAddResourcePack764To765(\n                                pack, packOverrideForClient())));\n            }\n            return new Outcome(List.of(), toClient, false, false);\n        }\n        if (is770()) {\n            advanceFlowSafely(() -> joinFlow.joinGameReceived());\n            List<MinecraftPacket> toClient = new ArrayList<>();\n            toClient.add(new MinecraftPacket(0x2B, packet.payload()));\n            while (!pendingPacks.isEmpty()) {\n                PendingPack pack = pendingPacks.removeFirst();\n                lastSentPack = pack;\n                toClient.add(new MinecraftPacket(0x4A,\n                        PlayTranslator.translateConfigAddResourcePack764To765(\n                                pack, packOverrideForClient())));\n            }\n            return new Outcome(List.of(), toClient, false, false);\n        }\n        return handleServerJoinGame770(packet);\n    }\n', '    private Outcome handleServerJoinGame771(MinecraftPacket packet) {\n        if (is776()) {\n            advanceFlowSafely(() -> joinFlow.joinGameReceived());\n            List<MinecraftPacket> toClient = new ArrayList<>();\n            toClient.add(new MinecraftPacket(0x31, packet.payload()));\n            while (!pendingPacks.isEmpty()) {\n                PendingPack pack = pendingPacks.removeFirst();\n                lastSentPack = pack;\n                toClient.add(new MinecraftPacket(0x51,\n                        PlayTranslator.translateConfigAddResourcePack764To765(\n                                pack, packOverrideForClient())));\n            }\n            return new Outcome(List.of(), toClient, false, false);\n        }\n        if (is775()) {\n            advanceFlowSafely(() -> joinFlow.joinGameReceived());\n            List<MinecraftPacket> toClient = new ArrayList<>();\n            toClient.add(new MinecraftPacket(0x31, packet.payload()));\n            while (!pendingPacks.isEmpty()) {\n                PendingPack pack = pendingPacks.removeFirst();\n                lastSentPack = pack;\n                toClient.add(new MinecraftPacket(0x51,\n                        PlayTranslator.translateConfigAddResourcePack764To765(\n                                pack, packOverrideForClient())));\n            }\n            return new Outcome(List.of(), toClient, false, false);\n        }\n        if (is774()) {\n            advanceFlowSafely(() -> joinFlow.joinGameReceived());\n            List<MinecraftPacket> toClient = new ArrayList<>();\n            toClient.add(new MinecraftPacket(0x30, packet.payload()));\n            while (!pendingPacks.isEmpty()) {\n                PendingPack pack = pendingPacks.removeFirst();\n                lastSentPack = pack;\n                toClient.add(new MinecraftPacket(0x4F,\n                        PlayTranslator.translateConfigAddResourcePack764To765(\n                                pack, packOverrideForClient())));\n            }\n            return new Outcome(List.of(), toClient, false, false);\n        }\n        if (is773()) {\n            advanceFlowSafely(() -> joinFlow.joinGameReceived());\n            List<MinecraftPacket> toClient = new ArrayList<>();\n            toClient.add(new MinecraftPacket(0x30, packet.payload()));\n            while (!pendingPacks.isEmpty()) {\n                PendingPack pack = pendingPacks.removeFirst();\n                lastSentPack = pack;\n                toClient.add(new MinecraftPacket(0x4F,\n                        PlayTranslator.translateConfigAddResourcePack764To765(\n                                pack, packOverrideForClient())));\n            }\n            return new Outcome(List.of(), toClient, false, false);\n        }\n        if (is772()) {\n            advanceFlowSafely(() -> joinFlow.joinGameReceived());\n            List<MinecraftPacket> toClient = new ArrayList<>();\n            toClient.add(new MinecraftPacket(0x2B, packet.payload()));\n            while (!pendingPacks.isEmpty()) {\n                PendingPack pack = pendingPacks.removeFirst();\n                lastSentPack = pack;\n                toClient.add(new MinecraftPacket(0x4A,\n                        PlayTranslator.translateConfigAddResourcePack764To765(\n                                pack, packOverrideForClient())));\n            }\n            return new Outcome(List.of(), toClient, false, false);\n        }\n        if (is770()) {\n            advanceFlowSafely(() -> joinFlow.joinGameReceived());\n            List<MinecraftPacket> toClient = new ArrayList<>();\n            toClient.add(new MinecraftPacket(0x2B, packet.payload()));\n            while (!pendingPacks.isEmpty()) {\n                PendingPack pack = pendingPacks.removeFirst();\n                lastSentPack = pack;\n                toClient.add(new MinecraftPacket(0x4A,\n                        PlayTranslator.translateConfigAddResourcePack764To765(\n                                pack, packOverrideForClient())));\n            }\n            return new Outcome(List.of(), toClient, false, false);\n        }\n        return handleServerJoinGame770(packet);\n    }\n'), 'handleServerJoinGame772 775 branch': ('    private Outcome handleServerJoinGame772(MinecraftPacket packet) {\n        if (is775()) {\n            advanceFlowSafely(() -> joinFlow.joinGameReceived());\n            List<MinecraftPacket> toClient = new ArrayList<>();\n            toClient.add(new MinecraftPacket(0x31, packet.payload()));\n            while (!pendingPacks.isEmpty()) {\n                PendingPack pack = pendingPacks.removeFirst();\n                lastSentPack = pack;\n                toClient.add(new MinecraftPacket(0x51,\n                        PlayTranslator.translateConfigAddResourcePack764To765(\n                                pack, packOverrideForClient())));\n            }\n            return new Outcome(List.of(), toClient, false, false);\n        }\n        if (is774()) {\n            advanceFlowSafely(() -> joinFlow.joinGameReceived());\n            List<MinecraftPacket> toClient = new ArrayList<>();\n            toClient.add(new MinecraftPacket(0x30, packet.payload()));\n            while (!pendingPacks.isEmpty()) {\n                PendingPack pack = pendingPacks.removeFirst();\n                lastSentPack = pack;\n                toClient.add(new MinecraftPacket(0x4F,\n                        PlayTranslator.translateConfigAddResourcePack764To765(\n                                pack, packOverrideForClient())));\n            }\n            return new Outcome(List.of(), toClient, false, false);\n        }\n        if (is773()) {\n            advanceFlowSafely(() -> joinFlow.joinGameReceived());\n            List<MinecraftPacket> toClient = new ArrayList<>();\n            toClient.add(new MinecraftPacket(0x30, packet.payload()));\n            while (!pendingPacks.isEmpty()) {\n                PendingPack pack = pendingPacks.removeFirst();\n                lastSentPack = pack;\n                toClient.add(new MinecraftPacket(0x4F,\n                        PlayTranslator.translateConfigAddResourcePack764To765(\n                                pack, packOverrideForClient())));\n            }\n            return new Outcome(List.of(), toClient, false, false);\n        }\n        if (is771()) {\n            advanceFlowSafely(() -> joinFlow.joinGameReceived());\n            List<MinecraftPacket> toClient = new ArrayList<>();\n            toClient.add(new MinecraftPacket(0x2B, packet.payload()));\n            while (!pendingPacks.isEmpty()) {\n                PendingPack pack = pendingPacks.removeFirst();\n                lastSentPack = pack;\n                toClient.add(new MinecraftPacket(0x4A,\n                        PlayTranslator.translateConfigAddResourcePack764To765(\n                                pack, packOverrideForClient())));\n            }\n            return new Outcome(List.of(), toClient, false, false);\n        }\n        return handleServerJoinGame771(packet);\n    }\n', '    private Outcome handleServerJoinGame772(MinecraftPacket packet) {\n        if (is776()) {\n            advanceFlowSafely(() -> joinFlow.joinGameReceived());\n            List<MinecraftPacket> toClient = new ArrayList<>();\n            toClient.add(new MinecraftPacket(0x31, packet.payload()));\n            while (!pendingPacks.isEmpty()) {\n                PendingPack pack = pendingPacks.removeFirst();\n                lastSentPack = pack;\n                toClient.add(new MinecraftPacket(0x51,\n                        PlayTranslator.translateConfigAddResourcePack764To765(\n                                pack, packOverrideForClient())));\n            }\n            return new Outcome(List.of(), toClient, false, false);\n        }\n        if (is775()) {\n            advanceFlowSafely(() -> joinFlow.joinGameReceived());\n            List<MinecraftPacket> toClient = new ArrayList<>();\n            toClient.add(new MinecraftPacket(0x31, packet.payload()));\n            while (!pendingPacks.isEmpty()) {\n                PendingPack pack = pendingPacks.removeFirst();\n                lastSentPack = pack;\n                toClient.add(new MinecraftPacket(0x51,\n                        PlayTranslator.translateConfigAddResourcePack764To765(\n                                pack, packOverrideForClient())));\n            }\n            return new Outcome(List.of(), toClient, false, false);\n        }\n        if (is774()) {\n            advanceFlowSafely(() -> joinFlow.joinGameReceived());\n            List<MinecraftPacket> toClient = new ArrayList<>();\n            toClient.add(new MinecraftPacket(0x30, packet.payload()));\n            while (!pendingPacks.isEmpty()) {\n                PendingPack pack = pendingPacks.removeFirst();\n                lastSentPack = pack;\n                toClient.add(new MinecraftPacket(0x4F,\n                        PlayTranslator.translateConfigAddResourcePack764To765(\n                                pack, packOverrideForClient())));\n            }\n            return new Outcome(List.of(), toClient, false, false);\n        }\n        if (is773()) {\n            advanceFlowSafely(() -> joinFlow.joinGameReceived());\n            List<MinecraftPacket> toClient = new ArrayList<>();\n            toClient.add(new MinecraftPacket(0x30, packet.payload()));\n            while (!pendingPacks.isEmpty()) {\n                PendingPack pack = pendingPacks.removeFirst();\n                lastSentPack = pack;\n                toClient.add(new MinecraftPacket(0x4F,\n                        PlayTranslator.translateConfigAddResourcePack764To765(\n                                pack, packOverrideForClient())));\n            }\n            return new Outcome(List.of(), toClient, false, false);\n        }\n        if (is771()) {\n            advanceFlowSafely(() -> joinFlow.joinGameReceived());\n            List<MinecraftPacket> toClient = new ArrayList<>();\n            toClient.add(new MinecraftPacket(0x2B, packet.payload()));\n            while (!pendingPacks.isEmpty()) {\n                PendingPack pack = pendingPacks.removeFirst();\n                lastSentPack = pack;\n                toClient.add(new MinecraftPacket(0x4A,\n                        PlayTranslator.translateConfigAddResourcePack764To765(\n                                pack, packOverrideForClient())));\n            }\n            return new Outcome(List.of(), toClient, false, false);\n        }\n        return handleServerJoinGame771(packet);\n    }\n'), 'synthesized config registry loop': ('        if (is766() || is767() || is768() || is769() || is770() || is771() || is772() || is773() || is774() || is775()) {\n            // 766／767／768／769／770／771／772／773／774／775：レジストリごとに1パケット（エントリ配列）。\n            for (byte[] registryPayload : PlayTranslator.buildRegistryData766(registries)) {\n                if (is773()) {\n                    // 773：766→767→768→769→770→771→772→773へ七段で並べ替える。\n                    byte[] payload766to767 =\n                            PlayTranslator.translateRegistryData766To767(registryPayload);\n                    byte[] payload767to768 =\n                            PlayTranslator.translateRegistryData767To768(payload766to767);\n                    byte[] payload768to769 =\n                            PlayTranslator.translateRegistryData768To769(payload767to768);\n                    byte[] payload769to770 =\n                            PlayTranslator.translateRegistryData769To770(payload768to769);\n                    byte[] payload770to771 =\n                            PlayTranslator.translateRegistryData770To771(payload769to770);\n                    byte[] payload771to772 =\n                            PlayTranslator.translateRegistryData771To772(payload770to771);\n                    registryPayload =\n                            PlayTranslator.translateRegistryData772To773(payload771to772);\n                }\n                if (is774()) {\n                    // 774：766→767→768→769→770→771→772→773→774へ八段で並べ替える。\n                    byte[] payload766to767 =\n                            PlayTranslator.translateRegistryData766To767(registryPayload);\n                    byte[] payload767to768 =\n                            PlayTranslator.translateRegistryData767To768(payload766to767);\n                    byte[] payload768to769 =\n                            PlayTranslator.translateRegistryData768To769(payload767to768);\n                    byte[] payload769to770 =\n                            PlayTranslator.translateRegistryData769To770(payload768to769);\n                    byte[] payload770to771 =\n                            PlayTranslator.translateRegistryData770To771(payload769to770);\n                    byte[] payload771to772 =\n                            PlayTranslator.translateRegistryData771To772(payload770to771);\n                    byte[] payload772to773 =\n                            PlayTranslator.translateRegistryData772To773(payload771to772);\n                    registryPayload =\n                            PlayTranslator.translateRegistryData773To774(payload772to773);\n                }\n                if (is775()) {\n                    // 775：766→767→768→769→770→771→772→773→774→775へ九段で並べ替える。\n                    byte[] payload766to767 =\n                            PlayTranslator.translateRegistryData766To767(registryPayload);\n                    byte[] payload767to768 =\n                            PlayTranslator.translateRegistryData767To768(payload766to767);\n                    byte[] payload768to769 =\n                            PlayTranslator.translateRegistryData768To769(payload767to768);\n                    byte[] payload769to770 =\n                            PlayTranslator.translateRegistryData769To770(payload768to769);\n                    byte[] payload770to771 =\n                            PlayTranslator.translateRegistryData770To771(payload769to770);\n                    byte[] payload771to772 =\n                            PlayTranslator.translateRegistryData771To772(payload770to771);\n                    byte[] payload772to773 =\n                            PlayTranslator.translateRegistryData772To773(payload771to772);\n                    byte[] payload773to774 =\n                            PlayTranslator.translateRegistryData773To774(payload772to773);\n                    registryPayload =\n                            PlayTranslator.translateRegistryData774To775(payload773to774);\n                }\n', '        if (is766() || is767() || is768() || is769() || is770() || is771() || is772() || is773() || is774() || is775() || is776()) {\n            // 766／767／768／769／770／771／772／773／774／775／776：レジストリごとに1パケット（エントリ配列）。\n            for (byte[] registryPayload : PlayTranslator.buildRegistryData766(registries)) {\n                if (is773()) {\n                    // 773：766→767→768→769→770→771→772→773へ七段で並べ替える。\n                    byte[] payload766to767 =\n                            PlayTranslator.translateRegistryData766To767(registryPayload);\n                    byte[] payload767to768 =\n                            PlayTranslator.translateRegistryData767To768(payload766to767);\n                    byte[] payload768to769 =\n                            PlayTranslator.translateRegistryData768To769(payload767to768);\n                    byte[] payload769to770 =\n                            PlayTranslator.translateRegistryData769To770(payload768to769);\n                    byte[] payload770to771 =\n                            PlayTranslator.translateRegistryData770To771(payload769to770);\n                    byte[] payload771to772 =\n                            PlayTranslator.translateRegistryData771To772(payload770to771);\n                    registryPayload =\n                            PlayTranslator.translateRegistryData772To773(payload771to772);\n                }\n                if (is774()) {\n                    // 774：766→767→768→769→770→771→772→773→774へ八段で並べ替える。\n                    byte[] payload766to767 =\n                            PlayTranslator.translateRegistryData766To767(registryPayload);\n                    byte[] payload767to768 =\n                            PlayTranslator.translateRegistryData767To768(payload766to767);\n                    byte[] payload768to769 =\n                            PlayTranslator.translateRegistryData768To769(payload767to768);\n                    byte[] payload769to770 =\n                            PlayTranslator.translateRegistryData769To770(payload768to769);\n                    byte[] payload770to771 =\n                            PlayTranslator.translateRegistryData770To771(payload769to770);\n                    byte[] payload771to772 =\n                            PlayTranslator.translateRegistryData771To772(payload770to771);\n                    byte[] payload772to773 =\n                            PlayTranslator.translateRegistryData772To773(payload771to772);\n                    registryPayload =\n                            PlayTranslator.translateRegistryData773To774(payload772to773);\n                }\n                if (is775()) {\n                    // 775：766→767→768→769→770→771→772→773→774→775へ九段で並べ替える。\n                    byte[] payload766to767 =\n                            PlayTranslator.translateRegistryData766To767(registryPayload);\n                    byte[] payload767to768 =\n                            PlayTranslator.translateRegistryData767To768(payload766to767);\n                    byte[] payload768to769 =\n                            PlayTranslator.translateRegistryData768To769(payload767to768);\n                    byte[] payload769to770 =\n                            PlayTranslator.translateRegistryData769To770(payload768to769);\n                    byte[] payload770to771 =\n                            PlayTranslator.translateRegistryData770To771(payload769to770);\n                    byte[] payload771to772 =\n                            PlayTranslator.translateRegistryData771To772(payload770to771);\n                    byte[] payload772to773 =\n                            PlayTranslator.translateRegistryData772To773(payload771to772);\n                    byte[] payload773to774 =\n                            PlayTranslator.translateRegistryData773To774(payload772to773);\n                    registryPayload =\n                            PlayTranslator.translateRegistryData774To775(payload773to774);\n                }\n                if (is776()) {\n                    // 776：766→767→768→769→770→771→772→773→774→775→776へ十段で並べ替える。\n                    byte[] payload766to767 =\n                            PlayTranslator.translateRegistryData766To767(registryPayload);\n                    byte[] payload767to768 =\n                            PlayTranslator.translateRegistryData767To768(payload766to767);\n                    byte[] payload768to769 =\n                            PlayTranslator.translateRegistryData768To769(payload767to768);\n                    byte[] payload769to770 =\n                            PlayTranslator.translateRegistryData769To770(payload768to769);\n                    byte[] payload770to771 =\n                            PlayTranslator.translateRegistryData770To771(payload769to770);\n                    byte[] payload771to772 =\n                            PlayTranslator.translateRegistryData771To772(payload770to771);\n                    byte[] payload772to773 =\n                            PlayTranslator.translateRegistryData772To773(payload771to772);\n                    byte[] payload773to774 =\n                            PlayTranslator.translateRegistryData773To774(payload772to773);\n                    byte[] payload774to775 =\n                            PlayTranslator.translateRegistryData774To775(payload773to774);\n                    registryPayload =\n                            PlayTranslator.translateRegistryData775To776(payload774to775);\n                }\n'), 'handleServerConfiguration 763 branch': ('        if (is763Like()) {\n            if (isServer775()) {\n                return handleServerConfiguration763From775(packet);\n            }\n            if (isServer774()) {\n                return handleServerConfiguration763From774(packet);\n            }\n            if (isServer773()) {\n                return handleServerConfiguration763From773(packet);\n            }\n            if (isServer772()) {\n                return handleServerConfiguration763From772(packet);\n            }\n            if (isServer771()) {\n                return handleServerConfiguration763From771(packet);\n            }\n            if (isServer770()) {\n                return handleServerConfiguration763From770(packet);\n            }\n            if (isServer769()) {\n                return handleServerConfiguration763From769(packet);\n            }\n            if (isServer768()) {\n                return handleServerConfiguration763From768(packet);\n            }\n            if (isServer767()) {\n                return handleServerConfiguration763From767(packet);\n            }\n            if (isServer766()) {\n                return handleServerConfiguration763From766(packet);\n            }\n', '        if (is763Like()) {\n            if (isServer776()) {\n                return handleServerConfiguration763From776(packet);\n            }\n            if (isServer775()) {\n                return handleServerConfiguration763From775(packet);\n            }\n            if (isServer774()) {\n                return handleServerConfiguration763From774(packet);\n            }\n            if (isServer773()) {\n                return handleServerConfiguration763From773(packet);\n            }\n            if (isServer772()) {\n                return handleServerConfiguration763From772(packet);\n            }\n            if (isServer771()) {\n                return handleServerConfiguration763From771(packet);\n            }\n            if (isServer770()) {\n                return handleServerConfiguration763From770(packet);\n            }\n            if (isServer769()) {\n                return handleServerConfiguration763From769(packet);\n            }\n            if (isServer768()) {\n                return handleServerConfiguration763From768(packet);\n            }\n            if (isServer767()) {\n                return handleServerConfiguration763From767(packet);\n            }\n            if (isServer766()) {\n                return handleServerConfiguration763From766(packet);\n            }\n'), 'handleServerConfiguration 775 branch': ('        // 775クライアントは設定フェーズを中継する（番号と Registry Data\n        // の形式だけ変換する）。\n        if (is775()) {\n            if (isServer775()) {\n                // 内部775＋775クライアントはネイティブのためここには来ない。\n                // 念のためそのまま通す。\n                return Outcome.toClient(packet);\n            }\n            if (isServer774()) {\n                return handleServerConfiguration775From774(packet);\n            }\n            if (isServer773()) {\n                return handleServerConfiguration775From773(packet);\n            }\n            if (isServer772()) {\n                return handleServerConfiguration775From772(packet);\n            }\n            if (isServer771()) {\n                return handleServerConfiguration775From771(packet);\n            }\n            if (isServer770()) {\n                return handleServerConfiguration775From770(packet);\n            }\n            if (isServer769()) {\n                return handleServerConfiguration775From769(packet);\n            }\n            if (isServer768()) {\n                return handleServerConfiguration775From768(packet);\n            }\n            if (isServer767()) {\n                return handleServerConfiguration775From767(packet);\n            }\n            if (isServer766()) {\n                return handleServerConfiguration775From766(packet);\n            }\n            return handleServerConfiguration775(packet, isServer764());\n        }\n        // 774クライアントは設定フェーズを中継する（番号と Registry Data\n        // の形式だけ変換する）。\n        if (is774()) {\n            if (isServer774()) {\n                // 内部774＋774クライアントはネイティブのためここには来ない。\n                // 念のためそのまま通す。\n                return Outcome.toClient(packet);\n            }\n            if (isServer775()) {\n                return handleServerConfiguration774From775(packet);\n            }\n            if (isServer773()) {\n                return handleServerConfiguration774From773(packet);\n            }\n            if (isServer772()) {\n                return handleServerConfiguration774From772(packet);\n            }\n            if (isServer771()) {\n                return handleServerConfiguration774From771(packet);\n            }\n            if (isServer770()) {\n                return handleServerConfiguration774From770(packet);\n            }\n            if (isServer769()) {\n                return handleServerConfiguration774From769(packet);\n            }\n            if (isServer768()) {\n                return handleServerConfiguration774From768(packet);\n            }\n            if (isServer767()) {\n                return handleServerConfiguration774From767(packet);\n            }\n            if (isServer766()) {\n                return handleServerConfiguration774From766(packet);\n            }\n            return handleServerConfiguration774(packet, isServer764());\n        }\n        // 773クライアントは設定フェーズを中継する（番号と Registry Data\n        // の形式だけ変換する）。\n        if (is773()) {\n            if (isServer773()) {\n                // 内部773＋773クライアントはネイティブのためここには来ない。\n                // 念のためそのまま通す。\n                return Outcome.toClient(packet);\n            }\n            if (isServer774()) {\n                return handleServerConfiguration773From774(packet);\n            }\n            if (isServer772()) {\n                return handleServerConfiguration773From772(packet);\n            }\n            if (isServer771()) {\n                return handleServerConfiguration773From771(packet);\n            }\n            if (isServer770()) {\n                return handleServerConfiguration773From770(packet);\n            }\n            if (isServer769()) {\n                return handleServerConfiguration773From769(packet);\n            }\n            if (isServer768()) {\n                return handleServerConfiguration773From768(packet);\n            }\n            if (isServer767()) {\n                return handleServerConfiguration773From767(packet);\n            }\n            if (isServer766()) {\n                return handleServerConfiguration773From766(packet);\n            }\n            return handleServerConfiguration773(packet, isServer764());\n        }\n', '        // 776クライアントは設定フェーズを中継する（番号と Registry Data\n        // の形式だけ変換する）。\n        if (is776()) {\n            if (isServer776()) {\n                // 内部776＋776クライアントはネイティブのためここには来ない。\n                // 念のためそのまま通す。\n                return Outcome.toClient(packet);\n            }\n            if (isServer775()) {\n                return handleServerConfiguration776From775(packet);\n            }\n            if (isServer774()) {\n                return handleServerConfiguration776From774(packet);\n            }\n            if (isServer773()) {\n                return handleServerConfiguration776From773(packet);\n            }\n            if (isServer772()) {\n                return handleServerConfiguration776From772(packet);\n            }\n            if (isServer771()) {\n                return handleServerConfiguration776From771(packet);\n            }\n            if (isServer770()) {\n                return handleServerConfiguration776From770(packet);\n            }\n            if (isServer769()) {\n                return handleServerConfiguration776From769(packet);\n            }\n            if (isServer768()) {\n                return handleServerConfiguration776From768(packet);\n            }\n            if (isServer767()) {\n                return handleServerConfiguration776From767(packet);\n            }\n            if (isServer766()) {\n                return handleServerConfiguration776From766(packet);\n            }\n            return handleServerConfiguration776(packet, isServer764());\n        }\n        // 775クライアントは設定フェーズを中継する（番号と Registry Data\n        // の形式だけ変換する）。\n        if (is775()) {\n            if (isServer775()) {\n                // 内部775＋775クライアントはネイティブのためここには来ない。\n                // 念のためそのまま通す。\n                return Outcome.toClient(packet);\n            }\n            if (isServer776()) {\n                return handleServerConfiguration775From776(packet);\n            }\n            if (isServer774()) {\n                return handleServerConfiguration775From774(packet);\n            }\n            if (isServer773()) {\n                return handleServerConfiguration775From773(packet);\n            }\n            if (isServer772()) {\n                return handleServerConfiguration775From772(packet);\n            }\n            if (isServer771()) {\n                return handleServerConfiguration775From771(packet);\n            }\n            if (isServer770()) {\n                return handleServerConfiguration775From770(packet);\n            }\n            if (isServer769()) {\n                return handleServerConfiguration775From769(packet);\n            }\n            if (isServer768()) {\n                return handleServerConfiguration775From768(packet);\n            }\n            if (isServer767()) {\n                return handleServerConfiguration775From767(packet);\n            }\n            if (isServer766()) {\n                return handleServerConfiguration775From766(packet);\n            }\n            return handleServerConfiguration775(packet, isServer764());\n        }\n        // 774クライアントは設定フェーズを中継する（番号と Registry Data\n        // の形式だけ変換する）。\n        if (is774()) {\n            if (isServer774()) {\n                // 内部774＋774クライアントはネイティブのためここには来ない。\n                // 念のためそのまま通す。\n                return Outcome.toClient(packet);\n            }\n            if (isServer776()) {\n                return handleServerConfiguration774From776(packet);\n            }\n            if (isServer775()) {\n                return handleServerConfiguration774From775(packet);\n            }\n            if (isServer773()) {\n                return handleServerConfiguration774From773(packet);\n            }\n            if (isServer772()) {\n                return handleServerConfiguration774From772(packet);\n            }\n            if (isServer771()) {\n                return handleServerConfiguration774From771(packet);\n            }\n            if (isServer770()) {\n                return handleServerConfiguration774From770(packet);\n            }\n            if (isServer769()) {\n                return handleServerConfiguration774From769(packet);\n            }\n            if (isServer768()) {\n                return handleServerConfiguration774From768(packet);\n            }\n            if (isServer767()) {\n                return handleServerConfiguration774From767(packet);\n            }\n            if (isServer766()) {\n                return handleServerConfiguration774From766(packet);\n            }\n            return handleServerConfiguration774(packet, isServer764());\n        }\n        // 773クライアントは設定フェーズを中継する（番号と Registry Data\n        // の形式だけ変換する）。\n        if (is773()) {\n            if (isServer773()) {\n                // 内部773＋773クライアントはネイティブのためここには来ない。\n                // 念のためそのまま通す。\n                return Outcome.toClient(packet);\n            }\n            if (isServer774()) {\n                return handleServerConfiguration773From774(packet);\n            }\n            if (isServer772()) {\n                return handleServerConfiguration773From772(packet);\n            }\n            if (isServer771()) {\n                return handleServerConfiguration773From771(packet);\n            }\n            if (isServer770()) {\n                return handleServerConfiguration773From770(packet);\n            }\n            if (isServer769()) {\n                return handleServerConfiguration773From769(packet);\n            }\n            if (isServer768()) {\n                return handleServerConfiguration773From768(packet);\n            }\n            if (isServer767()) {\n                return handleServerConfiguration773From767(packet);\n            }\n            if (isServer766()) {\n                return handleServerConfiguration773From766(packet);\n            }\n            return handleServerConfiguration773(packet, isServer764());\n        }\n'), 'handleServerConfiguration 775 branch isServer775': ('            if (isServer771()) {\n                return handleServerConfiguration772From771(packet);\n            }\n            if (isServer770()) {\n                return handleServerConfiguration772From770(packet);\n            }\n            if (isServer775()) {\n                return handleServerConfiguration772From775(packet);\n            }\n            if (isServer774()) {\n                return handleServerConfiguration772From774(packet);\n            }\n            if (isServer773()) {\n                return handleServerConfiguration772From773(packet);\n            }\n            if (isServer769()) {\n                return handleServerConfiguration772From769(packet);\n            }', '            if (isServer771()) {\n                return handleServerConfiguration772From771(packet);\n            }\n            if (isServer770()) {\n                return handleServerConfiguration772From770(packet);\n            }\n            if (isServer776()) {\n                return handleServerConfiguration772From776(packet);\n            }\n            if (isServer775()) {\n                return handleServerConfiguration772From775(packet);\n            }\n            if (isServer774()) {\n                return handleServerConfiguration772From774(packet);\n            }\n            if (isServer773()) {\n                return handleServerConfiguration772From773(packet);\n            }\n            if (isServer769()) {\n                return handleServerConfiguration772From769(packet);\n            }'), 'handleClientConfiguration 775-server guard': ('        // 771サーバーへは Custom Report Details（0x08）／Server Links\n        // （0x09）を送らない（771で削除された）。771クライアントの\n        // 0x08 は Custom Click Action のためこの限りではない。772クライアントの\n        // 0x08 も Custom Click Action のためこの限りではない。774クライアントの\n        // 0x08 も Custom Click Action のためこの限りではない。775クライアントの\n        // 0x08 も Custom Click Action のためこの限りではない。\n        if (isServer771() && !is771() && !is772() && !is773() && !is774() && !is775()\n                && (packet.id() == 0x08 || packet.id() == 0x09)) {\n            return Outcome.drop();\n        }\n        // 772サーバーへは Custom Report Details（0x08）／Server Links\n        // （0x09）を送らない（771で削除された。772も同じ）。772クライアントの\n        // 0x08 は Custom Click Action のためこの限りではない。771クライアントの\n        // 0x08 も Custom Click Action のためこの限りではない。774クライアントの\n        // 0x08 も Custom Click Action のためこの限りではない。775クライアントの\n        // 0x08 も Custom Click Action のためこの限りではない。\n        if (isServer772() && !is772() && !is771() && !is773() && !is774() && !is775()\n                && (packet.id() == 0x08 || packet.id() == 0x09)) {\n            return Outcome.drop();\n        }\n        // 774サーバーへは Custom Report Details（0x08）／Server Links\n        // （0x09）を送らない（771で削除された。774も同じ）。774クライアントの\n        // 0x08 は Custom Click Action のためこの限りではない。772クライアントの\n        // 0x08 も Custom Click Action のためこの限りではない。771クライアントの\n        // 0x08 も Custom Click Action のためこの限りではない。775クライアントの\n        // 0x08 も Custom Click Action のためこの限りではない。\n        if (isServer774() && !is774() && !is773() && !is772() && !is771() && !is775()\n                && (packet.id() == 0x08 || packet.id() == 0x09)) {\n            return Outcome.drop();\n        }\n        // 775サーバーへは Custom Report Details（0x08）／Server Links\n        // （0x09）を送らない（771で削除された。775も同じ）。775クライアントの\n        // 0x08 は Custom Click Action のためこの限りではない。774クライアントの\n        // 0x08 も Custom Click Action のためこの限りではない。772クライアントの\n        // 0x08 も Custom Click Action のためこの限りではない。771クライアントの\n        // 0x08 も Custom Click Action のためこの限りではない。\n        if (isServer775() && !is775() && !is774() && !is773() && !is772() && !is771()\n                && (packet.id() == 0x08 || packet.id() == 0x09)) {\n            return Outcome.drop();\n        }\n', '        // 771サーバーへは Custom Report Details（0x08）／Server Links\n        // （0x09）を送らない（771で削除された。771も同じ）。771クライアントの\n        // 0x08 は Custom Click Action のためこの限りではない。772クライアントの\n        // 0x08 も Custom Click Action のためこの限りではない。773クライアントの\n        // 0x08 も Custom Click Action のためこの限りではない。774クライアントの\n        // 0x08 も Custom Click Action のためこの限りではない。775クライアントの\n        // 0x08 も Custom Click Action のためこの限りではない。776クライアントの\n        // 0x08 も Custom Click Action のためこの限りではない。\n        if (isServer771() && !is771() && !is772() && !is773() && !is774() && !is775() && !is776()\n                && (packet.id() == 0x08 || packet.id() == 0x09)) {\n            return Outcome.drop();\n        }\n        // 772サーバーへは Custom Report Details（0x08）／Server Links\n        // （0x09）を送らない（771で削除された。772も同じ）。772クライアントの\n        // 0x08 は Custom Click Action のためこの限りではない。771クライアントの\n        // 0x08 も Custom Click Action のためこの限りではない。773クライアントの\n        // 0x08 も Custom Click Action のためこの限りではない。774クライアントの\n        // 0x08 も Custom Click Action のためこの限りではない。775クライアントの\n        // 0x08 も Custom Click Action のためこの限りではない。776クライアントの\n        // 0x08 も Custom Click Action のためこの限りではない。\n        if (isServer772() && !is772() && !is771() && !is773() && !is774() && !is775() && !is776()\n                && (packet.id() == 0x08 || packet.id() == 0x09)) {\n            return Outcome.drop();\n        }\n        // 774サーバーへは Custom Report Details（0x08）／Server Links\n        // （0x09）を送らない（771で削除された。774も同じ）。774クライアントの\n        // 0x08 は Custom Click Action のためこの限りではない。773クライアントの\n        // 0x08 も Custom Click Action のためこの限りではない。772クライアントの\n        // 0x08 も Custom Click Action のためこの限りではない。771クライアントの\n        // 0x08 も Custom Click Action のためこの限りではない。775クライアントの\n        // 0x08 も Custom Click Action のためこの限りではない。776クライアントの\n        // 0x08 も Custom Click Action のためこの限りではない。\n        if (isServer774() && !is774() && !is773() && !is772() && !is771() && !is775() && !is776()\n                && (packet.id() == 0x08 || packet.id() == 0x09)) {\n            return Outcome.drop();\n        }\n        // 775サーバーへは Custom Report Details（0x08）／Server Links\n        // （0x09）を送らない（771で削除された。775も同じ）。775クライアントの\n        // 0x08 は Custom Click Action のためこの限りではない。774クライアントの\n        // 0x08 も Custom Click Action のためこの限りではない。773クライアントの\n        // 0x08 も Custom Click Action のためこの限りではない。772クライアントの\n        // 0x08 も Custom Click Action のためこの限りではない。771クライアントの\n        // 0x08 も Custom Click Action のためこの限りではない。776クライアントの\n        // 0x08 も Custom Click Action のためこの限りではない。\n        if (isServer775() && !is775() && !is774() && !is773() && !is772() && !is771() && !is776()\n                && (packet.id() == 0x08 || packet.id() == 0x09)) {\n            return Outcome.drop();\n        }\n        // 776サーバーへは Custom Report Details（0x08）／Server Links\n        // （0x09）を送らない（771で削除された。776も同じ）。776クライアントの\n        // 0x08 は Custom Click Action のためこの限りではない。775クライアントの\n        // 0x08 も Custom Click Action のためこの限りではない。774クライアントの\n        // 0x08 も Custom Click Action のためこの限りではない。773クライアントの\n        // 0x08 も Custom Click Action のためこの限りではない。772クライアントの\n        // 0x08 も Custom Click Action のためこの限りではない。771クライアントの\n        // 0x08 も Custom Click Action のためこの限りではない。\n        if (isServer776() && !is776() && !is775() && !is774() && !is773() && !is772() && !is771()\n                && (packet.id() == 0x08 || packet.id() == 0x09)) {\n            return Outcome.drop();\n        }\n'), 'chatGlobalIndex field': ('    /**\n     * 773クライアントに送るリソースパック（pack_format 69）。\n     * 内部が773でない場合に使う差し替え用。内部が773の場合はバニラが\n     * 送ってきたパックをそのまま出すため null のままでよい。\n     * 出し分けの基準はクライアントのプロトコルである\n     * （{@link #packOverrideForClient()} 参照）。\n     */\n    private ResourcePack pack773;\n\n    /**\n     * 774クライアントに送るリソースパック（pack_format 75）。\n     * 内部が774でない場合に使う差し替え用。内部が774の場合はバニラが\n     * 送ってきたパックをそのまま出すため null のままでよい。\n     * 出し分けの基準はクライアントのプロトコルである\n     * （{@link #packOverrideForClient()} 参照）。\n     */\n    private ResourcePack pack774;\n\n    /**\n     * 775クライアントに送るリソースパック（pack_format 84）。\n     * 内部が775でない場合に使う差し替え用。内部が775の場合はバニラが\n     * 送ってきたパックをそのまま出すため null のままでよい。\n     * 出し分けの基準はクライアントのプロトコルである\n     * （{@link #packOverrideForClient()} 参照）。\n     */\n    private ResourcePack pack775;\n\n    /**\n     * 776クライアントに送るリソースパック（pack_format 88）。\n     * 内部が776でない場合に使う差し替え用。内部が776の場合はバニラが\n     * 送ってきたパックをそのまま出すため null のままでよい。\n     * 出し分けの基準はクライアントのプロトコルである\n     * （{@link #packOverrideForClient()} 参照）。\n     */\n    private ResourcePack pack776;\n\n    /**\n     * 770／771／772／773／774／775クライアントへ送る player_chat に付ける globalIndex。\n     * 769サーバーは globalIndex を送ってこないため、プロキシが\n     * セッションごとに 0 から数える。\n     */\n    private int chatGlobalIndex = 0;', '    /**\n     * 773クライアントに送るリソースパック（pack_format 69）。\n     * 内部が773でない場合に使う差し替え用。内部が773の場合はバニラが\n     * 送ってきたパックをそのまま出すため null のままでよい。\n     * 出し分けの基準はクライアントのプロトコルである\n     * （{@link #packOverrideForClient()} 参照）。\n     */\n    private ResourcePack pack773;\n\n    /**\n     * 774クライアントに送るリソースパック（pack_format 75）。\n     * 内部が774でない場合に使う差し替え用。内部が774の場合はバニラが\n     * 送ってきたパックをそのまま出すため null のままでよい。\n     * 出し分けの基準はクライアントのプロトコルである\n     * （{@link #packOverrideForClient()} 参照）。\n     */\n    private ResourcePack pack774;\n\n    /**\n     * 775クライアントに送るリソースパック（pack_format 84）。\n     * 内部が775でない場合に使う差し替え用。内部が775の場合はバニラが\n     * 送ってきたパックをそのまま出すため null のままでよい。\n     * 出し分けの基準はクライアントのプロトコルである\n     * （{@link #packOverrideForClient()} 参照）。\n     */\n    private ResourcePack pack775;\n\n    /**\n     * 776クライアントに送るリソースパック（pack_format 88）。\n     * 内部が776でない場合に使う差し替え用。内部が776の場合はバニラが\n     * 送ってきたパックをそのまま出すため null のままでよい。\n     * 出し分けの基準はクライアントのプロトコルである\n     * （{@link #packOverrideForClient()} 参照）。\n     */\n    private ResourcePack pack776;\n\n    /**\n     * 770／771／772／773／774／775／776クライアントへ送る player_chat に付ける globalIndex。\n     * 769サーバーは globalIndex を送ってこないため、プロキシが\n     * セッションごとに 0 から数える。\n     */\n    private int chatGlobalIndex = 0;')}


# 775パッチから外す編集。774ステージで既に live へ適用済みの修正で、
# shift すると存在しないメソッド（translatePlayerInfo774To768 等）を
# 呼ぶ壊れた編集になってしまうため。
SKIP_EDITS = {'handleServerPlayFrom775 775 branch (add)', 'handleServerJoinGame775 775 branch'}


ADD_EDITS = [('handleServerPlayFrom774 776 branch', '        // 773クライアント：774→773へ変換する。\n        if (is773()) {\n            return handleServerPlay774To773(packet);', '        // 776クライアント：774→775→776へ二段で変換する。\n        if (is776()) {\n            return handleServerPlay774To776(packet);\n        }\n        // 773クライアント：774→773へ変換する。\n        if (is773()) {\n            return handleServerPlay774To773(packet);'), ('handleServerPlayFrom775 776 branch', '        // 774クライアント：775→774へ変換する。\n        if (is774()) {\n            return handleServerPlay775To774(packet);', '        // 776クライアント：775→776へ変換する。\n        if (is776()) {\n            return handleServerPlay775To776(packet);\n        }\n        // 774クライアント：775→774へ変換する。\n        if (is774()) {\n            return handleServerPlay775To774(packet);'), ('handleServerJoinGame774 776 branch', '    private Outcome handleServerJoinGame774(MinecraftPacket packet) {\n        if (is775()) {', '    private Outcome handleServerJoinGame774(MinecraftPacket packet) {\n        if (is776()) {\n            advanceFlowSafely(() -> joinFlow.joinGameReceived());\n            List<MinecraftPacket> toClient = new ArrayList<>();\n            toClient.add(new MinecraftPacket(0x31, packet.payload()));\n            while (!pendingPacks.isEmpty()) {\n                PendingPack pack = pendingPacks.removeFirst();\n                lastSentPack = pack;\n                toClient.add(new MinecraftPacket(0x51,\n                        PlayTranslator.translateConfigAddResourcePack764To765(\n                                pack, packOverrideForClient())));\n            }\n            return new Outcome(List.of(), toClient, false, false);\n        }\n        if (is775()) {'), ('handleServerJoinGame775 776 branch', '    private Outcome handleServerJoinGame775(MinecraftPacket packet) {\n        if (is774()) {', '    private Outcome handleServerJoinGame775(MinecraftPacket packet) {\n        if (is776()) {\n            advanceFlowSafely(() -> joinFlow.joinGameReceived());\n            List<MinecraftPacket> toClient = new ArrayList<>();\n            toClient.add(new MinecraftPacket(0x31, packet.payload()));\n            while (!pendingPacks.isEmpty()) {\n                PendingPack pack = pendingPacks.removeFirst();\n                lastSentPack = pack;\n                toClient.add(new MinecraftPacket(0x51,\n                        PlayTranslator.translateConfigAddResourcePack764To765(\n                                pack, packOverrideForClient())));\n            }\n            return new Outcome(List.of(), toClient, false, false);\n        }\n        if (is774()) {')]


# CONFIG_775 に追加する、旧クライアント＋内部775の設定フェーズ変換器。
# shift では作れない（774ステージにも同型の手書きメソッドがある）。



# FIX_BLOCKS：775版の修正は shift 後のブロックに既に含まれているため、
# 776では段数・連鎖の潰れ（772段の欠落）だけを直す。キーは読む側の版名
#（CLIENT_PLAY_776 等）のまま。
FIX_BLOCKS = {'CLIENT_PLAY_776': [('                                    ChunkTranslator.translateSetCreativeSlot772To774(\n                                            ChunkTranslator.translateSetCreativeSlot771To772(', '                                    ChunkTranslator.translateSetCreativeSlot773To774(\n                                            ChunkTranslator.translateSetCreativeSlot772To773(\n                                            ChunkTranslator.translateSetCreativeSlot771To772(')], 'SERVER_PLAY_776': [('        if (is775()) {\n            advanceFlowSafely(() -> joinFlow.joinGameReceived());\n            List<MinecraftPacket> toClient = new ArrayList<>();\n            toClient.add(new MinecraftPacket(0x30, packet.payload()));\n            while (!pendingPacks.isEmpty()) {\n                PendingPack pack = pendingPacks.removeFirst();\n                lastSentPack = pack;\n                toClient.add(new MinecraftPacket(0x4F,\n                        PlayTranslator.translateConfigAddResourcePack764To765(\n                                pack, packOverrideForClient())));\n            }\n            return new Outcome(List.of(), toClient, false, false);\n        }\n', '        if (is775()) {\n            advanceFlowSafely(() -> joinFlow.joinGameReceived());\n            List<MinecraftPacket> toClient = new ArrayList<>();\n            toClient.add(new MinecraftPacket(0x31, packet.payload()));\n            while (!pendingPacks.isEmpty()) {\n                PendingPack pack = pendingPacks.removeFirst();\n                lastSentPack = pack;\n                toClient.add(new MinecraftPacket(0x51,\n                        PlayTranslator.translateConfigAddResourcePack764To765(\n                                pack, packOverrideForClient())));\n            }\n            return new Outcome(List.of(), toClient, false, false);\n        }\n'), ('        // Respawn（776：0x52。775（0x50）と同形式のためそのまま送る）。', '        // Respawn（776：0x52。775（0x52）と同形式のためそのまま送る）。'), ('            if (is775()) {\n                // 775クライアント：Respawn は 0x50（同形式）のまま。\n                return Outcome.toClient(new MinecraftPacket(0x50, packet.payload()));', '            if (is775()) {\n                // 775クライアント：Respawn は 0x52（同形式）のまま。\n                return Outcome.toClient(new MinecraftPacket(0x52, packet.payload()));'), ('            if (is774()) {\n                // 774クライアント：Respawn は 0x50（同形式）のまま。\n                return Outcome.toClient(new MinecraftPacket(0x50, packet.payload()));\n            }\n            if (is770() || is771() || is772()) {', '            if (is774()) {\n                // 774クライアント：Respawn は 0x50（同形式）のまま。\n                return Outcome.toClient(new MinecraftPacket(0x50, packet.payload()));\n            }\n            if (is773()) {\n                // 773クライアント：Respawn は 0x50（同形式）のまま。\n                return Outcome.toClient(new MinecraftPacket(0x50, packet.payload()));\n            }\n            if (is770() || is771() || is772()) {'), ('767クライアント：776→775→774→773→772→771→770→769→768へ変換してから770→769へ。', '767クライアント：776→775→774→773→772→771→770→769→768へ変換してから769→768へ。'), ('766クライアント：776→775→774→773→772→771→770→769→768へ変換してから769→767へ。', '766クライアント：776→775→774→773→772→771→770→769→768へ変換してから768→766へ。')]}


FIX_BLOCKS_ALL = {'CONFIG_776': [('                byte[] payload772to774 =\n                        PlayTranslator.translateRegistryData772To774(payload771to772);\n                byte[] payload774to775 =\n                        PlayTranslator.translateRegistryData774To775(payload772to774);', '                byte[] payload772to773 =\n                        PlayTranslator.translateRegistryData772To773(payload771to772);\n                byte[] payload773to774 =\n                        PlayTranslator.translateRegistryData773To774(payload772to773);\n                byte[] payload774to775 =\n                        PlayTranslator.translateRegistryData774To775(payload773to774);', 4), ('                    byte[] payload772to774 =\n                            PlayTranslator.translateRegistryData772To774(payload771to772);\n                    byte[] payload774to775 =\n                            PlayTranslator.translateRegistryData774To775(payload772to774);', '                    byte[] payload772to773 =\n                            PlayTranslator.translateRegistryData772To773(payload771to772);\n                    byte[] payload773to774 =\n                            PlayTranslator.translateRegistryData773To774(payload772to773);\n                    byte[] payload774to775 =\n                            PlayTranslator.translateRegistryData774To775(payload773to774);', 1), ('                byte[] payload774to772 =\n                        PlayTranslator.translateRegistryData774To772(payload775to774);\n                byte[] payload772to771 =\n                        PlayTranslator.translateRegistryData772To771(payload774to772);', '                byte[] payload774to773 =\n                        PlayTranslator.translateRegistryData774To773(payload775to774);\n                byte[] payload773to772 =\n                        PlayTranslator.translateRegistryData773To772(payload774to773);\n                byte[] payload772to771 =\n                        PlayTranslator.translateRegistryData772To771(payload773to772);', 1), ('                                PlayTranslator.translateRegistryData774To775(\n                                        PlayTranslator.translateRegistryData772To774(\n                                                PlayTranslator.translateRegistryData771To772(\n                                                        PlayTranslator.translateRegistryData770To771(\n                                                                PlayTranslator.translateRegistryData769To770(\n                                                                        PlayTranslator.translateRegistryData768To769(packet.payload())))))))));', '                                PlayTranslator.translateRegistryData774To775(\n                                        PlayTranslator.translateRegistryData773To774(\n                                                PlayTranslator.translateRegistryData772To773(\n                                                        PlayTranslator.translateRegistryData771To772(\n                                                                PlayTranslator.translateRegistryData770To771(\n                                                                        PlayTranslator.translateRegistryData769To770(\n                                                                                PlayTranslator.translateRegistryData768To769(packet.payload()))))))))));', 1), ('                byte[] payload774to772 =\n                        PlayTranslator.translateRegistryData774To772(payload775to774);\n                if (payload774to772 == null) {\n                    yield Outcome.drop();\n                }\n                byte[] payload772to771 =', '                byte[] payload774to773 =\n                        PlayTranslator.translateRegistryData774To773(payload775to774);\n                if (payload774to773 == null) {\n                    yield Outcome.drop();\n                }\n                byte[] payload773to772 =\n                        PlayTranslator.translateRegistryData773To772(payload774to773);\n                if (payload773to772 == null) {\n                    yield Outcome.drop();\n                }\n                byte[] payload772to771 =', 4)]}


SERVER_PLAY_776_TO = r'''
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
'''


def main():
    src = SRC.read_text(encoding="utf-8")
    tree = ast.parse(src)
    assigns = {n.targets[0].id: n.value for n in tree.body
               if isinstance(n, ast.Assign) and len(n.targets) == 1
               and isinstance(n.targets[0], ast.Name)}
    edits = ast.literal_eval(assigns["EDITS"])
    blocks = {}
    for name in ("CLIENT_PLAY_775", "SERVER_PLAY_775", "CONFIG_775"):
        blocks[name] = ast.literal_eval(assigns[name])

    new_edits = []
    for name, old, new in edits:
        if new.startswith(old) and new != old:
            kind = "APPEND"
            added = new[len(old):]
            new_edits.append((name.replace("774", "775"), new, new + shift(added)))
        elif old in new and new != old:
            kind = "CONTAINS-OLD"
            assert new.endswith(old), f"{name}: CONTAINS-OLD but not suffix form"
            prefix = new[: len(new) - len(old)]
            new_edits.append((name.replace("774", "775"), new, shift(prefix) + new))
        else:
            kind = "REPLACE"
            new_edits.append((name.replace("774", "775"), new, shift(new)))
        print(f"{kind}: {name} -> {new_edits[-1][0]}", file=sys.stderr)

    # ---- 編集名の重複を解消する（776で初出）。shift 後の編集名が衝突する
    # 場合がある（例：handleServerPlayFrom774 774 branch と handleServerPlayFrom774 775 branch
    # がどちらも handleServerPlayFrom775 775 branch になる）。後から来た方に
    # " (add)" を付けて区別し、SKIP_EDITS で外す側を指定する。
    _seen_names = {}
    _deduped = []
    for _name, _old, _new in new_edits:
        if _name in _seen_names:
            _name = _name + " (add)"
            print(f"DEDUP: {_name}（重複のため (add) を付けた）", file=sys.stderr)
        _seen_names[_name] = True
        _deduped.append((_name, _old, _new))
    new_edits = _deduped

    # ---- 手直し（FIX_EDITS_FULL / FIX_EDITS / SKIP_EDITS / ADD_EDITS / FIX_BLOCKS）----
    def apply_subs(text, subs, where):
        for old_sub, new_sub in subs:
            n = text.count(old_sub)
            if n != 1:
                print(f"FIX MISMATCH in {where}: count={n} for {old_sub[:80]!r}",
                      file=sys.stderr)
                sys.exit(1)
            text = text.replace(old_sub, new_sub)
        return text

    def apply_replace_all(text, subs, where):
        for old_sub, new_sub, expected in subs:
            n = text.count(old_sub)
            if n != expected:
                print(f"FIX_ALL MISMATCH in {where}: count={n} (expected {expected}) for {old_sub[:80]!r}",
                      file=sys.stderr)
                sys.exit(1)
            text = text.replace(old_sub, new_sub)
        return text

    by_name = {}
    fixed_edits = []
    for name, old, new in new_edits:
        if name in SKIP_EDITS:
            print(f"SKIP: {name}（774で適用済みのため775パッチから外す）", file=sys.stderr)
            continue
        if name in FIX_EDITS_FULL:
            old, new = FIX_EDITS_FULL[name]
        elif name in FIX_EDITS:
            new = apply_subs(new, FIX_EDITS[name], f"edit {name}")
        fixed_edits.append((name, old, new))
        by_name[name] = (old, new)
    new_edits = fixed_edits
    for name, old, new in ADD_EDITS:
        if name in by_name:
            print(f"ADD_EDITS duplicate name: {name}", file=sys.stderr)
            sys.exit(1)
        new_edits.append((name, old, new))
        by_name[name] = (old, new)

    # ブロックの手直し（shifted 版に適用する。厳密に1回一致すること）。
    # 774ステージと違い、775ではメソッド名の衝突は無い（shift 後の
    # to775ClientPacket／to774ClientPacketFrom775／to774ServerPacketFrom775
    # はすべて新規名のため、改名の手直しは要らない）。
    new_blocks = {}
    for name in ("CLIENT_PLAY_775", "SERVER_PLAY_775", "CONFIG_775"):
        shifted = shift(blocks[name])
        new_name = name.replace("775", "776")
        if new_name in FIX_BLOCKS:
            shifted = apply_subs(shifted, FIX_BLOCKS[new_name], f"block {new_name}")
        if new_name in FIX_BLOCKS_ALL:
            shifted = apply_replace_all(shifted, FIX_BLOCKS_ALL[new_name], f"block {new_name}")
        new_blocks[new_name] = shifted

    # ヘッダ（手書き）と EDITS を出力し、ブロックは後段の手作業用に
    # shifted 版をそのまま載せる。
    out_lines = []
    out_lines.append('''#!/usr/bin/env python3
"""ProxySession.java へ Minecraft 26.2（プロトコル776）の対応を入れる。

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
"""
import sys
from pathlib import Path

PATH = Path(__file__).resolve().parent.parent / "src" / "main" / "java" \\
    / "io" / "detergentmc" / "protocol" / "ProxySession.java"


EDITS = [
''')
    for name, old, new in new_edits:
        out_lines.append(f"    ({name!r},")
        out_lines.append(f"     {old!r},")
        out_lines.append(f"     {new!r}),")
    out_lines.append("]")
    out_lines.append("")
    for name in ("CLIENT_PLAY_776", "SERVER_PLAY_776", "CONFIG_776"):
        out_lines.append(f"{name} = r'''{new_blocks[name]}'''")
        out_lines.append("")
    out_lines.append(f"SERVER_PLAY_776_TO = r'''{SERVER_PLAY_776_TO}'''")
    out_lines.append("")
    # INSERTS: 773版の INSERTS を流用（メソッド名だけずらす）。
    # 773版と違い、SERVER_PLAY_774_TO の挿入は shift 済み INSERTS に
    # 既に含まれているため、extra_insert は追加しない。
    inserts_start = src.index("INSERTS = [")
    inserts_end = src.index("\n]\n", inserts_start) + len("\n]\n")
    inserts_shifted = shift(src[inserts_start:inserts_end])
    out_lines.append(inserts_shifted)
    out_lines.append("")
    # INSERTS の後ろ（CONFIG_ANCHOR／CONFIG_REPLACEMENT／apply／
    # insert_before_method）もずらして載せる。
    rest_src = src[inserts_end:src.index("def main(")]
    rest_shifted = shift(rest_src)
    # CONFIG_ANCHOR：774パッチの rest は既に置換済みの anchor（default -> drop）
    # で、shift が警告文の 773→774 と CONFIG_774→CONFIG_775 を済ませている
    # ため、ここでの手直しは要らない。
    out_lines.append(rest_shifted)
    out_lines.append("")
    out_lines.append("")
    main_src = src[src.index("def main("):]
    main_src = shift(main_src)
    out_lines.append(main_src)
    OUT.write_text("\n".join(out_lines), encoding="utf-8")
    print(f"wrote {OUT}", file=sys.stderr)
    if warnings:
        print("WARNINGS:", file=sys.stderr)
        for w in warnings:
            print(f"  {w}", file=sys.stderr)


if __name__ == "__main__":
    main()
