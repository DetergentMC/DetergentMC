package io.detergentmc.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.detergentmc.protocol.PacketIds.PacketName;
import java.util.List;
import java.util.OptionalInt;
import org.junit.jupiter.api.Test;

/**
 * 404（1.13.2）のパケットID表。404 列は 1.13.2 自身の protocol.json
 * （minecraft-data pc/1.13.2）から packet-ids-404.py で全281エントリを
 * 機械生成した。477 との差分は実データで確定済み：存在する137件のうち
 * 101件が ID 相違、36件が一致。404 にだけ存在するパケットは
 * PLAY_BED（0x33）。実バニラ 1.13.2 jar の SHA-1 は
 * 3737db93722a9e39eeada7c27e7aca28b144ffa7（jar ルートに
 * version.json は無い世代）。
 */
class PacketIds404Test {

    private static final int V404 = ProtocolVersion.V1_13_2;

    @Test
    void tableKnows404() {
        assertTrue(PacketIds.isSupported(V404));
        assertEquals(List.of(335, 338, 340, 393, 401, 404, 477, 480, 485, 490, 498, 573, 575, 578, 758, 759, 760, 761, 762, 763, 764, 765, 766, 767, 768, 769, 770, 771, 772, 773, 774, 775, 776),
                PacketIds.supportedProtocols());
    }

    @Test
    void presentCount404Is137() {
        int count404 = 0;
        int count477 = 0;
        for (PacketName name : PacketName.values()) {
            if (name.id404() != -1) {
                count404++;
            }
            if (name.id477() != -1) {
                count477++;
            }
        }
        // 共通表 281 件のうち 137 件が 404 に存在し、それに 404 だけの
        // 新規パケット PLAY_BED を加えて 138 件（477 は 147 件）。
        assertEquals(138, count404);
        assertEquals(147, count477);
    }

    @Test
    void differenceAgainst477Is101() {
        // Stage A の実データ照合どおり：共通表の 137 件のうち 101 件が
        // ID 相違、36 件が一致。PLAY_BED は 477 に無いため相違に数える
        // （列挙全体では相違 102 件・一致 36 件）。
        int differing = 0;
        int identical = 0;
        for (PacketName name : PacketName.values()) {
            if (name.id404() == -1) {
                continue;
            }
            if (name.id404() != name.id477()) {
                differing++;
            } else {
                identical++;
            }
        }
        assertEquals(102, differing);
        assertEquals(36, identical);
    }

    @Test
    void keyPlayIdsAreThe1132Values() {
        assertEquals(OptionalInt.of(0x25), PacketIds.id(PacketName.PLAY_JOIN_GAME, V404));
        assertEquals(OptionalInt.of(0x22), PacketIds.id(PacketName.PLAY_CHUNK_DATA, V404));
        assertEquals(OptionalInt.of(0x21), PacketIds.id(PacketName.PLAY_KEEP_ALIVE, V404));
        assertEquals(OptionalInt.of(0x0e), PacketIds.id(PacketName.PLAY_CHAT, V404));
        assertEquals(OptionalInt.of(0x00), PacketIds.id(PacketName.PLAY_SPAWN_ENTITY, V404));
        assertEquals(OptionalInt.of(0x03),
                PacketIds.id(PacketName.PLAY_SPAWN_ENTITY_LIVING, V404));
        assertEquals(OptionalInt.of(0x02), PacketIds.id(PacketName.PLAY_CHAT_TO_SERVER, V404));
        assertEquals(OptionalInt.of(0x0e),
                PacketIds.id(PacketName.PLAY_KEEP_ALIVE_TO_SERVER, V404));
        // 404 に無いもの：Trade List は 1.14 で追加、掘削 ack は 1.14.4 で追加、
        // Update Light は 1.14 で追加（404 はチャンク内蔵の光）。
        assertEquals(OptionalInt.empty(), PacketIds.id(PacketName.PLAY_TRADE_LIST, V404));
        assertEquals(OptionalInt.empty(),
                PacketIds.id(PacketName.PLAY_ACKNOWLEDGE_PLAYER_DIGGING, V404));
        assertEquals(OptionalInt.empty(), PacketIds.id(PacketName.PLAY_UPDATE_LIGHT, V404));
        assertEquals(OptionalInt.empty(), PacketIds.id(PacketName.CONFIG_REGISTRY_DATA, V404));
        assertEquals(OptionalInt.empty(), PacketIds.id(PacketName.PLAY_PLAYER_CHAT, V404));
    }

    @Test
    void bedExistsOnlyIn335And338And340And393And401And404() {
        assertEquals(OptionalInt.of(0x33), PacketIds.id(PacketName.PLAY_BED, V404));
        assertEquals(OptionalInt.of(0x33),
                PacketIds.id(PacketName.PLAY_BED, ProtocolVersion.V1_13_1));
        // 393（1.13）にも PLAY_BED はある（0x33、401／404 と同値）。
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
            if (other != V404 && other != ProtocolVersion.V1_13_1
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
