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
 * {@link PacketIds} の770（1.21.5）向けスポットチェック。
 * 期待値は PrismarineJS minecraft-data の protocol.json（1.21.5）と
 * tools/packet-ids-770.py の出力による。
 * 769と比べ、クライアント行きのIDは117件が1つずつ下がり（spawn_entity_experience_orb
 * 0x02 の削除による。削除1件・新規1件で件数は変わらない）、サーバー行きは5件のIDが変わり、
 * 新規は2件（set_test_block・test_instance_block_action）。
 * 設定フェーズ・ログインのIDは変わらない。
 */
class PacketIds770Test {

    private static final int V763 = ProtocolVersion.V1_20_1;
    private static final int V764 = ProtocolVersion.V1_20_2;
    private static final int V765 = ProtocolVersion.V1_20_4;
    private static final int V766 = ProtocolVersion.V1_20_5;
    private static final int V767 = ProtocolVersion.V1_21;
    private static final int V768 = ProtocolVersion.V1_21_2;
    private static final int V769 = ProtocolVersion.V1_21_4;
    private static final int V770 = ProtocolVersion.V1_21_5;

    private static void assertId(int expected, PacketName name, int protocol) {
        assertEquals(OptionalInt.of(expected), PacketIds.id(name, protocol),
                name + " for protocol " + protocol);
    }

    private static void assertAbsent(PacketName name, int protocol) {
        assertEquals(OptionalInt.empty(), PacketIds.id(name, protocol),
                name + " は " + protocol + " に存在しないこと");
    }

    @Test
    void tableKnows770() {
        assertTrue(PacketIds.isSupported(V770));
        assertEquals(List.of(759, 760, 761, 762, 763, 764, 765, 766, 767, 768, 769, 770, 771, 772, 773, 774, 775), PacketIds.supportedProtocols());
    }

    @Test
    void handshakeAndLogin() {
        assertId(0x00, PacketName.HANDSHAKE, V770);
        assertId(0x00, PacketName.LOGIN_DISCONNECT, V770);
        assertId(0x01, PacketName.LOGIN_ENCRYPTION_REQUEST, V770);
        // Login Success のIDは変わらない（0x02）。ペイロードの
        // Strict Error Handling の bool は768・769・770とも無い（767にはある）。
        assertId(0x02, PacketName.LOGIN_SUCCESS, V770);
        assertId(0x03, PacketName.LOGIN_SET_COMPRESSION, V770);
        assertId(0x04, PacketName.LOGIN_PLUGIN_REQUEST, V770);
        assertId(0x05, PacketName.LOGIN_COOKIE_REQUEST, V770);
        assertId(0x04, PacketName.LOGIN_COOKIE_RESPONSE, V770);
    }

    @Test
    void configurationIdsUnchangedFrom769() {
        // 設定フェーズのIDは769と同じ並び。
        assertId(0x00, PacketName.CONFIG_COOKIE_REQUEST, V770);
        assertId(0x01, PacketName.CONFIG_PLUGIN_MESSAGE, V770);
        assertId(0x02, PacketName.CONFIG_DISCONNECT, V770);
        assertId(0x03, PacketName.CONFIG_FINISH_CONFIGURATION, V770);
        assertId(0x04, PacketName.CONFIG_KEEP_ALIVE, V770);
        assertId(0x05, PacketName.CONFIG_PING, V770);
        assertId(0x06, PacketName.CONFIG_RESET_CHAT, V770);
        assertId(0x07, PacketName.CONFIG_REGISTRY_DATA, V770);
        assertId(0x08, PacketName.CONFIG_REMOVE_RESOURCE_PACK, V770);
        assertId(0x09, PacketName.CONFIG_ADD_RESOURCE_PACK, V770);
        assertId(0x0a, PacketName.CONFIG_STORE_COOKIE, V770);
        assertId(0x0b, PacketName.CONFIG_TRANSFER, V770);
        assertId(0x0c, PacketName.CONFIG_FEATURE_FLAGS, V770);
        assertId(0x0d, PacketName.CONFIG_TAGS, V770);
        assertId(0x0e, PacketName.CONFIG_SELECT_KNOWN_PACKS, V770);
        assertId(0x0f, PacketName.CONFIG_CUSTOM_REPORT_DETAILS, V770);
        assertId(0x10, PacketName.CONFIG_SERVER_LINKS, V770);
        assertId(0x08, PacketName.CONFIG_CUSTOM_REPORT_DETAILS_TO_SERVER, V770);
        assertId(0x09, PacketName.CONFIG_SERVER_LINKS_TO_SERVER, V770);
        // 件数：toClient 17、toServer 10（769と同じ）。
        assertEquals(17, PacketIds.names(State.CONFIGURATION, Direction.CLIENTBOUND, V770).size());
        assertEquals(10, PacketIds.names(State.CONFIGURATION, Direction.SERVERBOUND, V770).size());
    }

    @Test
    void playClientboundIdsShiftedDownByOneFrom769() {
        // 770でクライアント行きのIDは、spawn_entity_experience_orb（0x02）の
        // 削除により、それより後ろのものが1つずつ下がる（117件）。
        assertId(0x2b, PacketName.PLAY_JOIN_GAME, V770);
        assertId(0x2c, PacketName.PLAY_JOIN_GAME, V769);
        assertId(0x5a, PacketName.PLAY_SPAWN_POSITION, V770);
        assertId(0x5b, PacketName.PLAY_SPAWN_POSITION, V769);
        assertId(0x26, PacketName.PLAY_KEEP_ALIVE, V770);
        assertId(0x27, PacketName.PLAY_KEEP_ALIVE, V769);
        assertId(0x3a, PacketName.PLAY_PLAYER_CHAT, V770);
        assertId(0x3b, PacketName.PLAY_PLAYER_CHAT, V769);
        assertId(0x27, PacketName.PLAY_CHUNK_DATA, V770);
        assertId(0x28, PacketName.PLAY_CHUNK_DATA, V769);
        assertId(0x1f, PacketName.PLAY_SYNC_ENTITY_POSITION, V770);
        assertId(0x20, PacketName.PLAY_SYNC_ENTITY_POSITION, V769);
        assertId(0x43, PacketName.PLAY_RECIPE_BOOK_ADD, V770);
        assertId(0x44, PacketName.PLAY_RECIPE_BOOK_ADD, V769);
        assertId(0x59, PacketName.PLAY_SET_CURSOR_ITEM, V770);
        assertId(0x5a, PacketName.PLAY_SET_CURSOR_ITEM, V769);
        assertId(0x65, PacketName.PLAY_SET_PLAYER_INVENTORY, V770);
        assertId(0x66, PacketName.PLAY_SET_PLAYER_INVENTORY, V769);
        // 770で新規（1件：test_instance_block_status 0x77）。
        assertId(0x77, PacketName.PLAY_TEST_INSTANCE_BLOCK_STATUS, V770);
        assertAbsent(PacketName.PLAY_TEST_INSTANCE_BLOCK_STATUS, V769);
        // 770で削除（spawn_entity_experience_orb。769では 0x02。
        // XPオーブは通常の Spawn Entity（type 47）で送られる）。
        assertAbsent(PacketName.PLAY_SPAWN_EXPERIENCE_ORB, V770);
        assertId(0x02, PacketName.PLAY_SPAWN_EXPERIENCE_ORB, V769);
        // 件数：toClient 131（769と同じ。新規＋1、削除−1）。
        assertEquals(131, PacketIds.names(State.PLAY, Direction.CLIENTBOUND, V770).size());
    }

    @Test
    void playServerboundIdsChangedFrom769() {
        // 769と ID が変わったもの（5件。すべてサーバー行き）。
        assertId(0x3a, PacketName.PLAY_UPDATE_SIGN, V770);
        assertId(0x39, PacketName.PLAY_UPDATE_SIGN, V769);
        assertId(0x3b, PacketName.PLAY_ARM_ANIMATION, V770);
        assertId(0x3a, PacketName.PLAY_ARM_ANIMATION, V769);
        assertId(0x3d, PacketName.PLAY_SPECTATE, V770);
        assertId(0x3b, PacketName.PLAY_SPECTATE, V769);
        assertId(0x3e, PacketName.PLAY_BLOCK_PLACE, V770);
        assertId(0x3c, PacketName.PLAY_BLOCK_PLACE, V769);
        assertId(0x3f, PacketName.PLAY_USE_ITEM, V770);
        assertId(0x3d, PacketName.PLAY_USE_ITEM, V769);
        // 770で新規（2件）。
        assertId(0x39, PacketName.PLAY_SET_TEST_BLOCK, V770);
        assertId(0x3c, PacketName.PLAY_TEST_INSTANCE_BLOCK_ACTION, V770);
        assertAbsent(PacketName.PLAY_SET_TEST_BLOCK, V769);
        assertAbsent(PacketName.PLAY_TEST_INSTANCE_BLOCK_ACTION, V769);
        // 変わらないもの（スポットチェック）。
        assertId(0x36, PacketName.PLAY_SET_CREATIVE_SLOT, V770);
        assertId(0x36, PacketName.PLAY_SET_CREATIVE_SLOT, V769);
        assertId(0x10, PacketName.PLAY_WINDOW_CLICK, V770);
        assertId(0x10, PacketName.PLAY_WINDOW_CLICK, V769);
        assertId(0x29, PacketName.PLAY_PLAYER_INPUT, V770);
        assertId(0x29, PacketName.PLAY_PLAYER_INPUT, V769);
        assertId(0x2f, PacketName.PLAY_RESOURCE_PACK_RESPONSE, V770);
        assertId(0x2f, PacketName.PLAY_RESOURCE_PACK_RESPONSE, V769);
        assertId(0x07, PacketName.PLAY_CHAT_MESSAGE, V770);
        assertId(0x07, PacketName.PLAY_CHAT_MESSAGE, V769);
        // ping_request（770では 0x24）は表に入れない（プロキシが落とす方針）。
        // 件数：toServer 63（769の61から新規＋2）。
        assertEquals(63, PacketIds.names(State.PLAY, Direction.SERVERBOUND, V770).size());
    }

    @Test
    void reverseLookup770() {
        assertEquals(PacketName.PLAY_JOIN_GAME,
                PacketIds.nameFor(0x2b, State.PLAY, Direction.CLIENTBOUND, V770).orElseThrow());
        assertEquals(PacketName.PLAY_TEST_INSTANCE_BLOCK_STATUS,
                PacketIds.nameFor(0x77, State.PLAY, Direction.CLIENTBOUND, V770).orElseThrow());
        assertEquals(PacketName.PLAY_SET_TEST_BLOCK,
                PacketIds.nameFor(0x39, State.PLAY, Direction.SERVERBOUND, V770).orElseThrow());
        assertEquals(PacketName.PLAY_TEST_INSTANCE_BLOCK_ACTION,
                PacketIds.nameFor(0x3c, State.PLAY, Direction.SERVERBOUND, V770).orElseThrow());
        assertEquals(PacketName.PLAY_USE_ITEM,
                PacketIds.nameFor(0x3f, State.PLAY, Direction.SERVERBOUND, V770).orElseThrow());
        assertEquals(PacketName.CONFIG_SERVER_LINKS,
                PacketIds.nameFor(0x10, State.CONFIGURATION, Direction.CLIENTBOUND, V770)
                        .orElseThrow());
    }

    @Test
    void legacyPingAndPingRequestNotInTable() {
        // 従来通り、legacy_server_list_ping（0xFE）と play の ping_request
        // （770では 0x24）は表に入れない（プロキシが落とす方針のため）。
        assertFalse(PacketIds.names(State.PLAY, Direction.SERVERBOUND, V770).stream()
                .anyMatch(n -> n.name().contains("PING_REQUEST")));
        assertFalse(PacketIds.names(State.HANDSHAKING, Direction.SERVERBOUND, V770).stream()
                .anyMatch(n -> n.name().contains("LEGACY")));
    }
}
