package io.detergentmc.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.detergentmc.protocol.PacketIds.PacketName;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 4（1.7.2〜1.7.5）のパケット ID 列。Stage A で実バニラ 1.7.2 の
 * ワイヤ採取（Status 自己申告・Login Success・Keep Alive・
 * チャンクバルク・named_entity_spawn の2人採取）により確定した。
 * 5 との関係：パケット ID は全エントリで一致（差分0件）。形式差分は
 * 2件だけ（UUID 文字列の形、named_entity_spawn のプロパティ配列）。
 */
class PacketIds4Test {

    private static final int V4 = ProtocolVersion.V1_7_2;

    @Test
    void tableKnows4() {
        assertTrue(PacketIds.isSupported(V4));
        assertEquals(List.of(4, 5, 47, 107, 108, 109, 110, 210, 315, 316, 335, 338, 340, 393, 401, 404, 477, 480, 485, 490, 498, 573, 575, 578, 735, 736, 751, 753, 754, 755, 756, 757, 758, 759, 760, 761, 762, 763, 764, 765, 766, 767, 768, 769, 770, 771, 772, 773, 774, 775, 776),
                PacketIds.supportedProtocols());
    }

    @Test
    void id4EqualsId5ForEveryEntry() {
        for (PacketName name : PacketName.values()) {
            assertEquals(name.id5(), name.id4(),
                    name + " の 4 列が 5 列と食い違っている");
        }
    }

    @Test
    void presentCount4Is95() {
        int count = 0;
        for (PacketName name : PacketName.values()) {
            if (name.id4() != -1) {
                count++;
            }
        }
        // 5 と同じ95件（47 の107件から1.8の追加分12件を引いた数）。
        assertEquals(95, count);
    }

    @Test
    void idFor4MatchesColumn() {
        for (PacketIds.State state : PacketIds.State.values()) {
            for (PacketIds.Direction direction : PacketIds.Direction.values()) {
                for (PacketName name : PacketName.values()) {
                    java.util.OptionalInt id = PacketIds.id(name, state, direction, V4);
                    if (name.state() == state && name.direction() == direction
                            && name.id4() != -1) {
                        assertTrue(id.isPresent(), name.toString());
                        assertEquals(name.id4(), id.getAsInt(), name.toString());
                    }
                }
            }
        }
    }

    @Test
    void keepAliveIdsAreZeroBothDirections() {
        assertEquals(0x00, PacketName.PLAY_KEEP_ALIVE.id4());
        assertEquals(0x00, PacketName.PLAY_KEEP_ALIVE_TO_SERVER.id4());
    }
}
