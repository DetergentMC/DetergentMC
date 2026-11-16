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
 * {@link PacketIds} の773（1.21.9／1.21.10）向けスポットチェック。
 * 期待値は PrismarineJS minecraft-data の protocol.json（1.21.9）と
 * tools/packet-ids-773.py の出力による。
 * 772と比べ、プレイ状態・クライアント行きのIDが108件変わる（新規の
 * デバッグ系5件の挿入によるずれ）。新規パケットは8件、削除は1件
 * （debug_sample_subscription。773では同ID 0x16に形式の違う
 * debug_subscription_request が入る）。
 */
class PacketIds773Test {

    private static final int V772 = ProtocolVersion.V1_21_7;
    private static final int V773 = ProtocolVersion.V1_21_9;

    private static void assertId(int expected, PacketName name, int protocol) {
        assertEquals(OptionalInt.of(expected), PacketIds.id(name, protocol),
                name + " for protocol " + protocol);
    }

    private static void assertAbsent(PacketName name, int protocol) {
        assertEquals(OptionalInt.empty(), PacketIds.id(name, protocol),
                name + " は " + protocol + " に存在しないこと");
    }

    @Test
    void tableKnows773() {
        assertTrue(PacketIds.isSupported(V773));
        assertEquals(List.of(755, 756, 757, 759, 760, 761, 762, 763, 764, 765, 766, 767, 768, 769, 770, 771, 772, 773, 774, 775),
                PacketIds.supportedProtocols());
    }

    @Test
    void changedIdsMatchMapping() {
        // packet-ids-773.py：247エントリ中108件が772とIDが違い（すべて
        // プレイ・クライアント行き。id773 が -1 の削除はこの108件に
        // 含まれない）。削除は新規に1件（debug_sample_subscription。
        // ほかの8件は772以前から -1）。さらに新規エントリ8件
        // （-1→値あり）が増えるため、変わるのは108＋8＋1＝117件。
        // 新規・削除のエントリ以外はすべてプレイ・クライアント行きで
        // あることを確認する。
        int changed = 0;
        for (PacketName name : PacketName.values()) {
            if (name.id772() != name.id773()) {
                changed++;
                boolean isRemovedIn773 = name == PacketName.PLAY_DEBUG_SAMPLE_SUBSCRIPTION;
                boolean isNew = isRemovedIn773 || name == PacketName.CONFIG_CODE_OF_CONDUCT
                        || name == PacketName.CONFIG_ACCEPT_CODE_OF_CONDUCT
                        || name == PacketName.PLAY_DEBUG_BLOCK_VALUE
                        || name == PacketName.PLAY_DEBUG_CHUNK_VALUE
                        || name == PacketName.PLAY_DEBUG_ENTITY_VALUE
                        || name == PacketName.PLAY_DEBUG_EVENT
                        || name == PacketName.PLAY_GAME_TEST_HIGHLIGHT_POS
                        || name == PacketName.PLAY_DEBUG_SUBSCRIPTION_REQUEST;
                if (!isNew) {
                    assertEquals(State.PLAY, name.state(),
                            name + " はプレイ状態のはず");
                    assertEquals(Direction.CLIENTBOUND, name.direction(),
                            name + " はクライアント行きのはず");
                }
            }
        }
        assertEquals(117, changed);
        assertEquals(-1, PacketName.PLAY_DEBUG_SAMPLE_SUBSCRIPTION.id773());
        assertEquals(0x16, PacketName.PLAY_DEBUG_SAMPLE_SUBSCRIPTION.id772());
    }

    @Test
    void handshakeAndLogin() {
        assertId(0x00, PacketName.HANDSHAKE, V773);
        assertId(0x00, PacketName.LOGIN_DISCONNECT, V773);
        assertId(0x01, PacketName.LOGIN_ENCRYPTION_REQUEST, V773);
        // Login Success のIDは変わらない（0x02）。
        assertId(0x02, PacketName.LOGIN_SUCCESS, V773);
        assertId(0x03, PacketName.LOGIN_SET_COMPRESSION, V773);
        assertId(0x04, PacketName.LOGIN_PLUGIN_REQUEST, V773);
        assertId(0x05, PacketName.LOGIN_COOKIE_REQUEST, V773);
        assertId(0x04, PacketName.LOGIN_COOKIE_RESPONSE, V773);
    }

    @Test
    void configurationIdsWithNewCodeOfConduct() {
        // 設定フェーズの既存のIDは772と同じ並び。新規は code_of_conduct
        // （クライアント行き 0x13）と accept_code_of_conduct（サーバー行き 0x09）。
        assertId(0x00, PacketName.CONFIG_COOKIE_REQUEST, V773);
        assertId(0x01, PacketName.CONFIG_PLUGIN_MESSAGE, V773);
        assertId(0x02, PacketName.CONFIG_DISCONNECT, V773);
        assertId(0x03, PacketName.CONFIG_FINISH_CONFIGURATION, V773);
        assertId(0x04, PacketName.CONFIG_KEEP_ALIVE, V773);
        assertId(0x05, PacketName.CONFIG_PING, V773);
        assertId(0x06, PacketName.CONFIG_RESET_CHAT, V773);
        assertId(0x07, PacketName.CONFIG_REGISTRY_DATA, V773);
        assertId(0x08, PacketName.CONFIG_REMOVE_RESOURCE_PACK, V773);
        assertId(0x09, PacketName.CONFIG_ADD_RESOURCE_PACK, V773);
        assertId(0x0a, PacketName.CONFIG_STORE_COOKIE, V773);
        assertId(0x0b, PacketName.CONFIG_TRANSFER, V773);
        assertId(0x0c, PacketName.CONFIG_FEATURE_FLAGS, V773);
        assertId(0x0d, PacketName.CONFIG_TAGS, V773);
        assertId(0x0e, PacketName.CONFIG_SELECT_KNOWN_PACKS, V773);
        assertId(0x0f, PacketName.CONFIG_CUSTOM_REPORT_DETAILS, V773);
        assertId(0x10, PacketName.CONFIG_SERVER_LINKS, V773);
        // 771で新規のダイアログ関連も同じID。
        assertId(0x11, PacketName.CONFIG_CLEAR_DIALOG, V773);
        assertId(0x12, PacketName.CONFIG_SHOW_DIALOG, V773);
        // 773で新規の行動規範。
        assertId(0x13, PacketName.CONFIG_CODE_OF_CONDUCT, V773);
        assertId(0x09, PacketName.CONFIG_ACCEPT_CODE_OF_CONDUCT, V773);
        assertAbsent(PacketName.CONFIG_CODE_OF_CONDUCT, V772);
        assertAbsent(PacketName.CONFIG_ACCEPT_CODE_OF_CONDUCT, V772);
        assertId(0x08, PacketName.CONFIG_CUSTOM_CLICK_ACTION_TO_SERVER, V773);
        // 771で削除の2件は773でも無い。
        assertAbsent(PacketName.CONFIG_CUSTOM_REPORT_DETAILS_TO_SERVER, V773);
        assertAbsent(PacketName.CONFIG_SERVER_LINKS_TO_SERVER, V773);
        // 件数：toClient 20、toServer 10（772の19／9に新規1件ずつ）。
        assertEquals(20, PacketIds.names(State.CONFIGURATION, Direction.CLIENTBOUND, V773).size());
        assertEquals(10, PacketIds.names(State.CONFIGURATION, Direction.SERVERBOUND, V773).size());
    }

    @Test
    void playIdsShiftedByNewDebugPackets() {
        // 新規デバッグ系4件（0x1A〜0x1D）の挿入で 0x1A 以降が +4、さらに
        // game_test_highlight_pos（0x27）の挿入で 0x27 以降がもう +1。
        assertId(0x30, PacketName.PLAY_JOIN_GAME, V773);
        assertId(0x2b, PacketName.PLAY_JOIN_GAME, V772);
        assertId(0x5f, PacketName.PLAY_SPAWN_POSITION, V773);
        assertId(0x2b, PacketName.PLAY_KEEP_ALIVE, V773);
        assertId(0x3f, PacketName.PLAY_PLAYER_CHAT, V773);
        assertId(0x2c, PacketName.PLAY_CHUNK_DATA, V773);
        assertId(0x88, PacketName.PLAY_TRACKED_WAYPOINT, V773);
        assertId(0x89, PacketName.PLAY_CLEAR_DIALOG, V773);
        assertId(0x8a, PacketName.PLAY_SHOW_DIALOG, V773);
        // 新規のデバッグ系パケット。
        assertId(0x1a, PacketName.PLAY_DEBUG_BLOCK_VALUE, V773);
        assertId(0x1b, PacketName.PLAY_DEBUG_CHUNK_VALUE, V773);
        assertId(0x1c, PacketName.PLAY_DEBUG_ENTITY_VALUE, V773);
        assertId(0x1d, PacketName.PLAY_DEBUG_EVENT, V773);
        assertId(0x27, PacketName.PLAY_GAME_TEST_HIGHLIGHT_POS, V773);
        assertAbsent(PacketName.PLAY_DEBUG_BLOCK_VALUE, V772);
        assertAbsent(PacketName.PLAY_GAME_TEST_HIGHLIGHT_POS, V772);
        // 773で削除の debug_sample_subscription と、後継の
        // debug_subscription_request（同ID 0x16）。
        assertAbsent(PacketName.PLAY_DEBUG_SAMPLE_SUBSCRIPTION, V773);
        assertId(0x16, PacketName.PLAY_DEBUG_SUBSCRIPTION_REQUEST, V773);
        assertAbsent(PacketName.PLAY_DEBUG_SUBSCRIPTION_REQUEST, V772);
        // サーバー行きのほかのIDは772と同じ（change_gamemode 0x04、custom_click_action 0x41）。
        assertId(0x04, PacketName.PLAY_CHANGE_GAMEMODE, V773);
        assertId(0x41, PacketName.PLAY_CUSTOM_CLICK_ACTION_TO_SERVER, V773);
        assertId(0x08, PacketName.PLAY_CHAT_MESSAGE, V773);
        assertId(0x08, PacketName.PLAY_CHAT_MESSAGE, V772);
        assertId(0x11, PacketName.PLAY_WINDOW_CLICK, V773);
        assertId(0x37, PacketName.PLAY_SET_CREATIVE_SLOT, V773);
        assertId(0x1d, PacketName.PLAY_SET_PLAYER_POSITION, V773);
        assertId(0x1d, PacketName.PLAY_SET_PLAYER_POSITION, V772);
        // 件数：toClient 139、toServer 65（772の134／65に新規5件と
        // 削除・新規の差し替え1件）。
        assertEquals(139, PacketIds.names(State.PLAY, Direction.CLIENTBOUND, V773).size());
        assertEquals(65, PacketIds.names(State.PLAY, Direction.SERVERBOUND, V773).size());
        // 771で削除のものは773でも無い。
        assertAbsent(PacketName.PLAY_SPAWN_EXPERIENCE_ORB, V773);
        assertAbsent(PacketName.PLAY_PICK_ITEM, V773);
        assertAbsent(PacketName.PLAY_UNLOCK_RECIPES, V773);
        assertAbsent(PacketName.PLAY_STEER_VEHICLE, V773);
    }

    @Test
    void reverseLookup773() {
        assertEquals(PacketName.PLAY_JOIN_GAME,
                PacketIds.nameFor(0x30, State.PLAY, Direction.CLIENTBOUND, V773).orElseThrow());
        assertEquals(PacketName.PLAY_TRACKED_WAYPOINT,
                PacketIds.nameFor(0x88, State.PLAY, Direction.CLIENTBOUND, V773).orElseThrow());
        assertEquals(PacketName.PLAY_SHOW_DIALOG,
                PacketIds.nameFor(0x8a, State.PLAY, Direction.CLIENTBOUND, V773).orElseThrow());
        assertEquals(PacketName.PLAY_DEBUG_BLOCK_VALUE,
                PacketIds.nameFor(0x1a, State.PLAY, Direction.CLIENTBOUND, V773).orElseThrow());
        assertEquals(PacketName.PLAY_DEBUG_SUBSCRIPTION_REQUEST,
                PacketIds.nameFor(0x16, State.PLAY, Direction.SERVERBOUND, V773).orElseThrow());
        assertEquals(PacketName.PLAY_CHANGE_GAMEMODE,
                PacketIds.nameFor(0x04, State.PLAY, Direction.SERVERBOUND, V773).orElseThrow());
        assertEquals(PacketName.PLAY_CUSTOM_CLICK_ACTION_TO_SERVER,
                PacketIds.nameFor(0x41, State.PLAY, Direction.SERVERBOUND, V773).orElseThrow());
        assertEquals(PacketName.CONFIG_CODE_OF_CONDUCT,
                PacketIds.nameFor(0x13, State.CONFIGURATION, Direction.CLIENTBOUND, V773)
                        .orElseThrow());
        assertEquals(PacketName.CONFIG_SERVER_LINKS,
                PacketIds.nameFor(0x10, State.CONFIGURATION, Direction.CLIENTBOUND, V773)
                        .orElseThrow());
        assertEquals(PacketName.CONFIG_SHOW_DIALOG,
                PacketIds.nameFor(0x12, State.CONFIGURATION, Direction.CLIENTBOUND, V773)
                        .orElseThrow());
    }

    @Test
    void legacyPingAndPingRequestNotInTable() {
        // 従来通り、legacy_server_list_ping（0xFE）と play の ping_request
        // （773でも 0x25）は表に入れない（プロキシが落とす方針のため）。
        assertFalse(PacketIds.names(State.PLAY, Direction.SERVERBOUND, V773).stream()
                .anyMatch(n -> n.name().contains("PING_REQUEST")));
        assertFalse(PacketIds.names(State.HANDSHAKING, Direction.SERVERBOUND, V773).stream()
                .anyMatch(n -> n.name().contains("LEGACY")));
    }
}
