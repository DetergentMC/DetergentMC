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
 * {@link PacketIds} の767（1.21／1.21.1）向けスポットチェック。
 * 期待値は PrismarineJS minecraft-data の protocol.json（1.21.1）と
 * tools/packet-ids-767.py の出力（packet-ids-767.json）による。
 * 766と比べ、既存のパケットはすべて同じIDで、新規は6件だけ
 * （config の custom_report_details／server_links 各2方向と
 * play の同2件）。削除されたパケットは無い。
 */
class PacketIds767Test {

    private static final int V763 = ProtocolVersion.V1_20_1;
    private static final int V764 = ProtocolVersion.V1_20_2;
    private static final int V765 = ProtocolVersion.V1_20_4;
    private static final int V766 = ProtocolVersion.V1_20_5;
    private static final int V767 = ProtocolVersion.V1_21;

    private static void assertId(int expected, PacketName name, int protocol) {
        assertEquals(OptionalInt.of(expected), PacketIds.id(name, protocol),
                name + " for protocol " + protocol);
    }

    private static void assertAbsent(PacketName name, int protocol) {
        assertEquals(OptionalInt.empty(), PacketIds.id(name, protocol),
                name + " は " + protocol + " に存在しないこと");
    }

    @Test
    void tableKnows767() {
        assertTrue(PacketIds.isSupported(V767));
        assertEquals(List.of(756, 757, 759, 760, 761, 762, 763, 764, 765, 766, 767, 768, 769, 770, 771, 772, 773, 774, 775), PacketIds.supportedProtocols());
    }

    @Test
    void handshakeAndLogin() {
        assertId(0x00, PacketName.HANDSHAKE, V767);
        assertId(0x00, PacketName.LOGIN_DISCONNECT, V767);
        assertId(0x01, PacketName.LOGIN_ENCRYPTION_REQUEST, V767);
        // Login Success のIDは変わらない（0x02）。766と同様、ペイロードに
        // Strict Error Handling の bool が付く（ペイロード側の仕事）。
        // 768（1.21.2）で廃止されるため、767だけの扱いになる。
        assertId(0x02, PacketName.LOGIN_SUCCESS, V767);
        assertId(0x03, PacketName.LOGIN_SET_COMPRESSION, V767);
        assertId(0x04, PacketName.LOGIN_PLUGIN_REQUEST, V767);
        assertId(0x05, PacketName.LOGIN_COOKIE_REQUEST, V767);
        assertId(0x04, PacketName.LOGIN_COOKIE_RESPONSE, V767);
    }

    @Test
    void configurationIds() {
        // 766と同じ並び。末尾に custom_report_details／server_links が追加。
        assertId(0x00, PacketName.CONFIG_COOKIE_REQUEST, V767);
        assertId(0x01, PacketName.CONFIG_PLUGIN_MESSAGE, V767);
        assertId(0x02, PacketName.CONFIG_DISCONNECT, V767);
        assertId(0x03, PacketName.CONFIG_FINISH_CONFIGURATION, V767);
        assertId(0x04, PacketName.CONFIG_KEEP_ALIVE, V767);
        assertId(0x05, PacketName.CONFIG_PING, V767);
        assertId(0x06, PacketName.CONFIG_RESET_CHAT, V767);
        assertId(0x07, PacketName.CONFIG_REGISTRY_DATA, V767);
        assertId(0x08, PacketName.CONFIG_REMOVE_RESOURCE_PACK, V767);
        assertId(0x09, PacketName.CONFIG_ADD_RESOURCE_PACK, V767);
        assertId(0x0a, PacketName.CONFIG_STORE_COOKIE, V767);
        assertId(0x0b, PacketName.CONFIG_TRANSFER, V767);
        assertId(0x0c, PacketName.CONFIG_FEATURE_FLAGS, V767);
        assertId(0x0d, PacketName.CONFIG_TAGS, V767);
        assertId(0x0e, PacketName.CONFIG_SELECT_KNOWN_PACKS, V767);
        // 767で追加（toClient）。
        assertId(0x0f, PacketName.CONFIG_CUSTOM_REPORT_DETAILS, V767);
        assertId(0x10, PacketName.CONFIG_SERVER_LINKS, V767);
        assertAbsent(PacketName.CONFIG_CUSTOM_REPORT_DETAILS, V766);
        assertAbsent(PacketName.CONFIG_SERVER_LINKS, V766);
        // 767で追加（toServer）。
        assertId(0x08, PacketName.CONFIG_CUSTOM_REPORT_DETAILS_TO_SERVER, V767);
        assertId(0x09, PacketName.CONFIG_SERVER_LINKS_TO_SERVER, V767);
        assertAbsent(PacketName.CONFIG_CUSTOM_REPORT_DETAILS_TO_SERVER, V766);
        assertAbsent(PacketName.CONFIG_SERVER_LINKS_TO_SERVER, V766);
        // 件数：toClient 17、toServer 10。
        assertEquals(17, PacketIds.names(State.CONFIGURATION, Direction.CLIENTBOUND, V767).size());
        assertEquals(10, PacketIds.names(State.CONFIGURATION, Direction.SERVERBOUND, V767).size());
    }

    @Test
    void playIdsUnchangedFrom766() {
        // 766と ID が変わらないもの（スポットチェック）。
        assertId(0x2b, PacketName.PLAY_JOIN_GAME, V767);
        assertId(0x2b, PacketName.PLAY_JOIN_GAME, V766);
        assertId(0x26, PacketName.PLAY_KEEP_ALIVE, V767);
        assertId(0x24, PacketName.PLAY_KEEP_ALIVE, V765);
        assertId(0x06, PacketName.PLAY_CHAT_MESSAGE, V767);
        assertId(0x39, PacketName.PLAY_USE_ITEM, V767);
        assertId(0x15, PacketName.PLAY_SET_SLOT, V767);
        assertId(0x27, PacketName.PLAY_CHUNK_DATA, V767);
        // 767で追加。
        assertId(0x7a, PacketName.PLAY_CUSTOM_REPORT_DETAILS, V767);
        assertId(0x7b, PacketName.PLAY_SERVER_LINKS, V767);
        assertAbsent(PacketName.PLAY_CUSTOM_REPORT_DETAILS, V766);
        assertAbsent(PacketName.PLAY_SERVER_LINKS, V766);
        // 削除されたパケットは無い：766の resource_pack 削除は767にも無い。
        assertAbsent(PacketName.PLAY_RESOURCE_PACK, V766);
        assertAbsent(PacketName.PLAY_RESOURCE_PACK, V767);
        assertAbsent(PacketName.CONFIG_RESOURCE_PACK_SEND, V766);
        assertAbsent(PacketName.CONFIG_RESOURCE_PACK_SEND, V767);
    }

    @Test
    void reverseLookup767() {
        assertEquals(PacketName.CONFIG_SERVER_LINKS,
                PacketIds.nameFor(0x10, State.CONFIGURATION, Direction.CLIENTBOUND, V767)
                        .orElseThrow());
        assertEquals(PacketName.CONFIG_CUSTOM_REPORT_DETAILS,
                PacketIds.nameFor(0x0f, State.CONFIGURATION, Direction.CLIENTBOUND, V767)
                        .orElseThrow());
        assertEquals(PacketName.PLAY_SERVER_LINKS,
                PacketIds.nameFor(0x7b, State.PLAY, Direction.CLIENTBOUND, V767).orElseThrow());
        assertEquals(PacketName.PLAY_CUSTOM_REPORT_DETAILS,
                PacketIds.nameFor(0x7a, State.PLAY, Direction.CLIENTBOUND, V767).orElseThrow());
        assertEquals(PacketName.PLAY_JOIN_GAME,
                PacketIds.nameFor(0x2b, State.PLAY, Direction.CLIENTBOUND, V767).orElseThrow());
    }

    @Test
    void legacyPingAndPingRequestNotInTable() {
        // 従来通り、legacy_server_list_ping（0xFE）と play の ping_request
        // （767では 0x21）は表に入れない（プロキシが落とす方針のため）。
        assertFalse(PacketIds.names(State.PLAY, Direction.SERVERBOUND, V767).stream()
                .anyMatch(n -> n.name().contains("PING_REQUEST")));
        assertFalse(PacketIds.names(State.HANDSHAKING, Direction.SERVERBOUND, V767).stream()
                .anyMatch(n -> n.name().contains("LEGACY")));
    }
}
