#!/usr/bin/env python3
"""DetergentServer.java に 575（1.15.1）のリソースパック配線を足す。

578 のときと同じ流儀：resourcePack575 フィールド、設定キー
resource-pack.url-575／sha1-575、build575、ResourcePackServer への
built575 受け渡し、内部サーバーが 575 のときのネイティブ扱い
（hostResourcePack と proxy.setPack575(null)）。

使い方: python3 tools/patch-575-detergentserver.py
（detergentmc-core/tools/ に置くこと）
書き換えた後は必ず git diff で内容を確認してからコミットすること。
"""
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
P = ROOT / "src/main/java/io/detergentmc/core/DetergentServer.java"


def sub(text, old, new, count):
    found = text.count(old)
    if found != count:
        print(f"NG: {found} 件（期待 {count}）: {old[:70]!r}")
        sys.exit(1)
    return text.replace(old, new)


def main():
    text = P.read_text(encoding="utf-8")
    if "resourcePack575" in text:
        print("skip（既存）: 575 配線はすでにある")
        return 0

    # 1. フィールド。
    text = sub(text,
               "    private ResourcePack resourcePack578;",
               "    private ResourcePack resourcePack575;\n"
               "    private ResourcePack resourcePack578;", 1)

    # 2. decide() へ url-575／sha1-575 を渡す（578 の直後）。
    text = sub(text,
               '                        config.getString("resource-pack.url-578", ""),\n'
               '                        config.getString("resource-pack.sha1-578", ""),\n'
               '                        required, promptKey);',
               '                        config.getString("resource-pack.url-578", ""),\n'
               '                        config.getString("resource-pack.sha1-578", ""),\n'
               '                        config.getString("resource-pack.url-575", ""),\n'
               '                        config.getString("resource-pack.sha1-575", ""),\n'
               '                        required, promptKey);', 1)

    # 3. EXTERNAL：pack575 を受け取り、失敗時は null リセットにも含める。
    text = sub(text,
               "                resourcePack578 = decision.pack578();",
               "                resourcePack575 = decision.pack575();\n"
               "                if (resourcePack575 != null) {\n"
               "                    LOG.info(() -> translator.translate(\n"
               '                            "resource-pack.external-enabled", resourcePack575.url()));\n'
               "                }\n"
               "                resourcePack578 = decision.pack578();", 1)
    text = sub(text,
               "                    resourcePack = null;\n                    resourcePack578 = null;",
               "                    resourcePack = null;\n                    resourcePack575 = null;\n                    resourcePack578 = null;", 1)

    # 4. hostResourcePack()：内部 575 のときは 575 パックをバニラへ書く。
    text = sub(text,
               "        if (serverProtocol == ProtocolVersion.V1_15_2 && resourcePack578 != null) {\n"
               "            return resourcePack578;\n"
               "        }",
               "        if (serverProtocol == ProtocolVersion.V1_15_1 && resourcePack575 != null) {\n"
               "            return resourcePack575;\n"
               "        }\n"
               "        if (serverProtocol == ProtocolVersion.V1_15_2 && resourcePack578 != null) {\n"
               "            return resourcePack578;\n"
               "        }", 1)

    # 5. プロキシへ setPack575（内部が 575 のときは null＝ネイティブ中継）。
    text = sub(text,
               "        proxy.setPack578(serverProtocol == ProtocolVersion.V1_15_2\n"
               "                ? null : resourcePack578);",
               "        proxy.setPack575(serverProtocol == ProtocolVersion.V1_15_1\n"
               "                ? null : resourcePack575);\n"
               "        proxy.setPack578(serverProtocol == ProtocolVersion.V1_15_2\n"
               "                ? null : resourcePack578);", 1)

    # 6. LOOPBACK：built575 を作って ResourcePackServer へ渡し、pack575() を受ける。
    text = sub(text,
               "            ResourcePackBuilder.BuiltPack built578 = new ResourcePackBuilder()\n"
               "                    .build578(workingDir.resolve(\"data\").resolve(\"resource-pack\"));",
               "            ResourcePackBuilder.BuiltPack built575 = new ResourcePackBuilder()\n"
               "                    .build575(workingDir.resolve(\"data\").resolve(\"resource-pack\"));\n"
               "            ResourcePackBuilder.BuiltPack built578 = new ResourcePackBuilder()\n"
               "                    .build578(workingDir.resolve(\"data\").resolve(\"resource-pack\"));", 1)
    text = sub(text,
               "                    built, built578, built758, built759, built760, built761, built762, built763, built764, built766, built767, built768, built769,",
               "                    built, built575, built578, built758, built759, built760, built761, built762, built763, built764, built766, built767, built768, built769,", 1)
    text = sub(text,
               "            resourcePack578 = resourcePackServer.pack578();",
               "            resourcePack575 = resourcePackServer.pack575();\n"
               "            resourcePack578 = resourcePackServer.pack578();", 1)

    # 7. stopResourcePack() の null リセット。
    text = sub(text,
               "        resourcePack = null;\n        resourcePack578 = null;\n        resourcePack758 = null;",
               "        resourcePack = null;\n        resourcePack575 = null;\n        resourcePack578 = null;\n        resourcePack758 = null;", 1)

    # 8. アクセサ。
    text = sub(text,
               "    /** The 1.15.2 resource pack offered to 578 clients, or {@code null}. */\n"
               "    public ResourcePack resourcePack578() {",
               "    /** The 1.15.1 resource pack offered to 575 clients, or {@code null}. */\n"
               "    public ResourcePack resourcePack575() {\n"
               "        return resourcePack575;\n"
               "    }\n"
               "\n"
               "    /** The 1.15.2 resource pack offered to 578 clients, or {@code null}. */\n"
               "    public ResourcePack resourcePack578() {", 1)

    P.write_text(text, encoding="utf-8")
    print("DetergentServer.java を更新した")
    return 0


if __name__ == "__main__":
    sys.exit(main())
