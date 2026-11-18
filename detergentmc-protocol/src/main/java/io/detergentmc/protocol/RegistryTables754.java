package io.detergentmc.protocol;

/**
 * 754（1.16.5）のレジストリ対応表（区間＋上書きの生の表）。
 *
 * <p>755（1.17）との隣接表だけを置く。754 は最古バージョンのため
 * 離れた組み合わせの合成表は作らない（763〜775 の組が合成表を持つ
 * のとは違い、759〜762 の組は隣接表の連鎖だけを使う設計のため。
 * 754↔763〜775 は 754→755→756→757→759→760→761→762→763→合成表の連鎖で到達する）。
 * 755→754 の順向き表（*_754_*）と 754→755 の反転表（*_754_TO_755_*）
 * を両方置く。反転表は名前の真値（同名のものを優先）で作ってあり、
 * {@link RegistryRemapper#invert} の first-wins と違う箇所は
 * tools/maps754-corrections.json の補正（UP_754_CORRECTIONS）で
 * 埋める（補正はブロック状態 46 件・アイテム 5 件・音 35 件。
 * レールの waterlogged・銅系の代替などが first-wins と食い違う）。
 *
 * <p>バイオームの id は 1.16.5 が 0〜173（79 件）、1.17 が 0〜175（81 件）の
 * 非連番（旧来の数値バイオームID）。0〜173 は同じ id・同じ名前。
 * 順向き表（BIOME_754）は 755 側 176 件（id 0〜175。dripstone_caves・
 * lush_caves は平原（754 id 1）へ落とす）、反転表
 * （BIOME_754_TO_755）は 754 側 174 件（id 0〜173。隙間の id は
 * 平原（755 id 1）で埋める）。ワイヤ上の id がこの通りであることは
 * Join Game の dimensionCodec との突き合わせで確認する
 * （tools/gen-754-tables.py の §11.6 相当の Join Game codec 確認を参照。
 * 755 では実バニラ 1.17 の生キャプチャで確認済み）。
 *
 * <p>{@link RegistryRemapper} から分離した。理由はバイトコードの制限：
 * 754 の表を RegistryRemapper に置くと静的初期化子（{@code <clinit>}）
 * が 64KB を超えてコンパイルできなくなる（"code too large"）ため。
 * このクラスの表は {@link RegistryRemapper} から static import で
 * 同じ名前のまま参照する（展開済み表 {@code *_754_MAP} やアクセサの
 * 書き方は変わらない）。
 *
 * <p>表の内容と検証は {@code detergentmc-protocol/tools/} を参照：
 * 生成は {@code gen-754-tables.py}、検証は {@code verify-754-tables.py}。
 */
final class RegistryTables754 {

    private RegistryTables754() {
    }

    static final int[][] ITEM_754_RANGES = {
            {0, 7, 0}, {14, 17, -6}, {19, 39, -7}, {101, 138, -64}, {141, 143, -66}, {146, 148, -65},
            {149, 151, -61}, {154, 156, -63}, {157, 185, -62}, {187, 197, -63}, {204, 232, -66}, {233, 245, -65},
            {246, 249, -63}, {251, 266, -49}, {268, 272, -50}, {274, 275, -51}, {276, 281, -42}, {283, 286, -43},
            {290, 299, -48}, {301, 314, -41}, {317, 341, -38}, {346, 348, -32}, {349, 353, -25}, {354, 370, -23},
            {372, 389, -23}, {390, 392, -22}, {393, 450, -21}, {451, 562, -20}, {567, 579, -24}, {588, 589, -22},
            {611, 618, -306}, {621, 622, -303}, {623, 630, -432}, {631, 639, -74}, {641, 648, -415}, {649, 656, -397},
            {657, 658, -572}, {663, 664, 17}, {665, 666, 186}, {667, 668, 175}, {671, 675, 228}, {676, 686, -108},
            {694, 695, -115}, {696, 778, -116}, {781, 786, -113}, {788, 793, -114}, {794, 795, -112}, {797, 798, -113},
            {800, 807, -114}, {808, 809, -115}, {810, 872, -114}, {874, 891, -115}, {894, 943, -117}, {945, 958, -117},
            {959, 964, -115}, {965, 1009, -112}, {1010, 1047, -106}, {1048, 1053, -105}, {1054, 1061, -106}, {1062, 1063, -105},
            {1064, 1077, -102},
    };

    static final int[][] ITEM_754_OVERRIDES = {
            {8, 1}, {9, 14}, {10, 7}, {11, 325}, {12, 1}, {13, 1}, {18, 9}, {40, 35},
            {41, 35}, {42, 34}, {43, 34}, {44, 34}, {45, 34}, {46, 33}, {47, 33}, {48, 200},
            {49, 200}, {50, 276}, {51, 276}, {52, 78}, {53, 78}, {54, 181}, {55, 181}, {56, 36},
            {57, 322}, {58, 960}, {59, 367}, {60, 137}, {61, 137}, {62, 136}, {63, 325}, {64, 325},
            {65, 137}, {66, 137}, {67, 136}, {68, 182}, {69, 959}, {70, 137}, {71, 137}, {72, 137},
            {73, 137}, {74, 137}, {75, 137}, {76, 137}, {77, 137}, {78, 137}, {79, 137}, {80, 137},
            {81, 137}, {82, 137}, {83, 137}, {84, 137}, {85, 137}, {86, 137}, {87, 137}, {88, 137},
            {89, 137}, {90, 137}, {91, 137}, {92, 137}, {93, 137}, {94, 137}, {95, 137}, {96, 137},
            {97, 137}, {98, 137}, {99, 137}, {100, 137}, {139, 69}, {140, 69}, {144, 77}, {145, 79},
            {152, 23}, {153, 23}, {186, 373}, {198, 363}, {199, 8}, {200, 91}, {201, 263}, {202, 263},
            {203, 135}, {250, 188}, {267, 225}, {273, 221}, {282, 234}, {287, 240}, {288, 242}, {289, 240},
            {300, 251}, {315, 275}, {316, 277}, {342, 287}, {343, 294}, {344, 294}, {345, 294}, {371, 171},
            {563, 188}, {564, 541}, {565, 261}, {566, 261}, {580, 151}, {581, 554}, {582, 153}, {583, 153},
            {584, 556}, {585, 665}, {586, 201}, {587, 321}, {590, 94}, {591, 87}, {592, 371}, {593, 956},
            {594, 430}, {595, 323}, {596, 80}, {597, 330}, {598, 942}, {599, 961}, {600, 189}, {601, 172},
            {602, 320}, {603, 320}, {604, 278}, {605, 317}, {606, 167}, {607, 274}, {608, 84}, {609, 304},
            {610, 313}, {619, 190}, {620, 199}, {640, 348}, {659, 187}, {660, 329}, {661, 664}, {662, 663},
            {669, 898}, {670, 667}, {687, 827}, {688, 695}, {689, 850}, {690, 850}, {691, 579}, {692, 579},
            {693, 579}, {779, 661}, {780, 666}, {787, 661}, {796, 180}, {799, 613}, {873, 809}, {892, 805},
            {893, 798}, {944, 828}, {1078, 171}, {1079, 171}, {1080, 171}, {1081, 171}, {1082, 171}, {1083, 171},
            {1084, 171}, {1085, 171}, {1086, 171}, {1087, 171}, {1088, 171}, {1089, 171}, {1090, 171}, {1091, 171},
            {1092, 171}, {1093, 171}, {1094, 171}, {1095, 325}, {1096, 325}, {1097, 325}, {1098, 325}, {1099, 1},
    };
    // ITEM_754: 755 entries=1100 ranges=61 overrides=176

    static final int[][] ENTITY_TYPE_754_RANGES = {
            {0, 2, 0}, {4, 31, -1}, {35, 48, -4}, {50, 112, -5},
    };

    static final int[][] ENTITY_TYPE_754_OVERRIDES = {
            {3, 90}, {32, 38}, {33, 81}, {34, 69}, {49, 1},
    };
    // ENTITY_TYPE_754: 755 entries=113 ranges=4 overrides=5

    static final int[][] SOUND_754_RANGES = {
            {0, 21, 0}, {24, 27, 291}, {28, 32, 286}, {33, 62, -11}, {72, 76, 247}, {77, 81, 242},
            {82, 118, -30}, {119, 123, 200}, {124, 135, -35}, {137, 149, -36}, {150, 152, -37}, {155, 157, -39},
            {169, 172, 790}, {173, 181, -53}, {182, 186, 137}, {188, 217, -59}, {223, 245, -64}, {247, 249, 572},
            {252, 254, 567}, {257, 259, 562}, {261, 279, -79}, {297, 305, -96}, {307, 371, -97}, {372, 376, -53},
            {377, 420, -102}, {422, 426, -27}, {427, 430, 385}, {431, 432, 286}, {439, 440, 278}, {448, 466, -129},
            {467, 471, -148}, {472, 486, -134}, {488, 519, -135}, {521, 547, -136}, {550, 580, -138}, {583, 590, -140},
            {591, 597, -141}, {598, 602, -279}, {603, 607, -284}, {608, 800, -151}, {802, 814, -152}, {816, 818, 3},
            {820, 822, -157}, {823, 824, -27}, {825, 827, -22}, {828, 864, -162}, {865, 869, -546}, {870, 883, -167},
            {887, 889, -68}, {891, 920, -174}, {921, 943, -175}, {946, 950, -627}, {951, 961, -182}, {962, 966, -643},
            {967, 998, -187}, {1001, 1039, -189}, {1045, 1093, -194}, {1094, 1097, -775}, {1098, 1189, -198},
    };

    static final int[][] SOUND_754_OVERRIDES = {
            {22, 314}, {23, 543}, {63, 51}, {64, 51}, {65, 849}, {66, 848}, {67, 850}, {68, 847},
            {69, 847}, {70, 270}, {71, 270}, {136, 322}, {153, 113}, {154, 116}, {158, 116}, {159, 954},
            {160, 816}, {161, 824}, {162, 821}, {163, 820}, {164, 819}, {165, 119}, {166, 268}, {167, 958},
            {168, 269}, {187, 831}, {218, 443}, {219, 449}, {220, 446}, {221, 445}, {222, 444}, {246, 816},
            {250, 824}, {251, 816}, {255, 824}, {256, 816}, {260, 824}, {280, 816}, {281, 824}, {282, 821},
            {283, 820}, {284, 819}, {285, 816}, {286, 824}, {287, 821}, {288, 820}, {289, 819}, {290, 819},
            {291, 412}, {292, 912}, {293, 412}, {294, 912}, {295, 323}, {296, 323}, {306, 322}, {421, 322},
            {433, 717}, {434, 719}, {435, 717}, {436, 167}, {437, 717}, {438, 719}, {441, 717}, {442, 719},
            {443, 717}, {444, 167}, {445, 717}, {446, 719}, {447, 721}, {487, 256}, {520, 322}, {548, 314},
            {549, 317}, {581, 314}, {582, 317}, {801, 648}, {815, 816}, {819, 824}, {884, 951}, {885, 950},
            {886, 816}, {890, 824}, {944, 314}, {945, 317}, {999, 256}, {1000, 256}, {1040, 816}, {1041, 824},
            {1042, 821}, {1043, 820}, {1044, 819},
    };
    // SOUND_754: 755 entries=1190 ranges=59 overrides=91

    static final int[][] PARTICLE_754_RANGES = {
            {0, 2, 0}, {4, 15, -1}, {16, 36, -2}, {38, 66, -3}, {68, 70, -4}, {72, 76, -5},
            {79, 80, -70}, {81, 82, -69},
    };

    static final int[][] PARTICLE_754_OVERRIDES = {
            {3, 19}, {37, 40}, {67, 30}, {71, 30}, {77, 26}, {78, 71}, {83, 47}, {84, 18},
            {85, 30}, {86, 44}, {87, 6}, {88, 6},
    };
    // PARTICLE_754: 755 entries=89 ranges=8 overrides=12

    static final int[][] BLOCK_754_RANGES = {
            {0, 31, 0}, {32, 33, -1}, {34, 35, -2}, {36, 67, -3}, {70, 73, -5}, {74, 155, -6},
            {156, 187, -7}, {188, 257, -8}, {258, 270, -9}, {273, 281, -12}, {282, 391, -13}, {393, 776, -14},
    };

    static final int[][] BLOCK_754_OVERRIDES = {
            {68, 59}, {69, 59}, {271, 261}, {272, 261}, {392, 141}, {777, 141}, {778, 141}, {779, 141},
            {780, 141}, {781, 141}, {782, 141}, {783, 141}, {784, 141}, {785, 141}, {786, 141}, {787, 141},
            {788, 141}, {789, 141}, {790, 141}, {791, 141}, {792, 141}, {793, 141}, {794, 204}, {795, 204},
            {796, 204}, {797, 204}, {798, 204}, {799, 204}, {800, 204}, {801, 204}, {802, 204}, {803, 204},
            {804, 204}, {805, 204}, {806, 204}, {807, 204}, {808, 204}, {809, 204}, {810, 204}, {811, 337},
            {812, 337}, {813, 337}, {814, 337}, {815, 337}, {816, 337}, {817, 1}, {818, 337}, {819, 67},
            {820, 186}, {821, 333}, {822, 135}, {823, 135}, {824, 135}, {825, 135}, {826, 32}, {827, 32},
            {828, 135}, {829, 135}, {830, 135}, {831, 135}, {832, 135}, {833, 135}, {834, 135}, {835, 135},
            {836, 135}, {837, 135}, {838, 135}, {839, 135}, {840, 135}, {841, 135}, {842, 135}, {843, 135},
            {844, 135}, {845, 135}, {846, 135}, {847, 135}, {848, 135}, {849, 135}, {850, 135}, {851, 135},
            {852, 135}, {853, 135}, {854, 135}, {855, 135}, {856, 490}, {857, 1}, {858, 1}, {859, 249},
            {860, 249}, {861, 410}, {862, 19}, {863, 19}, {864, 404}, {865, 8}, {866, 254}, {867, 254},
            {868, 254}, {869, 97}, {870, 9}, {871, 1}, {872, 12}, {873, 164}, {874, 463}, {875, 279},
            {876, 7}, {877, 639}, {878, 652}, {879, 659}, {880, 228}, {881, 252}, {882, 465}, {883, 659},
            {884, 228}, {885, 252}, {886, 465}, {887, 659}, {888, 231}, {889, 230}, {890, 230}, {891, 232},
            {892, 196}, {893, 135}, {894, 135}, {895, 134}, {896, 282}, {897, 282},
    };
    // BLOCK_754: 755 entries=898 ranges=12 overrides=126

    static final int[][] MENU_754_RANGES = {
            {0, 23, 0},
    };

    static final int[][] MENU_754_OVERRIDES = {
    };
    // MENU_754: 755 entries=24 ranges=1 overrides=0

    static final int[][] ITEM_REVERSE_754_RANGES = {
            {0, 7, 0}, {8, 11, 6}, {12, 32, 7}, {37, 74, 64}, {75, 77, 66}, {81, 83, 65},
            {85, 86, 572}, {88, 90, 61}, {91, 93, 63}, {95, 123, 62}, {124, 134, 63}, {138, 166, 66},
            {168, 180, 65}, {183, 186, 63}, {191, 198, 432}, {202, 217, 49}, {218, 222, 50}, {223, 224, 51},
            {226, 233, 415}, {234, 239, 42}, {240, 243, 43}, {244, 251, 48}, {252, 259, 397}, {260, 273, 41},
            {279, 303, 38}, {305, 312, 306}, {314, 316, 32}, {318, 319, 303}, {324, 328, 25}, {331, 347, 23},
            {349, 366, 23}, {368, 370, 22}, {372, 429, 21}, {431, 542, 20}, {543, 555, 24}, {557, 565, 74},
            {566, 567, 22}, {568, 578, 108}, {580, 662, 116}, {668, 673, 113}, {674, 679, 114}, {680, 681, -17},
            {682, 683, 112}, {684, 685, 113}, {686, 693, 114}, {696, 758, 114}, {759, 776, 115}, {777, 826, 117},
            {829, 841, 117}, {842, 843, -175}, {844, 849, 115}, {851, 852, -186}, {853, 897, 112}, {899, 903, -228},
            {904, 941, 106}, {943, 948, 105}, {949, 955, 106}, {957, 958, 105}, {962, 975, 102},
    };

    static final int[][] ITEM_REVERSE_754_OVERRIDES = {
            {33, 46}, {34, 42}, {35, 40}, {36, 56}, {78, 52}, {79, 145}, {80, 596}, {84, 608},
            {87, 591}, {94, 590}, {135, 203}, {136, 67}, {137, 65}, {167, 606}, {181, 54}, {182, 68},
            {187, 659}, {188, 250}, {189, 600}, {190, 619}, {199, 620}, {200, 48}, {201, 586}, {225, 267},
            {274, 607}, {275, 315}, {276, 50}, {277, 316}, {278, 604}, {304, 609}, {313, 610}, {317, 605},
            {320, 602}, {321, 587}, {322, 57}, {323, 595}, {329, 660}, {330, 597}, {348, 640}, {367, 59},
            {371, 592}, {430, 594}, {556, 584}, {579, 692}, {663, 662}, {664, 661}, {665, 585}, {666, 780},
            {667, 670}, {694, 809}, {695, 688}, {827, 687}, {828, 944}, {850, 689}, {898, 669}, {942, 598},
            {956, 593}, {959, 69}, {960, 58}, {961, 599},
    };
    // ITEM_REVERSE_754: 754 entries=976 ranges=59 overrides=60

    static final int[][] BLOCK_STATE_754_TO_755_RANGES = {
            {0, 69, 0}, {72, 228, 3}, {229, 232, 31}, {233, 1304, 32}, {1328, 3354, 56}, {3355, 3644, 57},
            {3654, 3886, 67}, {3887, 4823, 69}, {4824, 5148, 197}, {5149, 5254, 201}, {5255, 6826, 202}, {6838, 7540, 214},
            {7541, 17111, 246},
    };

    static final int[][] BLOCK_STATE_754_TO_755_OVERRIDES = {
            {70, 71}, {71, 73}, {1305, 1338}, {1306, 1340}, {1307, 1342}, {1308, 1344}, {1309, 1346}, {1310, 1348},
            {1311, 1350}, {1312, 1352}, {1313, 1354}, {1314, 1356}, {1315, 1358}, {1316, 1360}, {1317, 1362}, {1318, 1364},
            {1319, 1366}, {1320, 1368}, {1321, 1370}, {1322, 1372}, {1323, 1374}, {1324, 1376}, {1325, 1378}, {1326, 1380},
            {1327, 1382}, {3645, 3703}, {3646, 3705}, {3647, 3707}, {3648, 3709}, {3649, 3711}, {3650, 3713}, {3651, 3715},
            {3652, 3717}, {3653, 3719}, {6827, 7030}, {6828, 7032}, {6829, 7034}, {6830, 7036}, {6831, 7038}, {6832, 7040},
            {6833, 7042}, {6834, 7044}, {6835, 7046}, {6836, 7048}, {6837, 7050},
    };
    // BLOCK_STATE_754_TO_755: 754 entries=17112 ranges=13 overrides=45

    static final int[][] ITEM_754_TO_755_RANGES = {
            {0, 7, 0}, {8, 11, 6}, {12, 32, 7}, {37, 74, 64}, {75, 77, 66}, {81, 83, 65},
            {85, 86, 572}, {88, 90, 61}, {91, 93, 63}, {95, 123, 62}, {124, 134, 63}, {138, 166, 66},
            {168, 180, 65}, {183, 186, 63}, {191, 198, 432}, {202, 217, 49}, {218, 222, 50}, {223, 224, 51},
            {226, 233, 415}, {234, 239, 42}, {240, 243, 43}, {244, 251, 48}, {252, 259, 397}, {260, 273, 41},
            {279, 303, 38}, {305, 312, 306}, {314, 316, 32}, {318, 319, 303}, {324, 328, 25}, {331, 347, 23},
            {349, 366, 23}, {368, 370, 22}, {372, 429, 21}, {431, 542, 20}, {543, 555, 24}, {557, 565, 74},
            {566, 567, 22}, {568, 578, 108}, {580, 662, 116}, {668, 673, 113}, {674, 679, 114}, {680, 681, -17},
            {682, 683, 112}, {684, 685, 113}, {686, 693, 114}, {696, 758, 114}, {759, 776, 115}, {777, 826, 117},
            {829, 841, 117}, {842, 843, -175}, {844, 849, 115}, {851, 852, -186}, {853, 897, 112}, {899, 903, -228},
            {904, 941, 106}, {943, 948, 105}, {949, 955, 106}, {957, 958, 105}, {962, 975, 102},
    };

    static final int[][] ITEM_754_TO_755_OVERRIDES = {
            {33, 46}, {34, 42}, {35, 40}, {36, 56}, {78, 52}, {79, 145}, {80, 596}, {84, 608},
            {87, 591}, {94, 590}, {135, 203}, {136, 67}, {137, 65}, {167, 606}, {181, 54}, {182, 68},
            {187, 659}, {188, 250}, {189, 600}, {190, 619}, {199, 620}, {200, 48}, {201, 586}, {225, 267},
            {274, 607}, {275, 315}, {276, 50}, {277, 316}, {278, 604}, {304, 609}, {313, 610}, {317, 605},
            {320, 602}, {321, 587}, {322, 57}, {323, 595}, {329, 660}, {330, 597}, {348, 640}, {367, 59},
            {371, 592}, {430, 594}, {556, 584}, {579, 692}, {663, 662}, {664, 661}, {665, 585}, {666, 780},
            {667, 670}, {694, 809}, {695, 688}, {827, 687}, {828, 944}, {850, 689}, {898, 669}, {942, 598},
            {956, 593}, {959, 69}, {960, 58}, {961, 599},
    };
    // ITEM_754_TO_755: 754 entries=976 ranges=59 overrides=60

    static final int[][] ENTITY_TYPE_754_TO_755_RANGES = {
            {0, 2, 0}, {3, 30, 1}, {31, 44, 4}, {45, 107, 5},
    };

    static final int[][] ENTITY_TYPE_754_TO_755_OVERRIDES = {
    };
    // ENTITY_TYPE_754_TO_755: 754 entries=108 ranges=4 overrides=0

    static final int[][] SOUND_754_TO_755_RANGES = {
            {0, 21, 0}, {22, 51, 11}, {52, 88, 30}, {89, 100, 35}, {101, 113, 36}, {114, 115, 37},
            {117, 118, 39}, {120, 128, 53}, {129, 158, 59}, {159, 181, 64}, {182, 200, 79}, {201, 209, 96},
            {210, 274, 97}, {275, 318, 102}, {319, 337, 129}, {338, 352, 134}, {353, 384, 135}, {385, 411, 136},
            {412, 442, 138}, {443, 449, 140}, {450, 456, 141}, {457, 649, 151}, {650, 662, 152}, {663, 665, 157},
            {666, 702, 162}, {703, 716, 167}, {717, 746, 174}, {747, 768, 175}, {769, 779, 182}, {780, 811, 187},
            {812, 850, 189}, {851, 899, 194}, {900, 991, 198},
    };

    static final int[][] SOUND_754_TO_755_OVERRIDES = {
            {116, 154}, {119, 165},
    };
    // SOUND_754_TO_755: 754 entries=992 ranges=33 overrides=2

    static final int[][] PARTICLE_754_TO_755_RANGES = {
            {0, 2, 0}, {3, 14, 1}, {15, 34, 2}, {35, 63, 3}, {64, 66, 4}, {67, 71, 5},
    };

    static final int[][] PARTICLE_754_TO_755_OVERRIDES = {
    };
    // PARTICLE_754_TO_755: 754 entries=72 ranges=6 overrides=0

    static final int[][] BLOCK_754_TO_755_RANGES = {
            {0, 31, 0}, {34, 64, 3}, {65, 68, 5}, {69, 149, 6}, {150, 180, 7}, {181, 249, 8},
            {250, 261, 9}, {262, 269, 12}, {270, 378, 13}, {379, 762, 14},
    };

    static final int[][] BLOCK_754_TO_755_OVERRIDES = {
            {32, 33}, {33, 35},
    };
    // BLOCK_754_TO_755: 754 entries=763 ranges=10 overrides=2

    static final int[][] MENU_754_TO_755_RANGES = {
            {0, 23, 0},
    };

    static final int[][] MENU_754_TO_755_OVERRIDES = {
    };
    // MENU_754_TO_755: 754 entries=24 ranges=1 overrides=0

    static final String[][] SOUND_NAME_FALLBACKS_754 = {
            {"block.amethyst_block.break", "block.glass.break"},
            {"block.amethyst_block.chime", "block.note_block.pling"},
            {"block.amethyst_block.fall", "block.glass.fall"},
            {"block.amethyst_block.hit", "block.glass.hit"},
            {"block.amethyst_block.place", "block.glass.place"},
            {"block.amethyst_block.step", "block.glass.step"},
            {"block.amethyst_cluster.break", "block.glass.break"},
            {"block.amethyst_cluster.fall", "block.glass.fall"},
            {"block.amethyst_cluster.hit", "block.glass.hit"},
            {"block.amethyst_cluster.place", "block.glass.place"},
            {"block.amethyst_cluster.step", "block.glass.step"},
            {"block.azalea.break", "block.grass.break"},
            {"block.azalea.fall", "block.grass.fall"},
            {"block.azalea.hit", "block.grass.hit"},
            {"block.azalea.place", "block.grass.place"},
            {"block.azalea.step", "block.grass.step"},
            {"block.azalea_leaves.break", "block.grass.break"},
            {"block.azalea_leaves.fall", "block.grass.fall"},
            {"block.azalea_leaves.hit", "block.grass.hit"},
            {"block.azalea_leaves.place", "block.grass.place"},
            {"block.azalea_leaves.step", "block.grass.step"},
            {"block.big_dripleaf.break", "block.grass.break"},
            {"block.big_dripleaf.fall", "block.grass.fall"},
            {"block.big_dripleaf.hit", "block.grass.hit"},
            {"block.big_dripleaf.place", "block.grass.place"},
            {"block.big_dripleaf.step", "block.grass.step"},
            {"block.big_dripleaf.tilt_down", "block.grass.step"},
            {"block.big_dripleaf.tilt_up", "block.grass.step"},
            {"block.cake.add_candle", "block.wood.place"},
            {"block.calcite.break", "block.stone.break"},
            {"block.calcite.fall", "block.stone.fall"},
            {"block.calcite.hit", "block.stone.hit"},
            {"block.calcite.place", "block.stone.place"},
            {"block.calcite.step", "block.stone.step"},
            {"block.candle.ambient", "block.fire.ambient"},
            {"block.candle.break", "block.wool.break"},
            {"block.candle.extinguish", "block.fire.extinguish"},
            {"block.candle.fall", "block.wool.fall"},
            {"block.candle.hit", "block.wool.hit"},
            {"block.candle.place", "block.wool.place"},
            {"block.candle.step", "block.wool.step"},
            {"block.cave_vines.break", "block.grass.break"},
            {"block.cave_vines.fall", "block.grass.fall"},
            {"block.cave_vines.hit", "block.grass.hit"},
            {"block.cave_vines.pick_berries", "item.sweet_berries.pick_from_bush"},
            {"block.cave_vines.place", "block.grass.place"},
            {"block.cave_vines.step", "block.grass.step"},
            {"block.copper.break", "block.metal.break"},
            {"block.copper.fall", "block.metal.fall"},
            {"block.copper.hit", "block.metal.hit"},
            {"block.copper.place", "block.metal.place"},
            {"block.copper.step", "block.metal.step"},
            {"block.deepslate.break", "block.stone.break"},
            {"block.deepslate.fall", "block.stone.fall"},
            {"block.deepslate.hit", "block.stone.hit"},
            {"block.deepslate.place", "block.stone.place"},
            {"block.deepslate.step", "block.stone.step"},
            {"block.deepslate_bricks.break", "block.stone.break"},
            {"block.deepslate_bricks.fall", "block.stone.fall"},
            {"block.deepslate_bricks.hit", "block.stone.hit"},
            {"block.deepslate_bricks.place", "block.stone.place"},
            {"block.deepslate_bricks.step", "block.stone.step"},
            {"block.deepslate_tiles.break", "block.stone.break"},
            {"block.deepslate_tiles.fall", "block.stone.fall"},
            {"block.deepslate_tiles.hit", "block.stone.hit"},
            {"block.deepslate_tiles.place", "block.stone.place"},
            {"block.deepslate_tiles.step", "block.stone.step"},
            {"block.dripstone_block.break", "block.stone.break"},
            {"block.dripstone_block.fall", "block.stone.fall"},
            {"block.dripstone_block.hit", "block.stone.hit"},
            {"block.dripstone_block.place", "block.stone.place"},
            {"block.dripstone_block.step", "block.stone.step"},
            {"block.flowering_azalea.break", "block.grass.break"},
            {"block.flowering_azalea.fall", "block.grass.fall"},
            {"block.flowering_azalea.hit", "block.grass.hit"},
            {"block.flowering_azalea.place", "block.grass.place"},
            {"block.flowering_azalea.step", "block.grass.step"},
            {"block.hanging_roots.break", "block.grass.break"},
            {"block.hanging_roots.fall", "block.grass.fall"},
            {"block.hanging_roots.hit", "block.grass.hit"},
            {"block.hanging_roots.place", "block.grass.place"},
            {"block.hanging_roots.step", "block.grass.step"},
            {"block.large_amethyst_bud.break", "block.glass.break"},
            {"block.large_amethyst_bud.place", "block.glass.place"},
            {"block.medium_amethyst_bud.break", "block.glass.break"},
            {"block.medium_amethyst_bud.place", "block.glass.place"},
            {"block.moss.break", "block.grass.break"},
            {"block.moss.fall", "block.grass.fall"},
            {"block.moss.hit", "block.grass.hit"},
            {"block.moss.place", "block.grass.place"},
            {"block.moss.step", "block.grass.step"},
            {"block.moss_carpet.break", "block.grass.break"},
            {"block.moss_carpet.fall", "block.grass.fall"},
            {"block.moss_carpet.hit", "block.grass.hit"},
            {"block.moss_carpet.place", "block.grass.place"},
            {"block.moss_carpet.step", "block.grass.step"},
            {"block.pointed_dripstone.break", "block.stone.break"},
            {"block.pointed_dripstone.drip_lava", "block.lava.ambient"},
            {"block.pointed_dripstone.drip_lava_into_cauldron", "block.lava.ambient"},
            {"block.pointed_dripstone.drip_water", "block.water.ambient"},
            {"block.pointed_dripstone.drip_water_into_cauldron", "block.water.ambient"},
            {"block.pointed_dripstone.fall", "block.stone.fall"},
            {"block.pointed_dripstone.hit", "block.stone.hit"},
            {"block.pointed_dripstone.land", "block.stone.fall"},
            {"block.pointed_dripstone.place", "block.stone.place"},
            {"block.pointed_dripstone.step", "block.stone.step"},
            {"block.polished_deepslate.break", "block.stone.break"},
            {"block.polished_deepslate.fall", "block.stone.fall"},
            {"block.polished_deepslate.hit", "block.stone.hit"},
            {"block.polished_deepslate.place", "block.stone.place"},
            {"block.polished_deepslate.step", "block.stone.step"},
            {"block.powder_snow.break", "block.snow.break"},
            {"block.powder_snow.fall", "block.snow.fall"},
            {"block.powder_snow.hit", "block.snow.hit"},
            {"block.powder_snow.place", "block.snow.place"},
            {"block.powder_snow.step", "block.snow.step"},
            {"block.rooted_dirt.break", "block.grass.break"},
            {"block.rooted_dirt.fall", "block.grass.fall"},
            {"block.rooted_dirt.hit", "block.grass.hit"},
            {"block.rooted_dirt.place", "block.grass.place"},
            {"block.rooted_dirt.step", "block.grass.step"},
            {"block.sculk_sensor.break", "block.stone.break"},
            {"block.sculk_sensor.clicking", "block.wooden_button.click_on"},
            {"block.sculk_sensor.clicking_stop", "block.wooden_button.click_off"},
            {"block.sculk_sensor.fall", "block.stone.fall"},
            {"block.sculk_sensor.hit", "block.stone.hit"},
            {"block.sculk_sensor.place", "block.stone.place"},
            {"block.sculk_sensor.step", "block.stone.step"},
            {"block.small_amethyst_bud.break", "block.glass.break"},
            {"block.small_amethyst_bud.place", "block.glass.place"},
            {"block.small_dripleaf.break", "block.grass.break"},
            {"block.small_dripleaf.fall", "block.grass.fall"},
            {"block.small_dripleaf.hit", "block.grass.hit"},
            {"block.small_dripleaf.place", "block.grass.place"},
            {"block.small_dripleaf.step", "block.grass.step"},
            {"block.spore_blossom.break", "block.grass.break"},
            {"block.spore_blossom.fall", "block.grass.fall"},
            {"block.spore_blossom.hit", "block.grass.hit"},
            {"block.spore_blossom.place", "block.grass.place"},
            {"block.spore_blossom.step", "block.grass.step"},
            {"block.sweet_berry_bush.pick_berries", "item.sweet_berries.pick_from_bush"},
            {"block.tuff.break", "block.stone.break"},
            {"block.tuff.fall", "block.stone.fall"},
            {"block.tuff.hit", "block.stone.hit"},
            {"block.tuff.place", "block.stone.place"},
            {"block.tuff.step", "block.stone.step"},
            {"block.vine.break", "block.grass.break"},
            {"block.vine.fall", "block.grass.fall"},
            {"block.vine.hit", "block.grass.hit"},
            {"block.vine.place", "block.grass.place"},
            {"entity.axolotl.attack", "entity.tropical_fish.flop"},
            {"entity.axolotl.death", "entity.tropical_fish.death"},
            {"entity.axolotl.hurt", "entity.tropical_fish.hurt"},
            {"entity.axolotl.idle_air", "entity.tropical_fish.ambient"},
            {"entity.axolotl.idle_water", "entity.tropical_fish.ambient"},
            {"entity.axolotl.splash", "entity.fish.swim"},
            {"entity.axolotl.swim", "entity.fish.swim"},
            {"entity.glow_item_frame.add_item", "entity.item_frame.add_item"},
            {"entity.glow_item_frame.break", "entity.item_frame.break"},
            {"entity.glow_item_frame.place", "entity.item_frame.place"},
            {"entity.glow_item_frame.remove_item", "entity.item_frame.remove_item"},
            {"entity.glow_item_frame.rotate_item", "entity.item_frame.rotate_item"},
            {"entity.glow_squid.ambient", "entity.squid.ambient"},
            {"entity.glow_squid.death", "entity.squid.death"},
            {"entity.glow_squid.hurt", "entity.squid.hurt"},
            {"entity.glow_squid.squirt", "entity.squid.squirt"},
            {"entity.goat.ambient", "entity.sheep.ambient"},
            {"entity.goat.death", "entity.sheep.death"},
            {"entity.goat.eat", "entity.sheep.ambient"},
            {"entity.goat.hurt", "entity.sheep.hurt"},
            {"entity.goat.long_jump", "entity.sheep.ambient"},
            {"entity.goat.milk", "entity.cow.milk"},
            {"entity.goat.prepare_ram", "entity.sheep.ambient"},
            {"entity.goat.ram_impact", "entity.sheep.hurt"},
            {"entity.goat.screaming.ambient", "entity.sheep.ambient"},
            {"entity.goat.screaming.death", "entity.sheep.death"},
            {"entity.goat.screaming.eat", "entity.sheep.ambient"},
            {"entity.goat.screaming.hurt", "entity.sheep.hurt"},
            {"entity.goat.screaming.long_jump", "entity.sheep.ambient"},
            {"entity.goat.screaming.milk", "entity.cow.milk"},
            {"entity.goat.screaming.prepare_ram", "entity.sheep.ambient"},
            {"entity.goat.screaming.ram_impact", "entity.sheep.hurt"},
            {"entity.goat.step", "entity.sheep.step"},
            {"entity.minecart.inside.underwater", "entity.minecart.inside"},
            {"entity.player.hurt_freeze", "entity.player.hurt"},
            {"entity.skeleton.converted_to_stray", "entity.skeleton.ambient"},
            {"item.axe.scrape", "item.axe.strip"},
            {"item.axe.wax_off", "item.axe.strip"},
            {"item.bone_meal.use", "block.grass.place"},
            {"item.bucket.empty_axolotl", "item.bucket.empty"},
            {"item.bucket.empty_powder_snow", "item.bucket.empty"},
            {"item.bucket.fill_axolotl", "item.bucket.fill"},
            {"item.bucket.fill_powder_snow", "item.bucket.fill"},
            {"item.dye.use", "block.grass.place"},
            {"item.glow_ink_sac.use", "block.grass.place"},
            {"item.honeycomb.wax_on", "entity.experience_orb.pickup"},
            {"item.ink_sac.use", "block.grass.place"},
            {"item.spyglass.stop_using", "entity.experience_orb.pickup"},
            {"item.spyglass.use", "entity.experience_orb.pickup"},
    };

    static final String[][] SOUND_NAME_FALLBACKS_755_TO_754 = {
            {"block.amethyst_block.break", "block.glass.break"},
            {"block.amethyst_block.chime", "block.note_block.pling"},
            {"block.amethyst_block.fall", "block.glass.fall"},
            {"block.amethyst_block.hit", "block.glass.hit"},
            {"block.amethyst_block.place", "block.glass.place"},
            {"block.amethyst_block.step", "block.glass.step"},
            {"block.amethyst_cluster.break", "block.glass.break"},
            {"block.amethyst_cluster.fall", "block.glass.fall"},
            {"block.amethyst_cluster.hit", "block.glass.hit"},
            {"block.amethyst_cluster.place", "block.glass.place"},
            {"block.amethyst_cluster.step", "block.glass.step"},
            {"block.azalea.break", "block.grass.break"},
            {"block.azalea.fall", "block.grass.fall"},
            {"block.azalea.hit", "block.grass.hit"},
            {"block.azalea.place", "block.grass.place"},
            {"block.azalea.step", "block.grass.step"},
            {"block.azalea_leaves.break", "block.grass.break"},
            {"block.azalea_leaves.fall", "block.grass.fall"},
            {"block.azalea_leaves.hit", "block.grass.hit"},
            {"block.azalea_leaves.place", "block.grass.place"},
            {"block.azalea_leaves.step", "block.grass.step"},
            {"block.big_dripleaf.break", "block.grass.break"},
            {"block.big_dripleaf.fall", "block.grass.fall"},
            {"block.big_dripleaf.hit", "block.grass.hit"},
            {"block.big_dripleaf.place", "block.grass.place"},
            {"block.big_dripleaf.step", "block.grass.step"},
            {"block.big_dripleaf.tilt_down", "block.grass.step"},
            {"block.big_dripleaf.tilt_up", "block.grass.step"},
            {"block.cake.add_candle", "block.wood.place"},
            {"block.calcite.break", "block.stone.break"},
            {"block.calcite.fall", "block.stone.fall"},
            {"block.calcite.hit", "block.stone.hit"},
            {"block.calcite.place", "block.stone.place"},
            {"block.calcite.step", "block.stone.step"},
            {"block.candle.ambient", "block.fire.ambient"},
            {"block.candle.break", "block.wool.break"},
            {"block.candle.extinguish", "block.fire.extinguish"},
            {"block.candle.fall", "block.wool.fall"},
            {"block.candle.hit", "block.wool.hit"},
            {"block.candle.place", "block.wool.place"},
            {"block.candle.step", "block.wool.step"},
            {"block.cave_vines.break", "block.grass.break"},
            {"block.cave_vines.fall", "block.grass.fall"},
            {"block.cave_vines.hit", "block.grass.hit"},
            {"block.cave_vines.pick_berries", "item.sweet_berries.pick_from_bush"},
            {"block.cave_vines.place", "block.grass.place"},
            {"block.cave_vines.step", "block.grass.step"},
            {"block.copper.break", "block.metal.break"},
            {"block.copper.fall", "block.metal.fall"},
            {"block.copper.hit", "block.metal.hit"},
            {"block.copper.place", "block.metal.place"},
            {"block.copper.step", "block.metal.step"},
            {"block.deepslate.break", "block.stone.break"},
            {"block.deepslate.fall", "block.stone.fall"},
            {"block.deepslate.hit", "block.stone.hit"},
            {"block.deepslate.place", "block.stone.place"},
            {"block.deepslate.step", "block.stone.step"},
            {"block.deepslate_bricks.break", "block.stone.break"},
            {"block.deepslate_bricks.fall", "block.stone.fall"},
            {"block.deepslate_bricks.hit", "block.stone.hit"},
            {"block.deepslate_bricks.place", "block.stone.place"},
            {"block.deepslate_bricks.step", "block.stone.step"},
            {"block.deepslate_tiles.break", "block.stone.break"},
            {"block.deepslate_tiles.fall", "block.stone.fall"},
            {"block.deepslate_tiles.hit", "block.stone.hit"},
            {"block.deepslate_tiles.place", "block.stone.place"},
            {"block.deepslate_tiles.step", "block.stone.step"},
            {"block.dripstone_block.break", "block.stone.break"},
            {"block.dripstone_block.fall", "block.stone.fall"},
            {"block.dripstone_block.hit", "block.stone.hit"},
            {"block.dripstone_block.place", "block.stone.place"},
            {"block.dripstone_block.step", "block.stone.step"},
            {"block.flowering_azalea.break", "block.grass.break"},
            {"block.flowering_azalea.fall", "block.grass.fall"},
            {"block.flowering_azalea.hit", "block.grass.hit"},
            {"block.flowering_azalea.place", "block.grass.place"},
            {"block.flowering_azalea.step", "block.grass.step"},
            {"block.hanging_roots.break", "block.grass.break"},
            {"block.hanging_roots.fall", "block.grass.fall"},
            {"block.hanging_roots.hit", "block.grass.hit"},
            {"block.hanging_roots.place", "block.grass.place"},
            {"block.hanging_roots.step", "block.grass.step"},
            {"block.large_amethyst_bud.break", "block.glass.break"},
            {"block.large_amethyst_bud.place", "block.glass.place"},
            {"block.medium_amethyst_bud.break", "block.glass.break"},
            {"block.medium_amethyst_bud.place", "block.glass.place"},
            {"block.moss.break", "block.grass.break"},
            {"block.moss.fall", "block.grass.fall"},
            {"block.moss.hit", "block.grass.hit"},
            {"block.moss.place", "block.grass.place"},
            {"block.moss.step", "block.grass.step"},
            {"block.moss_carpet.break", "block.grass.break"},
            {"block.moss_carpet.fall", "block.grass.fall"},
            {"block.moss_carpet.hit", "block.grass.hit"},
            {"block.moss_carpet.place", "block.grass.place"},
            {"block.moss_carpet.step", "block.grass.step"},
            {"block.pointed_dripstone.break", "block.stone.break"},
            {"block.pointed_dripstone.drip_lava", "block.lava.ambient"},
            {"block.pointed_dripstone.drip_lava_into_cauldron", "block.lava.ambient"},
            {"block.pointed_dripstone.drip_water", "block.water.ambient"},
            {"block.pointed_dripstone.drip_water_into_cauldron", "block.water.ambient"},
            {"block.pointed_dripstone.fall", "block.stone.fall"},
            {"block.pointed_dripstone.hit", "block.stone.hit"},
            {"block.pointed_dripstone.land", "block.stone.fall"},
            {"block.pointed_dripstone.place", "block.stone.place"},
            {"block.pointed_dripstone.step", "block.stone.step"},
            {"block.polished_deepslate.break", "block.stone.break"},
            {"block.polished_deepslate.fall", "block.stone.fall"},
            {"block.polished_deepslate.hit", "block.stone.hit"},
            {"block.polished_deepslate.place", "block.stone.place"},
            {"block.polished_deepslate.step", "block.stone.step"},
            {"block.powder_snow.break", "block.snow.break"},
            {"block.powder_snow.fall", "block.snow.fall"},
            {"block.powder_snow.hit", "block.snow.hit"},
            {"block.powder_snow.place", "block.snow.place"},
            {"block.powder_snow.step", "block.snow.step"},
            {"block.rooted_dirt.break", "block.grass.break"},
            {"block.rooted_dirt.fall", "block.grass.fall"},
            {"block.rooted_dirt.hit", "block.grass.hit"},
            {"block.rooted_dirt.place", "block.grass.place"},
            {"block.rooted_dirt.step", "block.grass.step"},
            {"block.sculk_sensor.break", "block.stone.break"},
            {"block.sculk_sensor.clicking", "block.wooden_button.click_on"},
            {"block.sculk_sensor.clicking_stop", "block.wooden_button.click_off"},
            {"block.sculk_sensor.fall", "block.stone.fall"},
            {"block.sculk_sensor.hit", "block.stone.hit"},
            {"block.sculk_sensor.place", "block.stone.place"},
            {"block.sculk_sensor.step", "block.stone.step"},
            {"block.small_amethyst_bud.break", "block.glass.break"},
            {"block.small_amethyst_bud.place", "block.glass.place"},
            {"block.small_dripleaf.break", "block.grass.break"},
            {"block.small_dripleaf.fall", "block.grass.fall"},
            {"block.small_dripleaf.hit", "block.grass.hit"},
            {"block.small_dripleaf.place", "block.grass.place"},
            {"block.small_dripleaf.step", "block.grass.step"},
            {"block.spore_blossom.break", "block.grass.break"},
            {"block.spore_blossom.fall", "block.grass.fall"},
            {"block.spore_blossom.hit", "block.grass.hit"},
            {"block.spore_blossom.place", "block.grass.place"},
            {"block.spore_blossom.step", "block.grass.step"},
            {"block.sweet_berry_bush.pick_berries", "item.sweet_berries.pick_from_bush"},
            {"block.tuff.break", "block.stone.break"},
            {"block.tuff.fall", "block.stone.fall"},
            {"block.tuff.hit", "block.stone.hit"},
            {"block.tuff.place", "block.stone.place"},
            {"block.tuff.step", "block.stone.step"},
            {"block.vine.break", "block.grass.break"},
            {"block.vine.fall", "block.grass.fall"},
            {"block.vine.hit", "block.grass.hit"},
            {"block.vine.place", "block.grass.place"},
            {"entity.axolotl.attack", "entity.tropical_fish.flop"},
            {"entity.axolotl.death", "entity.tropical_fish.death"},
            {"entity.axolotl.hurt", "entity.tropical_fish.hurt"},
            {"entity.axolotl.idle_air", "entity.tropical_fish.ambient"},
            {"entity.axolotl.idle_water", "entity.tropical_fish.ambient"},
            {"entity.axolotl.splash", "entity.fish.swim"},
            {"entity.axolotl.swim", "entity.fish.swim"},
            {"entity.glow_item_frame.add_item", "entity.item_frame.add_item"},
            {"entity.glow_item_frame.break", "entity.item_frame.break"},
            {"entity.glow_item_frame.place", "entity.item_frame.place"},
            {"entity.glow_item_frame.remove_item", "entity.item_frame.remove_item"},
            {"entity.glow_item_frame.rotate_item", "entity.item_frame.rotate_item"},
            {"entity.glow_squid.ambient", "entity.squid.ambient"},
            {"entity.glow_squid.death", "entity.squid.death"},
            {"entity.glow_squid.hurt", "entity.squid.hurt"},
            {"entity.glow_squid.squirt", "entity.squid.squirt"},
            {"entity.goat.ambient", "entity.sheep.ambient"},
            {"entity.goat.death", "entity.sheep.death"},
            {"entity.goat.eat", "entity.sheep.ambient"},
            {"entity.goat.hurt", "entity.sheep.hurt"},
            {"entity.goat.long_jump", "entity.sheep.ambient"},
            {"entity.goat.milk", "entity.cow.milk"},
            {"entity.goat.prepare_ram", "entity.sheep.ambient"},
            {"entity.goat.ram_impact", "entity.sheep.hurt"},
            {"entity.goat.screaming.ambient", "entity.sheep.ambient"},
            {"entity.goat.screaming.death", "entity.sheep.death"},
            {"entity.goat.screaming.eat", "entity.sheep.ambient"},
            {"entity.goat.screaming.hurt", "entity.sheep.hurt"},
            {"entity.goat.screaming.long_jump", "entity.sheep.ambient"},
            {"entity.goat.screaming.milk", "entity.cow.milk"},
            {"entity.goat.screaming.prepare_ram", "entity.sheep.ambient"},
            {"entity.goat.screaming.ram_impact", "entity.sheep.hurt"},
            {"entity.goat.step", "entity.sheep.step"},
            {"entity.minecart.inside.underwater", "entity.minecart.inside"},
            {"entity.player.hurt_freeze", "entity.player.hurt"},
            {"entity.skeleton.converted_to_stray", "entity.skeleton.ambient"},
            {"item.axe.scrape", "item.axe.strip"},
            {"item.axe.wax_off", "item.axe.strip"},
            {"item.bone_meal.use", "block.grass.place"},
            {"item.bucket.empty_axolotl", "item.bucket.empty"},
            {"item.bucket.empty_powder_snow", "item.bucket.empty"},
            {"item.bucket.fill_axolotl", "item.bucket.fill"},
            {"item.bucket.fill_powder_snow", "item.bucket.fill"},
            {"item.dye.use", "block.grass.place"},
            {"item.glow_ink_sac.use", "block.grass.place"},
            {"item.honeycomb.wax_on", "entity.experience_orb.pickup"},
            {"item.ink_sac.use", "block.grass.place"},
            {"item.spyglass.stop_using", "entity.experience_orb.pickup"},
            {"item.spyglass.use", "entity.experience_orb.pickup"},
    };

    static final String[][] SOUND_NAME_FALLBACKS_754_TO_755 = {
            {"item.sweet_berries.pick_from_bush", "block.sweet_berry_bush.pick_berries"},
    };

    static final int[][] ATTRIBUTE_754_RANGES = {
            {0, 12, 0},
    };

    static final int[][] ATTRIBUTE_754_OVERRIDES = {
    };
    // ATTRIBUTE_754: 755 entries=13 ranges=1 overrides=0

    static final int[][] ATTRIBUTE_REVERSE_754_RANGES = {
            {0, 12, 0},
    };

    static final int[][] ATTRIBUTE_REVERSE_754_OVERRIDES = {
    };
    // ATTRIBUTE_REVERSE_754: 754 entries=13 ranges=1 overrides=0

    static final String[] ATTRIBUTE_754_NAMES = {
            "generic.max_health", "generic.follow_range", "generic.knockback_resistance", "generic.movement_speed",
            "generic.flying_speed", "generic.attack_damage", "generic.attack_knockback", "generic.attack_speed",
            "generic.armor", "generic.armor_toughness", "generic.luck", "zombie.spawn_reinforcements",
            "horse.jump_strength",
    };

    static final int[][] ENCHANTMENT_754_RANGES = {
            {0, 37, 0},
    };

    static final int[][] ENCHANTMENT_754_OVERRIDES = {
    };
    // ENCHANTMENT_754: 755 entries=38 ranges=1 overrides=0

    static final int[][] ENCHANTMENT_REVERSE_754_RANGES = {
            {0, 37, 0},
    };

    static final int[][] ENCHANTMENT_REVERSE_754_OVERRIDES = {
    };
    // ENCHANTMENT_REVERSE_754: 754 entries=38 ranges=1 overrides=0

    static final int[][] BIOME_754_RANGES = {
            {0, 50, 0}, {129, 134, 0}, {155, 158, 0}, {160, 173, 0},
    };

    static final int[][] BIOME_754_OVERRIDES = {
            {51, 1}, {52, 1}, {53, 1}, {54, 1}, {55, 1}, {56, 1}, {57, 1}, {58, 1},
            {59, 1}, {60, 1}, {61, 1}, {62, 1}, {63, 1}, {64, 1}, {65, 1}, {66, 1},
            {67, 1}, {68, 1}, {69, 1}, {70, 1}, {71, 1}, {72, 1}, {73, 1}, {74, 1},
            {75, 1}, {76, 1}, {77, 1}, {78, 1}, {79, 1}, {80, 1}, {81, 1}, {82, 1},
            {83, 1}, {84, 1}, {85, 1}, {86, 1}, {87, 1}, {88, 1}, {89, 1}, {90, 1},
            {91, 1}, {92, 1}, {93, 1}, {94, 1}, {95, 1}, {96, 1}, {97, 1}, {98, 1},
            {99, 1}, {100, 1}, {101, 1}, {102, 1}, {103, 1}, {104, 1}, {105, 1}, {106, 1},
            {107, 1}, {108, 1}, {109, 1}, {110, 1}, {111, 1}, {112, 1}, {113, 1}, {114, 1},
            {115, 1}, {116, 1}, {117, 1}, {118, 1}, {119, 1}, {120, 1}, {121, 1}, {122, 1},
            {123, 1}, {124, 1}, {125, 1}, {126, 1}, {127, 127}, {128, 1}, {135, 1}, {136, 1},
            {137, 1}, {138, 1}, {139, 1}, {140, 140}, {141, 1}, {142, 1}, {143, 1}, {144, 1},
            {145, 1}, {146, 1}, {147, 1}, {148, 1}, {149, 149}, {150, 1}, {151, 151}, {152, 1},
            {153, 1}, {154, 1}, {159, 1}, {174, 1}, {175, 1},
    };
    // BIOME_754: 755 entries=176 ranges=4 overrides=101

    static final int[][] BIOME_754_TO_755_RANGES = {
            {0, 50, 0}, {129, 134, 0}, {155, 158, 0}, {160, 173, 0},
    };

    static final int[][] BIOME_754_TO_755_OVERRIDES = {
            {51, 1}, {52, 1}, {53, 1}, {54, 1}, {55, 1}, {56, 1}, {57, 1}, {58, 1},
            {59, 1}, {60, 1}, {61, 1}, {62, 1}, {63, 1}, {64, 1}, {65, 1}, {66, 1},
            {67, 1}, {68, 1}, {69, 1}, {70, 1}, {71, 1}, {72, 1}, {73, 1}, {74, 1},
            {75, 1}, {76, 1}, {77, 1}, {78, 1}, {79, 1}, {80, 1}, {81, 1}, {82, 1},
            {83, 1}, {84, 1}, {85, 1}, {86, 1}, {87, 1}, {88, 1}, {89, 1}, {90, 1},
            {91, 1}, {92, 1}, {93, 1}, {94, 1}, {95, 1}, {96, 1}, {97, 1}, {98, 1},
            {99, 1}, {100, 1}, {101, 1}, {102, 1}, {103, 1}, {104, 1}, {105, 1}, {106, 1},
            {107, 1}, {108, 1}, {109, 1}, {110, 1}, {111, 1}, {112, 1}, {113, 1}, {114, 1},
            {115, 1}, {116, 1}, {117, 1}, {118, 1}, {119, 1}, {120, 1}, {121, 1}, {122, 1},
            {123, 1}, {124, 1}, {125, 1}, {126, 1}, {127, 127}, {128, 1}, {135, 1}, {136, 1},
            {137, 1}, {138, 1}, {139, 1}, {140, 140}, {141, 1}, {142, 1}, {143, 1}, {144, 1},
            {145, 1}, {146, 1}, {147, 1}, {148, 1}, {149, 149}, {150, 1}, {151, 151}, {152, 1},
            {153, 1}, {154, 1}, {159, 1},
    };
    // BIOME_754_TO_755: 754 entries=174 ranges=4 overrides=99

    static final int[][] DAMAGE_TYPE_754_RANGES = {
            {0, 41, 0},
    };

    static final int[][] DAMAGE_TYPE_754_OVERRIDES = {
    };
    // DAMAGE_TYPE_754: 755 entries=42 ranges=1 overrides=0

    static final int[][] DAMAGE_TYPE_754_TO_755_RANGES = {
            {0, 41, 0},
    };

    static final int[][] DAMAGE_TYPE_754_TO_755_OVERRIDES = {
    };
    // DAMAGE_TYPE_754_TO_755: 754 entries=42 ranges=1 overrides=0

}
