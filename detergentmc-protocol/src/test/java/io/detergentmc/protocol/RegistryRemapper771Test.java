package io.detergentmc.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * {@link RegistryRemapper} の771（1.21.6）向けスポットチェックと往復テスト。
 * 期待値は minecraft-data のレジストリから計算したもの（推測ではない）。
 * 生成スクリプト（tools/generate-remap-tables.py --target 771）の
 * maps771.json／maps_composed_771.json と同じ値。
 * 検証は tools/verify-771-tables.py を参照。
 *
 * <p>770→771 の差分：ブロック状態 27,914→27,946（+32。乾燥ガストの追加。
 * 13826番に挿入され、それ以降が+32ずれる）、アイテム 1,396→1,415（+19。
 * ハーネス・乾燥ガストのスポーンエッグなど）、エンティティ 150→151
 * （ハッピーガスト 56 の追加。56以降が+1ずれる）、音 1,702→1,727
 * （+29／−4。entity.puffer_fish.ambient が削除され blow_out に集約される
 * 名前レベルの衝突あり）、パーティクル 114件同士で並び同一、属性 32→35件
 * （camera_distance が8番に挿入、waypoint_transmit_range／
 * waypoint_receive_range が33・34番に追加）、エンチャント 42件同士で
 * 並び同一、データコンポーネント 96件のまま、バイオーム 65件同士で
 * 並び同一、ダメージ種別 49件同士で並び同一（実バニラ1.21.6のキャプチャで
 * 確認済み）、ジュークボックスの歌 19→20件（tears を17番に挿入）。
 * ブロックエンティティは 47件同士で並び同一。
 */
class RegistryRemapper771Test {

    // ---- 770 → 771 ----

    @Test
    void blockStatesForward() {
        assertEquals(0, RegistryRemapper.mapBlockState770To771(0)); // air
        assertEquals(1, RegistryRemapper.mapBlockState770To771(1)); // stone
        assertEquals(13825, RegistryRemapper.mapBlockState770To771(13825)); // 挿入点の直前
        assertEquals(13858, RegistryRemapper.mapBlockState770To771(13826)); // 乾燥ガストの挿入で+32
        assertEquals(27945, RegistryRemapper.mapBlockState770To771(27913)); // 末尾
        assertEquals(1, RegistryRemapper.mapBlockState770To771(99999)); // 範囲外は石
    }

    @Test
    void itemsForward() {
        assertEquals(0, RegistryRemapper.mapItem770To771(0)); // air
        assertEquals(1, RegistryRemapper.mapItem770To771(1)); // stone
        assertEquals(618, RegistryRemapper.mapItem770To771(618)); // 境界の直前
        assertEquals(620, RegistryRemapper.mapItem770To771(619)); // +1
        assertEquals(801, RegistryRemapper.mapItem770To771(800)); // +1
        assertEquals(818, RegistryRemapper.mapItem770To771(801)); // +17
        assertEquals(1110, RegistryRemapper.mapItem770To771(1093)); // +17
        assertEquals(1112, RegistryRemapper.mapItem770To771(1094)); // +18
        assertEquals(1267, RegistryRemapper.mapItem770To771(1249)); // +18
        assertEquals(1269, RegistryRemapper.mapItem770To771(1250)); // +19
        assertEquals(1414, RegistryRemapper.mapItem770To771(1395)); // 末尾
        assertEquals(1, RegistryRemapper.mapItem770To771(99999)); // 範囲外は石
    }

    @Test
    void entitiesSoundsParticlesBlocksMenusForward() {
        assertEquals(0, RegistryRemapper.mapEntityType770To771(0)); // acacia_boat
        assertEquals(55, RegistryRemapper.mapEntityType770To771(55)); // ghast
        assertEquals(57, RegistryRemapper.mapEntityType770To771(56)); // ハッピーガストの挿入で+1
        assertEquals(95, RegistryRemapper.mapEntityType770To771(94)); // pig
        assertEquals(150, RegistryRemapper.mapEntityType770To771(149)); // 末尾
        assertEquals(95, RegistryRemapper.mapEntityType770To771(99999)); // 範囲外はブタ
        assertEquals(0, RegistryRemapper.mapSound770To771(0));
        assertEquals(460, RegistryRemapper.mapSound770To771(452)); // +8
        assertEquals(840, RegistryRemapper.mapSound770To771(816));
        assertEquals(839, RegistryRemapper.mapSound770To771(817));
        assertEquals(465, RegistryRemapper.mapSound770To771(1231)); // +29／-4のずれ
        // entity.puffer_fish の衝突：770の ambient（1179）と blow_out（1180）が
        // 両方771の blow_out（1204）へ写る（verify-771-tables.py の注記）。
        assertEquals(1204, RegistryRemapper.mapSound770To771(1179)); // ambient
        assertEquals(1204, RegistryRemapper.mapSound770To771(1180)); // blow_out
        assertEquals(1255, RegistryRemapper.mapSound770To771(1232)); // +23
        assertEquals(1725, RegistryRemapper.mapSound770To771(1701)); // 末尾
        assertEquals(1441, RegistryRemapper.mapSound770To771(1417)); // block.stone.break
        assertEquals(1441, RegistryRemapper.mapSound770To771(99999)); // 範囲外は石の破壊音
        assertEquals(20, RegistryRemapper.mapParticle770To771(20)); // entity_effect
        assertEquals(35, RegistryRemapper.mapParticle770To771(35)); // tinted_leaves
        assertEquals(113, RegistryRemapper.mapParticle770To771(113)); // firefly
        assertEquals(1, RegistryRemapper.mapBlock770To771(1)); // stone
        assertEquals(714, RegistryRemapper.mapBlock770To771(714)); // 挿入点の直前
        assertEquals(716, RegistryRemapper.mapBlock770To771(715)); // 乾燥ガストのブロック
        assertEquals(1104, RegistryRemapper.mapBlock770To771(1103)); // 末尾
        assertEquals(12, RegistryRemapper.mapMenu770To771(12)); // crafting
        assertEquals(0, RegistryRemapper.mapEffect770To771(0)); // Speed
        assertEquals(38, RegistryRemapper.mapEffect770To771(38)); // Infested
    }

    @Test
    void blockEntityForward() {
        // 770と771のブロックエンティティの並びは同一（恒等写像）。
        assertEquals(9, RegistryRemapper.mapBlockEntityType770To771(9)); // mob_spawner
        assertEquals(10, RegistryRemapper.mapBlockEntityType770To771(10)); // creaking_heart
        assertEquals(11, RegistryRemapper.mapBlockEntityType770To771(11)); // piston
        assertEquals(43, RegistryRemapper.mapBlockEntityType770To771(43)); // trial_spawner
        assertEquals(44, RegistryRemapper.mapBlockEntityType770To771(44)); // vault
        assertEquals(45, RegistryRemapper.mapBlockEntityType770To771(45)); // test_block
        assertEquals(46, RegistryRemapper.mapBlockEntityType770To771(46)); // test_instance_block
        assertEquals(-1, RegistryRemapper.mapBlockEntityType770To771(47)); // 範囲外は DROP
    }

    @Test
    void soundNamesForward() {
        assertEquals("block.stone.break",
                RegistryRemapper.mapSoundName770To771("block.stone.break"));
        assertEquals("entity.creaking.ambient",
                RegistryRemapper.mapSoundName770To771("entity.creaking.ambient"));
        assertEquals("item.goat_horn.sound.0",
                RegistryRemapper.mapSoundName770To771("item.goat_horn.sound.0"));
        // 771で削除された entity.puffer_fish.ambient は blow_out へ。
        assertEquals("entity.puffer_fish.blow_out",
                RegistryRemapper.mapSoundName770To771("entity.puffer_fish.ambient"));
        // blow_out 自体は771にもあるためそのまま。
        assertEquals("entity.puffer_fish.blow_out",
                RegistryRemapper.mapSoundName770To771("entity.puffer_fish.blow_out"));
        // 771で削除された block.sand.wind は block.dry_grass.ambient へ。
        assertEquals("block.dry_grass.ambient",
                RegistryRemapper.mapSoundName770To771("block.sand.wind"));
    }

    @Test
    void attributesForward() {
        // camera_distance が8番に挿入され、それ以降が+1ずれる。
        assertEquals(0, RegistryRemapper.mapAttribute770To771(0)); // generic.armor
        assertEquals(7, RegistryRemapper.mapAttribute770To771(7)); // burning_time
        assertEquals(9, RegistryRemapper.mapAttribute770To771(8)); // 挿入点以降は+1
        assertEquals(32, RegistryRemapper.mapAttribute770To771(31)); // water_movement_efficiency
        assertEquals("camera_distance", RegistryRemapper.attributeName771(8));
        assertEquals("waypoint_transmit_range", RegistryRemapper.attributeName771(33));
        assertEquals("waypoint_receive_range", RegistryRemapper.attributeName771(34));
        assertEquals(8, RegistryRemapper.attributeId771("camera_distance"));
        assertEquals(33, RegistryRemapper.attributeId771("waypoint_transmit_range"));
        assertEquals(34, RegistryRemapper.attributeId771("waypoint_receive_range"));
        assertEquals(-1, RegistryRemapper.attributeId771("not_an_attribute"));
        // 770 のワイヤ上の名前は接頭辞付き。付いていても同じIDを返す。
        assertEquals(8, RegistryRemapper.attributeId771("minecraft:camera_distance"));
    }

    @Test
    void enchantmentsForward() {
        // 770と771のエンチャントの並びは同一（恒等写像）。
        assertEquals(0, RegistryRemapper.mapEnchantment770To771(0)); // aqua_affinity
        assertEquals(27, RegistryRemapper.mapEnchantment770To771(27)); // protection
        assertEquals(41, RegistryRemapper.mapEnchantment770To771(41)); // wind_burst
        assertEquals(0, RegistryRemapper.mapEnchantment771To770(0));
        assertEquals(27, RegistryRemapper.mapEnchantment771To770(27));
        assertEquals(41, RegistryRemapper.mapEnchantment771To770(41));
        assertEquals(-1, RegistryRemapper.mapEnchantment770To771(999)); // 範囲外は DROP
        assertEquals(-1, RegistryRemapper.mapEnchantment771To770(999));
    }

    @Test
    void damageTypesAndJukeboxSongs() {
        // ダメージ種別は770と771で49種・同じ並び（実バニラ1.21.6のキャプチャで確認済み）。
        assertEquals(0, RegistryRemapper.mapDamageType770To771(0));
        assertEquals(48, RegistryRemapper.mapDamageType770To771(48));
        assertEquals(0, RegistryRemapper.mapDamageType771To770(0));
        assertEquals(48, RegistryRemapper.mapDamageType771To770(48));
        assertEquals(-1, RegistryRemapper.mapDamageType770To771(49)); // 範囲外は DROP
        assertEquals(-1, RegistryRemapper.mapDamageType771To770(49));
        // ジュークボックスの歌は tears を17番に挿入。
        assertEquals(0, RegistryRemapper.mapJukeboxSong770To771(0)); // 11
        assertEquals(16, RegistryRemapper.mapJukeboxSong770To771(16)); // strad
        assertEquals(18, RegistryRemapper.mapJukeboxSong770To771(17)); // wait
        assertEquals(19, RegistryRemapper.mapJukeboxSong770To771(18)); // ward
        assertEquals(-1, RegistryRemapper.mapJukeboxSong770To771(19)); // 範囲外は DROP
        assertEquals(-1, RegistryRemapper.mapJukeboxSong771To770(17)); // tears → DROP
        assertEquals(17, RegistryRemapper.mapJukeboxSong771To770(18)); // wait
        assertEquals(18, RegistryRemapper.mapJukeboxSong771To770(19)); // ward
        assertEquals(-1, RegistryRemapper.mapJukeboxSong771To770(20)); // 範囲外は DROP
    }

    // ---- 771 → 770（invert）----

    @Test
    void blockStatesUp() {
        assertEquals(13826, RegistryRemapper.mapBlockStateUp771(13858)); // 挿入点以降は-32
        assertEquals(27913, RegistryRemapper.mapBlockStateUp771(27945)); // 末尾
        assertEquals(1, RegistryRemapper.mapBlockStateUp771(13826)); // 乾燥ガストの新規状態 → 石
        assertEquals(1, RegistryRemapper.mapBlockStateUp771(13857)); // 乾燥ガストの新規状態 → 石
        assertEquals(1, RegistryRemapper.mapBlockStateUp771(99999)); // 範囲外は石
    }

    @Test
    void itemsUp() {
        // 771で新規のアイテムは石へ。
        assertEquals(1, RegistryRemapper.mapItemUp771(619)); // 新規アイテム
        assertEquals(1, RegistryRemapper.mapItemUp771(802)); // 新規アイテム
        assertEquals(1, RegistryRemapper.mapItemUp771(1111)); // 新規アイテム
        assertEquals(1, RegistryRemapper.mapItemUp771(1268)); // 新規アイテム
        assertEquals(619, RegistryRemapper.mapItemUp771(620));
        assertEquals(801, RegistryRemapper.mapItemUp771(818));
        assertEquals(1395, RegistryRemapper.mapItemUp771(1414));
        assertEquals(1, RegistryRemapper.mapItemUp771(99999)); // 範囲外は石
    }

    @Test
    void itemReverse771KeepsUnknownIds() {
        assertEquals(1, RegistryRemapper.mapItemReverse771(619)); // 新規アイテム → 石
        assertEquals(1395, RegistryRemapper.mapItemReverse771(1414));
        assertEquals(9999, RegistryRemapper.mapItemReverse771(9999)); // 範囲外はそのまま
    }

    @Test
    void entitiesSoundsParticlesUp() {
        assertEquals(94, RegistryRemapper.mapEntityTypeUp771(95)); // pig
        assertEquals(56, RegistryRemapper.mapEntityTypeUp771(57));
        assertEquals(149, RegistryRemapper.mapEntityTypeUp771(150));
        // ハッピーガスト（771: 56）は770ではブタ（94）になる
        // （Spawn Entity の翻訳子ではガスト（55）に代替する。別経路）。
        assertEquals(94, RegistryRemapper.mapEntityTypeUp771(56));
        assertEquals(94, RegistryRemapper.mapEntityTypeUp771(99999)); // 範囲外はブタ
        assertEquals(816, RegistryRemapper.mapSoundUp771(840)); // blow_out → ambient
        assertEquals(817, RegistryRemapper.mapSoundUp771(839));
        assertEquals(1231, RegistryRemapper.mapSoundUp771(465));
        assertEquals(452, RegistryRemapper.mapSoundUp771(460));
        assertEquals(1701, RegistryRemapper.mapSoundUp771(1725));
        assertEquals(0, RegistryRemapper.mapSoundUp771(0)); // 変わらない音
        assertEquals(1417, RegistryRemapper.mapSoundUp771(1441)); // block.stone.break
        assertEquals(1417, RegistryRemapper.mapSoundUp771(99999)); // 範囲外は石の破壊音
        assertEquals(1417, RegistryRemapper.mapSoundUp771(452)); // 771の新規の音 → 石の破壊音
        assertEquals(20, RegistryRemapper.mapParticleUp771(20)); // entity_effect
        assertEquals(35, RegistryRemapper.mapParticleUp771(35)); // tinted_leaves
        assertEquals(113, RegistryRemapper.mapParticleUp771(113)); // firefly
        assertEquals(1, RegistryRemapper.mapBlockUp771(1)); // stone
        assertEquals(1, RegistryRemapper.mapBlockUp771(715)); // 乾燥ガストのブロック → 石
        assertEquals(715, RegistryRemapper.mapBlockUp771(716));
        assertEquals(1103, RegistryRemapper.mapBlockUp771(1104));
        assertEquals(12, RegistryRemapper.mapMenuUp771(12)); // crafting
        assertEquals(0, RegistryRemapper.mapEffectUp771(0)); // Speed
        assertEquals(38, RegistryRemapper.mapEffectUp771(38)); // Infested
    }

    @Test
    void blockEntityUp() {
        assertEquals(10, RegistryRemapper.mapBlockEntityTypeUp771(10)); // creaking_heart
        assertEquals(11, RegistryRemapper.mapBlockEntityTypeUp771(11)); // piston
        assertEquals(43, RegistryRemapper.mapBlockEntityTypeUp771(43)); // trial_spawner
        assertEquals(44, RegistryRemapper.mapBlockEntityTypeUp771(44)); // vault
        assertEquals(45, RegistryRemapper.mapBlockEntityTypeUp771(45)); // test_block
        assertEquals(46, RegistryRemapper.mapBlockEntityTypeUp771(46)); // test_instance_block
        assertEquals(-1, RegistryRemapper.mapBlockEntityTypeUp771(47)); // 範囲外は DROP
    }

    @Test
    void soundNamesUp() {
        assertEquals("item.goat_horn.sound.0",
                RegistryRemapper.mapSoundNameUp771("item.goat_horn.sound.0"));
        // 771にしかない音（ハッピーガスト・乾燥ガスト）は770向けに石の破壊音へ。
        assertEquals("block.stone.break",
                RegistryRemapper.mapSoundNameUp771("entity.happy_ghast.ambient"));
        assertEquals("block.stone.break",
                RegistryRemapper.mapSoundNameUp771("block.dried_ghast.break"));
        // blow_out の逆向きは先行勝ちで ambient が選ばれる（名前レベルの衝突）。
        assertEquals("entity.puffer_fish.ambient",
                RegistryRemapper.mapSoundNameUp771("entity.puffer_fish.blow_out"));
    }

    @Test
    void attributesUp() {
        assertEquals(0, RegistryRemapper.mapAttribute771To770(0)); // generic.armor
        assertEquals(7, RegistryRemapper.mapAttribute771To770(7)); // burning_time
        assertEquals(-1, RegistryRemapper.mapAttribute771To770(8)); // camera_distance → DROP
        assertEquals(8, RegistryRemapper.mapAttribute771To770(9));
        assertEquals(31, RegistryRemapper.mapAttribute771To770(32)); // water_movement_efficiency
        assertEquals(-1, RegistryRemapper.mapAttribute771To770(33)); // waypoint_transmit_range → DROP
        assertEquals(-1, RegistryRemapper.mapAttribute771To770(34)); // waypoint_receive_range → DROP
    }

    // ---- 往復・網羅 ----

    @Test
    void roundTrip770To771To770() {
        for (int i = 0; i <= RegistryRemapper.MAX_BLOCK_STATE_770; i++) {
            assertEquals(i, RegistryRemapper.mapBlockStateUp771(
                    RegistryRemapper.mapBlockState770To771(i)), "blockstate " + i);
        }
        for (int i = 0; i <= RegistryRemapper.MAX_ITEM_770; i++) {
            assertEquals(i, RegistryRemapper.mapItemUp771(
                    RegistryRemapper.mapItem770To771(i)), "item " + i);
        }
        for (int i = 0; i <= RegistryRemapper.MAX_ENTITY_TYPE_770; i++) {
            assertEquals(i, RegistryRemapper.mapEntityTypeUp771(
                    RegistryRemapper.mapEntityType770To771(i)), "entity " + i);
        }
        for (int i = 0; i <= RegistryRemapper.MAX_SOUND_770; i++) {
            // entity.puffer_fish.blow_out（1180）は771の blow_out（1204）に
            // 集約されるため往復しない（ambient（1179）側が選ばれる）。
            if (i == 1180) {
                continue;
            }
            assertEquals(i, RegistryRemapper.mapSoundUp771(
                    RegistryRemapper.mapSound770To771(i)), "sound " + i);
        }
        // blow_out（1180）は往復で ambient（1179）になる（集約のため）。
        assertEquals(1179, RegistryRemapper.mapSoundUp771(
                RegistryRemapper.mapSound770To771(1180)));
        for (int i = 0; i <= RegistryRemapper.MAX_PARTICLE_770; i++) {
            assertEquals(i, RegistryRemapper.mapParticleUp771(
                    RegistryRemapper.mapParticle770To771(i)), "particle " + i);
        }
        for (int i = 0; i <= RegistryRemapper.MAX_BLOCK_770; i++) {
            assertEquals(i, RegistryRemapper.mapBlockUp771(
                    RegistryRemapper.mapBlock770To771(i)), "block " + i);
        }
        for (int i = 0; i <= RegistryRemapper.MAX_MENU_770; i++) {
            assertEquals(i, RegistryRemapper.mapMenuUp771(
                    RegistryRemapper.mapMenu770To771(i)), "menu " + i);
        }
    }

    @Test
    void exhaustiveForwardStayInRange() {
        for (int i = 0; i <= RegistryRemapper.MAX_BLOCK_STATE_770; i++) {
            int mapped = RegistryRemapper.mapBlockState770To771(i);
            assertTrue(mapped >= 0 && mapped <= RegistryRemapper.MAX_BLOCK_STATE_771,
                    "770 blockstate " + i + " -> " + mapped);
        }
        for (int i = 0; i <= RegistryRemapper.MAX_ITEM_770; i++) {
            int mapped = RegistryRemapper.mapItem770To771(i);
            assertTrue(mapped >= 0 && mapped <= RegistryRemapper.MAX_ITEM_771,
                    "770 item " + i + " -> " + mapped);
        }
        for (int i = 0; i <= RegistryRemapper.MAX_SOUND_770; i++) {
            int mapped = RegistryRemapper.mapSound770To771(i);
            assertTrue(mapped >= 0 && mapped <= RegistryRemapper.MAX_SOUND_771,
                    "770 sound " + i + " -> " + mapped);
        }
        for (int i = 0; i <= RegistryRemapper.MAX_PARTICLE_770; i++) {
            int mapped = RegistryRemapper.mapParticle770To771(i);
            assertTrue(mapped >= 0 && mapped <= RegistryRemapper.MAX_PARTICLE_771,
                    "770 particle " + i + " -> " + mapped);
        }
    }

    // ---- 合成表 763↔771 / 765↔771 ----

    @Test
    void composed763To771() {
        // 763→770 の値に770→771の付け替えを重ねたもの。
        assertEquals(852, RegistryRemapper.mapItem763To771(757)); // scute → turtle_scute
        assertEquals(85, RegistryRemapper.mapEntityType763To771(9)); // boat → oak_boat
        assertEquals(86, RegistryRemapper.mapEntityType763To771(13)); // chest_boat → oak_chest_boat
        assertEquals(1441, RegistryRemapper.mapSound763To771(1264)); // block.stone.break
        assertEquals(0, RegistryRemapper.mapEffect763To771(1)); // Speed（1ベース→0ベース）
        assertEquals(32, RegistryRemapper.mapEffect763To771(33)); // Unluck
        assertEquals(9, RegistryRemapper.mapBlockEntityType763To771(9)); // mob_spawner
        assertEquals(11, RegistryRemapper.mapBlockEntityType763To771(10)); // piston
        assertEquals("block.stone.break",
                RegistryRemapper.mapSoundName763To771("block.stone.break"));
    }

    @Test
    void composed765To771AndBack() {
        assertEquals(852, RegistryRemapper.mapItem765To771(794)); // scute → turtle_scute
        assertEquals(1441, RegistryRemapper.mapSound765To771(1312)); // block.stone.break
        assertEquals(794, RegistryRemapper.mapItem771To765(852)); // turtle_scute → scute
        assertEquals(1, RegistryRemapper.mapItem771To765(619)); // 771の新規アイテム → 石
        assertEquals(0, RegistryRemapper.mapEffect765To771(0)); // Speed
        assertEquals(43, RegistryRemapper.mapBlockEntityType765To771(42)); // trial_spawner
        assertEquals("block.trial_spawner.ominous_activate",
                RegistryRemapper.mapSoundName765To771("block.trial_spawner.charge_activate"));
        assertEquals("block.stone.break",
                RegistryRemapper.mapSoundName771To765("music_disc.creator"));
    }

    @Test
    void protocolDispatchRoutes771() {
        // プロトコル指定のオーバーロードが 771 を 771表へ振ること。
        assertEquals(27736, RegistryRemapper.mapBlockState(26638, 771));
        assertEquals(852, RegistryRemapper.mapItem(794, 771));
        assertEquals(95, RegistryRemapper.mapEntityType(73, 771));
        assertEquals(1441, RegistryRemapper.mapSound(1312, 771));
        // 765のパーティクル 0（ambient_entity_effect）は 768・769・770・771 では
        // entity_effect（20）になる（766 で挿入によるずれ）。
        assertEquals(20, RegistryRemapper.mapParticle(0, 771));
        assertEquals(1, RegistryRemapper.mapBlock(1, 771));
        assertEquals(12, RegistryRemapper.mapMenu(12, 771));
        assertEquals(0, RegistryRemapper.mapEffect(0, 771));
        assertEquals(43, RegistryRemapper.mapBlockEntityType(42, 771));
        assertEquals("block.trial_spawner.ominous_activate",
                RegistryRemapper.mapSoundName("block.trial_spawner.charge_activate", 771));
        // 771クライアントのアイテムID → 765サーバーのアイテムID。
        assertEquals(794, RegistryRemapper.mapItemReverse(852, 771));
        assertEquals(1, RegistryRemapper.mapItemReverse(619, 771));
        // 770の振り分けが変わっていないこと。
        assertEquals(27704, RegistryRemapper.mapBlockState(26638, 770));
        assertEquals(835, RegistryRemapper.mapItem(794, 770));
    }
}
