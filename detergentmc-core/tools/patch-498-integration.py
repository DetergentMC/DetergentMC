#!/usr/bin/env python3
"""ProxyIntegrationTest に 498（1.14.4）の統合セルを足す。

- findRealVanilla1144Jar
- fake498ClientJoinsRealVanilla1144ThroughProxy（498→実1.14.4 ネイティブ）
- fake498ClientJoinsRealVanilla115ThroughProxy（498→実1.15）
- fake573ClientJoinsRealVanilla1144ThroughProxy（573→実1.14.4）

498 は 573 とワイヤ形式が違う（Join Game 0x25・seed 無し等）ため、
待つパケット ID とパースは 498 用にする。Chat Message（0x03）と
移動（Set Player Position 0x11）の ID は 573 と同じ。

使い方: python3 tools/patch-498-integration.py
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
    /** 本物のバニラ1.14.4のjarを探す。無ければ内部498の統合テストはスキップする。 */
    private static Path findRealVanilla1144Jar() {
        for (String candidate : List.of(
                "run/vanilla/server-1.14.4.jar",
                "../run/vanilla/server-1.14.4.jar",
                "../../run/vanilla/server-1.14.4.jar")) {
            Path path = Path.of(candidate).toAbsolutePath().normalize();
            if (Files.isRegularFile(path)) {
                return path;
            }
        }
        return null;
    }
'''


def make_test(name, client_protocol, jar_version, jar_finder, internal_protocol,
              chat_text, join_id, join_type, join_parser, javadoc_lines):
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

                    MinecraftPacket joinGame = client.waitForId({join_id}, 30_000);
                    assertTrue(joinGame != null, "Join Game（{client_protocol}形式 {join_id}）が届きませんでした。");
                    io.detergentmc.protocol.PlayTranslator.{join_type} parsed =
                            io.detergentmc.protocol.PlayTranslator.{join_parser}(
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
    if "fake498ClientJoinsRealVanilla1144ThroughProxy" in text:
        print("skip（既存）: 498 統合セルはすでにある")
        return 0

    # 1. jar 探索ヘルパー（1.15 版の直前）。
    anchor = '''    /** 本物のバニラ1.15のjarを探す。無ければ内部573の統合テストはスキップする。 */'''
    text = sub(text, anchor, FINDER.strip() + "\n\n" + anchor, 1)

    # 2. 統合セル3件（573 セルの直前）。
    tests = ""
    tests += make_test(
        "fake498ClientJoinsRealVanilla1144ThroughProxy",
        498, "1.14.4", "findRealVanilla1144Jar", 498,
        "hello from 1.14.4", "0x25", "JoinGame498", "parseJoinGame498",
        ["偽498クライアントがプロキシ経由で本物のバニラ1.14.4（内部498）に",
         "参加する。498 ネイティブで参加・チャット・移動が動くこと。",
         "Join Game は 498 形式（0x25、hashedSeed 無し）。",
         "1.14.4 は Java 8 で起動する（Java8TestSupport が detergentmc.java8.home を設定する）。"])
    tests += make_test(
        "fake498ClientJoinsRealVanilla115ThroughProxy",
        498, "1.15", "findRealVanilla115Jar", 573,
        "hello from 498 to 1.15", "0x25", "JoinGame498", "parseJoinGame498",
        ["偽498クライアントがプロキシ経由で本物のバニラ1.15（内部573）に",
         "参加する。498→573 変換（Join Game の seed 合成・レジストリ付け替え・",
         "チャンクのバイオーム 256→1024 展開）が実バニラで通ること。"])
    tests += make_test(
        "fake573ClientJoinsRealVanilla1144ThroughProxy",
        573, "1.14.4", "findRealVanilla1144Jar", 498,
        "hello from 573 to 1.14.4", "0x26", "JoinGame578", "parseJoinGame578",
        ["偽573クライアントがプロキシ経由で本物のバニラ1.14.4（内部498）に",
         "参加する。573→498 変換（Join Game の seed 落とし・バイオーム",
         "1024→256 間引き）が実バニラで通ること。"])
    idx = text.index("fake573ClientJoinsRealVanilla115ThroughProxy")
    insert_at = text.rfind("    /**", 0, idx)
    text = text[:insert_at] + tests + text[insert_at:]

    P.write_text(text, encoding="utf-8")
    print("ProxyIntegrationTest に 498 の統合セル3件を追加した")
    return 0


if __name__ == "__main__":
    sys.exit(main())
