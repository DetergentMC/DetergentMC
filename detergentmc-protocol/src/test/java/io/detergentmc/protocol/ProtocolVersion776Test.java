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

/** Tests for the 26.2 internal server protocol (776) in {@link ProtocolVersion}. */
class ProtocolVersion776Test {

    @Test
    void serverProtocolFor262Is776() {
        assertEquals(776, ProtocolVersion.serverProtocolFor("26.2"));
        assertEquals(ProtocolVersion.V26_2, ProtocolVersion.serverProtocolFor("26.2"));
    }

    @Test
    void serverProtocolFor262LogsNoWarning() {
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
            assertEquals(776, ProtocolVersion.serverProtocolFor("26.2"));
        } finally {
            logger.removeHandler(handler);
        }
        assertTrue(warnings.isEmpty(),
                "26.2 must not warn: " + warnings);
    }

    @Test
    void latestKnownIs776AndDefaultIsUnchanged() {
        assertEquals(776, ProtocolVersion.LATEST_KNOWN);
        assertEquals(ProtocolVersion.V26_2, ProtocolVersion.LATEST_KNOWN);
        // The default server version stays 1.20.4 (765): 26.2 is opt-in
        // via minecraft-version, not the new default.
        assertEquals("1.20.4", ProtocolVersion.DEFAULT_MINECRAFT_VERSION);
        assertEquals(765, ProtocolVersion.DEFAULT_SERVER_PROTOCOL);
        assertEquals(765, ProtocolVersion.serverProtocolFor(
                ProtocolVersion.DEFAULT_MINECRAFT_VERSION));
    }
}
