#!/usr/bin/env python3
"""core のテストに 485（1.14.2）のケースを足す。

- AdapterDiscoveryTest／ServerProtocolWiringTest／
  ResourcePackWiringTest／DatapackGeneratorTest／
  VanillaServerHostTest に 485 のメソッド・断言を追加。
- ProxyIntegrationTest に findRealVanilla1143Jar と統合セル3件
  （fake485→実1.14.2、fake485→実1.14.3、fake490→実1.14.2）を追加。
  セルは 490 のセルと同じ形で、Keep Alive は 485 も 0x20
  （応答 0x0f）を使う。

使い方: python3 tools/patch-485-core-tests.py
"""
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
CORE = ROOT / "src/test/java/io/detergentmc/core"


def sub(path, old, new, count, where):
    text = path.read_text(encoding="utf-8")
    found = text.count(old)
    if found != count:
        print(f"NG[{where}]: {found} 件（期待 {count}）: {old[:70]!r}")
        sys.exit(1)
    path.write_text(text.replace(old, new), encoding="utf-8")
    print(f"{where} を更新した")


def adapter_discovery():
    p = CORE / "AdapterDiscoveryTest.java"
    sub(p,
        "    @Test\n"
        "    void discoversThe1144Adapter() {",
        "    @Test\n"
        "    void discoversThe1142Adapter() {\n"
        "        boolean found = StreamSupport.stream(\n"
        "                        ServiceLoader.load(VersionAdapter.class).spliterator(), false)\n"
        "                .anyMatch(adapter -> adapter.minecraftVersion().equals(\"1.14.2\")\n"
        "                        && adapter.protocolVersion() == 485);\n"
        "        assertTrue(found, \"Expected a 1.14.2 adapter on the test classpath\");\n"
        "\n"
        "        VersionAdapter adapter = DetergentServer.selectAdapter(\"1.14.2\");\n"
        "        assertEquals(\"1.14.2\", adapter.minecraftVersion());\n"
        "        assertEquals(485, adapter.protocolVersion());\n"
        "    }\n"
        "\n"
        "    @Test\n"
        "    void discoversThe1144Adapter() {", 1, "AdapterDiscoveryTest")


def server_protocol():
    p = CORE / "ServerProtocolWiringTest.java"
    sub(p,
        "    @Test\n"
        "    void minecraftVersion1144SelectsInternalProtocol498() throws Exception {",
        "    @Test\n"
        "    void minecraftVersion1143SelectsInternalProtocol485() throws Exception {\n"
        "        DetergentServer server = serverWith(Map.of(\"minecraft-version\", \"1.14.2\"));\n"
        "        assertEquals(485, server.serverProtocol());\n"
        "    }\n"
        "\n"
        "    @Test\n"
        "    void minecraftVersion1144SelectsInternalProtocol498() throws Exception {", 1,
        "ServerProtocolWiringTest")


def resource_pack():
    p = CORE / "ResourcePackWiringTest.java"
    sub(p,
        "    @Test\n"
        "    void enabledConfigBuildsAndServes490Pack() throws Exception {",
        "    @Test\n"
        "    void enabledConfigBuildsAndServes485Pack() throws Exception {\n"
        "        DetergentServer server = serverWith(Map.of(\n"
        "                \"resource-pack\", Map.of(\"enabled\", true, \"port\", 0, \"required\", false)));\n"
        "        try {\n"
        "            ResourcePack pack = server.startResourcePack();\n"
        "            assertNotNull(pack);\n"
        "            // 1.14.2 用（pack_format 4）も一緒にビルド・配信される。\n"
        "            ResourcePack pack485 = server.resourcePack485();\n"
        "            assertNotNull(pack485);\n"
        "            assertTrue(pack485.url().endsWith(\"/detergentmc-pack-1.14.2.zip\"), pack485.url());\n"
        "            assertTrue(Files.exists(tempDir.resolve(\"data\").resolve(\"resource-pack\")\n"
        "                    .resolve(\"detergentmc-pack-1.14.2.zip\")));\n"
        "            HttpURLConnection connection = (HttpURLConnection)\n"
        "                    URI.create(pack485.url()).toURL().openConnection();\n"
        "            try {\n"
        "                assertEquals(200, connection.getResponseCode());\n"
        "                byte[] body = connection.getInputStream().readAllBytes();\n"
        "                assertEquals(pack485.sha1(),\n"
        "                        io.detergentmc.protocol.ResourcePackBuilder.sha1Hex(body));\n"
        "            } finally {\n"
        "                connection.disconnect();\n"
        "            }\n"
        "        } finally {\n"
        "            server.stopResourcePack();\n"
        "        }\n"
        "        assertNull(server.resourcePack485());\n"
        "    }\n"
        "\n"
        "    @Test\n"
        "    void enabledConfigBuildsAndServes490Pack() throws Exception {", 1,
        "ResourcePackWiringTest")


def datapack():
    p = CORE / "vanilla/DatapackGeneratorTest.java"
    sub(p,
        "    @Test\n"
        "    void packFormatFor1144Is4() throws Exception {",
        "    @Test\n"
        "    void packFormatFor1142Is4() throws Exception {\n"
        "        // 実バニラ 1.14.2 jar の version.json：pack_version = 4。\n"
        "        assertEquals(4, DatapackGenerator.packFormatFor(\"1.14.2\"));\n"
        "        Path vanillaDir = tempDir.resolve(\"vanilla-1-14-2\");\n"
        "        Path file = new DatapackGenerator()\n"
        "                .build(vanillaDir, DatapackGenerator.packFormatFor(\"1.14.2\"));\n"
        "        Map<String, String> entries = readZip(file);\n"
        "        assertEquals(\"{\\\"pack\\\":{\\\"pack_format\\\":4,\\\"description\\\":\\\"DetergentMC\\\"}}\",\n"
        "                entries.get(\"pack.mcmeta\"));\n"
        "    }\n"
        "\n"
        "    @Test\n"
        "    void packFormatFor1144Is4() throws Exception {", 1, "DatapackGeneratorTest")


def vanilla_host():
    p = CORE / "vanilla/VanillaServerHostTest.java"
    sub(p,
        '            // 1.14.3 も同じ Java 8 世代。\n'
        '            assertEquals(Path.of("/opt/example-jdk-8", "bin", "java").toString(),\n'
        '                    VanillaServerHost.javaBinaryFor("1.14.3"));',
        '            // 1.14.3 も同じ Java 8 世代。\n'
        '            assertEquals(Path.of("/opt/example-jdk-8", "bin", "java").toString(),\n'
        '                    VanillaServerHost.javaBinaryFor("1.14.3"));\n'
        '            // 1.14.2 も同じ Java 8 世代。\n'
        '            assertEquals(Path.of("/opt/example-jdk-8", "bin", "java").toString(),\n'
        '                    VanillaServerHost.javaBinaryFor("1.14.2"));', 1,
        "VanillaServerHostTest")


CELL_TEMPLATE = '''    /**
     * {javadoc}
     */
    @Test
    void {method}() throws Exception {{
        Path realJar = {findJar};
        assumeTrue(realJar != null,
                "本物のバニラjar（run/vanilla/server-{version}.jar）が無いためスキップします。");

        Path vanillaDir = tempDir.resolve("vanilla");
        Files.createDirectories(vanillaDir);
        Files.copy(realJar, vanillaDir.resolve("server-{version}.jar"));

        int vanillaPort = freePort();
        int proxyPort = freePort();
        CountDownLatch joined = new CountDownLatch(1);
        CountDownLatch chatSeen = new CountDownLatch(1);

        try (FakeMojang mojang = new FakeMojang(
                vanillaDir.resolve("server-{version}.jar"), "{version}")) {{
            VanillaServerHost host = new VanillaServerHost(vanillaDir, "{version}", vanillaPort,
                    false, 10, "DetergentMC test", null, 512,
                    new VanillaDownloader(mojang.manifestUrl()));
            host.addListener(event -> {{
                if (event instanceof VanillaLogEvent.PlayerJoined joinedEvent
                        && joinedEvent.name().equals("Steve")) {{
                    joined.countDown();
                }}
                if (event instanceof VanillaLogEvent.ChatMessage chat
                        && chat.name().equals("Steve")
                        && chat.message().equals("{chat}")) {{
                    chatSeen.countDown();
                }}
            }});
            host.start();
            try {{
                assertTrue(host.waitForReady(600_000), "バニラが600秒以内に起動しませんでした。");
                ClientProxy proxy = new ClientProxy(proxyPort, "127.0.0.1", vanillaPort, false,
                        null, null, null, ProxyAuthConfig.defaults(),
                        null, null, null, null, null, null, {internal});
                assertEquals({internal}, proxy.internalProtocol());
                proxy.start();
                try (Fake578Client client = new Fake578Client(proxyPort)) {{
                    client.useKeepAliveId(0x20);
                    client.sendPacket(new MinecraftPacket(0x00,
                            handshakePayload({client}, 2, proxyPort)));
                    client.sendPacket(new MinecraftPacket(0x00, loginStart758("Steve")));

                    MinecraftPacket joinGame = client.waitForId(0x25, 30_000);
                    assertTrue(joinGame != null, "Join Game（旧式 0x25）が届きませんでした。");
                    io.detergentmc.protocol.PlayTranslator.JoinGame498 parsed =
                            io.detergentmc.protocol.PlayTranslator.parseJoinGame498(
                                    joinGame.payload());
                    assertEquals(0, parsed.dimension(), "overworld の次元 ID は 0");

                    client.sendPacket(new MinecraftPacket(0x03,
                            chatMessage758("{chat}")));
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


def integration():
    p = CORE / "vanilla/ProxyIntegrationTest.java"
    text = p.read_text(encoding="utf-8")
    if "fake485ClientJoinsRealVanilla1143ThroughProxy" in text:
        print("skip（既存）: ProxyIntegrationTest")
        return
    # findRealVanilla1142Jar を 1144 の直前に追加。
    anchor = "    private static Path findRealVanilla1144Jar() {"
    finder = (
        "    /** 本物のバニラ1.14.2のjarを探す。無ければ内部485の統合テストはスキップする。 */\n"
        "    private static Path findRealVanilla1142Jar() {\n"
        "        for (String candidate : List.of(\n"
        "                \"run/vanilla/server-1.14.2.jar\",\n"
        "                \"../run/vanilla/server-1.14.2.jar\",\n"
        "                \"../../run/vanilla/server-1.14.2.jar\")) {\n"
        "            Path path = Path.of(candidate).toAbsolutePath().normalize();\n"
        "            if (Files.isRegularFile(path)) {\n"
        "                return path;\n"
        "            }\n"
        "        }\n"
        "        return null;\n"
        "    }\n"
        "\n"
    )
    if text.count(anchor) != 1:
        print("NG[integration]: findRealVanilla1144Jar のアンカーが一意でない")
        sys.exit(1)
    text = text.replace(anchor, finder + anchor)

    cells = ""
    cells += CELL_TEMPLATE.format(
        javadoc="偽485クライアントがプロキシ経由で本物のバニラ1.14.2（内部485）に\n"
                "     * 参加する。485 ネイティブで参加・チャット・移動が動くこと。\n"
                "     * Join Game は旧式形式（0x25、hashedSeed 無し）。Keep Alive は\n"
                "     * 485 も 0x20（応答 0x0f）。\n"
                "     * 1.14.2 は Java 8 で起動する（Java8TestSupport が detergentmc.java8.home を設定する）。",
        method="fake485ClientJoinsRealVanilla1142ThroughProxy",
        findJar="findRealVanilla1142Jar()", version="1.14.2",
        chat="hello from 1.14.2", internal=485, client=485)
    cells += CELL_TEMPLATE.format(
        javadoc="偽485クライアントがプロキシ経由で本物のバニラ1.14.3（内部490）に\n"
                "     * 参加する。485→490 は形式が同一のため、パケットIDの付け替え\n"
                "     * （485 列→490 列。差は掘削 ack の有無だけ）だけで通ること。",
        method="fake485ClientJoinsRealVanilla1143ThroughProxy",
        findJar="findRealVanilla1143Jar()", version="1.14.3",
        chat="hello from 485 to 1.14.3", internal=490, client=485)
    cells += CELL_TEMPLATE.format(
        javadoc="偽490クライアントがプロキシ経由で本物のバニラ1.14.2（内部485）に\n"
                "     * 参加する。490→485 も形式が同一のため、パケットIDの付け替え\n"
                "     * だけで通ること。",
        method="fake490ClientJoinsRealVanilla1142ThroughProxy",
        findJar="findRealVanilla1142Jar()", version="1.14.2",
        chat="hello from 490 to 1.14.2", internal=485, client=490)

    anchor2 = ("    /**\n"
               "     * 偽490クライアントがプロキシ経由で本物のバニラ1.14.3（内部490）に")
    if text.count(anchor2) != 1:
        print("NG[integration]: 490 セルのアンカーが一意でない")
        sys.exit(1)
    text = text.replace(anchor2, cells + anchor2)
    p.write_text(text, encoding="utf-8")
    print("ProxyIntegrationTest を更新した")


if __name__ == "__main__":
    adapter_discovery()
    server_protocol()
    resource_pack()
    datapack()
    vanilla_host()
    integration()
