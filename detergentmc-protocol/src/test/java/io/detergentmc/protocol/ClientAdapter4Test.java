package io.detergentmc.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.detergentmc.protocol.ClientAdapter.Decision;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 4（1.7.2〜1.7.5）クライアントの受け入れと対応一覧。
 * 最古脱落の罠を固定する：対応一覧の先頭は4、5・47・107・108・109・
 * 110・210・315・316 が残存し、計43プロトコルであること。
 */
class ClientAdapter4Test {

    @Test
    void oldestIs4() {
        assertEquals(4, ClientAdapter.OLDEST_SUPPORTED_CLIENT_PROTOCOL);
        assertEquals(ProtocolVersion.V1_7_2,
                ClientAdapter.OLDEST_SUPPORTED_CLIENT_PROTOCOL);
    }

    @Test
    void supportedListStartsWith4AndKeepsAllPrevious() {
        List<Integer> supported = ClientAdapter.supportedClientProtocols();
        assertEquals(4, supported.get(0), supported.toString());
        for (int protocol : new int[] {5, 47, 107, 108, 109, 110, 210,
                315, 316, 335, 338, 340, 393, 401, 404, 477, 758, 763,
                765, 776}) {
            assertTrue(supported.contains(protocol),
                    protocol + " が対応一覧から脱落している: " + supported);
        }
        assertEquals(51, supported.size(), supported.toString());
    }

    @Test
    void versionName4() {
        assertEquals("1.7.2", ClientAdapter.versionName(4));
        assertEquals("1.7.10", ClientAdapter.versionName(5));
        assertEquals("1.8", ClientAdapter.versionName(47));
    }

    @Test
    void decideAccepts4ViaAdapter() {
        ClientAdapter.Handshake handshake = ClientAdapter.decide(4, 765);
        assertEquals(Decision.ACCEPT_VIA_ADAPTER, handshake.decision());
        assertFalse(handshake.message().isEmpty());
        assertTrue(handshake.message().contains("4"), handshake.message());
    }

    @Test
    void decideAccepts4AgainstAllServerProtocols() {
        for (int server : new int[] {4, 5, 47, 107, 108, 109, 110, 210,
                315, 316, 335, 338, 340, 393, 401, 404, 477, 480, 485,
                490, 498, 573, 575, 578, 735, 736, 751, 753, 754, 755, 756, 757, 758, 759, 760, 761, 762, 763,
                764, 765, 766, 767, 768, 769, 770, 771, 772, 773, 774,
                775, 776}) {
            ClientAdapter.Handshake handshake = ClientAdapter.decide(4, server);
            assertTrue(handshake.accepted(), "server=" + server);
            if (server == 4) {
                assertEquals(Decision.ACCEPT_NATIVE, handshake.decision());
            } else {
                assertEquals(Decision.ACCEPT_VIA_ADAPTER, handshake.decision());
            }
        }
    }

    @Test
    void decideRejects3() {
        // プロトコル3以下は Netty 以前の別世代（今回の対象外）。
        ClientAdapter.Handshake handshake = ClientAdapter.decide(3, 765);
        assertEquals(Decision.REJECT_OUTDATED, handshake.decision());
    }
}
