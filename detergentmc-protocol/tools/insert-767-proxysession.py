#!/usr/bin/env python3
"""ProxySession に 767（1.21）のルーティングとハンドラを挿入する。

方針（1.20系相互連携と同じく、767と766はプレイIDが同一で
スロット形式も同じため、以下を新設する）：
- 767クライアント→内部763/764/765/766/767（handleClientPlay767／
  handleClientPlayTo767）。内部766向けはスロットを767→766へ変換し、
  内部763/764/765向けは落とす（データコンポーネントのため）。
- 内部767→旧クライアント（handleServerPlayFrom767）は766と同一形式の
  ため handleServerPlayFrom766 へ委譲し、新規パケット 0x7A/0x7B だけ落とす。
- 内部766→767クライアント（handleServerPlay766To767）はスロット等を
  766→767へ変換し、チャンク等の同一形式は素通し、レシピ等は落とす。
- 設定フェーズは767と766で同IDのため、Registry Data の並べ替え
  （PlayTranslator.translateRegistryData*767）と新規設定パケット
  0x0F/0x10 の送受信の落としだけが差分。
"""
import pathlib
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
PATH = ROOT / "src/main/java/io/detergentmc/protocol/ProxySession.java"


def insert_once(text, anchor, block, where="before"):
    if text.count(anchor) != 1:
        print(f"ANCHOR 失敗（{text.count(anchor)}件）: {anchor[:90]!r}")
        sys.exit(1)
    if where == "before":
        return text.replace(anchor, block + "\n" + anchor, 1)
    return text.replace(anchor, anchor + "\n" + block, 1)


def replace_once(text, old, new):
    if text.count(old) != 1:
        print(f"REPLACE 失敗（{text.count(old)}件）: {old[:90]!r}")
        sys.exit(1)
    return text.replace(old, new, 1)


def main():
    text = PATH.read_text(encoding="utf-8")
    if "private boolean is767()" in text:
        print("既に挿入済みのため何もしません。")
        return

    # --- A. ヘルパー ---
    text = insert_once(text,
        "    // ------------------------------------------------------------------\n"
        "    // クライアント→サーバー\n"
        "    // ------------------------------------------------------------------",
        """    /** クライアントが 1.21／1.21.1（767）か。 */
    private boolean is767() {
        return clientProtocol == ProtocolVersion.V1_21;
    }

    /** 内部が 1.21／1.21.1（767）か。 */
    private boolean isServer767() {
        return serverProtocol == ProtocolVersion.V1_21;
    }

    /** 内部が 1.20.3／1.20.4（765）か。 */
    private boolean isServer765() {
        return serverProtocol == ProtocolVersion.V1_20_4;
    }
""")

    # --- B. Login Success ---
    text = replace_once(text,
        "case 0x02 -> { // Login Success（全バージョン同ID。766だけ末尾に strictErrorHandling 付き）",
        "case 0x02 -> { // Login Success（全バージョン同ID。766／767だけ末尾に strictErrorHandling 付き）")
    text = replace_once(text,
        "                    // 766のサーバーが送ってきた Login Success は末尾に",
        "                    // 766／767のサーバーが送ってきた Login Success は末尾に")
    text = replace_once(text,
        "                    byte[] serverPayload = isServer766()\n"
        "                            ? PlayTranslator.translateLoginSuccess766To765(packet.payload())",
        "                    byte[] serverPayload = isServer766() || isServer767()\n"
        "                            ? PlayTranslator.translateLoginSuccess766To765(packet.payload())")
    text = replace_once(text,
        "                if (is764() || is765() || is766()) {",
        "                if (is764() || is765() || is766() || is767()) {")
    text = replace_once(text,
        "                    // 764／765／766クライアント：クライアントもバニラもこれから",
        "                    // 764／765／766／767クライアント：クライアントもバニラもこれから")
    text = replace_once(text,
        "     * 766クライアントには false を1バイト足し、それ以外はそのまま返す。\n"
        "     */\n"
        "    private byte[] adjustLoginSuccessPayload(byte[] payload) {\n"
        "        return is766()\n"
        "                ? PlayTranslator.translateLoginSuccess765To766(payload)\n"
        "                : payload;",
        "     * 766／767クライアントには false を1バイト足し、それ以外はそのまま返す。\n"
        "     */\n"
        "    private byte[] adjustLoginSuccessPayload(byte[] payload) {\n"
        "        return is766() || is767()\n"
        "                ? PlayTranslator.translateLoginSuccess765To766(payload)\n"
        "                : payload;")

    # --- C. handleClientPlay の振り分け ---
    text = replace_once(text,
        "        // 766クライアント：番号を内部向けに付け替える。スロットを含む\n"
        "        // パケット（Window Click／Set Creative Slot）は766のデータ\n"
        "        // コンポーネント形式のため変換できず落とす。\n"
        "        if (is766()) {\n"
        "            return handleClientPlay766(packet);\n"
        "        }\n"
        "        // 内部766：763／764／765クライアントの番号を766向けに付け替える。\n"
        "        if (isServer766()) {\n"
        "            return handleClientPlayTo766(packet);\n"
        "        }",
        "        // 767クライアント：番号を内部向けに付け替える。内部766／767向けは\n"
        "        // スロットを767→766へ変換する。内部763／764／765向けは766クライアントと\n"
        "        // 同じくデータコンポーネントのため落とす。\n"
        "        if (is767()) {\n"
        "            return handleClientPlay767(packet);\n"
        "        }\n"
        "        // 766クライアント：番号を内部向けに付け替える。スロットを含む\n"
        "        // パケット（Window Click／Set Creative Slot）は766のデータ\n"
        "        // コンポーネント形式のため変換できず落とす。\n"
        "        if (is766()) {\n"
        "            return handleClientPlay766(packet);\n"
        "        }\n"
        "        // 内部767：763／764／765／766クライアントの番号を767向けに付け替える。\n"
        "        if (isServer767()) {\n"
        "            return handleClientPlayTo767(packet);\n"
        "        }\n"
        "        // 内部766：763／764／765クライアントの番号を766向けに付け替える。\n"
        "        if (isServer766()) {\n"
        "            return handleClientPlayTo766(packet);\n"
        "        }")

    # --- D. handleClientPlay767（handleClientPlayTo766 の javadoc の直前） ---
    client767 = """
    /**
     * 767クライアント→内部763／764／765／766／767のプレイ状態（クライアント→サーバー）。
     * チャット・設定・Keep Alive・Pong・Plugin Message は形式が同じため
     * 番号だけ付け替える。Chat Command は767で符号なし（文字列だけ）と
     * 符号付き（完全な形式）に分かれているため、符号なしは完全な形式に
     * 合成し（内部763／764／765向け。内部766／767は符号なしのまま通す）、
     * 符号付きはそのまま送る。
     * Window Click／Set Creative Slot は内部766／767向けはスロットを
     * 767→766へ変換する（同形式）。内部763／764／765向けは766クライアントと
     * 同じくデータコンポーネントのため変換できず落とす。
     * Configuration Acknowledged・Chunk Batch Received は763には無いため
     * 763向けは落とす。
     */
    private Outcome handleClientPlay767(MinecraftPacket packet) {
        return switch (packet.id()) {
            case 0x06 -> { // Chat Message
                int outId = isServer766() || isServer767() ? 0x06 : 0x05;
                yield Outcome.toServer(new MinecraftPacket(outId, packet.payload()));
            }
            case 0x04 -> { // Chat Command（符号なし。文字列だけ）
                if (isServer766() || isServer767()) {
                    // 766／767も符号なしの Chat Command（0x04）は同じ形式。
                    yield Outcome.toServer(packet);
                }
                byte[] payload = PlayTranslator.translateChatCommand767To765(packet.payload());
                yield Outcome.toServer(new MinecraftPacket(0x04, payload));
            }
            case 0x05 -> { // Chat Command Signed（完全な形式）
                int outId = isServer766() || isServer767() ? 0x05 : 0x04;
                yield Outcome.toServer(new MinecraftPacket(outId, packet.payload()));
            }
            case 0x0A -> { // Settings
                int outId = isServer763() ? 0x08
                        : isServer764() || isServer765() ? 0x09 : 0x0A;
                yield Outcome.toServer(new MinecraftPacket(outId, packet.payload()));
            }
            case 0x18 -> { // Keep Alive
                int outId = isServer763() ? 0x12 : isServer764() ? 0x14
                        : isServer765() ? 0x15 : 0x18;
                yield Outcome.toServer(new MinecraftPacket(outId, packet.payload()));
            }
            case 0x27 -> { // Pong
                int outId = isServer763() ? 0x20 : isServer764() ? 0x23
                        : isServer765() ? 0x24 : 0x27;
                yield Outcome.toServer(new MinecraftPacket(outId, packet.payload()));
            }
            case 0x2B -> { // Resource Pack Response（767：UUID＋結果）
                if (lastSentPack == null) {
                    LOG.warning("送っていないリソースパックへの応答（id=0x2B）を落とします。");
                    yield Outcome.drop();
                }
                if (isServer763() || isServer764()) {
                    byte[] payload = stripResourcePackResponseUuid(packet.payload());
                    yield Outcome.toServer(new MinecraftPacket(
                            isServer763() ? 0x24 : 0x27, payload));
                }
                yield Outcome.toServer(new MinecraftPacket(
                        isServer765() ? 0x28 : 0x2B, packet.payload()));
            }
            case 0x0C -> { // Configuration Acknowledged
                if (isServer763()) {
                    LOG.warning("内部763に Configuration Acknowledged を送れないため落とします。");
                    yield Outcome.drop();
                }
                clientState = ClientState.CONFIGURATION;
                serverState = ServerState.CONFIGURATION;
                yield Outcome.toServer(new MinecraftPacket(0x0B, packet.payload()));
            }
            case 0x08 -> { // Chunk Batch Received
                if (isServer763()) {
                    yield Outcome.drop();
                }
                yield Outcome.toServer(new MinecraftPacket(0x07, packet.payload()));
            }
            case 0x0E -> { // Window Click
                if (isServer766() || isServer767()) {
                    byte[] payload = ChunkTranslator.translateWindowClick767To766(
                            packet.payload());
                    yield Outcome.toServer(new MinecraftPacket(0x0E, payload));
                }
                logUntranslatedOnce("PLAY_WINDOW_CLICK");
                yield Outcome.drop();
            }
            case 0x32 -> { // Set Creative Slot
                if (isServer766() || isServer767()) {
                    byte[] payload = ChunkTranslator.translateSetCreativeSlot767To766(
                            packet.payload());
                    yield Outcome.toServer(new MinecraftPacket(0x32, payload));
                }
                logUntranslatedOnce("PLAY_SET_CREATIVE_SLOT");
                yield Outcome.drop();
            }
            case 0x12 -> { // Custom Payload → Plugin Message
                int outId = isServer763() ? 0x0D : isServer764() ? 0x0F
                        : isServer765() ? 0x10 : 0x12;
                yield Outcome.toServer(new MinecraftPacket(outId, packet.payload()));
            }
            case 0x00 -> Outcome.toServer(packet); // Teleport Confirm（同ID）
            case 0x03 -> Outcome.toServer(packet); // Message Acknowledgement（同ID）
            case 0x11 -> Outcome.drop(); // Cookie Response（内部に送る先が無い）
            default -> {
                Optional<PacketName> name = PacketIds.nameFor(packet.id(), State.PLAY,
                        Direction.SERVERBOUND, ProtocolVersion.V1_21);
                if (name.isPresent()) {
                    OptionalInt serverId = PacketTranslator.translateId(
                            name.get(), ProtocolVersion.V1_21, serverProtocol);
                    if (serverId.isPresent()) {
                        yield Outcome.toServer(new MinecraftPacket(serverId.getAsInt(),
                                packet.payload()));
                    }
                }
                LOG.warning("767向けに変換できないプレイ状態のパケット（id=0x"
                        + Integer.toHexString(packet.id()) + "）を落とします。");
                yield Outcome.drop();
            }
        };
    }
"""
    text = insert_once(text,
        "    /**\n"
        "     * 763／764／765クライアント→内部766のプレイ状態（クライアント→サーバー）。",
        client767)

    # --- E. handleClientPlayTo767（handleClientPlayTo763 の javadoc の直前） ---
    to767 = """
    /**
     * 763／764／765／766クライアント→内部767のプレイ状態（クライアント→サーバー）。
     * 767のプレイIDは766とすべて同じため、番号の付け替えは
     * {@link #handleClientPlayTo766} と同じである。Chat Command（完全な形式）は
     * 767の Chat Command Signed（0x05）に送る。Window Click／Set Creative Slot は
     * 767のスロットがデータコンポーネント形式のため変換できず落とす。
     */
    private Outcome handleClientPlayTo767(MinecraftPacket packet) {
        if (is763()) {
            return switch (packet.id()) {
                case 0x05 -> // Chat Message（763→767 0x06。同形式）
                        Outcome.toServer(new MinecraftPacket(0x06, packet.payload()));
                case 0x04 -> // Chat Command（完全な形式→Chat Command Signed 0x05）
                        Outcome.toServer(new MinecraftPacket(0x05, packet.payload()));
                case 0x08 -> // Settings（763→767 0x0A）
                        Outcome.toServer(new MinecraftPacket(0x0A, packet.payload()));
                case 0x12 -> // Keep Alive（763→767 0x18）
                        Outcome.toServer(new MinecraftPacket(0x18, packet.payload()));
                case 0x20 -> // Pong（763→767 0x27）
                        Outcome.toServer(new MinecraftPacket(0x27, packet.payload()));
                case 0x24 -> { // Resource Pack Response（763：結果のみ→UUID付き）
                    if (lastSentPack == null) {
                        LOG.warning("送っていないリソースパックへの応答（id=0x24）を落とします。");
                        yield Outcome.drop();
                    }
                    byte[] payload = PlayTranslator.translateResourcePackResponse763To765(
                            packet.payload(), lastSentPack);
                    yield Outcome.toServer(new MinecraftPacket(0x2B, payload));
                }
                case 0x0B, 0x2B -> { // Window Click／Set Creative Slot：データコンポーネントのため落とす
                    logUntranslatedOnce(packet.id() == 0x0B
                            ? "PLAY_WINDOW_CLICK" : "PLAY_SET_CREATIVE_SLOT");
                    yield Outcome.drop();
                }
                case 0x0D -> // Plugin Message → Custom Payload
                        Outcome.toServer(new MinecraftPacket(0x12, packet.payload()));
                case 0x00 -> Outcome.toServer(packet); // Teleport Confirm（同ID）
                case 0x03 -> Outcome.toServer(packet); // Message Acknowledgement（同ID）
                default -> {
                    OptionalInt serverId = PacketTranslator.translateId(
                            PacketIds.nameFor(packet.id(), State.PLAY,
                                    Direction.SERVERBOUND, 763).orElse(null), 763, 767);
                    if (serverId.isPresent()) {
                        yield Outcome.toServer(new MinecraftPacket(serverId.getAsInt(),
                                packet.payload()));
                    }
                    LOG.warning("767向けに変換できないプレイ状態のパケット（id=0x"
                            + Integer.toHexString(packet.id()) + "）を落とします。");
                    yield Outcome.drop();
                }
            };
        }
        if (is764()) {
            return switch (packet.id()) {
                case 0x05 -> // Chat Message（764→767 0x06。同形式）
                        Outcome.toServer(new MinecraftPacket(0x06, packet.payload()));
                case 0x04 -> // Chat Command（完全な形式→Chat Command Signed 0x05）
                        Outcome.toServer(new MinecraftPacket(0x05, packet.payload()));
                case 0x09 -> // Settings（764→767 0x0A）
                        Outcome.toServer(new MinecraftPacket(0x0A, packet.payload()));
                case 0x14 -> // Keep Alive（764→767 0x18）
                        Outcome.toServer(new MinecraftPacket(0x18, packet.payload()));
                case 0x23 -> // Pong（764→767 0x27）
                        Outcome.toServer(new MinecraftPacket(0x27, packet.payload()));
                case 0x27 -> { // Resource Pack Response（764：結果のみ→UUID付き）
                    if (lastSentPack == null) {
                        LOG.warning("送っていないリソースパックへの応答（id=0x27）を落とします。");
                        yield Outcome.drop();
                    }
                    byte[] payload = PlayTranslator.translateResourcePackResponse764To765(
                            packet.payload(), lastSentPack);
                    yield Outcome.toServer(new MinecraftPacket(0x2B, payload));
                }
                case 0x0D, 0x2E -> { // Window Click／Set Creative Slot：データコンポーネントのため落とす
                    logUntranslatedOnce(packet.id() == 0x0D
                            ? "PLAY_WINDOW_CLICK" : "PLAY_SET_CREATIVE_SLOT");
                    yield Outcome.drop();
                }
                case 0x0B -> { // Configuration Acknowledged（同ID・同形式）
                    clientState = ClientState.CONFIGURATION;
                    serverState = ServerState.CONFIGURATION;
                    yield Outcome.toServer(new MinecraftPacket(0x0C, packet.payload()));
                }
                case 0x07 -> Outcome.toServer(new MinecraftPacket(0x08, packet.payload()));
                case 0x0F -> // Plugin Message → Custom Payload
                        Outcome.toServer(new MinecraftPacket(0x12, packet.payload()));
                case 0x00 -> Outcome.toServer(packet); // Teleport Confirm（同ID）
                case 0x03 -> Outcome.toServer(packet); // Message Acknowledgement（同ID）
                default -> {
                    OptionalInt serverId = PacketTranslator.translateId(
                            PacketIds.nameFor(packet.id(), State.PLAY,
                                    Direction.SERVERBOUND, 764).orElse(null), 764, 767);
                    if (serverId.isPresent()) {
                        yield Outcome.toServer(new MinecraftPacket(serverId.getAsInt(),
                                packet.payload()));
                    }
                    LOG.warning("767向けに変換できないプレイ状態のパケット（id=0x"
                            + Integer.toHexString(packet.id()) + "）を落とします。");
                    yield Outcome.drop();
                }
            };
        }
        // 765／766クライアント。0x04／0x05 は765と766で意味が違うため
        // is766() で分ける（765：0x04 Chat Command／0x05 Chat Message、
        // 766：0x04 Chat Command（符号なし）／0x05 Chat Command Signed／
        // 0x06 Chat Message）。
        return switch (packet.id()) {
            case 0x05 -> { // Chat Message（765→767 0x06）／Chat Command Signed（766→767 0x05。同ID）
                if (is766()) {
                    yield Outcome.toServer(packet);
                }
                yield Outcome.toServer(new MinecraftPacket(0x06, packet.payload()));
            }
            case 0x04 -> { // Chat Command（765：完全な形式→0x05。766：符号なし→合成して0x05）
                if (is766()) {
                    byte[] payload = PlayTranslator.translateChatCommand766To765(
                            packet.payload());
                    yield Outcome.toServer(new MinecraftPacket(0x05, payload));
                }
                yield Outcome.toServer(new MinecraftPacket(0x05, packet.payload()));
            }
            case 0x06 -> { // Chat Message（766→767 0x06。同ID・同形式）
                if (!is766()) {
                    LOG.warning("765のプレイ状態で想定外のパケット（id=0x06）を落とします。");
                    yield Outcome.drop();
                }
                yield Outcome.toServer(packet);
            }
            case 0x09 -> // Settings（765／766→767 0x0A）
                    Outcome.toServer(new MinecraftPacket(0x0A, packet.payload()));
            case 0x0A -> { // Settings（766→767 0x0A。同ID）
                if (!is766()) {
                    LOG.warning("765のプレイ状態で想定外のパケット（id=0x0A）を落とします。");
                    yield Outcome.drop();
                }
                yield Outcome.toServer(packet);
            }
            case 0x15 -> // Keep Alive（765→767 0x18）
                    Outcome.toServer(new MinecraftPacket(0x18, packet.payload()));
            case 0x18 -> { // Keep Alive（766→767 0x18。同ID）
                if (!is766()) {
                    LOG.warning("765のプレイ状態で想定外のパケット（id=0x18）を落とします。");
                    yield Outcome.drop();
                }
                yield Outcome.toServer(packet);
            }
            case 0x24 -> // Pong（765→767 0x27）
                    Outcome.toServer(new MinecraftPacket(0x27, packet.payload()));
            case 0x27 -> { // Pong（766→767 0x27。同ID）
                if (!is766()) {
                    LOG.warning("765のプレイ状態で想定外のパケット（id=0x27）を落とします。");
                    yield Outcome.drop();
                }
                yield Outcome.toServer(packet);
            }
            case 0x28 -> { // Resource Pack Response（765：UUID＋結果→767 0x2B）
                if (is766()) {
                    LOG.warning("766のプレイ状態で想定外のパケット（id=0x28）を落とします。");
                    yield Outcome.drop();
                }
                if (lastSentPack == null) {
                    LOG.warning("送っていないリソースパックへの応答（id=0x28）を落とします。");
                    yield Outcome.drop();
                }
                yield Outcome.toServer(new MinecraftPacket(0x2B, packet.payload()));
            }
            case 0x2B -> { // Resource Pack Response（766：UUID＋結果→767 0x2B。同ID）
                if (!is766()) {
                    LOG.warning("765のプレイ状態で想定外のパケット（id=0x2B）を落とします。");
                    yield Outcome.drop();
                }
                if (lastSentPack == null) {
                    LOG.warning("送っていないリソースパックへの応答（id=0x2B）を落とします。");
                    yield Outcome.drop();
                }
                yield Outcome.toServer(packet);
            }
            case 0x0D, 0x2F, 0x0E, 0x32 -> { // Window Click／Set Creative Slot：データコンポーネントのため落とす
                logUntranslatedOnce(packet.id() == 0x0D || packet.id() == 0x0E
                        ? "PLAY_WINDOW_CLICK" : "PLAY_SET_CREATIVE_SLOT");
                yield Outcome.drop();
            }
            case 0x0B -> { // Configuration Acknowledged（765→767 0x0C）
                clientState = ClientState.CONFIGURATION;
                serverState = ServerState.CONFIGURATION;
                yield Outcome.toServer(new MinecraftPacket(0x0C, packet.payload()));
            }
            case 0x0C -> { // Configuration Acknowledged（766→767 0x0C。同ID）
                if (!is766()) {
                    LOG.warning("765のプレイ状態で想定外のパケット（id=0x0C）を落とします。");
                    yield Outcome.drop();
                }
                clientState = ClientState.CONFIGURATION;
                serverState = ServerState.CONFIGURATION;
                yield Outcome.toServer(packet);
            }
            case 0x07 -> Outcome.toServer(new MinecraftPacket(0x08, packet.payload())); // Chunk Batch Received（765→767）
            case 0x08 -> { // Chunk Batch Received（766→767 0x08。同ID）
                if (!is766()) {
                    LOG.warning("765のプレイ状態で想定外のパケット（id=0x08）を落とします。");
                    yield Outcome.drop();
                }
                yield Outcome.toServer(packet);
            }
            case 0x10 -> // Plugin Message（765）→ Custom Payload
                    Outcome.toServer(new MinecraftPacket(0x12, packet.payload()));
            case 0x12 -> { // Custom Payload（766）→ Custom Payload（同ID）
                if (!is766()) {
                    LOG.warning("765のプレイ状態で想定外のパケット（id=0x12）を落とします。");
                    yield Outcome.drop();
                }
                yield Outcome.toServer(packet);
            }
            case 0x00 -> Outcome.toServer(packet); // Teleport Confirm（同ID）
            case 0x03 -> Outcome.toServer(packet); // Message Acknowledgement（同ID）
            case 0x11 -> Outcome.drop(); // Cookie Response（内部に送る先が無い）
            default -> {
                int from = is766() ? 766 : 765;
                OptionalInt serverId = PacketTranslator.translateId(
                        PacketIds.nameFor(packet.id(), State.PLAY,
                                Direction.SERVERBOUND, from).orElse(null), from, 767);
                if (serverId.isPresent()) {
                    yield Outcome.toServer(new MinecraftPacket(serverId.getAsInt(),
                            packet.payload()));
                }
                LOG.warning("767向けに変換できないプレイ状態のパケット（id=0x"
                        + Integer.toHexString(packet.id()) + "）を落とします。");
                yield Outcome.drop();
            }
        };
    }
"""
    text = insert_once(text,
        "    /**\n"
        "     * 764／765クライアント→内部763のプレイ状態（クライアント→サーバー）。",
        to767)

    # --- F. handleClientConfiguration の振り分け ---
    text = replace_once(text,
        "        // 766クライアント：番号を764／765向けに付け替える。\n"
        "        if (is766()) {\n"
        "            return handleClientConfiguration766(packet);\n"
        "        }\n"
        "        // 764／765クライアント＋内部766：番号を766向けに付け替える。\n"
        "        if (isServer766()) {\n"
        "            return handleClientConfigurationTo766(packet);\n"
        "        }",
        "        // 767クライアント：番号を764／765向けに付け替える。\n"
        "        if (is767()) {\n"
        "            return handleClientConfiguration767(packet);\n"
        "        }\n"
        "        // 766クライアント＋内部767：番号を767向けに付け替える。\n"
        "        if (is766() && isServer767()) {\n"
        "            return handleClientConfiguration766To767(packet);\n"
        "        }\n"
        "        // 766クライアント：番号を764／765向けに付け替える。\n"
        "        if (is766()) {\n"
        "            return handleClientConfiguration766(packet);\n"
        "        }\n"
        "        // 764／765クライアント＋内部767：番号を767向けに付け替える。\n"
        "        if (isServer767()) {\n"
        "            return handleClientConfigurationTo767(packet);\n"
        "        }\n"
        "        // 764／765クライアント＋内部766：番号を766向けに付け替える。\n"
        "        if (isServer766()) {\n"
        "            return handleClientConfigurationTo766(packet);\n"
        "        }")

    # --- G. クライアント設定の767メソッド（handleClientConfiguration の javadoc の直前） ---
    client_config = """
    /**
     * 767クライアントの設定フェーズ（クライアント→バニラ）。
     * 内部767（ネイティブ）は番号のまま通す（Finish Configuration の
     * 状態遷移だけ行う）。内部764／765向けは766クライアントと同じく
     * 番号を付け替える。Custom Report Details（0x08）／Server Links
     * （0x09）は767で追加された設定フェーズのサーバー行きパケットで、
     * 旧サーバーに無いため落とす。
     */
    private Outcome handleClientConfiguration767(MinecraftPacket packet) {
        if (isServer767()) {
            // ネイティブ：767の番号のまま通す。
            if (packet.id() == 0x03) { // Acknowledge Finish Configuration
                clientState = ClientState.PLAY;
                serverState = ServerState.PLAY;
                if (joinFlow.current() == ClientJoinFlow.Stage.CONFIGURATION) {
                    advanceFlowSafely(() -> joinFlow.configurationFinished());
                }
            }
            return Outcome.toServer(packet);
        }
        if (packet.id() == 0x08 || packet.id() == 0x09) {
            // Custom Report Details／Server Links：旧サーバーに無い。
            return Outcome.drop();
        }
        return handleClientConfiguration766(packet);
    }

    /**
     * 766クライアント＋内部767の設定フェーズ（クライアント→バニラ）。
     * 766と767の設定フェーズのサーバー行き番号は同じため、ほぼ素通し
     * （Finish Configuration の状態遷移だけ行う）。
     * Cookie Response・Select Known Packs はサーバー側が送ってこない
     * （プロキシが消費している）ため、クライアントから来たら落とす。
     */
    private Outcome handleClientConfiguration766To767(MinecraftPacket packet) {
        return switch (packet.id()) {
            case 0x00 -> Outcome.toServer(packet); // Settings（同ID・同形式）
            case 0x01 -> Outcome.drop(); // Cookie Response（サーバーは送ってこない）
            case 0x02 -> // Custom Payload（同ID・同形式）
                    Outcome.toServer(packet);
            case 0x03 -> { // Acknowledge Finish Configuration
                clientState = ClientState.PLAY;
                serverState = ServerState.PLAY;
                if (joinFlow.current() == ClientJoinFlow.Stage.CONFIGURATION) {
                    advanceFlowSafely(() -> joinFlow.configurationFinished());
                }
                yield Outcome.toServer(packet);
            }
            case 0x04 -> Outcome.toServer(packet); // Keep Alive（同ID）
            case 0x05 -> Outcome.toServer(packet); // Pong（同ID）
            case 0x06 -> { // Resource Pack Response（766：UUID＋結果。同形式）
                if (lastSentPack == null) {
                    LOG.warning("送っていないリソースパックへの応答（設定 0x06）を落とします。");
                    yield Outcome.drop();
                }
                yield Outcome.toServer(packet);
            }
            case 0x07 -> Outcome.drop(); // Select Known Packs（サーバーは送ってこない）
            default -> {
                LOG.warning("766の設定フェーズで想定外のパケット（id=0x"
                        + Integer.toHexString(packet.id()) + "）を落とします。");
                yield Outcome.drop();
            }
        };
    }

    /**
     * 764／765クライアント＋内部767の設定フェーズ（クライアント→バニラ）。
     * 767の番号は766と同じため {@link #handleClientConfigurationTo766} と同じ。
     */
    private Outcome handleClientConfigurationTo767(MinecraftPacket packet) {
        return handleClientConfigurationTo766(packet);
    }
"""
    text = insert_once(text,
        "    /**\n"
        "     * 764の設定フェーズ（クライアント→バニラ）：中継する。",
        client_config)

    # --- H. handleServerConfiguration の振り分け ---
    text = replace_once(text,
        "        if (is763()) {\n"
        "            if (isServer766()) {\n"
        "                return handleServerConfiguration763From766(packet);\n"
        "            }",
        "        if (is763()) {\n"
        "            if (isServer767()) {\n"
        "                return handleServerConfiguration763From767(packet);\n"
        "            }\n"
        "            if (isServer766()) {\n"
        "                return handleServerConfiguration763From766(packet);\n"
        "            }")
    text = replace_once(text,
        "        // 766クライアントは設定フェーズを中継する（番号と Registry Data\n"
        "        // の形式だけ変換する）。\n"
        "        if (is766()) {\n"
        "            if (isServer766()) {\n"
        "                // 内部766＋766クライアントはネイティブのためここには来ない。\n"
        "                // 念のためそのまま通す。\n"
        "                return Outcome.toClient(packet);\n"
        "            }\n"
        "            return handleServerConfiguration766(packet, isServer764());\n"
        "        }\n"
        "        // 764／765クライアントは設定フェーズを中継する。\n"
        "        // 内部766の場合は番号と Registry Data の形式を変換する。\n"
        "        if (isServer766()) {\n"
        "            return handleServerConfigurationFrom766(packet, is764());\n"
        "        }",
        "        // 767クライアントは設定フェーズを中継する（番号と Registry Data\n"
        "        // の形式だけ変換する）。\n"
        "        if (is767()) {\n"
        "            if (isServer767()) {\n"
        "                // 内部767＋767クライアントはネイティブのためここには来ない。\n"
        "                // 念のためそのまま通す。\n"
        "                return Outcome.toClient(packet);\n"
        "            }\n"
        "            return handleServerConfiguration767(packet, isServer764());\n"
        "        }\n"
        "        // 766クライアントは設定フェーズを中継する（番号と Registry Data\n"
        "        // の形式だけ変換する）。\n"
        "        if (is766()) {\n"
        "            if (isServer766()) {\n"
        "                // 内部766＋766クライアントはネイティブのためここには来ない。\n"
        "                // 念のためそのまま通す。\n"
        "                return Outcome.toClient(packet);\n"
        "            }\n"
        "            if (isServer767()) {\n"
        "                return handleServerConfiguration766From767(packet);\n"
        "            }\n"
        "            return handleServerConfiguration766(packet, isServer764());\n"
        "        }\n"
        "        // 764／765クライアントは設定フェーズを中継する。\n"
        "        // 内部766の場合は番号と Registry Data の形式を変換する。\n"
        "        if (isServer766()) {\n"
        "            return handleServerConfigurationFrom766(packet, is764());\n"
        "        }\n"
        "        // 内部767の場合は番号と Registry Data の形式を変換する。\n"
        "        if (isServer767()) {\n"
        "            return handleServerConfigurationFrom767(packet, is764());\n"
        "        }")

    # --- I. サーバー設定の767メソッド ---
    # I-1. handleServerConfiguration763From767（763From766 の javadoc の直前）
    server_config_763 = """
    /**
     * 763クライアント＋内部767：設定フェーズをプロキシが767クライアント役
     * として消費する。767の設定フェーズは766と同じ番号・形式で、
     * Custom Report Details（0x0F）／Server Links（0x10）が追加されている。
     * Cookie Request には空の Cookie Response を、Select Known Packs には
     * 空の一覧をプロキシがクライアント役として返す。Transfer は763クライアントに
     * 送る先が無いため落とす。Custom Report Details／Server Links は
     * 763に相当が無いため落とす。
     * Join Game は767形式で来るので、763向けには
     * {@link PlayTranslator#translateJoinGame766To763} で codec を埋め込む
     * （767の Join Game は766と同一形式）。
     */
    private Outcome handleServerConfiguration763From767(MinecraftPacket packet) {
        return switch (packet.id()) {
            case 0x00 -> { // Cookie Request：空の Cookie Response を返す
                String key = readCookieKey(packet.payload());
                yield Outcome.toServer(new MinecraftPacket(0x01,
                        emptyCookieResponsePayload(key)));
            }
            case 0x01 -> Outcome.drop(); // Custom Payload（brand 等）
            case 0x02 -> { // Disconnect（NBT）
                byte[] payload = PlayTranslator.translateDisconnect765To763(packet.payload());
                yield closeWithPacket(new MinecraftPacket(0x00, payload));
            }
            case 0x03 -> { // Finish Configuration
                serverState = ServerState.PLAY;
                if (!reconfiguring) {
                    // 参加時の設定フェーズ：そのまま終わる。
                    yield Outcome.toServer(new MinecraftPacket(0x03, new byte[0]));
                }
                // プレイ中の再設定が終わった。溜めたクライアントパケットを送り、
                // 再設定中に来たリソースパックがあれば763向けに送る。
                reconfiguring = false;
                List<MinecraftPacket> toServer = new ArrayList<>();
                toServer.add(new MinecraftPacket(0x03, new byte[0]));
                toServer.addAll(queuedClientPackets);
                queuedClientPackets.clear();
                List<MinecraftPacket> toClient = new ArrayList<>();
                while (!pendingPacks.isEmpty()) {
                    PendingPack pack = pendingPacks.removeFirst();
                    lastSentPack = pack;
                    toClient.add(new MinecraftPacket(0x40,
                            PlayTranslator.buildPlayResourcePack763(pack, pack763)));
                }
                yield new Outcome(toServer, toClient, false, false);
            }
            case 0x04 -> Outcome.toServer(packet); // Keep Alive（応答は同ID）
            case 0x05 -> Outcome.toServer(packet); // Ping→Pong は同ID・同形式
            case 0x06 -> Outcome.drop(); // Reset Chat（763に相当無し）
            case 0x07 -> { // Registry Data（767はエントリ配列。parseRegistryData が読む）
                Map<String, Nbt.Tag.CompoundTag> parsed =
                        PlayTranslator.parseRegistryData(packet.payload());
                registries.putAll(parsed);
                yield Outcome.drop();
            }
            case 0x08 -> Outcome.drop(); // Remove Resource Pack（763に相当無し）
            case 0x09 -> { // Add Resource Pack（767：UUID付き）：保持して Join Game の後に送る
                pendingPacks.add(PlayTranslator.parseConfigAddResourcePack(packet.payload()));
                yield Outcome.drop();
            }
            case 0x0A -> Outcome.drop(); // Store Cookie（763に相当無し）
            case 0x0B -> { // Transfer：763クライアントに送る先が無いため落とす
                LOG.info(() -> "763クライアントに送れない Transfer を落とします（" + playerName + "）。");
                yield Outcome.drop();
            }
            case 0x0C, 0x0D -> Outcome.drop(); // Feature Flags / Tags
            case 0x0E -> // Select Known Packs：空の一覧を返す
                    Outcome.toServer(new MinecraftPacket(0x07, emptySelectKnownPacksPayload()));
            case 0x0F, 0x10 -> Outcome.drop(); // Custom Report Details／Server Links（763に相当無し）
            default -> {
                LOG.warning("767の設定フェーズで想定外のパケット（id=0x"
                        + Integer.toHexString(packet.id()) + "）を落とします。");
                yield Outcome.drop();
            }
        };
    }
"""
    text = insert_once(text,
        "    /**\n"
        "     * 763クライアント＋内部766：設定フェーズをプロキシが766クライアント役",
        server_config_763)

    # I-2. handleServerConfiguration767（766版の javadoc の直前）
    server_config_767 = """
    /**
     * 767クライアント＋内部764／765：設定フェーズを中継する。
     * {@link #handleServerConfiguration766} と同じ番号の付け替えで、
     * Registry Data だけ765→767へ並べ替える（codec 段階で合成する）。
     * Add Resource Pack は767の UUID 付きにし（767用パックを出す）、
     * Tags はレジストリ番号がずれるため落とす。
     *
     * @param from764 内部が764の場合 true。false の場合は内部765
     */
    private Outcome handleServerConfiguration767(MinecraftPacket packet, boolean from764) {
        return switch (packet.id()) {
            case 0x00 -> Outcome.toClient(new MinecraftPacket(0x01, packet.payload())); // Plugin Message → Custom Payload
            case 0x01 -> { // Disconnect（764は JSON 文字列、765／766は NBT）
                byte[] payload = from764
                        ? PlayTranslator.translateDisconnect764To765(packet.payload())
                        : packet.payload();
                yield closeWithPacket(new MinecraftPacket(0x02, payload));
            }
            case 0x02 -> // Finish Configuration
                    Outcome.toClient(new MinecraftPacket(0x03, new byte[0]));
            case 0x03 -> Outcome.toClient(new MinecraftPacket(0x04, packet.payload())); // Keep Alive
            case 0x04 -> Outcome.toClient(new MinecraftPacket(0x05, packet.payload())); // Ping
            case 0x05 -> { // Registry Data：764／765の codec を767のエントリ配列に変換する
                Map<String, Nbt.Tag.CompoundTag> parsed =
                        PlayTranslator.parseRegistryData(packet.payload());
                registries.putAll(parsed);
                List<MinecraftPacket> toClient = new ArrayList<>();
                for (byte[] registryPayload
                        : PlayTranslator.translateRegistryData765To767(packet.payload())) {
                    toClient.add(new MinecraftPacket(0x07, registryPayload));
                }
                yield new Outcome(List.of(), toClient, false, false);
            }
            case 0x06 -> { // 764の Add Resource Pack（UUIDなし）／765の Remove Resource Pack
                if (from764) {
                    PendingPack pack = parseResourcePack764(packet.payload());
                    lastSentPack = pack;
                    yield Outcome.toClient(new MinecraftPacket(0x09,
                            PlayTranslator.translateConfigAddResourcePack764To765(
                                    pack, packOverrideForClient())));
                }
                yield Outcome.toClient(new MinecraftPacket(0x08, packet.payload()));
            }
            case 0x07 -> { // 764の Feature Flags／765の Add Resource Pack（UUID付き）
                if (from764) {
                    yield Outcome.toClient(new MinecraftPacket(0x0C, packet.payload()));
                }
                PendingPack pack = PlayTranslator.parseConfigAddResourcePack(packet.payload());
                lastSentPack = pack;
                yield Outcome.toClient(new MinecraftPacket(0x09,
                        PlayTranslator.translateConfigAddResourcePack764To765(
                                pack, packOverrideForClient())));
            }
            case 0x08 -> { // 764の Tags／765の Feature Flags
                if (from764) {
                    LOG.info(() -> "レジストリ番号がずれるため Tags を767向けに落とします（" + playerName + "）。");
                    yield Outcome.drop();
                }
                yield Outcome.toClient(new MinecraftPacket(0x0C, packet.payload()));
            }
            case 0x09 -> { // 765の Tags：レジストリ番号がずれるため落とす（764には無い番号）
                LOG.info(() -> "レジストリ番号がずれるため Tags を767向けに落とします（" + playerName + "）。");
                yield Outcome.drop();
            }
            default -> {
                LOG.warning((from764 ? "764" : "765") + "の設定フェーズで想定外のパケット（id=0x"
                        + Integer.toHexString(packet.id()) + "）を落とします。");
                yield Outcome.drop();
            }
        };
    }

    /**
     * 766クライアント＋内部767：設定フェーズを中継する。
     * 766と767の設定フェーズの番号は同じため、ほとんど素通しする。
     * Registry Data だけ767→766へ並べ替え（jukebox_song は落とす）、
     * Tags はレジストリ番号がずれるため落とし、Custom Report Details
     * （0x0F）／Server Links（0x10）は766に無いため落とす。
     * Cookie Request には空の Cookie Response を、Select Known Packs には
     * 空の一覧をプロキシがクライアント役として返す。
     */
    private Outcome handleServerConfiguration766From767(MinecraftPacket packet) {
        return switch (packet.id()) {
            case 0x00 -> { // Cookie Request：空の Cookie Response を返す
                String key = readCookieKey(packet.payload());
                yield Outcome.toServer(new MinecraftPacket(0x01,
                        emptyCookieResponsePayload(key)));
            }
            case 0x01 -> Outcome.toClient(new MinecraftPacket(0x00, packet.payload())); // Custom Payload → Plugin Message
            case 0x02 -> // Disconnect（767は NBT。766も NBT）
                    Outcome.toClient(packet);
            case 0x03 -> // Finish Configuration
                    Outcome.toClient(packet);
            case 0x04 -> Outcome.toClient(packet); // Keep Alive（同ID）
            case 0x05 -> Outcome.toClient(packet); // Ping（同ID）
            case 0x06 -> Outcome.toClient(packet); // Reset Chat（同ID）
            case 0x07 -> { // Registry Data：767のエントリ配列を766の順序に並べ替える
                Map<String, Nbt.Tag.CompoundTag> parsed =
                        PlayTranslator.parseRegistryData(packet.payload());
                registries.putAll(parsed);
                byte[] payload767to766 =
                        PlayTranslator.translateRegistryData767To766(packet.payload());
                if (payload767to766 == null) {
                    // jukebox_song：766に存在しないため落とす。
                    yield Outcome.drop();
                }
                yield Outcome.toClient(new MinecraftPacket(0x07, payload767to766));
            }
            case 0x08 -> Outcome.toClient(packet); // Remove Resource Pack（同ID）
            case 0x09 -> { // Add Resource Pack（767：UUID付き）
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
                LOG.info(() -> "レジストリ番号がずれるため Tags を766向けに落とします（" + playerName + "）。");
                yield Outcome.drop();
            }
            case 0x0E -> // Select Known Packs：空の一覧を返す
                    Outcome.toServer(new MinecraftPacket(0x07, emptySelectKnownPacksPayload()));
            case 0x0F, 0x10 -> // Custom Report Details／Server Links（766に無い）
                    Outcome.drop();
            default -> {
                LOG.warning("767の設定フェーズで想定外のパケット（id=0x"
                        + Integer.toHexString(packet.id()) + "）を落とします。");
                yield Outcome.drop();
            }
        };
    }
"""
    text = insert_once(text,
        "    /**\n"
        "     * 766クライアント＋内部764／765：設定フェーズを中継する。",
        server_config_767)

    # I-3. handleServerConfigurationFrom767（765From764 の javadoc の直前）
    server_config_from767 = """
    /**
     * 764／765クライアント＋内部767：設定フェーズを中継する。
     * 番号の付け替え（Custom Payload 0x01→Plugin Message 0x00、
     * Disconnect 0x02→0x01、Finish 0x03→0x02、Keep Alive 0x04→0x03、
     * Ping 0x05→0x04）と、Registry Data の形式変換（767のエントリ配列
     * →764／765の1個の codec NBT）だけを行う。
     * Cookie Request には空の Cookie Response を、Select Known Packs には
     * 空の一覧をプロキシがクライアント役として返す。Reset Chat・
     * Store Cookie・Transfer は764／765に相当が無いため落とす
     * （Transfer は送る先が無い）。Tags はレジストリ番号がずれるため落とす。
     * Custom Report Details（0x0F）／Server Links（0x10）は764／765に
     * 無いため落とす。jukebox_song の Registry Data も766／765に無いため
     * 落とす。Disconnect は767の NBT を764向けは JSON 文字列に変換する
     * （765は NBT のまま）。
     *
     * @param to764 クライアントが764の場合 true。false の場合は765
     */
    private Outcome handleServerConfigurationFrom767(MinecraftPacket packet, boolean to764) {
        return switch (packet.id()) {
            case 0x00 -> { // Cookie Request：空の Cookie Response を返す
                String key = readCookieKey(packet.payload());
                yield Outcome.toServer(new MinecraftPacket(0x01,
                        emptyCookieResponsePayload(key)));
            }
            case 0x01 -> Outcome.toClient(new MinecraftPacket(0x00, packet.payload())); // Custom Payload → Plugin Message
            case 0x02 -> { // Disconnect（767は NBT。764は JSON 文字列）
                byte[] payload = to764
                        ? PlayTranslator.translateDisconnect765To763(packet.payload())
                        : packet.payload();
                yield closeWithPacket(new MinecraftPacket(0x01, payload));
            }
            case 0x03 -> // Finish Configuration
                    Outcome.toClient(new MinecraftPacket(0x02, new byte[0]));
            case 0x04 -> Outcome.toClient(new MinecraftPacket(0x03, packet.payload())); // Keep Alive
            case 0x05 -> Outcome.toClient(new MinecraftPacket(0x04, packet.payload())); // Ping
            case 0x06 -> Outcome.drop(); // Reset Chat（764／765に相当無し）
            case 0x07 -> { // Registry Data：767のエントリ配列を764／765の codec に変換する
                Map<String, Nbt.Tag.CompoundTag> parsed =
                        PlayTranslator.parseRegistryData(packet.payload());
                registries.putAll(parsed);
                byte[] codec = PlayTranslator.translateRegistryData767To765(packet.payload());
                if (codec == null) {
                    // jukebox_song：764／765に存在しないため落とす。
                    yield Outcome.drop();
                }
                yield Outcome.toClient(new MinecraftPacket(0x05, codec));
            }
            case 0x08 -> { // Remove Resource Pack：765だけに送る（764に相当無し）
                if (to764) {
                    LOG.info(() -> "764に無い Remove Resource Pack を落とします（" + playerName + "）。");
                    yield Outcome.drop();
                }
                yield Outcome.toClient(new MinecraftPacket(0x06, packet.payload()));
            }
            case 0x09 -> { // Add Resource Pack（767：UUID付き）
                PendingPack pack = PlayTranslator.parseConfigAddResourcePack(packet.payload());
                lastSentPack = pack;
                if (to764) {
                    yield Outcome.toClient(new MinecraftPacket(0x06,
                            PlayTranslator.buildConfigResourcePack764(pack, pack764)));
                }
                yield Outcome.toClient(new MinecraftPacket(0x07,
                        PlayTranslator.translateConfigAddResourcePack764To765(
                                pack, pack765)));
            }
            case 0x0A -> Outcome.drop(); // Store Cookie（764／765に相当無し）
            case 0x0B -> { // Transfer：764／765クライアントに送る先が無いため落とす
                LOG.info(() -> (to764 ? "764" : "765")
                        + "クライアントに送れない Transfer を落とします（" + playerName + "）。");
                yield Outcome.drop();
            }
            case 0x0C -> // Feature Flags
                    Outcome.toClient(new MinecraftPacket(to764 ? 0x07 : 0x08, packet.payload()));
            case 0x0D -> { // Tags：レジストリ番号がずれるため落とす
                LOG.info(() -> "レジストリ番号がずれるため Tags を"
                        + (to764 ? "764" : "765") + "向けに落とします（" + playerName + "）。");
                yield Outcome.drop();
            }
            case 0x0E -> // Select Known Packs：空の一覧を返す
                    Outcome.toServer(new MinecraftPacket(0x07, emptySelectKnownPacksPayload()));
            case 0x0F, 0x10 -> // Custom Report Details／Server Links（764／765に無い）
                    Outcome.drop();
            default -> {
                LOG.warning("767の設定フェーズで想定外のパケット（id=0x"
                        + Integer.toHexString(packet.id()) + "）を落とします。");
                yield Outcome.drop();
            }
        };
    }
"""
    text = insert_once(text,
        "    /**\n"
        "     * 765クライアント＋内部764：設定フェーズを下向き中継する。",
        server_config_from767)

    # --- J. handleServerPlay の振り分け（内部767） ---
    text = replace_once(text,
        "        // 内部766：Join Game は 0x2B、Spawn Position は 0x56、\n"
        "        // Start Configuration は 0x69。\n"
        "        if (isServer766()) {\n"
        "            return handleServerPlayFrom766(packet);\n"
        "        }",
        "        // 内部767：Join Game は 0x2B、Spawn Position は 0x56、\n"
        "        // Start Configuration は 0x69（766と同じ）。\n"
        "        if (isServer767()) {\n"
        "            return handleServerPlayFrom767(packet);\n"
        "        }\n"
        "        // 内部766：Join Game は 0x2B、Spawn Position は 0x56、\n"
        "        // Start Configuration は 0x69。\n"
        "        if (isServer766()) {\n"
        "            return handleServerPlayFrom766(packet);\n"
        "        }")
    text = replace_once(text,
        "        if (isServer764()) {\n"
        "            if (is763()) {\n"
        "                return handleServerPlay763From764(packet);\n"
        "            }\n"
        "            if (is766()) {\n"
        "                return handleServerPlay766From764(packet);\n"
        "            }\n"
        "            return handleServerPlay765From764(packet);\n"
        "        }",
        "        if (isServer764()) {\n"
        "            if (is763()) {\n"
        "                return handleServerPlay763From764(packet);\n"
        "            }\n"
        "            if (is766()) {\n"
        "                return handleServerPlay766From764(packet);\n"
        "            }\n"
        "            if (is767()) {\n"
        "                return handleServerPlay767From764(packet);\n"
        "            }\n"
        "            return handleServerPlay765From764(packet);\n"
        "        }")

    # --- K. startConfiguration／spawnPosition／keepAlive／ping の767フォールバック ---
    for method, fallback in [
        ("startConfigurationClientId", "0x69"),
        ("spawnPositionClientId", "0x56"),
        ("keepAliveClientId", "0x26"),
        ("pingClientId", "0x35"),
    ]:
        text = replace_once(text,
            f"    private int {method}() {{\n        if (is766()) {{",
            f"    private int {method}() {{\n        if (is766() || is767()) {{")

    # --- L. handleServerPlayFrom766：Respawn の767素通し＋フォールバックで766→767変換 ---
    text = replace_once(text,
        "        // Respawn（766：0x47）。\n"
        "        if (packet.id() == 0x47) {\n"
        "            byte[] payload;",
        "        // Respawn（766：0x47）。\n"
        "        if (packet.id() == 0x47) {\n"
        "            if (is767()) {\n"
        "                // 767クライアント：Respawn は766と同一形式のためそのまま通す。\n"
        "                return Outcome.toClient(new MinecraftPacket(0x47, packet.payload()));\n"
        "            }\n"
        "            byte[] payload;")
    text = replace_once(text,
        "        // それ以外は落とす（766のプレイ状態の変換は別担当の対応表待ち）。\n"
        "        PacketName name = PacketIds.nameFor(packet.id(), State.PLAY,\n"
        "                Direction.CLIENTBOUND, 766).orElse(null);",
        "        // 767クライアント：スロット等は766→767へ変換する。\n"
        "        if (is767()) {\n"
        "            return handleServerPlay766To767(packet);\n"
        "        }\n"
        "        // それ以外は落とす（766のプレイ状態の変換は別担当の対応表待ち）。\n"
        "        PacketName name = PacketIds.nameFor(packet.id(), State.PLAY,\n"
        "                Direction.CLIENTBOUND, 766).orElse(null);")

    # --- M. handleServerJoinGame766 の767分岐 ---
    text = replace_once(text,
        "        byte[] payload = is764()\n"
        "                ? PlayTranslator.translateJoinGame766To764(join, registries)\n"
        "                : PlayTranslator.translateJoinGame766To765(join, registries);\n"
        "        toClient.add(new MinecraftPacket(0x29, payload));",
        "        if (is767()) {\n"
        "            // 767クライアント：Join Game は766と同一形式のためそのまま通す。\n"
        "            toClient.add(new MinecraftPacket(0x2B, packet.payload()));\n"
        "            advanceFlowSafely(() -> joinFlow.joinGameReceived());\n"
        "            while (!pendingPacks.isEmpty()) {\n"
        "                PendingPack pack = pendingPacks.removeFirst();\n"
        "                lastSentPack = pack;\n"
        "                toClient.add(new MinecraftPacket(0x09,\n"
        "                        PlayTranslator.translateConfigAddResourcePack764To765(\n"
        "                                pack, packOverrideForClient())));\n"
        "            }\n"
        "            return new Outcome(List.of(), toClient, false, false);\n"
        "        }\n"
        "        byte[] payload = is764()\n"
        "                ? PlayTranslator.translateJoinGame766To764(join, registries)\n"
        "                : PlayTranslator.translateJoinGame766To765(join, registries);\n"
        "        toClient.add(new MinecraftPacket(0x29, payload));")

    # --- N. handleServerJoinGame の767分岐 ---
    text = replace_once(text,
        "    private Outcome handleServerJoinGame(MinecraftPacket packet) {\n"
        "        if (is766()) {",
        "    private Outcome handleServerJoinGame(MinecraftPacket packet) {\n"
        "        if (is767()) {\n"
        "            // 767クライアント：dimension を文字列からレジストリ番号にする。\n"
        "            // 764／765の Join Game は同形式のため、どちらも同じ変換でよい。\n"
        "            advanceFlowSafely(() -> joinFlow.joinGameReceived());\n"
        "            List<MinecraftPacket> toClient = new ArrayList<>();\n"
        "            toClient.add(new MinecraftPacket(0x2B,\n"
        "                    PlayTranslator.translateJoinGame765To767(\n"
        "                            PlayTranslator.parseJoinGame765(packet.payload()), registries)));\n"
        "            while (!pendingPacks.isEmpty()) {\n"
        "                PendingPack pack = pendingPacks.removeFirst();\n"
        "                lastSentPack = pack;\n"
        "                toClient.add(new MinecraftPacket(0x09,\n"
        "                        PlayTranslator.translateConfigAddResourcePack764To765(\n"
        "                                pack, packOverrideForClient())));\n"
        "            }\n"
        "            return new Outcome(List.of(), toClient, false, false);\n"
        "        }\n"
        "        if (is766()) {")

    # --- O. handleServerPlayUp の767拡張 ---
    text = replace_once(text,
        "        // 766クライアント：Keep Alive／Ping／Disconnect／Respawn／\n"
        "        // Add Resource Pack だけ変換する。それ以外のプレイ状態のパケットは\n"
        "        // 766の対応表・レジストリ表（別担当の作業中）が要るため落とす。\n"
        "        // （Keep Alive／Ping を落とすとサーバーがタイムアウトで切断する\n"
        "        // ため、接続維持のためだけは中継する。）\n"
        "        if (is766()) {",
        "        // 766／767クライアント：Keep Alive／Ping／Disconnect／Respawn／\n"
        "        // Add Resource Pack だけ変換する。それ以外のプレイ状態のパケットは\n"
        "        // 766の対応表・レジストリ表（別担当の作業中）が要るため落とす。\n"
        "        // （Keep Alive／Ping を落とすとサーバーがタイムアウトで切断する\n"
        "        // ため、接続維持のためだけは中継する。）\n"
        "        if (is766() || is767()) {")
    text = replace_once(text,
        "            if (name766 == PacketName.PLAY_RESPAWN) {\n"
        "                byte[] payload = PlayTranslator.translateRespawn765To766(\n"
        "                        packet.payload(), registries);",
        "            if (name766 == PacketName.PLAY_RESPAWN) {\n"
        "                byte[] payload = is767()\n"
        "                        ? PlayTranslator.translateRespawn765To767(\n"
        "                                packet.payload(), registries)\n"
        "                        : PlayTranslator.translateRespawn765To766(\n"
        "                                packet.payload(), registries);")

    # --- P. handleServerPlayFrom763 の767分岐 ---
    text = replace_once(text,
        "            PendingPack pack = parseResourcePack764(packet.payload());\n"
        "            lastSentPack = pack;\n"
        "            if (is766()) {\n"
        "                return Outcome.toClient(new MinecraftPacket(0x46,\n"
        "                        PlayTranslator.translateConfigAddResourcePack764To765(\n"
        "                                pack, packOverrideForClient())));\n"
        "            }\n"
        "            if (is764()) {",
        "            PendingPack pack = parseResourcePack764(packet.payload());\n"
        "            lastSentPack = pack;\n"
        "            if (is766() || is767()) {\n"
        "                return Outcome.toClient(new MinecraftPacket(0x46,\n"
        "                        PlayTranslator.translateConfigAddResourcePack764To765(\n"
        "                                pack, packOverrideForClient())));\n"
        "            }\n"
        "            if (is764()) {")
    text = replace_once(text,
        "        // Disconnect（763：JSON 文字列）。\n"
        "        if (name == PacketName.PLAY_DISCONNECT) {\n"
        "            closed = true;\n"
        "            if (is766()) {",
        "        // Disconnect（763：JSON 文字列）。\n"
        "        if (name == PacketName.PLAY_DISCONNECT) {\n"
        "            closed = true;\n"
        "            if (is766() || is767()) {")
    text = replace_once(text,
        "        // Declare Recipes／Tags：763形式のままでは764／765／766に送れないため落とす。",
        "        // Declare Recipes／Tags：763形式のままでは764／765／766／767に送れないため落とす。")
    text = replace_once(text,
        "        if (name == PacketName.PLAY_RESPAWN) {\n"
        "            if (is766()) {\n"
        "                logUntranslatedOnce(name);\n"
        "                return Outcome.drop();\n"
        "            }\n"
        "            byte[] payload = is764()\n"
        "                    ? ExtraPlayTranslator.translateRespawn763To764(packet.payload())",
        "        if (name == PacketName.PLAY_RESPAWN) {\n"
        "            if (is766() || is767()) {\n"
        "                logUntranslatedOnce(name);\n"
        "                return Outcome.drop();\n"
        "            }\n"
        "            byte[] payload = is764()\n"
        "                    ? ExtraPlayTranslator.translateRespawn763To764(packet.payload())")
    text = replace_once(text,
        "        if (name == PacketName.PLAY_ADVANCEMENTS) {\n"
        "            if (is766()) {\n"
        "                logUntranslatedOnce(name);\n"
        "                return Outcome.drop();\n"
        "            }",
        "        if (name == PacketName.PLAY_ADVANCEMENTS) {\n"
        "            if (is766() || is767()) {\n"
        "                logUntranslatedOnce(name);\n"
        "                return Outcome.drop();\n"
        "            }")
    text = replace_once(text,
        "        // 766向け：チャット・表示系は763が JSON 文字列・766が NBT のため、\n"
        "        // JSON のまま送ると誤読される。チャンク・ブロック状態・スロット・\n"
        "        // エンティティも766の変換が要る。Join Game／Spawn Position／\n"
        "        // Keep Alive／Ping／Respawn 以外は未対応のため落とす\n"
        "        // （766のプレイ状態の変換は別担当の対応表待ち）。\n"
        "        if (is766()) {",
        "        // 766／767向け：チャット・表示系は763が JSON 文字列・766が NBT のため、\n"
        "        // JSON のまま送ると誤読される。チャンク・ブロック状態・スロット・\n"
        "        // エンティティも766の変換が要る。Join Game／Spawn Position／\n"
        "        // Keep Alive／Ping／Respawn 以外は未対応のため落とす\n"
        "        // （766のプレイ状態の変換は別担当の対応表待ち）。\n"
        "        if (is766() || is767()) {")

    # --- Q. 合成した設定フェーズの767対応 ---
    text = replace_once(text,
        "        if (is766()) {\n"
        "            // 766：レジストリごとに1パケット（エントリ配列）。",
        "        if (is766() || is767()) {\n"
        "            // 766／767：レジストリごとに1パケット（エントリ配列）。")
    text = replace_once(text,
        "        int ackId = is766() ? 0x03 : 0x02;",
        "        int ackId = is766() || is767() ? 0x03 : 0x02;")
    text = replace_once(text,
        "            if (is766()) {\n"
        "                toClient.add(new MinecraftPacket(0x09,\n"
        "                        PlayTranslator.translateConfigAddResourcePack764To765(\n"
        "                                pack, packOverrideForClient())));\n"
        "            } else if (is764()) {",
        "            if (is766() || is767()) {\n"
        "                toClient.add(new MinecraftPacket(0x09,\n"
        "                        PlayTranslator.translateConfigAddResourcePack764To765(\n"
        "                                pack, packOverrideForClient())));\n"
        "            } else if (is764()) {")

    # --- R. 新規プレイ変換メソッド（handleServerPlayFrom766 の直前） ---
    play_methods = """
    /**
     * 内部767→クライアントのプレイ状態（サーバー→クライアント）。
     * 767のプレイ状態は766と同一形式のため、Start Configuration／
     * Join Game／Spawn Position／Keep Alive／Ping／Disconnect／Respawn は
     * {@link #handleServerPlayFrom766} に委譲する。767で追加された
     * Custom Report Details（0x7A）／Server Links（0x7B）は旧クライアントに
     * 無いため落とす。
     */
    private Outcome handleServerPlayFrom767(MinecraftPacket packet) {
        if (packet.id() == 0x7A || packet.id() == 0x7B) {
            logUntranslatedOnce(packet.id() == 0x7A
                    ? "PLAY_CUSTOM_REPORT_DETAILS" : "PLAY_SERVER_LINKS");
            return Outcome.drop();
        }
        return handleServerPlayFrom766(packet);
    }

    /**
     * 内部766→767クライアントのプレイ状態（サーバー→クライアント）。
     * スロットを含むパケットは766→767へ変換する（同ID）。
     * チャンク・ブロック変更・ブロックエンティティはブロック状態・
     * バイオーム・ブロックエンティティ種別が766と767で同一のため素通しする。
     * Declare Recipes／Statistics／Tags／World Event ID／Advancements などは
     * レジストリ番号・スロット変換が要るため落とす（766向けと同じ方針）。
     * Join Game／Spawn Position／Keep Alive／Ping／Disconnect／Respawn／
     * Start Configuration はここに来る前に処理済みである。
     */
    private Outcome handleServerPlay766To767(MinecraftPacket packet) {
        PacketName name = PacketIds.nameFor(packet.id(), State.PLAY,
                Direction.CLIENTBOUND, 766).orElse(null);
        if (name == null) {
            logUntranslatedOnce("PLAY_UNKNOWN_0x" + Integer.toHexString(packet.id()));
            return Outcome.drop();
        }
        byte[] translated = switch (name) {
            case PLAY_ENTITY_METADATA -> ChunkTranslator.translateEntityMetadata766To767(
                    packet.payload());
            case PLAY_SET_SLOT -> ChunkTranslator.translateSetSlot766To767(packet.payload());
            case PLAY_WINDOW_ITEMS -> ChunkTranslator.translateWindowItems766To767(
                    packet.payload());
            case PLAY_SET_COOLDOWN -> ChunkTranslator.translateSetCooldown766To767(
                    packet.payload());
            case PLAY_ENTITY_EQUIPMENT -> ChunkTranslator.translateEntityEquipment766To767(
                    packet.payload());
            case PLAY_TRADE_LIST -> ChunkTranslator.translateTradeList766To767(
                    packet.payload());
            case PLAY_OPEN_WINDOW -> ChunkTranslator.translateOpenWindow766To767(
                    packet.payload());
            case PLAY_BLOCK_ACTION -> ChunkTranslator.translateBlockAction766To767(
                    packet.payload());
            case PLAY_SPAWN_ENTITY -> ChunkTranslator.translateSpawnEntity766To767(
                    packet.payload());
            case PLAY_SOUND_EFFECT -> ChunkTranslator.translateSoundEffect766To767(
                    packet.payload());
            case PLAY_ENTITY_SOUND_EFFECT -> ChunkTranslator.translateEntitySoundEffect766To767(
                    packet.payload());
            case PLAY_STOP_SOUND -> ChunkTranslator.translateStopSound766To767(
                    packet.payload());
            case PLAY_WORLD_PARTICLES -> ChunkTranslator.translateWorldParticles766To767(
                    packet.payload());
            case PLAY_ENTITY_EFFECT -> ChunkTranslator.translateEntityEffect766To767(
                    packet.payload());
            case PLAY_REMOVE_ENTITY_EFFECT -> ChunkTranslator.translateRemoveEntityEffect766To767(
                    packet.payload());
            case PLAY_DAMAGE_EVENT -> ChunkTranslator.translateDamageEvent766To767(
                    packet.payload());
            case PLAY_ENTITY_UPDATE_ATTRIBUTES -> ChunkTranslator
                    .translateUpdateAttributes766To767(packet.payload());
            case PLAY_CHUNK_DATA, PLAY_BLOCK_CHANGE, PLAY_MULTI_BLOCK_CHANGE,
                    PLAY_BLOCK_ENTITY_DATA -> packet.payload();
            default -> null;
        };
        if (translated == null) {
            logUntranslatedOnce(name);
            return Outcome.drop();
        }
        return Outcome.toClient(new MinecraftPacket(packet.id(), translated));
    }

    /**
     * 内部764→767クライアントのプレイ状態（サーバー→クライアント）。
     * Join Game／Spawn Position／Start Configuration はここに来る前に
     * 処理済みである。Keep Alive／Ping／Disconnect／Respawn／
     * Add Resource Pack だけ変換し、それ以外は落とす（766クライアント向けの
     * handleServerPlayUp と同じ join レベルの方針）。
     */
    private Outcome handleServerPlay767From764(MinecraftPacket packet) {
        PacketName name = PacketIds.nameFor(packet.id(), State.PLAY,
                Direction.CLIENTBOUND, 764).orElse(null);
        if (name == null) {
            logUntranslatedOnce("PLAY_UNKNOWN_0x" + Integer.toHexString(packet.id()));
            return Outcome.drop();
        }
        return switch (name) {
            case PLAY_KEEP_ALIVE -> Outcome.toClient(new MinecraftPacket(0x26, packet.payload()));
            case PLAY_PING -> Outcome.toClient(new MinecraftPacket(0x35, packet.payload()));
            case PLAY_DISCONNECT -> {
                closed = true;
                byte[] payload = PlayTranslator.translateDisconnect764To765(packet.payload());
                yield new Outcome(List.of(),
                        List.of(new MinecraftPacket(0x1D, payload)), false, true);
            }
            case PLAY_RESPAWN -> {
                byte[] payload = PlayTranslator.translateRespawn764To767(
                        packet.payload(), registries);
                yield Outcome.toClient(new MinecraftPacket(0x47, payload));
            }
            case PLAY_ADD_RESOURCE_PACK -> {
                PendingPack pack = PlayTranslator.parseConfigAddResourcePack(packet.payload());
                lastSentPack = pack;
                yield Outcome.toClient(new MinecraftPacket(0x46,
                        PlayTranslator.translateConfigAddResourcePack764To765(
                                pack, packOverrideForClient())));
            }
            default -> {
                logUntranslatedOnce(name);
                yield Outcome.drop();
            }
        };
    }
"""
    text = insert_once(text,
        "    /**\n"
        "     * 内部766→クライアントのプレイ状態（サーバー→クライアント）。",
        play_methods)

    PATH.write_text(text, encoding="utf-8")
    print("ProxySession.java へ767を追加しました。")


if __name__ == "__main__":
    main()
