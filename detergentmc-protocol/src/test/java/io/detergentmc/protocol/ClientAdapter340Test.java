package io.detergentmc.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.detergentmc.protocol.ClientAdapter.Decision;
import io.detergentmc.protocol.ClientAdapter.Handshake;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 340（1.12.2）クライアントの受け入れ判断。最古は 338 に移ったが、340 が一覧に残ることも固定する。 */
class ClientAdapter340Test {

    @Test
    void decide340IsAcceptedViaAdapter() {
        Handshake handshake = ClientAdapter.decide(340);
        assertEquals(Decision.ACCEPT_VIA_ADAPTER, handshake.decision());
        assertTrue(handshake.accepted());
        assertTrue(handshake.needsTranslation());
        assertEquals(340, handshake.clientProtocol());
        assertTrue(handshake.message().contains("340"), handshake.message());
    }

    @Test
    void decide340AgainstAllServerProtocols() {
        for (int server : new int[]{340, 393, 401, 404, 477, 480, 485, 490, 498, 573, 575, 578, 758, 759, 760, 761, 762, 763, 764, 765, 766, 767, 768, 769, 770, 771, 772, 773, 774, 775, 776}) {
            Handshake handshake = ClientAdapter.decide(340, server);
            assertTrue(handshake.accepted(), "server=" + server);
            if (server == 340) {
                assertEquals(Decision.ACCEPT_NATIVE, handshake.decision());
                assertFalse(handshake.needsTranslation());
            } else {
                assertEquals(Decision.ACCEPT_VIA_ADAPTER, handshake.decision());
                assertTrue(handshake.needsTranslation());
            }
        }
        // 777より新しい／間の未対応帯は断る（338 は対応済みのため含めない）。
        assertFalse(ClientAdapter.decide(777, 340).accepted());
        assertFalse(ClientAdapter.decide(339, 340).accepted());
        assertFalse(ClientAdapter.decide(341, 340).accepted());
        assertFalse(ClientAdapter.decide(392, 340).accepted());
        // 335（1.12）は対応済みになったため受け入れる。
        assertTrue(ClientAdapter.decide(335, 340).accepted());
        assertFalse(ClientAdapter.decide(334, 340).accepted());
    }

    @Test
    void versionName340Is1122() {
        assertEquals("1.12.2", ClientAdapter.versionName(340));
    }

    @Test
    void oldestSupportedIs338And340And393And401And404AreStillListed() {
        assertEquals(335, ClientAdapter.OLDEST_SUPPORTED_CLIENT_PROTOCOL);
        List<Integer> supported = ClientAdapter.supportedClientProtocols();
        assertEquals(335, supported.get(0), supported.toString());
        assertEquals(338, supported.get(1), supported.toString());
        assertEquals(340, supported.get(2), supported.toString());
        // 最古を 338 に下げても 340・393・401・404 が一覧から脱落していない
        // こと（404 のときに 477 で踏んだ地雷の回帰確認。393 は OLDEST の
        // 差し替えで脱落しやすいため明示を固定する）。
        assertEquals(393, supported.get(3), supported.toString());
        assertEquals(401, supported.get(4), supported.toString());
        assertEquals(404, supported.get(5), supported.toString());
        assertEquals(477, supported.get(6), supported.toString());
        assertEquals(33, supported.size(), supported.toString());
        assertTrue(supported.contains(393), supported.toString());
        assertTrue(supported.contains(401), supported.toString());
        assertTrue(supported.contains(404), supported.toString());
        assertTrue(supported.contains(758), supported.toString());
    }

    @Test
    void differencesFor340Exist() {
        assertFalse(ClientAdapter.differences(340).isEmpty());
    }
}
