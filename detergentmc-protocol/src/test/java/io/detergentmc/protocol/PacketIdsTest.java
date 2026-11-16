package io.detergentmc.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.detergentmc.protocol.PacketIds.Direction;
import io.detergentmc.protocol.PacketIds.PacketName;
import io.detergentmc.protocol.PacketIds.State;
import java.util.HashSet;
import java.util.List;
import java.util.OptionalInt;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * パケットID表の健全性。IDは wiki.vg / minecraft-data の protocol.json
 * （1.20 用と 1.20.3 用）で確認した値。
 */
class PacketIdsTest {

    private static final int V763 = ProtocolVersion.V1_20_1;
    private static final int V764 = ProtocolVersion.V1_20_2;
    private static final int V765 = ProtocolVersion.V1_20_4;
    private static final int V766 = ProtocolVersion.V1_20_5;

    private static void assertId(int expected, PacketName name, int protocol) {
        assertEquals(OptionalInt.of(expected), PacketIds.id(name, protocol),
                name + " for protocol " + protocol);
    }

    private static void assertId(int expected, PacketName name, State state,
            Direction direction, int protocol) {
        assertEquals(OptionalInt.of(expected),
                PacketIds.id(name, state, direction, protocol),
                name + " for protocol " + protocol);
    }

    @Test
    void handshakeIsSameInBothVersions() {
        assertId(0x00, PacketName.HANDSHAKE, V763);
        assertId(0x00, PacketName.HANDSHAKE, V765);
        assertId(0x00, PacketName.HANDSHAKE,
                State.HANDSHAKING, Direction.SERVERBOUND, V763);
    }

    @Test
    void loginPacketsAreSameInBothVersions() {
        assertId(0x00, PacketName.LOGIN_DISCONNECT, V763);
        assertId(0x00, PacketName.LOGIN_DISCONNECT, V765);
        assertId(0x01, PacketName.LOGIN_ENCRYPTION_REQUEST, V763);
        assertId(0x01, PacketName.LOGIN_ENCRYPTION_REQUEST, V765);
        assertId(0x02, PacketName.LOGIN_SUCCESS, V763);
        assertId(0x02, PacketName.LOGIN_SUCCESS, V765);
        assertId(0x03, PacketName.LOGIN_SET_COMPRESSION, V763);
        assertId(0x03, PacketName.LOGIN_SET_COMPRESSION, V765);
        assertId(0x04, PacketName.LOGIN_PLUGIN_REQUEST, V763);
        assertId(0x04, PacketName.LOGIN_PLUGIN_REQUEST, V765);
        assertId(0x00, PacketName.LOGIN_START, V763);
        assertId(0x01, PacketName.LOGIN_ENCRYPTION_RESPONSE, V763);
        assertId(0x02, PacketName.LOGIN_PLUGIN_RESPONSE, V763);
    }

    @Test
    void loginAcknowledgedIs765Only() {
        assertTrue(PacketIds.id(PacketName.LOGIN_ACKNOWLEDGED, V763).isEmpty());
        assertId(0x03, PacketName.LOGIN_ACKNOWLEDGED, V765);
    }

    @Test
    void configurationDoesNotExistIn763() {
        for (PacketName name : PacketName.values()) {
            if (name.state() == State.CONFIGURATION) {
                assertTrue(PacketIds.id(name, V763).isEmpty(),
                        name + " は763に存在しないこと");
                // 764だけのパケット（CONFIG_RESOURCE_PACK_SEND）や766だけの
                // パケット（CONFIG_COOKIE_REQUEST など）や767だけの
                // パケット（CONFIG_SERVER_LINKS など）や771だけの
                // パケット（CONFIG_CLEAR_DIALOG など）や773だけの
                // パケット（CONFIG_CODE_OF_CONDUCT など）があるため、
                // 765か764か766か767か771か773のどれかに存在すればよい。
                assertTrue(PacketIds.id(name, V765).isPresent()
                                || PacketIds.id(name, V764).isPresent()
                                || PacketIds.id(name, 766).isPresent()
                                || PacketIds.id(name, 767).isPresent()
                                || PacketIds.id(name, 771).isPresent()
                                || PacketIds.id(name, 773).isPresent(),
                        name + " は765か764か766か767か771か773に存在すること");
            }
        }
        assertTrue(PacketIds.names(State.CONFIGURATION, Direction.CLIENTBOUND, V763).isEmpty());
        assertTrue(PacketIds.names(State.CONFIGURATION, Direction.SERVERBOUND, V763).isEmpty());
        assertFalse(PacketIds.names(State.CONFIGURATION, Direction.CLIENTBOUND, V765).isEmpty());
        assertFalse(PacketIds.names(State.CONFIGURATION, Direction.CLIENTBOUND, V764).isEmpty());
    }

    @Test
    void configurationIdsFor765() {
        assertId(0x00, PacketName.CONFIG_PLUGIN_MESSAGE, V765);
        assertId(0x01, PacketName.CONFIG_DISCONNECT, V765);
        assertId(0x02, PacketName.CONFIG_FINISH_CONFIGURATION, V765);
        assertId(0x03, PacketName.CONFIG_KEEP_ALIVE, V765);
        assertId(0x05, PacketName.CONFIG_REGISTRY_DATA, V765);
        assertId(0x07, PacketName.CONFIG_ADD_RESOURCE_PACK, V765);
        assertId(0x00, PacketName.CONFIG_CLIENT_INFORMATION, V765);
        assertId(0x02, PacketName.CONFIG_ACKNOWLEDGE_FINISH_CONFIGURATION, V765);
        assertId(0x05, PacketName.CONFIG_RESOURCE_PACK_RESPONSE, V765);
    }

    @Test
    void joinGameDiffersBetweenVersions() {
        assertId(0x28, PacketName.PLAY_JOIN_GAME, V763);
        assertId(0x29, PacketName.PLAY_JOIN_GAME, V765);
    }

    @Test
    void keepAliveDiffersBetweenVersions() {
        assertId(0x23, PacketName.PLAY_KEEP_ALIVE, V763);
        assertId(0x24, PacketName.PLAY_KEEP_ALIVE, V765);
        assertId(0x12, PacketName.PLAY_KEEP_ALIVE_TO_SERVER, V763);
        assertId(0x15, PacketName.PLAY_KEEP_ALIVE_TO_SERVER, V765);
    }

    @Test
    void chatIdsDifferBetweenVersions() {
        // サーバー行きの Chat Message は両方 0x05（IDは同じだが中身が違う）。
        assertId(0x05, PacketName.PLAY_CHAT_MESSAGE, V763);
        assertId(0x05, PacketName.PLAY_CHAT_MESSAGE, V765);
        assertId(0x35, PacketName.PLAY_PLAYER_CHAT, V763);
        assertId(0x37, PacketName.PLAY_PLAYER_CHAT, V765);
        assertId(0x64, PacketName.PLAY_SYSTEM_CHAT, V763);
        assertId(0x69, PacketName.PLAY_SYSTEM_CHAT, V765);
    }

    @Test
    void playIdsDifferBetweenVersions() {
        assertId(0x1A, PacketName.PLAY_DISCONNECT, V763);
        assertId(0x1B, PacketName.PLAY_DISCONNECT, V765);
        assertId(0x50, PacketName.PLAY_SPAWN_POSITION, V763);
        assertId(0x54, PacketName.PLAY_SPAWN_POSITION, V765);
        assertId(0x1F, PacketName.PLAY_GAME_EVENT, V763);
        assertId(0x20, PacketName.PLAY_GAME_EVENT, V765);
        assertId(0x24, PacketName.PLAY_CHUNK_DATA, V763);
        assertId(0x25, PacketName.PLAY_CHUNK_DATA, V765);
        assertId(0x0A, PacketName.PLAY_BLOCK_CHANGE, V763);
        assertId(0x09, PacketName.PLAY_BLOCK_CHANGE, V765);
        assertId(0x3A, PacketName.PLAY_PLAYER_INFO_UPDATE, V763);
        assertId(0x3C, PacketName.PLAY_PLAYER_INFO_UPDATE, V765);
        assertId(0x39, PacketName.PLAY_PLAYER_INFO_REMOVE, V763);
        assertId(0x3B, PacketName.PLAY_PLAYER_INFO_REMOVE, V765);
        assertId(0x14, PacketName.PLAY_SET_PLAYER_POSITION, V763);
        assertId(0x17, PacketName.PLAY_SET_PLAYER_POSITION, V765);
        assertId(0x15, PacketName.PLAY_SET_PLAYER_POSITION_AND_ROTATION, V763);
        assertId(0x18, PacketName.PLAY_SET_PLAYER_POSITION_AND_ROTATION, V765);
        assertId(0x17, PacketName.PLAY_PLUGIN_MESSAGE, V763);
        assertId(0x18, PacketName.PLAY_PLUGIN_MESSAGE, V765);
    }

    @Test
    void resourcePackDiffersBetweenVersions() {
        // 763はプレイ状態の旧式オファー（UUIDなし）。765には無い。
        assertId(0x40, PacketName.PLAY_RESOURCE_PACK, V763);
        assertTrue(PacketIds.id(PacketName.PLAY_RESOURCE_PACK, V765).isEmpty());
        // 765はUUID付きの追加・削除。763には無い。
        assertTrue(PacketIds.id(PacketName.PLAY_ADD_RESOURCE_PACK, V763).isEmpty());
        assertId(0x44, PacketName.PLAY_ADD_RESOURCE_PACK, V765);
        assertId(0x43, PacketName.PLAY_REMOVE_RESOURCE_PACK, V765);
        assertId(0x24, PacketName.PLAY_RESOURCE_PACK_RESPONSE, V763);
        assertId(0x28, PacketName.PLAY_RESOURCE_PACK_RESPONSE, V765);
    }

    @Test
    void reverseLookupFindsNames() {
        assertEquals(PacketName.PLAY_JOIN_GAME,
                PacketIds.nameFor(0x28, State.PLAY, Direction.CLIENTBOUND, V763).orElseThrow());
        assertEquals(PacketName.PLAY_JOIN_GAME,
                PacketIds.nameFor(0x29, State.PLAY, Direction.CLIENTBOUND, V765).orElseThrow());
        assertEquals(PacketName.LOGIN_SUCCESS,
                PacketIds.nameFor(0x02, State.LOGIN, Direction.CLIENTBOUND, V763).orElseThrow());
        // 763の 0x28 はプレイでは Join Game だが、ログインでは別パケット。
        assertTrue(PacketIds.nameFor(0x28, State.LOGIN, Direction.CLIENTBOUND, V763).isEmpty());
        // 763の設定フェーズに 0x02 は存在しない。
        assertTrue(PacketIds.nameFor(0x02, State.CONFIGURATION, Direction.CLIENTBOUND, V763)
                .isEmpty());
    }

    @Test
    void mismatchedStateOrDirectionIsEmpty() {
        assertTrue(PacketIds.id(PacketName.PLAY_JOIN_GAME,
                State.LOGIN, Direction.CLIENTBOUND, V765).isEmpty());
        assertTrue(PacketIds.id(PacketName.PLAY_JOIN_GAME,
                State.PLAY, Direction.SERVERBOUND, V765).isEmpty());
        assertTrue(PacketIds.id(PacketName.HANDSHAKE,
                State.HANDSHAKING, Direction.CLIENTBOUND, V765).isEmpty());
    }

    @Test
    void unknownProtocolIsEmpty() {
        for (int protocol : new int[]{776, 340}) {
            assertTrue(PacketIds.id(PacketName.PLAY_JOIN_GAME, protocol).isEmpty(),
                    "protocol " + protocol + " は未対応");
            assertFalse(PacketIds.isSupported(protocol));
        }
        assertTrue(PacketIds.isSupported(V763));
        assertTrue(PacketIds.isSupported(V764));
        assertTrue(PacketIds.isSupported(V765));
        assertTrue(PacketIds.isSupported(766));
        assertTrue(PacketIds.isSupported(767));
        assertTrue(PacketIds.isSupported(769));
        assertTrue(PacketIds.isSupported(770));
        assertTrue(PacketIds.isSupported(771));
        assertTrue(PacketIds.isSupported(772));
        assertTrue(PacketIds.isSupported(773));
        assertTrue(PacketIds.isSupported(774));
        assertTrue(PacketIds.isSupported(775));
        assertEquals(List.of(756, 757, 759, 760, 761, 762, 763, 764, 765, 766, 767, 768, 769, 770, 771, 772, 773, 774, 775), PacketIds.supportedProtocols());
    }

    @Test
    void addedPlayClientboundIdsForStage2() {
        // 第2段階で追加した、参加直後〜基本プレイで使うパケット。
        assertId(0x01, PacketName.PLAY_SPAWN_ENTITY, V763);
        assertId(0x01, PacketName.PLAY_SPAWN_ENTITY, V765);
        assertId(0x0C, PacketName.PLAY_DIFFICULTY, V763);
        assertId(0x0B, PacketName.PLAY_DIFFICULTY, V765);
        assertId(0x10, PacketName.PLAY_DECLARE_COMMANDS, V763);
        assertId(0x11, PacketName.PLAY_DECLARE_COMMANDS, V765);
        assertId(0x1E, PacketName.PLAY_UNLOAD_CHUNK, V763);
        assertId(0x1F, PacketName.PLAY_UNLOAD_CHUNK, V765);
        assertId(0x27, PacketName.PLAY_UPDATE_LIGHT, V763);
        assertId(0x28, PacketName.PLAY_UPDATE_LIGHT, V765);
        assertId(0x4E, PacketName.PLAY_UPDATE_VIEW_POSITION, V763);
        assertId(0x52, PacketName.PLAY_UPDATE_VIEW_POSITION, V765);
        assertId(0x4F, PacketName.PLAY_UPDATE_VIEW_DISTANCE, V763);
        assertId(0x53, PacketName.PLAY_UPDATE_VIEW_DISTANCE, V765);
        assertId(0x5C, PacketName.PLAY_SIMULATION_DISTANCE, V763);
        assertId(0x60, PacketName.PLAY_SIMULATION_DISTANCE, V765);
        assertId(0x45, PacketName.PLAY_SERVER_DATA, V763);
        assertId(0x49, PacketName.PLAY_SERVER_DATA, V765);
        assertId(0x46, PacketName.PLAY_ACTION_BAR, V763);
        assertId(0x4A, PacketName.PLAY_ACTION_BAR, V765);
        assertId(0x65, PacketName.PLAY_PLAYERLIST_HEADER, V763);
        assertId(0x6A, PacketName.PLAY_PLAYERLIST_HEADER, V765);
        assertId(0x5F, PacketName.PLAY_SET_TITLE_TEXT, V763);
        assertId(0x63, PacketName.PLAY_SET_TITLE_TEXT, V765);
        assertId(0x56, PacketName.PLAY_EXPERIENCE, V763);
        assertId(0x5A, PacketName.PLAY_EXPERIENCE, V765);
        assertId(0x57, PacketName.PLAY_UPDATE_HEALTH, V763);
        assertId(0x5B, PacketName.PLAY_UPDATE_HEALTH, V765);
        assertId(0x68, PacketName.PLAY_ENTITY_TELEPORT, V763);
        assertId(0x6D, PacketName.PLAY_ENTITY_TELEPORT, V765);
        assertId(0x6C, PacketName.PLAY_ENTITY_EFFECT, V763);
        assertId(0x72, PacketName.PLAY_ENTITY_EFFECT, V765);
    }

    @Test
    void addedPlayServerboundIdsForStage2() {
        assertId(0x00, PacketName.PLAY_TELEPORT_CONFIRM, V763);
        assertId(0x00, PacketName.PLAY_TELEPORT_CONFIRM, V765);
        assertId(0x03, PacketName.PLAY_MESSAGE_ACKNOWLEDGEMENT, V763);
        assertId(0x03, PacketName.PLAY_MESSAGE_ACKNOWLEDGEMENT, V765);
        assertId(0x07, PacketName.PLAY_CLIENT_COMMAND, V763);
        assertId(0x08, PacketName.PLAY_CLIENT_COMMAND, V765);
        assertId(0x17, PacketName.PLAY_FLYING, V763);
        assertId(0x1A, PacketName.PLAY_FLYING, V765);
        assertId(0x20, PacketName.PLAY_PONG, V763);
        assertId(0x24, PacketName.PLAY_PONG, V765);
        assertId(0x28, PacketName.PLAY_HELD_ITEM_SLOT_TO_SERVER, V763);
        assertId(0x2C, PacketName.PLAY_HELD_ITEM_SLOT_TO_SERVER, V765);
        assertId(0x2F, PacketName.PLAY_ARM_ANIMATION, V763);
        assertId(0x33, PacketName.PLAY_ARM_ANIMATION, V765);
        assertId(0x32, PacketName.PLAY_USE_ITEM, V763);
        assertId(0x36, PacketName.PLAY_USE_ITEM, V765);
    }

    @Test
    void addedPlayClientboundIdsForStage2Batch2() {
        // 実機バニラとの統合テストで見つかった不足分。
        assertId(0x00, PacketName.PLAY_BUNDLE_DELIMITER, V763);
        assertId(0x00, PacketName.PLAY_BUNDLE_DELIMITER, V765);
        assertId(0x02, PacketName.PLAY_SPAWN_EXPERIENCE_ORB, V763);
        assertId(0x02, PacketName.PLAY_SPAWN_EXPERIENCE_ORB, V765);
        assertId(0x04, PacketName.PLAY_ANIMATION, V763);
        assertId(0x03, PacketName.PLAY_ANIMATION, V765);
        assertId(0x12, PacketName.PLAY_WINDOW_ITEMS, V763);
        assertId(0x13, PacketName.PLAY_WINDOW_ITEMS, V765);
        assertId(0x1C, PacketName.PLAY_ENTITY_STATUS, V763);
        assertId(0x1D, PacketName.PLAY_ENTITY_STATUS, V765);
        assertId(0x22, PacketName.PLAY_INITIALIZE_WORLD_BORDER, V763);
        assertId(0x23, PacketName.PLAY_INITIALIZE_WORLD_BORDER, V765);
        assertId(0x3D, PacketName.PLAY_UNLOCK_RECIPES, V763);
        assertId(0x3F, PacketName.PLAY_UNLOCK_RECIPES, V765);
        assertId(0x52, PacketName.PLAY_ENTITY_METADATA, V763);
        assertId(0x56, PacketName.PLAY_ENTITY_METADATA, V765);
        assertId(0x6D, PacketName.PLAY_DECLARE_RECIPES, V763);
        assertId(0x73, PacketName.PLAY_DECLARE_RECIPES, V765);
        assertId(0x6E, PacketName.PLAY_TAGS, V763);
        assertId(0x74, PacketName.PLAY_TAGS, V765);
        assertId(0x1B, PacketName.PLAY_PROFILELESS_CHAT, V763);
        assertId(0x1C, PacketName.PLAY_PROFILELESS_CHAT, V765);
        assertId(0x38, PacketName.PLAY_DEATH_COMBAT_EVENT, V763);
        assertId(0x3A, PacketName.PLAY_DEATH_COMBAT_EVENT, V765);
        assertId(0x58, PacketName.PLAY_SCOREBOARD_OBJECTIVE, V763);
        assertId(0x5C, PacketName.PLAY_SCOREBOARD_OBJECTIVE, V765);
        assertId(0x5B, PacketName.PLAY_SCOREBOARD_SCORE, V763);
        assertId(0x5F, PacketName.PLAY_SCOREBOARD_SCORE, V765);
    }

    @Test
    void droppedPacketsAreMarkedAbsentFor763() {
        // 763に無い／変換しないものは -1 で載せてあり、toClientId は空になる。
        // Boss Bar、Tab Complete、Explosion、Respawn、Teams、Advancements は
        // 担当3で変換を実装したため、ここには含めない。
        for (PacketName name : List.of(
                PacketName.PLAY_CHUNK_BATCH_FINISHED,
                PacketName.PLAY_CHUNK_BATCH_START,
                PacketName.PLAY_PING_RESPONSE,
                PacketName.PLAY_RESET_SCORE,
                PacketName.PLAY_START_CONFIGURATION,
                PacketName.PLAY_SET_TICKING_STATE,
                PacketName.PLAY_STEP_TICK)) {
            assertTrue(PacketTranslator.toClientId(name, V763).isEmpty(),
                    name + " は763向けに落とすこと");
            assertTrue(PacketIds.id(name, V765).isPresent(),
                    name + " は765に存在すること");
        }
    }

    @Test
    void translatedPacketsArePresentFor763() {
        // 担当3で変換を実装したものは、763のIDを持つこと。
        assertId(0x0B, PacketName.PLAY_BOSS_BAR, V763);
        assertId(0x0A, PacketName.PLAY_BOSS_BAR, V765);
        assertId(0x0F, PacketName.PLAY_TAB_COMPLETE, V763);
        assertId(0x10, PacketName.PLAY_TAB_COMPLETE, V765);
        assertId(0x1D, PacketName.PLAY_EXPLOSION, V763);
        assertId(0x1E, PacketName.PLAY_EXPLOSION, V765);
        assertId(0x41, PacketName.PLAY_RESPAWN, V763);
        assertId(0x45, PacketName.PLAY_RESPAWN, V765);
        assertId(0x5A, PacketName.PLAY_TEAMS, V763);
        assertId(0x5E, PacketName.PLAY_TEAMS, V765);
        assertId(0x69, PacketName.PLAY_ADVANCEMENTS, V763);
        assertId(0x70, PacketName.PLAY_ADVANCEMENTS, V765);
    }

    @Test
    void updateSignServerbound() {
        assertId(0x2E, PacketName.PLAY_UPDATE_SIGN, V763);
        assertId(0x32, PacketName.PLAY_UPDATE_SIGN, V765);
    }

    @Test
    void noPlayRegistryDataIn763() {
        // 763のプレイ状態に Registry Data は存在しない。レジストリは
        // Join Game の codec で渡す（PlayTranslator 参照）。
        for (PacketName name : PacketName.values()) {
            if (name.state() == State.PLAY) {
                assertFalse(name.name().contains("REGISTRY"),
                        name + " はプレイ状態のレジストリではないこと");
            }
        }
    }

    @Test
    void noDuplicateIdsWithinOneStateDirectionAndProtocol() {
        for (State state : State.values()) {
            for (Direction direction : Direction.values()) {
                for (int protocol : new int[]{V763, V764, V765, V766}) {
                    List<PacketName> names = PacketIds.names(state, direction, protocol);
                    Set<Integer> seen = new HashSet<>();
                    for (PacketName name : names) {
                        int id = PacketIds.id(name, protocol).orElseThrow();
                        assertTrue(seen.add(id),
                                state + "/" + direction + "/" + protocol + " でIDが重複: 0x"
                                        + Integer.toHexString(id));
                    }
                }
            }
        }
    }
}
