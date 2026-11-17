package io.detergentmc.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.detergentmc.protocol.PacketIds.PacketName;
import java.util.List;
import java.util.OptionalInt;
import org.junit.jupiter.api.Test;

/**
 * {@link PacketIds} の578（1.15.2）向けスポットチェック。
 * 期待値は実バニラ 1.15.2 jar（SHA-1 bb2b6b1aefcd70dfd1892149ac3a215f6c636b07）と
 * PrismarineJS minecraft-data の protocol.json（1.15.2）、および
 * tools/packet-ids-578.py の出力による。
 * 表全体281件のうち578に存在するのは148件、758とIDが違うものは83件、
 * 578にだけあるものは8件（旧式の出現・タイトル・戦闘・ワールドボーダー等）。
 */
class PacketIds578Test {

    private static final int V578 = ProtocolVersion.V1_15_2;
    private static final int V758 = ProtocolVersion.V1_18_2;

    private static void assertId(int expected, PacketName name, int protocol) {
        assertEquals(OptionalInt.of(expected), PacketIds.id(name, protocol),
                name + " for protocol " + protocol);
    }

    private static void assertAbsent(PacketName name, int protocol) {
        assertEquals(OptionalInt.empty(), PacketIds.id(name, protocol),
                name + " は " + protocol + " に存在しないこと");
    }

    @Test
    void tableKnows578() {
        assertTrue(PacketIds.isSupported(V578));
        assertEquals(List.of(480, 485, 490, 498, 573, 575, 578, 758, 759, 760, 761, 762, 763, 764, 765, 766, 767, 768, 769, 770, 771, 772, 773, 774, 775, 776),
                PacketIds.supportedProtocols());
    }

    @Test
    void countsMatchMeasuredTable() {
        int present = 0;
        int changed = 0;
        int only578 = 0;
        int same = 0;
        for (PacketName name : PacketName.values()) {
            if (name.id578() != -1) {
                present++;
                if (name.id758() == -1) {
                    only578++;
                } else if (name.id578() != name.id758()) {
                    changed++;
                } else {
                    same++;
                }
            }
        }
        assertEquals(148, present);
        assertEquals(83, changed);
        assertEquals(8, only578);
        assertEquals(57, same);
    }

    @Test
    void keyPlayIds() {
        assertId(0x26, PacketName.PLAY_JOIN_GAME, V578);
        assertId(0x22, PacketName.PLAY_CHUNK_DATA, V578);
        assertId(0x25, PacketName.PLAY_UPDATE_LIGHT, V578);
        assertId(0x21, PacketName.PLAY_KEEP_ALIVE, V578);
        assertId(0x0f, PacketName.PLAY_CHAT, V578);
        assertId(0x36, PacketName.PLAY_SYNCHRONIZE_PLAYER_POSITION, V578);
        assertId(0x34, PacketName.PLAY_PLAYER_INFO_UPDATE, V578);
        assertId(0x3b, PacketName.PLAY_RESPAWN, V578);
    }

    @Test
    void legacyOnlyPacketsExist() {
        assertId(0x02, PacketName.PLAY_SPAWN_ENTITY_WEATHER, V578);
        assertId(0x13, PacketName.PLAY_TRANSACTION, V578);
        assertId(0x2c, PacketName.PLAY_ENTITY, V578);
        assertId(0x33, PacketName.PLAY_COMBAT_EVENT, V578);
        assertId(0x3e, PacketName.PLAY_WORLD_BORDER, V578);
        assertId(0x50, PacketName.PLAY_TITLE, V578);
        assertId(0x07, PacketName.PLAY_TRANSACTION_TO_SERVER, V578);
        assertId(0x1d, PacketName.PLAY_CRAFTING_BOOK_DATA, V578);
        assertAbsent(PacketName.PLAY_SPAWN_ENTITY_WEATHER, V758);
        assertAbsent(PacketName.PLAY_TITLE, V758);
        assertAbsent(PacketName.PLAY_COMBAT_EVENT, V758);
        assertAbsent(PacketName.PLAY_WORLD_BORDER, V758);
    }

    @Test
    void modernPacketsAbsent() {
        // 設定フェーズは 578 に無い。
        assertAbsent(PacketName.CONFIG_REGISTRY_DATA, V578);
        assertAbsent(PacketName.CONFIG_ACKNOWLEDGE_FINISH_CONFIGURATION, V578);
        // 署名付きチャット系は 759 から。
        assertAbsent(PacketName.PLAY_PLAYER_CHAT, V578);
        assertAbsent(PacketName.PLAY_SYSTEM_CHAT, V578);
        // 分割されたタイトル・戦闘・ボーダーは 758 側の形。
        assertAbsent(PacketName.PLAY_SET_TITLE_TEXT, V578);
        assertAbsent(PacketName.PLAY_ENTER_COMBAT_EVENT, V578);
        assertAbsent(PacketName.PLAY_INITIALIZE_WORLD_BORDER, V578);
        assertAbsent(PacketName.PLAY_PING, V578);
    }
}
