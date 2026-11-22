package io.detergentmc.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * {@link RegistryRemapper} の485（1.14.2）⇔758 変換チェック。
 * Stage A で実バニラ 1.14.2 jar（SHA-1
 * d0d0fe2b1dc6ab4c65554cb734270872b72dadd6）と 1.14.3 jar の
 * data-generator ダンプを突き合わせ、ワイヤに関わる全レジストリ
 * （ブロック状態・ブロック・アイテム・エンティティ・音・
 * バイオーム等）がバイト単位で一致することを証明済み。そのため
 * 485 の写像は 490 の表へ委譲し、ここでは全範囲で 490 と一致する
 * ことを固定する。唯一の差分は memory_module_type（1.14.3 で
 * opened_doors 追加）だが、変換表には現れない。
 */
class RegistryRemapper485Test {

    @Test
    void maxConstantsEqual490() {
        assertEquals(RegistryRemapper.MAX_BLOCK_STATE_490, RegistryRemapper.MAX_BLOCK_STATE_485);
        assertEquals(RegistryRemapper.MAX_ITEM_490, RegistryRemapper.MAX_ITEM_485);
        assertEquals(RegistryRemapper.MAX_ENTITY_TYPE_490, RegistryRemapper.MAX_ENTITY_TYPE_485);
        assertEquals(RegistryRemapper.MAX_SOUND_490, RegistryRemapper.MAX_SOUND_485);
        assertEquals(RegistryRemapper.MAX_PARTICLE_490, RegistryRemapper.MAX_PARTICLE_485);
        assertEquals(RegistryRemapper.MAX_BLOCK_490, RegistryRemapper.MAX_BLOCK_485);
        assertEquals(RegistryRemapper.MAX_MENU_490, RegistryRemapper.MAX_MENU_485);
        assertEquals(RegistryRemapper.MAX_BLOCK_ENTITY_490, RegistryRemapper.MAX_BLOCK_ENTITY_485);
        assertEquals(RegistryRemapper.MAX_BIOME_490, RegistryRemapper.MAX_BIOME_485);
        // 実数も固定する（1.14.3 と同じ）。
        assertEquals(11270, RegistryRemapper.MAX_BLOCK_STATE_485);
        assertEquals(876, RegistryRemapper.MAX_ITEM_485);
        assertEquals(101, RegistryRemapper.MAX_ENTITY_TYPE_485);
        assertEquals(794, RegistryRemapper.MAX_SOUND_485);
        assertEquals(74, RegistryRemapper.MAX_BIOME_485);
    }

    @Test
    void blockStateMapsEqual490OverFullRange() {
        for (int i = -2; i <= RegistryRemapper.MAX_BLOCK_STATE_485 + 2; i++) {
            assertEquals(RegistryRemapper.mapBlockState490To758(i),
                    RegistryRemapper.mapBlockState485To758(i), "to758 " + i);
            assertEquals(RegistryRemapper.mapBlockState758To490(i),
                    RegistryRemapper.mapBlockState758To485(i), "from758 " + i);
        }
    }

    @Test
    void itemEntityBiomeMenuMapsEqual490OverFullRange() {
        for (int i = -2; i <= RegistryRemapper.MAX_ITEM_485 + 2; i++) {
            assertEquals(RegistryRemapper.mapItem490To758(i),
                    RegistryRemapper.mapItem485To758(i), "item to758 " + i);
            assertEquals(RegistryRemapper.mapItem758To490(i),
                    RegistryRemapper.mapItem758To485(i), "item from758 " + i);
        }
        for (int i = -2; i <= RegistryRemapper.MAX_ENTITY_TYPE_485 + 2; i++) {
            assertEquals(RegistryRemapper.mapEntityType490To758(i),
                    RegistryRemapper.mapEntityType485To758(i), "entity to758 " + i);
            assertEquals(RegistryRemapper.mapEntityType758To490(i),
                    RegistryRemapper.mapEntityType758To485(i), "entity from758 " + i);
        }
        for (int i = -2; i <= RegistryRemapper.MAX_BIOME_485 + 2; i++) {
            assertEquals(RegistryRemapper.mapBiome490To758(i),
                    RegistryRemapper.mapBiome485To758(i), "biome to758 " + i);
            assertEquals(RegistryRemapper.mapBiome758To490(i),
                    RegistryRemapper.mapBiome758To485(i), "biome from758 " + i);
        }
        for (int i = -2; i <= RegistryRemapper.MAX_MENU_485 + 2; i++) {
            assertEquals(RegistryRemapper.mapMenu490To758(i),
                    RegistryRemapper.mapMenu485To758(i), "menu to758 " + i);
            assertEquals(RegistryRemapper.mapMenu758To490(i),
                    RegistryRemapper.mapMenu758To485(i), "menu from758 " + i);
        }
        for (int i = -2; i <= RegistryRemapper.MAX_BLOCK_ENTITY_485 + 2; i++) {
            assertEquals(RegistryRemapper.mapBlockEntityType490To758(i),
                    RegistryRemapper.mapBlockEntityType485To758(i), "be to758 " + i);
            assertEquals(RegistryRemapper.mapBlockEntityType758To490(i),
                    RegistryRemapper.mapBlockEntityType758To485(i), "be from758 " + i);
        }
        for (int i = -2; i <= RegistryRemapper.MAX_PARTICLE_485 + 2; i++) {
            assertEquals(RegistryRemapper.mapParticle490To758(i),
                    RegistryRemapper.mapParticle485To758(i), "particle to758 " + i);
            assertEquals(RegistryRemapper.mapParticle758To490(i),
                    RegistryRemapper.mapParticle758To485(i), "particle from758 " + i);
        }
    }

    @Test
    void namesEqual490() {
        assertEquals(RegistryRemapper.biomeNames490(), RegistryRemapper.biomeNames485());
        assertEquals(75, RegistryRemapper.biomeNames485().size());
        assertEquals(RegistryRemapper.mapSoundName490To758("entity.zombie_pigman.ambient"),
                RegistryRemapper.mapSoundName485To758("entity.zombie_pigman.ambient"));
        assertEquals(RegistryRemapper.mapSoundName758To490("entity.zombified_piglin.ambient"),
                RegistryRemapper.mapSoundName758To485("entity.zombified_piglin.ambient"));
    }

    @Test
    void outOfRangeFallsBackSafely() {
        assertTrue(RegistryRemapper.mapBlockState485To758(-1) >= 0);
        assertTrue(RegistryRemapper.mapBlockState485To758(99999) >= 0);
        assertTrue(RegistryRemapper.mapItem485To758(-1) >= 0);
        assertTrue(RegistryRemapper.mapEntityType485To758(99999) >= 0);
    }
}
