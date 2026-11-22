package io.detergentmc.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.detergentmc.protocol.PacketIds.PacketName;
import java.util.List;
import java.util.OptionalInt;
import org.junit.jupiter.api.Test;

/**
 * 340（1.12.2）のパケットID表。340 列は tools/packet-ids-340.py が
 * 合意形成方式で全282エントリを一意に同定し、1.12.2 の protocol.json
 * （minecraft-data pc/1.12.2、Burger 抽出）と実バニラ 1.12.2 の
 * ワイヤ採取で裏付けた。393 と共通するパケットは125件、そのうち
 * ID が違うものは95件。340 に実在するパケットは120件。
 * 実バニラ 1.12.2 jar の SHA-1 は
 * 886945bfb2b978778c3a0288fd7fab09d315b25f。
 */
class PacketIds340Test {

    private static final int V340 = ProtocolVersion.V1_12_2;

    @Test
    void tableKnows340() {
        assertTrue(PacketIds.isSupported(V340));
        assertEquals(List.of(4, 5, 47, 107, 108, 109, 110, 210, 315, 316, 335, 338, 340, 393, 401, 404, 477, 480, 485, 490, 498, 573, 575, 578, 735, 736, 751, 753, 754, 755, 756, 757, 758, 759, 760, 761, 762, 763, 764, 765, 766, 767, 768, 769, 770, 771, 772, 773, 774, 775, 776),
                PacketIds.supportedProtocols());
    }

    @Test
    void presentCount340Is120() {
        int count = 0;
        for (PacketName name : PacketName.values()) {
            if (name.id340() != -1) {
                count++;
            }
        }
        assertEquals(120, count);
    }

    @Test
    void idDifferenceFrom393Is95() {
        // Stage A の実データ照合どおり：共通パケットの ID 差分は95件。
        int diffs = 0;
        for (PacketName name : PacketName.values()) {
            if (name.id340() != -1 && name.id393() != -1
                    && name.id340() != name.id393()) {
                diffs++;
            }
        }
        assertEquals(95, diffs);
    }

    @Test
    void keyPlayIdsAreThe1122Values() {
        assertEquals(OptionalInt.of(0x23), PacketIds.id(PacketName.PLAY_JOIN_GAME, V340));
        assertEquals(OptionalInt.of(0x20), PacketIds.id(PacketName.PLAY_CHUNK_DATA, V340));
        assertEquals(OptionalInt.of(0x1F), PacketIds.id(PacketName.PLAY_KEEP_ALIVE, V340));
        assertEquals(OptionalInt.of(0x14), PacketIds.id(PacketName.PLAY_WINDOW_ITEMS, V340));
        assertEquals(OptionalInt.of(0x16), PacketIds.id(PacketName.PLAY_SET_SLOT, V340));
        assertEquals(OptionalInt.of(0x3F), PacketIds.id(PacketName.PLAY_ENTITY_EQUIPMENT, V340));
        assertEquals(OptionalInt.of(0x07), PacketIds.id(PacketName.PLAY_WINDOW_CLICK, V340));
        assertEquals(OptionalInt.of(0x1B), PacketIds.id(PacketName.PLAY_SET_CREATIVE_SLOT, V340));
        assertEquals(OptionalInt.of(0x48), PacketIds.id(PacketName.PLAY_TITLE, V340));
        assertEquals(OptionalInt.of(0x30), PacketIds.id(PacketName.PLAY_BED, V340));
        // 340 に無いもの（1.13 以降の追加や、世代的に存在しないもの）。
        assertEquals(OptionalInt.empty(), PacketIds.id(PacketName.PLAY_TRADE_LIST, V340));
        assertEquals(OptionalInt.empty(), PacketIds.id(PacketName.PLAY_UPDATE_LIGHT, V340));
        assertEquals(OptionalInt.empty(), PacketIds.id(PacketName.PLAY_STOP_SOUND, V340));
        assertEquals(OptionalInt.empty(), PacketIds.id(PacketName.PLAY_DECLARE_COMMANDS, V340));
        assertEquals(OptionalInt.empty(), PacketIds.id(PacketName.PLAY_FACE_PLAYER, V340));
    }
}
