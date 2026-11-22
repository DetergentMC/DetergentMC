#!/usr/bin/env python3
"""core のテストに 477（1.14）のケースを足す。

- AdapterDiscoveryTest／ServerProtocolWiringTest／
  ResourcePackWiringTest／DatapackGeneratorTest／
  VanillaServerHostTest に 477 のメソッド・断言を追加。
- ProxyIntegrationTest に findRealVanilla114Jar と統合セル3件
  （fake477→実1.14、fake477→実1.14.1、fake480→実1.14）を追加。
  セルは 480 のセルと同じ形で、Keep Alive は 477 も 0x20
  （応答 0x0f）を使う。

使い方: python3 tools/patch-477-core-tests.py
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
        "    void discoversThe1141Adapter() {",
        "    @Test\n"
        "    void discoversThe114Adapter() {\n"
        "        boolean found = StreamSupport.stream(\n"
        "                        ServiceLoader.load(VersionAdapter.class).spliterator(), false)\n"
        "                .anyMatch(adapter -> adapter.minecraftVersion().equals(\"1.14\")\n"
        "                        && adapter.protocolVersion() == 477);\n"
        "        assertTrue(found, \"Expected a 1.14 adapter on the test classpath\");\n"
        "\n"
        "        VersionAdapter adapter = DetergentServer.selectAdapter(\"1.14\");\n"
        "        assertEquals(\"1.14\", adapter.minecraftVersion());\n"
        "        assertEquals(477, adapter.protocolVersion());\n"
        "    }\n"
        "\n"
        "    @Test\n"
        "    void discoversThe1141Adapter() {", 1, "AdapterDiscoveryTest")


def server_protocol():
    p = CORE / "ServerProtocolWiringTest.java"
    sub(p,
        "    @Test\n"
        "    void minecraftVersion1141SelectsInternalProtocol480() throws Exception {",
        "    @Test\n"
        "    void minecraftVersion114SelectsInternalProtocol477() throws Exception {\n"
        "        DetergentServer server = serverWith(Map.of(\"minecraft-version\", \"1.14\"));\n"
        "        assertEquals(477, server.serverProtocol());\n"
        "    }\n"
        "\n"
        "    @Test\n"
        "    void minecraftVersion1141SelectsInternalProtocol480() throws Exception {", 1,
        "ServerProtocolWiringTest")


def resource_pack():
    p = CORE / "ResourcePackWiringTest.java"
    sub(p,
        "    @Test\n"
        "    void enabledConfigBuildsAndServes480Pack() throws Exception {",
        "    @Test\n"
        "    void enabledConfigBuildsAndServes477Pack() throws Exception {\n"
        "        DetergentServer server = serverWith(Map.of(\n"
        "                \"resource-pack\", Map.of(\"enabled\", true, \"port\", 0, \"required\", false)));\n"
        "        try {\n"
        "            ResourcePack pack = server.startResourcePack();\n"
        "            assertNotNull(pack);\n"
        "            // 1.14 用（pack_format 4）も一緒にビルド・配信される。\n"
        "            ResourcePack pack477 = server.resourcePack477();\n"
        "            assertNotNull(pack477);\n"
        "            assertTrue(pack477.url().endsWith(\"/detergentmc-pack-1.14.zip\"), pack477.url());\n"
        "            assertTrue(Files.exists(tempDir.resolve(\"data\").resolve(\"resource-pack\")\n"
        "                    .resolve(\"detergentmc-pack-1.14.zip\")));\n"
        "            HttpURLConnection connection = (HttpURLConnection)\n"
        "                    URI.create(pack477.url()).toURL().openConnection();\n"
        "            try {\n"
        "                assertEquals(200, connection.getResponseCode());\n"
        "                byte[] body = connection.getInputStream().readAllBytes();\n"
        "                assertEquals(pack477.sha1(),\n"
        "                        io.detergentmc.protocol.ResourcePackBuilder.sha1Hex(body));\n"
        "            } finally {\n"
        "                connection.disconnect();\n"
        "            }\n"
        "        } finally {\n"
        "            server.stopResourcePack();\n"
        "        }\n"
        "        assertNull(server.resourcePack477());\n"
        "    }\n"
        "\n"
        "    @Test\n"
        "    void enabledConfigBuildsAndServes480Pack() throws Exception {", 1,
        "ResourcePackWiringTest")


def datapack():
    p = CORE / "vanilla/DatapackGeneratorTest.java"
    sub(p,
        "    @Test\n"
        "    void packFormatFor1141Is4() throws Exception {",
        "    @Test\n"
        "    void packFormatFor114Is4() throws Exception {\n"
        "        // 実バニラ 1.14 jar の version.json：pack_version = 4。\n"
        "        assertEquals(4, DatapackGenerator.packFormatFor(\"1.14\"));\n"
        "        Path vanillaDir = tempDir.resolve(\"vanilla-1-14\");\n"
        "        Path file = new DatapackGenerator()\n"
        "                .build(vanillaDir, DatapackGenerator.packFormatFor(\"1.14\"));\n"
        "        Map<String, String> entries = readZip(file);\n"
        "        assertEquals(\"{\\\"pack\\\":{\\\"pack_format\\\":4,\\\"description\\\":\\\"DetergentMC\\\"}}\",\n"
        "                entries.get(\"pack.mcmeta\"));\n"
        "    }\n"
        "\n"
        "    @Test\n"
        "    void packFormatFor1141Is4() throws Exception {", 1, "DatapackGeneratorTest")


def vanilla_host():
    p = CORE / "vanilla/VanillaServerHostTest.java"
    sub(p,
        '            // 1.14.1 も同じ Java 8 世代。\n'
        '            assertEquals(Path.of("/opt/example-jdk-8", "bin", "java").toString(),\n'
        '                    VanillaServerHost.javaBinaryFor("1.14.1"));',
        '            // 1.14.1 も同じ Java 8 世代。\n'
        '            assertEquals(Path.of("/opt/example-jdk-8", "bin", "java").toString(),\n'
        '                    VanillaServerHost.javaBinaryFor("1.14.1"));\n'
        '            // 1.14 も同じ Java 8 世代。\n'
        '            assertEquals(Path.of("/opt/example-jdk-8", "bin", "java").toString(),\n'
        '                    VanillaServerHost.javaBinaryFor("1.14"));', 1,
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
    if "fake477ClientJoinsRealVanilla114ThroughProxy" in text:
        print("skip（既存）: ProxyIntegrationTest")
        return
    anchor = "    private static Path findRealVanilla1141Jar() {"
    finder = (
        "    /** 本物のバニラ1.14のjarを探す。無ければ内部477の統合テストはスキップする。 */\n"
        "    private static Path findRealVanilla114Jar() {\n"
        "        for (String candidate : List.of(\n"
        "                \"run/vanilla/server-1.14.jar\",\n"
        "                \"../run/vanilla/server-1.14.jar\",\n"
        "                \"../../run/vanilla/server-1.14.jar\")) {\n"
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
        print("NG[integration]: findRealVanilla1141Jar のアンカーが一意でない")
        sys.exit(1)
    text = text.replace(anchor, finder + anchor)

    cells = ""
    cells += CELL_TEMPLATE.format(
        javadoc="偽477クライアントがプロキシ経由で本物のバニラ1.14（内部477）に\n"
                "     * 参加する。477 ネイティブで参加・チャット・移動が動くこと。\n"
                "     * Join Game は旧式形式（0x25、hashedSeed 無し）。Keep Alive は\n"
                "     * 477 も 0x20（応答 0x0f）。\n"
                "     * 1.14 は Java 8 で起動する（Java8TestSupport が detergentmc.java8.home を設定する）。",
        method="fake477ClientJoinsRealVanilla114ThroughProxy",
        findJar="findRealVanilla114Jar()", version="1.14",
        chat="hello from 1.14", internal=477, client=477)
    cells += CELL_TEMPLATE.format(
        javadoc="偽477クライアントがプロキシ経由で本物のバニラ1.14.1（内部480）に\n"
                "     * 参加する。477→480 は形式が同一のため、パケットIDの付け替え\n"
                "     * （477 列→480 列。全エントリ一致）だけで通ること。",
        method="fake477ClientJoinsRealVanilla1141ThroughProxy",
        findJar="findRealVanilla1141Jar()", version="1.14.1",
        chat="hello from 477 to 1.14.1", internal=480, client=477)
    cells += CELL_TEMPLATE.format(
        javadoc="偽480クライアントがプロキシ経由で本物のバニラ1.14（内部477）に\n"
                "     * 参加する。480→477 も形式が同一のため、パケットIDの付け替え\n"
                "     * だけで通ること。",
        method="fake480ClientJoinsRealVanilla114ThroughProxy",
        findJar="findRealVanilla114Jar()", version="1.14",
        chat="hello from 480 to 1.14", internal=477, client=480)

    anchor2 = ("    /**\n"
               "     * 偽480クライアントがプロキシ経由で本物のバニラ1.14.1（内部480）に")
    if text.count(anchor2) != 1:
        print("NG[integration]: 480 セルのアンカーが一意でない")
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
