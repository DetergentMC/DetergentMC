package io.detergentmc.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * {@link RegistryRemapper} の770（1.21.5）向けスポットチェックと往復テスト。
 * 期待値は ViaVersion Mappings（mapping-1.21.4.json＝769、
 * mapping-1.21.5.json＝770）と minecraft-data を名前＋プロパティで突き合わせたもの。
 * 生成スクリプト（tools/generate-remap-tables.py --target 770）の
 * maps770.json／maps_composed_770.json／map_items_rev770.json と同じ値。
 * 検証は tools/verify-770-tables.py を参照。
 *
 * <p>769→770 の差分：ブロック状態 27,866→27,914（+60／−12。茂み・枯れ草・
 * ワイルドフラワー・リーフリッター・ホタルブッシュ・サボテンの花・テストブロックの
 * 追加と creaking_heart のプロパティ変更）、アイテム 1,385→1,396（+11。青・茶の
 * 卵、茂み、ホタルブッシュ、枯れ草、ワイルドフラワー、リーフリッター、サボテンの花、
 * テストブロック、テストインスタンスブロック）、エンティティ 149→150（potion が
 * splash_potion と lingering_potion に分裂）、音 1,651→1,702（+52／−1。
 * entity.wolf.howl の削除とオオカミの鳴き声バリアント36件など）、パーティクル
 * 112→114（tinted_leaves 35 と firefly 113 の挿入）、属性 32件同士で並び同一、
 * エンチャント 42件同士で並び同一、データコンポーネント 67→96種、バイオーム
 * 65件同士で並び同一、ダメージ種別・絵画・旗の模様・トリムは変わらない。
 * ブロックエンティティは 45→47件（test_block 45・test_instance_block 46 の追加）。
 */
class RegistryRemapper770Test {

    // ---- 769 → 770 ----

    @Test
    void blockStatesForward() {
        assertEquals(0, RegistryRemapper.mapBlockState769To770(0)); // air
        assertEquals(1, RegistryRemapper.mapBlockState769To770(1)); // stone
        assertEquals(27698, RegistryRemapper.mapBlockState769To770(27651)); // trial_spawner
        assertEquals(27710, RegistryRemapper.mapBlockState769To770(27663)); // vault
        assertEquals(27742, RegistryRemapper.mapBlockState769To770(27695)); // heavy_core
        // creaking_heart（769 の 12 状態）：active=true→awake、active=false→dormant。
        assertEquals(2924, RegistryRemapper.mapBlockState769To770(2917));
        assertEquals(2925, RegistryRemapper.mapBlockState769To770(2918));
        assertEquals(2930, RegistryRemapper.mapBlockState769To770(2919));
        assertEquals(2922, RegistryRemapper.mapBlockState769To770(2923));
        assertEquals(2923, RegistryRemapper.mapBlockState769To770(2924));
        assertEquals(2934, RegistryRemapper.mapBlockState769To770(2927));
        assertEquals(1, RegistryRemapper.mapBlockState769To770(99999)); // 範囲外は石
    }

    @Test
    void itemsForward() {
        assertEquals(0, RegistryRemapper.mapItem769To770(0)); // air
        assertEquals(1, RegistryRemapper.mapItem769To770(1)); // stone
        assertEquals(810, RegistryRemapper.mapItem769To770(803)); // oak_boat
        assertEquals(1235, RegistryRemapper.mapItem769To770(1224)); // music_disc_creator
        assertEquals(1207, RegistryRemapper.mapItem769To770(1196)); // music_disc_precipice
        assertEquals(1154, RegistryRemapper.mapItem769To770(1143)); // breeze_rod
        assertEquals(1, RegistryRemapper.mapItem769To770(99999)); // 範囲外は石
    }

    @Test
    void entitiesSoundsParticlesBlocksMenusForward() {
        assertEquals(0, RegistryRemapper.mapEntityType769To770(0)); // acacia_boat
        assertEquals(94, RegistryRemapper.mapEntityType769To770(94)); // pig
        assertEquals(84, RegistryRemapper.mapEntityType769To770(84)); // oak_boat
        assertEquals(85, RegistryRemapper.mapEntityType769To770(85)); // oak_chest_boat
        assertEquals(12, RegistryRemapper.mapEntityType769To770(12)); // breeze
        assertEquals(129, RegistryRemapper.mapEntityType769To770(128)); // fishing_bobber
        assertEquals(99, RegistryRemapper.mapEntityType769To770(99)); // potion → splash_potion
        assertEquals(94, RegistryRemapper.mapEntityType769To770(99999)); // 範囲外はブタ
        assertEquals(1417, RegistryRemapper.mapSound769To770(1401)); // block.stone.break
        assertEquals(734, RegistryRemapper.mapSound769To770(730)); // item.goat_horn.sound.0
        assertEquals(646, RegistryRemapper.mapSound769To770(642)); // entity.goat.horn_break
        assertEquals(20, RegistryRemapper.mapParticle769To770(20)); // entity_effect
        assertEquals(48, RegistryRemapper.mapParticle769To770(47)); // trail
        assertEquals(112, RegistryRemapper.mapParticle769To770(111)); // block_crumble
        assertEquals(1, RegistryRemapper.mapBlock769To770(1)); // stone
        assertEquals(1074, RegistryRemapper.mapBlock769To770(1066)); // heavy_core
        assertEquals(12, RegistryRemapper.mapMenu769To770(12)); // crafting
        assertEquals(0, RegistryRemapper.mapEffect769To770(0)); // Speed
        assertEquals(38, RegistryRemapper.mapEffect769To770(38)); // Infested
    }

    @Test
    void blockEntityForward() {
        // 769と770のブロックエンティティの並びは同一（恒等写像）。770は2件追加。
        assertEquals(9, RegistryRemapper.mapBlockEntityType769To770(9)); // mob_spawner
        assertEquals(10, RegistryRemapper.mapBlockEntityType769To770(10)); // creaking_heart
        assertEquals(11, RegistryRemapper.mapBlockEntityType769To770(11)); // piston
        assertEquals(43, RegistryRemapper.mapBlockEntityType769To770(43)); // trial_spawner
        assertEquals(44, RegistryRemapper.mapBlockEntityType769To770(44)); // vault
        assertEquals(-1, RegistryRemapper.mapBlockEntityType769To770(45)); // 770にない番号は DROP
    }

    @Test
    void soundNamesForward() {
        assertEquals("block.stone.break",
                RegistryRemapper.mapSoundName769To770("block.stone.break"));
        assertEquals("entity.creaking.ambient",
                RegistryRemapper.mapSoundName769To770("entity.creaking.ambient"));
        assertEquals("item.goat_horn.sound.0",
                RegistryRemapper.mapSoundName769To770("item.goat_horn.sound.0"));
        // 770で削除された entity.wolf.howl は entity.wolf.ambient へ。
        assertEquals("entity.wolf.ambient",
                RegistryRemapper.mapSoundName769To770("entity.wolf.howl"));
    }

    @Test
    void attributesForward() {
        // 769と770の属性の並びは同一（恒等写像）。
        assertEquals(0, RegistryRemapper.mapAttribute769To770(0)); // generic.armor
        assertEquals(7, RegistryRemapper.mapAttribute769To770(7)); // burning_time
        assertEquals(19, RegistryRemapper.mapAttribute769To770(19)); // mining_efficiency
        assertEquals(21, RegistryRemapper.mapAttribute769To770(21)); // generic.movement_speed
        assertEquals(31, RegistryRemapper.mapAttribute769To770(31)); // water_movement_efficiency
        assertEquals("burning_time", RegistryRemapper.attributeName770(7));
        assertEquals("tempt_range", RegistryRemapper.attributeName770(30));
        assertEquals("water_movement_efficiency", RegistryRemapper.attributeName770(31));
        assertEquals(7, RegistryRemapper.attributeId770("burning_time"));
        assertEquals(30, RegistryRemapper.attributeId770("tempt_range"));
        // 770 のワイヤ上の名前は接頭辞付き。付いていても同じIDを返す。
        assertEquals(30, RegistryRemapper.attributeId770("minecraft:tempt_range"));
        assertEquals(-1, RegistryRemapper.attributeId770("not_an_attribute"));
    }

    @Test
    void enchantmentsForward() {
        // 769と770のエンチャントの並びは同一（恒等写像）。
        assertEquals(0, RegistryRemapper.mapEnchantment769To770(0)); // aqua_affinity
        assertEquals(27, RegistryRemapper.mapEnchantment769To770(27)); // protection
        assertEquals(41, RegistryRemapper.mapEnchantment769To770(41)); // wind_burst
        assertEquals(0, RegistryRemapper.mapEnchantment770To769(0));
        assertEquals(27, RegistryRemapper.mapEnchantment770To769(27));
        assertEquals(41, RegistryRemapper.mapEnchantment770To769(41));
        assertEquals(-1, RegistryRemapper.mapEnchantment769To770(999)); // 範囲外は DROP
        assertEquals(-1, RegistryRemapper.mapEnchantment770To769(999));
    }

    // ---- 770 → 769（invert）----

    @Test
    void blockStatesUp() {
        assertEquals(27651, RegistryRemapper.mapBlockStateUp770(27698)); // trial_spawner
        assertEquals(27663, RegistryRemapper.mapBlockStateUp770(27710)); // vault
        assertEquals(1, RegistryRemapper.mapBlockStateUp770(2051)); // 茂みの新規状態 → 石
        assertEquals(1, RegistryRemapper.mapBlockStateUp770(99999)); // 範囲外は石
        // creaking_heart の補正（uprooted の 6 状態は 769 の active=false へ。
        // 順向きの像ではないため往復には影響しない）。
        assertEquals(2923, RegistryRemapper.mapBlockStateUp770(2920));
        assertEquals(2924, RegistryRemapper.mapBlockStateUp770(2921));
        assertEquals(2925, RegistryRemapper.mapBlockStateUp770(2926));
        assertEquals(2927, RegistryRemapper.mapBlockStateUp770(2932));
    }

    @Test
    void itemsUp() {
        // 770で新規のアイテムは石へ。
        assertEquals(1, RegistryRemapper.mapItemUp770(970)); // blue_egg
        assertEquals(1, RegistryRemapper.mapItemUp770(971)); // brown_egg
        assertEquals(1, RegistryRemapper.mapItemUp770(204)); // bush
        assertEquals(1, RegistryRemapper.mapItemUp770(832)); // test_block
        assertEquals(1224, RegistryRemapper.mapItemUp770(1235)); // music_disc_creator
        assertEquals(1143, RegistryRemapper.mapItemUp770(1154)); // breeze_rod
        assertEquals(1, RegistryRemapper.mapItemUp770(99999)); // 範囲外は石
    }

    @Test
    void itemReverse770KeepsUnknownIds() {
        assertEquals(1, RegistryRemapper.mapItemReverse770(970)); // blue_egg → 石
        assertEquals(1224, RegistryRemapper.mapItemReverse770(1235)); // music_disc_creator
        assertEquals(1143, RegistryRemapper.mapItemReverse770(1154)); // breeze_rod
        assertEquals(9999, RegistryRemapper.mapItemReverse770(9999)); // 範囲外はそのまま
    }

    @Test
    void entitiesSoundsParticlesUp() {
        assertEquals(94, RegistryRemapper.mapEntityTypeUp770(94)); // pig
        assertEquals(84, RegistryRemapper.mapEntityTypeUp770(84)); // oak_boat
        assertEquals(85, RegistryRemapper.mapEntityTypeUp770(85)); // oak_chest_boat
        assertEquals(0, RegistryRemapper.mapEntityTypeUp770(0)); // acacia_boat
        assertEquals(9, RegistryRemapper.mapEntityTypeUp770(9)); // bamboo_raft
        assertEquals(29, RegistryRemapper.mapEntityTypeUp770(29)); // creaking
        assertEquals(99, RegistryRemapper.mapEntityTypeUp770(99)); // splash_potion → potion
        assertEquals(99, RegistryRemapper.mapEntityTypeUp770(100)); // lingering_potion → potion
        assertEquals(94, RegistryRemapper.mapEntityTypeUp770(99999)); // 範囲外はブタ
        assertEquals(1401, RegistryRemapper.mapSoundUp770(1417)); // block.stone.break
        assertEquals(730, RegistryRemapper.mapSoundUp770(734)); // item.goat_horn.sound.0
        assertEquals(20, RegistryRemapper.mapSoundUp770(20)); // 変わらない音
        assertEquals(1401, RegistryRemapper.mapSoundUp770(99999)); // 範囲外は石の破壊音
        assertEquals(1401, RegistryRemapper.mapSoundUp770(227)); // 770の新規の音 → 石の破壊音
        assertEquals(20, RegistryRemapper.mapParticleUp770(20)); // entity_effect
        assertEquals(47, RegistryRemapper.mapParticleUp770(48)); // trail
        assertEquals(111, RegistryRemapper.mapParticleUp770(112)); // block_crumble
        assertEquals(20, RegistryRemapper.mapParticleUp770(35)); // tinted_leaves → entity_effect
        assertEquals(20, RegistryRemapper.mapParticleUp770(113)); // firefly → entity_effect
        assertEquals(1, RegistryRemapper.mapBlockUp770(1)); // stone
        assertEquals(1066, RegistryRemapper.mapBlockUp770(1074)); // heavy_core
        assertEquals(12, RegistryRemapper.mapMenuUp770(12)); // crafting
        assertEquals(0, RegistryRemapper.mapEffectUp770(0)); // Speed
        assertEquals(38, RegistryRemapper.mapEffectUp770(38)); // Infested
    }

    @Test
    void blockEntityUp() {
        assertEquals(10, RegistryRemapper.mapBlockEntityTypeUp770(10)); // creaking_heart
        assertEquals(11, RegistryRemapper.mapBlockEntityTypeUp770(11)); // piston
        assertEquals(43, RegistryRemapper.mapBlockEntityTypeUp770(43)); // trial_spawner
        assertEquals(44, RegistryRemapper.mapBlockEntityTypeUp770(44)); // vault
        assertEquals(-1, RegistryRemapper.mapBlockEntityTypeUp770(45)); // test_block → DROP
        assertEquals(-1, RegistryRemapper.mapBlockEntityTypeUp770(46)); // test_instance_block → DROP
    }

    @Test
    void soundNamesUp() {
        assertEquals("item.goat_horn.sound.0",
                RegistryRemapper.mapSoundNameUp770("item.goat_horn.sound.0"));
        // 770にしかない音（オオカミの怒り変種・サボテンの花）は769向けに石の破壊音へ。
        assertEquals("block.stone.break",
                RegistryRemapper.mapSoundNameUp770("entity.wolf_angry.ambient"));
        assertEquals("block.stone.break",
                RegistryRemapper.mapSoundNameUp770("block.cactus_flower.break"));
    }

    @Test
    void attributesUp() {
        assertEquals(0, RegistryRemapper.mapAttribute770To769(0)); // generic.armor
        assertEquals(7, RegistryRemapper.mapAttribute770To769(7)); // burning_time
        assertEquals(19, RegistryRemapper.mapAttribute770To769(19)); // mining_efficiency
        assertEquals(30, RegistryRemapper.mapAttribute770To769(30)); // tempt_range（両方にある）
        assertEquals(31, RegistryRemapper.mapAttribute770To769(31)); // water_movement_efficiency
    }

    // ---- 往復・網羅 ----

    @Test
    void roundTrip769To770To769() {
        for (int i = 0; i <= RegistryRemapper.MAX_BLOCK_STATE_769; i++) {
            assertEquals(i, RegistryRemapper.mapBlockStateUp770(
                    RegistryRemapper.mapBlockState769To770(i)), "blockstate " + i);
        }
        for (int i = 0; i <= RegistryRemapper.MAX_ITEM_769; i++) {
            assertEquals(i, RegistryRemapper.mapItemUp770(
                    RegistryRemapper.mapItem769To770(i)), "item " + i);
        }
        for (int i = 0; i <= RegistryRemapper.MAX_ENTITY_TYPE_769; i++) {
            assertEquals(i, RegistryRemapper.mapEntityTypeUp770(
                    RegistryRemapper.mapEntityType769To770(i)), "entity " + i);
        }
        for (int i = 0; i <= RegistryRemapper.MAX_SOUND_769; i++) {
            // entity.wolf.howl（1595）は 770 の entity.wolf.ambient（1610）に集約されるため往復しない。
            if (i == 1595) {
                continue;
            }
            assertEquals(i, RegistryRemapper.mapSoundUp770(
                    RegistryRemapper.mapSound769To770(i)), "sound " + i);
        }
        // wolf.howl（1595）は往復で wolf.ambient（1592）になる（集約のため）。
        assertEquals(1592, RegistryRemapper.mapSoundUp770(
                RegistryRemapper.mapSound769To770(1595)));
        for (int i = 0; i <= RegistryRemapper.MAX_PARTICLE_769; i++) {
            assertEquals(i, RegistryRemapper.mapParticleUp770(
                    RegistryRemapper.mapParticle769To770(i)), "particle " + i);
        }
        for (int i = 0; i <= RegistryRemapper.MAX_BLOCK_769; i++) {
            assertEquals(i, RegistryRemapper.mapBlockUp770(
                    RegistryRemapper.mapBlock769To770(i)), "block " + i);
        }
        for (int i = 0; i <= RegistryRemapper.MAX_MENU_769; i++) {
            assertEquals(i, RegistryRemapper.mapMenuUp770(
                    RegistryRemapper.mapMenu769To770(i)), "menu " + i);
        }
    }

    @Test
    void exhaustiveForwardStayInRange() {
        for (int i = 0; i <= RegistryRemapper.MAX_BLOCK_STATE_769; i++) {
            int mapped = RegistryRemapper.mapBlockState769To770(i);
            assertTrue(mapped >= 0 && mapped <= RegistryRemapper.MAX_BLOCK_STATE_770,
                    "769 blockstate " + i + " -> " + mapped);
        }
        for (int i = 0; i <= RegistryRemapper.MAX_ITEM_769; i++) {
            int mapped = RegistryRemapper.mapItem769To770(i);
            assertTrue(mapped >= 0 && mapped <= RegistryRemapper.MAX_ITEM_770,
                    "769 item " + i + " -> " + mapped);
        }
        for (int i = 0; i <= RegistryRemapper.MAX_SOUND_769; i++) {
            int mapped = RegistryRemapper.mapSound769To770(i);
            assertTrue(mapped >= 0 && mapped <= RegistryRemapper.MAX_SOUND_770,
                    "769 sound " + i + " -> " + mapped);
        }
        for (int i = 0; i <= RegistryRemapper.MAX_PARTICLE_769; i++) {
            int mapped = RegistryRemapper.mapParticle769To770(i);
            assertTrue(mapped >= 0 && mapped <= RegistryRemapper.MAX_PARTICLE_770,
                    "769 particle " + i + " -> " + mapped);
        }
    }

    // ---- 合成表 763↔770 / 764↔770 / 765↔770 / 766↔770 / 768↔770 ----

    @Test
    void composed763To770() {
        assertEquals(835, RegistryRemapper.mapItem763To770(757)); // scute → turtle_scute
        assertEquals(84, RegistryRemapper.mapEntityType763To770(9)); // boat → oak_boat
        assertEquals(85, RegistryRemapper.mapEntityType763To770(13)); // chest_boat → oak_chest_boat
        assertEquals(734, RegistryRemapper.mapSound763To770(558)); // item.goat_horn.play
        assertEquals(1417, RegistryRemapper.mapSound763To770(1264)); // block.stone.break
        assertEquals(0, RegistryRemapper.mapEffect763To770(1)); // Speed（1ベース→0ベース）
        assertEquals(32, RegistryRemapper.mapEffect763To770(33)); // Unluck
        assertEquals(9, RegistryRemapper.mapBlockEntityType763To770(9)); // mob_spawner
        assertEquals(11, RegistryRemapper.mapBlockEntityType763To770(10)); // piston
        assertEquals(41, RegistryRemapper.mapBlockEntityType763To770(40));
        assertEquals("block.stone.break",
                RegistryRemapper.mapSoundName763To770("block.stone.break"));
    }

    @Test
    void composed770To763() {
        assertEquals(2873, RegistryRemapper.mapBlockState770To763(27710)); // vault → spawner
        assertEquals(1, RegistryRemapper.mapBlockState770To763(2924)); // creaking_heart → stone
        assertEquals(1, RegistryRemapper.mapBlockState770To763(2051)); // 茂みの新規状態 → stone
        assertEquals(757, RegistryRemapper.mapItem770To763(835)); // turtle_scute → scute
        assertEquals(1, RegistryRemapper.mapItem770To763(970)); // blue_egg → stone
        assertEquals(9, RegistryRemapper.mapEntityType770To763(84)); // oak_boat → boat
        assertEquals(9, RegistryRemapper.mapEntityType770To763(0)); // acacia_boat → boat
        assertEquals(72, RegistryRemapper.mapEntityType770To763(29)); // creaking → pig
        assertEquals(1264, RegistryRemapper.mapSound770To763(1417)); // stone.break
        assertEquals(1, RegistryRemapper.mapEffect770To763(0)); // Speed（0ベース→1ベース）
        assertEquals(33, RegistryRemapper.mapEffect770To763(32)); // Unluck
        assertEquals(1, RegistryRemapper.mapEffect770To763(33)); // TrialOmen → Speed
        assertEquals(-1, RegistryRemapper.mapBlockEntityType770To763(43)); // trial_spawner → DROP
        assertEquals(-1, RegistryRemapper.mapBlockEntityType770To763(10)); // creaking_heart → DROP
        assertEquals(9, RegistryRemapper.mapBlockEntityType770To763(9)); // mob_spawner
        assertEquals(10, RegistryRemapper.mapBlockEntityType770To763(11)); // piston
        assertEquals(40, RegistryRemapper.mapBlockEntityType770To763(41));
        assertEquals(-1, RegistryRemapper.mapBlockEntityType770To763(42)); // crafter → DROP
        assertEquals("block.stone.break",
                RegistryRemapper.mapSoundName770To763("block.trial_spawner.ominous_activate"));
    }

    @Test
    void composed765To770AndBack() {
        assertEquals(835, RegistryRemapper.mapItem765To770(794)); // scute → turtle_scute
        assertEquals(1586, RegistryRemapper.mapSound765To770(1466)); // generic.wind_burst → wind_charge.wind_burst
        assertEquals(1417, RegistryRemapper.mapSound765To770(1312)); // block.stone.break
        assertEquals(787, RegistryRemapper.mapItem770To765(826)); // turtle_scute → scute
        assertEquals(26638, RegistryRemapper.mapBlockState770To765(27710)); // vault → trial_spawner
        assertEquals(1, RegistryRemapper.mapBlockState770To765(2924)); // creaking_heart → stone
        assertEquals(1, RegistryRemapper.mapItem770To765(1235)); // music_disc_creator → stone
        assertEquals(0, RegistryRemapper.mapEffect765To770(0)); // Speed
        assertEquals(0, RegistryRemapper.mapEffect770To765(38)); // Infested → Speed
        assertEquals(43, RegistryRemapper.mapBlockEntityType765To770(42)); // trial_spawner
        assertEquals(-1, RegistryRemapper.mapBlockEntityType770To765(44)); // vault → DROP
        assertEquals("block.trial_spawner.ominous_activate",
                RegistryRemapper.mapSoundName765To770("block.trial_spawner.charge_activate"));
        assertEquals("block.stone.break",
                RegistryRemapper.mapSoundName770To765("music_disc.creator"));
        assertEquals("block.stone.break",
                RegistryRemapper.mapSoundName770To765("block.trial_spawner.ominous_activate"));
    }

    @Test
    void composed766To770AndBack() {
        assertEquals(1154, RegistryRemapper.mapItem766To770(1329)); // breeze_rod
        assertEquals(734, RegistryRemapper.mapSound766To770(617)); // item.goat_horn.play
        assertEquals(706, RegistryRemapper.mapSound770To766(734)); // item.goat_horn.sound.0
        assertEquals(1317, RegistryRemapper.mapItem770To766(1384)); // ominous_bottle
        assertEquals(44, RegistryRemapper.mapBlockEntityType766To770(43)); // vault
        assertEquals(43, RegistryRemapper.mapBlockEntityType770To766(44)); // vault
        assertEquals(-1, RegistryRemapper.mapBlockEntityType770To766(10)); // creaking_heart → DROP
        assertEquals("block.trial_spawner.ominous_activate",
                RegistryRemapper.mapSoundName766To770("block.trial_spawner.charge_activate"));
        assertEquals("block.trial_spawner.charge_activate",
                RegistryRemapper.mapSoundName770To766("block.trial_spawner.ominous_activate"));
    }

    @Test
    void composed768To770AndBack() {
        assertEquals(27698, RegistryRemapper.mapBlockState768To770(27107)); // trial_spawner
        assertEquals(810, RegistryRemapper.mapItem768To770(794)); // oak_boat
        assertEquals(27107, RegistryRemapper.mapBlockState770To768(27698)); // trial_spawner
        assertEquals(794, RegistryRemapper.mapItem770To768(810)); // oak_boat
    }

    @Test
    void protocolDispatchRoutes770() {
        // プロトコル指定のオーバーロードが 770 を 770表へ振ること。
        // 765の 26638 は trial_spawner の状態で、770では trial_spawner（27704）に
        // なる（vault の 27710 ではない。765に vault は無いため、逆向きの
        // 770→765では vault も trial_spawner に集約される）。
        assertEquals(27704, RegistryRemapper.mapBlockState(26638, 770));
        assertEquals(835, RegistryRemapper.mapItem(794, 770));
        assertEquals(94, RegistryRemapper.mapEntityType(73, 770));
        assertEquals(1417, RegistryRemapper.mapSound(1312, 770));
        // 765 のパーティクル 0（ambient_entity_effect）は 768・769・770 では
        // entity_effect（20）になる（766 で挿入によるずれ）。
        assertEquals(20, RegistryRemapper.mapParticle(0, 770));
        assertEquals(1, RegistryRemapper.mapBlock(1, 770));
        assertEquals(12, RegistryRemapper.mapMenu(12, 770));
        assertEquals(0, RegistryRemapper.mapEffect(0, 770));
        assertEquals(43, RegistryRemapper.mapBlockEntityType(42, 770));
        assertEquals("block.trial_spawner.ominous_activate",
                RegistryRemapper.mapSoundName("block.trial_spawner.charge_activate", 770));
        // 770クライアントのアイテムID → 765サーバーのアイテムID。
        assertEquals(794, RegistryRemapper.mapItemReverse(835, 770));
        assertEquals(1, RegistryRemapper.mapItemReverse(970, 770));
        // 769の振り分けが変わっていないこと。
        assertEquals(27657, RegistryRemapper.mapBlockState(26638, 769));
        assertEquals(826, RegistryRemapper.mapItem(794, 769));
    }
}
