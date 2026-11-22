#!/usr/bin/env python3
"""210 Stage D1b の配線を core 側へ適用する（210 の D1b と同型）。

対象：DetergentServer（resourcePack210 の保持・出し分け・外部設定・
ビルド・ゲッタ）と VanillaServerHost（Java 8 対象への 1.10.2 追加）。
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
    ("""    /** 315クライアントに送るリソースパック（pack_format 3）。無ければ null。 */
    private ResourcePack resourcePack315;""",
     """    /** 210クライアントに送るリソースパック（pack_format 3）。無ければ null。 */
    private ResourcePack resourcePack210;
    /** 315クライアントに送るリソースパック（pack_format 3）。無ければ null。 */
    private ResourcePack resourcePack315;""", 1),
    ("""        if (serverProtocol == ProtocolVersion.V1_11 && resourcePack315 != null) {
            return resourcePack315;
        }""",
     """        if (serverProtocol == ProtocolVersion.V1_10_2 && resourcePack210 != null) {
            return resourcePack210;
        }
        if (serverProtocol == ProtocolVersion.V1_10_2 && resourcePack315 != null) {
            return resourcePack315;
        }""", 1),
    ("""        proxy.setPack315(serverProtocol == ProtocolVersion.V1_11
                ? null : resourcePack315);""",
     """        proxy.setPack210(serverProtocol == ProtocolVersion.V1_10_2
                ? null : resourcePack210);
        proxy.setPack315(serverProtocol == ProtocolVersion.V1_10_2
                ? null : resourcePack315);""", 1),
    ("""                        config.getString("resource-pack.url-315", ""),
                        config.getString("resource-pack.sha1-315", ""),""",
     """                        config.getString("resource-pack.url-315", ""),
                        config.getString("resource-pack.sha1-315", ""),
                        config.getString("resource-pack.url-210", ""),
                        config.getString("resource-pack.sha1-210", ""),""", 1),
    ("""                    resourcePack = null;
                    resourcePack315 = null;""",
     """                    resourcePack = null;
                    resourcePack210 = null;
                    resourcePack315 = null;""", 1),
    ("""                resourcePack315 = decision.pack315();
                if (resourcePack315 != null) {""",
     """                resourcePack210 = decision.pack210();
                if (resourcePack210 != null) {
                    LOG.info(() -> translator.translate(
                            "resource-pack.external-enabled", resourcePack210.url()));
                }
                resourcePack315 = decision.pack315();
                if (resourcePack315 != null) {""", 1),
    ("""            ResourcePackBuilder.BuiltPack built315 = new ResourcePackBuilder()
                    .build315(workingDir.resolve("data").resolve("resource-pack"));""",
     """            ResourcePackBuilder.BuiltPack built210 = new ResourcePackBuilder()
                    .build210(workingDir.resolve("data").resolve("resource-pack"));
            ResourcePackBuilder.BuiltPack built315 = new ResourcePackBuilder()
                    .build315(workingDir.resolve("data").resolve("resource-pack"));""", 1),
    ("                    built, built315, built335, built338, built340,",
     "                    built, built210, built315, built335, built338, built340,", 1),
    ("""            resourcePack315 = resourcePackServer.pack315();""",
     """            resourcePack210 = resourcePackServer.pack210();
            resourcePack315 = resourcePackServer.pack315();""", 1),
    ("""        resourcePack = null;
        resourcePack315 = null;""",
     """        resourcePack = null;
        resourcePack210 = null;
        resourcePack315 = null;""", 1),
    ("""    /** The 1.11 resource pack offered to 315 clients, or {@code null}. */
    public ResourcePack resourcePack315() {""",
     """    /** The 1.10.2 resource pack offered to 210 clients, or {@code null}. */
    public ResourcePack resourcePack210() {
        return resourcePack210;
    }

    /** The 1.11 resource pack offered to 315 clients, or {@code null}. */
    public ResourcePack resourcePack315() {""", 1),
])

patch(CORE / "vanilla" / "VanillaServerHost.java", [
    ('|| "1.11".equals(minecraftVersion)) {',
     '|| "1.11".equals(minecraftVersion) || "1.10.2".equals(minecraftVersion)) {', 1),
])

if failures:
    print("失敗した置換:")
    for f in failures:
        print(" ", f)
    sys.exit(1)
print("全ての置換が期待件数で適用できた。")
