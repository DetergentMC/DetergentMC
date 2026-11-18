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

/** Tests for the 1.16.4 / 1.16.5 internal server protocol (754) in {@link ProtocolVersion}. */
class ProtocolVersion754Test {

    @Test
    void serverProtocolFor1165Is754() {
        assertEquals(754, ProtocolVersion.serverProtocolFor("1.16.5"));
        assertEquals(ProtocolVersion.V1_16_5, ProtocolVersion.serverProtocolFor("1.16.5"));
        // 1.16.4 and 1.16.5 share protocol 754 (version.json実測).
        assertEquals(754, ProtocolVersion.serverProtocolFor("1.16.4"));
        assertEquals(ProtocolVersion.V1_16_5, ProtocolVersion.serverProtocolFor("1.16.4"));
    }

    @Test
    void serverProtocolFor1165LogsNoWarning() {
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
            assertEquals(754, ProtocolVersion.serverProtocolFor("1.16.5"));
            assertEquals(754, ProtocolVersion.serverProtocolFor("1.16.4"));
        } finally {
            logger.removeHandler(handler);
        }
        assertTrue(warnings.isEmpty(),
                "1.16.5 / 1.16.4 must not warn: " + warnings);
    }

    @Test
    void latestKnownIsStill775AndDefaultIsUnchanged() {
        // 754 の追加では LATEST_KNOWN は変わらない（775＝26.1 のまま）。
        assertEquals(775, ProtocolVersion.LATEST_KNOWN);
        assertEquals(ProtocolVersion.V26_1, ProtocolVersion.LATEST_KNOWN);
        // The default server version stays 1.20.4 (765): 1.16.5 is opt-in
        // via minecraft-version, not the new default.
        assertEquals("1.20.4", ProtocolVersion.DEFAULT_MINECRAFT_VERSION);
        assertEquals(765, ProtocolVersion.DEFAULT_SERVER_PROTOCOL);
    }
}
