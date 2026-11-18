package io.detergentmc.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.detergentmc.protocol.ClientAdapter.Decision;
import io.detergentmc.protocol.ClientAdapter.Handshake;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 485（1.14.2）クライアントの受け入れ判断。このツリーで最古の対応プロトコル。 */
class ClientAdapter485Test {

    @Test
    void decide485IsAcceptedViaAdapter() {
        Handshake handshake = ClientAdapter.decide(485);
        assertEquals(Decision.ACCEPT_VIA_ADAPTER, handshake.decision());
        assertTrue(handshake.accepted());
        assertTrue(handshake.needsTranslation());
        assertEquals(485, handshake.clientProtocol());
        assertTrue(handshake.message().contains("485"), handshake.message());
    }

    @Test
    void decide485AgainstAllServerProtocols() {
        for (int server : new int[]{477, 480, 485, 490, 498, 573, 575, 578, 758, 759, 760, 761, 762, 763, 764, 765, 766, 767, 768, 769, 770, 771, 772, 773, 774, 775, 776}) {
            Handshake handshake = ClientAdapter.decide(485, server);
            assertTrue(handshake.accepted(), "server=" + server);
            if (server == 485) {
                assertEquals(Decision.ACCEPT_NATIVE, handshake.decision());
                assertFalse(handshake.needsTranslation());
            } else {
                assertEquals(Decision.ACCEPT_VIA_ADAPTER, handshake.decision());
                assertTrue(handshake.needsTranslation());
            }
        }
        // 777より新しい／485より古い／間の未対応帯は断る。
        assertFalse(ClientAdapter.decide(777, 485).accepted());
        assertFalse(ClientAdapter.decide(574, 485).accepted());
        assertFalse(ClientAdapter.decide(489, 485).accepted());
        assertFalse(ClientAdapter.decide(484, 485).accepted());
        assertFalse(ClientAdapter.decide(339, 485).accepted());
    }

    @Test
    void versionName485Is1142() {
        assertEquals("1.14.2", ClientAdapter.versionName(485));
    }

    @Test
    void oldestSupportedIs477() {
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
    void differencesFor485Exist() {
        assertFalse(ClientAdapter.differences(485).isEmpty());
    }
}
