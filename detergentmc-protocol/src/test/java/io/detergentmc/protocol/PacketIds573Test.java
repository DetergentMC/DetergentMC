package io.detergentmc.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.detergentmc.protocol.PacketIds.PacketName;
import java.util.List;
import java.util.OptionalInt;
import org.junit.jupiter.api.Test;

/**
 * {@link PacketIds} の573（1.15）向けチェック。
 * 期待値の根拠は Stage A の実データ証明：実バニラ 1.15 jar
 * （SHA-1 e9f105b3c5c7e85c7b445249a93362a22f62442d）と 1.15.2 jar の
 * data-generator ダンプが全41レジストリで完全一致し、minecraft-data の
 * protocol.json（1.15／1.15.2）はバイト単位で同一
 * （tools/packet-ids-573.py が同一でない場合は表を作らない）。
 * よって 573 の列は 578 の列と全281件で一致するはずで、それをここで固定する。
 */
class PacketIds573Test {

    private static final int V573 = ProtocolVersion.V1_15;
    private static final int V578 = ProtocolVersion.V1_15_2;

    @Test
    void tableKnows573() {
        assertTrue(PacketIds.isSupported(V573));
        assertEquals(List.of(480, 485, 490, 498, 573, 575, 578, 758, 759, 760, 761, 762, 763, 764, 765, 766, 767, 768, 769, 770, 771, 772, 773, 774, 775, 776),
                PacketIds.supportedProtocols());
    }

    @Test
    void everyIdMatches578() {
        // Stage A の証明の固定：573 の ID は 578 と全件一致する。
        for (PacketName name : PacketName.values()) {
            assertEquals(name.id578(), name.id573(), name.name());
            assertEquals(PacketIds.id(name, V578), PacketIds.id(name, V573),
                    name.name());
        }
    }

    @Test
    void countsMatch578() {
        int present = 0;
        for (PacketName name : PacketName.values()) {
            if (name.id573() != -1) {
                present++;
            }
        }
        assertEquals(148, present);
    }

    @Test
    void keyPlayIds() {
        assertEquals(OptionalInt.of(0x26), PacketIds.id(PacketName.PLAY_JOIN_GAME, V573));
        assertEquals(OptionalInt.of(0x22), PacketIds.id(PacketName.PLAY_CHUNK_DATA, V573));
        assertEquals(OptionalInt.of(0x21), PacketIds.id(PacketName.PLAY_KEEP_ALIVE, V573));
        assertEquals(OptionalInt.of(0x0f), PacketIds.id(PacketName.PLAY_CHAT, V573));
        assertEquals(OptionalInt.of(0x02), PacketIds.id(PacketName.PLAY_SPAWN_ENTITY_WEATHER, V573));
        assertEquals(OptionalInt.empty(), PacketIds.id(PacketName.CONFIG_REGISTRY_DATA, V573));
        assertEquals(OptionalInt.empty(), PacketIds.id(PacketName.PLAY_PLAYER_CHAT, V573));
    }
}
