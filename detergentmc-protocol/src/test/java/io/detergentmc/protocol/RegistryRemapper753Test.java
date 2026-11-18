package io.detergentmc.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

/**
 * {@link RegistryRemapper} の754（1.16.4／1.16.5）⇔753（1.16.3）向けスポットチェックと往復テスト。
 * 期待値は ViaVersion の mapping-1.16.json を実バニラ 1.16.3 の registries.json／
 * reports で補正したもの（vv-1.16.3.json）と minecraft-data（pc/1.16.2 の
 * blocks.json／items.json／entities.json／biomes.json）から計算したもの（推測ではない）。
 * 生成スクリプト（detergentmc-protocol/tools/gen-753-tables.py）の出力と同じ値。
 * 検証は tools/verify-753-tables.py の実行結果を参照。
 *
 * <p>1.16.3 と 1.16.5 のレジストリ内容（名前＋protocol_id）は同一（registries-1.16.3.json と
 * blocks-1.16.3-reports.json は 1.16.5 版とバイト単位で完全一致。cmp 確認済み）。
 * そのため 754⇔753 は 7 種すべて恒等写像で、補正は全種 0 件（maps753-corrections.json）。
 * ブロック状態 17,112 件・ブロック 763 件・アイテム 976 件・エンティティ 108 件・
 * 音 992 件・パーティクル 72 件・メニュー 24 件・属性 13 件・エンチャント 38 件・
 * エフェクト 32 件・ブロックエンティティ 33 件・バイオーム 79 件（id 0〜173、隙間 95 件）。
 * ダメージ種別は 42 件の恒等写像のプレースホルダ。
 * Join Game はフィールド構成が同一（実バニラ 1.16.3 の生キャプチャで 30,847 バイト。
 * dimensionCodec は dimension_type 4 件・worldgen/biome 79 件。1.16.5 と同一）。
 * パケットIDは 754 と 753 で同じ（変更 0 件・新規 0 件・削除 0 件。packet-ids-753.json）。
 */
class RegistryRemapper753Test {

    // ---- 754 → 753 ----

    @Test
    void blockStatesForward() {
        assertEquals(0, RegistryRemapper.mapBlockState754To753(0)); // air
        assertEquals(1, RegistryRemapper.mapBlockState754To753(1)); // stone
        assertEquals(1311, RegistryRemapper.mapBlockState754To753(1311)); // powered_rail
        assertEquals(5145, RegistryRemapper.mapBlockState754To753(5145)); // cauldron[level=0]
        assertEquals(9227, RegistryRemapper.mapBlockState754To753(9227)); // grass_path
        assertEquals(17111, RegistryRemapper.mapBlockState754To753(17111)); // 末尾
        assertEquals(1, RegistryRemapper.mapBlockState754To753(99999)); // 範囲外は石
    }

    @Test
    void blockStatesReverse() {
        assertEquals(0, RegistryRemapper.mapBlockState753To754(0)); // air
        assertEquals(1, RegistryRemapper.mapBlockState753To754(1)); // stone
        assertEquals(1311, RegistryRemapper.mapBlockState753To754(1311)); // powered_rail
        assertEquals(5145, RegistryRemapper.mapBlockState753To754(5145)); // cauldron[level=0]
        assertEquals(9227, RegistryRemapper.mapBlockState753To754(9227)); // grass_path
        assertEquals(17111, RegistryRemapper.mapBlockState753To754(17111)); // 末尾
        assertEquals(1, RegistryRemapper.mapBlockState753To754(99999)); // 範囲外は石
    }

    @Test
    void items() {
        assertEquals(0, RegistryRemapper.mapItem754To753(0)); // air
        assertEquals(1, RegistryRemapper.mapItem754To753(1)); // stone
        assertEquals(372, RegistryRemapper.mapItem754To753(372)); // grass_path
        assertEquals(809, RegistryRemapper.mapItem754To753(809)); // tropical_fish_spawn_egg
        assertEquals(840, RegistryRemapper.mapItem754To753(840)); // creeper_head
        assertEquals(975, RegistryRemapper.mapItem754To753(975)); // 末尾
        assertEquals(1, RegistryRemapper.mapItem754To753(99999)); // 範囲外は石
        assertEquals(0, RegistryRemapper.mapItem753To754(0));
        assertEquals(1, RegistryRemapper.mapItem753To754(1));
        assertEquals(372, RegistryRemapper.mapItem753To754(372)); // grass_path
        assertEquals(809, RegistryRemapper.mapItem753To754(809)); // tropical_fish_spawn_egg
        assertEquals(840, RegistryRemapper.mapItem753To754(840)); // creeper_head
        assertEquals(975, RegistryRemapper.mapItem753To754(975)); // 末尾
        assertEquals(1, RegistryRemapper.mapItem753To754(99999)); // 範囲外は石
    }

    @Test
    void entities() {
        assertEquals(59, RegistryRemapper.mapEntityType754To753(59)); // pig
        assertEquals(90, RegistryRemapper.mapEntityType754To753(90)); // tropical_fish
        assertEquals(1, RegistryRemapper.mapEntityType754To753(1)); // armor_stand
        assertEquals(107, RegistryRemapper.mapEntityType754To753(107)); // 末尾
        assertEquals(59, RegistryRemapper.mapEntityType754To753(99999)); // 範囲外はブタ
        assertEquals(59, RegistryRemapper.mapEntityType753To754(59)); // pig
        assertEquals(107, RegistryRemapper.mapEntityType753To754(107)); // 末尾
        assertEquals(59, RegistryRemapper.mapEntityType753To754(99999)); // 範囲外はブタ
    }

    @Test
    void sounds() {
        assertEquals(0, RegistryRemapper.mapSound754To753(0)); // ambient.cave
        assertEquals(816, RegistryRemapper.mapSound754To753(816)); // block.stone.break
        assertEquals(991, RegistryRemapper.mapSound754To753(991)); // 末尾
        assertEquals(816, RegistryRemapper.mapSound754To753(99999)); // 範囲外は石の破壊音
        assertEquals(0, RegistryRemapper.mapSound753To754(0)); // ambient.cave
        assertEquals(816, RegistryRemapper.mapSound753To754(816)); // block.stone.break
        assertEquals(991, RegistryRemapper.mapSound753To754(991)); // 末尾
        assertEquals(816, RegistryRemapper.mapSound753To754(99999)); // 範囲外は石の破壊音
    }

    @Test
    void soundNames() {
        // 754⇔753 に音の改名は無い（SOUND_NAME_FALLBACKS は空）。恒等写像。
        assertEquals("block.stone.break",
                RegistryRemapper.mapSoundName754To753("block.stone.break"));
        assertEquals("ambient.cave",
                RegistryRemapper.mapSoundName754To753("ambient.cave"));
        assertEquals("block.stone.break",
                RegistryRemapper.mapSoundName753To754("block.stone.break"));
        assertEquals("ambient.cave",
                RegistryRemapper.mapSoundName753To754("ambient.cave"));
    }

    @Test
    void particles() {
        assertEquals(0, RegistryRemapper.mapParticle754To753(0)); // ambient_entity_effect
        assertEquals(19, RegistryRemapper.mapParticle754To753(19)); // end_rod
        assertEquals(71, RegistryRemapper.mapParticle754To753(71)); // 末尾
        assertEquals(0, RegistryRemapper.mapParticle754To753(99999)); // 範囲外
        assertEquals(0, RegistryRemapper.mapParticle753To754(0)); // ambient_entity_effect
        assertEquals(71, RegistryRemapper.mapParticle753To754(71)); // 末尾
        assertEquals(0, RegistryRemapper.mapParticle753To754(99999)); // 範囲外
    }

    @Test
    void blocksAndMenus() {
        assertEquals(1, RegistryRemapper.mapBlock754To753(1)); // stone
        assertEquals(0, RegistryRemapper.mapBlock754To753(0)); // air
        assertEquals(498, RegistryRemapper.mapBlock754To753(498)); // grass_path
        assertEquals(762, RegistryRemapper.mapBlock754To753(762)); // 末尾
        assertEquals(1, RegistryRemapper.mapBlock754To753(99999)); // 範囲外は石
        assertEquals(1, RegistryRemapper.mapBlock753To754(1));
        assertEquals(762, RegistryRemapper.mapBlock753To754(762)); // 末尾
        assertEquals(1, RegistryRemapper.mapBlock753To754(99999)); // 範囲外は石
        assertEquals(11, RegistryRemapper.mapMenu754To753(11)); // crafting
        assertEquals(0, RegistryRemapper.mapMenu754To753(0)); // generic_9x1
        assertEquals(23, RegistryRemapper.mapMenu754To753(23)); // stonecutter（末尾）
        assertEquals(11, RegistryRemapper.mapMenu754To753(99999)); // 範囲外は作業台
        assertEquals(11, RegistryRemapper.mapMenu753To754(11));
        assertEquals(23, RegistryRemapper.mapMenu753To754(23)); // 末尾
        assertEquals(11, RegistryRemapper.mapMenu753To754(99999)); // 範囲外は作業台
    }

    @Test
    void biomes() {
        // 順向き・反転とも 174 件（id 0〜173）。0〜173 は同じ id・同じ名前。
        // 新規バイオームは無く、隙間の id だけが平原（1）へ。
        assertEquals(127, RegistryRemapper.mapBiome754To753(127)); // the_void
        assertEquals(1, RegistryRemapper.mapBiome754To753(1)); // plains
        assertEquals(12, RegistryRemapper.mapBiome754To753(12)); // snowy_tundra
        assertEquals(3, RegistryRemapper.mapBiome754To753(3)); // mountains
        assertEquals(1, RegistryRemapper.mapBiome754To753(51)); // 隙間の id は平原
        assertEquals(1, RegistryRemapper.mapBiome754To753(99999)); // 範囲外は平原
        assertEquals(127, RegistryRemapper.mapBiome753To754(127)); // the_void
        assertEquals(12, RegistryRemapper.mapBiome753To754(12)); // snowy_tundra
        assertEquals(1, RegistryRemapper.mapBiome753To754(51)); // 隙間の id は平原
        assertEquals(1, RegistryRemapper.mapBiome753To754(99999)); // 範囲外は平原
    }

    @Test
    void damageTypesAreIdentity() {
        assertEquals(0, RegistryRemapper.mapDamageType754To753(0));
        assertEquals(16, RegistryRemapper.mapDamageType754To753(16)); // generic
        assertEquals(41, RegistryRemapper.mapDamageType754To753(41));
        assertEquals(0, RegistryRemapper.mapDamageType753To754(0));
        assertEquals(41, RegistryRemapper.mapDamageType753To754(41));
        assertEquals(RegistryRemapper.DROP, RegistryRemapper.mapDamageType754To753(99999));
        assertEquals(RegistryRemapper.DROP, RegistryRemapper.mapDamageType753To754(99999));
    }

    @Test
    void effects() {
        assertEquals(1, RegistryRemapper.mapEffect754To753(1)); // speed
        assertEquals(32, RegistryRemapper.mapEffect754To753(32)); // 末尾
        assertEquals(1, RegistryRemapper.mapEffect754To753(99999)); // 範囲外は Speed
        assertEquals(1, RegistryRemapper.mapEffect753To754(1)); // speed
        assertEquals(32, RegistryRemapper.mapEffect753To754(32)); // 末尾
        assertEquals(1, RegistryRemapper.mapEffect753To754(99999)); // 範囲外は Speed
    }

    @Test
    void blockEntities() {
        // 754・753 ともに 33 件（0〜32）で同一順序のため恒等写像（DROP は無い）。
        // 33 は範囲外のため DROP（754 と違い、片方だけの種別は無い）。
        assertEquals(0, RegistryRemapper.mapBlockEntityType754To753(0)); // furnace
        assertEquals(32, RegistryRemapper.mapBlockEntityType754To753(32)); // 末尾
        assertEquals(RegistryRemapper.DROP,
                RegistryRemapper.mapBlockEntityType754To753(33)); // 範囲外
        assertEquals(RegistryRemapper.DROP,
                RegistryRemapper.mapBlockEntityType754To753(99999));
        assertEquals(0, RegistryRemapper.mapBlockEntityType753To754(0)); // furnace
        assertEquals(32, RegistryRemapper.mapBlockEntityType753To754(32)); // 末尾
        assertEquals(RegistryRemapper.DROP,
                RegistryRemapper.mapBlockEntityType753To754(33)); // 範囲外
        assertEquals(RegistryRemapper.DROP,
                RegistryRemapper.mapBlockEntityType753To754(99999));
    }

    @Test
    void attributes() {
        assertEquals(0, RegistryRemapper.mapAttribute754To753(0));
        assertEquals(12, RegistryRemapper.mapAttribute754To753(12)); // 末尾
        assertEquals(RegistryRemapper.DROP, RegistryRemapper.mapAttribute754To753(99999));
        assertEquals(0, RegistryRemapper.mapAttribute753To754(0));
        assertEquals(12, RegistryRemapper.mapAttribute753To754(12)); // 末尾
        assertEquals(RegistryRemapper.DROP, RegistryRemapper.mapAttribute753To754(99999));
        String name = RegistryRemapper.attributeName753(0);
        assertNotNull(name);
        assertEquals("generic.max_health", name);
        assertEquals(0, RegistryRemapper.attributeId753(name));
        assertEquals(0, RegistryRemapper.attributeId753("minecraft:" + name));
        assertNull(RegistryRemapper.attributeName753(99999));
        assertEquals(RegistryRemapper.DROP,
                RegistryRemapper.attributeId753("minecraft:generic.unknown_attribute"));
        assertEquals(RegistryRemapper.DROP, RegistryRemapper.attributeId753(null));
    }

    @Test
    void enchantments() {
        assertEquals(0, RegistryRemapper.mapEnchantment754To753(0)); // protection
        assertEquals(37, RegistryRemapper.mapEnchantment754To753(37)); // vanishing_curse（末尾）
        assertEquals(RegistryRemapper.DROP, RegistryRemapper.mapEnchantment754To753(99999));
        assertEquals(0, RegistryRemapper.mapEnchantment753To754(0));
        assertEquals(37, RegistryRemapper.mapEnchantment753To754(37)); // 末尾
        assertEquals(RegistryRemapper.DROP, RegistryRemapper.mapEnchantment753To754(99999));
    }

    @Test
    void dispatch() {
        assertEquals(0, RegistryRemapper.mapBlockState(0, 753)); // air
        assertEquals(1, RegistryRemapper.mapBlockState(1, 753)); // stone
        assertEquals(0, RegistryRemapper.mapItem(0, 753)); // air
        assertEquals(1, RegistryRemapper.mapItem(1, 753)); // stone
        assertEquals(1, RegistryRemapper.mapEffect(0, 753)); // speed（765=0、763以降=1。753 段は恒等写像）
        assertEquals("ambient.cave", RegistryRemapper.mapSoundName("ambient.cave", 753));
        // mapItemReverse は正しい順序の合成（753→754 は最も内側）。真値アンカー：
        // tropical_fish_spawn_egg（753=809→754=809→755=926）→ 765 id 1067、
        // creeper_head（753=840→754=840→755=957）→ 765 id 1100
        // （755=926→1067・755=957→1100 は RegistryRemapper755Test のアンカーと同じ）。
        assertEquals(1067, RegistryRemapper.mapItemReverse(809, 753));
        assertEquals(1100, RegistryRemapper.mapItemReverse(840, 753));
        assertEquals(0, RegistryRemapper.mapItemReverse(0, 753)); // air
        assertEquals(500, RegistryRemapper.mapItemReverse(
                RegistryRemapper.mapItem(500, 753), 753));
    }

    @Test
    void roundTripIsIdentity() {
        // 754⇔753 は恒等写像のため、往復はすべての id で自分に戻る。
        assertEquals(1311, RegistryRemapper.mapBlockState753To754(
                RegistryRemapper.mapBlockState754To753(1311))); // powered_rail
        assertEquals(17111, RegistryRemapper.mapBlockState753To754(
                RegistryRemapper.mapBlockState754To753(17111))); // 末尾
        assertEquals(809, RegistryRemapper.mapItem753To754(
                RegistryRemapper.mapItem754To753(809))); // tropical_fish_spawn_egg
        assertEquals(975, RegistryRemapper.mapItem753To754(
                RegistryRemapper.mapItem754To753(975))); // 末尾
        assertEquals(59, RegistryRemapper.mapEntityType753To754(
                RegistryRemapper.mapEntityType754To753(59))); // pig
        assertEquals(127, RegistryRemapper.mapBiome753To754(
                RegistryRemapper.mapBiome754To753(127))); // the_void
        assertEquals(816, RegistryRemapper.mapSound753To754(
                RegistryRemapper.mapSound754To753(816))); // block.stone.break
        assertEquals(32, RegistryRemapper.mapBlockEntityType753To754(
                RegistryRemapper.mapBlockEntityType754To753(32))); // 末尾
    }
}
