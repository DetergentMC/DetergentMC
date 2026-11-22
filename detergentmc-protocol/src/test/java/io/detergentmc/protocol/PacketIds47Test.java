package io.detergentmc.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.detergentmc.protocol.PacketIds.PacketName;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * 47（1.8〜1.8.9）のパケット ID 列。Stage A で実バニラ 1.8.9 の
 * ワイヤ採取と minecraft-data の protocol.json 全面比較により確定
 * （検証スクリプトは tools/packet-ids-47.py）。107 との関係：
 * 両方に存在するパケットは104件、そのうち ID が違うのは96件。
 * 47 にだけ存在するパケットは Map Chunk Bulk・Set Compression
 * （プレイ状態）・Update Entity NBT の3件。
 */
class PacketIds47Test {

    private static final int V47 = ProtocolVersion.V1_8;

    @Test
    void tableKnows47() {
        assertTrue(PacketIds.isSupported(V47));
        assertEquals(List.of(4, 5, 47, 107, 108, 109, 110, 210, 315, 316, 335, 338, 340, 393, 401, 404, 477, 480, 485, 490, 498, 573, 575, 578, 735, 736, 751, 753, 754, 755, 756, 757, 758, 759, 760, 761, 762, 763, 764, 765, 766, 767, 768, 769, 770, 771, 772, 773, 774, 775, 776),
                PacketIds.supportedProtocols());
    }

    @Test
    void presentCount47Is107() {
        int count = 0;
        for (PacketName name : PacketName.values()) {
            if (name.id47() != -1) {
                count++;
            }
        }
        assertEquals(107, count);
    }

    @Test
    void diffAgainst107Is96Of104Common() {
        int common = 0;
        int differ = 0;
        for (PacketName name : PacketName.values()) {
            if (name.id47() != -1 && name.id107() != -1) {
                common++;
                if (name.id47() != name.id107()) {
                    differ++;
                }
            }
        }
        assertEquals(104, common);
        assertEquals(96, differ);
    }

    @Test
    void onlyIn47AreBulkSetCompressionAndUpdateEntityNbt() {
        Set<PacketName> only47 = new java.util.HashSet<>();
        for (PacketName name : PacketName.values()) {
            if (name.id47() != -1 && name.id107() == -1) {
                only47.add(name);
            }
        }
        assertEquals(Set.of(PacketName.PLAY_MAP_CHUNK_BULK,
                PacketName.PLAY_SET_COMPRESSION,
                PacketName.PLAY_UPDATE_ENTITY_NBT), only47);
    }

    @Test
    void playIds47() {
        assertEquals(0x01, PacketIds.id(PacketName.PLAY_JOIN_GAME,
                PacketIds.State.PLAY, PacketIds.Direction.CLIENTBOUND,
                V47).orElseThrow());
        assertEquals(0x02, PacketIds.id(PacketName.PLAY_CHAT,
                PacketIds.State.PLAY, PacketIds.Direction.CLIENTBOUND,
                V47).orElseThrow());
        assertEquals(0x21, PacketIds.id(PacketName.PLAY_CHUNK_DATA,
                PacketIds.State.PLAY, PacketIds.Direction.CLIENTBOUND,
                V47).orElseThrow());
        assertEquals(0x26, PacketIds.id(PacketName.PLAY_MAP_CHUNK_BULK,
                PacketIds.State.PLAY, PacketIds.Direction.CLIENTBOUND,
                V47).orElseThrow());
        assertEquals(0x45, PacketIds.id(PacketName.PLAY_TITLE,
                PacketIds.State.PLAY, PacketIds.Direction.CLIENTBOUND,
                V47).orElseThrow());
        assertEquals(0x46, PacketIds.id(PacketName.PLAY_SET_COMPRESSION,
                PacketIds.State.PLAY, PacketIds.Direction.CLIENTBOUND,
                V47).orElseThrow());
        assertEquals(0x49, PacketIds.id(PacketName.PLAY_UPDATE_ENTITY_NBT,
                PacketIds.State.PLAY, PacketIds.Direction.CLIENTBOUND,
                V47).orElseThrow());
        // Keep Alive は両方向とも varint で、ID は 0x00。
        assertEquals(0x00, PacketIds.id(PacketName.PLAY_KEEP_ALIVE,
                PacketIds.State.PLAY, PacketIds.Direction.CLIENTBOUND,
                V47).orElseThrow());
        assertEquals(0x00, PacketIds.id(PacketName.PLAY_KEEP_ALIVE_TO_SERVER,
                PacketIds.State.PLAY, PacketIds.Direction.SERVERBOUND,
                V47).orElseThrow());
        // Teleport Confirm は 47 に無い。
        assertTrue(PacketIds.id(PacketName.PLAY_TELEPORT_CONFIRM,
                PacketIds.State.PLAY, PacketIds.Direction.SERVERBOUND,
                V47).isEmpty());
    }
}
