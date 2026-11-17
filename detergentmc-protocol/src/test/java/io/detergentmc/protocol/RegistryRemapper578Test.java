package io.detergentmc.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * {@link RegistryRemapper} の578（1.15.2）⇔758（1.18.2）向けスポットチェック。
 * 期待値は実バニラ 1.15.2 jar の data-generator ダンプ（registries.json /
 * blocks.json）と ViaVersion の mapping-1.15 / mapping-1.18 を名前で
 * 突き合わせたもの（生成スクリプト tools/gen-578-tables.py が検証済み：
 * 全37,955件一致）。
 *
 * <p>差分：ブロック状態 11,337⇔20,342、ブロック 680⇔898、アイテム
 * 884⇔1,101、エンティティ 103⇔113、音 810⇔1,203、パーティクル 62⇔88、
 * バイオーム 75⇔61。絵画（motive 26件）とエフェクト（32件）は両側で
 * 同一順序のため表を持たない。
 */
class RegistryRemapper578Test {

    @Test
    void blockStatesIdentityAndRenames() {
        assertEquals(0, RegistryRemapper.mapBlockState578To758(0)); // air
        assertEquals(1, RegistryRemapper.mapBlockState578To758(1)); // stone
        assertEquals(9, RegistryRemapper.mapBlockState578To758(9)); // grass_block 既定
        assertEquals(77, RegistryRemapper.mapBlockState578To758(73)); // oak_log[axis=y]
        assertEquals(9473, RegistryRemapper.mapBlockState578To758(8687)); // grass_path → dirt_path
        assertEquals(0, RegistryRemapper.mapBlockState758To578(0));
        assertEquals(1, RegistryRemapper.mapBlockState758To578(1));
        assertEquals(8687, RegistryRemapper.mapBlockState758To578(9473)); // dirt_path → grass_path
        assertEquals(1, RegistryRemapper.mapBlockState578To758(99999)); // 範囲外は石
        assertEquals(1, RegistryRemapper.mapBlockState758To578(99999));
    }

    @Test
    void blockStateCauldronLevelIsPreserved() {
        // 578 の cauldron[level] は 758 で level=0 が素の cauldron、
        // level>0 が water_cauldron[level] に分かれる（特殊ケース）。
        assertEquals(5342, RegistryRemapper.mapBlockState578To758(5125)); // level=0
        assertEquals(5343, RegistryRemapper.mapBlockState578To758(5126)); // level=1
        assertEquals(5345, RegistryRemapper.mapBlockState578To758(5128)); // level=3
        assertEquals(5125, RegistryRemapper.mapBlockState758To578(5342));
        assertEquals(5126, RegistryRemapper.mapBlockState758To578(5343));
        assertEquals(5128, RegistryRemapper.mapBlockState758To578(5345));
    }

    @Test
    void blockStateWallShapeIsConverted() {
        // 壁：578 は east 等が bool、758 は none/low/tall。既定状態同士が対応する。
        assertEquals(12740, RegistryRemapper.mapBlockState578To758(10710));
        assertEquals(10710, RegistryRemapper.mapBlockState758To578(12740));
    }

    @Test
    void blockStateJigsawOrientation() {
        // ジグソー：578 の facing は 758 の orientation へ。実測の既定ペア
        // （578 の 11276 ⇔ 758 の 15999、jar の表で検証済み）。
        assertEquals(15999, RegistryRemapper.mapBlockState578To758(11276));
        assertEquals(11276, RegistryRemapper.mapBlockState758To578(15999));
    }

    @Test
    void items() {
        assertEquals(0, RegistryRemapper.mapItem578To758(0)); // air
        assertEquals(1, RegistryRemapper.mapItem578To758(1)); // stone
        assertEquals(939, RegistryRemapper.mapItem578To758(754)); // zombie_pigman_spawn_egg → zombified_piglin_spawn_egg
        assertEquals(393, RegistryRemapper.mapItem578To758(322)); // grass_path → dirt_path
        assertEquals(541, RegistryRemapper.mapItem758To578(724)); // netherite_sword → diamond_sword
        assertEquals(529, RegistryRemapper.mapItem758To578(697)); // netherite_ingot → diamond
        assertEquals(146, RegistryRemapper.mapItem758To578(371)); // light → torch
        assertEquals(1, RegistryRemapper.mapItem578To758(99999)); // 範囲外は石
        assertEquals(1, RegistryRemapper.mapItem758To578(99999));
    }

    @Test
    void entities() {
        assertEquals(64, RegistryRemapper.mapEntityType578To758(55)); // pig
        assertEquals(110, RegistryRemapper.mapEntityType578To758(57)); // zombie_pigman → zombified_piglin
        assertEquals(57, RegistryRemapper.mapEntityType758To578(110));
        assertEquals(77, RegistryRemapper.mapEntityType758To578(3)); // axolotl → tropical_fish
        assertEquals(74, RegistryRemapper.mapEntityType758To578(33)); // glow_squid → squid
        assertEquals(62, RegistryRemapper.mapEntityType758To578(34)); // goat → sheep
        assertEquals(36, RegistryRemapper.mapEntityType758To578(32)); // glow_item_frame → item_frame
        assertEquals(0, RegistryRemapper.mapEntityType758To578(49)); // marker → area_effect_cloud
        assertEquals(64, RegistryRemapper.mapEntityType578To758(99999)); // 範囲外はブタ（758側）
        assertEquals(55, RegistryRemapper.mapEntityType758To578(99999)); // 範囲外はブタ（578側）
    }

    @Test
    void sounds() {
        assertEquals(1018, RegistryRemapper.mapSound578To758(651)); // block.stone.break
        assertEquals(651, RegistryRemapper.mapSound758To578(1018));
        assertEquals(651, RegistryRemapper.mapSound758To578(99999)); // 範囲外は石の破壊音（578）
        assertEquals("item.armor.equip_diamond",
                RegistryRemapper.mapSoundName758To578("item.armor.equip_netherite"));
        assertEquals("entity.zombified_piglin.ambient",
                RegistryRemapper.mapSoundName578To758("entity.zombie_pigman.ambient"));
        assertEquals("entity.zombie_pigman.ambient",
                RegistryRemapper.mapSoundName758To578("entity.zombified_piglin.ambient"));
    }

    @Test
    void biomes() {
        assertEquals(1, RegistryRemapper.mapBiome578To758(1)); // plains
        assertEquals(18, RegistryRemapper.mapBiome578To758(3)); // mountains → windswept_hills
        assertEquals(3, RegistryRemapper.mapBiome578To758(12)); // snowy_tundra → snowy_plains
        assertEquals(8, RegistryRemapper.mapBiome758To578(53)); // crimson_forest → nether_wastes
        assertEquals(1, RegistryRemapper.mapBiome758To578(1));
    }

    @Test
    void menusAndBlockEntities() {
        assertEquals(0, RegistryRemapper.mapMenu578To758(0)); // generic_9x1
        assertEquals(11, RegistryRemapper.mapMenu578To758(11)); // crafting
        assertEquals(7, RegistryRemapper.mapMenu758To578(20)); // smithing → anvil
        assertEquals(20, RegistryRemapper.mapMenu758To578(21)); // smoker（578 では 20）
        assertEquals(-1, RegistryRemapper.mapBlockEntityType758To578(33)); // sculk_sensor → DROP
        assertEquals(1, RegistryRemapper.mapBlockEntityType578To758(1)); // chest
    }
}
