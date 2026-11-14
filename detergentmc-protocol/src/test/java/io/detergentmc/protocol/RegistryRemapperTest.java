package io.detergentmc.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * {@link RegistryRemapper} のスポットチェック。期待値は ViaVersion
 * Mappings（mapping-1.20.json＝763、mapping-1.20.3.json＝765）の
 * 順序付きリストを名前で突き合わせたもの。
 */
class RegistryRemapperTest {

    @Test
    void blockStatesIdentityForUnchanged() {
        assertEquals(0, RegistryRemapper.mapBlockState(0)); // air
        assertEquals(1, RegistryRemapper.mapBlockState(1)); // stone
        assertEquals(2005, RegistryRemapper.mapBlockState(2005)); // short_grass → grass（番号は同じ）
    }

    @Test
    void blockStatesShiftedByInsertion() {
        // 765で pumpkin が 5849→6811 へ移動し、その間の状態が1ずれた。
        assertEquals(5850, RegistryRemapper.mapBlockState(5849)); // netherrack
        assertEquals(5849, RegistryRemapper.mapBlockState(6811)); // pumpkin
        assertEquals(6811, RegistryRemapper.mapBlockState(6810)); // glass_pane
    }

    @Test
    void blockStatesSkullsDropPowered() {
        // 1.20.2 で powered が追加された。powered を落として同じ向きのものへ。
        assertEquals(8827, RegistryRemapper.mapBlockState(8827)); // skeleton_skull[powered=true,rotation=0]
        assertEquals(8827, RegistryRemapper.mapBlockState(8843)); // skeleton_skull[powered=false,rotation=0]
    }

    @Test
    void blockStatesNewBlocksFallBack() {
        assertEquals(11081, RegistryRemapper.mapBlockState(21082)); // tuff_slab → stone_slab（向きを保つ）
        assertEquals(13301, RegistryRemapper.mapBlockState(21088)); // tuff_stairs → stone_stairs
        assertEquals(15639, RegistryRemapper.mapBlockState(21168)); // tuff_wall → stone_brick_wall
        assertEquals(20940, RegistryRemapper.mapBlockState(21492)); // polished_tuff → tuff
        assertEquals(6541, RegistryRemapper.mapBlockState(21903)); // chiseled_tuff → chiseled_stone_bricks
        assertEquals(5652, RegistryRemapper.mapBlockState(23652)); // copper_door → iron_door（向きを保つ）
        assertEquals(6771, RegistryRemapper.mapBlockState(24676)); // copper_grate → iron_bars
        assertEquals(5864, RegistryRemapper.mapBlockState(24692)); // copper_bulb → glowstone
        assertEquals(4277, RegistryRemapper.mapBlockState(26590)); // crafter → crafting_table
        assertEquals(2873, RegistryRemapper.mapBlockState(26638)); // trial_spawner → spawner
        assertEquals(2873, RegistryRemapper.mapBlockState(26643)); // trial_spawner（末尾の状態）
    }

    @Test
    void blockStatesUnknownFallsBackToStone() {
        assertEquals(1, RegistryRemapper.mapBlockState(99999));
        assertEquals(1, RegistryRemapper.mapBlockState(-1));
    }

    @Test
    void itemsShiftedAndNew() {
        assertEquals(0, RegistryRemapper.mapItem(0)); // air
        assertEquals(12, RegistryRemapper.mapItem(13)); // tuff_slab → tuff
        assertEquals(318, RegistryRemapper.mapItem(21)); // tuff_bricks → stone_bricks
        assertEquals(75, RegistryRemapper.mapItem(95)); // chiseled_copper → copper_block
        assertEquals(173, RegistryRemapper.mapItem(194)); // short_grass → grass
        assertEquals(688, RegistryRemapper.mapItem(721)); // copper_door → iron_door
        assertEquals(278, RegistryRemapper.mapItem(978)); // crafter → crafting_table
        assertEquals(971, RegistryRemapper.mapItem(1010)); // breeze_spawn_egg → blaze_spawn_egg
        assertEquals(310, RegistryRemapper.mapItem(1302)); // copper_bulb → glowstone
        assertEquals(276, RegistryRemapper.mapItem(1310)); // trial_spawner → spawner
        assertEquals(655, RegistryRemapper.mapItem(1311)); // trial_key → tripwire_hook
        assertEquals(1, RegistryRemapper.mapItem(99999)); // 範囲外は石
    }

    @Test
    void itemsReverse() {
        // crafting_table は 765にも同じ名前のアイテムがある（299）ため、
        // crafter（978）にはならない。名前で突き合わせた結果が正しい。
        assertEquals(299, RegistryRemapper.mapItemReverse(278)); // crafting_table
        assertEquals(194, RegistryRemapper.mapItemReverse(173)); // grass → short_grass
        assertEquals(1, RegistryRemapper.mapItemReverse(1)); // stone は同じ
    }

    @Test
    void entityTypes() {
        assertEquals(0, RegistryRemapper.mapEntityType(0)); // allay
        assertEquals(7, RegistryRemapper.mapEntityType(10)); // breeze → blaze
        assertEquals(89, RegistryRemapper.mapEntityType(113)); // wind_charge → small_fireball
        assertEquals(123, RegistryRemapper.mapEntityType(125)); // fishing_bobber
        assertEquals(72, RegistryRemapper.mapEntityType(999)); // 範囲外はブタ
    }

    @Test
    void sounds() {
        assertEquals(0, RegistryRemapper.mapSound(0));
        assertEquals(147, RegistryRemapper.mapSound(166)); // entity.breeze.inhale → blaze.ambient
        assertEquals(534, RegistryRemapper.mapSound(310)); // copper_bulb.break → glass.break
        assertEquals(666, RegistryRemapper.mapSound(322)); // copper_door.close → iron_door.close
        assertEquals(1264, RegistryRemapper.mapSound(638)); // trial_spawner.break → stone.break
        assertEquals(1264, RegistryRemapper.mapSound(99999)); // 範囲外は石の破壊音
    }

    @Test
    void soundNames() {
        assertEquals("minecraft:entity.blaze.hurt",
                RegistryRemapper.mapSoundName("minecraft:entity.breeze.hurt"));
        assertEquals("entity.blaze.shoot",
                RegistryRemapper.mapSoundName("entity.breeze.shoot"));
        assertEquals("minecraft:block.stone.break",
                RegistryRemapper.mapSoundName("minecraft:block.stone.break"));
    }

    @Test
    void particles() {
        assertEquals(5, RegistryRemapper.mapParticle(24)); // gust → cloud
        assertEquals(5, RegistryRemapper.mapParticle(25)); // gust_emitter → cloud
        assertEquals(51, RegistryRemapper.mapParticle(54)); // white_smoke → smoke
        assertEquals(25, RegistryRemapper.mapParticle(27)); // falling_dust
        assertEquals(14, RegistryRemapper.mapParticle(98)); // dust_plume → dust
        assertEquals(30, RegistryRemapper.mapParticle(100)); // trial_spawner_detection → sculk_soul
        assertEquals(0, RegistryRemapper.mapParticle(999)); // 範囲外
    }

    @Test
    void blocksAndMenus() {
        assertEquals(255, RegistryRemapper.mapBlock(311)); // pumpkin
        assertEquals(909, RegistryRemapper.mapBlock(910)); // tuff_slab → tuff
        assertEquals(182, RegistryRemapper.mapBlock(1056)); // crafter → crafting_table
        assertEquals(175, RegistryRemapper.mapBlock(1057)); // trial_spawner → spawner
        assertEquals(11, RegistryRemapper.mapMenu(7)); // crafter_3x3 → crafting
        assertEquals(7, RegistryRemapper.mapMenu(8)); // anvil
    }

    @Test
    void effectsShiftByOne() {
        // 1.20.2 でエフェクトが 0ベースになった（Speed は 765=0、763=1）。
        assertEquals(1, RegistryRemapper.mapEffect(0));
        assertEquals(33, RegistryRemapper.mapEffect(32));
        assertEquals(1, RegistryRemapper.mapEffect(33)); // 範囲外は Speed
    }

    @Test
    void blockEntityTypes() {
        assertEquals(0, RegistryRemapper.mapBlockEntityType(0)); // furnace
        assertEquals(40, RegistryRemapper.mapBlockEntityType(40)); // decorated_pot
        assertEquals(RegistryRemapper.DROP, RegistryRemapper.mapBlockEntityType(41)); // crafter
        assertEquals(RegistryRemapper.DROP, RegistryRemapper.mapBlockEntityType(42)); // trial_spawner
        assertEquals(RegistryRemapper.DROP, RegistryRemapper.mapBlockEntityType(43));
    }
}
