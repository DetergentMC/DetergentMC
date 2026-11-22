#!/usr/bin/env python3
"""Bedrock 側に 480（1.14.1）バックエンドの配線を足す。

480 のワイヤ形式は 485 と完全一致（差分0件）のため、485 の分岐に
480 を足し、レジストリ正規化は 480 用のケース（中身は 485 表への
委譲）を足す。

あわせて、485 の配線（patch-485-bedrock.py）で誤って落ちていた
498 を3箇所（チャット送信・コマンド送信・Login Start）へ復旧する。
485 の置換前は `... || 573 || 498 || 490` だったものが
`... || 573 || 490 || 485` になっており、498 バックエンドでは
旧式形式（{message} のみ等）ではなく 761 以降の署名付き形式で
送ってしまう状態だった。ここで 498 を戻し、480 も足す。

対象: JavaBackendConnection / BedrockChunkTranslator /
BedrockEntityTranslator。使い方:
    python3 tools/patch-480-bedrock.py
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
    if "serverProtocol == 480" in text:
        print("skip（既存）: JavaBackendConnection")
        return
    # チャット送信・コマンド送信・Login Start（旧式形式は 480 も同じ。
    # 485 の配線で落ちていた 498 もここで復旧する）。
    text = sub(text,
               "if (serverProtocol == 758 || serverProtocol == 578 || serverProtocol == 575 || serverProtocol == 573 || serverProtocol == 490 || serverProtocol == 485) {",
               "if (serverProtocol == 758 || serverProtocol == 578 || serverProtocol == 575 || serverProtocol == 573 || serverProtocol == 498 || serverProtocol == 490 || serverProtocol == 485 || serverProtocol == 480) {",
               3, "backend")
    # Login Success（設定フェーズ無し）。
    text = sub(text,
               "if (serverProtocol == 485 || serverProtocol == 490 || serverProtocol == 498 || serverProtocol == 573 || serverProtocol == 575 || serverProtocol == 578 || serverProtocol == 758 || serverProtocol == 759 || serverProtocol == 760 || serverProtocol == 761 || serverProtocol == 762 || serverProtocol == 763) {",
               "if (serverProtocol == 480 || serverProtocol == 485 || serverProtocol == 490 || serverProtocol == 498 || serverProtocol == 573 || serverProtocol == 575 || serverProtocol == 578 || serverProtocol == 758 || serverProtocol == 759 || serverProtocol == 760 || serverProtocol == 761 || serverProtocol == 762 || serverProtocol == 763) {",
               1, "backend")
    # 受信チャット（旧式 {JSON, position}）。
    text = sub(text,
               "PlayerChat chat = serverProtocol == 578 || serverProtocol == 575 || serverProtocol == 573 || serverProtocol == 498 || serverProtocol == 490 || serverProtocol == 485",
               "PlayerChat chat = serverProtocol == 578 || serverProtocol == 575 || serverProtocol == 573 || serverProtocol == 498 || serverProtocol == 490 || serverProtocol == 485 || serverProtocol == 480",
               1, "backend")
    # Player Info（旧式 action 形式。crypto は 480 にも無い）。
    text = sub(text,
               "if (serverProtocol == 485 || serverProtocol == 490 || serverProtocol == 498 || serverProtocol == 573 || serverProtocol == 575 || serverProtocol == 578 || serverProtocol == 758 || serverProtocol == 759 || serverProtocol == 760) {",
               "if (serverProtocol == 480 || serverProtocol == 485 || serverProtocol == 490 || serverProtocol == 498 || serverProtocol == 573 || serverProtocol == 575 || serverProtocol == 578 || serverProtocol == 758 || serverProtocol == 759 || serverProtocol == 760) {",
               1, "backend")
    text = sub(text,
               "recordPlayerInfo760(packet.payload(), serverProtocol != 758 && serverProtocol != 578 && serverProtocol != 575 && serverProtocol != 573 && serverProtocol != 498 && serverProtocol != 490 && serverProtocol != 485);",
               "recordPlayerInfo760(packet.payload(), serverProtocol != 758 && serverProtocol != 578 && serverProtocol != 575 && serverProtocol != 573 && serverProtocol != 498 && serverProtocol != 490 && serverProtocol != 485 && serverProtocol != 480);",
               1, "backend")
    # Respawn の次元（先頭 i32）。
    text = sub(text,
               "} else if (serverProtocol == 578 || serverProtocol == 575 || serverProtocol == 573 || serverProtocol == 498 || serverProtocol == 490 || serverProtocol == 485) {",
               "} else if (serverProtocol == 578 || serverProtocol == 575 || serverProtocol == 573 || serverProtocol == 498 || serverProtocol == 490 || serverProtocol == 485 || serverProtocol == 480) {",
               1, "backend")
    # Spawn Entity Living（480 も末尾メタデータ付き。490 用パースを共用）。
    text = sub(text,
               "BedrockEntityTranslator.SpawnEntity spawn = serverProtocol == 498 || serverProtocol == 490 || serverProtocol == 485\n",
               "BedrockEntityTranslator.SpawnEntity spawn = serverProtocol == 498 || serverProtocol == 490 || serverProtocol == 485 || serverProtocol == 480\n",
               1, "backend")
    # Named Entity Spawn（480 も末尾メタデータ付き。578 用パースを共用）。
    text = sub(text,
               "BedrockEntityTranslator.SpawnEntity spawn = serverProtocol == 578 || serverProtocol == 575 || serverProtocol == 573 || serverProtocol == 498 || serverProtocol == 490 || serverProtocol == 485\n",
               "BedrockEntityTranslator.SpawnEntity spawn = serverProtocol == 578 || serverProtocol == 575 || serverProtocol == 573 || serverProtocol == 498 || serverProtocol == 490 || serverProtocol == 485 || serverProtocol == 480\n",
               1, "backend")
    # Join Game（480 も parseJoinGame490 で読める。バイオーム名だけ 480 用）。
    text = sub(text,
               "        if (serverProtocol == 498 || serverProtocol == 490 || serverProtocol == 485) {",
               "        if (serverProtocol == 498 || serverProtocol == 490 || serverProtocol == 485 || serverProtocol == 480) {",
               1, "backend")
    text = sub(text,
               "            biomeNames = serverProtocol == 485\n"
               "                    ? RegistryRemapper.biomeNames485()\n",
               "            biomeNames = serverProtocol == 480\n"
               "                    ? RegistryRemapper.biomeNames480()\n"
               "                    : serverProtocol == 485\n"
               "                    ? RegistryRemapper.biomeNames485()\n",
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
    if "PROTOCOL_480" in text:
        print("skip（既存）: BedrockChunkTranslator")
        return
    # normalizeBlockState の 485 ケースを複製して 480 ケースを作る。
    text = dup_case(text, "normalizeBlockState", "PROTOCOL_485", "PROTOCOL_498",
                    [("PROTOCOL_485", "PROTOCOL_480"),
                     ("mapBlockState485To758", "mapBlockState480To758"),
                     ("485 → 758", "480 → 758")], "chunk")
    # translate() の振り分け（チャンク形式は 485 と同一）。
    text = sub(text,
               "        if (input.serverProtocol() == RegistryRemapper.PROTOCOL_485) {\n"
               "            // チャンク形式は 490 と同一（バイオームは末尾の256件）。\n"
               "            return translate498(input);\n"
               "        }",
               "        if (input.serverProtocol() == RegistryRemapper.PROTOCOL_480) {\n"
               "            // チャンク形式は 485 と同一（バイオームは末尾の256件）。\n"
               "            return translate498(input);\n"
               "        }\n"
               "        if (input.serverProtocol() == RegistryRemapper.PROTOCOL_485) {\n"
               "            // チャンク形式は 490 と同一（バイオームは末尾の256件）。\n"
               "            return translate498(input);\n"
               "        }",
               1, "chunk")
    p.write_text(text, encoding="utf-8")
    print("BedrockChunkTranslator.java を更新した")


def patch_entity():
    p = BASE / "BedrockEntityTranslator.java"
    text = p.read_text(encoding="utf-8")
    if "PROTOCOL_480" in text:
        print("skip（既存）: BedrockEntityTranslator")
        return
    # normalizeEntityType の 485 ケースを複製。
    text = dup_case(text, "normalizeEntityType", "PROTOCOL_485", "PROTOCOL_498",
                    [("PROTOCOL_485", "PROTOCOL_480"),
                     ("mapEntityType485To758", "mapEntityType480To758"),
                     ("485 → 758", "480 → 758")], "entity")
    # normalizeItem の 485 ケースを複製。
    text = dup_case(text, "normalizeItem", "PROTOCOL_485", "PROTOCOL_490",
                    [("PROTOCOL_485", "PROTOCOL_480"),
                     ("mapItem485To758", "mapItem480To758"),
                     ("485 → 758", "480 → 758")], "entity")
    # Spawn Entity の objectData 形式（480 も headPitch 無し・i32）。
    text = sub(text,
               "                || serverProtocol == RegistryRemapper.PROTOCOL_485) {",
               "                || serverProtocol == RegistryRemapper.PROTOCOL_485\n"
               "                || serverProtocol == RegistryRemapper.PROTOCOL_480) {",
               1, "entity")
    p.write_text(text, encoding="utf-8")
    print("BedrockEntityTranslator.java を更新した")


if __name__ == "__main__":
    patch_backend()
    patch_chunk()
    patch_entity()
