package io.detergentmc.legacy;

import io.detergentmc.legacy.LegacyWire.MetaEntry;
import io.detergentmc.legacy.LegacyWire.Reader;
import io.detergentmc.legacy.LegacyWire.Slot;
import io.detergentmc.legacy.LegacyWire.Writer;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.GZIPOutputStream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LegacyWireTest {

    @Test
    void stringRoundTripUtf16Be() {
        Writer w = new Writer();
        w.string("こんにちは、世界！§1");
        byte[] bytes = w.bytes();
        // i16 char count first
        int chars = ((bytes[0] & 0xFF) << 8) | (bytes[1] & 0xFF);
        assertEquals("こんにちは、世界！§1".length(), chars);
        assertEquals("こんにちは、世界！§1", new Reader(bytes).string());
    }

    @Test
    void stringWireFormIsUtf16BeWithCharCount() {
        byte[] bytes = new Writer().string("AB").bytes();
        assertArrayEquals(new byte[]{0, 2, 0, 'A', 0, 'B'}, bytes);
    }

    @Test
    void primitivesRoundTrip() {
        Writer w = new Writer();
        w.u8(200).i8(-5).bool(true).i16(-3000).u16(60000).i32(-70000)
                .i64(123456789012345L).f32(1.5f).f64(-2.25);
        Reader r = new Reader(w.bytes());
        assertEquals(200, r.u8());
        assertEquals(-5, r.i8());
        assertTrue(r.bool());
        assertEquals(-3000, r.i16());
        assertEquals(60000, r.u16());
        assertEquals(-70000, r.i32());
        assertEquals(123456789012345L, r.i64());
        assertEquals(1.5f, r.f32());
        assertEquals(-2.25, r.f64());
        r.requireEnd("primitives");
    }

    @Test
    void emptySlotRoundTrip() {
        byte[] bytes = new Writer().slot(Slot.EMPTY).bytes();
        assertArrayEquals(new byte[]{-1, -1}, bytes);
        assertEquals(Slot.EMPTY, new Reader(bytes).slot());
    }

    @Test
    void slotWithGzipNbtRoundTrip() throws Exception {
        byte[] nbt = gzip(new byte[]{1, 2, 3, 4, 5});
        Slot slot = new Slot(276, 3, 12, nbt);
        Slot back = new Reader(new Writer().slot(slot).bytes()).slot();
        assertEquals(276, back.itemId());
        assertEquals(3, back.count());
        assertEquals(12, back.damage());
        assertArrayEquals(nbt, back.nbtGzip());
    }

    @Test
    void slotWithoutNbtRoundTrip() {
        Slot slot = new Slot(1, 64, 0, null);
        Slot back = new Reader(new Writer().slot(slot).bytes()).slot();
        assertEquals(slot.itemId(), back.itemId());
        assertNull(back.nbtGzip());
    }

    @Test
    void metadataRoundTripAllLegacyTypes() throws Exception {
        List<MetaEntry> entries = List.of(
                new MetaEntry(0, 0, (byte) 5),
                new MetaEntry(1, 1, (short) 300),
                new MetaEntry(2, 2, 123456),
                new MetaEntry(3, 3, 20.0f),
                new MetaEntry(4, 4, "名前"),
                new MetaEntry(5, 5, new Slot(35, 1, 14, null)),
                new MetaEntry(6, 6, new int[]{10, 64, -20}));
        byte[] bytes = new Writer().metadata(entries).bytes();
        // terminator 0x7F at the end
        assertEquals(0x7F, bytes[bytes.length - 1] & 0xFF);
        List<MetaEntry> back = new Reader(bytes).metadata();
        assertEquals(entries.size(), back.size());
        for (int i = 0; i < entries.size(); i++) {
            assertEquals(entries.get(i).index(), back.get(i).index());
            assertEquals(entries.get(i).type(), back.get(i).type());
            Object expected = entries.get(i).value();
            Object actual = back.get(i).value();
            if (expected instanceof int[] e) {
                assertArrayEquals(e, (int[]) actual);
            } else {
                assertEquals(expected, actual);
            }
        }
    }

    @Test
    void metadataHeaderPacksTypeAndIndex() {
        // type 3 (float), index 6 -> (3 << 5) | 6 = 0x66, the health entry
        byte[] bytes = new Writer().metadata(List.of(new MetaEntry(6, 3, 20.0f))).bytes();
        assertEquals(0x66, bytes[0] & 0xFF);
    }

    @Test
    void metadataType7DoesNotExistIn78() {
        // A header with type 7 (rotation) must be rejected on the legacy side.
        byte[] bad = new byte[]{(byte) ((7 << 5) | 0), 0, 0, 0, 0, 0x7F};
        assertThrows(IllegalArgumentException.class, () -> new Reader(bad).metadata());
    }

    @Test
    void underflowIsDistinct() {
        assertThrows(LegacyWire.Underflow.class, () -> new Reader(new byte[2]).i32());
    }

    static byte[] gzip(byte[] raw) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (GZIPOutputStream gz = new GZIPOutputStream(out)) {
            gz.write(raw);
        }
        return out.toByteArray();
    }

    @Test
    void bytes16AndNbtBytes() {
        Writer w = new Writer();
        w.bytes16("abc".getBytes(StandardCharsets.UTF_8)).nbtBytes(null).nbtBytes(new byte[]{9});
        Reader r = new Reader(w.bytes());
        assertArrayEquals(new byte[]{'a', 'b', 'c'}, r.bytes16());
        assertNull(r.nbtBytes());
        assertArrayEquals(new byte[]{9}, r.nbtBytes());
    }
}
