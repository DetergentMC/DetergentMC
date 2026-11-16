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
 * 757（1.18／1.18.1）クライアントの受け入れ判断。
 *
 * <p>Stage B で decide() による 757 クライアントの受け入れと
 * differences() の 757 対応が入った。Stage A の時点ではまだ受け入れが
 * 無かったが、このテストは受け入れ側の期待値になっている。
 */
class ClientAdapter757Test {

    @Test
    void decide757IsAcceptedViaAdapter() {
        Handshake handshake = ClientAdapter.decide(757);
        assertEquals(Decision.ACCEPT_VIA_ADAPTER, handshake.decision());
        assertTrue(handshake.accepted());
        assertTrue(handshake.needsTranslation());
        assertEquals(757, handshake.clientProtocol());
        assertTrue(handshake.message().contains("757"), handshake.message());
        // 設定フェーズは無い（1.20.2／764で追加）。パケットIDは759から123件変わる。
        assertTrue(handshake.message().contains("設定フェーズは無い"), handshake.message());
        assertTrue(handshake.message().contains("123件"), handshake.message());
    }

    @Test
    void decide757AgainstAllServerProtocols() {
        for (int server : new int[]{751, 757, 759, 760, 761, 762, 763, 764, 765, 766, 767, 768, 769, 770, 771,
                772, 773, 774, 775}) {
            Handshake handshake = ClientAdapter.decide(757, server);
            assertTrue(handshake.accepted(), "server=" + server);
            if (server == 757) {
                assertEquals(Decision.ACCEPT_NATIVE, handshake.decision());
                assertFalse(handshake.needsTranslation());
            } else {
                assertEquals(Decision.ACCEPT_VIA_ADAPTER, handshake.decision());
                assertTrue(handshake.needsTranslation());
            }
        }
        // 776より新しい／758（1.18.2は別作業）／752（未対応番号）は断る。
        assertFalse(ClientAdapter.decide(776, 757).accepted());
        assertFalse(ClientAdapter.decide(758, 757).accepted());
        assertFalse(ClientAdapter.decide(752, 757).accepted());
    }

    @Test
    void direction757IsComparisonOnly() {
        assertEquals(Direction.NATIVE, ClientAdapter.direction(757, 757));
        assertEquals(Direction.UP, ClientAdapter.direction(757, 759));
        assertEquals(Direction.UP, ClientAdapter.direction(757, 775));
        assertEquals(Direction.DOWN, ClientAdapter.direction(759, 757));
        assertEquals(Direction.DOWN, ClientAdapter.direction(775, 757));
    }

    @Test
    void versionName757Is118() {
        assertEquals("1.18", ClientAdapter.versionName(757));
    }

    @Test
    void supportedClientProtocolsInclude757() {
        List<Integer> supported = ClientAdapter.supportedClientProtocols();
        assertEquals(751, supported.get(0), supported.toString());
        assertTrue(supported.contains(751), supported.toString());
        assertTrue(supported.contains(757), supported.toString());
        assertFalse(supported.contains(758), supported.toString());
        assertTrue(supported.contains(759), supported.toString());
    }

    @Test
    void differencesFor757AgainstInternal757AreEmpty() {
        assertTrue(ClientAdapter.differences(757, 757).isEmpty());
    }

    @Test
    void differencesFor757MentionConfigurationRegistryAndPlay() {
        List<Difference> differences = ClientAdapter.differences(757);
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
        // 新規パケット5件、削除8件、パケットID変更123件（759から）。
        assertTrue(differences.stream()
                .anyMatch(d -> d.summary().contains("5件")),
                differences.toString());
        assertTrue(differences.stream()
                .anyMatch(d -> d.summary().contains("8件")),
                differences.toString());
        assertTrue(differences.stream()
                .anyMatch(d -> d.summary().contains("123件")),
                differences.toString());
        // レジストリ差分：ブロック状態 20,342→21,448、swift_sneak の追加。
        assertTrue(differences.stream()
                .anyMatch(d -> d.area().equals("REGISTRY")
                        && d.detail().contains("20,342")),
                differences.toString());
        assertTrue(differences.stream()
                .anyMatch(d -> d.detail().contains("swift_sneak")),
                differences.toString());
        // Join Game 0x23、Chunk Data 0x1F、pack_format 8。
        assertTrue(differences.stream()
                .anyMatch(d -> d.detail().contains("0x23")),
                differences.toString());
        assertTrue(differences.stream()
                .anyMatch(d -> d.detail().contains("pack_format 8")),
                differences.toString());
    }

    @Test
    void differencesFor757AgainstInternal759MentionNoConfiguration() {
        // 内部759＋757クライアントはどちらにも設定フェーズが無い。
        List<Difference> differences = ClientAdapter.differences(757, 759);
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
                        && d.summary().contains("123件")),
                differences.toString());
        assertTrue(differences.stream()
                .anyMatch(d -> d.detail().contains("pack_format 8")),
                differences.toString());
    }

    @Test
    void differencesFor757AgainstInternal764AreNotUnsupported() {
        // 内部764＋757クライアントは decide() が受け入れるので、
        // 2引数版 differences() も NOT_SUPPORTED を返してはいけない。
        List<Difference> differences = ClientAdapter.differences(757, 764);
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
        // 757 クライアントには pack_format 8 のパックを出す。
        assertTrue(differences.stream()
                .anyMatch(d -> d.detail().contains("pack_format 8")),
                differences.toString());
    }

    @Test
    void differencesForInternal757MentionConfiguration() {
        // 内部757＋759クライアントはどちらにも設定フェーズが無い。
        List<Difference> differences759 = ClientAdapter.differences(759, 757);
        assertFalse(differences759.isEmpty());
        assertTrue(differences759.stream()
                .noneMatch(d -> d.handling() == Handling.NOT_SUPPORTED),
                differences759.toString());
        assertTrue(differences759.stream()
                .anyMatch(d -> d.area().equals("CONFIGURATION")
                        && d.summary().contains("どちらにも設定フェーズが無い")),
                differences759.toString());
        // 内部757＋775クライアントは設定フェーズを合成する。
        List<Difference> differences775 = ClientAdapter.differences(775, 757);
        assertFalse(differences775.isEmpty());
        assertTrue(differences775.stream()
                .noneMatch(d -> d.handling() == Handling.NOT_SUPPORTED),
                differences775.toString());
        assertTrue(differences775.stream()
                .anyMatch(d -> d.area().equals("CONFIGURATION")
                        && d.summary().contains("合成する")),
                differences775.toString());
        // 内部757＋758クライアントは未対応。
        List<Difference> differences758 = ClientAdapter.differences(758, 757);
        assertTrue(differences758.stream()
                .anyMatch(d -> d.handling() == Handling.NOT_SUPPORTED),
                differences758.toString());
    }

    @Test
    void rewriteStatusJsonFor757Client() {
        String json765 = "{\"version\":{\"name\":\"1.20.4\",\"protocol\":765},"
                + "\"players\":{\"max\":20,\"online\":0}}";
        String rewritten = ClientAdapter.rewriteStatusJson(json765, 765, 757);
        assertTrue(rewritten.contains("\"protocol\":757"), rewritten);
        assertTrue(rewritten.contains("\"name\":\"1.18\""), rewritten);
    }
}
