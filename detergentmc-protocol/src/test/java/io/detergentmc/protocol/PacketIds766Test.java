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
 * {@link PacketIds} の766（1.20.5／1.20.6）向けスポットチェック。
 * 期待値は PrismarineJS minecraft-data の protocol.json（1.20.5）と
 * tools/packet-ids-766.py の出力（packet-ids-766.json）による。
 * 199件のうち 159件が 765と違い、2件（CONFIG_RESOURCE_PACK_SEND／
 * PLAY_RESOURCE_PACK）が 766で削除された。
 */
class PacketIds766Test {

    private static final int V763 = ProtocolVersion.V1_20_1;
    private static final int V764 = ProtocolVersion.V1_20_2;
    private static final int V765 = ProtocolVersion.V1_20_4;
    private static final int V766 = ProtocolVersion.V1_20_5;

    private static void assertId(int expected, PacketName name, int protocol) {
        assertEquals(OptionalInt.of(expected), PacketIds.id(name, protocol),
                name + " for protocol " + protocol);
    }

    private static void assertAbsent(PacketName name, int protocol) {
        assertEquals(OptionalInt.empty(), PacketIds.id(name, protocol),
                name + " は " + protocol + " に存在しないこと");
    }

    @Test
    void tableKnows766() {
        assertTrue(PacketIds.isSupported(V766));
        assertEquals(List.of(753, 754, 755, 756, 757, 759, 760, 761, 762, 763, 764, 765, 766, 767, 768, 769, 770, 771, 772, 773, 774, 775), PacketIds.supportedProtocols());
    }

    @Test
    void handshakeAndLogin() {
        assertId(0x00, PacketName.HANDSHAKE, V766);
        assertId(0x00, PacketName.LOGIN_DISCONNECT, V766);
        assertId(0x01, PacketName.LOGIN_ENCRYPTION_REQUEST, V766);
        // Login Success のIDは変わらない（0x02）。ただしペイロードに
        // Strict Error Handling の bool が追加された（ペイロード側の仕事）。
        assertId(0x02, PacketName.LOGIN_SUCCESS, V766);
        assertId(0x03, PacketName.LOGIN_SET_COMPRESSION, V766);
        assertId(0x04, PacketName.LOGIN_PLUGIN_REQUEST, V766);
        // 766で追加：cookie。
        assertId(0x05, PacketName.LOGIN_COOKIE_REQUEST, V766);
        assertId(0x04, PacketName.LOGIN_COOKIE_RESPONSE, V766);
    }

    @Test
    void configurationIds() {
        // 766の config toClient は 0x00 が cookie_request で始まる。
        assertId(0x00, PacketName.CONFIG_COOKIE_REQUEST, V766);
        assertId(0x01, PacketName.CONFIG_PLUGIN_MESSAGE, V766);
        assertId(0x02, PacketName.CONFIG_DISCONNECT, V766);
        assertId(0x03, PacketName.CONFIG_FINISH_CONFIGURATION, V766);
        assertId(0x04, PacketName.CONFIG_KEEP_ALIVE, V766);
        assertId(0x05, PacketName.CONFIG_PING, V766);
        assertId(0x06, PacketName.CONFIG_RESET_CHAT, V766);
        assertId(0x07, PacketName.CONFIG_REGISTRY_DATA, V766);
        assertId(0x08, PacketName.CONFIG_REMOVE_RESOURCE_PACK, V766);
        assertId(0x09, PacketName.CONFIG_ADD_RESOURCE_PACK, V766);
        assertId(0x0a, PacketName.CONFIG_STORE_COOKIE, V766);
        assertId(0x0b, PacketName.CONFIG_TRANSFER, V766);
        assertId(0x0c, PacketName.CONFIG_FEATURE_FLAGS, V766);
        assertId(0x0d, PacketName.CONFIG_TAGS, V766);
        assertId(0x0e, PacketName.CONFIG_SELECT_KNOWN_PACKS, V766);
        // 766で削除：resource_pack_send（764にだけあった）。
        assertId(0x06, PacketName.CONFIG_RESOURCE_PACK_SEND, V764);
        assertAbsent(PacketName.CONFIG_RESOURCE_PACK_SEND, V765);
        assertAbsent(PacketName.CONFIG_RESOURCE_PACK_SEND, V766);
        // 766で追加（toServer）。
        assertId(0x01, PacketName.CONFIG_COOKIE_RESPONSE, V766);
        assertId(0x07, PacketName.CONFIG_SELECT_KNOWN_PACKS_TO_SERVER, V766);
        // 件数：toClient 15、toServer 8。
        assertEquals(15, PacketIds.names(State.CONFIGURATION, Direction.CLIENTBOUND, V766).size());
        assertEquals(8, PacketIds.names(State.CONFIGURATION, Direction.SERVERBOUND, V766).size());
    }

    @Test
    void playChatIds() {
        // 766で chat_command_signed（0x05）が追加され、chat_message は
        // 0x05→0x06 へずれた。chat_command（0x04）は変わらない。
        assertId(0x04, PacketName.PLAY_CHAT_COMMAND, V766);
        assertId(0x05, PacketName.PLAY_CHAT_COMMAND_SIGNED, V766);
        assertId(0x06, PacketName.PLAY_CHAT_MESSAGE, V766);
        assertId(0x05, PacketName.PLAY_CHAT_MESSAGE, V765);
    }

    @Test
    void playNewPackets() {
        assertId(0x16, PacketName.PLAY_COOKIE_REQUEST, V766);
        assertId(0x1b, PacketName.PLAY_DEBUG_SAMPLE, V766);
        assertId(0x6b, PacketName.PLAY_STORE_COOKIE, V766);
        assertId(0x73, PacketName.PLAY_TRANSFER, V766);
        assertId(0x79, PacketName.PLAY_SET_PROJECTILE_POWER, V766);
        assertId(0x11, PacketName.PLAY_COOKIE_RESPONSE, V766);
        assertId(0x13, PacketName.PLAY_DEBUG_SAMPLE_SUBSCRIPTION, V766);
        // 766で削除：resource_pack（763・764にだけあった）。
        assertAbsent(PacketName.PLAY_RESOURCE_PACK, V765);
        assertAbsent(PacketName.PLAY_RESOURCE_PACK, V766);
        assertId(0x40, PacketName.PLAY_RESOURCE_PACK, V763);
        assertId(0x42, PacketName.PLAY_RESOURCE_PACK, V764);
    }

    @Test
    void playChangedIds() {
        // 変わるもの（スポットチェック）。
        assertId(0x26, PacketName.PLAY_KEEP_ALIVE, V766);
        assertId(0x24, PacketName.PLAY_KEEP_ALIVE, V765);
        assertId(0x39, PacketName.PLAY_USE_ITEM, V766);
        assertId(0x36, PacketName.PLAY_USE_ITEM, V765);
    }

    @Test
    void reverseLookup766() {
        // chat_message はサーバー行き（クライアント→サーバー）。
        assertEquals(PacketName.PLAY_CHAT_MESSAGE,
                PacketIds.nameFor(0x06, State.PLAY, Direction.SERVERBOUND, V766).orElseThrow());
        assertEquals(PacketName.PLAY_CHAT_COMMAND_SIGNED,
                PacketIds.nameFor(0x05, State.PLAY, Direction.SERVERBOUND, V766).orElseThrow());
        assertEquals(PacketName.CONFIG_COOKIE_REQUEST,
                PacketIds.nameFor(0x00, State.CONFIGURATION, Direction.CLIENTBOUND, V766)
                        .orElseThrow());
        // 削除された resource_pack_send の ID 0x06 は reset_chat に再利用された。
        assertEquals(PacketName.CONFIG_RESET_CHAT,
                PacketIds.nameFor(0x06, State.CONFIGURATION, Direction.CLIENTBOUND, V766)
                        .orElseThrow());
    }

    @Test
    void legacyPingAndPingRequestNotInTable() {
        // 従来通り、legacy_server_list_ping（0xFE）と play の ping_request
        // （0x21）は表に入れない（プロキシが落とす方針のため）。
        assertFalse(PacketIds.names(State.PLAY, Direction.SERVERBOUND, V766).stream()
                .anyMatch(n -> n.name().contains("PING_REQUEST")));
    }
}
