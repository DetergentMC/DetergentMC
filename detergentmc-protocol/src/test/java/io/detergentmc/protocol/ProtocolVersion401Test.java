package io.detergentmc.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** 401（1.13.1）のプロトコル番号解決。 */
class ProtocolVersion401Test {

    @Test
    void version1131Is401() {
        assertEquals(401, ProtocolVersion.V1_13_1);
        assertEquals(401, ProtocolVersion.serverProtocolFor("1.13.1"));
    }

    @Test
    void knownVersionsUnchanged() {
        assertEquals(404, ProtocolVersion.serverProtocolFor("1.13.2"));
        assertEquals(477, ProtocolVersion.serverProtocolFor("1.14"));
        assertEquals(776, ProtocolVersion.LATEST_KNOWN);
        assertEquals(765, ProtocolVersion.DEFAULT_SERVER_PROTOCOL);
        assertEquals(765, ProtocolVersion.serverProtocolFor(null));
        assertEquals(765, ProtocolVersion.serverProtocolFor("unknown"));
        assertTrue(PacketIds.isSupported(401));
        assertFalse(PacketIds.isSupported(400));
        assertFalse(PacketIds.isSupported(402));
        assertFalse(PacketIds.isSupported(403));
    }
}
