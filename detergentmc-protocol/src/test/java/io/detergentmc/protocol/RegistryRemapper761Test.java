package io.detergentmc.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * {@link RegistryRemapper} の761（1.19.3）⇔762（1.19.4）向けスポットチェックと往復テスト。
 * 期待値は ViaVersion の mapping-1.19.3.json／mapping-1.19.4.json と
 * minecraft-data（biomes.json）から計算したもの（推測ではない）。
 * 生成スクリプト（detergentmc-protocol/tools/gen-761-tables.py）の
 * tables-761.json と同じ値。検証は tools/verify-761-tables.py を参照。
 *
 * <p>761→762 の差分：ブロック状態 23,232→23,725（+493。サクラ系・
 * decorated_pot・torchflower・pink_petals・suspicious_sand）、
 * ブロック 972→998（+26）、アイテム 1,186→1,228（+42）、
 * エンティティ 119→124（+5。block_display・interaction・item_display・
 * sniffer・text_display。並びは762がアルファベット順、761がレジストリ順
 * のため同名でも番号が違う。ブタは761=68・762=72）、
 * 音 1,392→1,454（+62。共通の並びも同じではない）、
 * パーティクル 93→96（サクラの葉3種）、エンチャント 39件同士で
 * 並び同一、エフェクト 33件同士で並び同一（1ベース）、
 * ブロックエンティティ 38→40（suspicious_sand・decorated_pot の追加）、
 * メニュー 24→25（legacy_smithing の追加。761 の smithing は旧2スロット
 * で 762 の legacy_smithing に写る）、バイオーム 63件同士で同一、
 * ダメージ種別は 761 の codec に存在しない（本物の1.19.3の Join Game codec は
 * chat_type・dimension_type・worldgen/biome の3つだけ。damage_type は
 * 1.19.4 で追加。対応表は恒等写像として残すがワイヤ上の呼び出し元は無い）、
 * 絵画 30件同士で並び同一。
 *
 * <p>既知の注意点：バイオームの代替先は plains（761=38、762=38）。
 * minecraft-data の biomes.json はアルファベット順の登録で、plains は 1 ではない。
 */
class RegistryRemapper761Test {

    // ---- 762 → 761 ----

    @Test
    void blockStatesForward() {
        assertEquals(0, RegistryRemapper.mapBlockState762To761(0)); // air
        assertEquals(1, RegistryRemapper.mapBlockState762To761(1)); // stone
        // 762にしかないブロック状態は近い既存ブロックの既定状態に代替
        // （gen-761-tables.py の BLOCK_FALLBACK_762_TO_761）。
        assertEquals(241, RegistryRemapper.mapBlockState762To761(376)); // cherry_leaves→oak_leaves
        assertEquals(8337, RegistryRemapper.mapBlockState762To761(23717)); // decorated_pot→flower_pot
        assertEquals(4226, RegistryRemapper.mapBlockState762To761(12352)); // torchflower_crop→wheat
        assertEquals(109, RegistryRemapper.mapBlockState762To761(116)); // suspicious_sand→sand
        assertEquals(10277, RegistryRemapper.mapBlockState762To761(21973)); // pink_petals→peony
        assertEquals(8337, RegistryRemapper.mapBlockState762To761(23724)); // 末尾（decorated_pot）
        assertEquals(1, RegistryRemapper.mapBlockState762To761(99999)); // 範囲外は石
    }

    @Test
    void blockStatesReverse() {
        assertEquals(0, RegistryRemapper.mapBlockState761To762(0)); // air
        assertEquals(1, RegistryRemapper.mapBlockState761To762(1)); // stone
        // 761 の状態はすべて 762 に同文字列で存在する（恒等）。
        assertEquals(236, RegistryRemapper.mapBlockState761To762(217)); // oak_leaves
        assertEquals(23716, RegistryRemapper.mapBlockState761To762(23231)); // 末尾
        assertEquals(1, RegistryRemapper.mapBlockState761To762(99999)); // 範囲外は石
    }

    @Test
    void itemsForward() {
        assertEquals(0, RegistryRemapper.mapItem762To761(0)); // air
        assertEquals(1, RegistryRemapper.mapItem762To761(1)); // stone
        // 762にしかないアイテムは近い既存アイテムに代替
        // （gen-761-tables.py の ITEM_FALLBACK_762_TO_761）。
        assertEquals(23, RegistryRemapper.mapItem762To761(28)); // cherry_planks→oak_planks
        assertEquals(1001, RegistryRemapper.mapItem762To761(1016)); // sniffer_spawn_egg→turtle
        assertEquals(782, RegistryRemapper.mapItem762To761(1211)); // brush→stick
        assertEquals(750, RegistryRemapper.mapItem762To761(1212)); // netherite template→ingot
        assertEquals(745, RegistryRemapper.mapItem762To761(1213)); // sentry trim→iron_ingot
        assertEquals(854, RegistryRemapper.mapItem762To761(1224)); // pottery_shard_archer→brick
        assertEquals(788, RegistryRemapper.mapItem762To761(1102)); // torchflower_seeds→wheat_seeds
        assertEquals(713, RegistryRemapper.mapItem762To761(742)); // cherry_boat→oak_boat
        assertEquals(821, RegistryRemapper.mapItem762To761(847)); // cherry_sign→oak_sign
        assertEquals(854, RegistryRemapper.mapItem762To761(1227)); // 末尾（陶片→brick）
        assertEquals(1, RegistryRemapper.mapItem762To761(99999)); // 範囲外は石
    }

    @Test
    void itemsReverse() {
        assertEquals(0, RegistryRemapper.mapItem761To762(0)); // air
        assertEquals(1, RegistryRemapper.mapItem761To762(1)); // stone
        assertEquals(23, RegistryRemapper.mapItem761To762(23)); // oak_planks
        assertEquals(1210, RegistryRemapper.mapItem761To762(1185)); // 末尾
        assertEquals(1, RegistryRemapper.mapItem761To762(99999)); // 範囲外は石
    }

    @Test
    void entitiesSoundsParticlesBlocksMenusForward() {
        // エンティティは並びが違うため同名でも番号が変わる。
        // 762にしかない5件はブタに代替。
        assertEquals(0, RegistryRemapper.mapEntityType762To761(0)); // allay
        assertEquals(68, RegistryRemapper.mapEntityType762To761(72)); // pig
        assertEquals(68, RegistryRemapper.mapEntityType762To761(90)); // sniffer→pig
        assertEquals(68, RegistryRemapper.mapEntityType762To761(8)); // block_display→pig
        assertEquals(118, RegistryRemapper.mapEntityType762To761(123)); // fishing_bobber
        assertEquals(68, RegistryRemapper.mapEntityType762To761(99999)); // 範囲外はブタ
        // 音は挿入でずれる（block.stone.break 1245→1183）。
        assertEquals(0, RegistryRemapper.mapSound762To761(0));
        assertEquals(1183, RegistryRemapper.mapSound762To761(1245)); // block.stone.break
        assertEquals(1028, RegistryRemapper.mapSound762To761(166)); // brush→sand.break
        assertEquals(1028, RegistryRemapper.mapSound762To761(167)); // brush completed
        assertEquals(756, RegistryRemapper.mapSound762To761(807)); // cherry_grove→jungle_and_forest
        assertEquals(1349, RegistryRemapper.mapSound762To761(233)); // cherry_wood→wood
        assertEquals(916, RegistryRemapper.mapSound762To761(1212)); // sniffer.idle→pig.ambient
        assertEquals(482, RegistryRemapper.mapSound762To761(330)); // decorated_pot.shatter→glass
        assertEquals(1183, RegistryRemapper.mapSound762To761(837)); // intentionally_empty→stone
        assertEquals(733, RegistryRemapper.mapSound762To761(781)); // music_disc.cat
        assertEquals(1391, RegistryRemapper.mapSound762To761(1453)); // 末尾
        assertEquals(1183, RegistryRemapper.mapSound762To761(99999)); // 範囲外は石の破壊音
        // パーティクルはサクラの葉3種が white_ash へ。
        assertEquals(0, RegistryRemapper.mapParticle762To761(0)); // ambient_entity_effect
        assertEquals(79, RegistryRemapper.mapParticle762To761(29)); // dripping
        assertEquals(79, RegistryRemapper.mapParticle762To761(30)); // falling
        assertEquals(79, RegistryRemapper.mapParticle762To761(31)); // landing
        assertEquals(30, RegistryRemapper.mapParticle762To761(33)); // sculk_charge
        assertEquals(92, RegistryRemapper.mapParticle762To761(95)); // shriek
        assertEquals(0, RegistryRemapper.mapParticle762To761(99999)); // 範囲外は代替
        // ブロックは新規26件が近い既存ブロックに代替。
        assertEquals(1, RegistryRemapper.mapBlock762To761(1)); // stone
        assertEquals(13, RegistryRemapper.mapBlock762To761(18)); // cherry_planks→oak_planks
        assertEquals(32, RegistryRemapper.mapBlock762To761(35)); // suspicious_sand→sand
        assertEquals(173, RegistryRemapper.mapBlock762To761(597)); // torchflower_crop→wheat
        assertEquals(339, RegistryRemapper.mapBlock762To761(997)); // decorated_pot→flower_pot（末尾）
        assertEquals(1, RegistryRemapper.mapBlock762To761(99999)); // 範囲外は石
        // メニューは legacy_smithing の分だけずれる。
        assertEquals(11, RegistryRemapper.mapMenu762To761(11)); // crafting
        assertEquals(20, RegistryRemapper.mapMenu762To761(20)); // legacy_smithing→smithing
        assertEquals(20, RegistryRemapper.mapMenu762To761(21)); // smithing→smithing
        assertEquals(21, RegistryRemapper.mapMenu762To761(22)); // smoker
        assertEquals(22, RegistryRemapper.mapMenu762To761(23)); // cartography_table
        assertEquals(23, RegistryRemapper.mapMenu762To761(24)); // stonecutter
        assertEquals(11, RegistryRemapper.mapMenu762To761(99999)); // 範囲外は作業台
    }

    @Test
    void entitiesSoundsParticlesBlocksMenusReverse() {
        assertEquals(72, RegistryRemapper.mapEntityType761To762(68)); // pig
        assertEquals(123, RegistryRemapper.mapEntityType761To762(118)); // fishing_bobber
        assertEquals(72, RegistryRemapper.mapEntityType761To762(99999)); // 範囲外はブタ
        assertEquals(1245, RegistryRemapper.mapSound761To762(1183)); // block.stone.break
        assertEquals(1453, RegistryRemapper.mapSound761To762(1391)); // 末尾
        assertEquals(1245, RegistryRemapper.mapSound761To762(99999)); // 範囲外は石の破壊音
        assertEquals(33, RegistryRemapper.mapParticle761To762(30)); // sculk_charge
        assertEquals(95, RegistryRemapper.mapParticle761To762(92)); // shriek
        assertEquals(82, RegistryRemapper.mapParticle761To762(79)); // white_ash
        assertEquals(0, RegistryRemapper.mapParticle761To762(99999)); // 範囲外は代替
        assertEquals(996, RegistryRemapper.mapBlock761To762(971)); // 末尾
        assertEquals(1, RegistryRemapper.mapBlock761To762(99999)); // 範囲外は石
        assertEquals(11, RegistryRemapper.mapMenu761To762(11)); // crafting
        assertEquals(20, RegistryRemapper.mapMenu761To762(20)); // smithing→legacy_smithing
        assertEquals(22, RegistryRemapper.mapMenu761To762(21)); // smoker
        assertEquals(23, RegistryRemapper.mapMenu761To762(22)); // cartography_table
        assertEquals(24, RegistryRemapper.mapMenu761To762(23)); // stonecutter
        assertEquals(11, RegistryRemapper.mapMenu761To762(99999)); // 範囲外は作業台
    }

    @Test
    void blockEntityForward() {
        // 762にしかない suspicious_sand・decorated_pot は DROP。
        // 範囲外も DROP（Block Entity Data ではパケットごと落とす）。
        assertEquals(9, RegistryRemapper.mapBlockEntityType762To761(9)); // mob_spawner
        assertEquals(1, RegistryRemapper.mapBlockEntityType762To761(1)); // chest
        assertEquals(-1, RegistryRemapper.mapBlockEntityType762To761(38)); // suspicious_sand
        assertEquals(-1, RegistryRemapper.mapBlockEntityType762To761(39)); // decorated_pot
        assertEquals(-1, RegistryRemapper.mapBlockEntityType762To761(99999)); // 範囲外は DROP
    }

    @Test
    void blockEntityReverse() {
        assertEquals(9, RegistryRemapper.mapBlockEntityType761To762(9)); // mob_spawner
        assertEquals(1, RegistryRemapper.mapBlockEntityType761To762(1)); // chest
        assertEquals(37, RegistryRemapper.mapBlockEntityType761To762(37)); // 末尾
        assertEquals(-1, RegistryRemapper.mapBlockEntityType761To762(99999)); // 範囲外は DROP
    }

    @Test
    void biomesForward() {
        // 63件すべて同一（plains は 38 のまま）。
        assertEquals(38, RegistryRemapper.mapBiome762To761(38)); // plains
        assertEquals(31, RegistryRemapper.mapBiome762To761(31)); // meadow
        assertEquals(34, RegistryRemapper.mapBiome762To761(34)); // ocean
        assertEquals(62, RegistryRemapper.mapBiome762To761(62)); // wooded_badlands
        assertEquals(38, RegistryRemapper.mapBiome762To761(99999)); // 範囲外は plains
    }

    @Test
    void biomesReverse() {
        assertEquals(38, RegistryRemapper.mapBiome761To762(38)); // plains
        assertEquals(31, RegistryRemapper.mapBiome761To762(31)); // meadow
        assertEquals(34, RegistryRemapper.mapBiome761To762(34)); // ocean
        assertEquals(62, RegistryRemapper.mapBiome761To762(62)); // wooded_badlands
        assertEquals(38, RegistryRemapper.mapBiome761To762(99999)); // 範囲外は plains
    }

    @Test
    void damageTypesForward() {
        // 761の codec に damage_type は存在しない（本物の1.19.3で確認）。
        // 対応表は恒等写像として残すが、ワイヤ上の呼び出し元は無い。
        assertEquals(16, RegistryRemapper.mapDamageType762To761(16)); // generic
        assertEquals(17, RegistryRemapper.mapDamageType762To761(17)); // hot_floor
        assertEquals(41, RegistryRemapper.mapDamageType762To761(41)); // 末尾
        // 範囲外は DROP（Damage Event ではパケットごと落とす）。
        assertEquals(-1, RegistryRemapper.mapDamageType762To761(99999));
    }

    @Test
    void damageTypesReverse() {
        assertEquals(16, RegistryRemapper.mapDamageType761To762(16)); // generic
        assertEquals(17, RegistryRemapper.mapDamageType761To762(17)); // hot_floor
        assertEquals(41, RegistryRemapper.mapDamageType761To762(41)); // 末尾
        assertEquals(-1, RegistryRemapper.mapDamageType761To762(99999)); // 範囲外は DROP
    }

    @Test
    void soundNamesForward() {
        assertEquals("block.stone.break",
                RegistryRemapper.mapSoundName762To761("block.stone.break"));
        assertEquals("block.wood.break",
                RegistryRemapper.mapSoundName762To761("block.cherry_wood.break"));
        assertEquals("block.sand.break",
                RegistryRemapper.mapSoundName762To761("item.brush.brushing"));
        assertEquals("entity.pig.ambient",
                RegistryRemapper.mapSoundName762To761("entity.sniffer.idle"));
        assertEquals("entity.generic.eat",
                RegistryRemapper.mapSoundName762To761("entity.sniffer.eat"));
        assertEquals("block.glass.break",
                RegistryRemapper.mapSoundName762To761("block.decorated_pot.shatter"));
        assertEquals("music.overworld.jungle_and_forest",
                RegistryRemapper.mapSoundName762To761("music.overworld.cherry_grove"));
        assertEquals("block.stone.break",
                RegistryRemapper.mapSoundName762To761("intentionally_empty"));
    }

    @Test
    void soundNamesReverse() {
        // 761 の音はすべて 762 に同名で存在するため、逆向きは恒等写像。
        assertEquals("block.stone.break",
                RegistryRemapper.mapSoundName761To762("block.stone.break"));
        assertEquals("block.wood.break",
                RegistryRemapper.mapSoundName761To762("block.wood.break"));
        assertEquals("music.overworld.jungle_and_forest",
                RegistryRemapper.mapSoundName761To762("music.overworld.jungle_and_forest"));
    }

    @Test
    void targetDispatchThrough762() {
        // 765起点の target 指定は762経由で761に届く（RegistryRemapper の hub は765）。
        assertEquals(RegistryRemapper.mapBlockState762To761(
                        RegistryRemapper.mapBlockState763To762(
                                RegistryRemapper.mapBlockState(1, 763))),
                RegistryRemapper.mapBlockState(1, 761));
        assertEquals(RegistryRemapper.mapItem762To761(
                        RegistryRemapper.mapItem763To762(
                                RegistryRemapper.mapItem(1, 763))),
                RegistryRemapper.mapItem(1, 761));
        assertEquals(RegistryRemapper.mapSound762To761(
                        RegistryRemapper.mapSound763To762(
                                RegistryRemapper.mapSound(1264, 763))),
                RegistryRemapper.mapSound(1264, 761));
        // エフェクトは765が0ベース、761・762が1ベースのため +1 する。
        // Speed は765=0→761=1、Slowness は765=1→761=2。
        assertEquals(1, RegistryRemapper.mapEffect(0, 761));
        assertEquals(2, RegistryRemapper.mapEffect(1, 761));
        assertEquals(33, RegistryRemapper.mapEffect(32, 761));
        // 範囲外は Speed（1）に代替。
        assertEquals(1, RegistryRemapper.mapEffect(33, 761));
    }
}
