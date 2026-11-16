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
 * 736（1.16.1）クライアントの受け入れ判断。
 *
 * <p>Stage B で decide() による 736 クライアントの受け入れと
 * differences() の 736 対応が入った。Stage A の時点ではまだ受け入れが
 * 無かったが、このテストは受け入れ側の期待値になっている。
 */
class ClientAdapter736Test {

    @Test
    void decide736IsAcceptedViaAdapter() {
        Handshake handshake = ClientAdapter.decide(736);
        assertEquals(Decision.ACCEPT_VIA_ADAPTER, handshake.decision());
        assertTrue(handshake.accepted());
        assertTrue(handshake.needsTranslation());
        assertEquals(736, handshake.clientProtocol());
        assertTrue(handshake.message().contains("736"), handshake.message());
        // 設定フェーズは無い（1.20.2／764で追加）。パケットIDは751から62件変更、レジストリも751と違う。
        assertTrue(handshake.message().contains("設定フェーズは無い"), handshake.message());
        assertTrue(handshake.message().contains("パケットIDは751から62件変わる"), handshake.message());
        assertTrue(handshake.message().contains("レジストリも751と違う"), handshake.message());
        // Join Game は 0x25、pack_format は 5。
        assertTrue(handshake.message().contains("0x25"), handshake.message());
        assertTrue(handshake.message().contains("pack_format は 5"), handshake.message());
    }

    @Test
    void decide736AgainstAllServerProtocols() {
        for (int server : new int[]{736, 751, 753, 754, 755, 756, 757, 759, 760, 761, 762, 763, 764, 765, 766, 767, 768, 769, 770, 771,
                772, 773, 774, 775}) {
            Handshake handshake = ClientAdapter.decide(736, server);
            assertTrue(handshake.accepted(), "server=" + server);
            if (server == 736) {
                assertEquals(Decision.ACCEPT_NATIVE, handshake.decision());
                assertFalse(handshake.needsTranslation());
            } else {
                assertEquals(Decision.ACCEPT_VIA_ADAPTER, handshake.decision());
                assertTrue(handshake.needsTranslation());
            }
        }
        // 776より新しい／758（1.18.2は別作業）／735（1.16は次のループの対象）／752（未対応番号）は断る。
        assertFalse(ClientAdapter.decide(776, 736).accepted());
        assertFalse(ClientAdapter.decide(758, 736).accepted());
        assertFalse(ClientAdapter.decide(735, 736).accepted());
        assertFalse(ClientAdapter.decide(752, 736).accepted());
    }

    @Test
    void direction736IsComparisonOnly() {
        assertEquals(Direction.NATIVE, ClientAdapter.direction(736, 736));
        assertEquals(Direction.UP, ClientAdapter.direction(736, 751));
        assertEquals(Direction.UP, ClientAdapter.direction(736, 775));
        assertEquals(Direction.DOWN, ClientAdapter.direction(751, 736));
        assertEquals(Direction.DOWN, ClientAdapter.direction(775, 736));
    }

    @Test
    void versionName736Is1161() {
        assertEquals("1.16.1", ClientAdapter.versionName(736));
    }

    @Test
    void supportedClientProtocolsInclude736() {
        List<Integer> supported = ClientAdapter.supportedClientProtocols();
        assertEquals(736, supported.get(0), supported.toString());
        assertTrue(supported.contains(736), supported.toString());
        assertFalse(supported.contains(758), supported.toString());
        assertFalse(supported.contains(735), supported.toString());
        assertTrue(supported.contains(751), supported.toString());
    }

    @Test
    void differencesFor736AgainstInternal736AreEmpty() {
        assertTrue(ClientAdapter.differences(736, 736).isEmpty());
    }

    @Test
    void differencesFor736MentionConfigurationRegistryAndPlay() {
        List<Difference> differences = ClientAdapter.differences(736);
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
        // 新規パケット0件、削除1件、パケットID変更62件（751から）。
        assertTrue(differences.stream()
                .anyMatch(d -> d.area().equals("PLAY")
                        && d.summary().contains("62件")),
                differences.toString());
        // レジストリは751と違う：ブロック状態 17,104、ブロック 763、アイテム 975、バイオーム 79。
        assertTrue(differences.stream()
                .anyMatch(d -> d.area().equals("REGISTRY")
                        && d.detail().contains("17,104")),
                differences.toString());
        assertTrue(differences.stream()
                .anyMatch(d -> d.area().equals("REGISTRY")
                        && d.detail().contains("79→79")),
                differences.toString());
        // Join Game 0x25、Chunk Data 0x21、pack_format 5。Join Game は1,159バイトで751と違う。
        assertTrue(differences.stream()
                .anyMatch(d -> d.detail().contains("0x25")),
                differences.toString());
        assertTrue(differences.stream()
                .anyMatch(d -> d.detail().contains("0x21")),
                differences.toString());
        assertTrue(differences.stream()
                .anyMatch(d -> d.detail().contains("pack_format 5")),
                differences.toString());
        assertTrue(differences.stream()
                .anyMatch(d -> d.detail().contains("1,159")),
                differences.toString());
    }

    @Test
    void differencesFor736AgainstInternal751MentionNoConfiguration() {
        // 内部751＋736クライアントはどちらにも設定フェーズが無い。
        // 756のStage Bで differences(756, 757) が NOT_SUPPORTED になっていた
        // バグの再発防止：differences(736, 751) も NOT_SUPPORTED でないこと。
        List<Difference> differences = ClientAdapter.differences(736, 751);
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
                        && d.summary().contains("62件")),
                differences.toString());
        assertTrue(differences.stream()
                .anyMatch(d -> d.detail().contains("pack_format 5")),
                differences.toString());
    }

    @Test
    void differencesFor736AgainstInternal753And754MentionNoConfiguration() {
        // 内部753／754＋736クライアントもどちらにも設定フェーズが無い。
        for (int server : new int[]{753, 754}) {
            List<Difference> differences = ClientAdapter.differences(736, server);
            assertFalse(differences.isEmpty(), "server=" + server);
            assertTrue(differences.stream()
                    .noneMatch(d -> d.handling() == Handling.NOT_SUPPORTED),
                    differences.toString());
            assertTrue(differences.stream()
                    .anyMatch(d -> d.area().equals("CONFIGURATION")
                            && d.summary().contains("どちらにも設定フェーズが無い")),
                    differences.toString());
            assertTrue(differences.stream()
                    .anyMatch(d -> d.detail().contains("pack_format 5")),
                    differences.toString());
        }
        // 753向けは二段変換（736→751→753）、754向けは三段変換。
        assertTrue(ClientAdapter.differences(736, 753).stream()
                .anyMatch(d -> d.area().equals("PLAY")
                        && d.summary().contains("二段変換")),
                ClientAdapter.differences(736, 753).toString());
        assertTrue(ClientAdapter.differences(736, 754).stream()
                .anyMatch(d -> d.area().equals("PLAY")
                        && d.summary().contains("三段変換")),
                ClientAdapter.differences(736, 754).toString());
    }

    @Test
    void differencesFor736AgainstInternal755And757MentionNoConfiguration() {
        // 内部755／757＋736クライアントもどちらにも設定フェーズが無い。
        for (int server : new int[]{755, 757}) {
            List<Difference> differences = ClientAdapter.differences(736, server);
            assertFalse(differences.isEmpty(), "server=" + server);
            assertTrue(differences.stream()
                    .noneMatch(d -> d.handling() == Handling.NOT_SUPPORTED),
                    differences.toString());
            assertTrue(differences.stream()
                    .anyMatch(d -> d.area().equals("CONFIGURATION")
                            && d.summary().contains("どちらにも設定フェーズが無い")),
                    differences.toString());
            assertTrue(differences.stream()
                    .anyMatch(d -> d.detail().contains("pack_format 5")),
                    differences.toString());
        }
        // 755向けは四段変換（736→751→753→754→755）、757向けは六段変換。
        assertTrue(ClientAdapter.differences(736, 755).stream()
                .anyMatch(d -> d.area().equals("PLAY")
                        && d.summary().contains("四段変換")),
                ClientAdapter.differences(736, 755).toString());
        assertTrue(ClientAdapter.differences(736, 757).stream()
                .anyMatch(d -> d.area().equals("PLAY")
                        && d.summary().contains("六段変換")),
                ClientAdapter.differences(736, 757).toString());
    }

    @Test
    void differencesFor736AgainstInternal764AreNotUnsupported() {
        // 内部764＋736クライアントは decide() が受け入れるので、
        // 2引数版 differences() も NOT_SUPPORTED を返してはいけない。
        List<Difference> differences = ClientAdapter.differences(736, 764);
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
        // 736 クライアントには pack_format 5 のパックを出す。
        assertTrue(differences.stream()
                .anyMatch(d -> d.detail().contains("pack_format 5")),
                differences.toString());
    }

    @Test
    void differencesFor735AreNotSupported() {
        // 735（1.16）は次のループの対象で、まだ受け入れない。
        assertEquals(Decision.REJECT_OUTDATED, ClientAdapter.decide(735).decision());
        List<Difference> differences = ClientAdapter.differences(735);
        assertFalse(differences.isEmpty());
        assertTrue(differences.stream()
                .anyMatch(d -> d.handling() == Handling.NOT_SUPPORTED),
                differences.toString());
    }

    @Test
    void rewriteStatusJsonFor736Client() {
        String json765 = "{\"version\":{\"name\":\"1.20.4\",\"protocol\":765},"
                + "\"players\":{\"max\":20,\"online\":0}}";
        String rewritten = ClientAdapter.rewriteStatusJson(json765, 765, 736);
        assertTrue(rewritten.contains("\"protocol\":736"), rewritten);
        assertTrue(rewritten.contains("\"name\":\"1.16.1\""), rewritten);
    }
}
