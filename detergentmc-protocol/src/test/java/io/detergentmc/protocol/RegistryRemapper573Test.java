package io.detergentmc.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * {@link RegistryRemapper} の573（1.15）⇔758 変換チェック。
 * Stage A で実バニラ 1.15／1.15.2 両 jar の data-generator ダンプが
 * 全41レジストリで完全一致することを証明したため、573 の表は 578 の表と
 * 全範囲で一致しなければならない。ここでは全範囲の一致と代表値の両方を固定する
 * （tools/verify-573-tables.py の6項目検証と対になる実行時側の固定）。
 */
class RegistryRemapper573Test {

    @Test
    void maxConstantsMatch578() {
        assertEquals(RegistryRemapper.MAX_BLOCK_STATE_578, RegistryRemapper.MAX_BLOCK_STATE_573);
        assertEquals(RegistryRemapper.MAX_ITEM_578, RegistryRemapper.MAX_ITEM_573);
        assertEquals(RegistryRemapper.MAX_ENTITY_TYPE_578, RegistryRemapper.MAX_ENTITY_TYPE_573);
        assertEquals(RegistryRemapper.MAX_SOUND_578, RegistryRemapper.MAX_SOUND_573);
        assertEquals(RegistryRemapper.MAX_PARTICLE_578, RegistryRemapper.MAX_PARTICLE_573);
        assertEquals(RegistryRemapper.MAX_BLOCK_578, RegistryRemapper.MAX_BLOCK_573);
        assertEquals(RegistryRemapper.MAX_MENU_578, RegistryRemapper.MAX_MENU_573);
        assertEquals(RegistryRemapper.MAX_BLOCK_ENTITY_578, RegistryRemapper.MAX_BLOCK_ENTITY_573);
        assertEquals(RegistryRemapper.MAX_BIOME_578, RegistryRemapper.MAX_BIOME_573);
    }

    @Test
    void fullRangeMatches578() {
        for (int i = -2; i <= RegistryRemapper.MAX_BLOCK_STATE_578 + 2; i++) {
            assertEquals(RegistryRemapper.mapBlockState578To758(i),
                    RegistryRemapper.mapBlockState573To758(i), "blockState to758 " + i);
            assertEquals(RegistryRemapper.mapBlockState758To578(i),
                    RegistryRemapper.mapBlockState758To573(i), "blockState from758 " + i);
        }
        for (int i = -2; i <= RegistryRemapper.MAX_ITEM_578 + 2; i++) {
            assertEquals(RegistryRemapper.mapItem578To758(i),
                    RegistryRemapper.mapItem573To758(i), "item to758 " + i);
            assertEquals(RegistryRemapper.mapItem758To578(i),
                    RegistryRemapper.mapItem758To573(i), "item from758 " + i);
        }
        for (int i = -2; i <= RegistryRemapper.MAX_ENTITY_TYPE_578 + 2; i++) {
            assertEquals(RegistryRemapper.mapEntityType578To758(i),
                    RegistryRemapper.mapEntityType573To758(i), "entity to758 " + i);
            assertEquals(RegistryRemapper.mapEntityType758To578(i),
                    RegistryRemapper.mapEntityType758To573(i), "entity from758 " + i);
        }
        for (int i = -2; i <= RegistryRemapper.MAX_SOUND_578 + 2; i++) {
            assertEquals(RegistryRemapper.mapSound578To758(i),
                    RegistryRemapper.mapSound573To758(i), "sound to758 " + i);
            assertEquals(RegistryRemapper.mapSound758To578(i),
                    RegistryRemapper.mapSound758To573(i), "sound from758 " + i);
        }
        for (int i = -2; i <= RegistryRemapper.MAX_PARTICLE_578 + 2; i++) {
            assertEquals(RegistryRemapper.mapParticle578To758(i),
                    RegistryRemapper.mapParticle573To758(i), "particle to758 " + i);
            assertEquals(RegistryRemapper.mapParticle758To578(i),
                    RegistryRemapper.mapParticle758To573(i), "particle from758 " + i);
        }
        for (int i = -2; i <= RegistryRemapper.MAX_BLOCK_578 + 2; i++) {
            assertEquals(RegistryRemapper.mapBlock578To758(i),
                    RegistryRemapper.mapBlock573To758(i), "block to758 " + i);
            assertEquals(RegistryRemapper.mapBlock758To578(i),
                    RegistryRemapper.mapBlock758To573(i), "block from758 " + i);
        }
        for (int i = -2; i <= RegistryRemapper.MAX_MENU_578 + 2; i++) {
            assertEquals(RegistryRemapper.mapMenu578To758(i),
                    RegistryRemapper.mapMenu573To758(i), "menu to758 " + i);
            assertEquals(RegistryRemapper.mapMenu758To578(i),
                    RegistryRemapper.mapMenu758To573(i), "menu from758 " + i);
        }
        for (int i = -2; i <= RegistryRemapper.MAX_BLOCK_ENTITY_578 + 2; i++) {
            assertEquals(RegistryRemapper.mapBlockEntityType578To758(i),
                    RegistryRemapper.mapBlockEntityType573To758(i), "blockEntity to758 " + i);
            assertEquals(RegistryRemapper.mapBlockEntityType758To578(i),
                    RegistryRemapper.mapBlockEntityType758To573(i), "blockEntity from758 " + i);
        }
        for (int i = -2; i <= RegistryRemapper.MAX_BIOME_578 + 2; i++) {
            assertEquals(RegistryRemapper.mapBiome578To758(i),
                    RegistryRemapper.mapBiome573To758(i), "biome to758 " + i);
            assertEquals(RegistryRemapper.mapBiome758To578(i),
                    RegistryRemapper.mapBiome758To573(i), "biome from758 " + i);
        }
        assertEquals(RegistryRemapper.biomeNames578(), RegistryRemapper.biomeNames573());
    }

    @Test
    void spotChecks() {
        assertEquals(0, RegistryRemapper.mapBlockState573To758(0)); // air
        assertEquals(9473, RegistryRemapper.mapBlockState573To758(8687)); // grass_path → dirt_path
        assertEquals(8687, RegistryRemapper.mapBlockState758To573(9473));
        assertEquals(939, RegistryRemapper.mapItem573To758(754)); // zombie_pigman_spawn_egg → zombified_piglin_spawn_egg
        assertEquals(110, RegistryRemapper.mapEntityType573To758(57)); // zombie_pigman → zombified_piglin
        assertEquals(1018, RegistryRemapper.mapSound573To758(651)); // block.stone.break
        assertEquals(18, RegistryRemapper.mapBiome573To758(3)); // mountains → windswept_hills
        assertEquals("entity.zombified_piglin.ambient",
                RegistryRemapper.mapSoundName573To758("entity.zombie_pigman.ambient"));
    }
}
