package io.detergentmc.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** 316（1.11.2）のプロトコル番号解決。最古は 316 に移った。 */
class ProtocolVersion316Test {

    @Test
    void version1112Is316() {
        assertEquals(316, ProtocolVersion.V1_11_2);
        assertEquals(316, ProtocolVersion.serverProtocolFor("1.11.2"));
    }

    @Test
    void knownVersionsUnchanged() {
        assertEquals(335, ProtocolVersion.serverProtocolFor("1.12"));
        assertEquals(393, ProtocolVersion.serverProtocolFor("1.13"));
        assertEquals(776, ProtocolVersion.LATEST_KNOWN);
        assertEquals(765, ProtocolVersion.DEFAULT_SERVER_PROTOCOL);
        assertEquals(765, ProtocolVersion.serverProtocolFor(null));
        assertEquals(765, ProtocolVersion.serverProtocolFor("unknown"));
        assertTrue(PacketIds.isSupported(316));
        assertTrue(PacketIds.isSupported(335));
        // 315 は 1.11 として対応済み。314 と 317 は未対応。
        assertTrue(PacketIds.isSupported(315));
        assertFalse(PacketIds.isSupported(314));
        assertFalse(PacketIds.isSupported(317));
    }
}
