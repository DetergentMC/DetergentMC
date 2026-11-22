#!/usr/bin/env python3
"""316 Stage D3 の配線を Bedrock モジュールへ適用する（335 の D3 と同型）。

335 の Bedrock バックエンド形式は 338／340 と完全同一のため、変換は
340 の関数を共用し、入口だけ parseSpawnEntity316／Living335 を新設する
（種別がずれる疑いのある 498 用関数には流さない）。JavaBackendConnection
の列挙（Player Info の crypto 有無の否定列挙を含む）へ 335 を追加する。
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
    ("            case RegistryRemapper.PROTOCOL_335, RegistryRemapper.PROTOCOL_340 -> {",
     "            case RegistryRemapper.PROTOCOL_316, RegistryRemapper.PROTOCOL_335, RegistryRemapper.PROTOCOL_340 -> {", 1),
    ("""        if (input.serverProtocol() == RegistryRemapper.PROTOCOL_340
                || input.serverProtocol() == RegistryRemapper.PROTOCOL_335) {""",
     """        if (input.serverProtocol() == RegistryRemapper.PROTOCOL_340
                || input.serverProtocol() == RegistryRemapper.PROTOCOL_335
                || input.serverProtocol() == RegistryRemapper.PROTOCOL_316) {""", 1),
])

patch(BEDROCK / "BedrockEntityTranslator.java", [
    ("            case RegistryRemapper.PROTOCOL_335, RegistryRemapper.PROTOCOL_340 -> {",
     "            case RegistryRemapper.PROTOCOL_316, RegistryRemapper.PROTOCOL_335, RegistryRemapper.PROTOCOL_340 -> {", 2),
    ("""    /**
     * 335（1.12）の Spawn Entity（オブジェクト系）を読む。形式も
     * 種別表も 340 と完全同一のため、340 のパースへ委譲する
     * （種別番号が違う 498 用関数には流さない）。
     */
    public static SpawnEntity parseSpawnEntity335(byte[] payload) {""",
     """    /**
     * 316（1.11.2）の Spawn Entity（オブジェクト系）を読む。形式も
     * 種別表も 340 と完全同一のため、340 のパースへ委譲する
     * （種別番号が違う 498 用関数には流さない）。
     */
    public static SpawnEntity parseSpawnEntity316(byte[] payload) {
        return parseSpawnEntity340(payload);
    }

    /**
     * 335（1.12）の Spawn Entity（オブジェクト系）を読む。形式も
     * 種別表も 340 と完全同一のため、340 のパースへ委譲する
     * （種別番号が違う 498 用関数には流さない）。
     */
    public static SpawnEntity parseSpawnEntity335(byte[] payload) {""", 1),
    ("""    /**
     * 335（1.12）の Spawn Entity Living を読む。形式も種別表も
     * 340 と完全同一のため、340 のパースへ委譲する
     * （種別番号が違う 498 用関数には流さない）。
     */
    public static SpawnEntity parseSpawnEntityLiving335(byte[] payload) {""",
     """    /**
     * 316（1.11.2）の Spawn Entity Living を読む。形式も種別表も
     * 340 と完全同一のため、340 のパースへ委譲する
     * （種別番号が違う 498 用関数には流さない）。
     */
    public static SpawnEntity parseSpawnEntityLiving316(byte[] payload) {
        return parseSpawnEntityLiving340(payload);
    }

    /**
     * 335（1.12）の Spawn Entity Living を読む。形式も種別表も
     * 340 と完全同一のため、340 のパースへ委譲する
     * （種別番号が違う 498 用関数には流さない）。
     */
    public static SpawnEntity parseSpawnEntityLiving335(byte[] payload) {""", 1),
])

patch(BEDROCK / "JavaBackendConnection.java", [
    # 末尾型の肯定列挙（チャット・チャットコマンド・Login Start・Respawn）。
    # parseJoinGame の同型行は末尾の専用置換が担うため、ここでは4件。
    ("serverProtocol == 340 || serverProtocol == 335) {",
     "serverProtocol == 340 || serverProtocol == 335 || serverProtocol == 316) {", 4),
    # 中間型の肯定列挙（Login Success・Player Info）
    ("serverProtocol == 340 || serverProtocol == 335 || serverProtocol == 393",
     "serverProtocol == 340 || serverProtocol == 335 || serverProtocol == 316 || serverProtocol == 393", 2),
    # 行末型の肯定列挙（旧チャット抽出・Spawn 系の 578 形式振り分け・
    # Spawn 振り分けの継続行2件を含むため4件）
    ("serverProtocol == 340 || serverProtocol == 335\n",
     "serverProtocol == 340 || serverProtocol == 335 || serverProtocol == 316\n", 4),
    # Player Info の crypto 有無の否定列挙（落とすとプレイヤー一覧が消える罠）
    ("serverProtocol != 340 && serverProtocol != 335);",
     "serverProtocol != 340 && serverProtocol != 335 && serverProtocol != 316);", 1),
    # Spawn Entity の振り分けに 335 の入口を追加
    ("""                BedrockEntityTranslator.SpawnEntity spawn = serverProtocol == 335
                        ? BedrockEntityTranslator.parseSpawnEntity335(packet.payload())""",
     """                BedrockEntityTranslator.SpawnEntity spawn = serverProtocol == 316
                        ? BedrockEntityTranslator.parseSpawnEntity316(packet.payload())
                        : serverProtocol == 335
                        ? BedrockEntityTranslator.parseSpawnEntity335(packet.payload())""", 1),
    ("""                BedrockEntityTranslator.SpawnEntity spawn = serverProtocol == 335
                        ? BedrockEntityTranslator.parseSpawnEntityLiving335(packet.payload())""",
     """                BedrockEntityTranslator.SpawnEntity spawn = serverProtocol == 316
                        ? BedrockEntityTranslator.parseSpawnEntityLiving316(packet.payload())
                        : serverProtocol == 335
                        ? BedrockEntityTranslator.parseSpawnEntityLiving335(packet.payload())""", 1),
    # Join Game のパース（340 形式を共用）
    ("        if (serverProtocol == 340 || serverProtocol == 335) {",
     "        if (serverProtocol == 340 || serverProtocol == 335 || serverProtocol == 316) {", 1),
])

if failures:
    print("失敗した置換:")
    for f in failures:
        print(" ", f)
    sys.exit(1)
print("全ての置換が期待件数で適用できた。")
