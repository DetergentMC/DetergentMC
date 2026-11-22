package io.detergentmc.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.detergentmc.protocol.PacketIds.PacketName;
import java.util.List;
import java.util.OptionalInt;
import org.junit.jupiter.api.Test;

/**
 * {@link PacketIds} の490（1.14.3）向けチェック。
 * 期待値の根拠は Stage A の実データ：実バニラ 1.14.3 jar
 * （SHA-1 d0d0fe2b1dc6ab4c65554cb734270872b72dadd6）と
 * minecraft-data 1.14.3 の protocol.json を突き合わせた
 * tools/packet-ids-490.py の出力（全281件を一意に対応付け済み）。
 * 498 との差分は acknowledge_player_digging が 490 に無いことの
 * 1件だけ（498 では 0x5c）。他の147件は ID が完全一致する。
 */
class PacketIds490Test {

    private static final int V490 = ProtocolVersion.V1_14_3;
    private static final int V498 = ProtocolVersion.V1_14_4;

    @Test
    void tableKnows490() {
        assertTrue(PacketIds.isSupported(V490));
        assertEquals(List.of(4, 5, 47, 107, 108, 109, 110, 210, 315, 316, 335, 338, 340, 393, 401, 404, 477, 480, 485, 490, 498, 573, 575, 578, 735, 736, 751, 753, 754, 755, 756, 757, 758, 759, 760, 761, 762, 763, 764, 765, 766, 767, 768, 769, 770, 771, 772, 773, 774, 775, 776),
                PacketIds.supportedProtocols());
    }

    @Test
    void presentCountIs147() {
        int present = 0;
        for (PacketName name : PacketName.values()) {
            if (name.id490() != -1) {
                present++;
            }
        }
        assertEquals(147, present);
    }

    @Test
    void onlyDifferenceAgainst498IsAcknowledgePlayerDigging() {
        int differing = 0;
        for (PacketName name : PacketName.values()) {
            if (name.id490() != name.id498()) {
                differing++;
                assertEquals(PacketName.PLAY_ACKNOWLEDGE_PLAYER_DIGGING, name);
                assertEquals(PacketIds.Direction.CLIENTBOUND, name.direction());
                assertEquals(-1, name.id490());
                assertEquals(0x5c, name.id498());
            }
        }
        assertEquals(1, differing);
    }

    @Test
    void keyPlayIds() {
        assertEquals(OptionalInt.of(0x25), PacketIds.id(PacketName.PLAY_JOIN_GAME, V490));
        assertEquals(OptionalInt.of(0x21), PacketIds.id(PacketName.PLAY_CHUNK_DATA, V490));
        assertEquals(OptionalInt.of(0x20), PacketIds.id(PacketName.PLAY_KEEP_ALIVE, V490));
        assertEquals(OptionalInt.of(0x0e), PacketIds.id(PacketName.PLAY_CHAT, V490));
        assertEquals(OptionalInt.of(0x27), PacketIds.id(PacketName.PLAY_TRADE_LIST, V490));
        assertEquals(OptionalInt.of(0x03), PacketIds.id(PacketName.PLAY_SPAWN_ENTITY_LIVING, V490));
        assertEquals(OptionalInt.of(0x03), PacketIds.id(PacketName.PLAY_CHAT_TO_SERVER, V490));
        // 490 には掘削 ack が無い。498 には 0x5c で存在する。
        assertEquals(OptionalInt.empty(),
                PacketIds.id(PacketName.PLAY_ACKNOWLEDGE_PLAYER_DIGGING, V490));
        assertEquals(OptionalInt.of(0x5c),
                PacketIds.id(PacketName.PLAY_ACKNOWLEDGE_PLAYER_DIGGING, V498));
        assertEquals(OptionalInt.empty(), PacketIds.id(PacketName.CONFIG_REGISTRY_DATA, V490));
        assertEquals(OptionalInt.empty(), PacketIds.id(PacketName.PLAY_PLAYER_CHAT, V490));
    }
}
