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

/** 759（1.19）クライアントの受け入れ判断。 */
class ClientAdapter759Test {

    @Test
    void decide759IsAcceptedViaAdapter() {
        Handshake handshake = ClientAdapter.decide(759);
        assertEquals(Decision.ACCEPT_VIA_ADAPTER, handshake.decision());
        assertTrue(handshake.accepted());
        assertTrue(handshake.needsTranslation());
        assertEquals(759, handshake.clientProtocol());
        assertTrue(handshake.message().contains("759"), handshake.message());
    }

    @Test
    void decide759AgainstAllServerProtocols() {
        for (int server : new int[]{759, 760, 761, 762, 763, 764, 765, 766, 767, 768, 769, 770, 771, 772, 773, 774}) {
            Handshake handshake = ClientAdapter.decide(759, server);
            assertTrue(handshake.accepted(), "server=" + server);
            if (server == 759) {
                assertEquals(Decision.ACCEPT_NATIVE, handshake.decision());
                assertFalse(handshake.needsTranslation());
            } else {
                assertEquals(Decision.ACCEPT_VIA_ADAPTER, handshake.decision());
                assertTrue(handshake.needsTranslation());
            }
        }
        // 776より新しい／758より古いは断る。
        assertFalse(ClientAdapter.decide(776, 759).accepted());
        assertFalse(ClientAdapter.decide(758, 759).accepted());
    }

    @Test
    void direction759IsComparisonOnly() {
        assertEquals(Direction.NATIVE, ClientAdapter.direction(759, 759));
        assertEquals(Direction.UP, ClientAdapter.direction(759, 760));
        assertEquals(Direction.UP, ClientAdapter.direction(759, 774));
        assertEquals(Direction.DOWN, ClientAdapter.direction(760, 759));
        assertEquals(Direction.DOWN, ClientAdapter.direction(774, 759));
    }

    @Test
    void versionName759Is119() {
        assertEquals("1.19", ClientAdapter.versionName(759));
    }

    @Test
    void supportedClientProtocolsInclude759And760() {
        // Stage B の教訓：OLDEST（759）だけでは760が漏れるため、
        // 760は明示する。両方とも含まれていること。
        List<Integer> supported = ClientAdapter.supportedClientProtocols();
        assertTrue(supported.contains(759), supported.toString());
        assertTrue(supported.contains(760), supported.toString());
        assertFalse(supported.contains(758), supported.toString());
    }

    @Test
    void differencesFor759MentionConfigurationRegistryAndPlay() {
        List<Difference> differences = ClientAdapter.differences(759);
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
        // 760と759でパケットIDは131件変更。
        assertTrue(differences.stream()
                .anyMatch(d -> d.summary().contains("131件")),
                differences.toString());
        // レジストリは全カテゴリ同一（ブロック状態 21,448→21,448、アイテム 1,152→1,152）。
        assertTrue(differences.stream()
                .anyMatch(d -> d.detail().contains("21,448")),
                differences.toString());
        assertTrue(differences.stream()
                .anyMatch(d -> d.detail().contains("1,152")),
                differences.toString());
        // 761と同じく設定フェーズが無い。
        assertTrue(differences.stream()
                .anyMatch(d -> d.area().equals("CONFIGURATION")
                        && d.detail().contains("設定フェーズ")),
                differences.toString());
        // 759クライアントには pack_format 9 のパックを出す。
        assertTrue(differences.stream()
                .anyMatch(d -> d.area().equals("RESOURCE_PACK")
                        && d.detail().contains("pack_format 9")),
                differences.toString());
        // 署名付きチャットは759で導入された（760ではない）。
        assertTrue(differences.stream()
                .anyMatch(d -> d.area().equals("LOGIN")
                        && d.summary().contains("759（1.19）で導入")),
                differences.toString());
    }

    @Test
    void differencesFor759AgainstInternal759AreEmpty() {
        assertTrue(ClientAdapter.differences(759, 759).isEmpty());
    }

    @Test
    void differencesFor759AgainstInternal760MentionNoConfigPhase() {
        List<Difference> differences = ClientAdapter.differences(759, 760);
        assertFalse(differences.isEmpty());
        assertTrue(differences.stream()
                .anyMatch(d -> d.area().equals("CONFIGURATION")
                        && d.summary().contains("設定フェーズが無い")),
                differences.toString());
        assertTrue(differences.stream()
                .anyMatch(d -> d.area().equals("PLAY")
                        && d.summary().contains("131件")),
                differences.toString());
        assertTrue(differences.stream()
                .noneMatch(d -> d.handling() == Handling.NOT_SUPPORTED),
                differences.toString());
    }

    @Test
    void differencesFor759AgainstInternal761MentionNoConfigPhase() {
        List<Difference> differences = ClientAdapter.differences(759, 761);
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
    void differencesFor759AgainstInternal763MentionNoConfigPhase() {
        List<Difference> differences = ClientAdapter.differences(759, 763);
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
    void differencesFor759AgainstInternal764AreNotUnsupported() {
        // 内部764＋759クライアントは decide() が受け入れるので、
        // 2引数版 differences() も NOT_SUPPORTED を返してはいけない。
        List<Difference> differences = ClientAdapter.differences(759, 764);
        assertFalse(differences.isEmpty());
        assertTrue(differences.stream()
                .noneMatch(d -> d.handling() == Handling.NOT_SUPPORTED),
                differences.toString());
        assertTrue(differences.stream()
                .anyMatch(d -> d.area().equals("CONFIGURATION")),
                differences.toString());
    }

    @Test
    void differencesFor759AgainstInternal774AreNotUnsupported() {
        List<Difference> differences = ClientAdapter.differences(759, 774);
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
    void rewriteStatusJsonFor759Client() {
        String json765 = "{\"version\":{\"name\":\"1.20.4\",\"protocol\":765},"
                + "\"players\":{\"max\":20,\"online\":0}}";
        String rewritten = ClientAdapter.rewriteStatusJson(json765, 765, 759);
        assertTrue(rewritten.contains("\"protocol\":759"), rewritten);
        assertTrue(rewritten.contains("\"name\":\"1.19\""), rewritten);
    }
}
