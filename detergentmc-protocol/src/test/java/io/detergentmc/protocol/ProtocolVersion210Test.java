package io.detergentmc.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * 210 は 1.10／1.10.1／1.10.2 の3リリース共通プロトコル
 * （実バニラの Status 応答で3版とも protocol 210 を確認済み。
 * 201 という製品版プロトコルは存在しない）。
 */
class ProtocolVersion210Test {

    @Test
    void allThree110ReleasesMapTo210() {
        assertEquals(ProtocolVersion.V1_10_2,
                ProtocolVersion.serverProtocolFor("1.10.2"));
        assertEquals(ProtocolVersion.V1_10_2,
                ProtocolVersion.serverProtocolFor("1.10.1"));
        assertEquals(ProtocolVersion.V1_10_2,
                ProtocolVersion.serverProtocolFor("1.10"));
    }

    @Test
    void neighboursUnchanged() {
        assertEquals(ProtocolVersion.V1_11,
                ProtocolVersion.serverProtocolFor("1.11"));
        assertEquals(ProtocolVersion.V1_11_2,
                ProtocolVersion.serverProtocolFor("1.11.2"));
        assertEquals(ProtocolVersion.V1_11_2,
                ProtocolVersion.serverProtocolFor("1.11.1"));
    }
}
