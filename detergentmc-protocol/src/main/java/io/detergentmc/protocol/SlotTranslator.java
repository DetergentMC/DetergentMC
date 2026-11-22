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

    /**
     * サーバー行きパケット（ハイブリッド形式）のスロットを 401形式へ
     * 付け替える。スロットを含まないパケットはそのまま返す。
     * （監査で新設：従来は ProxySession のサーバー行き分岐が
     * clientboundTo401 を流用しており、名前解決が CLIENTBOUND 表になる
     * ため Window Click／Set Creative Slot が誤解決され、スロットが
     * ハイブリッド形式のままバックエンドへ届いていた。）
     */
    public static MinecraftPacket serverboundTo401(MinecraftPacket packet) {
        return rewriteServerboundToLegacy(packet, ProtocolVersion.V1_13_1);
    }

    /**
     * サーバー行きパケット（ハイブリッド形式）のスロットを 393形式へ
     * 付け替える。393 の slot 型は 401 と同一の旧形式で、パケットIDも
     * 401 と同一（名前引きだけ 393 で行う）。
     */
    public static MinecraftPacket serverboundTo393(MinecraftPacket packet) {
        return rewriteServerboundToLegacy(packet, ProtocolVersion.V1_13);
    }

    private static MinecraftPacket rewriteServerboundToLegacy(MinecraftPacket packet,
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
                    writeSlot(reader, writer, payload, true); // clickedItem
                }
                case PLAY_SET_CREATIVE_SLOT -> {
                    writer.writeShort(reader.readShort()); // slot
                    writeSlot(reader, writer, payload, true);
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

    /**
     * サーバー行きパケット（ハイブリッド形式）のスロットを 340 の実ワイヤ
     * 形式へ付け替える。スロットを含まないパケットはそのまま返す。
     * （監査で新設：従来は ProxySession のサーバー行き分岐が
     * clientboundTo340 を流用しており、名前解決が CLIENTBOUND 表になる
     * ため Window Click／Set Creative Slot が誤解決され、スロットが
     * ハイブリッド形式のままバックエンドへ届いていた。）
     */
    public static MinecraftPacket serverboundTo340(MinecraftPacket packet) {
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
                    writeSlotAs340(reader, writer, payload); // clickedItem
                }
                case PLAY_SET_CREATIVE_SLOT -> {
                    writer.writeShort(reader.readShort()); // slot
                    writeSlotAs340(reader, writer, payload);
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
    // ------------------------------------------------------------------
    // 316（1.11.2）対応
    //
    // 316 の実ワイヤ形式は 316 と同じ（スロット：itemId i16＋
    // count i8＋itemDamage i16＋NBT、メタデータの型は i8、
    // World Particles の粒子 ID は i32 でデータ形も 316 と同じ）
    // ため、この節は 316 の節を写して番号だけ 316 に差し替えた
    // もの。316 固有の差分は1点だけ：
    //   ・エンティティメタデータの型は 316 では 0〜12 のみ。
    //     ハイブリッド側の型 13（blockid）・14（optblockid）・
    //     15（nbt）・16（particle）は 316 に無いため、
    //     writeMetadataEntries316 が値だけ読み捨てて落とす
    //     （逆向きはワイヤに 0〜12 しか来ないため素通し）。
    // ------------------------------------------------------------------

    /** スロット1個だけのペイロード変換（ハイブリッド形式 → 316形式）。 */
    public static byte[] slotTo316(byte[] payload) {
        PacketIO.Reader reader = new PacketIO.Reader(payload);
        PacketIO.Writer writer = new PacketIO.Writer();
        writeSlotAs316(reader, writer, payload);
        return writer.bytes();
    }

    /** スロット1個だけのペイロード変換（316形式 → ハイブリッド形式）。 */
    public static byte[] slotFrom316(byte[] payload) {
        PacketIO.Reader reader = new PacketIO.Reader(payload);
        PacketIO.Writer writer = new PacketIO.Writer();
        writeSlotAsHybridFrom316(reader, writer, payload);
        return writer.bytes();
    }

    /** ハイブリッド形式のスロットを読み、316形式で書く。 */
    static void writeSlotAs316(PacketIO.Reader reader, PacketIO.Writer writer, byte[] source) {
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
            LOG.warning("316のスロットに収まらないアイテムID " + item
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

    /** 316形式のスロットを読み、ハイブリッド形式で書く。 */
    static void writeSlotAsHybridFrom316(PacketIO.Reader reader, PacketIO.Writer writer,
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

    private static void writeSlot316(PacketIO.Reader reader, PacketIO.Writer writer,
            byte[] source, boolean to316) {
        if (to316) {
            writeSlotAs316(reader, writer, source);
        } else {
            writeSlotAsHybridFrom316(reader, writer, source);
        }
    }

    /**
     * クライアント行きパケットをハイブリッド形式から 316形式へ付け替える。
     * スロットを含まないパケットはそのまま返す。
     */
    public static MinecraftPacket clientboundTo316(MinecraftPacket packet) {
        return rewriteClientbound316(packet, true);
    }

    /**
     * クライアント行きパケットを 316形式からハイブリッド形式へ付け替える。
     * スロットを含まないパケットはそのまま返す。
     */
    public static MinecraftPacket clientboundFrom316(MinecraftPacket packet) {
        return rewriteClientbound316(packet, false);
    }

    private static MinecraftPacket rewriteClientbound316(MinecraftPacket packet, boolean to316) {
        PacketIds.PacketName name = PacketIds.nameFor(packet.id(), PacketIds.State.PLAY,
                PacketIds.Direction.CLIENTBOUND, ProtocolVersion.V1_11_2).orElse(null);
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
                        writeSlot316(reader, writer, payload, to316);
                    }
                }
                case PLAY_SET_SLOT -> {
                    writer.writeByte(reader.readByte()); // windowId
                    writer.writeShort(reader.readShort()); // slot
                    writeSlot316(reader, writer, payload, to316);
                }
                case PLAY_ENTITY_EQUIPMENT -> {
                    writer.writeVarInt(reader.readVarInt()); // entityId
                    writer.writeVarInt(reader.readVarInt()); // slot
                    writeSlot316(reader, writer, payload, to316);
                }
                case PLAY_ENTITY_METADATA -> {
                    writer.writeVarInt(reader.readVarInt()); // entityId
                    writeMetadataEntries316(reader, writer, payload, to316);
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
                    writeMetadataEntries316(reader, writer, payload, to316);
                }
                case PLAY_NAMED_ENTITY_SPAWN -> {
                    writer.writeVarInt(reader.readVarInt()); // entityId
                    writer.writeBytes(reader.readBytes(16)); // uuid
                    writer.writeDouble(reader.readDouble());
                    writer.writeDouble(reader.readDouble());
                    writer.writeDouble(reader.readDouble());
                    writer.writeByte(reader.readByte()); // yaw
                    writer.writeByte(reader.readByte()); // pitch
                    writeMetadataEntries316(reader, writer, payload, to316);
                }
                case PLAY_WORLD_PARTICLES -> {
                    int particle;
                    if (to316) {
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
                    writeParticleData316(reader, writer, payload, particle, to316);
                }
                case PLAY_ADVANCEMENTS -> writeAdvancements316(reader, writer, payload, to316);
                default -> {
                    return packet;
                }
            }
            requireEnd(reader, name);
            return new MinecraftPacket(packet.id(), writer.bytes());
        } catch (IllegalArgumentException e) {
            LOG.warning("316のスロット付け替えができなかったため素通しします（"
                    + name + "）: " + e.getMessage());
            return packet;
        }
    }

    /**
     * サーバー行きパケットを 316形式からハイブリッド形式へ付け替える。
     * スロットを含まないパケットはそのまま返す。
     */
    public static MinecraftPacket serverboundFrom316(MinecraftPacket packet) {
        PacketIds.PacketName name = PacketIds.nameFor(packet.id(), PacketIds.State.PLAY,
                PacketIds.Direction.SERVERBOUND, ProtocolVersion.V1_11_2).orElse(null);
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
                    writer.writeByte(reader.readByte()); // mode（316 は i8）
                    writeSlotAsHybridFrom316(reader, writer, payload); // clickedItem
                }
                case PLAY_SET_CREATIVE_SLOT -> {
                    writer.writeShort(reader.readShort()); // slot
                    writeSlotAsHybridFrom316(reader, writer, payload);
                }
                default -> {
                    return packet;
                }
            }
            requireEnd(reader, name);
            return new MinecraftPacket(packet.id(), writer.bytes());
        } catch (IllegalArgumentException e) {
            LOG.warning("316のスロット付け替えができなかったため素通しします（"
                    + name + "）: " + e.getMessage());
            return packet;
        }
    }

    /**
     * サーバー行きパケット（ハイブリッド形式）のスロットを 316 の実ワイヤ
     * 形式へ付け替える。スロットを含まないパケットはそのまま返す。
     * （監査で新設：従来は ProxySession のサーバー行き分岐が
     * clientboundTo316 を流用しており、名前解決が CLIENTBOUND 表になる
     * ため Window Click／Set Creative Slot が誤解決され、スロットが
     * ハイブリッド形式のままバックエンドへ届いていた。）
     */
    public static MinecraftPacket serverboundTo316(MinecraftPacket packet) {
        PacketIds.PacketName name = PacketIds.nameFor(packet.id(), PacketIds.State.PLAY,
                PacketIds.Direction.SERVERBOUND, ProtocolVersion.V1_11_2).orElse(null);
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
                    writer.writeByte(reader.readByte()); // mode（316 は i8）
                    writeSlotAs316(reader, writer, payload); // clickedItem
                }
                case PLAY_SET_CREATIVE_SLOT -> {
                    writer.writeShort(reader.readShort()); // slot
                    writeSlotAs316(reader, writer, payload);
                }
                default -> {
                    return packet;
                }
            }
            requireEnd(reader, name);
            return new MinecraftPacket(packet.id(), writer.bytes());
        } catch (IllegalArgumentException e) {
            LOG.warning("316のスロット付け替えができなかったため素通しします（"
                    + name + "）: " + e.getMessage());
            return packet;
        }
    }

    /** メタデータのエントリ列を付け替える（型は i8⇄varint、番号は 316 のまま）。 */
    private static void writeMetadataEntries316(PacketIO.Reader reader, PacketIO.Writer writer,
            byte[] source, boolean to316) {
        while (true) {
            int index = reader.readUnsignedByte();
            if (index == 0xFF) {
                writer.writeUnsignedByte(0xFF);
                return;
            }
            int type;
            if (to316) {
                type = reader.readVarInt();
            } else {
                type = reader.readUnsignedByte();
            }
            if (to316 && type >= 13 && type <= 16) {
                // 316 に無い型（13 blockid・14 optblockid・15 nbt・
                // 16 particle）は値だけ読み捨てて落とす。
                writeMetadataValue316(reader, new PacketIO.Writer(),
                        source, type, true);
                continue;
            }
            writer.writeUnsignedByte(index);
            if (to316) {
                writer.writeByte(type);
            } else {
                writer.writeVarInt(type);
            }
            writeMetadataValue316(reader, writer, source, type, to316);
        }
    }

    /** メタデータの値1個を付け替える（型番号は世代内番号のまま）。 */
    private static void writeMetadataValue316(PacketIO.Reader reader, PacketIO.Writer writer,
            byte[] source, int type, boolean to316) {
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
            case 6 -> writeSlot316(reader, writer, source, to316);
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
                writeParticleData316(reader, writer, source, particle, to316);
            }
            default -> throw new IllegalArgumentException("未知のメタデータ型です: " + type);
        }
    }
    /** 粒子のデータ部（316 の粒子 ID で形が決まる）を境界形式に付け替える。 */
    private static void writeParticleData316(PacketIO.Reader reader, PacketIO.Writer writer,
            byte[] source, int particle316, boolean to316) {
        switch (particle316) {
            case 37, 38, 46 -> writer.writeVarInt(reader.readVarInt()); // block 系は varint 1個
            case 30 -> { // reddust：実ワイヤはデータ無し
                if (to316) {
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
                if (to316) {
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
    private static void writeAdvancements316(PacketIO.Reader reader, PacketIO.Writer writer,
            byte[] source, boolean to316) {
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
                writeSlot316(reader, writer, source, to316); // icon
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
    // ------------------------------------------------------------------
    // 315（1.11）対応
    //
    // 315 の実ワイヤ形式は 315 と完全同一（スロット：itemId i16＋
    // count i8＋itemDamage i16＋NBT、メタデータの型は i8 で
    // 0〜12 のみ）のため、この節は 315 の節を写して番号だけ
    // 315 に差し替えたもの。メタデータ型 13〜16 の落としも
    // 315 と同じ（writeMetadataEntries315）。
    // ------------------------------------------------------------------

    /** スロット1個だけのペイロード変換（ハイブリッド形式 → 315形式）。 */
    public static byte[] slotTo315(byte[] payload) {
        PacketIO.Reader reader = new PacketIO.Reader(payload);
        PacketIO.Writer writer = new PacketIO.Writer();
        writeSlotAs315(reader, writer, payload);
        return writer.bytes();
    }

    /** スロット1個だけのペイロード変換（315形式 → ハイブリッド形式）。 */
    public static byte[] slotFrom315(byte[] payload) {
        PacketIO.Reader reader = new PacketIO.Reader(payload);
        PacketIO.Writer writer = new PacketIO.Writer();
        writeSlotAsHybridFrom315(reader, writer, payload);
        return writer.bytes();
    }

    /** ハイブリッド形式のスロットを読み、315形式で書く。 */
    static void writeSlotAs315(PacketIO.Reader reader, PacketIO.Writer writer, byte[] source) {
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
            LOG.warning("315のスロットに収まらないアイテムID " + item
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

    /** 315形式のスロットを読み、ハイブリッド形式で書く。 */
    static void writeSlotAsHybridFrom315(PacketIO.Reader reader, PacketIO.Writer writer,
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

    private static void writeSlot315(PacketIO.Reader reader, PacketIO.Writer writer,
            byte[] source, boolean to315) {
        if (to315) {
            writeSlotAs315(reader, writer, source);
        } else {
            writeSlotAsHybridFrom315(reader, writer, source);
        }
    }

    /**
     * クライアント行きパケットをハイブリッド形式から 315形式へ付け替える。
     * スロットを含まないパケットはそのまま返す。
     */
    public static MinecraftPacket clientboundTo315(MinecraftPacket packet) {
        return rewriteClientbound315(packet, true);
    }

    /**
     * クライアント行きパケットを 315形式からハイブリッド形式へ付け替える。
     * スロットを含まないパケットはそのまま返す。
     */
    public static MinecraftPacket clientboundFrom315(MinecraftPacket packet) {
        return rewriteClientbound315(packet, false);
    }

    private static MinecraftPacket rewriteClientbound315(MinecraftPacket packet, boolean to315) {
        PacketIds.PacketName name = PacketIds.nameFor(packet.id(), PacketIds.State.PLAY,
                PacketIds.Direction.CLIENTBOUND, ProtocolVersion.V1_11).orElse(null);
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
                        writeSlot315(reader, writer, payload, to315);
                    }
                }
                case PLAY_SET_SLOT -> {
                    writer.writeByte(reader.readByte()); // windowId
                    writer.writeShort(reader.readShort()); // slot
                    writeSlot315(reader, writer, payload, to315);
                }
                case PLAY_ENTITY_EQUIPMENT -> {
                    writer.writeVarInt(reader.readVarInt()); // entityId
                    writer.writeVarInt(reader.readVarInt()); // slot
                    writeSlot315(reader, writer, payload, to315);
                }
                case PLAY_ENTITY_METADATA -> {
                    writer.writeVarInt(reader.readVarInt()); // entityId
                    writeMetadataEntries315(reader, writer, payload, to315);
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
                    writeMetadataEntries315(reader, writer, payload, to315);
                }
                case PLAY_NAMED_ENTITY_SPAWN -> {
                    writer.writeVarInt(reader.readVarInt()); // entityId
                    writer.writeBytes(reader.readBytes(16)); // uuid
                    writer.writeDouble(reader.readDouble());
                    writer.writeDouble(reader.readDouble());
                    writer.writeDouble(reader.readDouble());
                    writer.writeByte(reader.readByte()); // yaw
                    writer.writeByte(reader.readByte()); // pitch
                    writeMetadataEntries315(reader, writer, payload, to315);
                }
                case PLAY_WORLD_PARTICLES -> {
                    int particle;
                    if (to315) {
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
                    writeParticleData315(reader, writer, payload, particle, to315);
                }
                case PLAY_ADVANCEMENTS -> writeAdvancements315(reader, writer, payload, to315);
                default -> {
                    return packet;
                }
            }
            requireEnd(reader, name);
            return new MinecraftPacket(packet.id(), writer.bytes());
        } catch (IllegalArgumentException e) {
            LOG.warning("315のスロット付け替えができなかったため素通しします（"
                    + name + "）: " + e.getMessage());
            return packet;
        }
    }

    /**
     * サーバー行きパケットを 315形式からハイブリッド形式へ付け替える。
     * スロットを含まないパケットはそのまま返す。
     */
    public static MinecraftPacket serverboundFrom315(MinecraftPacket packet) {
        PacketIds.PacketName name = PacketIds.nameFor(packet.id(), PacketIds.State.PLAY,
                PacketIds.Direction.SERVERBOUND, ProtocolVersion.V1_11).orElse(null);
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
                    writer.writeByte(reader.readByte()); // mode（315 は i8）
                    writeSlotAsHybridFrom315(reader, writer, payload); // clickedItem
                }
                case PLAY_SET_CREATIVE_SLOT -> {
                    writer.writeShort(reader.readShort()); // slot
                    writeSlotAsHybridFrom315(reader, writer, payload);
                }
                default -> {
                    return packet;
                }
            }
            requireEnd(reader, name);
            return new MinecraftPacket(packet.id(), writer.bytes());
        } catch (IllegalArgumentException e) {
            LOG.warning("315のスロット付け替えができなかったため素通しします（"
                    + name + "）: " + e.getMessage());
            return packet;
        }
    }

    /**
     * サーバー行きパケット（ハイブリッド形式）のスロットを 315 の実ワイヤ
     * 形式へ付け替える。スロットを含まないパケットはそのまま返す。
     * （監査で新設：従来は ProxySession のサーバー行き分岐が
     * clientboundTo315 を流用しており、名前解決が CLIENTBOUND 表になる
     * ため Window Click／Set Creative Slot が誤解決され、スロットが
     * ハイブリッド形式のままバックエンドへ届いていた。）
     */
    public static MinecraftPacket serverboundTo315(MinecraftPacket packet) {
        PacketIds.PacketName name = PacketIds.nameFor(packet.id(), PacketIds.State.PLAY,
                PacketIds.Direction.SERVERBOUND, ProtocolVersion.V1_11).orElse(null);
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
                    writer.writeByte(reader.readByte()); // mode（315 は i8）
                    writeSlotAs315(reader, writer, payload); // clickedItem
                }
                case PLAY_SET_CREATIVE_SLOT -> {
                    writer.writeShort(reader.readShort()); // slot
                    writeSlotAs315(reader, writer, payload);
                }
                default -> {
                    return packet;
                }
            }
            requireEnd(reader, name);
            return new MinecraftPacket(packet.id(), writer.bytes());
        } catch (IllegalArgumentException e) {
            LOG.warning("315のスロット付け替えができなかったため素通しします（"
                    + name + "）: " + e.getMessage());
            return packet;
        }
    }

    /** メタデータのエントリ列を付け替える（型は i8⇄varint、番号は 315 のまま）。 */
    private static void writeMetadataEntries315(PacketIO.Reader reader, PacketIO.Writer writer,
            byte[] source, boolean to315) {
        while (true) {
            int index = reader.readUnsignedByte();
            if (index == 0xFF) {
                writer.writeUnsignedByte(0xFF);
                return;
            }
            int type;
            if (to315) {
                type = reader.readVarInt();
            } else {
                type = reader.readUnsignedByte();
            }
            if (to315 && type >= 13 && type <= 16) {
                // 315 に無い型（13 blockid・14 optblockid・15 nbt・
                // 16 particle）は値だけ読み捨てて落とす。
                writeMetadataValue315(reader, new PacketIO.Writer(),
                        source, type, true);
                continue;
            }
            writer.writeUnsignedByte(index);
            if (to315) {
                writer.writeByte(type);
            } else {
                writer.writeVarInt(type);
            }
            writeMetadataValue315(reader, writer, source, type, to315);
        }
    }

    /** メタデータの値1個を付け替える（型番号は世代内番号のまま）。 */
    private static void writeMetadataValue315(PacketIO.Reader reader, PacketIO.Writer writer,
            byte[] source, int type, boolean to315) {
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
            case 6 -> writeSlot315(reader, writer, source, to315);
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
                writeParticleData315(reader, writer, source, particle, to315);
            }
            default -> throw new IllegalArgumentException("未知のメタデータ型です: " + type);
        }
    }
    /** 粒子のデータ部（315 の粒子 ID で形が決まる）を境界形式に付け替える。 */
    private static void writeParticleData315(PacketIO.Reader reader, PacketIO.Writer writer,
            byte[] source, int particle315, boolean to315) {
        switch (particle315) {
            case 37, 38, 46 -> writer.writeVarInt(reader.readVarInt()); // block 系は varint 1個
            case 30 -> { // reddust：実ワイヤはデータ無し
                if (to315) {
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
                if (to315) {
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
    private static void writeAdvancements315(PacketIO.Reader reader, PacketIO.Writer writer,
            byte[] source, boolean to315) {
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
                writeSlot315(reader, writer, source, to315); // icon
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
    // ------------------------------------------------------------------
    // 210（1.10.2）対応
    //
    // 210 のスロット形式は 210 と完全同一（itemId i16＋count i8＋
    // itemDamage i16＋NBT、メタデータの型は i8 で 0〜12 のみ）の
    // ため、この節は 210 の節を写して番号だけ 210 に差し替えた
    // もの。メタデータ型 13〜16 の落としも 210 と同じ
    // （writeMetadataEntries210）。
    // ------------------------------------------------------------------

    /** スロット1個だけのペイロード変換（ハイブリッド形式 → 210形式）。 */
    public static byte[] slotTo210(byte[] payload) {
        PacketIO.Reader reader = new PacketIO.Reader(payload);
        PacketIO.Writer writer = new PacketIO.Writer();
        writeSlotAs210(reader, writer, payload);
        return writer.bytes();
    }

    /** スロット1個だけのペイロード変換（210形式 → ハイブリッド形式）。 */
    public static byte[] slotFrom210(byte[] payload) {
        PacketIO.Reader reader = new PacketIO.Reader(payload);
        PacketIO.Writer writer = new PacketIO.Writer();
        writeSlotAsHybridFrom210(reader, writer, payload);
        return writer.bytes();
    }

    /** ハイブリッド形式のスロットを読み、210形式で書く。 */
    static void writeSlotAs210(PacketIO.Reader reader, PacketIO.Writer writer, byte[] source) {
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
            LOG.warning("210のスロットに収まらないアイテムID " + item
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

    /** 210形式のスロットを読み、ハイブリッド形式で書く。 */
    static void writeSlotAsHybridFrom210(PacketIO.Reader reader, PacketIO.Writer writer,
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

    private static void writeSlot210(PacketIO.Reader reader, PacketIO.Writer writer,
            byte[] source, boolean to210) {
        if (to210) {
            writeSlotAs210(reader, writer, source);
        } else {
            writeSlotAsHybridFrom210(reader, writer, source);
        }
    }

    /**
     * クライアント行きパケットをハイブリッド形式から 210形式へ付け替える。
     * スロットを含まないパケットはそのまま返す。
     */
    public static MinecraftPacket clientboundTo210(MinecraftPacket packet) {
        return rewriteClientbound210(packet, true);
    }

    /**
     * クライアント行きパケットを 210形式からハイブリッド形式へ付け替える。
     * スロットを含まないパケットはそのまま返す。
     */
    public static MinecraftPacket clientboundFrom210(MinecraftPacket packet) {
        return rewriteClientbound210(packet, false);
    }

    private static MinecraftPacket rewriteClientbound210(MinecraftPacket packet, boolean to210) {
        PacketIds.PacketName name = PacketIds.nameFor(packet.id(), PacketIds.State.PLAY,
                PacketIds.Direction.CLIENTBOUND, ProtocolVersion.V1_10_2).orElse(null);
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
                        writeSlot210(reader, writer, payload, to210);
                    }
                }
                case PLAY_SET_SLOT -> {
                    writer.writeByte(reader.readByte()); // windowId
                    writer.writeShort(reader.readShort()); // slot
                    writeSlot210(reader, writer, payload, to210);
                }
                case PLAY_ENTITY_EQUIPMENT -> {
                    writer.writeVarInt(reader.readVarInt()); // entityId
                    writer.writeVarInt(reader.readVarInt()); // slot
                    writeSlot210(reader, writer, payload, to210);
                }
                case PLAY_ENTITY_METADATA -> {
                    writer.writeVarInt(reader.readVarInt()); // entityId
                    writeMetadataEntries210(reader, writer, payload, to210);
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
                    writeMetadataEntries210(reader, writer, payload, to210);
                }
                case PLAY_NAMED_ENTITY_SPAWN -> {
                    writer.writeVarInt(reader.readVarInt()); // entityId
                    writer.writeBytes(reader.readBytes(16)); // uuid
                    writer.writeDouble(reader.readDouble());
                    writer.writeDouble(reader.readDouble());
                    writer.writeDouble(reader.readDouble());
                    writer.writeByte(reader.readByte()); // yaw
                    writer.writeByte(reader.readByte()); // pitch
                    writeMetadataEntries210(reader, writer, payload, to210);
                }
                case PLAY_WORLD_PARTICLES -> {
                    int particle;
                    if (to210) {
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
                    writeParticleData210(reader, writer, payload, particle, to210);
                }
                case PLAY_ADVANCEMENTS -> writeAdvancements210(reader, writer, payload, to210);
                default -> {
                    return packet;
                }
            }
            requireEnd(reader, name);
            return new MinecraftPacket(packet.id(), writer.bytes());
        } catch (IllegalArgumentException e) {
            LOG.warning("210のスロット付け替えができなかったため素通しします（"
                    + name + "）: " + e.getMessage());
            return packet;
        }
    }

    /**
     * サーバー行きパケットを 210形式からハイブリッド形式へ付け替える。
     * スロットを含まないパケットはそのまま返す。
     */
    public static MinecraftPacket serverboundFrom210(MinecraftPacket packet) {
        PacketIds.PacketName name = PacketIds.nameFor(packet.id(), PacketIds.State.PLAY,
                PacketIds.Direction.SERVERBOUND, ProtocolVersion.V1_10_2).orElse(null);
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
                    writer.writeByte(reader.readByte()); // mode（210 は i8）
                    writeSlotAsHybridFrom210(reader, writer, payload); // clickedItem
                }
                case PLAY_SET_CREATIVE_SLOT -> {
                    writer.writeShort(reader.readShort()); // slot
                    writeSlotAsHybridFrom210(reader, writer, payload);
                }
                default -> {
                    return packet;
                }
            }
            requireEnd(reader, name);
            return new MinecraftPacket(packet.id(), writer.bytes());
        } catch (IllegalArgumentException e) {
            LOG.warning("210のスロット付け替えができなかったため素通しします（"
                    + name + "）: " + e.getMessage());
            return packet;
        }
    }

    /**
     * サーバー行きパケット（ハイブリッド形式）のスロットを 210 の実ワイヤ
     * 形式へ付け替える。スロットを含まないパケットはそのまま返す。
     * （監査で新設：従来は ProxySession のサーバー行き分岐が
     * clientboundTo210 を流用しており、名前解決が CLIENTBOUND 表になる
     * ため Window Click／Set Creative Slot が誤解決され、スロットが
     * ハイブリッド形式のままバックエンドへ届いていた。）
     */
    public static MinecraftPacket serverboundTo210(MinecraftPacket packet) {
        PacketIds.PacketName name = PacketIds.nameFor(packet.id(), PacketIds.State.PLAY,
                PacketIds.Direction.SERVERBOUND, ProtocolVersion.V1_10_2).orElse(null);
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
                    writer.writeByte(reader.readByte()); // mode（210 は i8）
                    writeSlotAs210(reader, writer, payload); // clickedItem
                }
                case PLAY_SET_CREATIVE_SLOT -> {
                    writer.writeShort(reader.readShort()); // slot
                    writeSlotAs210(reader, writer, payload);
                }
                default -> {
                    return packet;
                }
            }
            requireEnd(reader, name);
            return new MinecraftPacket(packet.id(), writer.bytes());
        } catch (IllegalArgumentException e) {
            LOG.warning("210のスロット付け替えができなかったため素通しします（"
                    + name + "）: " + e.getMessage());
            return packet;
        }
    }

    /** メタデータのエントリ列を付け替える（型は i8⇄varint、番号は 210 のまま）。 */
    private static void writeMetadataEntries210(PacketIO.Reader reader, PacketIO.Writer writer,
            byte[] source, boolean to210) {
        while (true) {
            int index = reader.readUnsignedByte();
            if (index == 0xFF) {
                writer.writeUnsignedByte(0xFF);
                return;
            }
            int type;
            if (to210) {
                type = reader.readVarInt();
            } else {
                type = reader.readUnsignedByte();
            }
            if (to210 && type >= 13 && type <= 16) {
                // 210 に無い型（13 blockid・14 optblockid・15 nbt・
                // 16 particle）は値だけ読み捨てて落とす。
                writeMetadataValue210(reader, new PacketIO.Writer(),
                        source, type, true);
                continue;
            }
            writer.writeUnsignedByte(index);
            if (to210) {
                writer.writeByte(type);
            } else {
                writer.writeVarInt(type);
            }
            writeMetadataValue210(reader, writer, source, type, to210);
        }
    }

    /** メタデータの値1個を付け替える（型番号は世代内番号のまま）。 */
    private static void writeMetadataValue210(PacketIO.Reader reader, PacketIO.Writer writer,
            byte[] source, int type, boolean to210) {
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
            case 6 -> writeSlot210(reader, writer, source, to210);
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
                writeParticleData210(reader, writer, source, particle, to210);
            }
            default -> throw new IllegalArgumentException("未知のメタデータ型です: " + type);
        }
    }
    /** 粒子のデータ部（210 の粒子 ID で形が決まる）を境界形式に付け替える。 */
    private static void writeParticleData210(PacketIO.Reader reader, PacketIO.Writer writer,
            byte[] source, int particle210, boolean to210) {
        switch (particle210) {
            case 37, 38, 46 -> writer.writeVarInt(reader.readVarInt()); // block 系は varint 1個
            case 30 -> { // reddust：実ワイヤはデータ無し
                if (to210) {
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
                if (to210) {
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
    private static void writeAdvancements210(PacketIO.Reader reader, PacketIO.Writer writer,
            byte[] source, boolean to210) {
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
                writeSlot210(reader, writer, source, to210); // icon
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
    // ------------------------------------------------------------------
    // 110（1.9.4）対応
    //
    // 110 のスロット形式は 110 と完全同一（itemId i16＋count i8＋
    // itemDamage i16＋NBT、メタデータの型は i8 で 0〜12 のみ）の
    // ため、この節は 110 の節を写して番号だけ 110 に差し替えた
    // もの。メタデータ型 13〜16 の落としも 110 と同じ
    // （writeMetadataEntries110）。
    // ------------------------------------------------------------------

    /** スロット1個だけのペイロード変換（ハイブリッド形式 → 110形式）。 */
    public static byte[] slotTo110(byte[] payload) {
        PacketIO.Reader reader = new PacketIO.Reader(payload);
        PacketIO.Writer writer = new PacketIO.Writer();
        writeSlotAs110(reader, writer, payload);
        return writer.bytes();
    }

    /** スロット1個だけのペイロード変換（110形式 → ハイブリッド形式）。 */
    public static byte[] slotFrom110(byte[] payload) {
        PacketIO.Reader reader = new PacketIO.Reader(payload);
        PacketIO.Writer writer = new PacketIO.Writer();
        writeSlotAsHybridFrom110(reader, writer, payload);
        return writer.bytes();
    }

    /** ハイブリッド形式のスロットを読み、110形式で書く。 */
    static void writeSlotAs110(PacketIO.Reader reader, PacketIO.Writer writer, byte[] source) {
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
            LOG.warning("110のスロットに収まらないアイテムID " + item
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

    /** 110形式のスロットを読み、ハイブリッド形式で書く。 */
    static void writeSlotAsHybridFrom110(PacketIO.Reader reader, PacketIO.Writer writer,
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

    private static void writeSlot110(PacketIO.Reader reader, PacketIO.Writer writer,
            byte[] source, boolean to110) {
        if (to110) {
            writeSlotAs110(reader, writer, source);
        } else {
            writeSlotAsHybridFrom110(reader, writer, source);
        }
    }

    /**
     * クライアント行きパケットをハイブリッド形式から 110形式へ付け替える。
     * スロットを含まないパケットはそのまま返す。
     */
    public static MinecraftPacket clientboundTo110(MinecraftPacket packet) {
        return rewriteClientbound110(packet, true);
    }

    /**
     * クライアント行きパケットを 110形式からハイブリッド形式へ付け替える。
     * スロットを含まないパケットはそのまま返す。
     */
    public static MinecraftPacket clientboundFrom110(MinecraftPacket packet) {
        return rewriteClientbound110(packet, false);
    }

    private static MinecraftPacket rewriteClientbound110(MinecraftPacket packet, boolean to110) {
        PacketIds.PacketName name = PacketIds.nameFor(packet.id(), PacketIds.State.PLAY,
                PacketIds.Direction.CLIENTBOUND, ProtocolVersion.V1_9_4).orElse(null);
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
                        writeSlot110(reader, writer, payload, to110);
                    }
                }
                case PLAY_SET_SLOT -> {
                    writer.writeByte(reader.readByte()); // windowId
                    writer.writeShort(reader.readShort()); // slot
                    writeSlot110(reader, writer, payload, to110);
                }
                case PLAY_ENTITY_EQUIPMENT -> {
                    writer.writeVarInt(reader.readVarInt()); // entityId
                    writer.writeVarInt(reader.readVarInt()); // slot
                    writeSlot110(reader, writer, payload, to110);
                }
                case PLAY_ENTITY_METADATA -> {
                    writer.writeVarInt(reader.readVarInt()); // entityId
                    writeMetadataEntries110(reader, writer, payload, to110);
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
                    writeMetadataEntries110(reader, writer, payload, to110);
                }
                case PLAY_NAMED_ENTITY_SPAWN -> {
                    writer.writeVarInt(reader.readVarInt()); // entityId
                    writer.writeBytes(reader.readBytes(16)); // uuid
                    writer.writeDouble(reader.readDouble());
                    writer.writeDouble(reader.readDouble());
                    writer.writeDouble(reader.readDouble());
                    writer.writeByte(reader.readByte()); // yaw
                    writer.writeByte(reader.readByte()); // pitch
                    writeMetadataEntries110(reader, writer, payload, to110);
                }
                case PLAY_WORLD_PARTICLES -> {
                    int particle;
                    if (to110) {
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
                    writeParticleData110(reader, writer, payload, particle, to110);
                }
                case PLAY_ADVANCEMENTS -> writeAdvancements110(reader, writer, payload, to110);
                default -> {
                    return packet;
                }
            }
            requireEnd(reader, name);
            return new MinecraftPacket(packet.id(), writer.bytes());
        } catch (IllegalArgumentException e) {
            LOG.warning("110のスロット付け替えができなかったため素通しします（"
                    + name + "）: " + e.getMessage());
            return packet;
        }
    }

    /**
     * サーバー行きパケットを 110形式からハイブリッド形式へ付け替える。
     * スロットを含まないパケットはそのまま返す。
     */
    public static MinecraftPacket serverboundFrom110(MinecraftPacket packet) {
        PacketIds.PacketName name = PacketIds.nameFor(packet.id(), PacketIds.State.PLAY,
                PacketIds.Direction.SERVERBOUND, ProtocolVersion.V1_9_4).orElse(null);
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
                    writer.writeByte(reader.readByte()); // mode（110 は i8）
                    writeSlotAsHybridFrom110(reader, writer, payload); // clickedItem
                }
                case PLAY_SET_CREATIVE_SLOT -> {
                    writer.writeShort(reader.readShort()); // slot
                    writeSlotAsHybridFrom110(reader, writer, payload);
                }
                default -> {
                    return packet;
                }
            }
            requireEnd(reader, name);
            return new MinecraftPacket(packet.id(), writer.bytes());
        } catch (IllegalArgumentException e) {
            LOG.warning("110のスロット付け替えができなかったため素通しします（"
                    + name + "）: " + e.getMessage());
            return packet;
        }
    }

    /**
     * サーバー行きパケット（ハイブリッド形式）のスロットを 110 の実ワイヤ
     * 形式へ付け替える。スロットを含まないパケットはそのまま返す。
     * （監査で新設：従来は ProxySession のサーバー行き分岐が
     * clientboundTo110 を流用しており、名前解決が CLIENTBOUND 表になる
     * ため Window Click／Set Creative Slot が誤解決され、スロットが
     * ハイブリッド形式のままバックエンドへ届いていた。）
     */
    public static MinecraftPacket serverboundTo110(MinecraftPacket packet) {
        PacketIds.PacketName name = PacketIds.nameFor(packet.id(), PacketIds.State.PLAY,
                PacketIds.Direction.SERVERBOUND, ProtocolVersion.V1_9_4).orElse(null);
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
                    writer.writeByte(reader.readByte()); // mode（110 は i8）
                    writeSlotAs110(reader, writer, payload); // clickedItem
                }
                case PLAY_SET_CREATIVE_SLOT -> {
                    writer.writeShort(reader.readShort()); // slot
                    writeSlotAs110(reader, writer, payload);
                }
                default -> {
                    return packet;
                }
            }
            requireEnd(reader, name);
            return new MinecraftPacket(packet.id(), writer.bytes());
        } catch (IllegalArgumentException e) {
            LOG.warning("110のスロット付け替えができなかったため素通しします（"
                    + name + "）: " + e.getMessage());
            return packet;
        }
    }

    /** メタデータのエントリ列を付け替える（型は i8⇄varint、番号は 110 のまま）。 */
    private static void writeMetadataEntries110(PacketIO.Reader reader, PacketIO.Writer writer,
            byte[] source, boolean to110) {
        while (true) {
            int index = reader.readUnsignedByte();
            if (index == 0xFF) {
                writer.writeUnsignedByte(0xFF);
                return;
            }
            int type;
            if (to110) {
                type = reader.readVarInt();
            } else {
                type = reader.readUnsignedByte();
            }
            if (to110 && type >= 13 && type <= 16) {
                // 110 に無い型（13 blockid・14 optblockid・15 nbt・
                // 16 particle）は値だけ読み捨てて落とす。
                writeMetadataValue110(reader, new PacketIO.Writer(),
                        source, type, true);
                continue;
            }
            writer.writeUnsignedByte(index);
            if (to110) {
                writer.writeByte(type);
            } else {
                writer.writeVarInt(type);
            }
            writeMetadataValue110(reader, writer, source, type, to110);
        }
    }

    /** メタデータの値1個を付け替える（型番号は世代内番号のまま）。 */
    private static void writeMetadataValue110(PacketIO.Reader reader, PacketIO.Writer writer,
            byte[] source, int type, boolean to110) {
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
            case 6 -> writeSlot110(reader, writer, source, to110);
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
                writeParticleData110(reader, writer, source, particle, to110);
            }
            default -> throw new IllegalArgumentException("未知のメタデータ型です: " + type);
        }
    }
    /** 粒子のデータ部（110 の粒子 ID で形が決まる）を境界形式に付け替える。 */
    private static void writeParticleData110(PacketIO.Reader reader, PacketIO.Writer writer,
            byte[] source, int particle110, boolean to110) {
        switch (particle110) {
            case 37, 38, 46 -> writer.writeVarInt(reader.readVarInt()); // block 系は varint 1個
            case 30 -> { // reddust：実ワイヤはデータ無し
                if (to110) {
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
                if (to110) {
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
    private static void writeAdvancements110(PacketIO.Reader reader, PacketIO.Writer writer,
            byte[] source, boolean to110) {
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
                writeSlot110(reader, writer, source, to110); // icon
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
    // ------------------------------------------------------------------
    // 109（1.9.2）対応
    //
    // 109 のスロット形式は 109 と完全同一（itemId i16＋count i8＋
    // itemDamage i16＋NBT、メタデータの型は i8 で 0〜12 のみ）の
    // ため、この節は 109 の節を写して番号だけ 109 に差し替えた
    // もの。メタデータ型 13〜16 の落としも 109 と同じ
    // （writeMetadataEntries109）。パケット名の引き直しは
    // PacketIds の 109 列（update_sign 分のずれ込み）を使う。
    // ------------------------------------------------------------------

    /** スロット1個だけのペイロード変換（ハイブリッド形式 → 109形式）。 */
    public static byte[] slotTo109(byte[] payload) {
        PacketIO.Reader reader = new PacketIO.Reader(payload);
        PacketIO.Writer writer = new PacketIO.Writer();
        writeSlotAs109(reader, writer, payload);
        return writer.bytes();
    }

    /** スロット1個だけのペイロード変換（109形式 → ハイブリッド形式）。 */
    public static byte[] slotFrom109(byte[] payload) {
        PacketIO.Reader reader = new PacketIO.Reader(payload);
        PacketIO.Writer writer = new PacketIO.Writer();
        writeSlotAsHybridFrom109(reader, writer, payload);
        return writer.bytes();
    }

    /** ハイブリッド形式のスロットを読み、109形式で書く。 */
    static void writeSlotAs109(PacketIO.Reader reader, PacketIO.Writer writer, byte[] source) {
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
            LOG.warning("109のスロットに収まらないアイテムID " + item
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

    /** 109形式のスロットを読み、ハイブリッド形式で書く。 */
    static void writeSlotAsHybridFrom109(PacketIO.Reader reader, PacketIO.Writer writer,
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

    private static void writeSlot109(PacketIO.Reader reader, PacketIO.Writer writer,
            byte[] source, boolean to109) {
        if (to109) {
            writeSlotAs109(reader, writer, source);
        } else {
            writeSlotAsHybridFrom109(reader, writer, source);
        }
    }

    /**
     * クライアント行きパケットをハイブリッド形式から 109形式へ付け替える。
     * スロットを含まないパケットはそのまま返す。
     */
    public static MinecraftPacket clientboundTo109(MinecraftPacket packet) {
        return rewriteClientbound109(packet, true);
    }

    /**
     * クライアント行きパケットを 109形式からハイブリッド形式へ付け替える。
     * スロットを含まないパケットはそのまま返す。
     */
    public static MinecraftPacket clientboundFrom109(MinecraftPacket packet) {
        return rewriteClientbound109(packet, false);
    }

    private static MinecraftPacket rewriteClientbound109(MinecraftPacket packet, boolean to109) {
        PacketIds.PacketName name = PacketIds.nameFor(packet.id(), PacketIds.State.PLAY,
                PacketIds.Direction.CLIENTBOUND, ProtocolVersion.V1_9_2).orElse(null);
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
                        writeSlot109(reader, writer, payload, to109);
                    }
                }
                case PLAY_SET_SLOT -> {
                    writer.writeByte(reader.readByte()); // windowId
                    writer.writeShort(reader.readShort()); // slot
                    writeSlot109(reader, writer, payload, to109);
                }
                case PLAY_ENTITY_EQUIPMENT -> {
                    writer.writeVarInt(reader.readVarInt()); // entityId
                    writer.writeVarInt(reader.readVarInt()); // slot
                    writeSlot109(reader, writer, payload, to109);
                }
                case PLAY_ENTITY_METADATA -> {
                    writer.writeVarInt(reader.readVarInt()); // entityId
                    writeMetadataEntries109(reader, writer, payload, to109);
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
                    writeMetadataEntries109(reader, writer, payload, to109);
                }
                case PLAY_NAMED_ENTITY_SPAWN -> {
                    writer.writeVarInt(reader.readVarInt()); // entityId
                    writer.writeBytes(reader.readBytes(16)); // uuid
                    writer.writeDouble(reader.readDouble());
                    writer.writeDouble(reader.readDouble());
                    writer.writeDouble(reader.readDouble());
                    writer.writeByte(reader.readByte()); // yaw
                    writer.writeByte(reader.readByte()); // pitch
                    writeMetadataEntries109(reader, writer, payload, to109);
                }
                case PLAY_WORLD_PARTICLES -> {
                    int particle;
                    if (to109) {
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
                    writeParticleData109(reader, writer, payload, particle, to109);
                }
                case PLAY_ADVANCEMENTS -> writeAdvancements109(reader, writer, payload, to109);
                default -> {
                    return packet;
                }
            }
            requireEnd(reader, name);
            return new MinecraftPacket(packet.id(), writer.bytes());
        } catch (IllegalArgumentException e) {
            LOG.warning("109のスロット付け替えができなかったため素通しします（"
                    + name + "）: " + e.getMessage());
            return packet;
        }
    }

    /**
     * サーバー行きパケットを 109形式からハイブリッド形式へ付け替える。
     * スロットを含まないパケットはそのまま返す。
     */
    public static MinecraftPacket serverboundFrom109(MinecraftPacket packet) {
        PacketIds.PacketName name = PacketIds.nameFor(packet.id(), PacketIds.State.PLAY,
                PacketIds.Direction.SERVERBOUND, ProtocolVersion.V1_9_2).orElse(null);
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
                    writer.writeByte(reader.readByte()); // mode（109 は i8）
                    writeSlotAsHybridFrom109(reader, writer, payload); // clickedItem
                }
                case PLAY_SET_CREATIVE_SLOT -> {
                    writer.writeShort(reader.readShort()); // slot
                    writeSlotAsHybridFrom109(reader, writer, payload);
                }
                default -> {
                    return packet;
                }
            }
            requireEnd(reader, name);
            return new MinecraftPacket(packet.id(), writer.bytes());
        } catch (IllegalArgumentException e) {
            LOG.warning("109のスロット付け替えができなかったため素通しします（"
                    + name + "）: " + e.getMessage());
            return packet;
        }
    }

    /**
     * サーバー行きパケット（ハイブリッド形式）のスロットを 109 の実ワイヤ
     * 形式へ付け替える。スロットを含まないパケットはそのまま返す。
     * （監査で新設：従来は ProxySession のサーバー行き分岐が
     * clientboundTo109 を流用しており、名前解決が CLIENTBOUND 表になる
     * ため Window Click／Set Creative Slot が誤解決され、スロットが
     * ハイブリッド形式のままバックエンドへ届いていた。）
     */
    public static MinecraftPacket serverboundTo109(MinecraftPacket packet) {
        PacketIds.PacketName name = PacketIds.nameFor(packet.id(), PacketIds.State.PLAY,
                PacketIds.Direction.SERVERBOUND, ProtocolVersion.V1_9_2).orElse(null);
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
                    writer.writeByte(reader.readByte()); // mode（109 は i8）
                    writeSlotAs109(reader, writer, payload); // clickedItem
                }
                case PLAY_SET_CREATIVE_SLOT -> {
                    writer.writeShort(reader.readShort()); // slot
                    writeSlotAs109(reader, writer, payload);
                }
                default -> {
                    return packet;
                }
            }
            requireEnd(reader, name);
            return new MinecraftPacket(packet.id(), writer.bytes());
        } catch (IllegalArgumentException e) {
            LOG.warning("109のスロット付け替えができなかったため素通しします（"
                    + name + "）: " + e.getMessage());
            return packet;
        }
    }

    /** メタデータのエントリ列を付け替える（型は i8⇄varint、番号は 109 のまま）。 */
    private static void writeMetadataEntries109(PacketIO.Reader reader, PacketIO.Writer writer,
            byte[] source, boolean to109) {
        while (true) {
            int index = reader.readUnsignedByte();
            if (index == 0xFF) {
                writer.writeUnsignedByte(0xFF);
                return;
            }
            int type;
            if (to109) {
                type = reader.readVarInt();
            } else {
                type = reader.readUnsignedByte();
            }
            if (to109 && type >= 13 && type <= 16) {
                // 109 に無い型（13 blockid・14 optblockid・15 nbt・
                // 16 particle）は値だけ読み捨てて落とす。
                writeMetadataValue109(reader, new PacketIO.Writer(),
                        source, type, true);
                continue;
            }
            writer.writeUnsignedByte(index);
            if (to109) {
                writer.writeByte(type);
            } else {
                writer.writeVarInt(type);
            }
            writeMetadataValue109(reader, writer, source, type, to109);
        }
    }

    /** メタデータの値1個を付け替える（型番号は世代内番号のまま）。 */
    private static void writeMetadataValue109(PacketIO.Reader reader, PacketIO.Writer writer,
            byte[] source, int type, boolean to109) {
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
            case 6 -> writeSlot109(reader, writer, source, to109);
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
                writeParticleData109(reader, writer, source, particle, to109);
            }
            default -> throw new IllegalArgumentException("未知のメタデータ型です: " + type);
        }
    }
    /** 粒子のデータ部（109 の粒子 ID で形が決まる）を境界形式に付け替える。 */
    private static void writeParticleData109(PacketIO.Reader reader, PacketIO.Writer writer,
            byte[] source, int particle109, boolean to109) {
        switch (particle109) {
            case 37, 38, 46 -> writer.writeVarInt(reader.readVarInt()); // block 系は varint 1個
            case 30 -> { // reddust：実ワイヤはデータ無し
                if (to109) {
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
                if (to109) {
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
    private static void writeAdvancements109(PacketIO.Reader reader, PacketIO.Writer writer,
            byte[] source, boolean to109) {
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
                writeSlot109(reader, writer, source, to109); // icon
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
    // ------------------------------------------------------------------
    // 108（1.9.1）対応
    //
    // 108 のスロット形式は 108 と完全同一（itemId i16＋count i8＋
    // itemDamage i16＋NBT、メタデータの型は i8 で 0〜12 のみ）の
    // ため、この節は 108 の節を写して番号だけ 108 に差し替えた
    // もの。メタデータ型 13〜16 の落としも 108 と同じ
    // （writeMetadataEntries108）。パケット名の引き直しは
    // PacketIds の 108 列（108 列と同値）を使う。
    // ------------------------------------------------------------------

    /** スロット1個だけのペイロード変換（ハイブリッド形式 → 108形式）。 */
    public static byte[] slotTo108(byte[] payload) {
        PacketIO.Reader reader = new PacketIO.Reader(payload);
        PacketIO.Writer writer = new PacketIO.Writer();
        writeSlotAs108(reader, writer, payload);
        return writer.bytes();
    }

    /** スロット1個だけのペイロード変換（108形式 → ハイブリッド形式）。 */
    public static byte[] slotFrom108(byte[] payload) {
        PacketIO.Reader reader = new PacketIO.Reader(payload);
        PacketIO.Writer writer = new PacketIO.Writer();
        writeSlotAsHybridFrom108(reader, writer, payload);
        return writer.bytes();
    }

    /** ハイブリッド形式のスロットを読み、108形式で書く。 */
    static void writeSlotAs108(PacketIO.Reader reader, PacketIO.Writer writer, byte[] source) {
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
            LOG.warning("108のスロットに収まらないアイテムID " + item
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

    /** 108形式のスロットを読み、ハイブリッド形式で書く。 */
    static void writeSlotAsHybridFrom108(PacketIO.Reader reader, PacketIO.Writer writer,
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

    private static void writeSlot108(PacketIO.Reader reader, PacketIO.Writer writer,
            byte[] source, boolean to108) {
        if (to108) {
            writeSlotAs108(reader, writer, source);
        } else {
            writeSlotAsHybridFrom108(reader, writer, source);
        }
    }

    /**
     * クライアント行きパケットをハイブリッド形式から 108形式へ付け替える。
     * スロットを含まないパケットはそのまま返す。
     */
    public static MinecraftPacket clientboundTo108(MinecraftPacket packet) {
        return rewriteClientbound108(packet, true);
    }

    /**
     * クライアント行きパケットを 108形式からハイブリッド形式へ付け替える。
     * スロットを含まないパケットはそのまま返す。
     */
    public static MinecraftPacket clientboundFrom108(MinecraftPacket packet) {
        return rewriteClientbound108(packet, false);
    }

    private static MinecraftPacket rewriteClientbound108(MinecraftPacket packet, boolean to108) {
        PacketIds.PacketName name = PacketIds.nameFor(packet.id(), PacketIds.State.PLAY,
                PacketIds.Direction.CLIENTBOUND, ProtocolVersion.V1_9_1).orElse(null);
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
                        writeSlot108(reader, writer, payload, to108);
                    }
                }
                case PLAY_SET_SLOT -> {
                    writer.writeByte(reader.readByte()); // windowId
                    writer.writeShort(reader.readShort()); // slot
                    writeSlot108(reader, writer, payload, to108);
                }
                case PLAY_ENTITY_EQUIPMENT -> {
                    writer.writeVarInt(reader.readVarInt()); // entityId
                    writer.writeVarInt(reader.readVarInt()); // slot
                    writeSlot108(reader, writer, payload, to108);
                }
                case PLAY_ENTITY_METADATA -> {
                    writer.writeVarInt(reader.readVarInt()); // entityId
                    writeMetadataEntries108(reader, writer, payload, to108);
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
                    writeMetadataEntries108(reader, writer, payload, to108);
                }
                case PLAY_NAMED_ENTITY_SPAWN -> {
                    writer.writeVarInt(reader.readVarInt()); // entityId
                    writer.writeBytes(reader.readBytes(16)); // uuid
                    writer.writeDouble(reader.readDouble());
                    writer.writeDouble(reader.readDouble());
                    writer.writeDouble(reader.readDouble());
                    writer.writeByte(reader.readByte()); // yaw
                    writer.writeByte(reader.readByte()); // pitch
                    writeMetadataEntries108(reader, writer, payload, to108);
                }
                case PLAY_WORLD_PARTICLES -> {
                    int particle;
                    if (to108) {
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
                    writeParticleData108(reader, writer, payload, particle, to108);
                }
                case PLAY_ADVANCEMENTS -> writeAdvancements108(reader, writer, payload, to108);
                default -> {
                    return packet;
                }
            }
            requireEnd(reader, name);
            return new MinecraftPacket(packet.id(), writer.bytes());
        } catch (IllegalArgumentException e) {
            LOG.warning("108のスロット付け替えができなかったため素通しします（"
                    + name + "）: " + e.getMessage());
            return packet;
        }
    }

    /**
     * サーバー行きパケットを 108形式からハイブリッド形式へ付け替える。
     * スロットを含まないパケットはそのまま返す。
     */
    public static MinecraftPacket serverboundFrom108(MinecraftPacket packet) {
        PacketIds.PacketName name = PacketIds.nameFor(packet.id(), PacketIds.State.PLAY,
                PacketIds.Direction.SERVERBOUND, ProtocolVersion.V1_9_1).orElse(null);
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
                    writer.writeByte(reader.readByte()); // mode（108 は i8）
                    writeSlotAsHybridFrom108(reader, writer, payload); // clickedItem
                }
                case PLAY_SET_CREATIVE_SLOT -> {
                    writer.writeShort(reader.readShort()); // slot
                    writeSlotAsHybridFrom108(reader, writer, payload);
                }
                default -> {
                    return packet;
                }
            }
            requireEnd(reader, name);
            return new MinecraftPacket(packet.id(), writer.bytes());
        } catch (IllegalArgumentException e) {
            LOG.warning("108のスロット付け替えができなかったため素通しします（"
                    + name + "）: " + e.getMessage());
            return packet;
        }
    }

    /**
     * サーバー行きパケット（ハイブリッド形式）のスロットを 108 の実ワイヤ
     * 形式へ付け替える。スロットを含まないパケットはそのまま返す。
     * （監査で新設：従来は ProxySession のサーバー行き分岐が
     * clientboundTo108 を流用しており、名前解決が CLIENTBOUND 表になる
     * ため Window Click／Set Creative Slot が誤解決され、スロットが
     * ハイブリッド形式のままバックエンドへ届いていた。）
     */
    public static MinecraftPacket serverboundTo108(MinecraftPacket packet) {
        PacketIds.PacketName name = PacketIds.nameFor(packet.id(), PacketIds.State.PLAY,
                PacketIds.Direction.SERVERBOUND, ProtocolVersion.V1_9_1).orElse(null);
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
                    writer.writeByte(reader.readByte()); // mode（108 は i8）
                    writeSlotAs108(reader, writer, payload); // clickedItem
                }
                case PLAY_SET_CREATIVE_SLOT -> {
                    writer.writeShort(reader.readShort()); // slot
                    writeSlotAs108(reader, writer, payload);
                }
                default -> {
                    return packet;
                }
            }
            requireEnd(reader, name);
            return new MinecraftPacket(packet.id(), writer.bytes());
        } catch (IllegalArgumentException e) {
            LOG.warning("108のスロット付け替えができなかったため素通しします（"
                    + name + "）: " + e.getMessage());
            return packet;
        }
    }

    /** メタデータのエントリ列を付け替える（型は i8⇄varint、番号は 108 のまま）。 */
    private static void writeMetadataEntries108(PacketIO.Reader reader, PacketIO.Writer writer,
            byte[] source, boolean to108) {
        while (true) {
            int index = reader.readUnsignedByte();
            if (index == 0xFF) {
                writer.writeUnsignedByte(0xFF);
                return;
            }
            int type;
            if (to108) {
                type = reader.readVarInt();
            } else {
                type = reader.readUnsignedByte();
            }
            if (to108 && type >= 13 && type <= 16) {
                // 108 に無い型（13 blockid・14 optblockid・15 nbt・
                // 16 particle）は値だけ読み捨てて落とす。
                writeMetadataValue108(reader, new PacketIO.Writer(),
                        source, type, true);
                continue;
            }
            writer.writeUnsignedByte(index);
            if (to108) {
                writer.writeByte(type);
            } else {
                writer.writeVarInt(type);
            }
            writeMetadataValue108(reader, writer, source, type, to108);
        }
    }

    /** メタデータの値1個を付け替える（型番号は世代内番号のまま）。 */
    private static void writeMetadataValue108(PacketIO.Reader reader, PacketIO.Writer writer,
            byte[] source, int type, boolean to108) {
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
            case 6 -> writeSlot108(reader, writer, source, to108);
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
                writeParticleData108(reader, writer, source, particle, to108);
            }
            default -> throw new IllegalArgumentException("未知のメタデータ型です: " + type);
        }
    }
    /** 粒子のデータ部（108 の粒子 ID で形が決まる）を境界形式に付け替える。 */
    private static void writeParticleData108(PacketIO.Reader reader, PacketIO.Writer writer,
            byte[] source, int particle108, boolean to108) {
        switch (particle108) {
            case 37, 38, 46 -> writer.writeVarInt(reader.readVarInt()); // block 系は varint 1個
            case 30 -> { // reddust：実ワイヤはデータ無し
                if (to108) {
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
                if (to108) {
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
    private static void writeAdvancements108(PacketIO.Reader reader, PacketIO.Writer writer,
            byte[] source, boolean to108) {
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
                writeSlot108(reader, writer, source, to108); // icon
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
    // ------------------------------------------------------------------
    // 107（1.9）対応
    //
    // 107 のスロット形式は 107 と完全同一（itemId i16＋count i8＋
    // itemDamage i16＋NBT、メタデータの型は i8 で 0〜12 のみ）の
    // ため、この節は 107 の節を写して番号だけ 107 に差し替えた
    // もの。メタデータ型 13〜16 の落としも 107 と同じ
    // （writeMetadataEntries107）。パケット名の引き直しは
    // PacketIds の 107 列（107 列と同値）を使う。
    // ------------------------------------------------------------------

    /** スロット1個だけのペイロード変換（ハイブリッド形式 → 107形式）。 */
    public static byte[] slotTo107(byte[] payload) {
        PacketIO.Reader reader = new PacketIO.Reader(payload);
        PacketIO.Writer writer = new PacketIO.Writer();
        writeSlotAs107(reader, writer, payload);
        return writer.bytes();
    }

    /** スロット1個だけのペイロード変換（107形式 → ハイブリッド形式）。 */
    public static byte[] slotFrom107(byte[] payload) {
        PacketIO.Reader reader = new PacketIO.Reader(payload);
        PacketIO.Writer writer = new PacketIO.Writer();
        writeSlotAsHybridFrom107(reader, writer, payload);
        return writer.bytes();
    }

    /** ハイブリッド形式のスロットを読み、107形式で書く。 */
    static void writeSlotAs107(PacketIO.Reader reader, PacketIO.Writer writer, byte[] source) {
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
            LOG.warning("107のスロットに収まらないアイテムID " + item
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

    /** 107形式のスロットを読み、ハイブリッド形式で書く。 */
    static void writeSlotAsHybridFrom107(PacketIO.Reader reader, PacketIO.Writer writer,
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

    private static void writeSlot107(PacketIO.Reader reader, PacketIO.Writer writer,
            byte[] source, boolean to107) {
        if (to107) {
            writeSlotAs107(reader, writer, source);
        } else {
            writeSlotAsHybridFrom107(reader, writer, source);
        }
    }

    /**
     * クライアント行きパケットをハイブリッド形式から 107形式へ付け替える。
     * スロットを含まないパケットはそのまま返す。
     */
    public static MinecraftPacket clientboundTo107(MinecraftPacket packet) {
        return rewriteClientbound107(packet, true);
    }

    /**
     * クライアント行きパケットを 107形式からハイブリッド形式へ付け替える。
     * スロットを含まないパケットはそのまま返す。
     */
    public static MinecraftPacket clientboundFrom107(MinecraftPacket packet) {
        return rewriteClientbound107(packet, false);
    }

    private static MinecraftPacket rewriteClientbound107(MinecraftPacket packet, boolean to107) {
        PacketIds.PacketName name = PacketIds.nameFor(packet.id(), PacketIds.State.PLAY,
                PacketIds.Direction.CLIENTBOUND, ProtocolVersion.V1_9).orElse(null);
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
                        writeSlot107(reader, writer, payload, to107);
                    }
                }
                case PLAY_SET_SLOT -> {
                    writer.writeByte(reader.readByte()); // windowId
                    writer.writeShort(reader.readShort()); // slot
                    writeSlot107(reader, writer, payload, to107);
                }
                case PLAY_ENTITY_EQUIPMENT -> {
                    writer.writeVarInt(reader.readVarInt()); // entityId
                    writer.writeVarInt(reader.readVarInt()); // slot
                    writeSlot107(reader, writer, payload, to107);
                }
                case PLAY_ENTITY_METADATA -> {
                    writer.writeVarInt(reader.readVarInt()); // entityId
                    writeMetadataEntries107(reader, writer, payload, to107);
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
                    writeMetadataEntries107(reader, writer, payload, to107);
                }
                case PLAY_NAMED_ENTITY_SPAWN -> {
                    writer.writeVarInt(reader.readVarInt()); // entityId
                    writer.writeBytes(reader.readBytes(16)); // uuid
                    writer.writeDouble(reader.readDouble());
                    writer.writeDouble(reader.readDouble());
                    writer.writeDouble(reader.readDouble());
                    writer.writeByte(reader.readByte()); // yaw
                    writer.writeByte(reader.readByte()); // pitch
                    writeMetadataEntries107(reader, writer, payload, to107);
                }
                case PLAY_WORLD_PARTICLES -> {
                    int particle;
                    if (to107) {
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
                    writeParticleData107(reader, writer, payload, particle, to107);
                }
                case PLAY_ADVANCEMENTS -> writeAdvancements107(reader, writer, payload, to107);
                default -> {
                    return packet;
                }
            }
            requireEnd(reader, name);
            return new MinecraftPacket(packet.id(), writer.bytes());
        } catch (IllegalArgumentException e) {
            LOG.warning("107のスロット付け替えができなかったため素通しします（"
                    + name + "）: " + e.getMessage());
            return packet;
        }
    }

    /**
     * サーバー行きパケットを 107形式からハイブリッド形式へ付け替える。
     * スロットを含まないパケットはそのまま返す。
     */
    public static MinecraftPacket serverboundFrom107(MinecraftPacket packet) {
        PacketIds.PacketName name = PacketIds.nameFor(packet.id(), PacketIds.State.PLAY,
                PacketIds.Direction.SERVERBOUND, ProtocolVersion.V1_9).orElse(null);
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
                    writer.writeByte(reader.readByte()); // mode（107 は i8）
                    writeSlotAsHybridFrom107(reader, writer, payload); // clickedItem
                }
                case PLAY_SET_CREATIVE_SLOT -> {
                    writer.writeShort(reader.readShort()); // slot
                    writeSlotAsHybridFrom107(reader, writer, payload);
                }
                default -> {
                    return packet;
                }
            }
            requireEnd(reader, name);
            return new MinecraftPacket(packet.id(), writer.bytes());
        } catch (IllegalArgumentException e) {
            LOG.warning("107のスロット付け替えができなかったため素通しします（"
                    + name + "）: " + e.getMessage());
            return packet;
        }
    }

    /**
     * サーバー行きパケット（ハイブリッド形式）のスロットを 107 の実ワイヤ
     * 形式へ付け替える。スロットを含まないパケットはそのまま返す。
     * （監査で新設：従来は ProxySession のサーバー行き分岐が
     * clientboundTo107 を流用しており、名前解決が CLIENTBOUND 表になる
     * ため Window Click／Set Creative Slot が誤解決され、スロットが
     * ハイブリッド形式のままバックエンドへ届いていた。）
     */
    public static MinecraftPacket serverboundTo107(MinecraftPacket packet) {
        PacketIds.PacketName name = PacketIds.nameFor(packet.id(), PacketIds.State.PLAY,
                PacketIds.Direction.SERVERBOUND, ProtocolVersion.V1_9).orElse(null);
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
                    writer.writeByte(reader.readByte()); // mode（107 は i8）
                    writeSlotAs107(reader, writer, payload); // clickedItem
                }
                case PLAY_SET_CREATIVE_SLOT -> {
                    writer.writeShort(reader.readShort()); // slot
                    writeSlotAs107(reader, writer, payload);
                }
                default -> {
                    return packet;
                }
            }
            requireEnd(reader, name);
            return new MinecraftPacket(packet.id(), writer.bytes());
        } catch (IllegalArgumentException e) {
            LOG.warning("107のスロット付け替えができなかったため素通しします（"
                    + name + "）: " + e.getMessage());
            return packet;
        }
    }

    /** メタデータのエントリ列を付け替える（型は i8⇄varint、番号は 107 のまま）。 */
    private static void writeMetadataEntries107(PacketIO.Reader reader, PacketIO.Writer writer,
            byte[] source, boolean to107) {
        while (true) {
            int index = reader.readUnsignedByte();
            if (index == 0xFF) {
                writer.writeUnsignedByte(0xFF);
                return;
            }
            int type;
            if (to107) {
                type = reader.readVarInt();
            } else {
                type = reader.readUnsignedByte();
            }
            if (to107 && type >= 13 && type <= 16) {
                // 107 に無い型（13 blockid・14 optblockid・15 nbt・
                // 16 particle）は値だけ読み捨てて落とす。
                writeMetadataValue107(reader, new PacketIO.Writer(),
                        source, type, true);
                continue;
            }
            writer.writeUnsignedByte(index);
            if (to107) {
                writer.writeByte(type);
            } else {
                writer.writeVarInt(type);
            }
            writeMetadataValue107(reader, writer, source, type, to107);
        }
    }

    /** メタデータの値1個を付け替える（型番号は世代内番号のまま）。 */
    private static void writeMetadataValue107(PacketIO.Reader reader, PacketIO.Writer writer,
            byte[] source, int type, boolean to107) {
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
            case 6 -> writeSlot107(reader, writer, source, to107);
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
                writeParticleData107(reader, writer, source, particle, to107);
            }
            default -> throw new IllegalArgumentException("未知のメタデータ型です: " + type);
        }
    }
    /** 粒子のデータ部（107 の粒子 ID で形が決まる）を境界形式に付け替える。 */
    private static void writeParticleData107(PacketIO.Reader reader, PacketIO.Writer writer,
            byte[] source, int particle107, boolean to107) {
        switch (particle107) {
            case 37, 38, 46 -> writer.writeVarInt(reader.readVarInt()); // block 系は varint 1個
            case 30 -> { // reddust：実ワイヤはデータ無し
                if (to107) {
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
                if (to107) {
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
    private static void writeAdvancements107(PacketIO.Reader reader, PacketIO.Writer writer,
            byte[] source, boolean to107) {
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
                writeSlot107(reader, writer, source, to107); // icon
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

    // ------------------------------------------------------------------
    // 47（1.8〜1.8.9）対応
    //
    // 47 のスロット形式は 107 と完全同一（itemId i16＋count i8＋
    // itemDamage i16＋NBT）のため、スロットコーデックは 107 のものを
    // そのまま使う。47 固有の付け替えは次の4点。
    //  1. メタデータ：ハイブリッド（キー u8＋型 varint（47 の番号
    //     0〜7）＋値、終端 0xFF）⇔ 実ワイヤ（先頭バイトが
    //     型3bit＋キー5bit の bitfield、終端 0x7f）。
    //  2. エンティティ座標：ハイブリッド f64 ⇔ 実ワイヤ i32 固定
    //     小数点（×32）。相対移動は i16（1/4096 単位）⇔ i8
    //     （1/32 単位）で、換算は ÷128／×128。
    //  3. Spawn Entity の速度は data!=0 のときだけ実ワイヤに存在する。
    //     Named Entity Spawn には currentItem（i16）が挟まる。
    //  4. 粒子 ID は varint ⇔ i32。データ部は 47 の粒子 ID で決まる。
    // ------------------------------------------------------------------

    /** スロット1個だけのペイロード変換（ハイブリッド形式 → 47形式）。 */
    public static byte[] slotTo47(byte[] payload) {
        PacketIO.Reader reader = new PacketIO.Reader(payload);
        PacketIO.Writer writer = new PacketIO.Writer();
        writeSlotAs107(reader, writer, payload);
        return writer.bytes();
    }

    /** スロット1個だけのペイロード変換（47形式 → ハイブリッド形式）。 */
    public static byte[] slotFrom47(byte[] payload) {
        PacketIO.Reader reader = new PacketIO.Reader(payload);
        PacketIO.Writer writer = new PacketIO.Writer();
        writeSlotAsHybridFrom107(reader, writer, payload);
        return writer.bytes();
    }

    private static void writeSlot47(PacketIO.Reader reader, PacketIO.Writer writer,
            byte[] source, boolean to47) {
        if (to47) {
            writeSlotAs107(reader, writer, source);
        } else {
            writeSlotAsHybridFrom107(reader, writer, source);
        }
    }

    /** f64 座標 → 47 の i32 固定小数点（×32、四捨五入）。 */
    private static int fixed47FromDouble(double value) {
        long v = Math.round(value * 32.0);
        if (v > Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }
        if (v < Integer.MIN_VALUE) {
            return Integer.MIN_VALUE;
        }
        return (int) v;
    }

    /** 47 の i32 固定小数点 → f64 座標。 */
    private static double double47FromFixed(int value) {
        return value / 32.0;
    }

    /**
     * クライアント行きパケットをハイブリッド形式から 47形式へ付け替える。
     * 対象外のパケットはそのまま返す。
     */
    public static MinecraftPacket clientboundTo47(MinecraftPacket packet) {
        return rewriteClientbound47(packet, true);
    }

    /**
     * クライアント行きパケットを 47形式からハイブリッド形式へ付け替える。
     * 対象外のパケットはそのまま返す。
     */
    public static MinecraftPacket clientboundFrom47(MinecraftPacket packet) {
        return rewriteClientbound47(packet, false);
    }

    private static MinecraftPacket rewriteClientbound47(MinecraftPacket packet, boolean to47) {
        PacketIds.PacketName name = PacketIds.nameFor(packet.id(), PacketIds.State.PLAY,
                PacketIds.Direction.CLIENTBOUND, ProtocolVersion.V1_8).orElse(null);
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
                        writeSlot47(reader, writer, payload, to47);
                    }
                }
                case PLAY_SET_SLOT -> {
                    writer.writeByte(reader.readByte()); // windowId
                    writer.writeShort(reader.readShort()); // slot
                    writeSlot47(reader, writer, payload, to47);
                }
                case PLAY_ENTITY_EQUIPMENT -> {
                    writer.writeVarInt(reader.readVarInt()); // entityId
                    if (to47) {
                        writer.writeShort(reader.readVarInt()); // slot（47 は i16）
                    } else {
                        writer.writeVarInt(reader.readShort());
                    }
                    writeSlot47(reader, writer, payload, to47);
                }
                case PLAY_ENTITY_METADATA -> {
                    writer.writeVarInt(reader.readVarInt()); // entityId
                    writeMetadataEntries47(reader, writer, payload, to47);
                }
                case PLAY_SPAWN_ENTITY -> {
                    writer.writeVarInt(reader.readVarInt()); // entityId
                    writer.writeByte(reader.readByte()); // type（object バイト）
                    writeFixedCoord47(reader, writer, to47); // x
                    writeFixedCoord47(reader, writer, to47); // y
                    writeFixedCoord47(reader, writer, to47); // z
                    writer.writeByte(reader.readByte()); // pitch
                    writer.writeByte(reader.readByte()); // yaw
                    int data = reader.readInt();
                    writer.writeInt(data);
                    // 速度は data!=0 のときだけ実ワイヤに存在する。
                    // ハイブリッドでは常時3件持つ。
                    if (to47) {
                        int vx = reader.readShort();
                        int vy = reader.readShort();
                        int vz = reader.readShort();
                        if (data != 0) {
                            writer.writeShort(vx);
                            writer.writeShort(vy);
                            writer.writeShort(vz);
                        }
                    } else {
                        if (data != 0) {
                            writer.writeShort(reader.readShort());
                            writer.writeShort(reader.readShort());
                            writer.writeShort(reader.readShort());
                        } else {
                            writer.writeShort(0);
                            writer.writeShort(0);
                            writer.writeShort(0);
                        }
                    }
                }
                case PLAY_SPAWN_ENTITY_LIVING -> {
                    writer.writeVarInt(reader.readVarInt()); // entityId
                    writer.writeByte(reader.readByte()); // type
                    writeFixedCoord47(reader, writer, to47); // x
                    writeFixedCoord47(reader, writer, to47); // y
                    writeFixedCoord47(reader, writer, to47); // z
                    writer.writeByte(reader.readByte()); // yaw
                    writer.writeByte(reader.readByte()); // pitch
                    writer.writeByte(reader.readByte()); // headPitch
                    writer.writeShort(reader.readShort()); // velocity
                    writer.writeShort(reader.readShort());
                    writer.writeShort(reader.readShort());
                    writeMetadataEntries47(reader, writer, payload, to47);
                }
                case PLAY_NAMED_ENTITY_SPAWN -> {
                    writer.writeVarInt(reader.readVarInt()); // entityId
                    writer.writeBytes(reader.readBytes(16)); // uuid
                    writeFixedCoord47(reader, writer, to47); // x
                    writeFixedCoord47(reader, writer, to47); // y
                    writeFixedCoord47(reader, writer, to47); // z
                    writer.writeByte(reader.readByte()); // yaw
                    writer.writeByte(reader.readByte()); // pitch
                    if (to47) {
                        writer.writeShort(0); // currentItem（47 固有。情報が無いため 0）
                    } else {
                        reader.readShort(); // currentItem（ハイブリッドには無い）
                    }
                    writeMetadataEntries47(reader, writer, payload, to47);
                }
                case PLAY_SPAWN_ENTITY_WEATHER -> {
                    writer.writeVarInt(reader.readVarInt()); // entityId
                    writer.writeByte(reader.readByte()); // type
                    writeFixedCoord47(reader, writer, to47); // x
                    writeFixedCoord47(reader, writer, to47); // y
                    writeFixedCoord47(reader, writer, to47); // z
                }
                case PLAY_SPAWN_EXPERIENCE_ORB -> {
                    writer.writeVarInt(reader.readVarInt()); // entityId
                    writeFixedCoord47(reader, writer, to47); // x
                    writeFixedCoord47(reader, writer, to47); // y
                    writeFixedCoord47(reader, writer, to47); // z
                    if (to47) {
                        writer.writeShort(reader.readVarInt()); // count（47 は i16）
                    } else {
                        writer.writeVarInt(reader.readShort());
                    }
                }
                case PLAY_ENTITY_TELEPORT -> {
                    writer.writeVarInt(reader.readVarInt()); // entityId
                    writeFixedCoord47(reader, writer, to47); // x
                    writeFixedCoord47(reader, writer, to47); // y
                    writeFixedCoord47(reader, writer, to47); // z
                    writer.writeByte(reader.readByte()); // yaw
                    writer.writeByte(reader.readByte()); // pitch
                    writer.writeBoolean(reader.readBoolean()); // onGround
                }
                case PLAY_REL_ENTITY_MOVE -> {
                    writer.writeVarInt(reader.readVarInt()); // entityId
                    writeDelta47(reader, writer, to47); // dx
                    writeDelta47(reader, writer, to47); // dy
                    writeDelta47(reader, writer, to47); // dz
                    writer.writeBoolean(reader.readBoolean()); // onGround
                }
                case PLAY_ENTITY_MOVE_LOOK -> {
                    writer.writeVarInt(reader.readVarInt()); // entityId
                    writeDelta47(reader, writer, to47); // dx
                    writeDelta47(reader, writer, to47); // dy
                    writeDelta47(reader, writer, to47); // dz
                    writer.writeByte(reader.readByte()); // yaw
                    writer.writeByte(reader.readByte()); // pitch
                    writer.writeBoolean(reader.readBoolean()); // onGround
                }
                case PLAY_WORLD_PARTICLES -> {
                    int particle;
                    if (to47) {
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
                    writeParticleData47(reader, writer, payload, particle, to47);
                }
                default -> {
                    return packet;
                }
            }
            requireEnd(reader, name);
            return new MinecraftPacket(packet.id(), writer.bytes());
        } catch (IllegalArgumentException e) {
            LOG.warning("47のスロット付け替えができなかったため素通しします（"
                    + name + "）: " + e.getMessage());
            return packet;
        }
    }

    /** 座標1件を f64 ⇔ i32 固定小数点で付け替える。 */
    private static void writeFixedCoord47(PacketIO.Reader reader, PacketIO.Writer writer,
            boolean to47) {
        if (to47) {
            writer.writeInt(fixed47FromDouble(reader.readDouble()));
        } else {
            writer.writeDouble(double47FromFixed(reader.readInt()));
        }
    }

    /** 相対移動1件を i16（1/4096）⇔ i8（1/32）で付け替える（換算は ÷128／×128、丸めあり）。 */
    private static void writeDelta47(PacketIO.Reader reader, PacketIO.Writer writer,
            boolean to47) {
        if (to47) {
            int v = Math.round(reader.readShort() / 128.0f);
            writer.writeByte(Math.max(-128, Math.min(127, v)));
        } else {
            writer.writeShort(reader.readByte() * 128);
        }
    }

    /**
     * サーバー行きパケットを 47形式からハイブリッド形式へ付け替える。
     * 対象外のパケットはそのまま返す。
     */
    public static MinecraftPacket serverboundFrom47(MinecraftPacket packet) {
        PacketIds.PacketName name = PacketIds.nameFor(packet.id(), PacketIds.State.PLAY,
                PacketIds.Direction.SERVERBOUND, ProtocolVersion.V1_8).orElse(null);
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
                    writer.writeByte(reader.readByte()); // mode（47 は i8）
                    writeSlotAsHybridFrom107(reader, writer, payload); // clickedItem
                }
                case PLAY_SET_CREATIVE_SLOT -> {
                    writer.writeShort(reader.readShort()); // slot
                    writeSlotAsHybridFrom107(reader, writer, payload);
                }
                case PLAY_BLOCK_PLACE -> {
                    // 47：location＋direction i8＋heldItem＋cursor（i8×3）。
                    // ハイブリッド：location＋direction varint＋cursor。
                    // heldItem は読み捨てる（758 形式には無い）。
                    writer.writeBytes(reader.readBytes(8)); // location
                    writer.writeVarInt(reader.readByte()); // direction
                    // heldItem はハイブリッドに無いため、ダミーへ書いて捨てる。
                    writeSlotAsHybridFrom107(reader, new PacketIO.Writer(), payload);
                    writer.writeByte(reader.readByte()); // cursorX
                    writer.writeByte(reader.readByte()); // cursorY
                    writer.writeByte(reader.readByte()); // cursorZ
                }
                default -> {
                    return packet;
                }
            }
            requireEnd(reader, name);
            return new MinecraftPacket(packet.id(), writer.bytes());
        } catch (IllegalArgumentException e) {
            LOG.warning("47のスロット付け替えができなかったため素通しします（"
                    + name + "）: " + e.getMessage());
            return packet;
        }
    }

    /**
     * サーバー行きパケットをハイブリッド形式から 47 ワイヤへ付け替える
     * （{@link #serverboundFrom47} の逆変換）。対象はスロットを持つ
     * Window Click／Set Creative Slot と、heldItem の挿入が要る
     * Block Place のみ。その他のサーバー行きパケットはハイブリッドと
     * 47 ワイヤで形式が同一のため素通しする。
     * 4 の Stage E で追加：従来は ProxySession が誤って clientboundTo47 を
     * サーバー行きにも適用しており、名前解決が CLIENTBOUND になるため
     * スロットの付け替えが一切効かず、ハイブリッド形式のスロットが
     * そのまま実バニラ 1.8.9 へ届いていた（5 の serverboundTo5 と同種）。
     */
    public static MinecraftPacket serverboundTo47(MinecraftPacket packet) {
        PacketIds.PacketName name = PacketIds.nameFor(packet.id(), PacketIds.State.PLAY,
                PacketIds.Direction.SERVERBOUND, ProtocolVersion.V1_8).orElse(null);
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
                    writer.writeByte(reader.readByte()); // mode
                    writeSlot47(reader, writer, payload, true); // clickedItem
                }
                case PLAY_SET_CREATIVE_SLOT -> {
                    writer.writeShort(reader.readShort()); // slot
                    writeSlot47(reader, writer, payload, true);
                }
                case PLAY_BLOCK_PLACE -> {
                    // ハイブリッド：location＋direction varint＋cursor（i8×3）。
                    // 47 ワイヤ：location＋direction i8＋heldItem＋cursor。
                    // heldItem はハイブリッドに無いため空スロットを合成する
                    // （設置に使うアイテムは 1.8 では選択中ホットバーが正）。
                    writer.writeBytes(reader.readBytes(8)); // location
                    writer.writeByte((byte) reader.readVarInt()); // direction
                    writer.writeShort(-1); // heldItem（空スロット）
                    writer.writeByte(reader.readByte()); // cursorX
                    writer.writeByte(reader.readByte()); // cursorY
                    writer.writeByte(reader.readByte()); // cursorZ
                }
                default -> {
                    return packet;
                }
            }
            requireEnd(reader, name);
            return new MinecraftPacket(packet.id(), writer.bytes());
        } catch (IllegalArgumentException e) {
            LOG.warning("47へのサーバー行き付け替えができなかったため素通しします（"
                    + name + "）: " + e.getMessage());
            return packet;
        }
    }

    /** メタデータのエントリ列を付け替える（ハイブリッド ⇔ 47 実ワイヤの bitfield 形式）。 */
    private static void writeMetadataEntries47(PacketIO.Reader reader, PacketIO.Writer writer,
            byte[] source, boolean to47) {
        if (to47) {
            while (true) {
                int index = reader.readUnsignedByte();
                if (index == 0xFF) {
                    writer.writeUnsignedByte(0x7F); // 47 の終端
                    return;
                }
                int type = reader.readVarInt();
                writer.writeUnsignedByte(((type & 7) << 5) | (index & 0x1F));
                writeMetadataValue47(reader, writer, source, type, true);
            }
        } else {
            while (true) {
                int header = reader.readUnsignedByte();
                if (header == 0x7F) {
                    writer.writeUnsignedByte(0xFF); // ハイブリッドの終端
                    return;
                }
                int type = (header >> 5) & 7;
                int index = header & 0x1F;
                writer.writeUnsignedByte(index);
                writer.writeVarInt(type);
                writeMetadataValue47(reader, writer, source, type, false);
            }
        }
    }

    /** メタデータの値1個を付け替える（型番号は 47 の番号。値の形はスロット以外同一）。 */
    private static void writeMetadataValue47(PacketIO.Reader reader, PacketIO.Writer writer,
            byte[] source, int type, boolean to47) {
        switch (type) {
            case 0 -> writer.writeByte(reader.readByte());
            case 1 -> writer.writeShort(reader.readShort());
            case 2 -> writer.writeInt(reader.readInt());
            case 3 -> writer.writeFloat(reader.readFloat());
            case 4 -> writer.writeString(reader.readString());
            case 5 -> writeSlot47(reader, writer, source, to47);
            case 6 -> {
                writer.writeInt(reader.readInt());
                writer.writeInt(reader.readInt());
                writer.writeInt(reader.readInt());
            }
            case 7 -> {
                writer.writeFloat(reader.readFloat());
                writer.writeFloat(reader.readFloat());
                writer.writeFloat(reader.readFloat());
            }
            default -> throw new IllegalArgumentException("未知のメタデータ型です: " + type);
        }
    }

    /** 粒子のデータ部（47 の粒子 ID で形が決まる）を付け替える。 */
    private static void writeParticleData47(PacketIO.Reader reader, PacketIO.Writer writer,
            byte[] source, int particle47, boolean to47) {
        switch (particle47) {
            case 37, 38 -> writer.writeVarInt(reader.readVarInt()); // block 系は varint 1個
            case 30 -> { // reddust：実ワイヤはデータ無し
                if (to47) {
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
                if (to47) {
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

    // ------------------------------------------------------------------
    // 5 ⇔ ハイブリッド（1.7.6〜1.7.10 ⇔ 47系ハイブリッド）
    // ハイブリッドのレイアウトは 47 と完全同一（47 の節を参照）。
    // 5 のワイヤとの差分は次のとおり：
    // ・スロットの NBT が gzip 圧縮（compressedNbt：i16 長＋gzip バイト列、
    //   無しは長さ -1）。47 は生 optionalNbt。
    // ・entityId が i32（47 ハイブリッドは varint）。約20パケットに横断。
    // ・ブロック座標がバラ数値3つ（x i32＋y（u8／i16／i32）＋z i32）。
    //   ハイブリッドは 64bit 詰め（x26|y12|z26）。
    // ・entityMetadata のヘッダは 47 と完全同一（bitfield・終端 0x7f）。
    //   値のスロットだけ writeSlot5 で付け替える。
    // ・座標の i32 固定小数点は 47 と共通（fixed47 系ヘルパを流用）。
    // ・Keep Alive は ProxySession 側で i32⇔i64 を付け替える。
    // ------------------------------------------------------------------

    /** ハイブリッドの詰め座標（x26|y12|z26）を x に解く（符号拡張つき）。 */
    private static int positionX5(long packed) {
        return (int) (packed << 0 >> 38);
    }

    /** ハイブリッドの詰め座標（x26|y12|z26）を y に解く（符号拡張つき）。 */
    private static int positionY5(long packed) {
        return (int) (packed << 26 >> 52);
    }

    /** ハイブリッドの詰め座標（x26|y12|z26）を z に解く（符号拡張つき）。 */
    private static int positionZ5(long packed) {
        return (int) (packed << 38 >> 38);
    }

    /** x,y,z をハイブリッドの詰め座標（x26|y12|z26）に詰める。 */
    private static long packPosition5(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (y & 0xFFF) << 26)
                | (z & 0x3FFFFFFL);
    }

    /**
     * 座標1個を付け替える。to5 なら詰め→バラ（y の幅は kind：0=u8、1=i16、2=i32）、
     * !to5 ならバラ→詰め。
     */
    private static void writePosition5(PacketIO.Reader reader, PacketIO.Writer writer,
            int kind, boolean to5) {
        if (to5) {
            long packed = reader.readPositionRaw();
            writer.writeInt(positionX5(packed));
            int y = positionY5(packed);
            switch (kind) {
                case 0 -> writer.writeByte(y);
                case 1 -> writer.writeShort(y);
                default -> writer.writeInt(y);
            }
            writer.writeInt(positionZ5(packed));
        } else {
            int x = reader.readInt();
            int y = switch (kind) {
                case 0 -> reader.readUnsignedByte();
                case 1 -> reader.readShort();
                default -> reader.readInt();
            };
            int z = reader.readInt();
            writer.writePositionRaw(packPosition5(x, y, z));
        }
    }

    /** gzip 圧縮する。 */
    private static byte[] gzip5(byte[] raw) {
        try {
            java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream();
            try (java.util.zip.GZIPOutputStream gzip =
                    new java.util.zip.GZIPOutputStream(buffer)) {
                gzip.write(raw);
            }
            return buffer.toByteArray();
        } catch (java.io.IOException e) {
            throw new IllegalArgumentException("NBT の gzip 圧縮に失敗: " + e.getMessage());
        }
    }

    /** gzip を展開する。 */
    private static byte[] gunzip5(byte[] compressed) {
        try (java.util.zip.GZIPInputStream gzip = new java.util.zip.GZIPInputStream(
                new java.io.ByteArrayInputStream(compressed))) {
            return gzip.readAllBytes();
        } catch (java.io.IOException e) {
            throw new IllegalArgumentException("NBT の gzip 展開に失敗: " + e.getMessage());
        }
    }

    /**
     * スロット1個を付け替える。to5：ハイブリッド（47 と同一形式）→ 5 ワイヤ
     * （item i16・count i8・damage i16・compressedNbt）。アイテム番号は
     * 47 番号⇔5 番号を RegistryRemapper の合成で付け替える。damage は
     * ハイブリッド側では NBT の "Damage" タグに載っている（47 と同じ約束）。
     */
    static void writeSlot5(PacketIO.Reader reader, PacketIO.Writer writer,
            byte[] source, boolean to5) {
        if (to5) {
            if (!reader.readBoolean()) {
                writer.writeShort(-1);
                return;
            }
            int item47 = reader.readVarInt();
            int count = reader.readUnsignedByte();
            Nbt.Tag nbt = reader.readNamedNbt().tag();
            Integer damageTag = ChunkTranslator.slotDamageTag(nbt);
            int damage = damageTag != null ? damageTag : 0;
            Nbt.Tag rest = ChunkTranslator.withoutSlotDamageTag(nbt);
            writer.writeShort(RegistryRemapper.mapItem758To5(
                    RegistryRemapper.mapItem47To758(item47)));
            writer.writeByte(count);
            writer.writeShort(damage);
            if (rest == null) {
                writer.writeShort(-1);
            } else {
                PacketIO.Writer nbtWriter = new PacketIO.Writer();
                nbtWriter.writeNamedNbt("", rest);
                byte[] compressed = gzip5(nbtWriter.bytes());
                writer.writeShort(compressed.length);
                writer.writeBytes(compressed);
            }
        } else {
            int item5 = reader.readShort();
            if (item5 < 0) {
                writer.writeBoolean(false);
                return;
            }
            int count = reader.readUnsignedByte();
            int damage = reader.readShort();
            int length = reader.readShort();
            Nbt.Tag rest = null;
            if (length >= 0) {
                byte[] raw = gunzip5(reader.readBytes(length));
                rest = new PacketIO.Reader(raw).readNamedNbt().tag();
            }
            writer.writeBoolean(true);
            writer.writeVarInt(RegistryRemapper.mapItem758To47(
                    RegistryRemapper.mapItem5To758(item5)));
            writer.writeByte(count);
            Nbt.Tag nbt = ChunkTranslator.withSlotDamageTag(rest, damage);
            if (nbt == null) {
                writer.writeByte(0);
            } else {
                writer.writeNamedNbt("", nbt);
            }
        }
    }

    /** スロット1個だけのペイロードを 5 ワイヤへ変換する。 */
    public static byte[] slotTo5(byte[] payload) {
        try {
            PacketIO.Reader reader = new PacketIO.Reader(payload);
            PacketIO.Writer writer = new PacketIO.Writer();
            writeSlot5(reader, writer, payload, true);
            if (reader.remaining() != 0) { throw new IllegalArgumentException("スロット（5）に余りがあります"); }
            return writer.bytes();
        } catch (IllegalArgumentException e) {
            LOG.warning("スロットを 5 形式に変換できなかったため素通しします: "
                    + e.getMessage());
            return payload;
        }
    }

    /** スロット1個だけのペイロードを 5 ワイヤからハイブリッドへ変換する。 */
    public static byte[] slotFrom5(byte[] payload) {
        try {
            PacketIO.Reader reader = new PacketIO.Reader(payload);
            PacketIO.Writer writer = new PacketIO.Writer();
            writeSlot5(reader, writer, payload, false);
            if (reader.remaining() != 0) { throw new IllegalArgumentException("スロット（5 から）に余りがあります"); }
            return writer.bytes();
        } catch (IllegalArgumentException e) {
            LOG.warning("スロットを 5 形式から変換できなかったため素通しします: "
                    + e.getMessage());
            return payload;
        }
    }

    /** living 種別（47 番号＝ハイブリッド）を 5 のワイヤ番号へ付け替える。 */
    static int livingType5ForHybrid(int type47) {
        return switch (type47) {
            case 67 -> 90;   // endermite → pig（1.7 に無い）
            case 68 -> 94;   // guardian → squid（1.7 に無い）
            case 101 -> 90;  // rabbit → pig（1.7 に無い）
            default -> type47;
        };
    }

    /**
     * entityMetadata の並び（ハイブリッド：0xff 終端・47 型番号）を
     * 5 ワイヤ（0x7f 終端・bitfield ヘッダ）へ付け替える。値の変換は
     * 47 の節と同じ規則で、スロットだけ writeSlot5 を使う。
     */
    static void writeMetadataEntries5(PacketIO.Reader reader, PacketIO.Writer writer,
            byte[] source, boolean to5) {
        while (true) {
            int key = to5 ? reader.readUnsignedByte() : reader.readUnsignedByte();
            if (to5 && key == 0xFF) {
                writer.writeByte(0x7F);
                return;
            }
            if (!to5 && key == 0x7F) {
                writer.writeByte(0xFF);
                return;
            }
            int index;
            int type;
            if (to5) {
                index = key;
                type = reader.readVarInt();
                writer.writeByte((type << 5) | (index & 0x1F));
            } else {
                index = key & 0x1F;
                type = (key >> 5) & 0x7;
                writer.writeByte(index);
                writer.writeVarInt(type);
            }
            writeMetadataValue5(reader, writer, source, type, to5);
        }
    }

    /** メタデータ値1個を付け替える（型番号は 47 と共通の 0〜7）。 */
    private static void writeMetadataValue5(PacketIO.Reader reader,
            PacketIO.Writer writer, byte[] source, int type, boolean to5) {
        switch (type) {
            case 0 -> writer.writeByte(reader.readByte());
            case 1 -> writer.writeShort(reader.readShort());
            case 2 -> writer.writeInt(reader.readInt());
            case 3 -> writer.writeFloat(reader.readFloat());
            case 4 -> writer.writeString(reader.readString());
            case 5 -> writeSlot5(reader, writer, source, to5);
            case 6 -> {
                writer.writeInt(reader.readInt());
                writer.writeInt(reader.readInt());
                writer.writeInt(reader.readInt());
            }
            case 7 -> {
                writer.writeFloat(reader.readFloat());
                writer.writeFloat(reader.readFloat());
                writer.writeFloat(reader.readFloat());
            }
            default -> throw new IllegalArgumentException(
                    "未対応のメタデータ型（5）: " + type);
        }
    }

    /** メタデータだけのバイト列をハイブリッド→5 ワイヤへ変換する（名前付きスポーン用）。 */
    public static byte[] metadataTo5(byte[] hybridMetadata) {
        PacketIO.Reader reader = new PacketIO.Reader(hybridMetadata);
        PacketIO.Writer writer = new PacketIO.Writer();
        writeMetadataEntries5(reader, writer, hybridMetadata, true);
        return writer.bytes();
    }

    /** メタデータだけのバイト列を 5 ワイヤ→ハイブリッドへ変換する。 */
    public static byte[] metadataFrom5(byte[] wireMetadata) {
        PacketIO.Reader reader = new PacketIO.Reader(wireMetadata);
        PacketIO.Writer writer = new PacketIO.Writer();
        writeMetadataEntries5(reader, writer, wireMetadata, false);
        return writer.bytes();
    }

    /**
     * JSON テキストコンポーネントから平文を取り出す（open_window のタイトル・
     * 看板用）。{"text":...} と extra の入れ子に対応し、解釈できない場合は
     * 入力をそのまま返す。
     */
    static String jsonToPlain5(String json) {
        if (json == null) {
            return "";
        }
        String trimmed = json.trim();
        if (!trimmed.startsWith("{") && !trimmed.startsWith("[")) {
            return json;
        }
        StringBuilder out = new StringBuilder();
        collectJsonText5(trimmed, out);
        return out.toString();
    }

    /** JSON 文字列中の "text":"..." の値を再帰的に集める簡易スキャナ。 */
    private static void collectJsonText5(String json, StringBuilder out) {
        int i = 0;
        while (i < json.length()) {
            int at = json.indexOf("\"text\"", i);
            if (at < 0) {
                return;
            }
            int colon = json.indexOf(':', at + 6);
            if (colon < 0) {
                return;
            }
            int p = colon + 1;
            while (p < json.length() && Character.isWhitespace(json.charAt(p))) {
                p++;
            }
            if (p >= json.length() || json.charAt(p) != '"') {
                i = at + 6;
                continue;
            }
            StringBuilder value = new StringBuilder();
            p++;
            while (p < json.length()) {
                char c = json.charAt(p);
                if (c == '\\' && p + 1 < json.length()) {
                    char next = json.charAt(p + 1);
                    switch (next) {
                        case 'n' -> value.append('\n');
                        case 't' -> value.append('\t');
                        case 'r' -> value.append('\r');
                        case 'u' -> {
                            if (p + 5 < json.length()) {
                                value.append((char) Integer.parseInt(
                                        json.substring(p + 2, p + 6), 16));
                                p += 4;
                            }
                        }
                        default -> value.append(next);
                    }
                    p += 2;
                } else if (c == '"') {
                    p++;
                    break;
                } else {
                    value.append(c);
                    p++;
                }
            }
            out.append(value);
            i = p;
        }
    }

    /** 平文を {"text":...} の JSON に包む（看板・タイトル用）。 */
    static String plainToJson5(String plain) {
        return "{\"text\":" + Nbt.jsonString(plain != null ? plain : "") + "}";
    }

    /** 1.7 のインベントリ種別番号⇔文字列（open_window 用）。 */
    private static final String[] WINDOW_TYPES_5 = {
            "minecraft:chest", "minecraft:crafting_table", "minecraft:furnace",
            "minecraft:dispenser", "minecraft:enchanting_table",
            "minecraft:brewing_stand", "minecraft:villager", "minecraft:beacon",
            "minecraft:anvil", "minecraft:hopper", "minecraft:dropper",
            "EntityHorse"};

    /** インベントリ種別文字列を 1.7 の番号へ（不明は 0＝チェスト）。 */
    static int windowType5ForName(String type) {
        for (int i = 0; i < WINDOW_TYPES_5.length; i++) {
            if (WINDOW_TYPES_5[i].equals(type)) {
                return i;
            }
        }
        return 0;
    }

    /** 1.7 のインベントリ種別番号を文字列へ（範囲外はチェスト）。 */
    static String windowTypeName5(int id) {
        return id >= 0 && id < WINDOW_TYPES_5.length ? WINDOW_TYPES_5[id]
                : "minecraft:chest";
    }

    /** クライアント行きパケットをハイブリッド→5 ワイヤへ付け替える。 */
    public static MinecraftPacket clientboundTo5(MinecraftPacket packet) {
        return rewriteClientbound5(packet, true, ProtocolVersion.V1_7_10);
    }

    /** クライアント行きパケットを 5 ワイヤ→ハイブリッドへ付け替える。 */
    public static MinecraftPacket clientboundFrom5(MinecraftPacket packet) {
        return rewriteClientbound5(packet, false, ProtocolVersion.V1_7_10);
    }

    /**
     * クライアント行きパケットをハイブリッド→4 ワイヤへ付け替える。
     * 4 のワイヤは named_entity_spawn 以外 5 と同一で、その分岐は
     * ProxySession 側で処理するため、ここは 5 と同じ付け替えでよい。
     */
    public static MinecraftPacket clientboundTo4(MinecraftPacket packet) {
        return rewriteClientbound5(packet, true, ProtocolVersion.V1_7_2);
    }

    /**
     * クライアント行きパケットを 4 ワイヤ→ハイブリッドへ付け替える。
     * 5 との違いは named_entity_spawn の1件だけ（プロパティ配列が
     * 無く、UUID 文字列がダッシュ無し32文字）。
     */
    public static MinecraftPacket clientboundFrom4(MinecraftPacket packet) {
        return rewriteClientbound5(packet, false, ProtocolVersion.V1_7_2);
    }

    /**
     * entityId を付け替える（ハイブリッド varint ⇔ 5 ワイヤ i32）。
     */
    private static void writeEntityId5(PacketIO.Reader reader, PacketIO.Writer writer,
            boolean to5) {
        if (to5) {
            writer.writeInt(reader.readVarInt());
        } else {
            writer.writeVarInt(reader.readInt());
        }
    }

    private static MinecraftPacket rewriteClientbound5(MinecraftPacket packet,
            boolean to5, int protocol) {
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
                        writeSlot5(reader, writer, payload, to5);
                    }
                }
                case PLAY_SET_SLOT -> {
                    writer.writeByte(reader.readByte()); // windowId
                    writer.writeShort(reader.readShort()); // slot
                    writeSlot5(reader, writer, payload, to5);
                }
                case PLAY_ENTITY_EQUIPMENT -> {
                    writeEntityId5(reader, writer, to5);
                    if (to5) {
                        writer.writeShort(reader.readVarInt()); // slot（5 は i16）
                    } else {
                        writer.writeVarInt(reader.readShort());
                    }
                    writeSlot5(reader, writer, payload, to5);
                }
                case PLAY_ENTITY_METADATA -> {
                    writeEntityId5(reader, writer, to5);
                    writeMetadataEntries5(reader, writer, payload, to5);
                }
                case PLAY_SPAWN_ENTITY -> {
                    writer.writeVarInt(reader.readVarInt()); // entityId
                    int type = reader.readUnsignedByte();
                    writer.writeByte(type); // type（object バイト）
                    writeFixedCoord47(reader, writer, to5); // x
                    writeFixedCoord47(reader, writer, to5); // y
                    writeFixedCoord47(reader, writer, to5); // z
                    writer.writeByte(reader.readByte()); // pitch
                    writer.writeByte(reader.readByte()); // yaw
                    int data = reader.readInt();
                    if (type == 70) { // 落下ブロック：データ詰めが世代で違う
                        data = to5 ? (data >> 4) | ((data & 15) << 16)
                                : ((data & 0xFFFF) << 4) | ((data >> 16) & 15);
                    }
                    writer.writeInt(data);
                    if (to5) {
                        int vx = reader.readShort();
                        int vy = reader.readShort();
                        int vz = reader.readShort();
                        if (data != 0) {
                            writer.writeShort(vx);
                            writer.writeShort(vy);
                            writer.writeShort(vz);
                        }
                    } else {
                        if (data != 0) {
                            writer.writeShort(reader.readShort());
                            writer.writeShort(reader.readShort());
                            writer.writeShort(reader.readShort());
                        } else {
                            writer.writeShort(0);
                            writer.writeShort(0);
                            writer.writeShort(0);
                        }
                    }
                }
                case PLAY_SPAWN_ENTITY_LIVING -> {
                    writer.writeVarInt(reader.readVarInt()); // entityId
                    int type = reader.readUnsignedByte();
                    writer.writeByte(to5 ? livingType5ForHybrid(type) : type);
                    writeFixedCoord47(reader, writer, to5); // x
                    writeFixedCoord47(reader, writer, to5); // y
                    writeFixedCoord47(reader, writer, to5); // z
                    writer.writeByte(reader.readByte()); // yaw
                    writer.writeByte(reader.readByte()); // pitch
                    writer.writeByte(reader.readByte()); // headPitch
                    writer.writeShort(reader.readShort()); // velocity
                    writer.writeShort(reader.readShort());
                    writer.writeShort(reader.readShort());
                    writeMetadataEntries5(reader, writer, payload, to5);
                }
                case PLAY_NAMED_ENTITY_SPAWN -> {
                    if (to5) {
                        // 名前・プロパティの合成が要るため ProxySession 側で処理する。
                        return packet;
                    }
                    writer.writeVarInt(reader.readVarInt()); // entityId
                    String uuidText = reader.readString();
                    reader.readString(); // playerName（ハイブリッドには無い）
                    if (protocol != ProtocolVersion.V1_7_2) {
                        // プロパティ配列は 5 のみ（4 には無く、名前の
                        // 直後が座標になる）。
                        int properties = reader.readVarInt();
                        for (int i = 0; i < properties; i++) {
                            reader.readString();
                            reader.readString();
                            reader.readString();
                        }
                    }
                    try {
                        writer.writeUuid(protocol == ProtocolVersion.V1_7_2
                                ? PlayTranslator.uuidFromString4(uuidText)
                                : java.util.UUID.fromString(uuidText));
                    } catch (IllegalArgumentException e) {
                        writer.writeUuid(new java.util.UUID(0L, 0L));
                    }
                    writer.writeDouble(double47FromFixed(reader.readInt())); // x
                    writer.writeDouble(double47FromFixed(reader.readInt())); // y
                    writer.writeDouble(double47FromFixed(reader.readInt())); // z
                    writer.writeByte(reader.readByte()); // yaw
                    writer.writeByte(reader.readByte()); // pitch
                    reader.readShort(); // currentItem（ハイブリッドには無い）
                    writeMetadataEntries5(reader, writer, payload, false);
                }
                case PLAY_SPAWN_ENTITY_WEATHER -> {
                    writer.writeVarInt(reader.readVarInt()); // entityId
                    writer.writeByte(reader.readByte()); // type
                    writeFixedCoord47(reader, writer, to5); // x
                    writeFixedCoord47(reader, writer, to5); // y
                    writeFixedCoord47(reader, writer, to5); // z
                }
                case PLAY_SPAWN_EXPERIENCE_ORB -> {
                    writer.writeVarInt(reader.readVarInt()); // entityId
                    writeFixedCoord47(reader, writer, to5); // x
                    writeFixedCoord47(reader, writer, to5); // y
                    writeFixedCoord47(reader, writer, to5); // z
                    if (to5) {
                        writer.writeShort(reader.readVarInt()); // count（5 は i16）
                    } else {
                        writer.writeVarInt(reader.readShort());
                    }
                }
                case PLAY_SPAWN_ENTITY_PAINTING -> {
                    writer.writeVarInt(reader.readVarInt()); // entityId
                    writer.writeString(reader.readString()); // title
                    writePosition5(reader, writer, 2, to5); // location（y は i32）
                    if (to5) {
                        writer.writeInt(reader.readUnsignedByte()); // direction
                    } else {
                        writer.writeByte(reader.readInt());
                    }
                }
                case PLAY_ENTITY_TELEPORT -> {
                    writeEntityId5(reader, writer, to5);
                    writeFixedCoord47(reader, writer, to5); // x
                    writeFixedCoord47(reader, writer, to5); // y
                    writeFixedCoord47(reader, writer, to5); // z
                    writer.writeByte(reader.readByte()); // yaw
                    writer.writeByte(reader.readByte()); // pitch
                    if (to5) {
                        reader.readBoolean(); // onGround（5 には無い）
                    } else {
                        writer.writeBoolean(false); // onGround を補う
                    }
                }
                case PLAY_REL_ENTITY_MOVE -> {
                    writeEntityId5(reader, writer, to5);
                    writeDelta47(reader, writer, to5); // dx
                    writeDelta47(reader, writer, to5); // dy
                    writeDelta47(reader, writer, to5); // dz
                    if (to5) {
                        reader.readBoolean(); // onGround（5 には無い）
                    } else {
                        writer.writeBoolean(false);
                    }
                }
                case PLAY_ENTITY_MOVE_LOOK -> {
                    writeEntityId5(reader, writer, to5);
                    writeDelta47(reader, writer, to5); // dx
                    writeDelta47(reader, writer, to5); // dy
                    writeDelta47(reader, writer, to5); // dz
                    writer.writeByte(reader.readByte()); // yaw
                    writer.writeByte(reader.readByte()); // pitch
                    if (to5) {
                        reader.readBoolean(); // onGround（5 には無い）
                    } else {
                        writer.writeBoolean(false);
                    }
                }
                case PLAY_ENTITY_LOOK -> {
                    writeEntityId5(reader, writer, to5);
                    writer.writeByte(reader.readByte()); // yaw
                    writer.writeByte(reader.readByte()); // pitch
                    if (to5) {
                        reader.readBoolean(); // onGround（5 には無い）
                    } else {
                        writer.writeBoolean(false);
                    }
                }
                case PLAY_ENTITY -> {
                    writeEntityId5(reader, writer, to5);
                }
                case PLAY_ENTITY_VELOCITY -> {
                    writeEntityId5(reader, writer, to5);
                    writer.writeShort(reader.readShort());
                    writer.writeShort(reader.readShort());
                    writer.writeShort(reader.readShort());
                }
                case PLAY_ENTITY_HEAD_ROTATION -> {
                    writeEntityId5(reader, writer, to5);
                    writer.writeByte(reader.readByte()); // headYaw
                }
                case PLAY_ENTITY_DESTROY -> {
                    if (to5) {
                        int count = reader.readVarInt();
                        writer.writeByte(count);
                        for (int i = 0; i < count; i++) {
                            writer.writeInt(reader.readVarInt());
                        }
                    } else {
                        int count = reader.readUnsignedByte();
                        writer.writeVarInt(count);
                        for (int i = 0; i < count; i++) {
                            writer.writeVarInt(reader.readInt());
                        }
                    }
                }
                case PLAY_COLLECT -> {
                    writeEntityId5(reader, writer, to5); // collected
                    writeEntityId5(reader, writer, to5); // collector
                }
                case PLAY_BED -> {
                    writeEntityId5(reader, writer, to5);
                    writePosition5(reader, writer, 0, to5); // y は u8
                }
                case PLAY_BLOCK_CHANGE -> {
                    if (to5) {
                        long packed = reader.readPositionRaw();
                        int state47 = reader.readVarInt();
                        int state5 = RegistryRemapper.mapBlockState758To5(
                                RegistryRemapper.mapBlockState47To758(state47));
                        writer.writeInt(positionX5(packed));
                        writer.writeByte(positionY5(packed));
                        writer.writeInt(positionZ5(packed));
                        writer.writeVarInt(state5 >> 4); // type（ブロック番号）
                        writer.writeByte(state5 & 15); // metadata
                    } else {
                        int x = reader.readInt();
                        int y = reader.readUnsignedByte();
                        int z = reader.readInt();
                        int type = reader.readVarInt();
                        int meta = reader.readUnsignedByte();
                        int state5 = (type << 4) | (meta & 15);
                        writer.writePositionRaw(packPosition5(x, y, z));
                        writer.writeVarInt(RegistryRemapper.mapBlockState758To47(
                                RegistryRemapper.mapBlockState5To758(state5)));
                    }
                }
                case PLAY_BLOCK_ACTION -> {
                    writePosition5(reader, writer, 1, to5); // y は i16
                    writer.writeByte(reader.readByte());
                    writer.writeByte(reader.readByte());
                    if (to5) {
                        writer.writeVarInt(RegistryRemapper.mapBlock758To5(
                                RegistryRemapper.mapBlock47To758(reader.readVarInt())));
                    } else {
                        writer.writeVarInt(RegistryRemapper.mapBlock758To47(
                                RegistryRemapper.mapBlock5To758(reader.readVarInt())));
                    }
                }
                case PLAY_BLOCK_BREAK_ANIMATION -> {
                    writer.writeVarInt(reader.readVarInt()); // entityId（両世代 varint）
                    writePosition5(reader, writer, 2, to5); // y は i32
                    writer.writeByte(reader.readByte()); // stage
                }
                case PLAY_WORLD_EVENT -> {
                    writer.writeInt(reader.readInt()); // effectId
                    writePosition5(reader, writer, 0, to5); // y は u8
                    writer.writeInt(reader.readInt()); // data
                    writer.writeBoolean(reader.readBoolean()); // global
                }
                case PLAY_SPAWN_POSITION, PLAY_OPEN_SIGN_EDITOR -> {
                    writePosition5(reader, writer, 2, to5); // y は i32
                }
                case PLAY_BLOCK_ENTITY_DATA -> {
                    writePosition5(reader, writer, 1, to5); // y は i16
                    writer.writeByte(reader.readByte()); // action
                    if (to5) {
                        byte[] raw = reader.rest();
                        if (raw.length == 0) {
                            writer.writeShort(-1);
                        } else {
                            byte[] compressed = gzip5(raw);
                            writer.writeShort(compressed.length);
                            writer.writeBytes(compressed);
                        }
                    } else {
                        int length = reader.readShort();
                        if (length >= 0) {
                            writer.writeBytes(gunzip5(reader.readBytes(length)));
                        }
                    }
                }
                case PLAY_UPDATE_SIGN -> {
                    writePosition5(reader, writer, 1, to5); // y は i16
                    for (int i = 0; i < 4; i++) {
                        if (to5) {
                            writer.writeString(jsonToPlain5(reader.readString()));
                        } else {
                            writer.writeString(plainToJson5(reader.readString()));
                        }
                    }
                }
                case PLAY_ENTITY_EFFECT -> {
                    writeEntityId5(reader, writer, to5);
                    writer.writeByte(reader.readByte()); // effectId
                    writer.writeByte(reader.readByte()); // amplifier
                    if (to5) {
                        int duration = reader.readVarInt();
                        writer.writeShort(Math.min(duration, Short.MAX_VALUE));
                        reader.readBoolean(); // hideParticles（5 には無い）
                    } else {
                        writer.writeVarInt(reader.readShort());
                        writer.writeBoolean(false); // hideParticles を補う
                    }
                }
                case PLAY_REMOVE_ENTITY_EFFECT -> {
                    writeEntityId5(reader, writer, to5);
                    writer.writeByte(reader.readByte()); // effectId
                }
                case PLAY_EXPERIENCE -> {
                    writer.writeFloat(reader.readFloat()); // bar
                    if (to5) {
                        writer.writeShort(reader.readVarInt()); // level
                        writer.writeShort(reader.readVarInt()); // total
                    } else {
                        writer.writeVarInt(reader.readShort());
                        writer.writeVarInt(reader.readShort());
                    }
                }
                case PLAY_UPDATE_HEALTH -> {
                    writer.writeFloat(reader.readFloat()); // health
                    if (to5) {
                        writer.writeShort(reader.readVarInt()); // food
                    } else {
                        writer.writeVarInt(reader.readShort());
                    }
                    writer.writeFloat(reader.readFloat()); // saturation
                }
                case PLAY_SCOREBOARD_OBJECTIVE -> {
                    if (to5) {
                        String objName = reader.readString();
                        int action = reader.readByte();
                        String display = "";
                        if (action == 0 || action == 2) {
                            display = reader.readString();
                            reader.readString(); // type（5 には無い）
                        }
                        writer.writeString(objName);
                        writer.writeString(display);
                        writer.writeByte(action);
                    } else {
                        String objName = reader.readString();
                        String display = reader.readString();
                        int action = reader.readByte();
                        writer.writeString(objName);
                        writer.writeByte(action);
                        if (action == 0 || action == 2) {
                            writer.writeString(display);
                            writer.writeString("integer"); // type を補う
                        }
                    }
                }
                case PLAY_SCOREBOARD_SCORE -> {
                    if (to5) {
                        writer.writeString(reader.readString()); // itemName
                        int action = reader.readVarInt();
                        writer.writeByte(action);
                        String scoreName = reader.readString();
                        if (action != 1) {
                            writer.writeString(scoreName);
                            writer.writeInt(reader.readVarInt()); // value
                        }
                    } else {
                        writer.writeString(reader.readString()); // itemName
                        int action = reader.readByte();
                        writer.writeVarInt(action);
                        if (action != 1) {
                            writer.writeString(reader.readString()); // scoreName
                            writer.writeVarInt(reader.readInt()); // value
                        } else {
                            writer.writeString(""); // scoreName を補う
                        }
                    }
                }
                case PLAY_TEAMS -> {
                    writer.writeString(reader.readString()); // team
                    int mode = reader.readByte();
                    writer.writeByte(mode);
                    if (mode == 0 || mode == 2) {
                        writer.writeString(reader.readString()); // name
                        writer.writeString(reader.readString()); // prefix
                        writer.writeString(reader.readString()); // suffix
                        writer.writeByte(reader.readByte()); // friendlyFire
                        if (to5) {
                            reader.readString(); // nameTagVisibility（5 には無い）
                            reader.readByte(); // color（5 には無い）
                        } else {
                            writer.writeString("always"); // nameTagVisibility
                            writer.writeByte(-1); // color（継承）
                        }
                    }
                    if (mode == 0 || mode == 3 || mode == 4) {
                        if (to5) {
                            int count = reader.readVarInt();
                            writer.writeShort(count);
                            for (int i = 0; i < count; i++) {
                                writer.writeString(reader.readString());
                            }
                        } else {
                            int count = reader.readShort();
                            writer.writeVarInt(count);
                            for (int i = 0; i < count; i++) {
                                writer.writeString(reader.readString());
                            }
                        }
                    }
                }
                case PLAY_OPEN_WINDOW -> {
                    writer.writeUnsignedByte(reader.readUnsignedByte()); // windowId
                    if (to5) {
                        String typeName = reader.readString();
                        int typeId = windowType5ForName(typeName);
                        writer.writeByte(typeId);
                        writer.writeString(jsonToPlain5(reader.readString()));
                        writer.writeUnsignedByte(reader.readUnsignedByte());
                        writer.writeBoolean(true); // useProvidedTitle
                        if (typeId == 11) {
                            writer.writeInt(reader.readInt()); // entityId
                        }
                    } else {
                        int typeId = reader.readUnsignedByte();
                        writer.writeString(windowTypeName5(typeId));
                        writer.writeString(plainToJson5(reader.readString()));
                        writer.writeUnsignedByte(reader.readUnsignedByte());
                        reader.readBoolean(); // useProvidedTitle（ハイブリッドには無い）
                        if (typeId == 11) {
                            writer.writeInt(reader.readInt()); // entityId
                        }
                    }
                }
                case PLAY_PLUGIN_MESSAGE -> {
                    writer.writeString(reader.readString()); // channel
                    if (to5) {
                        byte[] data = reader.rest();
                        writer.writeShort(data.length);
                        writer.writeBytes(data);
                    } else {
                        int length = reader.readShort();
                        writer.writeBytes(reader.readBytes(Math.max(length, 0)));
                    }
                }
                case PLAY_CHAT -> {
                    writer.writeString(reader.readString()); // JSON
                    if (to5) {
                        reader.readByte(); // position（5 には無い）
                    } else {
                        writer.writeByte(0); // position: chat
                    }
                }
                case PLAY_ENTITY_UPDATE_ATTRIBUTES -> {
                    writeEntityId5(reader, writer, to5);
                    int count = reader.readInt();
                    writer.writeInt(count);
                    for (int i = 0; i < count; i++) {
                        writer.writeString(reader.readString()); // key
                        writer.writeDouble(reader.readDouble()); // value
                        if (to5) {
                            int modifiers = reader.readVarInt();
                            writer.writeShort(modifiers);
                            for (int j = 0; j < modifiers; j++) {
                                writer.writeUuid(reader.readUuid());
                                writer.writeDouble(reader.readDouble());
                                writer.writeByte(reader.readByte());
                            }
                        } else {
                            int modifiers = reader.readShort();
                            writer.writeVarInt(modifiers);
                            for (int j = 0; j < modifiers; j++) {
                                writer.writeUuid(reader.readUuid());
                                writer.writeDouble(reader.readDouble());
                                writer.writeByte(reader.readByte());
                            }
                        }
                    }
                }
                case PLAY_WORLD_PARTICLES -> {
                    if (to5) {
                        int particle = reader.readVarInt();
                        reader.readBoolean(); // longDistance（5 には無い）
                        float[] floats = new float[7];
                        for (int i = 0; i < floats.length; i++) {
                            floats[i] = reader.readFloat();
                        }
                        int count = reader.readInt();
                        PacketIO.Writer dummy = new PacketIO.Writer();
                        writeParticleData47(reader, dummy, payload, particle, true);
                        writer.writeString(RegistryRemapper.particleName5(particle));
                        for (float f : floats) {
                            writer.writeFloat(f);
                        }
                        writer.writeInt(count);
                    } else {
                        String particleName = reader.readString();
                        int particle = RegistryRemapper.particleId5ForName(particleName);
                        if (particle < 0) {
                            particle = 0; // explode で代替
                        }
                        writer.writeVarInt(particle);
                        writer.writeBoolean(false); // longDistance
                        for (int i = 0; i < 7; i++) {
                            writer.writeFloat(reader.readFloat());
                        }
                        writer.writeInt(reader.readInt()); // count
                        switch (particle) {
                            case 37, 38 -> writer.writeVarInt(0); // block 系データ
                            case 36 -> { // iconcrack：スロット形式で補う
                                writer.writeBoolean(true);
                                writer.writeVarInt(1); // stone
                                writer.writeByte(1);
                                writer.writeByte(0);
                            }
                            case 30 -> { // reddust
                                writer.writeFloat(1.0f);
                                writer.writeFloat(0.0f);
                                writer.writeFloat(0.0f);
                                writer.writeFloat(1.0f);
                            }
                            default -> {
                            }
                        }
                    }
                }
                default -> {
                    return packet;
                }
            }
            requireEnd(reader, name);
            return new MinecraftPacket(packet.id(), writer.bytes());
        } catch (IllegalArgumentException e) {
            LOG.warning("5のスロット付け替えができなかったため素通しします（"
                    + name + "）: " + e.getMessage());
            return packet;
        }
    }

    /**
     * サーバー行きパケットを 5 ワイヤからハイブリッド形式へ付け替える。
     * 対象外のパケットはそのまま返す。
     */
    public static MinecraftPacket serverboundFrom5(MinecraftPacket packet) {
        return serverboundFrom5(packet, ProtocolVersion.V1_7_10);
    }

    /**
     * サーバー行きパケットを 4 ワイヤ→ハイブリッドへ付け替える。
     * サーバー行きの形式は 4⇔5 で同一（差分の2件はどちらも
     * クライアント行き）のため、5 と同じ付け替えを使う。
     */
    public static MinecraftPacket serverboundFrom4(MinecraftPacket packet) {
        return serverboundFrom5(packet, ProtocolVersion.V1_7_2);
    }

    private static MinecraftPacket serverboundFrom5(MinecraftPacket packet,
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
                    writer.writeByte(reader.readByte()); // windowId
                    writer.writeShort(reader.readShort()); // slot
                    writer.writeByte(reader.readByte()); // button
                    writer.writeShort(reader.readShort()); // actionNumber
                    writer.writeByte(reader.readByte()); // mode
                    writeSlot5(reader, writer, payload, false);
                }
                case PLAY_SET_CREATIVE_SLOT -> {
                    writer.writeShort(reader.readShort()); // slot
                    writeSlot5(reader, writer, payload, false);
                }
                case PLAY_BLOCK_PLACE -> {
                    writePosition5(reader, writer, 0, false); // y は u8
                    writer.writeVarInt(reader.readByte()); // direction
                    PacketIO.Writer dummy = new PacketIO.Writer();
                    writeSlot5(reader, dummy, payload, false); // heldItem は捨てる
                    writer.writeByte(reader.readByte()); // cursorX
                    writer.writeByte(reader.readByte()); // cursorY
                    writer.writeByte(reader.readByte()); // cursorZ
                }
                case PLAY_BLOCK_DIG -> {
                    writer.writeVarInt(reader.readByte()); // status
                    writePosition5(reader, writer, 0, false); // y は u8
                    writer.writeByte(reader.readByte()); // face
                }
                case PLAY_UPDATE_SIGN -> {
                    writePosition5(reader, writer, 1, false); // y は i16
                    for (int i = 0; i < 4; i++) {
                        writer.writeString(plainToJson5(reader.readString()));
                    }
                }
                case PLAY_SET_PLAYER_POSITION -> {
                    writer.writeDouble(reader.readDouble()); // x
                    reader.readDouble(); // stance（ハイブリッドには無い）
                    writer.writeDouble(reader.readDouble()); // y
                    writer.writeDouble(reader.readDouble()); // z
                    writer.writeBoolean(reader.readBoolean()); // onGround
                }
                case PLAY_SET_PLAYER_POSITION_AND_ROTATION -> {
                    writer.writeDouble(reader.readDouble()); // x
                    reader.readDouble(); // stance
                    writer.writeDouble(reader.readDouble()); // y
                    writer.writeDouble(reader.readDouble()); // z
                    writer.writeFloat(reader.readFloat()); // yaw
                    writer.writeFloat(reader.readFloat()); // pitch
                    writer.writeBoolean(reader.readBoolean()); // onGround
                }
                case PLAY_ARM_ANIMATION -> {
                    reader.readInt(); // entityId（自プレイヤー固定のため捨てる）
                    reader.readByte(); // animation
                    // ハイブリッド（47）の arm_animation は空ペイロード
                }
                case PLAY_ENTITY_ACTION -> {
                    writer.writeVarInt(reader.readInt()); // entityId
                    writer.writeVarInt(reader.readByte()); // actionId
                    writer.writeVarInt(reader.readInt()); // jumpBoost
                }
                case PLAY_USE_ENTITY -> {
                    writer.writeVarInt(reader.readInt()); // target
                    int mouse = reader.readByte();
                    writer.writeVarInt(mouse);
                    if (mouse == 2) {
                        writer.writeFloat(reader.readFloat());
                        writer.writeFloat(reader.readFloat());
                        writer.writeFloat(reader.readFloat());
                    }
                }
                case PLAY_CLIENT_COMMAND -> {
                    writer.writeVarInt(reader.readByte()); // payload
                }
                case PLAY_CLIENT_INFORMATION -> {
                    writer.writeString(reader.readString()); // locale
                    writer.writeByte(reader.readByte()); // viewDistance
                    writer.writeByte(reader.readByte()); // chatFlags
                    writer.writeBoolean(reader.readBoolean()); // chatColors
                    reader.readUnsignedByte(); // difficulty（ハイブリッドには無い）
                    boolean showCape = reader.readBoolean();
                    writer.writeByte(showCape ? 0x7F : 0x7E); // skinParts で近似
                }
                case PLAY_STEER_VEHICLE -> {
                    writer.writeFloat(reader.readFloat()); // sideways
                    writer.writeFloat(reader.readFloat()); // forward
                    boolean jump = reader.readBoolean();
                    boolean unmount = reader.readBoolean();
                    writer.writeByte((jump ? 0x01 : 0) | (unmount ? 0x02 : 0));
                }
                case PLAY_TAB_COMPLETE_TO_SERVER -> {
                    writer.writeString(reader.readString()); // text
                    writer.writeByte(0); // 対象ブロック無し
                }
                case PLAY_PLUGIN_MESSAGE_TO_SERVER -> {
                    writer.writeString(reader.readString()); // channel
                    int length = reader.readShort();
                    writer.writeBytes(reader.readBytes(Math.max(length, 0)));
                }
                default -> {
                    return packet;
                }
            }
            requireEnd(reader, name);
            return new MinecraftPacket(packet.id(), writer.bytes());
        } catch (IllegalArgumentException e) {
            LOG.warning("5のサーバー行き付け替えができなかったため素通しします（"
                    + name + "）: " + e.getMessage());
            return packet;
        }
    }

    /**
     * サーバー行きパケットをハイブリッド形式から 5 ワイヤへ付け替える
     * （{@link #serverboundFrom5} の逆変換）。対象外のパケットはそのまま
     * 返す。腕アニメーションはハイブリッドに entityId が無いため、Join Game
     * から得た自プレイヤーの entityId（ownEntityId）で合成する。
     * ownEntityId が不明（負値）なら腕アニメーションは {@code null}（落とす）。
     * Stage E で追加：従来は ProxySession が誤って clientboundTo5 を
     * サーバー行きにも適用しており、名前解決が CLIENTBOUND になるため
     * 移動の stance 合成・スロット・座標の付け替えが一切効かず、実バニラ
     * 1.7.10 が DecoderException で切断していた。
     */
    public static MinecraftPacket serverboundTo5(MinecraftPacket packet, int ownEntityId) {
        return serverboundTo5(packet, ownEntityId, ProtocolVersion.V1_7_10);
    }

    /**
     * サーバー行きパケットをハイブリッド形式から 4 ワイヤへ付け替える。
     * 4⇔5 のサーバー行きパケット ID・形式は同一（Stage A で確定）のため
     * 実装は 5 と共通で、名前解決の世代だけが違う。4 の Stage E で追加：
     * 従来は ProxySession が誤って clientboundTo4 をサーバー行きにも
     * 適用しており、5 の Stage E で直したのと同種の素通しが 4 でも
     * 起きていた。
     */
    public static MinecraftPacket serverboundTo4(MinecraftPacket packet, int ownEntityId) {
        return serverboundTo5(packet, ownEntityId, ProtocolVersion.V1_7_2);
    }

    private static MinecraftPacket serverboundTo5(MinecraftPacket packet, int ownEntityId,
            int version) {
        PacketIds.PacketName name = PacketIds.nameFor(packet.id(), PacketIds.State.PLAY,
                PacketIds.Direction.SERVERBOUND, version).orElse(null);
        if (name == null) {
            return packet;
        }
        try {
            byte[] payload = packet.payload();
            PacketIO.Reader reader = new PacketIO.Reader(payload);
            PacketIO.Writer writer = new PacketIO.Writer();
            switch (name) {
                case PLAY_WINDOW_CLICK -> {
                    writer.writeByte(reader.readByte()); // windowId
                    writer.writeShort(reader.readShort()); // slot
                    writer.writeByte(reader.readByte()); // button
                    writer.writeShort(reader.readShort()); // actionNumber
                    writer.writeByte(reader.readByte()); // mode
                    writeSlot5(reader, writer, payload, true);
                }
                case PLAY_SET_CREATIVE_SLOT -> {
                    writer.writeShort(reader.readShort()); // slot
                    writeSlot5(reader, writer, payload, true);
                }
                case PLAY_BLOCK_PLACE -> {
                    writePosition5(reader, writer, 0, true); // y は u8
                    writer.writeByte((byte) reader.readVarInt()); // direction
                    writer.writeShort(-1); // heldItem はハイブリッドに無いため空
                    writer.writeByte(reader.readByte()); // cursorX
                    writer.writeByte(reader.readByte()); // cursorY
                    writer.writeByte(reader.readByte()); // cursorZ
                }
                case PLAY_BLOCK_DIG -> {
                    writer.writeByte((byte) reader.readVarInt()); // status
                    writePosition5(reader, writer, 0, true); // y は u8
                    writer.writeByte(reader.readByte()); // face
                }
                case PLAY_UPDATE_SIGN -> {
                    writePosition5(reader, writer, 1, true); // y は i16
                    for (int i = 0; i < 4; i++) {
                        writer.writeString(jsonToPlain5(reader.readString()));
                    }
                }
                case PLAY_SET_PLAYER_POSITION -> {
                    double x = reader.readDouble();
                    double y = reader.readDouble();
                    writer.writeDouble(x);
                    writer.writeDouble(y + 1.62); // stance（目の高さ）
                    writer.writeDouble(y);
                    writer.writeDouble(reader.readDouble()); // z
                    writer.writeBoolean(reader.readBoolean()); // onGround
                }
                case PLAY_SET_PLAYER_POSITION_AND_ROTATION -> {
                    double x = reader.readDouble();
                    double y = reader.readDouble();
                    writer.writeDouble(x);
                    writer.writeDouble(y + 1.62); // stance
                    writer.writeDouble(y);
                    writer.writeDouble(reader.readDouble()); // z
                    writer.writeFloat(reader.readFloat()); // yaw
                    writer.writeFloat(reader.readFloat()); // pitch
                    writer.writeBoolean(reader.readBoolean()); // onGround
                }
                case PLAY_ARM_ANIMATION -> {
                    // ハイブリッド（47）は空ペイロード。5 ワイヤは
                    // entityId i32＋animation i8 が要る。クライアントが
                    // サーバーへ送るアニメーションは腕振り（0）だけ。
                    if (ownEntityId < 0) {
                        return null;
                    }
                    writer.writeInt(ownEntityId);
                    writer.writeByte(0);
                }
                case PLAY_ENTITY_ACTION -> {
                    writer.writeInt(reader.readVarInt()); // entityId
                    writer.writeByte((byte) reader.readVarInt()); // actionId
                    writer.writeInt(reader.readVarInt()); // jumpBoost
                }
                case PLAY_USE_ENTITY -> {
                    writer.writeInt(reader.readVarInt()); // target
                    int mouse = reader.readVarInt();
                    writer.writeByte((byte) mouse);
                    if (mouse == 2) {
                        writer.writeFloat(reader.readFloat());
                        writer.writeFloat(reader.readFloat());
                        writer.writeFloat(reader.readFloat());
                    }
                }
                case PLAY_CLIENT_COMMAND -> {
                    writer.writeByte((byte) reader.readVarInt()); // payload
                }
                case PLAY_CLIENT_INFORMATION -> {
                    writer.writeString(reader.readString()); // locale
                    writer.writeByte(reader.readByte()); // viewDistance
                    writer.writeByte(reader.readByte()); // chatFlags
                    writer.writeBoolean(reader.readBoolean()); // chatColors
                    writer.writeByte(0); // difficulty（ハイブリッドに無い。専用サーバーでは不使用）
                    int skinParts = reader.readUnsignedByte();
                    writer.writeBoolean((skinParts & 0x01) != 0); // showCape
                }
                case PLAY_STEER_VEHICLE -> {
                    writer.writeFloat(reader.readFloat()); // sideways
                    writer.writeFloat(reader.readFloat()); // forward
                    int flags = reader.readUnsignedByte();
                    writer.writeBoolean((flags & 0x01) != 0); // jump
                    writer.writeBoolean((flags & 0x02) != 0); // unmount
                }
                case PLAY_TAB_COMPLETE_TO_SERVER -> {
                    writer.writeString(reader.readString()); // text
                    // ハイブリッドの対象ブロック（有無＋座標）は 5 に無いため捨てる
                    if (reader.readUnsignedByte() != 0) {
                        reader.readBytes(8);
                    }
                }
                case PLAY_PLUGIN_MESSAGE_TO_SERVER -> {
                    writer.writeString(reader.readString()); // channel
                    byte[] data = reader.readBytes(reader.remaining());
                    writer.writeShort(data.length);
                    writer.writeBytes(data);
                }
                default -> {
                    return packet;
                }
            }
            requireEnd(reader, name);
            return new MinecraftPacket(packet.id(), writer.bytes());
        } catch (IllegalArgumentException e) {
            LOG.warning((version == ProtocolVersion.V1_7_2 ? "4" : "5")
                    + "へのサーバー行き付け替えができなかったため素通しします（"
                    + name + "）: " + e.getMessage());
            return packet;
        }
    }
}
