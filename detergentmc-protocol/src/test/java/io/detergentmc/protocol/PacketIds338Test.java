package io.detergentmc.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.detergentmc.protocol.PacketIds.PacketName;
import java.util.List;
import java.util.OptionalInt;
import org.junit.jupiter.api.Test;

/**
 * 338（1.12.1）のパケットID表。338 列は tools/packet-ids-338.py が
 * 合意形成方式で全282エントリを一意に同定し、1.12.1 の protocol.json
 * （minecraft-data pc/1.12.1）から独立に引き直した。結論は
 * 「338 のIDは全件 340 と同一」で、このテストでも全件照合する。
 * 340 との形式差分は Keep Alive の keepAliveId 型（varint）だけ。
 * 実バニラ 1.12.1 jar の SHA-1 は
 * 561c7b2d54bae80cc06b05d950633a9ac95da816。
 */
class PacketIds338Test {

    private static final int V338 = ProtocolVersion.V1_12_1;

    @Test
    void tableKnows338() {
        assertTrue(PacketIds.isSupported(V338));
        assertEquals(List.of(4, 5, 47, 107, 108, 109, 110, 210, 315, 316, 335, 338, 340, 393, 401, 404, 477, 480, 485, 490, 498, 573, 575, 578, 735, 736, 751, 753, 754, 755, 756, 757, 758, 759, 760, 761, 762, 763, 764, 765, 766, 767, 768, 769, 770, 771, 772, 773, 774, 775, 776),
                PacketIds.supportedProtocols());
    }

    @Test
    void allIdsAreIdenticalTo340() {
        // Stage A の実データ照合どおり：338 のIDは全件 340 と同一。
        for (PacketName name : PacketName.values()) {
            assertEquals(name.id340(), name.id338(), name.name());
            assertEquals(name.id340(), name.idFor(V338), name.name());
        }
    }

    @Test
    void presentCount338Is120() {
        int count = 0;
        for (PacketName name : PacketName.values()) {
            if (name.id338() != -1) {
                count++;
            }
        }
        assertEquals(120, count);
    }

    @Test
    void keyPlayIdsAreThe1121Values() {
        assertEquals(OptionalInt.of(0x23), PacketIds.id(PacketName.PLAY_JOIN_GAME, V338));
        assertEquals(OptionalInt.of(0x20), PacketIds.id(PacketName.PLAY_CHUNK_DATA, V338));
        assertEquals(OptionalInt.of(0x1F), PacketIds.id(PacketName.PLAY_KEEP_ALIVE, V338));
        assertEquals(OptionalInt.of(0x0B), PacketIds.id(PacketName.PLAY_KEEP_ALIVE_TO_SERVER, V338));
        assertEquals(OptionalInt.of(0x14), PacketIds.id(PacketName.PLAY_WINDOW_ITEMS, V338));
        assertEquals(OptionalInt.of(0x16), PacketIds.id(PacketName.PLAY_SET_SLOT, V338));
        assertEquals(OptionalInt.of(0x3F), PacketIds.id(PacketName.PLAY_ENTITY_EQUIPMENT, V338));
        assertEquals(OptionalInt.of(0x07), PacketIds.id(PacketName.PLAY_WINDOW_CLICK, V338));
        assertEquals(OptionalInt.of(0x1B), PacketIds.id(PacketName.PLAY_SET_CREATIVE_SLOT, V338));
        assertEquals(OptionalInt.of(0x48), PacketIds.id(PacketName.PLAY_TITLE, V338));
        assertEquals(OptionalInt.of(0x30), PacketIds.id(PacketName.PLAY_BED, V338));
        // レシピブック再編は 338 時点で発生済み（340 と同じ側）。
        assertEquals(OptionalInt.of(0x12), PacketIds.id(PacketName.PLAY_CRAFT_RECIPE_REQUEST, V338));
        assertEquals(OptionalInt.of(0x2B), PacketIds.id(PacketName.PLAY_CRAFT_RECIPE_RESPONSE, V338));
        // 338 に無いもの（1.13 以降の追加や、世代的に存在しないもの）。
        assertEquals(OptionalInt.empty(), PacketIds.id(PacketName.PLAY_TRADE_LIST, V338));
        assertEquals(OptionalInt.empty(), PacketIds.id(PacketName.PLAY_UPDATE_LIGHT, V338));
        assertEquals(OptionalInt.empty(), PacketIds.id(PacketName.PLAY_STOP_SOUND, V338));
        assertEquals(OptionalInt.empty(), PacketIds.id(PacketName.PLAY_DECLARE_COMMANDS, V338));
        assertEquals(OptionalInt.empty(), PacketIds.id(PacketName.PLAY_FACE_PLAYER, V338));
        assertEquals(OptionalInt.empty(), PacketIds.id(PacketName.PLAY_PING, V338));
    }

    @Test
    void nameFor338Matches340() {
        // 逆引きも 340 と同じ名前が返ること（ID が全件同一のため）。
        for (PacketName name : PacketName.values()) {
            if (name.id338() < 0) {
                continue;
            }
            assertEquals(name, PacketIds.nameFor(name.id338(), name.state(),
                    name.direction(), V338).orElseThrow(), name.name());
        }
    }
}
