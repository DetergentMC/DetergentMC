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
        assertFalse(ClientAdapter.decide(340, 498).accepted());
    }

    @Test
    void versionName498Is1144() {
        assertEquals("1.14.4", ClientAdapter.versionName(498));
    }

    @Test
    void oldestSupportedIs480() {
        // 480（1.14.1）対応で最古は 480 になった。485 は2番目、490 は3番目、498 は4番目。
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
    void differencesFor498Exist() {
        assertFalse(ClientAdapter.differences(498).isEmpty());
    }
}
