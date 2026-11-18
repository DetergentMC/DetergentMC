#!/usr/bin/env python3
"""DetergentServer.java に 498（1.14.4）のリソースパック配線を足す。

573 のときと同じ流儀：resourcePack498 フィールド、設定キー
resource-pack.url-498／sha1-498、build498、ResourcePackServer への
built498 受け渡し、内部サーバーが 498 のときのネイティブ扱い
（hostResourcePack と proxy.setPack498(null)）。

使い方: python3 tools/patch-498-detergentserver.py
（detergentmc-core/tools/ に置くこと）
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
    if "resourcePack498" in text:
        print("skip（既存）: 498 配線はすでにある")
        return 0

    # 1. フィールド。
    text = sub(text,
               "    private ResourcePack resourcePack573;",
               "    private ResourcePack resourcePack498;\n"
               "    private ResourcePack resourcePack573;", 1)

    # 2. decide() へ url-498／sha1-498 を渡す（573 の直後＝引数順の末尾側）。
    text = sub(text,
               '                        config.getString("resource-pack.url-573", ""),\n'
               '                        config.getString("resource-pack.sha1-573", ""),\n'
               '                        required, promptKey);',
               '                        config.getString("resource-pack.url-573", ""),\n'
               '                        config.getString("resource-pack.sha1-573", ""),\n'
               '                        config.getString("resource-pack.url-498", ""),\n'
               '                        config.getString("resource-pack.sha1-498", ""),\n'
               '                        required, promptKey);', 1)

    # 3. EXTERNAL：pack498 を受け取り、失敗時の null リセットにも含める。
    text = sub(text,
               "                resourcePack573 = decision.pack573();",
               "                resourcePack498 = decision.pack498();\n"
               "                if (resourcePack498 != null) {\n"
               "                    LOG.info(() -> translator.translate(\n"
               '                            "resource-pack.external-enabled", resourcePack498.url()));\n'
               "                }\n"
               "                resourcePack573 = decision.pack573();", 1)
    text = sub(text,
               "                    resourcePack573 = null;\n                    resourcePack575 = null;",
               "                    resourcePack498 = null;\n                    resourcePack573 = null;\n                    resourcePack575 = null;", 1)

    # 4. hostResourcePack()：内部 498 のときは 498 パックをバニラへ書く。
    text = sub(text,
               "        if (serverProtocol == ProtocolVersion.V1_15 && resourcePack573 != null) {\n"
               "            return resourcePack573;\n"
               "        }",
               "        if (serverProtocol == ProtocolVersion.V1_14_4 && resourcePack498 != null) {\n"
               "            return resourcePack498;\n"
               "        }\n"
               "        if (serverProtocol == ProtocolVersion.V1_15 && resourcePack573 != null) {\n"
               "            return resourcePack573;\n"
               "        }", 1)

    # 5. プロキシへ setPack498（内部が 498 のときは null＝ネイティブ中継）。
    text = sub(text,
               "        proxy.setPack573(serverProtocol == ProtocolVersion.V1_15\n"
               "                ? null : resourcePack573);",
               "        proxy.setPack498(serverProtocol == ProtocolVersion.V1_14_4\n"
               "                ? null : resourcePack498);\n"
               "        proxy.setPack573(serverProtocol == ProtocolVersion.V1_15\n"
               "                ? null : resourcePack573);", 1)

    # 6. LOOPBACK：built498 を作って ResourcePackServer へ渡し、pack498() を受ける。
    text = sub(text,
               "            ResourcePackBuilder.BuiltPack built573 = new ResourcePackBuilder()\n"
               "                    .build573(workingDir.resolve(\"data\").resolve(\"resource-pack\"));",
               "            ResourcePackBuilder.BuiltPack built498 = new ResourcePackBuilder()\n"
               "                    .build498(workingDir.resolve(\"data\").resolve(\"resource-pack\"));\n"
               "            ResourcePackBuilder.BuiltPack built573 = new ResourcePackBuilder()\n"
               "                    .build573(workingDir.resolve(\"data\").resolve(\"resource-pack\"));", 1)
    text = sub(text,
               "                    built, built573, built575, built578, built758, built759, built760, built761, built762, built763, built764, built766, built767, built768, built769,",
               "                    built, built498, built573, built575, built578, built758, built759, built760, built761, built762, built763, built764, built766, built767, built768, built769,", 1)
    text = sub(text,
               "            resourcePack573 = resourcePackServer.pack573();",
               "            resourcePack498 = resourcePackServer.pack498();\n"
               "            resourcePack573 = resourcePackServer.pack573();", 1)

    # 7. stopResourcePack() の null リセット。
    text = sub(text,
               "        resourcePack573 = null;\n        resourcePack575 = null;",
               "        resourcePack498 = null;\n        resourcePack573 = null;\n        resourcePack575 = null;", 1)

    # 8. アクセサ。
    text = sub(text,
               "    /** The 1.15 resource pack offered to 573 clients, or {@code null}. */\n"
               "    public ResourcePack resourcePack573() {",
               "    /** The 1.14.4 resource pack offered to 498 clients, or {@code null}. */\n"
               "    public ResourcePack resourcePack498() {\n"
               "        return resourcePack498;\n"
               "    }\n"
               "\n"
               "    /** The 1.15 resource pack offered to 573 clients, or {@code null}. */\n"
               "    public ResourcePack resourcePack573() {", 1)

    P.write_text(text, encoding="utf-8")
    print("DetergentServer.java を更新した")
    return 0


if __name__ == "__main__":
    sys.exit(main())
