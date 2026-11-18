#!/usr/bin/env python3
"""DetergentMC Bedrock バージョン別アイテム対応表を生成する。

入力（公開データのみ）：
  - detergentmc-bedrock/src/main/java/.../BedrockItemIds.java
    （Java 765 アイテムID順の Bedrock ネットワークID表。コメント行の
    Java アイテム名も一緒に読む。値自体は 1.26 系のもの）
  - PMMP BedrockData <tag>/required_item_list.json
    （Bedrock のアイテム名 -> ランタイムID。バージョンごと。
    例：https://raw.githubusercontent.com/pmmp/BedrockData/bedrock-1.20.50/required_item_list.json）

出力：
  - detergentmc-bedrock/src/main/resources/io/detergentmc/bedrock/
    bedrock_item_ids_<tag>.bin（Java 765 順の (network_id, metadata) ペア。
    各項目は int32 LE の network_id と int32 LE の metadata の 2 語。
    network_id -1 は未対応。1.20.50 は BedrockItemIds.java の表が
    既にあるため生成しない）

方針：
  1. 1.26.30 の一覧と既存テーブルから Java 名 -> Bedrock 名の対応を
     決める（完全一致、なければ同じ ID を持つ名前から最も似たもの）。
     その対応で既存テーブルの全 1312 件が再現できることを検証する。
  2. 各バージョンの一覧で同じ Bedrock 名の ID を引く。
  3. 古いバージョンで名前が無いもの（1.20 系では種類ごとに ID を
     共有していたブロック類）は SHARED_GROUPS（既知のレガシー仕様：
     sapling・leaves・leaves2・sand・dirt・bed・stained_glass・
     red_sandstone など）の共有 ID とメタ（種類の位置）で出す。
  4. どれにも当てはまらないものは -1（石に代替）にして件数を報告する。

実行例：
  python3 generate_item_tables.py
（/tmp/be-items/ に required_item_list.json を保存し、再実行時は流用する）
"""
import difflib
import json
import os
import re
import struct
import sys
import urllib.request

HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.dirname(os.path.dirname(HERE))
JAVA_TABLE = os.path.join(
    REPO, 'detergentmc-bedrock/src/main/java/io/detergentmc/bedrock/BedrockItemIds.java')
OUT_DIR = os.path.join(
    REPO, 'detergentmc-bedrock/src/main/resources/io/detergentmc/bedrock')
CACHE_DIR = '/tmp/be-items'

PMMP_BASE = 'https://raw.githubusercontent.com/pmmp/BedrockData'

# Bedrock プロトコル番号 -> PMMP BedrockData のタグ。
# 630（1.20.50）は BedrockItemIds.java の表があるためここには含めない。
# タグが無いバージョンは最も近い既存タグを使う（値はそのタグのもの）。
VERSION_TAGS = {
    589: 'bedrock-1.20.0', 594: 'bedrock-1.20.10', 618: 'bedrock-1.20.30',
    622: 'bedrock-1.20.40', 630: 'bedrock-1.20.50', 649: 'bedrock-1.20.60',
    662: 'bedrock-1.20.70', 671: 'bedrock-1.20.80',
    685: 'bedrock-1.21.0', 686: 'bedrock-1.21.0',
    712: 'bedrock-1.21.20', 729: 'bedrock-1.21.30', 748: 'bedrock-1.21.40',
    766: 'bedrock-1.21.50', 776: 'bedrock-1.21.60', 786: 'bedrock-1.21.70',
    800: 'bedrock-1.21.80', 818: 'bedrock-1.21.90', 819: 'bedrock-1.21.93',
    827: 'bedrock-1.21.93', 844: 'bedrock-1.21.111', 859: 'bedrock-1.21.120',
    898: 'bedrock-1.21.130',
    924: 'bedrock-1.26.0', 944: 'bedrock-1.26.10', 975: 'bedrock-1.26.20',
    1001: 'bedrock-1.26.30', 2168: 'bedrock-1.26.30', 2169: 'bedrock-1.26.30',
    2193: 'bedrock-1.26.30',
}
ANCHOR_TAG = 'bedrock-1.26.30'

# Java 名 -> Bedrock 名の例外。MS Learn の一覧の ID と 1.26.30 の
# 一覧の ID が食い違う 4 件、名前自体が違うもの。
# ID は常にバージョンごとの一覧から取り、テーブル値は使わない。
NAME_OVERRIDES = {
    'light': 'light_block',
    'witch_spawn_egg': 'witch_spawn_egg',
    'end_crystal': 'end_crystal',
    'beetroot_soup': 'beetroot_soup',
    'rooted_dirt': 'dirt_with_roots',
    'flowering_azalea_leaves': 'azalea_leaves_flowered',
    'stone_slab': 'smooth_stone_slab',
    'jack_o_lantern': 'lit_pumpkin',
    'lily_pad': 'waterlily',
    'terracotta': 'hardened_clay',
    'oak_door': 'wooden_door',
    'scute': 'turtle_scute',
    'zombified_piglin_spawn_egg': 'zombie_pigman_spawn_egg',
    'item_frame': 'item.frame',
    'glow_item_frame': 'item.glow_frame',
    'map': 'filled_map',
    'frogspawn': 'frog_spawn',
}

def _group(members):
    """順序リスト（位置がメタ）か明示 {Java名: メタ} を {Java名: メタ} にする。"""
    if isinstance(members, dict):
        return dict(members)
    return {name: i for i, name in enumerate(members)}


COLORS_16 = ['white', 'orange', 'magenta', 'light_blue', 'yellow', 'lime',
             'pink', 'gray', 'light_gray', 'cyan', 'purple', 'blue',
             'brown', 'green', 'red', 'black']

# 共有 ID + メタ（レガシー Bedrock の仕様。種類の並びは Java の
# レジストリ順＝旧 data 値の順）。Bedrock 名 -> {Java 名: メタ}。
# バージョンの一覧に共有名が無ければ使われない（安全側に倒れる）。
SHARED_GROUPS = {
    'sapling': _group(['oak_sapling', 'spruce_sapling', 'birch_sapling',
                       'jungle_sapling', 'acacia_sapling', 'dark_oak_sapling']),
    'leaves': _group(['oak_leaves', 'spruce_leaves', 'birch_leaves', 'jungle_leaves']),
    'leaves2': _group(['acacia_leaves', 'dark_oak_leaves']),
    'sand': _group(['sand', 'red_sand']),
    'dirt': _group(['dirt', 'coarse_dirt']),
    'bed': _group([c + '_bed' for c in COLORS_16]),
    'banner': _group([c + '_banner' for c in COLORS_16]),
    'stained_glass': _group([c + '_stained_glass' for c in COLORS_16]),
    'stained_glass_pane': _group([c + '_stained_glass_pane' for c in COLORS_16]),
    'stained_hardened_clay': _group([c + '_terracotta' for c in COLORS_16]),
    'concrete': _group([c + '_concrete' for c in COLORS_16]),
    'concrete_powder': _group([c + '_concrete_powder' for c in COLORS_16]),
    'shulker_box': _group([c + '_shulker_box' for c in COLORS_16]),
    'red_sandstone': _group(['red_sandstone', 'chiseled_red_sandstone',
                             'smooth_red_sandstone']),
    # 樹皮付き丸太の太い方（wood）は 1.20.50 で共有 ID。
    'wood': _group(['oak_wood', 'spruce_wood', 'birch_wood', 'jungle_wood',
                    'acacia_wood', 'dark_oak_wood']),
    'stone': _group(['stone', 'granite', 'polished_granite', 'diorite',
                     'polished_diorite', 'andesite', 'polished_andesite']),
    'planks': _group(['oak_planks', 'spruce_planks', 'birch_planks',
                      'jungle_planks', 'acacia_planks', 'dark_oak_planks']),
    'wooden_slab': _group(['oak_slab', 'spruce_slab', 'birch_slab',
                           'jungle_slab', 'acacia_slab', 'dark_oak_slab']),
    # 旧 Bedrock の石ハーフブロックは stone_block_slab(2/3/4)。
    # stone_slab（Java の滑らかな石ハーフブロック）は smooth_stone_slab と同じ。
    'stone_block_slab': {'smooth_stone_slab': 0, 'stone_slab': 0,
                         'sandstone_slab': 1, 'petrified_oak_slab': 2,
                         'cobblestone_slab': 3, 'brick_slab': 4,
                         'stone_brick_slab': 5, 'nether_brick_slab': 6,
                         'quartz_slab': 7},
    'stone_block_slab2': _group(['red_sandstone_slab', 'purpur_slab',
                                 'prismarine_slab', 'dark_prismarine_slab',
                                 'mossy_cobblestone_slab',
                                 'smooth_sandstone_slab',
                                 'red_nether_brick_slab']),
    'stone_block_slab3': _group(['end_stone_brick_slab',
                                 'smooth_red_sandstone_slab',
                                 'polished_andesite_slab', 'andesite_slab',
                                 'diorite_slab', 'polished_diorite_slab',
                                 'granite_slab', 'polished_granite_slab']),
    # stone_block_slab4 の meta 2（石ハーフブロック）は Java に対応物が無い。
    'stone_block_slab4': {'mossy_stone_brick_slab': 0, 'smooth_quartz_slab': 1,
                          'cut_sandstone_slab': 3, 'cut_red_sandstone_slab': 4},
    'skull': {'skeleton_skull': 0, 'wither_skeleton_skull': 1, 'zombie_head': 2,
              'player_head': 3, 'creeper_head': 4, 'dragon_head': 5,
              'piglin_head': 6},
    'stonebrick': _group(['stone_bricks', 'mossy_stone_bricks',
                          'cracked_stone_bricks', 'chiseled_stone_bricks']),
    'monster_egg': _group(['infested_stone', 'infested_cobblestone',
                           'infested_stone_bricks',
                           'infested_mossy_stone_bricks',
                           'infested_cracked_stone_bricks',
                           'infested_chiseled_stone_bricks']),
    'quartz_block': {'chiseled_quartz_block': 1, 'quartz_pillar': 2,
                     'smooth_quartz': 3},
    'purpur_block': {'purpur_pillar': 1},
    'prismarine': {'prismarine_bricks': 1, 'dark_prismarine': 2},
    'anvil': {'chipped_anvil': 1, 'damaged_anvil': 2},
    'coral_block': {'tube_coral_block': 0, 'brain_coral_block': 1,
                    'bubble_coral_block': 2, 'fire_coral_block': 3,
                    'horn_coral_block': 4, 'dead_tube_coral_block': 8,
                    'dead_brain_coral_block': 9, 'dead_bubble_coral_block': 10,
                    'dead_fire_coral_block': 11, 'dead_horn_coral_block': 12},
    'coral': {'tube_coral': 0, 'brain_coral': 1, 'bubble_coral': 2,
              'fire_coral': 3, 'horn_coral': 4, 'dead_tube_coral': 8,
              'dead_brain_coral': 9, 'dead_bubble_coral': 10,
              'dead_fire_coral': 11, 'dead_horn_coral': 12},
    'coral_fan': {'tube_coral_fan': 0, 'brain_coral_fan': 1,
                  'bubble_coral_fan': 2, 'fire_coral_fan': 3,
                  'horn_coral_fan': 4},
    'coral_fan_dead': {'dead_tube_coral_fan': 0, 'dead_brain_coral_fan': 1,
                       'dead_bubble_coral_fan': 2, 'dead_fire_coral_fan': 3,
                       'dead_horn_coral_fan': 4},
    # 旧 Bedrock の塀は cobblestone_wall 共有。meta 順は Bedrock の
    # ブロック data 順（wiki の block data 表に同じ）。
    'cobblestone_wall': {'cobblestone_wall': 0, 'mossy_cobblestone_wall': 1,
                         'granite_wall': 2, 'diorite_wall': 3,
                         'andesite_wall': 4, 'sandstone_wall': 5,
                         'brick_wall': 6, 'stone_brick_wall': 7,
                         'mossy_stone_brick_wall': 8, 'nether_brick_wall': 9,
                         'end_stone_brick_wall': 10, 'prismarine_wall': 11,
                         'red_sandstone_wall': 12, 'red_nether_brick_wall': 13},
}

# 旧 Bedrock の名前への置き換え（Java 名 -> (Bedrock 名, メタ)）。
# モダン名がそのバージョンの一覧に無い場合にだけ使う。
LEGACY_MAP = {
    'grass_block': ('grass', 0),
    'dirt_path': ('grass_path', 0),
    'short_grass': ('tallgrass', 0),
    'fern': ('tallgrass', 1),
    'dead_bush': ('tallgrass', 2),
    'dandelion': ('yellow_flower', 0),
    'poppy': ('red_flower', 0),
    'blue_orchid': ('red_flower', 1),
    'allium': ('red_flower', 2),
    'azure_bluet': ('red_flower', 3),
    'red_tulip': ('red_flower', 4),
    'orange_tulip': ('red_flower', 5),
    'white_tulip': ('red_flower', 6),
    'pink_tulip': ('red_flower', 7),
    'oxeye_daisy': ('red_flower', 8),
    'cornflower': ('red_flower', 9),
    'lily_of_the_valley': ('red_flower', 10),
    'sunflower': ('double_plant', 0),
    'lilac': ('double_plant', 1),
    'tall_grass': ('double_plant', 2),
    'large_fern': ('double_plant', 3),
    'rose_bush': ('double_plant', 4),
    'peony': ('double_plant', 5),
    'wet_sponge': ('sponge', 1),
    'chiseled_sandstone': ('sandstone', 1),
    # smooth と cut は旧 Bedrock では同じ meta 2 に寄せる（近似）。
    'smooth_sandstone': ('sandstone', 2),
    'cut_sandstone': ('sandstone', 2),
    'chiseled_red_sandstone': ('red_sandstone', 1),
    'smooth_red_sandstone': ('red_sandstone', 2),
    'cut_red_sandstone': ('red_sandstone', 2),
    'stripped_oak_wood': ('stripped_oak_log', 0),
    'stripped_spruce_wood': ('stripped_spruce_log', 0),
    'stripped_birch_wood': ('stripped_birch_log', 0),
    'stripped_jungle_wood': ('stripped_jungle_log', 0),
    'stripped_acacia_wood': ('stripped_acacia_log', 0),
    'stripped_dark_oak_wood': ('stripped_dark_oak_log', 0),
    'chain': ('chain', 0),
}


def parse_java_table():
    """(java_names, bedrock_ids) を返す。1312 件。"""
    src = open(JAVA_TABLE, encoding='utf-8').read()
    m = re.search(r'NETWORK_IDS_765 = \{(.*?)\};', src, re.S)
    body = m.group(1)
    values = [int(x) for x in re.findall(r'-?\d+', body)]
    names = []
    for comment in re.findall(r'// (.*)', body):
        names += [n.strip() for n in comment.split(',') if n.strip()]
    assert len(names) == 1312, len(names)
    assert len(values) >= 1312, len(values)
    return names, values[:1312]


def fetch_required_item_list(tag):
    """PMMP の required_item_list.json を取って name -> runtime_id を返す。"""
    os.makedirs(CACHE_DIR, exist_ok=True)
    cached = os.path.join(CACHE_DIR, f'required-item-list-{tag}.json')
    if not os.path.exists(cached):
        url = f'{PMMP_BASE}/{tag}/required_item_list.json'
        try:
            req = urllib.request.Request(url, headers={'User-Agent': 'detergentmc-generator'})
            with urllib.request.urlopen(req, timeout=120) as r:
                data = r.read()
        except Exception as e:
            return None, f'{url}: {e}'
        with open(cached, 'wb') as f:
            f.write(data)
    raw = json.load(open(cached, encoding='utf-8'))
    out = {}
    for key, val in raw.items():
        name = key[10:] if key.startswith('minecraft:') else key
        out[name] = val['runtime_id']
    return out, None


def derive_name_map(java_names, table_ids, anchor):
    """Java 名 -> Bedrock 名。
    名前を優先する（Java 名が一覧にあればそれを使う。ID は一覧の値を
    信頼し、MS Learn のテーブル値は使わない）。名前が一覧に無い場合
    のみ、テーブルの ID と同じ ID を持つ一覧の名前から最も似たものを
    選ぶ（類似度 0.5 未満は採用しない）。"""
    id_to_names = {}
    for name, bid in anchor.items():
        id_to_names.setdefault(bid, []).append(name)
    name_map = {}
    exact = alias = unmapped = 0
    problems = []
    for java_name, table_id in zip(java_names, table_ids):
        if table_id == -1:
            name_map[java_name] = None
            unmapped += 1
            continue
        if java_name in NAME_OVERRIDES:
            name_map[java_name] = NAME_OVERRIDES[java_name]
            exact += 1
            continue
        if java_name in anchor:
            # 名前が一覧にある：その名前を使う（ID は一覧を信頼）。
            name_map[java_name] = java_name
            exact += 1
            continue
        candidates = id_to_names.get(table_id, [])
        best = None
        best_ratio = 0.0
        for c in candidates:
            r = difflib.SequenceMatcher(None, java_name, c).ratio()
            if r > best_ratio:
                best_ratio = r
                best = c
        if best is not None and best_ratio >= 0.5:
            name_map[java_name] = best
            alias += 1
            continue
        # 共有グループの一員なら問題ではない（後で共有 ID で出す）。
        if any(java_name in members for members in SHARED_GROUPS.values()):
            name_map[java_name] = None
            continue
        problems.append((java_name, table_id,
                         f'NO_GOOD_ALIAS best={best} ratio={best_ratio:.2f}'))
        name_map[java_name] = None
    print(f'  name map (anchor {ANCHOR_TAG}): exact={exact} alias={alias} unmapped={unmapped} problems={len(problems)}')
    for p in problems[:40]:
        print('   PROBLEM:', p)
    return name_map, problems


def main():
    java_names, table_ids = parse_java_table()
    print(f'Java table: {len(java_names)} items')
    anchor, err = fetch_required_item_list(ANCHOR_TAG)
    if err:
        print('anchor が取れません:', err)
        return 1
    print(f'anchor {ANCHOR_TAG}: {len(anchor)} names')
    name_map, problems = derive_name_map(java_names, table_ids, anchor)
    if problems:
        print(f'NOTE: name map に {len(problems)} 件の問題あり（curate 対象）。'
              ' 生成は続ける（問題のものは -1 になる）。')
    # 共有グループの検査：グループ内の Java 名が anchor で個別に
    # 存在する場合は、そのグループはそのバージョンでは不要。
    summary = {}
    for protocol in sorted(VERSION_TAGS):
        tag = VERSION_TAGS[protocol]
        registry, err = fetch_required_item_list(tag)
        if err:
            print(f'protocol {protocol} ({tag}): SKIP ({err})')
            summary[protocol] = {'tag': tag, 'skipped': str(err)}
            continue
        out = []  # (network_id, metadata)
        exact_n = shared_n = legacy_n = fallback_n = 0
        missing = []
        for java_name in java_names:
            bedrock_name = name_map[java_name]
            if bedrock_name is not None and bedrock_name in registry:
                out.append((registry[bedrock_name], 0))
                exact_n += 1
                continue
            # 共有グループを探す（Java 名で引く。name_map が None でも見る）。
            hit = None
            for shared_name, members in SHARED_GROUPS.items():
                if java_name in members and shared_name in registry:
                    hit = (registry[shared_name], members[java_name])
                    break
            if hit is not None:
                out.append(hit)
                shared_n += 1
                continue
            # 旧 Bedrock の名前への置き換え。
            leg = LEGACY_MAP.get(java_name)
            if leg is not None and leg[0] in registry:
                out.append((registry[leg[0]], leg[1]))
                legacy_n += 1
                continue
            out.append((-1, 0))
            fallback_n += 1
            missing.append((java_name, bedrock_name))
        tag_file = tag.replace('bedrock-', '').replace('.', '_')
        path = os.path.join(OUT_DIR, f'bedrock_item_ids_{tag_file}.bin')
        with open(path, 'wb') as f:
            for nid, meta in out:
                f.write(struct.pack('<ii', nid, meta))
        print(f'protocol {protocol} ({tag}): exact={exact_n} shared={shared_n} '
              f'legacy={legacy_n} fallback={fallback_n} -> {os.path.basename(path)}')
        if missing:
            print('   missing examples:', missing[:12])
        summary[protocol] = {'tag': tag, 'exact': exact_n, 'shared': shared_n,
                             'legacy': legacy_n, 'fallback': fallback_n,
                             'missing_names': [m[1] for m in missing]}
    with open(os.path.join(HERE, 'item_tables_summary.json'), 'w', encoding='utf-8') as f:
        json.dump(summary, f, ensure_ascii=False, indent=1)
    print('summary -> tools/item_tables_summary.json')
    return 0


if __name__ == '__main__':
    sys.exit(main())
