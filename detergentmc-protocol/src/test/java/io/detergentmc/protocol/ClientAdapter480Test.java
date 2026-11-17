package io.detergentmc.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.detergentmc.protocol.ClientAdapter.Decision;
import io.detergentmc.protocol.ClientAdapter.Handshake;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 480（1.14.1）クライアントの受け入れ判断。このツリーで最古の対応プロトコル。 */
class ClientAdapter480Test {

    @Test
    void decide480IsAcceptedViaAdapter() {
        Handshake handshake = ClientAdapter.decide(480);
        assertEquals(Decision.ACCEPT_VIA_ADAPTER, handshake.decision());
        assertTrue(handshake.accepted());
        assertTrue(handshake.needsTranslation());
        assertEquals(480, handshake.clientProtocol());
        assertTrue(handshake.message().contains("480"), handshake.message());
    }

    @Test
    void decide480AgainstAllServerProtocols() {
        for (int server : new int[]{480, 485, 490, 498, 573, 575, 578, 758, 759, 760, 761, 762, 763, 764, 765, 766, 767, 768, 769, 770, 771, 772, 773, 774, 775, 776}) {
            Handshake handshake = ClientAdapter.decide(480, server);
            assertTrue(handshake.accepted(), "server=" + server);
            if (server == 480) {
                assertEquals(Decision.ACCEPT_NATIVE, handshake.decision());
                assertFalse(handshake.needsTranslation());
            } else {
                assertEquals(Decision.ACCEPT_VIA_ADAPTER, handshake.decision());
                assertTrue(handshake.needsTranslation());
            }
        }
        // 777より新しい／480より古い／間の未対応帯は断る。
        assertFalse(ClientAdapter.decide(777, 480).accepted());
        assertFalse(ClientAdapter.decide(574, 480).accepted());
        assertFalse(ClientAdapter.decide(489, 480).accepted());
        assertFalse(ClientAdapter.decide(484, 480).accepted());
        assertFalse(ClientAdapter.decide(479, 480).accepted());
        assertFalse(ClientAdapter.decide(340, 480).accepted());
    }

    @Test
    void versionName480Is1141() {
        assertEquals("1.14.1", ClientAdapter.versionName(480));
    }

    @Test
    void oldestSupportedIs480() {
        assertEquals(480, ClientAdapter.OLDEST_SUPPORTED_CLIENT_PROTOCOL);
        List<Integer> supported = ClientAdapter.supportedClientProtocols();
        assertEquals(480, supported.get(0), supported.toString());
        assertEquals(485, supported.get(1), supported.toString());
        assertEquals(490, supported.get(2), supported.toString());
        assertEquals(26, supported.size(), supported.toString());
        assertTrue(supported.contains(758), supported.toString());
    }

    @Test
    void differencesFor480Exist() {
        assertFalse(ClientAdapter.differences(480).isEmpty());
    }
}
