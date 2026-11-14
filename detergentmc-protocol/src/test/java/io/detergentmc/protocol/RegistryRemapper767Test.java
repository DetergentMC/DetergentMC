package io.detergentmc.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * {@link RegistryRemapper} の767（1.21／1.21.1）向けスポットチェックと往復テスト。
 * 期待値は ViaVersion Mappings（mapping-1.20.5.json＝766、
 * mapping-1.21.json＝767）と minecraft-data を名前＋プロパティで突き合わせたもの。
 * 生成スクリプト（tools/generate-remap-tables.py --target 767）の
 * maps767.json／maps_composed_767.json／map_items_rev767.json と同じ値。
 * 検証は tools/verify-767-tables.py を参照。
 *
 * <p>767は766に近く、ブロック状態・ブロック・エンティティ・パーティクル・
 * メニューは同一。アイテムは音楽ディスク3件の挿入で 1172 以降がずれ、
 * 音は改名2件と新6件。属性は新9件（既存の番号がずれる）。エンチャントは
 * 42件同士で並びが大きく変わった（1.21 でデータパック化され
 * アルファベット順になったため。40件が移動）。
 * なお 765→766 のエンチャントの並びは 0〜36 だけ同一で、766では
 * density／breach／wind_burst が 37〜39 に挿入され、mending／
 * vanishing_curse が 40／41 へずれた（765=37→766=40、765=38→766=41）。
 */
class RegistryRemapper767Test {

    // ---- 766 → 767 ----

    @Test
    void blockStatesForward() {
        assertEquals(0, RegistryRemapper.mapBlockState766To767(0)); // air
        assertEquals(1, RegistryRemapper.mapBlockState766To767(1)); // stone
        assertEquals(26638, RegistryRemapper.mapBlockState766To767(26638)); // trial_spawner
        assertEquals(26650, RegistryRemapper.mapBlockState766To767(26650)); // vault
        assertEquals(26683, RegistryRemapper.mapBlockState766To767(26683)); // heavy_core
        assertEquals(1, RegistryRemapper.mapBlockState766To767(99999)); // 範囲外は石
    }

    @Test
    void itemsForward() {
        assertEquals(0, RegistryRemapper.mapItem766To767(0)); // air
        assertEquals(1, RegistryRemapper.mapItem766To767(1)); // stone
        assertEquals(794, RegistryRemapper.mapItem766To767(794)); // turtle_helmet（挿入点より前）
        assertEquals(1171, RegistryRemapper.mapItem766To767(1171)); // 挿入点の直前
        // 音楽ディスク3件の挿入で 1172 以降がずれる。
        assertEquals(1174, RegistryRemapper.mapItem766To767(1172));
        assertEquals(1188, RegistryRemapper.mapItem766To767(1185));
        assertEquals(1189, RegistryRemapper.mapItem766To767(1186));
        assertEquals(1332, RegistryRemapper.mapItem766To767(1329)); // breeze_rod
        assertEquals(1, RegistryRemapper.mapItem766To767(99999)); // 範囲外は石
    }

    @Test
    void entitiesSoundsParticlesBlocksMenusForward() {
        assertEquals(0, RegistryRemapper.mapEntityType766To767(0)); // area_effect_cloud
        assertEquals(77, RegistryRemapper.mapEntityType766To767(77)); // pig
        assertEquals(129, RegistryRemapper.mapEntityType766To767(129)); // fishing_bobber
        assertEquals(77, RegistryRemapper.mapEntityType766To767(99999)); // 範囲外はブタ
        assertEquals(1361, RegistryRemapper.mapSound766To767(1358)); // block.stone.break
        assertEquals(683, RegistryRemapper.mapSound766To767(683)); // charge_activate → ominous_activate（同番号）
        assertEquals(685, RegistryRemapper.mapSound766To767(685)); // ambient_charged → ambient_ominous（同番号）
        assertEquals(889, RegistryRemapper.mapSound766To767(886)); // 3件の挿入で後ろへ
        assertEquals(20, RegistryRemapper.mapParticle766To767(20)); // entity_effect
        assertEquals(1, RegistryRemapper.mapBlock766To767(1)); // stone
        assertEquals(1059, RegistryRemapper.mapBlock766To767(1059)); // heavy_core
        assertEquals(12, RegistryRemapper.mapMenu766To767(12)); // crafting
        assertEquals(0, RegistryRemapper.mapEffect766To767(0)); // Speed
        assertEquals(38, RegistryRemapper.mapEffect766To767(38)); // Infested
    }

    @Test
    void blockEntityForward() {
        assertEquals(42, RegistryRemapper.mapBlockEntityType766To767(42)); // trial_spawner
        assertEquals(43, RegistryRemapper.mapBlockEntityType766To767(43)); // vault
        assertEquals(-1, RegistryRemapper.mapBlockEntityType766To767(44)); // 767にない番号は DROP
    }

    @Test
    void soundNamesForward() {
        assertEquals("block.trial_spawner.ominous_activate",
                RegistryRemapper.mapSoundName766To767("block.trial_spawner.charge_activate"));
        assertEquals("block.trial_spawner.ambient_ominous",
                RegistryRemapper.mapSoundName766To767("block.trial_spawner.ambient_charged"));
        assertEquals("block.stone.break",
                RegistryRemapper.mapSoundName766To767("block.stone.break"));
    }

    @Test
    void attributesForward() {
        assertEquals(0, RegistryRemapper.mapAttribute766To767(0)); // generic.armor
        assertEquals(9, RegistryRemapper.mapAttribute766To767(7)); // player.entity_interaction_range
        assertEquals(21, RegistryRemapper.mapAttribute766To767(17)); // generic.movement_speed
        assertEquals(27, RegistryRemapper.mapAttribute766To767(21)); // generic.step_height
        assertEquals("player.entity_interaction_range", RegistryRemapper.attributeName766(7));
        assertEquals("player.entity_interaction_range", RegistryRemapper.attributeName767(9));
        assertEquals(21, RegistryRemapper.attributeId767("generic.movement_speed"));
        // 765 のワイヤ上の名前は接頭辞付き。付いていても同じIDを返す。
        assertEquals(21, RegistryRemapper.attributeId767("minecraft:generic.movement_speed"));
        assertEquals(-1, RegistryRemapper.attributeId767("not_an_attribute"));
    }

    @Test
    void enchantmentsForward() {
        assertEquals(27, RegistryRemapper.mapEnchantment766To767(0)); // protection
        assertEquals(32, RegistryRemapper.mapEnchantment766To767(13)); // sharpness
        assertEquals(22, RegistryRemapper.mapEnchantment766To767(40)); // mending
        assertEquals(6, RegistryRemapper.mapEnchantment766To767(37)); // density
        assertEquals(41, RegistryRemapper.mapEnchantment766To767(39)); // wind_burst
        assertEquals(0, RegistryRemapper.mapEnchantment766To767(6)); // aqua_affinity
        assertEquals(-1, RegistryRemapper.mapEnchantment766To767(999)); // 範囲外は DROP
    }

    @Test
    void enchantments765Shift() {
        // 765→766 の挿入ずれ（density／breach／wind_burst が 37〜39 に挿入）。
        assertEquals(36, RegistryRemapper.mapEnchantment765To766(36)); // piercing
        assertEquals(40, RegistryRemapper.mapEnchantment765To766(37)); // mending
        assertEquals(41, RegistryRemapper.mapEnchantment765To766(38)); // vanishing_curse
        assertEquals(36, RegistryRemapper.mapEnchantment766To765(36));
        assertEquals(-1, RegistryRemapper.mapEnchantment766To765(37)); // density → DROP
        assertEquals(-1, RegistryRemapper.mapEnchantment766To765(38)); // breach → DROP
        assertEquals(-1, RegistryRemapper.mapEnchantment766To765(39)); // wind_burst → DROP
        assertEquals(37, RegistryRemapper.mapEnchantment766To765(40)); // mending
        assertEquals(38, RegistryRemapper.mapEnchantment766To765(41)); // vanishing_curse
        // 765→767 は 766 を経由する。
        assertEquals(27, RegistryRemapper.mapEnchantment765To767(0)); // protection
        assertEquals(22, RegistryRemapper.mapEnchantment765To767(37)); // mending
        assertEquals(37, RegistryRemapper.mapEnchantment767To765(22)); // mending
        assertEquals(-1, RegistryRemapper.mapEnchantment767To765(6)); // density → DROP
        assertEquals(0, RegistryRemapper.mapEnchantment767To765(27)); // protection
        assertEquals(22, RegistryRemapper.mapEnchantment763To767(37)); // 763も同じ並び
        assertEquals(-1, RegistryRemapper.mapEnchantment767To763(6));
    }

    // ---- 767 → 766（invert）----

    @Test
    void blockStatesUp() {
        assertEquals(26638, RegistryRemapper.mapBlockStateUp767(26638)); // trial_spawner
        assertEquals(26650, RegistryRemapper.mapBlockStateUp767(26650)); // vault
        assertEquals(1, RegistryRemapper.mapBlockStateUp767(99999)); // 範囲外は石
    }

    @Test
    void itemsUp() {
        // 767で新規の音楽ディスク3件は石へ。
        assertEquals(1, RegistryRemapper.mapItemUp767(1172)); // music_disc_creator
        assertEquals(1, RegistryRemapper.mapItemUp767(1173)); // music_disc_creator_music_box
        assertEquals(1, RegistryRemapper.mapItemUp767(1186)); // music_disc_precipice
        assertEquals(1171, RegistryRemapper.mapItemUp767(1171));
        assertEquals(1329, RegistryRemapper.mapItemUp767(1332)); // breeze_rod
        assertEquals(1, RegistryRemapper.mapItemUp767(99999)); // 範囲外は石
    }

    @Test
    void itemReverse767KeepsUnknownIds() {
        assertEquals(1, RegistryRemapper.mapItemReverse767(1172)); // 音楽ディスク → 石
        assertEquals(1171, RegistryRemapper.mapItemReverse767(1171));
        assertEquals(9999, RegistryRemapper.mapItemReverse767(9999)); // 範囲外はそのまま
    }

    @Test
    void entitiesSoundsParticlesUp() {
        assertEquals(77, RegistryRemapper.mapEntityTypeUp767(77)); // pig
        assertEquals(1358, RegistryRemapper.mapSoundUp767(1361)); // block.stone.break
        assertEquals(683, RegistryRemapper.mapSoundUp767(683)); // ominous_activate → charge_activate
        assertEquals(1358, RegistryRemapper.mapSoundUp767(886)); // music_disc.creator → stone.break
        assertEquals(20, RegistryRemapper.mapParticleUp767(20)); // entity_effect
        assertEquals(1, RegistryRemapper.mapBlockUp767(1)); // stone
        assertEquals(12, RegistryRemapper.mapMenuUp767(12)); // crafting
        assertEquals(0, RegistryRemapper.mapEffectUp767(0)); // Speed
        assertEquals(38, RegistryRemapper.mapEffectUp767(38)); // Infested
    }

    @Test
    void blockEntityUp() {
        assertEquals(43, RegistryRemapper.mapBlockEntityTypeUp767(43)); // vault
        assertEquals(-1, RegistryRemapper.mapBlockEntityTypeUp767(44)); // 767にない番号は DROP
    }

    @Test
    void soundNamesUp() {
        assertEquals("block.trial_spawner.charge_activate",
                RegistryRemapper.mapSoundNameUp767("block.trial_spawner.ominous_activate"));
        assertEquals("block.trial_spawner.ambient_charged",
                RegistryRemapper.mapSoundNameUp767("block.trial_spawner.ambient_ominous"));
        assertEquals("block.stone.break",
                RegistryRemapper.mapSoundNameUp767("music_disc.creator"));
        assertEquals("block.stone.break",
                RegistryRemapper.mapSoundNameUp767("block.vault.reject_rewarded_player"));
    }

    @Test
    void attributesUp() {
        assertEquals(0, RegistryRemapper.mapAttribute767To766(0)); // generic.armor
        assertEquals(7, RegistryRemapper.mapAttribute767To766(9)); // entity_interaction_range
        assertEquals(17, RegistryRemapper.mapAttribute767To766(21)); // movement_speed
        assertEquals(-1, RegistryRemapper.mapAttribute767To766(7)); // burning_time（766に無い）
        assertEquals(-1, RegistryRemapper.mapAttribute767To766(30)); // water_movement_efficiency
    }

    // ---- 往復・網羅 ----

    @Test
    void roundTrip766To767To766() {
        for (int i = 0; i <= RegistryRemapper.MAX_BLOCK_STATE_766; i++) {
            assertEquals(i, RegistryRemapper.mapBlockStateUp767(
                    RegistryRemapper.mapBlockState766To767(i)), "blockstate " + i);
        }
        for (int i = 0; i <= RegistryRemapper.MAX_ITEM_766; i++) {
            assertEquals(i, RegistryRemapper.mapItemUp767(
                    RegistryRemapper.mapItem766To767(i)), "item " + i);
        }
        for (int i = 0; i <= RegistryRemapper.MAX_ENTITY_TYPE_766; i++) {
            assertEquals(i, RegistryRemapper.mapEntityTypeUp767(
                    RegistryRemapper.mapEntityType766To767(i)), "entity " + i);
        }
        for (int i = 0; i <= RegistryRemapper.MAX_SOUND_766; i++) {
            assertEquals(i, RegistryRemapper.mapSoundUp767(
                    RegistryRemapper.mapSound766To767(i)), "sound " + i);
        }
        for (int i = 0; i <= RegistryRemapper.MAX_BLOCK_766; i++) {
            assertEquals(i, RegistryRemapper.mapBlockUp767(
                    RegistryRemapper.mapBlock766To767(i)), "block " + i);
        }
        for (int i = 0; i <= RegistryRemapper.MAX_MENU_766; i++) {
            assertEquals(i, RegistryRemapper.mapMenuUp767(
                    RegistryRemapper.mapMenu766To767(i)), "menu " + i);
        }
    }

    @Test
    void exhaustiveForwardStayInRange() {
        for (int i = 0; i <= RegistryRemapper.MAX_BLOCK_STATE_766; i++) {
            int mapped = RegistryRemapper.mapBlockState766To767(i);
            assertTrue(mapped >= 0 && mapped <= RegistryRemapper.MAX_BLOCK_STATE_767,
                    "766 blockstate " + i + " -> " + mapped);
        }
        for (int i = 0; i <= RegistryRemapper.MAX_ITEM_766; i++) {
            int mapped = RegistryRemapper.mapItem766To767(i);
            assertTrue(mapped >= 0 && mapped <= RegistryRemapper.MAX_ITEM_767,
                    "766 item " + i + " -> " + mapped);
        }
        for (int i = 0; i <= RegistryRemapper.MAX_SOUND_766; i++) {
            int mapped = RegistryRemapper.mapSound766To767(i);
            assertTrue(mapped >= 0 && mapped <= RegistryRemapper.MAX_SOUND_767,
                    "766 sound " + i + " -> " + mapped);
        }
        for (int i = 0; i <= RegistryRemapper.MAX_PARTICLE_766; i++) {
            int mapped = RegistryRemapper.mapParticle766To767(i);
            assertTrue(mapped >= 0 && mapped <= RegistryRemapper.MAX_PARTICLE_767,
                    "766 particle " + i + " -> " + mapped);
        }
    }

    // ---- 合成表 763↔767 / 764↔767 / 765↔767 ----

    @Test
    void composed763To767() {
        assertEquals(8843, RegistryRemapper.mapBlockState763To767(8827)); // skeleton_skull
        assertEquals(10366, RegistryRemapper.mapBlockState763To767(10225)); // barrier
        assertEquals(2005, RegistryRemapper.mapBlockState763To767(2005)); // short_grass
        assertEquals(795, RegistryRemapper.mapItem763To767(757)); // scute → turtle_scute
        assertEquals(746, RegistryRemapper.mapSound763To767(666)); // block.iron_door.close
        assertEquals(0, RegistryRemapper.mapEffect763To767(1)); // Speed（1ベース→0ベース）
        assertEquals(32, RegistryRemapper.mapEffect763To767(33)); // Unluck
        assertEquals(40, RegistryRemapper.mapBlockEntityType763To767(40));
        assertEquals(-1, RegistryRemapper.mapBlockEntityType763To767(41));
        assertEquals("block.stone.break",
                RegistryRemapper.mapSoundName763To767("block.stone.break"));
    }

    @Test
    void composed767To763() {
        assertEquals(2873, RegistryRemapper.mapBlockState767To763(26650)); // vault → spawner
        assertEquals(1, RegistryRemapper.mapBlockState767To763(26682)); // heavy_core → stone
        assertEquals(1, RegistryRemapper.mapItem767To763(1093)); // mace → stone
        assertEquals(1, RegistryRemapper.mapItem767To763(1172)); // music_disc_creator → stone
        assertEquals(757, RegistryRemapper.mapItem767To763(795)); // turtle_scute → scute
        assertEquals(72, RegistryRemapper.mapEntityType767To763(2)); // armadillo → pig
        assertEquals(1264, RegistryRemapper.mapSound767To763(1361)); // stone.break
        assertEquals(1264, RegistryRemapper.mapSound767To763(886)); // music_disc.creator → stone.break
        assertEquals(1, RegistryRemapper.mapEffect767To763(0)); // Speed（0ベース→1ベース）
        assertEquals(33, RegistryRemapper.mapEffect767To763(32)); // Unluck
        assertEquals(1, RegistryRemapper.mapEffect767To763(33)); // TrialOmen → Speed
        assertEquals(-1, RegistryRemapper.mapBlockEntityType767To763(41)); // crafter → DROP
        // 763に trial_spawner の音が無いため、767の ominous_activate は
        // 石の破壊音へ（数値の合成表と一致）。
        assertEquals("block.stone.break",
                RegistryRemapper.mapSoundName767To763("block.trial_spawner.ominous_activate"));
    }

    @Test
    void composed764To767AndBack() {
        assertEquals(8827, RegistryRemapper.mapBlockState764To767(8827));
        assertEquals(2873, RegistryRemapper.mapBlockState767To764(26650)); // vault → spawner
        assertEquals(11, RegistryRemapper.mapMenu767To764(7)); // crafter_3x3 → crafting
        assertEquals(1, RegistryRemapper.mapItem767To764(1093)); // mace → stone
        assertEquals(0, RegistryRemapper.mapEffect764To767(0)); // Speed
        assertEquals(0, RegistryRemapper.mapEffect767To764(38)); // Infested → Speed
        assertEquals(40, RegistryRemapper.mapBlockEntityType764To767(40));
        assertEquals(-1, RegistryRemapper.mapBlockEntityType767To764(43)); // vault → DROP
    }

    @Test
    void composed765To767AndBack() {
        assertEquals(795, RegistryRemapper.mapItem765To767(794)); // scute → turtle_scute
        assertEquals(1328, RegistryRemapper.mapItem765To767(1311)); // trial_key → ominous_trial_key
        // 765の charge_activate は 766で追加された音のため、765の 683 は
        // entity.horse.land（767では 723）。stone.break と wind_burst で確認する。
        assertEquals(723, RegistryRemapper.mapSound765To767(683)); // entity.horse.land
        assertEquals(1361, RegistryRemapper.mapSound765To767(1312)); // block.stone.break
        assertEquals(1530, RegistryRemapper.mapSound765To767(1466)); // generic.wind_burst → wind_charge.wind_burst
        assertEquals(794, RegistryRemapper.mapItem767To765(795)); // turtle_scute → scute
        assertEquals(26638, RegistryRemapper.mapBlockState767To765(26650)); // vault → trial_spawner
        assertEquals(1, RegistryRemapper.mapBlockState767To765(26682)); // heavy_core → stone
        assertEquals(1, RegistryRemapper.mapItem767To765(1172)); // music_disc_creator → stone
        assertEquals(0, RegistryRemapper.mapEffect765To767(0)); // Speed
        assertEquals(0, RegistryRemapper.mapEffect767To765(38)); // Infested → Speed
        assertEquals(42, RegistryRemapper.mapBlockEntityType765To767(42)); // trial_spawner
        assertEquals(-1, RegistryRemapper.mapBlockEntityType767To765(43)); // vault → DROP
        assertEquals("block.trial_spawner.ominous_activate",
                RegistryRemapper.mapSoundName765To767("block.trial_spawner.charge_activate"));
        assertEquals("block.stone.break",
                RegistryRemapper.mapSoundName767To765("music_disc.creator"));
        // 765に charge_activate の音が無いため、767の ominous_activate は
        // 765では石の破壊音へ（766には charge_activate があるのでそちらは改名で戻る）。
        assertEquals("block.stone.break",
                RegistryRemapper.mapSoundName767To765("block.trial_spawner.ominous_activate"));
    }

    @Test
    void protocolDispatchRoutes767() {
        // プロトコル指定のオーバーロードが 767 を 767表へ振ること。
        assertEquals(26644, RegistryRemapper.mapBlockState(26638, 767));
        assertEquals(795, RegistryRemapper.mapItem(794, 767));
        assertEquals(77, RegistryRemapper.mapEntityType(73, 767));
        assertEquals(1361, RegistryRemapper.mapSound(1312, 767));
        assertEquals(20, RegistryRemapper.mapParticle(0, 767));
        assertEquals(1, RegistryRemapper.mapBlock(1, 767));
        assertEquals(12, RegistryRemapper.mapMenu(12, 767));
        assertEquals(0, RegistryRemapper.mapEffect(0, 767));
        assertEquals(42, RegistryRemapper.mapBlockEntityType(42, 767));
        assertEquals("block.trial_spawner.ominous_activate",
                RegistryRemapper.mapSoundName("block.trial_spawner.charge_activate", 767));
        // 767クライアントのアイテムID → 765サーバーのアイテムID。
        assertEquals(794, RegistryRemapper.mapItemReverse(795, 767));
        assertEquals(1, RegistryRemapper.mapItemReverse(1172, 767));
        // 766の振り分けが変わっていないこと。
        assertEquals(26644, RegistryRemapper.mapBlockState(26638, 766));
        assertEquals(795, RegistryRemapper.mapItem(794, 766));
    }
}
