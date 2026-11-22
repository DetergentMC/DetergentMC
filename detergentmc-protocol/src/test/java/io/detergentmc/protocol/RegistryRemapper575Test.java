package io.detergentmc.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * {@link RegistryRemapper} の575（1.15.1）⇔758 変換チェック。
 * Stage A で実バニラ 1.15.1／1.15.2 両 jar の data-generator ダンプが
 * 全41レジストリで完全一致することを証明したため、575 の表は 578 の表と
 * 全範囲で一致しなければならない。ここでは全範囲の一致と代表値の両方を固定する
 * （tools/verify-575-tables.py の6項目検証と対になる実行時側の固定）。
 */
class RegistryRemapper575Test {

    @Test
    void maxConstantsMatch578() {
        assertEquals(RegistryRemapper.MAX_BLOCK_STATE_578, RegistryRemapper.MAX_BLOCK_STATE_575);
        assertEquals(RegistryRemapper.MAX_ITEM_578, RegistryRemapper.MAX_ITEM_575);
        assertEquals(RegistryRemapper.MAX_ENTITY_TYPE_578, RegistryRemapper.MAX_ENTITY_TYPE_575);
        assertEquals(RegistryRemapper.MAX_SOUND_578, RegistryRemapper.MAX_SOUND_575);
        assertEquals(RegistryRemapper.MAX_PARTICLE_578, RegistryRemapper.MAX_PARTICLE_575);
        assertEquals(RegistryRemapper.MAX_BLOCK_578, RegistryRemapper.MAX_BLOCK_575);
        assertEquals(RegistryRemapper.MAX_MENU_578, RegistryRemapper.MAX_MENU_575);
        assertEquals(RegistryRemapper.MAX_BLOCK_ENTITY_578, RegistryRemapper.MAX_BLOCK_ENTITY_575);
        assertEquals(RegistryRemapper.MAX_BIOME_578, RegistryRemapper.MAX_BIOME_575);
    }

    @Test
    void fullRangeMatches578() {
        for (int i = -2; i <= RegistryRemapper.MAX_BLOCK_STATE_578 + 2; i++) {
            assertEquals(RegistryRemapper.mapBlockState578To758(i),
                    RegistryRemapper.mapBlockState575To758(i), "blockState to758 " + i);
            assertEquals(RegistryRemapper.mapBlockState758To578(i),
                    RegistryRemapper.mapBlockState758To575(i), "blockState from758 " + i);
        }
        for (int i = -2; i <= RegistryRemapper.MAX_ITEM_578 + 2; i++) {
            assertEquals(RegistryRemapper.mapItem578To758(i),
                    RegistryRemapper.mapItem575To758(i), "item to758 " + i);
            assertEquals(RegistryRemapper.mapItem758To578(i),
                    RegistryRemapper.mapItem758To575(i), "item from758 " + i);
        }
        for (int i = -2; i <= RegistryRemapper.MAX_ENTITY_TYPE_578 + 2; i++) {
            assertEquals(RegistryRemapper.mapEntityType578To758(i),
                    RegistryRemapper.mapEntityType575To758(i), "entity to758 " + i);
            assertEquals(RegistryRemapper.mapEntityType758To578(i),
                    RegistryRemapper.mapEntityType758To575(i), "entity from758 " + i);
        }
        for (int i = -2; i <= RegistryRemapper.MAX_SOUND_578 + 2; i++) {
            assertEquals(RegistryRemapper.mapSound578To758(i),
                    RegistryRemapper.mapSound575To758(i), "sound to758 " + i);
            assertEquals(RegistryRemapper.mapSound758To578(i),
                    RegistryRemapper.mapSound758To575(i), "sound from758 " + i);
        }
        for (int i = -2; i <= RegistryRemapper.MAX_PARTICLE_578 + 2; i++) {
            assertEquals(RegistryRemapper.mapParticle578To758(i),
                    RegistryRemapper.mapParticle575To758(i), "particle to758 " + i);
            assertEquals(RegistryRemapper.mapParticle758To578(i),
                    RegistryRemapper.mapParticle758To575(i), "particle from758 " + i);
        }
        for (int i = -2; i <= RegistryRemapper.MAX_BLOCK_578 + 2; i++) {
            assertEquals(RegistryRemapper.mapBlock578To758(i),
                    RegistryRemapper.mapBlock575To758(i), "block to758 " + i);
            assertEquals(RegistryRemapper.mapBlock758To578(i),
                    RegistryRemapper.mapBlock758To575(i), "block from758 " + i);
        }
        for (int i = -2; i <= RegistryRemapper.MAX_MENU_578 + 2; i++) {
            assertEquals(RegistryRemapper.mapMenu578To758(i),
                    RegistryRemapper.mapMenu575To758(i), "menu to758 " + i);
            assertEquals(RegistryRemapper.mapMenu758To578(i),
                    RegistryRemapper.mapMenu758To575(i), "menu from758 " + i);
        }
        for (int i = -2; i <= RegistryRemapper.MAX_BLOCK_ENTITY_578 + 2; i++) {
            assertEquals(RegistryRemapper.mapBlockEntityType578To758(i),
                    RegistryRemapper.mapBlockEntityType575To758(i), "blockEntity to758 " + i);
            assertEquals(RegistryRemapper.mapBlockEntityType758To578(i),
                    RegistryRemapper.mapBlockEntityType758To575(i), "blockEntity from758 " + i);
        }
        for (int i = -2; i <= RegistryRemapper.MAX_BIOME_578 + 2; i++) {
            assertEquals(RegistryRemapper.mapBiome578To758(i),
                    RegistryRemapper.mapBiome575To758(i), "biome to758 " + i);
            assertEquals(RegistryRemapper.mapBiome758To578(i),
                    RegistryRemapper.mapBiome758To575(i), "biome from758 " + i);
        }
        assertEquals(RegistryRemapper.biomeNames578(), RegistryRemapper.biomeNames575());
    }

    @Test
    void spotChecks() {
        assertEquals(0, RegistryRemapper.mapBlockState575To758(0)); // air
        assertEquals(9473, RegistryRemapper.mapBlockState575To758(8687)); // grass_path → dirt_path
        assertEquals(8687, RegistryRemapper.mapBlockState758To575(9473));
        assertEquals(939, RegistryRemapper.mapItem575To758(754)); // zombie_pigman_spawn_egg → zombified_piglin_spawn_egg
        assertEquals(110, RegistryRemapper.mapEntityType575To758(57)); // zombie_pigman → zombified_piglin
        assertEquals(1018, RegistryRemapper.mapSound575To758(651)); // block.stone.break
        assertEquals(18, RegistryRemapper.mapBiome575To758(3)); // mountains → windswept_hills
        assertEquals("entity.zombified_piglin.ambient",
                RegistryRemapper.mapSoundName575To758("entity.zombie_pigman.ambient"));
    }
}
