package io.detergentmc.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.detergentmc.protocol.PacketIds.PacketName;
import java.util.List;
import java.util.OptionalInt;
import org.junit.jupiter.api.Test;

/**
 * 393（1.13）のパケットID表。393 列は 401 列と全件同値で、
 * tools/packet-ids-393.py が合意形成方式で全282エントリを一意に写像し、
 * 1.13 自身の protocol.json（minecraft-data pc/1.13）から引いた ID が
 * 401 と全件一致することを確認済み。1.13 と 1.13.1 の protocol.json は
 * 差分が edit_book の末尾 hand（1.13.1 で追加）だけで、共有型 slot の
 * 定義はどちらも旧形式（itemId: i16）。実バニラ 1.13 jar の SHA-1 は
 * d0caafb8438ebd206f99930cfaecfa6c9a13dca0（jar ルートに
 * version.json は無い世代）。
 */
class PacketIds393Test {

    private static final int V393 = ProtocolVersion.V1_13;

    @Test
    void tableKnows393() {
        assertTrue(PacketIds.isSupported(V393));
        assertEquals(List.of(4, 5, 47, 107, 108, 109, 110, 210, 315, 316, 335, 338, 340, 393, 401, 404, 477, 480, 485, 490, 498, 573, 575, 578, 735, 736, 751, 753, 754, 755, 756, 757, 758, 759, 760, 761, 762, 763, 764, 765, 766, 767, 768, 769, 770, 771, 772, 773, 774, 775, 776),
                PacketIds.supportedProtocols());
    }

    @Test
    void everyIdMatches401() {
        // Stage A の実データ照合どおり：393 のIDは全エントリで 401 と一致する。
        for (PacketName name : PacketName.values()) {
            assertEquals(name.id401(), name.id393(), name.name());
        }
    }

    @Test
    void presentCount393Is138() {
        int count = 0;
        for (PacketName name : PacketName.values()) {
            if (name.id393() != -1) {
                count++;
            }
        }
        assertEquals(138, count);
    }

    @Test
    void keyPlayIdsAreThe113Values() {
        assertEquals(OptionalInt.of(0x25), PacketIds.id(PacketName.PLAY_JOIN_GAME, V393));
        assertEquals(OptionalInt.of(0x22), PacketIds.id(PacketName.PLAY_CHUNK_DATA, V393));
        assertEquals(OptionalInt.of(0x21), PacketIds.id(PacketName.PLAY_KEEP_ALIVE, V393));
        assertEquals(OptionalInt.of(0x15), PacketIds.id(PacketName.PLAY_WINDOW_ITEMS, V393));
        assertEquals(OptionalInt.of(0x17), PacketIds.id(PacketName.PLAY_SET_SLOT, V393));
        assertEquals(OptionalInt.of(0x42), PacketIds.id(PacketName.PLAY_ENTITY_EQUIPMENT, V393));
        assertEquals(OptionalInt.of(0x08), PacketIds.id(PacketName.PLAY_WINDOW_CLICK, V393));
        assertEquals(OptionalInt.of(0x24), PacketIds.id(PacketName.PLAY_SET_CREATIVE_SLOT, V393));
        // 393 に無いものは 401／404 と同じ（Trade List は 1.14 で追加）。
        assertEquals(OptionalInt.empty(), PacketIds.id(PacketName.PLAY_TRADE_LIST, V393));
        assertEquals(OptionalInt.empty(), PacketIds.id(PacketName.PLAY_UPDATE_LIGHT, V393));
    }

    @Test
    void bedExistsIn47And107And108And109And110And210And315And316And335And338And340And393And401And404() {
        // PLAY_BED は 340（0x30）にもある。存在範囲は 335・338・340・393・401・404。
        // 338（1.12.1）にも PLAY_BED はある（0x30、340 と同値）。
        assertEquals(OptionalInt.of(0x30),
                PacketIds.id(PacketName.PLAY_BED, ProtocolVersion.V1_12_1));
        // 335（1.12）にも PLAY_BED はある（0x2F、338 より 1 小さい）。
        assertEquals(OptionalInt.of(0x2F),
                PacketIds.id(PacketName.PLAY_BED, ProtocolVersion.V1_12));
        // 316（1.11.2）にも PLAY_BED はある（0x2F、335 と同値）。
        assertEquals(OptionalInt.of(0x2F),
                PacketIds.id(PacketName.PLAY_BED, ProtocolVersion.V1_11_2));
        // 315（1.11）にも PLAY_BED はある（0x2F、316 と同値）。
        assertEquals(OptionalInt.of(0x2F),
                PacketIds.id(PacketName.PLAY_BED, ProtocolVersion.V1_11));
        // 210（1.10.2）にも PLAY_BED はある（0x2F、315 と同値）。
        assertEquals(OptionalInt.of(0x2F),
                PacketIds.id(PacketName.PLAY_BED, ProtocolVersion.V1_10_2));
        // 110（1.9.4）にも PLAY_BED はある（0x2F、210 と同値）。
        assertEquals(OptionalInt.of(0x2F),
                PacketIds.id(PacketName.PLAY_BED, ProtocolVersion.V1_9_4));
        // 109（1.9.2）にも PLAY_BED はある（0x2F、110 と同値）。
        assertEquals(OptionalInt.of(0x2F),
                PacketIds.id(PacketName.PLAY_BED, ProtocolVersion.V1_9_2));
        // 108（1.9.1）にも PLAY_BED はある（0x2F、109 と同値）。
        assertEquals(OptionalInt.of(0x2F),
                PacketIds.id(PacketName.PLAY_BED, ProtocolVersion.V1_9_1));
        // 107（1.9）にも PLAY_BED はある（0x2F、108 と同値）。
        assertEquals(OptionalInt.of(0x2F),
                PacketIds.id(PacketName.PLAY_BED, ProtocolVersion.V1_9));
        // 47（1.8〜1.8.9）にも PLAY_BED はある（0x0A）。
        assertEquals(OptionalInt.of(0x0A),
                PacketIds.id(PacketName.PLAY_BED, ProtocolVersion.V1_8));
        // 5（1.7.6〜1.7.10）にも PLAY_BED はある（0x0A、47 と同値）。
        assertEquals(OptionalInt.of(0x0A),
                PacketIds.id(PacketName.PLAY_BED, ProtocolVersion.V1_7_10));
        // 4（1.7.2〜1.7.5）にも PLAY_BED はある（0x0A、5 と同値）。
        assertEquals(OptionalInt.of(0x0A),
                PacketIds.id(PacketName.PLAY_BED, ProtocolVersion.V1_7_2));
        assertEquals(OptionalInt.of(0x30),
                PacketIds.id(PacketName.PLAY_BED, ProtocolVersion.V1_12_2));
        assertEquals(OptionalInt.of(0x33), PacketIds.id(PacketName.PLAY_BED, V393));
        assertEquals(OptionalInt.of(0x33),
                PacketIds.id(PacketName.PLAY_BED, ProtocolVersion.V1_13_1));
        assertEquals(OptionalInt.of(0x33),
                PacketIds.id(PacketName.PLAY_BED, ProtocolVersion.V1_13_2));
        for (int other : PacketIds.supportedProtocols()) {
            if (other != ProtocolVersion.V1_12_2 && other != V393
                    && other != ProtocolVersion.V1_13_1
                    && other != ProtocolVersion.V1_13_2
                    && other != ProtocolVersion.V1_12_1
                    && other != ProtocolVersion.V1_12
                    && other != ProtocolVersion.V1_11_2
                    && other != ProtocolVersion.V1_11
                    && other != ProtocolVersion.V1_10_2
                    && other != ProtocolVersion.V1_9_4
                    && other != ProtocolVersion.V1_9_2
                    && other != ProtocolVersion.V1_9_1
                    && other != ProtocolVersion.V1_9
                    && other != ProtocolVersion.V1_8
                    && other != ProtocolVersion.V1_7_10
                    && other != ProtocolVersion.V1_7_2) {
                assertEquals(OptionalInt.empty(), PacketIds.id(PacketName.PLAY_BED, other),
                        "protocol " + other);
            }
        }
    }
}
