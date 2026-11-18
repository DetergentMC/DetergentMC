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

/** Tests for the 1.18 / 1.18.1 internal server protocol (757) in {@link ProtocolVersion}. */
class ProtocolVersion757Test {

    @Test
    void serverProtocolFor118Is757() {
        assertEquals(757, ProtocolVersion.serverProtocolFor("1.18"));
        assertEquals(757, ProtocolVersion.serverProtocolFor("1.18.1"));
        assertEquals(ProtocolVersion.V1_18, ProtocolVersion.serverProtocolFor("1.18"));
        assertEquals(ProtocolVersion.V1_18, ProtocolVersion.serverProtocolFor("1.18.1"));
    }

    @Test
    void serverProtocolFor118LogsNoWarning() {
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
            assertEquals(757, ProtocolVersion.serverProtocolFor("1.18"));
            assertEquals(757, ProtocolVersion.serverProtocolFor("1.18.1"));
        } finally {
            logger.removeHandler(handler);
        }
        assertTrue(warnings.isEmpty(),
                "1.18 / 1.18.1 must not warn: " + warnings);
    }

    @Test
    void latestKnownIsStill775AndDefaultIsUnchanged() {
        // 757 の追加では LATEST_KNOWN は変わらない（775＝26.1 のまま）。
        assertEquals(775, ProtocolVersion.LATEST_KNOWN);
        assertEquals(ProtocolVersion.V26_1, ProtocolVersion.LATEST_KNOWN);
        // The default server version stays 1.20.4 (765): 1.18 is opt-in
        // via minecraft-version, not the new default.
        assertEquals("1.20.4", ProtocolVersion.DEFAULT_MINECRAFT_VERSION);
        assertEquals(765, ProtocolVersion.DEFAULT_SERVER_PROTOCOL);
        assertEquals(765, ProtocolVersion.serverProtocolFor(
                ProtocolVersion.DEFAULT_MINECRAFT_VERSION));
    }
}
