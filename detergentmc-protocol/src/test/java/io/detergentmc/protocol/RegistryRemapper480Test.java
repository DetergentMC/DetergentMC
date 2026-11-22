package io.detergentmc.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * {@link RegistryRemapper} の480（1.14.1）⇔758 変換チェック。
 * Stage A で実バニラ 1.14.1 jar（SHA-1
 * ed76d597a44c5266be2a7fcd77a8270f1f0bc118）と 1.14.2 jar の
 * data-generator ダンプを突き合わせ、ワイヤに関わる全レジストリ
 * （ブロック状態・ブロック・アイテム・エンティティ・音・
 * バイオーム等）がバイト単位で一致することを証明済み。そのため
 * 480 の写像は 485 の表へ委譲し、ここでは全範囲で 485 と一致する
 * ことを固定する。
 */
class RegistryRemapper480Test {

    @Test
    void maxConstantsEqual485() {
        assertEquals(RegistryRemapper.MAX_BLOCK_STATE_485, RegistryRemapper.MAX_BLOCK_STATE_480);
        assertEquals(RegistryRemapper.MAX_ITEM_485, RegistryRemapper.MAX_ITEM_480);
        assertEquals(RegistryRemapper.MAX_ENTITY_TYPE_485, RegistryRemapper.MAX_ENTITY_TYPE_480);
        assertEquals(RegistryRemapper.MAX_SOUND_485, RegistryRemapper.MAX_SOUND_480);
        assertEquals(RegistryRemapper.MAX_PARTICLE_485, RegistryRemapper.MAX_PARTICLE_480);
        assertEquals(RegistryRemapper.MAX_BLOCK_485, RegistryRemapper.MAX_BLOCK_480);
        assertEquals(RegistryRemapper.MAX_MENU_485, RegistryRemapper.MAX_MENU_480);
        assertEquals(RegistryRemapper.MAX_BLOCK_ENTITY_485, RegistryRemapper.MAX_BLOCK_ENTITY_480);
        assertEquals(RegistryRemapper.MAX_BIOME_485, RegistryRemapper.MAX_BIOME_480);
        // 実数も固定する（1.14.2 と同じ）。
        assertEquals(11270, RegistryRemapper.MAX_BLOCK_STATE_480);
        assertEquals(876, RegistryRemapper.MAX_ITEM_480);
        assertEquals(101, RegistryRemapper.MAX_ENTITY_TYPE_480);
        assertEquals(794, RegistryRemapper.MAX_SOUND_480);
        assertEquals(74, RegistryRemapper.MAX_BIOME_480);
    }

    @Test
    void blockStateMapsEqual485OverFullRange() {
        for (int i = -2; i <= RegistryRemapper.MAX_BLOCK_STATE_480 + 2; i++) {
            assertEquals(RegistryRemapper.mapBlockState485To758(i),
                    RegistryRemapper.mapBlockState480To758(i), "to758 " + i);
            assertEquals(RegistryRemapper.mapBlockState758To485(i),
                    RegistryRemapper.mapBlockState758To480(i), "from758 " + i);
        }
    }

    @Test
    void itemEntityBiomeMenuMapsEqual485OverFullRange() {
        for (int i = -2; i <= RegistryRemapper.MAX_ITEM_480 + 2; i++) {
            assertEquals(RegistryRemapper.mapItem485To758(i),
                    RegistryRemapper.mapItem480To758(i), "item to758 " + i);
            assertEquals(RegistryRemapper.mapItem758To485(i),
                    RegistryRemapper.mapItem758To480(i), "item from758 " + i);
        }
        for (int i = -2; i <= RegistryRemapper.MAX_ENTITY_TYPE_480 + 2; i++) {
            assertEquals(RegistryRemapper.mapEntityType485To758(i),
                    RegistryRemapper.mapEntityType480To758(i), "entity to758 " + i);
            assertEquals(RegistryRemapper.mapEntityType758To485(i),
                    RegistryRemapper.mapEntityType758To480(i), "entity from758 " + i);
        }
        for (int i = -2; i <= RegistryRemapper.MAX_BIOME_480 + 2; i++) {
            assertEquals(RegistryRemapper.mapBiome485To758(i),
                    RegistryRemapper.mapBiome480To758(i), "biome to758 " + i);
            assertEquals(RegistryRemapper.mapBiome758To485(i),
                    RegistryRemapper.mapBiome758To480(i), "biome from758 " + i);
        }
        for (int i = -2; i <= RegistryRemapper.MAX_MENU_480 + 2; i++) {
            assertEquals(RegistryRemapper.mapMenu485To758(i),
                    RegistryRemapper.mapMenu480To758(i), "menu to758 " + i);
            assertEquals(RegistryRemapper.mapMenu758To485(i),
                    RegistryRemapper.mapMenu758To480(i), "menu from758 " + i);
        }
        for (int i = -2; i <= RegistryRemapper.MAX_BLOCK_ENTITY_480 + 2; i++) {
            assertEquals(RegistryRemapper.mapBlockEntityType485To758(i),
                    RegistryRemapper.mapBlockEntityType480To758(i), "be to758 " + i);
            assertEquals(RegistryRemapper.mapBlockEntityType758To485(i),
                    RegistryRemapper.mapBlockEntityType758To480(i), "be from758 " + i);
        }
        for (int i = -2; i <= RegistryRemapper.MAX_PARTICLE_480 + 2; i++) {
            assertEquals(RegistryRemapper.mapParticle485To758(i),
                    RegistryRemapper.mapParticle480To758(i), "particle to758 " + i);
            assertEquals(RegistryRemapper.mapParticle758To485(i),
                    RegistryRemapper.mapParticle758To480(i), "particle from758 " + i);
        }
    }

    @Test
    void namesEqual485() {
        assertEquals(RegistryRemapper.biomeNames485(), RegistryRemapper.biomeNames480());
        assertEquals(75, RegistryRemapper.biomeNames480().size());
        assertEquals(RegistryRemapper.mapSoundName485To758("entity.zombie_pigman.ambient"),
                RegistryRemapper.mapSoundName480To758("entity.zombie_pigman.ambient"));
        assertEquals(RegistryRemapper.mapSoundName758To485("entity.zombified_piglin.ambient"),
                RegistryRemapper.mapSoundName758To480("entity.zombified_piglin.ambient"));
    }

    @Test
    void outOfRangeFallsBackSafely() {
        assertTrue(RegistryRemapper.mapBlockState480To758(-1) >= 0);
        assertTrue(RegistryRemapper.mapBlockState480To758(99999) >= 0);
        assertTrue(RegistryRemapper.mapItem480To758(-1) >= 0);
        assertTrue(RegistryRemapper.mapEntityType480To758(99999) >= 0);
    }
}
