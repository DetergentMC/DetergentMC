package io.detergentmc.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** Tests for the 1.15 internal server protocol (573) in {@link ProtocolVersion}. */
class ProtocolVersion573Test {

    @Test
    void serverProtocolFor1151Is573() {
        assertEquals(573, ProtocolVersion.serverProtocolFor("1.15"));
        assertEquals(ProtocolVersion.V1_15, ProtocolVersion.serverProtocolFor("1.15"));
    }

    @Test
    void constants() {
        assertEquals(573, ProtocolVersion.V1_15);
        // 最新側は 776 のまま。既定は 1.20.4（765）のまま。
        assertEquals(776, ProtocolVersion.LATEST_KNOWN);
        assertEquals("1.20.4", ProtocolVersion.DEFAULT_MINECRAFT_VERSION);
        assertEquals(765, ProtocolVersion.DEFAULT_SERVER_PROTOCOL);
    }
}
