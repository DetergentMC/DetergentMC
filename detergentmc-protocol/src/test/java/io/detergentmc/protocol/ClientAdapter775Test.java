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

/** 775（26.1）クライアントの受け入れ判断。 */
class ClientAdapter775Test {

    @Test
    void decide775IsAcceptedViaAdapter() {
        Handshake handshake = ClientAdapter.decide(775);
        assertEquals(Decision.ACCEPT_VIA_ADAPTER, handshake.decision());
        assertTrue(handshake.accepted());
        assertTrue(handshake.needsTranslation());
        assertEquals(775, handshake.clientProtocol());
        assertTrue(handshake.message().contains("775"), handshake.message());
    }

    @Test
    void decide775AgainstAllServerProtocols() {
        for (int server : new int[]{759, 760, 761, 762, 763, 764, 765, 766, 767, 768, 769, 770, 771,
                772, 773, 774, 775}) {
            Handshake handshake = ClientAdapter.decide(775, server);
            assertTrue(handshake.accepted(), "server=" + server);
            if (server == 775) {
                assertEquals(Decision.ACCEPT_NATIVE, handshake.decision());
                assertFalse(handshake.needsTranslation());
            } else {
                assertEquals(Decision.ACCEPT_VIA_ADAPTER, handshake.decision());
                assertTrue(handshake.needsTranslation());
            }
        }
        // 776より新しい／758より古いは断る。
        assertFalse(ClientAdapter.decide(776, 775).accepted());
        assertFalse(ClientAdapter.decide(758, 775).accepted());
    }

    @Test
    void direction775IsComparisonOnly() {
        assertEquals(Direction.NATIVE, ClientAdapter.direction(775, 775));
        assertEquals(Direction.UP, ClientAdapter.direction(763, 775));
        assertEquals(Direction.UP, ClientAdapter.direction(774, 775));
        assertEquals(Direction.DOWN, ClientAdapter.direction(775, 763));
        assertEquals(Direction.DOWN, ClientAdapter.direction(775, 774));
    }

    @Test
    void versionName775Is261() {
        assertEquals("26.1", ClientAdapter.versionName(775));
    }

    @Test
    void supportedClientProtocolsInclude775() {
        assertTrue(ClientAdapter.supportedClientProtocols().contains(775));
    }

    @Test
    void differencesFor775MentionConfigurationRegistryAndPlay() {
        List<Difference> differences = ClientAdapter.differences(775);
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
        // Login Success の strict bool は774・775とも無い（詳細に記載）。
        assertTrue(differences.stream()
                .anyMatch(d -> d.area().equals("LOGIN")
                        && d.detail().contains("strictErrorHandling")),
                differences.toString());
        // 新規パケット5件、パケットID変更164件（774から）。
        assertTrue(differences.stream()
                .anyMatch(d -> d.summary().contains("5件")),
                differences.toString());
        assertTrue(differences.stream()
                .anyMatch(d -> d.summary().contains("164件")),
                differences.toString());
        // 金のタンポポ（アイテム）の追加。
        assertTrue(differences.stream()
                .anyMatch(d -> d.detail().contains("金のタンポポ")),
                differences.toString());
        // Update Time の再構成と Use Entity の再構成。
        assertTrue(differences.stream()
                .anyMatch(d -> d.detail().contains("clockUpdates")),
                differences.toString());
        assertTrue(differences.stream()
                .anyMatch(d -> d.detail().contains("Attack パケット")),
                differences.toString());
    }

    @Test
    void differencesFor775AgainstInternal775AreEmpty() {
        assertTrue(ClientAdapter.differences(775, 775).isEmpty());
    }

    @Test
    void differencesFor775AgainstInternal769MentionConfiguration() {
        List<Difference> differences = ClientAdapter.differences(775, 769);
        assertFalse(differences.isEmpty());
        assertTrue(differences.stream()
                .anyMatch(d -> d.area().equals("CONFIGURATION")),
                differences.toString());
    }

    @Test
    void differencesFor775AgainstInternal764AreNotUnsupported() {
        // 内部764＋775クライアントは decide() が受け入れるので、
        // 2引数版 differences() も NOT_SUPPORTED を返してはいけない。
        List<Difference> differences = ClientAdapter.differences(775, 764);
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
        // 775 クライアントには pack_format 84 のパックを出す。
        assertTrue(differences.stream()
                .anyMatch(d -> d.detail().contains("pack_format 84")),
                differences.toString());
    }

    @Test
    void rewriteStatusJsonFor775Client() {
        String json765 = "{\"version\":{\"name\":\"1.20.4\",\"protocol\":765},"
                + "\"players\":{\"max\":20,\"online\":0}}";
        String rewritten = ClientAdapter.rewriteStatusJson(json765, 765, 775);
        assertTrue(rewritten.contains("\"protocol\":775"), rewritten);
        assertTrue(rewritten.contains("\"name\":\"26.1\""), rewritten);
    }
}
