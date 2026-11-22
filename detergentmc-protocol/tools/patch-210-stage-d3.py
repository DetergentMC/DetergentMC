#!/usr/bin/env python3
"""210 Stage D3 の配線を Bedrock モジュールへ適用する（315 の D3 と同型）。

210 の Bedrock バックエンド形式は 315 と完全同一のため、変換は
315 の入口を共用し、parseSpawnEntity210／Living210 を新設して
315 のパースへ委譲する（種別がずれる疑いのある 498 用関数には
流さない）。JavaBackendConnection の列挙（Player Info の crypto
有無の否定列挙を含む）へ 210 を追加する。
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
    ("            case RegistryRemapper.PROTOCOL_315, RegistryRemapper.PROTOCOL_335, RegistryRemapper.PROTOCOL_338, RegistryRemapper.PROTOCOL_340 -> {",
     "            case RegistryRemapper.PROTOCOL_210, RegistryRemapper.PROTOCOL_315, RegistryRemapper.PROTOCOL_335, RegistryRemapper.PROTOCOL_338, RegistryRemapper.PROTOCOL_340 -> {", 1),
    ("""        if (input.serverProtocol() == RegistryRemapper.PROTOCOL_340
                || input.serverProtocol() == RegistryRemapper.PROTOCOL_338
                || input.serverProtocol() == RegistryRemapper.PROTOCOL_335
                || input.serverProtocol() == RegistryRemapper.PROTOCOL_315) {""",
     """        if (input.serverProtocol() == RegistryRemapper.PROTOCOL_340
                || input.serverProtocol() == RegistryRemapper.PROTOCOL_338
                || input.serverProtocol() == RegistryRemapper.PROTOCOL_335
                || input.serverProtocol() == RegistryRemapper.PROTOCOL_315
                || input.serverProtocol() == RegistryRemapper.PROTOCOL_210) {""", 1),
])

patch(BEDROCK / "BedrockEntityTranslator.java", [
    ("            case RegistryRemapper.PROTOCOL_315, RegistryRemapper.PROTOCOL_335, RegistryRemapper.PROTOCOL_338, RegistryRemapper.PROTOCOL_340 -> {",
     "            case RegistryRemapper.PROTOCOL_210, RegistryRemapper.PROTOCOL_315, RegistryRemapper.PROTOCOL_335, RegistryRemapper.PROTOCOL_338, RegistryRemapper.PROTOCOL_340 -> {", 2),
    ("""    /**
     * 315（1.11）の Spawn Entity（オブジェクト系）を読む。形式も
     * 種別表も 340 と完全同一のため、340 のパースへ委譲する
     * （種別番号が違う 498 用関数には流さない）。
     */
    public static SpawnEntity parseSpawnEntity315(byte[] payload) {""",
     """    /**
     * 210（1.10.2）の Spawn Entity（オブジェクト系）を読む。形式も
     * 種別表も 315 と完全同一のため、315 のパースへ委譲する
     * （種別番号が違う 498 用関数には流さない）。
     */
    public static SpawnEntity parseSpawnEntity210(byte[] payload) {
        return parseSpawnEntity315(payload);
    }

    /**
     * 315（1.11）の Spawn Entity（オブジェクト系）を読む。形式も
     * 種別表も 340 と完全同一のため、340 のパースへ委譲する
     * （種別番号が違う 498 用関数には流さない）。
     */
    public static SpawnEntity parseSpawnEntity315(byte[] payload) {""", 1),
    ("""    /**
     * 315（1.11）の Spawn Entity Living を読む。形式も種別表も
     * 340 と完全同一のため、340 のパースへ委譲する
     * （種別番号が違う 498 用関数には流さない）。
     */
    public static SpawnEntity parseSpawnEntityLiving315(byte[] payload) {""",
     """    /**
     * 210（1.10.2）の Spawn Entity Living を読む。形式も種別表も
     * 315 と完全同一のため、315 のパースへ委譲する
     * （種別番号が違う 498 用関数には流さない）。
     */
    public static SpawnEntity parseSpawnEntityLiving210(byte[] payload) {
        return parseSpawnEntityLiving315(payload);
    }

    /**
     * 315（1.11）の Spawn Entity Living を読む。形式も種別表も
     * 340 と完全同一のため、340 のパースへ委譲する
     * （種別番号が違う 498 用関数には流さない）。
     */
    public static SpawnEntity parseSpawnEntityLiving315(byte[] payload) {""", 1),
])

patch(BEDROCK / "JavaBackendConnection.java", [
    # 末尾型の肯定列挙（末尾が 315 で閉じるもの5件）
    ("|| serverProtocol == 315) {",
     "|| serverProtocol == 315 || serverProtocol == 210) {", 5),
    # 中間型の肯定列挙（Login Success・Player Info）
    ("|| serverProtocol == 315 || serverProtocol == 393",
     "|| serverProtocol == 315 || serverProtocol == 210 || serverProtocol == 393", 2),
    # 行末型の肯定列挙（旧チャット抽出・Spawn 系の振り分け継続行）
    ("|| serverProtocol == 315\n",
     "|| serverProtocol == 315 || serverProtocol == 210\n", 4),
    # Player Info の crypto 有無の否定列挙（落とすとプレイヤー一覧が消える罠）
    ("&& serverProtocol != 315);",
     "&& serverProtocol != 315 && serverProtocol != 210);", 1),
    # Spawn Entity の振り分けに 210 の入口を追加
    ("""                BedrockEntityTranslator.SpawnEntity spawn = serverProtocol == 315
                        ? BedrockEntityTranslator.parseSpawnEntity315(packet.payload())""",
     """                BedrockEntityTranslator.SpawnEntity spawn = serverProtocol == 210
                        ? BedrockEntityTranslator.parseSpawnEntity210(packet.payload())
                        : serverProtocol == 315
                        ? BedrockEntityTranslator.parseSpawnEntity315(packet.payload())""", 1),
    ("""                BedrockEntityTranslator.SpawnEntity spawn = serverProtocol == 315
                        ? BedrockEntityTranslator.parseSpawnEntityLiving315(packet.payload())""",
     """                BedrockEntityTranslator.SpawnEntity spawn = serverProtocol == 210
                        ? BedrockEntityTranslator.parseSpawnEntityLiving210(packet.payload())
                        : serverProtocol == 315
                        ? BedrockEntityTranslator.parseSpawnEntityLiving315(packet.payload())""", 1),
])

if failures:
    print("失敗した置換:")
    for f in failures:
        print(" ", f)
    sys.exit(1)
print("全ての置換が期待件数で適用できた。")
