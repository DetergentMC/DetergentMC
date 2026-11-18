package io.detergentmc.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** 393（1.13）のプロトコル番号解決。このツリーで最古の対応プロトコル。 */
class ProtocolVersion393Test {

    @Test
    void version113Is393() {
        assertEquals(393, ProtocolVersion.V1_13);
        assertEquals(393, ProtocolVersion.serverProtocolFor("1.13"));
    }

    @Test
    void knownVersionsUnchanged() {
        assertEquals(401, ProtocolVersion.serverProtocolFor("1.13.1"));
        assertEquals(404, ProtocolVersion.serverProtocolFor("1.13.2"));
        assertEquals(477, ProtocolVersion.serverProtocolFor("1.14"));
        assertEquals(776, ProtocolVersion.LATEST_KNOWN);
        assertEquals(765, ProtocolVersion.DEFAULT_SERVER_PROTOCOL);
        assertEquals(765, ProtocolVersion.serverProtocolFor(null));
        assertEquals(765, ProtocolVersion.serverProtocolFor("unknown"));
        assertTrue(PacketIds.isSupported(393));
        assertFalse(PacketIds.isSupported(392));
        assertFalse(PacketIds.isSupported(394));
        assertFalse(PacketIds.isSupported(400));
    }
}
