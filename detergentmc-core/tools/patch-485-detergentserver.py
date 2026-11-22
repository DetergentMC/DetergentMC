#!/usr/bin/env python3
"""core モジュールに 485（1.14.2）の配線を足す。

- DetergentServer：resourcePack485 フィールド、hostResourcePack の
  485 分岐、proxy.setPack485、ResourcePackMode.decide() へ
  url-485／sha1-485 を渡す、EXTERNAL の pack485 取り出し、
  ローカル配信（build485＋24パック版コンストラクタ）、
  stopResourcePack、resourcePack485() アクセサ。
- VanillaServerHost：内部 1.14.2 は Java 8 で起動する。
- DatapackGenerator：1.14.2 の datapack pack_format は 4。

使い方: python3 tools/patch-485-detergentserver.py
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
    if "resourcePack485" in text:
        print("skip（既存）: DetergentServer")
        return
    # フィールド。
    text = sub(text,
               "    private ResourcePack resourcePack490;",
               "    private ResourcePack resourcePack485;\n"
               "    private ResourcePack resourcePack490;", 1, "ds")
    # hostResourcePack の 485 分岐。
    text = sub(text,
               "        if (serverProtocol == ProtocolVersion.V1_14_3 && resourcePack490 != null) {\n"
               "            return resourcePack490;\n"
               "        }",
               "        if (serverProtocol == ProtocolVersion.V1_14_2 && resourcePack485 != null) {\n"
               "            return resourcePack485;\n"
               "        }\n"
               "        if (serverProtocol == ProtocolVersion.V1_14_3 && resourcePack490 != null) {\n"
               "            return resourcePack490;\n"
               "        }", 1, "ds")
    # proxy.setPack485。
    text = sub(text,
               "        proxy.setPack490(serverProtocol == ProtocolVersion.V1_14_3\n"
               "                ? null : resourcePack490);",
               "        proxy.setPack485(serverProtocol == ProtocolVersion.V1_14_2\n"
               "                ? null : resourcePack485);\n"
               "        proxy.setPack490(serverProtocol == ProtocolVersion.V1_14_3\n"
               "                ? null : resourcePack490);", 1, "ds")
    # decide() へ url-485／sha1-485 を渡す（490 の直後）。
    text = sub(text,
               '                        config.getString("resource-pack.url-490", ""),\n'
               '                        config.getString("resource-pack.sha1-490", ""),\n',
               '                        config.getString("resource-pack.url-490", ""),\n'
               '                        config.getString("resource-pack.sha1-490", ""),\n'
               '                        config.getString("resource-pack.url-485", ""),\n'
               '                        config.getString("resource-pack.sha1-485", ""),\n', 1, "ds")
    # EXTERNAL：pack 無しの場合の null 化。
    text = sub(text,
               "                    resourcePack = null;\n"
               "                    resourcePack490 = null;",
               "                    resourcePack = null;\n"
               "                    resourcePack485 = null;\n"
               "                    resourcePack490 = null;", 1, "ds")
    # EXTERNAL：pack485 の取り出し。
    text = sub(text,
               "                resourcePack490 = decision.pack490();",
               "                resourcePack485 = decision.pack485();\n"
               "                if (resourcePack485 != null) {\n"
               "                    LOG.info(() -> translator.translate(\n"
               "                            \"resource-pack.external-enabled\", resourcePack485.url()));\n"
               "                }\n"
               "                resourcePack490 = decision.pack490();", 1, "ds")
    # ローカル配信：build485 と 24 パック版コンストラクタ。
    text = sub(text,
               "            ResourcePackBuilder.BuiltPack built490 = new ResourcePackBuilder()\n"
               "                    .build490(workingDir.resolve(\"data\").resolve(\"resource-pack\"));",
               "            ResourcePackBuilder.BuiltPack built485 = new ResourcePackBuilder()\n"
               "                    .build485(workingDir.resolve(\"data\").resolve(\"resource-pack\"));\n"
               "            ResourcePackBuilder.BuiltPack built490 = new ResourcePackBuilder()\n"
               "                    .build490(workingDir.resolve(\"data\").resolve(\"resource-pack\"));", 1, "ds")
    text = sub(text,
               "                    built, built490, built498, built573, built575, built578, built758, built759, built760, built761, built762, built763, built764, built766, built767, built768, built769,",
               "                    built, built485, built490, built498, built573, built575, built578, built758, built759, built760, built761, built762, built763, built764, built766, built767, built768, built769,", 1, "ds")
    text = sub(text,
               "            resourcePack490 = resourcePackServer.pack490();",
               "            resourcePack485 = resourcePackServer.pack485();\n"
               "            resourcePack490 = resourcePackServer.pack490();", 1, "ds")
    # stopResourcePack。
    text = sub(text,
               "        resourcePack = null;\n"
               "        resourcePack490 = null;",
               "        resourcePack = null;\n"
               "        resourcePack485 = null;\n"
               "        resourcePack490 = null;", 1, "ds")
    # アクセサ。
    text = sub(text,
               "    /** The 1.14.3 resource pack offered to 490 clients, or {@code null}. */\n"
               "    public ResourcePack resourcePack490() {",
               "    /** The 1.14.2 resource pack offered to 485 clients, or {@code null}. */\n"
               "    public ResourcePack resourcePack485() {\n"
               "        return resourcePack485;\n"
               "    }\n"
               "\n"
               "    /** The 1.14.3 resource pack offered to 490 clients, or {@code null}. */\n"
               "    public ResourcePack resourcePack490() {", 1, "ds")
    p.write_text(text, encoding="utf-8")
    print("DetergentServer.java を更新した")


def vanilla_host():
    p = MAIN / "vanilla/VanillaServerHost.java"
    text = p.read_text(encoding="utf-8")
    if '"1.14.2"' in text:
        print("skip（既存）: VanillaServerHost")
        return
    text = sub(text,
               '|| "1.14.3".equals(minecraftVersion)) {',
               '|| "1.14.3".equals(minecraftVersion) || "1.14.2".equals(minecraftVersion)) {', 1, "host")
    p.write_text(text, encoding="utf-8")
    print("VanillaServerHost.java を更新した")


def datapack():
    p = MAIN / "vanilla/DatapackGenerator.java"
    text = p.read_text(encoding="utf-8")
    if '"1.14.2"' in text:
        print("skip（既存）: DatapackGenerator")
        return
    text = sub(text,
               '        if ("1.14.4".equals(minecraftVersion) || "1.14.3".equals(minecraftVersion)) {',
               '        if ("1.14.4".equals(minecraftVersion) || "1.14.3".equals(minecraftVersion) || "1.14.2".equals(minecraftVersion)) {', 1, "datapack")
    p.write_text(text, encoding="utf-8")
    print("DatapackGenerator.java を更新した")


if __name__ == "__main__":
    detergent_server()
    vanilla_host()
    datapack()
