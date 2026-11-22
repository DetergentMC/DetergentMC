#!/usr/bin/env python3
"""108 Stage D1 の追補：DetergentServer と VanillaServerHost への配線。

- DetergentServer：resourcePack108 フィールド、ホストパック選択
  （108 → 109 の順）、プロキシへの setPack108、外部モードの
  url-108 / sha1-108、ループバックの built108 構築と
  resourcePack108() ゲッタ。
- VanillaServerHost：1.9.1 / 1.9.3 を Java 8 起動の対象へ追加
  （108 は 1.9.1 と 1.9.3 の両方。内部サーバーは代表の 1.9.1）。
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
    ("""    /** 109クライアントに送るリソースパック（pack_format 2）。無ければ null。 */
    private ResourcePack resourcePack109;""",
     """    /** 108クライアントに送るリソースパック（pack_format 2）。無ければ null。 */
    private ResourcePack resourcePack108;
    /** 109クライアントに送るリソースパック（pack_format 2）。無ければ null。 */
    private ResourcePack resourcePack109;""", 1),
    ("""        if (serverProtocol == ProtocolVersion.V1_9_2 && resourcePack109 != null) {
            return resourcePack109;
        }""",
     """        if (serverProtocol == ProtocolVersion.V1_9_1 && resourcePack108 != null) {
            return resourcePack108;
        }
        if (serverProtocol == ProtocolVersion.V1_9_1 && resourcePack109 != null) {
            return resourcePack109;
        }
        if (serverProtocol == ProtocolVersion.V1_9_1 && resourcePack110 != null) {
            return resourcePack110;
        }
        if (serverProtocol == ProtocolVersion.V1_9_2 && resourcePack109 != null) {
            return resourcePack109;
        }""", 1),
    ("""        proxy.setPack109(serverProtocol == ProtocolVersion.V1_9_2
                ? null : resourcePack109);
        proxy.setPack110(serverProtocol == ProtocolVersion.V1_9_2
                ? null : resourcePack110);""",
     """        proxy.setPack108(serverProtocol == ProtocolVersion.V1_9_1
                ? null : resourcePack108);
        proxy.setPack109(serverProtocol == ProtocolVersion.V1_9_1
                ? null : resourcePack109);
        proxy.setPack110(serverProtocol == ProtocolVersion.V1_9_1
                ? null : resourcePack110);""", 1),
    ("""                        config.getString("resource-pack.url-109", ""),
                        config.getString("resource-pack.sha1-109", ""),
                        required, promptKey);""",
     """                        config.getString("resource-pack.url-109", ""),
                        config.getString("resource-pack.sha1-109", ""),
                        config.getString("resource-pack.url-108", ""),
                        config.getString("resource-pack.sha1-108", ""),
                        required, promptKey);""", 1),
    ("""                    resourcePack = null;
                    resourcePack109 = null;""",
     """                    resourcePack = null;
                    resourcePack108 = null;
                    resourcePack109 = null;""", 1),
    ("""                resourcePack109 = decision.pack109();""",
     """                resourcePack108 = decision.pack108();
                if (resourcePack108 != null) {
                    LOG.info(() -> translator.translate(
                            "resource-pack.external-enabled", resourcePack108.url()));
                }
                resourcePack109 = decision.pack109();""", 1),
    ("""            ResourcePackBuilder.BuiltPack built109 = new ResourcePackBuilder()
                    .build109(workingDir.resolve("data").resolve("resource-pack"));""",
     """            ResourcePackBuilder.BuiltPack built108 = new ResourcePackBuilder()
                    .build108(workingDir.resolve("data").resolve("resource-pack"));
            ResourcePackBuilder.BuiltPack built109 = new ResourcePackBuilder()
                    .build109(workingDir.resolve("data").resolve("resource-pack"));""", 1),
    ("built, built210, built110, built109, built315,",
     "built, built210, built110, built109, built108, built315,", 1),
    ("""            resourcePack109 = resourcePackServer.pack109();""",
     """            resourcePack108 = resourcePackServer.pack108();
            resourcePack109 = resourcePackServer.pack109();""", 1),
    ("""        resourcePack = null;
        resourcePack109 = null;""",
     """        resourcePack = null;
        resourcePack108 = null;
        resourcePack109 = null;""", 1),
    ("""    /** The 1.9.2 resource pack offered to 109 clients, or {@code null}. */
    public ResourcePack resourcePack109() {""",
     """    /** The 1.9.1 resource pack offered to 108 clients, or {@code null}. */
    public ResourcePack resourcePack108() {
        return resourcePack108;
    }

    /** The 1.9.2 resource pack offered to 109 clients, or {@code null}. */
    public ResourcePack resourcePack109() {""", 1),
])

# VanillaServerHost：Java 8 起動の対象へ 1.9.1 を追加する。
patch(CORE / "vanilla" / "VanillaServerHost.java", [
    ('|| "1.9.2".equals(minecraftVersion)) {',
     '|| "1.9.2".equals(minecraftVersion) || "1.9.1".equals(minecraftVersion)) {', 1),
])


if failures:
    print("失敗:")
    for f in failures:
        print(" -", f)
    sys.exit(1)
print("完了")
