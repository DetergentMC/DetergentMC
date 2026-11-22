package io.detergentmc.legacy;

import io.detergentmc.legacy.LegacyPacketIO.LegacyPacket;
import io.detergentmc.legacy.LegacyPacketIO.Modifier;
import io.detergentmc.legacy.LegacyPacketIO.Property;
import io.detergentmc.legacy.LegacyWire.MetaEntry;
import io.detergentmc.legacy.LegacyWire.Slot;
import io.detergentmc.protocol.MinecraftPacket;
import io.detergentmc.protocol.PacketIO;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;

/**
 * Translates play-phase packets between legacy protocol 78 (1.6.4)
 * and the modern side of the bridge: a protocol-5 (1.7.10) wire
 * connection to the DetergentMC core.
 *
 * <p>Why protocol 5 is the bridge target: its slot form (i16 id +
 * gzip NBT), chunk section content, bulk header, metadata bitfield
 * form, name-based player list and i32 keep-alive are all identical
 * or near-identical to the legacy forms, and every other layout used
 * here is the one the core itself emits/expects for protocol-5
 * clients (as implemented by ProxySession / SlotTranslator /
 * ChunkTranslator for that protocol and verified against real
 * vanilla 1.7.10 in that work).
 *
 * <p>Packets with no legacy counterpart, or whose conversion is not
 * defined here, are DROPPED (and logged at FINE level). Nothing is
 * passed through blindly: an unconverted packet would desynchronise
 * the legacy stream, which has no length prefix to recover from.
 */
public final class LegacyPlayTranslator {

    private static final Logger LOG = Logger.getLogger(LegacyPlayTranslator.class.getName());

    // Modern (protocol 5) clientbound packet IDs.
    private static final int M_KEEP_ALIVE = 0x00;
    private static final int M_JOIN_GAME = 0x01;
    private static final int M_CHAT = 0x02;
    private static final int M_TIME = 0x03;
    private static final int M_EQUIPMENT = 0x04;
    private static final int M_SPAWN_POSITION = 0x05;
    private static final int M_HEALTH = 0x06;
    private static final int M_RESPAWN = 0x07;
    private static final int M_SYNC_POSITION = 0x08;
    private static final int M_HELD_ITEM = 0x09;
    private static final int M_USE_BED = 0x0A;
    private static final int M_ANIMATION = 0x0B;
    private static final int M_SPAWN_PLAYER = 0x0C;
    private static final int M_COLLECT = 0x0D;
    private static final int M_SPAWN_OBJECT = 0x0E;
    private static final int M_SPAWN_MOB = 0x0F;
    private static final int M_SPAWN_PAINTING = 0x10;
    private static final int M_SPAWN_ORB = 0x11;
    private static final int M_VELOCITY = 0x12;
    private static final int M_DESTROY = 0x13;
    private static final int M_ENTITY = 0x14;
    private static final int M_REL_MOVE = 0x15;
    private static final int M_ENTITY_LOOK = 0x16;
    private static final int M_MOVE_LOOK = 0x17;
    private static final int M_TELEPORT = 0x18;
    private static final int M_HEAD_LOOK = 0x19;
    private static final int M_STATUS = 0x1A;
    private static final int M_ATTACH = 0x1B;
    private static final int M_METADATA = 0x1C;
    private static final int M_EFFECT = 0x1D;
    private static final int M_REMOVE_EFFECT = 0x1E;
    private static final int M_EXPERIENCE = 0x1F;
    private static final int M_ATTRIBUTES = 0x20;
    private static final int M_CHUNK = 0x21;
    private static final int M_MULTI_BLOCK = 0x22;
    private static final int M_BLOCK_CHANGE = 0x23;
    private static final int M_BLOCK_ACTION = 0x24;
    private static final int M_BREAK_ANIMATION = 0x25;
    private static final int M_BULK = 0x26;
    private static final int M_EXPLOSION = 0x27;
    private static final int M_WORLD_EVENT = 0x28;
    private static final int M_NAMED_SOUND = 0x29;
    private static final int M_PARTICLES = 0x2A;
    private static final int M_GAME_EVENT = 0x2B;
    private static final int M_WEATHER = 0x2C;
    private static final int M_OPEN_WINDOW = 0x2D;
    private static final int M_CLOSE_WINDOW = 0x2E;
    private static final int M_SET_SLOT = 0x2F;
    private static final int M_WINDOW_ITEMS = 0x30;
    private static final int M_WINDOW_PROPERTY = 0x31;
    private static final int M_CONFIRM = 0x32;
    private static final int M_UPDATE_SIGN = 0x33;
    private static final int M_MAP = 0x34;
    private static final int M_BLOCK_ENTITY = 0x35;
    private static final int M_SIGN_EDITOR = 0x36;
    private static final int M_STATISTICS = 0x37;
    private static final int M_PLAYER_INFO = 0x38;
    private static final int M_ABILITIES = 0x39;
    private static final int M_TAB_COMPLETE = 0x3A;
    private static final int M_SCORE_OBJECTIVE = 0x3B;
    private static final int M_SCORE = 0x3C;
    private static final int M_DISPLAY_SCORE = 0x3D;
    private static final int M_TEAMS = 0x3E;
    private static final int M_PLUGIN = 0x3F;
    private static final int M_DISCONNECT = 0x40;

    // Modern (protocol 5) serverbound packet IDs.
    private static final int S_KEEP_ALIVE = 0x00;
    private static final int S_CHAT = 0x01;
    private static final int S_USE_ENTITY = 0x02;
    private static final int S_FLYING = 0x03;
    private static final int S_POSITION = 0x04;
    private static final int S_LOOK = 0x05;
    private static final int S_POS_LOOK = 0x06;
    private static final int S_DIG = 0x07;
    private static final int S_PLACE = 0x08;
    private static final int S_HELD_ITEM = 0x09;
    private static final int S_ARM_ANIMATION = 0x0A;
    private static final int S_ENTITY_ACTION = 0x0B;
    private static final int S_STEER = 0x0C;
    private static final int S_CLOSE_WINDOW = 0x0D;
    private static final int S_WINDOW_CLICK = 0x0E;
    private static final int S_CONFIRM = 0x0F;
    private static final int S_CREATIVE = 0x10;
    private static final int S_ENCHANT = 0x11;
    private static final int S_UPDATE_SIGN = 0x12;
    private static final int S_ABILITIES = 0x13;
    private static final int S_TAB_COMPLETE = 0x14;
    private static final int S_CLIENT_INFO = 0x15;
    private static final int S_CLIENT_COMMAND = 0x16;
    private static final int S_PLUGIN = 0x17;

    /** Per-session translation state. */
    public static final class State {
        public int ownEntityId = -1;
        public double lastX;
        public double lastY;
        public double lastZ;
        public float lastYaw;
        public float lastPitch;
        public boolean positionKnown;
    }

    /** The legacy protocol of the client being translated for. */
    private final int clientProtocol;

    public LegacyPlayTranslator() {
        this(LegacyProtocol.V1_6_4);
    }

    public LegacyPlayTranslator(int clientProtocol) {
        this.clientProtocol = clientProtocol;
    }

    // ------------------------------------------------------------------
    // Slot / metadata helpers (modern protocol-5 forms)
    // ------------------------------------------------------------------

    static Slot readModernSlot(PacketIO.Reader reader) {
        int itemId = reader.readShort();
        if (itemId < 0) {
            return Slot.EMPTY;
        }
        int count = reader.readUnsignedByte();
        int damage = reader.readShort();
        int nbtLen = reader.readShort();
        byte[] nbt = nbtLen < 0 ? null : reader.readBytes(nbtLen);
        return new Slot(itemId, count, damage, nbt);
    }

    static void writeModernSlot(PacketIO.Writer writer, Slot slot) {
        if (slot == null || slot.isEmpty()) {
            writer.writeShort(-1);
            return;
        }
        writer.writeShort(slot.itemId());
        writer.writeByte(slot.count());
        writer.writeShort(slot.damage());
        if (slot.nbtGzip() == null) {
            writer.writeShort(-1);
        } else {
            writer.writeShort(slot.nbtGzip().length);
            writer.writeBytes(slot.nbtGzip());
        }
    }

    /** Applies the 5->78 item approximation to a slot. */
    private static Slot remapSlot(Slot slot) {
        return remapSlot(slot, LegacyProtocol.V1_6_4);
    }

    /** Applies the item approximation for the client protocol. */
    private static Slot remapSlot(Slot slot, int legacyProtocol) {
        if (slot.isEmpty()) {
            return slot;
        }
        int mapped = legacyProtocol == LegacyProtocol.V1_0
                ? LegacyChunkRemap.itemId5To22(slot.itemId())
                : legacyProtocol == LegacyProtocol.V1_1
                ? LegacyChunkRemap.itemId5To23(slot.itemId())
                : legacyProtocol == LegacyProtocol.V1_2_3
                ? LegacyChunkRemap.itemId5To28(slot.itemId())
                : legacyProtocol == LegacyProtocol.V1_2_5
                ? LegacyChunkRemap.itemId5To29(slot.itemId())
                : legacyProtocol == LegacyProtocol.V1_3_2
                ? LegacyChunkRemap.itemId5To39(slot.itemId())
                : legacyProtocol == LegacyProtocol.V1_4_2
                ? LegacyChunkRemap.itemId5To47Old(slot.itemId())
                : legacyProtocol == LegacyProtocol.V1_4_5
                        ? LegacyChunkRemap.itemId5To49(slot.itemId())
                        : LegacyProtocol.is1_4Generation(legacyProtocol)
                                ? LegacyChunkRemap.itemId5To51(slot.itemId())
                                : LegacyProtocol.is1_5Generation(legacyProtocol)
                                ? LegacyChunkRemap.itemId5To61(slot.itemId())
                                : LegacyChunkRemap.itemId5To78(slot.itemId());
        return mapped == slot.itemId() ? slot
                : new Slot(mapped, slot.count(), slot.damage(), slot.nbtGzip());
    }

    /** Block value approximation for the client protocol. */
    private int blockValueForClient(int value) {
        if (clientProtocol == LegacyProtocol.V1_0) {
            return LegacyChunkRemap.blockValue5To22(value);
        }
        if (clientProtocol == LegacyProtocol.V1_1) {
            return LegacyChunkRemap.blockValue5To23(value);
        }
        if (LegacyProtocol.is1_2Generation(clientProtocol)) {
            return LegacyChunkRemap.blockValue5To29(value);
        }
        if (LegacyProtocol.is1_3Generation(clientProtocol)) {
            return LegacyChunkRemap.blockValue5To39(value);
        }
        if (LegacyProtocol.is1_4Generation(clientProtocol)) {
            return LegacyChunkRemap.blockValue5To51(value);
        }
        if (LegacyProtocol.is1_5Generation(clientProtocol)) {
            return LegacyChunkRemap.blockValue5To61(value);
        }
        return LegacyChunkRemap.blockValue5To78(value);
    }

    /** (id, meta) block approximation for the client protocol. */
    private int[] blockIdMetaForClient(int id, int meta) {
        if (clientProtocol == LegacyProtocol.V1_0) {
            return LegacyChunkRemap.blockIdAndMeta5To22(id, meta);
        }
        if (clientProtocol == LegacyProtocol.V1_1) {
            return LegacyChunkRemap.blockIdAndMeta5To23(id, meta);
        }
        if (LegacyProtocol.is1_2Generation(clientProtocol)) {
            return LegacyChunkRemap.blockIdAndMeta5To29(id, meta);
        }
        if (LegacyProtocol.is1_3Generation(clientProtocol)) {
            return LegacyChunkRemap.blockIdAndMeta5To39(id, meta);
        }
        if (LegacyProtocol.is1_4Generation(clientProtocol)) {
            return LegacyChunkRemap.blockIdAndMeta5To51(id, meta);
        }
        if (LegacyProtocol.is1_5Generation(clientProtocol)) {
            return LegacyChunkRemap.blockIdAndMeta5To61(id, meta);
        }
        return new int[]{LegacyChunkRemap.BLOCK_ID_5_TO_78.getOrDefault(id, id), meta};
    }

    /** Reads modern (protocol-5 form) metadata entries. */
    static List<MetaEntry> readModernMetadata(PacketIO.Reader reader) {
        List<MetaEntry> entries = new ArrayList<>();
        while (true) {
            int header = reader.readUnsignedByte();
            if (header == 0x7F) {
                return entries;
            }
            int type = (header >> 5) & 0x7;
            int index = header & 0x1F;
            Object value = switch (type) {
                case 0 -> (byte) reader.readByte();
                case 1 -> (short) reader.readShort();
                case 2 -> reader.readInt();
                case 3 -> reader.readFloat();
                case 4 -> reader.readString();
                case 5 -> readModernSlot(reader);
                case 6 -> new int[]{reader.readInt(), reader.readInt(), reader.readInt()};
                case 7 -> new float[]{reader.readFloat(), reader.readFloat(), reader.readFloat()};
                default -> throw new IllegalArgumentException("metadata type " + type);
            };
            entries.add(new MetaEntry(index, type, value));
        }
    }

    /** Writes metadata in the modern (protocol-5) form. */
    static void writeModernMetadata(PacketIO.Writer writer, List<MetaEntry> entries) {
        for (MetaEntry e : entries) {
            writer.writeByte(((e.type() & 0x7) << 5) | (e.index() & 0x1F));
            switch (e.type()) {
                case 0 -> writer.writeByte((Byte) e.value());
                case 1 -> writer.writeShort((Short) e.value());
                case 2 -> writer.writeInt((Integer) e.value());
                case 3 -> writer.writeFloat((Float) e.value());
                case 4 -> writer.writeString((String) e.value());
                case 5 -> writeModernSlot(writer, (Slot) e.value());
                case 6 -> {
                    int[] p = (int[]) e.value();
                    writer.writeInt(p[0]);
                    writer.writeInt(p[1]);
                    writer.writeInt(p[2]);
                }
                case 7 -> {
                    float[] p = (float[]) e.value();
                    writer.writeFloat(p[0]);
                    writer.writeFloat(p[1]);
                    writer.writeFloat(p[2]);
                }
                default -> throw new IllegalArgumentException("metadata type " + e.type());
            }
        }
        writer.writeByte(0x7F);
    }

    /**
     * Modern metadata -> legacy metadata: identical bitfield form;
     * only type 7 (rotation, absent in protocol 78) is dropped and
     * slot item IDs are remapped.
     */
    static List<MetaEntry> metadataToLegacy(List<MetaEntry> modern) {
        return metadataToLegacy(modern, LegacyProtocol.V1_6_4);
    }

    static List<MetaEntry> metadataToLegacy(List<MetaEntry> modern, int legacyProtocol) {
        List<MetaEntry> out = new ArrayList<>(modern.size());
        for (MetaEntry e : modern) {
            if (e.type() == 7) {
                continue;
            }
            if (e.type() == 5) {
                Slot slot = (Slot) e.value();
                if (slot.isEmpty()
                        && LegacyProtocol.watcherSlotHasNoNbt(legacyProtocol)) {
                    // The plain watcher-slot generations (legacy
                    // 47 and 39) have no agreed wire form for an
                    // empty slot entry: vanilla servers of those
                    // generations never send one (a dropped item
                    // always carries its stack), and 1.3.2's own
                    // reader would misread the -1-only form its
                    // neighbour generation uses. Drop the entry —
                    // an empty slot carries nothing to show.
                    continue;
                }
                out.add(new MetaEntry(e.index(), e.type(),
                        remapSlot(slot, legacyProtocol)));
            } else {
                out.add(e);
            }
        }
        return out;
    }

    // ------------------------------------------------------------------
    // Clientbound: modern (protocol 5) -> legacy 78
    // ------------------------------------------------------------------

    /**
     * Translates one clientbound packet. The modern Join Game becomes
     * the legacy login packet (0x01) — the session sends it as the
     * legacy login response. Empty means: drop the packet.
     */
    public Optional<LegacyPacket> toLegacy(MinecraftPacket modern, State state) {
        try {
            PacketIO.Reader r = new PacketIO.Reader(modern.payload());
            LegacyPacket out = switch (modern.id()) {
                case M_KEEP_ALIVE -> new LegacyPacket(0x00, List.of(r.readInt()));
                case M_JOIN_GAME -> {
                    int entityId = r.readInt();
                    int gamemode = r.readUnsignedByte();
                    int dimension = r.readByte();
                    int difficulty = r.readUnsignedByte();
                    int maxPlayers = r.readUnsignedByte();
                    String levelType = r.readString();
                    // reducedDebug bool (if present) is not carried.
                    state.ownEntityId = entityId;
                    // Protocols 23 (1.1) and 22 (1.0.0): the
                    // transitional login — an (empty) username
                    // string and an i64 world seed follow the
                    // entity id, then (23 only) the level type;
                    // the tail is gamemode i32 and four i8s. The
                    // modern side has no world seed: 0 is sent
                    // (the client only derives biome tinting from
                    // it). The world height is 128 in these
                    // generations.
                    // Protocol 29 (1.2.5): the login packet has
                    // eight fields — an (empty) username string
                    // follows the entity id, and gamemode /
                    // dimension are i32 on its wire.
                    yield LegacyProtocol.is1_1OrOlderGeneration(clientProtocol)
                            ? clientProtocol == LegacyProtocol.V1_1
                                    ? new LegacyPacket(0x01, List.of(entityId, "", 0L,
                                            levelType, gamemode & 0x7, dimension,
                                            difficulty, 128, maxPlayers))
                                    : new LegacyPacket(0x01, List.of(entityId, "", 0L,
                                            gamemode & 0x7, dimension, difficulty, 128,
                                            maxPlayers))
                            : LegacyProtocol.is1_2Generation(clientProtocol)
                            ? new LegacyPacket(0x01, List.of(entityId, "", levelType,
                                    gamemode & 0x7, dimension, difficulty, 0, maxPlayers))
                            : new LegacyPacket(0x01, List.of(entityId, levelType,
                                    gamemode & 0x7, dimension, difficulty, 0, maxPlayers));
                }
                case M_CHAT -> new LegacyPacket(0x03,
                        List.of(LegacyText.jsonToPlain(r.readString())));
                case M_TIME -> {
                    long total = r.readLong();
                    long dayTime = r.readLong();
                    // Protocol 39 (1.3.2): the time packet carries a
                    // single i64 — the world's one "Time" value,
                    // which is both its age and its day time (the
                    // split arrived in 1.4). The modern day time is
                    // the value a 1.3.2 client must see: it is what
                    // /time set and sleeping move, exactly like the
                    // single value on a real 1.3.2 server. Protocol
                    // 29 (1.2.5) has the same single-value form
                    // (measured identical to 39).
                    yield LegacyProtocol.is1_3Generation(clientProtocol)
                            || LegacyProtocol.is1_2OrOlderGeneration(clientProtocol)
                            ? new LegacyPacket(0x04, List.of(dayTime))
                            : new LegacyPacket(0x04, List.of(total, dayTime));
                }
                case M_EQUIPMENT -> new LegacyPacket(0x05,
                        List.of(r.readInt(), (int) r.readShort(), remapSlot(readModernSlot(r), clientProtocol)));
                case M_SPAWN_POSITION -> new LegacyPacket(0x06,
                        List.of(r.readInt(), r.readInt(), r.readInt()));
                case M_HEALTH -> {
                    float health = r.readFloat();
                    int food = (int) r.readShort();
                    float saturation = r.readFloat();
                    // Protocols 61 and 51: health is an I16 on the wire.
                    yield LegacyProtocol.is1_5OrOlderGeneration(clientProtocol)
                            ? new LegacyPacket(0x08, List.of(
                                    Math.round(health), food, saturation))
                            : new LegacyPacket(0x08, List.of(health, food, saturation));
                }
                case M_RESPAWN -> {
                    int dimension = r.readInt();
                    int difficulty = r.readUnsignedByte();
                    int gamemode = r.readUnsignedByte();
                    String levelType = r.readString();
                    // Protocols 23 (1.1) and 22 (1.0.0): the
                    // transitional respawn — dimension / difficulty
                    // / gamemode as i8s, world height 128 as i16,
                    // then the i64 world seed (0 from the bridge,
                    // as in the login) and, in 23 only, the level
                    // type string.
                    yield LegacyProtocol.is1_1OrOlderGeneration(clientProtocol)
                            ? clientProtocol == LegacyProtocol.V1_1
                                    ? new LegacyPacket(0x09, List.of(dimension,
                                            difficulty, gamemode, 128, 0L, levelType))
                                    : new LegacyPacket(0x09, List.of(dimension,
                                            difficulty, gamemode, 128, 0L))
                            : new LegacyPacket(0x09, List.of(dimension,
                                    difficulty, gamemode, 256, levelType));
                }
                case M_SYNC_POSITION -> {
                    double x = r.readDouble();
                    double y = r.readDouble();
                    double z = r.readDouble();
                    float yaw = r.readFloat();
                    float pitch = r.readFloat();
                    int flags = r.readByte();
                    if ((flags & 0x01) != 0) {
                        x += state.lastX;
                    }
                    if ((flags & 0x02) != 0) {
                        y += state.lastY;
                    }
                    if ((flags & 0x04) != 0) {
                        z += state.lastZ;
                    }
                    if ((flags & 0x08) != 0) {
                        yaw += state.lastYaw;
                    }
                    if ((flags & 0x10) != 0) {
                        pitch += state.lastPitch;
                    }
                    state.lastX = x;
                    state.lastY = y;
                    state.lastZ = z;
                    state.lastYaw = yaw;
                    state.lastPitch = pitch;
                    state.positionKnown = true;
                    yield new LegacyPacket(0x0D, List.of(x, y + 1.62, y, z, yaw, pitch, false));
                }
                case M_HELD_ITEM -> new LegacyPacket(0x10, List.of((int) r.readByte()));
                case M_USE_BED -> new LegacyPacket(0x11,
                        List.of(r.readInt(), 0, r.readInt(), r.readUnsignedByte(), r.readInt()));
                case M_ANIMATION -> new LegacyPacket(0x12,
                        List.of(r.readVarInt(), (int) r.readUnsignedByte()));
                case M_SPAWN_PLAYER -> {
                    int entityId = r.readVarInt();
                    r.readString(); // uuid (legacy has no uuid here)
                    String name = r.readString();
                    int props = r.readVarInt();
                    for (int i = 0; i < props; i++) {
                        r.readString();
                        r.readString();
                        r.readString();
                    }
                    int x = r.readInt();
                    int y = r.readInt();
                    int z = r.readInt();
                    int yaw = r.readByte();
                    int pitch = r.readByte();
                    int item = r.readShort();
                    List<MetaEntry> meta = metadataToLegacy(readModernMetadata(r), clientProtocol);
                    // Protocols 29 (1.2.5) and older: the
                    // spawn_player packet has no trailing metadata
                    // — the client builds the player entity with
                    // default metadata.
                    yield LegacyProtocol.is1_2OrOlderGeneration(clientProtocol)
                            ? new LegacyPacket(0x14, List.of(entityId, name, x, y, z, yaw, pitch, item))
                            : new LegacyPacket(0x14, List.of(entityId, name, x, y, z, yaw, pitch, item, meta));
                }
                case M_COLLECT -> new LegacyPacket(0x16, List.of(r.readInt(), r.readInt()));
                case M_SPAWN_OBJECT -> {
                    int entityId = r.readVarInt();
                    int type = r.readUnsignedByte();
                    int x = r.readInt();
                    int y = r.readInt();
                    int z = r.readInt();
                    int pitch = r.readByte();
                    int yaw = r.readByte();
                    int data = r.readInt();
                    int vx = 0;
                    int vy = 0;
                    int vz = 0;
                    if (data != 0) {
                        vx = r.readShort();
                        vy = r.readShort();
                        vz = r.readShort();
                    }
                    if (type == 70) {
                        // Falling block: the modern data packs the meta
                        // at <<16; legacy clients get the block id only.
                        data = data & 0xFFFF;
                        if (data == 0) {
                            data = 12; // sand fallback so velocity stays valid
                        }
                    }
                    if (type == 10 && (LegacyProtocol.is1_4Generation(clientProtocol)
                            || LegacyProtocol.is1_3Generation(clientProtocol))
                            && (data == 3 || data == 4 || data == 5)) {
                        // Minecart variants added in 1.5 (TNT = 3,
                        // spawner = 4, hopper = 5) do not exist in
                        // 1.4.7: fall back to a plain minecart. data
                        // == 0 also drops the velocity tail, which the
                        // spawn_object codec handles.
                        data = 0;
                    }
                    if (type == 10
                            && LegacyProtocol.is1_1OrOlderGeneration(clientProtocol)) {
                        // Protocols 23 (1.1) and 22 (1.0.0): the
                        // minecart variant lives in the TYPE byte,
                        // not the data — their trackers construct
                        // type 11 (chest) and 12 (furnace) spawns
                        // with data 0 (measured in the real jars).
                        // The variants added later (TNT = 3,
                        // spawner = 4, hopper = 5, command = 6)
                        // fall back to a plain minecart.
                        if (data == 1) {
                            type = 11;
                            data = 0;
                        } else if (data == 2) {
                            type = 12;
                            data = 0;
                        } else if (data >= 3 && data <= 6) {
                            data = 0;
                        }
                    }
                    if (type == 75
                            && LegacyProtocol.is1_1OrOlderGeneration(clientProtocol)) {
                        // Thrown exp bottles arrived in 1.2 (the
                        // 1.1 / 1.0.0 trackers have no type-75
                        // construction): drop the spawn. (Thrown
                        // potions, type 73, DO exist in 1.1 and
                        // 1.0.0 and pass through.)
                        yield null;
                    }
                    if (type == 71
                            && LegacyProtocol.is1_1OrOlderGeneration(clientProtocol)) {
                        // Item frames arrived in 1.4 — and in
                        // 1.1 / 1.0.0 the type byte 71 means a
                        // falling GRAVEL block, so passing an item
                        // frame through would render falling
                        // gravel: drop the spawn.
                        yield null;
                    }
                    if (type == 22 && LegacyProtocol.hasNoFireworkRocket(clientProtocol)) {
                        // Firework rockets arrived in 1.4.6: a 49
                        // or legacy-47 (1.4.2) client has no such
                        // object type. The entity is a two-second
                        // visual; drop the spawn (its destroy
                        // follows later and is harmless).
                        yield null;
                    }
                    // The two rotation bytes keep their wire order
                    // (pitch, yaw) on both generations.
                    yield new LegacyPacket(0x17, List.of(entityId, type, x, y, z,
                            pitch, yaw, data, vx, vy, vz));
                }
                case M_SPAWN_MOB -> {
                    int entityId = r.readVarInt();
                    int type = r.readUnsignedByte();
                    if (LegacyProtocol.is1_5OrOlderGeneration(clientProtocol) && type == 100) {
                        type = 90; // horse -> pig (horses arrived in 1.6)
                    }
                    if ((LegacyProtocol.is1_3Generation(clientProtocol)
                            || LegacyProtocol.is1_2OrOlderGeneration(clientProtocol))
                            && (type == 64 || type == 65 || type == 66)) {
                        // Wither (64), bat (65) and witch (66)
                        // arrived in 1.4: a 1.3.2 or 1.2-generation
                        // client has no such mob types (their entity
                        // registries are identical, measured).
                        // Same substitution as the horse: a pig
                        // keeps the entity visible and moving.
                        type = 90;
                    }
                    if (LegacyProtocol.is1_1OrOlderGeneration(clientProtocol)
                            && (type == 98 || type == 99)) {
                        // Ocelot (98) and iron golem (99) arrived
                        // in 1.2: a 1.1 / 1.0.0 client has no such
                        // mob types. Same pig substitution.
                        type = 90;
                    }
                    int x = r.readInt();
                    int y = r.readInt();
                    int z = r.readInt();
                    int yaw = r.readByte();
                    int pitch = r.readByte();
                    int head = r.readByte();
                    int vx = r.readShort();
                    int vy = r.readShort();
                    int vz = r.readShort();
                    List<MetaEntry> meta = metadataToLegacy(readModernMetadata(r), clientProtocol);
                    // Protocol 29 (1.2.5): the spawn_mob packet has
                    // no velocity shorts — the client interpolates
                    // from the entity's movement packets.
                    // Protocols 23 (1.1) and 22 (1.0.0): likewise
                    // no velocity shorts, and only TWO rotation
                    // bytes (the head-yaw byte arrived in 1.2).
                    yield LegacyProtocol.is1_1OrOlderGeneration(clientProtocol)
                            ? new LegacyPacket(0x18, List.of(entityId, type, x, y, z,
                                    yaw, pitch, meta))
                            : LegacyProtocol.is1_2Generation(clientProtocol)
                            ? new LegacyPacket(0x18, List.of(entityId, type, x, y, z,
                                    yaw, pitch, head, meta))
                            : new LegacyPacket(0x18, List.of(entityId, type, x, y, z,
                                    yaw, pitch, head, vx, vy, vz, meta));
                }
                case M_SPAWN_PAINTING -> new LegacyPacket(0x19, List.of(r.readVarInt(),
                        r.readString(), r.readInt(), r.readInt(), r.readInt(), r.readInt()));
                case M_SPAWN_ORB -> new LegacyPacket(0x1A, List.of(r.readVarInt(),
                        r.readInt(), r.readInt(), r.readInt(), (int) r.readShort()));
                case M_VELOCITY -> new LegacyPacket(0x1C, List.of(r.readInt(),
                        (int) r.readShort(), (int) r.readShort(), (int) r.readShort()));
                case M_DESTROY -> {
                    int count = r.readUnsignedByte();
                    List<Integer> ids = new ArrayList<>(count);
                    for (int i = 0; i < count; i++) {
                        ids.add(r.readInt());
                    }
                    yield new LegacyPacket(0x1D, List.of(ids));
                }
                case M_ENTITY -> new LegacyPacket(0x1E, List.of(r.readInt()));
                case M_REL_MOVE -> new LegacyPacket(0x1F, List.of(r.readInt(),
                        (int) r.readByte(), (int) r.readByte(), (int) r.readByte()));
                case M_ENTITY_LOOK -> new LegacyPacket(0x20, List.of(r.readInt(),
                        (int) r.readByte(), (int) r.readByte()));
                case M_MOVE_LOOK -> new LegacyPacket(0x21, List.of(r.readInt(),
                        (int) r.readByte(), (int) r.readByte(), (int) r.readByte(),
                        (int) r.readByte(), (int) r.readByte()));
                case M_TELEPORT -> new LegacyPacket(0x22, List.of(r.readInt(),
                        r.readInt(), r.readInt(), r.readInt(),
                        (int) r.readByte(), (int) r.readByte()));
                case M_HEAD_LOOK -> LegacyPacketTable.packet(clientProtocol, 0x23) != null
                        ? new LegacyPacket(0x23, List.of(r.readInt(), (int) r.readByte()))
                        : null; // 0x23 does not exist before 1.2 (protocols 23/22): drop
                case M_STATUS -> new LegacyPacket(0x26,
                        List.of(r.readInt(), (int) r.readByte()));
                case M_ATTACH -> {
                    int entityId = r.readInt();
                    int vehicleId = r.readInt();
                    int leash = r.readUnsignedByte();
                    // Protocols 61 and 51: no leash flag (leads arrived in 1.6).
                    yield LegacyProtocol.is1_5OrOlderGeneration(clientProtocol)
                            ? new LegacyPacket(0x27, List.of(entityId, vehicleId))
                            : new LegacyPacket(0x27, List.of(entityId, vehicleId, leash));
                }
                case M_METADATA -> new LegacyPacket(0x28, List.of(r.readInt(),
                        metadataToLegacy(readModernMetadata(r), clientProtocol)));
                case M_EFFECT -> new LegacyPacket(0x29, List.of(r.readInt(),
                        (int) r.readByte(), (int) r.readByte(), (int) r.readShort()));
                case M_REMOVE_EFFECT -> new LegacyPacket(0x2A,
                        List.of(r.readInt(), (int) r.readByte()));
                case M_EXPERIENCE -> new LegacyPacket(0x2B, List.of(r.readFloat(),
                        (int) r.readShort(), (int) r.readShort()));
                case M_ATTRIBUTES -> {
                    int entityId = r.readInt();
                    int count = r.readInt();
                    List<Property> props = new ArrayList<>(count);
                    for (int i = 0; i < count; i++) {
                        String key = r.readString();
                        double base = r.readDouble();
                        int mods = r.readShort();
                        List<Modifier> modifiers = new ArrayList<>(mods);
                        for (int j = 0; j < mods; j++) {
                            java.util.UUID uuid = r.readUuid();
                            modifiers.add(new Modifier(uuid.getMostSignificantBits(),
                                    uuid.getLeastSignificantBits(), r.readDouble(),
                                    r.readUnsignedByte()));
                        }
                        props.add(new Property(key, base, modifiers));
                    }
                    // 0x2C does not exist before 1.6 (protocol 61): drop.
                    yield LegacyPacketTable.packet(clientProtocol, 0x2C) != null
                            ? new LegacyPacket(0x2C, List.of(entityId, props))
                            : null;
                }
                case M_CHUNK -> {
                    int x = r.readInt();
                    int z = r.readInt();
                    boolean groundUp = r.readBoolean();
                    int primary = r.readShort();
                    int add = r.readShort();
                    int len = r.readInt();
                    byte[] data = r.readBytes(len);
                    // Protocols 23 (1.1) and 22 (1.0.0): the
                    // chunk is a cuboid, not sections (the
                    // pre_chunk pairing is toLegacyAll's job, as
                    // for protocol 29).
                    if (LegacyProtocol.is1_1OrOlderGeneration(clientProtocol)) {
                        LegacyChunkRemap.CuboidData cuboid =
                                LegacyChunkRemap.remapChunkToCuboid(
                                        data, primary, add, groundUp, clientProtocol);
                        yield cuboid == null ? null
                                : new LegacyPacket(0x33, List.of(x * 16, cuboid.yBase(),
                                        z * 16, 16, cuboid.sizeY(), 16,
                                        cuboid.compressedData()));
                    }
                    byte[] remapped = LegacyChunkRemap.remapSingleChunkData(
                            data, primary, add, groundUp, clientProtocol);
                    yield new LegacyPacket(0x33, List.of(x, z, groundUp, primary, add, remapped));
                }
                case M_MULTI_BLOCK -> {
                    int x = r.readInt();
                    int z = r.readInt();
                    int count = r.readShort();
                    int len = r.readInt();
                    byte[] raw = r.readBytes(len);
                    PacketIO.Reader rr = new PacketIO.Reader(raw);
                    List<int[]> records = new ArrayList<>(count);
                    for (int i = 0; i < count; i++) {
                        int pos = rr.readUnsignedShort();
                        int block = rr.readShort();
                        records.add(new int[]{pos, blockValueForClient(block)});
                    }
                    yield new LegacyPacket(0x34, List.of(x, z, records));
                }
                case M_BLOCK_CHANGE -> {
                    int x = r.readInt();
                    int y = r.readUnsignedByte();
                    int z = r.readInt();
                    int type = r.readVarInt();
                    int meta = r.readUnsignedByte();
                    int[] bm = blockIdMetaForClient(type, meta);
                    yield new LegacyPacket(0x35, List.of(x, y, z, bm[0], bm[1]));
                }
                case M_BLOCK_ACTION -> {
                    int x = r.readInt();
                    int y = r.readShort();
                    int z = r.readInt();
                    int a1 = r.readUnsignedByte();
                    int a2 = r.readUnsignedByte();
                    int blockType = r.readVarInt();
                    int mapped = blockIdMetaForClient(blockType, 0)[0];
                    // Protocol 29 (1.2.5) and older: no trailing
                    // block-type field — the client looks the
                    // block up in its world.
                    yield LegacyProtocol.is1_2OrOlderGeneration(clientProtocol)
                            ? new LegacyPacket(0x36, List.of(x, y, z, a1, a2))
                            : new LegacyPacket(0x36, List.of(x, y, z, a1, a2, mapped));
                }
                case M_BREAK_ANIMATION -> LegacyPacketTable.packet(clientProtocol, 0x37) != null
                        ? new LegacyPacket(0x37, List.of(r.readVarInt(),
                                r.readInt(), r.readInt(), r.readInt(), (int) r.readByte()))
                        : null; // 0x37 does not exist in protocol 29: drop
                case M_BULK -> {
                    int count = r.readShort();
                    int len = r.readInt();
                    boolean sky = r.readBoolean();
                    byte[] data = r.readBytes(len);
                    List<int[]> meta = new ArrayList<>(count);
                    for (int i = 0; i < count; i++) {
                        meta.add(new int[]{r.readInt(), r.readInt(),
                                r.readShort(), r.readShort()});
                    }
                    byte[] remapped = LegacyChunkRemap.remapBulkData(data, sky, meta, clientProtocol);
                    yield new LegacyPacket(0x38, List.of(sky,
                            remapped != null ? remapped : data, meta));
                }
                case M_EXPLOSION -> {
                    double x = r.readDouble();
                    double y = r.readDouble();
                    double z = r.readDouble();
                    float radius = r.readFloat();
                    int count = r.readVarInt();
                    List<int[]> offsets = new ArrayList<>(count);
                    for (int i = 0; i < count; i++) {
                        offsets.add(new int[]{r.readByte(), r.readByte(), r.readByte()});
                    }
                    yield new LegacyPacket(0x3C, List.of(x, y, z, radius, offsets,
                            r.readFloat(), r.readFloat(), r.readFloat()));
                }
                case M_WORLD_EVENT -> {
                    int effectId = r.readInt();
                    int x = r.readInt();
                    int y = r.readUnsignedByte();
                    int z = r.readInt();
                    int data = r.readInt();
                    boolean global = r.readBoolean();
                    // Protocol 39 (1.3.2): no trailing "global"
                    // flag (it arrived in 1.4); a 1.3.2 client
                    // plays every effect non-globally, as its own
                    // generation's servers present them. Protocol
                    // 29 (1.2.5) has the same five-field form
                    // (measured identical to 39).
                    yield LegacyProtocol.is1_3Generation(clientProtocol)
                            || LegacyProtocol.is1_2OrOlderGeneration(clientProtocol)
                            ? new LegacyPacket(0x3D, List.of(effectId, x, y, z, data))
                            : new LegacyPacket(0x3D, List.of(effectId, x, y, z, data, global));
                }
                case M_NAMED_SOUND -> {
                    String name = r.readString();
                    int x = r.readInt();
                    int y = r.readInt();
                    int z = r.readInt();
                    float volume = r.readFloat();
                    int pitch = r.readUnsignedByte();
                    // 0x3E does not exist in protocol 29 (named
                    // sounds arrived in 1.3): drop.
                    yield LegacyPacketTable.packet(clientProtocol, 0x3E) != null
                            ? new LegacyPacket(0x3E, List.of(name, x, y, z, volume, pitch))
                            : null;
                }
                case M_PARTICLES -> {
                    String name = r.readString();
                    List<Object> fields = new ArrayList<>();
                    fields.add(name);
                    for (int i = 0; i < 7; i++) {
                        fields.add(r.readFloat());
                    }
                    fields.add(r.readInt());
                    // 0x3F does not exist before 1.5 (protocol 51): drop.
                    yield LegacyPacketTable.packet(clientProtocol, 0x3F) != null
                            ? new LegacyPacket(0x3F, fields)
                            : null;
                }
                case M_GAME_EVENT -> new LegacyPacket(0x46,
                        List.of(r.readUnsignedByte(), (int) r.readFloat()));
                case M_WEATHER -> new LegacyPacket(0x47, List.of(r.readVarInt(),
                        (int) r.readByte(), r.readInt(), r.readInt(), r.readInt()));
                case M_OPEN_WINDOW -> {
                    int windowId = r.readUnsignedByte();
                    int typeId = r.readUnsignedByte();
                    String title = r.readString();
                    int slots = r.readUnsignedByte();
                    boolean useTitle = r.readBoolean();
                    Integer entityId = typeId == 11 ? r.readInt() : null;
                    yield new LegacyPacket(0x64, java.util.Arrays.asList(
                            windowId, typeId, title, slots, useTitle, entityId));
                }
                case M_CLOSE_WINDOW -> new LegacyPacket(0x65, List.of(r.readUnsignedByte()));
                case M_SET_SLOT -> new LegacyPacket(0x67, List.of((int) r.readByte(),
                        (int) r.readShort(), remapSlot(readModernSlot(r), clientProtocol)));
                case M_WINDOW_ITEMS -> {
                    int windowId = r.readUnsignedByte();
                    int count = r.readShort();
                    List<Slot> slots = new ArrayList<>(count);
                    for (int i = 0; i < count; i++) {
                        slots.add(remapSlot(readModernSlot(r), clientProtocol));
                    }
                    yield new LegacyPacket(0x68, List.of(windowId, slots));
                }
                case M_WINDOW_PROPERTY -> new LegacyPacket(0x69, List.of(
                        r.readUnsignedByte(), (int) r.readShort(), (int) r.readShort()));
                case M_CONFIRM -> new LegacyPacket(0x6A, List.of((int) r.readByte(),
                        (int) r.readShort(), r.readBoolean()));
                case M_UPDATE_SIGN -> new LegacyPacket(0x82, List.of(r.readInt(),
                        (int) r.readShort(), r.readInt(), r.readString(), r.readString(),
                        r.readString(), r.readString()));
                case M_BLOCK_ENTITY -> {
                    int x = r.readInt();
                    int y = (int) r.readShort();
                    int z = r.readInt();
                    int action = r.readUnsignedByte();
                    byte[] nbt = readModernNbt(r);
                    // Protocol 29 (1.2.5): the tile-entity packet
                    // carries three int parameters, not NBT — the
                    // NBT form arrived in 1.3, and spawner / head
                    // contents have no 1.2.5 wire home. Drop: the
                    // tile entity renders with client defaults.
                    // Protocols 23 (1.1) and 22 (1.0.0) have no
                    // tile-entity packet at all: likewise drop.
                    yield LegacyProtocol.is1_2Generation(clientProtocol)
                            || LegacyPacketTable.packet(clientProtocol, 0x84) == null
                            ? null
                            : new LegacyPacket(0x84, List.of(x, y, z, action, nbt));
                }
                case M_SIGN_EDITOR -> LegacyPacketTable.packet(clientProtocol, 0x85) != null
                        ? new LegacyPacket(0x85,
                                List.of(0, r.readInt(), r.readInt(), r.readInt()))
                        : null; // 0x85 does not exist before 1.6.2: drop
                case M_PLAYER_INFO -> new LegacyPacket(0xC9, List.of(r.readString(),
                        r.readBoolean(), (int) r.readShort()));
                case M_ABILITIES -> {
                    int flags = r.readUnsignedByte();
                    float flySpeed = r.readFloat();
                    float walkSpeed = r.readFloat();
                    // Protocol 29 (1.2.5): four bools
                    // (invulnerable, flying, canFly, creative);
                    // ability speeds are not on its wire at all.
                    // Protocols 61 and 51: speeds are bytes (value = byte / 255).
                    // Protocols 28 (1.2.3) and older have no
                    // abilities packet at all (it arrived in
                    // 1.2.4): drop — abilities there follow the
                    // game mode alone.
                    yield LegacyPacketTable.packet(clientProtocol, 0xCA) == null
                            ? null
                            : LegacyProtocol.is1_2Generation(clientProtocol)
                            ? new LegacyPacket(0xCA, List.of(
                                    (flags & 0x1) != 0, (flags & 0x2) != 0,
                                    (flags & 0x4) != 0, (flags & 0x8) != 0))
                            : LegacyProtocol.is1_5OrOlderGeneration(clientProtocol)
                            ? new LegacyPacket(0xCA, List.of(flags,
                                    speedToByte61(flySpeed), speedToByte61(walkSpeed)))
                            : new LegacyPacket(0xCA, List.of(flags, flySpeed, walkSpeed));
                }
                case M_TAB_COMPLETE -> {
                    int count = r.readVarInt();
                    StringBuilder joined = new StringBuilder();
                    for (int i = 0; i < count; i++) {
                        joined.append(r.readString()).append('\0');
                    }
                    // 0xCB does not exist in protocol 29 (tab
                    // completion arrived in 1.3): drop.
                    yield LegacyPacketTable.packet(clientProtocol, 0xCB) != null
                            ? new LegacyPacket(0xCB, List.of(joined.toString()))
                            : null;
                }
                // The scoreboard packets (0xCE/0xCF/0xD0/0xD1) do not
                // exist before 1.5 (protocol 51): read, then drop.
                case M_SCORE_OBJECTIVE -> {
                    String name = r.readString();
                    String title = r.readString();
                    int mode = r.readByte();
                    yield LegacyPacketTable.packet(clientProtocol, 0xCE) != null
                            ? new LegacyPacket(0xCE, List.of(name, title, mode))
                            : null;
                }
                case M_SCORE -> {
                    String name = r.readString();
                    int action = r.readByte();
                    String objective = null;
                    Integer value = null;
                    if (action != 1) {
                        objective = r.readString();
                        value = r.readInt();
                    }
                    yield LegacyPacketTable.packet(clientProtocol, 0xCF) != null
                            ? new LegacyPacket(0xCF, java.util.Arrays.asList(
                                    name, action, objective, value))
                            : null;
                }
                case M_DISPLAY_SCORE -> {
                    int position = r.readVarInt();
                    String objective = r.readString();
                    yield LegacyPacketTable.packet(clientProtocol, 0xD0) != null
                            ? new LegacyPacket(0xD0, List.of(position, objective))
                            : null;
                }
                case M_TEAMS -> {
                    String name = r.readString();
                    int mode = r.readByte();
                    String display = null;
                    String prefix = null;
                    String suffix = null;
                    Integer friendlyFire = null;
                    if (mode == 0 || mode == 2) {
                        display = r.readString();
                        prefix = r.readString();
                        suffix = r.readString();
                        friendlyFire = (int) r.readByte();
                    }
                    List<String> players = null;
                    if (mode == 0 || mode == 3 || mode == 4) {
                        int count = r.readShort();
                        players = new ArrayList<>(count);
                        for (int i = 0; i < count; i++) {
                            players.add(r.readString());
                        }
                    }
                    yield LegacyPacketTable.packet(clientProtocol, 0xD1) != null
                            ? new LegacyPacket(0xD1, java.util.Arrays.asList(
                                    name, mode, display, prefix, suffix, friendlyFire, players))
                            : null;
                }
                case M_PLUGIN -> {
                    String channel = r.readString();
                    int len = r.readShort();
                    byte[] data = r.readBytes(Math.max(len, 0));
                    if (LegacyPacketTable.packet(clientProtocol, 0xFA) == null) {
                        // Protocol 22 (1.0.0) has no plugin-message
                        // packet at all (it arrived in 1.1): drop.
                        yield null;
                    }
                    if (channel.equals("MC|RPack")) {
                        // Protocol 29 (1.2.5) and older: the client
                        // jar has no MC|TPack handling at all (and
                        // no texture-pack server property) — server
                        // texture-pack offers do not exist in these
                        // generations. Drop the offer.
                        if (LegacyProtocol.is1_2OrOlderGeneration(clientProtocol)) {
                            yield null;
                        }
                        // The core offers resource packs to 1.7 clients as
                        // a plugin message holding the URL; the legacy
                        // form is MC|TPack with url + NUL + size.
                        String url = new String(data, StandardCharsets.UTF_8);
                        byte[] tpack = (url + "\0" + "16")
                                .getBytes(StandardCharsets.UTF_8);
                        yield new LegacyPacket(0xFA, List.of("MC|TPack", tpack));
                    }
                    yield new LegacyPacket(0xFA, List.of(channel, data));
                }
                case M_DISCONNECT -> new LegacyPacket(0xFF,
                        List.of(LegacyText.jsonToPlain(r.readString())));
                default -> null;
            };
            return Optional.ofNullable(out);
        } catch (IllegalArgumentException e) {
            LOG.fine("clientbound packet 0x" + Integer.toHexString(modern.id())
                    + " dropped (conversion failed): " + e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Translates one clientbound packet into the legacy packets it
     * produces — for every generation but one, exactly the result
     * of {@link #toLegacy}. The 1.2-and-older generations (29, 28,
     * 23, 22) are the exception: they have no chunk bulk and no
     * counted destroy packet, so a modern chunk becomes a
     * pre_chunk + chunk_data pair (a modern unload — ground-up
     * with an empty bitmap — becomes a lone pre_chunk with mode
     * 0), a modern bulk becomes one such pair per chunk, and a
     * multi-entity destroy becomes one packet per entity id. For
     * protocols 23 (1.1) and 22 (1.0.0) the chunk_data of each pair
     * is a cuboid converted by {@link LegacyChunkRemap}; a chunk
     * with no section below y 128 produces only its pre_chunk.
     */
    public List<LegacyPacket> toLegacyAll(MinecraftPacket modern, State state) {
        if (!LegacyProtocol.is1_2OrOlderGeneration(clientProtocol)) {
            return toLegacy(modern, state).map(List::of).orElseGet(List::of);
        }
        try {
            PacketIO.Reader r = new PacketIO.Reader(modern.payload());
            switch (modern.id()) {
                case M_CHUNK -> {
                    int x = r.readInt();
                    int z = r.readInt();
                    boolean groundUp = r.readBoolean();
                    int primary = r.readShort();
                    int add = r.readShort();
                    int len = r.readInt();
                    byte[] data = r.readBytes(len);
                    if (groundUp && primary == 0) {
                        // A modern chunk unload: these generations
                        // unload chunks with a pre_chunk of mode 0.
                        return List.of(new LegacyPacket(0x32, List.of(x, z, 0)));
                    }
                    if (LegacyProtocol.is1_1OrOlderGeneration(clientProtocol)) {
                        LegacyChunkRemap.CuboidData cuboid =
                                LegacyChunkRemap.remapChunkToCuboid(
                                        data, primary, add, groundUp, clientProtocol);
                        if (cuboid == null) {
                            return List.of(new LegacyPacket(0x32, List.of(x, z, 1)));
                        }
                        return List.of(
                                new LegacyPacket(0x32, List.of(x, z, 1)),
                                new LegacyPacket(0x33, List.of(x * 16, cuboid.yBase(),
                                        z * 16, 16, cuboid.sizeY(), 16,
                                        cuboid.compressedData())));
                    }
                    byte[] remapped = LegacyChunkRemap.remapSingleChunkData(
                            data, primary, add, groundUp, clientProtocol);
                    return List.of(
                            new LegacyPacket(0x32, List.of(x, z, 1)),
                            new LegacyPacket(0x33, List.of(x, z, groundUp,
                                    primary, add, remapped)));
                }
                case M_BULK -> {
                    int count = r.readShort();
                    int len = r.readInt();
                    boolean sky = r.readBoolean();
                    byte[] data = r.readBytes(len);
                    List<int[]> meta = new ArrayList<>(count);
                    for (int i = 0; i < count; i++) {
                        meta.add(new int[]{r.readInt(), r.readInt(),
                                r.readShort(), r.readShort()});
                    }
                    // The bulk payload is one zlib stream of
                    // concatenated per-chunk data. Slice it with
                    // the same length formula the bulk remapper
                    // uses, remap each chunk on its own, and emit
                    // a pre_chunk + chunk_data pair per chunk.
                    byte[] raw = LegacyChunkRemap.inflate(data);
                    List<LegacyPacket> out = new ArrayList<>(count * 2);
                    int offset = 0;
                    for (int[] m : meta) {
                        int primary = m[2] & 0xFFFF;
                        int add = m[3] & 0xFFFF;
                        int sections = Integer.bitCount(primary);
                        int adds = Integer.bitCount(add);
                        int chunkLength = sections * (4096 + 2048 + 2048)
                                + (sky ? sections * 2048 : 0) + adds * 2048 + 256;
                        if (offset + chunkLength > raw.length) {
                            LOG.fine("clientbound bulk packet dropped "
                                    + "(malformed bulk payload)");
                            return List.of();
                        }
                        byte[] chunkRaw = java.util.Arrays.copyOfRange(
                                raw, offset, offset + chunkLength);
                        offset += chunkLength;
                        if (LegacyProtocol.is1_1OrOlderGeneration(clientProtocol)) {
                            LegacyChunkRemap.CuboidData cuboid =
                                    LegacyChunkRemap.remapChunkToCuboid(
                                            LegacyChunkRemap.deflate(chunkRaw),
                                            primary, add, true, clientProtocol);
                            out.add(new LegacyPacket(0x32, List.of(m[0], m[1], 1)));
                            if (cuboid != null) {
                                out.add(new LegacyPacket(0x33, List.of(
                                        m[0] * 16, cuboid.yBase(), m[1] * 16, 16,
                                        cuboid.sizeY(), 16, cuboid.compressedData())));
                            }
                            continue;
                        }
                        byte[] remapped = LegacyChunkRemap.remapSingleChunkData(
                                LegacyChunkRemap.deflate(chunkRaw),
                                primary, add, true, clientProtocol);
                        out.add(new LegacyPacket(0x32, List.of(m[0], m[1], 1)));
                        out.add(new LegacyPacket(0x33, List.of(m[0], m[1], true,
                                m[2], m[3], remapped)));
                    }
                    return out;
                }
                case M_DESTROY -> {
                    int count = r.readUnsignedByte();
                    List<LegacyPacket> out = new ArrayList<>(count);
                    for (int i = 0; i < count; i++) {
                        out.add(new LegacyPacket(0x1D,
                                List.of(List.of(r.readInt()))));
                    }
                    return out;
                }
                default -> {
                    return toLegacy(modern, state).map(List::of).orElseGet(List::of);
                }
            }
        } catch (IllegalArgumentException e) {
            LOG.fine("clientbound packet 0x" + Integer.toHexString(modern.id())
                    + " dropped (conversion failed): " + e.getMessage());
            return List.of();
        }
    }

    /** Protocol 61 ability speed: float -> wire byte (speed * 255, clamped). */
    private static int speedToByte61(float speed) {
        return Math.max(0, Math.min(127, Math.round(speed * 255f)));
    }

    /**
     * An ability speed field coming from a legacy client: a Float on
     * the 1.6 wire, a byte-valued Integer (value / 255) on protocol 61.
     */
    private static float abilitySpeed(Object field) {
        return field instanceof Float f ? f : ((Number) field).intValue() / 255f;
    }

    /** Modern block-entity NBT tail: i16 length + gzip bytes (null when absent). */
    private static byte[] readModernNbt(PacketIO.Reader r) {
        int len = r.readShort();
        if (len < 0) {
            return null;
        }
        return r.readBytes(len);
    }

    // ------------------------------------------------------------------
    // Serverbound: legacy 78 -> modern (protocol 5)
    // ------------------------------------------------------------------

    /** Translates one serverbound packet. Empty means: drop. */
    public Optional<MinecraftPacket> toModern(LegacyPacket legacy, State state) {
        try {
            PacketIO.Writer w = new PacketIO.Writer();
            int modernId = switch (legacy.id()) {
                case 0x00 -> {
                    w.writeInt(legacy.intField(0));
                    yield S_KEEP_ALIVE;
                }
                case 0x03 -> {
                    w.writeString((String) legacy.field(0));
                    yield S_CHAT;
                }
                case 0x09 -> {
                    // Protocols 29 (1.2.5) and older only: their
                    // clients send the respawn packet itself to
                    // request a respawn (the client-status packet
                    // arrived in 1.3). The modern form is a client
                    // command with action 0 (respawn). Other
                    // generations' serverbound 0x09 packets keep
                    // the historical drop. (The packet's fields
                    // differ per generation but are all ignored
                    // here.)
                    if (!LegacyProtocol.is1_2OrOlderGeneration(clientProtocol)) {
                        yield -1;
                    }
                    w.writeByte(0);
                    yield S_CLIENT_COMMAND;
                }
                case 0x07 -> {
                    w.writeInt(legacy.intField(1)); // target
                    w.writeByte(legacy.intField(2)); // mouse
                    yield S_USE_ENTITY;
                }
                case 0x0A -> {
                    w.writeBoolean((Boolean) legacy.field(0));
                    yield S_FLYING;
                }
                case 0x0B -> {
                    double x = (Double) legacy.field(0);
                    double stance = (Double) legacy.field(1);
                    double y = (Double) legacy.field(2);
                    double z = (Double) legacy.field(3);
                    w.writeDouble(x);
                    w.writeDouble(stance);
                    w.writeDouble(y);
                    w.writeDouble(z);
                    w.writeBoolean((Boolean) legacy.field(4));
                    state.lastX = x;
                    state.lastY = y;
                    state.lastZ = z;
                    state.positionKnown = true;
                    yield S_POSITION;
                }
                case 0x0C -> {
                    w.writeFloat((Float) legacy.field(0));
                    w.writeFloat((Float) legacy.field(1));
                    w.writeBoolean((Boolean) legacy.field(2));
                    state.lastYaw = (Float) legacy.field(0);
                    state.lastPitch = (Float) legacy.field(1);
                    yield S_LOOK;
                }
                case 0x0D -> {
                    double x = (Double) legacy.field(0);
                    double stance = (Double) legacy.field(1);
                    double y = (Double) legacy.field(2);
                    double z = (Double) legacy.field(3);
                    float yaw = (Float) legacy.field(4);
                    float pitch = (Float) legacy.field(5);
                    w.writeDouble(x);
                    w.writeDouble(stance);
                    w.writeDouble(y);
                    w.writeDouble(z);
                    w.writeFloat(yaw);
                    w.writeFloat(pitch);
                    w.writeBoolean((Boolean) legacy.field(6));
                    state.lastX = x;
                    state.lastY = y;
                    state.lastZ = z;
                    state.lastYaw = yaw;
                    state.lastPitch = pitch;
                    state.positionKnown = true;
                    yield S_POS_LOOK;
                }
                case 0x0E -> {
                    w.writeByte(legacy.intField(0));
                    w.writeInt(legacy.intField(1));
                    w.writeByte(legacy.intField(2));
                    w.writeInt(legacy.intField(3));
                    w.writeByte(legacy.intField(4));
                    yield S_DIG;
                }
                case 0x0F -> {
                    w.writeInt(legacy.intField(0));
                    w.writeByte(legacy.intField(1));
                    w.writeInt(legacy.intField(2));
                    w.writeByte(legacy.intField(3));
                    writeModernSlot(w, (Slot) legacy.field(4));
                    // Protocol 29 (1.2.5): no cursor bytes on the
                    // wire (they arrived in 1.3) — the modern side
                    // gets the centred defaults.
                    if (legacy.fields().size() > 5) {
                        w.writeByte(legacy.intField(5));
                        w.writeByte(legacy.intField(6));
                        w.writeByte(legacy.intField(7));
                    } else {
                        w.writeByte(0);
                        w.writeByte(0);
                        w.writeByte(0);
                    }
                    yield S_PLACE;
                }
                case 0x10 -> {
                    w.writeShort(legacy.intField(0));
                    yield S_HELD_ITEM;
                }
                case 0x12 -> S_ARM_ANIMATION; // empty payload by design
                case 0x13 -> {
                    w.writeInt(legacy.intField(0));
                    w.writeByte(legacy.intField(1));
                    // Protocol 61 has no aux field: synthesize 0.
                    w.writeInt(legacy.fields().size() > 2 ? legacy.intField(2) : 0);
                    yield S_ENTITY_ACTION;
                }
                case 0x1B -> {
                    // Protocol 22 (1.0.0): 0x1B is player_input —
                    // the client's raw movement input (strafe /
                    // forward / pitch / yaw floats, jumping and
                    // sneaking bools) — not a vehicle steer. It
                    // has no modern counterpart (the position
                    // packets carry the movement itself, and
                    // sneaking also arrives via entity_action):
                    // read and drop.
                    if (clientProtocol == LegacyProtocol.V1_0) {
                        yield -1;
                    }
                    w.writeFloat((Float) legacy.field(0));
                    w.writeFloat((Float) legacy.field(1));
                    w.writeBoolean((Boolean) legacy.field(2));
                    w.writeBoolean((Boolean) legacy.field(3));
                    yield S_STEER;
                }
                case 0x65 -> {
                    w.writeByte(legacy.intField(0));
                    yield S_CLOSE_WINDOW;
                }
                case 0x66 -> {
                    w.writeByte(legacy.intField(0));
                    w.writeShort(legacy.intField(1));
                    w.writeByte(legacy.intField(2));
                    w.writeShort(legacy.intField(3));
                    w.writeByte(legacy.intField(4));
                    writeModernSlot(w, (Slot) legacy.field(5));
                    yield S_WINDOW_CLICK;
                }
                case 0x6A -> {
                    w.writeByte(legacy.intField(0));
                    w.writeShort(legacy.intField(1));
                    w.writeBoolean((Boolean) legacy.field(2));
                    yield S_CONFIRM;
                }
                case 0x6B -> {
                    w.writeShort(legacy.intField(0));
                    writeModernSlot(w, (Slot) legacy.field(1));
                    yield S_CREATIVE;
                }
                case 0x6C -> {
                    w.writeByte(legacy.intField(0));
                    w.writeByte(legacy.intField(1));
                    yield S_ENCHANT;
                }
                case 0x82 -> {
                    w.writeInt(legacy.intField(0));
                    w.writeShort(legacy.intField(1));
                    w.writeInt(legacy.intField(2));
                    for (int i = 3; i < 7; i++) {
                        w.writeString((String) legacy.field(i));
                    }
                    yield S_UPDATE_SIGN;
                }
                case 0xCA -> {
                    // Protocol 29 (1.2.5): four bools
                    // (invulnerable, flying, canFly, creative) and
                    // no speeds — the modern side gets the vanilla
                    // default speeds.
                    if (LegacyProtocol.is1_2Generation(clientProtocol)) {
                        int flags = 0;
                        for (int i = 0; i < 4; i++) {
                            if ((Boolean) legacy.field(i)) {
                                flags |= 1 << i;
                            }
                        }
                        w.writeByte(flags);
                        w.writeFloat(0.05f);
                        w.writeFloat(0.1f);
                        yield S_ABILITIES;
                    }
                    w.writeByte(legacy.intField(0));
                    w.writeFloat(abilitySpeed(legacy.field(1)));
                    w.writeFloat(abilitySpeed(legacy.field(2)));
                    yield S_ABILITIES;
                }
                case 0xCB -> {
                    w.writeString((String) legacy.field(0));
                    yield S_TAB_COMPLETE;
                }
                case 0xCC -> {
                    w.writeString((String) legacy.field(0)); // locale
                    w.writeByte(legacy.intField(1)); // view distance
                    w.writeByte(legacy.intField(2)); // chat flags
                    w.writeBoolean(true); // chat colors
                    w.writeByte(legacy.intField(3)); // difficulty
                    // Protocol 39 (1.3.2) has no showCape field
                    // (it arrived in 1.4): synthesize true, which
                    // is what a 1.3.2 client always renders.
                    w.writeBoolean(legacy.fields().size() > 4
                            ? (Boolean) legacy.field(4) : true); // show cape
                    yield S_CLIENT_INFO;
                }
                case 0xCD -> {
                    w.writeByte(legacy.intField(0));
                    yield S_CLIENT_COMMAND;
                }
                case 0xFA -> {
                    String channel = (String) legacy.field(0);
                    byte[] data = (byte[]) legacy.field(1);
                    w.writeString(channel);
                    w.writeShort(data.length);
                    w.writeBytes(data);
                    yield S_PLUGIN;
                }
                default -> -1;
            };
            if (modernId < 0) {
                return Optional.empty();
            }
            return Optional.of(new MinecraftPacket(modernId, w.bytes()));
        } catch (IllegalArgumentException | ClassCastException e) {
            LOG.fine("serverbound legacy packet 0x" + Integer.toHexString(legacy.id())
                    + " dropped (conversion failed): " + e.getMessage());
            return Optional.empty();
        }
    }
}
