package io.detergentmc.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * 110 は 1.9.3 と 1.9.4 の2リリース共通プロトコル
 * （実バニラの Status 応答で両版とも protocol 110 を確認済み）。
 */
class ProtocolVersion110Test {

    @Test
    void both19ReleasesMapTo110() {
        assertEquals(ProtocolVersion.V1_9_4,
                ProtocolVersion.serverProtocolFor("1.9.4"));
        assertEquals(ProtocolVersion.V1_9_4,
                ProtocolVersion.serverProtocolFor("1.9.3"));
    }

    @Test
    void neighboursUnchanged() {
        assertEquals(ProtocolVersion.V1_10_2,
                ProtocolVersion.serverProtocolFor("1.10.2"));
        assertEquals(ProtocolVersion.V1_10_2,
                ProtocolVersion.serverProtocolFor("1.10"));
        assertEquals(ProtocolVersion.V1_11,
                ProtocolVersion.serverProtocolFor("1.11"));
    }
}
