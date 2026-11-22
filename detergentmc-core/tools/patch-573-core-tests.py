#!/usr/bin/env python3
"""core の既存テストへ 573（1.15）分のケースを足す。

575 の追加分（ba7460f）と同じ流儀で、各ファイルの 575 テストの直前に
573 テストを差し込む。

使い方: python3 tools/patch-573-core-tests.py
"""
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent

EDITS = [
    (
        "src/test/java/io/detergentmc/core/AdapterDiscoveryTest.java",
        """    @Test
    void discoversThe1151Adapter() {""",
        """    @Test
    void discoversThe115Adapter() {
        boolean found = StreamSupport.stream(
                        ServiceLoader.load(VersionAdapter.class).spliterator(), false)
                .anyMatch(adapter -> adapter.minecraftVersion().equals("1.15")
                        && adapter.protocolVersion() == 573);
        assertTrue(found, "Expected a 1.15 adapter on the test classpath");

        VersionAdapter adapter = DetergentServer.selectAdapter("1.15");
        assertEquals("1.15", adapter.minecraftVersion());
        assertEquals(573, adapter.protocolVersion());
    }

""",
    ),
    (
        "src/test/java/io/detergentmc/core/ServerProtocolWiringTest.java",
        """    @Test
    void minecraftVersion1151SelectsInternalProtocol575() throws Exception {""",
        """    @Test
    void minecraftVersion115SelectsInternalProtocol573() throws Exception {
        DetergentServer server = serverWith(Map.of("minecraft-version", "1.15"));
        assertEquals(573, server.serverProtocol());
    }

""",
    ),
    (
        "src/test/java/io/detergentmc/core/ResourcePackWiringTest.java",
        """    @Test
    void enabledConfigBuildsAndServes575Pack() throws Exception {""",
        """    @Test
    void enabledConfigBuildsAndServes573Pack() throws Exception {
        DetergentServer server = serverWith(Map.of(
                "resource-pack", Map.of("enabled", true, "port", 0, "required", false)));
        try {
            ResourcePack pack = server.startResourcePack();
            assertNotNull(pack);
            // 1.15 用（pack_format 5）も一緒にビルド・配信される。
            ResourcePack pack573 = server.resourcePack573();
            assertNotNull(pack573);
            assertTrue(pack573.url().endsWith("/detergentmc-pack-1.15.zip"), pack573.url());
            assertTrue(Files.exists(tempDir.resolve("data").resolve("resource-pack")
                    .resolve("detergentmc-pack-1.15.zip")));
            HttpURLConnection connection = (HttpURLConnection)
                    URI.create(pack573.url()).toURL().openConnection();
            try {
                assertEquals(200, connection.getResponseCode());
                byte[] body = connection.getInputStream().readAllBytes();
                assertEquals(pack573.sha1(),
                        io.detergentmc.protocol.ResourcePackBuilder.sha1Hex(body));
            } finally {
                connection.disconnect();
            }
        } finally {
            server.stopResourcePack();
        }
        assertNull(server.resourcePack573());
    }

""",
    ),
    (
        "src/test/java/io/detergentmc/core/vanilla/DatapackGeneratorTest.java",
        """    @Test
    void packFormatFor1151Is5() throws Exception {""",
        """    @Test
    void packFormatFor115Is5() throws Exception {
        // 実バニラ 1.15 jar の version.json：pack_version = 5。
        assertEquals(5, DatapackGenerator.PACK_FORMAT_1_15, "1.15 datapack format");
        assertEquals(5, DatapackGenerator.packFormatFor("1.15"));
        Path vanillaDir = tempDir.resolve("vanilla-1-15");
        Path file = new DatapackGenerator()
                .build(vanillaDir, DatapackGenerator.packFormatFor("1.15"));
        Map<String, String> entries = readZip(file);
        assertEquals("{\\"pack\\":{\\"pack_format\\":5,\\"description\\":\\"DetergentMC\\"}}",
                entries.get("pack.mcmeta"));
    }

""",
    ),
    (
        "src/test/java/io/detergentmc/core/vanilla/VanillaServerHostTest.java",
        """            // 1.15.1 も同じ Java 8 世代。""",
        """            // 1.15 も同じ Java 8 世代。
            assertEquals(Path.of("/opt/example-jdk-8", "bin", "java").toString(),
                    VanillaServerHost.javaBinaryFor("1.15"));
""",
    ),
]


def main():
    for rel, anchor, insert in EDITS:
        p = ROOT / rel
        text = p.read_text(encoding="utf-8")
        n = text.count(anchor)
        if n != 1:
            print(f"NG: anchor x{n} in {rel}: {anchor[:60]!r}")
            return 1
        p.write_text(text.replace(anchor, insert + anchor), encoding="utf-8")
        print(f"OK: {rel}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
