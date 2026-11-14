package io.detergentmc.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;

/** Tests for the 1.21 / 1.21.1 internal server protocol (767) in {@link ProtocolVersion}. */
class ProtocolVersion767Test {

    @Test
    void serverProtocolFor121Is767() {
        assertEquals(767, ProtocolVersion.serverProtocolFor("1.21"));
        assertEquals(767, ProtocolVersion.serverProtocolFor("1.21.1"));
        assertEquals(ProtocolVersion.V1_21, ProtocolVersion.serverProtocolFor("1.21"));
    }

    @Test
    void serverProtocolFor121LogsNoWarning() {
        List<LogRecord> warnings = new ArrayList<>();
        Logger logger = Logger.getLogger(ProtocolVersion.class.getName());
        Handler handler = new Handler() {
            @Override
            public void publish(LogRecord record) {
                if (record.getLevel().intValue() >= Level.WARNING.intValue()) {
                    warnings.add(record);
                }
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        };
        logger.addHandler(handler);
        try {
            assertEquals(767, ProtocolVersion.serverProtocolFor("1.21"));
            assertEquals(767, ProtocolVersion.serverProtocolFor("1.21.1"));
        } finally {
            logger.removeHandler(handler);
        }
        assertTrue(warnings.isEmpty(),
                "1.21 / 1.21.1 must not warn: " + warnings);
    }

    @Test
    void latestKnownIsNow772AndDefaultIsUnchanged() {
        // 775対応後、LATEST_KNOWN は 775（26.1）になった。
        assertEquals(775, ProtocolVersion.LATEST_KNOWN);
        assertEquals(ProtocolVersion.V26_1, ProtocolVersion.LATEST_KNOWN);
        // The default server version stays 1.20.4 (765): 1.21 is opt-in
        // via minecraft-version, not the new default.
        assertEquals("1.20.4", ProtocolVersion.DEFAULT_MINECRAFT_VERSION);
        assertEquals(765, ProtocolVersion.DEFAULT_SERVER_PROTOCOL);
        assertEquals(765, ProtocolVersion.serverProtocolFor(
                ProtocolVersion.DEFAULT_MINECRAFT_VERSION));
    }

    @Test
    void unknownVersionStillFallsBackTo765() {
        // 26.2 (776) is not known yet: it falls back to 765 with a
        // warning, the same as any other unknown version.
        assertEquals(765, ProtocolVersion.serverProtocolFor("26.2"));
        assertEquals(765, ProtocolVersion.serverProtocolFor("1.99.9"));
    }
}
