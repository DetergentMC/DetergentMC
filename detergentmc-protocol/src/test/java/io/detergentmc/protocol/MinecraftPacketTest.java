package io.detergentmc.protocol;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/** パケットのフレーミング（長さプレフィックス付き）の往復。 */
class MinecraftPacketTest {

    @Test
    void encodesLengthPrefixedFrame() {
        // ID 0x00、ペイロードなし → 長さ1、ID 0。
        assertArrayEquals(new byte[]{0x01, 0x00},
                new MinecraftPacket(0, new byte[0]).encode());
        // ID 0x25（Join Game 765）、ペイロード3バイト → 長さ4。
        assertArrayEquals(new byte[]{0x04, 0x25, 0x01, 0x02, 0x03},
                new MinecraftPacket(0x25, new byte[]{0x01, 0x02, 0x03}).encode());
    }

    @Test
    void roundTripsEmptyAndLargePayloads() {
        for (byte[] payload : new byte[][]{new byte[0], {0x7F}, new byte[1000]}) {
            MinecraftPacket packet = new MinecraftPacket(0x24, payload);
            MinecraftPacket.Decoded decoded = MinecraftPacket.decodeOne(packet.encode(), 0);
            assertEquals(packet, decoded.packet());
            assertEquals(packet.encode().length, decoded.bytesConsumed());
        }
    }

    @Test
    void payloadIsCopied() {
        byte[] payload = {0x01, 0x02};
        MinecraftPacket packet = new MinecraftPacket(1, payload);
        payload[0] = 0x09;
        assertEquals(0x01, packet.payload()[0], "元の配列を変えてもパケットは変わらないこと");
        packet.payload()[0] = 0x08;
        assertEquals(0x01, packet.payload()[0], "取り出した配列を変えてもパケットは変わらないこと");
    }

    @Test
    void decodeAllHandlesConcatenatedPackets() {
        MinecraftPacket first = new MinecraftPacket(0x00, new byte[]{0x01});
        MinecraftPacket second = new MinecraftPacket(0x24, new byte[0]);
        MinecraftPacket third = new MinecraftPacket(0x69, new byte[]{0x05, 0x06, 0x07});
        byte[] joined = concat(first.encode(), second.encode(), third.encode());
        assertEquals(List.of(first, second, third), MinecraftPacket.decodeAll(joined));
        assertTrue(MinecraftPacket.decodeAll(new byte[0]).isEmpty());
    }

    @Test
    void decoderHandlesSplitReads() {
        MinecraftPacket.Decoder decoder = new MinecraftPacket.Decoder();
        MinecraftPacket packet = new MinecraftPacket(0x24, new byte[]{0x01, 0x02, 0x03});
        byte[] frame = packet.encode();
        // 1バイトずつ流し込んでも、最後の1バイトで1個のパケットが完成する。
        List<MinecraftPacket> completed = List.of();
        for (int i = 0; i < frame.length - 1; i++) {
            completed = decoder.feed(new byte[]{frame[i]});
            assertTrue(completed.isEmpty(), i + "バイト目ではまだ完成しないこと");
        }
        completed = decoder.feed(new byte[]{frame[frame.length - 1]});
        assertEquals(List.of(packet), completed);
        assertEquals(0, decoder.bufferedBytes());
    }

    @Test
    void decoderHandlesMultiplePacketsInOneRead() {
        MinecraftPacket.Decoder decoder = new MinecraftPacket.Decoder();
        MinecraftPacket first = new MinecraftPacket(0x00, new byte[]{0x01});
        MinecraftPacket second = new MinecraftPacket(0x24, new byte[0]);
        // 2個分を一度に流し込むと2個とも返る。
        assertEquals(List.of(first, second),
                decoder.feed(concat(first.encode(), second.encode())));
        // 半分だけ流し込んでから残りを流し込むと、後半で完成する。
        byte[] frame = first.encode();
        assertTrue(decoder.feed(new byte[]{frame[0]}).isEmpty());
        assertEquals(List.of(first),
                decoder.feed(new byte[]{frame[1], frame[2]}));
    }

    @Test
    void decoderKeepsPartialLengthPrefix() {
        // 長さが2バイトになる大きなパケットで、長さのVarInt自体が分かれる場合。
        // ペイロード300バイト＋ID 1バイト＝長さ301は2バイトのVarInt。
        MinecraftPacket.Decoder decoder = new MinecraftPacket.Decoder();
        MinecraftPacket packet = new MinecraftPacket(0x25, new byte[300]);
        byte[] frame = packet.encode();
        assertEquals(2, VarInt.encodedSize(301));
        assertTrue(decoder.feed(new byte[]{frame[0]}).isEmpty());
        assertEquals(List.of(packet),
                decoder.feed(frame, 1, frame.length - 1));
    }

    @Test
    void rejectsTruncatedFrame() {
        byte[] frame = new MinecraftPacket(0x24, new byte[]{0x01, 0x02}).encode();
        byte[] truncated = new byte[frame.length - 1];
        System.arraycopy(frame, 0, truncated, 0, truncated.length);
        assertThrows(IllegalArgumentException.class, () -> MinecraftPacket.decodeOne(truncated, 0));
        assertThrows(IllegalArgumentException.class, () -> MinecraftPacket.decodeAll(truncated));
    }

    @Test
    void rejectsNegativeId() {
        assertThrows(IllegalArgumentException.class, () -> new MinecraftPacket(-1, new byte[0]));
    }

    @Test
    void rejectsOversizedFrame() {
        // 長さが上限（2,097,152）を超えるフレーム。
        byte[] header = VarInt.encode(MinecraftPacket.MAX_FRAME_LENGTH + 1);
        assertThrows(IllegalArgumentException.class, () -> MinecraftPacket.decodeOne(header, 0));
        MinecraftPacket.Decoder decoder = new MinecraftPacket.Decoder();
        assertThrows(IllegalArgumentException.class, () -> decoder.feed(header));
    }

    private static byte[] concat(byte[]... parts) {
        int length = 0;
        for (byte[] part : parts) {
            length += part.length;
        }
        byte[] out = new byte[length];
        int offset = 0;
        for (byte[] part : parts) {
            System.arraycopy(part, 0, out, offset, part.length);
            offset += part.length;
        }
        return out;
    }
}
