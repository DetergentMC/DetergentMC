package io.detergentmc.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.detergentmc.protocol.ClientAdapter.Decision;
import io.detergentmc.protocol.ClientAdapter.Handshake;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 578（1.15.2）クライアントの受け入れ判断。このツリーで最古の対応プロトコル。 */
class ClientAdapter578Test {

    @Test
    void decide578IsAcceptedViaAdapter() {
        Handshake handshake = ClientAdapter.decide(578);
        assertEquals(Decision.ACCEPT_VIA_ADAPTER, handshake.decision());
        assertTrue(handshake.accepted());
        assertTrue(handshake.needsTranslation());
        assertEquals(578, handshake.clientProtocol());
        assertTrue(handshake.message().contains("578"), handshake.message());
    }

    @Test
    void decide578AgainstAllServerProtocols() {
        for (int server : new int[]{578, 758, 759, 760, 761, 762, 763, 764, 765, 766, 767, 768, 769, 770, 771, 772, 773, 774, 775, 776}) {
            Handshake handshake = ClientAdapter.decide(578, server);
            assertTrue(handshake.accepted(), "server=" + server);
            if (server == 578) {
                assertEquals(Decision.ACCEPT_NATIVE, handshake.decision());
                assertFalse(handshake.needsTranslation());
            } else {
                assertEquals(Decision.ACCEPT_VIA_ADAPTER, handshake.decision());
                assertTrue(handshake.needsTranslation());
            }
        }
        // 777より新しい／577より古いは断る。
        assertFalse(ClientAdapter.decide(777, 578).accepted());
        assertFalse(ClientAdapter.decide(577, 578).accepted());
        assertFalse(ClientAdapter.decide(339, 578).accepted());
    }

    @Test
    void versionName578Is1152() {
        assertEquals("1.15.2", ClientAdapter.versionName(578));
    }

    @Test
    void oldestSupportedIs573And578IsThird() {
        // 477（1.14）対応で最古は 477 になった。並びは 477・480・485・490・498・573・575・578…。
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
        assertEquals(498, supported.get(10), supported.toString());
        assertEquals(573, supported.get(11), supported.toString());
        assertEquals(575, supported.get(12), supported.toString());
        assertEquals(578, supported.get(13), supported.toString());
        assertEquals(33, supported.size(), supported.toString());
        assertTrue(supported.contains(758), supported.toString());
    }

    @Test
    void differencesFor578Exist() {
        assertFalse(ClientAdapter.differences(578).isEmpty());
    }
}
