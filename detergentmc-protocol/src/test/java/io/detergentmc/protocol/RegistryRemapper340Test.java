package io.detergentmc.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * 340（1.12.2）のレジストリ写像。340 側の実数は minecraft-data 1.12
 * （Burger 抽出：ブロック254種・アイテム410種）と実バニラ 1.12.2 jar
 * のバイトコード（音549件は SoundEvents、エンティティ83件は
 * EntityList の登録呼び出し）から確定。ブロックは状態が無く
 * (数値ID&lt;&lt;4)|メタデータ（実在組合せ1,328通り）で、意味対応表
 * （tools/gen-340-tables.py）で 758 側のブロック状態と付け替える。
 * 表は tools/verify-340-tables.py で生成 Java と JSON の全件照合済み。
 * 実バニラ 1.12.2 jar の SHA-1 は
 * 886945bfb2b978778c3a0288fd7fab09d315b25f。
 */
class RegistryRemapper340Test {

    @Test
    void maxConstantsAreThe1122Values() {
        assertEquals(4095, RegistryRemapper.MAX_BLOCK_STATE_340);
        assertEquals(2267, RegistryRemapper.MAX_ITEM_340);
        assertEquals(200, RegistryRemapper.MAX_ENTITY_TYPE_340);
        assertEquals(548, RegistryRemapper.MAX_SOUND_340);
        assertEquals(48, RegistryRemapper.MAX_PARTICLE_340);
        assertEquals(255, RegistryRemapper.MAX_BLOCK_340);
        assertEquals(167, RegistryRemapper.MAX_BIOME_340);
        assertEquals(41, RegistryRemapper.MAX_DAMAGE_TYPE_340);
        assertEquals(23, RegistryRemapper.MAX_BLOCK_ENTITY_340);
        assertEquals(27, RegistryRemapper.MAX_EFFECT_340);
    }

    @Test
    void blockStateSemanticAnchors() {
        // 空気は 0、石は (1<<4)|0 = 16、草ブロックは (2<<4)|0 = 32。
        assertEquals(0, RegistryRemapper.mapBlockState340To758(0));
        assertEquals(16, RegistryRemapper.mapBlockState758To340(1)); // 758 の stone → (1,0)
        assertEquals(16, RegistryRemapper.mapBlockState758To340(
                RegistryRemapper.mapBlockState340To758(16)));
        // 白羊毛 (35,0) = 560。758 の white_wool 既定状態からは 560 へ戻る。
        int whiteWool758 = RegistryRemapper.mapBlockState340To758(560);
        assertEquals(560, RegistryRemapper.mapBlockState758To340(whiteWool758));
        // 黒羊毛 (35,15) = 575。
        int blackWool758 = RegistryRemapper.mapBlockState340To758(575);
        assertEquals(575, RegistryRemapper.mapBlockState758To340(blackWool758));
        // 原木の軸：(17,0)=y軸、(17,4)=x軸、(17,12)=oak_wood。
        int logY = RegistryRemapper.mapBlockState340To758(17 << 4);
        int logX = RegistryRemapper.mapBlockState340To758((17 << 4) | 4);
        int logWood = RegistryRemapper.mapBlockState340To758((17 << 4) | 12);
        assertTrue(logY != logX && logX != logWood && logY != logWood);
        assertEquals(17 << 4, RegistryRemapper.mapBlockState758To340(logY));
        assertEquals((17 << 4) | 4, RegistryRemapper.mapBlockState758To340(logX));
        assertEquals((17 << 4) | 12, RegistryRemapper.mapBlockState758To340(logWood));
    }

    @Test
    void entityAndItemAnchors() {
        // 豚：340 のモブ ID は 90、758 では 64。
        assertEquals(64, RegistryRemapper.mapEntityType340To758(90));
        assertEquals(90, RegistryRemapper.mapEntityType758To340(64));
        // ダイヤ剣：340 の 276 は 758 の 719。
        assertEquals(719, RegistryRemapper.mapItem340To758(276));
        assertEquals(276, RegistryRemapper.mapItem758To340(719));
        // 音：340 の 450 は block.stone.break。758 では 1018。
        assertEquals(1018, RegistryRemapper.mapSound340To758(450));
        assertEquals(450, RegistryRemapper.mapSound758To340(1018));
        // 平原：340 の 1 は 758 でも 1。
        assertEquals(1, RegistryRemapper.mapBiome340To758(1));
        assertEquals(1, RegistryRemapper.mapBiome758To340(1));
    }

    @Test
    void splitItemsFallBackToBaseItem() {
        // ID のみの表では、分かれたアイテムは基本アイテムへ寄る
        // （正確な付け替えはスロットの Damage タグが担う）。
        assertEquals(349, RegistryRemapper.mapItem758To340(802)); // salmon → fish
        assertEquals(425, RegistryRemapper.mapItem758To340(982)); // white_banner → banner
        assertEquals(355, RegistryRemapper.mapItem758To340(830)); // white_bed → bed
        assertEquals(397, RegistryRemapper.mapItem758To340(953)); // skeleton_skull → skull
        assertEquals(351, RegistryRemapper.mapItem758To340(807)); // ink_sac → dye
        assertEquals(383, RegistryRemapper.mapItem758To340(
                RegistryRemapper.mapItem340To758(383))); // spawn_egg 往復
    }

    @Test
    void itemRoundTripsHoldForRegistered() {
        // 340 のアイテム ID は疎（410種、最大2267）。未登録 ID は代替へ
        // 落ちるため、往復は登録済みの代表で固定する。全域では
        // 「758 側が常に有効な値になる」ことだけを見る。
        for (int i = 0; i <= RegistryRemapper.MAX_ITEM_340; i++) {
            assertTrue(RegistryRemapper.mapItem340To758(i) >= 0, "item " + i);
        }
        int[] registered = {1, 4, 35, 256, 267, 276, 297, 319, 331, 349,
                351, 355, 383, 397, 425, 2256, 2267};
        for (int i : registered) {
            int as758 = RegistryRemapper.mapItem340To758(i);
            assertEquals(i, RegistryRemapper.mapItem758To340(as758),
                    "item round trip " + i);
        }
    }

    @Test
    void entityRoundTripsHoldForRegistered() {
        // 340 のエンティティ ID は疎（1..200 に83件）。登録済みの範囲では
        // 往復が保存されること、未登録は代替（豚）へ落ちること。
        for (int i = 0; i <= RegistryRemapper.MAX_ENTITY_TYPE_340; i++) {
            int as758 = RegistryRemapper.mapEntityType340To758(i);
            assertTrue(as758 >= 0, "entity " + i);
            int back = RegistryRemapper.mapEntityType758To340(as758);
            assertTrue(back >= 0, "entity back " + i);
        }
        assertEquals(90, RegistryRemapper.mapEntityType758To340(
                RegistryRemapper.mapEntityType340To758(90)));
        assertEquals(50, RegistryRemapper.mapEntityType758To340(
                RegistryRemapper.mapEntityType340To758(50))); // creeper
    }

    @Test
    void biomeNames340() {
        assertEquals("plains", RegistryRemapper.biomeNames340().get(1));
    }

    @Test
    void soundNameFallbacksCoverRenames() {
        assertEquals("block.stone.break",
                RegistryRemapper.mapSoundName340To758("block.stone.break"));
        // 1.12.2 の block.slime.* は 758 では block.slime_block.*。
        assertEquals("block.slime_block.break",
                RegistryRemapper.mapSoundName340To758("block.slime.break"));
        assertEquals("block.slime.break",
                RegistryRemapper.mapSoundName758To340("block.slime_block.break"));
    }

    @Test
    void outOfRangeFallsBackSafely() {
        assertTrue(RegistryRemapper.mapBlockState340To758(-1) >= 0);
        assertTrue(RegistryRemapper.mapBlockState340To758(99999) >= 0);
        assertTrue(RegistryRemapper.mapItem340To758(-1) >= 0);
        assertTrue(RegistryRemapper.mapEntityType340To758(99999) >= 0);
        assertTrue(RegistryRemapper.mapBiome340To758(99999) >= 0);
    }
}
