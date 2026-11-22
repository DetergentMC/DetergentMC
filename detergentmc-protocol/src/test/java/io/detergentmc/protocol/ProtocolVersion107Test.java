package io.detergentmc.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * 107 は 1.9 単独のプロトコル（実バニラ 1.9 の Status 応答で
 * protocol 107 を確認済み。1.9.1 は 108、1.9.2 は 109、
 * 1.9.3／1.9.4 は 110）。
 */
class ProtocolVersion107Test {

    @Test
    void release19MapsTo107() {
        assertEquals(ProtocolVersion.V1_9,
                ProtocolVersion.serverProtocolFor("1.9"));
    }

    @Test
    void neighboursUnchanged() {
        assertEquals(ProtocolVersion.V1_9_1,
                ProtocolVersion.serverProtocolFor("1.9.1"));
        assertEquals(ProtocolVersion.V1_9_2,
                ProtocolVersion.serverProtocolFor("1.9.2"));
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
