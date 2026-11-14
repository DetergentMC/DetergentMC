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

/** 771（1.21.6）クライアントの受け入れ判断。 */
class ClientAdapter771Test {

    @Test
    void decide771IsAcceptedViaAdapter() {
        Handshake handshake = ClientAdapter.decide(771);
        assertEquals(Decision.ACCEPT_VIA_ADAPTER, handshake.decision());
        assertTrue(handshake.accepted());
        assertTrue(handshake.needsTranslation());
        assertEquals(771, handshake.clientProtocol());
        assertTrue(handshake.message().contains("771"), handshake.message());
    }

    @Test
    void decide771AgainstAllServerProtocols() {
        for (int server : new int[]{763, 764, 765, 766, 767, 768, 769, 770, 771}) {
            Handshake handshake = ClientAdapter.decide(771, server);
            assertTrue(handshake.accepted(), "server=" + server);
            if (server == 771) {
                assertEquals(Decision.ACCEPT_NATIVE, handshake.decision());
                assertFalse(handshake.needsTranslation());
            } else {
                assertEquals(Decision.ACCEPT_VIA_ADAPTER, handshake.decision());
                assertTrue(handshake.needsTranslation());
            }
        }
        // 776より新しい／761より古いは断る。
        assertFalse(ClientAdapter.decide(776, 771).accepted());
        assertFalse(ClientAdapter.decide(758, 771).accepted());
    }

    @Test
    void direction771IsComparisonOnly() {
        assertEquals(Direction.NATIVE, ClientAdapter.direction(771, 771));
        assertEquals(Direction.UP, ClientAdapter.direction(763, 771));
        assertEquals(Direction.UP, ClientAdapter.direction(769, 771));
        assertEquals(Direction.DOWN, ClientAdapter.direction(771, 763));
        assertEquals(Direction.DOWN, ClientAdapter.direction(771, 769));
    }

    @Test
    void versionName771Is1216() {
        assertEquals("1.21.6", ClientAdapter.versionName(771));
    }

    @Test
    void differencesFor771MentionConfigurationRegistryAndNewPackets() {
        List<Difference> differences = ClientAdapter.differences(771);
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
        // Login Success の strict bool は770・771とも無い（詳細に記載）。
        assertTrue(differences.stream()
                .anyMatch(d -> d.area().equals("LOGIN")
                        && d.detail().contains("strictErrorHandling")),
                differences.toString());
        // 新規パケット3件、既存IDの変更122件。
        assertTrue(differences.stream()
                .anyMatch(d -> d.summary().contains("8件")),
                differences.toString());
        assertTrue(differences.stream()
                .anyMatch(d -> d.summary().contains("58件")),
                differences.toString());
        // tears（ジュークボックスの歌）の挿入。
        assertTrue(differences.stream()
                .anyMatch(d -> d.detail().contains("tears")),
                differences.toString());
    }

    @Test
    void differencesFor771AgainstInternal771AreEmpty() {
        assertTrue(ClientAdapter.differences(771, 771).isEmpty());
    }

    @Test
    void differencesFor771AgainstInternal769MentionConfiguration() {
        List<Difference> differences = ClientAdapter.differences(771, 769);
        assertFalse(differences.isEmpty());
        assertTrue(differences.stream()
                .anyMatch(d -> d.area().equals("CONFIGURATION")),
                differences.toString());
    }

    @Test
    void differencesFor771AgainstInternal764AreNotUnsupported() {
        // 内部764＋771クライアントは decide() が受け入れるので、
        // 2引数版 differences() も NOT_SUPPORTED を返してはいけない。
        List<Difference> differences = ClientAdapter.differences(771, 764);
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
    void rewriteStatusJsonFor771Client() {
        String json765 = "{\"version\":{\"name\":\"1.20.4\",\"protocol\":765},"
                + "\"players\":{\"max\":20,\"online\":0}}";
        String rewritten = ClientAdapter.rewriteStatusJson(json765, 765, 771);
        assertTrue(rewritten.contains("\"protocol\":771"), rewritten);
        assertTrue(rewritten.contains("\"name\":\"1.21.6\""), rewritten);
    }
}
