package io.detergentmc.bedrock;

/**
 * Java 1.20.4（プロトコル 765）のアイテムIDから Bedrock の
 * アイテム・ネットワークIDへの対応表。
 *
 * <p>Java 側の順序は ViaVersion Mappings の mapping-1.20.3.json
 * （items リスト。index がレジストリ ID）による。
 * バニラ 1.20.4 は設定フェーズで minecraft:item を送らない
 * ため（実機プローブで確認済み）、この静的表が唯一の典拠である。
 * 763／764／766 サーバーのスロットは RegistryRemapper で
 * 765 に正規化してから引く。
 *
 * <p>Bedrock 側のネットワーク ID は Microsoft Learn「Default
 * Minecraft Item Listings」（2026-11-08 閲覧）による。
 * 載っていないのは空気（アイテムとしては送らない）と、
 * 銅のドア／トラップドア／格子／電球類（1.21以降で Bedrock
 * 1.20.50 に無い）、束・スペクトル矢・先端矢・知識の本・
 * 輝くベリー・かまどトロッコ・トライアルキーなどで、
 * それらは石（1）になる。実験的（1.21系）アイテムの ID は
 * 現行一覧の値で、1.20.50 では表示が異なる場合がある。
 *
 * <p>この表は 1.26 世代の並びのもので、1.20.50 の Bedrock の
 * 番号とは一致しない箇所がある。バージョン別の正しい番号は
 * PMMP BedrockData の {@code required_item_list.json}
 * （各 Bedrock バージョンのタグ）から生成した
 * {@code bedrock_item_ids_<tag>.bin}（resources、1312 個×
 * （network_id・metadata)）にあり、
 * {@link #networkId(int, int)}・{@link #metadata(int, int)}
 * がそれを読む。bin が無いバージョンはこの表にフォールバックする
 * （metadata は 0）。
 *
 * <p>Bedrock の network_id は負の値もふつうにある（ブロック系は
 * zigzag32 で負で表す）。未対応の 40 アイテムだけ -1 で、-1 以外は
 * すべて有効な番号である。負だからといって石（1）に置き換えては
 * いけない（以前の実装はここが誤りで、約 625 アイテムが石になっていた）。
 *
 * <p>網羅率（1312種中）：対応 1272、未対応 40。
 */
public final class BedrockItemIds {

    private BedrockItemIds() {
    }

    /** Java 765 のアイテム数。 */
    public static final int COUNT = 1312;

    /** この表で Bedrock ID が決まるアイテム数。 */
    public static final int MAPPED_COUNT = 1272;

    /** 表に無いアイテムの代替（石）。 */
    public static final int FALLBACK_NETWORK_ID = 1;

    /** 表に無いアイテムの代替 metadata。 */
    public static final int FALLBACK_METADATA = 0;

    /** バージョン別表の読み込みを同期する錠。 */
    private static final Object TABLES_LOCK = new Object();

    /** バージョン（タグ）→読み込んだ表。読み込めなかったタグは空の配列を入れる。 */
    private static final java.util.Map<String, int[][]> TABLES_BY_TAG =
            new java.util.HashMap<>();

    /** 765 のアイテムID順。無いものは -1。 */
    private static final int[] NETWORK_IDS_765 = {
               -1,     1,  -590,  -591,  -592,  -593,  -594,  -595, // air, stone, granite, polished_granite, diorite, polished_diorite, andesite, polished_andesite
             -378,  -379,  -383,  -326,  -333,  -744,  -746,  -747, // deepslate, cobbled_deepslate, polished_deepslate, calcite, tuff, tuff_slab, tuff_stairs, tuff_wall
             -753,  -748,  -749,  -751,  -752,  -754,  -755,  -757, // chiseled_tuff, polished_tuff, polished_tuff_slab, polished_tuff_stairs, polished_tuff_wall, tuff_bricks, tuff_brick_slab, tuff_brick_stairs
             -758,  -759,  -317,     2,     3,  -962,   243,  -318, // tuff_brick_wall, chiseled_tuff_bricks, dripstone_block, grass_block, dirt, coarse_dirt, podzol, rooted_dirt
             -473,  -232,  -233,     4,     5,  -739,  -740,  -741, // mud, crimson_nylium, warped_nylium, cobblestone, oak_planks, spruce_planks, birch_planks, jungle_planks
             -742,  -537,  -743,  -486,  -510,  -242,  -243,  -509, // acacia_planks, cherry_planks, dark_oak_planks, mangrove_planks, bamboo_planks, crimson_planks, warped_planks, bamboo_mosaic
                6,  -825,  -826,  -827,  -828,  -547,  -829,  -474, // oak_sapling, spruce_sapling, birch_sapling, jungle_sapling, acacia_sapling, cherry_sapling, dark_oak_sapling, mangrove_propagule
                7,    12,  -529,  -573,  -949,    13,    16,  -406, // bedrock, sand, suspicious_sand, suspicious_gravel, red_sand, gravel, coal_ore, deepslate_coal_ore
               15,  -401,  -311,  -408,    14,  -402,    73,  -403, // iron_ore, deepslate_iron_ore, copper_ore, deepslate_copper_ore, gold_ore, deepslate_gold_ore, redstone_ore, deepslate_redstone_ore
              129,  -407,    21,  -400,    56,  -405,  -288,   153, // emerald_ore, deepslate_emerald_ore, lapis_ore, deepslate_lapis_ore, diamond_ore, deepslate_diamond_ore, nether_gold_ore, nether_quartz_ore
             -271,   173,  -451,  -452,  -453,  -327,  -328,    42, // ancient_debris, coal_block, raw_iron_block, raw_copper_block, raw_gold_block, amethyst_block, budding_amethyst, iron_block
             -340,    41,    57,  -270,  -341,  -342,  -343,  -760, // copper_block, gold_block, diamond_block, netherite_block, exposed_copper, weathered_copper, oxidized_copper, chiseled_copper
             -761,  -762,  -763,  -347,  -348,  -349,  -350,  -354, // exposed_chiseled_copper, weathered_chiseled_copper, oxidized_chiseled_copper, cut_copper, exposed_cut_copper, weathered_cut_copper, oxidized_cut_copper, cut_copper_stairs
             -355,  -356,  -357,  -361,  -362,  -363,  -364,  -344, // exposed_cut_copper_stairs, weathered_cut_copper_stairs, oxidized_cut_copper_stairs, cut_copper_slab, exposed_cut_copper_slab, weathered_cut_copper_slab, oxidized_cut_copper_slab, waxed_copper_block
             -345,  -346,  -446,  -764,  -765,  -766,  -767,  -351, // waxed_exposed_copper, waxed_weathered_copper, waxed_oxidized_copper, waxed_chiseled_copper, waxed_exposed_chiseled_copper, waxed_weathered_chiseled_copper, waxed_oxidized_chiseled_copper, waxed_cut_copper
             -352,  -353,  -447,  -358,  -359,  -360,  -448,  -365, // waxed_exposed_cut_copper, waxed_weathered_cut_copper, waxed_oxidized_cut_copper, waxed_cut_copper_stairs, waxed_exposed_cut_copper_stairs, waxed_weathered_cut_copper_stairs, waxed_oxidized_cut_copper_stairs, waxed_cut_copper_slab
             -366,  -367,  -449,    17,  -569,  -570,  -571,   162, // waxed_exposed_cut_copper_slab, waxed_weathered_cut_copper_slab, waxed_oxidized_cut_copper_slab, oak_log, spruce_log, birch_log, jungle_log, acacia_log
             -536,  -572,  -484,  -482,  -483,  -225,  -226,  -527, // cherry_log, dark_oak_log, mangrove_log, mangrove_roots, muddy_mangrove_roots, crimson_stem, warped_stem, bamboo_block
              -10,    -5,    -6,    -7,    -8,  -535,    -9,  -485, // stripped_oak_log, stripped_spruce_log, stripped_birch_log, stripped_jungle_log, stripped_acacia_log, stripped_cherry_log, stripped_dark_oak_log, stripped_mangrove_log
             -240,  -241,  -819,  -820,  -821,  -822,  -823,  -545, // stripped_crimson_stem, stripped_warped_stem, stripped_oak_wood, stripped_spruce_wood, stripped_birch_wood, stripped_jungle_wood, stripped_acacia_wood, stripped_cherry_wood
             -824,  -498,  -300,  -301,  -528,  -212,  -814,  -815, // stripped_dark_oak_wood, stripped_mangrove_wood, stripped_crimson_hyphae, stripped_warped_hyphae, stripped_bamboo_block, oak_wood, spruce_wood, birch_wood
             -816,  -817,  -546,  -818,  -497,  -299,  -298,    18, // jungle_wood, acacia_wood, cherry_wood, dark_oak_wood, mangrove_wood, crimson_hyphae, warped_hyphae, oak_leaves
             -800,  -801,  -802,   161,  -548,  -803,  -472,  -324, // spruce_leaves, birch_leaves, jungle_leaves, acacia_leaves, cherry_leaves, dark_oak_leaves, mangrove_leaves, azalea_leaves
             -322,    19,  -984,    20,  -334,    22,    24,  -944, // flowering_azalea_leaves, sponge, wet_sponge, glass, tinted_glass, lapis_block, sandstone, chiseled_sandstone
             -945,    30,    31,  -848,  -337,  -338,    32,  -130, // cut_sandstone, cobweb, short_grass, fern, azalea, flowering_azalea, dead_bush, seagrass
             -156,    35,  -557,  -565,  -562,  -558,  -559,  -566, // sea_pickle, white_wool, orange_wool, magenta_wool, light_blue_wool, yellow_wool, lime_wool, pink_wool
             -553,  -552,  -561,  -564,  -563,  -555,  -560,  -556, // gray_wool, light_gray_wool, cyan_wool, purple_wool, blue_wool, brown_wool, green_wool, red_wool
             -554,    37,    38,  -830,  -831,  -832,  -833,  -834, // black_wool, dandelion, poppy, blue_orchid, allium, azure_bluet, red_tulip, orange_tulip
             -835,  -836,  -837,  -838,  -839,  -216,  -568,  -612, // white_tulip, pink_tulip, oxeye_daisy, cornflower, lily_of_the_valley, wither_rose, torchflower, pitcher_plant
             -321,    39,    40,  -228,  -229,  -223,  -224,   637, // spore_blossom, brown_mushroom, red_mushroom, crimson_fungus, warped_fungus, crimson_roots, warped_roots, nether_sprouts
             -231,  -287,   388,   385,  -335,  -549,  -320,  -319, // weeping_vines, twisting_vines, sugar_cane, kelp, moss_carpet, pink_petals, moss_block, hanging_roots
             -323,  -336,  -163,   158,  -804,  -805,  -806,  -807, // big_dripleaf, small_dripleaf, bamboo, oak_slab, spruce_slab, birch_slab, jungle_slab, acacia_slab
             -539,  -808,  -489,  -513,  -524,  -264,  -265,   799, // cherry_slab, dark_oak_slab, mangrove_slab, bamboo_slab, bamboo_mosaic_slab, crimson_slab, warped_slab, stone_slab
               44,  -872,  -900,    44,  -873,  -874,  -875,  -478, // smooth_stone_slab, sandstone_slab, cut_sandstone_slab, petrified_oak_slab, cobblestone_slab, brick_slab, stone_brick_slab, mud_brick_slab
             -877,  -876,   182,  -901,  -884,  -885,  -887,  -886, // nether_brick_slab, quartz_slab, red_sandstone_slab, cut_red_sandstone_slab, purpur_slab, prismarine_slab, prismarine_brick_slab, dark_prismarine_slab
             -955,  -958,  -946,  -183,    45,    47,  -526,  -551, // smooth_quartz, smooth_red_sandstone, smooth_sandstone, smooth_stone, bricks, bookshelf, chiseled_bookshelf, decorated_pot
               48,    49,    50,   208,   240,   200,   201,  -951, // mossy_cobblestone, obsidian, torch, end_rod, chorus_plant, chorus_flower, purpur_block, purpur_pillar
              203,    52,    54,    58,    60,    61,    65,    67, // purpur_stairs, spawner, chest, crafting_table, farmland, furnace, ladder, cobblestone_stairs
               80,    79,    80,    81,    82,    84,    85,  -579, // snow, ice, snow_block, cactus, clay, jukebox, oak_fence, spruce_fence
             -576,  -578,  -575,  -532,  -577,  -491,  -515,  -256, // birch_fence, jungle_fence, acacia_fence, cherry_fence, dark_oak_fence, mangrove_fence, bamboo_fence, crimson_fence
             -257,    86,  -155,    91,    87,    88,  -236,  -234, // warped_fence, pumpkin, carved_pumpkin, jack_o_lantern, netherrack, soul_sand, soul_soil, basalt
             -235,  -377,  -268,    89,    97,  -858,  -859,  -860, // polished_basalt, smooth_basalt, soul_torch, glowstone, infested_stone, infested_cobblestone, infested_stone_bricks, infested_mossy_stone_bricks
             -861,  -862,  -454,    98,  -868,  -869,  -870,  -477, // infested_cracked_stone_bricks, infested_chiseled_stone_bricks, infested_deepslate, stone_bricks, mossy_stone_bricks, cracked_stone_bricks, chiseled_stone_bricks, packed_mud
             -475,  -391,  -410,  -387,  -409,  -395,  -466,    99, // mud_bricks, deepslate_bricks, cracked_deepslate_bricks, deepslate_tiles, cracked_deepslate_tiles, chiseled_deepslate, reinforced_deepslate, brown_mushroom_block
              100, -1008,   101,  -286,   102,   103,   106,  -411, // red_mushroom_block, mushroom_stem, iron_bars, chain, glass_pane, melon, vine, glow_lichen
              108,   109,  -480,   110,   111,   112,  -303,  -302, // brick_stairs, stone_brick_stairs, mud_brick_stairs, mycelium, lily_pad, nether_bricks, cracked_nether_bricks, chiseled_nether_bricks
              113,   114,  -458,  -459,  -460,  -461,   116,   120, // nether_brick_fence, nether_brick_stairs, sculk, sculk_vein, sculk_catalyst, sculk_shrieker, enchanting_table, end_portal_frame
              121,   206,   122,   128,   130,   133,    53,   134, // end_stone, end_stone_bricks, dragon_egg, sandstone_stairs, ender_chest, emerald_block, oak_stairs, spruce_stairs
              135,   136,   163,  -541,   164,  -488,  -512,  -523, // birch_stairs, jungle_stairs, acacia_stairs, cherry_stairs, dark_oak_stairs, mangrove_stairs, bamboo_stairs, bamboo_mosaic_stairs
             -254,  -255,   137,   138,   139,  -971,  -976,  -981, // crimson_stairs, warped_stairs, command_block, beacon, cobblestone_wall, mossy_cobblestone_wall, brick_wall, prismarine_wall
             -982,  -978,  -972,  -977,  -481,  -979,  -974,  -983, // red_sandstone_wall, mossy_stone_brick_wall, granite_wall, stone_brick_wall, mud_brick_wall, nether_brick_wall, andesite_wall, red_nether_brick_wall
             -975,  -980,  -973,  -277,  -297,  -278,  -382,  -386, // sandstone_wall, end_stone_brick_wall, diorite_wall, blackstone_wall, polished_blackstone_wall, polished_blackstone_brick_wall, cobbled_deepslate_wall, polished_deepslate_wall
             -394,  -390,   145,  -959,  -960,  -953,   155,  -304, // deepslate_brick_wall, deepslate_tile_wall, anvil, chipped_anvil, damaged_anvil, chiseled_quartz_block, quartz_block, quartz_bricks
             -954,   156,   159,  -724,  -725,  -726,  -727,  -728, // quartz_pillar, quartz_stairs, white_terracotta, orange_terracotta, magenta_terracotta, light_blue_terracotta, yellow_terracotta, lime_terracotta
             -729,  -730,  -731,  -732,  -733,  -734,  -735,  -736, // pink_terracotta, gray_terracotta, light_gray_terracotta, cyan_terracotta, purple_terracotta, blue_terracotta, brown_terracotta, green_terracotta
             -737,  -738,  -161,   847,   170,   171,  -597,  -598, // red_terracotta, black_terracotta, barrier, light, hay_block, white_carpet, orange_carpet, magenta_carpet
             -599,  -600,  -601,  -602,  -603,  -604,  -605,  -606, // light_blue_carpet, yellow_carpet, lime_carpet, pink_carpet, gray_carpet, light_gray_carpet, cyan_carpet, purple_carpet
             -607,  -608,  -609,  -610,  -611,   172,   174,   198, // blue_carpet, brown_carpet, green_carpet, red_carpet, black_carpet, terracotta, packed_ice, dirt_path
              175,  -863,  -866,  -867,   823,  -865,   241,  -673, // sunflower, lilac, rose_bush, peony, tall_grass, large_fern, white_stained_glass, orange_stained_glass
             -674,  -675,  -676,  -677,  -678,  -679,  -680,  -681, // magenta_stained_glass, light_blue_stained_glass, yellow_stained_glass, lime_stained_glass, pink_stained_glass, gray_stained_glass, light_gray_stained_glass, cyan_stained_glass
             -682,  -683,  -684,  -685,  -686,  -687,   160,  -643, // purple_stained_glass, blue_stained_glass, brown_stained_glass, green_stained_glass, red_stained_glass, black_stained_glass, white_stained_glass_pane, orange_stained_glass_pane
             -644,  -645,  -646,  -647,  -648,  -649,  -650,  -651, // magenta_stained_glass_pane, light_blue_stained_glass_pane, yellow_stained_glass_pane, lime_stained_glass_pane, pink_stained_glass_pane, gray_stained_glass_pane, light_gray_stained_glass_pane, cyan_stained_glass_pane
             -652,  -653,  -654,  -655,  -656,  -657,   168,  -948, // purple_stained_glass_pane, blue_stained_glass_pane, brown_stained_glass_pane, green_stained_glass_pane, red_stained_glass_pane, black_stained_glass_pane, prismarine, prismarine_bricks
             -947,    -2,    -4,    -3,   169,   179,  -956,  -957, // dark_prismarine, prismarine_stairs, prismarine_brick_stairs, dark_prismarine_stairs, sea_lantern, red_sandstone, chiseled_red_sandstone, cut_red_sandstone
              180,   188,   189,   213,   214,  -227,   215,   216, // red_sandstone_stairs, repeating_command_block, chain_command_block, magma_block, nether_wart_block, warped_wart_block, red_nether_bricks, bone_block
              217,   832,   218,  -613,  -614,  -615,  -616,  -617, // structure_void, shulker_box, white_shulker_box, orange_shulker_box, magenta_shulker_box, light_blue_shulker_box, yellow_shulker_box, lime_shulker_box
             -618,  -619,  -620,  -621,  -622,  -623,  -624,  -625, // pink_shulker_box, gray_shulker_box, light_gray_shulker_box, cyan_shulker_box, purple_shulker_box, blue_shulker_box, brown_shulker_box, green_shulker_box
             -626,  -627,   220,   221,   222,   223,   224,   225, // red_shulker_box, black_shulker_box, white_glazed_terracotta, orange_glazed_terracotta, magenta_glazed_terracotta, light_blue_glazed_terracotta, yellow_glazed_terracotta, lime_glazed_terracotta
              226,   227,   228,   229,   219,   231,   232,   233, // pink_glazed_terracotta, gray_glazed_terracotta, light_gray_glazed_terracotta, cyan_glazed_terracotta, purple_glazed_terracotta, blue_glazed_terracotta, brown_glazed_terracotta, green_glazed_terracotta
              234,   235,   236,  -628,  -629,  -630,  -631,  -632, // red_glazed_terracotta, black_glazed_terracotta, white_concrete, orange_concrete, magenta_concrete, light_blue_concrete, yellow_concrete, lime_concrete
             -633,  -634,  -635,  -636,  -637,  -638,  -639,  -640, // pink_concrete, gray_concrete, light_gray_concrete, cyan_concrete, purple_concrete, blue_concrete, brown_concrete, green_concrete
             -641,  -642,   237,  -709,  -710,  -711,  -712,  -713, // red_concrete, black_concrete, white_concrete_powder, orange_concrete_powder, magenta_concrete_powder, light_blue_concrete_powder, yellow_concrete_powder, lime_concrete_powder
             -714,  -715,  -716,  -717,  -718,  -719,  -720,  -721, // pink_concrete_powder, gray_concrete_powder, light_gray_concrete_powder, cyan_concrete_powder, purple_concrete_powder, blue_concrete_powder, brown_concrete_powder, green_concrete_powder
             -722,  -723,  -159,  -596,  -853,  -854,  -855,  -856, // red_concrete_powder, black_concrete_powder, turtle_egg, sniffer_egg, dead_tube_coral_block, dead_brain_coral_block, dead_bubble_coral_block, dead_fire_coral_block
             -857,  -132,  -849,  -850,  -851,  -852,  -131,  -581, // dead_horn_coral_block, tube_coral_block, brain_coral_block, bubble_coral_block, fire_coral_block, horn_coral_block, tube_coral, brain_coral
             -582,  -583,  -584,  -586,  -587,  -588,  -589,  -585, // bubble_coral, fire_coral, horn_coral, dead_brain_coral, dead_bubble_coral, dead_fire_coral, dead_horn_coral, dead_tube_coral
             -133,  -840,  -841,  -842,  -843,  -134,  -844,  -845, // tube_coral_fan, brain_coral_fan, bubble_coral_fan, fire_coral_fan, horn_coral_fan, dead_tube_coral_fan, dead_brain_coral_fan, dead_bubble_coral_fan
             -846,  -847,   -11,  -157,  -172,  -176,  -175,  -173, // dead_fire_coral_fan, dead_horn_coral_fan, blue_ice, conduit, polished_granite_stairs, smooth_red_sandstone_stairs, mossy_stone_brick_stairs, polished_diorite_stairs
             -179,  -178,    67,  -177,  -185,  -169,  -171,  -184, // mossy_cobblestone_stairs, end_stone_brick_stairs, stone_stairs, smooth_sandstone_stairs, smooth_quartz_stairs, granite_stairs, andesite_stairs, red_nether_brick_stairs
             -174,  -170,  -381,  -385,  -393,  -389,  -897,  -891, // polished_andesite_stairs, diorite_stairs, cobbled_deepslate_stairs, polished_deepslate_stairs, deepslate_brick_stairs, deepslate_tile_stairs, polished_granite_slab, smooth_red_sandstone_slab
             -166,  -895,  -888,  -162,  -889,  -898,  -896,  -893, // mossy_stone_brick_slab, polished_diorite_slab, mossy_cobblestone_slab, end_stone_brick_slab, smooth_sandstone_slab, smooth_quartz_slab, granite_slab, andesite_slab
             -890,  -892,  -894,  -380,  -384,  -392,  -388,  -165, // red_nether_brick_slab, polished_andesite_slab, diorite_slab, cobbled_deepslate_slab, polished_deepslate_slab, deepslate_brick_slab, deepslate_tile_slab, scaffolding
              376,    76,   152,   422,   532,    33,    29,   165, // redstone, redstone_torch, redstone_block, repeater, comparator, piston, sticky_piston, slime_block
             -220,   251,   537,    23,   125,  -194,  -239,    69, // honey_block, observer, hopper, dispenser, dropper, lectern, target, lever
             -312,   151,  -307,  -580,   131,   146,    46,   123, // lightning_rod, daylight_detector, sculk_sensor, calibrated_sculk_sensor, tripwire_hook, trapped_chest, tnt, redstone_lamp
               25,    77,  -296,   143,  -144,  -141,  -143,  -140, // note_block, stone_button, polished_blackstone_button, oak_button, spruce_button, birch_button, jungle_button, acacia_button
             -530,  -142,  -487,  -511,  -260,  -261,    70,  -295, // cherry_button, dark_oak_button, mangrove_button, bamboo_button, crimson_button, warped_button, stone_pressure_plate, polished_blackstone_pressure_plate
              147,   148,    72,  -154,  -151,  -153,  -150,  -538, // light_weighted_pressure_plate, heavy_weighted_pressure_plate, oak_pressure_plate, spruce_pressure_plate, birch_pressure_plate, jungle_pressure_plate, acacia_pressure_plate, cherry_pressure_plate
             -152,  -490,  -514,  -262,  -263,   375,   362,   563, // dark_oak_pressure_plate, mangrove_pressure_plate, bamboo_pressure_plate, crimson_pressure_plate, warped_pressure_plate, iron_door, oak_door, spruce_door
              564,   565,   566,  -531,   567,   649,  -517,   633, // birch_door, jungle_door, acacia_door, cherry_door, dark_oak_door, mangrove_door, bamboo_door, crimson_door
              634,    -1,    -1,    -1,    -1,    -1,    -1,    -1, // warped_door, copper_door, exposed_copper_door, weathered_copper_door, oxidized_copper_door, waxed_copper_door, waxed_exposed_copper_door, waxed_weathered_copper_door
               -1,   167,    96,  -149,  -146,  -148,  -145,  -543, // waxed_oxidized_copper_door, iron_trapdoor, oak_trapdoor, spruce_trapdoor, birch_trapdoor, jungle_trapdoor, acacia_trapdoor, cherry_trapdoor
             -147,  -496,  -520,  -246,  -247,    -1,    -1,    -1, // dark_oak_trapdoor, mangrove_trapdoor, bamboo_trapdoor, crimson_trapdoor, warped_trapdoor, copper_trapdoor, exposed_copper_trapdoor, weathered_copper_trapdoor
               -1,    -1,    -1,    -1,    -1,   107,   183,   184, // oxidized_copper_trapdoor, waxed_copper_trapdoor, waxed_exposed_copper_trapdoor, waxed_weathered_copper_trapdoor, waxed_oxidized_copper_trapdoor, oak_fence_gate, spruce_fence_gate, birch_fence_gate
              185,   187,  -533,   186,  -492,  -516,  -258,  -259, // jungle_fence_gate, acacia_fence_gate, cherry_fence_gate, dark_oak_fence_gate, mangrove_fence_gate, bamboo_fence_gate, crimson_fence_gate, warped_fence_gate
               27,    28,    66,   126,   374,   373,   392,    -1, // powered_rail, detector_rail, rail, activator_rail, saddle, minecart, chest_minecart, furnace_minecart
              535,   536,   527,   635,   574,   378,   654,   381, // tnt_minecart, hopper_minecart, carrot_on_a_stick, warped_fungus_on_a_stick, elytra, oak_boat, oak_chest_boat, spruce_boat
              657,   379,   655,   380,   656,   382,   658,   665, // spruce_chest_boat, birch_boat, birch_chest_boat, jungle_boat, jungle_chest_boat, acacia_boat, acacia_chest_boat, cherry_boat
              666,   383,   659,   651,   660,   669,   670,   252, // cherry_chest_boat, dark_oak_boat, dark_oak_chest_boat, mangrove_boat, mangrove_chest_boat, bamboo_raft, bamboo_chest_raft, structure_block
             -211,   583,   582,   301,   257,   302,   303,   304, // jigsaw, turtle_helmet, scute, flint_and_steel, apple, bow, arrow, coal
              305,   306,   523,   417,   534,   640,   516,   307, // charcoal, diamond, emerald, lapis_lazuli, quartz, amethyst_shard, raw_iron, iron_ingot
              518,   515,   517,   308,   625,   630,   310,   311, // raw_copper, copper_ingot, raw_gold, gold_ingot, netherite_ingot, netherite_scrap, wooden_sword, wooden_shovel
              312,   313,   332,   314,   315,   316,   317,   333, // wooden_pickaxe, wooden_axe, wooden_hoe, stone_sword, stone_shovel, stone_pickaxe, stone_axe, stone_hoe
              325,   326,   327,   328,   336,   309,   298,   299, // golden_sword, golden_shovel, golden_pickaxe, golden_axe, golden_hoe, iron_sword, iron_shovel, iron_pickaxe
              300,   334,   318,   319,   320,   321,   335,   620, // iron_axe, iron_hoe, diamond_sword, diamond_shovel, diamond_pickaxe, diamond_axe, diamond_hoe, netherite_sword
              621,   622,   623,   624,   323,   324,   260,   329, // netherite_shovel, netherite_pickaxe, netherite_axe, netherite_hoe, stick, bowl, mushroom_stew, string
              330,   331,   291,   337,   261,   338,   339,   340, // feather, gunpowder, wheat_seeds, wheat, bread, leather_helmet, leather_chestplate, leather_leggings
              341,   342,   343,   344,   345,   346,   347,   348, // leather_boots, chainmail_helmet, chainmail_chestplate, chainmail_leggings, chainmail_boots, iron_helmet, iron_chestplate, iron_leggings
              349,   350,   351,   352,   353,   354,   355,   356, // iron_boots, diamond_helmet, diamond_chestplate, diamond_leggings, diamond_boots, golden_helmet, golden_chestplate, golden_leggings
              357,   626,   627,   628,   629,   359,   262,   263, // golden_boots, netherite_helmet, netherite_chestplate, netherite_leggings, netherite_boots, flint, porkchop, cooked_porkchop
              360,   258,   259,   361,   586,   587,   588,   589, // painting, golden_apple, enchanted_golden_apple, oak_sign, spruce_sign, birch_sign, jungle_sign, acacia_sign
              667,   590,   650,   668,   631,   632,  -500,  -501, // cherry_sign, dark_oak_sign, mangrove_sign, bamboo_sign, crimson_sign, warped_sign, oak_hanging_sign, spruce_hanging_sign
             -502,  -503,  -504,  -534,  -505,  -508,  -522,  -506, // birch_hanging_sign, jungle_hanging_sign, acacia_hanging_sign, cherry_hanging_sign, dark_oak_hanging_sign, mangrove_hanging_sign, bamboo_hanging_sign, crimson_hanging_sign
             -507,   363,   365,   366,   371,   377,   384,   364, // warped_hanging_sign, bucket, water_bucket, lava_bucket, powder_snow_bucket, snowball, leather, milk_bucket
              370,   368,   367,   369,   372,   646,   386,   387, // pufferfish_bucket, salmon_bucket, cod_bucket, tropical_fish_bucket, axolotl_bucket, tadpole_bucket, brick, clay_ball
             -139,   389,   390,   391,   393,   394,   662,    -1, // dried_kelp_block, paper, book, slime_ball, egg, compass, recovery_compass, bundle
              395,   396,   641,   397,   264,   265,   266,   267, // fishing_rod, clock, spyglass, glowstone_dust, cod, salmon, tropical_fish, pufferfish
              268,   269,   416,   514,   415,   413,   412,   411, // cooked_cod, cooked_salmon, ink_sac, glow_ink_sac, cocoa_beans, white_dye, orange_dye, magenta_dye
              410,   409,   408,   407,   406,   405,   404,   403, // light_blue_dye, yellow_dye, lime_dye, pink_dye, gray_dye, light_gray_dye, cyan_dye, purple_dye
              402,   401,   400,   399,   398,   414,   418,   419, // blue_dye, brown_dye, green_dye, red_dye, black_dye, bone_meal, bone, sugar
              420,   421,   421,   421,   421,   421,   421,   421, // cake, white_bed, orange_bed, magenta_bed, light_blue_bed, yellow_bed, lime_bed, pink_bed
              421,   421,   421,   421,   421,   421,   421,   421, // gray_bed, light_gray_bed, cyan_bed, purple_bed, blue_bed, brown_bed, green_bed, red_bed
              421,   271,  -313,   423,   424,   272,   270,   292, // black_bed, cookie, crafter, filled_map, shears, melon_slice, dried_kelp, pumpkin_seeds
              293,   273,   274,   275,   276,   277,   425,   426, // melon_seeds, beef, cooked_beef, chicken, cooked_chicken, rotten_flesh, ender_pearl, blaze_rod
              428,   429,   294,   430,   431,   278,   432,   433, // ghast_tear, gold_nugget, nether_wart, potion, glass_bottle, spider_eye, fermented_spider_eye, blaze_powder
              434,   435,   436,   437,   438,   647,   507,   457, // magma_cream, brewing_stand, cauldron, ender_eye, glistering_melon_slice, allay_spawn_egg, axolotl_spawn_egg, bat_spawn_egg
              499,   460,   506,   493,   671,   461,   439,   485, // bee_spawn_egg, blaze_spawn_egg, breeze_spawn_egg, cat_spawn_egg, camel_spawn_egg, cave_spider_spawn_egg, chicken_spawn_egg, cod_spawn_egg
              440,   445,   489,   470,   488,   476,   512,   446, // cow_spawn_egg, creeper_spawn_egg, dolphin_spawn_egg, donkey_spawn_egg, drowned_spawn_egg, elder_guardian_spawn_egg, ender_dragon_spawn_egg, enderman_spawn_egg
              464,   480,   495,   644,   458,   509,   508,   465, // endermite_spawn_egg, evoker_spawn_egg, fox_spawn_egg, frog_spawn_egg, ghast_spawn_egg, glow_squid_spawn_egg, goat_spawn_egg, guardian_spawn_egg
              501,   462,   468,   510,   478,   459,   444,   471, // hoglin_spawn_egg, horse_spawn_egg, husk_spawn_egg, iron_golem_spawn_egg, llama_spawn_egg, magma_cube_spawn_egg, mooshroom_spawn_egg, mule_spawn_egg
              455,   494,   483,   491,   441,   502,   504,   496, // ocelot_spawn_egg, panda_spawn_egg, parrot_spawn_egg, phantom_spawn_egg, pig_spawn_egg, piglin_spawn_egg, piglin_brute_spawn_egg, pillager_spawn_egg
              477,   486,   463,   498,   487,   442,   474,   447, // polar_bear_spawn_egg, pufferfish_spawn_egg, rabbit_spawn_egg, ravager_spawn_egg, salmon_spawn_egg, sheep_spawn_egg, shulker_spawn_egg, silverfish_spawn_egg
              448,   472,   449,   505,   511,   450,   454,   466, // skeleton_spawn_egg, skeleton_horse_spawn_egg, slime_spawn_egg, sniffer_spawn_egg, snow_golem_spawn_egg, spider_spawn_egg, squid_spawn_egg, stray_spawn_egg
              500,   645,   664,   484,   490,   481,   453,   479, // strider_spawn_egg, tadpole_spawn_egg, trader_llama_spawn_egg, tropical_fish_spawn_egg, turtle_spawn_egg, vex_spawn_egg, villager_spawn_egg, vindicator_spawn_egg
              497,   648,   456,   513,   469,   443,   503,   451, // wandering_trader_spawn_egg, warden_spawn_egg, witch_spawn_egg, wither_spawn_egg, wither_skeleton_spawn_egg, wolf_spawn_egg, zoglin_spawn_egg, zombie_spawn_egg
              473,   482,   452,   519,   520,   521,   522,   524, // zombie_horse_spawn_egg, zombie_villager_spawn_egg, zombified_piglin_spawn_egg, experience_bottle, fire_charge, writable_book, written_book, item_frame
              639,   525,   279,   280,   281,   282,   423,   283, // glow_item_frame, flower_pot, carrot, potato, baked_potato, poisonous_potato, map, golden_carrot
              144,  -965,  -967,  -966,  -968,  -969,  -970,   528, // skeleton_skull, wither_skeleton_skull, player_head, zombie_head, creeper_head, dragon_head, piglin_head, nether_star
              284,   529,   530,   531,   533,   575,   559,   288, // pumpkin_pie, firework_rocket, firework_star, enchanted_book, nether_brick, prismarine_shard, prismarine_crystals, rabbit
              289,   290,   538,   539,   562,   541,   542,   543, // cooked_rabbit, rabbit_stew, rabbit_foot, rabbit_hide, armor_stand, iron_horse_armor, golden_horse_armor, diamond_horse_armor
              540,   557,   558,   573,   560,   561,   577,   577, // leather_horse_armor, lead, name_tag, command_block_minecart, mutton, cooked_mutton, white_banner, orange_banner
              577,   577,   577,   577,   577,   577,   577,   577, // magenta_banner, light_blue_banner, yellow_banner, lime_banner, pink_banner, gray_banner, light_gray_banner, cyan_banner
              577,   577,   577,   577,   577,   577,   854,   568, // purple_banner, blue_banner, brown_banner, green_banner, red_banner, black_banner, end_crystal, chorus_fruit
              569,   296,   297,   285,   295,   286,   570,   571, // popped_chorus_fruit, torchflower_seeds, pitcher_pod, beetroot, beetroot_seeds, beetroot_soup, dragon_breath, splash_potion
               -1,    -1,   572,   358,   578,   576,   579,    -1, // spectral_arrow, tipped_arrow, lingering_potion, shield, totem_of_undying, shulker_shell, iron_nugget, knowledge_book
              603,   544,   545,   546,   547,   548,   549,   550, // debug_stick, music_disc_13, music_disc_cat, music_disc_blocks, music_disc_chirp, music_disc_far, music_disc_mall, music_disc_mellohi
              551,   552,   553,   554,   555,   642,   716,   652, // music_disc_stal, music_disc_strad, music_disc_ward, music_disc_11, music_disc_wait, music_disc_otherside, music_disc_relic, music_disc_5
              636,   653,   556,   584,   580,   581,   585,   602, // music_disc_pigstep, disc_fragment_5, trident, phantom_membrane, nautilus_shell, heart_of_the_sea, crossbow, suspicious_stew
             -204,   591,   592,   593,   594,   598,   597,   643, // loom, flower_banner_pattern, creeper_banner_pattern, skull_banner_pattern, mojang_banner_pattern, globe_banner_pattern, piglin_banner_pattern, goat_horn
             -213,  -203,  -198,  -196,  -200,  -201,  -195,  -202, // composter, barrel, smoker, blast_furnace, cartography_table, fletching_table, grindstone, smithing_table
              245,  -206,  -208,  -269,   287,    -1,   601,   638, // stonecutter, bell, lantern, soul_lantern, sweet_berries, glow_berries, campfire, soul_campfire
             -230,   604,  -218,  -219,   605,  -221,  -222,  -289, // shroomlight, honeycomb, bee_nest, beehive, honey_bottle, honeycomb_block, lodestone, crying_obsidian
             -273,  -282,  -276,  -281,  -291,  -293,  -292,  -279, // blackstone, blackstone_slab, blackstone_stairs, gilded_blackstone, polished_blackstone, polished_blackstone_slab, polished_blackstone_stairs, chiseled_polished_blackstone
             -274,  -284,  -275,  -280,  -272,  -412,  -413,  -414, // polished_blackstone_bricks, polished_blackstone_brick_slab, polished_blackstone_brick_stairs, cracked_polished_blackstone_bricks, respawn_anchor, candle, white_candle, orange_candle
             -415,  -416,  -417,  -418,  -419,  -420,  -421,  -422, // magenta_candle, light_blue_candle, yellow_candle, lime_candle, pink_candle, gray_candle, light_gray_candle, cyan_candle
             -423,  -424,  -425,  -426,  -427,  -428,  -332,  -331, // purple_candle, blue_candle, brown_candle, green_candle, red_candle, black_candle, small_amethyst_bud, medium_amethyst_bud
             -330,  -329,  -308,  -471,  -470,  -469,  -468,   663, // large_amethyst_bud, amethyst_cluster, pointed_dripstone, ochre_froglight, verdant_froglight, pearlescent_froglight, frogspawn, echo_shard
              696,   697,   698,   699,   700,   701,   702,   703, // brush, netherite_upgrade_smithing_template, sentry_armor_trim_smithing_template, dune_armor_trim_smithing_template, coast_armor_trim_smithing_template, wild_armor_trim_smithing_template, ward_armor_trim_smithing_template, eye_armor_trim_smithing_template
              704,   705,   706,   707,   708,   710,   712,   709, // vex_armor_trim_smithing_template, tide_armor_trim_smithing_template, snout_armor_trim_smithing_template, rib_armor_trim_smithing_template, spire_armor_trim_smithing_template, wayfinder_armor_trim_smithing_template, shaper_armor_trim_smithing_template, silence_armor_trim_smithing_template
              711,   713,   673,   674,   675,   676,   677,   678, // raiser_armor_trim_smithing_template, host_armor_trim_smithing_template, angler_pottery_sherd, archer_pottery_sherd, arms_up_pottery_sherd, blade_pottery_sherd, brewer_pottery_sherd, burn_pottery_sherd
              679,   680,   682,   684,   685,   686,   687,   688, // danger_pottery_sherd, explorer_pottery_sherd, friend_pottery_sherd, heart_pottery_sherd, heartbreak_pottery_sherd, howl_pottery_sherd, miner_pottery_sherd, mourner_pottery_sherd
              689,   690,   692,   693,   694,   695,    -1,    -1, // plenty_pottery_sherd, prize_pottery_sherd, sheaf_pottery_sherd, shelter_pottery_sherd, skull_pottery_sherd, snort_pottery_sherd, copper_grate, exposed_copper_grate
               -1,    -1,    -1,    -1,    -1,    -1,    -1,    -1, // weathered_copper_grate, oxidized_copper_grate, waxed_copper_grate, waxed_exposed_copper_grate, waxed_weathered_copper_grate, waxed_oxidized_copper_grate, copper_bulb, exposed_copper_bulb
               -1,    -1,    -1,    -1,    -1,    -1,  -315,    -1, // weathered_copper_bulb, oxidized_copper_bulb, waxed_copper_bulb, waxed_exposed_copper_bulb, waxed_weathered_copper_bulb, waxed_oxidized_copper_bulb, trial_spawner, trial_key
    };

    /**
     * Java 765 のアイテムIDから Bedrock のネットワークIDを引く。
     * -1（未対応）・範囲外のみ石（1）を返す。
     * <b>負の network_id は有効な番号なので、そのまま返す。</b>
     */
    public static int networkId(int item765) {
        if (item765 < 0 || item765 >= NETWORK_IDS_765.length) {
            return FALLBACK_NETWORK_ID;
        }
        int id = NETWORK_IDS_765[item765];
        return id == -1 ? FALLBACK_NETWORK_ID : id;
    }

    /**
     * Java 765 のアイテムIDから、その Bedrock バージョンの
     * network_id を返す。バージョン別表（bin）が読めない場合は
     * {@link #networkId(int)} の従来表を読む。
     */
    public static int networkId(int item765, int bedrockProtocol) {
        int[][] table = tableFor(bedrockProtocol);
        if (table != null && item765 >= 0 && item765 < table.length) {
            // 表が読めた場合は、その表の -1（未対応）も尊重して石にする。
            // 従来表へのフォールバックは表そのものが読めないときだけ。
            int id = table[item765][0];
            return id == -1 ? FALLBACK_NETWORK_ID : id;
        }
        return networkId(item765);
    }

    /** Java 765 のアイテムIDの metadata（変種番号）を返す。全て 0。 */
    public static int metadata(int item765) {
        return FALLBACK_METADATA;
    }

    /**
     * Java 765 のアイテムIDから、その Bedrock バージョンの
     * metadata（変種番号）を返す。バージョン別表（bin）が読めない場合は 0。
     */
    public static int metadata(int item765, int bedrockProtocol) {
        int[][] table = tableFor(bedrockProtocol);
        if (table != null && item765 >= 0 && item765 < table.length) {
            // 表が読めた場合は、その表の -1（未対応）も尊重して 0 にする。
            return table[item765][0] == -1 ? FALLBACK_METADATA : table[item765][1];
        }
        return FALLBACK_METADATA;
    }

    /** この 765 アイテムIDが表に載っているかどうか。 */
    public static boolean isMapped(int item765) {
        return item765 >= 0 && item765 < NETWORK_IDS_765.length
                && NETWORK_IDS_765[item765] != -1;
    }

    /**
     * この 765 アイテムIDが、その Bedrock バージョンにあるかどうか。
     * バージョン別表が読める場合はそちらで判定する。
     */
    public static boolean isMapped(int item765, int bedrockProtocol) {
        int[][] table = tableFor(bedrockProtocol);
        if (table != null && item765 >= 0 && item765 < table.length) {
            return table[item765][0] != -1;
        }
        return isMapped(item765);
    }

    /**
     * Bedrock プロトコル番号に対応するバージョン別表を返す。読めなかった
     * （bin が無い・読めない）場合は null。何度呼んでも 1 回だけ読み込む。
     */
    private static int[][] tableFor(int bedrockProtocol) {
        BedrockVersion version = BedrockVersion.fromProtocol(bedrockProtocol);
        String tag = version == null ? null : version.itemTableTag();
        if (tag == null) {
            return null;
        }
        synchronized (TABLES_LOCK) {
            if (TABLES_BY_TAG.containsKey(tag)) {
                int[][] cached = TABLES_BY_TAG.get(tag);
                return cached.length == 0 ? null : cached;
            }
            int[][] table = loadTable(tag);
            TABLES_BY_TAG.put(tag, table == null ? new int[0][] : table);
            return table;
        }
    }

    /** resources から {@code bedrock_item_ids_<tag>.bin} を読む。 */
    private static int[][] loadTable(String tag) {
        String resource = "io/detergentmc/bedrock/bedrock_item_ids_" + tag + ".bin";
        try (java.io.InputStream in =
                BedrockItemIds.class.getClassLoader().getResourceAsStream(resource)) {
            if (in == null) {
                return null;
            }
            byte[] bytes = in.readAllBytes();
            if (bytes.length != COUNT * 8) {
                return null;
            }
            int[][] table = new int[COUNT][2];
            java.nio.ByteBuffer buffer =
                    java.nio.ByteBuffer.wrap(bytes).order(java.nio.ByteOrder.LITTLE_ENDIAN);
            for (int i = 0; i < COUNT; i++) {
                table[i][0] = buffer.getInt();
                table[i][1] = buffer.getInt();
            }
            return table;
        } catch (java.io.IOException e) {
            return null;
        }
    }
}
