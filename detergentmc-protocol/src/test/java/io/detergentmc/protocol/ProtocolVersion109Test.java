package io.detergentmc.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * 109 は 1.9.2 単独のプロトコル（実バニラ 1.9.2 の Status 応答で
 * protocol 109 を確認済み。1.9.3／1.9.4 は 110）。
 */
class ProtocolVersion109Test {

    @Test
    void release192MapsTo109() {
        assertEquals(ProtocolVersion.V1_9_2,
                ProtocolVersion.serverProtocolFor("1.9.2"));
    }

    @Test
    void neighboursUnchanged() {
        assertEquals(ProtocolVersion.V1_9_4,
                ProtocolVersion.serverProtocolFor("1.9.4"));
        assertEquals(ProtocolVersion.V1_9_4,
                ProtocolVersion.serverProtocolFor("1.9.3"));
        assertEquals(ProtocolVersion.V1_10_2,
                ProtocolVersion.serverProtocolFor("1.10.2"));
        assertEquals(ProtocolVersion.V1_10_2,
                ProtocolVersion.serverProtocolFor("1.10"));
        assertEquals(ProtocolVersion.V1_11,
                ProtocolVersion.serverProtocolFor("1.11"));
    }
}
