package io.detergentmc.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * {@link RegistryRemapper} の768（1.21.2／1.21.3）向け表のうち、
 * バナーパターン・絵画バリアント・装飾パターン・ダメージ種別のテスト。
 * 767→768 ではバナー・絵画・装飾の並びは変わらない（恒等写像）。
 * ダメージ種別は ender_pearl（768では 8）と mace_smash（768では 26）が
 * アルファベット順で挿入された。mace_smash は magic（768では 27）より前。
 * バニラ jar の data パックのファイル名のアルファベット順から作った表で、
 * 実サーバーの Registry Data で確認するまでは推定。
 */
class RegistryRemapper768TablesTest {

    // ---- バナーパターン（767→768 は恒等） ----

    @Test
    void bannerPattern767To768() {
        assertEquals(0, RegistryRemapper.mapBannerPattern767To768(0)); // base
        assertEquals(12, RegistryRemapper.mapBannerPattern767To768(12)); // flower
        assertEquals(42, RegistryRemapper.mapBannerPattern767To768(42)); // triangles_top
    }

    @Test
    void bannerPattern768To767() {
        assertEquals(0, RegistryRemapper.mapBannerPattern768To767(0)); // base
        assertEquals(11, RegistryRemapper.mapBannerPattern768To767(11)); // flow
        assertEquals(42, RegistryRemapper.mapBannerPattern768To767(42)); // triangles_top
    }

    @Test
    void bannerPattern766To768() {
        assertEquals(0, RegistryRemapper.mapBannerPattern766To768(0)); // base
        assertEquals(12, RegistryRemapper.mapBannerPattern766To768(11)); // flower
        assertEquals(42, RegistryRemapper.mapBannerPattern766To768(40)); // triangles_top
    }

    @Test
    void bannerPattern768To766() {
        assertEquals(0, RegistryRemapper.mapBannerPattern768To766(0)); // base
        assertEquals(-1, RegistryRemapper.mapBannerPattern768To766(11)); // flow は766に無い
        assertEquals(11, RegistryRemapper.mapBannerPattern768To766(12)); // flower
        assertEquals(-1, RegistryRemapper.mapBannerPattern768To766(16)); // guster は766に無い
        assertEquals(40, RegistryRemapper.mapBannerPattern768To766(42)); // triangles_top
    }

    @Test
    void bannerPatternRoundTrip() {
        for (int id = 0; id <= 40; id++) {
            int mapped = RegistryRemapper.mapBannerPattern766To768(id);
            assertEquals(id, RegistryRemapper.mapBannerPattern768To766(mapped));
        }
        for (int id = 0; id <= 42; id++) {
            int mapped = RegistryRemapper.mapBannerPattern767To768(id);
            assertEquals(id, RegistryRemapper.mapBannerPattern768To767(mapped));
        }
    }

    // ---- 絵画バリアント（767→768 は恒等） ----

    @Test
    void paintingVariant767To768() {
        assertEquals(0, RegistryRemapper.mapPaintingVariant767To768(0)); // alban
        assertEquals(23, RegistryRemapper.mapPaintingVariant767To768(23)); // kebab
        assertEquals(49, RegistryRemapper.mapPaintingVariant767To768(49)); // wither
    }

    @Test
    void paintingVariant766To768() {
        assertEquals(23, RegistryRemapper.mapPaintingVariant766To768(0)); // kebab
        assertEquals(1, RegistryRemapper.mapPaintingVariant766To768(1)); // aztec
        assertEquals(0, RegistryRemapper.mapPaintingVariant766To768(2)); // alban
        assertEquals(49, RegistryRemapper.mapPaintingVariant766To768(19)); // wither
        assertEquals(15, RegistryRemapper.mapPaintingVariant766To768(25)); // earth
        assertEquals(14, RegistryRemapper.mapPaintingVariant766To768(29)); // donkey_kong
    }

    @Test
    void paintingVariant768To766() {
        assertEquals(2, RegistryRemapper.mapPaintingVariant768To766(0)); // alban
        assertEquals(0, RegistryRemapper.mapPaintingVariant768To766(23)); // kebab
        assertEquals(-1, RegistryRemapper.mapPaintingVariant768To766(3)); // backyard は766に無い
        assertEquals(19, RegistryRemapper.mapPaintingVariant768To766(49)); // wither
    }

    @Test
    void paintingVariantRoundTrip() {
        for (int id = 0; id <= 29; id++) {
            int mapped = RegistryRemapper.mapPaintingVariant766To768(id);
            assertEquals(id, RegistryRemapper.mapPaintingVariant768To766(mapped));
        }
    }

    // ---- 装飾パターン（767→768 は恒等） ----

    @Test
    void trimPattern767To768() {
        assertEquals(0, RegistryRemapper.mapTrimPattern767To768(0)); // bolt
        assertEquals(1, RegistryRemapper.mapTrimPattern767To768(1)); // coast
        assertEquals(17, RegistryRemapper.mapTrimPattern767To768(17)); // wild
    }

    @Test
    void trimPattern766To768() {
        assertEquals(1, RegistryRemapper.mapTrimPattern766To768(0)); // coast
        assertEquals(3, RegistryRemapper.mapTrimPattern766To768(2)); // eye
        assertEquals(17, RegistryRemapper.mapTrimPattern766To768(15)); // wild
    }

    @Test
    void trimPattern768To766() {
        assertEquals(-1, RegistryRemapper.mapTrimPattern768To766(0)); // bolt は766に無い
        assertEquals(0, RegistryRemapper.mapTrimPattern768To766(1)); // coast
        assertEquals(-1, RegistryRemapper.mapTrimPattern768To766(4)); // flow は766に無い
        assertEquals(15, RegistryRemapper.mapTrimPattern768To766(17)); // wild
    }

    @Test
    void trimPatternRoundTrip() {
        for (int id = 0; id <= 15; id++) {
            int mapped = RegistryRemapper.mapTrimPattern766To768(id);
            assertEquals(id, RegistryRemapper.mapTrimPattern768To766(mapped));
        }
    }

    // ---- ダメージ種別 ----

    @Test
    void damageType767To768() {
        assertEquals(0, RegistryRemapper.mapDamageType767To768(0)); // arrow
        assertEquals(7, RegistryRemapper.mapDamageType767To768(7)); // dry_out
        assertEquals(9, RegistryRemapper.mapDamageType767To768(8)); // explosion
        assertEquals(27, RegistryRemapper.mapDamageType767To768(25)); // magic
        assertEquals(28, RegistryRemapper.mapDamageType767To768(26)); // mob_attack
        assertEquals(47, RegistryRemapper.mapDamageType767To768(45)); // wither
        assertEquals(48, RegistryRemapper.mapDamageType767To768(46)); // wither_skull
    }

    @Test
    void damageType768To767() {
        assertEquals(0, RegistryRemapper.mapDamageType768To767(0)); // arrow
        assertEquals(-1, RegistryRemapper.mapDamageType768To767(8)); // ender_pearl は767に無い
        assertEquals(-1, RegistryRemapper.mapDamageType768To767(26)); // mace_smash は767に無い
        assertEquals(8, RegistryRemapper.mapDamageType768To767(9)); // explosion
        assertEquals(25, RegistryRemapper.mapDamageType768To767(27)); // magic
        assertEquals(26, RegistryRemapper.mapDamageType768To767(28)); // mob_attack
        assertEquals(45, RegistryRemapper.mapDamageType768To767(47)); // wither
        assertEquals(46, RegistryRemapper.mapDamageType768To767(48)); // wither_skull
    }

    @Test
    void damageTypeRoundTrip() {
        for (int id = 0; id <= 46; id++) {
            int mapped = RegistryRemapper.mapDamageType767To768(id);
            assertEquals(id, RegistryRemapper.mapDamageType768To767(mapped));
        }
    }

    @Test
    void damageType766To768() {
        assertEquals(0, RegistryRemapper.mapDamageType766To768(0)); // arrow
        assertEquals(4, RegistryRemapper.mapDamageType766To768(3)); // cramming
        assertEquals(47, RegistryRemapper.mapDamageType766To768(43)); // wither
        assertEquals(48, RegistryRemapper.mapDamageType766To768(44)); // wither_skull
    }

    @Test
    void damageType768To766() {
        assertEquals(0, RegistryRemapper.mapDamageType768To766(0)); // arrow
        assertEquals(3, RegistryRemapper.mapDamageType768To766(4)); // cramming
        assertEquals(-1, RegistryRemapper.mapDamageType768To766(8)); // ender_pearl は766に無い
        assertEquals(-1, RegistryRemapper.mapDamageType768To766(26)); // mace_smash は766に無い
        assertEquals(43, RegistryRemapper.mapDamageType768To766(47)); // wither
        assertEquals(44, RegistryRemapper.mapDamageType768To766(48)); // wither_skull
    }
}
