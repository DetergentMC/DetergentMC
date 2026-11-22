package io.detergentmc.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * 404（1.13.2）のレジストリ写像。404 側の実数は実バニラ 1.13.2 jar の
 * data generator 出力（blocks 598 種・ブロック状態 8,599・アイテム 790）と
 * minecraft-data 1.13.2（エンティティ mob 95・バイオーム 73）から確定。
 * 生成した表は tools/verify-404-tables.py で 35,377 件全件照合済み。
 * 実バニラ 1.13.2 jar の SHA-1 は 3737db93722a9e39eeada7c27e7aca28b144ffa7。
 */
class RegistryRemapper404Test {

    @Test
    void maxConstantsAreThe1132Values() {
        assertEquals(8598, RegistryRemapper.MAX_BLOCK_STATE_404);
        assertEquals(789, RegistryRemapper.MAX_ITEM_404);
        assertEquals(94, RegistryRemapper.MAX_ENTITY_TYPE_404);
        assertEquals(661, RegistryRemapper.MAX_SOUND_404);
        assertEquals(49, RegistryRemapper.MAX_PARTICLE_404);
        assertEquals(597, RegistryRemapper.MAX_BLOCK_404);
        assertEquals(72, RegistryRemapper.MAX_BIOME_404);
        assertEquals(41, RegistryRemapper.MAX_DAMAGE_TYPE_404);
        assertEquals(24, RegistryRemapper.MAX_BLOCK_ENTITY_404);
        assertEquals(25, RegistryRemapper.MAX_PAINTING_VARIANT_404);
        assertEquals(29, RegistryRemapper.MAX_EFFECT_404);
    }

    @Test
    void identityAnchors() {
        // 空気と石は両世代で 0／1。
        assertEquals(0, RegistryRemapper.mapBlockState404To758(0));
        assertEquals(1, RegistryRemapper.mapBlockState404To758(1));
        assertEquals(0, RegistryRemapper.mapBlockState758To404(0));
        assertEquals(1, RegistryRemapper.mapBlockState758To404(1));
        assertEquals(1, RegistryRemapper.mapItem404To758(1));
        // エンティティ：area_effect_cloud は両世代 0、arrow は両世代 2。
        assertEquals(0, RegistryRemapper.mapEntityType404To758(0));
        assertEquals(2, RegistryRemapper.mapEntityType404To758(2));
        // トライデント：404 では最後尾（94）、758 では 93（実サーバーで確認済み）。
        assertEquals(93, RegistryRemapper.mapEntityType404To758(94));
        assertEquals(94, RegistryRemapper.mapEntityType758To404(93));
        // ブロックエンティティ：furnace 0・chest 1 は両世代で同順。
        assertEquals(1, RegistryRemapper.mapBlockEntityType404To758(1));
        assertEquals(1, RegistryRemapper.mapBlockEntityType758To404(1));
    }

    @Test
    void roundTripsHoldForSamples() {
        for (int i = 0; i <= RegistryRemapper.MAX_BLOCK_STATE_404; i += 37) {
            int as758 = RegistryRemapper.mapBlockState404To758(i);
            assertTrue(as758 >= 0, "block state " + i);
            assertEquals(i, RegistryRemapper.mapBlockState758To404(as758),
                    "block state round trip " + i);
        }
        for (int i = 0; i <= RegistryRemapper.MAX_ITEM_404; i++) {
            int as758 = RegistryRemapper.mapItem404To758(i);
            assertTrue(as758 >= 0, "item " + i);
            assertEquals(i, RegistryRemapper.mapItem758To404(as758),
                    "item round trip " + i);
        }
        for (int i = 0; i <= RegistryRemapper.MAX_ENTITY_TYPE_404; i++) {
            int as758 = RegistryRemapper.mapEntityType404To758(i);
            assertTrue(as758 >= 0, "entity " + i);
            assertEquals(i, RegistryRemapper.mapEntityType758To404(as758),
                    "entity round trip " + i);
        }
        // バイオームは変種（hills 等）が素のバイオームへ寄るため往復は
        // 保存されない。plains（1）だけ往復を固定し、他は「758 側へ寄せた
        // 結果が安定すること」（再写像で同じ 758 値になる）を確認する。
        assertEquals(1, RegistryRemapper.mapBiome758To404(
                RegistryRemapper.mapBiome404To758(1)));
        for (int i = 0; i <= RegistryRemapper.MAX_BIOME_404; i++) {
            int as758 = RegistryRemapper.mapBiome404To758(i);
            assertTrue(as758 >= 0, "biome " + i);
            assertEquals(as758, RegistryRemapper.mapBiome404To758(
                    RegistryRemapper.mapBiome758To404(as758)),
                    "biome collapse is stable " + i);
        }
    }

    @Test
    void biomeNames404Are73() {
        assertEquals(73, RegistryRemapper.biomeNames404().size());
        assertEquals("plains", RegistryRemapper.biomeNames404().get(1));
    }

    @Test
    void soundNameFallbacksCoverRenames() {
        // zombie_pigman は 1.14 で zombified_piglin に改名された。
        assertEquals("entity.zombified_piglin.ambient",
                RegistryRemapper.mapSoundName404To758("entity.zombie_pigman.ambient"));
        assertEquals("entity.zombie_pigman.ambient",
                RegistryRemapper.mapSoundName758To404("entity.zombified_piglin.ambient"));
    }

    @Test
    void outOfRangeFallsBackSafely() {
        assertTrue(RegistryRemapper.mapBlockState404To758(-1) >= 0);
        assertTrue(RegistryRemapper.mapBlockState404To758(99999) >= 0);
        assertTrue(RegistryRemapper.mapItem404To758(-1) >= 0);
        assertTrue(RegistryRemapper.mapEntityType404To758(99999) >= 0);
        assertTrue(RegistryRemapper.mapBiome404To758(99999) >= 0);
    }
}
