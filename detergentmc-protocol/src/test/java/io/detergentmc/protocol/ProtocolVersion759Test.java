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

/** Tests for the 1.19 internal server protocol (759) in {@link ProtocolVersion}. */
class ProtocolVersion759Test {

    @Test
    void serverProtocolFor119Is759() {
        assertEquals(759, ProtocolVersion.serverProtocolFor("1.19"));

        assertEquals(ProtocolVersion.V1_19, ProtocolVersion.serverProtocolFor("1.19"));
    }

    @Test
    void serverProtocolFor119LogsNoWarning() {
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
            assertEquals(759, ProtocolVersion.serverProtocolFor("1.19"));
        } finally {
            logger.removeHandler(handler);
        }
        assertTrue(warnings.isEmpty(),
                "1.19 must not warn: " + warnings);
    }

    @Test
    void oldestSupportedIs759AndDefaultIsUnchanged() {
        assertEquals(759, ProtocolVersion.V1_19);
        // The default server version stays 1.20.4 (765): 1.19 is opt-in
        // via minecraft-version, not the new default.
        assertEquals("1.20.4", ProtocolVersion.DEFAULT_MINECRAFT_VERSION);
        assertEquals(765, ProtocolVersion.DEFAULT_SERVER_PROTOCOL);
        assertEquals(765, ProtocolVersion.serverProtocolFor(
                ProtocolVersion.DEFAULT_MINECRAFT_VERSION));
    }
}
