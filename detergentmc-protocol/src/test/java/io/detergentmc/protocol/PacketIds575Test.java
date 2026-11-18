package io.detergentmc.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.detergentmc.protocol.PacketIds.PacketName;
import java.util.List;
import java.util.OptionalInt;
import org.junit.jupiter.api.Test;

/**
 * {@link PacketIds} の575（1.15.1）向けチェック。
 * 期待値の根拠は Stage A の実データ証明：実バニラ 1.15.1 jar
 * （SHA-1 4d1826eebac84847c71a77f9349cc22afd0cf0a1）と 1.15.2 jar の
 * data-generator ダンプが全41レジストリで完全一致し、minecraft-data の
 * protocol.json（1.15.1／1.15.2）はバイト単位で同一
 * （tools/packet-ids-575.py が同一でない場合は表を作らない）。
 * よって 575 の列は 578 の列と全281件で一致するはずで、それをここで固定する。
 */
class PacketIds575Test {

    private static final int V575 = ProtocolVersion.V1_15_1;
    private static final int V578 = ProtocolVersion.V1_15_2;

    @Test
    void tableKnows575() {
        assertTrue(PacketIds.isSupported(V575));
        assertEquals(List.of(335, 338, 340, 393, 401, 404, 477, 480, 485, 490, 498, 573, 575, 578, 758, 759, 760, 761, 762, 763, 764, 765, 766, 767, 768, 769, 770, 771, 772, 773, 774, 775, 776),
                PacketIds.supportedProtocols());
    }

    @Test
    void everyIdMatches578() {
        // Stage A の証明の固定：575 の ID は 578 と全件一致する。
        for (PacketName name : PacketName.values()) {
            assertEquals(name.id578(), name.id575(), name.name());
            assertEquals(PacketIds.id(name, V578), PacketIds.id(name, V575),
                    name.name());
        }
    }

    @Test
    void countsMatch578() {
        int present = 0;
        for (PacketName name : PacketName.values()) {
            if (name.id575() != -1) {
                present++;
            }
        }
        assertEquals(148, present);
    }

    @Test
    void keyPlayIds() {
        assertEquals(OptionalInt.of(0x26), PacketIds.id(PacketName.PLAY_JOIN_GAME, V575));
        assertEquals(OptionalInt.of(0x22), PacketIds.id(PacketName.PLAY_CHUNK_DATA, V575));
        assertEquals(OptionalInt.of(0x21), PacketIds.id(PacketName.PLAY_KEEP_ALIVE, V575));
        assertEquals(OptionalInt.of(0x0f), PacketIds.id(PacketName.PLAY_CHAT, V575));
        assertEquals(OptionalInt.of(0x02), PacketIds.id(PacketName.PLAY_SPAWN_ENTITY_WEATHER, V575));
        assertEquals(OptionalInt.empty(), PacketIds.id(PacketName.CONFIG_REGISTRY_DATA, V575));
        assertEquals(OptionalInt.empty(), PacketIds.id(PacketName.PLAY_PLAYER_CHAT, V575));
    }
}
