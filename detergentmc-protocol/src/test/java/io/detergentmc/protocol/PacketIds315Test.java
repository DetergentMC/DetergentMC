package io.detergentmc.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.detergentmc.protocol.PacketIds.PacketName;
import java.util.List;
import java.util.OptionalInt;
import org.junit.jupiter.api.Test;

/**
 * 315（1.11）のパケットID表。315⇔316 のパケットID差分は 0 件
 * （Stage A 実測：minecraft-data の 1.11（315 自身の Burger 抽出）と
 * 1.11.2 の protocol.json の全表照合、1.11⇔1.11.1 のプロトコル
 * 登録クラスのバイトコード同一性、実バニラ 1.11 へのワイヤ採取）。
 * そのため 315 列は 316 列と同値。335 との差分は 316 と同じ
 * （315 に無いのは 1.12 で追加された進捗・レシピブックの6件だけ、
 * ID 差分は61件、共有パケットの形式差分は0件）。
 * 実バニラ 1.11 jar の SHA-1 は
 * 48820c84cb1ed502cb5b2fe23b8153d5e4fa61c0。
 */
class PacketIds315Test {

    private static final int V315 = ProtocolVersion.V1_11;

    @Test
    void tableKnows315() {
        assertTrue(PacketIds.isSupported(V315));
        assertEquals(List.of(4, 5, 47, 107, 108, 109, 110, 210, 315, 316, 335, 338, 340, 393, 401, 404, 477, 480, 485, 490, 498, 573, 575, 578, 735, 736, 751, 753, 754, 755, 756, 757, 758, 759, 760, 761, 762, 763, 764, 765, 766, 767, 768, 769, 770, 771, 772, 773, 774, 775, 776),
                PacketIds.supportedProtocols());
    }

    @Test
    void allIdsEqual316() {
        // Stage A の確定事項：315⇔316 のパケットID差分は全エントリで 0 件。
        for (PacketName name : PacketName.values()) {
            assertEquals(name.id316(), name.id315(), name.name());
        }
    }

    @Test
    void presentCount315Is113() {
        int count = 0;
        for (PacketName name : PacketName.values()) {
            if (name.id315() != -1) {
                count++;
            }
        }
        assertEquals(113, count);
    }

    @Test
    void onlyIn335AreTheSixAdvancementAndRecipeBookPackets() {
        // Stage A の実データ照合どおり：335 にあって 315 に無いのは
        // 進捗・レシピブックの6件だけ、315 にだけあるものは無い。
        List<String> only335 = new java.util.ArrayList<>();
        int only315 = 0;
        for (PacketName name : PacketName.values()) {
            if (name.id335() != -1 && name.id315() == -1) {
                only335.add(name.name());
            }
            if (name.id315() != -1 && name.id335() == -1) {
                only315++;
            }
        }
        assertEquals(new java.util.TreeSet<>(List.of(
                "PLAY_ADVANCEMENTS", "PLAY_SELECT_ADVANCEMENT_TAB",
                "PLAY_UNLOCK_RECIPES", "PLAY_ADVANCEMENT_TAB",
                "PLAY_CRAFTING_BOOK_DATA", "PLAY_PREPARE_CRAFTING_GRID")),
                new java.util.TreeSet<>(only335));
        assertEquals(0, only315);
    }

    @Test
    void idDifferenceFrom335Is61() {
        int diffs = 0;
        for (PacketName name : PacketName.values()) {
            if (name.id315() != -1 && name.id335() != -1
                    && name.id315() != name.id335()) {
                diffs++;
            }
        }
        assertEquals(61, diffs);
    }

    @Test
    void keyPlayIdsAreThe1112Values() {
        assertEquals(OptionalInt.of(0x23), PacketIds.id(PacketName.PLAY_JOIN_GAME, V315));
        assertEquals(OptionalInt.of(0x20), PacketIds.id(PacketName.PLAY_CHUNK_DATA, V315));
        assertEquals(OptionalInt.of(0x1F), PacketIds.id(PacketName.PLAY_KEEP_ALIVE, V315));
        assertEquals(OptionalInt.of(0x0B), PacketIds.id(PacketName.PLAY_KEEP_ALIVE_TO_SERVER, V315));
        assertEquals(OptionalInt.of(0x14), PacketIds.id(PacketName.PLAY_WINDOW_ITEMS, V315));
        assertEquals(OptionalInt.of(0x16), PacketIds.id(PacketName.PLAY_SET_SLOT, V315));
        assertEquals(OptionalInt.of(0x3C), PacketIds.id(PacketName.PLAY_ENTITY_EQUIPMENT, V315));
        assertEquals(OptionalInt.of(0x07), PacketIds.id(PacketName.PLAY_WINDOW_CLICK, V315));
        assertEquals(OptionalInt.of(0x18), PacketIds.id(PacketName.PLAY_SET_CREATIVE_SLOT, V315));
        assertEquals(OptionalInt.of(0x45), PacketIds.id(PacketName.PLAY_TITLE, V315));
        assertEquals(OptionalInt.of(0x2F), PacketIds.id(PacketName.PLAY_BED, V315));
        // 315 に無いもの（1.12 の進捗・レシピブックと、1.13 以降の追加）。
        assertEquals(OptionalInt.empty(), PacketIds.id(PacketName.PLAY_ADVANCEMENTS, V315));
        assertEquals(OptionalInt.empty(), PacketIds.id(PacketName.PLAY_UNLOCK_RECIPES, V315));
        assertEquals(OptionalInt.empty(), PacketIds.id(PacketName.PLAY_TRADE_LIST, V315));
        assertEquals(OptionalInt.empty(), PacketIds.id(PacketName.PLAY_UPDATE_LIGHT, V315));
        assertEquals(OptionalInt.empty(), PacketIds.id(PacketName.PLAY_STOP_SOUND, V315));
        assertEquals(OptionalInt.empty(), PacketIds.id(PacketName.PLAY_DECLARE_COMMANDS, V315));
        assertEquals(OptionalInt.empty(), PacketIds.id(PacketName.PLAY_FACE_PLAYER, V315));
    }
}
