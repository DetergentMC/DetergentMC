package io.detergentmc.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.detergentmc.protocol.ClientAdapter.Decision;
import io.detergentmc.protocol.ClientAdapter.Handshake;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 573（1.15）クライアントの受け入れ判断。このツリーで最古の対応プロトコル。 */
class ClientAdapter573Test {

    @Test
    void decide573IsAcceptedViaAdapter() {
        Handshake handshake = ClientAdapter.decide(573);
        assertEquals(Decision.ACCEPT_VIA_ADAPTER, handshake.decision());
        assertTrue(handshake.accepted());
        assertTrue(handshake.needsTranslation());
        assertEquals(573, handshake.clientProtocol());
        assertTrue(handshake.message().contains("573"), handshake.message());
    }

    @Test
    void decide573AgainstAllServerProtocols() {
        for (int server : new int[]{573, 575, 578, 758, 759, 760, 761, 762, 763, 764, 765, 766, 767, 768, 769, 770, 771, 772, 773, 774, 775, 776}) {
            Handshake handshake = ClientAdapter.decide(573, server);
            assertTrue(handshake.accepted(), "server=" + server);
            if (server == 573) {
                assertEquals(Decision.ACCEPT_NATIVE, handshake.decision());
                assertFalse(handshake.needsTranslation());
            } else {
                assertEquals(Decision.ACCEPT_VIA_ADAPTER, handshake.decision());
                assertTrue(handshake.needsTranslation());
            }
        }
        // 777より新しい／574より古いは断る。
        assertFalse(ClientAdapter.decide(777, 573).accepted());
        assertFalse(ClientAdapter.decide(574, 573).accepted());
        assertFalse(ClientAdapter.decide(340, 573).accepted());
    }

    @Test
    void versionName573Is115() {
        assertEquals("1.15", ClientAdapter.versionName(573));
    }

    @Test
    void oldestSupportedIs573() {
        assertEquals(480, ClientAdapter.OLDEST_SUPPORTED_CLIENT_PROTOCOL);
        List<Integer> supported = ClientAdapter.supportedClientProtocols();
        assertEquals(480, supported.get(0), supported.toString());
        assertEquals(485, supported.get(1), supported.toString());
        assertEquals(490, supported.get(2), supported.toString());
        assertEquals(498, supported.get(3), supported.toString());
        assertEquals(26, supported.size(), supported.toString());
        assertTrue(supported.contains(758), supported.toString());
    }

    @Test
    void differencesFor573Exist() {
        assertFalse(ClientAdapter.differences(573).isEmpty());
    }
}
