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
 * {@link PacketIds} の776（26.2）向けスポットチェック。
 * 期待値は本物のバニラ 26.2 の jar から抽出したパケット順序で作った
 * mc-26.2-protocol.json と tools/packet-ids-776.py の出力による。
 * 775と比べ、パケットIDが違うものは0件。削除は1件（改名のため
 * PLAY_SPECTATE_ENTITY が -1）。新規パケットは1件（PLAY_SPECTATOR_ACTION）。
 */
class PacketIds776Test {

    private static final int V775 = ProtocolVersion.V26_1;
    private static final int V776 = ProtocolVersion.V26_2;

    private static void assertId(int expected, PacketName name, int protocol) {
        assertEquals(OptionalInt.of(expected), PacketIds.id(name, protocol),
                name + " for protocol " + protocol);
    }

    private static void assertAbsent(PacketName name, int protocol) {
        assertEquals(OptionalInt.empty(), PacketIds.id(name, protocol),
                name + " は " + protocol + " に存在しないこと");
    }

    @Test
    void tableKnows776() {
        assertTrue(PacketIds.isSupported(V776));
        assertEquals(List.of(335, 338, 340, 393, 401, 404, 477, 480, 485, 490, 498, 573, 575, 578, 758, 759, 760, 761, 762, 763, 764, 765, 766, 767, 768, 769, 770, 771, 772, 773, 774, 775, 776),
                PacketIds.supportedProtocols());
    }

    @Test
    void idsChangedFrom775() {
        // packet-ids-776.py：267エントリすべてが一意に写像でき、775とIDが
        // 違うものは0件、776で削除（-1）は1件、776で新規は1件。
        // 削除と新規は同じ位置（play toServer 0x3e）の改名
        // （spectate_entity → spectator_action）。
        int changed = 0;
        int removed = 0;
        int added = 0;
        for (PacketName name : PacketName.values()) {
            int id775 = name.id775();
            int id776 = name.id776();
            if (id775 >= 0 && id776 >= 0 && id775 != id776) {
                changed++;
            }
            if (id775 >= 0 && id776 < 0) {
                removed++;
            }
            if (id775 < 0 && id776 >= 0) {
                added++;
            }
        }
        assertEquals(0, changed, "775とIDが違う件数");
        assertEquals(1, removed, "776で削除された件数");
        assertEquals(1, added, "776で新規の件数");
        // 改名：775 の spectate_entity（0x3e）は 776 では無い。
        assertAbsent(PacketName.PLAY_SPECTATE_ENTITY, V776);
        assertId(0x3e, PacketName.PLAY_SPECTATE_ENTITY, V775);
        // 改名：776 の spectator_action（0x3e）は 775 では無い。
        assertId(0x3e, PacketName.PLAY_SPECTATOR_ACTION, V776);
        assertAbsent(PacketName.PLAY_SPECTATOR_ACTION, V775);
        // 775 の新規5件は 776 でも同じID。
        assertId(0x27, PacketName.PLAY_GAME_RULE_VALUES, V776);
        assertId(0x32, PacketName.PLAY_LOW_DISK_SPACE_WARNING, V776);
        assertId(0x01, PacketName.PLAY_ATTACK, V776);
        assertId(0x39, PacketName.PLAY_SET_GAME_RULE, V776);
    }

    @Test
    void handshakeAndLogin() {
        // ハンドシェイク・ログイン・設定フェーズのIDは775と同じ。
        assertId(0x00, PacketName.HANDSHAKE, V776);
        assertId(0x00, PacketName.LOGIN_DISCONNECT, V776);
        assertId(0x01, PacketName.LOGIN_ENCRYPTION_REQUEST, V776);
        // Login Success のIDは変わらない（0x02）。
        assertId(0x02, PacketName.LOGIN_SUCCESS, V776);
        assertId(0x03, PacketName.LOGIN_SET_COMPRESSION, V776);
        assertId(0x04, PacketName.LOGIN_PLUGIN_REQUEST, V776);
        assertId(0x05, PacketName.LOGIN_COOKIE_REQUEST, V776);
        assertId(0x04, PacketName.LOGIN_COOKIE_RESPONSE, V776);
    }

    @Test
    void configurationIdsAreSameAs775() {
        // 設定フェーズのIDは775と同じ並び。code_of_conduct
        // （クライアント行き 0x13）と accept_code_of_conduct（サーバー行き 0x09）は776でも同じ。
        assertId(0x00, PacketName.CONFIG_COOKIE_REQUEST, V776);
        assertId(0x01, PacketName.CONFIG_PLUGIN_MESSAGE, V776);
        assertId(0x02, PacketName.CONFIG_DISCONNECT, V776);
        assertId(0x03, PacketName.CONFIG_FINISH_CONFIGURATION, V776);
        assertId(0x04, PacketName.CONFIG_KEEP_ALIVE, V776);
        assertId(0x05, PacketName.CONFIG_PING, V776);
        assertId(0x06, PacketName.CONFIG_RESET_CHAT, V776);
        assertId(0x07, PacketName.CONFIG_REGISTRY_DATA, V776);
        assertId(0x08, PacketName.CONFIG_REMOVE_RESOURCE_PACK, V776);
        assertId(0x09, PacketName.CONFIG_ADD_RESOURCE_PACK, V776);
        assertId(0x0a, PacketName.CONFIG_STORE_COOKIE, V776);
        assertId(0x0b, PacketName.CONFIG_TRANSFER, V776);
        assertId(0x0c, PacketName.CONFIG_FEATURE_FLAGS, V776);
        assertId(0x0d, PacketName.CONFIG_TAGS, V776);
        assertId(0x0e, PacketName.CONFIG_SELECT_KNOWN_PACKS, V776);
        assertId(0x0f, PacketName.CONFIG_CUSTOM_REPORT_DETAILS, V776);
        assertId(0x10, PacketName.CONFIG_SERVER_LINKS, V776);
        assertId(0x11, PacketName.CONFIG_CLEAR_DIALOG, V776);
        assertId(0x12, PacketName.CONFIG_SHOW_DIALOG, V776);
        assertId(0x13, PacketName.CONFIG_CODE_OF_CONDUCT, V776);
        assertId(0x09, PacketName.CONFIG_ACCEPT_CODE_OF_CONDUCT, V776);
        assertId(0x08, PacketName.CONFIG_CUSTOM_CLICK_ACTION_TO_SERVER, V776);
        // 771で削除の2件は776でも無い。
        assertAbsent(PacketName.CONFIG_CUSTOM_REPORT_DETAILS_TO_SERVER, V776);
        assertAbsent(PacketName.CONFIG_SERVER_LINKS_TO_SERVER, V776);
        // 件数：toClient 20、toServer 10（775と同じ）。
        assertEquals(20, PacketIds.names(State.CONFIGURATION, Direction.CLIENTBOUND, V776).size());
        assertEquals(10, PacketIds.names(State.CONFIGURATION, Direction.SERVERBOUND, V776).size());
    }

    @Test
    void playIds() {
        // 776のパケットIDは775とすべて同じ（0件変更）。代表例。
        assertId(0x31, PacketName.PLAY_JOIN_GAME, V776);
        assertId(0x61, PacketName.PLAY_SPAWN_POSITION, V776);
        assertId(0x2c, PacketName.PLAY_KEEP_ALIVE, V776);
        assertId(0x41, PacketName.PLAY_PLAYER_CHAT, V776);
        assertId(0x2d, PacketName.PLAY_CHUNK_DATA, V776);
        assertId(0x79, PacketName.PLAY_SYSTEM_CHAT, V776);
        assertId(0x46, PacketName.PLAY_PLAYER_INFO_UPDATE, V776);
        assertId(0x45, PacketName.PLAY_PLAYER_INFO_REMOVE, V776);
        assertId(0x1a, PacketName.PLAY_DEBUG_BLOCK_VALUE, V776);
        assertId(0x1b, PacketName.PLAY_DEBUG_CHUNK_VALUE, V776);
        assertId(0x1c, PacketName.PLAY_DEBUG_ENTITY_VALUE, V776);
        assertId(0x1d, PacketName.PLAY_DEBUG_EVENT, V776);
        assertId(0x05, PacketName.PLAY_CHANGE_GAMEMODE, V776);
        assertId(0x44, PacketName.PLAY_CUSTOM_CLICK_ACTION_TO_SERVER, V776);
        assertId(0x09, PacketName.PLAY_CHAT_MESSAGE, V776);
        assertId(0x12, PacketName.PLAY_WINDOW_CLICK, V776);
        assertId(0x38, PacketName.PLAY_SET_CREATIVE_SLOT, V776);
        assertId(0x1e, PacketName.PLAY_SET_PLAYER_POSITION, V776);
        // 件数：toClient 141、toServer 68（775と同じ。toServer は
        // spectate_entity が spectator_action に置き換わっただけ）。
        assertEquals(141, PacketIds.names(State.PLAY, Direction.CLIENTBOUND, V776).size());
        assertEquals(68, PacketIds.names(State.PLAY, Direction.SERVERBOUND, V776).size());
        // 771で削除のものは776でも無い。
        assertAbsent(PacketName.PLAY_SPAWN_EXPERIENCE_ORB, V776);
        assertAbsent(PacketName.PLAY_PICK_ITEM, V776);
        assertAbsent(PacketName.PLAY_UNLOCK_RECIPES, V776);
        assertAbsent(PacketName.PLAY_STEER_VEHICLE, V776);
    }

    @Test
    void reverseLookup776() {
        assertEquals(PacketName.PLAY_JOIN_GAME,
                PacketIds.nameFor(0x31, State.PLAY, Direction.CLIENTBOUND, V776).orElseThrow());
        assertEquals(PacketName.PLAY_GAME_RULE_VALUES,
                PacketIds.nameFor(0x27, State.PLAY, Direction.CLIENTBOUND, V776).orElseThrow());
        assertEquals(PacketName.PLAY_LOW_DISK_SPACE_WARNING,
                PacketIds.nameFor(0x32, State.PLAY, Direction.CLIENTBOUND, V776).orElseThrow());
        assertEquals(PacketName.PLAY_ATTACK,
                PacketIds.nameFor(0x01, State.PLAY, Direction.SERVERBOUND, V776).orElseThrow());
        assertEquals(PacketName.PLAY_SET_GAME_RULE,
                PacketIds.nameFor(0x39, State.PLAY, Direction.SERVERBOUND, V776).orElseThrow());
        assertEquals(PacketName.PLAY_SPECTATOR_ACTION,
                PacketIds.nameFor(0x3e, State.PLAY, Direction.SERVERBOUND, V776).orElseThrow());
        assertEquals(PacketName.PLAY_CUSTOM_CLICK_ACTION_TO_SERVER,
                PacketIds.nameFor(0x44, State.PLAY, Direction.SERVERBOUND, V776).orElseThrow());
        assertEquals(PacketName.CONFIG_CODE_OF_CONDUCT,
                PacketIds.nameFor(0x13, State.CONFIGURATION, Direction.CLIENTBOUND, V776)
                        .orElseThrow());
    }

    @Test
    void legacyPingAndPingRequestNotInTable() {
        // 従来通り、legacy_server_list_ping（0xFE）と play の ping_request
        // （776では 0x26）は表に入れない（プロキシが落とす方針のため）。
        assertFalse(PacketIds.names(State.PLAY, Direction.SERVERBOUND, V776).stream()
                .anyMatch(n -> n.name().contains("PING_REQUEST")));
        assertFalse(PacketIds.names(State.HANDSHAKING, Direction.SERVERBOUND, V776).stream()
                .anyMatch(n -> n.name().contains("LEGACY")));
    }
}
