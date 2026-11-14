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

【注意】この生成器で作ったパッチ（patch-proxysession-771.py）を適用した
後、ProxySession771Test で見つかった771漏れを手で直してある
（コミット 8fa47ab）。この生成器を再実行して作り直したパッチは、
下記の手直しを含まないため live と一致しない。再実行する場合は
下記も FIX_EDITS_FULL 等に取り込むこと。
- handleSynthesizedJoinGame／handleServerJoinGame763／
  handleServerJoinGame（764／765）：is770() → is770() || is771()。
- handleServerJoinGame766：is770() || is771() 分岐を新設
  （0x2B＋translateJoinGame767To768＋Add Pack 0x4A。770段階では
  770クライアントも 0x29 に落ちていた）。
- handleServerPlayUp（765サーバー）：分岐条件に is771() を追加し、
  Ping／Disconnect／Respawn／Add Resource Pack の三項を
  is770() || is771() に直す。
- handleClientConfiguration768：Client Information の剥がし条件に
  !isServer771() を追加し、素通し条件に isServer771() を追加。
- handleServerPlayFrom771：Respawn に is768() || is769() → 0x4C 分岐を
  追加、一般分岐に is769()／is768() を追加（handleServerPlay771To769／
  771To768 を新設）。
- handleServerPlayFrom770：Respawn の is769() → is768() || is769()
  （770段階の既存バグ。768クライアントは 765形式の 0x45 に落ちていた）。
"""
import ast
import re
import sys
from pathlib import Path

SRC = Path("/home/hatch/workspace/detergentmc/detergentmc-protocol/tools/patch-proxysession-770.py")
OUT = Path("/home/hatch/workspace/detergentmc/detergentmc-protocol/tools/patch-proxysession-771.py")

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
IS_CHAIN = re.compile(r"((?:is(?:Server)?\d{3}\(\)\s*\|\|\s*)+is(?:Server)?770\(\))")


def shift_chain(m, kind):
    sep = "→" if kind == "arrow" else "⇔"
    nums = m.group(0).split(sep)
    if len(nums) == 2:
        # 2要素のペアは1段ずらす（768→769 は 769→770 になる）。
        return sep.join(str(int(n) + 1) for n in nums)
    if len(nums) >= 3 and nums[0] == "770":
        # 769から始まる多段チェーンは段が1つ増える（769→768→767 は
        # 770→769→768→767 になる）。末端（765/766/767）は固定。
        return "771" + sep + m.group(0)
    if nums[-1] == "770":
        return m.group(0) + sep + "771"
    warnings.append(f"arrow chain {m.group(0)} -> +1 each")
    return sep.join(str(int(n) + 1) for n in nums)


def shift_list(m, sep):
    nums = m.group(0).split(sep)
    if nums[-1] == "770":
        return m.group(0) + sep + "771"
    if nums[-1] == "769":
        # 新バージョンの一つ下まで列挙しているリストは 770 を追加する。
        return m.group(0) + sep + "770"
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
    # 1b) isチェーン（is764() || ... || is770()）に is771() を追加し、
    # そのまま保護する（後の数字シフトで二重にずれないように）。
    def is_chain_sub(m):
        chain = m.group(1)
        last = re.search(r"is(Server)?770\(\)$", chain)
        suffix = "isServer771()" if last.group(1) else "is771()"
        key = f"\x02{_counter[0]}\x03"
        _counter[0] += 1
        is_chain_holders[key] = chain + " || " + suffix
        return key

    is_chain_holders = {}
    text = IS_CHAIN.sub(is_chain_sub, text)
    holders = {**h0, **h1, **h2, **h3, **h4, **is_chain_holders}
    # 2) バージョン文字列
    text = text.replace("1.21.2／1.21.3", "1.21.4")
    text = text.replace("1.21.5", "1.21.6")
    text = text.replace("1.21.4", "1.21.5")
    text = text.replace("1.21.2", "1.21.4")
    text = text.replace("V1_21_5", "V1_21_6")
    text = text.replace("V1_21_4", "V1_21_5")
    text = text.replace("V1_21_2", "V1_21_4")
    text = text.replace("PROTOCOL_770", "PROTOCOL_771")
    text = text.replace("PROTOCOL_769", "PROTOCOL_770")
    text = text.replace("pack770", "pack771")
    text = text.replace("pack769", "pack770")
    # pack769/pack770 は数字シフトの対象外にする（pack769→pack770 と
    # 数字シフトが二重にかかると pack770→pack770 のような誤りになる）。
    def pack_sub(m):
        key = f"\x02{_counter[0]}\x03"
        _counter[0] += 1
        pack_holders[key] = m.group(0)
        return key

    pack_holders = {}
    text = re.sub(r"pack(?:770|771)", pack_sub, text)
    holders.update(pack_holders)
    # 3) 単独の数字。\b は日本語文字を \w と見なすため使えないので、
    # 数字の前後を見る lookaround を使う（識別子内の 769 も対象にする）。
    # 16進リテラル (0x..）は数字続きなので対象外になる。
    text = re.sub(r"(?<!\d)770(?!\d)", "\x01770", text)
    text = re.sub(r"(?<!\d)769(?!\d)", "770", text)
    text = text.replace("\x01770", "771")
    # 4) 復元
    return restore(text, holders)


FIX_EDITS = {
    # name -> [(old_sub, new_sub), ...] を shift した NEW テキストに適用する
    # （各1回一致。shift の誤変換の修正）。
    "constructor javadoc": [
        ("769＝1.21.4、770＝1.21.5）", "769＝1.21.4、770＝1.21.5、771＝1.21.6）"),
    ],
    "constructor validation": [
        # shift は V1_21_2 を V1_21_4 にずらして重複させる（768の検査が欠ける）。
        # 検査の一覧に V1_21_2（768）を戻す。
        ("&& serverProtocol != ProtocolVersion.V1_21\n"
         "                && serverProtocol != ProtocolVersion.V1_21_4",
         "&& serverProtocol != ProtocolVersion.V1_21\n"
         "                && serverProtocol != ProtocolVersion.V1_21_2\n"
         "                && serverProtocol != ProtocolVersion.V1_21_4"),
        ("769 (1.21.4) or 770 (1.21.5): ",
         "769 (1.21.4), 770 (1.21.5) or 771 (1.21.6): "),
    ],
    "serverProtocol() javadoc": [
        ("769＝1.21.4、770＝1.21.5）。", "769＝1.21.4、770＝1.21.5、771＝1.21.6）。"),
    ],
    "pack771 field": [
        # 771の pack_format は 63（770の 55 のままになっている）。
        ("771クライアントに送るリソースパック（pack_format 55）",
         "771クライアントに送るリソースパック（pack_format 63）"),
    ],
    "packOverrideForClient javadoc": [
        # shift は pack_format の数字をずらさない（771=63、770=55、769=46）。
        # 769の行も shift で欠けているため戻す。
        ("771→pack771\n     * （pack_format 55）、770→pack770\n"
         "     * （pack_format 46）、768→pack768",
         "771→pack771\n     * （pack_format 63）、770→pack770\n"
         "     * （pack_format 55）、769→pack769\n"
         "     * （pack_format 46）、768→pack768"),
    ],
    "packOverrideForClient branch": [
        # shift は 771分岐の代替チェーンから pack769 を欠落させる。
        (": pack770 != null ? pack770\n"
         "                    : pack768 != null ? pack768",
         ": pack770 != null ? pack770\n"
         "                    : pack769 != null ? pack769\n"
         "                    : pack768 != null ? pack768"),
    ],
    "synthesized config registry loop": [
        # 771向けの Registry Data は766→767→768→769→770→771の五段。
        # shift は 768→769 の段を translateRegistryData768To770
        # （存在しない）に潰してしまうため、768→769→770の二段に戻す。
        ("// 771：766→767→768→769→770→771へ四段で並べ替える。",
         "// 771：766→767→768→769→770→771へ五段で並べ替える。"),
        ("                    byte[] payload768to770 =\n"
         "                            PlayTranslator.translateRegistryData768To770(payload767to768);\n"
         "                    registryPayload =\n"
         "                            PlayTranslator.translateRegistryData770To771(payload768to770);",
         "                    byte[] payload768to769 =\n"
         "                            PlayTranslator.translateRegistryData768To769(payload767to768);\n"
         "                    byte[] payload769to770 =\n"
         "                            PlayTranslator.translateRegistryData769To770(payload768to769);\n"
         "                    registryPayload =\n"
         "                            PlayTranslator.translateRegistryData770To771(payload769to770);"),
    ],
    "startConfigurationClientId": [
        # 三項の shift 崩れ：768／769は 0x70、770／771は 0x6F。
        (".orElse(is768() || is770() ? 0x70 : is771() ? 0x6F : 0x69)",
         ".orElse(is768() || is769() ? 0x70 : is770() || is771() ? 0x6F : 0x69)"),
    ],
    "spawnPositionClientId": [
        # 三項の shift 崩れ：768／769は 0x5B、770／771は 0x5A。
        (".orElse(is768() || is770() ? 0x5B : is771() ? 0x5A : 0x56)",
         ".orElse(is768() || is769() ? 0x5B : is770() || is771() ? 0x5A : 0x56)"),
    ],
    "keepAliveClientId": [
        # shift 崩れ：768／769は 0x27、770／771は 0x26。
        (".orElse(is768() || is770() ? 0x27 : 0x26)",
         ".orElse(is768() || is769() ? 0x27 : 0x26)"),
    ],
    "pingClientId": [
        # 三項の shift 崩れ：768／769は 0x37、770／771は 0x36。
        (".orElse(is768() || is770() ? 0x37 : is771() ? 0x36 : 0x35)",
         ".orElse(is768() || is769() ? 0x37 : is770() || is771() ? 0x36 : 0x35)"),
    ],
    "handleServerPlay dispatch": [
        # 771の Start Configuration は 0x6F（770と同じ。-1 ではない）。
        ("// 内部771：Join Game は 0x2B、Spawn Position は 0x5A、\n"
         "        // Start Configuration は 0x6F（770から -1）。",
         "// 内部771：Join Game は 0x2B、Spawn Position は 0x5A、\n"
         "        // Start Configuration は 0x6F（770と同じ）。"),
    ],
    "handleServerConfiguration 763 branch": [
        # shift は isServer768 の分岐を isServer769 にずらさない
        # （769の分岐が欠ける）。769の分岐を戻す。
        ("            if (isServer768()) {\n"
         "                return handleServerConfiguration763From768(packet);\n"
         "            }",
         "            if (isServer769()) {\n"
         "                return handleServerConfiguration763From769(packet);\n"
         "            }\n"
         "            if (isServer768()) {\n"
         "                return handleServerConfiguration763From768(packet);\n"
         "            }"),
    ],
    "handleServerPlayFrom769 771 branch": [
        # 769サーバー→771クライアントは 769→770→771（770→771 ではない）。
        ("return handleServerPlay770To771(packet);",
         "return handleServerPlay769To771(packet);"),
        ("// 771クライアント：770→771へ変換する。",
         "// 771クライアント：769→770→771へ変換する。"),
    ],
    "handleServerPlayFrom766 771 branch": [
        # 段数の数え間違い：766からは五段、767からは四段。
        ("// 内部766は 766→767→768→769→770→771 の四段、内部767は 767→768→769→770→771 の三段で送る。",
         "// 内部766は 766→767→768→769→770→771 の五段、内部767は 767→768→769→770→771 の四段で送る。"),
    ],
    "handleServerPlayFrom763 ResourcePack 771": [
        # 三項の shift 崩れ：768／769は 0x4B、770／771は 0x4A。
        ("return Outcome.toClient(new MinecraftPacket(is768() || is770() ? 0x4B : is771() ? 0x4A : 0x46,",
         "return Outcome.toClient(new MinecraftPacket(is768() || is769() ? 0x4B : is770() || is771() ? 0x4A : 0x46,"),
    ],
}


# (old, new) を丸ごと置き換える編集。shift した (old, new) では
# アンカーが live に無かったり、意味が変わったりするもの。
FIX_EDITS_FULL = {
    # 'handleServerConfiguration 771 branch'：shift した old は770パッチの
    # new（770分岐＋769分岐）だが、live の769分岐には770ステージの手直しで
    # isServer770 の分岐が入っているためアンカーが合わない。live の770分岐の
    # 先頭をアンカーにして771分岐を丸ごと書き直す。
    "handleServerConfiguration 771 branch": (
        "        // 770クライアントは設定フェーズを中継する（番号と Registry Data\n"
        "        // の形式だけ変換する）。\n"
        "        if (is770()) {",
        "        // 771クライアントは設定フェーズを中継する（番号と Registry Data\n"
        "        // の形式だけ変換する）。\n"
        "        if (is771()) {\n"
        "            if (isServer771()) {\n"
        "                // 内部771＋771クライアントはネイティブのためここには来ない。\n"
        "                // 念のためそのまま通す。\n"
        "                return Outcome.toClient(packet);\n"
        "            }\n"
        "            if (isServer770()) {\n"
        "                return handleServerConfiguration771From770(packet);\n"
        "            }\n"
        "            if (isServer769()) {\n"
        "                return handleServerConfiguration771From769(packet);\n"
        "            }\n"
        "            if (isServer768()) {\n"
        "                return handleServerConfiguration771From768(packet);\n"
        "            }\n"
        "            if (isServer767()) {\n"
        "                return handleServerConfiguration771From767(packet);\n"
        "            }\n"
        "            if (isServer766()) {\n"
        "                return handleServerConfiguration771From766(packet);\n"
        "            }\n"
        "            return handleServerConfiguration771(packet, isServer764());\n"
        "        }\n"
        "        // 770クライアントは設定フェーズを中継する（番号と Registry Data\n"
        "        // の形式だけ変換する）。\n"
        "        if (is770()) {",
    ),
    # 'synthesized add-pack 771 id'：shift した old は770パッチの new
    # （is770() ? 0x4A）だが、'synthesized add-pack' 編集が先に条件と
    # コメントを771用に変えるためアンカーが合わない。編集後の状態を
    # アンカーにして、770・771とも 0x4A にする。
    "synthesized add-pack 771 id": (
        "            if (is768() || is769() || is770() || is771()) {\n"
        "                // プレイ状態の Add Resource Pack は 0x4B（PLAY_ADD_RESOURCE_PACK）。771は 0x4A。\n"
        "                toClient.add(new MinecraftPacket(is770() ? 0x4A : 0x4B,",
        "            if (is768() || is769() || is770() || is771()) {\n"
        "                // プレイ状態の Add Resource Pack は 0x4B（PLAY_ADD_RESOURCE_PACK）。771は 0x4A。\n"
        "                toClient.add(new MinecraftPacket(is770() || is771() ? 0x4A : 0x4B,",
    ),
    # 'handleServerPlayFrom769 Respawn 771' / 'handleServerPlayFrom768
    # Respawn 771'：shift は is770 分岐を is771 に書き換えてしまい、
    # is770（From769）／is769（From768）の分岐を消してしまう。
    # 770の分岐（From768は769の分岐も）を残したまま771分岐を前に足す。
    "handleServerPlayFrom769 Respawn 771": (
        "        // Respawn（769：0x4C。768と同形式のため768の変換を使い回す）。\n"
        "        if (packet.id() == 0x4C) {\n"
        "            if (is770()) {\n"
        "                // 770クライアント：Respawn は 0x4B（同形式）のまま。\n"
        "                return Outcome.toClient(new MinecraftPacket(0x4B, packet.payload()));\n"
        "            }\n"
        "            if (is768()) {",
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
    ),
    "handleServerPlayFrom768 Respawn 771": (
        "        // Respawn（768：0x4C）。\n"
        "        if (packet.id() == 0x4C) {\n"
        "            if (is770()) {\n"
        "                // 770クライアント：Respawn は 0x4B（同形式）のまま。\n"
        "                return Outcome.toClient(new MinecraftPacket(0x4B, packet.payload()));\n"
        "            }\n"
        "            if (is769()) {",
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
    ),
    # 'chatGlobalIndex field'：shift した old は pack770 ブロック＋770用
    # フィールドだが、'pack771 field' 編集が先に pack771 ブロックを挟むため
    # アンカーが合わず、new もフィールドを重複させる。pack771 ブロックと
    # 旧770用カウンタをアンカーにして、770／771共用のカウンタを1本だけ置く。
    "chatGlobalIndex field": (
        "    /**\n"
        "     * 771クライアントに送るリソースパック（pack_format 63）。\n"
        "     * 内部が771でない場合に使う差し替え用。内部が771の場合はバニラが\n"
        "     * 送ってきたパックをそのまま出すため null のままでよい。\n"
        "     * 出し分けの基準はクライアントのプロトコルである\n"
        "     * （{@link #packOverrideForClient()} 参照）。\n"
        "     */\n"
        "    private ResourcePack pack771;\n"
        "\n"
        "    /**\n"
        "     * 770クライアントへ送る player_chat に付ける globalIndex。\n"
        "     * 769サーバーは globalIndex を送ってこないため、プロキシが\n"
        "     * セッションごとに 0 から数える。\n"
        "     */\n"
        "    private int chatGlobalIndex = 0;",
        "    /**\n"
        "     * 771クライアントに送るリソースパック（pack_format 63）。\n"
        "     * 内部が771でない場合に使う差し替え用。内部が771の場合はバニラが\n"
        "     * 送ってきたパックをそのまま出すため null のままでよい。\n"
        "     * 出し分けの基準はクライアントのプロトコルである\n"
        "     * （{@link #packOverrideForClient()} 参照）。\n"
        "     */\n"
        "    private ResourcePack pack771;\n"
        "\n"
        "    /**\n"
        "     * 770／771クライアントへ送る player_chat に付ける globalIndex。\n"
        "     * 769サーバーは globalIndex を送ってこないため、プロキシが\n"
        "     * セッションごとに 0 から数える。\n"
        "     */\n"
        "    private int chatGlobalIndex = 0;",
    ),
}


# 771パッチから外す編集。770ステージで既に live へ適用済みの修正で、
# shift すると存在しないメソッド（translatePlayerInfo770To768 等）を
# 呼ぶ壊れた編集になってしまうため。
SKIP_EDITS = {
    "to768ServerPacket chat passthrough",
    "handleServerPlay768To767 chat passthrough",
    "handleServerPlay767To766 system_chat passthrough",
}


ADD_EDITS = [
    # 770サーバー→771クライアント：一般プレイ状態の変換。
    # （shift では作れない。handleServerPlayFrom770 に771分岐が無いため。）
    ("handleServerPlayFrom770 771 branch",
     "        // 769クライアント：770→769へ変換する。\n"
     "        if (is769()) {\n"
     "            return handleServerPlay770To769(packet);\n"
     "        }",
     "        // 771クライアント：770→771へ変換する。\n"
     "        if (is771()) {\n"
     "            return handleServerPlay770To771(packet);\n"
     "        }\n"
     "        // 769クライアント：770→769へ変換する。\n"
     "        if (is769()) {\n"
     "            return handleServerPlay770To769(packet);\n"
     "        }"),
    # 770サーバー→771クライアント：Respawn（770：0x4B、771：0x4B、同形式）。
    ("handleServerPlayFrom770 Respawn 771",
     "        // Respawn（770：0x4B。769（0x4C）と同形式のため769の変換を使い回す）。\n"
     "        if (packet.id() == 0x4B) {\n"
     "            if (is769()) {",
     "        // Respawn（770：0x4B。769（0x4C）と同形式のため769の変換を使い回す）。\n"
     "        if (packet.id() == 0x4B) {\n"
     "            if (is771()) {\n"
     "                // 771クライアント：Respawn は 0x4B（同形式）のまま。\n"
     "                return Outcome.toClient(new MinecraftPacket(0x4B, packet.payload()));\n"
     "            }\n"
     "            if (is769()) {"),
    # 770サーバー→771クライアント：Disconnect（770：0x1C、771：0x1C）。
    # From766 にも同文の分岐があるため、is770() 分岐からアンカーする。
    ("handleServerPlayFrom770 Disconnect 771",
     "            if (is770()) {\n"
     "                // 770クライアント：Disconnect は 0x1C（NBT）のまま。\n"
     "                return new Outcome(List.of(),\n"
     "                        List.of(new MinecraftPacket(0x1C, packet.payload())), false, true);\n"
     "            }\n"
     "            if (is766() || is767() || is768() || is769()) {\n"
     "                // 766／767／768／769クライアント：Disconnect は 0x1D（NBT）のまま。",
     "            if (is770()) {\n"
     "                // 770クライアント：Disconnect は 0x1C（NBT）のまま。\n"
     "                return new Outcome(List.of(),\n"
     "                        List.of(new MinecraftPacket(0x1C, packet.payload())), false, true);\n"
     "            }\n"
     "            if (is771()) {\n"
     "                // 771クライアント：Disconnect は 0x1C（NBT）のまま。\n"
     "                return new Outcome(List.of(),\n"
     "                        List.of(new MinecraftPacket(0x1C, packet.payload())), false, true);\n"
     "            }\n"
     "            if (is766() || is767() || is768() || is769()) {\n"
     "                // 766／767／768／769クライアント：Disconnect は 0x1D（NBT）のまま。"),
    # 770サーバー→771クライアント：Join Game（770：0x2B、771：0x2B）。
    ("handleServerJoinGame770 771 branch",
     "    private Outcome handleServerJoinGame770(MinecraftPacket packet) {\n"
     "        if (is769()) {",
     "    private Outcome handleServerJoinGame770(MinecraftPacket packet) {\n"
     "        if (is771()) {\n"
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
     "        if (is769()) {"),
    # 770クライアント＋内部771：設定フェーズを中継する。
    ("handleServerConfiguration 770 branch isServer771",
     "            if (isServer769()) {\n"
     "                return handleServerConfiguration770From769(packet);\n"
     "            }",
     "            if (isServer771()) {\n"
     "                return handleServerConfiguration770From771(packet);\n"
     "            }\n"
     "            if (isServer769()) {\n"
     "                return handleServerConfiguration770From769(packet);\n"
     "            }"),
    # 769クライアント＋内部771：設定フェーズを中継する。
    ("handleServerConfiguration 769 branch isServer771",
     "            if (isServer770()) {\n"
     "                return handleServerConfiguration769From770(packet);\n"
     "            }",
     "            if (isServer771()) {\n"
     "                return handleServerConfiguration769From771(packet);\n"
     "            }\n"
     "            if (isServer770()) {\n"
     "                return handleServerConfiguration769From770(packet);\n"
     "            }"),
    # 768クライアント＋内部771：設定フェーズを中継する。
    ("handleServerConfiguration 768 branch isServer771",
     "            if (isServer770()) {\n"
     "                return handleServerConfiguration768From770(packet);\n"
     "            }",
     "            if (isServer771()) {\n"
     "                return handleServerConfiguration768From771(packet);\n"
     "            }\n"
     "            if (isServer770()) {\n"
     "                return handleServerConfiguration768From770(packet);\n"
     "            }"),
    # クライアント→サーバーの設定フェーズ：771サーバーへは Custom Report
    # Details（0x08）／Server Links（0x09）を送らない（771で削除された。
    # 送ると771サーバーが切断する）。ただし771クライアントの 0x08 は
    # Custom Click Action のため送る（handleClientConfiguration771 で扱う）。
    ("handleClientConfiguration 771-server guard",
     "        if (synthesizingConfig) {\n"
     "            return handleSynthesizedClientConfig(packet);\n"
     "        }",
     "        if (synthesizingConfig) {\n"
     "            return handleSynthesizedClientConfig(packet);\n"
     "        }\n"
     "        // 771サーバーへは Custom Report Details（0x08）／Server Links\n"
     "        // （0x09）を送らない（771で削除された）。771クライアントの\n"
     "        // 0x08 は Custom Click Action のためこの限りではない。\n"
     "        if (isServer771() && !is771()\n"
     "                && (packet.id() == 0x08 || packet.id() == 0x09)) {\n"
     "            return Outcome.drop();\n"
     "        }"),
]


# CONFIG_771 に追加する、旧クライアント＋内部771の設定フェーズ変換器。
# shift では作れない（770ステージにも同型の手書きメソッドがある）。
# 771→770→769→768へ段々に並べ替え、Show Dialog／Clear Dialog
# （771だけの config パケット）は旧クライアント向けに落とす。
CONFIG_771_EXTRA = r'''
    /**
     * 771クライアント＋内部769：設定フェーズを中継する。
     * 769と771の設定フェーズの番号は同じため、ほとんど素通しする。
     * Registry Data だけ769→770→771へ二段で並べ替え、Tags はレジストリ番号が
     * ずれるため落とす。Cookie Request には空の Cookie Response を、Select Known
     * Packs には空の一覧をプロキシがクライアント役として返す。
     */
    private Outcome handleServerConfiguration771From769(MinecraftPacket packet) {
        return switch (packet.id()) {
            case 0x00 -> { // Cookie Request：空の Cookie Response を返す
                String key = readCookieKey(packet.payload());
                yield Outcome.toServer(new MinecraftPacket(0x01,
                        emptyCookieResponsePayload(key)));
            }
            case 0x01 -> Outcome.toClient(new MinecraftPacket(0x01, packet.payload())); // Custom Payload（同ID）
            case 0x02 -> // Disconnect（769は NBT。771も NBT）
                    Outcome.toClient(packet);
            case 0x03 -> // Finish Configuration
                    Outcome.toClient(packet);
            case 0x04 -> Outcome.toClient(packet); // Keep Alive（同ID）
            case 0x05 -> Outcome.toClient(packet); // Ping（同ID）
            case 0x06 -> Outcome.toClient(packet); // Reset Chat（同ID）
            case 0x07 -> { // Registry Data：769のエントリ配列を771の順序に並べ替える
                Map<String, Nbt.Tag.CompoundTag> parsed =
                        PlayTranslator.parseRegistryData(packet.payload());
                registries.putAll(parsed);
                byte[] payload769to770 =
                        PlayTranslator.translateRegistryData769To770(packet.payload());
                yield Outcome.toClient(new MinecraftPacket(0x07,
                        PlayTranslator.translateRegistryData770To771(payload769to770)));
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
                LOG.info(() -> "レジストリ番号がずれるため Tags を771向けに落とします（" + playerName + "）。");
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
     * 770クライアント＋内部771：設定フェーズを中継する。
     * 770と771の設定フェーズの番号は同じため、ほとんど素通しする。
     * Registry Data だけ771→770へ並べ替え（minecraft:dialog は770に無いため
     * 落とし、jukebox_song の tears は落とす）、Tags はレジストリ番号が
     * ずれるため落とす。Show Dialog（0x11）／Clear Dialog（0x12）は
     * 770に無いため落とす。Cookie Request には空の Cookie Response を、
     * Select Known Packs には空の一覧をプロキシがクライアント役として返す。
     */
    private Outcome handleServerConfiguration770From771(MinecraftPacket packet) {
        return switch (packet.id()) {
            case 0x00 -> { // Cookie Request：空の Cookie Response を返す
                String key = readCookieKey(packet.payload());
                yield Outcome.toServer(new MinecraftPacket(0x01,
                        emptyCookieResponsePayload(key)));
            }
            case 0x01 -> Outcome.toClient(new MinecraftPacket(0x01, packet.payload())); // Custom Payload（同ID）
            case 0x02 -> // Disconnect（771は NBT。770も NBT）
                    Outcome.toClient(packet);
            case 0x03 -> // Finish Configuration
                    Outcome.toClient(packet);
            case 0x04 -> Outcome.toClient(packet); // Keep Alive（同ID）
            case 0x05 -> Outcome.toClient(packet); // Ping（同ID）
            case 0x06 -> Outcome.toClient(packet); // Reset Chat（同ID）
            case 0x07 -> { // Registry Data：771のエントリ配列を770の順序に並べ替える
                Map<String, Nbt.Tag.CompoundTag> parsed =
                        PlayTranslator.parseRegistryData(packet.payload());
                registries.putAll(parsed);
                byte[] payload = PlayTranslator.translateRegistryData771To770(packet.payload());
                if (payload == null) {
                    yield Outcome.drop();
                }
                yield Outcome.toClient(new MinecraftPacket(0x07, payload));
            }
            case 0x08 -> Outcome.toClient(packet); // Remove Resource Pack（同ID）
            case 0x09 -> { // Add Resource Pack（771：UUID付き）
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
                LOG.info(() -> "レジストリ番号がずれるため Tags を770向けに落とします（" + playerName + "）。");
                yield Outcome.drop();
            }
            case 0x0E -> // Select Known Packs：空の一覧を返す
                    Outcome.toServer(new MinecraftPacket(0x07, emptySelectKnownPacksPayload()));
            case 0x0F -> Outcome.toClient(packet); // Custom Report Details（同ID）
            case 0x10 -> Outcome.toClient(packet); // Server Links（同ID）
            case 0x11, 0x12 -> // Show Dialog／Clear Dialog（770に無い）
                    Outcome.drop();
            default -> {
                LOG.warning("771の設定フェーズで想定外のパケット（id=0x"
                        + Integer.toHexString(packet.id()) + "）を落とします。");
                yield Outcome.drop();
            }
        };
    }

    /**
     * 769クライアント＋内部771：設定フェーズを中継する。
     * 769と771の設定フェーズの番号は同じため、ほとんど素通しする。
     * Registry Data だけ771→770→769へ二段で並べ替え（minecraft:dialog と
     * jukebox_song の tears は落とす）、Tags はレジストリ番号がずれるため
     * 落とす。Show Dialog（0x11）／Clear Dialog（0x12）は769に無いため
     * 落とす。Cookie Request には空の Cookie Response を、Select Known
     * Packs には空の一覧をプロキシがクライアント役として返す。
     */
    private Outcome handleServerConfiguration769From771(MinecraftPacket packet) {
        return switch (packet.id()) {
            case 0x00 -> { // Cookie Request：空の Cookie Response を返す
                String key = readCookieKey(packet.payload());
                yield Outcome.toServer(new MinecraftPacket(0x01,
                        emptyCookieResponsePayload(key)));
            }
            case 0x01 -> Outcome.toClient(new MinecraftPacket(0x01, packet.payload())); // Custom Payload（同ID）
            case 0x02 -> // Disconnect（771は NBT。769も NBT）
                    Outcome.toClient(packet);
            case 0x03 -> // Finish Configuration
                    Outcome.toClient(packet);
            case 0x04 -> Outcome.toClient(packet); // Keep Alive（同ID）
            case 0x05 -> Outcome.toClient(packet); // Ping（同ID）
            case 0x06 -> Outcome.toClient(packet); // Reset Chat（同ID）
            case 0x07 -> { // Registry Data：771のエントリ配列を769の順序に並べ替える
                Map<String, Nbt.Tag.CompoundTag> parsed =
                        PlayTranslator.parseRegistryData(packet.payload());
                // 769向けの辞書は769形式で蓄える（771と769でバイオームの
                // 順序は同じため、実質そのまま）。
                registries.putAll(PlayTranslator.reorderRegistries(
                        parsed, ProtocolVersion.V1_21_6, ProtocolVersion.V1_21_4));
                byte[] payload771to770 =
                        PlayTranslator.translateRegistryData771To770(packet.payload());
                if (payload771to770 == null) {
                    yield Outcome.drop();
                }
                byte[] payload = PlayTranslator.translateRegistryData770To769(payload771to770);
                if (payload == null) {
                    yield Outcome.drop();
                }
                yield Outcome.toClient(new MinecraftPacket(0x07, payload));
            }
            case 0x08 -> Outcome.toClient(packet); // Remove Resource Pack（同ID）
            case 0x09 -> { // Add Resource Pack（771：UUID付き）
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
                LOG.warning("771の設定フェーズで想定外のパケット（id=0x"
                        + Integer.toHexString(packet.id()) + "）を落とします。");
                yield Outcome.drop();
            }
        };
    }

    /**
     * 768クライアント＋内部771：設定フェーズを中継する。
     * 768と771の設定フェーズの番号は同じため、ほとんど素通しする。
     * Registry Data だけ771→770→769→768へ三段で並べ替え（pale_garden を
     * 落として plains を39に戻す）、Tags はレジストリ番号がずれるため
     * 落とす。Show Dialog（0x11）／Clear Dialog（0x12）は768に無いため
     * 落とす。Cookie Request には空の Cookie Response を、Select Known
     * Packs には空の一覧をプロキシがクライアント役として返す。
     */
    private Outcome handleServerConfiguration768From771(MinecraftPacket packet) {
        return switch (packet.id()) {
            case 0x00 -> { // Cookie Request：空の Cookie Response を返す
                String key = readCookieKey(packet.payload());
                yield Outcome.toServer(new MinecraftPacket(0x01,
                        emptyCookieResponsePayload(key)));
            }
            case 0x01 -> Outcome.toClient(new MinecraftPacket(0x01, packet.payload())); // Custom Payload（同ID）
            case 0x02 -> // Disconnect（771は NBT。768も NBT）
                    Outcome.toClient(packet);
            case 0x03 -> // Finish Configuration
                    Outcome.toClient(packet);
            case 0x04 -> Outcome.toClient(packet); // Keep Alive（同ID）
            case 0x05 -> Outcome.toClient(packet); // Ping（同ID）
            case 0x06 -> Outcome.toClient(packet); // Reset Chat（同ID）
            case 0x07 -> { // Registry Data：771のエントリ配列を768の順序に並べ替える
                Map<String, Nbt.Tag.CompoundTag> parsed =
                        PlayTranslator.parseRegistryData(packet.payload());
                // 768向けの辞書は768形式で蓄える（771のままでは
                // バイオーム pale_garden／plains の番号がずれる）。
                registries.putAll(PlayTranslator.removePaleGardenBiome(
                        PlayTranslator.reorderRegistries(
                                parsed, ProtocolVersion.V1_21_6, ProtocolVersion.V1_21_2)));
                byte[] payload771to770 =
                        PlayTranslator.translateRegistryData771To770(packet.payload());
                if (payload771to770 == null) {
                    yield Outcome.drop();
                }
                byte[] payload770to769 =
                        PlayTranslator.translateRegistryData770To769(payload771to770);
                if (payload770to769 == null) {
                    yield Outcome.drop();
                }
                byte[] payload769to768 =
                        PlayTranslator.translateRegistryData769To768(payload770to769);
                if (payload769to768 == null) {
                    yield Outcome.drop();
                }
                yield Outcome.toClient(new MinecraftPacket(0x07, payload769to768));
            }
            case 0x08 -> Outcome.toClient(packet); // Remove Resource Pack（同ID）
            case 0x09 -> { // Add Resource Pack（771：UUID付き）
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
                LOG.warning("771の設定フェーズで想定外のパケット（id=0x"
                        + Integer.toHexString(packet.id()) + "）を落とします。");
                yield Outcome.drop();
            }
        };
    }
'''


FIX_BLOCKS = {
    "CLIENT_PLAY_771": [
        # handleClientPlay771 の javadoc：Vehicle Move は落とさない（onGround は
        # 770・771ともある）。Window Click／Set Creative Slot の「770の Slot」
        # は誤り（770も HashedSlot／UntrustedSlot）。
        ("そのまま通す。Vehicle Move は末尾の onGround を落とす。Window Click は\n"
         "     * HashedSlot のため770の Slot に戻せず落とす。Set Creative Slot は\n"
         "     * UntrustedSlot から770の Slot に組み直す。",
         "そのまま通す。Vehicle Move は770・771とも末尾に onGround があるため\n"
         "     * そのまま通す。Window Click は771・770とも HashedSlot のため落とす。\n"
         "     * Set Creative Slot は UntrustedSlot のままアイテム／成分を付け替える。\n"
         "     * Change Gamemode／Custom Click Action は770に無いため落とす。"),
        # to770ClientPacketFrom771：Vehicle Move は 771:0x21→770:0x20。
        # onGround は両方にあるため落とさない。
        ("        // Vehicle Move（771：0x20）：末尾の onGround を落として770形式にする。\n"
         "        if (packet.id() == 0x20) {\n"
         "            return new MinecraftPacket(0x20,\n"
         "                    PlayTranslator.translateVehicleMove771To770(packet.payload()));\n"
         "        }",
         "        // Vehicle Move（771：0x21→770：0x20）：770・771とも末尾に onGround が\n"
         "        // あるため形式は同一。そのまま通す。\n"
         "        if (packet.id() == 0x21) {\n"
         "            return new MinecraftPacket(0x20,\n"
         "                    PlayTranslator.translateVehicleMove771To770(packet.payload()));\n"
         "        }"),
        # to770ClientPacketFrom771：Window Click は 771:0x11。
        ("        // Window Click（771：0x10）：HashedSlot はハッシュしか持たないため\n"
         "        // 770の Slot には戻せない。落とす（ハッシュの計算方法は未確定）。\n"
         "        if (packet.id() == 0x10) {",
         "        // Window Click（771：0x11）：771・770とも HashedSlot のため\n"
         "        // 落とす（ハッシュの計算方法は未確定）。\n"
         "        if (packet.id() == 0x11) {"),
        # to770ClientPacketFrom771：Set Creative Slot は 771:0x37→770:0x36。
        # 770も UntrustedSlot のため「Slot に組み直す」は誤り。
        ("        // Set Creative Slot（771：0x36→770：0x36、同ID）：\n"
         "        // UntrustedSlot（生バイト列のコンポーネント）から770の Slot に組み直す。\n"
         "        if (packet.id() == 0x36) {\n"
         "            return new MinecraftPacket(0x36,\n"
         "                    ChunkTranslator.translateSetCreativeSlot771To770(packet.payload()));\n"
         "        }",
         "        // Set Creative Slot（771：0x37→770：0x36）：\n"
         "        // UntrustedSlot のままアイテム／成分を付け替える。\n"
         "        if (packet.id() == 0x37) {\n"
         "            return new MinecraftPacket(0x36,\n"
         "                    ChunkTranslator.translateSetCreativeSlot771To770(packet.payload()));\n"
         "        }"),
        # to770ClientPacketFrom771：Chat の checksum は770・771ともあるため
        # 剥がさない。771の Chat は 0x08／0x07（shift は770の 0x07／0x06を
        # そのまま使っており、Chat Command を誤って剥がしてしまう）。
        # 番号だけ付け替える generic 経路に任せる。
        ("        // Chat Message／Chat Command Signed：末尾の checksum（u8）を剥がす。\n"
         "        if (packet.id() == 0x07 || packet.id() == 0x06) {\n"
         "            byte[] payload = PlayTranslator.stripChatChecksum771(packet.payload());\n"
         "            OptionalInt id770 = PacketTranslator.translateId(\n"
         "                    packet.id() == 0x07 ? PacketName.PLAY_CHAT_MESSAGE\n"
         "                            : PacketName.PLAY_CHAT_COMMAND_SIGNED,\n"
         "                    ProtocolVersion.V1_21_6, ProtocolVersion.V1_21_5);\n"
         "            if (id770.isEmpty()) {\n"
         "                return null;\n"
         "            }\n"
         "            return new MinecraftPacket(id770.getAsInt(), payload);\n"
         "        }\n",
         "        // Chat Message／Chat Command Signed：末尾の checksum（u8）は\n"
         "        // 770・771ともあるため剥がさない。番号だけ付け替える（下の generic 経路）。\n"),
        # handleClientPlayTo771 の javadoc：767からは四段。
        ("     * Set Creative Slot は767→768→769→770→771へ三段で変換する",
         "     * Set Creative Slot は767→768→769→770→771へ四段で変換する"),
        # handleClientPlayTo771：Set Creative Slot は767→768→769→770→771の
        # 四段（shift は 768→769 の段を translateSetCreativeSlot768To770
        # （存在しない）に潰してしまう）。出力は 771:0x37。
        ("                if (is767()) {\n"
         "                    byte[] payload = ChunkTranslator.translateSetCreativeSlot770To771(\n"
         "                            ChunkTranslator.translateSetCreativeSlot768To770(\n"
         "                                    ChunkTranslator.translateSetCreativeSlot767To768(packet.payload())));\n"
         "                    return Outcome.toServer(new MinecraftPacket(0x36, payload));\n"
         "                }",
         "                if (is767()) {\n"
         "                    byte[] payload = ChunkTranslator.translateSetCreativeSlot770To771(\n"
         "                            ChunkTranslator.translateSetCreativeSlot769To770(\n"
         "                                    ChunkTranslator.translateSetCreativeSlot768To769(\n"
         "                                            ChunkTranslator.translateSetCreativeSlot767To768(packet.payload()))));\n"
         "                    return Outcome.toServer(new MinecraftPacket(0x37, payload));\n"
         "                }"),
        # handleClientPlayTo771：Vehicle Move は 771:0x21。769は onGround 付き、
        # 768以下は onGround が無いため補う（shift は770形式を「onGround 無し」
        # と言って translateVehicleMove770To771 を呼んでいたが、770には onGround
        # があるため passthrough で、768以下には補いが要る）。
        ("            case PLAY_VEHICLE_MOVE_TO_SERVER -> {\n"
         "                // 770形式（onGround 無し）に onGround=false を付けて771形式にする。\n"
         "                byte[] payload = PlayTranslator.translateVehicleMove770To771(packet.payload());\n"
         "                return Outcome.toServer(new MinecraftPacket(0x20, payload));\n"
         "            }",
         "            case PLAY_VEHICLE_MOVE_TO_SERVER -> {\n"
         "                // 769は onGround 付き（770・771と同形式）。768以下は onGround が\n"
         "                // 無いため onGround=false を補う。\n"
         "                byte[] payload = is769()\n"
         "                        ? packet.payload()\n"
         "                        : PlayTranslator.translateVehicleMove768To769(packet.payload());\n"
         "                return Outcome.toServer(new MinecraftPacket(0x21, payload));\n"
         "            }"),
        # handleClientPlayTo771：Steer Vehicle は Player Input 771:0x2a。
        ("                byte[] payload = PlayTranslator.translateSteerVehicleToPlayerInput768(\n"
         "                        packet.payload());\n"
         "                return Outcome.toServer(new MinecraftPacket(0x29, payload));",
         "                byte[] payload = PlayTranslator.translateSteerVehicleToPlayerInput768(\n"
         "                        packet.payload());\n"
         "                return Outcome.toServer(new MinecraftPacket(0x2a, payload));"),
        # handleClientPlayTo771：Chat Command は 771:0x06／0x07
        # （shift は770の 0x05／0x06をそのまま使っている）。
        ("                    // 771の Chat Command Signed（0x06）に送る。\n"
         "                    return Outcome.toServer(new MinecraftPacket(0x06, packet.payload()));",
         "                    // 771の Chat Command Signed（0x07）に送る。\n"
         "                    return Outcome.toServer(new MinecraftPacket(0x07, packet.payload()));"),
        ("                // 766／767の符号なし Chat Command は771の 0x05（同形式）。\n"
         "                return Outcome.toServer(new MinecraftPacket(0x05, packet.payload()));",
         "                // 766／767の符号なし Chat Command は771の 0x06（同形式）。\n"
         "                return Outcome.toServer(new MinecraftPacket(0x06, packet.payload()));"),
        # handleClientPlayTo771：Resource Pack Response は 771:0x30。
        ("            case PLAY_RESOURCE_PACK_RESPONSE -> {\n"
         "                if (lastSentPack == null) {\n"
         "                    LOG.warning(\"送っていないリソースパックへの応答（id=0x\"\n"
         "                            + Integer.toHexString(packet.id()) + \"）を落とします。\");\n"
         "                    return Outcome.drop();\n"
         "                }\n"
         "                if (is763()) {\n"
         "                    byte[] payload = PlayTranslator.translateResourcePackResponse763To765(\n"
         "                            packet.payload(), lastSentPack);\n"
         "                    return Outcome.toServer(new MinecraftPacket(0x2F, payload));\n"
         "                }\n"
         "                if (is764()) {\n"
         "                    byte[] payload = PlayTranslator.translateResourcePackResponse764To765(\n"
         "                            packet.payload(), lastSentPack);\n"
         "                    return Outcome.toServer(new MinecraftPacket(0x2F, payload));\n"
         "                }\n"
         "                return Outcome.toServer(new MinecraftPacket(0x2F, packet.payload()));\n"
         "            }",
         "            case PLAY_RESOURCE_PACK_RESPONSE -> {\n"
         "                if (lastSentPack == null) {\n"
         "                    LOG.warning(\"送っていないリソースパックへの応答（id=0x\"\n"
         "                            + Integer.toHexString(packet.id()) + \"）を落とします。\");\n"
         "                    return Outcome.drop();\n"
         "                }\n"
         "                if (is763()) {\n"
         "                    byte[] payload = PlayTranslator.translateResourcePackResponse763To765(\n"
         "                            packet.payload(), lastSentPack);\n"
         "                    return Outcome.toServer(new MinecraftPacket(0x30, payload));\n"
         "                }\n"
         "                if (is764()) {\n"
         "                    byte[] payload = PlayTranslator.translateResourcePackResponse764To765(\n"
         "                            packet.payload(), lastSentPack);\n"
         "                    return Outcome.toServer(new MinecraftPacket(0x30, payload));\n"
         "                }\n"
         "                return Outcome.toServer(new MinecraftPacket(0x30, packet.payload()));\n"
         "            }"),
        # handleClientPlayTo771：Configuration Acknowledged は 771:0x0f。
        ("            case PLAY_CONFIGURATION_ACKNOWLEDGED -> {\n"
         "                clientState = ClientState.CONFIGURATION;\n"
         "                serverState = ServerState.CONFIGURATION;\n"
         "                return Outcome.toServer(new MinecraftPacket(0x0E, packet.payload()));\n"
         "            }",
         "            case PLAY_CONFIGURATION_ACKNOWLEDGED -> {\n"
         "                clientState = ClientState.CONFIGURATION;\n"
         "                serverState = ServerState.CONFIGURATION;\n"
         "                return Outcome.toServer(new MinecraftPacket(0x0f, packet.payload()));\n"
         "            }"),
        # handleClientPlayTo771：Chunk Batch Received は 771:0x0a。
        ("            case PLAY_CHUNK_BATCH_RECEIVED -> {\n"
         "                if (is763()) {\n"
         "                    return Outcome.drop();\n"
         "                }\n"
         "                return Outcome.toServer(new MinecraftPacket(0x09, packet.payload()));\n"
         "            }",
         "            case PLAY_CHUNK_BATCH_RECEIVED -> {\n"
         "                if (is763()) {\n"
         "                    return Outcome.drop();\n"
         "                }\n"
         "                return Outcome.toServer(new MinecraftPacket(0x0a, packet.payload()));\n"
         "            }"),
        # handleClientPlayTo771：Enchant Item は 771:0x10。
        ("            case PLAY_ENCHANT_ITEM -> {\n"
         "                byte[] payload = PlayTranslator.translateEnchantItem767To768(\n"
         "                        packet.payload());\n"
         "                return Outcome.toServer(new MinecraftPacket(0x0F, payload));\n"
         "            }",
         "            case PLAY_ENCHANT_ITEM -> {\n"
         "                byte[] payload = PlayTranslator.translateEnchantItem767To768(\n"
         "                        packet.payload());\n"
         "                return Outcome.toServer(new MinecraftPacket(0x10, payload));\n"
         "            }"),
        # handleClientPlayTo771：Close Window は 771:0x12。
        ("            case PLAY_CLOSE_WINDOW_TO_SERVER -> {\n"
         "                byte[] payload = PlayTranslator.translateCloseWindow767To768(\n"
         "                        packet.payload());\n"
         "                return Outcome.toServer(new MinecraftPacket(0x11, payload));\n"
         "            }",
         "            case PLAY_CLOSE_WINDOW_TO_SERVER -> {\n"
         "                byte[] payload = PlayTranslator.translateCloseWindow767To768(\n"
         "                        packet.payload());\n"
         "                return Outcome.toServer(new MinecraftPacket(0x12, payload));\n"
         "            }"),
        # handleClientPlayTo771：Client Information は 771:0x0d。
        # 769は既に 9 フィールド形式（particleStatus 付き）のため、そのまま送る
        # （shift は全クライアントに particleStatus を付け足してしまう）。
        ("            case PLAY_CLIENT_INFORMATION -> {\n"
         "                // Settings：旧クライアントの 8 フィールド形式に particleStatus=0 を付けて\n"
         "                // 771 形式にする。付けないと 771 サーバーが読み取り途中で切断する。\n"
         "                byte[] payload = PlayTranslator.translateClientInformation767To768(\n"
         "                        packet.payload());\n"
         "                return Outcome.toServer(new MinecraftPacket(0x0C, payload));\n"
         "            }",
         "            case PLAY_CLIENT_INFORMATION -> {\n"
         "                // Settings：768以下の 8 フィールド形式に particleStatus=0 を付けて\n"
         "                // 771 形式にする。付けないと 771 サーバーが読み取り途中で切断する。\n"
         "                // 769は 9 フィールド形式（particleStatus 付き）のためそのまま送る。\n"
         "                byte[] payload = is769() ? packet.payload()\n"
         "                        : PlayTranslator.translateClientInformation767To768(\n"
         "                                packet.payload());\n"
         "                return Outcome.toServer(new MinecraftPacket(0x0d, payload));\n"
         "            }"),
        # to771ClientPacket：Vehicle Move の出力は 771:0x21。
        # onGround は770・771ともあるため「付ける」は誤り。
        ("        // Vehicle Move（770：0x20）：末尾に onGround=false を付けて771形式にする。\n"
         "        if (packet.id() == 0x20) {\n"
         "            return new MinecraftPacket(0x20,\n"
         "                    PlayTranslator.translateVehicleMove770To771(packet.payload()));\n"
         "        }",
         "        // Vehicle Move（770：0x20→771：0x21）：770・771とも末尾に onGround が\n"
         "        // あるため形式は同一。そのまま通す。\n"
         "        if (packet.id() == 0x20) {\n"
         "            return new MinecraftPacket(0x21,\n"
         "                    PlayTranslator.translateVehicleMove770To771(packet.payload()));\n"
         "        }"),
        # to771ClientPacket：Set Creative Slot の出力は 771:0x37。
        ("        // Set Creative Slot（770：0x36→771：0x36、同ID）：Slot の\n"
         "        // コンポーネントを生バイト列にした UntrustedSlot にする。\n"
         "        if (packet.id() == 0x36) {\n"
         "            return new MinecraftPacket(0x36,\n"
         "                    ChunkTranslator.translateSetCreativeSlot770To771(packet.payload()));\n"
         "        }",
         "        // Set Creative Slot（770：0x36→771：0x37）：\n"
         "        // UntrustedSlot のままアイテム／成分を付け替える。\n"
         "        if (packet.id() == 0x36) {\n"
         "            return new MinecraftPacket(0x37,\n"
         "                    ChunkTranslator.translateSetCreativeSlot770To771(packet.payload()));\n"
         "        }"),
        # to771ClientPacket：Chat の checksum は770・771ともあるため付け足さない。
        # 番号だけ付け替える generic 経路に任せる。
        ("        // Chat Message／Chat Command Signed：末尾に checksum（u8）を付ける。\n"
         "        // 値の計算方法は未確定のため 0x00（実バニラでの確認待ち）。\n"
         "        if (packet.id() == 0x07 || packet.id() == 0x06) {\n"
         "            byte[] payload = PlayTranslator.appendChatChecksum771(packet.payload());\n"
         "            OptionalInt id771 = PacketTranslator.translateId(\n"
         "                    packet.id() == 0x07 ? PacketName.PLAY_CHAT_MESSAGE\n"
         "                            : PacketName.PLAY_CHAT_COMMAND_SIGNED,\n"
         "                    ProtocolVersion.V1_21_5, ProtocolVersion.V1_21_6);\n"
         "            if (id771.isEmpty()) {\n"
         "                return null;\n"
         "            }\n"
         "            return new MinecraftPacket(id771.getAsInt(), payload);\n"
         "        }\n",
         "        // Chat Message／Chat Command Signed：末尾の checksum（u8）は\n"
         "        // 770・771ともあるため付け足しは要らない。番号だけ付け替える（下の generic 経路）。\n"),
    ],
    "SERVER_PLAY_771": [
        # handleServerPlayFrom771 の javadoc：771のクライアント行きIDは770と
        # 同じ（「1 小さい」は769→770の話）。
        ("     * Join Game／Respawn／Spawn Position／Keep Alive／Ping／Disconnect／\n"
         "     * Start Configuration は770と同形式（ID は 1 小さい）のため、770の処理を使い回す。",
         "     * Join Game／Respawn／Spawn Position／Keep Alive／Ping／Disconnect／\n"
         "     * Start Configuration は770と同形式（ID も同じ）のため、770の処理を使い回す。"),
        # handleServerPlayFrom771 の Disconnect：769クライアントは 0x1D。
        # shift は 769 を欠落させる。
        ("            if (is766() || is767() || is768() || is770()) {\n"
         "                // 766／767／768／769／770クライアント：Disconnect は 0x1D（NBT）のまま。",
         "            if (is766() || is767() || is768() || is769() || is770()) {\n"
         "                // 766／767／768／769／770クライアント：Disconnect は 0x1D（NBT）のまま。"),
        # handleServerPlayFrom771 の767分岐：771→770の次に770→769が要る
        # （shift は 770→769 の段を飛ばして 768 に直結させてしまう）。
        ("        // 767クライアント：771→770→769→768へ変換してから769→768へ。\n"
         "        if (is767()) {\n"
         "            MinecraftPacket as770 = to770ServerPacketFrom771(packet);\n"
         "            if (as770 == null) {\n"
         "                return Outcome.drop();\n"
         "            }\n"
         "            MinecraftPacket as768 = to768ServerPacket(as770);",
         "        // 767クライアント：771→770→769→768へ変換してから768→767へ。\n"
         "        if (is767()) {\n"
         "            MinecraftPacket as770 = to770ServerPacketFrom771(packet);\n"
         "            if (as770 == null) {\n"
         "                return Outcome.drop();\n"
         "            }\n"
         "            MinecraftPacket as769 = to769ServerPacketFrom770(as770);\n"
         "            if (as769 == null) {\n"
         "                return Outcome.drop();\n"
         "            }\n"
         "            MinecraftPacket as768 = to768ServerPacket(as769);"),
        # handleServerPlayFrom771 の766分岐：同上。
        ("        // 766クライアント：771→770→769→768へ変換してから769→767へ。\n"
         "        if (is766()) {\n"
         "            MinecraftPacket as770 = to770ServerPacketFrom771(packet);\n"
         "            if (as770 == null) {\n"
         "                return Outcome.drop();\n"
         "            }\n"
         "            MinecraftPacket as768 = to768ServerPacket(as770);",
         "        // 766クライアント：771→770→769→768へ変換してから768→766へ。\n"
         "        if (is766()) {\n"
         "            MinecraftPacket as770 = to770ServerPacketFrom771(packet);\n"
         "            if (as770 == null) {\n"
         "                return Outcome.drop();\n"
         "            }\n"
         "            MinecraftPacket as769 = to769ServerPacketFrom770(as770);\n"
         "            if (as769 == null) {\n"
         "                return Outcome.drop();\n"
         "            }\n"
         "            MinecraftPacket as768 = to768ServerPacket(as769);"),
        # handleServerJoinGame771 の770分岐：Join Game は 0x2B、
        # Add Resource Pack は 0x4A（shift は769の 0x2C／0x4Bのまま）。
        ("        if (is770()) {\n"
         "            advanceFlowSafely(() -> joinFlow.joinGameReceived());\n"
         "            List<MinecraftPacket> toClient = new ArrayList<>();\n"
         "            toClient.add(new MinecraftPacket(0x2C, packet.payload()));\n"
         "            while (!pendingPacks.isEmpty()) {\n"
         "                PendingPack pack = pendingPacks.removeFirst();\n"
         "                lastSentPack = pack;\n"
         "                toClient.add(new MinecraftPacket(0x4B,\n"
         "                        PlayTranslator.translateConfigAddResourcePack764To765(\n"
         "                                pack, packOverrideForClient())));\n"
         "            }\n"
         "            return new Outcome(List.of(), toClient, false, false);\n"
         "        }\n"
         "        return handleServerJoinGame770(packet);",
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
         "        return handleServerJoinGame770(packet);"),
        # to770ServerPacketFrom771：経験値オーブの分岐を削除する。770には
        # Spawn Experience Orb が無く（770で Spawn Entity に統合済み）、
        # translateId が空を返してオーブを落としてしまうため。771・770とも
        # 通常の Spawn Entity（type experience_orb）のため、下の
        # PLAY_SPAWN_ENTITY 経路で通す。
        ("        if (name == PacketName.PLAY_SPAWN_ENTITY\n"
         "                && ChunkTranslator.spawnEntityType771(packet.payload()) == 47) {\n"
         "            // experience_orb（771のエンティティ種別 47）は 770 では専用パケット\n"
         "            // （Spawn Experience Orb 0x02）になる。\n"
         "            byte[] payload = ChunkTranslator.translateSpawnEntityExperienceOrb771To770(\n"
         "                    packet.payload());\n"
         "            OptionalInt id770 = PacketTranslator.translateId(\n"
         "                    PacketName.PLAY_SPAWN_EXPERIENCE_ORB,\n"
         "                    ProtocolVersion.V1_21_6, ProtocolVersion.V1_21_5);\n"
         "            if (id770.isEmpty()) {\n"
         "                return null;\n"
         "            }\n"
         "            return new MinecraftPacket(id770.getAsInt(), payload);\n"
         "        }\n",
         "        // experience_orb：771・770とも通常の Spawn Entity\n"
         "        // （type experience_orb）のため、専用の分岐は要らない\n"
         "        // （下の PLAY_SPAWN_ENTITY 経路で通す）。\n"),
        # to770ServerPacketFrom771：771だけのパケットは明示して落とす。
        ("            case PLAY_HELD_ITEM_SLOT ->\n"
         "                    PlayTranslator.translateSetHeldItem771To770(packet.payload());\n"
         "            case PLAY_EXPLOSION ->\n"
         "                    ChunkTranslator.translateExplosion771To770(packet.payload());\n"
         "            default -> null;",
         "            case PLAY_HELD_ITEM_SLOT ->\n"
         "                    PlayTranslator.translateSetHeldItem771To770(packet.payload());\n"
         "            case PLAY_EXPLOSION ->\n"
         "                    ChunkTranslator.translateExplosion771To770(packet.payload());\n"
         "            // 771だけのパケット（ロケーターバー／ダイアログ）は770に無いため落とす。\n"
         "            case PLAY_TRACKED_WAYPOINT, PLAY_SHOW_DIALOG, PLAY_CLEAR_DIALOG -> null;\n"
         "            default -> null;"),
    ],
    "CONFIG_771": [
        # Registry Data の 768→770 の段（存在しないメソッド）を
        # 768→769→770の二段に戻す（771From768）。
        ("                        PlayTranslator.translateRegistryData770To771(\n"
         "                                PlayTranslator.translateRegistryData768To770(packet.payload()))));",
         "                        PlayTranslator.translateRegistryData770To771(\n"
         "                                PlayTranslator.translateRegistryData769To770(\n"
         "                                        PlayTranslator.translateRegistryData768To769(packet.payload())))));"),
        # 同上（771From767）。
        ("            case 0x07 -> { // Registry Data：767のエントリ配列を771の順序に並べ替える\n"
         "                Map<String, Nbt.Tag.CompoundTag> parsed =\n"
         "                        PlayTranslator.parseRegistryData(packet.payload());\n"
         "                registries.putAll(parsed);\n"
         "                byte[] payload767to768 =\n"
         "                        PlayTranslator.translateRegistryData767To768(packet.payload());\n"
         "                byte[] payload768to770 =\n"
         "                        PlayTranslator.translateRegistryData768To770(payload767to768);\n"
         "                yield Outcome.toClient(new MinecraftPacket(0x07,\n"
         "                        PlayTranslator.translateRegistryData770To771(payload768to770)));\n"
         "            }",
         "            case 0x07 -> { // Registry Data：767のエントリ配列を771の順序に並べ替える\n"
         "                Map<String, Nbt.Tag.CompoundTag> parsed =\n"
         "                        PlayTranslator.parseRegistryData(packet.payload());\n"
         "                registries.putAll(parsed);\n"
         "                byte[] payload767to768 =\n"
         "                        PlayTranslator.translateRegistryData767To768(packet.payload());\n"
         "                byte[] payload768to769 =\n"
         "                        PlayTranslator.translateRegistryData768To769(payload767to768);\n"
         "                byte[] payload769to770 =\n"
         "                        PlayTranslator.translateRegistryData769To770(payload768to769);\n"
         "                yield Outcome.toClient(new MinecraftPacket(0x07,\n"
         "                        PlayTranslator.translateRegistryData770To771(payload769to770)));\n"
         "            }"),
        # 同上（771From766）。
        ("            case 0x07 -> { // Registry Data：766のエントリ配列を771の順序に並べ替える\n"
         "                Map<String, Nbt.Tag.CompoundTag> parsed =\n"
         "                        PlayTranslator.parseRegistryData(packet.payload());\n"
         "                registries.putAll(parsed);\n"
         "                byte[] payload766to767 =\n"
         "                        PlayTranslator.translateRegistryData766To767(packet.payload());\n"
         "                byte[] payload767to768 =\n"
         "                        PlayTranslator.translateRegistryData767To768(payload766to767);\n"
         "                byte[] payload768to770 =\n"
         "                        PlayTranslator.translateRegistryData768To770(payload767to768);\n"
         "                yield Outcome.toClient(new MinecraftPacket(0x07,\n"
         "                        PlayTranslator.translateRegistryData770To771(payload768to770)));\n"
         "            }",
         "            case 0x07 -> { // Registry Data：766のエントリ配列を771の順序に並べ替える\n"
         "                Map<String, Nbt.Tag.CompoundTag> parsed =\n"
         "                        PlayTranslator.parseRegistryData(packet.payload());\n"
         "                registries.putAll(parsed);\n"
         "                byte[] payload766to767 =\n"
         "                        PlayTranslator.translateRegistryData766To767(packet.payload());\n"
         "                byte[] payload767to768 =\n"
         "                        PlayTranslator.translateRegistryData767To768(payload766to767);\n"
         "                byte[] payload768to769 =\n"
         "                        PlayTranslator.translateRegistryData768To769(payload767to768);\n"
         "                byte[] payload769to770 =\n"
         "                        PlayTranslator.translateRegistryData769To770(payload768to769);\n"
         "                yield Outcome.toClient(new MinecraftPacket(0x07,\n"
         "                        PlayTranslator.translateRegistryData770To771(payload769to770)));\n"
         "            }"),
        # 同上（handleServerConfiguration771 のループ）。
        ("                    byte[] payload768to770 =\n"
         "                            PlayTranslator.translateRegistryData768To770(payload767to768);\n"
         "                    toClient.add(new MinecraftPacket(0x07,\n"
         "                            PlayTranslator.translateRegistryData770To771(payload768to770)));",
         "                    byte[] payload768to769 =\n"
         "                            PlayTranslator.translateRegistryData768To769(payload767to768);\n"
         "                    byte[] payload769to770 =\n"
         "                            PlayTranslator.translateRegistryData769To770(payload768to769);\n"
         "                    toClient.add(new MinecraftPacket(0x07,\n"
         "                            PlayTranslator.translateRegistryData770To771(payload769to770)));"),
        # 767From771：771→770→768 の段（存在しないメソッド）を
        # 771→770→769→768の三段に戻す。段数のコメントも直す。
        ("     * Registry Data だけ771→770→769→768→767へ三段で並べ替え、Tags はレジストリ番号が",
         "     * Registry Data だけ771→770→769→768→767へ四段で並べ替え、Tags はレジストリ番号が"),
        ("                byte[] payload770to768 =\n"
         "                        PlayTranslator.translateRegistryData770To768(payload771to770);\n"
         "                if (payload770to768 == null) {\n"
         "                    yield Outcome.drop();\n"
         "                }\n"
         "                byte[] payload770to767 =\n"
         "                        PlayTranslator.translateRegistryData768To767(payload770to768);\n"
         "                if (payload770to767 == null) {\n"
         "                    yield Outcome.drop();\n"
         "                }\n"
         "                yield Outcome.toClient(new MinecraftPacket(0x07, payload770to767));",
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
        # 766From771：同上。段数のコメントも直す。
        ("     * Registry Data だけ771→770→769→768→767→766へ四段で並べ替え（jukebox_song は落とす）、",
         "     * Registry Data だけ771→770→769→768→767→766へ五段で並べ替え（jukebox_song は落とす）、"),
        ("                byte[] payload770to768 =\n"
         "                        PlayTranslator.translateRegistryData770To768(payload771to770);\n"
         "                if (payload770to768 == null) {\n"
         "                    yield Outcome.drop();\n"
         "                }\n"
         "                byte[] payload770to767 =\n"
         "                        PlayTranslator.translateRegistryData768To767(payload770to768);\n"
         "                if (payload770to767 == null) {\n"
         "                    yield Outcome.drop();\n"
         "                }\n"
         "                byte[] payload767to766 =\n"
         "                        PlayTranslator.translateRegistryData767To766(payload770to767);",
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
        # handleServerConfigurationFrom771：同上。段数のコメントも直す。
        ("     * （765→767→768→769→770→771へ四段）。",
         "     * （765→767→768→769→770→771へ五段）。"),
        ("                byte[] payload770to768 =\n"
         "                        PlayTranslator.translateRegistryData770To768(payload771to770);\n"
         "                byte[] payload770to767 =\n"
         "                        PlayTranslator.translateRegistryData768To767(payload770to768);\n"
         "                byte[] codec = payload770to767 == null ? null\n"
         "                        : PlayTranslator.translateRegistryData767To765(payload770to767);",
         "                byte[] payload770to769 =\n"
         "                        PlayTranslator.translateRegistryData770To769(payload771to770);\n"
         "                byte[] payload769to768 =\n"
         "                        PlayTranslator.translateRegistryData769To768(payload770to769);\n"
         "                byte[] payload768to767 =\n"
         "                        PlayTranslator.translateRegistryData768To767(payload769to768);\n"
         "                byte[] codec = payload768to767 == null ? null\n"
         "                        : PlayTranslator.translateRegistryData767To765(payload768to767);"),
        # handleClientConfiguration771：委譲だけのスタブを、Custom Click Action
        # （771だけの config パケット）を扱う実体に置き換える。
        ("    private Outcome handleClientConfiguration771(MinecraftPacket packet) {\n"
         "        return handleClientConfiguration770(packet);\n"
         "    }",
         "    /**\n"
         "     * 771クライアント＋内部770以下の設定フェーズ（クライアント→バニラ）。\n"
         "     * 771の番号は770と同じため、ほとんど {@link #handleClientConfiguration770}\n"
         "     * と同じ。ただし Custom Click Action（0x08）は771だけのパケットのため、\n"
         "     * 771サーバーへはそのまま送り、旧サーバー向けは落とす。\n"
         "     */\n"
         "    private Outcome handleClientConfiguration771(MinecraftPacket packet) {\n"
         "        if (packet.id() == 0x08) {\n"
         "            // Custom Click Action（771のみ。770では Custom Report Details と同ID）。\n"
         "            // 771サーバーへはそのまま送り、旧サーバー向けは落とす。\n"
         "            if (isServer771()) {\n"
         "                return Outcome.toServer(packet);\n"
         "            }\n"
         "            return Outcome.drop();\n"
         "        }\n"
         "        return handleClientConfiguration770(packet);\n"
         "    }"),
        # CONFIG_771 の末尾に、旧クライアント＋内部771の設定フェーズ変換器と
        # 771クライアント＋内部769の設定フェーズ変換器を追加する。
        ("    private Outcome handleClientConfigurationTo771(MinecraftPacket packet) {\n"
         "        return handleClientConfigurationTo770(packet);\n"
         "    }",
         "    private Outcome handleClientConfigurationTo771(MinecraftPacket packet) {\n"
         "        return handleClientConfigurationTo770(packet);\n"
         "    }\n"
         + CONFIG_771_EXTRA),
    ],
}


SERVER_PLAY_771_TO = r'''
    /**
     * 770サーバーのプレイ状態パケットを771形式にする。771に相当が無い／
     * 変換できないものは null を返す（ログ済み）。
     * 771クライアントへの送出と、769／768／767／766クライアントへの
     * 多段変換の最終段で共用する。経験値オーブは770・771とも通常の
     * Spawn Entity（type experience_orb）のため、専用の分岐は要らない。
     */
    private MinecraftPacket to771ServerPacket(MinecraftPacket packet) {
        PacketName name = PacketIds.nameFor(packet.id(), State.PLAY,
                Direction.CLIENTBOUND, ProtocolVersion.V1_21_5).orElse(null);
        if (name == null) {
            logUntranslatedOnce("PLAY_UNKNOWN_0x" + Integer.toHexString(packet.id()));
            return null;
        }
        byte[] translated = switch (name) {
            case PLAY_ENTITY_METADATA ->
                    ChunkTranslator.translateEntityMetadata770To771(packet.payload());
            case PLAY_SET_SLOT ->
                    ChunkTranslator.translateSetSlot770To771(packet.payload());
            case PLAY_SET_CURSOR_ITEM ->
                    ChunkTranslator.translateSetCursorItem770To771(packet.payload());
            case PLAY_SET_PLAYER_INVENTORY ->
                    ChunkTranslator.translateSetPlayerInventory770To771(packet.payload());
            case PLAY_WINDOW_ITEMS ->
                    ChunkTranslator.translateWindowItems770To771(packet.payload());
            case PLAY_SET_COOLDOWN ->
                    ChunkTranslator.translateSetCooldown770To771(packet.payload());
            case PLAY_ENTITY_EQUIPMENT ->
                    ChunkTranslator.translateEntityEquipment770To771(packet.payload());
            case PLAY_TRADE_LIST ->
                    ChunkTranslator.translateTradeList770To771(packet.payload());
            case PLAY_OPEN_WINDOW ->
                    ChunkTranslator.translateOpenWindow770To771(packet.payload());
            case PLAY_BLOCK_ACTION ->
                    ChunkTranslator.translateBlockAction770To771(packet.payload());
            case PLAY_SPAWN_ENTITY ->
                    ChunkTranslator.translateSpawnEntity770To771(packet.payload());
            case PLAY_SOUND_EFFECT ->
                    ChunkTranslator.translateSoundEffect770To771(packet.payload());
            case PLAY_ENTITY_SOUND_EFFECT ->
                    ChunkTranslator.translateEntitySoundEffect770To771(packet.payload());
            case PLAY_STOP_SOUND ->
                    ChunkTranslator.translateStopSound770To771(packet.payload());
            case PLAY_WORLD_PARTICLES ->
                    ChunkTranslator.translateWorldParticles770To771(packet.payload());
            case PLAY_ENTITY_EFFECT ->
                    ChunkTranslator.translateEntityEffect770To771(packet.payload());
            case PLAY_REMOVE_ENTITY_EFFECT ->
                    ChunkTranslator.translateRemoveEntityEffect770To771(packet.payload());
            case PLAY_DAMAGE_EVENT ->
                    ChunkTranslator.translateDamageEvent770To771(packet.payload());
            case PLAY_ENTITY_UPDATE_ATTRIBUTES ->
                    ChunkTranslator.translateUpdateAttributes770To771(packet.payload());
            case PLAY_CHUNK_DATA ->
                    ChunkTranslator.translateChunkData770To771(packet.payload());
            case PLAY_BLOCK_CHANGE ->
                    ChunkTranslator.translateBlockChange770To771(packet.payload());
            case PLAY_MULTI_BLOCK_CHANGE ->
                    ChunkTranslator.translateMultiBlockChange770To771(packet.payload());
            case PLAY_BLOCK_ENTITY_DATA ->
                    ChunkTranslator.translateBlockEntityData770To771(packet.payload());
            case PLAY_PLAYER_INFO_UPDATE ->
                    PlayTranslator.translatePlayerInfo770To771(packet.payload());
            case PLAY_PLAYER_CHAT ->
                    PlayTranslator.translatePlayerChat770To771(packet.payload());
            case PLAY_SYSTEM_CHAT, PLAY_PROFILELESS_CHAT ->
                    packet.payload(); // 同形式
            case PLAY_SYNCHRONIZE_PLAYER_POSITION, PLAY_UPDATE_TIME, PLAY_SET_SLOT_STATE ->
                    packet.payload(); // 同形式
            case PLAY_HELD_ITEM_SLOT ->
                    PlayTranslator.translateSetHeldItem770To771(packet.payload());
            case PLAY_EXPLOSION ->
                    ChunkTranslator.translateExplosion770To771(packet.payload());
            default -> null;
        };
        if (translated == null) {
            logUntranslatedOnce(name);
            return null;
        }
        OptionalInt id771 = PacketTranslator.translateId(name,
                ProtocolVersion.V1_21_5, ProtocolVersion.V1_21_6);
        if (id771.isEmpty()) {
            logUntranslatedOnce(name);
            return null;
        }
        return new MinecraftPacket(id771.getAsInt(), translated);
    }

    /**
     * 内部770→771クライアントのプレイ状態（サーバー→クライアント）。
     * 770→771へ変換してそのまま送る。
     */
    private Outcome handleServerPlay770To771(MinecraftPacket packet) {
        MinecraftPacket as771 = to771ServerPacket(packet);
        if (as771 == null) {
            return Outcome.drop();
        }
        return Outcome.toClient(as771);
    }

    /**
     * 内部769→771クライアントのプレイ状態（サーバー→クライアント）。
     * 769→770→771へ二段で変換する。
     */
    private Outcome handleServerPlay769To771(MinecraftPacket packet) {
        Outcome to770 = handleServerPlay769To770(packet);
        if (to770.toClient().isEmpty()) {
            return to770;
        }
        return handleServerPlay770To771(to770.toClient().get(0));
    }

    /**
     * 内部768→771クライアントのプレイ状態（サーバー→クライアント）。
     * 768→770→771へ二段で変換する。
     */
    private Outcome handleServerPlay768To771(MinecraftPacket packet) {
        Outcome to770 = handleServerPlay768To770(packet);
        if (to770.toClient().isEmpty()) {
            return to770;
        }
        return handleServerPlay770To771(to770.toClient().get(0));
    }

    /**
     * 内部767→771クライアントのプレイ状態（サーバー→クライアント）。
     * 767→770→771へ二段で変換する。
     */
    private Outcome handleServerPlay767To771(MinecraftPacket packet) {
        Outcome to770 = handleServerPlay767To770(packet);
        if (to770.toClient().isEmpty()) {
            return to770;
        }
        return handleServerPlay770To771(to770.toClient().get(0));
    }

    /**
     * 内部766→771クライアントのプレイ状態（サーバー→クライアント）。
     * 766→770→771へ二段で変換する。
     */
    private Outcome handleServerPlay766To771(MinecraftPacket packet) {
        Outcome to770 = handleServerPlay766To770(packet);
        if (to770.toClient().isEmpty()) {
            return to770;
        }
        return handleServerPlay770To771(to770.toClient().get(0));
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
    for name in ("CLIENT_PLAY_770", "SERVER_PLAY_770", "CONFIG_770"):
        blocks[name] = ast.literal_eval(assigns[name])

    new_edits = []
    for name, old, new in edits:
        if new.startswith(old) and new != old:
            kind = "APPEND"
            added = new[len(old):]
            new_edits.append((name.replace("770", "771"), new, new + shift(added)))
        elif old in new and new != old:
            kind = "CONTAINS-OLD"
            assert new.endswith(old), f"{name}: CONTAINS-OLD but not suffix form"
            prefix = new[: len(new) - len(old)]
            new_edits.append((name.replace("770", "771"), new, shift(prefix) + new))
        else:
            kind = "REPLACE"
            new_edits.append((name.replace("770", "771"), new, shift(new)))
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
            print(f"SKIP: {name}（770で適用済みのため771パッチから外す）", file=sys.stderr)
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
    # 770ステージと違い、771ではメソッド名の衝突は無い（shift 後の
    # to771ClientPacket／to770ClientPacketFrom771／to770ServerPacketFrom771
    # はすべて新規名のため、改名の手直しは要らない）。
    new_blocks = {}
    for name in ("CLIENT_PLAY_770", "SERVER_PLAY_770", "CONFIG_770"):
        shifted = shift(blocks[name])
        new_name = name.replace("770", "771")
        if new_name in FIX_BLOCKS:
            shifted = apply_subs(shifted, FIX_BLOCKS[new_name], f"block {new_name}")
        new_blocks[new_name] = shifted

    # ヘッダ（手書き）と EDITS を出力し、ブロックは後段の手作業用に
    # shifted 版をそのまま載せる。
    out_lines = []
    out_lines.append('''#!/usr/bin/env python3
"""ProxySession.java へ Java 1.21.6（プロトコル771）の対応を入れる。

770パッチ（patch-proxysession-770.py）を1段ずらしたもの。生成元は
tools/gen-771-proxysession.py で、EDITS・3ブロック・INSERTS を機械的に
ずらし、771特有の箇所だけ手で直した。

771の要点（770からの差分）:
- クライアント行きのプレイ・設定・ログインのIDは770と変わらない。
  Join Game 0x2B、Spawn Position 0x5A、Chunk Data 0x27、Keep Alive 0x26、
  Ping 0x36、Start Configuration 0x6F、Disconnect 0x1C、Respawn 0x4B、
  Add Resource Pack 0x4A。
- サーバー行きは Change Gamemode 0x04 の挿入で 0x04 以降が+1ずれる。
  Chat Message 0x07→0x08、Chat Command Signed 0x06→0x07、
  Chat Command 0x05→0x06、Window Click 0x10→0x11、Vehicle Move 0x20→0x21、
  Set Creative Slot 0x36→0x37、Keep Alive 応答 0x1A→0x1B、Ping 応答 0x2B→0x2C。
  新規: Change Gamemode 0x04、Custom Click Action 0x41（共に落とす）。
- チャットの checksum（u8）は770・771ともあるため、771⇔770では
  付け足しも剥がしもししない（番号だけ付け替える）。769⇔770の変換器は
  そのまま（769には checksum が無い）。
- Vehicle Move の onGround は769・770・771ともある（768に無い）。
  771⇔770では素通しする。
- Window Click は771・770とも HashedSlot のため落とす。Set Creative Slot は
  771・770とも UntrustedSlot のため、アイテム／成分を付け替えるだけ。
- Player Chat は先頭に globalIndex が770・771ともあるため素通しする。
  769サーバーからの player_chat にはプロキシが 0 から数えて付ける
  （770／771クライアント共用の chatGlobalIndex）。
- 設定フェーズは770と同形（同じ番号・同じ形式）で中継する。
  Registry Data の並び替えだけ 770→771 が要る（minecraft:dialog は
  770には無く771にだけある。jukebox_song は tears の挿入で17番以降が+1）。
  Show Dialog（config 0x12）／Clear Dialog（0x11）は旧クライアント向けに
  落とす。Custom Click Action（config 0x08）は771サーバーへ通し、
  旧サーバー向けに落とす。Custom Report Details／Server Links
  （config サーバー行き）は771では削除されたため、771サーバー向けは落とす。
- 経験値オーブは770・771とも通常の Spawn Entity（type experience_orb）。
  770の to769ServerPacketFrom770 の専用分岐は771→770では使わない。
- ハッピーガスト（771: 56）は770ではガスト（55）に代替する。
  （770の invert ではブタ(94)になるが、見た目が近いガストを選ぶ。
  メタデータの個体差はプロキシが種別を追跡しないため残る。）
- ロケーターバー（tracked_waypoint 0x83）／ダイアログ（show_dialog 0x85／
  clear_dialog 0x84）は770に無いため、旧クライアント向けに落とす。
- 内部771サーバー＋771クライアントはネイティブ（素通し）。

使い方: repo ルートで `python3 detergentmc-protocol/tools/patch-proxysession-771.py`
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
    for name in ("CLIENT_PLAY_771", "SERVER_PLAY_771", "CONFIG_771"):
        out_lines.append(f"{name} = r'''{new_blocks[name]}'''")
        out_lines.append("")
    out_lines.append(f"SERVER_PLAY_771_TO = r'''{SERVER_PLAY_771_TO}'''")
    out_lines.append("")
    # INSERTS: 770版の INSERTS を流用（メソッド名だけずらす）。
    # 770版と違い、SERVER_PLAY_771_TO の挿入は shift 済み INSERTS に
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
    # 770版と違い、CONFIG_ANCHOR は手直しが要る。shift は 768 を動かさない
    # ため、delegate が To768 になるが、live の To770 は To769 へ委譲する。
    # （770パッチの wrong_anchor／right_anchor 手直しと同型。）
    rest_shifted = rest_shifted.replace(
        "    private Outcome handleClientConfigurationTo770(MinecraftPacket packet) {\n"
        "        return handleClientConfigurationTo768(packet);\n"
        "    }",
        "    private Outcome handleClientConfigurationTo770(MinecraftPacket packet) {\n"
        "        return handleClientConfigurationTo769(packet);\n"
        "    }")
    assert 'CONFIG_ANCHOR = """    private Outcome handleClientConfigurationTo770' in rest_shifted, \
        "CONFIG_ANCHOR の shift 結果が想定と違う"
    assert "return handleClientConfigurationTo769(packet);" in rest_shifted, \
        "CONFIG_ANCHOR の delegate が To769 になっていない"
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
