package io.detergentmc.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.detergentmc.protocol.ClientJoinFlow.Stage;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 758（1.18.2）クライアントの参加フロー。設定フェーズは無い（759・760・761・762・763と同じ）。 */
class ClientJoinFlow758Test {

    @Test
    void stagesFor758HaveNoConfigurationLike759() {
        ClientJoinFlow flow = new ClientJoinFlow(758);
        assertEquals(List.of(Stage.HANDSHAKE, Stage.LOGIN,
                Stage.PLAY, Stage.JOINED, Stage.IN_GAME), flow.stages());
        assertFalse(flow.hasConfigurationPhase());
        // 759・760・761・762・763と段階の順番は同じ（設定フェーズなし）。
        assertEquals(new ClientJoinFlow(759).stages(), flow.stages());
        assertEquals(new ClientJoinFlow(760).stages(), flow.stages());
        assertEquals(new ClientJoinFlow(761).stages(), flow.stages());
        assertEquals(new ClientJoinFlow(762).stages(), flow.stages());
        assertEquals(new ClientJoinFlow(763).stages(), flow.stages());
    }

    @Test
    void joinGameCanArriveRightAfterLoginFor758() {
        ClientJoinFlow flow = new ClientJoinFlow(758);
        flow.handshakeReceived();
        flow.loginSucceeded();
        assertEquals(Stage.PLAY, flow.current());
        flow.joinGameReceived();
        assertEquals(Stage.JOINED, flow.current());
    }

    @Test
    void fullFlowToInGame() {
        ClientJoinFlow flow = new ClientJoinFlow(758);
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
    void configurationFinishedIsIllegalFor758() {
        ClientJoinFlow flow = new ClientJoinFlow(758);
        flow.handshakeReceived();
        flow.loginSucceeded();
        assertThrows(IllegalStateException.class, flow::configurationFinished);
    }

    @Test
    void unsupportedProtocolsAreStillRejected() {
        assertThrows(IllegalArgumentException.class, () -> new ClientJoinFlow(757));
        assertThrows(IllegalArgumentException.class, () -> new ClientJoinFlow(777));
    }
}
