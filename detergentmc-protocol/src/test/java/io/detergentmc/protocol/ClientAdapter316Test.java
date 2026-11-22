package io.detergentmc.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.detergentmc.protocol.ClientAdapter.Decision;
import io.detergentmc.protocol.ClientAdapter.Handshake;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 316（1.11.2）クライアントの受け入れ判断。最古が 316 に移っても
 * 335・338・340 が一覧から脱落していないことを固定する（最古を
 * 下げた際に旧最古が脱落するバグが過去に複数回起きている）。
 */
class ClientAdapter316Test {

    @Test
    void decide316IsAcceptedViaAdapter() {
        Handshake handshake = ClientAdapter.decide(316);
        assertEquals(Decision.ACCEPT_VIA_ADAPTER, handshake.decision());
        assertTrue(handshake.accepted());
        assertTrue(handshake.needsTranslation());
        assertEquals(316, handshake.clientProtocol());
        assertTrue(handshake.message().contains("316"), handshake.message());
    }

    @Test
    void decide316AgainstAllServerProtocols() {
        for (int server : new int[]{315, 316, 335, 338, 340, 393, 401, 404, 477, 480, 485, 490, 498, 573, 575, 578, 735, 736, 751, 753, 754, 755, 756, 757, 758, 759, 760, 761, 762, 763, 764, 765, 766, 767, 768, 769, 770, 771, 772, 773, 774, 775, 776}) {
            Handshake handshake = ClientAdapter.decide(316, server);
            assertTrue(handshake.accepted(), "server=" + server);
            if (server == 316) {
                assertEquals(Decision.ACCEPT_NATIVE, handshake.decision());
                assertFalse(handshake.needsTranslation());
            } else {
                assertEquals(Decision.ACCEPT_VIA_ADAPTER, handshake.decision());
                assertTrue(handshake.needsTranslation());
            }
        }
        // 314 と 317、777 はこのツリーでは未対応のため断る。
        // （315 は 1.11 として対応済み。旧版の本テストにあった
        // 「315（1.11.1）は未対応」という断言は番号対応の訂正で無効。）
        assertFalse(ClientAdapter.decide(314, 316).accepted());
        assertFalse(ClientAdapter.decide(317, 316).accepted());
        assertFalse(ClientAdapter.decide(777, 316).accepted());
    }

    @Test
    void versionName316Is1112() {
        assertEquals("1.11.2", ClientAdapter.versionName(316));
    }

    @Test
    void oldestSupportedIs316And335And338And340AreStillListed() {
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
        // 最古を 316 に下げても 335・338・340 が一覧から脱落していない
        // （最古脱落の罠の回帰防止）。
        assertEquals(316, supported.get(9), supported.toString());
        assertEquals(335, supported.get(10), supported.toString());
        assertEquals(338, supported.get(11), supported.toString());
        assertTrue(supported.contains(393), supported.toString());
        assertTrue(supported.contains(776), supported.toString());
        assertEquals(51, supported.size(), supported.toString());
    }
}
