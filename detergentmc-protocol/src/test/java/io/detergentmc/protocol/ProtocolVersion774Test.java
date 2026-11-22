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

/** Tests for the 1.21.11 internal server protocol (774) in {@link ProtocolVersion}. */
class ProtocolVersion774Test {

    @Test
    void serverProtocolFor12111Is774() {
        assertEquals(774, ProtocolVersion.serverProtocolFor("1.21.11"));
        
        assertEquals(ProtocolVersion.V1_21_11, ProtocolVersion.serverProtocolFor("1.21.11"));
        
    }

    @Test
    void serverProtocolFor12111LogsNoWarning() {
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
            assertEquals(774, ProtocolVersion.serverProtocolFor("1.21.11"));
            
        } finally {
            logger.removeHandler(handler);
        }
        assertTrue(warnings.isEmpty(),
                "1.21.11 must not warn: " + warnings);
    }

    @Test
    void latestKnownIs774AndDefaultIsUnchanged() {
        assertEquals(776, ProtocolVersion.LATEST_KNOWN);
        assertEquals(ProtocolVersion.V26_2, ProtocolVersion.LATEST_KNOWN);
        // The default server version stays 1.20.4 (765): 1.21.11 is opt-in
        // via minecraft-version, not the new default.
        assertEquals("1.20.4", ProtocolVersion.DEFAULT_MINECRAFT_VERSION);
        assertEquals(765, ProtocolVersion.DEFAULT_SERVER_PROTOCOL);
        assertEquals(765, ProtocolVersion.serverProtocolFor(
                ProtocolVersion.DEFAULT_MINECRAFT_VERSION));
    }
}
