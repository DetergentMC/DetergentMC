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

/** Tests for the 1.18.2 internal server protocol (758) in {@link ProtocolVersion}. */
class ProtocolVersion758Test {

    @Test
    void serverProtocolFor1182Is758() {
        assertEquals(758, ProtocolVersion.serverProtocolFor("1.18.2"));

        assertEquals(ProtocolVersion.V1_18_2, ProtocolVersion.serverProtocolFor("1.18.2"));
    }

    @Test
    void serverProtocolFor1182LogsNoWarning() {
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
            assertEquals(758, ProtocolVersion.serverProtocolFor("1.18.2"));
        } finally {
            logger.removeHandler(handler);
        }
        assertTrue(warnings.isEmpty(),
                "1.18.2 must not warn: " + warnings);
    }

    @Test
    void oldestSupportedIs758AndDefaultIsUnchanged() {
        assertEquals(758, ProtocolVersion.V1_18_2);
        // LATEST_KNOWN は 776（26.2）。758 は最古の対応プロトコル
        // として追加され、最新側は 776 のまま。
        assertEquals(776, ProtocolVersion.LATEST_KNOWN);
        assertEquals(ProtocolVersion.V26_2, ProtocolVersion.LATEST_KNOWN);
        // The default server version stays 1.20.4 (765): 1.18.2 is opt-in
        // via minecraft-version, not the new default.
        assertEquals("1.20.4", ProtocolVersion.DEFAULT_MINECRAFT_VERSION);
        assertEquals(765, ProtocolVersion.DEFAULT_SERVER_PROTOCOL);
        assertEquals(765, ProtocolVersion.serverProtocolFor(
                ProtocolVersion.DEFAULT_MINECRAFT_VERSION));
    }
}
