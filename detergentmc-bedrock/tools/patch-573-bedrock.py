#!/usr/bin/env python3
"""Bedrock 側に 573（1.15）バックエンドの配線を足す。

573 のワイヤ形式は 575／578 と同一（Stage A で実データ証明済み）のため、
575／578 の分岐が発火する条件すべてに 573 を足す。レジストリ変換は
573 用の委譲メソッド（中身は 578 表と同一）を通す。

対象: JavaBackendConnection / BedrockChunkTranslator /
BedrockEntityTranslator。使い方:
    python3 tools/patch-573-bedrock.py
書き換えた後は必ず git diff で内容を確認してからコミットすること。
"""
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
BASE = ROOT / "src/main/java/io/detergentmc/bedrock"


def sub(text, old, new, count, where):
    found = text.count(old)
    if found != count:
        print(f"NG[{where}]: {found} 件（期待 {count}）: {old[:70]!r}")
        sys.exit(1)
    return text.replace(old, new)


def patch_backend():
    p = BASE / "JavaBackendConnection.java"
    text = p.read_text(encoding="utf-8")
    if "serverProtocol == 573" in text:
        print("skip（既存）: JavaBackendConnection")
        return
    text = sub(text,
               "if (serverProtocol == 758 || serverProtocol == 578 || serverProtocol == 575) {",
               "if (serverProtocol == 758 || serverProtocol == 578 || serverProtocol == 575 || serverProtocol == 573) {", 3, "backend")
    text = sub(text,
               "if (serverProtocol == 575 || serverProtocol == 578 || serverProtocol == 758 || serverProtocol == 759 || serverProtocol == 760 || serverProtocol == 761 || serverProtocol == 762 || serverProtocol == 763) {",
               "if (serverProtocol == 573 || serverProtocol == 575 || serverProtocol == 578 || serverProtocol == 758 || serverProtocol == 759 || serverProtocol == 760 || serverProtocol == 761 || serverProtocol == 762 || serverProtocol == 763) {", 1, "backend")
    text = sub(text,
               "            PlayerChat chat = serverProtocol == 578 || serverProtocol == 575\n                    ? extractOldChat578(packet.payload())",
               "            PlayerChat chat = serverProtocol == 578 || serverProtocol == 575 || serverProtocol == 573\n                    ? extractOldChat578(packet.payload())", 1, "backend")
    text = sub(text,
               "if (serverProtocol == 575 || serverProtocol == 578 || serverProtocol == 758 || serverProtocol == 759 || serverProtocol == 760) {",
               "if (serverProtocol == 573 || serverProtocol == 575 || serverProtocol == 578 || serverProtocol == 758 || serverProtocol == 759 || serverProtocol == 760) {", 1, "backend")
    text = sub(text,
               "recordPlayerInfo760(packet.payload(), serverProtocol != 758 && serverProtocol != 578 && serverProtocol != 575);",
               "recordPlayerInfo760(packet.payload(), serverProtocol != 758 && serverProtocol != 578 && serverProtocol != 575 && serverProtocol != 573);", 1, "backend")
    text = sub(text,
               "} else if (serverProtocol == 578 || serverProtocol == 575) {",
               "} else if (serverProtocol == 578 || serverProtocol == 575 || serverProtocol == 573) {", 1, "backend")
    text = sub(text,
               "BedrockEntityTranslator.SpawnEntity spawn = serverProtocol == 578 || serverProtocol == 575\n                        ? BedrockEntityTranslator.parseSpawnEntityLiving578(packet.payload())",
               "BedrockEntityTranslator.SpawnEntity spawn = serverProtocol == 578 || serverProtocol == 575 || serverProtocol == 573\n                        ? BedrockEntityTranslator.parseSpawnEntityLiving578(packet.payload())", 1, "backend")
    text = sub(text,
               "BedrockEntityTranslator.SpawnEntity spawn = serverProtocol == 578 || serverProtocol == 575\n                        ? BedrockEntityTranslator.parseNamedEntitySpawn578(packet.payload())",
               "BedrockEntityTranslator.SpawnEntity spawn = serverProtocol == 578 || serverProtocol == 575 || serverProtocol == 573\n                        ? BedrockEntityTranslator.parseNamedEntitySpawn578(packet.payload())", 1, "backend")
    text = sub(text,
               "        if (serverProtocol == 578 || serverProtocol == 575) {\n"
               "            // 578／575 の Join Game は次元が i32 の ID で、codec は無い",
               "        if (serverProtocol == 578 || serverProtocol == 575 || serverProtocol == 573) {\n"
               "            // 578／575／573 の Join Game は次元が i32 の ID で、codec は無い", 1, "backend")
    text = sub(text,
               "            biomeNames = serverProtocol == 575\n"
               "                    ? RegistryRemapper.biomeNames575()\n"
               "                    : RegistryRemapper.biomeNames578();",
               "            biomeNames = serverProtocol == 573\n"
               "                    ? RegistryRemapper.biomeNames573()\n"
               "                    : serverProtocol == 575\n"
               "                    ? RegistryRemapper.biomeNames575()\n"
               "                    : RegistryRemapper.biomeNames578();", 1, "backend")
    p.write_text(text, encoding="utf-8")
    print("JavaBackendConnection.java を更新した")


def patch_chunk():
    p = BASE / "BedrockChunkTranslator.java"
    text = p.read_text(encoding="utf-8")
    if "PROTOCOL_573" in text:
        print("skip（既存）: BedrockChunkTranslator")
        return
    # normalizeBlockState の 575 ケース全体を読み、573 ケースを前に足す。
    start = text.index("            case RegistryRemapper.PROTOCOL_575 -> {")
    end = text.index("            case RegistryRemapper.PROTOCOL_578 -> {")
    block575 = text[start:end]
    block573 = (block575
                .replace("PROTOCOL_575", "PROTOCOL_573")
                .replace("mapBlockState575To758", "mapBlockState573To758")
                .replace("575 → 758", "573 → 758"))
    text = text[:start] + block573 + text[start:]
    text = sub(text,
               "        if (input.serverProtocol() == RegistryRemapper.PROTOCOL_578\n                || input.serverProtocol() == RegistryRemapper.PROTOCOL_575) {\n            return translate578(input);\n        }",
               "        if (input.serverProtocol() == RegistryRemapper.PROTOCOL_578\n                || input.serverProtocol() == RegistryRemapper.PROTOCOL_575\n                || input.serverProtocol() == RegistryRemapper.PROTOCOL_573) {\n            return translate578(input);\n        }", 1, "chunk")
    p.write_text(text, encoding="utf-8")
    print("BedrockChunkTranslator.java を更新した")


def patch_entity():
    p = BASE / "BedrockEntityTranslator.java"
    text = p.read_text(encoding="utf-8")
    if "PROTOCOL_573" in text:
        print("skip（既存）: BedrockEntityTranslator")
        return
    # normalizeEntityType の 575 ケース。
    start = text.index("            case RegistryRemapper.PROTOCOL_575 -> {")
    end = text.index("            case RegistryRemapper.PROTOCOL_578 -> {", start)
    block575 = text[start:end]
    block573 = (block575
                .replace("PROTOCOL_575", "PROTOCOL_573")
                .replace("mapEntityType575To758", "mapEntityType573To758")
                .replace("575 → 758", "573 → 758"))
    text = text[:start] + block573 + text[start:]
    # normalizeItem の 575 ケース（2 件目の出現箇所）。
    first = text.index("            case RegistryRemapper.PROTOCOL_575 -> {")
    start = text.index("            case RegistryRemapper.PROTOCOL_575 -> {", first + 1)
    end = text.index("            case RegistryRemapper.PROTOCOL_578 -> {", start)
    block575 = text[start:end]
    block573 = (block575
                .replace("PROTOCOL_575", "PROTOCOL_573")
                .replace("mapItem575To758", "mapItem573To758")
                .replace("575 → 758", "573 → 758"))
    text = text[:start] + block573 + text[start:]
    # Spawn Entity の headPitch 無し分岐。
    text = sub(text,
               "                || serverProtocol == RegistryRemapper.PROTOCOL_575) {",
               "                || serverProtocol == RegistryRemapper.PROTOCOL_575\n                || serverProtocol == RegistryRemapper.PROTOCOL_573) {", 1, "entity")
    p.write_text(text, encoding="utf-8")
    print("BedrockEntityTranslator.java を更新した")


def main():
    patch_backend()
    patch_chunk()
    patch_entity()
    return 0


if __name__ == "__main__":
    sys.exit(main())
