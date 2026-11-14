package io.detergentmc.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * {@link RegistryRemapper} の逆向き（764→765）変換テスト。
 * 期待値は ViaVersion Mappings（mapping-1.20.2.json＝764、
 * mapping-1.20.3.json＝765）の順序付きリストを名前で突き合わせたもの。
 * 反転の方法：既存の765→764表（区間＋上書き）を起動時に反転する
 * （区間を先に、上書きを後から。音だけ4件の補正あり）。
 * tools/verify-reverse-tables.py で同じ検証を名前ベースで行っている。
 */
class RegistryRemapperUp764Test {

    @Test
    void blockStatesUp() {
        assertEquals(0, RegistryRemapper.mapBlockStateUp764(0)); // air
        assertEquals(1, RegistryRemapper.mapBlockStateUp764(1)); // stone
        assertEquals(2005, RegistryRemapper.mapBlockStateUp764(2005)); // grass → short_grass（番号は同じ）
        assertEquals(5849, RegistryRemapper.mapBlockStateUp764(5850)); // netherrack（765=5849→764=5850 の逆）
        assertEquals(6811, RegistryRemapper.mapBlockStateUp764(5849)); // pumpkin（765=6811→764=5849 の逆）
        assertEquals(6810, RegistryRemapper.mapBlockStateUp764(6811)); // glass_pane
        // 代替で潰れた番号は「元のブロック自身」に戻る（crafter の状態は764に無い）。
        assertEquals(4277, RegistryRemapper.mapBlockStateUp764(4277)); // crafting_table 自身
        assertEquals(2873, RegistryRemapper.mapBlockStateUp764(2873)); // spawner 自身
        // 765の tuff_slab（21085）の代替先だった stone_slab の状態は、
        // 765でも同じ番号（11225）の stone_slab 自身に戻る。
        assertEquals(11225, RegistryRemapper.mapBlockStateUp764(11225));
    }

    @Test
    void blockStatesUpRoundTrip() {
        // 764の全ブロック状態：上向き→下向きで元に戻る。
        for (int i = 0; i <= RegistryRemapper.MAX_BLOCK_STATE_764; i++) {
            int up = RegistryRemapper.mapBlockStateUp764(i);
            assertTrue(up >= 0 && up <= RegistryRemapper.MAX_BLOCK_STATE_765, "up out of range: " + i + " -> " + up);
            assertEquals(i, RegistryRemapper.mapBlockState764(up), "round-trip failed for 764 blockstate " + i);
        }
    }

    @Test
    void blockStatesDownUpIdempotent() {
        // 765の全ブロック状態：下向き→上向き→下向きで下向きの結果と一致する
        // （765にしかない状態は代替に落ち、その代替が往復で安定すること）。
        for (int i = 0; i <= RegistryRemapper.MAX_BLOCK_STATE_765; i++) {
            int down = RegistryRemapper.mapBlockState764(i);
            int back = RegistryRemapper.mapBlockState764(RegistryRemapper.mapBlockStateUp764(down));
            assertEquals(down, back, "765 blockstate " + i);
        }
    }

    @Test
    void blockStatesUpUnknownFallsBackToStone() {
        assertEquals(1, RegistryRemapper.mapBlockStateUp764(99999));
        assertEquals(1, RegistryRemapper.mapBlockStateUp764(-1));
    }

    @Test
    void itemsUp() {
        assertEquals(0, RegistryRemapper.mapItemUp764(0)); // air
        assertEquals(1, RegistryRemapper.mapItemUp764(1)); // stone
        assertEquals(194, RegistryRemapper.mapItemUp764(173)); // grass → short_grass
        assertEquals(339, RegistryRemapper.mapItemUp764(318)); // stone_bricks
        assertEquals(342, RegistryRemapper.mapItemUp764(321)); // chiseled_stone_bricks
        assertEquals(12, RegistryRemapper.mapItemUp764(12)); // tuff 自身
        assertEquals(1, RegistryRemapper.mapItemUp764(99999)); // 範囲外は石
    }

    @Test
    void itemsUpMatchesExistingReverseTable() {
        // 反転した表は、生成スクリプトが名前から作った既存の逆変換表と一致する。
        for (int i = 0; i <= RegistryRemapper.MAX_ITEM_764; i++) {
            assertEquals(RegistryRemapper.mapItemReverse764(i), RegistryRemapper.mapItemUp764(i),
                    "764 item " + i);
        }
    }

    @Test
    void itemsUpRoundTrip() {
        for (int i = 0; i <= RegistryRemapper.MAX_ITEM_764; i++) {
            int up = RegistryRemapper.mapItemUp764(i);
            assertEquals(i, RegistryRemapper.mapItem764(up), "round-trip failed for 764 item " + i);
        }
    }

    @Test
    void entityTypesUp() {
        assertEquals(73, RegistryRemapper.mapEntityTypeUp764(72)); // pig（764=72→765=73）
        assertEquals(37, RegistryRemapper.mapEntityTypeUp764(36)); // falling_block
        assertEquals(7, RegistryRemapper.mapEntityTypeUp764(7)); // blaze 自身（breeze の代替先ではない）
        assertEquals(90, RegistryRemapper.mapEntityTypeUp764(89)); // 764=89 の対応は765=90
        assertEquals(73, RegistryRemapper.mapEntityTypeUp764(99999)); // 範囲外はブタ
    }

    @Test
    void entityTypesUpRoundTrip() {
        for (int i = 0; i <= RegistryRemapper.MAX_ENTITY_TYPE_764; i++) {
            int up = RegistryRemapper.mapEntityTypeUp764(i);
            assertEquals(i, RegistryRemapper.mapEntityType764(up), "round-trip failed for 764 entity " + i);
        }
    }

    @Test
    void soundsUp() {
        assertEquals(0, RegistryRemapper.mapSoundUp764(0));
        assertEquals(1312, RegistryRemapper.mapSoundUp764(1270)); // block.stone.break
        assertEquals(563, RegistryRemapper.mapSoundUp764(534)); // block.glass.break
        // 補正の4件：copper_door／copper_trapdoor の代替に惑わされず同名の音に戻る。
        assertEquals(706, RegistryRemapper.mapSoundUp764(666)); // block.iron_door.close
        assertEquals(707, RegistryRemapper.mapSoundUp764(667)); // block.iron_door.open
        assertEquals(714, RegistryRemapper.mapSoundUp764(674)); // block.iron_trapdoor.close
        assertEquals(715, RegistryRemapper.mapSoundUp764(675)); // block.iron_trapdoor.open
        assertEquals(1312, RegistryRemapper.mapSoundUp764(99999)); // 範囲外は石の破壊音
    }

    @Test
    void soundsUpRoundTrip() {
        for (int i = 0; i <= RegistryRemapper.MAX_SOUND_764; i++) {
            int up = RegistryRemapper.mapSoundUp764(i);
            assertEquals(i, RegistryRemapper.mapSound764(up), "round-trip failed for 764 sound " + i);
        }
    }

    @Test
    void particlesUp() {
        assertEquals(0, RegistryRemapper.mapParticleUp764(0)); // ambient_entity_effect
        assertEquals(27, RegistryRemapper.mapParticleUp764(25)); // falling_dust
        assertEquals(42, RegistryRemapper.mapParticleUp764(40)); // item
        assertEquals(43, RegistryRemapper.mapParticleUp764(41)); // vibration
        assertEquals(96, RegistryRemapper.mapParticleUp764(93)); // shriek
        assertEquals(26, RegistryRemapper.mapParticleUp764(24)); // sonic_boom
        assertEquals(97, RegistryRemapper.mapParticleUp764(94)); // egg_crack
        assertEquals(0, RegistryRemapper.mapParticleUp764(99999)); // 範囲外は ambient
    }

    @Test
    void particlesUpRoundTrip() {
        for (int i = 0; i <= RegistryRemapper.MAX_PARTICLE_764; i++) {
            int up = RegistryRemapper.mapParticleUp764(i);
            assertEquals(i, RegistryRemapper.mapParticle764(up), "round-trip failed for 764 particle " + i);
        }
    }

    @Test
    void blocksUp() {
        assertEquals(1, RegistryRemapper.mapBlockUp764(1)); // stone
        assertEquals(182, RegistryRemapper.mapBlockUp764(182)); // 自身（765=1056 の代替先ではない）
        assertEquals(1, RegistryRemapper.mapBlockUp764(99999)); // 範囲外は石
    }

    @Test
    void blocksUpRoundTrip() {
        for (int i = 0; i <= RegistryRemapper.MAX_BLOCK_764; i++) {
            int up = RegistryRemapper.mapBlockUp764(i);
            assertEquals(i, RegistryRemapper.mapBlock764(up), "round-trip failed for 764 block " + i);
        }
    }

    @Test
    void menusUp() {
        assertEquals(12, RegistryRemapper.mapMenuUp764(11)); // crafting（crafter_3x3=7 ではない）
        assertEquals(0, RegistryRemapper.mapMenuUp764(0));
        assertEquals(12, RegistryRemapper.mapMenuUp764(99999)); // 範囲外は作業台
    }

    @Test
    void menusUpRoundTrip() {
        for (int i = 0; i <= RegistryRemapper.MAX_MENU_764; i++) {
            int up = RegistryRemapper.mapMenuUp764(i);
            assertEquals(i, RegistryRemapper.mapMenu764(up), "round-trip failed for 764 menu " + i);
        }
    }

    @Test
    void effectsUpAreIdentity() {
        for (int i = 0; i <= RegistryRemapper.MAX_EFFECT_764; i++) {
            assertEquals(i, RegistryRemapper.mapEffectUp764(i));
        }
        assertEquals(0, RegistryRemapper.mapEffectUp764(99999)); // 範囲外は Speed
    }

    @Test
    void blockEntityTypesUp() {
        assertEquals(0, RegistryRemapper.mapBlockEntityTypeUp764(0)); // furnace
        assertEquals(40, RegistryRemapper.mapBlockEntityTypeUp764(40)); // decorated_pot
        assertEquals(RegistryRemapper.DROP, RegistryRemapper.mapBlockEntityTypeUp764(41));
        assertEquals(RegistryRemapper.DROP, RegistryRemapper.mapBlockEntityTypeUp764(42));
        assertEquals(RegistryRemapper.DROP, RegistryRemapper.mapBlockEntityTypeUp764(999));
    }

    @Test
    void soundNamesUpAreIdentity() {
        assertEquals("minecraft:block.stone.break",
                RegistryRemapper.mapSoundNameUp764("minecraft:block.stone.break"));
        assertEquals("block.stone.break", RegistryRemapper.mapSoundNameUp764("block.stone.break"));
        assertEquals("minecraft:block.stone.break", RegistryRemapper.mapSoundNameUp764(null));
    }

    @Test
    void forwardMapsStillIntact() {
        // 既存の下向き変換が壊れていないことのスポットチェック。
        assertEquals(5849, RegistryRemapper.mapBlockState764(6811)); // pumpkin
        assertEquals(173, RegistryRemapper.mapItem764(194)); // short_grass → grass
        assertEquals(11225, RegistryRemapper.mapBlockState764(21085)); // tuff_slab → stone_slab
        assertEquals(666, RegistryRemapper.mapSound764(706)); // block.iron_door.close
        assertEquals(11, RegistryRemapper.mapMenu764(7)); // crafter_3x3 → crafting
    }
}
