package io.detergentmc.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.detergentmc.protocol.PacketIds.PacketName;
import java.util.List;
import java.util.OptionalInt;
import org.junit.jupiter.api.Test;

/**
 * 480（1.14.1）のパケットID表。480 列は 1.14.1 自身の protocol.json
 * （minecraft-data pc/1.14.1）から packet-ids-480.py で全281エントリを
 * 機械生成し、485 列と1件も違わないことを確認済み。実バニラ 1.14.1
 * jar の SHA-1 は ed76d597a44c5266be2a7fcd77a8270f1f0bc118。
 * 表に無い唯一の 480 パケットは legacy_server_list_ping（0xfe。
 * 旧式 ping で、どのバージョンでも表管理していない）。
 */
class PacketIds480Test {

    private static final int V480 = ProtocolVersion.V1_14_1;

    @Test
    void tableKnows480() {
        assertTrue(PacketIds.isSupported(V480));
        assertEquals(List.of(335, 338, 340, 393, 401, 404, 477, 480, 485, 490, 498, 573, 575, 578, 758, 759, 760, 761, 762, 763, 764, 765, 766, 767, 768, 769, 770, 771, 772, 773, 774, 775, 776),
                PacketIds.supportedProtocols());
    }

    @Test
    void presentCount480Is147() {
        int count480 = 0;
        int count485 = 0;
        for (PacketName name : PacketName.values()) {
            if (name.id480() != -1) {
                count480++;
            }
            if (name.id485() != -1) {
                count485++;
            }
        }
        assertEquals(147, count480);
        assertEquals(147, count485);
    }

    @Test
    void noDifferenceAgainst485() {
        // Stage A の実データ照合どおり、480 と 485 の ID は全エントリ一致。
        int differing = 0;
        for (PacketName name : PacketName.values()) {
            if (name.id480() != name.id485()) {
                differing++;
            }
        }
        assertEquals(0, differing);
    }

    @Test
    void keyPlayIdsAreThe1141Values() {
        assertEquals(OptionalInt.of(0x25), PacketIds.id(PacketName.PLAY_JOIN_GAME, V480));
        assertEquals(OptionalInt.of(0x21), PacketIds.id(PacketName.PLAY_CHUNK_DATA, V480));
        assertEquals(OptionalInt.of(0x20), PacketIds.id(PacketName.PLAY_KEEP_ALIVE, V480));
        assertEquals(OptionalInt.of(0x0e), PacketIds.id(PacketName.PLAY_CHAT, V480));
        assertEquals(OptionalInt.of(0x27), PacketIds.id(PacketName.PLAY_TRADE_LIST, V480));
        assertEquals(OptionalInt.of(0x03),
                PacketIds.id(PacketName.PLAY_SPAWN_ENTITY_LIVING, V480));
        assertEquals(OptionalInt.of(0x03), PacketIds.id(PacketName.PLAY_CHAT_TO_SERVER, V480));
        // acknowledge_player_digging は 1.14.4（498）で追加。480 には無い。
        assertEquals(OptionalInt.empty(),
                PacketIds.id(PacketName.PLAY_ACKNOWLEDGE_PLAYER_DIGGING, V480));
        assertEquals(OptionalInt.of(0x5c),
                PacketIds.id(PacketName.PLAY_ACKNOWLEDGE_PLAYER_DIGGING,
                        ProtocolVersion.V1_14_4));
        assertEquals(OptionalInt.empty(), PacketIds.id(PacketName.CONFIG_REGISTRY_DATA, V480));
        assertEquals(OptionalInt.empty(), PacketIds.id(PacketName.PLAY_PLAYER_CHAT, V480));
    }
}
