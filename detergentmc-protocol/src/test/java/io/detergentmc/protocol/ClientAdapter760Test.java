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

/** 760（1.19.1／1.19.2）クライアントの受け入れ判断。 */
class ClientAdapter760Test {

    @Test
    void decide760IsAcceptedViaAdapter() {
        Handshake handshake = ClientAdapter.decide(760);
        assertEquals(Decision.ACCEPT_VIA_ADAPTER, handshake.decision());
        assertTrue(handshake.accepted());
        assertTrue(handshake.needsTranslation());
        assertEquals(760, handshake.clientProtocol());
        assertTrue(handshake.message().contains("760"), handshake.message());
    }

    @Test
    void decide760AgainstAllServerProtocols() {
        for (int server : new int[]{760, 761, 762, 763, 764, 765, 766, 767, 768, 769, 770, 771, 772, 773, 774}) {
            Handshake handshake = ClientAdapter.decide(760, server);
            assertTrue(handshake.accepted(), "server=" + server);
            if (server == 760) {
                assertEquals(Decision.ACCEPT_NATIVE, handshake.decision());
                assertFalse(handshake.needsTranslation());
            } else {
                assertEquals(Decision.ACCEPT_VIA_ADAPTER, handshake.decision());
                assertTrue(handshake.needsTranslation());
            }
        }
        // 777より新しい／757より古いは断る。
        assertFalse(ClientAdapter.decide(777, 760).accepted());
        assertFalse(ClientAdapter.decide(757, 760).accepted());
    }

    @Test
    void direction760IsComparisonOnly() {
        assertEquals(Direction.NATIVE, ClientAdapter.direction(760, 760));
        assertEquals(Direction.UP, ClientAdapter.direction(760, 761));
        assertEquals(Direction.UP, ClientAdapter.direction(760, 774));
        assertEquals(Direction.DOWN, ClientAdapter.direction(761, 760));
        assertEquals(Direction.DOWN, ClientAdapter.direction(774, 760));
    }

    @Test
    void versionName760Is1191() {
        assertEquals("1.19.1", ClientAdapter.versionName(760));
    }

    @Test
    void differencesFor760MentionConfigurationRegistryAndPlay() {
        List<Difference> differences = ClientAdapter.differences(760);
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
        // 761と760でパケットIDは118件変更。
        assertTrue(differences.stream()
                .anyMatch(d -> d.summary().contains("118件")),
                differences.toString());
        // レジストリ差分の数値（ブロック状態 21,448→23,232、アイテム 1,152→1,186）。
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
        // 760クライアントには pack_format 9 のパックを出す。
        assertTrue(differences.stream()
                .anyMatch(d -> d.area().equals("RESOURCE_PACK")
                        && d.detail().contains("pack_format 9")),
                differences.toString());
    }

    @Test
    void differencesFor760AgainstInternal760AreEmpty() {
        assertTrue(ClientAdapter.differences(760, 760).isEmpty());
    }

    @Test
    void differencesFor760AgainstInternal761MentionNoConfigPhase() {
        List<Difference> differences = ClientAdapter.differences(760, 761);
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
    void differencesFor760AgainstInternal762MentionNoConfigPhase() {
        List<Difference> differences = ClientAdapter.differences(760, 762);
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
    void differencesFor760AgainstInternal763MentionNoConfigPhase() {
        List<Difference> differences = ClientAdapter.differences(760, 763);
        assertFalse(differences.isEmpty());
        assertTrue(differences.stream()
                .anyMatch(d -> d.area().equals("CONFIGURATION")
                        && d.detail().contains("設定フェーズが無い")),
                differences.toString());
        assertTrue(differences.stream()
                .anyMatch(d -> d.area().equals("PLAY")),
                differences.toString());
        assertTrue(differences.stream()
                .noneMatch(d -> d.handling() == Handling.NOT_SUPPORTED),
                differences.toString());
    }

    @Test
    void differencesFor760AgainstInternal764AreNotUnsupported() {
        // 内部764＋760クライアントは decide() が受け入れるので、
        // 2引数版 differences() も NOT_SUPPORTED を返してはいけない。
        List<Difference> differences = ClientAdapter.differences(760, 764);
        assertFalse(differences.isEmpty());
        assertTrue(differences.stream()
                .noneMatch(d -> d.handling() == Handling.NOT_SUPPORTED),
                differences.toString());
        assertTrue(differences.stream()
                .anyMatch(d -> d.area().equals("CONFIGURATION")),
                differences.toString());
    }

    @Test
    void differencesFor760AgainstInternal774AreNotUnsupported() {
        List<Difference> differences = ClientAdapter.differences(760, 774);
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
    void rewriteStatusJsonFor760Client() {
        String json765 = "{\"version\":{\"name\":\"1.20.4\",\"protocol\":765},"
                + "\"players\":{\"max\":20,\"online\":0}}";
        String rewritten = ClientAdapter.rewriteStatusJson(json765, 765, 760);
        assertTrue(rewritten.contains("\"protocol\":760"), rewritten);
        assertTrue(rewritten.contains("\"name\":\"1.19.1\""), rewritten);
    }
}
