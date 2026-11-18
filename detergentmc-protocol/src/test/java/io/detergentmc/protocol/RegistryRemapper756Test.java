package io.detergentmc.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

/**
 * {@link RegistryRemapper} の756（1.17.1）⇔757（1.18／1.18.1）向けスポットチェックと往復テスト。
 * 期待値は ViaVersion の mapping-1.17／1.18.json と minecraft-data（pc/1.17・
 * pc/1.18 の blocks.json／items.json／entities.json／biomes.json）から
 * 計算したもの（推測ではない）。生成スクリプト
 * （detergentmc-protocol/tools/gen-756-tables.py）の出力と同じ値。
 * 検証は tools/verify-756-tables.py の実行結果を参照。
 *
 * <p>757と756のレジストリの違いは小さい：ブロック状態 20,342・ブロック 898・
 * エンティティ 113 は同一順序、アイテムは 757 の music_disc_otherside（1027）
 * が 756 に無く music_disc_13 へ代替、音は 13 件が代替、パーティクルは
 * 1.18 の block_marker（3）が 1.17 の barrier（2）と light（3）に分かれる、
 * バイオームは 757 の 61 件（0〜60）に対し 756 は 81 件（id 0〜175 の非連番）。
 * エフェクト 32 件・ブロックエンティティ 34 件・属性 13 件・エンチャント 38 件は
 * 同一順序。ダメージ種別は 42 件の恒等写像のプレースホルダ。
 */
class RegistryRemapper756Test {

    // ---- 757 → 756 ----

    @Test
    void blockStatesForward() {
        assertEquals(0, RegistryRemapper.mapBlockState757To756(0)); // air
        assertEquals(1, RegistryRemapper.mapBlockState757To756(1)); // stone
        assertEquals(118, RegistryRemapper.mapBlockState757To756(118)); // oak_log
        assertEquals(20341, RegistryRemapper.mapBlockState757To756(20341)); // 末尾
        assertEquals(1, RegistryRemapper.mapBlockState757To756(99999)); // 範囲外は石
    }

    @Test
    void blockStatesReverse() {
        assertEquals(0, RegistryRemapper.mapBlockState756To757(0)); // air
        assertEquals(1, RegistryRemapper.mapBlockState756To757(1)); // stone
        assertEquals(118, RegistryRemapper.mapBlockState756To757(118)); // oak_log
        assertEquals(20341, RegistryRemapper.mapBlockState756To757(20341)); // 末尾
        assertEquals(1, RegistryRemapper.mapBlockState756To757(99999)); // 範囲外は石
    }

    @Test
    void items() {
        assertEquals(0, RegistryRemapper.mapItem757To756(0)); // air
        assertEquals(1, RegistryRemapper.mapItem757To756(1)); // stone
        // music_disc_otherside（757=1027）は 756 に無いため music_disc_13 へ代替。
        assertEquals(1015, RegistryRemapper.mapItem757To756(1027));
        // otherside の挿入で 1 ずれる：pigstep は 757=1028 → 756=1027。
        assertEquals(1027, RegistryRemapper.mapItem757To756(1028));
        assertEquals(1099, RegistryRemapper.mapItem757To756(1100)); // 末尾
        assertEquals(1, RegistryRemapper.mapItem757To756(99999)); // 範囲外は石
        assertEquals(0, RegistryRemapper.mapItem756To757(0));
        assertEquals(1, RegistryRemapper.mapItem756To757(1));
        assertEquals(1015, RegistryRemapper.mapItem756To757(1015)); // music_disc_13
        assertEquals(1028, RegistryRemapper.mapItem756To757(1027)); // pigstep
        assertEquals(1100, RegistryRemapper.mapItem756To757(1099)); // 末尾
        assertEquals(1, RegistryRemapper.mapItem756To757(99999)); // 範囲外は石
    }

    @Test
    void entities() {
        assertEquals(64, RegistryRemapper.mapEntityType757To756(64)); // pig
        assertEquals(0, RegistryRemapper.mapEntityType757To756(0)); // area_effect_cloud
        assertEquals(112, RegistryRemapper.mapEntityType757To756(112)); // 末尾
        assertEquals(64, RegistryRemapper.mapEntityType757To756(99999)); // 範囲外はブタ
        assertEquals(64, RegistryRemapper.mapEntityType756To757(64)); // pig
        assertEquals(0, RegistryRemapper.mapEntityType756To757(0));
        assertEquals(112, RegistryRemapper.mapEntityType756To757(112)); // 末尾
        assertEquals(64, RegistryRemapper.mapEntityType756To757(99999)); // 範囲外はブタ
    }

    @Test
    void sounds() {
        assertEquals(617, RegistryRemapper.mapSound757To756(633)); // otherside → disc.13
        assertEquals(631, RegistryRemapper.mapSound757To756(644)); // overworld.meadow → game
        assertEquals(236, RegistryRemapper.mapSound757To756(462)); // growing_plant.crop → crop.break
        assertEquals(236, RegistryRemapper.mapSound757To756(239)); // crop.break
        assertEquals(0, RegistryRemapper.mapSound757To756(0)); // ambient.cave
        assertEquals(1005, RegistryRemapper.mapSound757To756(99999)); // 範囲外は石の破壊音
        assertEquals(621, RegistryRemapper.mapSound756To757(617)); // disc.13
        assertEquals(636, RegistryRemapper.mapSound756To757(631)); // game
        assertEquals(239, RegistryRemapper.mapSound756To757(236)); // crop.break
        assertEquals(0, RegistryRemapper.mapSound756To757(0)); // ambient.cave
        assertEquals(1018, RegistryRemapper.mapSound756To757(99999)); // 範囲外は石の破壊音
    }

    @Test
    void soundNames() {
        assertEquals("music_disc.13",
                RegistryRemapper.mapSoundName757To756("music_disc.otherside"));
        assertEquals("music.game",
                RegistryRemapper.mapSoundName757To756("music.overworld.meadow"));
        assertEquals("block.crop.break",
                RegistryRemapper.mapSoundName757To756("block.growing_plant.crop"));
        assertEquals("ambient.cave",
                RegistryRemapper.mapSoundName757To756("ambient.cave"));
        assertEquals("music_disc.13",
                RegistryRemapper.mapSoundName756To757("music_disc.13"));
        assertEquals("ambient.cave",
                RegistryRemapper.mapSoundName756To757("ambient.cave"));
    }

    @Test
    void particles() {
        // 1.18 の block_marker（3）は 1.17 の barrier（2）へ。
        assertEquals(2, RegistryRemapper.mapParticle757To756(3));
        assertEquals(0, RegistryRemapper.mapParticle757To756(0)); // ambient_entity_effect
        assertEquals(0, RegistryRemapper.mapParticle757To756(99999)); // 範囲外
        // 反転は barrier（2）・light（3）とも block_marker（3）へ。
        assertEquals(3, RegistryRemapper.mapParticle756To757(2));
        assertEquals(3, RegistryRemapper.mapParticle756To757(3));
        assertEquals(0, RegistryRemapper.mapParticle756To757(99999)); // 範囲外
    }

    @Test
    void blocksAndMenus() {
        assertEquals(1, RegistryRemapper.mapBlock757To756(1)); // stone
        assertEquals(0, RegistryRemapper.mapBlock757To756(0)); // air
        assertEquals(897, RegistryRemapper.mapBlock757To756(897)); // 末尾
        assertEquals(1, RegistryRemapper.mapBlock757To756(99999)); // 範囲外は石
        assertEquals(1, RegistryRemapper.mapBlock756To757(1));
        assertEquals(897, RegistryRemapper.mapBlock756To757(897)); // 末尾
        assertEquals(1, RegistryRemapper.mapBlock756To757(99999)); // 範囲外は石
        assertEquals(11, RegistryRemapper.mapMenu757To756(11)); // crafting
        assertEquals(0, RegistryRemapper.mapMenu757To756(0)); // generic_9x1
        assertEquals(23, RegistryRemapper.mapMenu757To756(23)); // 末尾
        assertEquals(11, RegistryRemapper.mapMenu757To756(99999)); // 範囲外は作業台
        assertEquals(11, RegistryRemapper.mapMenu756To757(11));
        assertEquals(23, RegistryRemapper.mapMenu756To757(23)); // 末尾
        assertEquals(11, RegistryRemapper.mapMenu756To757(99999)); // 範囲外は作業台
    }

    @Test
    void biomes() {
        // 順向き：the_void（0→127）、plains（1→1）、snowy_plains→snowy_tundra（3→12）、
        // meadow（28→plains 1）、dripstone_caves（49→174）。
        assertEquals(127, RegistryRemapper.mapBiome757To756(0));
        assertEquals(1, RegistryRemapper.mapBiome757To756(1)); // plains
        assertEquals(12, RegistryRemapper.mapBiome757To756(3));
        assertEquals(1, RegistryRemapper.mapBiome757To756(28));
        assertEquals(174, RegistryRemapper.mapBiome757To756(49));
        assertEquals(1, RegistryRemapper.mapBiome757To756(99999)); // 範囲外は平原
        // 反転：the_void（127→0）、snowy_tundra（12→snowy_plains 3）、
        // mountains（3→windswept_hills 18）、隙間の id 51→平原、lush_caves（175→50）。
        assertEquals(0, RegistryRemapper.mapBiome756To757(127));
        assertEquals(3, RegistryRemapper.mapBiome756To757(12));
        assertEquals(18, RegistryRemapper.mapBiome756To757(3));
        assertEquals(1, RegistryRemapper.mapBiome756To757(51));
        assertEquals(50, RegistryRemapper.mapBiome756To757(175));
        assertEquals(1, RegistryRemapper.mapBiome756To757(99999)); // 範囲外は平原
    }

    @Test
    void damageTypesAreIdentity() {
        assertEquals(0, RegistryRemapper.mapDamageType757To756(0));
        assertEquals(16, RegistryRemapper.mapDamageType757To756(16)); // generic
        assertEquals(41, RegistryRemapper.mapDamageType757To756(41));
        assertEquals(0, RegistryRemapper.mapDamageType756To757(0));
        assertEquals(41, RegistryRemapper.mapDamageType756To757(41));
        assertEquals(RegistryRemapper.DROP, RegistryRemapper.mapDamageType757To756(99999));
        assertEquals(RegistryRemapper.DROP, RegistryRemapper.mapDamageType756To757(99999));
    }

    @Test
    void effects() {
        assertEquals(1, RegistryRemapper.mapEffect757To756(1)); // speed
        assertEquals(32, RegistryRemapper.mapEffect757To756(32)); // 末尾
        assertEquals(1, RegistryRemapper.mapEffect757To756(99999)); // 範囲外は Speed
        assertEquals(1, RegistryRemapper.mapEffect756To757(1)); // speed
        assertEquals(32, RegistryRemapper.mapEffect756To757(32)); // 末尾
        assertEquals(1, RegistryRemapper.mapEffect756To757(99999)); // 範囲外は Speed
    }

    @Test
    void blockEntities() {
        assertEquals(0, RegistryRemapper.mapBlockEntityType757To756(0)); // furnace
        assertEquals(33, RegistryRemapper.mapBlockEntityType757To756(33)); // 末尾
        assertEquals(RegistryRemapper.DROP,
                RegistryRemapper.mapBlockEntityType757To756(99999));
        assertEquals(0, RegistryRemapper.mapBlockEntityType756To757(0)); // furnace
        assertEquals(33, RegistryRemapper.mapBlockEntityType756To757(33)); // 末尾
        assertEquals(RegistryRemapper.DROP,
                RegistryRemapper.mapBlockEntityType756To757(99999));
    }

    @Test
    void attributes() {
        assertEquals(0, RegistryRemapper.mapAttribute757To756(0));
        assertEquals(12, RegistryRemapper.mapAttribute757To756(12)); // 末尾
        assertEquals(RegistryRemapper.DROP, RegistryRemapper.mapAttribute757To756(99999));
        assertEquals(0, RegistryRemapper.mapAttribute756To757(0));
        assertEquals(12, RegistryRemapper.mapAttribute756To757(12)); // 末尾
        assertEquals(RegistryRemapper.DROP, RegistryRemapper.mapAttribute756To757(99999));
        String name = RegistryRemapper.attributeName756(0);
        assertNotNull(name);
        assertEquals(0, RegistryRemapper.attributeId756(name));
        assertEquals(0, RegistryRemapper.attributeId756("minecraft:" + name));
        assertNull(RegistryRemapper.attributeName756(99999));
        assertEquals(RegistryRemapper.DROP,
                RegistryRemapper.attributeId756("minecraft:generic.unknown_attribute"));
        assertEquals(RegistryRemapper.DROP, RegistryRemapper.attributeId756(null));
    }

    @Test
    void enchantments() {
        assertEquals(0, RegistryRemapper.mapEnchantment757To756(0));
        assertEquals(37, RegistryRemapper.mapEnchantment757To756(37)); // 末尾
        assertEquals(RegistryRemapper.DROP, RegistryRemapper.mapEnchantment757To756(99999));
        assertEquals(0, RegistryRemapper.mapEnchantment756To757(0));
        assertEquals(37, RegistryRemapper.mapEnchantment756To757(37)); // 末尾
        assertEquals(RegistryRemapper.DROP, RegistryRemapper.mapEnchantment756To757(99999));
    }

    @Test
    void dispatch() {
        assertEquals(0, RegistryRemapper.mapBlockState(0, 756)); // air
        assertEquals(1, RegistryRemapper.mapBlockState(1, 756)); // stone
        assertEquals(0, RegistryRemapper.mapItem(0, 756)); // air
        assertEquals(1, RegistryRemapper.mapItem(1, 756)); // stone
        assertEquals(1, RegistryRemapper.mapEffect(0, 756)); // speed（765=0、763以降=1）
        assertEquals("ambient.cave", RegistryRemapper.mapSoundName("ambient.cave", 756));
        // mapItemReverse は正しい順序の合成（§12.5）。真値アンカー：
        // tropical_fish_spawn_egg（756=926）→ 765 id 1067、
        // creeper_head（756=957）→ 765 id 1100。
        assertEquals(1067, RegistryRemapper.mapItemReverse(926, 756));
        assertEquals(1100, RegistryRemapper.mapItemReverse(957, 756));
        assertEquals(0, RegistryRemapper.mapItemReverse(0, 756)); // air
        assertEquals(500, RegistryRemapper.mapItemReverse(
                RegistryRemapper.mapItem(500, 756), 756));
    }

    @Test
    void roundTripIsIdentityBothWays() {
        // otherside（1027）以外は往復で自分に戻る。
        assertEquals(118, RegistryRemapper.mapBlockState756To757(
                RegistryRemapper.mapBlockState757To756(118)));
        assertEquals(118, RegistryRemapper.mapBlockState757To756(
                RegistryRemapper.mapBlockState756To757(118)));
        assertEquals(937, RegistryRemapper.mapItem756To757(
                RegistryRemapper.mapItem757To756(937)));
        assertEquals(937, RegistryRemapper.mapItem757To756(
                RegistryRemapper.mapItem756To757(937)));
        assertEquals(67, RegistryRemapper.mapEntityType756To757(
                RegistryRemapper.mapEntityType757To756(67)));
        assertEquals(1, RegistryRemapper.mapBiome756To757(
                RegistryRemapper.mapBiome757To756(1)));
        assertEquals(239, RegistryRemapper.mapSound756To757(
                RegistryRemapper.mapSound757To756(239)));
        // otherside は 756 に無いため music_disc_13（1015）へ落ち、戻らない。
        assertEquals(1015, RegistryRemapper.mapItem757To756(1027));
        assertEquals(1015, RegistryRemapper.mapItem756To757(1015));
    }
}
