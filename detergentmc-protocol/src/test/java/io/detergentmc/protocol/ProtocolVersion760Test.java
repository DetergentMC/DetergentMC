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

/** Tests for the 1.19.1 / 1.19.2 internal server protocol (760) in {@link ProtocolVersion}. */
class ProtocolVersion760Test {

    @Test
    void serverProtocolFor1191And1192Is760() {
        assertEquals(760, ProtocolVersion.serverProtocolFor("1.19.1"));
        assertEquals(760, ProtocolVersion.serverProtocolFor("1.19.2"));

        assertEquals(ProtocolVersion.V1_19_1, ProtocolVersion.serverProtocolFor("1.19.1"));
        assertEquals(ProtocolVersion.V1_19_1, ProtocolVersion.serverProtocolFor("1.19.2"));
    }

    @Test
    void serverProtocolFor1191LogsNoWarning() {
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
            assertEquals(760, ProtocolVersion.serverProtocolFor("1.19.1"));
            assertEquals(760, ProtocolVersion.serverProtocolFor("1.19.2"));

        } finally {
            logger.removeHandler(handler);
        }
        assertTrue(warnings.isEmpty(),
                "1.19.1 / 1.19.2 must not warn: " + warnings);
    }

    @Test
    void oldestSupportedIs760AndDefaultIsUnchanged() {
        assertEquals(760, ProtocolVersion.V1_19_1);
        // The default server version stays 1.20.4 (765): 1.19.1 / 1.19.2 is opt-in
        // via minecraft-version, not the new default.
        assertEquals("1.20.4", ProtocolVersion.DEFAULT_MINECRAFT_VERSION);
        assertEquals(765, ProtocolVersion.DEFAULT_SERVER_PROTOCOL);
        assertEquals(765, ProtocolVersion.serverProtocolFor(
                ProtocolVersion.DEFAULT_MINECRAFT_VERSION));
    }
}
