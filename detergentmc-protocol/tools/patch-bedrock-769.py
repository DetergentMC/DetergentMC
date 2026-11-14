#!/usr/bin/env python3
"""Java 1.21.4（プロトコル769）の Bedrock→Java バックエンド対応を入れる。

対象: detergentmc-bedrock の JavaBackendConnection（769 サーバーとの
入出力）と BedrockEntityTranslator／BedrockChunkTranslator（769 の
ID を 765 に正規化）。769 のペイロードは 768 と形が同じ箇所が多く、
既存の 768 分岐に 769 を足すだけで済む。例外は Player Info
（update_hat ビット 0x40 と update_list_order ビット 0x80 が新設）
で、recordPlayerInfo のループ上限を 0x80 まで伸ばして読み飛ばす。

方針は 768（コミット 4a6442c）のときと同じ：正規化は 765 を基準にし、
属性・エフェクトは 768 と同一の表（属性名は attributeName769、
768 と内容同一）を使う。実機 Bedrock クライアントでの確認は未実施
（既存の全 Bedrock 組み合わせと同様）。
"""
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent  # detergentmc-protocol/
BEDROCK = ROOT.parent / "detergentmc-bedrock" / "src" / "main" / "java" \
    / "io" / "detergentmc" / "bedrock"


def edit(path, pairs):
    src = path.read_text(encoding="utf-8")
    for label, old, new in pairs:
        n = src.count(old)
        if n != 1:
            print(f"NG: {path.name} {label}: anchor が {n} 回見つかった（1回のはず）",
                  file=sys.stderr)
            sys.exit(1)
        src = src.replace(old, new)
        print(f"OK: {path.name} {label}")
    path.write_text(src, encoding="utf-8")


CONN = BEDROCK / "JavaBackendConnection.java"
edit(CONN, [
    ("dimensionNamesById comment", """\
    /** 次元種別の番号→名前（766／767／768 の Join Game／Respawn の番号解決用）。 */""",
     """\
    /** 次元種別の番号→名前（766／767／768／769 の Join Game／Respawn の番号解決用）。 */"""),
    ("Add Resource Pack comment", """\
                // 追加パック（765: 0x07、766／767／768: 0x09）。UUID を読んで accepted（3）で返す。""",
     """\
                // 追加パック（765: 0x07、766／767／768／769: 0x09）。UUID を読んで accepted（3）で返す。"""),
    ("Sync Player Position 768/769", """\
            if (serverProtocol == 768) {
                // 768：teleportId が先頭、dx／dy／dz が追加、flags は u32。""",
     """\
            if (serverProtocol == 768 || serverProtocol == 769) {
                // 768／769：teleportId が先頭、dx／dy／dz が追加、flags は u32。
                // 769 の Synchronize Player Position は 768 と同形。"""),
    ("Respawn dimension 769", """\
                if (serverProtocol == 766 || serverProtocol == 767
                        || serverProtocol == 768) {""",
     """\
                if (serverProtocol == 766 || serverProtocol == 767
                        || serverProtocol == 768 || serverProtocol == 769) {"""),
    ("Join Game 769", """\
        if (serverProtocol == 766 || serverProtocol == 767 || serverProtocol == 768) {
            // 768 の Join Game は767形式に seaLevel を補ったもののため、
            // 767へ直してから読む（parseJoinGame766 は余分なバイトを拒む）。
            byte[] payload767 = serverProtocol == 768
                    ? PlayTranslator.translateJoinGame768To767(payload) : payload;""",
     """\
        if (serverProtocol == 766 || serverProtocol == 767 || serverProtocol == 768
                || serverProtocol == 769) {
            // 768／769 の Join Game は767形式に seaLevel を補ったもののため、
            // 767へ直してから読む（parseJoinGame766 は余分なバイトを拒む）。
            // 769 の Join Game は 768 と同形のため同じ変換が使える。
            byte[] payload767 = serverProtocol == 768 || serverProtocol == 769
                    ? PlayTranslator.translateJoinGame768To767(payload) : payload;"""),
    ("Client Information particleStatus 769", """\
        if (serverProtocol == 768) {
            // 768 は末尾に particleStatus varint がある（0=all）。
            // 付けないと 1.21.2／1.21.3 のバニラが読み取り途中で切断する。
            writer.writeVarInt(0);
        }""",
     """\
        if (serverProtocol == 768 || serverProtocol == 769) {
            // 768／769 は末尾に particleStatus varint がある（0=all）。
            // 付けないと 1.21.2／1.21.3／1.21.4 のバニラが読み取り途中で切断する。
            writer.writeVarInt(0);
        }"""),
    ("recordPlayerInfo loop to 0x80", """\
            // プレイヤー名・ゲームモードの対応表（チャット送信者・AddPlayer 用）。
            // ビットセット形式（0x01 add player、0x04 gamemode 等。763〜766共通）。
            recordPlayerInfo(packet.payload());""",
     """\
            // プレイヤー名・ゲームモードの対応表（チャット送信者・AddPlayer 用）。
            // ビットセット形式（0x01 add player、0x04 gamemode 等）。
            // 769 は update_hat（0x40）と update_list_order（0x80）が増えた。
            recordPlayerInfo(packet.payload());"""),
    ("recordPlayerInfo bit loop", """\
            int actions = reader.readVarInt();
            for (int bit = 0x01; bit <= 0x20; bit <<= 1) {""",
     """\
            int actions = reader.readVarInt();
            // 769 の update_hat（0x40）と update_list_order（0x80）も読む。
            // 読まないと後続のビットの並びがずれ、名前・ゲームモードが壊れる。
            for (int bit = 0x01; bit <= 0x80; bit <<= 1) {"""),
    ("recordPlayerInfo hat/list-order cases", """\
                        case 0x20 -> { // update display name
                            reader.readUuid();
                            if (reader.readBoolean()) {
                                if (serverProtocol == 763) {
                                    reader.readString(); // display name（JSON）
                                } else {
                                    reader.readAnonymousNbt(); // display name
                                }
                            }
                        }""",
     """\
                        case 0x20 -> { // update display name
                            reader.readUuid();
                            if (reader.readBoolean()) {
                                if (serverProtocol == 763) {
                                    reader.readString(); // display name（JSON）
                                } else {
                                    reader.readAnonymousNbt(); // display name
                                }
                            }
                        }
                        case 0x40 -> { // update hat（769 で新設）
                            reader.readUuid();
                            reader.readBoolean(); // showHat（帽子の表示）
                        }
                        case 0x80 -> { // update list order（769 で新設）
                            reader.readUuid();
                            reader.readVarInt(); // listPriority
                        }"""),
])

ENTITY = BEDROCK / "BedrockEntityTranslator.java"
edit(ENTITY, [
    ("normalizeEntityType 769", """\
            case RegistryRemapper.PROTOCOL_768 ->
                    RegistryRemapper.mapEntityType768To765(typeId);
            default -> (typeId < 0 || typeId > RegistryRemapper.MAX_ENTITY_TYPE_765)""",
     """\
            case RegistryRemapper.PROTOCOL_768 ->
                    RegistryRemapper.mapEntityType768To765(typeId);
            case RegistryRemapper.PROTOCOL_769 ->
                    RegistryRemapper.mapEntityType769To765(typeId);
            default -> (typeId < 0 || typeId > RegistryRemapper.MAX_ENTITY_TYPE_765)"""),
    ("normalizeItem 769", """\
            case RegistryRemapper.PROTOCOL_768 -> RegistryRemapper.mapItem768To765(itemId);
            default -> (itemId < 0 || itemId > RegistryRemapper.MAX_ITEM_765)""",
     """\
            case RegistryRemapper.PROTOCOL_768 -> RegistryRemapper.mapItem768To765(itemId);
            case RegistryRemapper.PROTOCOL_769 -> RegistryRemapper.mapItem769To765(itemId);
            default -> (itemId < 0 || itemId > RegistryRemapper.MAX_ITEM_765)"""),
    ("normalizeEffectTo765 769", """\
            // エフェクトは 767⇔768 で同一のため 768 も同じ表でよい。
            case RegistryRemapper.PROTOCOL_767, RegistryRemapper.PROTOCOL_768 ->
                    RegistryRemapper.mapEffect767To765(effectId);""",
     """\
            // エフェクトは 767⇔768⇔769 で同一のため 768／769 も同じ表でよい。
            case RegistryRemapper.PROTOCOL_767, RegistryRemapper.PROTOCOL_768,
                    RegistryRemapper.PROTOCOL_769 ->
                    RegistryRemapper.mapEffect767To765(effectId);"""),
    ("normalizeParticleTo765 769", """\
            case RegistryRemapper.PROTOCOL_768 ->
                    RegistryRemapper.mapParticle768To765(particleId);
            default -> (particleId < 0 || particleId > RegistryRemapper.MAX_PARTICLE_765)""",
     """\
            case RegistryRemapper.PROTOCOL_768 ->
                    RegistryRemapper.mapParticle768To765(particleId);
            case RegistryRemapper.PROTOCOL_769 ->
                    RegistryRemapper.mapParticle769To765(particleId);
            default -> (particleId < 0 || particleId > RegistryRemapper.MAX_PARTICLE_765)"""),
    ("readSlot javadoc 769", """\
    /**
     * Java のスロット 1 個を読む。765 までは NBT（763 は名前付き、764／765 は
     * 名前なし）を読み飛ばす。766／767／768 はデータコンポーネント形式で、中身は
     * 読まずに個数だけ確認する（成分付きだと後ろは読めないため
     * {@code clean=false} を返す）。768 のスロットの外形（個数・added・removed
     * の varint 3 個）は 767 と同じため、同じ読み方でよい。
     */""",
     """\
    /**
     * Java のスロット 1 個を読む。765 までは NBT（763 は名前付き、764／765 は
     * 名前なし）を読み飛ばす。766／767／768／769 はデータコンポーネント形式で、
     * 中身は読まずに個数だけ確認する（成分付きだと後ろは読めないため
     * {@code clean=false} を返す）。768／769 のスロットの外形（個数・added・removed
     * の varint 3 個）は 767 と同じため、同じ読み方でよい。
     */"""),
    ("readSlot condition 769", """\
        if (serverProtocol == RegistryRemapper.PROTOCOL_766
                || serverProtocol == RegistryRemapper.PROTOCOL_767
                || serverProtocol == RegistryRemapper.PROTOCOL_768) {""",
     """\
        if (serverProtocol == RegistryRemapper.PROTOCOL_766
                || serverProtocol == RegistryRemapper.PROTOCOL_767
                || serverProtocol == RegistryRemapper.PROTOCOL_768
                || serverProtocol == RegistryRemapper.PROTOCOL_769) {"""),
    ("parseUpdateAttributes javadoc 769", """\
    /**
     * Update Attributes を読む。763 は属性キーが番号（0＝最大体力、
     * 2＝ノックバック耐性、3＝移動速度。minecraft-data 1.20 の
     * attributes.json の並びによる）、764／765 は識別子文字列、
     * 766／767／768 は番号（属性名の表で識別子に直す）。
     * 767／768 は modifier の UUID が文字列（766 までは 16 バイトの UUID）。
     * 768 は一部の属性名から接頭辞が外れた（generic.burning_time →
     * burning_time 等。protocol.json の Update Attributes キー表による）。
     */""",
     """\
    /**
     * Update Attributes を読む。763 は属性キーが番号（0＝最大体力、
     * 2＝ノックバック耐性、3＝移動速度。minecraft-data 1.20 の
     * attributes.json の並びによる）、764／765 は識別子文字列、
     * 766／767／768／769 は番号（属性名の表で識別子に直す）。
     * 767／768／769 は modifier の UUID が文字列（766 までは 16 バイトの UUID）。
     * 768 は一部の属性名から接頭辞が外れた（generic.burning_time →
     * burning_time 等。protocol.json の Update Attributes キー表による）。
     * 769 の属性名は 768 と同一（attributeName769 は 768 と同じ内容）。
     */"""),
    ("parseUpdateAttributes numeric keys 769", """\
            } else if (serverProtocol == RegistryRemapper.PROTOCOL_766
                    || serverProtocol == RegistryRemapper.PROTOCOL_767
                    || serverProtocol == RegistryRemapper.PROTOCOL_768) {
                // 766／767／768 は属性キーが番号。名前表で識別子に直す。
                // 表は "generic.movement_speed" のような平文の名前なので、
                // 下の比較（"minecraft:generic.movement_speed"）に合わせるため
                // "minecraft:" を付ける。
                int keyId = reader.readVarInt();
                String name = serverProtocol == RegistryRemapper.PROTOCOL_768
                        ? RegistryRemapper.attributeName768(keyId)
                        : serverProtocol == RegistryRemapper.PROTOCOL_767
                                ? RegistryRemapper.attributeName767(keyId)
                                : RegistryRemapper.attributeName766(keyId);""",
     """\
            } else if (serverProtocol == RegistryRemapper.PROTOCOL_766
                    || serverProtocol == RegistryRemapper.PROTOCOL_767
                    || serverProtocol == RegistryRemapper.PROTOCOL_768
                    || serverProtocol == RegistryRemapper.PROTOCOL_769) {
                // 766／767／768／769 は属性キーが番号。名前表で識別子に直す。
                // 表は "generic.movement_speed" のような平文の名前なので、
                // 下の比較（"minecraft:generic.movement_speed"）に合わせるため
                // "minecraft:" を付ける。
                int keyId = reader.readVarInt();
                String name = serverProtocol == RegistryRemapper.PROTOCOL_769
                        ? RegistryRemapper.attributeName769(keyId)
                        : serverProtocol == RegistryRemapper.PROTOCOL_768
                                ? RegistryRemapper.attributeName768(keyId)
                                : serverProtocol == RegistryRemapper.PROTOCOL_767
                                        ? RegistryRemapper.attributeName767(keyId)
                                        : RegistryRemapper.attributeName766(keyId);"""),
    ("parseUpdateAttributes modifier UUID 769", """\
                if (serverProtocol == RegistryRemapper.PROTOCOL_767
                        || serverProtocol == RegistryRemapper.PROTOCOL_768) {
                    reader.readString(); // modifier UUID（767／768 は文字列）
                } else {""",
     """\
                if (serverProtocol == RegistryRemapper.PROTOCOL_767
                        || serverProtocol == RegistryRemapper.PROTOCOL_768
                        || serverProtocol == RegistryRemapper.PROTOCOL_769) {
                    reader.readString(); // modifier UUID（767／768／769 は文字列）
                } else {"""),
])

CHUNK = BEDROCK / "BedrockChunkTranslator.java"
edit(CHUNK, [
    ("normalizeBlockState javadoc 769", """\
    /**
     * Java のブロック状態IDを 765 に正規化する（Bedrock 変換の共通の入口）。
     * 765 はそのまま、764／766 は RegistryRemapper の上向き表、767 は
     * 767→765 表、768 は 768→765 表、763 は 765→763 表の逆引き。
     * 範囲外は石（765 の 1）。
     */""",
     """\
    /**
     * Java のブロック状態IDを 765 に正規化する（Bedrock 変換の共通の入口）。
     * 765 はそのまま、764／766 は RegistryRemapper の上向き表、767 は
     * 767→765 表、768 は 768→765 表、769 は 769→765 表、763 は 765→763 表の逆引き。
     * 範囲外は石（765 の 1）。
     */"""),
    ("normalizeBlockState 769", """\
            case RegistryRemapper.PROTOCOL_768 -> RegistryRemapper.mapBlockState768To765(stateId);
            default -> stateId;""",
     """\
            case RegistryRemapper.PROTOCOL_768 -> RegistryRemapper.mapBlockState768To765(stateId);
            case RegistryRemapper.PROTOCOL_769 -> RegistryRemapper.mapBlockState769To765(stateId);
            default -> stateId;"""),
])

print("Bedrock→769サーバーの対応を入れた")
