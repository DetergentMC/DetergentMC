package io.detergentmc.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.detergentmc.protocol.ClientAdapter.Decision;
import io.detergentmc.protocol.ClientAdapter.Handshake;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 401（1.13.1）クライアントの受け入れ判断。このツリーで最古の対応プロトコル。 */
class ClientAdapter401Test {

    @Test
    void decide401IsAcceptedViaAdapter() {
        Handshake handshake = ClientAdapter.decide(401);
        assertEquals(Decision.ACCEPT_VIA_ADAPTER, handshake.decision());
        assertTrue(handshake.accepted());
        assertTrue(handshake.needsTranslation());
        assertEquals(401, handshake.clientProtocol());
        assertTrue(handshake.message().contains("401"), handshake.message());
    }

    @Test
    void decide401AgainstAllServerProtocols() {
        for (int server : new int[]{401, 404, 477, 480, 485, 490, 498, 573, 575, 578, 758, 759, 760, 761, 762, 763, 764, 765, 766, 767, 768, 769, 770, 771, 772, 773, 774, 775, 776}) {
            Handshake handshake = ClientAdapter.decide(401, server);
            assertTrue(handshake.accepted(), "server=" + server);
            if (server == 401) {
                assertEquals(Decision.ACCEPT_NATIVE, handshake.decision());
                assertFalse(handshake.needsTranslation());
            } else {
                assertEquals(Decision.ACCEPT_VIA_ADAPTER, handshake.decision());
                assertTrue(handshake.needsTranslation());
            }
        }
        // 777より新しい／401より古い／間の未対応帯は断る。
        assertFalse(ClientAdapter.decide(777, 401).accepted());
        assertFalse(ClientAdapter.decide(400, 401).accepted());
        assertFalse(ClientAdapter.decide(402, 401).accepted());
        assertFalse(ClientAdapter.decide(403, 401).accepted());
        assertFalse(ClientAdapter.decide(476, 401).accepted());
        assertFalse(ClientAdapter.decide(339, 401).accepted());
        // 335（1.12）は対応済みになったため受け入れる。
        assertTrue(ClientAdapter.decide(335, 401).accepted());
        assertFalse(ClientAdapter.decide(334, 401).accepted());
    }

    @Test
    void versionName401Is1131() {
        assertEquals("1.13.1", ClientAdapter.versionName(401));
    }

    @Test
    void oldestSupportedIs401And404IsStillListed() {
        assertEquals(335, ClientAdapter.OLDEST_SUPPORTED_CLIENT_PROTOCOL);
        List<Integer> supported = ClientAdapter.supportedClientProtocols();
        assertEquals(335, supported.get(0), supported.toString());
        assertEquals(338, supported.get(1), supported.toString());
        assertEquals(340, supported.get(2), supported.toString());
        assertEquals(393, supported.get(3), supported.toString());
        assertEquals(401, supported.get(4), supported.toString());
        // 最古を 393 に下げても 401・404 が一覧から脱落していないこと
        // （404 のときに 477 で踏んだ地雷の回帰確認）。
        assertEquals(404, supported.get(5), supported.toString());
        assertEquals(477, supported.get(6), supported.toString());
        assertEquals(33, supported.size(), supported.toString());
        assertTrue(supported.contains(404), supported.toString());
        assertTrue(supported.contains(758), supported.toString());
    }

    @Test
    void differencesFor401Exist() {
        assertFalse(ClientAdapter.differences(401).isEmpty());
    }
}
