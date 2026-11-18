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
 * 751（1.16.2）クライアントの受け入れ判断。
 *
 * <p>Stage B で decide() による 751 クライアントの受け入れと
 * differences() の 751 対応が入った。Stage A の時点ではまだ受け入れが
 * 無かったが、このテストは受け入れ側の期待値になっている。
 */
class ClientAdapter751Test {

    @Test
    void decide751IsAcceptedViaAdapter() {
        Handshake handshake = ClientAdapter.decide(751);
        assertEquals(Decision.ACCEPT_VIA_ADAPTER, handshake.decision());
        assertTrue(handshake.accepted());
        assertTrue(handshake.needsTranslation());
        assertEquals(751, handshake.clientProtocol());
        assertTrue(handshake.message().contains("751"), handshake.message());
        // 設定フェーズは無い（1.20.2／764で追加）。パケットIDは753から0件変更、レジストリも753と同一。
        assertTrue(handshake.message().contains("設定フェーズは無い"), handshake.message());
        assertTrue(handshake.message().contains("パケットIDは753から0件変わる"), handshake.message());
        assertTrue(handshake.message().contains("レジストリも753と同一"), handshake.message());
    }

    @Test
    void decide751AgainstAllServerProtocols() {
        for (int server : new int[]{735, 736, 751, 753, 754, 755, 756, 757, 759, 760, 761, 762, 763, 764, 765, 766, 767, 768, 769, 770, 771,
                772, 773, 774, 775}) {
            Handshake handshake = ClientAdapter.decide(751, server);
            assertTrue(handshake.accepted(), "server=" + server);
            if (server == 751) {
                assertEquals(Decision.ACCEPT_NATIVE, handshake.decision());
                assertFalse(handshake.needsTranslation());
            } else {
                assertEquals(Decision.ACCEPT_VIA_ADAPTER, handshake.decision());
                assertTrue(handshake.needsTranslation());
            }
        }
        // 776より新しい／758（1.18.2は別作業）／750（次のループの対象）は断る。
        assertFalse(ClientAdapter.decide(776, 751).accepted());
        assertFalse(ClientAdapter.decide(758, 751).accepted());
        assertFalse(ClientAdapter.decide(750, 751).accepted());
    }

    @Test
    void direction751IsComparisonOnly() {
        assertEquals(Direction.NATIVE, ClientAdapter.direction(751, 751));
        assertEquals(Direction.UP, ClientAdapter.direction(751, 753));
        assertEquals(Direction.UP, ClientAdapter.direction(751, 775));
        assertEquals(Direction.DOWN, ClientAdapter.direction(753, 751));
        assertEquals(Direction.DOWN, ClientAdapter.direction(775, 751));
    }

    @Test
    void versionName751Is1162() {
        assertEquals("1.16.2", ClientAdapter.versionName(751));
    }

    @Test
    void supportedClientProtocolsInclude751() {
        List<Integer> supported = ClientAdapter.supportedClientProtocols();
        assertEquals(735, supported.get(0), supported.toString());
        assertEquals(736, supported.get(1), supported.toString());
        assertEquals(751, supported.get(2), supported.toString());
        assertTrue(supported.contains(751), supported.toString());
        assertFalse(supported.contains(758), supported.toString());
        assertFalse(supported.contains(750), supported.toString());
        assertTrue(supported.contains(753), supported.toString());
    }

    @Test
    void differencesFor751AgainstInternal751AreEmpty() {
        assertTrue(ClientAdapter.differences(751, 751).isEmpty());
    }

    @Test
    void differencesFor751MentionConfigurationRegistryAndPlay() {
        List<Difference> differences = ClientAdapter.differences(751);
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
        // 新規パケット0件、削除0件、パケットID変更0件（753から）。
        assertTrue(differences.stream()
                .anyMatch(d -> d.area().equals("PLAY")
                        && d.summary().contains("0件")),
                differences.toString());
        // レジストリは753と同一：ブロック状態 17,112、ブロック 763、アイテム 976、バイオーム 79。
        assertTrue(differences.stream()
                .anyMatch(d -> d.area().equals("REGISTRY")
                        && d.detail().contains("17,112")),
                differences.toString());
        assertTrue(differences.stream()
                .anyMatch(d -> d.area().equals("REGISTRY")
                        && d.detail().contains("79→79")),
                differences.toString());
        // Join Game 0x24、Chunk Data 0x20、pack_format 6。Join Game は30,847バイトで753と同一。
        assertTrue(differences.stream()
                .anyMatch(d -> d.detail().contains("0x24")),
                differences.toString());
        assertTrue(differences.stream()
                .anyMatch(d -> d.detail().contains("0x20")),
                differences.toString());
        assertTrue(differences.stream()
                .anyMatch(d -> d.detail().contains("pack_format 6")),
                differences.toString());
        assertTrue(differences.stream()
                .anyMatch(d -> d.detail().contains("30,847")),
                differences.toString());
    }

    @Test
    void differencesFor751AgainstInternal753MentionNoConfiguration() {
        // 内部753＋751クライアントはどちらにも設定フェーズが無い。
        // 756のStage Bで differences(756, 757) が NOT_SUPPORTED になっていた
        // バグの再発防止：differences(751, 753) も NOT_SUPPORTED でないこと。
        List<Difference> differences = ClientAdapter.differences(751, 753);
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
                .anyMatch(d -> d.detail().contains("pack_format 6")),
                differences.toString());
    }

    @Test
    void differencesFor751AgainstInternal754And755MentionNoConfiguration() {
        // 内部754／755＋751クライアントもどちらにも設定フェーズが無い。
        for (int server : new int[]{754, 755}) {
            List<Difference> differences = ClientAdapter.differences(751, server);
            assertFalse(differences.isEmpty(), "server=" + server);
            assertTrue(differences.stream()
                    .noneMatch(d -> d.handling() == Handling.NOT_SUPPORTED),
                    differences.toString());
            assertTrue(differences.stream()
                    .anyMatch(d -> d.area().equals("CONFIGURATION")
                            && d.summary().contains("どちらにも設定フェーズが無い")),
                    differences.toString());
            assertTrue(differences.stream()
                    .anyMatch(d -> d.detail().contains("pack_format 6")),
                    differences.toString());
        }
        // 754向けは二段変換（751→753→754）、755向けは三段変換。
        assertTrue(ClientAdapter.differences(751, 754).stream()
                .anyMatch(d -> d.area().equals("PLAY")
                        && d.summary().contains("二段変換")),
                ClientAdapter.differences(751, 754).toString());
        assertTrue(ClientAdapter.differences(751, 755).stream()
                .anyMatch(d -> d.area().equals("PLAY")
                        && d.summary().contains("三段変換")),
                ClientAdapter.differences(751, 755).toString());
    }

    @Test
    void differencesFor751AgainstInternal756And757MentionNoConfiguration() {
        // 内部756／757＋751クライアントもどちらにも設定フェーズが無い。
        for (int server : new int[]{756, 757}) {
            List<Difference> differences = ClientAdapter.differences(751, server);
            assertFalse(differences.isEmpty(), "server=" + server);
            assertTrue(differences.stream()
                    .noneMatch(d -> d.handling() == Handling.NOT_SUPPORTED),
                    differences.toString());
            assertTrue(differences.stream()
                    .anyMatch(d -> d.area().equals("CONFIGURATION")
                            && d.summary().contains("どちらにも設定フェーズが無い")),
                    differences.toString());
            assertTrue(differences.stream()
                    .anyMatch(d -> d.detail().contains("pack_format 6")),
                    differences.toString());
        }
        // 756向けは四段変換（751→753→754→755→756）、757向けは五段変換。
        assertTrue(ClientAdapter.differences(751, 756).stream()
                .anyMatch(d -> d.area().equals("PLAY")
                        && d.summary().contains("四段変換")),
                ClientAdapter.differences(751, 756).toString());
        assertTrue(ClientAdapter.differences(751, 757).stream()
                .anyMatch(d -> d.area().equals("PLAY")
                        && d.summary().contains("五段変換")),
                ClientAdapter.differences(751, 757).toString());
    }

    @Test
    void differencesFor751AgainstInternal764AreNotUnsupported() {
        // 内部764＋751クライアントは decide() が受け入れるので、
        // 2引数版 differences() も NOT_SUPPORTED を返してはいけない。
        List<Difference> differences = ClientAdapter.differences(751, 764);
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
        // 751 クライアントには pack_format 6 のパックを出す。
        assertTrue(differences.stream()
                .anyMatch(d -> d.detail().contains("pack_format 6")),
                differences.toString());
    }

    @Test
    void differencesFor750AreNotSupported() {
        // 750（1.16.1）は次のループの対象で、まだ受け入れない。
        assertEquals(Decision.REJECT_OUTDATED, ClientAdapter.decide(750).decision());
        List<Difference> differences = ClientAdapter.differences(750);
        assertFalse(differences.isEmpty());
        assertTrue(differences.stream()
                .anyMatch(d -> d.handling() == Handling.NOT_SUPPORTED),
                differences.toString());
    }

    @Test
    void rewriteStatusJsonFor751Client() {
        String json765 = "{\"version\":{\"name\":\"1.20.4\",\"protocol\":765},"
                + "\"players\":{\"max\":20,\"online\":0}}";
        String rewritten = ClientAdapter.rewriteStatusJson(json765, 765, 751);
        assertTrue(rewritten.contains("\"protocol\":751"), rewritten);
        assertTrue(rewritten.contains("\"name\":\"1.16.2\""), rewritten);
    }
}
