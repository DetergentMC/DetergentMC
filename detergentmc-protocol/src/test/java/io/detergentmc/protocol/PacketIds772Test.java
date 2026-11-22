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
 * {@link PacketIds} の772（1.21.7／1.21.8）向けスポットチェック。
 * 期待値は PrismarineJS minecraft-data の protocol.json（1.21.8）と
 * tools/packet-ids-772.py の出力による。
 * 771と比べ、パケットIDは1件も変わらない（新規・削除なし）。
 * minecraft-data 1.21.8 の protocol.json は 1.21.6 のものとバイト単位で
 * 同一のため、772のIDはすべて771と同じである。
 */
class PacketIds772Test {

    private static final int V771 = ProtocolVersion.V1_21_6;
    private static final int V772 = ProtocolVersion.V1_21_7;

    private static void assertId(int expected, PacketName name, int protocol) {
        assertEquals(OptionalInt.of(expected), PacketIds.id(name, protocol),
                name + " for protocol " + protocol);
    }

    private static void assertAbsent(PacketName name, int protocol) {
        assertEquals(OptionalInt.empty(), PacketIds.id(name, protocol),
                name + " は " + protocol + " に存在しないこと");
    }

    @Test
    void tableKnows772() {
        assertTrue(PacketIds.isSupported(V772));
        assertEquals(List.of(4, 5, 47, 107, 108, 109, 110, 210, 315, 316, 335, 338, 340, 393, 401, 404, 477, 480, 485, 490, 498, 573, 575, 578, 735, 736, 751, 753, 754, 755, 756, 757, 758, 759, 760, 761, 762, 763, 764, 765, 766, 767, 768, 769, 770, 771, 772, 773, 774, 775, 776),
                PacketIds.supportedProtocols());
    }

    @Test
    void everyIdIsIdenticalTo771() {
        // 247エントリすべてについて772のIDが771と等しいこと
        // （packet-ids-772.py：771とIDが違うものは0件、772で削除は0件、
        // 772で新規は0件）。-1（存在しない）も含めて一致する。
        for (PacketName name : PacketName.values()) {
            assertEquals(name.id771(), name.id772(),
                    name + " の772のIDは771と同じこと");
        }
    }

    @Test
    void handshakeAndLogin() {
        assertId(0x00, PacketName.HANDSHAKE, V772);
        assertId(0x00, PacketName.LOGIN_DISCONNECT, V772);
        assertId(0x01, PacketName.LOGIN_ENCRYPTION_REQUEST, V772);
        // Login Success のIDは変わらない（0x02）。
        assertId(0x02, PacketName.LOGIN_SUCCESS, V772);
        assertId(0x03, PacketName.LOGIN_SET_COMPRESSION, V772);
        assertId(0x04, PacketName.LOGIN_PLUGIN_REQUEST, V772);
        assertId(0x05, PacketName.LOGIN_COOKIE_REQUEST, V772);
        assertId(0x04, PacketName.LOGIN_COOKIE_RESPONSE, V772);
    }

    @Test
    void configurationIdsUnchangedFrom771() {
        // 設定フェーズのIDは771と同じ並び（新規・削除なし）。
        assertId(0x00, PacketName.CONFIG_COOKIE_REQUEST, V772);
        assertId(0x01, PacketName.CONFIG_PLUGIN_MESSAGE, V772);
        assertId(0x02, PacketName.CONFIG_DISCONNECT, V772);
        assertId(0x03, PacketName.CONFIG_FINISH_CONFIGURATION, V772);
        assertId(0x04, PacketName.CONFIG_KEEP_ALIVE, V772);
        assertId(0x05, PacketName.CONFIG_PING, V772);
        assertId(0x06, PacketName.CONFIG_RESET_CHAT, V772);
        assertId(0x07, PacketName.CONFIG_REGISTRY_DATA, V772);
        assertId(0x08, PacketName.CONFIG_REMOVE_RESOURCE_PACK, V772);
        assertId(0x09, PacketName.CONFIG_ADD_RESOURCE_PACK, V772);
        assertId(0x0a, PacketName.CONFIG_STORE_COOKIE, V772);
        assertId(0x0b, PacketName.CONFIG_TRANSFER, V772);
        assertId(0x0c, PacketName.CONFIG_FEATURE_FLAGS, V772);
        assertId(0x0d, PacketName.CONFIG_TAGS, V772);
        assertId(0x0e, PacketName.CONFIG_SELECT_KNOWN_PACKS, V772);
        assertId(0x0f, PacketName.CONFIG_CUSTOM_REPORT_DETAILS, V772);
        assertId(0x10, PacketName.CONFIG_SERVER_LINKS, V772);
        // 771で新規のダイアログ関連も同じID。
        assertId(0x11, PacketName.CONFIG_CLEAR_DIALOG, V772);
        assertId(0x12, PacketName.CONFIG_SHOW_DIALOG, V772);
        assertId(0x08, PacketName.CONFIG_CUSTOM_CLICK_ACTION_TO_SERVER, V772);
        // 771で削除の2件は772でも無い。
        assertAbsent(PacketName.CONFIG_CUSTOM_REPORT_DETAILS_TO_SERVER, V772);
        assertAbsent(PacketName.CONFIG_SERVER_LINKS_TO_SERVER, V772);
        // 件数：toClient 19、toServer 9（771と同じ）。
        assertEquals(19, PacketIds.names(State.CONFIGURATION, Direction.CLIENTBOUND, V772).size());
        assertEquals(9, PacketIds.names(State.CONFIGURATION, Direction.SERVERBOUND, V772).size());
    }

    @Test
    void playIdsUnchangedFrom771() {
        assertId(0x2b, PacketName.PLAY_JOIN_GAME, V772);
        assertId(0x2b, PacketName.PLAY_JOIN_GAME, V771);
        assertId(0x5a, PacketName.PLAY_SPAWN_POSITION, V772);
        assertId(0x26, PacketName.PLAY_KEEP_ALIVE, V772);
        assertId(0x3a, PacketName.PLAY_PLAYER_CHAT, V772);
        assertId(0x27, PacketName.PLAY_CHUNK_DATA, V772);
        assertId(0x83, PacketName.PLAY_TRACKED_WAYPOINT, V772);
        assertId(0x84, PacketName.PLAY_CLEAR_DIALOG, V772);
        assertId(0x85, PacketName.PLAY_SHOW_DIALOG, V772);
        // サーバー行きも771と同じ（change_gamemode 0x04、custom_click_action 0x41）。
        assertId(0x04, PacketName.PLAY_CHANGE_GAMEMODE, V772);
        assertId(0x41, PacketName.PLAY_CUSTOM_CLICK_ACTION_TO_SERVER, V772);
        assertId(0x08, PacketName.PLAY_CHAT_MESSAGE, V772);
        assertId(0x08, PacketName.PLAY_CHAT_MESSAGE, V771);
        assertId(0x11, PacketName.PLAY_WINDOW_CLICK, V772);
        assertId(0x37, PacketName.PLAY_SET_CREATIVE_SLOT, V772);
        assertId(0x1d, PacketName.PLAY_SET_PLAYER_POSITION, V772);
        assertId(0x1d, PacketName.PLAY_SET_PLAYER_POSITION, V771);
        // 件数：toClient 134、toServer 65（771と同じ）。
        assertEquals(134, PacketIds.names(State.PLAY, Direction.CLIENTBOUND, V772).size());
        assertEquals(65, PacketIds.names(State.PLAY, Direction.SERVERBOUND, V772).size());
        // 771で削除のものは772でも無い。
        assertAbsent(PacketName.PLAY_SPAWN_EXPERIENCE_ORB, V772);
        assertAbsent(PacketName.PLAY_PICK_ITEM, V772);
        assertAbsent(PacketName.PLAY_UNLOCK_RECIPES, V772);
        assertAbsent(PacketName.PLAY_STEER_VEHICLE, V772);
    }

    @Test
    void reverseLookup772() {
        assertEquals(PacketName.PLAY_JOIN_GAME,
                PacketIds.nameFor(0x2b, State.PLAY, Direction.CLIENTBOUND, V772).orElseThrow());
        assertEquals(PacketName.PLAY_TRACKED_WAYPOINT,
                PacketIds.nameFor(0x83, State.PLAY, Direction.CLIENTBOUND, V772).orElseThrow());
        assertEquals(PacketName.PLAY_SHOW_DIALOG,
                PacketIds.nameFor(0x85, State.PLAY, Direction.CLIENTBOUND, V772).orElseThrow());
        assertEquals(PacketName.PLAY_CHANGE_GAMEMODE,
                PacketIds.nameFor(0x04, State.PLAY, Direction.SERVERBOUND, V772).orElseThrow());
        assertEquals(PacketName.PLAY_CUSTOM_CLICK_ACTION_TO_SERVER,
                PacketIds.nameFor(0x41, State.PLAY, Direction.SERVERBOUND, V772).orElseThrow());
        assertEquals(PacketName.CONFIG_SERVER_LINKS,
                PacketIds.nameFor(0x10, State.CONFIGURATION, Direction.CLIENTBOUND, V772)
                        .orElseThrow());
        assertEquals(PacketName.CONFIG_SHOW_DIALOG,
                PacketIds.nameFor(0x12, State.CONFIGURATION, Direction.CLIENTBOUND, V772)
                        .orElseThrow());
    }

    @Test
    void legacyPingAndPingRequestNotInTable() {
        // 従来通り、legacy_server_list_ping（0xFE）と play の ping_request
        // （772では 0x25）は表に入れない（プロキシが落とす方針のため）。
        assertFalse(PacketIds.names(State.PLAY, Direction.SERVERBOUND, V772).stream()
                .anyMatch(n -> n.name().contains("PING_REQUEST")));
        assertFalse(PacketIds.names(State.HANDSHAKING, Direction.SERVERBOUND, V772).stream()
                .anyMatch(n -> n.name().contains("LEGACY")));
    }
}
