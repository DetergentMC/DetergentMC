package io.detergentmc.protocol;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Minecraftのパケット1個（ID＋ペイロードのバイト列）と、
 * 長さプレフィックス付きフレームへの変換。
 *
 * <p>ワイヤ上の形は wiki.vg の Protocol（Packet format）による：
 * <pre>
 *   [ 全体の長さ: VarInt ][ パケットID: VarInt ][ ペイロード ]
 * </pre>
 * 「全体の長さ」はパケットIDのバイト数＋ペイロードのバイト数。
 * このクラスはペイロードの中身を解釈しない。IDの付け替えと
 * ペイロードの素通しだけが仕事で、中身の変換はプロキシ担当の範囲。
 *
 * <p>TCPはストリームなので、1回の読み取りに複数パケットが混ざったり、
 * 1パケットが複数回に分かれて届いたりする。その両方に対応するため、
 * 一括変換（{@link #encode()}／{@link #decodeAll(byte[])}）に加えて、
 * 少しずつ流し込める {@link Decoder} を用意してある。
 * 圧縮（Set Compression）と暗号化はこの層の外側（Nettyパイプライン）で
 * 行うものとし、ここでは扱わない。
 */
public record MinecraftPacket(int id, byte[] payload) {

    /** バニラが受け付けるパケットの最大長（バイト）。長さがこれを超えるフレームは不正とする。 */
    public static final int MAX_FRAME_LENGTH = 2_097_152;

    public MinecraftPacket {
        if (id < 0) {
            throw new IllegalArgumentException("パケットIDは0以上でなければなりません: " + id);
        }
        if (payload == null) {
            throw new NullPointerException("payloadはnullにできません。空の場合は長さ0の配列を使ってください。");
        }
        payload = payload.clone();
    }

    /** ペイロードの防御コピーを返す。 */
    @Override
    public byte[] payload() {
        return payload.clone();
    }

    /**
     * レコードの自動生成される equals は配列を参照で比べるため、
     * ペイロードの中身で比べるよう上書きする。
     */
    @Override
    public boolean equals(Object other) {
        return other instanceof MinecraftPacket that
                && id == that.id
                && Arrays.equals(payload, that.payload);
    }

    @Override
    public int hashCode() {
        return 31 * Integer.hashCode(id) + Arrays.hashCode(payload);
    }

    /**
     * 長さプレフィックス付きのフレーム1個に変換する。
     * 返されたバイト列をそのままソケットへ書ける。
     */
    public byte[] encode() {
        byte[] idBytes = VarInt.encode(id);
        int length = idBytes.length + payload.length;
        byte[] lengthBytes = VarInt.encode(length);
        byte[] frame = new byte[lengthBytes.length + length];
        System.arraycopy(lengthBytes, 0, frame, 0, lengthBytes.length);
        System.arraycopy(idBytes, 0, frame, lengthBytes.length, idBytes.length);
        System.arraycopy(payload, 0, frame, lengthBytes.length + idBytes.length, payload.length);
        return frame;
    }

    /** フレーム1個の読み取り結果（パケットと消費したバイト数）。 */
    public record Decoded(MinecraftPacket packet, int bytesConsumed) {
    }

    /**
     * {@code bytes} の {@code offset} からフレームを1個読む。
     *
     * @throws IllegalArgumentException バイト列が途中で切れている場合、
     *         長さが {@link #MAX_FRAME_LENGTH} を超える場合、
     *         または長さ・IDのVarIntが不正な場合
     */
    public static Decoded decodeOne(byte[] bytes, int offset) {
        VarInt.ReadResult lengthResult = VarInt.decode(bytes, offset);
        int length = lengthResult.value();
        if (length < 0 || length > MAX_FRAME_LENGTH) {
            throw new IllegalArgumentException("パケットの長さが不正です: " + length);
        }
        int cursor = offset + lengthResult.bytesRead();
        if (bytes.length - cursor < length) {
            throw new IllegalArgumentException(
                    "フレームが途中で切れています（必要=" + length
                            + "バイト、残り=" + (bytes.length - cursor) + "バイト）。");
        }
        VarInt.ReadResult idResult = VarInt.decode(bytes, cursor);
        int id = idResult.value();
        int payloadLength = length - idResult.bytesRead();
        if (payloadLength < 0) {
            throw new IllegalArgumentException(
                    "パケットの長さがIDのバイト数より短いです: length=" + length);
        }
        byte[] payload = Arrays.copyOfRange(
                bytes, cursor + idResult.bytesRead(), cursor + length);
        return new Decoded(new MinecraftPacket(id, payload),
                lengthResult.bytesRead() + length);
    }

    /**
     * 連結されたフレームをすべて読む。末尾に不完全なフレームが残っている
     * 場合は {@link IllegalArgumentException} になる（分割読み取りには
     * {@link Decoder} を使うこと）。
     */
    public static List<MinecraftPacket> decodeAll(byte[] bytes) {
        List<MinecraftPacket> packets = new ArrayList<>();
        int offset = 0;
        while (offset < bytes.length) {
            Decoded decoded = decodeOne(bytes, offset);
            packets.add(decoded.packet());
            offset += decoded.bytesConsumed();
        }
        return List.copyOf(packets);
    }

    /**
     * 分割して届くバイト列からフレームを少しずつ取り出すデコーダ。
     *
     * <p>使い方：ソケットから読んだバイト列を {@link #feed(byte[])} に
     * 渡すたびに、その時点で完成したパケットの一覧が返る。未完成の
     * 断片は内部に保持され、続きが届いたときに完成する。スレッドセーフ
     * ではないので、1接続につき1インスタンスを1スレッドから使うこと。
     */
    public static final class Decoder {

        private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();

        /**
         * バイト列を流し込み、この時点で完成したパケットを返す。
         * 完成しなかった断片は内部に残る。
         *
         * @throws IllegalArgumentException 完成したフレームの長さが不正な場合
         */
        public List<MinecraftPacket> feed(byte[] chunk) {
            return feed(chunk, 0, chunk.length);
        }

        /** {@link #feed(byte[])} の範囲指定版。 */
        public List<MinecraftPacket> feed(byte[] chunk, int offset, int length) {
            buffer.write(chunk, offset, length);
            byte[] bytes = buffer.toByteArray();
            List<MinecraftPacket> packets = new ArrayList<>();
            int cursor = 0;
            while (cursor < bytes.length) {
                VarInt.ReadResult lengthResult;
                try {
                    lengthResult = VarInt.decode(bytes, cursor);
                } catch (IllegalArgumentException invalid) {
                    // 長さのVarInt自体がまだ届き切っていない（残りがすべて
                    // 継続ビット付きで5バイト未満）場合は続きを待つ。
                    // それ以外は本物の不正値なので投げ直す。
                    if (isTruncatedVarInt(bytes, cursor)) {
                        break;
                    }
                    throw invalid;
                }
                int frameLength = lengthResult.value();
                if (frameLength < 0 || frameLength > MAX_FRAME_LENGTH) {
                    throw new IllegalArgumentException(
                            "パケットの長さが不正です: " + frameLength);
                }
                int frameTotal = lengthResult.bytesRead() + frameLength;
                if (bytes.length - cursor < frameTotal) {
                    break; // フレームの残りがまだ届いていないので待つ。
                }
                Decoded decoded = decodeOne(bytes, cursor);
                packets.add(decoded.packet());
                cursor += decoded.bytesConsumed();
            }
            buffer.reset();
            if (cursor < bytes.length) {
                buffer.write(bytes, cursor, bytes.length - cursor);
            }
            return List.copyOf(packets);
        }

        /** 内部に残っている未完成のバイト数。 */
        public int bufferedBytes() {
            return buffer.size();
        }

        /**
         * 内部に残っている未完成のバイト列を取り出す。取り出した分は
         * 内部から消える。リレーモードへ切り替えるときに使う：デコーダが
         * 抱えている断片は次のパケットの先頭なので、生バイトとして
         * そのまま相手へ送らなければならない。
         */
        public byte[] takeBuffered() {
            byte[] bytes = buffer.toByteArray();
            buffer.reset();
            return bytes;
        }

        /**
         * {@code offset} から先が、まだ届き切っていないVarIntの断片か。
         * 残りのバイトがすべて継続ビット付きで5バイト未満なら断片とみなす。
         */
        private static boolean isTruncatedVarInt(byte[] bytes, int offset) {
            int remaining = bytes.length - offset;
            if (remaining <= 0 || remaining >= VarInt.MAX_BYTES) {
                return false;
            }
            for (int i = offset; i < bytes.length; i++) {
                if ((bytes[i] & 0x80) == 0) {
                    return false;
                }
            }
            return true;
        }
    }
}
