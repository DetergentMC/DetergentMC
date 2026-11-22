package io.detergentmc.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.detergentmc.protocol.ClientJoinFlow.Stage;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 766（1.20.5／1.20.6）クライアントの参加フロー。 */
class ClientJoinFlow766Test {

    @Test
    void stagesFor766IncludeConfiguration() {
        ClientJoinFlow flow = new ClientJoinFlow(766);
        assertEquals(List.of(Stage.HANDSHAKE, Stage.LOGIN, Stage.CONFIGURATION,
                Stage.PLAY, Stage.JOINED, Stage.IN_GAME), flow.stages());
        assertTrue(flow.hasConfigurationPhase());
    }

    @Test
    void configurationCannotBeSkippedFor766() {
        ClientJoinFlow flow = new ClientJoinFlow(766);
        flow.handshakeReceived();
        flow.loginSucceeded();
        assertThrows(IllegalStateException.class, flow::joinGameReceived);
        flow.configurationFinished();
        flow.joinGameReceived();
        assertEquals(Stage.JOINED, flow.current());
    }

    @Test
    void fullFlowToInGame() {
        ClientJoinFlow flow = new ClientJoinFlow(766);
        assertEquals(Stage.HANDSHAKE, flow.current());
        assertFalse(flow.canChat());
        flow.handshakeReceived();
        flow.loginSucceeded();
        assertEquals(Stage.CONFIGURATION, flow.current());
        flow.configurationFinished();
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
        assertThrows(IllegalArgumentException.class, () -> new ClientJoinFlow(757));
        assertThrows(IllegalArgumentException.class, () -> new ClientJoinFlow(777));
    }
}
