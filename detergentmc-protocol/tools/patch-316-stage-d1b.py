#!/usr/bin/env python3
"""316 Stage D1b: DetergentServer への 316 配線（現行ファイルの実文で
アンカーを取った手書き版。335 の D1 と同型）。"""

import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent.parent
DS = ROOT / "detergentmc-core/src/main/java/io/detergentmc/core/DetergentServer.java"
text = DS.read_text(encoding="utf-8")
failures = []

REPLACEMENTS = [
    # フィールド
    ("""    /** 335クライアントに送るリソースパック（pack_format 3）。無ければ null。 */
    private ResourcePack resourcePack335;""",
     """    /** 316クライアントに送るリソースパック（pack_format 3）。無ければ null。 */
    private ResourcePack resourcePack316;
    /** 335クライアントに送るリソースパック（pack_format 3）。無ければ null。 */
    private ResourcePack resourcePack335;""", 1),
    # hostResourcePack の 316 分岐
    ("""        if (serverProtocol == ProtocolVersion.V1_12 && resourcePack335 != null) {
            return resourcePack335;
        }""",
     """        if (serverProtocol == ProtocolVersion.V1_11_2 && resourcePack316 != null) {
            return resourcePack316;
        }
        if (serverProtocol == ProtocolVersion.V1_12 && resourcePack335 != null) {
            return resourcePack335;
        }""", 1),
    # proxy.setPack316
    ("""        proxy.setPack335(serverProtocol == ProtocolVersion.V1_12
                ? null : resourcePack335);""",
     """        proxy.setPack316(serverProtocol == ProtocolVersion.V1_11_2
                ? null : resourcePack316);
        proxy.setPack335(serverProtocol == ProtocolVersion.V1_12
                ? null : resourcePack335);""", 1),
    # decide() へ url-316/sha1-316 を追加（335 ペアの後＝署名順）
    ("""                        config.getString("resource-pack.url-335", ""),
                        config.getString("resource-pack.sha1-335", ""),
                        required, promptKey);""",
     """                        config.getString("resource-pack.url-335", ""),
                        config.getString("resource-pack.sha1-335", ""),
                        config.getString("resource-pack.url-316", ""),
                        config.getString("resource-pack.sha1-316", ""),
                        required, promptKey);""", 1),
    # 問題時のリセット
    ("""                    resourcePack = null;
                    resourcePack335 = null;""",
     """                    resourcePack = null;
                    resourcePack316 = null;
                    resourcePack335 = null;""", 1),
    # decision からの取り出し
    ("""                resourcePack335 = decision.pack335();
                if (resourcePack335 != null) {
                    LOG.info(() -> translator.translate(
                            "resource-pack.external-enabled", resourcePack335.url()));
                }""",
     """                resourcePack316 = decision.pack316();
                if (resourcePack316 != null) {
                    LOG.info(() -> translator.translate(
                            "resource-pack.external-enabled", resourcePack316.url()));
                }
                resourcePack335 = decision.pack335();
                if (resourcePack335 != null) {
                    LOG.info(() -> translator.translate(
                            "resource-pack.external-enabled", resourcePack335.url()));
                }""", 1),
    # built316 のビルド
    ("""            ResourcePackBuilder.BuiltPack built335 = new ResourcePackBuilder()
                    .build335(workingDir.resolve("data").resolve("resource-pack"));""",
     """            ResourcePackBuilder.BuiltPack built316 = new ResourcePackBuilder()
                    .build316(workingDir.resolve("data").resolve("resource-pack"));
            ResourcePackBuilder.BuiltPack built335 = new ResourcePackBuilder()
                    .build335(workingDir.resolve("data").resolve("resource-pack"));""", 1),
    # ResourcePackServer の全パックコンストラクタ引数
    ("                    built, built335, built338,",
     "                    built, built316, built335, built338,", 1),
    # サーバーからの取り出し
    ("""            resourcePack335 = resourcePackServer.pack335();""",
     """            resourcePack316 = resourcePackServer.pack316();
            resourcePack335 = resourcePackServer.pack335();""", 1),
    # stop 時のリセット
    ("""        resourcePack = null;
        resourcePack335 = null;""",
     """        resourcePack = null;
        resourcePack316 = null;
        resourcePack335 = null;""", 1),
    # getter
    ("""    /** The 1.12 resource pack offered to 335 clients, or {@code null}. */
    public ResourcePack resourcePack335() {""",
     """    /** The 1.11.2 resource pack offered to 316 clients, or {@code null}. */
    public ResourcePack resourcePack316() {
        return resourcePack316;
    }

    /** The 1.12 resource pack offered to 335 clients, or {@code null}. */
    public ResourcePack resourcePack335() {""", 1),
]

for old, new, expected in REPLACEMENTS:
    count = text.count(old)
    if count != expected:
        failures.append(f"期待 {expected} 件のところ {count} 件: {old[:70]!r}")
        continue
    text = text.replace(old, new)
DS.write_text(text, encoding="utf-8")
print("DetergentServer.java: 適用した")
if failures:
    print("失敗した置換:")
    for f in failures:
        print(" ", f)
    sys.exit(1)
print("全ての置換が期待件数で適用できた。")
