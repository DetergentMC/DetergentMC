#!/usr/bin/env python3
"""insert-767-proxysession.py の補完：766クライアント→内部767の
Join Game／Disconnect／Respawn の素通しと、767クライアント→内部763の
Join Game（763→766変換を流用）を追加する。
"""
import pathlib
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
PATH = ROOT / "src/main/java/io/detergentmc/protocol/ProxySession.java"


def replace_once(text, old, new):
    if text.count(old) != 1:
        print(f"REPLACE 失敗（{text.count(old)}件）: {old[:90]!r}")
        sys.exit(1)
    return text.replace(old, new, 1)


def main():
    text = PATH.read_text(encoding="utf-8")
    if "766／767クライアント：Respawn は766と同一形式" in text:
        print("既に挿入済みのため何もしません。")
        return

    # 1. handleSynthesizedJoinGame：767クライアントも763→766変換を流用する。
    text = replace_once(text,
        "        if (is766()) {\n"
        "            heldJoinGame = PlayTranslator.translateJoinGame763To766(join, registries);\n"
        "            heldJoinGameId = 0x2B;",
        "        if (is766() || is767()) {\n"
        "            // 767の Join Game は766と同一形式のため流用する。\n"
        "            heldJoinGame = PlayTranslator.translateJoinGame763To766(join, registries);\n"
        "            heldJoinGameId = 0x2B;")

    # 2. handleServerJoinGame763：767クライアントも763→766変換を流用する。
    text = replace_once(text,
        "        List<MinecraftPacket> toClient = new ArrayList<>();\n"
        "        if (is766()) {\n"
        "            toClient.add(new MinecraftPacket(0x2B,\n"
        "                    PlayTranslator.translateJoinGame763To766(join, registries)));",
        "        List<MinecraftPacket> toClient = new ArrayList<>();\n"
        "        if (is766() || is767()) {\n"
        "            // 767の Join Game は766と同一形式のため流用する。\n"
        "            toClient.add(new MinecraftPacket(0x2B,\n"
        "                    PlayTranslator.translateJoinGame763To766(join, registries)));")

    # 3. handleServerJoinGame766：766クライアント（内部767の場合）の素通し。
    text = replace_once(text,
        "        if (is767()) {\n"
        "            // 767クライアント：Join Game は766と同一形式のためそのまま通す。\n"
        "            toClient.add(new MinecraftPacket(0x2B, packet.payload()));",
        "        if (is766() || is767()) {\n"
        "            // 766／767クライアント：Join Game は766と同一形式のためそのまま通す\n"
        "            // （内部767＋766クライアントの場合にここを通る。内部766＋766は\n"
        "            // ネイティブのためここには来ない）。\n"
        "            toClient.add(new MinecraftPacket(0x2B, packet.payload()));")

    # 4. handleServerPlayFrom766 の Disconnect：766／767クライアントは 0x1D。
    text = replace_once(text,
        "        // Disconnect（766：0x1D、NBT）。\n"
        "        if (packet.id() == 0x1D) {\n"
        "            closed = true;\n"
        "            if (is763() || is764()) {\n"
        "                byte[] payload = PlayTranslator.translateDisconnect765To763(packet.payload());\n"
        "                int outId = is763() ? 0x1A : 0x1B;\n"
        "                return new Outcome(List.of(),\n"
        "                        List.of(new MinecraftPacket(outId, payload)), false, true);\n"
        "            }\n"
        "            return new Outcome(List.of(),\n"
        "                    List.of(new MinecraftPacket(0x1B, packet.payload())), false, true);\n"
        "        }",
        "        // Disconnect（766：0x1D、NBT）。\n"
        "        if (packet.id() == 0x1D) {\n"
        "            closed = true;\n"
        "            if (is763() || is764()) {\n"
        "                byte[] payload = PlayTranslator.translateDisconnect765To763(packet.payload());\n"
        "                int outId = is763() ? 0x1A : 0x1B;\n"
        "                return new Outcome(List.of(),\n"
        "                        List.of(new MinecraftPacket(outId, payload)), false, true);\n"
        "            }\n"
        "            if (is766() || is767()) {\n"
        "                // 766／767クライアント：Disconnect は 0x1D（NBT）のまま。\n"
        "                return new Outcome(List.of(),\n"
        "                        List.of(new MinecraftPacket(0x1D, packet.payload())), false, true);\n"
        "            }\n"
        "            return new Outcome(List.of(),\n"
        "                    List.of(new MinecraftPacket(0x1B, packet.payload())), false, true);\n"
        "        }")

    # 5. handleServerPlayFrom766 の Respawn：766クライアントも素通し。
    text = replace_once(text,
        "            if (is767()) {\n"
        "                // 767クライアント：Respawn は766と同一形式のためそのまま通す。\n"
        "                return Outcome.toClient(new MinecraftPacket(0x47, packet.payload()));\n"
        "            }",
        "            if (is766() || is767()) {\n"
        "                // 766／767クライアント：Respawn は766と同一形式のためそのまま通す。\n"
        "                return Outcome.toClient(new MinecraftPacket(0x47, packet.payload()));\n"
        "            }")

    PATH.write_text(text, encoding="utf-8")
    print("ProxySession.java の767補完を追加しました。")


if __name__ == "__main__":
    main()
