package io.detergentmc.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.detergentmc.protocol.ClientJoinFlow.Stage;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 753（1.16.3）クライアントの参加フロー。設定フェーズは無い（754と同じ）。 */
class ClientJoinFlow753Test {

    @Test
    void stagesFor753HaveNoConfigurationLike754() {
        ClientJoinFlow flow = new ClientJoinFlow(753);
        assertEquals(List.of(Stage.HANDSHAKE, Stage.LOGIN,
                Stage.PLAY, Stage.JOINED, Stage.IN_GAME), flow.stages());
        assertFalse(flow.hasConfigurationPhase());
        // 754・755・756・757・759・760・761・762・763と段階の順番は同じ（設定フェーズなし）。
        assertEquals(new ClientJoinFlow(754).stages(), flow.stages());
        assertEquals(new ClientJoinFlow(755).stages(), flow.stages());
        assertEquals(new ClientJoinFlow(756).stages(), flow.stages());
        assertEquals(new ClientJoinFlow(757).stages(), flow.stages());
        assertEquals(new ClientJoinFlow(759).stages(), flow.stages());
        assertEquals(new ClientJoinFlow(760).stages(), flow.stages());
        assertEquals(new ClientJoinFlow(761).stages(), flow.stages());
        assertEquals(new ClientJoinFlow(762).stages(), flow.stages());
        assertEquals(new ClientJoinFlow(763).stages(), flow.stages());
    }

    @Test
    void joinGameCanArriveRightAfterLoginFor753() {
        ClientJoinFlow flow = new ClientJoinFlow(753);
        flow.handshakeReceived();
        flow.loginSucceeded();
        assertEquals(Stage.PLAY, flow.current());
        flow.joinGameReceived();
        assertEquals(Stage.JOINED, flow.current());
    }

    @Test
    void fullFlowToInGame() {
        ClientJoinFlow flow = new ClientJoinFlow(753);
        assertEquals(Stage.HANDSHAKE, flow.current());
        assertFalse(flow.canChat());
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
    void configurationFinishedIsIllegalFor753() {
        ClientJoinFlow flow = new ClientJoinFlow(753);
        flow.handshakeReceived();
        flow.loginSucceeded();
        assertThrows(IllegalStateException.class, flow::configurationFinished);
    }

    @Test
    void unsupportedProtocolsAreStillRejected() {
        assertThrows(IllegalArgumentException.class, () -> new ClientJoinFlow(752));
        assertThrows(IllegalArgumentException.class, () -> new ClientJoinFlow(758));
        assertThrows(IllegalArgumentException.class, () -> new ClientJoinFlow(776));
    }
}
