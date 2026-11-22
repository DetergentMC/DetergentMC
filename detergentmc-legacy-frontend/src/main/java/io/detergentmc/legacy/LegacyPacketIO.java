package io.detergentmc.legacy;

import io.detergentmc.legacy.LegacyPacketTable.Field;
import io.detergentmc.legacy.LegacyPacketTable.PacketDef;
import io.detergentmc.legacy.LegacyWire.MetaEntry;
import io.detergentmc.legacy.LegacyWire.Reader;
import io.detergentmc.legacy.LegacyWire.Slot;
import io.detergentmc.legacy.LegacyWire.Writer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Whole-packet codec for the legacy protocols, driven by
 * {@link LegacyPacketTable}. A parsed packet is an ID plus a list of
 * field values in table order (see {@link LegacyWire} for the Java
 * type of each field kind).
 *
 * <p>Custom-layout packets use these field shapes:
 * <ul>
 *   <li>spawn_object: [entityId, type, x, y, z, yaw, pitch, data,
 *       velX, velY, velZ] — velocity values are 0 when data == 0
 *       (the wire omits them then).</li>
 *   <li>spawn_object_norot (protocol 49 only): same field shape,
 *       but the wire carries no yaw/pitch bytes (they arrived in
 *       1.4.6); parsing supplies 0 for both and writing drops them.</li>
 *   <li>pickup_spawn (protocol 49 only): [entityId, Slot, x, y, z,
 *       yaw, pitch, roll] — the dedicated dropped-item spawn that
 *       51 merged into spawn_object type 2.</li>
 *   <li>pickup_spawn_expanded (protocol 39 only): the same field
 *       shape as pickup_spawn, but the wire inlines the stack as
 *       (itemId i16, count u8, damage i16) with no NBT — 1.3.2
 *       pickup spawns carry no NBT, so a written slot's NBT is
 *       dropped and a parsed slot always has null NBT.</li>
 *   <li>equipment_inline (protocols 29 and 28): the same field shape
 *       as the flat entity_equipment ([entityId, slotId, Slot]),
 *       but the wire inlines the stack as (itemId i16, damage
 *       i16) — 1.2-generation equipment carries no count and no
 *       NBT, so a parsed slot has count 1 and null NBT, and a
 *       written slot's count and NBT are dropped.</li>
 *   <li>destroy_entities_single (protocols 29 and 28): the same
 *       field shape as destroy_entities ([List&lt;Integer&gt;]),
 *       but the wire carries exactly ONE i32 entity id per
 *       packet; writing a list of any other size is an error
 *       (the translator emits one packet per id).</li>
 *   <li>chunk_data_125 (protocols 29 and 28): the same field shape
 *       as chunk_data, but the wire carries TWO length ints
 *       before the data — the compressed length and an unused
 *       int (the 1.2.5 constructor never sets it; it is 0 on
 *       the wire and ignored when parsing).</li>
 *   <li>explosion_novel (protocols 29 and 28): the same field
 *       shape as explosion, but the wire carries no trailing
 *       player-motion floats; parsing supplies 0 velocities
 *       and writing drops them.</li>
 *   <li>chunk_data_cuboid (protocols 23 and 22): [x, y, z, sizeX,
 *       sizeY, sizeZ, byte[] compressedData] — a cuboid region
 *       rather than chunk sections; the wire sizes are u8
 *       counts-minus-one while the field list keeps counts. The
 *       inflated payload (block ids, then metadata / block-light
 *       / sky-light nibbles, y-fastest) is interpreted by
 *       {@link LegacyChunkRemap}.</li>
 *   <li>multi_block_change_early (protocols 23 and 22): the same
 *       field shape as multi_block_change ([chunkX, chunkZ,
 *       List&lt;int[2]&gt; records] with (packedPosition,
 *       blockData)), but the wire splits the records into three
 *       parallel arrays: packed i16 coordinates, u8 types, u8
 *       metas.</li>
 *   <li>chunk_bulk_nosky (protocol 49 only): same field shape as
 *       chunk_bulk, but the wire carries no skyLight flag byte (it
 *       arrived in 1.4.6); the parsed skyLight field is always
 *       false and carries no meaning — whether sky arrays are in
 *       the payload follows the dimension, as in vanilla 1.4.5.</li>
 *   <li>destroy_entities: [List&lt;Integer&gt; entityIds] (the wire
 *       count is a u8; longer lists are split by the writer).</li>
 *   <li>entity_properties: [entityId, List&lt;Property&gt;]</li>
 *   <li>entity_properties_nomod (protocol 73 only): same field
 *       shape, but the wire carries no modifiers — parsed properties
 *       have empty modifier lists and written modifiers are dropped.</li>
 *   <li>chunk_data: [x, z, groundUp, primaryBitmap, addBitmap,
 *       byte[] data]</li>
 *   <li>multi_block_change: [chunkX, chunkZ, List&lt;int[2]&gt;
 *       records] — each record is (packedPosition, blockData).</li>
 *   <li>chunk_bulk: [skyLight, byte[] data, List&lt;int[4]&gt; meta]
 *       — each meta entry is (x, z, primaryBitmap, addBitmap).</li>
 *   <li>explosion: [x, y, z, radius, List&lt;int[3]&gt; offsets,
 *       velX, velY, velZ]</li>
 *   <li>window_items: [windowId, List&lt;Slot&gt;]</li>
 *   <li>update_score: [name, mode, objective, value] — objective and
 *       value are null when mode == 1 (remove).</li>
 *   <li>teams: [name, mode, display, prefix, suffix, friendlyFire,
 *       List&lt;String&gt; players] — the middle group is null unless
 *       mode is 0 or 2; players is null unless mode is 0, 3 or 4.</li>
 *   <li>item_data: [itemType, itemDamage, byte[] data]</li>
 *   <li>open_window: [windowId, typeId, title, slots, useTitle,
 *       entityId] — entityId is present on the wire only when
 *       typeId == 11 (horse); the field value is null otherwise.</li>
 *   <li>open_window_nohorse: same field list, but the entityId is
 *       never on the wire (protocol 61 has no horse window).</li>
 *   <li>open_window_notitle: same field list, but neither useTitle
 *       nor entityId is on the wire (protocol 51 predates the
 *       useTitle flag); reading supplies useTitle = true, because
 *       1.4.7 window titles are always literal.</li>
 * </ul>
 */
public final class LegacyPacketIO {

    /** One attribute property of entity_properties. */
    public record Property(String key, double base, List<Modifier> modifiers) {
    }

    /** One attribute modifier (UUID as two big-endian longs). */
    public record Modifier(long uuidMsb, long uuidLsb, double amount, int operation) {
    }

    /** A parsed legacy packet: ID + field values in table order. */
    public record LegacyPacket(int id, List<Object> fields) {
        public Object field(int i) {
            return fields.get(i);
        }

        public int intField(int i) {
            return (Integer) fields.get(i);
        }

        public String name() {
            return name(LegacyProtocol.V1_6_4);
        }

        /** The packet's name in the given legacy protocol's table. */
        public String name(int legacyProtocol) {
            PacketDef def = LegacyPacketTable.packet(legacyProtocol, id);
            return def != null ? def.name() : "unknown_0x" + Integer.toHexString(id);
        }
    }

    private LegacyPacketIO() {
    }

    // ------------------------------------------------------------------
    // Parsing
    // ------------------------------------------------------------------

    /**
     * Parses exactly one packet from the start of {@code buf}.
     *
     * @return the packet and the number of bytes it consumed
     * @throws LegacyWire.Underflow when the buffer holds only part of
     *         the packet (streaming callers wait for more bytes)
     * @throws IllegalArgumentException on malformed input or an
     *         unregistered packet ID
     */
    public static ParseResult parseOne(byte[] buf, int offset, int length) {
        return parseOne(LegacyProtocol.V1_6_4, buf, offset, length);
    }

    /** As {@link #parseOne(byte[], int, int)} for a specific legacy protocol. */
    public static ParseResult parseOne(int legacyProtocol, byte[] buf, int offset, int length) {
        if (length < 1) {
            throw new LegacyWire.Underflow();
        }
        int id = buf[offset] & 0xFF;
        PacketDef def = LegacyPacketTable.packet(legacyProtocol, id);
        if (def == null) {
            throw new IllegalArgumentException(
                    "unregistered legacy packet id 0x" + Integer.toHexString(id));
        }
        Reader reader = new Reader(Arrays.copyOfRange(buf, offset, offset + length))
                .plainWatcherSlot(LegacyProtocol.watcherSlotHasNoNbt(legacyProtocol))
                .conditionalSlotNbt(LegacyProtocol.slotNbtIsConditional(legacyProtocol));
        reader.u8(); // id
        List<Object> fields = def.isCustom()
                ? parseCustom(def.custom(), reader)
                : parseFlat(def.fields(), reader);
        return new ParseResult(new LegacyPacket(id, fields), length - reader.remaining());
    }

    public record ParseResult(LegacyPacket packet, int consumed) {
    }

    private static List<Object> parseFlat(List<Field> fields, Reader r) {
        List<Object> out = new ArrayList<>(fields.size());
        for (Field f : fields) {
            out.add(readField(f, r));
        }
        return out;
    }

    private static Object readField(Field f, Reader r) {
        return switch (f) {
            case U8 -> r.u8();
            case I8 -> r.i8();
            case BOOL -> r.bool();
            case I16 -> r.i16();
            case U16 -> r.u16();
            case I32 -> r.i32();
            case I64 -> r.i64();
            case F32 -> r.f32();
            case F64 -> r.f64();
            case STRING -> r.string();
            case BYTES16 -> r.bytes16();
            case SLOT -> r.slot();
            case METADATA -> r.metadata();
            case NBTBYTES -> r.nbtBytes();
        };
    }

    private static List<Object> parseCustom(String kind, Reader r) {
        List<Object> out = new ArrayList<>();
        switch (kind) {
            case "spawn_object" -> {
                out.add(r.i32());
                out.add(r.i8());
                out.add(r.i32());
                out.add(r.i32());
                out.add(r.i32());
                out.add(r.i8());
                out.add(r.i8());
                int data = r.i32();
                out.add(data);
                if (data != 0) {
                    out.add((int) (short) r.i16());
                    out.add((int) (short) r.i16());
                    out.add((int) (short) r.i16());
                } else {
                    out.add(0);
                    out.add(0);
                    out.add(0);
                }
            }
            case "spawn_object_norot" -> {
                // Protocol 49 (1.4.5): no pitch/yaw bytes on the
                // wire. The field list keeps the spawn_object shape
                // with both rotations 0.
                out.add(r.i32());
                out.add(r.i8());
                out.add(r.i32());
                out.add(r.i32());
                out.add(r.i32());
                out.add(0);
                out.add(0);
                int data = r.i32();
                out.add(data);
                if (data != 0) {
                    out.add((int) (short) r.i16());
                    out.add((int) (short) r.i16());
                    out.add((int) (short) r.i16());
                } else {
                    out.add(0);
                    out.add(0);
                    out.add(0);
                }
            }
            case "pickup_spawn" -> {
                // Protocol 49 (1.4.5) only: the dedicated spawn for
                // dropped item entities.
                out.add(r.i32());
                out.add(r.slot());
                out.add(r.i32());
                out.add(r.i32());
                out.add(r.i32());
                out.add(r.i8());
                out.add(r.i8());
                out.add(r.i8());
            }
            case "pickup_spawn_expanded" -> {
                // Protocol 39 (1.3.2) only: the stack fields are
                // inlined (itemId, count, damage) with no NBT. The
                // field list keeps the pickup_spawn shape. (The
                // 1.3.2 reader reads all three fields
                // unconditionally; vanilla always sends a real
                // stack here.)
                out.add(r.i32());
                int itemId = r.i16();
                int count = r.u8();
                int damage = r.i16();
                out.add(itemId < 0 ? Slot.EMPTY
                        : new Slot(itemId, count, damage, null));
                out.add(r.i32());
                out.add(r.i32());
                out.add(r.i32());
                out.add(r.i8());
                out.add(r.i8());
                out.add(r.i8());
            }
            case "destroy_entities" -> {
                int count = r.u8();
                List<Integer> ids = new ArrayList<>(count);
                for (int i = 0; i < count; i++) {
                    ids.add(r.i32());
                }
                out.add(ids);
            }
            case "equipment_inline" -> {
                // Protocol 29 (1.2.5) only: the stack is inlined
                // as (itemId, damage); there is no count and no
                // NBT on the wire. The field list keeps the flat
                // entity_equipment shape with a Slot whose count
                // is 1.
                out.add(r.i32());
                out.add(r.i16());
                int itemId = r.i16();
                int damage = r.i16();
                out.add(itemId < 0 ? Slot.EMPTY : new Slot(itemId, 1, damage, null));
            }
            case "destroy_entities_single" -> {
                // Protocol 29 (1.2.5) only: one entity id per
                // packet. The field list keeps the
                // destroy_entities shape (a list), with one entry.
                out.add(List.of(r.i32()));
            }
            case "chunk_data_125" -> {
                // Protocol 29 (1.2.5) only: two length ints
                // precede the data; the second is unused (never
                // set by the vanilla constructor, ignored here).
                out.add(r.i32());
                out.add(r.i32());
                out.add(r.bool());
                out.add(r.i16());
                out.add(r.i16());
                int len = r.i32();
                r.i32(); // unused second length
                out.add(r.bytes(len));
            }
            case "explosion_novel" -> {
                // Protocol 29 (1.2.5) only: no trailing
                // player-motion floats. The field list keeps the
                // explosion shape with zero velocities.
                out.add(r.f64());
                out.add(r.f64());
                out.add(r.f64());
                out.add(r.f32());
                int count = r.i32();
                List<int[]> offsets = new ArrayList<>(count);
                for (int i = 0; i < count; i++) {
                    offsets.add(new int[]{r.i8(), r.i8(), r.i8()});
                }
                out.add(offsets);
                out.add(0f);
                out.add(0f);
                out.add(0f);
            }
            case "chunk_data_cuboid" -> {
                // Protocols 23 (1.1) and 22 (1.0.0): a cuboid
                // region, not chunk sections. The wire carries
                // (x i32, y i16, z i32), then the three sizes as
                // u8 COUNTS-MINUS-ONE, then the compressed length
                // and the data. The field list keeps the sizes as
                // counts. The inflated data layout (block ids,
                // then metadata / block-light / sky-light nibble
                // arrays, y-fastest index) is interpreted by
                // LegacyChunkRemap, not here.
                out.add(r.i32());
                out.add(r.i16());
                out.add(r.i32());
                out.add(r.u8() + 1);
                out.add(r.u8() + 1);
                out.add(r.u8() + 1);
                int len = r.i32();
                out.add(r.bytes(len));
            }
            case "multi_block_change_early" -> {
                // Protocols 23 (1.1) and 22 (1.0.0): three
                // parallel arrays instead of one counted byte
                // array — packed i16 coordinates ((lx << 12) |
                // (lz << 8) | y), u8 block types, u8 metas. The
                // field list keeps the multi_block_change shape:
                // each record is (packedPosition, (type << 4) |
                // meta), the same blockData encoding the hybrid
                // side uses.
                out.add(r.i32());
                out.add(r.i32());
                int count = r.u16();
                int[] coords = new int[count];
                for (int i = 0; i < count; i++) {
                    coords[i] = r.u16();
                }
                int[] types = new int[count];
                for (int i = 0; i < count; i++) {
                    types[i] = r.u8();
                }
                List<int[]> records = new ArrayList<>(count);
                for (int i = 0; i < count; i++) {
                    records.add(new int[]{coords[i], (types[i] << 4) | r.u8()});
                }
                out.add(records);
            }
            case "entity_properties" -> {
                out.add(r.i32());
                int count = r.i32();
                List<Property> props = new ArrayList<>(count);
                for (int i = 0; i < count; i++) {
                    String key = r.string();
                    double base = r.f64();
                    int mods = r.i16();
                    List<Modifier> modifiers = new ArrayList<>(mods);
                    for (int j = 0; j < mods; j++) {
                        modifiers.add(new Modifier(r.i64(), r.i64(), r.f64(), r.u8()));
                    }
                    props.add(new Property(key, base, modifiers));
                }
                out.add(props);
            }
            case "entity_properties_nomod" -> {
                // Protocol 73 (1.6.1): properties are key + base value
                // only; attribute modifiers are not on the wire.
                out.add(r.i32());
                int count = r.i32();
                List<Property> props = new ArrayList<>(count);
                for (int i = 0; i < count; i++) {
                    props.add(new Property(r.string(), r.f64(), List.of()));
                }
                out.add(props);
            }
            case "chunk_data" -> {
                out.add(r.i32());
                out.add(r.i32());
                out.add(r.bool());
                out.add(r.i16());
                out.add(r.i16());
                int len = r.i32();
                out.add(r.bytes(len));
            }
            case "multi_block_change" -> {
                out.add(r.i32());
                out.add(r.i32());
                int count = r.i16();
                int len = r.i32();
                byte[] raw = r.bytes(len);
                if (raw.length != count * 4) {
                    throw new IllegalArgumentException(
                            "multi_block_change: data length " + raw.length
                                    + " != count*4 " + (count * 4));
                }
                Reader rr = new Reader(raw);
                List<int[]> records = new ArrayList<>(count);
                for (int i = 0; i < count; i++) {
                    records.add(new int[]{rr.u16(), rr.i16()});
                }
                out.add(records);
            }
            case "chunk_bulk" -> {
                int count = r.i16();
                int len = r.i32();
                boolean sky = r.bool();
                byte[] data = r.bytes(len);
                List<int[]> meta = new ArrayList<>(count);
                for (int i = 0; i < count; i++) {
                    meta.add(new int[]{r.i32(), r.i32(), r.i16(), r.i16()});
                }
                out.add(sky);
                out.add(data);
                out.add(meta);
            }
            case "chunk_bulk_nosky" -> {
                // Protocol 49 (1.4.5): no skyLight flag byte on the
                // wire. The field list keeps the chunk_bulk shape;
                // the skyLight field is a placeholder (false) — the
                // payload layout follows the dimension.
                int count = r.i16();
                int len = r.i32();
                byte[] data = r.bytes(len);
                List<int[]> meta = new ArrayList<>(count);
                for (int i = 0; i < count; i++) {
                    meta.add(new int[]{r.i32(), r.i32(), r.i16(), r.i16()});
                }
                out.add(Boolean.FALSE);
                out.add(data);
                out.add(meta);
            }
            case "explosion" -> {
                out.add(r.f64());
                out.add(r.f64());
                out.add(r.f64());
                out.add(r.f32());
                int count = r.i32();
                List<int[]> offsets = new ArrayList<>(count);
                for (int i = 0; i < count; i++) {
                    offsets.add(new int[]{r.i8(), r.i8(), r.i8()});
                }
                out.add(offsets);
                out.add(r.f32());
                out.add(r.f32());
                out.add(r.f32());
            }
            case "window_items" -> {
                out.add(r.i8());
                int count = r.i16();
                List<Slot> slots = new ArrayList<>(count);
                for (int i = 0; i < count; i++) {
                    slots.add(r.slot());
                }
                out.add(slots);
            }
            case "update_score" -> {
                out.add(r.string());
                int mode = r.i8();
                out.add(mode);
                if (mode != 1) {
                    out.add(r.string());
                    out.add(r.i32());
                } else {
                    out.add(null);
                    out.add(null);
                }
            }
            case "teams" -> {
                out.add(r.string());
                int mode = r.i8();
                out.add(mode);
                if (mode == 0 || mode == 2) {
                    out.add(r.string());
                    out.add(r.string());
                    out.add(r.string());
                    out.add(r.i8());
                } else {
                    out.add(null);
                    out.add(null);
                    out.add(null);
                    out.add(null);
                }
                if (mode == 0 || mode == 3 || mode == 4) {
                    int count = r.i16();
                    List<String> players = new ArrayList<>(count);
                    for (int i = 0; i < count; i++) {
                        players.add(r.string());
                    }
                    out.add(players);
                } else {
                    out.add(null);
                }
            }
            case "item_data" -> {
                out.add(r.i16());
                out.add(r.i16());
                int len = r.u16();
                out.add(r.bytes(len));
            }
            case "open_window" -> {
                out.add(r.i8());
                int typeId = r.i8();
                out.add(typeId);
                out.add(r.string());
                out.add(r.i8());
                out.add(r.bool());
                out.add(typeId == 11 ? r.i32() : null);
            }
            case "open_window_nohorse" -> {
                // Protocol 61 (1.5.2): no horse window exists, so the
                // wire has no trailing entity id. The field list keeps
                // the open_window shape with a null entity id.
                out.add(r.i8());
                out.add(r.i8());
                out.add(r.string());
                out.add(r.i8());
                out.add(r.bool());
                out.add(null);
            }
            case "open_window_notitle" -> {
                // Protocol 51 (1.4.7): the wire is [windowId, typeId,
                // title, slots] only — no useTitle flag (titles are
                // always literal in 1.4.7) and no entity id. The
                // field list keeps the open_window shape.
                out.add(r.i8());
                out.add(r.i8());
                out.add(r.string());
                out.add(r.i8());
                out.add(Boolean.TRUE);
                out.add(null);
            }
            default -> throw new IllegalArgumentException("unknown custom packet kind: " + kind);
        }
        return out;
    }

    // ------------------------------------------------------------------
    // Writing
    // ------------------------------------------------------------------

    /** Serializes one packet (ID byte + fields). */
    public static byte[] write(LegacyPacket packet) {
        return write(LegacyProtocol.V1_6_4, packet);
    }

    /** As {@link #write(LegacyPacket)} for a specific legacy protocol. */
    public static byte[] write(int legacyProtocol, LegacyPacket packet) {
        PacketDef def = LegacyPacketTable.packet(legacyProtocol, packet.id());
        if (def == null) {
            throw new IllegalArgumentException(
                    "unregistered legacy packet id 0x" + Integer.toHexString(packet.id()));
        }
        Writer w = new Writer(packet.id())
                .plainWatcherSlot(LegacyProtocol.watcherSlotHasNoNbt(legacyProtocol))
                .conditionalSlotNbt(LegacyProtocol.slotNbtIsConditional(legacyProtocol));
        if (def.isCustom()) {
            writeCustom(def.custom(), packet.fields(), w);
        } else {
            List<Field> fields = def.fields();
            if (packet.fields().size() != fields.size()) {
                throw new IllegalArgumentException(def.name() + ": expected "
                        + fields.size() + " fields, got " + packet.fields().size());
            }
            for (int i = 0; i < fields.size(); i++) {
                writeField(fields.get(i), packet.fields().get(i), w);
            }
        }
        return w.bytes();
    }

    private static void writeField(Field f, Object v, Writer w) {
        switch (f) {
            case U8 -> w.u8((Integer) v);
            case I8 -> w.i8((Integer) v);
            case BOOL -> w.bool((Boolean) v);
            case I16 -> w.i16((Integer) v);
            case U16 -> w.u16((Integer) v);
            case I32 -> w.i32((Integer) v);
            case I64 -> w.i64((Long) v);
            case F32 -> w.f32((Float) v);
            case F64 -> w.f64((Double) v);
            case STRING -> w.string((String) v);
            case BYTES16 -> w.bytes16((byte[]) v);
            case SLOT -> w.slot((Slot) v);
            case METADATA -> {
                @SuppressWarnings("unchecked")
                List<MetaEntry> entries = (List<MetaEntry>) v;
                w.metadata(entries);
            }
            case NBTBYTES -> w.nbtBytes((byte[]) v);
        }
    }

    @SuppressWarnings("unchecked")
    private static void writeCustom(String kind, List<Object> f, Writer w) {
        switch (kind) {
            case "spawn_object" -> {
                for (int i = 0; i < 8; i++) {
                    writeIntField(f, i, w, i == 1 || i == 5 || i == 6);
                }
                int data = (Integer) f.get(7);
                if (data != 0) {
                    w.i16((Integer) f.get(8));
                    w.i16((Integer) f.get(9));
                    w.i16((Integer) f.get(10));
                }
            }
            case "spawn_object_norot" -> {
                // Protocol 49: the pitch/yaw fields (5 and 6) are
                // not on the wire; data is field 7 as usual.
                writeIntField(f, 0, w, false);
                writeIntField(f, 1, w, true);
                writeIntField(f, 2, w, false);
                writeIntField(f, 3, w, false);
                writeIntField(f, 4, w, false);
                int data = (Integer) f.get(7);
                w.i32(data);
                if (data != 0) {
                    w.i16((Integer) f.get(8));
                    w.i16((Integer) f.get(9));
                    w.i16((Integer) f.get(10));
                }
            }
            case "pickup_spawn" -> {
                w.i32((Integer) f.get(0));
                w.slot((LegacyWire.Slot) f.get(1));
                w.i32((Integer) f.get(2));
                w.i32((Integer) f.get(3));
                w.i32((Integer) f.get(4));
                w.i8((Integer) f.get(5));
                w.i8((Integer) f.get(6));
                w.i8((Integer) f.get(7));
            }
            case "pickup_spawn_expanded" -> {
                // Protocol 39: the stack is inlined; its NBT is
                // dropped, exactly as a real 1.3.2 server never
                // sends NBT in a pickup spawn.
                w.i32((Integer) f.get(0));
                Slot stack = (Slot) f.get(1);
                if (stack == null || stack.isEmpty()) {
                    w.i16(-1);
                    w.u8(0);
                    w.i16(0);
                } else {
                    w.i16(stack.itemId());
                    w.u8(stack.count());
                    w.i16(stack.damage());
                }
                w.i32((Integer) f.get(2));
                w.i32((Integer) f.get(3));
                w.i32((Integer) f.get(4));
                w.i8((Integer) f.get(5));
                w.i8((Integer) f.get(6));
                w.i8((Integer) f.get(7));
            }
            case "destroy_entities" -> {
                List<Integer> ids = (List<Integer>) f.get(0);
                if (ids.size() > 255) {
                    throw new IllegalArgumentException(
                            "destroy_entities holds at most 255 ids per packet");
                }
                w.u8(ids.size());
                for (int id : ids) {
                    w.i32(id);
                }
            }
            case "equipment_inline" -> {
                // Protocol 29: only the item id and damage are
                // written; the slot's count and NBT have no wire
                // home, exactly as a real 1.2.5 server never
                // sends them in an equipment packet.
                w.i32((Integer) f.get(0));
                w.i16((Integer) f.get(1));
                Slot stack = (Slot) f.get(2);
                if (stack == null || stack.isEmpty()) {
                    w.i16(-1);
                    w.i16(0);
                } else {
                    w.i16(stack.itemId());
                    w.i16(stack.damage());
                }
            }
            case "destroy_entities_single" -> {
                // Protocol 29: exactly one id per packet (the
                // translator splits multi-id destroys).
                List<Integer> ids = (List<Integer>) f.get(0);
                if (ids.size() != 1) {
                    throw new IllegalArgumentException(
                            "destroy_entities_single holds exactly one id, got "
                                    + ids.size());
                }
                w.i32(ids.get(0));
            }
            case "chunk_data_125" -> {
                // Protocol 29: the compressed length, then the
                // unused second length int (0, as vanilla writes).
                w.i32((Integer) f.get(0));
                w.i32((Integer) f.get(1));
                w.bool((Boolean) f.get(2));
                w.i16((Integer) f.get(3));
                w.i16((Integer) f.get(4));
                byte[] data = (byte[]) f.get(5);
                w.i32(data.length);
                w.i32(0);
                w.bytes(data);
            }
            case "explosion_novel" -> {
                // Protocol 29: the motion fields (5..7) are not
                // on the wire; a 1.2.5 client applies no knockback
                // from the explosion packet.
                w.f64((Double) f.get(0));
                w.f64((Double) f.get(1));
                w.f64((Double) f.get(2));
                w.f32((Float) f.get(3));
                List<int[]> offsets = (List<int[]>) f.get(4);
                w.i32(offsets.size());
                for (int[] o : offsets) {
                    w.i8(o[0]);
                    w.i8(o[1]);
                    w.i8(o[2]);
                }
            }
            case "chunk_data_cuboid" -> {
                // Protocols 23 / 22: sizes are counts in the
                // field list and counts-minus-one on the wire.
                w.i32((Integer) f.get(0));
                w.i16((Integer) f.get(1));
                w.i32((Integer) f.get(2));
                w.u8((Integer) f.get(3) - 1);
                w.u8((Integer) f.get(4) - 1);
                w.u8((Integer) f.get(5) - 1);
                byte[] data = (byte[]) f.get(6);
                w.i32(data.length);
                w.bytes(data);
            }
            case "multi_block_change_early" -> {
                // Protocols 23 / 22: split the records into the
                // three parallel arrays (coordinates, types,
                // metas). The record blockData is (type << 4) |
                // meta, as in the hybrid shape.
                w.i32((Integer) f.get(0));
                w.i32((Integer) f.get(1));
                List<int[]> records = (List<int[]>) f.get(2);
                w.u16(records.size());
                for (int[] rec : records) {
                    w.u16(rec[0]);
                }
                for (int[] rec : records) {
                    w.u8((rec[1] >> 4) & 0xFF);
                }
                for (int[] rec : records) {
                    w.u8(rec[1] & 0xF);
                }
            }
            case "entity_properties" -> {
                w.i32((Integer) f.get(0));
                List<Property> props = (List<Property>) f.get(1);
                w.i32(props.size());
                for (Property p : props) {
                    w.string(p.key());
                    w.f64(p.base());
                    w.i16(p.modifiers().size());
                    for (Modifier m : p.modifiers()) {
                        w.i64(m.uuidMsb());
                        w.i64(m.uuidLsb());
                        w.f64(m.amount());
                        w.u8(m.operation());
                    }
                }
            }
            case "entity_properties_nomod" -> {
                // Protocol 73 (1.6.1): modifiers are dropped, exactly
                // like a real 1.6.1 server, which never syncs them.
                w.i32((Integer) f.get(0));
                List<Property> props = (List<Property>) f.get(1);
                w.i32(props.size());
                for (Property p : props) {
                    w.string(p.key());
                    w.f64(p.base());
                }
            }
            case "chunk_data" -> {
                w.i32((Integer) f.get(0));
                w.i32((Integer) f.get(1));
                w.bool((Boolean) f.get(2));
                w.i16((Integer) f.get(3));
                w.i16((Integer) f.get(4));
                byte[] data = (byte[]) f.get(5);
                w.i32(data.length);
                w.bytes(data);
            }
            case "multi_block_change" -> {
                w.i32((Integer) f.get(0));
                w.i32((Integer) f.get(1));
                List<int[]> records = (List<int[]>) f.get(2);
                w.i16(records.size());
                w.i32(records.size() * 4);
                for (int[] rec : records) {
                    w.u16(rec[0]);
                    w.i16(rec[1]);
                }
            }
            case "chunk_bulk" -> {
                boolean sky = (Boolean) f.get(0);
                byte[] data = (byte[]) f.get(1);
                List<int[]> meta = (List<int[]>) f.get(2);
                w.i16(meta.size());
                w.i32(data.length);
                w.bool(sky);
                w.bytes(data);
                for (int[] m : meta) {
                    w.i32(m[0]);
                    w.i32(m[1]);
                    w.i16(m[2]);
                    w.i16(m[3]);
                }
            }
            case "chunk_bulk_nosky" -> {
                // Protocol 49: no skyLight flag byte; the field
                // list's first entry is ignored on write.
                byte[] data = (byte[]) f.get(1);
                List<int[]> meta = (List<int[]>) f.get(2);
                w.i16(meta.size());
                w.i32(data.length);
                w.bytes(data);
                for (int[] m : meta) {
                    w.i32(m[0]);
                    w.i32(m[1]);
                    w.i16(m[2]);
                    w.i16(m[3]);
                }
            }
            case "explosion" -> {
                w.f64((Double) f.get(0));
                w.f64((Double) f.get(1));
                w.f64((Double) f.get(2));
                w.f32((Float) f.get(3));
                List<int[]> offsets = (List<int[]>) f.get(4);
                w.i32(offsets.size());
                for (int[] o : offsets) {
                    w.i8(o[0]);
                    w.i8(o[1]);
                    w.i8(o[2]);
                }
                w.f32((Float) f.get(5));
                w.f32((Float) f.get(6));
                w.f32((Float) f.get(7));
            }
            case "window_items" -> {
                w.i8((Integer) f.get(0));
                List<Slot> slots = (List<Slot>) f.get(1);
                w.i16(slots.size());
                for (Slot s : slots) {
                    w.slot(s);
                }
            }
            case "update_score" -> {
                w.string((String) f.get(0));
                int mode = (Integer) f.get(1);
                w.i8(mode);
                if (mode != 1) {
                    w.string((String) f.get(2));
                    w.i32((Integer) f.get(3));
                }
            }
            case "teams" -> {
                w.string((String) f.get(0));
                int mode = (Integer) f.get(1);
                w.i8(mode);
                if (mode == 0 || mode == 2) {
                    w.string((String) f.get(2));
                    w.string((String) f.get(3));
                    w.string((String) f.get(4));
                    w.i8((Integer) f.get(5));
                }
                if (mode == 0 || mode == 3 || mode == 4) {
                    List<String> players = (List<String>) f.get(6);
                    w.i16(players.size());
                    for (String p : players) {
                        w.string(p);
                    }
                }
            }
            case "item_data" -> {
                w.i16((Integer) f.get(0));
                w.i16((Integer) f.get(1));
                byte[] data = (byte[]) f.get(2);
                w.u16(data.length);
                w.bytes(data);
            }
            case "open_window" -> {
                w.i8((Integer) f.get(0));
                int typeId = (Integer) f.get(1);
                w.i8(typeId);
                w.string((String) f.get(2));
                w.i8((Integer) f.get(3));
                w.bool((Boolean) f.get(4));
                if (typeId == 11) {
                    Integer entityId = (Integer) f.get(5);
                    w.i32(entityId != null ? entityId : 0);
                }
            }
            case "open_window_nohorse" -> {
                // Protocol 61: a sixth field (entity id) may be
                // present in the field list; it is not written.
                w.i8((Integer) f.get(0));
                w.i8((Integer) f.get(1));
                w.string((String) f.get(2));
                w.i8((Integer) f.get(3));
                w.bool((Boolean) f.get(4));
            }
            case "open_window_notitle" -> {
                // Protocol 51: neither useTitle nor the entity id
                // is on the wire; only the four leading fields are.
                w.i8((Integer) f.get(0));
                w.i8((Integer) f.get(1));
                w.string((String) f.get(2));
                w.i8((Integer) f.get(3));
            }
            default -> throw new IllegalArgumentException("unknown custom packet kind: " + kind);
        }
    }

    private static void writeIntField(List<Object> f, int i, Writer w, boolean asByte) {
        int v = (Integer) f.get(i);
        if (asByte) {
            w.i8(v);
        } else {
            w.i32(v);
        }
    }

    // ------------------------------------------------------------------
    // Streaming parser (no length prefix: parse from the table)
    // ------------------------------------------------------------------

    /**
     * Incremental parser for a legacy byte stream. Feed bytes in;
     * complete packets come out. A malformed packet fails the parser
     * permanently (the connection is unusable afterwards, exactly like
     * the vanilla generation's own behaviour).
     */
    public static final class StreamParser {
        private byte[] buf = new byte[8192];
        private int size = 0;
        private int legacyProtocol;

        public StreamParser() {
            this(LegacyProtocol.V1_6_4);
        }

        public StreamParser(int legacyProtocol) {
            this.legacyProtocol = legacyProtocol;
        }

        public int legacyProtocol() {
            return legacyProtocol;
        }

        /**
         * Switches the table this parser reads with. Parsing is
         * stateless per packet apart from the byte buffer, so the
         * session can adopt the client's protocol right after the
         * handshake without losing buffered bytes.
         */
        public void setLegacyProtocol(int legacyProtocol) {
            this.legacyProtocol = legacyProtocol;
        }

        public void feed(byte[] data, int offset, int length) {
            if (size + length > buf.length) {
                buf = Arrays.copyOf(buf, Math.max(buf.length * 2, size + length));
            }
            System.arraycopy(data, offset, buf, size, length);
            size += length;
        }

        /** Next complete packet, or null when more bytes are needed. */
        public LegacyPacket next() {
            if (size == 0) {
                return null;
            }
            ParseResult result;
            try {
                result = parseOne(legacyProtocol, buf, 0, size);
            } catch (LegacyWire.Underflow e) {
                return null;
            }
            int consumed = result.consumed();
            System.arraycopy(buf, consumed, buf, 0, size - consumed);
            size -= consumed;
            return result.packet();
        }

        public int bufferedBytes() {
            return size;
        }
    }
}
