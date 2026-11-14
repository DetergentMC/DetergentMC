package io.detergentmc.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * {@link RegistryRemapper} の764（1.20.2）向けスポットチェック。
 * 期待値は ViaVersion Mappings（mapping-1.20.2.json＝764、
 * mapping-1.20.3.json＝765）の順序付きリストを名前で突き合わせたもの。
 * 生成スクリプト（tools/generate-remap-tables.py）の maps764.json と同じ値。
 */
class RegistryRemapper764Test {

    @Test
    void blockStatesIdentityForUnchanged() {
        assertEquals(0, RegistryRemapper.mapBlockState764(0)); // air
        assertEquals(1, RegistryRemapper.mapBlockState764(1)); // stone
        assertEquals(2005, RegistryRemapper.mapBlockState764(2005)); // short_grass → grass（番号は同じ）
    }

    @Test
    void blockStatesPumpkinMovedIn1203() {
        // 1.20.3 で pumpkin の状態が 5849→6811 へ移動した（763と同じずれ方）。
        assertEquals(5850, RegistryRemapper.mapBlockState764(5849)); // netherrack
        assertEquals(5849, RegistryRemapper.mapBlockState764(6811)); // pumpkin
        assertEquals(6811, RegistryRemapper.mapBlockState764(6810)); // glass_pane
    }

    @Test
    void blockStatesNewBlocksFallBack() {
        assertEquals(11225, RegistryRemapper.mapBlockState764(21085)); // tuff_slab → stone_slab
        assertEquals(13442, RegistryRemapper.mapBlockState764(21088)); // tuff_stairs → stone_stairs
        assertEquals(15780, RegistryRemapper.mapBlockState764(21168)); // tuff_wall → stone_brick_wall
        assertEquals(21081, RegistryRemapper.mapBlockState764(21492)); // polished_tuff → tuff
        assertEquals(6541, RegistryRemapper.mapBlockState764(21903)); // chiseled_tuff → chiseled_stone_bricks
        assertEquals(5711, RegistryRemapper.mapBlockState764(23711)); // copper_door → iron_door（向きを保つ）
        assertEquals(6771, RegistryRemapper.mapBlockState764(24676)); // copper_grate → iron_bars
        assertEquals(5864, RegistryRemapper.mapBlockState764(24692)); // copper_bulb → glowstone
        assertEquals(4277, RegistryRemapper.mapBlockState764(26590)); // crafter → crafting_table
        assertEquals(2873, RegistryRemapper.mapBlockState764(26638)); // trial_spawner → spawner
        assertEquals(2873, RegistryRemapper.mapBlockState764(26643)); // trial_spawner（末尾の状態）
    }

    @Test
    void blockStatesUnknownFallsBackToStone() {
        assertEquals(1, RegistryRemapper.mapBlockState764(99999));
        assertEquals(1, RegistryRemapper.mapBlockState764(-1));
    }

    @Test
    void blockStatesExhaustiveStayInRange() {
        // 全765ブロック状態が764の範囲内の番号になること（生成表の網羅性）。
        for (int i = 0; i <= RegistryRemapper.MAX_BLOCK_STATE_765; i++) {
            int mapped = RegistryRemapper.mapBlockState764(i);
            assertEquals(true, mapped >= 0 && mapped <= RegistryRemapper.MAX_BLOCK_STATE_764,
                    "765 blockstate " + i + " -> " + mapped);
        }
    }

    @Test
    void itemsShiftedAndNew() {
        assertEquals(0, RegistryRemapper.mapItem764(0)); // air
        assertEquals(12, RegistryRemapper.mapItem764(13)); // tuff_slab → tuff
        assertEquals(318, RegistryRemapper.mapItem764(21)); // tuff_bricks → stone_bricks
        assertEquals(75, RegistryRemapper.mapItem764(95)); // chiseled_copper → copper_block
        assertEquals(173, RegistryRemapper.mapItem764(194)); // short_grass → grass
        assertEquals(688, RegistryRemapper.mapItem764(721)); // copper_door → iron_door
        assertEquals(278, RegistryRemapper.mapItem764(978)); // crafter → crafting_table
        assertEquals(971, RegistryRemapper.mapItem764(1010)); // breeze_spawn_egg → blaze_spawn_egg
        assertEquals(310, RegistryRemapper.mapItem764(1302)); // copper_bulb → glowstone
        assertEquals(276, RegistryRemapper.mapItem764(1310)); // trial_spawner → spawner
        assertEquals(655, RegistryRemapper.mapItem764(1311)); // trial_key → tripwire_hook
        assertEquals(1, RegistryRemapper.mapItem764(99999)); // 範囲外は石
    }

    @Test
    void itemsExhaustiveStayInRange() {
        for (int i = 0; i <= RegistryRemapper.MAX_ITEM_765; i++) {
            int mapped = RegistryRemapper.mapItem764(i);
            assertEquals(true, mapped >= 0 && mapped <= RegistryRemapper.MAX_ITEM_764,
                    "765 item " + i + " -> " + mapped);
        }
    }

    @Test
    void itemsReverse764() {
        assertEquals(299, RegistryRemapper.mapItemReverse764(278)); // crafting_table
        assertEquals(194, RegistryRemapper.mapItemReverse764(173)); // grass → short_grass
        assertEquals(1, RegistryRemapper.mapItemReverse764(1)); // stone は同じ
    }

    @Test
    void entityTypes() {
        assertEquals(0, RegistryRemapper.mapEntityType764(0)); // allay
        assertEquals(7, RegistryRemapper.mapEntityType764(10)); // breeze → blaze
        assertEquals(89, RegistryRemapper.mapEntityType764(113)); // wind_charge → small_fireball
        assertEquals(123, RegistryRemapper.mapEntityType764(125)); // fishing_bobber
        assertEquals(72, RegistryRemapper.mapEntityType764(999)); // 範囲外はブタ
    }

    @Test
    void sounds() {
        assertEquals(0, RegistryRemapper.mapSound764(0));
        assertEquals(147, RegistryRemapper.mapSound764(166)); // entity.breeze.inhale → blaze.ambient
        assertEquals(534, RegistryRemapper.mapSound764(310)); // copper_bulb.break → glass.break
        assertEquals(666, RegistryRemapper.mapSound764(322)); // copper_door.close → iron_door.close
        assertEquals(1270, RegistryRemapper.mapSound764(638)); // trial_spawner.break → stone.break
        assertEquals(1270, RegistryRemapper.mapSound764(1312)); // block.stone.break（765=1312 → 764=1270）
        assertEquals(1270, RegistryRemapper.mapSound764(99999)); // 範囲外は石の破壊音
    }

    @Test
    void soundNames764() {
        assertEquals("minecraft:entity.blaze.hurt",
                RegistryRemapper.mapSoundName764("minecraft:entity.breeze.hurt"));
        assertEquals("entity.blaze.shoot",
                RegistryRemapper.mapSoundName764("entity.breeze.shoot"));
        assertEquals("minecraft:block.stone.break",
                RegistryRemapper.mapSoundName764("minecraft:block.stone.break"));
    }

    @Test
    void particles() {
        assertEquals(5, RegistryRemapper.mapParticle764(24)); // gust → cloud
        assertEquals(5, RegistryRemapper.mapParticle764(25)); // gust_emitter → cloud
        assertEquals(51, RegistryRemapper.mapParticle764(54)); // white_smoke → smoke
        assertEquals(25, RegistryRemapper.mapParticle764(27)); // falling_dust
        assertEquals(14, RegistryRemapper.mapParticle764(98)); // dust_plume → dust
        assertEquals(30, RegistryRemapper.mapParticle764(100)); // trial_spawner_detection → sculk_soul
        assertEquals(0, RegistryRemapper.mapParticle764(999)); // 範囲外
    }

    @Test
    void blocksAndMenus() {
        assertEquals(255, RegistryRemapper.mapBlock764(311)); // pumpkin
        assertEquals(909, RegistryRemapper.mapBlock764(910)); // tuff_slab → tuff
        assertEquals(182, RegistryRemapper.mapBlock764(1056)); // crafter → crafting_table
        assertEquals(175, RegistryRemapper.mapBlock764(1057)); // trial_spawner → spawner
        assertEquals(11, RegistryRemapper.mapMenu764(7)); // crafter_3x3 → crafting
        assertEquals(7, RegistryRemapper.mapMenu764(8)); // anvil
    }

    @Test
    void effectsAreIdentityIn764() {
        // 764も765もエフェクトは0ベース（Speed は両方 0）。+1 シフトは無い。
        assertEquals(0, RegistryRemapper.mapEffect764(0));
        assertEquals(5, RegistryRemapper.mapEffect764(5));
        assertEquals(32, RegistryRemapper.mapEffect764(32));
        assertEquals(0, RegistryRemapper.mapEffect764(33)); // 範囲外は Speed
    }

    @Test
    void blockEntityTypes() {
        assertEquals(0, RegistryRemapper.mapBlockEntityType764(0)); // furnace
        assertEquals(40, RegistryRemapper.mapBlockEntityType764(40)); // decorated_pot
        assertEquals(RegistryRemapper.DROP, RegistryRemapper.mapBlockEntityType764(41)); // crafter
        assertEquals(RegistryRemapper.DROP, RegistryRemapper.mapBlockEntityType764(42)); // trial_spawner
        assertEquals(RegistryRemapper.DROP, RegistryRemapper.mapBlockEntityType764(43));
    }

    @Test
    void protocolDispatch() {
        // プロトコル番号での切り替え。765は素通し、763は従来の表。
        assertEquals(2005, RegistryRemapper.mapBlockState(2005, 765));
        assertEquals(2873, RegistryRemapper.mapBlockState(26638, 764));
        assertEquals(2873, RegistryRemapper.mapBlockState(26638, 763));
        assertEquals(0, RegistryRemapper.mapEffect(0, 764)); // 764は恒等
        assertEquals(1, RegistryRemapper.mapEffect(0, 763)); // 763は +1
        assertEquals(0, RegistryRemapper.mapEffect(0, 765)); // 765は素通し
        assertEquals(194, RegistryRemapper.mapItemReverse(173, 764));
        assertEquals(194, RegistryRemapper.mapItemReverse(173, 763));
        assertEquals(1270, RegistryRemapper.mapSound(638, 764));
        assertEquals(1264, RegistryRemapper.mapSound(638, 763));
    }
}
