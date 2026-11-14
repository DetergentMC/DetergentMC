package io.detergentmc.bedrock;

/**
 * Java 1.20.4（プロトコル 765）のアイテムIDから、そのアイテムが
 * ブロックである場合の Bedrock ブロック・ランタイムIDへの対応表。
 *
 * <p>Bedrock の Item には block_runtime_id があり、ブロック系
 * アイテム（石・丸太など）はここが正しいと 3D で表示される。
 * 対応は ViaVersion Mappings mapping-1.20.3.json の blocks／
 * blockstates（各ブロックの最初の状態IDを代表状態として使う）と
 * BedrockBlockRuntimeIds の元データから作る。ブロックでない
 * アイテム（-1）は 0（ブロック無し）になる。
 *
 * <p>網羅率（1312種中）：ブロック系 911、非ブロック
 * 401。
 */
public final class BedrockItemBlockIds {

    private BedrockItemBlockIds() {
    }

    /** Java 765 のアイテム数。 */
    public static final int COUNT = 1312;

    /** ブロック系として写せたアイテム数。 */
    public static final int BLOCK_MAPPED_COUNT = 911;

    /** 765 のアイテムID順。ブロックでないものは -1。 */
    private static final int[] BLOCK_RUNTIME_IDS_765 = {
            11881,   1774,     82,   1149,    147,  10017,   1772,  13228, // air, stone, granite, polished_granite, diorite, polished_diorite, andesite, polished_andesite
              677,  11873,  13448,    632,   1088,    699,  10563,   2635, // deepslate, cobbled_deepslate, polished_deepslate, calcite, tuff, tuff_slab, tuff_stairs, tuff_wall
            13774,  12892,    151,  12948,   2642,  11868,   6116,  11411, // chiseled_tuff, polished_tuff, polished_tuff_slab, polished_tuff_stairs, polished_tuff_wall, tuff_bricks, tuff_brick_slab, tuff_brick_stairs
             2642,  13116,   2330,  12183,   9550,   9551,   7942,   9161, // tuff_brick_wall, chiseled_tuff_bricks, dripstone_block, grass_block, dirt, coarse_dirt, podzol, rooted_dirt
            11875,   6969,  11492,   5995,  13224,  13579,   9091,  11816, // mud, crimson_nylium, warped_nylium, cobblestone, oak_planks, spruce_planks, birch_planks, jungle_planks
             7285,  13447,   5931,   2402,   9417,   8586,   2373,  14063, // acacia_planks, cherry_planks, dark_oak_planks, mangrove_planks, bamboo_planks, crimson_planks, warped_planks, bamboo_mosaic
             2141,   2142,   2143,   2144,   2145,  12927,   2146,  12189, // oak_sapling, spruce_sapling, birch_sapling, jungle_sapling, acacia_sapling, cherry_sapling, dark_oak_sapling, mangrove_propagule
            12229,   6981,   2589,   8057,   6982,  14093,   7076,  12841, // bedrock, sand, suspicious_sand, suspicious_gravel, red_sand, gravel, coal_ore, deepslate_coal_ore
             7982,  12953,   5930,    113,   2365,  11250,   7944,  13288, // iron_ore, deepslate_iron_ore, copper_ore, deepslate_copper_ore, gold_ore, deepslate_gold_ore, redstone_ore, deepslate_redstone_ore
            13027,  11493,  13383,  12925,   7193,  13818,     28,   7344, // emerald_ore, deepslate_emerald_ore, lapis_ore, deepslate_lapis_ore, diamond_ore, deepslate_diamond_ore, nether_gold_ore, nether_quartz_ore
            11251,   9162,  14064,   9044,   1102,    771,  12210,  14067, // ancient_debris, coal_block, raw_iron_block, raw_copper_block, raw_gold_block, amethyst_block, budding_amethyst, iron_block
             7943,    772,    716,   6169,   1344,  14032,   5929,   9514, // copper_block, gold_block, diamond_block, netherite_block, exposed_copper, weathered_copper, oxidized_copper, chiseled_copper
            11783,   1041,   9037,   7981,  11310,  12842,   9275,   7474, // exposed_chiseled_copper, weathered_chiseled_copper, oxidized_chiseled_copper, cut_copper, exposed_cut_copper, weathered_cut_copper, oxidized_cut_copper, cut_copper_stairs
             7457,   7137,   1098,   8993,  11801,  11170,   9058,  13426, // exposed_cut_copper_stairs, weathered_cut_copper_stairs, oxidized_cut_copper_stairs, cut_copper_slab, exposed_cut_copper_slab, weathered_cut_copper_slab, oxidized_cut_copper_slab, waxed_copper_block
             1961,   1974,  13223,  13591,    697,   6100,     71,  12973, // waxed_exposed_copper, waxed_weathered_copper, waxed_oxidized_copper, waxed_chiseled_copper, waxed_exposed_chiseled_copper, waxed_weathered_chiseled_copper, waxed_oxidized_chiseled_copper, waxed_cut_copper
             6204,   8587,    631,   1146,   6684,  11317,  10036,  13593, // waxed_exposed_cut_copper, waxed_weathered_cut_copper, waxed_oxidized_cut_copper, waxed_cut_copper_stairs, waxed_exposed_cut_copper_stairs, waxed_weathered_cut_copper_stairs, waxed_oxidized_cut_copper_stairs, waxed_cut_copper_slab
              673,  11745,   1976,    726,   7057,   1776,    637,   7162, // waxed_exposed_cut_copper_slab, waxed_weathered_cut_copper_slab, waxed_oxidized_cut_copper_slab, oak_log, spruce_log, birch_log, jungle_log, acacia_log
            12930,   4900,   1090,  11319,   1085,  10537,  11676,     63, // cherry_log, dark_oak_log, mangrove_log, mangrove_roots, muddy_mangrove_roots, crimson_stem, warped_stem, bamboo_block
            13226,  11428,  10692,   1762,  10071,   7271,    634,  14091, // stripped_oak_log, stripped_spruce_log, stripped_birch_log, stripped_jungle_log, stripped_acacia_log, stripped_cherry_log, stripped_dark_oak_log, stripped_mangrove_log
            12156,  13078,   4920,   4921,   4922,   4923,   4924,   8614, // stripped_crimson_stem, stripped_warped_stem, stripped_oak_wood, stripped_spruce_wood, stripped_birch_wood, stripped_jungle_wood, stripped_acacia_wood, stripped_cherry_wood
             4925,   7016,  11680,   9378,   5975,   4914,   4915,   4916, // stripped_dark_oak_wood, stripped_mangrove_wood, stripped_crimson_hyphae, stripped_warped_hyphae, stripped_bamboo_block, oak_wood, spruce_wood, birch_wood
             4917,   4918,  12369,   4919,   6941,   7121,  10545,  11242, // jungle_wood, acacia_wood, cherry_wood, dark_oak_wood, mangrove_wood, crimson_hyphae, warped_hyphae, oak_leaves
            11243,  11244,  11245,   7189,  10022,   7190,  11871,  13398, // spruce_leaves, birch_leaves, jungle_leaves, acacia_leaves, cherry_leaves, dark_oak_leaves, mangrove_leaves, azalea_leaves
            11484,   1363,   1364,  11308,  10694,   7075,   6037,   6038, // flowering_azalea_leaves, sponge, wet_sponge, glass, tinted_glass, lapis_block, sandstone, chiseled_sandstone
             6039,  11905,   2381,   2383,  12088,   9274,   7969,    669, // cut_sandstone, cobweb, short_grass, fern, azalea, flowering_azalea, dead_bush, seagrass
            10077,   9163,   1942,   2409,  12328,    552,  11111,   6170, // sea_pickle, white_wool, orange_wool, magenta_wool, light_blue_wool, yellow_wool, lime_wool, pink_wool
              643,  13835,   9104,  14066,   9276,    696,   6117,    126, // gray_wool, light_gray_wool, cyan_wool, purple_wool, blue_wool, brown_wool, green_wool, red_wool
             1105,   1039,   5999,   6000,   6001,   6002,   6003,   6004, // black_wool, dandelion, poppy, blue_orchid, allium, azure_bluet, red_tulip, orange_tulip
             6005,   6006,   6007,   6008,   6009,  11309,  11187,   6152, // white_tulip, pink_tulip, oxeye_daisy, cornflower, lily_of_the_valley, wither_rose, torchflower, pitcher_plant
            12992,   5905,   7448,  13446,    736,  13258,   7194,  11668, // spore_blossom, brown_mushroom, red_mushroom, crimson_fungus, warped_fungus, crimson_roots, warped_roots, nether_sprouts
             9277,   9488,  11124,   9191,    735,   7533,  11736,    622, // weeping_vines, twisting_vines, sugar_cane, kelp, moss_carpet, pink_petals, moss_block, hanging_roots
            10715,   7144,   6068,   9051,   9052,   9053,   9054,   9055, // big_dripleaf, small_dripleaf, bamboo, oak_slab, spruce_slab, birch_slab, jungle_slab, acacia_slab
            11362,   9056,   2579,  11711,   4898,  10543,  11674,   9628, // cherry_slab, dark_oak_slab, mangrove_slab, bamboo_slab, bamboo_mosaic_slab, crimson_slab, warped_slab, stone_slab
             7067,   7068,   9629,   9051,   7070,   7071,   7072,   6687, // smooth_stone_slab, sandstone_slab, cut_sandstone_slab, petrified_oak_slab, cobblestone_slab, brick_slab, stone_brick_slab, mud_brick_slab
             7074,   7073,   9597,   9630,   9598,   9599,   9601,   9600, // nether_brick_slab, quartz_slab, red_sandstone_slab, cut_red_sandstone_slab, purpur_slab, prismarine_slab, prismarine_brick_slab, dark_prismarine_slab
             6083,  11782,   6040,   7427,   8097,  11874,    801,  11920, // smooth_quartz, smooth_red_sandstone, smooth_sandstone, smooth_stone, bricks, bookshelf, chiseled_bookshelf, decorated_pot
              675,   1177,   2166,  10532,   9303,   7373,  13400,  13406, // mossy_cobblestone, obsidian, torch, end_rod, chorus_plant, chorus_flower, purpur_block, purpur_pillar
            13457,   1148,  12365,  10076,   6688,  12908,  14070,   6098, // purpur_stairs, spawner, chest, crafting_table, farmland, furnace, ladder, cobblestone_stairs
              571,  11880,   6980,  12194,  12376,   8612,   9547,   1137, // snow, ice, snow_block, cactus, clay, jukebox, oak_fence, spruce_fence
            13860,   1130,  13865,   2401,  12330,  11834,   1367,  13773, // birch_fence, jungle_fence, acacia_fence, cherry_fence, dark_oak_fence, mangrove_fence, bamboo_fence, crimson_fence
            10075,   7422,  13057,  11878,  12250,   9632,   9631,   7181, // warped_fence, pumpkin, carved_pumpkin, jack_o_lantern, netherrack, soul_sand, soul_soil, basalt
               26,   2586,   7941,   6661,   6920,   6921,   6922,   6923, // polished_basalt, smooth_basalt, soul_torch, glowstone, infested_stone, infested_cobblestone, infested_stone_bricks, infested_mossy_stone_bricks
             6924,   6925,   7934,  11746,  11747,  11748,  11749,    732, // infested_cracked_stone_bricks, infested_chiseled_stone_bricks, infested_deepslate, stone_bricks, mossy_stone_bricks, cracked_stone_bricks, chiseled_stone_bricks, packed_mud
            12089,   9261,   9126,   7426,   6938,   8991,  10018,  13042, // mud_bricks, deepslate_bricks, cracked_deepslate_bricks, deepslate_tiles, cracked_deepslate_tiles, chiseled_deepslate, reinforced_deepslate, brown_mushroom_block
             5991,  13043,   8150,  12374,   8990,   1138,   2346,   9481, // red_mushroom_block, mushroom_stem, iron_bars, chain, glass_pane, melon, vine, glow_lichen
            11718,   2391,   9326,   6067,   2587,  12952,   7397,  12910, // brick_stairs, stone_brick_stairs, mud_brick_stairs, mycelium, lily_pad, nether_bricks, cracked_nether_bricks, chiseled_nether_bricks
             7082,    120,  12249,  12839,   5994,    642,  11922,  11194, // nether_brick_fence, nether_brick_stairs, sculk, sculk_vein, sculk_catalyst, sculk_shrieker, enchanting_table, end_portal_frame
             6609,    729,  12950,   5970,   7204,   2588,    723,    145, // end_stone, end_stone_bricks, dragon_egg, sandstone_stairs, ender_chest, emerald_block, oak_stairs, spruce_stairs
            12220,  12181,  11334,  12383,   8826,   7465,   2159,  11342, // birch_stairs, jungle_stairs, acacia_stairs, cherry_stairs, dark_oak_stairs, mangrove_stairs, bamboo_stairs, bamboo_mosaic_stairs
            11425,   6111,  13515,    561,   2635,   2636,   2641,   2646, // crimson_stairs, warped_stairs, command_block, beacon, cobblestone_wall, mossy_cobblestone_wall, brick_wall, prismarine_wall
             2647,   2643,   2637,   2642,   2641,   2644,   2639,   2648, // red_sandstone_wall, mossy_stone_brick_wall, granite_wall, stone_brick_wall, mud_brick_wall, nether_brick_wall, andesite_wall, red_nether_brick_wall
             2640,   2645,   2638,   2644,   2644,   2644,   2635,   2642, // sandstone_wall, end_stone_brick_wall, diorite_wall, blackstone_wall, polished_blackstone_wall, polished_blackstone_brick_wall, cobbled_deepslate_wall, polished_deepslate_wall
             2642,   2642,  11838,  11842,  11846,   6081,   6080,  11494, // deepslate_brick_wall, deepslate_tile_wall, anvil, chipped_anvil, damaged_anvil, chiseled_quartz_block, quartz_block, quartz_bricks
             6086,   8121,   8151,  13222,   7450,   7183,   6979,  14039, // quartz_pillar, quartz_stairs, white_terracotta, orange_terracotta, magenta_terracotta, light_blue_terracotta, yellow_terracotta, lime_terracotta
             6935,   7428,   1766,      0,  11533,   6036,  13801,   6102, // pink_terracotta, gray_terracotta, light_gray_terracotta, cyan_terracotta, purple_terracotta, blue_terracotta, brown_terracotta, green_terracotta
             2404,  11233,  11141,  13757,   1966,  12940,  12287,    679, // red_terracotta, black_terracotta, barrier, light, hay_block, white_carpet, orange_carpet, magenta_carpet
             8152,  10565,  11917,  13385,    645,  14065,   5998,  13443, // light_blue_carpet, yellow_carpet, lime_carpet, pink_carpet, gray_carpet, light_gray_carpet, cyan_carpet, purple_carpet
              595,   2380,   6118,  13098,  11150,   1376,    731,  13866, // blue_carpet, brown_carpet, green_carpet, red_carpet, black_carpet, terracotta, packed_ice, dirt_path
             9223,   9224,   9227,   9228,   9225,   9226,   8588,   7395, // sunflower, lilac, rose_bush, peony, tall_grass, large_fern, white_stained_glass, orange_stained_glass
            12893,   9588,  13270,    125,   7147,   6101,   1176,  10566, // magenta_stained_glass, light_blue_stained_glass, yellow_stained_glass, lime_stained_glass, pink_stained_glass, gray_stained_glass, light_gray_stained_glass, cyan_stained_glass
             2613,   9552,   1771,   7081,   9004,  10567,   7396,    644, // purple_stained_glass, blue_stained_glass, brown_stained_glass, green_stained_glass, red_stained_glass, black_stained_glass, white_stained_glass_pane, orange_stained_glass_pane
             8149,   6660,    559,  13268,  12327,  12102,   1178,  11669, // magenta_stained_glass_pane, light_blue_stained_glass_pane, yellow_stained_glass_pane, lime_stained_glass_pane, pink_stained_glass_pane, gray_stained_glass_pane, light_gray_stained_glass_pane, cyan_stained_glass_pane
             8589,     83,    728,   6926,  12322,   2620,  11212,  11214, // purple_stained_glass_pane, blue_stained_glass_pane, brown_stained_glass_pane, green_stained_glass_pane, red_stained_glass_pane, black_stained_glass_pane, prismarine, prismarine_bricks
            11213,  12938,    629,  13114,  13229,  11779,  11780,  11781, // dark_prismarine, prismarine_stairs, prismarine_brick_stairs, dark_prismarine_stairs, sea_lantern, red_sandstone, chiseled_red_sandstone, cut_red_sandstone
             9118,  13435,  11775,  13788,   7087,  10547,    562,   7024, // red_sandstone_stairs, repeating_command_block, chain_command_block, magma_block, nether_wart_block, warped_wart_block, red_nether_bricks, bone_block
             6970,   6065,   1103,  11364,    730,  12174,    122,   1040, // structure_void, shulker_box, white_shulker_box, orange_shulker_box, magenta_shulker_box, light_blue_shulker_box, yellow_shulker_box, lime_shulker_box
             7212,   9111,  10548,  12240,  13054,  11413,  12087,  11678, // pink_shulker_box, gray_shulker_box, light_gray_shulker_box, cyan_shulker_box, purple_shulker_box, blue_shulker_box, brown_shulker_box, green_shulker_box
             7269,  11140,   9373,   2582,   2412,   9270,   2368,    648, // red_shulker_box, black_shulker_box, white_glazed_terracotta, orange_glazed_terracotta, magenta_glazed_terracotta, light_blue_glazed_terracotta, yellow_glazed_terracotta, lime_glazed_terracotta
            11740,  14058,   5507,   9122,  12224,   9264,   5909,  11812, // pink_glazed_terracotta, gray_glazed_terracotta, light_gray_glazed_terracotta, cyan_glazed_terracotta, purple_glazed_terracotta, blue_glazed_terracotta, brown_glazed_terracotta, green_glazed_terracotta
             6947,  10026,  13864,  11915,   7195,  13117,   5997,   7445, // red_glazed_terracotta, black_glazed_terracotta, white_concrete, orange_concrete, magenta_concrete, light_blue_concrete, yellow_concrete, lime_concrete
             5519,  13045,   1952,  12941,   6972,  12951,  11344,  10539, // pink_concrete, gray_concrete, light_gray_concrete, cyan_concrete, purple_concrete, blue_concrete, brown_concrete, green_concrete
            13384,  11898,   8537,  14029,   7286,     61,  13263,  13775, // red_concrete, black_concrete, white_concrete_powder, orange_concrete_powder, magenta_concrete_powder, light_blue_concrete_powder, yellow_concrete_powder, lime_concrete_powder
             7085,  13044,  12924,   5906,  11737,  11914,  10113,  12228, // pink_concrete_powder, gray_concrete_powder, light_gray_concrete_powder, cyan_concrete_powder, purple_concrete_powder, blue_concrete_powder, brown_concrete_powder, green_concrete_powder
            12923,   1139,  13776,  12211,   8999,   9000,   9001,   9002, // red_concrete_powder, black_concrete_powder, turtle_egg, sniffer_egg, dead_tube_coral_block, dead_brain_coral_block, dead_bubble_coral_block, dead_fire_coral_block
             9003,   8994,   8995,   8996,   8997,   8998,  13459,   1941, // dead_horn_coral_block, tube_coral_block, brain_coral_block, bubble_coral_block, fire_coral_block, horn_coral_block, tube_coral, brain_coral
            11363,   1773,   5996,  12840,  12926,  11418,  10600,   7086, // bubble_coral, fire_coral, horn_coral, dead_brain_coral, dead_bubble_coral, dead_fire_coral, dead_horn_coral, dead_tube_coral
             7477,   7478,   7479,   7480,   7481,     72,     73,     74, // tube_coral_fan, brain_coral_fan, bubble_coral_fan, fire_coral_fan, horn_coral_fan, dead_tube_coral_fan, dead_brain_coral_fan, dead_bubble_coral_fan
               75,     76,  12239,   7018,   6933,   9350,  10528,  11912, // dead_fire_coral_fan, dead_horn_coral_fan, blue_ice, conduit, polished_granite_stairs, smooth_red_sandstone_stairs, mossy_stone_brick_stairs, polished_diorite_stairs
             6874,  11531,   1374,   6016,  13392,   5517,   9089,  11808, // mossy_cobblestone_stairs, end_stone_brick_stairs, stone_stairs, smooth_sandstone_stairs, smooth_quartz_stairs, granite_stairs, andesite_stairs, red_nether_brick_stairs
            12247,   7234,    569,   1037,  13106,   7951,   9620,   9614, // polished_andesite_stairs, diorite_stairs, cobbled_deepslate_stairs, polished_deepslate_stairs, deepslate_brick_stairs, deepslate_tile_stairs, polished_granite_slab, smooth_red_sandstone_slab
             9626,   9618,   9602,   9613,   9603,   9627,   9619,   9616, // mossy_stone_brick_slab, polished_diorite_slab, mossy_cobblestone_slab, end_stone_brick_slab, smooth_sandstone_slab, smooth_quartz_slab, granite_slab, andesite_slab
             9604,   9615,   9617,  12991,    738,   6104,   7084,   5956, // red_nether_brick_slab, polished_andesite_slab, diorite_slab, cobbled_deepslate_slab, polished_deepslate_slab, deepslate_brick_slab, deepslate_tile_slab, scaffolding
               -1,   5504,   6171,   7400,   1124,   2376,   7198,   7019, // redstone, redstone_torch, redstone_block, repeater, comparator, piston, sticky_piston, slime_block
             2329,   5495,  12103,  13797,  13067,  12153,  11534,  11709, // honey_block, observer, hopper, dispenser, dropper, lectern, target, lever
             2616,   6983,   7221,  10048,  10615,   9383,  11900,   7184, // lightning_rod, daylight_detector, sculk_sensor, calibrated_sculk_sensor, tripwire_hook, trapped_chest, tnt, redstone_lamp
             1087,   1354,  13526,  11542,   7156,  13502,    134,  12901, // note_block, stone_button, polished_blackstone_button, oak_button, spruce_button, birch_button, jungle_button, acacia_button
             7494,    108,  12282,  11663,   7280,  12918,   6663,  11374, // cherry_button, dark_oak_button, mangrove_button, bamboo_button, crimson_button, warped_button, stone_pressure_plate, polished_blackstone_pressure_plate
             6049,   2597,  13845,   6154,   5933,   6019,   9006,    153, // light_weighted_pressure_plate, heavy_weighted_pressure_plate, oak_pressure_plate, spruce_pressure_plate, birch_pressure_plate, jungle_pressure_plate, acacia_pressure_plate, cherry_pressure_plate
            10644,   6645,  11197,  14075,    701,   7301,   6133,   7997, // dark_oak_pressure_plate, mangrove_pressure_plate, bamboo_pressure_plate, crimson_pressure_plate, warped_pressure_plate, iron_door, oak_door, spruce_door
            12345,  11290,   7251,   7102,   9529,  11561,    753,   6626, // birch_door, jungle_door, acacia_door, cherry_door, dark_oak_door, mangrove_door, bamboo_door, crimson_door
               43,  10673,   8039,  11593,  14109,  13474,   9243,  12304, // warped_door, copper_door, exposed_copper_door, weathered_copper_door, oxidized_copper_door, waxed_copper_door, waxed_exposed_copper_door, waxed_weathered_copper_door
            10582,   1074,    666,  11765,  11866,   9159,   9399,   2361, // waxed_oxidized_copper_door, iron_trapdoor, oak_trapdoor, spruce_trapdoor, birch_trapdoor, jungle_trapdoor, acacia_trapdoor, cherry_trapdoor
            13196,   7342,   9035,   7178,   8079,  10068,  11231,  11734, // dark_oak_trapdoor, mangrove_trapdoor, bamboo_trapdoor, crimson_trapdoor, warped_trapdoor, copper_trapdoor, exposed_copper_trapdoor, weathered_copper_trapdoor
             7443,   5927,  10512,   8113,   8604,     98,  11798,   6186, // oxidized_copper_trapdoor, waxed_copper_trapdoor, waxed_exposed_copper_trapdoor, waxed_weathered_copper_trapdoor, waxed_oxidized_copper_trapdoor, oak_fence_gate, spruce_fence_gate, birch_fence_gate
             9141,  13286,  14054,   6965,   7515,   8817,   7967,   9178, // jungle_fence_gate, acacia_fence_gate, cherry_fence_gate, dark_oak_fence_gate, mangrove_fence_gate, bamboo_fence_gate, crimson_fence_gate, warped_fence_gate
             9098,   6914,   6696,   1054,     -1,     -1,     -1,     -1, // powered_rail, detector_rail, rail, activator_rail, saddle, minecart, chest_minecart, furnace_minecart
               -1,     -1,     -1,     -1,     -1,     -1,     -1,     -1, // tnt_minecart, hopper_minecart, carrot_on_a_stick, warped_fungus_on_a_stick, elytra, oak_boat, oak_chest_boat, spruce_boat
               -1,     -1,     -1,     -1,     -1,     -1,     -1,     -1, // spruce_chest_boat, birch_boat, birch_chest_boat, jungle_boat, jungle_chest_boat, acacia_boat, acacia_chest_boat, cherry_boat
               -1,     -1,     -1,     -1,     -1,     -1,     -1,  11520, // cherry_chest_boat, dark_oak_boat, dark_oak_chest_boat, mangrove_boat, mangrove_chest_boat, bamboo_raft, bamboo_chest_raft, structure_block
             8616,     -1,     -1,     -1,     -1,     -1,     -1,     -1, // jigsaw, turtle_helmet, scute, flint_and_steel, apple, bow, arrow, coal
               -1,     -1,     -1,     -1,     -1,     -1,     -1,     -1, // charcoal, diamond, emerald, lapis_lazuli, quartz, amethyst_shard, raw_iron, iron_ingot
               -1,     -1,     -1,     -1,     -1,     -1,     -1,     -1, // raw_copper, copper_ingot, raw_gold, gold_ingot, netherite_ingot, netherite_scrap, wooden_sword, wooden_shovel
               -1,     -1,     -1,     -1,     -1,     -1,     -1,     -1, // wooden_pickaxe, wooden_axe, wooden_hoe, stone_sword, stone_shovel, stone_pickaxe, stone_axe, stone_hoe
               -1,     -1,     -1,     -1,     -1,     -1,     -1,     -1, // golden_sword, golden_shovel, golden_pickaxe, golden_axe, golden_hoe, iron_sword, iron_shovel, iron_pickaxe
               -1,     -1,     -1,     -1,     -1,     -1,     -1,     -1, // iron_axe, iron_hoe, diamond_sword, diamond_shovel, diamond_pickaxe, diamond_axe, diamond_hoe, netherite_sword
               -1,     -1,     -1,     -1,     -1,     -1,     -1,     -1, // netherite_shovel, netherite_pickaxe, netherite_axe, netherite_hoe, stick, bowl, mushroom_stew, string
               -1,     -1,     -1,  12965,     -1,     -1,     -1,     -1, // feather, gunpowder, wheat_seeds, wheat, bread, leather_helmet, leather_chestplate, leather_leggings
               -1,     -1,     -1,     -1,     -1,     -1,     -1,     -1, // leather_boots, chainmail_helmet, chainmail_chestplate, chainmail_leggings, chainmail_boots, iron_helmet, iron_chestplate, iron_leggings
               -1,     -1,     -1,     -1,     -1,     -1,     -1,     -1, // iron_boots, diamond_helmet, diamond_chestplate, diamond_leggings, diamond_boots, golden_helmet, golden_chestplate, golden_leggings
               -1,     -1,     -1,     -1,     -1,     -1,     -1,     -1, // golden_boots, netherite_helmet, netherite_chestplate, netherite_leggings, netherite_boots, flint, porkchop, cooked_porkchop
               -1,     -1,     -1,   9401,  12259,      9,  11151,  11389, // painting, golden_apple, enchanted_golden_apple, oak_sign, spruce_sign, birch_sign, jungle_sign, acacia_sign
            10085,  13009,   6999,  12860,  12993,  12843,   8348,   9828, // cherry_sign, dark_oak_sign, mangrove_sign, bamboo_sign, crimson_sign, warped_sign, oak_hanging_sign, spruce_hanging_sign
             1572,   6416,   5133,    363,   5715,  10309,   7744,  12587, // birch_hanging_sign, jungle_hanging_sign, acacia_hanging_sign, cherry_hanging_sign, dark_oak_hanging_sign, mangrove_hanging_sign, bamboo_hanging_sign, crimson_hanging_sign
            10922,     -1,     -1,     -1,     -1,     -1,     -1,     -1, // warped_hanging_sign, bucket, water_bucket, lava_bucket, powder_snow_bucket, snowball, leather, milk_bucket
               -1,     -1,     -1,     -1,     -1,     -1,     -1,     -1, // pufferfish_bucket, salmon_bucket, cod_bucket, tropical_fish_bucket, axolotl_bucket, tadpole_bucket, brick, clay_ball
            13756,     -1,     -1,     -1,     -1,     -1,     -1,     -1, // dried_kelp_block, paper, book, slime_ball, egg, compass, recovery_compass, bundle
               -1,     -1,     -1,     -1,     -1,     -1,     -1,     -1, // fishing_rod, clock, spyglass, glowstone_dust, cod, salmon, tropical_fish, pufferfish
               -1,     -1,     -1,     -1,     -1,     -1,     -1,     -1, // cooked_cod, cooked_salmon, ink_sac, glow_ink_sac, cocoa_beans, white_dye, orange_dye, magenta_dye
               -1,     -1,     -1,     -1,     -1,     -1,     -1,     -1, // light_blue_dye, yellow_dye, lime_dye, pink_dye, gray_dye, light_gray_dye, cyan_dye, purple_dye
               -1,     -1,     -1,     -1,     -1,     -1,     -1,     -1, // blue_dye, brown_dye, green_dye, red_dye, black_dye, bone_meal, bone, sugar
            12385,  11896,  11896,  11896,  11896,  11896,  11896,  11896, // cake, white_bed, orange_bed, magenta_bed, light_blue_bed, yellow_bed, lime_bed, pink_bed
            11896,  11896,  11896,  11896,  11896,  11896,  11896,  11896, // gray_bed, light_gray_bed, cyan_bed, purple_bed, blue_bed, brown_bed, green_bed, red_bed
            11896,     -1,  13567,     -1,     -1,     -1,     -1,     -1, // black_bed, cookie, crafter, filled_map, shears, melon_slice, dried_kelp, pumpkin_seeds
               -1,     -1,     -1,     -1,     -1,     -1,     -1,     -1, // melon_seeds, beef, cooked_beef, chicken, cooked_chicken, rotten_flesh, ender_pearl, blaze_rod
               -1,     -1,  13439,     -1,     -1,     -1,     -1,     -1, // ghast_tear, gold_nugget, nether_wart, potion, glass_bottle, spider_eye, fermented_spider_eye, blaze_powder
               -1,  13237,  13135,     -1,     -1,     -1,     -1,     -1, // magma_cream, brewing_stand, cauldron, ender_eye, glistering_melon_slice, allay_spawn_egg, axolotl_spawn_egg, bat_spawn_egg
               -1,     -1,     -1,     -1,     -1,     -1,     -1,     -1, // bee_spawn_egg, blaze_spawn_egg, breeze_spawn_egg, cat_spawn_egg, camel_spawn_egg, cave_spider_spawn_egg, chicken_spawn_egg, cod_spawn_egg
               -1,     -1,     -1,     -1,     -1,     -1,     -1,     -1, // cow_spawn_egg, creeper_spawn_egg, dolphin_spawn_egg, donkey_spawn_egg, drowned_spawn_egg, elder_guardian_spawn_egg, ender_dragon_spawn_egg, enderman_spawn_egg
               -1,     -1,     -1,     -1,     -1,     -1,     -1,     -1, // endermite_spawn_egg, evoker_spawn_egg, fox_spawn_egg, frog_spawn_egg, ghast_spawn_egg, glow_squid_spawn_egg, goat_spawn_egg, guardian_spawn_egg
               -1,     -1,     -1,     -1,     -1,     -1,     -1,     -1, // hoglin_spawn_egg, horse_spawn_egg, husk_spawn_egg, iron_golem_spawn_egg, llama_spawn_egg, magma_cube_spawn_egg, mooshroom_spawn_egg, mule_spawn_egg
               -1,     -1,     -1,     -1,     -1,     -1,     -1,     -1, // ocelot_spawn_egg, panda_spawn_egg, parrot_spawn_egg, phantom_spawn_egg, pig_spawn_egg, piglin_spawn_egg, piglin_brute_spawn_egg, pillager_spawn_egg
               -1,     -1,     -1,     -1,     -1,     -1,     -1,     -1, // polar_bear_spawn_egg, pufferfish_spawn_egg, rabbit_spawn_egg, ravager_spawn_egg, salmon_spawn_egg, sheep_spawn_egg, shulker_spawn_egg, silverfish_spawn_egg
               -1,     -1,     -1,     -1,     -1,     -1,     -1,     -1, // skeleton_spawn_egg, skeleton_horse_spawn_egg, slime_spawn_egg, sniffer_spawn_egg, snow_golem_spawn_egg, spider_spawn_egg, squid_spawn_egg, stray_spawn_egg
               -1,     -1,     -1,     -1,     -1,     -1,     -1,     -1, // strider_spawn_egg, tadpole_spawn_egg, trader_llama_spawn_egg, tropical_fish_spawn_egg, turtle_spawn_egg, vex_spawn_egg, villager_spawn_egg, vindicator_spawn_egg
               -1,     -1,     -1,     -1,     -1,     -1,     -1,     -1, // wandering_trader_spawn_egg, warden_spawn_egg, witch_spawn_egg, wither_spawn_egg, wither_skeleton_spawn_egg, wolf_spawn_egg, zoglin_spawn_egg, zombie_spawn_egg
               -1,     -1,     -1,     -1,     -1,     -1,     -1,     -1, // zombie_horse_spawn_egg, zombie_villager_spawn_egg, zombified_piglin_spawn_egg, experience_bottle, fire_charge, writable_book, written_book, item_frame
               -1,    773,     -1,     -1,     -1,     -1,     -1,     -1, // glow_item_frame, flower_pot, carrot, potato, baked_potato, poisonous_potato, map, golden_carrot
             9068,   9068,   9068,   9068,   9068,   9068,   9068,     -1, // skeleton_skull, wither_skeleton_skull, player_head, zombie_head, creeper_head, dragon_head, piglin_head, nether_star
               -1,     -1,     -1,     -1,     -1,     -1,     -1,     -1, // pumpkin_pie, firework_rocket, firework_star, enchanted_book, nether_brick, prismarine_shard, prismarine_crystals, rabbit
               -1,     -1,     -1,     -1,     -1,     -1,     -1,     -1, // cooked_rabbit, rabbit_stew, rabbit_foot, rabbit_hide, armor_stand, iron_horse_armor, golden_horse_armor, diamond_horse_armor
               -1,     -1,     -1,     -1,     -1,     -1,  12158,  12158, // leather_horse_armor, lead, name_tag, command_block_minecart, mutton, cooked_mutton, white_banner, orange_banner
            12158,  12158,  12158,  12158,  12158,  12158,  12158,  12158, // magenta_banner, light_blue_banner, yellow_banner, lime_banner, pink_banner, gray_banner, light_gray_banner, cyan_banner
            12158,  12158,  12158,  12158,  12158,  12158,     -1,     -1, // purple_banner, blue_banner, brown_banner, green_banner, red_banner, black_banner, end_crystal, chorus_fruit
               -1,     -1,     -1,     -1,     -1,     -1,     -1,     -1, // popped_chorus_fruit, torchflower_seeds, pitcher_pod, beetroot, beetroot_seeds, beetroot_soup, dragon_breath, splash_potion
               -1,     -1,     -1,     -1,     -1,     -1,     -1,     -1, // spectral_arrow, tipped_arrow, lingering_potion, shield, totem_of_undying, shulker_shell, iron_nugget, knowledge_book
               -1,     -1,     -1,     -1,     -1,     -1,     -1,     -1, // debug_stick, music_disc_13, music_disc_cat, music_disc_blocks, music_disc_chirp, music_disc_far, music_disc_mall, music_disc_mellohi
               -1,     -1,     -1,     -1,     -1,     -1,     -1,     -1, // music_disc_stal, music_disc_strad, music_disc_ward, music_disc_11, music_disc_wait, music_disc_otherside, music_disc_relic, music_disc_5
               -1,     -1,     -1,     -1,     -1,     -1,     -1,     -1, // music_disc_pigstep, disc_fragment_5, trident, phantom_membrane, nautilus_shell, heart_of_the_sea, crossbow, suspicious_stew
             6607,     -1,     -1,     -1,     -1,     -1,     -1,     -1, // loom, flower_banner_pattern, creeper_banner_pattern, skull_banner_pattern, mojang_banner_pattern, globe_banner_pattern, piglin_banner_pattern, goat_horn
             9180,   7369,   1769,  13256,  14094,  10019,  13821,   6113, // composter, barrel, smoker, blast_furnace, cartography_table, fletching_table, grindstone, smithing_table
             2372,  12133,  12289,   9549,     -1,     -1,   9582,  13838, // stonecutter, bell, lantern, soul_lantern, sweet_berries, glow_berries, campfire, soul_campfire
             8802,     -1,   9556,  11254,     -1,   7319,  14062,  11916, // shroomlight, honeycomb, bee_nest, beehive, honey_bottle, honeycomb_block, lodestone, crying_obsidian
            13271,   2364,  12237,   7449,   6066,  11123,   7129,   8819, // blackstone, blackstone_slab, blackstone_stairs, gilded_blackstone, polished_blackstone, polished_blackstone_slab, polished_blackstone_stairs, chiseled_polished_blackstone
             7972,   6978,   7326,  12859,   1947,  13084,   9079,   1110, // polished_blackstone_bricks, polished_blackstone_brick_slab, polished_blackstone_brick_stairs, cracked_polished_blackstone_bricks, respawn_anchor, candle, white_candle, orange_candle
             1170,   7418,  11324,  11515,  13050,   2397,  11369,  13422, // magenta_candle, light_blue_candle, yellow_candle, lime_candle, pink_candle, gray_candle, light_gray_candle, cyan_candle
            12255,      5,  10518,   1957,   7977,    591,   1044,   7208, // purple_candle, blue_candle, brown_candle, green_candle, red_candle, black_candle, small_amethyst_bud, medium_amethyst_bud
             8021,  13587,  13088,   5323,  11671,  11612,   7236,     -1, // large_amethyst_bud, amethyst_cluster, pointed_dripstone, ochre_froglight, verdant_froglight, pearlescent_froglight, frogspawn, echo_shard
               -1,     -1,     -1,     -1,     -1,     -1,     -1,     -1, // brush, netherite_upgrade_smithing_template, sentry_armor_trim_smithing_template, dune_armor_trim_smithing_template, coast_armor_trim_smithing_template, wild_armor_trim_smithing_template, ward_armor_trim_smithing_template, eye_armor_trim_smithing_template
               -1,     -1,     -1,     -1,     -1,     -1,     -1,     -1, // vex_armor_trim_smithing_template, tide_armor_trim_smithing_template, snout_armor_trim_smithing_template, rib_armor_trim_smithing_template, spire_armor_trim_smithing_template, wayfinder_armor_trim_smithing_template, shaper_armor_trim_smithing_template, silence_armor_trim_smithing_template
               -1,     -1,     -1,     -1,     -1,     -1,     -1,     -1, // raiser_armor_trim_smithing_template, host_armor_trim_smithing_template, angler_pottery_sherd, archer_pottery_sherd, arms_up_pottery_sherd, blade_pottery_sherd, brewer_pottery_sherd, burn_pottery_sherd
               -1,     -1,     -1,     -1,     -1,     -1,     -1,     -1, // danger_pottery_sherd, explorer_pottery_sherd, friend_pottery_sherd, heart_pottery_sherd, heartbreak_pottery_sherd, howl_pottery_sherd, miner_pottery_sherd, mourner_pottery_sherd
               -1,     -1,     -1,     -1,     -1,     -1,   1104,   9143, // plenty_pottery_sherd, prize_pottery_sherd, sheaf_pottery_sherd, shelter_pottery_sherd, skull_pottery_sherd, snort_pottery_sherd, copper_grate, exposed_copper_grate
             6114,  11835,   9144,   1341,   7148,  13863,   7227,  11417, // weathered_copper_grate, oxidized_copper_grate, waxed_copper_grate, waxed_exposed_copper_grate, waxed_weathered_copper_grate, waxed_oxidized_copper_grate, copper_bulb, exposed_copper_bulb
            13253,   2408,   1362,   6976,  10041,  11433,   1148,     -1, // weathered_copper_bulb, oxidized_copper_bulb, waxed_copper_bulb, waxed_exposed_copper_bulb, waxed_weathered_copper_bulb, waxed_oxidized_copper_bulb, trial_spawner, trial_key
    };

    /**
     * Java 765 のアイテムIDから Bedrock ブロック・ランタイムIDを引く。
     * ブロックでないアイテム・範囲外は 0。
     */
    public static int blockRuntimeId(int item765) {
        if (item765 < 0 || item765 >= BLOCK_RUNTIME_IDS_765.length) {
            return 0;
        }
        int id = BLOCK_RUNTIME_IDS_765[item765];
        return id < 0 ? 0 : id;
    }
}
