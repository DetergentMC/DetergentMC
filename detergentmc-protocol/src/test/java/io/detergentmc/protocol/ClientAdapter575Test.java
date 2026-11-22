package io.detergentmc.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.detergentmc.protocol.ClientAdapter.Decision;
import io.detergentmc.protocol.ClientAdapter.Handshake;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 575（1.15.1）クライアントの受け入れ判断。このツリーで最古の対応プロトコル。 */
class ClientAdapter575Test {

    @Test
    void decide575IsAcceptedViaAdapter() {
        Handshake handshake = ClientAdapter.decide(575);
        assertEquals(Decision.ACCEPT_VIA_ADAPTER, handshake.decision());
        assertTrue(handshake.accepted());
        assertTrue(handshake.needsTranslation());
        assertEquals(575, handshake.clientProtocol());
        assertTrue(handshake.message().contains("575"), handshake.message());
    }

    @Test
    void decide575AgainstAllServerProtocols() {
        for (int server : new int[]{575, 578, 758, 759, 760, 761, 762, 763, 764, 765, 766, 767, 768, 769, 770, 771, 772, 773, 774, 775, 776}) {
            Handshake handshake = ClientAdapter.decide(575, server);
            assertTrue(handshake.accepted(), "server=" + server);
            if (server == 575) {
                assertEquals(Decision.ACCEPT_NATIVE, handshake.decision());
                assertFalse(handshake.needsTranslation());
            } else {
                assertEquals(Decision.ACCEPT_VIA_ADAPTER, handshake.decision());
                assertTrue(handshake.needsTranslation());
            }
        }
        // 777より新しい／574より古いは断る。
        assertFalse(ClientAdapter.decide(777, 575).accepted());
        assertFalse(ClientAdapter.decide(574, 575).accepted());
        assertFalse(ClientAdapter.decide(339, 575).accepted());
    }

    @Test
    void versionName575Is1151() {
        assertEquals("1.15.1", ClientAdapter.versionName(575));
    }

    @Test
    void oldestSupportedIs573() {
        assertEquals(4, ClientAdapter.OLDEST_SUPPORTED_CLIENT_PROTOCOL);
        List<Integer> supported = ClientAdapter.supportedClientProtocols();
        assertEquals(4, supported.get(0), supported.toString());
        assertEquals(5, supported.get(1), supported.toString());
        assertEquals(47, supported.get(2), supported.toString());
        assertEquals(107, supported.get(3), supported.toString());
        assertEquals(108, supported.get(4), supported.toString());
        assertEquals(109, supported.get(5), supported.toString());
        assertEquals(110, supported.get(6), supported.toString());
        assertEquals(210, supported.get(7), supported.toString());
        assertEquals(315, supported.get(8), supported.toString());
        assertEquals(316, supported.get(9), supported.toString());
        assertEquals(335, supported.get(10), supported.toString());
        assertEquals(338, supported.get(11), supported.toString());
        assertEquals(393, supported.get(13), supported.toString());
        assertEquals(401, supported.get(14), supported.toString());
        assertEquals(404, supported.get(15), supported.toString());
        assertEquals(477, supported.get(16), supported.toString());
        assertEquals(480, supported.get(17), supported.toString());
        assertEquals(485, supported.get(18), supported.toString());
        assertEquals(490, supported.get(19), supported.toString());
        assertEquals(51, supported.size(), supported.toString());
        assertTrue(supported.contains(758), supported.toString());
    }

    @Test
    void differencesFor575Exist() {
        assertFalse(ClientAdapter.differences(575).isEmpty());
    }
}
