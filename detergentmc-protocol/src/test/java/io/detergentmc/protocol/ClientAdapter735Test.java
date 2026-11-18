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
 * 735（1.16）クライアントの受け入れ判断。
 *
 * <p>Stage B で decide() による 735 クライアントの受け入れと
 * differences() の 735 対応が入った。Stage A の時点ではまだ受け入れが
 * 無かったが、このテストは受け入れ側の期待値になっている。
 */
class ClientAdapter735Test {

    @Test
    void decide735IsAcceptedViaAdapter() {
        Handshake handshake = ClientAdapter.decide(735);
        assertEquals(Decision.ACCEPT_VIA_ADAPTER, handshake.decision());
        assertTrue(handshake.accepted());
        assertTrue(handshake.needsTranslation());
        assertEquals(735, handshake.clientProtocol());
        assertTrue(handshake.message().contains("735"), handshake.message());
        // 設定フェーズは無い（1.20.2／764で追加）。パケットIDは736と変わらない、レジストリも736と同一。
        assertTrue(handshake.message().contains("設定フェーズは無い"), handshake.message());
        assertTrue(handshake.message().contains("パケットIDは736と変わらない"), handshake.message());
        assertTrue(handshake.message().contains("レジストリも736と同一"), handshake.message());
        // Join Game は 0x25、pack_format は 5。
        assertTrue(handshake.message().contains("0x25"), handshake.message());
        assertTrue(handshake.message().contains("pack_format は 5"), handshake.message());
    }

    @Test
    void decide735AgainstAllServerProtocols() {
        for (int server : new int[]{735, 736, 751, 753, 754, 755, 756, 757, 759, 760, 761, 762, 763, 764, 765, 766, 767, 768, 769, 770, 771,
                772, 773, 774, 775}) {
            Handshake handshake = ClientAdapter.decide(735, server);
            assertTrue(handshake.accepted(), "server=" + server);
            if (server == 735) {
                assertEquals(Decision.ACCEPT_NATIVE, handshake.decision());
                assertFalse(handshake.needsTranslation());
            } else {
                assertEquals(Decision.ACCEPT_VIA_ADAPTER, handshake.decision());
                assertTrue(handshake.needsTranslation());
            }
        }
        // 776より新しい／758（1.18.2は別作業）／734（735より古い）／752（未対応番号）は断る。
        assertFalse(ClientAdapter.decide(776, 735).accepted());
        assertFalse(ClientAdapter.decide(758, 735).accepted());
        assertFalse(ClientAdapter.decide(734, 735).accepted());
        assertFalse(ClientAdapter.decide(752, 735).accepted());
    }

    @Test
    void direction735IsComparisonOnly() {
        assertEquals(Direction.NATIVE, ClientAdapter.direction(735, 735));
        assertEquals(Direction.UP, ClientAdapter.direction(735, 736));
        assertEquals(Direction.UP, ClientAdapter.direction(735, 775));
        assertEquals(Direction.DOWN, ClientAdapter.direction(736, 735));
        assertEquals(Direction.DOWN, ClientAdapter.direction(775, 735));
    }

    @Test
    void versionName735Is116() {
        assertEquals("1.16", ClientAdapter.versionName(735));
    }

    @Test
    void supportedClientProtocolsInclude735() {
        List<Integer> supported = ClientAdapter.supportedClientProtocols();
        assertEquals(735, supported.get(0), supported.toString());
        assertTrue(supported.contains(735), supported.toString());
        assertFalse(supported.contains(758), supported.toString());
        assertTrue(supported.contains(736), supported.toString());
        assertTrue(supported.contains(751), supported.toString());
    }

    @Test
    void differencesFor735AgainstInternal735AreEmpty() {
        assertTrue(ClientAdapter.differences(735, 735).isEmpty());
    }

    @Test
    void differencesFor735MentionConfigurationRegistryAndPlay() {
        List<Difference> differences = ClientAdapter.differences(735);
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
        // 新規パケット0件、削除0件、パケットID変更0件（736と同一）。
        assertTrue(differences.stream()
                .anyMatch(d -> d.area().equals("PLAY")
                        && d.summary().contains("0件")),
                differences.toString());
        // レジストリは736と同一：ブロック状態 17,104、ブロック 763、アイテム 975、バイオーム 79。
        assertTrue(differences.stream()
                .anyMatch(d -> d.area().equals("REGISTRY")
                        && d.detail().contains("17,104")),
                differences.toString());
        assertTrue(differences.stream()
                .anyMatch(d -> d.area().equals("REGISTRY")
                        && d.detail().contains("79→79")),
                differences.toString());
        // Join Game 0x25、Chunk Data 0x21、pack_format 5。Join Game は1,159バイトで736と同じ。
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
    void differencesFor735AgainstInternal736MentionNoConfiguration() {
        // 内部736＋735クライアントはどちらにも設定フェーズが無い。
        // 756のStage Bで differences(756, 757) が NOT_SUPPORTED になっていた
        // バグの再発防止：differences(735, 736) も NOT_SUPPORTED でないこと。
        List<Difference> differences = ClientAdapter.differences(735, 736);
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
                        && d.summary().contains("0件")),
                differences.toString());
        assertTrue(differences.stream()
                .anyMatch(d -> d.detail().contains("pack_format 5")),
                differences.toString());
    }

    @Test
    void differencesFor735AgainstInternal751And753MentionNoConfiguration() {
        // 内部751／753＋735クライアントもどちらにも設定フェーズが無い。
        for (int server : new int[]{751, 753}) {
            List<Difference> differences = ClientAdapter.differences(735, server);
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
        // 751向けは二段変換（735→736→751）、753向けは三段変換。
        assertTrue(ClientAdapter.differences(735, 751).stream()
                .anyMatch(d -> d.area().equals("PLAY")
                        && d.summary().contains("二段変換")),
                ClientAdapter.differences(735, 751).toString());
        assertTrue(ClientAdapter.differences(735, 753).stream()
                .anyMatch(d -> d.area().equals("PLAY")
                        && d.summary().contains("三段変換")),
                ClientAdapter.differences(735, 753).toString());
    }

    @Test
    void differencesFor735AgainstInternal754And755MentionNoConfiguration() {
        // 内部754／755＋735クライアントもどちらにも設定フェーズが無い。
        for (int server : new int[]{754, 755}) {
            List<Difference> differences = ClientAdapter.differences(735, server);
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
        // 754向けは四段変換（735→736→751→753→754）、755向けは五段変換。
        assertTrue(ClientAdapter.differences(735, 754).stream()
                .anyMatch(d -> d.area().equals("PLAY")
                        && d.summary().contains("四段変換")),
                ClientAdapter.differences(735, 754).toString());
        assertTrue(ClientAdapter.differences(735, 755).stream()
                .anyMatch(d -> d.area().equals("PLAY")
                        && d.summary().contains("五段変換")),
                ClientAdapter.differences(735, 755).toString());
    }

    @Test
    void differencesFor735AgainstInternal757And759GroupMentionNoConfiguration() {
        // 内部757／759〜763＋735クライアントもどちらにも設定フェーズが無い。
        for (int server : new int[]{757, 759, 760, 761, 762, 763}) {
            List<Difference> differences = ClientAdapter.differences(735, server);
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
        // 757向けは七段変換（735→736→751→753→754→755→756→757）。
        assertTrue(ClientAdapter.differences(735, 757).stream()
                .anyMatch(d -> d.area().equals("PLAY")
                        && d.summary().contains("七段変換")),
                ClientAdapter.differences(735, 757).toString());
    }

    @Test
    void differencesFor735AgainstInternal764AreNotUnsupported() {
        // 内部764＋735クライアントは decide() が受け入れるので、
        // 2引数版 differences() も NOT_SUPPORTED を返してはいけない。
        List<Difference> differences = ClientAdapter.differences(735, 764);
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
        // 735 クライアントには pack_format 5 のパックを出す。
        assertTrue(differences.stream()
                .anyMatch(d -> d.detail().contains("pack_format 5")),
                differences.toString());
    }

    @Test
    void rewriteStatusJsonFor735Client() {
        String json765 = "{\"version\":{\"name\":\"1.20.4\",\"protocol\":765},"
                + "\"players\":{\"max\":20,\"online\":0}}";
        String rewritten = ClientAdapter.rewriteStatusJson(json765, 765, 735);
        assertTrue(rewritten.contains("\"protocol\":735"), rewritten);
        assertTrue(rewritten.contains("\"name\":\"1.16\""), rewritten);
    }
}
