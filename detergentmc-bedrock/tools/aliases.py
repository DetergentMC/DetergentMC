"""Java ブロック名 → Bedrock 名のエイリアス表とプロパティ変換。

Java 1.20.3 (pc blocks.json) のブロック名を、Bedrock 1.20.50
(canonical_block_states.nbt) の名前に写す。同名のものはここには無い。
"""

# ---- プロパティ変換 ----
def t_id(v):
    return v

def t_bool_bit(v):
    """Java bool -> Bedrock 0/1。"""
    return 1 if v else 0

def t_facing_dir(v):
    """Java facing (north/south/east/west) -> Bedrock direction (0-3)。"""
    return {'south': 0, 'west': 1, 'north': 2, 'east': 3}.get(v)

def t_facing_dir6(v):
    """Java facing (上下含む) -> Bedrock facing_direction (0-5)。"""
    return {'down': 0, 'up': 1, 'north': 2, 'south': 3, 'west': 4, 'east': 5}.get(v)

def t_torch_facing(v):
    return {'north': 'north', 'south': 'south', 'east': 'east', 'west': 'west',
            'up': 'top', 'down': 'bottom'}.get(v)

def t_half_upside_down(v):
    return {'top': 1, 'bottom': 0}.get(v)

def t_half_upper(v):
    return {'upper': 1, 'lower': 0}.get(v)

def t_hinge_bit(v):
    return {'left': 0, 'right': 1}.get(v)

def t_head_bit(v):
    return {'head': 1, 'foot': 0}.get(v)

def t_axis(v):
    return v  # axis/pillar_axis は同じ文字列

def t_rail_dir(v):
    return {'north_south': 0, 'east_west': 1, 'ascending_east': 2, 'ascending_west': 3,
            'ascending_north': 4, 'ascending_south': 5, 'south_east': 6, 'south_west': 7,
            'north_west': 8, 'north_east': 9}.get(v)

def t_layers_minus1(v):
    return v - 1  # Java layers 1-8 -> Bedrock height 0-7

def t_dripleaf_tilt(v):
    return {'partial': 'partial_tilt', 'full': 'full_tilt'}.get(v, v)

def t_seagrass_type(v):
    return {'lower': 'double_bot', 'upper': 'double_top'}.get(v)

def t_sculk_phase(v):
    return {'inactive': 0, 'active': 1, 'cooldown': 2}.get(v)

def t_wall_conn(v):
    return {'none': 'none', 'low': 'short', 'tall': 'tall'}.get(v)

def _face_bits(props):
    """Java の6方向 bool -> Bedrock の方向ビットマスク。
    ビット順は Bedrock の Direction 列挙（down=0, up=1, north=2, south=3,
    west=4, east=5）と対応する best-effort の推定。"""
    bits = 0
    for name, bit in (('down', 1), ('up', 2), ('north', 4), ('south', 8),
                      ('west', 16), ('east', 32)):
        if props.get(name) is True:
            bits |= bit
    return bits

def t_multiface_bits(props):
    return _face_bits(props)

def t_vine_bits(props):
    # Bedrock vine_direction_bits (Minecraft Wiki "Vines"):
    # 0x1 south, 0x2 west, 0x4 north, 0x8 east。up は対象外。
    bits = 0
    for name, bit in (('south', 1), ('west', 2), ('north', 4), ('east', 8)):
        if props.get(name) is True:
            bits |= bit
    return bits

def t_mushroom_bits(props):
    # Bedrock huge_mushroom_bits (Minecraft Wiki "Mushroom Block")。
    # 値は 3x3 グリッド式：5 + ns + we (north=-3, south=+3, west=-1, east=+1)。
    # 0=全面 pores、5=上面のみ cap、14=全面 cap、15=全面 stem。
    sides = {k: props.get(k) is True for k in ('north', 'east', 'south', 'west')}
    up = props.get('up') is True
    if not any(sides.values()) and not up:
        return 0
    if all(sides.values()) and up:
        return 14
    ns = -3 if (sides['north'] and not sides['south']) else \
        (3 if (sides['south'] and not sides['north']) else 0)
    we = -1 if (sides['west'] and not sides['east']) else \
        (1 if (sides['east'] and not sides['west']) else 0)
    return 5 + ns + we

def t_lever_dir(props):
    # Java lever (face, facing) -> Bedrock lever_direction。
    face, facing = props.get('face'), props.get('facing')
    if face == 'floor':
        return 'down_east_west' if facing in ('east', 'west') else 'down_north_south'
    if face == 'ceiling':
        return 'up_east_west' if facing in ('east', 'west') else 'up_north_south'
    return facing  # wall: north/south/east/west

def t_jigsaw_facing(props):
    # Java jigsaw orientation の先頭（向き）-> Bedrock facing_direction。
    first = (props.get('orientation') or '').split('_')[0]
    return {'down': 0, 'up': 1, 'north': 2, 'south': 3, 'west': 4, 'east': 5}.get(first)

def t_grindstone_attach(v):
    return {'floor': 'standing', 'ceiling': 'hanging', 'wall': 'side'}.get(v)

def t_bell_attach(v):
    return {'floor': 'standing', 'ceiling': 'hanging',
            'single_wall': 'side', 'double_wall': 'multiple'}.get(v)

def t_cracked(v):
    return {0: 'no_cracks', 1: 'cracked', 2: 'max_cracked'}.get(v)

def t_egg_count(v):
    return {1: 'one_egg', 2: 'two_egg', 3: 'three_egg', 4: 'four_egg'}.get(v)

def t_button_facing(props):
    # Java button (face, facing) -> Bedrock facing_direction。
    # 床=up(1)、天井=down(0)、壁=向き。
    face = props.get('face')
    if face == 'floor':
        return 1
    if face == 'ceiling':
        return 0
    return t_facing_dir6(props.get('facing'))

def t_hanging_sign_facing(props):
    # Java 吊り看板の rotation (0-15) -> 最も近い Bedrock facing_direction。
    # rotation 0=south, 4=west, 8=north, 12=east。
    rot = props.get('rotation', 0)
    idx = ((int(rot) + 2) // 4) % 4
    return [3, 4, 2, 5][idx]


def t_books_stored(props):
    return sum(1 for i in range(6) if props.get(f'slot_{i}_occupied') is True)

# 壁の接続プロパティ（全ての壁ブロックで共通）
WALL_PMAP = {
    'wall_connection_type_east': ('east', t_wall_conn),
    'wall_connection_type_north': ('north', t_wall_conn),
    'wall_connection_type_south': ('south', t_wall_conn),
    'wall_connection_type_west': ('west', t_wall_conn),
    'wall_post_bit': ('up', t_bool_bit),
}

# Bedrock プロパティ -> (Java プロパティ, 変換) のよく使う対応。
# (これらは名前が同じブロックで共通に使えるもの)
COMMON_PROP_MAP = {
    'pillar_axis': ('axis', t_axis),
    'liquid_depth': ('level', t_id),
    'growth': ('age', t_id),
    'redstone_signal': ('power', t_id),
    'repeater_delay': ('delay', t_id),
    'kelp_age': ('age', t_id),
    'twisting_vines_age': ('age', t_id),
    'weeping_vines_age': ('age', t_id),
    'height': ('layers', t_layers_minus1),
    'facing_direction': ('facing', t_facing_dir6),
    'direction': ('facing', t_facing_dir),
    'weirdo_direction': ('facing', t_facing_dir),
    'minecraft:cardinal_direction': ('facing', t_id),
    'torch_facing_direction': ('facing', t_torch_facing),
    'ground_sign_direction': ('rotation', t_id),
    'upside_down_bit': ('half', t_half_upside_down),
    'upper_block_bit': ('half', t_half_upper),
    'minecraft:vertical_half': ('half', t_id),
    'door_hinge_bit': ('hinge', t_hinge_bit),
    'head_piece_bit': ('part', t_head_bit),
    'occupied_bit': ('occupied', t_bool_bit),
    'open_bit': ('open', t_bool_bit),
    'powered_bit': ('powered', t_bool_bit),
    'in_wall_bit': ('in_wall', t_bool_bit),
    'persistent_bit': ('persistent', t_bool_bit),
    'age_bit': ('stage', t_bool_bit),
    'attached_bit': ('attached', t_bool_bit),
    'triggered_bit': ('triggered', t_bool_bit),
    'rail_direction': ('shape', t_rail_dir),
    'rail_data_bit': ('powered', t_bool_bit),
    'block_light_level': ('level', t_id),
    'minecraft:block_face': ('facing', t_id),
    'composter_fill_level': ('level', t_id),
    'moisturized_amount': ('moisture', t_id),
    'brewing_stand_slot_a_bit': ('has_bottle_0', t_bool_bit),
    'brewing_stand_slot_b_bit': ('has_bottle_1', t_bool_bit),
    'brewing_stand_slot_c_bit': ('has_bottle_2', t_bool_bit),
    'bite_counter': ('bites', t_id),
    'stability': ('distance', t_id),
    'stability_check': ('bottom', t_bool_bit),
    'conditional_bit': ('conditional', t_bool_bit),
    'minecraft:facing_direction': ('facing', t_id),
    'end_portal_eye_bit': ('eye', t_bool_bit),
    'can_summon': ('can_summon', t_bool_bit),
    'active': ('shrieking', t_bool_bit),
    'candles': ('candles', lambda v: v - 1),
    'respawn_anchor_charge': ('charges', t_id),
    'brushed_progress': ('dusted', t_id),
    'structure_block_type': ('mode', t_id),
    'drag_down': ('drag', t_bool_bit),
}

# 無視してよい Java プロパティ（Bedrock 側に対応が無く、層1の水以外で
# 形に影響しないもの）。waterlogged は別途ビットセットで記録する。
IGNORE_PROPS = {
    'waterlogged', 'snowy', 'shape', 'conditional', 'triggered', 'enabled',
    'locked', 'has_record', 'has_book', 'cracked', 'eye', 'inverted', 'mode',
    'disarmed', 'distance', 'dusted', 'hatch', 'signal_fire', 'unstable',
    'vertical_direction', 'trial_spawner_state', 'can_summon', 'crafting',
    'slot_0_occupied', 'slot_1_occupied', 'slot_2_occupied', 'slot_3_occupied',
    'slot_4_occupied', 'slot_5_occupied', 'has_bottle_0', 'has_bottle_1',
    'has_bottle_2', 'sculk_sensor_phase', 'shrieking', 'bloom', 'berries',
    'bottom', 'drag', 'eggs', 'flower_amount', 'honey_level', 'moisture',
    'note', 'instrument', 'orientation', 'part', 'rotation', 'short',
    'thickness', 'tilt', 'type', 'age', 'attached', 'attachment', 'face',
    'hanging', 'lit', 'open', 'powered', 'east', 'north', 'south', 'west',
    'up', 'down', 'bites', 'candles', 'charges', 'delay', 'extended',
    'leaves', 'level', 'pickles', 'stage',
}

# エイリアス：Java 名 -> (Bedrock 名, 固定プロパティ, 追加プロパティ対応)
# 固定プロパティ： Bedrock プロパティ -> 値
# 追加プロパティ対応： Bedrock プロパティ -> (Java プロパティ, 変換)
# 条件付きは COND_ALIASES で扱う。
ALIASES = {
    'grass_block': ('grass', {}, {}),
    'dirt_path': ('grass_path', {}, {}),
    'coarse_dirt': ('dirt', {'dirt_type': 'coarse'}, {}),
    'dirt': ('dirt', {'dirt_type': 'normal'}, {}),
    'red_sand': ('sand', {'sand_type': 'red'}, {}),
    'sand': ('sand', {'sand_type': 'normal'}, {}),
    'snow': ('snow_layer', {'covered_bit': 0}, {'height': ('layers', t_layers_minus1)}),
    'snow_block': ('snow', {}, {}),
    'bricks': ('brick_block', {}, {}),
    'terracotta': ('hardened_clay', {}, {}),
    'nether_bricks': ('nether_brick', {}, {}),
    'end_stone_bricks': ('end_bricks', {}, {}),
    'red_nether_bricks': ('red_nether_brick', {}, {}),
    'magma_block': ('magma', {}, {}),
    'melon': ('melon_block', {}, {}),
    'nether_portal': ('portal', {}, {'portal_axis': ('axis', t_axis)}),
    'sugar_cane': ('reeds', {}, {'age': ('age', t_id)}),
    'lily_pad': ('waterlily', {}, {}),
    'cobweb': ('web', {}, {}),
    'spawner': ('mob_spawner', {}, {}),
    'trial_spawner': ('mob_spawner', {}, {}),
    'note_block': ('noteblock', {}, {}),
    'slime_block': ('slime', {}, {}),
    'rooted_dirt': ('dirt_with_roots', {}, {}),
    'wet_sponge': ('sponge', {'sponge_type': 'wet'}, {}),
    'sponge': ('sponge', {'sponge_type': 'dry'}, {}),
    'nether_quartz_ore': ('quartz_ore', {}, {}),
    'light': ('light_block', {}, {'block_light_level': ('level', t_id)}),
    'cave_air': ('air', {}, {}),
    'void_air': ('air', {}, {}),
    'moving_piston': ('air', {}, {}),
    'piston_head': ('piston_arm_collision', {}, {}),
    # 松明（床置きは torch_facing_direction=top）
    'bedrock': ('bedrock', {'infiniburn_bit': 0}, {}),
    'soul_fire': ('soul_fire', {'age': 0}, {}),
    'pumpkin': ('pumpkin', {'minecraft:cardinal_direction': 'south'}, {}),
    'cauldron': ('cauldron', {'cauldron_liquid': 'water', 'fill_level': 0}, {}),
    'hay_block': ('hay_block', {'deprecated': 0}, {'pillar_axis': ('axis', t_axis)}),
    'bone_block': ('bone_block', {'deprecated': 0}, {'pillar_axis': ('axis', t_axis)}),
    'torch': ('torch', {'torch_facing_direction': 'top'}, {}),
    'soul_torch': ('soul_torch', {'torch_facing_direction': 'top'}, {}),
    'stone_pressure_plate': ('stone_pressure_plate', {},
                             {'redstone_signal': ('powered', t_bool_bit)}),
    'polished_blackstone_pressure_plate': ('polished_blackstone_pressure_plate', {},
                             {'redstone_signal': ('powered', t_bool_bit)}),
    'polished_blackstone_button': ('polished_blackstone_button', {},
                     {'button_pressed_bit': ('powered', t_bool_bit),
                      'facing_direction': ('*', t_button_facing)}),
    'stone_button': ('stone_button', {},
                     {'button_pressed_bit': ('powered', t_bool_bit),
                      'facing_direction': ('*', t_button_facing)}),
    'tripwire': ('trip_wire', {'suspended_bit': 0},
               {'attached_bit': ('attached', t_bool_bit),
                'disarmed_bit': ('disarmed', t_bool_bit),
                'powered_bit': ('powered', t_bool_bit)}),
    'dead_bush': ('deadbush', {}, {}),
    'short_grass': ('tallgrass', {'tall_grass_type': 'default'}, {}),
    'fern': ('tallgrass', {'tall_grass_type': 'fern'}, {}),
    'tall_grass': ('double_plant', {'double_plant_type': 'grass'},
                   {'upper_block_bit': ('half', t_half_upper)}),
    'large_fern': ('double_plant', {'double_plant_type': 'fern'},
                   {'upper_block_bit': ('half', t_half_upper)}),
    'lilac': ('double_plant', {'double_plant_type': 'syringa'},
              {'upper_block_bit': ('half', t_half_upper)}),
    'rose_bush': ('double_plant', {'double_plant_type': 'rose'},
                 {'upper_block_bit': ('half', t_half_upper)}),
    'peony': ('double_plant', {'double_plant_type': 'paeonia'},
              {'upper_block_bit': ('half', t_half_upper)}),
    'sunflower': ('double_plant', {'double_plant_type': 'sunflower'},
                  {'upper_block_bit': ('half', t_half_upper)}),
    'poppy': ('red_flower', {'flower_type': 'poppy'}, {}),
    'blue_orchid': ('red_flower', {'flower_type': 'orchid'}, {}),
    'allium': ('red_flower', {'flower_type': 'allium'}, {}),
    'azure_bluet': ('red_flower', {'flower_type': 'houstonia'}, {}),
    'red_tulip': ('red_flower', {'flower_type': 'tulip_red'}, {}),
    'orange_tulip': ('red_flower', {'flower_type': 'tulip_orange'}, {}),
    'white_tulip': ('red_flower', {'flower_type': 'tulip_white'}, {}),
    'pink_tulip': ('red_flower', {'flower_type': 'tulip_pink'}, {}),
    'oxeye_daisy': ('red_flower', {'flower_type': 'oxeye'}, {}),
    'cornflower': ('red_flower', {'flower_type': 'cornflower'}, {}),
    'lily_of_the_valley': ('red_flower', {'flower_type': 'lily_of_the_valley'}, {}),
    'dandelion': ('yellow_flower', {}, {}),
    'wheat': ('wheat', {}, {'growth': ('age', t_id)}),
    'carrots': ('carrots', {}, {'growth': ('age', t_id)}),
    'potatoes': ('potatoes', {}, {'growth': ('age', t_id)}),
    'beetroots': ('beetroot', {}, {'growth': ('age', t_id)}),
    # 葉：Bedrock は leaves / leaves2
    'oak_leaves': ('leaves', {'old_leaf_type': 'oak', 'update_bit': 0},
                   {'persistent_bit': ('persistent', t_bool_bit)}),
    'spruce_leaves': ('leaves', {'old_leaf_type': 'spruce', 'update_bit': 0},
                      {'persistent_bit': ('persistent', t_bool_bit)}),
    'birch_leaves': ('leaves', {'old_leaf_type': 'birch', 'update_bit': 0},
                     {'persistent_bit': ('persistent', t_bool_bit)}),
    'jungle_leaves': ('leaves', {'old_leaf_type': 'jungle', 'update_bit': 0},
                      {'persistent_bit': ('persistent', t_bool_bit)}),
    'acacia_leaves': ('leaves2', {'new_leaf_type': 'acacia', 'update_bit': 0},
                      {'persistent_bit': ('persistent', t_bool_bit)}),
    'dark_oak_leaves': ('leaves2', {'new_leaf_type': 'dark_oak', 'update_bit': 0},
                        {'persistent_bit': ('persistent', t_bool_bit)}),
    'cherry_leaves': ('cherry_leaves', {'update_bit': 0}, {}),
    'mangrove_leaves': ('mangrove_leaves', {'update_bit': 0}, {}),
    'azalea_leaves': ('azalea_leaves', {'update_bit': 0}, {}),
    'flowering_azalea_leaves': ('azalea_leaves_flowered', {'update_bit': 0}, {}),
    # 薄板・階段・壁・ドア類（木材ごと）は build_aliases() で生成
    # クォーツ・砂岩・石レンガ・プリズマリン
    'chiseled_sandstone': ('sandstone', {'sand_stone_type': 'heiroglyphs'}, {}),
    'cut_sandstone': ('sandstone', {'sand_stone_type': 'cut'}, {}),
    'smooth_sandstone': ('sandstone', {'sand_stone_type': 'smooth'}, {}),
    'sandstone': ('sandstone', {'sand_stone_type': 'default'}, {}),
    'chiseled_red_sandstone': ('red_sandstone', {'sand_stone_type': 'heiroglyphs'}, {}),
    'cut_red_sandstone': ('red_sandstone', {'sand_stone_type': 'cut'}, {}),
    'smooth_red_sandstone': ('red_sandstone', {'sand_stone_type': 'smooth'}, {}),
    'red_sandstone': ('red_sandstone', {'sand_stone_type': 'default'}, {}),
    'stone_bricks': ('stonebrick', {'stone_brick_type': 'default'}, {}),
    'mossy_stone_bricks': ('stonebrick', {'stone_brick_type': 'mossy'}, {}),
    'cracked_stone_bricks': ('stonebrick', {'stone_brick_type': 'cracked'}, {}),
    'chiseled_stone_bricks': ('stonebrick', {'stone_brick_type': 'chiseled'}, {}),
    'prismarine': ('prismarine', {'prismarine_block_type': 'default'}, {}),
    'prismarine_bricks': ('prismarine', {'prismarine_block_type': 'bricks'}, {}),
    'dark_prismarine': ('prismarine', {'prismarine_block_type': 'dark'}, {}),
    'chiseled_quartz_block': ('quartz_block', {'chisel_type': 'chiseled', 'pillar_axis': 'y'}, {}),
    'quartz_pillar': ('quartz_block', {'chisel_type': 'lines'},
                      {'pillar_axis': ('axis', t_axis)}),
    'smooth_quartz': ('quartz_block', {'chisel_type': 'smooth', 'pillar_axis': 'y'}, {}),
    'quartz_block': ('quartz_block', {'chisel_type': 'default', 'pillar_axis': 'y'}, {}),
    'purpur_pillar': ('purpur_block', {'chisel_type': 'lines'},
                      {'pillar_axis': ('axis', t_axis)}),
    'purpur_block': ('purpur_block', {'chisel_type': 'default', 'pillar_axis': 'y'}, {}),
    # 壁
    'cobblestone_wall': ('cobblestone_wall', {'wall_block_type': 'cobblestone'}, WALL_PMAP),
    'mossy_cobblestone_wall': ('cobblestone_wall', {'wall_block_type': 'mossy_cobblestone'}, WALL_PMAP),
    'granite_wall': ('cobblestone_wall', {'wall_block_type': 'granite'}, WALL_PMAP),
    'diorite_wall': ('cobblestone_wall', {'wall_block_type': 'diorite'}, WALL_PMAP),
    'andesite_wall': ('cobblestone_wall', {'wall_block_type': 'andesite'}, WALL_PMAP),
    'sandstone_wall': ('cobblestone_wall', {'wall_block_type': 'sandstone'}, WALL_PMAP),
    'brick_wall': ('cobblestone_wall', {'wall_block_type': 'brick'}, WALL_PMAP),
    'stone_brick_wall': ('cobblestone_wall', {'wall_block_type': 'stone_brick'}, WALL_PMAP),
    'mossy_stone_brick_wall': ('cobblestone_wall', {'wall_block_type': 'mossy_stone_brick'}, WALL_PMAP),
    'nether_brick_wall': ('cobblestone_wall', {'wall_block_type': 'nether_brick'}, WALL_PMAP),
    'end_stone_brick_wall': ('cobblestone_wall', {'wall_block_type': 'end_brick'}, WALL_PMAP),
    'prismarine_wall': ('cobblestone_wall', {'wall_block_type': 'prismarine'}, WALL_PMAP),
    'red_sandstone_wall': ('cobblestone_wall', {'wall_block_type': 'red_sandstone'}, WALL_PMAP),
    'red_nether_brick_wall': ('cobblestone_wall', {'wall_block_type': 'red_nether_brick'}, WALL_PMAP),
    # 1.20.40/1.20.50 のパレットに無い壁材（近い色・素材へ代替。docs 参照）
    'mud_brick_wall': ('cobblestone_wall', {'wall_block_type': 'brick'}, WALL_PMAP),
    'blackstone_wall': ('cobblestone_wall', {'wall_block_type': 'nether_brick'}, WALL_PMAP),
    'polished_blackstone_wall': ('cobblestone_wall', {'wall_block_type': 'nether_brick'}, WALL_PMAP),
    'polished_blackstone_brick_wall': ('cobblestone_wall', {'wall_block_type': 'nether_brick'}, WALL_PMAP),
    'tuff_wall': ('cobblestone_wall', {'wall_block_type': 'cobblestone'}, WALL_PMAP),
    'polished_tuff_wall': ('cobblestone_wall', {'wall_block_type': 'stone_brick'}, WALL_PMAP),
    'tuff_brick_wall': ('cobblestone_wall', {'wall_block_type': 'stone_brick'}, WALL_PMAP),
    'cobbled_deepslate_wall': ('cobblestone_wall', {'wall_block_type': 'cobblestone'}, WALL_PMAP),
    'polished_deepslate_wall': ('cobblestone_wall', {'wall_block_type': 'stone_brick'}, WALL_PMAP),
    'deepslate_tile_wall': ('cobblestone_wall', {'wall_block_type': 'stone_brick'}, WALL_PMAP),
    'deepslate_brick_wall': ('cobblestone_wall', {'wall_block_type': 'stone_brick'}, WALL_PMAP),
    # 階段（Bedrock は素材ごとの名前）
    'cobblestone_stairs': ('stone_stairs', {}, {}),
    'stone_stairs': ('normal_stone_stairs', {}, {}),
    'brick_stairs': ('brick_stairs', {}, {}),
    'sandstone_stairs': ('sandstone_stairs', {}, {}),
    'smooth_sandstone_stairs': ('smooth_sandstone_stairs', {}, {}),
    'smooth_red_sandstone_stairs': ('smooth_red_sandstone_stairs', {}, {}),
    'red_sandstone_stairs': ('red_sandstone_stairs', {}, {}),
    'quartz_stairs': ('quartz_stairs', {}, {}),
    'smooth_quartz_stairs': ('smooth_quartz_stairs', {}, {}),
    'purpur_stairs': ('purpur_stairs', {}, {}),
    'prismarine_stairs': ('prismarine_stairs', {}, {}),
    'dark_prismarine_stairs': ('dark_prismarine_stairs', {}, {}),
    'mossy_cobblestone_stairs': ('mossy_cobblestone_stairs', {}, {}),
    'mossy_stone_brick_stairs': ('mossy_stone_brick_stairs', {}, {}),
    'stone_brick_stairs': ('stone_brick_stairs', {}, {}),
    'nether_brick_stairs': ('nether_brick_stairs', {}, {}),
    'red_nether_brick_stairs': ('red_nether_brick_stairs', {}, {}),
    'end_stone_brick_stairs': ('end_brick_stairs', {}, {}),
    'granite_stairs': ('granite_stairs', {}, {}),
    'diorite_stairs': ('diorite_stairs', {}, {}),
    'andesite_stairs': ('andesite_stairs', {}, {}),
    'polished_granite_stairs': ('polished_granite_stairs', {}, {}),
    'polished_diorite_stairs': ('polished_diorite_stairs', {}, {}),
    'polished_andesite_stairs': ('polished_andesite_stairs', {}, {}),
    # 薄板（stone_block_slab系）
    'smooth_stone_slab': ('stone_block_slab', {'stone_slab_type': 'smooth_stone'}, {}),
    'sandstone_slab': ('stone_block_slab', {'stone_slab_type': 'sandstone'}, {}),
    'cobblestone_slab': ('stone_block_slab', {'stone_slab_type': 'cobblestone'}, {}),
    'brick_slab': ('stone_block_slab', {'stone_slab_type': 'brick'}, {}),
    'stone_brick_slab': ('stone_block_slab', {'stone_slab_type': 'stone_brick'}, {}),
    'quartz_slab': ('stone_block_slab', {'stone_slab_type': 'quartz'}, {}),
    'nether_brick_slab': ('stone_block_slab', {'stone_slab_type': 'nether_brick'}, {}),
    'red_sandstone_slab': ('stone_block_slab2', {'stone_slab_type_2': 'red_sandstone'}, {}),
    'purpur_slab': ('stone_block_slab2', {'stone_slab_type_2': 'purpur'}, {}),
    'prismarine_slab': ('stone_block_slab2', {'stone_slab_type_2': 'prismarine_rough'}, {}),
    'dark_prismarine_slab': ('stone_block_slab2', {'stone_slab_type_2': 'prismarine_dark'}, {}),
    'prismarine_brick_slab': ('stone_block_slab2', {'stone_slab_type_2': 'prismarine_brick'}, {}),
    'mossy_cobblestone_slab': ('stone_block_slab2', {'stone_slab_type_2': 'mossy_cobblestone'}, {}),
    'smooth_sandstone_slab': ('stone_block_slab2', {'stone_slab_type_2': 'smooth_sandstone'}, {}),
    'red_nether_brick_slab': ('stone_block_slab2', {'stone_slab_type_2': 'red_nether_brick'}, {}),
    'end_stone_brick_slab': ('stone_block_slab3', {'stone_slab_type_3': 'end_stone_brick'}, {}),
    'smooth_red_sandstone_slab': ('stone_block_slab3', {'stone_slab_type_3': 'smooth_red_sandstone'}, {}),
    'polished_andesite_slab': ('stone_block_slab3', {'stone_slab_type_3': 'polished_andesite'}, {}),
    'andesite_slab': ('stone_block_slab3', {'stone_slab_type_3': 'andesite'}, {}),
    'diorite_slab': ('stone_block_slab3', {'stone_slab_type_3': 'diorite'}, {}),
    'polished_diorite_slab': ('stone_block_slab3', {'stone_slab_type_3': 'polished_diorite'}, {}),
    'granite_slab': ('stone_block_slab3', {'stone_slab_type_3': 'granite'}, {}),
    'polished_granite_slab': ('stone_block_slab3', {'stone_slab_type_3': 'polished_granite'}, {}),
    'mossy_stone_brick_slab': ('stone_block_slab4', {'stone_slab_type_4': 'mossy_stone_brick'}, {}),
    'smooth_quartz_slab': ('stone_block_slab4', {'stone_slab_type_4': 'smooth_quartz'}, {}),
    'stone_slab': ('stone_block_slab4', {'stone_slab_type_4': 'stone'}, {}),
    'cut_sandstone_slab': ('stone_block_slab4', {'stone_slab_type_4': 'cut_sandstone'}, {}),
    'cut_red_sandstone_slab': ('stone_block_slab4', {'stone_slab_type_4': 'cut_red_sandstone'}, {}),
    # トーチ類
    'wall_torch': ('torch', {}, {'torch_facing_direction': ('facing', t_torch_facing)}),
    'redstone_wall_torch': ('redstone_torch', {}, {'torch_facing_direction': ('facing', t_torch_facing)}),
    'soul_wall_torch': ('soul_torch', {}, {'torch_facing_direction': ('facing', t_torch_facing)}),
    # レッドストーン
    'powered_rail': ('golden_rail', {}, {'rail_direction': ('shape', t_rail_dir),
                                         'rail_data_bit': ('powered', t_bool_bit)}),
    'comparator': ('unpowered_comparator', {}, {}),
    'repeater': ('unpowered_repeater', {}, {}),
    # サンゴ
    'tube_coral_block': ('coral_block', {'coral_color': 'blue', 'dead_bit': 0}, {}),
    'brain_coral_block': ('coral_block', {'coral_color': 'pink', 'dead_bit': 0}, {}),
    'bubble_coral_block': ('coral_block', {'coral_color': 'purple', 'dead_bit': 0}, {}),
    'fire_coral_block': ('coral_block', {'coral_color': 'red', 'dead_bit': 0}, {}),
    'horn_coral_block': ('coral_block', {'coral_color': 'yellow', 'dead_bit': 0}, {}),
    'dead_tube_coral_block': ('coral_block', {'coral_color': 'blue', 'dead_bit': 1}, {}),
    'dead_brain_coral_block': ('coral_block', {'coral_color': 'pink', 'dead_bit': 1}, {}),
    'dead_bubble_coral_block': ('coral_block', {'coral_color': 'purple', 'dead_bit': 1}, {}),
    'dead_fire_coral_block': ('coral_block', {'coral_color': 'red', 'dead_bit': 1}, {}),
    'dead_horn_coral_block': ('coral_block', {'coral_color': 'yellow', 'dead_bit': 1}, {}),
    'jack_o_lantern': ('lit_pumpkin', {}, {'minecraft:cardinal_direction': ('facing', t_id)}),
    'chipped_anvil': ('anvil', {'damage': 'slightly_damaged'}, {}),
    'damaged_anvil': ('anvil', {'damage': 'very_damaged'}, {}),
    'anvil': ('anvil', {'damage': 'undamaged'}, {}),
    # その他
    'kelp_plant': ('kelp', {'kelp_age': 0}, {}),
    'twisting_vines_plant': ('twisting_vines', {'twisting_vines_age': 0}, {}),
    'weeping_vines_plant': ('weeping_vines', {'weeping_vines_age': 0}, {}),
    'structure_void': ('structure_void', {'structure_void_type': 'void'}, {}),
    'bamboo_sapling': ('bamboo_sapling', {'age_bit': 0, 'sapling_type': 'oak'}, {}),
    'small_dripleaf': ('small_dripleaf_block', {}, {'minecraft:cardinal_direction': ('facing', t_id)}),
    'frogspawn': ('frog_spawn', {}, {}),
    'infested_stone': ('monster_egg', {'monster_egg_stone_type': 'stone'}, {}),
    'infested_cobblestone': ('monster_egg', {'monster_egg_stone_type': 'cobblestone'}, {}),
    'infested_stone_bricks': ('monster_egg', {'monster_egg_stone_type': 'stone_brick'}, {}),
    'infested_mossy_stone_bricks': ('monster_egg', {'monster_egg_stone_type': 'mossy_stone_brick'}, {}),
    'infested_cracked_stone_bricks': ('monster_egg', {'monster_egg_stone_type': 'cracked_stone_brick'}, {}),
    'infested_chiseled_stone_bricks': ('monster_egg', {'monster_egg_stone_type': 'chiseled_stone_brick'}, {}),
    'mushroom_stem': ('brown_mushroom_block', {'huge_mushroom_bits': 15}, {}),
    'waxed_copper_block': ('waxed_copper', {}, {}),
    # ツタ・発光地衣・スカルク脈・キノコ（方向ビットマスク）
    'vine': ('vine', {}, {'vine_direction_bits': ('*', t_vine_bits)}),
    'glow_lichen': ('glow_lichen', {}, {'multi_face_direction_bits': ('*', t_multiface_bits)}),
    'sculk_vein': ('sculk_vein', {}, {'multi_face_direction_bits': ('*', t_multiface_bits)}),
    'brown_mushroom_block': ('brown_mushroom_block', {},
                             {'huge_mushroom_bits': ('*', t_mushroom_bits)}),
    'red_mushroom_block': ('red_mushroom_block', {},
                           {'huge_mushroom_bits': ('*', t_mushroom_bits)}),
    # スカルクセンサー（phase のみ。他のプロパティは Bedrock に無い）
    'sculk_sensor': ('sculk_sensor', {},
                     {'sculk_sensor_phase': ('sculk_sensor_phase', t_sculk_phase)}),
    'calibrated_sculk_sensor': ('calibrated_sculk_sensor', {},
                                {'sculk_sensor_phase': ('sculk_sensor_phase', t_sculk_phase)}),
    # 本棚（空きスロット数 -> books_stored）
    'chiseled_bookshelf': ('chiseled_bookshelf', {},
                           {'books_stored': ('*', t_books_stored)}),
    # 海草（背の高い方は double_bot / double_top）
    'seagrass': ('seagrass', {'sea_grass_type': 'default'}, {}),
    'tall_seagrass': ('seagrass', {},
                      {'sea_grass_type': ('half', t_seagrass_type)}),
    # 大きなドリップリーフ（Java は常に head、stem は別ブロック）
    'big_dripleaf': ('big_dripleaf', {'big_dripleaf_head': 1},
                     {'big_dripleaf_tilt': ('tilt', t_dripleaf_tilt)}),
    'big_dripleaf_stem': ('big_dripleaf', {'big_dripleaf_head': 0,
                                           'big_dripleaf_tilt': 'none'}, {}),
    # 付きの茎（Bedrock は通常の茎と同じ）
    'attached_pumpkin_stem': ('pumpkin_stem', {'growth': 7},
                             {'facing_direction': ('facing', t_facing_dir6)}),
    'attached_melon_stem': ('melon_stem', {'growth': 7},
                            {'facing_direction': ('facing', t_facing_dir6)}),
    # 大釜（Bedrock は cauldron 1種。溶岩は常に満タン）
    'water_cauldron': ('cauldron', {'cauldron_liquid': 'water'},
                       {'fill_level': ('level', t_id)}),
    'lava_cauldron': ('cauldron', {'cauldron_liquid': 'lava', 'fill_level': 6}, {}),
    'powder_snow_cauldron': ('cauldron', {'cauldron_liquid': 'powder_snow'},
                             {'fill_level': ('level', t_id)}),
    # 頭蓋骨（Bedrock は skull 1種）
    'skeleton_skull': ('skull', {'facing_direction': 1}, {}),
    'wither_skeleton_skull': ('skull', {'facing_direction': 1}, {}),
    'skeleton_wall_skull': ('skull', {},
                            {'facing_direction': ('facing', t_facing_dir6)}),
    'wither_skeleton_wall_skull': ('skull', {},
                                   {'facing_direction': ('facing', t_facing_dir6)}),
    # 石化したオークのハーフブロックは Bedrock に無い -> 木のハーフブロック
    'petrified_oak_slab': ('wooden_slab', {'wood_type': 'oak'},
                           {'minecraft:vertical_half': ('half', t_id)}),
    # プリズマリンレンガの階段（Bedrock は prismarine_bricks_stairs）
    'prismarine_brick_stairs': ('prismarine_bricks_stairs', {}, {}),
    'lever': ('lever', {},
              {'open_bit': ('powered', t_bool_bit),
               'lever_direction': ('*', t_lever_dir)}),
    'scaffolding': ('scaffolding', {}, {}),
    'pointed_dripstone': ('pointed_dripstone', {},
                          {'dripstone_thickness': ('thickness',
                                       lambda v: 'tip' if v == 'tip_merge' else v),
                           'hanging': ('vertical_direction',
                                       lambda v: 1 if v == 'down' else 0)}),
    'bell': ('bell', {},
             {'attachment': ('attachment', t_bell_attach),
              'direction': ('facing', t_facing_dir),
              'toggle_bit': ('powered', t_bool_bit)}),
    'campfire': ('campfire', {},
                 {'extinguished': ('lit', lambda v: 0 if v else 1)}),
    'soul_campfire': ('soul_campfire', {},
                      {'extinguished': ('lit', lambda v: 0 if v else 1)}),
    'mangrove_propagule': ('mangrove_propagule', {},
                           {'propagule_stage': ('age', t_id)}),
    'jigsaw': ('jigsaw', {},
               {'facing_direction': ('*', t_jigsaw_facing),
                'rotation': ('*', lambda p: 0)}),
    'turtle_egg': ('turtle_egg', {},
                   {'cracked_state': ('hatch', t_cracked),
                    'turtle_egg_count': ('eggs', t_egg_count)}),
    'tnt': ('tnt', {'allow_underwater_bit': 0},
            {'explode_bit': ('unstable', t_bool_bit)}),
    'suspicious_sand': ('suspicious_sand', {'hanging': 0}, {}),
    'suspicious_gravel': ('suspicious_gravel', {'hanging': 0}, {}),
    'sniffer_egg': ('sniffer_egg', {},
                    {'cracked_state': ('hatch', t_cracked)}),
    'pumpkin_stem': ('pumpkin_stem', {'facing_direction': 0}, {}),
    'melon_stem': ('melon_stem', {'facing_direction': 0}, {}),
    'pink_petals': ('pink_petals', {},
                    {'growth': ('flower_amount', lambda v: v - 1)}),
    'grindstone': ('grindstone', {},
                   {'attachment': ('face', t_grindstone_attach),
                    'direction': ('facing', t_facing_dir)}),
    'hopper': ('hopper', {},
               {'facing_direction': ('facing', t_facing_dir6),
                'toggle_bit': ('enabled', lambda v: 0 if v else 1)}),
    'sea_pickle': ('sea_pickle', {'dead_bit': 0},
                   {'cluster_count': ('pickles', lambda v: v - 1)}),
    # 植木鉢（中身は区別しない）
    'flower_pot': ('flower_pot', {'update_bit': 0}, {}),
    'shulker_box': ('undyed_shulker_box', {}, {}),
    'bamboo': ('bamboo', {},
               {'age_bit': ('age', lambda v: 1 if v == 1 else 0),
                'bamboo_leaf_size': ('leaves',
                    lambda v: {'none': 'no_leaves', 'small': 'small_leaves',
                               'large': 'large_leaves'}.get(v)),
                'bamboo_stalk_thickness': ('stage',
                    lambda v: 'thick' if v == 1 else 'thin')}),
}

# 条件付きエイリアス：(条件dict, Bedrock 名, 固定プロパティ, 追加プロパティ対応)
# 条件は Java プロパティの値と一致すること。最初に一致したものを使う。
COND_ALIASES = {
    'water': [
        ({'level': 0}, 'water', {'liquid_depth': 0}, {}),
        (None, 'flowing_water', {}, {'liquid_depth': ('level', t_id)}),
    ],
    'lava': [
        ({'level': 0}, 'lava', {'liquid_depth': 0}, {}),
        (None, 'flowing_lava', {}, {'liquid_depth': ('level', t_id)}),
    ],
    'furnace': [
        ({'lit': True}, 'lit_furnace', {}, {'facing_direction': ('facing', t_facing_dir6)}),
        (None, 'furnace', {}, {'facing_direction': ('facing', t_facing_dir6)}),
    ],
    'redstone_lamp': [
        ({'lit': True}, 'lit_redstone_lamp', {}, {}),
        (None, 'redstone_lamp', {}, {}),
    ],
    'redstone_ore': [
        ({'lit': True}, 'lit_redstone_ore', {}, {}),
        (None, 'redstone_ore', {}, {}),
    ],
    'deepslate_redstone_ore': [
        ({'lit': True}, 'lit_deepslate_redstone_ore', {}, {}),
        (None, 'deepslate_redstone_ore', {}, {}),
    ],
    'cave_vines': [
        ({'berries': True}, 'cave_vines_head_with_berries', {},
         {'growing_plant_age': ('age', t_id)}),
        (None, 'cave_vines', {}, {'growing_plant_age': ('age', t_id)}),
    ],
    'cave_vines_plant': [
        ({'berries': True}, 'cave_vines_body_with_berries',
         {'growing_plant_age': 0}, {}),
        (None, 'cave_vines', {'growing_plant_age': 0}, {}),
    ],
    'redstone_torch': [
        ({'lit': True}, 'redstone_torch', {'torch_facing_direction': 'top'}, {}),
        (None, 'unlit_redstone_torch', {'torch_facing_direction': 'top'}, {}),
    ],
    'redstone_wall_torch': [
        ({'lit': True}, 'redstone_torch', {},
         {'torch_facing_direction': ('facing', t_torch_facing)}),
        (None, 'unlit_redstone_torch', {},
         {'torch_facing_direction': ('facing', t_torch_facing)}),
    ],
    'comparator': [
        ({'powered': True}, 'powered_comparator', {},
         {'minecraft:cardinal_direction': ('facing', t_id),
          'output_subtract_bit': ('mode', lambda v: 1 if v == 'subtract' else 0),
          'output_lit_bit': ('powered', t_bool_bit)}),
        (None, 'unpowered_comparator', {},
         {'minecraft:cardinal_direction': ('facing', t_id),
          'output_subtract_bit': ('mode', lambda v: 1 if v == 'subtract' else 0),
          'output_lit_bit': ('powered', t_bool_bit)}),
    ],
    'repeater': [
        ({'powered': True}, 'powered_repeater', {},
         {'minecraft:cardinal_direction': ('facing', t_id),
          'repeater_delay': ('delay', lambda v: v - 1)}),
        (None, 'unpowered_repeater', {},
         {'minecraft:cardinal_direction': ('facing', t_id),
          'repeater_delay': ('delay', lambda v: v - 1)}),
    ],
}

# 木材ごとの名前（Bedrock 側の表記ゆれ：dark_oak は darkoak、hanging は dark_oak）
_WOOD_STANDING_SIGN = {
    'oak': 'standing_sign', 'spruce': 'spruce_standing_sign',
    'birch': 'birch_standing_sign', 'jungle': 'jungle_standing_sign',
    'acacia': 'acacia_standing_sign', 'dark_oak': 'darkoak_standing_sign',
    'cherry': 'cherry_standing_sign', 'mangrove': 'mangrove_standing_sign',
    'crimson': 'crimson_standing_sign', 'warped': 'warped_standing_sign',
    'bamboo': 'bamboo_standing_sign',
}
_WOOD_WALL_SIGN = {
    'oak': 'wall_sign', 'spruce': 'spruce_wall_sign',
    'birch': 'birch_wall_sign', 'jungle': 'jungle_wall_sign',
    'acacia': 'acacia_wall_sign', 'dark_oak': 'darkoak_wall_sign',
    'cherry': 'cherry_wall_sign', 'mangrove': 'mangrove_wall_sign',
    'crimson': 'crimson_wall_sign', 'warped': 'warped_wall_sign',
    'bamboo': 'bamboo_wall_sign',
}
_WOOD_HANGING_SIGN = {
    'oak': 'oak_hanging_sign', 'spruce': 'spruce_hanging_sign',
    'birch': 'birch_hanging_sign', 'jungle': 'jungle_hanging_sign',
    'acacia': 'acacia_hanging_sign', 'dark_oak': 'dark_oak_hanging_sign',
    'cherry': 'cherry_hanging_sign', 'mangrove': 'mangrove_hanging_sign',
    'crimson': 'crimson_hanging_sign', 'warped': 'warped_hanging_sign',
    'bamboo': 'bamboo_hanging_sign',
}
_WOOD_DOOR = {
    'oak': 'wooden_door', 'spruce': 'spruce_door', 'birch': 'birch_door',
    'jungle': 'jungle_door', 'acacia': 'acacia_door', 'dark_oak': 'dark_oak_door',
    'cherry': 'cherry_door', 'mangrove': 'mangrove_door',
    'crimson': 'crimson_door', 'warped': 'warped_door', 'bamboo': 'bamboo_door',
}
_WOOD_TRAPDOOR = {
    'oak': 'trapdoor', 'spruce': 'spruce_trapdoor', 'birch': 'birch_trapdoor',
    'jungle': 'jungle_trapdoor', 'acacia': 'acacia_trapdoor',
    'dark_oak': 'dark_oak_trapdoor', 'cherry': 'cherry_trapdoor',
    'mangrove': 'mangrove_trapdoor', 'crimson': 'crimson_trapdoor',
    'warped': 'warped_trapdoor', 'bamboo': 'bamboo_trapdoor',
}
_WOOD_BUTTON = {
    'oak': 'wooden_button', 'spruce': 'spruce_button', 'birch': 'birch_button',
    'jungle': 'jungle_button', 'acacia': 'acacia_button',
    'dark_oak': 'dark_oak_button', 'cherry': 'cherry_button',
    'mangrove': 'mangrove_button', 'crimson': 'crimson_button',
    'warped': 'warped_button', 'bamboo': 'bamboo_button',
}
_WOOD_PLATE = {
    'oak': 'wooden_pressure_plate', 'spruce': 'spruce_pressure_plate',
    'birch': 'birch_pressure_plate', 'jungle': 'jungle_pressure_plate',
    'acacia': 'acacia_pressure_plate', 'dark_oak': 'dark_oak_pressure_plate',
    'cherry': 'cherry_pressure_plate', 'mangrove': 'mangrove_pressure_plate',
    'crimson': 'crimson_pressure_plate', 'warped': 'warped_pressure_plate',
    'bamboo': 'bamboo_pressure_plate',
}
_WOOD_FENCE_GATE = {
    'oak': 'fence_gate', 'spruce': 'spruce_fence_gate',
    'birch': 'birch_fence_gate', 'jungle': 'jungle_fence_gate',
    'acacia': 'acacia_fence_gate', 'dark_oak': 'dark_oak_fence_gate',
    'cherry': 'cherry_fence_gate', 'mangrove': 'mangrove_fence_gate',
    'crimson': 'crimson_fence_gate', 'warped': 'warped_fence_gate',
    'bamboo': 'bamboo_fence_gate',
}
_WOOD_SLAB = {  # wooden_slab の wood_type に入るのは6種だけ
    'oak': 'oak', 'spruce': 'spruce', 'birch': 'birch', 'jungle': 'jungle',
    'acacia': 'acacia', 'dark_oak': 'dark_oak',
}
_WOOD_SLAB_OWN = {  # 専用ブロックがあるもの
    'cherry': 'cherry_slab', 'mangrove': 'mangrove_slab',
    'crimson': 'crimson_slab', 'warped': 'warped_slab', 'bamboo': 'bamboo_slab',
}
_WOOD_STAIRS = {
    'oak': 'oak_stairs', 'spruce': 'spruce_stairs', 'birch': 'birch_stairs',
    'jungle': 'jungle_stairs', 'acacia': 'acacia_stairs',
    'dark_oak': 'dark_oak_stairs', 'cherry': 'cherry_stairs',
    'mangrove': 'mangrove_stairs', 'crimson': 'crimson_stairs',
    'warped': 'warped_stairs', 'bamboo': 'bamboo_stairs',
}
_WOOD_PLANKS_OWN = {
    'cherry': 'cherry_planks', 'mangrove': 'mangrove_planks',
    'crimson': 'crimson_planks', 'warped': 'warped_planks',
    'bamboo': 'bamboo_planks',
}

def build_aliases():
    """木材ごとのブロック（看板・ドア・薄板・階段・板材・原木・木材）を生成する。"""
    out = {}
    woods = list(_WOOD_DOOR)
    for w in woods:
        # 看板
        out[f'{w}_sign'] = (_WOOD_STANDING_SIGN[w], {},
                            {'ground_sign_direction': ('rotation', t_id)})
        out[f'{w}_wall_sign'] = (_WOOD_WALL_SIGN[w], {},
                                 {'facing_direction': ('facing', t_facing_dir6)})
        out[f'{w}_wall_hanging_sign'] = (
            _WOOD_HANGING_SIGN[w],
            {'attached_bit': 1, 'hanging': 0, 'ground_sign_direction': 0},
            {'facing_direction': ('facing', t_facing_dir6)})
        out[f'{w}_hanging_sign'] = (
            _WOOD_HANGING_SIGN[w], {},
            {'attached_bit': ('attached', t_bool_bit),
             'ground_sign_direction': ('rotation', t_id),
             'facing_direction': ('*', t_hanging_sign_facing),
             'hanging': ('attached', lambda v: 0 if v else 1)})
        # ドア・トラップドア・ボタン・感圧板・フェンスゲート
        out[f'{w}_door'] = (_WOOD_DOOR[w], {},
                            {'direction': ('facing', t_facing_dir),
                             'door_hinge_bit': ('hinge', t_hinge_bit),
                             'open_bit': ('open', t_bool_bit),
                             'upper_block_bit': ('half', t_half_upper)})
        out[f'{w}_trapdoor'] = (_WOOD_TRAPDOOR[w], {},
                                {'direction': ('facing', t_facing_dir),
                                 'open_bit': ('open', t_bool_bit),
                                 'upside_down_bit': ('half', t_half_upside_down)})
        out[f'{w}_button'] = (_WOOD_BUTTON[w], {},
                              {'button_pressed_bit': ('powered', t_bool_bit),
                               'facing_direction': ('*', t_button_facing)})
        out[f'{w}_pressure_plate'] = (_WOOD_PLATE[w], {},
                                      {'redstone_signal': ('powered', t_bool_bit)})
        out[f'{w}_fence_gate'] = (_WOOD_FENCE_GATE[w], {},
                                  {'direction': ('facing', t_facing_dir),
                                   'open_bit': ('open', t_bool_bit),
                                   'in_wall_bit': ('in_wall', t_bool_bit)})
        # 階段・薄板・板材
        out[f'{w}_stairs'] = (_WOOD_STAIRS[w], {},
                              {'upside_down_bit': ('half', t_half_upside_down),
                               'weirdo_direction': ('facing', t_facing_dir)})
        if w in _WOOD_SLAB:
            out[f'{w}_slab'] = ('wooden_slab', {'wood_type': _WOOD_SLAB[w]},
                               {'minecraft:vertical_half': ('half', t_id)})
        elif w in _WOOD_SLAB_OWN:
            out[f'{w}_slab'] = (_WOOD_SLAB_OWN[w], {},
                               {'minecraft:vertical_half': ('half', t_id)})
        # 板材・原木は Bedrock でも同名（oak_planks / oak_log など）。
        # 樹皮ブロック：6種は wood（wood_type＋stripped_bit）、
        # 桜・マングローブ・真紅・歪み・竹は専用ブロック名。
        if w in ('oak', 'spruce', 'birch', 'jungle', 'acacia', 'dark_oak'):
            out[f'{w}_wood'] = ('wood', {'wood_type': w, 'stripped_bit': 0},
                               {'pillar_axis': ('axis', t_axis)})
            out[f'stripped_{w}_wood'] = ('wood', {'wood_type': w, 'stripped_bit': 1},
                                        {'pillar_axis': ('axis', t_axis)})
        elif w == 'cherry':
            out['cherry_wood'] = ('cherry_wood', {'stripped_bit': 0},
                                  {'pillar_axis': ('axis', t_axis)})
            out['stripped_cherry_wood'] = ('stripped_cherry_wood', {'stripped_bit': 1},
                                           {'pillar_axis': ('axis', t_axis)})
        elif w == 'mangrove':
            out['mangrove_wood'] = ('mangrove_wood', {'stripped_bit': 0},
                                    {'pillar_axis': ('axis', t_axis)})
            out['stripped_mangrove_wood'] = ('stripped_mangrove_wood', {'stripped_bit': 1},
                                             {'pillar_axis': ('axis', t_axis)})
        elif w == 'crimson':
            out['crimson_hyphae'] = ('crimson_hyphae', {}, {'pillar_axis': ('axis', t_axis)})
            out['stripped_crimson_hyphae'] = ('stripped_crimson_hyphae', {},
                                              {'pillar_axis': ('axis', t_axis)})
        elif w == 'warped':
            out['warped_hyphae'] = ('warped_hyphae', {}, {'pillar_axis': ('axis', t_axis)})
            out['stripped_warped_hyphae'] = ('stripped_warped_hyphae', {},
                                             {'pillar_axis': ('axis', t_axis)})
        elif w == 'bamboo':
            out['bamboo_block'] = ('bamboo_block', {}, {'pillar_axis': ('axis', t_axis)})
            out['stripped_bamboo_block'] = ('stripped_bamboo_block', {},
                                            {'pillar_axis': ('axis', t_axis)})
        # 苗木：sapling_type がある6種だけ。桜は cherry_sapling（同名）、
        # マングローブは mangrove_propagule（同名）のためエイリアス不要。
        if w in ('oak', 'spruce', 'birch', 'jungle', 'acacia', 'dark_oak'):
            out[f'{w}_sapling'] = ('sapling', {'sapling_type': w},
                                  {'age_bit': ('stage', t_bool_bit)})
    return out

# 特殊な名前（接頭辞・接尾辞で決まるもの）
# Java 名がこの接尾辞で終わる場合の Bedrock 名・固定プロパティ。
SUFFIX_ALIASES = [
    ('_wall_banner', 'wall_banner', {},
     {'facing_direction': ('facing', t_facing_dir6)}),
    ('_banner', 'standing_banner', {},
     {'ground_sign_direction': ('rotation', t_id)}),
    ('_bed', 'bed', {},
     {'direction': ('facing', t_facing_dir),
      'head_piece_bit': ('part', t_head_bit),
      'occupied_bit': ('occupied', t_bool_bit)}),
    ('_wall_skull', 'skull', {},
     {'facing_direction': ('facing', t_facing_dir6)}),
    ('_skull', 'skull', {'facing_direction': 1}, {}),
    ('_wall_head', 'skull', {},
     {'facing_direction': ('facing', t_facing_dir6)}),
    ('_head', 'skull', {'facing_direction': 1}, {}),
    ('_coral_wall_fan', None, {}, {}),  # 専用処理（色は名前から）
    ('_coral_fan', None, {}, {}),       # 専用処理
]

# サンゴの色（Java 名の先頭）
CORAL_COLORS = {
    'tube': 'blue', 'brain': 'pink', 'bubble': 'purple',
    'fire': 'red', 'horn': 'yellow',
}

# バイオーム：Java 名 -> Bedrock 名（同名のものは書かない）
BIOME_ALIASES = {
    'windswept_hills': 'extreme_hills',
    'windswept_gravelly_hills': 'extreme_hills_edge',
    'windswept_forest': 'extreme_hills_plus_trees',
    'windswept_savanna': 'savanna_plateau',
    'snowy_taiga': 'cold_taiga',
    'snowy_plains': 'ice_plains',
    'snowy_beach': 'cold_beach',
    'snowy_slopes': 'ice_mountains',
    'frozen_peaks': 'ice_mountains',
    'jagged_peaks': 'ice_mountains',
    'grove': 'cold_taiga_hills',
    'meadow': 'plains',
    'stony_peaks': 'extreme_hills',
    'stony_shore': 'stone_beach',
    'mushroom_fields': 'mushroom_island',
    'nether_wastes': 'hell',
    'the_end': 'the_end',
    'end_highlands': 'the_end',
    'end_midlands': 'the_end',
    'end_barrens': 'the_end',
    'small_end_islands': 'the_end',
    'the_void': 'the_end',
    'badlands': 'mesa',
    'wooded_badlands': 'mesa_plateau',
    'eroded_badlands': 'mesa_plateau',
    'dark_forest': 'roofed_forest',
    'ice_spikes': 'ice_plains_spikes',
    'old_growth_birch_forest': 'birch_forest_mutated',
    'old_growth_pine_taiga': 'mega_taiga',
    'old_growth_spruce_taiga': 'mega_taiga_hills',
    'sparse_jungle': 'jungle_edge',
    'swamp': 'swampland',
    'soul_sand_valley': 'soulsand_valley',
    'deep_dark': 'plains',
    'dripstone_caves': 'extreme_hills',
    'lush_caves': 'jungle',
    'cherry_grove': 'cherry_grove',
}
