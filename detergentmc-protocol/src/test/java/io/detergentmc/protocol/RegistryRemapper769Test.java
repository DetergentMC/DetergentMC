package io.detergentmc.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * {@link RegistryRemapper} の769（1.21.4）向けスポットチェックと往復テスト。
 * 期待値は ViaVersion Mappings（mapping-1.21.2.json＝768、
 * mapping-1.21.4.json＝769）と minecraft-data を名前＋プロパティで突き合わせたもの。
 * 生成スクリプト（tools/generate-remap-tables.py --target 769）の
 * maps769.json／maps_composed_769.json／map_items_rev769.json と同じ値。
 * 検証は tools/verify-769-tables.py を参照。
 *
 * <p>768→769 の差分：ブロック状態 27,318→27,866（+557／−9。樹脂・アイブロッサムの
 * 追加と creaking_heart のプロパティ変更）、アイテム 1,375→1,385（+10。樹脂の
 * レンガなど）、エンティティ 150→149（creaking_transient の削除。30 より大きい
 * ID はすべて −1 ずれる）、音 1,636→1,651（+15。樹脂・アイブロッサム・
 * creaking.twitch）、パーティクル 111→112（pale_oak_leaves が 34 に挿入）、
 * 属性 32件同士で並び同一、エンチャント 42件同士で並び同一、データコンポーネント
 * 67件同士で並び同一、ダメージ種別・絵画・旗の模様・トリムは変わらない。
 * ブロックエンティティは 768 と並び同一（creaking_heart は両方 10）。
 */
class RegistryRemapper769Test {

    // ---- 768 → 769 ----

    @Test
    void blockStatesForward() {
        assertEquals(0, RegistryRemapper.mapBlockState768To769(0)); // air
        assertEquals(1, RegistryRemapper.mapBlockState768To769(1)); // stone
        assertEquals(27651, RegistryRemapper.mapBlockState768To769(27107)); // trial_spawner
        assertEquals(27663, RegistryRemapper.mapBlockState768To769(27119)); // vault
        assertEquals(27695, RegistryRemapper.mapBlockState768To769(27151)); // heavy_core
        // creaking_heart（768 の 9 状態）：active→active=true,natural=true、
        // dormant→active=false,natural=true、disabled→active=false,natural=false。
        assertEquals(2924, RegistryRemapper.mapBlockState768To769(2917));
        assertEquals(2923, RegistryRemapper.mapBlockState768To769(2918));
        assertEquals(2917, RegistryRemapper.mapBlockState768To769(2919));
        assertEquals(1, RegistryRemapper.mapBlockState768To769(99999)); // 範囲外は石
    }

    @Test
    void itemsForward() {
        assertEquals(0, RegistryRemapper.mapItem768To769(0)); // air
        assertEquals(1, RegistryRemapper.mapItem768To769(1)); // stone
        assertEquals(803, RegistryRemapper.mapItem768To769(794)); // oak_boat
        assertEquals(1224, RegistryRemapper.mapItem768To769(1214)); // music_disc_creator
        assertEquals(1196, RegistryRemapper.mapItem768To769(1186)); // music_disc_precipice
        assertEquals(1143, RegistryRemapper.mapItem768To769(1134)); // breeze_rod
        assertEquals(1, RegistryRemapper.mapItem768To769(99999)); // 範囲外は石
    }

    @Test
    void entitiesSoundsParticlesBlocksMenusForward() {
        assertEquals(0, RegistryRemapper.mapEntityType768To769(0)); // acacia_boat
        assertEquals(94, RegistryRemapper.mapEntityType768To769(95)); // pig
        assertEquals(84, RegistryRemapper.mapEntityType768To769(85)); // oak_boat
        assertEquals(85, RegistryRemapper.mapEntityType768To769(86)); // oak_chest_boat
        assertEquals(12, RegistryRemapper.mapEntityType768To769(12)); // breeze
        assertEquals(128, RegistryRemapper.mapEntityType768To769(129)); // fishing_bobber
        assertEquals(29, RegistryRemapper.mapEntityType768To769(30)); // creaking_transient → creaking
        assertEquals(94, RegistryRemapper.mapEntityType768To769(99999)); // 範囲外はブタ
        assertEquals(1401, RegistryRemapper.mapSound768To769(1386)); // block.stone.break
        assertEquals(730, RegistryRemapper.mapSound768To769(724)); // item.goat_horn.sound.0
        assertEquals(642, RegistryRemapper.mapSound768To769(636)); // entity.goat.horn_break
        assertEquals(20, RegistryRemapper.mapParticle768To769(20)); // entity_effect
        assertEquals(47, RegistryRemapper.mapParticle768To769(46)); // trail
        assertEquals(111, RegistryRemapper.mapParticle768To769(110)); // block_crumble
        assertEquals(1, RegistryRemapper.mapBlock768To769(1)); // stone
        assertEquals(1066, RegistryRemapper.mapBlock768To769(1059)); // heavy_core
        assertEquals(12, RegistryRemapper.mapMenu768To769(12)); // crafting
        assertEquals(0, RegistryRemapper.mapEffect768To769(0)); // Speed
        assertEquals(38, RegistryRemapper.mapEffect768To769(38)); // Infested
    }

    @Test
    void blockEntityForward() {
        // 768と769のブロックエンティティの並びは同一（恒等写像）。
        assertEquals(9, RegistryRemapper.mapBlockEntityType768To769(9)); // mob_spawner
        assertEquals(10, RegistryRemapper.mapBlockEntityType768To769(10)); // creaking_heart
        assertEquals(11, RegistryRemapper.mapBlockEntityType768To769(11)); // piston
        assertEquals(43, RegistryRemapper.mapBlockEntityType768To769(43)); // trial_spawner
        assertEquals(44, RegistryRemapper.mapBlockEntityType768To769(44)); // vault
        assertEquals(-1, RegistryRemapper.mapBlockEntityType768To769(45)); // 769にない番号は DROP
    }

    @Test
    void soundNamesForward() {
        assertEquals("block.stone.break",
                RegistryRemapper.mapSoundName768To769("block.stone.break"));
        assertEquals("entity.creaking.ambient",
                RegistryRemapper.mapSoundName768To769("entity.creaking.ambient"));
        assertEquals("item.goat_horn.sound.0",
                RegistryRemapper.mapSoundName768To769("item.goat_horn.sound.0"));
    }

    @Test
    void attributesForward() {
        // 768と769の属性の並びは同一（恒等写像）。
        assertEquals(0, RegistryRemapper.mapAttribute768To769(0)); // generic.armor
        assertEquals(7, RegistryRemapper.mapAttribute768To769(7)); // burning_time
        assertEquals(19, RegistryRemapper.mapAttribute768To769(19)); // mining_efficiency
        assertEquals(21, RegistryRemapper.mapAttribute768To769(21)); // generic.movement_speed
        assertEquals(31, RegistryRemapper.mapAttribute768To769(31)); // water_movement_efficiency
        assertEquals("burning_time", RegistryRemapper.attributeName769(7));
        assertEquals("tempt_range", RegistryRemapper.attributeName769(30));
        assertEquals("water_movement_efficiency", RegistryRemapper.attributeName769(31));
        assertEquals(7, RegistryRemapper.attributeId769("burning_time"));
        assertEquals(30, RegistryRemapper.attributeId769("tempt_range"));
        // 769 のワイヤ上の名前は接頭辞付き。付いていても同じIDを返す。
        assertEquals(30, RegistryRemapper.attributeId769("minecraft:tempt_range"));
        assertEquals(-1, RegistryRemapper.attributeId769("not_an_attribute"));
    }

    @Test
    void enchantmentsForward() {
        // 768と769のエンチャントの並びは同一（恒等写像）。
        assertEquals(0, RegistryRemapper.mapEnchantment768To769(0)); // aqua_affinity
        assertEquals(27, RegistryRemapper.mapEnchantment768To769(27)); // protection
        assertEquals(41, RegistryRemapper.mapEnchantment768To769(41)); // wind_burst
        assertEquals(0, RegistryRemapper.mapEnchantment769To768(0));
        assertEquals(27, RegistryRemapper.mapEnchantment769To768(27));
        assertEquals(41, RegistryRemapper.mapEnchantment769To768(41));
        assertEquals(-1, RegistryRemapper.mapEnchantment768To769(999)); // 範囲外は DROP
        assertEquals(-1, RegistryRemapper.mapEnchantment769To768(999));
    }

    // ---- 769 → 768（invert）----

    @Test
    void blockStatesUp() {
        assertEquals(27107, RegistryRemapper.mapBlockStateUp769(27651)); // trial_spawner
        assertEquals(27119, RegistryRemapper.mapBlockStateUp769(27663)); // vault
        assertEquals(1, RegistryRemapper.mapBlockStateUp769(7230)); // 樹脂の新規状態 → 石
        assertEquals(1, RegistryRemapper.mapBlockStateUp769(99999)); // 範囲外は石
        // creaking_heart の補正（active=true,natural=false の 3 状態は
        // 768 の creaking=active へ。順向きの像ではないため往復には影響しない）。
        assertEquals(2919, RegistryRemapper.mapBlockStateUp769(2918));
        assertEquals(2922, RegistryRemapper.mapBlockStateUp769(2920));
        assertEquals(2925, RegistryRemapper.mapBlockStateUp769(2922));
    }

    @Test
    void itemsUp() {
        // 769で新規のアイテムは石へ。
        assertEquals(1, RegistryRemapper.mapItemUp769(1167)); // resin_brick
        assertEquals(1, RegistryRemapper.mapItemUp769(376)); // resin_clump
        assertEquals(1, RegistryRemapper.mapItemUp769(226)); // open_eyeblossom
        assertEquals(1214, RegistryRemapper.mapItemUp769(1224)); // music_disc_creator
        assertEquals(1134, RegistryRemapper.mapItemUp769(1143)); // breeze_rod
        assertEquals(1, RegistryRemapper.mapItemUp769(99999)); // 範囲外は石
    }

    @Test
    void itemReverse769KeepsUnknownIds() {
        assertEquals(1, RegistryRemapper.mapItemReverse769(1167)); // resin_brick → 石
        assertEquals(1214, RegistryRemapper.mapItemReverse769(1224)); // music_disc_creator
        assertEquals(1134, RegistryRemapper.mapItemReverse769(1143)); // breeze_rod
        assertEquals(9999, RegistryRemapper.mapItemReverse769(9999)); // 範囲外はそのまま
    }

    @Test
    void entitiesSoundsParticlesUp() {
        assertEquals(95, RegistryRemapper.mapEntityTypeUp769(94)); // pig
        assertEquals(85, RegistryRemapper.mapEntityTypeUp769(84)); // oak_boat
        assertEquals(86, RegistryRemapper.mapEntityTypeUp769(85)); // oak_chest_boat
        assertEquals(0, RegistryRemapper.mapEntityTypeUp769(0)); // acacia_boat
        assertEquals(9, RegistryRemapper.mapEntityTypeUp769(9)); // bamboo_raft
        assertEquals(29, RegistryRemapper.mapEntityTypeUp769(29)); // creaking
        assertEquals(95, RegistryRemapper.mapEntityTypeUp769(99999)); // 範囲外はブタ
        assertEquals(1386, RegistryRemapper.mapSoundUp769(1401)); // block.stone.break
        assertEquals(724, RegistryRemapper.mapSoundUp769(730)); // item.goat_horn.sound.0
        assertEquals(20, RegistryRemapper.mapSoundUp769(20)); // 変わらない音
        assertEquals(1386, RegistryRemapper.mapSoundUp769(99999)); // 範囲外は石の破壊音
        assertEquals(1386, RegistryRemapper.mapSoundUp769(1328)); // block.resin.break → 石の破壊音
        assertEquals(1386, RegistryRemapper.mapSoundUp769(384)); // entity.creaking.twitch → 石の破壊音
        assertEquals(20, RegistryRemapper.mapParticleUp769(20)); // entity_effect
        assertEquals(46, RegistryRemapper.mapParticleUp769(47)); // trail
        assertEquals(110, RegistryRemapper.mapParticleUp769(111)); // block_crumble
        assertEquals(20, RegistryRemapper.mapParticleUp769(34)); // pale_oak_leaves → entity_effect
        assertEquals(1, RegistryRemapper.mapBlockUp769(1)); // stone
        assertEquals(1059, RegistryRemapper.mapBlockUp769(1066)); // heavy_core
        assertEquals(12, RegistryRemapper.mapMenuUp769(12)); // crafting
        assertEquals(0, RegistryRemapper.mapEffectUp769(0)); // Speed
        assertEquals(38, RegistryRemapper.mapEffectUp769(38)); // Infested
    }

    @Test
    void blockEntityUp() {
        assertEquals(10, RegistryRemapper.mapBlockEntityTypeUp769(10)); // creaking_heart
        assertEquals(11, RegistryRemapper.mapBlockEntityTypeUp769(11)); // piston
        assertEquals(43, RegistryRemapper.mapBlockEntityTypeUp769(43)); // trial_spawner
        assertEquals(44, RegistryRemapper.mapBlockEntityTypeUp769(44)); // vault
        assertEquals(-1, RegistryRemapper.mapBlockEntityTypeUp769(45)); // 769にない番号は DROP
    }

    @Test
    void soundNamesUp() {
        assertEquals("item.goat_horn.sound.0",
                RegistryRemapper.mapSoundNameUp769("item.goat_horn.sound.0"));
        assertEquals("block.stone.break",
                RegistryRemapper.mapSoundNameUp769("block.resin.break"));
        assertEquals("block.stone.break",
                RegistryRemapper.mapSoundNameUp769("block.eyeblossom.open"));
        assertEquals("block.stone.break",
                RegistryRemapper.mapSoundNameUp769("entity.creaking.twitch"));
    }

    @Test
    void attributesUp() {
        assertEquals(0, RegistryRemapper.mapAttribute769To768(0)); // generic.armor
        assertEquals(7, RegistryRemapper.mapAttribute769To768(7)); // burning_time
        assertEquals(19, RegistryRemapper.mapAttribute769To768(19)); // mining_efficiency
        assertEquals(30, RegistryRemapper.mapAttribute769To768(30)); // tempt_range（両方にある）
        assertEquals(31, RegistryRemapper.mapAttribute769To768(31)); // water_movement_efficiency
    }

    // ---- 往復・網羅 ----

    @Test
    void roundTrip768To769To768() {
        for (int i = 0; i <= RegistryRemapper.MAX_BLOCK_STATE_768; i++) {
            assertEquals(i, RegistryRemapper.mapBlockStateUp769(
                    RegistryRemapper.mapBlockState768To769(i)), "blockstate " + i);
        }
        for (int i = 0; i <= RegistryRemapper.MAX_ITEM_768; i++) {
            assertEquals(i, RegistryRemapper.mapItemUp769(
                    RegistryRemapper.mapItem768To769(i)), "item " + i);
        }
        for (int i = 0; i <= RegistryRemapper.MAX_ENTITY_TYPE_768; i++) {
            // creaking_transient（30）は 769 の creaking（29）に集約されるため往復しない。
            if (i == 30) {
                continue;
            }
            assertEquals(i, RegistryRemapper.mapEntityTypeUp769(
                    RegistryRemapper.mapEntityType768To769(i)), "entity " + i);
        }
        assertEquals(29, RegistryRemapper.mapEntityTypeUp769(
                RegistryRemapper.mapEntityType768To769(30))); // creaking_transient
        for (int i = 0; i <= RegistryRemapper.MAX_SOUND_768; i++) {
            assertEquals(i, RegistryRemapper.mapSoundUp769(
                    RegistryRemapper.mapSound768To769(i)), "sound " + i);
        }
        for (int i = 0; i <= RegistryRemapper.MAX_PARTICLE_768; i++) {
            assertEquals(i, RegistryRemapper.mapParticleUp769(
                    RegistryRemapper.mapParticle768To769(i)), "particle " + i);
        }
        for (int i = 0; i <= RegistryRemapper.MAX_BLOCK_768; i++) {
            assertEquals(i, RegistryRemapper.mapBlockUp769(
                    RegistryRemapper.mapBlock768To769(i)), "block " + i);
        }
        for (int i = 0; i <= RegistryRemapper.MAX_MENU_768; i++) {
            assertEquals(i, RegistryRemapper.mapMenuUp769(
                    RegistryRemapper.mapMenu768To769(i)), "menu " + i);
        }
    }

    @Test
    void exhaustiveForwardStayInRange() {
        for (int i = 0; i <= RegistryRemapper.MAX_BLOCK_STATE_768; i++) {
            int mapped = RegistryRemapper.mapBlockState768To769(i);
            assertTrue(mapped >= 0 && mapped <= RegistryRemapper.MAX_BLOCK_STATE_769,
                    "768 blockstate " + i + " -> " + mapped);
        }
        for (int i = 0; i <= RegistryRemapper.MAX_ITEM_768; i++) {
            int mapped = RegistryRemapper.mapItem768To769(i);
            assertTrue(mapped >= 0 && mapped <= RegistryRemapper.MAX_ITEM_769,
                    "768 item " + i + " -> " + mapped);
        }
        for (int i = 0; i <= RegistryRemapper.MAX_SOUND_768; i++) {
            int mapped = RegistryRemapper.mapSound768To769(i);
            assertTrue(mapped >= 0 && mapped <= RegistryRemapper.MAX_SOUND_769,
                    "768 sound " + i + " -> " + mapped);
        }
        for (int i = 0; i <= RegistryRemapper.MAX_PARTICLE_768; i++) {
            int mapped = RegistryRemapper.mapParticle768To769(i);
            assertTrue(mapped >= 0 && mapped <= RegistryRemapper.MAX_PARTICLE_769,
                    "768 particle " + i + " -> " + mapped);
        }
    }

    // ---- 合成表 763↔769 / 764↔769 / 765↔769 / 766↔769 ----

    @Test
    void composed763To769() {
        assertEquals(826, RegistryRemapper.mapItem763To769(757)); // scute → turtle_scute
        assertEquals(84, RegistryRemapper.mapEntityType763To769(9)); // boat → oak_boat
        assertEquals(85, RegistryRemapper.mapEntityType763To769(13)); // chest_boat → oak_chest_boat
        assertEquals(730, RegistryRemapper.mapSound763To769(558)); // item.goat_horn.play
        assertEquals(1401, RegistryRemapper.mapSound763To769(1264)); // block.stone.break
        assertEquals(0, RegistryRemapper.mapEffect763To769(1)); // Speed（1ベース→0ベース）
        assertEquals(32, RegistryRemapper.mapEffect763To769(33)); // Unluck
        assertEquals(9, RegistryRemapper.mapBlockEntityType763To769(9)); // mob_spawner
        assertEquals(11, RegistryRemapper.mapBlockEntityType763To769(10)); // piston
        assertEquals(41, RegistryRemapper.mapBlockEntityType763To769(40));
        assertEquals("block.stone.break",
                RegistryRemapper.mapSoundName763To769("block.stone.break"));
    }

    @Test
    void composed769To763() {
        assertEquals(2873, RegistryRemapper.mapBlockState769To763(27663)); // vault → spawner
        assertEquals(1, RegistryRemapper.mapBlockState769To763(2917)); // creaking_heart → stone
        assertEquals(1, RegistryRemapper.mapBlockState769To763(7230)); // 樹脂の新規状態 → stone
        assertEquals(757, RegistryRemapper.mapItem769To763(826)); // turtle_scute → scute
        assertEquals(1, RegistryRemapper.mapItem769To763(1167)); // resin_brick → stone
        assertEquals(9, RegistryRemapper.mapEntityType769To763(84)); // oak_boat → boat
        assertEquals(9, RegistryRemapper.mapEntityType769To763(0)); // acacia_boat → boat
        assertEquals(72, RegistryRemapper.mapEntityType769To763(29)); // creaking → pig
        assertEquals(1264, RegistryRemapper.mapSound769To763(1401)); // stone.break
        assertEquals(1, RegistryRemapper.mapEffect769To763(0)); // Speed（0ベース→1ベース）
        assertEquals(33, RegistryRemapper.mapEffect769To763(32)); // Unluck
        assertEquals(1, RegistryRemapper.mapEffect769To763(33)); // TrialOmen → Speed
        assertEquals(-1, RegistryRemapper.mapBlockEntityType769To763(43)); // trial_spawner → DROP
        assertEquals(-1, RegistryRemapper.mapBlockEntityType769To763(10)); // creaking_heart → DROP
        assertEquals(9, RegistryRemapper.mapBlockEntityType769To763(9)); // mob_spawner
        assertEquals(10, RegistryRemapper.mapBlockEntityType769To763(11)); // piston
        assertEquals(40, RegistryRemapper.mapBlockEntityType769To763(41));
        assertEquals(-1, RegistryRemapper.mapBlockEntityType769To763(42)); // crafter → DROP
        // 763に trial_spawner の音が無いため、769の ominous_activate は
        // 石の破壊音へ（数値の合成表と一致）。
        assertEquals("block.stone.break",
                RegistryRemapper.mapSoundName769To763("block.trial_spawner.ominous_activate"));
    }

    @Test
    void composed764To769AndBack() {
        assertEquals(84, RegistryRemapper.mapEntityType764To769(9)); // boat → oak_boat
        assertEquals(2873, RegistryRemapper.mapBlockState769To764(27663)); // vault → spawner
        assertEquals(11, RegistryRemapper.mapMenu769To764(7)); // crafter_3x3 → crafting
        assertEquals(1, RegistryRemapper.mapItem769To764(1224)); // music_disc_creator → stone
        assertEquals(0, RegistryRemapper.mapEffect764To769(0)); // Speed
        assertEquals(0, RegistryRemapper.mapEffect769To764(38)); // Infested → Speed
        assertEquals(9, RegistryRemapper.mapBlockEntityType764To769(9)); // mob_spawner
        assertEquals(11, RegistryRemapper.mapBlockEntityType764To769(10)); // piston
        assertEquals(41, RegistryRemapper.mapBlockEntityType764To769(40));
        assertEquals(9, RegistryRemapper.mapBlockEntityType769To764(9)); // mob_spawner
        assertEquals(10, RegistryRemapper.mapBlockEntityType769To764(11)); // piston
        assertEquals(40, RegistryRemapper.mapBlockEntityType769To764(41));
        assertEquals(-1, RegistryRemapper.mapBlockEntityType769To764(42)); // crafter → DROP
        assertEquals(-1, RegistryRemapper.mapBlockEntityType769To764(44)); // vault → DROP
        assertEquals(-1, RegistryRemapper.mapBlockEntityType769To764(43)); // trial_spawner → DROP
    }

    @Test
    void composed765To769AndBack() {
        assertEquals(826, RegistryRemapper.mapItem765To769(794)); // scute → turtle_scute
        assertEquals(1570, RegistryRemapper.mapSound765To769(1466)); // generic.wind_burst → wind_charge.wind_burst
        assertEquals(1401, RegistryRemapper.mapSound765To769(1312)); // block.stone.break
        assertEquals(794, RegistryRemapper.mapItem769To765(826)); // turtle_scute → scute
        assertEquals(26638, RegistryRemapper.mapBlockState769To765(27663)); // vault → trial_spawner
        assertEquals(1, RegistryRemapper.mapBlockState769To765(2917)); // creaking_heart → stone
        assertEquals(1, RegistryRemapper.mapItem769To765(1224)); // music_disc_creator → stone
        assertEquals(0, RegistryRemapper.mapEffect765To769(0)); // Speed
        assertEquals(0, RegistryRemapper.mapEffect769To765(38)); // Infested → Speed
        assertEquals(43, RegistryRemapper.mapBlockEntityType765To769(42)); // trial_spawner
        assertEquals(-1, RegistryRemapper.mapBlockEntityType769To765(44)); // vault → DROP
        assertEquals("block.trial_spawner.ominous_activate",
                RegistryRemapper.mapSoundName765To769("block.trial_spawner.charge_activate"));
        assertEquals("block.stone.break",
                RegistryRemapper.mapSoundName769To765("music_disc.creator"));
        assertEquals("block.stone.break",
                RegistryRemapper.mapSoundName769To765("block.trial_spawner.ominous_activate"));
    }

    @Test
    void composed766To769AndBack() {
        assertEquals(1143, RegistryRemapper.mapItem766To769(1329)); // breeze_rod
        assertEquals(730, RegistryRemapper.mapSound766To769(617)); // item.goat_horn.play
        assertEquals(700, RegistryRemapper.mapSound769To766(724)); // item.goat_horn.sound.0
        assertEquals(1328, RegistryRemapper.mapItem769To766(1384)); // ominous_bottle
        assertEquals(44, RegistryRemapper.mapBlockEntityType766To769(43)); // vault
        assertEquals(43, RegistryRemapper.mapBlockEntityType769To766(44)); // vault
        assertEquals(-1, RegistryRemapper.mapBlockEntityType769To766(10)); // creaking_heart → DROP
        assertEquals("block.trial_spawner.ominous_activate",
                RegistryRemapper.mapSoundName766To769("block.trial_spawner.charge_activate"));
        assertEquals("block.trial_spawner.charge_activate",
                RegistryRemapper.mapSoundName769To766("block.trial_spawner.ominous_activate"));
    }

    @Test
    void protocolDispatchRoutes769() {
        // プロトコル指定のオーバーロードが 769 を 769表へ振ること。
        assertEquals(27657, RegistryRemapper.mapBlockState(26638, 769));
        assertEquals(826, RegistryRemapper.mapItem(794, 769));
        assertEquals(94, RegistryRemapper.mapEntityType(73, 769));
        assertEquals(1401, RegistryRemapper.mapSound(1312, 769));
        // 765 のパーティクル 0（ambient_entity_effect）は 768・769 では
        // entity_effect（20）になる（766 で挿入によるずれ）。
        assertEquals(20, RegistryRemapper.mapParticle(0, 769));
        assertEquals(1, RegistryRemapper.mapBlock(1, 769));
        assertEquals(12, RegistryRemapper.mapMenu(12, 769));
        assertEquals(0, RegistryRemapper.mapEffect(0, 769));
        assertEquals(43, RegistryRemapper.mapBlockEntityType(42, 769));
        assertEquals("block.trial_spawner.ominous_activate",
                RegistryRemapper.mapSoundName("block.trial_spawner.charge_activate", 769));
        // 769クライアントのアイテムID → 765サーバーのアイテムID。
        assertEquals(794, RegistryRemapper.mapItemReverse(826, 769));
        assertEquals(1, RegistryRemapper.mapItemReverse(140, 769));
        // 768の振り分けが変わっていないこと。
        assertEquals(27113, RegistryRemapper.mapBlockState(26638, 768));
        assertEquals(817, RegistryRemapper.mapItem(794, 768));
    }
}
