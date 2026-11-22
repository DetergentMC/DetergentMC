#!/usr/bin/env python3
"""core モジュールに 490（1.14.3）の配線を足す。

- DetergentServer：resourcePack490 フィールド、hostResourcePack の
  490 分岐、proxy.setPack490、ResourcePackMode.decide() へ
  url-490／sha1-490 を渡す、EXTERNAL の pack490 取り出し、
  ローカル配信（build490＋24パック版コンストラクタ）、
  stopResourcePack、resourcePack490() アクセサ。
- VanillaServerHost：内部 1.14.3 は Java 8 で起動する。
- DatapackGenerator：1.14.3 の datapack pack_format は 4。

使い方: python3 tools/patch-490-detergentserver.py
"""
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
MAIN = ROOT / "src/main/java/io/detergentmc/core"


def sub(text, old, new, count, where):
    found = text.count(old)
    if found != count:
        print(f"NG[{where}]: {found} 件（期待 {count}）: {old[:70]!r}")
        sys.exit(1)
    return text.replace(old, new)


def detergent_server():
    p = MAIN / "DetergentServer.java"
    text = p.read_text(encoding="utf-8")
    if "resourcePack490" in text:
        print("skip（既存）: DetergentServer")
        return
    # フィールド。
    text = sub(text,
               "    private ResourcePack resourcePack498;",
               "    private ResourcePack resourcePack490;\n"
               "    private ResourcePack resourcePack498;", 1, "ds")
    # hostResourcePack の 490 分岐。
    text = sub(text,
               "        if (serverProtocol == ProtocolVersion.V1_14_4 && resourcePack498 != null) {\n"
               "            return resourcePack498;\n"
               "        }",
               "        if (serverProtocol == ProtocolVersion.V1_14_3 && resourcePack490 != null) {\n"
               "            return resourcePack490;\n"
               "        }\n"
               "        if (serverProtocol == ProtocolVersion.V1_14_4 && resourcePack498 != null) {\n"
               "            return resourcePack498;\n"
               "        }", 1, "ds")
    # proxy.setPack490。
    text = sub(text,
               "        proxy.setPack498(serverProtocol == ProtocolVersion.V1_14_4\n"
               "                ? null : resourcePack498);",
               "        proxy.setPack490(serverProtocol == ProtocolVersion.V1_14_3\n"
               "                ? null : resourcePack490);\n"
               "        proxy.setPack498(serverProtocol == ProtocolVersion.V1_14_4\n"
               "                ? null : resourcePack498);", 1, "ds")
    # decide() へ url-490／sha1-490 を渡す（498 の直後）。
    text = sub(text,
               '                        config.getString("resource-pack.url-498", ""),\n'
               '                        config.getString("resource-pack.sha1-498", ""),\n',
               '                        config.getString("resource-pack.url-498", ""),\n'
               '                        config.getString("resource-pack.sha1-498", ""),\n'
               '                        config.getString("resource-pack.url-490", ""),\n'
               '                        config.getString("resource-pack.sha1-490", ""),\n', 1, "ds")
    # EXTERNAL：pack 無しの場合の null 化。
    text = sub(text,
               "                    resourcePack = null;\n"
               "                    resourcePack498 = null;",
               "                    resourcePack = null;\n"
               "                    resourcePack490 = null;\n"
               "                    resourcePack498 = null;", 1, "ds")
    # EXTERNAL：pack490 の取り出し。
    text = sub(text,
               "                resourcePack498 = decision.pack498();",
               "                resourcePack490 = decision.pack490();\n"
               "                if (resourcePack490 != null) {\n"
               "                    LOG.info(() -> translator.translate(\n"
               "                            \"resource-pack.external-enabled\", resourcePack490.url()));\n"
               "                }\n"
               "                resourcePack498 = decision.pack498();", 1, "ds")
    # ローカル配信：build490 と 24 パック版コンストラクタ。
    text = sub(text,
               "            ResourcePackBuilder.BuiltPack built498 = new ResourcePackBuilder()\n"
               "                    .build498(workingDir.resolve(\"data\").resolve(\"resource-pack\"));",
               "            ResourcePackBuilder.BuiltPack built490 = new ResourcePackBuilder()\n"
               "                    .build490(workingDir.resolve(\"data\").resolve(\"resource-pack\"));\n"
               "            ResourcePackBuilder.BuiltPack built498 = new ResourcePackBuilder()\n"
               "                    .build498(workingDir.resolve(\"data\").resolve(\"resource-pack\"));", 1, "ds")
    text = sub(text,
               "                    built, built498, built573, built575, built578, built758, built759, built760, built761, built762, built763, built764, built766, built767, built768, built769,",
               "                    built, built490, built498, built573, built575, built578, built758, built759, built760, built761, built762, built763, built764, built766, built767, built768, built769,", 1, "ds")
    text = sub(text,
               "            resourcePack498 = resourcePackServer.pack498();",
               "            resourcePack490 = resourcePackServer.pack490();\n"
               "            resourcePack498 = resourcePackServer.pack498();", 1, "ds")
    # stopResourcePack。
    text = sub(text,
               "        resourcePack = null;\n"
               "        resourcePack498 = null;",
               "        resourcePack = null;\n"
               "        resourcePack490 = null;\n"
               "        resourcePack498 = null;", 1, "ds")
    # アクセサ。
    text = sub(text,
               "    /** The 1.14.4 resource pack offered to 498 clients, or {@code null}. */\n"
               "    public ResourcePack resourcePack498() {",
               "    /** The 1.14.3 resource pack offered to 490 clients, or {@code null}. */\n"
               "    public ResourcePack resourcePack490() {\n"
               "        return resourcePack490;\n"
               "    }\n"
               "\n"
               "    /** The 1.14.4 resource pack offered to 498 clients, or {@code null}. */\n"
               "    public ResourcePack resourcePack498() {", 1, "ds")
    p.write_text(text, encoding="utf-8")
    print("DetergentServer.java を更新した")


def vanilla_host():
    p = MAIN / "vanilla/VanillaServerHost.java"
    text = p.read_text(encoding="utf-8")
    if '"1.14.3"' in text:
        print("skip（既存）: VanillaServerHost")
        return
    text = sub(text,
               '|| "1.14.4".equals(minecraftVersion)) {',
               '|| "1.14.4".equals(minecraftVersion) || "1.14.3".equals(minecraftVersion)) {', 1, "host")
    p.write_text(text, encoding="utf-8")
    print("VanillaServerHost.java を更新した")


def datapack():
    p = MAIN / "vanilla/DatapackGenerator.java"
    text = p.read_text(encoding="utf-8")
    if '"1.14.3"' in text:
        print("skip（既存）: DatapackGenerator")
        return
    text = sub(text,
               '        if ("1.14.4".equals(minecraftVersion)) {',
               '        if ("1.14.4".equals(minecraftVersion) || "1.14.3".equals(minecraftVersion)) {', 1, "datapack")
    p.write_text(text, encoding="utf-8")
    print("DatapackGenerator.java を更新した")


if __name__ == "__main__":
    detergent_server()
    vanilla_host()
    datapack()
