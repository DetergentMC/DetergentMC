package io.detergentmc.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.detergentmc.protocol.ClientAdapter.Decision;
import io.detergentmc.protocol.ClientAdapter.Difference;
import io.detergentmc.protocol.ClientAdapter.Handshake;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 764（1.20.2）クライアントの受け入れ判断。 */
class ClientAdapter764Test {

    @Test
    void decide764IsAcceptedViaAdapter() {
        Handshake handshake = ClientAdapter.decide(764);
        assertEquals(Decision.ACCEPT_VIA_ADAPTER, handshake.decision());
        assertTrue(handshake.accepted());
        assertTrue(handshake.needsTranslation());
        assertEquals(764, handshake.clientProtocol());
        assertTrue(handshake.message().contains("764"), handshake.message());
    }

    @Test
    void decideBoundariesAreUnchanged() {
        // 759より古い／776より新しいは断る。
        assertEquals(Decision.REJECT_OUTDATED, ClientAdapter.decide(758).decision());
        assertEquals(Decision.REJECT_OUTDATED, ClientAdapter.decide(340).decision());
        assertEquals(Decision.REJECT_TOO_NEW, ClientAdapter.decide(776).decision());
        assertEquals(Decision.ACCEPT_VIA_ADAPTER, ClientAdapter.decide(766).decision());
        assertEquals(Decision.ACCEPT_VIA_ADAPTER, ClientAdapter.decide(767).decision());
        assertEquals(Decision.ACCEPT_VIA_ADAPTER, ClientAdapter.decide(768).decision());
        assertEquals(Decision.ACCEPT_VIA_ADAPTER, ClientAdapter.decide(763).decision());
        assertEquals(Decision.ACCEPT_NATIVE, ClientAdapter.decide(765).decision());
    }

    @Test
    void supportedProtocolsInclude764() {
        assertEquals(List.of(753, 754, 755, 756, 757, 759, 760, 761, 762, 763, 764, 765, 766, 767, 768, 769, 770, 771, 772, 773, 774, 775), ClientAdapter.supportedClientProtocols());
    }

    @Test
    void differencesFor764MentionConfigurationRelay() {
        List<Difference> differences = ClientAdapter.differences(764);
        assertFalse(differences.isEmpty());
        assertTrue(differences.stream()
                .anyMatch(d -> d.area().equals("CONFIGURATION")
                        && d.detail().contains("中継")),
                differences.toString());
        assertTrue(differences.stream()
                .anyMatch(d -> d.area().equals("LOGIN")
                        && d.detail().contains("UUID")),
                differences.toString());
        assertTrue(differences.stream()
                .anyMatch(d -> d.area().equals("SPAWN")
                        && d.detail().contains("13")),
                differences.toString());
        assertTrue(differences.stream()
                .anyMatch(d -> d.area().equals("RESOURCE_PACK")
                        && d.detail().contains("UUID")),
                differences.toString());
    }

    @Test
    void differencesFor765AreStillEmptyAnd763AreStillPresent() {
        assertEquals(List.of(), ClientAdapter.differences(765));
        assertFalse(ClientAdapter.differences(763).isEmpty());
        assertTrue(ClientAdapter.differences(763).stream()
                .anyMatch(d -> d.area().equals("CONFIGURATION")));
    }
}
