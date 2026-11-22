package io.detergentmc.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.detergentmc.protocol.ClientAdapter.Decision;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 5（1.7.6〜1.7.10）クライアントの受け入れと対応一覧。
 * 最古が 4（1.7.2〜1.7.5）へ移ったあとも 5 が一覧から脱落して
 * いないことを固定する（最古脱落の罠の回帰防止）。
 */
class ClientAdapter5Test {

    @Test
    void oldestIsNow4And5IsStillListed() {
        assertEquals(4, ClientAdapter.OLDEST_SUPPORTED_CLIENT_PROTOCOL);
        assertEquals(ProtocolVersion.V1_7_2,
                ClientAdapter.OLDEST_SUPPORTED_CLIENT_PROTOCOL);
        List<Integer> supported = ClientAdapter.supportedClientProtocols();
        assertEquals(5, supported.get(1), supported.toString());
    }

    @Test
    void supportedListStartsWith4AndKeeps5AndAllPrevious() {
        List<Integer> supported = ClientAdapter.supportedClientProtocols();
        assertEquals(4, supported.get(0), supported.toString());
        for (int protocol : new int[] {5, 47, 107, 108, 109, 110, 210,
                315, 316, 335, 338, 340, 393, 401, 404, 477, 758, 763,
                765, 776}) {
            assertTrue(supported.contains(protocol),
                    protocol + " が対応一覧から脱落している: " + supported);
        }
        assertEquals(51, supported.size(), supported.toString());
    }

    @Test
    void versionName5() {
        assertEquals("1.7.10", ClientAdapter.versionName(5));
        assertEquals("1.7.2", ClientAdapter.versionName(4));
        assertEquals("1.8", ClientAdapter.versionName(47));
    }

    @Test
    void decideAccepts5ViaAdapter() {
        ClientAdapter.Handshake handshake = ClientAdapter.decide(5, 765);
        assertEquals(Decision.ACCEPT_VIA_ADAPTER, handshake.decision());
        assertFalse(handshake.message().isEmpty());
    }

    @Test
    void decideAccepts4Now() {
        // 4（1.7.2〜1.7.5）は 4 の段で対応済み。
        ClientAdapter.Handshake handshake = ClientAdapter.decide(4, 765);
        assertEquals(Decision.ACCEPT_VIA_ADAPTER, handshake.decision());
        // プロトコル3以下は Netty 以前の別世代で、まだ受け入れない。
        assertEquals(Decision.REJECT_OUTDATED,
                ClientAdapter.decide(3, 765).decision());
    }
}
