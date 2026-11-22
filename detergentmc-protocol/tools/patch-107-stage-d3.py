#!/usr/bin/env python3
"""107 Stage D の Bedrock 配線を既存ファイルへ適用する（108 の D3 と同型）。

対象：BedrockChunkTranslator、BedrockEntityTranslator、
JavaBackendConnection。107 のスポーン形式・種別表は 108 と完全
同一のため、パースは 108 の関数へ委譲する（種別番号が違う
498 用関数には流さない）。

チャンクについては 107 固有の癖（dataLen 内の大きなゼロ埋め）に
対処する：translate340 の末尾検証を、107 の場合だけ「残りが全部
ゼロなら読み捨てる」に緩める。他プロトコルの検証は従来どおり。
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
    ("            case RegistryRemapper.PROTOCOL_108, RegistryRemapper.PROTOCOL_109,",
     "            case RegistryRemapper.PROTOCOL_107, RegistryRemapper.PROTOCOL_108, RegistryRemapper.PROTOCOL_109,", 1),
    ("                || input.serverProtocol() == RegistryRemapper.PROTOCOL_108) {",
     "                || input.serverProtocol() == RegistryRemapper.PROTOCOL_108\n"
     "                || input.serverProtocol() == RegistryRemapper.PROTOCOL_107) {", 1),
    ("""                if (groundUp) {
                    if (sections.remaining() != 256) {
                        throw new IllegalArgumentException("340 チャンクの末尾が合いません");
                    }
                    parsedBiomes = new int[256];
                    for (int i = 0; i < 256; i++) {
                        parsedBiomes[i] = sections.readUnsignedByte();
                    }
                } else if (sections.remaining() != 0) {
                    throw new IllegalArgumentException("340 チャンクの末尾が合いません");
                }""",
     """                if (groundUp) {
                    if (sections.remaining() < 256) {
                        throw new IllegalArgumentException("340 チャンクの末尾が合いません");
                    }
                    parsedBiomes = new int[256];
                    for (int i = 0; i < 256; i++) {
                        parsedBiomes[i] = sections.readUnsignedByte();
                    }
                    requireZeroPaddedTail(sections, input);
                } else {
                    requireZeroPaddedTail(sections, input);
                }""", 1),
    ("""    private static TranslatedChunk translate340(ChunkInput input) {""",
     """    /**
     * セクション本体の後ろに残ったバイトを検証する。通常は0バイトで
     * なければならないが、107（1.9）のチャンクは dataLen の内側に
     * 大きなゼロ埋めを含む癖があるため（Stage A 実測：153,600バイト
     * 等が全部ゼロ）、107 の場合だけ「残りが全部ゼロ」を条件に
     * 読み捨てる。他プロトコルの末尾検証は従来どおり厳密に行う。
     */
    private static void requireZeroPaddedTail(PacketIO.Reader sections, ChunkInput input) {
        if (sections.remaining() == 0) {
            return;
        }
        if (input.serverProtocol() != RegistryRemapper.PROTOCOL_107) {
            throw new IllegalArgumentException("340 チャンクの末尾が合いません");
        }
        while (sections.remaining() > 0) {
            if (sections.readUnsignedByte() != 0) {
                throw new IllegalArgumentException("107 チャンクの末尾のゼロ埋めが崩れています");
            }
        }
    }

    private static TranslatedChunk translate340(ChunkInput input) {""", 1),
])

patch(BEDROCK / "BedrockEntityTranslator.java", [
    ("            case RegistryRemapper.PROTOCOL_108, RegistryRemapper.PROTOCOL_109,",
     "            case RegistryRemapper.PROTOCOL_107, RegistryRemapper.PROTOCOL_108, RegistryRemapper.PROTOCOL_109,", 2),
    ("""    /**
     * 108（1.9.1）の Spawn Entity（オブジェクト系）を読む。形式は""",
     """    /**
     * 107（1.9）の Spawn Entity（オブジェクト系）を読む。形式は
     * 108 と同一で、種別表も 108 と完全同一のため、108 のパースへ
     * 委譲する（種別番号が違う 498 用関数には流さない）。
     */
    public static SpawnEntity parseSpawnEntity107(byte[] payload) {
        return parseSpawnEntity108(payload);
    }

    /**
     * 108（1.9.1）の Spawn Entity（オブジェクト系）を読む。形式は""", 1),
    ("""    /**
     * 108（1.9.1）の Spawn Entity Living を読む。形式は 109 と同一で、""",
     """    /**
     * 107（1.9）の Spawn Entity Living を読む。形式は 108 と同一で、
     * 種別表も 108 と完全同一のため、108 のパースへ委譲する
     * （種別番号が違う 498 用関数には流さない）。
     */
    public static SpawnEntity parseSpawnEntityLiving107(byte[] payload) {
        return parseSpawnEntityLiving108(payload);
    }

    /**
     * 108（1.9.1）の Spawn Entity Living を読む。形式は 109 と同一で、""", 1),
])

patch(BEDROCK / "JavaBackendConnection.java", [
    ("serverProtocol == 109 || serverProtocol == 108) {",
     "serverProtocol == 109 || serverProtocol == 108 || serverProtocol == 107) {", 5),
    ("serverProtocol == 109 || serverProtocol == 108 || serverProtocol == 393",
     "serverProtocol == 109 || serverProtocol == 108 || serverProtocol == 107 || serverProtocol == 393", 2),
    ("serverProtocol != 109 && serverProtocol != 108);",
     "serverProtocol != 109 && serverProtocol != 108 && serverProtocol != 107);", 1),
    ("""serverProtocol == 109 || serverProtocol == 108
                    ? extractOldChat578(packet.payload())""",
     """serverProtocol == 109 || serverProtocol == 108 || serverProtocol == 107
                    ? extractOldChat578(packet.payload())""", 1),
    ("""                BedrockEntityTranslator.SpawnEntity spawn = serverProtocol == 108
                        ? BedrockEntityTranslator.parseSpawnEntity108(packet.payload())""",
     """                BedrockEntityTranslator.SpawnEntity spawn = serverProtocol == 107
                        ? BedrockEntityTranslator.parseSpawnEntity107(packet.payload())
                        : serverProtocol == 108
                        ? BedrockEntityTranslator.parseSpawnEntity108(packet.payload())""", 1),
    ("""                BedrockEntityTranslator.SpawnEntity spawn = serverProtocol == 108
                        ? BedrockEntityTranslator.parseSpawnEntityLiving108(packet.payload())""",
     """                BedrockEntityTranslator.SpawnEntity spawn = serverProtocol == 107
                        ? BedrockEntityTranslator.parseSpawnEntityLiving107(packet.payload())
                        : serverProtocol == 108
                        ? BedrockEntityTranslator.parseSpawnEntityLiving108(packet.payload())""", 1),
    ("""|| serverProtocol == 109 || serverProtocol == 108
                        ? BedrockEntityTranslator.parseSpawnEntity340(packet.payload())""",
     """|| serverProtocol == 109 || serverProtocol == 108 || serverProtocol == 107
                        ? BedrockEntityTranslator.parseSpawnEntity340(packet.payload())""", 1),
    ("""|| serverProtocol == 109 || serverProtocol == 108
                        ? BedrockEntityTranslator.parseSpawnEntityLiving340(packet.payload())""",
     """|| serverProtocol == 109 || serverProtocol == 108 || serverProtocol == 107
                        ? BedrockEntityTranslator.parseSpawnEntityLiving340(packet.payload())""", 1),
    ("""|| serverProtocol == 109 || serverProtocol == 108
                        ? BedrockEntityTranslator.parseNamedEntitySpawn578(packet.payload())""",
     """|| serverProtocol == 109 || serverProtocol == 108 || serverProtocol == 107
                        ? BedrockEntityTranslator.parseNamedEntitySpawn578(packet.payload())""", 1),
])

if failures:
    print("失敗した置換:")
    for f in failures:
        print(" ", f)
    sys.exit(1)
print("全ての置換が期待件数で適用できた。")
