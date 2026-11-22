package io.detergentmc.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.detergentmc.protocol.PacketIds.PacketName;
import java.util.List;
import java.util.OptionalInt;
import org.junit.jupiter.api.Test;

/**
 * 316（1.11.2）のパケットID表。316 列は tools/packet-ids-316.py が
 * 合意形成方式で全283エントリを一意に同定し、1.11 の protocol.json
 * （minecraft-data pc/1.11。data.js で 1.11.2 が指す 316 自身の
 * データ）と実バニラ 1.11.2 のワイヤ採取で裏付けた。335 との差分：
 * 316 に無いパケットは 1.12 で追加された進捗・レシピブックの6件
 * だけ、ID 差分は61件、共有パケットの形式差分は0件。
 * 実バニラ 1.11.2 jar の SHA-1 は
 * f00c294a1576e03fddcac777c3cf4c7d404c4ba4。
 */
class PacketIds316Test {

    private static final int V316 = ProtocolVersion.V1_11_2;

    @Test
    void tableKnows316() {
        assertTrue(PacketIds.isSupported(V316));
        assertEquals(List.of(4, 5, 47, 107, 108, 109, 110, 210, 315, 316, 335, 338, 340, 393, 401, 404, 477, 480, 485, 490, 498, 573, 575, 578, 735, 736, 751, 753, 754, 755, 756, 757, 758, 759, 760, 761, 762, 763, 764, 765, 766, 767, 768, 769, 770, 771, 772, 773, 774, 775, 776),
                PacketIds.supportedProtocols());
    }

    @Test
    void presentCount316Is113() {
        int count = 0;
        for (PacketName name : PacketName.values()) {
            if (name.id316() != -1) {
                count++;
            }
        }
        assertEquals(113, count);
    }

    @Test
    void onlyIn335AreTheSixAdvancementAndRecipeBookPackets() {
        // Stage A の実データ照合どおり：335 にあって 316 に無いのは
        // 進捗・レシピブックの6件だけ、316 にだけあるものは無い。
        List<String> only335 = new java.util.ArrayList<>();
        int only316 = 0;
        for (PacketName name : PacketName.values()) {
            if (name.id335() != -1 && name.id316() == -1) {
                only335.add(name.name());
            }
            if (name.id316() != -1 && name.id335() == -1) {
                only316++;
            }
        }
        assertEquals(new java.util.TreeSet<>(List.of(
                "PLAY_ADVANCEMENTS", "PLAY_SELECT_ADVANCEMENT_TAB",
                "PLAY_UNLOCK_RECIPES", "PLAY_ADVANCEMENT_TAB",
                "PLAY_CRAFTING_BOOK_DATA", "PLAY_PREPARE_CRAFTING_GRID")),
                new java.util.TreeSet<>(only335));
        assertEquals(0, only316);
    }

    @Test
    void idDifferenceFrom335Is61() {
        int diffs = 0;
        for (PacketName name : PacketName.values()) {
            if (name.id316() != -1 && name.id335() != -1
                    && name.id316() != name.id335()) {
                diffs++;
            }
        }
        assertEquals(61, diffs);
    }

    @Test
    void keyPlayIdsAreThe1112Values() {
        assertEquals(OptionalInt.of(0x23), PacketIds.id(PacketName.PLAY_JOIN_GAME, V316));
        assertEquals(OptionalInt.of(0x20), PacketIds.id(PacketName.PLAY_CHUNK_DATA, V316));
        assertEquals(OptionalInt.of(0x1F), PacketIds.id(PacketName.PLAY_KEEP_ALIVE, V316));
        assertEquals(OptionalInt.of(0x0B), PacketIds.id(PacketName.PLAY_KEEP_ALIVE_TO_SERVER, V316));
        assertEquals(OptionalInt.of(0x14), PacketIds.id(PacketName.PLAY_WINDOW_ITEMS, V316));
        assertEquals(OptionalInt.of(0x16), PacketIds.id(PacketName.PLAY_SET_SLOT, V316));
        assertEquals(OptionalInt.of(0x3C), PacketIds.id(PacketName.PLAY_ENTITY_EQUIPMENT, V316));
        assertEquals(OptionalInt.of(0x07), PacketIds.id(PacketName.PLAY_WINDOW_CLICK, V316));
        assertEquals(OptionalInt.of(0x18), PacketIds.id(PacketName.PLAY_SET_CREATIVE_SLOT, V316));
        assertEquals(OptionalInt.of(0x45), PacketIds.id(PacketName.PLAY_TITLE, V316));
        assertEquals(OptionalInt.of(0x2F), PacketIds.id(PacketName.PLAY_BED, V316));
        // 316 に無いもの（1.12 の進捗・レシピブックと、1.13 以降の追加）。
        assertEquals(OptionalInt.empty(), PacketIds.id(PacketName.PLAY_ADVANCEMENTS, V316));
        assertEquals(OptionalInt.empty(), PacketIds.id(PacketName.PLAY_UNLOCK_RECIPES, V316));
        assertEquals(OptionalInt.empty(), PacketIds.id(PacketName.PLAY_TRADE_LIST, V316));
        assertEquals(OptionalInt.empty(), PacketIds.id(PacketName.PLAY_UPDATE_LIGHT, V316));
        assertEquals(OptionalInt.empty(), PacketIds.id(PacketName.PLAY_STOP_SOUND, V316));
        assertEquals(OptionalInt.empty(), PacketIds.id(PacketName.PLAY_DECLARE_COMMANDS, V316));
        assertEquals(OptionalInt.empty(), PacketIds.id(PacketName.PLAY_FACE_PLAYER, V316));
    }
}
