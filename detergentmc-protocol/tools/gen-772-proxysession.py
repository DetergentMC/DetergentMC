#!/usr/bin/env python3
"""patch-proxysession-769.py から patch-proxysession-771.py を生成する。

方針: 769パッチの EDITS・3ブロック・INSERTS を「1段ずつらす」。
- 矢印チェーン (→): 末尾が769なら「→771」を追加（鎖が伸びる）。
  2要素のペア（770→769 等）は両端を+1する（770→769→771→770）。
  それ以外の多要素チェーンは各要素+1して警告ログに出す（要目視確認）。
- ⇔チェーン・／リスト・・リスト: 末尾が769なら 771 を追加する。
  2要素 (769⇔770 等) は+1する。
- 単独の 770→771、769→770（V1_21_5→V1_21_6、V1_21_4→V1_21_5、
  1.21.5→1.21.6、1.21.2／1.21.3→1.21.5、pack769→pack771、pack768→pack769）。
- 16進リテラル (0x..) と pack_format の数値は触らない（後で手で直す）。
- CONTAINS-OLD 編集は new = PREFIX + old の形なので、
  new_771 = shift(PREFIX) + new_769、old_771 = new_769 とする。
  （全6件がこの形であることをアサートする）
- APPEND 編集は old_771 = new_769、new_771 = new_769 + shift(追加分)。
- REPLACE 編集は old_771 = new_769、new_771 = shift(new_769)。

【注意】この生成器で作ったパッチ（patch-proxysession-772.py）を適用した
後、ProxySession772Test で見つかった772漏れを手で直してある
（コミット 8fa47ab）。この生成器を再実行して作り直したパッチは、
下記の手直しを含まないため live と一致しない。再実行する場合は
下記も FIX_EDITS_FULL 等に取り込むこと。
- handleSynthesizedJoinGame／handleServerJoinGame763／
  handleServerJoinGame（764／765）：is771() → is770() || is771() || is772()。
- handleServerJoinGame766：is770() || is771() || is772() 分岐を新設
  （0x2B＋translateJoinGame767To768＋Add Pack 0x4A。771段階では
  771クライアントも 0x29 に落ちていた）。
- handleServerPlayUp（765サーバー）：分岐条件に is772() を追加し、
  Ping／Disconnect／Respawn／Add Resource Pack の三項を
  is770() || is771() || is772() に直す。
- handleClientConfiguration768：Client Information の剥がし条件に
  !isServer772() を追加し、素通し条件に isServer772() を追加。
- handleServerPlayFrom772：Respawn に is768() || is769() → 0x4C 分岐を
  追加、一般分岐に is769()／is768() を追加（handleServerPlay772To769／
  772To768 を新設）。
- handleServerPlayFrom771：Respawn の is769() → is768() || is769()
  （771段階の既存バグ。768クライアントは 765形式の 0x45 に落ちていた）。
"""
import ast
import re
import sys
from pathlib import Path

SRC = Path("/home/hatch/workspace/detergentmc/detergentmc-protocol/tools/patch-proxysession-771.py")
OUT = Path("/home/hatch/workspace/detergentmc/detergentmc-protocol/tools/patch-proxysession-772.py")

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
IS_CHAIN = re.compile(r"((?:is(?:Server)?\d{3}\(\)\s*\|\|\s*)+is(?:Server)?771\(\))")
IS_CHAIN_770 = re.compile(r"((?:is(?:Server)?\d{3}\(\)\s*\|\|\s*)+is(?:Server)?770\(\))")


def shift_chain(m, kind):
    sep = "→" if kind == "arrow" else "⇔"
    nums = m.group(0).split(sep)
    if len(nums) == 2:
        # 2要素のペアは1段ずらす（770→771 は 771→772 になる）。
        return sep.join(str(int(n) + 1) for n in nums)
    if len(nums) >= 3 and nums[0] == "771":
        # 771から始まる多段チェーンは段が1つ増える（771→770→769 は
        # 772→771→770→769 になる）。末端（765/766/767）は固定。
        return "772" + sep + m.group(0)
    if nums[-1] == "771":
        return m.group(0) + sep + "772"
    warnings.append(f"arrow chain {m.group(0)} -> +1 each")
    return sep.join(str(int(n) + 1) for n in nums)


def shift_list(m, sep):
    nums = m.group(0).split(sep)
    if nums[-1] == "771":
        return m.group(0) + sep + "772"
    if nums[-1] == "770":
        # 新バージョンの一つ下まで列挙しているリストは 771 を追加する。
        return m.group(0) + sep + "771"
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
    # 1b) isチェーン（is764() || ... || is771()）に is772() を追加し、
    # そのまま保護する（後の数字シフトで二重にずれないように）。
    def is_chain_sub(m):
        chain = m.group(1)
        last = re.search(r"is(Server)?771\(\)$", chain)
        suffix = "isServer772()" if last.group(1) else "is772()"
        key = f"\x02{_counter[0]}\x03"
        _counter[0] += 1
        is_chain_holders[key] = chain + " || " + suffix
        return key

    is_chain_holders = {}
    text = IS_CHAIN.sub(is_chain_sub, text)

    def is_chain_sub_770(m):
        chain = m.group(1)
        last = re.search(r"is(Server)?770\(\)$", chain)
        suffix = "isServer771()" if last.group(1) else "is771()"
        key = f"\x02{_counter[0]}\x03"
        _counter[0] += 1
        is_chain_holders[key] = chain + " || " + suffix
        return key

    text = IS_CHAIN_770.sub(is_chain_sub_770, text)
    holders = {**h0, **h1, **h2, **h3, **h4, **is_chain_holders}
    # 2) バージョン文字列
    text = text.replace("1.21.6", "1.21.7")
    text = text.replace("1.21.5", "1.21.6")
    text = text.replace("1.21.4", "1.21.5")
    text = text.replace("1.21.2／1.21.3", "1.21.4")
    text = text.replace("1.21.2", "1.21.4")
    text = text.replace("V1_21_6", "V1_21_7")
    text = text.replace("V1_21_5", "V1_21_6")
    text = text.replace("V1_21_4", "V1_21_5")
    text = text.replace("V1_21_2", "V1_21_4")
    text = text.replace("PROTOCOL_771", "PROTOCOL_772")
    text = text.replace("PROTOCOL_770", "PROTOCOL_771")
    text = text.replace("PROTOCOL_769", "PROTOCOL_770")
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
    text = re.sub(r"pack(?:770|771|772)", pack_sub, text)
    holders.update(pack_holders)
    # PROTOCOL_77x も数字シフトから保護する（バージョン置き換えで既に
    # ずらし済みのため。二重にかかると PROTOCOL_770→772 になる）。
    text = re.sub(r"PROTOCOL_77[0-9]", pack_sub, text)
    holders.update(pack_holders)
    # 3) 単独の数字。\b は日本語文字を \w と見なすため使えないので、
    # 数字の前後を見る lookaround を使う（識別子内の 769 も対象にする）。
    # 16進リテラル (0x..）は数字続きなので対象外になる。
    text = re.sub(r"(?<!\d)771(?!\d)", "\x01771", text)
    text = re.sub(r"(?<!\d)770(?!\d)", "771", text)
    text = text.replace("\x01771", "772")
    # 4) 復元
    return restore(text, holders)


FIX_EDITS = {
    # name -> [(old_sub, new_sub), ...] を shift した NEW テキストに適用する
    # （各1回一致。shift の誤変換の修正）。
    "constructor javadoc": [
        ("769＝1.21.4、770＝1.21.5、771＝1.21.6）", "769＝1.21.4、770＝1.21.5、771＝1.21.6、772＝1.21.7）"),
    ],
    "constructor validation": [
        # shift は V1_21_2 を V1_21_4 にずらして重複させる（768の検査が欠ける）。
        # 検査の一覧に V1_21_2（768）を戻す。
        ("&& serverProtocol != ProtocolVersion.V1_21\n"
         "                && serverProtocol != ProtocolVersion.V1_21_4",
         "&& serverProtocol != ProtocolVersion.V1_21\n"
         "                && serverProtocol != ProtocolVersion.V1_21_2\n"
         "                && serverProtocol != ProtocolVersion.V1_21_4"),
        ("769 (1.21.4), 770 (1.21.5) or 771 (1.21.6): ",
         "769 (1.21.4), 770 (1.21.5), 771 (1.21.6) or 772 (1.21.7): "),
    ],
    "serverProtocol() javadoc": [
        ("769＝1.21.4、770＝1.21.5、771＝1.21.6）。", "769＝1.21.4、770＝1.21.5、771＝1.21.6、772＝1.21.7）。"),
    ],
    "pack772 field": [
        # 772の pack_format は 64（771の 63 のままになっている）。
        ("772クライアントに送るリソースパック（pack_format 63）",
         "772クライアントに送るリソースパック（pack_format 64）"),
    ],
    "packOverrideForClient javadoc": [
        # shift は pack_format の数字をずらさない（772=64、771=63、770=55、769=46）。
        # 770の行も shift で 769→pack770 に潰れているため戻す。
        ("772→pack772\n     * （pack_format 63）、771→pack771\n"
         "     * （pack_format 55）、769→pack770\n"
         "     * （pack_format 46）、768→pack768",
         "772→pack772\n     * （pack_format 64）、771→pack771\n"
         "     * （pack_format 63）、770→pack770\n"
         "     * （pack_format 55）、769→pack769\n"
         "     * （pack_format 46）、768→pack768"),
    ],
    "packOverrideForClient branch": [
        # shift は 772分岐の代替チェーンから pack769 を欠落させる。
        (": pack771 != null ? pack771\n"
         "                    : pack770 != null ? pack770\n"
         "                    : pack768 != null ? pack768",
         ": pack771 != null ? pack771\n"
         "                    : pack770 != null ? pack770\n"
         "                    : pack769 != null ? pack769\n"
         "                    : pack768 != null ? pack768"),
    ],
    "synthesized config registry loop": [
        # 772向けの Registry Data は766→767→768→769→770→771→772の六段。
        # shift は 769→770→771 の段を translateRegistryData769To771
        # （存在しない）に潰してしまうため、769→770→771の二段に戻す。
        ("// 772：766→767→768→769→770→771→772へ五段で並べ替える。",
         "// 772：766→767→768→769→770→771→772へ六段で並べ替える。"),
        ("                    byte[] payload769to771 =\n"
         "                            PlayTranslator.translateRegistryData769To771(payload768to769);\n"
         "                    registryPayload =\n"
         "                            PlayTranslator.translateRegistryData771To772(payload769to771);",
         "                    byte[] payload769to770 =\n"
         "                            PlayTranslator.translateRegistryData769To770(payload768to769);\n"
         "                    byte[] payload770to771 =\n"
         "                            PlayTranslator.translateRegistryData770To771(payload769to770);\n"
         "                    registryPayload =\n"
         "                            PlayTranslator.translateRegistryData771To772(payload770to771);"),
    ],
    # "startConfigurationClientId"：772の shift は既に正しいため FIX は不要。
    # "spawnPositionClientId"：772の shift は既に正しいため FIX は不要。
    # "keepAliveClientId"：772の shift は既に正しいため FIX は不要。
    # "pingClientId"：772の shift は既に正しいため FIX は不要。
    # "handleServerPlay dispatch"：772の shift は既に正しいため FIX は不要。
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
    # "handleServerPlayFrom769 772 branch"：772の shift は既に正しいため FIX は不要。
    "handleServerPlayFrom766 772 branch": [
        # 段数の数え間違い：766からは六段、767からは五段。
        ("// 内部766は 766→767→768→769→770→771→772 の五段、内部767は 767→768→769→770→771→772 の四段で送る。",
         "// 内部766は 766→767→768→769→770→771→772 の六段、内部767は 767→768→769→770→771→772 の五段で送る。"),
    ],
    # "handleServerPlayFrom763 ResourcePack 772"：772の shift は既に正しいため FIX は不要。
    # 772で削除された、とシフトされてしまうが、削除されたのは771。
    "handleClientConfiguration 772-server guard": [
        ("// （0x09）を送らない（772で削除された）。772クライアントの",
         "// （0x09）を送らない（771で削除された。772も同じ）。772クライアントの"),
    ],

}


# (old, new) を丸ごと置き換える編集。shift した (old, new) では
# アンカーが live に無かったり、意味が変わったりするもの。
FIX_EDITS_FULL = {
    # 'handleServerConfiguration 772 branch'：shift した old は771パッチの
    # new（771分岐＋769分岐）だが、live の769分岐には771ステージの手直しで
    # isServer771 の分岐が入っているためアンカーが合わない。live の771分岐の
    # 先頭をアンカーにして772分岐を丸ごと書き直す。
    "handleServerConfiguration 772 branch": (
        "        // 771クライアントは設定フェーズを中継する（番号と Registry Data\n"
        "        // の形式だけ変換する）。\n"
        "        if (is771()) {",
        "        // 772クライアントは設定フェーズを中継する（番号と Registry Data\n"
        "        // の形式だけ変換する）。\n"
        "        if (is772()) {\n"
        "            if (isServer772()) {\n"
        "                // 内部772＋772クライアントはネイティブのためここには来ない。\n"
        "                // 念のためそのまま通す。\n"
        "                return Outcome.toClient(packet);\n"
        "            }\n"
        "            if (isServer771()) {\n"
        "                return handleServerConfiguration772From771(packet);\n"
        "            }\n"
        "            if (isServer770()) {\n"
        "                return handleServerConfiguration772From770(packet);\n"
        "            }\n"
        "            if (isServer769()) {\n"
        "                return handleServerConfiguration772From769(packet);\n"
        "            }\n"
        "            if (isServer768()) {\n"
        "                return handleServerConfiguration772From768(packet);\n"
        "            }\n"
        "            if (isServer767()) {\n"
        "                return handleServerConfiguration772From767(packet);\n"
        "            }\n"
        "            if (isServer766()) {\n"
        "                return handleServerConfiguration772From766(packet);\n"
        "            }\n"
        "            return handleServerConfiguration772(packet, isServer764());\n"
        "        }\n"
        "        // 771クライアントは設定フェーズを中継する（番号と Registry Data\n"
        "        // の形式だけ変換する）。\n"
        "        if (is771()) {",
    ),
    # 以下の3件は shift の REPLACE による分岐置換の誤り（771段階では
    # 「追加」だったものが772では「置換」になり、分岐を失う）。771段階の
    # Respawn 772（FIX_EDITS_FULL）と同じ手直しで、分岐を残したまま
    # is772() 分岐を前に足す。
    "handleServerJoinGame770 772 branch": (
        '    private Outcome handleServerJoinGame770(MinecraftPacket packet) {\n        if (is771()) {\n            advanceFlowSafely(() -> joinFlow.joinGameReceived());\n            List<MinecraftPacket> toClient = new ArrayList<>();\n            toClient.add(new MinecraftPacket(0x2B, packet.payload()));\n            while (!pendingPacks.isEmpty()) {\n                PendingPack pack = pendingPacks.removeFirst();\n                lastSentPack = pack;\n                toClient.add(new MinecraftPacket(0x4A,\n                        PlayTranslator.translateConfigAddResourcePack764To765(\n                                pack, packOverrideForClient())));\n            }\n            return new Outcome(List.of(), toClient, false, false);\n        }\n        if (is769()) {',
        '    private Outcome handleServerJoinGame770(MinecraftPacket packet) {\n        if (is772()) {\n            advanceFlowSafely(() -> joinFlow.joinGameReceived());\n            List<MinecraftPacket> toClient = new ArrayList<>();\n            toClient.add(new MinecraftPacket(0x2B, packet.payload()));\n            while (!pendingPacks.isEmpty()) {\n                PendingPack pack = pendingPacks.removeFirst();\n                lastSentPack = pack;\n                toClient.add(new MinecraftPacket(0x4A,\n                        PlayTranslator.translateConfigAddResourcePack764To765(\n                                pack, packOverrideForClient())));\n            }\n            return new Outcome(List.of(), toClient, false, false);\n        }\n        if (is771()) {\n            advanceFlowSafely(() -> joinFlow.joinGameReceived());\n            List<MinecraftPacket> toClient = new ArrayList<>();\n            toClient.add(new MinecraftPacket(0x2B, packet.payload()));\n            while (!pendingPacks.isEmpty()) {\n                PendingPack pack = pendingPacks.removeFirst();\n                lastSentPack = pack;\n                toClient.add(new MinecraftPacket(0x4A,\n                        PlayTranslator.translateConfigAddResourcePack764To765(\n                                pack, packOverrideForClient())));\n            }\n            return new Outcome(List.of(), toClient, false, false);\n        }\n        if (is769()) {',
    ),
    "handleServerPlayFrom770 Disconnect 772": (
        '            if (is770()) {\n                // 770クライアント：Disconnect は 0x1C（NBT）のまま。\n                return new Outcome(List.of(),\n                        List.of(new MinecraftPacket(0x1C, packet.payload())), false, true);\n            }\n            if (is771()) {\n                // 771クライアント：Disconnect は 0x1C（NBT）のまま。\n                return new Outcome(List.of(),\n                        List.of(new MinecraftPacket(0x1C, packet.payload())), false, true);\n            }\n            if (is766() || is767() || is768() || is769()) {\n                // 766／767／768／769クライアント：Disconnect は 0x1D（NBT）のまま。',
        '            if (is772()) {\n                // 772クライアント：Disconnect は 0x1C（NBT）のまま。\n                return new Outcome(List.of(),\n                        List.of(new MinecraftPacket(0x1C, packet.payload())), false, true);\n            }\n            if (is771()) {\n                // 771クライアント：Disconnect は 0x1C（NBT）のまま。\n                return new Outcome(List.of(),\n                        List.of(new MinecraftPacket(0x1C, packet.payload())), false, true);\n            }\n            if (is770()) {\n                // 770クライアント：Disconnect は 0x1C（NBT）のまま。\n                return new Outcome(List.of(),\n                        List.of(new MinecraftPacket(0x1C, packet.payload())), false, true);\n            }\n            if (is766() || is767() || is768() || is769()) {\n                // 766／767／768／769クライアント：Disconnect は 0x1D（NBT）のまま。',
    ),
    "synthesized config registry loop": (
        '        if (is766() || is767() || is768() || is769() || is770() || is771()) {\n            // 766／767／768／769／770／771：レジストリごとに1パケット（エントリ配列）。\n            for (byte[] registryPayload : PlayTranslator.buildRegistryData766(registries)) {\n                if (is771()) {\n                    // 771：766→767→768→769→770→771へ五段で並べ替える。\n                    byte[] payload766to767 =\n                            PlayTranslator.translateRegistryData766To767(registryPayload);\n                    byte[] payload767to768 =\n                            PlayTranslator.translateRegistryData767To768(payload766to767);\n                    byte[] payload768to769 =\n                            PlayTranslator.translateRegistryData768To769(payload767to768);\n                    byte[] payload769to770 =\n                            PlayTranslator.translateRegistryData769To770(payload768to769);\n                    registryPayload =\n                            PlayTranslator.translateRegistryData770To771(payload769to770);\n                }\n                toClient.add(new MinecraftPacket(0x07, registryPayload));\n            }',
        '        if (is766() || is767() || is768() || is769() || is770() || is771() || is772()) {\n            // 766／767／768／769／770／771／772：レジストリごとに1パケット（エントリ配列）。\n            for (byte[] registryPayload : PlayTranslator.buildRegistryData766(registries)) {\n                if (is771()) {\n                    // 771：766→767→768→769→770→771へ五段で並べ替える。\n                    byte[] payload766to767 =\n                            PlayTranslator.translateRegistryData766To767(registryPayload);\n                    byte[] payload767to768 =\n                            PlayTranslator.translateRegistryData767To768(payload766to767);\n                    byte[] payload768to769 =\n                            PlayTranslator.translateRegistryData768To769(payload767to768);\n                    byte[] payload769to770 =\n                            PlayTranslator.translateRegistryData769To770(payload768to769);\n                    registryPayload =\n                            PlayTranslator.translateRegistryData770To771(payload769to770);\n                }\n                if (is772()) {\n                    // 772：766→767→768→769→770→771→772へ六段で並べ替える。\n                    byte[] payload766to767 =\n                            PlayTranslator.translateRegistryData766To767(registryPayload);\n                    byte[] payload767to768 =\n                            PlayTranslator.translateRegistryData767To768(payload766to767);\n                    byte[] payload768to769 =\n                            PlayTranslator.translateRegistryData768To769(payload767to768);\n                    byte[] payload769to770 =\n                            PlayTranslator.translateRegistryData769To770(payload768to769);\n                    byte[] payload770to771 =\n                            PlayTranslator.translateRegistryData770To771(payload769to770);\n                    registryPayload =\n                            PlayTranslator.translateRegistryData771To772(payload770to771);\n                }\n                toClient.add(new MinecraftPacket(0x07, registryPayload));\n            }',
    ),
    # "synthesized add-pack 772 id"：772の shift は三項を
    # is770() || is771() || is772() に正しく伸ばすため FULL は不要。
    # 'handleServerPlayFrom769 Respawn 772' / 'handleServerPlayFrom768
    # Respawn 772'：shift は is771 分岐を is772 に書き換えてしまい、
    # is771（From769）／is769（From768）の分岐を消してしまう。
    # 771の分岐（From768は769の分岐も）を残したまま772分岐を前に足す。
    "handleServerPlayFrom769 Respawn 772": (
        "        // Respawn（769：0x4C。768と同形式のため768の変換を使い回す）。\n"
        "        if (packet.id() == 0x4C) {\n"
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
    "handleServerPlayFrom768 Respawn 772": (
        "        // Respawn（768：0x4C）。\n"
        "        if (packet.id() == 0x4C) {\n"
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
    # 'chatGlobalIndex field'：shift した old は pack771 ブロック＋771用
    # フィールドだが、'pack772 field' 編集が先に pack772 ブロックを挟むため
    # アンカーが合わず、new もフィールドを重複させる。pack772 ブロックと
    # 旧771用カウンタをアンカーにして、770／771／772共用のカウンタを1本だけ置く。
    "chatGlobalIndex field": (
        "    /**\n"
        "     * 772クライアントに送るリソースパック（pack_format 64）。\n"
        "     * 内部が772でない場合に使う差し替え用。内部が772の場合はバニラが\n"
        "     * 送ってきたパックをそのまま出すため null のままでよい。\n"
        "     * 出し分けの基準はクライアントのプロトコルである\n"
        "     * （{@link #packOverrideForClient()} 参照）。\n"
        "     */\n"
        "    private ResourcePack pack772;\n"
        "\n"
        "    /**\n"
        "     * 770／771クライアントへ送る player_chat に付ける globalIndex。\n"
        "     * 769サーバーは globalIndex を送ってこないため、プロキシが\n"
        "     * セッションごとに 0 から数える。\n"
        "     */\n"
        "    private int chatGlobalIndex = 0;",
        "    /**\n"
        "     * 772クライアントに送るリソースパック（pack_format 64）。\n"
        "     * 内部が772でない場合に使う差し替え用。内部が772の場合はバニラが\n"
        "     * 送ってきたパックをそのまま出すため null のままでよい。\n"
        "     * 出し分けの基準はクライアントのプロトコルである\n"
        "     * （{@link #packOverrideForClient()} 参照）。\n"
        "     */\n"
        "    private ResourcePack pack772;\n"
        "\n"
        "    /**\n"
        "     * 770／771／772クライアントへ送る player_chat に付ける globalIndex。\n"
        "     * 769サーバーは globalIndex を送ってこないため、プロキシが\n"
        "     * セッションごとに 0 から数える。\n"
        "     */\n"
        "    private int chatGlobalIndex = 0;",
    ),
    # 'synthesized add-pack'：shift した版はチェーンとコメントだけを
    # 変えるが、三項の 0x4A 側も is772() を足す必要がある。3行まとめて
    # 置き換える（後続の 'synthesized add-pack 772 id' は SKIP）。
    "synthesized add-pack": (
        "            if (is768() || is769() || is770() || is771()) {\n"
        "                // プレイ状態の Add Resource Pack は 0x4B（PLAY_ADD_RESOURCE_PACK）。771は 0x4A。\n"
        "                toClient.add(new MinecraftPacket(is770() || is771() ? 0x4A : 0x4B,",
        "            if (is768() || is769() || is770() || is771() || is772()) {\n"
        "                // プレイ状態の Add Resource Pack は 0x4B（PLAY_ADD_RESOURCE_PACK）。772は 0x4A。\n"
        "                toClient.add(new MinecraftPacket(is770() || is771() || is772() ? 0x4A : 0x4B,"),

    # 'handleServerPlayFrom770 Respawn 772'：771パッチの new は
    # is769() 単独分岐だが、live は771段階の手直しで is768() || is769()
    # になっている（コメント「771段階で修正」）。live に合わせて丸ごと書き直す。
    # コメントの 770：0x4B は From770 のものなので変えない。
    "handleServerPlayFrom770 Respawn 772": (
        "        // Respawn（770：0x4B。769（0x4C）と同形式のため769の変換を使い回す）。\n"
        "        if (packet.id() == 0x4B) {\n"
        "            if (is771()) {\n"
        "                // 771クライアント：Respawn は 0x4B（同形式）のまま。\n"
        "                return Outcome.toClient(new MinecraftPacket(0x4B, packet.payload()));\n"
        "            }\n"
        "            if (is768() || is769()) {",
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
        "            if (is768() || is769()) {"),

}


# 772パッチから外す編集。771ステージで既に live へ適用済みの修正で、
# shift すると存在しないメソッド（translatePlayerInfo771To768 等）を
# 呼ぶ壊れた編集になってしまうため。
SKIP_EDITS = {
    "to768ServerPacket chat passthrough",
    "handleServerPlay768To767 chat passthrough",
    "handleServerPlay767To766 system_chat passthrough",    "synthesized add-pack 772 id",

}


ADD_EDITS = [
    # 771サーバー→772クライアント：一般プレイ状態の変換。
    # （shift では作れない。handleServerPlayFrom771 に772分岐が無いため。）
        ("handleServerPlayFrom771 772 branch",
     '        // 770クライアント：771→770へ変換する。\n        if (is770()) {\n            return handleServerPlay771To770(packet);\n        }',
     '        // 772クライアント：771→772へ変換する。\n        if (is772()) {\n            return handleServerPlay771To772(packet);\n        }\n        // 770クライアント：771→770へ変換する。\n        if (is770()) {\n            return handleServerPlay771To770(packet);\n        }'),
    # 771サーバー→772クライアント：Respawn（771：0x4B、772：0x4B、同形式）。
        ("handleServerPlayFrom771 Respawn 772",
     '        // Respawn（771：0x4B。770（0x4C）と同形式のため770の変換を使い回す）。\n        if (packet.id() == 0x4B) {\n            if (is770()) {',
     '        // Respawn（771：0x4B。770（0x4C）と同形式のため770の変換を使い回す）。\n        if (packet.id() == 0x4B) {\n            if (is772()) {\n                // 772クライアント：Respawn は 0x4B（同形式）のまま。\n                return Outcome.toClient(new MinecraftPacket(0x4B, packet.payload()));\n            }\n            if (is770()) {'),
    # 771サーバー→772クライアント：Disconnect（771：0x1C、772：0x1C）。
    # From766 にも同文の分岐があるため、is771() 分岐からアンカーする。
    ("handleServerPlayFrom771 Disconnect 772",
     '            if (is771()) {\n                // 771クライアント：Disconnect は 0x1C（NBT）のまま。\n                return new Outcome(List.of(),\n                        List.of(new MinecraftPacket(0x1C, packet.payload())), false, true);\n            }\n            if (is766() || is767() || is768() || is769() || is770()) {\n                // 766／767／768／769／770クライアント：Disconnect は 0x1D（NBT）のまま。',
     '            if (is771()) {\n                // 771クライアント：Disconnect は 0x1C（NBT）のまま。\n                return new Outcome(List.of(),\n                        List.of(new MinecraftPacket(0x1C, packet.payload())), false, true);\n            }\n            if (is772()) {\n                // 772クライアント：Disconnect は 0x1C（NBT）のまま。\n                return new Outcome(List.of(),\n                        List.of(new MinecraftPacket(0x1C, packet.payload())), false, true);\n            }\n            if (is766() || is767() || is768() || is769() || is770()) {\n                // 766／767／768／769／770クライアント：Disconnect は 0x1D（NBT）のまま。'),
    # 771サーバー→772クライアント：Join Game（771：0x2B、772：0x2B）。
    ("handleServerJoinGame771 772 branch",
     '    private Outcome handleServerJoinGame771(MinecraftPacket packet) {\n        if (is770()) {',
     '    private Outcome handleServerJoinGame771(MinecraftPacket packet) {\n        if (is772()) {\n            advanceFlowSafely(() -> joinFlow.joinGameReceived());\n            List<MinecraftPacket> toClient = new ArrayList<>();\n            toClient.add(new MinecraftPacket(0x2B, packet.payload()));\n            while (!pendingPacks.isEmpty()) {\n                PendingPack pack = pendingPacks.removeFirst();\n                lastSentPack = pack;\n                toClient.add(new MinecraftPacket(0x4A,\n                        PlayTranslator.translateConfigAddResourcePack764To765(\n                                pack, packOverrideForClient())));\n            }\n            return new Outcome(List.of(), toClient, false, false);\n        }\n        if (is770()) {'),
    # 771クライアント＋内部772：設定フェーズを中継する。
    ("handleServerConfiguration 771 branch isServer772",
     "            if (isServer769()) {\n"
     "                return handleServerConfiguration771From769(packet);\n"
     "            }",
     "            if (isServer772()) {\n"
     "                return handleServerConfiguration771From772(packet);\n"
     "            }\n"
     "            if (isServer769()) {\n"
     "                return handleServerConfiguration771From769(packet);\n"
     "            }"),

]


# CONFIG_772 に追加する、旧クライアント＋内部772の設定フェーズ変換器。
# shift では作れない（771ステージにも同型の手書きメソッドがある）。
# 772→771→770→769→768へ段々に並べ替え、Show Dialog／Clear Dialog
# （772だけの config パケット）は旧クライアント向けに落とす。
CONFIG_772_EXTRA = r'''
    /**
     * 772クライアント＋内部769：設定フェーズを中継する。
     * 769と772の設定フェーズの番号は同じため、ほとんど素通しする。
     * Registry Data だけ769→770→771→772へ二段で並べ替え、Tags はレジストリ番号が
     * ずれるため落とす。Cookie Request には空の Cookie Response を、Select Known
     * Packs には空の一覧をプロキシがクライアント役として返す。
     */
    private Outcome handleServerConfiguration772From769(MinecraftPacket packet) {
        return switch (packet.id()) {
            case 0x00 -> { // Cookie Request：空の Cookie Response を返す
                String key = readCookieKey(packet.payload());
                yield Outcome.toServer(new MinecraftPacket(0x01,
                        emptyCookieResponsePayload(key)));
            }
            case 0x01 -> Outcome.toClient(new MinecraftPacket(0x01, packet.payload())); // Custom Payload（同ID）
            case 0x02 -> // Disconnect（769は NBT。772も NBT）
                    Outcome.toClient(packet);
            case 0x03 -> // Finish Configuration
                    Outcome.toClient(packet);
            case 0x04 -> Outcome.toClient(packet); // Keep Alive（同ID）
            case 0x05 -> Outcome.toClient(packet); // Ping（同ID）
            case 0x06 -> Outcome.toClient(packet); // Reset Chat（同ID）
            case 0x07 -> { // Registry Data：769のエントリ配列を772の順序に並べ替える
                Map<String, Nbt.Tag.CompoundTag> parsed =
                        PlayTranslator.parseRegistryData(packet.payload());
                registries.putAll(parsed);
                byte[] payload769to771 =
                        PlayTranslator.translateRegistryData769To771(packet.payload());
                yield Outcome.toClient(new MinecraftPacket(0x07,
                        PlayTranslator.translateRegistryData771To772(payload769to771)));
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
                LOG.info(() -> "レジストリ番号がずれるため Tags を772向けに落とします（" + playerName + "）。");
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
     * 771クライアント＋内部772：設定フェーズを中継する。
     * 771と772の設定フェーズの番号は同じため、ほとんど素通しする。
     * Registry Data だけ772→771へ並べ替え（minecraft:dialog は771に無いため
     * 落とし、jukebox_song の tears は落とす）、Tags はレジストリ番号が
     * ずれるため落とす。Show Dialog（0x11）／Clear Dialog（0x12）は
     * 771に無いため落とす。Cookie Request には空の Cookie Response を、
     * Select Known Packs には空の一覧をプロキシがクライアント役として返す。
     */
    private Outcome handleServerConfiguration771From772(MinecraftPacket packet) {
        return switch (packet.id()) {
            case 0x00 -> { // Cookie Request：空の Cookie Response を返す
                String key = readCookieKey(packet.payload());
                yield Outcome.toServer(new MinecraftPacket(0x01,
                        emptyCookieResponsePayload(key)));
            }
            case 0x01 -> Outcome.toClient(new MinecraftPacket(0x01, packet.payload())); // Custom Payload（同ID）
            case 0x02 -> // Disconnect（772は NBT。771も NBT）
                    Outcome.toClient(packet);
            case 0x03 -> // Finish Configuration
                    Outcome.toClient(packet);
            case 0x04 -> Outcome.toClient(packet); // Keep Alive（同ID）
            case 0x05 -> Outcome.toClient(packet); // Ping（同ID）
            case 0x06 -> Outcome.toClient(packet); // Reset Chat（同ID）
            case 0x07 -> { // Registry Data：772のエントリ配列を771の順序に並べ替える
                Map<String, Nbt.Tag.CompoundTag> parsed =
                        PlayTranslator.parseRegistryData(packet.payload());
                registries.putAll(parsed);
                byte[] payload = PlayTranslator.translateRegistryData772To771(packet.payload());
                if (payload == null) {
                    yield Outcome.drop();
                }
                yield Outcome.toClient(new MinecraftPacket(0x07, payload));
            }
            case 0x08 -> Outcome.toClient(packet); // Remove Resource Pack（同ID）
            case 0x09 -> { // Add Resource Pack（772：UUID付き）
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
                LOG.info(() -> "レジストリ番号がずれるため Tags を771向けに落とします（" + playerName + "）。");
                yield Outcome.drop();
            }
            case 0x0E -> // Select Known Packs：空の一覧を返す
                    Outcome.toServer(new MinecraftPacket(0x07, emptySelectKnownPacksPayload()));
            case 0x0F -> Outcome.toClient(packet); // Custom Report Details（同ID）
            case 0x10 -> Outcome.toClient(packet); // Server Links（同ID）
            case 0x11, 0x12 -> // Show Dialog／Clear Dialog（771に無い）
                    Outcome.drop();
            default -> {
                LOG.warning("772の設定フェーズで想定外のパケット（id=0x"
                        + Integer.toHexString(packet.id()) + "）を落とします。");
                yield Outcome.drop();
            }
        };
    }

    /**
     * 769クライアント＋内部772：設定フェーズを中継する。
     * 769と772の設定フェーズの番号は同じため、ほとんど素通しする。
     * Registry Data だけ772→771→770→769へ二段で並べ替え（minecraft:dialog と
     * jukebox_song の tears は落とす）、Tags はレジストリ番号がずれるため
     * 落とす。Show Dialog（0x11）／Clear Dialog（0x12）は769に無いため
     * 落とす。Cookie Request には空の Cookie Response を、Select Known
     * Packs には空の一覧をプロキシがクライアント役として返す。
     */
    private Outcome handleServerConfiguration769From772(MinecraftPacket packet) {
        return switch (packet.id()) {
            case 0x00 -> { // Cookie Request：空の Cookie Response を返す
                String key = readCookieKey(packet.payload());
                yield Outcome.toServer(new MinecraftPacket(0x01,
                        emptyCookieResponsePayload(key)));
            }
            case 0x01 -> Outcome.toClient(new MinecraftPacket(0x01, packet.payload())); // Custom Payload（同ID）
            case 0x02 -> // Disconnect（772は NBT。769も NBT）
                    Outcome.toClient(packet);
            case 0x03 -> // Finish Configuration
                    Outcome.toClient(packet);
            case 0x04 -> Outcome.toClient(packet); // Keep Alive（同ID）
            case 0x05 -> Outcome.toClient(packet); // Ping（同ID）
            case 0x06 -> Outcome.toClient(packet); // Reset Chat（同ID）
            case 0x07 -> { // Registry Data：772のエントリ配列を769の順序に並べ替える
                Map<String, Nbt.Tag.CompoundTag> parsed =
                        PlayTranslator.parseRegistryData(packet.payload());
                // 769向けの辞書は769形式で蓄える（772と769でバイオームの
                // 順序は同じため、実質そのまま）。
                registries.putAll(PlayTranslator.reorderRegistries(
                        parsed, ProtocolVersion.V1_21_7, ProtocolVersion.V1_21_5));
                byte[] payload772to771 =
                        PlayTranslator.translateRegistryData772To771(packet.payload());
                if (payload772to771 == null) {
                    yield Outcome.drop();
                }
                byte[] payload = PlayTranslator.translateRegistryData771To769(payload772to771);
                if (payload == null) {
                    yield Outcome.drop();
                }
                yield Outcome.toClient(new MinecraftPacket(0x07, payload));
            }
            case 0x08 -> Outcome.toClient(packet); // Remove Resource Pack（同ID）
            case 0x09 -> { // Add Resource Pack（772：UUID付き）
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
                LOG.warning("772の設定フェーズで想定外のパケット（id=0x"
                        + Integer.toHexString(packet.id()) + "）を落とします。");
                yield Outcome.drop();
            }
        };
    }

    /**
     * 768クライアント＋内部772：設定フェーズを中継する。
     * 768と772の設定フェーズの番号は同じため、ほとんど素通しする。
     * Registry Data だけ772→771→770→769→768へ三段で並べ替え（pale_garden を
     * 落として plains を39に戻す）、Tags はレジストリ番号がずれるため
     * 落とす。Show Dialog（0x11）／Clear Dialog（0x12）は768に無いため
     * 落とす。Cookie Request には空の Cookie Response を、Select Known
     * Packs には空の一覧をプロキシがクライアント役として返す。
     */
    private Outcome handleServerConfiguration768From772(MinecraftPacket packet) {
        return switch (packet.id()) {
            case 0x00 -> { // Cookie Request：空の Cookie Response を返す
                String key = readCookieKey(packet.payload());
                yield Outcome.toServer(new MinecraftPacket(0x01,
                        emptyCookieResponsePayload(key)));
            }
            case 0x01 -> Outcome.toClient(new MinecraftPacket(0x01, packet.payload())); // Custom Payload（同ID）
            case 0x02 -> // Disconnect（772は NBT。768も NBT）
                    Outcome.toClient(packet);
            case 0x03 -> // Finish Configuration
                    Outcome.toClient(packet);
            case 0x04 -> Outcome.toClient(packet); // Keep Alive（同ID）
            case 0x05 -> Outcome.toClient(packet); // Ping（同ID）
            case 0x06 -> Outcome.toClient(packet); // Reset Chat（同ID）
            case 0x07 -> { // Registry Data：772のエントリ配列を768の順序に並べ替える
                Map<String, Nbt.Tag.CompoundTag> parsed =
                        PlayTranslator.parseRegistryData(packet.payload());
                // 768向けの辞書は768形式で蓄える（772のままでは
                // バイオーム pale_garden／plains の番号がずれる）。
                registries.putAll(PlayTranslator.removePaleGardenBiome(
                        PlayTranslator.reorderRegistries(
                                parsed, ProtocolVersion.V1_21_7, ProtocolVersion.V1_21_4)));
                byte[] payload772to771 =
                        PlayTranslator.translateRegistryData772To771(packet.payload());
                if (payload772to771 == null) {
                    yield Outcome.drop();
                }
                byte[] payload771to769 =
                        PlayTranslator.translateRegistryData771To769(payload772to771);
                if (payload771to769 == null) {
                    yield Outcome.drop();
                }
                byte[] payload769to768 =
                        PlayTranslator.translateRegistryData769To768(payload771to769);
                if (payload769to768 == null) {
                    yield Outcome.drop();
                }
                yield Outcome.toClient(new MinecraftPacket(0x07, payload769to768));
            }
            case 0x08 -> Outcome.toClient(packet); // Remove Resource Pack（同ID）
            case 0x09 -> { // Add Resource Pack（772：UUID付き）
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
                LOG.warning("772の設定フェーズで想定外のパケット（id=0x"
                        + Integer.toHexString(packet.id()) + "）を落とします。");
                yield Outcome.drop();
            }
        };
    }
'''


FIX_BLOCKS = {
    "CLIENT_PLAY_772": [
        # javadoc：Window Click のガーブ（771・770・771 → 771・772）
        ("Window Click は771・770・771とも HashedSlot のため落とす。",
         "Window Click は771・772とも HashedSlot のため落とす。"),
        # Vehicle Move（772→771）：0x21→0x20 の付け替えは誤り。generic 経路で通すため分岐ごと削除。
        ("        // Vehicle Move（772：0x21→771：0x20）：770・771・772とも末尾に onGround が\n"
         "        // あるため形式は同一。そのまま通す。\n"
         "        if (packet.id() == 0x21) {\n"
         "            return new MinecraftPacket(0x20,\n"
         "                    PlayTranslator.translateVehicleMove772To771(packet.payload()));\n"
         "        }",
         ""),
        # Window Click（772→771）のコメント
        ("        // Window Click（772：0x11）：771・770・771とも HashedSlot のため\n"
         "        // 落とす（ハッシュの計算方法は未確定）。",
         "        // Window Click（772：0x11）：771・772とも HashedSlot のため\n"
         "        // 落とす（ハッシュの計算方法は未確定）。"),
        # Set Creative Slot（772→771）：0x37→0x36 は誤り。0x37のまま成分だけ付け替える。
        ("        // Set Creative Slot（772：0x37→771：0x36）：\n"
         "        // UntrustedSlot のままアイテム／成分を付け替える。\n"
         "        if (packet.id() == 0x37) {\n"
         "            return new MinecraftPacket(0x36,\n"
         "                    ChunkTranslator.translateSetCreativeSlot772To771(packet.payload()));\n"
         "        }",
         "        // Set Creative Slot（772：0x37→771：0x37）：\n"
         "        // UntrustedSlot のままアイテム／成分を付け替える。\n"
         "        if (packet.id() == 0x37) {\n"
         "            return new MinecraftPacket(0x37,\n"
         "                    ChunkTranslator.translateSetCreativeSlot772To771(packet.payload()));\n"
         "        }"),
        # Set Creative Slot（767→772）の段数：呼び出し5回のため五段
        ("     * Set Creative Slot は767→768→769→770→771→772へ四段で変換する",
         "     * Set Creative Slot は767→768→769→770→771→772へ五段で変換する"),
        # 767→772 の Set Creative Slot 連鎖：769To771（不存在）を769To770＋770To771に戻す
        ("                if (is767()) {\n"
         "                    byte[] payload = ChunkTranslator.translateSetCreativeSlot771To772(\n"
         "                            ChunkTranslator.translateSetCreativeSlot769To771(\n"
         "                                    ChunkTranslator.translateSetCreativeSlot768To769(\n"
         "                                            ChunkTranslator.translateSetCreativeSlot767To768(packet.payload()))));\n"
         "                    return Outcome.toServer(new MinecraftPacket(0x37, payload));\n"
         "                }",
         "                if (is767()) {\n"
         "                    byte[] payload = ChunkTranslator.translateSetCreativeSlot771To772(\n"
         "                            ChunkTranslator.translateSetCreativeSlot770To771(\n"
         "                                    ChunkTranslator.translateSetCreativeSlot769To770(\n"
         "                                            ChunkTranslator.translateSetCreativeSlot768To769(\n"
         "                                                    ChunkTranslator.translateSetCreativeSlot767To768(packet.payload())))));\n"
         "                    return Outcome.toServer(new MinecraftPacket(0x37, payload));\n"
         "                }"),
        # 移動パケットのコメント：769→770 ではなく 767→768 の変換
        ("                // 771→772の移動パケットは同形式のため、769→770の変換だけでよい（768／769／770／771／772は同形式）。",
         "                // 771→772の移動パケットは同形式のため、767→768の変換だけでよい（768／769／770／771／772は同形式）。"),
        # Vehicle Move（771→772）：0x20→0x21 の付け替えは誤り。generic 経路で通すため分岐ごと削除。
        ("        // Vehicle Move（771：0x20→772：0x21）：770・771・772とも末尾に onGround が\n"
         "        // あるため形式は同一。そのまま通す。\n"
         "        if (packet.id() == 0x20) {\n"
         "            return new MinecraftPacket(0x21,\n"
         "                    PlayTranslator.translateVehicleMove771To772(packet.payload()));\n"
         "        }",
         ""),
        # Window Click（771→772）：771側は 0x10 ではなく 0x11
        ("        // Window Click（771：0x10）：772は HashedSlot で、コンポーネントの\n"
         "        // ハッシュ計算方法が未確定のため落とす。\n"
         "        if (packet.id() == 0x10) {",
         "        // Window Click（771：0x11）：771・772とも HashedSlot で、コンポーネントの\n"
         "        // ハッシュ計算方法が未確定のため落とす。\n"
         "        if (packet.id() == 0x11) {"),
        # Set Creative Slot（771→772）：0x36→0x37 は誤り。0x37のまま成分だけ付け替える。
        ("        // Set Creative Slot（771：0x36→772：0x37）：\n"
         "        // UntrustedSlot のままアイテム／成分を付け替える。\n"
         "        if (packet.id() == 0x36) {\n"
         "            return new MinecraftPacket(0x37,\n"
         "                    ChunkTranslator.translateSetCreativeSlot771To772(packet.payload()));\n"
         "        }",
         "        // Set Creative Slot（771：0x37→772：0x37）：\n"
         "        // UntrustedSlot のままアイテム／成分を付け替える。\n"
         "        if (packet.id() == 0x37) {\n"
         "            return new MinecraftPacket(0x37,\n"
         "                    ChunkTranslator.translateSetCreativeSlot771To772(packet.payload()));\n"
         "        }"),
    ],
    "SERVER_PLAY_772": [
        # Respawn（772→771）：771は 0x4B（0x4C ではない）
        ("        // Respawn（772：0x4B。771（0x4C）と同形式のため771の変換を使い回す）。\n"
         "        if (packet.id() == 0x4B) {\n"
         "            if (is771()) {\n"
         "                // 771クライアント：Respawn は 0x4C（同形式）のまま。\n"
         "                return Outcome.toClient(new MinecraftPacket(0x4C, packet.payload()));\n"
         "            }",
         "        // Respawn（772：0x4B。771（0x4B）と同形式のためそのまま送る）。\n"
         "        if (packet.id() == 0x4B) {\n"
         "            if (is771()) {\n"
         "                // 771クライアント：Respawn は 0x4B（同形式）のまま。\n"
         "                return Outcome.toClient(new MinecraftPacket(0x4B, packet.payload()));\n"
         "            }"),
        # Disconnect（772→771）：771も 0x1C。0x1D は766〜770のみ。
        ("            if (is772()) {\n"
         "                // 772クライアント：Disconnect は 0x1C（NBT）のまま。\n"
         "                return new Outcome(List.of(),\n"
         "                        List.of(new MinecraftPacket(0x1C, packet.payload())), false, true);\n"
         "            }\n"
         "            if (is766() || is767() || is768() || is769() || is770() || is771()) {\n"
         "                // 766／767／768／769／770／771クライアント：Disconnect は 0x1D（NBT）のまま。",
         "            if (is771() || is772()) {\n"
         "                // 771／772クライアント：Disconnect は 0x1C（NBT）のまま。\n"
         "                return new Outcome(List.of(),\n"
         "                        List.of(new MinecraftPacket(0x1C, packet.payload())), false, true);\n"
         "            }\n"
         "            if (is766() || is767() || is768() || is769() || is770()) {\n"
         "                // 766／767／768／769／770クライアント：Disconnect は 0x1D（NBT）のまま。"),
        # 767分岐：to769ServerPacketFrom771（不存在）を to770→to769 の二段に戻す
        ("        // 767クライアント：772→771→770→769→768へ変換してから769→768へ。\n"
         "        if (is767()) {\n"
         "            MinecraftPacket as771 = to771ServerPacketFrom772(packet);\n"
         "            if (as771 == null) {\n"
         "                return Outcome.drop();\n"
         "            }\n"
         "            MinecraftPacket as769 = to769ServerPacketFrom771(as771);",
         "        // 767クライアント：772→771→770→769→768へ変換してから768→767へ。\n"
         "        if (is767()) {\n"
         "            MinecraftPacket as771 = to771ServerPacketFrom772(packet);\n"
         "            if (as771 == null) {\n"
         "                return Outcome.drop();\n"
         "            }\n"
         "            MinecraftPacket as770 = to770ServerPacketFrom771(as771);\n"
         "            if (as770 == null) {\n"
         "                return Outcome.drop();\n"
         "            }\n"
         "            MinecraftPacket as769 = to769ServerPacketFrom770(as770);"),
        # 766分岐：同上
        ("        // 766クライアント：772→771→770→769→768へ変換してから769→767へ。\n"
         "        if (is766()) {\n"
         "            MinecraftPacket as771 = to771ServerPacketFrom772(packet);\n"
         "            if (as771 == null) {\n"
         "                return Outcome.drop();\n"
         "            }\n"
         "            MinecraftPacket as769 = to769ServerPacketFrom771(as771);",
         "        // 766クライアント：772→771→770→769→768へ変換してから768→766へ。\n"
         "        if (is766()) {\n"
         "            MinecraftPacket as771 = to771ServerPacketFrom772(packet);\n"
         "            if (as771 == null) {\n"
         "                return Outcome.drop();\n"
         "            }\n"
         "            MinecraftPacket as770 = to770ServerPacketFrom771(as771);\n"
         "            if (as770 == null) {\n"
         "                return Outcome.drop();\n"
         "            }\n"
         "            MinecraftPacket as769 = to769ServerPacketFrom770(as770);"),
        # experience_orb のコメント
        ("        // experience_orb：771・770・771とも通常の Spawn Entity",
         "        // experience_orb：771・772とも通常の Spawn Entity"),
        # ロケーターバー／ダイアログ：771にもあるため通す（772だけではない）
        ("            // 772だけのパケット（ロケーターバー／ダイアログ）は771に無いため落とす。\n"
         "            case PLAY_TRACKED_WAYPOINT, PLAY_SHOW_DIALOG, PLAY_CLEAR_DIALOG -> null;",
         "            // ロケーターバー／ダイアログは771にもあるためそのまま通す（同形式）。\n"
         "            case PLAY_TRACKED_WAYPOINT, PLAY_SHOW_DIALOG, PLAY_CLEAR_DIALOG ->\n"
         "                    packet.payload();"),
    ],
    "CONFIG_772": [
        # From768 連鎖：769To771（不存在）を769To770＋770To771に戻す
        ("                yield Outcome.toClient(new MinecraftPacket(0x07,\n"
         "                        PlayTranslator.translateRegistryData771To772(\n"
         "                                PlayTranslator.translateRegistryData769To771(\n"
         "                                        PlayTranslator.translateRegistryData768To769(packet.payload())))));",
         "                yield Outcome.toClient(new MinecraftPacket(0x07,\n"
         "                        PlayTranslator.translateRegistryData771To772(\n"
         "                                PlayTranslator.translateRegistryData770To771(\n"
         "                                        PlayTranslator.translateRegistryData769To770(\n"
         "                                                PlayTranslator.translateRegistryData768To769(packet.payload()))))));"),
        ("768→769→770→771→772へ二段で並べ替え",
         "768→769→770→771→772へ四段で並べ替え"),
        # From767 連鎖（payload767to768 の出所で From766 と区別）
        ("                byte[] payload767to768 =\n"
         "                        PlayTranslator.translateRegistryData767To768(packet.payload());\n"
         "                byte[] payload768to769 =\n"
         "                        PlayTranslator.translateRegistryData768To769(payload767to768);\n"
         "                byte[] payload769to771 =\n"
         "                        PlayTranslator.translateRegistryData769To771(payload768to769);\n"
         "                yield Outcome.toClient(new MinecraftPacket(0x07,\n"
         "                        PlayTranslator.translateRegistryData771To772(payload769to771)));",
         "                byte[] payload767to768 =\n"
         "                        PlayTranslator.translateRegistryData767To768(packet.payload());\n"
         "                byte[] payload768to769 =\n"
         "                        PlayTranslator.translateRegistryData768To769(payload767to768);\n"
         "                byte[] payload769to770 =\n"
         "                        PlayTranslator.translateRegistryData769To770(payload768to769);\n"
         "                byte[] payload770to771 =\n"
         "                        PlayTranslator.translateRegistryData770To771(payload769to770);\n"
         "                yield Outcome.toClient(new MinecraftPacket(0x07,\n"
         "                        PlayTranslator.translateRegistryData771To772(payload770to771)));"),
        ("767→768→769→770→771→772へ三段で並べ替え",
         "767→768→769→770→771→772へ五段で並べ替え"),
        # From766 連鎖
        ("                byte[] payload767to768 =\n"
         "                        PlayTranslator.translateRegistryData767To768(payload766to767);\n"
         "                byte[] payload768to769 =\n"
         "                        PlayTranslator.translateRegistryData768To769(payload767to768);\n"
         "                byte[] payload769to771 =\n"
         "                        PlayTranslator.translateRegistryData769To771(payload768to769);\n"
         "                yield Outcome.toClient(new MinecraftPacket(0x07,\n"
         "                        PlayTranslator.translateRegistryData771To772(payload769to771)));",
         "                byte[] payload767to768 =\n"
         "                        PlayTranslator.translateRegistryData767To768(payload766to767);\n"
         "                byte[] payload768to769 =\n"
         "                        PlayTranslator.translateRegistryData768To769(payload767to768);\n"
         "                byte[] payload769to770 =\n"
         "                        PlayTranslator.translateRegistryData769To770(payload768to769);\n"
         "                byte[] payload770to771 =\n"
         "                        PlayTranslator.translateRegistryData770To771(payload769to770);\n"
         "                yield Outcome.toClient(new MinecraftPacket(0x07,\n"
         "                        PlayTranslator.translateRegistryData771To772(payload770to771)));"),
        ("766→767→768→769→770→771→772へ四段で並べ替え",
         "766→767→768→769→770→771→772へ六段で並べ替え"),
        # 764／765サーバー側の連鎖
        ("                    byte[] payload769to771 =\n"
         "                            PlayTranslator.translateRegistryData769To771(payload768to769);\n"
         "                    toClient.add(new MinecraftPacket(0x07,\n"
         "                            PlayTranslator.translateRegistryData771To772(payload769to771)));",
         "                    byte[] payload769to770 =\n"
         "                            PlayTranslator.translateRegistryData769To770(payload768to769);\n"
         "                    byte[] payload770to771 =\n"
         "                            PlayTranslator.translateRegistryData770To771(payload769to770);\n"
         "                    toClient.add(new MinecraftPacket(0x07,\n"
         "                            PlayTranslator.translateRegistryData771To772(payload770to771)));"),
        ("765→767→768→769→770→771→772へ五段",
         "765→767→768→769→770→771→772へ六段"),
        # From769 連鎖
        ("                byte[] payload769to771 =\n"
         "                        PlayTranslator.translateRegistryData769To771(packet.payload());\n"
         "                yield Outcome.toClient(new MinecraftPacket(0x07,\n"
         "                        PlayTranslator.translateRegistryData771To772(payload769to771)));",
         "                byte[] payload769to770 =\n"
         "                        PlayTranslator.translateRegistryData769To770(packet.payload());\n"
         "                byte[] payload770to771 =\n"
         "                        PlayTranslator.translateRegistryData770To771(payload769to770);\n"
         "                yield Outcome.toClient(new MinecraftPacket(0x07,\n"
         "                        PlayTranslator.translateRegistryData771To772(payload770to771)));"),
        ("Registry Data だけ769→770→771→772へ二段で並べ替え",
         "Registry Data だけ769→770→771→772へ三段で並べ替え"),
        # 771From772 の javadoc：dialog は771にもある。tears → lava_chicken
        ("     * Registry Data だけ772→771へ並べ替え（minecraft:dialog は771に無いため\n"
         "     * 落とし、jukebox_song の tears は落とす）",
         "     * Registry Data だけ772→771へ並べ替え（minecraft:dialog は771にもあるため通し、\n"
         "     * jukebox_song の lava_chicken は落とす）"),
        # 769From772 連鎖：771To769（不存在）を771To770＋770To769に戻す
        ("                byte[] payload = PlayTranslator.translateRegistryData771To769(payload772to771);\n"
         "                if (payload == null) {\n"
         "                    yield Outcome.drop();\n"
         "                }",
         "                byte[] payload771to770 =\n"
         "                        PlayTranslator.translateRegistryData771To770(payload772to771);\n"
         "                if (payload771to770 == null) {\n"
         "                    yield Outcome.drop();\n"
         "                }\n"
         "                byte[] payload = PlayTranslator.translateRegistryData770To769(payload771to770);\n"
         "                if (payload == null) {\n"
         "                    yield Outcome.drop();\n"
         "                }"),
        ("Registry Data だけ772→771→770→769へ二段で並べ替え（minecraft:dialog と\n"
         "     * jukebox_song の tears は落とす）",
         "Registry Data だけ772→771→770→769へ三段で並べ替え（minecraft:dialog と\n"
         "     * jukebox_song の lava_chicken は落とす）"),
        # 768From772 連鎖（yield 行まで含めて From772系と区別）
        ("                byte[] payload771to769 =\n"
         "                        PlayTranslator.translateRegistryData771To769(payload772to771);\n"
         "                if (payload771to769 == null) {\n"
         "                    yield Outcome.drop();\n"
         "                }\n"
         "                byte[] payload769to768 =\n"
         "                        PlayTranslator.translateRegistryData769To768(payload771to769);\n"
         "                if (payload769to768 == null) {\n"
         "                    yield Outcome.drop();\n"
         "                }\n"
         "                yield Outcome.toClient(new MinecraftPacket(0x07, payload769to768));",
         "                byte[] payload771to770 =\n"
         "                        PlayTranslator.translateRegistryData771To770(payload772to771);\n"
         "                if (payload771to770 == null) {\n"
         "                    yield Outcome.drop();\n"
         "                }\n"
         "                byte[] payload770to769 =\n"
         "                        PlayTranslator.translateRegistryData770To769(payload771to770);\n"
         "                if (payload770to769 == null) {\n"
         "                    yield Outcome.drop();\n"
         "                }\n"
         "                byte[] payload769to768 =\n"
         "                        PlayTranslator.translateRegistryData769To768(payload770to769);\n"
         "                if (payload769to768 == null) {\n"
         "                    yield Outcome.drop();\n"
         "                }\n"
         "                yield Outcome.toClient(new MinecraftPacket(0x07, payload769to768));"),
        ("772→771→770→769→768へ三段で並べ替え",
         "772→771→770→769→768へ四段で並べ替え"),
        # 767From772 連鎖（yield 行まで含めて区別）
        ("                byte[] payload771to769 =\n"
         "                        PlayTranslator.translateRegistryData771To769(payload772to771);\n"
         "                if (payload771to769 == null) {\n"
         "                    yield Outcome.drop();\n"
         "                }\n"
         "                byte[] payload769to768 =\n"
         "                        PlayTranslator.translateRegistryData769To768(payload771to769);\n"
         "                if (payload769to768 == null) {\n"
         "                    yield Outcome.drop();\n"
         "                }\n"
         "                byte[] payload768to767 =\n"
         "                        PlayTranslator.translateRegistryData768To767(payload769to768);\n"
         "                if (payload768to767 == null) {\n"
         "                    yield Outcome.drop();\n"
         "                }\n"
         "                yield Outcome.toClient(new MinecraftPacket(0x07, payload768to767));",
         "                byte[] payload771to770 =\n"
         "                        PlayTranslator.translateRegistryData771To770(payload772to771);\n"
         "                if (payload771to770 == null) {\n"
         "                    yield Outcome.drop();\n"
         "                }\n"
         "                byte[] payload770to769 =\n"
         "                        PlayTranslator.translateRegistryData770To769(payload771to770);\n"
         "                if (payload770to769 == null) {\n"
         "                    yield Outcome.drop();\n"
         "                }\n"
         "                byte[] payload769to768 =\n"
         "                        PlayTranslator.translateRegistryData769To768(payload770to769);\n"
         "                if (payload769to768 == null) {\n"
         "                    yield Outcome.drop();\n"
         "                }\n"
         "                byte[] payload768to767 =\n"
         "                        PlayTranslator.translateRegistryData768To767(payload769to768);\n"
         "                if (payload768to767 == null) {\n"
         "                    yield Outcome.drop();\n"
         "                }\n"
         "                yield Outcome.toClient(new MinecraftPacket(0x07, payload768to767));"),
        ("772→771→770→769→768→767へ四段で並べ替え",
         "772→771→770→769→768→767へ五段で並べ替え"),
        # 766From772 連鎖（payload767to766 行まで含めて区別）
        ("                byte[] payload771to769 =\n"
         "                        PlayTranslator.translateRegistryData771To769(payload772to771);\n"
         "                if (payload771to769 == null) {\n"
         "                    yield Outcome.drop();\n"
         "                }\n"
         "                byte[] payload769to768 =\n"
         "                        PlayTranslator.translateRegistryData769To768(payload771to769);\n"
         "                if (payload769to768 == null) {\n"
         "                    yield Outcome.drop();\n"
         "                }\n"
         "                byte[] payload768to767 =\n"
         "                        PlayTranslator.translateRegistryData768To767(payload769to768);\n"
         "                if (payload768to767 == null) {\n"
         "                    yield Outcome.drop();\n"
         "                }\n"
         "                byte[] payload767to766 =\n"
         "                        PlayTranslator.translateRegistryData767To766(payload768to767);",
         "                byte[] payload771to770 =\n"
         "                        PlayTranslator.translateRegistryData771To770(payload772to771);\n"
         "                if (payload771to770 == null) {\n"
         "                    yield Outcome.drop();\n"
         "                }\n"
         "                byte[] payload770to769 =\n"
         "                        PlayTranslator.translateRegistryData770To769(payload771to770);\n"
         "                if (payload770to769 == null) {\n"
         "                    yield Outcome.drop();\n"
         "                }\n"
         "                byte[] payload769to768 =\n"
         "                        PlayTranslator.translateRegistryData769To768(payload770to769);\n"
         "                if (payload769to768 == null) {\n"
         "                    yield Outcome.drop();\n"
         "                }\n"
         "                byte[] payload768to767 =\n"
         "                        PlayTranslator.translateRegistryData768To767(payload769to768);\n"
         "                if (payload768to767 == null) {\n"
         "                    yield Outcome.drop();\n"
         "                }\n"
         "                byte[] payload767to766 =\n"
         "                        PlayTranslator.translateRegistryData767To766(payload768to767);"),
        ("772→771→770→769→768→767→766へ五段で並べ替え",
         "772→771→770→769→768→767→766へ六段で並べ替え"),
        # 764／765クライアント側の連鎖（codec 行まで含めて767From772と区別）
        ("                byte[] payload771to769 =\n"
         "                        PlayTranslator.translateRegistryData771To769(payload772to771);\n"
         "                byte[] payload769to768 =\n"
         "                        PlayTranslator.translateRegistryData769To768(payload771to769);\n"
         "                byte[] payload768to767 =\n"
         "                        PlayTranslator.translateRegistryData768To767(payload769to768);",
         "                byte[] payload771to770 =\n"
         "                        PlayTranslator.translateRegistryData771To770(payload772to771);\n"
         "                byte[] payload770to769 =\n"
         "                        PlayTranslator.translateRegistryData770To769(payload771to770);\n"
         "                byte[] payload769to768 =\n"
         "                        PlayTranslator.translateRegistryData769To768(payload770to769);\n"
         "                byte[] payload768to767 =\n"
         "                        PlayTranslator.translateRegistryData768To767(payload769to768);"),
        ("Registry Data だけ772→771→770→769→768→767→765へ三段で変換する",
         "Registry Data だけ772→771→770→769→768→767→765へ六段で変換する"),
        # handleClientConfiguration772：Custom Click Action は771・772両方にある。
        # javadoc の二重化も解消する。
        ("    /**\n"
         "     * 772クライアントの設定フェーズ（クライアント→バニラ）。\n"
         "     * 772と771の設定フェーズの番号・形式は同じため、\n"
         "     * {@link #handleClientConfiguration771} を使い回す。\n"
         "     */\n"
         "    /**\n"
         "     * 772クライアント＋内部771以下の設定フェーズ（クライアント→バニラ）。\n"
         "     * 772の番号は771と同じため、ほとんど {@link #handleClientConfiguration771}\n"
         "     * と同じ。ただし Custom Click Action（0x08）は772だけのパケットのため、\n"
         "     * 772サーバーへはそのまま送り、旧サーバー向けは落とす。\n"
         "     */\n"
         "    private Outcome handleClientConfiguration772(MinecraftPacket packet) {\n"
         "        if (packet.id() == 0x08) {\n"
         "            // Custom Click Action（772のみ。771では Custom Report Details と同ID）。\n"
         "            // 772サーバーへはそのまま送り、旧サーバー向けは落とす。\n"
         "            if (isServer772()) {\n"
         "                return Outcome.toServer(packet);\n"
         "            }\n"
         "            return Outcome.drop();\n"
         "        }\n"
         "        return handleClientConfiguration771(packet);\n"
         "    }",
         "    /**\n"
         "     * 772クライアント＋内部771以下の設定フェーズ（クライアント→バニラ）。\n"
         "     * 772の番号は771と同じため、ほとんど {@link #handleClientConfiguration771}\n"
         "     * と同じ。ただし Custom Click Action（0x08）は771・772だけのパケットのため、\n"
         "     * 771・772サーバーへはそのまま送り、旧サーバー向けは落とす。\n"
         "     */\n"
         "    private Outcome handleClientConfiguration772(MinecraftPacket packet) {\n"
         "        if (packet.id() == 0x08) {\n"
         "            // Custom Click Action（771・772のみ。770では Custom Report Details と同ID）。\n"
         "            // 771・772サーバーへはそのまま送り、旧サーバー向けは落とす。\n"
         "            if (isServer772() || isServer771()) {\n"
         "                return Outcome.toServer(packet);\n"
         "            }\n"
         "            return Outcome.drop();\n"
         "        }\n"
         "        return handleClientConfiguration771(packet);\n"
         "    }"),
        # 772From770：shift は769以下の番号をずらさないため、772クライアント
        # ←770サーバーの変換器が抜け落ちる（ディスパッチからは呼ばれる）。
        # 771From770 を雛形に二段で手書きし、772From769 の後ろに挿す。
        ("        };\n"
         "    }\n"
         "\n"
         "    /**\n"
         "     * 771クライアント＋内部772：設定フェーズを中継する。\n"
         "     * 771と772の設定フェーズの番号は同じため、ほとんど素通しする。",
         "        };\n"
         "    }\n"
         "\n"
'    /**\n     * 772クライアント＋内部770：設定フェーズを中継する。\n     * 770と772の設定フェーズの番号は同じため、ほとんど素通しする。\n     * Registry Data だけ770→771→772へ二段で並べ替え、Tags はレジストリ番号が\n     * ずれるため落とす。Cookie Request には空の Cookie Response を、Select Known\n     * Packs には空の一覧をプロキシがクライアント役として返す。\n     */\n    private Outcome handleServerConfiguration772From770(MinecraftPacket packet) {\n        return switch (packet.id()) {\n            case 0x00 -> { // Cookie Request：空の Cookie Response を返す\n                String key = readCookieKey(packet.payload());\n                yield Outcome.toServer(new MinecraftPacket(0x01,\n                        emptyCookieResponsePayload(key)));\n            }\n            case 0x01 -> Outcome.toClient(new MinecraftPacket(0x01, packet.payload())); // Custom Payload（同ID）\n            case 0x02 -> // Disconnect（770は NBT。772も NBT）\n                    Outcome.toClient(packet);\n            case 0x03 -> // Finish Configuration\n                    Outcome.toClient(packet);\n            case 0x04 -> Outcome.toClient(packet); // Keep Alive（同ID）\n            case 0x05 -> Outcome.toClient(packet); // Ping（同ID）\n            case 0x06 -> Outcome.toClient(packet); // Reset Chat（同ID）\n            case 0x07 -> { // Registry Data：770のエントリ配列を772の順序に並べ替える\n                Map<String, Nbt.Tag.CompoundTag> parsed =\n                        PlayTranslator.parseRegistryData(packet.payload());\n                registries.putAll(parsed);\n                byte[] payload770to771 =\n                        PlayTranslator.translateRegistryData770To771(packet.payload());\n                yield Outcome.toClient(new MinecraftPacket(0x07,\n                        PlayTranslator.translateRegistryData771To772(payload770to771)));\n            }\n            case 0x08 -> Outcome.toClient(packet); // Remove Resource Pack（同ID）\n            case 0x09 -> { // Add Resource Pack（770：UUID付き）\n                PendingPack pack = PlayTranslator.parseConfigAddResourcePack(packet.payload());\n                lastSentPack = pack;\n                yield Outcome.toClient(new MinecraftPacket(0x09,\n                        PlayTranslator.translateConfigAddResourcePack764To765(\n                                pack, packOverrideForClient())));\n            }\n            case 0x0A -> Outcome.toClient(packet); // Store Cookie（同ID）\n            case 0x0B -> Outcome.toClient(packet); // Transfer（同ID）\n            case 0x0C -> Outcome.toClient(packet); // Feature Flags（同ID）\n            case 0x0D -> { // Tags：レジストリ番号がずれるため落とす\n                LOG.info(() -> "レジストリ番号がずれるため Tags を772向けに落とします（" + playerName + "）。");\n                yield Outcome.drop();\n            }\n            case 0x0E -> // Select Known Packs：空の一覧を返す\n                    Outcome.toServer(new MinecraftPacket(0x07, emptySelectKnownPacksPayload()));\n            case 0x0F -> Outcome.toClient(packet); // Custom Report Details（同ID）\n            case 0x10 -> Outcome.toClient(packet); // Server Links（同ID）\n            default -> {\n                LOG.warning("770の設定フェーズで想定外のパケット（id=0x"\n                        + Integer.toHexString(packet.id()) + "）を落とします。");\n                yield Outcome.drop();\n            }\n        };\n    }\n' + "\n"
         "    /**\n"
         "     * 771クライアント＋内部772：設定フェーズを中継する。\n"
         "     * 771と772の設定フェーズの番号は同じため、ほとんど素通しする。"),
        # 771From772 の Show／Clear Dialog：771にも同ID・同形式であるため
        # 落とさず素通しする（javadoc は既に「通し」に直してあるがコードが
        # drop のまま）。番号も Clear 0x11／Show 0x12 が正しい。
        ("            case 0x11, 0x12 -> // Show Dialog／Clear Dialog（771に無い）\n"
         "                    Outcome.drop();",
         "            case 0x11, 0x12 -> // Clear Dialog（0x11）／Show Dialog（0x12）は771・772とも同ID・同形式のため素通し\n"
         "                    Outcome.toClient(packet);"),
        # 771From772 の javadoc：Show／Clear の番号と「771に無い」を直す。
        ("     * ずれるため落とす。Show Dialog（0x11）／Clear Dialog（0x12）は\n"
         "     * 771に無いため落とす。Cookie Request には空の Cookie Response を、",
         "     * ずれるため落とす。Clear Dialog（0x11）／Show Dialog（0x12）は771・772とも\n"
         "     * 同ID・同形式のため素通しする。Cookie Request には空の Cookie Response を、"),
        # 769／768From772 の javadoc：Show／Clear の番号が逆（Show 0x12／Clear 0x11）。
        ("     * 落とす。Show Dialog（0x11）／Clear Dialog（0x12）は769に無いため\n"
         "     * 落とす。Cookie Request には空の Cookie Response を、",
         "     * 落とす。Clear Dialog（0x11）／Show Dialog（0x12）は769に無いため\n"
         "     * 落とす。Cookie Request には空の Cookie Response を、"),
        ("     * 落とす。Show Dialog（0x11）／Clear Dialog（0x12）は768に無いため\n"
         "     * 落とす。Cookie Request には空の Cookie Response を、",
         "     * 落とす。Clear Dialog（0x11）／Show Dialog（0x12）は768に無いため\n"
         "     * 落とす。Cookie Request には空の Cookie Response を、"),

    ],
}


# 771版の SERVER_PLAY_771_TO（to771ServerPacket 等）を1段ずらしたもの。
# 771→772でパケットIDは変わらないため、ロケーターバー／ダイアログは
# 771にもあるため通す（770向けの落としはしない）。
SERVER_PLAY_772_TO = r'''
    /**
     * 771サーバーのプレイ状態パケットを772形式にする。772に相当が無い／
     * 変換できないものは null を返す（ログ済み）。
     * 772クライアントへの送出と、769／768／767／766クライアントへの
     * 多段変換の最終段で共用する。経験値オーブは770・771・772とも通常の
     * Spawn Entity（type experience_orb）のため、専用の分岐は要らない。
     */
    private MinecraftPacket to772ServerPacket(MinecraftPacket packet) {
        PacketName name = PacketIds.nameFor(packet.id(), State.PLAY,
                Direction.CLIENTBOUND, ProtocolVersion.V1_21_6).orElse(null);
        if (name == null) {
            logUntranslatedOnce("PLAY_UNKNOWN_0x" + Integer.toHexString(packet.id()));
            return null;
        }
        byte[] translated = switch (name) {
            case PLAY_ENTITY_METADATA ->
                    ChunkTranslator.translateEntityMetadata771To772(packet.payload());
            case PLAY_SET_SLOT ->
                    ChunkTranslator.translateSetSlot771To772(packet.payload());
            case PLAY_SET_CURSOR_ITEM ->
                    ChunkTranslator.translateSetCursorItem771To772(packet.payload());
            case PLAY_SET_PLAYER_INVENTORY ->
                    ChunkTranslator.translateSetPlayerInventory771To772(packet.payload());
            case PLAY_WINDOW_ITEMS ->
                    ChunkTranslator.translateWindowItems771To772(packet.payload());
            case PLAY_SET_COOLDOWN ->
                    ChunkTranslator.translateSetCooldown771To772(packet.payload());
            case PLAY_ENTITY_EQUIPMENT ->
                    ChunkTranslator.translateEntityEquipment771To772(packet.payload());
            case PLAY_TRADE_LIST ->
                    ChunkTranslator.translateTradeList771To772(packet.payload());
            case PLAY_OPEN_WINDOW ->
                    ChunkTranslator.translateOpenWindow771To772(packet.payload());
            case PLAY_BLOCK_ACTION ->
                    ChunkTranslator.translateBlockAction771To772(packet.payload());
            case PLAY_SPAWN_ENTITY ->
                    ChunkTranslator.translateSpawnEntity771To772(packet.payload());
            case PLAY_SOUND_EFFECT ->
                    ChunkTranslator.translateSoundEffect771To772(packet.payload());
            case PLAY_ENTITY_SOUND_EFFECT ->
                    ChunkTranslator.translateEntitySoundEffect771To772(packet.payload());
            case PLAY_STOP_SOUND ->
                    ChunkTranslator.translateStopSound771To772(packet.payload());
            case PLAY_WORLD_PARTICLES ->
                    ChunkTranslator.translateWorldParticles771To772(packet.payload());
            case PLAY_ENTITY_EFFECT ->
                    ChunkTranslator.translateEntityEffect771To772(packet.payload());
            case PLAY_REMOVE_ENTITY_EFFECT ->
                    ChunkTranslator.translateRemoveEntityEffect771To772(packet.payload());
            case PLAY_DAMAGE_EVENT ->
                    ChunkTranslator.translateDamageEvent771To772(packet.payload());
            case PLAY_ENTITY_UPDATE_ATTRIBUTES ->
                    ChunkTranslator.translateUpdateAttributes771To772(packet.payload());
            case PLAY_CHUNK_DATA ->
                    ChunkTranslator.translateChunkData771To772(packet.payload());
            case PLAY_BLOCK_CHANGE ->
                    ChunkTranslator.translateBlockChange771To772(packet.payload());
            case PLAY_MULTI_BLOCK_CHANGE ->
                    ChunkTranslator.translateMultiBlockChange771To772(packet.payload());
            case PLAY_BLOCK_ENTITY_DATA ->
                    ChunkTranslator.translateBlockEntityData771To772(packet.payload());
            case PLAY_PLAYER_INFO_UPDATE ->
                    PlayTranslator.translatePlayerInfo771To772(packet.payload());
            case PLAY_PLAYER_CHAT ->
                    PlayTranslator.translatePlayerChat771To772(packet.payload());
            case PLAY_SYSTEM_CHAT, PLAY_PROFILELESS_CHAT ->
                    packet.payload(); // 同形式
            case PLAY_SYNCHRONIZE_PLAYER_POSITION, PLAY_UPDATE_TIME, PLAY_SET_SLOT_STATE ->
                    packet.payload(); // 同形式
            case PLAY_HELD_ITEM_SLOT ->
                    PlayTranslator.translateSetHeldItem771To772(packet.payload());
            case PLAY_EXPLOSION ->
                    ChunkTranslator.translateExplosion771To772(packet.payload());
            // ロケーターバー／ダイアログは771にもあるためそのまま通す（同形式）。
            case PLAY_TRACKED_WAYPOINT, PLAY_SHOW_DIALOG, PLAY_CLEAR_DIALOG ->
                    packet.payload();
            default -> null;
        };
        if (translated == null) {
            logUntranslatedOnce(name);
            return null;
        }
        OptionalInt id772 = PacketTranslator.translateId(name,
                ProtocolVersion.V1_21_6, ProtocolVersion.V1_21_7);
        if (id772.isEmpty()) {
            logUntranslatedOnce(name);
            return null;
        }
        return new MinecraftPacket(id772.getAsInt(), translated);
    }

    /**
     * 内部771→772クライアントのプレイ状態（サーバー→クライアント）。
     * 771→772へ変換してそのまま送る。
     */
    private Outcome handleServerPlay771To772(MinecraftPacket packet) {
        MinecraftPacket as772 = to772ServerPacket(packet);
        if (as772 == null) {
            return Outcome.drop();
        }
        return Outcome.toClient(as772);
    }

    /**
     * 内部769→772クライアントのプレイ状態（サーバー→クライアント）。
     * 769→771→772へ二段で変換する。
     */
    private Outcome handleServerPlay769To772(MinecraftPacket packet) {
        Outcome to771 = handleServerPlay769To771(packet);
        if (to771.toClient().isEmpty()) {
            return to771;
        }
        return handleServerPlay771To772(to771.toClient().get(0));
    }

    /**
     * 内部768→772クライアントのプレイ状態（サーバー→クライアント）。
     * 768→771→772へ二段で変換する。
     */
    private Outcome handleServerPlay768To772(MinecraftPacket packet) {
        Outcome to771 = handleServerPlay768To771(packet);
        if (to771.toClient().isEmpty()) {
            return to771;
        }
        return handleServerPlay771To772(to771.toClient().get(0));
    }

    /**
     * 内部767→772クライアントのプレイ状態（サーバー→クライアント）。
     * 767→771→772へ二段で変換する。
     */
    private Outcome handleServerPlay767To772(MinecraftPacket packet) {
        Outcome to771 = handleServerPlay767To771(packet);
        if (to771.toClient().isEmpty()) {
            return to771;
        }
        return handleServerPlay771To772(to771.toClient().get(0));
    }

    /**
     * 内部766→772クライアントのプレイ状態（サーバー→クライアント）。
     * 766→771→772へ二段で変換する。
     */
    private Outcome handleServerPlay766To772(MinecraftPacket packet) {
        Outcome to771 = handleServerPlay766To771(packet);
        if (to771.toClient().isEmpty()) {
            return to771;
        }
        return handleServerPlay771To772(to771.toClient().get(0));
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
    for name in ("CLIENT_PLAY_771", "SERVER_PLAY_771", "CONFIG_771"):
        blocks[name] = ast.literal_eval(assigns[name])

    new_edits = []
    for name, old, new in edits:
        if new.startswith(old) and new != old:
            kind = "APPEND"
            added = new[len(old):]
            new_edits.append((name.replace("771", "772"), new, new + shift(added)))
        elif old in new and new != old:
            kind = "CONTAINS-OLD"
            assert new.endswith(old), f"{name}: CONTAINS-OLD but not suffix form"
            prefix = new[: len(new) - len(old)]
            new_edits.append((name.replace("771", "772"), new, shift(prefix) + new))
        else:
            kind = "REPLACE"
            new_edits.append((name.replace("771", "772"), new, shift(new)))
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
            print(f"SKIP: {name}（771で適用済みのため772パッチから外す）", file=sys.stderr)
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
    # 771ステージと違い、772ではメソッド名の衝突は無い（shift 後の
    # to772ClientPacket／to771ClientPacketFrom772／to771ServerPacketFrom772
    # はすべて新規名のため、改名の手直しは要らない）。
    new_blocks = {}
    for name in ("CLIENT_PLAY_771", "SERVER_PLAY_771", "CONFIG_771"):
        shifted = shift(blocks[name])
        new_name = name.replace("771", "772")
        if new_name in FIX_BLOCKS:
            shifted = apply_subs(shifted, FIX_BLOCKS[new_name], f"block {new_name}")
        new_blocks[new_name] = shifted

    # ヘッダ（手書き）と EDITS を出力し、ブロックは後段の手作業用に
    # shifted 版をそのまま載せる。
    out_lines = []
    out_lines.append('''#!/usr/bin/env python3
"""ProxySession.java へ Java 1.21.7（プロトコル772）の対応を入れる。

771パッチ（patch-proxysession-771.py）を1段ずらしたもの。生成元は
tools/gen-772-proxysession.py で、EDITS・3ブロック・INSERTS を機械的に
ずらし、772特有の箇所だけ手で直した。

772の要点（771からの差分）:
- クライアント行きのプレイ・設定・ログインのIDは771と変わらない。
  Join Game 0x2B、Spawn Position 0x5A、Chunk Data 0x27、Keep Alive 0x26、
  Ping 0x36、Start Configuration 0x6F、Disconnect 0x1C、Respawn 0x4B、
  Add Resource Pack 0x4A。
- サーバー行きは Change Gamemode 0x04 の挿入で 0x04 以降が+1ずれる。
  Chat Message 0x07→0x08、Chat Command Signed 0x06→0x07、
  Chat Command 0x05→0x06、Window Click 0x10→0x11、Vehicle Move 0x20→0x21、
  Set Creative Slot 0x36→0x37、Keep Alive 応答 0x1A→0x1B、Ping 応答 0x2B→0x2C。
  新規: Change Gamemode 0x04、Custom Click Action 0x41（共に落とす）。
- チャットの checksum（u8）は770・771・772ともあるため、772⇔771では
  付け足しも剥がしもししない（番号だけ付け替える）。770⇔771の変換器は
  そのまま（769には checksum が無い）。
- Vehicle Move の onGround は769・770・771・772ともある（768に無い）。
  772⇔771では素通しする。
- Window Click は771・770・771とも HashedSlot のため落とす。Set Creative Slot は
  771・770・771とも UntrustedSlot のため、アイテム／成分を付け替えるだけ。
- Player Chat は先頭に globalIndex が770・771・772ともあるため素通しする。
  769サーバーからの player_chat にはプロキシが 0 から数えて付ける
  （770／771／772クライアント共用の chatGlobalIndex）。
- 設定フェーズは771と同形（同じ番号・同じ形式）で中継する。
  Registry Data の並び替えだけ 771→772 が要る（minecraft:dialog は
  771には無く772にだけある。jukebox_song は tears の挿入で17番以降が+1）。
  Show Dialog（config 0x12）／Clear Dialog（0x11）は旧クライアント向けに
  落とす。Custom Click Action（config 0x08）は772サーバーへ通し、
  旧サーバー向けに落とす。Custom Report Details／Server Links
  （config サーバー行き）は772では削除されたため、772サーバー向けは落とす。
- 経験値オーブは770・771・772とも通常の Spawn Entity（type experience_orb）。
  771の to769ServerPacketFrom771 の専用分岐は772→771では使わない。
- ハッピーガスト（772: 56）は771ではガスト（55）に代替する。
  （771の invert ではブタ(94)になるが、見た目が近いガストを選ぶ。
  メタデータの個体差はプロキシが種別を追跡しないため残る。）
- ロケーターバー（tracked_waypoint 0x83）／ダイアログ（show_dialog 0x85／
  clear_dialog 0x84）は771に無いため、旧クライアント向けに落とす。
- 内部772サーバー＋772クライアントはネイティブ（素通し）。

使い方: repo ルートで `python3 detergentmc-protocol/tools/patch-proxysession-772.py`
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
    for name in ("CLIENT_PLAY_772", "SERVER_PLAY_772", "CONFIG_772"):
        out_lines.append(f"{name} = r'''{new_blocks[name]}'''")
        out_lines.append("")
    out_lines.append(f"SERVER_PLAY_772_TO = r'''{SERVER_PLAY_772_TO}'''")
    out_lines.append("")
    # INSERTS: 771版の INSERTS を流用（メソッド名だけずらす）。
    # 771版と違い、SERVER_PLAY_772_TO の挿入は shift 済み INSERTS に
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
    # CONFIG_ANCHOR：shift は委譲先の To769 を動かさないため、shift 後は
    # To771→To769 になっている。live の To771 は To770 へ委譲するため、
    # 委譲先を To770 に直す（アンカーと置換の両方に現れる）。
    rest_shifted = rest_shifted.replace(
        "    private Outcome handleClientConfigurationTo771(MinecraftPacket packet) {\n"
        "        return handleClientConfigurationTo769(packet);\n"
        "    }",
        "    private Outcome handleClientConfigurationTo771(MinecraftPacket packet) {\n"
        "        return handleClientConfigurationTo770(packet);\n"
        "    }")
    assert "return handleClientConfigurationTo770(packet);" in rest_shifted, \
        "CONFIG_ANCHOR の delegate が To770 になっていない"
    # CONFIG_ANCHOR：771パッチのアンカー（To771＋クラス末尾）は live では
    # To771 の後に CONFIG_771 のメソッド群が続くため合わない。クラスの
    # 末尾（handleServerConfiguration768From771 の末尾）をアンカーにする。
    _anchor_old = 'CONFIG_ANCHOR = """    private Outcome handleClientConfigurationTo771(MinecraftPacket packet) {\n        return handleClientConfigurationTo770(packet);\n    }\n\n}"""'
    _anchor_new = 'CONFIG_ANCHOR = """            default -> {\n                LOG.warning("771の設定フェーズで想定外のパケット（id=0x"\n                        + Integer.toHexString(packet.id()) + "）を落とします。");\n                yield Outcome.drop();\n            }\n        };\n    }\n\n\n}"""'
    assert rest_shifted.count(_anchor_old) == 1, "CONFIG_ANCHOR の shift 結果が想定と違う"
    rest_shifted = rest_shifted.replace(_anchor_old, _anchor_new, 1)
    _repl_old = 'CONFIG_REPLACEMENT = """    private Outcome handleClientConfigurationTo771(MinecraftPacket packet) {\n        return handleClientConfigurationTo770(packet);\n    }\n""" + CONFIG_772 + "\\n}\\n"'
    _repl_new = 'CONFIG_REPLACEMENT = """            default -> {\n                LOG.warning("771の設定フェーズで想定外のパケット（id=0x"\n                        + Integer.toHexString(packet.id()) + "）を落とします。");\n                yield Outcome.drop();\n            }\n        };\n    }\n""" + CONFIG_772 + "\\n}\\n"'
    assert rest_shifted.count(_repl_old) == 1, "CONFIG_REPLACEMENT の shift 結果が想定と違う"
    rest_shifted = rest_shifted.replace(_repl_old, _repl_new, 1)
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
