package io.detergentmc.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.detergentmc.protocol.PacketIds.Direction;
import io.detergentmc.protocol.PacketIds.PacketName;
import io.detergentmc.protocol.PacketIds.State;
import java.util.OptionalInt;
import org.junit.jupiter.api.Test;

/** 763⇄765のパケットID変換。 */
class PacketTranslatorTest {

    private static final int V763 = ProtocolVersion.V1_20_1;
    private static final int V765 = ProtocolVersion.V1_20_4;

    @Test
    void translatesJoinGameBothWays() {
        assertEquals(OptionalInt.of(0x29),
                PacketTranslator.translateId(PacketName.PLAY_JOIN_GAME, V763, V765));
        assertEquals(OptionalInt.of(0x28),
                PacketTranslator.translateId(PacketName.PLAY_JOIN_GAME, V765, V763));
    }

    @Test
    void translatesKeepAliveBothWays() {
        assertEquals(OptionalInt.of(0x24),
                PacketTranslator.translateId(PacketName.PLAY_KEEP_ALIVE, V763, V765));
        assertEquals(OptionalInt.of(0x15),
                PacketTranslator.translateId(PacketName.PLAY_KEEP_ALIVE_TO_SERVER, V763, V765));
        assertEquals(OptionalInt.of(0x12),
                PacketTranslator.translateId(PacketName.PLAY_KEEP_ALIVE_TO_SERVER, V765, V763));
    }

    @Test
    void translatesChatMessageByName() {
        // Chat Message は両バージョンとも 0x05 なので、付け替え結果も 0x05。
        assertEquals(OptionalInt.of(0x05),
                PacketTranslator.translateId(PacketName.PLAY_CHAT_MESSAGE, V763, V765));
        assertEquals(OptionalInt.of(0x05),
                PacketTranslator.translateId(PacketName.PLAY_CHAT_MESSAGE, V765, V763));
        assertEquals(OptionalInt.of(0x37),
                PacketTranslator.translateId(PacketName.PLAY_PLAYER_CHAT, V763, V765));
    }

    @Test
    void translatesByWireId() {
        assertEquals(OptionalInt.of(0x29),
                PacketTranslator.translateId(0x28,
                        State.PLAY, Direction.CLIENTBOUND, V763, V765));
        assertEquals(OptionalInt.of(0x28),
                PacketTranslator.translateId(0x29,
                        State.PLAY, Direction.CLIENTBOUND, V765, V763));
        // その状態・方向に無いIDは空。
        assertTrue(PacketTranslator.translateId(0x7F,
                State.PLAY, Direction.CLIENTBOUND, V763, V765).isEmpty());
    }

    @Test
    void sameProtocolIsIdentity() {
        assertEquals(OptionalInt.of(0x28),
                PacketTranslator.translateId(PacketName.PLAY_JOIN_GAME, V763, V763));
        assertEquals(OptionalInt.of(0x29),
                PacketTranslator.translateId(PacketName.PLAY_JOIN_GAME, V765, V765));
    }

    @Test
    void noCounterpartIsEmpty() {
        // 設定フェーズのパケットは763に無い。
        assertTrue(PacketTranslator.translateId(
                PacketName.CONFIG_REGISTRY_DATA, V765, V763).isEmpty());
        assertTrue(PacketTranslator.translateId(
                PacketName.CONFIG_REGISTRY_DATA, V763, V765).isEmpty());
        // Login Acknowledged は765にしか無い。
        assertTrue(PacketTranslator.translateId(
                PacketName.LOGIN_ACKNOWLEDGED, V763, V765).isEmpty());
        assertTrue(PacketTranslator.translateId(
                PacketName.LOGIN_ACKNOWLEDGED, V765, V763).isEmpty());
        // 763の旧式リソースパックは765に無い。
        assertTrue(PacketTranslator.translateId(
                PacketName.PLAY_RESOURCE_PACK, V763, V765).isEmpty());
        // 未対応プロトコルは空（766・767・768・769・770・771・772は対応済みのため Join Game の
        // 付け替えができる）。
        assertEquals(OptionalInt.of(0x29), PacketTranslator.translateId(
                PacketName.PLAY_JOIN_GAME, 766, V765));
        assertEquals(OptionalInt.of(0x29), PacketTranslator.translateId(
                PacketName.PLAY_JOIN_GAME, 767, V765));
        assertEquals(OptionalInt.of(0x29), PacketTranslator.translateId(
                PacketName.PLAY_JOIN_GAME, 768, V765));
        assertEquals(OptionalInt.of(0x29), PacketTranslator.translateId(
                PacketName.PLAY_JOIN_GAME, 769, V765));
        assertEquals(OptionalInt.of(0x29), PacketTranslator.translateId(
                PacketName.PLAY_JOIN_GAME, 770, V765));
        assertEquals(OptionalInt.of(0x29), PacketTranslator.translateId(
                PacketName.PLAY_JOIN_GAME, 771, V765));
        assertEquals(OptionalInt.of(0x29), PacketTranslator.translateId(
                PacketName.PLAY_JOIN_GAME, 772, V765));
    }

    @Test
    void toServerAndToClientHelpers() {
        // 763クライアントからの Keep Alive（サーバー行き）を765のサーバーへ。
        assertEquals(OptionalInt.of(0x15),
                PacketTranslator.toServerId(PacketName.PLAY_KEEP_ALIVE_TO_SERVER, V763));
        // 765クライアントなら付け替えなし。
        assertEquals(OptionalInt.of(0x15),
                PacketTranslator.toServerId(PacketName.PLAY_KEEP_ALIVE_TO_SERVER, V765));
        // サーバーからの Join Game を763クライアントへ。
        assertEquals(OptionalInt.of(0x28),
                PacketTranslator.toClientId(PacketName.PLAY_JOIN_GAME, V763));
        // 765クライアントなら付け替えなし。
        assertEquals(OptionalInt.of(0x29),
                PacketTranslator.toClientId(PacketName.PLAY_JOIN_GAME, V765));
        // 763に無いパケットを763クライアントへ送るIDは無い。
        assertTrue(PacketTranslator.toClientId(PacketName.CONFIG_REGISTRY_DATA, V763).isEmpty());
    }

    @Test
    void needsTranslationFor763And764And766() {
        assertTrue(PacketTranslator.needsTranslation(V763));
        assertTrue(PacketTranslator.needsTranslation(ProtocolVersion.V1_20_2));
        assertFalse(PacketTranslator.needsTranslation(V765));
        assertTrue(PacketTranslator.needsTranslation(766));
        // 未対応のプロトコルは「変換」ではなく「拒否」が正しい扱い。
        assertFalse(PacketTranslator.needsTranslation(340));
    }

    @Test
    void existsInMatchesTable() {
        assertTrue(PacketTranslator.existsIn(PacketName.PLAY_JOIN_GAME, V763));
        assertTrue(PacketTranslator.existsIn(PacketName.PLAY_JOIN_GAME, V765));
        assertTrue(PacketTranslator.existsIn(PacketName.PLAY_JOIN_GAME, 766));
        assertFalse(PacketTranslator.existsIn(PacketName.CONFIG_REGISTRY_DATA, V763));
        assertTrue(PacketTranslator.existsIn(PacketName.CONFIG_REGISTRY_DATA, V765));
        assertFalse(PacketTranslator.existsIn(PacketName.LOGIN_ACKNOWLEDGED, V763));
        assertTrue(PacketTranslator.existsIn(PacketName.LOGIN_ACKNOWLEDGED, V765));
    }
}
