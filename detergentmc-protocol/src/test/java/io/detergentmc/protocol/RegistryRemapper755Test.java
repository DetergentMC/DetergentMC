package io.detergentmc.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

/**
 * {@link RegistryRemapper} の755（1.17）⇔756（1.17.1）向けスポットチェックと往復テスト。
 * 期待値は ViaVersion の mapping-1.17.json と minecraft-data（pc/1.17 の
 * blocks.json／items.json／entities.json／biomes.json）から計算したもの（推測ではない）。
 * 生成スクリプト（detergentmc-protocol/tools/gen-755-tables.py）の出力と同じ値。
 * 検証は tools/verify-755-tables.py の実行結果を参照。
 *
 * <p>1.17と1.17.1のレジストリは同一（registries-1.17.json と registries-1.17.1.json
 * はバイト単位で一致。blocks-1.17-reports.json も同一）ため、7種の表はすべて恒等写像。
 * 補正は全種 0 件。バイオームは 81 件・id 0〜175 の非連番で、順向き・反転とも
 * ワイヤ id 空間（176 件）の恒等写像。隙間の id は平原（1）で埋める。
 * エフェクト 32 件・ブロックエンティティ 34 件・属性 13 件・エンチャント 38 件は
 * 同一順序。ダメージ種別は 42 件の恒等写像のプレースホルダ。
 * Join Game は同一（実バニラ 1.17 と 1.17.1 の生キャプチャで 31,555 バイト、
 * entityId・seed 以外バイト同一）。
 */
class RegistryRemapper755Test {

    // ---- 756 → 755 ----

    @Test
    void blockStatesForward() {
        assertEquals(0, RegistryRemapper.mapBlockState756To755(0)); // air
        assertEquals(1, RegistryRemapper.mapBlockState756To755(1)); // stone
        assertEquals(118, RegistryRemapper.mapBlockState756To755(118)); // birch_wood[axis=x]
        assertEquals(20341, RegistryRemapper.mapBlockState756To755(20341)); // 末尾
        assertEquals(1, RegistryRemapper.mapBlockState756To755(99999)); // 範囲外は石
    }

    @Test
    void blockStatesReverse() {
        assertEquals(0, RegistryRemapper.mapBlockState755To756(0)); // air
        assertEquals(1, RegistryRemapper.mapBlockState755To756(1)); // stone
        assertEquals(118, RegistryRemapper.mapBlockState755To756(118)); // birch_wood[axis=x]
        assertEquals(20341, RegistryRemapper.mapBlockState755To756(20341)); // 末尾
        assertEquals(1, RegistryRemapper.mapBlockState755To756(99999)); // 範囲外は石
    }

    @Test
    void items() {
        assertEquals(0, RegistryRemapper.mapItem756To755(0)); // air
        assertEquals(1, RegistryRemapper.mapItem756To755(1)); // stone
        assertEquals(1099, RegistryRemapper.mapItem756To755(1099)); // pointed_dripstone（末尾）
        assertEquals(1, RegistryRemapper.mapItem756To755(99999)); // 範囲外は石
        assertEquals(0, RegistryRemapper.mapItem755To756(0));
        assertEquals(1, RegistryRemapper.mapItem755To756(1));
        assertEquals(1099, RegistryRemapper.mapItem755To756(1099)); // 末尾
        assertEquals(1, RegistryRemapper.mapItem755To756(99999)); // 範囲外は石
    }

    @Test
    void entities() {
        assertEquals(64, RegistryRemapper.mapEntityType756To755(64)); // pig
        assertEquals(0, RegistryRemapper.mapEntityType756To755(0)); // area_effect_cloud
        assertEquals(112, RegistryRemapper.mapEntityType756To755(112)); // 末尾
        assertEquals(64, RegistryRemapper.mapEntityType756To755(99999)); // 範囲外はブタ
        assertEquals(64, RegistryRemapper.mapEntityType755To756(64)); // pig
        assertEquals(0, RegistryRemapper.mapEntityType755To756(0));
        assertEquals(112, RegistryRemapper.mapEntityType755To756(112)); // 末尾
        assertEquals(64, RegistryRemapper.mapEntityType755To756(99999)); // 範囲外はブタ
    }

    @Test
    void sounds() {
        assertEquals(0, RegistryRemapper.mapSound756To755(0)); // ambient.cave
        assertEquals(1005, RegistryRemapper.mapSound756To755(1005)); // block.stone.break
        assertEquals(1189, RegistryRemapper.mapSound756To755(1189)); // entity.zombie_villager.step（末尾）
        assertEquals(1005, RegistryRemapper.mapSound756To755(99999)); // 範囲外は石の破壊音
        assertEquals(0, RegistryRemapper.mapSound755To756(0)); // ambient.cave
        assertEquals(1005, RegistryRemapper.mapSound755To756(1005)); // block.stone.break
        assertEquals(1189, RegistryRemapper.mapSound755To756(1189)); // 末尾
        assertEquals(1005, RegistryRemapper.mapSound755To756(99999)); // 範囲外は石の破壊音
    }

    @Test
    void soundNames() {
        // 755 と 756 の音の名前は同一（SOUND_NAME_FALLBACKS_755_TO_756 は空）。
        assertEquals("block.stone.break",
                RegistryRemapper.mapSoundName756To755("block.stone.break"));
        assertEquals("ambient.cave",
                RegistryRemapper.mapSoundName756To755("ambient.cave"));
        assertEquals("block.stone.break",
                RegistryRemapper.mapSoundName755To756("block.stone.break"));
        assertEquals("ambient.cave",
                RegistryRemapper.mapSoundName755To756("ambient.cave"));
    }

    @Test
    void particles() {
        assertEquals(0, RegistryRemapper.mapParticle756To755(0)); // ambient_entity_effect
        assertEquals(2, RegistryRemapper.mapParticle756To755(2)); // barrier
        assertEquals(3, RegistryRemapper.mapParticle756To755(3)); // light
        assertEquals(22, RegistryRemapper.mapParticle756To755(22)); // entity_effect
        assertEquals(88, RegistryRemapper.mapParticle756To755(88)); // scrape（末尾）
        assertEquals(0, RegistryRemapper.mapParticle756To755(99999)); // 範囲外
        assertEquals(0, RegistryRemapper.mapParticle755To756(0)); // ambient_entity_effect
        assertEquals(2, RegistryRemapper.mapParticle755To756(2)); // barrier
        assertEquals(3, RegistryRemapper.mapParticle755To756(3)); // light
        assertEquals(88, RegistryRemapper.mapParticle755To756(88)); // 末尾
        assertEquals(0, RegistryRemapper.mapParticle755To756(99999)); // 範囲外
    }

    @Test
    void blocksAndMenus() {
        assertEquals(1, RegistryRemapper.mapBlock756To755(1)); // stone
        assertEquals(0, RegistryRemapper.mapBlock756To755(0)); // air
        assertEquals(897, RegistryRemapper.mapBlock756To755(897)); // potted_flowering_azalea_bush（末尾）
        assertEquals(1, RegistryRemapper.mapBlock756To755(99999)); // 範囲外は石
        assertEquals(1, RegistryRemapper.mapBlock755To756(1));
        assertEquals(897, RegistryRemapper.mapBlock755To756(897)); // 末尾
        assertEquals(1, RegistryRemapper.mapBlock755To756(99999)); // 範囲外は石
        assertEquals(11, RegistryRemapper.mapMenu756To755(11)); // crafting
        assertEquals(0, RegistryRemapper.mapMenu756To755(0)); // generic_9x1
        assertEquals(23, RegistryRemapper.mapMenu756To755(23)); // stonecutter（末尾）
        assertEquals(11, RegistryRemapper.mapMenu756To755(99999)); // 範囲外は作業台
        assertEquals(11, RegistryRemapper.mapMenu755To756(11));
        assertEquals(23, RegistryRemapper.mapMenu755To756(23)); // 末尾
        assertEquals(11, RegistryRemapper.mapMenu755To756(99999)); // 範囲外は作業台
    }

    @Test
    void biomes() {
        // 順向き・反転ともワイヤ id 空間（176 件）の恒等写像。
        // the_void（127）、snowy_tundra（12）、mountains（3）、
        // dripstone_caves（174）、lush_caves（175）、plains（1）。
        assertEquals(127, RegistryRemapper.mapBiome756To755(127)); // the_void
        assertEquals(1, RegistryRemapper.mapBiome756To755(1)); // plains
        assertEquals(12, RegistryRemapper.mapBiome756To755(12)); // snowy_tundra
        assertEquals(3, RegistryRemapper.mapBiome756To755(3)); // mountains
        assertEquals(174, RegistryRemapper.mapBiome756To755(174)); // dripstone_caves
        assertEquals(175, RegistryRemapper.mapBiome756To755(175)); // lush_caves
        assertEquals(1, RegistryRemapper.mapBiome756To755(51)); // 隙間の id は平原
        assertEquals(1, RegistryRemapper.mapBiome756To755(99999)); // 範囲外は平原
        assertEquals(127, RegistryRemapper.mapBiome755To756(127)); // the_void
        assertEquals(12, RegistryRemapper.mapBiome755To756(12)); // snowy_tundra
        assertEquals(174, RegistryRemapper.mapBiome755To756(174)); // dripstone_caves
        assertEquals(175, RegistryRemapper.mapBiome755To756(175)); // lush_caves
        assertEquals(1, RegistryRemapper.mapBiome755To756(51)); // 隙間の id は平原
        assertEquals(1, RegistryRemapper.mapBiome755To756(99999)); // 範囲外は平原
    }

    @Test
    void damageTypesAreIdentity() {
        assertEquals(0, RegistryRemapper.mapDamageType756To755(0));
        assertEquals(16, RegistryRemapper.mapDamageType756To755(16)); // generic
        assertEquals(41, RegistryRemapper.mapDamageType756To755(41));
        assertEquals(0, RegistryRemapper.mapDamageType755To756(0));
        assertEquals(41, RegistryRemapper.mapDamageType755To756(41));
        assertEquals(RegistryRemapper.DROP, RegistryRemapper.mapDamageType756To755(99999));
        assertEquals(RegistryRemapper.DROP, RegistryRemapper.mapDamageType755To756(99999));
    }

    @Test
    void effects() {
        assertEquals(1, RegistryRemapper.mapEffect756To755(1)); // speed
        assertEquals(32, RegistryRemapper.mapEffect756To755(32)); // 末尾
        assertEquals(1, RegistryRemapper.mapEffect756To755(99999)); // 範囲外は Speed
        assertEquals(1, RegistryRemapper.mapEffect755To756(1)); // speed
        assertEquals(32, RegistryRemapper.mapEffect755To756(32)); // 末尾
        assertEquals(1, RegistryRemapper.mapEffect755To756(99999)); // 範囲外は Speed
    }

    @Test
    void blockEntities() {
        assertEquals(0, RegistryRemapper.mapBlockEntityType756To755(0)); // furnace
        assertEquals(33, RegistryRemapper.mapBlockEntityType756To755(33)); // 末尾
        assertEquals(RegistryRemapper.DROP,
                RegistryRemapper.mapBlockEntityType756To755(99999));
        assertEquals(0, RegistryRemapper.mapBlockEntityType755To756(0)); // furnace
        assertEquals(33, RegistryRemapper.mapBlockEntityType755To756(33)); // 末尾
        assertEquals(RegistryRemapper.DROP,
                RegistryRemapper.mapBlockEntityType755To756(99999));
    }

    @Test
    void attributes() {
        assertEquals(0, RegistryRemapper.mapAttribute756To755(0));
        assertEquals(12, RegistryRemapper.mapAttribute756To755(12)); // 末尾
        assertEquals(RegistryRemapper.DROP, RegistryRemapper.mapAttribute756To755(99999));
        assertEquals(0, RegistryRemapper.mapAttribute755To756(0));
        assertEquals(12, RegistryRemapper.mapAttribute755To756(12)); // 末尾
        assertEquals(RegistryRemapper.DROP, RegistryRemapper.mapAttribute755To756(99999));
        String name = RegistryRemapper.attributeName755(0);
        assertNotNull(name);
        assertEquals("generic.max_health", name);
        assertEquals(0, RegistryRemapper.attributeId755(name));
        assertEquals(0, RegistryRemapper.attributeId755("minecraft:" + name));
        assertNull(RegistryRemapper.attributeName755(99999));
        assertEquals(RegistryRemapper.DROP,
                RegistryRemapper.attributeId755("minecraft:generic.unknown_attribute"));
        assertEquals(RegistryRemapper.DROP, RegistryRemapper.attributeId755(null));
    }

    @Test
    void enchantments() {
        assertEquals(0, RegistryRemapper.mapEnchantment756To755(0));
        assertEquals(37, RegistryRemapper.mapEnchantment756To755(37)); // vanishing_curse（末尾）
        assertEquals(RegistryRemapper.DROP, RegistryRemapper.mapEnchantment756To755(99999));
        assertEquals(0, RegistryRemapper.mapEnchantment755To756(0));
        assertEquals(37, RegistryRemapper.mapEnchantment755To756(37)); // 末尾
        assertEquals(RegistryRemapper.DROP, RegistryRemapper.mapEnchantment755To756(99999));
    }

    @Test
    void dispatch() {
        assertEquals(0, RegistryRemapper.mapBlockState(0, 755)); // air
        assertEquals(1, RegistryRemapper.mapBlockState(1, 755)); // stone
        assertEquals(0, RegistryRemapper.mapItem(0, 755)); // air
        assertEquals(1, RegistryRemapper.mapItem(1, 755)); // stone
        assertEquals(1, RegistryRemapper.mapEffect(0, 755)); // speed（765=0、763以降=1。755 段は恒等写像）
        assertEquals("ambient.cave", RegistryRemapper.mapSoundName("ambient.cave", 755));
        // mapItemReverse は正しい順序の合成（755→756 は最も内側）。真値アンカー：
        // tropical_fish_spawn_egg（755=926）→ 765 id 1067、
        // creeper_head（755=957）→ 765 id 1100（756 と同じ値。755↔756 は恒等写像のため）。
        assertEquals(1067, RegistryRemapper.mapItemReverse(926, 755));
        assertEquals(1100, RegistryRemapper.mapItemReverse(957, 755));
        assertEquals(0, RegistryRemapper.mapItemReverse(0, 755)); // air
        assertEquals(500, RegistryRemapper.mapItemReverse(
                RegistryRemapper.mapItem(500, 755), 755));
    }

    @Test
    void roundTripIsIdentityBothWays() {
        // 755↔756 は恒等写像のため、往復はすべて自分に戻る。
        assertEquals(118, RegistryRemapper.mapBlockState755To756(
                RegistryRemapper.mapBlockState756To755(118)));
        assertEquals(118, RegistryRemapper.mapBlockState756To755(
                RegistryRemapper.mapBlockState755To756(118)));
        assertEquals(937, RegistryRemapper.mapItem755To756(
                RegistryRemapper.mapItem756To755(937)));
        assertEquals(937, RegistryRemapper.mapItem756To755(
                RegistryRemapper.mapItem755To756(937)));
        assertEquals(67, RegistryRemapper.mapEntityType755To756(
                RegistryRemapper.mapEntityType756To755(67)));
        assertEquals(127, RegistryRemapper.mapBiome755To756(
                RegistryRemapper.mapBiome756To755(127)));
        assertEquals(239, RegistryRemapper.mapSound755To756(
                RegistryRemapper.mapSound756To755(239)));
    }
}
