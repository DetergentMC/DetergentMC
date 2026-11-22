package io.detergentmc.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** Tests for the 1.14.4 internal server protocol (498) in {@link ProtocolVersion}. */
class ProtocolVersion498Test {

    @Test
    void serverProtocolFor1144Is498() {
        assertEquals(498, ProtocolVersion.serverProtocolFor("1.14.4"));
        assertEquals(ProtocolVersion.V1_14_4, ProtocolVersion.serverProtocolFor("1.14.4"));
    }

    @Test
    void constants() {
        assertEquals(498, ProtocolVersion.V1_14_4);
        // 最新側は 776 のまま。既定は 1.20.4（765）のまま。
        assertEquals(776, ProtocolVersion.LATEST_KNOWN);
        assertEquals("1.20.4", ProtocolVersion.DEFAULT_MINECRAFT_VERSION);
        assertEquals(765, ProtocolVersion.DEFAULT_SERVER_PROTOCOL);
    }
}
