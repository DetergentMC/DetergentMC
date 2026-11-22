# 注：DetergentServer の置換はアンカーずれのため一部失敗し、
# patch-316-stage-d1b.py（手書き版）で適用した。再実行しないこと。
#!/usr/bin/env python3
"""316 Stage D1 の配線を既存ファイルへ適用する（338 の D1 と同型）。

対象：settings.gradle、build-numbers.properties、detergentmc-core/build.gradle、
VanillaServerHost、RegistryRemapper、AdapterDiscoveryTest、DetergentServer。
各置換は期待件数を検証し、合わなければ失敗として報告する。
"""

import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent.parent
failures = []


def patch(relpath, replacements):
    path = ROOT / relpath
    text = path.read_text(encoding="utf-8")
    for old, new, expected in replacements:
        count = text.count(old)
        if count != expected:
            failures.append(f"{relpath}: 期待 {expected} 件のところ {count} 件: {old[:70]!r}")
            continue
        text = text.replace(old, new)
    path.write_text(text, encoding="utf-8")
    print(f"{relpath}: 適用した")


patch("settings.gradle", [
    ("include 'detergentmc-adapter-v1_12'",
     "include 'detergentmc-adapter-v1_11_2'\ninclude 'detergentmc-adapter-v1_12'", 1),
])

patch("build-numbers.properties", [
    ("1.12=1", "1.11.2=1\n1.12=1", 1),
])

patch("detergentmc-core/build.gradle", [
    ("    runtimeOnly project(':detergentmc-adapter-v1_12')",
     "    runtimeOnly project(':detergentmc-adapter-v1_11_2')\n    runtimeOnly project(':detergentmc-adapter-v1_12')", 1),
    ("    testImplementation project(':detergentmc-adapter-v1_12')",
     "    testImplementation project(':detergentmc-adapter-v1_11_2')\n    testImplementation project(':detergentmc-adapter-v1_12')", 1),
    ("    testRuntimeOnly project(':detergentmc-adapter-v1_12')",
     "    testRuntimeOnly project(':detergentmc-adapter-v1_11_2')\n    testRuntimeOnly project(':detergentmc-adapter-v1_12')", 1),
    ("        '1.12', '1.12.2',",
     "        '1.11.2', '1.12', '1.12.2',", 1),
])

patch("detergentmc-core/src/main/java/io/detergentmc/core/vanilla/VanillaServerHost.java", [
    ('|| "1.12".equals(minecraftVersion)) {',
     '|| "1.12".equals(minecraftVersion) || "1.11.2".equals(minecraftVersion)) {', 1),
])



patch("detergentmc-core/src/test/java/io/detergentmc/core/AdapterDiscoveryTest.java", [
    ("""    @Test
    void discoversThe112Adapter() {""",
     """    @Test
    void discoversThe1112Adapter() {
        boolean found = StreamSupport.stream(
                        ServiceLoader.load(VersionAdapter.class).spliterator(), false)
                .anyMatch(adapter -> adapter.minecraftVersion().equals("1.11.2")
                        && adapter.protocolVersion() == 316);
        assertTrue(found, "Expected a 1.11.2 adapter on the test classpath");

        VersionAdapter adapter = DetergentServer.selectAdapter("1.11.2");
        assertEquals("1.11.2", adapter.minecraftVersion());
        assertEquals(316, adapter.protocolVersion());
    }

    @Test
    void discoversThe112Adapter() {""", 1),
])

DS = "detergentmc-core/src/main/java/io/detergentmc/core/DetergentServer.java"
patch(DS, [
    # フィールド
    ("""    /** 338クライアントに送るリソースパック（pack_format 3）。無ければ null。 */
    private ResourcePack resourcePack338;""",
     """    /** 316クライアントに送るリソースパック（pack_format 3）。無ければ null。 */
    private ResourcePack resourcePack316;
    /** 335クライアントに送るリソースパック（pack_format 3）。無ければ null。 */
    private ResourcePack resourcePack335;""", 1),
    # hostResourcePack の 335 分岐
    ("""        if (serverProtocol == ProtocolVersion.V1_12 && resourcePack338 != null) {
            return resourcePack338;
        }""",
     """        if (serverProtocol == ProtocolVersion.V1_11_2 && resourcePack335 != null) {
            return resourcePack335;
        }
        if (serverProtocol == ProtocolVersion.V1_12 && resourcePack338 != null) {
            return resourcePack338;
        }""", 1),
    # proxy.setPack316
    ("""        proxy.setPack335(serverProtocol == ProtocolVersion.V1_12
                ? null : resourcePack338);""",
     """        proxy.setPack316(serverProtocol == ProtocolVersion.V1_11_2
                ? null : resourcePack335);
        proxy.setPack335(serverProtocol == ProtocolVersion.V1_12
                ? null : resourcePack338);""", 1),
    # decide() へ url-335/sha1-335 を追加（338 ペアの後）
    ("""                        config.getString("resource-pack.url-338", ""),
                        config.getString("resource-pack.sha1-338", ""),
                        required, promptKey);""",
     """                        config.getString("resource-pack.url-338", ""),
                        config.getString("resource-pack.sha1-338", ""),
                        config.getString("resource-pack.url-335", ""),
                        config.getString("resource-pack.sha1-335", ""),
                        required, promptKey);""", 1),
    # 問題時のリセット
    ("""                    resourcePack = null;
                    resourcePack338 = null;""",
     """                    resourcePack = null;
                    resourcePack335 = null;
                    resourcePack338 = null;""", 1),
    # decision からの取り出し
    ("""                resourcePack338 = decision.pack335();""",
     """                resourcePack335 = decision.pack316();
                if (resourcePack335 != null) {
                    LOG.info(() -> translator.translate(
                            "resource-pack.external-enabled", resourcePack335.url()));
                }
                resourcePack338 = decision.pack335();""", 1),
    # built316 のビルド
    ("""            ResourcePackBuilder.BuiltPack built335 = new ResourcePackBuilder()
                    .build335(workingDir.resolve("data").resolve("resource-pack"));""",
     """            ResourcePackBuilder.BuiltPack built316 = new ResourcePackBuilder()
                    .build316(workingDir.resolve("data").resolve("resource-pack"));
            ResourcePackBuilder.BuiltPack built335 = new ResourcePackBuilder()
                    .build335(workingDir.resolve("data").resolve("resource-pack"));""", 1),
    # ResourcePackServer の全パックコンストラクタ引数
    ("                    built, built335, built340,",
     "                    built, built316, built335, built340,", 1),
    # サーバーからの取り出し
    ("""            resourcePack338 = resourcePackServer.pack335();""",
     """            resourcePack335 = resourcePackServer.pack316();
            resourcePack338 = resourcePackServer.pack335();""", 1),
    # stop 時のリセット
    ("""        resourcePack = null;
        resourcePack338 = null;""",
     """        resourcePack = null;
        resourcePack335 = null;
        resourcePack338 = null;""", 1),
    # getter
    ("""    /** The 1.12 resource pack offered to 338 clients, or {@code null}. */
    public ResourcePack resourcePack338() {""",
     """    /** The 1.11.2 resource pack offered to 335 clients, or {@code null}. */
    public ResourcePack resourcePack335() {
        return resourcePack335;
    }

    /** The 1.12 resource pack offered to 338 clients, or {@code null}. */
    public ResourcePack resourcePack338() {""", 1),
])

if failures:
    print("失敗した置換:")
    for f in failures:
        print(" ", f)
    sys.exit(1)
print("全ての置換が期待件数で適用できた。")
