package io.detergentmc.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.detergentmc.protocol.PacketIds.Direction;
import io.detergentmc.protocol.PacketIds.PacketName;
import io.detergentmc.protocol.PacketIds.State;
import java.util.List;
import java.util.OptionalInt;
import org.junit.jupiter.api.Test;

/**
 * {@link PacketIds} の768（1.21.2／1.21.3）向けスポットチェック。
 * 期待値は PrismarineJS minecraft-data の protocol.json（1.21.3）と
 * tools/packet-ids-768.py の出力（packet-ids-768.json）による。
 * 767と比べ、既存のパケットは145件のIDが変わり、削除は2件
 * （unlock_recipes・steer_vehicle）、新規は11件（プレイの
 * Sync Entity Position・Move Minecart・Player Rotation・
 * Recipe Book Add／Remove／Settings・Set Cursor Item・
 * Set Player Inventoryと、サーバー行きの Select Bundle Item・
 * Tick End・Player Input）である。設定フェーズ・ログインのIDは
 * 変わらない。
 */
class PacketIds768Test {

    private static final int V763 = ProtocolVersion.V1_20_1;
    private static final int V764 = ProtocolVersion.V1_20_2;
    private static final int V765 = ProtocolVersion.V1_20_4;
    private static final int V766 = ProtocolVersion.V1_20_5;
    private static final int V767 = ProtocolVersion.V1_21;
    private static final int V768 = ProtocolVersion.V1_21_2;

    private static void assertId(int expected, PacketName name, int protocol) {
        assertEquals(OptionalInt.of(expected), PacketIds.id(name, protocol),
                name + " for protocol " + protocol);
    }

    private static void assertAbsent(PacketName name, int protocol) {
        assertEquals(OptionalInt.empty(), PacketIds.id(name, protocol),
                name + " は " + protocol + " に存在しないこと");
    }

    @Test
    void tableKnows768() {
        assertTrue(PacketIds.isSupported(V768));
        assertEquals(List.of(735, 736, 751, 753, 754, 755, 756, 757, 759, 760, 761, 762, 763, 764, 765, 766, 767, 768, 769, 770, 771, 772, 773, 774, 775), PacketIds.supportedProtocols());
    }

    @Test
    void handshakeAndLogin() {
        assertId(0x00, PacketName.HANDSHAKE, V768);
        assertId(0x00, PacketName.LOGIN_DISCONNECT, V768);
        assertId(0x01, PacketName.LOGIN_ENCRYPTION_REQUEST, V768);
        // Login Success のIDは変わらない（0x02）。ただしペイロードの
        // Strict Error Handling の bool は768で廃止された（767にはある）。
        assertId(0x02, PacketName.LOGIN_SUCCESS, V768);
        assertId(0x03, PacketName.LOGIN_SET_COMPRESSION, V768);
        assertId(0x04, PacketName.LOGIN_PLUGIN_REQUEST, V768);
        assertId(0x05, PacketName.LOGIN_COOKIE_REQUEST, V768);
        assertId(0x04, PacketName.LOGIN_COOKIE_RESPONSE, V768);
    }

    @Test
    void configurationIdsUnchangedFrom767() {
        // 設定フェーズのIDは767と同じ並び。
        assertId(0x00, PacketName.CONFIG_COOKIE_REQUEST, V768);
        assertId(0x01, PacketName.CONFIG_PLUGIN_MESSAGE, V768);
        assertId(0x02, PacketName.CONFIG_DISCONNECT, V768);
        assertId(0x03, PacketName.CONFIG_FINISH_CONFIGURATION, V768);
        assertId(0x04, PacketName.CONFIG_KEEP_ALIVE, V768);
        assertId(0x05, PacketName.CONFIG_PING, V768);
        assertId(0x06, PacketName.CONFIG_RESET_CHAT, V768);
        assertId(0x07, PacketName.CONFIG_REGISTRY_DATA, V768);
        assertId(0x08, PacketName.CONFIG_REMOVE_RESOURCE_PACK, V768);
        assertId(0x09, PacketName.CONFIG_ADD_RESOURCE_PACK, V768);
        assertId(0x0a, PacketName.CONFIG_STORE_COOKIE, V768);
        assertId(0x0b, PacketName.CONFIG_TRANSFER, V768);
        assertId(0x0c, PacketName.CONFIG_FEATURE_FLAGS, V768);
        assertId(0x0d, PacketName.CONFIG_TAGS, V768);
        assertId(0x0e, PacketName.CONFIG_SELECT_KNOWN_PACKS, V768);
        assertId(0x0f, PacketName.CONFIG_CUSTOM_REPORT_DETAILS, V768);
        assertId(0x10, PacketName.CONFIG_SERVER_LINKS, V768);
        assertId(0x08, PacketName.CONFIG_CUSTOM_REPORT_DETAILS_TO_SERVER, V768);
        assertId(0x09, PacketName.CONFIG_SERVER_LINKS_TO_SERVER, V768);
        // 件数：toClient 17、toServer 10（767と同じ）。
        assertEquals(17, PacketIds.names(State.CONFIGURATION, Direction.CLIENTBOUND, V768).size());
        assertEquals(10, PacketIds.names(State.CONFIGURATION, Direction.SERVERBOUND, V768).size());
    }

    @Test
    void playIdsChangedFrom767() {
        // 767と ID が変わったもの（スポットチェック）。
        assertId(0x2c, PacketName.PLAY_JOIN_GAME, V768);
        assertId(0x2b, PacketName.PLAY_JOIN_GAME, V767);
        assertId(0x5b, PacketName.PLAY_SPAWN_POSITION, V768);
        assertId(0x27, PacketName.PLAY_KEEP_ALIVE, V768);
        assertId(0x26, PacketName.PLAY_KEEP_ALIVE, V767);
        assertId(0x07, PacketName.PLAY_CHAT_MESSAGE, V768);
        assertId(0x3b, PacketName.PLAY_USE_ITEM, V768);
        assertId(0x28, PacketName.PLAY_CHUNK_DATA, V768);
        assertId(0x27, PacketName.PLAY_CHUNK_DATA, V767);
        // 768で新規。
        assertId(0x20, PacketName.PLAY_SYNC_ENTITY_POSITION, V768);
        assertId(0x31, PacketName.PLAY_MOVE_MINECART, V768);
        assertId(0x43, PacketName.PLAY_PLAYER_ROTATION, V768);
        assertId(0x44, PacketName.PLAY_RECIPE_BOOK_ADD, V768);
        assertId(0x45, PacketName.PLAY_RECIPE_BOOK_REMOVE, V768);
        assertId(0x46, PacketName.PLAY_RECIPE_BOOK_SETTINGS, V768);
        assertId(0x5a, PacketName.PLAY_SET_CURSOR_ITEM, V768);
        assertId(0x66, PacketName.PLAY_SET_PLAYER_INVENTORY, V768);
        assertId(0x02, PacketName.PLAY_SELECT_BUNDLE_ITEM, V768);
        assertId(0x0b, PacketName.PLAY_TICK_END, V768);
        assertId(0x28, PacketName.PLAY_PLAYER_INPUT, V768);
        assertAbsent(PacketName.PLAY_SYNC_ENTITY_POSITION, V767);
        assertAbsent(PacketName.PLAY_MOVE_MINECART, V767);
        assertAbsent(PacketName.PLAY_PLAYER_ROTATION, V767);
        assertAbsent(PacketName.PLAY_RECIPE_BOOK_ADD, V767);
        assertAbsent(PacketName.PLAY_RECIPE_BOOK_REMOVE, V767);
        assertAbsent(PacketName.PLAY_RECIPE_BOOK_SETTINGS, V767);
        assertAbsent(PacketName.PLAY_SET_CURSOR_ITEM, V767);
        assertAbsent(PacketName.PLAY_SET_PLAYER_INVENTORY, V767);
        assertAbsent(PacketName.PLAY_SELECT_BUNDLE_ITEM, V767);
        assertAbsent(PacketName.PLAY_TICK_END, V767);
        assertAbsent(PacketName.PLAY_PLAYER_INPUT, V767);
        // 768で削除（Unlock Recipes・Steer Vehicle）。
        assertAbsent(PacketName.PLAY_UNLOCK_RECIPES, V768);
        assertAbsent(PacketName.PLAY_STEER_VEHICLE, V768);
        assertId(0x41, PacketName.PLAY_UNLOCK_RECIPES, V767);
        assertId(0x26, PacketName.PLAY_STEER_VEHICLE, V767);
        // 削除されたままのもの：766の resource_pack 削除は768にも無い。
        assertAbsent(PacketName.PLAY_RESOURCE_PACK, V767);
        assertAbsent(PacketName.PLAY_RESOURCE_PACK, V768);
        assertAbsent(PacketName.CONFIG_RESOURCE_PACK_SEND, V767);
        assertAbsent(PacketName.CONFIG_RESOURCE_PACK_SEND, V768);
        // 件数：toClient 131、toServer 59。
        assertEquals(131, PacketIds.names(State.PLAY, Direction.CLIENTBOUND, V768).size());
        assertEquals(59, PacketIds.names(State.PLAY, Direction.SERVERBOUND, V768).size());
    }

    @Test
    void reverseLookup768() {
        assertEquals(PacketName.PLAY_JOIN_GAME,
                PacketIds.nameFor(0x2c, State.PLAY, Direction.CLIENTBOUND, V768).orElseThrow());
        assertEquals(PacketName.PLAY_SYNC_ENTITY_POSITION,
                PacketIds.nameFor(0x20, State.PLAY, Direction.CLIENTBOUND, V768).orElseThrow());
        assertEquals(PacketName.PLAY_PLAYER_INPUT,
                PacketIds.nameFor(0x28, State.PLAY, Direction.SERVERBOUND, V768).orElseThrow());
        assertEquals(PacketName.PLAY_RECIPE_BOOK_ADD,
                PacketIds.nameFor(0x44, State.PLAY, Direction.CLIENTBOUND, V768).orElseThrow());
        assertEquals(PacketName.CONFIG_SERVER_LINKS,
                PacketIds.nameFor(0x10, State.CONFIGURATION, Direction.CLIENTBOUND, V768)
                        .orElseThrow());
    }

    @Test
    void legacyPingAndPingRequestNotInTable() {
        // 従来通り、legacy_server_list_ping（0xFE）と play の ping_request
        // （768では 0x23）は表に入れない（プロキシが落とす方針のため）。
        assertFalse(PacketIds.names(State.PLAY, Direction.SERVERBOUND, V768).stream()
                .anyMatch(n -> n.name().contains("PING_REQUEST")));
        assertFalse(PacketIds.names(State.HANDSHAKING, Direction.SERVERBOUND, V768).stream()
                .anyMatch(n -> n.name().contains("LEGACY")));
    }
}
