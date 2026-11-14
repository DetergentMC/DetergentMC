package io.detergentmc.protocol;

/**
 * 775（26.1）のレジストリ対応表（区間＋上書きの生の表）。
 *
 * <p>{@link RegistryRemapper} から分離した。理由はバイトコードの制限：
 * 775 の表を RegistryRemapper に置くと静的初期化子（{@code <clinit>}）
 * が 64KB を超えてコンパイルできなくなる（"code too large"）ため。
 * このクラスの表は {@link RegistryRemapper} から static import で
 * 同じ名前のまま参照する（展開済み表 {@code *_775_MAP} やアクセサの
 * 書き方は変わらない）。
 *
 * <p>表の内容と検証は {@code detergentmc-protocol/tools/} を参照：
 * 生成は {@code generate-remap-tables.py --target 775}、
 * 検証は {@code verify-775-tables.py}。
 */
final class RegistryTables775 {

    private RegistryTables775() {
    }

    static final int[][] BLOCK_STATE_775_RANGES = {
            {0, 1380, 0}, {1381, 2121, 200}, {2122, 10440, 201}, {10441, 29670, 202},
    };

    static final int[][] BLOCK_STATE_775_OVERRIDES = {
    };
    // BLOCK_STATE_775: 765 entries=29671 ranges=4 overrides=0

    static final int[][] ITEM_775_RANGES = {
            {0, 229, 0}, {230, 1504, 1},
    };

    static final int[][] ITEM_775_OVERRIDES = {
    };
    // ITEM_775: 765 entries=1505 ranges=2 overrides=0

    static final int[][] ENTITY_TYPE_775_RANGES = {
            {0, 156, 0},
    };

    static final int[][] ENTITY_TYPE_775_OVERRIDES = {
    };
    // ENTITY_TYPE_775: 765 entries=157 ranges=1 overrides=0

    static final int[][] SOUND_775_RANGES = {
            {0, 276, 0}, {277, 278, 9}, {279, 280, 11}, {284, 285, 9}, {286, 329, 18}, {335, 421, 25},
            {427, 721, 29}, {722, 816, 31}, {818, 820, 33}, {823, 824, 36}, {825, 826, 37}, {827, 828, 38},
            {829, 1127, 39}, {1128, 1213, 43}, {1217, 1218, 40}, {1219, 1723, 57}, {1724, 1726, 58}, {1727, 1728, 62},
            {1729, 1837, 64},
    };

    static final int[][] SOUND_775_OVERRIDES = {
            {281, 288}, {282, 292}, {283, 289}, {330, 354}, {331, 356}, {332, 350}, {333, 355}, {334, 352},
            {422, 448}, {423, 450}, {424, 449}, {425, 447}, {426, 451}, {817, 849}, {821, 855}, {822, 857},
            {1214, 1264}, {1215, 1266}, {1216, 1265},
    };
    // SOUND_775: 765 entries=1838 ranges=19 overrides=19

    static final int[][] PARTICLE_775_RANGES = {
            {0, 49, 0}, {50, 114, 2},
    };

    static final int[][] PARTICLE_775_OVERRIDES = {
    };
    // PARTICLE_775: 765 entries=115 ranges=2 overrides=0

    static final int[][] BLOCK_775_RANGES = {
            {0, 157, 0}, {158, 422, 1}, {423, 1165, 2},
    };

    static final int[][] BLOCK_775_OVERRIDES = {
    };
    // BLOCK_775: 765 entries=1166 ranges=3 overrides=0

    static final int[][] MENU_775_RANGES = {
            {0, 24, 0},
    };

    static final int[][] MENU_775_OVERRIDES = {
    };
    // MENU_775: 765 entries=25 ranges=1 overrides=0

    static final int[][] ITEM_REVERSE_775_RANGES = {
            {0, 229, 0}, {231, 1505, -1},
    };

    static final int[][] ITEM_REVERSE_775_OVERRIDES = {
            {230, 1},
    };
    // ITEM_REVERSE_775: 775 entries=1506 ranges=2 overrides=1

    static final int[][] BLOCK_STATE_774_TO_775_RANGES = {
            {0, 1380, 0}, {1381, 2121, 200}, {2122, 10440, 201}, {10441, 29670, 202},
    };

    static final int[][] BLOCK_STATE_774_TO_775_OVERRIDES = {
    };
    // BLOCK_STATE_774_TO_775: entries=29671 ranges=4 overrides=0

    static final int[][] ITEM_774_TO_775_RANGES = {
            {0, 229, 0}, {230, 1504, 1},
    };

    static final int[][] ITEM_774_TO_775_OVERRIDES = {
    };
    // ITEM_774_TO_775: entries=1505 ranges=2 overrides=0

    static final int[][] ENTITY_TYPE_774_TO_775_RANGES = {
            {0, 156, 0},
    };

    static final int[][] ENTITY_TYPE_774_TO_775_OVERRIDES = {
    };
    // ENTITY_TYPE_774_TO_775: entries=157 ranges=1 overrides=0

    static final int[][] SOUND_774_TO_775_RANGES = {
            {0, 276, 0}, {277, 278, 9}, {279, 280, 11}, {284, 285, 9}, {286, 329, 18}, {335, 421, 25},
            {427, 721, 29}, {722, 816, 31}, {818, 820, 33}, {823, 824, 36}, {825, 826, 37}, {827, 828, 38},
            {829, 1127, 39}, {1128, 1213, 43}, {1217, 1218, 40}, {1219, 1723, 57}, {1724, 1726, 58}, {1727, 1728, 62},
            {1729, 1837, 64},
    };

    static final int[][] SOUND_774_TO_775_OVERRIDES = {
            {281, 288}, {282, 292}, {283, 289}, {330, 354}, {331, 356}, {332, 350}, {333, 355}, {334, 352},
            {422, 448}, {423, 450}, {424, 449}, {425, 447}, {426, 451}, {817, 849}, {821, 855}, {822, 857},
            {1214, 1264}, {1215, 1266}, {1216, 1265},
    };
    // SOUND_774_TO_775: entries=1838 ranges=19 overrides=19

    static final int[][] PARTICLE_774_TO_775_RANGES = {
            {0, 49, 0}, {50, 114, 2},
    };

    static final int[][] PARTICLE_774_TO_775_OVERRIDES = {
    };
    // PARTICLE_774_TO_775: entries=115 ranges=2 overrides=0

    static final int[][] BLOCK_774_TO_775_RANGES = {
            {0, 157, 0}, {158, 422, 1}, {423, 1165, 2},
    };

    static final int[][] BLOCK_774_TO_775_OVERRIDES = {
    };
    // BLOCK_774_TO_775: entries=1166 ranges=3 overrides=0

    static final int[][] MENU_774_TO_775_RANGES = {
            {0, 24, 0},
    };

    static final int[][] MENU_774_TO_775_OVERRIDES = {
    };
    // MENU_774_TO_775: entries=25 ranges=1 overrides=0

    static final int[][] BLOCK_STATE_775_TO_774_RANGES = {
            {0, 1380, 0}, {1581, 2321, -200}, {2323, 10641, -201}, {10643, 29872, -202},
    };

    static final int[][] BLOCK_STATE_775_TO_774_OVERRIDES = {
            {1381, 1}, {1382, 1}, {1383, 1}, {1384, 1}, {1385, 1}, {1386, 1}, {1387, 1}, {1388, 1},
            {1389, 1}, {1390, 1}, {1391, 1}, {1392, 1}, {1393, 1}, {1394, 1}, {1395, 1}, {1396, 1},
            {1397, 1}, {1398, 1}, {1399, 1}, {1400, 1}, {1401, 1}, {1402, 1}, {1403, 1}, {1404, 1},
            {1405, 1}, {1406, 1}, {1407, 1}, {1408, 1}, {1409, 1}, {1410, 1}, {1411, 1}, {1412, 1},
            {1413, 1}, {1414, 1}, {1415, 1}, {1416, 1}, {1417, 1}, {1418, 1}, {1419, 1}, {1420, 1},
            {1421, 1}, {1422, 1}, {1423, 1}, {1424, 1}, {1425, 1}, {1426, 1}, {1427, 1}, {1428, 1},
            {1429, 1}, {1430, 1}, {1431, 1}, {1432, 1}, {1433, 1}, {1434, 1}, {1435, 1}, {1436, 1},
            {1437, 1}, {1438, 1}, {1439, 1}, {1440, 1}, {1441, 1}, {1442, 1}, {1443, 1}, {1444, 1},
            {1445, 1}, {1446, 1}, {1447, 1}, {1448, 1}, {1449, 1}, {1450, 1}, {1451, 1}, {1452, 1},
            {1453, 1}, {1454, 1}, {1455, 1}, {1456, 1}, {1457, 1}, {1458, 1}, {1459, 1}, {1460, 1},
            {1461, 1}, {1462, 1}, {1463, 1}, {1464, 1}, {1465, 1}, {1466, 1}, {1467, 1}, {1468, 1},
            {1469, 1}, {1470, 1}, {1471, 1}, {1472, 1}, {1473, 1}, {1474, 1}, {1475, 1}, {1476, 1},
            {1477, 1}, {1478, 1}, {1479, 1}, {1480, 1}, {1481, 1}, {1482, 1}, {1483, 1}, {1484, 1},
            {1485, 1}, {1486, 1}, {1487, 1}, {1488, 1}, {1489, 1}, {1490, 1}, {1491, 1}, {1492, 1},
            {1493, 1}, {1494, 1}, {1495, 1}, {1496, 1}, {1497, 1}, {1498, 1}, {1499, 1}, {1500, 1},
            {1501, 1}, {1502, 1}, {1503, 1}, {1504, 1}, {1505, 1}, {1506, 1}, {1507, 1}, {1508, 1},
            {1509, 1}, {1510, 1}, {1511, 1}, {1512, 1}, {1513, 1}, {1514, 1}, {1515, 1}, {1516, 1},
            {1517, 1}, {1518, 1}, {1519, 1}, {1520, 1}, {1521, 1}, {1522, 1}, {1523, 1}, {1524, 1},
            {1525, 1}, {1526, 1}, {1527, 1}, {1528, 1}, {1529, 1}, {1530, 1}, {1531, 1}, {1532, 1},
            {1533, 1}, {1534, 1}, {1535, 1}, {1536, 1}, {1537, 1}, {1538, 1}, {1539, 1}, {1540, 1},
            {1541, 1}, {1542, 1}, {1543, 1}, {1544, 1}, {1545, 1}, {1546, 1}, {1547, 1}, {1548, 1},
            {1549, 1}, {1550, 1}, {1551, 1}, {1552, 1}, {1553, 1}, {1554, 1}, {1555, 1}, {1556, 1},
            {1557, 1}, {1558, 1}, {1559, 1}, {1560, 1}, {1561, 1}, {1562, 1}, {1563, 1}, {1564, 1},
            {1565, 1}, {1566, 1}, {1567, 1}, {1568, 1}, {1569, 1}, {1570, 1}, {1571, 1}, {1572, 1},
            {1573, 1}, {1574, 1}, {1575, 1}, {1576, 1}, {1577, 1}, {1578, 1}, {1579, 1}, {1580, 1},
            {2322, 1}, {10642, 1},
    };
    // BLOCK_STATE_775_TO_774: entries=29873 ranges=4 overrides=202

    static final int[][] ITEM_775_TO_774_RANGES = {
            {0, 229, 0}, {231, 1505, -1},
    };

    static final int[][] ITEM_775_TO_774_OVERRIDES = {
            {230, 1},
    };
    // ITEM_775_TO_774: entries=1506 ranges=2 overrides=1

    static final int[][] ENTITY_TYPE_775_TO_774_RANGES = {
            {0, 156, 0},
    };

    static final int[][] ENTITY_TYPE_775_TO_774_OVERRIDES = {
    };
    // ENTITY_TYPE_775_TO_774: entries=157 ranges=1 overrides=0

    static final int[][] SOUND_775_TO_774_RANGES = {
            {0, 276, 0}, {286, 287, -9}, {290, 291, -11}, {293, 294, -9}, {304, 347, -18}, {360, 446, -25},
            {456, 750, -29}, {753, 847, -31}, {851, 853, -33}, {859, 860, -36}, {862, 863, -37}, {865, 866, -38},
            {868, 1166, -39}, {1171, 1256, -43}, {1257, 1258, -40}, {1276, 1780, -57}, {1782, 1784, -58}, {1789, 1790, -62},
            {1793, 1901, -64},
    };

    static final int[][] SOUND_775_TO_774_OVERRIDES = {
            {277, 1535}, {278, 1535}, {279, 1535}, {280, 1535}, {281, 1535}, {282, 1535}, {283, 1535}, {284, 1535},
            {285, 1535}, {288, 281}, {289, 283}, {292, 282}, {295, 1535}, {296, 1535}, {297, 1535}, {298, 1535},
            {299, 1535}, {300, 1535}, {301, 1535}, {302, 1535}, {303, 1535}, {348, 1535}, {349, 1535}, {350, 332},
            {351, 1535}, {352, 334}, {353, 1535}, {354, 330}, {355, 333}, {356, 331}, {357, 1535}, {358, 1535},
            {359, 1535}, {447, 425}, {448, 422}, {449, 424}, {450, 423}, {451, 426}, {452, 1535}, {453, 1535},
            {454, 1535}, {455, 1535}, {751, 1535}, {752, 1535}, {848, 1535}, {849, 817}, {850, 1535}, {854, 1535},
            {855, 821}, {856, 1535}, {857, 822}, {858, 1535}, {861, 1535}, {864, 1535}, {867, 1535}, {1167, 1535},
            {1168, 1535}, {1169, 1535}, {1170, 1535}, {1259, 1535}, {1260, 1535}, {1261, 1535}, {1262, 1535}, {1263, 1535},
            {1264, 1214}, {1265, 1216}, {1266, 1215}, {1267, 1535}, {1268, 1535}, {1269, 1535}, {1270, 1535}, {1271, 1535},
            {1272, 1535}, {1273, 1535}, {1274, 1535}, {1275, 1535}, {1781, 1535}, {1785, 1535}, {1786, 1535}, {1787, 1535},
            {1788, 1535}, {1791, 1535}, {1792, 1535},
    };
    // SOUND_775_TO_774: entries=1902 ranges=19 overrides=83

    static final int[][] PARTICLE_775_TO_774_RANGES = {
            {0, 49, 0}, {52, 116, -2},
    };

    static final int[][] PARTICLE_775_TO_774_OVERRIDES = {
            {50, 21}, {51, 21},
    };
    // PARTICLE_775_TO_774: entries=117 ranges=2 overrides=2

    static final int[][] BLOCK_775_TO_774_RANGES = {
            {0, 157, 0}, {159, 423, -1}, {425, 1167, -2},
    };

    static final int[][] BLOCK_775_TO_774_OVERRIDES = {
            {158, 1}, {424, 1},
    };
    // BLOCK_775_TO_774: entries=1168 ranges=3 overrides=2

    static final int[][] MENU_775_TO_774_RANGES = {
            {0, 24, 0},
    };

    static final int[][] MENU_775_TO_774_OVERRIDES = {
    };
    // MENU_775_TO_774: entries=25 ranges=1 overrides=0

    static final int[][] ATTRIBUTE_775_RANGES = {
            {0, 34, 0},
    };

    static final int[][] ATTRIBUTE_775_OVERRIDES = {
    };
    // ATTRIBUTE_775: 774 entries=35 ranges=1 overrides=0

    static final int[][] ATTRIBUTE_REVERSE_775_RANGES = {
            {0, 34, 0},
    };

    static final int[][] ATTRIBUTE_REVERSE_775_OVERRIDES = {
    };
    // ATTRIBUTE_REVERSE_775: 775 entries=35 ranges=1 overrides=0

    static final String[] ATTRIBUTE_775_NAMES = {
            "generic.armor", "generic.armor_toughness", "generic.attack_damage", "generic.attack_knockback",
            "generic.attack_speed", "player.block_break_speed", "player.block_interaction_range", "burning_time",
            "camera_distance", "explosion_knockback_resistance", "player.entity_interaction_range", "generic.fall_damage_multiplier",
            "generic.flying_speed", "generic.follow_range", "generic.gravity", "generic.jump_strength",
            "generic.knockback_resistance", "generic.luck", "generic.max_absorption", "generic.max_health",
            "mining_efficiency", "movement_efficiency", "generic.movement_speed", "oxygen_bonus",
            "generic.safe_fall_distance", "generic.scale", "sneaking_speed", "zombie.spawn_reinforcements",
            "generic.step_height", "submerged_mining_speed", "sweeping_damage_ratio", "tempt_range",
            "water_movement_efficiency", "waypoint_transmit_range", "waypoint_receive_range",
    };

    static final int[][] ENCHANTMENT_775_RANGES = {
            {0, 42, 0},
    };

    static final int[][] ENCHANTMENT_775_OVERRIDES = {
    };
    // ENCHANTMENT_775: 774 entries=43 ranges=1 overrides=0

    static final int[][] ENCHANTMENT_REVERSE_775_RANGES = {
            {0, 42, 0},
    };

    static final int[][] ENCHANTMENT_REVERSE_775_OVERRIDES = {
    };
    // ENCHANTMENT_REVERSE_775: 775 entries=43 ranges=1 overrides=0

}
