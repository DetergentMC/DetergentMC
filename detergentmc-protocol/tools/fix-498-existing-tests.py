#!/usr/bin/env python3
"""498 追加に伴う既存テストの追随と、core 側の 498 テスト追加。

- PacketIds／ClientAdapter 系テストの対応プロトコル一覧に 498 を足す。
- OLDEST の期待値を 573 → 498 に更新する。
- ResourcePackMode の 573／575／578 テストの decideFull に 498 ペア
  （未設定）を足す。
- core の AdapterDiscovery／ServerProtocolWiring／ResourcePackWiring／
  DatapackGenerator／VanillaServerHost テストに 498 のメソッドを足す。

使い方: python3 tools/fix-498-existing-tests.py
（detergentmc-protocol/tools/ に置くこと。core 側は ../../detergentmc-core）
"""
import sys
from pathlib import Path

PROTO_TEST = Path(__file__).resolve().parent.parent / "src/test/java/io/detergentmc/protocol"
CORE_TEST = (Path(__file__).resolve().parent.parent.parent
             / "detergentmc-core/src/test/java/io/detergentmc/core")


def sub_in(path, old, new, count=None, required=True):
    text = path.read_text(encoding="utf-8")
    found = text.count(old)
    if count is not None and found != count:
        if required:
            print(f"NG[{path.name}]: {found} 件（期待 {count}）: {old[:60]!r}")
            sys.exit(1)
        return False
    if found == 0:
        if required:
            print(f"NG[{path.name}]: 0 件: {old[:60]!r}")
            sys.exit(1)
        return False
    path.write_text(text.replace(old, new), encoding="utf-8")
    return True


def bulk_protocol_tests():
    changed = 0
    for path in sorted(PROTO_TEST.glob("*Test.java")):
        if "498" in path.name:
            continue
        text = path.read_text(encoding="utf-8")
        original = text
        text = text.replace("List.of(573, 575, 578, 758,",
                            "List.of(498, 573, 575, 578, 758,")
        text = text.replace(
            "assertEquals(573, ClientAdapter.OLDEST_SUPPORTED_CLIENT_PROTOCOL);",
            "assertEquals(498, ClientAdapter.OLDEST_SUPPORTED_CLIENT_PROTOCOL);")
        text = text.replace(
            "assertEquals(ProtocolVersion.V1_15, ClientAdapter.OLDEST_SUPPORTED_CLIENT_PROTOCOL);",
            "assertEquals(ProtocolVersion.V1_14_4, ClientAdapter.OLDEST_SUPPORTED_CLIENT_PROTOCOL);")
        text = text.replace(
            "assertEquals(573, supported.get(0), supported.toString());",
            "assertEquals(498, supported.get(0), supported.toString());")
        text = text.replace(
            "assertEquals(575, supported.get(1), supported.toString());",
            "assertEquals(573, supported.get(1), supported.toString());")
        text = text.replace(
            "assertEquals(22, supported.size(), supported.toString());",
            "assertEquals(23, supported.size(), supported.toString());")
        if text != original:
            path.write_text(text, encoding="utf-8")
            changed += 1
    print(f"protocol テスト一括更新: {changed} ファイル")

    # ResourcePackMode の decideFull に 498 ペア（未設定）を足す。
    for name in ("ResourcePackMode578Test.java", "ResourcePackMode575Test.java"):
        sub_in(PROTO_TEST / name,
               "                // 573（未設定）\n                \"\", \"\",\n                false, \"\");",
               "                // 573（未設定）\n                \"\", \"\",\n                // 498（未設定）\n                \"\", \"\",\n                false, \"\");",
               count=1)
    sub_in(PROTO_TEST / "ResourcePackMode573Test.java",
           "                url573, sha1573,\n                false, \"\");",
           "                url573, sha1573,\n                // 498（未設定）\n                \"\", \"\",\n                false, \"\");",
           count=1)
    print("ResourcePackMode テストを更新した")


def core_additions():
    p = CORE_TEST / "AdapterDiscoveryTest.java"
    sub_in(p,
           "    @Test\n    void discoversThe115Adapter() {",
           "    @Test\n"
           "    void discoversThe1144Adapter() {\n"
           "        boolean found = StreamSupport.stream(\n"
           "                        ServiceLoader.load(VersionAdapter.class).spliterator(), false)\n"
           "                .anyMatch(adapter -> adapter.minecraftVersion().equals(\"1.14.4\")\n"
           "                        && adapter.protocolVersion() == 498);\n"
           "        assertTrue(found, \"Expected a 1.14.4 adapter on the test classpath\");\n"
           "\n"
           "        VersionAdapter adapter = DetergentServer.selectAdapter(\"1.14.4\");\n"
           "        assertEquals(\"1.14.4\", adapter.minecraftVersion());\n"
           "        assertEquals(498, adapter.protocolVersion());\n"
           "    }\n"
           "\n"
           "    @Test\n    void discoversThe115Adapter() {",
           count=1)

    p = CORE_TEST / "ServerProtocolWiringTest.java"
    sub_in(p,
           "    @Test\n    void minecraftVersion115SelectsInternalProtocol573() throws Exception {",
           "    @Test\n"
           "    void minecraftVersion1144SelectsInternalProtocol498() throws Exception {\n"
           "        DetergentServer server = serverWith(Map.of(\"minecraft-version\", \"1.14.4\"));\n"
           "        assertEquals(498, server.serverProtocol());\n"
           "    }\n"
           "\n"
           "    @Test\n    void minecraftVersion115SelectsInternalProtocol573() throws Exception {",
           count=1)

    p = CORE_TEST / "ResourcePackWiringTest.java"
    method573 = p.read_text(encoding="utf-8")
    start = method573.index("    @Test\n    void enabledConfigBuildsAndServes573Pack()")
    end = method573.index("    @Test\n    void enabledConfigBuildsAndServes575Pack()")
    block = method573[start:end]
    block498 = (block
                .replace("enabledConfigBuildsAndServes573Pack",
                         "enabledConfigBuildsAndServes498Pack")
                .replace("resourcePack573()", "resourcePack498()")
                .replace("pack573", "pack498")
                .replace("1.15 用（pack_format 5）", "1.14.4 用（pack_format 4）")
                .replace("detergentmc-pack-1.15.zip", "detergentmc-pack-1.14.4.zip"))
    text = method573[:start] + block498 + method573[start:]
    p.write_text(text, encoding="utf-8")

    p = CORE_TEST / "vanilla/DatapackGeneratorTest.java"
    sub_in(p,
           "    @Test\n    void packFormatFor115Is5() throws Exception {",
           "    @Test\n"
           "    void packFormatFor1144Is4() throws Exception {\n"
           "        // 実バニラ 1.14.4 jar の version.json：pack_version = 4。\n"
           "        assertEquals(4, DatapackGenerator.PACK_FORMAT_1_14_4, \"1.14.4 datapack format\");\n"
           "        assertEquals(4, DatapackGenerator.packFormatFor(\"1.14.4\"));\n"
           "        Path vanillaDir = tempDir.resolve(\"vanilla-1-14-4\");\n"
           "        Path file = new DatapackGenerator()\n"
           "                .build(vanillaDir, DatapackGenerator.packFormatFor(\"1.14.4\"));\n"
           "        Map<String, String> entries = readZip(file);\n"
           "        assertEquals(\"{\\\"pack\\\":{\\\"pack_format\\\":4,\\\"description\\\":\\\"DetergentMC\\\"}}\",\n"
           "                entries.get(\"pack.mcmeta\"));\n"
           "    }\n"
           "\n"
           "    @Test\n    void packFormatFor115Is5() throws Exception {",
           count=1)

    p = CORE_TEST / "vanilla/VanillaServerHostTest.java"
    sub_in(p,
           "            // 1.15.1 も同じ Java 8 世代。\n"
           "            assertEquals(Path.of(\"/opt/example-jdk-8\", \"bin\", \"java\").toString(),\n"
           "                    VanillaServerHost.javaBinaryFor(\"1.15.1\"));",
           "            // 1.15.1 も同じ Java 8 世代。\n"
           "            assertEquals(Path.of(\"/opt/example-jdk-8\", \"bin\", \"java\").toString(),\n"
           "                    VanillaServerHost.javaBinaryFor(\"1.15.1\"));\n"
           "            // 1.14.4 も同じ Java 8 世代。\n"
           "            assertEquals(Path.of(\"/opt/example-jdk-8\", \"bin\", \"java\").toString(),\n"
           "                    VanillaServerHost.javaBinaryFor(\"1.14.4\"));",
           count=1)
    print("core テストへ 498 を追加した")


def main():
    bulk_protocol_tests()
    core_additions()
    return 0


if __name__ == "__main__":
    sys.exit(main())
