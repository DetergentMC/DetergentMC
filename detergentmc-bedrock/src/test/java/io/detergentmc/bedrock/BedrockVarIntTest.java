package io.detergentmc.bedrock;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.detergentmc.bedrock.BedrockVarInt.ByteReader;
import io.detergentmc.bedrock.BedrockVarInt.ByteWriter;
import org.junit.jupiter.api.Test;

/** Bedrock の基本型（符号なし VarInt・リトルエンディアン・文字列）のテスト。 */
class BedrockVarIntTest {

    private static byte[] writeUnsigned(long value) {
        ByteWriter out = new ByteWriter();
        BedrockVarInt.writeUnsignedVarInt(out, value);
        return out.bytes();
    }

    @Test
    void unsignedVarIntRoundTrip() {
        for (long value : new long[]{0, 1, 127, 128, 300, 16383, 16384,
                2097151, 268435455L, 4294967295L}) {
            byte[] bytes = writeUnsigned(value);
            assertEquals(value, BedrockVarInt.readUnsignedVarInt(new ByteReader(bytes)),
                    "値 " + value + " の往復");
        }
    }

    @Test
    void unsignedVarIntSingleByteBelow128() {
        assertArrayEquals(new byte[]{0x01}, writeUnsigned(1));
        assertArrayEquals(new byte[]{0x7f}, writeUnsigned(127));
        // 128 は 2 バイト（Java の符号付き VarInt とは違い、負数への符号拡張は無い）。
        assertArrayEquals(new byte[]{(byte) 0x80, 0x01}, writeUnsigned(128));
    }

    @Test
    void unsignedVarIntTooLongIsRejected() {
        byte[] tooLong = new byte[]{(byte) 0x80, (byte) 0x80, (byte) 0x80, (byte) 0x80,
                (byte) 0x80, (byte) 0x80, (byte) 0x80, (byte) 0x80, (byte) 0x80, (byte) 0x80,
                0x01};
        assertThrows(IllegalArgumentException.class,
                () -> BedrockVarInt.readUnsignedVarInt(new ByteReader(tooLong)));
    }

    @Test
    void zigzagRoundTrip() {
        for (int value : new int[]{0, 1, -1, 2, -2, 63, -64, 123456, -123456,
                Integer.MAX_VALUE, Integer.MIN_VALUE}) {
            ByteWriter out = new ByteWriter();
            BedrockVarInt.writeZigzag32(out, value);
            assertEquals(value, BedrockVarInt.readZigzag32(new ByteReader(out.bytes())),
                    "zigzag32 " + value);
        }
        for (long value : new long[]{0L, 1L, -1L, 1234567890123L, -1234567890123L}) {
            ByteWriter out = new ByteWriter();
            BedrockVarInt.writeZigzag64(out, value);
            assertEquals(value, BedrockVarInt.readZigzag64(new ByteReader(out.bytes())),
                    "zigzag64 " + value);
        }
    }

    @Test
    void littleEndianPrimitives() {
        ByteWriter out = new ByteWriter();
        out.writeLittleInt(0x01020304);
        out.writeLittleShort(0x0506);
        out.writeLittleLong(0x0102030405060708L);
        out.writeLittleFloat(1.5f);
        ByteReader in = new ByteReader(out.bytes());
        assertEquals(0x01020304, in.readLittleInt());
        assertEquals(0x0506, in.readLittleUnsignedShort());
        assertEquals(0x0102030405060708L, in.readLittleLong());
        assertEquals(1.5f, in.readLittleFloat());
    }

    @Test
    void bigEndianForLoginProtocol() {
        ByteWriter out = new ByteWriter();
        out.writeBigInt(630);
        assertArrayEquals(new byte[]{0, 0, 0x02, 0x76}, out.bytes());
        assertEquals(630, new ByteReader(out.bytes()).readBigInt());
    }

    @Test
    void bedrockStringIsLittleEndianLength() {
        ByteWriter out = new ByteWriter();
        BedrockVarInt.writeString(out, "Steve");
        // u32 LE 長 5＋UTF-8。
        assertArrayEquals(new byte[]{5, 0, 0, 0, 'S', 't', 'e', 'v', 'e'}, out.bytes());
        assertEquals("Steve", BedrockVarInt.readString(new ByteReader(out.bytes())));
        // 日本語（マルチバイト）も往復する。
        ByteWriter japanese = new ByteWriter();
        BedrockVarInt.writeString(japanese, "ああ");
        assertEquals("ああ", BedrockVarInt.readString(new ByteReader(japanese.bytes())));
    }
}
