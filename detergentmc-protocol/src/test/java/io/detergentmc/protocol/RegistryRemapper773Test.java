package io.detergentmc.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * {@link RegistryRemapper} の773（1.21.9／1.21.10）向けスポットチェックと往復テスト。
 * 期待値は minecraft-data のレジストリから計算したもの（推測ではない）。
 * 生成スクリプト（tools/generate-remap-tables.py --target 773）の
 * maps773.json／maps_composed_773.json と同じ値。
 * 検証は tools/verify-773-tables.py を参照。
 *
 * <p>772→773 の差分：ブロック状態 27,946→29,671（+1,725。銅の棚・銅の松明・
 * 銅の棒・銅の鎖・銅のランタン・銅のチェスト・銅ゴーレム像などの追加と挿入による
 * 番号のずれ）、ブロック 1,105→1,166（+61。chain→iron_chain の改名1件あり）、
 * アイテム 1,416→1,488（+72。銅の道具・防具・棚・銅の松明類・銅の延べ棒・
 * 銅ゴーレムのスポーンエッグ・銅の馬鎧など。chain→iron_chain の改名あり）、
 * エンティティ 151→153（+2。copper_golem が 27、mannequin が 82 に挿入され、
 * それ以降が+2ずれる）、音 1,728→1,771（+43。銅ゴーレム・棚・銅のチェスト関連
 * など）、パーティクル 114→115（+1。copper_fire_flame が 5 に挿入され、
 * それ以降が+1ずれる）、属性 35件同士で並び同一、エンチャント 42件同士で並び同一、
 * データコンポーネント 96件のまま、バイオーム 65件同士で並び同一、ダメージ種別
 * 49件同士で並び同一、ジュークボックスの歌 21件同士で並び同一、絵画バリアント
 * 51件同士で並び同一。
 * ブロックエンティティは 47→49件（shelf が 40 に挿入され 40 以降が+1、
 * copper_golem_statue を 48 に追加）。
 */
class RegistryRemapper773Test {

    // ---- 772 → 773 ----

    @Test
    void blockStatesForward() {
        assertEquals(0, RegistryRemapper.mapBlockState772To773(0)); // air
        assertEquals(1, RegistryRemapper.mapBlockState772To773(1)); // stone
        assertEquals(29670, RegistryRemapper.mapBlockState772To773(27945)); // 末尾
        assertEquals(1, RegistryRemapper.mapBlockState772To773(99999)); // 範囲外は石
    }

    @Test
    void itemsForward() {
        assertEquals(0, RegistryRemapper.mapItem772To773(0)); // air
        assertEquals(1, RegistryRemapper.mapItem772To773(1)); // stone
        assertEquals(1303, RegistryRemapper.mapItem772To773(1255)); // music_disc_far
        assertEquals(1304, RegistryRemapper.mapItem772To773(1256)); // music_disc_lava_chicken
        assertEquals(1305, RegistryRemapper.mapItem772To773(1257)); // music_disc_mall
        assertEquals(1487, RegistryRemapper.mapItem772To773(1415)); // 末尾
        assertEquals(1, RegistryRemapper.mapItem772To773(99999)); // 範囲外は石
    }

    @Test
    void entitiesSoundsParticlesBlocksMenusForward() {
        // エンティティ・音・パーティクル・ブロックは挿入でずれる。メニュー・エフェクトは同一。
        assertEquals(0, RegistryRemapper.mapEntityType772To773(0)); // acacia_boat
        assertEquals(97, RegistryRemapper.mapEntityType772To773(95)); // pig（copper_golem・mannequin の挿入で+2）
        assertEquals(152, RegistryRemapper.mapEntityType772To773(150)); // 末尾
        assertEquals(97, RegistryRemapper.mapEntityType772To773(99999)); // 範囲外はブタ
        assertEquals(0, RegistryRemapper.mapSound772To773(0));
        assertEquals(969, RegistryRemapper.mapSound772To773(938)); // music_disc.far
        assertEquals(970, RegistryRemapper.mapSound772To773(939)); // music_disc.lava_chicken
        assertEquals(971, RegistryRemapper.mapSound772To773(940)); // music_disc.mall
        assertEquals(1484, RegistryRemapper.mapSound772To773(1442)); // block.stone.break
        assertEquals(1770, RegistryRemapper.mapSound772To773(1727)); // 末尾
        assertEquals(1484, RegistryRemapper.mapSound772To773(99999)); // 範囲外は石の破壊音
        assertEquals(21, RegistryRemapper.mapParticle772To773(20)); // entity_effect（copper_fire_flame の挿入で+1）
        assertEquals(6, RegistryRemapper.mapParticle772To773(5)); // 挿入点の直後
        assertEquals(114, RegistryRemapper.mapParticle772To773(113)); // firefly
        assertEquals(1, RegistryRemapper.mapBlock772To773(1)); // stone
        assertEquals(1165, RegistryRemapper.mapBlock772To773(1104)); // 末尾
        assertEquals(12, RegistryRemapper.mapMenu772To773(12)); // crafting
        assertEquals(0, RegistryRemapper.mapEffect772To773(0)); // Speed
        assertEquals(38, RegistryRemapper.mapEffect772To773(38)); // Infested
    }

    @Test
    void blockEntityForward() {
        // shelf（40）の挿入で 40 以降が+1。copper_golem_statue は 48 に追加。
        assertEquals(9, RegistryRemapper.mapBlockEntityType772To773(9)); // mob_spawner
        assertEquals(10, RegistryRemapper.mapBlockEntityType772To773(10)); // creaking_heart
        assertEquals(11, RegistryRemapper.mapBlockEntityType772To773(11)); // piston
        assertEquals(44, RegistryRemapper.mapBlockEntityType772To773(43)); // trial_spawner
        assertEquals(45, RegistryRemapper.mapBlockEntityType772To773(44)); // vault
        assertEquals(46, RegistryRemapper.mapBlockEntityType772To773(45)); // test_block
        assertEquals(47, RegistryRemapper.mapBlockEntityType772To773(46)); // test_instance_block
        assertEquals(-1, RegistryRemapper.mapBlockEntityType772To773(47)); // 範囲外は DROP
    }

    @Test
    void soundNamesForward() {
        // 772→773 の音の名前の表は空（772 の音はすべてそのまま通る）。
        assertEquals("block.stone.break",
                RegistryRemapper.mapSoundName772To773("block.stone.break"));
        assertEquals("music_disc.mall",
                RegistryRemapper.mapSoundName772To773("music_disc.mall"));
        assertEquals("entity.puffer_fish.blow_out",
                RegistryRemapper.mapSoundName772To773("entity.puffer_fish.blow_out"));
    }

    @Test
    void attributesForward() {
        // 772と773の属性の並びは同一（恒等写像）。
        assertEquals(0, RegistryRemapper.mapAttribute772To773(0)); // generic.armor
        assertEquals(8, RegistryRemapper.mapAttribute772To773(8)); // camera_distance
        assertEquals(34, RegistryRemapper.mapAttribute772To773(34)); // waypoint_receive_range
        assertEquals("camera_distance", RegistryRemapper.attributeName773(8));
        assertEquals("waypoint_transmit_range", RegistryRemapper.attributeName773(33));
        assertEquals("waypoint_receive_range", RegistryRemapper.attributeName773(34));
        assertEquals(8, RegistryRemapper.attributeId773("camera_distance"));
        assertEquals(33, RegistryRemapper.attributeId773("waypoint_transmit_range"));
        assertEquals(34, RegistryRemapper.attributeId773("waypoint_receive_range"));
        assertEquals(-1, RegistryRemapper.attributeId773("not_an_attribute"));
        assertEquals(8, RegistryRemapper.attributeId773("minecraft:camera_distance"));
    }

    @Test
    void enchantmentsForward() {
        // 772と773のエンチャントの並びは同一（恒等写像）。
        assertEquals(0, RegistryRemapper.mapEnchantment772To773(0)); // aqua_affinity
        assertEquals(27, RegistryRemapper.mapEnchantment772To773(27)); // protection
        assertEquals(41, RegistryRemapper.mapEnchantment772To773(41)); // wind_burst
        assertEquals(0, RegistryRemapper.mapEnchantment773To772(0));
        assertEquals(27, RegistryRemapper.mapEnchantment773To772(27));
        assertEquals(41, RegistryRemapper.mapEnchantment773To772(41));
        assertEquals(-1, RegistryRemapper.mapEnchantment772To773(999)); // 範囲外は DROP
        assertEquals(-1, RegistryRemapper.mapEnchantment773To772(999));
    }

    @Test
    void damageTypesPaintingsAndJukeboxSongs() {
        // ダメージ種別は772と773で49種・同じ並び。
        assertEquals(0, RegistryRemapper.mapDamageType772To773(0));
        assertEquals(48, RegistryRemapper.mapDamageType772To773(48));
        assertEquals(0, RegistryRemapper.mapDamageType773To772(0));
        assertEquals(48, RegistryRemapper.mapDamageType773To772(48));
        assertEquals(-1, RegistryRemapper.mapDamageType772To773(49)); // 範囲外は DROP
        assertEquals(-1, RegistryRemapper.mapDamageType773To772(49));
        // 絵画バリアントは772と773で51件・同じ並び（dennis=14、wither=50）。
        assertEquals(12, RegistryRemapper.mapPaintingVariant772To773(12)); // courbet
        assertEquals(13, RegistryRemapper.mapPaintingVariant772To773(13)); // creebet
        assertEquals(14, RegistryRemapper.mapPaintingVariant772To773(14)); // dennis
        assertEquals(50, RegistryRemapper.mapPaintingVariant772To773(50)); // wither
        assertEquals(-1, RegistryRemapper.mapPaintingVariant772To773(51)); // 範囲外は DROP
        assertEquals(14, RegistryRemapper.mapPaintingVariant773To772(14)); // dennis
        assertEquals(50, RegistryRemapper.mapPaintingVariant773To772(50)); // wither
        assertEquals(-1, RegistryRemapper.mapPaintingVariant773To772(51)); // 範囲外は DROP
        // ジュークボックスの歌は772と773で21件・同じ並び（lava_chicken=9）。
        assertEquals(8, RegistryRemapper.mapJukeboxSong772To773(8)); // far
        assertEquals(9, RegistryRemapper.mapJukeboxSong772To773(9)); // lava_chicken
        assertEquals(10, RegistryRemapper.mapJukeboxSong772To773(10)); // mall
        assertEquals(20, RegistryRemapper.mapJukeboxSong772To773(20)); // ward
        assertEquals(-1, RegistryRemapper.mapJukeboxSong772To773(21)); // 範囲外は DROP
        assertEquals(9, RegistryRemapper.mapJukeboxSong773To772(9)); // lava_chicken
        assertEquals(10, RegistryRemapper.mapJukeboxSong773To772(10)); // mall
        assertEquals(20, RegistryRemapper.mapJukeboxSong773To772(20)); // ward
        assertEquals(-1, RegistryRemapper.mapJukeboxSong773To772(21)); // 範囲外は DROP
        assertEquals("lava_chicken", RegistryRemapper.jukeboxSongName773(9));
        assertEquals("mall", RegistryRemapper.jukeboxSongName773(10));
    }

    // ---- 773 → 772（invert）----

    @Test
    void blockStatesUp() {
        assertEquals(0, RegistryRemapper.mapBlockStateUp773(0));
        assertEquals(27945, RegistryRemapper.mapBlockStateUp773(29670)); // 末尾
        assertEquals(1, RegistryRemapper.mapBlockStateUp773(99999)); // 範囲外は石
    }

    @Test
    void itemsUp() {
        // 773で新規のアイテム（銅の道具・防具など）は石へ。chain→iron_chain は改名で写る。
        assertEquals(1, RegistryRemapper.mapItemUp773(916)); // copper_sword
        assertEquals(1, RegistryRemapper.mapItemUp773(958)); // copper_helmet
        assertEquals(378, RegistryRemapper.mapItemUp773(399)); // iron_chain → chain
        assertEquals(1257, RegistryRemapper.mapItemUp773(1305)); // mall
        assertEquals(1415, RegistryRemapper.mapItemUp773(1487)); // 末尾
        assertEquals(1, RegistryRemapper.mapItemUp773(99999)); // 範囲外は石
    }

    @Test
    void itemReverse773KeepsUnknownIds() {
        assertEquals(1, RegistryRemapper.mapItemReverse773(916)); // copper_sword → 石
        assertEquals(378, RegistryRemapper.mapItemReverse773(399)); // iron_chain → chain
        assertEquals(1415, RegistryRemapper.mapItemReverse773(1487));
        assertEquals(9999, RegistryRemapper.mapItemReverse773(9999)); // 範囲外はそのまま
    }

    @Test
    void entitiesSoundsParticlesUp() {
        assertEquals(95, RegistryRemapper.mapEntityTypeUp773(97)); // pig（773 の 97 → 772 の 95）
        assertEquals(93, RegistryRemapper.mapEntityTypeUp773(95)); // parrot（773 の 95 → 772 の 93）
        assertEquals(150, RegistryRemapper.mapEntityTypeUp773(152));
        assertEquals(95, RegistryRemapper.mapEntityTypeUp773(99999)); // 範囲外はブタ（772 のブタ）
        assertEquals(940, RegistryRemapper.mapSoundUp773(971)); // mall
        assertEquals(1442, RegistryRemapper.mapSoundUp773(1484)); // block.stone.break
        assertEquals(1727, RegistryRemapper.mapSoundUp773(1770)); // 末尾
        assertEquals(0, RegistryRemapper.mapSoundUp773(0)); // 変わらない音
        assertEquals(1442, RegistryRemapper.mapSoundUp773(365)); // copper_golem.hurt → 石の破壊音
        assertEquals(1442, RegistryRemapper.mapSoundUp773(99999)); // 範囲外は石の破壊音
        assertEquals(20, RegistryRemapper.mapParticleUp773(21)); // entity_effect
        assertEquals(113, RegistryRemapper.mapParticleUp773(114)); // firefly
        assertEquals(1, RegistryRemapper.mapBlockUp773(1)); // stone
        assertEquals(1104, RegistryRemapper.mapBlockUp773(1165));
        assertEquals(12, RegistryRemapper.mapMenuUp773(12)); // crafting
        assertEquals(0, RegistryRemapper.mapEffectUp773(0)); // Speed
        assertEquals(38, RegistryRemapper.mapEffectUp773(38)); // Infested
    }

    @Test
    void blockEntityUp() {
        assertEquals(10, RegistryRemapper.mapBlockEntityTypeUp773(10)); // creaking_heart
        assertEquals(11, RegistryRemapper.mapBlockEntityTypeUp773(11)); // piston
        assertEquals(-1, RegistryRemapper.mapBlockEntityTypeUp773(40)); // shelf → DROP
        assertEquals(43, RegistryRemapper.mapBlockEntityTypeUp773(44)); // trial_spawner
        assertEquals(44, RegistryRemapper.mapBlockEntityTypeUp773(45)); // vault
        assertEquals(45, RegistryRemapper.mapBlockEntityTypeUp773(46)); // test_block
        assertEquals(46, RegistryRemapper.mapBlockEntityTypeUp773(47)); // test_instance_block
        assertEquals(-1, RegistryRemapper.mapBlockEntityTypeUp773(48)); // copper_golem_statue → DROP
        assertEquals(-1, RegistryRemapper.mapBlockEntityTypeUp773(49)); // 範囲外は DROP
    }

    @Test
    void soundNamesUp() {
        assertEquals("block.stone.break",
                RegistryRemapper.mapSoundNameUp773("block.stone.break"));
        // 773にしかない銅ゴーレムの音は772向けに石の破壊音へ。
        assertEquals("block.stone.break",
                RegistryRemapper.mapSoundNameUp773("entity.copper_golem.hurt"));
        assertEquals("entity.puffer_fish.blow_out",
                RegistryRemapper.mapSoundNameUp773("entity.puffer_fish.blow_out"));
    }

    @Test
    void attributesUp() {
        // 772と773の属性の並びは同一（恒等写像）。
        assertEquals(0, RegistryRemapper.mapAttribute773To772(0)); // generic.armor
        assertEquals(8, RegistryRemapper.mapAttribute773To772(8)); // camera_distance
        assertEquals(34, RegistryRemapper.mapAttribute773To772(34)); // waypoint_receive_range
    }

    // ---- 往復・網羅 ----

    @Test
    void roundTrip772To773To772() {
        for (int i = 0; i <= RegistryRemapper.MAX_BLOCK_STATE_772; i++) {
            assertEquals(i, RegistryRemapper.mapBlockStateUp773(
                    RegistryRemapper.mapBlockState772To773(i)), "blockstate " + i);
        }
        for (int i = 0; i <= RegistryRemapper.MAX_ITEM_772; i++) {
            assertEquals(i, RegistryRemapper.mapItemUp773(
                    RegistryRemapper.mapItem772To773(i)), "item " + i);
        }
        for (int i = 0; i <= RegistryRemapper.MAX_ENTITY_TYPE_772; i++) {
            assertEquals(i, RegistryRemapper.mapEntityTypeUp773(
                    RegistryRemapper.mapEntityType772To773(i)), "entity " + i);
        }
        for (int i = 0; i <= RegistryRemapper.MAX_SOUND_772; i++) {
            assertEquals(i, RegistryRemapper.mapSoundUp773(
                    RegistryRemapper.mapSound772To773(i)), "sound " + i);
        }
        for (int i = 0; i <= RegistryRemapper.MAX_PARTICLE_772; i++) {
            assertEquals(i, RegistryRemapper.mapParticleUp773(
                    RegistryRemapper.mapParticle772To773(i)), "particle " + i);
        }
        for (int i = 0; i <= RegistryRemapper.MAX_BLOCK_772; i++) {
            assertEquals(i, RegistryRemapper.mapBlockUp773(
                    RegistryRemapper.mapBlock772To773(i)), "block " + i);
        }
        for (int i = 0; i <= RegistryRemapper.MAX_MENU_772; i++) {
            assertEquals(i, RegistryRemapper.mapMenuUp773(
                    RegistryRemapper.mapMenu772To773(i)), "menu " + i);
        }
    }

    @Test
    void exhaustiveForwardStayInRange() {
        for (int i = 0; i <= RegistryRemapper.MAX_BLOCK_STATE_772; i++) {
            int mapped = RegistryRemapper.mapBlockState772To773(i);
            assertTrue(mapped >= 0 && mapped <= RegistryRemapper.MAX_BLOCK_STATE_773,
                    "772 blockstate " + i + " -> " + mapped);
        }
        for (int i = 0; i <= RegistryRemapper.MAX_ITEM_772; i++) {
            int mapped = RegistryRemapper.mapItem772To773(i);
            assertTrue(mapped >= 0 && mapped <= RegistryRemapper.MAX_ITEM_773,
                    "772 item " + i + " -> " + mapped);
        }
        for (int i = 0; i <= RegistryRemapper.MAX_SOUND_772; i++) {
            int mapped = RegistryRemapper.mapSound772To773(i);
            assertTrue(mapped >= 0 && mapped <= RegistryRemapper.MAX_SOUND_773,
                    "772 sound " + i + " -> " + mapped);
        }
        for (int i = 0; i <= RegistryRemapper.MAX_PARTICLE_772; i++) {
            int mapped = RegistryRemapper.mapParticle772To773(i);
            assertTrue(mapped >= 0 && mapped <= RegistryRemapper.MAX_PARTICLE_773,
                    "772 particle " + i + " -> " + mapped);
        }
    }

    // ---- 合成表 763↔773 / 765↔773 / 771↔773 ----

    @Test
    void composed763To773() {
        // 763→772 の値に772→773の付け替えを重ねたもの。
        assertEquals(888, RegistryRemapper.mapItem763To773(757)); // scute → turtle_scute
        assertEquals(87, RegistryRemapper.mapEntityType763To773(9)); // boat → oak_boat
        assertEquals(88, RegistryRemapper.mapEntityType763To773(13)); // chest_boat → oak_chest_boat
        assertEquals(1484, RegistryRemapper.mapSound763To773(1264)); // block.stone.break
        assertEquals(0, RegistryRemapper.mapEffect763To773(1)); // Speed（1ベース→0ベース）
        assertEquals(32, RegistryRemapper.mapEffect763To773(33)); // Unluck
        assertEquals(9, RegistryRemapper.mapBlockEntityType763To773(9)); // mob_spawner
        assertEquals(11, RegistryRemapper.mapBlockEntityType763To773(10)); // piston
        assertEquals("block.stone.break",
                RegistryRemapper.mapSoundName763To773("block.stone.break"));
    }

    @Test
    void composed765To773AndBack() {
        assertEquals(888, RegistryRemapper.mapItem765To773(794)); // scute → turtle_scute
        assertEquals(1484, RegistryRemapper.mapSound765To773(1312)); // block.stone.break
        assertEquals(794, RegistryRemapper.mapItem773To765(888)); // turtle_scute → scute
        assertEquals(1, RegistryRemapper.mapItem773To765(916)); // copper_sword → 石
        assertEquals(0, RegistryRemapper.mapEffect765To773(0)); // Speed
        assertEquals(44, RegistryRemapper.mapBlockEntityType765To773(42)); // trial_spawner
        assertEquals("block.trial_spawner.ominous_activate",
                RegistryRemapper.mapSoundName765To773("block.trial_spawner.charge_activate"));
        assertEquals("block.stone.break",
                RegistryRemapper.mapSoundName773To765("music_disc.creator"));
    }

    @Test
    void composed771To773AndBack() {
        assertEquals(1487, RegistryRemapper.mapItem771To773(1414)); // 末尾
        assertEquals(1770, RegistryRemapper.mapSound771To773(1726)); // 末尾
        assertEquals(1484, RegistryRemapper.mapSound771To773(1441)); // block.stone.break
        assertEquals(1414, RegistryRemapper.mapItem773To771(1487)); // 末尾
        assertEquals(1726, RegistryRemapper.mapSound773To771(1770)); // 末尾（item.saddle.unequip）
        assertEquals(1441, RegistryRemapper.mapSound773To771(1484)); // block.stone.break
        assertEquals(1, RegistryRemapper.mapItem773To771(916)); // copper_sword → 石
        assertEquals(47, RegistryRemapper.mapBlockEntityType771To773(46)); // test_instance_block
        assertEquals(46, RegistryRemapper.mapBlockEntityType773To771(47)); // test_instance_block
    }

    @Test
    void protocolDispatchRoutes773() {
        // プロトコル指定のオーバーロードが 773 を 773表へ振ること。
        assertEquals(29461, RegistryRemapper.mapBlockState(26638, 773));
        assertEquals(888, RegistryRemapper.mapItem(794, 773));
        assertEquals(97, RegistryRemapper.mapEntityType(73, 773));
        assertEquals(1484, RegistryRemapper.mapSound(1312, 773));
        // 765のパーティクル 0（ambient_entity_effect）は 773 では
        // entity_effect（21）になる（copper_fire_flame の挿入で+1）。
        assertEquals(21, RegistryRemapper.mapParticle(0, 773));
        assertEquals(1, RegistryRemapper.mapBlock(1, 773));
        assertEquals(12, RegistryRemapper.mapMenu(12, 773));
        assertEquals(0, RegistryRemapper.mapEffect(0, 773));
        assertEquals(44, RegistryRemapper.mapBlockEntityType(42, 773));
        assertEquals("block.trial_spawner.ominous_activate",
                RegistryRemapper.mapSoundName("block.trial_spawner.charge_activate", 773));
        // 773クライアントのアイテムID → 765サーバーのアイテムID。
        assertEquals(794, RegistryRemapper.mapItemReverse(888, 773));
        assertEquals(1, RegistryRemapper.mapItemReverse(916, 773));
        // 772の振り分けが変わっていないこと。
        assertEquals(27736, RegistryRemapper.mapBlockState(26638, 772));
        assertEquals(852, RegistryRemapper.mapItem(794, 772));
    }
}
