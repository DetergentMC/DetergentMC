package io.detergentmc.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * {@link RegistryRemapper} の498（1.14.4）⇔758 変換チェック。
 * 期待値は Stage A で実バニラ 1.14.4 jar（SHA-1
 * 3dc3d84a581f14691199cf6831b71ed1296a9fdf）の data-generator
 * ダンプから確定した実数。573 との差分：ブロック状態 11,271
 * （573 は 11,337）、アイテム 877、エンティティ 102（bee 無し）、
 * 音 795、粒子 58。バイオームだけは 75 件・同順で完全一致。
 * 表の全件照合は tools/verify-498-tables.py（37,857 件）が担い、
 * ここでは代表値と上限を固定する。
 */
class RegistryRemapper498Test {

    @Test
    void maxConstantsAreTheReal1144Counts() {
        assertEquals(11270, RegistryRemapper.MAX_BLOCK_STATE_498);
        assertEquals(876, RegistryRemapper.MAX_ITEM_498);
        assertEquals(101, RegistryRemapper.MAX_ENTITY_TYPE_498);
        assertEquals(794, RegistryRemapper.MAX_SOUND_498);
        assertEquals(57, RegistryRemapper.MAX_PARTICLE_498);
        assertEquals(675, RegistryRemapper.MAX_BLOCK_498);
        assertEquals(22, RegistryRemapper.MAX_MENU_498);
        assertEquals(31, RegistryRemapper.MAX_BLOCK_ENTITY_498);
        assertEquals(74, RegistryRemapper.MAX_BIOME_498);
    }

    @Test
    void biomesAreIdentityWith573() {
        // Stage A の実データ証明：498 と 573 のバイオームは75件・同順で一致。
        for (int i = -2; i <= RegistryRemapper.MAX_BIOME_498 + 2; i++) {
            assertEquals(RegistryRemapper.mapBiome573To758(i),
                    RegistryRemapper.mapBiome498To758(i), "biome to758 " + i);
            assertEquals(RegistryRemapper.mapBiome758To573(i),
                    RegistryRemapper.mapBiome758To498(i), "biome from758 " + i);
        }
        assertEquals(RegistryRemapper.biomeNames573(), RegistryRemapper.biomeNames498());
        assertEquals(75, RegistryRemapper.biomeNames498().size());
    }

    @Test
    void spotChecks() {
        assertEquals(0, RegistryRemapper.mapBlockState498To758(0)); // air
        assertEquals(1, RegistryRemapper.mapBlockState498To758(1)); // stone
        assertEquals(0, RegistryRemapper.mapBlockState758To498(0));
        // pig：498 の 54 は 573 の 55 と同じ 758 の pig へ行く。
        assertEquals(RegistryRemapper.mapEntityType573To758(55),
                RegistryRemapper.mapEntityType498To758(54));
        // zombie_pigman_spawn_egg：498 の 753 は 573 の 754 と同じ 758 へ。
        assertEquals(RegistryRemapper.mapItem573To758(754),
                RegistryRemapper.mapItem498To758(753));
        assertEquals("entity.zombified_piglin.ambient",
                RegistryRemapper.mapSoundName498To758("entity.zombie_pigman.ambient"));
    }

    @Test
    void beeFallsBackToParrot() {
        // 498 に bee は無い。758 の bee は parrot（498 の 53）へ寄せる。
        int bee758 = RegistryRemapper.mapEntityType573To758(4);
        assertEquals(53, RegistryRemapper.mapEntityType758To498(bee758));
    }

    @Test
    void cartographyMenuRenamesToCartographyTable() {
        // 498 の "cartography"（21）は 758 の "cartography_table"（22）。
        assertEquals(22, RegistryRemapper.mapMenu498To758(21));
        assertEquals(21, RegistryRemapper.mapMenu758To498(22));
    }

    @Test
    void outOfRangeFallsBackSafely() {
        assertTrue(RegistryRemapper.mapBlockState498To758(-1) >= 0);
        assertTrue(RegistryRemapper.mapBlockState498To758(99999) >= 0);
        assertTrue(RegistryRemapper.mapItem498To758(-1) >= 0);
        assertTrue(RegistryRemapper.mapEntityType498To758(99999) >= 0);
    }
}
