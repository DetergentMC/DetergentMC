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

/** Tests for the internal server protocol selection in {@link ProtocolVersion}. */
class ProtocolVersionTest {

    @Test
    void serverProtocolFor1202Is764() {
        assertEquals(764, ProtocolVersion.serverProtocolFor("1.20.2"));
    }

    @Test
    void serverProtocolFor1201Is763() {
        assertEquals(763, ProtocolVersion.serverProtocolFor("1.20.1"));
        assertEquals(763, ProtocolVersion.serverProtocolFor("1.20"));
    }

    @Test
    void serverProtocolFor1205Is766() {
        assertEquals(766, ProtocolVersion.serverProtocolFor("1.20.5"));
        assertEquals(766, ProtocolVersion.serverProtocolFor("1.20.6"));
    }

    @Test
    void serverProtocolFor1204Is765() {
        assertEquals(765, ProtocolVersion.serverProtocolFor("1.20.4"));
    }

    @Test
    void serverProtocolFor1203Is765() {
        // 1.20.3 and 1.20.4 share protocol 765.
        assertEquals(765, ProtocolVersion.serverProtocolFor("1.20.3"));
    }

    @Test
    void serverProtocolForDefaultVersionIs765() {
        assertEquals(ProtocolVersion.DEFAULT_SERVER_PROTOCOL,
                ProtocolVersion.serverProtocolFor(ProtocolVersion.DEFAULT_MINECRAFT_VERSION));
        assertEquals(765, ProtocolVersion.serverProtocolFor("1.20.4"));
    }

    @Test
    void serverProtocolForUnknownVersionFallsBackTo765WithWarning() {
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
            assertEquals(765, ProtocolVersion.serverProtocolFor("1.99.9"));
            assertEquals(765, ProtocolVersion.serverProtocolFor(null));
            assertEquals(765, ProtocolVersion.serverProtocolFor(""));
        } finally {
            logger.removeHandler(handler);
        }
        assertTrue(warnings.size() >= 3,
                "expected a warning for each unknown version, got: " + warnings);
        assertTrue(warnings.stream().anyMatch(r -> r.getMessage().contains("1.99.9")),
                "warning should name the unknown version: " + warnings);
    }
}
