package io.detergentmc.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.detergentmc.protocol.ClientAdapter.Decision;
import io.detergentmc.protocol.ClientAdapter.Difference;
import io.detergentmc.protocol.ClientAdapter.Direction;
import io.detergentmc.protocol.ClientAdapter.Handshake;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 766（1.20.5／1.20.6）クライアントの受け入れ判断。 */
class ClientAdapter766Test {

    @Test
    void decide766IsAcceptedViaAdapter() {
        Handshake handshake = ClientAdapter.decide(766);
        assertEquals(Decision.ACCEPT_VIA_ADAPTER, handshake.decision());
        assertTrue(handshake.accepted());
        assertTrue(handshake.needsTranslation());
        assertEquals(766, handshake.clientProtocol());
        assertTrue(handshake.message().contains("766"), handshake.message());
    }

    @Test
    void decide766AgainstAllServerProtocols() {
        for (int server : new int[]{763, 764, 765, 766}) {
            Handshake handshake = ClientAdapter.decide(766, server);
            assertTrue(handshake.accepted(), "server=" + server);
            if (server == 766) {
                assertEquals(Decision.ACCEPT_NATIVE, handshake.decision());
                assertFalse(handshake.needsTranslation());
            } else {
                assertEquals(Decision.ACCEPT_VIA_ADAPTER, handshake.decision());
                assertTrue(handshake.needsTranslation());
            }
        }
        // 777より新しい／757より古いは断る。
        assertFalse(ClientAdapter.decide(777, 766).accepted());
        assertFalse(ClientAdapter.decide(757, 766).accepted());
    }

    @Test
    void direction766IsComparisonOnly() {
        assertEquals(Direction.NATIVE, ClientAdapter.direction(766, 766));
        assertEquals(Direction.UP, ClientAdapter.direction(763, 766));
        assertEquals(Direction.UP, ClientAdapter.direction(765, 766));
        assertEquals(Direction.DOWN, ClientAdapter.direction(766, 763));
        assertEquals(Direction.DOWN, ClientAdapter.direction(766, 765));
    }

    @Test
    void versionName766Is1205() {
        assertEquals("1.20.5", ClientAdapter.versionName(766));
    }

    @Test
    void differencesFor766MentionConfigurationAndPlay() {
        List<Difference> differences = ClientAdapter.differences(766);
        assertFalse(differences.isEmpty());
        assertTrue(differences.stream()
                .anyMatch(d -> d.area().equals("CONFIGURATION")),
                differences.toString());
        assertTrue(differences.stream()
                .anyMatch(d -> d.area().equals("PLAY")),
                differences.toString());
    }

    @Test
    void rewriteStatusJsonFor766Client() {
        String json765 = "{\"version\":{\"name\":\"1.20.4\",\"protocol\":765},"
                + "\"players\":{\"max\":20,\"online\":0}}";
        String rewritten = ClientAdapter.rewriteStatusJson(json765, 765, 766);
        assertTrue(rewritten.contains("\"protocol\":766"), rewritten);
        assertTrue(rewritten.contains("\"name\":\"1.20.5\""), rewritten);
    }
}
