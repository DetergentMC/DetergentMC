package io.detergentmc.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * 477（1.14）のレジストリ写像。Stage A の実データ照合で、
 * 477 と 477 はワイヤ上のレジストリが一致する（実 jar の
 * data generator 出力で blocks.json・commands.json がバイト一致、
 * registries.json の差は memory_module_type の1カテゴリだけで、
 * 変換表には現れない）と証明済み。そのため
 * 477 の写像は 477 の表へ委譲し、ここでは全範囲で 477 と一致する
 * ことを固定する。実バニラ 1.14 jar の SHA-1 は
 * f1a0073671057f01aa843443fef34330281333ce。
 */
class RegistryRemapper477Test {

    @Test
    void maxConstantsEqual480() {
        assertEquals(RegistryRemapper.MAX_BLOCK_STATE_480, RegistryRemapper.MAX_BLOCK_STATE_477);
        assertEquals(RegistryRemapper.MAX_ITEM_480, RegistryRemapper.MAX_ITEM_477);
        assertEquals(RegistryRemapper.MAX_ENTITY_TYPE_480, RegistryRemapper.MAX_ENTITY_TYPE_477);
        assertEquals(RegistryRemapper.MAX_SOUND_480, RegistryRemapper.MAX_SOUND_477);
        assertEquals(RegistryRemapper.MAX_PARTICLE_480, RegistryRemapper.MAX_PARTICLE_477);
        assertEquals(RegistryRemapper.MAX_BLOCK_480, RegistryRemapper.MAX_BLOCK_477);
        assertEquals(RegistryRemapper.MAX_MENU_480, RegistryRemapper.MAX_MENU_477);
        assertEquals(RegistryRemapper.MAX_BLOCK_ENTITY_480, RegistryRemapper.MAX_BLOCK_ENTITY_477);
        assertEquals(RegistryRemapper.MAX_BIOME_480, RegistryRemapper.MAX_BIOME_477);
        // 実数も固定する（1.14.2 と同じ）。
        assertEquals(11270, RegistryRemapper.MAX_BLOCK_STATE_477);
        assertEquals(876, RegistryRemapper.MAX_ITEM_477);
        assertEquals(101, RegistryRemapper.MAX_ENTITY_TYPE_477);
        assertEquals(794, RegistryRemapper.MAX_SOUND_477);
        assertEquals(74, RegistryRemapper.MAX_BIOME_477);
    }

    @Test
    void blockStateMapsEqual480OverFullRange() {
        for (int i = -2; i <= RegistryRemapper.MAX_BLOCK_STATE_477 + 2; i++) {
            assertEquals(RegistryRemapper.mapBlockState480To758(i),
                    RegistryRemapper.mapBlockState477To758(i), "to758 " + i);
            assertEquals(RegistryRemapper.mapBlockState758To480(i),
                    RegistryRemapper.mapBlockState758To477(i), "from758 " + i);
        }
    }

    @Test
    void itemEntityBiomeMenuMapsEqual480OverFullRange() {
        for (int i = -2; i <= RegistryRemapper.MAX_ITEM_477 + 2; i++) {
            assertEquals(RegistryRemapper.mapItem480To758(i),
                    RegistryRemapper.mapItem477To758(i), "item to758 " + i);
            assertEquals(RegistryRemapper.mapItem758To480(i),
                    RegistryRemapper.mapItem758To477(i), "item from758 " + i);
        }
        for (int i = -2; i <= RegistryRemapper.MAX_ENTITY_TYPE_477 + 2; i++) {
            assertEquals(RegistryRemapper.mapEntityType480To758(i),
                    RegistryRemapper.mapEntityType477To758(i), "entity to758 " + i);
            assertEquals(RegistryRemapper.mapEntityType758To480(i),
                    RegistryRemapper.mapEntityType758To477(i), "entity from758 " + i);
        }
        for (int i = -2; i <= RegistryRemapper.MAX_BIOME_477 + 2; i++) {
            assertEquals(RegistryRemapper.mapBiome480To758(i),
                    RegistryRemapper.mapBiome477To758(i), "biome to758 " + i);
            assertEquals(RegistryRemapper.mapBiome758To480(i),
                    RegistryRemapper.mapBiome758To477(i), "biome from758 " + i);
        }
        for (int i = -2; i <= RegistryRemapper.MAX_MENU_477 + 2; i++) {
            assertEquals(RegistryRemapper.mapMenu480To758(i),
                    RegistryRemapper.mapMenu477To758(i), "menu to758 " + i);
            assertEquals(RegistryRemapper.mapMenu758To480(i),
                    RegistryRemapper.mapMenu758To477(i), "menu from758 " + i);
        }
        for (int i = -2; i <= RegistryRemapper.MAX_BLOCK_ENTITY_477 + 2; i++) {
            assertEquals(RegistryRemapper.mapBlockEntityType480To758(i),
                    RegistryRemapper.mapBlockEntityType477To758(i), "be to758 " + i);
            assertEquals(RegistryRemapper.mapBlockEntityType758To480(i),
                    RegistryRemapper.mapBlockEntityType758To477(i), "be from758 " + i);
        }
        for (int i = -2; i <= RegistryRemapper.MAX_PARTICLE_477 + 2; i++) {
            assertEquals(RegistryRemapper.mapParticle480To758(i),
                    RegistryRemapper.mapParticle477To758(i), "particle to758 " + i);
            assertEquals(RegistryRemapper.mapParticle758To480(i),
                    RegistryRemapper.mapParticle758To477(i), "particle from758 " + i);
        }
    }

    @Test
    void namesEqual480() {
        assertEquals(RegistryRemapper.biomeNames480(), RegistryRemapper.biomeNames477());
        assertEquals(75, RegistryRemapper.biomeNames477().size());
        assertEquals(RegistryRemapper.mapSoundName480To758("entity.zombie_pigman.ambient"),
                RegistryRemapper.mapSoundName477To758("entity.zombie_pigman.ambient"));
        assertEquals(RegistryRemapper.mapSoundName758To480("entity.zombified_piglin.ambient"),
                RegistryRemapper.mapSoundName758To477("entity.zombified_piglin.ambient"));
    }

    @Test
    void outOfRangeFallsBackSafely() {
        assertTrue(RegistryRemapper.mapBlockState477To758(-1) >= 0);
        assertTrue(RegistryRemapper.mapBlockState477To758(99999) >= 0);
        assertTrue(RegistryRemapper.mapItem477To758(-1) >= 0);
        assertTrue(RegistryRemapper.mapEntityType477To758(99999) >= 0);
    }
}
