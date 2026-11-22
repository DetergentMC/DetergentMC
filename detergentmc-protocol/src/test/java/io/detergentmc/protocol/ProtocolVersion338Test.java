package io.detergentmc.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** 338（1.12.1）のプロトコル番号解決。このツリーで最古の対応プロトコル。 */
class ProtocolVersion338Test {

    @Test
    void version1121Is338() {
        assertEquals(338, ProtocolVersion.V1_12_1);
        assertEquals(338, ProtocolVersion.serverProtocolFor("1.12.1"));
    }

    @Test
    void knownVersionsUnchanged() {
        assertEquals(340, ProtocolVersion.serverProtocolFor("1.12.2"));
        assertEquals(393, ProtocolVersion.serverProtocolFor("1.13"));
        assertEquals(401, ProtocolVersion.serverProtocolFor("1.13.1"));
        assertEquals(404, ProtocolVersion.serverProtocolFor("1.13.2"));
        assertEquals(776, ProtocolVersion.LATEST_KNOWN);
        assertEquals(765, ProtocolVersion.DEFAULT_SERVER_PROTOCOL);
        assertEquals(765, ProtocolVersion.serverProtocolFor(null));
        assertEquals(765, ProtocolVersion.serverProtocolFor("unknown"));
        assertTrue(PacketIds.isSupported(338));
        assertTrue(PacketIds.isSupported(340));
        assertFalse(PacketIds.isSupported(337));
        assertFalse(PacketIds.isSupported(339));
        // 335（1.12）は対応済みになった。
        assertTrue(PacketIds.isSupported(335));
        assertFalse(PacketIds.isSupported(334));
    }
}
