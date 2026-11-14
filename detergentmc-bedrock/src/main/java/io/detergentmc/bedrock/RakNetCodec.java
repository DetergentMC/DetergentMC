package io.detergentmc.bedrock;

import io.detergentmc.bedrock.BedrockVarInt.ByteReader;
import io.detergentmc.bedrock.BedrockVarInt.ByteWriter;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * RakNet（UDP）層のバイト列の組み立てと分解。
 *
 * <p>ここで扱うのは「信頼性なしのオフライン・ハンドシェイク」と「フレーム
 * セットの枠」だけで、ゲームの中身（Login など）は
 * {@link BedrockBatch}／{@link BedrockPackets} が扱う。形式は
 * wiki.bedrock.dev「RakNet Protocol」と Minecraft Wiki「RakNet」による。
 *
 * <ul>
 *   <li>Unconnected Ping（0x01）：時刻 i64 BE＋Magic＋クライアント GUID i64 BE。
 *       Pong（0x1c）：Ping の時刻＋サーバー GUID＋Magic＋MOTD 文字列
 *       （u16 BE 長＋UTF-8）。</li>
 *   <li>Open Connection Request 1（0x05）：Magic＋RakNet プロトコル 11＋
 *       ゼロ詰め（MTU 探り）。Reply 1（0x06）：Magic＋サーバー GUID＋
 *       セキュリティ有無（false）＋MTU u16 BE。MTU は要求の長さから
 *       {@code 長さ＋28}（28 は IP＋UDP のオーバーヘッド）で決め、
 *       576〜1492 に収める。</li>
 *   <li>Open Connection Request 2（0x07）：Magic＋サーバー・アドレス＋
 *       MTU u16 BE＋クライアント GUID。Reply 2（0x08）：Magic＋サーバー GUID＋
 *       クライアント・アドレス＋MTU u16 BE＋セキュリティ false。</li>
 *   <li>フレームセット（0x84）：フラグ 1 バイト＋シーケンス番号 u24 LE＋
 *       フレーム列。フレームは「信頼性フラグ 1 バイト＋長さ u16 BE（ビット数）＋
 *       信頼性ごとの付加フィールド＋ペイロード」。信頼性は 0=unreliable、
 *       2=reliable、3=reliable ordered、4=reliable sequenced を読む。
 *       分割（split）フラグが立っているフレームは splitId ごとに集めて
 *       index 順に繋ぎ直す（Login はスキン込みで 100KB 近くになるため、
 *       分割なしでは届かない）。</li>
 *   <li>ACK（0xc0）：届いたフレームセットのシーケンス番号を返す。
 *       NACK（0xa0）は受け取るだけで再送はしない（未実装。制限一覧を参照）。</li>
 *   <li>ソケット・アドレスは 7 バイト（バージョン 0x04＋IPv4 4 バイト＋
 *       ポート u16 BE）。RakNet の慣習どおり IP の各オクテットは
 *       ビット反転（{@code ~octet}）して書く。go-raknet など他の実装も
 *       同じ慣習で、クライアントはこのアドレスを接続の判定には使わない
 *       （情報表示用）ため、実機での厳密な検証はまだできていない。
 *       くわしくは docs/BEDROCK.md の制限一覧を参照。</li>
 * </ul>
 */
public final class RakNetCodec {

    private RakNetCodec() {
    }

    /** Unconnected Ping を読む。形式が違えば null。 */
    public static Ping parseUnconnectedPing(byte[] datagram) {
        if (datagram == null || datagram.length < 1 + 8 + 16 + 8
                || datagram[0] != (byte) BedrockProtocol.ID_UNCONNECTED_PING) {
            return null;
        }
        ByteReader in = new ByteReader(datagram, 1);
        long time = in.readBigLong();
        byte[] magic = in.readBytes(16);
        if (!Arrays.equals(magic, BedrockProtocol.RAKNET_MAGIC)) {
            return null;
        }
        long clientGuid = in.readBigLong();
        return new Ping(time, clientGuid);
    }

    /** Unconnected Ping の内容（時刻とクライアント GUID）。 */
    public record Ping(long time, long clientGuid) {
    }

    /** Unconnected Pong を作る。 */
    public static byte[] buildUnconnectedPong(long pingTime, long serverGuid, String motd) {
        ByteWriter out = new ByteWriter();
        out.writeByte(BedrockProtocol.ID_UNCONNECTED_PONG);
        out.writeBigLong(pingTime);
        out.writeBigLong(serverGuid);
        out.writeBytes(BedrockProtocol.RAKNET_MAGIC);
        out.writeBytes(BedrockMotd.encodeMotdField(motd));
        return out.bytes();
    }

    /** Open Connection Request 1 を読む。形式が違えば null。 */
    public static OpenConnectionRequest1 parseOpenConnectionRequest1(byte[] datagram) {
        if (datagram == null || datagram.length < 1 + 16 + 1
                || datagram[0] != (byte) BedrockProtocol.ID_OPEN_CONNECTION_REQUEST_1) {
            return null;
        }
        ByteReader in = new ByteReader(datagram, 1);
        byte[] magic = in.readBytes(16);
        if (!Arrays.equals(magic, BedrockProtocol.RAKNET_MAGIC)) {
            return null;
        }
        int raknetProtocol = in.readUnsignedByte();
        return new OpenConnectionRequest1(raknetProtocol, datagram.length);
    }

    /** Open Connection Request 1 の内容。 */
    public record OpenConnectionRequest1(int raknetProtocol, int datagramLength) {
    }

    /** Open Connection Reply 1 を作る。 */
    public static byte[] buildOpenConnectionReply1(long serverGuid, int mtu) {
        ByteWriter out = new ByteWriter();
        out.writeByte(BedrockProtocol.ID_OPEN_CONNECTION_REPLY_1);
        out.writeBytes(BedrockProtocol.RAKNET_MAGIC);
        out.writeBigLong(serverGuid);
        out.writeBoolean(false); // セキュリティなし
        out.writeByte((mtu >> 8) & 0xFF);
        out.writeByte(mtu & 0xFF);
        return out.bytes();
    }

    /**
     * Request 1 の長さから MTU を決める（長さ＋28、576〜1492 に収める）。
     * 28 は IP＋UDP ヘッダのオーバーヘッド分で、wiki.vg の RakNet の
     * 「padding＋46」相当の計算である。
     */
    public static int mtuForRequestLength(int datagramLength) {
        return Math.min(1492, Math.max(576, datagramLength + 28));
    }

    /** Incompatible Protocol（0x19）を作る。RakNet バージョンが 11 以外のとき送る。 */
    public static byte[] buildIncompatibleProtocol(long serverGuid) {
        ByteWriter out = new ByteWriter();
        out.writeByte(BedrockProtocol.ID_INCOMPATIBLE_PROTOCOL);
        out.writeByte(BedrockProtocol.RAKNET_PROTOCOL_VERSION);
        out.writeBytes(BedrockProtocol.RAKNET_MAGIC);
        out.writeBigLong(serverGuid);
        return out.bytes();
    }

    /** Open Connection Request 2 を読む。形式が違えば null。 */
    public static OpenConnectionRequest2 parseOpenConnectionRequest2(byte[] datagram) {
        if (datagram == null || datagram.length < 1 + 16 + 7 + 2 + 8
                || datagram[0] != (byte) BedrockProtocol.ID_OPEN_CONNECTION_REQUEST_2) {
            return null;
        }
        ByteReader in = new ByteReader(datagram, 1);
        byte[] magic = in.readBytes(16);
        if (!Arrays.equals(magic, BedrockProtocol.RAKNET_MAGIC)) {
            return null;
        }
        byte[] addressBytes = in.readBytes(7); // サーバー・アドレス
        int mtu = in.readBigUnsignedShort();
        long clientGuid = in.readBigLong();
        return new OpenConnectionRequest2(addressBytes, mtu, clientGuid);
    }

    /** Open Connection Request 2 の内容。 */
    public record OpenConnectionRequest2(byte[] serverAddressBytes, int mtu, long clientGuid) {
    }

    /** Open Connection Reply 2 を作る。 */
    public static byte[] buildOpenConnectionReply2(long serverGuid,
            byte[] clientAddressBytes, int mtu) {
        ByteWriter out = new ByteWriter();
        out.writeByte(BedrockProtocol.ID_OPEN_CONNECTION_REPLY_2);
        out.writeBytes(BedrockProtocol.RAKNET_MAGIC);
        out.writeBigLong(serverGuid);
        out.writeBytes(clientAddressBytes);
        out.writeByte((mtu >> 8) & 0xFF);
        out.writeByte(mtu & 0xFF);
        out.writeBoolean(false); // セキュリティなし
        return out.bytes();
    }

    /**
     * ソケット・アドレスを 7 バイトに符号化する（バージョン 0x04＋IPv4＋
     * ポート u16 BE）。RakNet の慣習どおり IP の各オクテットはビット反転する。
     */
    public static byte[] encodeAddress(InetSocketAddress address) {
        ByteWriter out = new ByteWriter();
        out.writeByte(4);
        byte[] ip = address.getAddress().getAddress();
        for (int i = 0; i < 4; i++) {
            out.writeByte(~ip[i]);
        }
        int port = address.getPort();
        out.writeByte((port >> 8) & 0xFF);
        out.writeByte(port & 0xFF);
        return out.bytes();
    }

    /** 7 バイトのソケット・アドレスを読む（ビット反転を戻す）。 */
    public static InetSocketAddress decodeAddress(byte[] bytes, int offset) {
        if (bytes == null || bytes.length - offset < 7 || bytes[offset] != 4) {
            return null;
        }
        byte[] ip = new byte[4];
        for (int i = 0; i < 4; i++) {
            ip[i] = (byte) ~bytes[offset + 1 + i];
        }
        int port = ((bytes[offset + 5] & 0xFF) << 8) | (bytes[offset + 6] & 0xFF);
        try {
            return new InetSocketAddress(java.net.InetAddress.getByAddress(ip), port);
        } catch (java.net.UnknownHostException e) {
            return null;
        }
    }

    // ---- フレームセット ----

    /** 信頼性：unreliable。 */
    public static final int RELIABILITY_UNRELIABLE = 0;
    /** 信頼性：reliable。 */
    public static final int RELIABILITY_RELIABLE = 2;
    /** 信頼性：reliable ordered。 */
    public static final int RELIABILITY_RELIABLE_ORDERED = 3;
    /** 信頼性：reliable sequenced。 */
    public static final int RELIABILITY_RELIABLE_SEQUENCED = 4;

    /** フレームセット 1 個を「信頼性つきペイロード」に分解する。 */
    public record Frame(int reliability, byte[] payload) {
    }

    /** フレームセットの分解結果。 */
    public record FrameSet(int sequenceNumber, List<Frame> frames) {
    }

    /** フレームセット（0x80〜0x8f）かどうか。 */
    public static boolean isFrameSet(byte[] datagram) {
        return datagram != null && datagram.length >= 4
                && (datagram[0] & 0xF0) == 0x80;
    }

    /** フレームセットを分解する（分割フレームはここでは繋がない）。 */
    public static FrameSet parseFrameSet(byte[] datagram) {
        if (!isFrameSet(datagram)) {
            throw new IllegalArgumentException("フレームセットではありません。");
        }
        ByteReader in = new ByteReader(datagram, 1);
        int sequence = (in.readUnsignedByte())
                | (in.readUnsignedByte() << 8)
                | (in.readUnsignedByte() << 16);
        List<Frame> frames = new ArrayList<>();
        while (in.hasRemaining()) {
            int flags = in.readUnsignedByte();
            int reliability = (flags >> 5) & 0x07;
            boolean split = (flags & 0x10) != 0;
            int lengthBits = in.readBigUnsignedShort();
            int lengthBytes = (lengthBits + 7) / 8;
            // 信頼性ごとの付加フィールドを読み飛ばす。
            switch (reliability) {
                case RELIABILITY_UNRELIABLE -> { /* なし */ }
                case 1 -> in.readBytes(3); // unreliable sequenced：順序 index 3 バイト
                case RELIABILITY_RELIABLE -> in.readBytes(3); // message index
                case RELIABILITY_RELIABLE_ORDERED, RELIABILITY_RELIABLE_SEQUENCED -> {
                    in.readBytes(3); // message index
                    in.readUnsignedByte(); // ordering channel
                    in.readBytes(3); // ordering index
                }
                case 5 -> in.readBytes(3); // unreliable with ACK receipt
                case 6, 7 -> {
                    in.readBytes(3);
                    if (reliability == 7) {
                        in.readUnsignedByte();
                        in.readBytes(3);
                    }
                }
                default -> throw new IllegalArgumentException(
                        "未知の信頼性です: " + reliability);
            }
            if (split) {
                // split count（BE int32）、split id（BE int16）、split index（BE int32）
                in.readBytes(4 + 2 + 4);
                // 分割されたペイロードは「このフレームの一部」であり、ここでは
                // そのまま 1 フレームとして返す。繋ぎ直しは SplitReassembler が行う。
                // ただし split 情報を失わないよう、ペイロードの先頭に目印は付けない。
                // 呼び出し側は parseFrameSetWithSplit を使うこと。
                throw new IllegalArgumentException(
                        "分割フレームは parseFrameSetWithSplit で読んでください。");
            }
            frames.add(new Frame(reliability, in.readBytes(lengthBytes)));
        }
        return new FrameSet(sequence, frames);
    }

    /** 分割フレームを含むフレームセットを分解し、分割を繋ぎ直したペイロード列を返す。 */
    public static List<byte[]> parseFrameSetPayloads(byte[] datagram, SplitReassembler splits) {
        if (!isFrameSet(datagram)) {
            throw new IllegalArgumentException("フレームセットではありません。");
        }
        ByteReader in = new ByteReader(datagram, 1);
        int sequence = (in.readUnsignedByte())
                | (in.readUnsignedByte() << 8)
                | (in.readUnsignedByte() << 16);
        List<byte[]> payloads = new ArrayList<>();
        while (in.hasRemaining()) {
            int flags = in.readUnsignedByte();
            int reliability = (flags >> 5) & 0x07;
            boolean split = (flags & 0x10) != 0;
            int lengthBits = in.readBigUnsignedShort();
            int lengthBytes = (lengthBits + 7) / 8;
            switch (reliability) {
                case RELIABILITY_UNRELIABLE -> { /* なし */ }
                case 1, RELIABILITY_RELIABLE, 5, 6 -> in.readBytes(3);
                case RELIABILITY_RELIABLE_ORDERED, RELIABILITY_RELIABLE_SEQUENCED, 7 -> {
                    in.readBytes(3);
                    in.readUnsignedByte();
                    in.readBytes(3);
                }
                default -> throw new IllegalArgumentException(
                        "未知の信頼性です: " + reliability);
            }
            int splitCount = -1;
            int splitId = -1;
            int splitIndex = -1;
            if (split) {
                splitCount = in.readBigInt();
                splitId = in.readBigUnsignedShort();
                splitIndex = in.readBigInt();
            }
            byte[] payload = in.readBytes(lengthBytes);
            if (split) {
                byte[] whole = splits.addFragment(splitId, splitCount, splitIndex, payload);
                if (whole != null) {
                    payloads.add(whole);
                }
            } else {
                payloads.add(payload);
            }
        }
        // シーケンス番号は呼び出し側（ACK 送信用）が別途読む。
        splits.lastSequenceNumber = sequence;
        return payloads;
    }

    /** フレームセットのシーケンス番号だけ読む（ACK 送信用）。 */
    public static int frameSetSequenceNumber(byte[] datagram) {
        if (!isFrameSet(datagram)) {
            return -1;
        }
        return (datagram[1] & 0xFF)
                | ((datagram[2] & 0xFF) << 8)
                | ((datagram[3] & 0xFF) << 16);
    }

    /** 1 個のフレーム（reliable）を含むフレームセットを作る。 */
    public static byte[] buildFrameSet(int sequenceNumber, int reliability,
            int messageIndex, byte[] payload) {
        ByteWriter out = new ByteWriter();
        out.writeByte(BedrockProtocol.ID_FRAME_SET);
        out.writeByte(sequenceNumber);
        out.writeByte(sequenceNumber >> 8);
        out.writeByte(sequenceNumber >> 16);
        int flags = (reliability << 5) & 0xE0;
        out.writeByte(flags);
        int lengthBits = payload.length * 8;
        out.writeByte((lengthBits >> 8) & 0xFF);
        out.writeByte(lengthBits & 0xFF);
        if (reliability == RELIABILITY_RELIABLE) {
            out.writeByte(messageIndex);
            out.writeByte(messageIndex >> 8);
            out.writeByte(messageIndex >> 16);
        } else if (reliability == RELIABILITY_RELIABLE_ORDERED
                || reliability == RELIABILITY_RELIABLE_SEQUENCED) {
            out.writeByte(messageIndex);
            out.writeByte(messageIndex >> 8);
            out.writeByte(messageIndex >> 16);
            out.writeByte(0); // ordering channel
            out.writeByte(0);
            out.writeByte(0);
            out.writeByte(0);
        }
        out.writeBytes(payload);
        return out.bytes();
    }

    /** 1 個の分割フレームを含むフレームセットを作る。 */
    public static byte[] buildFrameSetSplit(int sequenceNumber, int reliability,
            int messageIndex, byte[] payload,
            int splitCount, int splitId, int splitIndex) {
        ByteWriter out = new ByteWriter();
        out.writeByte(BedrockProtocol.ID_FRAME_SET);
        out.writeByte(sequenceNumber);
        out.writeByte(sequenceNumber >> 8);
        out.writeByte(sequenceNumber >> 16);
        int flags = ((reliability << 5) & 0xE0) | 0x10;
        out.writeByte(flags);
        int lengthBits = payload.length * 8;
        out.writeByte((lengthBits >> 8) & 0xFF);
        out.writeByte(lengthBits & 0xFF);
        if (reliability == RELIABILITY_RELIABLE) {
            out.writeByte(messageIndex);
            out.writeByte(messageIndex >> 8);
            out.writeByte(messageIndex >> 16);
        } else if (reliability == RELIABILITY_RELIABLE_ORDERED
                || reliability == RELIABILITY_RELIABLE_SEQUENCED) {
            out.writeByte(messageIndex);
            out.writeByte(messageIndex >> 8);
            out.writeByte(messageIndex >> 16);
            out.writeByte(0); // ordering channel
            out.writeByte(0);
            out.writeByte(0);
            out.writeByte(0);
        }
        out.writeByte(splitCount >> 24);
        out.writeByte(splitCount >> 16);
        out.writeByte(splitCount >> 8);
        out.writeByte(splitCount);
        out.writeByte(splitId >> 8);
        out.writeByte(splitId);
        out.writeByte(splitIndex >> 24);
        out.writeByte(splitIndex >> 16);
        out.writeByte(splitIndex >> 8);
        out.writeByte(splitIndex);
        out.writeBytes(payload);
        return out.bytes();
    }

    /**
     * ペイロードを MTU に収まるフレームセットの列にわける。収まる場合は
     * 1 個だけ返す。分割する場合は 1 断片＝1 フレームセットで、同じ
     * splitId を振る。断片は {@code mtu - 64} バイト以下にする
     * （フレームセット／フレーム／分割ヘッダの余裕）。
     */
    public static List<byte[]> buildFrameSets(int firstSequenceNumber, int reliability,
            int firstMessageIndex, byte[] payload, int mtu, int splitId) {
        int fragmentMax = Math.max(1, mtu - 64);
        List<byte[]> datagrams = new ArrayList<>();
        if (payload.length + 32 <= mtu) {
            datagrams.add(buildFrameSet(firstSequenceNumber, reliability,
                    firstMessageIndex, payload));
            return datagrams;
        }
        int splitCount = (payload.length + fragmentMax - 1) / fragmentMax;
        for (int i = 0; i < splitCount; i++) {
            int start = i * fragmentMax;
            int end = Math.min(payload.length, start + fragmentMax);
            byte[] fragment = new byte[end - start];
            System.arraycopy(payload, start, fragment, 0, fragment.length);
            datagrams.add(buildFrameSetSplit(firstSequenceNumber + i, reliability,
                    firstMessageIndex + i, fragment, splitCount, splitId, i));
        }
        return datagrams;
    }

    /** ACK（0xc0）を作る。シーケンス番号は単独レコードで並べる。 */
    public static byte[] buildAck(int... sequenceNumbers) {
        ByteWriter out = new ByteWriter();
        out.writeByte(BedrockProtocol.ID_ACK);
        out.writeByte((sequenceNumbers.length >> 8) & 0xFF);
        out.writeByte(sequenceNumbers.length & 0xFF);
        for (int seq : sequenceNumbers) {
            out.writeByte(0); // 単独レコード（範囲ではない）
            out.writeByte(seq);
            out.writeByte(seq >> 8);
            out.writeByte(seq >> 16);
        }
        return out.bytes();
    }

    /** Connected Pong（0x03）を作る。 */
    public static byte[] buildConnectedPong(long pingTime, long pongTime) {
        ByteWriter out = new ByteWriter();
        out.writeByte(BedrockProtocol.ID_CONNECTED_PONG);
        out.writeBigLong(pingTime);
        out.writeBigLong(pongTime);
        return out.bytes();
    }

    /** Connection Request Accepted（0x10）を作る。 */
    public static byte[] buildConnectionRequestAccepted(
            InetSocketAddress clientAddress, long pingTime, long pongTime) {
        ByteWriter out = new ByteWriter();
        out.writeByte(BedrockProtocol.ID_CONNECTION_REQUEST_ACCEPTED);
        out.writeBytes(encodeAddress(clientAddress));
        // system index（short BE。0 でよい）
        out.writeByte(0);
        out.writeByte(0);
        // system addresses：20 個の空アドレス（バージョン 0x04＋0.0.0.0:0 を
        // 20 個。クライアントはここを使わない）。
        for (int i = 0; i < 20; i++) {
            out.writeByte(4);
            out.writeByte(0xFF);
            out.writeByte(0xFF);
            out.writeByte(0xFF);
            out.writeByte(0xFF);
            out.writeByte(0);
            out.writeByte(0);
        }
        out.writeBigLong(pingTime);
        out.writeBigLong(pongTime);
        return out.bytes();
    }

    /** 分割フレームの繋ぎ直し。 */
    public static final class SplitReassembler {
        private final Map<Integer, byte[][]> buffers = new HashMap<>();
        private final Map<Integer, Integer> counts = new HashMap<>();
        private final Map<Integer, Integer> received = new HashMap<>();
        /** 最後に読んだフレームセットのシーケンス番号（ACK 送信用の記録）。 */
        int lastSequenceNumber = -1;

        /**
         * 断片を1個追加する。全部揃ったら繋いだ全体を返し、まだなら null。
         * splitCount が 512 を超えるものは捨てる（異常な分割への防御）。
         */
        public byte[] addFragment(int splitId, int splitCount, int splitIndex, byte[] payload) {
            if (splitCount <= 0 || splitCount > 512
                    || splitIndex < 0 || splitIndex >= splitCount) {
                return null;
            }
            byte[][] parts = buffers.computeIfAbsent(splitId, k -> new byte[splitCount][]);
            if (parts.length != splitCount) {
                buffers.put(splitId, parts = new byte[splitCount][]);
            }
            if (parts[splitIndex] == null) {
                parts[splitIndex] = payload;
                received.merge(splitId, 1, Integer::sum);
            }
            counts.put(splitId, splitCount);
            if (received.getOrDefault(splitId, 0) >= splitCount) {
                int total = 0;
                for (byte[] part : parts) {
                    total += part.length;
                }
                byte[] whole = new byte[total];
                int offset = 0;
                for (byte[] part : parts) {
                    System.arraycopy(part, 0, whole, offset, part.length);
                    offset += part.length;
                }
                buffers.remove(splitId);
                counts.remove(splitId);
                received.remove(splitId);
                return whole;
            }
            return null;
        }
    }
}
