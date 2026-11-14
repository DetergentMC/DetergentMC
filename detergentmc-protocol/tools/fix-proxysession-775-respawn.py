#!/usr/bin/env python3
"""775パッチで落ちた is773() 分岐を復元する（+ From774 Respawn の is775() 追加）。

背景: 775 (26.1) の ProxySession パッチ (56d7afa) は、From*/JoinGame の
if-chain を「1段ずらす」形で書き換えた際に末尾の is773() 分岐を落とした。
773クライアントが 768/769/770/772 サーバーにいると Respawn が 765形式の
0x45 で送られ、ProxySession773Test の join773Against772/770 が失敗する。
JoinGame770/771/772 の is773() 分岐も同様に落ちており（直接分岐が無いため
汎用ハンドラへの迂回で今は通るが、欠落自体が誤り）、
sendSynthesizedConfigIfReady の is773() 7段並べ替えも落ちている
（773クライアントに766順のレジストリがそのまま届く）。

さらに From774 の Respawn には is775() 分岐がそもそも無かった
（パッチの追加漏れ。775クライアントが774サーバーにいると Respawn が
0x45 で送られる）。これもここで追加する。

復元テキストはすべて 4b51d25（759完了時）の当該メソッドから取った原文。
同一プロトコル時は handleServerPlay の素通しで From* に来ないため、
From773 Respawn の is773() 分岐は実質デッドコードだが、4b51d25の形を
保つため復元する。

使い方: detergentmc/ で python3 detergentmc-protocol/tools/fix-proxysession-775-respawn.py
"""
import re
import sys

PATH = "detergentmc-protocol/src/main/java/io/detergentmc/protocol/ProxySession.java"

RESPAWN_774 = """            if (is774()) {
                // 774クライアント：Respawn は 0x50（同形式）のまま。
                return Outcome.toClient(new MinecraftPacket(0x50, packet.payload()));
            }
"""

RESPAWN_773 = """            if (is773()) {
                // 773クライアント：Respawn は 0x50（同形式）のまま。
                return Outcome.toClient(new MinecraftPacket(0x50, packet.payload()));
            }
"""

RESPAWN_775 = """            if (is775()) {
                // 775クライアント：Respawn は 0x52（同形式）のまま。
                return Outcome.toClient(new MinecraftPacket(0x52, packet.payload()));
            }
"""

JOINGAME_774 = """        if (is774()) {
            advanceFlowSafely(() -> joinFlow.joinGameReceived());
            List<MinecraftPacket> toClient = new ArrayList<>();
            toClient.add(new MinecraftPacket(0x30, packet.payload()));
            while (!pendingPacks.isEmpty()) {
                PendingPack pack = pendingPacks.removeFirst();
                lastSentPack = pack;
                toClient.add(new MinecraftPacket(0x4F,
                        PlayTranslator.translateConfigAddResourcePack764To765(
                                pack, packOverrideForClient())));
            }
            return new Outcome(List.of(), toClient, false, false);
        }
"""

JOINGAME_773 = JOINGAME_774.replace("if (is774()) {", "if (is773()) {", 1)

SYNTH_773 = """                if (is773()) {
                    // 773：766→767→768→769→770→771→772→773へ七段で並べ替える。
                    byte[] payload766to767 =
                            PlayTranslator.translateRegistryData766To767(registryPayload);
                    byte[] payload767to768 =
                            PlayTranslator.translateRegistryData767To768(payload766to767);
                    byte[] payload768to769 =
                            PlayTranslator.translateRegistryData768To769(payload767to768);
                    byte[] payload769to770 =
                            PlayTranslator.translateRegistryData769To770(payload768to769);
                    byte[] payload770to771 =
                            PlayTranslator.translateRegistryData770To771(payload769to770);
                    byte[] payload771to772 =
                            PlayTranslator.translateRegistryData771To772(payload770to771);
                    registryPayload =
                            PlayTranslator.translateRegistryData772To773(payload771to772);
                }
"""

SYNTH_774_HEAD = """                if (is774()) {
                    // 774：766→767→768→769→770→771→772→773→774へ八段で並べ替える。
"""


def method_span(src, name):
    m = re.search(r"private Outcome " + re.escape(name) + r"\(", src)
    if not m:
        raise SystemExit("method not found: " + name)
    k = src.find("{", m.start())
    depth = 0
    p = k
    while p < len(src):
        if src[p] == "{":
            depth += 1
        elif src[p] == "}":
            depth -= 1
            if depth == 0:
                return m.start(), p + 1
        p += 1
    raise SystemExit("unbalanced braces in: " + name)


def method_body(src, name):
    s, e = method_span(src, name)
    return src[s:e]


def replace_in_method(src, name, old, new, expect=1):
    s, e = method_span(src, name)
    body = src[s:e]
    n = body.count(old)
    if n != expect:
        raise SystemExit(
            f"{name}: expected {expect} occurrence(s) of anchor, found {n}")
    return src[:s] + body.replace(old, new, 1) + src[e:]


def main():
    src = open(PATH).read()

    # 1. JoinGame770/771/772: is774() 分岐の直後に is773() 分岐を復元する。
    for ver in ("770", "771", "772"):
        name = "handleServerJoinGame" + ver
        body = method_body(src, name)
        if "if (is773()) {" in body:
            raise SystemExit(name + ": is773() already present, refusing")
        src = replace_in_method(src, name, JOINGAME_774,
                                JOINGAME_774 + JOINGAME_773)
        print("restored is773() in " + name)

    # 2. From768/769/770/772/773 の Respawn: is774() 分岐の直後に is773() を復元する。
    for ver in ("768", "769", "770", "772", "773"):
        name = "handleServerPlayFrom" + ver
        body = method_body(src, name)
        if body.count(RESPAWN_773) != 0:
            raise SystemExit(name + ": Respawn is773() already present, refusing")
        src = replace_in_method(src, name, RESPAWN_774,
                                RESPAWN_774 + RESPAWN_773)
        print("restored Respawn is773() in " + name)

    # 3. From774 の Respawn: is775() 分岐を is773() 分岐の直前に追加する。
    name = "handleServerPlayFrom774"
    body = method_body(src, name)
    if "if (is775()) {" in body.split("// Respawn")[1].split("// 775クライアント")[0]:
        raise SystemExit(name + ": Respawn is775() already present, refusing")
    src = replace_in_method(src, name, RESPAWN_773,
                            RESPAWN_775 + RESPAWN_773)
    print("added Respawn is775() in " + name)

    # 4. sendSynthesizedConfigIfReady: is774() 並べ替えの直前に is773() 七段を復元する。
    name = "sendSynthesizedConfigIfReady"
    body = method_body(src, name)
    if "if (is773()) {" in body:
        raise SystemExit(name + ": is773() already present, refusing")
    src = replace_in_method(src, name, SYNTH_774_HEAD,
                            SYNTH_773 + SYNTH_774_HEAD)
    print("restored is773() reorder in " + name)

    open(PATH, "w").write(src)
    print("ProxySession.java に 10 箇所の修正を適用した")


if __name__ == "__main__":
    main()
