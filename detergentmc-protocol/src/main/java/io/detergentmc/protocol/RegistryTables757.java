package io.detergentmc.protocol;

/**
 * 757（1.18／1.18.1）のレジストリ対応表（区間＋上書きの生の表）。
 *
 * <p>759（1.19）との隣接表だけを置く。757 は最古バージョンのため
 * 離れた組み合わせの合成表は作らない（763〜775 の組が合成表を持つ
 * のとは違い、759〜762 の組は隣接表の連鎖だけを使う設計のため。
 * 757↔763〜775 は 757→759→760→761→762→763→合成表の連鎖で到達する）。
 * 759→757 の順向き表（*_757_*）と 757→759 の反転表（*_757_TO_759_*）
 * を両方置く。反転表は名前の真値（同名のものを優先）で作ってあり、
 * {@link RegistryRemapper#invert} の first-wins と違う箇所は
 * tools/maps757-corrections.json の補正（UP_757_CORRECTIONS）で
 * 埋める（補正はブロック状態 110 件・音 20 件）。
 *
 * <p>{@link RegistryRemapper} から分離した。理由はバイトコードの制限：
 * 757 の表を RegistryRemapper に置くと静的初期化子（{@code <clinit>}）
 * が 64KB を超えてコンパイルできなくなる（"code too large"）ため。
 * このクラスの表は {@link RegistryRemapper} から static import で
 * 同じ名前のまま参照する（展開済み表 {@code *_757_MAP} やアクセサの
 * 書き方は変わらない）。
 *
 * <p>表の内容と検証は {@code detergentmc-protocol/tools/} を参照：
 * 生成は {@code gen-757-tables.py}、検証は {@code verify-757-tables.py}。
 */
final class RegistryTables757 {

    private RegistryTables757() {
    }

    static final int[][] BLOCK_STATE_757_RANGES = {
            {0, 20, 0}, {22, 33, -1}, {35, 36, -14}, {39, 40, -18}, {43, 44, -22}, {47, 48, -26},
            {51, 52, -30}, {55, 56, -34}, {59, 60, -38}, {63, 64, -42}, {67, 68, -46}, {71, 72, -50},
            {74, 134, -41}, {135, 137, -59}, {143, 160, -49}, {161, 181, -52}, {182, 184, -70}, {185, 202, -55},
            {203, 205, -73}, {207, 208, -59}, {209, 210, -60}, {211, 212, -61}, {213, 214, -62}, {215, 216, -63},
            {217, 218, -64}, {219, 220, -65}, {221, 222, -66}, {223, 224, -67}, {225, 226, -68}, {227, 228, -69},
            {229, 230, -70}, {231, 232, -71}, {233, 234, -72}, {235, 236, -73}, {237, 238, -74}, {239, 240, -75},
            {241, 242, -76}, {243, 244, -77}, {245, 246, -78}, {247, 248, -79}, {249, 250, -80}, {251, 252, -81},
            {253, 254, -82}, {255, 256, -83}, {257, 258, -84}, {259, 260, -85}, {261, 262, -86}, {263, 264, -87},
            {265, 266, -88}, {267, 268, -89}, {269, 270, -90}, {271, 272, -91}, {273, 274, -92}, {275, 276, -93},
            {277, 278, -94}, {279, 280, -95}, {281, 282, -96}, {283, 284, -97}, {285, 286, -98}, {287, 288, -99},
            {289, 290, -100}, {291, 292, -101}, {293, 294, -102}, {295, 296, -103}, {297, 298, -104}, {299, 300, -105},
            {301, 302, -106}, {303, 304, -107}, {305, 306, -108}, {307, 308, -109}, {309, 310, -110}, {311, 312, -111},
            {313, 314, -112}, {315, 316, -113}, {317, 318, -114}, {319, 320, -115}, {321, 322, -116}, {323, 324, -117},
            {325, 326, -118}, {327, 328, -119}, {329, 330, -120}, {331, 332, -121}, {333, 334, -122}, {335, 336, -123},
            {337, 338, -124}, {339, 340, -125}, {341, 342, -126}, {343, 344, -127}, {345, 346, -128}, {347, 348, -129},
            {349, 350, -130}, {351, 352, -131}, {353, 354, -132}, {355, 356, -133}, {357, 358, -134}, {359, 360, -135},
            {361, 362, -136}, {363, 364, -137}, {365, 366, -138}, {367, 368, -139}, {369, 370, -140}, {371, 372, -141},
            {375, 376, -227}, {377, 378, -228}, {379, 380, -229}, {381, 382, -230}, {383, 384, -231}, {385, 386, -232},
            {387, 388, -233}, {389, 390, -234}, {391, 392, -235}, {393, 394, -236}, {395, 396, -237}, {397, 398, -238},
            {399, 400, -239}, {403, 404, -171}, {405, 406, -172}, {407, 408, -173}, {409, 410, -174}, {411, 412, -175},
            {413, 414, -176}, {415, 416, -177}, {417, 418, -178}, {419, 420, -179}, {421, 422, -180}, {423, 424, -181},
            {425, 426, -182}, {427, 428, -183}, {429, 430, -184}, {431, 432, -185}, {433, 434, -186}, {435, 436, -187},
            {437, 438, -188}, {439, 440, -189}, {441, 442, -190}, {443, 444, -191}, {445, 446, -192}, {447, 448, -193},
            {449, 450, -194}, {451, 452, -195}, {453, 454, -196}, {455, 456, -197}, {457, 3827, -198}, {3828, 3859, -390},
            {3860, 4079, -230}, {4080, 4087, -278}, {4088, 4189, -238}, {4190, 4191, -250}, {4192, 4803, -240}, {4804, 4867, -624},
            {4868, 4871, -304}, {4874, 5518, -306}, {5519, 5598, -466}, {5599, 6903, -386}, {6905, 7082, -387}, {7083, 7106, -531},
            {7107, 8163, -411}, {8164, 8243, -6154}, {8244, 9076, -491}, {9077, 9082, -527}, {9083, 9130, -497}, {9131, 9136, -509},
            {9137, 9330, -503}, {9331, 9362, -4310}, {9363, 9522, -535}, {9523, 9554, -5488}, {9555, 9874, -567}, {9875, 9938, -6245},
            {9939, 13691, -631}, {13692, 14015, -2575}, {14016, 18768, -955}, {18770, 18897, -13877}, {18908, 19776, -1094}, {19778, 21436, -1095},
    };

    static final int[][] BLOCK_STATE_757_OVERRIDES = {
            {21, 15}, {34, 21}, {37, 22}, {38, 21}, {41, 22}, {42, 21}, {45, 22}, {46, 21},
            {49, 22}, {50, 21}, {53, 22}, {54, 21}, {57, 22}, {58, 21}, {61, 22}, {62, 21},
            {65, 22}, {66, 21}, {69, 22}, {70, 21}, {73, 22}, {138, 15}, {139, 15}, {140, 10},
            {141, 10}, {142, 10}, {206, 148}, {373, 231}, {374, 148}, {401, 161}, {402, 232}, {4872, 10},
            {4873, 1485}, {6904, 6512}, {18769, 18684}, {18898, 18684}, {18899, 18684}, {18900, 17719}, {18901, 17719}, {18902, 17719},
            {18903, 17719}, {18904, 17719}, {18905, 17719}, {18906, 17719}, {18907, 17719}, {19777, 10}, {21437, 4082}, {21438, 4082},
            {21439, 4082}, {21440, 4082}, {21441, 4082}, {21442, 4082}, {21443, 4082}, {21444, 4082}, {21445, 4082}, {21446, 5215},
            {21447, 18684},
    };
    // BLOCK_STATE_757: 759 entries=21448 ranges=174 overrides=57

    static final int[][] ITEM_757_RANGES = {
            {0, 18, 0}, {20, 28, -1}, {30, 37, -2}, {39, 109, -3}, {113, 120, -6}, {122, 129, -7},
            {131, 138, -8}, {140, 147, -9}, {149, 219, -10}, {221, 230, -11}, {232, 255, -12}, {256, 273, -11},
            {275, 298, -12}, {301, 305, -14}, {307, 317, -15}, {319, 325, -16}, {330, 337, -20}, {339, 341, -21},
            {342, 343, 49}, {345, 356, -24}, {358, 415, -25}, {416, 639, -23}, {641, 652, -24}, {654, 662, -25},
            {664, 672, -26}, {674, 681, -27}, {683, 698, -28}, {699, 700, -29}, {701, 702, -30}, {703, 704, -31},
            {705, 706, -32}, {707, 708, -33}, {712, 809, -36}, {811, 824, -37}, {826, 833, -38}, {834, 911, -39},
            {913, 930, -40}, {932, 965, -41}, {967, 973, -42}, {975, 1070, -43}, {1074, 1086, -45}, {1088, 1146, -46},
    };

    static final int[][] ITEM_757_OVERRIDES = {
            {19, 15}, {29, 22}, {38, 30}, {110, 101}, {111, 22}, {112, 15}, {121, 109}, {130, 117},
            {139, 125}, {148, 133}, {220, 204}, {231, 218}, {274, 257}, {299, 15}, {300, 232}, {306, 8},
            {318, 301}, {326, 8}, {327, 300}, {328, 8}, {329, 603}, {338, 244}, {344, 244}, {357, 327},
            {640, 611}, {653, 623}, {663, 632}, {673, 641}, {682, 649}, {709, 675}, {710, 670}, {711, 670},
            {810, 768}, {825, 777}, {912, 928}, {931, 873}, {966, 873}, {974, 913}, {1071, 1015}, {1072, 1028},
            {1073, 1015}, {1087, 1015}, {1147, 275}, {1148, 275}, {1149, 275}, {1150, 304}, {1151, 690},
    };
    // ITEM_757: 759 entries=1152 ranges=42 overrides=47

    static final int[][] ENTITY_TYPE_757_RANGES = {
            {1, 8, -1}, {9, 31, -2}, {33, 91, -3}, {93, 104, -4}, {106, 117, -5},
    };

    static final int[][] ENTITY_TYPE_757_OVERRIDES = {
            {0, 97}, {32, 3}, {92, 3}, {105, 72},
    };
    // ENTITY_TYPE_757: 759 entries=118 ranges=5 overrides=4

    static final int[][] SOUND_757_RANGES = {
            {2, 3, 1079}, {7, 160, -7}, {162, 166, -8}, {168, 399, -9}, {400, 404, 19}, {406, 407, 546},
            {409, 410, 545}, {415, 416, -345}, {419, 469, -28}, {472, 479, -30}, {481, 523, -31}, {532, 623, -39},
            {625, 627, 538}, {629, 655, -44}, {656, 660, -200}, {662, 664, 359}, {667, 669, 496}, {671, 678, -59},
            {680, 700, -60}, {701, 704, -61}, {708, 722, -64}, {723, 727, -267}, {728, 830, -69}, {832, 968, -70},
            {969, 973, -715}, {975, 979, -721}, {980, 986, -83}, {987, 990, -733}, {993, 997, -808}, {998, 1127, -94},
            {1132, 1220, -98}, {1236, 1237, -381}, {1241, 1320, -118},
    };

    static final int[][] SOUND_757_OVERRIDES = {
            {0, 1079}, {1, 1079}, {4, 356}, {5, 356}, {6, 1080}, {161, 150}, {167, 155}, {405, 956},
            {408, 952}, {411, 69}, {412, 66}, {413, 65}, {414, 67}, {417, 71}, {418, 65}, {470, 540},
            {471, 700}, {480, 540}, {524, 700}, {525, 700}, {526, 700}, {527, 700}, {528, 700}, {529, 700},
            {530, 700}, {531, 700}, {624, 1160}, {628, 1168}, {661, 1018}, {665, 1026}, {666, 1160}, {670, 1168},
            {679, 621}, {705, 643}, {706, 641}, {707, 647}, {831, 753}, {974, 897}, {991, 411}, {992, 258},
            {1128, 66}, {1129, 209}, {1130, 70}, {1131, 67}, {1221, 855}, {1222, 855}, {1223, 862}, {1224, 856},
            {1225, 858}, {1226, 860}, {1227, 862}, {1228, 855}, {1229, 859}, {1230, 855}, {1231, 855}, {1232, 855},
            {1233, 855}, {1234, 855}, {1235, 862}, {1238, 855}, {1239, 860}, {1240, 855},
    };
    // SOUND_757: 759 entries=1321 ranges=33 overrides=62

    static final int[][] PARTICLE_757_RANGES = {
            {0, 23, 0}, {24, 28, -1}, {32, 91, -4},
    };

    static final int[][] PARTICLE_757_OVERRIDES = {
            {29, 29}, {30, 29}, {31, 29}, {92, 50},
    };
    // PARTICLE_757: 759 entries=93 ranges=3 overrides=4

    static final int[][] BLOCK_757_RANGES = {
            {0, 18, 0}, {20, 25, -1}, {27, 45, -2}, {49, 54, -5}, {55, 61, -6}, {63, 68, -7},
            {70, 75, -8}, {77, 176, -9}, {178, 187, -10}, {189, 197, -11}, {199, 247, -12}, {249, 252, -13},
            {255, 276, -15}, {278, 316, -16}, {318, 343, -17}, {345, 407, -18}, {409, 490, -19}, {492, 499, -20},
            {501, 514, -21}, {516, 520, -22}, {522, 526, -23}, {528, 697, -24}, {699, 846, -25}, {850, 899, -29},
            {901, 927, -30},
    };

    static final int[][] BLOCK_757_OVERRIDES = {
            {19, 13}, {26, 19}, {46, 38}, {47, 13}, {48, 9}, {62, 50}, {69, 56}, {76, 62},
            {177, 162}, {188, 172}, {198, 181}, {248, 230}, {253, 9}, {254, 142}, {277, 260}, {317, 295},
            {344, 321}, {408, 152}, {491, 466}, {500, 478}, {515, 259}, {521, 199}, {527, 168}, {698, 668},
            {847, 871}, {848, 258}, {849, 871}, {900, 9}, {928, 208}, {929, 208}, {930, 208}, {931, 263},
            {932, 871},
    };
    // BLOCK_757: 759 entries=933 ranges=25 overrides=33

    static final int[][] MENU_757_RANGES = {
            {0, 23, 0},
    };

    static final int[][] MENU_757_OVERRIDES = {
    };
    // MENU_757: 759 entries=24 ranges=1 overrides=0

    static final int[][] ITEM_REVERSE_757_RANGES = {
            {0, 18, 0}, {19, 27, 1}, {28, 35, 2}, {36, 106, 3}, {107, 114, 6}, {115, 122, 7},
            {123, 130, 8}, {131, 138, 9}, {139, 209, 10}, {210, 219, 11}, {220, 243, 12}, {245, 262, 11},
            {263, 286, 12}, {287, 291, 14}, {292, 302, 15}, {303, 309, 16}, {310, 317, 20}, {318, 320, 21},
            {321, 332, 24}, {333, 390, 25}, {391, 392, -49}, {393, 616, 23}, {617, 628, 24}, {629, 637, 25},
            {638, 646, 26}, {647, 654, 27}, {655, 670, 28}, {676, 773, 36}, {774, 787, 37}, {788, 795, 38},
            {796, 872, 39}, {873, 890, 40}, {891, 924, 41}, {925, 931, 42}, {932, 1027, 43}, {1029, 1041, 45},
            {1042, 1100, 46},
    };

    static final int[][] ITEM_REVERSE_757_OVERRIDES = {
            {244, 338}, {671, 700}, {672, 702}, {673, 704}, {674, 706}, {675, 708}, {1028, 1072},
    };
    // ITEM_REVERSE_757: 757 entries=1101 ranges=37 overrides=7

    static final int[][] BLOCK_STATE_757_TO_759_RANGES = {
            {0, 20, 0}, {21, 32, 1}, {33, 93, 41}, {94, 111, 49}, {112, 129, 52}, {130, 147, 55},
            {259, 3629, 198}, {3630, 3849, 230}, {3850, 3951, 238}, {3952, 4563, 240}, {4564, 4567, 304}, {4568, 5212, 306},
            {5213, 6517, 386}, {6518, 6695, 387}, {6696, 7752, 411}, {7753, 8585, 491}, {8586, 8633, 497}, {8634, 8827, 503},
            {8828, 8987, 535}, {8988, 9307, 567}, {9308, 13060, 631}, {13061, 17813, 955}, {17814, 18682, 1094}, {18683, 20341, 1095},
    };

    static final int[][] BLOCK_STATE_757_TO_759_OVERRIDES = {
            {148, 207}, {149, 209}, {150, 211}, {151, 213}, {152, 215}, {153, 217}, {154, 219}, {155, 221},
            {156, 223}, {157, 225}, {158, 227}, {159, 229}, {160, 231}, {161, 233}, {162, 235}, {163, 237},
            {164, 239}, {165, 241}, {166, 243}, {167, 245}, {168, 247}, {169, 249}, {170, 251}, {171, 253},
            {172, 255}, {173, 257}, {174, 259}, {175, 261}, {176, 263}, {177, 265}, {178, 267}, {179, 269},
            {180, 271}, {181, 273}, {182, 275}, {183, 277}, {184, 279}, {185, 281}, {186, 283}, {187, 285},
            {188, 287}, {189, 289}, {190, 291}, {191, 293}, {192, 295}, {193, 297}, {194, 299}, {195, 301},
            {196, 303}, {197, 305}, {198, 307}, {199, 309}, {200, 311}, {201, 313}, {202, 315}, {203, 317},
            {204, 319}, {205, 321}, {206, 323}, {207, 325}, {208, 327}, {209, 329}, {210, 331}, {211, 333},
            {212, 335}, {213, 337}, {214, 339}, {215, 341}, {216, 343}, {217, 345}, {218, 347}, {219, 349},
            {220, 351}, {221, 353}, {222, 355}, {223, 357}, {224, 359}, {225, 361}, {226, 363}, {227, 365},
            {228, 367}, {229, 369}, {230, 371}, {231, 373}, {232, 403}, {233, 405}, {234, 407}, {235, 409},
            {236, 411}, {237, 413}, {238, 415}, {239, 417}, {240, 419}, {241, 421}, {242, 423}, {243, 425},
            {244, 427}, {245, 429}, {246, 431}, {247, 433}, {248, 435}, {249, 437}, {250, 439}, {251, 441},
            {252, 443}, {253, 445}, {254, 447}, {255, 449}, {256, 451}, {257, 453}, {258, 455},
    };
    // BLOCK_STATE_757_TO_759: 759 entries=20342 ranges=24 overrides=111

    static final int[][] ITEM_757_TO_759_RANGES = {
            {0, 18, 0}, {19, 27, 1}, {28, 35, 2}, {36, 106, 3}, {107, 114, 6}, {115, 122, 7},
            {123, 130, 8}, {131, 138, 9}, {139, 209, 10}, {210, 219, 11}, {220, 243, 12}, {245, 262, 11},
            {263, 286, 12}, {287, 291, 14}, {292, 302, 15}, {303, 309, 16}, {310, 317, 20}, {318, 320, 21},
            {321, 332, 24}, {333, 390, 25}, {391, 392, -49}, {393, 616, 23}, {617, 628, 24}, {629, 637, 25},
            {638, 646, 26}, {647, 654, 27}, {655, 670, 28}, {676, 773, 36}, {774, 787, 37}, {788, 795, 38},
            {796, 872, 39}, {873, 890, 40}, {891, 924, 41}, {925, 931, 42}, {932, 1027, 43}, {1029, 1041, 45},
            {1042, 1100, 46},
    };

    static final int[][] ITEM_757_TO_759_OVERRIDES = {
            {244, 338}, {671, 700}, {672, 702}, {673, 704}, {674, 706}, {675, 708}, {1028, 1072},
    };
    // ITEM_757_TO_759: 759 entries=1101 ranges=37 overrides=7

    static final int[][] ENTITY_TYPE_757_TO_759_RANGES = {
            {0, 7, 1}, {8, 29, 2}, {30, 88, 3}, {89, 100, 4}, {101, 112, 5},
    };

    static final int[][] ENTITY_TYPE_757_TO_759_OVERRIDES = {
    };
    // ENTITY_TYPE_757_TO_759: 759 entries=113 ranges=5 overrides=0

    static final int[][] SOUND_757_TO_759_RANGES = {
            {0, 153, 7}, {154, 158, 8}, {159, 390, 9}, {391, 441, 28}, {442, 449, 30}, {450, 492, 31},
            {493, 584, 39}, {585, 611, 44}, {612, 619, 59}, {620, 639, 60}, {640, 643, 61}, {644, 658, 64},
            {659, 761, 69}, {762, 896, 70}, {897, 903, 83}, {904, 1033, 94}, {1034, 1122, 98}, {1123, 1202, 118},
    };

    static final int[][] SOUND_757_TO_759_OVERRIDES = {
    };
    // SOUND_757_TO_759: 759 entries=1203 ranges=18 overrides=0

    static final int[][] PARTICLE_757_TO_759_RANGES = {
            {0, 23, 0}, {24, 27, 1}, {28, 87, 4},
    };

    static final int[][] PARTICLE_757_TO_759_OVERRIDES = {
    };
    // PARTICLE_757_TO_759: 759 entries=88 ranges=3 overrides=0

    static final int[][] BLOCK_757_TO_759_RANGES = {
            {0, 18, 0}, {19, 24, 1}, {25, 43, 2}, {44, 49, 5}, {50, 55, 6}, {56, 61, 7},
            {62, 67, 8}, {68, 167, 9}, {168, 177, 10}, {178, 186, 11}, {187, 235, 12}, {236, 239, 13},
            {240, 261, 15}, {262, 300, 16}, {301, 326, 17}, {327, 389, 18}, {390, 471, 19}, {472, 479, 20},
            {480, 493, 21}, {494, 498, 22}, {499, 503, 23}, {504, 673, 24}, {674, 821, 25}, {822, 870, 29},
            {871, 897, 30},
    };

    static final int[][] BLOCK_757_TO_759_OVERRIDES = {
    };
    // BLOCK_757_TO_759: 759 entries=898 ranges=25 overrides=0

    static final int[][] MENU_757_TO_759_RANGES = {
            {0, 23, 0},
    };

    static final int[][] MENU_757_TO_759_OVERRIDES = {
    };
    // MENU_757_TO_759: 759 entries=24 ranges=1 overrides=0

    static final String[][] SOUND_NAME_FALLBACKS_757 = {
            {"block.froglight.break", "block.glass.break"},
            {"block.froglight.fall", "block.glass.fall"},
            {"block.froglight.hit", "block.glass.hit"},
            {"block.froglight.place", "block.glass.place"},
            {"block.froglight.step", "block.glass.step"},
            {"block.frogspawn.break", "block.slime_block.break"},
            {"block.frogspawn.fall", "block.slime_block.fall"},
            {"block.frogspawn.hatch", "block.slime_block.break"},
            {"block.frogspawn.hit", "block.slime_block.hit"},
            {"block.frogspawn.place", "block.slime_block.place"},
            {"block.frogspawn.step", "block.slime_block.step"},
            {"block.mangrove_roots.break", "block.wood.break"},
            {"block.mangrove_roots.fall", "block.wood.fall"},
            {"block.mangrove_roots.hit", "block.wood.hit"},
            {"block.mangrove_roots.place", "block.wood.place"},
            {"block.mangrove_roots.step", "block.wood.step"},
            {"block.mud.break", "block.gravel.break"},
            {"block.mud.fall", "block.gravel.fall"},
            {"block.mud.hit", "block.gravel.hit"},
            {"block.mud.place", "block.gravel.place"},
            {"block.mud.step", "block.gravel.step"},
            {"block.mud_bricks.break", "block.stone.break"},
            {"block.mud_bricks.fall", "block.stone.fall"},
            {"block.mud_bricks.hit", "block.stone.hit"},
            {"block.mud_bricks.place", "block.stone.place"},
            {"block.mud_bricks.step", "block.stone.step"},
            {"block.muddy_mangrove_roots.break", "block.wood.break"},
            {"block.muddy_mangrove_roots.fall", "block.wood.fall"},
            {"block.muddy_mangrove_roots.hit", "block.wood.hit"},
            {"block.muddy_mangrove_roots.place", "block.wood.place"},
            {"block.muddy_mangrove_roots.step", "block.wood.step"},
            {"block.packed_mud.break", "block.gravel.break"},
            {"block.packed_mud.fall", "block.gravel.fall"},
            {"block.packed_mud.hit", "block.gravel.hit"},
            {"block.packed_mud.place", "block.gravel.place"},
            {"block.packed_mud.step", "block.gravel.step"},
            {"block.sculk.break", "block.deepslate.break"},
            {"block.sculk.charge", "block.sculk_sensor.clicking_stop"},
            {"block.sculk.fall", "block.deepslate.fall"},
            {"block.sculk.hit", "block.deepslate.hit"},
            {"block.sculk.place", "block.deepslate.place"},
            {"block.sculk.spread", "block.sculk_sensor.clicking"},
            {"block.sculk.step", "block.deepslate.step"},
            {"block.sculk_catalyst.bloom", "block.sculk_sensor.clicking"},
            {"block.sculk_catalyst.break", "block.deepslate.break"},
            {"block.sculk_catalyst.fall", "block.deepslate.fall"},
            {"block.sculk_catalyst.hit", "block.deepslate.hit"},
            {"block.sculk_catalyst.place", "block.deepslate.place"},
            {"block.sculk_catalyst.step", "block.deepslate.step"},
            {"block.sculk_shrieker.break", "block.deepslate.break"},
            {"block.sculk_shrieker.fall", "block.deepslate.fall"},
            {"block.sculk_shrieker.hit", "block.deepslate.hit"},
            {"block.sculk_shrieker.place", "block.deepslate.place"},
            {"block.sculk_shrieker.shriek", "entity.ghast.scream"},
            {"block.sculk_shrieker.step", "block.deepslate.step"},
            {"block.sculk_vein.break", "block.cave_vines.break"},
            {"block.sculk_vein.fall", "block.cave_vines.fall"},
            {"block.sculk_vein.hit", "block.cave_vines.hit"},
            {"block.sculk_vein.place", "block.cave_vines.place"},
            {"block.sculk_vein.step", "block.cave_vines.step"},
            {"entity.allay.ambient_with_item", "entity.vex.ambient"},
            {"entity.allay.ambient_without_item", "entity.vex.ambient"},
            {"entity.allay.death", "entity.vex.death"},
            {"entity.allay.hurt", "entity.vex.hurt"},
            {"entity.allay.item_given", "entity.experience_orb.pickup"},
            {"entity.allay.item_taken", "entity.experience_orb.pickup"},
            {"entity.allay.item_thrown", "entity.vex.charge"},
            {"entity.frog.ambient", "entity.axolotl.idle_water"},
            {"entity.frog.death", "entity.axolotl.death"},
            {"entity.frog.eat", "entity.axolotl.attack"},
            {"entity.frog.hurt", "entity.axolotl.hurt"},
            {"entity.frog.lay_spawn", "entity.axolotl.splash"},
            {"entity.frog.long_jump", "entity.axolotl.swim"},
            {"entity.frog.step", "entity.axolotl.swim"},
            {"entity.frog.tongue", "entity.axolotl.attack"},
            {"entity.goat.horn_break", "entity.item.break"},
            {"entity.goat.screaming.horn_break", "entity.item.break"},
            {"entity.parrot.imitate.warden", "entity.parrot.imitate.ravager"},
            {"entity.tadpole.death", "entity.axolotl.death"},
            {"entity.tadpole.flop", "entity.cod.flop"},
            {"entity.tadpole.grow_up", "entity.axolotl.splash"},
            {"entity.tadpole.hurt", "entity.axolotl.hurt"},
            {"entity.warden.agitated", "entity.ravager.ambient"},
            {"entity.warden.ambient", "entity.ravager.ambient"},
            {"entity.warden.angry", "entity.ravager.roar"},
            {"entity.warden.attack_impact", "entity.ravager.attack"},
            {"entity.warden.death", "entity.ravager.death"},
            {"entity.warden.dig", "entity.ravager.step"},
            {"entity.warden.emerge", "entity.ravager.roar"},
            {"entity.warden.heartbeat", "entity.ravager.ambient"},
            {"entity.warden.hurt", "entity.ravager.hurt"},
            {"entity.warden.listening", "entity.ravager.ambient"},
            {"entity.warden.listening_angry", "entity.ravager.ambient"},
            {"entity.warden.nearby_close", "entity.ravager.ambient"},
            {"entity.warden.nearby_closer", "entity.ravager.ambient"},
            {"entity.warden.nearby_closest", "entity.ravager.ambient"},
            {"entity.warden.roar", "entity.ravager.roar"},
            {"entity.warden.sniff", "entity.ravager.ambient"},
            {"entity.warden.sonic_boom", "entity.ravager.attack"},
            {"entity.warden.sonic_charge", "entity.ravager.ambient"},
            {"entity.warden.step", "entity.ravager.step"},
            {"entity.warden.tendril_clicks", "entity.ravager.ambient"},
            {"item.bucket.empty_tadpole", "item.bucket.empty_axolotl"},
            {"item.bucket.fill_tadpole", "item.bucket.fill_axolotl"},
            {"item.goat_horn.play", "block.note_block.bass"},
            {"item.goat_horn.sound.0", "block.note_block.bass"},
            {"item.goat_horn.sound.1", "block.note_block.bass"},
            {"item.goat_horn.sound.2", "block.note_block.bass"},
            {"item.goat_horn.sound.3", "block.note_block.bass"},
            {"item.goat_horn.sound.4", "block.note_block.bass"},
            {"item.goat_horn.sound.5", "block.note_block.bass"},
            {"item.goat_horn.sound.6", "block.note_block.bass"},
            {"item.goat_horn.sound.7", "block.note_block.bass"},
            {"music.overworld.deep_dark", "music.overworld.dripstone_caves"},
            {"music.overworld.jungle_and_forest", "music.overworld.grove"},
            {"music.overworld.old_growth_taiga", "music.overworld.snowy_slopes"},
            {"music.overworld.swamp", "music.overworld.lush_caves"},
            {"music_disc.5", "music_disc.13"},
    };

    static final String[][] SOUND_NAME_FALLBACKS_759_TO_757 = {
            {"block.froglight.break", "block.glass.break"},
            {"block.froglight.fall", "block.glass.fall"},
            {"block.froglight.hit", "block.glass.hit"},
            {"block.froglight.place", "block.glass.place"},
            {"block.froglight.step", "block.glass.step"},
            {"block.frogspawn.break", "block.slime_block.break"},
            {"block.frogspawn.fall", "block.slime_block.fall"},
            {"block.frogspawn.hatch", "block.slime_block.break"},
            {"block.frogspawn.hit", "block.slime_block.hit"},
            {"block.frogspawn.place", "block.slime_block.place"},
            {"block.frogspawn.step", "block.slime_block.step"},
            {"block.mangrove_roots.break", "block.wood.break"},
            {"block.mangrove_roots.fall", "block.wood.fall"},
            {"block.mangrove_roots.hit", "block.wood.hit"},
            {"block.mangrove_roots.place", "block.wood.place"},
            {"block.mangrove_roots.step", "block.wood.step"},
            {"block.mud.break", "block.gravel.break"},
            {"block.mud.fall", "block.gravel.fall"},
            {"block.mud.hit", "block.gravel.hit"},
            {"block.mud.place", "block.gravel.place"},
            {"block.mud.step", "block.gravel.step"},
            {"block.mud_bricks.break", "block.stone.break"},
            {"block.mud_bricks.fall", "block.stone.fall"},
            {"block.mud_bricks.hit", "block.stone.hit"},
            {"block.mud_bricks.place", "block.stone.place"},
            {"block.mud_bricks.step", "block.stone.step"},
            {"block.muddy_mangrove_roots.break", "block.wood.break"},
            {"block.muddy_mangrove_roots.fall", "block.wood.fall"},
            {"block.muddy_mangrove_roots.hit", "block.wood.hit"},
            {"block.muddy_mangrove_roots.place", "block.wood.place"},
            {"block.muddy_mangrove_roots.step", "block.wood.step"},
            {"block.packed_mud.break", "block.gravel.break"},
            {"block.packed_mud.fall", "block.gravel.fall"},
            {"block.packed_mud.hit", "block.gravel.hit"},
            {"block.packed_mud.place", "block.gravel.place"},
            {"block.packed_mud.step", "block.gravel.step"},
            {"block.sculk.break", "block.deepslate.break"},
            {"block.sculk.charge", "block.sculk_sensor.clicking_stop"},
            {"block.sculk.fall", "block.deepslate.fall"},
            {"block.sculk.hit", "block.deepslate.hit"},
            {"block.sculk.place", "block.deepslate.place"},
            {"block.sculk.spread", "block.sculk_sensor.clicking"},
            {"block.sculk.step", "block.deepslate.step"},
            {"block.sculk_catalyst.bloom", "block.sculk_sensor.clicking"},
            {"block.sculk_catalyst.break", "block.deepslate.break"},
            {"block.sculk_catalyst.fall", "block.deepslate.fall"},
            {"block.sculk_catalyst.hit", "block.deepslate.hit"},
            {"block.sculk_catalyst.place", "block.deepslate.place"},
            {"block.sculk_catalyst.step", "block.deepslate.step"},
            {"block.sculk_shrieker.break", "block.deepslate.break"},
            {"block.sculk_shrieker.fall", "block.deepslate.fall"},
            {"block.sculk_shrieker.hit", "block.deepslate.hit"},
            {"block.sculk_shrieker.place", "block.deepslate.place"},
            {"block.sculk_shrieker.shriek", "entity.ghast.scream"},
            {"block.sculk_shrieker.step", "block.deepslate.step"},
            {"block.sculk_vein.break", "block.cave_vines.break"},
            {"block.sculk_vein.fall", "block.cave_vines.fall"},
            {"block.sculk_vein.hit", "block.cave_vines.hit"},
            {"block.sculk_vein.place", "block.cave_vines.place"},
            {"block.sculk_vein.step", "block.cave_vines.step"},
            {"entity.allay.ambient_with_item", "entity.vex.ambient"},
            {"entity.allay.ambient_without_item", "entity.vex.ambient"},
            {"entity.allay.death", "entity.vex.death"},
            {"entity.allay.hurt", "entity.vex.hurt"},
            {"entity.allay.item_given", "entity.experience_orb.pickup"},
            {"entity.allay.item_taken", "entity.experience_orb.pickup"},
            {"entity.allay.item_thrown", "entity.vex.charge"},
            {"entity.frog.ambient", "entity.axolotl.idle_water"},
            {"entity.frog.death", "entity.axolotl.death"},
            {"entity.frog.eat", "entity.axolotl.attack"},
            {"entity.frog.hurt", "entity.axolotl.hurt"},
            {"entity.frog.lay_spawn", "entity.axolotl.splash"},
            {"entity.frog.long_jump", "entity.axolotl.swim"},
            {"entity.frog.step", "entity.axolotl.swim"},
            {"entity.frog.tongue", "entity.axolotl.attack"},
            {"entity.goat.horn_break", "entity.item.break"},
            {"entity.goat.screaming.horn_break", "entity.item.break"},
            {"entity.parrot.imitate.warden", "entity.parrot.imitate.ravager"},
            {"entity.tadpole.death", "entity.axolotl.death"},
            {"entity.tadpole.flop", "entity.cod.flop"},
            {"entity.tadpole.grow_up", "entity.axolotl.splash"},
            {"entity.tadpole.hurt", "entity.axolotl.hurt"},
            {"entity.warden.agitated", "entity.ravager.ambient"},
            {"entity.warden.ambient", "entity.ravager.ambient"},
            {"entity.warden.angry", "entity.ravager.roar"},
            {"entity.warden.attack_impact", "entity.ravager.attack"},
            {"entity.warden.death", "entity.ravager.death"},
            {"entity.warden.dig", "entity.ravager.step"},
            {"entity.warden.emerge", "entity.ravager.roar"},
            {"entity.warden.heartbeat", "entity.ravager.ambient"},
            {"entity.warden.hurt", "entity.ravager.hurt"},
            {"entity.warden.listening", "entity.ravager.ambient"},
            {"entity.warden.listening_angry", "entity.ravager.ambient"},
            {"entity.warden.nearby_close", "entity.ravager.ambient"},
            {"entity.warden.nearby_closer", "entity.ravager.ambient"},
            {"entity.warden.nearby_closest", "entity.ravager.ambient"},
            {"entity.warden.roar", "entity.ravager.roar"},
            {"entity.warden.sniff", "entity.ravager.ambient"},
            {"entity.warden.sonic_boom", "entity.ravager.attack"},
            {"entity.warden.sonic_charge", "entity.ravager.ambient"},
            {"entity.warden.step", "entity.ravager.step"},
            {"entity.warden.tendril_clicks", "entity.ravager.ambient"},
            {"item.bucket.empty_tadpole", "item.bucket.empty_axolotl"},
            {"item.bucket.fill_tadpole", "item.bucket.fill_axolotl"},
            {"item.goat_horn.play", "block.note_block.bass"},
            {"item.goat_horn.sound.0", "block.note_block.bass"},
            {"item.goat_horn.sound.1", "block.note_block.bass"},
            {"item.goat_horn.sound.2", "block.note_block.bass"},
            {"item.goat_horn.sound.3", "block.note_block.bass"},
            {"item.goat_horn.sound.4", "block.note_block.bass"},
            {"item.goat_horn.sound.5", "block.note_block.bass"},
            {"item.goat_horn.sound.6", "block.note_block.bass"},
            {"item.goat_horn.sound.7", "block.note_block.bass"},
            {"music.overworld.deep_dark", "music.overworld.dripstone_caves"},
            {"music.overworld.jungle_and_forest", "music.overworld.grove"},
            {"music.overworld.old_growth_taiga", "music.overworld.snowy_slopes"},
            {"music.overworld.swamp", "music.overworld.lush_caves"},
            {"music_disc.5", "music_disc.13"},
    };

    static final String[][] SOUND_NAME_FALLBACKS_757_TO_759 = {
    };

    static final int[][] ATTRIBUTE_757_RANGES = {
            {0, 12, 0},
    };

    static final int[][] ATTRIBUTE_757_OVERRIDES = {
    };
    // ATTRIBUTE_757: 759 entries=13 ranges=1 overrides=0

    static final int[][] ATTRIBUTE_REVERSE_757_RANGES = {
            {0, 12, 0},
    };

    static final int[][] ATTRIBUTE_REVERSE_757_OVERRIDES = {
    };
    // ATTRIBUTE_REVERSE_757: 757 entries=13 ranges=1 overrides=0

    static final String[] ATTRIBUTE_757_NAMES = {
            "generic.max_health", "generic.follow_range", "generic.knockback_resistance", "generic.movement_speed",
            "generic.flying_speed", "generic.attack_damage", "generic.attack_knockback", "generic.attack_speed",
            "generic.armor", "generic.armor_toughness", "generic.luck", "zombie.spawn_reinforcements",
            "horse.jump_strength",
    };

    static final int[][] ENCHANTMENT_757_RANGES = {
            {0, 11, 0}, {13, 38, -1},
    };

    static final int[][] ENCHANTMENT_757_OVERRIDES = {
            {12, -1},
    };
    // ENCHANTMENT_757: 759 entries=39 ranges=2 overrides=1

    static final int[][] ENCHANTMENT_REVERSE_757_RANGES = {
            {0, 11, 0}, {12, 37, 1},
    };

    static final int[][] ENCHANTMENT_REVERSE_757_OVERRIDES = {
    };
    // ENCHANTMENT_REVERSE_757: 757 entries=38 ranges=2 overrides=0

    static final int[][] BIOME_757_RANGES = {
            {0, 6, 0}, {7, 51, -1}, {53, 62, -2},
    };

    static final int[][] BIOME_757_OVERRIDES = {
            {52, 49},
    };
    // BIOME_757: 759 entries=63 ranges=3 overrides=1

    static final int[][] BIOME_757_TO_759_RANGES = {
            {0, 6, 0}, {7, 50, 1}, {51, 60, 2},
    };

    static final int[][] BIOME_757_TO_759_OVERRIDES = {
    };
    // BIOME_757_TO_759: 759 entries=61 ranges=3 overrides=0

    static final int[][] DAMAGE_TYPE_757_RANGES = {
            {0, 41, 0},
    };

    static final int[][] DAMAGE_TYPE_757_OVERRIDES = {
    };
    // DAMAGE_TYPE_757: 759 entries=42 ranges=1 overrides=0

    static final int[][] DAMAGE_TYPE_757_TO_759_RANGES = {
            {0, 41, 0},
    };

    static final int[][] DAMAGE_TYPE_757_TO_759_OVERRIDES = {
    };
    // DAMAGE_TYPE_757_TO_759: 759 entries=42 ranges=1 overrides=0

}
