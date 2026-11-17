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
 * {@link PacketIds} の758（1.18.2）向けスポットチェック。
 * 期待値は PrismarineJS minecraft-data の protocol.json（1.18.2）と
 * tools/packet-ids-758.py の出力による。
 * 759と比べ、パケットIDが違うものは123件（存在する161件のうち123件）。
 * 758に無いものは111件（表全体で -1 のもの。うち759にもあるのは
 * 署名付きチャット系の8件だけ。残りは設定フェーズと760以降の追加分）。
 * 758にだけあるものは5件（旧式チャット・旧式出現3種・スカルク振動）。
 * 758にあって表に無かったのは legacy_server_list_ping（0xFE。従来通り
 * 表に入れない）だけ。
 */
class PacketIds758Test {

    private static final int V758 = ProtocolVersion.V1_18_2;
    private static final int V759 = ProtocolVersion.V1_19;
    private static final int V760 = ProtocolVersion.V1_19_1;

    private static void assertId(int expected, PacketName name, int protocol) {
        assertEquals(OptionalInt.of(expected), PacketIds.id(name, protocol),
                name + " for protocol " + protocol);
    }

    private static void assertAbsent(PacketName name, int protocol) {
        assertEquals(OptionalInt.empty(), PacketIds.id(name, protocol),
                name + " は " + protocol + " に存在しないこと");
    }

    @Test
    void tableKnows758() {
        assertTrue(PacketIds.isSupported(V758));
        assertEquals(List.of(480, 485, 490, 498, 573, 575, 578, 758, 759, 760, 761, 762, 763, 764, 765, 766, 767, 768, 769, 770, 771, 772, 773, 774, 775, 776),
                PacketIds.supportedProtocols());
    }

    @Test
    void idsChangedFrom759() {
        // packet-ids-758.py：267エントリすべてが一意に写像でき、759とIDが
        // 違うものは123件（両方に存在するもの同士の比較）、758で存在する
        // （-1 でない）ものは161件、758にだけあるものは5件。
        // 758に存在する161件（play toClient 104・toServer 48・
        // login toClient 5・toServer 3・handshaking 1）のうち、759とIDが
        // 同じものは33件だけ（161 - 123 - 5）。
        int changed = 0;
        int present = 0;
        int added = 0;
        for (PacketName name : PacketName.values()) {
            if (name.id758() != -1) {
                present++;
                if (name.id759() == -1) {
                    added++;
                } else if (name.id758() != name.id759()) {
                    changed++;
                }
            }
        }
        assertEquals(123, changed);
        assertEquals(161, present);
        assertEquals(5, added);
        assertEquals(161, PacketIds.names(State.PLAY, Direction.CLIENTBOUND, V758).size()
                + PacketIds.names(State.PLAY, Direction.SERVERBOUND, V758).size()
                + PacketIds.names(State.LOGIN, Direction.CLIENTBOUND, V758).size()
                + PacketIds.names(State.LOGIN, Direction.SERVERBOUND, V758).size()
                + PacketIds.names(State.HANDSHAKING, Direction.SERVERBOUND, V758).size());
    }

    @Test
    void handshakeAndLogin() {
        // ハンドシェイク・ログインのIDは759と同じ。
        assertId(0x00, PacketName.HANDSHAKE, V758);
        assertId(0x00, PacketName.LOGIN_DISCONNECT, V758);
        assertId(0x01, PacketName.LOGIN_ENCRYPTION_REQUEST, V758);
        assertId(0x02, PacketName.LOGIN_SUCCESS, V758);
        assertId(0x03, PacketName.LOGIN_SET_COMPRESSION, V758);
        assertId(0x04, PacketName.LOGIN_PLUGIN_REQUEST, V758);
        // Cookie は設定フェーズと共に1.20.2で追加されたため758には無い。
        assertAbsent(PacketName.LOGIN_COOKIE_REQUEST, V758);
        assertAbsent(PacketName.LOGIN_COOKIE_RESPONSE, V758);
        // Login Acknowledged は1.20.2で追加されたため758には無い。
        assertAbsent(PacketName.LOGIN_ACKNOWLEDGED, V758);
    }

    @Test
    void configurationPhaseDoesNotExistIn758() {
        // 758（1.18.2）には設定フェーズそのものが無い（1.20.2で追加）。
        // 設定フェーズのパケットはすべて -1。
        assertEquals(0, PacketIds.names(State.CONFIGURATION, Direction.CLIENTBOUND, V758).size());
        assertEquals(0, PacketIds.names(State.CONFIGURATION, Direction.SERVERBOUND, V758).size());
        assertAbsent(PacketName.CONFIG_REGISTRY_DATA, V758);
        assertAbsent(PacketName.CONFIG_FINISH_CONFIGURATION, V758);
        assertAbsent(PacketName.CONFIG_ADD_RESOURCE_PACK, V758);
    }

    @Test
    void playIds() {
        // 759との差分の代表値（packet-ids-758.py の出力）。
        assertId(0x26, PacketName.PLAY_JOIN_GAME, V758);
        assertId(0x23, PacketName.PLAY_JOIN_GAME, V759);
        assertId(0x4b, PacketName.PLAY_SPAWN_POSITION, V758);
        assertId(0x21, PacketName.PLAY_KEEP_ALIVE, V758);
        assertId(0x30, PacketName.PLAY_PING, V758);
        assertId(0x22, PacketName.PLAY_CHUNK_DATA, V758);
        assertId(0x1a, PacketName.PLAY_DISCONNECT, V758);
        assertId(0x25, PacketName.PLAY_UPDATE_LIGHT, V758);
        assertId(0x2f, PacketName.PLAY_OPEN_SIGN_EDITOR, V758);
        assertId(0x33, PacketName.PLAY_END_COMBAT_EVENT, V758);
        assertId(0x35, PacketName.PLAY_DEATH_COMBAT_EVENT, V758);
        assertId(0x3d, PacketName.PLAY_RESPAWN, V758);
        assertId(0x63, PacketName.PLAY_ADVANCEMENTS, V758);
        // 旧式リソースパック（プレイ状態・UUIDなし）は758・759とも。
        assertId(0x3c, PacketName.PLAY_RESOURCE_PACK, V758);
        assertId(0x3a, PacketName.PLAY_RESOURCE_PACK, V759);
        // サーバー行き：Window Click・Set Creative Slot・Update Sign。
        assertId(0x08, PacketName.PLAY_WINDOW_CLICK, V758);
        assertId(0x28, PacketName.PLAY_SET_CREATIVE_SLOT, V758);
        assertId(0x2b, PacketName.PLAY_UPDATE_SIGN, V758);
        // 出現は758では4種に分かれている（759では spawn_entity に統合）。
        assertId(0x00, PacketName.PLAY_SPAWN_ENTITY, V758);
        assertId(0x02, PacketName.PLAY_SPAWN_ENTITY_LIVING, V758);
        assertId(0x03, PacketName.PLAY_SPAWN_ENTITY_PAINTING, V758);
        assertId(0x04, PacketName.PLAY_NAMED_ENTITY_SPAWN, V758);
        assertId(0x02, PacketName.PLAY_NAMED_ENTITY_SPAWN, V759);
        // Trade List・Named Sound Effect。
        assertId(0x28, PacketName.PLAY_TRADE_LIST, V758);
        assertId(0x19, PacketName.PLAY_NAMED_SOUND_EFFECT, V758);
        // 件数：toClient 104、toServer 48（minecraft-data の 1.18.2 と一致）。
        assertEquals(104, PacketIds.names(State.PLAY, Direction.CLIENTBOUND, V758).size());
        assertEquals(48, PacketIds.names(State.PLAY, Direction.SERVERBOUND, V758).size());
    }

    @Test
    void chatPacketsOnlyIn758() {
        // 758にだけある5パケット（759には無い）。
        // 旧式チャット（署名なし。署名付きチャットは1.19で導入）。
        assertId(0x0f, PacketName.PLAY_CHAT, V758);
        assertAbsent(PacketName.PLAY_CHAT, V759);
        assertId(0x03, PacketName.PLAY_CHAT_TO_SERVER, V758);
        assertAbsent(PacketName.PLAY_CHAT_TO_SERVER, V759);
        // 旧式出現3種とスカルク振動（1.19で統合・削除）。
        assertId(0x02, PacketName.PLAY_SPAWN_ENTITY_LIVING, V758);
        assertAbsent(PacketName.PLAY_SPAWN_ENTITY_LIVING, V759);
        assertId(0x03, PacketName.PLAY_SPAWN_ENTITY_PAINTING, V758);
        assertAbsent(PacketName.PLAY_SPAWN_ENTITY_PAINTING, V759);
        assertId(0x05, PacketName.PLAY_SCULK_VIBRATION_SIGNAL, V758);
        assertAbsent(PacketName.PLAY_SCULK_VIBRATION_SIGNAL, V759);
        // 逆に759にあって758に無いのは署名付きチャット系の8件だけ。
        assertAbsent(PacketName.PLAY_PLAYER_CHAT, V758);
        assertId(0x30, PacketName.PLAY_PLAYER_CHAT, V759);
        assertAbsent(PacketName.PLAY_SYSTEM_CHAT, V758);
        assertId(0x5f, PacketName.PLAY_SYSTEM_CHAT, V759);
        assertAbsent(PacketName.PLAY_SERVER_DATA, V758);
        assertId(0x3f, PacketName.PLAY_SERVER_DATA, V759);
        assertAbsent(PacketName.PLAY_CHAT_PREVIEW, V758);
        assertId(0x0c, PacketName.PLAY_CHAT_PREVIEW, V759);
        assertAbsent(PacketName.PLAY_CHAT_MESSAGE, V758);
        assertId(0x04, PacketName.PLAY_CHAT_MESSAGE, V759);
        assertAbsent(PacketName.PLAY_CHAT_COMMAND, V758);
        assertId(0x03, PacketName.PLAY_CHAT_COMMAND, V759);
        assertAbsent(PacketName.PLAY_CHAT_PREVIEW_TO_SERVER, V758);
        assertId(0x05, PacketName.PLAY_CHAT_PREVIEW_TO_SERVER, V759);
        assertAbsent(PacketName.PLAY_SHOULD_DISPLAY_CHAT_PREVIEW, V758);
        assertId(0x4b, PacketName.PLAY_SHOULD_DISPLAY_CHAT_PREVIEW, V759);
    }

    @Test
    void reverseLookup758() {
        assertEquals(PacketName.PLAY_JOIN_GAME,
                PacketIds.nameFor(0x26, State.PLAY, Direction.CLIENTBOUND, V758).orElseThrow());
        assertEquals(PacketName.PLAY_CHUNK_DATA,
                PacketIds.nameFor(0x22, State.PLAY, Direction.CLIENTBOUND, V758).orElseThrow());
        assertEquals(PacketName.PLAY_RESOURCE_PACK,
                PacketIds.nameFor(0x3c, State.PLAY, Direction.CLIENTBOUND, V758).orElseThrow());
        assertEquals(PacketName.PLAY_WINDOW_CLICK,
                PacketIds.nameFor(0x08, State.PLAY, Direction.SERVERBOUND, V758).orElseThrow());
        assertEquals(PacketName.PLAY_SET_CREATIVE_SLOT,
                PacketIds.nameFor(0x28, State.PLAY, Direction.SERVERBOUND, V758).orElseThrow());
        assertEquals(PacketName.PLAY_UPDATE_SIGN,
                PacketIds.nameFor(0x2b, State.PLAY, Direction.SERVERBOUND, V758).orElseThrow());
        assertEquals(PacketName.PLAY_CHAT,
                PacketIds.nameFor(0x0f, State.PLAY, Direction.CLIENTBOUND, V758).orElseThrow());
        assertEquals(PacketName.PLAY_SPAWN_ENTITY_LIVING,
                PacketIds.nameFor(0x02, State.PLAY, Direction.CLIENTBOUND, V758).orElseThrow());
        // 設定フェーズのIDは758では何にも写らない。
        assertTrue(PacketIds.nameFor(0x07, State.CONFIGURATION, Direction.CLIENTBOUND, V758)
                .isEmpty());
    }

    @Test
    void legacyPingAndPingRequestNotInTable() {
        // 従来通り、legacy_server_list_ping（0xFE）と play の ping_request
        // は表に入れない（プロキシが落とす方針のため）。
        assertFalse(PacketIds.names(State.PLAY, Direction.SERVERBOUND, V758).stream()
                .anyMatch(n -> n.name().contains("PING_REQUEST")));
        assertFalse(PacketIds.names(State.HANDSHAKING, Direction.SERVERBOUND, V758).stream()
                .anyMatch(n -> n.name().contains("LEGACY")));
    }
}
