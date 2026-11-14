package io.detergentmc.protocol;

import static io.detergentmc.protocol.RegistryRemapper.*;
import static io.detergentmc.protocol.RegistryTables774.*;
import static io.detergentmc.protocol.RegistryTables774ComposedUp.*;
import static io.detergentmc.protocol.RegistryTables774Down763.*;
import static io.detergentmc.protocol.RegistryTables774Down763BlockState.*;
import static io.detergentmc.protocol.RegistryTables774Down764.*;
import static io.detergentmc.protocol.RegistryTables774Down764BlockState.*;
import static io.detergentmc.protocol.RegistryTables774Down765.*;
import static io.detergentmc.protocol.RegistryTables774Down765BlockState.*;
import static io.detergentmc.protocol.RegistryTables774Down766.*;
import static io.detergentmc.protocol.RegistryTables774Down766BlockState.*;
import static io.detergentmc.protocol.RegistryTables774Down767.*;
import static io.detergentmc.protocol.RegistryTables774Down767BlockState.*;
import static io.detergentmc.protocol.RegistryTables774Down768.*;
import static io.detergentmc.protocol.RegistryTables774Down768BlockState.*;
import static io.detergentmc.protocol.RegistryTables774Down769.*;
import static io.detergentmc.protocol.RegistryTables774Down769BlockState.*;
import static io.detergentmc.protocol.RegistryTables774Down770.*;
import static io.detergentmc.protocol.RegistryTables774Down770BlockState.*;
import static io.detergentmc.protocol.RegistryTables774Down771.*;
import static io.detergentmc.protocol.RegistryTables774Down771BlockState.*;
import static io.detergentmc.protocol.RegistryTables774Down772.*;
import static io.detergentmc.protocol.RegistryTables774Down772BlockState.*;

/**
 * 774 の展開済み表（隣接表 {@code *_774_MAP}・合成表 {@code *_X_TO_774_MAP}／
 * {@code *_774_TO_X_MAP}・反転表 {@code *_UP_774_MAP}）。
 *
 * <p>中身は RegistryTables774*.java の RANGES／OVERRIDES を
 * {@link RegistryRemapper} の expand()／invert() で展開したもので、773 までと
 * 同じ作り方。置き場所だけ別クラスにしているのは、RegistryRemapper.java の
 * {@code <clinit>} が 64KB 制限に迫ったため。774 の 158 フィールド分を入れた
 * 時点で 61,817 バイト（残り 3,718 バイト）まで膨らんでいたので、次の版で
 * 制限を越える前に移してある。
 */
final class RegistryTables774Expanded {

    static final int[] BLOCK_STATE_774_MAP =
            expand(BLOCK_STATE_774_RANGES, BLOCK_STATE_774_OVERRIDES, MAX_BLOCK_STATE_773 + 1,
                    STONE_BLOCK_STATE_774);
    static final int[] ITEM_774_MAP =
            expand(ITEM_774_RANGES, ITEM_774_OVERRIDES, MAX_ITEM_773 + 1,
                    STONE_ITEM_774);
    static final int[] ENTITY_TYPE_774_MAP =
            expand(ENTITY_TYPE_774_RANGES, ENTITY_TYPE_774_OVERRIDES, MAX_ENTITY_TYPE_773 + 1,
                    PIG_ENTITY_TYPE_774);
    static final int[] SOUND_774_MAP =
            expand(SOUND_774_RANGES, SOUND_774_OVERRIDES, MAX_SOUND_773 + 1,
                    GENERIC_SOUND_774);
    static final int[] PARTICLE_774_MAP =
            expand(PARTICLE_774_RANGES, PARTICLE_774_OVERRIDES, MAX_PARTICLE_773 + 1,
                    ENTITY_EFFECT_PARTICLE_774);
    static final int[] BLOCK_774_MAP =
            expand(BLOCK_774_RANGES, BLOCK_774_OVERRIDES, MAX_BLOCK_773 + 1,
                    STONE_BLOCK_774);
    static final int[] MENU_774_MAP =
            expand(MENU_774_RANGES, MENU_774_OVERRIDES, MAX_MENU_773 + 1,
                    CRAFTING_MENU_774);
    static final int[] ITEM_REVERSE_774_MAP =
            expand(ITEM_REVERSE_774_RANGES, ITEM_REVERSE_774_OVERRIDES, 1505, -1);
    static final int[] ATTRIBUTE_774_MAP =
            expand(ATTRIBUTE_774_RANGES, ATTRIBUTE_774_OVERRIDES, MAX_ATTRIBUTE_773 + 1, DROP);
    static final int[] ATTRIBUTE_REVERSE_774_MAP =
            expand(ATTRIBUTE_REVERSE_774_RANGES, ATTRIBUTE_REVERSE_774_OVERRIDES,
                    MAX_ATTRIBUTE_774 + 1, DROP);
    static final int[] ENCHANTMENT_774_MAP =
            expand(ENCHANTMENT_774_RANGES, ENCHANTMENT_774_OVERRIDES,
                    MAX_ENCHANTMENT_773 + 1, DROP);
    static final int[] ENCHANTMENT_REVERSE_774_MAP =
            expand(ENCHANTMENT_REVERSE_774_RANGES, ENCHANTMENT_REVERSE_774_OVERRIDES,
                    MAX_ENCHANTMENT_774 + 1, DROP);
    static final int[] BLOCK_STATE_763_TO_774_MAP =
            expand(BLOCK_STATE_763_TO_774_RANGES, BLOCK_STATE_763_TO_774_OVERRIDES,
                    24135, STONE_BLOCK_STATE_774);
    static final int[] ITEM_763_TO_774_MAP =
            expand(ITEM_763_TO_774_RANGES, ITEM_763_TO_774_OVERRIDES,
                    1255, STONE_ITEM_774);
    static final int[] ENTITY_TYPE_763_TO_774_MAP =
            expand(ENTITY_TYPE_763_TO_774_RANGES, ENTITY_TYPE_763_TO_774_OVERRIDES,
                    124, PIG_ENTITY_TYPE_774);
    static final int[] SOUND_763_TO_774_MAP =
            expand(SOUND_763_TO_774_RANGES, SOUND_763_TO_774_OVERRIDES,
                    1474, GENERIC_SOUND_774);
    static final int[] PARTICLE_763_TO_774_MAP =
            expand(PARTICLE_763_TO_774_RANGES, PARTICLE_763_TO_774_OVERRIDES,
                    95, ENTITY_EFFECT_PARTICLE_774);
    static final int[] BLOCK_763_TO_774_MAP =
            expand(BLOCK_763_TO_774_RANGES, BLOCK_763_TO_774_OVERRIDES,
                    1003, STONE_BLOCK_774);
    static final int[] MENU_763_TO_774_MAP =
            expand(MENU_763_TO_774_RANGES, MENU_763_TO_774_OVERRIDES,
                    24, CRAFTING_MENU_774);
    static final int[] BLOCK_STATE_774_TO_763_MAP =
            expand(BLOCK_STATE_774_TO_763_RANGES, BLOCK_STATE_774_TO_763_OVERRIDES,
                    29671, STONE_BLOCK_STATE_763);
    static final int[] ITEM_774_TO_763_MAP =
            expand(ITEM_774_TO_763_RANGES, ITEM_774_TO_763_OVERRIDES,
                    1505, STONE_ITEM_763);
    static final int[] ENTITY_TYPE_774_TO_763_MAP =
            expand(ENTITY_TYPE_774_TO_763_RANGES, ENTITY_TYPE_774_TO_763_OVERRIDES,
                    157, PIG_ENTITY_TYPE_763);
    static final int[] SOUND_774_TO_763_MAP =
            expand(SOUND_774_TO_763_RANGES, SOUND_774_TO_763_OVERRIDES,
                    1838, GENERIC_SOUND_763);
    static final int[] PARTICLE_774_TO_763_MAP =
            expand(PARTICLE_774_TO_763_RANGES, PARTICLE_774_TO_763_OVERRIDES,
                    115, AMBIENT_PARTICLE_763);
    static final int[] BLOCK_774_TO_763_MAP =
            expand(BLOCK_774_TO_763_RANGES, BLOCK_774_TO_763_OVERRIDES,
                    1166, STONE_BLOCK_763);
    static final int[] MENU_774_TO_763_MAP =
            expand(MENU_774_TO_763_RANGES, MENU_774_TO_763_OVERRIDES,
                    25, CRAFTING_MENU_763);
    static final int[] BLOCK_STATE_764_TO_774_MAP =
            expand(BLOCK_STATE_764_TO_774_RANGES, BLOCK_STATE_764_TO_774_OVERRIDES,
                    24276, STONE_BLOCK_STATE_774);
    static final int[] ITEM_764_TO_774_MAP =
            expand(ITEM_764_TO_774_RANGES, ITEM_764_TO_774_OVERRIDES,
                    1255, STONE_ITEM_774);
    static final int[] ENTITY_TYPE_764_TO_774_MAP =
            expand(ENTITY_TYPE_764_TO_774_RANGES, ENTITY_TYPE_764_TO_774_OVERRIDES,
                    124, PIG_ENTITY_TYPE_774);
    static final int[] SOUND_764_TO_774_MAP =
            expand(SOUND_764_TO_774_RANGES, SOUND_764_TO_774_OVERRIDES,
                    1485, GENERIC_SOUND_774);
    static final int[] PARTICLE_764_TO_774_MAP =
            expand(PARTICLE_764_TO_774_RANGES, PARTICLE_764_TO_774_OVERRIDES,
                    95, ENTITY_EFFECT_PARTICLE_774);
    static final int[] BLOCK_764_TO_774_MAP =
            expand(BLOCK_764_TO_774_RANGES, BLOCK_764_TO_774_OVERRIDES,
                    1003, STONE_BLOCK_774);
    static final int[] MENU_764_TO_774_MAP =
            expand(MENU_764_TO_774_RANGES, MENU_764_TO_774_OVERRIDES,
                    24, CRAFTING_MENU_774);
    static final int[] BLOCK_STATE_774_TO_764_MAP =
            expand(BLOCK_STATE_774_TO_764_RANGES, BLOCK_STATE_774_TO_764_OVERRIDES,
                    29671, STONE_BLOCK_STATE_764);
    static final int[] ITEM_774_TO_764_MAP =
            expand(ITEM_774_TO_764_RANGES, ITEM_774_TO_764_OVERRIDES,
                    1505, STONE_ITEM_764);
    static final int[] ENTITY_TYPE_774_TO_764_MAP =
            expand(ENTITY_TYPE_774_TO_764_RANGES, ENTITY_TYPE_774_TO_764_OVERRIDES,
                    157, PIG_ENTITY_TYPE_764);
    static final int[] SOUND_774_TO_764_MAP =
            expand(SOUND_774_TO_764_RANGES, SOUND_774_TO_764_OVERRIDES,
                    1838, GENERIC_SOUND_764);
    static final int[] PARTICLE_774_TO_764_MAP =
            expand(PARTICLE_774_TO_764_RANGES, PARTICLE_774_TO_764_OVERRIDES,
                    115, AMBIENT_PARTICLE_764);
    static final int[] BLOCK_774_TO_764_MAP =
            expand(BLOCK_774_TO_764_RANGES, BLOCK_774_TO_764_OVERRIDES,
                    1166, STONE_BLOCK_764);
    static final int[] MENU_774_TO_764_MAP =
            expand(MENU_774_TO_764_RANGES, MENU_774_TO_764_OVERRIDES,
                    25, CRAFTING_MENU_764);
    static final int[] BLOCK_STATE_765_TO_774_MAP =
            expand(BLOCK_STATE_765_TO_774_RANGES, BLOCK_STATE_765_TO_774_OVERRIDES,
                    26644, STONE_BLOCK_STATE_774);
    static final int[] ITEM_765_TO_774_MAP =
            expand(ITEM_765_TO_774_RANGES, ITEM_765_TO_774_OVERRIDES,
                    1312, STONE_ITEM_774);
    static final int[] ENTITY_TYPE_765_TO_774_MAP =
            expand(ENTITY_TYPE_765_TO_774_RANGES, ENTITY_TYPE_765_TO_774_OVERRIDES,
                    126, PIG_ENTITY_TYPE_774);
    static final int[] SOUND_765_TO_774_MAP =
            expand(SOUND_765_TO_774_RANGES, SOUND_765_TO_774_OVERRIDES,
                    1539, GENERIC_SOUND_774);
    static final int[] PARTICLE_765_TO_774_MAP =
            expand(PARTICLE_765_TO_774_RANGES, PARTICLE_765_TO_774_OVERRIDES,
                    101, ENTITY_EFFECT_PARTICLE_774);
    static final int[] BLOCK_765_TO_774_MAP =
            expand(BLOCK_765_TO_774_RANGES, BLOCK_765_TO_774_OVERRIDES,
                    1058, STONE_BLOCK_774);
    static final int[] MENU_765_TO_774_MAP =
            expand(MENU_765_TO_774_RANGES, MENU_765_TO_774_OVERRIDES,
                    25, CRAFTING_MENU_774);
    static final int[] BLOCK_STATE_774_TO_765_MAP =
            expand(BLOCK_STATE_774_TO_765_RANGES, BLOCK_STATE_774_TO_765_OVERRIDES,
                    29671, STONE_BLOCK_STATE_765);
    static final int[] ITEM_774_TO_765_MAP =
            expand(ITEM_774_TO_765_RANGES, ITEM_774_TO_765_OVERRIDES,
                    1505, STONE_ITEM_765);
    static final int[] ENTITY_TYPE_774_TO_765_MAP =
            expand(ENTITY_TYPE_774_TO_765_RANGES, ENTITY_TYPE_774_TO_765_OVERRIDES,
                    157, PIG_ENTITY_TYPE_765);
    static final int[] SOUND_774_TO_765_MAP =
            expand(SOUND_774_TO_765_RANGES, SOUND_774_TO_765_OVERRIDES,
                    1838, GENERIC_SOUND_765);
    static final int[] PARTICLE_774_TO_765_MAP =
            expand(PARTICLE_774_TO_765_RANGES, PARTICLE_774_TO_765_OVERRIDES,
                    115, AMBIENT_PARTICLE_765);
    static final int[] BLOCK_774_TO_765_MAP =
            expand(BLOCK_774_TO_765_RANGES, BLOCK_774_TO_765_OVERRIDES,
                    1166, STONE_BLOCK_765);
    static final int[] MENU_774_TO_765_MAP =
            expand(MENU_774_TO_765_RANGES, MENU_774_TO_765_OVERRIDES,
                    25, CRAFTING_MENU_765);
    static final int[] BLOCK_STATE_766_TO_774_MAP =
            expand(BLOCK_STATE_766_TO_774_RANGES, BLOCK_STATE_766_TO_774_OVERRIDES,
                    26684, STONE_BLOCK_STATE_774);
    static final int[] ITEM_766_TO_774_MAP =
            expand(ITEM_766_TO_774_RANGES, ITEM_766_TO_774_OVERRIDES,
                    1330, STONE_ITEM_774);
    static final int[] ENTITY_TYPE_766_TO_774_MAP =
            expand(ENTITY_TYPE_766_TO_774_RANGES, ENTITY_TYPE_766_TO_774_OVERRIDES,
                    130, PIG_ENTITY_TYPE_774);
    static final int[] SOUND_766_TO_774_MAP =
            expand(SOUND_766_TO_774_RANGES, SOUND_766_TO_774_OVERRIDES,
                    1607, GENERIC_SOUND_774);
    static final int[] PARTICLE_766_TO_774_MAP =
            expand(PARTICLE_766_TO_774_RANGES, PARTICLE_766_TO_774_OVERRIDES,
                    109, ENTITY_EFFECT_PARTICLE_774);
    static final int[] BLOCK_766_TO_774_MAP =
            expand(BLOCK_766_TO_774_RANGES, BLOCK_766_TO_774_OVERRIDES,
                    1060, STONE_BLOCK_774);
    static final int[] MENU_766_TO_774_MAP =
            expand(MENU_766_TO_774_RANGES, MENU_766_TO_774_OVERRIDES,
                    25, CRAFTING_MENU_774);
    static final int[] BLOCK_STATE_774_TO_766_MAP =
            expand(BLOCK_STATE_774_TO_766_RANGES, BLOCK_STATE_774_TO_766_OVERRIDES,
                    29671, STONE_BLOCK_STATE_766);
    static final int[] ITEM_774_TO_766_MAP =
            expand(ITEM_774_TO_766_RANGES, ITEM_774_TO_766_OVERRIDES,
                    1505, STONE_ITEM_766);
    static final int[] ENTITY_TYPE_774_TO_766_MAP =
            expand(ENTITY_TYPE_774_TO_766_RANGES, ENTITY_TYPE_774_TO_766_OVERRIDES,
                    157, PIG_ENTITY_TYPE_766);
    static final int[] SOUND_774_TO_766_MAP =
            expand(SOUND_774_TO_766_RANGES, SOUND_774_TO_766_OVERRIDES,
                    1838, GENERIC_SOUND_766);
    static final int[] PARTICLE_774_TO_766_MAP =
            expand(PARTICLE_774_TO_766_RANGES, PARTICLE_774_TO_766_OVERRIDES,
                    115, ENTITY_EFFECT_PARTICLE_766);
    static final int[] BLOCK_774_TO_766_MAP =
            expand(BLOCK_774_TO_766_RANGES, BLOCK_774_TO_766_OVERRIDES,
                    1166, STONE_BLOCK_766);
    static final int[] MENU_774_TO_766_MAP =
            expand(MENU_774_TO_766_RANGES, MENU_774_TO_766_OVERRIDES,
                    25, CRAFTING_MENU_766);
    static final int[] BLOCK_STATE_767_TO_774_MAP =
            expand(BLOCK_STATE_767_TO_774_RANGES, BLOCK_STATE_767_TO_774_OVERRIDES,
                    26684, STONE_BLOCK_STATE_774);
    static final int[] ITEM_767_TO_774_MAP =
            expand(ITEM_767_TO_774_RANGES, ITEM_767_TO_774_OVERRIDES,
                    1333, STONE_ITEM_774);
    static final int[] ENTITY_TYPE_767_TO_774_MAP =
            expand(ENTITY_TYPE_767_TO_774_RANGES, ENTITY_TYPE_767_TO_774_OVERRIDES,
                    130, PIG_ENTITY_TYPE_774);
    static final int[] SOUND_767_TO_774_MAP =
            expand(SOUND_767_TO_774_RANGES, SOUND_767_TO_774_OVERRIDES,
                    1611, GENERIC_SOUND_774);
    static final int[] PARTICLE_767_TO_774_MAP =
            expand(PARTICLE_767_TO_774_RANGES, PARTICLE_767_TO_774_OVERRIDES,
                    109, ENTITY_EFFECT_PARTICLE_774);
    static final int[] BLOCK_767_TO_774_MAP =
            expand(BLOCK_767_TO_774_RANGES, BLOCK_767_TO_774_OVERRIDES,
                    1060, STONE_BLOCK_774);
    static final int[] MENU_767_TO_774_MAP =
            expand(MENU_767_TO_774_RANGES, MENU_767_TO_774_OVERRIDES,
                    25, CRAFTING_MENU_774);
    static final int[] BLOCK_STATE_774_TO_767_MAP =
            expand(BLOCK_STATE_774_TO_767_RANGES, BLOCK_STATE_774_TO_767_OVERRIDES,
                    29671, STONE_BLOCK_STATE_767);
    static final int[] ITEM_774_TO_767_MAP =
            expand(ITEM_774_TO_767_RANGES, ITEM_774_TO_767_OVERRIDES,
                    1505, STONE_ITEM_767);
    static final int[] ENTITY_TYPE_774_TO_767_MAP =
            expand(ENTITY_TYPE_774_TO_767_RANGES, ENTITY_TYPE_774_TO_767_OVERRIDES,
                    157, PIG_ENTITY_TYPE_767);
    static final int[] SOUND_774_TO_767_MAP =
            expand(SOUND_774_TO_767_RANGES, SOUND_774_TO_767_OVERRIDES,
                    1838, GENERIC_SOUND_767);
    static final int[] PARTICLE_774_TO_767_MAP =
            expand(PARTICLE_774_TO_767_RANGES, PARTICLE_774_TO_767_OVERRIDES,
                    115, ENTITY_EFFECT_PARTICLE_767);
    static final int[] BLOCK_774_TO_767_MAP =
            expand(BLOCK_774_TO_767_RANGES, BLOCK_774_TO_767_OVERRIDES,
                    1166, STONE_BLOCK_767);
    static final int[] MENU_774_TO_767_MAP =
            expand(MENU_774_TO_767_RANGES, MENU_774_TO_767_OVERRIDES,
                    25, CRAFTING_MENU_767);
    static final int[] BLOCK_STATE_768_TO_774_MAP =
            expand(BLOCK_STATE_768_TO_774_RANGES, BLOCK_STATE_768_TO_774_OVERRIDES,
                    27318, STONE_BLOCK_STATE_774);
    static final int[] ITEM_768_TO_774_MAP =
            expand(ITEM_768_TO_774_RANGES, ITEM_768_TO_774_OVERRIDES,
                    1375, STONE_ITEM_774);
    static final int[] ENTITY_TYPE_768_TO_774_MAP =
            expand(ENTITY_TYPE_768_TO_774_RANGES, ENTITY_TYPE_768_TO_774_OVERRIDES,
                    150, PIG_ENTITY_TYPE_774);
    static final int[] SOUND_768_TO_774_MAP =
            expand(SOUND_768_TO_774_RANGES, SOUND_768_TO_774_OVERRIDES,
                    1636, GENERIC_SOUND_774);
    static final int[] PARTICLE_768_TO_774_MAP =
            expand(PARTICLE_768_TO_774_RANGES, PARTICLE_768_TO_774_OVERRIDES,
                    111, ENTITY_EFFECT_PARTICLE_774);
    static final int[] BLOCK_768_TO_774_MAP =
            expand(BLOCK_768_TO_774_RANGES, BLOCK_768_TO_774_OVERRIDES,
                    1084, STONE_BLOCK_774);
    static final int[] MENU_768_TO_774_MAP =
            expand(MENU_768_TO_774_RANGES, MENU_768_TO_774_OVERRIDES,
                    25, CRAFTING_MENU_774);
    static final int[] BLOCK_STATE_774_TO_768_MAP =
            expand(BLOCK_STATE_774_TO_768_RANGES, BLOCK_STATE_774_TO_768_OVERRIDES,
                    29671, STONE_BLOCK_STATE_768);
    static final int[] ITEM_774_TO_768_MAP =
            expand(ITEM_774_TO_768_RANGES, ITEM_774_TO_768_OVERRIDES,
                    1505, STONE_ITEM_768);
    static final int[] ENTITY_TYPE_774_TO_768_MAP =
            expand(ENTITY_TYPE_774_TO_768_RANGES, ENTITY_TYPE_774_TO_768_OVERRIDES,
                    157, PIG_ENTITY_TYPE_768);
    static final int[] SOUND_774_TO_768_MAP =
            expand(SOUND_774_TO_768_RANGES, SOUND_774_TO_768_OVERRIDES,
                    1838, GENERIC_SOUND_768);
    static final int[] PARTICLE_774_TO_768_MAP =
            expand(PARTICLE_774_TO_768_RANGES, PARTICLE_774_TO_768_OVERRIDES,
                    115, ENTITY_EFFECT_PARTICLE_768);
    static final int[] BLOCK_774_TO_768_MAP =
            expand(BLOCK_774_TO_768_RANGES, BLOCK_774_TO_768_OVERRIDES,
                    1166, STONE_BLOCK_768);
    static final int[] MENU_774_TO_768_MAP =
            expand(MENU_774_TO_768_RANGES, MENU_774_TO_768_OVERRIDES,
                    25, CRAFTING_MENU_768);
    static final int[] BLOCK_STATE_769_TO_774_MAP =
            expand(BLOCK_STATE_769_TO_774_RANGES, BLOCK_STATE_769_TO_774_OVERRIDES,
                    27866, STONE_BLOCK_STATE_774);
    static final int[] ITEM_769_TO_774_MAP =
            expand(ITEM_769_TO_774_RANGES, ITEM_769_TO_774_OVERRIDES,
                    1385, STONE_ITEM_774);
    static final int[] ENTITY_TYPE_769_TO_774_MAP =
            expand(ENTITY_TYPE_769_TO_774_RANGES, ENTITY_TYPE_769_TO_774_OVERRIDES,
                    149, PIG_ENTITY_TYPE_774);
    static final int[] SOUND_769_TO_774_MAP =
            expand(SOUND_769_TO_774_RANGES, SOUND_769_TO_774_OVERRIDES,
                    1651, GENERIC_SOUND_774);
    static final int[] PARTICLE_769_TO_774_MAP =
            expand(PARTICLE_769_TO_774_RANGES, PARTICLE_769_TO_774_OVERRIDES,
                    112, ENTITY_EFFECT_PARTICLE_774);
    static final int[] BLOCK_769_TO_774_MAP =
            expand(BLOCK_769_TO_774_RANGES, BLOCK_769_TO_774_OVERRIDES,
                    1095, STONE_BLOCK_774);
    static final int[] MENU_769_TO_774_MAP =
            expand(MENU_769_TO_774_RANGES, MENU_769_TO_774_OVERRIDES,
                    25, CRAFTING_MENU_774);
    static final int[] BLOCK_STATE_774_TO_769_MAP =
            expand(BLOCK_STATE_774_TO_769_RANGES, BLOCK_STATE_774_TO_769_OVERRIDES,
                    29671, STONE_BLOCK_STATE_769);
    static final int[] ITEM_774_TO_769_MAP =
            expand(ITEM_774_TO_769_RANGES, ITEM_774_TO_769_OVERRIDES,
                    1505, STONE_ITEM_769);
    static final int[] ENTITY_TYPE_774_TO_769_MAP =
            expand(ENTITY_TYPE_774_TO_769_RANGES, ENTITY_TYPE_774_TO_769_OVERRIDES,
                    157, PIG_ENTITY_TYPE_769);
    static final int[] SOUND_774_TO_769_MAP =
            expand(SOUND_774_TO_769_RANGES, SOUND_774_TO_769_OVERRIDES,
                    1838, GENERIC_SOUND_769);
    static final int[] PARTICLE_774_TO_769_MAP =
            expand(PARTICLE_774_TO_769_RANGES, PARTICLE_774_TO_769_OVERRIDES,
                    115, ENTITY_EFFECT_PARTICLE_769);
    static final int[] BLOCK_774_TO_769_MAP =
            expand(BLOCK_774_TO_769_RANGES, BLOCK_774_TO_769_OVERRIDES,
                    1166, STONE_BLOCK_769);
    static final int[] MENU_774_TO_769_MAP =
            expand(MENU_774_TO_769_RANGES, MENU_774_TO_769_OVERRIDES,
                    25, CRAFTING_MENU_769);
    static final int[] BLOCK_STATE_770_TO_774_MAP =
            expand(BLOCK_STATE_770_TO_774_RANGES, BLOCK_STATE_770_TO_774_OVERRIDES,
                    27914, STONE_BLOCK_STATE_774);
    static final int[] ITEM_770_TO_774_MAP =
            expand(ITEM_770_TO_774_RANGES, ITEM_770_TO_774_OVERRIDES,
                    1396, STONE_ITEM_774);
    static final int[] ENTITY_TYPE_770_TO_774_MAP =
            expand(ENTITY_TYPE_770_TO_774_RANGES, ENTITY_TYPE_770_TO_774_OVERRIDES,
                    150, PIG_ENTITY_TYPE_774);
    static final int[] SOUND_770_TO_774_MAP =
            expand(SOUND_770_TO_774_RANGES, SOUND_770_TO_774_OVERRIDES,
                    1702, GENERIC_SOUND_774);
    static final int[] PARTICLE_770_TO_774_MAP =
            expand(PARTICLE_770_TO_774_RANGES, PARTICLE_770_TO_774_OVERRIDES,
                    114, ENTITY_EFFECT_PARTICLE_774);
    static final int[] BLOCK_770_TO_774_MAP =
            expand(BLOCK_770_TO_774_RANGES, BLOCK_770_TO_774_OVERRIDES,
                    1104, STONE_BLOCK_774);
    static final int[] MENU_770_TO_774_MAP =
            expand(MENU_770_TO_774_RANGES, MENU_770_TO_774_OVERRIDES,
                    25, CRAFTING_MENU_774);
    static final int[] BLOCK_STATE_774_TO_770_MAP =
            expand(BLOCK_STATE_774_TO_770_RANGES, BLOCK_STATE_774_TO_770_OVERRIDES,
                    29671, STONE_BLOCK_STATE_770);
    static final int[] ITEM_774_TO_770_MAP =
            expand(ITEM_774_TO_770_RANGES, ITEM_774_TO_770_OVERRIDES,
                    1505, STONE_ITEM_770);
    static final int[] ENTITY_TYPE_774_TO_770_MAP =
            expand(ENTITY_TYPE_774_TO_770_RANGES, ENTITY_TYPE_774_TO_770_OVERRIDES,
                    157, PIG_ENTITY_TYPE_770);
    static final int[] SOUND_774_TO_770_MAP =
            expand(SOUND_774_TO_770_RANGES, SOUND_774_TO_770_OVERRIDES,
                    1838, GENERIC_SOUND_770);
    static final int[] PARTICLE_774_TO_770_MAP =
            expand(PARTICLE_774_TO_770_RANGES, PARTICLE_774_TO_770_OVERRIDES,
                    115, ENTITY_EFFECT_PARTICLE_770);
    static final int[] BLOCK_774_TO_770_MAP =
            expand(BLOCK_774_TO_770_RANGES, BLOCK_774_TO_770_OVERRIDES,
                    1166, STONE_BLOCK_770);
    static final int[] MENU_774_TO_770_MAP =
            expand(MENU_774_TO_770_RANGES, MENU_774_TO_770_OVERRIDES,
                    25, CRAFTING_MENU_770);
    static final int[] BLOCK_STATE_771_TO_774_MAP =
            expand(BLOCK_STATE_771_TO_774_RANGES, BLOCK_STATE_771_TO_774_OVERRIDES,
                    27946, STONE_BLOCK_STATE_774);
    static final int[] ITEM_771_TO_774_MAP =
            expand(ITEM_771_TO_774_RANGES, ITEM_771_TO_774_OVERRIDES,
                    1415, STONE_ITEM_774);
    static final int[] ENTITY_TYPE_771_TO_774_MAP =
            expand(ENTITY_TYPE_771_TO_774_RANGES, ENTITY_TYPE_771_TO_774_OVERRIDES,
                    151, PIG_ENTITY_TYPE_774);
    static final int[] SOUND_771_TO_774_MAP =
            expand(SOUND_771_TO_774_RANGES, SOUND_771_TO_774_OVERRIDES,
                    1727, GENERIC_SOUND_774);
    static final int[] PARTICLE_771_TO_774_MAP =
            expand(PARTICLE_771_TO_774_RANGES, PARTICLE_771_TO_774_OVERRIDES,
                    114, ENTITY_EFFECT_PARTICLE_774);
    static final int[] BLOCK_771_TO_774_MAP =
            expand(BLOCK_771_TO_774_RANGES, BLOCK_771_TO_774_OVERRIDES,
                    1105, STONE_BLOCK_774);
    static final int[] MENU_771_TO_774_MAP =
            expand(MENU_771_TO_774_RANGES, MENU_771_TO_774_OVERRIDES,
                    25, CRAFTING_MENU_774);
    static final int[] BLOCK_STATE_774_TO_771_MAP =
            expand(BLOCK_STATE_774_TO_771_RANGES, BLOCK_STATE_774_TO_771_OVERRIDES,
                    29671, STONE_BLOCK_STATE_771);
    static final int[] ITEM_774_TO_771_MAP =
            expand(ITEM_774_TO_771_RANGES, ITEM_774_TO_771_OVERRIDES,
                    1505, STONE_ITEM_771);
    static final int[] ENTITY_TYPE_774_TO_771_MAP =
            expand(ENTITY_TYPE_774_TO_771_RANGES, ENTITY_TYPE_774_TO_771_OVERRIDES,
                    157, PIG_ENTITY_TYPE_771);
    static final int[] SOUND_774_TO_771_MAP =
            expand(SOUND_774_TO_771_RANGES, SOUND_774_TO_771_OVERRIDES,
                    1838, GENERIC_SOUND_771);
    static final int[] PARTICLE_774_TO_771_MAP =
            expand(PARTICLE_774_TO_771_RANGES, PARTICLE_774_TO_771_OVERRIDES,
                    115, ENTITY_EFFECT_PARTICLE_771);
    static final int[] BLOCK_774_TO_771_MAP =
            expand(BLOCK_774_TO_771_RANGES, BLOCK_774_TO_771_OVERRIDES,
                    1166, STONE_BLOCK_771);
    static final int[] MENU_774_TO_771_MAP =
            expand(MENU_774_TO_771_RANGES, MENU_774_TO_771_OVERRIDES,
                    25, CRAFTING_MENU_771);
    static final int[] BLOCK_STATE_772_TO_774_MAP =
            expand(BLOCK_STATE_772_TO_774_RANGES, BLOCK_STATE_772_TO_774_OVERRIDES,
                    27946, STONE_BLOCK_STATE_774);
    static final int[] ITEM_772_TO_774_MAP =
            expand(ITEM_772_TO_774_RANGES, ITEM_772_TO_774_OVERRIDES,
                    1416, STONE_ITEM_774);
    static final int[] ENTITY_TYPE_772_TO_774_MAP =
            expand(ENTITY_TYPE_772_TO_774_RANGES, ENTITY_TYPE_772_TO_774_OVERRIDES,
                    151, PIG_ENTITY_TYPE_774);
    static final int[] SOUND_772_TO_774_MAP =
            expand(SOUND_772_TO_774_RANGES, SOUND_772_TO_774_OVERRIDES,
                    1728, GENERIC_SOUND_774);
    static final int[] PARTICLE_772_TO_774_MAP =
            expand(PARTICLE_772_TO_774_RANGES, PARTICLE_772_TO_774_OVERRIDES,
                    114, ENTITY_EFFECT_PARTICLE_774);
    static final int[] BLOCK_772_TO_774_MAP =
            expand(BLOCK_772_TO_774_RANGES, BLOCK_772_TO_774_OVERRIDES,
                    1105, STONE_BLOCK_774);
    static final int[] MENU_772_TO_774_MAP =
            expand(MENU_772_TO_774_RANGES, MENU_772_TO_774_OVERRIDES,
                    25, CRAFTING_MENU_774);
    static final int[] BLOCK_STATE_774_TO_772_MAP =
            expand(BLOCK_STATE_774_TO_772_RANGES, BLOCK_STATE_774_TO_772_OVERRIDES,
                    29671, STONE_BLOCK_STATE_772);
    static final int[] ITEM_774_TO_772_MAP =
            expand(ITEM_774_TO_772_RANGES, ITEM_774_TO_772_OVERRIDES,
                    1505, STONE_ITEM_772);
    static final int[] ENTITY_TYPE_774_TO_772_MAP =
            expand(ENTITY_TYPE_774_TO_772_RANGES, ENTITY_TYPE_774_TO_772_OVERRIDES,
                    157, PIG_ENTITY_TYPE_772);
    static final int[] SOUND_774_TO_772_MAP =
            expand(SOUND_774_TO_772_RANGES, SOUND_774_TO_772_OVERRIDES,
                    1838, GENERIC_SOUND_772);
    static final int[] PARTICLE_774_TO_772_MAP =
            expand(PARTICLE_774_TO_772_RANGES, PARTICLE_774_TO_772_OVERRIDES,
                    115, ENTITY_EFFECT_PARTICLE_772);
    static final int[] BLOCK_774_TO_772_MAP =
            expand(BLOCK_774_TO_772_RANGES, BLOCK_774_TO_772_OVERRIDES,
                    1166, STONE_BLOCK_772);
    static final int[] MENU_774_TO_772_MAP =
            expand(MENU_774_TO_772_RANGES, MENU_774_TO_772_OVERRIDES,
                    25, CRAFTING_MENU_772);
    static final int[] BLOCK_STATE_UP_774_MAP = invert(
            BLOCK_STATE_774_RANGES, BLOCK_STATE_774_OVERRIDES,
            MAX_BLOCK_STATE_773 + 1, MAX_BLOCK_STATE_774 + 1,
            STONE_BLOCK_STATE_773, null);
    static final int[] ENTITY_TYPE_UP_774_MAP = invert(
            ENTITY_TYPE_774_RANGES, ENTITY_TYPE_774_OVERRIDES,
            MAX_ENTITY_TYPE_773 + 1, MAX_ENTITY_TYPE_774 + 1,
            PIG_ENTITY_TYPE_773, null);
    static final int[] SOUND_UP_774_MAP = invert(
            SOUND_774_RANGES, SOUND_774_OVERRIDES,
            MAX_SOUND_773 + 1, MAX_SOUND_774 + 1,
            GENERIC_SOUND_773, null);
    static final int[] PARTICLE_UP_774_MAP = invert(
            PARTICLE_774_RANGES, PARTICLE_774_OVERRIDES,
            MAX_PARTICLE_773 + 1, MAX_PARTICLE_774 + 1,
            ENTITY_EFFECT_PARTICLE_773, null);
    static final int[] BLOCK_UP_774_MAP = invert(
            BLOCK_774_RANGES, BLOCK_774_OVERRIDES,
            MAX_BLOCK_773 + 1, MAX_BLOCK_774 + 1,
            STONE_BLOCK_773, null);
    static final int[] MENU_UP_774_MAP = invert(
            MENU_774_RANGES, MENU_774_OVERRIDES,
            MAX_MENU_773 + 1, MAX_MENU_774 + 1,
            CRAFTING_MENU_773, null);
}
