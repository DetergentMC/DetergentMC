#!/usr/bin/env python3
"""485（1.14.2）のテストから 480（1.14.1）のテストを生成する。

480 と 485 は Stage A の実データ照合で差分0（パケットID・レジストリ・
形式すべて一致）と証明済みのため、テストは 485 のものを土台に、
「485 と同一であること自体」を固定する形に読み替える。

使い方: python3 tools/gen-480-tests.py
"""
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
TEST = ROOT / "src/test/java/io/detergentmc/protocol"
BTEST = ROOT.parent / "detergentmc-bedrock/src/test/java/io/detergentmc/bedrock"


def load(name, base=TEST):
    return (base / name).read_text(encoding="utf-8")


def save(name, text, base=TEST):
    (base / name).write_text(text, encoding="utf-8")
    print(f"{name} を生成した")


def rep(text, old, new, count, where):
    found = text.count(old)
    if found != count:
        print(f"NG[{where}]: {found} 件（期待 {count}）: {old[:70]!r}")
        sys.exit(1)
    return text.replace(old, new)


def client_adapter():
    t = load("ClientAdapter485Test.java")
    t = rep(t, "485（1.14.2）クライアントの受け入れ判断。このツリーで最古の対応プロトコル。",
            "480（1.14.1）クライアントの受け入れ判断。このツリーで最古の対応プロトコル。", 1, "ca-head")
    t = rep(t, "class ClientAdapter485Test", "class ClientAdapter480Test", 1, "ca-class")
    t = rep(t, "void decide485IsAcceptedViaAdapter() {\n        Handshake handshake = ClientAdapter.decide(485);",
            "void decide480IsAcceptedViaAdapter() {\n        Handshake handshake = ClientAdapter.decide(480);", 1, "ca-1")
    t = rep(t, "assertEquals(485, handshake.clientProtocol());\n        assertTrue(handshake.message().contains(\"485\"), handshake.message());",
            "assertEquals(480, handshake.clientProtocol());\n        assertTrue(handshake.message().contains(\"480\"), handshake.message());", 1, "ca-2")
    t = rep(t, "void decide485AgainstAllServerProtocols() {\n        for (int server : new int[]{485, 490,",
            "void decide480AgainstAllServerProtocols() {\n        for (int server : new int[]{480, 485, 490,", 1, "ca-3")
    t = rep(t, "Handshake handshake = ClientAdapter.decide(485, server);",
            "Handshake handshake = ClientAdapter.decide(480, server);", 1, "ca-4")
    t = rep(t, "if (server == 485) {", "if (server == 480) {", 1, "ca-5")
    t = rep(t, "        // 777より新しい／485より古い／間の未対応帯は断る。\n"
               "        assertFalse(ClientAdapter.decide(777, 485).accepted());\n"
               "        assertFalse(ClientAdapter.decide(574, 485).accepted());\n"
               "        assertFalse(ClientAdapter.decide(489, 485).accepted());\n"
               "        assertFalse(ClientAdapter.decide(484, 485).accepted());\n"
               "        assertFalse(ClientAdapter.decide(340, 485).accepted());",
            "        // 777より新しい／480より古い／間の未対応帯は断る。\n"
               "        assertFalse(ClientAdapter.decide(777, 480).accepted());\n"
               "        assertFalse(ClientAdapter.decide(574, 480).accepted());\n"
               "        assertFalse(ClientAdapter.decide(489, 480).accepted());\n"
               "        assertFalse(ClientAdapter.decide(484, 480).accepted());\n"
               "        assertFalse(ClientAdapter.decide(479, 480).accepted());\n"
               "        assertFalse(ClientAdapter.decide(340, 480).accepted());", 1, "ca-6")
    t = rep(t, "void versionName485Is1142() {\n        assertEquals(\"1.14.2\", ClientAdapter.versionName(485));",
            "void versionName480Is1141() {\n        assertEquals(\"1.14.1\", ClientAdapter.versionName(480));", 1, "ca-7")
    t = rep(t, "    void oldestSupportedIs485() {\n"
               "        assertEquals(485, ClientAdapter.OLDEST_SUPPORTED_CLIENT_PROTOCOL);\n"
               "        List<Integer> supported = ClientAdapter.supportedClientProtocols();\n"
               "        assertEquals(485, supported.get(0), supported.toString());\n"
               "        assertEquals(490, supported.get(1), supported.toString());\n"
               "        assertEquals(498, supported.get(2), supported.toString());\n"
               "        assertEquals(25, supported.size(), supported.toString());",
            "    void oldestSupportedIs480() {\n"
               "        assertEquals(480, ClientAdapter.OLDEST_SUPPORTED_CLIENT_PROTOCOL);\n"
               "        List<Integer> supported = ClientAdapter.supportedClientProtocols();\n"
               "        assertEquals(480, supported.get(0), supported.toString());\n"
               "        assertEquals(485, supported.get(1), supported.toString());\n"
               "        assertEquals(490, supported.get(2), supported.toString());\n"
               "        assertEquals(26, supported.size(), supported.toString());", 1, "ca-8")
    t = rep(t, "void differencesFor485Exist() {\n        assertFalse(ClientAdapter.differences(485).isEmpty());",
            "void differencesFor480Exist() {\n        assertFalse(ClientAdapter.differences(480).isEmpty());", 1, "ca-9")
    save("ClientAdapter480Test.java", t)


def client_join_flow():
    t = load("ClientJoinFlow485Test.java")
    t = t.replace("485", "480").replace("1.14.2", "1.14.1")
    t = rep(t, "assertThrows(IllegalArgumentException.class, () -> new ClientJoinFlow(489));",
            "assertThrows(IllegalArgumentException.class, () -> new ClientJoinFlow(479));\n"
            "        assertThrows(IllegalArgumentException.class, () -> new ClientJoinFlow(489));", 1, "cjf")
    save("ClientJoinFlow480Test.java", t)


def registry_remapper():
    t = load("RegistryRemapper485Test.java")
    # まず対象（485→480）を置換し、そのあと参照先（490→485）をずらす。
    t = t.replace("485", "480")
    for ident in ("MAX_BLOCK_STATE_490", "MAX_ITEM_490", "MAX_ENTITY_TYPE_490",
                  "MAX_SOUND_490", "MAX_PARTICLE_490", "MAX_BLOCK_490", "MAX_MENU_490",
                  "MAX_BLOCK_ENTITY_490", "MAX_BIOME_490",
                  "mapBlockState490To758", "mapBlockState758To490",
                  "mapItem490To758", "mapItem758To490",
                  "mapEntityType490To758", "mapEntityType758To490",
                  "mapBiome490To758", "mapBiome758To490",
                  "mapMenu490To758", "mapMenu758To490",
                  "mapBlockEntityType490To758", "mapBlockEntityType758To490",
                  "mapParticle490To758", "mapParticle758To490",
                  "biomeNames490()", "mapSoundName490To758", "mapSoundName758To490"):
        t = t.replace(ident, ident.replace("490", "485"))
    t = rep(t, "void maxConstantsEqual490()", "void maxConstantsEqual485()", 1, "rr-c2")
    t = rep(t, "void blockStateMapsEqual490OverFullRange()",
            "void blockStateMapsEqual485OverFullRange()", 1, "rr-c3")
    t = rep(t, "void itemEntityBiomeMenuMapsEqual490OverFullRange()",
            "void itemEntityBiomeMenuMapsEqual485OverFullRange()", 1, "rr-c4")
    t = rep(t, "void namesEqual490()", "void namesEqual485()", 1, "rr-c5")
    t = rep(t, "// 実数も固定する（1.14.3 と同じ）。", "// 実数も固定する（1.14.2 と同じ）。", 1, "rr-c6")
    # ヘッダーは 480 用に書き直す（SHA-1 は実測値）。
    head_start = t.index("/**")
    head_end = t.index("*/") + 2
    header = ("/**\n"
              " * {@link RegistryRemapper} の480（1.14.1）⇔758 変換チェック。\n"
              " * Stage A で実バニラ 1.14.1 jar（SHA-1\n"
              " * ed76d597a44c5266be2a7fcd77a8270f1f0bc118）と 1.14.2 jar の\n"
              " * data-generator ダンプを突き合わせ、ワイヤに関わる全レジストリ\n"
              " * （ブロック状態・ブロック・アイテム・エンティティ・音・\n"
              " * バイオーム等）がバイト単位で一致することを証明済み。そのため\n"
              " * 480 の写像は 485 の表へ委譲し、ここでは全範囲で 485 と一致する\n"
              " * ことを固定する。\n"
              " */")
    t = t[:head_start] + header + t[head_end:]
    save("RegistryRemapper480Test.java", t)


def play_translator():
    t = load("PlayTranslator485Test.java")
    t = t.replace("485", "480").replace("1.14.2", "1.14.1")
    t = rep(t, "Respawn のレイアウトは 490 と完全一致することを確認済みのため、",
            "Respawn のレイアウトは 485 と完全一致することを確認済みのため、", 1, "pt")
    save("PlayTranslator480Test.java", t)


def proxy_session():
    t = load("ProxySession485Test.java")
    head_start = t.index("/**")
    head_end = t.index("*/") + 2
    header = ("/**\n"
              " * 480（1.14.1）クライアント・サーバーのプロキシ統合テスト。\n"
              " * 480 のワイヤ形式は 485 と完全同一（Stage A でパケットID・\n"
              " * レジストリ・形式の差分0を証明済み）のため 485 の経路を共用する。\n"
              " * ここでは 480 がプロトコル番号として一級に扱われることと、\n"
              " * 485 との相互運用（480⇔485 は付け替えのみ）が通ることを固定する。\n"
              " */")
    t = t[:head_start] + header + t[head_end:]
    t = rep(t, "/** 485／490 共通の Join Game ペイロード（seed／respawn screen 無し）。 */",
            "/** 480／485 共通の Join Game ペイロード（seed／respawn screen 無し）。 */", 1, "ps-0")
    t = rep(t, "void handshake485IsAccepted() {\n        ProxySession.HandshakeResult result = handshake(485, 2, 763);\n"
               "        assertFalse(result.disconnect());\n        assertNotNull(result.session());\n"
               "        assertEquals(485, result.session().clientProtocol());",
            "void handshake480IsAccepted() {\n        ProxySession.HandshakeResult result = handshake(480, 2, 763);\n"
               "        assertFalse(result.disconnect());\n        assertNotNull(result.session());\n"
               "        assertEquals(480, result.session().clientProtocol());", 1, "ps-1")
    t = rep(t, "void handshake489IsRejected() {\n        ProxySession.HandshakeResult result = handshake(489, 2, 763);",
            "void handshake479IsRejected() {\n        ProxySession.HandshakeResult result = handshake(479, 2, 763);", 1, "ps-2")
    t = rep(t, "void join485Against758LoginSuccessUsesStringUuid() {\n        ProxySession session = loginTo(485, 758);",
            "void join480Against758LoginSuccessUsesStringUuid() {\n        ProxySession session = loginTo(480, 758);", 1, "ps-3")
    t = rep(t, "void join485Against758JoinGameIsOldForm() {\n        ProxySession session = loginTo(485, 758);",
            "void join480Against758JoinGameIsOldForm() {\n        ProxySession session = loginTo(480, 758);", 1, "ps-4")
    t = rep(t, "void join485Against485JoinGameSurvivesRoundTrip() {\n        ProxySession session = loginTo(485, 485);",
            "void join480Against480JoinGameSurvivesRoundTrip() {\n        ProxySession session = loginTo(480, 480);", 1, "ps-5")
    t = rep(t, "    void join485ClientAgainst490Server() {\n"
               "        // 485 クライアント → 内部 490。形式は同一のため素通しに近い往復。\n"
               "        ProxySession session = loginTo(485, 490);",
            "    void join480ClientAgainst485Server() {\n"
               "        // 480 クライアント → 内部 485。形式は同一のため素通しに近い往復。\n"
               "        ProxySession session = loginTo(480, 485);", 1, "ps-6")
    t = rep(t, "    void join490ClientAgainst485Server() {\n"
               "        // 490 クライアント → 内部 485。形式は同一のため素通しに近い往復。\n"
               "        ProxySession session = loginTo(490, 485);",
            "    void join485ClientAgainst480Server() {\n"
               "        // 485 クライアント → 内部 480。形式は同一のため素通しに近い往復。\n"
               "        ProxySession session = loginTo(485, 480);", 1, "ps-7")
    t = rep(t, "void acknowledgePlayerDiggingIsDroppedFor485Client() {",
            "void acknowledgePlayerDiggingIsDroppedFor480Client() {", 1, "ps-8")
    t = rep(t, "        // その合成先は 485 にも 490 にも存在しないため両方に届かず、\n"
               "        // 498 クライアントにだけ 0x5c で届く。",
            "        // その合成先は 480 にも 485 にも存在しないため両方に届かず、\n"
               "        // 498 クライアントにだけ 0x5c で届く。", 1, "ps-9")
    t = rep(t, "        ProxySession session485 = loginTo(485, 758);\n"
               "        session485.handleServerPacket(new MinecraftPacket(0x02, loginSuccess758()));\n"
               "        session485.handleServerPacket(new MinecraftPacket(0x26, joinGame758Payload()));\n"
               "        dig(session485);\n"
               "        Outcome dropped = session485.handleServerPacket(new MinecraftPacket(0x08, ack.bytes()));\n"
               "        assertEquals(0, dropped.toClient().size(),\n"
               "                \"485 に無いパケットはクライアントへ送らない\");\n"
               "\n"
               "        ProxySession session490 = loginTo(490, 758);\n"
               "        session490.handleServerPacket(new MinecraftPacket(0x02, loginSuccess758()));\n"
               "        session490.handleServerPacket(new MinecraftPacket(0x26, joinGame758Payload()));\n"
               "        dig(session490);\n"
               "        Outcome dropped490 = session490.handleServerPacket(new MinecraftPacket(0x08, ack.bytes()));\n"
               "        assertEquals(0, dropped490.toClient().size(),\n"
               "                \"490 にも無いパケットはクライアントへ送らない\");",
            "        ProxySession session480 = loginTo(480, 758);\n"
               "        session480.handleServerPacket(new MinecraftPacket(0x02, loginSuccess758()));\n"
               "        session480.handleServerPacket(new MinecraftPacket(0x26, joinGame758Payload()));\n"
               "        dig(session480);\n"
               "        Outcome dropped = session480.handleServerPacket(new MinecraftPacket(0x08, ack.bytes()));\n"
               "        assertEquals(0, dropped.toClient().size(),\n"
               "                \"480 に無いパケットはクライアントへ送らない\");\n"
               "\n"
               "        ProxySession session485 = loginTo(485, 758);\n"
               "        session485.handleServerPacket(new MinecraftPacket(0x02, loginSuccess758()));\n"
               "        session485.handleServerPacket(new MinecraftPacket(0x26, joinGame758Payload()));\n"
               "        dig(session485);\n"
               "        Outcome dropped485 = session485.handleServerPacket(new MinecraftPacket(0x08, ack.bytes()));\n"
               "        assertEquals(0, dropped485.toClient().size(),\n"
               "                \"485 にも無いパケットはクライアントへ送らない\");", 1, "ps-10")
    t = rep(t, "void chat485To758ServerStaysLegacyChat() {\n        ProxySession session = loginTo(485, 758);",
            "void chat480To758ServerStaysLegacyChat() {\n        ProxySession session = loginTo(480, 758);", 1, "ps-11")
    t = rep(t, "void chat758To485ClientDropsSenderUuid() {\n        ProxySession session = loginTo(485, 758);",
            "void chat758To480ClientDropsSenderUuid() {\n        ProxySession session = loginTo(480, 758);", 1, "ps-12")
    t = rep(t, "class ProxySession485Test", "class ProxySession480Test", 1, "ps-13")
    save("ProxySession480Test.java", t)


def real_vanilla_join():
    t = load("RealVanilla758Join485Test.java")
    t = t.replace("485", "480")
    save("RealVanilla758Join480Test.java", t)


def resource_pack_builder():
    t = load("ResourcePackBuilder485Test.java")
    t = t.replace("485", "480").replace("1.14.2", "1.14.1")
    save("ResourcePackBuilder480Test.java", t)


def bedrock_chunk():
    t = load("BedrockChunkTranslator485Test.java", BTEST)
    t = t.replace("485", "480").replace("1.14.2", "1.14.1")
    t = rep(t, "480 のチャンク形式は 490 と同一（バイオームはデータバッファ末尾の\n * 256件）のため、translate490 のパスを共用することと、正規化が\n * 全範囲で 490 と一致することを固定する。",
            "480 のチャンク形式は 485 と同一（バイオームはデータバッファ末尾の\n * 256件）のため、485 と同じ経路（translate498）を共用することと、\n * 正規化が全範囲で 485 と一致することを固定する。", 1, "bc-0")
    # 参照先は 490 のままではなく 485（隣接）へ。
    t = rep(t, "void normalizeBlockState480Equals490OverFullRange()",
            "void normalizeBlockState480Equals485OverFullRange()", 1, "bc-1")
    t = rep(t, "BedrockChunkTranslator.normalizeBlockState(i, 490),\n                    BedrockChunkTranslator.normalizeBlockState(i, 480)",
            "BedrockChunkTranslator.normalizeBlockState(i, 485),\n                    BedrockChunkTranslator.normalizeBlockState(i, 480)", 1, "bc-2")
    save("BedrockChunkTranslator480Test.java", t, BTEST)


def bedrock_backend():
    t = load("JavaBackendConnection485Test.java", BTEST)
    t = t.replace("485", "480").replace("1.14.2", "1.14.1")
    # 比較対象は 490 ではなく 485（隣接）に寄せる。
    t = rep(t, "480 の形式は 490 と同一（Join Game に hashedSeed が無い、",
            "480 の形式は 485 と同一（Join Game に hashedSeed が無い、", 1, "bb-1")
    t = rep(t, "{@link JavaBackendConnection} の 480 分岐が 490 と同じ経路を",
            "{@link JavaBackendConnection} の 480 分岐が 485 と同じ経路を", 1, "bb-2")
    t = rep(t, "/** 480 の Join Game ペイロード（hashedSeed が無い。490 と同一形式）。 */",
            "/** 480 の Join Game ペイロード（hashedSeed が無い。485 と同一形式）。 */", 1, "bb-2b")
    t = rep(t, "void normalize480EntityAndItemEqual490() {", "void normalize480EntityAndItemEqual485() {", 1, "bb-3")
    t = rep(t, "BedrockEntityTranslator.normalizeEntityType(54, 490),\n                BedrockEntityTranslator.normalizeEntityType(54, 480));",
            "BedrockEntityTranslator.normalizeEntityType(54, 485),\n                BedrockEntityTranslator.normalizeEntityType(54, 480));", 1, "bb-4")
    t = rep(t, "BedrockEntityTranslator.normalizeItem(753, 490),\n                BedrockEntityTranslator.normalizeItem(753, 480)); // zombie_pigman_spawn_egg",
            "BedrockEntityTranslator.normalizeItem(753, 485),\n                BedrockEntityTranslator.normalizeItem(753, 480)); // zombie_pigman_spawn_egg", 1, "bb-5")
    t = rep(t, "    void packetIds480Match490ExceptAckDigging() {\n"
               "        // Bedrock 側が使うパケットは 480 でも 490 と同じ ID。\n"
               "        assertEquals(PacketIds.id(PacketIds.PacketName.PLAY_JOIN_GAME, 490),\n"
               "                PacketIds.id(PacketIds.PacketName.PLAY_JOIN_GAME, 480));\n"
               "        assertEquals(PacketIds.id(PacketIds.PacketName.PLAY_SPAWN_ENTITY_LIVING, 490),\n"
               "                PacketIds.id(PacketIds.PacketName.PLAY_SPAWN_ENTITY_LIVING, 480));\n"
               "    }",
            "    void packetIds480Match485OverAllNames() {\n"
               "        // 480 のパケットIDは全エントリで 485 と一致する（Stage A で証明済み）。\n"
               "        for (PacketIds.PacketName name : PacketIds.PacketName.values()) {\n"
               "            assertEquals(PacketIds.id(name, 485), PacketIds.id(name, 480),\n"
               "                    String.valueOf(name));\n"
               "        }\n"
               "    }", 1, "bb-6")
    save("JavaBackendConnection480Test.java", t, BTEST)


if __name__ == "__main__":
    client_adapter()
    client_join_flow()
    registry_remapper()
    play_translator()
    proxy_session()
    real_vanilla_join()
    resource_pack_builder()
    bedrock_chunk()
    bedrock_backend()
