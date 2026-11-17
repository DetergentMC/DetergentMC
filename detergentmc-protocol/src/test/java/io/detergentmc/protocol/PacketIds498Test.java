package io.detergentmc.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.detergentmc.protocol.PacketIds.PacketName;
import java.util.List;
import java.util.OptionalInt;
import org.junit.jupiter.api.Test;

/**
 * {@link PacketIds} の498（1.14.4）向けチェック。
 * 期待値の根拠は Stage A の実データ：実バニラ 1.14.4 jar
 * （SHA-1 3dc3d84a581f14691199cf6831b71ed1296a9fdf）の protocol 定義と
 * minecraft-data 1.14.4 の protocol.json を突き合わせた
 * tools/packet-ids-498.py の出力。573 との差分は play/toClient で
 * 85件の ID 変更、play/toServer は 0 件（構造差は Join Game 等にある）。
 */
class PacketIds498Test {

    private static final int V498 = ProtocolVersion.V1_14_4;
    private static final int V573 = ProtocolVersion.V1_15;

    @Test
    void tableKnows498() {
        assertTrue(PacketIds.isSupported(V498));
        assertEquals(List.of(480, 485, 490, 498, 573, 575, 578, 758, 759, 760, 761, 762, 763, 764, 765, 766, 767, 768, 769, 770, 771, 772, 773, 774, 775, 776),
                PacketIds.supportedProtocols());
    }

    @Test
    void presentCountIs148() {
        int present = 0;
        for (PacketName name : PacketName.values()) {
            if (name.id498() != -1) {
                present++;
            }
        }
        assertEquals(148, present);
    }

    @Test
    void differencesAgainst573Are85AndClientboundOnly() {
        int differing = 0;
        for (PacketName name : PacketName.values()) {
            if (name.id498() != name.id573()) {
                differing++;
                assertEquals(PacketIds.Direction.CLIENTBOUND, name.direction(),
                        name.name());
            }
        }
        assertEquals(85, differing);
    }

    @Test
    void keyPlayIds() {
        assertEquals(OptionalInt.of(0x25), PacketIds.id(PacketName.PLAY_JOIN_GAME, V498));
        assertEquals(OptionalInt.of(0x21), PacketIds.id(PacketName.PLAY_CHUNK_DATA, V498));
        assertEquals(OptionalInt.of(0x20), PacketIds.id(PacketName.PLAY_KEEP_ALIVE, V498));
        assertEquals(OptionalInt.of(0x0e), PacketIds.id(PacketName.PLAY_CHAT, V498));
        assertEquals(OptionalInt.of(0x03), PacketIds.id(PacketName.PLAY_SPAWN_ENTITY_LIVING, V498));
        assertEquals(OptionalInt.of(0x05), PacketIds.id(PacketName.PLAY_NAMED_ENTITY_SPAWN, V498));
        assertEquals(OptionalInt.of(0x02), PacketIds.id(PacketName.PLAY_SPAWN_ENTITY_WEATHER, V498));
        assertEquals(OptionalInt.of(0x03), PacketIds.id(PacketName.PLAY_CHAT_TO_SERVER, V498));
        assertEquals(OptionalInt.empty(), PacketIds.id(PacketName.CONFIG_REGISTRY_DATA, V498));
        assertEquals(OptionalInt.empty(), PacketIds.id(PacketName.PLAY_PLAYER_CHAT, V498));
    }
}
