package io.detergentmc.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * {@link RegistryRemapper} の766（1.20.5／1.20.6）向けスポットチェックと往復テスト。
 * 期待値は ViaVersion Mappings（mapping-1.20.3.json＝765、
 * mapping-1.20.5.json＝766）と minecraft-data を名前＋プロパティで突き合わせたもの。
 * 生成スクリプト（tools/generate-remap-tables.py）の maps766.json／
 * maps_composed_766.json／map_items_rev766.json と同じ値。
 * 往復テストの例外：パーティクルの ambient_entity_effect（765[0]）だけは
 * 766の entity_effect（20）へ行き、戻りは 765の entity_effect（21）になる。
 */
class RegistryRemapper766Test {

    // ---- 765 → 766 ----

    @Test
    void blockStatesForward() {
        assertEquals(0, RegistryRemapper.mapBlockState766(0)); // air
        assertEquals(1, RegistryRemapper.mapBlockState766(1)); // stone
        assertEquals(2005, RegistryRemapper.mapBlockState766(2005)); // short_grass
        assertEquals(5849, RegistryRemapper.mapBlockState766(5849)); // pumpkin（1.20.5では移動なし）
        // trial_spawner：ominous プロパティが増え、6状態→12状態へ。
        // 765の 26638〜26643（ominous=false）は 766の 26644〜26649 へ。
        assertEquals(26644, RegistryRemapper.mapBlockState766(26638));
        assertEquals(26649, RegistryRemapper.mapBlockState766(26643));
        assertEquals(1, RegistryRemapper.mapBlockState766(99999)); // 範囲外は石
    }

    @Test
    void itemsForward() {
        assertEquals(0, RegistryRemapper.mapItem766(0)); // air
        assertEquals(1, RegistryRemapper.mapItem766(1)); // stone
        assertEquals(795, RegistryRemapper.mapItem766(794)); // scute → turtle_scute（改名）
        assertEquals(798, RegistryRemapper.mapItem766(795)); // flint_and_steel（3つ後ろへ）
        assertEquals(799, RegistryRemapper.mapItem766(796)); // apple
        assertEquals(1325, RegistryRemapper.mapItem766(1311)); // trial_key → ominous_trial_key
        assertEquals(1, RegistryRemapper.mapItem766(99999)); // 範囲外は石
    }

    @Test
    void entitiesForward() {
        assertEquals(0, RegistryRemapper.mapEntityType766(0)); // area_effect_cloud
        assertEquals(77, RegistryRemapper.mapEntityType766(73)); // pig
        assertEquals(40, RegistryRemapper.mapEntityType766(37)); // falling_block
        assertEquals(129, RegistryRemapper.mapEntityType766(125)); // fishing_bobber
        assertEquals(77, RegistryRemapper.mapEntityType766(99999)); // 範囲外はブタ
    }

    @Test
    void soundsForward() {
        assertEquals(1358, RegistryRemapper.mapSound766(1312)); // block.stone.break
        assertEquals(1526, RegistryRemapper.mapSound766(1466)); // entity.generic.wind_burst → wind_charge.wind_burst
        assertEquals(188, RegistryRemapper.mapSound766(166)); // entity.breeze.inhale
        assertEquals(190, RegistryRemapper.mapSound766(168)); // entity.breeze.idle_air
        assertEquals(191, RegistryRemapper.mapSound766(169)); // entity.breeze.shoot
        assertEquals(196, RegistryRemapper.mapSound766(174)); // entity.breeze.hurt
    }

    @Test
    void particlesForward() {
        assertEquals(20, RegistryRemapper.mapParticle766(0)); // ambient_entity_effect → entity_effect
        assertEquals(25, RegistryRemapper.mapParticle766(25)); // gust_emitter → gust_emitter_large
        assertEquals(24, RegistryRemapper.mapParticle766(99)); // gust_dust → small_gust
        assertEquals(102, RegistryRemapper.mapParticle766(100)); // trial_spawner_detection
        assertEquals(1, RegistryRemapper.mapParticle766(2)); // block
        assertEquals(28, RegistryRemapper.mapParticle766(27)); // falling_dust
        assertEquals(13, RegistryRemapper.mapParticle766(14)); // dust
        assertEquals(44, RegistryRemapper.mapParticle766(42)); // item
        assertEquals(99, RegistryRemapper.mapParticle766(96)); // shriek
    }

    @Test
    void blocksMenusEffectsForward() {
        assertEquals(0, RegistryRemapper.mapBlock766(0)); // air
        assertEquals(1, RegistryRemapper.mapBlock766(1)); // stone
        assertEquals(123, RegistryRemapper.mapBlock766(123)); // grass
        assertEquals(1057, RegistryRemapper.mapBlock766(1057)); // trial_spawner
        assertEquals(0, RegistryRemapper.mapMenu766(0)); // generic_9x1
        assertEquals(7, RegistryRemapper.mapMenu766(7)); // crafter_3x3
        assertEquals(12, RegistryRemapper.mapMenu766(12)); // crafting
        assertEquals(24, RegistryRemapper.mapMenu766(24)); // smithing
        assertEquals(0, RegistryRemapper.mapEffect766(0)); // Speed
        assertEquals(32, RegistryRemapper.mapEffect766(32)); // Bad Omen→Unluck は同番号
    }

    @Test
    void blockEntityForward() {
        assertEquals(42, RegistryRemapper.mapBlockEntityType766(42)); // trial_spawner
        assertEquals(-1, RegistryRemapper.mapBlockEntityType766(43)); // 765にない番号は DROP
    }

    @Test
    void soundNamesForward() {
        assertEquals("entity.wind_charge.wind_burst",
                RegistryRemapper.mapSoundName766("entity.generic.wind_burst"));
        assertEquals("block.stone.break",
                RegistryRemapper.mapSoundName766("block.stone.break"));
    }

    // ---- 766 → 765（invert）----

    @Test
    void blockStatesUp() {
        // ominous=true の 766[26638〜26643] は 765の同番号へ（補正表）。
        assertEquals(26638, RegistryRemapper.mapBlockStateUp766(26638));
        assertEquals(26643, RegistryRemapper.mapBlockStateUp766(26643));
        // ominous=false の 766[26644〜26649] は 765の 26638〜26643 へ。
        assertEquals(26638, RegistryRemapper.mapBlockStateUp766(26644));
        assertEquals(26643, RegistryRemapper.mapBlockStateUp766(26649));
        // vault（766で新規、32状態）は 765の trial_spawner 既定（26638）へ。
        assertEquals(26638, RegistryRemapper.mapBlockStateUp766(26650));
        assertEquals(26638, RegistryRemapper.mapBlockStateUp766(26681));
        // heavy_core（766で新規）は石へ。
        assertEquals(1, RegistryRemapper.mapBlockStateUp766(26682));
        assertEquals(1, RegistryRemapper.mapBlockStateUp766(26683));
        assertEquals(1, RegistryRemapper.mapBlockStateUp766(99999)); // 範囲外は石
    }

    @Test
    void itemsUp() {
        assertEquals(794, RegistryRemapper.mapItemUp766(795)); // turtle_scute → scute
        assertEquals(793, RegistryRemapper.mapItemUp766(794)); // turtle_helmet
        // 766で新規のアイテムは石へ。
        assertEquals(1, RegistryRemapper.mapItemUp766(85)); // heavy_core
        assertEquals(1, RegistryRemapper.mapItemUp766(796)); // armadillo_scute
        assertEquals(1, RegistryRemapper.mapItemUp766(1093)); // mace
        assertEquals(1, RegistryRemapper.mapItemUp766(1327)); // vault
        assertEquals(1, RegistryRemapper.mapItemUp766(1329)); // breeze_rod
        assertEquals(1, RegistryRemapper.mapItemUp766(99999)); // 範囲外は石
    }

    @Test
    void itemReverse766KeepsUnknownIds() {
        // mapItemReverse766 は対応表にあるIDを765のIDへ写し、766で新規の
        // アイテムは石（1）へ写す。範囲外のIDだけをそのまま返す。
        assertEquals(794, RegistryRemapper.mapItemReverse766(795)); // turtle_scute → scute
        assertEquals(1, RegistryRemapper.mapItemReverse766(1093)); // mace → stone
        assertEquals(9999, RegistryRemapper.mapItemReverse766(9999)); // 範囲外はそのまま
    }

    @Test
    void entitiesUp() {
        assertEquals(73, RegistryRemapper.mapEntityTypeUp766(2)); // armadillo → pig
        assertEquals(73, RegistryRemapper.mapEntityTypeUp766(11)); // bogged → pig
        assertEquals(73, RegistryRemapper.mapEntityTypeUp766(13)); // breeze_wind_charge → pig
        assertEquals(73, RegistryRemapper.mapEntityTypeUp766(61)); // ominous_item_spawner → pig
        assertEquals(37, RegistryRemapper.mapEntityTypeUp766(40)); // falling_block
        assertEquals(73, RegistryRemapper.mapEntityTypeUp766(77)); // pig
        assertEquals(73, RegistryRemapper.mapEntityTypeUp766(99999)); // 範囲外はブタ
    }

    @Test
    void soundsUp() {
        assertEquals(1466, RegistryRemapper.mapSoundUp766(1527)); // wind_charge.throw → generic.wind_burst
        assertEquals(1466, RegistryRemapper.mapSoundUp766(198)); // breeze.wind_burst → generic.wind_burst
        assertEquals(166, RegistryRemapper.mapSoundUp766(186)); // breeze.charge → breeze.inhale
        assertEquals(1466, RegistryRemapper.mapSoundUp766(1526)); // wind_charge.wind_burst
        assertEquals(1312, RegistryRemapper.mapSoundUp766(1358)); // stone.break
        assertEquals(166, RegistryRemapper.mapSoundUp766(188)); // breeze.inhale
    }

    @Test
    void particlesUp() {
        assertEquals(99, RegistryRemapper.mapParticleUp766(24)); // small_gust → gust_dust
        assertEquals(25, RegistryRemapper.mapParticleUp766(26)); // gust_emitter_small → gust_emitter
        assertEquals(100, RegistryRemapper.mapParticleUp766(103)); // trial_spawner_detection_ominous → trial_spawner_detection
        assertEquals(100, RegistryRemapper.mapParticleUp766(102)); // trial_spawner_detection
        assertEquals(21, RegistryRemapper.mapParticleUp766(20)); // entity_effect
        assertEquals(25, RegistryRemapper.mapParticleUp766(25)); // gust_emitter_large → gust_emitter
        assertEquals(2, RegistryRemapper.mapParticleUp766(1)); // block
        assertEquals(42, RegistryRemapper.mapParticleUp766(44)); // item
        assertEquals(96, RegistryRemapper.mapParticleUp766(99)); // shriek
    }

    @Test
    void blocksMenusEffectsUp() {
        assertEquals(1, RegistryRemapper.mapBlockUp766(1058)); // vault → stone
        assertEquals(1, RegistryRemapper.mapBlockUp766(1059)); // heavy_core → stone
        assertEquals(1, RegistryRemapper.mapBlockUp766(1)); // stone
        assertEquals(12, RegistryRemapper.mapMenuUp766(12)); // crafting
        assertEquals(0, RegistryRemapper.mapEffectUp766(0)); // Speed
        assertEquals(32, RegistryRemapper.mapEffectUp766(32)); // Unluck
        assertEquals(0, RegistryRemapper.mapEffectUp766(33)); // TrialOmen → Speed
        assertEquals(0, RegistryRemapper.mapEffectUp766(38)); // Infested → Speed
    }

    @Test
    void blockEntityUp() {
        assertEquals(42, RegistryRemapper.mapBlockEntityTypeUp766(42)); // trial_spawner
        assertEquals(-1, RegistryRemapper.mapBlockEntityTypeUp766(43)); // vault は DROP
    }

    @Test
    void soundNamesUp() {
        assertEquals("entity.generic.wind_burst",
                RegistryRemapper.mapSoundNameUp766("entity.wind_charge.wind_burst"));
        assertEquals("entity.generic.wind_burst",
                RegistryRemapper.mapSoundNameUp766("entity.wind_charge.throw"));
        assertEquals("entity.breeze.inhale",
                RegistryRemapper.mapSoundNameUp766("entity.breeze.charge"));
        // 近い代替がない766新音は stone.break へ。
        assertEquals("block.stone.break",
                RegistryRemapper.mapSoundNameUp766("entity.breeze.deflect"));
        assertEquals("block.stone.break",
                RegistryRemapper.mapSoundNameUp766("block.vault.break"));
    }

    // ---- 往復・網羅 ----

    @Test
    void roundTrip765To766To765() {
        for (int i = 0; i <= RegistryRemapper.MAX_BLOCK_STATE_765; i++) {
            assertEquals(i, RegistryRemapper.mapBlockStateUp766(
                    RegistryRemapper.mapBlockState766(i)), "blockstate " + i);
        }
        for (int i = 0; i <= RegistryRemapper.MAX_ITEM_765; i++) {
            assertEquals(i, RegistryRemapper.mapItemUp766(
                    RegistryRemapper.mapItem766(i)), "item " + i);
        }
        for (int i = 0; i <= RegistryRemapper.MAX_ENTITY_TYPE_765; i++) {
            assertEquals(i, RegistryRemapper.mapEntityTypeUp766(
                    RegistryRemapper.mapEntityType766(i)), "entity " + i);
        }
        for (int i = 0; i <= RegistryRemapper.MAX_SOUND_765; i++) {
            assertEquals(i, RegistryRemapper.mapSoundUp766(
                    RegistryRemapper.mapSound766(i)), "sound " + i);
        }
        for (int i = 0; i <= RegistryRemapper.MAX_BLOCK_765; i++) {
            assertEquals(i, RegistryRemapper.mapBlockUp766(
                    RegistryRemapper.mapBlock766(i)), "block " + i);
        }
        for (int i = 0; i <= RegistryRemapper.MAX_MENU_765; i++) {
            assertEquals(i, RegistryRemapper.mapMenuUp766(
                    RegistryRemapper.mapMenu766(i)), "menu " + i);
        }
    }

    @Test
    void roundTripParticlesExceptAmbient() {
        for (int i = 0; i <= RegistryRemapper.MAX_PARTICLE_765; i++) {
            int back = RegistryRemapper.mapParticleUp766(
                    RegistryRemapper.mapParticle766(i));
            if (i == 0) {
                // ambient_entity_effect だけは entity_effect へ行き、戻りは 21。
                assertEquals(21, back, "particle 0 (ambient)");
            } else {
                assertEquals(i, back, "particle " + i);
            }
        }
    }

    @Test
    void exhaustiveForwardStayInRange() {
        for (int i = 0; i <= RegistryRemapper.MAX_BLOCK_STATE_765; i++) {
            int mapped = RegistryRemapper.mapBlockState766(i);
            assertTrue(mapped >= 0 && mapped <= RegistryRemapper.MAX_BLOCK_STATE_766,
                    "765 blockstate " + i + " -> " + mapped);
        }
        for (int i = 0; i <= RegistryRemapper.MAX_ITEM_765; i++) {
            int mapped = RegistryRemapper.mapItem766(i);
            assertTrue(mapped >= 0 && mapped <= RegistryRemapper.MAX_ITEM_766,
                    "765 item " + i + " -> " + mapped);
        }
        for (int i = 0; i <= RegistryRemapper.MAX_SOUND_765; i++) {
            int mapped = RegistryRemapper.mapSound766(i);
            assertTrue(mapped >= 0 && mapped <= RegistryRemapper.MAX_SOUND_766,
                    "765 sound " + i + " -> " + mapped);
        }
        for (int i = 0; i <= RegistryRemapper.MAX_PARTICLE_765; i++) {
            int mapped = RegistryRemapper.mapParticle766(i);
            assertTrue(mapped >= 0 && mapped <= RegistryRemapper.MAX_PARTICLE_766,
                    "765 particle " + i + " -> " + mapped);
        }
    }

    @Test
    void exhaustiveUpStayInRange() {
        for (int i = 0; i <= RegistryRemapper.MAX_BLOCK_STATE_766; i++) {
            int mapped = RegistryRemapper.mapBlockStateUp766(i);
            assertTrue(mapped >= 0 && mapped <= RegistryRemapper.MAX_BLOCK_STATE_765,
                    "766 blockstate " + i + " -> " + mapped);
        }
        for (int i = 0; i <= RegistryRemapper.MAX_ITEM_766; i++) {
            int mapped = RegistryRemapper.mapItemUp766(i);
            assertTrue(mapped >= 0 && mapped <= RegistryRemapper.MAX_ITEM_765,
                    "766 item " + i + " -> " + mapped);
        }
        for (int i = 0; i <= RegistryRemapper.MAX_ENTITY_TYPE_766; i++) {
            int mapped = RegistryRemapper.mapEntityTypeUp766(i);
            assertTrue(mapped >= 0 && mapped <= RegistryRemapper.MAX_ENTITY_TYPE_765,
                    "766 entity " + i + " -> " + mapped);
        }
        for (int i = 0; i <= RegistryRemapper.MAX_SOUND_766; i++) {
            int mapped = RegistryRemapper.mapSoundUp766(i);
            assertTrue(mapped >= 0 && mapped <= RegistryRemapper.MAX_SOUND_765,
                    "766 sound " + i + " -> " + mapped);
        }
        for (int i = 0; i <= RegistryRemapper.MAX_PARTICLE_766; i++) {
            int mapped = RegistryRemapper.mapParticleUp766(i);
            assertTrue(mapped >= 0 && mapped <= RegistryRemapper.MAX_PARTICLE_765,
                    "766 particle " + i + " -> " + mapped);
        }
        for (int i = 0; i <= RegistryRemapper.MAX_BLOCK_766; i++) {
            int mapped = RegistryRemapper.mapBlockUp766(i);
            assertTrue(mapped >= 0 && mapped <= RegistryRemapper.MAX_BLOCK_765,
                    "766 block " + i + " -> " + mapped);
        }
    }

    // ---- 合成表 763↔766 / 764↔766 ----

    @Test
    void composed763To766() {
        // 頭蓋骨：763の powered なしの状態は 766の powered=false の状態へ
        // （powered=true を誤って選ばないこと。verify-766-tables.py で検出した不具合）。
        assertEquals(8843, RegistryRemapper.mapBlockState763To766(8827)); // skeleton_skull
        assertEquals(8860, RegistryRemapper.mapBlockState763To766(8843)); // skeleton_wall_skull
        assertEquals(10366, RegistryRemapper.mapBlockState763To766(10225)); // barrier
        assertEquals(2005, RegistryRemapper.mapBlockState763To766(2005)); // short_grass
        assertEquals(0, RegistryRemapper.mapBlockState763To766(0)); // air
        assertEquals(795, RegistryRemapper.mapItem763To766(757)); // scute → turtle_scute
        assertEquals(1, RegistryRemapper.mapItem763To766(1)); // stone
        assertEquals(746, RegistryRemapper.mapSound763To766(666)); // block.iron_door.close
    }

    @Test
    void composed766To763() {
        assertEquals(2873, RegistryRemapper.mapBlockState766To763(26650)); // vault → spawner
        assertEquals(2873, RegistryRemapper.mapBlockState766To763(26644)); // trial_spawner → spawner
        assertEquals(2873, RegistryRemapper.mapBlockState766To763(26638)); // ominous trial_spawner → spawner
        assertEquals(1, RegistryRemapper.mapBlockState766To763(26682)); // heavy_core → stone
        assertEquals(1, RegistryRemapper.mapItem766To763(1093)); // mace → stone
        assertEquals(1, RegistryRemapper.mapItem766To763(85)); // heavy_core → stone
        assertEquals(757, RegistryRemapper.mapItem766To763(795)); // turtle_scute → scute
        assertEquals(72, RegistryRemapper.mapEntityType766To763(2)); // armadillo → pig
        assertEquals(72, RegistryRemapper.mapEntityType766To763(11)); // bogged → pig
        assertEquals(89, RegistryRemapper.mapEntityType766To763(117)); // wind_charge → small_fireball
        assertEquals(517, RegistryRemapper.mapSound766To763(1527)); // wind_charge.throw → explode
        assertEquals(517, RegistryRemapper.mapSound766To763(198)); // breeze.wind_burst → explode
        assertEquals(147, RegistryRemapper.mapSound766To763(186)); // breeze.charge → blaze.ambient
        assertEquals(1264, RegistryRemapper.mapSound766To763(1358)); // stone.break
        assertEquals(5, RegistryRemapper.mapParticle766To763(25)); // gust_emitter_large → cloud
        assertEquals(14, RegistryRemapper.mapParticle766To763(24)); // small_gust → dust
        assertEquals(30, RegistryRemapper.mapParticle766To763(103)); // trial_spawner_detection_ominous → sculk_soul
        assertEquals(21, RegistryRemapper.mapParticle766To763(20)); // entity_effect
        assertEquals(11, RegistryRemapper.mapMenu766To763(7)); // crafter_3x3 → crafting（763に crafter はない）
        assertEquals("entity.generic.explode",
                RegistryRemapper.mapSoundName766To763("entity.wind_charge.throw"));
        assertEquals("entity.blaze.ambient",
                RegistryRemapper.mapSoundName766To763("entity.breeze.charge"));
    }

    @Test
    void composed764To766AndBack() {
        // 764の頭蓋骨は 763と違い、同じ番号のまま（764の skull 状態は
        // 766と同じ並びになっている）。
        assertEquals(8827, RegistryRemapper.mapBlockState764To766(8827));
        assertEquals(2873, RegistryRemapper.mapBlockState766To764(26650)); // vault → spawner
        assertEquals(11, RegistryRemapper.mapMenu766To764(7)); // crafter_3x3 → crafting
        assertEquals(1, RegistryRemapper.mapItem766To764(1093)); // mace → stone
    }

    @Test
    void composedExhaustiveStayInRange() {
        for (int i = 0; i <= RegistryRemapper.MAX_BLOCK_STATE_763; i++) {
            int mapped = RegistryRemapper.mapBlockState763To766(i);
            assertTrue(mapped >= 0 && mapped <= RegistryRemapper.MAX_BLOCK_STATE_766,
                    "763 blockstate " + i + " -> " + mapped);
        }
        for (int i = 0; i <= RegistryRemapper.MAX_BLOCK_STATE_764; i++) {
            int mapped = RegistryRemapper.mapBlockState764To766(i);
            assertTrue(mapped >= 0 && mapped <= RegistryRemapper.MAX_BLOCK_STATE_766,
                    "764 blockstate " + i + " -> " + mapped);
        }
        for (int i = 0; i <= RegistryRemapper.MAX_BLOCK_STATE_766; i++) {
            int mapped = RegistryRemapper.mapBlockState766To763(i);
            assertTrue(mapped >= 0 && mapped <= RegistryRemapper.MAX_BLOCK_STATE_763,
                    "766 blockstate " + i + " -> 763 " + mapped);
            mapped = RegistryRemapper.mapBlockState766To764(i);
            assertTrue(mapped >= 0 && mapped <= RegistryRemapper.MAX_BLOCK_STATE_764,
                    "766 blockstate " + i + " -> 764 " + mapped);
        }
        for (int i = 0; i <= RegistryRemapper.MAX_ITEM_766; i++) {
            int mapped = RegistryRemapper.mapItem766To763(i);
            assertTrue(mapped >= 0 && mapped <= RegistryRemapper.MAX_ITEM_763,
                    "766 item " + i + " -> 763 " + mapped);
            mapped = RegistryRemapper.mapItem766To764(i);
            assertTrue(mapped >= 0 && mapped <= RegistryRemapper.MAX_ITEM_764,
                    "766 item " + i + " -> 764 " + mapped);
        }
    }

    @Test
    void protocolDispatchRoutes766() {
        // プロトコル指定のオーバーロードが 766 を 766表へ振ること。
        assertEquals(26644, RegistryRemapper.mapBlockState(26638, 766));
        assertEquals(795, RegistryRemapper.mapItem(794, 766));
        assertEquals(77, RegistryRemapper.mapEntityType(73, 766));
        assertEquals(1358, RegistryRemapper.mapSound(1312, 766));
        assertEquals(20, RegistryRemapper.mapParticle(0, 766));
        assertEquals(1, RegistryRemapper.mapBlock(1, 766));
        assertEquals(12, RegistryRemapper.mapMenu(12, 766));
        assertEquals(0, RegistryRemapper.mapEffect(0, 766));
        assertEquals(42, RegistryRemapper.mapBlockEntityType(42, 766));
        assertEquals("entity.wind_charge.wind_burst",
                RegistryRemapper.mapSoundName("entity.generic.wind_burst", 766));
        // 765の振り分けが変わっていないこと。
        assertEquals(26638, RegistryRemapper.mapBlockState(26638, 765));
        assertEquals(794, RegistryRemapper.mapItem(794, 765));
    }
}
