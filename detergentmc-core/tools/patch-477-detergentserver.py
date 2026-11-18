#!/usr/bin/env python3
"""core モジュールに 477（1.14）の配線を足す。

- DetergentServer：resourcePack477 フィールド、hostResourcePack の
  477 分岐、proxy.setPack477、ResourcePackMode.decide() へ
  url-477／sha1-477 を渡す、EXTERNAL の pack477 取り出し、
  ローカル配信（build477＋27パック版コンストラクタ）、
  stopResourcePack、resourcePack477() アクセサ。
- VanillaServerHost：内部 1.14 は Java 8 で起動する。
- DatapackGenerator：1.14 の datapack pack_format は 4。

使い方: python3 tools/patch-477-detergentserver.py
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
    if "resourcePack477" in text:
        print("skip（既存）: DetergentServer")
        return
    # フィールド。
    text = sub(text,
               "    private ResourcePack resourcePack480;",
               "    private ResourcePack resourcePack477;\n"
               "    private ResourcePack resourcePack480;", 1, "ds")
    # hostResourcePack の 477 分岐。
    text = sub(text,
               "        if (serverProtocol == ProtocolVersion.V1_14_1 && resourcePack480 != null) {\n"
               "            return resourcePack480;\n"
               "        }",
               "        if (serverProtocol == ProtocolVersion.V1_14 && resourcePack477 != null) {\n"
               "            return resourcePack477;\n"
               "        }\n"
               "        if (serverProtocol == ProtocolVersion.V1_14_1 && resourcePack480 != null) {\n"
               "            return resourcePack480;\n"
               "        }", 1, "ds")
    # proxy.setPack477。
    text = sub(text,
               "        proxy.setPack480(serverProtocol == ProtocolVersion.V1_14_1\n"
               "                ? null : resourcePack480);",
               "        proxy.setPack477(serverProtocol == ProtocolVersion.V1_14\n"
               "                ? null : resourcePack477);\n"
               "        proxy.setPack480(serverProtocol == ProtocolVersion.V1_14_1\n"
               "                ? null : resourcePack480);", 1, "ds")
    # decide() へ url-477／sha1-477 を渡す（480 の直後）。
    text = sub(text,
               '                        config.getString("resource-pack.url-480", ""),\n'
               '                        config.getString("resource-pack.sha1-480", ""),\n',
               '                        config.getString("resource-pack.url-480", ""),\n'
               '                        config.getString("resource-pack.sha1-480", ""),\n'
               '                        config.getString("resource-pack.url-477", ""),\n'
               '                        config.getString("resource-pack.sha1-477", ""),\n', 1, "ds")
    # EXTERNAL：pack 無しの場合の null 化。
    text = sub(text,
               "                    resourcePack = null;\n"
               "                    resourcePack480 = null;",
               "                    resourcePack = null;\n"
               "                    resourcePack477 = null;\n"
               "                    resourcePack480 = null;", 1, "ds")
    # EXTERNAL：pack477 の取り出し。
    text = sub(text,
               "                resourcePack480 = decision.pack480();",
               "                resourcePack477 = decision.pack477();\n"
               "                if (resourcePack477 != null) {\n"
               "                    LOG.info(() -> translator.translate(\n"
               "                            \"resource-pack.external-enabled\", resourcePack477.url()));\n"
               "                }\n"
               "                resourcePack480 = decision.pack480();", 1, "ds")
    # ローカル配信：build477 と 27 パック版コンストラクタ。
    text = sub(text,
               "            ResourcePackBuilder.BuiltPack built480 = new ResourcePackBuilder()\n"
               "                    .build480(workingDir.resolve(\"data\").resolve(\"resource-pack\"));",
               "            ResourcePackBuilder.BuiltPack built477 = new ResourcePackBuilder()\n"
               "                    .build477(workingDir.resolve(\"data\").resolve(\"resource-pack\"));\n"
               "            ResourcePackBuilder.BuiltPack built480 = new ResourcePackBuilder()\n"
               "                    .build480(workingDir.resolve(\"data\").resolve(\"resource-pack\"));", 1, "ds")
    text = sub(text,
               "                    built, built480, built485, built490, built498, built573, built575, built578, built758, built759, built760, built761, built762, built763, built764, built766, built767, built768, built769,",
               "                    built, built477, built480, built485, built490, built498, built573, built575, built578, built758, built759, built760, built761, built762, built763, built764, built766, built767, built768, built769,", 1, "ds")
    text = sub(text,
               "            resourcePack480 = resourcePackServer.pack480();",
               "            resourcePack477 = resourcePackServer.pack477();\n"
               "            resourcePack480 = resourcePackServer.pack480();", 1, "ds")
    # stopResourcePack。
    text = sub(text,
               "        resourcePack = null;\n"
               "        resourcePack480 = null;",
               "        resourcePack = null;\n"
               "        resourcePack477 = null;\n"
               "        resourcePack480 = null;", 1, "ds")
    # アクセサ。
    text = sub(text,
               "    /** The 1.14.1 resource pack offered to 480 clients, or {@code null}. */\n"
               "    public ResourcePack resourcePack480() {",
               "    /** The 1.14 resource pack offered to 477 clients, or {@code null}. */\n"
               "    public ResourcePack resourcePack477() {\n"
               "        return resourcePack477;\n"
               "    }\n"
               "\n"
               "    /** The 1.14.1 resource pack offered to 480 clients, or {@code null}. */\n"
               "    public ResourcePack resourcePack480() {", 1, "ds")
    p.write_text(text, encoding="utf-8")
    print("DetergentServer.java を更新した")


def vanilla_host():
    p = MAIN / "vanilla/VanillaServerHost.java"
    text = p.read_text(encoding="utf-8")
    if '"1.14"' in text:
        print("skip（既存）: VanillaServerHost")
        return
    text = sub(text,
               '|| "1.14.1".equals(minecraftVersion)) {',
               '|| "1.14.1".equals(minecraftVersion) || "1.14".equals(minecraftVersion)) {', 1, "host")
    p.write_text(text, encoding="utf-8")
    print("VanillaServerHost.java を更新した")


def datapack():
    p = MAIN / "vanilla/DatapackGenerator.java"
    text = p.read_text(encoding="utf-8")
    if '"1.14"' in text:
        print("skip（既存）: DatapackGenerator")
        return
    text = sub(text,
               '        if ("1.14.4".equals(minecraftVersion) || "1.14.3".equals(minecraftVersion) || "1.14.2".equals(minecraftVersion) || "1.14.1".equals(minecraftVersion)) {',
               '        if ("1.14.4".equals(minecraftVersion) || "1.14.3".equals(minecraftVersion) || "1.14.2".equals(minecraftVersion) || "1.14.1".equals(minecraftVersion) || "1.14".equals(minecraftVersion)) {', 1, "datapack")
    p.write_text(text, encoding="utf-8")
    print("DatapackGenerator.java を更新した")


if __name__ == "__main__":
    detergent_server()
    vanilla_host()
    datapack()
