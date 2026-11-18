package io.detergentmc.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

/**
 * {@link RegistryRemapper} の751（1.16.2）⇔736（1.16.1）向けスポットチェックと往復テスト。
 * 期待値は ViaVersion の mapping-1.16.json を実バニラ 1.16.1 の registries.json／
 * reports で補正したもの（vv-1.16.1.json）と minecraft-data（pc/1.16.1 の
 * blocks.json／items.json／entities.json／biomes.json）から計算したもの（推測ではない）。
 * 生成スクリプト（detergentmc-protocol/tools/gen-736-tables.py）の出力と同じ値。
 * 検証は tools/verify-736-tables.py の実行結果を参照。
 *
 * <p>1.16.1 と 1.16.2 のレジストリ内容は違う（1.16.2 で追加された piglin_brute 系が
 * 1.16.1 には無い。registries-1.16.1.json ≠ registries-1.16.2.json、cmp 確認済み）。
 * 751⇔736 は非恒等：順向き表はブロック状態 12,381 件・アイテム 249 件・
 * エンティティ 47 件・音 405 件が恒等でない。補正はブロック状態 4 件・他は 0 件
 * （maps736-corrections.json）。
 * ブロック状態 17,104 件・ブロック 763 件・アイテム 975 件・エンティティ 107 件・
 * 音 985 件・パーティクル 72 件・メニュー 24 件・属性 13 件・エンチャント 38 件・
 * エフェクト 32 件・ブロックエンティティ 33 件・バイオーム 79 件（id 0〜173、隙間 95 件）。
 * ダメージ種別は 42 件の恒等写像のプレースホルダ。
 * アイテムの順序は 1.16.1 と 1.16.2 で違う（道具・クワ・染料まわりの並べ替え＋
 * piglin_brute_spawn_egg の追加）。エンティティ・音は piglin_brute 系の
 * 挿入だけが違い。ブロック状態は chain／lantern／soul_lantern の
 * プロパティ追加（axis／waterlogged）が違い。
 */
class RegistryRemapper736Test {

    // ---- 751 → 736 ----

    @Test
    void blockStatesForward() {
        assertEquals(0, RegistryRemapper.mapBlockState751To736(0)); // air
        assertEquals(1, RegistryRemapper.mapBlockState751To736(1)); // stone
        assertEquals(1311, RegistryRemapper.mapBlockState751To736(1311)); // powered_rail
        assertEquals(5141, RegistryRemapper.mapBlockState751To736(5145)); // cauldron[level=0]
        assertEquals(9223, RegistryRemapper.mapBlockState751To736(9227)); // grass_path
        assertEquals(17103, RegistryRemapper.mapBlockState751To736(17111)); // 末尾
        assertEquals(1, RegistryRemapper.mapBlockState751To736(99999)); // 範囲外は石
        assertEquals(4729, RegistryRemapper.mapBlockState751To736(4731)); // chain[axis=y,waterlogged=true] → chain[waterlogged=true]
        assertEquals(14886, RegistryRemapper.mapBlockState751To736(14890)); // lantern[hanging=true,waterlogged=true] → lantern[hanging=true]
    }

    @Test
    void blockStatesReverse() {
        assertEquals(0, RegistryRemapper.mapBlockState736To751(0)); // air
        assertEquals(1, RegistryRemapper.mapBlockState736To751(1)); // stone
        assertEquals(1311, RegistryRemapper.mapBlockState736To751(1311)); // powered_rail
        assertEquals(5145, RegistryRemapper.mapBlockState736To751(5141)); // cauldron[level=0]
        assertEquals(9227, RegistryRemapper.mapBlockState736To751(9223)); // grass_path
        assertEquals(17111, RegistryRemapper.mapBlockState736To751(17103)); // 末尾
        assertEquals(1, RegistryRemapper.mapBlockState736To751(99999)); // 範囲外は石
        assertEquals(14891, RegistryRemapper.mapBlockState736To751(14886)); // lantern[hanging=true] → lantern[hanging=true,waterlogged=false]（補正）
        assertEquals(14895, RegistryRemapper.mapBlockState736To751(14888)); // soul_lantern[hanging=true] → soul_lantern[hanging=true,waterlogged=false]（補正）
    }

    @Test
    void items() {
        assertEquals(0, RegistryRemapper.mapItem751To736(0)); // air
        assertEquals(1, RegistryRemapper.mapItem751To736(1)); // stone
        assertEquals(372, RegistryRemapper.mapItem751To736(372)); // grass_path
        assertEquals(790, RegistryRemapper.mapItem751To736(791)); // piglin_brute_spawn_egg → piglin_spawn_egg
        assertEquals(808, RegistryRemapper.mapItem751To736(809)); // tropical_fish_spawn_egg
        assertEquals(839, RegistryRemapper.mapItem751To736(840)); // creeper_head
        assertEquals(974, RegistryRemapper.mapItem751To736(975)); // 末尾
        assertEquals(1, RegistryRemapper.mapItem751To736(99999)); // 範囲外は石
        assertEquals(0, RegistryRemapper.mapItem736To751(0));
        assertEquals(1, RegistryRemapper.mapItem736To751(1));
        assertEquals(372, RegistryRemapper.mapItem736To751(372)); // grass_path
        assertEquals(790, RegistryRemapper.mapItem736To751(790)); // piglin_spawn_egg
        assertEquals(809, RegistryRemapper.mapItem736To751(808)); // tropical_fish_spawn_egg
        assertEquals(840, RegistryRemapper.mapItem736To751(839)); // creeper_head
        assertEquals(975, RegistryRemapper.mapItem736To751(974)); // 末尾
        assertEquals(1, RegistryRemapper.mapItem736To751(99999)); // 範囲外は石
    }

    @Test
    void entities() {
        assertEquals(59, RegistryRemapper.mapEntityType751To736(59)); // pig
        assertEquals(60, RegistryRemapper.mapEntityType751To736(61)); // piglin_brute → piglin
        assertEquals(89, RegistryRemapper.mapEntityType751To736(90)); // tropical_fish
        assertEquals(1, RegistryRemapper.mapEntityType751To736(1)); // armor_stand
        assertEquals(106, RegistryRemapper.mapEntityType751To736(107)); // 末尾
        assertEquals(59, RegistryRemapper.mapEntityType751To736(99999)); // 範囲外はブタ
        assertEquals(59, RegistryRemapper.mapEntityType736To751(59)); // pig
        assertEquals(60, RegistryRemapper.mapEntityType736To751(60)); // piglin
        assertEquals(90, RegistryRemapper.mapEntityType736To751(89)); // tropical_fish
        assertEquals(107, RegistryRemapper.mapEntityType736To751(106)); // 末尾
        assertEquals(59, RegistryRemapper.mapEntityType736To751(99999)); // 範囲外はブタ
    }

    @Test
    void sounds() {
        assertEquals(0, RegistryRemapper.mapSound751To736(0)); // ambient.cave
        assertEquals(586, RegistryRemapper.mapSound751To736(587)); // entity.parrot.imitate.piglin_brute → piglin
        assertEquals(616, RegistryRemapper.mapSound751To736(626)); // entity.piglin_brute.ambient → piglin.ambient
        assertEquals(809, RegistryRemapper.mapSound751To736(816)); // block.stone.break
        assertEquals(984, RegistryRemapper.mapSound751To736(991)); // 末尾
        assertEquals(809, RegistryRemapper.mapSound751To736(99999)); // 範囲外は石の破壊音
        assertEquals(0, RegistryRemapper.mapSound736To751(0)); // ambient.cave
        assertEquals(586, RegistryRemapper.mapSound736To751(586)); // entity.parrot.imitate.piglin（751 の 586 と同名）
        assertEquals(617, RegistryRemapper.mapSound736To751(616)); // entity.piglin.ambient（751 の 617 と同名）
        assertEquals(816, RegistryRemapper.mapSound736To751(809)); // block.stone.break
        assertEquals(991, RegistryRemapper.mapSound736To751(984)); // 末尾
        assertEquals(816, RegistryRemapper.mapSound736To751(99999)); // 範囲外は石の破壊音
    }

    @Test
    void soundNames() {
        // 751→736 は piglin_brute 系 7 件の代替。736→751 に改名は無い。
        assertEquals("entity.parrot.imitate.piglin",
                RegistryRemapper.mapSoundName751To736("entity.parrot.imitate.piglin_brute"));
        assertEquals("entity.piglin.ambient",
                RegistryRemapper.mapSoundName751To736("entity.piglin_brute.ambient"));
        assertEquals("entity.piglin.angry",
                RegistryRemapper.mapSoundName751To736("entity.piglin_brute.angry"));
        assertEquals("entity.piglin.death",
                RegistryRemapper.mapSoundName751To736("entity.piglin_brute.death"));
        assertEquals("entity.piglin.hurt",
                RegistryRemapper.mapSoundName751To736("entity.piglin_brute.hurt"));
        assertEquals("entity.piglin.step",
                RegistryRemapper.mapSoundName751To736("entity.piglin_brute.step"));
        assertEquals("entity.piglin.converted_to_zombified",
                RegistryRemapper.mapSoundName751To736("entity.piglin_brute.converted_to_zombified"));
        assertEquals("block.stone.break",
                RegistryRemapper.mapSoundName751To736("block.stone.break"));
        assertEquals("entity.piglin.ambient",
                RegistryRemapper.mapSoundName736To751("entity.piglin.ambient"));
        assertEquals("ambient.cave",
                RegistryRemapper.mapSoundName736To751("ambient.cave"));
    }

    @Test
    void particles() {
        assertEquals(0, RegistryRemapper.mapParticle751To736(0)); // ambient_entity_effect
        assertEquals(19, RegistryRemapper.mapParticle751To736(19)); // end_rod
        assertEquals(71, RegistryRemapper.mapParticle751To736(71)); // 末尾
        assertEquals(0, RegistryRemapper.mapParticle751To736(99999)); // 範囲外
        assertEquals(0, RegistryRemapper.mapParticle736To751(0)); // ambient_entity_effect
        assertEquals(71, RegistryRemapper.mapParticle736To751(71)); // 末尾
        assertEquals(0, RegistryRemapper.mapParticle736To751(99999)); // 範囲外
    }

    @Test
    void blocksAndMenus() {
        assertEquals(1, RegistryRemapper.mapBlock751To736(1)); // stone
        assertEquals(0, RegistryRemapper.mapBlock751To736(0)); // air
        assertEquals(498, RegistryRemapper.mapBlock751To736(498)); // grass_path
        assertEquals(762, RegistryRemapper.mapBlock751To736(762)); // 末尾
        assertEquals(1, RegistryRemapper.mapBlock751To736(99999)); // 範囲外は石
        assertEquals(1, RegistryRemapper.mapBlock736To751(1));
        assertEquals(762, RegistryRemapper.mapBlock736To751(762)); // 末尾
        assertEquals(1, RegistryRemapper.mapBlock736To751(99999)); // 範囲外は石
        assertEquals(11, RegistryRemapper.mapMenu751To736(11)); // crafting
        assertEquals(0, RegistryRemapper.mapMenu751To736(0)); // generic_9x1
        assertEquals(23, RegistryRemapper.mapMenu751To736(23)); // stonecutter（末尾）
        assertEquals(11, RegistryRemapper.mapMenu751To736(99999)); // 範囲外は作業台
        assertEquals(11, RegistryRemapper.mapMenu736To751(11));
        assertEquals(23, RegistryRemapper.mapMenu736To751(23)); // 末尾
        assertEquals(11, RegistryRemapper.mapMenu736To751(99999)); // 範囲外は作業台
    }

    @Test
    void biomes() {
        // 順向き・反転とも 174 件（id 0〜173）。0〜173 は同じ id・同じ名前。
        // 新規バイオームは無く、隙間の id だけが平原（1）へ。
        assertEquals(127, RegistryRemapper.mapBiome751To736(127)); // the_void
        assertEquals(1, RegistryRemapper.mapBiome751To736(1)); // plains
        assertEquals(12, RegistryRemapper.mapBiome751To736(12)); // snowy_tundra
        assertEquals(3, RegistryRemapper.mapBiome751To736(3)); // mountains
        assertEquals(1, RegistryRemapper.mapBiome751To736(51)); // 隙間の id は平原
        assertEquals(1, RegistryRemapper.mapBiome751To736(99999)); // 範囲外は平原
        assertEquals(127, RegistryRemapper.mapBiome736To751(127)); // the_void
        assertEquals(12, RegistryRemapper.mapBiome736To751(12)); // snowy_tundra
        assertEquals(1, RegistryRemapper.mapBiome736To751(51)); // 隙間の id は平原
        assertEquals(1, RegistryRemapper.mapBiome736To751(99999)); // 範囲外は平原
    }

    @Test
    void damageTypesAreIdentity() {
        assertEquals(0, RegistryRemapper.mapDamageType751To736(0));
        assertEquals(16, RegistryRemapper.mapDamageType751To736(16)); // generic
        assertEquals(41, RegistryRemapper.mapDamageType751To736(41));
        assertEquals(0, RegistryRemapper.mapDamageType736To751(0));
        assertEquals(41, RegistryRemapper.mapDamageType736To751(41));
        assertEquals(RegistryRemapper.DROP, RegistryRemapper.mapDamageType751To736(99999));
        assertEquals(RegistryRemapper.DROP, RegistryRemapper.mapDamageType736To751(99999));
    }

    @Test
    void effects() {
        assertEquals(1, RegistryRemapper.mapEffect751To736(1)); // speed
        assertEquals(32, RegistryRemapper.mapEffect751To736(32)); // 末尾
        assertEquals(1, RegistryRemapper.mapEffect751To736(99999)); // 範囲外は Speed
        assertEquals(1, RegistryRemapper.mapEffect736To751(1)); // speed
        assertEquals(32, RegistryRemapper.mapEffect736To751(32)); // 末尾
        assertEquals(1, RegistryRemapper.mapEffect736To751(99999)); // 範囲外は Speed
    }

    @Test
    void blockEntities() {
        // 751・736 ともに 33 件（0〜32）で同一順序のため恒等写像（DROP は無い）。
        // 33 は範囲外のため DROP。
        assertEquals(0, RegistryRemapper.mapBlockEntityType751To736(0)); // furnace
        assertEquals(32, RegistryRemapper.mapBlockEntityType751To736(32)); // 末尾
        assertEquals(RegistryRemapper.DROP,
                RegistryRemapper.mapBlockEntityType751To736(33)); // 範囲外
        assertEquals(RegistryRemapper.DROP,
                RegistryRemapper.mapBlockEntityType751To736(99999));
        assertEquals(0, RegistryRemapper.mapBlockEntityType736To751(0)); // furnace
        assertEquals(32, RegistryRemapper.mapBlockEntityType736To751(32)); // 末尾
        assertEquals(RegistryRemapper.DROP,
                RegistryRemapper.mapBlockEntityType736To751(33)); // 範囲外
        assertEquals(RegistryRemapper.DROP,
                RegistryRemapper.mapBlockEntityType736To751(99999));
    }

    @Test
    void attributes() {
        assertEquals(0, RegistryRemapper.mapAttribute751To736(0));
        assertEquals(12, RegistryRemapper.mapAttribute751To736(12)); // 末尾
        assertEquals(RegistryRemapper.DROP, RegistryRemapper.mapAttribute751To736(99999));
        assertEquals(0, RegistryRemapper.mapAttribute736To751(0));
        assertEquals(12, RegistryRemapper.mapAttribute736To751(12)); // 末尾
        assertEquals(RegistryRemapper.DROP, RegistryRemapper.mapAttribute736To751(99999));
        String name = RegistryRemapper.attributeName736(0);
        assertNotNull(name);
        assertEquals("generic.max_health", name);
        assertEquals(0, RegistryRemapper.attributeId736(name));
        assertEquals(0, RegistryRemapper.attributeId736("minecraft:" + name));
        assertNull(RegistryRemapper.attributeName736(99999));
        assertEquals(RegistryRemapper.DROP,
                RegistryRemapper.attributeId736("minecraft:generic.unknown_attribute"));
        assertEquals(RegistryRemapper.DROP, RegistryRemapper.attributeId736(null));
    }

    @Test
    void enchantments() {
        assertEquals(0, RegistryRemapper.mapEnchantment751To736(0)); // protection
        assertEquals(37, RegistryRemapper.mapEnchantment751To736(37)); // vanishing_curse（末尾）
        assertEquals(RegistryRemapper.DROP, RegistryRemapper.mapEnchantment751To736(99999));
        assertEquals(0, RegistryRemapper.mapEnchantment736To751(0));
        assertEquals(37, RegistryRemapper.mapEnchantment736To751(37)); // 末尾
        assertEquals(RegistryRemapper.DROP, RegistryRemapper.mapEnchantment736To751(99999));
    }

    @Test
    void dispatch() {
        assertEquals(0, RegistryRemapper.mapBlockState(0, 736)); // air
        assertEquals(1, RegistryRemapper.mapBlockState(1, 736)); // stone
        assertEquals(0, RegistryRemapper.mapItem(0, 736)); // air
        assertEquals(1, RegistryRemapper.mapItem(1, 736)); // stone
        assertEquals(1, RegistryRemapper.mapEffect(0, 736)); // speed（765=0、763以降=1。736 段は恒等写像）
        assertEquals("ambient.cave", RegistryRemapper.mapSoundName("ambient.cave", 736));
        // mapItemReverse は正しい順序の合成（736→751 は最も内側）。真値アンカー：
        // turtle_spawn_egg（736=809→751=810）→ 765 id 1068、
        // dragon_head（736=840→751=841）→ 765 id 1101
        // （751=810→1068・751=841→1101 は Probe736b.java で実測）。
        assertEquals(1068, RegistryRemapper.mapItemReverse(809, 736));
        assertEquals(1101, RegistryRemapper.mapItemReverse(840, 736));
        assertEquals(0, RegistryRemapper.mapItemReverse(0, 736)); // air
        assertEquals(500, RegistryRemapper.mapItemReverse(
                RegistryRemapper.mapItem(500, 736), 736));
    }

    @Test
    void roundTripIsIdentity() {
        // 下向き∘上向きは 736 側の全 id で恒等写像（Check736Remap で全件確認）。
        // ここでは代表値だけ確認する。
        assertEquals(1311, RegistryRemapper.mapBlockState751To736(
                RegistryRemapper.mapBlockState736To751(1311))); // powered_rail
        assertEquals(17103, RegistryRemapper.mapBlockState751To736(
                RegistryRemapper.mapBlockState736To751(17103))); // 末尾
        assertEquals(809, RegistryRemapper.mapItem751To736(
                RegistryRemapper.mapItem736To751(809))); // turtle_spawn_egg
        assertEquals(974, RegistryRemapper.mapItem751To736(
                RegistryRemapper.mapItem736To751(974))); // 末尾
        assertEquals(59, RegistryRemapper.mapEntityType751To736(
                RegistryRemapper.mapEntityType736To751(59))); // pig
        assertEquals(127, RegistryRemapper.mapBiome751To736(
                RegistryRemapper.mapBiome736To751(127))); // the_void
        assertEquals(809, RegistryRemapper.mapSound751To736(
                RegistryRemapper.mapSound736To751(809))); // block.stone.break
        assertEquals(32, RegistryRemapper.mapBlockEntityType751To736(
                RegistryRemapper.mapBlockEntityType736To751(32))); // 末尾
    }
}
