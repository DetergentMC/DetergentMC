#!/usr/bin/env python3
"""DetergentServer に resourcePack4（1.7.2）を配線する（resourcePack5 の鏡）。

フィールド、hostResourcePack のフォールバック、proxy.setPack4、
decide() への url-4／sha1-4、decision.pack4() の取り出し、
build4() の構築、ResourcePackServer への built4 受け渡し、
pack4() の取り出し、リセット時の null 化、ゲッター。
冪等ではない（実行は1回だけ）。件数が合わなければ中止する。
"""
from pathlib import Path

P = Path(__file__).resolve().parent.parent / "src" / "main" / "java" / "io" / "detergentmc" / "core" / "DetergentServer.java"
src = P.read_text(encoding="utf-8")


def rep(old, new, expect=1):
    global src
    n = src.count(old)
    assert n == expect, f"{n} != {expect}: {old[:70]}"
    src = src.replace(old, new)
    print(f"ok {expect} x {old[:60]}")


rep("    private ResourcePack resourcePack5;",
    "    private ResourcePack resourcePack5;\n    private ResourcePack resourcePack4;")
rep("""        if (serverProtocol == ProtocolVersion.V1_7_10 && resourcePack5 != null) {
            return resourcePack5;
        }
        if (serverProtocol == ProtocolVersion.V1_7_10 && resourcePack47 != null) {""",
    """        if (serverProtocol == ProtocolVersion.V1_7_2 && resourcePack4 != null) {
            return resourcePack4;
        }
        if (serverProtocol == ProtocolVersion.V1_7_2 && resourcePack5 != null) {
            return resourcePack5;
        }
        if (serverProtocol == ProtocolVersion.V1_7_2 && resourcePack47 != null) {
            return resourcePack47;
        }
        if (serverProtocol == ProtocolVersion.V1_7_10 && resourcePack5 != null) {
            return resourcePack5;
        }
        if (serverProtocol == ProtocolVersion.V1_7_10 && resourcePack47 != null) {""")
rep("""        proxy.setPack5(serverProtocol == ProtocolVersion.V1_7_10
                ? null : resourcePack5);""",
    """        proxy.setPack5(serverProtocol == ProtocolVersion.V1_7_10
                ? null : resourcePack5);
        proxy.setPack4(serverProtocol == ProtocolVersion.V1_7_2
                ? null : resourcePack4);""")
rep("""                        config.getString("resource-pack.url-5", ""),
                        config.getString("resource-pack.sha1-5", ""),""",
    """                        config.getString("resource-pack.url-5", ""),
                        config.getString("resource-pack.sha1-5", ""),
                        config.getString("resource-pack.url-4", ""),
                        config.getString("resource-pack.sha1-4", ""),""")
rep("""                    resourcePack47 = null;
                    resourcePack5 = null;""",
    """                    resourcePack47 = null;
                    resourcePack5 = null;
                    resourcePack4 = null;""")
rep("""                resourcePack5 = decision.pack5();
                if (resourcePack5 != null) {
                    LOG.info(() -> translator.translate(
                            "resource-pack.external-enabled", resourcePack5.url()));
                }""",
    """                resourcePack5 = decision.pack5();
                if (resourcePack5 != null) {
                    LOG.info(() -> translator.translate(
                            "resource-pack.external-enabled", resourcePack5.url()));
                }
                resourcePack4 = decision.pack4();
                if (resourcePack4 != null) {
                    LOG.info(() -> translator.translate(
                            "resource-pack.external-enabled", resourcePack4.url()));
                }""")
rep("""            ResourcePackBuilder.BuiltPack built5 = new ResourcePackBuilder()
                    .build5(workingDir.resolve("data").resolve("resource-pack"));""",
    """            ResourcePackBuilder.BuiltPack built5 = new ResourcePackBuilder()
                    .build5(workingDir.resolve("data").resolve("resource-pack"));
            ResourcePackBuilder.BuiltPack built4 = new ResourcePackBuilder()
                    .build4(workingDir.resolve("data").resolve("resource-pack"));""")
rep("built, built210, built110, built109, built108, built107, built47, built5, built315,",
    "built, built210, built110, built109, built108, built107, built47, built5, built4, built315,")
rep("""            resourcePack5 = resourcePackServer.pack5();""",
    """            resourcePack5 = resourcePackServer.pack5();
            resourcePack4 = resourcePackServer.pack4();""")
rep("""        resourcePack47 = null;
        resourcePack5 = null;""",
    """        resourcePack47 = null;
        resourcePack5 = null;
        resourcePack4 = null;""")
rep("""    /** The 1.7.10 resource pack offered to 5 clients, or {@code null}. */
    public ResourcePack resourcePack5() {
        return resourcePack5;
    }""",
    """    /** The 1.7.10 resource pack offered to 5 clients, or {@code null}. */
    public ResourcePack resourcePack5() {
        return resourcePack5;
    }

    /** The 1.7.2 resource pack offered to 4 clients, or {@code null}. */
    public ResourcePack resourcePack4() {
        return resourcePack4;
    }""")

P.write_text(src, encoding="utf-8")
print("written", P)
