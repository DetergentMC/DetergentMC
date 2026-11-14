#!/usr/bin/env python3
"""DetergentMC Bedrock チャンク対応の対応表を生成する。

入力（公開データのみ）：
  - PrismarineJS minecraft-data pc 1.20.3 blocks.json（Java 765 のブロック状態）
  - PrismarineJS minecraft-data pc 1.20.2 biomes.json（Java のバイオーム名・順序）
  - PMMP BedrockData bedrock-1.20.50 タグの canonical_block_states.nbt
    （Bedrock 1.20.50 の正規パレット。ランタイム ID はこの並びの位置）
  - PMMP BedrockData bedrock-1.20.50 タグの biome_id_map.json

出力：
  - BedrockBlockRuntimeIds.java（Java 765 状態ID -> Bedrock ランタイムID、26,644件）
  - BedrockBiomeIds.java（Java バイオーム -> Bedrock バイオームID）
  - 網羅率レポート（標準出力）
"""
import json
import sys
from collections import defaultdict

sys.path.insert(0, '/tmp/gen')
from parse_nbt import parse_canonical
import aliases as A

JAVA_BLOCKS = '/tmp/be-blocks.json'
JAVA_BIOMES = '/tmp/pc-biomes-1.20.2.json'
CANON_NBT = '/tmp/canonical-1.20.50.nbt'
BE_BIOME_MAP = '/tmp/be-biome-id-map.json'
OUT_DIR = '/tmp/gen/out'

STONE_RUNTIME_ID = None  # 後で canonical から決める（minecraft:stone）


def decode_java_state(block, state_id):
    """Java 状態ID -> プロパティ dict。
    最後のプロパティが最も速く変わる混合基数。bool は [true, false] の順。
    （oak_stairs / grass_block / furnace の defaultState で確認済み。）"""
    off = state_id - block['minStateId']
    props = {}
    for st in reversed(block['states']):
        vals = st.get('values')
        n = st['num_values']
        if vals is None:
            vals = [True, False] if st['type'] == 'bool' else list(range(n))
        idx = off % n
        off //= n
        v = vals[idx]
        if isinstance(v, str) and v.lstrip('-').isdigit():
            v = int(v)  # minecraft-data は int 型の値も文字列で持つ
        props[st['name']] = v
    assert off == 0, (block['name'], state_id)
    return props


def resolve_alias(java_name, java_props):
    """(bedrock_name, const_props, prop_map) を返す。見つからなければ None。"""
    # light_gray は Bedrock では silver
    if java_name == 'light_gray_glazed_terracotta':
        return ('silver_glazed_terracotta', {}, {})
    # 条件付き
    if java_name in A.COND_ALIASES:
        for cond, bname, const, pmap in A.COND_ALIASES[java_name]:
            if cond is None or all(java_props.get(k) == v for k, v in cond.items()):
                return bname, const, pmap
    full = dict(A.ALIASES)
    full.update(A.build_aliases())
    # 薄板：Java は type=top/bottom/double。Bedrock は single と double で別ブロック。
    # single は minecraft:vertical_half <- type、double は double_系ブロック。
    if java_name.endswith('_slab'):
        base = full.get(java_name)
        if base is not None:
            bname, const, _pmap = base
        else:
            bname, const = java_name, {}
        if java_props.get('type') == 'double':
            if bname == 'wooden_slab':
                dname = 'double_wooden_slab'
            elif bname.startswith('stone_block_slab'):
                dname = 'double_' + bname
            elif 'cut_copper_slab' in bname:
                # double_cut_copper_slab / oxidized_double_cut_copper_slab 等
                dname = bname.replace('cut_copper_slab', 'double_cut_copper_slab')
            else:
                dname = bname[:-len('_slab')] + '_double_slab'
            # Bedrock のダブルスラブも vertical_half を持つ（bottom が既定）
            return (dname, dict(const, **{'minecraft:vertical_half': 'bottom'}), {})
        return (bname, const, {'minecraft:vertical_half': ('type', A.t_id)})
    if java_name in full:
        return full[java_name]
    # サンゴ（壁・床・死）
    for prefix, color in A.CORAL_COLORS.items():
        if java_name == f'{prefix}_coral_fan':
            return ('coral_fan', {'coral_color': color, 'coral_fan_direction': 0}, {})
        if java_name == f'dead_{prefix}_coral_fan':
            return ('coral_fan_dead', {'coral_color': color, 'coral_fan_direction': 0}, {})
        if java_name == f'{prefix}_coral_wall_fan':
            return wall_fan_alias(color, dead=False)
        if java_name == f'dead_{prefix}_coral_wall_fan':
            return wall_fan_alias(color, dead=True)
    # 接尾辞
    for suffix, bname, const, pmap in A.SUFFIX_ALIASES:
        if bname is None:
            continue
        if java_name.endswith(suffix):
            return bname, const, pmap
    # 植木鉢
    if java_name.startswith('potted_'):
        return ('flower_pot', {'update_bit': 0}, {})
    # 同名
    return (java_name, {}, {})


def wall_fan_alias(color, dead):
    """壁サンゴは Bedrock では色ごとに coral_fan_hang / hang2 / hang3 に分かれる。
    hang_type_bit が各ブロック内の2色を分ける。"""
    mapping = {
        'blue': ('coral_fan_hang', 0), 'pink': ('coral_fan_hang', 1),
        'purple': ('coral_fan_hang2', 0), 'red': ('coral_fan_hang2', 1),
        'yellow': ('coral_fan_hang3', 0),
    }
    bname, hang_type = mapping[color]
    return (bname, {'coral_hang_type_bit': hang_type, 'dead_bit': 1 if dead else 0},
            {'coral_direction': ('facing', A.t_facing_dir)})


def match_score(candidate_states, expected, java_props):
    """Bedrock 候補の状態と、Java 状態から期待される値の一致数。
    (score, all_props_matched) を返す。"""
    score = 0
    for k, v in candidate_states.items():
        if k in expected:
            if expected[k] == v:
                score += 1
        elif k in java_props:
            jv = java_props[k]
            if isinstance(jv, bool):
                jv = 1 if jv else 0
            if jv == v:
                score += 1
    return score


def expected_props(const, pmap, java_props):
    expected = dict(const)
    merged = dict(A.COMMON_PROP_MAP)
    merged.update(pmap)
    for bprop, (jprop, tr) in merged.items():
        if jprop == '*':
            # 関数に Java プロパティ全体を渡す（方向ビットマスクなど）
            try:
                v = tr(java_props)
            except Exception:
                v = None
            if v is not None:
                expected[bprop] = v
        elif jprop in java_props:
            try:
                v = tr(java_props[jprop])
            except Exception:
                v = None
            if v is not None:
                expected[bprop] = v
    return expected


def main():
    global STONE_RUNTIME_ID
    entries, slices, raw = parse_canonical(CANON_NBT)
    canon_index = defaultdict(list)
    for i, e in enumerate(entries):
        canon_index[e['name'].replace('minecraft:', '')].append((i, e['states']))
    STONE_RUNTIME_ID = canon_index['stone'][0][0]
    air_id = canon_index['air'][0][0]
    print(f'canonical entries: {len(entries)}  air={air_id} stone={STONE_RUNTIME_ID}'
          f' version={entries[0]["version"]}')

    blocks = json.load(open(JAVA_BLOCKS))
    blocks.sort(key=lambda b: b['minStateId'])
    max_state = max(b['maxStateId'] for b in blocks)
    print(f'java blocks: {len(blocks)}  max state id: {max_state}')
    table = [STONE_RUNTIME_ID] * (max_state + 1)
    waterlogged = [False] * (max_state + 1)
    stats = defaultdict(int)
    kinds = defaultdict(lambda: defaultdict(int))
    stone_names = defaultdict(int)
    name_only = defaultdict(int)
    partial = defaultdict(int)
    for b in blocks:
        jname = b['name']
        for sid in range(b['minStateId'], b['maxStateId'] + 1):
            props = decode_java_state(b, sid)
            if props.get('waterlogged') is True:
                waterlogged[sid] = True
            alias = resolve_alias(jname, props)
            if alias is None:
                table[sid] = STONE_RUNTIME_ID
                stats['stone'] += 1
                stone_names[jname] += 1
                continue
            bname, const, pmap = alias
            cands = canon_index.get(bname)
            if not cands:
                table[sid] = STONE_RUNTIME_ID
                stats['stone'] += 1
                stone_names[jname + ' -> ' + bname + ' (missing)'] += 1
                continue
            expected = expected_props(const, pmap, props)
            best_id, best_score, best_nprops = None, -1, 0
            for rid, states in cands:
                sc = match_score(states, expected, props)
                if sc > best_score:
                    best_id, best_score, best_nprops = rid, sc, len(states)
            table[sid] = best_id
            if best_nprops == 0:
                stats['exact'] += 1
                kinds[jname]['exact'] += 1
            elif best_score == best_nprops:
                stats['exact'] += 1
                kinds[jname]['exact'] += 1
            elif best_score > 0:
                stats['partial'] += 1
                kinds[jname]['partial'] += 1
                partial[jname] += 1
            else:
                stats['name_only'] += 1
                kinds[jname]['name_only'] += 1
                name_only[jname] += 1
    total = max_state + 1
    print(f'total java states: {total}')
    for k in ('exact', 'partial', 'name_only', 'stone'):
        print(f'  {k}: {stats[k]} ({stats[k]/total*100:.1f}%)')
    print(f'  waterlogged states: {sum(waterlogged)}')
    print(f'stone-fallback names ({len(stone_names)}):')
    for name, n in sorted(stone_names.items(), key=lambda kv: -kv[1]):
        print(f'  {name}: {n} states')
    print(f'name-only (no prop matched) names ({len(name_only)}):')
    for name, n in sorted(name_only.items(), key=lambda kv: -kv[1])[:30]:
        print(f'  {name}: {n} states')
    print(f'partial names ({len(partial)}):')
    for name, n in sorted(partial.items(), key=lambda kv: -kv[1])[:30]:
        print(f'  {name}: {n} states')
    # 既知の対応のスポットチェック
    checks = {
        'air': 0, 'stone': 1, 'grass_block': 8, 'dirt': None, 'water': None,
        'oak_log': 130, 'sand': None, 'bedrock': None, 'cobblestone': None,
    }
    by_name = {b['name']: b for b in blocks}
    for name, sid in checks.items():
        if sid is None:
            sid = by_name[name]['minStateId']
        b = by_name[name]
        print(f'check {name} state {sid} -> runtime {table[sid]}'
              f' ({entries[table[sid]]["name"]} {entries[table[sid]]["states"]})')
    json.dump({'table': table,
               'waterlogged': [i for i, w in enumerate(waterlogged) if w],
               'stats': dict(stats)},
              open('/tmp/gen/out-table.json', 'w'))
    print('saved /tmp/gen/out-table.json')
    # バイオーム
    java_biomes = json.load(open(JAVA_BIOMES))
    be_map = json.load(open(BE_BIOME_MAP))
    biome_table = []
    biome_names = []
    unmapped = []
    for jb in sorted(java_biomes, key=lambda x: x['id']):
        name = jb['name']
        be_name = A.BIOME_ALIASES.get(name, name)
        if be_name in be_map:
            biome_table.append(be_map[be_name])
            biome_names.append((name, be_name, be_map[be_name]))
        else:
            biome_table.append(be_map['plains'])
            unmapped.append(name)
            biome_names.append((name, be_name + ' -> plains', be_map['plains']))
    print(f'biomes: java {len(java_biomes)} -> bedrock ids, unmapped: {unmapped}')
    for name, be_name, bid in biome_names:
        print(f'  {name} -> {be_name} ({bid})')
    json.dump({'biome_table': biome_table, 'biome_names': biome_names},
              open('/tmp/gen/out-biomes.json', 'w'))


if __name__ == '__main__':
    main()
