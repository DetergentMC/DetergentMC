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
 * {@link PacketIds} の771（1.21.6）向けスポットチェック。
 * 期待値は PrismarineJS minecraft-data の protocol.json（1.21.6）と
 * tools/packet-ids-771.py の出力による。
 * 770と比べ、クライアント行きのIDは変わらず（新規3件：tracked_waypoint
 * 0x83、clear_dialog 0x84、show_dialog 0x85）、サーバー行きは59件のIDが
 * 1つずつ上がり（change_gamemode 0x04 の挿入による。ただし spectate は
 * 0x3d のままで、test_instance_block_action が 0x3c から 0x3e へ
 * 2つ動いた）、新規は2件（change_gamemode・custom_click_action）。
 * 設定フェーズは新規3件（clear_dialog 0x11、show_dialog 0x12、
 * custom_click_action 0x08）と削除2件（custom_report_details・
 * server_links のサーバー行き）。
 */
class PacketIds771Test {

    private static final int V763 = ProtocolVersion.V1_20_1;
    private static final int V764 = ProtocolVersion.V1_20_2;
    private static final int V765 = ProtocolVersion.V1_20_4;
    private static final int V766 = ProtocolVersion.V1_20_5;
    private static final int V767 = ProtocolVersion.V1_21;
    private static final int V768 = ProtocolVersion.V1_21_2;
    private static final int V769 = ProtocolVersion.V1_21_4;
    private static final int V770 = ProtocolVersion.V1_21_5;
    private static final int V771 = ProtocolVersion.V1_21_6;

    private static void assertId(int expected, PacketName name, int protocol) {
        assertEquals(OptionalInt.of(expected), PacketIds.id(name, protocol),
                name + " for protocol " + protocol);
    }

    private static void assertAbsent(PacketName name, int protocol) {
        assertEquals(OptionalInt.empty(), PacketIds.id(name, protocol),
                name + " は " + protocol + " に存在しないこと");
    }

    @Test
    void tableKnows771() {
        assertTrue(PacketIds.isSupported(V771));
        assertEquals(List.of(755, 756, 757, 759, 760, 761, 762, 763, 764, 765, 766, 767, 768, 769, 770, 771, 772, 773, 774, 775), PacketIds.supportedProtocols());
    }

    @Test
    void handshakeAndLogin() {
        assertId(0x00, PacketName.HANDSHAKE, V771);
        assertId(0x00, PacketName.LOGIN_DISCONNECT, V771);
        assertId(0x01, PacketName.LOGIN_ENCRYPTION_REQUEST, V771);
        // Login Success のIDは変わらない（0x02）。
        assertId(0x02, PacketName.LOGIN_SUCCESS, V771);
        assertId(0x03, PacketName.LOGIN_SET_COMPRESSION, V771);
        assertId(0x04, PacketName.LOGIN_PLUGIN_REQUEST, V771);
        assertId(0x05, PacketName.LOGIN_COOKIE_REQUEST, V771);
        assertId(0x04, PacketName.LOGIN_COOKIE_RESPONSE, V771);
    }

    @Test
    void configurationIdsChangedFrom770() {
        // 設定フェーズのIDは770と同じ並びに、新規3件と削除2件。
        assertId(0x00, PacketName.CONFIG_COOKIE_REQUEST, V771);
        assertId(0x01, PacketName.CONFIG_PLUGIN_MESSAGE, V771);
        assertId(0x02, PacketName.CONFIG_DISCONNECT, V771);
        assertId(0x03, PacketName.CONFIG_FINISH_CONFIGURATION, V771);
        assertId(0x04, PacketName.CONFIG_KEEP_ALIVE, V771);
        assertId(0x05, PacketName.CONFIG_PING, V771);
        assertId(0x06, PacketName.CONFIG_RESET_CHAT, V771);
        assertId(0x07, PacketName.CONFIG_REGISTRY_DATA, V771);
        assertId(0x08, PacketName.CONFIG_REMOVE_RESOURCE_PACK, V771);
        assertId(0x09, PacketName.CONFIG_ADD_RESOURCE_PACK, V771);
        assertId(0x0a, PacketName.CONFIG_STORE_COOKIE, V771);
        assertId(0x0b, PacketName.CONFIG_TRANSFER, V771);
        assertId(0x0c, PacketName.CONFIG_FEATURE_FLAGS, V771);
        assertId(0x0d, PacketName.CONFIG_TAGS, V771);
        assertId(0x0e, PacketName.CONFIG_SELECT_KNOWN_PACKS, V771);
        assertId(0x0f, PacketName.CONFIG_CUSTOM_REPORT_DETAILS, V771);
        assertId(0x10, PacketName.CONFIG_SERVER_LINKS, V771);
        // 771で新規（クライアント行き2件）。
        assertId(0x11, PacketName.CONFIG_CLEAR_DIALOG, V771);
        assertId(0x12, PacketName.CONFIG_SHOW_DIALOG, V771);
        assertAbsent(PacketName.CONFIG_CLEAR_DIALOG, V770);
        assertAbsent(PacketName.CONFIG_SHOW_DIALOG, V770);
        // 771で新規（サーバー行き1件）。
        assertId(0x08, PacketName.CONFIG_CUSTOM_CLICK_ACTION_TO_SERVER, V771);
        assertAbsent(PacketName.CONFIG_CUSTOM_CLICK_ACTION_TO_SERVER, V770);
        // 771で削除（サーバー行き2件。770では 0x08／0x09）。
        assertAbsent(PacketName.CONFIG_CUSTOM_REPORT_DETAILS_TO_SERVER, V771);
        assertAbsent(PacketName.CONFIG_SERVER_LINKS_TO_SERVER, V771);
        assertId(0x08, PacketName.CONFIG_CUSTOM_REPORT_DETAILS_TO_SERVER, V770);
        assertId(0x09, PacketName.CONFIG_SERVER_LINKS_TO_SERVER, V770);
        // 件数：toClient 19（770の17から新規＋2）、toServer 9（770の10から新規＋1・削除−2）。
        assertEquals(19, PacketIds.names(State.CONFIGURATION, Direction.CLIENTBOUND, V771).size());
        assertEquals(9, PacketIds.names(State.CONFIGURATION, Direction.SERVERBOUND, V771).size());
    }

    @Test
    void playClientboundIdsUnchangedFrom770() {
        // 771でクライアント行きのIDは変わらない（新規3件のみ）。
        assertId(0x2b, PacketName.PLAY_JOIN_GAME, V771);
        assertId(0x2b, PacketName.PLAY_JOIN_GAME, V770);
        assertId(0x5a, PacketName.PLAY_SPAWN_POSITION, V771);
        assertId(0x5a, PacketName.PLAY_SPAWN_POSITION, V770);
        assertId(0x26, PacketName.PLAY_KEEP_ALIVE, V771);
        assertId(0x26, PacketName.PLAY_KEEP_ALIVE, V770);
        assertId(0x3a, PacketName.PLAY_PLAYER_CHAT, V771);
        assertId(0x3a, PacketName.PLAY_PLAYER_CHAT, V770);
        assertId(0x27, PacketName.PLAY_CHUNK_DATA, V771);
        assertId(0x27, PacketName.PLAY_CHUNK_DATA, V770);
        assertId(0x1f, PacketName.PLAY_SYNC_ENTITY_POSITION, V771);
        assertId(0x1f, PacketName.PLAY_SYNC_ENTITY_POSITION, V770);
        assertId(0x43, PacketName.PLAY_RECIPE_BOOK_ADD, V771);
        assertId(0x43, PacketName.PLAY_RECIPE_BOOK_ADD, V770);
        assertId(0x59, PacketName.PLAY_SET_CURSOR_ITEM, V771);
        assertId(0x59, PacketName.PLAY_SET_CURSOR_ITEM, V770);
        assertId(0x65, PacketName.PLAY_SET_PLAYER_INVENTORY, V771);
        assertId(0x65, PacketName.PLAY_SET_PLAYER_INVENTORY, V770);
        assertId(0x77, PacketName.PLAY_TEST_INSTANCE_BLOCK_STATUS, V771);
        assertId(0x77, PacketName.PLAY_TEST_INSTANCE_BLOCK_STATUS, V770);
        // 771で新規（3件）。
        assertId(0x83, PacketName.PLAY_TRACKED_WAYPOINT, V771);
        assertId(0x84, PacketName.PLAY_CLEAR_DIALOG, V771);
        assertId(0x85, PacketName.PLAY_SHOW_DIALOG, V771);
        assertAbsent(PacketName.PLAY_TRACKED_WAYPOINT, V770);
        assertAbsent(PacketName.PLAY_CLEAR_DIALOG, V770);
        assertAbsent(PacketName.PLAY_SHOW_DIALOG, V770);
        // 件数：toClient 134（770の131から新規＋3）。
        assertEquals(134, PacketIds.names(State.PLAY, Direction.CLIENTBOUND, V771).size());
    }

    @Test
    void playServerboundIdsShiftedUpByOneFrom770() {
        // 770と ID が変わったもの（change_gamemode 0x04 の挿入で1つ上がる）。
        assertId(0x05, PacketName.PLAY_MESSAGE_ACKNOWLEDGEMENT, V771);
        assertId(0x04, PacketName.PLAY_MESSAGE_ACKNOWLEDGEMENT, V770);
        assertId(0x08, PacketName.PLAY_CHAT_MESSAGE, V771);
        assertId(0x07, PacketName.PLAY_CHAT_MESSAGE, V770);
        assertId(0x11, PacketName.PLAY_WINDOW_CLICK, V771);
        assertId(0x10, PacketName.PLAY_WINDOW_CLICK, V770);
        assertId(0x37, PacketName.PLAY_SET_CREATIVE_SLOT, V771);
        assertId(0x36, PacketName.PLAY_SET_CREATIVE_SLOT, V770);
        assertId(0x2a, PacketName.PLAY_PLAYER_INPUT, V771);
        assertId(0x29, PacketName.PLAY_PLAYER_INPUT, V770);
        assertId(0x30, PacketName.PLAY_RESOURCE_PACK_RESPONSE, V771);
        assertId(0x2f, PacketName.PLAY_RESOURCE_PACK_RESPONSE, V770);
        assertId(0x3b, PacketName.PLAY_UPDATE_SIGN, V771);
        assertId(0x3a, PacketName.PLAY_UPDATE_SIGN, V770);
        assertId(0x3c, PacketName.PLAY_ARM_ANIMATION, V771);
        assertId(0x3b, PacketName.PLAY_ARM_ANIMATION, V770);
        assertId(0x3f, PacketName.PLAY_BLOCK_PLACE, V771);
        assertId(0x3e, PacketName.PLAY_BLOCK_PLACE, V770);
        assertId(0x40, PacketName.PLAY_USE_ITEM, V771);
        assertId(0x3f, PacketName.PLAY_USE_ITEM, V770);
        assertId(0x3a, PacketName.PLAY_SET_TEST_BLOCK, V771);
        assertId(0x39, PacketName.PLAY_SET_TEST_BLOCK, V770);
        // spectate は 0x3d のまま。test_instance_block_action は
        // 0x3c から 0x3e へ 2つ動いた（spectate の後ろへ）。
        assertId(0x3d, PacketName.PLAY_SPECTATE, V771);
        assertId(0x3d, PacketName.PLAY_SPECTATE, V770);
        assertId(0x3e, PacketName.PLAY_TEST_INSTANCE_BLOCK_ACTION, V771);
        assertId(0x3c, PacketName.PLAY_TEST_INSTANCE_BLOCK_ACTION, V770);
        // 771で新規（2件）。
        assertId(0x04, PacketName.PLAY_CHANGE_GAMEMODE, V771);
        assertId(0x41, PacketName.PLAY_CUSTOM_CLICK_ACTION_TO_SERVER, V771);
        assertAbsent(PacketName.PLAY_CHANGE_GAMEMODE, V770);
        assertAbsent(PacketName.PLAY_CUSTOM_CLICK_ACTION_TO_SERVER, V770);
        // ping_request（771では 0x25）は表に入れない（プロキシが落とす方針）。
        // 件数：toServer 65（770の63から新規＋2）。
        assertEquals(65, PacketIds.names(State.PLAY, Direction.SERVERBOUND, V771).size());
    }

    @Test
    void reverseLookup771() {
        assertEquals(PacketName.PLAY_JOIN_GAME,
                PacketIds.nameFor(0x2b, State.PLAY, Direction.CLIENTBOUND, V771).orElseThrow());
        assertEquals(PacketName.PLAY_TRACKED_WAYPOINT,
                PacketIds.nameFor(0x83, State.PLAY, Direction.CLIENTBOUND, V771).orElseThrow());
        assertEquals(PacketName.PLAY_SHOW_DIALOG,
                PacketIds.nameFor(0x85, State.PLAY, Direction.CLIENTBOUND, V771).orElseThrow());
        assertEquals(PacketName.PLAY_CHANGE_GAMEMODE,
                PacketIds.nameFor(0x04, State.PLAY, Direction.SERVERBOUND, V771).orElseThrow());
        assertEquals(PacketName.PLAY_CUSTOM_CLICK_ACTION_TO_SERVER,
                PacketIds.nameFor(0x41, State.PLAY, Direction.SERVERBOUND, V771).orElseThrow());
        assertEquals(PacketName.CONFIG_SERVER_LINKS,
                PacketIds.nameFor(0x10, State.CONFIGURATION, Direction.CLIENTBOUND, V771)
                        .orElseThrow());
        assertEquals(PacketName.CONFIG_SHOW_DIALOG,
                PacketIds.nameFor(0x12, State.CONFIGURATION, Direction.CLIENTBOUND, V771)
                        .orElseThrow());
    }

    @Test
    void legacyPingAndPingRequestNotInTable() {
        // 従来通り、legacy_server_list_ping（0xFE）と play の ping_request
        // （771では 0x25）は表に入れない（プロキシが落とす方針のため）。
        assertFalse(PacketIds.names(State.PLAY, Direction.SERVERBOUND, V771).stream()
                .anyMatch(n -> n.name().contains("PING_REQUEST")));
        assertFalse(PacketIds.names(State.HANDSHAKING, Direction.SERVERBOUND, V771).stream()
                .anyMatch(n -> n.name().contains("LEGACY")));
    }
}
