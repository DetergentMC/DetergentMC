#!/usr/bin/env python3
"""109 Stage D：Bedrock → 109 バックエンド対応（110 の D3 と同型）。

- BedrockChunkTranslator：109 のチャンク形式は 110 と同一なので
  110／210／315 と同じ分岐へ入れる。
- BedrockEntityTranslator：ケース列挙へ 109 を追加し、専用パース
  parseSpawnEntity109 / parseSpawnEntityLiving109 を新設する。
  109 の種別番号は 110 と完全同一（レジストリ差分ゼロを Stage A で
  実測）なので 110 のパースへ委譲する（種別番号が違う 498 用関数
  には流さない）。
- JavaBackendConnection：プロトコル列挙へ 109 を 110 の隣に追加し、
  スポーン振り分けの先頭へ 109 を入れる。
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
    ("case RegistryRemapper.PROTOCOL_110, RegistryRemapper.PROTOCOL_210,",
     "case RegistryRemapper.PROTOCOL_109, RegistryRemapper.PROTOCOL_110, RegistryRemapper.PROTOCOL_210,", 1),
    ("""                || input.serverProtocol() == RegistryRemapper.PROTOCOL_210
                || input.serverProtocol() == RegistryRemapper.PROTOCOL_110) {""",
     """                || input.serverProtocol() == RegistryRemapper.PROTOCOL_210
                || input.serverProtocol() == RegistryRemapper.PROTOCOL_110
                || input.serverProtocol() == RegistryRemapper.PROTOCOL_109) {""", 1),
])

patch(BEDROCK / "BedrockEntityTranslator.java", [
    ("case RegistryRemapper.PROTOCOL_110, RegistryRemapper.PROTOCOL_210,",
     "case RegistryRemapper.PROTOCOL_109, RegistryRemapper.PROTOCOL_110, RegistryRemapper.PROTOCOL_210,", 2),
    ("""    public static SpawnEntity parseSpawnEntity110(byte[] payload) {""",
     """    /**
     * 109（1.9.2）の Spawn Entity（オブジェクト系）を読む。形式は
     * 110 と同一で、種別表も 110 と完全同一のため、110 のパースへ
     * 委譲する（種別番号が違う 498 用関数には流さない）。
     */
    public static SpawnEntity parseSpawnEntity109(byte[] payload) {
        return parseSpawnEntity110(payload);
    }

    public static SpawnEntity parseSpawnEntity110(byte[] payload) {""", 1),
])

# Living 側は 110 の Living パース定義の直前へ 109 を差し込む。
path = BEDROCK / "BedrockEntityTranslator.java"
text = path.read_text(encoding="utf-8")
anchor = "parseSpawnEntityLiving110(byte[] payload) {"
idx = text.index(anchor)
jstart = text.rindex("    /**", 0, idx)
addition = """    /**
     * 109（1.9.2）の Spawn Entity Living を読む。形式は 110 と同一で、
     * 種別表も 110 と完全同一のため、110 のパースへ委譲する
     * （種別番号が違う 498 用関数には流さない）。
     */
    public static SpawnEntity parseSpawnEntityLiving109(byte[] payload) {
        return parseSpawnEntityLiving110(payload);
    }

"""
path.write_text(text[:jstart] + addition + text[jstart:], encoding="utf-8")
print("BedrockEntityTranslator.java: Living109 を追加した")

patch(BEDROCK / "JavaBackendConnection.java", [
    ("|| serverProtocol == 315 || serverProtocol == 210 || serverProtocol == 110) {",
     "|| serverProtocol == 315 || serverProtocol == 210 || serverProtocol == 110 || serverProtocol == 109) {", 5),
    ("serverProtocol == 210 || serverProtocol == 110 || serverProtocol == 393",
     "serverProtocol == 210 || serverProtocol == 110 || serverProtocol == 109 || serverProtocol == 393", 2),
    ("|| serverProtocol == 210 || serverProtocol == 110\n",
     "|| serverProtocol == 210 || serverProtocol == 110 || serverProtocol == 109\n", 4),
    ("&& serverProtocol != 210 && serverProtocol != 110);",
     "&& serverProtocol != 210 && serverProtocol != 110 && serverProtocol != 109);", 1),
    ("""                BedrockEntityTranslator.SpawnEntity spawn = serverProtocol == 110
                        ? BedrockEntityTranslator.parseSpawnEntity110(packet.payload())""",
     """                BedrockEntityTranslator.SpawnEntity spawn = serverProtocol == 109
                        ? BedrockEntityTranslator.parseSpawnEntity109(packet.payload())
                        : serverProtocol == 110
                        ? BedrockEntityTranslator.parseSpawnEntity110(packet.payload())""", 1),
    ("""                BedrockEntityTranslator.SpawnEntity spawn = serverProtocol == 110
                        ? BedrockEntityTranslator.parseSpawnEntityLiving110(packet.payload())""",
     """                BedrockEntityTranslator.SpawnEntity spawn = serverProtocol == 109
                        ? BedrockEntityTranslator.parseSpawnEntityLiving109(packet.payload())
                        : serverProtocol == 110
                        ? BedrockEntityTranslator.parseSpawnEntityLiving110(packet.payload())""", 1),
])

if failures:
    print("失敗した置換:")
    for f in failures:
        print(" ", f)
    sys.exit(1)
print("全ての置換が期待件数で適用できた。")
