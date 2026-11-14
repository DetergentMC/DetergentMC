package io.detergentmc.bedrock;

import java.nio.charset.StandardCharsets;

/**
 * Bedrock プロトコルの基本型の読み書き。
 *
 * <p>Java 版とはここが違う（混同しないこと）：
 * <ul>
 *   <li>VarInt は<b>符号なし LEB128</b>（Java の VarInt は符号付きで負数が
 *       5 バイトに符号拡張されるが、Bedrock は符号なしで値そのまま）。
 *       パケット ID や長さはこれで読む。値が 128 未満なら 1 バイトで、
 *       Java の VarInt と見た目が同じになる。</li>
 *   <li>整数・浮動小数点は<b>リトルエンディアン</b>（li16 / li32 / li64 /
 *       lf32）。Java 版はビッグエンディアン。ただし Login パケットの
 *       protocol_version（i32）だけは例外でビッグエンディアンである
 *       （minecraft-data の注記「Protocol version (Big Endian!)」）。</li>
 *   <li>文字列は <b>u32 リトルエンディアン長＋UTF-8</b>（LittleString と
 *       string はどちらもこの形）。Java 版の VarInt 長とは違う。</li>
 *   <li>符号付き VarInt は zigzag32 / zigzag64（値の符号を最下位ビットに
 *       畳む。Java 版の符号拡張とは別物）。</li>
 * </ul>
 */
public final class BedrockVarInt {

    private BedrockVarInt() {
    }

    /** 符号なし VarInt を書く。 */
    public static void writeUnsignedVarInt(ByteWriter out, long value) {
        long remaining = value;
        while ((remaining & ~0x7FL) != 0) {
            out.writeByte((int) ((remaining & 0x7F) | 0x80));
            remaining >>>= 7;
        }
        out.writeByte((int) remaining);
    }

    /** 符号なし VarInt を読む。 */
    public static long readUnsignedVarInt(ByteReader in) {
        long value = 0;
        int shift = 0;
        while (true) {
            int b = in.readUnsignedByte();
            value |= (long) (b & 0x7F) << shift;
            if ((b & 0x80) == 0) {
                return value;
            }
            shift += 7;
            if (shift >= 70) {
                throw new IllegalArgumentException("Bedrock VarInt が長すぎます。");
            }
        }
    }

    /** 符号なし VarInt（int 範囲）を書く。 */
    public static void writeVarInt(ByteWriter out, int value) {
        writeUnsignedVarInt(out, value & 0xFFFFFFFFL);
    }

    /** 符号なし VarInt を int として読む。 */
    public static int readVarInt(ByteReader in) {
        long value = readUnsignedVarInt(in);
        if (value > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Bedrock VarInt が int の範囲を超えています: " + value);
        }
        return (int) value;
    }

    /** 符号なし VarLong を書く。 */
    public static void writeVarLong(ByteWriter out, long value) {
        writeUnsignedVarInt(out, value);
    }

    /** 符号なし VarLong を読む。 */
    public static long readVarLong(ByteReader in) {
        return readUnsignedVarInt(in);
    }

    /** zigzag32 を書く。 */
    public static void writeZigzag32(ByteWriter out, int value) {
        writeUnsignedVarInt(out, ((long) value << 1) ^ (value >> 31));
    }

    /** zigzag32 を読む。 */
    public static int readZigzag32(ByteReader in) {
        long raw = readUnsignedVarInt(in);
        return (int) ((raw >>> 1) ^ -(raw & 1));
    }

    /** zigzag64 を書く。 */
    public static void writeZigzag64(ByteWriter out, long value) {
        writeUnsignedVarInt(out, (value << 1) ^ (value >> 63));
    }

    /** zigzag64 を読む。 */
    public static long readZigzag64(ByteReader in) {
        long raw = readUnsignedVarInt(in);
        return (raw >>> 1) ^ -(raw & 1);
    }

    /** Bedrock 文字列（u32 LE 長＋UTF-8）を書く。 */
    public static void writeString(ByteWriter out, String value) {
        byte[] utf8 = value.getBytes(StandardCharsets.UTF_8);
        out.writeLittleInt(utf8.length);
        out.writeBytes(utf8);
    }

    /** Bedrock 文字列（u32 LE 長＋UTF-8）を読む。 */
    public static String readString(ByteReader in) {
        int length = in.readLittleInt();
        if (length < 0) {
            throw new IllegalArgumentException("Bedrock 文字列の長さが負です: " + length);
        }
        return new String(in.readBytes(length), StandardCharsets.UTF_8);
    }

    /** バイト列をためる書き手。 */
    public static final class ByteWriter {
        private byte[] buffer = new byte[64];
        private int size;

        public void writeByte(int value) {
            ensure(1);
            buffer[size++] = (byte) value;
        }

        public void writeBytes(byte[] value) {
            ensure(value.length);
            System.arraycopy(value, 0, buffer, size, value.length);
            size += value.length;
        }

        public void writeBoolean(boolean value) {
            writeByte(value ? 1 : 0);
        }

        /** 符号なし16ビット・リトルエンディアン。 */
        public void writeLittleShort(int value) {
            writeByte(value);
            writeByte(value >> 8);
        }

        /** 符号付き16ビット・リトルエンディアン。 */
        public void writeLittleInt16(int value) {
            writeLittleShort(value);
        }

        /** 32ビット・リトルエンディアン。 */
        public void writeLittleInt(int value) {
            writeByte(value);
            writeByte(value >> 8);
            writeByte(value >> 16);
            writeByte(value >> 24);
        }

        /** 32ビット・ビッグエンディアン（Login の protocol_version 用）。 */
        public void writeBigInt(int value) {
            writeByte(value >> 24);
            writeByte(value >> 16);
            writeByte(value >> 8);
            writeByte(value);
        }

        /** 64ビット・リトルエンディアン。 */
        public void writeLittleLong(long value) {
            for (int i = 0; i < 8; i++) {
                writeByte((int) (value >> (i * 8)));
            }
        }

        /** 64ビット・ビッグエンディアン（RakNet の時刻・GUID 用）。 */
        public void writeBigLong(long value) {
            for (int i = 7; i >= 0; i--) {
                writeByte((int) (value >> (i * 8)));
            }
        }

        /** float32・リトルエンディアン。 */
        public void writeLittleFloat(float value) {
            writeLittleInt(Float.floatToIntBits(value));
        }

        public byte[] bytes() {
            byte[] out = new byte[size];
            System.arraycopy(buffer, 0, out, 0, size);
            return out;
        }

        public int size() {
            return size;
        }

        private void ensure(int extra) {
            if (size + extra > buffer.length) {
                byte[] next = new byte[Math.max(buffer.length * 2, size + extra)];
                System.arraycopy(buffer, 0, next, 0, size);
                buffer = next;
            }
        }
    }

    /** バイト列を進みながら読む読み手。 */
    public static final class ByteReader {
        private final byte[] bytes;
        private int cursor;

        public ByteReader(byte[] bytes) {
            this(bytes, 0);
        }

        public ByteReader(byte[] bytes, int offset) {
            this.bytes = bytes;
            this.cursor = offset;
        }

        public int remaining() {
            return bytes.length - cursor;
        }

        public int position() {
            return cursor;
        }

        public boolean hasRemaining() {
            return cursor < bytes.length;
        }

        private void need(int length) {
            if (length < 0 || bytes.length - cursor < length) {
                throw new IllegalArgumentException(
                        "Bedrock ペイロードが途中で切れています（位置=" + cursor
                                + "、必要=" + length + "バイト）。");
            }
        }

        public int readUnsignedByte() {
            need(1);
            return bytes[cursor++] & 0xFF;
        }

        public boolean readBoolean() {
            return readUnsignedByte() != 0;
        }

        public byte[] readBytes(int length) {
            need(length);
            byte[] value = new byte[length];
            System.arraycopy(bytes, cursor, value, 0, length);
            cursor += length;
            return value;
        }

        public byte[] rest() {
            return readBytes(remaining());
        }

        /** 符号なし16ビット・リトルエンディアン。 */
        public int readLittleUnsignedShort() {
            need(2);
            int value = (bytes[cursor] & 0xFF) | ((bytes[cursor + 1] & 0xFF) << 8);
            cursor += 2;
            return value;
        }

        /** 符号付き16ビット・リトルエンディアン。 */
        public short readLittleShort() {
            return (short) readLittleUnsignedShort();
        }

        /** 32ビット・リトルエンディアン。 */
        public int readLittleInt() {
            need(4);
            int value = (bytes[cursor] & 0xFF)
                    | ((bytes[cursor + 1] & 0xFF) << 8)
                    | ((bytes[cursor + 2] & 0xFF) << 16)
                    | ((bytes[cursor + 3] & 0xFF) << 24);
            cursor += 4;
            return value;
        }

        /** 32ビット・ビッグエンディアン（Login の protocol_version 用）。 */
        public int readBigInt() {
            need(4);
            int value = ((bytes[cursor] & 0xFF) << 24)
                    | ((bytes[cursor + 1] & 0xFF) << 16)
                    | ((bytes[cursor + 2] & 0xFF) << 8)
                    | (bytes[cursor + 3] & 0xFF);
            cursor += 4;
            return value;
        }

        /** 64ビット・リトルエンディアン。 */
        public long readLittleLong() {
            need(8);
            long value = 0;
            for (int i = 0; i < 8; i++) {
                value |= (long) (bytes[cursor + i] & 0xFF) << (i * 8);
            }
            cursor += 8;
            return value;
        }

        /** 64ビット・ビッグエンディアン（RakNet の時刻・GUID 用）。 */
        public long readBigLong() {
            need(8);
            long value = 0;
            for (int i = 0; i < 8; i++) {
                value = (value << 8) | (bytes[cursor + i] & 0xFFL);
            }
            cursor += 8;
            return value;
        }

        /** float32・リトルエンディアン。 */
        public float readLittleFloat() {
            return Float.intBitsToFloat(readLittleInt());
        }

        /** 符号なし16ビット・ビッグエンディアン（RakNet の文字列長・MTU 用）。 */
        public int readBigUnsignedShort() {
            need(2);
            int value = ((bytes[cursor] & 0xFF) << 8) | (bytes[cursor + 1] & 0xFF);
            cursor += 2;
            return value;
        }
    }
}
