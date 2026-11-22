package io.detergentmc.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.detergentmc.protocol.ClientJoinFlow.Stage;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 110（1.9.4）クライアントの参加フロー。設定フェーズは無い（335／340／393と同じ）。 */
class ClientJoinFlow110Test {

    @Test
    void stagesFor110HaveNoConfiguration() {
        ClientJoinFlow flow = new ClientJoinFlow(110);
        assertEquals(List.of(Stage.HANDSHAKE, Stage.LOGIN,
                Stage.PLAY, Stage.JOINED, Stage.IN_GAME), flow.stages());
        assertFalse(flow.hasConfigurationPhase());
        assertEquals(new ClientJoinFlow(335).stages(), flow.stages());
        assertEquals(new ClientJoinFlow(393).stages(), flow.stages());
    }

    @Test
    void fullFlowToInGame() {
        ClientJoinFlow flow = new ClientJoinFlow(110);
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
        // 314 と 317 はこのツリーでは未対応（110 は 1.9.4 として対応済み）。
        assertThrows(IllegalArgumentException.class, () -> new ClientJoinFlow(314));
        assertThrows(IllegalArgumentException.class, () -> new ClientJoinFlow(317));
        assertThrows(IllegalArgumentException.class, () -> new ClientJoinFlow(777));
    }
}
