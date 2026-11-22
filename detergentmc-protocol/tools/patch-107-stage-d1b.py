#!/usr/bin/env python3
"""107 Stage D1 の追補：DetergentServer と VanillaServerHost への配線。

- DetergentServer：resourcePack107 フィールド、ホストパック選択
  （107 → 108 の順）、プロキシへの setPack107、外部モードの
  url-107 / sha1-107、ループバックの built107 構築と
  resourcePack107() ゲッタ。
- VanillaServerHost：1.9 を Java 8 起動の対象へ追加
  （内部サーバーは代表の 1.9）。
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
    ("""    /** 108クライアントに送るリソースパック（pack_format 2）。無ければ null。 */
    private ResourcePack resourcePack108;""",
     """    /** 107クライアントに送るリソースパック（pack_format 2）。無ければ null。 */
    private ResourcePack resourcePack107;
    /** 108クライアントに送るリソースパック（pack_format 2）。無ければ null。 */
    private ResourcePack resourcePack108;""", 1),
    ("""        if (serverProtocol == ProtocolVersion.V1_9_1 && resourcePack108 != null) {
            return resourcePack108;
        }""",
     """        if (serverProtocol == ProtocolVersion.V1_9 && resourcePack107 != null) {
            return resourcePack107;
        }
        if (serverProtocol == ProtocolVersion.V1_9 && resourcePack108 != null) {
            return resourcePack108;
        }
        if (serverProtocol == ProtocolVersion.V1_9 && resourcePack109 != null) {
            return resourcePack109;
        }
        if (serverProtocol == ProtocolVersion.V1_9 && resourcePack110 != null) {
            return resourcePack110;
        }
        if (serverProtocol == ProtocolVersion.V1_9_1 && resourcePack108 != null) {
            return resourcePack108;
        }""", 1),
    ("""        proxy.setPack108(serverProtocol == ProtocolVersion.V1_9_1
                ? null : resourcePack108);
        proxy.setPack109(serverProtocol == ProtocolVersion.V1_9_1
                ? null : resourcePack109);
        proxy.setPack110(serverProtocol == ProtocolVersion.V1_9_1
                ? null : resourcePack110);""",
     """        proxy.setPack107(serverProtocol == ProtocolVersion.V1_9
                ? null : resourcePack107);
        proxy.setPack108(serverProtocol == ProtocolVersion.V1_9
                ? null : resourcePack108);
        proxy.setPack109(serverProtocol == ProtocolVersion.V1_9
                ? null : resourcePack109);
        proxy.setPack110(serverProtocol == ProtocolVersion.V1_9
                ? null : resourcePack110);""", 1),
    ("""                        config.getString("resource-pack.url-108", ""),
                        config.getString("resource-pack.sha1-108", ""),
                        required, promptKey);""",
     """                        config.getString("resource-pack.url-108", ""),
                        config.getString("resource-pack.sha1-108", ""),
                        config.getString("resource-pack.url-107", ""),
                        config.getString("resource-pack.sha1-107", ""),
                        required, promptKey);""", 1),
    ("""                    resourcePack = null;
                    resourcePack108 = null;""",
     """                    resourcePack = null;
                    resourcePack107 = null;
                    resourcePack108 = null;""", 1),
    ("""                resourcePack108 = decision.pack108();""",
     """                resourcePack107 = decision.pack107();
                if (resourcePack107 != null) {
                    LOG.info(() -> translator.translate(
                            "resource-pack.external-enabled", resourcePack107.url()));
                }
                resourcePack108 = decision.pack108();""", 1),
    ("""            ResourcePackBuilder.BuiltPack built108 = new ResourcePackBuilder()
                    .build108(workingDir.resolve("data").resolve("resource-pack"));""",
     """            ResourcePackBuilder.BuiltPack built107 = new ResourcePackBuilder()
                    .build107(workingDir.resolve("data").resolve("resource-pack"));
            ResourcePackBuilder.BuiltPack built108 = new ResourcePackBuilder()
                    .build108(workingDir.resolve("data").resolve("resource-pack"));""", 1),
    ("built, built210, built110, built109, built108, built315,",
     "built, built210, built110, built109, built108, built107, built315,", 1),
    ("""            resourcePack108 = resourcePackServer.pack108();""",
     """            resourcePack107 = resourcePackServer.pack107();
            resourcePack108 = resourcePackServer.pack108();""", 1),
    ("""        resourcePack = null;
        resourcePack108 = null;""",
     """        resourcePack = null;
        resourcePack107 = null;
        resourcePack108 = null;""", 1),
    ("""    /** The 1.9.1 resource pack offered to 108 clients, or {@code null}. */
    public ResourcePack resourcePack108() {""",
     """    /** The 1.9 resource pack offered to 107 clients, or {@code null}. */
    public ResourcePack resourcePack107() {
        return resourcePack107;
    }

    /** The 1.9.1 resource pack offered to 108 clients, or {@code null}. */
    public ResourcePack resourcePack108() {""", 1),
])

# VanillaServerHost：Java 8 起動の対象へ 1.9 を追加する。
patch(CORE / "vanilla" / "VanillaServerHost.java", [
    ('|| "1.9.1".equals(minecraftVersion)) {',
     '|| "1.9.1".equals(minecraftVersion) || "1.9".equals(minecraftVersion)) {', 1),
])


if failures:
    print("失敗:")
    for f in failures:
        print(" -", f)
    sys.exit(1)
print("完了")
