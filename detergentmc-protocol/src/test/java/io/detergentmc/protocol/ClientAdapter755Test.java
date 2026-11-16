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
 * 755（1.17）クライアントの受け入れ判断。
 *
 * <p>Stage B で decide() による 755 クライアントの受け入れと
 * differences() の 755 対応が入った。Stage A の時点ではまだ受け入れが
 * 無かったが、このテストは受け入れ側の期待値になっている。
 */
class ClientAdapter755Test {

    @Test
    void decide755IsAcceptedViaAdapter() {
        Handshake handshake = ClientAdapter.decide(755);
        assertEquals(Decision.ACCEPT_VIA_ADAPTER, handshake.decision());
        assertTrue(handshake.accepted());
        assertTrue(handshake.needsTranslation());
        assertEquals(755, handshake.clientProtocol());
        assertTrue(handshake.message().contains("755"), handshake.message());
        // 設定フェーズは無い（1.20.2／764で追加）。パケットIDは756と同一、レジストリも756と同一。
        assertTrue(handshake.message().contains("設定フェーズは無い"), handshake.message());
        assertTrue(handshake.message().contains("パケットIDは756と同一"), handshake.message());
        assertTrue(handshake.message().contains("レジストリは756と同一"), handshake.message());
    }

    @Test
    void decide755AgainstAllServerProtocols() {
        for (int server : new int[]{755, 756, 757, 759, 760, 761, 762, 763, 764, 765, 766, 767, 768, 769, 770, 771,
                772, 773, 774, 775}) {
            Handshake handshake = ClientAdapter.decide(755, server);
            assertTrue(handshake.accepted(), "server=" + server);
            if (server == 755) {
                assertEquals(Decision.ACCEPT_NATIVE, handshake.decision());
                assertFalse(handshake.needsTranslation());
            } else {
                assertEquals(Decision.ACCEPT_VIA_ADAPTER, handshake.decision());
                assertTrue(handshake.needsTranslation());
            }
        }
        // 776より新しい／758（1.18.2は別作業）／754より古いは断る。
        assertFalse(ClientAdapter.decide(776, 755).accepted());
        assertFalse(ClientAdapter.decide(758, 755).accepted());
        assertFalse(ClientAdapter.decide(754, 755).accepted());
    }

    @Test
    void direction755IsComparisonOnly() {
        assertEquals(Direction.NATIVE, ClientAdapter.direction(755, 755));
        assertEquals(Direction.UP, ClientAdapter.direction(755, 756));
        assertEquals(Direction.UP, ClientAdapter.direction(755, 775));
        assertEquals(Direction.DOWN, ClientAdapter.direction(756, 755));
        assertEquals(Direction.DOWN, ClientAdapter.direction(775, 755));
    }

    @Test
    void versionName755Is117() {
        assertEquals("1.17", ClientAdapter.versionName(755));
    }

    @Test
    void supportedClientProtocolsInclude755() {
        List<Integer> supported = ClientAdapter.supportedClientProtocols();
        assertEquals(755, supported.get(0), supported.toString());
        assertTrue(supported.contains(755), supported.toString());
        assertFalse(supported.contains(758), supported.toString());
        assertFalse(supported.contains(754), supported.toString());
        assertTrue(supported.contains(756), supported.toString());
    }

    @Test
    void differencesFor755AgainstInternal755AreEmpty() {
        assertTrue(ClientAdapter.differences(755, 755).isEmpty());
    }

    @Test
    void differencesFor755MentionConfigurationRegistryAndPlay() {
        List<Difference> differences = ClientAdapter.differences(755);
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
        // 新規パケット0件、削除0件、パケットID変更0件（756から。756↔755は同一）。
        assertTrue(differences.stream()
                .anyMatch(d -> d.summary().contains("0件")),
                differences.toString());
        // レジストリ差分：ブロック状態 20,342→20,342（同一）、内部名の改名1件のみ。
        assertTrue(differences.stream()
                .anyMatch(d -> d.area().equals("REGISTRY")
                        && d.detail().contains("20,342")),
                differences.toString());
        assertTrue(differences.stream()
                .anyMatch(d -> d.detail().contains("destroy_entity")),
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
    void differencesFor755AgainstInternal756MentionNoConfiguration() {
        // 内部756＋755クライアントはどちらにも設定フェーズが無い。
        // 756のStage Bで differences(756, 757) が NOT_SUPPORTED になっていた
        // バグの再発防止：differences(755, 756) も NOT_SUPPORTED でないこと。
        List<Difference> differences = ClientAdapter.differences(755, 756);
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
                .anyMatch(d -> d.detail().contains("pack_format 7")),
                differences.toString());
    }

    @Test
    void differencesFor755AgainstInternal757MentionNoConfiguration() {
        // 内部757＋755クライアントもどちらにも設定フェーズが無い。
        List<Difference> differences = ClientAdapter.differences(755, 757);
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
                .anyMatch(d -> d.detail().contains("pack_format 7")),
                differences.toString());
    }

    @Test
    void differencesFor755AgainstInternal764AreNotUnsupported() {
        // 内部764＋755クライアントは decide() が受け入れるので、
        // 2引数版 differences() も NOT_SUPPORTED を返してはいけない。
        List<Difference> differences = ClientAdapter.differences(755, 764);
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
        // 755 クライアントには pack_format 7 のパックを出す。
        assertTrue(differences.stream()
                .anyMatch(d -> d.detail().contains("pack_format 7")),
                differences.toString());
    }

    @Test
    void differencesForInternal755MentionConfiguration() {
        // 内部755＋756クライアントはどちらにも設定フェーズが無い。
        List<Difference> differences756 = ClientAdapter.differences(756, 755);
        assertFalse(differences756.isEmpty());
        assertTrue(differences756.stream()
                .noneMatch(d -> d.handling() == Handling.NOT_SUPPORTED),
                differences756.toString());
        assertTrue(differences756.stream()
                .anyMatch(d -> d.area().equals("CONFIGURATION")
                        && d.summary().contains("どちらにも設定フェーズが無い")),
                differences756.toString());
        // 内部755＋775クライアントは設定フェーズを合成する。
        List<Difference> differences775 = ClientAdapter.differences(775, 755);
        assertFalse(differences775.isEmpty());
        assertTrue(differences775.stream()
                .noneMatch(d -> d.handling() == Handling.NOT_SUPPORTED),
                differences775.toString());
        assertTrue(differences775.stream()
                .anyMatch(d -> d.area().equals("CONFIGURATION")
                        && d.summary().contains("合成する")),
                differences775.toString());
        // 内部755＋758クライアントは未対応。
        List<Difference> differences758 = ClientAdapter.differences(758, 755);
        assertTrue(differences758.stream()
                .anyMatch(d -> d.handling() == Handling.NOT_SUPPORTED),
                differences758.toString());
    }

    @Test
    void rewriteStatusJsonFor755Client() {
        String json765 = "{\"version\":{\"name\":\"1.20.4\",\"protocol\":765},"
                + "\"players\":{\"max\":20,\"online\":0}}";
        String rewritten = ClientAdapter.rewriteStatusJson(json765, 765, 755);
        assertTrue(rewritten.contains("\"protocol\":755"), rewritten);
        assertTrue(rewritten.contains("\"name\":\"1.17\""), rewritten);
    }
}
