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
 * {@link PacketIds} の775（26.1）向けスポットチェック。
 * 期待値は PrismarineJS minecraft-data の protocol.json（26.1）と
 * tools/packet-ids-775.py の出力による。
 * 774と比べ、パケットIDが違うものは164件（クライアント行き100・サーバー行き64）。
 * 削除は0件。新規パケットは5件。
 */
class PacketIds775Test {

    private static final int V774 = ProtocolVersion.V1_21_11;
    private static final int V775 = ProtocolVersion.V26_1;

    private static void assertId(int expected, PacketName name, int protocol) {
        assertEquals(OptionalInt.of(expected), PacketIds.id(name, protocol),
                name + " for protocol " + protocol);
    }

    private static void assertAbsent(PacketName name, int protocol) {
        assertEquals(OptionalInt.empty(), PacketIds.id(name, protocol),
                name + " は " + protocol + " に存在しないこと");
    }

    @Test
    void tableKnows775() {
        assertTrue(PacketIds.isSupported(V775));
        assertEquals(List.of(4, 5, 47, 107, 108, 109, 110, 210, 315, 316, 335, 338, 340, 393, 401, 404, 477, 480, 485, 490, 498, 573, 575, 578, 735, 736, 751, 753, 754, 755, 756, 757, 758, 759, 760, 761, 762, 763, 764, 765, 766, 767, 768, 769, 770, 771, 772, 773, 774, 775, 776),
                PacketIds.supportedProtocols());
    }

    @Test
    void idsChangedFrom774() {
        // packet-ids-775.py：262エントリすべてが一意に写像でき、774とIDが
        // 違うものは164件、775で削除（-1）は0件、775で新規は5件。
        int changed = 0;
        int removed = 0;
        int added = 0;
        for (PacketName name : PacketName.values()) {
            int id774 = name.id774();
            int id775 = name.id775();
            if (id774 >= 0 && id775 >= 0 && id774 != id775) {
                changed++;
            }
            if (id774 >= 0 && id775 < 0) {
                removed++;
            }
            if (id774 < 0 && id775 >= 0) {
                added++;
            }
        }
        assertEquals(164, changed, "774とIDが違う件数");
        assertEquals(0, removed, "775で削除された件数");
        assertEquals(5, added, "775で新規の件数");
        // 新規5件のID。
        assertId(0x27, PacketName.PLAY_GAME_RULE_VALUES, V775);
        assertId(0x32, PacketName.PLAY_LOW_DISK_SPACE_WARNING, V775);
        assertId(0x01, PacketName.PLAY_ATTACK, V775);
        assertId(0x39, PacketName.PLAY_SET_GAME_RULE, V775);
        assertId(0x3e, PacketName.PLAY_SPECTATE_ENTITY, V775);
        // 新規5件は774に無い。
        assertAbsent(PacketName.PLAY_GAME_RULE_VALUES, V774);
        assertAbsent(PacketName.PLAY_LOW_DISK_SPACE_WARNING, V774);
        assertAbsent(PacketName.PLAY_ATTACK, V774);
        assertAbsent(PacketName.PLAY_SET_GAME_RULE, V774);
        assertAbsent(PacketName.PLAY_SPECTATE_ENTITY, V774);
    }

    @Test
    void handshakeAndLogin() {
        // ハンドシェイク・ログイン・設定フェーズのIDは774と同じ。
        assertId(0x00, PacketName.HANDSHAKE, V775);
        assertId(0x00, PacketName.LOGIN_DISCONNECT, V775);
        assertId(0x01, PacketName.LOGIN_ENCRYPTION_REQUEST, V775);
        // Login Success のIDは変わらない（0x02）。
        assertId(0x02, PacketName.LOGIN_SUCCESS, V775);
        assertId(0x03, PacketName.LOGIN_SET_COMPRESSION, V775);
        assertId(0x04, PacketName.LOGIN_PLUGIN_REQUEST, V775);
        assertId(0x05, PacketName.LOGIN_COOKIE_REQUEST, V775);
        assertId(0x04, PacketName.LOGIN_COOKIE_RESPONSE, V775);
    }

    @Test
    void configurationIdsAreSameAs774() {
        // 設定フェーズのIDは774と同じ並び。code_of_conduct
        // （クライアント行き 0x13）と accept_code_of_conduct（サーバー行き 0x09）は775でも同じ。
        assertId(0x00, PacketName.CONFIG_COOKIE_REQUEST, V775);
        assertId(0x01, PacketName.CONFIG_PLUGIN_MESSAGE, V775);
        assertId(0x02, PacketName.CONFIG_DISCONNECT, V775);
        assertId(0x03, PacketName.CONFIG_FINISH_CONFIGURATION, V775);
        assertId(0x04, PacketName.CONFIG_KEEP_ALIVE, V775);
        assertId(0x05, PacketName.CONFIG_PING, V775);
        assertId(0x06, PacketName.CONFIG_RESET_CHAT, V775);
        assertId(0x07, PacketName.CONFIG_REGISTRY_DATA, V775);
        assertId(0x08, PacketName.CONFIG_REMOVE_RESOURCE_PACK, V775);
        assertId(0x09, PacketName.CONFIG_ADD_RESOURCE_PACK, V775);
        assertId(0x0a, PacketName.CONFIG_STORE_COOKIE, V775);
        assertId(0x0b, PacketName.CONFIG_TRANSFER, V775);
        assertId(0x0c, PacketName.CONFIG_FEATURE_FLAGS, V775);
        assertId(0x0d, PacketName.CONFIG_TAGS, V775);
        assertId(0x0e, PacketName.CONFIG_SELECT_KNOWN_PACKS, V775);
        assertId(0x0f, PacketName.CONFIG_CUSTOM_REPORT_DETAILS, V775);
        assertId(0x10, PacketName.CONFIG_SERVER_LINKS, V775);
        assertId(0x11, PacketName.CONFIG_CLEAR_DIALOG, V775);
        assertId(0x12, PacketName.CONFIG_SHOW_DIALOG, V775);
        assertId(0x13, PacketName.CONFIG_CODE_OF_CONDUCT, V775);
        assertId(0x09, PacketName.CONFIG_ACCEPT_CODE_OF_CONDUCT, V775);
        assertId(0x08, PacketName.CONFIG_CUSTOM_CLICK_ACTION_TO_SERVER, V775);
        // 771で削除の2件は775でも無い。
        assertAbsent(PacketName.CONFIG_CUSTOM_REPORT_DETAILS_TO_SERVER, V775);
        assertAbsent(PacketName.CONFIG_SERVER_LINKS_TO_SERVER, V775);
        // 件数：toClient 20、toServer 10（774と同じ）。
        assertEquals(20, PacketIds.names(State.CONFIGURATION, Direction.CLIENTBOUND, V775).size());
        assertEquals(10, PacketIds.names(State.CONFIGURATION, Direction.SERVERBOUND, V775).size());
    }

    @Test
    void playIds() {
        // 775のずれの代表例（packet-ids-775.py の出力による）。
        // プレイの先頭に attack（0x01）が挿入され、0x27 に game_rule_values、
        // 0x32 に low_disk_space_warning が入ったため、それ以降がずれる。
        assertId(0x31, PacketName.PLAY_JOIN_GAME, V775);
        assertId(0x61, PacketName.PLAY_SPAWN_POSITION, V775);
        assertId(0x2c, PacketName.PLAY_KEEP_ALIVE, V775);
        assertId(0x41, PacketName.PLAY_PLAYER_CHAT, V775);
        assertId(0x2d, PacketName.PLAY_CHUNK_DATA, V775);
        assertId(0x79, PacketName.PLAY_SYSTEM_CHAT, V775);
        assertId(0x46, PacketName.PLAY_PLAYER_INFO_UPDATE, V775);
        assertId(0x45, PacketName.PLAY_PLAYER_INFO_REMOVE, V775);
        // デバッグ系パケット（773で新規）は775でも同じID（クライアント行きの
        // 新規は 0x27／0x32 で、デバッグ系より後ろのためずれない）。
        assertId(0x1a, PacketName.PLAY_DEBUG_BLOCK_VALUE, V775);
        assertId(0x1b, PacketName.PLAY_DEBUG_CHUNK_VALUE, V775);
        assertId(0x1c, PacketName.PLAY_DEBUG_ENTITY_VALUE, V775);
        assertId(0x1d, PacketName.PLAY_DEBUG_EVENT, V775);
        // サーバー行きの代表例（attack 0x01 の挿入で +1 ずれている）。
        assertId(0x05, PacketName.PLAY_CHANGE_GAMEMODE, V775);
        assertId(0x44, PacketName.PLAY_CUSTOM_CLICK_ACTION_TO_SERVER, V775);
        assertId(0x09, PacketName.PLAY_CHAT_MESSAGE, V775);
        assertId(0x12, PacketName.PLAY_WINDOW_CLICK, V775);
        assertId(0x38, PacketName.PLAY_SET_CREATIVE_SLOT, V775);
        assertId(0x1e, PacketName.PLAY_SET_PLAYER_POSITION, V775);
        // 件数：toClient 141（774の139＋新規2）、toServer 68（774の65＋新規3）。
        assertEquals(141, PacketIds.names(State.PLAY, Direction.CLIENTBOUND, V775).size());
        assertEquals(68, PacketIds.names(State.PLAY, Direction.SERVERBOUND, V775).size());
        // 771で削除のものは775でも無い。
        assertAbsent(PacketName.PLAY_SPAWN_EXPERIENCE_ORB, V775);
        assertAbsent(PacketName.PLAY_PICK_ITEM, V775);
        assertAbsent(PacketName.PLAY_UNLOCK_RECIPES, V775);
        assertAbsent(PacketName.PLAY_STEER_VEHICLE, V775);
    }

    @Test
    void reverseLookup775() {
        assertEquals(PacketName.PLAY_JOIN_GAME,
                PacketIds.nameFor(0x31, State.PLAY, Direction.CLIENTBOUND, V775).orElseThrow());
        assertEquals(PacketName.PLAY_GAME_RULE_VALUES,
                PacketIds.nameFor(0x27, State.PLAY, Direction.CLIENTBOUND, V775).orElseThrow());
        assertEquals(PacketName.PLAY_LOW_DISK_SPACE_WARNING,
                PacketIds.nameFor(0x32, State.PLAY, Direction.CLIENTBOUND, V775).orElseThrow());
        assertEquals(PacketName.PLAY_ATTACK,
                PacketIds.nameFor(0x01, State.PLAY, Direction.SERVERBOUND, V775).orElseThrow());
        assertEquals(PacketName.PLAY_SET_GAME_RULE,
                PacketIds.nameFor(0x39, State.PLAY, Direction.SERVERBOUND, V775).orElseThrow());
        assertEquals(PacketName.PLAY_SPECTATE_ENTITY,
                PacketIds.nameFor(0x3e, State.PLAY, Direction.SERVERBOUND, V775).orElseThrow());
        assertEquals(PacketName.PLAY_CUSTOM_CLICK_ACTION_TO_SERVER,
                PacketIds.nameFor(0x44, State.PLAY, Direction.SERVERBOUND, V775).orElseThrow());
        assertEquals(PacketName.CONFIG_CODE_OF_CONDUCT,
                PacketIds.nameFor(0x13, State.CONFIGURATION, Direction.CLIENTBOUND, V775)
                        .orElseThrow());
    }

    @Test
    void legacyPingAndPingRequestNotInTable() {
        // 従来通り、legacy_server_list_ping（0xFE）と play の ping_request
        // （775では 0x26）は表に入れない（プロキシが落とす方針のため）。
        assertFalse(PacketIds.names(State.PLAY, Direction.SERVERBOUND, V775).stream()
                .anyMatch(n -> n.name().contains("PING_REQUEST")));
        assertFalse(PacketIds.names(State.HANDSHAKING, Direction.SERVERBOUND, V775).stream()
                .anyMatch(n -> n.name().contains("LEGACY")));
    }
}
