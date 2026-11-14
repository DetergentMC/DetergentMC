#!/usr/bin/env python3
"""Bedrock 側の 767（1.21／1.21.1）対応。

- BedrockChunkTranslator.normalizeBlockState：767 は 767→765 表で正規化する。
- BedrockEntityTranslator：エンティティ／アイテム／エフェクト／パーティクルの
  767→765 正規化、readSlot の 766 分岐に 767 を追加、Update Attributes の
  766／767 番号キーと 767 の文字列 modifier UUID 対応。
  （Update Attributes の 766 番号キーは従来の読み方の誤りも直す。
  766／767 はキーが文字列ではなく番号。）
- JavaBackendConnection：Join Game／Respawn の 766 分岐に 767 を追加。
"""
import pathlib
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent.parent
BEDROCK = ROOT / "detergentmc-bedrock/src/main/java/io/detergentmc/bedrock"


def replace_once(text, old, new, where):
    if text.count(old) != 1:
        print(f"REPLACE 失敗（{where}、{text.count(old)}件）: {old[:90]!r}")
        sys.exit(1)
    return text.replace(old, new, 1)


def patch(path, pairs):
    text = path.read_text(encoding="utf-8")
    for old, new in pairs:
        text = replace_once(text, old, new, path.name)
    path.write_text(text, encoding="utf-8")
    print(f"{path.name} を更新しました。")


def main():
    # ---- BedrockChunkTranslator ----
    patch(BEDROCK / "BedrockChunkTranslator.java", [
        ("     * 765 はそのまま、764／766 は RegistryRemapper の上向き表、763 は\n"
         "     * 765→763 表の逆引き。範囲外は石（765 の 1）。",
         "     * 765 はそのまま、764／766 は RegistryRemapper の上向き表、767 は\n"
         "     * 767→765 表、763 は 765→763 表の逆引き。範囲外は石（765 の 1）。"),
        ("            case RegistryRemapper.PROTOCOL_766 -> RegistryRemapper.mapBlockStateUp766(stateId);",
         "            case RegistryRemapper.PROTOCOL_766 -> RegistryRemapper.mapBlockStateUp766(stateId);\n"
         "            case RegistryRemapper.PROTOCOL_767 -> RegistryRemapper.mapBlockState767To765(stateId);"),
    ])

    # ---- BedrockEntityTranslator ----
    patch(BEDROCK / "BedrockEntityTranslator.java", [
        ("            case RegistryRemapper.PROTOCOL_766 ->\n"
         "                    RegistryRemapper.mapEntityTypeUp766(typeId);",
         "            case RegistryRemapper.PROTOCOL_766 ->\n"
         "                    RegistryRemapper.mapEntityTypeUp766(typeId);\n"
         "            case RegistryRemapper.PROTOCOL_767 ->\n"
         "                    RegistryRemapper.mapEntityType767To765(typeId);"),
        ("            case RegistryRemapper.PROTOCOL_766 -> RegistryRemapper.mapItemUp766(itemId);",
         "            case RegistryRemapper.PROTOCOL_766 -> RegistryRemapper.mapItemUp766(itemId);\n"
         "            case RegistryRemapper.PROTOCOL_767 -> RegistryRemapper.mapItem767To765(itemId);"),
        ("            case RegistryRemapper.PROTOCOL_766 -> RegistryRemapper.mapEffectUp766(effectId);",
         "            case RegistryRemapper.PROTOCOL_766 -> RegistryRemapper.mapEffectUp766(effectId);\n"
         "            case RegistryRemapper.PROTOCOL_767 -> RegistryRemapper.mapEffect767To765(effectId);"),
        ("            case RegistryRemapper.PROTOCOL_766 ->\n"
         "                    RegistryRemapper.mapParticleUp766(particleId);",
         "            case RegistryRemapper.PROTOCOL_766 ->\n"
         "                    RegistryRemapper.mapParticleUp766(particleId);\n"
         "            case RegistryRemapper.PROTOCOL_767 ->\n"
         "                    RegistryRemapper.mapParticle767To765(particleId);"),
        ("     * 名前なし）を読み飛ばす。766 はデータコンポーネント形式で、中身は\n"
         "     * 読まずに個数だけ確認する（成分付きだと後ろは読めないため\n"
         "     * {@code clean=false} を返す）。",
         "     * 名前なし）を読み飛ばす。766／767 はデータコンポーネント形式で、中身は\n"
         "     * 読まずに個数だけ確認する（成分付きだと後ろは読めないため\n"
         "     * {@code clean=false} を返す）。"),
        ("        if (serverProtocol == RegistryRemapper.PROTOCOL_766) {",
         "        if (serverProtocol == RegistryRemapper.PROTOCOL_766\n"
         "                || serverProtocol == RegistryRemapper.PROTOCOL_767) {"),
        ("     * Update Attributes を読む。763 は属性キーが番号（0＝最大体力、\n"
         "     * 2＝ノックバック耐性、3＝移動速度。minecraft-data 1.20 の\n"
         "     * attributes.json の並びによる）、764／765／766 は識別子文字列。",
         "     * Update Attributes を読む。763 は属性キーが番号（0＝最大体力、\n"
         "     * 2＝ノックバック耐性、3＝移動速度。minecraft-data 1.20 の\n"
         "     * attributes.json の並びによる）、764／765 は識別子文字列、\n"
         "     * 766／767 は番号（属性名の表で識別子に直す）。\n"
         "     * 767 は modifier の UUID が文字列（766 までは 16 バイトの UUID）。"),
        ("            String key;\n"
         "            if (serverProtocol == RegistryRemapper.PROTOCOL_763) {\n"
         "                int keyId = reader.readVarInt();\n"
         "                key = switch (keyId) {\n"
         "                    case 0 -> \"minecraft:generic.max_health\";\n"
         "                    case 3 -> \"minecraft:generic.movement_speed\";\n"
         "                    default -> \"\";\n"
         "                };\n"
         "            } else {\n"
         "                key = reader.readString();\n"
         "            }",
         "            String key;\n"
         "            if (serverProtocol == RegistryRemapper.PROTOCOL_763) {\n"
         "                int keyId = reader.readVarInt();\n"
         "                key = switch (keyId) {\n"
         "                    case 0 -> \"minecraft:generic.max_health\";\n"
         "                    case 3 -> \"minecraft:generic.movement_speed\";\n"
         "                    default -> \"\";\n"
         "                };\n"
         "            } else if (serverProtocol == RegistryRemapper.PROTOCOL_766\n"
         "                    || serverProtocol == RegistryRemapper.PROTOCOL_767) {\n"
         "                // 766／767 は属性キーが番号。名前表で識別子に直す。\n"
         "                int keyId = reader.readVarInt();\n"
         "                String name = serverProtocol == RegistryRemapper.PROTOCOL_767\n"
         "                        ? RegistryRemapper.attributeName767(keyId)\n"
         "                        : RegistryRemapper.attributeName766(keyId);\n"
         "                key = name != null ? name : \"\";\n"
         "            } else {\n"
         "                key = reader.readString();\n"
         "            }"),
        ("            for (int j = 0; j < modifierCount; j++) {\n"
         "                reader.readBytes(16); // modifier UUID\n"
         "                reader.readDouble(); // amount\n"
         "                reader.readByte(); // operation\n"
         "            }",
         "            for (int j = 0; j < modifierCount; j++) {\n"
         "                if (serverProtocol == RegistryRemapper.PROTOCOL_767) {\n"
         "                    reader.readString(); // modifier UUID（767 は文字列）\n"
         "                } else {\n"
         "                    reader.readBytes(16); // modifier UUID\n"
         "                }\n"
         "                reader.readDouble(); // amount\n"
         "                reader.readByte(); // operation\n"
         "            }"),
    ])

    # ---- JavaBackendConnection ----
    patch(BEDROCK / "JavaBackendConnection.java", [
        ("    /** 次元種別の番号→名前（766 の Join Game／Respawn の番号解決用）。 */",
         "    /** 次元種別の番号→名前（766／767 の Join Game／Respawn の番号解決用）。 */"),
        ("                // 追加パック（765: 0x07、766: 0x09）。UUID を読んで accepted（3）で返す。",
         "                // 追加パック（765: 0x07、766／767: 0x09）。UUID を読んで accepted（3）で返す。"),
        ("                if (serverProtocol == 766) {\n"
         "                    int dimensionId = reader.readVarInt();",
         "                if (serverProtocol == 766 || serverProtocol == 767) {\n"
         "                    int dimensionId = reader.readVarInt();"),
        ("        if (serverProtocol == 766) {\n"
         "            PlayTranslator.JoinGame766 join = PlayTranslator.parseJoinGame766(payload);",
         "        if (serverProtocol == 766 || serverProtocol == 767) {\n"
         "            PlayTranslator.JoinGame766 join = PlayTranslator.parseJoinGame766(payload);"),
    ])


if __name__ == "__main__":
    main()
