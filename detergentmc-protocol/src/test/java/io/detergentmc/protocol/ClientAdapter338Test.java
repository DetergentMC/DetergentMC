package io.detergentmc.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.detergentmc.protocol.ClientAdapter.Decision;
import io.detergentmc.protocol.ClientAdapter.Handshake;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 338（1.12.1）クライアントの受け入れ判断。このツリーで最古の対応プロトコル。 */
class ClientAdapter338Test {

    @Test
    void decide338IsAcceptedViaAdapter() {
        Handshake handshake = ClientAdapter.decide(338);
        assertEquals(Decision.ACCEPT_VIA_ADAPTER, handshake.decision());
        assertTrue(handshake.accepted());
        assertTrue(handshake.needsTranslation());
        assertEquals(338, handshake.clientProtocol());
        assertTrue(handshake.message().contains("338"), handshake.message());
    }

    @Test
    void decide338AgainstAllServerProtocols() {
        for (int server : new int[]{338, 340, 393, 401, 404, 477, 480, 485, 490, 498, 573, 575, 578, 758, 759, 760, 761, 762, 763, 764, 765, 766, 767, 768, 769, 770, 771, 772, 773, 774, 775, 776}) {
            Handshake handshake = ClientAdapter.decide(338, server);
            assertTrue(handshake.accepted(), "server=" + server);
            if (server == 338) {
                assertEquals(Decision.ACCEPT_NATIVE, handshake.decision());
                assertFalse(handshake.needsTranslation());
            } else {
                assertEquals(Decision.ACCEPT_VIA_ADAPTER, handshake.decision());
                assertTrue(handshake.needsTranslation());
            }
        }
        // 340クライアントも内部338に対して受け入れる（340 が一覧から
        // 脱落していないことの確認を兼ねる）。
        assertTrue(ClientAdapter.decide(340, 338).accepted());
        // 777より新しい／338より古い／間の未対応帯は断る。
        assertFalse(ClientAdapter.decide(777, 338).accepted());
        assertFalse(ClientAdapter.decide(337, 338).accepted());
        assertFalse(ClientAdapter.decide(339, 338).accepted());
        assertFalse(ClientAdapter.decide(341, 338).accepted());
        assertFalse(ClientAdapter.decide(392, 338).accepted());
        // 335（1.12）は対応済みになったため、内部338に対しても受け入れる。
        assertTrue(ClientAdapter.decide(335, 338).accepted());
        assertFalse(ClientAdapter.decide(334, 338).accepted());
    }

    @Test
    void versionName338Is1121() {
        assertEquals("1.12.1", ClientAdapter.versionName(338));
    }

    @Test
    void oldestSupportedIs338And340IsStillListed() {
        assertEquals(335, ClientAdapter.OLDEST_SUPPORTED_CLIENT_PROTOCOL);
        List<Integer> supported = ClientAdapter.supportedClientProtocols();
        assertEquals(335, supported.get(0), supported.toString());
        assertEquals(338, supported.get(1), supported.toString());
        // 最古を 338 に下げても 340・393・401・404 が一覧から脱落して
        // いないこと（404 のときに 477 で踏んだ地雷の回帰確認。340 は
        // OLDEST の差し替えで脱落しやすいため明示を固定する）。
        assertEquals(340, supported.get(2), supported.toString());
        assertEquals(393, supported.get(3), supported.toString());
        assertEquals(401, supported.get(4), supported.toString());
        assertEquals(404, supported.get(5), supported.toString());
        assertEquals(477, supported.get(6), supported.toString());
        assertEquals(33, supported.size(), supported.toString());
        assertTrue(supported.contains(340), supported.toString());
        assertTrue(supported.contains(393), supported.toString());
        assertTrue(supported.contains(401), supported.toString());
        assertTrue(supported.contains(404), supported.toString());
        assertTrue(supported.contains(758), supported.toString());
    }

    @Test
    void differencesFor338Exist() {
        assertFalse(ClientAdapter.differences(338).isEmpty());
    }
}
