package io.detergentmc.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.detergentmc.protocol.ClientAdapter.Decision;
import io.detergentmc.protocol.ClientAdapter.Handshake;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 110（1.9.4／1.9.3）クライアントの受け入れ判断。
 * 最古が 110 に移っても 210・315・316・335・338・340 が一覧から
 * 脱落していないことを固定する（最古を下げた際に旧最古が
 * 脱落するバグが過去に起きている。210 が脱落すると 1.10 系、
 * 315 が脱落すると 1.11、316 が脱落すると 1.11.1／1.11.2 まで
 * 巻き添えになる）。110 は 1.9.3 と 1.9.4 の2リリース共通。
 */
class ClientAdapter110Test {

    @Test
    void decide110IsAcceptedViaAdapter() {
        Handshake handshake = ClientAdapter.decide(110);
        assertEquals(Decision.ACCEPT_VIA_ADAPTER, handshake.decision());
        assertTrue(handshake.accepted());
        assertTrue(handshake.needsTranslation());
        assertEquals(110, handshake.clientProtocol());
        assertTrue(handshake.message().contains("110"), handshake.message());
    }

    @Test
    void decide110AgainstAllServerProtocols() {
        for (int server : new int[]{109, 110, 210, 315, 316, 335, 338, 340, 393, 401, 404, 477, 480, 485, 490, 498, 573, 575, 578, 735, 736, 751, 753, 754, 755, 756, 757, 758, 759, 760, 761, 762, 763, 764, 765, 766, 767, 768, 769, 770, 771, 772, 773, 774, 775, 776}) {
            Handshake handshake = ClientAdapter.decide(110, server);
            assertTrue(handshake.accepted(), "server=" + server);
            if (server == 110) {
                assertEquals(Decision.ACCEPT_NATIVE, handshake.decision());
                assertFalse(handshake.needsTranslation());
            } else {
                assertEquals(Decision.ACCEPT_VIA_ADAPTER, handshake.decision());
                assertTrue(handshake.needsTranslation());
            }
        }
        // 777 はこのツリーでは未対応のため断る（4 は対応済みで受け入れる）。
        assertTrue(ClientAdapter.decide(4, 110).accepted());
        assertFalse(ClientAdapter.decide(777, 110).accepted());
    }

    @Test
    void versionName110Is194() {
        assertEquals("1.9.4", ClientAdapter.versionName(110));
        assertEquals("1.10.2", ClientAdapter.versionName(210));
        assertEquals("1.11", ClientAdapter.versionName(315));
        assertEquals("1.11.2", ClientAdapter.versionName(316));
    }

    @Test
    void oldestSupportedIs47And210And315And316AreStillListed() {
        assertEquals(4, ClientAdapter.OLDEST_SUPPORTED_CLIENT_PROTOCOL);
        List<Integer> supported = ClientAdapter.supportedClientProtocols();
        assertEquals(4, supported.get(0), supported.toString());
        assertEquals(5, supported.get(1), supported.toString());
        assertEquals(47, supported.get(2), supported.toString());
        assertEquals(107, supported.get(3), supported.toString());
        assertEquals(108, supported.get(4), supported.toString());
        assertEquals(109, supported.get(5), supported.toString());
        assertEquals(110, supported.get(6), supported.toString());
        // 最古を 47 に下げても 210・315・316・335・338・340 が
        // 一覧から脱落していない（最古脱落の罠の回帰防止）。
        assertEquals(210, supported.get(7), supported.toString());
        assertEquals(315, supported.get(8), supported.toString());
        assertEquals(316, supported.get(9), supported.toString());
        assertEquals(335, supported.get(10), supported.toString());
        assertEquals(338, supported.get(11), supported.toString());
        assertEquals(340, supported.get(12), supported.toString());
        assertTrue(supported.contains(393), supported.toString());
        assertTrue(supported.contains(776), supported.toString());
        assertEquals(51, supported.size(), supported.toString());
    }
}
