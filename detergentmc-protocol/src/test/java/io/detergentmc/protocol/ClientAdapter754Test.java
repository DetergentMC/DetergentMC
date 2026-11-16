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
 * 754（1.16.4／1.16.5）クライアントの受け入れ判断。
 *
 * <p>Stage B で decide() による 754 クライアントの受け入れと
 * differences() の 754 対応が入った。Stage A の時点ではまだ受け入れが
 * 無かったが、このテストは受け入れ側の期待値になっている。
 */
class ClientAdapter754Test {

    @Test
    void decide754IsAcceptedViaAdapter() {
        Handshake handshake = ClientAdapter.decide(754);
        assertEquals(Decision.ACCEPT_VIA_ADAPTER, handshake.decision());
        assertTrue(handshake.accepted());
        assertTrue(handshake.needsTranslation());
        assertEquals(754, handshake.clientProtocol());
        assertTrue(handshake.message().contains("754"), handshake.message());
        // 設定フェーズは無い（1.20.2／764で追加）。パケットIDは755から120件変更、レジストリも755と違う。
        assertTrue(handshake.message().contains("設定フェーズは無い"), handshake.message());
        assertTrue(handshake.message().contains("パケットIDは755から120件変わる"), handshake.message());
        assertTrue(handshake.message().contains("レジストリも755と違う"), handshake.message());
    }

    @Test
    void decide754AgainstAllServerProtocols() {
        for (int server : new int[]{753, 754, 755, 756, 757, 759, 760, 761, 762, 763, 764, 765, 766, 767, 768, 769, 770, 771,
                772, 773, 774, 775}) {
            Handshake handshake = ClientAdapter.decide(754, server);
            assertTrue(handshake.accepted(), "server=" + server);
            if (server == 754) {
                assertEquals(Decision.ACCEPT_NATIVE, handshake.decision());
                assertFalse(handshake.needsTranslation());
            } else {
                assertEquals(Decision.ACCEPT_VIA_ADAPTER, handshake.decision());
                assertTrue(handshake.needsTranslation());
            }
        }
        // 776より新しい／758（1.18.2は別作業）／752より古いは断る。
        assertFalse(ClientAdapter.decide(776, 754).accepted());
        assertFalse(ClientAdapter.decide(758, 754).accepted());
        assertFalse(ClientAdapter.decide(752, 754).accepted());
    }

    @Test
    void direction754IsComparisonOnly() {
        assertEquals(Direction.NATIVE, ClientAdapter.direction(754, 754));
        assertEquals(Direction.UP, ClientAdapter.direction(754, 755));
        assertEquals(Direction.UP, ClientAdapter.direction(754, 775));
        assertEquals(Direction.DOWN, ClientAdapter.direction(755, 754));
        assertEquals(Direction.DOWN, ClientAdapter.direction(775, 754));
    }

    @Test
    void versionName754Is1165() {
        assertEquals("1.16.5", ClientAdapter.versionName(754));
    }

    @Test
    void supportedClientProtocolsInclude754() {
        List<Integer> supported = ClientAdapter.supportedClientProtocols();
        assertEquals(753, supported.get(0), supported.toString());
        assertEquals(754, supported.get(1), supported.toString());
        assertTrue(supported.contains(754), supported.toString());
        assertFalse(supported.contains(758), supported.toString());
        assertTrue(supported.contains(753), supported.toString());
        assertTrue(supported.contains(755), supported.toString());
    }

    @Test
    void differencesFor754AgainstInternal754AreEmpty() {
        assertTrue(ClientAdapter.differences(754, 754).isEmpty());
    }

    @Test
    void differencesFor754MentionConfigurationRegistryAndPlay() {
        List<Difference> differences = ClientAdapter.differences(754);
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
        // 新規パケット0件、削除1件（sculk）、パケットID変更120件（755から）。
        assertTrue(differences.stream()
                .anyMatch(d -> d.area().equals("PLAY")
                        && d.summary().contains("120件")),
                differences.toString());
        assertTrue(differences.stream()
                .anyMatch(d -> d.detail().contains("sculk")),
                differences.toString());
        // レジストリ差分：ブロック状態 20,342→17,112、ブロック 898→763、アイテム 1,100→976、バイオーム 81→79。
        assertTrue(differences.stream()
                .anyMatch(d -> d.area().equals("REGISTRY")
                        && d.detail().contains("17,112")),
                differences.toString());
        assertTrue(differences.stream()
                .anyMatch(d -> d.area().equals("REGISTRY")
                        && d.detail().contains("81→79")),
                differences.toString());
        // Join Game 0x24、Chunk Data 0x20、pack_format 6。
        assertTrue(differences.stream()
                .anyMatch(d -> d.detail().contains("0x24")),
                differences.toString());
        assertTrue(differences.stream()
                .anyMatch(d -> d.detail().contains("0x20")),
                differences.toString());
        assertTrue(differences.stream()
                .anyMatch(d -> d.detail().contains("pack_format 6")),
                differences.toString());
    }

    @Test
    void differencesFor754AgainstInternal755MentionNoConfiguration() {
        // 内部755＋754クライアントはどちらにも設定フェーズが無い。
        // 756のStage Bで differences(756, 757) が NOT_SUPPORTED になっていた
        // バグの再発防止：differences(754, 755) も NOT_SUPPORTED でないこと。
        List<Difference> differences = ClientAdapter.differences(754, 755);
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
                        && d.summary().contains("120件")),
                differences.toString());
        assertTrue(differences.stream()
                .anyMatch(d -> d.detail().contains("pack_format 6")),
                differences.toString());
    }

    @Test
    void differencesFor754AgainstInternal756MentionNoConfiguration() {
        // 内部756＋754クライアントもどちらにも設定フェーズが無い。
        List<Difference> differences = ClientAdapter.differences(754, 756);
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
                        && d.summary().contains("二段変換")),
                differences.toString());
        assertTrue(differences.stream()
                .anyMatch(d -> d.detail().contains("pack_format 6")),
                differences.toString());
    }

    @Test
    void differencesFor754AgainstInternal757MentionNoConfiguration() {
        // 内部757＋754クライアントもどちらにも設定フェーズが無い。
        List<Difference> differences = ClientAdapter.differences(754, 757);
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
                        && d.summary().contains("三段変換")),
                differences.toString());
        assertTrue(differences.stream()
                .anyMatch(d -> d.detail().contains("pack_format 6")),
                differences.toString());
    }

    @Test
    void differencesFor754AgainstInternal764AreNotUnsupported() {
        // 内部764＋754クライアントは decide() が受け入れるので、
        // 2引数版 differences() も NOT_SUPPORTED を返してはいけない。
        List<Difference> differences = ClientAdapter.differences(754, 764);
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
        // 754 クライアントには pack_format 6 のパックを出す。
        assertTrue(differences.stream()
                .anyMatch(d -> d.detail().contains("pack_format 6")),
                differences.toString());
    }

    @Test
    void differencesFor753AreNowSupported() {
        // 753（1.16.3）は Stage B で受け入れた（754と同じ段取り）。
        assertEquals(Decision.ACCEPT_VIA_ADAPTER, ClientAdapter.decide(753).decision());
        List<Difference> differences = ClientAdapter.differences(753);
        assertFalse(differences.isEmpty());
        assertTrue(differences.stream()
                .noneMatch(d -> d.handling() == Handling.NOT_SUPPORTED),
                differences.toString());
    }

    @Test
    void rewriteStatusJsonFor754Client() {
        String json765 = "{\"version\":{\"name\":\"1.20.4\",\"protocol\":765},"
                + "\"players\":{\"max\":20,\"online\":0}}";
        String rewritten = ClientAdapter.rewriteStatusJson(json765, 765, 754);
        assertTrue(rewritten.contains("\"protocol\":754"), rewritten);
        assertTrue(rewritten.contains("\"name\":\"1.16.5\""), rewritten);
    }
}
