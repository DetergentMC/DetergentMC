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

/** 761（1.19.3）クライアントの受け入れ判断。 */
class ClientAdapter761Test {

    @Test
    void decide761IsAcceptedViaAdapter() {
        Handshake handshake = ClientAdapter.decide(761);
        assertEquals(Decision.ACCEPT_VIA_ADAPTER, handshake.decision());
        assertTrue(handshake.accepted());
        assertTrue(handshake.needsTranslation());
        assertEquals(761, handshake.clientProtocol());
        assertTrue(handshake.message().contains("761"), handshake.message());
    }

    @Test
    void decide761AgainstAllServerProtocols() {
        for (int server : new int[]{761, 762, 763, 764, 765, 766, 767, 768, 769, 770, 771, 772, 773, 774}) {
            Handshake handshake = ClientAdapter.decide(761, server);
            assertTrue(handshake.accepted(), "server=" + server);
            if (server == 761) {
                assertEquals(Decision.ACCEPT_NATIVE, handshake.decision());
                assertFalse(handshake.needsTranslation());
            } else {
                assertEquals(Decision.ACCEPT_VIA_ADAPTER, handshake.decision());
                assertTrue(handshake.needsTranslation());
            }
        }
        // 777より新しい／757より古いは断る。
        assertFalse(ClientAdapter.decide(777, 761).accepted());
        assertFalse(ClientAdapter.decide(734, 761).accepted());
    }

    @Test
    void direction761IsComparisonOnly() {
        assertEquals(Direction.NATIVE, ClientAdapter.direction(761, 761));
        assertEquals(Direction.UP, ClientAdapter.direction(761, 762));
        assertEquals(Direction.UP, ClientAdapter.direction(761, 774));
        assertEquals(Direction.DOWN, ClientAdapter.direction(762, 761));
        assertEquals(Direction.DOWN, ClientAdapter.direction(774, 761));
    }

    @Test
    void versionName761Is1193() {
        assertEquals("1.19.3", ClientAdapter.versionName(761));
    }

    @Test
    void differencesFor761MentionConfigurationRegistryAndPlay() {
        List<Difference> differences = ClientAdapter.differences(761);
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
        // 762と761でパケットIDは132件変更。
        assertTrue(differences.stream()
                .anyMatch(d -> d.summary().contains("132件")),
                differences.toString());
        // レジストリ差分の数値（ブロック状態 23,232→23,725、アイテム 1,186→1,228）。
        assertTrue(differences.stream()
                .anyMatch(d -> d.detail().contains("23,232")),
                differences.toString());
        assertTrue(differences.stream()
                .anyMatch(d -> d.detail().contains("1,186")),
                differences.toString());
        // 762と同じく設定フェーズが無い。
        assertTrue(differences.stream()
                .anyMatch(d -> d.area().equals("CONFIGURATION")
                        && d.detail().contains("設定フェーズ")),
                differences.toString());
        // 761クライアントには pack_format 12 のパックを出す。
        assertTrue(differences.stream()
                .anyMatch(d -> d.area().equals("RESOURCE_PACK")
                        && d.detail().contains("pack_format 12")),
                differences.toString());
    }

    @Test
    void differencesFor761AgainstInternal761AreEmpty() {
        assertTrue(ClientAdapter.differences(761, 761).isEmpty());
    }

    @Test
    void differencesFor761AgainstInternal762MentionNoConfigPhase() {
        List<Difference> differences = ClientAdapter.differences(761, 762);
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
    void differencesFor761AgainstInternal763MentionNoConfigPhase() {
        List<Difference> differences = ClientAdapter.differences(761, 763);
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
    void differencesFor761AgainstInternal764AreNotUnsupported() {
        // 内部764＋761クライアントは decide() が受け入れるので、
        // 2引数版 differences() も NOT_SUPPORTED を返してはいけない。
        List<Difference> differences = ClientAdapter.differences(761, 764);
        assertFalse(differences.isEmpty());
        assertTrue(differences.stream()
                .noneMatch(d -> d.handling() == Handling.NOT_SUPPORTED),
                differences.toString());
        assertTrue(differences.stream()
                .anyMatch(d -> d.area().equals("CONFIGURATION")),
                differences.toString());
    }

    @Test
    void differencesFor761AgainstInternal774AreNotUnsupported() {
        List<Difference> differences = ClientAdapter.differences(761, 774);
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
    void rewriteStatusJsonFor761Client() {
        String json765 = "{\"version\":{\"name\":\"1.20.4\",\"protocol\":765},"
                + "\"players\":{\"max\":20,\"online\":0}}";
        String rewritten = ClientAdapter.rewriteStatusJson(json765, 765, 761);
        assertTrue(rewritten.contains("\"protocol\":761"), rewritten);
        assertTrue(rewritten.contains("\"name\":\"1.19.3\""), rewritten);
    }
}
