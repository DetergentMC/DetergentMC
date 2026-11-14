package io.detergentmc.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * {@link RegistryRemapper} の769（1.21.4）向け表のうち、
 * バナーパターン・絵画バリアント・装飾パターン・ダメージ種別のテスト。
 * 768→769 ではバナー・絵画・装飾・ダメージ種別の並びは変わらない（恒等写像）。
 * 766／767→769 は 768 を経由した合成（768 と同じ値）。
 * ダメージ種別は 49件同士で並び同一（ender_pearl・mace_smash は768・769ともある）。
 */
class RegistryRemapper769TablesTest {

    // ---- バナーパターン（768→769 は恒等） ----

    @Test
    void bannerPattern768To769() {
        assertEquals(0, RegistryRemapper.mapBannerPattern768To769(0)); // base
        assertEquals(12, RegistryRemapper.mapBannerPattern768To769(12)); // flower
        assertEquals(42, RegistryRemapper.mapBannerPattern768To769(42)); // triangles_top
    }

    @Test
    void bannerPattern769To768() {
        assertEquals(0, RegistryRemapper.mapBannerPattern769To768(0)); // base
        assertEquals(11, RegistryRemapper.mapBannerPattern769To768(11)); // flow
        assertEquals(42, RegistryRemapper.mapBannerPattern769To768(42)); // triangles_top
    }

    @Test
    void bannerPattern767To769() {
        assertEquals(0, RegistryRemapper.mapBannerPattern767To769(0)); // base
        assertEquals(12, RegistryRemapper.mapBannerPattern767To769(12)); // flower
        assertEquals(42, RegistryRemapper.mapBannerPattern767To769(42)); // triangles_top
    }

    @Test
    void bannerPattern769To767() {
        assertEquals(0, RegistryRemapper.mapBannerPattern769To767(0)); // base
        assertEquals(11, RegistryRemapper.mapBannerPattern769To767(11)); // flow
        assertEquals(42, RegistryRemapper.mapBannerPattern769To767(42)); // triangles_top
    }

    @Test
    void bannerPattern766To769() {
        assertEquals(0, RegistryRemapper.mapBannerPattern766To769(0)); // base
        assertEquals(12, RegistryRemapper.mapBannerPattern766To769(11)); // flower
        assertEquals(42, RegistryRemapper.mapBannerPattern766To769(40)); // triangles_top
    }

    @Test
    void bannerPattern769To766() {
        assertEquals(0, RegistryRemapper.mapBannerPattern769To766(0)); // base
        assertEquals(-1, RegistryRemapper.mapBannerPattern769To766(11)); // flow は766に無い
        assertEquals(11, RegistryRemapper.mapBannerPattern769To766(12)); // flower
        assertEquals(-1, RegistryRemapper.mapBannerPattern769To766(16)); // guster は766に無い
        assertEquals(40, RegistryRemapper.mapBannerPattern769To766(42)); // triangles_top
    }

    @Test
    void bannerPatternRoundTrip() {
        for (int id = 0; id <= 40; id++) {
            int mapped = RegistryRemapper.mapBannerPattern766To769(id);
            assertEquals(id, RegistryRemapper.mapBannerPattern769To766(mapped));
        }
        for (int id = 0; id <= 42; id++) {
            int mapped = RegistryRemapper.mapBannerPattern767To769(id);
            assertEquals(id, RegistryRemapper.mapBannerPattern769To767(mapped));
        }
        for (int id = 0; id <= 42; id++) {
            int mapped = RegistryRemapper.mapBannerPattern768To769(id);
            assertEquals(id, RegistryRemapper.mapBannerPattern769To768(mapped));
        }
    }

    // ---- 絵画バリアント（768→769 は恒等） ----

    @Test
    void paintingVariant768To769() {
        assertEquals(0, RegistryRemapper.mapPaintingVariant768To769(0)); // alban
        assertEquals(23, RegistryRemapper.mapPaintingVariant768To769(23)); // kebab
        assertEquals(49, RegistryRemapper.mapPaintingVariant768To769(49)); // wither
    }

    @Test
    void paintingVariant769To768() {
        assertEquals(0, RegistryRemapper.mapPaintingVariant769To768(0)); // alban
        assertEquals(23, RegistryRemapper.mapPaintingVariant769To768(23)); // kebab
        assertEquals(49, RegistryRemapper.mapPaintingVariant769To768(49)); // wither
    }

    @Test
    void paintingVariant767To769() {
        assertEquals(0, RegistryRemapper.mapPaintingVariant767To769(0)); // alban
        assertEquals(23, RegistryRemapper.mapPaintingVariant767To769(23)); // kebab
        assertEquals(49, RegistryRemapper.mapPaintingVariant767To769(49)); // wither
    }

    @Test
    void paintingVariant766To769() {
        assertEquals(23, RegistryRemapper.mapPaintingVariant766To769(0)); // kebab
        assertEquals(1, RegistryRemapper.mapPaintingVariant766To769(1)); // aztec
        assertEquals(0, RegistryRemapper.mapPaintingVariant766To769(2)); // alban
        assertEquals(49, RegistryRemapper.mapPaintingVariant766To769(19)); // wither
        assertEquals(15, RegistryRemapper.mapPaintingVariant766To769(25)); // earth
        assertEquals(14, RegistryRemapper.mapPaintingVariant766To769(29)); // donkey_kong
    }

    @Test
    void paintingVariant769To766() {
        assertEquals(2, RegistryRemapper.mapPaintingVariant769To766(0)); // alban
        assertEquals(0, RegistryRemapper.mapPaintingVariant769To766(23)); // kebab
        assertEquals(-1, RegistryRemapper.mapPaintingVariant769To766(3)); // backyard は766に無い
        assertEquals(19, RegistryRemapper.mapPaintingVariant769To766(49)); // wither
    }

    @Test
    void paintingVariantRoundTrip() {
        for (int id = 0; id <= 29; id++) {
            int mapped = RegistryRemapper.mapPaintingVariant766To769(id);
            assertEquals(id, RegistryRemapper.mapPaintingVariant769To766(mapped));
        }
    }

    // ---- 装飾パターン（768→769 は恒等） ----

    @Test
    void trimPattern768To769() {
        assertEquals(0, RegistryRemapper.mapTrimPattern768To769(0)); // bolt
        assertEquals(1, RegistryRemapper.mapTrimPattern768To769(1)); // coast
        assertEquals(17, RegistryRemapper.mapTrimPattern768To769(17)); // wild
    }

    @Test
    void trimPattern769To768() {
        assertEquals(0, RegistryRemapper.mapTrimPattern769To768(0)); // bolt
        assertEquals(1, RegistryRemapper.mapTrimPattern769To768(1)); // coast
        assertEquals(17, RegistryRemapper.mapTrimPattern769To768(17)); // wild
    }

    @Test
    void trimPattern767To769() {
        assertEquals(0, RegistryRemapper.mapTrimPattern767To769(0)); // bolt
        assertEquals(1, RegistryRemapper.mapTrimPattern767To769(1)); // coast
        assertEquals(17, RegistryRemapper.mapTrimPattern767To769(17)); // wild
    }

    @Test
    void trimPattern766To769() {
        assertEquals(1, RegistryRemapper.mapTrimPattern766To769(0)); // coast
        assertEquals(3, RegistryRemapper.mapTrimPattern766To769(2)); // eye
        assertEquals(17, RegistryRemapper.mapTrimPattern766To769(15)); // wild
    }

    @Test
    void trimPattern769To766() {
        assertEquals(-1, RegistryRemapper.mapTrimPattern769To766(0)); // bolt は766に無い
        assertEquals(0, RegistryRemapper.mapTrimPattern769To766(1)); // coast
        assertEquals(-1, RegistryRemapper.mapTrimPattern769To766(4)); // flow は766に無い
        assertEquals(15, RegistryRemapper.mapTrimPattern769To766(17)); // wild
    }

    @Test
    void trimPatternRoundTrip() {
        for (int id = 0; id <= 15; id++) {
            int mapped = RegistryRemapper.mapTrimPattern766To769(id);
            assertEquals(id, RegistryRemapper.mapTrimPattern769To766(mapped));
        }
    }

    // ---- ダメージ種別（768→769 は恒等） ----

    @Test
    void damageType768To769() {
        assertEquals(0, RegistryRemapper.mapDamageType768To769(0)); // arrow
        assertEquals(8, RegistryRemapper.mapDamageType768To769(8)); // ender_pearl
        assertEquals(26, RegistryRemapper.mapDamageType768To769(26)); // mace_smash
        assertEquals(27, RegistryRemapper.mapDamageType768To769(27)); // magic
        assertEquals(47, RegistryRemapper.mapDamageType768To769(47)); // wither
        assertEquals(48, RegistryRemapper.mapDamageType768To769(48)); // wither_skull
    }

    @Test
    void damageType769To768() {
        assertEquals(0, RegistryRemapper.mapDamageType769To768(0)); // arrow
        assertEquals(8, RegistryRemapper.mapDamageType769To768(8)); // ender_pearl
        assertEquals(26, RegistryRemapper.mapDamageType769To768(26)); // mace_smash
        assertEquals(27, RegistryRemapper.mapDamageType769To768(27)); // magic
        assertEquals(47, RegistryRemapper.mapDamageType769To768(47)); // wither
        assertEquals(48, RegistryRemapper.mapDamageType769To768(48)); // wither_skull
    }

    @Test
    void damageTypeRoundTrip() {
        for (int id = 0; id <= 48; id++) {
            int mapped = RegistryRemapper.mapDamageType768To769(id);
            assertEquals(id, RegistryRemapper.mapDamageType769To768(mapped));
        }
    }

    @Test
    void damageType767To769() {
        assertEquals(0, RegistryRemapper.mapDamageType767To769(0)); // arrow
        assertEquals(7, RegistryRemapper.mapDamageType767To769(7)); // dry_out
        assertEquals(9, RegistryRemapper.mapDamageType767To769(8)); // explosion
        assertEquals(27, RegistryRemapper.mapDamageType767To769(25)); // magic
        assertEquals(28, RegistryRemapper.mapDamageType767To769(26)); // mob_attack
        assertEquals(47, RegistryRemapper.mapDamageType767To769(45)); // wither
        assertEquals(48, RegistryRemapper.mapDamageType767To769(46)); // wither_skull
    }

    @Test
    void damageType769To767() {
        assertEquals(0, RegistryRemapper.mapDamageType769To767(0)); // arrow
        assertEquals(-1, RegistryRemapper.mapDamageType769To767(8)); // ender_pearl は767に無い
        assertEquals(-1, RegistryRemapper.mapDamageType769To767(26)); // mace_smash は767に無い
        assertEquals(8, RegistryRemapper.mapDamageType769To767(9)); // explosion
        assertEquals(25, RegistryRemapper.mapDamageType769To767(27)); // magic
        assertEquals(26, RegistryRemapper.mapDamageType769To767(28)); // mob_attack
        assertEquals(45, RegistryRemapper.mapDamageType769To767(47)); // wither
        assertEquals(46, RegistryRemapper.mapDamageType769To767(48)); // wither_skull
    }

    @Test
    void damageType766To769() {
        assertEquals(0, RegistryRemapper.mapDamageType766To769(0)); // arrow
        assertEquals(4, RegistryRemapper.mapDamageType766To769(3)); // cramming
        assertEquals(47, RegistryRemapper.mapDamageType766To769(43)); // wither
        assertEquals(48, RegistryRemapper.mapDamageType766To769(44)); // wither_skull
    }

    @Test
    void damageType769To766() {
        assertEquals(0, RegistryRemapper.mapDamageType769To766(0)); // arrow
        assertEquals(3, RegistryRemapper.mapDamageType769To766(4)); // cramming
        assertEquals(-1, RegistryRemapper.mapDamageType769To766(8)); // ender_pearl は766に無い
        assertEquals(-1, RegistryRemapper.mapDamageType769To766(26)); // mace_smash は766に無い
        assertEquals(43, RegistryRemapper.mapDamageType769To766(47)); // wither
        assertEquals(44, RegistryRemapper.mapDamageType769To766(48)); // wither_skull
    }
}
