#!/usr/bin/env python3
"""110 Stage D：Bedrock → 110 バックエンド対応（210 の D3 と同型）。

- BedrockChunkTranslator：110 のチャンク形式は 210 と同一なので
  210／315 と同じ分岐へ入れる。
- BedrockEntityTranslator：ケース列挙へ 110 を追加し、専用パース
  parseSpawnEntity110 / parseSpawnEntityLiving110 を新設する。
  110 の種別番号は 210 から polar_bear（102）を除いたものなので
  210 のパースへ委譲する（種別番号が違う 498 用関数には流さない）。
- JavaBackendConnection：プロトコル列挙へ 110 を 210 の隣に追加し、
  スポーン振り分けの先頭へ 110 を入れる。
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
    ("case RegistryRemapper.PROTOCOL_210, RegistryRemapper.PROTOCOL_315,",
     "case RegistryRemapper.PROTOCOL_110, RegistryRemapper.PROTOCOL_210, RegistryRemapper.PROTOCOL_315,", 1),
    ("""                || input.serverProtocol() == RegistryRemapper.PROTOCOL_315
                || input.serverProtocol() == RegistryRemapper.PROTOCOL_210) {""",
     """                || input.serverProtocol() == RegistryRemapper.PROTOCOL_315
                || input.serverProtocol() == RegistryRemapper.PROTOCOL_210
                || input.serverProtocol() == RegistryRemapper.PROTOCOL_110) {""", 1),
])

patch(BEDROCK / "BedrockEntityTranslator.java", [
    ("case RegistryRemapper.PROTOCOL_210, RegistryRemapper.PROTOCOL_315,",
     "case RegistryRemapper.PROTOCOL_110, RegistryRemapper.PROTOCOL_210, RegistryRemapper.PROTOCOL_315,", 2),
    ("""    public static SpawnEntity parseSpawnEntity210(byte[] payload) {""",
     """    /**
     * 110（1.9.4）の Spawn Entity（オブジェクト系）を読む。形式は
     * 210 と同一で、種別表も polar_bear を除いて同一のため、210 の
     * パースへ委譲する（種別番号が違う 498 用関数には流さない）。
     */
    public static SpawnEntity parseSpawnEntity110(byte[] payload) {
        return parseSpawnEntity210(payload);
    }

    public static SpawnEntity parseSpawnEntity210(byte[] payload) {""", 1),
])

# Living 側は 210 の Living パース定義の直前へ 110 を差し込む。
path = BEDROCK / "BedrockEntityTranslator.java"
text = path.read_text(encoding="utf-8")
anchor = "parseSpawnEntityLiving210(byte[] payload) {"
idx = text.index(anchor)
jstart = text.rindex("    /**", 0, idx)
addition = """    /**
     * 110（1.9.4）の Spawn Entity Living を読む。形式は 210 と同一で、
     * 種別表も polar_bear を除いて同一のため、210 のパースへ委譲する
     * （種別番号が違う 498 用関数には流さない）。
     */
    public static SpawnEntity parseSpawnEntityLiving110(byte[] payload) {
        return parseSpawnEntityLiving210(payload);
    }

"""
path.write_text(text[:jstart] + addition + text[jstart:], encoding="utf-8")
print("BedrockEntityTranslator.java: Living110 を追加した")

patch(BEDROCK / "JavaBackendConnection.java", [
    ("|| serverProtocol == 315 || serverProtocol == 210) {",
     "|| serverProtocol == 315 || serverProtocol == 210 || serverProtocol == 110) {", 5),
    # 中間列挙（210 の直後が 393 のもの）2件。素の "== 210 ||" は
    # 他の列挙にも多数現れるため、この形で対象を絞る。
    ("serverProtocol == 210 || serverProtocol == 393",
     "serverProtocol == 210 || serverProtocol == 110 || serverProtocol == 393", 2),
    # 行末の列挙（チャット判定・Living スポーン判定・340フォールバック×2）。
    ("|| serverProtocol == 210\n",
     "|| serverProtocol == 210 || serverProtocol == 110\n", 4),
    ("&& serverProtocol != 210);",
     "&& serverProtocol != 210 && serverProtocol != 110);", 1),
    ("""                BedrockEntityTranslator.SpawnEntity spawn = serverProtocol == 210
                        ? BedrockEntityTranslator.parseSpawnEntity210(packet.payload())""",
     """                BedrockEntityTranslator.SpawnEntity spawn = serverProtocol == 110
                        ? BedrockEntityTranslator.parseSpawnEntity110(packet.payload())
                        : serverProtocol == 210
                        ? BedrockEntityTranslator.parseSpawnEntity210(packet.payload())""", 1),
    ("""                BedrockEntityTranslator.SpawnEntity spawn = serverProtocol == 210
                        ? BedrockEntityTranslator.parseSpawnEntityLiving210(packet.payload())""",
     """                BedrockEntityTranslator.SpawnEntity spawn = serverProtocol == 110
                        ? BedrockEntityTranslator.parseSpawnEntityLiving110(packet.payload())
                        : serverProtocol == 210
                        ? BedrockEntityTranslator.parseSpawnEntityLiving210(packet.payload())""", 1),
])

if failures:
    print("失敗した置換:")
    for f in failures:
        print(" ", f)
    sys.exit(1)
print("全ての置換が期待件数で適用できた。")
