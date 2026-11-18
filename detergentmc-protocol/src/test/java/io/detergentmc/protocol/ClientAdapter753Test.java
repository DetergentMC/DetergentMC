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
 * 753（1.16.3）クライアントの受け入れ判断。
 *
 * <p>Stage B で decide() による 753 クライアントの受け入れと
 * differences() の 753 対応が入った。Stage A の時点ではまだ受け入れが
 * 無かったが、このテストは受け入れ側の期待値になっている。
 */
class ClientAdapter753Test {

    @Test
    void decide753IsAcceptedViaAdapter() {
        Handshake handshake = ClientAdapter.decide(753);
        assertEquals(Decision.ACCEPT_VIA_ADAPTER, handshake.decision());
        assertTrue(handshake.accepted());
        assertTrue(handshake.needsTranslation());
        assertEquals(753, handshake.clientProtocol());
        assertTrue(handshake.message().contains("753"), handshake.message());
        // 設定フェーズは無い（1.20.2／764で追加）。パケットIDは754から0件変更、レジストリも754と同一。
        assertTrue(handshake.message().contains("設定フェーズは無い"), handshake.message());
        assertTrue(handshake.message().contains("パケットIDは754から0件変わる"), handshake.message());
        assertTrue(handshake.message().contains("レジストリも754と同一"), handshake.message());
    }

    @Test
    void decide753AgainstAllServerProtocols() {
        for (int server : new int[]{735, 736, 751, 753, 754, 755, 756, 757, 759, 760, 761, 762, 763, 764, 765, 766, 767, 768, 769, 770, 771,
                772, 773, 774, 775}) {
            Handshake handshake = ClientAdapter.decide(753, server);
            assertTrue(handshake.accepted(), "server=" + server);
            if (server == 753) {
                assertEquals(Decision.ACCEPT_NATIVE, handshake.decision());
                assertFalse(handshake.needsTranslation());
            } else {
                assertEquals(Decision.ACCEPT_VIA_ADAPTER, handshake.decision());
                assertTrue(handshake.needsTranslation());
            }
        }
        // 776より新しい／758（1.18.2は別作業）／752（未対応番号）は断る。
        assertFalse(ClientAdapter.decide(776, 753).accepted());
        assertFalse(ClientAdapter.decide(758, 753).accepted());
        assertFalse(ClientAdapter.decide(752, 753).accepted());
    }

    @Test
    void direction753IsComparisonOnly() {
        assertEquals(Direction.NATIVE, ClientAdapter.direction(753, 753));
        assertEquals(Direction.UP, ClientAdapter.direction(753, 754));
        assertEquals(Direction.UP, ClientAdapter.direction(753, 775));
        assertEquals(Direction.DOWN, ClientAdapter.direction(754, 753));
        assertEquals(Direction.DOWN, ClientAdapter.direction(775, 753));
    }

    @Test
    void versionName753Is1163() {
        assertEquals("1.16.3", ClientAdapter.versionName(753));
    }

    @Test
    void supportedClientProtocolsInclude753() {
        List<Integer> supported = ClientAdapter.supportedClientProtocols();
        assertEquals(735, supported.get(0), supported.toString());
        assertEquals(736, supported.get(1), supported.toString());
        assertEquals(751, supported.get(2), supported.toString());
        assertEquals(753, supported.get(3), supported.toString());
        assertTrue(supported.contains(753), supported.toString());
        assertFalse(supported.contains(758), supported.toString());
        assertFalse(supported.contains(752), supported.toString());
        assertTrue(supported.contains(754), supported.toString());
    }

    @Test
    void differencesFor753AgainstInternal753AreEmpty() {
        assertTrue(ClientAdapter.differences(753, 753).isEmpty());
    }

    @Test
    void differencesFor753MentionConfigurationRegistryAndPlay() {
        List<Difference> differences = ClientAdapter.differences(753);
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
        // 新規パケット0件、削除0件、パケットID変更0件（754から）。
        assertTrue(differences.stream()
                .anyMatch(d -> d.area().equals("PLAY")
                        && d.summary().contains("0件")),
                differences.toString());
        // レジストリは754と同一：ブロック状態 17,112、ブロック 763、アイテム 976、バイオーム 79。
        assertTrue(differences.stream()
                .anyMatch(d -> d.area().equals("REGISTRY")
                        && d.detail().contains("17,112")),
                differences.toString());
        assertTrue(differences.stream()
                .anyMatch(d -> d.area().equals("REGISTRY")
                        && d.detail().contains("79→79")),
                differences.toString());
        // Join Game 0x24、Chunk Data 0x20、pack_format 6。Join Game は30,847バイトで754と同一。
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
    void differencesFor753AgainstInternal754MentionNoConfiguration() {
        // 内部754＋753クライアントはどちらにも設定フェーズが無い。
        // 756のStage Bで differences(756, 757) が NOT_SUPPORTED になっていた
        // バグの再発防止：differences(753, 754) も NOT_SUPPORTED でないこと。
        List<Difference> differences = ClientAdapter.differences(753, 754);
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
    void differencesFor753AgainstInternal755And756MentionNoConfiguration() {
        // 内部755／756＋753クライアントもどちらにも設定フェーズが無い。
        for (int server : new int[]{755, 756}) {
            List<Difference> differences = ClientAdapter.differences(753, server);
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
        // 755向けは二段変換（753→754→755）、756向けは三段変換。
        assertTrue(ClientAdapter.differences(753, 755).stream()
                .anyMatch(d -> d.area().equals("PLAY")
                        && d.summary().contains("二段変換")),
                ClientAdapter.differences(753, 755).toString());
        assertTrue(ClientAdapter.differences(753, 756).stream()
                .anyMatch(d -> d.area().equals("PLAY")
                        && d.summary().contains("三段変換")),
                ClientAdapter.differences(753, 756).toString());
    }

    @Test
    void differencesFor753AgainstInternal757MentionNoConfiguration() {
        // 内部757＋753クライアントもどちらにも設定フェーズが無い。
        List<Difference> differences = ClientAdapter.differences(753, 757);
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
                        && d.summary().contains("四段変換")),
                differences.toString());
        assertTrue(differences.stream()
                .anyMatch(d -> d.detail().contains("pack_format 6")),
                differences.toString());
    }

    @Test
    void differencesFor753AgainstInternal764AreNotUnsupported() {
        // 内部764＋753クライアントは decide() が受け入れるので、
        // 2引数版 differences() も NOT_SUPPORTED を返してはいけない。
        List<Difference> differences = ClientAdapter.differences(753, 764);
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
        // 753 クライアントには pack_format 6 のパックを出す。
        assertTrue(differences.stream()
                .anyMatch(d -> d.detail().contains("pack_format 6")),
                differences.toString());
    }

    @Test
    void differencesFor752AreNotSupported() {
        // 752はリリースの無い未対応番号で、まだ受け入れない（1.16.2は751）。
        assertEquals(Decision.REJECT_OUTDATED, ClientAdapter.decide(752).decision());
        List<Difference> differences = ClientAdapter.differences(752);
        assertFalse(differences.isEmpty());
        assertTrue(differences.stream()
                .anyMatch(d -> d.handling() == Handling.NOT_SUPPORTED),
                differences.toString());
    }

    @Test
    void rewriteStatusJsonFor753Client() {
        String json765 = "{\"version\":{\"name\":\"1.20.4\",\"protocol\":765},"
                + "\"players\":{\"max\":20,\"online\":0}}";
        String rewritten = ClientAdapter.rewriteStatusJson(json765, 765, 753);
        assertTrue(rewritten.contains("\"protocol\":753"), rewritten);
        assertTrue(rewritten.contains("\"name\":\"1.16.3\""), rewritten);
    }
}
