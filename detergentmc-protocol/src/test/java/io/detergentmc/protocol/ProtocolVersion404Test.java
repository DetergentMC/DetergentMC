package io.detergentmc.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** 404（1.13.2）のプロトコル番号解決。 */
class ProtocolVersion404Test {

    @Test
    void version1132Is404() {
        assertEquals(404, ProtocolVersion.V1_13_2);
        assertEquals(404, ProtocolVersion.serverProtocolFor("1.13.2"));
    }

    @Test
    void knownVersionsUnchanged() {
        assertEquals(477, ProtocolVersion.serverProtocolFor("1.14"));
        assertEquals(776, ProtocolVersion.LATEST_KNOWN);
        assertEquals(765, ProtocolVersion.DEFAULT_SERVER_PROTOCOL);
        assertEquals(765, ProtocolVersion.serverProtocolFor(null));
        assertEquals(765, ProtocolVersion.serverProtocolFor("unknown"));
        assertTrue(PacketIds.isSupported(404));
        assertFalse(PacketIds.isSupported(403));
    }
}
