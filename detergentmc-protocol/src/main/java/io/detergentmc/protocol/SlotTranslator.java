package io.detergentmc.protocol;

import java.util.Arrays;
import java.util.logging.Logger;

/**
 * 401（1.13.1）と 404（1.13.2）の間のスロット形式変換。
 *
 * <p>1.13.1 と 1.13.2 は protocol.json の平坦化比較で、パケットID表と
 * 全パケットのフィールド構成が完全一致し、違うのは共有型 {@code slot}
 * の定義だけであることを確認済み（tools/packet-ids-401.py と
 * docs/PROXY-1.13.1.md 参照）。そのため 401 の変換は「404 の変換経路を
 * そのまま使い、境界でスロットの符号化だけを付け替える」形にする。
 * このクラスはその付け替えだけを担当する。
 *
 * <ul>
 *   <li>401 のスロット（旧形式）：{@code itemId: i16}（-1 = 空）。
 *       空でなければ {@code itemCount: i8}＋{@code nbtData: optionalNbt}
 *       が続く。</li>
 *   <li>404 のスロット（現行形式）：{@code present: bool}。true なら
 *       {@code itemId: varint}＋{@code itemCount: i8}＋
 *       {@code nbtData: optionalNbt} が続く。</li>
 * </ul>
 *
 * <p>アイテムIDの数値そのものは 401・404 で同一（レジストリが一致）ため、
 * ここでは付け替えはしない。旧形式は i16 のため、404 側の値が
 * 32767 を超える場合は収まらないが、404 のアイテムIDは最大 789 で
 * あることがレジストリ表で保証される。万一範囲外の値が来たら、
 * 落とさず石（1）に置き換えて警告を出す（変換表の代替方針と同じ）。
 *
 * <p>393（1.13）も同じ旧形式スロットで、パケットIDも 401 と同一、
 * 粒子レジストリも 404 と一致するため、このクラスの機構をそのまま
 * 共用する（名前引きのプロトコルだけ 393 に差し替える。アイテムIDの
 * 数値は 393⇔404 で付け替えが要るが、それは RegistryRemapper の
 * 393 表が上流の変換で担当し、ここでは符号化だけを扱う）。
 *
 * <p>スロットを含むパケットだけを対象とし、それ以外は素通しする。
 * 対象はクライアント行きでは Window Items／Set Slot／Entity Equipment／
 * Entity Metadata（型6と粒子内のアイテム）／World Particles
 * （アイテム粒子）／Advancements（表示アイコン）／Spawn Entity Living／
 * Named Entity Spawn（末尾メタデータ）、サーバー行きでは Window Click／
 * Set Creative Slot。Declare Recipes のスロットは、上流
 * （763段）でパケットごと落とされるため対象外（404 と同じ扱い）。
 * Edit Book は 404 経路で名前引きにより落とされるため対象外。
 * 変換に失敗したパケットは警告を出して元のまま返す（他の変換器と同じ
 * 「素通し」方針）。
 */
public final class SlotTranslator {

    private static final Logger LOG = Logger.getLogger(SlotTranslator.class.getName());

    /** 404 側の粒子ID：ブロック（データはブロック状態 varint）。 */
    private static final int PARTICLE_BLOCK_404 = 3;
    /** 404 側の粒子ID：ダスト（データは f32×4）。 */
    private static final int PARTICLE_DUST_404 = 11;
    /** 404 側の粒子ID：落下ダスト（データはブロック状態 varint）。 */
    private static final int PARTICLE_FALLING_DUST_404 = 20;
    /** 404 側の粒子ID：アイテム（データはスロット）。 */
    private static final int PARTICLE_ITEM_404 = 27;

    /** 範囲外アイテムの代替（石）。 */
    private static final int FALLBACK_ITEM = 1;

    private SlotTranslator() {
    }

    // ------------------------------------------------------------------
    // スロット1個
    // ------------------------------------------------------------------

    /** スロット1個だけのペイロード変換（404形式 → 401形式）。 */
    public static byte[] slotTo401(byte[] payload) {
        PacketIO.Reader reader = new PacketIO.Reader(payload);
        PacketIO.Writer writer = new PacketIO.Writer();
        writeSlotAs401(reader, writer, payload);
        return writer.bytes();
    }

    /** スロット1個だけのペイロード変換（401形式 → 404形式）。 */
    public static byte[] slotFrom401(byte[] payload) {
        PacketIO.Reader reader = new PacketIO.Reader(payload);
        PacketIO.Writer writer = new PacketIO.Writer();
        writeSlotAs404(reader, writer, payload);
        return writer.bytes();
    }

    /** スロット1個だけのペイロード変換（404形式 → 393形式）。393 の slot 型は 401 と同一の旧形式。 */
    public static byte[] slotTo393(byte[] payload) {
        return slotTo401(payload);
    }

    /** スロット1個だけのペイロード変換（393形式 → 404形式）。 */
    public static byte[] slotFrom393(byte[] payload) {
        return slotFrom401(payload);
    }

    /** 404形式のスロットを読み、401形式で書く。 */
    static void writeSlotAs401(PacketIO.Reader reader, PacketIO.Writer writer, byte[] source) {
        boolean present = reader.readBoolean();
        if (!present) {
            writer.writeShort(-1);
            return;
        }
        int item = reader.readVarInt();
        int count = reader.readUnsignedByte();
        byte[] nbt = readRawNbt(reader, source);
        if (item < 0 || item > Short.MAX_VALUE) {
            LOG.warning("401のスロットに収まらないアイテムID " + item
                    + " を石に置き換えます。");
            item = FALLBACK_ITEM;
        }
        writer.writeShort(item);
        writer.writeByte(count);
        writer.writeBytes(nbt);
    }

    /** 401形式のスロットを読み、404形式で書く。 */
    static void writeSlotAs404(PacketIO.Reader reader, PacketIO.Writer writer, byte[] source) {
        int item = reader.readShort();
        if (item == -1) {
            writer.writeBoolean(false);
            return;
        }
        int count = reader.readUnsignedByte();
        byte[] nbt = readRawNbt(reader, source);
        writer.writeBoolean(true);
        writer.writeVarInt(item);
        writer.writeByte(count);
        writer.writeBytes(nbt);
    }

    /** optionalNbt 部分（無ければ 0x00 の1バイト）を生バイトのまま読む。 */
    private static byte[] readRawNbt(PacketIO.Reader reader, byte[] source) {
        int start = reader.position();
        reader.readNamedNbt();
        return Arrays.copyOfRange(source, start, reader.position());
    }

    // ------------------------------------------------------------------
    // パケット単位（クライアント行き：404形式 ⇄ 401形式）
    // ------------------------------------------------------------------

    /**
     * クライアント行きパケットを 404形式から 401形式へ付け替える。
     * スロットを含まないパケットはそのまま返す。
     */
    public static MinecraftPacket clientboundTo401(MinecraftPacket packet) {
        return rewriteClientbound(packet, true);
    }

    /**
     * クライアント行きパケットを 401形式から 404形式へ付け替える。
     * スロットを含まないパケットはそのまま返す。
     */
    public static MinecraftPacket clientboundFrom401(MinecraftPacket packet) {
        return rewriteClientbound(packet, false);
    }

    /**
     * クライアント行きパケットを 404形式から 393形式へ付け替える。
     * 393 の slot 型は 401 と同一の旧形式で、パケットIDも 401 と同一
     * （名前引きだけ 393 で行う）。
     */
    public static MinecraftPacket clientboundTo393(MinecraftPacket packet) {
        return rewriteClientbound(packet, true, ProtocolVersion.V1_13);
    }

    /**
     * クライアント行きパケットを 393形式から 404形式へ付け替える。
     * スロットを含まないパケットはそのまま返す。
     */
    public static MinecraftPacket clientboundFrom393(MinecraftPacket packet) {
        return rewriteClientbound(packet, false, ProtocolVersion.V1_13);
    }

    private static MinecraftPacket rewriteClientbound(MinecraftPacket packet, boolean to401) {
        return rewriteClientbound(packet, to401, ProtocolVersion.V1_13_1);
    }

    private static MinecraftPacket rewriteClientbound(MinecraftPacket packet, boolean to401,
            int protocol) {
        PacketIds.PacketName name = PacketIds.nameFor(packet.id(), PacketIds.State.PLAY,
                PacketIds.Direction.CLIENTBOUND, protocol).orElse(null);
        if (name == null) {
            return packet;
        }
        try {
            byte[] payload = packet.payload();
            PacketIO.Reader reader = new PacketIO.Reader(payload);
            PacketIO.Writer writer = new PacketIO.Writer();
            switch (name) {
                case PLAY_WINDOW_ITEMS -> {
                    writer.writeUnsignedByte(reader.readUnsignedByte()); // windowId
                    int count = reader.readShort();
                    writer.writeShort(count);
                    for (int i = 0; i < count; i++) {
                        writeSlot(reader, writer, payload, to401);
                    }
                }
                case PLAY_SET_SLOT -> {
                    writer.writeByte(reader.readByte()); // windowId
                    writer.writeShort(reader.readShort()); // slot
                    writeSlot(reader, writer, payload, to401);
                }
                case PLAY_ENTITY_EQUIPMENT -> {
                    writer.writeVarInt(reader.readVarInt()); // entityId
                    writer.writeVarInt(reader.readVarInt()); // slot（404形式は1件だけ）
                    writeSlot(reader, writer, payload, to401);
                }
                case PLAY_ENTITY_METADATA -> {
                    writer.writeVarInt(reader.readVarInt()); // entityId
                    writeMetadataEntries(reader, writer, payload, to401);
                }
                case PLAY_SPAWN_ENTITY_LIVING -> {
                    writer.writeVarInt(reader.readVarInt()); // entityId
                    writer.writeBytes(reader.readBytes(16)); // uuid
                    writer.writeVarInt(reader.readVarInt()); // type
                    writer.writeDouble(reader.readDouble());
                    writer.writeDouble(reader.readDouble());
                    writer.writeDouble(reader.readDouble());
                    writer.writeByte(reader.readByte()); // yaw
                    writer.writeByte(reader.readByte()); // pitch
                    writer.writeByte(reader.readByte()); // headPitch
                    writer.writeShort(reader.readShort()); // velocity
                    writer.writeShort(reader.readShort());
                    writer.writeShort(reader.readShort());
                    writeMetadataEntries(reader, writer, payload, to401);
                }
                case PLAY_NAMED_ENTITY_SPAWN -> {
                    writer.writeVarInt(reader.readVarInt()); // entityId
                    writer.writeBytes(reader.readBytes(16)); // uuid
                    writer.writeDouble(reader.readDouble());
                    writer.writeDouble(reader.readDouble());
                    writer.writeDouble(reader.readDouble());
                    writer.writeByte(reader.readByte()); // yaw
                    writer.writeByte(reader.readByte()); // pitch
                    writeMetadataEntries(reader, writer, payload, to401);
                }
                case PLAY_WORLD_PARTICLES -> {
                    int particle = reader.readVarInt();
                    writer.writeVarInt(particle);
                    writer.writeBoolean(reader.readBoolean()); // longDistance
                    writer.writeFloat(reader.readFloat());
                    writer.writeFloat(reader.readFloat());
                    writer.writeFloat(reader.readFloat());
                    writer.writeFloat(reader.readFloat()); // offset
                    writer.writeFloat(reader.readFloat());
                    writer.writeFloat(reader.readFloat());
                    writer.writeFloat(reader.readFloat()); // speed
                    writer.writeInt(reader.readInt()); // count
                    writeParticleData(reader, writer, payload, particle, to401);
                }
                case PLAY_ADVANCEMENTS -> writeAdvancements(reader, writer, payload, to401);
                default -> {
                    return packet;
                }
            }
            requireEnd(reader, name);
            return new MinecraftPacket(packet.id(), writer.bytes());
        } catch (IllegalArgumentException e) {
            LOG.warning("401のスロット付け替えができなかったため素通しします（"
                    + name + "）: " + e.getMessage());
            return packet;
        }
    }

    // ------------------------------------------------------------------
    // パケット単位（サーバー行き：401形式 → 404形式）
    // ------------------------------------------------------------------

    /**
     * サーバー行きパケットを 401形式から 404形式へ付け替える。
     * スロットを含まないパケットはそのまま返す。
     */
    public static MinecraftPacket serverboundFrom401(MinecraftPacket packet) {
        return rewriteServerboundFromLegacy(packet, ProtocolVersion.V1_13_1);
    }

    /**
     * サーバー行きパケットを 393形式から 404形式へ付け替える。
     * スロットを含まないパケットはそのまま返す。
     */
    public static MinecraftPacket serverboundFrom393(MinecraftPacket packet) {
        return rewriteServerboundFromLegacy(packet, ProtocolVersion.V1_13);
    }

    private static MinecraftPacket rewriteServerboundFromLegacy(MinecraftPacket packet,
            int protocol) {
        PacketIds.PacketName name = PacketIds.nameFor(packet.id(), PacketIds.State.PLAY,
                PacketIds.Direction.SERVERBOUND, protocol).orElse(null);
        if (name == null) {
            return packet;
        }
        try {
            byte[] payload = packet.payload();
            PacketIO.Reader reader = new PacketIO.Reader(payload);
            PacketIO.Writer writer = new PacketIO.Writer();
            switch (name) {
                case PLAY_WINDOW_CLICK -> {
                    writer.writeUnsignedByte(reader.readUnsignedByte()); // windowId
                    writer.writeShort(reader.readShort()); // slot
                    writer.writeByte(reader.readByte()); // button
                    writer.writeShort(reader.readShort()); // actionNumber
                    writer.writeByte(reader.readByte()); // mode（404 は i8）
                    writeSlotAs404(reader, writer, payload); // clickedItem
                }
                case PLAY_SET_CREATIVE_SLOT -> {
                    writer.writeShort(reader.readShort()); // slot
                    writeSlotAs404(reader, writer, payload);
                }
                default -> {
                    return packet;
                }
            }
            requireEnd(reader, name);
            return new MinecraftPacket(packet.id(), writer.bytes());
        } catch (IllegalArgumentException e) {
            LOG.warning("401のスロット付け替えができなかったため素通しします（"
                    + name + "）: " + e.getMessage());
            return packet;
        }
    }

    // ------------------------------------------------------------------
    // 共通部品
    // ------------------------------------------------------------------

    private static void writeSlot(PacketIO.Reader reader, PacketIO.Writer writer,
            byte[] source, boolean to401) {
        if (to401) {
            writeSlotAs401(reader, writer, source);
        } else {
            writeSlotAs404(reader, writer, source);
        }
    }

    /**
     * メタデータのエントリ列（entityId は含まない）を付け替える。
     * 型番号と値の形は 404 の変換器（ChunkTranslator の
     * translateMetadataValue404）と同じ解釈にする。型6がスロット、
     * 型15の粒子データにもスロットが入り得る。
     */
    private static void writeMetadataEntries(PacketIO.Reader reader, PacketIO.Writer writer,
            byte[] source, boolean to401) {
        while (true) {
            int index = reader.readUnsignedByte();
            writer.writeUnsignedByte(index);
            if (index == 0xFF) {
                return;
            }
            int type = reader.readVarInt();
            writer.writeVarInt(type);
            switch (type) {
                case 0 -> writer.writeByte(reader.readByte());
                case 1 -> writer.writeVarInt(reader.readVarInt());
                case 2 -> writer.writeFloat(reader.readFloat());
                case 3, 4 -> writer.writeString(reader.readString());
                case 5 -> {
                    boolean present = reader.readBoolean();
                    writer.writeBoolean(present);
                    if (present) {
                        writer.writeString(reader.readString());
                    }
                }
                case 6 -> writeSlot(reader, writer, source, to401);
                case 7 -> writer.writeBoolean(reader.readBoolean());
                case 8 -> {
                    writer.writeFloat(reader.readFloat());
                    writer.writeFloat(reader.readFloat());
                    writer.writeFloat(reader.readFloat());
                }
                case 9 -> writer.writeLong(reader.readLong()); // position
                case 10 -> {
                    boolean present = reader.readBoolean();
                    writer.writeBoolean(present);
                    if (present) {
                        writer.writeLong(reader.readLong());
                    }
                }
                case 11 -> writer.writeVarInt(reader.readVarInt()); // direction
                case 12 -> {
                    boolean present = reader.readBoolean();
                    writer.writeBoolean(present);
                    if (present) {
                        writer.writeBytes(reader.readBytes(16)); // uuid
                    }
                }
                case 13 -> writer.writeVarInt(reader.readVarInt()); // blockState
                case 14 -> writer.writeBytes(readRawNbt(reader, source));
                case 15 -> {
                    int particle = reader.readVarInt();
                    writer.writeVarInt(particle);
                    writeParticleData(reader, writer, source, particle, to401);
                }
                default -> throw new IllegalArgumentException("未知のメタデータ型です: " + type);
            }
        }
    }

    /** 粒子のデータ部（404側の粒子IDで形が決まる）を付け替える。 */
    private static void writeParticleData(PacketIO.Reader reader, PacketIO.Writer writer,
            byte[] source, int particle404, boolean to401) {
        switch (particle404) {
            case PARTICLE_BLOCK_404, PARTICLE_FALLING_DUST_404 ->
                    writer.writeVarInt(reader.readVarInt());
            case PARTICLE_DUST_404 -> {
                writer.writeFloat(reader.readFloat());
                writer.writeFloat(reader.readFloat());
                writer.writeFloat(reader.readFloat());
                writer.writeFloat(reader.readFloat());
            }
            case PARTICLE_ITEM_404 -> writeSlot(reader, writer, source, to401);
            default -> {
            }
        }
    }

    /** Advancements を付け替える（表示アイコンのスロットだけが対象）。 */
    private static void writeAdvancements(PacketIO.Reader reader, PacketIO.Writer writer,
            byte[] source, boolean to401) {
        writer.writeBoolean(reader.readBoolean()); // reset
        int size = reader.readVarInt();
        writer.writeVarInt(size);
        for (int i = 0; i < size; i++) {
            writer.writeString(reader.readString()); // key
            boolean hasParent = reader.readBoolean();
            writer.writeBoolean(hasParent);
            if (hasParent) {
                writer.writeString(reader.readString());
            }
            boolean hasDisplay = reader.readBoolean();
            writer.writeBoolean(hasDisplay);
            if (hasDisplay) {
                writer.writeString(reader.readString()); // title
                writer.writeString(reader.readString()); // description
                writeSlot(reader, writer, source, to401); // icon
                writer.writeVarInt(reader.readVarInt()); // frameType
                int flags = reader.readInt();
                writer.writeInt(flags);
                if ((flags & 1) != 0) {
                    writer.writeString(reader.readString()); // backgroundTexture
                }
                writer.writeFloat(reader.readFloat()); // x
                writer.writeFloat(reader.readFloat()); // y
            }
            writeCriteria(reader, writer);
            int requirements = reader.readVarInt();
            writer.writeVarInt(requirements);
            for (int r = 0; r < requirements; r++) {
                int inner = reader.readVarInt();
                writer.writeVarInt(inner);
                for (int k = 0; k < inner; k++) {
                    writer.writeString(reader.readString());
                }
            }
        }
        int removed = reader.readVarInt();
        writer.writeVarInt(removed);
        for (int i = 0; i < removed; i++) {
            writer.writeString(reader.readString());
        }
        int progressSize = reader.readVarInt();
        writer.writeVarInt(progressSize);
        for (int i = 0; i < progressSize; i++) {
            writer.writeString(reader.readString()); // key
            writeCriteria(reader, writer);
        }
    }

    /** Advancements の達成状況（criterionId＋任意の達成日時）の列。 */
    private static void writeCriteria(PacketIO.Reader reader, PacketIO.Writer writer) {
        int criteria = reader.readVarInt();
        writer.writeVarInt(criteria);
        for (int c = 0; c < criteria; c++) {
            writer.writeString(reader.readString()); // criterionId
            boolean achieved = reader.readBoolean();
            writer.writeBoolean(achieved);
            if (achieved) {
                writer.writeLong(reader.readLong()); // dateAchieved
            }
        }
    }

    private static void requireEnd(PacketIO.Reader reader, PacketIds.PacketName name) {
        if (reader.remaining() != 0) {
            throw new IllegalArgumentException(name + " の末尾に "
                    + reader.remaining() + " バイト残っています。");
        }
    }

    // ------------------------------------------------------------------
    // 340（1.12.2）
    //
    // 340 の実ワイヤ形式は、この層の内側（404形式の符号化に 340 の
    // レジストリ番号を載せたハイブリッド形式）と次の点が違う。
    //   ・スロット：itemId i16＋count i8＋itemDamage i16＋NBT。
    //     ハイブリッド側の NBT の Damage タグが itemDamage になる
    //     （逆向きは itemDamage≠0 を Damage タグに戻す）。
    //   ・エンティティメタデータ：型フィールドが i8（型番号自体は
    //     340 の番号のまま。番号の付け替えは ChunkTranslator が担う）。
    //   ・World Particles：粒子 ID が i32。データの形も違い、
    //     iconcrack(36) はスロットではなく (itemId, damage) の
    //     varint 2個、reddust(30) はデータを持たない（実 jar の
    //     EnumParticleTypes の引数個数で確認：36→2、37/38/46→1、
    //     30→0）。ハイブリッド側の dust 4 float は落とし、復元時は
    //     既定色 (1,0,0,1) を補う。
    // ------------------------------------------------------------------

    /** スロット1個だけのペイロード変換（ハイブリッド形式 → 340形式）。 */
    public static byte[] slotTo340(byte[] payload) {
        PacketIO.Reader reader = new PacketIO.Reader(payload);
        PacketIO.Writer writer = new PacketIO.Writer();
        writeSlotAs340(reader, writer, payload);
        return writer.bytes();
    }

    /** スロット1個だけのペイロード変換（340形式 → ハイブリッド形式）。 */
    public static byte[] slotFrom340(byte[] payload) {
        PacketIO.Reader reader = new PacketIO.Reader(payload);
        PacketIO.Writer writer = new PacketIO.Writer();
        writeSlotAsHybridFrom340(reader, writer, payload);
        return writer.bytes();
    }

    /** ハイブリッド形式のスロットを読み、340形式で書く。 */
    static void writeSlotAs340(PacketIO.Reader reader, PacketIO.Writer writer, byte[] source) {
        boolean present = reader.readBoolean();
        if (!present) {
            writer.writeShort(-1);
            return;
        }
        int item = reader.readVarInt();
        int count = reader.readUnsignedByte();
        Nbt.Tag nbt = reader.readNamedNbt().tag();
        Integer damage = ChunkTranslator.slotDamageTag(nbt);
        Nbt.Tag rest = ChunkTranslator.withoutSlotDamageTag(nbt);
        if (item < 0 || item > Short.MAX_VALUE) {
            LOG.warning("340のスロットに収まらないアイテムID " + item
                    + " を石に置き換えます。");
            item = FALLBACK_ITEM;
        }
        writer.writeShort(item);
        writer.writeByte(count);
        writer.writeShort(damage != null ? damage : 0);
        if (rest == null) {
            writer.writeByte(0);
        } else {
            writer.writeNamedNbt("", rest);
        }
    }

    /** 340形式のスロットを読み、ハイブリッド形式で書く。 */
    static void writeSlotAsHybridFrom340(PacketIO.Reader reader, PacketIO.Writer writer,
            byte[] source) {
        int item = reader.readShort();
        if (item == -1) {
            writer.writeBoolean(false);
            return;
        }
        int count = reader.readUnsignedByte();
        int damage = reader.readShort();
        Nbt.Tag nbt = reader.readNamedNbt().tag();
        if (damage != 0) {
            nbt = ChunkTranslator.withSlotDamageTag(nbt, damage);
        }
        writer.writeBoolean(true);
        writer.writeVarInt(item);
        writer.writeByte(count);
        if (nbt == null) {
            writer.writeByte(0);
        } else {
            writer.writeNamedNbt("", nbt);
        }
    }

    private static void writeSlot340(PacketIO.Reader reader, PacketIO.Writer writer,
            byte[] source, boolean to340) {
        if (to340) {
            writeSlotAs340(reader, writer, source);
        } else {
            writeSlotAsHybridFrom340(reader, writer, source);
        }
    }

    /**
     * クライアント行きパケットをハイブリッド形式から 340形式へ付け替える。
     * スロットを含まないパケットはそのまま返す。
     */
    public static MinecraftPacket clientboundTo340(MinecraftPacket packet) {
        return rewriteClientbound340(packet, true);
    }

    /**
     * クライアント行きパケットを 340形式からハイブリッド形式へ付け替える。
     * スロットを含まないパケットはそのまま返す。
     */
    public static MinecraftPacket clientboundFrom340(MinecraftPacket packet) {
        return rewriteClientbound340(packet, false);
    }

    private static MinecraftPacket rewriteClientbound340(MinecraftPacket packet, boolean to340) {
        PacketIds.PacketName name = PacketIds.nameFor(packet.id(), PacketIds.State.PLAY,
                PacketIds.Direction.CLIENTBOUND, ProtocolVersion.V1_12_2).orElse(null);
        if (name == null) {
            return packet;
        }
        try {
            byte[] payload = packet.payload();
            PacketIO.Reader reader = new PacketIO.Reader(payload);
            PacketIO.Writer writer = new PacketIO.Writer();
            switch (name) {
                case PLAY_WINDOW_ITEMS -> {
                    writer.writeUnsignedByte(reader.readUnsignedByte()); // windowId
                    int count = reader.readShort();
                    writer.writeShort(count);
                    for (int i = 0; i < count; i++) {
                        writeSlot340(reader, writer, payload, to340);
                    }
                }
                case PLAY_SET_SLOT -> {
                    writer.writeByte(reader.readByte()); // windowId
                    writer.writeShort(reader.readShort()); // slot
                    writeSlot340(reader, writer, payload, to340);
                }
                case PLAY_ENTITY_EQUIPMENT -> {
                    writer.writeVarInt(reader.readVarInt()); // entityId
                    writer.writeVarInt(reader.readVarInt()); // slot
                    writeSlot340(reader, writer, payload, to340);
                }
                case PLAY_ENTITY_METADATA -> {
                    writer.writeVarInt(reader.readVarInt()); // entityId
                    writeMetadataEntries340(reader, writer, payload, to340);
                }
                case PLAY_SPAWN_ENTITY_LIVING -> {
                    writer.writeVarInt(reader.readVarInt()); // entityId
                    writer.writeBytes(reader.readBytes(16)); // uuid
                    writer.writeVarInt(reader.readVarInt()); // type
                    writer.writeDouble(reader.readDouble());
                    writer.writeDouble(reader.readDouble());
                    writer.writeDouble(reader.readDouble());
                    writer.writeByte(reader.readByte()); // yaw
                    writer.writeByte(reader.readByte()); // pitch
                    writer.writeByte(reader.readByte()); // headPitch
                    writer.writeShort(reader.readShort()); // velocity
                    writer.writeShort(reader.readShort());
                    writer.writeShort(reader.readShort());
                    writeMetadataEntries340(reader, writer, payload, to340);
                }
                case PLAY_NAMED_ENTITY_SPAWN -> {
                    writer.writeVarInt(reader.readVarInt()); // entityId
                    writer.writeBytes(reader.readBytes(16)); // uuid
                    writer.writeDouble(reader.readDouble());
                    writer.writeDouble(reader.readDouble());
                    writer.writeDouble(reader.readDouble());
                    writer.writeByte(reader.readByte()); // yaw
                    writer.writeByte(reader.readByte()); // pitch
                    writeMetadataEntries340(reader, writer, payload, to340);
                }
                case PLAY_WORLD_PARTICLES -> {
                    int particle;
                    if (to340) {
                        particle = reader.readVarInt();
                        writer.writeInt(particle);
                    } else {
                        particle = reader.readInt();
                        writer.writeVarInt(particle);
                    }
                    writer.writeBoolean(reader.readBoolean()); // longDistance
                    writer.writeFloat(reader.readFloat());
                    writer.writeFloat(reader.readFloat());
                    writer.writeFloat(reader.readFloat());
                    writer.writeFloat(reader.readFloat()); // offset
                    writer.writeFloat(reader.readFloat());
                    writer.writeFloat(reader.readFloat());
                    writer.writeFloat(reader.readFloat()); // speed
                    writer.writeInt(reader.readInt()); // count
                    writeParticleData340(reader, writer, payload, particle, to340);
                }
                case PLAY_ADVANCEMENTS -> writeAdvancements340(reader, writer, payload, to340);
                default -> {
                    return packet;
                }
            }
            requireEnd(reader, name);
            return new MinecraftPacket(packet.id(), writer.bytes());
        } catch (IllegalArgumentException e) {
            LOG.warning("340のスロット付け替えができなかったため素通しします（"
                    + name + "）: " + e.getMessage());
            return packet;
        }
    }

    /**
     * サーバー行きパケットを 340形式からハイブリッド形式へ付け替える。
     * スロットを含まないパケットはそのまま返す。
     */
    public static MinecraftPacket serverboundFrom340(MinecraftPacket packet) {
        PacketIds.PacketName name = PacketIds.nameFor(packet.id(), PacketIds.State.PLAY,
                PacketIds.Direction.SERVERBOUND, ProtocolVersion.V1_12_2).orElse(null);
        if (name == null) {
            return packet;
        }
        try {
            byte[] payload = packet.payload();
            PacketIO.Reader reader = new PacketIO.Reader(payload);
            PacketIO.Writer writer = new PacketIO.Writer();
            switch (name) {
                case PLAY_WINDOW_CLICK -> {
                    writer.writeUnsignedByte(reader.readUnsignedByte()); // windowId
                    writer.writeShort(reader.readShort()); // slot
                    writer.writeByte(reader.readByte()); // button
                    writer.writeShort(reader.readShort()); // actionNumber
                    writer.writeByte(reader.readByte()); // mode（340 は i8）
                    writeSlotAsHybridFrom340(reader, writer, payload); // clickedItem
                }
                case PLAY_SET_CREATIVE_SLOT -> {
                    writer.writeShort(reader.readShort()); // slot
                    writeSlotAsHybridFrom340(reader, writer, payload);
                }
                default -> {
                    return packet;
                }
            }
            requireEnd(reader, name);
            return new MinecraftPacket(packet.id(), writer.bytes());
        } catch (IllegalArgumentException e) {
            LOG.warning("340のスロット付け替えができなかったため素通しします（"
                    + name + "）: " + e.getMessage());
            return packet;
        }
    }

    /** メタデータのエントリ列を付け替える（型は i8⇄varint、番号は 340 のまま）。 */
    private static void writeMetadataEntries340(PacketIO.Reader reader, PacketIO.Writer writer,
            byte[] source, boolean to340) {
        while (true) {
            int index = reader.readUnsignedByte();
            writer.writeUnsignedByte(index);
            if (index == 0xFF) {
                return;
            }
            int type;
            if (to340) {
                type = reader.readVarInt();
                writer.writeByte(type);
            } else {
                type = reader.readUnsignedByte();
                writer.writeVarInt(type);
            }
            switch (type) {
                case 0 -> writer.writeByte(reader.readByte());
                case 1 -> writer.writeVarInt(reader.readVarInt());
                case 2 -> writer.writeFloat(reader.readFloat());
                case 3, 4 -> writer.writeString(reader.readString());
                case 5 -> {
                    boolean present = reader.readBoolean();
                    writer.writeBoolean(present);
                    if (present) {
                        writer.writeString(reader.readString());
                    }
                }
                case 6 -> writeSlot340(reader, writer, source, to340);
                case 7 -> writer.writeBoolean(reader.readBoolean());
                case 8 -> {
                    writer.writeFloat(reader.readFloat());
                    writer.writeFloat(reader.readFloat());
                    writer.writeFloat(reader.readFloat());
                }
                case 9 -> writer.writeLong(reader.readLong()); // position
                case 10 -> {
                    boolean present = reader.readBoolean();
                    writer.writeBoolean(present);
                    if (present) {
                        writer.writeLong(reader.readLong());
                    }
                }
                case 11 -> writer.writeVarInt(reader.readVarInt()); // direction
                case 12 -> {
                    boolean present = reader.readBoolean();
                    writer.writeBoolean(present);
                    if (present) {
                        writer.writeBytes(reader.readBytes(16)); // uuid
                    }
                }
                case 13 -> writer.writeVarInt(reader.readVarInt()); // blockid
                case 14 -> { // optblockid
                    boolean present = reader.readBoolean();
                    writer.writeBoolean(present);
                    if (present) {
                        writer.writeVarInt(reader.readVarInt());
                    }
                }
                case 15 -> writer.writeBytes(readRawNbt(reader, source)); // nbt
                case 16 -> { // particle
                    int particle = reader.readVarInt();
                    writer.writeVarInt(particle);
                    writeParticleData340(reader, writer, source, particle, to340);
                }
                default -> throw new IllegalArgumentException("未知のメタデータ型です: " + type);
            }
        }
    }

    /** 粒子のデータ部（340 の粒子 ID で形が決まる）を境界形式に付け替える。 */
    private static void writeParticleData340(PacketIO.Reader reader, PacketIO.Writer writer,
            byte[] source, int particle340, boolean to340) {
        switch (particle340) {
            case 37, 38, 46 -> writer.writeVarInt(reader.readVarInt()); // block 系は varint 1個
            case 30 -> { // reddust：実ワイヤはデータ無し
                if (to340) {
                    reader.readFloat();
                    reader.readFloat();
                    reader.readFloat();
                    reader.readFloat();
                } else {
                    writer.writeFloat(1.0f);
                    writer.writeFloat(0.0f);
                    writer.writeFloat(0.0f);
                    writer.writeFloat(1.0f);
                }
            }
            case 36 -> { // iconcrack：実ワイヤは (itemId, damage) の varint 2個
                if (to340) {
                    boolean present = reader.readBoolean();
                    if (!present) {
                        writer.writeVarInt(0);
                        writer.writeVarInt(0);
                    } else {
                        int item = reader.readVarInt();
                        reader.readUnsignedByte(); // count はワイヤに無いため落とす
                        Nbt.Tag nbt = reader.readNamedNbt().tag();
                        Integer damage = ChunkTranslator.slotDamageTag(nbt);
                        writer.writeVarInt(item);
                        writer.writeVarInt(damage != null ? damage : 0);
                    }
                } else {
                    int item = reader.readVarInt();
                    int damage = reader.readVarInt();
                    writer.writeBoolean(true);
                    writer.writeVarInt(item);
                    writer.writeByte(1); // count
                    if (damage != 0) {
                        writer.writeNamedNbt("",
                                ChunkTranslator.withSlotDamageTag(null, damage));
                    } else {
                        writer.writeByte(0);
                    }
                }
            }
            default -> {
            }
        }
    }

    /** Advancements を付け替える（表示アイコンのスロットだけが対象。構造は 404 と同一）。 */
    private static void writeAdvancements340(PacketIO.Reader reader, PacketIO.Writer writer,
            byte[] source, boolean to340) {
        writer.writeBoolean(reader.readBoolean()); // reset
        int size = reader.readVarInt();
        writer.writeVarInt(size);
        for (int i = 0; i < size; i++) {
            writer.writeString(reader.readString()); // key
            boolean hasParent = reader.readBoolean();
            writer.writeBoolean(hasParent);
            if (hasParent) {
                writer.writeString(reader.readString());
            }
            boolean hasDisplay = reader.readBoolean();
            writer.writeBoolean(hasDisplay);
            if (hasDisplay) {
                writer.writeString(reader.readString()); // title
                writer.writeString(reader.readString()); // description
                writeSlot340(reader, writer, source, to340); // icon
                writer.writeVarInt(reader.readVarInt()); // frameType
                int flags = reader.readInt();
                writer.writeInt(flags);
                if ((flags & 1) != 0) {
                    writer.writeString(reader.readString()); // backgroundTexture
                }
                writer.writeFloat(reader.readFloat()); // x
                writer.writeFloat(reader.readFloat()); // y
            }
            writeCriteria(reader, writer);
            int requirements = reader.readVarInt();
            writer.writeVarInt(requirements);
            for (int r = 0; r < requirements; r++) {
                int inner = reader.readVarInt();
                writer.writeVarInt(inner);
                for (int k = 0; k < inner; k++) {
                    writer.writeString(reader.readString());
                }
            }
        }
        int removed = reader.readVarInt();
        writer.writeVarInt(removed);
        for (int i = 0; i < removed; i++) {
            writer.writeString(reader.readString());
        }
        int progressSize = reader.readVarInt();
        writer.writeVarInt(progressSize);
        for (int i = 0; i < progressSize; i++) {
            writer.writeString(reader.readString()); // key
            writeCriteria(reader, writer);
        }
    }
}
