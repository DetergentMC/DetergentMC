package io.detergentmc.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * {@link RegistryRemapper} の758（1.18.2）⇔759（1.19）向けスポットチェックと往復テスト。
 * 期待値は ViaVersion の mapping-1.18.json と mapping-1.19.json から
 * 名前で突き合わせたもの（推測ではない）。生成スクリプト
 * （detergentmc-protocol/tools/gen-758-tables.py）の出力と同じ値。
 * 順序が本物のバニラ 1.18.2 と一致することは jar の data-generator ダンプ
 * （reports/registries.json・reports/blocks.json）で全カテゴリ確認済み。
 *
 * <p>759→758 の差分：ブロック状態 21,448→20,342、ブロック 933→898、
 * アイテム 1,152→1,101、エンティティ 118→113、音 1,321→1,203、
 * パーティクル 93→88、バイオーム 63→61（plains は759=1・758=1）、
 * エフェクト 33→32（Darkness は758に無い）、エンチャント 39→38
 * （swift_sneak は758に無い）、ブロックエンティティ 36→34、メニュー 24、
 * ダメージ種別 42、絵画 30→26。
 * 1.19 の新要素の代替は RegistryTables758 の冒頭コメントの通り
 * （アレイ→ヴェックス、カエル→ウサギ、オタマジャクシ→ウーパールーパー、
 * ウォーデン→アイアンゴーレム、泥→土、マングローブ系→オーク系など）。
 */
class RegistryRemapper758Test {

    // ---- 759 → 758 ----

    @Test
    void blockStatesForward() {
        assertEquals(0, RegistryRemapper.mapBlockState759To758(0)); // air
        assertEquals(1, RegistryRemapper.mapBlockState759To758(1)); // stone
        assertEquals(77, RegistryRemapper.mapBlockState759To758(118)); // oak_log[axis=y]
        assertEquals(10, RegistryRemapper.mapBlockState759To758(19777)); // mud → dirt
        assertEquals(18684, RegistryRemapper.mapBlockState759To758(21447)); // 末尾
        assertEquals(1, RegistryRemapper.mapBlockState759To758(99999)); // 範囲外は石
    }

    @Test
    void blockStatesReverse() {
        assertEquals(0, RegistryRemapper.mapBlockState758To759(0)); // air
        assertEquals(1, RegistryRemapper.mapBlockState758To759(1)); // stone
        assertEquals(118, RegistryRemapper.mapBlockState758To759(77)); // oak_log[axis=y]
        assertEquals(21436, RegistryRemapper.mapBlockState758To759(20341)); // 末尾
        assertEquals(1, RegistryRemapper.mapBlockState758To759(99999)); // 範囲外は石
    }

    @Test
    void items() {
        assertEquals(0, RegistryRemapper.mapItem759To758(0)); // air
        assertEquals(1, RegistryRemapper.mapItem759To758(1)); // stone
        assertEquals(913, RegistryRemapper.mapItem759To758(974)); // warden_spawn_egg → ravager_spawn_egg
        assertEquals(101, RegistryRemapper.mapItem759To758(110)); // mangrove_log → oak_log
        assertEquals(690, RegistryRemapper.mapItem759To758(1151)); // 末尾
        assertEquals(1, RegistryRemapper.mapItem759To758(99999)); // 範囲外は石
        assertEquals(0, RegistryRemapper.mapItem758To759(0));
        assertEquals(1, RegistryRemapper.mapItem758To759(1));
        assertEquals(954, RegistryRemapper.mapItem758To759(913)); // ravager_spawn_egg
        assertEquals(1146, RegistryRemapper.mapItem758To759(1100)); // 末尾
        assertEquals(1, RegistryRemapper.mapItem758To759(99999)); // 範囲外は石
    }

    @Test
    void entities() {
        assertEquals(64, RegistryRemapper.mapEntityType759To758(67)); // pig
        assertEquals(37, RegistryRemapper.mapEntityType759To758(40)); // horse
        assertEquals(40, RegistryRemapper.mapEntityType759To758(105)); // warden → iron_golem
        assertEquals(97, RegistryRemapper.mapEntityType759To758(0)); // allay → vex
        assertEquals(71, RegistryRemapper.mapEntityType759To758(32)); // frog → rabbit
        assertEquals(3, RegistryRemapper.mapEntityType759To758(92)); // tadpole → axolotl
        assertEquals(7, RegistryRemapper.mapEntityType759To758(9)); // chest_boat → boat
        assertEquals(112, RegistryRemapper.mapEntityType759To758(117)); // 末尾
        assertEquals(64, RegistryRemapper.mapEntityType759To758(99999)); // 範囲外はブタ
        assertEquals(67, RegistryRemapper.mapEntityType758To759(64)); // pig
        assertEquals(40, RegistryRemapper.mapEntityType758To759(37)); // horse
        assertEquals(43, RegistryRemapper.mapEntityType758To759(40)); // iron_golem
        assertEquals(117, RegistryRemapper.mapEntityType758To759(112)); // 末尾
        assertEquals(67, RegistryRemapper.mapEntityType758To759(99999)); // 範囲外はブタ
    }

    @Test
    void sounds() {
        assertEquals(1018, RegistryRemapper.mapSound759To758(1112)); // block.stone.break
        assertEquals(1160, RegistryRemapper.mapSound759To758(1278)); // block.wood.break
        assertEquals(1202, RegistryRemapper.mapSound759To758(1320)); // 末尾
        assertEquals(1018, RegistryRemapper.mapSound759To758(99999)); // 範囲外は石の破壊音
        assertEquals(1112, RegistryRemapper.mapSound758To759(1018)); // block.stone.break
        assertEquals(1320, RegistryRemapper.mapSound758To759(1202)); // 末尾
        assertEquals(1112, RegistryRemapper.mapSound758To759(99999)); // 範囲外は石の破壊音
        assertEquals("block.stone.break",
                RegistryRemapper.mapSoundName759To758("block.stone.break"));
        assertEquals("block.glass.break",
                RegistryRemapper.mapSoundName759To758("block.froglight.break"));
        assertEquals("block.stone.break",
                RegistryRemapper.mapSoundName758To759("block.stone.break"));
    }

    @Test
    void particlesAndMenus() {
        assertEquals(29, RegistryRemapper.mapParticle759To758(30)); // sculk_charge
        assertEquals(36, RegistryRemapper.mapParticle759To758(92)); // shriek
        assertEquals(0, RegistryRemapper.mapParticle759To758(99999)); // 範囲外は ambient
        assertEquals(91, RegistryRemapper.mapParticle758To759(87)); // 末尾
        assertEquals(11, RegistryRemapper.mapMenu759To758(11)); // crafting
        assertEquals(11, RegistryRemapper.mapMenu758To759(11));
    }

    @Test
    void blockEntities() {
        assertEquals(0, RegistryRemapper.mapBlockEntityType759To758(0)); // furnace
        assertEquals(33, RegistryRemapper.mapBlockEntityType759To758(34)); // sculk_catalyst → sculk_sensor
        assertEquals(33, RegistryRemapper.mapBlockEntityType759To758(35)); // sculk_shrieker → sculk_sensor
        assertEquals(RegistryRemapper.DROP, RegistryRemapper.mapBlockEntityType759To758(99999));
        assertEquals(0, RegistryRemapper.mapBlockEntityType758To759(0)); // furnace
        assertEquals(33, RegistryRemapper.mapBlockEntityType758To759(33)); // sculk_sensor
        assertEquals(RegistryRemapper.DROP, RegistryRemapper.mapBlockEntityType758To759(99999));
    }

    @Test
    void biomes() {
        // plains は 759=1・758=1。
        assertEquals(1, RegistryRemapper.mapBiome759To758(1)); // plains
        assertEquals(6, RegistryRemapper.mapBiome759To758(7)); // mangrove_swamp → swamp
        assertEquals(49, RegistryRemapper.mapBiome759To758(52)); // deep_dark → dripstone_caves
        assertEquals(1, RegistryRemapper.mapBiome759To758(99999)); // 範囲外は平原
        assertEquals(1, RegistryRemapper.mapBiome758To759(1)); // plains
        assertEquals(6, RegistryRemapper.mapBiome758To759(6)); // swamp
        assertEquals(50, RegistryRemapper.mapBiome758To759(49)); // dripstone_caves
        assertEquals(1, RegistryRemapper.mapBiome758To759(99999)); // 範囲外は平原
    }

    @Test
    void damageTypesAreIdentity() {
        assertEquals(0, RegistryRemapper.mapDamageType759To758(0));
        assertEquals(41, RegistryRemapper.mapDamageType759To758(41));
        assertEquals(RegistryRemapper.DROP, RegistryRemapper.mapDamageType759To758(99999));
        assertEquals(0, RegistryRemapper.mapDamageType758To759(0));
        assertEquals(41, RegistryRemapper.mapDamageType758To759(41));
        assertEquals(RegistryRemapper.DROP, RegistryRemapper.mapDamageType758To759(99999));
    }

    @Test
    void blocks() {
        assertEquals(1, RegistryRemapper.mapBlock759To758(1)); // stone
        assertEquals(9, RegistryRemapper.mapBlock759To758(900)); // mud → dirt
        assertEquals(38, RegistryRemapper.mapBlock759To758(46)); // mangrove_log → oak_log
        assertEquals(871, RegistryRemapper.mapBlock759To758(932)); // 末尾
        assertEquals(1, RegistryRemapper.mapBlock759To758(99999)); // 範囲外は石
        assertEquals(1, RegistryRemapper.mapBlock758To759(1)); // stone
        assertEquals(927, RegistryRemapper.mapBlock758To759(897)); // 末尾
        assertEquals(1, RegistryRemapper.mapBlock758To759(99999)); // 範囲外は石
    }

    @Test
    void composedDispatchReaches758() {
        // 765→758 の合成分岐（765→763→762→761→760→759→758）が通ること。
        // 石は全段で同じ番号のまま。作業台は 765 では 12（1.20.4 の並び）。
        assertEquals(1, RegistryRemapper.mapBlockState(1, 758));
        assertEquals(1, RegistryRemapper.mapItem(1, 758));
        assertEquals(11, RegistryRemapper.mapMenu(12, 758));
    }

    @Test
    void roundTripIsIdentityBothWays() {
        // 両方にあるものは往復で自分に戻る。
        assertEquals(118, RegistryRemapper.mapBlockState758To759(
                RegistryRemapper.mapBlockState759To758(118)));
        assertEquals(77, RegistryRemapper.mapBlockState759To758(
                RegistryRemapper.mapBlockState758To759(77)));
        assertEquals(67, RegistryRemapper.mapEntityType758To759(
                RegistryRemapper.mapEntityType759To758(67)));
        assertEquals(1, RegistryRemapper.mapBiome758To759(
                RegistryRemapper.mapBiome759To758(1)));
        assertEquals(1112, RegistryRemapper.mapSound758To759(
                RegistryRemapper.mapSound759To758(1112)));
        // 759にしか無いものは往復で代替先に変わる（warden_spawn_egg →
        // ravager_spawn_egg）。
        assertEquals(954, RegistryRemapper.mapItem758To759(
                RegistryRemapper.mapItem759To758(974)));
    }
}
