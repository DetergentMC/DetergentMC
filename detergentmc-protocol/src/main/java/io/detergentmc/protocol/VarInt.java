package io.detergentmc.protocol;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * MinecraftのVarInt（可変長整数）の読み書き。
 *
 * <p>仕様は wiki.vg の Protocol（Data types / VarInt and VarLong）による。
 * 7ビットずつリトルエンディアン風に並べ、最上位ビットが「続きがある」
 * 合図になる。32ビット符号付き整数なので最大5バイト。
 * 負の数は5バイトで符号拡張される（例：-1 は FF FF FF FF 0F）。
 *
 * <p>このクラスはバイト列の変換だけを行う。パケットの長さプレフィックス
 * やIDの読み書きは {@link MinecraftPacket} がこのクラスを使う。
 */
public final class VarInt {

    /** VarIntが取り得る最大のバイト数（32ビット整数は5バイトまで）。 */
    public static final int MAX_BYTES = 5;

    private VarInt() {
    }

    /** この値をVarIntで表すのに必要なバイト数。 */
    public static int encodedSize(int value) {
        int size = 1;
        while ((value & ~0x7F) != 0) {
            value >>>= 7;
            size++;
        }
        return size;
    }

    /** 値をVarIntのバイト列に変換する。 */
    public static byte[] encode(int value) {
        byte[] out = new byte[encodedSize(value)];
        int i = 0;
        while ((value & ~0x7F) != 0) {
            out[i++] = (byte) ((value & 0x7F) | 0x80);
            value >>>= 7;
        }
        out[i] = (byte) value;
        return out;
    }

    /**
     * 値をVarIntとしてストリームへ書く。
     *
     * @throws IOException 書き込みに失敗した場合
     */
    public static void writeTo(int value, OutputStream out) throws IOException {
        out.write(encode(value));
    }

    /**
     * バイト列の {@code offset} からVarIntを1つ読む。
     *
     * @return 読んだ値と消費したバイト数
     * @throws IllegalArgumentException バイト列が途中で切れている場合、
     *         または6バイト目以降にも続きがある場合（不正なVarInt）
     */
    public static ReadResult decode(byte[] bytes, int offset) {
        int value = 0;
        int position = 0;
        while (true) {
            if (offset + position >= bytes.length) {
                throw new IllegalArgumentException(
                        "VarIntが途中で切れています（offset=" + offset + "）。");
            }
            byte current = bytes[offset + position];
            value |= (current & 0x7F) << (7 * position);
            position++;
            if (position > MAX_BYTES) {
                throw new IllegalArgumentException(
                        "VarIntが長すぎます（最大" + MAX_BYTES + "バイト）。");
            }
            if ((current & 0x80) == 0) {
                return new ReadResult(value, position);
            }
        }
    }

    /** {@link #decode(byte[], int)} の戻り値。 */
    public record ReadResult(int value, int bytesRead) {
    }

    /**
     * ストリームからVarIntを1つ読む。
     *
     * @throws EOFException ストリームが途中で終わった場合
     * @throws IllegalArgumentException 6バイト目以降にも続きがある場合
     * @throws IOException 読み取りに失敗した場合
     */
    public static int readFrom(InputStream in) throws IOException {
        int value = 0;
        int position = 0;
        while (true) {
            int current = in.read();
            if (current < 0) {
                throw new EOFException("VarIntの読み取り中にストリームが終わりました。");
            }
            value |= (current & 0x7F) << (7 * position);
            position++;
            if (position > MAX_BYTES) {
                throw new IllegalArgumentException(
                        "VarIntが長すぎます（最大" + MAX_BYTES + "バイト）。");
            }
            if ((current & 0x80) == 0) {
                return value;
            }
        }
    }
}
