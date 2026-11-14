#!/usr/bin/env python3
"""Java 1.21.6（プロトコル771）の Bedrock→Java バックエンド対応を入れる。

対象: detergentmc-bedrock の JavaBackendConnection（771 サーバーとの
入出力）と BedrockEntityTranslator／BedrockChunkTranslator（771 の
ID を 765 に正規化）。770の流儀をなぞる。

771 で新たに必要なもの（770 には無かった）は無い。770と違うのは：
- Join Game／Player Info／Synchronize Player Position／Update Attributes／
  Client Information／Chat Message／チャンクの heightmap／パレット付き
  コンテナ／Entity Metadata の型番号／粒子IDは770と同一のため、既存の
  770分岐に771を足すだけで済む（実バニラ1.21.6のプローブで確認済み：
  heightmapは770と同じ直接配列、checksum 0x00は受け入れられた）。
- 属性だけは770と違う（camera_distanceが8番に挿入、
  waypoint_transmit_range／waypoint_receive_rangeが追加）。
  attributeName771は771専用の表を使う。
- エンティティはhappy_ghast（771: 56）が増えたが、Bedrockへの正規化は
  mapEntityType771To765（ステージ1で追加済み）で行う。
  771→770でガストに代替するのはJava側プロキシの話で、Bedrock側では
  765のガスト（55）に正規化される。
- ハーネス・乾燥ガストはアイテム・ブロック状態の付け替えで通る
  （mapItem771To765／mapBlockState771To765）。

それ以外は770と同形で、既存の770分岐に771を足すだけで済む。
実機 Bedrock クライアントでの確認は未実施（既存の全 Bedrock 組み合わせと同様）。
各アンカーはファイル中にちょうど1回だけ現れること。
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
    /** 次元種別の番号→名前（766／767／768／769／770 の Join Game／Respawn の番号解決用）。 */""",
     """\
    /** 次元種別の番号→名前（766／767／768／769／770／771 の Join Game／Respawn の番号解決用）。 */"""),
    ("sendChat checksum 771", """\
            if (serverProtocol == 770) {
                // 770の Chat Message は末尾に checksum u8 が増えた。
                // プロキシは770クライアントからの Chat Message の末尾1バイトを
                // checksum として落とすため、ここで付けないと acknowledged の
                // 末尾バイトが削られて壊れる。値は 0x00（実バニラ1.21.5に
                // 受け入れられることは統合テストで確認済み）。""",
     """\
            if (serverProtocol == 770 || serverProtocol == 771) {
                // 770／771の Chat Message は末尾に checksum u8 が増えた。
                // プロキシは770／771クライアントからの Chat Message の末尾1バイトを
                // checksum として落とすため、ここで付けないと acknowledged の
                // 末尾バイトが削られて壊れる。値は 0x00（実バニラ1.21.5／1.21.6に
                // 受け入れられることは統合テスト／実バニラ1.21.6のプローブで確認済み）。"""),
    ("Add Resource Pack comment", """\
                // 追加パック（765: 0x07、766／767／768／769／770: 0x09）。UUID を読んで accepted（3）で返す。""",
     """\
                // 追加パック（765: 0x07、766／767／768／769／770／771: 0x09）。UUID を読んで accepted（3）で返す。"""),
    ("Sync Player Position 771", """\
            if (serverProtocol == 768 || serverProtocol == 769 || serverProtocol == 770) {
                // 768／769／770：teleportId が先頭、dx／dy／dz が追加、flags は u32。
                // 769／770 の Synchronize Player Position は 768 と同形。""",
     """\
            if (serverProtocol == 768 || serverProtocol == 769 || serverProtocol == 770
                    || serverProtocol == 771) {
                // 768／769／770／771：teleportId が先頭、dx／dy／dz が追加、flags は u32。
                // 769／770／771 の Synchronize Player Position は 768 と同形。"""),
    ("Respawn dimension 771", """\
                if (serverProtocol == 766 || serverProtocol == 767
                        || serverProtocol == 768 || serverProtocol == 769
                        || serverProtocol == 770) {""",
     """\
                if (serverProtocol == 766 || serverProtocol == 767
                        || serverProtocol == 768 || serverProtocol == 769
                        || serverProtocol == 770 || serverProtocol == 771) {"""),
    ("Join Game 771", """\
        if (serverProtocol == 766 || serverProtocol == 767 || serverProtocol == 768
                || serverProtocol == 769 || serverProtocol == 770) {
            // 768／769／770 の Join Game は767形式に seaLevel を補ったもののため、
            // 767へ直してから読む（parseJoinGame766 は余分なバイトを拒む）。
            // 769／770 の Join Game は 768 と同形のため同じ変換が使える。
            byte[] payload767 = serverProtocol == 768 || serverProtocol == 769
                    || serverProtocol == 770
                    ? PlayTranslator.translateJoinGame768To767(payload) : payload;""",
     """\
        if (serverProtocol == 766 || serverProtocol == 767 || serverProtocol == 768
                || serverProtocol == 769 || serverProtocol == 770
                || serverProtocol == 771) {
            // 768／769／770／771 の Join Game は767形式に seaLevel を補ったもののため、
            // 767へ直してから読む（parseJoinGame766 は余分なバイトを拒む）。
            // 769／770／771 の Join Game は 768 と同形のため同じ変換が使える。
            byte[] payload767 = serverProtocol == 768 || serverProtocol == 769
                    || serverProtocol == 770 || serverProtocol == 771
                    ? PlayTranslator.translateJoinGame768To767(payload) : payload;"""),
    ("Client Information particleStatus 771", """\
        if (serverProtocol == 768 || serverProtocol == 769 || serverProtocol == 770) {
            // 768／769／770 は末尾に particleStatus varint がある（0=all）。
            // 付けないと 1.21.2／1.21.3／1.21.4／1.21.5 のバニラが読み取り途中で切断する。
            writer.writeVarInt(0);
        }""",
     """\
        if (serverProtocol == 768 || serverProtocol == 769 || serverProtocol == 770
                || serverProtocol == 771) {
            // 768／769／770／771 は末尾に particleStatus varint がある（0=all）。
            // 付けないと 1.21.2／1.21.3／1.21.4／1.21.5／1.21.6 のバニラが読み取り途中で切断する。
            writer.writeVarInt(0);
        }"""),
])

ENTITY = BEDROCK / "BedrockEntityTranslator.java"
edit(ENTITY, [
    ("normalizeEntityType 771", """\
            case RegistryRemapper.PROTOCOL_769 ->
                    RegistryRemapper.mapEntityType769To765(typeId);
            case RegistryRemapper.PROTOCOL_770 ->
                    RegistryRemapper.mapEntityType770To765(typeId);
            default -> (typeId < 0 || typeId > RegistryRemapper.MAX_ENTITY_TYPE_765)""",
     """\
            case RegistryRemapper.PROTOCOL_769 ->
                    RegistryRemapper.mapEntityType769To765(typeId);
            case RegistryRemapper.PROTOCOL_770 ->
                    RegistryRemapper.mapEntityType770To765(typeId);
            case RegistryRemapper.PROTOCOL_771 ->
                    RegistryRemapper.mapEntityType771To765(typeId);
            default -> (typeId < 0 || typeId > RegistryRemapper.MAX_ENTITY_TYPE_765)"""),
    ("normalizeItem 771", """\
            case RegistryRemapper.PROTOCOL_769 -> RegistryRemapper.mapItem769To765(itemId);
            case RegistryRemapper.PROTOCOL_770 -> RegistryRemapper.mapItem770To765(itemId);
            default -> (itemId < 0 || itemId > RegistryRemapper.MAX_ITEM_765)""",
     """\
            case RegistryRemapper.PROTOCOL_769 -> RegistryRemapper.mapItem769To765(itemId);
            case RegistryRemapper.PROTOCOL_770 -> RegistryRemapper.mapItem770To765(itemId);
            case RegistryRemapper.PROTOCOL_771 -> RegistryRemapper.mapItem771To765(itemId);
            default -> (itemId < 0 || itemId > RegistryRemapper.MAX_ITEM_765)"""),
    ("normalizeEffectTo765 771", """\
            // エフェクトは 767⇔768⇔769⇔770 で同一のため 768／769／770 も同じ表でよい。
            case RegistryRemapper.PROTOCOL_767, RegistryRemapper.PROTOCOL_768,
                    RegistryRemapper.PROTOCOL_769, RegistryRemapper.PROTOCOL_770 ->
                    RegistryRemapper.mapEffect767To765(effectId);""",
     """\
            // エフェクトは 767⇔768⇔769⇔770⇔771 で同一のため 768／769／770／771 も同じ表でよい。
            case RegistryRemapper.PROTOCOL_767, RegistryRemapper.PROTOCOL_768,
                    RegistryRemapper.PROTOCOL_769, RegistryRemapper.PROTOCOL_770,
                    RegistryRemapper.PROTOCOL_771 ->
                    RegistryRemapper.mapEffect767To765(effectId);"""),
    ("normalizeParticleTo765 771", """\
            case RegistryRemapper.PROTOCOL_769 ->
                    RegistryRemapper.mapParticle769To765(particleId);
            case RegistryRemapper.PROTOCOL_770 ->
                    RegistryRemapper.mapParticle770To765(particleId);
            default -> (particleId < 0 || particleId > RegistryRemapper.MAX_PARTICLE_765)""",
     """\
            case RegistryRemapper.PROTOCOL_769 ->
                    RegistryRemapper.mapParticle769To765(particleId);
            case RegistryRemapper.PROTOCOL_770 ->
                    RegistryRemapper.mapParticle770To765(particleId);
            case RegistryRemapper.PROTOCOL_771 ->
                    RegistryRemapper.mapParticle771To765(particleId);
            default -> (particleId < 0 || particleId > RegistryRemapper.MAX_PARTICLE_765)"""),
    ("readSlot javadoc 771", """\
    /**
     * Java のスロット 1 個を読む。765 までは NBT（763 は名前付き、764／765 は
     * 名前なし）を読み飛ばす。766／767／768／769／770 はデータコンポーネント形式で、
     * 中身は読まずに個数だけ確認する（成分付きだと後ろは読めないため
     * {@code clean=false} を返す）。768／769／770 のスロットの外形（個数・added・removed
     * の varint 3 個）は 767 と同じため、同じ読み方でよい。
     */""",
     """\
    /**
     * Java のスロット 1 個を読む。765 までは NBT（763 は名前付き、764／765 は
     * 名前なし）を読み飛ばす。766／767／768／769／770／771 はデータコンポーネント形式で、
     * 中身は読まずに個数だけ確認する（成分付きだと後ろは読めないため
     * {@code clean=false} を返す）。768／769／770／771 のスロットの外形（個数・added・removed
     * の varint 3 個）は 767 と同じため、同じ読み方でよい。
     */"""),
    ("readSlot condition 771", """\
        if (serverProtocol == RegistryRemapper.PROTOCOL_766
                || serverProtocol == RegistryRemapper.PROTOCOL_767
                || serverProtocol == RegistryRemapper.PROTOCOL_768
                || serverProtocol == RegistryRemapper.PROTOCOL_769
                || serverProtocol == RegistryRemapper.PROTOCOL_770) {""",
     """\
        if (serverProtocol == RegistryRemapper.PROTOCOL_766
                || serverProtocol == RegistryRemapper.PROTOCOL_767
                || serverProtocol == RegistryRemapper.PROTOCOL_768
                || serverProtocol == RegistryRemapper.PROTOCOL_769
                || serverProtocol == RegistryRemapper.PROTOCOL_770
                || serverProtocol == RegistryRemapper.PROTOCOL_771) {"""),
    ("parseUpdateAttributes javadoc 771", """\
     * 766／767／768／769／770 は番号（属性名の表で識別子に直す）。
     * 767／768／769／770 は modifier の UUID が文字列（766 までは 16 バイトの UUID）。
     * 768 は一部の属性名から接頭辞が外れた（generic.burning_time →
     * burning_time 等。protocol.json の Update Attributes キー表による）。
     * 769／770 の属性名は 768 と同一（attributeName769／attributeName770 は
     * 768 と同じ内容）。
     */""",
     """\
     * 766／767／768／769／770／771 は番号（属性名の表で識別子に直す）。
     * 767／768／769／770／771 は modifier の UUID が文字列（766 までは 16 バイトの UUID）。
     * 768 は一部の属性名から接頭辞が外れた（generic.burning_time →
     * burning_time 等。protocol.json の Update Attributes キー表による）。
     * 769／770 の属性名は 768 と同一（attributeName769／attributeName770 は
     * 768 と同じ内容）。771 の属性名は 770 とは違う（camera_distance が
     * 8番に挿入、waypoint_transmit_range／waypoint_receive_range が追加。
     * attributeName771 は771専用の表）。
     */"""),
    ("parseUpdateAttributes numeric keys 771", """\
            } else if (serverProtocol == RegistryRemapper.PROTOCOL_766
                    || serverProtocol == RegistryRemapper.PROTOCOL_767
                    || serverProtocol == RegistryRemapper.PROTOCOL_768
                    || serverProtocol == RegistryRemapper.PROTOCOL_769
                    || serverProtocol == RegistryRemapper.PROTOCOL_770) {
                // 766／767／768／769／770 は属性キーが番号。名前表で識別子に直す。
                // 表は "generic.movement_speed" のような平文の名前なので、
                // 下の比較（"minecraft:generic.movement_speed"）に合わせるため
                // "minecraft:" を付ける。
                int keyId = reader.readVarInt();
                String name = serverProtocol == RegistryRemapper.PROTOCOL_770
                        ? RegistryRemapper.attributeName770(keyId)
                        : serverProtocol == RegistryRemapper.PROTOCOL_769""",
     """\
            } else if (serverProtocol == RegistryRemapper.PROTOCOL_766
                    || serverProtocol == RegistryRemapper.PROTOCOL_767
                    || serverProtocol == RegistryRemapper.PROTOCOL_768
                    || serverProtocol == RegistryRemapper.PROTOCOL_769
                    || serverProtocol == RegistryRemapper.PROTOCOL_770
                    || serverProtocol == RegistryRemapper.PROTOCOL_771) {
                // 766／767／768／769／770／771 は属性キーが番号。名前表で識別子に直す。
                // 表は "generic.movement_speed" のような平文の名前なので、
                // 下の比較（"minecraft:generic.movement_speed"）に合わせるため
                // "minecraft:" を付ける。
                int keyId = reader.readVarInt();
                String name = serverProtocol == RegistryRemapper.PROTOCOL_771
                        ? RegistryRemapper.attributeName771(keyId)
                        : serverProtocol == RegistryRemapper.PROTOCOL_770
                                ? RegistryRemapper.attributeName770(keyId)
                                : serverProtocol == RegistryRemapper.PROTOCOL_769"""),
    ("parseUpdateAttributes modifier UUID 771", """\
                if (serverProtocol == RegistryRemapper.PROTOCOL_767
                        || serverProtocol == RegistryRemapper.PROTOCOL_768
                        || serverProtocol == RegistryRemapper.PROTOCOL_769
                        || serverProtocol == RegistryRemapper.PROTOCOL_770) {
                    reader.readString(); // modifier UUID（767／768／769／770 は文字列）""",
     """\
                if (serverProtocol == RegistryRemapper.PROTOCOL_767
                        || serverProtocol == RegistryRemapper.PROTOCOL_768
                        || serverProtocol == RegistryRemapper.PROTOCOL_769
                        || serverProtocol == RegistryRemapper.PROTOCOL_770
                        || serverProtocol == RegistryRemapper.PROTOCOL_771) {
                    reader.readString(); // modifier UUID（767／768／769／770／771 は文字列）"""),
    ("readMetadataValue 771", """\
        if (protocol == RegistryRemapper.PROTOCOL_770 && type >= 23 && type <= 34) {
            // 770 は型番号がずれている（769 の 23 は wolf_variant（Holder）だが
            // 770 の 23 は cow_variant（varint）。769 の番号のまま読むと、
            // 値 0 のときに存在しない NBT を読んで後続が壊れる。
            // 770：23 cow_variant、24 wolf_variant、25 wolf_sound_variant、""",
     """\
        if ((protocol == RegistryRemapper.PROTOCOL_770
                || protocol == RegistryRemapper.PROTOCOL_771) && type >= 23 && type <= 34) {
            // 770／771 は型番号がずれている（769 の 23 は wolf_variant（Holder）だが
            // 770／771 の 23 は cow_variant（varint）。769 の番号のまま読むと、
            // 値 0 のときに存在しない NBT を読んで後続が壊れる。
            // 770／771：23 cow_variant、24 wolf_variant、25 wolf_sound_variant、"""),
    ("skipParticle 771", """\
        int rawId = reader.readVarInt();
        if (protocol == RegistryRemapper.PROTOCOL_770) {
            // 770 にだけある粒子は 765 に相当が無いため、正規化ではデータが""",
     """\
        int rawId = reader.readVarInt();
        if (protocol == RegistryRemapper.PROTOCOL_770
                || protocol == RegistryRemapper.PROTOCOL_771) {
            // 770／771 にだけある粒子は 765 に相当が無いため、正規化ではデータが"""),
])

CHUNK = BEDROCK / "BedrockChunkTranslator.java"
edit(CHUNK, [
    ("normalizeBlockState javadoc 771", """\
    /**
     * Java のブロック状態IDを 765 に正規化する（Bedrock 変換の共通の入口）。
     * 765 はそのまま、764／766 は RegistryRemapper の上向き表、767 は
     * 767→765 表、768 は 768→765 表、769 は 769→765 表、770 は 770→765 表、
     * 763 は 765→763 表の逆引き。
     * 範囲外は石（765 の 1）。
     */""",
     """\
    /**
     * Java のブロック状態IDを 765 に正規化する（Bedrock 変換の共通の入口）。
     * 765 はそのまま、764／766 は RegistryRemapper の上向き表、767 は
     * 767→765 表、768 は 768→765 表、769 は 769→765 表、770 は 770→765 表、
     * 771 は 771→765 表、763 は 765→763 表の逆引き。
     * 範囲外は石（765 の 1）。
     */"""),
    ("normalizeBlockState 771", """\
            case RegistryRemapper.PROTOCOL_769 -> RegistryRemapper.mapBlockState769To765(stateId);
            case RegistryRemapper.PROTOCOL_770 -> RegistryRemapper.mapBlockState770To765(stateId);
            default -> stateId;""",
     """\
            case RegistryRemapper.PROTOCOL_769 -> RegistryRemapper.mapBlockState769To765(stateId);
            case RegistryRemapper.PROTOCOL_770 -> RegistryRemapper.mapBlockState770To765(stateId);
            case RegistryRemapper.PROTOCOL_771 -> RegistryRemapper.mapBlockState771To765(stateId);
            default -> stateId;"""),
    ("translate heightmap 771", """\
        if (input.serverProtocol() == RegistryRemapper.PROTOCOL_763) {
            reader.readNamedNbt();
        } else if (input.serverProtocol() == RegistryRemapper.PROTOCOL_770) {
            // 770 の heightmap は NBT ではなく配列（varint 個数 ×
            // {type varint, data long[] }）。NBT として読むと壊れるため
            // 配列として読み飛ばす（高さの情報自体は Bedrock 変換では使わない）。""",
     """\
        if (input.serverProtocol() == RegistryRemapper.PROTOCOL_763) {
            reader.readNamedNbt();
        } else if (input.serverProtocol() == RegistryRemapper.PROTOCOL_770
                || input.serverProtocol() == RegistryRemapper.PROTOCOL_771) {
            // 770／771 の heightmap は NBT ではなく配列（varint 個数 ×
            // {type varint, data long[] }）。NBT として読むと壊れるため
            // 配列として読み飛ばす（高さの情報自体は Bedrock 変換では使わない）。"""),
    ("paletted single-palette 771", """\
            int value = reader.readVarInt();
            if (serverProtocol != RegistryRemapper.PROTOCOL_770) {
                int dataLength = reader.readVarInt();
                if (dataLength != 0) {
                    throw new IllegalArgumentException("シングルパレットの Data Array 長が 0 ではありません。");
                }
            }
            // 770では Data Array の長さ接頭辞が無いため、シングルパレットは
            // 値の直後が次のフィールドになる。""",
     """\
            int value = reader.readVarInt();
            if (serverProtocol != RegistryRemapper.PROTOCOL_770
                    && serverProtocol != RegistryRemapper.PROTOCOL_771) {
                int dataLength = reader.readVarInt();
                if (dataLength != 0) {
                    throw new IllegalArgumentException("シングルパレットの Data Array 長が 0 ではありません。");
                }
            }
            // 770／771では Data Array の長さ接頭辞が無いため、シングルパレットは
            // 値の直後が次のフィールドになる。"""),
    ("paletted data array 771", """\
        int dataLength;
        if (serverProtocol == RegistryRemapper.PROTOCOL_770) {
            // 1.21.5（770）ではパレット付きコンテナの Data Array に長さの
            // varint 接頭辞が無い。長さは bitsPerEntry とエントリ数から計算する。""",
     """\
        int dataLength;
        if (serverProtocol == RegistryRemapper.PROTOCOL_770
                || serverProtocol == RegistryRemapper.PROTOCOL_771) {
            // 1.21.5（770）／1.21.6（771）ではパレット付きコンテナの Data Array に長さの
            // varint 接頭辞が無い。長さは bitsPerEntry とエントリ数から計算する。"""),
])

print("Bedrock→771サーバーの対応を入れた")
