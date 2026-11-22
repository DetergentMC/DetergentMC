#!/usr/bin/env python3
"""477（1.14）のテストを 480 のテストから生成する。

480 との差分は Stage A で 0 と証明済み（パケットID・形式は
protocol.json がバイト一致、レジストリはワイヤ上一致）のため、
477 のテストは 480 のテストを写し、参照先を 480 にずらした形になる。

使い方: python3 tools/gen-477-tests.py
"""
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
PROTO_TEST = ROOT / "src/test/java/io/detergentmc/protocol"
BEDROCK = ROOT.parent / "detergentmc-bedrock"
BEDROCK_TEST = BEDROCK / "src/test/java/io/detergentmc/bedrock"


def read(path):
    return path.read_text(encoding="utf-8")


def write(path, text):
    path.write_text(text, encoding="utf-8")
    print(f"生成: {path.relative_to(ROOT.parent)}")


def sub(text, old, new, count, where):
    found = text.count(old)
    if found != count:
        print(f"NG[{where}]: {found} 件（期待 {count}）: {old[:70]!r}")
        sys.exit(1)
    return text.replace(old, new)


def client_adapter():
    t = read(PROTO_TEST / "ClientAdapter480Test.java")
    t = sub(t, "/** 480（1.14.1）クライアントの受け入れ判断。このツリーで最古の対応プロトコル。 */",
            "/** 477（1.14）クライアントの受け入れ判断。このツリーで最古の対応プロトコル。 */", 1, "ca-header")
    t = sub(t, "class ClientAdapter480Test", "class ClientAdapter477Test", 1, "ca-class")
    t = sub(t, "void decide480IsAcceptedViaAdapter() {\n        Handshake handshake = ClientAdapter.decide(480);",
            "void decide477IsAcceptedViaAdapter() {\n        Handshake handshake = ClientAdapter.decide(477);", 1, "ca-1")
    t = sub(t, "assertEquals(480, handshake.clientProtocol());\n        assertTrue(handshake.message().contains(\"480\"), handshake.message());",
            "assertEquals(477, handshake.clientProtocol());\n        assertTrue(handshake.message().contains(\"477\"), handshake.message());", 1, "ca-2")
    t = sub(t, "void decide480AgainstAllServerProtocols() {\n        for (int server : new int[]{480, 485,",
            "void decide477AgainstAllServerProtocols() {\n        for (int server : new int[]{477, 480, 485,", 1, "ca-3")
    t = sub(t, "Handshake handshake = ClientAdapter.decide(480, server);\n            assertTrue(handshake.accepted(), \"server=\" + server);\n            if (server == 480) {",
            "Handshake handshake = ClientAdapter.decide(477, server);\n            assertTrue(handshake.accepted(), \"server=\" + server);\n            if (server == 477) {", 1, "ca-4")
    t = sub(t, """        // 777より新しい／480より古い／間の未対応帯は断る。
        assertFalse(ClientAdapter.decide(777, 480).accepted());
        assertFalse(ClientAdapter.decide(574, 480).accepted());
        assertFalse(ClientAdapter.decide(489, 480).accepted());
        assertFalse(ClientAdapter.decide(484, 480).accepted());
        assertFalse(ClientAdapter.decide(479, 480).accepted());
        assertFalse(ClientAdapter.decide(340, 480).accepted());""",
            """        // 777より新しい／477より古い／間の未対応帯は断る。
        assertFalse(ClientAdapter.decide(777, 477).accepted());
        assertFalse(ClientAdapter.decide(574, 477).accepted());
        assertFalse(ClientAdapter.decide(489, 477).accepted());
        assertFalse(ClientAdapter.decide(484, 477).accepted());
        assertFalse(ClientAdapter.decide(479, 477).accepted());
        assertFalse(ClientAdapter.decide(476, 477).accepted());
        assertFalse(ClientAdapter.decide(340, 477).accepted());""", 1, "ca-5")
    t = sub(t, "void versionName480Is1141() {\n        assertEquals(\"1.14.1\", ClientAdapter.versionName(480));",
            "void versionName477Is114() {\n        assertEquals(\"1.14\", ClientAdapter.versionName(477));", 1, "ca-6")
    t = sub(t, """    void oldestSupportedIs480() {
        assertEquals(480, ClientAdapter.OLDEST_SUPPORTED_CLIENT_PROTOCOL);
        List<Integer> supported = ClientAdapter.supportedClientProtocols();
        assertEquals(480, supported.get(0), supported.toString());
        assertEquals(485, supported.get(1), supported.toString());
        assertEquals(490, supported.get(2), supported.toString());
        assertEquals(26, supported.size(), supported.toString());""",
            """    void oldestSupportedIs477() {
        assertEquals(477, ClientAdapter.OLDEST_SUPPORTED_CLIENT_PROTOCOL);
        List<Integer> supported = ClientAdapter.supportedClientProtocols();
        assertEquals(477, supported.get(0), supported.toString());
        assertEquals(480, supported.get(1), supported.toString());
        assertEquals(485, supported.get(2), supported.toString());
        assertEquals(27, supported.size(), supported.toString());""", 1, "ca-7")
    t = sub(t, "void differencesFor480Exist() {\n        assertFalse(ClientAdapter.differences(480).isEmpty());",
            "void differencesFor477Exist() {\n        assertFalse(ClientAdapter.differences(477).isEmpty());", 1, "ca-8")
    write(PROTO_TEST / "ClientAdapter477Test.java", t)


def client_join_flow():
    t = read(PROTO_TEST / "ClientJoinFlow480Test.java")
    t = sub(t, "/** 480（1.14.1）クライアントの参加フロー。設定フェーズは無い（490／578／758と同じ）。 */",
            "/** 477（1.14）クライアントの参加フロー。設定フェーズは無い（480／490／578／758と同じ）。 */", 1, "cjf-header")
    t = t.replace("480", "477")
    t = sub(t, "assertThrows(IllegalArgumentException.class, () -> new ClientJoinFlow(479));",
            "assertThrows(IllegalArgumentException.class, () -> new ClientJoinFlow(476));\n        assertThrows(IllegalArgumentException.class, () -> new ClientJoinFlow(479));", 1, "cjf-476")
    write(PROTO_TEST / "ClientJoinFlow477Test.java", t)


def registry_remapper():
    t = read(PROTO_TEST / "RegistryRemapper480Test.java")
    head_end = t.index("class RegistryRemapper480Test")
    pre = t[:t.index("/**")]
    head = (
        "/**\n"
        " * 477（1.14）のレジストリ写像。Stage A の実データ照合で、\n"
        " * 477 と 480 はワイヤ上のレジストリが一致する（実 jar の\n"
        " * data generator 出力で blocks.json・commands.json がバイト一致、\n"
        " * registries.json の差は memory_module_type の1カテゴリだけで、\n"
        " * 変換表には現れない）と証明済み。そのため\n"
        " * 477 の写像は 480 の表へ委譲し、ここでは全範囲で 480 と一致する\n"
        " * ことを固定する。実バニラ 1.14 jar の SHA-1 は\n"
        " * f1a0073671057f01aa843443fef34330281333ce。\n"
        " */\n"
    )
    t = pre + head + t[head_end:]
    t = t.replace("480", "\x00").replace("485", "480").replace("\x00", "477")
    write(PROTO_TEST / "RegistryRemapper477Test.java", t)


def play_translator():
    t = read(PROTO_TEST / "PlayTranslator480Test.java")
    # 参照先（485）はプレースホルダ経由で 480 にずらす（480→477 の置換に巻き込まれないように）。
    t = sub(t, "485 と完全一致", "\x01 と完全一致", 1, "pt-ref")
    t = t.replace("480", "477").replace("1.14.1", "1.14")
    t = t.replace("\x01", "480")
    write(PROTO_TEST / "PlayTranslator477Test.java", t)


def proxy_session():
    t = read(PROTO_TEST / "ProxySession480Test.java")
    head_end = t.index("class ProxySession480Test")
    pre = t[:t.index("/**")]
    head = (
        "/**\n"
        " * 477（1.14）クライアント・サーバーのプロキシ統合テスト。\n"
        " * 477 のワイヤ形式は 480 と完全同一（Stage A でパケットID・\n"
        " * レジストリ・形式の差分0を証明済み）のため 480／485 の経路を共用する。\n"
        " * ここでは 477 がプロトコル番号として一級に扱われることと、\n"
        " * 480 との相互運用（477⇔480 は付け替えのみ）が通ることを固定する。\n"
        " */\n"
    )
    t = pre + head + t[head_end:]
    t = sub(t, "class ProxySession480Test", "class ProxySession477Test", 1, "ps-class")
    t = sub(t, "/** 480／485 共通の Join Game ペイロード（seed／respawn screen 無し）。 */",
            "/** 477／480／485 共通の Join Game ペイロード（seed／respawn screen 無し）。 */", 1, "ps-jg")
    t = sub(t, "void handshake480IsAccepted() {\n        ProxySession.HandshakeResult result = handshake(480, 2, 763);",
            "void handshake477IsAccepted() {\n        ProxySession.HandshakeResult result = handshake(477, 2, 763);", 1, "ps-hs1")
    t = sub(t, "assertEquals(480, result.session().clientProtocol());",
            "assertEquals(477, result.session().clientProtocol());", 1, "ps-hs2")
    t = sub(t, "void handshake479IsRejected() {\n        ProxySession.HandshakeResult result = handshake(479, 2, 763);",
            "void handshake476IsRejected() {\n        ProxySession.HandshakeResult result = handshake(476, 2, 763);", 1, "ps-hs3")
    t = sub(t, "void join480Against758LoginSuccessUsesStringUuid() {\n        ProxySession session = loginTo(480, 758);",
            "void join477Against758LoginSuccessUsesStringUuid() {\n        ProxySession session = loginTo(477, 758);", 1, "ps-j1")
    t = sub(t, "void join480Against758JoinGameIsOldForm() {\n        ProxySession session = loginTo(480, 758);",
            "void join477Against758JoinGameIsOldForm() {\n        ProxySession session = loginTo(477, 758);", 1, "ps-j2")
    t = sub(t, "void join480Against480JoinGameSurvivesRoundTrip() {\n        ProxySession session = loginTo(480, 480);",
            "void join477Against477JoinGameSurvivesRoundTrip() {\n        ProxySession session = loginTo(477, 477);", 1, "ps-j3")
    t = sub(t, """    void join480ClientAgainst485Server() {
        // 480 クライアント → 内部 485。形式は同一のため素通しに近い往復。
        ProxySession session = loginTo(480, 485);""",
            """    void join477ClientAgainst480Server() {
        // 477 クライアント → 内部 480。形式は同一のため素通しに近い往復。
        ProxySession session = loginTo(477, 480);""", 1, "ps-j4")
    t = sub(t, """    void join485ClientAgainst480Server() {
        // 485 クライアント → 内部 480。形式は同一のため素通しに近い往復。
        ProxySession session = loginTo(485, 480);""",
            """    void join480ClientAgainst477Server() {
        // 480 クライアント → 内部 477。形式は同一のため素通しに近い往復。
        ProxySession session = loginTo(480, 477);""", 1, "ps-j5")
    t = sub(t, """    void acknowledgePlayerDiggingIsDroppedFor480Client() {
        // 758 サーバー発の掘削 ack（758 では 0x08）は、先にクライアントが
        // 掘削して記録がある場合にだけ旧式クライアントへ合成される。
        // その合成先は 480 にも 485 にも存在しないため両方に届かず、
        // 498 クライアントにだけ 0x5c で届く。""",
            """    void acknowledgePlayerDiggingIsDroppedFor477Client() {
        // 758 サーバー発の掘削 ack（758 では 0x08）は、先にクライアントが
        // 掘削して記録がある場合にだけ旧式クライアントへ合成される。
        // その合成先は 477 にも 480 にも存在しないため両方に届かず、
        // 498 クライアントにだけ 0x5c で届く.""", 1, "ps-ack1")
    t = sub(t, """        ProxySession session480 = loginTo(480, 758);
        session480.handleServerPacket(new MinecraftPacket(0x02, loginSuccess758()));
        session480.handleServerPacket(new MinecraftPacket(0x26, joinGame758Payload()));
        dig(session480);
        Outcome dropped = session480.handleServerPacket(new MinecraftPacket(0x08, ack.bytes()));
        assertEquals(0, dropped.toClient().size(),
                "480 に無いパケットはクライアントへ送らない");

        ProxySession session485 = loginTo(485, 758);
        session485.handleServerPacket(new MinecraftPacket(0x02, loginSuccess758()));
        session485.handleServerPacket(new MinecraftPacket(0x26, joinGame758Payload()));
        dig(session485);
        Outcome dropped485 = session485.handleServerPacket(new MinecraftPacket(0x08, ack.bytes()));
        assertEquals(0, dropped485.toClient().size(),
                "485 にも無いパケットはクライアントへ送らない");""",
            """        ProxySession session477 = loginTo(477, 758);
        session477.handleServerPacket(new MinecraftPacket(0x02, loginSuccess758()));
        session477.handleServerPacket(new MinecraftPacket(0x26, joinGame758Payload()));
        dig(session477);
        Outcome dropped = session477.handleServerPacket(new MinecraftPacket(0x08, ack.bytes()));
        assertEquals(0, dropped.toClient().size(),
                "477 に無いパケットはクライアントへ送らない");

        ProxySession session480 = loginTo(480, 758);
        session480.handleServerPacket(new MinecraftPacket(0x02, loginSuccess758()));
        session480.handleServerPacket(new MinecraftPacket(0x26, joinGame758Payload()));
        dig(session480);
        Outcome dropped480 = session480.handleServerPacket(new MinecraftPacket(0x08, ack.bytes()));
        assertEquals(0, dropped480.toClient().size(),
                "480 にも無いパケットはクライアントへ送らない");""", 1, "ps-ack2")
    t = sub(t, "void chat480To758ServerStaysLegacyChat() {\n        ProxySession session = loginTo(480, 758);",
            "void chat477To758ServerStaysLegacyChat() {\n        ProxySession session = loginTo(477, 758);", 1, "ps-chat1")
    t = sub(t, "void chat758To480ClientDropsSenderUuid() {\n        ProxySession session = loginTo(480, 758);",
            "void chat758To477ClientDropsSenderUuid() {\n        ProxySession session = loginTo(477, 758);", 1, "ps-chat2")
    write(PROTO_TEST / "ProxySession477Test.java", t)


def real_vanilla():
    t = read(PROTO_TEST / "RealVanilla758Join480Test.java")
    t = t.replace("480", "477")
    write(PROTO_TEST / "RealVanilla758Join477Test.java", t)


def resource_pack_builder():
    t = read(PROTO_TEST / "ResourcePackBuilder480Test.java")
    t = t.replace("480", "477").replace("1.14.1", "1.14")
    write(PROTO_TEST / "ResourcePackBuilder477Test.java", t)


def packet_ids():
    t = read(PROTO_TEST / "PacketIds480Test.java")
    t = sub(t, """/**
 * 480（1.14.1）のパケットID表。480 列は 1.14.1 自身の protocol.json
 * （minecraft-data pc/1.14.1）から packet-ids-480.py で全281エントリを
 * 機械生成し、485 列と1件も違わないことを確認済み。実バニラ 1.14.1
 * jar の SHA-1 は ed76d597a44c5266be2a7fcd77a8270f1f0bc118。
 * 表に無い唯一の 480 パケットは legacy_server_list_ping（0xfe。
 * 旧式 ping で、どのバージョンでも表管理していない）。
 */""",
            """/**
 * 477（1.14）のパケットID表。477 列は 1.14 自身の protocol.json
 * （minecraft-data pc/1.14。1.14.1 のものとバイト一致）から
 * packet-ids-477.py で全281エントリを機械生成し、480 列と1件も
 * 違わないことを確認済み。実バニラ 1.14
 * jar の SHA-1 は f1a0073671057f01aa843443fef34330281333ce。
 * 表に無い唯一の 477 パケットは legacy_server_list_ping（0xfe。
 * 旧式 ping で、どのバージョンでも表管理していない）。
 */""", 1, "pi-header")
    t = sub(t, "class PacketIds480Test", "class PacketIds477Test", 1, "pi-class")
    t = sub(t, "private static final int V480 = ProtocolVersion.V1_14_1;",
            "private static final int V477 = ProtocolVersion.V1_14;", 1, "pi-v")
    t = t.replace("V480", "V477")
    t = sub(t, "void tableKnows480() {\n        assertTrue(PacketIds.isSupported(V477));\n        assertEquals(List.of(480, 485,",
            "void tableKnows477() {\n        assertTrue(PacketIds.isSupported(V477));\n        assertEquals(List.of(477, 480, 485,", 1, "pi-list")
    t = sub(t, """    void presentCount480Is147() {
        int count480 = 0;
        int count485 = 0;
        for (PacketName name : PacketName.values()) {
            if (name.id480() != -1) {
                count480++;
            }
            if (name.id485() != -1) {
                count485++;
            }
        }
        assertEquals(147, count480);
        assertEquals(147, count485);
    }""",
            """    void presentCount477Is147() {
        int count477 = 0;
        int count480 = 0;
        for (PacketName name : PacketName.values()) {
            if (name.id477() != -1) {
                count477++;
            }
            if (name.id480() != -1) {
                count480++;
            }
        }
        assertEquals(147, count477);
        assertEquals(147, count480);
    }""", 1, "pi-count")
    t = sub(t, """    void noDifferenceAgainst485() {
        // Stage A の実データ照合どおり、480 と 485 の ID は全エントリ一致。
        int differing = 0;
        for (PacketName name : PacketName.values()) {
            if (name.id480() != name.id485()) {
                differing++;
            }
        }
        assertEquals(0, differing);
    }""",
            """    void noDifferenceAgainst480() {
        // Stage A の実データ照合どおり、477 と 480 の ID は全エントリ一致。
        int differing = 0;
        for (PacketName name : PacketName.values()) {
            if (name.id477() != name.id480()) {
                differing++;
            }
        }
        assertEquals(0, differing);
    }""", 1, "pi-diff")
    t = sub(t, "void keyPlayIdsAreThe1141Values() {", "void keyPlayIdsAreThe114Values() {", 1, "pi-key")
    t = sub(t, "// acknowledge_player_digging は 1.14.4（498）で追加。480 には無い。",
            "// acknowledge_player_digging は 1.14.4（498）で追加。477 には無い。", 1, "pi-ack")
    write(PROTO_TEST / "PacketIds477Test.java", t)


def chunk_translator():
    t = read(PROTO_TEST / "ChunkTranslator480Test.java")
    # 参照先（485 との一致・同一）はプレースホルダ経由で 480 にずらす。
    # 「485 用変換」「*485 メソッド」は実メソッド名なので 485 のまま残す。
    t = sub(t, "485 と完全同一", "\x01 と完全同一", 1, "ct-ref1")
    t = sub(t, "485 形式と同一", "\x01 形式と同一", 1, "ct-ref2")
    t = sub(t, "485 と同一", "\x01 と同一", 1, "ct-ref3")
    t = t.replace("480", "477").replace("1.14.1", "1.14")
    t = t.replace("\x01", "480")
    write(PROTO_TEST / "ChunkTranslator477Test.java", t)


def protocol_version():
    text = """package io.detergentmc.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** 477（1.14）のプロトコル番号解決。 */
class ProtocolVersion477Test {

    @Test
    void version114Is477() {
        assertEquals(477, ProtocolVersion.V1_14);
        assertEquals(477, ProtocolVersion.serverProtocolFor("1.14"));
    }

    @Test
    void knownVersionsUnchanged() {
        assertEquals(480, ProtocolVersion.serverProtocolFor("1.14.1"));
        assertEquals(776, ProtocolVersion.LATEST_KNOWN);
        assertEquals(765, ProtocolVersion.DEFAULT_SERVER_PROTOCOL);
        assertEquals(765, ProtocolVersion.serverProtocolFor(null));
        assertEquals(765, ProtocolVersion.serverProtocolFor("unknown"));
        assertTrue(PacketIds.isSupported(477));
        assertFalse(PacketIds.isSupported(476));
    }
}
"""
    write(PROTO_TEST / "ProtocolVersion477Test.java", text)


def resource_pack_mode():
    text = """package io.detergentmc.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** 477（1.14）用の外部パック設定（ResourcePackMode の最大オーバーロード）。 */
class ResourcePackMode477Test {

    private static final String VALID_SHA1 = "0123456789abcdef0123456789abcdef01234567";
    private static final String URL = "https://example.invalid/packs/detergentmc-pack.zip";
    private static final String URL_477 =
            "https://example.invalid/packs/detergentmc-pack-1.14.zip";
    private static final String VALID_SHA1_477 =
            "efefefefefefefefefefefefefefefefefefefef";
    private static final String URL_480 =
            "https://example.invalid/packs/detergentmc-pack-1.14.1.zip";
    private static final String VALID_SHA1_480 =
            "cdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcd";

    /** 最大オーバーロード：477 ペアまで渡す（480 はフォールバック先として設定）。 */
    private static ResourcePackMode.Decision decideFull(String url477, String sha1477) {
        return ResourcePackMode.decide(
                URL, VALID_SHA1,
                // 761・762・763・764・766〜776（15ペア＝30個は未設定）
                "", "", "", "", "", "", "", "",
                "", "", "", "", "", "", "", "",
                "", "", "", "", "", "", "", "",
                "", "", "", "", "", "",
                // 760・759・758（未設定）
                "", "", "", "", "", "",
                // 578・575・573（未設定）
                "", "", "", "",
                "", "",
                // 498・490（未設定）
                "", "",
                "", "",
                // 485（未設定）
                "", "",
                // 480（フォールバック先として設定）
                URL_480, VALID_SHA1_480,
                url477, sha1477,
                false, "");
    }

    @Test
    void valid477UrlAndSha1YieldPack477() {
        ResourcePackMode.Decision decision = decideFull(URL_477, VALID_SHA1_477);
        assertEquals(ResourcePackMode.Kind.EXTERNAL, decision.kind());
        assertNotNull(decision.pack477());
        assertEquals(URL_477, decision.pack477().url());
        assertEquals(VALID_SHA1_477, decision.pack477().sha1());
        // packForProtocol は 477 クライアントに 477 パックを返す。
        assertEquals(URL_477, decision.packForProtocol(477).url());
        assertEquals(URL_480, decision.packForProtocol(480).url());
    }

    @Test
    void empty477UrlFallsBackTo480Pack() {
        ResourcePackMode.Decision decision = decideFull("", "");
        assertNull(decision.pack477());
        assertEquals(URL_480, decision.packForProtocol(477).url());
    }

    @Test
    void invalid477Sha1WarnsAndFallsBack() {
        ResourcePackMode.Decision decision = decideFull(URL_477, "not-a-sha1");
        assertNull(decision.pack477());
        assertTrue(decision.problem() != null && decision.problem().contains("477"),
                String.valueOf(decision.problem()));
        assertEquals(URL_480, decision.packForProtocol(477).url());
    }

    @Test
    void nonHttps477UrlWarnsAndFallsBack() {
        ResourcePackMode.Decision decision =
                decideFull("http://example.invalid/pack.zip", VALID_SHA1_477);
        assertNull(decision.pack477());
        assertTrue(decision.problem() != null && decision.problem().contains("477"),
                String.valueOf(decision.problem()));
    }
}
"""
    write(PROTO_TEST / "ResourcePackMode477Test.java", text)


def resource_pack_server():
    t = read(PROTO_TEST / "ResourcePackServer480Test.java")
    t = sub(t, "/** 480（1.14.1）パックの配信（ResourcePackServer の26パック構成と480欠落時の代替）。 */",
            "/** 477（1.14）パックの配信（ResourcePackServer の27パック構成と477欠落時の代替）。 */", 1, "rps-header")
    t = sub(t, "class ResourcePackServer480Test", "class ResourcePackServer477Test", 1, "rps-class")
    t = sub(t, "private ResourcePackServer twentySixPackServer() throws Exception {",
            "private ResourcePackServer twentySevenPackServer() throws Exception {", 1, "rps-m")
    t = sub(t, 'builder.build(tempDir.resolve("765")),\n                builder.build480(tempDir.resolve("480")),',
            'builder.build(tempDir.resolve("765")),\n                builder.build477(tempDir.resolve("477")),\n                builder.build480(tempDir.resolve("480")),', 1, "rps-build")
    t = t.replace("twentySixPackServer()", "twentySevenPackServer()")
    # 477 固有のテスト（有無・振り分け）を差し込む。
    t = sub(t, "    @Test\n    void packForProtocol480FallsBackToPack485When480Missing() throws Exception {",
            """    @Test
    void pack477IsAvailable() throws Exception {
        ResourcePackServer server = twentySevenPackServer();
        ResourcePack pack477 = server.pack477();
        assertNotNull(pack477);
        assertTrue(pack477.url().endsWith(ResourcePackServer.PACK_PATH_477), pack477.url());
        assertEquals(pack477.url(), server.url477());
        assertNotNull(server.pack480());
    }

    @Test
    void packForProtocol477ReturnsPack477() throws Exception {
        ResourcePackServer server = twentySevenPackServer();
        assertEquals(server.pack477().url(), server.packForProtocol(477).url());
        assertEquals(server.pack480().url(), server.packForProtocol(480).url());
    }

    @Test
    void packForProtocol477FallsBackToPack480When477Missing() throws Exception {
        // 477 パックを持たない26パック構成では 480 パックで代替する。
        ResourcePackBuilder builder = new ResourcePackBuilder();
        ResourcePackServer server = new ResourcePackServer(
                builder.build(tempDir.resolve("765")),
                builder.build480(tempDir.resolve("480")),
                builder.build485(tempDir.resolve("485")),
                builder.build490(tempDir.resolve("490")),
                builder.build498(tempDir.resolve("498")),
                builder.build573(tempDir.resolve("573")),
                builder.build575(tempDir.resolve("575")),
                builder.build578(tempDir.resolve("578")),
                builder.build758(tempDir.resolve("758")),
                builder.build759(tempDir.resolve("759")),
                builder.build760(tempDir.resolve("760")),
                builder.build761(tempDir.resolve("761")),
                builder.build762(tempDir.resolve("762")),
                builder.build763(tempDir.resolve("763")),
                builder.build764(tempDir.resolve("764")),
                builder.build766(tempDir.resolve("766")),
                builder.build767(tempDir.resolve("767")),
                builder.build768(tempDir.resolve("768")),
                builder.build769(tempDir.resolve("769")),
                builder.build770(tempDir.resolve("770")),
                builder.build771(tempDir.resolve("771")),
                builder.build772(tempDir.resolve("772")),
                builder.build773(tempDir.resolve("773")),
                builder.build774(tempDir.resolve("774")),
                builder.build775(tempDir.resolve("775")),
                builder.build776(tempDir.resolve("776")),
                0, false, "");
        assertEquals(server.pack480().url(), server.packForProtocol(477).url());
    }

    @Test
    void packForProtocol480FallsBackToPack485When480Missing() throws Exception {""", 1, "rps-477tests")
    write(PROTO_TEST / "ResourcePackServer477Test.java", t)


def bedrock_chunk():
    t = read(BEDROCK_TEST / "BedrockChunkTranslator480Test.java")
    t = t.replace("480", "\x00").replace("485", "480").replace("\x00", "477")
    t = t.replace("1.14.1", "1.14")
    write(BEDROCK_TEST / "BedrockChunkTranslator477Test.java", t)


def bedrock_backend():
    t = read(BEDROCK_TEST / "JavaBackendConnection480Test.java")
    t = t.replace("480", "\x00").replace("485", "480").replace("\x00", "477")
    t = t.replace("1.14.1", "1.14")
    write(BEDROCK_TEST / "JavaBackendConnection477Test.java", t)


if __name__ == "__main__":
    client_adapter()
    client_join_flow()
    registry_remapper()
    play_translator()
    proxy_session()
    real_vanilla()
    resource_pack_builder()
    packet_ids()
    chunk_translator()
    protocol_version()
    resource_pack_mode()
    resource_pack_server()
    bedrock_chunk()
    bedrock_backend()
