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

/** 758（1.18.2）クライアントの受け入れ判断。最古の対応プロトコル。 */
class ClientAdapter758Test {

    @Test
    void decide758IsAcceptedViaAdapter() {
        Handshake handshake = ClientAdapter.decide(758);
        assertEquals(Decision.ACCEPT_VIA_ADAPTER, handshake.decision());
        assertTrue(handshake.accepted());
        assertTrue(handshake.needsTranslation());
        assertEquals(758, handshake.clientProtocol());
        assertTrue(handshake.message().contains("758"), handshake.message());
    }

    @Test
    void decide758AgainstAllServerProtocols() {
        for (int server : new int[]{758, 759, 760, 761, 762, 763, 764, 765, 766, 767, 768, 769, 770, 771, 772, 773, 774, 775, 776}) {
            Handshake handshake = ClientAdapter.decide(758, server);
            assertTrue(handshake.accepted(), "server=" + server);
            if (server == 758) {
                assertEquals(Decision.ACCEPT_NATIVE, handshake.decision());
                assertFalse(handshake.needsTranslation());
            } else {
                assertEquals(Decision.ACCEPT_VIA_ADAPTER, handshake.decision());
                assertTrue(handshake.needsTranslation());
            }
        }
        // 777より新しい／757より古いは断る。
        assertFalse(ClientAdapter.decide(777, 758).accepted());
        assertFalse(ClientAdapter.decide(734, 758).accepted());
    }

    @Test
    void direction758IsComparisonOnly() {
        assertEquals(Direction.NATIVE, ClientAdapter.direction(758, 758));
        assertEquals(Direction.UP, ClientAdapter.direction(758, 759));
        assertEquals(Direction.UP, ClientAdapter.direction(758, 775));
        assertEquals(Direction.DOWN, ClientAdapter.direction(759, 758));
        assertEquals(Direction.DOWN, ClientAdapter.direction(775, 758));
    }

    @Test
    void versionName758Is1182() {
        assertEquals("1.18.2", ClientAdapter.versionName(758));
    }

    @Test
    void oldestSupportedIs573And758IsFourth() {
        // 404（1.13.2）対応で最古は 404 になった。並びは 404・477・480・485・490・498・573・575・578・758…。
        assertEquals(4, ClientAdapter.OLDEST_SUPPORTED_CLIENT_PROTOCOL);
        assertEquals(ProtocolVersion.V1_7_2, ClientAdapter.OLDEST_SUPPORTED_CLIENT_PROTOCOL);
        List<Integer> supported = ClientAdapter.supportedClientProtocols();
        assertEquals(4, supported.get(0), supported.toString());
        assertEquals(5, supported.get(1), supported.toString());
        assertEquals(47, supported.get(2), supported.toString());
        assertEquals(107, supported.get(3), supported.toString());
        assertEquals(108, supported.get(4), supported.toString());
        assertEquals(109, supported.get(5), supported.toString());
        assertEquals(315, supported.get(8), supported.toString());
        assertEquals(316, supported.get(9), supported.toString());
        assertEquals(335, supported.get(10), supported.toString());
        assertEquals(338, supported.get(11), supported.toString());
        assertEquals(340, supported.get(12), supported.toString());
        assertEquals(393, supported.get(13), supported.toString());
        assertTrue(supported.contains(758), supported.toString());
        assertEquals(401, supported.get(14), supported.toString());
        assertEquals(404, supported.get(15), supported.toString());
        assertEquals(477, supported.get(16), supported.toString());
        assertEquals(480, supported.get(17), supported.toString());
        assertEquals(485, supported.get(18), supported.toString());
        assertEquals(490, supported.get(19), supported.toString());
        assertEquals(498, supported.get(20), supported.toString());
        assertEquals(573, supported.get(21), supported.toString());
        assertEquals(575, supported.get(22), supported.toString());
        assertEquals(578, supported.get(23), supported.toString());
        assertEquals(758, supported.get(32), supported.toString());
    }

    @Test
    void differencesFor758MentionConfigurationRegistryAndPlay() {
        List<Difference> differences = ClientAdapter.differences(758);
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
        // 759と758でパケットIDは123件変更。
        assertTrue(differences.stream()
                .anyMatch(d -> d.summary().contains("123件")),
                differences.toString());
        // レジストリはブロック状態 21,448→20,342、アイテム 1,152→1,101。
        assertTrue(differences.stream()
                .anyMatch(d -> d.detail().contains("20,342")),
                differences.toString());
        assertTrue(differences.stream()
                .anyMatch(d -> d.detail().contains("1,101")),
                differences.toString());
        // 759と同じく設定フェーズが無い。
        assertTrue(differences.stream()
                .anyMatch(d -> d.area().equals("CONFIGURATION")
                        && d.detail().contains("設定フェーズ")),
                differences.toString());
        // 758クライアントには pack_format 8 のパックを出す。
        assertTrue(differences.stream()
                .anyMatch(d -> d.area().equals("RESOURCE_PACK")
                        && d.detail().contains("pack_format 8")),
                differences.toString());
        // ログイン開始は署名なし（署名付きチャットは759で導入）。
        assertTrue(differences.stream()
                .anyMatch(d -> d.area().equals("LOGIN")
                        && d.summary().contains("署名なし")),
                differences.toString());
    }

    @Test
    void differencesFor758AgainstInternal758AreEmpty() {
        assertTrue(ClientAdapter.differences(758, 758).isEmpty());
    }

    @Test
    void differencesFor758AgainstInternal759MentionNoConfigPhase() {
        List<Difference> differences = ClientAdapter.differences(758, 759);
        assertFalse(differences.isEmpty());
        assertTrue(differences.stream()
                .anyMatch(d -> d.area().equals("CONFIGURATION")
                        && d.summary().contains("設定フェーズが無い")),
                differences.toString());
        assertTrue(differences.stream()
                .anyMatch(d -> d.area().equals("PLAY")
                        && d.summary().contains("123件")),
                differences.toString());
        assertTrue(differences.stream()
                .noneMatch(d -> d.handling() == Handling.NOT_SUPPORTED),
                differences.toString());
    }

    @Test
    void differencesFor758AgainstInternal765AreNotUnsupported() {
        // 内部765＋758クライアントは decide() が受け入れるので、
        // 2引数版 differences() も NOT_SUPPORTED を返してはいけない。
        List<Difference> differences = ClientAdapter.differences(758, 765);
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
    void rewriteStatusJsonFor758Client() {
        String json765 = "{\"version\":{\"name\":\"1.20.4\",\"protocol\":765},"
                + "\"players\":{\"max\":20,\"online\":0}}";
        String rewritten = ClientAdapter.rewriteStatusJson(json765, 765, 758);
        assertTrue(rewritten.contains("\"protocol\":758"), rewritten);
        assertTrue(rewritten.contains("\"name\":\"1.18.2\""), rewritten);
    }
}
