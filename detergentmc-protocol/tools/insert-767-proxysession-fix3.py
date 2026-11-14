#!/usr/bin/env python3
"""ProxySession の767補完その3：
- コンストラクタの serverProtocol 検証に 767 を追加する。
- 767クライアント＋内部766の設定フェーズ中継（handleServerConfiguration767From766）
  と、クライアント→サーバー側の素通し（handleClientConfiguration767 に
  isServer766() を追加）を入れる。
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


def insert_once(text, anchor, block):
    if text.count(anchor) != 1:
        print(f"ANCHOR 失敗（{text.count(anchor)}件）: {anchor[:90]!r}")
        sys.exit(1)
    return text.replace(anchor, block + "\n" + anchor, 1)


def main():
    text = PATH.read_text(encoding="utf-8")
    if "handleServerConfiguration767From766" in text:
        print("既に挿入済みのため何もしません。")
        return

    # 1. コンストラクタの検証に 767 を追加する。
    text = replace_once(text,
        "     * @param serverProtocol 内部バニラのプロトコル（763＝1.20.1、\n"
        "     *                       764＝1.20.2、765＝1.20.4、766＝1.20.5／1.20.6）\n"
        "     */\n"
        "    public ProxySession(int clientProtocol, boolean onlineMode, int serverProtocol) {\n"
        "        if (serverProtocol != ProtocolVersion.V1_20_1\n"
        "                && serverProtocol != ProtocolVersion.V1_20_2\n"
        "                && serverProtocol != ProtocolVersion.V1_20_4\n"
        "                && serverProtocol != ProtocolVersion.V1_20_5) {\n"
        "            throw new IllegalArgumentException(\n"
        "                    \"serverProtocol must be 763 (1.20.1), 764 (1.20.2), \"\n"
        "                            + \"765 (1.20.4) or 766 (1.20.5/1.20.6): \"\n"
        "                            + serverProtocol);\n"
        "        }",
        "     * @param serverProtocol 内部バニラのプロトコル（763＝1.20.1、\n"
        "     *                       764＝1.20.2、765＝1.20.4、766＝1.20.5／1.20.6、\n"
        "     *                       767＝1.21／1.21.1）\n"
        "     */\n"
        "    public ProxySession(int clientProtocol, boolean onlineMode, int serverProtocol) {\n"
        "        if (serverProtocol != ProtocolVersion.V1_20_1\n"
        "                && serverProtocol != ProtocolVersion.V1_20_2\n"
        "                && serverProtocol != ProtocolVersion.V1_20_4\n"
        "                && serverProtocol != ProtocolVersion.V1_20_5\n"
        "                && serverProtocol != ProtocolVersion.V1_21) {\n"
        "            throw new IllegalArgumentException(\n"
        "                    \"serverProtocol must be 763 (1.20.1), 764 (1.20.2), \"\n"
        "                            + \"765 (1.20.4), 766 (1.20.5/1.20.6) or 767 (1.21/1.21.1): \"\n"
        "                            + serverProtocol);\n"
        "        }")
    text = replace_once(text,
        "    /** 内部バニラのプロトコル（763＝1.20.1、764＝1.20.2、765＝1.20.4、766＝1.20.5／1.20.6）。 */",
        "    /** 内部バニラのプロトコル（763＝1.20.1、764＝1.20.2、765＝1.20.4、766＝1.20.5／1.20.6、767＝1.21／1.21.1）。 */")

    # 2. handleClientConfiguration767：内部766も素通しする。
    text = replace_once(text,
        "    private Outcome handleClientConfiguration767(MinecraftPacket packet) {\n"
        "        if (isServer767()) {\n"
        "            // ネイティブ：767の番号のまま通す。\n"
        "            if (packet.id() == 0x03) { // Acknowledge Finish Configuration",
        "    private Outcome handleClientConfiguration767(MinecraftPacket packet) {\n"
        "        if (isServer767() || isServer766()) {\n"
        "            // 内部767（ネイティブ）／内部766：767の番号のまま通す\n"
        "            // （766の設定フェーズのサーバー行き番号は767と同じ）。\n"
        "            if (packet.id() == 0x03) { // Acknowledge Finish Configuration")

    # 3. handleServerConfiguration の is767() 分岐：内部766は専用の中継へ。
    text = replace_once(text,
        "        if (is767()) {\n"
        "            if (isServer767()) {\n"
        "                // 内部767＋767クライアントはネイティブのためここには来ない。\n"
        "                // 念のためそのまま通す。\n"
        "                return Outcome.toClient(packet);\n"
        "            }\n"
        "            return handleServerConfiguration767(packet, isServer764());\n"
        "        }",
        "        if (is767()) {\n"
        "            if (isServer767()) {\n"
        "                // 内部767＋767クライアントはネイティブのためここには来ない。\n"
        "                // 念のためそのまま通す。\n"
        "                return Outcome.toClient(packet);\n"
        "            }\n"
        "            if (isServer766()) {\n"
        "                return handleServerConfiguration767From766(packet);\n"
        "            }\n"
        "            return handleServerConfiguration767(packet, isServer764());\n"
        "        }")

    # 4. handleServerConfiguration767From766 を追加する。
    method = """
    /**
     * 767クライアント＋内部766：設定フェーズを中継する。
     * 766と767の設定フェーズの番号は同じため、ほとんど素通しする。
     * Registry Data だけ766→767へ並べ替え、Tags はレジストリ番号が
     * ずれるため落とす。Cookie Request には空の Cookie Response を、
     * Select Known Packs には空の一覧をプロキシがクライアント役として返す。
     */
    private Outcome handleServerConfiguration767From766(MinecraftPacket packet) {
        return switch (packet.id()) {
            case 0x00 -> { // Cookie Request：空の Cookie Response を返す
                String key = readCookieKey(packet.payload());
                yield Outcome.toServer(new MinecraftPacket(0x01,
                        emptyCookieResponsePayload(key)));
            }
            case 0x01 -> Outcome.toClient(new MinecraftPacket(0x01, packet.payload())); // Custom Payload（同ID）
            case 0x02 -> // Disconnect（766は NBT。767も NBT）
                    Outcome.toClient(packet);
            case 0x03 -> // Finish Configuration
                    Outcome.toClient(packet);
            case 0x04 -> Outcome.toClient(packet); // Keep Alive（同ID）
            case 0x05 -> Outcome.toClient(packet); // Ping（同ID）
            case 0x06 -> Outcome.toClient(packet); // Reset Chat（同ID）
            case 0x07 -> { // Registry Data：766のエントリ配列を767の順序に並べ替える
                Map<String, Nbt.Tag.CompoundTag> parsed =
                        PlayTranslator.parseRegistryData(packet.payload());
                registries.putAll(parsed);
                yield Outcome.toClient(new MinecraftPacket(0x07,
                        PlayTranslator.translateRegistryData766To767(packet.payload())));
            }
            case 0x08 -> Outcome.toClient(packet); // Remove Resource Pack（同ID）
            case 0x09 -> { // Add Resource Pack（766：UUID付き）
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
                LOG.info(() -> "レジストリ番号がずれるため Tags を767向けに落とします（" + playerName + "）。");
                yield Outcome.drop();
            }
            case 0x0E -> // Select Known Packs：空の一覧を返す
                    Outcome.toServer(new MinecraftPacket(0x07, emptySelectKnownPacksPayload()));
            default -> {
                LOG.warning("766の設定フェーズで想定外のパケット（id=0x"
                        + Integer.toHexString(packet.id()) + "）を落とします。");
                yield Outcome.drop();
            }
        };
    }
"""
    text = insert_once(text,
        "    /**\n"
        "     * 766クライアント＋内部767：設定フェーズを中継する。",
        method)

    PATH.write_text(text, encoding="utf-8")
    print("ProxySession.java の767補完その3を追加しました。")


if __name__ == "__main__":
    main()
