package io.detergentmc.bedrock;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.detergentmc.bedrock.BedrockVarInt.ByteWriter;
import java.net.InetSocketAddress;
import java.util.List;
import org.junit.jupiter.api.Test;

/** RakNet オフライン・ハンドシェイクとフレームセットのテスト。 */
class RakNetCodecTest {

    @Test
    void unconnectedPingPongRoundTrip() {
        ByteWriter ping = new ByteWriter();
        ping.writeByte(BedrockProtocol.ID_UNCONNECTED_PING);
        ping.writeBigLong(123456789L);
        ping.writeBytes(BedrockProtocol.RAKNET_MAGIC);
        ping.writeBigLong(-987654321L);
        RakNetCodec.Ping parsed = RakNetCodec.parseUnconnectedPing(ping.bytes());
        assertNotNull(parsed);
        assertEquals(123456789L, parsed.time());
        assertEquals(-987654321L, parsed.clientGuid());

        byte[] pong = RakNetCodec.buildUnconnectedPong(
                123456789L, 555L, "MCPE;DetergentMC;630;1.20.50;0;20;555;DetergentMC;Survival;1;19132;19133;");
        assertEquals((byte) BedrockProtocol.ID_UNCONNECTED_PONG, pong[0]);
        // 時刻・サーバー GUID・Magic が Pong に入っていること。
        BedrockVarInt.ByteReader in = new BedrockVarInt.ByteReader(pong, 1);
        assertEquals(123456789L, in.readBigLong());
        assertEquals(555L, in.readBigLong());
        assertArrayEquals(BedrockProtocol.RAKNET_MAGIC, in.readBytes(16));
        int motdLength = in.readBigUnsignedShort();
        assertTrue(motdLength > 0);
        assertTrue(new String(in.readBytes(motdLength),
                java.nio.charset.StandardCharsets.UTF_8).startsWith("MCPE;"));
    }

    @Test
    void unconnectedPingWithBadMagicIsRejected() {
        ByteWriter ping = new ByteWriter();
        ping.writeByte(BedrockProtocol.ID_UNCONNECTED_PING);
        ping.writeBigLong(1L);
        ping.writeBytes(new byte[16]); // Magic ではない
        ping.writeBigLong(2L);
        assertNull(RakNetCodec.parseUnconnectedPing(ping.bytes()));
    }

    @Test
    void openConnectionReply1() {
        ByteWriter request = new ByteWriter();
        request.writeByte(BedrockProtocol.ID_OPEN_CONNECTION_REQUEST_1);
        request.writeBytes(BedrockProtocol.RAKNET_MAGIC);
        request.writeByte(11);
        for (int i = 0; i < 100; i++) {
            request.writeByte(0);
        }
        RakNetCodec.OpenConnectionRequest1 parsed =
                RakNetCodec.parseOpenConnectionRequest1(request.bytes());
        assertNotNull(parsed);
        assertEquals(11, parsed.raknetProtocol());
        // 小さい要求は 576 に切り上がる。
        assertEquals(576, RakNetCodec.mtuForRequestLength(parsed.datagramLength()));
        // 大きい要求は「長さ＋28」になる。
        ByteWriter bigRequest = new ByteWriter();
        bigRequest.writeByte(BedrockProtocol.ID_OPEN_CONNECTION_REQUEST_1);
        bigRequest.writeBytes(BedrockProtocol.RAKNET_MAGIC);
        bigRequest.writeByte(11);
        for (int i = 0; i < 1000; i++) {
            bigRequest.writeByte(0);
        }
        RakNetCodec.OpenConnectionRequest1 bigParsed =
                RakNetCodec.parseOpenConnectionRequest1(bigRequest.bytes());
        assertNotNull(bigParsed);
        int mtu = RakNetCodec.mtuForRequestLength(bigParsed.datagramLength());
        assertEquals(bigRequest.bytes().length + 28, mtu);

        byte[] reply = RakNetCodec.buildOpenConnectionReply1(777L, mtu);
        assertEquals((byte) 0x06, reply[0]);
        BedrockVarInt.ByteReader in = new BedrockVarInt.ByteReader(reply, 1);
        assertArrayEquals(BedrockProtocol.RAKNET_MAGIC, in.readBytes(16));
        assertEquals(777L, in.readBigLong());
        assertEquals(false, in.readBoolean());
        assertEquals(mtu, in.readBigUnsignedShort());
    }

    @Test
    void openConnectionRequest2RoundTrip() {
        InetSocketAddress server = new InetSocketAddress("127.0.0.1", 19132);
        ByteWriter request = new ByteWriter();
        request.writeByte(BedrockProtocol.ID_OPEN_CONNECTION_REQUEST_2);
        request.writeBytes(BedrockProtocol.RAKNET_MAGIC);
        request.writeBytes(RakNetCodec.encodeAddress(server));
        request.writeByte(0x05);
        request.writeByte(0xdc); // MTU 1500（のつもりの値）
        request.writeBigLong(-42L);
        RakNetCodec.OpenConnectionRequest2 parsed =
                RakNetCodec.parseOpenConnectionRequest2(request.bytes());
        assertNotNull(parsed);
        assertEquals(1500, parsed.mtu());
        assertEquals(-42L, parsed.clientGuid());

        byte[] reply = RakNetCodec.buildOpenConnectionReply2(
                777L, parsed.serverAddressBytes(), 1400);
        assertEquals((byte) 0x08, reply[0]);
    }

    @Test
    void addressEncodingRoundTrip() {
        InetSocketAddress address = new InetSocketAddress("192.168.1.10", 19132);
        byte[] encoded = RakNetCodec.encodeAddress(address);
        assertEquals(7, encoded.length);
        assertEquals(4, encoded[0]);
        // IP の各オクテットはビット反転してあること。
        assertEquals((byte) ~192, encoded[1]);
        assertEquals((byte) ~168, encoded[2]);
        assertEquals(address, RakNetCodec.decodeAddress(encoded, 0));
        assertNull(RakNetCodec.decodeAddress(new byte[]{6, 0, 0, 0, 0, 0, 0}, 0));
    }

    @Test
    void frameSetRoundTrip() {
        byte[] payload = "hello-bedrock".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] frameSet = RakNetCodec.buildFrameSet(7,
                RakNetCodec.RELIABILITY_RELIABLE, 3, payload);
        assertTrue(RakNetCodec.isFrameSet(frameSet));
        assertEquals(7, RakNetCodec.frameSetSequenceNumber(frameSet));
        RakNetCodec.FrameSet parsed = RakNetCodec.parseFrameSet(frameSet);
        assertEquals(7, parsed.sequenceNumber());
        assertEquals(1, parsed.frames().size());
        assertEquals(RakNetCodec.RELIABILITY_RELIABLE, parsed.frames().get(0).reliability());
        assertArrayEquals(payload, parsed.frames().get(0).payload());
    }

    @Test
    void splitFramesAreReassembled() {
        // 3000 バイトを 3 断片に分けて送る（Login は 100KB 近くになるため必須）。
        byte[] whole = new byte[3000];
        for (int i = 0; i < whole.length; i++) {
            whole[i] = (byte) (i % 251);
        }
        RakNetCodec.SplitReassembler splits = new RakNetCodec.SplitReassembler();
        int splitId = 9;
        int fragmentSize = 1000;
        List<byte[]> got = new java.util.ArrayList<>();
        for (int index = 0; index < 3; index++) {
            byte[] fragment = new byte[fragmentSize];
            System.arraycopy(whole, index * fragmentSize, fragment, 0, fragmentSize);
            ByteWriter frameSet = new ByteWriter();
            frameSet.writeByte(BedrockProtocol.ID_FRAME_SET);
            frameSet.writeByte(index);
            frameSet.writeByte(0);
            frameSet.writeByte(0);
            // reliable（2）＋split フラグ。
            frameSet.writeByte((2 << 5) | 0x10);
            int bits = fragment.length * 8;
            frameSet.writeByte((bits >> 8) & 0xFF);
            frameSet.writeByte(bits & 0xFF);
            frameSet.writeByte(0);
            frameSet.writeByte(0);
            frameSet.writeByte(0); // message index
            frameSet.writeByte(0);
            frameSet.writeByte(0);
            frameSet.writeByte(0);
            frameSet.writeByte(3); // split count（BE int32）
            frameSet.writeByte(splitId >> 8);
            frameSet.writeByte(splitId & 0xFF); // split id（BE u16）
            frameSet.writeByte(0);
            frameSet.writeByte(0);
            frameSet.writeByte(0);
            frameSet.writeByte(index); // split index（BE int32）
            frameSet.writeBytes(fragment);
            got.addAll(RakNetCodec.parseFrameSetPayloads(frameSet.bytes(), splits));
            if (index < 2) {
                assertTrue(got.isEmpty(), "断片が揃うまでは何も返らないこと");
            }
        }
        assertEquals(1, got.size());
        assertArrayEquals(whole, got.get(0));
    }

    @Test
    void buildFrameSetsSplitsLargePayloads() {
        byte[] whole = new byte[100_000];
        for (int i = 0; i < whole.length; i++) {
            whole[i] = (byte) (i % 251);
        }
        List<byte[]> datagrams = RakNetCodec.buildFrameSets(10,
                RakNetCodec.RELIABILITY_RELIABLE, 5, whole, 1400, 77);
        assertTrue(datagrams.size() > 1, "100KB は MTU 1400 では複数に分かれること");
        for (byte[] datagram : datagrams) {
            assertTrue(datagram.length <= 1400, "各データグラムが MTU に収まること: " + datagram.length);
        }
        RakNetCodec.SplitReassembler splits = new RakNetCodec.SplitReassembler();
        List<byte[]> got = new java.util.ArrayList<>();
        for (byte[] datagram : datagrams) {
            got.addAll(RakNetCodec.parseFrameSetPayloads(datagram, splits));
        }
        assertEquals(1, got.size());
        assertArrayEquals(whole, got.get(0));
    }

    @Test
    void buildFrameSetsKeepsSmallPayloadsWhole() {
        byte[] small = "small".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        List<byte[]> datagrams = RakNetCodec.buildFrameSets(3,
                RakNetCodec.RELIABILITY_RELIABLE, 1, small, 1400, 1);
        assertEquals(1, datagrams.size());
        // 分割フラグ無しで parseFrameSet が読めること。
        RakNetCodec.FrameSet parsed = RakNetCodec.parseFrameSet(datagrams.get(0));
        assertEquals(1, parsed.frames().size());
        assertArrayEquals(small, parsed.frames().get(0).payload());
    }

    @Test
    void ackEncoding() {
        byte[] ack = RakNetCodec.buildAck(5, 6, 9);
        assertEquals((byte) 0xc0, ack[0]);
        assertEquals(3, ack[1] << 8 | (ack[2] & 0xFF));
    }

    @Test
    void connectedPongEncoding() {
        byte[] pong = RakNetCodec.buildConnectedPong(100L, 200L);
        assertEquals((byte) 0x03, pong[0]);
        BedrockVarInt.ByteReader in = new BedrockVarInt.ByteReader(pong, 1);
        assertEquals(100L, in.readBigLong());
        assertEquals(200L, in.readBigLong());
    }
}
