package io.detergentmc.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.detergentmc.protocol.PacketIds.PacketName;
import java.util.List;
import java.util.OptionalInt;
import org.junit.jupiter.api.Test;

/**
 * 401（1.13.1）のパケットID表。401 列は 404 列と全件同値で、
 * tools/packet-ids-401.py が合意形成方式で全282エントリを一意に写像し、
 * 1.13.1 自身の protocol.json（minecraft-data pc/1.13.1）から引いた ID が
 * 404 と全件一致することを確認済み。1.13.1 と 1.13.2 の protocol.json は
 * mapper 表と全パケットのフィールド構成が一致し、差分は共有型 slot の
 * 定義だけ。実バニラ 1.13.1 jar の SHA-1 は
 * fe123682e9cb30031eae351764f653500b7396c9（jar ルートに
 * version.json は無い世代）。
 */
class PacketIds401Test {

    private static final int V401 = ProtocolVersion.V1_13_1;

    @Test
    void tableKnows401() {
        assertTrue(PacketIds.isSupported(V401));
        assertEquals(List.of(335, 338, 340, 393, 401, 404, 477, 480, 485, 490, 498, 573, 575, 578, 758, 759, 760, 761, 762, 763, 764, 765, 766, 767, 768, 769, 770, 771, 772, 773, 774, 775, 776),
                PacketIds.supportedProtocols());
    }

    @Test
    void everyIdMatches404() {
        // Stage A の実データ照合どおり：401 のIDは全エントリで 404 と一致する。
        for (PacketName name : PacketName.values()) {
            assertEquals(name.id404(), name.id401(), name.name());
        }
    }

    @Test
    void presentCount401Is138() {
        int count = 0;
        for (PacketName name : PacketName.values()) {
            if (name.id401() != -1) {
                count++;
            }
        }
        assertEquals(138, count);
    }

    @Test
    void keyPlayIdsAreThe1131Values() {
        assertEquals(OptionalInt.of(0x25), PacketIds.id(PacketName.PLAY_JOIN_GAME, V401));
        assertEquals(OptionalInt.of(0x22), PacketIds.id(PacketName.PLAY_CHUNK_DATA, V401));
        assertEquals(OptionalInt.of(0x21), PacketIds.id(PacketName.PLAY_KEEP_ALIVE, V401));
        assertEquals(OptionalInt.of(0x15), PacketIds.id(PacketName.PLAY_WINDOW_ITEMS, V401));
        assertEquals(OptionalInt.of(0x17), PacketIds.id(PacketName.PLAY_SET_SLOT, V401));
        assertEquals(OptionalInt.of(0x42), PacketIds.id(PacketName.PLAY_ENTITY_EQUIPMENT, V401));
        assertEquals(OptionalInt.of(0x08), PacketIds.id(PacketName.PLAY_WINDOW_CLICK, V401));
        assertEquals(OptionalInt.of(0x24), PacketIds.id(PacketName.PLAY_SET_CREATIVE_SLOT, V401));
        // 401 に無いものは 404 と同じ（Trade List は 1.14 で追加）。
        assertEquals(OptionalInt.empty(), PacketIds.id(PacketName.PLAY_TRADE_LIST, V401));
        assertEquals(OptionalInt.empty(), PacketIds.id(PacketName.PLAY_UPDATE_LIGHT, V401));
    }

    @Test
    void bedExistsIn335And338And340And393And401And404() {
        assertEquals(OptionalInt.of(0x33), PacketIds.id(PacketName.PLAY_BED, V401));
        assertEquals(OptionalInt.of(0x33),
                PacketIds.id(PacketName.PLAY_BED, ProtocolVersion.V1_13_2));
        // 393（1.13）にも PLAY_BED はある（0x33、401 と同値）。
        assertEquals(OptionalInt.of(0x33),
                PacketIds.id(PacketName.PLAY_BED, ProtocolVersion.V1_13));
        // 340（1.12.2）にも PLAY_BED はある（0x30）。
        assertEquals(OptionalInt.of(0x30),
                PacketIds.id(PacketName.PLAY_BED, ProtocolVersion.V1_12_2));
        // 338（1.12.1）にも PLAY_BED はある（0x30、340 と同値）。
        assertEquals(OptionalInt.of(0x30),
                PacketIds.id(PacketName.PLAY_BED, ProtocolVersion.V1_12_1));
        // 335（1.12）にも PLAY_BED はある（0x2F、338 より 1 小さい）。
        assertEquals(OptionalInt.of(0x2F),
                PacketIds.id(PacketName.PLAY_BED, ProtocolVersion.V1_12));
        for (int other : PacketIds.supportedProtocols()) {
            if (other != V401 && other != ProtocolVersion.V1_13_2
                    && other != ProtocolVersion.V1_13
                    && other != ProtocolVersion.V1_12_2
                    && other != ProtocolVersion.V1_12_1
                    && other != ProtocolVersion.V1_12) {
                assertEquals(OptionalInt.empty(), PacketIds.id(PacketName.PLAY_BED, other),
                        "protocol " + other);
            }
        }
    }
}
