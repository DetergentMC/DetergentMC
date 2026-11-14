package io.detergentmc.bedrock;

/**
 * Java のバイオーム -> Bedrock 1.20.50 のバイオームID。
 *
 * <p>生成物。生成元は {@code detergentmc-bedrock/tools/generate.py}
 * （PMMP BedrockData の biome_id_map.json と PrismarineJS minecraft-data の
 * pc 1.20.2 biomes.json から作る）。直接編集しないこと。
 *
 * <p>Java のバイオームは名前で引く（{@link #idForName(String)}）。
 * チャンクのバイオームパレットが持つのはレジストリの番号（インデックス）で、
 * その番号が指す名前は設定フェーズの Registry Data（{@code minecraft:worldgen/biome}）
 * で Java 側から届く。名前が分からない場合の代替は平原（{@link #FALLBACK_ID}）。
 */
public final class BedrockBiomeIds {

    private BedrockBiomeIds() {
    }

    /** 代替のバイオームID：平原。 */
    public static final int FALLBACK_ID = 1;

    /** Java のバイオームレジストリ番号（0〜63、1.20.3 の既定の順序）-> Bedrock バイオームID。 */
    public static final int[] BY_JAVA_ID = {
            37, 48, 181, 16, 27, 192, 44, 179, 29, 45, 1, 47, 43, 24, 2, 3,
            9, 9, 9, 39, 132, 4, 46, 13, 11, 31, 140, 13, 21, 42, 21, 191,
            1, 14, 8, 0, 155, 32, 33, 1, 7, 35, 36, 9, 26, 12, 13, 30,
            178, 23, 3, 25, 129, 6, 5, 9, 9, 40, 180, 34, 20, 3, 36, 39,
    };

    /** Java のバイオーム名（{@code minecraft:} 付きでも無しでも可）-> Bedrock バイオームID。 */
    public static int idForName(String javaBiomeName) {
        if (javaBiomeName == null) {
            return FALLBACK_ID;
        }
        String name = javaBiomeName.startsWith("minecraft:")
                ? javaBiomeName.substring("minecraft:".length()) : javaBiomeName;
        return switch (name) {
            case "badlands" -> 37;
            case "bamboo_jungle" -> 48;
            case "basalt_deltas" -> 181;
            case "beach" -> 16;
            case "birch_forest" -> 27;
            case "cherry_grove" -> 192;
            case "cold_ocean" -> 44;
            case "crimson_forest" -> 179;
            case "dark_forest" -> 29;
            case "deep_cold_ocean" -> 45;
            case "deep_dark" -> 1;
            case "deep_frozen_ocean" -> 47;
            case "deep_lukewarm_ocean" -> 43;
            case "deep_ocean" -> 24;
            case "desert" -> 2;
            case "dripstone_caves" -> 3;
            case "end_barrens" -> 9;
            case "end_highlands" -> 9;
            case "end_midlands" -> 9;
            case "eroded_badlands" -> 39;
            case "flower_forest" -> 132;
            case "forest" -> 4;
            case "frozen_ocean" -> 46;
            case "frozen_peaks" -> 13;
            case "frozen_river" -> 11;
            case "grove" -> 31;
            case "ice_spikes" -> 140;
            case "jagged_peaks" -> 13;
            case "jungle" -> 21;
            case "lukewarm_ocean" -> 42;
            case "lush_caves" -> 21;
            case "mangrove_swamp" -> 191;
            case "meadow" -> 1;
            case "mushroom_fields" -> 14;
            case "nether_wastes" -> 8;
            case "ocean" -> 0;
            case "old_growth_birch_forest" -> 155;
            case "old_growth_pine_taiga" -> 32;
            case "old_growth_spruce_taiga" -> 33;
            case "plains" -> 1;
            case "river" -> 7;
            case "savanna" -> 35;
            case "savanna_plateau" -> 36;
            case "small_end_islands" -> 9;
            case "snowy_beach" -> 26;
            case "snowy_plains" -> 12;
            case "snowy_slopes" -> 13;
            case "snowy_taiga" -> 30;
            case "soul_sand_valley" -> 178;
            case "sparse_jungle" -> 23;
            case "stony_peaks" -> 3;
            case "stony_shore" -> 25;
            case "sunflower_plains" -> 129;
            case "swamp" -> 6;
            case "taiga" -> 5;
            case "the_end" -> 9;
            case "the_void" -> 9;
            case "warm_ocean" -> 40;
            case "warped_forest" -> 180;
            case "windswept_forest" -> 34;
            case "windswept_gravelly_hills" -> 20;
            case "windswept_hills" -> 3;
            case "windswept_savanna" -> 36;
            case "wooded_badlands" -> 39;
            default -> FALLBACK_ID;
        };
    }

    /** Java のバイオームレジストリ番号 -> Bedrock バイオームID（範囲外は平原）。 */
    public static int idForJavaId(int javaBiomeId) {
        if (javaBiomeId < 0 || javaBiomeId >= BY_JAVA_ID.length) {
            return FALLBACK_ID;
        }
        return BY_JAVA_ID[javaBiomeId];
    }
}
