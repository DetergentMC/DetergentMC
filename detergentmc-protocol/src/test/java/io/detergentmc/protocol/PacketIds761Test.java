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
 * {@link PacketIds} の761（1.19.3）向けスポットチェック。
 * 期待値は PrismarineJS minecraft-data の protocol.json（1.19.3）と
 * tools/packet-ids-761.py の出力による。
 * 762と比べ、パケットIDが違うものは134件（既存255件のうち132件＋
 * 新規エントリ2件）。761に無いものは90件
 * （設定フェーズのパケットと764以降の追加パケット）。
 * 761にあって表に無かったのは legacy_server_list_ping（0xFE。従来通り
 * 表に入れない）のほか、Named Entity Spawn（0x02）と Feature Flags
 * （0x67）の2件で、この2件は新規エントリとして表に追加した。
 */
class PacketIds761Test {

    private static final int V761 = ProtocolVersion.V1_19_3;
    private static final int V762 = ProtocolVersion.V1_19_4;
    private static final int V764 = ProtocolVersion.V1_20_2;

    private static void assertId(int expected, PacketName name, int protocol) {
        assertEquals(OptionalInt.of(expected), PacketIds.id(name, protocol),
                name + " for protocol " + protocol);
    }

    private static void assertAbsent(PacketName name, int protocol) {
        assertEquals(OptionalInt.empty(), PacketIds.id(name, protocol),
                name + " は " + protocol + " に存在しないこと");
    }

    @Test
    void tableKnows761() {
        assertTrue(PacketIds.isSupported(V761));
        assertEquals(List.of(759, 760, 761, 762, 763, 764, 765, 766, 767, 768, 769, 770, 771, 772, 773, 774, 775),
                PacketIds.supportedProtocols());
    }

    @Test
    void idsChangedFrom762() {
        // packet-ids-761.py：255エントリすべてが一意に写像でき、762とIDが
        // 違うものは132件、761で存在しない（-1）は90件。さらに新規エントリ
        // 2件（Named Entity Spawn・Feature Flags）も762とIDが違うため、
        // 762とIDが違うものは合計134件。
        // 761に存在する167件（play toClient 107・toServer 51・
        // login toClient 5・toServer 3・handshaking 1）のうち、762とIDが
        // 同じものは33件だけ。
        int changed = 0;
        int present = 0;
        for (PacketName name : PacketName.values()) {
            if (name.id761() != -1) {
                present++;
                if (name.id761() != name.id762()) {
                    changed++;
                }
            }
        }
        assertEquals(134, changed);
        assertEquals(167, present);
        assertEquals(167, PacketIds.names(State.PLAY, Direction.CLIENTBOUND, V761).size()
                + PacketIds.names(State.PLAY, Direction.SERVERBOUND, V761).size()
                + PacketIds.names(State.LOGIN, Direction.CLIENTBOUND, V761).size()
                + PacketIds.names(State.LOGIN, Direction.SERVERBOUND, V761).size()
                + PacketIds.names(State.HANDSHAKING, Direction.SERVERBOUND, V761).size());
    }

    @Test
    void handshakeAndLogin() {
        assertId(0x00, PacketName.HANDSHAKE, V761);
        assertId(0x00, PacketName.LOGIN_DISCONNECT, V761);
        assertId(0x01, PacketName.LOGIN_ENCRYPTION_REQUEST, V761);
        // Login Success のIDは762と同じ（0x02）。Login Start も同じ。
        assertId(0x02, PacketName.LOGIN_SUCCESS, V761);
        assertId(0x03, PacketName.LOGIN_SET_COMPRESSION, V761);
        assertId(0x04, PacketName.LOGIN_PLUGIN_REQUEST, V761);
        // Cookie は設定フェーズと共に1.20.2で追加されたため761には無い。
        assertAbsent(PacketName.LOGIN_COOKIE_REQUEST, V761);
        assertAbsent(PacketName.LOGIN_COOKIE_RESPONSE, V761);
    }

    @Test
    void configurationPhaseDoesNotExistIn761() {
        // 761（1.19.3）には設定フェーズそのものが無い（1.20.2で追加）。
        // 設定フェーズのパケットはすべて -1。
        assertEquals(0, PacketIds.names(State.CONFIGURATION, Direction.CLIENTBOUND, V761).size());
        assertEquals(0, PacketIds.names(State.CONFIGURATION, Direction.SERVERBOUND, V761).size());
        assertAbsent(PacketName.CONFIG_REGISTRY_DATA, V761);
        assertAbsent(PacketName.CONFIG_FINISH_CONFIGURATION, V761);
        assertAbsent(PacketName.CONFIG_ADD_RESOURCE_PACK, V761);
    }

    @Test
    void playIds() {
        // 762との差分の代表値（packet-ids-761.py の出力）。
        assertId(0x24, PacketName.PLAY_JOIN_GAME, V761);
        assertId(0x28, PacketName.PLAY_JOIN_GAME, V762);
        assertId(0x4c, PacketName.PLAY_SPAWN_POSITION, V761);
        assertId(0x1f, PacketName.PLAY_KEEP_ALIVE, V761);
        assertId(0x2e, PacketName.PLAY_PING, V761);
        assertId(0x20, PacketName.PLAY_CHUNK_DATA, V761);
        assertId(0x17, PacketName.PLAY_DISCONNECT, V761);
        assertId(0x23, PacketName.PLAY_UPDATE_LIGHT, V761);
        assertId(0x2d, PacketName.PLAY_OPEN_SIGN_EDITOR, V761);
        assertId(0x32, PacketName.PLAY_END_COMBAT_EVENT, V761);
        assertId(0x34, PacketName.PLAY_DEATH_COMBAT_EVENT, V761);
        assertId(0x3d, PacketName.PLAY_RESPAWN, V761);
        assertId(0x65, PacketName.PLAY_ADVANCEMENTS, V761);
        // 旧式リソースパック（プレイ状態・UUIDなし）は761・762とも。
        assertId(0x3c, PacketName.PLAY_RESOURCE_PACK, V761);
        assertId(0x40, PacketName.PLAY_RESOURCE_PACK, V762);
        // サーバー行き：Window Click・Set Creative Slot・Update Sign。
        assertId(0x0a, PacketName.PLAY_WINDOW_CLICK, V761);
        assertId(0x2b, PacketName.PLAY_SET_CREATIVE_SLOT, V761);
        assertId(0x2e, PacketName.PLAY_UPDATE_SIGN, V761);
        // Chat Session Update は 761 では 0x20、762 では 0x06 へ移動した。
        assertId(0x20, PacketName.PLAY_CHAT_SESSION_UPDATE, V761);
        assertId(0x06, PacketName.PLAY_CHAT_SESSION_UPDATE, V762);
        // Spawn Entity は 761 では 0x00、762 では 0x01。
        assertId(0x00, PacketName.PLAY_SPAWN_ENTITY, V761);
        assertId(0x01, PacketName.PLAY_SPAWN_ENTITY, V762);
        // 件数：toClient 107、toServer 51（minecraft-data の 1.19.3 と一致）。
        assertEquals(107, PacketIds.names(State.PLAY, Direction.CLIENTBOUND, V761).size());
        assertEquals(51, PacketIds.names(State.PLAY, Direction.SERVERBOUND, V761).size());
    }

    @Test
    void namedEntitySpawnAndFeatureFlagsOnlyUpTo763() {
        // Named Entity Spawn は 761〜763 にだけある（プレイヤーの出現）。
        // 764 以降は Spawn Entity へ統合された。
        assertId(0x02, PacketName.PLAY_NAMED_ENTITY_SPAWN, V761);
        assertId(0x03, PacketName.PLAY_NAMED_ENTITY_SPAWN, V762);
        assertId(0x03, PacketName.PLAY_NAMED_ENTITY_SPAWN, ProtocolVersion.V1_20_1);
        assertAbsent(PacketName.PLAY_NAMED_ENTITY_SPAWN, V764);
        // Feature Flags は 761〜763 はプレイ状態、764 以降は設定フェーズ。
        assertId(0x67, PacketName.PLAY_FEATURE_FLAGS, V761);
        assertId(0x6b, PacketName.PLAY_FEATURE_FLAGS, V762);
        assertId(0x6b, PacketName.PLAY_FEATURE_FLAGS, ProtocolVersion.V1_20_1);
        assertAbsent(PacketName.PLAY_FEATURE_FLAGS, V764);
        // 762 で追加された 4 パケットは 761 には無い。
        assertAbsent(PacketName.PLAY_BUNDLE_DELIMITER, V761);
        assertAbsent(PacketName.PLAY_CHUNK_BIOMES, V761);
        assertAbsent(PacketName.PLAY_DAMAGE_EVENT, V761);
        assertAbsent(PacketName.PLAY_HURT_ANIMATION, V761);
    }

    @Test
    void reverseLookup761() {
        assertEquals(PacketName.PLAY_JOIN_GAME,
                PacketIds.nameFor(0x24, State.PLAY, Direction.CLIENTBOUND, V761).orElseThrow());
        assertEquals(PacketName.PLAY_CHUNK_DATA,
                PacketIds.nameFor(0x20, State.PLAY, Direction.CLIENTBOUND, V761).orElseThrow());
        assertEquals(PacketName.PLAY_RESOURCE_PACK,
                PacketIds.nameFor(0x3c, State.PLAY, Direction.CLIENTBOUND, V761).orElseThrow());
        assertEquals(PacketName.PLAY_WINDOW_CLICK,
                PacketIds.nameFor(0x0a, State.PLAY, Direction.SERVERBOUND, V761).orElseThrow());
        assertEquals(PacketName.PLAY_SET_CREATIVE_SLOT,
                PacketIds.nameFor(0x2b, State.PLAY, Direction.SERVERBOUND, V761).orElseThrow());
        assertEquals(PacketName.PLAY_UPDATE_SIGN,
                PacketIds.nameFor(0x2e, State.PLAY, Direction.SERVERBOUND, V761).orElseThrow());
        assertEquals(PacketName.PLAY_NAMED_ENTITY_SPAWN,
                PacketIds.nameFor(0x02, State.PLAY, Direction.CLIENTBOUND, V761).orElseThrow());
        assertEquals(PacketName.PLAY_FEATURE_FLAGS,
                PacketIds.nameFor(0x67, State.PLAY, Direction.CLIENTBOUND, V761).orElseThrow());
        // 設定フェーズのIDは761では何にも写らない。
        assertTrue(PacketIds.nameFor(0x07, State.CONFIGURATION, Direction.CLIENTBOUND, V761)
                .isEmpty());
    }

    @Test
    void legacyPingAndPingRequestNotInTable() {
        // 従来通り、legacy_server_list_ping（0xFE）と play の ping_request
        // は表に入れない（プロキシが落とす方針のため）。
        assertFalse(PacketIds.names(State.PLAY, Direction.SERVERBOUND, V761).stream()
                .anyMatch(n -> n.name().contains("PING_REQUEST")));
        assertFalse(PacketIds.names(State.HANDSHAKING, Direction.SERVERBOUND, V761).stream()
                .anyMatch(n -> n.name().contains("LEGACY")));
    }
}
