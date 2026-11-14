package io.detergentmc.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

/**
 * {@link RegistryRemapper} の767（1.21／1.21.1）向け表のうち、
 * バナーパターン・絵画バリアント・装飾パターン・ダメージ種別と
 * 765の属性のテスト。バニラ jar の data パックのファイル名の
 * アルファベット順から作った表で、実サーバーの Registry Data で
 * 確認するまでは推定。
 */
class RegistryRemapper767TablesTest {

    // ---- バナーパターン ----

    @Test
    void bannerPattern766To767() {
        assertEquals(0, RegistryRemapper.mapBannerPattern766To767(0)); // base
        assertEquals(12, RegistryRemapper.mapBannerPattern766To767(11)); // flower
        assertEquals(17, RegistryRemapper.mapBannerPattern766To767(15)); // half_horizontal
        assertEquals(42, RegistryRemapper.mapBannerPattern766To767(40)); // triangles_top
    }

    @Test
    void bannerPattern767To766() {
        assertEquals(0, RegistryRemapper.mapBannerPattern767To766(0)); // base
        assertEquals(-1, RegistryRemapper.mapBannerPattern767To766(11)); // flow は766に無い
        assertEquals(11, RegistryRemapper.mapBannerPattern767To766(12)); // flower
        assertEquals(-1, RegistryRemapper.mapBannerPattern767To766(16)); // guster は766に無い
        assertEquals(40, RegistryRemapper.mapBannerPattern767To766(42)); // triangles_top
    }

    @Test
    void bannerPatternRoundTrip() {
        for (int id = 0; id <= 40; id++) {
            int mapped = RegistryRemapper.mapBannerPattern766To767(id);
            assertEquals(id, RegistryRemapper.mapBannerPattern767To766(mapped));
        }
    }

    // ---- 絵画バリアント ----

    @Test
    void paintingVariant766To767() {
        assertEquals(23, RegistryRemapper.mapPaintingVariant766To767(0)); // kebab
        assertEquals(1, RegistryRemapper.mapPaintingVariant766To767(1)); // aztec
        assertEquals(0, RegistryRemapper.mapPaintingVariant766To767(2)); // alban
        assertEquals(49, RegistryRemapper.mapPaintingVariant766To767(19)); // wither
        // 766は30件。コマンド限定の4件（earth・wind・water・fire）も変換する。
        assertEquals(15, RegistryRemapper.mapPaintingVariant766To767(25)); // earth
        assertEquals(48, RegistryRemapper.mapPaintingVariant766To767(26)); // wind
        assertEquals(47, RegistryRemapper.mapPaintingVariant766To767(27)); // water
        assertEquals(20, RegistryRemapper.mapPaintingVariant766To767(28)); // fire
        assertEquals(14, RegistryRemapper.mapPaintingVariant766To767(29)); // donkey_kong
    }

    @Test
    void paintingVariant767To766() {
        assertEquals(2, RegistryRemapper.mapPaintingVariant767To766(0)); // alban
        assertEquals(0, RegistryRemapper.mapPaintingVariant767To766(23)); // kebab
        assertEquals(27, RegistryRemapper.mapPaintingVariant767To766(47)); // water
        assertEquals(26, RegistryRemapper.mapPaintingVariant767To766(48)); // wind
        assertEquals(28, RegistryRemapper.mapPaintingVariant767To766(20)); // fire
        assertEquals(25, RegistryRemapper.mapPaintingVariant767To766(15)); // earth
        assertEquals(29, RegistryRemapper.mapPaintingVariant767To766(14)); // donkey_kong
        assertEquals(-1, RegistryRemapper.mapPaintingVariant767To766(3)); // backyard は766に無い
        assertEquals(19, RegistryRemapper.mapPaintingVariant767To766(49)); // wither
    }

    @Test
    void paintingVariantRoundTrip() {
        for (int id = 0; id <= 29; id++) {
            int mapped = RegistryRemapper.mapPaintingVariant766To767(id);
            assertEquals(id, RegistryRemapper.mapPaintingVariant767To766(mapped));
        }
    }

    // ---- 装飾パターン ----

    @Test
    void trimPattern766To767() {
        assertEquals(1, RegistryRemapper.mapTrimPattern766To767(0)); // coast
        assertEquals(3, RegistryRemapper.mapTrimPattern766To767(2)); // eye
        assertEquals(5, RegistryRemapper.mapTrimPattern766To767(3)); // host
        assertEquals(17, RegistryRemapper.mapTrimPattern766To767(15)); // wild
    }

    @Test
    void trimPattern767To766() {
        assertEquals(-1, RegistryRemapper.mapTrimPattern767To766(0)); // bolt は766に無い
        assertEquals(0, RegistryRemapper.mapTrimPattern767To766(1)); // coast
        assertEquals(-1, RegistryRemapper.mapTrimPattern767To766(4)); // flow は766に無い
        assertEquals(15, RegistryRemapper.mapTrimPattern767To766(17)); // wild
    }

    @Test
    void trimPatternRoundTrip() {
        for (int id = 0; id <= 15; id++) {
            int mapped = RegistryRemapper.mapTrimPattern766To767(id);
            assertEquals(id, RegistryRemapper.mapTrimPattern767To766(mapped));
        }
    }

    // ---- ダメージ種別 ----

    @Test
    void damageType766To767() {
        assertEquals(0, RegistryRemapper.mapDamageType766To767(0)); // arrow
        assertEquals(4, RegistryRemapper.mapDamageType766To767(3)); // cramming
        assertEquals(45, RegistryRemapper.mapDamageType766To767(43)); // wither
        assertEquals(46, RegistryRemapper.mapDamageType766To767(44)); // wither_skull
    }

    @Test
    void damageType767To766() {
        assertEquals(0, RegistryRemapper.mapDamageType767To766(0)); // arrow
        assertEquals(-1, RegistryRemapper.mapDamageType767To766(3)); // campfire は766に無い
        assertEquals(3, RegistryRemapper.mapDamageType767To766(4)); // cramming
        assertEquals(-1, RegistryRemapper.mapDamageType767To766(44)); // wind_charge は766に無い
        assertEquals(44, RegistryRemapper.mapDamageType767To766(46)); // wither_skull
    }

    @Test
    void damageTypeRoundTrip() {
        for (int id = 0; id <= 44; id++) {
            int mapped = RegistryRemapper.mapDamageType766To767(id);
            assertEquals(id, RegistryRemapper.mapDamageType767To766(mapped));
        }
    }

    // ---- 765の属性 ----

    @Test
    void attributeName765From766() {
        // 765 のワイヤ上の名前は "minecraft:generic.max_health" のような
        // 接頭辞付き（本物のバニラ1.20.4から受け取って確認済み）。
        assertEquals("minecraft:generic.armor", RegistryRemapper.attributeName765From766(0));
        assertEquals("minecraft:generic.flying_speed", RegistryRemapper.attributeName765From766(9));
        assertEquals("minecraft:horse.jump_strength", RegistryRemapper.attributeName765From766(12));
        assertEquals("minecraft:generic.movement_speed", RegistryRemapper.attributeName765From766(17));
        assertEquals("minecraft:zombie.spawn_reinforcements", RegistryRemapper.attributeName765From766(20));
        // 766で増えた8件は765に無い。
        assertNull(RegistryRemapper.attributeName765From766(5));
        assertNull(RegistryRemapper.attributeName765From766(6));
        assertNull(RegistryRemapper.attributeName765From766(7));
        assertNull(RegistryRemapper.attributeName765From766(8));
        assertNull(RegistryRemapper.attributeName765From766(11));
        assertNull(RegistryRemapper.attributeName765From766(18));
        assertNull(RegistryRemapper.attributeName765From766(19));
        assertNull(RegistryRemapper.attributeName765From766(21));
    }

    @Test
    void attributeId767From765() {
        assertEquals(21, RegistryRemapper.attributeId767From765("generic.movement_speed"));
        assertEquals(14, RegistryRemapper.attributeId767From765("horse.jump_strength"));
        assertEquals(0, RegistryRemapper.attributeId767From765("generic.armor"));
        assertEquals(RegistryRemapper.DROP, RegistryRemapper.attributeId767From765("minecraft:no_such_attribute"));
        // 765 のワイヤ上の名前は接頭辞付き。付いていても同じIDを返す。
        assertEquals(21, RegistryRemapper.attributeId767From765("minecraft:generic.movement_speed"));
        assertEquals(14, RegistryRemapper.attributeId767From765("minecraft:horse.jump_strength"));
        assertEquals(0, RegistryRemapper.attributeId767From765("minecraft:generic.armor"));
    }

    // ---- 765との共用（並びが同じ） ----

    @Test
    void bannerAndPainting765Share766Order() {
        assertEquals(12, RegistryRemapper.mapBannerPattern765To767(11));
        assertEquals(11, RegistryRemapper.mapBannerPattern767To765(12));
        assertEquals(23, RegistryRemapper.mapPaintingVariant765To767(0));
        assertEquals(0, RegistryRemapper.mapPaintingVariant767To765(23));
        assertEquals(1, RegistryRemapper.mapTrimPattern765To767(0));
        assertEquals(0, RegistryRemapper.mapTrimPattern767To765(1));
        assertEquals(4, RegistryRemapper.mapDamageType765To767(3));
        assertEquals(3, RegistryRemapper.mapDamageType767To765(4));
    }
}
