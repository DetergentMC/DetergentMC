#!/usr/bin/env python3
"""verify-771-tables.py から verify-772-tables.py を作る（1回だけの作業用）。

機械的なずらし（771→772、770→771）と、772で意味が変わる箇所
（Bの補正なし・新音は lava_chicken だけ、D(b)オラクルの隣接元が770→771、
Gの属性35件同士、Hの772→771反転・770追加、Iのダメージ種別772）をまとめて行う。
"""
import shutil
import sys
from pathlib import Path

SCRIPT_DIR = Path(__file__).resolve().parent
SRC = SCRIPT_DIR / "verify-771-tables.py"
DST = SCRIPT_DIR / "verify-772-tables.py"

s = SRC.read_text(encoding="utf-8")
orig = s
failed = []


def rep(old, new, label):
    global s
    n = s.count(old)
    if n != 1:
        failed.append(f"{label}: 一致 {n} 件 (期待 1 件)")
        return
    s = s.replace(old, new)


def rep_all(old, new, label, expected):
    global s
    n = s.count(old)
    if n != expected:
        failed.append(f"{label}: 一致 {n} 件 (期待 {expected} 件)")
        return
    s = s.replace(old, new)


def replace_section(start_marker, end_marker, new_text, label):
    """start_marker を含む行から end_marker を含む行の直前までを置き換える。"""
    global s
    i = s.find(start_marker)
    j = s.find(end_marker, i)
    if i < 0 or j < 0 or j <= i:
        failed.append(f"{label}: 区間が見つからない")
        return
    s = s[:i] + new_text + s[j:]


# --- docstring ---
i = s.find('"""RegistryRemapper.java の 771')
j = s.find('"""', i + 3)
assert i >= 0 and j > i
s = s[:i] + '''"""RegistryRemapper.java の 772 の表を検証する。

検証すること（すべて名前ベースの独立した真値と突き合わせる）：
  A. *_772 表（771→772）が生成スクリプトの出力（maps772.json）と
     一致すること（転記ミスの検出）。
  B. *_772 表の反転（Java の invert() と同じ決め方。補正なし）と
     ITEM_REVERSE_772 表が、名前から作った 772→771 の真値と一致
     すること。772 にしか無いものは代替先（石・ブタ・石の破壊音・
     entity_effect・作業台）になる。
  C. 合成表（763↔772、764↔772、765↔772、766↔772、767↔772、768↔772、
     769↔772、770↔772）が生成スクリプトの出力（maps_composed_772.json）と一致すること。
  D. 合成表が、両端のレジストリを名前で直接突き合わせた真値と一致
     すること。上向き（763／764／765／766／767／768／769／770→772）は不一致ゼロの
     はず。下向き（772→763／764／765／766／767／768／769／770）は (a) 同名があるものは
     その番号と一致すること、(b) 合成表全体が「772→771 の反転 ×
     771→相手の表」と一致すること（オラクル）。
  E. 生成スクリプトの再現性：--target 772 を再実行し、Java ファイル
     内の表と完全一致すること。
  F. ITEM_REVERSE_772 表が生成スクリプトの出力
     （map_items_rev772.json）と一致すること。
  G. 属性・エンチャントの表（ATTRIBUTE_772／ATTRIBUTE_REVERSE_772／
     ENCHANTMENT_772／ENCHANTMENT_REVERSE_772）が生成スクリプトの
     出力（gen772.txt）と一致し、属性の名前表が minecraft-data の
     protocol.json の Update Attributes の key mapper と一致し、
     エンチャントの表が minecraft-data の enchantments-*.json の
     名前の並びと一致すること。エンチャントの並びそのものが正しい
     かは実機の Registry Data での確認（統合テスト）で行う。
     属性は 771・772 とも 35 件で同一。ATTRIBUTE_772_MAP の入力は
     771 の 35 件、逆向きの入力は 772 の 35 件。
  H. 音の名前の表（SOUND_NAME_FALLBACKS_772_TO_*）が数値の表と
     一致すること（772 の音の名前 → 数値で写した先の名前）。

使い方：
  python3 tools/verify-772-tables.py [--data-dir DIR]
  終了コード 0 = すべて合格。1 = 不一致あり（詳細を標準出力に出す）。
"""''' + s[j + 3:]

# --- TABLES_FILES と頭コメント ---
rep(
    '# 771 の表の実体。RegistryRemapper.java の静的初期化子が 64KB を超えた\n'
    '# ため、表だけ別クラスへ分離した（tools/insert-771-tables.py を参照）。',
    '# 772 の表の実体。RegistryRemapper.java の静的初期化子が 64KB を超えた\n'
    '# ため、表だけ別クラスへ分離した（tools/insert-772-tables.py を参照）。',
    "TABLES_FILES のコメント")
rep(
    '    for name in ("RegistryTables771.java", "RegistryTables771ComposedUp.java",\n'
    '                 "RegistryTables771Down763.java", "RegistryTables771Down764.java",\n'
    '                 "RegistryTables771Down765.java", "RegistryTables771Down766.java",\n'
    '                 "RegistryTables771Down767.java", "RegistryTables771Down768.java",\n'
    '                 "RegistryTables771Down769.java")',
    '    for name in ("RegistryTables772.java", "RegistryTables772ComposedUp.java",\n'
    '                 "RegistryTables772Down763.java", "RegistryTables772Down764.java",\n'
    '                 "RegistryTables772Down765.java", "RegistryTables772Down766.java",\n'
    '                 "RegistryTables772Down767.java", "RegistryTables772Down768.java",\n'
    '                 "RegistryTables772Down769.java", "RegistryTables772Down770.java")',
    "TABLES_FILES")

# --- 音の代替表の定数名とコメント ---
rep(
    '# （770 で entity.wolf.howl が削除されたが、こちらは\n'
    '# sound_up_rename で扱う。）771 で削除された音（leash_knot の 2 件と\n'
    '# puffer_fish.ambient）は 771 の対応する音へ写す。\n'
    'SOUND_FALLBACK_OLD_TO_771 = {',
    '# （770 で entity.wolf.howl が削除されたが、こちらは\n'
    '# sound_up_rename で扱う。）771 で削除された音（leash_knot の 2 件と\n'
    '# puffer_fish.ambient）は 772 の対応する音へ写す。772 で削除された\n'
    '# 音は無い（772 の新音は music_disc.lava_chicken だけ）。\n'
    'SOUND_FALLBACK_OLD_TO_772 = {',
    "SOUND_FALLBACK_OLD_TO_772")
rep(
    'SOUND_FALLBACK_OLD_TO_772 = {\n'
    '    "item.goat_horn.play": "item.goat_horn.sound.0",\n'
    '    "entity.goat.screaming.horn_break": "entity.goat.horn_break",\n'
    '    "entity.leash_knot.break": "item.lead.break",\n'
    '    "entity.leash_knot.place": "item.lead.tied",\n'
    '    "entity.puffer_fish.ambient": "entity.puffer_fish.blow_out",\n'
    '}',
    'SOUND_FALLBACK_OLD_TO_772 = {\n'
    '    "item.goat_horn.play": "item.goat_horn.sound.0",\n'
    '    "entity.goat.screaming.horn_break": "entity.goat.horn_break",\n'
    '    "entity.leash_knot.break": "item.lead.break",\n'
    '    "entity.leash_knot.place": "item.lead.tied",\n'
    '    "entity.puffer_fish.ambient": "entity.puffer_fish.blow_out",\n'
    '    # block.sand.wind は 770 だけにあり、771 で dry_grass.ambient\n'
    '    # へ写った。770→772 は合成表（D の対象）のため、ここで扱う。\n'
    '    "block.sand.wind": "block.dry_grass.ambient",\n'
    '}',
    "SOUND_FALLBACK_OLD_TO_772 に sand.wind を追加")
rep(
    '# 771→相手の音の代替表。下向きの真値では、771 の音の名前のうち相手に\n'
    '# 無いものは、改名の逆向きか石の破壊音へ。770→771 の改名は無いため、\n'
    '# 771→765／763／764 では 766→767 の改名の逆向き（ominous→charge）\n'
    '# と 767→768 の削除音の逆向き（不要：771 側に無い）は出てこない。\n'
    '# 771 の新音（29件）は石の破壊音へ。\n'
    'SOUND_FALLBACK_771_TO_TARGET = {',
    '# 772→相手の音の代替表。下向きの真値では、772 の音の名前のうち相手に\n'
    '# 無いものは、改名の逆向きか石の破壊音へ。771→772 の改名は無いため、\n'
    '# 772→765／763／764 では 766→767 の改名の逆向き（ominous→charge）\n'
    '# と 767→768 の削除音の逆向き（不要：772 側に無い）は出てこない。\n'
    '# 772 の新音（1件：music_disc.lava_chicken）は石の破壊音へ。\n'
    'SOUND_FALLBACK_772_TO_TARGET = {',
    "SOUND_FALLBACK_772_TO_TARGET")
rep(
    '# 771→770 の音の代替表（B の真値で使う。771 の新音のうち 770 の削除音\n'
    '# から写ったものは、その 770 の音へ戻す。item.lead.untied など\n'
    '# 770 に対応する音が無いものは石の破壊音）。\n'
    '# entity.puffer_fish.blow_out はここには入れない：770 にも同名が\n'
    '# あるため代替表は適用されず、B では別途真値を上書きする。\n'
    'SOUND_FALLBACK_771_TO_770 = {\n'
    '    "block.dry_grass.ambient": "block.sand.wind",\n'
    '    "item.lead.tied": "entity.leash_knot.place",\n'
    '    "item.lead.break": "entity.leash_knot.break",\n'
    '}\n',
    '# 772→771 の音の代替表（B の真値で使う。772 の新音は\n'
    '# music_disc.lava_chicken だけ。771 に対応する音は無く石の破壊音へ）。\n'
    'SOUND_FALLBACK_772_TO_771 = {\n'
    '    "music_disc.lava_chicken": "block.stone.break",\n'
    '}\n',
    "SOUND_FALLBACK_772_TO_771 を追加")

# --- main() のデータ読み込み ---
rep(
    '    java = JAVA_FILE.read_text(encoding="utf-8")\n'
    '    tables771 = "".join(p.read_text(encoding="utf-8") for p in TABLES_FILES)\n'
    '    vv = {v: json.loads((data_dir / f"vv-{v}.json").read_text(encoding="utf-8"))\n'
    '          for v in ("1.20", "1.20.2", "1.20.3", "1.20.5", "1.21", "1.21.2", "1.21.4", "1.21.5",\n'
    '                    "1.21.6")}\n'
    '    maps771 = json.loads((data_dir / "maps771.json").read_text(encoding="utf-8"))\n'
    '    maps_comp = json.loads((data_dir / "maps_composed_771.json").read_text(encoding="utf-8"))\n'
    '    rev771 = json.loads((data_dir / "map_items_rev771.json").read_text(encoding="utf-8"))\n'
    '    gen771 = (data_dir / "gen771.txt").read_text(encoding="utf-8")\n'
    '    # D (b) のオラクル用に 770 の合成表が必要。無ければ生成する。\n'
    '    maps_comp_770_path = data_dir / "maps_composed_770.json"\n'
    '    maps770_path = data_dir / "maps770.json"\n'
    '    if not maps_comp_770_path.exists() or not maps770_path.exists():\n'
    '        proc = subprocess.run(\n'
    '            [sys.executable, str(SCRIPT_DIR / "generate-remap-tables.py"),\n'
    '             "--target", "770", "--out", str(data_dir / "gen770-tmp.txt")],\n'
    '            capture_output=True, text=True, timeout=300)\n'
    '        if proc.returncode != 0:\n'
    '            print("[FAIL] 770 の表の生成に失敗した（D (b) のオラクル用）")\n'
    '            print(proc.stderr[-2000:])\n'
    '            return 1\n'
    '    maps_comp_770 = json.loads(maps_comp_770_path.read_text(encoding="utf-8"))\n'
    '    maps770 = json.loads(maps770_path.read_text(encoding="utf-8"))\n'
    '    tables770 = (REPO_ROOT / "detergentmc-protocol" / "src" / "main" / "java"\n'
    '                 / "io" / "detergentmc" / "protocol" / "RegistryTables770.java").read_text(\n'
    '                     encoding="utf-8")',
    '    java = JAVA_FILE.read_text(encoding="utf-8")\n'
    '    tables772 = "".join(p.read_text(encoding="utf-8") for p in TABLES_FILES)\n'
    '    vv = {v: json.loads((data_dir / f"vv-{v}.json").read_text(encoding="utf-8"))\n'
    '          for v in ("1.20", "1.20.2", "1.20.3", "1.20.5", "1.21", "1.21.2", "1.21.4", "1.21.5",\n'
    '                    "1.21.6", "1.21.7")}\n'
    '    maps772 = json.loads((data_dir / "maps772.json").read_text(encoding="utf-8"))\n'
    '    maps_comp = json.loads((data_dir / "maps_composed_772.json").read_text(encoding="utf-8"))\n'
    '    rev772 = json.loads((data_dir / "map_items_rev772.json").read_text(encoding="utf-8"))\n'
    '    gen772 = (data_dir / "gen772.txt").read_text(encoding="utf-8")\n'
    '    # D (b) のオラクル用に 771 の合成表が必要。無ければ生成する。\n'
    '    maps_comp_771_path = data_dir / "maps_composed_771.json"\n'
    '    maps771_path = data_dir / "maps771.json"\n'
    '    if not maps_comp_771_path.exists() or not maps771_path.exists():\n'
    '        proc = subprocess.run(\n'
    '            [sys.executable, str(SCRIPT_DIR / "generate-remap-tables.py"),\n'
    '             "--target", "771", "--out", str(data_dir / "gen771-tmp.txt")],\n'
    '            capture_output=True, text=True, timeout=300)\n'
    '        if proc.returncode != 0:\n'
    '            print("[FAIL] 771 の表の生成に失敗した（D (b) のオラクル用）")\n'
    '            print(proc.stderr[-2000:])\n'
    '            return 1\n'
    '    maps_comp_771 = json.loads(maps_comp_771_path.read_text(encoding="utf-8"))\n'
    '    maps771 = json.loads(maps771_path.read_text(encoding="utf-8"))\n'
    '    tables771 = (REPO_ROOT / "detergentmc-protocol" / "src" / "main" / "java"\n'
    '                 / "io" / "detergentmc" / "protocol" / "RegistryTables771.java").read_text(\n'
    '                     encoding="utf-8")',
    "データ読み込み")

# --- 代替先の定数 ---
rep(
    '    # 771 側の代替先（770→771 の表の範囲外用。Java の定数と同じ値）。\n'
    '    # ブタは 771 では 95、石の破壊音は 771 では 1441。\n'
    '    fb771 = {"blockstates": 1, "items": 1, "entities": 95, "sounds": 1441,\n'
    '             "particles": 20, "blocks": 1, "menus": 12}\n'
    '    # 770 側の代替先（771→770 の反転の代替先。Java の定数と同じ値）。\n'
    '    # ブタは 770 では 94、石の破壊音は 770 では 1417。\n'
    '    fb770 = {"blockstates": 1, "items": 1, "entities": 94, "sounds": 1417,\n'
    '             "particles": 20, "blocks": 1, "menus": 12}\n'
    '    # 769 側の代替先（770→769 の反転の代替先。Java の定数と同じ値）。\n'
    '    # ブタは 769 では 94、石の破壊音は 769 では 1401。\n'
    '    fb769 = {"blockstates": 1, "items": 1, "entities": 94, "sounds": 1401,\n'
    '             "particles": 20, "blocks": 1, "menus": 12}',
    '    # 772 側の代替先（771→772 の表の範囲外用。Java の定数と同じ値）。\n'
    '    # ブタは 772 でも 95、石の破壊音は 772 では 1442。\n'
    '    fb772 = {"blockstates": 1, "items": 1, "entities": 95, "sounds": 1442,\n'
    '             "particles": 20, "blocks": 1, "menus": 12}\n'
    '    # 771 側の代替先（772→771 の反転の代替先。Java の定数と同じ値）。\n'
    '    # ブタは 771 では 95、石の破壊音は 771 では 1441。\n'
    '    fb771 = {"blockstates": 1, "items": 1, "entities": 95, "sounds": 1441,\n'
    '             "particles": 20, "blocks": 1, "menus": 12}\n'
    '    # 770 側の代替先（771→770 の反転の代替先。Java の定数と同じ値）。\n'
    '    # ブタは 770 では 94、石の破壊音は 770 では 1417。\n'
    '    fb770 = {"blockstates": 1, "items": 1, "entities": 94, "sounds": 1417,\n'
    '             "particles": 20, "blocks": 1, "menus": 12}',
    "代替先の定数")

if failed:
    print("失敗:", file=sys.stderr)
    for f in failed:
        print(" -", f, file=sys.stderr)
    sys.exit(1)
print("part 1 OK (docstring / TABLES_FILES / 音の代替表 / データ読み込み / 代替先）")

# --- A. *_772 表 ---
rep(
    '    # --- A. *_771 表が生成スクリプトの出力と一致する ---\n'
    '    for kind in kinds:\n'
    '        ranges = parse_int3_table(tables771, java_names[kind] + "_771_RANGES")\n'
    '        overrides = parse_int_table(tables771, java_names[kind] + "_771_OVERRIDES")\n'
    '        expanded = expand(ranges, overrides, len(vv["1.21.5"][kind]), fb771[kind])\n'
    '        gen = {int(k): v for k, v in maps771[kind].items()}\n'
    '        problems = [f"{kind}[{i}] Java={expanded[i]} 生成={gen[i]}"\n'
    '                    for i in range(len(vv["1.21.5"][kind]))\n'
    '                    if expanded[i] != gen[i]]\n'
    '        check(f"A. {kind}_771 表 = 生成出力", problems)',
    '    # --- A. *_772 表が生成スクリプトの出力と一致する ---\n'
    '    for kind in kinds:\n'
    '        ranges = parse_int3_table(tables772, java_names[kind] + "_772_RANGES")\n'
    '        overrides = parse_int_table(tables772, java_names[kind] + "_772_OVERRIDES")\n'
    '        expanded = expand(ranges, overrides, len(vv["1.21.6"][kind]), fb772[kind])\n'
    '        gen = {int(k): v for k, v in maps772[kind].items()}\n'
    '        problems = [f"{kind}[{i}] Java={expanded[i]} 生成={gen[i]}"\n'
    '                    for i in range(len(vv["1.21.6"][kind]))\n'
    '                    if expanded[i] != gen[i]]\n'
    '        check(f"A. {kind}_772 表 = 生成出力", problems)',
    "A")

# --- F. ITEM_REVERSE_772 ---
rep(
    '    # --- F. ITEM_REVERSE_771 表が生成スクリプトの出力と一致する ---\n'
    '    ranges = parse_int3_table(tables771, "ITEM_REVERSE_771_RANGES")\n'
    '    overrides = parse_int_table(tables771, "ITEM_REVERSE_771_OVERRIDES")\n'
    '    expanded = expand(ranges, overrides, len(vv["1.21.6"]["items"]), 1)\n'
    '    gen = {int(k): v for k, v in rev771.items()}\n'
    '    problems = [f"items[{i}] Java={expanded[i]} 生成={gen[i]}"\n'
    '                for i in range(len(vv["1.21.6"]["items"]))\n'
    '                if expanded[i] != gen[i]]\n'
    '    check("F. ITEM_REVERSE_771 表 = 生成出力", problems)',
    '    # --- F. ITEM_REVERSE_772 表が生成スクリプトの出力と一致する ---\n'
    '    ranges = parse_int3_table(tables772, "ITEM_REVERSE_772_RANGES")\n'
    '    overrides = parse_int_table(tables772, "ITEM_REVERSE_772_OVERRIDES")\n'
    '    expanded = expand(ranges, overrides, len(vv["1.21.7"]["items"]), 1)\n'
    '    gen = {int(k): v for k, v in rev772.items()}\n'
    '    problems = [f"items[{i}] Java={expanded[i]} 生成={gen[i]}"\n'
    '                for i in range(len(vv["1.21.7"]["items"]))\n'
    '                if expanded[i] != gen[i]]\n'
    '    check("F. ITEM_REVERSE_772 表 = 生成出力", problems)',
    "F")

# --- B. 反転（区間ごと置き換え）---
# 772→771 に補正は無い。771 に blow_out の衝突は無い（771 に
# entity.puffer_fish.ambient が無いため）ので B の上書きは不要。
NEW_B = '''    # --- B. *_772 表の反転と ITEM_REVERSE_772 が 772→771 の名前の真値と一致する ---
    # 772→771 に補正は無い。
    defaults771 = {b["name"]: b["defaultState"]
                   for b in json.loads(
                       (data_dir / "1.21.6_blocks.json").read_text(encoding="utf-8"))}
    for kind in kinds:
        if kind == "items":
            ranges = parse_int3_table(tables772, "ITEM_REVERSE_772_RANGES")
            overrides = parse_int_table(tables772, "ITEM_REVERSE_772_OVERRIDES")
            up = expand(ranges, overrides, len(vv["1.21.7"]["items"]), 1)
        else:
            ranges = parse_int3_table(tables772, java_names[kind] + "_772_RANGES")
            overrides = parse_int_table(tables772, java_names[kind] + "_772_OVERRIDES")
            up = invert_java(ranges, overrides, len(vv["1.21.6"][kind]),
                             len(vv["1.21.7"][kind]), fb771[kind], [])
        truth = name_truth(
            vv["1.21.7"][kind], vv["1.21.6"][kind], kind, fb771[kind],
            rename=None,
            sound_fallbacks=SOUND_FALLBACK_772_TO_771 if kind == "sounds" else None,
            particle_fallbacks=None,
            item_fallbacks=None,
            block_fallbacks=None,
            target_defaults=defaults771 if kind == "blockstates" else None,
            drop_one_prop=(kind == "blockstates"))
        problems = []
        for i in range(len(vv["1.21.7"][kind])):
            if up[i] != truth[i]:
                problems.append(
                    f"{kind}[{i}] {vv['1.21.7'][kind][i]}: 反転={up[i]} "
                    f"真値={truth[i]}")
        check(f"B. {kind} 772→771 反転 = 名前の真値", problems)

'''
replace_section("    # --- B. *_771 表の反転", "    # --- C. 合成表", NEW_B, "B")

# --- C. 合成表（区間ごと置き換え）---
NEW_C = '''    # --- C. 合成表が生成スクリプトの出力と一致する ---
    fb_by_target = {"1.20": {"blockstates": 1, "items": 1, "entities": 72,
                             "sounds": 1264, "particles": 0, "blocks": 1, "menus": 11},
                    "1.20.2": {"blockstates": 1, "items": 1, "entities": 72,
                               "sounds": 1270, "particles": 0, "blocks": 1, "menus": 11},
                    "1.20.3": {"blockstates": 1, "items": 1, "entities": 73,
                               "sounds": 1312, "particles": 0, "blocks": 1, "menus": 12},
                    "1.20.5": {"blockstates": 1, "items": 1, "entities": 77,
                               "sounds": 1358, "particles": 20, "blocks": 1, "menus": 12},
                    "1.21": {"blockstates": 1, "items": 1, "entities": 77,
                             "sounds": 1361, "particles": 20, "blocks": 1, "menus": 12},
                    "1.21.2": {"blockstates": 1, "items": 1, "entities": 95,
                               "sounds": 1386, "particles": 20, "blocks": 1, "menus": 12},
                    "1.21.4": {"blockstates": 1, "items": 1, "entities": 94,
                               "sounds": 1401, "particles": 20, "blocks": 1, "menus": 12},
                    "1.21.5": {"blockstates": 1, "items": 1, "entities": 94,
                               "sounds": 1417, "particles": 20, "blocks": 1, "menus": 12}}
    for tag in ("763_TO_772", "772_TO_763", "764_TO_772", "772_TO_764",
                "765_TO_772", "772_TO_765", "766_TO_772", "772_TO_766",
                "767_TO_772", "772_TO_767", "768_TO_772", "772_TO_768",
                "769_TO_772", "772_TO_769", "770_TO_772", "772_TO_770"):
        for kind in kinds:
            ranges = parse_int3_table(
                tables772, java_names[kind] + "_" + tag + "_RANGES")
            overrides = parse_int_table(
                tables772, java_names[kind] + "_" + tag + "_OVERRIDES")
            if tag.endswith("772"):
                src_v = {"763": "1.20", "764": "1.20.2",
                         "765": "1.20.3", "766": "1.20.5",
                         "767": "1.21", "768": "1.21.2",
                         "769": "1.21.4", "770": "1.21.5"}[tag[:3]]
                generic = fb772[kind]
            else:
                src_v = "1.21.7"
                generic = fb_by_target[{"763": "1.20", "764": "1.20.2",
                                        "765": "1.20.3", "766": "1.20.5",
                                        "767": "1.21", "768": "1.21.2",
                                        "769": "1.21.4", "770": "1.21.5"}[tag[7:10]]][kind]
            size = len(vv[src_v][kind])
            expanded = expand(ranges, overrides, size, generic)
            gen = {int(k): v for k, v in maps_comp[tag][kind].items()}
            problems = [f"{kind}[{i}] Java={expanded[i]} 生成={gen[i]}"
                        for i in range(size) if expanded[i] != gen[i]]
            check(f"C. {kind}_{tag} 合成表 = 生成出力", problems)

'''
replace_section("    # --- C. 合成表", "    # --- D. 合成表が両端の名前の真値と一致する ---", NEW_C, "C")

if failed:
    print("失敗:", file=sys.stderr)
    for f in failed:
        print(" -", f, file=sys.stderr)
    sys.exit(1)
print("part 2 OK (A / F / B / C)")

# --- D. defaults に 1.21.7 を追加（772 の blocks は mc-data 1.21.8） ---
rep(
    '                     ("1.21.5", "1.21.5_blocks.json"),\n'
    '                     ("1.21.6", "1.21.6_blocks.json")):',
    '                     ("1.21.5", "1.21.5_blocks.json"),\n'
    '                     ("1.21.6", "1.21.6_blocks.json"),\n'
    '                     ("1.21.7", "1.21.8_blocks.json")):',
    "D defaults 1.21.7")

# --- D. pairs（区間ごと置き換え） ---
NEW_PAIRS = '''    pairs = [
        ("763_TO_772", "1.20", "1.21.7",
         {"grass": "short_grass", "scute": "turtle_scute"},
         sound_up_rename,
         {"ambient_entity_effect": "entity_effect",
          "gust_emitter": "gust_emitter_large",
          "gust_dust": "small_gust"},
         {"trial_spawner": "spawner"}),
        ("772_TO_763", "1.21.7", "1.20",
         sound_down_rename,
         None, None,
         {"vault": "trial_spawner", "heavy_core": "stone"}),
        ("764_TO_772", "1.20.2", "1.21.7",
         {"grass": "short_grass", "scute": "turtle_scute"},
         sound_up_rename,
         {"ambient_entity_effect": "entity_effect",
          "gust_emitter": "gust_emitter_large",
          "gust_dust": "small_gust"},
         {"trial_spawner": "spawner"}),
        ("772_TO_764", "1.21.7", "1.20.2",
         sound_down_rename,
         None, None,
         {"vault": "trial_spawner", "heavy_core": "stone"}),
        ("765_TO_772", "1.20.3", "1.21.7",
         {"grass": "short_grass", "scute": "turtle_scute"},
         sound_up_rename,
         {"ambient_entity_effect": "entity_effect",
          "gust_emitter": "gust_emitter_large",
          "gust_dust": "small_gust"},
         {"trial_spawner": "spawner"}),
        ("772_TO_765", "1.21.7", "1.20.3",
         sound_down_rename,
         None, None,
         {"vault": "trial_spawner", "heavy_core": "stone"}),
        ("766_TO_772", "1.20.5", "1.21.7",
         {}, sound_up_rename, {}, {}),
        ("772_TO_766", "1.21.7", "1.20.5",
         sound_down_rename_766_767,
         None, None,
         {"vault": "trial_spawner", "heavy_core": "stone"}),
        ("767_TO_772", "1.21", "1.21.7",
         {}, sound_up_rename, {}, {}),
        ("772_TO_767", "1.21.7", "1.21",
         sound_down_rename_766_767,
         None, None,
         {"vault": "trial_spawner", "heavy_core": "stone"}),
        ("768_TO_772", "1.21.2", "1.21.7",
         {}, sound_up_rename, {}, {}),
        ("772_TO_768", "1.21.7", "1.21.2",
         sound_down_rename_766_767,
         None, None, {}),
        ("769_TO_772", "1.21.4", "1.21.7",
         {}, sound_up_rename, {}, {}),
        ("772_TO_769", "1.21.7", "1.21.4",
         sound_down_rename_766_767,
         None, None, {}),
        ("770_TO_772", "1.21.5", "1.21.7",
         {}, sound_up_rename, {}, {}),
        ("772_TO_770", "1.21.7", "1.21.5",
         sound_down_rename_766_767,
         None, None, {}),
    ]

'''
replace_section("    pairs = [\n", "    # D-down (b)", NEW_PAIRS, "D pairs")

# --- D(b) オラクル用 up771（区間ごと置き換え） ---
# 772→771 の反転に補正は無いため、771 の表の反転は補正なしで作る。
NEW_ORACLE = '''    # D-down (b) の合成オラクル用：771→相手は生成済みの合成表
    # （maps_composed_771.json）を使う。771→770 だけは合成表が無く
    # （770 が隣接元の版のため）、Java の *_771 表の反転で作る。
    # 771 の表が無ければ生成する（上の --target 771 の再生成と同じ）。
    up771 = {}
    for kind in kinds:
        ranges = parse_int3_table(tables771, java_names[kind] + "_771_RANGES")
        overrides = parse_int_table(tables771, java_names[kind] + "_771_OVERRIDES")
        up771[kind] = invert_java(ranges, overrides, len(vv["1.21.5"][kind]),
                                  len(vv["1.21.6"][kind]), fb770[kind], [])

'''
replace_section("    # D-down (b)", "    for tag, src_v, dst_v, item_rename",
                NEW_ORACLE, "D oracle setup")

# --- D ループ内の小さいずれ ---
rep('        down = tag.startswith("771")',
    '        down = tag.startswith("772")', "D down フラグ")
rep(
    '            ranges = parse_int3_table(\n'
    '                tables771, java_names[kind] + "_" + tag + "_RANGES")\n'
    '            overrides = parse_int_table(\n'
    '                tables771, java_names[kind] + "_" + tag + "_OVERRIDES")',
    '            ranges = parse_int3_table(\n'
    '                tables772, java_names[kind] + "_" + tag + "_RANGES")\n'
    '            overrides = parse_int_table(\n'
    '                tables772, java_names[kind] + "_" + tag + "_OVERRIDES")',
    "D 合成表の parse")
rep(
    '            generic = (fb771[kind] if tag.endswith("771")\n'
    '                       else fb_by_target[dst_v][kind])',
    '            generic = (fb772[kind] if tag.endswith("772")\n'
    '                       else fb_by_target[dst_v][kind])',
    "D generic")
rep(
    '                        # 768 の creaking_transient は 769 で削除され、\n'
    '                        # 生成器は creaking に落とす（generate-remap-tables.py\n'
    '                        # の 769 の改名表）。771 でも creaking のまま。',
    '                        # 768 の creaking_transient は 769 で削除され、\n'
    '                        # 生成器は creaking に落とす（generate-remap-tables.py\n'
    '                        # の 769 の改名表）。772 でも creaking のまま。',
    "D creaking_transient コメント")
rep(
    '                    sound_fallbacks=SOUND_FALLBACK_OLD_TO_771\n'
    '                    if kind == "sounds" else None,',
    '                    sound_fallbacks=SOUND_FALLBACK_OLD_TO_772\n'
    '                    if kind == "sounds" else None,',
    "D sound_fallbacks 上向き")
rep_all("→ 771 の真値", "→ 772 の真値", "D creaking_heart 真値", 2)
rep_all("（770 の B の補正と同じ決め方）", "（771 の反転と同じ決め方）",
        "D creaking_heart 補正コメント", 2)
rep_all("idx771", "idx772", "D idx772（G節の2件も含む）", 6)
rep_all('vv["1.21.6"]["blockstates"]', 'vv["1.21.7"]["blockstates"]',
        "D creaking_heart の対象版", 2)
rep(
    '                sound_fallbacks=SOUND_FALLBACK_771_TO_TARGET if kind == "sounds" else None,',
    '                sound_fallbacks=SOUND_FALLBACK_772_TO_TARGET if kind == "sounds" else None,',
    "D sound_fallbacks 下向き")
rep(
    '                # 771 の creaking_heart → 768 の真値（768 は creaking',
    '                # 772 の creaking_heart → 768 の真値（768 は creaking',
    "D creaking_heart 下向き 768")
rep(
    '                # 771 の creaking_heart → 769 の真値（769 は active＋axis＋',
    '                # 772 の creaking_heart → 769 の真値（769 は active＋axis＋',
    "D creaking_heart 下向き 769")
rep(
    '                # 771 の blow_out は 770 の ambient と blow_out の両方から',
    '                # 772 の blow_out は 770 の ambient と blow_out の両方から',
    "D blow_out 下向き")

# --- D(b) オラクル部分（区間ごと置き換え） ---
NEW_DB = '''            # 下向き (b)：合成表全体が「772→771 の反転 × 771→相手の表」
            # と一致するはず。代替先の決め方は 771→相手の表に委ねる。
            # 771→770 だけ合成表が無いため up771（771→770 の反転）を使う。
            if dst_v == "1.21.5":
                oracle_map = up771[kind]
            else:
                tag2 = "771_TO_" + tag[7:10]
                oracle_raw = {int(k): v for k, v in maps_comp_771[tag2][kind].items()}
                oracle_map = [oracle_raw[i] for i in range(len(vv["1.21.6"][kind]))]
            if kind == "items":
                r = parse_int3_table(tables772, "ITEM_REVERSE_772_RANGES")
                o = parse_int_table(tables772, "ITEM_REVERSE_772_OVERRIDES")
                up772 = expand(r, o, len(vv["1.21.7"]["items"]), 1)
            else:
                r = parse_int3_table(tables772, java_names[kind] + "_772_RANGES")
                o = parse_int_table(tables772, java_names[kind] + "_772_OVERRIDES")
                up772 = invert_java(r, o, len(vv["1.21.6"][kind]),
                                    len(vv["1.21.7"][kind]), fb771[kind], [])
            problems = []
            for i in range(size):
                expected = oracle_map[up772[i]]
                if expanded[i] != expected:
                    problems.append(
                        f"{kind}[{i}] {vv[src_v][kind][i]}: 合成={expanded[i]} "
                        f"オラクル={expected}")
'''
replace_section("            # 下向き (b)：", "            check(f\"D. {kind}_{tag} 合成表 = 合成オラクル\", problems)",
                NEW_DB, "D(b) オラクル")

if failed:
    print("失敗:", file=sys.stderr)
    for f in failed:
        print(" -", f, file=sys.stderr)
    sys.exit(1)
print("part 3 OK (D pairs / oracle / ループ)")

# --- E. 再現性（区間ごと置き換え） ---
NEW_E = '''    # --- E. 生成スクリプトの再現性 ---
    # --target 772 を再実行し、Java ファイル内の表と完全一致する
    # （生成スクリプトをコピーした直後の転記ミスの検出）。
    tmp = data_dir / "verify-tmp-772.txt"
    proc = subprocess.run(
        [sys.executable, str(SCRIPT_DIR / "generate-remap-tables.py"),
         "--target", "772", "--out", str(tmp)],
        capture_output=True, text=True, timeout=300)
    if proc.returncode != 0:
        print("[FAIL] E. 生成スクリプト --target 772 が失敗した")
        print(proc.stderr[-2000:])
        failures.append("E-772")
    else:
        gen_re = tmp.read_text(encoding="utf-8")
        problems = []
        for m in re.finditer(
                r"^(?:public static final int\\[\\]\\[\\] )?([A-Z0-9_]+) = "
                r"compress\\(new int\\[\\]\\[\\]\\{(.*?)\\}\\);",
                gen_re, re.MULTILINE | re.DOTALL):
            name = m.group(1)
            # ATTRIBUTE_771_NAMES は RegistryTables771.java にあるため
            # 772 側には入れない（insert-772-tables.py と同じ）。
            if name == "ATTRIBUTE_771_NAMES":
                continue
            m2 = re.search(re.escape(name)
                           + r" = \\{(.*?)\\n    \\};", tables772, re.DOTALL)
            if not m2:
                problems.append(f"{name} が RegistryTables772*.java に無い")
        check("E. 生成スクリプト --target 772 の再現性", problems)

'''
replace_section("    # --- E. 生成スクリプト", "    # --- G.", NEW_E, "E")

# --- G. 属性・エンチャント（区間ごと置き換え） ---
NEW_G = '''    # --- G. 属性・エンチャントの表 ---
    for name, size, fb in (("ATTRIBUTE_772", 35, -1),
                           ("ATTRIBUTE_REVERSE_772", 35, -1),
                           ("ENCHANTMENT_772", 42, -1),
                           ("ENCHANTMENT_REVERSE_772", 42, -1)):
        ranges = parse_int3_table(tables772, name + "_RANGES")
        overrides = parse_int_table(tables772, name + "_OVERRIDES")
        expanded = expand(ranges, overrides, size, fb)
        gm_ranges = re.search(r"int\\[\\]\\[\\] " + re.escape(name) + r"_RANGES = \\{(.*?)\\n    \\};",
                              gen772, re.DOTALL)
        gm_overrides = re.search(r"int\\[\\]\\[\\] " + re.escape(name) + r"_OVERRIDES = \\{(.*?)\\n    \\};",
                                 gen772, re.DOTALL)
        if not gm_ranges or not gm_overrides:
            check(f"G. {name} 表 = 生成出力", [f"{name} が gen772.txt に無い"])
            continue
        gen_ranges = [tuple(map(int, t))
                      for t in re.findall(r"\\{(-?\\d+), (-?\\d+), (-?\\d+)\\}", gm_ranges.group(1))]
        gen_overrides = [tuple(map(int, t))
                         for t in re.findall(r"\\{(-?\\d+), (-?\\d+)\\}", gm_overrides.group(1))]
        gen_expanded = expand(gen_ranges, gen_overrides, size, fb)
        problems = [f"{name}[{i}] Java={expanded[i]} 生成={gen_expanded[i]}"
                    for i in range(size) if expanded[i] != gen_expanded[i]]
        check(f"G. {name} 表 = 生成出力", problems)
    # 属性の名前表 = protocol.json の key mapper。
    mc771 = json.loads((data_dir / "mc-1.21.6-protocol.json").read_text(encoding="utf-8"))
    mc772 = json.loads((data_dir / "mc-1.21.8-protocol.json").read_text(encoding="utf-8"))
    for name, proto, source in (("ATTRIBUTE_771_NAMES", mc771, tables771),
                                ("ATTRIBUTE_772_NAMES", mc772, tables772)):
        java_names_list = parse_string_array(source, name)
        truth = attribute_names(proto)
        problems = [f"{name}[{i}] Java={java_names_list[i]} 真値={truth[i]}"
                    for i in range(len(truth))
                    if i >= len(java_names_list) or java_names_list[i] != truth[i]]
        if len(java_names_list) != len(truth):
            problems.append(f"{name} の件数が違う：Java={len(java_names_list)} 真値={len(truth)}")
        check(f"G. {name} = protocol.json", problems)
    # エンチャントの表 = minecraft-data の enchantments-*.json の並び。
    ench772 = [e["name"] for e in json.loads(
        (data_dir / "enchantments-1.21.8.json").read_text(encoding="utf-8"))]
    idx772 = {n: i for i, n in enumerate(ench772)}
    # 1.21.8 の enchantments は dataPaths.json により pc/1.21.8 を使う
    # （1.21.6 の 42 件と同じ名前・同じ並び）。
    for name, src in (("ENCHANTMENT_772", ench772),
                      ("ENCHANTMENT_REVERSE_772", ench772)):
        ranges = parse_int3_table(tables772, name + "_RANGES")
        overrides = parse_int_table(tables772, name + "_OVERRIDES")
        expanded = expand(ranges, overrides, len(src), -1)
        problems = []
        for i, n in enumerate(src):
            expected = idx772.get(n, -1)
            if expanded[i] != expected:
                problems.append(f"{name}[{i}] {n}: Java={expanded[i]} 真値={expected}")
        check(f"G. {name} = enchantments-*.json の並び", problems)

'''
replace_section("    # --- G.", "    # --- H.", NEW_G, "G")

# --- H. 音の名前の表（区間ごと置き換え） ---
NEW_H = '''    # --- H. 音の名前の表が数値の表と一致する ---
    sound_name_tables = {
        "SOUND_NAME_FALLBACKS_772_TO_771": ("1.21.7", "1.21.6", "772_TO_771"),
        "SOUND_NAME_FALLBACKS_772_TO_770": ("1.21.7", "1.21.5", "772_TO_770"),
        "SOUND_NAME_FALLBACKS_772_TO_769": ("1.21.7", "1.21.4", "772_TO_769"),
        "SOUND_NAME_FALLBACKS_772_TO_768": ("1.21.7", "1.21.2", "772_TO_768"),
        "SOUND_NAME_FALLBACKS_772_TO_767": ("1.21.7", "1.21", "772_TO_767"),
        "SOUND_NAME_FALLBACKS_772_TO_765": ("1.21.7", "1.20.3", "772_TO_765"),
        "SOUND_NAME_FALLBACKS_772_TO_763": ("1.21.7", "1.20", "772_TO_763"),
        "SOUND_NAME_FALLBACKS_772_TO_764": ("1.21.7", "1.20.2", "772_TO_764"),
        "SOUND_NAME_FALLBACKS_772_TO_766": ("1.21.7", "1.20.5", "772_TO_766"),
    }
    for name, (src_v, dst_v, tag) in sound_name_tables.items():
        pairs = parse_string_table(tables772, name)
        if tag == "772_TO_771":
            # 772→771 は合成表ではなく直接の表の反転。
            ranges = parse_int3_table(tables772, "SOUND_772_RANGES")
            overrides = parse_int_table(tables772, "SOUND_772_OVERRIDES")
            numeric = invert_java(ranges, overrides, len(vv["1.21.6"]["sounds"]),
                                  len(vv["1.21.7"]["sounds"]), 1441, [])
        else:
            ranges = parse_int3_table(tables772, "SOUND_" + tag + "_RANGES")
            overrides = parse_int_table(tables772, "SOUND_" + tag + "_OVERRIDES")
            fb = fb_by_target[dst_v]["sounds"]
            numeric = expand(ranges, overrides, len(vv[src_v]["sounds"]), fb)
        truth = {vv[src_v]["sounds"][i]: vv[dst_v]["sounds"][numeric[i]]
                 for i in range(len(vv[src_v]["sounds"]))}
        problems = []
        for src_name, dst_name in pairs:
            if src_name not in truth:
                problems.append(f"{src_name} が {src_v} の音に無い")
            elif truth[src_name] != dst_name:
                problems.append(f"{src_name}: 表={dst_name} 数値={truth[src_name]}")
        # 逆に、数値の表で代替先になった音はすべて名前の表にもあるはず。
        for src_name, dst_name in truth.items():
            if src_name != dst_name and (src_name, dst_name) not in pairs:
                problems.append(f"{src_name}→{dst_name} が名前の表に無い")
        check(f"H. {name} = 数値の表", problems)

'''
replace_section("    # --- H.", "    # --- I.", NEW_H, "H")

# --- I. ダメージ種別（区間ごと置き換え） ---
NEW_I = '''    # --- I. ダメージ種別の表（手書き）が 771 と同じ並びであること ---
    # DAMAGE_TYPE_772_MAP／REVERSE_772_MAP は生成スクリプトの出力では
    # なく insert-772-java.py の手書きのため、ここで独立に検証する。
    # 772 のダメージ種別は 771 と同じ 49 種で、並びも同じはず
    # （実バニラの Registry Data での確認は統合テストで行う）。
    # まだ insert-772-java.py が走っていない場合は検証を飛ばす。
    if "DAMAGE_TYPE_772_MAP" not in java:
        print("[SKIP] I. ダメージ種別の表（insert-772-java.py 未実行）")
    else:
        play = (REPO_ROOT / "detergentmc-protocol" / "src" / "main" / "java"
                / "io" / "detergentmc" / "protocol" / "PlayTranslator.java").read_text(
                    encoding="utf-8")
        m769_names = re.search(
            r"private static final String\\[\\] DAMAGE_768_NAMES = \\{(.*?)\\n    \\};",
            play, re.DOTALL)
        if not m769_names:
            check("I. ダメージ種別の表", ["DAMAGE_768_NAMES が PlayTranslator.java に無い"])
        else:
            names768 = re.findall(r'"([^"]+)"', m769_names.group(1))

            def parse_int_array(text, name):
                m = re.search(r"int\\[\\] " + re.escape(name) + r" = \\{(.*?)\\n    \\};",
                              text, re.DOTALL)
                if not m:
                    raise SystemExit(f"Java ファイルに {name} が見つからない")
                return [int(x) for x in re.findall(r"(-?\\d+)", m.group(1))]

            fwd = parse_int_array(java, "DAMAGE_TYPE_772_MAP")
            problems = []
            if len(fwd) != len(names768):
                problems.append(f"DAMAGE_TYPE_772_MAP の件数が違う：{len(fwd)} != {len(names768)}")
            for i in range(len(fwd)):
                if fwd[i] != i:
                    problems.append(f"771[{i}] {names768[i]}: Java={fwd[i]} 真値={i}")
            check("I. DAMAGE_TYPE_772_MAP = 771 と同じ並び", problems)
            rev = parse_int_array(java, "DAMAGE_TYPE_REVERSE_772_MAP")
            problems = []
            if len(rev) != len(names768):
                problems.append(f"DAMAGE_TYPE_REVERSE_772_MAP の件数が違う：{len(rev)} != {len(names768)}")
            for i in range(len(rev)):
                if rev[i] != i:
                    problems.append(f"772[{i}] {names768[i]}: Java={rev[i]} 真値={i}")
            check("I. DAMAGE_TYPE_REVERSE_772_MAP = 771 と同じ並び", problems)

'''
replace_section("    # --- I.", "    print()\n    if failures:", NEW_I, "I")

if failed:
    print("失敗:", file=sys.stderr)
    for f in failed:
        print(" -", f, file=sys.stderr)
    sys.exit(1)

DST.write_text(s, encoding="utf-8")
print(f"wrote {DST}")
print(f"変更: {len(s.splitlines())} 行（元 {len(orig.splitlines())} 行）")
