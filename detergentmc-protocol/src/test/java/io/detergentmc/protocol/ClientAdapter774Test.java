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

/** 774（1.21.11）クライアントの受け入れ判断。 */
class ClientAdapter774Test {

    @Test
    void decide774IsAcceptedViaAdapter() {
        Handshake handshake = ClientAdapter.decide(774);
        assertEquals(Decision.ACCEPT_VIA_ADAPTER, handshake.decision());
        assertTrue(handshake.accepted());
        assertTrue(handshake.needsTranslation());
        assertEquals(774, handshake.clientProtocol());
        assertTrue(handshake.message().contains("774"), handshake.message());
    }

    @Test
    void decide774AgainstAllServerProtocols() {
        for (int server : new int[]{763, 764, 765, 766, 767, 768, 769, 770, 771, 772, 773, 774}) {
            Handshake handshake = ClientAdapter.decide(774, server);
            assertTrue(handshake.accepted(), "server=" + server);
            if (server == 774) {
                assertEquals(Decision.ACCEPT_NATIVE, handshake.decision());
                assertFalse(handshake.needsTranslation());
            } else {
                assertEquals(Decision.ACCEPT_VIA_ADAPTER, handshake.decision());
                assertTrue(handshake.needsTranslation());
            }
        }
        // 776より新しい／761より古いは断る。
        assertFalse(ClientAdapter.decide(776, 774).accepted());
        assertFalse(ClientAdapter.decide(758, 774).accepted());
    }

    @Test
    void direction774IsComparisonOnly() {
        assertEquals(Direction.NATIVE, ClientAdapter.direction(774, 774));
        assertEquals(Direction.UP, ClientAdapter.direction(763, 774));
        assertEquals(Direction.UP, ClientAdapter.direction(769, 774));
        assertEquals(Direction.DOWN, ClientAdapter.direction(774, 763));
        assertEquals(Direction.DOWN, ClientAdapter.direction(774, 769));
    }

    @Test
    void versionName774Is12111() {
        assertEquals("1.21.11", ClientAdapter.versionName(774));
    }

    @Test
    void differencesFor774MentionConfigurationRegistryAndPlay() {
        List<Difference> differences = ClientAdapter.differences(774);
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
        // Login Success の strict bool は772・773とも無い（詳細に記載）。
        assertTrue(differences.stream()
                .anyMatch(d -> d.area().equals("LOGIN")
                        && d.detail().contains("strictErrorHandling")),
                differences.toString());
        // 新規パケット0件、ID変更0件（773とIDはまったく同じ）。
        assertTrue(differences.stream()
                .anyMatch(d -> d.summary().contains("0件")),
                differences.toString());
        // ノーチラス（エンティティ）の追加。
        assertTrue(differences.stream()
                .anyMatch(d -> d.detail().contains("ノーチラス")),
                differences.toString());
    }

    @Test
    void differencesFor774AgainstInternal774AreEmpty() {
        assertTrue(ClientAdapter.differences(774, 774).isEmpty());
    }

    @Test
    void differencesFor774AgainstInternal769MentionConfiguration() {
        List<Difference> differences = ClientAdapter.differences(774, 769);
        assertFalse(differences.isEmpty());
        assertTrue(differences.stream()
                .anyMatch(d -> d.area().equals("CONFIGURATION")),
                differences.toString());
    }

    @Test
    void differencesFor774AgainstInternal764AreNotUnsupported() {
        // 内部764＋774クライアントは decide() が受け入れるので、
        // 2引数版 differences() も NOT_SUPPORTED を返してはいけない。
        List<Difference> differences = ClientAdapter.differences(774, 764);
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
    }

    @Test
    void rewriteStatusJsonFor774Client() {
        String json765 = "{\"version\":{\"name\":\"1.20.4\",\"protocol\":765},"
                + "\"players\":{\"max\":20,\"online\":0}}";
        String rewritten = ClientAdapter.rewriteStatusJson(json765, 765, 774);
        assertTrue(rewritten.contains("\"protocol\":774"), rewritten);
        assertTrue(rewritten.contains("\"name\":\"1.21.11\""), rewritten);
    }
}
