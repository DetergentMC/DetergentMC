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

/**
 * 756（1.17.1）クライアントの受け入れ判断。
 *
 * <p>Stage B で decide() による 756 クライアントの受け入れと
 * differences() の 756 対応が入った。Stage A の時点ではまだ受け入れが
 * 無かったが、このテストは受け入れ側の期待値になっている。
 */
class ClientAdapter756Test {

    @Test
    void decide756IsAcceptedViaAdapter() {
        Handshake handshake = ClientAdapter.decide(756);
        assertEquals(Decision.ACCEPT_VIA_ADAPTER, handshake.decision());
        assertTrue(handshake.accepted());
        assertTrue(handshake.needsTranslation());
        assertEquals(756, handshake.clientProtocol());
        assertTrue(handshake.message().contains("756"), handshake.message());
        // 設定フェーズは無い（1.20.2／764で追加）。パケットIDは757から16件変わる。
        assertTrue(handshake.message().contains("設定フェーズは無い"), handshake.message());
        assertTrue(handshake.message().contains("16件"), handshake.message());
        // simulation_distance（0x57）の削除による-1ずれ。
        assertTrue(handshake.message().contains("simulation_distance"), handshake.message());
    }

    @Test
    void decide756AgainstAllServerProtocols() {
        for (int server : new int[]{756, 757, 759, 760, 761, 762, 763, 764, 765, 766, 767, 768, 769, 770, 771,
                772, 773, 774, 775}) {
            Handshake handshake = ClientAdapter.decide(756, server);
            assertTrue(handshake.accepted(), "server=" + server);
            if (server == 756) {
                assertEquals(Decision.ACCEPT_NATIVE, handshake.decision());
                assertFalse(handshake.needsTranslation());
            } else {
                assertEquals(Decision.ACCEPT_VIA_ADAPTER, handshake.decision());
                assertTrue(handshake.needsTranslation());
            }
        }
        // 776より新しい／758（1.18.2は別作業）／752より古いは断る。
        assertFalse(ClientAdapter.decide(776, 756).accepted());
        assertFalse(ClientAdapter.decide(758, 756).accepted());
        assertFalse(ClientAdapter.decide(752, 756).accepted());
    }

    @Test
    void direction756IsComparisonOnly() {
        assertEquals(Direction.NATIVE, ClientAdapter.direction(756, 756));
        assertEquals(Direction.UP, ClientAdapter.direction(756, 757));
        assertEquals(Direction.UP, ClientAdapter.direction(756, 775));
        assertEquals(Direction.DOWN, ClientAdapter.direction(757, 756));
        assertEquals(Direction.DOWN, ClientAdapter.direction(775, 756));
    }

    @Test
    void versionName756Is1171() {
        assertEquals("1.17.1", ClientAdapter.versionName(756));
    }

    @Test
    void supportedClientProtocolsInclude756() {
        List<Integer> supported = ClientAdapter.supportedClientProtocols();
        assertEquals(753, supported.get(0), supported.toString());
        assertTrue(supported.contains(756), supported.toString());
        assertFalse(supported.contains(758), supported.toString());
        assertTrue(supported.contains(755), supported.toString());
        assertTrue(supported.contains(757), supported.toString());
    }

    @Test
    void differencesFor756AgainstInternal756AreEmpty() {
        assertTrue(ClientAdapter.differences(756, 756).isEmpty());
    }

    @Test
    void differencesFor756MentionConfigurationRegistryAndPlay() {
        List<Difference> differences = ClientAdapter.differences(756);
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
        // ログイン開始は名前だけで署名データは無い（署名付きチャットは759で導入）。
        assertTrue(differences.stream()
                .anyMatch(d -> d.area().equals("LOGIN")
                        && d.detail().contains("署名データ")),
                differences.toString());
        // 新規パケット0件、削除1件、パケットID変更16件（757から）。
        assertTrue(differences.stream()
                .anyMatch(d -> d.summary().contains("0件")),
                differences.toString());
        assertTrue(differences.stream()
                .anyMatch(d -> d.summary().contains("1件")),
                differences.toString());
        assertTrue(differences.stream()
                .anyMatch(d -> d.summary().contains("16件")),
                differences.toString());
        // レジストリ差分：ブロック状態 20,342→20,342（同一）、music_disc_otherside の追加。
        assertTrue(differences.stream()
                .anyMatch(d -> d.area().equals("REGISTRY")
                        && d.detail().contains("20,342")),
                differences.toString());
        assertTrue(differences.stream()
                .anyMatch(d -> d.detail().contains("music_disc_otherside")),
                differences.toString());
        // Join Game 0x26、Chunk Data 0x22、pack_format 7。
        assertTrue(differences.stream()
                .anyMatch(d -> d.detail().contains("0x26")),
                differences.toString());
        assertTrue(differences.stream()
                .anyMatch(d -> d.detail().contains("pack_format 7")),
                differences.toString());
    }

    @Test
    void differencesFor756AgainstInternal757MentionNoConfiguration() {
        // 内部757＋756クライアントはどちらにも設定フェーズが無い。
        List<Difference> differences = ClientAdapter.differences(756, 757);
        assertFalse(differences.isEmpty());
        assertTrue(differences.stream()
                .noneMatch(d -> d.handling() == Handling.NOT_SUPPORTED),
                differences.toString());
        assertTrue(differences.stream()
                .anyMatch(d -> d.area().equals("CONFIGURATION")
                        && d.summary().contains("どちらにも設定フェーズが無い")),
                differences.toString());
        assertTrue(differences.stream()
                .anyMatch(d -> d.area().equals("PLAY")
                        && d.summary().contains("16件")),
                differences.toString());
        assertTrue(differences.stream()
                .anyMatch(d -> d.detail().contains("pack_format 7")),
                differences.toString());
    }

    @Test
    void differencesFor756AgainstInternal764AreNotUnsupported() {
        // 内部764＋756クライアントは decide() が受け入れるので、
        // 2引数版 differences() も NOT_SUPPORTED を返してはいけない。
        List<Difference> differences = ClientAdapter.differences(756, 764);
        assertFalse(differences.isEmpty());
        assertTrue(differences.stream()
                .noneMatch(d -> d.handling() == Handling.NOT_SUPPORTED),
                differences.toString());
        assertTrue(differences.stream()
                .anyMatch(d -> d.area().equals("CONFIGURATION")),
                differences.toString());
        assertTrue(differences.stream()
                .anyMatch(d -> d.area().equals("PLAY")
                        && d.detail().contains("simulationDistance")),
                differences.toString());
        // 756 クライアントには pack_format 7 のパックを出す。
        assertTrue(differences.stream()
                .anyMatch(d -> d.detail().contains("pack_format 7")),
                differences.toString());
    }

    @Test
    void differencesForInternal756MentionConfiguration() {
        // 内部756＋759クライアントはどちらにも設定フェーズが無い。
        List<Difference> differences759 = ClientAdapter.differences(759, 756);
        assertFalse(differences759.isEmpty());
        assertTrue(differences759.stream()
                .noneMatch(d -> d.handling() == Handling.NOT_SUPPORTED),
                differences759.toString());
        assertTrue(differences759.stream()
                .anyMatch(d -> d.area().equals("CONFIGURATION")
                        && d.summary().contains("どちらにも設定フェーズが無い")),
                differences759.toString());
        // 内部756＋775クライアントは設定フェーズを合成する。
        List<Difference> differences775 = ClientAdapter.differences(775, 756);
        assertFalse(differences775.isEmpty());
        assertTrue(differences775.stream()
                .noneMatch(d -> d.handling() == Handling.NOT_SUPPORTED),
                differences775.toString());
        assertTrue(differences775.stream()
                .anyMatch(d -> d.area().equals("CONFIGURATION")
                        && d.summary().contains("合成する")),
                differences775.toString());
        // 内部756＋758クライアントは未対応。
        List<Difference> differences758 = ClientAdapter.differences(758, 756);
        assertTrue(differences758.stream()
                .anyMatch(d -> d.handling() == Handling.NOT_SUPPORTED),
                differences758.toString());
    }

    @Test
    void rewriteStatusJsonFor756Client() {
        String json765 = "{\"version\":{\"name\":\"1.20.4\",\"protocol\":765},"
                + "\"players\":{\"max\":20,\"online\":0}}";
        String rewritten = ClientAdapter.rewriteStatusJson(json765, 765, 756);
        assertTrue(rewritten.contains("\"protocol\":756"), rewritten);
        assertTrue(rewritten.contains("\"name\":\"1.17.1\""), rewritten);
    }
}
