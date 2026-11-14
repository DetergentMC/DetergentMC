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
}
