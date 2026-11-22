package io.detergentmc.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** 340（1.12.2）のプロトコル番号解決。最古は 338 に移った。 */
class ProtocolVersion340Test {

    @Test
    void version1122Is340() {
        assertEquals(340, ProtocolVersion.V1_12_2);
        assertEquals(340, ProtocolVersion.serverProtocolFor("1.12.2"));
    }

    @Test
    void knownVersionsUnchanged() {
        assertEquals(393, ProtocolVersion.serverProtocolFor("1.13"));
        assertEquals(401, ProtocolVersion.serverProtocolFor("1.13.1"));
        assertEquals(404, ProtocolVersion.serverProtocolFor("1.13.2"));
        assertEquals(776, ProtocolVersion.LATEST_KNOWN);
        assertEquals(765, ProtocolVersion.DEFAULT_SERVER_PROTOCOL);
        assertEquals(765, ProtocolVersion.serverProtocolFor(null));
        assertEquals(765, ProtocolVersion.serverProtocolFor("unknown"));
        assertTrue(PacketIds.isSupported(340));
        assertTrue(PacketIds.isSupported(393));
        assertFalse(PacketIds.isSupported(339));
        assertFalse(PacketIds.isSupported(341));
        // 335（1.12）は対応済みになった。
        assertTrue(PacketIds.isSupported(335));
        assertFalse(PacketIds.isSupported(334));
        assertTrue(PacketIds.isSupported(338));
    }
}
