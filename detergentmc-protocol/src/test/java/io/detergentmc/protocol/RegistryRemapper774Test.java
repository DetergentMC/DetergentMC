package io.detergentmc.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * {@link RegistryRemapper} の774（1.21.11）向けスポットチェックと往復テスト。
 * 期待値は minecraft-data のレジストリから計算したもの（推測ではない）。
 * 生成スクリプト（tools/generate-remap-tables.py --target 774）の
 * maps774.json／maps_composed_774.json と同じ値。
 * 検証は tools/verify-774-tables.py を参照。
 *
 * <p>773→774 の差分：ブロック状態 29,671件同士で並び同一、ブロック 1,166件同士で
 * 並び同一、アイテム 1,488→1,505（+17。nautilus／camel_husk／parched／
 * zombie_nautilus のスポーンエッグ4件、netherite_horse_armor 1件、spear 7件、
 * nautilus の防具5件）、エンティティ 153→157（+4。camel_husk が 20、
 * nautilus が 88、parched が 97、zombie_nautilus が 152 に挿入され、
 * それ以降がずれる）、音 1,771→1,838（+67。nautilus／camel_husk／parched／
 * zombie_nautilus／spear 関連など）、パーティクル 115件同士で並び同一、
 * 属性 35件同士で並び同一、エンチャント 42→43（lunge が 21 に挿入され、
 * lure 以降が+1ずれる）、エフェクト 39→40（BreathOfTheNautilus を 39 に追加）、
 * ダメージ種別 49→50（spear を 37 に挿入）、バイオーム 65件同士で並び同一、
 * データコンポーネント 96→104（+8）、ブロックエンティティ 49件同士で並び同一、
 * 絵画バリアント 51件同士で並び同一、ジュークボックスの歌 21件同士で並び同一。
 *
 * <p>既知の注意点：パーティクルの代替先定数 {@code ENTITY_EFFECT_PARTICLE_*}
 * は 766 以降すべて 20 だが、ViaVersion／minecraft-data では entity_effect は
 * 21（20 は end_rod）。773 までと同じく 774 の代替先も 20 のままで、
 * 隣接表は恒等写像のためこのテストの範囲では影響しない（別途要調査）。
 */
class RegistryRemapper774Test {

    // ---- 773 → 774 ----

    @Test
    void blockStatesForward() {
        assertEquals(0, RegistryRemapper.mapBlockState773To774(0)); // air
        assertEquals(1, RegistryRemapper.mapBlockState773To774(1)); // stone
        assertEquals(29670, RegistryRemapper.mapBlockState773To774(29670)); // 末尾
        assertEquals(1, RegistryRemapper.mapBlockState773To774(99999)); // 範囲外は石
    }

    @Test
    void itemsForward() {
        assertEquals(0, RegistryRemapper.mapItem773To774(0)); // air
        assertEquals(1, RegistryRemapper.mapItem773To774(1)); // stone
        assertEquals(1315, RegistryRemapper.mapItem773To774(1303)); // music_disc_far
        assertEquals(1317, RegistryRemapper.mapItem773To774(1305)); // music_disc_mall
        assertEquals(1504, RegistryRemapper.mapItem773To774(1487)); // 末尾
        assertEquals(1, RegistryRemapper.mapItem773To774(99999)); // 範囲外は石
    }

    @Test
    void entitiesSoundsParticlesBlocksMenusForward() {
        // エンティティ・音は挿入でずれる。ブロック状態・ブロック・パーティクル・メニュー・エフェクトは同一。
        assertEquals(0, RegistryRemapper.mapEntityType773To774(0)); // acacia_boat
        assertEquals(89, RegistryRemapper.mapEntityType773To774(87)); // oak_boat（camel_husk・nautilus の挿入で+2）
        assertEquals(100, RegistryRemapper.mapEntityType773To774(97)); // pig（+3）
        assertEquals(156, RegistryRemapper.mapEntityType773To774(152)); // 末尾
        assertEquals(100, RegistryRemapper.mapEntityType773To774(99999)); // 範囲外はブタ
        assertEquals(0, RegistryRemapper.mapSound773To774(0));
        assertEquals(994, RegistryRemapper.mapSound773To774(969)); // music_disc.far
        assertEquals(996, RegistryRemapper.mapSound773To774(971)); // music_disc.mall
        assertEquals(1535, RegistryRemapper.mapSound773To774(1484)); // block.stone.break
        assertEquals(1835, RegistryRemapper.mapSound773To774(1770)); // 末尾（item.saddle.unequip。1836・1837 は 774 の新音）
        assertEquals(1535, RegistryRemapper.mapSound773To774(99999)); // 範囲外は石の破壊音
        assertEquals(20, RegistryRemapper.mapParticle773To774(20)); // entity_effect（恒等）
        assertEquals(21, RegistryRemapper.mapParticle773To774(21));
        assertEquals(114, RegistryRemapper.mapParticle773To774(114)); // firefly
        assertEquals(1, RegistryRemapper.mapBlock773To774(1)); // stone
        assertEquals(1165, RegistryRemapper.mapBlock773To774(1165)); // 末尾
        assertEquals(12, RegistryRemapper.mapMenu773To774(12)); // crafting
        assertEquals(0, RegistryRemapper.mapEffect773To774(0)); // Speed
        assertEquals(38, RegistryRemapper.mapEffect773To774(38)); // Infested
    }

    @Test
    void blockEntityForward() {
        // 773と774のブロックエンティティの並びは同一（恒等写像）。
        assertEquals(9, RegistryRemapper.mapBlockEntityType773To774(9)); // mob_spawner
        assertEquals(10, RegistryRemapper.mapBlockEntityType773To774(10)); // creaking_heart
        assertEquals(44, RegistryRemapper.mapBlockEntityType773To774(44)); // trial_spawner
        assertEquals(45, RegistryRemapper.mapBlockEntityType773To774(45)); // vault
        assertEquals(48, RegistryRemapper.mapBlockEntityType773To774(48)); // copper_golem_statue
        assertEquals(-1, RegistryRemapper.mapBlockEntityType773To774(49)); // 範囲外は DROP
    }

    @Test
    void soundNamesForward() {
        // 773→774 の音の名前の表は空（773 の音はすべてそのまま通る）。
        assertEquals("block.stone.break",
                RegistryRemapper.mapSoundName773To774("block.stone.break"));
        assertEquals("music_disc.mall",
                RegistryRemapper.mapSoundName773To774("music_disc.mall"));
        assertEquals("entity.puffer_fish.blow_out",
                RegistryRemapper.mapSoundName773To774("entity.puffer_fish.blow_out"));
    }

    @Test
    void attributesForward() {
        // 773と774の属性の並びは同一（恒等写像）。
        assertEquals(0, RegistryRemapper.mapAttribute773To774(0)); // generic.armor
        assertEquals(8, RegistryRemapper.mapAttribute773To774(8)); // camera_distance
        assertEquals(34, RegistryRemapper.mapAttribute773To774(34)); // waypoint_receive_range
        assertEquals("camera_distance", RegistryRemapper.attributeName774(8));
        assertEquals("waypoint_transmit_range", RegistryRemapper.attributeName774(33));
        assertEquals("waypoint_receive_range", RegistryRemapper.attributeName774(34));
        assertEquals(8, RegistryRemapper.attributeId774("camera_distance"));
        assertEquals(33, RegistryRemapper.attributeId774("waypoint_transmit_range"));
        assertEquals(34, RegistryRemapper.attributeId774("waypoint_receive_range"));
        assertEquals(-1, RegistryRemapper.attributeId774("not_an_attribute"));
        assertEquals(8, RegistryRemapper.attributeId774("minecraft:camera_distance"));
    }

    @Test
    void enchantmentsForward() {
        // lunge が 21 に挿入され、lure（21）以降が+1ずれる。
        assertEquals(0, RegistryRemapper.mapEnchantment773To774(0)); // aqua_affinity
        assertEquals(20, RegistryRemapper.mapEnchantment773To774(20)); // luck_of_the_sea
        assertEquals(22, RegistryRemapper.mapEnchantment773To774(21)); // lure
        assertEquals(28, RegistryRemapper.mapEnchantment773To774(27)); // protection
        assertEquals(42, RegistryRemapper.mapEnchantment773To774(41)); // wind_burst
        assertEquals(-1, RegistryRemapper.mapEnchantment773To774(999)); // 範囲外は DROP
        assertEquals(0, RegistryRemapper.mapEnchantment774To773(0));
        assertEquals(-1, RegistryRemapper.mapEnchantment774To773(21)); // lunge → DROP
        assertEquals(21, RegistryRemapper.mapEnchantment774To773(22)); // lure
        assertEquals(27, RegistryRemapper.mapEnchantment774To773(28)); // protection
        assertEquals(41, RegistryRemapper.mapEnchantment774To773(42)); // wind_burst
        assertEquals(-1, RegistryRemapper.mapEnchantment774To773(999));
    }

    @Test
    void damageTypesPaintingsAndJukeboxSongs() {
        // ダメージ種別は spear が 37 に挿入され、37 以降が+1ずれる。
        assertEquals(0, RegistryRemapper.mapDamageType773To774(0));
        assertEquals(36, RegistryRemapper.mapDamageType773To774(36)); // sonic_boom
        assertEquals(38, RegistryRemapper.mapDamageType773To774(37)); // spit
        assertEquals(49, RegistryRemapper.mapDamageType773To774(48)); // wither_skull
        assertEquals(-1, RegistryRemapper.mapDamageType773To774(49)); // 範囲外は DROP
        assertEquals(0, RegistryRemapper.mapDamageType774To773(0));
        assertEquals(36, RegistryRemapper.mapDamageType774To773(36)); // sonic_boom
        assertEquals(-1, RegistryRemapper.mapDamageType774To773(37)); // spear → DROP
        assertEquals(37, RegistryRemapper.mapDamageType774To773(38)); // spit
        assertEquals(48, RegistryRemapper.mapDamageType774To773(49)); // wither_skull
        assertEquals(-1, RegistryRemapper.mapDamageType774To773(50));
        // 絵画バリアントは773と774で51件・同じ並び（dennis=14、wither=50）。
        assertEquals(12, RegistryRemapper.mapPaintingVariant773To774(12)); // courbet
        assertEquals(13, RegistryRemapper.mapPaintingVariant773To774(13)); // creebet
        assertEquals(14, RegistryRemapper.mapPaintingVariant773To774(14)); // dennis
        assertEquals(50, RegistryRemapper.mapPaintingVariant773To774(50)); // wither
        assertEquals(-1, RegistryRemapper.mapPaintingVariant773To774(51)); // 範囲外は DROP
        assertEquals(14, RegistryRemapper.mapPaintingVariant774To773(14)); // dennis
        assertEquals(50, RegistryRemapper.mapPaintingVariant774To773(50)); // wither
        assertEquals(-1, RegistryRemapper.mapPaintingVariant774To773(51)); // 範囲外は DROP
        // ジュークボックスの歌は773と774で21件・同じ並び（lava_chicken=9）。
        assertEquals(8, RegistryRemapper.mapJukeboxSong773To774(8)); // far
        assertEquals(9, RegistryRemapper.mapJukeboxSong773To774(9)); // lava_chicken
        assertEquals(10, RegistryRemapper.mapJukeboxSong773To774(10)); // mall
        assertEquals(20, RegistryRemapper.mapJukeboxSong773To774(20)); // ward
        assertEquals(-1, RegistryRemapper.mapJukeboxSong773To774(21)); // 範囲外は DROP
        assertEquals(9, RegistryRemapper.mapJukeboxSong774To773(9)); // lava_chicken
        assertEquals(10, RegistryRemapper.mapJukeboxSong774To773(10)); // mall
        assertEquals(20, RegistryRemapper.mapJukeboxSong774To773(20)); // ward
        assertEquals(-1, RegistryRemapper.mapJukeboxSong774To773(21)); // 範囲外は DROP
        assertEquals("lava_chicken", RegistryRemapper.jukeboxSongName774(9));
        assertEquals("mall", RegistryRemapper.jukeboxSongName774(10));
    }

    // ---- 774 → 773（invert）----

    @Test
    void blockStatesUp() {
        assertEquals(0, RegistryRemapper.mapBlockStateUp774(0));
        assertEquals(29670, RegistryRemapper.mapBlockStateUp774(29670)); // 末尾
        assertEquals(1, RegistryRemapper.mapBlockStateUp774(99999)); // 範囲外は石
    }

    @Test
    void itemsUp() {
        // 774で新規のアイテム（スポーンエッグ・槍・nautilus の防具など）は石へ。
        assertEquals(1, RegistryRemapper.mapItemUp774(1156)); // nautilus_spawn_egg
        assertEquals(1, RegistryRemapper.mapItemUp774(1259)); // netherite_horse_armor
        assertEquals(1, RegistryRemapper.mapItemUp774(1296)); // wooden_spear
        assertEquals(1, RegistryRemapper.mapItemUp774(1333)); // iron nautilus armour
        assertEquals(1303, RegistryRemapper.mapItemUp774(1315)); // music_disc_far
        assertEquals(1487, RegistryRemapper.mapItemUp774(1504)); // 末尾
        assertEquals(1, RegistryRemapper.mapItemUp774(99999)); // 範囲外は石
    }

    @Test
    void itemReverse774KeepsUnknownIds() {
        assertEquals(1, RegistryRemapper.mapItemReverse774(1156)); // nautilus_spawn_egg → 石
        assertEquals(1, RegistryRemapper.mapItemReverse774(1296)); // wooden_spear → 石
        assertEquals(1487, RegistryRemapper.mapItemReverse774(1504));
        assertEquals(9999, RegistryRemapper.mapItemReverse774(9999)); // 範囲外はそのまま
    }

    @Test
    void entitiesSoundsParticlesUp() {
        assertEquals(97, RegistryRemapper.mapEntityTypeUp774(20)); // camel_husk → ブタ
        assertEquals(97, RegistryRemapper.mapEntityTypeUp774(88)); // nautilus → ブタ
        assertEquals(97, RegistryRemapper.mapEntityTypeUp774(97)); // parched → ブタ
        assertEquals(97, RegistryRemapper.mapEntityTypeUp774(100)); // pig
        assertEquals(97, RegistryRemapper.mapEntityTypeUp774(152)); // zombie_nautilus → ブタ
        assertEquals(152, RegistryRemapper.mapEntityTypeUp774(156)); // 末尾
        assertEquals(97, RegistryRemapper.mapEntityTypeUp774(99999)); // 範囲外はブタ（773 のブタ）
        assertEquals(971, RegistryRemapper.mapSoundUp774(996)); // mall
        assertEquals(1484, RegistryRemapper.mapSoundUp774(1535)); // block.stone.break
        assertEquals(1770, RegistryRemapper.mapSoundUp774(1835)); // item.saddle.unequip
        assertEquals(1484, RegistryRemapper.mapSoundUp774(1038)); // entity.nautilus.ambient → 石の破壊音
        assertEquals(1484, RegistryRemapper.mapSoundUp774(1837)); // item.nautilus_saddle_equip → 石の破壊音
        assertEquals(0, RegistryRemapper.mapSoundUp774(0)); // 変わらない音
        assertEquals(1484, RegistryRemapper.mapSoundUp774(99999)); // 範囲外は石の破壊音
        assertEquals(20, RegistryRemapper.mapParticleUp774(20)); // entity_effect（恒等）
        assertEquals(21, RegistryRemapper.mapParticleUp774(21));
        assertEquals(114, RegistryRemapper.mapParticleUp774(114)); // firefly
        assertEquals(1, RegistryRemapper.mapBlockUp774(1)); // stone
        assertEquals(1165, RegistryRemapper.mapBlockUp774(1165));
        assertEquals(12, RegistryRemapper.mapMenuUp774(12)); // crafting
        assertEquals(0, RegistryRemapper.mapEffectUp774(0)); // Speed
        assertEquals(38, RegistryRemapper.mapEffectUp774(38)); // Infested
        assertEquals(0, RegistryRemapper.mapEffectUp774(39)); // BreathOfTheNautilus → Speed
    }

    @Test
    void blockEntityUp() {
        // 773と774のブロックエンティティの並びは同一（恒等写像）。
        assertEquals(10, RegistryRemapper.mapBlockEntityTypeUp774(10)); // creaking_heart
        assertEquals(11, RegistryRemapper.mapBlockEntityTypeUp774(11)); // piston
        assertEquals(44, RegistryRemapper.mapBlockEntityTypeUp774(44)); // trial_spawner
        assertEquals(45, RegistryRemapper.mapBlockEntityTypeUp774(45)); // vault
        assertEquals(48, RegistryRemapper.mapBlockEntityTypeUp774(48)); // copper_golem_statue
        assertEquals(-1, RegistryRemapper.mapBlockEntityTypeUp774(49)); // 範囲外は DROP
    }

    @Test
    void soundNamesUp() {
        assertEquals("block.stone.break",
                RegistryRemapper.mapSoundNameUp774("block.stone.break"));
        // 774にしかない nautilus の音は773向けに石の破壊音へ。
        assertEquals("block.stone.break",
                RegistryRemapper.mapSoundNameUp774("entity.nautilus.ambient"));
        assertEquals("entity.puffer_fish.blow_out",
                RegistryRemapper.mapSoundNameUp774("entity.puffer_fish.blow_out"));
    }

    @Test
    void attributesUp() {
        // 773と774の属性の並びは同一（恒等写像）。
        assertEquals(0, RegistryRemapper.mapAttribute774To773(0)); // generic.armor
        assertEquals(8, RegistryRemapper.mapAttribute774To773(8)); // camera_distance
        assertEquals(34, RegistryRemapper.mapAttribute774To773(34)); // waypoint_receive_range
    }

    // ---- 往復・網羅 ----

    @Test
    void roundTrip773To774To773() {
        for (int i = 0; i <= RegistryRemapper.MAX_BLOCK_STATE_773; i++) {
            assertEquals(i, RegistryRemapper.mapBlockStateUp774(
                    RegistryRemapper.mapBlockState773To774(i)), "blockstate " + i);
        }
        for (int i = 0; i <= RegistryRemapper.MAX_ITEM_773; i++) {
            assertEquals(i, RegistryRemapper.mapItemUp774(
                    RegistryRemapper.mapItem773To774(i)), "item " + i);
        }
        for (int i = 0; i <= RegistryRemapper.MAX_ENTITY_TYPE_773; i++) {
            assertEquals(i, RegistryRemapper.mapEntityTypeUp774(
                    RegistryRemapper.mapEntityType773To774(i)), "entity " + i);
        }
        for (int i = 0; i <= RegistryRemapper.MAX_SOUND_773; i++) {
            assertEquals(i, RegistryRemapper.mapSoundUp774(
                    RegistryRemapper.mapSound773To774(i)), "sound " + i);
        }
        for (int i = 0; i <= RegistryRemapper.MAX_PARTICLE_773; i++) {
            assertEquals(i, RegistryRemapper.mapParticleUp774(
                    RegistryRemapper.mapParticle773To774(i)), "particle " + i);
        }
        for (int i = 0; i <= RegistryRemapper.MAX_BLOCK_773; i++) {
            assertEquals(i, RegistryRemapper.mapBlockUp774(
                    RegistryRemapper.mapBlock773To774(i)), "block " + i);
        }
        for (int i = 0; i <= RegistryRemapper.MAX_MENU_773; i++) {
            assertEquals(i, RegistryRemapper.mapMenuUp774(
                    RegistryRemapper.mapMenu773To774(i)), "menu " + i);
        }
    }

    @Test
    void exhaustiveForwardStayInRange() {
        for (int i = 0; i <= RegistryRemapper.MAX_BLOCK_STATE_773; i++) {
            int mapped = RegistryRemapper.mapBlockState773To774(i);
            assertTrue(mapped >= 0 && mapped <= RegistryRemapper.MAX_BLOCK_STATE_774,
                    "773 blockstate " + i + " -> " + mapped);
        }
        for (int i = 0; i <= RegistryRemapper.MAX_ITEM_773; i++) {
            int mapped = RegistryRemapper.mapItem773To774(i);
            assertTrue(mapped >= 0 && mapped <= RegistryRemapper.MAX_ITEM_774,
                    "773 item " + i + " -> " + mapped);
        }
        for (int i = 0; i <= RegistryRemapper.MAX_SOUND_773; i++) {
            int mapped = RegistryRemapper.mapSound773To774(i);
            assertTrue(mapped >= 0 && mapped <= RegistryRemapper.MAX_SOUND_774,
                    "773 sound " + i + " -> " + mapped);
        }
        for (int i = 0; i <= RegistryRemapper.MAX_PARTICLE_773; i++) {
            int mapped = RegistryRemapper.mapParticle773To774(i);
            assertTrue(mapped >= 0 && mapped <= RegistryRemapper.MAX_PARTICLE_774,
                    "773 particle " + i + " -> " + mapped);
        }
    }

    // ---- 合成表 763↔774 / 765↔774 / 771↔774 / 772↔774 ----

    @Test
    void composed763To774() {
        // 763→773 の値に773→774の付け替えを重ねたもの。
        assertEquals(888, RegistryRemapper.mapItem763To774(757)); // scute → turtle_scute
        assertEquals(89, RegistryRemapper.mapEntityType763To774(9)); // boat → oak_boat
        assertEquals(1535, RegistryRemapper.mapSound763To774(1264)); // block.stone.break
        assertEquals(0, RegistryRemapper.mapEffect763To774(1)); // Speed（1ベース→0ベース）
        assertEquals(32, RegistryRemapper.mapEffect763To774(33)); // Unluck
        assertEquals(9, RegistryRemapper.mapBlockEntityType763To774(9)); // mob_spawner
        assertEquals(11, RegistryRemapper.mapBlockEntityType763To774(10)); // piston
        assertEquals("block.stone.break",
                RegistryRemapper.mapSoundName763To774("block.stone.break"));
    }

    @Test
    void composed765To774AndBack() {
        assertEquals(888, RegistryRemapper.mapItem765To774(794)); // scute → turtle_scute
        assertEquals(1535, RegistryRemapper.mapSound765To774(1312)); // block.stone.break
        assertEquals(794, RegistryRemapper.mapItem774To765(888)); // turtle_scute → scute
        assertEquals(1, RegistryRemapper.mapItem774To765(1156)); // nautilus_spawn_egg → 石
        assertEquals(0, RegistryRemapper.mapEffect765To774(0)); // Speed
        assertEquals(44, RegistryRemapper.mapBlockEntityType765To774(42)); // trial_spawner
        assertEquals("block.trial_spawner.ominous_activate",
                RegistryRemapper.mapSoundName765To774("block.trial_spawner.charge_activate"));
        assertEquals("block.stone.break",
                RegistryRemapper.mapSoundName774To765("music_disc.creator"));
    }

    @Test
    void composed771To774AndBack() {
        assertEquals(1504, RegistryRemapper.mapItem771To774(1414)); // 末尾（ominous_bottle）
        assertEquals(1835, RegistryRemapper.mapSound771To774(1726)); // 末尾（item.saddle.unequip）
        assertEquals(1535, RegistryRemapper.mapSound771To774(1441)); // block.stone.break
        assertEquals(1414, RegistryRemapper.mapItem774To771(1504)); // 末尾
        assertEquals(1726, RegistryRemapper.mapSound774To771(1835)); // 末尾（item.saddle.unequip）
        assertEquals(1441, RegistryRemapper.mapSound774To771(1535)); // block.stone.break
        assertEquals(1441, RegistryRemapper.mapSound774To771(1837)); // item.nautilus_saddle_equip → 石の破壊音
        assertEquals(1, RegistryRemapper.mapItem774To771(1156)); // nautilus_spawn_egg → 石
        assertEquals(47, RegistryRemapper.mapBlockEntityType771To774(46)); // test_instance_block
        assertEquals(46, RegistryRemapper.mapBlockEntityType774To771(47)); // test_instance_block
    }

    @Test
    void composed772To774AndBack() {
        assertEquals(1504, RegistryRemapper.mapItem772To774(1415)); // 末尾
        assertEquals(1835, RegistryRemapper.mapSound772To774(1727)); // 末尾（item.saddle.unequip）
        assertEquals(1535, RegistryRemapper.mapSound772To774(1442)); // block.stone.break
        assertEquals(1415, RegistryRemapper.mapItem774To772(1504)); // 末尾
        assertEquals(1727, RegistryRemapper.mapSound774To772(1835)); // 末尾
        assertEquals(1442, RegistryRemapper.mapSound774To772(1535)); // block.stone.break
        assertEquals(1442, RegistryRemapper.mapSound774To772(1837)); // item.nautilus_saddle_equip → 石の破壊音
        assertEquals(1, RegistryRemapper.mapItem774To772(1156)); // nautilus_spawn_egg → 石
        assertEquals(47, RegistryRemapper.mapBlockEntityType772To774(46)); // test_instance_block
        assertEquals(46, RegistryRemapper.mapBlockEntityType774To772(47)); // test_instance_block
        assertEquals(38, RegistryRemapper.mapEffect772To774(38)); // Infested
        assertEquals(0, RegistryRemapper.mapEffect774To772(39)); // BreathOfTheNautilus → Speed
    }

    @Test
    void protocolDispatchRoutes774() {
        // プロトコル指定のオーバーロードが 774 を 774表へ振ること。
        assertEquals(29461, RegistryRemapper.mapBlockState(26638, 774));
        assertEquals(888, RegistryRemapper.mapItem(794, 774));
        assertEquals(100, RegistryRemapper.mapEntityType(73, 774));
        assertEquals(1535, RegistryRemapper.mapSound(1312, 774));
        // 765のパーティクル 0（ambient_entity_effect）は 774 では
        // entity_effect（21）になる（合成表は名前で写す）。
        assertEquals(21, RegistryRemapper.mapParticle(0, 774));
        assertEquals(1, RegistryRemapper.mapBlock(1, 774));
        assertEquals(12, RegistryRemapper.mapMenu(12, 774));
        assertEquals(0, RegistryRemapper.mapEffect(0, 774));
        assertEquals(44, RegistryRemapper.mapBlockEntityType(42, 774));
        assertEquals("block.trial_spawner.ominous_activate",
                RegistryRemapper.mapSoundName("block.trial_spawner.charge_activate", 774));
        // 774クライアントのアイテムID → 765サーバーのアイテムID。
        assertEquals(794, RegistryRemapper.mapItemReverse(888, 774));
        assertEquals(1, RegistryRemapper.mapItemReverse(1156, 774));
        // 773の振り分けが変わっていないこと。
        assertEquals(29461, RegistryRemapper.mapBlockState(26638, 773));
        assertEquals(888, RegistryRemapper.mapItem(794, 773)); // 773 の振り分けは 888（852 は 772）
    }
}
