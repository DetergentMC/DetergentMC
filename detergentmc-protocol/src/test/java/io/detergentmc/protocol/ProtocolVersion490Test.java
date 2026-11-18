package io.detergentmc.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** Tests for the 1.14.3 internal server protocol (490) in {@link ProtocolVersion}. */
class ProtocolVersion490Test {

    @Test
    void serverProtocolFor1143Is490() {
        assertEquals(490, ProtocolVersion.serverProtocolFor("1.14.3"));
        assertEquals(ProtocolVersion.V1_14_3, ProtocolVersion.serverProtocolFor("1.14.3"));
    }

    @Test
    void constants() {
        assertEquals(490, ProtocolVersion.V1_14_3);
        // 最新側は 776 のまま。既定は 1.20.4（765）のまま。
        assertEquals(776, ProtocolVersion.LATEST_KNOWN);
        assertEquals("1.20.4", ProtocolVersion.DEFAULT_MINECRAFT_VERSION);
        assertEquals(765, ProtocolVersion.DEFAULT_SERVER_PROTOCOL);
    }
}
