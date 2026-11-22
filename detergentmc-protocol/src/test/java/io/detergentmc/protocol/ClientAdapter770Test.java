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

/** 770（1.21.5）クライアントの受け入れ判断。 */
class ClientAdapter770Test {

    @Test
    void decide770IsAcceptedViaAdapter() {
        Handshake handshake = ClientAdapter.decide(770);
        assertEquals(Decision.ACCEPT_VIA_ADAPTER, handshake.decision());
        assertTrue(handshake.accepted());
        assertTrue(handshake.needsTranslation());
        assertEquals(770, handshake.clientProtocol());
        assertTrue(handshake.message().contains("770"), handshake.message());
    }

    @Test
    void decide770AgainstAllServerProtocols() {
        for (int server : new int[]{763, 764, 765, 766, 767, 768, 769, 770}) {
            Handshake handshake = ClientAdapter.decide(770, server);
            assertTrue(handshake.accepted(), "server=" + server);
            if (server == 770) {
                assertEquals(Decision.ACCEPT_NATIVE, handshake.decision());
                assertFalse(handshake.needsTranslation());
            } else {
                assertEquals(Decision.ACCEPT_VIA_ADAPTER, handshake.decision());
                assertTrue(handshake.needsTranslation());
            }
        }
        // 777より新しい／757より古いは断る。
        assertFalse(ClientAdapter.decide(777, 770).accepted());
        assertFalse(ClientAdapter.decide(734, 770).accepted());
    }

    @Test
    void direction770IsComparisonOnly() {
        assertEquals(Direction.NATIVE, ClientAdapter.direction(770, 770));
        assertEquals(Direction.UP, ClientAdapter.direction(763, 770));
        assertEquals(Direction.UP, ClientAdapter.direction(769, 770));
        assertEquals(Direction.DOWN, ClientAdapter.direction(770, 763));
        assertEquals(Direction.DOWN, ClientAdapter.direction(770, 769));
    }

    @Test
    void versionName770Is1215() {
        assertEquals("1.21.5", ClientAdapter.versionName(770));
    }

    @Test
    void differencesFor770MentionConfigurationRegistryAndNewPackets() {
        List<Difference> differences = ClientAdapter.differences(770);
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
        // Login Success の strict bool は769・770とも無い（詳細に記載）。
        assertTrue(differences.stream()
                .anyMatch(d -> d.area().equals("LOGIN")
                        && d.detail().contains("strictErrorHandling")),
                differences.toString());
        // 新規パケット3件、既存IDの変更122件。
        assertTrue(differences.stream()
                .anyMatch(d -> d.summary().contains("3件")),
                differences.toString());
        assertTrue(differences.stream()
                .anyMatch(d -> d.summary().contains("122件")),
                differences.toString());
        // tinted_leaves・firefly の追加。
        assertTrue(differences.stream()
                .anyMatch(d -> d.detail().contains("tinted_leaves")),
                differences.toString());
    }

    @Test
    void differencesFor770AgainstInternal770AreEmpty() {
        assertTrue(ClientAdapter.differences(770, 770).isEmpty());
    }

    @Test
    void differencesFor770AgainstInternal769MentionConfiguration() {
        List<Difference> differences = ClientAdapter.differences(770, 769);
        assertFalse(differences.isEmpty());
        assertTrue(differences.stream()
                .anyMatch(d -> d.area().equals("CONFIGURATION")),
                differences.toString());
    }

    @Test
    void differencesFor770AgainstInternal764AreNotUnsupported() {
        // 内部764＋770クライアントは decide() が受け入れるので、
        // 2引数版 differences() も NOT_SUPPORTED を返してはいけない。
        List<Difference> differences = ClientAdapter.differences(770, 764);
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
    void rewriteStatusJsonFor770Client() {
        String json765 = "{\"version\":{\"name\":\"1.20.4\",\"protocol\":765},"
                + "\"players\":{\"max\":20,\"online\":0}}";
        String rewritten = ClientAdapter.rewriteStatusJson(json765, 765, 770);
        assertTrue(rewritten.contains("\"protocol\":770"), rewritten);
        assertTrue(rewritten.contains("\"name\":\"1.21.5\""), rewritten);
    }
}
