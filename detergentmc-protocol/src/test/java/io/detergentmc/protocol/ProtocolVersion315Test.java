package io.detergentmc.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * 315（1.11）のプロトコル番号解決。番号対応の訂正（実バニラの
 * Status 応答とハンドシェイクハンドラのバイトコードで確定）：
 * 315＝1.11、316＝1.11.1／1.11.2 の両方。最古は 315 に移った。
 */
class ProtocolVersion315Test {

    @Test
    void version111Is315() {
        assertEquals(315, ProtocolVersion.V1_11);
        assertEquals(315, ProtocolVersion.serverProtocolFor("1.11"));
    }

    @Test
    void version1111And1112AreBoth316() {
        // 1.11.1 は独立したプロトコルを持たず、1.11.2 と同じ 316。
        // 内部サーバーは 1.11.2 のままで 1.11.1 の相手もできる。
        assertEquals(316, ProtocolVersion.serverProtocolFor("1.11.1"));
        assertEquals(316, ProtocolVersion.serverProtocolFor("1.11.2"));
    }

    @Test
    void knownVersionsUnchanged() {
        assertEquals(335, ProtocolVersion.serverProtocolFor("1.12"));
        assertEquals(393, ProtocolVersion.serverProtocolFor("1.13"));
        assertEquals(776, ProtocolVersion.LATEST_KNOWN);
        assertEquals(765, ProtocolVersion.DEFAULT_SERVER_PROTOCOL);
        assertEquals(765, ProtocolVersion.serverProtocolFor(null));
        assertEquals(765, ProtocolVersion.serverProtocolFor("unknown"));
        assertTrue(PacketIds.isSupported(315));
        assertTrue(PacketIds.isSupported(316));
        // 314 と 317 はこのツリーでは未対応。
        assertFalse(PacketIds.isSupported(314));
        assertFalse(PacketIds.isSupported(317));
    }
}
