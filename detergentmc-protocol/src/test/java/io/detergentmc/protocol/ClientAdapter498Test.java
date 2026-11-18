package io.detergentmc.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.detergentmc.protocol.ClientAdapter.Decision;
import io.detergentmc.protocol.ClientAdapter.Handshake;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 498（1.14.4）クライアントの受け入れ判断。このツリーで最古の対応プロトコル。 */
class ClientAdapter498Test {

    @Test
    void decide498IsAcceptedViaAdapter() {
        Handshake handshake = ClientAdapter.decide(498);
        assertEquals(Decision.ACCEPT_VIA_ADAPTER, handshake.decision());
        assertTrue(handshake.accepted());
        assertTrue(handshake.needsTranslation());
        assertEquals(498, handshake.clientProtocol());
        assertTrue(handshake.message().contains("498"), handshake.message());
    }

    @Test
    void decide498AgainstAllServerProtocols() {
        for (int server : new int[]{498, 573, 575, 578, 758, 759, 760, 761, 762, 763, 764, 765, 766, 767, 768, 769, 770, 771, 772, 773, 774, 775, 776}) {
            Handshake handshake = ClientAdapter.decide(498, server);
            assertTrue(handshake.accepted(), "server=" + server);
            if (server == 498) {
                assertEquals(Decision.ACCEPT_NATIVE, handshake.decision());
                assertFalse(handshake.needsTranslation());
            } else {
                assertEquals(Decision.ACCEPT_VIA_ADAPTER, handshake.decision());
                assertTrue(handshake.needsTranslation());
            }
        }
        // 777より新しい／498より古い／間の未対応帯は断る。
        assertFalse(ClientAdapter.decide(777, 498).accepted());
        assertFalse(ClientAdapter.decide(574, 498).accepted());
        assertFalse(ClientAdapter.decide(497, 498).accepted());
        assertFalse(ClientAdapter.decide(339, 498).accepted());
    }

    @Test
    void versionName498Is1144() {
        assertEquals("1.14.4", ClientAdapter.versionName(498));
    }

    @Test
    void oldestSupportedIs477() {
        // 477（1.14）対応で最古は 477 になった。480 は2番目、485 は3番目、490 は4番目、498 は5番目。
        assertEquals(335, ClientAdapter.OLDEST_SUPPORTED_CLIENT_PROTOCOL);
        List<Integer> supported = ClientAdapter.supportedClientProtocols();
        assertEquals(335, supported.get(0), supported.toString());
        assertEquals(338, supported.get(1), supported.toString());
        assertEquals(340, supported.get(2), supported.toString());
        assertEquals(393, supported.get(3), supported.toString());
        assertEquals(401, supported.get(4), supported.toString());
        assertEquals(404, supported.get(5), supported.toString());
        assertEquals(477, supported.get(6), supported.toString());
        assertEquals(480, supported.get(7), supported.toString());
        assertEquals(485, supported.get(8), supported.toString());
        assertEquals(490, supported.get(9), supported.toString());
        assertEquals(33, supported.size(), supported.toString());
        assertTrue(supported.contains(758), supported.toString());
    }

    @Test
    void differencesFor498Exist() {
        assertFalse(ClientAdapter.differences(498).isEmpty());
    }
}
