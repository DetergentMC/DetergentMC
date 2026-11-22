package io.detergentmc.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.detergentmc.protocol.ClientAdapter.Decision;
import io.detergentmc.protocol.ClientAdapter.Handshake;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 335（1.12）クライアントの受け入れ判断。このツリーで最古の対応プロトコル（降下の最終版）。 */
class ClientAdapter335Test {

    @Test
    void decide335IsAcceptedViaAdapter() {
        Handshake handshake = ClientAdapter.decide(335);
        assertEquals(Decision.ACCEPT_VIA_ADAPTER, handshake.decision());
        assertTrue(handshake.accepted());
        assertTrue(handshake.needsTranslation());
        assertEquals(335, handshake.clientProtocol());
        assertTrue(handshake.message().contains("335"), handshake.message());
    }

    @Test
    void decide335AgainstAllServerProtocols() {
        for (int server : new int[]{335, 338, 340, 393, 401, 404, 477, 480, 485, 490, 498, 573, 575, 578, 758, 759, 760, 761, 762, 763, 764, 765, 766, 767, 768, 769, 770, 771, 772, 773, 774, 775, 776}) {
            Handshake handshake = ClientAdapter.decide(335, server);
            assertTrue(handshake.accepted(), "server=" + server);
            if (server == 335) {
                assertEquals(Decision.ACCEPT_NATIVE, handshake.decision());
                assertFalse(handshake.needsTranslation());
            } else {
                assertEquals(Decision.ACCEPT_VIA_ADAPTER, handshake.decision());
                assertTrue(handshake.needsTranslation());
            }
        }
        // 338・340クライアントも内部335に対して受け入れる（338・340 が
        // 一覧から脱落していないことの確認を兼ねる）。
        assertTrue(ClientAdapter.decide(338, 335).accepted());
        assertTrue(ClientAdapter.decide(340, 335).accepted());
        // 777より新しい／335より古い／間の未対応帯は断る。
        assertFalse(ClientAdapter.decide(777, 335).accepted());
        assertFalse(ClientAdapter.decide(334, 335).accepted());
        assertFalse(ClientAdapter.decide(336, 335).accepted());
        assertFalse(ClientAdapter.decide(337, 335).accepted());
        assertFalse(ClientAdapter.decide(339, 335).accepted());
        assertFalse(ClientAdapter.decide(341, 335).accepted());
        assertFalse(ClientAdapter.decide(392, 335).accepted());
    }

    @Test
    void versionName335Is112() {
        assertEquals("1.12", ClientAdapter.versionName(335));
    }

    @Test
    void oldestSupportedIs335And338And340AreStillListed() {
        assertEquals(335, ClientAdapter.OLDEST_SUPPORTED_CLIENT_PROTOCOL);
        List<Integer> supported = ClientAdapter.supportedClientProtocols();
        assertEquals(335, supported.get(0), supported.toString());
        // 最古を 335 に下げても 338・340・393・401・404 が一覧から脱落して
        // いないこと（404 のときに 477 で、401／338 のときに旧最古で踏んだ
        // 地雷の回帰確認。338 は OLDEST の差し替えで脱落しやすいため
        // 明示を固定する）。
        assertEquals(338, supported.get(1), supported.toString());
        assertEquals(340, supported.get(2), supported.toString());
        assertEquals(393, supported.get(3), supported.toString());
        assertEquals(401, supported.get(4), supported.toString());
        assertEquals(404, supported.get(5), supported.toString());
        assertEquals(477, supported.get(6), supported.toString());
        assertEquals(33, supported.size(), supported.toString());
        assertTrue(supported.contains(338), supported.toString());
        assertTrue(supported.contains(340), supported.toString());
        assertTrue(supported.contains(393), supported.toString());
        assertTrue(supported.contains(401), supported.toString());
        assertTrue(supported.contains(404), supported.toString());
        assertTrue(supported.contains(758), supported.toString());
    }

    @Test
    void differencesFor335Exist() {
        assertFalse(ClientAdapter.differences(335).isEmpty());
    }
}
