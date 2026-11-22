#!/usr/bin/env python3
"""Bedrock 側に 490（1.14.3）バックエンドの配線を足す。

490 のワイヤ形式は 498 と同一（差はパケットIDの
acknowledge_player_digging の有無と Trade List の demand だけで、
どちらも Bedrock 側のパースには現れない）ため、498 の分岐に
490 を足し、レジストリ正規化は 490 用のケース（中身は 498 表への
委譲）を足す。

対象: JavaBackendConnection / BedrockChunkTranslator /
BedrockEntityTranslator。使い方:
    python3 tools/patch-490-bedrock.py
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
    if "serverProtocol == 490" in text:
        print("skip（既存）: JavaBackendConnection")
        return
    # Chat 送信・コマンド送信・Login Start（旧式形式は 490 も同じ）。
    text = sub(text,
               "if (serverProtocol == 758 || serverProtocol == 578 || serverProtocol == 575 || serverProtocol == 573 || serverProtocol == 498) {",
               "if (serverProtocol == 758 || serverProtocol == 578 || serverProtocol == 575 || serverProtocol == 573 || serverProtocol == 498 || serverProtocol == 490) {",
               3, "backend")
    # Login Success（設定フェーズ無し）。
    text = sub(text,
               "if (serverProtocol == 498 || serverProtocol == 573 || serverProtocol == 575 || serverProtocol == 578 || serverProtocol == 758 || serverProtocol == 759 || serverProtocol == 760 || serverProtocol == 761 || serverProtocol == 762 || serverProtocol == 763) {",
               "if (serverProtocol == 490 || serverProtocol == 498 || serverProtocol == 573 || serverProtocol == 575 || serverProtocol == 578 || serverProtocol == 758 || serverProtocol == 759 || serverProtocol == 760 || serverProtocol == 761 || serverProtocol == 762 || serverProtocol == 763) {",
               1, "backend")
    # 受信チャット（旧式 {JSON, position}）。
    text = sub(text,
               "PlayerChat chat = serverProtocol == 578 || serverProtocol == 575 || serverProtocol == 573 || serverProtocol == 498",
               "PlayerChat chat = serverProtocol == 578 || serverProtocol == 575 || serverProtocol == 573 || serverProtocol == 498 || serverProtocol == 490",
               1, "backend")
    # Player Info（旧式 action 形式。crypto は 490 にも無い）。
    text = sub(text,
               "if (serverProtocol == 498 || serverProtocol == 573 || serverProtocol == 575 || serverProtocol == 578 || serverProtocol == 758 || serverProtocol == 759 || serverProtocol == 760) {",
               "if (serverProtocol == 490 || serverProtocol == 498 || serverProtocol == 573 || serverProtocol == 575 || serverProtocol == 578 || serverProtocol == 758 || serverProtocol == 759 || serverProtocol == 760) {",
               1, "backend")
    text = sub(text,
               "recordPlayerInfo760(packet.payload(), serverProtocol != 758 && serverProtocol != 578 && serverProtocol != 575 && serverProtocol != 573 && serverProtocol != 498);",
               "recordPlayerInfo760(packet.payload(), serverProtocol != 758 && serverProtocol != 578 && serverProtocol != 575 && serverProtocol != 573 && serverProtocol != 498 && serverProtocol != 490);",
               1, "backend")
    # Respawn の次元（先頭 i32）。
    text = sub(text,
               "} else if (serverProtocol == 578 || serverProtocol == 575 || serverProtocol == 573 || serverProtocol == 498) {",
               "} else if (serverProtocol == 578 || serverProtocol == 575 || serverProtocol == 573 || serverProtocol == 498 || serverProtocol == 490) {",
               1, "backend")
    # Spawn Entity Living（490 も末尾メタデータ付き。498 用パースを共用）。
    text = sub(text,
               "BedrockEntityTranslator.SpawnEntity spawn = serverProtocol == 498\n",
               "BedrockEntityTranslator.SpawnEntity spawn = serverProtocol == 498 || serverProtocol == 490\n",
               1, "backend")
    # Named Entity Spawn（490 も末尾メタデータ付き。578 用パースを共用）。
    text = sub(text,
               "BedrockEntityTranslator.SpawnEntity spawn = serverProtocol == 578 || serverProtocol == 575 || serverProtocol == 573 || serverProtocol == 498\n",
               "BedrockEntityTranslator.SpawnEntity spawn = serverProtocol == 578 || serverProtocol == 575 || serverProtocol == 573 || serverProtocol == 498 || serverProtocol == 490\n",
               1, "backend")
    # Join Game（490 も parseJoinGame498 で読める。バイオーム名だけ 490 用）。
    text = sub(text,
               "        if (serverProtocol == 498) {",
               "        if (serverProtocol == 498 || serverProtocol == 490) {",
               1, "backend")
    text = sub(text,
               "            biomeNames = RegistryRemapper.biomeNames498();",
               "            biomeNames = serverProtocol == 490\n"
               "                    ? RegistryRemapper.biomeNames490()\n"
               "                    : RegistryRemapper.biomeNames498();",
               1, "backend")
    p.write_text(text, encoding="utf-8")
    print("JavaBackendConnection.java を更新した")


def patch_chunk():
    p = BASE / "BedrockChunkTranslator.java"
    text = p.read_text(encoding="utf-8")
    if "PROTOCOL_490" in text:
        print("skip（既存）: BedrockChunkTranslator")
        return
    # normalizeBlockState の 498 ケースを複製して 490 ケースを作る。
    start = text.index("            case RegistryRemapper.PROTOCOL_498 -> {")
    end = text.index("            case RegistryRemapper.PROTOCOL_573 -> {")
    block498 = text[start:end]
    block490 = (block498
                .replace("PROTOCOL_498", "PROTOCOL_490")
                .replace("mapBlockState498To758", "mapBlockState490To758")
                .replace("498 → 758", "490 → 758"))
    text = text[:start] + block490 + text[start:]
    # translate() の振り分け（チャンク形式は 498 と同一）。
    text = sub(text,
               "        if (input.serverProtocol() == RegistryRemapper.PROTOCOL_498) {\n"
               "            return translate498(input);\n"
               "        }",
               "        if (input.serverProtocol() == RegistryRemapper.PROTOCOL_490) {\n"
               "            // チャンク形式は 498 と同一（バイオームは末尾の256件）。\n"
               "            return translate498(input);\n"
               "        }\n"
               "        if (input.serverProtocol() == RegistryRemapper.PROTOCOL_498) {\n"
               "            return translate498(input);\n"
               "        }",
               1, "chunk")
    p.write_text(text, encoding="utf-8")
    print("BedrockChunkTranslator.java を更新した")


def patch_entity():
    p = BASE / "BedrockEntityTranslator.java"
    text = p.read_text(encoding="utf-8")
    if "PROTOCOL_490" in text:
        print("skip（既存）: BedrockEntityTranslator")
        return
    # normalizeEntityType の 498 ケースを複製。
    start = text.index("            case RegistryRemapper.PROTOCOL_498 -> {")
    end = text.index("            case RegistryRemapper.PROTOCOL_573 -> {", start)
    block498 = text[start:end]
    block490 = (block498
                .replace("PROTOCOL_498", "PROTOCOL_490")
                .replace("mapEntityType498To758", "mapEntityType490To758")
                .replace("498 → 758", "490 → 758"))
    text = text[:start] + block490 + text[start:]
    # normalizeItem の 498 ケースを複製。
    start = text.index("            case RegistryRemapper.PROTOCOL_498 -> {")
    end = text.index("            case RegistryRemapper.PROTOCOL_573 -> {", start)
    block498 = text[start:end]
    block490 = (block498
                .replace("PROTOCOL_498", "PROTOCOL_490")
                .replace("mapItem498To758", "mapItem490To758")
                .replace("498 → 758", "490 → 758"))
    text = text[:start] + block490 + text[start:]
    # Spawn Entity の objectData 形式（490 も headPitch 無し・i32）。
    text = sub(text,
               "                || serverProtocol == RegistryRemapper.PROTOCOL_498) {",
               "                || serverProtocol == RegistryRemapper.PROTOCOL_498\n"
               "                || serverProtocol == RegistryRemapper.PROTOCOL_490) {",
               1, "entity")
    p.write_text(text, encoding="utf-8")
    print("BedrockEntityTranslator.java を更新した")


if __name__ == "__main__":
    patch_backend()
    patch_chunk()
    patch_entity()
