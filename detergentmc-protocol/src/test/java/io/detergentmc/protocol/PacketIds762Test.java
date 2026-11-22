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
 * {@link PacketIds} の762（1.19.4）向けスポットチェック。
 * 期待値は PrismarineJS minecraft-data の protocol.json（1.19.4）と
 * tools/packet-ids-762.py の出力による。
 * 763と比べ、パケットIDが違うものは0件。新規パケットは0件、削除も0件。
 * 762に無いのは設定フェーズのパケット（1.20.2で追加）と763以降の
 * 追加パケットのみ（86件）。
 */
class PacketIds762Test {

    private static final int V762 = ProtocolVersion.V1_19_4;
    private static final int V763 = ProtocolVersion.V1_20_1;

    private static void assertId(int expected, PacketName name, int protocol) {
        assertEquals(OptionalInt.of(expected), PacketIds.id(name, protocol),
                name + " for protocol " + protocol);
    }

    private static void assertAbsent(PacketName name, int protocol) {
        assertEquals(OptionalInt.empty(), PacketIds.id(name, protocol),
                name + " は " + protocol + " に存在しないこと");
    }

    @Test
    void tableKnows762() {
        assertTrue(PacketIds.isSupported(V762));
        assertEquals(List.of(4, 5, 47, 107, 108, 109, 110, 210, 315, 316, 335, 338, 340, 393, 401, 404, 477, 480, 485, 490, 498, 573, 575, 578, 735, 736, 751, 753, 754, 755, 756, 757, 758, 759, 760, 761, 762, 763, 764, 765, 766, 767, 768, 769, 770, 771, 772, 773, 774, 775, 776),
                PacketIds.supportedProtocols());
    }

    @Test
    void noIdsChangedFrom763() {
        // packet-ids-762.py：255エントリすべてが一意に写像でき、763とIDが
        // 違うものは0件、762で削除（-1）も0件、762で新規も0件。
        // 762に存在する171件はすべて763とIDが同じ（Named Entity Spawn・
        // Feature Flags の2件は761の対応で表に追加されたが、762・763の
        // IDも同じ）。
        for (PacketName name : PacketName.values()) {
            if (name.id762() != -1) {
                assertEquals(name.id763(), name.id762(),
                        name + " は762と763でIDが同じはず");
            }
        }
        assertEquals(171, PacketIds.names(State.PLAY, Direction.CLIENTBOUND, V762).size()
                + PacketIds.names(State.PLAY, Direction.SERVERBOUND, V762).size()
                + PacketIds.names(State.LOGIN, Direction.CLIENTBOUND, V762).size()
                + PacketIds.names(State.LOGIN, Direction.SERVERBOUND, V762).size()
                + PacketIds.names(State.HANDSHAKING, Direction.SERVERBOUND, V762).size());
    }

    @Test
    void handshakeAndLogin() {
        assertId(0x00, PacketName.HANDSHAKE, V762);
        assertId(0x00, PacketName.LOGIN_DISCONNECT, V762);
        assertId(0x01, PacketName.LOGIN_ENCRYPTION_REQUEST, V762);
        // Login Success のIDは763と同じ（0x02）。Login Start も同じ。
        assertId(0x02, PacketName.LOGIN_SUCCESS, V762);
        assertId(0x03, PacketName.LOGIN_SET_COMPRESSION, V762);
        assertId(0x04, PacketName.LOGIN_PLUGIN_REQUEST, V762);
        // Cookie は設定フェーズと共に1.20.2で追加されたため762には無い。
        assertAbsent(PacketName.LOGIN_COOKIE_REQUEST, V762);
        assertAbsent(PacketName.LOGIN_COOKIE_RESPONSE, V762);
    }

    @Test
    void configurationPhaseDoesNotExistIn762() {
        // 762（1.19.4）には設定フェーズそのものが無い（1.20.2で追加）。
        // 設定フェーズのパケットはすべて -1。
        assertEquals(0, PacketIds.names(State.CONFIGURATION, Direction.CLIENTBOUND, V762).size());
        assertEquals(0, PacketIds.names(State.CONFIGURATION, Direction.SERVERBOUND, V762).size());
        assertAbsent(PacketName.CONFIG_REGISTRY_DATA, V762);
        assertAbsent(PacketName.CONFIG_FINISH_CONFIGURATION, V762);
        assertAbsent(PacketName.CONFIG_ADD_RESOURCE_PACK, V762);
    }

    @Test
    void playIdsAreSameAs763() {
        // パケットIDは763とまったく同じ（新規・削除・ID変更は0件）。
        assertId(0x28, PacketName.PLAY_JOIN_GAME, V762);
        assertId(0x28, PacketName.PLAY_JOIN_GAME, V763);
        assertId(0x50, PacketName.PLAY_SPAWN_POSITION, V762);
        assertId(0x23, PacketName.PLAY_KEEP_ALIVE, V762);
        assertId(0x32, PacketName.PLAY_PING, V762);
        assertId(0x24, PacketName.PLAY_CHUNK_DATA, V762);
        assertId(0x1a, PacketName.PLAY_DISCONNECT, V762);
        assertId(0x27, PacketName.PLAY_UPDATE_LIGHT, V762);
        assertId(0x31, PacketName.PLAY_OPEN_SIGN_EDITOR, V762);
        assertId(0x36, PacketName.PLAY_END_COMBAT_EVENT, V762);
        assertId(0x38, PacketName.PLAY_DEATH_COMBAT_EVENT, V762);
        assertId(0x41, PacketName.PLAY_RESPAWN, V762);
        assertId(0x69, PacketName.PLAY_ADVANCEMENTS, V762);
        // 旧式リソースパック（プレイ状態・UUIDなし）は762・763とも 0x40。
        assertId(0x40, PacketName.PLAY_RESOURCE_PACK, V762);
        assertId(0x40, PacketName.PLAY_RESOURCE_PACK, V763);
        // サーバー行き：Window Click・Set Creative Slot・Update Sign も763と同じID。
        assertId(0x0b, PacketName.PLAY_WINDOW_CLICK, V762);
        assertId(0x2b, PacketName.PLAY_SET_CREATIVE_SLOT, V762);
        assertId(0x2e, PacketName.PLAY_UPDATE_SIGN, V762);
        // 件数：toClient 111、toServer 51（763と同じ。toClient は
        // Named Entity Spawn・Feature Flags の2件を表に追加したため
        // 109 から 111 になった）。
        assertEquals(111, PacketIds.names(State.PLAY, Direction.CLIENTBOUND, V762).size());
        assertEquals(51, PacketIds.names(State.PLAY, Direction.SERVERBOUND, V762).size());
        assertEquals(111, PacketIds.names(State.PLAY, Direction.CLIENTBOUND, V763).size());
        assertEquals(51, PacketIds.names(State.PLAY, Direction.SERVERBOUND, V763).size());
    }

    @Test
    void reverseLookup762() {
        assertEquals(PacketName.PLAY_JOIN_GAME,
                PacketIds.nameFor(0x28, State.PLAY, Direction.CLIENTBOUND, V762).orElseThrow());
        assertEquals(PacketName.PLAY_CHUNK_DATA,
                PacketIds.nameFor(0x24, State.PLAY, Direction.CLIENTBOUND, V762).orElseThrow());
        assertEquals(PacketName.PLAY_RESOURCE_PACK,
                PacketIds.nameFor(0x40, State.PLAY, Direction.CLIENTBOUND, V762).orElseThrow());
        assertEquals(PacketName.PLAY_WINDOW_CLICK,
                PacketIds.nameFor(0x0b, State.PLAY, Direction.SERVERBOUND, V762).orElseThrow());
        assertEquals(PacketName.PLAY_SET_CREATIVE_SLOT,
                PacketIds.nameFor(0x2b, State.PLAY, Direction.SERVERBOUND, V762).orElseThrow());
        assertEquals(PacketName.PLAY_UPDATE_SIGN,
                PacketIds.nameFor(0x2e, State.PLAY, Direction.SERVERBOUND, V762).orElseThrow());
        // 設定フェーズのIDは762では何にも写らない。
        assertTrue(PacketIds.nameFor(0x07, State.CONFIGURATION, Direction.CLIENTBOUND, V762)
                .isEmpty());
    }

    @Test
    void legacyPingAndPingRequestNotInTable() {
        // 従来通り、legacy_server_list_ping（0xFE）と play の ping_request
        // は表に入れない（プロキシが落とす方針のため）。
        assertFalse(PacketIds.names(State.PLAY, Direction.SERVERBOUND, V762).stream()
                .anyMatch(n -> n.name().contains("PING_REQUEST")));
        assertFalse(PacketIds.names(State.HANDSHAKING, Direction.SERVERBOUND, V762).stream()
                .anyMatch(n -> n.name().contains("LEGACY")));
    }
}
