#!/usr/bin/env python3
"""315 Stage D1b の配線を core 側へ適用する（316 の D1b と同型）。

対象：DetergentServer（resourcePack315 の保持・出し分け・外部設定・
ビルド・ゲッタ）と VanillaServerHost（Java 8 対象への 1.11 追加）。
settings.gradle／build-numbers.properties／core の build.gradle は
別途適用済み。各置換は期待件数を検証する。
"""

import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent.parent
failures = []


def patch(path, replacements):
    text = path.read_text(encoding="utf-8")
    for old, new, expected in replacements:
        count = text.count(old)
        if count != expected:
            failures.append(f"{path.name}: 期待 {expected} 件のところ {count} 件: {old[:70]!r}")
            continue
        text = text.replace(old, new)
    path.write_text(text, encoding="utf-8")
    print(f"{path.name}: 適用した")


CORE = ROOT / "detergentmc-core/src/main/java/io/detergentmc/core"
patch(CORE / "DetergentServer.java", [
    ("""    /** 316クライアントに送るリソースパック（pack_format 3）。無ければ null。 */
    private ResourcePack resourcePack316;""",
     """    /** 315クライアントに送るリソースパック（pack_format 3）。無ければ null。 */
    private ResourcePack resourcePack315;
    /** 316クライアントに送るリソースパック（pack_format 3）。無ければ null。 */
    private ResourcePack resourcePack316;""", 1),
    ("""        if (serverProtocol == ProtocolVersion.V1_11_2 && resourcePack316 != null) {
            return resourcePack316;
        }""",
     """        if (serverProtocol == ProtocolVersion.V1_11 && resourcePack315 != null) {
            return resourcePack315;
        }
        if (serverProtocol == ProtocolVersion.V1_11_2 && resourcePack316 != null) {
            return resourcePack316;
        }""", 1),
    ("""        proxy.setPack316(serverProtocol == ProtocolVersion.V1_11_2
                ? null : resourcePack316);""",
     """        proxy.setPack315(serverProtocol == ProtocolVersion.V1_11
                ? null : resourcePack315);
        proxy.setPack316(serverProtocol == ProtocolVersion.V1_11_2
                ? null : resourcePack316);""", 1),
    ("""                        config.getString("resource-pack.url-316", ""),
                        config.getString("resource-pack.sha1-316", ""),""",
     """                        config.getString("resource-pack.url-316", ""),
                        config.getString("resource-pack.sha1-316", ""),
                        config.getString("resource-pack.url-315", ""),
                        config.getString("resource-pack.sha1-315", ""),""", 1),
    ("""                    resourcePack = null;
                    resourcePack316 = null;""",
     """                    resourcePack = null;
                    resourcePack315 = null;
                    resourcePack316 = null;""", 1),
    ("""                resourcePack316 = decision.pack316();
                if (resourcePack316 != null) {""",
     """                resourcePack315 = decision.pack315();
                if (resourcePack315 != null) {
                    LOG.info(() -> translator.translate(
                            "resource-pack.external-enabled", resourcePack315.url()));
                }
                resourcePack316 = decision.pack316();
                if (resourcePack316 != null) {""", 1),
    ("""            ResourcePackBuilder.BuiltPack built316 = new ResourcePackBuilder()
                    .build316(workingDir.resolve("data").resolve("resource-pack"));""",
     """            ResourcePackBuilder.BuiltPack built315 = new ResourcePackBuilder()
                    .build315(workingDir.resolve("data").resolve("resource-pack"));
            ResourcePackBuilder.BuiltPack built316 = new ResourcePackBuilder()
                    .build316(workingDir.resolve("data").resolve("resource-pack"));""", 1),
    ("                    built, built316, built335, built338, built340,",
     "                    built, built315, built316, built335, built338, built340,", 1),
    ("""            resourcePack316 = resourcePackServer.pack316();""",
     """            resourcePack315 = resourcePackServer.pack315();
            resourcePack316 = resourcePackServer.pack316();""", 1),
    ("""        resourcePack = null;
        resourcePack316 = null;""",
     """        resourcePack = null;
        resourcePack315 = null;
        resourcePack316 = null;""", 1),
    ("""    /** The 1.11.2 resource pack offered to 316 clients, or {@code null}. */
    public ResourcePack resourcePack316() {""",
     """    /** The 1.11 resource pack offered to 315 clients, or {@code null}. */
    public ResourcePack resourcePack315() {
        return resourcePack315;
    }

    /** The 1.11.2 resource pack offered to 316 clients, or {@code null}. */
    public ResourcePack resourcePack316() {""", 1),
])

patch(CORE / "vanilla" / "VanillaServerHost.java", [
    ('|| "1.11.2".equals(minecraftVersion)) {',
     '|| "1.11.2".equals(minecraftVersion) || "1.11".equals(minecraftVersion)) {', 1),
])

if failures:
    print("失敗した置換:")
    for f in failures:
        print(" ", f)
    sys.exit(1)
print("全ての置換が期待件数で適用できた。")
