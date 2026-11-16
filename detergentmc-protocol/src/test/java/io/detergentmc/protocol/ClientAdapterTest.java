package io.detergentmc.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.detergentmc.protocol.ClientAdapter.Decision;
import io.detergentmc.protocol.ClientAdapter.Difference;
import io.detergentmc.protocol.ClientAdapter.Handling;
import io.detergentmc.protocol.ClientAdapter.Handshake;
import java.util.List;
import org.junit.jupiter.api.Test;

/** ハンドシェイク判定とバージョン差の一覧。 */
class ClientAdapterTest {

    @Test
    void accepts1204Natively() {
        Handshake handshake = ClientAdapter.decide(ProtocolVersion.V1_20_4);
        assertEquals(Decision.ACCEPT_NATIVE, handshake.decision());
        assertTrue(handshake.accepted());
        assertFalse(handshake.needsTranslation());
    }

    @Test
    void accepts1201ViaAdapter() {
        // 763を受け入れ、765扱いへの変換方針：サーバー内部は765のまま、
        // クライアントとの境界面だけで変換する。
        Handshake handshake = ClientAdapter.decide(ProtocolVersion.V1_20_1);
        assertEquals(Decision.ACCEPT_VIA_ADAPTER, handshake.decision());
        assertTrue(handshake.accepted());
        assertTrue(handshake.needsTranslation());
        assertEquals(763, handshake.clientProtocol());
    }

    @Test
    void rejectsOlderClients() {
        for (int protocol : List.of(47, 340, 754)) {
            Handshake handshake = ClientAdapter.decide(protocol);
            assertEquals(Decision.REJECT_OUTDATED, handshake.decision(),
                    "protocol " + protocol + " should be rejected as outdated");
            assertFalse(handshake.accepted());
        }
    }

    @Test
    void rejectsNewerClients() {
        Handshake handshake = ClientAdapter.decide(776);
        assertEquals(Decision.REJECT_TOO_NEW, handshake.decision());
        assertFalse(handshake.accepted());
    }

    @Test
    void accepts768Client() {
        Handshake handshake = ClientAdapter.decide(768);
        assertEquals(Decision.ACCEPT_VIA_ADAPTER, handshake.decision());
        assertTrue(handshake.accepted());
        assertTrue(handshake.message().contains("768"), handshake.message());
    }

    @Test
    void supportedProtocolsAre1201And1202And1204() {
        assertEquals(List.of(756, 757, 759, 760, 761, 762, 763, 764, 765, 766, 767, 768, 769, 770, 771, 772, 773, 774, 775), ClientAdapter.supportedClientProtocols());
    }

    @Test
    void differencesFor1204AreEmpty() {
        assertTrue(ClientAdapter.differences(ProtocolVersion.V1_20_4).isEmpty());
    }

    @Test
    void differencesFor1201CoverEveryArea() {
        List<Difference> differences = ClientAdapter.differences(ProtocolVersion.V1_20_1);
        // 設定フェーズは変換、ブロックは代替、チャット・移動は未検証。
        assertTrue(has(differences, "CONFIGURATION", Handling.TRANSLATED));
        assertTrue(has(differences, "BLOCK", Handling.FALLBACK));
        assertTrue(has(differences, "SPAWN", Handling.TRANSLATED));
        assertTrue(has(differences, "CHAT", Handling.NEEDS_VERIFICATION));
        assertTrue(has(differences, "MOVE", Handling.NEEDS_VERIFICATION));
        assertTrue(has(differences, "RESOURCE_PACK", Handling.TRANSLATED));
        // ハンドシェイクとログインは差がない。
        assertTrue(has(differences, "HANDSHAKE", Handling.UNAFFECTED));
        assertTrue(has(differences, "LOGIN", Handling.UNAFFECTED));
        // 変換できないものが無いことにはしない：未検証は未検証と書く。
        assertFalse(differences.stream()
                .anyMatch(d -> d.handling() == Handling.NOT_SUPPORTED));
    }

    @Test
    void differencesForUnsupportedProtocolSaySo() {
        List<Difference> differences = ClientAdapter.differences(340);
        assertEquals(1, differences.size());
        assertEquals(Handling.NOT_SUPPORTED, differences.get(0).handling());
    }

    @Test
    void protocolTranslatorExposesHandshakeAndJoinFlow() {
        ProtocolTranslator protocol = new ProtocolTranslator();
        assertEquals(Decision.ACCEPT_VIA_ADAPTER,
                protocol.decideHandshake(763).decision());
        assertEquals(ClientJoinFlow.Stage.HANDSHAKE,
                protocol.joinFlow(763).current());
        assertThrows(IllegalArgumentException.class, () -> protocol.joinFlow(340));
    }

    private static boolean has(List<Difference> differences, String area, Handling handling) {
        return differences.stream().anyMatch(d -> d.area().equals(area)
                && d.handling() == handling);
    }
}
