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

/** Tests for the 1.16.3 internal server protocol (753) in {@link ProtocolVersion}. */
class ProtocolVersion753Test {

    @Test
    void serverProtocolFor1163Is753() {
        assertEquals(753, ProtocolVersion.serverProtocolFor("1.16.3"));
        assertEquals(ProtocolVersion.V1_16_3, ProtocolVersion.serverProtocolFor("1.16.3"));
    }

    @Test
    void serverProtocolFor1163LogsNoWarning() {
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
            assertEquals(753, ProtocolVersion.serverProtocolFor("1.16.3"));
        } finally {
            logger.removeHandler(handler);
        }
        assertTrue(warnings.isEmpty(),
                "1.16.3 must not warn: " + warnings);
    }

    @Test
    void latestKnownIsStill775AndDefaultIsUnchanged() {
        // 753 の追加では LATEST_KNOWN は変わらない（775＝26.1 のまま）。
        assertEquals(775, ProtocolVersion.LATEST_KNOWN);
        assertEquals(ProtocolVersion.V26_1, ProtocolVersion.LATEST_KNOWN);
        // The default server version stays 1.20.4 (765): 1.16.3 is opt-in
        // via minecraft-version, not the new default.
        assertEquals("1.20.4", ProtocolVersion.DEFAULT_MINECRAFT_VERSION);
        assertEquals(765, ProtocolVersion.DEFAULT_SERVER_PROTOCOL);
    }
}
