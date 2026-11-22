#!/usr/bin/env python3
"""ProxyIntegrationTest に 573（1.15）の統合セルを足す。

- findRealVanilla115Jar（1.15.1 版の隣）
- fake573ClientJoinsRealVanilla115ThroughProxy（573→実1.15 ネイティブ）
- fake573ClientJoinsRealVanilla1151ThroughProxy（573→実1.15.1）
- fake575ClientJoinsRealVanilla115ThroughProxy（575→実1.15）

573 のワイヤ形式は 575／578 と同一のため Fake578Client をそのまま使う
（ハンドシェイクのプロトコル番号だけが違う）。

使い方: python3 tools/patch-573-integration.py
（detergentmc-core/tools/ に置くこと）
"""
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
P = ROOT / "src/test/java/io/detergentmc/core/vanilla/ProxyIntegrationTest.java"


def sub(text, old, new, count):
    found = text.count(old)
    if found != count:
        print(f"NG: {found} 件（期待 {count}）: {old[:70]!r}")
        sys.exit(1)
    return text.replace(old, new)


FINDER = '''
    /** 本物のバニラ1.15のjarを探す。無ければ内部573の統合テストはスキップする。 */
    private static Path findRealVanilla115Jar() {
        for (String candidate : List.of(
                "run/vanilla/server-1.15.jar",
                "../run/vanilla/server-1.15.jar",
                "../../run/vanilla/server-1.15.jar")) {
            Path path = Path.of(candidate).toAbsolutePath().normalize();
            if (Files.isRegularFile(path)) {
                return path;
            }
        }
        return null;
    }
'''


def make_test(name, client_protocol, jar_version, jar_finder, internal_protocol,
              chat_text, javadoc_lines):
    javadoc = "    /**\n" + "".join(f"     * {line}\n" for line in javadoc_lines) + "     */\n"
    return javadoc + f'''    @Test
    void {name}() throws Exception {{
        Path realJar = {jar_finder}();
        assumeTrue(realJar != null,
                "本物のバニラjar（run/vanilla/server-{jar_version}.jar）が無いためスキップします。");

        Path vanillaDir = tempDir.resolve("vanilla");
        Files.createDirectories(vanillaDir);
        Files.copy(realJar, vanillaDir.resolve("server-{jar_version}.jar"));

        int vanillaPort = freePort();
        int proxyPort = freePort();
        CountDownLatch joined = new CountDownLatch(1);
        CountDownLatch chatSeen = new CountDownLatch(1);

        try (FakeMojang mojang = new FakeMojang(
                vanillaDir.resolve("server-{jar_version}.jar"), "{jar_version}")) {{
            VanillaServerHost host = new VanillaServerHost(vanillaDir, "{jar_version}", vanillaPort,
                    false, 10, "DetergentMC test", null, 512,
                    new VanillaDownloader(mojang.manifestUrl()));
            host.addListener(event -> {{
                if (event instanceof VanillaLogEvent.PlayerJoined joinedEvent
                        && joinedEvent.name().equals("Steve")) {{
                    joined.countDown();
                }}
                if (event instanceof VanillaLogEvent.ChatMessage chat
                        && chat.name().equals("Steve")
                        && chat.message().equals("{chat_text}")) {{
                    chatSeen.countDown();
                }}
            }});
            host.start();
            try {{
                assertTrue(host.waitForReady(600_000), "バニラが600秒以内に起動しませんでした。");
                ClientProxy proxy = new ClientProxy(proxyPort, "127.0.0.1", vanillaPort, false,
                        null, null, null, ProxyAuthConfig.defaults(),
                        null, null, null, null, null, null, {internal_protocol});
                assertEquals({internal_protocol}, proxy.internalProtocol());
                proxy.start();
                try (Fake578Client client = new Fake578Client(proxyPort)) {{
                    client.sendPacket(new MinecraftPacket(0x00,
                            handshakePayload({client_protocol}, 2, proxyPort)));
                    client.sendPacket(new MinecraftPacket(0x00, loginStart758("Steve")));

                    MinecraftPacket joinGame = client.waitForId(0x26, 30_000);
                    assertTrue(joinGame != null, "Join Game（{client_protocol}形式 0x26）が届きませんでした。");
                    io.detergentmc.protocol.PlayTranslator.JoinGame578 parsed =
                            io.detergentmc.protocol.PlayTranslator.parseJoinGame578(
                                    joinGame.payload());
                    assertEquals(0, parsed.dimension(), "overworld の次元 ID は 0");

                    client.sendPacket(new MinecraftPacket(0x03,
                            chatMessage758("{chat_text}")));
                    client.sendPacket(new MinecraftPacket(0x11, move758()));

                    assertTrue(joined.await(30, TimeUnit.SECONDS),
                            "バニラログに Steve の参加が現れませんでした。");
                    assertTrue(chatSeen.await(30, TimeUnit.SECONDS),
                            "バニラログにチャットが現れませんでした。");
                    assertTrue(!client.isClosed(), "参加後に接続が切れていないこと");
                }} finally {{
                    proxy.stop();
                }}
            }} finally {{
                host.stop();
            }}
        }}
    }}

'''


def main():
    text = P.read_text(encoding="utf-8")
    if "fake573ClientJoinsRealVanilla115ThroughProxy" in text:
        print("skip（既存）: 573 統合セルはすでにある")
        return 0

    # 1. jar 探索ヘルパー（1.15.2 版の直後）。
    anchor = '''    /** 本物のバニラ1.20.4のjarを探す（findRealVanillaJar の別名）。 */'''
    text = sub(text, anchor, FINDER + "\n" + anchor, 1)

    # 2. 統合セル3件（575 セルの直前に挿入）。
    tests = ""
    tests += make_test(
        "fake573ClientJoinsRealVanilla115ThroughProxy",
        573, "1.15", "findRealVanilla115Jar", 573,
        "hello from 1.15",
        ["偽573クライアントがプロキシ経由で本物のバニラ1.15（内部573）に",
         "参加する。573 ネイティブで参加・チャット・移動が動くこと。",
         "パケットIDは 575／578 と同一（Join Game 0x26／Chat Message 0x03／移動 0x11）。",
         "1.15 は Java 8 で起動する（Java8TestSupport が detergentmc.java8.home を設定する）。"])
    tests += make_test(
        "fake573ClientJoinsRealVanilla1151ThroughProxy",
        573, "1.15.1", "findRealVanilla1151Jar", 575,
        "hello from 573 to 1.15.1",
        ["偽573クライアントがプロキシ経由で本物のバニラ1.15.1（内部575）に",
         "参加する。573 と 575 はワイヤ形式が同一（Stage A で実データ証明済み）",
         "のため変換なしで届き、チャットがバニラへ届くこと。"])
    tests += make_test(
        "fake575ClientJoinsRealVanilla115ThroughProxy",
        575, "1.15", "findRealVanilla115Jar", 573,
        "hello from 575 to 1.15",
        ["偽575クライアントがプロキシ経由で本物のバニラ1.15（内部573）に",
         "参加する。573 と 575 はワイヤ形式が同一のため変換なしで届き、",
         "チャットがバニラへ届くこと。"])
    anchor2 = '''    /**
     * 偽575クライアントがプロキシ経由で本物のバニラ1.15.1（内部575）に'''
    text = sub(text, anchor2, tests + anchor2, 1)

    P.write_text(text, encoding="utf-8")
    print("ProxyIntegrationTest に 573 の統合セル3件を追加した")
    return 0


if __name__ == "__main__":
    sys.exit(main())
