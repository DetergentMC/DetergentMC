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

/** 762（1.19.4）クライアントの受け入れ判断。 */
class ClientAdapter762Test {

    @Test
    void decide762IsAcceptedViaAdapter() {
        Handshake handshake = ClientAdapter.decide(762);
        assertEquals(Decision.ACCEPT_VIA_ADAPTER, handshake.decision());
        assertTrue(handshake.accepted());
        assertTrue(handshake.needsTranslation());
        assertEquals(762, handshake.clientProtocol());
        assertTrue(handshake.message().contains("762"), handshake.message());
    }

    @Test
    void decide762AgainstAllServerProtocols() {
        for (int server : new int[]{762, 763, 764, 765, 766, 767, 768, 769, 770, 771, 772, 773, 774}) {
            Handshake handshake = ClientAdapter.decide(762, server);
            assertTrue(handshake.accepted(), "server=" + server);
            if (server == 762) {
                assertEquals(Decision.ACCEPT_NATIVE, handshake.decision());
                assertFalse(handshake.needsTranslation());
            } else {
                assertEquals(Decision.ACCEPT_VIA_ADAPTER, handshake.decision());
                assertTrue(handshake.needsTranslation());
            }
        }
        // 777より新しい／757より古いは断る。
        assertFalse(ClientAdapter.decide(777, 762).accepted());
        assertFalse(ClientAdapter.decide(734, 762).accepted());
    }

    @Test
    void direction762IsComparisonOnly() {
        assertEquals(Direction.NATIVE, ClientAdapter.direction(762, 762));
        assertEquals(Direction.UP, ClientAdapter.direction(762, 763));
        assertEquals(Direction.UP, ClientAdapter.direction(762, 774));
        assertEquals(Direction.DOWN, ClientAdapter.direction(763, 762));
        assertEquals(Direction.DOWN, ClientAdapter.direction(774, 762));
    }

    @Test
    void versionName762Is1194() {
        assertEquals("1.19.4", ClientAdapter.versionName(762));
    }

    @Test
    void differencesFor762MentionConfigurationRegistryAndPlay() {
        List<Difference> differences = ClientAdapter.differences(762);
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
        // 762と763でパケットIDは0件変更。
        assertTrue(differences.stream()
                .anyMatch(d -> d.summary().contains("0件")),
                differences.toString());
        // レジストリ差分の数値（ブロック状態 +410、アイテム +27）。
        assertTrue(differences.stream()
                .anyMatch(d -> d.detail().contains("23,725")),
                differences.toString());
        assertTrue(differences.stream()
                .anyMatch(d -> d.detail().contains("1,228")),
                differences.toString());
        // 763と同じく設定フェーズが無い。
        assertTrue(differences.stream()
                .anyMatch(d -> d.area().equals("CONFIGURATION")
                        && d.detail().contains("設定フェーズ")),
                differences.toString());
    }

    @Test
    void differencesFor762AgainstInternal762AreEmpty() {
        assertTrue(ClientAdapter.differences(762, 762).isEmpty());
    }

    @Test
    void differencesFor762AgainstInternal763MentionNoConfigPhase() {
        List<Difference> differences = ClientAdapter.differences(762, 763);
        assertFalse(differences.isEmpty());
        assertTrue(differences.stream()
                .anyMatch(d -> d.area().equals("CONFIGURATION")
                        && d.summary().contains("設定フェーズが無い")),
                differences.toString());
        assertTrue(differences.stream()
                .anyMatch(d -> d.area().equals("PLAY")),
                differences.toString());
        assertTrue(differences.stream()
                .noneMatch(d -> d.handling() == Handling.NOT_SUPPORTED),
                differences.toString());
    }

    @Test
    void differencesFor762AgainstInternal764AreNotUnsupported() {
        // 内部764＋762クライアントは decide() が受け入れるので、
        // 2引数版 differences() も NOT_SUPPORTED を返してはいけない。
        List<Difference> differences = ClientAdapter.differences(762, 764);
        assertFalse(differences.isEmpty());
        assertTrue(differences.stream()
                .noneMatch(d -> d.handling() == Handling.NOT_SUPPORTED),
                differences.toString());
        assertTrue(differences.stream()
                .anyMatch(d -> d.area().equals("CONFIGURATION")),
                differences.toString());
    }

    @Test
    void differencesFor762AgainstInternal774AreNotUnsupported() {
        List<Difference> differences = ClientAdapter.differences(762, 774);
        assertFalse(differences.isEmpty());
        assertTrue(differences.stream()
                .noneMatch(d -> d.handling() == Handling.NOT_SUPPORTED),
                differences.toString());
        assertTrue(differences.stream()
                .anyMatch(d -> d.area().equals("CONFIGURATION")),
                differences.toString());
        assertTrue(differences.stream()
                .anyMatch(d -> d.area().equals("PLAY")),
                differences.toString());
    }

    @Test
    void rewriteStatusJsonFor762Client() {
        String json765 = "{\"version\":{\"name\":\"1.20.4\",\"protocol\":765},"
                + "\"players\":{\"max\":20,\"online\":0}}";
        String rewritten = ClientAdapter.rewriteStatusJson(json765, 765, 762);
        assertTrue(rewritten.contains("\"protocol\":762"), rewritten);
        assertTrue(rewritten.contains("\"name\":\"1.19.4\""), rewritten);
    }
}
