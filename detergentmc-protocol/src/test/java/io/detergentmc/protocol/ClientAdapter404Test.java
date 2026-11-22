package io.detergentmc.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.detergentmc.protocol.ClientAdapter.Decision;
import io.detergentmc.protocol.ClientAdapter.Handshake;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 404（1.13.2）クライアントの受け入れ判断。このツリーで最古の対応プロトコル。 */
class ClientAdapter404Test {

    @Test
    void decide404IsAcceptedViaAdapter() {
        Handshake handshake = ClientAdapter.decide(404);
        assertEquals(Decision.ACCEPT_VIA_ADAPTER, handshake.decision());
        assertTrue(handshake.accepted());
        assertTrue(handshake.needsTranslation());
        assertEquals(404, handshake.clientProtocol());
        assertTrue(handshake.message().contains("404"), handshake.message());
    }

    @Test
    void decide404AgainstAllServerProtocols() {
        for (int server : new int[]{404, 477, 480, 485, 490, 498, 573, 575, 578, 758, 759, 760, 761, 762, 763, 764, 765, 766, 767, 768, 769, 770, 771, 772, 773, 774, 775, 776}) {
            Handshake handshake = ClientAdapter.decide(404, server);
            assertTrue(handshake.accepted(), "server=" + server);
            if (server == 404) {
                assertEquals(Decision.ACCEPT_NATIVE, handshake.decision());
                assertFalse(handshake.needsTranslation());
            } else {
                assertEquals(Decision.ACCEPT_VIA_ADAPTER, handshake.decision());
                assertTrue(handshake.needsTranslation());
            }
        }
        // 777より新しい／404より古い／間の未対応帯は断る。
        assertFalse(ClientAdapter.decide(777, 404).accepted());
        assertFalse(ClientAdapter.decide(403, 404).accepted());
        assertFalse(ClientAdapter.decide(405, 404).accepted());
        assertFalse(ClientAdapter.decide(476, 404).accepted());
        assertFalse(ClientAdapter.decide(339, 404).accepted());
        // 335（1.12）は対応済みになったため受け入れる。
        assertTrue(ClientAdapter.decide(335, 404).accepted());
        assertFalse(ClientAdapter.decide(334, 404).accepted());
    }

    @Test
    void versionName404Is1132() {
        assertEquals("1.13.2", ClientAdapter.versionName(404));
    }

    @Test
    void oldestSupportedIs404() {
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
        assertEquals(33, supported.size(), supported.toString());
        assertTrue(supported.contains(758), supported.toString());
    }

    @Test
    void differencesFor404Exist() {
        assertFalse(ClientAdapter.differences(404).isEmpty());
    }
}
