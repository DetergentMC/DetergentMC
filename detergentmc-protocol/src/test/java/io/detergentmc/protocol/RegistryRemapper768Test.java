package io.detergentmc.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * {@link RegistryRemapper} の768（1.21.2／1.21.3）向けスポットチェックと往復テスト。
 * 期待値は ViaVersion Mappings（mapping-1.21.json＝767、
 * mapping-1.21.2.json＝768）と minecraft-data を名前＋プロパティで突き合わせたもの。
 * 生成スクリプト（tools/generate-remap-tables.py --target 768）の
 * maps768.json／maps_composed_768.json／map_items_rev768.json と同じ値。
 * 検証は tools/verify-768-tables.py を参照。
 *
 * <p>767→768 の差分：ブロック状態 26,684→27,318（+634）、アイテム 1,333→1,375（+42）、
 * エンティティ 130→150（boat／chest_boat が木材ごとの20種に分かれ、
 * creaking・creaking_transient が増えた）、音 1,611→1,636（ヤギの角笛の音2件が削除され
 * item.goat_horn.sound.0・entity.goat.horn_break へ代替）、パーティクル 109→111
 * （trail・block_crumble が新規）、属性 31→32（接頭辞が落ちた改名9件と tempt_range 新規）、
 * エンチャントは 42件同士で並び同一、ダメージ種別 47→49（ender_pearl・mace_smash が新規。
 * アルファベット順で mace_smash は magic より前＝768では 26、magic は 27）。
 * ブロックエンティティは creaking_heart が 10 に挿入され、piston 以降が +1 ずれる。
 */
class RegistryRemapper768Test {

    // ---- 767 → 768 ----

    @Test
    void blockStatesForward() {
        assertEquals(0, RegistryRemapper.mapBlockState767To768(0)); // air
        assertEquals(1, RegistryRemapper.mapBlockState767To768(1)); // stone
        assertEquals(27107, RegistryRemapper.mapBlockState767To768(26638)); // trial_spawner
        assertEquals(27119, RegistryRemapper.mapBlockState767To768(26650)); // vault
        assertEquals(27151, RegistryRemapper.mapBlockState767To768(26682)); // heavy_core
        assertEquals(27152, RegistryRemapper.mapBlockState767To768(26683)); // heavy_core（末尾）
        assertEquals(1, RegistryRemapper.mapBlockState767To768(99999)); // 範囲外は石
    }

    @Test
    void itemsForward() {
        assertEquals(0, RegistryRemapper.mapItem767To768(0)); // air
        assertEquals(1, RegistryRemapper.mapItem767To768(1)); // stone
        assertEquals(816, RegistryRemapper.mapItem767To768(794)); // turtle_helmet
        assertEquals(1213, RegistryRemapper.mapItem767To768(1171)); // 挿入点の直前
        assertEquals(1214, RegistryRemapper.mapItem767To768(1172)); // music_disc_creator
        assertEquals(1228, RegistryRemapper.mapItem767To768(1186)); // music_disc_precipice
        assertEquals(1134, RegistryRemapper.mapItem767To768(1332)); // breeze_rod
        assertEquals(1, RegistryRemapper.mapItem767To768(99999)); // 範囲外は石
    }

    @Test
    void entitiesSoundsParticlesBlocksMenusForward() {
        assertEquals(2, RegistryRemapper.mapEntityType767To768(0)); // area_effect_cloud（768ではボート類が先）
        assertEquals(95, RegistryRemapper.mapEntityType767To768(77)); // pig
        assertEquals(85, RegistryRemapper.mapEntityType767To768(10)); // boat → oak_boat
        assertEquals(86, RegistryRemapper.mapEntityType767To768(17)); // chest_boat → oak_chest_boat
        assertEquals(17, RegistryRemapper.mapEntityType767To768(12)); // breeze
        assertEquals(149, RegistryRemapper.mapEntityType767To768(129)); // fishing_bobber
        assertEquals(95, RegistryRemapper.mapEntityType767To768(99999)); // 範囲外はブタ
        assertEquals(1386, RegistryRemapper.mapSound767To768(1361)); // block.stone.break
        assertEquals(724, RegistryRemapper.mapSound767To768(617)); // item.goat_horn.play → sound.0
        assertEquals(636, RegistryRemapper.mapSound767To768(626)); // screaming.horn_break → horn_break
        assertEquals(724, RegistryRemapper.mapSound767To768(706)); // item.goat_horn.sound.0
        assertEquals(20, RegistryRemapper.mapParticle767To768(20)); // entity_effect
        assertEquals(1, RegistryRemapper.mapBlock767To768(1)); // stone
        assertEquals(1080, RegistryRemapper.mapBlock767To768(1059)); // heavy_core
        assertEquals(12, RegistryRemapper.mapMenu767To768(12)); // crafting
        assertEquals(0, RegistryRemapper.mapEffect767To768(0)); // Speed
        assertEquals(38, RegistryRemapper.mapEffect767To768(38)); // Infested
    }

    @Test
    void blockEntityForward() {
        assertEquals(9, RegistryRemapper.mapBlockEntityType767To768(9)); // mob_spawner
        assertEquals(11, RegistryRemapper.mapBlockEntityType767To768(10)); // piston
        assertEquals(43, RegistryRemapper.mapBlockEntityType767To768(42)); // trial_spawner
        assertEquals(44, RegistryRemapper.mapBlockEntityType767To768(43)); // vault
        assertEquals(-1, RegistryRemapper.mapBlockEntityType767To768(44)); // 768にない番号は DROP
    }

    @Test
    void soundNamesForward() {
        assertEquals("item.goat_horn.sound.0",
                RegistryRemapper.mapSoundName767To768("item.goat_horn.play"));
        assertEquals("entity.goat.horn_break",
                RegistryRemapper.mapSoundName767To768("entity.goat.screaming.horn_break"));
        assertEquals("block.stone.break",
                RegistryRemapper.mapSoundName767To768("block.stone.break"));
    }

    @Test
    void attributesForward() {
        assertEquals(0, RegistryRemapper.mapAttribute767To768(0)); // generic.armor
        assertEquals(7, RegistryRemapper.mapAttribute767To768(7)); // burning_time
        assertEquals(19, RegistryRemapper.mapAttribute767To768(19)); // mining_efficiency
        assertEquals(21, RegistryRemapper.mapAttribute767To768(21)); // generic.movement_speed
        assertEquals(31, RegistryRemapper.mapAttribute767To768(30)); // water_movement_efficiency
        assertEquals("burning_time", RegistryRemapper.attributeName768(7));
        assertEquals("tempt_range", RegistryRemapper.attributeName768(30));
        assertEquals("water_movement_efficiency", RegistryRemapper.attributeName768(31));
        assertEquals(7, RegistryRemapper.attributeId768("burning_time"));
        assertEquals(30, RegistryRemapper.attributeId768("tempt_range"));
        // 768 のワイヤ上の名前は接頭辞付き。付いていても同じIDを返す。
        assertEquals(30, RegistryRemapper.attributeId768("minecraft:tempt_range"));
        assertEquals(-1, RegistryRemapper.attributeId768("not_an_attribute"));
    }

    @Test
    void enchantmentsForward() {
        // 767と768のエンチャントの並びは同一（恒等写像）。
        assertEquals(0, RegistryRemapper.mapEnchantment767To768(0)); // aqua_affinity
        assertEquals(27, RegistryRemapper.mapEnchantment767To768(27)); // protection
        assertEquals(41, RegistryRemapper.mapEnchantment767To768(41)); // wind_burst
        assertEquals(0, RegistryRemapper.mapEnchantment768To767(0));
        assertEquals(27, RegistryRemapper.mapEnchantment768To767(27));
        assertEquals(41, RegistryRemapper.mapEnchantment768To767(41));
        assertEquals(-1, RegistryRemapper.mapEnchantment767To768(999)); // 範囲外は DROP
        assertEquals(-1, RegistryRemapper.mapEnchantment768To767(999));
    }

    // ---- 768 → 767（invert）----

    @Test
    void blockStatesUp() {
        assertEquals(26638, RegistryRemapper.mapBlockStateUp768(27107)); // trial_spawner
        assertEquals(26650, RegistryRemapper.mapBlockStateUp768(27119)); // vault
        assertEquals(1, RegistryRemapper.mapBlockStateUp768(157)); // pale_oak_log → 石
        assertEquals(1, RegistryRemapper.mapBlockStateUp768(2917)); // creaking_heart → 石
        assertEquals(1, RegistryRemapper.mapBlockStateUp768(99999)); // 範囲外は石
    }

    @Test
    void itemsUp() {
        // 768で新規のアイテムは石へ。
        assertEquals(1, RegistryRemapper.mapItemUp768(140)); // pale_oak_log
        assertEquals(1, RegistryRemapper.mapItemUp768(955)); // white_bundle
        assertEquals(1, RegistryRemapper.mapItemUp768(1124)); // creaking_spawn_egg
        assertEquals(1172, RegistryRemapper.mapItemUp768(1214)); // music_disc_creator
        assertEquals(1332, RegistryRemapper.mapItemUp768(1134)); // breeze_rod
        assertEquals(1, RegistryRemapper.mapItemUp768(99999)); // 範囲外は石
    }

    @Test
    void itemReverse768KeepsUnknownIds() {
        assertEquals(1, RegistryRemapper.mapItemReverse768(140)); // pale_oak_log → 石
        assertEquals(1172, RegistryRemapper.mapItemReverse768(1214)); // music_disc_creator
        assertEquals(1332, RegistryRemapper.mapItemReverse768(1134)); // breeze_rod
        assertEquals(9999, RegistryRemapper.mapItemReverse768(9999)); // 範囲外はそのまま
    }

    @Test
    void entitiesSoundsParticlesUp() {
        assertEquals(77, RegistryRemapper.mapEntityTypeUp768(95)); // pig
        // 768 の per-wood boat／raft は 767 の boat／chest_boat へ。
        // oak だけは順向きの先行勝ちでも同じ答えになる。
        assertEquals(10, RegistryRemapper.mapEntityTypeUp768(85)); // oak_boat
        assertEquals(17, RegistryRemapper.mapEntityTypeUp768(86)); // oak_chest_boat
        assertEquals(10, RegistryRemapper.mapEntityTypeUp768(0)); // acacia_boat
        assertEquals(17, RegistryRemapper.mapEntityTypeUp768(1)); // acacia_chest_boat
        assertEquals(10, RegistryRemapper.mapEntityTypeUp768(9)); // bamboo_raft
        assertEquals(17, RegistryRemapper.mapEntityTypeUp768(91)); // pale_oak_chest_boat
        assertEquals(77, RegistryRemapper.mapEntityTypeUp768(29)); // creaking → ブタ
        assertEquals(1361, RegistryRemapper.mapSoundUp768(1386)); // block.stone.break
        assertEquals(706, RegistryRemapper.mapSoundUp768(724)); // item.goat_horn.sound.0
        assertEquals(20, RegistryRemapper.mapParticleUp768(20)); // entity_effect
        assertEquals(20, RegistryRemapper.mapParticleUp768(46)); // trail → entity_effect
        assertEquals(20, RegistryRemapper.mapParticleUp768(110)); // block_crumble → entity_effect
        assertEquals(1, RegistryRemapper.mapBlockUp768(1)); // stone
        assertEquals(1059, RegistryRemapper.mapBlockUp768(1080)); // heavy_core
        assertEquals(12, RegistryRemapper.mapMenuUp768(12)); // crafting
        assertEquals(0, RegistryRemapper.mapEffectUp768(0)); // Speed
        assertEquals(38, RegistryRemapper.mapEffectUp768(38)); // Infested
    }

    @Test
    void blockEntityUp() {
        assertEquals(-1, RegistryRemapper.mapBlockEntityTypeUp768(10)); // creaking_heart → DROP
        assertEquals(10, RegistryRemapper.mapBlockEntityTypeUp768(11)); // piston
        assertEquals(42, RegistryRemapper.mapBlockEntityTypeUp768(43)); // trial_spawner
        assertEquals(43, RegistryRemapper.mapBlockEntityTypeUp768(44)); // vault
        assertEquals(-1, RegistryRemapper.mapBlockEntityTypeUp768(45)); // 768にない番号は DROP
    }

    @Test
    void soundNamesUp() {
        assertEquals("item.goat_horn.sound.0",
                RegistryRemapper.mapSoundNameUp768("item.goat_horn.sound.0"));
        assertEquals("block.stone.break",
                RegistryRemapper.mapSoundNameUp768("entity.creaking.ambient"));
        assertEquals("block.stone.break",
                RegistryRemapper.mapSoundNameUp768("item.bundle.insert_fail"));
        assertEquals("block.stone.break",
                RegistryRemapper.mapSoundNameUp768("block.spawner.break"));
    }

    @Test
    void attributesUp() {
        assertEquals(0, RegistryRemapper.mapAttribute768To767(0)); // generic.armor
        assertEquals(7, RegistryRemapper.mapAttribute768To767(7)); // burning_time
        assertEquals(19, RegistryRemapper.mapAttribute768To767(19)); // mining_efficiency
        assertEquals(-1, RegistryRemapper.mapAttribute768To767(30)); // tempt_range（767に無い）
        assertEquals(30, RegistryRemapper.mapAttribute768To767(31)); // water_movement_efficiency
    }

    // ---- 往復・網羅 ----

    @Test
    void roundTrip767To768To767() {
        for (int i = 0; i <= RegistryRemapper.MAX_BLOCK_STATE_767; i++) {
            assertEquals(i, RegistryRemapper.mapBlockStateUp768(
                    RegistryRemapper.mapBlockState767To768(i)), "blockstate " + i);
        }
        for (int i = 0; i <= RegistryRemapper.MAX_ITEM_767; i++) {
            assertEquals(i, RegistryRemapper.mapItemUp768(
                    RegistryRemapper.mapItem767To768(i)), "item " + i);
        }
        for (int i = 0; i <= RegistryRemapper.MAX_ENTITY_TYPE_767; i++) {
            assertEquals(i, RegistryRemapper.mapEntityTypeUp768(
                    RegistryRemapper.mapEntityType767To768(i)), "entity " + i);
        }
        for (int i = 0; i <= RegistryRemapper.MAX_SOUND_767; i++) {
            // ヤギの角笛の音2件は 768 で item.goat_horn.sound.0・
            // entity.goat.horn_break に集約されるため往復しない。
            // 617（item.goat_horn.play）→ 724 → 706（sound.0）、
            // 626（screaming.horn_break）→ 636 → 616（horn_break）。
            if (i == 617 || i == 626) {
                continue;
            }
            assertEquals(i, RegistryRemapper.mapSoundUp768(
                    RegistryRemapper.mapSound767To768(i)), "sound " + i);
        }
        assertEquals(706, RegistryRemapper.mapSoundUp768(
                RegistryRemapper.mapSound767To768(617))); // item.goat_horn.play
        assertEquals(616, RegistryRemapper.mapSoundUp768(
                RegistryRemapper.mapSound767To768(626))); // screaming.horn_break
        for (int i = 0; i <= RegistryRemapper.MAX_BLOCK_767; i++) {
            assertEquals(i, RegistryRemapper.mapBlockUp768(
                    RegistryRemapper.mapBlock767To768(i)), "block " + i);
        }
        for (int i = 0; i <= RegistryRemapper.MAX_MENU_767; i++) {
            assertEquals(i, RegistryRemapper.mapMenuUp768(
                    RegistryRemapper.mapMenu767To768(i)), "menu " + i);
        }
    }

    @Test
    void exhaustiveForwardStayInRange() {
        for (int i = 0; i <= RegistryRemapper.MAX_BLOCK_STATE_767; i++) {
            int mapped = RegistryRemapper.mapBlockState767To768(i);
            assertTrue(mapped >= 0 && mapped <= RegistryRemapper.MAX_BLOCK_STATE_768,
                    "767 blockstate " + i + " -> " + mapped);
        }
        for (int i = 0; i <= RegistryRemapper.MAX_ITEM_767; i++) {
            int mapped = RegistryRemapper.mapItem767To768(i);
            assertTrue(mapped >= 0 && mapped <= RegistryRemapper.MAX_ITEM_768,
                    "767 item " + i + " -> " + mapped);
        }
        for (int i = 0; i <= RegistryRemapper.MAX_SOUND_767; i++) {
            int mapped = RegistryRemapper.mapSound767To768(i);
            assertTrue(mapped >= 0 && mapped <= RegistryRemapper.MAX_SOUND_768,
                    "767 sound " + i + " -> " + mapped);
        }
        for (int i = 0; i <= RegistryRemapper.MAX_PARTICLE_767; i++) {
            int mapped = RegistryRemapper.mapParticle767To768(i);
            assertTrue(mapped >= 0 && mapped <= RegistryRemapper.MAX_PARTICLE_768,
                    "767 particle " + i + " -> " + mapped);
        }
    }

    // ---- 合成表 763↔768 / 764↔768 / 765↔768 / 766↔768 ----

    @Test
    void composed763To768() {
        assertEquals(817, RegistryRemapper.mapItem763To768(757)); // scute → turtle_scute
        assertEquals(85, RegistryRemapper.mapEntityType763To768(9)); // boat → oak_boat
        assertEquals(86, RegistryRemapper.mapEntityType763To768(13)); // chest_boat → oak_chest_boat
        assertEquals(724, RegistryRemapper.mapSound763To768(558)); // item.goat_horn.play
        assertEquals(1386, RegistryRemapper.mapSound763To768(1264)); // block.stone.break
        assertEquals(0, RegistryRemapper.mapEffect763To768(1)); // Speed（1ベース→0ベース）
        assertEquals(32, RegistryRemapper.mapEffect763To768(33)); // Unluck
        assertEquals(9, RegistryRemapper.mapBlockEntityType763To768(9)); // mob_spawner
        assertEquals(11, RegistryRemapper.mapBlockEntityType763To768(10)); // piston
        assertEquals(41, RegistryRemapper.mapBlockEntityType763To768(40));
        assertEquals("block.stone.break",
                RegistryRemapper.mapSoundName763To768("block.stone.break"));
    }

    @Test
    void composed768To763() {
        assertEquals(2873, RegistryRemapper.mapBlockState768To763(27119)); // vault → spawner
        assertEquals(1, RegistryRemapper.mapBlockState768To763(2917)); // creaking_heart → stone
        assertEquals(1, RegistryRemapper.mapBlockState768To763(157)); // pale_oak_log → stone
        assertEquals(757, RegistryRemapper.mapItem768To763(817)); // turtle_scute → scute
        assertEquals(1, RegistryRemapper.mapItem768To763(140)); // pale_oak_log → stone
        assertEquals(9, RegistryRemapper.mapEntityType768To763(85)); // oak_boat → boat
        assertEquals(9, RegistryRemapper.mapEntityType768To763(0)); // acacia_boat → boat
        assertEquals(72, RegistryRemapper.mapEntityType768To763(29)); // creaking → pig
        assertEquals(1264, RegistryRemapper.mapSound768To763(1386)); // stone.break
        assertEquals(1, RegistryRemapper.mapEffect768To763(0)); // Speed（0ベース→1ベース）
        assertEquals(33, RegistryRemapper.mapEffect768To763(32)); // Unluck
        assertEquals(1, RegistryRemapper.mapEffect768To763(33)); // TrialOmen → Speed
        assertEquals(-1, RegistryRemapper.mapBlockEntityType768To763(43)); // trial_spawner → DROP
        assertEquals(-1, RegistryRemapper.mapBlockEntityType768To763(10)); // creaking_heart → DROP
        assertEquals(9, RegistryRemapper.mapBlockEntityType768To763(9)); // mob_spawner
        assertEquals(10, RegistryRemapper.mapBlockEntityType768To763(11)); // piston
        assertEquals(40, RegistryRemapper.mapBlockEntityType768To763(41));
        assertEquals(-1, RegistryRemapper.mapBlockEntityType768To763(42)); // crafter → DROP
        // 763に trial_spawner の音が無いため、768の ominous_activate は
        // 石の破壊音へ（数値の合成表と一致）。
        assertEquals("block.stone.break",
                RegistryRemapper.mapSoundName768To763("block.trial_spawner.ominous_activate"));
    }

    @Test
    void composed764To768AndBack() {
        assertEquals(85, RegistryRemapper.mapEntityType764To768(9)); // boat → oak_boat
        assertEquals(2873, RegistryRemapper.mapBlockState768To764(27119)); // vault → spawner
        assertEquals(11, RegistryRemapper.mapMenu768To764(7)); // crafter_3x3 → crafting
        assertEquals(1, RegistryRemapper.mapItem768To764(1214)); // music_disc_creator → stone
        assertEquals(0, RegistryRemapper.mapEffect764To768(0)); // Speed
        assertEquals(0, RegistryRemapper.mapEffect768To764(38)); // Infested → Speed
        assertEquals(9, RegistryRemapper.mapBlockEntityType764To768(9)); // mob_spawner
        assertEquals(11, RegistryRemapper.mapBlockEntityType764To768(10)); // piston
        assertEquals(41, RegistryRemapper.mapBlockEntityType764To768(40));
        assertEquals(9, RegistryRemapper.mapBlockEntityType768To764(9)); // mob_spawner
        assertEquals(10, RegistryRemapper.mapBlockEntityType768To764(11)); // piston
        assertEquals(40, RegistryRemapper.mapBlockEntityType768To764(41));
        assertEquals(-1, RegistryRemapper.mapBlockEntityType768To764(42)); // crafter → DROP
        assertEquals(-1, RegistryRemapper.mapBlockEntityType768To764(44)); // vault → DROP
        assertEquals(-1, RegistryRemapper.mapBlockEntityType768To764(43)); // trial_spawner → DROP
    }

    @Test
    void composed765To768AndBack() {
        assertEquals(817, RegistryRemapper.mapItem765To768(794)); // scute → turtle_scute
        assertEquals(1555, RegistryRemapper.mapSound765To768(1466)); // generic.wind_burst → wind_charge.wind_burst
        assertEquals(1386, RegistryRemapper.mapSound765To768(1312)); // block.stone.break
        assertEquals(794, RegistryRemapper.mapItem768To765(817)); // turtle_scute → scute
        assertEquals(26638, RegistryRemapper.mapBlockState768To765(27119)); // vault → trial_spawner
        assertEquals(1, RegistryRemapper.mapBlockState768To765(2917)); // creaking_heart → stone
        assertEquals(1, RegistryRemapper.mapItem768To765(1214)); // music_disc_creator → stone
        assertEquals(0, RegistryRemapper.mapEffect765To768(0)); // Speed
        assertEquals(0, RegistryRemapper.mapEffect768To765(38)); // Infested → Speed
        assertEquals(43, RegistryRemapper.mapBlockEntityType765To768(42)); // trial_spawner
        assertEquals(-1, RegistryRemapper.mapBlockEntityType768To765(44)); // vault → DROP
        assertEquals("block.trial_spawner.ominous_activate",
                RegistryRemapper.mapSoundName765To768("block.trial_spawner.charge_activate"));
        assertEquals("block.stone.break",
                RegistryRemapper.mapSoundName768To765("music_disc.creator"));
        assertEquals("block.stone.break",
                RegistryRemapper.mapSoundName768To765("block.trial_spawner.ominous_activate"));
    }

    @Test
    void composed766To768AndBack() {
        assertEquals(1134, RegistryRemapper.mapItem766To768(1329)); // breeze_rod
        assertEquals(724, RegistryRemapper.mapSound766To768(617)); // item.goat_horn.play
        assertEquals(706, RegistryRemapper.mapSound768To766(724)); // item.goat_horn.sound.0
        assertEquals(1328, RegistryRemapper.mapItem768To766(1374)); // ominous_bottle
        assertEquals(44, RegistryRemapper.mapBlockEntityType766To768(43)); // vault
        assertEquals(43, RegistryRemapper.mapBlockEntityType768To766(44)); // vault
        assertEquals(-1, RegistryRemapper.mapBlockEntityType768To766(10)); // creaking_heart → DROP
        assertEquals("block.trial_spawner.ominous_activate",
                RegistryRemapper.mapSoundName766To768("block.trial_spawner.charge_activate"));
        assertEquals("block.trial_spawner.charge_activate",
                RegistryRemapper.mapSoundName768To766("block.trial_spawner.ominous_activate"));
    }

    @Test
    void protocolDispatchRoutes768() {
        // プロトコル指定のオーバーロードが 768 を 768表へ振ること。
        assertEquals(27113, RegistryRemapper.mapBlockState(26638, 768));
        assertEquals(817, RegistryRemapper.mapItem(794, 768));
        assertEquals(95, RegistryRemapper.mapEntityType(73, 768));
        assertEquals(1386, RegistryRemapper.mapSound(1312, 768));
        assertEquals(20, RegistryRemapper.mapParticle(0, 768));
        assertEquals(1, RegistryRemapper.mapBlock(1, 768));
        assertEquals(12, RegistryRemapper.mapMenu(12, 768));
        assertEquals(0, RegistryRemapper.mapEffect(0, 768));
        assertEquals(43, RegistryRemapper.mapBlockEntityType(42, 768));
        assertEquals("block.trial_spawner.ominous_activate",
                RegistryRemapper.mapSoundName("block.trial_spawner.charge_activate", 768));
        // 768クライアントのアイテムID → 765サーバーのアイテムID。
        assertEquals(794, RegistryRemapper.mapItemReverse(817, 768));
        assertEquals(1, RegistryRemapper.mapItemReverse(140, 768));
        // 767の振り分けが変わっていないこと。
        assertEquals(26644, RegistryRemapper.mapBlockState(26638, 767));
        assertEquals(795, RegistryRemapper.mapItem(794, 767));
    }
}
