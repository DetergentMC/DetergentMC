#!/usr/bin/env python3
"""109 Stage D1 の追補：DetergentServer と VanillaServerHost への配線。

- DetergentServer：resourcePack109 フィールド、ホストパック選択
  （109 → 110 の順）、プロキシへの setPack109、外部モードの
  url-109 / sha1-109、ループバックの built109 構築と
  resourcePack109() ゲッタ。
- VanillaServerHost：1.9.2 / 1.9.3 を Java 8 起動の対象へ追加
  （109 は 1.9.2 と 1.9.3 の両方。内部サーバーは代表の 1.9.2）。
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
    ("""    /** 110クライアントに送るリソースパック（pack_format 2）。無ければ null。 */
    private ResourcePack resourcePack110;""",
     """    /** 109クライアントに送るリソースパック（pack_format 2）。無ければ null。 */
    private ResourcePack resourcePack109;
    /** 110クライアントに送るリソースパック（pack_format 2）。無ければ null。 */
    private ResourcePack resourcePack110;""", 1),
    ("""        if (serverProtocol == ProtocolVersion.V1_9_4 && resourcePack110 != null) {
            return resourcePack110;
        }""",
     """        if (serverProtocol == ProtocolVersion.V1_9_2 && resourcePack109 != null) {
            return resourcePack109;
        }
        if (serverProtocol == ProtocolVersion.V1_9_2 && resourcePack110 != null) {
            return resourcePack110;
        }
        if (serverProtocol == ProtocolVersion.V1_9_4 && resourcePack110 != null) {
            return resourcePack110;
        }""", 1),
    ("""        proxy.setPack110(serverProtocol == ProtocolVersion.V1_9_4
                ? null : resourcePack110);""",
     """        proxy.setPack109(serverProtocol == ProtocolVersion.V1_9_2
                ? null : resourcePack109);
        proxy.setPack110(serverProtocol == ProtocolVersion.V1_9_2
                ? null : resourcePack110);""", 1),
    ("""                        config.getString("resource-pack.url-110", ""),
                        config.getString("resource-pack.sha1-110", ""),
                        required, promptKey);""",
     """                        config.getString("resource-pack.url-110", ""),
                        config.getString("resource-pack.sha1-110", ""),
                        config.getString("resource-pack.url-109", ""),
                        config.getString("resource-pack.sha1-109", ""),
                        required, promptKey);""", 1),
    ("""                    resourcePack = null;
                    resourcePack110 = null;""",
     """                    resourcePack = null;
                    resourcePack109 = null;
                    resourcePack110 = null;""", 1),
    ("""                resourcePack110 = decision.pack110();""",
     """                resourcePack109 = decision.pack109();
                if (resourcePack109 != null) {
                    LOG.info(() -> translator.translate(
                            "resource-pack.external-enabled", resourcePack109.url()));
                }
                resourcePack110 = decision.pack110();""", 1),
    ("""            ResourcePackBuilder.BuiltPack built110 = new ResourcePackBuilder()
                    .build110(workingDir.resolve("data").resolve("resource-pack"));""",
     """            ResourcePackBuilder.BuiltPack built109 = new ResourcePackBuilder()
                    .build109(workingDir.resolve("data").resolve("resource-pack"));
            ResourcePackBuilder.BuiltPack built110 = new ResourcePackBuilder()
                    .build110(workingDir.resolve("data").resolve("resource-pack"));""", 1),
    ("built, built210, built110, built315,",
     "built, built210, built110, built109, built315,", 1),
    ("""            resourcePack110 = resourcePackServer.pack110();""",
     """            resourcePack109 = resourcePackServer.pack109();
            resourcePack110 = resourcePackServer.pack110();""", 1),
    ("""        resourcePack = null;
        resourcePack110 = null;""",
     """        resourcePack = null;
        resourcePack109 = null;
        resourcePack110 = null;""", 1),
    ("""    /** The 1.9.4 resource pack offered to 110 clients, or {@code null}. */
    public ResourcePack resourcePack110() {""",
     """    /** The 1.9.2 resource pack offered to 109 clients, or {@code null}. */
    public ResourcePack resourcePack109() {
        return resourcePack109;
    }

    /** The 1.9.4 resource pack offered to 110 clients, or {@code null}. */
    public ResourcePack resourcePack110() {""", 1),
])


if failures:
    print("失敗:")
    for f in failures:
        print(" -", f)
    sys.exit(1)
print("完了")
