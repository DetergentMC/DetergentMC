package io.detergentmc.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** Tests for the 1.15.2 internal server protocol (578) in {@link ProtocolVersion}. */
class ProtocolVersion578Test {

    @Test
    void serverProtocolFor1152Is578() {
        assertEquals(578, ProtocolVersion.serverProtocolFor("1.15.2"));
        assertEquals(ProtocolVersion.V1_15_2, ProtocolVersion.serverProtocolFor("1.15.2"));
    }

    @Test
    void constants() {
        assertEquals(578, ProtocolVersion.V1_15_2);
        // 最新側は 776 のまま。既定は 1.20.4（765）のまま。
        assertEquals(776, ProtocolVersion.LATEST_KNOWN);
        assertEquals("1.20.4", ProtocolVersion.DEFAULT_MINECRAFT_VERSION);
        assertEquals(765, ProtocolVersion.DEFAULT_SERVER_PROTOCOL);
    }
}
