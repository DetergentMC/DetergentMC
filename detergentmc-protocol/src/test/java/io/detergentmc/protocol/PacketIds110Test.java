package io.detergentmc.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.detergentmc.protocol.PacketIds.PacketName;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 110（1.9.4／1.9.3）のパケット ID 列。210 と完全同一
 * （Stage A：minecraft-data の pc/1.9.4 と pc/1.10 の protocol.json
 * 全状態比較で ID 差分 0、実バニラ 1.9.4 のワイヤ採取でも確認）。
 * 形式差分は3件だけ（音の pitch が u8、resource_pack_receive の
 * hash）で、ID には現れない。
 */
class PacketIds110Test {

    private static final int V110 = ProtocolVersion.V1_9_4;

    @Test
    void tableKnows110() {
        assertTrue(PacketIds.isSupported(V110));
        assertEquals(List.of(4, 5, 47, 107, 108, 109, 110, 210, 315, 316, 335, 338, 340, 393, 401, 404, 477, 480, 485, 490, 498, 573, 575, 578, 735, 736, 751, 753, 754, 755, 756, 757, 758, 759, 760, 761, 762, 763, 764, 765, 766, 767, 768, 769, 770, 771, 772, 773, 774, 775, 776),
                PacketIds.supportedProtocols());
    }

    @Test
    void allIdsEqual210() {
        // Stage A の確定事項：110⇔210 のパケットID差分は全エントリで 0 件。
        for (PacketName name : PacketName.values()) {
            assertEquals(name.id210(), name.id110(), name.name());
        }
    }

    @Test
    void presentCount110Is113() {
        int count = 0;
        for (PacketName name : PacketName.values()) {
            if (name.id110() != -1) {
                count++;
            }
        }
        assertEquals(113, count);
    }

    @Test
    void playIds110() {
        assertEquals(0x0f, PacketIds.id(PacketName.PLAY_CHAT,
                PacketIds.State.PLAY, PacketIds.Direction.CLIENTBOUND,
                V110).orElseThrow());
        assertEquals(0x45, PacketIds.id(PacketName.PLAY_TITLE,
                PacketIds.State.PLAY, PacketIds.Direction.CLIENTBOUND,
                V110).orElseThrow());
        assertEquals(0x48, PacketIds.id(PacketName.PLAY_COLLECT,
                PacketIds.State.PLAY, PacketIds.Direction.CLIENTBOUND,
                V110).orElseThrow());
        assertEquals(0x16, PacketIds.id(PacketName.PLAY_RESOURCE_PACK_RESPONSE,
                PacketIds.State.PLAY, PacketIds.Direction.SERVERBOUND,
                V110).orElseThrow());
    }
}
