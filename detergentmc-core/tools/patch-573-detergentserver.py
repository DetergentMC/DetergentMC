#!/usr/bin/env python3
"""DetergentServer.java に 573（1.15）のリソースパック配線を足す。

575 のときと同じ流儀：resourcePack573 フィールド、設定キー
resource-pack.url-573／sha1-573、build573、ResourcePackServer への
built573 受け渡し、内部サーバーが 573 のときのネイティブ扱い
（hostResourcePack と proxy.setPack573(null)）。

使い方: python3 tools/patch-573-detergentserver.py
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
    if "resourcePack573" in text:
        print("skip（既存）: 573 配線はすでにある")
        return 0

    # 1. フィールド。
    text = sub(text,
               "    private ResourcePack resourcePack575;",
               "    private ResourcePack resourcePack573;\n"
               "    private ResourcePack resourcePack575;", 1)

    # 2. decide() へ url-573／sha1-573 を渡す（575 の直後）。
    text = sub(text,
               '                        config.getString("resource-pack.url-575", ""),\n'
               '                        config.getString("resource-pack.sha1-575", ""),\n'
               '                        required, promptKey);',
               '                        config.getString("resource-pack.url-575", ""),\n'
               '                        config.getString("resource-pack.sha1-575", ""),\n'
               '                        config.getString("resource-pack.url-573", ""),\n'
               '                        config.getString("resource-pack.sha1-573", ""),\n'
               '                        required, promptKey);', 1)

    # 3. EXTERNAL：pack573 を受け取り、失敗時の null リセットにも含める。
    text = sub(text,
               "                resourcePack575 = decision.pack575();",
               "                resourcePack573 = decision.pack573();\n"
               "                if (resourcePack573 != null) {\n"
               "                    LOG.info(() -> translator.translate(\n"
               '                            "resource-pack.external-enabled", resourcePack573.url()));\n'
               "                }\n"
               "                resourcePack575 = decision.pack575();", 1)
    text = sub(text,
               "                    resourcePack = null;\n                    resourcePack575 = null;",
               "                    resourcePack = null;\n                    resourcePack573 = null;\n                    resourcePack575 = null;", 1)

    # 4. hostResourcePack()：内部 573 のときは 573 パックをバニラへ書く。
    text = sub(text,
               "        if (serverProtocol == ProtocolVersion.V1_15_1 && resourcePack575 != null) {\n"
               "            return resourcePack575;\n"
               "        }",
               "        if (serverProtocol == ProtocolVersion.V1_15 && resourcePack573 != null) {\n"
               "            return resourcePack573;\n"
               "        }\n"
               "        if (serverProtocol == ProtocolVersion.V1_15_1 && resourcePack575 != null) {\n"
               "            return resourcePack575;\n"
               "        }", 1)

    # 5. プロキシへ setPack573（内部が 573 のときは null＝ネイティブ中継）。
    text = sub(text,
               "        proxy.setPack575(serverProtocol == ProtocolVersion.V1_15_1\n"
               "                ? null : resourcePack575);",
               "        proxy.setPack573(serverProtocol == ProtocolVersion.V1_15\n"
               "                ? null : resourcePack573);\n"
               "        proxy.setPack575(serverProtocol == ProtocolVersion.V1_15_1\n"
               "                ? null : resourcePack575);", 1)

    # 6. LOOPBACK：built573 を作って ResourcePackServer へ渡し、pack573() を受ける。
    text = sub(text,
               "            ResourcePackBuilder.BuiltPack built575 = new ResourcePackBuilder()\n"
               "                    .build575(workingDir.resolve(\"data\").resolve(\"resource-pack\"));",
               "            ResourcePackBuilder.BuiltPack built573 = new ResourcePackBuilder()\n"
               "                    .build573(workingDir.resolve(\"data\").resolve(\"resource-pack\"));\n"
               "            ResourcePackBuilder.BuiltPack built575 = new ResourcePackBuilder()\n"
               "                    .build575(workingDir.resolve(\"data\").resolve(\"resource-pack\"));", 1)
    text = sub(text,
               "                    built, built575, built578, built758, built759, built760, built761, built762, built763, built764, built766, built767, built768, built769,",
               "                    built, built573, built575, built578, built758, built759, built760, built761, built762, built763, built764, built766, built767, built768, built769,", 1)
    text = sub(text,
               "            resourcePack575 = resourcePackServer.pack575();",
               "            resourcePack573 = resourcePackServer.pack573();\n"
               "            resourcePack575 = resourcePackServer.pack575();", 1)

    # 7. stopResourcePack() の null リセット。
    text = sub(text,
               "        resourcePack = null;\n        resourcePack575 = null;",
               "        resourcePack = null;\n        resourcePack573 = null;\n        resourcePack575 = null;", 1)

    # 8. アクセサ。
    text = sub(text,
               "    /** The 1.15.1 resource pack offered to 575 clients, or {@code null}. */\n"
               "    public ResourcePack resourcePack575() {",
               "    /** The 1.15 resource pack offered to 573 clients, or {@code null}. */\n"
               "    public ResourcePack resourcePack573() {\n"
               "        return resourcePack573;\n"
               "    }\n"
               "\n"
               "    /** The 1.15.1 resource pack offered to 575 clients, or {@code null}. */\n"
               "    public ResourcePack resourcePack575() {", 1)

    P.write_text(text, encoding="utf-8")
    print("DetergentServer.java を更新した")
    return 0


if __name__ == "__main__":
    sys.exit(main())
