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

/** 772（1.21.7）クライアントの受け入れ判断。 */
class ClientAdapter772Test {

    @Test
    void decide772IsAcceptedViaAdapter() {
        Handshake handshake = ClientAdapter.decide(772);
        assertEquals(Decision.ACCEPT_VIA_ADAPTER, handshake.decision());
        assertTrue(handshake.accepted());
        assertTrue(handshake.needsTranslation());
        assertEquals(772, handshake.clientProtocol());
        assertTrue(handshake.message().contains("772"), handshake.message());
    }

    @Test
    void decide772AgainstAllServerProtocols() {
        for (int server : new int[]{763, 764, 765, 766, 767, 768, 769, 770, 771, 772}) {
            Handshake handshake = ClientAdapter.decide(772, server);
            assertTrue(handshake.accepted(), "server=" + server);
            if (server == 772) {
                assertEquals(Decision.ACCEPT_NATIVE, handshake.decision());
                assertFalse(handshake.needsTranslation());
            } else {
                assertEquals(Decision.ACCEPT_VIA_ADAPTER, handshake.decision());
                assertTrue(handshake.needsTranslation());
            }
        }
        // 777より新しい／757より古いは断る。
        assertFalse(ClientAdapter.decide(777, 772).accepted());
        assertFalse(ClientAdapter.decide(734, 772).accepted());
    }

    @Test
    void direction772IsComparisonOnly() {
        assertEquals(Direction.NATIVE, ClientAdapter.direction(772, 772));
        assertEquals(Direction.UP, ClientAdapter.direction(763, 772));
        assertEquals(Direction.UP, ClientAdapter.direction(769, 772));
        assertEquals(Direction.DOWN, ClientAdapter.direction(772, 763));
        assertEquals(Direction.DOWN, ClientAdapter.direction(772, 769));
    }

    @Test
    void versionName772Is1217() {
        assertEquals("1.21.7", ClientAdapter.versionName(772));
    }

    @Test
    void differencesFor772MentionConfigurationRegistryAndNewPackets() {
        List<Difference> differences = ClientAdapter.differences(772);
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
        // Login Success の strict bool は771・772とも無い（詳細に記載）。
        assertTrue(differences.stream()
                .anyMatch(d -> d.area().equals("LOGIN")
                        && d.detail().contains("strictErrorHandling")),
                differences.toString());
        // 新規パケットは無く、ID変更も無い（247件すべて771と同一）。
        assertTrue(differences.stream()
                .anyMatch(d -> d.summary().contains("無い")),
                differences.toString());
        assertTrue(differences.stream()
                .anyMatch(d -> d.summary().contains("247件")),
                differences.toString());
        // lava_chicken（ジュークボックスの歌）の挿入。
        assertTrue(differences.stream()
                .anyMatch(d -> d.detail().contains("lava_chicken")),
                differences.toString());
    }

    @Test
    void differencesFor772AgainstInternal772AreEmpty() {
        assertTrue(ClientAdapter.differences(772, 772).isEmpty());
    }

    @Test
    void differencesFor772AgainstInternal769MentionConfiguration() {
        List<Difference> differences = ClientAdapter.differences(772, 769);
        assertFalse(differences.isEmpty());
        assertTrue(differences.stream()
                .anyMatch(d -> d.area().equals("CONFIGURATION")),
                differences.toString());
    }

    @Test
    void differencesFor772AgainstInternal764AreNotUnsupported() {
        // 内部764＋772クライアントは decide() が受け入れるので、
        // 2引数版 differences() も NOT_SUPPORTED を返してはいけない。
        List<Difference> differences = ClientAdapter.differences(772, 764);
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
    void rewriteStatusJsonFor772Client() {
        String json765 = "{\"version\":{\"name\":\"1.20.4\",\"protocol\":765},"
                + "\"players\":{\"max\":20,\"online\":0}}";
        String rewritten = ClientAdapter.rewriteStatusJson(json765, 765, 772);
        assertTrue(rewritten.contains("\"protocol\":772"), rewritten);
        assertTrue(rewritten.contains("\"name\":\"1.21.7\""), rewritten);
    }
}
