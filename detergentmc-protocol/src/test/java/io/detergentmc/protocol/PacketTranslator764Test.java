package io.detergentmc.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.detergentmc.protocol.PacketIds.PacketName;
import java.util.OptionalInt;
import org.junit.jupiter.api.Test;

/** 764（1.20.2）のパケットID付け替え。 */
class PacketTranslator764Test {

    private static final int V764 = ProtocolVersion.V1_20_2;
    private static final int V765 = ProtocolVersion.V1_20_4;

    @Test
    void needsTranslationFor764() {
        assertTrue(PacketTranslator.needsTranslation(V764));
        assertTrue(PacketTranslator.isSupported(V764));
    }

    @Test
    void toServerIdsFor764() {
        // Keep Alive 0x14 → 0x15、移動 0x16 → 0x17。
        assertEquals(OptionalInt.of(0x15),
                PacketTranslator.toServerId(PacketName.PLAY_KEEP_ALIVE_TO_SERVER, V764));
        assertEquals(OptionalInt.of(0x17),
                PacketTranslator.toServerId(PacketName.PLAY_SET_PLAYER_POSITION, V764));
        assertEquals(OptionalInt.of(0x0D),
                PacketTranslator.toServerId(PacketName.PLAY_WINDOW_CLICK, V764));
        // 同IDのものはそのまま：チャット 0x05、Chunk Batch Received 0x07、
        // Configuration Acknowledged 0x0B。
        assertEquals(OptionalInt.of(0x05),
                PacketTranslator.toServerId(PacketName.PLAY_CHAT_MESSAGE, V764));
        assertEquals(OptionalInt.of(0x07),
                PacketTranslator.toServerId(PacketName.PLAY_CHUNK_BATCH_RECEIVED, V764));
        assertEquals(OptionalInt.of(0x0B),
                PacketTranslator.toServerId(PacketName.PLAY_CONFIGURATION_ACKNOWLEDGED, V764));
        // 764に無いものは空：Set Slot State は765だけ。
        assertTrue(PacketTranslator.toServerId(PacketName.PLAY_SET_SLOT_STATE, V764).isEmpty());
        assertEquals(OptionalInt.of(0x0F),
                PacketTranslator.toServerId(PacketName.PLAY_SET_SLOT_STATE, V765));
    }

    @Test
    void toClientIdsFor764() {
        // Join Game は764と765で同ID（0x29）。Respawn 0x45 → 0x43。
        assertEquals(OptionalInt.of(0x29),
                PacketTranslator.toClientId(PacketName.PLAY_JOIN_GAME, V764));
        assertEquals(OptionalInt.of(0x43),
                PacketTranslator.toClientId(PacketName.PLAY_RESPAWN, V764));
        assertEquals(OptionalInt.of(0x52),
                PacketTranslator.toClientId(PacketName.PLAY_SPAWN_POSITION, V764));
        assertEquals(OptionalInt.of(0x0D),
                PacketTranslator.toClientId(PacketName.PLAY_CHUNK_BATCH_START, V764));
        assertEquals(OptionalInt.of(0x34),
                PacketTranslator.toClientId(PacketName.PLAY_PING_RESPONSE, V764));
        // 764に無いものは空。
        assertTrue(PacketTranslator.toClientId(PacketName.PLAY_RESET_SCORE, V764).isEmpty());
        assertTrue(PacketTranslator.toClientId(PacketName.PLAY_SET_TICKING_STATE, V764).isEmpty());
        assertTrue(PacketTranslator.toClientId(PacketName.PLAY_STEP_TICK, V764).isEmpty());
        assertTrue(PacketTranslator.toClientId(PacketName.PLAY_ADD_RESOURCE_PACK, V764).isEmpty());
        assertTrue(PacketTranslator.toClientId(PacketName.PLAY_REMOVE_RESOURCE_PACK, V764).isEmpty());
        // 764の単一リソースパックは765に無い（765→764の付け替えは存在しない）。
        assertTrue(PacketTranslator.toClientId(PacketName.PLAY_RESOURCE_PACK, V765).isEmpty());
        assertEquals(OptionalInt.of(0x42), PacketIds.id(PacketName.PLAY_RESOURCE_PACK, V764));
    }

    @Test
    void translateIdBetween764And765() {
        assertEquals(OptionalInt.of(0x29),
                PacketTranslator.translateId(PacketName.PLAY_JOIN_GAME, V764, V765));
        assertEquals(OptionalInt.of(0x29),
                PacketTranslator.translateId(PacketName.PLAY_JOIN_GAME, V765, V764));
        assertEquals(OptionalInt.of(0x69),
                PacketTranslator.translateId(PacketName.PLAY_SYSTEM_CHAT, V764, V765));
        assertEquals(OptionalInt.of(0x67),
                PacketTranslator.translateId(PacketName.PLAY_SYSTEM_CHAT, V765, V764));
    }
}
