package io.detergentmc.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** 477（1.14）のプロトコル番号解決。 */
class ProtocolVersion477Test {

    @Test
    void version114Is477() {
        assertEquals(477, ProtocolVersion.V1_14);
        assertEquals(477, ProtocolVersion.serverProtocolFor("1.14"));
    }

    @Test
    void knownVersionsUnchanged() {
        assertEquals(480, ProtocolVersion.serverProtocolFor("1.14.1"));
        assertEquals(776, ProtocolVersion.LATEST_KNOWN);
        assertEquals(765, ProtocolVersion.DEFAULT_SERVER_PROTOCOL);
        assertEquals(765, ProtocolVersion.serverProtocolFor(null));
        assertEquals(765, ProtocolVersion.serverProtocolFor("unknown"));
        assertTrue(PacketIds.isSupported(477));
        assertFalse(PacketIds.isSupported(476));
    }
}
