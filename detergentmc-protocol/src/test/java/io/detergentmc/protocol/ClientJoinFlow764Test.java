package io.detergentmc.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.detergentmc.protocol.ClientJoinFlow.Stage;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 764（1.20.2）の参加フロー：763と違い設定フェーズがある。 */
class ClientJoinFlow764Test {

    @Test
    void stagesFor764IncludeConfiguration() {
        ClientJoinFlow flow = new ClientJoinFlow(764);
        assertEquals(List.of(Stage.HANDSHAKE, Stage.LOGIN, Stage.CONFIGURATION,
                Stage.PLAY, Stage.JOINED, Stage.IN_GAME), flow.stages());
        assertTrue(flow.hasConfigurationPhase());
        assertEquals(764, flow.clientProtocol());
    }

    @Test
    void fullWalkFor764() {
        ClientJoinFlow flow = new ClientJoinFlow(764);
        assertEquals(Stage.HANDSHAKE, flow.current());
        flow.handshakeReceived();
        assertEquals(Stage.LOGIN, flow.current());
        flow.loginSucceeded();
        assertEquals(Stage.CONFIGURATION, flow.current());
        assertFalse(flow.canChat());
        flow.configurationFinished();
        assertEquals(Stage.PLAY, flow.current());
        flow.joinGameReceived();
        assertEquals(Stage.JOINED, flow.current());
        flow.spawned();
        assertEquals(Stage.IN_GAME, flow.current());
        assertTrue(flow.isInGame());
        assertTrue(flow.canChat());
        assertTrue(flow.canMove());
    }

    @Test
    void configurationCannotBeSkippedFor764() {
        ClientJoinFlow flow = new ClientJoinFlow(764);
        flow.handshakeReceived();
        flow.loginSucceeded();
        assertThrows(IllegalStateException.class, flow::joinGameReceived);
    }

    @Test
    void stagesFor763And765AreUnchanged() {
        assertEquals(List.of(Stage.HANDSHAKE, Stage.LOGIN, Stage.PLAY,
                Stage.JOINED, Stage.IN_GAME), new ClientJoinFlow(763).stages());
        assertFalse(new ClientJoinFlow(763).hasConfigurationPhase());
        assertEquals(List.of(Stage.HANDSHAKE, Stage.LOGIN, Stage.CONFIGURATION,
                Stage.PLAY, Stage.JOINED, Stage.IN_GAME), new ClientJoinFlow(765).stages());
        assertTrue(new ClientJoinFlow(765).hasConfigurationPhase());
    }

    @Test
    void unsupportedProtocolsAreStillRejected() {
        assertThrows(IllegalArgumentException.class, () -> new ClientJoinFlow(758));
        assertThrows(IllegalArgumentException.class, () -> new ClientJoinFlow(776));
    }
}
