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

/** 767（1.21／1.21.1）クライアントの受け入れ判断。 */
class ClientAdapter767Test {

    @Test
    void decide767IsAcceptedViaAdapter() {
        Handshake handshake = ClientAdapter.decide(767);
        assertEquals(Decision.ACCEPT_VIA_ADAPTER, handshake.decision());
        assertTrue(handshake.accepted());
        assertTrue(handshake.needsTranslation());
        assertEquals(767, handshake.clientProtocol());
        assertTrue(handshake.message().contains("767"), handshake.message());
    }

    @Test
    void decide767AgainstAllServerProtocols() {
        for (int server : new int[]{763, 764, 765, 766, 767}) {
            Handshake handshake = ClientAdapter.decide(767, server);
            assertTrue(handshake.accepted(), "server=" + server);
            if (server == 767) {
                assertEquals(Decision.ACCEPT_NATIVE, handshake.decision());
                assertFalse(handshake.needsTranslation());
            } else {
                assertEquals(Decision.ACCEPT_VIA_ADAPTER, handshake.decision());
                assertTrue(handshake.needsTranslation());
            }
        }
        // 777より新しい／757より古いは断る。
        assertFalse(ClientAdapter.decide(777, 767).accepted());
        assertFalse(ClientAdapter.decide(734, 767).accepted());
    }

    @Test
    void direction767IsComparisonOnly() {
        assertEquals(Direction.NATIVE, ClientAdapter.direction(767, 767));
        assertEquals(Direction.UP, ClientAdapter.direction(763, 767));
        assertEquals(Direction.UP, ClientAdapter.direction(766, 767));
        assertEquals(Direction.DOWN, ClientAdapter.direction(767, 763));
        assertEquals(Direction.DOWN, ClientAdapter.direction(767, 765));
    }

    @Test
    void versionName767Is121() {
        assertEquals("1.21", ClientAdapter.versionName(767));
    }

    @Test
    void differencesFor767MentionConfigurationAndNewPackets() {
        List<Difference> differences = ClientAdapter.differences(767);
        assertFalse(differences.isEmpty());
        assertTrue(differences.stream()
                .anyMatch(d -> d.area().equals("CONFIGURATION")),
                differences.toString());
        assertTrue(differences.stream()
                .anyMatch(d -> d.area().equals("PLAY")),
                differences.toString());
        // 設定フェーズは766と同形で、Login Success の strict bool あり。
        assertTrue(differences.stream()
                .anyMatch(d -> d.area().equals("LOGIN")
                        && d.summary().contains("strict")),
                differences.toString());
        // 新規パケット6件（Server Links・Custom Report Details）。
        assertTrue(differences.stream()
                .anyMatch(d -> d.summary().contains("6件")),
                differences.toString());
    }

    @Test
    void differencesFor767AgainstInternal767AreEmpty() {
        assertTrue(ClientAdapter.differences(767, 767).isEmpty());
    }

    @Test
    void differencesFor767AgainstInternal766MentionConfiguration() {
        List<Difference> differences = ClientAdapter.differences(767, 766);
        assertFalse(differences.isEmpty());
        assertTrue(differences.stream()
                .anyMatch(d -> d.area().equals("CONFIGURATION")),
                differences.toString());
    }

    @Test
    void differencesFor766And767AgainstInternal764AreNotUnsupported() {
        // 内部764＋766／767クライアントは decide() が受け入れるので、
        // 2引数版 differences() も NOT_SUPPORTED を返してはいけない
        // （766時代からの不整合だった）。
        for (int client : new int[]{766, 767}) {
            List<Difference> differences = ClientAdapter.differences(client, 764);
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
    }

    @Test
    void rewriteStatusJsonFor767Client() {
        String json765 = "{\"version\":{\"name\":\"1.20.4\",\"protocol\":765},"
                + "\"players\":{\"max\":20,\"online\":0}}";
        String rewritten = ClientAdapter.rewriteStatusJson(json765, 765, 767);
        assertTrue(rewritten.contains("\"protocol\":767"), rewritten);
        assertTrue(rewritten.contains("\"name\":\"1.21\""), rewritten);
    }
}
