package io.detergentmc.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** Tests for the 1.14.1 internal server protocol (480) in {@link ProtocolVersion}. */
class ProtocolVersion480Test {

    @Test
    void serverProtocolFor1141Is480() {
        assertEquals(480, ProtocolVersion.serverProtocolFor("1.14.1"));
        assertEquals(ProtocolVersion.V1_14_1, ProtocolVersion.serverProtocolFor("1.14.1"));
        assertEquals(485, ProtocolVersion.serverProtocolFor("1.14.2"));
    }

    @Test
    void constants() {
        assertEquals(480, ProtocolVersion.V1_14_1);
        assertEquals(485, ProtocolVersion.V1_14_2);
        // 最新側は 776 のまま。既定は 1.20.4（765）のまま。
        assertEquals(776, ProtocolVersion.LATEST_KNOWN);
        assertEquals("1.20.4", ProtocolVersion.DEFAULT_MINECRAFT_VERSION);
        assertEquals(765, ProtocolVersion.DEFAULT_SERVER_PROTOCOL);
    }
}
