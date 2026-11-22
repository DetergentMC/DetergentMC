package io.detergentmc.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** Tests for the 1.15.1 internal server protocol (575) in {@link ProtocolVersion}. */
class ProtocolVersion575Test {

    @Test
    void serverProtocolFor1151Is575() {
        assertEquals(575, ProtocolVersion.serverProtocolFor("1.15.1"));
        assertEquals(ProtocolVersion.V1_15_1, ProtocolVersion.serverProtocolFor("1.15.1"));
    }

    @Test
    void constants() {
        assertEquals(575, ProtocolVersion.V1_15_1);
        // 最新側は 776 のまま。既定は 1.20.4（765）のまま。
        assertEquals(776, ProtocolVersion.LATEST_KNOWN);
        assertEquals("1.20.4", ProtocolVersion.DEFAULT_MINECRAFT_VERSION);
        assertEquals(765, ProtocolVersion.DEFAULT_SERVER_PROTOCOL);
    }
}
