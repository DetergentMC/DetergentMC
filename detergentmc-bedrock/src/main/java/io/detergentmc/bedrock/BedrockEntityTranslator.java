package io.detergentmc.bedrock;

import io.detergentmc.protocol.Nbt;
import io.detergentmc.protocol.PacketIO;
import io.detergentmc.protocol.RegistryRemapper;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * Java エンティティパケット → Bedrock エンティティデータへの翻訳。
 *
 * <p>Java 側の種別・アイテム・エフェクト番号はすべて 765 に正規化してから
 * {@link BedrockEntityTypeIds}／{@link BedrockItemIds} を引く（Java 側の
 * 対応表の二重持ちはしない）。正規化は {@link BedrockChunkTranslator} の
 * ブロック状態と同じ方針：765 はそのまま、764／766 は
 * {@link RegistryRemapper} の上向き表、763 は 765→763 表の逆引き。
 *
 * <p>Java 側の形式は wiki.vg「Entity Metadata」・Minecraft Wiki「Java
 * Edition protocol/Entity metadata」と、DetergentMC のプロキシ
 * （{@code ChunkTranslator}・{@code PlayTranslator} の読み取り実装）で
 * 確認した。Bedrock 側の形式は PrismarineJS minecraft-data の Bedrock
 * 1.20.40 プロトコル記述による。
 *
 * <p>読めないパケットは例外（{@link IllegalArgumentException}）で落とす。
 * メタデータは成分付きスロット（766 のデータコンポーネント）などで
 * 途中で止まることがあり、その場合は読めた分だけを返す
 * （{@link JavaMetadata#complete()} が false）。
 */
public final class BedrockEntityTranslator {

    private BedrockEntityTranslator() {
    }

    /** 765 のブタの種別ID（範囲外の代替）。 */
    public static final int PIG_TYPE_765 = 73;

    /** 765 の石のアイテムID（範囲外の代替）。 */
    public static final int STONE_ITEM_765 = 1;

    // ---- 763→765 の逆引き表（765→763 表の初出を採用） ----

    private static final int[] ENTITY_TYPE_763_TO_765 = buildInverse763Entity();
    private static final int[] ITEM_763_TO_765 = buildInverse763Item();
    private static final int[] PARTICLE_763_TO_765 = buildInverse763Particle();

    private static int[] buildInverse763Entity() {
        int max765 = RegistryRemapper.MAX_ENTITY_TYPE_765;
        int[] inverse = new int[max765 + 1];
        Arrays.fill(inverse, -1);
        for (int id765 = 0; id765 <= max765; id765++) {
            int id763 = RegistryRemapper.mapEntityType(id765);
            if (id763 >= 0 && id763 <= max765 && inverse[id763] < 0) {
                inverse[id763] = id765;
            }
        }
        return inverse;
    }

    private static int[] buildInverse763Item() {
        int max765 = RegistryRemapper.MAX_ITEM_765;
        int[] inverse = new int[max765 + 1];
        Arrays.fill(inverse, -1);
        for (int id765 = 0; id765 <= max765; id765++) {
            int id763 = RegistryRemapper.mapItem(id765);
            if (id763 >= 0 && id763 <= max765 && inverse[id763] < 0) {
                inverse[id763] = id765;
            }
        }
        return inverse;
    }

    private static int[] buildInverse763Particle() {
        int max765 = RegistryRemapper.MAX_PARTICLE_765;
        int[] inverse = new int[max765 + 1];
        Arrays.fill(inverse, -1);
        for (int id765 = 0; id765 <= max765; id765++) {
            int id763 = RegistryRemapper.mapParticle(id765);
            if (id763 >= 0 && id763 <= max765 && inverse[id763] < 0) {
                inverse[id763] = id765;
            }
        }
        return inverse;
    }

    /**
     * サーバーのプロトコル番号のエンティティ種別IDを 765 に正規化する。
     * 範囲外・逆引きに無いものはブタ（73）。
     */
    public static int normalizeEntityType(int typeId, int serverProtocol) {
        return switch (serverProtocol) {
            case RegistryRemapper.PROTOCOL_763 -> {
                if (typeId < 0 || typeId >= ENTITY_TYPE_763_TO_765.length) {
                    yield PIG_TYPE_765;
                }
                int mapped = ENTITY_TYPE_763_TO_765[typeId];
                yield mapped < 0 ? PIG_TYPE_765 : mapped;
            }
            case RegistryRemapper.PROTOCOL_764 ->
                    RegistryRemapper.mapEntityTypeUp764(typeId);
            case RegistryRemapper.PROTOCOL_766 ->
                    RegistryRemapper.mapEntityTypeUp766(typeId);
            case RegistryRemapper.PROTOCOL_767 ->
                    RegistryRemapper.mapEntityType767To765(typeId);
            case RegistryRemapper.PROTOCOL_768 ->
                    RegistryRemapper.mapEntityType768To765(typeId);
            case RegistryRemapper.PROTOCOL_769 ->
                    RegistryRemapper.mapEntityType769To765(typeId);
            case RegistryRemapper.PROTOCOL_770 ->
                    RegistryRemapper.mapEntityType770To765(typeId);
            case RegistryRemapper.PROTOCOL_771 ->
                    RegistryRemapper.mapEntityType771To765(typeId);
            case RegistryRemapper.PROTOCOL_772 ->
                    RegistryRemapper.mapEntityType772To765(typeId);
            case RegistryRemapper.PROTOCOL_773 ->
                    RegistryRemapper.mapEntityType773To765(typeId);
            case RegistryRemapper.PROTOCOL_774 ->
                    RegistryRemapper.mapEntityType774To765(typeId);
            case RegistryRemapper.PROTOCOL_775 ->
                    RegistryRemapper.mapEntityType775To765(typeId);
            case RegistryRemapper.PROTOCOL_757 -> mapEntityType757To765(typeId);
            case RegistryRemapper.PROTOCOL_756 -> mapEntityType756To765(typeId);
            default -> (typeId < 0 || typeId > RegistryRemapper.MAX_ENTITY_TYPE_765)
                    ? PIG_TYPE_765 : typeId;
        };
    }

    /**
     * 757 のエンティティ種別IDを 765 に正規化する。RegistryRemapper に
     * mapEntityType757To765 は無いため、既存の隣接表の連鎖
     * 757→759→760→761→762→763 と、763→765 の逆引き表を繋ぐ。
     * 757 にあって 759 に無い種別は無い（759 は 757 の 113 種を全て含み、
     * 5 種を追加。Stage A §11.1 実測）ため、連鎖でブタに落ちるのは
     * 範囲外の番号だけ。RegistryRemapper には追加しない。
     */
    static int mapEntityType757To765(int typeId) {
        int id759 = RegistryRemapper.mapEntityType757To759(typeId);
        int id760 = RegistryRemapper.mapEntityType759To760(id759);
        int id761 = RegistryRemapper.mapEntityType760To761(id760);
        int id762 = RegistryRemapper.mapEntityType761To762(id761);
        int id763 = RegistryRemapper.mapEntityType762To763(id762);
        if (id763 < 0 || id763 >= ENTITY_TYPE_763_TO_765.length) {
            return PIG_TYPE_765;
        }
        int mapped = ENTITY_TYPE_763_TO_765[id763];
        return mapped < 0 ? PIG_TYPE_765 : mapped;
    }

    /**
     * 756 のエンティティ種別IDを 765 に正規化する。756→757 は恒等写像
     * （113 件・同一順序。Stage A §11.1 実測）のため、757 の連鎖
     * （{@link #mapEntityType757To765}）にそのまま繋ぐ。
     * RegistryRemapper には追加しない。
     */
    static int mapEntityType756To765(int typeId) {
        return mapEntityType757To765(RegistryRemapper.mapEntityType756To757(typeId));
    }

    /**
     * サーバーのプロトコル番号のアイテムIDを 765 に正規化する。
     * 範囲外・逆引きに無いものは石（1）。
     */
    public static int normalizeItem(int itemId, int serverProtocol) {
        return switch (serverProtocol) {
            case RegistryRemapper.PROTOCOL_763 -> {
                if (itemId < 0 || itemId >= ITEM_763_TO_765.length) {
                    yield STONE_ITEM_765;
                }
                int mapped = ITEM_763_TO_765[itemId];
                yield mapped < 0 ? STONE_ITEM_765 : mapped;
            }
            case RegistryRemapper.PROTOCOL_764 -> RegistryRemapper.mapItemUp764(itemId);
            case RegistryRemapper.PROTOCOL_766 -> RegistryRemapper.mapItemUp766(itemId);
            case RegistryRemapper.PROTOCOL_767 -> RegistryRemapper.mapItem767To765(itemId);
            case RegistryRemapper.PROTOCOL_768 -> RegistryRemapper.mapItem768To765(itemId);
            case RegistryRemapper.PROTOCOL_769 -> RegistryRemapper.mapItem769To765(itemId);
            case RegistryRemapper.PROTOCOL_770 -> RegistryRemapper.mapItem770To765(itemId);
            case RegistryRemapper.PROTOCOL_771 -> RegistryRemapper.mapItem771To765(itemId);
            case RegistryRemapper.PROTOCOL_772 -> RegistryRemapper.mapItem772To765(itemId);
            case RegistryRemapper.PROTOCOL_773 -> RegistryRemapper.mapItem773To765(itemId);
            case RegistryRemapper.PROTOCOL_774 -> RegistryRemapper.mapItem774To765(itemId);
            case RegistryRemapper.PROTOCOL_775 -> RegistryRemapper.mapItem775To765(itemId);
            case RegistryRemapper.PROTOCOL_757 -> {
                // 763 側の逆引き表（ITEM_763_TO_765）を使う。ブロック・
                // エンティティ・パーティクルと同じパターン。汎用の
                // mapItemReverse（ITEM_REVERSE_MAP）は 763→765 の写像が
                // Bedrock 側の表と食い違っており、ボート（oak_boat 736→779
                // になる。正しくは 773）・レコード・絵画がずれるため使わない。
                // 連鎖の終端（763）は 1.20.2 の実測値と一致することを確認済み
                // （2026-11-15：oak_boat 736、music_disc_cat 1123、painting 843）。
                int id763 = RegistryRemapper.mapItem762To763(
                        RegistryRemapper.mapItem761To762(
                                RegistryRemapper.mapItem760To761(
                                        RegistryRemapper.mapItem759To760(
                                                RegistryRemapper.mapItem757To759(itemId)))));
                if (id763 < 0 || id763 >= ITEM_763_TO_765.length) {
                    yield STONE_ITEM_765;
                }
                int mapped = ITEM_763_TO_765[id763];
                yield mapped < 0 ? STONE_ITEM_765 : mapped;
            }
            case RegistryRemapper.PROTOCOL_756 -> {
                // 757 と同じく汎用の mapItemReverse は使わない（ITEM_REVERSE_MAP
                // と ITEM_763_TO_765 の食い違いは RegistryRemapper の判断待ちで、
                // Bedrock 側は 756 分岐だけ正しい写像で書く。757 の柱 2 と同じ対処）。
                // 連鎖は 756→757→759→760→761→762→763（756→757 は
                // music_disc_otherside（757 の 1027）1 件のずれだけ。
                // Stage A §11.1 実測）。
                int id763 = RegistryRemapper.mapItem762To763(
                        RegistryRemapper.mapItem761To762(
                                RegistryRemapper.mapItem760To761(
                                        RegistryRemapper.mapItem759To760(
                                                RegistryRemapper.mapItem757To759(
                                                        RegistryRemapper.mapItem756To757(itemId))))));
                if (id763 < 0 || id763 >= ITEM_763_TO_765.length) {
                    yield STONE_ITEM_765;
                }
                int mapped = ITEM_763_TO_765[id763];
                yield mapped < 0 ? STONE_ITEM_765 : mapped;
            }
            default -> (itemId < 0 || itemId > RegistryRemapper.MAX_ITEM_765)
                    ? STONE_ITEM_765 : itemId;
        };
    }

    /**
     * サーバーのプロトコル番号のエフェクトIDを 765 に正規化する。
     * 763／757／756 のエフェクトは 1 ベース（Speed=1）のため 1 引く。
     * 766 で増えた 33〜38 は Speed（0）へ。
     * 757／756 のエフェクトは 1〜32（darkness は無い。Stage A §11.1 実測。
     * 756・757 は同一順序）。
     */
    public static int normalizeEffectTo765(int effectId, int serverProtocol) {
        return switch (serverProtocol) {
            case RegistryRemapper.PROTOCOL_756, RegistryRemapper.PROTOCOL_757 -> {
                // 756・757 のエフェクトは 1〜32（darkness は無い。Stage A §11.1 実測）。
                // 33 以上は範囲外のため Speed に落とす（763 と同じ id−1 の
                // 形だけでは 33→32＝darkness になってしまうため、756・757 の範囲で切る）。
                if (effectId < 1 || effectId > 32) {
                    yield RegistryRemapper.SPEED_EFFECT_765;
                }
                yield effectId - 1;
            }
            case RegistryRemapper.PROTOCOL_763 -> {
                int mapped = effectId - 1;
                yield (mapped < 0 || mapped > RegistryRemapper.MAX_EFFECT_765)
                        ? RegistryRemapper.SPEED_EFFECT_765 : mapped;
            }
            case RegistryRemapper.PROTOCOL_764 -> RegistryRemapper.mapEffectUp764(effectId);
            case RegistryRemapper.PROTOCOL_766 -> RegistryRemapper.mapEffectUp766(effectId);
            // エフェクトは 767⇔768⇔769⇔770⇔771⇔772⇔773 で同一のため 768／769／770／771／772／773 も同じ表でよい。
            // 774 は BreathOfTheNautilus（39）が末尾追加のため専用の表を使う。
            // 775 のエフェクトは 774 と同一の 40 件のため 774 と同じ表を使う。
            case RegistryRemapper.PROTOCOL_767, RegistryRemapper.PROTOCOL_768,
                    RegistryRemapper.PROTOCOL_769, RegistryRemapper.PROTOCOL_770,
                    RegistryRemapper.PROTOCOL_771, RegistryRemapper.PROTOCOL_772,
                    RegistryRemapper.PROTOCOL_773 ->
                    RegistryRemapper.mapEffect767To765(effectId);
            case RegistryRemapper.PROTOCOL_774, RegistryRemapper.PROTOCOL_775 ->
                    RegistryRemapper.mapEffect774To765(effectId);
            default -> (effectId < 0 || effectId > RegistryRemapper.MAX_EFFECT_765)
                    ? RegistryRemapper.SPEED_EFFECT_765 : effectId;
        };
    }

    /**
     * Bedrock のエフェクトID（1 ベース。Speed=1）。Java 764／765／766 は
     * 0 ベースのため 1 足す。763 は 1 ベースのためそのまま。
     */
    public static int bedrockEffectId(int effectId, int serverProtocol) {
        if (serverProtocol == RegistryRemapper.PROTOCOL_763) {
            return (effectId < 1 || effectId > RegistryRemapper.MAX_EFFECT_765 + 1)
                    ? 1 : effectId;
        }
        return normalizeEffectTo765(effectId, serverProtocol) + 1;
    }

    /**
     * パーティクルIDを 765 に正規化する（メタデータ内のパーティクル読み飛ばし用）。
     * 通せない番号は ambient_entity_effect（0）。
     */
    private static int normalizeParticleTo765(int particleId, int serverProtocol) {
        return switch (serverProtocol) {
            case RegistryRemapper.PROTOCOL_763 -> {
                if (particleId < 0 || particleId >= PARTICLE_763_TO_765.length) {
                    yield 0;
                }
                int mapped = PARTICLE_763_TO_765[particleId];
                yield mapped < 0 ? 0 : mapped;
            }
            case RegistryRemapper.PROTOCOL_764 ->
                    RegistryRemapper.mapParticleUp764(particleId);
            case RegistryRemapper.PROTOCOL_766 ->
                    RegistryRemapper.mapParticleUp766(particleId);
            case RegistryRemapper.PROTOCOL_767 ->
                    RegistryRemapper.mapParticle767To765(particleId);
            case RegistryRemapper.PROTOCOL_768 ->
                    RegistryRemapper.mapParticle768To765(particleId);
            case RegistryRemapper.PROTOCOL_769 ->
                    RegistryRemapper.mapParticle769To765(particleId);
            case RegistryRemapper.PROTOCOL_770 ->
                    RegistryRemapper.mapParticle770To765(particleId);
            case RegistryRemapper.PROTOCOL_771 ->
                    RegistryRemapper.mapParticle771To765(particleId);
            case RegistryRemapper.PROTOCOL_772 ->
                    RegistryRemapper.mapParticle772To765(particleId);
            case RegistryRemapper.PROTOCOL_773 ->
                    RegistryRemapper.mapParticle773To765(particleId);
            case RegistryRemapper.PROTOCOL_774 ->
                    RegistryRemapper.mapParticle774To765(particleId);
            case RegistryRemapper.PROTOCOL_775 ->
                    RegistryRemapper.mapParticle775To765(particleId);
            case RegistryRemapper.PROTOCOL_757 -> mapParticle757To765(particleId);
            case RegistryRemapper.PROTOCOL_756 -> mapParticle756To765(particleId);
            default -> (particleId < 0 || particleId > RegistryRemapper.MAX_PARTICLE_765)
                    ? 0 : particleId;
        };
    }

    /**
     * 757 のパーティクルIDを 765 に正規化する。RegistryRemapper に
     * mapParticle757To765 は無いため、既存の隣接表の連鎖
     * 757→759→760→761→762→763 と、763→765 の逆引き表を繋ぐ。
     * 757 の 88 粒子は 759 で全件カバーされる（Stage A §11.1 実測。
     * 759 の新規 5 粒子は 757 には無い）ため、連鎖で 0 に落ちるのは
     * 範囲外の番号だけ。RegistryRemapper には追加しない。
     */
    static int mapParticle757To765(int particleId) {
        int id759 = RegistryRemapper.mapParticle757To759(particleId);
        int id760 = RegistryRemapper.mapParticle759To760(id759);
        int id761 = RegistryRemapper.mapParticle760To761(id760);
        int id762 = RegistryRemapper.mapParticle761To762(id761);
        int id763 = RegistryRemapper.mapParticle762To763(id762);
        if (id763 < 0 || id763 >= PARTICLE_763_TO_765.length) {
            return 0;
        }
        int mapped = PARTICLE_763_TO_765[id763];
        return mapped < 0 ? 0 : mapped;
    }

    /**
     * 756 のパーティクルIDを 765 に正規化する。756→757 の写像
     * （barrier（2）／light（3）は 757 の block_marker（3）に統合。
     * Stage A §11.1／§11.3 実測）のあと、757 の連鎖
     * （{@link #mapParticle757To765}）に繋ぐ。RegistryRemapper には追加しない。
     * 注意：756 の barrier／light はデータ無し（SimpleParticleType）のため、
     * メタデータ内の読み飛ばし（{@link #skipParticle}）では 765 の
     * block_marker のブロック状態 varint を読んではいけない。
     */
    static int mapParticle756To765(int particleId) {
        return mapParticle757To765(RegistryRemapper.mapParticle756To757(particleId));
    }

    // ---- 角度・速度 ----

    /** Java の角度バイト（256＝360°）を度に直す。 */
    public static float byteToAngle(int value) {
        return (value & 0xFF) * 360.0f / 256.0f;
    }

    /** 度を Bedrock の角度バイト（256＝360°）に直す。 */
    public static int angleToByte(float angle) {
        int value = Math.round(angle * 256.0f / 360.0f) & 0xFF;
        return value;
    }

    /** Java の速度 short（1/8000 ブロック/tick）をブロック/tick に直す。 */
    public static float shortVelocityToFloat(int value) {
        return value / 8000.0f;
    }

    /** Java の相対移動 short（1/4096 ブロック）をブロックに直す。 */
    public static double shortDeltaToDouble(int value) {
        return value / 4096.0;
    }

    // ---- Java パケットの読み取り ----

    /** Spawn Entity（Java）の内容（種別IDは 765 に正規化済み）。 */
    public record SpawnEntity(int entityId, UUID uuid, int type765,
            double x, double y, double z, float pitch, float yaw, float headPitch,
            int objectData, double velX, double velY, double velZ) {
    }

    /** Spawn Entity を読む。 */
    public static SpawnEntity parseSpawnEntity(byte[] payload, int serverProtocol) {
        PacketIO.Reader reader = new PacketIO.Reader(payload);
        int entityId = reader.readVarInt();
        byte[] uuidBytes = reader.readBytes(16);
        int type = normalizeEntityType(reader.readVarInt(), serverProtocol);
        double x = reader.readDouble();
        double y = reader.readDouble();
        double z = reader.readDouble();
        int pitch;
        int yaw;
        int headPitch;
        int objectData;
        if (serverProtocol == RegistryRemapper.PROTOCOL_756
                || serverProtocol == RegistryRemapper.PROTOCOL_757) {
            // 1.17.1／1.18 の Spawn Entity（汎用。0x00）には headPitch が無く、
            // objectData は i32（1.19 では headPitch＋varint）。
            // minecraft-data mc-1.17.1／mc-1.18-protocol.json で確認
            // （Stage B §13.5／§14.4 も同じ）。
            pitch = reader.readByte();
            yaw = reader.readByte();
            headPitch = yaw;
            objectData = reader.readInt();
        } else {
            pitch = reader.readByte();
            yaw = reader.readByte();
            headPitch = reader.readByte();
            objectData = reader.readVarInt();
        }
        int velX = reader.readShort();
        int velY = reader.readShort();
        int velZ = reader.readShort();
        if (reader.remaining() != 0) {
            throw new IllegalArgumentException("Spawn Entity の余りがあります。");
        }
        return new SpawnEntity(entityId,
                new UUID(bytesToLong(uuidBytes, 0), bytesToLong(uuidBytes, 8)),
                type, x, y, z, byteToAngle(pitch), byteToAngle(yaw), byteToAngle(headPitch),
                objectData, shortVelocityToFloat(velX), shortVelocityToFloat(velY),
                shortVelocityToFloat(velZ));
    }

    /**
     * Spawn Entity Living（757 の 0x02）を読む。1.19 で Spawn Entity（0x00）に
     * 統合されたため、757 専用。{entityId varint, uuid, type varint, x/y/z f64,
     * yaw i8, pitch i8, headPitch i8, velocity 3×short}。yaw と pitch の順番が
     * 汎用 Spawn Entity と逆（yaw が先）な点に注意。objectData は無いため 0。
     * minecraft-data mc-1.18-protocol.json で確認。
     */
    public static SpawnEntity parseSpawnEntityLiving(byte[] payload, int serverProtocol) {
        PacketIO.Reader reader = new PacketIO.Reader(payload);
        int entityId = reader.readVarInt();
        byte[] uuidBytes = reader.readBytes(16);
        int type = normalizeEntityType(reader.readVarInt(), serverProtocol);
        double x = reader.readDouble();
        double y = reader.readDouble();
        double z = reader.readDouble();
        int yaw = reader.readByte();
        int pitch = reader.readByte();
        int headPitch = reader.readByte();
        int velX = reader.readShort();
        int velY = reader.readShort();
        int velZ = reader.readShort();
        if (reader.remaining() != 0) {
            throw new IllegalArgumentException("Spawn Entity Living の余りがあります。");
        }
        return new SpawnEntity(entityId,
                new UUID(bytesToLong(uuidBytes, 0), bytesToLong(uuidBytes, 8)),
                type, x, y, z, byteToAngle(pitch), byteToAngle(yaw), byteToAngle(headPitch),
                0, shortVelocityToFloat(velX), shortVelocityToFloat(velY),
                shortVelocityToFloat(velZ));
    }

    /** Spawn Entity Painting（757 の 0x03）の内容（題名は 757 の絵画レジストリ番号）。 */
    public record SpawnPainting(int entityId, UUID uuid, int title757, long positionRaw,
            int direction) {
    }

    /**
     * Spawn Entity Painting（757 の 0x03）を読む。1.19 で Spawn Entity（0x00）に
     * 統合されたため、757 専用。{entityId varint, uuid, title varint,
     * location position, direction u8}。minecraft-data mc-1.18-protocol.json で確認。
     * 1.18 の絵画にはメタデータの variant が無い（Painting に EntityDataAccessor が無い。
     * server-1.18.jar の mappings で確認）ため、題名はこのパケットからしか取れない。
     */
    public static SpawnPainting parseSpawnEntityPainting(byte[] payload) {
        PacketIO.Reader reader = new PacketIO.Reader(payload);
        int entityId = reader.readVarInt();
        byte[] uuidBytes = reader.readBytes(16);
        int title = reader.readVarInt();
        long positionRaw = reader.readPositionRaw();
        int direction = reader.readUnsignedByte();
        if (reader.remaining() != 0) {
            throw new IllegalArgumentException("Spawn Entity Painting の余りがあります。");
        }
        return new SpawnPainting(entityId,
                new UUID(bytesToLong(uuidBytes, 0), bytesToLong(uuidBytes, 8)),
                title, positionRaw, direction);
    }

    /**
     * 757 の絵画題名（レジストリ番号）を 765 の絵画バリエーション番号に直す。
     * 0〜24 は同一。25 は 757 では donkey_kong だが、765 では earth／wind／
     * water／fire（1.20.5 追加）が 25〜28 に挿入されたため donkey_kong は 29。
     * 範囲外は kebab（0）。
     */
    public static int paintingId757To765(int title757) {
        if (title757 < 0 || title757 > 25) {
            return 0;
        }
        return title757 == 25 ? 29 : title757;
    }

    /**
     * 756 の絵画題名（レジストリ番号）を 765 の絵画バリエーション番号に直す。
     * 756・757 の絵画は 26 件・同一順序（Stage A §11.1 実測）のため
     * 757 と同じ写像（0〜24 は同一、25 の donkey_kong は 29）。
     */
    public static int paintingId756To765(int title756) {
        return paintingId757To765(title756);
    }

    /**
     * 757 の絵画スポーン用の合成メタデータ（題名だけ入ったもの）。
     * 1.18 の絵画には variant のメタデータが無いため、スポーン直後に
     * これを送って Bedrock 側に題名を伝える（そうしないと絵画が送られない）。
     */
    public static JavaMetadata paintingMetadata(int entityId, int title765) {
        MetadataBuilder builder = new MetadataBuilder();
        builder.paintingVariant = paintingName(title765);
        return builder.build(entityId);
    }

    private static long bytesToLong(byte[] bytes, int offset) {
        long value = 0;
        for (int i = 0; i < 8; i++) {
            value = (value << 8) | (bytes[offset + i] & 0xFFL);
        }
        return value;
    }

    /** Spawn Experience Orb（Java）の内容。 */
    public record SpawnExperienceOrb(int entityId, double x, double y, double z, int count) {
    }

    /** Spawn Experience Orb を読む。 */
    public static SpawnExperienceOrb parseSpawnExperienceOrb(byte[] payload) {
        PacketIO.Reader reader = new PacketIO.Reader(payload);
        int entityId = reader.readVarInt();
        double x = reader.readDouble();
        double y = reader.readDouble();
        double z = reader.readDouble();
        int count = reader.readShort();
        if (reader.remaining() != 0) {
            throw new IllegalArgumentException("Spawn Experience Orb の余りがあります。");
        }
        return new SpawnExperienceOrb(entityId, x, y, z, count);
    }

    /** 相対移動（Rel Entity Move）の内容。 */
    public record EntityMove(int entityId, double dx, double dy, double dz, boolean onGround) {
    }

    /** 相対移動を読む。 */
    public static EntityMove parseRelativeMove(byte[] payload) {
        PacketIO.Reader reader = new PacketIO.Reader(payload);
        int entityId = reader.readVarInt();
        double dx = shortDeltaToDouble(reader.readShort());
        double dy = shortDeltaToDouble(reader.readShort());
        double dz = shortDeltaToDouble(reader.readShort());
        boolean onGround = reader.readBoolean();
        if (reader.remaining() != 0) {
            throw new IllegalArgumentException("Rel Entity Move の余りがあります。");
        }
        return new EntityMove(entityId, dx, dy, dz, onGround);
    }

    /** 移動＋向き（Entity Move Look）の内容。 */
    public record EntityMoveLook(int entityId, double dx, double dy, double dz,
            float yaw, float pitch, boolean onGround) {
    }

    /** 移動＋向きを読む。 */
    public static EntityMoveLook parseMoveLook(byte[] payload) {
        PacketIO.Reader reader = new PacketIO.Reader(payload);
        int entityId = reader.readVarInt();
        double dx = shortDeltaToDouble(reader.readShort());
        double dy = shortDeltaToDouble(reader.readShort());
        double dz = shortDeltaToDouble(reader.readShort());
        float yaw = byteToAngle(reader.readByte());
        float pitch = byteToAngle(reader.readByte());
        boolean onGround = reader.readBoolean();
        if (reader.remaining() != 0) {
            throw new IllegalArgumentException("Entity Move Look の余りがあります。");
        }
        return new EntityMoveLook(entityId, dx, dy, dz, yaw, pitch, onGround);
    }

    /** 向きのみ（Entity Look）の内容。 */
    public record EntityLook(int entityId, float yaw, float pitch, boolean onGround) {
    }

    /** 向きのみを読む。 */
    public static EntityLook parseLook(byte[] payload) {
        PacketIO.Reader reader = new PacketIO.Reader(payload);
        int entityId = reader.readVarInt();
        float yaw = byteToAngle(reader.readByte());
        float pitch = byteToAngle(reader.readByte());
        boolean onGround = reader.readBoolean();
        if (reader.remaining() != 0) {
            throw new IllegalArgumentException("Entity Look の余りがあります。");
        }
        return new EntityLook(entityId, yaw, pitch, onGround);
    }

    /** テレポート（Entity Teleport）の内容。 */
    public record EntityTeleport(int entityId, double x, double y, double z,
            float yaw, float pitch, boolean onGround) {
    }

    /** テレポートを読む。 */
    public static EntityTeleport parseTeleport(byte[] payload) {
        PacketIO.Reader reader = new PacketIO.Reader(payload);
        int entityId = reader.readVarInt();
        double x = reader.readDouble();
        double y = reader.readDouble();
        double z = reader.readDouble();
        float yaw = byteToAngle(reader.readByte());
        float pitch = byteToAngle(reader.readByte());
        boolean onGround = reader.readBoolean();
        if (reader.remaining() != 0) {
            throw new IllegalArgumentException("Entity Teleport の余りがあります。");
        }
        return new EntityTeleport(entityId, x, y, z, yaw, pitch, onGround);
    }

    /** 速度（Entity Velocity）の内容。 */
    public record EntityVelocity(int entityId, float velX, float velY, float velZ) {
    }

    /** 速度を読む。 */
    public static EntityVelocity parseVelocity(byte[] payload) {
        PacketIO.Reader reader = new PacketIO.Reader(payload);
        int entityId = reader.readVarInt();
        float velX = shortVelocityToFloat(reader.readShort());
        float velY = shortVelocityToFloat(reader.readShort());
        float velZ = shortVelocityToFloat(reader.readShort());
        if (reader.remaining() != 0) {
            throw new IllegalArgumentException("Entity Velocity の余りがあります。");
        }
        return new EntityVelocity(entityId, velX, velY, velZ);
    }

    /** 頭の向き（Entity Head Rotation）の内容。 */
    public record EntityHeadRotation(int entityId, float headYaw) {
    }

    /** 頭の向きを読む。 */
    public static EntityHeadRotation parseHeadRotation(byte[] payload) {
        PacketIO.Reader reader = new PacketIO.Reader(payload);
        int entityId = reader.readVarInt();
        float headYaw = byteToAngle(reader.readByte());
        if (reader.remaining() != 0) {
            throw new IllegalArgumentException("Entity Head Rotation の余りがあります。");
        }
        return new EntityHeadRotation(entityId, headYaw);
    }

    /** 削除（Remove Entities）のエンティティID一覧を読む。 */
    public static List<Integer> parseRemoveEntities(byte[] payload) {
        PacketIO.Reader reader = new PacketIO.Reader(payload);
        int count = reader.readVarInt();
        if (count < 0 || count > 1024) {
            throw new IllegalArgumentException("Remove Entities の件数が異常です: " + count);
        }
        List<Integer> ids = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            ids.add(reader.readVarInt());
        }
        if (reader.remaining() != 0) {
            throw new IllegalArgumentException("Remove Entities の余りがあります。");
        }
        return ids;
    }

    // ---- スロット（Java ItemStack）----

    /** Java のスロット 1 個。{@code clean} が false なら 766 の成分付きで、後ろが読めない。 */
    public record JavaSlot(boolean present, int item765, int count,
            boolean componentsPresent, boolean clean) {
        /** 空スロット。 */
        public static JavaSlot empty() {
            return new JavaSlot(false, 0, 0, false, true);
        }
    }

    /** Bedrock のアイテム 1 個（AddItemEntity／MobEquipment 用）。 */
    public record BedrockItem(int networkId, int count, int blockRuntimeId,
            int metadata) {
        /** 3 個版（metadata=0）のコンストラクタ。既存の呼び出し元用。 */
        public BedrockItem(int networkId, int count, int blockRuntimeId) {
            this(networkId, count, blockRuntimeId, 0);
        }

        /** 空アイテム。 */
        public static BedrockItem air() {
            return new BedrockItem(0, 0, 0);
        }
    }

    /**
     * Java のスロット 1 個を読む。765 までは NBT を読み飛ばす。763・756 は
     * 名前付き、757・759〜762・764／765 は名前なし（下の既定の分岐）。
     * 756 の NBT が名前付きなのは、実バニラの FriendlyByteBuf.writeNbt が
     * NbtIo.write（型＋空名＋ペイロード）で書くため（1.18 の server jar の
     * javap で確認。ChunkTranslator の「763 は名前付き、764 は名前なし」と同じ）。
     * 757 は既存の匿名読みのままにする（実バニラ 1.18 も名前付きのため
     * 757 の読みは非空 NBT でずれる疑いがあるが、判断待ちのため触らない）。
     * 757 のスロットは 759 と同一の
     * [present bool][id varint][count i8][optionalNbt]（minecraft-data
     * mc-1.18-protocol.json の slot 型で確認。Stage B §13.5 も同じ）で、
     * 756 も同一の外形（mc-1.17.1-protocol.json で確認。Stage B §14.4）。
     * 766／767／768／769／770／771／772／773／774／775 はデータコンポーネント形式で、
     * 中身は読まずに個数だけ確認する（成分付きだと後ろは読めないため
     * {@code clean=false} を返す）。768／769／770／771／772／773／774／775 のスロットの外形（個数・added・removed
     * の varint 3 個）は 767 と同じため、同じ読み方でよい。
     */
    public static JavaSlot readSlot(PacketIO.Reader reader, int serverProtocol) {
        boolean present = reader.readBoolean();
        if (!present) {
            return JavaSlot.empty();
        }
        int item = normalizeItem(reader.readVarInt(), serverProtocol);
        int count;
        if (serverProtocol == RegistryRemapper.PROTOCOL_766
                || serverProtocol == RegistryRemapper.PROTOCOL_767
                || serverProtocol == RegistryRemapper.PROTOCOL_768
                || serverProtocol == RegistryRemapper.PROTOCOL_769
                || serverProtocol == RegistryRemapper.PROTOCOL_770
                || serverProtocol == RegistryRemapper.PROTOCOL_771
                || serverProtocol == RegistryRemapper.PROTOCOL_772
                || serverProtocol == RegistryRemapper.PROTOCOL_773
                || serverProtocol == RegistryRemapper.PROTOCOL_774
                || serverProtocol == RegistryRemapper.PROTOCOL_775) {
            count = reader.readVarInt();
            int addCount = reader.readVarInt();
            int removeCount = reader.readVarInt();
            if (addCount == 0 && removeCount == 0) {
                return new JavaSlot(true, item, count, false, true);
            }
            // 成分付き（エンチャント・名前・ポーション等）のスロットは、
            // 成分の形式が種別ごとに違うため読み飛ばせない。
            return new JavaSlot(true, item, count, true, false);
        }
        count = reader.readByte();
        if (serverProtocol == RegistryRemapper.PROTOCOL_763
                || serverProtocol == RegistryRemapper.PROTOCOL_756) {
            reader.readNamedNbt();
        } else {
            reader.readAnonymousNbt();
        }
        return new JavaSlot(true, item, count, false, true);
    }

    /** Java のスロットを Bedrock のアイテムに直す（空なら air）。 */
    public static BedrockItem toBedrockItem(JavaSlot slot) {
        return toBedrockItem(slot, BedrockProtocol.PROTOCOL_1_20_50);
    }

    /**
     * Java のスロットを Bedrock のアイテムに直す（バージョン別。空なら air）。
     * そのバージョン用の network_id・metadata を使う。
     */
    public static BedrockItem toBedrockItem(JavaSlot slot, int bedrockProtocol) {
        if (slot == null || !slot.present()) {
            return BedrockItem.air();
        }
        int networkId = BedrockItemIds.networkId(slot.item765(), bedrockProtocol);
        int metadata = BedrockItemIds.metadata(slot.item765(), bedrockProtocol);
        return new BedrockItem(networkId, Math.max(1, slot.count()),
                BedrockItemBlockIds.blockRuntimeId(slot.item765()), metadata);
    }

    // ---- 装備・エフェクト・属性・乗騎 ----

    /** Entity Equipment の 1 エントリ（Java スロット番号＋中身）。 */
    public record EntityEquipmentEntry(int slot, JavaSlot item) {
    }

    /** Entity Equipment の内容。 */
    public record EntityEquipment(int entityId, List<EntityEquipmentEntry> entries,
            boolean complete) {
    }

    /**
     * Entity Equipment を読む。スロット番号の bit 0x80 が「続きあり」。
     * 766 の成分付きスロットで止まった場合は {@code complete=false}。
     */
    public static EntityEquipment parseEntityEquipment(byte[] payload, int serverProtocol) {
        PacketIO.Reader reader = new PacketIO.Reader(payload);
        int entityId = reader.readVarInt();
        List<EntityEquipmentEntry> entries = new ArrayList<>();
        boolean complete = true;
        while (true) {
            int slotByte = reader.readUnsignedByte();
            JavaSlot item = readSlot(reader, serverProtocol);
            entries.add(new EntityEquipmentEntry(slotByte & 0x7F, item));
            if (!item.clean()) {
                complete = false;
                break;
            }
            if ((slotByte & 0x80) == 0) {
                break;
            }
        }
        return new EntityEquipment(entityId, entries, complete);
    }

    /** Entity Effect の内容（エフェクトIDは 765 に正規化済み）。 */
    public record EntityEffect(int entityId, int effect765, int amplifier, int duration,
            boolean showParticles) {
    }

    /** Entity Effect を読む（factorCodec は読み飛ばす）。 */
    public static EntityEffect parseEntityEffect(byte[] payload, int serverProtocol) {
        PacketIO.Reader reader = new PacketIO.Reader(payload);
        int entityId = reader.readVarInt();
        if (serverProtocol == RegistryRemapper.PROTOCOL_756
                || serverProtocol == RegistryRemapper.PROTOCOL_757) {
            // 1.17.1／1.18 の Entity Effect：{entityId varint, effectId i8, amplifier i8,
            // duration varint, hideParticles i8}。1.19 では effectId が varint になり、
            // 末尾に factorCodec（option NBT）が付く（Stage B §13.5）。
            // minecraft-data mc-1.17.1／mc-1.18-protocol.json で確認。
            int effect = normalizeEffectTo765(reader.readByte(), serverProtocol);
            int amplifier = reader.readByte();
            int duration = reader.readVarInt();
            int hideParticles = reader.readByte();
            if (reader.remaining() != 0) {
                throw new IllegalArgumentException("Entity Effect の余りがあります。");
            }
            return new EntityEffect(entityId, effect, amplifier, duration,
                    hideParticles == 0);
        }
        int effect = normalizeEffectTo765(reader.readVarInt(), serverProtocol);
        int amplifier = reader.readByte();
        int duration = reader.readVarInt();
        int flags = reader.readByte();
        boolean hasFactor = reader.readBoolean();
        if (hasFactor) {
            if (serverProtocol == RegistryRemapper.PROTOCOL_763) {
                reader.readNamedNbt();
            } else {
                reader.readAnonymousNbt();
            }
        }
        if (reader.remaining() != 0) {
            throw new IllegalArgumentException("Entity Effect の余りがあります。");
        }
        return new EntityEffect(entityId, effect, amplifier, duration, (flags & 0x02) != 0);
    }

    /** Remove Entity Effect の内容（エフェクトIDは 765 に正規化済み）。 */
    public record RemoveEntityEffect(int entityId, int effect765) {
    }

    /** Remove Entity Effect を読む。 */
    public static RemoveEntityEffect parseRemoveEntityEffect(byte[] payload, int serverProtocol) {
        PacketIO.Reader reader = new PacketIO.Reader(payload);
        int entityId = reader.readVarInt();
        // 1.17.1／1.18 の effectId は i8（1.19 では varint。Stage B §13.5）。
        // 値は 1〜32 のため varint で読んでも同じバイトになるが、形式通り i8 で読む。
        int effect = serverProtocol == RegistryRemapper.PROTOCOL_756
                || serverProtocol == RegistryRemapper.PROTOCOL_757
                ? normalizeEffectTo765(reader.readByte(), serverProtocol)
                : normalizeEffectTo765(reader.readVarInt(), serverProtocol);
        if (reader.remaining() != 0) {
            throw new IllegalArgumentException("Remove Entity Effect の余りがあります。");
        }
        return new RemoveEntityEffect(entityId, effect);
    }

    /** Update Attributes の内容（最大体力・移動速度だけ抜く）。 */
    public record UpdateAttributes(int entityId, Double maxHealth, Double movementSpeed) {
    }

    /**
     * Update Attributes を読む。763 は属性キーが番号（0＝最大体力、
     * 2＝ノックバック耐性、3＝移動速度。minecraft-data 1.20 の
     * attributes.json の並びによる）、764／765 は識別子文字列、
     * 766／767／768／769／770／771／772／773／774／775 は番号（属性名の表で識別子に直す）。
     * 767／768／769／770／771／772／773／774／775 は modifier の UUID が文字列（766 までは 16 バイトの UUID）。
     * 768 は一部の属性名から接頭辞が外れた（generic.burning_time →
     * burning_time 等。protocol.json の Update Attributes キー表による）。
     * 769／770 の属性名は 768 と同一（attributeName769／attributeName770 は
     * 768 と同じ内容）。771 の属性名は 770 とは違う（camera_distance が
     * 8番に挿入、waypoint_transmit_range／waypoint_receive_range が追加。
     * attributeName771 は771専用の表）。772／773 の属性名は 771 と同一
     * （attributeName772／attributeName773 は771と同じ内容）。
     * 774 の属性名は 773 と同一（attributeName774 は773と同じ内容）。
     * 775 の属性名は 774 と同一（attributeName775 は774と同じ内容）。
     * 757 は 759 と同じく属性キーが識別子文字列、modifier UUID は 16 バイトのため、
     * 既定の分岐（readString＋readBytes(16)）で読める（minecraft-data
     * mc-1.18／mc-1.19-protocol.json の packet_entity_update_attributes で確認。
     * 757 専用の分岐は不要）。
     */
    public static UpdateAttributes parseUpdateAttributes(byte[] payload, int serverProtocol) {
        PacketIO.Reader reader = new PacketIO.Reader(payload);
        int entityId = reader.readVarInt();
        int count = reader.readVarInt();
        if (count < 0 || count > 64) {
            throw new IllegalArgumentException("Update Attributes の件数が異常です: " + count);
        }
        Double maxHealth = null;
        Double movementSpeed = null;
        for (int i = 0; i < count; i++) {
            String key;
            if (serverProtocol == RegistryRemapper.PROTOCOL_763) {
                int keyId = reader.readVarInt();
                key = switch (keyId) {
                    case 0 -> "minecraft:generic.max_health";
                    case 3 -> "minecraft:generic.movement_speed";
                    default -> "";
                };
            } else if (serverProtocol == RegistryRemapper.PROTOCOL_766
                    || serverProtocol == RegistryRemapper.PROTOCOL_767
                    || serverProtocol == RegistryRemapper.PROTOCOL_768
                    || serverProtocol == RegistryRemapper.PROTOCOL_769
                    || serverProtocol == RegistryRemapper.PROTOCOL_770
                    || serverProtocol == RegistryRemapper.PROTOCOL_771
                    || serverProtocol == RegistryRemapper.PROTOCOL_772
                    || serverProtocol == RegistryRemapper.PROTOCOL_773
                    || serverProtocol == RegistryRemapper.PROTOCOL_774
                    || serverProtocol == RegistryRemapper.PROTOCOL_775) {
                // 766／767／768／769／770／771／772／773／774／775 は属性キーが番号。名前表で識別子に直す。
                // 表は "generic.movement_speed" のような平文の名前なので、
                // 下の比較（"minecraft:generic.movement_speed"）に合わせるため
                // "minecraft:" を付ける。
                int keyId = reader.readVarInt();
                String name = serverProtocol == RegistryRemapper.PROTOCOL_775
                        ? RegistryRemapper.attributeName775(keyId)
                        : serverProtocol == RegistryRemapper.PROTOCOL_774
                        ? RegistryRemapper.attributeName774(keyId)
                        : serverProtocol == RegistryRemapper.PROTOCOL_773
                        ? RegistryRemapper.attributeName773(keyId)
                        : serverProtocol == RegistryRemapper.PROTOCOL_772
                                ? RegistryRemapper.attributeName772(keyId)
                                : serverProtocol == RegistryRemapper.PROTOCOL_771
                                        ? RegistryRemapper.attributeName771(keyId)
                                        : serverProtocol == RegistryRemapper.PROTOCOL_770
                                ? RegistryRemapper.attributeName770(keyId)
                                : serverProtocol == RegistryRemapper.PROTOCOL_769
                                ? RegistryRemapper.attributeName769(keyId)
                                : serverProtocol == RegistryRemapper.PROTOCOL_768
                                        ? RegistryRemapper.attributeName768(keyId)
                                        : serverProtocol == RegistryRemapper.PROTOCOL_767
                                                ? RegistryRemapper.attributeName767(keyId)
                                                : RegistryRemapper.attributeName766(keyId);
                key = name != null ? "minecraft:" + name : "";
            } else {
                key = reader.readString();
            }
            double value = reader.readDouble();
            int modifierCount = reader.readVarInt();
            if (modifierCount < 0 || modifierCount > 64) {
                throw new IllegalArgumentException("Update Attributes の補正数が異常です。");
            }
            for (int j = 0; j < modifierCount; j++) {
                if (serverProtocol == RegistryRemapper.PROTOCOL_767
                        || serverProtocol == RegistryRemapper.PROTOCOL_768
                        || serverProtocol == RegistryRemapper.PROTOCOL_769
                        || serverProtocol == RegistryRemapper.PROTOCOL_770
                        || serverProtocol == RegistryRemapper.PROTOCOL_771
                        || serverProtocol == RegistryRemapper.PROTOCOL_772
                        || serverProtocol == RegistryRemapper.PROTOCOL_773
                        || serverProtocol == RegistryRemapper.PROTOCOL_774
                        || serverProtocol == RegistryRemapper.PROTOCOL_775) {
                    reader.readString(); // modifier UUID（767／768／769／770／771／772／773／774／775 は文字列）
                } else {
                    reader.readBytes(16); // modifier UUID
                }
                reader.readDouble(); // amount
                reader.readByte(); // operation
            }
            if ("minecraft:generic.max_health".equals(key)) {
                maxHealth = value;
            } else if ("minecraft:generic.movement_speed".equals(key)) {
                movementSpeed = value;
            }
        }
        if (reader.remaining() != 0) {
            throw new IllegalArgumentException("Update Attributes の余りがあります。");
        }
        return new UpdateAttributes(entityId, maxHealth, movementSpeed);
    }

    /** Set Passengers の内容。 */
    public record SetPassengers(int vehicleId, List<Integer> passengerIds) {
    }

    /** Set Passengers を読む。 */
    public static SetPassengers parseSetPassengers(byte[] payload) {
        PacketIO.Reader reader = new PacketIO.Reader(payload);
        int vehicleId = reader.readVarInt();
        int count = reader.readVarInt();
        if (count < 0 || count > 64) {
            throw new IllegalArgumentException("Set Passengers の件数が異常です: " + count);
        }
        List<Integer> passengers = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            passengers.add(reader.readVarInt());
        }
        if (reader.remaining() != 0) {
            throw new IllegalArgumentException("Set Passengers の余りがあります。");
        }
        return new SetPassengers(vehicleId, passengers);
    }

    /** Attach Entity（リード）の内容。 */
    public record AttachEntity(int entityId, int vehicleId) {
    }

    /** Attach Entity を読む。 */
    public static AttachEntity parseAttachEntity(byte[] payload) {
        PacketIO.Reader reader = new PacketIO.Reader(payload);
        int entityId = reader.readInt();
        int vehicleId = reader.readInt();
        if (reader.remaining() != 0) {
            throw new IllegalArgumentException("Attach Entity の余りがあります。");
        }
        return new AttachEntity(entityId, vehicleId);
    }

    // ---- Bedrock の Entity Data（MetadataDictionary）----

    /**
     * Bedrock の MetadataDictionary 1 個分。エントリは (key, type, 値) で、
     * type は 0=byte, 1=short, 2=int(zigzag32), 3=float, 4=string,
     * 6=vec3i, 7=long(zigzag64), 8=vec3f。
     */
    public static final class BedrockEntityData {
        /** エントリ 1 個。 */
        public record Entry(int key, int type, Object value) {
        }

        /** Data key：flags。 */
        public static final int KEY_FLAGS = 0;
        /** Data key：体力（int）。 */
        public static final int KEY_HEALTH = 1;
        /** Data key：variant（int）。 */
        public static final int KEY_VARIANT = 2;
        /** Data key：色（int）。 */
        public static final int KEY_COLOR = 3;
        /** Data key：名前タグ（string）。 */
        public static final int KEY_NAMETAG = 4;
        /** Data key：owner のエンティティID（long）。 */
        public static final int KEY_OWNER_EID = 5;
        /** Data key：空気（int）。 */
        public static final int KEY_AIR = 7;
        /** Data key：ポーションの色（int）。 */
        public static final int KEY_POTION_COLOR = 8;
        /** Data key：経験値オーブの値（int）。 */
        public static final int KEY_EXPERIENCE_VALUE = 15;
        /** Data key：トロッコの表示ブロック（int）。 */
        public static final int KEY_MINECART_DISPLAY_BLOCK = 16;
        /** Data key：トロッコの表示オフセット（int）。 */
        public static final int KEY_MINECART_DISPLAY_OFFSET = 17;
        /** Data key：トロッコの表示有無（byte）。 */
        public static final int KEY_MINECART_HAS_DISPLAY = 18;
        /** Data key：馬の種別（int）。 */
        public static final int KEY_HORSE_TYPE = 19;
        /** Data key：クリーパーの膨らみ（int）。 */
        public static final int KEY_CREEPER_SWELL = 20;
        /** Data key：クリーパーの膨らみ方向（byte）。 */
        public static final int KEY_CREEPER_SWELL_DIRECTION = 21;
        /** Data key：エンダーマンの持ちブロック（int）。 */
        public static final int KEY_ENDERMAN_HELD_RUNTIME_ID = 23;
        /** Data key：リードの持ち主（long）。 */
        public static final int KEY_LEAD_HOLDER_EID = 37;
        /** Data key：大きさ（float）。 */
        public static final int KEY_SCALE = 38;
        /** Data key：TNT の導火線（int）。 */
        public static final int KEY_FUSE_LENGTH = 55;
        /** Data key：エリアエフェクトクラウドの半径（float）。 */
        public static final int KEY_AREA_EFFECT_CLOUD_RADIUS = 61;
        /** Data key：シュルカーの開き具合（byte）。 */
        public static final int KEY_SHULKER_PEEK_ID = 64;
        /** Data key：ヤギの角の数（int）。 */
        public static final int KEY_GOAT_HORN_COUNT = 123;
        /** Data key：強さ（ラマ。int）。 */
        public static final int KEY_STRENGTH = 75;
        /** Data key：最大の強さ（ラマ。int）。 */
        public static final int KEY_MAX_STRENGTH = 76;
        /** Data key：防具立てのポーズ（int）。 */
        public static final int KEY_ARMOR_STAND_POSE_INDEX = 79;
        /** Data key：名前タグを常に表示（byte）。 */
        public static final int KEY_ALWAYS_SHOW_NAMETAG = 81;
        /** Data key：フグの大きさ（int）。 */
        public static final int KEY_PUFFERFISH_SIZE = 86;
        /** Data key：座る量（float）。 */
        public static final int KEY_SITTING_AMOUNT = 89;
        /** Data key：flags 拡張（long）。 */
        public static final int KEY_FLAGS_EXTENDED = 92;
        /** Data key：ウィザーの無敵時間（int）。 */
        public static final int KEY_WITHER_INVULNERABLE_TICKS = 48;
        /** Data key：実体年齢（int。負＝赤ちゃん）。 */
        public static final int KEY_ENTITY_AGE = 24;
        /** Data key：ベッドの位置（vec3i）。 */
        public static final int KEY_PLAYER_BED_POSITION = 28;

        /** flags（key 0）のビット：燃えている。 */
        public static final int FLAG_ONFIRE = 0;
        /** flags のビット：しゃがみ。 */
        public static final int FLAG_SNEAKING = 1;
        /** flags のビット：ダッシュ。 */
        public static final int FLAG_SPRINTING = 3;
        /** flags のビット：不可視。 */
        public static final int FLAG_INVISIBLE = 5;
        /** flags のビット：鞍。 */
        public static final int FLAG_SADDLED = 8;
        /** flags のビット：点火。 */
        public static final int FLAG_IGNITED = 10;
        /** flags のビット：赤ちゃん。 */
        public static final int FLAG_BABY = 11;
        /** flags のビット：クリティカル。 */
        public static final int FLAG_CRITICAL = 13;
        /** flags のビット：名前タグを表示できる。 */
        public static final int FLAG_CAN_SHOW_NAMETAG = 14;
        /** flags のビット：名前タグを常に表示。 */
        public static final int FLAG_ALWAYS_SHOW_NAMETAG = 15;
        /** flags のビット：無音。 */
        public static final int FLAG_SILENT = 17;
        /** flags のビット：壁登り。 */
        public static final int FLAG_WALLCLIMBING = 18;
        /** flags のビット：座っている。 */
        public static final int FLAG_SITTING = 24;
        /** flags のビット：怒っている。 */
        public static final int FLAG_ANGRY = 25;
        /** flags のビット：興味あり。 */
        public static final int FLAG_INTERESTED = 26;
        /** flags のビット：帯電。 */
        public static final int FLAG_CHARGED = 27;
        /** flags のビット：懐いた。 */
        public static final int FLAG_TAMED = 28;
        /** flags のビット：毛刈り済み。 */
        public static final int FLAG_SHEARED = 31;
        /** flags のビット：滑空。 */
        public static final int FLAG_GLIDING = 32;
        /** flags のビット：土台を表示。 */
        public static final int FLAG_SHOWBASE = 38;
        /** flags のビット：立ち上がる。 */
        public static final int FLAG_REARING = 39;
        /** flags のビット：衝突あり。 */
        public static final int FLAG_HAS_COLLISION = 48;
        /** flags のビット：重力あり。 */
        public static final int FLAG_AFFECTED_BY_GRAVITY = 49;
        /** flags のビット：踊っている。 */
        public static final int FLAG_DANCING = 51;
        /** flags のビット：泳ぎ。 */
        public static final int FLAG_SWIMMING = 57;
        /** flags のビット：食べている。 */
        public static final int FLAG_EATING = 63;
        /** flags 拡張（key 92）のビット：くしゃみ。 */
        public static final int FLAG2_SNEEZING = 0;
        /** flags 拡張のビット：転がる。 */
        public static final int FLAG2_ROLLING = 2;
        /** flags 拡張のビット：眠っている。 */
        public static final int FLAG2_SLEEPING = 11;
        /** flags 拡張のビット：嗅いでいる。 */
        public static final int FLAG2_SNIFFING = 40;
        /** flags 拡張のビット：掘っている。 */
        public static final int FLAG2_DIGGING = 41;
        /** flags 拡張のビット：匂いを嗅いでいる。 */
        public static final int FLAG2_SCENTING = 45;
        /** flags 拡張のビット：起き上がる。 */
        public static final int FLAG2_RISING = 46;
        /** flags 拡張のビット：嬉しい。 */
        public static final int FLAG2_FEELING_HAPPY = 47;
        /** flags 拡張のビット：探している。 */
        public static final int FLAG2_SEARCHING = 48;
        /** 姿勢（Java index 6）：寝ている。 */
        public static final int POSE_SLEEPING = 5;

        private final List<Entry> entries = new ArrayList<>();

        /** byte エントリを追加する。 */
        public void addByte(int key, int value) {
            entries.add(new Entry(key, 0, value));
        }

        /** short エントリを追加する。 */
        public void addShort(int key, int value) {
            entries.add(new Entry(key, 1, value));
        }

        /** int エントリを追加する。 */
        public void addInt(int key, int value) {
            entries.add(new Entry(key, 2, value));
        }

        /** float エントリを追加する。 */
        public void addFloat(int key, float value) {
            entries.add(new Entry(key, 3, value));
        }

        /** string エントリを追加する。 */
        public void addString(int key, String value) {
            entries.add(new Entry(key, 4, value));
        }

        /** vec3i エントリを追加する。 */
        public void addVec3i(int key, int x, int y, int z) {
            entries.add(new Entry(key, 6, new int[]{x, y, z}));
        }

        /** long エントリを追加する。 */
        public void addLong(int key, long value) {
            entries.add(new Entry(key, 7, value));
        }

        /** vec3f エントリを追加する。 */
        public void addVec3f(int key, float x, float y, float z) {
            entries.add(new Entry(key, 8, new float[]{x, y, z}));
        }

        /** エントリ一覧（読み取り専用）。 */
        public List<Entry> entries() {
            return List.copyOf(entries);
        }

        /** 指定キーのエントリを探す（無ければ null）。 */
        public Entry find(int key) {
            for (Entry entry : entries) {
                if (entry.key() == key) {
                    return entry;
                }
            }
            return null;
        }

        /** MetadataDictionary として符号化する。 */
        public byte[] bytes() {
            BedrockVarInt.ByteWriter out = new BedrockVarInt.ByteWriter();
            BedrockVarInt.writeUnsignedVarInt(out, entries.size());
            for (Entry entry : entries) {
                BedrockVarInt.writeUnsignedVarInt(out, entry.key());
                BedrockVarInt.writeUnsignedVarInt(out, entry.type());
                switch (entry.type()) {
                    case 0 -> out.writeByte((int) entry.value());
                    case 1 -> out.writeLittleShort((int) entry.value());
                    case 2 -> BedrockVarInt.writeZigzag32(out, (int) entry.value());
                    case 3 -> out.writeLittleFloat((float) entry.value());
                    case 4 -> BedrockVarInt.writeString(out, (String) entry.value());
                    case 6 -> {
                        int[] v = (int[]) entry.value();
                        BedrockVarInt.writeZigzag32(out, v[0]);
                        BedrockVarInt.writeUnsignedVarInt(out, v[1]);
                        BedrockVarInt.writeZigzag32(out, v[2]);
                    }
                    case 7 -> BedrockVarInt.writeZigzag64(out, (long) entry.value());
                    case 8 -> {
                        float[] v = (float[]) entry.value();
                        out.writeLittleFloat(v[0]);
                        out.writeLittleFloat(v[1]);
                        out.writeLittleFloat(v[2]);
                    }
                    default -> throw new IllegalStateException(
                            "未知の Bedrock メタデータ型です: " + entry.type());
                }
            }
            return out.bytes();
        }
    }

    // ---- Java の Entity Metadata の読み取り ----

    /** Java の Entity Metadata パケット 1 個を「写せる形」にしたもの。 */
    public record JavaMetadata(int entityId,
            int flags, Integer air, String customName, Boolean nameVisible,
            Boolean silent, Boolean noGravity, Integer pose,
            Byte handStates, Float health, Integer potionColor, Boolean potionAmbient,
            Integer arrowsStuck, Integer absorption, Byte mobFlags,
            boolean baby, Integer variant, Integer profession, Integer color,
            Byte entityFlags, boolean saddled, boolean charged, boolean ignited,
            Integer creeperState, Integer tntFuse, Integer slimeSize,
            Integer shulkerPeek, Integer witherInvulnerable, Integer goatHornCount,
            Integer snifferState, Integer pufferfishState, Float areaEffectCloudRadius,
            Integer minecartDisplayBlock, Integer minecartDisplayOffset,
            Boolean minecartHasDisplay, Integer endermanBlockState,
            JavaSlot itemSlot, Integer xpValue, String paintingVariant,
            boolean arrowCritical, boolean spiderClimbing, boolean blazeOnFire,
            boolean ghastAttacking, boolean allayDancing, Integer strength,
            Integer horseType, Long bedPosition, Float playerExtraHearts,
            Integer playerScore, Byte playerSkinParts, Byte playerMainHand,
            boolean complete) {
    }

    /** holder 形式（VarInt＋任意の NBT）の読み取り結果。 */
    private record Holder(int id, Nbt.Tag inline) {
    }

    /** メタデータ解析の途中の入れ物。 */
    private static final class MetadataBuilder {
        int flags = -1;
        Integer air;
        String customName;
        Boolean nameVisible;
        Boolean silent;
        Boolean noGravity;
        Integer pose;
        Byte handStates;
        Float health;
        Integer potionColor;
        Boolean potionAmbient;
        Integer arrowsStuck;
        Integer absorption;
        Byte mobFlags;
        boolean baby;
        Integer variant;
        Integer profession;
        Integer color;
        Byte entityFlags;
        boolean saddled;
        boolean charged;
        boolean ignited;
        Integer creeperState;
        Integer tntFuse;
        Integer slimeSize;
        Integer shulkerPeek;
        Integer witherInvulnerable;
        Integer goatHornCount;
        Integer snifferState;
        Integer pufferfishState;
        Float areaEffectCloudRadius;
        Integer minecartDisplayBlock;
        Integer minecartDisplayOffset;
        Boolean minecartHasDisplay;
        Integer endermanBlockState;
        JavaSlot itemSlot;
        Integer xpValue;
        String paintingVariant;
        boolean arrowCritical;
        boolean spiderClimbing;
        boolean blazeOnFire;
        boolean ghastAttacking;
        boolean allayDancing;
        Integer strength;
        Integer horseType;
        Long bedPosition;
        Float playerExtraHearts;
        Integer playerScore;
        Byte playerSkinParts;
        Byte playerMainHand;
        boolean complete = true;

        JavaMetadata build(int entityId) {
            return new JavaMetadata(entityId, flags, air, customName, nameVisible,
                    silent, noGravity, pose, handStates, health, potionColor,
                    potionAmbient, arrowsStuck, absorption, mobFlags, baby, variant,
                    profession, color, entityFlags, saddled, charged, ignited,
                    creeperState, tntFuse, slimeSize, shulkerPeek, witherInvulnerable,
                    goatHornCount, snifferState, pufferfishState, areaEffectCloudRadius,
                    minecartDisplayBlock, minecartDisplayOffset, minecartHasDisplay,
                    endermanBlockState, itemSlot, xpValue, paintingVariant,
                    arrowCritical, spiderClimbing, blazeOnFire, ghastAttacking,
                    allayDancing, strength, horseType, bedPosition, playerExtraHearts,
                    playerScore, playerSkinParts, playerMainHand, complete);
        }
    }

    /**
     * Entity Metadata パケットを読む。読めないエントリ（未知の型・766 の
     * 成分付きスロット・パーティクルの形違い等）で止まった場合は、そこまでの
     * 内容を返し {@link JavaMetadata#complete()} を false にする。
     */
    public static JavaMetadata parseMetadata(byte[] payload, int serverProtocol, int type765) {
        PacketIO.Reader reader = new PacketIO.Reader(payload);
        int entityId = reader.readVarInt();
        MetadataBuilder builder = new MetadataBuilder();
        try {
            while (true) {
                int index = reader.readUnsignedByte();
                if (index == 0xFF) {
                    break;
                }
                int type = reader.readVarInt();
                Object value = readMetadataValue(reader, type, serverProtocol);
                extractMetadata(builder, type765, index, type, value);
            }
        } catch (IllegalArgumentException e) {
            builder.complete = false;
        }
        return builder.build(entityId);
    }

    /** メタデータの値 1 個を読む（型番号は wiki.vg の Entity Metadata による）。 */
    private static Object readMetadataValue(PacketIO.Reader reader, int type, int protocol) {
        if (protocol == RegistryRemapper.PROTOCOL_756
                || protocol == RegistryRemapper.PROTOCOL_757) {
            // 756（1.17.1）／757（1.18）のメタデータ型は 759 と同一で、763 以降とは番号が違う
            // （minecraft-data mc-1.17.1／mc-1.18／mc-1.19-protocol.json の entityMetadataItem で確認。
            // 763 で VarLong 型が 2 番に挿入され、後ろが 1 つずつずれた）。
            // 0 i8、1 varint、2 f32、3 string、4 chat（JSON 文字列）、
            // 5 opt chat（JSON 文字列）、6 slot、7 bool、8 rotation（3×f32）、
            // 9 position、10 opt position、11 direction（varint）、12 opt UUID、
            // 13 opt block id（varint、0＝無し）、14 NBT、15 particle、
            // 16 villager data（3×varint）、17 opt varint、18 pose（varint）。
            // 19 以上は 1.19 で追加された型（cat／frog／wolf variant 等）のため
            // 756・757 には無く、未知として扱う。なお 759 も同じ番号付けだが、
            // 既存の 759 は既定の switch（763 以降の番号）で読んでおり、
            // 型 2／3／5／14〜18 がずれて読まれる潜在問題がある。759 は
            // ここでは触らない（判断待ち）。
            // 型 14（NBT）だけ 756 と 757 で読み方が違う：実バニラの writeNbt は
            // 名前付き（型＋空名＋ペイロード）のため 756 は名前付きで読む。
            // 757 は既存の匿名読みのまま（判断待ちのため触らない）。
            return switch (type) {
                case 0 -> (byte) reader.readByte();
                case 1 -> reader.readVarInt();
                case 2 -> reader.readFloat();
                case 3 -> reader.readString();
                case 4 -> {
                    String json = reader.readString();
                    String text = BedrockLogin.extractString(json, "text");
                    yield text == null ? json : text;
                }
                case 5 -> {
                    boolean present = reader.readBoolean();
                    if (!present) {
                        yield null;
                    }
                    String json = reader.readString();
                    String text = BedrockLogin.extractString(json, "text");
                    yield text == null ? json : text;
                }
                case 6 -> readSlot(reader, protocol);
                case 7 -> reader.readBoolean();
                case 8 -> new float[]{reader.readFloat(), reader.readFloat(), reader.readFloat()};
                case 9 -> reader.readPositionRaw();
                case 10 -> {
                    boolean present = reader.readBoolean();
                    yield present ? reader.readPositionRaw() : null;
                }
                case 11 -> reader.readVarInt();
                case 12 -> {
                    boolean present = reader.readBoolean();
                    yield present ? reader.readBytes(16) : null;
                }
                case 13 -> {
                    int state = reader.readVarInt();
                    yield state == 0 ? null : state;
                }
                case 14 -> {
                    if (protocol == RegistryRemapper.PROTOCOL_756) {
                        reader.readNamedNbt();
                    } else {
                        reader.readAnonymousNbt();
                    }
                    yield null;
                }
                case 15 -> {
                    skipParticle(reader, protocol);
                    yield null;
                }
                case 16 -> new int[]{reader.readVarInt(), reader.readVarInt(),
                        reader.readVarInt()};
                case 17 -> {
                    boolean present = reader.readBoolean();
                    yield present ? reader.readVarInt() : null;
                }
                case 18 -> reader.readVarInt();
                default -> throw new IllegalArgumentException(
                        "756／757 の未知のメタデータ型です: " + type);
            };
        }
        // 775 は 774 とペイロード変更 0 件のため型番号も 774 と同じで、この 770〜773 専用の
        // ずれ分岐には含めない（774 と同じく下の既定の switch で読む）。
        if ((protocol == RegistryRemapper.PROTOCOL_770
                || protocol == RegistryRemapper.PROTOCOL_771
                || protocol == RegistryRemapper.PROTOCOL_772
                || protocol == RegistryRemapper.PROTOCOL_773) && type >= 23 && type <= 34) {
            // 770／771／772／773 は型番号がずれている（769 の 23 は wolf_variant（Holder）だが
            // 770／771／772／773 の 23 は cow_variant（varint）。769 の番号のまま読むと、
            // 値 0 のときに存在しない NBT を読んで後続が壊れる。
            // 770／771／772／773：23 cow_variant、24 wolf_variant、25 wolf_sound_variant、
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
        return switch (type) {
            case 0 -> (byte) reader.readByte();
            case 1 -> reader.readVarInt();
            case 2 -> reader.readVarLong();
            case 3 -> reader.readFloat();
            case 4 -> reader.readString();
            case 5 -> readComponentText(reader, protocol);
            case 6 -> {
                boolean present = reader.readBoolean();
                yield present ? readComponentText(reader, protocol) : null;
            }
            case 7 -> readSlot(reader, protocol);
            case 8 -> reader.readBoolean();
            case 9 -> new float[]{reader.readFloat(), reader.readFloat(), reader.readFloat()};
            case 10 -> reader.readPositionRaw();
            case 11 -> {
                boolean present = reader.readBoolean();
                yield present ? reader.readPositionRaw() : null;
            }
            case 12 -> reader.readVarInt();
            case 13 -> {
                boolean present = reader.readBoolean();
                yield present ? reader.readBytes(16) : null;
            }
            case 14 -> reader.readVarInt();
            case 15 -> {
                int state = reader.readVarInt();
                yield state == 0 ? null : state;
            }
            case 16 -> {
                if (protocol == RegistryRemapper.PROTOCOL_763) {
                    reader.readNamedNbt();
                } else {
                    reader.readAnonymousNbt();
                }
                yield null;
            }
            case 17 -> {
                skipParticle(reader, protocol);
                yield null;
            }
            case 18 -> {
                int count = reader.readVarInt();
                if (count < 0 || count > 64) {
                    throw new IllegalArgumentException("パーティクル一覧の件数が異常です。");
                }
                for (int i = 0; i < count; i++) {
                    skipParticle(reader, protocol);
                }
                yield null;
            }
            case 19 -> new int[]{reader.readVarInt(), reader.readVarInt(), reader.readVarInt()};
            case 20, 21, 22, 24, 27 -> reader.readVarInt();
            case 23, 26 -> {
                int holder = reader.readVarInt();
                Nbt.Tag inline = null;
                if (holder == 0) {
                    inline = protocol == RegistryRemapper.PROTOCOL_763
                            ? reader.readNamedNbt().tag() : reader.readAnonymousNbt().tag();
                }
                yield new Holder(holder, inline);
            }
            case 25 -> {
                boolean present = reader.readBoolean();
                if (!present) {
                    yield null;
                }
                reader.readString();
                yield reader.readPositionRaw();
            }
            case 28 -> new float[]{reader.readFloat(), reader.readFloat(), reader.readFloat()};
            case 29 -> new float[]{reader.readFloat(), reader.readFloat(),
                    reader.readFloat(), reader.readFloat()};
            default -> throw new IllegalArgumentException("未知のメタデータ型です: " + type);
        };
    }

    /** テキストコンポーネントを平文にする（765／766 は NBT、763／764 は JSON）。 */
    private static String readComponentText(PacketIO.Reader reader, int protocol) {
        if (protocol == RegistryRemapper.PROTOCOL_763
                || protocol == RegistryRemapper.PROTOCOL_764) {
            String json = reader.readString();
            String text = BedrockLogin.extractString(json, "text");
            return text == null ? json : text;
        }
        Nbt.ReadResult result = reader.readAnonymousNbt();
        return result.tag() == null ? "" : Nbt.plainText(result.tag());
    }

    /** パーティクル 1 個を読み飛ばす（765 の形に正規化してから判断する）。 */
    private static void skipParticle(PacketIO.Reader reader, int protocol) {
        int rawId = reader.readVarInt();
        if (protocol == RegistryRemapper.PROTOCOL_756 && (rawId == 2 || rawId == 3)) {
            // 756 の barrier（2）／light（3）は SimpleParticleType でデータ無し
            // （1.18 で両者を置き換えた block_marker はブロック状態を持つが、
            // 1.17 の barrier／light には無い。Minecraft Wiki「Particles」・
            // Forge「Particles」の SimpleParticleType の説明で確認）。
            // 正規化すると 765 の block_marker（3）になり、下の switch が
            // ブロック状態 varint を読んで後続を壊すため、ここで返す。
            return;
        }
        // 775 は 774 と同じ番号体系のため 770〜773 専用の生ID読み飛ばしには含めない。
        // 775 の新粒子 pause_mob_growth（50）／reset_mob_growth（51）は
        // mapParticle775To765 でデータ無しの 765 粒子（21）に写るため、下の正規化で読める。
        if (protocol == RegistryRemapper.PROTOCOL_770
                || protocol == RegistryRemapper.PROTOCOL_771
                || protocol == RegistryRemapper.PROTOCOL_772
                || protocol == RegistryRemapper.PROTOCOL_773) {
            // 770／771／772／773 にだけある粒子は 765 に相当が無いため、正規化ではデータが
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
        int particle765 = normalizeParticleTo765(rawId, protocol);
        switch (particle765) {
            case 2, 3, 27 -> reader.readVarInt(); // block, block_marker, falling_dust
            case 14 -> { // dust
                reader.readFloat();
                reader.readFloat();
                reader.readFloat();
                reader.readFloat();
            }
            case 15 -> { // dust_color_transition
                for (int i = 0; i < 7; i++) {
                    reader.readFloat();
                }
            }
            case 33 -> reader.readFloat(); // sculk_charge
            case 42 -> readSlot(reader, protocol); // item
            case 43 -> { // vibration（wiki.vg の 1.20.4 の形）
                String positionType = reader.readString();
                if ("minecraft:block".equals(positionType)) {
                    reader.readPositionRaw();
                } else if ("minecraft:entity".equals(positionType)) {
                    reader.readVarInt();
                    reader.readFloat();
                }
                reader.readVarInt(); // arrival ticks
            }
            case 96 -> reader.readVarInt(); // shriek
            default -> {
                // その他の粒子にデータは無い。
            }
        }
    }

    // ---- メタデータの振り分け（種別別 index の解釈） ----

    // Java 765 のエンティティ種別ID（抜粋。BedrockEntityTypeIds の表と同じ順序）。
    private static final int TYPE_ALLAY = 0;
    private static final int TYPE_AREA_EFFECT_CLOUD = 1;
    private static final int TYPE_ARMOR_STAND = 2;
    private static final int TYPE_ARROW = 3;
    private static final int TYPE_AXOLOTL = 4;
    private static final int TYPE_BEE = 6;
    private static final int TYPE_BLAZE = 7;
    private static final int TYPE_BOAT = 9;
    private static final int TYPE_CAT = 12;
    private static final int TYPE_CAVE_SPIDER = 13;
    private static final int TYPE_CHEST_BOAT = 14;
    private static final int TYPE_CREEPER = 20;
    private static final int TYPE_DROWNED = 24;
    private static final int TYPE_ENDERMAN = 30;
    private static final int TYPE_EXPERIENCE_ORB = 35;
    private static final int TYPE_FOX = 39;
    private static final int TYPE_FROG = 40;
    private static final int TYPE_GHAST = 42;
    private static final int TYPE_GIANT = 43;
    private static final int TYPE_GLOW_ITEM_FRAME = 44;
    private static final int TYPE_GOAT = 46;
    private static final int TYPE_HOGLIN = 48;
    private static final int TYPE_HORSE = 50;
    private static final int TYPE_HUSK = 51;
    private static final int TYPE_ITEM = 55;
    private static final int TYPE_ITEM_DISPLAY = 56;
    private static final int TYPE_ITEM_FRAME = 57;
    private static final int TYPE_LLAMA = 61;
    private static final int TYPE_MAGMA_CUBE = 63;
    private static final int TYPE_MINECART = 65;
    private static final int TYPE_MOOSHROOM = 66;
    private static final int TYPE_PAINTING = 69;
    private static final int TYPE_PANDA = 70;
    private static final int TYPE_PARROT = 71;
    private static final int TYPE_PIG = 73;
    private static final int TYPE_PIGLIN = 74;
    private static final int TYPE_PIGLIN_BRUTE = 75;
    private static final int TYPE_PUFFERFISH = 79;
    private static final int TYPE_RABBIT = 80;
    private static final int TYPE_SHEEP = 83;
    private static final int TYPE_SHULKER = 84;
    private static final int TYPE_SLIME = 89;
    private static final int TYPE_SNIFFER = 91;
    private static final int TYPE_SPIDER = 96;
    private static final int TYPE_STRIDER = 99;
    private static final int TYPE_TEXT_DISPLAY = 101;
    private static final int TYPE_TNT = 102;
    private static final int TYPE_TRADER_LLAMA = 104;
    private static final int TYPE_TRIDENT = 105;
    private static final int TYPE_TROPICAL_FISH = 106;
    private static final int TYPE_VILLAGER = 109;
    private static final int TYPE_WITHER = 115;
    private static final int TYPE_WOLF = 118;
    private static final int TYPE_ZOGLIN = 119;
    private static final int TYPE_ZOMBIE = 120;
    private static final int TYPE_ZOMBIE_VILLAGER = 122;
    private static final int TYPE_ZOMBIFIED_PIGLIN = 123;
    private static final int TYPE_PLAYER = 124;
    private static final int TYPE_CHEST_MINECART = 15;
    private static final int TYPE_HOPPER_MINECART = 49;
    private static final int TYPE_TNT_MINECART = 103;
    private static final int TYPE_FURNACE_MINECART = 41;
    private static final int TYPE_SPAWNER_MINECART = 94;
    private static final int TYPE_COMMAND_BLOCK_MINECART = 18;
    private static final int TYPE_DONKEY = 22;
    private static final int TYPE_MULE = 67;
    private static final int TYPE_SKELETON_HORSE = 88;
    private static final int TYPE_ZOMBIE_HORSE = 121;

    /** 赤ちゃんフラグを持つ種別（index 16 が boolean の「赤ちゃん」）。 */
    private static boolean isBabyType(int type765) {
        return switch (type765) {
            case TYPE_ZOMBIE, TYPE_HUSK, TYPE_DROWNED, TYPE_ZOMBIE_VILLAGER,
                    TYPE_ZOMBIFIED_PIGLIN, TYPE_PIGLIN, TYPE_PIGLIN_BRUTE,
                    TYPE_ZOGLIN, TYPE_HOGLIN, TYPE_GIANT -> true;
            default -> false;
        };
    }

    /** index 16 が馬系の flags・種別・見た目の種別。 */
    private static boolean isHorseType(int type765) {
        return switch (type765) {
            case TYPE_HORSE, TYPE_DONKEY, TYPE_MULE, TYPE_SKELETON_HORSE,
                    TYPE_ZOMBIE_HORSE -> true;
            default -> false;
        };
    }

    /** index 16〜18 がトロッコの表示ブロックの種別。 */
    private static boolean isMinecartType(int type765) {
        return switch (type765) {
            case TYPE_MINECART, TYPE_CHEST_MINECART, TYPE_HOPPER_MINECART,
                    TYPE_TNT_MINECART, TYPE_FURNACE_MINECART, TYPE_SPAWNER_MINECART,
                    TYPE_COMMAND_BLOCK_MINECART -> true;
            default -> false;
        };
    }

    /** メタデータの 1 エントリを、種別に応じて builder へ振り分ける。 */
    private static void extractMetadata(MetadataBuilder b, int type765,
            int index, int type, Object value) {
        // ---- 共通（全エンティティ） ----
        switch (index) {
            case 0 -> {
                if (value instanceof Byte flags) {
                    b.flags = flags & 0xFF;
                }
                return;
            }
            case 1 -> {
                if (value instanceof Integer air) {
                    b.air = air;
                }
                return;
            }
            case 2 -> {
                if (value instanceof String name && !name.isEmpty()) {
                    b.customName = name;
                }
                return;
            }
            case 3 -> {
                if (value instanceof Boolean visible) {
                    b.nameVisible = visible;
                }
                return;
            }
            case 4 -> {
                if (value instanceof Boolean silent) {
                    b.silent = silent;
                }
                return;
            }
            case 5 -> {
                if (value instanceof Boolean noGravity) {
                    b.noGravity = noGravity;
                }
                return;
            }
            case 6 -> {
                if (value instanceof Integer pose) {
                    b.pose = pose;
                }
                return;
            }
            case 8 -> {
                // index 8 は種別で意味が違う。
                if (value instanceof Byte handStates
                        && type765 != TYPE_ARROW && type765 != TYPE_TRIDENT) {
                    b.handStates = handStates;
                } else if (value instanceof JavaSlot slot
                        && (type765 == TYPE_ITEM || type765 == TYPE_ITEM_FRAME
                        || type765 == TYPE_GLOW_ITEM_FRAME
                        || type765 == TYPE_ITEM_DISPLAY)) {
                    b.itemSlot = slot;
                } else if (value instanceof Integer xp
                        && type765 == TYPE_EXPERIENCE_ORB) {
                    b.xpValue = xp;
                } else if (value instanceof Float radius
                        && type765 == TYPE_AREA_EFFECT_CLOUD) {
                    b.areaEffectCloudRadius = radius;
                } else if (value instanceof Byte arrowFlags && type765 == TYPE_ARROW) {
                    b.arrowCritical = (arrowFlags & 0x01) != 0;
                } else if (value instanceof Holder holder && type765 == TYPE_PAINTING) {
                    b.paintingVariant = paintingVariantFromHolder(holder);
                }
                return;
            }
            case 9 -> {
                if (value instanceof Float health) {
                    b.health = health;
                } else if (value instanceof Integer color
                        && type765 == TYPE_AREA_EFFECT_CLOUD) {
                    b.potionColor = color;
                }
                return;
            }
            case 10 -> {
                if (value instanceof Integer color) {
                    b.potionColor = color;
                }
                return;
            }
            case 11 -> {
                if (value instanceof Boolean ambient) {
                    b.potionAmbient = ambient;
                }
                return;
            }
            case 12 -> {
                if (value instanceof Integer arrows) {
                    b.arrowsStuck = arrows;
                }
                return;
            }
            case 13 -> {
                if (value instanceof Integer absorption) {
                    b.absorption = absorption;
                }
                return;
            }
            case 14 -> {
                if (value instanceof Long bedPosition) {
                    b.bedPosition = bedPosition;
                }
                return;
            }
            case 15 -> {
                if (value instanceof Byte mobFlags) {
                    b.mobFlags = mobFlags;
                }
                return;
            }
            default -> {
                // index 16 以降は下の種別別で扱う。
            }
        }
        // ---- 種別別（index 16 以降） ----
        if (index < 16) {
            return;
        }
        switch (type765) {
            case TYPE_ALLAY -> {
                if (index == 16 && value instanceof Boolean dancing) {
                    b.allayDancing = dancing;
                }
            }
            case TYPE_ARMOR_STAND -> {
                if (index == 16 && value instanceof Byte armorFlags) {
                    b.entityFlags = armorFlags;
                }
            }
            case TYPE_AXOLOTL, TYPE_FROG, TYPE_PARROT, TYPE_TROPICAL_FISH -> {
                if (index == 16 && value instanceof Integer variant) {
                    b.variant = variant;
                }
            }
            case TYPE_BEE, TYPE_FOX, TYPE_PANDA, TYPE_WOLF, TYPE_SHEEP -> {
                if (index == 16 && value instanceof Byte entityFlags) {
                    b.entityFlags = entityFlags;
                    if (type765 == TYPE_SHEEP) {
                        b.color = entityFlags & 0x0F;
                    }
                }
                if (index == 20 && type765 == TYPE_WOLF && value instanceof Integer collar) {
                    b.color = collar;
                }
            }
            case TYPE_BLAZE -> {
                if (index == 16 && value instanceof Byte blazeFlags) {
                    b.blazeOnFire = (blazeFlags & 0x01) != 0;
                }
            }
            case TYPE_BOAT, TYPE_CHEST_BOAT -> {
                if (index == 16 && value instanceof Integer boatType) {
                    b.variant = boatType;
                }
            }
            case TYPE_CAT -> {
                if (index == 16 && value instanceof Integer catVariant) {
                    // Java 1 black → Bedrock 10 black、Java 10 all_black → 10。
                    b.variant = catVariant == 1 || catVariant == 10 ? 10 : catVariant;
                }
                if (index == 19 && value instanceof Integer collar) {
                    b.color = collar;
                }
            }
            case TYPE_CAVE_SPIDER, TYPE_SPIDER -> {
                if (index == 16 && value instanceof Byte spiderFlags) {
                    b.spiderClimbing = (spiderFlags & 0x01) != 0;
                }
            }
            case TYPE_CREEPER -> {
                if (index == 16 && value instanceof Integer state) {
                    b.creeperState = state;
                }
                if (index == 17 && value instanceof Boolean charged) {
                    b.charged = charged;
                }
                if (index == 18 && value instanceof Boolean ignited) {
                    b.ignited = ignited;
                }
            }
            case TYPE_ENDERMAN -> {
                if (index == 16 && value instanceof Integer blockState) {
                    b.endermanBlockState = blockState;
                }
            }
            case TYPE_GHAST -> {
                if (index == 16 && value instanceof Boolean attacking) {
                    b.ghastAttacking = attacking;
                }
            }
            case TYPE_GOAT -> {
                if (index == 17 && value instanceof Boolean leftHorn) {
                    b.goatHornCount = (b.goatHornCount == null ? 0 : b.goatHornCount)
                            + (leftHorn ? 1 : 0);
                }
                if (index == 18 && value instanceof Boolean rightHorn) {
                    b.goatHornCount = (b.goatHornCount == null ? 0 : b.goatHornCount)
                            + (rightHorn ? 1 : 0);
                }
            }
            case TYPE_LLAMA, TYPE_TRADER_LLAMA -> {
                if (index == 16 && value instanceof Integer strength) {
                    b.strength = strength;
                }
                if (index == 17 && value instanceof Integer carpet) {
                    b.color = carpet;
                }
                if (index == 18 && value instanceof Integer variant) {
                    b.variant = variant;
                }
            }
            case TYPE_MAGMA_CUBE, TYPE_SLIME -> {
                if (index == 16 && value instanceof Integer size) {
                    b.slimeSize = size;
                }
            }
            case TYPE_MOOSHROOM -> {
                if (index == 16 && value instanceof String variant) {
                    b.variant = "brown".equals(variant) ? 1 : 0;
                }
            }
            case TYPE_PIG -> {
                if (index == 16 && value instanceof Boolean saddled) {
                    b.saddled = saddled;
                }
            }
            case TYPE_PUFFERFISH -> {
                if (index == 16 && value instanceof Integer state) {
                    b.pufferfishState = state;
                }
            }
            case TYPE_RABBIT -> {
                if (index == 16 && value instanceof Integer rabbitType) {
                    b.variant = rabbitType == 6 ? 0 : rabbitType;
                }
            }
            case TYPE_SHULKER -> {
                if (index == 18 && value instanceof Byte peek) {
                    b.shulkerPeek = peek & 0xFF;
                }
            }
            case TYPE_SNIFFER -> {
                if (index == 16 && value instanceof Integer state) {
                    b.snifferState = state;
                }
            }
            case TYPE_STRIDER -> {
                if (index == 17 && value instanceof Boolean saddled) {
                    b.saddled = saddled;
                }
            }
            case TYPE_TEXT_DISPLAY -> {
                if (index == 8 && value instanceof String text && !text.isEmpty()) {
                    b.customName = text;
                    b.nameVisible = true;
                }
            }
            case TYPE_TNT -> {
                if (index == 16 && value instanceof Integer fuse) {
                    b.tntFuse = fuse;
                }
            }
            case TYPE_VILLAGER -> {
                if (index == 16 && value instanceof int[] data && data.length == 3) {
                    b.profession = data[1];
                }
            }
            case TYPE_WITHER -> {
                if (index == 19 && value instanceof Integer invulnerable) {
                    b.witherInvulnerable = invulnerable;
                }
            }
            case TYPE_PLAYER -> {
                if (index == 16 && value instanceof Float hearts) {
                    b.playerExtraHearts = hearts;
                }
                if (index == 17 && value instanceof Integer score) {
                    b.playerScore = score;
                }
                if (index == 18 && value instanceof Byte skinParts) {
                    b.playerSkinParts = skinParts;
                }
                if (index == 19 && value instanceof Byte mainHand) {
                    b.playerMainHand = mainHand;
                }
            }
            default -> {
                // 何もしない。
            }
        }
        // 馬系・赤ちゃん・トロッコは上の switch とは別に扱う（複数種別で共通）。
        if (isHorseType(type765)) {
            if (index == 16 && value instanceof Byte horseFlags) {
                b.entityFlags = horseFlags;
            }
            if (index == 17 && value instanceof Integer horseType) {
                b.horseType = horseType;
            }
            if (index == 18 && value instanceof Integer variant) {
                b.variant = variant;
            }
        }
        if (isBabyType(type765)) {
            if (index == 16 && value instanceof Boolean baby) {
                b.baby = baby;
            }
        }
        if (isMinecartType(type765)) {
            if (index == 16 && value instanceof Integer blockState) {
                b.minecartDisplayBlock = blockState;
            }
            if (index == 17 && value instanceof Integer offset) {
                b.minecartDisplayOffset = offset;
            }
            if (index == 18 && value instanceof Boolean hasDisplay) {
                b.minecartHasDisplay = hasDisplay;
            }
        }
    }

    // ---- 絵画 ----

    /** Java 765 の絵画バリエーションのレジストリ順（ViaVersion mapping-1.20.3.json）。 */
    private static final String[] PAINTING_NAMES_765 = {
            "kebab", "aztec", "alban", "aztec2", "bomb", "plant", "wasteland",
            "pool", "courbet", "sea", "sunset", "creebet", "wanderer", "graham",
            "match", "bust", "stage", "void", "skull_and_roses", "wither",
            "fighters", "pointer", "pigscene", "burning_skull", "skeleton",
            "earth", "wind", "water", "fire", "donkey_kong"
    };

    /** Bedrock の絵画タイトル（AddPainting の title。Motive 名）。 */
    private static final String[] PAINTING_TITLES_765 = {
            "Kebab", "Aztec", "Alban", "Aztec2", "Bomb", "Plant", "Wasteland",
            "Pool", "Courbet", "Sea", "Sunset", "Creebet", "Wanderer", "Graham",
            "Match", "Bust", "Stage", "Void", "SkullAndRoses", "Wither",
            "Fighters", "Pointer", "Pigscene", "BurningSkull", "Skeleton",
            "Earth", "Wind", "Water", "Fire", "DonkeyKong"
    };

    /** 絵画バリエーションのレジストリIDから Java の名前を引く（範囲外は kebab）。 */
    public static String paintingName(int registryId) {
        if (registryId < 0 || registryId >= PAINTING_NAMES_765.length) {
            return "kebab";
        }
        return PAINTING_NAMES_765[registryId];
    }

    /**
     * 絵画バリエーションのレジストリIDから Bedrock のタイトルを引く。
     * earth／wind／water／fire（25〜28）は Bedrock 1.20.50 に無いため
     * Kebab にする。範囲外も Kebab。
     */
    public static String paintingTitle(int registryId) {
        if (registryId < 0 || registryId >= PAINTING_TITLES_765.length) {
            return "Kebab";
        }
        if (registryId >= 25 && registryId <= 28) {
            return "Kebab";
        }
        return PAINTING_TITLES_765[registryId];
    }

    /** 絵画バリエーションの Java 名から Bedrock のタイトルを引く。 */
    public static String paintingTitle(String javaName) {
        if (javaName == null) {
            return "Kebab";
        }
        String name = javaName.startsWith("minecraft:")
                ? javaName.substring("minecraft:".length()) : javaName;
        if ("fight".equals(name)) {
            return "Fighters";
        }
        for (int i = 0; i < PAINTING_NAMES_765.length; i++) {
            if (PAINTING_NAMES_765[i].equals(name)) {
                return paintingTitle(i);
            }
        }
        return "Kebab";
    }

    /**
     * 絵画の holder（VarInt＋任意の NBT）から Java 名を出す。呼び出し側は
     * {@link #paintingTitle(String)} に通して Bedrock のタイトルにする。
     * inline NBT の asset_id（例：minecraft:skull_and_roses）があればそれを、
     * 無ければレジストリID（1 始まり）から名前を引く。
     */
    private static String paintingVariantFromHolder(Holder holder) {
        if (holder.inline() instanceof Nbt.Tag.CompoundTag compound) {
            String assetId = compound.getString("asset_id").orElse(null);
            if (assetId != null) {
                return assetId;
            }
        }
        if (holder.id() > 0) {
            return paintingName(holder.id() - 1);
        }
        return null;
    }

    // ---- JavaMetadata → BedrockEntityData ----

    /** flags のビットを立てる。 */
    private static long bit(int position) {
        return 1L << position;
    }

    /** Spawn 直後（メタデータがまだ無い）の既定の Entity Data。 */
    public static BedrockEntityData defaultData(int type765) {
        BedrockEntityData data = new BedrockEntityData();
        long flags = bit(BedrockEntityData.FLAG_HAS_COLLISION)
                | bit(BedrockEntityData.FLAG_AFFECTED_BY_GRAVITY);
        if (type765 == TYPE_ARMOR_STAND) {
            flags |= bit(BedrockEntityData.FLAG_SHOWBASE);
        }
        data.addLong(BedrockEntityData.KEY_FLAGS, flags);
        return data;
    }

    /** Java のメタデータを Bedrock の Entity Data に直す。 */
    public static BedrockEntityData toBedrockData(JavaMetadata meta, int type765) {
        BedrockEntityData data = new BedrockEntityData();
        long flags = 0;
        int javaFlags = meta.flags();
        if (javaFlags >= 0) {
            if ((javaFlags & 0x01) != 0) {
                flags |= bit(BedrockEntityData.FLAG_ONFIRE);
            }
            if ((javaFlags & 0x02) != 0) {
                flags |= bit(BedrockEntityData.FLAG_SNEAKING);
            }
            if ((javaFlags & 0x04) != 0) {
                flags |= bit(BedrockEntityData.FLAG_SPRINTING);
            }
            if ((javaFlags & 0x08) != 0) {
                flags |= bit(BedrockEntityData.FLAG_SWIMMING);
            }
            if ((javaFlags & 0x10) != 0) {
                flags |= bit(BedrockEntityData.FLAG_INVISIBLE);
            }
            if ((javaFlags & 0x40) != 0) {
                flags |= bit(BedrockEntityData.FLAG_GLIDING);
            }
        }
        flags |= bit(BedrockEntityData.FLAG_HAS_COLLISION);
        boolean noGravity = meta.noGravity() != null && meta.noGravity();
        if (!noGravity) {
            flags |= bit(BedrockEntityData.FLAG_AFFECTED_BY_GRAVITY);
        }
        if (meta.customName() != null && !meta.customName().isEmpty()) {
            flags |= bit(BedrockEntityData.FLAG_CAN_SHOW_NAMETAG);
        }
        if (Boolean.TRUE.equals(meta.nameVisible())) {
            flags |= bit(BedrockEntityData.FLAG_ALWAYS_SHOW_NAMETAG);
        }
        if (Boolean.TRUE.equals(meta.silent())) {
            flags |= bit(BedrockEntityData.FLAG_SILENT);
        }
        if (meta.baby()) {
            flags |= bit(BedrockEntityData.FLAG_BABY);
        }
        if (meta.saddled()) {
            flags |= bit(BedrockEntityData.FLAG_SADDLED);
        }
        if (meta.charged()) {
            flags |= bit(BedrockEntityData.FLAG_CHARGED);
        }
        if (meta.ignited()) {
            flags |= bit(BedrockEntityData.FLAG_IGNITED);
        }
        if (meta.arrowCritical()) {
            flags |= bit(BedrockEntityData.FLAG_CRITICAL);
        }
        if (meta.spiderClimbing()) {
            flags |= bit(BedrockEntityData.FLAG_WALLCLIMBING);
        }
        if (meta.blazeOnFire()) {
            flags |= bit(BedrockEntityData.FLAG_ONFIRE);
        }
        if (meta.allayDancing()) {
            flags |= bit(BedrockEntityData.FLAG_DANCING);
        }
        boolean sitting = false;
        Byte entityFlags = meta.entityFlags();
        if (entityFlags != null) {
            int ef = entityFlags & 0xFF;
            switch (type765) {
                case TYPE_WOLF -> {
                    if ((ef & 0x01) != 0) {
                        flags |= bit(BedrockEntityData.FLAG_SITTING);
                        sitting = true;
                    }
                    if ((ef & 0x02) != 0) {
                        flags |= bit(BedrockEntityData.FLAG_ANGRY);
                    }
                    if ((ef & 0x04) != 0) {
                        flags |= bit(BedrockEntityData.FLAG_TAMED);
                    }
                }
                case TYPE_SHEEP -> {
                    if ((ef & 0x10) != 0) {
                        flags |= bit(BedrockEntityData.FLAG_SHEARED);
                    }
                }
                case TYPE_FOX -> {
                    if ((ef & 0x01) != 0) {
                        flags |= bit(BedrockEntityData.FLAG_SITTING);
                        sitting = true;
                    }
                    if ((ef & 0x08) != 0) {
                        flags |= bit(BedrockEntityData.FLAG_INTERESTED);
                    }
                }
                case TYPE_PANDA -> {
                    if ((ef & 0x08) != 0) {
                        flags |= bit(BedrockEntityData.FLAG_SITTING);
                        sitting = true;
                    }
                }
                case TYPE_BEE -> {
                    if ((ef & 0x02) != 0) {
                        flags |= bit(BedrockEntityData.FLAG_ANGRY);
                    }
                }
                case TYPE_HORSE, TYPE_DONKEY, TYPE_MULE, TYPE_SKELETON_HORSE,
                        TYPE_ZOMBIE_HORSE -> {
                    if ((ef & 0x02) != 0) {
                        flags |= bit(BedrockEntityData.FLAG_TAMED);
                    }
                    if ((ef & 0x04) != 0) {
                        flags |= bit(BedrockEntityData.FLAG_SADDLED);
                    }
                    if ((ef & 0x10) != 0) {
                        flags |= bit(BedrockEntityData.FLAG_EATING);
                    }
                    if ((ef & 0x20) != 0) {
                        flags |= bit(BedrockEntityData.FLAG_REARING);
                    }
                }
                case TYPE_ARMOR_STAND -> {
                    if ((ef & 0x08) == 0) {
                        flags |= bit(BedrockEntityData.FLAG_SHOWBASE);
                    }
                }
                default -> {
                    // 何もしない。
                }
            }
        } else if (type765 == TYPE_ARMOR_STAND) {
            flags |= bit(BedrockEntityData.FLAG_SHOWBASE);
        }
        long flags2 = 0;
        if (entityFlags != null) {
            int ef = entityFlags & 0xFF;
            switch (type765) {
                case TYPE_PANDA -> {
                    if ((ef & 0x02) != 0) {
                        flags2 |= bit(BedrockEntityData.FLAG2_SNEEZING);
                    }
                    if ((ef & 0x04) != 0) {
                        flags2 |= bit(BedrockEntityData.FLAG2_ROLLING);
                    }
                }
                case TYPE_FOX -> {
                    if ((ef & 0x20) != 0) {
                        flags2 |= bit(BedrockEntityData.FLAG2_SLEEPING);
                    }
                }
                default -> {
                    // 何もしない。
                }
            }
        }
        if (meta.snifferState() != null) {
            switch (meta.snifferState()) {
                case 1 -> flags2 |= bit(BedrockEntityData.FLAG2_FEELING_HAPPY);
                case 2 -> flags2 |= bit(BedrockEntityData.FLAG2_SCENTING);
                case 3 -> flags2 |= bit(BedrockEntityData.FLAG2_SNIFFING);
                case 4 -> flags2 |= bit(BedrockEntityData.FLAG2_SEARCHING);
                case 5 -> flags2 |= bit(BedrockEntityData.FLAG2_DIGGING);
                case 6 -> flags2 |= bit(BedrockEntityData.FLAG2_RISING);
                default -> {
                    // 何もしない。
                }
            }
        }
        if (meta.pose() != null && meta.pose() == BedrockEntityData.POSE_SLEEPING) {
            flags2 |= bit(BedrockEntityData.FLAG2_SLEEPING);
        }

        data.addLong(BedrockEntityData.KEY_FLAGS, flags);
        if (meta.health() != null) {
            data.addInt(BedrockEntityData.KEY_HEALTH, Math.max(0, Math.round(meta.health())));
        }
        if (meta.variant() != null) {
            data.addInt(BedrockEntityData.KEY_VARIANT, meta.variant());
        } else if (type765 == TYPE_VILLAGER && meta.profession() != null) {
            data.addInt(BedrockEntityData.KEY_VARIANT, meta.profession());
        }
        if (meta.color() != null) {
            data.addInt(BedrockEntityData.KEY_COLOR, meta.color());
        }
        if (meta.customName() != null && !meta.customName().isEmpty()) {
            data.addString(BedrockEntityData.KEY_NAMETAG, meta.customName());
        }
        if (meta.air() != null) {
            data.addInt(BedrockEntityData.KEY_AIR, meta.air());
        }
        if (meta.slimeSize() != null) {
            data.addFloat(BedrockEntityData.KEY_SCALE, meta.slimeSize() * 0.5f);
        } else if (type765 == TYPE_ARMOR_STAND && entityFlags != null
                && (entityFlags & 0x01) != 0) {
            data.addFloat(BedrockEntityData.KEY_SCALE, 0.5f);
        }
        if (Boolean.TRUE.equals(meta.nameVisible())) {
            data.addByte(BedrockEntityData.KEY_ALWAYS_SHOW_NAMETAG, 1);
        }
        if (sitting) {
            data.addFloat(BedrockEntityData.KEY_SITTING_AMOUNT, 1.0f);
        }
        if (flags2 != 0) {
            data.addLong(BedrockEntityData.KEY_FLAGS_EXTENDED, flags2);
        }
        if (meta.tntFuse() != null) {
            data.addInt(BedrockEntityData.KEY_FUSE_LENGTH, meta.tntFuse());
        }
        if (meta.shulkerPeek() != null) {
            data.addByte(BedrockEntityData.KEY_SHULKER_PEEK_ID, meta.shulkerPeek());
        }
        if (meta.endermanBlockState() != null) {
            data.addInt(BedrockEntityData.KEY_ENDERMAN_HELD_RUNTIME_ID,
                    BedrockBlockRuntimeIds.runtimeId(
                            BedrockChunkTranslator.normalizeBlockState(
                                    meta.endermanBlockState(), 765)));
        }
        if (meta.xpValue() != null) {
            data.addInt(BedrockEntityData.KEY_EXPERIENCE_VALUE, meta.xpValue());
        }
        if (meta.minecartDisplayBlock() != null) {
            data.addInt(BedrockEntityData.KEY_MINECART_DISPLAY_BLOCK,
                    BedrockBlockRuntimeIds.runtimeId(
                            BedrockChunkTranslator.normalizeBlockState(
                                    meta.minecartDisplayBlock(), 765)));
        }
        if (meta.minecartDisplayOffset() != null) {
            data.addInt(BedrockEntityData.KEY_MINECART_DISPLAY_OFFSET,
                    meta.minecartDisplayOffset());
        }
        if (meta.minecartHasDisplay() != null) {
            data.addByte(BedrockEntityData.KEY_MINECART_HAS_DISPLAY,
                    meta.minecartHasDisplay() ? 1 : 0);
        }
        if (meta.witherInvulnerable() != null) {
            data.addInt(BedrockEntityData.KEY_WITHER_INVULNERABLE_TICKS,
                    meta.witherInvulnerable());
        }
        if (meta.strength() != null) {
            data.addInt(BedrockEntityData.KEY_STRENGTH, meta.strength());
            data.addInt(BedrockEntityData.KEY_MAX_STRENGTH, 5);
        }
        if (meta.goatHornCount() != null) {
            data.addInt(BedrockEntityData.KEY_GOAT_HORN_COUNT, meta.goatHornCount());
        }
        if (meta.pufferfishState() != null) {
            data.addInt(BedrockEntityData.KEY_PUFFERFISH_SIZE, meta.pufferfishState());
        }
        if (meta.areaEffectCloudRadius() != null) {
            data.addFloat(BedrockEntityData.KEY_AREA_EFFECT_CLOUD_RADIUS,
                    meta.areaEffectCloudRadius());
        }
        if (meta.horseType() != null) {
            data.addInt(BedrockEntityData.KEY_HORSE_TYPE, meta.horseType());
        }
        if (meta.baby()) {
            data.addInt(BedrockEntityData.KEY_ENTITY_AGE, -1);
        }
        if (meta.potionColor() != null) {
            data.addInt(BedrockEntityData.KEY_POTION_COLOR, meta.potionColor());
        }
        if (meta.creeperState() != null && meta.creeperState() == 1) {
            data.addInt(BedrockEntityData.KEY_CREEPER_SWELL, 100);
            data.addByte(BedrockEntityData.KEY_CREEPER_SWELL_DIRECTION, 1);
        }
        if (type765 == TYPE_ARMOR_STAND) {
            data.addInt(BedrockEntityData.KEY_ARMOR_STAND_POSE_INDEX, 0);
        }
        if (meta.pose() != null && meta.pose() == BedrockEntityData.POSE_SLEEPING
                && meta.bedPosition() != null) {
            long packed = meta.bedPosition();
            int x = (int) (packed >> 38);
            int y = (int) (packed << 52 >> 52);
            int z = (int) (packed << 26 >> 38);
            data.addVec3i(BedrockEntityData.KEY_PLAYER_BED_POSITION, x, y, z);
        }
        return data;
    }
}
