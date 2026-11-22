package io.detergentmc.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.detergentmc.protocol.ClientAdapter.Decision;
import io.detergentmc.protocol.ClientAdapter.Handshake;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 393（1.13）クライアントの受け入れ判断。このツリーで最古の対応プロトコル。 */
class ClientAdapter393Test {

    @Test
    void decide393IsAcceptedViaAdapter() {
        Handshake handshake = ClientAdapter.decide(393);
        assertEquals(Decision.ACCEPT_VIA_ADAPTER, handshake.decision());
        assertTrue(handshake.accepted());
        assertTrue(handshake.needsTranslation());
        assertEquals(393, handshake.clientProtocol());
        assertTrue(handshake.message().contains("393"), handshake.message());
    }

    @Test
    void decide393AgainstAllServerProtocols() {
        for (int server : new int[]{393, 401, 404, 477, 480, 485, 490, 498, 573, 575, 578, 735, 736, 751, 753, 754, 755, 756, 757, 758, 759, 760, 761, 762, 763, 764, 765, 766, 767, 768, 769, 770, 771, 772, 773, 774, 775, 776}) {
            Handshake handshake = ClientAdapter.decide(393, server);
            assertTrue(handshake.accepted(), "server=" + server);
            if (server == 393) {
                assertEquals(Decision.ACCEPT_NATIVE, handshake.decision());
                assertFalse(handshake.needsTranslation());
            } else {
                assertEquals(Decision.ACCEPT_VIA_ADAPTER, handshake.decision());
                assertTrue(handshake.needsTranslation());
            }
        }
        // 777より新しい／393より古い／間の未対応帯は断る。
        assertFalse(ClientAdapter.decide(777, 393).accepted());
        assertFalse(ClientAdapter.decide(392, 393).accepted());
        assertFalse(ClientAdapter.decide(394, 393).accepted());
        assertFalse(ClientAdapter.decide(400, 393).accepted());
        assertFalse(ClientAdapter.decide(402, 393).accepted());
        assertFalse(ClientAdapter.decide(476, 393).accepted());
        assertFalse(ClientAdapter.decide(339, 393).accepted());
        // 335（1.12）は対応済みになったため受け入れる。
        assertTrue(ClientAdapter.decide(335, 393).accepted());
        assertFalse(ClientAdapter.decide(334, 393).accepted());
    }

    @Test
    void versionName393Is113() {
        assertEquals("1.13", ClientAdapter.versionName(393));
    }

    @Test
    void oldestMovedTo340But393And401And404AreStillListed() {
        // 最古は 340 へ移ったが、393・401・404 が一覧から脱落していない
        // こと（404 のときに 477 で踏んだ地雷の回帰確認。393 は OLDEST の
        // 差し替えで脱落しやすいため明示を固定する）。
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
        assertEquals(51, supported.size(), supported.toString());
        assertTrue(supported.contains(401), supported.toString());
        assertTrue(supported.contains(404), supported.toString());
        assertTrue(supported.contains(758), supported.toString());
    }

    @Test
    void differencesFor393Exist() {
        assertFalse(ClientAdapter.differences(393).isEmpty());
    }
}
