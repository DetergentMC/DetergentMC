#!/usr/bin/env python3
"""patch-proxysession-772.py から patch-proxysession-773.py を生成する。

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

SRC = Path("/home/hatch/workspace/detergentmc/detergentmc-protocol/tools/patch-proxysession-772.py")
OUT = Path("/home/hatch/workspace/detergentmc/detergentmc-protocol/tools/patch-proxysession-773.py")

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
IS_CHAIN = re.compile(r"((?:is(?:Server)?\d{3}\(\)\s*\|\|\s*)+is(?:Server)?772\(\))")
IS_CHAIN_770 = re.compile(r"((?:is(?:Server)?\d{3}\(\)\s*\|\|\s*)+is(?:Server)?771\(\))")


def shift_chain(m, kind):
    sep = "→" if kind == "arrow" else "⇔"
    nums = m.group(0).split(sep)
    if len(nums) == 2:
        # 2要素のペアは1段ずらす（771→772 は 772→773 になる）。
        return sep.join(str(int(n) + 1) for n in nums)
    if len(nums) >= 3 and nums[0] == "772":
        # 772から始まる多段チェーンは段が1つ増える（772→771→770 は
        # 773→772→771→770 になる）。末端（765/766/767）は固定。
        return "773" + sep + m.group(0)
    if nums[-1] == "772":
        return m.group(0) + sep + "773"
    warnings.append(f"arrow chain {m.group(0)} -> +1 each")
    return sep.join(str(int(n) + 1) for n in nums)


def shift_list(m, sep):
    nums = m.group(0).split(sep)
    if nums[-1] == "772":
        return m.group(0) + sep + "773"
    if nums[-1] == "771":
        # 新バージョンの一つ下まで列挙しているリストは 772 を追加する。
        return m.group(0) + sep + "772"
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
    # 1b) isチェーン（is764() || ... || is772()）に is773() を追加し、
    # そのまま保護する（後の数字シフトで二重にずれないように）。
    def is_chain_sub(m):
        chain = m.group(1)
        last = re.search(r"is(Server)?772\(\)$", chain)
        suffix = "isServer773()" if last.group(1) else "is773()"
        key = f"\x02{_counter[0]}\x03"
        _counter[0] += 1
        is_chain_holders[key] = chain + " || " + suffix
        return key

    is_chain_holders = {}
    text = IS_CHAIN.sub(is_chain_sub, text)

    def is_chain_sub_770(m):
        chain = m.group(1)
        last = re.search(r"is(Server)?771\(\)$", chain)
        suffix = "isServer772()" if last.group(1) else "is772()"
        key = f"\x02{_counter[0]}\x03"
        _counter[0] += 1
        is_chain_holders[key] = chain + " || " + suffix
        return key

    text = IS_CHAIN_770.sub(is_chain_sub_770, text)
    holders = {**h0, **h1, **h2, **h3, **h4, **is_chain_holders}
    # 2) バージョン文字列
    text = text.replace("1.21.7／1.21.8", "1.21.9／1.21.10")
    text = text.replace("1.21.7", "1.21.9")
    text = text.replace("1.21.8", "1.21.10")
    text = text.replace("1.21.6", "1.21.7")
    text = text.replace("1.21.5", "1.21.6")
    text = text.replace("1.21.4", "1.21.5")
    text = text.replace("1.21.2／1.21.3", "1.21.4")
    text = text.replace("1.21.2", "1.21.4")
    text = text.replace("V1_21_7", "V1_21_9")
    text = text.replace("V1_21_6", "V1_21_7")
    text = text.replace("V1_21_5", "V1_21_6")
    text = text.replace("V1_21_4", "V1_21_5")
    text = text.replace("V1_21_2", "V1_21_4")
    text = text.replace("PROTOCOL_772", "PROTOCOL_773")
    text = text.replace("PROTOCOL_771", "PROTOCOL_772")
    text = text.replace("PROTOCOL_770", "PROTOCOL_771")
    text = text.replace("PROTOCOL_769", "PROTOCOL_770")
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
    text = re.sub(r"pack(?:770|771|772|773)", pack_sub, text)
    holders.update(pack_holders)
    # PROTOCOL_77x も数字シフトから保護する（バージョン置き換えで既に
    # ずらし済みのため。二重にかかると PROTOCOL_770→772 になる）。
    text = re.sub(r"PROTOCOL_77[0-9]", pack_sub, text)
    holders.update(pack_holders)
    # 3) 単独の数字。\b は日本語文字を \w と見なすため使えないので、
    # 数字の前後を見る lookaround を使う（識別子内の 769 も対象にする）。
    # 16進リテラル (0x..）は数字続きなので対象外になる。
    text = re.sub(r"(?<!\d)772(?!\d)", "\x01772", text)
    text = re.sub(r"(?<!\d)771(?!\d)", "772", text)
    text = text.replace("\x01772", "773")
    # 4) 復元
    return restore(text, holders)


FIX_EDITS = {
    # name -> [(old_sub, new_sub), ...] を shift した NEW テキストに適用する
    # （各1回一致。shift の誤変換の修正）。
    "constructor javadoc": [
        ("769＝1.21.4、770＝1.21.5、771＝1.21.6、772＝1.21.7）", "769＝1.21.4、770＝1.21.5、771＝1.21.6、772＝1.21.7、773＝1.21.9／1.21.10）"),
    ],
    "constructor validation": [
        # shift は V1_21_2 を V1_21_4 にずらして重複させる（768の検査が欠ける）。
        # 検査の一覧に V1_21_2（768）を戻す。
        ("&& serverProtocol != ProtocolVersion.V1_21\n"
         "                && serverProtocol != ProtocolVersion.V1_21_4",
         "&& serverProtocol != ProtocolVersion.V1_21\n"
         "                && serverProtocol != ProtocolVersion.V1_21_2\n"
         "                && serverProtocol != ProtocolVersion.V1_21_4"),
        ("769 (1.21.4), 770 (1.21.5), 771 (1.21.6) or 772 (1.21.7): ",
         "769 (1.21.4), 770 (1.21.5), 771 (1.21.6), 772 (1.21.7) or 773 (1.21.9/1.21.10): "),
    ],
    "serverProtocol() javadoc": [
        ("769＝1.21.4、770＝1.21.5、771＝1.21.6、772＝1.21.7）。", "769＝1.21.4、770＝1.21.5、771＝1.21.6、772＝1.21.7、773＝1.21.9／1.21.10）。"),
    ],
    "pack773 field": [
        # 773の pack_format は 69（772の 64 のままになっている）。
        ("773クライアントに送るリソースパック（pack_format 64）",
         "773クライアントに送るリソースパック（pack_format 69）"),
    ],
    "packOverrideForClient javadoc": [
        # shift は pack_format の数字をずらさない（773=69、772=64、771=63、770=55、769=46）。
        # 770の行も shift で 770→pack771 に潰れているため戻す（769→pack770 も）。
        ("773→pack773\n     * （pack_format 64）、772→pack772\n"
         "     * （pack_format 63）、770→pack771\n"
         "     * （pack_format 55）、769→pack770\n"
         "     * （pack_format 46）、768→pack768",
         "773→pack773\n     * （pack_format 69）、772→pack772\n"
         "     * （pack_format 64）、771→pack771\n"
         "     * （pack_format 63）、770→pack770\n"
         "     * （pack_format 55）、769→pack769\n"
         "     * （pack_format 46）、768→pack768"),
    ],
    "packOverrideForClient branch": [
        # shift は 773分岐の代替チェーンから pack769 を欠落させる
        # （pack770 の次が pack768 になっている）。pack769 を戻す。
        ("pack773 != null ? pack773\n"
         "                    : pack772 != null ? pack772\n"
         "                    : pack771 != null ? pack771\n"
         "                    : pack770 != null ? pack770\n"
         "                    : pack768 != null ? pack768",
         "pack773 != null ? pack773\n"
         "                    : pack772 != null ? pack772\n"
         "                    : pack771 != null ? pack771\n"
         "                    : pack770 != null ? pack770\n"
         "                    : pack769 != null ? pack769\n"
         "                    : pack768 != null ? pack768"),
    ],
    # "startConfigurationClientId"：773の shift は既に正しいため FIX は不要。
    # "spawnPositionClientId"：773の shift は既に正しいため FIX は不要。
    # "keepAliveClientId"：773の shift は既に正しいため FIX は不要。
    # "pingClientId"：773の shift は既に正しいため FIX は不要。
    # "handleServerPlay dispatch"：773の shift は既に正しいため FIX は不要。
    "handleServerConfiguration 763 branch": [
        # shift は isServer769 の分岐を isServer770 にずらさない
        # （770の分岐が欠ける）。770の分岐を戻す。
        ("            if (isServer769()) {\n"
         "                return handleServerConfiguration763From769(packet);\n"
         "            }",
         "            if (isServer770()) {\n"
         "                return handleServerConfiguration763From770(packet);\n"
         "            }\n"
         "            if (isServer769()) {\n"
         "                return handleServerConfiguration763From769(packet);\n"
         "            }"),
    ],
    # "handleServerPlayFrom769 773 branch"：773の shift は既に正しいため FIX は不要。
    "handleServerPlayFrom766 773 branch": [
        # 段数の数え間違い：766からは七段、767からは六段。
        ("// 内部766は 766→767→768→769→770→771→772→773 の六段、内部767は 767→768→769→770→771→772→773 の五段で送る。",
         "// 内部766は 766→767→768→769→770→771→772→773 の七段、内部767は 767→768→769→770→771→772→773 の六段で送る。"),
    ],
    # "handleServerPlayFrom763 ResourcePack 773"：773の shift は既に正しいため FIX は不要。
    # 773で削除された、とシフトされてしまうが、削除されたのは771。

    "synthesized config registry loop": [
        ('                    byte[] payload770to772 =\n                            PlayTranslator.translateRegistryData770To772(payload769to770);\n                    registryPayload =\n                            PlayTranslator.translateRegistryData772To773(payload770to772);',
         '                    byte[] payload770to771 =\n                            PlayTranslator.translateRegistryData770To771(payload769to770);\n                    byte[] payload771to772 =\n                            PlayTranslator.translateRegistryData771To772(payload770to771);\n                    registryPayload =\n                            PlayTranslator.translateRegistryData772To773(payload771to772);'),
        ('// 773：766→767→768→769→770→771→772→773へ六段で並べ替える。',
         '// 773：766→767→768→769→770→771→772→773へ七段で並べ替える。'),
        ('                if (is772()) {\n                    // 772：767→768→769→770→771→772へ五段で並べ替える。\n                    byte[] payload766to767 =\n                            PlayTranslator.translateRegistryData766To767(registryPayload);\n                    byte[] payload767to768 =\n                            PlayTranslator.translateRegistryData767To768(payload766to767);\n                    byte[] payload768to769 =\n                            PlayTranslator.translateRegistryData768To769(payload767to768);\n                    byte[] payload769to770 =\n                            PlayTranslator.translateRegistryData769To770(payload768to769);\n                    registryPayload =\n                            PlayTranslator.translateRegistryData770To772(payload769to770);',
         '                if (is772()) {\n                    // 772：766→767→768→769→770→771→772へ六段で並べ替える。\n                    byte[] payload766to767 =\n                            PlayTranslator.translateRegistryData766To767(registryPayload);\n                    byte[] payload767to768 =\n                            PlayTranslator.translateRegistryData767To768(payload766to767);\n                    byte[] payload768to769 =\n                            PlayTranslator.translateRegistryData768To769(payload767to768);\n                    byte[] payload769to770 =\n                            PlayTranslator.translateRegistryData769To770(payload768to769);\n                    byte[] payload770to771 =\n                            PlayTranslator.translateRegistryData770To771(payload769to770);\n                    registryPayload =\n                            PlayTranslator.translateRegistryData771To772(payload770to771);'),
    ],

}


# (old, new) を丸ごと置き換える編集。shift した (old, new) では
# アンカーが live に無かったり、意味が変わったりするもの。
FIX_EDITS_FULL = {
    "handleServerConfiguration 763 branch": (
        '        if (is763()) {\n            if (isServer772()) {\n                return handleServerConfiguration763From772(packet);\n            }\n            if (isServer771()) {\n                return handleServerConfiguration763From771(packet);\n            }\n            if (isServer770()) {\n                return handleServerConfiguration763From770(packet);\n            }\n            if (isServer769()) {\n                return handleServerConfiguration763From769(packet);\n            }\n            if (isServer768()) {\n                return handleServerConfiguration763From768(packet);\n            }',
        '        if (is763()) {\n            if (isServer773()) {\n                return handleServerConfiguration763From773(packet);\n            }\n            if (isServer772()) {\n                return handleServerConfiguration763From772(packet);\n            }\n            if (isServer771()) {\n                return handleServerConfiguration763From771(packet);\n            }\n            if (isServer770()) {\n                return handleServerConfiguration763From770(packet);\n            }\n            if (isServer769()) {\n                return handleServerConfiguration763From769(packet);\n            }\n            if (isServer768()) {\n                return handleServerConfiguration763From768(packet);\n            }'),
    "handleServerPlayFrom771 Disconnect 773": (
        '            if (is771()) {\n                // 771クライアント：Disconnect は 0x1C（NBT）のまま。\n                return new Outcome(List.of(),\n                        List.of(new MinecraftPacket(0x1C, packet.payload())), false, true);\n            }\n            if (is772()) {\n                // 772クライアント：Disconnect は 0x1C（NBT）のまま。\n                return new Outcome(List.of(),\n                        List.of(new MinecraftPacket(0x1C, packet.payload())), false, true);\n            }\n            if (is766() || is767() || is768() || is769() || is770()) {\n                // 766／767／768／769／770クライアント：Disconnect は 0x1D（NBT）のまま。',
        '            if (is771()) {\n                // 771クライアント：Disconnect は 0x1C（NBT）のまま。\n                return new Outcome(List.of(),\n                        List.of(new MinecraftPacket(0x1C, packet.payload())), false, true);\n            }\n            if (is772()) {\n                // 772クライアント：Disconnect は 0x1C（NBT）のまま。\n                return new Outcome(List.of(),\n                        List.of(new MinecraftPacket(0x1C, packet.payload())), false, true);\n            }\n            if (is773()) {\n                // 773クライアント：Disconnect は 0x1C（NBT）のまま。\n                return new Outcome(List.of(),\n                        List.of(new MinecraftPacket(0x1C, packet.payload())), false, true);\n            }\n            if (is766() || is767() || is768() || is769() || is770()) {\n                // 766／767／768／769／770クライアント：Disconnect は 0x1D（NBT）のまま。'),
    "handleServerPlayFrom771 Respawn 773": (
        '// Respawn（771：0x4B。770（0x4C）と同形式のため770の変換を使い回す）。\n        if (packet.id() == 0x4B) {\n            if (is772()) {\n                // 772クライアント：Respawn は 0x4B（同形式）のまま。\n                return Outcome.toClient(new MinecraftPacket(0x4B, packet.payload()));\n            }\n            if (is770()) {',
        '// Respawn（771：0x4B。770（0x4C）と同形式のため770の変換を使い回す）。\n        if (packet.id() == 0x4B) {\n            if (is773()) {\n                // 773クライアント：Respawn は 0x4B（同形式）のまま。\n                return Outcome.toClient(new MinecraftPacket(0x4B, packet.payload()));\n            }\n            if (is772()) {\n                // 772クライアント：Respawn は 0x4B（同形式）のまま。\n                return Outcome.toClient(new MinecraftPacket(0x4B, packet.payload()));\n            }\n            if (is770()) {'),
    "handleServerJoinGame771 773 branch": (
        '    private Outcome handleServerJoinGame771(MinecraftPacket packet) {\n        if (is772()) {\n            advanceFlowSafely(() -> joinFlow.joinGameReceived());\n            List<MinecraftPacket> toClient = new ArrayList<>();\n            toClient.add(new MinecraftPacket(0x2B, packet.payload()));\n            while (!pendingPacks.isEmpty()) {\n                PendingPack pack = pendingPacks.removeFirst();\n                lastSentPack = pack;\n                toClient.add(new MinecraftPacket(0x4A,\n                        PlayTranslator.translateConfigAddResourcePack764To765(\n                                pack, packOverrideForClient())));\n            }\n            return new Outcome(List.of(), toClient, false, false);\n        }\n        if (is770()) {',
        '    private Outcome handleServerJoinGame771(MinecraftPacket packet) {\n        if (is773()) {\n            advanceFlowSafely(() -> joinFlow.joinGameReceived());\n            List<MinecraftPacket> toClient = new ArrayList<>();\n            toClient.add(new MinecraftPacket(0x2B, packet.payload()));\n            while (!pendingPacks.isEmpty()) {\n                PendingPack pack = pendingPacks.removeFirst();\n                lastSentPack = pack;\n                toClient.add(new MinecraftPacket(0x4A,\n                        PlayTranslator.translateConfigAddResourcePack764To765(\n                                pack, packOverrideForClient())));\n            }\n            return new Outcome(List.of(), toClient, false, false);\n        }\n        if (is772()) {\n            advanceFlowSafely(() -> joinFlow.joinGameReceived());\n            List<MinecraftPacket> toClient = new ArrayList<>();\n            toClient.add(new MinecraftPacket(0x2B, packet.payload()));\n            while (!pendingPacks.isEmpty()) {\n                PendingPack pack = pendingPacks.removeFirst();\n                lastSentPack = pack;\n                toClient.add(new MinecraftPacket(0x4A,\n                        PlayTranslator.translateConfigAddResourcePack764To765(\n                                pack, packOverrideForClient())));\n            }\n            return new Outcome(List.of(), toClient, false, false);\n        }\n        if (is770()) {'),
    "handleClientConfiguration 773-server guard": (
        '        if (synthesizingConfig) {\n            return handleSynthesizedClientConfig(packet);\n        }\n        // 771サーバーへは Custom Report Details（0x08）／Server Links\n        // （0x09）を送らない（771で削除された）。771クライアントの\n        // 0x08 は Custom Click Action のためこの限りではない。772クライアントの\n        // 0x08 も Custom Click Action のためこの限りではない。\n        if (isServer771() && !is771() && !is772()\n                && (packet.id() == 0x08 || packet.id() == 0x09)) {\n            return Outcome.drop();\n        }\n        // 772サーバーへは Custom Report Details（0x08）／Server Links\n        // （0x09）を送らない（771で削除された。772も同じ）。772クライアントの\n        // 0x08 は Custom Click Action のためこの限りではない。771クライアントの\n        // 0x08 も Custom Click Action のためこの限りではない。\n        if (isServer772() && !is772() && !is771()\n                && (packet.id() == 0x08 || packet.id() == 0x09)) {\n            return Outcome.drop();\n        }\n',
        '        if (synthesizingConfig) {\n            return handleSynthesizedClientConfig(packet);\n        }\n        // 771サーバーへは Custom Report Details（0x08）／Server Links\n        // （0x09）を送らない（771で削除された）。771クライアントの\n        // 0x08 は Custom Click Action のためこの限りではない。772クライアントの\n        // 0x08 も Custom Click Action のためこの限りではない。773クライアントの\n        // 0x08 も Custom Click Action のためこの限りではない。\n        if (isServer771() && !is771() && !is772() && !is773()\n                && (packet.id() == 0x08 || packet.id() == 0x09)) {\n            return Outcome.drop();\n        }\n        // 772サーバーへは Custom Report Details（0x08）／Server Links\n        // （0x09）を送らない（771で削除された。772も同じ）。772クライアントの\n        // 0x08 は Custom Click Action のためこの限りではない。771クライアントの\n        // 0x08 も Custom Click Action のためこの限りではない。773クライアントの\n        // 0x08 も Custom Click Action のためこの限りではない。\n        if (isServer772() && !is772() && !is771() && !is773()\n                && (packet.id() == 0x08 || packet.id() == 0x09)) {\n            return Outcome.drop();\n        }\n        // 773サーバーへは Custom Report Details（0x08）／Server Links\n        // （0x09）を送らない（771で削除された。773も同じ）。773クライアントの\n        // 0x08 は Custom Click Action のためこの限りではない。772クライアントの\n        // 0x08 も Custom Click Action のためこの限りではない。771クライアントの\n        // 0x08 も Custom Click Action のためこの限りではない。\n        if (isServer773() && !is773() && !is772() && !is771()\n                && (packet.id() == 0x08 || packet.id() == 0x09)) {\n            return Outcome.drop();\n        }\n'),
    # 'handleServerConfiguration 773 branch'：shift した old は772パッチの
    # new（772分岐＋769分岐）だが、live の769分岐には772ステージの手直しで
    # isServer772 の分岐が入っているためアンカーが合わない。live の772分岐の
    # 先頭をアンカーにして773分岐を丸ごと書き直す。
    "handleServerConfiguration 773 branch": (
        "        // 772クライアントは設定フェーズを中継する（番号と Registry Data\n"
        "        // の形式だけ変換する）。\n"
        "        if (is772()) {",
        "        // 773クライアントは設定フェーズを中継する（番号と Registry Data\n"
        "        // の形式だけ変換する）。\n"
        "        if (is773()) {\n"
        "            if (isServer773()) {\n"
        "                // 内部773＋773クライアントはネイティブのためここには来ない。\n"
        "                // 念のためそのまま通す。\n"
        "                return Outcome.toClient(packet);\n"
        "            }\n"
        "            if (isServer772()) {\n"
        "                return handleServerConfiguration773From772(packet);\n"
        "            }\n"
        "            if (isServer770()) {\n"
        "                return handleServerConfiguration773From770(packet);\n"
        "            }\n"
        "            if (isServer769()) {\n"
        "                return handleServerConfiguration773From769(packet);\n"
        "            }\n"
        "            if (isServer768()) {\n"
        "                return handleServerConfiguration773From768(packet);\n"
        "            }\n"
        "            if (isServer767()) {\n"
        "                return handleServerConfiguration773From767(packet);\n"
        "            }\n"
        "            if (isServer766()) {\n"
        "                return handleServerConfiguration773From766(packet);\n"
        "            }\n"
        "            return handleServerConfiguration773(packet, isServer764());\n"
        "        }\n"
        "        // 772クライアントは設定フェーズを中継する（番号と Registry Data\n"
        "        // の形式だけ変換する）。\n"
        "        if (is772()) {",
    ),
    # 以下の3件は shift の REPLACE による分岐置換の誤り（772段階では
    # 「追加」だったものが773では「置換」になり、分岐を失う）。772段階の
    # Respawn 773（FIX_EDITS_FULL）と同じ手直しで、分岐を残したまま
    # is773() 分岐を前に足す。
            "handleServerJoinGame770 773 branch": (
        '    private Outcome handleServerJoinGame770(MinecraftPacket packet) {\n        if (is772()) {\n            advanceFlowSafely(() -> joinFlow.joinGameReceived());\n            List<MinecraftPacket> toClient = new ArrayList<>();\n            toClient.add(new MinecraftPacket(0x2B, packet.payload()));\n            while (!pendingPacks.isEmpty()) {\n                PendingPack pack = pendingPacks.removeFirst();\n                lastSentPack = pack;\n                toClient.add(new MinecraftPacket(0x4A,\n                        PlayTranslator.translateConfigAddResourcePack764To765(\n                                pack, packOverrideForClient())));\n            }\n            return new Outcome(List.of(), toClient, false, false);\n        }\n        if (is771()) {\n            advanceFlowSafely(() -> joinFlow.joinGameReceived());\n            List<MinecraftPacket> toClient = new ArrayList<>();\n            toClient.add(new MinecraftPacket(0x2B, packet.payload()));\n            while (!pendingPacks.isEmpty()) {\n                PendingPack pack = pendingPacks.removeFirst();\n                lastSentPack = pack;\n                toClient.add(new MinecraftPacket(0x4A,\n                        PlayTranslator.translateConfigAddResourcePack764To765(\n                                pack, packOverrideForClient())));\n            }\n            return new Outcome(List.of(), toClient, false, false);\n        }\n',
        '    private Outcome handleServerJoinGame770(MinecraftPacket packet) {\n        if (is773()) {\n            advanceFlowSafely(() -> joinFlow.joinGameReceived());\n            List<MinecraftPacket> toClient = new ArrayList<>();\n            toClient.add(new MinecraftPacket(0x2B, packet.payload()));\n            while (!pendingPacks.isEmpty()) {\n                PendingPack pack = pendingPacks.removeFirst();\n                lastSentPack = pack;\n                toClient.add(new MinecraftPacket(0x4A,\n                        PlayTranslator.translateConfigAddResourcePack764To765(\n                                pack, packOverrideForClient())));\n            }\n            return new Outcome(List.of(), toClient, false, false);\n        }\n        if (is772()) {\n            advanceFlowSafely(() -> joinFlow.joinGameReceived());\n            List<MinecraftPacket> toClient = new ArrayList<>();\n            toClient.add(new MinecraftPacket(0x2B, packet.payload()));\n            while (!pendingPacks.isEmpty()) {\n                PendingPack pack = pendingPacks.removeFirst();\n                lastSentPack = pack;\n                toClient.add(new MinecraftPacket(0x4A,\n                        PlayTranslator.translateConfigAddResourcePack764To765(\n                                pack, packOverrideForClient())));\n            }\n            return new Outcome(List.of(), toClient, false, false);\n        }\n        if (is771()) {\n            advanceFlowSafely(() -> joinFlow.joinGameReceived());\n            List<MinecraftPacket> toClient = new ArrayList<>();\n            toClient.add(new MinecraftPacket(0x2B, packet.payload()));\n            while (!pendingPacks.isEmpty()) {\n                PendingPack pack = pendingPacks.removeFirst();\n                lastSentPack = pack;\n                toClient.add(new MinecraftPacket(0x4A,\n                        PlayTranslator.translateConfigAddResourcePack764To765(\n                                pack, packOverrideForClient())));\n            }\n            return new Outcome(List.of(), toClient, false, false);\n        }\n'),

            "handleServerPlayFrom770 Disconnect 773": (
        "            if (is772()) {\n"
        "                // 772クライアント：Disconnect は 0x1C（NBT）のまま。\n"
        "                return new Outcome(List.of(),\n"
        "                        List.of(new MinecraftPacket(0x1C, packet.payload())), false, true);\n"
        "            }\n"
        "            if (is771()) {\n"
        "                // 771クライアント：Disconnect は 0x1C（NBT）のまま。\n"
        "                return new Outcome(List.of(),\n"
        "                        List.of(new MinecraftPacket(0x1C, packet.payload())), false, true);\n"
        "            }\n"
        "            if (is770()) {\n"
        "                // 770クライアント：Disconnect は 0x1C（NBT）のまま。\n"
        "                return new Outcome(List.of(),\n"
        "                        List.of(new MinecraftPacket(0x1C, packet.payload())), false, true);\n"
        "            }\n"
        "            if (is766() || is767() || is768() || is769()) {\n"
        "                // 766／767／768／769クライアント：Disconnect は 0x1D（NBT）のまま。",
        "            if (is773()) {\n"
        "                // 773クライアント：Disconnect は 0x1C（NBT）のまま。\n"
        "                return new Outcome(List.of(),\n"
        "                        List.of(new MinecraftPacket(0x1C, packet.payload())), false, true);\n"
        "            }\n"
        "            if (is772()) {\n"
        "                // 772クライアント：Disconnect は 0x1C（NBT）のまま。\n"
        "                return new Outcome(List.of(),\n"
        "                        List.of(new MinecraftPacket(0x1C, packet.payload())), false, true);\n"
        "            }\n"
        "            if (is771()) {\n"
        "                // 771クライアント：Disconnect は 0x1C（NBT）のまま。\n"
        "                return new Outcome(List.of(),\n"
        "                        List.of(new MinecraftPacket(0x1C, packet.payload())), false, true);\n"
        "            }\n"
        "            if (is770()) {\n"
        "                // 770クライアント：Disconnect は 0x1C（NBT）のまま。\n"
        "                return new Outcome(List.of(),\n"
        "                        List.of(new MinecraftPacket(0x1C, packet.payload())), false, true);\n"
        "            }\n"
        "            if (is766() || is767() || is768() || is769()) {\n"
        "                // 766／767／768／769クライアント：Disconnect は 0x1D（NBT）のまま。"),
    # "synthesized add-pack 773 id"：773の shift は三項を
    # is770() || is771() || is772() || is773() に正しく伸ばすため FULL は不要。
    # 'handleServerPlayFrom769 Respawn 773' / 'handleServerPlayFrom768
    # Respawn 773'：shift は is772 分岐を is773 に書き換えてしまい、
    # is772（From769）／is769（From768）の分岐を消してしまう。
    # 772の分岐（From768は769の分岐も）を残したまま773分岐を前に足す。
    "handleServerPlayFrom769 Respawn 773": (
        "        // Respawn（769：0x4C。768と同形式のため768の変換を使い回す）。\n"
        "        if (packet.id() == 0x4C) {\n"
        "            if (is772()) {\n"
        "                // 772クライアント：Respawn は 0x4B（同形式）のまま。\n"
        "                return Outcome.toClient(new MinecraftPacket(0x4B, packet.payload()));\n"
        "            }\n"
        "            if (is771()) {\n"
        "                // 771クライアント：Respawn は 0x4B（同形式）のまま。\n"
        "                return Outcome.toClient(new MinecraftPacket(0x4B, packet.payload()));\n"
        "            }\n"
        "            if (is770()) {\n"
        "                // 770クライアント：Respawn は 0x4B（同形式）のまま。\n"
        "                return Outcome.toClient(new MinecraftPacket(0x4B, packet.payload()));\n"
        "            }\n"
        "            if (is768()) {",
        "        // Respawn（769：0x4C。768と同形式のため768の変換を使い回す）。\n"
        "        if (packet.id() == 0x4C) {\n"
        "            if (is773()) {\n"
        "                // 773クライアント：Respawn は 0x4B（同形式）のまま。\n"
        "                return Outcome.toClient(new MinecraftPacket(0x4B, packet.payload()));\n"
        "            }\n"
        "            if (is772()) {\n"
        "                // 772クライアント：Respawn は 0x4B（同形式）のまま。\n"
        "                return Outcome.toClient(new MinecraftPacket(0x4B, packet.payload()));\n"
        "            }\n"
        "            if (is771()) {\n"
        "                // 771クライアント：Respawn は 0x4B（同形式）のまま。\n"
        "                return Outcome.toClient(new MinecraftPacket(0x4B, packet.payload()));\n"
        "            }\n"
        "            if (is770()) {\n"
        "                // 770クライアント：Respawn は 0x4B（同形式）のまま。\n"
        "                return Outcome.toClient(new MinecraftPacket(0x4B, packet.payload()));\n"
        "            }\n"
        "            if (is768()) {",
    ),
    "handleServerPlayFrom768 Respawn 773": (
        "        // Respawn（768：0x4C）。\n"
        "        if (packet.id() == 0x4C) {\n"
        "            if (is772()) {\n"
        "                // 772クライアント：Respawn は 0x4B（同形式）のまま。\n"
        "                return Outcome.toClient(new MinecraftPacket(0x4B, packet.payload()));\n"
        "            }\n"
        "            if (is771()) {\n"
        "                // 771クライアント：Respawn は 0x4B（同形式）のまま。\n"
        "                return Outcome.toClient(new MinecraftPacket(0x4B, packet.payload()));\n"
        "            }\n"
        "            if (is770()) {\n"
        "                // 770クライアント：Respawn は 0x4B（同形式）のまま。\n"
        "                return Outcome.toClient(new MinecraftPacket(0x4B, packet.payload()));\n"
        "            }\n"
        "            if (is769()) {",
        "        // Respawn（768：0x4C）。\n"
        "        if (packet.id() == 0x4C) {\n"
        "            if (is773()) {\n"
        "                // 773クライアント：Respawn は 0x4B（同形式）のまま。\n"
        "                return Outcome.toClient(new MinecraftPacket(0x4B, packet.payload()));\n"
        "            }\n"
        "            if (is772()) {\n"
        "                // 772クライアント：Respawn は 0x4B（同形式）のまま。\n"
        "                return Outcome.toClient(new MinecraftPacket(0x4B, packet.payload()));\n"
        "            }\n"
        "            if (is771()) {\n"
        "                // 771クライアント：Respawn は 0x4B（同形式）のまま。\n"
        "                return Outcome.toClient(new MinecraftPacket(0x4B, packet.payload()));\n"
        "            }\n"
        "            if (is770()) {\n"
        "                // 770クライアント：Respawn は 0x4B（同形式）のまま。\n"
        "                return Outcome.toClient(new MinecraftPacket(0x4B, packet.payload()));\n"
        "            }\n"
        "            if (is769()) {",
    ),
    # 'chatGlobalIndex field'：shift した old は pack772 ブロック＋772用
    # フィールドだが、'pack773 field' 編集が先に pack773 ブロックを挟むため
    # アンカーが合わず、new もフィールドを重複させる。pack773 ブロックと
    # 旧772用カウンタをアンカーにして、770／771／772／773共用のカウンタを1本だけ置く。
    "chatGlobalIndex field": (
        "    /**\n"
        "     * 773クライアントに送るリソースパック（pack_format 69）。\n"
        "     * 内部が773でない場合に使う差し替え用。内部が773の場合はバニラが\n"
        "     * 送ってきたパックをそのまま出すため null のままでよい。\n"
        "     * 出し分けの基準はクライアントのプロトコルである\n"
        "     * （{@link #packOverrideForClient()} 参照）。\n"
        "     */\n"
        "    private ResourcePack pack773;\n"
        "\n"
        "    /**\n"
        "     * 770／771／772クライアントへ送る player_chat に付ける globalIndex。\n"
        "     * 769サーバーは globalIndex を送ってこないため、プロキシが\n"
        "     * セッションごとに 0 から数える。\n"
        "     */\n"
        "    private int chatGlobalIndex = 0;",
        "    /**\n"
        "     * 773クライアントに送るリソースパック（pack_format 69）。\n"
        "     * 内部が773でない場合に使う差し替え用。内部が773の場合はバニラが\n"
        "     * 送ってきたパックをそのまま出すため null のままでよい。\n"
        "     * 出し分けの基準はクライアントのプロトコルである\n"
        "     * （{@link #packOverrideForClient()} 参照）。\n"
        "     */\n"
        "    private ResourcePack pack773;\n"
        "\n"
        "    /**\n"
        "     * 770／771／772／773クライアントへ送る player_chat に付ける globalIndex。\n"
        "     * 769サーバーは globalIndex を送ってこないため、プロキシが\n"
        "     * セッションごとに 0 から数える。\n"
        "     */\n"
        "    private int chatGlobalIndex = 0;",
    ),
    # 'synthesized add-pack'：shift した版はチェーンとコメントだけを
    # 変えるが、三項の 0x4A 側も is773() を足す必要がある。3行まとめて
    # 置き換える（後続の 'synthesized add-pack 773 id' は SKIP）。
    "synthesized add-pack": (
        "            if (is768() || is769() || is770() || is771() || is772()) {\n"
        "                // プレイ状態の Add Resource Pack は 0x4B（PLAY_ADD_RESOURCE_PACK）。772は 0x4A。\n"
        "                toClient.add(new MinecraftPacket(is770() || is771() || is772() ? 0x4A : 0x4B,",
        "            if (is768() || is769() || is770() || is771() || is772() || is773()) {\n"
        "                // プレイ状態の Add Resource Pack は 0x4B（PLAY_ADD_RESOURCE_PACK）。773は 0x4A。\n"
        "                toClient.add(new MinecraftPacket(is770() || is771() || is772() || is773() ? 0x4A : 0x4B,"),

    # 'handleServerPlayFrom770 Respawn 773'：772パッチの new は
    # is769() 単独分岐だが、live は772段階の手直しで is768() || is769()
    # になっている（コメント「772段階で修正」）。live に合わせて丸ごと書き直す。
    # コメントの 770：0x4B は From770 のものなので変えない。
    "handleServerPlayFrom770 Respawn 773": (
        "        // Respawn（770：0x4B。769（0x4C）と同形式のため769の変換を使い回す）。\n"
        "        if (packet.id() == 0x4B) {\n"
        "            if (is772()) {\n"
        "                // 772クライアント：Respawn は 0x4B（同形式）のまま。\n"
        "                return Outcome.toClient(new MinecraftPacket(0x4B, packet.payload()));\n"
        "            }\n"
        "            if (is771()) {\n"
        "                // 771クライアント：Respawn は 0x4B（同形式）のまま。\n"
        "                return Outcome.toClient(new MinecraftPacket(0x4B, packet.payload()));\n"
        "            }\n"
        "            if (is768() || is769()) {",
        "        // Respawn（770：0x4B。769（0x4C）と同形式のため769の変換を使い回す）。\n"
        "        if (packet.id() == 0x4B) {\n"
        "            if (is773()) {\n"
        "                // 773クライアント：Respawn は 0x4B（同形式）のまま。\n"
        "                return Outcome.toClient(new MinecraftPacket(0x4B, packet.payload()));\n"
        "            }\n"
        "            if (is772()) {\n"
        "                // 772クライアント：Respawn は 0x4B（同形式）のまま。\n"
        "                return Outcome.toClient(new MinecraftPacket(0x4B, packet.payload()));\n"
        "            }\n"
        "            if (is771()) {\n"
        "                // 771クライアント：Respawn は 0x4B（同形式）のまま。\n"
        "                return Outcome.toClient(new MinecraftPacket(0x4B, packet.payload()));\n"
        "            }\n"
        "            if (is768() || is769()) {"),

}


# 773パッチから外す編集。772ステージで既に live へ適用済みの修正で、
# shift すると存在しないメソッド（translatePlayerInfo772To768 等）を
# 呼ぶ壊れた編集になってしまうため。
SKIP_EDITS = {
    "to768ServerPacket chat passthrough",
    "handleServerPlay768To767 chat passthrough",
    "handleServerPlay767To766 system_chat passthrough",    "synthesized add-pack 773 id",

}


ADD_EDITS = [
    # 772サーバー→773クライアント：一般プレイ状態の変換。
    # （shift では作れない。handleServerPlayFrom772 に773分岐が無いため。）
        ("handleServerPlayFrom772 773 branch",
     '        // 770クライアント：772→771へ変換してから771→770へ。\n        if (is770()) {\n            MinecraftPacket as771 = to771ServerPacketFrom772(packet);\n            if (as771 == null) {\n                return Outcome.drop();\n            }\n            return handleServerPlay771To770(as771);\n        }',
     '        // 773クライアント：772→773へ変換する。\n        if (is773()) {\n            return handleServerPlay772To773(packet);\n        }\n        // 770クライアント：772→771へ変換してから771→770へ。\n        if (is770()) {\n            MinecraftPacket as771 = to771ServerPacketFrom772(packet);\n            if (as771 == null) {\n                return Outcome.drop();\n            }\n            return handleServerPlay771To770(as771);\n        }'),
    # 772サーバー→773クライアント：Respawn（772：0x4B、773：0x4B、同形式）。
        ("handleServerPlayFrom772 Respawn 773",
     '        // Respawn（772：0x4B。771（0x4B）と同形式のためそのまま送る）。\n        if (packet.id() == 0x4B) {\n            if (is771()) {\n                // 771クライアント：Respawn は 0x4B（同形式）のまま。\n                return Outcome.toClient(new MinecraftPacket(0x4B, packet.payload()));\n            }\n            if (is770()) {',
     '        // Respawn（772：0x4B。771（0x4B）と同形式のためそのまま送る）。\n        if (packet.id() == 0x4B) {\n            if (is771()) {\n                // 771クライアント：Respawn は 0x4B（同形式）のまま。\n                return Outcome.toClient(new MinecraftPacket(0x4B, packet.payload()));\n            }\n            if (is773()) {\n                // 773クライアント：Respawn は 0x4B（同形式）のまま。\n                return Outcome.toClient(new MinecraftPacket(0x4B, packet.payload()));\n            }\n            if (is770()) {'),

    # 772サーバー→773クライアント：Disconnect（772：0x1C、773：0x1C）。
    # From766 にも同文の分岐があるため、is772() 分岐からアンカーする。
        ("handleServerPlayFrom772 Disconnect 773",
     '            if (is771() || is772()) {\n                // 771／772クライアント：Disconnect は 0x1C（NBT）のまま。\n',
     '            if (is771() || is772() || is773()) {\n                // 771／772／773クライアント：Disconnect は 0x1C（NBT）のまま。\n'),
    # 772サーバー→773クライアント：Join Game（772：0x2B、773：0x2B）。
        ("handleServerJoinGame772 773 branch",
     '    private Outcome handleServerJoinGame772(MinecraftPacket packet) {\n        if (is771()) {',
     '    private Outcome handleServerJoinGame772(MinecraftPacket packet) {\n        if (is773()) {\n            advanceFlowSafely(() -> joinFlow.joinGameReceived());\n            List<MinecraftPacket> toClient = new ArrayList<>();\n            toClient.add(new MinecraftPacket(0x2B, packet.payload()));\n            while (!pendingPacks.isEmpty()) {\n                PendingPack pack = pendingPacks.removeFirst();\n                lastSentPack = pack;\n                toClient.add(new MinecraftPacket(0x4A,\n                        PlayTranslator.translateConfigAddResourcePack764To765(\n                                pack, packOverrideForClient())));\n            }\n            return new Outcome(List.of(), toClient, false, false);\n        }\n        if (is771()) {'),
    # 772クライアント＋内部773：設定フェーズを中継する。
    ("handleServerConfiguration 772 branch isServer773",
     "            if (isServer769()) {\n"
     "                return handleServerConfiguration772From769(packet);\n"
     "            }",
     "            if (isServer773()) {\n"
     "                return handleServerConfiguration772From773(packet);\n"
     "            }\n"
     "            if (isServer769()) {\n"
     "                return handleServerConfiguration772From769(packet);\n"
     "            }"),

]


# CONFIG_773 に追加する、旧クライアント＋内部773の設定フェーズ変換器。
# shift では作れない（772ステージにも同型の手書きメソッドがある）。
# 773→772→771→770→769→768へ段々に並べ替え、Show Dialog／Clear Dialog
# （773だけの config パケット）は旧クライアント向けに落とす。
CONFIG_773_EXTRA = r'''
    /**
     * 773クライアント＋内部769：設定フェーズを中継する。
     * 769と773の設定フェーズの番号は同じため、ほとんど素通しする。
     * Registry Data だけ769→770→771→772→773へ二段で並べ替え、Tags はレジストリ番号が
     * ずれるため落とす。Cookie Request には空の Cookie Response を、Select Known
     * Packs には空の一覧をプロキシがクライアント役として返す。
     */
    private Outcome handleServerConfiguration773From769(MinecraftPacket packet) {
        return switch (packet.id()) {
            case 0x00 -> { // Cookie Request：空の Cookie Response を返す
                String key = readCookieKey(packet.payload());
                yield Outcome.toServer(new MinecraftPacket(0x01,
                        emptyCookieResponsePayload(key)));
            }
            case 0x01 -> Outcome.toClient(new MinecraftPacket(0x01, packet.payload())); // Custom Payload（同ID）
            case 0x02 -> // Disconnect（769は NBT。773も NBT）
                    Outcome.toClient(packet);
            case 0x03 -> // Finish Configuration
                    Outcome.toClient(packet);
            case 0x04 -> Outcome.toClient(packet); // Keep Alive（同ID）
            case 0x05 -> Outcome.toClient(packet); // Ping（同ID）
            case 0x06 -> Outcome.toClient(packet); // Reset Chat（同ID）
            case 0x07 -> { // Registry Data：769のエントリ配列を773の順序に並べ替える
                Map<String, Nbt.Tag.CompoundTag> parsed =
                        PlayTranslator.parseRegistryData(packet.payload());
                registries.putAll(parsed);
                byte[] payload769to772 =
                        PlayTranslator.translateRegistryData769To772(packet.payload());
                yield Outcome.toClient(new MinecraftPacket(0x07,
                        PlayTranslator.translateRegistryData772To773(payload769to772)));
            }
            case 0x08 -> Outcome.toClient(packet); // Remove Resource Pack（同ID）
            case 0x09 -> { // Add Resource Pack（769：UUID付き）
                PendingPack pack = PlayTranslator.parseConfigAddResourcePack(packet.payload());
                lastSentPack = pack;
                yield Outcome.toClient(new MinecraftPacket(0x09,
                        PlayTranslator.translateConfigAddResourcePack764To765(
                                pack, packOverrideForClient())));
            }
            case 0x0A -> Outcome.toClient(packet); // Store Cookie（同ID）
            case 0x0B -> Outcome.toClient(packet); // Transfer（同ID）
            case 0x0C -> Outcome.toClient(packet); // Feature Flags（同ID）
            case 0x0D -> { // Tags：レジストリ番号がずれるため落とす
                LOG.info(() -> "レジストリ番号がずれるため Tags を773向けに落とします（" + playerName + "）。");
                yield Outcome.drop();
            }
            case 0x0E -> // Select Known Packs：空の一覧を返す
                    Outcome.toServer(new MinecraftPacket(0x07, emptySelectKnownPacksPayload()));
            case 0x0F -> Outcome.toClient(packet); // Custom Report Details（同ID）
            case 0x10 -> Outcome.toClient(packet); // Server Links（同ID）
            default -> {
                LOG.warning("769の設定フェーズで想定外のパケット（id=0x"
                        + Integer.toHexString(packet.id()) + "）を落とします。");
                yield Outcome.drop();
            }
        };
    }

    /**
     * 772クライアント＋内部773：設定フェーズを中継する。
     * 772と773の設定フェーズの番号は同じため、ほとんど素通しする。
     * Registry Data だけ773→772へ並べ替え（minecraft:dialog は772に無いため
     * 落とし、jukebox_song の tears は落とす）、Tags はレジストリ番号が
     * ずれるため落とす。Show Dialog（0x11）／Clear Dialog（0x12）は
     * 772に無いため落とす。Cookie Request には空の Cookie Response を、
     * Select Known Packs には空の一覧をプロキシがクライアント役として返す。
     */
    private Outcome handleServerConfiguration772From773(MinecraftPacket packet) {
        return switch (packet.id()) {
            case 0x00 -> { // Cookie Request：空の Cookie Response を返す
                String key = readCookieKey(packet.payload());
                yield Outcome.toServer(new MinecraftPacket(0x01,
                        emptyCookieResponsePayload(key)));
            }
            case 0x01 -> Outcome.toClient(new MinecraftPacket(0x01, packet.payload())); // Custom Payload（同ID）
            case 0x02 -> // Disconnect（773は NBT。772も NBT）
                    Outcome.toClient(packet);
            case 0x03 -> // Finish Configuration
                    Outcome.toClient(packet);
            case 0x04 -> Outcome.toClient(packet); // Keep Alive（同ID）
            case 0x05 -> Outcome.toClient(packet); // Ping（同ID）
            case 0x06 -> Outcome.toClient(packet); // Reset Chat（同ID）
            case 0x07 -> { // Registry Data：773のエントリ配列を772の順序に並べ替える
                Map<String, Nbt.Tag.CompoundTag> parsed =
                        PlayTranslator.parseRegistryData(packet.payload());
                registries.putAll(parsed);
                byte[] payload = PlayTranslator.translateRegistryData773To772(packet.payload());
                if (payload == null) {
                    yield Outcome.drop();
                }
                yield Outcome.toClient(new MinecraftPacket(0x07, payload));
            }
            case 0x08 -> Outcome.toClient(packet); // Remove Resource Pack（同ID）
            case 0x09 -> { // Add Resource Pack（773：UUID付き）
                PendingPack pack = PlayTranslator.parseConfigAddResourcePack(packet.payload());
                lastSentPack = pack;
                yield Outcome.toClient(new MinecraftPacket(0x09,
                        PlayTranslator.translateConfigAddResourcePack764To765(
                                pack, packOverrideForClient())));
            }
            case 0x0A -> Outcome.toClient(packet); // Store Cookie（同ID）
            case 0x0B -> Outcome.toClient(packet); // Transfer（同ID）
            case 0x0C -> Outcome.toClient(packet); // Feature Flags（同ID）
            case 0x0D -> { // Tags：レジストリ番号がずれるため落とす
                LOG.info(() -> "レジストリ番号がずれるため Tags を772向けに落とします（" + playerName + "）。");
                yield Outcome.drop();
            }
            case 0x0E -> // Select Known Packs：空の一覧を返す
                    Outcome.toServer(new MinecraftPacket(0x07, emptySelectKnownPacksPayload()));
            case 0x0F -> Outcome.toClient(packet); // Custom Report Details（同ID）
            case 0x10 -> Outcome.toClient(packet); // Server Links（同ID）
            case 0x11, 0x12 -> // Show Dialog／Clear Dialog（772に無い）
                    Outcome.drop();
            default -> {
                LOG.warning("773の設定フェーズで想定外のパケット（id=0x"
                        + Integer.toHexString(packet.id()) + "）を落とします。");
                yield Outcome.drop();
            }
        };
    }

    /**
     * 769クライアント＋内部773：設定フェーズを中継する。
     * 769と773の設定フェーズの番号は同じため、ほとんど素通しする。
     * Registry Data だけ773→772→771→770→769へ二段で並べ替え（minecraft:dialog と
     * jukebox_song の tears は落とす）、Tags はレジストリ番号がずれるため
     * 落とす。Show Dialog（0x11）／Clear Dialog（0x12）は769に無いため
     * 落とす。Cookie Request には空の Cookie Response を、Select Known
     * Packs には空の一覧をプロキシがクライアント役として返す。
     */
    private Outcome handleServerConfiguration769From773(MinecraftPacket packet) {
        return switch (packet.id()) {
            case 0x00 -> { // Cookie Request：空の Cookie Response を返す
                String key = readCookieKey(packet.payload());
                yield Outcome.toServer(new MinecraftPacket(0x01,
                        emptyCookieResponsePayload(key)));
            }
            case 0x01 -> Outcome.toClient(new MinecraftPacket(0x01, packet.payload())); // Custom Payload（同ID）
            case 0x02 -> // Disconnect（773は NBT。769も NBT）
                    Outcome.toClient(packet);
            case 0x03 -> // Finish Configuration
                    Outcome.toClient(packet);
            case 0x04 -> Outcome.toClient(packet); // Keep Alive（同ID）
            case 0x05 -> Outcome.toClient(packet); // Ping（同ID）
            case 0x06 -> Outcome.toClient(packet); // Reset Chat（同ID）
            case 0x07 -> { // Registry Data：773のエントリ配列を769の順序に並べ替える
                Map<String, Nbt.Tag.CompoundTag> parsed =
                        PlayTranslator.parseRegistryData(packet.payload());
                // 769向けの辞書は769形式で蓄える（773と769でバイオームの
                // 順序は同じため、実質そのまま）。
                registries.putAll(PlayTranslator.reorderRegistries(
                        parsed, ProtocolVersion.V1_21_9, ProtocolVersion.V1_21_6));
                byte[] payload773to772 =
                        PlayTranslator.translateRegistryData773To772(packet.payload());
                if (payload773to772 == null) {
                    yield Outcome.drop();
                }
                byte[] payload = PlayTranslator.translateRegistryData772To769(payload773to772);
                if (payload == null) {
                    yield Outcome.drop();
                }
                yield Outcome.toClient(new MinecraftPacket(0x07, payload));
            }
            case 0x08 -> Outcome.toClient(packet); // Remove Resource Pack（同ID）
            case 0x09 -> { // Add Resource Pack（773：UUID付き）
                PendingPack pack = PlayTranslator.parseConfigAddResourcePack(packet.payload());
                lastSentPack = pack;
                yield Outcome.toClient(new MinecraftPacket(0x09,
                        PlayTranslator.translateConfigAddResourcePack764To765(
                                pack, packOverrideForClient())));
            }
            case 0x0A -> Outcome.toClient(packet); // Store Cookie（同ID）
            case 0x0B -> Outcome.toClient(packet); // Transfer（同ID）
            case 0x0C -> Outcome.toClient(packet); // Feature Flags（同ID）
            case 0x0D -> { // Tags：レジストリ番号がずれるため落とす
                LOG.info(() -> "レジストリ番号がずれるため Tags を769向けに落とします（" + playerName + "）。");
                yield Outcome.drop();
            }
            case 0x0E -> // Select Known Packs：空の一覧を返す
                    Outcome.toServer(new MinecraftPacket(0x07, emptySelectKnownPacksPayload()));
            case 0x0F -> Outcome.toClient(packet); // Custom Report Details（同ID）
            case 0x10 -> Outcome.toClient(packet); // Server Links（同ID）
            case 0x11, 0x12 -> // Show Dialog／Clear Dialog（769に無い）
                    Outcome.drop();
            default -> {
                LOG.warning("773の設定フェーズで想定外のパケット（id=0x"
                        + Integer.toHexString(packet.id()) + "）を落とします。");
                yield Outcome.drop();
            }
        };
    }

    /**
     * 768クライアント＋内部773：設定フェーズを中継する。
     * 768と773の設定フェーズの番号は同じため、ほとんど素通しする。
     * Registry Data だけ773→772→771→770→769→768へ三段で並べ替え（pale_garden を
     * 落として plains を39に戻す）、Tags はレジストリ番号がずれるため
     * 落とす。Show Dialog（0x11）／Clear Dialog（0x12）は768に無いため
     * 落とす。Cookie Request には空の Cookie Response を、Select Known
     * Packs には空の一覧をプロキシがクライアント役として返す。
     */
    private Outcome handleServerConfiguration768From773(MinecraftPacket packet) {
        return switch (packet.id()) {
            case 0x00 -> { // Cookie Request：空の Cookie Response を返す
                String key = readCookieKey(packet.payload());
                yield Outcome.toServer(new MinecraftPacket(0x01,
                        emptyCookieResponsePayload(key)));
            }
            case 0x01 -> Outcome.toClient(new MinecraftPacket(0x01, packet.payload())); // Custom Payload（同ID）
            case 0x02 -> // Disconnect（773は NBT。768も NBT）
                    Outcome.toClient(packet);
            case 0x03 -> // Finish Configuration
                    Outcome.toClient(packet);
            case 0x04 -> Outcome.toClient(packet); // Keep Alive（同ID）
            case 0x05 -> Outcome.toClient(packet); // Ping（同ID）
            case 0x06 -> Outcome.toClient(packet); // Reset Chat（同ID）
            case 0x07 -> { // Registry Data：773のエントリ配列を768の順序に並べ替える
                Map<String, Nbt.Tag.CompoundTag> parsed =
                        PlayTranslator.parseRegistryData(packet.payload());
                // 768向けの辞書は768形式で蓄える（773のままでは
                // バイオーム pale_garden／plains の番号がずれる）。
                registries.putAll(PlayTranslator.removePaleGardenBiome(
                        PlayTranslator.reorderRegistries(
                                parsed, ProtocolVersion.V1_21_9, ProtocolVersion.V1_21_5)));
                byte[] payload773to772 =
                        PlayTranslator.translateRegistryData773To772(packet.payload());
                if (payload773to772 == null) {
                    yield Outcome.drop();
                }
                byte[] payload772to769 =
                        PlayTranslator.translateRegistryData772To769(payload773to772);
                if (payload772to769 == null) {
                    yield Outcome.drop();
                }
                byte[] payload769to768 =
                        PlayTranslator.translateRegistryData769To768(payload772to769);
                if (payload769to768 == null) {
                    yield Outcome.drop();
                }
                yield Outcome.toClient(new MinecraftPacket(0x07, payload769to768));
            }
            case 0x08 -> Outcome.toClient(packet); // Remove Resource Pack（同ID）
            case 0x09 -> { // Add Resource Pack（773：UUID付き）
                PendingPack pack = PlayTranslator.parseConfigAddResourcePack(packet.payload());
                lastSentPack = pack;
                yield Outcome.toClient(new MinecraftPacket(0x09,
                        PlayTranslator.translateConfigAddResourcePack764To765(
                                pack, packOverrideForClient())));
            }
            case 0x0A -> Outcome.toClient(packet); // Store Cookie（同ID）
            case 0x0B -> Outcome.toClient(packet); // Transfer（同ID）
            case 0x0C -> Outcome.toClient(packet); // Feature Flags（同ID）
            case 0x0D -> { // Tags：レジストリ番号がずれるため落とす
                LOG.info(() -> "レジストリ番号がずれるため Tags を768向けに落とします（" + playerName + "）。");
                yield Outcome.drop();
            }
            case 0x0E -> // Select Known Packs：空の一覧を返す
                    Outcome.toServer(new MinecraftPacket(0x07, emptySelectKnownPacksPayload()));
            case 0x0F -> Outcome.toClient(packet); // Custom Report Details（同ID）
            case 0x10 -> Outcome.toClient(packet); // Server Links（同ID）
            case 0x11, 0x12 -> // Show Dialog／Clear Dialog（768に無い）
                    Outcome.drop();
            default -> {
                LOG.warning("773の設定フェーズで想定外のパケット（id=0x"
                        + Integer.toHexString(packet.id()) + "）を落とします。");
                yield Outcome.drop();
            }
        };
    }
'''


FIX_BLOCKS = {
    "CLIENT_PLAY_773": [
        ('     * Set Creative Slot は767→768→769→770→771→772→773へ五段で変換する',
         '     * Set Creative Slot は767→768→769→770→771→772→773へ六段で変換する'),
        ('                    byte[] payload = ChunkTranslator.translateSetCreativeSlot772To773(\n                            ChunkTranslator.translateSetCreativeSlot770To772(\n                                    ChunkTranslator.translateSetCreativeSlot769To770(\n                                            ChunkTranslator.translateSetCreativeSlot768To769(\n                                                    ChunkTranslator.translateSetCreativeSlot767To768(packet.payload())))));',
         '                    byte[] payload = ChunkTranslator.translateSetCreativeSlot772To773(\n                            ChunkTranslator.translateSetCreativeSlot771To772(\n                                    ChunkTranslator.translateSetCreativeSlot770To771(\n                                            ChunkTranslator.translateSetCreativeSlot769To770(\n                                                    ChunkTranslator.translateSetCreativeSlot768To769(\n                                                            ChunkTranslator.translateSetCreativeSlot767To768(packet.payload()))))));'),
    ],
    "CONFIG_773": [
        # From768 ネスト鎖：770To772 の潰れを 770To771→771To772 の二段に戻す
        ('                        PlayTranslator.translateRegistryData772To773(\n                                PlayTranslator.translateRegistryData770To772(\n                                        PlayTranslator.translateRegistryData769To770(\n                                                PlayTranslator.translateRegistryData768To769(packet.payload()))))));',
         '                        PlayTranslator.translateRegistryData772To773(\n                                PlayTranslator.translateRegistryData771To772(\n                                        PlayTranslator.translateRegistryData770To771(\n                                                PlayTranslator.translateRegistryData769To770(\n                                                        PlayTranslator.translateRegistryData768To769(packet.payload())))))));'),
        # From767 逐次：payload770to772 を 770To771→771To772 の二段に
        ('                byte[] payload767to768 =\n                        PlayTranslator.translateRegistryData767To768(packet.payload());\n                byte[] payload768to769 =\n                        PlayTranslator.translateRegistryData768To769(payload767to768);\n                byte[] payload769to770 =\n                        PlayTranslator.translateRegistryData769To770(payload768to769);\n                byte[] payload770to772 =\n                        PlayTranslator.translateRegistryData770To772(payload769to770);\n                yield Outcome.toClient(new MinecraftPacket(0x07,\n                        PlayTranslator.translateRegistryData772To773(payload770to772)));',
         '                byte[] payload767to768 =\n                        PlayTranslator.translateRegistryData767To768(packet.payload());\n                byte[] payload768to769 =\n                        PlayTranslator.translateRegistryData768To769(payload767to768);\n                byte[] payload769to770 =\n                        PlayTranslator.translateRegistryData769To770(payload768to769);\n                byte[] payload770to771 =\n                        PlayTranslator.translateRegistryData770To771(payload769to770);\n                byte[] payload771to772 =\n                        PlayTranslator.translateRegistryData771To772(payload770to771);\n                yield Outcome.toClient(new MinecraftPacket(0x07,\n                        PlayTranslator.translateRegistryData772To773(payload771to772)));'),
        # From766 逐次：payload770to772 を 770To771→771To772 の二段に
        ('                byte[] payload767to768 =\n                        PlayTranslator.translateRegistryData767To768(payload766to767);\n                byte[] payload768to769 =\n                        PlayTranslator.translateRegistryData768To769(payload767to768);\n                byte[] payload769to770 =\n                        PlayTranslator.translateRegistryData769To770(payload768to769);\n                byte[] payload770to772 =\n                        PlayTranslator.translateRegistryData770To772(payload769to770);\n                yield Outcome.toClient(new MinecraftPacket(0x07,\n                        PlayTranslator.translateRegistryData772To773(payload770to772)));',
         '                byte[] payload767to768 =\n                        PlayTranslator.translateRegistryData767To768(payload766to767);\n                byte[] payload768to769 =\n                        PlayTranslator.translateRegistryData768To769(payload767to768);\n                byte[] payload769to770 =\n                        PlayTranslator.translateRegistryData769To770(payload768to769);\n                byte[] payload770to771 =\n                        PlayTranslator.translateRegistryData770To771(payload769to770);\n                byte[] payload771to772 =\n                        PlayTranslator.translateRegistryData771To772(payload770to771);\n                yield Outcome.toClient(new MinecraftPacket(0x07,\n                        PlayTranslator.translateRegistryData772To773(payload771to772)));'),
        # From769 逐次：payload770to772 を 770To771→771To772 の二段に
        ('                byte[] payload769to770 =\n                        PlayTranslator.translateRegistryData769To770(packet.payload());\n                byte[] payload770to772 =\n                        PlayTranslator.translateRegistryData770To772(payload769to770);\n                yield Outcome.toClient(new MinecraftPacket(0x07,\n                        PlayTranslator.translateRegistryData772To773(payload770to772)));',
         '                byte[] payload769to770 =\n                        PlayTranslator.translateRegistryData769To770(packet.payload());\n                byte[] payload770to771 =\n                        PlayTranslator.translateRegistryData770To771(payload769to770);\n                byte[] payload771to772 =\n                        PlayTranslator.translateRegistryData771To772(payload770to771);\n                yield Outcome.toClient(new MinecraftPacket(0x07,\n                        PlayTranslator.translateRegistryData772To773(payload771to772)));'),
        # From770 逐次：payload770to772 を 770To771→771To772 の二段に
        ('            case 0x07 -> { // Registry Data：770のエントリ配列を773の順序に並べ替える\n                Map<String, Nbt.Tag.CompoundTag> parsed =\n                        PlayTranslator.parseRegistryData(packet.payload());\n                registries.putAll(parsed);\n                byte[] payload770to772 =\n                        PlayTranslator.translateRegistryData770To772(packet.payload());\n                yield Outcome.toClient(new MinecraftPacket(0x07,\n                        PlayTranslator.translateRegistryData772To773(payload770to772)));',
         '            case 0x07 -> { // Registry Data：770のエントリ配列を773の順序に並べ替える\n                Map<String, Nbt.Tag.CompoundTag> parsed =\n                        PlayTranslator.parseRegistryData(packet.payload());\n                registries.putAll(parsed);\n                byte[] payload770to771 =\n                        PlayTranslator.translateRegistryData770To771(packet.payload());\n                byte[] payload771to772 =\n                        PlayTranslator.translateRegistryData771To772(payload770to771);\n                yield Outcome.toClient(new MinecraftPacket(0x07,\n                        PlayTranslator.translateRegistryData772To773(payload771to772)));'),
        # 合成ループ：payload770to772 を 770To771→771To772 の二段に
        ('                    byte[] payload770to772 =\n                            PlayTranslator.translateRegistryData770To772(payload769to770);\n                    toClient.add(new MinecraftPacket(0x07,\n                            PlayTranslator.translateRegistryData772To773(payload770to772)));',
         '                    byte[] payload770to771 =\n                            PlayTranslator.translateRegistryData770To771(payload769to770);\n                    byte[] payload771to772 =\n                            PlayTranslator.translateRegistryData771To772(payload770to771);\n                    toClient.add(new MinecraftPacket(0x07,\n                            PlayTranslator.translateRegistryData772To773(payload771to772)));'),
        # 逆向き（nullチェックなし）：772To770 の潰れを 772To771→771To770 の二段に
        ('                byte[] payload773to772 =\n                        PlayTranslator.translateRegistryData773To772(packet.payload());\n                byte[] payload772to770 =\n                        PlayTranslator.translateRegistryData772To770(payload773to772);\n                byte[] payload770to769 =\n                        PlayTranslator.translateRegistryData770To769(payload772to770);',
         '                byte[] payload773to772 =\n                        PlayTranslator.translateRegistryData773To772(packet.payload());\n                byte[] payload772to771 =\n                        PlayTranslator.translateRegistryData772To771(payload773to772);\n                byte[] payload771to770 =\n                        PlayTranslator.translateRegistryData771To770(payload772to771);\n                byte[] payload770to769 =\n                        PlayTranslator.translateRegistryData770To769(payload771to770);'),
        # 逆向き→767：772To770 の潰れを 772To771→771To770 の二段に
        ('            case 0x07 -> { // Registry Data：773のエントリ配列を767の順序に並べ替える\n                Map<String, Nbt.Tag.CompoundTag> parsed =\n                        PlayTranslator.parseRegistryData(packet.payload());\n                registries.putAll(parsed);\n                byte[] payload773to772 =\n                        PlayTranslator.translateRegistryData773To772(packet.payload());\n                if (payload773to772 == null) {\n                    yield Outcome.drop();\n                }\n                byte[] payload772to770 =\n                        PlayTranslator.translateRegistryData772To770(payload773to772);\n                if (payload772to770 == null) {\n                    yield Outcome.drop();\n                }\n                byte[] payload770to769 =\n                        PlayTranslator.translateRegistryData770To769(payload772to770);',
         '            case 0x07 -> { // Registry Data：773のエントリ配列を767の順序に並べ替える\n                Map<String, Nbt.Tag.CompoundTag> parsed =\n                        PlayTranslator.parseRegistryData(packet.payload());\n                registries.putAll(parsed);\n                byte[] payload773to772 =\n                        PlayTranslator.translateRegistryData773To772(packet.payload());\n                if (payload773to772 == null) {\n                    yield Outcome.drop();\n                }\n                byte[] payload772to771 =\n                        PlayTranslator.translateRegistryData772To771(payload773to772);\n                if (payload772to771 == null) {\n                    yield Outcome.drop();\n                }\n                byte[] payload771to770 =\n                        PlayTranslator.translateRegistryData771To770(payload772to771);\n                if (payload771to770 == null) {\n                    yield Outcome.drop();\n                }\n                byte[] payload770to769 =\n                        PlayTranslator.translateRegistryData770To769(payload771to770);'),
        # 逆向き→766：772To770 の潰れを 772To771→771To770 の二段に
        ('            case 0x07 -> { // Registry Data：773のエントリ配列を766の順序に並べ替える\n                Map<String, Nbt.Tag.CompoundTag> parsed =\n                        PlayTranslator.parseRegistryData(packet.payload());\n                registries.putAll(parsed);\n                byte[] payload773to772 =\n                        PlayTranslator.translateRegistryData773To772(packet.payload());\n                if (payload773to772 == null) {\n                    yield Outcome.drop();\n                }\n                byte[] payload772to770 =\n                        PlayTranslator.translateRegistryData772To770(payload773to772);\n                if (payload772to770 == null) {\n                    yield Outcome.drop();\n                }\n                byte[] payload770to769 =\n                        PlayTranslator.translateRegistryData770To769(payload772to770);',
         '            case 0x07 -> { // Registry Data：773のエントリ配列を766の順序に並べ替える\n                Map<String, Nbt.Tag.CompoundTag> parsed =\n                        PlayTranslator.parseRegistryData(packet.payload());\n                registries.putAll(parsed);\n                byte[] payload773to772 =\n                        PlayTranslator.translateRegistryData773To772(packet.payload());\n                if (payload773to772 == null) {\n                    yield Outcome.drop();\n                }\n                byte[] payload772to771 =\n                        PlayTranslator.translateRegistryData772To771(payload773to772);\n                if (payload772to771 == null) {\n                    yield Outcome.drop();\n                }\n                byte[] payload771to770 =\n                        PlayTranslator.translateRegistryData771To770(payload772to771);\n                if (payload771to770 == null) {\n                    yield Outcome.drop();\n                }\n                byte[] payload770to769 =\n                        PlayTranslator.translateRegistryData770To769(payload771to770);'),
        # 逆向き→769（reorder）：772To770 の潰れを 772To771→771To770 の二段に
        ('                        parsed, ProtocolVersion.V1_21_9, ProtocolVersion.V1_21_6));\n                byte[] payload773to772 =\n                        PlayTranslator.translateRegistryData773To772(packet.payload());\n                if (payload773to772 == null) {\n                    yield Outcome.drop();\n                }\n                byte[] payload772to770 =\n                        PlayTranslator.translateRegistryData772To770(payload773to772);\n                if (payload772to770 == null) {\n                    yield Outcome.drop();\n                }\n                byte[] payload = PlayTranslator.translateRegistryData770To769(payload772to770);',
         '                        parsed, ProtocolVersion.V1_21_9, ProtocolVersion.V1_21_6));\n                byte[] payload773to772 =\n                        PlayTranslator.translateRegistryData773To772(packet.payload());\n                if (payload773to772 == null) {\n                    yield Outcome.drop();\n                }\n                byte[] payload772to771 =\n                        PlayTranslator.translateRegistryData772To771(payload773to772);\n                if (payload772to771 == null) {\n                    yield Outcome.drop();\n                }\n                byte[] payload771to770 =\n                        PlayTranslator.translateRegistryData771To770(payload772to771);\n                if (payload771to770 == null) {\n                    yield Outcome.drop();\n                }\n                byte[] payload = PlayTranslator.translateRegistryData770To769(payload771to770);'),
        # 逆向き→768（reorder）：772To770 の潰れを 772To771→771To770 の二段に
        ('                                parsed, ProtocolVersion.V1_21_9, ProtocolVersion.V1_21_5)));\n                byte[] payload773to772 =\n                        PlayTranslator.translateRegistryData773To772(packet.payload());\n                if (payload773to772 == null) {\n                    yield Outcome.drop();\n                }\n                byte[] payload772to770 =\n                        PlayTranslator.translateRegistryData772To770(payload773to772);\n                if (payload772to770 == null) {\n                    yield Outcome.drop();\n                }\n                byte[] payload770to769 =\n                        PlayTranslator.translateRegistryData770To769(payload772to770);',
         '                                parsed, ProtocolVersion.V1_21_9, ProtocolVersion.V1_21_5)));\n                byte[] payload773to772 =\n                        PlayTranslator.translateRegistryData773To772(packet.payload());\n                if (payload773to772 == null) {\n                    yield Outcome.drop();\n                }\n                byte[] payload772to771 =\n                        PlayTranslator.translateRegistryData772To771(payload773to772);\n                if (payload772to771 == null) {\n                    yield Outcome.drop();\n                }\n                byte[] payload771to770 =\n                        PlayTranslator.translateRegistryData771To770(payload772to771);\n                if (payload771to770 == null) {\n                    yield Outcome.drop();\n                }\n                byte[] payload770to769 =\n                        PlayTranslator.translateRegistryData770To769(payload771to770);'),
    ],
    "SERVER_PLAY_773": [
        ('        // 767クライアント：773→772→771→770→769→768へ変換してから769→768へ。\n        if (is767()) {\n            MinecraftPacket as772 = to772ServerPacketFrom773(packet);\n            if (as772 == null) {\n                return Outcome.drop();\n            }\n            MinecraftPacket as770 = to770ServerPacketFrom772(as772);\n            if (as770 == null) {\n                return Outcome.drop();\n            }\n',
         '        // 767クライアント：773→772→771→770→769→768へ変換してから769→768へ。\n        if (is767()) {\n            MinecraftPacket as772 = to772ServerPacketFrom773(packet);\n            if (as772 == null) {\n                return Outcome.drop();\n            }\n            MinecraftPacket as771 = to771ServerPacketFrom772(as772);\n            if (as771 == null) {\n                return Outcome.drop();\n            }\n            MinecraftPacket as770 = to770ServerPacketFrom771(as771);\n            if (as770 == null) {\n                return Outcome.drop();\n            }\n'),
        ('        // 766クライアント：773→772→771→770→769→768へ変換してから769→767へ。\n        if (is766()) {\n            MinecraftPacket as772 = to772ServerPacketFrom773(packet);\n            if (as772 == null) {\n                return Outcome.drop();\n            }\n            MinecraftPacket as770 = to770ServerPacketFrom772(as772);\n            if (as770 == null) {\n                return Outcome.drop();\n            }\n',
         '        // 766クライアント：773→772→771→770→769→768へ変換してから769→767へ。\n        if (is766()) {\n            MinecraftPacket as772 = to772ServerPacketFrom773(packet);\n            if (as772 == null) {\n                return Outcome.drop();\n            }\n            MinecraftPacket as771 = to771ServerPacketFrom772(as772);\n            if (as771 == null) {\n                return Outcome.drop();\n            }\n            MinecraftPacket as770 = to770ServerPacketFrom771(as771);\n            if (as770 == null) {\n                return Outcome.drop();\n            }\n'),
    ],

}


# 772版の SERVER_PLAY_772_TO（to772ServerPacket 等）を1段ずらしたもの。
# 772→773でパケットIDは変わらないため、ロケーターバー／ダイアログは
# 772にもあるため通す（770向けの落としはしない）。
SERVER_PLAY_773_TO = r'''
    /**
     * 772サーバーのプレイ状態パケットを773形式にする。773に相当が無い／
     * 変換できないものは null を返す（ログ済み）。
     * 773クライアントへの送出と、769／768／767／766クライアントへの
     * 多段変換の最終段で共用する。経験値オーブは770・771・772・773とも通常の
     * Spawn Entity（type experience_orb）のため、専用の分岐は要らない。
     */
    private MinecraftPacket to773ServerPacket(MinecraftPacket packet) {
        PacketName name = PacketIds.nameFor(packet.id(), State.PLAY,
                Direction.CLIENTBOUND, ProtocolVersion.V1_21_7).orElse(null);
        if (name == null) {
            logUntranslatedOnce("PLAY_UNKNOWN_0x" + Integer.toHexString(packet.id()));
            return null;
        }
        byte[] translated = switch (name) {
            case PLAY_ENTITY_METADATA ->
                    ChunkTranslator.translateEntityMetadata772To773(packet.payload());
            case PLAY_SET_SLOT ->
                    ChunkTranslator.translateSetSlot772To773(packet.payload());
            case PLAY_SET_CURSOR_ITEM ->
                    ChunkTranslator.translateSetCursorItem772To773(packet.payload());
            case PLAY_SET_PLAYER_INVENTORY ->
                    ChunkTranslator.translateSetPlayerInventory772To773(packet.payload());
            case PLAY_WINDOW_ITEMS ->
                    ChunkTranslator.translateWindowItems772To773(packet.payload());
            case PLAY_SET_COOLDOWN ->
                    ChunkTranslator.translateSetCooldown772To773(packet.payload());
            case PLAY_ENTITY_EQUIPMENT ->
                    ChunkTranslator.translateEntityEquipment772To773(packet.payload());
            case PLAY_TRADE_LIST ->
                    ChunkTranslator.translateTradeList772To773(packet.payload());
            case PLAY_OPEN_WINDOW ->
                    ChunkTranslator.translateOpenWindow772To773(packet.payload());
            case PLAY_BLOCK_ACTION ->
                    ChunkTranslator.translateBlockAction772To773(packet.payload());
            case PLAY_SPAWN_ENTITY ->
                    ChunkTranslator.translateSpawnEntity772To773(packet.payload());
            case PLAY_SOUND_EFFECT ->
                    ChunkTranslator.translateSoundEffect772To773(packet.payload());
            case PLAY_ENTITY_SOUND_EFFECT ->
                    ChunkTranslator.translateEntitySoundEffect772To773(packet.payload());
            case PLAY_STOP_SOUND ->
                    ChunkTranslator.translateStopSound772To773(packet.payload());
            case PLAY_WORLD_PARTICLES ->
                    ChunkTranslator.translateWorldParticles772To773(packet.payload());
            case PLAY_ENTITY_EFFECT ->
                    ChunkTranslator.translateEntityEffect772To773(packet.payload());
            case PLAY_REMOVE_ENTITY_EFFECT ->
                    ChunkTranslator.translateRemoveEntityEffect772To773(packet.payload());
            case PLAY_DAMAGE_EVENT ->
                    ChunkTranslator.translateDamageEvent772To773(packet.payload());
            case PLAY_ENTITY_UPDATE_ATTRIBUTES ->
                    ChunkTranslator.translateUpdateAttributes772To773(packet.payload());
            case PLAY_CHUNK_DATA ->
                    ChunkTranslator.translateChunkData772To773(packet.payload());
            case PLAY_BLOCK_CHANGE ->
                    ChunkTranslator.translateBlockChange772To773(packet.payload());
            case PLAY_MULTI_BLOCK_CHANGE ->
                    ChunkTranslator.translateMultiBlockChange772To773(packet.payload());
            case PLAY_BLOCK_ENTITY_DATA ->
                    ChunkTranslator.translateBlockEntityData772To773(packet.payload());
            case PLAY_PLAYER_INFO_UPDATE ->
                    PlayTranslator.translatePlayerInfo772To773(packet.payload());
            case PLAY_PLAYER_CHAT ->
                    PlayTranslator.translatePlayerChat772To773(packet.payload());
            case PLAY_SYSTEM_CHAT, PLAY_PROFILELESS_CHAT ->
                    packet.payload(); // 同形式
            case PLAY_SYNCHRONIZE_PLAYER_POSITION, PLAY_UPDATE_TIME, PLAY_SET_SLOT_STATE ->
                    packet.payload(); // 同形式
            case PLAY_HELD_ITEM_SLOT ->
                    PlayTranslator.translateSetHeldItem772To773(packet.payload());
            case PLAY_EXPLOSION ->
                    ChunkTranslator.translateExplosion772To773(packet.payload());
            // ロケーターバー／ダイアログは772にもあるためそのまま通す（同形式）。
            case PLAY_TRACKED_WAYPOINT, PLAY_SHOW_DIALOG, PLAY_CLEAR_DIALOG ->
                    packet.payload();
            default -> null;
        };
        if (translated == null) {
            logUntranslatedOnce(name);
            return null;
        }
        OptionalInt id773 = PacketTranslator.translateId(name,
                ProtocolVersion.V1_21_7, ProtocolVersion.V1_21_9);
        if (id773.isEmpty()) {
            logUntranslatedOnce(name);
            return null;
        }
        return new MinecraftPacket(id773.getAsInt(), translated);
    }

    /**
     * 内部772→773クライアントのプレイ状態（サーバー→クライアント）。
     * 772→773へ変換してそのまま送る。
     */
    private Outcome handleServerPlay772To773(MinecraftPacket packet) {
        MinecraftPacket as773 = to773ServerPacket(packet);
        if (as773 == null) {
            return Outcome.drop();
        }
        return Outcome.toClient(as773);
    }

    /**
     * 内部770→773クライアントのプレイ状態（サーバー→クライアント）。
     * 769→771→772→773へ二段で変換する。
     */
    private Outcome handleServerPlay769To773(MinecraftPacket packet) {
        Outcome to772 = handleServerPlay769To772(packet);
        if (to772.toClient().isEmpty()) {
            return to772;
        }
        return handleServerPlay772To773(to772.toClient().get(0));
    }

    /**
     * 内部769→773クライアントのプレイ状態（サーバー→クライアント）。
     * 768→771→772→773へ二段で変換する。
     */
    private Outcome handleServerPlay768To773(MinecraftPacket packet) {
        Outcome to772 = handleServerPlay768To772(packet);
        if (to772.toClient().isEmpty()) {
            return to772;
        }
        return handleServerPlay772To773(to772.toClient().get(0));
    }

    /**
     * 内部768→773クライアントのプレイ状態（サーバー→クライアント）。
     * 767→771→772→773へ二段で変換する。
     */
    private Outcome handleServerPlay767To773(MinecraftPacket packet) {
        Outcome to772 = handleServerPlay767To772(packet);
        if (to772.toClient().isEmpty()) {
            return to772;
        }
        return handleServerPlay772To773(to772.toClient().get(0));
    }

    /**
     * 内部767→773クライアントのプレイ状態（サーバー→クライアント）。
     * 766→771→772→773へ二段で変換する。
     */
    private Outcome handleServerPlay766To773(MinecraftPacket packet) {
        Outcome to772 = handleServerPlay766To772(packet);
        if (to772.toClient().isEmpty()) {
            return to772;
        }
        return handleServerPlay772To773(to772.toClient().get(0));
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
    for name in ("CLIENT_PLAY_772", "SERVER_PLAY_772", "CONFIG_772"):
        blocks[name] = ast.literal_eval(assigns[name])

    new_edits = []
    for name, old, new in edits:
        if new.startswith(old) and new != old:
            kind = "APPEND"
            added = new[len(old):]
            new_edits.append((name.replace("772", "773"), new, new + shift(added)))
        elif old in new and new != old:
            kind = "CONTAINS-OLD"
            assert new.endswith(old), f"{name}: CONTAINS-OLD but not suffix form"
            prefix = new[: len(new) - len(old)]
            new_edits.append((name.replace("772", "773"), new, shift(prefix) + new))
        else:
            kind = "REPLACE"
            new_edits.append((name.replace("772", "773"), new, shift(new)))
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

    by_name = {}
    fixed_edits = []
    for name, old, new in new_edits:
        if name in SKIP_EDITS:
            print(f"SKIP: {name}（772で適用済みのため773パッチから外す）", file=sys.stderr)
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
    # 772ステージと違い、773ではメソッド名の衝突は無い（shift 後の
    # to773ClientPacket／to772ClientPacketFrom773／to772ServerPacketFrom773
    # はすべて新規名のため、改名の手直しは要らない）。
    new_blocks = {}
    for name in ("CLIENT_PLAY_772", "SERVER_PLAY_772", "CONFIG_772"):
        shifted = shift(blocks[name])
        new_name = name.replace("772", "773")
        if new_name in FIX_BLOCKS:
            shifted = apply_subs(shifted, FIX_BLOCKS[new_name], f"block {new_name}")
        new_blocks[new_name] = shifted

    # ヘッダ（手書き）と EDITS を出力し、ブロックは後段の手作業用に
    # shifted 版をそのまま載せる。
    out_lines = []
    out_lines.append('''#!/usr/bin/env python3
"""ProxySession.java へ Java 1.21.9／1.21.10（プロトコル773）の対応を入れる。

772パッチ（patch-proxysession-772.py）を1段ずらしたもの。生成元は
tools/gen-773-proxysession.py で、EDITS・3ブロック・INSERTS を機械的に
ずらし、773特有の箇所だけ手で直した。

773の要点（772からの差分）:
- クライアント行きのプレイはデバッグ系パケットの挿入でずれる。
  debug_block_value 0x1A・debug_chunk_value 0x1B・debug_entity_value 0x1C・
  debug_event 0x1D の挿入で 0x1A 以降が+4、game_test_highlight_pos 0x27 の
  挿入で 0x27 以降がさらに+1（計+5）。
  Join Game 0x2B→0x30、Spawn Position 0x5A→0x5F、Chunk Data 0x27→0x2C、
  Keep Alive 0x26→0x2B、Ping 0x36→0x3B、Start Configuration 0x6F→0x74、
  Disconnect 0x1C→0x20、Respawn 0x4B→0x50、Add Resource Pack 0x4A→0x4F。
  新規のデバッグ系5件と game_test_highlight_pos は旧クライアント向けに落とす。
- サーバー行きのプレイは番号が変わらない。debug_sample_subscription
  （0x16）は773で削除され、同じ 0x16 に debug_subscription_request が
  入る（形式が違うため別パケットとして扱い、旧サーバー向けに落とす）。
- 設定フェーズも番号が変わらない。新規: Code of Conduct（config 0x13）と
  Accept Code of Conduct（config 0x09）。旧クライアント・旧サーバー向けに落とす。
- チャットの checksum（u8）は770・771・772・773ともあるため、773⇔772では
  付け足しも剥がしもししない（番号だけ付け替える）。770⇔771の変換器は
  そのまま（769には checksum が無い）。
- Vehicle Move の onGround は769・770・771・772・773ともある（768に無い）。
  773⇔772では素通しする。
- Window Click は771・772・773とも HashedSlot のため落とす。Set Creative Slot は
  772・773とも UntrustedSlot のため、アイテム／成分を付け替えるだけ。
- Player Chat は先頭に globalIndex が770・771・772・773ともあるため素通しする。
  769サーバーからの player_chat にはプロキシが 0 から数えて付ける
  （770／771／772／773クライアント共用の chatGlobalIndex）。
- 設定フェーズは772と同形（同じ番号・同じ形式）で中継する。
  Registry Data の並び替えだけ 772→773 が要る（minecraft:dialog は
  772・773ともある。jukebox_song・絵画は同一。銅ゴーレム像などの
  ブロックエンティティは Registry Data には出ない）。
  Show Dialog（config 0x12）／Clear Dialog（0x11）は旧クライアント向けに
  落とす。Custom Click Action（config 0x08）は773サーバーへ通し、
  旧サーバー向けに落とす。Custom Report Details／Server Links
  （config サーバー行き）は772で削除されたため、773サーバー向けも落とす。
- 経験値オーブは770・771・772・773とも通常の Spawn Entity（type experience_orb）。
  772の to769ServerPacketFrom772 の専用分岐は773→772では使わない。
- ハッピーガスト（773: 57）は772では 56 に写る（同じ種別）。
  771以前ではガスト（55）に代替する（invert ではブタになるが、見た目が
  近いガストを選ぶ。メタデータの個体差はプロキシが種別を追跡しないため残る）。
- ロケーターバー（tracked_waypoint 0x83→0x88）／ダイアログ（show_dialog 0x85→0x8A／
  clear_dialog 0x84→0x89）は772・773にだけあるため、771以前のクライアント向けに落とす。
- 内部773サーバー＋773クライアントはネイティブ（素通し）。

使い方: repo ルートで `python3 detergentmc-protocol/tools/patch-proxysession-773.py`
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
    for name in ("CLIENT_PLAY_773", "SERVER_PLAY_773", "CONFIG_773"):
        out_lines.append(f"{name} = r'''{new_blocks[name]}'''")
        out_lines.append("")
    out_lines.append(f"SERVER_PLAY_773_TO = r'''{SERVER_PLAY_773_TO}'''")
    out_lines.append("")
    # INSERTS: 772版の INSERTS を流用（メソッド名だけずらす）。
    # 772版と違い、SERVER_PLAY_773_TO の挿入は shift 済み INSERTS に
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
    # CONFIG_ANCHOR：772パッチの rest は既に置換済みの anchor（default -> drop）
    # で、shift が警告文の 771→772 と CONFIG_772→CONFIG_773 を済ませている
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
