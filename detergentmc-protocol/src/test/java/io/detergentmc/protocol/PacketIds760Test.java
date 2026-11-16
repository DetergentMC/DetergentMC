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
 * {@link PacketIds} の760（1.19.1／1.19.2）向けスポットチェック。
 * 期待値は PrismarineJS minecraft-data の protocol.json（1.19.2）と
 * tools/packet-ids-760.py の出力による。
 * 761と比べ、パケットIDが違うものは123件（既存257件のうち118件＋
 * 新規エントリ5件）。760に無いものは94件
 * （設定フェーズのパケットと764以降の追加パケット。うち761にも
 * あるのは Player Info Remove／Profileless Chat／Feature Flags／
 * Chat Session Update の4件だけ）。
 * 760にあって表に無かったのは legacy_server_list_ping（0xFE。従来通り
 * 表に入れない）のほか、Chat Preview（0x0c）・Named Sound Effect（0x17）・
 * Message Header（0x32）・Should Display Chat Preview（0x4e）・
 * Chat Preview（サーバー行き・0x06）の5件で、この5件は新規エントリとして
 * 表に追加した。
 */
class PacketIds760Test {

    private static final int V760 = ProtocolVersion.V1_19_1;
    private static final int V761 = ProtocolVersion.V1_19_3;
    private static final int V762 = ProtocolVersion.V1_19_4;

    private static void assertId(int expected, PacketName name, int protocol) {
        assertEquals(OptionalInt.of(expected), PacketIds.id(name, protocol),
                name + " for protocol " + protocol);
    }

    private static void assertAbsent(PacketName name, int protocol) {
        assertEquals(OptionalInt.empty(), PacketIds.id(name, protocol),
                name + " は " + protocol + " に存在しないこと");
    }

    @Test
    void tableKnows760() {
        assertTrue(PacketIds.isSupported(V760));
        assertEquals(List.of(753, 754, 755, 756, 757, 759, 760, 761, 762, 763, 764, 765, 766, 767, 768, 769, 770, 771, 772, 773, 774, 775),
                PacketIds.supportedProtocols());
    }

    @Test
    void idsChangedFrom761() {
        // packet-ids-760.py：257エントリすべてが一意に写像でき、761とIDが
        // 違うものは118件、760で存在しない（-1）は94件。さらに新規エントリ
        // 5件（Chat Preview／Named Sound Effect／Message Header／
        // Should Display Chat Preview／Chat Preview（サーバー行き））も761と
        // IDが違うため、761とIDが違うものは合計123件。
        // 760に存在する168件（play toClient 108・toServer 51・
        // login toClient 5・toServer 3・handshaking 1）のうち、761とIDが
        // 同じものは45件だけ。
        int changed = 0;
        int present = 0;
        for (PacketName name : PacketName.values()) {
            if (name.id760() != -1) {
                present++;
                if (name.id760() != name.id761()) {
                    changed++;
                }
            }
        }
        assertEquals(123, changed);
        assertEquals(168, present);
        assertEquals(168, PacketIds.names(State.PLAY, Direction.CLIENTBOUND, V760).size()
                + PacketIds.names(State.PLAY, Direction.SERVERBOUND, V760).size()
                + PacketIds.names(State.LOGIN, Direction.CLIENTBOUND, V760).size()
                + PacketIds.names(State.LOGIN, Direction.SERVERBOUND, V760).size()
                + PacketIds.names(State.HANDSHAKING, Direction.SERVERBOUND, V760).size());
    }

    @Test
    void handshakeAndLogin() {
        assertId(0x00, PacketName.HANDSHAKE, V760);
        assertId(0x00, PacketName.LOGIN_DISCONNECT, V760);
        assertId(0x01, PacketName.LOGIN_ENCRYPTION_REQUEST, V760);
        // Login Success のIDは761と同じ（0x02）。Login Start も同じ。
        assertId(0x02, PacketName.LOGIN_SUCCESS, V760);
        assertId(0x03, PacketName.LOGIN_SET_COMPRESSION, V760);
        assertId(0x04, PacketName.LOGIN_PLUGIN_REQUEST, V760);
        // Cookie は設定フェーズと共に1.20.2で追加されたため760には無い。
        assertAbsent(PacketName.LOGIN_COOKIE_REQUEST, V760);
        assertAbsent(PacketName.LOGIN_COOKIE_RESPONSE, V760);
        // Login Acknowledged は1.20.2で追加されたため760には無い。
        assertAbsent(PacketName.LOGIN_ACKNOWLEDGED, V760);
    }

    @Test
    void configurationPhaseDoesNotExistIn760() {
        // 760（1.19.1／1.19.2）には設定フェーズそのものが無い（1.20.2で追加）。
        // 設定フェーズのパケットはすべて -1。
        assertEquals(0, PacketIds.names(State.CONFIGURATION, Direction.CLIENTBOUND, V760).size());
        assertEquals(0, PacketIds.names(State.CONFIGURATION, Direction.SERVERBOUND, V760).size());
        assertAbsent(PacketName.CONFIG_REGISTRY_DATA, V760);
        assertAbsent(PacketName.CONFIG_FINISH_CONFIGURATION, V760);
        assertAbsent(PacketName.CONFIG_ADD_RESOURCE_PACK, V760);
    }

    @Test
    void playIds() {
        // 761との差分の代表値（packet-ids-760.py の出力）。
        assertId(0x25, PacketName.PLAY_JOIN_GAME, V760);
        assertId(0x24, PacketName.PLAY_JOIN_GAME, V761);
        assertId(0x4d, PacketName.PLAY_SPAWN_POSITION, V760);
        assertId(0x20, PacketName.PLAY_KEEP_ALIVE, V760);
        assertId(0x2f, PacketName.PLAY_PING, V760);
        assertId(0x21, PacketName.PLAY_CHUNK_DATA, V760);
        assertId(0x19, PacketName.PLAY_DISCONNECT, V760);
        assertId(0x24, PacketName.PLAY_UPDATE_LIGHT, V760);
        assertId(0x2e, PacketName.PLAY_OPEN_SIGN_EDITOR, V760);
        assertId(0x34, PacketName.PLAY_END_COMBAT_EVENT, V760);
        assertId(0x36, PacketName.PLAY_DEATH_COMBAT_EVENT, V760);
        assertId(0x3e, PacketName.PLAY_RESPAWN, V760);
        assertId(0x67, PacketName.PLAY_ADVANCEMENTS, V760);
        // 旧式リソースパック（プレイ状態・UUIDなし）は760・761とも。
        assertId(0x3d, PacketName.PLAY_RESOURCE_PACK, V760);
        assertId(0x3c, PacketName.PLAY_RESOURCE_PACK, V761);
        // サーバー行き：Window Click・Set Creative Slot・Update Sign。
        assertId(0x0b, PacketName.PLAY_WINDOW_CLICK, V760);
        assertId(0x2b, PacketName.PLAY_SET_CREATIVE_SLOT, V760);
        assertId(0x2e, PacketName.PLAY_UPDATE_SIGN, V760);
        // Chat Message／Chat Command／Message Acknowledgement は760・761とも同じID。
        assertId(0x05, PacketName.PLAY_CHAT_MESSAGE, V760);
        assertId(0x04, PacketName.PLAY_CHAT_COMMAND, V760);
        assertId(0x03, PacketName.PLAY_MESSAGE_ACKNOWLEDGEMENT, V760);
        // Spawn Entity／Named Entity Spawn は760・761とも同じID。
        assertId(0x00, PacketName.PLAY_SPAWN_ENTITY, V760);
        assertId(0x02, PacketName.PLAY_NAMED_ENTITY_SPAWN, V760);
        // 件数：toClient 108、toServer 51（minecraft-data の 1.19.2 と一致）。
        assertEquals(108, PacketIds.names(State.PLAY, Direction.CLIENTBOUND, V760).size());
        assertEquals(51, PacketIds.names(State.PLAY, Direction.SERVERBOUND, V760).size());
    }

    @Test
    void chatPacketsOnlyIn760() {
        // 760にだけある5パケット（新規エントリ）。
        assertId(0x0c, PacketName.PLAY_CHAT_PREVIEW, V760);
        assertAbsent(PacketName.PLAY_CHAT_PREVIEW, V761);
        assertId(0x17, PacketName.PLAY_NAMED_SOUND_EFFECT, V760);
        assertAbsent(PacketName.PLAY_NAMED_SOUND_EFFECT, V761);
        assertId(0x32, PacketName.PLAY_MESSAGE_HEADER, V760);
        assertAbsent(PacketName.PLAY_MESSAGE_HEADER, V761);
        assertId(0x4e, PacketName.PLAY_SHOULD_DISPLAY_CHAT_PREVIEW, V760);
        assertAbsent(PacketName.PLAY_SHOULD_DISPLAY_CHAT_PREVIEW, V761);
        assertId(0x06, PacketName.PLAY_CHAT_PREVIEW_TO_SERVER, V760);
        assertAbsent(PacketName.PLAY_CHAT_PREVIEW_TO_SERVER, V761);
    }

    @Test
    void packetsOnlyIn761() {
        // 761にあって760に無いのは4件だけ。
        assertAbsent(PacketName.PLAY_PLAYER_INFO_REMOVE, V760);
        assertId(0x35, PacketName.PLAY_PLAYER_INFO_REMOVE, V761);
        assertAbsent(PacketName.PLAY_PROFILELESS_CHAT, V760);
        assertId(0x18, PacketName.PLAY_PROFILELESS_CHAT, V761);
        assertAbsent(PacketName.PLAY_FEATURE_FLAGS, V760);
        assertId(0x67, PacketName.PLAY_FEATURE_FLAGS, V761);
        assertAbsent(PacketName.PLAY_CHAT_SESSION_UPDATE, V760);
        assertId(0x20, PacketName.PLAY_CHAT_SESSION_UPDATE, V761);
        // 762 で追加された 4 パケットは 760・761 ともに無い。
        assertAbsent(PacketName.PLAY_BUNDLE_DELIMITER, V760);
        assertAbsent(PacketName.PLAY_CHUNK_BIOMES, V760);
        assertAbsent(PacketName.PLAY_DAMAGE_EVENT, V760);
        assertAbsent(PacketName.PLAY_HURT_ANIMATION, V760);
    }

    @Test
    void reverseLookup760() {
        assertEquals(PacketName.PLAY_JOIN_GAME,
                PacketIds.nameFor(0x25, State.PLAY, Direction.CLIENTBOUND, V760).orElseThrow());
        assertEquals(PacketName.PLAY_CHUNK_DATA,
                PacketIds.nameFor(0x21, State.PLAY, Direction.CLIENTBOUND, V760).orElseThrow());
        assertEquals(PacketName.PLAY_RESOURCE_PACK,
                PacketIds.nameFor(0x3d, State.PLAY, Direction.CLIENTBOUND, V760).orElseThrow());
        assertEquals(PacketName.PLAY_WINDOW_CLICK,
                PacketIds.nameFor(0x0b, State.PLAY, Direction.SERVERBOUND, V760).orElseThrow());
        assertEquals(PacketName.PLAY_SET_CREATIVE_SLOT,
                PacketIds.nameFor(0x2b, State.PLAY, Direction.SERVERBOUND, V760).orElseThrow());
        assertEquals(PacketName.PLAY_UPDATE_SIGN,
                PacketIds.nameFor(0x2e, State.PLAY, Direction.SERVERBOUND, V760).orElseThrow());
        assertEquals(PacketName.PLAY_NAMED_ENTITY_SPAWN,
                PacketIds.nameFor(0x02, State.PLAY, Direction.CLIENTBOUND, V760).orElseThrow());
        assertEquals(PacketName.PLAY_MESSAGE_HEADER,
                PacketIds.nameFor(0x32, State.PLAY, Direction.CLIENTBOUND, V760).orElseThrow());
        assertEquals(PacketName.PLAY_NAMED_SOUND_EFFECT,
                PacketIds.nameFor(0x17, State.PLAY, Direction.CLIENTBOUND, V760).orElseThrow());
        // 設定フェーズのIDは760では何にも写らない。
        assertTrue(PacketIds.nameFor(0x07, State.CONFIGURATION, Direction.CLIENTBOUND, V760)
                .isEmpty());
    }

    @Test
    void legacyPingAndPingRequestNotInTable() {
        // 従来通り、legacy_server_list_ping（0xFE）と play の ping_request
        // は表に入れない（プロキシが落とす方針のため）。
        assertFalse(PacketIds.names(State.PLAY, Direction.SERVERBOUND, V760).stream()
                .anyMatch(n -> n.name().contains("PING_REQUEST")));
        assertFalse(PacketIds.names(State.HANDSHAKING, Direction.SERVERBOUND, V760).stream()
                .anyMatch(n -> n.name().contains("LEGACY")));
    }
}
