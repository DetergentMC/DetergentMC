package io.detergentmc.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * {@link RegistryRemapper} の759（1.19）⇔760（1.19.1／1.19.2）向けスポットチェックと往復テスト。
 * 期待値は ViaVersion の mapping-1.19.json と minecraft-data（pc/1.19・
 * pc/1.19.2 の blocks.json／items.json／entities.json／biomes.json）から
 * 計算したもの（推測ではない）。生成スクリプト
 * （detergentmc-protocol/tools/gen-759-tables.py）の出力と同じ値。
 * 検証は tools/gen-759-tables.py の実行結果を参照。
 *
 * <p>759と760のレジストリは全カテゴリ同一（恒等写像）：ブロック状態 21,448、
 * ブロック 933、アイテム 1,152、エンティティ 118、音 1,321、パーティクル 93、
 * バイオーム 63（plains は759=1・760=1）、エフェクト 33、エンチャント 39、
 * ブロックエンティティ 36、メニュー 24、ダメージ種別 42。唯一の既知の違いは
 * blocks.json の drops メタデータ 3 件（fern・attached_pumpkin_stem・
 * attached_melon_stem）で ID には影響しない。1.19.1 は 1.19 からの
 * レジストリ追加が無いため、mapping-1.19.json が両方のレジストリ源として
 * 有効（minecraft-data にも pc/1.19.1 は無い）。
 */
class RegistryRemapper759Test {

    // ---- 760 → 759（恒等） ----

    @Test
    void blockStatesForward() {
        assertEquals(0, RegistryRemapper.mapBlockState760To759(0)); // air
        assertEquals(1, RegistryRemapper.mapBlockState760To759(1)); // stone
        assertEquals(118, RegistryRemapper.mapBlockState760To759(118)); // oak_log
        assertEquals(21447, RegistryRemapper.mapBlockState760To759(21447)); // 末尾
        assertEquals(1, RegistryRemapper.mapBlockState760To759(99999)); // 範囲外は石
    }

    @Test
    void blockStatesReverse() {
        assertEquals(0, RegistryRemapper.mapBlockState759To760(0)); // air
        assertEquals(1, RegistryRemapper.mapBlockState759To760(1)); // stone
        assertEquals(118, RegistryRemapper.mapBlockState759To760(118)); // oak_log
        assertEquals(21447, RegistryRemapper.mapBlockState759To760(21447)); // 末尾
        assertEquals(1, RegistryRemapper.mapBlockState759To760(99999)); // 範囲外は石
    }

    @Test
    void items() {
        assertEquals(0, RegistryRemapper.mapItem760To759(0)); // air
        assertEquals(1, RegistryRemapper.mapItem760To759(1)); // stone
        assertEquals(1151, RegistryRemapper.mapItem760To759(1151)); // 末尾
        assertEquals(1, RegistryRemapper.mapItem760To759(99999)); // 範囲外は石
        assertEquals(0, RegistryRemapper.mapItem759To760(0));
        assertEquals(1, RegistryRemapper.mapItem759To760(1));
        assertEquals(1151, RegistryRemapper.mapItem759To760(1151)); // 末尾
        assertEquals(1, RegistryRemapper.mapItem759To760(99999)); // 範囲外は石
    }

    @Test
    void entities() {
        assertEquals(67, RegistryRemapper.mapEntityType760To759(67)); // pig
        assertEquals(40, RegistryRemapper.mapEntityType760To759(40)); // horse
        assertEquals(67, RegistryRemapper.mapEntityType759To760(67)); // pig
        assertEquals(40, RegistryRemapper.mapEntityType759To760(40)); // horse
        assertEquals(67, RegistryRemapper.mapEntityType760To759(99999)); // 範囲外はブタ
        assertEquals(67, RegistryRemapper.mapEntityType759To760(99999)); // 範囲外はブタ
    }

    @Test
    void sounds() {
        assertEquals(1278, RegistryRemapper.mapSound760To759(1278)); // block.wood.break
        assertEquals(1320, RegistryRemapper.mapSound760To759(1320)); // 末尾
        assertEquals(1278, RegistryRemapper.mapSound759To760(1278));
        assertEquals(1320, RegistryRemapper.mapSound759To760(1320)); // 末尾
        assertEquals("block.wood.break",
                RegistryRemapper.mapSoundName760To759("block.wood.break"));
        assertEquals("block.wood.break",
                RegistryRemapper.mapSoundName759To760("block.wood.break"));
    }

    @Test
    void particlesAndMenusAreIdentity() {
        assertEquals(30, RegistryRemapper.mapParticle760To759(30)); // sculk_charge
        assertEquals(39, RegistryRemapper.mapParticle760To759(39)); // item
        assertEquals(92, RegistryRemapper.mapParticle760To759(92)); // shriek
        assertEquals(30, RegistryRemapper.mapParticle759To760(30));
        assertEquals(11, RegistryRemapper.mapMenu760To759(11)); // crafting
        assertEquals(11, RegistryRemapper.mapMenu759To760(11));
    }

    @Test
    void blockEntities() {
        assertEquals(0, RegistryRemapper.mapBlockEntityType760To759(0)); // furnace
        assertEquals(35, RegistryRemapper.mapBlockEntityType760To759(35)); // 末尾
        assertEquals(0, RegistryRemapper.mapBlockEntityType759To760(0)); // furnace
        assertEquals(35, RegistryRemapper.mapBlockEntityType759To760(35)); // 末尾
    }

    @Test
    void biomes() {
        // plains は 759=1・760=1（並びも同一）。
        assertEquals(1, RegistryRemapper.mapBiome760To759(1)); // plains
        assertEquals(38, RegistryRemapper.mapBiome760To759(38));
        assertEquals(1, RegistryRemapper.mapBiome759To760(1)); // plains
        assertEquals(38, RegistryRemapper.mapBiome759To760(38));
    }

    @Test
    void damageTypesAreIdentity() {
        assertEquals(0, RegistryRemapper.mapDamageType760To759(0));
        assertEquals(41, RegistryRemapper.mapDamageType760To759(41));
        assertEquals(0, RegistryRemapper.mapDamageType759To760(0));
        assertEquals(41, RegistryRemapper.mapDamageType759To760(41));
    }

    @Test
    void blocks() {
        assertEquals(1, RegistryRemapper.mapBlock760To759(1)); // stone
        assertEquals(1, RegistryRemapper.mapBlock759To760(1)); // stone
    }

    @Test
    void roundTripIsIdentityBothWays() {
        // 759と760のレジストリは同一のため、往復はすべて自分に戻る。
        assertEquals(118, RegistryRemapper.mapBlockState759To760(
                RegistryRemapper.mapBlockState760To759(118)));
        assertEquals(118, RegistryRemapper.mapBlockState760To759(
                RegistryRemapper.mapBlockState759To760(118)));
        assertEquals(937, RegistryRemapper.mapItem759To760(
                RegistryRemapper.mapItem760To759(937)));
        assertEquals(937, RegistryRemapper.mapItem760To759(
                RegistryRemapper.mapItem759To760(937)));
        assertEquals(67, RegistryRemapper.mapEntityType759To760(
                RegistryRemapper.mapEntityType760To759(67)));
        assertEquals(1, RegistryRemapper.mapBiome759To760(
                RegistryRemapper.mapBiome760To759(1)));
        assertEquals(1278, RegistryRemapper.mapSound759To760(
                RegistryRemapper.mapSound760To759(1278)));
    }
}
