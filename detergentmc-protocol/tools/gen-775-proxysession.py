#!/usr/bin/env python3
"""patch-proxysession-774.py から patch-proxysession-775.py を生成する。

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

SRC = Path("/home/hatch/workspace/detergentmc/detergentmc-protocol/tools/patch-proxysession-774.py")
OUT = Path("/home/hatch/workspace/detergentmc/detergentmc-protocol/tools/patch-proxysession-775.py")

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
IS_CHAIN = re.compile(r"((?:is(?:Server)?\d{3}\(\)\s*\|\|\s*)+is(?:Server)?774\(\))")
IS_CHAIN_770 = re.compile(r"((?:is(?:Server)?\d{3}\(\)\s*\|\|\s*)+is(?:Server)?773\(\))")


def shift_chain(m, kind):
    sep = "→" if kind == "arrow" else "⇔"
    nums = m.group(0).split(sep)
    if len(nums) == 2:
        # 2要素のペアは1段ずらす（773→774 は 774→775 になる）。
        return sep.join(str(int(n) + 1) for n in nums)
    if len(nums) >= 3 and nums[0] == "774":
        # 774から始まる多段チェーンは段が1つ増える（774→773→772 は
        # 775→774→773→772 になる）。末端（765/766/767）は固定。
        return "775" + sep + m.group(0)
    if nums[-1] == "774":
        return m.group(0) + sep + "775"
    warnings.append(f"arrow chain {m.group(0)} -> +1 each")
    return sep.join(str(int(n) + 1) for n in nums)


def shift_list(m, sep):
    nums = m.group(0).split(sep)
    if nums[-1] == "774":
        return m.group(0) + sep + "775"
    if nums[-1] == "773":
        # 新バージョンの一つ下まで列挙しているリストは 774 を追加する。
        return m.group(0) + sep + "774"
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
    # 1b) isチェーン（is764() || ... || is774()）に is775() を追加し、
    # そのまま保護する（後の数字シフトで二重にずれないように）。
    def is_chain_sub(m):
        chain = m.group(1)
        last = re.search(r"is(Server)?774\(\)$", chain)
        suffix = "isServer775()" if last.group(1) else "is775()"
        key = f"\x02{_counter[0]}\x03"
        _counter[0] += 1
        is_chain_holders[key] = chain + " || " + suffix
        return key

    is_chain_holders = {}
    text = IS_CHAIN.sub(is_chain_sub, text)

    def is_chain_sub_770(m):
        chain = m.group(1)
        last = re.search(r"is(Server)?773\(\)$", chain)
        suffix = "isServer774()" if last.group(1) else "is774()"
        key = f"\x02{_counter[0]}\x03"
        _counter[0] += 1
        is_chain_holders[key] = chain + " || " + suffix
        return key

    text = IS_CHAIN_770.sub(is_chain_sub_770, text)
    holders = {**h0, **h1, **h2, **h3, **h4, **is_chain_holders}
    # 2) バージョン文字列
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
    text = text.replace("pack769", "pack770")
    # pack769/pack770/pack771 は数字シフトの対象外にする（pack769→pack770 と
    # 数字シフトが二重にかかると pack770→pack771 のような誤りになる）。
    def pack_sub(m):
        key = f"\x02{_counter[0]}\x03"
        _counter[0] += 1
        pack_holders[key] = m.group(0)
        return key

    pack_holders = {}
    text = re.sub(r"pack(?:770|771|772|773|774|775)", pack_sub, text)
    holders.update(pack_holders)
    # PROTOCOL_77x も数字シフトから保護する（バージョン置き換えで既に
    # ずらし済みのため。二重にかかると PROTOCOL_770→772 になる）。
    text = re.sub(r"PROTOCOL_77[0-9]", pack_sub, text)
    holders.update(pack_holders)
    # 3) 単独の数字。\b は日本語文字を \w と見なすため使えないので、
    # 数字の前後を見る lookaround を使う（識別子内の 769 も対象にする）。
    # 16進リテラル (0x..）は数字続きなので対象外になる。
    text = re.sub(r"(?<!\d)774(?!\d)", "\x01774", text)
    text = re.sub(r"(?<!\d)773(?!\d)", "774", text)
    text = text.replace("\x01774", "775")
    # 4) 復元
    return restore(text, holders)


FIX_EDITS = {
    "pack774 field": [
        # 775の pack_format は 84（774の 75 のままになっている）。
        ("775クライアントに送るリソースパック（pack_format 75）",
         "775クライアントに送るリソースパック（pack_format 84）"),
    ],
    "packOverrideForClient javadoc": [
        # shift は pack_format の数字をずらさない（775=84、774=75、773=69、772=64、771=63、770=55、769=46）。
        # pack の写像が版数の写像より1段多いため連鎖が1つ欠ける（773→pack773 が消える）。
        ("775→pack775\n     * （pack_format 75）、774→pack774\n"
         "     * （pack_format 69）、772→pack773\n"
         "     * （pack_format 64）、771→pack772\n"
         "     * （pack_format 63）、770→pack771\n"
         "     * （pack_format 55）、769→pack770\n"
         "     * （pack_format 46）、768→pack768",
         "775→pack775\n     * （pack_format 84）、774→pack774\n"
         "     * （pack_format 75）、773→pack773\n"
         "     * （pack_format 69）、772→pack772\n"
         "     * （pack_format 64）、771→pack771\n"
         "     * （pack_format 63）、770→pack770\n"
         "     * （pack_format 55）、769→pack769\n"
         "     * （pack_format 46）、768→pack768"),
    ],
    "packOverrideForClient branch": [
        # V26_1 の連鎖から pack769 が落ちている（shift の罠）。戻す。
        (": pack770 != null ? pack770\n                    : pack768 != null ? pack768",
         ": pack770 != null ? pack770\n                    : pack769 != null ? pack769\n                    : pack768 != null ? pack768"),
    ],
    "handleServerPlayFrom766 774 branch": [
        # 段数の数え間違い：766→775 は九段、767→775 は八段。
        ("// 内部766は 766→767→768→769→770→771→772→773→774→775 の八段、内部767は 767→768→769→770→771→772→773→774→775 の七段で送る。",
         "// 内部766は 766→767→768→769→770→771→772→773→774→775 の九段、内部767は 767→768→769→770→771→772→773→774→775 の八段で送る。"),
    ],
    "handleServerPlayFrom772 774 branch": [
        # コメントの始点が 773 になっている（772→775 が正しい）。
        ("// 775クライアント：773→775へ変換する。",
         "// 775クライアント：772→775へ変換する。"),
    ],
    "handleServerPlayFrom770 774 branch": [
        # 段数の数え間違い：770→775 は五段。
        ("// 775クライアント：770→771→772→773→774→775へ四段で変換する。",
         "// 775クライアント：770→771→772→773→774→775へ五段で変換する。"),
    ],
    "handleServerPlayFrom771 774 branch": [
        # 段数の数え間違い：771→775 は四段。
        ("// 775クライアント：771→772→773→774→775へ三段で変換する。",
         "// 775クライアント：771→772→773→774→775へ四段で変換する。"),
    ],
    "handleServerPlayFrom774 774 branch": [
        # From773（773サーバー）の is775 分岐：774→775 への直通は誤り。
        # 773のパケットを 774形式と誤読する（773→774 のペイロード変換が
        # 抜ける）。773→774→775 の二段にする（SERVER_PLAY_775_TO の
        # handleServerPlay773To775 を使う）。
        ("// 775クライアント：774→775へ変換する。\n        if (is775()) {\n            return handleServerPlay774To775(packet);",
         "// 775クライアント：773→774→775へ二段で変換する。\n        if (is775()) {\n            return handleServerPlay773To775(packet);"),
    ],
    "synthesized config registry loop": [
        # is774 分岐のコメント：先頭の 766 が落ちている（shift の罠）。
        # 766→774 は八段。
        ("// 774：767→768→769→770→771→772→773→774へ七段で並べ替える。",
         "// 774：766→767→768→769→770→771→772→773→774へ八段で並べ替える。"),
        # is775 分岐のコメント：766→775 は九段。
        ("// 775：766→767→768→769→770→771→772→773→774→775へ八段で並べ替える。",
         "// 775：766→767→768→769→770→771→772→773→774→775へ九段で並べ替える。"),
        # is774 分岐の連鎖：772→774 の潰れを 772→773→774 に展開する。
        ("                    byte[] payload771to772 =\n                            PlayTranslator.translateRegistryData771To772(payload770to771);\n                    registryPayload =\n                            PlayTranslator.translateRegistryData772To774(payload771to772);",
         "                    byte[] payload771to772 =\n                            PlayTranslator.translateRegistryData771To772(payload770to771);\n                    byte[] payload772to773 =\n                            PlayTranslator.translateRegistryData772To773(payload771to772);\n                    registryPayload =\n                            PlayTranslator.translateRegistryData773To774(payload772to773);"),
        # is775 分岐の連鎖：同上。
        ("                    byte[] payload772to774 =\n                            PlayTranslator.translateRegistryData772To774(payload771to772);\n                    registryPayload =\n                            PlayTranslator.translateRegistryData774To775(payload772to774);",
         "                    byte[] payload772to773 =\n                            PlayTranslator.translateRegistryData772To773(payload771to772);\n                    byte[] payload773to774 =\n                            PlayTranslator.translateRegistryData773To774(payload772to773);\n                    registryPayload =\n                            PlayTranslator.translateRegistryData774To775(payload773to774);"),
    ],
    "handleServerPlayFrom769 Respawn 774": [
        # is775 分岐の Respawn ID：775 は 0x52（shift は 774 の 0x50 のまま）。
        ("if (is775()) {\n                // 775クライアント：Respawn は 0x50（同形式）のまま。\n                return Outcome.toClient(new MinecraftPacket(0x50, packet.payload()));",
         "if (is775()) {\n                // 775クライアント：Respawn は 0x52（同形式）のまま。\n                return Outcome.toClient(new MinecraftPacket(0x52, packet.payload()));"),
    ],
    "handleServerPlayFrom768 Respawn 774": [
        ("if (is775()) {\n                // 775クライアント：Respawn は 0x50（同形式）のまま。\n                return Outcome.toClient(new MinecraftPacket(0x50, packet.payload()));",
         "if (is775()) {\n                // 775クライアント：Respawn は 0x52（同形式）のまま。\n                return Outcome.toClient(new MinecraftPacket(0x52, packet.payload()));"),
    ],
    "handleServerPlayFrom770 Respawn 774": [
        ("if (is775()) {\n                // 775クライアント：Respawn は 0x50（同形式）のまま。\n                return Outcome.toClient(new MinecraftPacket(0x50, packet.payload()));",
         "if (is775()) {\n                // 775クライアント：Respawn は 0x52（同形式）のまま。\n                return Outcome.toClient(new MinecraftPacket(0x52, packet.payload()));"),
    ],
    "handleServerPlayFrom772 Respawn 774": [
        ("if (is775()) {\n                // 775クライアント：Respawn は 0x50（同形式）のまま。\n                return Outcome.toClient(new MinecraftPacket(0x50, packet.payload()));",
         "if (is775()) {\n                // 775クライアント：Respawn は 0x52（同形式）のまま。\n                return Outcome.toClient(new MinecraftPacket(0x52, packet.payload()));"),
    ],
    "handleServerPlayFrom774 Respawn 774": [
        ("if (is775()) {\n                // 775クライアント：Respawn は 0x50（同形式）のまま。\n                return Outcome.toClient(new MinecraftPacket(0x50, packet.payload()));",
         "if (is775()) {\n                // 775クライアント：Respawn は 0x52（同形式）のまま。\n                return Outcome.toClient(new MinecraftPacket(0x52, packet.payload()));"),
    ],
    "handleServerJoinGame769 774 branch": [
        # is775 分岐の Join Game／Add Resource Pack ID：775 は 0x31／0x51。
        ("        if (is775()) {\n            advanceFlowSafely(() -> joinFlow.joinGameReceived());\n            List<MinecraftPacket> toClient = new ArrayList<>();\n            toClient.add(new MinecraftPacket(0x30, packet.payload()));\n            while (!pendingPacks.isEmpty()) {\n                PendingPack pack = pendingPacks.removeFirst();\n                lastSentPack = pack;\n                toClient.add(new MinecraftPacket(0x4F,\n                        PlayTranslator.translateConfigAddResourcePack764To765(\n                                pack, packOverrideForClient())));\n            }\n            return new Outcome(List.of(), toClient, false, false);\n        }",
         "        if (is775()) {\n            advanceFlowSafely(() -> joinFlow.joinGameReceived());\n            List<MinecraftPacket> toClient = new ArrayList<>();\n            toClient.add(new MinecraftPacket(0x31, packet.payload()));\n            while (!pendingPacks.isEmpty()) {\n                PendingPack pack = pendingPacks.removeFirst();\n                lastSentPack = pack;\n                toClient.add(new MinecraftPacket(0x51,\n                        PlayTranslator.translateConfigAddResourcePack764To765(\n                                pack, packOverrideForClient())));\n            }\n            return new Outcome(List.of(), toClient, false, false);\n        }"),
    ],
    "handleServerJoinGame770 774 branch": [
        ("        if (is775()) {\n            advanceFlowSafely(() -> joinFlow.joinGameReceived());\n            List<MinecraftPacket> toClient = new ArrayList<>();\n            toClient.add(new MinecraftPacket(0x30, packet.payload()));\n            while (!pendingPacks.isEmpty()) {\n                PendingPack pack = pendingPacks.removeFirst();\n                lastSentPack = pack;\n                toClient.add(new MinecraftPacket(0x4F,\n                        PlayTranslator.translateConfigAddResourcePack764To765(\n                                pack, packOverrideForClient())));\n            }\n            return new Outcome(List.of(), toClient, false, false);\n        }",
         "        if (is775()) {\n            advanceFlowSafely(() -> joinFlow.joinGameReceived());\n            List<MinecraftPacket> toClient = new ArrayList<>();\n            toClient.add(new MinecraftPacket(0x31, packet.payload()));\n            while (!pendingPacks.isEmpty()) {\n                PendingPack pack = pendingPacks.removeFirst();\n                lastSentPack = pack;\n                toClient.add(new MinecraftPacket(0x51,\n                        PlayTranslator.translateConfigAddResourcePack764To765(\n                                pack, packOverrideForClient())));\n            }\n            return new Outcome(List.of(), toClient, false, false);\n        }"),
    ],
    "handleServerJoinGame771 774 branch": [
        ("        if (is775()) {\n            advanceFlowSafely(() -> joinFlow.joinGameReceived());\n            List<MinecraftPacket> toClient = new ArrayList<>();\n            toClient.add(new MinecraftPacket(0x30, packet.payload()));\n            while (!pendingPacks.isEmpty()) {\n                PendingPack pack = pendingPacks.removeFirst();\n                lastSentPack = pack;\n                toClient.add(new MinecraftPacket(0x4F,\n                        PlayTranslator.translateConfigAddResourcePack764To765(\n                                pack, packOverrideForClient())));\n            }\n            return new Outcome(List.of(), toClient, false, false);\n        }",
         "        if (is775()) {\n            advanceFlowSafely(() -> joinFlow.joinGameReceived());\n            List<MinecraftPacket> toClient = new ArrayList<>();\n            toClient.add(new MinecraftPacket(0x31, packet.payload()));\n            while (!pendingPacks.isEmpty()) {\n                PendingPack pack = pendingPacks.removeFirst();\n                lastSentPack = pack;\n                toClient.add(new MinecraftPacket(0x51,\n                        PlayTranslator.translateConfigAddResourcePack764To765(\n                                pack, packOverrideForClient())));\n            }\n            return new Outcome(List.of(), toClient, false, false);\n        }"),
    ],
    "handleServerJoinGame772 774 branch": [
        ("        if (is775()) {\n            advanceFlowSafely(() -> joinFlow.joinGameReceived());\n            List<MinecraftPacket> toClient = new ArrayList<>();\n            toClient.add(new MinecraftPacket(0x30, packet.payload()));\n            while (!pendingPacks.isEmpty()) {\n                PendingPack pack = pendingPacks.removeFirst();\n                lastSentPack = pack;\n                toClient.add(new MinecraftPacket(0x4F,\n                        PlayTranslator.translateConfigAddResourcePack764To765(\n                                pack, packOverrideForClient())));\n            }\n            return new Outcome(List.of(), toClient, false, false);\n        }",
         "        if (is775()) {\n            advanceFlowSafely(() -> joinFlow.joinGameReceived());\n            List<MinecraftPacket> toClient = new ArrayList<>();\n            toClient.add(new MinecraftPacket(0x31, packet.payload()));\n            while (!pendingPacks.isEmpty()) {\n                PendingPack pack = pendingPacks.removeFirst();\n                lastSentPack = pack;\n                toClient.add(new MinecraftPacket(0x51,\n                        PlayTranslator.translateConfigAddResourcePack764To765(\n                                pack, packOverrideForClient())));\n            }\n            return new Outcome(List.of(), toClient, false, false);\n        }"),
    ],
    "handleServerJoinGame768 774 branch": [
        # 768 の is775 分岐はコメント付きで、Join Game／AddPack とも
        # 768 の ID（0x2B／0x4A）のままになっている。775 の 0x31／0x51 に直す。
        ("        if (is775()) {\n            // 775クライアント：Join Game は 0x30、Add Resource Pack は 0x4F。\n            advanceFlowSafely(() -> joinFlow.joinGameReceived());\n            List<MinecraftPacket> toClient = new ArrayList<>();\n            toClient.add(new MinecraftPacket(0x2B, packet.payload()));\n            while (!pendingPacks.isEmpty()) {\n                PendingPack pack = pendingPacks.removeFirst();\n                lastSentPack = pack;\n                toClient.add(new MinecraftPacket(0x4A,\n                        PlayTranslator.translateConfigAddResourcePack764To765(\n                                pack, packOverrideForClient())));\n            }\n            return new Outcome(List.of(), toClient, false, false);\n        }",
         "        if (is775()) {\n            // 775クライアント：Join Game は 0x31、Add Resource Pack は 0x51。\n            advanceFlowSafely(() -> joinFlow.joinGameReceived());\n            List<MinecraftPacket> toClient = new ArrayList<>();\n            toClient.add(new MinecraftPacket(0x31, packet.payload()));\n            while (!pendingPacks.isEmpty()) {\n                PendingPack pack = pendingPacks.removeFirst();\n                lastSentPack = pack;\n                toClient.add(new MinecraftPacket(0x51,\n                        PlayTranslator.translateConfigAddResourcePack764To765(\n                                pack, packOverrideForClient())));\n            }\n            return new Outcome(List.of(), toClient, false, false);\n        }"),
    ],
    "handleServerPlayFrom763 ResourcePack 774": [
        # Add Resource Pack の三項：775 は 0x51（shift は 774 の 0x4F のまま）。
        ("is773() || is774() || is775() ? 0x4F : is770() || is771() || is772() ? 0x4A : 0x46",
         "is775() ? 0x51 : is773() || is774() ? 0x4F : is770() || is771() || is772() ? 0x4A : 0x46"),
    ],
    "handleClientConfiguration To774 branch": [
        # 775 分岐のコメントは「774向けの処理を使い回す」と言いながら
        # handleClientConfigurationTo775（専用の処理）を呼ぶ。774 の
        # 分岐も同じ書き方だが、775 は新規の分岐なので言い方を直す。
        ("// 764／765クライアント＋内部775：番号を775向けに付け替える。774と\n        // 同じ番号・形式のため774向けの処理を使い回す。",
         "// 764／765クライアント＋内部775：番号を775向けに付け替える。774と\n        // 同じ番号・形式のため専用の処理を使う。"),
    ],
    "handleClientConfiguration 774-server guard": [
        # shift が !is773() を落とす（773→774 にずれて既存の !is774()
        # と重なり、片方が消える）。773 クライアントの Custom Click
        # Action 0x08 が落とされるため、4 ブロックとも復旧する。
        ("if (isServer771() && !is771() && !is772() && !is774() && !is775()",
         "if (isServer771() && !is771() && !is772() && !is773() && !is774() && !is775()"),
        ("if (isServer772() && !is772() && !is771() && !is774() && !is775()",
         "if (isServer772() && !is772() && !is771() && !is773() && !is774() && !is775()"),
        ("if (isServer774() && !is774() && !is772() && !is771() && !is775()",
         "if (isServer774() && !is774() && !is773() && !is772() && !is771() && !is775()"),
        ("if (isServer775() && !is775() && !is774() && !is772() && !is771()",
         "if (isServer775() && !is775() && !is774() && !is773() && !is772() && !is771()"),
    ],
}


# (old, new) を丸ごと置き換える編集。shift した (old, new) では
# アンカーが live に無かったり、意味が変わったりするもの。
FIX_EDITS_FULL = {
    # 「handleServerPlay dispatch」：775で Join Game／Spawn Position／
    # Start Configuration の ID が変わったため、775分岐を 775 の ID
    #（0x31／0x61／0x76）で前に足す。774分岐以下は live のまま。
    "handleServerPlay dispatch": (
        '        // 内部774：Join Game は 0x30、Spawn Position は 0x5F、\n        // Start Configuration は 0x74（773と同じ）。\n        if (isServer774()) {\n            return handleServerPlayFrom774(packet);\n        }\n        // 内部773：Join Game は 0x30、Spawn Position は 0x5F、\n        // Start Configuration は 0x74。\n        if (isServer773()) {\n            return handleServerPlayFrom773(packet);\n        }\n        // 内部772：Join Game は 0x2B、Spawn Position は 0x5A、\n        // Start Configuration は 0x6F（771と同じ）。\n        if (isServer772()) {\n            return handleServerPlayFrom772(packet);\n        }\n        // 内部771：Join Game は 0x2B、Spawn Position は 0x5A、\n        // Start Configuration は 0x6F（770と同じ）。\n        if (isServer771()) {\n            return handleServerPlayFrom771(packet);\n        }\n        // 内部770：Join Game は 0x2B、Spawn Position は 0x5A、\n        // Start Configuration は 0x6F（769から -1）。\n        if (isServer770()) {\n            return handleServerPlayFrom770(packet);\n        }\n        // 内部769：Join Game は 0x2C、Spawn Position は 0x5B、\n        // Start Configuration は 0x70（768と同じ番号）。\n        if (isServer769()) {\n            return handleServerPlayFrom769(packet);\n        }\n        // 内部768：Join Game は 0x2C、Spawn Position は 0x5B、\n        // Start Configuration は 0x70。\n        if (isServer768()) {\n            return handleServerPlayFrom768(packet);\n        }',
        '        // 内部775：Join Game は 0x31、Spawn Position は 0x61、\n        // Start Configuration は 0x76。\n        if (isServer775()) {\n            return handleServerPlayFrom775(packet);\n        }\n        // 内部774：Join Game は 0x30、Spawn Position は 0x5F、\n        // Start Configuration は 0x74（773と同じ）。\n        if (isServer774()) {\n            return handleServerPlayFrom774(packet);\n        }\n        // 内部773：Join Game は 0x30、Spawn Position は 0x5F、\n        // Start Configuration は 0x74。\n        if (isServer773()) {\n            return handleServerPlayFrom773(packet);\n        }\n        // 内部772：Join Game は 0x2B、Spawn Position は 0x5A、\n        // Start Configuration は 0x6F（771と同じ）。\n        if (isServer772()) {\n            return handleServerPlayFrom772(packet);\n        }\n        // 内部771：Join Game は 0x2B、Spawn Position は 0x5A、\n        // Start Configuration は 0x6F（770と同じ）。\n        if (isServer771()) {\n            return handleServerPlayFrom771(packet);\n        }\n        // 内部770：Join Game は 0x2B、Spawn Position は 0x5A、\n        // Start Configuration は 0x6F（769から -1）。\n        if (isServer770()) {\n            return handleServerPlayFrom770(packet);\n        }\n        // 内部769：Join Game は 0x2C、Spawn Position は 0x5B、\n        // Start Configuration は 0x70（768と同じ番号）。\n        if (isServer769()) {\n            return handleServerPlayFrom769(packet);\n        }\n        // 内部768：Join Game は 0x2C、Spawn Position は 0x5B、\n        // Start Configuration は 0x70。\n        if (isServer768()) {\n            return handleServerPlayFrom768(packet);\n        }'),
    # 「*ClientId」4件：775でパケットIDが変わったため、is775() の分岐に
    # 775 の ID を足す（Start Configuration 0x76、Spawn Position 0x61、
    # Keep Alive 0x2C、Ping 0x3D）。774 の ID はそのまま残す。
    "startConfigurationClientId": (
        "        if (is766() || is767() || is768() || is769() || is770() || is771() || is772() || is773() || is774()) {\n"
        "            return PacketTranslator.toClientId(PacketName.PLAY_START_CONFIGURATION,\n"
        "                    serverProtocol, clientProtocol).orElse(is768() || is769() ? 0x70 : is773() || is774() ? 0x74 : is770() || is771() || is772() ? 0x6F : 0x69);",
        "        if (is766() || is767() || is768() || is769() || is770() || is771() || is772() || is773() || is774() || is775()) {\n"
        "            return PacketTranslator.toClientId(PacketName.PLAY_START_CONFIGURATION,\n"
        "                    serverProtocol, clientProtocol).orElse(is768() || is769() ? 0x70 : is775() ? 0x76 : is773() || is774() ? 0x74 : is770() || is771() || is772() ? 0x6F : 0x69);"),
    "spawnPositionClientId": (
        "        if (is766() || is767() || is768() || is769() || is770() || is771() || is772() || is773() || is774()) {\n"
        "            return PacketTranslator.toClientId(PacketName.PLAY_SPAWN_POSITION,\n"
        "                    serverProtocol, clientProtocol).orElse(is768() || is769() ? 0x5B : is773() || is774() ? 0x5F : is770() || is771() || is772() ? 0x5A : 0x56);",
        "        if (is766() || is767() || is768() || is769() || is770() || is771() || is772() || is773() || is774() || is775()) {\n"
        "            return PacketTranslator.toClientId(PacketName.PLAY_SPAWN_POSITION,\n"
        "                    serverProtocol, clientProtocol).orElse(is768() || is769() ? 0x5B : is775() ? 0x61 : is773() || is774() ? 0x5F : is770() || is771() || is772() ? 0x5A : 0x56);"),
    "keepAliveClientId": (
        "        if (is766() || is767() || is768() || is769() || is770() || is771() || is772() || is773() || is774()) {\n"
        "            return PacketTranslator.toClientId(PacketName.PLAY_KEEP_ALIVE,\n"
        "                    serverProtocol, clientProtocol).orElse(is773() || is774() ? 0x2B : is768() || is769() ? 0x27 : 0x26);",
        "        if (is766() || is767() || is768() || is769() || is770() || is771() || is772() || is773() || is774() || is775()) {\n"
        "            return PacketTranslator.toClientId(PacketName.PLAY_KEEP_ALIVE,\n"
        "                    serverProtocol, clientProtocol).orElse(is775() ? 0x2C : is773() || is774() ? 0x2B : is768() || is769() ? 0x27 : 0x26);"),
    "pingClientId": (
        "        if (is766() || is767() || is768() || is769() || is770() || is771() || is772() || is773() || is774()) {\n"
        "            return PacketTranslator.toClientId(PacketName.PLAY_PING,\n"
        "                    serverProtocol, clientProtocol).orElse(is768() || is769() ? 0x37 : is773() || is774() ? 0x3B : is770() || is771() || is772() ? 0x36 : 0x35);",
        "        if (is766() || is767() || is768() || is769() || is770() || is771() || is772() || is773() || is774() || is775()) {\n"
        "            return PacketTranslator.toClientId(PacketName.PLAY_PING,\n"
        "                    serverProtocol, clientProtocol).orElse(is768() || is769() ? 0x37 : is775() ? 0x3D : is773() || is774() ? 0x3B : is770() || is771() || is772() ? 0x36 : 0x35);"),
    # 「handleServerConfiguration 763 branch」：live は is763Like() で、
    # shift した版（is763()）と合わない。775分岐を前に足すだけにする。
    "handleServerConfiguration 763 branch": (
        '        if (is763Like()) {\n            if (isServer774()) {\n                return handleServerConfiguration763From774(packet);\n            }\n            if (isServer773()) {\n                return handleServerConfiguration763From773(packet);\n            }\n            if (isServer772()) {\n                return handleServerConfiguration763From772(packet);\n            }\n            if (isServer771()) {\n                return handleServerConfiguration763From771(packet);\n            }\n            if (isServer770()) {\n                return handleServerConfiguration763From770(packet);\n            }\n            if (isServer769()) {\n                return handleServerConfiguration763From769(packet);\n            }\n            if (isServer768()) {\n                return handleServerConfiguration763From768(packet);\n            }',
        '        if (is763Like()) {\n            if (isServer775()) {\n                return handleServerConfiguration763From775(packet);\n            }\n            if (isServer774()) {\n                return handleServerConfiguration763From774(packet);\n            }\n            if (isServer773()) {\n                return handleServerConfiguration763From773(packet);\n            }\n            if (isServer772()) {\n                return handleServerConfiguration763From772(packet);\n            }\n            if (isServer771()) {\n                return handleServerConfiguration763From771(packet);\n            }\n            if (isServer770()) {\n                return handleServerConfiguration763From770(packet);\n            }\n            if (isServer769()) {\n                return handleServerConfiguration763From769(packet);\n            }\n            if (isServer768()) {\n                return handleServerConfiguration763From768(packet);\n            }'),
    # 「handleServerConfiguration 774 branch」：live の 774節は
    # isServer773 分岐を持ち、773節は isServer774 分岐を持つ
    #（shift した版は両方落としている）。775節を前に足し、
    # 774節には isServer775→774From775 を足す。773節は live のまま。
    "handleServerConfiguration 774 branch": (
        '        // 774クライアントは設定フェーズを中継する（番号と Registry Data\n        // の形式だけ変換する）。\n        if (is774()) {\n            if (isServer774()) {\n                // 内部774＋774クライアントはネイティブのためここには来ない。\n                // 念のためそのまま通す。\n                return Outcome.toClient(packet);\n            }\n            if (isServer773()) {\n                return handleServerConfiguration774From773(packet);\n            }\n            if (isServer772()) {\n                return handleServerConfiguration774From772(packet);\n            }\n            if (isServer771()) {\n                return handleServerConfiguration774From771(packet);\n            }\n            if (isServer770()) {\n                return handleServerConfiguration774From770(packet);\n            }\n            if (isServer769()) {\n                return handleServerConfiguration774From769(packet);\n            }\n            if (isServer768()) {\n                return handleServerConfiguration774From768(packet);\n            }\n            if (isServer767()) {\n                return handleServerConfiguration774From767(packet);\n            }\n            if (isServer766()) {\n                return handleServerConfiguration774From766(packet);\n            }\n            return handleServerConfiguration774(packet, isServer764());\n        }\n        // 773クライアントは設定フェーズを中継する（番号と Registry Data\n        // の形式だけ変換する）。\n        if (is773()) {\n            if (isServer773()) {\n                // 内部773＋773クライアントはネイティブのためここには来ない。\n                // 念のためそのまま通す。\n                return Outcome.toClient(packet);\n            }\n            if (isServer774()) {\n                return handleServerConfiguration773From774(packet);\n            }\n            if (isServer772()) {\n                return handleServerConfiguration773From772(packet);\n            }\n            if (isServer771()) {\n                return handleServerConfiguration773From771(packet);\n            }\n            if (isServer770()) {\n                return handleServerConfiguration773From770(packet);\n            }\n            if (isServer769()) {\n                return handleServerConfiguration773From769(packet);\n            }\n            if (isServer768()) {\n                return handleServerConfiguration773From768(packet);\n            }\n            if (isServer767()) {\n                return handleServerConfiguration773From767(packet);\n            }\n            if (isServer766()) {\n                return handleServerConfiguration773From766(packet);\n            }\n            return handleServerConfiguration773(packet, isServer764());\n        }',
        '        // 775クライアントは設定フェーズを中継する（番号と Registry Data\n        // の形式だけ変換する）。\n        if (is775()) {\n            if (isServer775()) {\n                // 内部775＋775クライアントはネイティブのためここには来ない。\n                // 念のためそのまま通す。\n                return Outcome.toClient(packet);\n            }\n            if (isServer774()) {\n                return handleServerConfiguration775From774(packet);\n            }\n            if (isServer773()) {\n                return handleServerConfiguration775From773(packet);\n            }\n            if (isServer772()) {\n                return handleServerConfiguration775From772(packet);\n            }\n            if (isServer771()) {\n                return handleServerConfiguration775From771(packet);\n            }\n            if (isServer770()) {\n                return handleServerConfiguration775From770(packet);\n            }\n            if (isServer769()) {\n                return handleServerConfiguration775From769(packet);\n            }\n            if (isServer768()) {\n                return handleServerConfiguration775From768(packet);\n            }\n            if (isServer767()) {\n                return handleServerConfiguration775From767(packet);\n            }\n            if (isServer766()) {\n                return handleServerConfiguration775From766(packet);\n            }\n            return handleServerConfiguration775(packet, isServer764());\n        }\n        // 774クライアントは設定フェーズを中継する（番号と Registry Data\n        // の形式だけ変換する）。\n        if (is774()) {\n            if (isServer774()) {\n                // 内部774＋774クライアントはネイティブのためここには来ない。\n                // 念のためそのまま通す。\n                return Outcome.toClient(packet);\n            }\n            if (isServer775()) {\n                return handleServerConfiguration774From775(packet);\n            }\n            if (isServer773()) {\n                return handleServerConfiguration774From773(packet);\n            }\n            if (isServer772()) {\n                return handleServerConfiguration774From772(packet);\n            }\n            if (isServer771()) {\n                return handleServerConfiguration774From771(packet);\n            }\n            if (isServer770()) {\n                return handleServerConfiguration774From770(packet);\n            }\n            if (isServer769()) {\n                return handleServerConfiguration774From769(packet);\n            }\n            if (isServer768()) {\n                return handleServerConfiguration774From768(packet);\n            }\n            if (isServer767()) {\n                return handleServerConfiguration774From767(packet);\n            }\n            if (isServer766()) {\n                return handleServerConfiguration774From766(packet);\n            }\n            return handleServerConfiguration774(packet, isServer764());\n        }\n        // 773クライアントは設定フェーズを中継する（番号と Registry Data\n        // の形式だけ変換する）。\n        if (is773()) {\n            if (isServer773()) {\n                // 内部773＋773クライアントはネイティブのためここには来ない。\n                // 念のためそのまま通す。\n                return Outcome.toClient(packet);\n            }\n            if (isServer774()) {\n                return handleServerConfiguration773From774(packet);\n            }\n            if (isServer772()) {\n                return handleServerConfiguration773From772(packet);\n            }\n            if (isServer771()) {\n                return handleServerConfiguration773From771(packet);\n            }\n            if (isServer770()) {\n                return handleServerConfiguration773From770(packet);\n            }\n            if (isServer769()) {\n                return handleServerConfiguration773From769(packet);\n            }\n            if (isServer768()) {\n                return handleServerConfiguration773From768(packet);\n            }\n            if (isServer767()) {\n                return handleServerConfiguration773From767(packet);\n            }\n            if (isServer766()) {\n                return handleServerConfiguration773From766(packet);\n            }\n            return handleServerConfiguration773(packet, isServer764());\n        }'),
    # 「handleServerConfiguration 774 branch isServer774」：shift した版は
    # 773From774 と書いているが、live の 772節は 772From774 である。
    # live の並び（771／770／774／773／769）に isServer775 を足す。
    "handleServerConfiguration 774 branch isServer774": (
        '            if (isServer771()) {\n                return handleServerConfiguration772From771(packet);\n            }\n            if (isServer770()) {\n                return handleServerConfiguration772From770(packet);\n            }\n            if (isServer774()) {\n                return handleServerConfiguration772From774(packet);\n            }\n            if (isServer773()) {\n                return handleServerConfiguration772From773(packet);\n            }\n            if (isServer769()) {\n                return handleServerConfiguration772From769(packet);\n            }',
        '            if (isServer771()) {\n                return handleServerConfiguration772From771(packet);\n            }\n            if (isServer770()) {\n                return handleServerConfiguration772From770(packet);\n            }\n            if (isServer775()) {\n                return handleServerConfiguration772From775(packet);\n            }\n            if (isServer774()) {\n                return handleServerConfiguration772From774(packet);\n            }\n            if (isServer773()) {\n                return handleServerConfiguration772From773(packet);\n            }\n            if (isServer769()) {\n                return handleServerConfiguration772From769(packet);\n            }'),
    # 「handleServerPlayFromNNN Disconnect 774」6件：live は is763() ではなく
    # is763Like() を使う（759〜762 ステージの改名）。Disconnect は 775 でも
    # 0x20 のため、is773() || is774() に || is775() を足すだけにする。
    "handleServerPlayFrom769 Disconnect 774": (
        '        // Disconnect（769：0x1D、NBT）。\n        if (packet.id() == 0x1D) {\n            closed = true;\n            if (is763Like() || is764()) {\n                byte[] payload = PlayTranslator.translateDisconnect765To763(packet.payload());\n                int outId = is763Like() ? 0x1A : 0x1B;\n                return new Outcome(List.of(),\n                        List.of(new MinecraftPacket(outId, payload)), false, true);\n            }\n            if (is773() || is774()) {\n                // 773／774クライアント：Disconnect は 0x20（NBT）。\n                return new Outcome(List.of(),\n                        List.of(new MinecraftPacket(0x20, packet.payload())), false, true);\n            }',
        '        // Disconnect（769：0x1D、NBT）。\n        if (packet.id() == 0x1D) {\n            closed = true;\n            if (is763Like() || is764()) {\n                byte[] payload = PlayTranslator.translateDisconnect765To763(packet.payload());\n                int outId = is763Like() ? 0x1A : 0x1B;\n                return new Outcome(List.of(),\n                        List.of(new MinecraftPacket(outId, payload)), false, true);\n            }\n            if (is773() || is774() || is775()) {\n                // 773／774／775クライアント：Disconnect は 0x20（NBT）。\n                return new Outcome(List.of(),\n                        List.of(new MinecraftPacket(0x20, packet.payload())), false, true);\n            }'),
    "handleServerPlayFrom768 Disconnect 774": (
        '        // Disconnect（768：0x1D、NBT）。\n        if (packet.id() == 0x1D) {\n            closed = true;\n            if (is763Like() || is764()) {\n                byte[] payload = PlayTranslator.translateDisconnect765To763(packet.payload());\n                int outId = is763Like() ? 0x1A : 0x1B;\n                return new Outcome(List.of(),\n                        List.of(new MinecraftPacket(outId, payload)), false, true);\n            }\n            if (is773() || is774()) {\n                // 773／774クライアント：Disconnect は 0x20（NBT）。\n                return new Outcome(List.of(),\n                        List.of(new MinecraftPacket(0x20, packet.payload())), false, true);\n            }',
        '        // Disconnect（768：0x1D、NBT）。\n        if (packet.id() == 0x1D) {\n            closed = true;\n            if (is763Like() || is764()) {\n                byte[] payload = PlayTranslator.translateDisconnect765To763(packet.payload());\n                int outId = is763Like() ? 0x1A : 0x1B;\n                return new Outcome(List.of(),\n                        List.of(new MinecraftPacket(outId, payload)), false, true);\n            }\n            if (is773() || is774() || is775()) {\n                // 773／774／775クライアント：Disconnect は 0x20（NBT）。\n                return new Outcome(List.of(),\n                        List.of(new MinecraftPacket(0x20, packet.payload())), false, true);\n            }'),
    "handleServerPlayFrom770 Disconnect 774": (
        '        // Disconnect（770：0x1C、NBT）。\n        if (packet.id() == 0x1C) {\n            closed = true;\n            if (is763Like() || is764()) {\n                byte[] payload = PlayTranslator.translateDisconnect765To763(packet.payload());\n                int outId = is763Like() ? 0x1A : 0x1B;\n                return new Outcome(List.of(),\n                        List.of(new MinecraftPacket(outId, payload)), false, true);\n            }\n            if (is773() || is774()) {\n                // 773／774クライアント：Disconnect は 0x20（NBT）。\n                return new Outcome(List.of(),\n                        List.of(new MinecraftPacket(0x20, packet.payload())), false, true);\n            }',
        '        // Disconnect（770：0x1C、NBT）。\n        if (packet.id() == 0x1C) {\n            closed = true;\n            if (is763Like() || is764()) {\n                byte[] payload = PlayTranslator.translateDisconnect765To763(packet.payload());\n                int outId = is763Like() ? 0x1A : 0x1B;\n                return new Outcome(List.of(),\n                        List.of(new MinecraftPacket(outId, payload)), false, true);\n            }\n            if (is773() || is774() || is775()) {\n                // 773／774／775クライアント：Disconnect は 0x20（NBT）。\n                return new Outcome(List.of(),\n                        List.of(new MinecraftPacket(0x20, packet.payload())), false, true);\n            }'),
    "handleServerPlayFrom771 Disconnect 774": (
        '        // Disconnect（771：0x1C、NBT）。\n        if (packet.id() == 0x1C) {\n            closed = true;\n            if (is763Like() || is764()) {\n                byte[] payload = PlayTranslator.translateDisconnect765To763(packet.payload());\n                int outId = is763Like() ? 0x1A : 0x1B;\n                return new Outcome(List.of(),\n                        List.of(new MinecraftPacket(outId, payload)), false, true);\n            }\n            if (is771()) {\n                // 771クライアント：Disconnect は 0x1C（NBT）のまま。\n                return new Outcome(List.of(),\n                        List.of(new MinecraftPacket(0x1C, packet.payload())), false, true);\n            }\n            if (is772()) {\n                // 772クライアント：Disconnect は 0x1C（NBT）のまま。\n                return new Outcome(List.of(),\n                        List.of(new MinecraftPacket(0x1C, packet.payload())), false, true);\n            }\n            if (is773() || is774()) {\n                // 773／774クライアント：Disconnect は 0x20（NBT）。\n                return new Outcome(List.of(),\n                        List.of(new MinecraftPacket(0x20, packet.payload())), false, true);\n            }',
        '        // Disconnect（771：0x1C、NBT）。\n        if (packet.id() == 0x1C) {\n            closed = true;\n            if (is763Like() || is764()) {\n                byte[] payload = PlayTranslator.translateDisconnect765To763(packet.payload());\n                int outId = is763Like() ? 0x1A : 0x1B;\n                return new Outcome(List.of(),\n                        List.of(new MinecraftPacket(outId, payload)), false, true);\n            }\n            if (is771()) {\n                // 771クライアント：Disconnect は 0x1C（NBT）のまま。\n                return new Outcome(List.of(),\n                        List.of(new MinecraftPacket(0x1C, packet.payload())), false, true);\n            }\n            if (is772()) {\n                // 772クライアント：Disconnect は 0x1C（NBT）のまま。\n                return new Outcome(List.of(),\n                        List.of(new MinecraftPacket(0x1C, packet.payload())), false, true);\n            }\n            if (is773() || is774() || is775()) {\n                // 773／774／775クライアント：Disconnect は 0x20（NBT）。\n                return new Outcome(List.of(),\n                        List.of(new MinecraftPacket(0x20, packet.payload())), false, true);\n            }'),
    "handleServerPlayFrom772 Disconnect 774": (
        '        // Disconnect（772：0x1C、NBT）。\n        if (packet.id() == 0x1C) {\n            closed = true;\n            if (is763Like() || is764()) {\n                byte[] payload = PlayTranslator.translateDisconnect765To763(packet.payload());\n                int outId = is763Like() ? 0x1A : 0x1B;\n                return new Outcome(List.of(),\n                        List.of(new MinecraftPacket(outId, payload)), false, true);\n            }\n            if (is773() || is774()) {\n                // 773／774クライアント：Disconnect は 0x20（NBT）。\n                return new Outcome(List.of(),\n                        List.of(new MinecraftPacket(0x20, packet.payload())), false, true);\n            }',
        '        // Disconnect（772：0x1C、NBT）。\n        if (packet.id() == 0x1C) {\n            closed = true;\n            if (is763Like() || is764()) {\n                byte[] payload = PlayTranslator.translateDisconnect765To763(packet.payload());\n                int outId = is763Like() ? 0x1A : 0x1B;\n                return new Outcome(List.of(),\n                        List.of(new MinecraftPacket(outId, payload)), false, true);\n            }\n            if (is773() || is774() || is775()) {\n                // 773／774／775クライアント：Disconnect は 0x20（NBT）。\n                return new Outcome(List.of(),\n                        List.of(new MinecraftPacket(0x20, packet.payload())), false, true);\n            }'),
    "handleServerPlayFrom774 Disconnect 774": (
        '        // Disconnect（774：0x20、NBT）。\n        if (packet.id() == 0x20) {\n            closed = true;\n            if (is763Like() || is764()) {\n                byte[] payload = PlayTranslator.translateDisconnect765To763(packet.payload());\n                int outId = is763Like() ? 0x1A : 0x1B;\n                return new Outcome(List.of(),\n                        List.of(new MinecraftPacket(outId, payload)), false, true);\n            }\n            if (is773() || is774()) {\n                // 773／774クライアント：Disconnect は 0x20（NBT）のまま。\n                return new Outcome(List.of(),\n                        List.of(new MinecraftPacket(0x20, packet.payload())), false, true);\n            }\n            if (is770() || is771() || is772()) {\n                // 770／771／772クライアント：Disconnect は 0x1C（NBT）のまま。\n                return new Outcome(List.of(),\n                        List.of(new MinecraftPacket(0x1C, packet.payload())), false, true);\n            }\n            if (is766() || is767() || is768() || is769()) {\n                // 766／767／768／769クライアント：Disconnect は 0x1D（NBT）のまま。\n                return new Outcome(List.of(),\n                        List.of(new MinecraftPacket(0x1D, packet.payload())), false, true);\n            }\n            return new Outcome(List.of(),\n                    List.of(new MinecraftPacket(0x1B, packet.payload())), false, true);\n        }',
        '        // Disconnect（774：0x20、NBT）。\n        if (packet.id() == 0x20) {\n            closed = true;\n            if (is763Like() || is764()) {\n                byte[] payload = PlayTranslator.translateDisconnect765To763(packet.payload());\n                int outId = is763Like() ? 0x1A : 0x1B;\n                return new Outcome(List.of(),\n                        List.of(new MinecraftPacket(outId, payload)), false, true);\n            }\n            if (is773() || is774() || is775()) {\n                // 773／774／775クライアント：Disconnect は 0x20（NBT）のまま。\n                return new Outcome(List.of(),\n                        List.of(new MinecraftPacket(0x20, packet.payload())), false, true);\n            }\n            if (is770() || is771() || is772()) {\n                // 770／771／772クライアント：Disconnect は 0x1C（NBT）のまま。\n                return new Outcome(List.of(),\n                        List.of(new MinecraftPacket(0x1C, packet.payload())), false, true);\n            }\n            if (is766() || is767() || is768() || is769()) {\n                // 766／767／768／769クライアント：Disconnect は 0x1D（NBT）のまま。\n                return new Outcome(List.of(),\n                        List.of(new MinecraftPacket(0x1D, packet.payload())), false, true);\n            }\n            return new Outcome(List.of(),\n                    List.of(new MinecraftPacket(0x1B, packet.payload())), false, true);\n        }'),
    # 「handleServerPlayFrom771 Respawn 774」：live のコメントは
    # 770（0x4B）（shift した版は 0x4C と書いている）。is775 分岐を
    # 0x52 で前に足す。
    "handleServerPlayFrom771 Respawn 774": (
        '        // Respawn（771：0x4B。770（0x4B）と同形式のため770の変換を使い回す）。\n        if (packet.id() == 0x4B) {\n            if (is774()) {\n                // 774クライアント：Respawn は 0x50（同形式）のまま。\n                return Outcome.toClient(new MinecraftPacket(0x50, packet.payload()));\n            }\n            if (is773()) {\n                // 773クライアント：Respawn は 0x50（同形式）のまま。\n                return Outcome.toClient(new MinecraftPacket(0x50, packet.payload()));\n            }\n            if (is772()) {\n                // 772クライアント：Respawn は 0x4B（同形式）のまま。\n                return Outcome.toClient(new MinecraftPacket(0x4B, packet.payload()));\n            }\n            if (is770()) {',
        '        // Respawn（771：0x4B。770（0x4B）と同形式のため770の変換を使い回す）。\n        if (packet.id() == 0x4B) {\n            if (is775()) {\n                // 775クライアント：Respawn は 0x52（同形式）のまま。\n                return Outcome.toClient(new MinecraftPacket(0x52, packet.payload()));\n            }\n            if (is774()) {\n                // 774クライアント：Respawn は 0x50（同形式）のまま。\n                return Outcome.toClient(new MinecraftPacket(0x50, packet.payload()));\n            }\n            if (is773()) {\n                // 773クライアント：Respawn は 0x50（同形式）のまま。\n                return Outcome.toClient(new MinecraftPacket(0x50, packet.payload()));\n            }\n            if (is772()) {\n                // 772クライアント：Respawn は 0x4B（同形式）のまま。\n                return Outcome.toClient(new MinecraftPacket(0x4B, packet.payload()));\n            }\n            if (is770()) {'),
    # 「chatGlobalIndex field」：'pack774 field' 編集が先に pack775
    # ブロックを pack774 の直後に挟むため、old は pack775 ブロックを
    # 含む形（pack_format 84）にする。
    "chatGlobalIndex field": (
        '    /**\n     * 773クライアントに送るリソースパック（pack_format 69）。\n     * 内部が773でない場合に使う差し替え用。内部が773の場合はバニラが\n     * 送ってきたパックをそのまま出すため null のままでよい。\n     * 出し分けの基準はクライアントのプロトコルである\n     * （{@link #packOverrideForClient()} 参照）。\n     */\n    private ResourcePack pack773;\n\n    /**\n     * 774クライアントに送るリソースパック（pack_format 75）。\n     * 内部が774でない場合に使う差し替え用。内部が774の場合はバニラが\n     * 送ってきたパックをそのまま出すため null のままでよい。\n     * 出し分けの基準はクライアントのプロトコルである\n     * （{@link #packOverrideForClient()} 参照）。\n     */\n    private ResourcePack pack774;\n\n    /**\n     * 775クライアントに送るリソースパック（pack_format 84）。\n     * 内部が775でない場合に使う差し替え用。内部が775の場合はバニラが\n     * 送ってきたパックをそのまま出すため null のままでよい。\n     * 出し分けの基準はクライアントのプロトコルである\n     * （{@link #packOverrideForClient()} 参照）。\n     */\n    private ResourcePack pack775;\n\n    /**\n     * 770／771／772／773／774クライアントへ送る player_chat に付ける globalIndex。\n     * 769サーバーは globalIndex を送ってこないため、プロキシが\n     * セッションごとに 0 から数える。\n     */\n    private int chatGlobalIndex = 0;',
        '    /**\n     * 773クライアントに送るリソースパック（pack_format 69）。\n     * 内部が773でない場合に使う差し替え用。内部が773の場合はバニラが\n     * 送ってきたパックをそのまま出すため null のままでよい。\n     * 出し分けの基準はクライアントのプロトコルである\n     * （{@link #packOverrideForClient()} 参照）。\n     */\n    private ResourcePack pack773;\n\n    /**\n     * 774クライアントに送るリソースパック（pack_format 75）。\n     * 内部が774でない場合に使う差し替え用。内部が774の場合はバニラが\n     * 送ってきたパックをそのまま出すため null のままでよい。\n     * 出し分けの基準はクライアントのプロトコルである\n     * （{@link #packOverrideForClient()} 参照）。\n     */\n    private ResourcePack pack774;\n\n    /**\n     * 775クライアントに送るリソースパック（pack_format 84）。\n     * 内部が775でない場合に使う差し替え用。内部が775の場合はバニラが\n     * 送ってきたパックをそのまま出すため null のままでよい。\n     * 出し分けの基準はクライアントのプロトコルである\n     * （{@link #packOverrideForClient()} 参照）。\n     */\n    private ResourcePack pack775;\n\n    /**\n     * 770／771／772／773／774／775クライアントへ送る player_chat に付ける globalIndex。\n     * 769サーバーは globalIndex を送ってこないため、プロキシが\n     * セッションごとに 0 から数える。\n     */\n    private int chatGlobalIndex = 0;'),
    # 「synthesized add-pack」：774／775の Add Resource Pack ID は 0x51
    # （PacketIds：774=0x4f、775=0x51。773までは 0x4F）。
    # shift は 0x4F のままにしてしまうため、live に合わせて丸ごと書き直す。
    # 三項は is775() ? 0x51 : is773() || is774() ? 0x4F : ... にする。
    "synthesized add-pack": (
        "            if (is768() || is769() || is770() || is771() || is772() || is773() || is774()) {\n"
        "                // プレイ状態の Add Resource Pack は 0x4B（PLAY_ADD_RESOURCE_PACK）。774は 0x4F。\n"
        "                toClient.add(new MinecraftPacket(is773() || is774() ? 0x4F : is770() || is771() || is772() ? 0x4A : 0x4B,",
        "            if (is768() || is769() || is770() || is771() || is772() || is773() || is774() || is775()) {\n"
        "                // プレイ状態の Add Resource Pack は 0x4B（PLAY_ADD_RESOURCE_PACK）。775は 0x51。\n"
        "                toClient.add(new MinecraftPacket(is775() ? 0x51 : is773() || is774() ? 0x4F : is770() || is771() || is772() ? 0x4A : 0x4B,"),
    # 「constructor javadoc」：live の版一覧は 759 から始まるため、
    # shift した版（763 から）と合わない。末尾に 775＝26.1 を足す。
    "constructor javadoc": (
        "     *                       769＝1.21.4、770＝1.21.5、771＝1.21.6、772＝1.21.7、773＝1.21.9／1.21.10、774＝1.21.11）",
        "     *                       769＝1.21.4、770＝1.21.5、771＝1.21.6、772＝1.21.7、773＝1.21.9／1.21.10、774＝1.21.11、775＝26.1）"),
    # 「serverProtocol() javadoc」：同上。
    "serverProtocol() javadoc": (
        "    /** 内部バニラのプロトコル（759＝1.19、763＝1.20.1、764＝1.20.2、765＝1.20.4、766＝1.20.5／1.20.6、767＝1.21／1.21.1、768＝1.21.2／1.21.3、769＝1.21.4、770＝1.21.5、771＝1.21.6、772＝1.21.7、773＝1.21.9／1.21.10、774＝1.21.11）。 */",
        "    /** 内部バニラのプロトコル（759＝1.19、763＝1.20.1、764＝1.20.2、765＝1.20.4、766＝1.20.5／1.20.6、767＝1.21／1.21.1、768＝1.21.2／1.21.3、769＝1.21.4、770＝1.21.5、771＝1.21.6、772＝1.21.7、773＝1.21.9／1.21.10、774＝1.21.11、775＝26.1）。 */"),
    # 「constructor validation」：live の検査一覧は V1_19 から始まるため、
    # shift した版（V1_21 から）と合わない。V26_1 の検査とメッセージの
    # 775 (26.1) を足す。V1_21_2（768）の検査は live にあるため残す。
    "constructor validation": (
        'if (serverProtocol != ProtocolVersion.V1_19\n                && serverProtocol != ProtocolVersion.V1_19_1\n                && serverProtocol != ProtocolVersion.V1_19_3\n                && serverProtocol != ProtocolVersion.V1_19_4\n                && serverProtocol != ProtocolVersion.V1_20_1\n                && serverProtocol != ProtocolVersion.V1_20_2\n                && serverProtocol != ProtocolVersion.V1_20_4\n                && serverProtocol != ProtocolVersion.V1_20_5\n                && serverProtocol != ProtocolVersion.V1_21\n                && serverProtocol != ProtocolVersion.V1_21_2\n                && serverProtocol != ProtocolVersion.V1_21_4\n                && serverProtocol != ProtocolVersion.V1_21_5\n                && serverProtocol != ProtocolVersion.V1_21_6\n                && serverProtocol != ProtocolVersion.V1_21_7\n                && serverProtocol != ProtocolVersion.V1_21_9\n                && serverProtocol != ProtocolVersion.V1_21_11) {\n            throw new IllegalArgumentException(\n                    "serverProtocol must be 759 (1.19), 760 (1.19.1/1.19.2), 761 (1.19.3), 762 (1.19.4), 763 (1.20.1), 764 (1.20.2), "\n                            + "765 (1.20.4), 766 (1.20.5/1.20.6), 767 (1.21/1.21.1), "\n                            + "768 (1.21.2/1.21.3), 769 (1.21.4), 770 (1.21.5), 771 (1.21.6), 772 (1.21.7), 773 (1.21.9/1.21.10) or 774 (1.21.11): "\n                            + serverProtocol);\n        }',
        'if (serverProtocol != ProtocolVersion.V1_19\n                && serverProtocol != ProtocolVersion.V1_19_1\n                && serverProtocol != ProtocolVersion.V1_19_3\n                && serverProtocol != ProtocolVersion.V1_19_4\n                && serverProtocol != ProtocolVersion.V1_20_1\n                && serverProtocol != ProtocolVersion.V1_20_2\n                && serverProtocol != ProtocolVersion.V1_20_4\n                && serverProtocol != ProtocolVersion.V1_20_5\n                && serverProtocol != ProtocolVersion.V1_21\n                && serverProtocol != ProtocolVersion.V1_21_2\n                && serverProtocol != ProtocolVersion.V1_21_4\n                && serverProtocol != ProtocolVersion.V1_21_5\n                && serverProtocol != ProtocolVersion.V1_21_6\n                && serverProtocol != ProtocolVersion.V1_21_7\n                && serverProtocol != ProtocolVersion.V1_21_9\n                && serverProtocol != ProtocolVersion.V1_21_11\n                && serverProtocol != ProtocolVersion.V26_1) {\n            throw new IllegalArgumentException(\n                    "serverProtocol must be 759 (1.19), 760 (1.19.1/1.19.2), 761 (1.19.3), 762 (1.19.4), 763 (1.20.1), 764 (1.20.2), "\n                            + "765 (1.20.4), 766 (1.20.5/1.20.6), 767 (1.21/1.21.1), "\n                            + "768 (1.21.2/1.21.3), 769 (1.21.4), 770 (1.21.5), 771 (1.21.6), 772 (1.21.7), 773 (1.21.9/1.21.10), 774 (1.21.11) or 775 (26.1): "\n                            + serverProtocol);\n        }'),
}


# 774パッチから外す編集。773ステージで既に live へ適用済みの修正で、
# shift すると存在しないメソッド（translatePlayerInfo773To768 等）を
# 呼ぶ壊れた編集になってしまうため。
SKIP_EDITS = {
    "to768ServerPacket chat passthrough",
    "handleServerPlay768To767 chat passthrough",
    "handleServerPlay767To766 system_chat passthrough",    "synthesized add-pack 773 id",
    "handleServerJoinGame773 773 branch",
    # 「handleServerConfiguration 772 branch isServer774」：shift した版は
    # 772節へ isServer774 分岐を挿すが、live の 772節は既に isServer774
    # 分岐を持つ（774パッチの別編集で入った）。挿すと重複分岐になる。
    # 772節への isServer775 挿入は FULL「handleServerConfiguration 774 branch
    # isServer774」が担うため、ここでは落とす。
    "handleServerConfiguration 772 branch isServer774",

}


ADD_EDITS = [
    # 774サーバー→775クライアント：一般プレイ状態の変換。
    # （shift では作れない。handleServerPlayFrom774 に775分岐が無いため。
    # patch-774 の From774 一般分岐は is773→774To773 が先頭で、shift の
    # 対象になる 774 分岐の編集が存在しない。）
        ("handleServerPlayFrom774 775 branch",
     '        // 773クライアント：774→773へ変換する。\n        if (is773()) {\n            return handleServerPlay774To773(packet);\n        }',
     '        // 775クライアント：774→775へ変換する。\n        if (is775()) {\n            return handleServerPlay774To775(packet);\n        }\n        // 773クライアント：774→773へ変換する。\n        if (is773()) {\n            return handleServerPlay774To773(packet);\n        }'),
    # 774サーバー→775クライアント：Join Game（774：0x30、775：0x31）と
    # Add Resource Pack（774：0x4F、775：0x51）。
    # （shift では作れない。handleServerJoinGame774 に775分岐が無いため。）
        ("handleServerJoinGame774 775 branch",
     '    private Outcome handleServerJoinGame774(MinecraftPacket packet) {\n        if (is773()) {',
     '    private Outcome handleServerJoinGame774(MinecraftPacket packet) {\n        if (is775()) {\n            advanceFlowSafely(() -> joinFlow.joinGameReceived());\n            List<MinecraftPacket> toClient = new ArrayList<>();\n            toClient.add(new MinecraftPacket(0x31, packet.payload()));\n            while (!pendingPacks.isEmpty()) {\n                PendingPack pack = pendingPacks.removeFirst();\n                lastSentPack = pack;\n                toClient.add(new MinecraftPacket(0x51,\n                        PlayTranslator.translateConfigAddResourcePack764To765(\n                                pack, packOverrideForClient())));\n            }\n            return new Outcome(List.of(), toClient, false, false);\n        }\n        if (is773()) {'),

]


# CONFIG_774 に追加する、旧クライアント＋内部774の設定フェーズ変換器。
# shift では作れない（773ステージにも同型の手書きメソッドがある）。



# FIX_BLOCKS：774版の修正は shift 後のブロックに既に含まれているため、
# 775では段数・連鎖の潰れ（773段の欠落）とパケットIDだけを直す。キーは
# 読む側の版名（CLIENT_PLAY_775 等）のまま。
FIX_BLOCKS = {
    "CLIENT_PLAY_775": [
        # Set Creative Slot：767→775 は八段。
        ('     * Set Creative Slot は767→768→769→770→771→772→773→774→775へ七段で変換する', '     * Set Creative Slot は767→768→769→770→771→772→773→774→775へ八段で変換する'),
        # Set Creative Slot の連鎖：772→774 の潰れを 772→773→774 に展開する。
        ('                    byte[] payload = ChunkTranslator.translateSetCreativeSlot774To775(\n                            ChunkTranslator.translateSetCreativeSlot772To774(\n                                    ChunkTranslator.translateSetCreativeSlot771To772(\n                                            ChunkTranslator.translateSetCreativeSlot770To771(\n                                            ChunkTranslator.translateSetCreativeSlot769To770(\n                                                    ChunkTranslator.translateSetCreativeSlot768To769(\n                                                            ChunkTranslator.translateSetCreativeSlot767To768(packet.payload())))))));',
         '                    byte[] payload = ChunkTranslator.translateSetCreativeSlot774To775(\n                            ChunkTranslator.translateSetCreativeSlot773To774(\n                                    ChunkTranslator.translateSetCreativeSlot772To773(\n                                            ChunkTranslator.translateSetCreativeSlot771To772(\n                                                    ChunkTranslator.translateSetCreativeSlot770To771(\n                                                            ChunkTranslator.translateSetCreativeSlot769To770(\n                                                                    ChunkTranslator.translateSetCreativeSlot768To769(\n                                                                            ChunkTranslator.translateSetCreativeSlot767To768(packet.payload()))))))));'),
        # Window Click（775クライアント→774サーバー）：775 の ID は 0x12。
        ('        // Window Click（775：0x11）：771・772・773・774・775とも HashedSlot のため\n        // 落とす（ハッシュの計算方法は未確定）。\n        if (packet.id() == 0x11) {',
         '        // Window Click（775：0x12）：771・772・773・774・775とも HashedSlot のため\n        // 落とす（ハッシュの計算方法は未確定）。\n        if (packet.id() == 0x12) {'),
        # Set Creative Slot（775クライアント→774サーバー）：775 の ID は 0x38。
        ('        // Set Creative Slot（775：0x37→774：0x37）：\n        // UntrustedSlot のままアイテム／成分を付け替える。\n        if (packet.id() == 0x37) {',
         '        // Set Creative Slot（775：0x38→774：0x37）：\n        // UntrustedSlot のままアイテム／成分を付け替える。\n        if (packet.id() == 0x38) {'),
        # Set Creative Slot（774クライアント→775サーバー）：775 の ID は 0x38。
        ('        // Set Creative Slot（774：0x37→775：0x37）：\n        // UntrustedSlot のままアイテム／成分を付け替える。\n        if (packet.id() == 0x37) {\n            return new MinecraftPacket(0x37,\n                    ChunkTranslator.translateSetCreativeSlot774To775(packet.payload()));',
         '        // Set Creative Slot（774：0x37→775：0x38）：\n        // UntrustedSlot のままアイテム／成分を付け替える。\n        if (packet.id() == 0x37) {\n            return new MinecraftPacket(0x38,\n                    ChunkTranslator.translateSetCreativeSlot774To775(packet.payload()));'),
    ],
    "CONFIG_775": [
        # Registry Data（766→775）：九段。
        ('     * Registry Data だけ766→767→768→769→770→771→772→773→774→775へ七段で並べ替え（jukebox_song は', '     * Registry Data だけ766→767→768→769→770→771→772→773→774→775へ九段で並べ替え（jukebox_song は'),
        # Registry Data（765→775）：九段。
        ('     * （765→767→768→769→770→771→772→773→774→775へ七段）。', '     * （765→767→768→769→770→771→772→773→774→775へ九段）。'),
        # Registry Data（775→765）：九段。
        ('     * Registry Data だけ775→774→773→772→771→770→769→768→767→765へ七段で変換する（jukebox_song は落とす）。', '     * Registry Data だけ775→774→773→772→771→770→769→768→767→765へ九段で変換する（jukebox_song は落とす）。'),
        # Registry Data の段数：FIX_BLOCKS_ALL で 772→774 の潰れを展開し
        # 774→775 の段を足したため、全連鎖の呼び出し回数が 774 のときより
        # 2 回増える。段数は呼び出し回数（774 の慣例）で数える。
        # 768→775：五段→七段、767→775：六段→八段、775→767：六段→八段、
        # 775→766：七段→九段、769→775：四段→六段、770→775：三段→五段、
        # 775→769：四段→六段、775→768：五段→七段。
        ('768→769→770→771→772→773→774→775へ五段', '768→769→770→771→772→773→774→775へ七段'),
        ('767→768→769→770→771→772→773→774→775へ六段', '767→768→769→770→771→772→773→774→775へ八段'),
        ('775→774→773→772→771→770→769→768→767へ六段', '775→774→773→772→771→770→769→768→767へ八段'),
        ('775→774→773→772→771→770→769→768→767→766へ七段', '775→774→773→772→771→770→769→768→767→766へ九段'),
        ('769→770→771→772→773→774→775へ四段', '769→770→771→772→773→774→775へ六段'),
        ('770→771→772→773→774→775へ三段', '770→771→772→773→774→775へ五段'),
        ('775→774→773→772→771→770→769へ四段', '775→774→773→772→771→770→769へ六段'),
        ('775→774→773→772→771→770→769→768へ五段', '775→774→773→772→771→770→769→768へ七段'),
    ],
    "SERVER_PLAY_775": [
        # 767クライアント：コメントの末尾を 769→768 に戻し、to772ServerPacketFrom774
        # の潰れを to773ServerPacketFrom774＋to772ServerPacketFrom773 に展開する。
        ('        // 767クライアント：775→774→773→772→771→770→769→768へ変換してから770→769へ。\n        if (is767()) {\n            MinecraftPacket as774 = to774ServerPacketFrom775(packet);\n            if (as774 == null) {\n                return Outcome.drop();\n            }\n            MinecraftPacket as772 = to772ServerPacketFrom774(as774);\n            if (as772 == null) {\n                return Outcome.drop();\n            }\n            MinecraftPacket as771 = to771ServerPacketFrom772(as772);\n            if (as771 == null) {\n                return Outcome.drop();\n            }\n            MinecraftPacket as770 = to770ServerPacketFrom771(as771);',
         '        // 767クライアント：775→774→773→772→771→770→769→768へ変換してから769→768へ。\n        if (is767()) {\n            MinecraftPacket as774 = to774ServerPacketFrom775(packet);\n            if (as774 == null) {\n                return Outcome.drop();\n            }\n            MinecraftPacket as773 = to773ServerPacketFrom774(as774);\n            if (as773 == null) {\n                return Outcome.drop();\n            }\n            MinecraftPacket as772 = to772ServerPacketFrom773(as773);\n            if (as772 == null) {\n                return Outcome.drop();\n            }\n            MinecraftPacket as771 = to771ServerPacketFrom772(as772);\n            if (as771 == null) {\n                return Outcome.drop();\n            }\n            MinecraftPacket as770 = to770ServerPacketFrom771(as771);'),
        # 766クライアント：同上（コメントの末尾は 768→766）。
        ('        // 766クライアント：775→774→773→772→771→770→769→768へ変換してから770→768へ。\n        if (is766()) {\n            MinecraftPacket as774 = to774ServerPacketFrom775(packet);\n            if (as774 == null) {\n                return Outcome.drop();\n            }\n            MinecraftPacket as772 = to772ServerPacketFrom774(as774);\n            if (as772 == null) {\n                return Outcome.drop();\n            }\n            MinecraftPacket as771 = to771ServerPacketFrom772(as772);\n            if (as771 == null) {\n                return Outcome.drop();\n            }\n            MinecraftPacket as770 = to770ServerPacketFrom771(as771);',
         '        // 766クライアント：775→774→773→772→771→770→769→768へ変換してから768→766へ。\n        if (is766()) {\n            MinecraftPacket as774 = to774ServerPacketFrom775(packet);\n            if (as774 == null) {\n                return Outcome.drop();\n            }\n            MinecraftPacket as773 = to773ServerPacketFrom774(as774);\n            if (as773 == null) {\n                return Outcome.drop();\n            }\n            MinecraftPacket as772 = to772ServerPacketFrom773(as773);\n            if (as772 == null) {\n                return Outcome.drop();\n            }\n            MinecraftPacket as771 = to771ServerPacketFrom772(as772);\n            if (as771 == null) {\n                return Outcome.drop();\n            }\n            MinecraftPacket as770 = to770ServerPacketFrom771(as771);'),
        # handleServerJoinGame775：patch-774 の handleServerJoinGame774 が
        # Join Game 0x2B／Add Resource Pack 0x4A のままだった罠を引き継ぐ。
        # javadoc は内部775：0x31、774分岐は 0x30／0x4F に直す。
        ('    /**\n     * Join Game（内部775：0x2B）のクライアント向け変換。\n     * 774と同形式のため、774クライアントにはそのまま送る。\n     * それ以外は {@link #handleServerJoinGame774} と同じ方針で各クライアントへ送る。\n     */\n    private Outcome handleServerJoinGame775(MinecraftPacket packet) {\n        if (is774()) {\n            advanceFlowSafely(() -> joinFlow.joinGameReceived());\n            List<MinecraftPacket> toClient = new ArrayList<>();\n            toClient.add(new MinecraftPacket(0x2B, packet.payload()));\n            while (!pendingPacks.isEmpty()) {\n                PendingPack pack = pendingPacks.removeFirst();\n                lastSentPack = pack;\n                toClient.add(new MinecraftPacket(0x4A,\n                        PlayTranslator.translateConfigAddResourcePack764To765(\n                                pack, packOverrideForClient())));\n            }\n            return new Outcome(List.of(), toClient, false, false);\n        }\n        return handleServerJoinGame774(packet);\n    }',
         '    /**\n     * Join Game（内部775：0x31）のクライアント向け変換。\n     * 774と同形式のため、774クライアントにはそのまま送る。\n     * それ以外は {@link #handleServerJoinGame774} と同じ方針で各クライアントへ送る。\n     */\n    private Outcome handleServerJoinGame775(MinecraftPacket packet) {\n        if (is774()) {\n            advanceFlowSafely(() -> joinFlow.joinGameReceived());\n            List<MinecraftPacket> toClient = new ArrayList<>();\n            toClient.add(new MinecraftPacket(0x30, packet.payload()));\n            while (!pendingPacks.isEmpty()) {\n                PendingPack pack = pendingPacks.removeFirst();\n                lastSentPack = pack;\n                toClient.add(new MinecraftPacket(0x4F,\n                        PlayTranslator.translateConfigAddResourcePack764To765(\n                                pack, packOverrideForClient())));\n            }\n            return new Outcome(List.of(), toClient, false, false);\n        }\n        return handleServerJoinGame774(packet);\n    }'),
    ],
}


# FIX_BLOCKS_ALL：ブロック内の同じ欠陥が複数箇所にあるものは、全置換で直す。
# （old, new, 期待する一致件数）。774 では FIX_BLOCKS の1回一致 sub が
# 1箇所しか直せず、残りの Registry Data 連鎖の潰れは patch-774.py へ
# 手で入れた。775 の CONFIG_775 には順方向 6 箇所・逆方向 5 箇所あるため、
# 件数を指定して全置換する。件数が合わなければ生成を止める。
FIX_BLOCKS_ALL = {
    "CONFIG_775": [
        # Registry Data（順方向）：772→774 の潰れを 772→773→774 に展開する
        #（16スペース字下げの変数宣言形。767／766／769／770節の 4 箇所）。
        ("                byte[] payload772to774 =\n                        PlayTranslator.translateRegistryData772To774(payload771to772);",
         "                byte[] payload772to773 =\n                        PlayTranslator.translateRegistryData772To773(payload771to772);\n                byte[] payload773to774 =\n                        PlayTranslator.translateRegistryData773To774(payload772to773);",
         4),
        # 順方向の後段：774To775 の引数を payload772to774 から payload773to774 にする（5 箇所）。
        ("PlayTranslator.translateRegistryData774To775(payload772to774)",
         "PlayTranslator.translateRegistryData774To775(payload773to774)",
         5),
        # Registry Data（順方向・20スペース字下げの for ループ内。765節の 1 箇所）。
        ("                    byte[] payload772to774 =\n                            PlayTranslator.translateRegistryData772To774(payload771to772);",
         "                    byte[] payload772to773 =\n                            PlayTranslator.translateRegistryData772To773(payload771to772);\n                    byte[] payload773to774 =\n                            PlayTranslator.translateRegistryData773To774(payload772to773);",
         1),
        # Registry Data（順方向・ネスト呼び出し形。768節の 1 箇所）。
        # 段を2つ足すため、後段の字下げも 16 ずらす。
        ("                        PlayTranslator.translateRegistryData774To775(\n                                PlayTranslator.translateRegistryData772To774(\n                                        PlayTranslator.translateRegistryData771To772(\n                                                PlayTranslator.translateRegistryData770To771(\n                                                        PlayTranslator.translateRegistryData769To770(\n                                                                PlayTranslator.translateRegistryData768To769(packet.payload()))))))));",
         "                        PlayTranslator.translateRegistryData774To775(\n                                PlayTranslator.translateRegistryData773To774(\n                                        PlayTranslator.translateRegistryData772To773(\n                                                PlayTranslator.translateRegistryData771To772(\n                                                        PlayTranslator.translateRegistryData770To771(\n                                                                PlayTranslator.translateRegistryData769To770(\n                                                                        PlayTranslator.translateRegistryData768To769(packet.payload())))))))));",
         1),
        # Registry Data（逆方向）：774→772 の潰れを 774→773→772 に展開する（5 箇所）。
        ("                byte[] payload774to772 =\n                        PlayTranslator.translateRegistryData774To772(payload775to774);",
         "                byte[] payload774to773 =\n                        PlayTranslator.translateRegistryData774To773(payload775to774);\n                byte[] payload773to772 =\n                        PlayTranslator.translateRegistryData773To772(payload774to773);",
         5),
        # 逆方向の後段：772To771 の引数を payload774to772 から payload773to772 にする（5 箇所）。
        ("PlayTranslator.translateRegistryData772To771(payload774to772)",
         "PlayTranslator.translateRegistryData772To771(payload773to772)",
         5),
        # 逆方向の null 検査：payload774to772 を payload773to772 にする（4 箇所。
        # 767節は null 検査が無いため 5 箇所中 4 箇所）。
        ("if (payload774to772 == null)",
         "if (payload773to772 == null)",
         4),
    ],
}


SERVER_PLAY_775_TO = r'''
    /**
     * 774サーバーのプレイ状態パケットを775形式にする。775に相当が無い／
     * 変換できないものは null を返す（ログ済み）。
     * 775クライアントへの送出と、773／772／771／770／769／768／767／766クライアントへの
     * 多段変換の最終段で共用する。経験値オーブは770・771・772・773・774・775とも通常の
     * Spawn Entity（type experience_orb）のため、専用の分岐は要らない。
     */
    private MinecraftPacket to775ServerPacket(MinecraftPacket packet) {
        PacketName name = PacketIds.nameFor(packet.id(), State.PLAY,
                Direction.CLIENTBOUND, ProtocolVersion.V1_21_11).orElse(null);
        if (name == null) {
            logUntranslatedOnce("PLAY_UNKNOWN_0x" + Integer.toHexString(packet.id()));
            return null;
        }
        byte[] translated = switch (name) {
            case PLAY_ENTITY_METADATA ->
                    ChunkTranslator.translateEntityMetadata774To775(packet.payload());
            case PLAY_SET_SLOT ->
                    ChunkTranslator.translateSetSlot774To775(packet.payload());
            case PLAY_SET_CURSOR_ITEM ->
                    ChunkTranslator.translateSetCursorItem774To775(packet.payload());
            case PLAY_SET_PLAYER_INVENTORY ->
                    ChunkTranslator.translateSetPlayerInventory774To775(packet.payload());
            case PLAY_WINDOW_ITEMS ->
                    ChunkTranslator.translateWindowItems774To775(packet.payload());
            case PLAY_SET_COOLDOWN ->
                    ChunkTranslator.translateSetCooldown774To775(packet.payload());
            case PLAY_ENTITY_EQUIPMENT ->
                    ChunkTranslator.translateEntityEquipment774To775(packet.payload());
            case PLAY_TRADE_LIST ->
                    ChunkTranslator.translateTradeList774To775(packet.payload());
            case PLAY_OPEN_WINDOW ->
                    ChunkTranslator.translateOpenWindow774To775(packet.payload());
            case PLAY_BLOCK_ACTION ->
                    ChunkTranslator.translateBlockAction774To775(packet.payload());
            case PLAY_SPAWN_ENTITY ->
                    ChunkTranslator.translateSpawnEntity774To775(packet.payload());
            case PLAY_SOUND_EFFECT ->
                    ChunkTranslator.translateSoundEffect774To775(packet.payload());
            case PLAY_ENTITY_SOUND_EFFECT ->
                    ChunkTranslator.translateEntitySoundEffect774To775(packet.payload());
            case PLAY_STOP_SOUND ->
                    ChunkTranslator.translateStopSound774To775(packet.payload());
            case PLAY_WORLD_PARTICLES ->
                    ChunkTranslator.translateWorldParticles774To775(packet.payload());
            case PLAY_ENTITY_EFFECT ->
                    ChunkTranslator.translateEntityEffect774To775(packet.payload());
            case PLAY_REMOVE_ENTITY_EFFECT ->
                    ChunkTranslator.translateRemoveEntityEffect774To775(packet.payload());
            case PLAY_DAMAGE_EVENT ->
                    ChunkTranslator.translateDamageEvent774To775(packet.payload());
            case PLAY_ENTITY_UPDATE_ATTRIBUTES ->
                    ChunkTranslator.translateUpdateAttributes774To775(packet.payload());
            case PLAY_CHUNK_DATA ->
                    ChunkTranslator.translateChunkData774To775(packet.payload());
            case PLAY_BLOCK_CHANGE ->
                    ChunkTranslator.translateBlockChange774To775(packet.payload());
            case PLAY_MULTI_BLOCK_CHANGE ->
                    ChunkTranslator.translateMultiBlockChange774To775(packet.payload());
            case PLAY_BLOCK_ENTITY_DATA ->
                    ChunkTranslator.translateBlockEntityData774To775(packet.payload());
            case PLAY_PLAYER_INFO_UPDATE ->
                    PlayTranslator.translatePlayerInfo774To775(packet.payload());
            case PLAY_PLAYER_CHAT ->
                    PlayTranslator.translatePlayerChat774To775(packet.payload());
            case PLAY_SYSTEM_CHAT, PLAY_PROFILELESS_CHAT ->
                    packet.payload(); // 同形式
            case PLAY_SYNCHRONIZE_PLAYER_POSITION, PLAY_UPDATE_TIME, PLAY_SET_SLOT_STATE ->
                    packet.payload(); // 同形式
            case PLAY_HELD_ITEM_SLOT ->
                    PlayTranslator.translateSetHeldItem774To775(packet.payload());
            case PLAY_EXPLOSION ->
                    ChunkTranslator.translateExplosion774To775(packet.payload());
            // ロケーターバー／ダイアログは774にもあるためそのまま通す（同形式）。
            case PLAY_TRACKED_WAYPOINT, PLAY_SHOW_DIALOG, PLAY_CLEAR_DIALOG ->
                    packet.payload();
            default -> null;
        };
        if (translated == null) {
            logUntranslatedOnce(name);
            return null;
        }
        OptionalInt id775 = PacketTranslator.translateId(name,
                ProtocolVersion.V1_21_11, ProtocolVersion.V26_1);
        if (id775.isEmpty()) {
            logUntranslatedOnce(name);
            return null;
        }
        return new MinecraftPacket(id775.getAsInt(), translated);
    }

    /**
     * 内部774→775クライアントのプレイ状態（サーバー→クライアント）。
     * 774→775へ変換してそのまま送る。
     */
    private Outcome handleServerPlay774To775(MinecraftPacket packet) {
        MinecraftPacket as775 = to775ServerPacket(packet);
        if (as775 == null) {
            return Outcome.drop();
        }
        return Outcome.toClient(as775);
    }

    /**
     * 内部773→775クライアントのプレイ状態（サーバー→クライアント）。
     * 773→774→775へ二段で変換する。
     */
    private Outcome handleServerPlay773To775(MinecraftPacket packet) {
        Outcome to774 = handleServerPlay773To774(packet);
        if (to774.toClient().isEmpty()) {
            return to774;
        }
        return handleServerPlay774To775(to774.toClient().get(0));
    }

    /**
     * 内部772→775クライアントのプレイ状態（サーバー→クライアント）。
     * 772→774→775へ二段で変換する。
     */
    private Outcome handleServerPlay772To775(MinecraftPacket packet) {
        Outcome to774 = handleServerPlay772To774(packet);
        if (to774.toClient().isEmpty()) {
            return to774;
        }
        return handleServerPlay774To775(to774.toClient().get(0));
    }

    /**
     * 内部771→775クライアントのプレイ状態（サーバー→クライアント）。
     * 771→774→775へ二段で変換する。
     */
    private Outcome handleServerPlay771To775(MinecraftPacket packet) {
        Outcome to774 = handleServerPlay771To774(packet);
        if (to774.toClient().isEmpty()) {
            return to774;
        }
        return handleServerPlay774To775(to774.toClient().get(0));
    }

    /**
     * 内部770→775クライアントのプレイ状態（サーバー→クライアント）。
     * 770→774→775へ二段で変換する。
     */
    private Outcome handleServerPlay770To775(MinecraftPacket packet) {
        Outcome to774 = handleServerPlay770To774(packet);
        if (to774.toClient().isEmpty()) {
            return to774;
        }
        return handleServerPlay774To775(to774.toClient().get(0));
    }

    /**
     * 内部769→775クライアントのプレイ状態（サーバー→クライアント）。
     * 769→774→775へ二段で変換する。
     */
    private Outcome handleServerPlay769To775(MinecraftPacket packet) {
        Outcome to774 = handleServerPlay769To774(packet);
        if (to774.toClient().isEmpty()) {
            return to774;
        }
        return handleServerPlay774To775(to774.toClient().get(0));
    }

    /**
     * 内部768→775クライアントのプレイ状態（サーバー→クライアント）。
     * 768→774→775へ二段で変換する。
     */
    private Outcome handleServerPlay768To775(MinecraftPacket packet) {
        Outcome to774 = handleServerPlay768To774(packet);
        if (to774.toClient().isEmpty()) {
            return to774;
        }
        return handleServerPlay774To775(to774.toClient().get(0));
    }

    /**
     * 内部767→775クライアントのプレイ状態（サーバー→クライアント）。
     * 767→774→775へ二段で変換する。
     */
    private Outcome handleServerPlay767To775(MinecraftPacket packet) {
        Outcome to774 = handleServerPlay767To774(packet);
        if (to774.toClient().isEmpty()) {
            return to774;
        }
        return handleServerPlay774To775(to774.toClient().get(0));
    }

    /**
     * 内部766→775クライアントのプレイ状態（サーバー→クライアント）。
     * 766→774→775へ二段で変換する。
     */
    private Outcome handleServerPlay766To775(MinecraftPacket packet) {
        Outcome to774 = handleServerPlay766To774(packet);
        if (to774.toClient().isEmpty()) {
            return to774;
        }
        return handleServerPlay774To775(to774.toClient().get(0));
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
    for name in ("CLIENT_PLAY_774", "SERVER_PLAY_774", "CONFIG_774"):
        blocks[name] = ast.literal_eval(assigns[name])

    new_edits = []
    for name, old, new in edits:
        if new.startswith(old) and new != old:
            kind = "APPEND"
            added = new[len(old):]
            new_edits.append((name.replace("773", "774"), new, new + shift(added)))
        elif old in new and new != old:
            kind = "CONTAINS-OLD"
            assert new.endswith(old), f"{name}: CONTAINS-OLD but not suffix form"
            prefix = new[: len(new) - len(old)]
            new_edits.append((name.replace("773", "774"), new, shift(prefix) + new))
        else:
            kind = "REPLACE"
            new_edits.append((name.replace("773", "774"), new, shift(new)))
        print(f"{kind}: {name} -> {new_edits[-1][0]}", file=sys.stderr)

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
            print(f"SKIP: {name}（773で適用済みのため774パッチから外す）", file=sys.stderr)
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
    # 773ステージと違い、774ではメソッド名の衝突は無い（shift 後の
    # to774ClientPacket／to773ClientPacketFrom774／to773ServerPacketFrom774
    # はすべて新規名のため、改名の手直しは要らない）。
    new_blocks = {}
    for name in ("CLIENT_PLAY_774", "SERVER_PLAY_774", "CONFIG_774"):
        shifted = shift(blocks[name])
        new_name = name.replace("774", "775")
        if new_name in FIX_BLOCKS:
            shifted = apply_subs(shifted, FIX_BLOCKS[new_name], f"block {new_name}")
        if new_name in FIX_BLOCKS_ALL:
            shifted = apply_replace_all(shifted, FIX_BLOCKS_ALL[new_name], f"block {new_name}")
        new_blocks[new_name] = shifted

    # ヘッダ（手書き）と EDITS を出力し、ブロックは後段の手作業用に
    # shifted 版をそのまま載せる。
    out_lines = []
    out_lines.append('''#!/usr/bin/env python3
"""ProxySession.java へ Minecraft 26.1（プロトコル775）の対応を入れる。

774パッチ（patch-proxysession-774.py）を1段ずらしたもの。生成元は
tools/gen-775-proxysession.py で、EDITS・3ブロック・INSERTS を機械的に
ずらし、775特有の箇所だけ手で直した。

775の要点（774からの差分）:
- クライアント行きのプレイは新規パケットの挿入で大きくずれる。
  game_rule_values 0x27・low_disk_space_warning 0x32 の挿入で
  0x27 以降が+1、0x32 以降がさらに+1（計+2）。
  Join Game 0x30→0x31、Spawn Position 0x5F→0x61、Chunk Data 0x2C→0x2D、
  Keep Alive 0x2B→0x2C、Ping 0x3B→0x3D、Start Configuration 0x74→0x76、
  Disconnect 0x20（変わらない）、Respawn 0x50→0x52、
  Add Resource Pack 0x4F→0x51。
  新規の game_rule_values・low_disk_space_warning は旧クライアント向けに落とす。
- サーバー行きのプレイも新規パケットの挿入でずれる。
  attack 0x01・set_game_rule 0x39・spectate_entity 0x3e の挿入で
  0x01 以降が+1、0x39 以降がさらに+1、0x3e 以降がさらに+1（計+3）。
  Window Click 0x11→0x12、Set Creative Slot 0x37→0x38、
  Chat Message 0x08→0x09、Chat Command Signed 0x07→0x08、
  Keep Alive 0x1B→0x1C、Pong 0x2C→0x2D。
  新規の attack・set_game_rule・spectate_entity は旧サーバー向けに落とす。
- 設定フェーズは番号が変わらない。新規パケットは無い。
- pack_format はリソース 84・データ 101（774 はリソース 75・データ 94）。
- チャットの checksum（u8）は770・771・772・773・774・775ともあるため、775⇔774では
  付け足しも剥がしもししない（番号だけ付け替える）。770⇔771の変換器は
  そのまま（769には checksum が無い）。
- Vehicle Move の onGround は769・770・771・772・773・774・775ともある（768に無い）。
  775⇔774では素通しする。
- Window Click は771・772・773・774・775とも HashedSlot のため落とす。Set Creative Slot は
  774・775とも UntrustedSlot のため、アイテム／成分を付け替えるだけ。
- Player Chat は先頭に globalIndex が770・771・772・773・774・775ともあるため素通しする。
  769サーバーからの player_chat にはプロキシが 0 から数えて付ける
  （770／771／772／773／774／775クライアント共用の chatGlobalIndex）。
- 設定フェーズは774と同形（同じ番号・同じ形式）で中継する。
  Registry Data の並び替えだけ 774→775 が要る（minecraft:dialog は
  774・775ともある。jukebox_song・絵画は同一。銅ゴーレム像などの
  ブロックエンティティは Registry Data には出ない）。
  Show Dialog（config 0x12）／Clear Dialog（0x11）は旧クライアント向けに
  落とす。Custom Click Action（config 0x08）は775サーバーへ通し、
  旧サーバー向けに落とす。Custom Report Details／Server Links
  （config サーバー行き）は772で削除されたため、775サーバー向けも落とす。
- 経験値オーブは770・771・772・773・774・775とも通常の Spawn Entity（type experience_orb）。
- 内部775サーバー＋775クライアントはネイティブ（素通し）。

使い方: repo ルートで `python3 detergentmc-protocol/tools/patch-proxysession-775.py`
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
    for name in ("CLIENT_PLAY_775", "SERVER_PLAY_775", "CONFIG_775"):
        out_lines.append(f"{name} = r'''{new_blocks[name]}'''")
        out_lines.append("")
    out_lines.append(f"SERVER_PLAY_775_TO = r'''{SERVER_PLAY_775_TO}'''")
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
    # CONFIG_ANCHOR：773パッチの rest は既に置換済みの anchor（default -> drop）
    # で、shift が警告文の 772→773 と CONFIG_773→CONFIG_774 を済ませている
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
