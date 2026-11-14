#!/usr/bin/env python3
"""Java 1.21.5（プロトコル770）の Bedrock→Java バックエンド対応を入れる。

対象: detergentmc-bedrock の JavaBackendConnection（770 サーバーとの
入出力）と BedrockEntityTranslator／BedrockChunkTranslator（770 の
ID を 765 に正規化）。769の流儀をなぞる。

770 で新たに必要なもの（769 には無かった）：
- Chunk Data の heightmap が NBT から配列に変わったため、
  BedrockChunkTranslator.translate() が 770 では配列として読み飛ばす。
- Entity Metadata の型番号がずれたため、BedrockEntityTranslator
  .readMetadataValue() が 770 では 23〜34 を 770 の番号で読む。
  （769 の番号のまま読むと cow_variant の 0 で NBT を誤読する等）。
- 770 にだけある粒子（tinted_leaves 35、trail 48、dust_pillar 108、
  block_crumble 112）は 765 に相当が無いため、skipParticle() が
  770 の ID で直接データを読み飛ばす。

それ以外は 769 と同形（Join Game、Player Info、Synchronize Player
Position、Update Attributes、Client Information は protocol.json で
769 と同一を確認済み）で、既存の 769 分岐に 770 を足すだけで済む。
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
    /** 次元種別の番号→名前（766／767／768／769 の Join Game／Respawn の番号解決用）。 */""",
     """\
    /** 次元種別の番号→名前（766／767／768／769／770 の Join Game／Respawn の番号解決用）。 */"""),
    ("Add Resource Pack comment", """\
                // 追加パック（765: 0x07、766／767／768／769: 0x09）。UUID を読んで accepted（3）で返す。""",
     """\
                // 追加パック（765: 0x07、766／767／768／769／770: 0x09）。UUID を読んで accepted（3）で返す。"""),
    ("Sync Player Position 770", """\
            if (serverProtocol == 768 || serverProtocol == 769) {
                // 768／769：teleportId が先頭、dx／dy／dz が追加、flags は u32。
                // 769 の Synchronize Player Position は 768 と同形。""",
     """\
            if (serverProtocol == 768 || serverProtocol == 769 || serverProtocol == 770) {
                // 768／769／770：teleportId が先頭、dx／dy／dz が追加、flags は u32。
                // 769／770 の Synchronize Player Position は 768 と同形。"""),
    ("Respawn dimension 770", """\
                if (serverProtocol == 766 || serverProtocol == 767
                        || serverProtocol == 768 || serverProtocol == 769) {""",
     """\
                if (serverProtocol == 766 || serverProtocol == 767
                        || serverProtocol == 768 || serverProtocol == 769
                        || serverProtocol == 770) {"""),
    ("Join Game 770", """\
        if (serverProtocol == 766 || serverProtocol == 767 || serverProtocol == 768
                || serverProtocol == 769) {
            // 768／769 の Join Game は767形式に seaLevel を補ったもののため、
            // 767へ直してから読む（parseJoinGame766 は余分なバイトを拒む）。
            // 769 の Join Game は 768 と同形のため同じ変換が使える。
            byte[] payload767 = serverProtocol == 768 || serverProtocol == 769
                    ? PlayTranslator.translateJoinGame768To767(payload) : payload;""",
     """\
        if (serverProtocol == 766 || serverProtocol == 767 || serverProtocol == 768
                || serverProtocol == 769 || serverProtocol == 770) {
            // 768／769／770 の Join Game は767形式に seaLevel を補ったもののため、
            // 767へ直してから読む（parseJoinGame766 は余分なバイトを拒む）。
            // 769／770 の Join Game は 768 と同形のため同じ変換が使える。
            byte[] payload767 = serverProtocol == 768 || serverProtocol == 769
                    || serverProtocol == 770
                    ? PlayTranslator.translateJoinGame768To767(payload) : payload;"""),
    ("Client Information particleStatus 770", """\
        if (serverProtocol == 768 || serverProtocol == 769) {
            // 768／769 は末尾に particleStatus varint がある（0=all）。
            // 付けないと 1.21.2／1.21.3／1.21.4 のバニラが読み取り途中で切断する。
            writer.writeVarInt(0);
        }""",
     """\
        if (serverProtocol == 768 || serverProtocol == 769 || serverProtocol == 770) {
            // 768／769／770 は末尾に particleStatus varint がある（0=all）。
            // 付けないと 1.21.2／1.21.3／1.21.4／1.21.5 のバニラが読み取り途中で切断する。
            writer.writeVarInt(0);
        }"""),
])

ENTITY = BEDROCK / "BedrockEntityTranslator.java"
edit(ENTITY, [
    ("normalizeEntityType 770", """\
            case RegistryRemapper.PROTOCOL_769 ->
                    RegistryRemapper.mapEntityType769To765(typeId);
            default -> (typeId < 0 || typeId > RegistryRemapper.MAX_ENTITY_TYPE_765)""",
     """\
            case RegistryRemapper.PROTOCOL_769 ->
                    RegistryRemapper.mapEntityType769To765(typeId);
            case RegistryRemapper.PROTOCOL_770 ->
                    RegistryRemapper.mapEntityType770To765(typeId);
            default -> (typeId < 0 || typeId > RegistryRemapper.MAX_ENTITY_TYPE_765)"""),
    ("normalizeItem 770", """\
            case RegistryRemapper.PROTOCOL_769 -> RegistryRemapper.mapItem769To765(itemId);
            default -> (itemId < 0 || itemId > RegistryRemapper.MAX_ITEM_765)""",
     """\
            case RegistryRemapper.PROTOCOL_769 -> RegistryRemapper.mapItem769To765(itemId);
            case RegistryRemapper.PROTOCOL_770 -> RegistryRemapper.mapItem770To765(itemId);
            default -> (itemId < 0 || itemId > RegistryRemapper.MAX_ITEM_765)"""),
    ("normalizeEffectTo765 770", """\
            // エフェクトは 767⇔768⇔769 で同一のため 768／769 も同じ表でよい。
            case RegistryRemapper.PROTOCOL_767, RegistryRemapper.PROTOCOL_768,
                    RegistryRemapper.PROTOCOL_769 ->
                    RegistryRemapper.mapEffect767To765(effectId);""",
     """\
            // エフェクトは 767⇔768⇔769⇔770 で同一のため 768／769／770 も同じ表でよい。
            case RegistryRemapper.PROTOCOL_767, RegistryRemapper.PROTOCOL_768,
                    RegistryRemapper.PROTOCOL_769, RegistryRemapper.PROTOCOL_770 ->
                    RegistryRemapper.mapEffect767To765(effectId);"""),
    ("normalizeParticleTo765 770", """\
            case RegistryRemapper.PROTOCOL_769 ->
                    RegistryRemapper.mapParticle769To765(particleId);
            default -> (particleId < 0 || particleId > RegistryRemapper.MAX_PARTICLE_765)""",
     """\
            case RegistryRemapper.PROTOCOL_769 ->
                    RegistryRemapper.mapParticle769To765(particleId);
            case RegistryRemapper.PROTOCOL_770 ->
                    RegistryRemapper.mapParticle770To765(particleId);
            default -> (particleId < 0 || particleId > RegistryRemapper.MAX_PARTICLE_765)"""),
    ("readSlot javadoc 770", """\
    /**
     * Java のスロット 1 個を読む。765 までは NBT（763 は名前付き、764／765 は
     * 名前なし）を読み飛ばす。766／767／768／769 はデータコンポーネント形式で、
     * 中身は読まずに個数だけ確認する（成分付きだと後ろは読めないため
     * {@code clean=false} を返す）。768／769 のスロットの外形（個数・added・removed
     * の varint 3 個）は 767 と同じため、同じ読み方でよい。
     */""",
     """\
    /**
     * Java のスロット 1 個を読む。765 までは NBT（763 は名前付き、764／765 は
     * 名前なし）を読み飛ばす。766／767／768／769／770 はデータコンポーネント形式で、
     * 中身は読まずに個数だけ確認する（成分付きだと後ろは読めないため
     * {@code clean=false} を返す）。768／769／770 のスロットの外形（個数・added・removed
     * の varint 3 個）は 767 と同じため、同じ読み方でよい。
     */"""),
    ("readSlot condition 770", """\
        if (serverProtocol == RegistryRemapper.PROTOCOL_766
                || serverProtocol == RegistryRemapper.PROTOCOL_767
                || serverProtocol == RegistryRemapper.PROTOCOL_768
                || serverProtocol == RegistryRemapper.PROTOCOL_769) {""",
     """\
        if (serverProtocol == RegistryRemapper.PROTOCOL_766
                || serverProtocol == RegistryRemapper.PROTOCOL_767
                || serverProtocol == RegistryRemapper.PROTOCOL_768
                || serverProtocol == RegistryRemapper.PROTOCOL_769
                || serverProtocol == RegistryRemapper.PROTOCOL_770) {"""),
    ("parseUpdateAttributes javadoc 770", """\
     * 766／767／768／769 は番号（属性名の表で識別子に直す）。
     * 767／768／769 は modifier の UUID が文字列（766 までは 16 バイトの UUID）。
     * 768 は一部の属性名から接頭辞が外れた（generic.burning_time →
     * burning_time 等。protocol.json の Update Attributes キー表による）。
     * 769 の属性名は 768 と同一（attributeName769 は 768 と同じ内容）。
     */""",
     """\
     * 766／767／768／769／770 は番号（属性名の表で識別子に直す）。
     * 767／768／769／770 は modifier の UUID が文字列（766 までは 16 バイトの UUID）。
     * 768 は一部の属性名から接頭辞が外れた（generic.burning_time →
     * burning_time 等。protocol.json の Update Attributes キー表による）。
     * 769／770 の属性名は 768 と同一（attributeName769／attributeName770 は
     * 768 と同じ内容）。
     */"""),
    ("parseUpdateAttributes numeric keys 770", """\
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
                                        : RegistryRemapper.attributeName766(keyId);""",
     """\
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
                        : serverProtocol == RegistryRemapper.PROTOCOL_769
                                ? RegistryRemapper.attributeName769(keyId)
                                : serverProtocol == RegistryRemapper.PROTOCOL_768
                                        ? RegistryRemapper.attributeName768(keyId)
                                        : serverProtocol == RegistryRemapper.PROTOCOL_767
                                                ? RegistryRemapper.attributeName767(keyId)
                                                : RegistryRemapper.attributeName766(keyId);"""),
    ("parseUpdateAttributes modifier UUID 770", """\
                if (serverProtocol == RegistryRemapper.PROTOCOL_767
                        || serverProtocol == RegistryRemapper.PROTOCOL_768
                        || serverProtocol == RegistryRemapper.PROTOCOL_769) {
                    reader.readString(); // modifier UUID（767／768／769 は文字列）""",
     """\
                if (serverProtocol == RegistryRemapper.PROTOCOL_767
                        || serverProtocol == RegistryRemapper.PROTOCOL_768
                        || serverProtocol == RegistryRemapper.PROTOCOL_769
                        || serverProtocol == RegistryRemapper.PROTOCOL_770) {
                    reader.readString(); // modifier UUID（767／768／769／770 は文字列）"""),
    ("readMetadataValue 770 pre-branch", """\
    /** メタデータの値 1 個を読む（型番号は wiki.vg の Entity Metadata による）。 */
    private static Object readMetadataValue(PacketIO.Reader reader, int type, int protocol) {
        return switch (type) {""",
     """\
    /** メタデータの値 1 個を読む（型番号は wiki.vg の Entity Metadata による）。 */
    private static Object readMetadataValue(PacketIO.Reader reader, int type, int protocol) {
        if (protocol == RegistryRemapper.PROTOCOL_770 && type >= 23 && type <= 34) {
            // 770 は型番号がずれている（769 の 23 は wolf_variant（Holder）だが
            // 770 の 23 は cow_variant（varint）。769 の番号のまま読むと、
            // 値 0 のときに存在しない NBT を読んで後続が壊れる。
            // 770：23 cow_variant、24 wolf_variant、25 wolf_sound_variant、
            // 26 frog_variant、27 pig_variant、28 chicken_variant（いずれも varint）、
            // 29 optional_global_pos、30 painting_variant（Holder）、
            // 31 sniffer_state、32 armadillo_state（varint）、33 vector3、34 quaternion。
            return switch (type) {
                case 23, 24, 25, 26, 27, 28, 31, 32 -> reader.readVarInt();
                case 29 -> {
                    boolean present = reader.readBoolean();
                    if (!present) {
                        yield null;
                    }
                    reader.readString();
                    yield reader.readPositionRaw();
                }
                case 30 -> {
                    int holder = reader.readVarInt();
                    Nbt.Tag inline = null;
                    if (holder == 0) {
                        inline = reader.readAnonymousNbt().tag();
                    }
                    yield new Holder(holder, inline);
                }
                case 33 -> new float[]{reader.readFloat(), reader.readFloat(), reader.readFloat()};
                case 34 -> new float[]{reader.readFloat(), reader.readFloat(),
                        reader.readFloat(), reader.readFloat()};
                default -> throw new IllegalArgumentException("未知のメタデータ型です: " + type);
            };
        }
        return switch (type) {"""),
    ("skipParticle 770 pre-branch", """\
    private static void skipParticle(PacketIO.Reader reader, int protocol) {
        int particle765 = normalizeParticleTo765(reader.readVarInt(), protocol);""",
     """\
    private static void skipParticle(PacketIO.Reader reader, int protocol) {
        int rawId = reader.readVarInt();
        if (protocol == RegistryRemapper.PROTOCOL_770) {
            // 770 にだけある粒子は 765 に相当が無いため、正規化ではデータが
            // 読めない。770 の ID で直接読み飛ばす。
            // tinted_leaves（35）：色の i32。trail（48）：target vec3f64＋
            // color i32＋duration varint。dust_pillar（108）と
            // block_crumble（112）：ブロック状態IDの varint。
            // firefly（113）と pale_oak_leaves（34）はデータ無しのため
            // 下の正規化（既定：読まない）でよい。
            switch (rawId) {
                case 35 -> {
                    reader.readInt();
                    return;
                }
                case 48 -> {
                    reader.readDouble();
                    reader.readDouble();
                    reader.readDouble();
                    reader.readInt();
                    reader.readVarInt();
                    return;
                }
                case 108, 112 -> {
                    reader.readVarInt();
                    return;
                }
                default -> {
                    // 765 にある粒子は下の正規化で読む。
                }
            }
        }
        int particle765 = normalizeParticleTo765(rawId, protocol);"""),
])

CHUNK = BEDROCK / "BedrockChunkTranslator.java"
edit(CHUNK, [
    ("normalizeBlockState javadoc 770", """\
    /**
     * Java のブロック状態IDを 765 に正規化する（Bedrock 変換の共通の入口）。
     * 765 はそのまま、764／766 は RegistryRemapper の上向き表、767 は
     * 767→765 表、768 は 768→765 表、769 は 769→765 表、763 は 765→763 表の逆引き。
     * 範囲外は石（765 の 1）。
     */""",
     """\
    /**
     * Java のブロック状態IDを 765 に正規化する（Bedrock 変換の共通の入口）。
     * 765 はそのまま、764／766 は RegistryRemapper の上向き表、767 は
     * 767→765 表、768 は 768→765 表、769 は 769→765 表、770 は 770→765 表、
     * 763 は 765→763 表の逆引き。
     * 範囲外は石（765 の 1）。
     */"""),
    ("normalizeBlockState 770", """\
            case RegistryRemapper.PROTOCOL_769 -> RegistryRemapper.mapBlockState769To765(stateId);
            default -> stateId;""",
     """\
            case RegistryRemapper.PROTOCOL_769 -> RegistryRemapper.mapBlockState769To765(stateId);
            case RegistryRemapper.PROTOCOL_770 -> RegistryRemapper.mapBlockState770To765(stateId);
            default -> stateId;"""),
    ("translate heightmap 770", """\
        if (input.serverProtocol() == RegistryRemapper.PROTOCOL_763) {
            reader.readNamedNbt();
        } else {
            reader.readAnonymousNbt();
        }""",
     """\
        if (input.serverProtocol() == RegistryRemapper.PROTOCOL_763) {
            reader.readNamedNbt();
        } else if (input.serverProtocol() == RegistryRemapper.PROTOCOL_770) {
            // 770 の heightmap は NBT ではなく配列（varint 個数 ×
            // {type varint, data long[] }）。NBT として読むと壊れるため
            // 配列として読み飛ばす（高さの情報自体は Bedrock 変換では使わない）。
            int heightmapCount = reader.readVarInt();
            if (heightmapCount < 0 || heightmapCount > 16) {
                throw new IllegalArgumentException("Java チャンクの heightmap 数が不正です: "
                        + heightmapCount);
            }
            for (int i = 0; i < heightmapCount; i++) {
                reader.readVarInt(); // type
                int dataLength = reader.readVarInt();
                if (dataLength < 0 || dataLength > reader.remaining() / 8) {
                    throw new IllegalArgumentException("Java チャンクの heightmap 長が不正です: "
                            + dataLength);
                }
                reader.readBytes(dataLength * 8);
            }
        } else {
            reader.readAnonymousNbt();
        }"""),
])

print("Bedrock→770サーバーの対応を入れた")
