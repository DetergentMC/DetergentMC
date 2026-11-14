package io.detergentmc.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.detergentmc.protocol.ClientJoinFlow.Stage;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 参加フローの単体テスト。実機クライアントが無くても、プロトコルレベルで
 * 1.20.1クライアントの参加・スポーン・移動・チャットまでの流れを確認する。
 */
class ClientJoinFlowTest {

    /** 1.20.1クライアントの参加：設定フェーズなしでスポーンまで行ける。 */
    @Test
    void client1201JoinsWithoutConfigurationPhase() {
        ClientJoinFlow flow = new ClientJoinFlow(ProtocolVersion.V1_20_1);
        assertEquals(List.of(Stage.HANDSHAKE, Stage.LOGIN, Stage.PLAY,
                Stage.JOINED, Stage.IN_GAME), flow.stages());
        assertFalse(flow.hasConfigurationPhase());
        assertFalse(flow.canChat());
        assertFalse(flow.canMove());

        flow.handshakeReceived();
        assertEquals(Stage.LOGIN, flow.current());
        flow.loginSucceeded();
        // 763はログイン成功のあとすぐプレイ状態へ（設定フェーズを挟まない）。
        assertEquals(Stage.PLAY, flow.current());
        flow.joinGameReceived();
        assertEquals(Stage.JOINED, flow.current());
        assertFalse(flow.canChat());
        flow.spawned();
        assertEquals(Stage.IN_GAME, flow.current());
        assertTrue(flow.isInGame());
        // ゲーム内に入って初めてチャットと移動ができる。
        assertTrue(flow.canChat());
        assertTrue(flow.canMove());
    }

    /** 1.20.4クライアントの参加：設定フェーズを挟む。 */
    @Test
    void client1204JoinsThroughConfigurationPhase() {
        ClientJoinFlow flow = new ClientJoinFlow(ProtocolVersion.V1_20_4);
        assertEquals(List.of(Stage.HANDSHAKE, Stage.LOGIN, Stage.CONFIGURATION,
                Stage.PLAY, Stage.JOINED, Stage.IN_GAME), flow.stages());
        assertTrue(flow.hasConfigurationPhase());

        flow.handshakeReceived();
        flow.loginSucceeded();
        assertEquals(Stage.CONFIGURATION, flow.current());
        assertFalse(flow.canChat());
        flow.configurationFinished();
        assertEquals(Stage.PLAY, flow.current());
        flow.joinGameReceived();
        flow.spawned();
        assertTrue(flow.isInGame());
        assertTrue(flow.canChat());
        assertTrue(flow.canMove());
    }

    @Test
    void client1201HasNoConfigurationPhaseToFinish() {
        ClientJoinFlow flow = new ClientJoinFlow(ProtocolVersion.V1_20_1);
        flow.handshakeReceived();
        flow.loginSucceeded();
        // 763で設定フェーズに入ろうとすると順番違反になる。
        assertThrows(IllegalStateException.class, flow::configurationFinished);
    }

    @Test
    void client1204CannotSkipConfigurationPhase() {
        ClientJoinFlow flow = new ClientJoinFlow(ProtocolVersion.V1_20_4);
        flow.handshakeReceived();
        flow.loginSucceeded();
        // 設定フェーズが終わる前にプレイ開始はできない。
        assertThrows(IllegalStateException.class, flow::joinGameReceived);
    }

    @Test
    void outOfOrderTransitionsAreRejected() {
        ClientJoinFlow flow = new ClientJoinFlow(ProtocolVersion.V1_20_1);
        // ハンドシェイクの前にログイン成功はあり得ない。
        assertThrows(IllegalStateException.class, flow::loginSucceeded);
        assertThrows(IllegalStateException.class, flow::spawned);
    }

    @Test
    void unsupportedProtocolIsRejectedAtConstruction() {
        assertThrows(IllegalArgumentException.class,
                () -> new ClientJoinFlow(ProtocolVersion.V1_12_2));
    }
}
