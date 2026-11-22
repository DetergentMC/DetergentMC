package io.detergentmc.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.detergentmc.protocol.PacketIds.PacketName;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 210（1.10／1.10.1／1.10.2）のパケット ID 列。315 と完全同一
 * （Stage A：minecraft-data の pc/1.10 と pc/1.11 の protocol.json
 * 全状態比較で ID 差分 0、実バニラ 1.10.2 のワイヤ採取でも確認）。
 * 形式差分は4件だけ（spawn type の u8、title の action 番号、
 * collect、block_place のカーソル）で、ID には現れない。
 * 201 という製品版プロトコルは存在しない（1.10 系は全て 210）。
 */
class PacketIds210Test {

    private static final int V210 = ProtocolVersion.V1_10_2;

    @Test
    void tableKnows210() {
        assertTrue(PacketIds.isSupported(V210));
        assertEquals(List.of(4, 5, 47, 107, 108, 109, 110, 210, 315, 316, 335, 338, 340, 393, 401, 404, 477, 480, 485, 490, 498, 573, 575, 578, 735, 736, 751, 753, 754, 755, 756, 757, 758, 759, 760, 761, 762, 763, 764, 765, 766, 767, 768, 769, 770, 771, 772, 773, 774, 775, 776),
                PacketIds.supportedProtocols());
    }

    @Test
    void allIdsEqual315() {
        // Stage A の確定事項：210⇔315 のパケットID差分は全エントリで 0 件。
        for (PacketName name : PacketName.values()) {
            assertEquals(name.id315(), name.id210(), name.name());
        }
    }

    @Test
    void presentCount210Is113() {
        int count = 0;
        for (PacketName name : PacketName.values()) {
            if (name.id210() != -1) {
                count++;
            }
        }
        assertEquals(113, count);
    }

    @Test
    void playIds210() {
        assertEquals(0x0f, PacketIds.id(PacketName.PLAY_CHAT,
                PacketIds.State.PLAY, PacketIds.Direction.CLIENTBOUND,
                V210).orElseThrow());
        assertEquals(0x45, PacketIds.id(PacketName.PLAY_TITLE,
                PacketIds.State.PLAY, PacketIds.Direction.CLIENTBOUND,
                V210).orElseThrow());
        assertEquals(0x48, PacketIds.id(PacketName.PLAY_COLLECT,
                PacketIds.State.PLAY, PacketIds.Direction.CLIENTBOUND,
                V210).orElseThrow());
    }
}
