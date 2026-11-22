#!/usr/bin/env python3
"""110 Stage D1 の追補：DetergentServer と VanillaServerHost への配線。

- DetergentServer：resourcePack110 フィールド、ホストパック選択
  （110 → 210 の順）、プロキシへの setPack110、外部モードの
  url-110 / sha1-110、ループバックの built110 構築と
  resourcePack110() ゲッタ。
- VanillaServerHost：1.9.4 / 1.9.3 を Java 8 起動の対象へ追加
  （110 は 1.9.4 と 1.9.3 の両方。内部サーバーは代表の 1.9.4）。
各置換は期待件数を検証し、合わなければ失敗として報告する。
"""

import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent.parent
CORE = ROOT / "detergentmc-core/src/main/java/io/detergentmc/core"
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


patch(CORE / "DetergentServer.java", [
    ("""    /** 210クライアントに送るリソースパック（pack_format 3）。無ければ null。 */
    private ResourcePack resourcePack210;""",
     """    /** 110クライアントに送るリソースパック（pack_format 2）。無ければ null。 */
    private ResourcePack resourcePack110;
    /** 210クライアントに送るリソースパック（pack_format 3）。無ければ null。 */
    private ResourcePack resourcePack210;""", 1),
    ("""        if (serverProtocol == ProtocolVersion.V1_10_2 && resourcePack210 != null) {
            return resourcePack210;
        }""",
     """        if (serverProtocol == ProtocolVersion.V1_9_4 && resourcePack110 != null) {
            return resourcePack110;
        }
        if (serverProtocol == ProtocolVersion.V1_9_4 && resourcePack210 != null) {
            return resourcePack210;
        }
        if (serverProtocol == ProtocolVersion.V1_10_2 && resourcePack210 != null) {
            return resourcePack210;
        }""", 1),
    ("""        proxy.setPack210(serverProtocol == ProtocolVersion.V1_10_2
                ? null : resourcePack210);""",
     """        proxy.setPack110(serverProtocol == ProtocolVersion.V1_9_4
                ? null : resourcePack110);
        proxy.setPack210(serverProtocol == ProtocolVersion.V1_9_4
                ? null : resourcePack210);""", 1),
    ("""                        config.getString("resource-pack.url-210", ""),
                        config.getString("resource-pack.sha1-210", ""),
                        required, promptKey);""",
     """                        config.getString("resource-pack.url-210", ""),
                        config.getString("resource-pack.sha1-210", ""),
                        config.getString("resource-pack.url-110", ""),
                        config.getString("resource-pack.sha1-110", ""),
                        required, promptKey);""", 1),
    ("""                    resourcePack = null;
                    resourcePack210 = null;""",
     """                    resourcePack = null;
                    resourcePack110 = null;
                    resourcePack210 = null;""", 1),
    ("""                resourcePack210 = decision.pack210();""",
     """                resourcePack110 = decision.pack110();
                if (resourcePack110 != null) {
                    LOG.info(() -> translator.translate(
                            "resource-pack.external-enabled", resourcePack110.url()));
                }
                resourcePack210 = decision.pack210();""", 1),
    ("""            ResourcePackBuilder.BuiltPack built210 = new ResourcePackBuilder()
                    .build210(workingDir.resolve("data").resolve("resource-pack"));""",
     """            ResourcePackBuilder.BuiltPack built110 = new ResourcePackBuilder()
                    .build110(workingDir.resolve("data").resolve("resource-pack"));
            ResourcePackBuilder.BuiltPack built210 = new ResourcePackBuilder()
                    .build210(workingDir.resolve("data").resolve("resource-pack"));""", 1),
    ("built, built210, built315,",
     "built, built210, built110, built315,", 1),
    ("""            resourcePack210 = resourcePackServer.pack210();""",
     """            resourcePack110 = resourcePackServer.pack110();
            resourcePack210 = resourcePackServer.pack210();""", 1),
    ("""        resourcePack = null;
        resourcePack210 = null;""",
     """        resourcePack = null;
        resourcePack110 = null;
        resourcePack210 = null;""", 1),
    ("""    /** The 1.10.2 resource pack offered to 210 clients, or {@code null}. */
    public ResourcePack resourcePack210() {""",
     """    /** The 1.9.4 resource pack offered to 110 clients, or {@code null}. */
    public ResourcePack resourcePack110() {
        return resourcePack110;
    }

    /** The 1.10.2 resource pack offered to 210 clients, or {@code null}. */
    public ResourcePack resourcePack210() {""", 1),
])

patch(CORE / "vanilla" / "VanillaServerHost.java", [
    ('|| "1.10.2".equals(minecraftVersion) || "1.10.1".equals(minecraftVersion) || "1.10".equals(minecraftVersion)) {',
     '|| "1.10.2".equals(minecraftVersion) || "1.10.1".equals(minecraftVersion) || "1.10".equals(minecraftVersion) || "1.9.4".equals(minecraftVersion) || "1.9.3".equals(minecraftVersion)) {', 1),
])

if failures:
    print("失敗した置換:")
    for f in failures:
        print(" ", f)
    sys.exit(1)
print("全ての置換が期待件数で適用できた。")
