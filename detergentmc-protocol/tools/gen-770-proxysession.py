#!/usr/bin/env python3
"""patch-proxysession-769.py から patch-proxysession-770.py を生成する。

方針: 769パッチの EDITS・3ブロック・INSERTS を「1段ずつらす」。
- 矢印チェーン (→): 末尾が769なら「→770」を追加（鎖が伸びる）。
  2要素のペア（769→768 等）は両端を+1する（769→768→770→769）。
  それ以外の多要素チェーンは各要素+1して警告ログに出す（要目視確認）。
- ⇔チェーン・／リスト・・リスト: 末尾が769なら 770 を追加する。
  2要素 (768⇔769 等) は+1する。
- 単独の 769→770、768→769（V1_21_4→V1_21_5、V1_21_2→V1_21_4、
  1.21.4→1.21.5、1.21.2／1.21.3→1.21.4、pack769→pack770、pack768→pack769）。
- 16進リテラル (0x..) と pack_format の数値は触らない（後で手で直す）。
- CONTAINS-OLD 編集は new = PREFIX + old の形なので、
  new_770 = shift(PREFIX) + new_769、old_770 = new_769 とする。
  （全6件がこの形であることをアサートする）
- APPEND 編集は old_770 = new_769、new_770 = new_769 + shift(追加分)。
- REPLACE 編集は old_770 = new_769、new_770 = shift(new_769)。
"""
import ast
import re
import sys
from pathlib import Path

SRC = Path("/home/hatch/workspace/detergentmc/detergentmc-protocol/tools/patch-proxysession-769.py")
OUT = Path("/home/hatch/workspace/detergentmc/detergentmc-protocol/tools/patch-proxysession-770.py")

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
# is764() || is765() || ... || is769() のような条件チェーン。
# 末尾が is769()/isServer769() なら is770()/isServer770() を追加する。
IS_CHAIN = re.compile(r"((?:is(?:Server)?\d{3}\(\)\s*\|\|\s*)+is(?:Server)?769\(\))")


def shift_chain(m, kind):
    sep = "→" if kind == "arrow" else "⇔"
    nums = m.group(0).split(sep)
    if len(nums) == 2:
        # 2要素のペアは1段ずらす（768→769 は 769→770 になる）。
        return sep.join(str(int(n) + 1) for n in nums)
    if len(nums) >= 3 and nums[0] == "769":
        # 769から始まる多段チェーンは段が1つ増える（769→768→767 は
        # 770→769→768→767 になる）。末端（765/766/767）は固定。
        return "770" + sep + m.group(0)
    if nums[-1] == "769":
        return m.group(0) + sep + "770"
    warnings.append(f"arrow chain {m.group(0)} -> +1 each")
    return sep.join(str(int(n) + 1) for n in nums)


def shift_list(m, sep):
    nums = m.group(0).split(sep)
    if nums[-1] == "769":
        return m.group(0) + sep + "770"
    if nums[-1] == "768":
        # 新バージョンの一つ下まで列挙しているリストは 769 を追加する。
        return m.group(0) + sep + "769"
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
    # 1b) isチェーン（is764() || ... || is769()）に is770() を追加し、
    # そのまま保護する（後の数字シフトで二重にずれないように）。
    def is_chain_sub(m):
        chain = m.group(1)
        last = re.search(r"is(Server)?769\(\)$", chain)
        suffix = "isServer770()" if last.group(1) else "is770()"
        key = f"\x02{_counter[0]}\x03"
        _counter[0] += 1
        is_chain_holders[key] = chain + " || " + suffix
        return key

    is_chain_holders = {}
    text = IS_CHAIN.sub(is_chain_sub, text)
    holders = {**h0, **h1, **h2, **h3, **h4, **is_chain_holders}
    # 2) バージョン文字列
    text = text.replace("1.21.2／1.21.3", "1.21.4")
    text = text.replace("1.21.4", "1.21.5")
    text = text.replace("1.21.2", "1.21.4")
    text = text.replace("V1_21_4", "V1_21_5")
    text = text.replace("V1_21_2", "V1_21_4")
    text = text.replace("PROTOCOL_769", "PROTOCOL_770")
    text = text.replace("PROTOCOL_768", "PROTOCOL_769")
    text = text.replace("pack769", "pack770")
    text = text.replace("pack768", "pack769")
    # pack769/pack770 は数字シフトの対象外にする（pack769→pack770 と
    # 数字シフトが二重にかかると pack770→pack770 のような誤りになる）。
    def pack_sub(m):
        key = f"\x02{_counter[0]}\x03"
        _counter[0] += 1
        pack_holders[key] = m.group(0)
        return key

    pack_holders = {}
    text = re.sub(r"pack(?:769|770)", pack_sub, text)
    holders.update(pack_holders)
    # 3) 単独の数字。\b は日本語文字を \w と見なすため使えないので、
    # 数字の前後を見る lookaround を使う（識別子内の 769 も対象にする）。
    # 16進リテラル (0x..）は数字続きなので対象外になる。
    text = re.sub(r"(?<!\d)769(?!\d)", "\x01769", text)
    text = re.sub(r"(?<!\d)768(?!\d)", "769", text)
    text = text.replace("\x01769", "770")
    # 4) 復元
    return restore(text, holders)


FIX_EDITS = {
    # name -> [(old_sub, new_sub), ...] を NEW テキストに適用する（各1回一致）。
    # shift の誤変換の修正：767→768 の翻訳器は 769／770 でも同形式のため
    # そのまま使う（767To769 というメソッドは存在しない）。
    "login clientInformation": [
        ("translateClientInformation767To769", "translateClientInformation767To768"),
    ],
    "synthesized add-pack": [
        ("// プレイ状態の Add Resource Pack は 0x4B（PLAY_ADD_RESOURCE_PACK。770も同ID）。",
         "// プレイ状態の Add Resource Pack は 0x4B（PLAY_ADD_RESOURCE_PACK）。770は 0x4A。"),
    ],
    "constructor javadoc": [
        ("769＝1.21.4）", "769＝1.21.4、770＝1.21.5）"),
    ],
    "constructor validation": [
        ("&& serverProtocol != ProtocolVersion.V1_21\n"
         "                && serverProtocol != ProtocolVersion.V1_21_4",
         "&& serverProtocol != ProtocolVersion.V1_21\n"
         "                && serverProtocol != ProtocolVersion.V1_21_2\n"
         "                && serverProtocol != ProtocolVersion.V1_21_4"),
        ("768 (1.21.2/1.21.3) or 769 (1.21.4): ",
         "768 (1.21.2/1.21.3), 769 (1.21.4) or 770 (1.21.5): "),
    ],
    "serverProtocol() javadoc": [
        ("769＝1.21.4）。", "769＝1.21.4、770＝1.21.5）。"),
    ],
    "pack770 field": [
        ("770クライアントに送るリソースパック（pack_format 46）",
         "770クライアントに送るリソースパック（pack_format 55）"),
    ],
    "packOverrideForClient javadoc": [
        ("770→pack770\n     * （pack_format 46）、769→pack769\n     * （pack_format 42）、767→pack767",
         "770→pack770\n     * （pack_format 55）、769→pack769\n     * （pack_format 46）、768→pack768\n     * （pack_format 42）、767→pack767"),
    ],
    "packOverrideForClient branch": [
        (": pack769 != null ? pack769\n"
         "                    : pack767 != null ? pack767 : pack766;\n"
         "        }\n        if (clientProtocol == ProtocolVersion.V1_21_4)",
         ": pack769 != null ? pack769\n"
         "                    : pack768 != null ? pack768\n"
         "                    : pack767 != null ? pack767 : pack766;\n"
         "        }\n        if (clientProtocol == ProtocolVersion.V1_21_4)"),
    ],
    "synthesized config registry loop": [
        ("// 770：766→767→768→769→770へ三段で並べ替える（pale_garden を挿入）。",
         "// 770：766→767→768→769→770へ四段で並べ替える。"),
        ("                    byte[] payload767to769 =\n"
         "                            PlayTranslator.translateRegistryData767To769(payload766to767);\n"
         "                    registryPayload =\n"
         "                            PlayTranslator.translateRegistryData769To770(payload767to769);",
         "                    byte[] payload767to768 =\n"
         "                            PlayTranslator.translateRegistryData767To768(payload766to767);\n"
         "                    byte[] payload768to769 =\n"
         "                            PlayTranslator.translateRegistryData768To769(payload767to768);\n"
         "                    registryPayload =\n"
         "                            PlayTranslator.translateRegistryData769To770(payload768to769);"),
    ],
    "startConfigurationClientId": [
        (".orElse(is768() || is769() || is770() ? 0x70 : 0x69)",
         ".orElse(is768() || is769() ? 0x70 : is770() ? 0x6F : 0x69)"),
    ],
    "spawnPositionClientId": [
        (".orElse(is768() || is769() || is770() ? 0x5B : 0x56)",
         ".orElse(is768() || is769() ? 0x5B : is770() ? 0x5A : 0x56)"),
    ],
    "keepAliveClientId": [
        (".orElse(is768() || is769() || is770() ? 0x27 : 0x26)",
         ".orElse(is768() || is769() ? 0x27 : 0x26)"),
    ],
    "pingClientId": [
        (".orElse(is768() || is769() || is770() ? 0x37 : 0x35)",
         ".orElse(is768() || is769() ? 0x37 : is770() ? 0x36 : 0x35)"),
    ],
    "handleClientPlay dispatch": [
        ("内部767／766向けは770→769→768→767／766へ",
         "内部768／767向けは770→769→768→767／766へ"),
    ],
    "handleServerPlay dispatch": [
        ("内部770：Join Game は 0x2C、Spawn Position は 0x5B、\n"
         "        // Start Configuration は 0x70（769と同じ番号）。",
         "内部770：Join Game は 0x2B、Spawn Position は 0x5A、\n"
         "        // Start Configuration は 0x6F（769から -1）。"),
    ],
    "handleClientConfiguration 767+770 branch": [
        ("767クライアント＋内部770：番号を770向けに付け替える。769と\n"
         "        // 同じ番号・形式のため769向けの処理を使い回す。\n"
         "        if (is767() && isServer770()) {\n"
         "            return handleClientConfiguration767To769(packet);",
         "767クライアント＋内部770：番号を770向けに付け替える。769と\n"
         "        // 同じ番号・形式のため768向けの処理を使い回す。\n"
         "        if (is767() && isServer770()) {\n"
         "            return handleClientConfiguration767To768(packet);"),
    ],
    "handleClientConfiguration 766+770 branch": [
        ("766クライアント＋内部770：番号を770向けに付け替える。769と\n"
         "        // 同じ番号・形式のため769向けの処理を使い回す。\n"
         "        if (is766() && isServer770()) {\n"
         "            return handleClientConfiguration766To769(packet);",
         "766クライアント＋内部770：番号を770向けに付け替える。769と\n"
         "        // 同じ番号・形式のため768向けの処理を使い回す。\n"
         "        if (is766() && isServer770()) {\n"
         "            return handleClientConfiguration766To768(packet);"),
    ],
    # 設定フェーズの dispatch：shift は 768 分岐を 769 にずらしてしまい
    # 元の 768 分岐が消える。763クライアント／770クライアントの両分岐で
    # 768 分岐を戻す。これを入れないと 763×768（設定フェーズの消費）と
    # 770×768（設定フェーズの中継）が壊れる（2026-11-11 に実テストで発覚）。
    "handleServerConfiguration 763 branch": [
        ("            if (isServer769()) {\n                return handleServerConfiguration763From769(packet);\n            }",
         "            if (isServer769()) {\n                return handleServerConfiguration763From769(packet);\n            }\n            if (isServer768()) {\n                return handleServerConfiguration763From768(packet);\n            }"),
    ],
    "handleServerConfiguration 770 branch": [
        # 770クライアント＋内部768の分岐。メソッド handleServerConfiguration770From768
        # は CONFIG_770 に別途追加すること（この FIX_EDITS だけでは足りない。
        # patch-proxysession-770.py の CONFIG_770 ブロックを参照）。
        ("            if (isServer769()) {\n                return handleServerConfiguration770From769(packet);\n            }",
         "            if (isServer769()) {\n                return handleServerConfiguration770From769(packet);\n            }\n            if (isServer768()) {\n                return handleServerConfiguration770From768(packet);\n            }"),
    ],
}

ADD_EDITS = [
    # synthesized add-pack の構築行（0x4B→770は 0x4A）。
    # 'synthesized add-pack' の NEW（2行）には構築行が含まれないため、
    # 別エントリで直す（add-pack 編集の後に実行される）。
    ("synthesized add-pack 770 id",
     "            if (is768() || is769() || is770()) {\n"
     "                // プレイ状態の Add Resource Pack は 0x4B（PLAY_ADD_RESOURCE_PACK）。770は 0x4A。\n"
     "                toClient.add(new MinecraftPacket(0x4B,\n"
     "                        PlayTranslator.translateConfigAddResourcePack764To765(\n"
     "                                pack, packOverrideForClient())));",
     "            if (is768() || is769() || is770()) {\n"
     "                // プレイ状態の Add Resource Pack は 0x4B（PLAY_ADD_RESOURCE_PACK）。770は 0x4A。\n"
     "                toClient.add(new MinecraftPacket(is770() ? 0x4A : 0x4B,\n"
     "                        PlayTranslator.translateConfigAddResourcePack764To765(\n"
     "                                pack, packOverrideForClient())));"),
    # chatGlobalIndex カウンタ（player_chat の globalIndex 合成用）。
    # 'pack770 field' の NEW テキストをアンカーにするため末尾に追加する。
    ("chatGlobalIndex field",
     "    /**\n"
     "     * 770クライアントに送るリソースパック（pack_format 55）。\n"
     "     * 内部が770でない場合に使う差し替え用。内部が770の場合はバニラが\n"
     "     * 送ってきたパックをそのまま出すため null のままでよい。\n"
     "     * 出し分けの基準はクライアントのプロトコルである\n"
     "     * （{@link #packOverrideForClient()} 参照）。\n"
     "     */\n"
     "    private ResourcePack pack770;",
     "    /**\n"
     "     * 770クライアントに送るリソースパック（pack_format 55）。\n"
     "     * 内部が770でない場合に使う差し替え用。内部が770の場合はバニラが\n"
     "     * 送ってきたパックをそのまま出すため null のままでよい。\n"
     "     * 出し分けの基準はクライアントのプロトコルである\n"
     "     * （{@link #packOverrideForClient()} 参照）。\n"
     "     */\n"
     "    private ResourcePack pack770;\n"
     "\n"
     "    /**\n"
     "     * 770クライアントへ送る player_chat に付ける globalIndex。\n"
     "     * 769サーバーは globalIndex を送ってこないため、プロキシが\n"
     "     * セッションごとに 0 から数える。\n"
     "     */\n"
     "    private int chatGlobalIndex = 0;"),
    # 769サーバー→770クライアント：一般プレイ状態の変換。
    ("handleServerPlayFrom769 770 branch",
     "        // 768クライアント：769→768へ変換する。\n"
     "        if (is768()) {\n"
     "            return handleServerPlay769To768(packet);\n"
     "        }",
     "        // 770クライアント：769→770へ変換する。\n"
     "        if (is770()) {\n"
     "            return handleServerPlay769To770(packet);\n"
     "        }\n"
     "        // 768クライアント：769→768へ変換する。\n"
     "        if (is768()) {\n"
     "            return handleServerPlay769To768(packet);\n"
     "        }"),
    ("handleServerPlayFrom769 Respawn 770",
     "        // Respawn（769：0x4C。768と同形式のため768の変換を使い回す）。\n"
     "        if (packet.id() == 0x4C) {\n"
     "            if (is768()) {",
     "        // Respawn（769：0x4C。768と同形式のため768の変換を使い回す）。\n"
     "        if (packet.id() == 0x4C) {\n"
     "            if (is770()) {\n"
     "                // 770クライアント：Respawn は 0x4B（同形式）のまま。\n"
     "                return Outcome.toClient(new MinecraftPacket(0x4B, packet.payload()));\n"
     "            }\n"
     "            if (is768()) {"),
    ("handleServerPlayFrom769 Disconnect 770",
     "            if (is766() || is767() || is768()) {\n"
     "                // 766／767／768クライアント：Disconnect は 0x1D（NBT）のまま。",
     "            if (is770()) {\n"
     "                // 770クライアント：Disconnect は 0x1C（NBT）のまま。\n"
     "                return new Outcome(List.of(),\n"
     "                        List.of(new MinecraftPacket(0x1C, packet.payload())), false, true);\n"
     "            }\n"
     "            if (is766() || is767() || is768()) {\n"
     "                // 766／767／768クライアント：Disconnect は 0x1D（NBT）のまま。"),
    ("handleServerJoinGame769 770 branch",
     "        if (is768()) {\n"
     "            advanceFlowSafely(() -> joinFlow.joinGameReceived());\n"
     "            List<MinecraftPacket> toClient = new ArrayList<>();\n"
     "            toClient.add(new MinecraftPacket(0x2C, packet.payload()));",
     "        if (is770()) {\n"
     "            advanceFlowSafely(() -> joinFlow.joinGameReceived());\n"
     "            List<MinecraftPacket> toClient = new ArrayList<>();\n"
     "            toClient.add(new MinecraftPacket(0x2B, packet.payload()));\n"
     "            while (!pendingPacks.isEmpty()) {\n"
     "                PendingPack pack = pendingPacks.removeFirst();\n"
     "                lastSentPack = pack;\n"
     "                toClient.add(new MinecraftPacket(0x4A,\n"
     "                        PlayTranslator.translateConfigAddResourcePack764To765(\n"
     "                                pack, packOverrideForClient())));\n"
     "            }\n"
     "            return new Outcome(List.of(), toClient, false, false);\n"
     "        }\n"
     "        if (is768()) {\n"
     "            advanceFlowSafely(() -> joinFlow.joinGameReceived());\n"
     "            List<MinecraftPacket> toClient = new ArrayList<>();\n"
     "            toClient.add(new MinecraftPacket(0x2C, packet.payload()));"),
    # 768サーバー→770クライアント。
    ("handleServerPlayFrom768 Respawn 770",
     "        // Respawn（768：0x4C）。\n"
     "        if (packet.id() == 0x4C) {\n"
     "            if (is769()) {",
     "        // Respawn（768：0x4C）。\n"
     "        if (packet.id() == 0x4C) {\n"
     "            if (is770()) {\n"
     "                // 770クライアント：Respawn は 0x4B（同形式）のまま。\n"
     "                return Outcome.toClient(new MinecraftPacket(0x4B, packet.payload()));\n"
     "            }\n"
     "            if (is769()) {"),
    ("handleServerPlayFrom768 Disconnect 770",
     "            if (is766() || is767() || is769()) {\n"
     "                // 766／767／769クライアント：Disconnect は 0x1D（NBT）のまま。",
     "            if (is770()) {\n"
     "                // 770クライアント：Disconnect は 0x1C（NBT）のまま。\n"
     "                return new Outcome(List.of(),\n"
     "                        List.of(new MinecraftPacket(0x1C, packet.payload())), false, true);\n"
     "            }\n"
     "            if (is766() || is767() || is769()) {\n"
     "                // 766／767／769クライアント：Disconnect は 0x1D（NBT）のまま。"),
    ("handleServerPlayFrom768 770 branch",
     "        // 769クライアント：スロット等は768→769へ変換する。\n"
     "        if (is769()) {\n"
     "            return handleServerPlay768To769(packet);\n"
     "        }",
     "        // 770クライアント：スロット等は768→769→770へ変換する。\n"
     "        if (is770()) {\n"
     "            return handleServerPlay768To770(packet);\n"
     "        }\n"
     "        // 769クライアント：スロット等は768→769へ変換する。\n"
     "        if (is769()) {\n"
     "            return handleServerPlay768To769(packet);\n"
     "        }"),
    ("handleServerJoinGame768 770 branch",
     "        if (is769()) {\n"
     "            // 769クライアント：Join Game は768と同形式のためそのまま通す。\n"
     "            advanceFlowSafely(() -> joinFlow.joinGameReceived());\n"
     "            List<MinecraftPacket> toClient = new ArrayList<>();\n"
     "            toClient.add(new MinecraftPacket(0x2C, packet.payload()));",
     "        if (is770()) {\n"
     "            // 770クライアント：Join Game は 0x2B、Add Resource Pack は 0x4A。\n"
     "            advanceFlowSafely(() -> joinFlow.joinGameReceived());\n"
     "            List<MinecraftPacket> toClient = new ArrayList<>();\n"
     "            toClient.add(new MinecraftPacket(0x2B, packet.payload()));\n"
     "            while (!pendingPacks.isEmpty()) {\n"
     "                PendingPack pack = pendingPacks.removeFirst();\n"
     "                lastSentPack = pack;\n"
     "                toClient.add(new MinecraftPacket(0x4A,\n"
     "                        PlayTranslator.translateConfigAddResourcePack764To765(\n"
     "                                pack, packOverrideForClient())));\n"
     "            }\n"
     "            return new Outcome(List.of(), toClient, false, false);\n"
     "        }\n"
     "        if (is769()) {\n"
     "            // 769クライアント：Join Game は768と同形式のためそのまま通す。\n"
     "            advanceFlowSafely(() -> joinFlow.joinGameReceived());\n"
     "            List<MinecraftPacket> toClient = new ArrayList<>();\n"
     "            toClient.add(new MinecraftPacket(0x2C, packet.payload()));"),
    # 766／767サーバー→770クライアント。
    ("handleServerPlayFrom766 770 branch",
     "        // 769クライアント：スロット等は766／767→768→769へ変換する。\n"
     "        // 内部766は 766→767→768→769 の三段、内部767は 767→768→769 の二段で送る。\n"
     "        if (is769()) {\n"
     "            return isServer766() ? handleServerPlay766To769(packet)\n"
     "                    : handleServerPlay767To769(packet);\n"
     "        }",
     "        // 770クライアント：スロット等は766／767→768→769→770へ変換する。\n"
     "        // 内部766は 766→767→768→769→770 の四段、内部767は 767→768→769→770 の三段で送る。\n"
     "        if (is770()) {\n"
     "            return isServer766() ? handleServerPlay766To770(packet)\n"
     "                    : handleServerPlay767To770(packet);\n"
     "        }\n"
     "        // 769クライアント：スロット等は766／767→768→769へ変換する。\n"
     "        // 内部766は 766→767→768→769 の三段、内部767は 767→768→769 の二段で送る。\n"
     "        if (is769()) {\n"
     "            return isServer766() ? handleServerPlay766To769(packet)\n"
     "                    : handleServerPlay767To769(packet);\n"
     "        }"),
    # 763サーバー→770クライアント（join レベルのみ）。
    ("handleServerPlayFrom763 ResourcePack 770",
     "            PendingPack pack = parseResourcePack764(packet.payload());\n"
     "            lastSentPack = pack;\n"
     "            if (is766() || is767() || is768() || is769()) {\n"
     "                return Outcome.toClient(new MinecraftPacket(is768() || is769() ? 0x4B : 0x46,",
     "            PendingPack pack = parseResourcePack764(packet.payload());\n"
     "            lastSentPack = pack;\n"
     "            if (is766() || is767() || is768() || is769() || is770()) {\n"
     "                return Outcome.toClient(new MinecraftPacket(is768() || is769() ? 0x4B : is770() ? 0x4A : 0x46,"),
    ("handleServerPlayFrom763 Disconnect 770",
     "            if (is766() || is767() || is768() || is769()) {\n"
     "                byte[] payload = PlayTranslator.translateDisconnect764To765(packet.payload());\n"
     "                return new Outcome(List.of(),\n"
     "                        List.of(new MinecraftPacket(0x1D, payload)), false, true);\n"
     "            }",
     "            if (is770()) {\n"
     "                byte[] payload = PlayTranslator.translateDisconnect764To765(packet.payload());\n"
     "                return new Outcome(List.of(),\n"
     "                        List.of(new MinecraftPacket(0x1C, payload)), false, true);\n"
     "            }\n"
     "            if (is766() || is767() || is768() || is769()) {\n"
     "                byte[] payload = PlayTranslator.translateDisconnect764To765(packet.payload());\n"
     "                return new Outcome(List.of(),\n"
     "                        List.of(new MinecraftPacket(0x1D, payload)), false, true);\n"
     "            }"),
    ("handleServerPlayFrom763 Respawn 770",
     "        if (name == PacketName.PLAY_RESPAWN) {\n"
     "            if (is766() || is767() || is768() || is769()) {",
     "        if (name == PacketName.PLAY_RESPAWN) {\n"
     "            if (is766() || is767() || is768() || is769() || is770()) {"),
    ("handleServerPlayFrom763 Advancements 770",
     "        if (name == PacketName.PLAY_ADVANCEMENTS) {\n"
     "            if (is766() || is767() || is768() || is769()) {",
     "        if (name == PacketName.PLAY_ADVANCEMENTS) {\n"
     "            if (is766() || is767() || is768() || is769() || is770()) {"),
    ("handleServerPlayFrom763 final drop 770",
     "        // 766／767／768／769向け：チャット・表示系は763が JSON 文字列・766が NBT のため、\n"
     "        // JSON のまま送ると誤読される。チャンク・ブロック状態・スロット・\n"
     "        // エンティティも766の変換が要る。Join Game／Spawn Position／\n"
     "        // Keep Alive／Ping／Respawn 以外は未対応のため落とす\n"
     "        // （766のプレイ状態の変換は別担当の対応表待ち）。\n"
     "        if (is766() || is767() || is768() || is769()) {",
     "        // 766／767／768／769／770向け：チャット・表示系は763が JSON 文字列・766が NBT のため、\n"
     "        // JSON のまま送ると誤読される。チャンク・ブロック状態・スロット・\n"
     "        // エンティティも766の変換が要る。Join Game／Spawn Position／\n"
     "        // Keep Alive／Ping／Respawn 以外は未対応のため落とす\n"
     "        // （766のプレイ状態の変換は別担当の対応表待ち）。\n"
     "        if (is766() || is767() || is768() || is769() || is770()) {"),
    # 769→768 のチャット欠落の修正（769では player_chat／system_chat／
    # profileless_chat が 768↔769 で同形式なのにホワイトリストから漏れて
    # 落とされていた）。770への二段変換でも要る。
    ("to768ServerPacket chat passthrough",
     "            case PLAY_PLAYER_INFO_UPDATE ->\n"
     "                    PlayTranslator.translatePlayerInfo769To768(packet.payload());\n"
     "            case PLAY_SYNCHRONIZE_PLAYER_POSITION, PLAY_UPDATE_TIME, PLAY_SET_SLOT_STATE ->\n"
     "                    packet.payload(); // 同形式",
     "            case PLAY_PLAYER_INFO_UPDATE ->\n"
     "                    PlayTranslator.translatePlayerInfo769To768(packet.payload());\n"
     "            case PLAY_PLAYER_CHAT, PLAY_SYSTEM_CHAT, PLAY_PROFILELESS_CHAT ->\n"
     "                    packet.payload(); // 同形式（769→768 は番号だけ）\n"
     "            case PLAY_SYNCHRONIZE_PLAYER_POSITION, PLAY_UPDATE_TIME, PLAY_SET_SLOT_STATE ->\n"
     "                    packet.payload(); // 同形式"),
    # 768→767 のチャット欠落の修正（769では 768↔767 で同形式なのに
    # ホワイトリストから漏れて落とされていた。770→767 でも要る）。
    ("handleServerPlay768To767 chat passthrough",
     "            case PLAY_PLAYER_INFO_UPDATE -> PlayTranslator.translatePlayerInfo768To767(\n"
     "                    packet.payload());\n"
     "            case PLAY_SYNCHRONIZE_PLAYER_POSITION -> PlayTranslator.translatePosition768To767(",
     "            case PLAY_PLAYER_INFO_UPDATE -> PlayTranslator.translatePlayerInfo768To767(\n"
     "                    packet.payload());\n"
     "            case PLAY_PLAYER_CHAT, PLAY_SYSTEM_CHAT, PLAY_PROFILELESS_CHAT ->\n"
     "                    packet.payload(); // 同形式（768→767 は番号だけ）\n"
     "            case PLAY_SYNCHRONIZE_PLAYER_POSITION -> PlayTranslator.translatePosition768To767("),
    # 767→766 の system_chat のみ素通し（同ID・同形式。player_chat／
    # profileless_chat は 767 が ChatTypesHolder、766 が varint のため
    # 別途の変換が要る。残る制限として報告する）。
    ("handleServerPlay767To766 system_chat passthrough",
     "            case PLAY_ENTITY_UPDATE_ATTRIBUTES -> ChunkTranslator\n"
     "                    .translateUpdateAttributes767To766(packet.payload());\n"
     "            case PLAY_CHUNK_DATA, PLAY_BLOCK_CHANGE, PLAY_MULTI_BLOCK_CHANGE,\n"
     "                    PLAY_BLOCK_ENTITY_DATA -> packet.payload();",
     "            case PLAY_ENTITY_UPDATE_ATTRIBUTES -> ChunkTranslator\n"
     "                    .translateUpdateAttributes767To766(packet.payload());\n"
     "            case PLAY_SYSTEM_CHAT ->\n"
     "                    packet.payload(); // 同形式（767→766 は番号だけ。ID も同じ 0x6C）\n"
     "            case PLAY_CHUNK_DATA, PLAY_BLOCK_CHANGE, PLAY_MULTI_BLOCK_CHANGE,\n"
     "                    PLAY_BLOCK_ENTITY_DATA -> packet.payload();"),
]

FIX_BLOCKS = {
    "CLIENT_PLAY_770": [
        ("     * 770だけのパケット（Pick Item From Block／Entity、Player Loaded）は\n"
         "     * 769に相当が無く、Pick Item は世界の情報が無いため変換できないので\n"
         "     * 落とす。Vehicle Move は末尾の onGround を落とす。",
         "     * Pick Item From Block／Entity、Player Loaded は769にもあり同形式のため\n"
         "     * そのまま通す。Vehicle Move は末尾の onGround を落とす。Window Click は\n"
         "     * HashedSlot のため769の Slot に戻せず落とす。Set Creative Slot は\n"
         "     * UntrustedSlot から769の Slot に組み直す。"),
        # Pick Item From Block/Entity と Player Loaded の落としを削除する
        # （769にもあるため汎用付け替えに任せる）。
        ("        // Pick Item From Block（770：0x22）／From Entity（770：0x23）：\n"
         "        // 770は位置・対象を指定するが、旧形式の Pick Item はスロット番号を\n"
         "        // 指定するため、世界の情報が無いプロキシでは変換できない。\n"
         "        if (packet.id() == 0x22 || packet.id() == 0x23) {\n"
         "            logUntranslatedOnce(\"PLAY_PICK_ITEM_FROM_\"\n"
         "                    + (packet.id() == 0x22 ? \"BLOCK\" : \"ENTITY\"));\n"
         "            return null;\n"
         "        }\n"
         "        // Player Loaded（770：0x2A）：読み込み完了の通知。769のサーバーが\n"
         "        // 待っていないため無くても進む。\n"
         "        if (packet.id() == 0x2A) {\n"
         "            return null;\n"
         "        }\n",
         ""),
        # Window Click は落とす（HashedSlot→Slot は不可）。
        ("        // Window Click（770：0x10）：スロットを770→769へ変換する。\n"
         "        if (packet.id() == 0x10) {\n"
         "            return new MinecraftPacket(0x10,\n"
         "                    ChunkTranslator.translateWindowClick770To769(packet.payload()));\n"
         "        }",
         "        // Window Click（770：0x10）：HashedSlot はハッシュしか持たないため\n"
         "        // 769の Slot には戻せない。落とす（ハッシュの計算方法は未確定）。\n"
         "        if (packet.id() == 0x10) {\n"
         "            logUntranslatedOnce(\"PLAY_WINDOW_CLICK\");\n"
         "            return null;\n"
         "        }"),
        # Set Creative Slot：769の ID は 0x36（0x34 は768のもの）。
        ("        // Set Creative Slot（770：0x36→769：0x34）：スロットを変換する。\n"
         "        if (packet.id() == 0x36) {\n"
         "            return new MinecraftPacket(0x34,\n"
         "                    ChunkTranslator.translateSetCreativeSlot770To769(packet.payload()));\n"
         "        }",
         "        // Set Creative Slot（770：0x36→769：0x36、同ID）：\n"
         "        // UntrustedSlot（生バイト列のコンポーネント）から769の Slot に組み直す。\n"
         "        if (packet.id() == 0x36) {\n"
         "            return new MinecraftPacket(0x36,\n"
         "                    ChunkTranslator.translateSetCreativeSlot770To769(packet.payload()));\n"
         "        }"),
        # Chat Message／Chat Command Signed：末尾の checksum を剥がす。
        ("        // それ以外：番号だけ769向けに付け替える（ペイロードは同形式）。\n"
         "        Optional<PacketName> name = PacketIds.nameFor(packet.id(), State.PLAY,\n"
         "                Direction.SERVERBOUND, ProtocolVersion.V1_21_5);\n"
         "        if (name.isEmpty()) {",
         "        // Chat Message／Chat Command Signed：末尾の checksum（u8）を剥がす。\n"
         "        if (packet.id() == 0x07 || packet.id() == 0x06) {\n"
         "            byte[] payload = PlayTranslator.stripChatChecksum770(packet.payload());\n"
         "            OptionalInt id769 = PacketTranslator.translateId(\n"
         "                    packet.id() == 0x07 ? PacketName.PLAY_CHAT_MESSAGE\n"
         "                            : PacketName.PLAY_CHAT_COMMAND_SIGNED,\n"
         "                    ProtocolVersion.V1_21_5, ProtocolVersion.V1_21_4);\n"
         "            if (id769.isEmpty()) {\n"
         "                return null;\n"
         "            }\n"
         "            return new MinecraftPacket(id769.getAsInt(), payload);\n"
         "        }\n"
         "        // それ以外：番号だけ769向けに付け替える（ペイロードは同形式）。\n"
         "        Optional<PacketName> name = PacketIds.nameFor(packet.id(), State.PLAY,\n"
         "                Direction.SERVERBOUND, ProtocolVersion.V1_21_5);\n"
         "        if (name.isEmpty()) {"),
        # 767クライアントの Window Click は落とす、Set Creative Slot は三段で。
        ("     * Window Click／Set Creative Slot は767→768→769→770へ二段で変換する",
         "     * Set Creative Slot は767→768→769→770へ三段で変換する"),
        ("     * （766以下はデータコンポーネント形式のため落とす）。",
         "     * （Window Click は HashedSlot のため落とす。766以下はデータコンポーネント形式のため落とす）。"),
        ("            case PLAY_WINDOW_CLICK -> {\n"
         "                if (is767()) {\n"
         "                    byte[] payload = ChunkTranslator.translateWindowClick769To770(\n"
         "                            ChunkTranslator.translateWindowClick767To769(packet.payload()));\n"
         "                    return Outcome.toServer(new MinecraftPacket(0x10, payload));\n"
         "                }\n"
         "                logUntranslatedOnce(\"PLAY_WINDOW_CLICK\");\n"
         "                return Outcome.drop();\n"
         "            }",
         "            case PLAY_WINDOW_CLICK -> {\n"
         "                // HashedSlot のハッシュ計算方法が未確定のため、770向けは落とす。\n"
         "                logUntranslatedOnce(\"PLAY_WINDOW_CLICK\");\n"
         "                return Outcome.drop();\n"
         "            }"),
        ("            case PLAY_SET_CREATIVE_SLOT -> {\n"
         "                if (is767()) {\n"
         "                    byte[] payload = ChunkTranslator.translateSetCreativeSlot769To770(\n"
         "                            ChunkTranslator.translateSetCreativeSlot767To769(packet.payload()));\n"
         "                    return Outcome.toServer(new MinecraftPacket(0x36, payload));\n"
         "                }",
         "            case PLAY_SET_CREATIVE_SLOT -> {\n"
         "                if (is767()) {\n"
         "                    byte[] payload = ChunkTranslator.translateSetCreativeSlot769To770(\n"
         "                            ChunkTranslator.translateSetCreativeSlot768To769(\n"
         "                                    ChunkTranslator.translateSetCreativeSlot767To768(packet.payload())));\n"
         "                    return Outcome.toServer(new MinecraftPacket(0x36, payload));\n"
         "                }"),
        # to770ClientPacket：Pick Item の落としを削除する（0x22 は
        # 769の Pick Item From Block。汎用付け替えで 0x22 のまま通す）。
        ("        // Pick Item（769：0x22）：770には位置・対象指定の Pick Item From\n"
         "        // Block／Entity しか無いため、スロット番号からは変換できない。\n"
         "        if (packet.id() == 0x22) {\n"
         "            logUntranslatedOnce(\"PLAY_PICK_ITEM\");\n"
         "            return null;\n"
         "        }\n",
         ""),
        # to770ClientPacket：Window Click は落とす。
        ("        // Window Click（769：0x10）：スロットを769→770へ変換する。\n"
         "        if (packet.id() == 0x10) {\n"
         "            return new MinecraftPacket(0x10,\n"
         "                    ChunkTranslator.translateWindowClick769To770(packet.payload()));\n"
         "        }",
         "        // Window Click（769：0x10）：770は HashedSlot で、コンポーネントの\n"
         "        // ハッシュ計算方法が未確定のため落とす。\n"
         "        if (packet.id() == 0x10) {\n"
         "            logUntranslatedOnce(\"PLAY_WINDOW_CLICK\");\n"
         "            return null;\n"
         "        }"),
        # to770ClientPacket：Set Creative Slot の 769側 ID は 0x36。
        ("        // Set Creative Slot（769：0x34→770：0x36）：スロットを変換する。\n"
         "        if (packet.id() == 0x34) {\n"
         "            return new MinecraftPacket(0x36,\n"
         "                    ChunkTranslator.translateSetCreativeSlot769To770(packet.payload()));\n"
         "        }",
         "        // Set Creative Slot（769：0x36→770：0x36、同ID）：Slot の\n"
         "        // コンポーネントを生バイト列にした UntrustedSlot にする。\n"
         "        if (packet.id() == 0x36) {\n"
         "            return new MinecraftPacket(0x36,\n"
         "                    ChunkTranslator.translateSetCreativeSlot769To770(packet.payload()));\n"
         "        }"),
        # to770ClientPacket：Chat Message／Chat Command Signed に checksum を付ける
        # （値は未検証の 0x00。実バニラで確認するまでの一時値）。
        ("        // それ以外：番号だけ770向けに付け替える（ペイロードは同形式）。\n"
         "        Optional<PacketName> name = PacketIds.nameFor(packet.id(), State.PLAY,\n"
         "                Direction.SERVERBOUND, ProtocolVersion.V1_21_4);\n"
         "        if (name.isEmpty()) {",
         "        // Chat Message／Chat Command Signed：末尾に checksum（u8）を付ける。\n"
         "        // 値の計算方法は未確定のため 0x00（実バニラでの確認待ち）。\n"
         "        if (packet.id() == 0x07 || packet.id() == 0x06) {\n"
         "            byte[] payload = PlayTranslator.appendChatChecksum770(packet.payload());\n"
         "            OptionalInt id770 = PacketTranslator.translateId(\n"
         "                    packet.id() == 0x07 ? PacketName.PLAY_CHAT_MESSAGE\n"
         "                            : PacketName.PLAY_CHAT_COMMAND_SIGNED,\n"
         "                    ProtocolVersion.V1_21_4, ProtocolVersion.V1_21_5);\n"
         "            if (id770.isEmpty()) {\n"
         "                return null;\n"
         "            }\n"
         "            return new MinecraftPacket(id770.getAsInt(), payload);\n"
         "        }\n"
         "        // それ以外：番号だけ770向けに付け替える（ペイロードは同形式）。\n"
         "        Optional<PacketName> name = PacketIds.nameFor(packet.id(), State.PLAY,\n"
         "                Direction.SERVERBOUND, ProtocolVersion.V1_21_4);\n"
         "        if (name.isEmpty()) {"),
        # shift の誤変換の修正（767To769 というメソッドは存在しない。
        # 767→768 の出力は 769／770 と同形式のためそのまま使う）。
        ("PlayTranslator.translateClientInformation767To769(",
         "PlayTranslator.translateClientInformation767To768("),
        ("// 769→770の移動パケットは同形式のため、768→769の変換だけでよい。",
         "// 769→770の移動パケットは同形式のため、767→768の変換だけでよい（768／769／770は同形式）。"),
        ("PlayTranslator.translatePlayerPosition767To769(",
         "PlayTranslator.translatePlayerPosition767To768("),
        ("PlayTranslator.translatePlayerPositionLook767To769(",
         "PlayTranslator.translatePlayerPositionLook767To768("),
        ("PlayTranslator.translatePlayerLook767To769(",
         "PlayTranslator.translatePlayerLook767To768("),
        ("PlayTranslator.translatePlayerFlying767To769(",
         "PlayTranslator.translatePlayerFlying767To768("),
        ("PlayTranslator.translateSteerVehicleToPlayerInput769(",
         "PlayTranslator.translateSteerVehicleToPlayerInput768("),
        ("PlayTranslator.translateEnchantItem767To769(",
         "PlayTranslator.translateEnchantItem767To768("),
        ("PlayTranslator.translateCloseWindow767To769(",
         "PlayTranslator.translateCloseWindow767To768("),
    ],
    "SERVER_PLAY_770": [
        # From770 の javadoc：ID は全部 -1（同形式だが同IDではない）。
        ("     * Join Game／Respawn／Spawn Position／Keep Alive／Ping／Disconnect／\n"
         "     * Start Configuration は769と同ID・同形式のため、769の処理を使い回す。",
         "     * Join Game／Respawn／Spawn Position／Keep Alive／Ping／Disconnect／\n"
         "     * Start Configuration は769と同形式（ID は 1 小さい）のため、769の処理を使い回す。"),
        # Start Configuration 0x70→0x6F。
        ("        // Start Configuration（770：0x70）。763には設定フェーズもこのパケットも無い。\n"
         "        if (packet.id() == 0x70) {",
         "        // Start Configuration（770：0x6F）。763には設定フェーズもこのパケットも無い。\n"
         "        if (packet.id() == 0x6F) {"),
        # Join Game 0x2C→0x2B。
        ("        // Join Game（770：0x2C）。\n"
         "        if (packet.id() == 0x2C) {",
         "        // Join Game（770：0x2B）。\n"
         "        if (packet.id() == 0x2B) {"),
        # Spawn Position 0x5B→0x5A。
        ("        // Spawn Position（770：0x5B）。\n"
         "        if (packet.id() == 0x5B) {",
         "        // Spawn Position（770：0x5A）。\n"
         "        if (packet.id() == 0x5A) {"),
        # Keep Alive 0x27→0x26、Ping 0x37→0x36。
        ("        // Keep Alive（770：0x27）／Ping（770：0x37）は接続維持のため中継する。\n"
         "        if (packet.id() == 0x27) {",
         "        // Keep Alive（770：0x26）／Ping（770：0x36）は接続維持のため中継する。\n"
         "        if (packet.id() == 0x26) {"),
        ("        if (packet.id() == 0x37) {",
         "        if (packet.id() == 0x36) {"),
        # Disconnect 0x1D→0x1C＋770分岐＋is768 の追加。
        ("        // Disconnect（770：0x1D、NBT）。\n"
         "        if (packet.id() == 0x1D) {",
         "        // Disconnect（770：0x1C、NBT）。\n"
         "        if (packet.id() == 0x1C) {"),
        ("            if (is766() || is767() || is769()) {\n"
         "                // 766／767／768／769クライアント：Disconnect は 0x1D（NBT）のまま。\n"
         "                return new Outcome(List.of(),\n"
         "                        List.of(new MinecraftPacket(0x1D, packet.payload())), false, true);\n"
         "            }",
         "            if (is770()) {\n"
         "                // 770クライアント：Disconnect は 0x1C（NBT）のまま。\n"
         "                return new Outcome(List.of(),\n"
         "                        List.of(new MinecraftPacket(0x1C, packet.payload())), false, true);\n"
         "            }\n"
         "            if (is766() || is767() || is768() || is769()) {\n"
         "                // 766／767／768／769クライアント：Disconnect は 0x1D（NBT）のまま。\n"
         "                return new Outcome(List.of(),\n"
         "                        List.of(new MinecraftPacket(0x1D, packet.payload())), false, true);\n"
         "            }"),
        # Respawn 0x4C→0x4B＋Respawn 変換の使い回し（770の Respawn 形式は769と同じ）。
        ("        // Respawn（770：0x4C。769と同形式のため769の変換を使い回す）。\n"
         "        if (packet.id() == 0x4C) {",
         "        // Respawn（770：0x4B。769（0x4C）と同形式のため769の変換を使い回す）。\n"
         "        if (packet.id() == 0x4B) {"),
        ("            byte[] payload767 = PlayTranslator.translateRespawn769To767(",
         "            byte[] payload767 = PlayTranslator.translateRespawn768To767("),
        # JoinGame770 の javadoc：内部770の Join Game は 0x2B。
        ("     * Join Game（内部770：0x2C）のクライアント向け変換。",
         "     * Join Game（内部770：0x2B）のクライアント向け変換。"),
        # to769ServerPacketFrom770：チャット3種を追加（769では同形式なのに
        # 769作業のホワイトリストから漏れていた。770→769 でも要る）。
        ("            case PLAY_PLAYER_INFO_UPDATE ->\n"
         "                    PlayTranslator.translatePlayerInfo770To769(packet.payload());\n"
         "            case PLAY_SYNCHRONIZE_PLAYER_POSITION, PLAY_UPDATE_TIME, PLAY_SET_SLOT_STATE ->\n"
         "                    packet.payload(); // 同形式",
         "            case PLAY_PLAYER_INFO_UPDATE ->\n"
         "                    PlayTranslator.translatePlayerInfo770To769(packet.payload());\n"
         "            case PLAY_PLAYER_CHAT ->\n"
         "                    PlayTranslator.translatePlayerChat770To769(packet.payload());\n"
         "            case PLAY_SYSTEM_CHAT, PLAY_PROFILELESS_CHAT ->\n"
         "                    packet.payload(); // 同形式\n"
         "            case PLAY_SYNCHRONIZE_PLAYER_POSITION, PLAY_UPDATE_TIME, PLAY_SET_SLOT_STATE ->\n"
         "                    packet.payload(); // 同形式"),
        # to769ServerPacketFrom770：Spawn Entity の experience_orb 分岐。
        ("    private MinecraftPacket to769ServerPacketFrom770(MinecraftPacket packet) {\n"
         "        PacketName name = PacketIds.nameFor(packet.id(), State.PLAY,\n"
         "                Direction.CLIENTBOUND, ProtocolVersion.V1_21_5).orElse(null);",
         "    private MinecraftPacket to769ServerPacketFrom770(MinecraftPacket packet) {\n"
         "        PacketName name = PacketIds.nameFor(packet.id(), State.PLAY,\n"
         "                Direction.CLIENTBOUND, ProtocolVersion.V1_21_5).orElse(null);\n"
         "        if (name == PacketName.PLAY_SPAWN_ENTITY\n"
         "                && ChunkTranslator.spawnEntityType770(packet.payload()) == 47) {\n"
         "            // experience_orb（770のエンティティ種別 47）は 769 では専用パケット\n"
         "            // （Spawn Experience Orb 0x02）になる。\n"
         "            byte[] payload = ChunkTranslator.translateSpawnEntityExperienceOrb770To769(\n"
         "                    packet.payload());\n"
         "            OptionalInt id769 = PacketTranslator.translateId(\n"
         "                    PacketName.PLAY_SPAWN_EXPERIENCE_ORB,\n"
         "                    ProtocolVersion.V1_21_5, ProtocolVersion.V1_21_4);\n"
         "            if (id769.isEmpty()) {\n"
         "                return null;\n"
         "            }\n"
         "            return new MinecraftPacket(id769.getAsInt(), payload);\n"
         "        }"),
        # From770 の 767／766 分岐：handleServerPlay769To767／769To766 は
        # 存在しない。769作業と同じく 770→769→768 へ直してから 768→767／766 へ。
        ("        // 767クライアント：770→769へ変換してから769→768へ。\n"
         "        if (is767()) {\n"
         "            MinecraftPacket as769 = to769ServerPacketFrom770(packet);\n"
         "            if (as769 == null) {\n"
         "                return Outcome.drop();\n"
         "            }\n"
         "            return handleServerPlay769To767(as769);\n"
         "        }\n"
         "        // 766クライアント：770→769へ変換してから769→767へ。\n"
         "        if (is766()) {\n"
         "            MinecraftPacket as769 = to769ServerPacketFrom770(packet);\n"
         "            if (as769 == null) {\n"
         "                return Outcome.drop();\n"
         "            }\n"
         "            return handleServerPlay769To766(as769);\n"
         "        }",
         "        // 767クライアント：770→769→768へ変換してから768→767へ。\n"
         "        if (is767()) {\n"
         "            MinecraftPacket as769 = to769ServerPacketFrom770(packet);\n"
         "            if (as769 == null) {\n"
         "                return Outcome.drop();\n"
         "            }\n"
         "            MinecraftPacket as768 = to768ServerPacket(as769);\n"
         "            if (as768 == null) {\n"
         "                return Outcome.drop();\n"
         "            }\n"
         "            return handleServerPlay768To767(as768);\n"
         "        }\n"
         "        // 766クライアント：770→769→768へ変換してから768→766へ。\n"
         "        if (is766()) {\n"
         "            MinecraftPacket as769 = to769ServerPacketFrom770(packet);\n"
         "            if (as769 == null) {\n"
         "                return Outcome.drop();\n"
         "            }\n"
         "            MinecraftPacket as768 = to768ServerPacket(as769);\n"
         "            if (as768 == null) {\n"
         "                return Outcome.drop();\n"
         "            }\n"
         "            return handleServerPlay768To766(as768);\n"
         "        }"),
    ],
    "CONFIG_770": [
        # From769：pale_garden の挿入は 768→769 の話。769→770 では無い。
        ("     * Registry Data だけ769→770へ並べ替え（pale_garden を挿入）、",
         "     * Registry Data だけ769→770へ並べ替え、"),
        # From767：二段→三段、767→769 の直接変換は無いので二段に分ける。
        ("     * Registry Data だけ767→768→769→770へ二段で並べ替え、Tags はレジストリ番号が",
         "     * Registry Data だけ767→768→769→770へ三段で並べ替え、Tags はレジストリ番号が"),
        ("                byte[] payload767to769 =\n"
         "                        PlayTranslator.translateRegistryData767To769(packet.payload());\n"
         "                yield Outcome.toClient(new MinecraftPacket(0x07,\n"
         "                        PlayTranslator.translateRegistryData769To770(payload767to769)));",
         "                byte[] payload767to768 =\n"
         "                        PlayTranslator.translateRegistryData767To768(packet.payload());\n"
         "                byte[] payload768to769 =\n"
         "                        PlayTranslator.translateRegistryData768To769(payload767to768);\n"
         "                yield Outcome.toClient(new MinecraftPacket(0x07,\n"
         "                        PlayTranslator.translateRegistryData769To770(payload768to769)));"),
        # From766：三段→四段、同じく二段に分ける。
        ("     * Registry Data だけ766→767→768→769→770へ三段で並べ替え（jukebox_song は",
         "     * Registry Data だけ766→767→768→769→770へ四段で並べ替え（jukebox_song は"),
        ("                byte[] payload767to769 =\n"
         "                        PlayTranslator.translateRegistryData767To769(payload766to767);\n"
         "                yield Outcome.toClient(new MinecraftPacket(0x07,\n"
         "                        PlayTranslator.translateRegistryData769To770(payload767to769)));",
         "                byte[] payload767to768 =\n"
         "                        PlayTranslator.translateRegistryData767To768(payload766to767);\n"
         "                byte[] payload768to769 =\n"
         "                        PlayTranslator.translateRegistryData768To769(payload767to768);\n"
         "                yield Outcome.toClient(new MinecraftPacket(0x07,\n"
         "                        PlayTranslator.translateRegistryData769To770(payload768to769)));"),
        # 764／765から：三段→四段、同じく二段に分ける。
        ("     * （765→767→768→769→770へ三段）。",
         "     * （765→767→768→769→770へ四段）。"),
        ("                    byte[] payload767to769 =\n"
         "                            PlayTranslator.translateRegistryData767To769(registryPayload);\n"
         "                    toClient.add(new MinecraftPacket(0x07,\n"
         "                            PlayTranslator.translateRegistryData769To770(payload767to769)));",
         "                    byte[] payload767to768 =\n"
         "                            PlayTranslator.translateRegistryData767To768(registryPayload);\n"
         "                    byte[] payload768to769 =\n"
         "                            PlayTranslator.translateRegistryData768To769(payload767to768);\n"
         "                    toClient.add(new MinecraftPacket(0x07,\n"
         "                            PlayTranslator.translateRegistryData769To770(payload768to769)));"),
        # 763向け：辞書は768形式で蓄える（pale_garden を除く）。
        ("     * Registry Data は763クライアント向けの Join Game codec を作るために\n"
         "     * 769形式に直してから蓄える（770のままではバイオーム plains の番号が\n"
         "     * ずれるため）。",
         "     * Registry Data は763クライアント向けの Join Game codec を作るために\n"
         "     * 768形式に直してから蓄える（770のままではバイオーム pale_garden／\n"
         "     * plains の番号がずれるため）。"),
        ("                // 763向けの辞書は769形式で蓄える（770のままでは\n"
         "                // バイオーム plains の番号がずれる）。\n"
         "                registries.putAll(PlayTranslator.removePaleGardenBiome(\n"
         "                        PlayTranslator.reorderRegistries(\n"
         "                                parsed, ProtocolVersion.V1_21_5, ProtocolVersion.V1_21_4)));",
         "                // 763向けの辞書は768形式で蓄える（770のままでは\n"
         "                // バイオーム pale_garden／plains の番号がずれる）。\n"
         "                registries.putAll(PlayTranslator.removePaleGardenBiome(\n"
         "                        PlayTranslator.reorderRegistries(\n"
         "                                parsed, ProtocolVersion.V1_21_5, ProtocolVersion.V1_21_2)));"),
        # 764／765向け：769→767 の直接変換は無いので二段に分ける。
        # （javadoc の「三段」は 770→769→767→765 の3回で正しい。）
        ("                byte[] payload769to767 =\n"
         "                        PlayTranslator.translateRegistryData769To767(payload770to769);\n"
         "                byte[] codec = payload769to767 == null ? null",
         "                byte[] payload769to768 =\n"
         "                        PlayTranslator.translateRegistryData769To768(payload770to769);\n"
         "                byte[] payload769to767 =\n"
         "                        PlayTranslator.translateRegistryData768To767(payload769to768);\n"
         "                byte[] codec = payload769to767 == null ? null"),
        # 764／765向けの辞書：こちらも768形式で蓄える。
        ("     * 763向けの辞書には769形式で蓄える。",
         "     * 763向けの辞書には768形式で蓄える。"),
        ("            case 0x07 -> { // Registry Data：770のエントリ配列を764／765の codec に変換する\n"
         "                Map<String, Nbt.Tag.CompoundTag> parsed =\n"
         "                        PlayTranslator.parseRegistryData(packet.payload());\n"
         "                registries.putAll(PlayTranslator.removePaleGardenBiome(\n"
         "                        PlayTranslator.reorderRegistries(\n"
         "                                parsed, ProtocolVersion.V1_21_5, ProtocolVersion.V1_21_4)));",
         "            case 0x07 -> { // Registry Data：770のエントリ配列を764／765の codec に変換する\n"
         "                Map<String, Nbt.Tag.CompoundTag> parsed =\n"
         "                        PlayTranslator.parseRegistryData(packet.payload());\n"
         "                registries.putAll(PlayTranslator.removePaleGardenBiome(\n"
         "                        PlayTranslator.reorderRegistries(\n"
         "                                parsed, ProtocolVersion.V1_21_5, ProtocolVersion.V1_21_2)));"),
        # 767向け（From770）：二段→三段、769→767 の直接変換は無いので二段に分ける。
        ("     * Registry Data だけ770→769→768→767へ二段で並べ替え、Tags はレジストリ番号が",
         "     * Registry Data だけ770→769→768→767へ三段で並べ替え、Tags はレジストリ番号が"),
        ("                byte[] payload769to767 =\n"
         "                        PlayTranslator.translateRegistryData769To767(payload770to769);\n"
         "                if (payload769to767 == null) {\n"
         "                    yield Outcome.drop();\n"
         "                }\n"
         "                yield Outcome.toClient(new MinecraftPacket(0x07, payload769to767));",
         "                byte[] payload769to768 =\n"
         "                        PlayTranslator.translateRegistryData769To768(payload770to769);\n"
         "                if (payload769to768 == null) {\n"
         "                    yield Outcome.drop();\n"
         "                }\n"
         "                byte[] payload769to767 =\n"
         "                        PlayTranslator.translateRegistryData768To767(payload769to768);\n"
         "                if (payload769to767 == null) {\n"
         "                    yield Outcome.drop();\n"
         "                }\n"
         "                yield Outcome.toClient(new MinecraftPacket(0x07, payload769to767));"),
        # 766向け（From770）：三段→四段、同じく二段に分ける。
        ("     * Registry Data だけ770→769→768→767→766へ三段で並べ替え（jukebox_song は落とす）、",
         "     * Registry Data だけ770→769→768→767→766へ四段で並べ替え（jukebox_song は落とす）、"),
        ("                byte[] payload769to767 =\n"
         "                        PlayTranslator.translateRegistryData769To767(payload770to769);\n"
         "                if (payload769to767 == null) {\n"
         "                    yield Outcome.drop();\n"
         "                }\n"
         "                byte[] payload767to766 =\n"
         "                        PlayTranslator.translateRegistryData767To766(payload769to767);",
         "                byte[] payload769to768 =\n"
         "                        PlayTranslator.translateRegistryData769To768(payload770to769);\n"
         "                if (payload769to768 == null) {\n"
         "                    yield Outcome.drop();\n"
         "                }\n"
         "                byte[] payload769to767 =\n"
         "                        PlayTranslator.translateRegistryData768To767(payload769to768);\n"
         "                if (payload769to767 == null) {\n"
         "                    yield Outcome.drop();\n"
         "                }\n"
         "                byte[] payload767to766 =\n"
         "                        PlayTranslator.translateRegistryData767To766(payload769to767);"),
    ],
}

# 769→770 のサーバー→クライアント変換器（手書き。shifted ブロックには無い）。
# handleServerPlay769To770／768To770／767To770／766To770 と共に
# handleServerPlayFrom770 の前に入れる。
SERVER_PLAY_770_TO = r'''
    /**
     * 769サーバーのプレイ状態パケットを770形式にする。770に相当が無い／
     * 変換できないものは null を返す（ログ済み）。
     * 770クライアントへの送出と、768／767／766クライアントへの
     * 多段変換の最終段で共用する。
     */
    private MinecraftPacket to770ServerPacket(MinecraftPacket packet) {
        PacketName name = PacketIds.nameFor(packet.id(), State.PLAY,
                Direction.CLIENTBOUND, ProtocolVersion.V1_21_4).orElse(null);
        if (name == null) {
            logUntranslatedOnce("PLAY_UNKNOWN_0x" + Integer.toHexString(packet.id()));
            return null;
        }
        if (name == PacketName.PLAY_SPAWN_EXPERIENCE_ORB) {
            // 770では通常の Spawn Entity（type experience_orb）になる。
            // UUID は769のパケットに無いため新しく作る。
            byte[] payload = ChunkTranslator.translateSpawnEntityExperienceOrb769To770(
                    packet.payload());
            OptionalInt id770 = PacketTranslator.translateId(
                    PacketName.PLAY_SPAWN_ENTITY,
                    ProtocolVersion.V1_21_4, ProtocolVersion.V1_21_5);
            if (id770.isEmpty()) {
                return null;
            }
            return new MinecraftPacket(id770.getAsInt(), payload);
        }
        byte[] translated = switch (name) {
            case PLAY_ENTITY_METADATA ->
                    ChunkTranslator.translateEntityMetadata769To770(packet.payload());
            case PLAY_SET_SLOT ->
                    ChunkTranslator.translateSetSlot769To770(packet.payload());
            case PLAY_SET_CURSOR_ITEM ->
                    ChunkTranslator.translateSetCursorItem769To770(packet.payload());
            case PLAY_SET_PLAYER_INVENTORY ->
                    ChunkTranslator.translateSetPlayerInventory769To770(packet.payload());
            case PLAY_WINDOW_ITEMS ->
                    ChunkTranslator.translateWindowItems769To770(packet.payload());
            case PLAY_SET_COOLDOWN ->
                    ChunkTranslator.translateSetCooldown769To770(packet.payload());
            case PLAY_ENTITY_EQUIPMENT ->
                    ChunkTranslator.translateEntityEquipment769To770(packet.payload());
            case PLAY_TRADE_LIST ->
                    ChunkTranslator.translateTradeList769To770(packet.payload());
            case PLAY_OPEN_WINDOW ->
                    ChunkTranslator.translateOpenWindow769To770(packet.payload());
            case PLAY_BLOCK_ACTION ->
                    ChunkTranslator.translateBlockAction769To770(packet.payload());
            case PLAY_SPAWN_ENTITY ->
                    ChunkTranslator.translateSpawnEntity769To770(packet.payload());
            case PLAY_SOUND_EFFECT ->
                    ChunkTranslator.translateSoundEffect769To770(packet.payload());
            case PLAY_ENTITY_SOUND_EFFECT ->
                    ChunkTranslator.translateEntitySoundEffect769To770(packet.payload());
            case PLAY_STOP_SOUND ->
                    ChunkTranslator.translateStopSound769To770(packet.payload());
            case PLAY_WORLD_PARTICLES ->
                    ChunkTranslator.translateWorldParticles769To770(packet.payload());
            case PLAY_ENTITY_EFFECT ->
                    ChunkTranslator.translateEntityEffect769To770(packet.payload());
            case PLAY_REMOVE_ENTITY_EFFECT ->
                    ChunkTranslator.translateRemoveEntityEffect769To770(packet.payload());
            case PLAY_DAMAGE_EVENT ->
                    ChunkTranslator.translateDamageEvent769To770(packet.payload());
            case PLAY_ENTITY_UPDATE_ATTRIBUTES ->
                    ChunkTranslator.translateUpdateAttributes769To770(packet.payload());
            case PLAY_CHUNK_DATA ->
                    ChunkTranslator.translateChunkData769To770(packet.payload());
            case PLAY_BLOCK_CHANGE ->
                    ChunkTranslator.translateBlockChange769To770(packet.payload());
            case PLAY_MULTI_BLOCK_CHANGE ->
                    ChunkTranslator.translateMultiBlockChange769To770(packet.payload());
            case PLAY_BLOCK_ENTITY_DATA ->
                    ChunkTranslator.translateBlockEntityData769To770(packet.payload());
            case PLAY_PLAYER_INFO_UPDATE ->
                    PlayTranslator.translatePlayerInfo769To770(packet.payload());
            case PLAY_PLAYER_CHAT ->
                    PlayTranslator.translatePlayerChat769To770(packet.payload(), chatGlobalIndex++);
            case PLAY_SYSTEM_CHAT, PLAY_PROFILELESS_CHAT ->
                    packet.payload(); // 同形式
            case PLAY_SYNCHRONIZE_PLAYER_POSITION, PLAY_UPDATE_TIME, PLAY_SET_SLOT_STATE ->
                    packet.payload(); // 同形式
            case PLAY_HELD_ITEM_SLOT ->
                    PlayTranslator.translateSetHeldItem769To770(packet.payload());
            case PLAY_EXPLOSION ->
                    ChunkTranslator.translateExplosion769To770(packet.payload());
            default -> null;
        };
        if (translated == null) {
            logUntranslatedOnce(name);
            return null;
        }
        OptionalInt id770 = PacketTranslator.translateId(name,
                ProtocolVersion.V1_21_4, ProtocolVersion.V1_21_5);
        if (id770.isEmpty()) {
            logUntranslatedOnce(name);
            return null;
        }
        return new MinecraftPacket(id770.getAsInt(), translated);
    }

    /**
     * 内部769→770クライアントのプレイ状態（サーバー→クライアント）。
     * 769→770へ変換してそのまま送る。
     */
    private Outcome handleServerPlay769To770(MinecraftPacket packet) {
        MinecraftPacket as770 = to770ServerPacket(packet);
        if (as770 == null) {
            return Outcome.drop();
        }
        return Outcome.toClient(as770);
    }

    /**
     * 内部768→770クライアントのプレイ状態（サーバー→クライアント）。
     * 768→769→770へ二段で変換する。
     */
    private Outcome handleServerPlay768To770(MinecraftPacket packet) {
        Outcome to769 = handleServerPlay768To769(packet);
        if (to769.toClient().isEmpty()) {
            return to769;
        }
        return handleServerPlay769To770(to769.toClient().get(0));
    }

    /**
     * 内部767→770クライアントのプレイ状態（サーバー→クライアント）。
     * 767→769→770へ二段で変換する。
     */
    private Outcome handleServerPlay767To770(MinecraftPacket packet) {
        Outcome to769 = handleServerPlay767To769(packet);
        if (to769.toClient().isEmpty()) {
            return to769;
        }
        return handleServerPlay769To770(to769.toClient().get(0));
    }

    /**
     * 内部766→770クライアントのプレイ状態（サーバー→クライアント）。
     * 766→769→770へ二段で変換する。
     */
    private Outcome handleServerPlay766To770(MinecraftPacket packet) {
        Outcome to769 = handleServerPlay766To769(packet);
        if (to769.toClient().isEmpty()) {
            return to769;
        }
        return handleServerPlay769To770(to769.toClient().get(0));
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
    for name in ("CLIENT_PLAY_769", "SERVER_PLAY_769", "CONFIG_769"):
        blocks[name] = ast.literal_eval(assigns[name])

    new_edits = []
    for name, old, new in edits:
        if new.startswith(old) and new != old:
            kind = "APPEND"
            added = new[len(old):]
            new_edits.append((name.replace("769", "770"), new, new + shift(added)))
        elif old in new and new != old:
            kind = "CONTAINS-OLD"
            assert new.endswith(old), f"{name}: CONTAINS-OLD but not suffix form"
            prefix = new[: len(new) - len(old)]
            new_edits.append((name.replace("769", "770"), new, shift(prefix) + new))
        else:
            kind = "REPLACE"
            new_edits.append((name.replace("769", "770"), new, shift(new)))
        print(f"{kind}: {name} -> {new_edits[-1][0]}", file=sys.stderr)

    # ---- 手直し（FIX_EDITS / FIX_EDITS_FULL / ADD_EDITS / FIX_BLOCKS）----
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
        if name in FIX_EDITS:
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
    # メソッド名の衝突：live には既に to769ClientPacket／to769ServerPacket
    # （768→769 の変換器）があるため、shifted 版の 770→769 変換器は
    # From770 付きに改名する（定義・呼び出し元とも。to770* は新規のため不要）。
    new_blocks = {}
    for name in ("CLIENT_PLAY_769", "SERVER_PLAY_769", "CONFIG_769"):
        shifted = shift(blocks[name])
        new_name = name.replace("769", "770")
        if new_name == "CLIENT_PLAY_770":
            shifted = shifted.replace("to769ClientPacket", "to769ClientPacketFrom770")
        if new_name == "SERVER_PLAY_770":
            shifted = shifted.replace("to769ServerPacket", "to769ServerPacketFrom770")
        if new_name in FIX_BLOCKS:
            shifted = apply_subs(shifted, FIX_BLOCKS[new_name], f"block {new_name}")
        new_blocks[new_name] = shifted

    # ヘッダ（手書き）と EDITS を出力し、ブロックは後段の手作業用に
    # shifted 版をそのまま載せる。
    out_lines = []
    out_lines.append('''#!/usr/bin/env python3
"""ProxySession.java へ Java 1.21.5（プロトコル770）の対応を入れる。

769パッチ（patch-proxysession-769.py）を1段ずらしたもの。生成元は
tools/gen-770-proxysession.py（/tmp に置いた変換スクリプト）で、
EDITS・3ブロック・INSERTS を機械的にずらし、770特有の箇所だけ
手で直した。

770の要点（769からの差分）:
- 設定フェーズは769と同形（同じ番号・同じ形式）で中継する。
  Registry Data の並び替えだけ 769→770 が要る。
- プレイ状態の clientbound ID は 0x02（Spawn Experience Orb 削除）の
  後ろが全て -1 になる。Join Game 0x2C→0x2B、Spawn Position
  0x5B→0x5A、Keep Alive 0x27→0x26、Ping 0x37→0x36、
  Start Configuration 0x70→0x6F、Add Resource Pack 0x4B→0x4A。
- serverbound は Update Sign 0x39→0x3A、Arm Animation 0x3A→0x3B、
  Spectate 0x3B→0x3D、Block Place 0x3C→0x3E、Use Item 0x3D→0x3F。
  新規: Set Test Block 0x39、Test Instance Block Action 0x3C（共に落とす）。
- 770クライアントの Window Click は HashedSlot（ハッシュのみ）で
  769の Slot に戻せないため落とす。Set Creative Slot は
  UntrustedSlot（生バイト列のコンポーネント）から 769 Slot に組み直す。
- Player Chat は先頭に globalIndex varint が増える（770→769 は剥がす。
  769→770 はプロキシが 0 から数えて付ける）。
- Chat Message／Chat Command Signed は末尾に checksum u8 が増える
  （770→769 は剥がす。769→770 は 0x00 を付けるが値は未検証。
  実バニラでの確認待ち）。
- チャンクの heightmap は NBT から直接配列に変わった。
  （ChunkTranslator.translateChunkData769To770 / 770To769）
- Spawn Experience Orb（769 0x02）は 770 では通常の Spawn Entity
  （type experience_orb）になる。
- Test Instance Block Status（770 0x77）は 769 には無いので落とす。
- 内部770サーバー＋770クライアントはネイティブ（素通し）。

使い方: repo ルートで `python3 detergentmc-protocol/tools/patch-proxysession-770.py`
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
    for name in ("CLIENT_PLAY_770", "SERVER_PLAY_770", "CONFIG_770"):
        out_lines.append(f"{name} = r'''{new_blocks[name]}'''")
        out_lines.append("")
    out_lines.append(f"SERVER_PLAY_770_TO = r'''{SERVER_PLAY_770_TO}'''")
    out_lines.append("")
    # INSERTS: 769版の INSERTS を流用（メソッド名だけずらす）
    inserts_start = src.index("INSERTS = [")
    inserts_end = src.index("\n]\n", inserts_start) + len("\n]\n")
    inserts_shifted = shift(src[inserts_start:inserts_end])
    # 追加：769→770 変換器（SERVER_PLAY_770_TO）は handleServerPlayFrom770 の
    # 前に入れる（SERVER_PLAY_770 の直後になる）。
    extra_insert = ('    ("server-play 769-to-770 methods",\n'
                    '     "    private Outcome handleServerPlayFrom770(MinecraftPacket packet) {",\n'
                    '     SERVER_PLAY_770_TO),\n')
    assert inserts_shifted.rstrip().endswith("]"), inserts_shifted[-80:]
    idx = inserts_shifted.rstrip().rfind("]")
    inserts_shifted = inserts_shifted[:idx] + extra_insert + inserts_shifted[idx:]
    out_lines.append(inserts_shifted)
    out_lines.append("")
    # INSERTS の後ろ（CONFIG_ANCHOR／CONFIG_REPLACEMENT／apply／
    # insert_before_method）もずらして載せる。
    rest_src = src[inserts_end:src.index("def main(")]
    rest_shifted = shift(rest_src)
    # CONFIG_ANCHOR の手直し：769の To769 は委譲だけの短いメソッド
    # （shift した To768 全文ではない）。live の実体に合わせる。
    wrong_anchor = '''CONFIG_ANCHOR = """    private Outcome handleClientConfigurationTo769(MinecraftPacket packet) {
        if (packet.id() == 0x00) { // Settings
            return Outcome.toServer(new MinecraftPacket(0x00,
                    PlayTranslator.translateClientInformation767To769(packet.payload())));
        }
        return handleClientConfigurationTo766(packet);
    }

}"""'''
    right_anchor = '''CONFIG_ANCHOR = """    private Outcome handleClientConfigurationTo769(MinecraftPacket packet) {
        return handleClientConfigurationTo768(packet);
    }

}"""'''
    assert wrong_anchor in rest_shifted, "CONFIG_ANCHOR shifted text not found"
    rest_shifted = rest_shifted.replace(wrong_anchor, right_anchor)
    wrong_repl = '''CONFIG_REPLACEMENT = """    private Outcome handleClientConfigurationTo769(MinecraftPacket packet) {
        if (packet.id() == 0x00) { // Settings
            return Outcome.toServer(new MinecraftPacket(0x00,
                    PlayTranslator.translateClientInformation767To769(packet.payload())));
        }
        return handleClientConfigurationTo766(packet);
    }
""" + CONFIG_770 + "\\n}\\n"'''
    right_repl = '''CONFIG_REPLACEMENT = """    private Outcome handleClientConfigurationTo769(MinecraftPacket packet) {
        return handleClientConfigurationTo768(packet);
    }
""" + CONFIG_770 + "\\n}\\n"'''
    assert wrong_repl in rest_shifted, "CONFIG_REPLACEMENT shifted text not found"
    rest_shifted = rest_shifted.replace(wrong_repl, right_repl)
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
