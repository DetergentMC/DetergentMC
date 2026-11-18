package io.detergentmc.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.detergentmc.protocol.ClientJoinFlow.Stage;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 480（1.14.1）クライアントの参加フロー。設定フェーズは無い（490／578／758と同じ）。 */
class ClientJoinFlow480Test {

    @Test
    void stagesFor480HaveNoConfiguration() {
        ClientJoinFlow flow = new ClientJoinFlow(480);
        assertEquals(List.of(Stage.HANDSHAKE, Stage.LOGIN,
                Stage.PLAY, Stage.JOINED, Stage.IN_GAME), flow.stages());
        assertFalse(flow.hasConfigurationPhase());
        assertEquals(new ClientJoinFlow(490).stages(), flow.stages());
    }

    @Test
    void fullFlowToInGame() {
        ClientJoinFlow flow = new ClientJoinFlow(480);
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
        assertThrows(IllegalArgumentException.class, () -> new ClientJoinFlow(479));
        assertThrows(IllegalArgumentException.class, () -> new ClientJoinFlow(489));
        assertThrows(IllegalArgumentException.class, () -> new ClientJoinFlow(574));
        assertThrows(IllegalArgumentException.class, () -> new ClientJoinFlow(777));
    }
}
