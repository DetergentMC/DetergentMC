package io.detergentmc.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * {@link RegistryRemapper} の772（1.21.7／1.21.8）向けスポットチェックと往復テスト。
 * 期待値は minecraft-data のレジストリから計算したもの（推測ではない）。
 * 生成スクリプト（tools/generate-remap-tables.py --target 772）の
 * maps772.json／maps_composed_772.json と同じ値。
 * 検証は tools/verify-772-tables.py を参照。
 *
 * <p>771→772 の差分：ブロック状態 27,946件同士で並び同一、アイテム 1,415→1,416
 * （+1。music_disc_lava_chicken が 1256 に挿入され、それ以降が+1ずれる）、
 * エンティティ 151件同士で並び同一、音 1,727→1,728（+1。music_disc.lava_chicken
 * が 939 に挿入され、それ以降が+1ずれる）、パーティクル 114件同士で並び同一、
 * 属性 35件同士で並び同一、エンチャント 42件同士で並び同一、データコンポーネント
 * 96件のまま、バイオーム 65件同士で並び同一、ダメージ種別 49件同士で並び同一、
 * ジュークボックスの歌 20→21件（lava_chicken を9番に挿入。アルファベット順からの
 * 仮定で、実バニラ1.21.7／1.21.8のキャプチャで確認する）、絵画バリアント 50→51件
 * （dennis を14番に挿入。771の絵画リストはアルファベット順で、creebet=13、donkey_kong=14
 * のため dennis はその間に入る。キャプチャで確認する）。
 * ブロックエンティティは 47件同士で並び同一。
 */
class RegistryRemapper772Test {

    // ---- 771 → 772 ----

    @Test
    void blockStatesForward() {
        // ブロック状態は同一（恒等写像）。
        assertEquals(0, RegistryRemapper.mapBlockState771To772(0)); // air
        assertEquals(1, RegistryRemapper.mapBlockState771To772(1)); // stone
        assertEquals(27945, RegistryRemapper.mapBlockState771To772(27945)); // 末尾
        assertEquals(1, RegistryRemapper.mapBlockState771To772(99999)); // 範囲外は石
    }

    @Test
    void itemsForward() {
        assertEquals(0, RegistryRemapper.mapItem771To772(0)); // air
        assertEquals(1, RegistryRemapper.mapItem771To772(1)); // stone
        assertEquals(1255, RegistryRemapper.mapItem771To772(1255)); // music_disc_far（挿入点の直前）
        assertEquals(1257, RegistryRemapper.mapItem771To772(1256)); // music_disc_mall（+1）
        assertEquals(1415, RegistryRemapper.mapItem771To772(1414)); // 末尾
        assertEquals(1, RegistryRemapper.mapItem771To772(99999)); // 範囲外は石
    }

    @Test
    void entitiesSoundsParticlesBlocksMenusForward() {
        // エンティティ・パーティクル・ブロック・メニュー・エフェクトは同一（恒等写像）。
        assertEquals(0, RegistryRemapper.mapEntityType771To772(0)); // acacia_boat
        assertEquals(95, RegistryRemapper.mapEntityType771To772(95)); // pig
        assertEquals(150, RegistryRemapper.mapEntityType771To772(150)); // 末尾
        assertEquals(95, RegistryRemapper.mapEntityType771To772(99999)); // 範囲外はブタ
        assertEquals(0, RegistryRemapper.mapSound771To772(0));
        assertEquals(938, RegistryRemapper.mapSound771To772(938)); // music_disc.far（挿入点の直前）
        assertEquals(940, RegistryRemapper.mapSound771To772(939)); // music_disc.mall（+1）
        assertEquals(1442, RegistryRemapper.mapSound771To772(1441)); // block.stone.break（+1）
        assertEquals(1727, RegistryRemapper.mapSound771To772(1726)); // 末尾
        assertEquals(1442, RegistryRemapper.mapSound771To772(99999)); // 範囲外は石の破壊音
        assertEquals(20, RegistryRemapper.mapParticle771To772(20)); // entity_effect
        assertEquals(113, RegistryRemapper.mapParticle771To772(113)); // firefly
        assertEquals(1, RegistryRemapper.mapBlock771To772(1)); // stone
        assertEquals(1104, RegistryRemapper.mapBlock771To772(1104)); // 末尾
        assertEquals(12, RegistryRemapper.mapMenu771To772(12)); // crafting
        assertEquals(0, RegistryRemapper.mapEffect771To772(0)); // Speed
        assertEquals(38, RegistryRemapper.mapEffect771To772(38)); // Infested
    }

    @Test
    void blockEntityForward() {
        // 771と772のブロックエンティティの並びは同一（恒等写像）。
        assertEquals(9, RegistryRemapper.mapBlockEntityType771To772(9)); // mob_spawner
        assertEquals(10, RegistryRemapper.mapBlockEntityType771To772(10)); // creaking_heart
        assertEquals(11, RegistryRemapper.mapBlockEntityType771To772(11)); // piston
        assertEquals(43, RegistryRemapper.mapBlockEntityType771To772(43)); // trial_spawner
        assertEquals(44, RegistryRemapper.mapBlockEntityType771To772(44)); // vault
        assertEquals(45, RegistryRemapper.mapBlockEntityType771To772(45)); // test_block
        assertEquals(46, RegistryRemapper.mapBlockEntityType771To772(46)); // test_instance_block
        assertEquals(-1, RegistryRemapper.mapBlockEntityType771To772(47)); // 範囲外は DROP
    }

    @Test
    void soundNamesForward() {
        // 771→772 の音の名前の表は空（771 の音はすべてそのまま通る）。
        assertEquals("block.stone.break",
                RegistryRemapper.mapSoundName771To772("block.stone.break"));
        assertEquals("music_disc.mall",
                RegistryRemapper.mapSoundName771To772("music_disc.mall"));
        assertEquals("entity.puffer_fish.blow_out",
                RegistryRemapper.mapSoundName771To772("entity.puffer_fish.blow_out"));
    }

    @Test
    void attributesForward() {
        // 771と772の属性の並びは同一（恒等写像）。
        assertEquals(0, RegistryRemapper.mapAttribute771To772(0)); // generic.armor
        assertEquals(8, RegistryRemapper.mapAttribute771To772(8)); // camera_distance
        assertEquals(34, RegistryRemapper.mapAttribute771To772(34)); // waypoint_receive_range
        assertEquals("camera_distance", RegistryRemapper.attributeName772(8));
        assertEquals("waypoint_transmit_range", RegistryRemapper.attributeName772(33));
        assertEquals("waypoint_receive_range", RegistryRemapper.attributeName772(34));
        assertEquals(8, RegistryRemapper.attributeId772("camera_distance"));
        assertEquals(33, RegistryRemapper.attributeId772("waypoint_transmit_range"));
        assertEquals(34, RegistryRemapper.attributeId772("waypoint_receive_range"));
        assertEquals(-1, RegistryRemapper.attributeId772("not_an_attribute"));
        assertEquals(8, RegistryRemapper.attributeId772("minecraft:camera_distance"));
    }

    @Test
    void enchantmentsForward() {
        // 771と772のエンチャントの並びは同一（恒等写像）。
        assertEquals(0, RegistryRemapper.mapEnchantment771To772(0)); // aqua_affinity
        assertEquals(27, RegistryRemapper.mapEnchantment771To772(27)); // protection
        assertEquals(41, RegistryRemapper.mapEnchantment771To772(41)); // wind_burst
        assertEquals(0, RegistryRemapper.mapEnchantment772To771(0));
        assertEquals(27, RegistryRemapper.mapEnchantment772To771(27));
        assertEquals(41, RegistryRemapper.mapEnchantment772To771(41));
        assertEquals(-1, RegistryRemapper.mapEnchantment771To772(999)); // 範囲外は DROP
        assertEquals(-1, RegistryRemapper.mapEnchantment772To771(999));
    }

    @Test
    void damageTypesPaintingsAndJukeboxSongs() {
        // ダメージ種別は771と772で49種・同じ並び。
        assertEquals(0, RegistryRemapper.mapDamageType771To772(0));
        assertEquals(48, RegistryRemapper.mapDamageType771To772(48));
        assertEquals(0, RegistryRemapper.mapDamageType772To771(0));
        assertEquals(48, RegistryRemapper.mapDamageType772To771(48));
        assertEquals(-1, RegistryRemapper.mapDamageType771To772(49)); // 範囲外は DROP
        assertEquals(-1, RegistryRemapper.mapDamageType772To771(49));
        // 絵画バリアントは dennis を14番に挿入（アルファベット順。771は
        // courbet=12、creebet=13、donkey_kong=14、wither=49。
        // 767の PAINTING_767_NAMES とキャプチャのピン（kebab=23、pointer=32、
        // pond=33、unpacked=43、wither=49）で順序を確認済み。771の絵画は
        // 50件すべてアルファベット順で、dennis は creebet（13）の次、
        // donkey_kong（14）の前に来る。キャプチャで再確認する）。
        assertEquals(12, RegistryRemapper.mapPaintingVariant771To772(12)); // courbet
        assertEquals(13, RegistryRemapper.mapPaintingVariant771To772(13)); // creebet
        assertEquals(15, RegistryRemapper.mapPaintingVariant771To772(14)); // donkey_kong
        assertEquals(50, RegistryRemapper.mapPaintingVariant771To772(49)); // wither
        assertEquals(-1, RegistryRemapper.mapPaintingVariant771To772(50)); // 範囲外は DROP
        assertEquals(13, RegistryRemapper.mapPaintingVariant772To771(13)); // creebet
        assertEquals(-1, RegistryRemapper.mapPaintingVariant772To771(14)); // dennis → DROP
        assertEquals(14, RegistryRemapper.mapPaintingVariant772To771(15)); // donkey_kong
        assertEquals(49, RegistryRemapper.mapPaintingVariant772To771(50)); // wither
        assertEquals(-1, RegistryRemapper.mapPaintingVariant772To771(51)); // 範囲外は DROP
        // ジュークボックスの歌は lava_chicken を9番に挿入（アルファベット順からの仮定）。
        assertEquals(8, RegistryRemapper.mapJukeboxSong771To772(8)); // far
        assertEquals(10, RegistryRemapper.mapJukeboxSong771To772(9)); // mall
        assertEquals(20, RegistryRemapper.mapJukeboxSong771To772(19)); // ward
        assertEquals(-1, RegistryRemapper.mapJukeboxSong771To772(20)); // 範囲外は DROP
        assertEquals(-1, RegistryRemapper.mapJukeboxSong772To771(9)); // lava_chicken → DROP
        assertEquals(9, RegistryRemapper.mapJukeboxSong772To771(10)); // mall
        assertEquals(19, RegistryRemapper.mapJukeboxSong772To771(20)); // ward
        assertEquals(-1, RegistryRemapper.mapJukeboxSong772To771(21)); // 範囲外は DROP
        assertEquals("lava_chicken", RegistryRemapper.jukeboxSongName772(9));
        assertEquals("mall", RegistryRemapper.jukeboxSongName772(10));
    }

    // ---- 772 → 771（invert）----

    @Test
    void blockStatesUp() {
        assertEquals(0, RegistryRemapper.mapBlockStateUp772(0));
        assertEquals(27945, RegistryRemapper.mapBlockStateUp772(27945)); // 末尾
        assertEquals(1, RegistryRemapper.mapBlockStateUp772(99999)); // 範囲外は石
    }

    @Test
    void itemsUp() {
        // 772で新規のアイテム（レコード「ラヴァ・チキン」）は石へ。
        assertEquals(1, RegistryRemapper.mapItemUp772(1256)); // lava_chicken
        assertEquals(1256, RegistryRemapper.mapItemUp772(1257)); // mall
        assertEquals(1414, RegistryRemapper.mapItemUp772(1415)); // 末尾
        assertEquals(1, RegistryRemapper.mapItemUp772(99999)); // 範囲外は石
    }

    @Test
    void itemReverse772KeepsUnknownIds() {
        assertEquals(1, RegistryRemapper.mapItemReverse772(1256)); // lava_chicken → 石
        assertEquals(1414, RegistryRemapper.mapItemReverse772(1415));
        assertEquals(9999, RegistryRemapper.mapItemReverse772(9999)); // 範囲外はそのまま
    }

    @Test
    void entitiesSoundsParticlesUp() {
        assertEquals(95, RegistryRemapper.mapEntityTypeUp772(95)); // pig
        assertEquals(150, RegistryRemapper.mapEntityTypeUp772(150));
        assertEquals(95, RegistryRemapper.mapEntityTypeUp772(99999)); // 範囲外はブタ
        assertEquals(939, RegistryRemapper.mapSoundUp772(940)); // mall
        assertEquals(1441, RegistryRemapper.mapSoundUp772(1442)); // block.stone.break
        assertEquals(1726, RegistryRemapper.mapSoundUp772(1727)); // 末尾
        assertEquals(0, RegistryRemapper.mapSoundUp772(0)); // 変わらない音
        assertEquals(1441, RegistryRemapper.mapSoundUp772(939)); // lava_chicken → 石の破壊音
        assertEquals(1441, RegistryRemapper.mapSoundUp772(99999)); // 範囲外は石の破壊音
        assertEquals(20, RegistryRemapper.mapParticleUp772(20)); // entity_effect
        assertEquals(113, RegistryRemapper.mapParticleUp772(113)); // firefly
        assertEquals(1, RegistryRemapper.mapBlockUp772(1)); // stone
        assertEquals(1104, RegistryRemapper.mapBlockUp772(1104));
        assertEquals(12, RegistryRemapper.mapMenuUp772(12)); // crafting
        assertEquals(0, RegistryRemapper.mapEffectUp772(0)); // Speed
        assertEquals(38, RegistryRemapper.mapEffectUp772(38)); // Infested
    }

    @Test
    void blockEntityUp() {
        assertEquals(10, RegistryRemapper.mapBlockEntityTypeUp772(10)); // creaking_heart
        assertEquals(11, RegistryRemapper.mapBlockEntityTypeUp772(11)); // piston
        assertEquals(43, RegistryRemapper.mapBlockEntityTypeUp772(43)); // trial_spawner
        assertEquals(44, RegistryRemapper.mapBlockEntityTypeUp772(44)); // vault
        assertEquals(45, RegistryRemapper.mapBlockEntityTypeUp772(45)); // test_block
        assertEquals(46, RegistryRemapper.mapBlockEntityTypeUp772(46)); // test_instance_block
        assertEquals(-1, RegistryRemapper.mapBlockEntityTypeUp772(47)); // 範囲外は DROP
    }

    @Test
    void soundNamesUp() {
        assertEquals("block.stone.break",
                RegistryRemapper.mapSoundNameUp772("block.stone.break"));
        // 772にしかない lava_chicken は771向けに石の破壊音へ。
        assertEquals("block.stone.break",
                RegistryRemapper.mapSoundNameUp772("music_disc.lava_chicken"));
        assertEquals("entity.puffer_fish.blow_out",
                RegistryRemapper.mapSoundNameUp772("entity.puffer_fish.blow_out"));
    }

    @Test
    void attributesUp() {
        // 771と772の属性の並びは同一（恒等写像）。
        assertEquals(0, RegistryRemapper.mapAttribute772To771(0)); // generic.armor
        assertEquals(8, RegistryRemapper.mapAttribute772To771(8)); // camera_distance
        assertEquals(34, RegistryRemapper.mapAttribute772To771(34)); // waypoint_receive_range
    }

    // ---- 往復・網羅 ----

    @Test
    void roundTrip771To772To771() {
        for (int i = 0; i <= RegistryRemapper.MAX_BLOCK_STATE_771; i++) {
            assertEquals(i, RegistryRemapper.mapBlockStateUp772(
                    RegistryRemapper.mapBlockState771To772(i)), "blockstate " + i);
        }
        for (int i = 0; i <= RegistryRemapper.MAX_ITEM_771; i++) {
            assertEquals(i, RegistryRemapper.mapItemUp772(
                    RegistryRemapper.mapItem771To772(i)), "item " + i);
        }
        for (int i = 0; i <= RegistryRemapper.MAX_ENTITY_TYPE_771; i++) {
            assertEquals(i, RegistryRemapper.mapEntityTypeUp772(
                    RegistryRemapper.mapEntityType771To772(i)), "entity " + i);
        }
        for (int i = 0; i <= RegistryRemapper.MAX_SOUND_771; i++) {
            assertEquals(i, RegistryRemapper.mapSoundUp772(
                    RegistryRemapper.mapSound771To772(i)), "sound " + i);
        }
        for (int i = 0; i <= RegistryRemapper.MAX_PARTICLE_771; i++) {
            assertEquals(i, RegistryRemapper.mapParticleUp772(
                    RegistryRemapper.mapParticle771To772(i)), "particle " + i);
        }
        for (int i = 0; i <= RegistryRemapper.MAX_BLOCK_771; i++) {
            assertEquals(i, RegistryRemapper.mapBlockUp772(
                    RegistryRemapper.mapBlock771To772(i)), "block " + i);
        }
        for (int i = 0; i <= RegistryRemapper.MAX_MENU_771; i++) {
            assertEquals(i, RegistryRemapper.mapMenuUp772(
                    RegistryRemapper.mapMenu771To772(i)), "menu " + i);
        }
    }

    @Test
    void exhaustiveForwardStayInRange() {
        for (int i = 0; i <= RegistryRemapper.MAX_BLOCK_STATE_771; i++) {
            int mapped = RegistryRemapper.mapBlockState771To772(i);
            assertTrue(mapped >= 0 && mapped <= RegistryRemapper.MAX_BLOCK_STATE_772,
                    "771 blockstate " + i + " -> " + mapped);
        }
        for (int i = 0; i <= RegistryRemapper.MAX_ITEM_771; i++) {
            int mapped = RegistryRemapper.mapItem771To772(i);
            assertTrue(mapped >= 0 && mapped <= RegistryRemapper.MAX_ITEM_772,
                    "771 item " + i + " -> " + mapped);
        }
        for (int i = 0; i <= RegistryRemapper.MAX_SOUND_771; i++) {
            int mapped = RegistryRemapper.mapSound771To772(i);
            assertTrue(mapped >= 0 && mapped <= RegistryRemapper.MAX_SOUND_772,
                    "771 sound " + i + " -> " + mapped);
        }
        for (int i = 0; i <= RegistryRemapper.MAX_PARTICLE_771; i++) {
            int mapped = RegistryRemapper.mapParticle771To772(i);
            assertTrue(mapped >= 0 && mapped <= RegistryRemapper.MAX_PARTICLE_772,
                    "771 particle " + i + " -> " + mapped);
        }
    }

    // ---- 合成表 763↔772 / 765↔772 / 770↔772 ----

    @Test
    void composed763To772() {
        // 763→771 の値に771→772の付け替えを重ねたもの（音は lava_chicken の挿入で+1）。
        assertEquals(852, RegistryRemapper.mapItem763To772(757)); // scute → turtle_scute
        assertEquals(85, RegistryRemapper.mapEntityType763To772(9)); // boat → oak_boat
        assertEquals(86, RegistryRemapper.mapEntityType763To772(13)); // chest_boat → oak_chest_boat
        assertEquals(1442, RegistryRemapper.mapSound763To772(1264)); // block.stone.break
        assertEquals(0, RegistryRemapper.mapEffect763To772(1)); // Speed（1ベース→0ベース）
        assertEquals(32, RegistryRemapper.mapEffect763To772(33)); // Unluck
        assertEquals(9, RegistryRemapper.mapBlockEntityType763To772(9)); // mob_spawner
        assertEquals(11, RegistryRemapper.mapBlockEntityType763To772(10)); // piston
        assertEquals("block.stone.break",
                RegistryRemapper.mapSoundName763To772("block.stone.break"));
    }

    @Test
    void composed765To772AndBack() {
        assertEquals(852, RegistryRemapper.mapItem765To772(794)); // scute → turtle_scute
        assertEquals(1442, RegistryRemapper.mapSound765To772(1312)); // block.stone.break
        assertEquals(794, RegistryRemapper.mapItem772To765(852)); // turtle_scute → scute
        assertEquals(1, RegistryRemapper.mapItem772To765(1256)); // lava_chicken → 石
        assertEquals(0, RegistryRemapper.mapEffect765To772(0)); // Speed
        assertEquals(43, RegistryRemapper.mapBlockEntityType765To772(42)); // trial_spawner
        assertEquals("block.trial_spawner.ominous_activate",
                RegistryRemapper.mapSoundName765To772("block.trial_spawner.charge_activate"));
        assertEquals("block.stone.break",
                RegistryRemapper.mapSoundName772To765("music_disc.creator"));
    }

    @Test
    void composed770To772AndBack() {
        assertEquals(1415, RegistryRemapper.mapItem770To772(1395)); // 末尾
        assertEquals(1726, RegistryRemapper.mapSound770To772(1701)); // 末尾
        assertEquals(1442, RegistryRemapper.mapSound770To772(1417)); // block.stone.break
        assertEquals(1395, RegistryRemapper.mapItem772To770(1415)); // 末尾
        assertEquals(1701, RegistryRemapper.mapSound772To770(1726)); // 末尾
        assertEquals(1417, RegistryRemapper.mapSound772To770(1727)); // 末尾。item.saddle.unequip は 770 に無いので stone.break（1417）へ
        assertEquals(1, RegistryRemapper.mapItem772To770(1256)); // lava_chicken → 石
    }

    @Test
    void protocolDispatchRoutes772() {
        // プロトコル指定のオーバーロードが 772 を 772表へ振ること。
        assertEquals(27736, RegistryRemapper.mapBlockState(26638, 772));
        assertEquals(852, RegistryRemapper.mapItem(794, 772));
        assertEquals(95, RegistryRemapper.mapEntityType(73, 772));
        assertEquals(1442, RegistryRemapper.mapSound(1312, 772));
        // 765のパーティクル 0（ambient_entity_effect）は 768・769・770・771・772 では
        // entity_effect（20）になる（766 で挿入によるずれ）。
        assertEquals(20, RegistryRemapper.mapParticle(0, 772));
        assertEquals(1, RegistryRemapper.mapBlock(1, 772));
        assertEquals(12, RegistryRemapper.mapMenu(12, 772));
        assertEquals(0, RegistryRemapper.mapEffect(0, 772));
        assertEquals(43, RegistryRemapper.mapBlockEntityType(42, 772));
        assertEquals("block.trial_spawner.ominous_activate",
                RegistryRemapper.mapSoundName("block.trial_spawner.charge_activate", 772));
        // 772クライアントのアイテムID → 765サーバーのアイテムID。
        assertEquals(794, RegistryRemapper.mapItemReverse(852, 772));
        assertEquals(1, RegistryRemapper.mapItemReverse(1256, 772));
        // 771の振り分けが変わっていないこと。
        assertEquals(27736, RegistryRemapper.mapBlockState(26638, 771));
        assertEquals(852, RegistryRemapper.mapItem(794, 771));
    }
}
