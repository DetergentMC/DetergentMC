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

/** 768（1.21.2／1.21.3）クライアントの受け入れ判断。 */
class ClientAdapter768Test {

    @Test
    void decide768IsAcceptedViaAdapter() {
        Handshake handshake = ClientAdapter.decide(768);
        assertEquals(Decision.ACCEPT_VIA_ADAPTER, handshake.decision());
        assertTrue(handshake.accepted());
        assertTrue(handshake.needsTranslation());
        assertEquals(768, handshake.clientProtocol());
        assertTrue(handshake.message().contains("768"), handshake.message());
    }

    @Test
    void decide768AgainstAllServerProtocols() {
        for (int server : new int[]{763, 764, 765, 766, 767, 768}) {
            Handshake handshake = ClientAdapter.decide(768, server);
            assertTrue(handshake.accepted(), "server=" + server);
            if (server == 768) {
                assertEquals(Decision.ACCEPT_NATIVE, handshake.decision());
                assertFalse(handshake.needsTranslation());
            } else {
                assertEquals(Decision.ACCEPT_VIA_ADAPTER, handshake.decision());
                assertTrue(handshake.needsTranslation());
            }
        }
        // 777より新しい／757より古いは断る。
        assertFalse(ClientAdapter.decide(777, 768).accepted());
        assertFalse(ClientAdapter.decide(734, 768).accepted());
    }

    @Test
    void direction768IsComparisonOnly() {
        assertEquals(Direction.NATIVE, ClientAdapter.direction(768, 768));
        assertEquals(Direction.UP, ClientAdapter.direction(763, 768));
        assertEquals(Direction.UP, ClientAdapter.direction(767, 768));
        assertEquals(Direction.DOWN, ClientAdapter.direction(768, 763));
        assertEquals(Direction.DOWN, ClientAdapter.direction(768, 765));
    }

    @Test
    void versionName768Is1212() {
        assertEquals("1.21.2", ClientAdapter.versionName(768));
    }

    @Test
    void differencesFor768MentionConfigurationAndNewPackets() {
        List<Difference> differences = ClientAdapter.differences(768);
        assertFalse(differences.isEmpty());
        assertTrue(differences.stream()
                .anyMatch(d -> d.area().equals("CONFIGURATION")),
                differences.toString());
        assertTrue(differences.stream()
                .anyMatch(d -> d.area().equals("PLAY")),
                differences.toString());
        // Login Success の strict bool が768で廃止された。
        assertTrue(differences.stream()
                .anyMatch(d -> d.area().equals("LOGIN")
                        && d.summary().contains("strictErrorHandling")),
                differences.toString());
        // 新規パケット11件、既存IDの変更145件。
        assertTrue(differences.stream()
                .anyMatch(d -> d.summary().contains("11件")),
                differences.toString());
        assertTrue(differences.stream()
                .anyMatch(d -> d.summary().contains("145件")),
                differences.toString());
    }

    @Test
    void differencesFor768AgainstInternal768AreEmpty() {
        assertTrue(ClientAdapter.differences(768, 768).isEmpty());
    }

    @Test
    void differencesFor768AgainstInternal767MentionConfiguration() {
        List<Difference> differences = ClientAdapter.differences(768, 767);
        assertFalse(differences.isEmpty());
        assertTrue(differences.stream()
                .anyMatch(d -> d.area().equals("CONFIGURATION")),
                differences.toString());
    }

    @Test
    void differencesFor768AgainstInternal764AreNotUnsupported() {
        // 内部764＋768クライアントは decide() が受け入れるので、
        // 2引数版 differences() も NOT_SUPPORTED を返してはいけない。
        List<Difference> differences = ClientAdapter.differences(768, 764);
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
    void rewriteStatusJsonFor768Client() {
        String json765 = "{\"version\":{\"name\":\"1.20.4\",\"protocol\":765},"
                + "\"players\":{\"max\":20,\"online\":0}}";
        String rewritten = ClientAdapter.rewriteStatusJson(json765, 765, 768);
        assertTrue(rewritten.contains("\"protocol\":768"), rewritten);
        assertTrue(rewritten.contains("\"name\":\"1.21.2\""), rewritten);
    }
}
