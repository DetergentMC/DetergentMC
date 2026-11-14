package io.detergentmc.bedrock;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.detergentmc.bedrock.BedrockBatch.GamePacket;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Bedrock バッチ（0xfe＋zlib）のテスト。 */
class BedrockBatchTest {

    @Test
    void encodeDecodeRoundTrip() throws Exception {
        List<GamePacket> packets = List.of(
                new GamePacket(0x01, new byte[]{1, 2, 3}),
                new GamePacket(0x09, "hello".getBytes(java.nio.charset.StandardCharsets.UTF_8)),
                new GamePacket(0x13, new byte[0]));
        byte[] batch = BedrockBatch.encode(packets);
        assertEquals((byte) 0xfe, batch[0]);
        List<GamePacket> decoded = BedrockBatch.decode(batch);
        assertEquals(3, decoded.size());
        assertEquals(0x01, decoded.get(0).id());
        assertArrayEquals(new byte[]{1, 2, 3}, decoded.get(0).payload());
        assertEquals(0x09, decoded.get(1).id());
        assertEquals("hello", new String(decoded.get(1).payload(),
                java.nio.charset.StandardCharsets.UTF_8));
        assertEquals(0x13, decoded.get(2).id());
        assertEquals(0, decoded.get(2).payload().length);
    }

    @Test
    void encodeOne() throws Exception {
        byte[] batch = BedrockBatch.encodeOne(0x09, new byte[]{0x41});
        List<GamePacket> decoded = BedrockBatch.decode(batch);
        assertEquals(1, decoded.size());
        assertEquals(0x09, decoded.get(0).id());
        assertArrayEquals(new byte[]{0x41}, decoded.get(0).payload());
    }

    @Test
    void emptyBatchRoundTrip() throws Exception {
        byte[] batch = BedrockBatch.encode(List.of());
        assertEquals(0, BedrockBatch.decode(batch).size());
    }

    @Test
    void largePayloadRoundTrip() throws Exception {
        // Login パケット級（100KB）の大きさでも往復すること。
        byte[] big = new byte[100_000];
        for (int i = 0; i < big.length; i++) {
            big[i] = (byte) (i % 251);
        }
        byte[] batch = BedrockBatch.encodeOne(0x01, big);
        List<GamePacket> decoded = BedrockBatch.decode(batch);
        assertEquals(1, decoded.size());
        assertArrayEquals(big, decoded.get(0).payload());
    }

    @Test
    void nonBatchIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> BedrockBatch.decode(new byte[]{0x01, 0x02, 0x03}));
        assertThrows(IllegalArgumentException.class,
                () -> BedrockBatch.decode(new byte[0]));
    }

    @Test
    void corruptZlibIsRejected() {
        byte[] batch = new byte[]{(byte) 0xfe, 0x01, 0x02, 0x03, 0x04};
        assertThrows(java.io.IOException.class, () -> BedrockBatch.decode(batch));
    }
}
