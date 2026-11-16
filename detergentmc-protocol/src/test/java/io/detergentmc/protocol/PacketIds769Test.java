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
 * {@link PacketIds} の769（1.21.4）向けスポットチェック。
 * 期待値は PrismarineJS minecraft-data の protocol.json（1.21.4）と
 * tools/packet-ids-769.py の出力による。
 * 768と比べ、クライアント行きのIDは変わらず、サーバー行きは24件のIDが変わり、
 * 削除は1件（pick_item）、新規は3件（Pick Item From Block・
 * Pick Item From Entity・Player Loaded）である。設定フェーズ・ログインのIDは
 * 変わらない。
 */
class PacketIds769Test {

    private static final int V763 = ProtocolVersion.V1_20_1;
    private static final int V764 = ProtocolVersion.V1_20_2;
    private static final int V765 = ProtocolVersion.V1_20_4;
    private static final int V766 = ProtocolVersion.V1_20_5;
    private static final int V767 = ProtocolVersion.V1_21;
    private static final int V768 = ProtocolVersion.V1_21_2;
    private static final int V769 = ProtocolVersion.V1_21_4;

    private static void assertId(int expected, PacketName name, int protocol) {
        assertEquals(OptionalInt.of(expected), PacketIds.id(name, protocol),
                name + " for protocol " + protocol);
    }

    private static void assertAbsent(PacketName name, int protocol) {
        assertEquals(OptionalInt.empty(), PacketIds.id(name, protocol),
                name + " は " + protocol + " に存在しないこと");
    }

    @Test
    void tableKnows769() {
        assertTrue(PacketIds.isSupported(V769));
        assertEquals(List.of(753, 754, 755, 756, 757, 759, 760, 761, 762, 763, 764, 765, 766, 767, 768, 769, 770, 771, 772, 773, 774, 775), PacketIds.supportedProtocols());
    }

    @Test
    void handshakeAndLogin() {
        assertId(0x00, PacketName.HANDSHAKE, V769);
        assertId(0x00, PacketName.LOGIN_DISCONNECT, V769);
        assertId(0x01, PacketName.LOGIN_ENCRYPTION_REQUEST, V769);
        // Login Success のIDは変わらない（0x02）。ペイロードの
        // Strict Error Handling の bool は768・769とも無い（767にはある）。
        assertId(0x02, PacketName.LOGIN_SUCCESS, V769);
        assertId(0x03, PacketName.LOGIN_SET_COMPRESSION, V769);
        assertId(0x04, PacketName.LOGIN_PLUGIN_REQUEST, V769);
        assertId(0x05, PacketName.LOGIN_COOKIE_REQUEST, V769);
        assertId(0x04, PacketName.LOGIN_COOKIE_RESPONSE, V769);
    }

    @Test
    void configurationIdsUnchangedFrom768() {
        // 設定フェーズのIDは768と同じ並び。
        assertId(0x00, PacketName.CONFIG_COOKIE_REQUEST, V769);
        assertId(0x01, PacketName.CONFIG_PLUGIN_MESSAGE, V769);
        assertId(0x02, PacketName.CONFIG_DISCONNECT, V769);
        assertId(0x03, PacketName.CONFIG_FINISH_CONFIGURATION, V769);
        assertId(0x04, PacketName.CONFIG_KEEP_ALIVE, V769);
        assertId(0x05, PacketName.CONFIG_PING, V769);
        assertId(0x06, PacketName.CONFIG_RESET_CHAT, V769);
        assertId(0x07, PacketName.CONFIG_REGISTRY_DATA, V769);
        assertId(0x08, PacketName.CONFIG_REMOVE_RESOURCE_PACK, V769);
        assertId(0x09, PacketName.CONFIG_ADD_RESOURCE_PACK, V769);
        assertId(0x0a, PacketName.CONFIG_STORE_COOKIE, V769);
        assertId(0x0b, PacketName.CONFIG_TRANSFER, V769);
        assertId(0x0c, PacketName.CONFIG_FEATURE_FLAGS, V769);
        assertId(0x0d, PacketName.CONFIG_TAGS, V769);
        assertId(0x0e, PacketName.CONFIG_SELECT_KNOWN_PACKS, V769);
        assertId(0x0f, PacketName.CONFIG_CUSTOM_REPORT_DETAILS, V769);
        assertId(0x10, PacketName.CONFIG_SERVER_LINKS, V769);
        assertId(0x08, PacketName.CONFIG_CUSTOM_REPORT_DETAILS_TO_SERVER, V769);
        assertId(0x09, PacketName.CONFIG_SERVER_LINKS_TO_SERVER, V769);
        // 件数：toClient 17、toServer 10（768と同じ）。
        assertEquals(17, PacketIds.names(State.CONFIGURATION, Direction.CLIENTBOUND, V769).size());
        assertEquals(10, PacketIds.names(State.CONFIGURATION, Direction.SERVERBOUND, V769).size());
    }

    @Test
    void playClientboundIdsUnchangedFrom768() {
        // 769でクライアント行きのIDは1件も変わらない。
        assertId(0x2c, PacketName.PLAY_JOIN_GAME, V769);
        assertId(0x2c, PacketName.PLAY_JOIN_GAME, V768);
        assertId(0x5b, PacketName.PLAY_SPAWN_POSITION, V769);
        assertId(0x27, PacketName.PLAY_KEEP_ALIVE, V769);
        assertId(0x07, PacketName.PLAY_CHAT_MESSAGE, V769);
        assertId(0x28, PacketName.PLAY_CHUNK_DATA, V769);
        assertId(0x20, PacketName.PLAY_SYNC_ENTITY_POSITION, V769);
        assertId(0x44, PacketName.PLAY_RECIPE_BOOK_ADD, V769);
        assertId(0x5a, PacketName.PLAY_SET_CURSOR_ITEM, V769);
        assertId(0x66, PacketName.PLAY_SET_PLAYER_INVENTORY, V769);
        // 件数：toClient 131（768と同じ）。
        assertEquals(131, PacketIds.names(State.PLAY, Direction.CLIENTBOUND, V769).size());
    }

    @Test
    void playServerboundIdsChangedFrom768() {
        // 768と ID が変わったもの（スポットチェック。24件すべてサーバー行き）。
        assertId(0x25, PacketName.PLAY_CRAFT_RECIPE_REQUEST, V769);
        assertId(0x24, PacketName.PLAY_CRAFT_RECIPE_REQUEST, V768);
        assertId(0x29, PacketName.PLAY_PLAYER_INPUT, V769);
        assertId(0x28, PacketName.PLAY_PLAYER_INPUT, V768);
        assertId(0x2f, PacketName.PLAY_RESOURCE_PACK_RESPONSE, V769);
        assertId(0x2d, PacketName.PLAY_RESOURCE_PACK_RESPONSE, V768);
        assertId(0x33, PacketName.PLAY_HELD_ITEM_SLOT_TO_SERVER, V769);
        assertId(0x31, PacketName.PLAY_HELD_ITEM_SLOT_TO_SERVER, V768);
        assertId(0x36, PacketName.PLAY_SET_CREATIVE_SLOT, V769);
        assertId(0x34, PacketName.PLAY_SET_CREATIVE_SLOT, V768);
        assertId(0x3c, PacketName.PLAY_BLOCK_PLACE, V769);
        assertId(0x3a, PacketName.PLAY_BLOCK_PLACE, V768);
        assertId(0x3d, PacketName.PLAY_USE_ITEM, V769);
        assertId(0x3b, PacketName.PLAY_USE_ITEM, V768);
        // ping_request（769では 0x24）は表に入れない（プロキシが落とす方針）。
        // 769で新規（3件）。
        assertId(0x22, PacketName.PLAY_PICK_ITEM_FROM_BLOCK, V769);
        assertId(0x23, PacketName.PLAY_PICK_ITEM_FROM_ENTITY, V769);
        assertId(0x2a, PacketName.PLAY_PLAYER_LOADED, V769);
        assertAbsent(PacketName.PLAY_PICK_ITEM_FROM_BLOCK, V768);
        assertAbsent(PacketName.PLAY_PICK_ITEM_FROM_ENTITY, V768);
        assertAbsent(PacketName.PLAY_PLAYER_LOADED, V768);
        // 769で削除（pick_item。768では 0x22）。
        assertAbsent(PacketName.PLAY_PICK_ITEM, V769);
        assertId(0x22, PacketName.PLAY_PICK_ITEM, V768);
        // 削除されたままのもの：766の resource_pack 削除は769にも無い。
        assertAbsent(PacketName.PLAY_RESOURCE_PACK, V768);
        assertAbsent(PacketName.PLAY_RESOURCE_PACK, V769);
        assertAbsent(PacketName.CONFIG_RESOURCE_PACK_SEND, V768);
        assertAbsent(PacketName.CONFIG_RESOURCE_PACK_SEND, V769);
        // 件数：toServer 61（768の59から pick_item −1、新規＋3）。
        assertEquals(61, PacketIds.names(State.PLAY, Direction.SERVERBOUND, V769).size());
    }

    @Test
    void reverseLookup769() {
        assertEquals(PacketName.PLAY_JOIN_GAME,
                PacketIds.nameFor(0x2c, State.PLAY, Direction.CLIENTBOUND, V769).orElseThrow());
        assertEquals(PacketName.PLAY_PICK_ITEM_FROM_BLOCK,
                PacketIds.nameFor(0x22, State.PLAY, Direction.SERVERBOUND, V769).orElseThrow());
        assertEquals(PacketName.PLAY_PICK_ITEM_FROM_ENTITY,
                PacketIds.nameFor(0x23, State.PLAY, Direction.SERVERBOUND, V769).orElseThrow());
        assertEquals(PacketName.PLAY_PLAYER_LOADED,
                PacketIds.nameFor(0x2a, State.PLAY, Direction.SERVERBOUND, V769).orElseThrow());
        assertEquals(PacketName.PLAY_PLAYER_INPUT,
                PacketIds.nameFor(0x29, State.PLAY, Direction.SERVERBOUND, V769).orElseThrow());
        assertEquals(PacketName.CONFIG_SERVER_LINKS,
                PacketIds.nameFor(0x10, State.CONFIGURATION, Direction.CLIENTBOUND, V769)
                        .orElseThrow());
    }

    @Test
    void legacyPingAndPingRequestNotInTable() {
        // 従来通り、legacy_server_list_ping（0xFE）と play の ping_request
        // （769では 0x24）は表に入れない（プロキシが落とす方針のため）。
        assertFalse(PacketIds.names(State.PLAY, Direction.SERVERBOUND, V769).stream()
                .anyMatch(n -> n.name().contains("PING_REQUEST")));
        assertFalse(PacketIds.names(State.HANDSHAKING, Direction.SERVERBOUND, V769).stream()
                .anyMatch(n -> n.name().contains("LEGACY")));
    }
}
