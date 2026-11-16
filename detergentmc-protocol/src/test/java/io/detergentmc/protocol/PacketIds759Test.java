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
 * {@link PacketIds} の759（1.19）向けスポットチェック。
 * 期待値は PrismarineJS minecraft-data の protocol.json（1.19）と
 * tools/packet-ids-759.py の出力による。
 * 760と比べ、パケットIDが違うものは131件（存在する164件のうち131件）。
 * 759に無いものは98件（設定フェーズのパケットと760以降の追加パケット。
 * うち760にもあるのは Message Header／Chat Suggestions／Hide Message／
 * Message Acknowledgement の4件だけ）。
 * 759にあって表に無かったのは legacy_server_list_ping（0xFE。従来通り
 * 表に入れない）だけ。Chat Preview（0x0c）・Named Sound Effect（0x16）・
 * Should Display Chat Preview（0x4b）・Chat Preview（サーバー行き・0x05）は
 * 760と同じく表にある。
 */
class PacketIds759Test {

    private static final int V759 = ProtocolVersion.V1_19;
    private static final int V760 = ProtocolVersion.V1_19_1;
    private static final int V761 = ProtocolVersion.V1_19_3;

    private static void assertId(int expected, PacketName name, int protocol) {
        assertEquals(OptionalInt.of(expected), PacketIds.id(name, protocol),
                name + " for protocol " + protocol);
    }

    private static void assertAbsent(PacketName name, int protocol) {
        assertEquals(OptionalInt.empty(), PacketIds.id(name, protocol),
                name + " は " + protocol + " に存在しないこと");
    }

    @Test
    void tableKnows759() {
        assertTrue(PacketIds.isSupported(V759));
        assertEquals(List.of(753, 754, 755, 756, 757, 759, 760, 761, 762, 763, 764, 765, 766, 767, 768, 769, 770, 771, 772, 773, 774, 775),
                PacketIds.supportedProtocols());
    }

    @Test
    void idsChangedFrom760() {
        // packet-ids-759.py：262エントリすべてが一意に写像でき、760とIDが
        // 違うものは131件、759で存在しない（-1）は98件。
        // 759に存在する164件（play toClient 105・toServer 50・
        // login toClient 5・toServer 3・handshaking 1）のうち、760とIDが
        // 同じものは33件だけ。
        int changed = 0;
        int present = 0;
        for (PacketName name : PacketName.values()) {
            if (name.id759() != -1) {
                present++;
                if (name.id759() != name.id760()) {
                    changed++;
                }
            }
        }
        assertEquals(131, changed);
        assertEquals(164, present);
        assertEquals(164, PacketIds.names(State.PLAY, Direction.CLIENTBOUND, V759).size()
                + PacketIds.names(State.PLAY, Direction.SERVERBOUND, V759).size()
                + PacketIds.names(State.LOGIN, Direction.CLIENTBOUND, V759).size()
                + PacketIds.names(State.LOGIN, Direction.SERVERBOUND, V759).size()
                + PacketIds.names(State.HANDSHAKING, Direction.SERVERBOUND, V759).size());
    }

    @Test
    void handshakeAndLogin() {
        assertId(0x00, PacketName.HANDSHAKE, V759);
        assertId(0x00, PacketName.LOGIN_DISCONNECT, V759);
        assertId(0x01, PacketName.LOGIN_ENCRYPTION_REQUEST, V759);
        // Login Success／Login Start のIDは760と同じ（0x02／0x00）。
        assertId(0x02, PacketName.LOGIN_SUCCESS, V759);
        assertId(0x03, PacketName.LOGIN_SET_COMPRESSION, V759);
        assertId(0x04, PacketName.LOGIN_PLUGIN_REQUEST, V759);
        // Cookie は設定フェーズと共に1.20.2で追加されたため759には無い。
        assertAbsent(PacketName.LOGIN_COOKIE_REQUEST, V759);
        assertAbsent(PacketName.LOGIN_COOKIE_RESPONSE, V759);
        // Login Acknowledged は1.20.2で追加されたため759には無い。
        assertAbsent(PacketName.LOGIN_ACKNOWLEDGED, V759);
    }

    @Test
    void configurationPhaseDoesNotExistIn759() {
        // 759（1.19）には設定フェーズそのものが無い（1.20.2で追加）。
        // 設定フェーズのパケットはすべて -1。
        assertEquals(0, PacketIds.names(State.CONFIGURATION, Direction.CLIENTBOUND, V759).size());
        assertEquals(0, PacketIds.names(State.CONFIGURATION, Direction.SERVERBOUND, V759).size());
        assertAbsent(PacketName.CONFIG_REGISTRY_DATA, V759);
        assertAbsent(PacketName.CONFIG_FINISH_CONFIGURATION, V759);
        assertAbsent(PacketName.CONFIG_ADD_RESOURCE_PACK, V759);
    }

    @Test
    void playIds() {
        // 760との差分の代表値（packet-ids-759.py の出力）。
        assertId(0x23, PacketName.PLAY_JOIN_GAME, V759);
        assertId(0x25, PacketName.PLAY_JOIN_GAME, V760);
        assertId(0x4a, PacketName.PLAY_SPAWN_POSITION, V759);
        assertId(0x1e, PacketName.PLAY_KEEP_ALIVE, V759);
        assertId(0x2d, PacketName.PLAY_PING, V759);
        assertId(0x1f, PacketName.PLAY_CHUNK_DATA, V759);
        assertId(0x17, PacketName.PLAY_DISCONNECT, V759);
        assertId(0x22, PacketName.PLAY_UPDATE_LIGHT, V759);
        assertId(0x2c, PacketName.PLAY_OPEN_SIGN_EDITOR, V759);
        assertId(0x31, PacketName.PLAY_END_COMBAT_EVENT, V759);
        assertId(0x33, PacketName.PLAY_DEATH_COMBAT_EVENT, V759);
        assertId(0x3b, PacketName.PLAY_RESPAWN, V759);
        assertId(0x64, PacketName.PLAY_ADVANCEMENTS, V759);
        // 旧式リソースパック（プレイ状態・UUIDなし）は759・760とも。
        assertId(0x3a, PacketName.PLAY_RESOURCE_PACK, V759);
        assertId(0x3d, PacketName.PLAY_RESOURCE_PACK, V760);
        // サーバー行き：Window Click・Set Creative Slot・Update Sign。
        assertId(0x0a, PacketName.PLAY_WINDOW_CLICK, V759);
        assertId(0x2a, PacketName.PLAY_SET_CREATIVE_SLOT, V759);
        assertId(0x2d, PacketName.PLAY_UPDATE_SIGN, V759);
        // Chat Message／Chat Command は759・760でIDが違う（構造も違う）。
        assertId(0x04, PacketName.PLAY_CHAT_MESSAGE, V759);
        assertId(0x05, PacketName.PLAY_CHAT_MESSAGE, V760);
        assertId(0x03, PacketName.PLAY_CHAT_COMMAND, V759);
        assertId(0x04, PacketName.PLAY_CHAT_COMMAND, V760);
        // Message Acknowledgement は760で追加されたため759には無い。
        assertAbsent(PacketName.PLAY_MESSAGE_ACKNOWLEDGEMENT, V759);
        assertId(0x03, PacketName.PLAY_MESSAGE_ACKNOWLEDGEMENT, V760);
        // Spawn Entity／Named Entity Spawn は759・760とも同じID。
        assertId(0x00, PacketName.PLAY_SPAWN_ENTITY, V759);
        assertId(0x02, PacketName.PLAY_NAMED_ENTITY_SPAWN, V759);
        // チャット系（Player Chat／System Chat／Server Data／Trade List）は
        // 759・760でIDが違う（構造も違う）。
        assertId(0x30, PacketName.PLAY_PLAYER_CHAT, V759);
        assertId(0x33, PacketName.PLAY_PLAYER_CHAT, V760);
        assertId(0x5f, PacketName.PLAY_SYSTEM_CHAT, V759);
        assertId(0x62, PacketName.PLAY_SYSTEM_CHAT, V760);
        assertId(0x3f, PacketName.PLAY_SERVER_DATA, V759);
        assertId(0x42, PacketName.PLAY_SERVER_DATA, V760);
        assertId(0x25, PacketName.PLAY_TRADE_LIST, V759);
        assertId(0x27, PacketName.PLAY_TRADE_LIST, V760);
        // 件数：toClient 105、toServer 50（minecraft-data の 1.19 と一致）。
        assertEquals(105, PacketIds.names(State.PLAY, Direction.CLIENTBOUND, V759).size());
        assertEquals(50, PacketIds.names(State.PLAY, Direction.SERVERBOUND, V759).size());
    }

    @Test
    void chatPacketsOnlyIn760() {
        // 760にだけある4パケット（759には無い）。
        assertAbsent(PacketName.PLAY_CHAT_SUGGESTIONS, V759);
        assertId(0x15, PacketName.PLAY_CHAT_SUGGESTIONS, V760);
        assertAbsent(PacketName.PLAY_HIDE_MESSAGE, V759);
        assertId(0x18, PacketName.PLAY_HIDE_MESSAGE, V760);
        assertAbsent(PacketName.PLAY_MESSAGE_HEADER, V759);
        assertId(0x32, PacketName.PLAY_MESSAGE_HEADER, V760);
        assertAbsent(PacketName.PLAY_MESSAGE_ACKNOWLEDGEMENT, V759);
        assertId(0x03, PacketName.PLAY_MESSAGE_ACKNOWLEDGEMENT, V760);
        // 759にも760にもあるチャットプレビュー系（IDが違うものもある）。
        assertId(0x0c, PacketName.PLAY_CHAT_PREVIEW, V759);
        assertId(0x0c, PacketName.PLAY_CHAT_PREVIEW, V760);
        assertId(0x16, PacketName.PLAY_NAMED_SOUND_EFFECT, V759);
        assertId(0x17, PacketName.PLAY_NAMED_SOUND_EFFECT, V760);
        assertId(0x4b, PacketName.PLAY_SHOULD_DISPLAY_CHAT_PREVIEW, V759);
        assertId(0x4e, PacketName.PLAY_SHOULD_DISPLAY_CHAT_PREVIEW, V760);
        assertId(0x05, PacketName.PLAY_CHAT_PREVIEW_TO_SERVER, V759);
        assertId(0x06, PacketName.PLAY_CHAT_PREVIEW_TO_SERVER, V760);
    }

    @Test
    void packetsOnlyIn761() {
        // 761にあって759に無いのは7件。
        assertAbsent(PacketName.PLAY_PLAYER_INFO_REMOVE, V759);
        assertId(0x35, PacketName.PLAY_PLAYER_INFO_REMOVE, V761);
        assertAbsent(PacketName.PLAY_CHAT_SUGGESTIONS, V759);
        assertId(0x14, PacketName.PLAY_CHAT_SUGGESTIONS, V761);
        assertAbsent(PacketName.PLAY_HIDE_MESSAGE, V759);
        assertId(0x16, PacketName.PLAY_HIDE_MESSAGE, V761);
        assertAbsent(PacketName.PLAY_PROFILELESS_CHAT, V759);
        assertId(0x18, PacketName.PLAY_PROFILELESS_CHAT, V761);
        assertAbsent(PacketName.PLAY_FEATURE_FLAGS, V759);
        assertId(0x67, PacketName.PLAY_FEATURE_FLAGS, V761);
        assertAbsent(PacketName.PLAY_MESSAGE_ACKNOWLEDGEMENT, V759);
        assertId(0x03, PacketName.PLAY_MESSAGE_ACKNOWLEDGEMENT, V761);
        assertAbsent(PacketName.PLAY_CHAT_SESSION_UPDATE, V759);
        assertId(0x20, PacketName.PLAY_CHAT_SESSION_UPDATE, V761);
        // 762 で追加された 4 パケットは 759・761 ともに無い。
        assertAbsent(PacketName.PLAY_BUNDLE_DELIMITER, V759);
        assertAbsent(PacketName.PLAY_CHUNK_BIOMES, V759);
        assertAbsent(PacketName.PLAY_DAMAGE_EVENT, V759);
        assertAbsent(PacketName.PLAY_HURT_ANIMATION, V759);
    }

    @Test
    void reverseLookup759() {
        assertEquals(PacketName.PLAY_JOIN_GAME,
                PacketIds.nameFor(0x23, State.PLAY, Direction.CLIENTBOUND, V759).orElseThrow());
        assertEquals(PacketName.PLAY_CHUNK_DATA,
                PacketIds.nameFor(0x1f, State.PLAY, Direction.CLIENTBOUND, V759).orElseThrow());
        assertEquals(PacketName.PLAY_RESOURCE_PACK,
                PacketIds.nameFor(0x3a, State.PLAY, Direction.CLIENTBOUND, V759).orElseThrow());
        assertEquals(PacketName.PLAY_WINDOW_CLICK,
                PacketIds.nameFor(0x0a, State.PLAY, Direction.SERVERBOUND, V759).orElseThrow());
        assertEquals(PacketName.PLAY_SET_CREATIVE_SLOT,
                PacketIds.nameFor(0x2a, State.PLAY, Direction.SERVERBOUND, V759).orElseThrow());
        assertEquals(PacketName.PLAY_UPDATE_SIGN,
                PacketIds.nameFor(0x2d, State.PLAY, Direction.SERVERBOUND, V759).orElseThrow());
        assertEquals(PacketName.PLAY_NAMED_ENTITY_SPAWN,
                PacketIds.nameFor(0x02, State.PLAY, Direction.CLIENTBOUND, V759).orElseThrow());
        assertEquals(PacketName.PLAY_CHAT_PREVIEW,
                PacketIds.nameFor(0x0c, State.PLAY, Direction.CLIENTBOUND, V759).orElseThrow());
        assertEquals(PacketName.PLAY_NAMED_SOUND_EFFECT,
                PacketIds.nameFor(0x16, State.PLAY, Direction.CLIENTBOUND, V759).orElseThrow());
        // 設定フェーズのIDは759では何にも写らない。
        assertTrue(PacketIds.nameFor(0x07, State.CONFIGURATION, Direction.CLIENTBOUND, V759)
                .isEmpty());
    }

    @Test
    void legacyPingAndPingRequestNotInTable() {
        // 従来通り、legacy_server_list_ping（0xFE）と play の ping_request
        // は表に入れない（プロキシが落とす方針のため）。
        assertFalse(PacketIds.names(State.PLAY, Direction.SERVERBOUND, V759).stream()
                .anyMatch(n -> n.name().contains("PING_REQUEST")));
        assertFalse(PacketIds.names(State.HANDSHAKING, Direction.SERVERBOUND, V759).stream()
                .anyMatch(n -> n.name().contains("LEGACY")));
    }
}
