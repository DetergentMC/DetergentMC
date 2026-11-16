package io.detergentmc.bedrock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.detergentmc.bedrock.BedrockChunkTranslator.BlockChange;
import io.detergentmc.bedrock.BedrockChunkTranslator.ChunkInput;
import io.detergentmc.bedrock.BedrockChunkTranslator.TranslatedChunk;
import io.detergentmc.bedrock.BedrockLevelChunkDecoder.Decoded;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Java チャンク → Bedrock LevelChunk の翻訳テスト。
 * 既知の地形パターンを作り、Bedrock 形式のデコーダー（翻訳とは別実装）で
 * 読んで、ブロックが正しい位置・種類で出ることを確認する。
 */
class BedrockChunkTranslatorTest {

    /** Java のバイオーム登録番号の順序（1.20.3 の既定。minecraft: 無し）。 */
    static final List<String> BIOME_NAMES_1203 = List.of(
            "badlands", "bamboo_jungle", "basalt_deltas", "beach", "birch_forest",
            "cherry_grove", "cold_ocean", "crimson_forest", "dark_forest",
            "deep_cold_ocean", "deep_dark", "deep_frozen_ocean", "deep_lukewarm_ocean",
            "deep_ocean", "desert", "dripstone_caves", "end_barrens", "end_highlands",
            "end_midlands", "eroded_badlands", "flower_forest", "forest", "frozen_ocean",
            "frozen_peaks", "frozen_river", "grove", "ice_spikes", "jagged_peaks",
            "jungle", "lukewarm_ocean", "lush_caves", "mangrove_swamp", "meadow",
            "mushroom_fields", "nether_wastes", "ocean", "old_growth_birch_forest",
            "old_growth_pine_taiga", "old_growth_spruce_taiga", "plains", "river",
            "savanna", "savanna_plateau", "small_end_islands", "snowy_beach",
            "snowy_plains", "snowy_slopes", "snowy_taiga", "soul_sand_valley",
            "sparse_jungle", "stony_peaks", "stony_shore", "sunflower_plains", "swamp",
            "taiga", "the_end", "the_void", "warm_ocean", "warped_forest",
            "windswept_forest", "windswept_gravelly_hills", "windswept_hills",
            "windswept_savanna", "wooded_badlands");

    /** 既知の地形：草の地面＋石の層、水たまり、水没ブロック、丸太、丸石。 */
    static final TestJavaChunk.BlockAt TERRAIN = (x, y, z) -> {
        if (x >= 4 && x <= 5 && z >= 4 && z <= 5 && y == 63) {
            return 80; // 水（深さ 0）
        }
        if (x == 2 && z == 2 && y == 63) {
            return 39; // 水没したマングローブの芽
        }
        if (x == 10 && z == 10 && y >= 64 && y <= 66) {
            return 131; // オークの丸太（axis=y）
        }
        if (x == 1 && z == 14 && y == 70) {
            return 14; // 丸石（転置の確認用：非対称な位置）
        }
        if (y < 60) {
            return 1; // 石
        }
        if (y < 63) {
            return 10; // 土
        }
        if (y == 63) {
            return 8; // 草ブロック
        }
        return 0; // 空気
    };

    /** 西半分は砂漠、東半分は平原。 */
    static final TestJavaChunk.BiomeAt TWO_BIOMES = (x, y, z) -> x < 8 ? 14 : 39;

    @Test
    void grassGroundStoneLayersDecodeAtCorrectPositions() {
        byte[] javaChunk = TestJavaChunk.build765(0, 0, TERRAIN, TWO_BIOMES, -64, 384, 7);
        TranslatedChunk translated = BedrockChunkTranslator.translate(
                new ChunkInput(0, 0, javaChunk, 765, -64, 384, BIOME_NAMES_1203));
        assertEquals(0, translated.chunkX());
        assertEquals(0, translated.chunkZ());
        assertEquals(24, translated.subChunkCount());
        Decoded decoded = BedrockLevelChunkDecoder.decode(translated.payload(), 24);
        assertTrue(decoded.fullyConsumed, "ペイロードを読み切ること");
        assertEquals(0, decoded.borderBlockCount);
        assertEquals(0, decoded.blockEntityCount);
        for (int s = 0; s < 24; s++) {
            assertEquals(-4 + s, decoded.subChunkY.get(s), "サブチャンク Y=" + s);
        }

        // 地面（セクション 7 は y 48〜63）。
        assertEquals(12183, decoded.blockAt(7, 8, 15, 8, 0), "y=63 は草ブロック");
        assertEquals(9550, decoded.blockAt(7, 8, 14, 8, 0), "y=62 は土");
        assertEquals(1774, decoded.blockAt(7, 8, 11, 8, 0), "y=59 は石");
        // 深い場所（セクション 4 は y 0〜15）。
        assertEquals(1774, decoded.blockAt(4, 0, 0, 0, 0), "y=0 は石");
        // 空中は空気。
        assertEquals(11881, decoded.blockAt(8, 0, 10, 0, 0), "y=74 は空気");

        // 水たまり。
        assertEquals(9304, decoded.blockAt(7, 4, 15, 4, 0), "水たまりは水");
        assertEquals(9550, decoded.blockAt(7, 4, 14, 4, 0), "水の下は土");
        // 丸太（axis=y）。
        assertEquals(725, decoded.blockAt(8, 10, 0, 10, 0), "y=64 の丸太");
        assertEquals(725, decoded.blockAt(8, 10, 2, 10, 0), "y=66 の丸太");
        // 転置の確認：非対称な位置の丸石がそのままの位置で出ること。
        assertEquals(5995, decoded.blockAt(8, 1, 6, 14, 0), "y=70 の丸石");
        assertEquals(11881, decoded.blockAt(8, 14, 6, 1, 0), "転置されていたらここに出る（空気のはず）");

        // 水没ブロック：層 0 が芽、層 1 が水。
        assertEquals(2, decoded.layerCounts.get(7), "水没があるセクションは 2 層");
        int propagule = BedrockBlockRuntimeIds.runtimeId(39);
        assertEquals(propagule, decoded.blockAt(7, 2, 15, 2, 0), "水没した芽");
        assertEquals(9304, decoded.blockAt(7, 2, 15, 2, 1), "水没層は水");
        // 水没していない場所の層 1 は無い（層数 1 のセクション）。
        assertEquals(1, decoded.layerCounts.get(8), "水没が無いセクションは 1 層");

        // バイオーム：西半分は砂漠、東半分は平原。
        assertEquals(2, decoded.biomeAt(7, 0, 3, 0), "西は砂漠");
        assertEquals(1, decoded.biomeAt(7, 2, 3, 0), "東は平原");
        assertEquals(2, decoded.biomeAt(7, 1, 0, 3), "砂漠は高さ方向にも同じ");
    }

    @Test
    void airOnlyChunkTranslatesToAllAir() {
        byte[] javaChunk = TestJavaChunk.build765(3, -2,
                (x, y, z) -> 0, (x, y, z) -> 39);
        TranslatedChunk translated = BedrockChunkTranslator.translate(
                new ChunkInput(3, -2, javaChunk, 765, -64, 384, BIOME_NAMES_1203));
        assertEquals(3, translated.chunkX());
        assertEquals(-2, translated.chunkZ());
        Decoded decoded = BedrockLevelChunkDecoder.decode(translated.payload(), 24);
        assertTrue(decoded.fullyConsumed);
        for (int s = 0; s < 24; s++) {
            assertEquals(1, decoded.layerCounts.get(s));
            assertEquals(11881, decoded.blockAt(s, 0, 0, 0, 0));
            assertEquals(1, decoded.biomeAt(s, 0, 0, 0));
        }
    }

    @Test
    void protocol763ChunkUsesNamedHeightmaps() {
        byte[] javaChunk = TestJavaChunk.build763(0, 0, TERRAIN, TWO_BIOMES);
        TranslatedChunk translated = BedrockChunkTranslator.translate(
                new ChunkInput(0, 0, javaChunk, 763, -64, 384, BIOME_NAMES_1203));
        Decoded decoded = BedrockLevelChunkDecoder.decode(translated.payload(), 24);
        assertEquals(12183, decoded.blockAt(7, 8, 15, 8, 0), "763 の草ブロックも草になること");
        assertEquals(1774, decoded.blockAt(7, 8, 11, 8, 0), "763 の石も石になること");
    }

    @Test
    void protocol764And766NormalizeTo765() {
        byte[] javaChunk = TestJavaChunk.build765(0, 0, TERRAIN, TWO_BIOMES);
        for (int protocol : List.of(764, 766)) {
            TranslatedChunk translated = BedrockChunkTranslator.translate(
                    new ChunkInput(0, 0, javaChunk, protocol, -64, 384, BIOME_NAMES_1203));
            Decoded decoded = BedrockLevelChunkDecoder.decode(translated.payload(), 24);
            assertEquals(12183, decoded.blockAt(7, 8, 15, 8, 0),
                    protocol + " の草ブロックも草になること");
            assertEquals(1774, decoded.blockAt(4, 0, 0, 0, 0),
                    protocol + " の石も石になること");
        }
    }

    @Test
    void netherChunkHasEightSubChunks() {
        byte[] javaChunk = TestJavaChunk.build765(0, 0,
                (x, y, z) -> 1, (x, y, z) -> 35, 0, 128, -1);
        TranslatedChunk translated = BedrockChunkTranslator.translate(
                new ChunkInput(0, 0, javaChunk, 765, 0, 128, BIOME_NAMES_1203));
        assertEquals(8, translated.subChunkCount());
        Decoded decoded = BedrockLevelChunkDecoder.decode(translated.payload(), 8);
        assertTrue(decoded.fullyConsumed);
        for (int s = 0; s < 8; s++) {
            assertEquals(s, decoded.subChunkY.get(s), "ネザーのサブチャンク Y=" + s);
        }
    }

    @Test
    void translateBlockChange() {
        BlockChange grass = BedrockChunkTranslator.translateBlockChange(8, 765);
        assertEquals(12183, grass.runtimeId());
        assertFalse(grass.waterlogged());
        BlockChange propagule = BedrockChunkTranslator.translateBlockChange(39, 765);
        assertEquals(BedrockBlockRuntimeIds.runtimeId(39), propagule.runtimeId());
        assertTrue(propagule.waterlogged());
        BlockChange stone764 = BedrockChunkTranslator.translateBlockChange(1, 764);
        assertEquals(1774, stone764.runtimeId());
    }

    @Test
    void biomeFallbackWithoutNames() {
        byte[] javaChunk = TestJavaChunk.build765(0, 0, TERRAIN, TWO_BIOMES);
        TranslatedChunk translated = BedrockChunkTranslator.translate(
                new ChunkInput(0, 0, javaChunk, 765, -64, 384, null));
        Decoded decoded = BedrockLevelChunkDecoder.decode(translated.payload(), 24);
        assertEquals(2, decoded.biomeAt(7, 0, 3, 0), "名前が無くても番号で砂漠になること");
        assertEquals(1, decoded.biomeAt(7, 2, 3, 0), "名前が無くても番号で平原になること");
    }

    // ---- 757（1.18／1.18.1）----

    /** 757 のバイオーム登録番号の順序（61 件。minecraft: 無し）。 */
    static final List<String> BIOME_NAMES_118 = List.of(
            "the_void", "plains", "sunflower_plains", "snowy_plains", "ice_spikes",
            "desert", "swamp", "forest", "flower_forest", "birch_forest",
            "dark_forest", "old_growth_birch_forest", "old_growth_pine_taiga",
            "old_growth_spruce_taiga", "taiga", "snowy_taiga", "savanna",
            "savanna_plateau", "windswept_hills", "windswept_gravelly_hills",
            "windswept_forest", "windswept_savanna", "jungle", "sparse_jungle",
            "bamboo_jungle", "badlands", "eroded_badlands", "wooded_badlands",
            "meadow", "grove", "snowy_slopes", "frozen_peaks", "jagged_peaks",
            "stony_peaks", "river", "frozen_river", "beach", "snowy_beach",
            "stony_shore", "warm_ocean", "lukewarm_ocean", "deep_lukewarm_ocean",
            "ocean", "deep_ocean", "cold_ocean", "deep_cold_ocean", "frozen_ocean",
            "deep_frozen_ocean", "mushroom_fields", "dripstone_caves", "lush_caves",
            "nether_wastes", "warped_forest", "crimson_forest", "soul_sand_valley",
            "basalt_deltas", "the_end", "end_highlands", "end_midlands",
            "small_end_islands", "end_barrens");

    /**
     * 757 の既知の地形。状態番号は 1.18 のもの（石 1、土 10、草 9、水 34、
     * オークの丸太 77、丸石 14。1.18_blocks.json で確認）。
     * マングローブの芽は 1.18 には無いので水没の確認は入れない。
     */
    static final TestJavaChunk.BlockAt TERRAIN_757 = (x, y, z) -> {
        if (x >= 4 && x <= 5 && z >= 4 && z <= 5 && y == 63) {
            return 34; // 水（level 0）
        }
        if (x == 10 && z == 10 && y >= 64 && y <= 66) {
            return 77; // オークの丸太（axis=y）
        }
        if (x == 1 && z == 14 && y == 70) {
            return 14; // 丸石（転置の確認用：非対称な位置）
        }
        if (y < 60) {
            return 1; // 石
        }
        if (y < 63) {
            return 10; // 土
        }
        if (y == 63) {
            return 9; // 草ブロック
        }
        return 0; // 空気
    };

    /** 西半分は砂漠（5）、東半分は平原（1）。757 のバイオーム番号。 */
    static final TestJavaChunk.BiomeAt TWO_BIOMES_757 = (x, y, z) -> x < 8 ? 5 : 1;

    @Test
    void protocol757ChunkRoundTrip() {
        byte[] javaChunk = TestJavaChunk.build757(0, 0, TERRAIN_757, TWO_BIOMES_757);
        TranslatedChunk translated = BedrockChunkTranslator.translate(
                new ChunkInput(0, 0, javaChunk, 757, -64, 384, BIOME_NAMES_118));
        assertEquals(0, translated.chunkX());
        assertEquals(0, translated.chunkZ());
        assertEquals(24, translated.subChunkCount());
        Decoded decoded = BedrockLevelChunkDecoder.decode(translated.payload(), 24);
        assertTrue(decoded.fullyConsumed, "ペイロードを読み切ること");
        assertEquals(0, decoded.borderBlockCount);
        assertEquals(0, decoded.blockEntityCount);

        // 地面（セクション 7 は y 48〜63）。
        assertEquals(12183, decoded.blockAt(7, 8, 15, 8, 0), "757 の y=63 は草ブロック");
        assertEquals(9550, decoded.blockAt(7, 8, 14, 8, 0), "757 の y=62 は土");
        assertEquals(1774, decoded.blockAt(7, 8, 11, 8, 0), "757 の y=59 は石");
        assertEquals(1774, decoded.blockAt(4, 0, 0, 0, 0), "757 の y=0 は石");
        assertEquals(11881, decoded.blockAt(8, 0, 10, 0, 0), "757 の y=74 は空気");
        // 水たまり（757 の水 34 → 765 の水 80）。
        assertEquals(9304, decoded.blockAt(7, 4, 15, 4, 0), "757 の水たまりは水");
        assertEquals(9550, decoded.blockAt(7, 4, 14, 4, 0), "水の下は土");
        // 丸太（757 の 77 → 765 の 131）。
        assertEquals(725, decoded.blockAt(8, 10, 0, 10, 0), "757 の y=64 の丸太");
        assertEquals(725, decoded.blockAt(8, 10, 2, 10, 0), "757 の y=66 の丸太");
        // 転置の確認。
        assertEquals(5995, decoded.blockAt(8, 1, 6, 14, 0), "757 の y=70 の丸石");
        assertEquals(11881, decoded.blockAt(8, 14, 6, 1, 0), "転置されていたらここに出る（空気のはず）");

        // バイオーム：西半分は砂漠、東半分は平原（757 の codec 名で引く）。
        assertEquals(2, decoded.biomeAt(7, 0, 3, 0), "757 の西は砂漠");
        assertEquals(1, decoded.biomeAt(7, 2, 3, 0), "757 の東は平原");
    }

    @Test
    void protocol757NormalizesBlockStates() {
        assertEquals(1, BedrockChunkTranslator.normalizeBlockState(1, 757), "757 の石は石");
        assertEquals(9, BedrockChunkTranslator.normalizeBlockState(9, 757), "757 の草は草");
        assertEquals(10, BedrockChunkTranslator.normalizeBlockState(10, 757), "757 の土は土");
        assertEquals(14, BedrockChunkTranslator.normalizeBlockState(14, 757), "757 の丸石は丸石");
        assertEquals(80, BedrockChunkTranslator.normalizeBlockState(34, 757),
                "757 の水（level 0）は 765 の水（level 0）");
        assertEquals(131, BedrockChunkTranslator.normalizeBlockState(77, 757),
                "757 のオークの丸太（axis=y）は 765 の丸太");
        assertEquals(1, BedrockChunkTranslator.normalizeBlockState(99999, 757),
                "範囲外の状態は石に落とす");
    }

    /**
     * 757 の BitStorage は long の境界をまたがない詰め（non-straddling）。
     * 20 種の状態があるセクションは BPE 5 で 342 long になり、straddling
     * （320 long）で読むとずれる。実バニラ 1.18 の生キャプチャで確認
     * （2026-11-15：BPE 5 の dataLen は 342）。
     */
    @Test
    void protocol757NonStraddlingPacking() {
        TestJavaChunk.BlockAt twentyStates = (x, y, z) -> {
            if (y >= 48 && y < 64) {
                int j = x | (z << 4) | ((y - 48) << 8);
                return 1 + (j % 20); // 状態 1〜20（20 種で BPE 5）
            }
            if (y < 48) {
                return 1;
            }
            return 0;
        };
        byte[] javaChunk = TestJavaChunk.build757(0, 0, twentyStates, (x, y, z) -> 1);
        TranslatedChunk translated = BedrockChunkTranslator.translate(
                new ChunkInput(0, 0, javaChunk, 757, -64, 384, BIOME_NAMES_118));
        Decoded decoded = BedrockLevelChunkDecoder.decode(translated.payload(), 24);
        assertTrue(decoded.fullyConsumed, "BPE 5 のセクションを読み切ること");
        assertEquals(1774, decoded.blockAt(7, 0, 0, 0, 0), "状態 1 は石");
        assertEquals(9550, decoded.blockAt(7, 9, 0, 0, 0), "状態 10 は土");
        assertEquals(12183, decoded.blockAt(7, 8, 0, 0, 0), "状態 9 は草ブロック");
    }

    @Test
    void protocol757BiomeFallbackToPlains() {
        // 名前が無い場合、範囲外のバイオーム番号は平原（1）に落とす。
        byte[] javaChunk = TestJavaChunk.build757(0, 0,
                (x, y, z) -> 1, (x, y, z) -> 100);
        TranslatedChunk translated = BedrockChunkTranslator.translate(
                new ChunkInput(0, 0, javaChunk, 757, -64, 384, null));
        Decoded decoded = BedrockLevelChunkDecoder.decode(translated.payload(), 24);
        assertTrue(decoded.fullyConsumed);
        assertEquals(1, decoded.biomeAt(7, 0, 0, 0), "範囲外のバイオームは平原");
        assertEquals(1, decoded.biomeAt(0, 0, 0, 0), "範囲外のバイオームは平原");
    }

    // ---- 756（1.17.1）----

    /**
     * 756 のバイオーム登録番号の順序（81 件。id 0〜175 の非連番。minecraft: 無し）。
     * minecraft-data pc/1.17/biomes.json の id 順で、隙間の id は ""（平原に落とす）。
     * 756 の chunk はバイオームを int[1024] で持ち、値はこの id そのもの。
     */
    static final List<String> BIOME_NAMES_117 = List.of(
            "ocean", "plains", "desert", "mountains", "forest", "taiga", "swamp", "river",
            "nether_wastes", "the_end", "frozen_ocean", "frozen_river", "snowy_tundra", "snowy_mountains", "mushroom_fields", "mushroom_field_shore",
            "beach", "desert_hills", "wooded_hills", "taiga_hills", "mountain_edge", "jungle", "jungle_hills", "jungle_edge",
            "deep_ocean", "stone_shore", "snowy_beach", "birch_forest", "birch_forest_hills", "dark_forest", "snowy_taiga", "snowy_taiga_hills",
            "giant_tree_taiga", "giant_tree_taiga_hills", "wooded_mountains", "savanna", "savanna_plateau", "badlands", "wooded_badlands_plateau", "badlands_plateau",
            "small_end_islands", "end_midlands", "end_highlands", "end_barrens", "warm_ocean", "lukewarm_ocean", "cold_ocean", "deep_warm_ocean",
            "deep_lukewarm_ocean", "deep_cold_ocean", "deep_frozen_ocean", "", "", "", "", "",
            "", "", "", "", "", "", "", "",
            "", "", "", "", "", "", "", "",
            "", "", "", "", "", "", "", "",
            "", "", "", "", "", "", "", "",
            "", "", "", "", "", "", "", "",
            "", "", "", "", "", "", "", "",
            "", "", "", "", "", "", "", "",
            "", "", "", "", "", "", "", "",
            "", "", "", "", "", "", "", "the_void",
            "", "sunflower_plains", "desert_lakes", "gravelly_mountains", "flower_forest", "taiga_mountains", "swamp_hills", "",
            "", "", "", "", "ice_spikes", "", "", "",
            "", "", "", "", "", "modified_jungle", "", "modified_jungle_edge",
            "", "", "", "tall_birch_forest", "tall_birch_hills", "dark_forest_hills", "snowy_taiga_mountains", "",
            "giant_spruce_taiga", "giant_spruce_taiga_hills", "modified_gravelly_mountains", "shattered_savanna", "shattered_savanna_plateau", "eroded_badlands", "modified_wooded_badlands_plateau", "modified_badlands_plateau",
            "bamboo_jungle", "bamboo_jungle_hills", "soul_sand_valley", "crimson_forest", "warped_forest", "basalt_deltas", "dripstone_caves", "lush_caves"
            );

    /** 西半分は砂漠（2）、東半分は平原（1）。756 のバイオーム番号。 */
    static final TestJavaChunk.BiomeAt TWO_BIOMES_756 = (x, y, z) -> x < 8 ? 2 : 1;

    @Test
    void protocol756ChunkRoundTrip() {
        // ブロック状態は 756・757 で同一順序のため TERRAIN_757 をそのまま使う。
        byte[] javaChunk = TestJavaChunk.build756(0, 0, TERRAIN_757, TWO_BIOMES_756);
        TranslatedChunk translated = BedrockChunkTranslator.translate(
                new ChunkInput(0, 0, javaChunk, 756, 0, 256, BIOME_NAMES_117));
        assertEquals(0, translated.chunkX());
        assertEquals(0, translated.chunkZ());
        assertEquals(16, translated.subChunkCount());
        Decoded decoded = BedrockLevelChunkDecoder.decode(translated.payload(), 16);
        assertTrue(decoded.fullyConsumed, "ペイロードを読み切ること");
        assertEquals(0, decoded.borderBlockCount);
        assertEquals(0, decoded.blockEntityCount);
        for (int s = 0; s < 16; s++) {
            assertEquals(s, decoded.subChunkY.get(s), "サブチャンク Y=" + s);
        }

        // 地面（セクション 3 は y 48〜63）。
        assertEquals(12183, decoded.blockAt(3, 8, 15, 8, 0), "756 の y=63 は草ブロック");
        assertEquals(9550, decoded.blockAt(3, 8, 14, 8, 0), "756 の y=62 は土");
        assertEquals(1774, decoded.blockAt(3, 8, 11, 8, 0), "756 の y=59 は石");
        assertEquals(1774, decoded.blockAt(0, 0, 0, 0, 0), "756 の y=0 は石");
        assertEquals(11881, decoded.blockAt(4, 0, 10, 0, 0), "756 の y=74 は空気");
        // 水たまり（756 の水 34 → 765 の水 80）。
        assertEquals(9304, decoded.blockAt(3, 4, 15, 4, 0), "756 の水たまりは水");
        assertEquals(9550, decoded.blockAt(3, 4, 14, 4, 0), "水の下は土");
        // 丸太（756 の 77 → 765 の 131）。
        assertEquals(725, decoded.blockAt(4, 10, 0, 10, 0), "756 の y=64 の丸太");
        assertEquals(725, decoded.blockAt(4, 10, 2, 10, 0), "756 の y=66 の丸太");
        // 転置の確認。
        assertEquals(5995, decoded.blockAt(4, 1, 6, 14, 0), "756 の y=70 の丸石");
        assertEquals(11881, decoded.blockAt(4, 14, 6, 1, 0), "転置されていたらここに出る（空気のはず）");

        // バイオーム：西半分は砂漠、東半分は平原（756 の codec 名で引く）。
        assertEquals(2, decoded.biomeAt(3, 0, 3, 0), "756 の西は砂漠");
        assertEquals(1, decoded.biomeAt(3, 2, 3, 0), "756 の東は平原");
    }

    @Test
    void protocol756NormalizesBlockStates() {
        assertEquals(1, BedrockChunkTranslator.normalizeBlockState(1, 756), "756 の石は石");
        assertEquals(9, BedrockChunkTranslator.normalizeBlockState(9, 756), "756 の草は草");
        assertEquals(10, BedrockChunkTranslator.normalizeBlockState(10, 756), "756 の土は土");
        assertEquals(14, BedrockChunkTranslator.normalizeBlockState(14, 756), "756 の丸石は丸石");
        assertEquals(80, BedrockChunkTranslator.normalizeBlockState(34, 756),
                "756 の水（level 0）は 765 の水（level 0）");
        assertEquals(131, BedrockChunkTranslator.normalizeBlockState(77, 756),
                "756 のオークの丸太（axis=y）は 765 の丸太");
        assertEquals(1, BedrockChunkTranslator.normalizeBlockState(99999, 756),
                "範囲外の状態は石に落とす");
    }

    /**
     * 756 の BitStorage は long の境界をまたがない詰め（non-straddling）。
     * 20 種の状態があるセクションは BPE 5 で 342 long になり、straddling
     * （320 long）で読むとずれる。実バニラ 1.17.1 の生キャプチャで確認
     * （2026-11-16：cap756-chunk-21-17.bin で BPE 5 の dataLen は 342）。
     */
    @Test
    void protocol756NonStraddlingPacking() {
        TestJavaChunk.BlockAt twentyStates = (x, y, z) -> {
            if (y >= 48 && y < 64) {
                int j = x | (z << 4) | ((y - 48) << 8);
                return 1 + (j % 20); // 状態 1〜20（20 種で BPE 5）
            }
            if (y < 48) {
                return 1;
            }
            return 0;
        };
        byte[] javaChunk = TestJavaChunk.build756(0, 0, twentyStates, (x, y, z) -> 1);
        TranslatedChunk translated = BedrockChunkTranslator.translate(
                new ChunkInput(0, 0, javaChunk, 756, 0, 256, BIOME_NAMES_117));
        Decoded decoded = BedrockLevelChunkDecoder.decode(translated.payload(), 16);
        assertTrue(decoded.fullyConsumed, "BPE 5 のセクションを読み切ること");
        assertEquals(1774, decoded.blockAt(3, 0, 0, 0, 0), "状態 1 は石");
        assertEquals(9550, decoded.blockAt(3, 9, 0, 0, 0), "状態 10 は土");
        assertEquals(12183, decoded.blockAt(3, 8, 0, 0, 0), "状態 9 は草ブロック");
    }

    @Test
    void protocol756BiomeFallbackToPlains() {
        // 名前が無い場合、範囲外のバイオーム番号は平原（1）に落とす。
        byte[] javaChunk = TestJavaChunk.build756(0, 0,
                (x, y, z) -> 1, (x, y, z) -> 200);
        TranslatedChunk translated = BedrockChunkTranslator.translate(
                new ChunkInput(0, 0, javaChunk, 756, 0, 256, null));
        Decoded decoded = BedrockLevelChunkDecoder.decode(translated.payload(), 16);
        assertTrue(decoded.fullyConsumed);
        assertEquals(1, decoded.biomeAt(3, 0, 0, 0), "範囲外のバイオームは平原");
        assertEquals(1, decoded.biomeAt(0, 0, 0, 0), "範囲外のバイオームは平原");
    }

    @Test
    void protocol756SparseSectionsAreAirButKeepBiomes() {
        // bitMap にセクション 3・4 だけが立っているチャンク。無いセクションは
        // ブロックだけ空気で補完し、バイオームは int[1024] の値をそのまま使う。
        byte[] javaChunk = TestJavaChunk.build756(0, 0, TERRAIN_757, TWO_BIOMES_756, 0x18);
        TranslatedChunk translated = BedrockChunkTranslator.translate(
                new ChunkInput(0, 0, javaChunk, 756, 0, 256, BIOME_NAMES_117));
        Decoded decoded = BedrockLevelChunkDecoder.decode(translated.payload(), 16);
        assertTrue(decoded.fullyConsumed, "ペイロードを読み切ること");
        assertEquals(11881, decoded.blockAt(0, 0, 0, 0, 0), "bitMap に無いセクションは空気");
        assertEquals(11881, decoded.blockAt(15, 0, 0, 0, 0), "bitMap に無いセクションは空気");
        assertEquals(12183, decoded.blockAt(3, 8, 15, 8, 0), "あるセクションの草ブロックは残る");
        assertEquals(2, decoded.biomeAt(0, 0, 0, 0), "無いセクションのバイオームも配列の値（砂漠）");
        assertEquals(1, decoded.biomeAt(0, 2, 0, 0), "無いセクションのバイオームも配列の値（平原）");
    }

    // ---- 755（1.17）----

    /**
     * 755 のチャンク形式は 756 と完全同一（Stage B §14.4 実測。packet_map_chunk は
     * protocol.json で完全一致し、実バニラ 1.17／1.17.1 の Chunk Data クラス qw は
     * フィールド同一で javap -c の差分は難読化クラス名の付け替えのみ。レジストリ差分も
     * 0 件）。そのためブロック状態は 756・757 と同一順序で TERRAIN_757 をそのまま使い、
     * バイオームも 756 と同じ 81 件・id 0〜175 の非連番で BIOME_NAMES_117／
     * TWO_BIOMES_756 をそのまま使う。1.17.1 の生キャプチャ cap756-chunk-21-17.bin は
     * 755 形式のチャンクでもある。
     */

    /** 西半分は砂漠（2）、東半分は平原（1）。755 のバイオーム番号（756 と同一）。 */
    static final TestJavaChunk.BiomeAt TWO_BIOMES_755 = (x, y, z) -> x < 8 ? 2 : 1;

    @Test
    void protocol755ChunkRoundTrip() {
        // ブロック状態は 755・756・757 で同一順序のため TERRAIN_757 をそのまま使う。
        byte[] javaChunk = TestJavaChunk.build755(0, 0, TERRAIN_757, TWO_BIOMES_755);
        TranslatedChunk translated = BedrockChunkTranslator.translate(
                new ChunkInput(0, 0, javaChunk, 755, 0, 256, BIOME_NAMES_117));
        assertEquals(0, translated.chunkX());
        assertEquals(0, translated.chunkZ());
        assertEquals(16, translated.subChunkCount());
        Decoded decoded = BedrockLevelChunkDecoder.decode(translated.payload(), 16);
        assertTrue(decoded.fullyConsumed, "ペイロードを読み切ること");
        assertEquals(0, decoded.borderBlockCount);
        assertEquals(0, decoded.blockEntityCount);
        for (int s = 0; s < 16; s++) {
            assertEquals(s, decoded.subChunkY.get(s), "サブチャンク Y=" + s);
        }

        // 地面（セクション 3 は y 48〜63）。
        assertEquals(12183, decoded.blockAt(3, 8, 15, 8, 0), "755 の y=63 は草ブロック");
        assertEquals(9550, decoded.blockAt(3, 8, 14, 8, 0), "755 の y=62 は土");
        assertEquals(1774, decoded.blockAt(3, 8, 11, 8, 0), "755 の y=59 は石");
        assertEquals(1774, decoded.blockAt(0, 0, 0, 0, 0), "755 の y=0 は石");
        assertEquals(11881, decoded.blockAt(4, 0, 10, 0, 0), "755 の y=74 は空気");
        // 水たまり（755 の水 34 → 765 の水 80）。
        assertEquals(9304, decoded.blockAt(3, 4, 15, 4, 0), "755 の水たまりは水");
        assertEquals(9550, decoded.blockAt(3, 4, 14, 4, 0), "水の下は土");
        // 丸太（755 の 77 → 765 の 131）。
        assertEquals(725, decoded.blockAt(4, 10, 0, 10, 0), "755 の y=64 の丸太");
        assertEquals(725, decoded.blockAt(4, 10, 2, 10, 0), "755 の y=66 の丸太");
        // 転置の確認。
        assertEquals(5995, decoded.blockAt(4, 1, 6, 14, 0), "755 の y=70 の丸石");
        assertEquals(11881, decoded.blockAt(4, 14, 6, 1, 0), "転置されていたらここに出る（空気のはず）");

        // バイオーム：西半分は砂漠、東半分は平原（755 の codec 名で引く）。
        assertEquals(2, decoded.biomeAt(3, 0, 3, 0), "755 の西は砂漠");
        assertEquals(1, decoded.biomeAt(3, 2, 3, 0), "755 の東は平原");
    }

    @Test
    void protocol755NormalizesBlockStates() {
        assertEquals(1, BedrockChunkTranslator.normalizeBlockState(1, 755), "755 の石は石");
        assertEquals(9, BedrockChunkTranslator.normalizeBlockState(9, 755), "755 の草は草");
        assertEquals(10, BedrockChunkTranslator.normalizeBlockState(10, 755), "755 の土は土");
        assertEquals(14, BedrockChunkTranslator.normalizeBlockState(14, 755), "755 の丸石は丸石");
        assertEquals(80, BedrockChunkTranslator.normalizeBlockState(34, 755),
                "755 の水（level 0）は 765 の水（level 0）");
        assertEquals(131, BedrockChunkTranslator.normalizeBlockState(77, 755),
                "755 のオークの丸太（axis=y）は 765 の丸太");
        assertEquals(1, BedrockChunkTranslator.normalizeBlockState(99999, 755),
                "範囲外の状態は石に落とす");
    }

    /**
     * 755 の BitStorage は long の境界をまたがない詰め（non-straddling）。
     * 20 種の状態があるセクションは BPE 5 で 342 long になり、straddling
     * （320 long）で読むとずれる。756 と Chunk Data が完全同一のため、
     * 実バニラ 1.17.1 の生キャプチャの裏付け（2026-11-16：
     * cap756-chunk-21-17.bin で BPE 5 の dataLen は 342）を流用する。
     */
    @Test
    void protocol755NonStraddlingPacking() {
        TestJavaChunk.BlockAt twentyStates = (x, y, z) -> {
            if (y >= 48 && y < 64) {
                int j = x | (z << 4) | ((y - 48) << 8);
                return 1 + (j % 20); // 状態 1〜20（20 種で BPE 5）
            }
            if (y < 48) {
                return 1;
            }
            return 0;
        };
        byte[] javaChunk = TestJavaChunk.build755(0, 0, twentyStates, (x, y, z) -> 1);
        TranslatedChunk translated = BedrockChunkTranslator.translate(
                new ChunkInput(0, 0, javaChunk, 755, 0, 256, BIOME_NAMES_117));
        Decoded decoded = BedrockLevelChunkDecoder.decode(translated.payload(), 16);
        assertTrue(decoded.fullyConsumed, "BPE 5 のセクションを読み切ること");
        assertEquals(1774, decoded.blockAt(3, 0, 0, 0, 0), "状態 1 は石");
        assertEquals(9550, decoded.blockAt(3, 9, 0, 0, 0), "状態 10 は土");
        assertEquals(12183, decoded.blockAt(3, 8, 0, 0, 0), "状態 9 は草ブロック");
    }

    @Test
    void protocol755BiomeFallbackToPlains() {
        // 名前が無い場合、範囲外のバイオーム番号は平原（1）に落とす。
        byte[] javaChunk = TestJavaChunk.build755(0, 0,
                (x, y, z) -> 1, (x, y, z) -> 200);
        TranslatedChunk translated = BedrockChunkTranslator.translate(
                new ChunkInput(0, 0, javaChunk, 755, 0, 256, null));
        Decoded decoded = BedrockLevelChunkDecoder.decode(translated.payload(), 16);
        assertTrue(decoded.fullyConsumed);
        assertEquals(1, decoded.biomeAt(3, 0, 0, 0), "範囲外のバイオームは平原");
        assertEquals(1, decoded.biomeAt(0, 0, 0, 0), "範囲外のバイオームは平原");
    }

    @Test
    void protocol755SparseSectionsAreAirButKeepBiomes() {
        // bitMap にセクション 3・4 だけが立っているチャンク。無いセクションは
        // ブロックだけ空気で補完し、バイオームは int[1024] の値をそのまま使う。
        // airBlocks() は Java の空気（状態 0）で埋める（756 の柱 2 で直した
        // 二重変換バグの修正済み。755 経路も同じ airBlocks() を使う）。
        byte[] javaChunk = TestJavaChunk.build755(0, 0, TERRAIN_757, TWO_BIOMES_755, 0x18);
        TranslatedChunk translated = BedrockChunkTranslator.translate(
                new ChunkInput(0, 0, javaChunk, 755, 0, 256, BIOME_NAMES_117));
        Decoded decoded = BedrockLevelChunkDecoder.decode(translated.payload(), 16);
        assertTrue(decoded.fullyConsumed, "ペイロードを読み切ること");
        assertEquals(11881, decoded.blockAt(0, 0, 0, 0, 0), "bitMap に無いセクションは空気");
        assertEquals(11881, decoded.blockAt(15, 0, 0, 0, 0), "bitMap に無いセクションは空気");
        assertEquals(12183, decoded.blockAt(3, 8, 15, 8, 0), "あるセクションの草ブロックは残る");
        assertEquals(2, decoded.biomeAt(0, 0, 0, 0), "無いセクションのバイオームも配列の値（砂漠）");
        assertEquals(1, decoded.biomeAt(0, 2, 0, 0), "無いセクションのバイオームも配列の値（平原）");
    }

    // ---- 754（1.16.4／1.16.5）----

    /**
     * 754 のバイオーム登録番号の順序（79 件。id 0〜173 の非連番。minecraft: 無し）。
     * 755 の 81 件から dripstone_caves（174）・lush_caves（175）を除いたもので、
     * 0〜173 は 755 と同じ id・同じ名前（Stage A §11.1 実測）。
     */
    static final List<String> BIOME_NAMES_116 = BIOME_NAMES_117.subList(0, 174);

    /**
     * 754 の既知の地形。状態番号は 1.16.5 のもの（石 1、土 10、草 9、水 34、
     * オークの丸太 axis=y は 74（755 では 77）、丸石 14。
     * blocks-1.16.5-reports.json で確認）。
     */
    static final TestJavaChunk.BlockAt TERRAIN_754 = (x, y, z) -> {
        if (x >= 4 && x <= 5 && z >= 4 && z <= 5 && y == 63) {
            return 34; // 水（level 0）
        }
        if (x == 10 && z == 10 && y >= 64 && y <= 66) {
            return 74; // オークの丸太（axis=y。754 は 74、755 は 77）
        }
        if (x == 1 && z == 14 && y == 70) {
            return 14; // 丸石（転置の確認用：非対称な位置）
        }
        if (y < 60) {
            return 1; // 石
        }
        if (y < 63) {
            return 10; // 土
        }
        if (y == 63) {
            return 9; // 草ブロック
        }
        return 0; // 空気
    };

    /** 西半分は砂漠（2）、東半分は平原（1）。754 のバイオーム番号（755 と同一）。 */
    static final TestJavaChunk.BiomeAt TWO_BIOMES_754 = (x, y, z) -> x < 8 ? 2 : 1;

    @Test
    void protocol754ChunkRoundTrip() {
        // ブロック状態は 754→755 の表で 765 に正規化される（丸太 74→77→131）。
        byte[] javaChunk = TestJavaChunk.build754(0, 0, TERRAIN_754, TWO_BIOMES_754);
        TranslatedChunk translated = BedrockChunkTranslator.translate(
                new ChunkInput(0, 0, javaChunk, 754, 0, 256, BIOME_NAMES_116));
        assertEquals(0, translated.chunkX());
        assertEquals(0, translated.chunkZ());
        assertEquals(16, translated.subChunkCount());
        Decoded decoded = BedrockLevelChunkDecoder.decode(translated.payload(), 16);
        assertTrue(decoded.fullyConsumed, "ペイロードを読み切ること");
        assertEquals(0, decoded.borderBlockCount);
        assertEquals(0, decoded.blockEntityCount);
        for (int s = 0; s < 16; s++) {
            assertEquals(s, decoded.subChunkY.get(s), "サブチャンク Y=" + s);
        }

        // 地面（セクション 3 は y 48〜63）。
        assertEquals(12183, decoded.blockAt(3, 8, 15, 8, 0), "754 の y=63 は草ブロック");
        assertEquals(9550, decoded.blockAt(3, 8, 14, 8, 0), "754 の y=62 は土");
        assertEquals(1774, decoded.blockAt(3, 8, 11, 8, 0), "754 の y=59 は石");
        assertEquals(1774, decoded.blockAt(0, 0, 0, 0, 0), "754 の y=0 は石");
        assertEquals(11881, decoded.blockAt(4, 0, 10, 0, 0), "754 の y=74 は空気");
        // 水たまり（754 の水 34 → 765 の水 80）。
        assertEquals(9304, decoded.blockAt(3, 4, 15, 4, 0), "754 の水たまりは水");
        assertEquals(9550, decoded.blockAt(3, 4, 14, 4, 0), "水の下は土");
        // 丸太（754 の 74 → 755 の 77 → 765 の 131）。
        assertEquals(725, decoded.blockAt(4, 10, 0, 10, 0), "754 の y=64 の丸太");
        assertEquals(725, decoded.blockAt(4, 10, 2, 10, 0), "754 の y=66 の丸太");
        // 転置の確認。
        assertEquals(5995, decoded.blockAt(4, 1, 6, 14, 0), "754 の y=70 の丸石");
        assertEquals(11881, decoded.blockAt(4, 14, 6, 1, 0), "転置されていたらここに出る（空気のはず）");

        // バイオーム：西半分は砂漠、東半分は平原（754 の codec 名で引く）。
        assertEquals(2, decoded.biomeAt(3, 0, 3, 0), "754 の西は砂漠");
        assertEquals(1, decoded.biomeAt(3, 2, 3, 0), "754 の東は平原");
    }

    @Test
    void protocol754NormalizesBlockStates() {
        assertEquals(1, BedrockChunkTranslator.normalizeBlockState(1, 754), "754 の石は石");
        assertEquals(9, BedrockChunkTranslator.normalizeBlockState(9, 754), "754 の草は草");
        assertEquals(10, BedrockChunkTranslator.normalizeBlockState(10, 754), "754 の土は土");
        assertEquals(14, BedrockChunkTranslator.normalizeBlockState(14, 754), "754 の丸石は丸石");
        assertEquals(80, BedrockChunkTranslator.normalizeBlockState(34, 754),
                "754 の水（level 0）は 765 の水（level 0）");
        assertEquals(131, BedrockChunkTranslator.normalizeBlockState(74, 754),
                "754 のオークの丸太（axis=y、74）は 765 の丸太（131）");
        assertEquals(1, BedrockChunkTranslator.normalizeBlockState(99999, 754),
                "範囲外の状態は石に落とす");
    }

    /**
     * 754 の BitStorage は long の境界をまたがない詰め（non-straddling）。
     * 20 種の状態があるセクションは BPE 5 で 342 long になり、straddling
     * （320 long）で読むとずれる。実バニラ 1.16.5 の生キャプチャで確認
     * （2026-11-16：cap754-chunk-x-15-z-7.bin で BPE 5 の dataLen は 342）。
     */
    @Test
    void protocol754NonStraddlingPacking() {
        TestJavaChunk.BlockAt twentyStates = (x, y, z) -> {
            if (y >= 48 && y < 64) {
                int j = x | (z << 4) | ((y - 48) << 8);
                return 1 + (j % 20); // 状態 1〜20（20 種で BPE 5）
            }
            if (y < 48) {
                return 1;
            }
            return 0;
        };
        byte[] javaChunk = TestJavaChunk.build754(0, 0, twentyStates, (x, y, z) -> 1);
        TranslatedChunk translated = BedrockChunkTranslator.translate(
                new ChunkInput(0, 0, javaChunk, 754, 0, 256, BIOME_NAMES_116));
        Decoded decoded = BedrockLevelChunkDecoder.decode(translated.payload(), 16);
        assertTrue(decoded.fullyConsumed, "BPE 5 のセクションを読み切ること");
        assertEquals(1774, decoded.blockAt(3, 0, 0, 0, 0), "状態 1 は石");
        assertEquals(9550, decoded.blockAt(3, 9, 0, 0, 0), "状態 10 は土");
        assertEquals(12183, decoded.blockAt(3, 8, 0, 0, 0), "状態 9 は草ブロック");
    }

    @Test
    void protocol754BiomeFallbackToPlains() {
        // 名前が無い場合、範囲外のバイオーム番号は平原（1）に落とす。
        byte[] javaChunk = TestJavaChunk.build754(0, 0,
                (x, y, z) -> 1, (x, y, z) -> 200);
        TranslatedChunk translated = BedrockChunkTranslator.translate(
                new ChunkInput(0, 0, javaChunk, 754, 0, 256, null));
        Decoded decoded = BedrockLevelChunkDecoder.decode(translated.payload(), 16);
        assertTrue(decoded.fullyConsumed);
        assertEquals(1, decoded.biomeAt(3, 0, 0, 0), "範囲外のバイオームは平原");
        assertEquals(1, decoded.biomeAt(0, 0, 0, 0), "範囲外のバイオームは平原");
    }

    @Test
    void protocol754SparseSectionsAreAirButKeepBiomes() {
        // bitMap にセクション 3・4 だけが立っているチャンク。無いセクションは
        // ブロックだけ空気で補完し、バイオームは int[1024] の値をそのまま使う。
        // airBlocks() は Java の空気（状態 0）で埋める（754 経路も 755 経路と
        // 同じ airBlocks() を使う）。
        byte[] javaChunk = TestJavaChunk.build754(0, 0, TERRAIN_754, TWO_BIOMES_754, 0x18);
        TranslatedChunk translated = BedrockChunkTranslator.translate(
                new ChunkInput(0, 0, javaChunk, 754, 0, 256, BIOME_NAMES_116));
        Decoded decoded = BedrockLevelChunkDecoder.decode(translated.payload(), 16);
        assertTrue(decoded.fullyConsumed, "ペイロードを読み切ること");
        assertEquals(11881, decoded.blockAt(0, 0, 0, 0, 0), "bitMap に無いセクションは空気");
        assertEquals(11881, decoded.blockAt(15, 0, 0, 0, 0), "bitMap に無いセクションは空気");
        assertEquals(12183, decoded.blockAt(3, 8, 15, 8, 0), "あるセクションの草ブロックは残る");
        assertEquals(2, decoded.biomeAt(0, 0, 0, 0), "無いセクションのバイオームも配列の値（砂漠）");
        assertEquals(1, decoded.biomeAt(0, 2, 0, 0), "無いセクションのバイオームも配列の値（平原）");
    }
    // ---- 753（1.16.3）----

    /**
     * 753 のバイオーム登録番号の順序は 754 と同一（79 件。id 0〜173 の非連番。
     * minecraft: 無し）。753⇔754 は恒等写像（Stage A §11.1・Stage B §14.4 実測）のため
     * BIOME_NAMES_116 をそのまま使う。
     */
    static final List<String> BIOME_NAMES_1163 = BIOME_NAMES_116;

    /**
     * 753 の既知の地形。状態番号は 754 と同一（753→754 は恒等写像。石 1、土 10、草 9、
     * 水 34、オークの丸太 axis=y は 74、丸石 14。blocks-1.16.3-reports.json は
     * 1.16.5 版とバイト同一）。
     */
    static final TestJavaChunk.BlockAt TERRAIN_753 = TERRAIN_754;

    /** 西半分は砂漠（2）、東半分は平原（1）。753 のバイオーム番号（754 と同一）。 */
    static final TestJavaChunk.BiomeAt TWO_BIOMES_753 = TWO_BIOMES_754;

    @Test
    void protocol753ChunkRoundTrip() {
        // ブロック状態は 753→754 恒等＋754→755 の表で 765 に正規化される（丸太 74→74→77→131）。
        byte[] javaChunk = TestJavaChunk.build753(0, 0, TERRAIN_753, TWO_BIOMES_753);
        TranslatedChunk translated = BedrockChunkTranslator.translate(
                new ChunkInput(0, 0, javaChunk, 753, 0, 256, BIOME_NAMES_1163));
        assertEquals(0, translated.chunkX());
        assertEquals(0, translated.chunkZ());
        assertEquals(16, translated.subChunkCount());
        Decoded decoded = BedrockLevelChunkDecoder.decode(translated.payload(), 16);
        assertTrue(decoded.fullyConsumed, "ペイロードを読み切ること");
        assertEquals(0, decoded.borderBlockCount);
        assertEquals(0, decoded.blockEntityCount);
        for (int s = 0; s < 16; s++) {
            assertEquals(s, decoded.subChunkY.get(s), "サブチャンク Y=" + s);
        }
        assertEquals(12183, decoded.blockAt(3, 8, 15, 8, 0), "753 の y=63 は草ブロック");
        assertEquals(9550, decoded.blockAt(3, 8, 14, 8, 0), "753 の y=62 は土");
        assertEquals(1774, decoded.blockAt(3, 8, 11, 8, 0), "753 の y=59 は石");
        assertEquals(1774, decoded.blockAt(0, 0, 0, 0, 0), "753 の y=0 は石");
        assertEquals(11881, decoded.blockAt(4, 0, 10, 0, 0), "753 の y=74 は空気");
        assertEquals(9304, decoded.blockAt(3, 4, 15, 4, 0), "753 の水たまりは水");
        assertEquals(9550, decoded.blockAt(3, 4, 14, 4, 0), "水の下は土");
        assertEquals(725, decoded.blockAt(4, 10, 0, 10, 0), "753 の y=64 の丸太");
        assertEquals(725, decoded.blockAt(4, 10, 2, 10, 0), "753 の y=66 の丸太");
        assertEquals(5995, decoded.blockAt(4, 1, 6, 14, 0), "753 の y=70 の丸石");
        assertEquals(11881, decoded.blockAt(4, 14, 6, 1, 0), "転置されていたらここに出る（空気のはず）");
        assertEquals(2, decoded.biomeAt(3, 0, 3, 0), "753 の西は砂漠");
        assertEquals(1, decoded.biomeAt(3, 2, 3, 0), "753 の東は平原");
    }

    @Test
    void protocol753NormalizesBlockStates() {
        assertEquals(1, BedrockChunkTranslator.normalizeBlockState(1, 753), "753 の石は石");
        assertEquals(9, BedrockChunkTranslator.normalizeBlockState(9, 753), "753 の草は草");
        assertEquals(10, BedrockChunkTranslator.normalizeBlockState(10, 753), "753 の土は土");
        assertEquals(14, BedrockChunkTranslator.normalizeBlockState(14, 753), "753 の丸石は丸石");
        assertEquals(80, BedrockChunkTranslator.normalizeBlockState(34, 753),
                "753 の水（level 0）は 765 の水（level 0）");
        assertEquals(131, BedrockChunkTranslator.normalizeBlockState(74, 753),
                "753 のオークの丸太（axis=y、74）は 765 の丸太（131）");
        assertEquals(1, BedrockChunkTranslator.normalizeBlockState(99999, 753),
                "範囲外の状態は石に落とす");
    }

    /**
     * 753 の BitStorage は long の境界をまたがない詰め（non-straddling）。
     * 20 種の状態があるセクションは BPE 5 で 342 long になり、straddling
     * （320 long）で読むとずれる。根拠は 754 と同一形式のため、実バニラ 1.16.5 の
     * 生キャプチャで確認（2026-11-16：cap754-chunk-x-15-z-7.bin で BPE 5 の
     * dataLen は 342。このファイルは 753 形式のバイトでもある。Stage B §14.4）。
     */
    @Test
    void protocol753NonStraddlingPacking() {
        TestJavaChunk.BlockAt twentyStates = (x, y, z) -> {
            if (y >= 48 && y < 64) {
                int j = x | (z << 4) | ((y - 48) << 8);
                return 1 + (j % 20); // 状態 1〜20（20 種で BPE 5）
            }
            if (y < 48) {
                return 1;
            }
            return 0;
        };
        byte[] javaChunk = TestJavaChunk.build753(0, 0, twentyStates, (x, y, z) -> 1);
        TranslatedChunk translated = BedrockChunkTranslator.translate(
                new ChunkInput(0, 0, javaChunk, 753, 0, 256, BIOME_NAMES_1163));
        Decoded decoded = BedrockLevelChunkDecoder.decode(translated.payload(), 16);
        assertTrue(decoded.fullyConsumed, "BPE 5 のセクションを読み切ること");
        assertEquals(1774, decoded.blockAt(3, 0, 0, 0, 0), "状態 1 は石");
        assertEquals(9550, decoded.blockAt(3, 9, 0, 0, 0), "状態 10 は土");
        assertEquals(12183, decoded.blockAt(3, 8, 0, 0, 0), "状態 9 は草ブロック");
    }

    @Test
    void protocol753BiomeFallbackToPlains() {
        // 名前が無い場合、範囲外のバイオーム番号は平原（1）に落とす。
        byte[] javaChunk = TestJavaChunk.build753(0, 0,
                (x, y, z) -> 1, (x, y, z) -> 200);
        TranslatedChunk translated = BedrockChunkTranslator.translate(
                new ChunkInput(0, 0, javaChunk, 753, 0, 256, null));
        Decoded decoded = BedrockLevelChunkDecoder.decode(translated.payload(), 16);
        assertTrue(decoded.fullyConsumed);
        assertEquals(1, decoded.biomeAt(3, 0, 0, 0), "範囲外のバイオームは平原");
        assertEquals(1, decoded.biomeAt(0, 0, 0, 0), "範囲外のバイオームは平原");
    }

    @Test
    void protocol753SparseSectionsAreAirButKeepBiomes() {
        // bitMap にセクション 3・4 だけが立っているチャンク。無いセクションは
        // ブロックだけ空気で補完し、バイオームは int[1024] の値をそのまま使う。
        // airBlocks() は Java の空気（状態 0）で埋める（753 経路も 754 経路と
        // 同じ airBlocks() を使う）。
        byte[] javaChunk = TestJavaChunk.build753(0, 0, TERRAIN_753, TWO_BIOMES_753, 0x18);
        TranslatedChunk translated = BedrockChunkTranslator.translate(
                new ChunkInput(0, 0, javaChunk, 753, 0, 256, BIOME_NAMES_1163));
        Decoded decoded = BedrockLevelChunkDecoder.decode(translated.payload(), 16);
        assertTrue(decoded.fullyConsumed, "ペイロードを読み切ること");
        assertEquals(11881, decoded.blockAt(0, 0, 0, 0, 0), "bitMap に無いセクションは空気");
        assertEquals(11881, decoded.blockAt(15, 0, 0, 0, 0), "bitMap に無いセクションは空気");
        assertEquals(12183, decoded.blockAt(3, 8, 15, 8, 0), "あるセクションの草ブロックは残る");
        assertEquals(2, decoded.biomeAt(0, 0, 0, 0), "無いセクションのバイオームも配列の値（砂漠）");
        assertEquals(1, decoded.biomeAt(0, 2, 0, 0), "無いセクションのバイオームも配列の値（平原）");
    }

}
