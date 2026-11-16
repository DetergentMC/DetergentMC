package io.detergentmc.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

/**
 * {@link RegistryRemapper} の754（1.16.4／1.16.5）⇔755（1.17）向けスポットチェックと往復テスト。
 * 期待値は ViaVersion の mapping-1.16.json を実バニラ 1.16.5 の registries.json／
 * reports で補正したもの（vv-1.16.json）と minecraft-data（pc/1.16.2 の
 * blocks.json／items.json／entities.json／biomes.json）から計算したもの（推測ではない）。
 * 生成スクリプト（detergentmc-protocol/tools/gen-754-tables.py）の出力と同じ値。
 * 検証は tools/verify-754-tables.py の実行結果を参照。
 *
 * <p>1.16.5 と 1.17 のレジストリ差分：ブロック状態 20,342→17,112、新ブロック 136 種・
 * 約 3,281 状態。ブロック 898→763（改名 1：grass_path→dirt_path）。
 * アイテム 1,100→976（改名 1）。エンティティ 113→108（新 5：axolotl・
 * glow_item_frame・glow_squid・goat・marker）。音 1,190→992（新 199・改名 1：
 * block.sweet_berry_bush.pick_berries→item.sweet_berries.pick_from_bush）。
 * パーティクル 89→72（新 17）。メニュー 24→24（同一順序）、属性 13→13（同一順序）、
 * エンチャント 38→38（同一順序）、エフェクト 32→32（同一順序）。
 * ブロックエンティティ 34→33（1.17 の新規 sculk_sensor（33）が 1.16.5 には無い）。
 * バイオーム 81→79（1.17 の新規 dripstone_caves（174）・lush_caves（175）が無い。
 * 0〜173 は同じ id・同じ名前。隙間 95 件）。
 * 補正はブロック状態 46 件・アイテム 5 件・音 35 件（maps754-corrections.json）。
 * ダメージ種別は 42 件の恒等写像のプレースホルダ。
 * Join Game はフィールド構成が同一（実バニラ 1.16.5 の生キャプチャで 30,847 バイト。
 * dimensionCodec は dimension_type 4 件・worldgen/biome 79 件。
 * 1.17 の 31,555 バイトとの差はバイオーム 2 件と dimension element の
 * min_y／height の有無）。
 */
class RegistryRemapper754Test {

    // ---- 755 → 754 ----

    @Test
    void blockStatesForward() {
        assertEquals(0, RegistryRemapper.mapBlockState755To754(0)); // air
        assertEquals(1, RegistryRemapper.mapBlockState755To754(1)); // stone
        // powered_rail の waterlogged=false（補正 46 件の代表。invert() だけだと
        // waterlogged 付き状態にすり替わる）。
        assertEquals(1311, RegistryRemapper.mapBlockState755To754(1350));
        // 大釜：1.17 の裸の cauldron は 1.16.5 の cauldron[level=0] へ。
        assertEquals(5145, RegistryRemapper.mapBlockState755To754(5342)); // cauldron
        assertEquals(5148, RegistryRemapper.mapBlockState755To754(5345)); // water_cauldron[level=3]
        assertEquals(5148, RegistryRemapper.mapBlockState755To754(5346)); // lava_cauldron → level=3
        assertEquals(9227, RegistryRemapper.mapBlockState755To754(9473)); // dirt_path → grass_path
        assertEquals(6310, RegistryRemapper.mapBlockState755To754(20341)); // 末尾
        assertEquals(1, RegistryRemapper.mapBlockState755To754(99999)); // 範囲外は石
    }

    @Test
    void blockStatesReverse() {
        assertEquals(0, RegistryRemapper.mapBlockState754To755(0)); // air
        assertEquals(1, RegistryRemapper.mapBlockState754To755(1)); // stone
        assertEquals(1350, RegistryRemapper.mapBlockState754To755(1311)); // powered_rail
        assertEquals(5342, RegistryRemapper.mapBlockState754To755(5145)); // cauldron[level=0]
        assertEquals(5345, RegistryRemapper.mapBlockState754To755(5148)); // cauldron[level=3]
        assertEquals(9473, RegistryRemapper.mapBlockState754To755(9227)); // grass_path → dirt_path
        assertEquals(17357, RegistryRemapper.mapBlockState754To755(17111)); // 末尾
        assertEquals(1, RegistryRemapper.mapBlockState754To755(99999)); // 範囲外は石
    }

    @Test
    void items() {
        assertEquals(0, RegistryRemapper.mapItem755To754(0)); // air
        assertEquals(1, RegistryRemapper.mapItem755To754(1)); // stone
        assertEquals(372, RegistryRemapper.mapItem755To754(393)); // dirt_path → grass_path
        assertEquals(1, RegistryRemapper.mapItem755To754(8)); // deepslate → stone
        assertEquals(579, RegistryRemapper.mapItem755To754(694)); // copper_ingot → iron_ingot
        assertEquals(850, RegistryRemapper.mapItem755To754(690)); // amethyst_shard → quartz
        assertEquals(180, RegistryRemapper.mapItem755To754(796)); // bundle → chest
        assertEquals(613, RegistryRemapper.mapItem755To754(799)); // spyglass → stick
        assertEquals(171, RegistryRemapper.mapItem755To754(1078)); // candle → torch
        assertEquals(809, RegistryRemapper.mapItem755To754(926)); // tropical_fish_spawn_egg
        assertEquals(136, RegistryRemapper.mapItem755To754(67)); // gold_block（補正の代表）
        assertEquals(1, RegistryRemapper.mapItem755To754(1099)); // pointed_dripstone（末尾）→ stone
        assertEquals(1, RegistryRemapper.mapItem755To754(99999)); // 範囲外は石
        assertEquals(0, RegistryRemapper.mapItem754To755(0));
        assertEquals(1, RegistryRemapper.mapItem754To755(1));
        assertEquals(393, RegistryRemapper.mapItem754To755(372)); // grass_path → dirt_path
        assertEquals(926, RegistryRemapper.mapItem754To755(809)); // tropical_fish_spawn_egg
        assertEquals(957, RegistryRemapper.mapItem754To755(840)); // creeper_head
        assertEquals(1077, RegistryRemapper.mapItem754To755(975)); // 末尾
        assertEquals(1, RegistryRemapper.mapItem754To755(99999)); // 範囲外は石
    }

    @Test
    void entities() {
        assertEquals(90, RegistryRemapper.mapEntityType755To754(3)); // axolotl → tropical_fish
        assertEquals(81, RegistryRemapper.mapEntityType755To754(33)); // glow_squid → squid
        assertEquals(69, RegistryRemapper.mapEntityType755To754(34)); // goat → sheep
        assertEquals(1, RegistryRemapper.mapEntityType755To754(49)); // marker → armor_stand
        assertEquals(59, RegistryRemapper.mapEntityType755To754(64)); // pig
        assertEquals(107, RegistryRemapper.mapEntityType755To754(112)); // 末尾
        assertEquals(59, RegistryRemapper.mapEntityType755To754(99999)); // 範囲外はブタ
        assertEquals(95, RegistryRemapper.mapEntityType754To755(90)); // tropical_fish（axolotl ではない）
        assertEquals(64, RegistryRemapper.mapEntityType754To755(59)); // pig
        assertEquals(1, RegistryRemapper.mapEntityType754To755(1)); // armor_stand
        assertEquals(112, RegistryRemapper.mapEntityType754To755(107)); // fishing_bobber（末尾。755 では 112）
        assertEquals(64, RegistryRemapper.mapEntityType754To755(99999)); // 範囲外はブタ
    }

    @Test
    void sounds() {
        assertEquals(0, RegistryRemapper.mapSound755To754(0)); // ambient.cave
        assertEquals(816, RegistryRemapper.mapSound755To754(1005)); // block.stone.break
        assertEquals(831, RegistryRemapper.mapSound755To754(1020)); // sweet_berry_bush → sweet_berries
        assertEquals(816, RegistryRemapper.mapSound755To754(251)); // block.deepslate.break → stone
        assertEquals(850, RegistryRemapper.mapSound755To754(67)); // entity.axolotl.hurt → tropical_fish
        assertEquals(991, RegistryRemapper.mapSound755To754(1189)); // 末尾
        assertEquals(816, RegistryRemapper.mapSound755To754(99999)); // 範囲外は石の破壊音
        assertEquals(0, RegistryRemapper.mapSound754To755(0)); // ambient.cave
        assertEquals(1005, RegistryRemapper.mapSound754To755(816)); // block.stone.break
        assertEquals(1020, RegistryRemapper.mapSound754To755(831)); // sweet_berries → sweet_berry_bush
        assertEquals(1189, RegistryRemapper.mapSound754To755(991)); // entity.zombie_villager.step（末尾。755 では 1189）
        assertEquals(1005, RegistryRemapper.mapSound754To755(99999)); // 範囲外は石の破壊音
    }

    @Test
    void soundNames() {
        assertEquals("block.stone.break",
                RegistryRemapper.mapSoundName755To754("block.deepslate.break"));
        assertEquals("entity.tropical_fish.hurt",
                RegistryRemapper.mapSoundName755To754("entity.axolotl.hurt"));
        assertEquals("block.stone.break",
                RegistryRemapper.mapSoundName755To754("block.stone.break"));
        assertEquals("ambient.cave",
                RegistryRemapper.mapSoundName755To754("ambient.cave"));
        assertEquals("block.sweet_berry_bush.pick_berries",
                RegistryRemapper.mapSoundName754To755("item.sweet_berries.pick_from_bush"));
        assertEquals("block.stone.break",
                RegistryRemapper.mapSoundName754To755("block.stone.break"));
    }

    @Test
    void particles() {
        assertEquals(0, RegistryRemapper.mapParticle755To754(0)); // ambient_entity_effect
        assertEquals(19, RegistryRemapper.mapParticle755To754(3)); // light → end_rod
        assertEquals(9, RegistryRemapper.mapParticle755To754(79)); // dripping_dripstone_lava
        assertEquals(47, RegistryRemapper.mapParticle755To754(83)); // glow_squid_ink → squid_ink
        assertEquals(71, RegistryRemapper.mapParticle755To754(78)); // snowflake → white_ash
        assertEquals(6, RegistryRemapper.mapParticle755To754(88)); // 末尾
        assertEquals(0, RegistryRemapper.mapParticle755To754(99999)); // 範囲外
        assertEquals(0, RegistryRemapper.mapParticle754To755(0)); // ambient_entity_effect
        assertEquals(21, RegistryRemapper.mapParticle754To755(19)); // end_rod
        assertEquals(76, RegistryRemapper.mapParticle754To755(71)); // white_ash（末尾。755 では 76）
        assertEquals(0, RegistryRemapper.mapParticle754To755(99999)); // 範囲外
    }

    @Test
    void blocksAndMenus() {
        assertEquals(1, RegistryRemapper.mapBlock755To754(1)); // stone
        assertEquals(0, RegistryRemapper.mapBlock755To754(0)); // air
        assertEquals(1, RegistryRemapper.mapBlock755To754(871)); // deepslate → stone
        assertEquals(498, RegistryRemapper.mapBlock755To754(512)); // dirt_path → grass_path
        assertEquals(337, RegistryRemapper.mapBlock755To754(818)); // calcite → quartz_block
        assertEquals(282, RegistryRemapper.mapBlock755To754(897)); // 末尾
        assertEquals(1, RegistryRemapper.mapBlock755To754(99999)); // 範囲外は石
        assertEquals(1, RegistryRemapper.mapBlock754To755(1));
        assertEquals(512, RegistryRemapper.mapBlock754To755(498)); // grass_path → dirt_path
        assertEquals(776, RegistryRemapper.mapBlock754To755(762)); // quartz_bricks（末尾。755 では 776）
        assertEquals(1, RegistryRemapper.mapBlock754To755(99999)); // 範囲外は石
        assertEquals(11, RegistryRemapper.mapMenu755To754(11)); // crafting
        assertEquals(0, RegistryRemapper.mapMenu755To754(0)); // generic_9x1
        assertEquals(23, RegistryRemapper.mapMenu755To754(23)); // stonecutter（末尾）
        assertEquals(11, RegistryRemapper.mapMenu755To754(99999)); // 範囲外は作業台
        assertEquals(11, RegistryRemapper.mapMenu754To755(11));
        assertEquals(23, RegistryRemapper.mapMenu754To755(23)); // 末尾
        assertEquals(11, RegistryRemapper.mapMenu754To755(99999)); // 範囲外は作業台
    }

    @Test
    void biomes() {
        // 順向きは 755 側 176 件（id 0〜175）、反転は 754 側 174 件（id 0〜173）。
        // 0〜173 は同じ id・同じ名前。1.17 の新規 2 件と隙間の id は平原（1）へ。
        assertEquals(127, RegistryRemapper.mapBiome755To754(127)); // the_void
        assertEquals(1, RegistryRemapper.mapBiome755To754(1)); // plains
        assertEquals(12, RegistryRemapper.mapBiome755To754(12)); // snowy_tundra
        assertEquals(3, RegistryRemapper.mapBiome755To754(3)); // mountains
        assertEquals(1, RegistryRemapper.mapBiome755To754(174)); // dripstone_caves → 平原
        assertEquals(1, RegistryRemapper.mapBiome755To754(175)); // lush_caves → 平原
        assertEquals(1, RegistryRemapper.mapBiome755To754(51)); // 隙間の id は平原
        assertEquals(1, RegistryRemapper.mapBiome755To754(99999)); // 範囲外は平原
        assertEquals(127, RegistryRemapper.mapBiome754To755(127)); // the_void
        assertEquals(12, RegistryRemapper.mapBiome754To755(12)); // snowy_tundra
        assertEquals(1, RegistryRemapper.mapBiome754To755(51)); // 隙間の id は平原
        assertEquals(1, RegistryRemapper.mapBiome754To755(99999)); // 範囲外は平原
    }

    @Test
    void damageTypesAreIdentity() {
        assertEquals(0, RegistryRemapper.mapDamageType755To754(0));
        assertEquals(16, RegistryRemapper.mapDamageType755To754(16)); // generic
        assertEquals(41, RegistryRemapper.mapDamageType755To754(41));
        assertEquals(0, RegistryRemapper.mapDamageType754To755(0));
        assertEquals(41, RegistryRemapper.mapDamageType754To755(41));
        assertEquals(RegistryRemapper.DROP, RegistryRemapper.mapDamageType755To754(99999));
        assertEquals(RegistryRemapper.DROP, RegistryRemapper.mapDamageType754To755(99999));
    }

    @Test
    void effects() {
        assertEquals(1, RegistryRemapper.mapEffect755To754(1)); // speed
        assertEquals(32, RegistryRemapper.mapEffect755To754(32)); // 末尾
        assertEquals(1, RegistryRemapper.mapEffect755To754(99999)); // 範囲外は Speed
        assertEquals(1, RegistryRemapper.mapEffect754To755(1)); // speed
        assertEquals(32, RegistryRemapper.mapEffect754To755(32)); // 末尾
        assertEquals(1, RegistryRemapper.mapEffect754To755(99999)); // 範囲外は Speed
    }

    @Test
    void blockEntities() {
        // 754 は 33 件（0〜32）、755 は 34 件（0〜33）。33（sculk_sensor）は
        // 1.16.5 には無いため DROP。755 と違い恒等写像ではない。
        assertEquals(0, RegistryRemapper.mapBlockEntityType755To754(0)); // furnace
        assertEquals(32, RegistryRemapper.mapBlockEntityType755To754(32)); // 末尾
        assertEquals(RegistryRemapper.DROP,
                RegistryRemapper.mapBlockEntityType755To754(33)); // sculk_sensor
        assertEquals(RegistryRemapper.DROP,
                RegistryRemapper.mapBlockEntityType755To754(99999));
        assertEquals(0, RegistryRemapper.mapBlockEntityType754To755(0)); // furnace
        assertEquals(32, RegistryRemapper.mapBlockEntityType754To755(32)); // 末尾
        assertEquals(RegistryRemapper.DROP,
                RegistryRemapper.mapBlockEntityType754To755(33)); // 範囲外
        assertEquals(RegistryRemapper.DROP,
                RegistryRemapper.mapBlockEntityType754To755(99999));
    }

    @Test
    void attributes() {
        assertEquals(0, RegistryRemapper.mapAttribute755To754(0));
        assertEquals(12, RegistryRemapper.mapAttribute755To754(12)); // 末尾
        assertEquals(RegistryRemapper.DROP, RegistryRemapper.mapAttribute755To754(99999));
        assertEquals(0, RegistryRemapper.mapAttribute754To755(0));
        assertEquals(12, RegistryRemapper.mapAttribute754To755(12)); // 末尾
        assertEquals(RegistryRemapper.DROP, RegistryRemapper.mapAttribute754To755(99999));
        String name = RegistryRemapper.attributeName754(0);
        assertNotNull(name);
        assertEquals("generic.max_health", name);
        assertEquals(0, RegistryRemapper.attributeId754(name));
        assertEquals(0, RegistryRemapper.attributeId754("minecraft:" + name));
        assertNull(RegistryRemapper.attributeName754(99999));
        assertEquals(RegistryRemapper.DROP,
                RegistryRemapper.attributeId754("minecraft:generic.unknown_attribute"));
        assertEquals(RegistryRemapper.DROP, RegistryRemapper.attributeId754(null));
    }

    @Test
    void enchantments() {
        assertEquals(0, RegistryRemapper.mapEnchantment755To754(0)); // protection
        assertEquals(37, RegistryRemapper.mapEnchantment755To754(37)); // vanishing_curse（末尾）
        assertEquals(RegistryRemapper.DROP, RegistryRemapper.mapEnchantment755To754(99999));
        assertEquals(0, RegistryRemapper.mapEnchantment754To755(0));
        assertEquals(37, RegistryRemapper.mapEnchantment754To755(37)); // 末尾
        assertEquals(RegistryRemapper.DROP, RegistryRemapper.mapEnchantment754To755(99999));
    }

    @Test
    void dispatch() {
        assertEquals(0, RegistryRemapper.mapBlockState(0, 754)); // air
        assertEquals(1, RegistryRemapper.mapBlockState(1, 754)); // stone
        assertEquals(0, RegistryRemapper.mapItem(0, 754)); // air
        assertEquals(1, RegistryRemapper.mapItem(1, 754)); // stone
        assertEquals(1, RegistryRemapper.mapEffect(0, 754)); // speed（765=0、763以降=1。754 段は恒等写像）
        assertEquals("ambient.cave", RegistryRemapper.mapSoundName("ambient.cave", 754));
        // mapItemReverse は正しい順序の合成（754→755 は最も内側）。真値アンカー：
        // tropical_fish_spawn_egg（754=809→755=926）→ 765 id 1067、
        // creeper_head（754=840→755=957）→ 765 id 1100
        // （755=926→1067・755=957→1100 は RegistryRemapper755Test のアンカーと同じ）。
        assertEquals(1067, RegistryRemapper.mapItemReverse(809, 754));
        assertEquals(1100, RegistryRemapper.mapItemReverse(840, 754));
        assertEquals(0, RegistryRemapper.mapItemReverse(0, 754)); // air
        assertEquals(500, RegistryRemapper.mapItemReverse(
                RegistryRemapper.mapItem(500, 754), 754));
    }

    @Test
    void roundTripIsIdentityForInjectiveIds() {
        // 754↔755 は代替で潰れる id があるため、往復は単射の id だけが自分に戻る。
        // powered_rail（補正の代表）：755=1350 → 754=1311 → 755=1350。
        assertEquals(1350, RegistryRemapper.mapBlockState754To755(
                RegistryRemapper.mapBlockState755To754(1350)));
        assertEquals(1311, RegistryRemapper.mapBlockState755To754(
                RegistryRemapper.mapBlockState754To755(1311)));
        // tropical_fish_spawn_egg：755=926 → 754=809 → 755=926。
        assertEquals(926, RegistryRemapper.mapItem754To755(
                RegistryRemapper.mapItem755To754(926)));
        assertEquals(809, RegistryRemapper.mapItem755To754(
                RegistryRemapper.mapItem754To755(809)));
        assertEquals(64, RegistryRemapper.mapEntityType754To755(
                RegistryRemapper.mapEntityType755To754(64))); // pig
        assertEquals(127, RegistryRemapper.mapBiome754To755(
                RegistryRemapper.mapBiome755To754(127))); // the_void
        assertEquals(1005, RegistryRemapper.mapSound754To755(
                RegistryRemapper.mapSound755To754(1005))); // block.stone.break
    }
}
