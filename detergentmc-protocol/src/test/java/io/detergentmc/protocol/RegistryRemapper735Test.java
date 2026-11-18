package io.detergentmc.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

/**
 * {@link RegistryRemapper} の736（1.16.1）⇔735（1.16）向けスポットチェックと往復テスト。
 * 期待値は ViaVersion の mapping-1.16.json を実バニラ 1.16 の registries.json／
 * reports で補正したもの（vv-1.16.json）と minecraft-data（pc/1.16.1 の
 * blocks.json／items.json／entities.json／biomes.json）から計算したもの（推測ではない）。
 * 生成スクリプト（detergentmc-protocol/tools/gen-735-tables.py）の出力と同じ値。
 * 検証は tools/verify-735-tables.py の実行結果を参照。
 *
 * <p>1.16 と 1.16.1 のレジストリ内容は同一（実バニラの reports がバイト同一。
 * registries-1.16.json と registries-1.16.1.json は cmp でバイト同一を確認済み）。
 * 736⇔735 は恒等写像：順向き表・反転表とも全種恒等。補正は 0 件
 * （maps735-corrections.json）。
 * ブロック状態 17,104 件・ブロック 763 件・アイテム 975 件・エンティティ 107 件・
 * 音 985 件・パーティクル 72 件・メニュー 24 件・属性 13 件・エンチャント 38 件・
 * エフェクト 32 件・ブロックエンティティ 33 件・バイオーム 79 件（id 0〜173、隙間 95 件）。
 * ダメージ種別は 42 件の恒等写像のプレースホルダ。
 * アイテム・エンティティ・音・ブロック状態の順序は 1.16 と 1.16.1 で同じ。
 */
class RegistryRemapper735Test {

    // ---- 736 → 735 ----

    @Test
    void blockStatesForward() {
        assertEquals(0, RegistryRemapper.mapBlockState736To735(0)); // air
        assertEquals(1, RegistryRemapper.mapBlockState736To735(1)); // stone
        assertEquals(1311, RegistryRemapper.mapBlockState736To735(1311)); // powered_rail
        assertEquals(5145, RegistryRemapper.mapBlockState736To735(5145)); // end_portal
        assertEquals(9227, RegistryRemapper.mapBlockState736To735(9227)); // repeating_command_block
        assertEquals(17103, RegistryRemapper.mapBlockState736To735(17103)); // 末尾
        assertEquals(1, RegistryRemapper.mapBlockState736To735(99999)); // 範囲外は石
        assertEquals(4731, RegistryRemapper.mapBlockState736To735(4731)); // glass_pane
        assertEquals(14890, RegistryRemapper.mapBlockState736To735(14890)); // campfire
    }

    @Test
    void blockStatesReverse() {
        assertEquals(0, RegistryRemapper.mapBlockState735To736(0)); // air
        assertEquals(1, RegistryRemapper.mapBlockState735To736(1)); // stone
        assertEquals(1311, RegistryRemapper.mapBlockState735To736(1311)); // powered_rail
        assertEquals(5141, RegistryRemapper.mapBlockState735To736(5141)); // end_portal の前後（5141 は 736 の id）
        assertEquals(9223, RegistryRemapper.mapBlockState735To736(9223)); // repeating_command_block の前後
        assertEquals(17103, RegistryRemapper.mapBlockState735To736(17103)); // 末尾
        assertEquals(1, RegistryRemapper.mapBlockState735To736(99999)); // 範囲外は石
        assertEquals(14886, RegistryRemapper.mapBlockState735To736(14886)); // campfire の前後（恒等写像。補正なし）
        assertEquals(14888, RegistryRemapper.mapBlockState735To736(14888)); // 恒等写像。補正なし
    }

    @Test
    void items() {
        assertEquals(0, RegistryRemapper.mapItem736To735(0)); // air
        assertEquals(1, RegistryRemapper.mapItem736To735(1)); // stone
        assertEquals(372, RegistryRemapper.mapItem736To735(372)); // grass_path
        assertEquals(791, RegistryRemapper.mapItem736To735(791)); // pillager_spawn_egg
        assertEquals(809, RegistryRemapper.mapItem736To735(809)); // turtle_spawn_egg
        assertEquals(840, RegistryRemapper.mapItem736To735(840)); // dragon_head
        assertEquals(974, RegistryRemapper.mapItem736To735(974)); // 末尾
        assertEquals(1, RegistryRemapper.mapItem736To735(99999)); // 範囲外は石
        assertEquals(0, RegistryRemapper.mapItem735To736(0));
        assertEquals(1, RegistryRemapper.mapItem735To736(1));
        assertEquals(372, RegistryRemapper.mapItem735To736(372)); // grass_path
        assertEquals(790, RegistryRemapper.mapItem735To736(790)); // piglin_spawn_egg
        assertEquals(808, RegistryRemapper.mapItem735To736(808)); // tropical_fish_spawn_egg
        assertEquals(839, RegistryRemapper.mapItem735To736(839)); // dragon_head の前後
        assertEquals(974, RegistryRemapper.mapItem735To736(974)); // 末尾
        assertEquals(1, RegistryRemapper.mapItem735To736(99999)); // 範囲外は石
    }

    @Test
    void entities() {
        assertEquals(59, RegistryRemapper.mapEntityType736To735(59)); // pig
        assertEquals(61, RegistryRemapper.mapEntityType736To735(61)); // pillager
        assertEquals(90, RegistryRemapper.mapEntityType736To735(90)); // turtle
        assertEquals(1, RegistryRemapper.mapEntityType736To735(1)); // armor_stand
        assertEquals(106, RegistryRemapper.mapEntityType736To735(106)); // 末尾
        assertEquals(59, RegistryRemapper.mapEntityType736To735(99999)); // 範囲外はブタ
        assertEquals(59, RegistryRemapper.mapEntityType735To736(59)); // pig
        assertEquals(60, RegistryRemapper.mapEntityType735To736(60)); // piglin
        assertEquals(89, RegistryRemapper.mapEntityType735To736(89)); // tropical_fish
        assertEquals(106, RegistryRemapper.mapEntityType735To736(106)); // 末尾
        assertEquals(59, RegistryRemapper.mapEntityType735To736(99999)); // 範囲外はブタ
    }

    @Test
    void sounds() {
        assertEquals(0, RegistryRemapper.mapSound736To735(0)); // ambient.cave
        assertEquals(587, RegistryRemapper.mapSound736To735(587)); // entity.parrot.imitate.pillager
        assertEquals(626, RegistryRemapper.mapSound736To735(626)); // entity.piglin.angry
        assertEquals(816, RegistryRemapper.mapSound736To735(816)); // block.stone_pressure_plate.click_on
        assertEquals(984, RegistryRemapper.mapSound736To735(984)); // 末尾
        assertEquals(809, RegistryRemapper.mapSound736To735(99999)); // 範囲外は石の破壊音
        assertEquals(0, RegistryRemapper.mapSound735To736(0)); // ambient.cave
        assertEquals(586, RegistryRemapper.mapSound735To736(586)); // entity.parrot.imitate.piglin
        assertEquals(616, RegistryRemapper.mapSound735To736(616)); // entity.piglin.ambient
        assertEquals(809, RegistryRemapper.mapSound735To736(809)); // block.stone.break
        assertEquals(984, RegistryRemapper.mapSound735To736(984)); // 末尾
        assertEquals(809, RegistryRemapper.mapSound735To736(99999)); // 範囲外は石の破壊音（736 側の代替先 809）
    }

    @Test
    void soundNames() {
        // 736→735 は恒等写像（代替表は空）。736／735 のどちらにも無い
        // piglin_brute 系の名前は入力がそのまま返る。
        assertEquals("entity.parrot.imitate.piglin",
                RegistryRemapper.mapSoundName736To735("entity.parrot.imitate.piglin"));
        assertEquals("entity.piglin.ambient",
                RegistryRemapper.mapSoundName736To735("entity.piglin.ambient"));
        assertEquals("entity.parrot.imitate.piglin_brute",
                RegistryRemapper.mapSoundName736To735("entity.parrot.imitate.piglin_brute"));
        assertEquals("entity.piglin_brute.ambient",
                RegistryRemapper.mapSoundName736To735("entity.piglin_brute.ambient"));
        assertEquals("block.stone.break",
                RegistryRemapper.mapSoundName736To735("block.stone.break"));
        assertEquals("entity.piglin.ambient",
                RegistryRemapper.mapSoundName735To736("entity.piglin.ambient"));
        assertEquals("ambient.cave",
                RegistryRemapper.mapSoundName735To736("ambient.cave"));
    }

    @Test
    void particles() {
        assertEquals(0, RegistryRemapper.mapParticle736To735(0)); // ambient_entity_effect
        assertEquals(19, RegistryRemapper.mapParticle736To735(19)); // end_rod
        assertEquals(71, RegistryRemapper.mapParticle736To735(71)); // 末尾
        assertEquals(0, RegistryRemapper.mapParticle736To735(99999)); // 範囲外
        assertEquals(0, RegistryRemapper.mapParticle735To736(0)); // ambient_entity_effect
        assertEquals(71, RegistryRemapper.mapParticle735To736(71)); // 末尾
        assertEquals(0, RegistryRemapper.mapParticle735To736(99999)); // 範囲外
    }

    @Test
    void blocksAndMenus() {
        assertEquals(1, RegistryRemapper.mapBlock736To735(1)); // stone
        assertEquals(0, RegistryRemapper.mapBlock736To735(0)); // air
        assertEquals(498, RegistryRemapper.mapBlock736To735(498)); // grass_path
        assertEquals(762, RegistryRemapper.mapBlock736To735(762)); // 末尾
        assertEquals(1, RegistryRemapper.mapBlock736To735(99999)); // 範囲外は石
        assertEquals(1, RegistryRemapper.mapBlock735To736(1));
        assertEquals(762, RegistryRemapper.mapBlock735To736(762)); // 末尾
        assertEquals(1, RegistryRemapper.mapBlock735To736(99999)); // 範囲外は石
        assertEquals(11, RegistryRemapper.mapMenu736To735(11)); // crafting
        assertEquals(0, RegistryRemapper.mapMenu736To735(0)); // generic_9x1
        assertEquals(23, RegistryRemapper.mapMenu736To735(23)); // stonecutter（末尾）
        assertEquals(11, RegistryRemapper.mapMenu736To735(99999)); // 範囲外は作業台
        assertEquals(11, RegistryRemapper.mapMenu735To736(11));
        assertEquals(23, RegistryRemapper.mapMenu735To736(23)); // 末尾
        assertEquals(11, RegistryRemapper.mapMenu735To736(99999)); // 範囲外は作業台
    }

    @Test
    void biomes() {
        // 順向き・反転とも 174 件（id 0〜173）。0〜173 は同じ id・同じ名前。
        // 新規バイオームは無く、隙間の id だけが平原（1）へ。
        assertEquals(127, RegistryRemapper.mapBiome736To735(127)); // the_void
        assertEquals(1, RegistryRemapper.mapBiome736To735(1)); // plains
        assertEquals(12, RegistryRemapper.mapBiome736To735(12)); // snowy_tundra
        assertEquals(3, RegistryRemapper.mapBiome736To735(3)); // mountains
        assertEquals(1, RegistryRemapper.mapBiome736To735(51)); // 隙間の id は平原
        assertEquals(1, RegistryRemapper.mapBiome736To735(99999)); // 範囲外は平原
        assertEquals(127, RegistryRemapper.mapBiome735To736(127)); // the_void
        assertEquals(12, RegistryRemapper.mapBiome735To736(12)); // snowy_tundra
        assertEquals(1, RegistryRemapper.mapBiome735To736(51)); // 隙間の id は平原
        assertEquals(1, RegistryRemapper.mapBiome735To736(99999)); // 範囲外は平原
    }

    @Test
    void damageTypesAreIdentity() {
        assertEquals(0, RegistryRemapper.mapDamageType736To735(0));
        assertEquals(16, RegistryRemapper.mapDamageType736To735(16)); // generic
        assertEquals(41, RegistryRemapper.mapDamageType736To735(41));
        assertEquals(0, RegistryRemapper.mapDamageType735To736(0));
        assertEquals(41, RegistryRemapper.mapDamageType735To736(41));
        assertEquals(RegistryRemapper.DROP, RegistryRemapper.mapDamageType736To735(99999));
        assertEquals(RegistryRemapper.DROP, RegistryRemapper.mapDamageType735To736(99999));
    }

    @Test
    void effects() {
        assertEquals(1, RegistryRemapper.mapEffect736To735(1)); // speed
        assertEquals(32, RegistryRemapper.mapEffect736To735(32)); // 末尾
        assertEquals(1, RegistryRemapper.mapEffect736To735(99999)); // 範囲外は Speed
        assertEquals(1, RegistryRemapper.mapEffect735To736(1)); // speed
        assertEquals(32, RegistryRemapper.mapEffect735To736(32)); // 末尾
        assertEquals(1, RegistryRemapper.mapEffect735To736(99999)); // 範囲外は Speed
    }

    @Test
    void blockEntities() {
        // 736・735 ともに 33 件（0〜32）で同一順序のため恒等写像（DROP は無い）。
        // 33 は範囲外のため DROP。
        assertEquals(0, RegistryRemapper.mapBlockEntityType736To735(0)); // furnace
        assertEquals(32, RegistryRemapper.mapBlockEntityType736To735(32)); // 末尾
        assertEquals(RegistryRemapper.DROP,
                RegistryRemapper.mapBlockEntityType736To735(33)); // 範囲外
        assertEquals(RegistryRemapper.DROP,
                RegistryRemapper.mapBlockEntityType736To735(99999));
        assertEquals(0, RegistryRemapper.mapBlockEntityType735To736(0)); // furnace
        assertEquals(32, RegistryRemapper.mapBlockEntityType735To736(32)); // 末尾
        assertEquals(RegistryRemapper.DROP,
                RegistryRemapper.mapBlockEntityType735To736(33)); // 範囲外
        assertEquals(RegistryRemapper.DROP,
                RegistryRemapper.mapBlockEntityType735To736(99999));
    }

    @Test
    void attributes() {
        assertEquals(0, RegistryRemapper.mapAttribute736To735(0));
        assertEquals(12, RegistryRemapper.mapAttribute736To735(12)); // 末尾
        assertEquals(RegistryRemapper.DROP, RegistryRemapper.mapAttribute736To735(99999));
        assertEquals(0, RegistryRemapper.mapAttribute735To736(0));
        assertEquals(12, RegistryRemapper.mapAttribute735To736(12)); // 末尾
        assertEquals(RegistryRemapper.DROP, RegistryRemapper.mapAttribute735To736(99999));
        String name = RegistryRemapper.attributeName735(0);
        assertNotNull(name);
        assertEquals("generic.max_health", name);
        assertEquals(0, RegistryRemapper.attributeId735(name));
        assertEquals(0, RegistryRemapper.attributeId735("minecraft:" + name));
        assertNull(RegistryRemapper.attributeName735(99999));
        assertEquals(RegistryRemapper.DROP,
                RegistryRemapper.attributeId735("minecraft:generic.unknown_attribute"));
        assertEquals(RegistryRemapper.DROP, RegistryRemapper.attributeId735(null));
    }

    @Test
    void enchantments() {
        assertEquals(0, RegistryRemapper.mapEnchantment736To735(0)); // protection
        assertEquals(37, RegistryRemapper.mapEnchantment736To735(37)); // vanishing_curse（末尾）
        assertEquals(RegistryRemapper.DROP, RegistryRemapper.mapEnchantment736To735(99999));
        assertEquals(0, RegistryRemapper.mapEnchantment735To736(0));
        assertEquals(37, RegistryRemapper.mapEnchantment735To736(37)); // 末尾
        assertEquals(RegistryRemapper.DROP, RegistryRemapper.mapEnchantment735To736(99999));
    }

    @Test
    void dispatch() {
        assertEquals(0, RegistryRemapper.mapBlockState(0, 735)); // air
        assertEquals(1, RegistryRemapper.mapBlockState(1, 735)); // stone
        assertEquals(0, RegistryRemapper.mapItem(0, 735)); // air
        assertEquals(1, RegistryRemapper.mapItem(1, 735)); // stone
        assertEquals(1, RegistryRemapper.mapEffect(0, 735)); // speed（765=0、763以降=1。735 段は恒等写像）
        assertEquals("ambient.cave", RegistryRemapper.mapSoundName("ambient.cave", 735));
        // mapItemReverse は正しい順序の合成（735→736 は最も内側）。真値アンカー：
        // turtle_spawn_egg（735=809→736=809→751=810）→ 765 id 1068、
        // dragon_head（735=840→736=840→751=841）→ 765 id 1101
        // （736=809→1068・736=840→1101 は 736 の Probe736b.java で実測。
        // 735→736 が恒等写像のため 735 分岐も同じ値を返す）。
        assertEquals(1068, RegistryRemapper.mapItemReverse(809, 735));
        assertEquals(1101, RegistryRemapper.mapItemReverse(840, 735));
        assertEquals(0, RegistryRemapper.mapItemReverse(0, 735)); // air
        assertEquals(500, RegistryRemapper.mapItemReverse(
                RegistryRemapper.mapItem(500, 735), 735));
    }

    @Test
    void roundTripIsIdentity() {
        // 下向き∘上向きは 735 側の全 id で恒等写像（Check735Remap で全件確認）。
        // ここでは代表値だけ確認する。
        assertEquals(1311, RegistryRemapper.mapBlockState736To735(
                RegistryRemapper.mapBlockState735To736(1311))); // powered_rail
        assertEquals(17103, RegistryRemapper.mapBlockState736To735(
                RegistryRemapper.mapBlockState735To736(17103))); // 末尾
        assertEquals(809, RegistryRemapper.mapItem736To735(
                RegistryRemapper.mapItem735To736(809))); // turtle_spawn_egg
        assertEquals(974, RegistryRemapper.mapItem736To735(
                RegistryRemapper.mapItem735To736(974))); // 末尾
        assertEquals(59, RegistryRemapper.mapEntityType736To735(
                RegistryRemapper.mapEntityType735To736(59))); // pig
        assertEquals(127, RegistryRemapper.mapBiome736To735(
                RegistryRemapper.mapBiome735To736(127))); // the_void
        assertEquals(809, RegistryRemapper.mapSound736To735(
                RegistryRemapper.mapSound735To736(809))); // block.stone.break
        assertEquals(32, RegistryRemapper.mapBlockEntityType736To735(
                RegistryRemapper.mapBlockEntityType735To736(32))); // 末尾
    }
}
