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

/** 773（1.21.9）クライアントの受け入れ判断。 */
class ClientAdapter773Test {

    @Test
    void decide773IsAcceptedViaAdapter() {
        Handshake handshake = ClientAdapter.decide(773);
        assertEquals(Decision.ACCEPT_VIA_ADAPTER, handshake.decision());
        assertTrue(handshake.accepted());
        assertTrue(handshake.needsTranslation());
        assertEquals(773, handshake.clientProtocol());
        assertTrue(handshake.message().contains("773"), handshake.message());
    }

    @Test
    void decide773AgainstAllServerProtocols() {
        for (int server : new int[]{763, 764, 765, 766, 767, 768, 769, 770, 771, 772, 773, 774}) {
            Handshake handshake = ClientAdapter.decide(773, server);
            assertTrue(handshake.accepted(), "server=" + server);
            if (server == 773) {
                assertEquals(Decision.ACCEPT_NATIVE, handshake.decision());
                assertFalse(handshake.needsTranslation());
            } else {
                assertEquals(Decision.ACCEPT_VIA_ADAPTER, handshake.decision());
                assertTrue(handshake.needsTranslation());
            }
        }
        // 777より新しい／757より古いは断る。
        assertFalse(ClientAdapter.decide(777, 773).accepted());
        assertFalse(ClientAdapter.decide(757, 773).accepted());
    }

    @Test
    void direction773IsComparisonOnly() {
        assertEquals(Direction.NATIVE, ClientAdapter.direction(773, 773));
        assertEquals(Direction.UP, ClientAdapter.direction(763, 773));
        assertEquals(Direction.UP, ClientAdapter.direction(769, 773));
        assertEquals(Direction.DOWN, ClientAdapter.direction(773, 763));
        assertEquals(Direction.DOWN, ClientAdapter.direction(773, 769));
    }

    @Test
    void versionName773Is1219() {
        assertEquals("1.21.9", ClientAdapter.versionName(773));
    }

    @Test
    void differencesFor773MentionConfigurationRegistryAndNewPackets() {
        List<Difference> differences = ClientAdapter.differences(773);
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
        // 新規パケット8件、ID変更108件（デバッグ系の挿入で0x1A以降が+4、0x27以降が+5）。
        assertTrue(differences.stream()
                .anyMatch(d -> d.summary().contains("8件")),
                differences.toString());
        assertTrue(differences.stream()
                .anyMatch(d -> d.summary().contains("108件")),
                differences.toString());
        // 銅ゴーレム（エンティティ）の追加。
        assertTrue(differences.stream()
                .anyMatch(d -> d.detail().contains("銅ゴーレム")),
                differences.toString());
    }

    @Test
    void differencesFor773AgainstInternal773AreEmpty() {
        assertTrue(ClientAdapter.differences(773, 773).isEmpty());
    }

    @Test
    void differencesFor773AgainstInternal769MentionConfiguration() {
        List<Difference> differences = ClientAdapter.differences(773, 769);
        assertFalse(differences.isEmpty());
        assertTrue(differences.stream()
                .anyMatch(d -> d.area().equals("CONFIGURATION")),
                differences.toString());
    }

    @Test
    void differencesFor773AgainstInternal764AreNotUnsupported() {
        // 内部764＋773クライアントは decide() が受け入れるので、
        // 2引数版 differences() も NOT_SUPPORTED を返してはいけない。
        List<Difference> differences = ClientAdapter.differences(773, 764);
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
    void rewriteStatusJsonFor773Client() {
        String json765 = "{\"version\":{\"name\":\"1.20.4\",\"protocol\":765},"
                + "\"players\":{\"max\":20,\"online\":0}}";
        String rewritten = ClientAdapter.rewriteStatusJson(json765, 765, 773);
        assertTrue(rewritten.contains("\"protocol\":773"), rewritten);
        assertTrue(rewritten.contains("\"name\":\"1.21.9\""), rewritten);
    }
}
