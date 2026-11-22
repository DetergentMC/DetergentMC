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

/** 769（1.21.4）クライアントの受け入れ判断。 */
class ClientAdapter769Test {

    @Test
    void decide769IsAcceptedViaAdapter() {
        Handshake handshake = ClientAdapter.decide(769);
        assertEquals(Decision.ACCEPT_VIA_ADAPTER, handshake.decision());
        assertTrue(handshake.accepted());
        assertTrue(handshake.needsTranslation());
        assertEquals(769, handshake.clientProtocol());
        assertTrue(handshake.message().contains("769"), handshake.message());
    }

    @Test
    void decide769AgainstAllServerProtocols() {
        for (int server : new int[]{763, 764, 765, 766, 767, 768, 769}) {
            Handshake handshake = ClientAdapter.decide(769, server);
            assertTrue(handshake.accepted(), "server=" + server);
            if (server == 769) {
                assertEquals(Decision.ACCEPT_NATIVE, handshake.decision());
                assertFalse(handshake.needsTranslation());
            } else {
                assertEquals(Decision.ACCEPT_VIA_ADAPTER, handshake.decision());
                assertTrue(handshake.needsTranslation());
            }
        }
        // 777より新しい／757より古いは断る。
        assertFalse(ClientAdapter.decide(777, 769).accepted());
        assertFalse(ClientAdapter.decide(757, 769).accepted());
    }

    @Test
    void direction769IsComparisonOnly() {
        assertEquals(Direction.NATIVE, ClientAdapter.direction(769, 769));
        assertEquals(Direction.UP, ClientAdapter.direction(763, 769));
        assertEquals(Direction.UP, ClientAdapter.direction(768, 769));
        assertEquals(Direction.DOWN, ClientAdapter.direction(769, 763));
        assertEquals(Direction.DOWN, ClientAdapter.direction(769, 768));
    }

    @Test
    void versionName769Is1214() {
        assertEquals("1.21.4", ClientAdapter.versionName(769));
    }

    @Test
    void differencesFor769MentionConfigurationRegistryAndNewPackets() {
        List<Difference> differences = ClientAdapter.differences(769);
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
        // Login Success の strict bool は768・769とも無い（詳細に記載）。
        assertTrue(differences.stream()
                .anyMatch(d -> d.area().equals("LOGIN")
                        && d.detail().contains("strictErrorHandling")),
                differences.toString());
        // 新規パケット3件、既存IDの変更24件。
        assertTrue(differences.stream()
                .anyMatch(d -> d.summary().contains("3件")),
                differences.toString());
        assertTrue(differences.stream()
                .anyMatch(d -> d.summary().contains("24件")),
                differences.toString());
        // pale_garden の追加。
        assertTrue(differences.stream()
                .anyMatch(d -> d.summary().contains("pale_garden")),
                differences.toString());
    }

    @Test
    void differencesFor769AgainstInternal769AreEmpty() {
        assertTrue(ClientAdapter.differences(769, 769).isEmpty());
    }

    @Test
    void differencesFor769AgainstInternal768MentionConfiguration() {
        List<Difference> differences = ClientAdapter.differences(769, 768);
        assertFalse(differences.isEmpty());
        assertTrue(differences.stream()
                .anyMatch(d -> d.area().equals("CONFIGURATION")),
                differences.toString());
    }

    @Test
    void differencesFor769AgainstInternal764AreNotUnsupported() {
        // 内部764＋769クライアントは decide() が受け入れるので、
        // 2引数版 differences() も NOT_SUPPORTED を返してはいけない。
        List<Difference> differences = ClientAdapter.differences(769, 764);
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
    void rewriteStatusJsonFor769Client() {
        String json765 = "{\"version\":{\"name\":\"1.20.4\",\"protocol\":765},"
                + "\"players\":{\"max\":20,\"online\":0}}";
        String rewritten = ClientAdapter.rewriteStatusJson(json765, 765, 769);
        assertTrue(rewritten.contains("\"protocol\":769"), rewritten);
        assertTrue(rewritten.contains("\"name\":\"1.21.4\""), rewritten);
    }
}
