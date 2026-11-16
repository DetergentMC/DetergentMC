package io.detergentmc.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.detergentmc.protocol.PacketIds.Direction;
import io.detergentmc.protocol.PacketIds.PacketName;
import io.detergentmc.protocol.PacketIds.State;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import org.junit.jupiter.api.Test;

/**
 * 764（1.20.2）のパケットID表。
 * 値は PrismarineJS minecraft-data の data/pc/1.20.2/protocol.json
 * から1件ずつ確認した（写像の正しさは、同じ写像で既存の763／765の値が
 * すべて再現できることで裏付けている。PacketIds のクラスコメント参照）。
 */
class PacketIds764Test {

    private static final int V763 = ProtocolVersion.V1_20_1;
    private static final int V764 = ProtocolVersion.V1_20_2;
    private static final int V765 = ProtocolVersion.V1_20_4;

    private static void assertId(int expected, PacketName name, int protocol) {
        assertEquals(OptionalInt.of(expected), PacketIds.id(name, protocol),
                name + " for protocol " + protocol);
    }

    @Test
    void tableKnows764() {
        assertTrue(PacketIds.isSupported(V764));
        assertEquals(List.of(756, 757, 759, 760, 761, 762, 763, 764, 765, 766, 767, 768, 769, 770, 771, 772, 773, 774, 775), PacketIds.supportedProtocols());
    }

    @Test
    void loginIds() {
        assertId(0x00, PacketName.LOGIN_START, V764);
        assertId(0x01, PacketName.LOGIN_ENCRYPTION_RESPONSE, V764);
        assertId(0x02, PacketName.LOGIN_PLUGIN_RESPONSE, V764);
        assertId(0x03, PacketName.LOGIN_ACKNOWLEDGED, V764);
        assertId(0x00, PacketName.LOGIN_DISCONNECT, V764);
        assertId(0x01, PacketName.LOGIN_ENCRYPTION_REQUEST, V764);
        assertId(0x02, PacketName.LOGIN_SUCCESS, V764);
        assertId(0x03, PacketName.LOGIN_SET_COMPRESSION, V764);
        assertId(0x04, PacketName.LOGIN_PLUGIN_REQUEST, V764);
    }

    @Test
    void configurationIds() {
        // 764の設定フェーズは765とほぼ同じ並び。リソースパックだけが違う。
        assertId(0x00, PacketName.CONFIG_PLUGIN_MESSAGE, V764);
        assertId(0x01, PacketName.CONFIG_DISCONNECT, V764);
        assertId(0x02, PacketName.CONFIG_FINISH_CONFIGURATION, V764);
        assertId(0x03, PacketName.CONFIG_KEEP_ALIVE, V764);
        assertId(0x04, PacketName.CONFIG_PING, V764);
        assertId(0x05, PacketName.CONFIG_REGISTRY_DATA, V764);
        assertId(0x06, PacketName.CONFIG_RESOURCE_PACK_SEND, V764);
        assertId(0x07, PacketName.CONFIG_FEATURE_FLAGS, V764);
        assertId(0x08, PacketName.CONFIG_TAGS, V764);
        assertId(0x00, PacketName.CONFIG_CLIENT_INFORMATION, V764);
        assertId(0x01, PacketName.CONFIG_PLUGIN_MESSAGE_TO_SERVER, V764);
        assertId(0x02, PacketName.CONFIG_ACKNOWLEDGE_FINISH_CONFIGURATION, V764);
        assertId(0x03, PacketName.CONFIG_KEEP_ALIVE_TO_SERVER, V764);
        assertId(0x04, PacketName.CONFIG_PONG, V764);
        assertId(0x05, PacketName.CONFIG_RESOURCE_PACK_RESPONSE, V764);
        // 765の Add／Remove Resource Pack は764に無い。
        assertTrue(PacketIds.id(PacketName.CONFIG_ADD_RESOURCE_PACK, V764).isEmpty());
        assertTrue(PacketIds.id(PacketName.CONFIG_REMOVE_RESOURCE_PACK, V764).isEmpty());
        // 逆に764の単一パケットは765に無い。
        assertTrue(PacketIds.id(PacketName.CONFIG_RESOURCE_PACK_SEND, V765).isEmpty());
        assertTrue(PacketIds.id(PacketName.CONFIG_RESOURCE_PACK_SEND, V763).isEmpty());
    }

    @Test
    void playClientboundIds() {
        assertId(0x29, PacketName.PLAY_JOIN_GAME, V764);
        assertId(0x1B, PacketName.PLAY_DISCONNECT, V764);
        assertId(0x52, PacketName.PLAY_SPAWN_POSITION, V764);
        assertId(0x20, PacketName.PLAY_GAME_EVENT, V764);
        assertId(0x24, PacketName.PLAY_KEEP_ALIVE, V764);
        assertId(0x67, PacketName.PLAY_SYSTEM_CHAT, V764);
        assertId(0x37, PacketName.PLAY_PLAYER_CHAT, V764);
        assertId(0x25, PacketName.PLAY_CHUNK_DATA, V764);
        assertId(0x09, PacketName.PLAY_BLOCK_CHANGE, V764);
        assertId(0x3C, PacketName.PLAY_PLAYER_INFO_UPDATE, V764);
        assertId(0x3B, PacketName.PLAY_PLAYER_INFO_REMOVE, V764);
        assertId(0x42, PacketName.PLAY_RESOURCE_PACK, V764);
        assertId(0x18, PacketName.PLAY_PLUGIN_MESSAGE, V764);
        assertId(0x3E, PacketName.PLAY_SYNCHRONIZE_PLAYER_POSITION, V764);
        assertId(0x43, PacketName.PLAY_RESPAWN, V764);
        assertId(0x65, PacketName.PLAY_START_CONFIGURATION, V764);
        assertId(0x6C, PacketName.PLAY_ADVANCEMENTS, V764);
        assertId(0x0A, PacketName.PLAY_BOSS_BAR, V764);
        assertId(0x5C, PacketName.PLAY_TEAMS, V764);
        assertId(0x10, PacketName.PLAY_TAB_COMPLETE, V764);
        assertId(0x1E, PacketName.PLAY_EXPLOSION, V764);
        assertId(0x0C, PacketName.PLAY_CHUNK_BATCH_FINISHED, V764);
        assertId(0x0D, PacketName.PLAY_CHUNK_BATCH_START, V764);
        assertId(0x34, PacketName.PLAY_PING_RESPONSE, V764);
        assertId(0x6E, PacketName.PLAY_ENTITY_EFFECT, V764);
        assertId(0x54, PacketName.PLAY_ENTITY_METADATA, V764);
        assertId(0x5D, PacketName.PLAY_SCOREBOARD_SCORE, V764);
        assertId(0x5A, PacketName.PLAY_SCOREBOARD_OBJECTIVE, V764);
        assertId(0x70, PacketName.PLAY_TAGS, V764);
        assertId(0x6F, PacketName.PLAY_DECLARE_RECIPES, V764);
    }

    @Test
    void playClientboundIdsMissingIn764() {
        // 765だけにあるものは764では空。
        assertTrue(PacketIds.id(PacketName.PLAY_RESET_SCORE, V764).isEmpty());
        assertTrue(PacketIds.id(PacketName.PLAY_SET_TICKING_STATE, V764).isEmpty());
        assertTrue(PacketIds.id(PacketName.PLAY_STEP_TICK, V764).isEmpty());
        assertTrue(PacketIds.id(PacketName.PLAY_ADD_RESOURCE_PACK, V764).isEmpty());
        assertTrue(PacketIds.id(PacketName.PLAY_REMOVE_RESOURCE_PACK, V764).isEmpty());
    }

    @Test
    void playServerboundIds() {
        assertId(0x05, PacketName.PLAY_CHAT_MESSAGE, V764);
        assertId(0x04, PacketName.PLAY_CHAT_COMMAND, V764);
        assertId(0x14, PacketName.PLAY_KEEP_ALIVE_TO_SERVER, V764);
        assertId(0x16, PacketName.PLAY_SET_PLAYER_POSITION, V764);
        assertId(0x17, PacketName.PLAY_SET_PLAYER_POSITION_AND_ROTATION, V764);
        assertId(0x18, PacketName.PLAY_SET_PLAYER_ROTATION, V764);
        assertId(0x27, PacketName.PLAY_RESOURCE_PACK_RESPONSE, V764);
        assertId(0x0F, PacketName.PLAY_PLUGIN_MESSAGE_TO_SERVER, V764);
        assertId(0x09, PacketName.PLAY_CLIENT_INFORMATION, V764);
        assertId(0x07, PacketName.PLAY_CHUNK_BATCH_RECEIVED, V764);
        assertId(0x0B, PacketName.PLAY_CONFIGURATION_ACKNOWLEDGED, V764);
        assertId(0x0D, PacketName.PLAY_WINDOW_CLICK, V764);
        assertId(0x2E, PacketName.PLAY_SET_CREATIVE_SLOT, V764);
        assertId(0x23, PacketName.PLAY_PONG, V764);
        assertId(0x35, PacketName.PLAY_USE_ITEM, V764);
        assertId(0x34, PacketName.PLAY_BLOCK_PLACE, V764);
        // 765だけにあるサーバー行きパケットは764では空。
        assertTrue(PacketIds.id(PacketName.PLAY_SET_SLOT_STATE, V764).isEmpty());
        assertId(0x0F, PacketName.PLAY_SET_SLOT_STATE, V765);
    }

    @Test
    void reverseLookupFor764() {
        assertEquals(Optional.of(PacketName.PLAY_JOIN_GAME),
                PacketIds.nameFor(0x29, State.PLAY, Direction.CLIENTBOUND, V764));
        assertEquals(Optional.of(PacketName.CONFIG_RESOURCE_PACK_SEND),
                PacketIds.nameFor(0x06, State.CONFIGURATION, Direction.CLIENTBOUND, V764));
        assertEquals(Optional.of(PacketName.PLAY_RESOURCE_PACK),
                PacketIds.nameFor(0x42, State.PLAY, Direction.CLIENTBOUND, V764));
        assertEquals(Optional.of(PacketName.PLAY_CHUNK_BATCH_RECEIVED),
                PacketIds.nameFor(0x07, State.PLAY, Direction.SERVERBOUND, V764));
        assertEquals(Optional.of(PacketName.PLAY_CONFIGURATION_ACKNOWLEDGED),
                PacketIds.nameFor(0x0B, State.PLAY, Direction.SERVERBOUND, V764));
        // 表に無いパケットは空：Ping Request（764は 0x1D）。
        assertTrue(PacketIds.nameFor(0x1D, State.PLAY, Direction.SERVERBOUND, V764).isEmpty());
        // 764に無いIDは空：Reset Score の765のID（0x42）は764では Resource Pack。
        assertEquals(Optional.of(PacketName.PLAY_RESOURCE_PACK),
                PacketIds.nameFor(0x42, State.PLAY, Direction.CLIENTBOUND, V764));
        assertTrue(PacketIds.nameFor(0x42, State.PLAY, Direction.CLIENTBOUND, V765)
                .map(name -> name == PacketName.PLAY_RESET_SCORE).orElse(false));
    }

    @Test
    void namesListFor764ConfigurationIsNotEmpty() {
        // 763の設定フェーズは空、764は9個のクライアント行きパケットがある。
        assertTrue(PacketIds.names(State.CONFIGURATION, Direction.CLIENTBOUND, V763).isEmpty());
        assertEquals(9, PacketIds.names(State.CONFIGURATION, Direction.CLIENTBOUND, V764).size());
        assertEquals(10, PacketIds.names(State.CONFIGURATION, Direction.CLIENTBOUND, V765).size());
    }

    @Test
    void existing763And765IdsAreUnchanged() {
        // 既存の表を壊していないことの抜粋確認。
        assertId(0x28, PacketName.PLAY_JOIN_GAME, V763);
        assertId(0x29, PacketName.PLAY_JOIN_GAME, V765);
        assertId(0x41, PacketName.PLAY_RESPAWN, V763);
        assertId(0x45, PacketName.PLAY_RESPAWN, V765);
        assertId(0x40, PacketName.PLAY_RESOURCE_PACK, V763);
        assertId(0x44, PacketName.PLAY_ADD_RESOURCE_PACK, V765);
        assertId(0x05, PacketName.CONFIG_REGISTRY_DATA, V765);
        assertTrue(PacketIds.id(PacketName.CONFIG_REGISTRY_DATA, V763).isEmpty());
        assertTrue(PacketIds.id(PacketName.LOGIN_ACKNOWLEDGED, V763).isEmpty());
        assertId(0x03, PacketName.LOGIN_ACKNOWLEDGED, V765);
    }
}
