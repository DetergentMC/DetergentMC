package io.detergentmc.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.detergentmc.protocol.PacketIds.PacketName;
import java.util.List;
import java.util.OptionalInt;
import org.junit.jupiter.api.Test;

/**
 * 477（1.14）のパケットID表。477 列は 1.14 自身の protocol.json
 * （minecraft-data pc/1.14。1.14.1 のものとバイト一致）から
 * packet-ids-477.py で全281エントリを機械生成し、480 列と1件も
 * 違わないことを確認済み。実バニラ 1.14
 * jar の SHA-1 は f1a0073671057f01aa843443fef34330281333ce。
 * 表に無い唯一の 477 パケットは legacy_server_list_ping（0xfe。
 * 旧式 ping で、どのバージョンでも表管理していない）。
 */
class PacketIds477Test {

    private static final int V477 = ProtocolVersion.V1_14;

    @Test
    void tableKnows477() {
        assertTrue(PacketIds.isSupported(V477));
        assertEquals(List.of(335, 338, 340, 393, 401, 404, 477, 480, 485, 490, 498, 573, 575, 578, 758, 759, 760, 761, 762, 763, 764, 765, 766, 767, 768, 769, 770, 771, 772, 773, 774, 775, 776),
                PacketIds.supportedProtocols());
    }

    @Test
    void presentCount477Is147() {
        int count477 = 0;
        int count480 = 0;
        for (PacketName name : PacketName.values()) {
            if (name.id477() != -1) {
                count477++;
            }
            if (name.id480() != -1) {
                count480++;
            }
        }
        assertEquals(147, count477);
        assertEquals(147, count480);
    }

    @Test
    void noDifferenceAgainst480() {
        // Stage A の実データ照合どおり、477 と 480 の ID は全エントリ一致。
        int differing = 0;
        for (PacketName name : PacketName.values()) {
            if (name.id477() != name.id480()) {
                differing++;
            }
        }
        assertEquals(0, differing);
    }

    @Test
    void keyPlayIdsAreThe114Values() {
        assertEquals(OptionalInt.of(0x25), PacketIds.id(PacketName.PLAY_JOIN_GAME, V477));
        assertEquals(OptionalInt.of(0x21), PacketIds.id(PacketName.PLAY_CHUNK_DATA, V477));
        assertEquals(OptionalInt.of(0x20), PacketIds.id(PacketName.PLAY_KEEP_ALIVE, V477));
        assertEquals(OptionalInt.of(0x0e), PacketIds.id(PacketName.PLAY_CHAT, V477));
        assertEquals(OptionalInt.of(0x27), PacketIds.id(PacketName.PLAY_TRADE_LIST, V477));
        assertEquals(OptionalInt.of(0x03),
                PacketIds.id(PacketName.PLAY_SPAWN_ENTITY_LIVING, V477));
        assertEquals(OptionalInt.of(0x03), PacketIds.id(PacketName.PLAY_CHAT_TO_SERVER, V477));
        // acknowledge_player_digging は 1.14.4（498）で追加。477 には無い。
        assertEquals(OptionalInt.empty(),
                PacketIds.id(PacketName.PLAY_ACKNOWLEDGE_PLAYER_DIGGING, V477));
        assertEquals(OptionalInt.of(0x5c),
                PacketIds.id(PacketName.PLAY_ACKNOWLEDGE_PLAYER_DIGGING,
                        ProtocolVersion.V1_14_4));
        assertEquals(OptionalInt.empty(), PacketIds.id(PacketName.CONFIG_REGISTRY_DATA, V477));
        assertEquals(OptionalInt.empty(), PacketIds.id(PacketName.PLAY_PLAYER_CHAT, V477));
    }
}
