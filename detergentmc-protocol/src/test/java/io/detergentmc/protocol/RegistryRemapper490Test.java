package io.detergentmc.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * {@link RegistryRemapper} の490（1.14.3）⇔758 変換チェック。
 * Stage A で実バニラ 1.14.3 jar（SHA-1
 * d0d0fe2b1dc6ab4c65554cb734270872b72dadd6）と 1.14.4 jar の
 * data-generator ダンプを突き合わせ、ワイヤに関わる全レジストリ
 * （ブロック状態・ブロック・アイテム・エンティティ・音・
 * バイオーム等）がバイト単位で一致することを証明済み。そのため
 * 490 の写像は 498 の表へ委譲し、ここでは全範囲で 498 と一致する
 * ことを固定する。唯一の差分は memory_module_type（1.14.4 で
 * opened_doors 追加）だが、変換表には現れない。
 */
class RegistryRemapper490Test {

    @Test
    void maxConstantsEqual498() {
        assertEquals(RegistryRemapper.MAX_BLOCK_STATE_498, RegistryRemapper.MAX_BLOCK_STATE_490);
        assertEquals(RegistryRemapper.MAX_ITEM_498, RegistryRemapper.MAX_ITEM_490);
        assertEquals(RegistryRemapper.MAX_ENTITY_TYPE_498, RegistryRemapper.MAX_ENTITY_TYPE_490);
        assertEquals(RegistryRemapper.MAX_SOUND_498, RegistryRemapper.MAX_SOUND_490);
        assertEquals(RegistryRemapper.MAX_PARTICLE_498, RegistryRemapper.MAX_PARTICLE_490);
        assertEquals(RegistryRemapper.MAX_BLOCK_498, RegistryRemapper.MAX_BLOCK_490);
        assertEquals(RegistryRemapper.MAX_MENU_498, RegistryRemapper.MAX_MENU_490);
        assertEquals(RegistryRemapper.MAX_BLOCK_ENTITY_498, RegistryRemapper.MAX_BLOCK_ENTITY_490);
        assertEquals(RegistryRemapper.MAX_BIOME_498, RegistryRemapper.MAX_BIOME_490);
        // 実数も固定する（1.14.4 と同じ）。
        assertEquals(11270, RegistryRemapper.MAX_BLOCK_STATE_490);
        assertEquals(876, RegistryRemapper.MAX_ITEM_490);
        assertEquals(101, RegistryRemapper.MAX_ENTITY_TYPE_490);
        assertEquals(794, RegistryRemapper.MAX_SOUND_490);
        assertEquals(74, RegistryRemapper.MAX_BIOME_490);
    }

    @Test
    void blockStateMapsEqual498OverFullRange() {
        for (int i = -2; i <= RegistryRemapper.MAX_BLOCK_STATE_490 + 2; i++) {
            assertEquals(RegistryRemapper.mapBlockState498To758(i),
                    RegistryRemapper.mapBlockState490To758(i), "to758 " + i);
            assertEquals(RegistryRemapper.mapBlockState758To498(i),
                    RegistryRemapper.mapBlockState758To490(i), "from758 " + i);
        }
    }

    @Test
    void itemEntityBiomeMenuMapsEqual498OverFullRange() {
        for (int i = -2; i <= RegistryRemapper.MAX_ITEM_490 + 2; i++) {
            assertEquals(RegistryRemapper.mapItem498To758(i),
                    RegistryRemapper.mapItem490To758(i), "item to758 " + i);
            assertEquals(RegistryRemapper.mapItem758To498(i),
                    RegistryRemapper.mapItem758To490(i), "item from758 " + i);
        }
        for (int i = -2; i <= RegistryRemapper.MAX_ENTITY_TYPE_490 + 2; i++) {
            assertEquals(RegistryRemapper.mapEntityType498To758(i),
                    RegistryRemapper.mapEntityType490To758(i), "entity to758 " + i);
            assertEquals(RegistryRemapper.mapEntityType758To498(i),
                    RegistryRemapper.mapEntityType758To490(i), "entity from758 " + i);
        }
        for (int i = -2; i <= RegistryRemapper.MAX_BIOME_490 + 2; i++) {
            assertEquals(RegistryRemapper.mapBiome498To758(i),
                    RegistryRemapper.mapBiome490To758(i), "biome to758 " + i);
            assertEquals(RegistryRemapper.mapBiome758To498(i),
                    RegistryRemapper.mapBiome758To490(i), "biome from758 " + i);
        }
        for (int i = -2; i <= RegistryRemapper.MAX_MENU_490 + 2; i++) {
            assertEquals(RegistryRemapper.mapMenu498To758(i),
                    RegistryRemapper.mapMenu490To758(i), "menu to758 " + i);
            assertEquals(RegistryRemapper.mapMenu758To498(i),
                    RegistryRemapper.mapMenu758To490(i), "menu from758 " + i);
        }
        for (int i = -2; i <= RegistryRemapper.MAX_BLOCK_ENTITY_490 + 2; i++) {
            assertEquals(RegistryRemapper.mapBlockEntityType498To758(i),
                    RegistryRemapper.mapBlockEntityType490To758(i), "be to758 " + i);
            assertEquals(RegistryRemapper.mapBlockEntityType758To498(i),
                    RegistryRemapper.mapBlockEntityType758To490(i), "be from758 " + i);
        }
        for (int i = -2; i <= RegistryRemapper.MAX_PARTICLE_490 + 2; i++) {
            assertEquals(RegistryRemapper.mapParticle498To758(i),
                    RegistryRemapper.mapParticle490To758(i), "particle to758 " + i);
            assertEquals(RegistryRemapper.mapParticle758To498(i),
                    RegistryRemapper.mapParticle758To490(i), "particle from758 " + i);
        }
    }

    @Test
    void namesEqual498() {
        assertEquals(RegistryRemapper.biomeNames498(), RegistryRemapper.biomeNames490());
        assertEquals(75, RegistryRemapper.biomeNames490().size());
        assertEquals(RegistryRemapper.mapSoundName498To758("entity.zombie_pigman.ambient"),
                RegistryRemapper.mapSoundName490To758("entity.zombie_pigman.ambient"));
        assertEquals(RegistryRemapper.mapSoundName758To498("entity.zombified_piglin.ambient"),
                RegistryRemapper.mapSoundName758To490("entity.zombified_piglin.ambient"));
    }

    @Test
    void outOfRangeFallsBackSafely() {
        assertTrue(RegistryRemapper.mapBlockState490To758(-1) >= 0);
        assertTrue(RegistryRemapper.mapBlockState490To758(99999) >= 0);
        assertTrue(RegistryRemapper.mapItem490To758(-1) >= 0);
        assertTrue(RegistryRemapper.mapEntityType490To758(99999) >= 0);
    }
}
