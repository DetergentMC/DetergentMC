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
 * {@link PacketIds} の774（1.21.11）向けスポットチェック。
 * 期待値は PrismarineJS minecraft-data の protocol.json（1.21.11）と
 * tools/packet-ids-774.py の出力による。
 * 773と比べ、パケットIDが違うものは0件。新規パケットは0件、削除も0件。
 */
class PacketIds774Test {

    private static final int V773 = ProtocolVersion.V1_21_9;
    private static final int V774 = ProtocolVersion.V1_21_11;

    private static void assertId(int expected, PacketName name, int protocol) {
        assertEquals(OptionalInt.of(expected), PacketIds.id(name, protocol),
                name + " for protocol " + protocol);
    }

    private static void assertAbsent(PacketName name, int protocol) {
        assertEquals(OptionalInt.empty(), PacketIds.id(name, protocol),
                name + " は " + protocol + " に存在しないこと");
    }

    @Test
    void tableKnows774() {
        assertTrue(PacketIds.isSupported(V774));
        assertEquals(List.of(480, 485, 490, 498, 573, 575, 578, 758, 759, 760, 761, 762, 763, 764, 765, 766, 767, 768, 769, 770, 771, 772, 773, 774, 775, 776),
                PacketIds.supportedProtocols());
    }

    @Test
    void noIdsChangedFrom773() {
        // packet-ids-774.py：255エントリすべてが一意に写像でき、773とIDが
        // 違うものは0件、774で削除（-1）も0件、774で新規も0件。
        for (PacketName name : PacketName.values()) {
            assertEquals(name.id773(), name.id774(),
                    name + " は773と774でIDが同じはず");
        }
        assertEquals(-1, PacketName.PLAY_DEBUG_SAMPLE_SUBSCRIPTION.id774());
        assertEquals(0x16, PacketName.PLAY_DEBUG_SUBSCRIPTION_REQUEST.id774());
    }

    @Test
    void handshakeAndLogin() {
        assertId(0x00, PacketName.HANDSHAKE, V774);
        assertId(0x00, PacketName.LOGIN_DISCONNECT, V774);
        assertId(0x01, PacketName.LOGIN_ENCRYPTION_REQUEST, V774);
        // Login Success のIDは変わらない（0x02）。
        assertId(0x02, PacketName.LOGIN_SUCCESS, V774);
        assertId(0x03, PacketName.LOGIN_SET_COMPRESSION, V774);
        assertId(0x04, PacketName.LOGIN_PLUGIN_REQUEST, V774);
        assertId(0x05, PacketName.LOGIN_COOKIE_REQUEST, V774);
        assertId(0x04, PacketName.LOGIN_COOKIE_RESPONSE, V774);
    }

    @Test
    void configurationIdsAreSameAs773() {
        // 設定フェーズのIDは773と同じ並び。code_of_conduct
        // （クライアント行き 0x13）と accept_code_of_conduct（サーバー行き 0x09）は774でも同じ。
        assertId(0x00, PacketName.CONFIG_COOKIE_REQUEST, V774);
        assertId(0x01, PacketName.CONFIG_PLUGIN_MESSAGE, V774);
        assertId(0x02, PacketName.CONFIG_DISCONNECT, V774);
        assertId(0x03, PacketName.CONFIG_FINISH_CONFIGURATION, V774);
        assertId(0x04, PacketName.CONFIG_KEEP_ALIVE, V774);
        assertId(0x05, PacketName.CONFIG_PING, V774);
        assertId(0x06, PacketName.CONFIG_RESET_CHAT, V774);
        assertId(0x07, PacketName.CONFIG_REGISTRY_DATA, V774);
        assertId(0x08, PacketName.CONFIG_REMOVE_RESOURCE_PACK, V774);
        assertId(0x09, PacketName.CONFIG_ADD_RESOURCE_PACK, V774);
        assertId(0x0a, PacketName.CONFIG_STORE_COOKIE, V774);
        assertId(0x0b, PacketName.CONFIG_TRANSFER, V774);
        assertId(0x0c, PacketName.CONFIG_FEATURE_FLAGS, V774);
        assertId(0x0d, PacketName.CONFIG_TAGS, V774);
        assertId(0x0e, PacketName.CONFIG_SELECT_KNOWN_PACKS, V774);
        assertId(0x0f, PacketName.CONFIG_CUSTOM_REPORT_DETAILS, V774);
        assertId(0x10, PacketName.CONFIG_SERVER_LINKS, V774);
        // 771で新規のダイアログ関連も773と同じID。
        assertId(0x11, PacketName.CONFIG_CLEAR_DIALOG, V774);
        assertId(0x12, PacketName.CONFIG_SHOW_DIALOG, V774);
        // 773で新規の行動規範（774でも同じ）。
        assertId(0x13, PacketName.CONFIG_CODE_OF_CONDUCT, V774);
        assertId(0x09, PacketName.CONFIG_ACCEPT_CODE_OF_CONDUCT, V774);
        assertId(0x08, PacketName.CONFIG_CUSTOM_CLICK_ACTION_TO_SERVER, V774);
        // 771で削除の2件は774でも無い。
        assertAbsent(PacketName.CONFIG_CUSTOM_REPORT_DETAILS_TO_SERVER, V774);
        assertAbsent(PacketName.CONFIG_SERVER_LINKS_TO_SERVER, V774);
        // 件数：toClient 20、toServer 10（772の19／9に新規1件ずつ）。
        assertEquals(20, PacketIds.names(State.CONFIGURATION, Direction.CLIENTBOUND, V774).size());
        assertEquals(10, PacketIds.names(State.CONFIGURATION, Direction.SERVERBOUND, V774).size());
    }

    @Test
    void playIdsAreSameAs773() {
        // パケットIDは773とまったく同じ（新規デバッグ系の挿入によるずれは無い）。
        assertId(0x30, PacketName.PLAY_JOIN_GAME, V774);
        assertId(0x30, PacketName.PLAY_JOIN_GAME, V774);
        assertId(0x5f, PacketName.PLAY_SPAWN_POSITION, V774);
        assertId(0x2b, PacketName.PLAY_KEEP_ALIVE, V774);
        assertId(0x3f, PacketName.PLAY_PLAYER_CHAT, V774);
        assertId(0x2c, PacketName.PLAY_CHUNK_DATA, V774);
        assertId(0x88, PacketName.PLAY_TRACKED_WAYPOINT, V774);
        assertId(0x89, PacketName.PLAY_CLEAR_DIALOG, V774);
        assertId(0x8a, PacketName.PLAY_SHOW_DIALOG, V774);
        // デバッグ系パケット（773で新規）は774でも同じID。
        assertId(0x1a, PacketName.PLAY_DEBUG_BLOCK_VALUE, V774);
        assertId(0x1b, PacketName.PLAY_DEBUG_CHUNK_VALUE, V774);
        assertId(0x1c, PacketName.PLAY_DEBUG_ENTITY_VALUE, V774);
        assertId(0x1d, PacketName.PLAY_DEBUG_EVENT, V774);
        assertId(0x27, PacketName.PLAY_GAME_TEST_HIGHLIGHT_POS, V774);
        // 773で削除の debug_sample_subscription は774でも無い。後継の
        // debug_subscription_request（同ID 0x16）は774でも同じ。
        assertAbsent(PacketName.PLAY_DEBUG_SAMPLE_SUBSCRIPTION, V774);
        assertId(0x16, PacketName.PLAY_DEBUG_SUBSCRIPTION_REQUEST, V774);
        // サーバー行きのIDは773と同じ（change_gamemode 0x04、custom_click_action 0x41）。
        assertId(0x04, PacketName.PLAY_CHANGE_GAMEMODE, V774);
        assertId(0x41, PacketName.PLAY_CUSTOM_CLICK_ACTION_TO_SERVER, V774);
        assertId(0x08, PacketName.PLAY_CHAT_MESSAGE, V774);
        assertId(0x08, PacketName.PLAY_CHAT_MESSAGE, V774);
        assertId(0x11, PacketName.PLAY_WINDOW_CLICK, V774);
        assertId(0x37, PacketName.PLAY_SET_CREATIVE_SLOT, V774);
        assertId(0x1d, PacketName.PLAY_SET_PLAYER_POSITION, V774);
        assertId(0x1d, PacketName.PLAY_SET_PLAYER_POSITION, V774);
        // 件数：toClient 139、toServer 65（773と同じ）。
        assertEquals(139, PacketIds.names(State.PLAY, Direction.CLIENTBOUND, V774).size());
        assertEquals(65, PacketIds.names(State.PLAY, Direction.SERVERBOUND, V774).size());
        // 771で削除のものは773でも無い。
        assertAbsent(PacketName.PLAY_SPAWN_EXPERIENCE_ORB, V774);
        assertAbsent(PacketName.PLAY_PICK_ITEM, V774);
        assertAbsent(PacketName.PLAY_UNLOCK_RECIPES, V774);
        assertAbsent(PacketName.PLAY_STEER_VEHICLE, V774);
    }

    @Test
    void reverseLookup774() {
        assertEquals(PacketName.PLAY_JOIN_GAME,
                PacketIds.nameFor(0x30, State.PLAY, Direction.CLIENTBOUND, V774).orElseThrow());
        assertEquals(PacketName.PLAY_TRACKED_WAYPOINT,
                PacketIds.nameFor(0x88, State.PLAY, Direction.CLIENTBOUND, V774).orElseThrow());
        assertEquals(PacketName.PLAY_SHOW_DIALOG,
                PacketIds.nameFor(0x8a, State.PLAY, Direction.CLIENTBOUND, V774).orElseThrow());
        assertEquals(PacketName.PLAY_DEBUG_BLOCK_VALUE,
                PacketIds.nameFor(0x1a, State.PLAY, Direction.CLIENTBOUND, V774).orElseThrow());
        assertEquals(PacketName.PLAY_DEBUG_SUBSCRIPTION_REQUEST,
                PacketIds.nameFor(0x16, State.PLAY, Direction.SERVERBOUND, V774).orElseThrow());
        assertEquals(PacketName.PLAY_CHANGE_GAMEMODE,
                PacketIds.nameFor(0x04, State.PLAY, Direction.SERVERBOUND, V774).orElseThrow());
        assertEquals(PacketName.PLAY_CUSTOM_CLICK_ACTION_TO_SERVER,
                PacketIds.nameFor(0x41, State.PLAY, Direction.SERVERBOUND, V774).orElseThrow());
        assertEquals(PacketName.CONFIG_CODE_OF_CONDUCT,
                PacketIds.nameFor(0x13, State.CONFIGURATION, Direction.CLIENTBOUND, V774)
                        .orElseThrow());
        assertEquals(PacketName.CONFIG_SERVER_LINKS,
                PacketIds.nameFor(0x10, State.CONFIGURATION, Direction.CLIENTBOUND, V774)
                        .orElseThrow());
        assertEquals(PacketName.CONFIG_SHOW_DIALOG,
                PacketIds.nameFor(0x12, State.CONFIGURATION, Direction.CLIENTBOUND, V774)
                        .orElseThrow());
    }

    @Test
    void legacyPingAndPingRequestNotInTable() {
        // 従来通り、legacy_server_list_ping（0xFE）と play の ping_request
        // （774でも 0x25）は表に入れない（プロキシが落とす方針のため）。
        assertFalse(PacketIds.names(State.PLAY, Direction.SERVERBOUND, V774).stream()
                .anyMatch(n -> n.name().contains("PING_REQUEST")));
        assertFalse(PacketIds.names(State.HANDSHAKING, Direction.SERVERBOUND, V774).stream()
                .anyMatch(n -> n.name().contains("LEGACY")));
    }
}
