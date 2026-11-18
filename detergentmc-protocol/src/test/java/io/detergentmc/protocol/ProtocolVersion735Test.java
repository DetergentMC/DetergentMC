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

/** Tests for the 1.16 internal server protocol (735) in {@link ProtocolVersion}. */
class ProtocolVersion735Test {

    @Test
    void serverProtocolFor116Is735() {
        assertEquals(735, ProtocolVersion.serverProtocolFor("1.16"));
        assertEquals(ProtocolVersion.V1_16, ProtocolVersion.serverProtocolFor("1.16"));
    }

    @Test
    void serverProtocolFor116LogsNoWarning() {
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
            assertEquals(735, ProtocolVersion.serverProtocolFor("1.16"));
        } finally {
            logger.removeHandler(handler);
        }
        assertTrue(warnings.isEmpty(),
                "1.16 must not warn: " + warnings);
    }

    @Test
    void latestKnownIsStill775AndDefaultIsUnchanged() {
        // 735 の追加では LATEST_KNOWN は変わらない（775＝26.1 のまま）。
        assertEquals(775, ProtocolVersion.LATEST_KNOWN);
        assertEquals(ProtocolVersion.V26_1, ProtocolVersion.LATEST_KNOWN);
        // The default server version stays 1.20.4 (765): 1.16 is opt-in
        // via minecraft-version, not the new default.
        assertEquals("1.20.4", ProtocolVersion.DEFAULT_MINECRAFT_VERSION);
        assertEquals(765, ProtocolVersion.DEFAULT_SERVER_PROTOCOL);
    }
}
