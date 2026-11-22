package io.detergentmc.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.detergentmc.protocol.PacketIds.PacketName;
import java.util.List;
import java.util.OptionalInt;
import org.junit.jupiter.api.Test;

/**
 * 335（1.12）のパケットID表。335 列は tools/packet-ids-335.py が
 * 合意形成方式で全エントリを一意に同定し、1.12 の protocol.json
 * （minecraft-data pc/1.12）から独立に引き直した。338 との差分は
 * レシピブック再編に集中し、ID差分53件はすべて機械的な挿入・削除
 * によるズレ（このテストでも規則として全件照合する）。
 * 実バニラ 1.12 jar の SHA-1 は
 * 8494e844e911ea0d63878f64da9dcc21f53a3463。
 */
class PacketIds335Test {

    private static final int V335 = ProtocolVersion.V1_12;

    @Test
    void tableKnows335() {
        assertTrue(PacketIds.isSupported(V335));
        assertEquals(List.of(4, 5, 47, 107, 108, 109, 110, 210, 315, 316, 335, 338, 340, 393, 401, 404, 477, 480, 485, 490, 498, 573, 575, 578, 735, 736, 751, 753, 754, 755, 756, 757, 758, 759, 760, 761, 762, 763, 764, 765, 766, 767, 768, 769, 770, 771, 772, 773, 774, 775, 776),
                PacketIds.supportedProtocols());
    }

    @Test
    void idDiffAgainst338FollowsTheMechanicalRule() {
        // Stage A の実データ照合どおり：335⇔338 のID差分は次の規則だけ。
        // クライアント行きは 0x2b（Craft Recipe Response）の有無で
        // それ以上が ±1、サーバー行きは先頭の Prepare Crafting Grid の
        // 削除と 0x12（Craft Recipe Request）の挿入が打ち消し合って
        // 0x01〜0x11 だけが ±1、0x13 以上は一致する。
        for (PacketName name : PacketName.values()) {
            int id338 = name.id338();
            int expected;
            if (name == PacketName.PLAY_PREPARE_CRAFTING_GRID) {
                expected = 0x01; // 335 のみ
            } else if (name == PacketName.PLAY_CRAFT_RECIPE_REQUEST
                    || name == PacketName.PLAY_CRAFT_RECIPE_RESPONSE) {
                expected = -1; // 338 以降のみ
            } else if (id338 < 0) {
                expected = -1;
            } else if (name.direction() == PacketIds.Direction.CLIENTBOUND) {
                expected = id338 >= 0x2c ? id338 - 1 : id338;
            } else if (name.state() == PacketIds.State.PLAY) {
                expected = id338 >= 0x01 && id338 <= 0x11 ? id338 + 1 : id338;
            } else {
                expected = id338;
            }
            assertEquals(expected, name.id335(), name.name());
            assertEquals(expected, name.idFor(V335), name.name());
        }
    }

    @Test
    void presentCount335Is119() {
        int count = 0;
        for (PacketName name : PacketName.values()) {
            if (name.id335() != -1) {
                count++;
            }
        }
        // protocol.json の play 124件から、表に載らない status 4件と
        // legacy_server_list_ping 1件を除いた 119 件。
        assertEquals(119, count);
    }

    @Test
    void keyPlayIdsAreThe112Values() {
        assertEquals(OptionalInt.of(0x23), PacketIds.id(PacketName.PLAY_JOIN_GAME, V335));
        assertEquals(OptionalInt.of(0x20), PacketIds.id(PacketName.PLAY_CHUNK_DATA, V335));
        assertEquals(OptionalInt.of(0x1F), PacketIds.id(PacketName.PLAY_KEEP_ALIVE, V335));
        assertEquals(OptionalInt.of(0x14), PacketIds.id(PacketName.PLAY_WINDOW_ITEMS, V335));
        assertEquals(OptionalInt.of(0x16), PacketIds.id(PacketName.PLAY_SET_SLOT, V335));
        // 0x2b 以上のクライアント行きは 338 より 1 小さい。
        assertEquals(OptionalInt.of(0x2B), PacketIds.id(PacketName.PLAY_ABILITIES, V335));
        assertEquals(OptionalInt.of(0x2D), PacketIds.id(PacketName.PLAY_PLAYER_INFO_UPDATE, V335));
        assertEquals(OptionalInt.of(0x46), PacketIds.id(PacketName.PLAY_UPDATE_TIME, V335));
        assertEquals(OptionalInt.of(0x4C), PacketIds.id(PacketName.PLAY_ADVANCEMENTS, V335));
        assertEquals(OptionalInt.of(0x4E), PacketIds.id(PacketName.PLAY_ENTITY_EFFECT, V335));
        // サーバー行きは 0x01〜0x11 が 338 より 1 大きい。
        assertEquals(OptionalInt.of(0x0C), PacketIds.id(PacketName.PLAY_KEEP_ALIVE_TO_SERVER, V335));
        assertEquals(OptionalInt.of(0x02), PacketIds.id(PacketName.PLAY_TAB_COMPLETE_TO_SERVER, V335));
        assertEquals(OptionalInt.of(0x03), PacketIds.id(PacketName.PLAY_CHAT_TO_SERVER, V335));
        // 335 のみにある Prepare Crafting Grid。
        assertEquals(OptionalInt.of(0x01), PacketIds.id(PacketName.PLAY_PREPARE_CRAFTING_GRID, V335));
        assertEquals(OptionalInt.empty(), PacketIds.id(PacketName.PLAY_PREPARE_CRAFTING_GRID, ProtocolVersion.V1_12_1));
        assertEquals(OptionalInt.empty(), PacketIds.id(PacketName.PLAY_PREPARE_CRAFTING_GRID, ProtocolVersion.V1_12_2));
        // 338 以降のレシピブック再編パケットは 335 に無い。
        assertEquals(OptionalInt.empty(), PacketIds.id(PacketName.PLAY_CRAFT_RECIPE_REQUEST, V335));
        assertEquals(OptionalInt.empty(), PacketIds.id(PacketName.PLAY_CRAFT_RECIPE_RESPONSE, V335));
        // 335 に無いもの（1.13 以降の追加や、世代的に存在しないもの）。
        assertEquals(OptionalInt.empty(), PacketIds.id(PacketName.PLAY_TRADE_LIST, V335));
        assertEquals(OptionalInt.empty(), PacketIds.id(PacketName.PLAY_UPDATE_LIGHT, V335));
        assertEquals(OptionalInt.empty(), PacketIds.id(PacketName.PLAY_STOP_SOUND, V335));
        assertEquals(OptionalInt.empty(), PacketIds.id(PacketName.PLAY_DECLARE_COMMANDS, V335));
        assertEquals(OptionalInt.empty(), PacketIds.id(PacketName.PLAY_FACE_PLAYER, V335));
        assertEquals(OptionalInt.empty(), PacketIds.id(PacketName.PLAY_PING, V335));
    }

    @Test
    void nameFor335RoundTrips() {
        // 逆引きも 335 の番号で同じ名前が返ること。
        for (PacketName name : PacketName.values()) {
            if (name.id335() < 0) {
                continue;
            }
            assertEquals(name, PacketIds.nameFor(name.id335(), name.state(),
                    name.direction(), V335).orElseThrow(), name.name());
        }
    }
}
