#!/usr/bin/env python3
"""Bedrock 側に 477（1.14）バックエンドの配線を足す。

477 のワイヤ形式は 480 と一致（パケットID・形式の差分0件、レジストリは
変換表に現れない memory_module_type の1件を除き一致）のため、480 の
分岐に 477 を足し、レジストリ正規化は 477 用のケース（中身は 480 表への
委譲）を足す。

対象: JavaBackendConnection / BedrockChunkTranslator /
BedrockEntityTranslator。使い方:
    python3 tools/patch-477-bedrock.py
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
    if "serverProtocol == 477" in text:
        print("skip（既存）: JavaBackendConnection")
        return
    # チャット送信・コマンド送信・Login Start（旧式形式は 477 も同じ）。
    text = sub(text,
               "if (serverProtocol == 758 || serverProtocol == 578 || serverProtocol == 575 || serverProtocol == 573 || serverProtocol == 498 || serverProtocol == 490 || serverProtocol == 485 || serverProtocol == 480) {",
               "if (serverProtocol == 758 || serverProtocol == 578 || serverProtocol == 575 || serverProtocol == 573 || serverProtocol == 498 || serverProtocol == 490 || serverProtocol == 485 || serverProtocol == 480 || serverProtocol == 477) {",
               3, "backend")
    # Login Success（設定フェーズ無し）。
    text = sub(text,
               "if (serverProtocol == 480 || serverProtocol == 485 || serverProtocol == 490 || serverProtocol == 498 || serverProtocol == 573 || serverProtocol == 575 || serverProtocol == 578 || serverProtocol == 758 || serverProtocol == 759 || serverProtocol == 760 || serverProtocol == 761 || serverProtocol == 762 || serverProtocol == 763) {",
               "if (serverProtocol == 477 || serverProtocol == 480 || serverProtocol == 485 || serverProtocol == 490 || serverProtocol == 498 || serverProtocol == 573 || serverProtocol == 575 || serverProtocol == 578 || serverProtocol == 758 || serverProtocol == 759 || serverProtocol == 760 || serverProtocol == 761 || serverProtocol == 762 || serverProtocol == 763) {",
               1, "backend")
    # 受信チャット（旧式 {JSON, position}）。
    text = sub(text,
               "PlayerChat chat = serverProtocol == 578 || serverProtocol == 575 || serverProtocol == 573 || serverProtocol == 498 || serverProtocol == 490 || serverProtocol == 485 || serverProtocol == 480",
               "PlayerChat chat = serverProtocol == 578 || serverProtocol == 575 || serverProtocol == 573 || serverProtocol == 498 || serverProtocol == 490 || serverProtocol == 485 || serverProtocol == 480 || serverProtocol == 477",
               1, "backend")
    # Player Info（旧式 action 形式。crypto は 477 にも無い）。
    text = sub(text,
               "if (serverProtocol == 480 || serverProtocol == 485 || serverProtocol == 490 || serverProtocol == 498 || serverProtocol == 573 || serverProtocol == 575 || serverProtocol == 578 || serverProtocol == 758 || serverProtocol == 759 || serverProtocol == 760) {",
               "if (serverProtocol == 477 || serverProtocol == 480 || serverProtocol == 485 || serverProtocol == 490 || serverProtocol == 498 || serverProtocol == 573 || serverProtocol == 575 || serverProtocol == 578 || serverProtocol == 758 || serverProtocol == 759 || serverProtocol == 760) {",
               1, "backend")
    text = sub(text,
               "recordPlayerInfo760(packet.payload(), serverProtocol != 758 && serverProtocol != 578 && serverProtocol != 575 && serverProtocol != 573 && serverProtocol != 498 && serverProtocol != 490 && serverProtocol != 485 && serverProtocol != 480);",
               "recordPlayerInfo760(packet.payload(), serverProtocol != 758 && serverProtocol != 578 && serverProtocol != 575 && serverProtocol != 573 && serverProtocol != 498 && serverProtocol != 490 && serverProtocol != 485 && serverProtocol != 480 && serverProtocol != 477);",
               1, "backend")
    # Respawn の次元（先頭 i32）。
    text = sub(text,
               "} else if (serverProtocol == 578 || serverProtocol == 575 || serverProtocol == 573 || serverProtocol == 498 || serverProtocol == 490 || serverProtocol == 485 || serverProtocol == 480) {",
               "} else if (serverProtocol == 578 || serverProtocol == 575 || serverProtocol == 573 || serverProtocol == 498 || serverProtocol == 490 || serverProtocol == 485 || serverProtocol == 480 || serverProtocol == 477) {",
               1, "backend")
    # Spawn Entity Living（477 も末尾メタデータ付き。490 用パースを共用）。
    text = sub(text,
               "BedrockEntityTranslator.SpawnEntity spawn = serverProtocol == 498 || serverProtocol == 490 || serverProtocol == 485 || serverProtocol == 480\n",
               "BedrockEntityTranslator.SpawnEntity spawn = serverProtocol == 498 || serverProtocol == 490 || serverProtocol == 485 || serverProtocol == 480 || serverProtocol == 477\n",
               1, "backend")
    # Named Entity Spawn（477 も末尾メタデータ付き。578 用パースを共用）。
    text = sub(text,
               "BedrockEntityTranslator.SpawnEntity spawn = serverProtocol == 578 || serverProtocol == 575 || serverProtocol == 573 || serverProtocol == 498 || serverProtocol == 490 || serverProtocol == 485 || serverProtocol == 480\n",
               "BedrockEntityTranslator.SpawnEntity spawn = serverProtocol == 578 || serverProtocol == 575 || serverProtocol == 573 || serverProtocol == 498 || serverProtocol == 490 || serverProtocol == 485 || serverProtocol == 480 || serverProtocol == 477\n",
               1, "backend")
    # Join Game（477 も parseJoinGame490 で読める。バイオーム名だけ 477 用）。
    text = sub(text,
               "        if (serverProtocol == 498 || serverProtocol == 490 || serverProtocol == 485 || serverProtocol == 480) {",
               "        if (serverProtocol == 498 || serverProtocol == 490 || serverProtocol == 485 || serverProtocol == 480 || serverProtocol == 477) {",
               1, "backend")
    text = sub(text,
               "            biomeNames = serverProtocol == 480\n"
               "                    ? RegistryRemapper.biomeNames480()\n",
               "            biomeNames = serverProtocol == 477\n"
               "                    ? RegistryRemapper.biomeNames477()\n"
               "                    : serverProtocol == 480\n"
               "                    ? RegistryRemapper.biomeNames480()\n",
               1, "backend")
    p.write_text(text, encoding="utf-8")
    print("JavaBackendConnection.java を更新した")


def dup_case(text, anchor, case_proto, next_case_proto, transforms, where):
    """指定メソッド内の case ブロックを複製して変換し、元のケースの直前に差し込む。"""
    base = text.index(anchor)
    start = text.index(f"            case RegistryRemapper.{case_proto} -> {{", base)
    end = text.index(f"            case RegistryRemapper.{next_case_proto} -> {{", start)
    block = text[start:end]
    new_block = block
    for old, new in transforms:
        new_block = new_block.replace(old, new)
    return text[:start] + new_block + text[start:]


def patch_chunk():
    p = BASE / "BedrockChunkTranslator.java"
    text = p.read_text(encoding="utf-8")
    if "PROTOCOL_477" in text:
        print("skip（既存）: BedrockChunkTranslator")
        return
    # normalizeBlockState の 480 ケースを複製して 477 ケースを作る。
    text = dup_case(text, "normalizeBlockState", "PROTOCOL_480", "PROTOCOL_485",
                    [("PROTOCOL_480", "PROTOCOL_477"),
                     ("mapBlockState480To758", "mapBlockState477To758"),
                     ("480 → 758", "477 → 758")], "chunk")
    # translate() の振り分け（チャンク形式は 480 と同一）。
    text = sub(text,
               "        if (input.serverProtocol() == RegistryRemapper.PROTOCOL_480) {\n"
               "            // チャンク形式は 485 と同一（バイオームは末尾の256件）。\n"
               "            return translate498(input);\n"
               "        }",
               "        if (input.serverProtocol() == RegistryRemapper.PROTOCOL_477) {\n"
               "            // チャンク形式は 480 と同一（バイオームは末尾の256件）。\n"
               "            return translate498(input);\n"
               "        }\n"
               "        if (input.serverProtocol() == RegistryRemapper.PROTOCOL_480) {\n"
               "            // チャンク形式は 485 と同一（バイオームは末尾の256件）。\n"
               "            return translate498(input);\n"
               "        }",
               1, "chunk")
    p.write_text(text, encoding="utf-8")
    print("BedrockChunkTranslator.java を更新した")


def patch_entity():
    p = BASE / "BedrockEntityTranslator.java"
    text = p.read_text(encoding="utf-8")
    if "PROTOCOL_477" in text:
        print("skip（既存）: BedrockEntityTranslator")
        return
    # normalizeEntityType の 480 ケースを複製。
    text = dup_case(text, "normalizeEntityType", "PROTOCOL_480", "PROTOCOL_485",
                    [("PROTOCOL_480", "PROTOCOL_477"),
                     ("mapEntityType480To758", "mapEntityType477To758"),
                     ("480 → 758", "477 → 758")], "entity")
    # normalizeItem の 480 ケースを複製。
    text = dup_case(text, "normalizeItem", "PROTOCOL_480", "PROTOCOL_485",
                    [("PROTOCOL_480", "PROTOCOL_477"),
                     ("mapItem480To758", "mapItem477To758"),
                     ("480 → 758", "477 → 758")], "entity")
    # Spawn Entity の objectData 形式（477 も headPitch 無し・i32）。
    text = sub(text,
               "                || serverProtocol == RegistryRemapper.PROTOCOL_480) {",
               "                || serverProtocol == RegistryRemapper.PROTOCOL_480\n"
               "                || serverProtocol == RegistryRemapper.PROTOCOL_477) {",
               1, "entity")
    p.write_text(text, encoding="utf-8")
    print("BedrockEntityTranslator.java を更新した")


if __name__ == "__main__":
    patch_backend()
    patch_chunk()
    patch_entity()
