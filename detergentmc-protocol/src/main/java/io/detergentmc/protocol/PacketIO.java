package io.detergentmc.protocol;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * パケットのペイロード（バイト列）に対する、プリミティブ型の読み書き。
 *
 * <p>文字列は VarInt 長＋UTF-8（Minecraft の String 型）、VarInt / VarLong
 * は {@link VarInt}、UUID は 16 バイトのビッグエンディアン、Position は
 * 1 個の i64 に x（26ビット）・z（26ビット）・y（12ビット）を詰めたもの。
 * 読み取りはオフセットを進めながら行い、末尾を超えたら
 * {@link IllegalArgumentException} になる。書き込みは
 * {@link ByteArrayOutputStream} にためて {@link #bytes()} で取り出す。
 */
public final class PacketIO {

    private PacketIO() {
    }

    /** ペイロードの読み手。 */
    public static final class Reader {
        private final byte[] bytes;
        private int cursor;

        public Reader(byte[] bytes) {
            this.bytes = bytes;
            this.cursor = 0;
        }

        /** まだ読んでいないバイト数。 */
        public int remaining() {
            return bytes.length - cursor;
        }

        /** 今の読み位置。 */
        public int position() {
            return cursor;
        }

        /** 読み位置を戻す（NBT の置き換えなどで使う）。 */
        public void rewind(int position) {
            if (position < 0 || position > bytes.length) {
                throw new IllegalArgumentException("読み位置が範囲外です: " + position);
            }
            this.cursor = position;
        }

        /** 残りのバイト列をそのまま返す。 */
        public byte[] rest() {
            byte[] value = new byte[remaining()];
            System.arraycopy(bytes, cursor, value, 0, value.length);
            cursor = bytes.length;
            return value;
        }

        private void need(int length) {
            if (length < 0 || bytes.length - cursor < length) {
                throw new IllegalArgumentException(
                        "ペイロードが途中で切れています（位置=" + cursor + "、必要=" + length + "バイト）。");
            }
        }

        public boolean readBoolean() {
            need(1);
            return bytes[cursor++] != 0;
        }

        public byte readByte() {
            need(1);
            return bytes[cursor++];
        }

        public int readUnsignedByte() {
            return readByte() & 0xFF;
        }

        public short readShort() {
            need(2);
            short value = (short) ((bytes[cursor] << 8) | (bytes[cursor + 1] & 0xFF));
            cursor += 2;
            return value;
        }

        public int readUnsignedShort() {
            need(2);
            int value = ((bytes[cursor] & 0xFF) << 8) | (bytes[cursor + 1] & 0xFF);
            cursor += 2;
            return value;
        }

        public int readInt() {
            need(4);
            int value = ((bytes[cursor] & 0xFF) << 24)
                    | ((bytes[cursor + 1] & 0xFF) << 16)
                    | ((bytes[cursor + 2] & 0xFF) << 8)
                    | (bytes[cursor + 3] & 0xFF);
            cursor += 4;
            return value;
        }

        public long readLong() {
            need(8);
            long value = 0;
            for (int i = 0; i < 8; i++) {
                value = (value << 8) | (bytes[cursor + i] & 0xFFL);
            }
            cursor += 8;
            return value;
        }

        public float readFloat() {
            return Float.intBitsToFloat(readInt());
        }

        public double readDouble() {
            return Double.longBitsToDouble(readLong());
        }

        public int readVarInt() {
            VarInt.ReadResult result = VarInt.decode(bytes, cursor);
            cursor += result.bytesRead();
            return result.value();
        }

        public long readVarLong() {
            long value = 0;
            int position = 0;
            while (true) {
                need(1);
                byte current = bytes[cursor++];
                value |= (long) (current & 0x7F) << (7 * position);
                position++;
                if (position > 10) {
                    throw new IllegalArgumentException("VarLongが長すぎます。");
                }
                if ((current & 0x80) == 0) {
                    return value;
                }
            }
        }

        /** Minecraft の文字列（VarInt 長＋UTF-8）。 */
        public String readString() {
            int length = readVarInt();
            if (length < 0) {
                throw new IllegalArgumentException("文字列の長さが負です: " + length);
            }
            need(length);
            String value = new String(bytes, cursor, length, StandardCharsets.UTF_8);
            cursor += length;
            return value;
        }

        /** VarInt 長＋バイト列。 */
        public byte[] readByteArray() {
            int length = readVarInt();
            if (length < 0) {
                throw new IllegalArgumentException("バイト配列の長さが負です: " + length);
            }
            need(length);
            byte[] value = new byte[length];
            System.arraycopy(bytes, cursor, value, 0, length);
            cursor += length;
            return value;
        }

        /** 固定長バイト列。 */
        public byte[] readBytes(int length) {
            need(length);
            byte[] value = new byte[length];
            System.arraycopy(bytes, cursor, value, 0, length);
            cursor += length;
            return value;
        }

        public UUID readUuid() {
            return new UUID(readLong(), readLong());
        }

        /** Position（i64 に詰めた x/z/y）を生の i64 のまま読む。 */
        public long readPositionRaw() {
            return readLong();
        }

        /** 名前付き NBT（Join Game の codec など）。 */
        public Nbt.NamedResult readNamedNbt() {
            Nbt.NamedResult result = Nbt.readNamed(bytes, cursor);
            cursor += result.bytesRead();
            return result;
        }

        /** 名前なし NBT（テキストコンポーネントなど）。 */
        public Nbt.ReadResult readAnonymousNbt() {
            Nbt.ReadResult result = Nbt.readAnonymous(bytes, cursor);
            cursor += result.bytesRead();
            return result;
        }

        /** 任意値（bool＋中身）の有無を読む。 */
        public boolean readPresent() {
            return readBoolean();
        }
    }

    /** ペイロードの書き手。 */
    public static final class Writer {
        private final ByteArrayOutputStream out = new ByteArrayOutputStream();

        public byte[] bytes() {
            return out.toByteArray();
        }

        public Writer writeBoolean(boolean value) {
            out.write(value ? 1 : 0);
            return this;
        }

        public Writer writeByte(int value) {
            out.write(value & 0xFF);
            return this;
        }

        public Writer writeUnsignedByte(int value) {
            return writeByte(value);
        }

        public Writer writeShort(int value) {
            out.write((value >> 8) & 0xFF);
            out.write(value & 0xFF);
            return this;
        }

        public Writer writeUnsignedShort(int value) {
            return writeShort(value);
        }

        public Writer writeInt(int value) {
            out.write((value >> 24) & 0xFF);
            out.write((value >> 16) & 0xFF);
            out.write((value >> 8) & 0xFF);
            out.write(value & 0xFF);
            return this;
        }

        public Writer writeLong(long value) {
            for (int i = 7; i >= 0; i--) {
                out.write((int) ((value >> (i * 8)) & 0xFF));
            }
            return this;
        }

        public Writer writeFloat(float value) {
            return writeInt(Float.floatToIntBits(value));
        }

        public Writer writeDouble(double value) {
            return writeLong(Double.doubleToLongBits(value));
        }

        public Writer writeVarInt(int value) {
            out.write(VarInt.encode(value), 0, VarInt.encode(value).length);
            return this;
        }

        public Writer writeVarLong(long value) {
            long remaining = value;
            while ((remaining & ~0x7FL) != 0) {
                out.write((int) ((remaining & 0x7F) | 0x80));
                remaining >>>= 7;
            }
            out.write((int) remaining);
            return this;
        }

        /** Minecraft の文字列（VarInt 長＋UTF-8）。 */
        public Writer writeString(String value) {
            byte[] utf8 = value.getBytes(StandardCharsets.UTF_8);
            writeVarInt(utf8.length);
            out.write(utf8, 0, utf8.length);
            return this;
        }

        /** VarInt 長＋バイト列。 */
        public Writer writeByteArray(byte[] value) {
            writeVarInt(value.length);
            out.write(value, 0, value.length);
            return this;
        }

        public Writer writeBytes(byte[] value) {
            out.write(value, 0, value.length);
            return this;
        }

        public Writer writeUuid(UUID uuid) {
            writeLong(uuid.getMostSignificantBits());
            writeLong(uuid.getLeastSignificantBits());
            return this;
        }

        /** Position の生 i64 をそのまま書く。 */
        public Writer writePositionRaw(long packed) {
            return writeLong(packed);
        }

        /** 名前付き NBT を書く。 */
        public Writer writeNamedNbt(String name, Nbt.Tag tag) {
            return writeBytes(Nbt.writeNamed(name, tag));
        }

        /** 名前なし NBT を書く。 */
        public Writer writeAnonymousNbt(Nbt.Tag tag) {
            return writeBytes(Nbt.writeAnonymous(tag));
        }
    }

    /** 文字列のリスト（VarInt 個数＋文字列）を1個読む。 */
    public static List<String> readStringList(Reader reader) {
        int count = reader.readVarInt();
        if (count < 0) {
            throw new IllegalArgumentException("文字列リストの個数が負です: " + count);
        }
        List<String> values = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            values.add(reader.readString());
        }
        return values;
    }

    /** 文字列のリストを書く。 */
    public static void writeStringList(Writer writer, List<String> values) {
        writer.writeVarInt(values.size());
        for (String value : values) {
            writer.writeString(value);
        }
    }
}
