package io.detergentmc.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.detergentmc.protocol.PacketIds.PacketName;
import java.util.List;
import java.util.OptionalInt;
import org.junit.jupiter.api.Test;

/**
 * {@link PacketIds} の485（1.14.2）向けチェック。
 * 期待値の根拠は Stage A の実データ：実バニラ 1.14.2 jar
 * （SHA-1 808be3869e2ca6b62378f9f4b33c946621620019）と、
 * minecraft-data に 1.14.2 が無いため 1.14.1 の protocol.json・
 * ViaVersion の 1.14.1→1.14.2 空プロトコルを突き合わせた
 * tools/packet-ids-485.py の出力（全281件を一意に対応付け済み）。
 * 490 とのパケットID差分は 0 件（全件一致）。485 に存在するのは
 * 147 件で、掘削 ack は 485 にも 490 にも無い（498 で追加）。
 */
class PacketIds485Test {

    private static final int V485 = ProtocolVersion.V1_14_2;
    private static final int V490 = ProtocolVersion.V1_14_3;

    @Test
    void tableKnows485() {
        assertTrue(PacketIds.isSupported(V485));
        assertEquals(List.of(335, 338, 340, 393, 401, 404, 477, 480, 485, 490, 498, 573, 575, 578, 758, 759, 760, 761, 762, 763, 764, 765, 766, 767, 768, 769, 770, 771, 772, 773, 774, 775, 776),
                PacketIds.supportedProtocols());
    }

    @Test
    void presentCountIs147() {
        int present = 0;
        for (PacketName name : PacketName.values()) {
            if (name.id485() != -1) {
                present++;
            }
        }
        assertEquals(147, present);
    }

    @Test
    void noDifferenceAgainst490() {
        // 485 と 490 のパケットIDは全件一致する（実データで確定した差分0件）。
        int differing = 0;
        for (PacketName name : PacketName.values()) {
            if (name.id485() != name.id490()) {
                differing++;
            }
        }
        assertEquals(0, differing);
    }

    @Test
    void keyPlayIds() {
        assertEquals(OptionalInt.of(0x25), PacketIds.id(PacketName.PLAY_JOIN_GAME, V485));
        assertEquals(OptionalInt.of(0x21), PacketIds.id(PacketName.PLAY_CHUNK_DATA, V485));
        assertEquals(OptionalInt.of(0x20), PacketIds.id(PacketName.PLAY_KEEP_ALIVE, V485));
        assertEquals(OptionalInt.of(0x0e), PacketIds.id(PacketName.PLAY_CHAT, V485));
        assertEquals(OptionalInt.of(0x27), PacketIds.id(PacketName.PLAY_TRADE_LIST, V485));
        assertEquals(OptionalInt.of(0x03), PacketIds.id(PacketName.PLAY_SPAWN_ENTITY_LIVING, V485));
        assertEquals(OptionalInt.of(0x03), PacketIds.id(PacketName.PLAY_CHAT_TO_SERVER, V485));
        // 掘削 ack は 485 にも 490 にも無い（498 で 0x5c として追加された）。
        assertEquals(OptionalInt.empty(),
                PacketIds.id(PacketName.PLAY_ACKNOWLEDGE_PLAYER_DIGGING, V485));
        assertEquals(OptionalInt.empty(),
                PacketIds.id(PacketName.PLAY_ACKNOWLEDGE_PLAYER_DIGGING, V490));
        assertEquals(OptionalInt.of(0x5c),
                PacketIds.id(PacketName.PLAY_ACKNOWLEDGE_PLAYER_DIGGING,
                        ProtocolVersion.V1_14_4));
        assertEquals(OptionalInt.empty(), PacketIds.id(PacketName.CONFIG_REGISTRY_DATA, V485));
        assertEquals(OptionalInt.empty(), PacketIds.id(PacketName.PLAY_PLAYER_CHAT, V485));
    }
}
