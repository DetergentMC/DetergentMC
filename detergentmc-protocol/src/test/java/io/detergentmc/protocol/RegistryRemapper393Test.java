package io.detergentmc.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * 393（1.13）のレジストリ写像。393 側の実数は実バニラ 1.13 jar の
 * data generator 出力（blocks 593 種・ブロック状態 8,582・アイテム 785）と
 * minecraft-data 1.13（エンティティ mob 95・バイオーム 73）から確定。
 * 404 との差分はサンゴまわりに集中する（dead coral 5種が無く、
 * tnt／coral／conduit のプロパティ追加も無い）。表は 393⇔404 の対応を
 * 検証済みの 404⇔758 表と合成して作り、tools/verify-393-tables.py で
 * 35,350 件全件照合済み。実バニラ 1.13 jar の SHA-1 は
 * d0caafb8438ebd206f99930cfaecfa6c9a13dca0。
 */
class RegistryRemapper393Test {

    @Test
    void maxConstantsAreThe113Values() {
        assertEquals(8581, RegistryRemapper.MAX_BLOCK_STATE_393);
        assertEquals(784, RegistryRemapper.MAX_ITEM_393);
        assertEquals(94, RegistryRemapper.MAX_ENTITY_TYPE_393);
        assertEquals(661, RegistryRemapper.MAX_SOUND_393);
        assertEquals(49, RegistryRemapper.MAX_PARTICLE_393);
        assertEquals(592, RegistryRemapper.MAX_BLOCK_393);
        assertEquals(72, RegistryRemapper.MAX_BIOME_393);
        assertEquals(41, RegistryRemapper.MAX_DAMAGE_TYPE_393);
        assertEquals(24, RegistryRemapper.MAX_BLOCK_ENTITY_393);
        assertEquals(25, RegistryRemapper.MAX_PAINTING_VARIANT_393);
        assertEquals(29, RegistryRemapper.MAX_EFFECT_393);
    }

    @Test
    void identityAnchors() {
        // 空気と石は両世代で 0／1。
        assertEquals(0, RegistryRemapper.mapBlockState393To758(0));
        assertEquals(1, RegistryRemapper.mapBlockState393To758(1));
        assertEquals(0, RegistryRemapper.mapBlockState758To393(0));
        assertEquals(1, RegistryRemapper.mapBlockState758To393(1));
        assertEquals(1, RegistryRemapper.mapItem393To758(1));
        // エンティティ：area_effect_cloud は両世代 0、arrow は両世代 2。
        assertEquals(0, RegistryRemapper.mapEntityType393To758(0));
        assertEquals(2, RegistryRemapper.mapEntityType393To758(2));
        // トライデント：393 では最後尾（94）、758 では 93（404 と同じ並び）。
        assertEquals(93, RegistryRemapper.mapEntityType393To758(94));
        assertEquals(94, RegistryRemapper.mapEntityType758To393(93));
        // ブロックエンティティ：furnace 0・chest 1 は両世代で同順。
        assertEquals(1, RegistryRemapper.mapBlockEntityType393To758(1));
        assertEquals(1, RegistryRemapper.mapBlockEntityType758To393(1));
    }

    @Test
    void coralDifferencesAreReal() {
        // 393 の tube_coral はプロパティ無しの単一状態 8459。
        // 758 へは waterlogged=true の既定値で写る（9780）。
        assertEquals(9780, RegistryRemapper.mapBlockState393To758(8459));
        // 758 の dead_tube_coral は 393 に無いため、生きた tube_coral
        // （8459）へ寄せる（404 の 8460 経由で 758 番号を引く）。
        int deadTube758 = RegistryRemapper.mapBlockState404To758(8460);
        assertEquals(8459, RegistryRemapper.mapBlockState758To393(deadTube758));
        // 758 の tube_coral[waterlogged=true] は 393 の tube_coral そのもの。
        int tube758 = RegistryRemapper.mapBlockState404To758(8470);
        assertEquals(8459, RegistryRemapper.mapBlockState758To393(tube758));
        // tnt：393 はプロパティ無し（1126）。758 の tnt[unstable=false]
        // からも 1126 へ戻る。
        assertEquals(1126, RegistryRemapper.mapBlockState758To393(
                RegistryRemapper.mapBlockState404To758(1127)));
        // アイテム：tube_coral は 393・404 とも 438。dead_tube_coral
        // （404 の 447）は 393 では生きた tube_coral（438）へ寄る。
        // 393 の 447 は別のアイテム（404 の 447 と数値がずれる実証）。
        assertEquals(438, RegistryRemapper.mapItem758To393(
                RegistryRemapper.mapItem404To758(447)));
        assertTrue(RegistryRemapper.mapItem393To758(447)
                != RegistryRemapper.mapItem404To758(447));
    }

    @Test
    void roundTripsHoldForSamples() {
        for (int i = 0; i <= RegistryRemapper.MAX_BLOCK_STATE_393; i += 37) {
            int as758 = RegistryRemapper.mapBlockState393To758(i);
            assertTrue(as758 >= 0, "block state " + i);
            assertEquals(i, RegistryRemapper.mapBlockState758To393(as758),
                    "block state round trip " + i);
        }
        for (int i = 0; i <= RegistryRemapper.MAX_ITEM_393; i++) {
            int as758 = RegistryRemapper.mapItem393To758(i);
            assertTrue(as758 >= 0, "item " + i);
            assertEquals(i, RegistryRemapper.mapItem758To393(as758),
                    "item round trip " + i);
        }
        for (int i = 0; i <= RegistryRemapper.MAX_ENTITY_TYPE_393; i++) {
            int as758 = RegistryRemapper.mapEntityType393To758(i);
            assertTrue(as758 >= 0, "entity " + i);
            assertEquals(i, RegistryRemapper.mapEntityType758To393(as758),
                    "entity round trip " + i);
        }
        // バイオームは変種（hills 等）が素のバイオームへ寄るため往復は
        // 保存されない。plains（1）だけ往復を固定し、他は「758 側へ寄せた
        // 結果が安定すること」を確認する（404 と同じ方針）。
        assertEquals(1, RegistryRemapper.mapBiome758To393(
                RegistryRemapper.mapBiome393To758(1)));
        for (int i = 0; i <= RegistryRemapper.MAX_BIOME_393; i++) {
            int as758 = RegistryRemapper.mapBiome393To758(i);
            assertTrue(as758 >= 0, "biome " + i);
            assertEquals(as758, RegistryRemapper.mapBiome393To758(
                    RegistryRemapper.mapBiome758To393(as758)),
                    "biome collapse is stable " + i);
        }
    }

    @Test
    void biomeNames393Are73() {
        assertEquals(73, RegistryRemapper.biomeNames393().size());
        assertEquals("plains", RegistryRemapper.biomeNames393().get(1));
    }

    @Test
    void soundNameFallbacksCoverRenames() {
        // zombie_pigman は 1.14 で zombified_piglin に改名された（404 と同じ表）。
        assertEquals("entity.zombified_piglin.ambient",
                RegistryRemapper.mapSoundName393To758("entity.zombie_pigman.ambient"));
        assertEquals("entity.zombie_pigman.ambient",
                RegistryRemapper.mapSoundName758To393("entity.zombified_piglin.ambient"));
    }

    @Test
    void outOfRangeFallsBackSafely() {
        assertTrue(RegistryRemapper.mapBlockState393To758(-1) >= 0);
        assertTrue(RegistryRemapper.mapBlockState393To758(99999) >= 0);
        assertTrue(RegistryRemapper.mapItem393To758(-1) >= 0);
        assertTrue(RegistryRemapper.mapEntityType393To758(99999) >= 0);
        assertTrue(RegistryRemapper.mapBiome393To758(99999) >= 0);
    }
}
