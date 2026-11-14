package io.detergentmc.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * {@link RegistryRemapper} の762（1.19.4）⇔763（1.20／1.20.1）向けスポットチェックと往復テスト。
 * 期待値は ViaVersion の mapping-1.19.4.json／mapping-1.20.json と
 * minecraft-data（biomes.json・loginPacket.json の codec）から計算したもの（推測ではない）。
 * 生成スクリプト（detergentmc-protocol/tools/gen-762-tables.py）の
 * tables762.json と同じ値。検証は tools/verify-762-tables.py を参照。
 *
 * <p>762→763 の差分：ブロック状態 23,725→24,135（+410。calibrated_sculk_sensor 384件、
 * decorated_pot の cracked 追加16件、pitcher_crop 10件、suspicious_gravel 4件、
 * sniffer_egg 3件、pitcher_plant 2件）、ブロック 998→1,003（+5）、
 * アイテム 1,228→1,255（+27。新規31件−改名4件）、エンティティ 124件同士で並び同一、
 * 音 1,454→1,474（+20）、パーティクル 96→95（cherry_leaves 系の入れ替え）、
 * エンチャント 39件同士で並び同一、エフェクト 33件同士で並び同一（1ベース）、
 * ブロックエンティティ 40→41（suspicious_sand→brushable_block の改名と
 * calibrated_sculk_sensor の追加）、メニュー 25→24（legacy_smithing→smithing）、
 * バイオーム 63→64（cherry_grove を 5 に挿入）、ダメージ種別 42→44
 * （generic_kill を 17、outside_border を 30 に挿入）、絵画 30件同士で並び同一、
 * 楽器 23件同士で並び同一。
 *
 * <p>既知の注意点：バイオームの代替先は plains（762=38、763=39）。
 * minecraft-data の biomes.json はアルファベット順の登録で、plains は 1 ではない。
 */
class RegistryRemapper762Test {

    // ---- 763 → 762 ----

    @Test
    void blockStatesForward() {
        assertEquals(0, RegistryRemapper.mapBlockState763To762(0)); // air
        assertEquals(1, RegistryRemapper.mapBlockState763To762(1)); // stone
        // decorated_pot は 763 の cracked 違いを落として762の同向きに写す。
        assertEquals(23718, RegistryRemapper.mapBlockState763To762(24128)); // cracked=false
        assertEquals(23718, RegistryRemapper.mapBlockState763To762(24120)); // cracked=true
        assertEquals(23724, RegistryRemapper.mapBlockState763To762(24134)); // 末尾
        // 763にしかないブロック状態は近い既存ブロックの既定状態に代替
        // （gen-762-tables.py の BLOCK_FALLBACK_763_TO_762）。
        assertEquals(4274, RegistryRemapper.mapBlockState763To762(12357)); // pitcher_crop→wheat
        assertEquals(12632, RegistryRemapper.mapBlockState763To762(12659)); // sniffer_egg→turtle_egg
        assertEquals(1, RegistryRemapper.mapBlockState763To762(99999)); // 範囲外は石
    }

    @Test
    void blockStatesReverse() {
        assertEquals(0, RegistryRemapper.mapBlockState762To763(0)); // air
        assertEquals(1, RegistryRemapper.mapBlockState762To763(1)); // stone
        // 762にしかない torchflower_crop[age=2] は同ブロックの age=1 に代替
        // （プロパティを1個だけ落として一致するものを探す）。
        assertEquals(12355, RegistryRemapper.mapBlockState762To763(12352));
        assertEquals(24128, RegistryRemapper.mapBlockState762To763(23718)); // decorated_pot
        assertEquals(24134, RegistryRemapper.mapBlockState762To763(23724)); // 末尾
        assertEquals(1, RegistryRemapper.mapBlockState762To763(99999)); // 範囲外は石
    }

    @Test
    void itemsForward() {
        assertEquals(0, RegistryRemapper.mapItem763To762(0)); // air
        assertEquals(1, RegistryRemapper.mapItem763To762(1)); // stone
        // 陶器の破片は 763 の sherd 名から 762 の shard 名へ。
        assertEquals(1224, RegistryRemapper.mapItem763To762(1236)); // archer
        assertEquals(1225, RegistryRemapper.mapItem763To762(1250)); // prize
        assertEquals(1226, RegistryRemapper.mapItem763To762(1237)); // arms_up
        assertEquals(1227, RegistryRemapper.mapItem763To762(1253)); // skull
        // 763にしかないアイテムは近い既存アイテムに代替
        // （gen-762-tables.py の ITEM_FALLBACK_763_TO_762）。
        assertEquals(1118, RegistryRemapper.mapItem763To762(1135)); // music_disc_relic→cat
        assertEquals(809, RegistryRemapper.mapItem763To762(1107)); // pitcher_pod→wheat_seeds
        assertEquals(563, RegistryRemapper.mapItem763To762(566)); // sniffer_egg→turtle_egg
        assertEquals(650, RegistryRemapper.mapItem763To762(654)); // calibrated→sculk_sensor
        assertEquals(47, RegistryRemapper.mapItem763To762(46)); // suspicious_gravel→gravel
        assertEquals(1213, RegistryRemapper.mapItem763To762(1230)); // wayfinder trim→sentry
        assertEquals(1224, RegistryRemapper.mapItem763To762(1254)); // snort sherd→archer shard
        assertEquals(1, RegistryRemapper.mapItem763To762(99999)); // 範囲外は石
    }

    @Test
    void itemsReverse() {
        assertEquals(0, RegistryRemapper.mapItem762To763(0)); // air
        assertEquals(1, RegistryRemapper.mapItem762To763(1)); // stone
        assertEquals(1236, RegistryRemapper.mapItem762To763(1224)); // archer shard
        assertEquals(1250, RegistryRemapper.mapItem762To763(1225)); // prize shard
        assertEquals(1237, RegistryRemapper.mapItem762To763(1226)); // arms_up shard
        assertEquals(1253, RegistryRemapper.mapItem762To763(1227)); // skull shard（末尾）
        assertEquals(1, RegistryRemapper.mapItem762To763(99999)); // 範囲外は石
    }

    @Test
    void entitiesSoundsParticlesBlocksMenusForward() {
        // エンティティは並び同一（恒等写像）。
        assertEquals(0, RegistryRemapper.mapEntityType763To762(0)); // allay
        assertEquals(72, RegistryRemapper.mapEntityType763To762(72)); // pig
        assertEquals(90, RegistryRemapper.mapEntityType763To762(90)); // sniffer
        assertEquals(123, RegistryRemapper.mapEntityType763To762(123)); // fishing_bobber
        assertEquals(72, RegistryRemapper.mapEntityType763To762(99999)); // 範囲外はブタ
        // 音は挿入でずれる（block.stone.break 1264→1245）。
        assertEquals(0, RegistryRemapper.mapSound763To762(0));
        assertEquals(1245, RegistryRemapper.mapSound763To762(1264)); // block.stone.break
        assertEquals(781, RegistryRemapper.mapSound763To762(801)); // music_disc.relic→cat
        assertEquals(781, RegistryRemapper.mapSound763To762(790)); // music_disc.cat
        assertEquals(166, RegistryRemapper.mapSound763To762(167)); // brush generic
        assertEquals(167, RegistryRemapper.mapSound763To762(170)); // brush sand complete
        assertEquals(804, RegistryRemapper.mapSound763To762(827)); // jungle music
        assertEquals(565, RegistryRemapper.mapSound763To762(482)); // suspicious_gravel→gravel
        assertEquals(1297, RegistryRemapper.mapSound763To762(1238)); // sniffer plop→turtle lay
        assertEquals(1293, RegistryRemapper.mapSound763To762(1239)); // sniffer crack
        assertEquals(30, RegistryRemapper.mapSound763To762(34)); // amethyst resonate→chime
        assertEquals(1245, RegistryRemapper.mapSound763To762(1393)); // waxed fail→stone
        assertEquals(1453, RegistryRemapper.mapSound763To762(1473)); // 末尾
        assertEquals(1245, RegistryRemapper.mapSound763To762(99999)); // 範囲外は石の破壊音
        // パーティクルは cherry_leaves 系の入れ替えと挿入ずれ。
        assertEquals(0, RegistryRemapper.mapParticle763To762(0)); // ambient_entity_effect
        assertEquals(2, RegistryRemapper.mapParticle763To762(2)); // block
        assertEquals(14, RegistryRemapper.mapParticle763To762(14)); // dust
        assertEquals(33, RegistryRemapper.mapParticle763To762(31)); // sculk_charge
        assertEquals(42, RegistryRemapper.mapParticle763To762(40)); // item
        assertEquals(43, RegistryRemapper.mapParticle763To762(41)); // vibration
        assertEquals(95, RegistryRemapper.mapParticle763To762(93)); // shriek
        assertEquals(30, RegistryRemapper.mapParticle763To762(29)); // cherry_leaves→falling
        assertEquals(0, RegistryRemapper.mapParticle763To762(94)); // egg_crack→代替なし
        assertEquals(0, RegistryRemapper.mapParticle763To762(99999)); // 範囲外は代替
        // ブロックは新規5件が近い既存ブロックに代替。
        assertEquals(1, RegistryRemapper.mapBlock763To762(1)); // stone
        assertEquals(909, RegistryRemapper.mapBlock763To762(914)); // calibrated→sculk_sensor
        assertEquals(37, RegistryRemapper.mapBlock763To762(38)); // suspicious_gravel→gravel
        assertEquals(678, RegistryRemapper.mapBlock763To762(682)); // sniffer_egg→turtle_egg
        assertEquals(499, RegistryRemapper.mapBlock763To762(600)); // pitcher_plant→peony
        assertEquals(997, RegistryRemapper.mapBlock763To762(1002)); // decorated_pot（末尾）
        assertEquals(1, RegistryRemapper.mapBlock763To762(99999)); // 範囲外は石
        // メニューは legacy_smithing の分だけずれる。
        assertEquals(11, RegistryRemapper.mapMenu763To762(11)); // crafting
        assertEquals(21, RegistryRemapper.mapMenu763To762(20)); // smithing
        assertEquals(22, RegistryRemapper.mapMenu763To762(21)); // smoker
        assertEquals(23, RegistryRemapper.mapMenu763To762(22)); // cartography
        assertEquals(24, RegistryRemapper.mapMenu763To762(23)); // stonecutter
        assertEquals(11, RegistryRemapper.mapMenu763To762(99999)); // 範囲外は作業台
    }

    @Test
    void entitiesSoundsParticlesBlocksMenusReverse() {
        assertEquals(72, RegistryRemapper.mapEntityType762To763(72)); // pig
        assertEquals(123, RegistryRemapper.mapEntityType762To763(123)); // fishing_bobber
        assertEquals(72, RegistryRemapper.mapEntityType762To763(99999)); // 範囲外はブタ
        assertEquals(1264, RegistryRemapper.mapSound762To763(1245)); // block.stone.break
        assertEquals(167, RegistryRemapper.mapSound762To763(166)); // brush brushing
        assertEquals(170, RegistryRemapper.mapSound762To763(167)); // brush completed
        assertEquals(827, RegistryRemapper.mapSound762To763(804)); // jungle music
        assertEquals(1473, RegistryRemapper.mapSound762To763(1453)); // 末尾
        assertEquals(1264, RegistryRemapper.mapSound762To763(99999)); // 範囲外は石の破壊音
        assertEquals(31, RegistryRemapper.mapParticle762To763(33)); // sculk_charge
        assertEquals(40, RegistryRemapper.mapParticle762To763(42)); // item
        assertEquals(41, RegistryRemapper.mapParticle762To763(43)); // vibration
        assertEquals(93, RegistryRemapper.mapParticle762To763(95)); // shriek
        // 762の桜の葉3種はすべて763の cherry_leaves に写る。
        assertEquals(29, RegistryRemapper.mapParticle762To763(29)); // dripping
        assertEquals(29, RegistryRemapper.mapParticle762To763(30)); // falling
        assertEquals(29, RegistryRemapper.mapParticle762To763(31)); // landing
        assertEquals(0, RegistryRemapper.mapParticle762To763(99999)); // 範囲外は代替
        assertEquals(1002, RegistryRemapper.mapBlock762To763(997)); // decorated_pot（末尾）
        assertEquals(1, RegistryRemapper.mapBlock762To763(99999)); // 範囲外は石
        assertEquals(11, RegistryRemapper.mapMenu762To763(11)); // crafting
        assertEquals(20, RegistryRemapper.mapMenu762To763(20)); // legacy_smithing→smithing
        assertEquals(20, RegistryRemapper.mapMenu762To763(21)); // smithing→smithing
        assertEquals(21, RegistryRemapper.mapMenu762To763(22)); // smoker
        assertEquals(22, RegistryRemapper.mapMenu762To763(23)); // cartography_table
        assertEquals(23, RegistryRemapper.mapMenu762To763(24)); // stonecutter
        assertEquals(11, RegistryRemapper.mapMenu762To763(99999)); // 範囲外は作業台
    }

    @Test
    void blockEntityForward() {
        // suspicious_sand→brushable_block の改名と、calibrated_sculk_sensor の
        // sculk_sensor への代替（DROP ではない。範囲外だけが DROP）。
        assertEquals(9, RegistryRemapper.mapBlockEntityType763To762(9)); // mob_spawner
        assertEquals(34, RegistryRemapper.mapBlockEntityType763To762(35)); // calibrated→sculk_sensor
        assertEquals(35, RegistryRemapper.mapBlockEntityType763To762(36)); // sculk_catalyst
        assertEquals(36, RegistryRemapper.mapBlockEntityType763To762(37)); // sculk_shrieker
        assertEquals(37, RegistryRemapper.mapBlockEntityType763To762(38)); // chiseled_bookshelf
        assertEquals(38, RegistryRemapper.mapBlockEntityType763To762(39)); // brushable_block
        assertEquals(39, RegistryRemapper.mapBlockEntityType763To762(40)); // decorated_pot
        assertEquals(-1, RegistryRemapper.mapBlockEntityType763To762(99999)); // 範囲外は DROP
    }

    @Test
    void blockEntityReverse() {
        assertEquals(9, RegistryRemapper.mapBlockEntityType762To763(9)); // mob_spawner
        assertEquals(36, RegistryRemapper.mapBlockEntityType762To763(35)); // sculk_catalyst
        assertEquals(39, RegistryRemapper.mapBlockEntityType762To763(38)); // suspicious_sand
        assertEquals(40, RegistryRemapper.mapBlockEntityType762To763(39)); // decorated_pot
        assertEquals(-1, RegistryRemapper.mapBlockEntityType762To763(99999)); // 範囲外は DROP
    }

    @Test
    void biomesForward() {
        // cherry_grove（763=5）は meadow（762=31）に代替。plains は 39→38。
        assertEquals(38, RegistryRemapper.mapBiome763To762(39)); // plains
        assertEquals(31, RegistryRemapper.mapBiome763To762(5)); // cherry_grove→meadow
        assertEquals(34, RegistryRemapper.mapBiome763To762(35)); // ocean
        assertEquals(62, RegistryRemapper.mapBiome763To762(63)); // wooded_badlands
        assertEquals(38, RegistryRemapper.mapBiome763To762(99999)); // 範囲外は plains
    }

    @Test
    void biomesReverse() {
        assertEquals(39, RegistryRemapper.mapBiome762To763(38)); // plains
        assertEquals(32, RegistryRemapper.mapBiome762To763(31)); // meadow
        assertEquals(35, RegistryRemapper.mapBiome762To763(34)); // ocean
        assertEquals(63, RegistryRemapper.mapBiome762To763(62)); // wooded_badlands
        assertEquals(39, RegistryRemapper.mapBiome762To763(99999)); // 範囲外は plains
    }

    @Test
    void damageTypesForward() {
        // generic_kill（17）・outside_border（30）は generic（16）に代替。
        assertEquals(16, RegistryRemapper.mapDamageType763To762(16)); // generic
        assertEquals(16, RegistryRemapper.mapDamageType763To762(17)); // generic_kill
        assertEquals(16, RegistryRemapper.mapDamageType763To762(30)); // outside_border
        assertEquals(17, RegistryRemapper.mapDamageType763To762(18)); // hot_floor
        assertEquals(41, RegistryRemapper.mapDamageType763To762(43)); // 末尾
        // 範囲外は DROP（Damage Event ではパケットごと落とす）。
        assertEquals(-1, RegistryRemapper.mapDamageType763To762(99999));
    }

    @Test
    void damageTypesReverse() {
        assertEquals(16, RegistryRemapper.mapDamageType762To763(16)); // generic
        assertEquals(18, RegistryRemapper.mapDamageType762To763(17)); // hot_floor
        assertEquals(43, RegistryRemapper.mapDamageType762To763(41)); // 末尾
        assertEquals(-1, RegistryRemapper.mapDamageType762To763(99999)); // 範囲外は DROP
    }

    @Test
    void soundNamesForward() {
        assertEquals("block.stone.break",
                RegistryRemapper.mapSoundName763To762("block.stone.break"));
        assertEquals("music_disc.cat",
                RegistryRemapper.mapSoundName763To762("music_disc.relic"));
        assertEquals("item.brush.brushing",
                RegistryRemapper.mapSoundName763To762("item.brush.brushing.generic"));
        assertEquals("item.brush.brush_sand_completed",
                RegistryRemapper.mapSoundName763To762("item.brush.brushing.sand.complete"));
        assertEquals("music.overworld.jungle_and_forest",
                RegistryRemapper.mapSoundName763To762("music.overworld.jungle"));
        assertEquals("block.gravel.break",
                RegistryRemapper.mapSoundName763To762("block.suspicious_gravel.break"));
        assertEquals("entity.turtle.lay_egg",
                RegistryRemapper.mapSoundName763To762("block.sniffer_egg.plop"));
        assertEquals("block.stone.break",
                RegistryRemapper.mapSoundName763To762("block.sign.waxed_interact_fail"));
        assertEquals("block.amethyst_block.chime",
                RegistryRemapper.mapSoundName763To762("block.amethyst_block.resonate"));
    }

    @Test
    void soundNamesReverse() {
        assertEquals("block.stone.break",
                RegistryRemapper.mapSoundName762To763("block.stone.break"));
        assertEquals("item.brush.brushing.generic",
                RegistryRemapper.mapSoundName762To763("item.brush.brushing"));
        assertEquals("item.brush.brushing.sand.complete",
                RegistryRemapper.mapSoundName762To763("item.brush.brush_sand_completed"));
        assertEquals("music.overworld.jungle",
                RegistryRemapper.mapSoundName762To763("music.overworld.jungle_and_forest"));
    }

    @Test
    void targetDispatchThrough763() {
        // 765起点の target 指定は763経由で762に届く（RegistryRemapper の hub は765）。
        assertEquals(RegistryRemapper.mapBlockState763To762(
                        RegistryRemapper.mapBlockState(1, 763)),
                RegistryRemapper.mapBlockState(1, 762));
        assertEquals(RegistryRemapper.mapItem763To762(
                        RegistryRemapper.mapItem(1, 763)),
                RegistryRemapper.mapItem(1, 762));
        assertEquals(RegistryRemapper.mapSound763To762(
                        RegistryRemapper.mapSound(1264, 763)),
                RegistryRemapper.mapSound(1264, 762));
        // エフェクトは765が0ベース、762・763が1ベースのため +1 する。
        // Speed は765=0→762=1、Slowness は765=1→762=2。
        assertEquals(1, RegistryRemapper.mapEffect(0, 762));
        assertEquals(2, RegistryRemapper.mapEffect(1, 762));
        assertEquals(33, RegistryRemapper.mapEffect(32, 762));
        // 範囲外は Speed（1）に代替。
        assertEquals(1, RegistryRemapper.mapEffect(33, 762));
    }
}
