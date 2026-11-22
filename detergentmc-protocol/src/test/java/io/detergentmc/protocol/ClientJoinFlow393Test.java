package io.detergentmc.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.detergentmc.protocol.ClientJoinFlow.Stage;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 393（1.13）クライアントの参加フロー。設定フェーズは無い（401／404／477／578／758と同じ）。 */
class ClientJoinFlow393Test {

    @Test
    void stagesFor393HaveNoConfiguration() {
        ClientJoinFlow flow = new ClientJoinFlow(393);
        assertEquals(List.of(Stage.HANDSHAKE, Stage.LOGIN,
                Stage.PLAY, Stage.JOINED, Stage.IN_GAME), flow.stages());
        assertFalse(flow.hasConfigurationPhase());
        assertEquals(new ClientJoinFlow(401).stages(), flow.stages());
        assertEquals(new ClientJoinFlow(404).stages(), flow.stages());
    }

    @Test
    void fullFlowToInGame() {
        ClientJoinFlow flow = new ClientJoinFlow(393);
        assertEquals(Stage.HANDSHAKE, flow.current());
        flow.handshakeReceived();
        flow.loginSucceeded();
        assertEquals(Stage.PLAY, flow.current());
        flow.joinGameReceived();
        assertEquals(Stage.JOINED, flow.current());
        flow.spawned();
        assertEquals(Stage.IN_GAME, flow.current());
        assertTrue(flow.canChat());
        assertTrue(flow.canMove());
    }

    @Test
    void unsupportedProtocolsAreStillRejected() {
        assertThrows(IllegalArgumentException.class, () -> new ClientJoinFlow(392));
        assertThrows(IllegalArgumentException.class, () -> new ClientJoinFlow(394));
        assertThrows(IllegalArgumentException.class, () -> new ClientJoinFlow(400));
        assertThrows(IllegalArgumentException.class, () -> new ClientJoinFlow(402));
        assertThrows(IllegalArgumentException.class, () -> new ClientJoinFlow(476));
        assertThrows(IllegalArgumentException.class, () -> new ClientJoinFlow(777));
    }
}
