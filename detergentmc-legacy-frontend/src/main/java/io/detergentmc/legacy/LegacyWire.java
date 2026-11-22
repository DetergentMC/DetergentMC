package io.detergentmc.legacy;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Primitive codec for the pre-Netty wire format (legacy protocol 78).
 *
 * <p>All multi-byte numbers are big-endian. Strings are an i16
 * character count followed by UTF-16BE code units (the count is in
 * UTF-16 code units, not bytes). There are no VarInts anywhere in
 * this generation.
 *
 * <p>Values are modelled with plain Java types so packets can be
 * handled generically (see {@link LegacyPacketIO}):
 * U8/I8/I16/U16/I32 -&gt; Integer, I64 -&gt; Long, F32 -&gt; Float,
 * F64 -&gt; Double, BOOL -&gt; Boolean, STRING -&gt; String,
 * BYTES16/NBTBYTES -&gt; byte[], SLOT -&gt; {@link Slot},
 * METADATA -&gt; {@code List<MetaEntry>}.
 */
public final class LegacyWire {

    private LegacyWire() {
    }

    /** Thrown when a packet parse runs out of bytes (incomplete packet). */
    public static final class Underflow extends IllegalArgumentException {
        public Underflow() {
            super("packet underflow");
        }
    }

    /** One inventory slot. {@code itemId < 0} means empty. */
    public record Slot(int itemId, int count, int damage, byte[] nbtGzip) {
        public static final Slot EMPTY = new Slot(-1, 0, 0, null);

        public boolean isEmpty() {
            return itemId < 0;
        }
    }

    /**
     * The item IDs whose slots carry NBT on the protocol-29
     * (1.2.5) wire: exactly the damageable items. Measured in the
     * real 1.2.5 jar (docs/PROXY-1.2.5.md): the base packet class's
     * slot reader/writer appends the gzip NBT only when the item
     * satisfies {@code maxDamage > 0 && !hasSubtypes} (no 1.2.5
     * item class overrides the second condition method to true),
     * and the set below is the item registry's damageable subset —
     * every sword, tool, armour piece, the bow, the fishing rod,
     * flint and steel and the shears, extracted from the jar's
     * item registrations and their constructors.
     */
    public static final java.util.Set<Integer> DAMAGEABLE_ITEMS_1_2_5 = java.util.Set.of(
            256, 257, 258, 259, 261, 267, 268, 269, 270, 271, 272, 273, 274, 275,
            276, 277, 278, 279, 283, 284, 285, 286, 290, 291, 292, 293, 294, 298,
            299, 300, 301, 302, 303, 304, 305, 306, 307, 308, 309, 310, 311, 312,
            313, 314, 315, 316, 317, 346, 359);

    /** True when a protocol-29 slot of this item id carries NBT. */
    public static boolean slotCarriesNbt29(int itemId) {
        return DAMAGEABLE_ITEMS_1_2_5.contains(itemId);
    }

    /** One entity-metadata (DataWatcher) entry. */
    public record MetaEntry(int index, int type, Object value) {
        // value by type: 0 Byte, 1 Short, 2 Integer, 3 Float,
        // 4 String, 5 Slot, 6 int[3], 7 float[3] (type 7 exists only
        // on the modern side; legacy protocol 78 has types 0-6).
    }

    /** Sequential reader over one packet's payload. */
    public static final class Reader {
        private final byte[] buf;
        private int pos;
        private boolean plainWatcherSlot;
        private boolean conditionalSlotNbt;

        public Reader(byte[] buf) {
            this.buf = buf;
        }

        /**
         * Selects the plain (no-NBT) DataWatcher slot form of
         * legacy protocols 47 and 39 for {@link #metadata()}.
         * Set by {@code LegacyPacketIO} from the session protocol.
         */
        public Reader plainWatcherSlot(boolean plain) {
            this.plainWatcherSlot = plain;
            return this;
        }

        /**
         * Selects the protocol-29 slot form for {@link #slot()}:
         * the gzip NBT tail is present only for damageable items
         * (see {@link LegacyWire#DAMAGEABLE_ITEMS_1_2_5}). Set by
         * {@code LegacyPacketIO} from the session protocol.
         */
        public Reader conditionalSlotNbt(boolean conditional) {
            this.conditionalSlotNbt = conditional;
            return this;
        }

        public int remaining() {
            return buf.length - pos;
        }

        public boolean atEnd() {
            return pos >= buf.length;
        }

        public void requireEnd(String what) {
            if (!atEnd()) {
                throw new IllegalArgumentException(
                        what + ": " + remaining() + " trailing bytes");
            }
        }

        private void need(int n) {
            if (remaining() < n) {
                throw new Underflow();
            }
        }

        public int u8() {
            need(1);
            return buf[pos++] & 0xFF;
        }

        public int i8() {
            return (byte) u8();
        }

        public boolean bool() {
            return u8() != 0;
        }

        public int i16() {
            need(2);
            int v = ((buf[pos] & 0xFF) << 8) | (buf[pos + 1] & 0xFF);
            pos += 2;
            return (short) v;
        }

        public int u16() {
            need(2);
            int v = ((buf[pos] & 0xFF) << 8) | (buf[pos + 1] & 0xFF);
            pos += 2;
            return v;
        }

        public int i32() {
            need(4);
            int v = ((buf[pos] & 0xFF) << 24) | ((buf[pos + 1] & 0xFF) << 16)
                    | ((buf[pos + 2] & 0xFF) << 8) | (buf[pos + 3] & 0xFF);
            pos += 4;
            return v;
        }

        public long i64() {
            return ((long) i32() << 32) | (i32() & 0xFFFF_FFFFL);
        }

        public float f32() {
            return Float.intBitsToFloat(i32());
        }

        public double f64() {
            return Double.longBitsToDouble(i64());
        }

        public byte[] bytes(int n) {
            need(n);
            byte[] out = new byte[n];
            System.arraycopy(buf, pos, out, 0, n);
            pos += n;
            return out;
        }

        public byte[] rest() {
            return bytes(remaining());
        }

        /** i16 char count + UTF-16BE. */
        public String string() {
            int chars = i16();
            if (chars < 0) {
                throw new IllegalArgumentException("negative string length");
            }
            byte[] raw = bytes(chars * 2);
            return new String(raw, StandardCharsets.UTF_16BE);
        }

        /** i16 byte length + raw bytes. */
        public byte[] bytes16() {
            int len = i16();
            if (len < 0) {
                throw new IllegalArgumentException("negative byte-array length");
            }
            return bytes(len);
        }

        /** i16 byte length + raw NBT bytes; -1 length means absent (null). */
        public byte[] nbtBytes() {
            int len = i16();
            if (len < 0) {
                return null;
            }
            return bytes(len);
        }

        public Slot slot() {
            int itemId = i16();
            if (itemId < 0) {
                return Slot.EMPTY;
            }
            int count = u8();
            int damage = i16();
            if (conditionalSlotNbt && !slotCarriesNbt29(itemId)) {
                // Protocol 29: no NBT tail at all for this item.
                return new Slot(itemId, count, damage, null);
            }
            int nbtLen = i16();
            byte[] nbt = nbtLen < 0 ? null : bytes(nbtLen);
            return new Slot(itemId, count, damage, nbt);
        }

        /**
         * A DataWatcher slot entry in the plain form (legacy
         * protocols 47 and 39): item id, count and damage only —
         * no NBT length, no NBT bytes (measured in the 1.4.2 and
         * 1.3.2 jars; the full-slot watcher form arrived in
         * 1.4.4). An empty entry is just the -1 id; vanilla
         * 1.3.2 never writes one, and the 1.4.2 reader consumes
         * nothing further for it either.
         */
        public Slot watcherSlot() {
            int itemId = i16();
            if (itemId < 0) {
                return Slot.EMPTY;
            }
            int count = u8();
            int damage = i16();
            return new Slot(itemId, count, damage, null);
        }

        /** DataWatcher entries until the 0x7F terminator. */
        public List<MetaEntry> metadata() {
            List<MetaEntry> entries = new ArrayList<>();
            while (true) {
                int header = u8();
                if (header == 0x7F) {
                    return entries;
                }
                int type = (header >> 5) & 0x7;
                int index = header & 0x1F;
                Object value = switch (type) {
                    case 0 -> (byte) i8();
                    case 1 -> (short) i16();
                    case 2 -> i32();
                    case 3 -> f32();
                    case 4 -> string();
                    case 5 -> plainWatcherSlot ? watcherSlot() : slot();
                    case 6 -> new int[]{i32(), i32(), i32()};
                    default -> throw new IllegalArgumentException(
                            "legacy metadata type " + type + " does not exist in protocol 78");
                };
                entries.add(new MetaEntry(index, type, value));
            }
        }
    }

    /** Sequential writer for one packet (ID byte first). */
    public static final class Writer {
        private final ByteArrayOutputStream out = new ByteArrayOutputStream();
        private boolean plainWatcherSlot;
        private boolean conditionalSlotNbt;

        public Writer() {
        }

        public Writer(int packetId) {
            u8(packetId);
        }

        /**
         * Selects the plain (no-NBT) DataWatcher slot form of
         * legacy protocols 47 and 39 for {@link #metadata(List)}.
         * Set by {@code LegacyPacketIO} from the target protocol.
         */
        public Writer plainWatcherSlot(boolean plain) {
            this.plainWatcherSlot = plain;
            return this;
        }

        /**
         * Selects the protocol-29 slot form for {@link #slot}:
         * the gzip NBT tail is written only for damageable items
         * (see {@link LegacyWire#DAMAGEABLE_ITEMS_1_2_5}); an NBT
         * on any other item is dropped, exactly as a real 1.2.5
         * writer never sends it. Set by {@code LegacyPacketIO}
         * from the target protocol.
         */
        public Writer conditionalSlotNbt(boolean conditional) {
            this.conditionalSlotNbt = conditional;
            return this;
        }

        public byte[] bytes() {
            return out.toByteArray();
        }

        public Writer u8(int v) {
            out.write(v & 0xFF);
            return this;
        }

        public Writer i8(int v) {
            return u8(v);
        }

        public Writer bool(boolean v) {
            return u8(v ? 1 : 0);
        }

        public Writer i16(int v) {
            out.write((v >> 8) & 0xFF);
            out.write(v & 0xFF);
            return this;
        }

        public Writer u16(int v) {
            return i16(v);
        }

        public Writer i32(int v) {
            out.write((v >> 24) & 0xFF);
            out.write((v >> 16) & 0xFF);
            out.write((v >> 8) & 0xFF);
            out.write(v & 0xFF);
            return this;
        }

        public Writer i64(long v) {
            i32((int) (v >> 32));
            i32((int) v);
            return this;
        }

        public Writer f32(float v) {
            return i32(Float.floatToIntBits(v));
        }

        public Writer f64(double v) {
            return i64(Double.doubleToLongBits(v));
        }

        public Writer bytes(byte[] data) {
            out.write(data, 0, data.length);
            return this;
        }

        /** i16 char count + UTF-16BE. */
        public Writer string(String s) {
            if (s == null) {
                s = "";
            }
            // The legacy format counts UTF-16 code units; a Java String's
            // length() is exactly that.
            if (s.length() > 32767) {
                s = s.substring(0, 32767);
            }
            i16(s.length());
            return bytes(s.getBytes(StandardCharsets.UTF_16BE));
        }

        /** i16 byte length + raw bytes. */
        public Writer bytes16(byte[] data) {
            i16(data.length);
            return bytes(data);
        }

        /** i16 byte length + raw NBT bytes; null writes -1. */
        public Writer nbtBytes(byte[] data) {
            if (data == null) {
                return i16(-1);
            }
            i16(data.length);
            return bytes(data);
        }

        public Writer slot(Slot slot) {
            if (slot == null || slot.isEmpty()) {
                return i16(-1);
            }
            i16(slot.itemId());
            u8(slot.count());
            i16(slot.damage());
            if (conditionalSlotNbt && !slotCarriesNbt29(slot.itemId())) {
                // Protocol 29: no NBT tail at all for this item.
                return this;
            }
            if (slot.nbtGzip() == null) {
                i16(-1);
            } else {
                i16(slot.nbtGzip().length);
                bytes(slot.nbtGzip());
            }
            return this;
        }

        /**
         * A DataWatcher slot entry in the plain form (legacy
         * protocols 47 and 39): item id, count and damage only;
         * any NBT is dropped, exactly as those generations' own
         * writers never send it (measured in the 1.4.2 and 1.3.2
         * jars). An empty entry is just the -1 id.
         */
        public Writer watcherSlot(Slot slot) {
            if (slot == null || slot.isEmpty()) {
                return i16(-1);
            }
            i16(slot.itemId());
            u8(slot.count());
            i16(slot.damage());
            return this;
        }

        public Writer metadata(List<MetaEntry> entries) {
            for (MetaEntry e : entries) {
                u8(((e.type() & 0x7) << 5) | (e.index() & 0x1F));
                switch (e.type()) {
                    case 0 -> i8((Byte) e.value());
                    case 1 -> i16((Short) e.value());
                    case 2 -> i32((Integer) e.value());
                    case 3 -> f32((Float) e.value());
                    case 4 -> string((String) e.value());
                    case 5 -> {
                        if (plainWatcherSlot) {
                            watcherSlot((Slot) e.value());
                        } else {
                            slot((Slot) e.value());
                        }
                    }
                    case 6 -> {
                        int[] p = (int[]) e.value();
                        i32(p[0]);
                        i32(p[1]);
                        i32(p[2]);
                    }
                    default -> throw new IllegalArgumentException(
                            "cannot write legacy metadata type " + e.type());
                }
            }
            return u8(0x7F);
        }
    }
}
