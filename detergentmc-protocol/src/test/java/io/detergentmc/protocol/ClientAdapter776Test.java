package io.detergentmc.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.detergentmc.protocol.ClientAdapter.Decision;
import io.detergentmc.protocol.ClientAdapter.Difference;
import io.detergentmc.protocol.ClientAdapter.Direction;
import io.detergentmc.protocol.ClientAdapter.Handshake;
import io.detergentmc.protocol.ClientAdapter.Handling;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 776（26.2）クライアントの受け入れ判断。 */
class ClientAdapter776Test {

    @Test
    void decide776IsAcceptedViaAdapter() {
        Handshake handshake = ClientAdapter.decide(776);
        assertEquals(Decision.ACCEPT_VIA_ADAPTER, handshake.decision());
        assertTrue(handshake.accepted());
        assertTrue(handshake.needsTranslation());
        assertEquals(776, handshake.clientProtocol());
        assertTrue(handshake.message().contains("776"), handshake.message());
    }

    @Test
    void decide776AgainstAllServerProtocols() {
        for (int server : new int[]{758, 759, 760, 761, 762, 763, 764, 765, 766, 767, 768, 769, 770, 771,
                772, 773, 774, 775, 776}) {
            Handshake handshake = ClientAdapter.decide(776, server);
            assertTrue(handshake.accepted(), "server=" + server);
            if (server == 776) {
                assertEquals(Decision.ACCEPT_NATIVE, handshake.decision());
                assertFalse(handshake.needsTranslation());
            } else {
                assertEquals(Decision.ACCEPT_VIA_ADAPTER, handshake.decision());
                assertTrue(handshake.needsTranslation());
            }
        }
        // 777より新しい／757より古いは断る。
        assertFalse(ClientAdapter.decide(777, 776).accepted());
        assertFalse(ClientAdapter.decide(734, 776).accepted());
    }

    @Test
    void direction776IsComparisonOnly() {
        assertEquals(Direction.NATIVE, ClientAdapter.direction(776, 776));
        assertEquals(Direction.UP, ClientAdapter.direction(763, 776));
        assertEquals(Direction.UP, ClientAdapter.direction(775, 776));
        assertEquals(Direction.DOWN, ClientAdapter.direction(776, 763));
        assertEquals(Direction.DOWN, ClientAdapter.direction(776, 775));
    }

    @Test
    void versionName776Is262() {
        assertEquals("26.2", ClientAdapter.versionName(776));
    }

    @Test
    void supportedClientProtocolsInclude776() {
        assertTrue(ClientAdapter.supportedClientProtocols().contains(776));
    }

    @Test
    void differencesFor776MentionConfigurationRegistryAndPlay() {
        List<Difference> differences = ClientAdapter.differences(776);
        assertFalse(differences.isEmpty());
        assertTrue(differences.stream()
                .anyMatch(d -> d.area().equals("CONFIGURATION")),
                differences.toString());
        assertTrue(differences.stream()
                .anyMatch(d -> d.area().equals("REGISTRY")),
                differences.toString());
        assertTrue(differences.stream()
                .anyMatch(d -> d.area().equals("PLAY")),
                differences.toString());
        // Login Success の末尾 sessionId（UUID）は775・776の差。
        assertTrue(differences.stream()
                .anyMatch(d -> d.area().equals("LOGIN")
                        && d.detail().contains("sessionId")),
                differences.toString());
        // 新規パケット0件、パケットID変更0件（775から）。
        assertTrue(differences.stream()
                .anyMatch(d -> d.summary().contains("0件")),
                differences.toString());
        // 硫黄キューブ（エンティティ）の追加。
        assertTrue(differences.stream()
                .anyMatch(d -> d.detail().contains("sulfur_cube")),
                differences.toString());
        // Join Game の onlineMode と Spectator Action の改名。
        assertTrue(differences.stream()
                .anyMatch(d -> d.detail().contains("onlineMode")),
                differences.toString());
        assertTrue(differences.stream()
                .anyMatch(d -> d.detail().contains("Spectator Action")),
                differences.toString());
    }

    @Test
    void differencesFor776AgainstInternal776AreEmpty() {
        assertTrue(ClientAdapter.differences(776, 776).isEmpty());
    }

    @Test
    void differencesFor776AgainstInternal769MentionConfiguration() {
        List<Difference> differences = ClientAdapter.differences(776, 769);
        assertFalse(differences.isEmpty());
        assertTrue(differences.stream()
                .anyMatch(d -> d.area().equals("CONFIGURATION")),
                differences.toString());
    }

    @Test
    void differencesFor776AgainstInternal764AreNotUnsupported() {
        // 内部764＋776クライアントは decide() が受け入れるので、
        // 2引数版 differences() も NOT_SUPPORTED を返してはいけない。
        List<Difference> differences = ClientAdapter.differences(776, 764);
        assertFalse(differences.isEmpty());
        assertTrue(differences.stream()
                .noneMatch(d -> d.handling() == Handling.NOT_SUPPORTED),
                differences.toString());
        assertTrue(differences.stream()
                .anyMatch(d -> d.area().equals("CONFIGURATION")),
                differences.toString());
        assertTrue(differences.stream()
                .anyMatch(d -> d.area().equals("PLAY")
                        && d.summary().contains("Window Click")),
                differences.toString());
        // 776 クライアントには pack_format 88 のパックを出す。
        assertTrue(differences.stream()
                .anyMatch(d -> d.detail().contains("pack_format 88")),
                differences.toString());
    }

    @Test
    void rewriteStatusJsonFor776Client() {
        String json765 = "{\"version\":{\"name\":\"1.20.4\",\"protocol\":765},"
                + "\"players\":{\"max\":20,\"online\":0}}";
        String rewritten = ClientAdapter.rewriteStatusJson(json765, 765, 776);
        assertTrue(rewritten.contains("\"protocol\":776"), rewritten);
        assertTrue(rewritten.contains("\"name\":\"26.2\""), rewritten);
    }
}
