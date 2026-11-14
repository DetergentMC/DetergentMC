package io.detergentmc.protocol;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import org.junit.jupiter.api.Test;

/** VarIntの読み書き。 */
class VarIntTest {

    @Test
    void encodesBoundaryValues() {
        assertArrayEquals(new byte[]{0x00}, VarInt.encode(0));
        assertArrayEquals(new byte[]{0x7F}, VarInt.encode(127));
        assertArrayEquals(new byte[]{(byte) 0x80, 0x01}, VarInt.encode(128));
        assertArrayEquals(new byte[]{(byte) 0xAC, 0x02}, VarInt.encode(300));
        assertArrayEquals(new byte[]{(byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, 0x0F},
                VarInt.encode(-1));
        assertArrayEquals(new byte[]{(byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, 0x07},
                VarInt.encode(Integer.MAX_VALUE));
        assertArrayEquals(new byte[]{(byte) 0x80, (byte) 0x80, (byte) 0x80, (byte) 0x80, 0x08},
                VarInt.encode(Integer.MIN_VALUE));
    }

    @Test
    void roundTripsBoundaryValues() {
        for (int value : new int[]{0, 1, 127, 128, 255, 300, 16384,
                2097151, Integer.MAX_VALUE, -1, -128, Integer.MIN_VALUE}) {
            VarInt.ReadResult result = VarInt.decode(VarInt.encode(value), 0);
            assertEquals(value, result.value(), "value " + value);
            assertEquals(VarInt.encodedSize(value), result.bytesRead());
        }
    }

    @Test
    void encodedSizeMatches() {
        assertEquals(1, VarInt.encodedSize(0));
        assertEquals(1, VarInt.encodedSize(127));
        assertEquals(2, VarInt.encodedSize(128));
        assertEquals(2, VarInt.encodedSize(16383));
        assertEquals(3, VarInt.encodedSize(16384));
        assertEquals(5, VarInt.encodedSize(-1));
        assertEquals(5, VarInt.encodedSize(Integer.MAX_VALUE));
    }

    @Test
    void decodesFromOffset() {
        byte[] bytes = {0x55, (byte) 0x80, 0x01, 0x33};
        VarInt.ReadResult result = VarInt.decode(bytes, 1);
        assertEquals(128, result.value());
        assertEquals(2, result.bytesRead());
    }

    @Test
    void streamRoundTrip() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (int value : new int[]{0, 127, 128, Integer.MAX_VALUE, -1}) {
            VarInt.writeTo(value, out);
        }
        ByteArrayInputStream in = new ByteArrayInputStream(out.toByteArray());
        assertEquals(0, VarInt.readFrom(in));
        assertEquals(127, VarInt.readFrom(in));
        assertEquals(128, VarInt.readFrom(in));
        assertEquals(Integer.MAX_VALUE, VarInt.readFrom(in));
        assertEquals(-1, VarInt.readFrom(in));
        assertEquals(-1, in.read(), "ストリームは読み切れていること");
    }

    @Test
    void rejectsTooLongVarInt() {
        // 6バイト目にも続きがある不正なVarInt。
        byte[] tooLong = {(byte) 0x80, (byte) 0x80, (byte) 0x80,
                (byte) 0x80, (byte) 0x80, 0x01};
        assertThrows(IllegalArgumentException.class, () -> VarInt.decode(tooLong, 0));
        assertThrows(IllegalArgumentException.class,
                () -> VarInt.readFrom(new ByteArrayInputStream(tooLong)));
    }

    @Test
    void rejectsTruncatedVarInt() {
        byte[] truncated = {(byte) 0x80};
        assertThrows(IllegalArgumentException.class, () -> VarInt.decode(truncated, 0));
        assertThrows(Exception.class,
                () -> VarInt.readFrom(new ByteArrayInputStream(truncated)));
    }
}
