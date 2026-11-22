package io.detergentmc.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.detergentmc.protocol.ClientJoinFlow.Stage;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 772（1.21.7）クライアントの参加フロー。 */
class ClientJoinFlow772Test {

    @Test
    void stagesFor772IncludeConfigurationLike771() {
        ClientJoinFlow flow = new ClientJoinFlow(772);
        assertEquals(List.of(Stage.HANDSHAKE, Stage.LOGIN, Stage.CONFIGURATION,
                Stage.PLAY, Stage.JOINED, Stage.IN_GAME), flow.stages());
        assertTrue(flow.hasConfigurationPhase());
        // 771と段階の順番は同じ（設定フェーズは771と同形）。
        assertEquals(new ClientJoinFlow(771).stages(), flow.stages());
    }

    @Test
    void configurationCannotBeSkippedFor772() {
        ClientJoinFlow flow = new ClientJoinFlow(772);
        flow.handshakeReceived();
        flow.loginSucceeded();
        assertThrows(IllegalStateException.class, flow::joinGameReceived);
        flow.configurationFinished();
        flow.joinGameReceived();
        assertEquals(Stage.JOINED, flow.current());
    }

    @Test
    void fullFlowToInGame() {
        ClientJoinFlow flow = new ClientJoinFlow(772);
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
        assertThrows(IllegalArgumentException.class, () -> new ClientJoinFlow(777));
    }
}
