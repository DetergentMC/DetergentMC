#!/usr/bin/env python3
"""108 Stage D：Bedrock → 108 バックエンド対応（109 の D3 と同型）。

- BedrockChunkTranslator：108 のチャンク形式は 109 と同一なので
  109／210／315 と同じ分岐へ入れる。
- BedrockEntityTranslator：ケース列挙へ 108 を追加し、専用パース
  parseSpawnEntity108 / parseSpawnEntityLiving108 を新設する。
  108 の種別番号は 109 と完全同一（レジストリ差分ゼロを Stage A で
  実測）なので 109 のパースへ委譲する（種別番号が違う 498 用関数
  には流さない）。
- JavaBackendConnection：プロトコル列挙へ 108 を 109 の隣に追加し、
  スポーン振り分けの先頭へ 108 を入れる。
各置換は期待件数を検証し、合わなければ失敗として報告する。
"""

import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent.parent
BEDROCK = ROOT / "detergentmc-bedrock/src/main/java/io/detergentmc/bedrock"
failures = []


def patch(path, replacements):
    text = path.read_text(encoding="utf-8")
    for old, new, expected in replacements:
        count = text.count(old)
        if count != expected:
            failures.append(f"{path.name}: 期待 {expected} 件のところ {count} 件: {old[:70]!r}")
            continue
        text = text.replace(old, new)
    path.write_text(text, encoding="utf-8")
    print(f"{path.name}: 適用した")


patch(BEDROCK / "BedrockChunkTranslator.java", [
    ("case RegistryRemapper.PROTOCOL_109, RegistryRemapper.PROTOCOL_210,",
     "case RegistryRemapper.PROTOCOL_108, RegistryRemapper.PROTOCOL_109, RegistryRemapper.PROTOCOL_210,", 1),
    ("""                || input.serverProtocol() == RegistryRemapper.PROTOCOL_210
                || input.serverProtocol() == RegistryRemapper.PROTOCOL_109) {""",
     """                || input.serverProtocol() == RegistryRemapper.PROTOCOL_210
                || input.serverProtocol() == RegistryRemapper.PROTOCOL_109
                || input.serverProtocol() == RegistryRemapper.PROTOCOL_108) {""", 1),
])

patch(BEDROCK / "BedrockEntityTranslator.java", [
    ("case RegistryRemapper.PROTOCOL_109, RegistryRemapper.PROTOCOL_210,",
     "case RegistryRemapper.PROTOCOL_108, RegistryRemapper.PROTOCOL_109, RegistryRemapper.PROTOCOL_210,", 2),
    ("""    public static SpawnEntity parseSpawnEntity109(byte[] payload) {""",
     """    /**
     * 108（1.9.1）の Spawn Entity（オブジェクト系）を読む。形式は
     * 109 と同一で、種別表も 109 と完全同一のため、109 のパースへ
     * 委譲する（種別番号が違う 498 用関数には流さない）。
     */
    public static SpawnEntity parseSpawnEntity108(byte[] payload) {
        return parseSpawnEntity109(payload);
    }

    public static SpawnEntity parseSpawnEntity109(byte[] payload) {""", 1),
])

# Living 側は 109 の Living パース定義の直前へ 108 を差し込む。
path = BEDROCK / "BedrockEntityTranslator.java"
text = path.read_text(encoding="utf-8")
anchor = "parseSpawnEntityLiving109(byte[] payload) {"
idx = text.index(anchor)
jstart = text.rindex("    /**", 0, idx)
addition = """    /**
     * 108（1.9.1）の Spawn Entity Living を読む。形式は 109 と同一で、
     * 種別表も 109 と完全同一のため、109 のパースへ委譲する
     * （種別番号が違う 498 用関数には流さない）。
     */
    public static SpawnEntity parseSpawnEntityLiving108(byte[] payload) {
        return parseSpawnEntityLiving109(payload);
    }

"""
path.write_text(text[:jstart] + addition + text[jstart:], encoding="utf-8")
print("BedrockEntityTranslator.java: Living108 を追加した")

patch(BEDROCK / "JavaBackendConnection.java", [
    ("|| serverProtocol == 315 || serverProtocol == 210 || serverProtocol == 109) {",
     "|| serverProtocol == 315 || serverProtocol == 210 || serverProtocol == 109 || serverProtocol == 108) {", 5),
    ("serverProtocol == 210 || serverProtocol == 109 || serverProtocol == 393",
     "serverProtocol == 210 || serverProtocol == 109 || serverProtocol == 108 || serverProtocol == 393", 2),
    ("|| serverProtocol == 210 || serverProtocol == 109\n",
     "|| serverProtocol == 210 || serverProtocol == 109 || serverProtocol == 108\n", 4),
    ("&& serverProtocol != 210 && serverProtocol != 109);",
     "&& serverProtocol != 210 && serverProtocol != 109 && serverProtocol != 108);", 1),
    ("""                BedrockEntityTranslator.SpawnEntity spawn = serverProtocol == 109
                        ? BedrockEntityTranslator.parseSpawnEntity109(packet.payload())""",
     """                BedrockEntityTranslator.SpawnEntity spawn = serverProtocol == 108
                        ? BedrockEntityTranslator.parseSpawnEntity108(packet.payload())
                        : serverProtocol == 109
                        ? BedrockEntityTranslator.parseSpawnEntity109(packet.payload())""", 1),
    ("""                BedrockEntityTranslator.SpawnEntity spawn = serverProtocol == 109
                        ? BedrockEntityTranslator.parseSpawnEntityLiving109(packet.payload())""",
     """                BedrockEntityTranslator.SpawnEntity spawn = serverProtocol == 108
                        ? BedrockEntityTranslator.parseSpawnEntityLiving108(packet.payload())
                        : serverProtocol == 109
                        ? BedrockEntityTranslator.parseSpawnEntityLiving109(packet.payload())""", 1),
])

if failures:
    print("失敗した置換:")
    for f in failures:
        print(" ", f)
    sys.exit(1)
print("全ての置換が期待件数で適用できた。")
