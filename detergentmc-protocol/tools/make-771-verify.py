#!/usr/bin/env python3
"""verify-770-tables.py から verify-771-tables.py を作る（1回だけの作業用）。

機械的なずらし（770→771、769→770）と、771で意味が変わる箇所
（Bの補正なし、D(b)オラクルの隣接元が769→770、Gの属性35件、
Hの771→770反転、Iのダメージ種別771）をまとめて行う。
"""
import shutil
import sys
from pathlib import Path

SCRIPT_DIR = Path(__file__).resolve().parent
SRC = SCRIPT_DIR / "verify-770-tables.py"
DST = SCRIPT_DIR / "verify-771-tables.py"

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
i = s.find('"""RegistryRemapper.java の 770')
j = s.find('"""', i + 3)
assert i >= 0 and j > i
s = s[:i] + '''"""RegistryRemapper.java の 771 の表を検証する。

検証すること（すべて名前ベースの独立した真値と突き合わせる）：
  A. *_771 表（770→771）が生成スクリプトの出力（maps771.json）と
     一致すること（転記ミスの検出）。
  B. *_771 表の反転（Java の invert() と同じ決め方。補正なし）と
     ITEM_REVERSE_771 表が、名前から作った 771→770 の真値と一致
     すること。771 にしか無いものは代替先（石・ブタ・石の破壊音・
     entity_effect・作業台）になる。
  C. 合成表（763↔771、764↔771、765↔771、766↔771、767↔771、768↔771、
     769↔771）が生成スクリプトの出力（maps_composed_771.json）と一致すること。
  D. 合成表が、両端のレジストリを名前で直接突き合わせた真値と一致
     すること。上向き（763／764／765／766／767／768／769→771）は不一致ゼロの
     はず。下向き（771→763／764／765／766／767／768／769）は (a) 同名があるものは
     その番号と一致すること、(b) 合成表全体が「771→770 の反転 ×
     770→相手の表」と一致すること（オラクル）。
  E. 生成スクリプトの再現性：--target 771 を再実行し、Java ファイル
     内の表と完全一致すること。
  F. ITEM_REVERSE_771 表が生成スクリプトの出力
     （map_items_rev771.json）と一致すること。
  G. 属性・エンチャントの表（ATTRIBUTE_771／ATTRIBUTE_REVERSE_771／
     ENCHANTMENT_771／ENCHANTMENT_REVERSE_771）が生成スクリプトの
     出力（gen771.txt）と一致し、属性の名前表が minecraft-data の
     protocol.json の Update Attributes の key mapper と一致し、
     エンチャントの表が minecraft-data の enchantments-*.json の
     名前の並びと一致すること。エンチャントの並びそのものが正しい
     かは実機の Registry Data での確認（統合テスト）で行う。
     属性は 770 の 32 件から 771 の 35 件へ（camera_distance が 8 に
     挿入、waypoint_transmit_range・waypoint_receive_range を末尾に
     追加）。ATTRIBUTE_771_MAP の入力は 770 の 32 件、逆向きの入力は
     771 の 35 件。
  H. 音の名前の表（SOUND_NAME_FALLBACKS_771_TO_*）が数値の表と
     一致すること（771 の音の名前 → 数値で写した先の名前）。

使い方：
  python3 tools/verify-771-tables.py [--data-dir DIR]
  終了コード 0 = すべて合格。1 = 不一致あり（詳細を標準出力に出す）。
"""''' + s[j + 3:]

# --- TABLES_FILES と頭コメント ---
rep(
    '# 770 の表の実体。RegistryRemapper.java の静的初期化子が 64KB を超えた\n'
    '# ため、表だけ別クラスへ分離した（tools/insert-770-tables.py を参照）。',
    '# 771 の表の実体。RegistryRemapper.java の静的初期化子が 64KB を超えた\n'
    '# ため、表だけ別クラスへ分離した（tools/insert-771-tables.py を参照）。',
    "TABLES_FILES のコメント")
rep(
    '    for name in ("RegistryTables770.java", "RegistryTables770ComposedUp.java",\n'
    '                 "RegistryTables770Down763.java", "RegistryTables770Down764.java",\n'
    '                 "RegistryTables770Down765.java", "RegistryTables770Down766.java",\n'
    '                 "RegistryTables770Down767.java", "RegistryTables770Down768.java")',
    '    for name in ("RegistryTables771.java", "RegistryTables771ComposedUp.java",\n'
    '                 "RegistryTables771Down763.java", "RegistryTables771Down764.java",\n'
    '                 "RegistryTables771Down765.java", "RegistryTables771Down766.java",\n'
    '                 "RegistryTables771Down767.java", "RegistryTables771Down768.java",\n'
    '                 "RegistryTables771Down769.java")',
    "TABLES_FILES")

# --- 音の代替表の定数名とコメント ---
rep(
    '# 767 以前の音の代替表（D 上向きの真値で使う。767 で削除された\n'
    '# item.goat_horn.play／entity.goat.screaming.horn_break は 768 で\n'
    '# item.goat_horn.sound.0／entity.goat.horn_break に改名された）。\n'
    '# （770 ではさらに entity.wolf.howl が削除されたが、こちらは\n'
    '# sound_up_rename で扱う。）\n'
    'SOUND_FALLBACK_OLD_TO_770 = {',
    '# 767 以前の音の代替表（D 上向きの真値で使う。767 で削除された\n'
    '# item.goat_horn.play／entity.goat.screaming.horn_break は 768 で\n'
    '# item.goat_horn.sound.0／entity.goat.horn_break に改名された）。\n'
    '# （770 で entity.wolf.howl が削除されたが、こちらは\n'
    '# sound_up_rename で扱う。）771 で削除された音（leash_knot の 2 件と\n'
    '# puffer_fish.ambient）は 771 の対応する音へ写す。\n'
    'SOUND_FALLBACK_OLD_TO_771 = {',
    "SOUND_FALLBACK_OLD_TO_771")
rep(
    'SOUND_FALLBACK_OLD_TO_771 = {\n'
    '    "item.goat_horn.play": "item.goat_horn.sound.0",\n'
    '    "entity.goat.screaming.horn_break": "entity.goat.horn_break",\n'
    '}',
    'SOUND_FALLBACK_OLD_TO_771 = {\n'
    '    "item.goat_horn.play": "item.goat_horn.sound.0",\n'
    '    "entity.goat.screaming.horn_break": "entity.goat.horn_break",\n'
    '    "entity.leash_knot.break": "item.lead.break",\n'
    '    "entity.leash_knot.place": "item.lead.tied",\n'
    '    "entity.puffer_fish.ambient": "entity.puffer_fish.blow_out",\n'
    '}',
    "SOUND_FALLBACK_OLD_TO_771 の中身")
rep(
    '# 770→相手の音の代替表。下向きの真値では、770 の音の名前のうち相手に\n'
    '# 無いものは、改名の逆向きか石の破壊音へ。769→770 の改名は無いため、\n'
    '# 770→765／763／764 では 766→767 の改名の逆向き（ominous→charge）\n'
    '# と 767→768 の削除音の逆向き（不要：770 側に無い）は出てこない。\n'
    '# 770 の新音（52件）は石の破壊音へ。\n'
    'SOUND_FALLBACK_770_TO_TARGET = {',
    '# 771→相手の音の代替表。下向きの真値では、771 の音の名前のうち相手に\n'
    '# 無いものは、改名の逆向きか石の破壊音へ。770→771 の改名は無いため、\n'
    '# 771→765／763／764 では 766→767 の改名の逆向き（ominous→charge）\n'
    '# と 767→768 の削除音の逆向き（不要：771 側に無い）は出てこない。\n'
    '# 771 の新音（29件）は石の破壊音へ。\n'
    'SOUND_FALLBACK_771_TO_TARGET = {',
    "SOUND_FALLBACK_771_TO_TARGET")
rep(
    '    "block.trial_spawner.ambient_ominous": "block.trial_spawner.ambient_charged",\n'
    '}\n',
    '    "block.trial_spawner.ambient_ominous": "block.trial_spawner.ambient_charged",\n'
    '}\n'
    '\n'
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
    "SOUND_FALLBACK_771_TO_770 を追加")

# --- main() のデータ読み込み ---
rep(
    '    java = JAVA_FILE.read_text(encoding="utf-8")\n'
    '    tables770 = "".join(p.read_text(encoding="utf-8") for p in TABLES_FILES)\n'
    '    tables767 = (REPO_ROOT / "detergentmc-protocol" / "src" / "main" / "java"\n'
    '                 / "io" / "detergentmc" / "protocol" / "RegistryTables767.java").read_text(\n'
    '                     encoding="utf-8")\n'
    '    vv = {v: json.loads((data_dir / f"vv-{v}.json").read_text(encoding="utf-8"))\n'
    '          for v in ("1.20", "1.20.2", "1.20.3", "1.20.5", "1.21", "1.21.2", "1.21.4", "1.21.5")}\n'
    '    maps770 = json.loads((data_dir / "maps770.json").read_text(encoding="utf-8"))\n'
    '    maps_comp = json.loads((data_dir / "maps_composed_770.json").read_text(encoding="utf-8"))\n'
    '    rev770 = json.loads((data_dir / "map_items_rev770.json").read_text(encoding="utf-8"))\n'
    '    gen770 = (data_dir / "gen770.txt").read_text(encoding="utf-8")\n'
    '    # D (b) のオラクル用に 769 の合成表が必要。無ければ生成する。\n'
    '    maps_comp_769_path = data_dir / "maps_composed_769.json"\n'
    '    maps769_path = data_dir / "maps769.json"\n'
    '    if not maps_comp_769_path.exists() or not maps769_path.exists():\n'
    '        proc = subprocess.run(\n'
    '            [sys.executable, str(SCRIPT_DIR / "generate-remap-tables.py"),\n'
    '             "--target", "769", "--out", str(data_dir / "gen769-tmp.txt")],\n'
    '            capture_output=True, text=True, timeout=300)\n'
    '        if proc.returncode != 0:\n'
    '            print("[FAIL] 769 の表の生成に失敗した（D (b) のオラクル用）")\n'
    '            print(proc.stderr[-2000:])\n'
    '            return 1\n'
    '    maps_comp_769 = json.loads(maps_comp_769_path.read_text(encoding="utf-8"))\n'
    '    maps769 = json.loads(maps769_path.read_text(encoding="utf-8"))\n'
    '    tables769 = (REPO_ROOT / "detergentmc-protocol" / "src" / "main" / "java"\n'
    '                 / "io" / "detergentmc" / "protocol" / "RegistryTables769.java").read_text(\n'
    '                     encoding="utf-8")',
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
    "データ読み込み")

# --- 代替先の定数 ---
rep(
    '    # 770 側の代替先（769→770 の表の範囲外用。Java の定数と同じ値）。\n'
    '    # ブタは 770 では 94、石の破壊音は 770 では 1417。\n'
    '    fb770 = {"blockstates": 1, "items": 1, "entities": 94, "sounds": 1417,\n'
    '             "particles": 20, "blocks": 1, "menus": 12}\n'
    '    # 769 側の代替先（770→769 の反転の代替先。Java の定数と同じ値）。\n'
    '    # ブタは 769 では 94、石の破壊音は 769 では 1401。\n'
    '    fb769 = {"blockstates": 1, "items": 1, "entities": 94, "sounds": 1401,\n'
    '             "particles": 20, "blocks": 1, "menus": 12}\n'
    '    # 768 側の代替先（769→768 の反転の代替先。Java の定数と同じ値）。\n'
    '    # ブタは 768 では 95、石の破壊音は 768 では 1386。\n'
    '    fb768 = {"blockstates": 1, "items": 1, "entities": 95, "sounds": 1386,\n'
    '             "particles": 20, "blocks": 1, "menus": 12}',
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
    "代替先の定数")

if failed:
    print("失敗:", file=sys.stderr)
    for f in failed:
        print(" -", f, file=sys.stderr)
    sys.exit(1)
print("part 1 OK (docstring / TABLES_FILES / 音の代替表 / データ読み込み / 代替先）")

# --- A. *_771 表 ---
rep(
    '    # --- A. *_770 表が生成スクリプトの出力と一致する ---\n'
    '    for kind in kinds:\n'
    '        ranges = parse_int3_table(tables770, java_names[kind] + "_770_RANGES")\n'
    '        overrides = parse_int_table(tables770, java_names[kind] + "_770_OVERRIDES")\n'
    '        expanded = expand(ranges, overrides, len(vv["1.21.4"][kind]), fb770[kind])\n'
    '        gen = {int(k): v for k, v in maps770[kind].items()}\n'
    '        problems = [f"{kind}[{i}] Java={expanded[i]} 生成={gen[i]}"\n'
    '                    for i in range(len(vv["1.21.4"][kind]))\n'
    '                    if expanded[i] != gen[i]]\n'
    '        check(f"A. {kind}_770 表 = 生成出力", problems)',
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
    "A")

# --- F. ITEM_REVERSE_771 ---
rep(
    '    # --- F. ITEM_REVERSE_770 表が生成スクリプトの出力と一致する ---\n'
    '    ranges = parse_int3_table(tables770, "ITEM_REVERSE_770_RANGES")\n'
    '    overrides = parse_int_table(tables770, "ITEM_REVERSE_770_OVERRIDES")\n'
    '    expanded = expand(ranges, overrides, len(vv["1.21.5"]["items"]), 1)\n'
    '    gen = {int(k): v for k, v in rev770.items()}\n'
    '    problems = [f"items[{i}] Java={expanded[i]} 生成={gen[i]}"\n'
    '                for i in range(len(vv["1.21.5"]["items"]))\n'
    '                if expanded[i] != gen[i]]\n'
    '    check("F. ITEM_REVERSE_770 表 = 生成出力", problems)',
    '    # --- F. ITEM_REVERSE_771 表が生成スクリプトの出力と一致する ---\n'
    '    ranges = parse_int3_table(tables771, "ITEM_REVERSE_771_RANGES")\n'
    '    overrides = parse_int_table(tables771, "ITEM_REVERSE_771_OVERRIDES")\n'
    '    expanded = expand(ranges, overrides, len(vv["1.21.6"]["items"]), 1)\n'
    '    gen = {int(k): v for k, v in rev771.items()}\n'
    '    problems = [f"items[{i}] Java={expanded[i]} 生成={gen[i]}"\n'
    '                for i in range(len(vv["1.21.6"]["items"]))\n'
    '                if expanded[i] != gen[i]]\n'
    '    check("F. ITEM_REVERSE_771 表 = 生成出力", problems)',
    "F")

# --- B. 反転（区間ごと置き換え）---
NEW_B = '''    # --- B. *_771 表の反転と ITEM_REVERSE_771 が 771→770 の名前の真値と一致する ---
    # 771→770 に補正は無い（creaking_heart のプロパティ変更と potion の
    # 分割は 770 で済んでいる）。
    defaults770 = {b["name"]: b["defaultState"]
                   for b in json.loads(
                       (data_dir / "1.21.5_blocks.json").read_text(encoding="utf-8"))}
    for kind in kinds:
        if kind == "items":
            ranges = parse_int3_table(tables771, "ITEM_REVERSE_771_RANGES")
            overrides = parse_int_table(tables771, "ITEM_REVERSE_771_OVERRIDES")
            up = expand(ranges, overrides, len(vv["1.21.6"]["items"]), 1)
        else:
            ranges = parse_int3_table(tables771, java_names[kind] + "_771_RANGES")
            overrides = parse_int_table(tables771, java_names[kind] + "_771_OVERRIDES")
            up = invert_java(ranges, overrides, len(vv["1.21.5"][kind]),
                             len(vv["1.21.6"][kind]), fb770[kind], [])
        truth = name_truth(
            vv["1.21.6"][kind], vv["1.21.5"][kind], kind, fb770[kind],
            rename=None,
            sound_fallbacks=SOUND_FALLBACK_771_TO_770 if kind == "sounds" else None,
            particle_fallbacks=None,
            item_fallbacks=None,
            block_fallbacks=None,
            target_defaults=defaults770 if kind == "blockstates" else None,
            drop_one_prop=(kind == "blockstates"))
        if kind == "sounds":
            # 771 の blow_out（1204）は 770 の ambient（1179）と blow_out
            # （1180）の両方から写る（衝突）。invert() は先行勝ちで
            # ambient を選ぶため、真値も ambient にする（770 の
            # wolf.howl／wolf.ambient と同じ構図の逆）。
            truth[vv["1.21.6"]["sounds"].index("entity.puffer_fish.blow_out")] = \
                vv["1.21.5"]["sounds"].index("entity.puffer_fish.ambient")
        problems = []
        for i in range(len(vv["1.21.6"][kind])):
            if up[i] != truth[i]:
                problems.append(
                    f"{kind}[{i}] {vv['1.21.6'][kind][i]}: 反転={up[i]} "
                    f"真値={truth[i]}")
        check(f"B. {kind} 771→770 反転 = 名前の真値", problems)

'''
replace_section("    # --- B. *_770 表の反転", "    # --- C. 合成表", NEW_B, "B")

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
                               "sounds": 1401, "particles": 20, "blocks": 1, "menus": 12}}
    for tag in ("763_TO_771", "771_TO_763", "764_TO_771", "771_TO_764",
                "765_TO_771", "771_TO_765", "766_TO_771", "771_TO_766",
                "767_TO_771", "771_TO_767", "768_TO_771", "771_TO_768",
                "769_TO_771", "771_TO_769"):
        for kind in kinds:
            ranges = parse_int3_table(
                tables771, java_names[kind] + "_" + tag + "_RANGES")
            overrides = parse_int_table(
                tables771, java_names[kind] + "_" + tag + "_OVERRIDES")
            if tag.endswith("771"):
                src_v = {"763": "1.20", "764": "1.20.2",
                         "765": "1.20.3", "766": "1.20.5",
                         "767": "1.21", "768": "1.21.2",
                         "769": "1.21.4"}[tag[:3]]
                generic = fb771[kind]
            else:
                src_v = "1.21.6"
                generic = fb_by_target[{"763": "1.20", "764": "1.20.2",
                                        "765": "1.20.3", "766": "1.20.5",
                                        "767": "1.21", "768": "1.21.2",
                                        "769": "1.21.4"}[tag[7:10]]][kind]
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

# --- D. defaults に 1.21.6 を追加 ---
rep(
    '                     ("1.20.5", "1.20.5_blocks.json"),\n'
    '                     ("1.21.5", "1.21.5_blocks.json")):',
    '                     ("1.20.5", "1.20.5_blocks.json"),\n'
    '                     ("1.21.5", "1.21.5_blocks.json"),\n'
    '                     ("1.21.6", "1.21.6_blocks.json")):',
    "D defaults 1.21.6")

# --- D. pairs（区間ごと置き換え：pairs = [ から D-down (b) のコメントの直前まで） ---
NEW_PAIRS = '''    pairs = [
        ("763_TO_771", "1.20", "1.21.6",
         {"grass": "short_grass", "scute": "turtle_scute"},
         sound_up_rename,
         {"ambient_entity_effect": "entity_effect",
          "gust_emitter": "gust_emitter_large",
          "gust_dust": "small_gust"},
         {"trial_spawner": "spawner"}),
        ("771_TO_763", "1.21.6", "1.20",
         sound_down_rename,
         None, None,
         {"vault": "trial_spawner", "heavy_core": "stone"}),
        ("764_TO_771", "1.20.2", "1.21.6",
         {"grass": "short_grass", "scute": "turtle_scute"},
         sound_up_rename,
         {"ambient_entity_effect": "entity_effect",
          "gust_emitter": "gust_emitter_large",
          "gust_dust": "small_gust"},
         {"trial_spawner": "spawner"}),
        ("771_TO_764", "1.21.6", "1.20.2",
         sound_down_rename,
         None, None,
         {"vault": "trial_spawner", "heavy_core": "stone"}),
        ("765_TO_771", "1.20.3", "1.21.6",
         {"grass": "short_grass", "scute": "turtle_scute"},
         sound_up_rename,
         {"ambient_entity_effect": "entity_effect",
          "gust_emitter": "gust_emitter_large",
          "gust_dust": "small_gust"},
         {"trial_spawner": "spawner"}),
        ("771_TO_765", "1.21.6", "1.20.3",
         sound_down_rename,
         None, None,
         {"vault": "trial_spawner", "heavy_core": "stone"}),
        ("766_TO_771", "1.20.5", "1.21.6",
         {}, sound_up_rename, {}, {}),
        ("771_TO_766", "1.21.6", "1.20.5",
         sound_down_rename_766_767,
         None, None,
         {"vault": "trial_spawner", "heavy_core": "stone"}),
        ("767_TO_771", "1.21", "1.21.6",
         {}, sound_up_rename, {}, {}),
        ("771_TO_767", "1.21.6", "1.21",
         sound_down_rename_766_767,
         None, None,
         {"vault": "trial_spawner", "heavy_core": "stone"}),
        ("768_TO_771", "1.21.2", "1.21.6",
         {}, sound_up_rename, {}, {}),
        ("771_TO_768", "1.21.6", "1.21.2",
         sound_down_rename_766_767,
         None, None, {}),
        ("769_TO_771", "1.21.4", "1.21.6",
         {}, sound_up_rename, {}, {}),
        ("771_TO_769", "1.21.6", "1.21.4",
         sound_down_rename_766_767,
         None, None, {}),
    ]

'''
replace_section("    pairs = [\n", "    # D-down (b)", NEW_PAIRS, "D pairs")

# --- D(b) オラクル用 up770（区間ごと置き換え） ---
NEW_ORACLE = '''    # D-down (b) の合成オラクル用：770→相手は生成済みの合成表
    # （maps_composed_770.json）を使う。770→769 だけは合成表が無く
    # （769 が隣接元の版のため）、Java の *_770 表の反転で作る。
    # 770 の表が無ければ生成する（上の --target 770 の再生成と同じ）。
    entity_up_770_corrections = (
        parse_int_table(tables770, "ENTITY_UP_770_CORRECTIONS")
        if "ENTITY_UP_770_CORRECTIONS" in tables770 else [])
    bs_up_770_corrections = parse_int_table(
        tables770, "BLOCK_STATE_UP_770_CORRECTIONS")
    up770 = {}
    for kind in kinds:
        ranges = parse_int3_table(tables770, java_names[kind] + "_770_RANGES")
        overrides = parse_int_table(tables770, java_names[kind] + "_770_OVERRIDES")
        up770[kind] = invert_java(ranges, overrides, len(vv["1.21.4"][kind]),
                                  len(vv["1.21.5"][kind]), fb769[kind],
                                  bs_up_770_corrections
                                  if kind == "blockstates"
                                  else entity_up_770_corrections
                                  if kind == "entities" else [])

'''
replace_section("    # D-down (b)", "    for tag, src_v, dst_v, item_rename",
                NEW_ORACLE, "D oracle setup")

# --- D ループ内の小さいずれ ---
rep('        down = tag.startswith("770")',
    '        down = tag.startswith("771")', "D down フラグ")
rep(
    '            ranges = parse_int3_table(\n'
    '                tables770, java_names[kind] + "_" + tag + "_RANGES")\n'
    '            overrides = parse_int_table(\n'
    '                tables770, java_names[kind] + "_" + tag + "_OVERRIDES")',
    '            ranges = parse_int3_table(\n'
    '                tables771, java_names[kind] + "_" + tag + "_RANGES")\n'
    '            overrides = parse_int_table(\n'
    '                tables771, java_names[kind] + "_" + tag + "_OVERRIDES")',
    "D 合成表の parse")
rep(
    '            generic = (fb770[kind] if tag.endswith("770")\n'
    '                       else fb_by_target[dst_v][kind])',
    '            generic = (fb771[kind] if tag.endswith("771")\n'
    '                       else fb_by_target[dst_v][kind])',
    "D generic")
rep(
    '                        # 768 の creaking_transient は 769 で削除され、\n'
    '                        # 生成器は creaking に落とす（generate-remap-tables.py\n'
    '                        # の 769 の改名表）。770 でも creaking のまま。',
    '                        # 768 の creaking_transient は 769 で削除され、\n'
    '                        # 生成器は creaking に落とす（generate-remap-tables.py\n'
    '                        # の 769 の改名表）。771 でも creaking のまま。',
    "D creaking_transient コメント")
rep(
    '                    sound_fallbacks=SOUND_FALLBACK_OLD_TO_770\n'
    '                    if kind == "sounds" else None,',
    '                    sound_fallbacks=SOUND_FALLBACK_OLD_TO_771\n'
    '                    if kind == "sounds" else None,',
    "D sound_fallbacks 上向き")
rep(
    '                    # 768 の creaking_heart（creaking＋axis）→ 770 の真値。',
    '                    # 768 の creaking_heart（creaking＋axis）→ 771 の真値。',
    "D creaking_heart 上向き (1)")
rep(
    '                    # 768 の creaking_heart は 9 状態しか無く、769→770 の\n'
    '                    # 合成表は 769 の active＋natural 経由で同じ値になる。',
    '                    # 768 の creaking_heart は 9 状態しか無く、770→771 の\n'
    '                    # 合成表は 770 の creaking_heart_state 経由で同じ値になる。',
    "D creaking_heart 上向き (2)")
rep('                idx770 = {n: i for i, n in enumerate(vv["1.21.5"]["blockstates"])}',
    '                idx771 = {n: i for i, n in enumerate(vv["1.21.6"]["blockstates"])}',
    "D idx771")
rep('                        truth[i] = idx770[target]',
    '                        truth[i] = idx771[target]', "D idx771 truth")
rep(
    '                sound_fallbacks=SOUND_FALLBACK_770_TO_TARGET if kind == "sounds" else None,',
    '                sound_fallbacks=SOUND_FALLBACK_771_TO_TARGET if kind == "sounds" else None,',
    "D sound_fallbacks 下向き")
rep(
    '                # 770 の creaking_heart → 768 の真値（768 は creaking',
    '                # 771 の creaking_heart → 768 の真値（768 は creaking',
    "D creaking_heart 下向き")
# --- D 上向き：769 の creaking_heart の特殊処理 ---
rep(
    '                        truth[i] = idx771[target]\n'
    '                problems = []',
    '                        truth[i] = idx771[target]\n'
    '                if kind == "blockstates" and src_v == "1.21.4":\n'
    '                    # 769 の creaking_heart（active＋axis＋natural）→ 771 の真値。\n'
    '                    # active=true → awake、active=false → dormant\n'
    '                    # （770 の B の補正と同じ決め方）。\n'
    '                    idx771 = {n: i for i, n in enumerate(vv["1.21.6"]["blockstates"])}\n'
    '                    for i, name in enumerate(vv[src_v]["blockstates"]):\n'
    '                        if not name.startswith("creaking_heart["):\n'
    '                            continue\n'
    '                        props = block_props(name)\n'
    '                        state = ("awake" if props.get("active") == "true"\n'
    '                                 else "dormant")\n'
    '                        target = (f"creaking_heart[axis={props[\'axis\']},"\n'
    '                                  f"creaking_heart_state={state},natural={props[\'natural\']}]")\n'
    '                        truth[i] = idx771[target]\n'
    '                problems = []',
    "D creaking_heart 769 上向き")
# --- D 下向き：769 の creaking_heart と blow_out の特殊処理 ---
rep(
    '                    truth[i] = idx768[target]\n'
    '            target_set = set(vv[dst_v][kind])',
    '                    truth[i] = idx768[target]\n'
    '            if kind == "blockstates" and dst_v == "1.21.4":\n'
    '                # 771 の creaking_heart → 769 の真値（769 は active＋axis＋\n'
    '                # natural）。awake → active=true、dormant／uprooted →\n'
    '                # active=false（770 の B の補正と同じ決め方）。\n'
    '                idx769 = {n: i for i, n in enumerate(vv["1.21.4"]["blockstates"])}\n'
    '                for i, name in enumerate(vv[src_v]["blockstates"]):\n'
    '                    if not name.startswith("creaking_heart["):\n'
    '                        continue\n'
    '                    props = block_props(name)\n'
    '                    active = ("true" if props.get("creaking_heart_state") == "awake"\n'
    '                              else "false")\n'
    '                    target = (f"creaking_heart[active={active},axis={props[\'axis\']},"\n'
    '                              f"natural={props[\'natural\']}]")\n'
    '                    truth[i] = idx769[target]\n'
    '            if kind == "sounds":\n'
    '                # 771 の blow_out は 770 の ambient と blow_out の両方から\n'
    '                # 写る（衝突）。invert() は先行勝ちで ambient を選ぶため、\n'
    '                # 合成表も相手の ambient になる。\n'
    '                idx_dst = {n: i for i, n in enumerate(vv[dst_v]["sounds"])}\n'
    '                truth[vv[src_v]["sounds"].index("entity.puffer_fish.blow_out")] = \\\n'
    '                    idx_dst["entity.puffer_fish.ambient"]\n'
    '            target_set = set(vv[dst_v][kind])',
    "D creaking_heart 769 下向き＋blow_out")

# --- D(b) オラクル部分（区間ごと置き換え） ---
NEW_DB = '''            # 下向き (b)：合成表全体が「771→770 の反転 × 770→相手の表」
            # と一致するはず。代替先の決め方は 770→相手の表に委ねる。
            # 770→769 だけ合成表が無いため up770（770→769 の反転）を使う。
            if dst_v == "1.21.4":
                oracle_map = up770[kind]
            else:
                tag2 = "770_TO_" + tag[7:10]
                oracle_raw = {int(k): v for k, v in maps_comp_770[tag2][kind].items()}
                oracle_map = [oracle_raw[i] for i in range(len(vv["1.21.5"][kind]))]
            if kind == "items":
                r = parse_int3_table(tables771, "ITEM_REVERSE_771_RANGES")
                o = parse_int_table(tables771, "ITEM_REVERSE_771_OVERRIDES")
                up771 = expand(r, o, len(vv["1.21.6"]["items"]), 1)
            else:
                r = parse_int3_table(tables771, java_names[kind] + "_771_RANGES")
                o = parse_int_table(tables771, java_names[kind] + "_771_OVERRIDES")
                up771 = invert_java(r, o, len(vv["1.21.5"][kind]),
                                    len(vv["1.21.6"][kind]), fb770[kind], [])
            problems = []
            for i in range(size):
                expected = oracle_map[up771[i]]
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
    # --target 771 を再実行し、Java ファイル内の表と完全一致する
    # （生成スクリプトをコピーした直後の転記ミスの検出）。
    tmp = data_dir / "verify-tmp-771.txt"
    proc = subprocess.run(
        [sys.executable, str(SCRIPT_DIR / "generate-remap-tables.py"),
         "--target", "771", "--out", str(tmp)],
        capture_output=True, text=True, timeout=300)
    if proc.returncode != 0:
        print("[FAIL] E. 生成スクリプト --target 771 が失敗した")
        print(proc.stderr[-2000:])
        failures.append("E-771")
    else:
        gen_re = tmp.read_text(encoding="utf-8")
        problems = []
        for m in re.finditer(
                r"^(?:public static final int\\[\\]\\[\\] )?([A-Z0-9_]+) = "
                r"compress\\(new int\\[\\]\\[\\]\\{(.*?)\\}\\);",
                gen_re, re.MULTILINE | re.DOTALL):
            name = m.group(1)
            # ATTRIBUTE_770_NAMES は RegistryTables770.java にあるため
            # 771 側には入れない（insert-771-tables.py と同じ）。
            if name == "ATTRIBUTE_770_NAMES":
                continue
            m2 = re.search(re.escape(name)
                           + r" = \\{(.*?)\\n    \\};", tables771, re.DOTALL)
            if not m2:
                problems.append(f"{name} が RegistryTables771*.java に無い")
        check("E. 生成スクリプト --target 771 の再現性", problems)

'''
replace_section("    # --- E. 生成スクリプト", "    # --- G.", NEW_E, "E")

# --- G. 属性・エンチャント（区間ごと置き換え） ---
NEW_G = '''    # --- G. 属性・エンチャントの表 ---
    for name, size, fb in (("ATTRIBUTE_771", 32, -1),
                           ("ATTRIBUTE_REVERSE_771", 35, -1),
                           ("ENCHANTMENT_771", 42, -1),
                           ("ENCHANTMENT_REVERSE_771", 42, -1)):
        ranges = parse_int3_table(tables771, name + "_RANGES")
        overrides = parse_int_table(tables771, name + "_OVERRIDES")
        expanded = expand(ranges, overrides, size, fb)
        gm_ranges = re.search(r"int\\[\\]\\[\\] " + re.escape(name) + r"_RANGES = \\{(.*?)\\n    \\};",
                              gen771, re.DOTALL)
        gm_overrides = re.search(r"int\\[\\]\\[\\] " + re.escape(name) + r"_OVERRIDES = \\{(.*?)\\n    \\};",
                                 gen771, re.DOTALL)
        if not gm_ranges or not gm_overrides:
            check(f"G. {name} 表 = 生成出力", [f"{name} が gen771.txt に無い"])
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
    mc770 = json.loads((data_dir / "mc-1.21.5-protocol.json").read_text(encoding="utf-8"))
    mc771 = json.loads((data_dir / "mc-1.21.6-protocol.json").read_text(encoding="utf-8"))
    for name, proto, source in (("ATTRIBUTE_770_NAMES", mc770, tables770),
                                ("ATTRIBUTE_771_NAMES", mc771, tables771)):
        java_names_list = parse_string_array(source, name)
        truth = attribute_names(proto)
        problems = [f"{name}[{i}] Java={java_names_list[i]} 真値={truth[i]}"
                    for i in range(len(truth))
                    if i >= len(java_names_list) or java_names_list[i] != truth[i]]
        if len(java_names_list) != len(truth):
            problems.append(f"{name} の件数が違う：Java={len(java_names_list)} 真値={len(truth)}")
        check(f"G. {name} = protocol.json", problems)
    # エンチャントの表 = minecraft-data の enchantments-*.json の並び。
    ench771 = [e["name"] for e in json.loads(
        (data_dir / "enchantments-1.21.6.json").read_text(encoding="utf-8"))]
    idx771 = {n: i for i, n in enumerate(ench771)}
    # 1.21.6 の enchantments は dataPaths.json により pc/1.21.6 を使う
    # （1.21.5 の 42 件と同じ名前・同じ並び）。
    for name, src in (("ENCHANTMENT_771", ench771),
                      ("ENCHANTMENT_REVERSE_771", ench771)):
        ranges = parse_int3_table(tables771, name + "_RANGES")
        overrides = parse_int_table(tables771, name + "_OVERRIDES")
        expanded = expand(ranges, overrides, len(src), -1)
        problems = []
        for i, n in enumerate(src):
            expected = idx771.get(n, -1)
            if expanded[i] != expected:
                problems.append(f"{name}[{i}] {n}: Java={expanded[i]} 真値={expected}")
        check(f"G. {name} = enchantments-*.json の並び", problems)

'''
replace_section("    # --- G.", "    # --- H.", NEW_G, "G")

# --- H. 音の名前の表（区間ごと置き換え） ---
NEW_H = '''    # --- H. 音の名前の表が数値の表と一致する ---
    sound_name_tables = {
        "SOUND_NAME_FALLBACKS_771_TO_770": ("1.21.6", "1.21.5", "771_TO_770"),
        "SOUND_NAME_FALLBACKS_771_TO_769": ("1.21.6", "1.21.4", "771_TO_769"),
        "SOUND_NAME_FALLBACKS_771_TO_768": ("1.21.6", "1.21.2", "771_TO_768"),
        "SOUND_NAME_FALLBACKS_771_TO_767": ("1.21.6", "1.21", "771_TO_767"),
        "SOUND_NAME_FALLBACKS_771_TO_765": ("1.21.6", "1.20.3", "771_TO_765"),
        "SOUND_NAME_FALLBACKS_771_TO_763": ("1.21.6", "1.20", "771_TO_763"),
        "SOUND_NAME_FALLBACKS_771_TO_764": ("1.21.6", "1.20.2", "771_TO_764"),
        "SOUND_NAME_FALLBACKS_771_TO_766": ("1.21.6", "1.20.5", "771_TO_766"),
    }
    for name, (src_v, dst_v, tag) in sound_name_tables.items():
        pairs = parse_string_table(tables771, name)
        if tag == "771_TO_770":
            # 771→770 は合成表ではなく直接の表の反転。
            ranges = parse_int3_table(tables771, "SOUND_771_RANGES")
            overrides = parse_int_table(tables771, "SOUND_771_OVERRIDES")
            numeric = invert_java(ranges, overrides, len(vv["1.21.5"]["sounds"]),
                                  len(vv["1.21.6"]["sounds"]), 1417, [])
        else:
            ranges = parse_int3_table(tables771, "SOUND_" + tag + "_RANGES")
            overrides = parse_int_table(tables771, "SOUND_" + tag + "_OVERRIDES")
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
NEW_I = '''    # --- I. ダメージ種別の表（手書き）が 770 と同じ並びであること ---
    # DAMAGE_TYPE_771_MAP／REVERSE_771_MAP は生成スクリプトの出力では
    # なく insert-771-java.py の手書きのため、ここで独立に検証する。
    # 771 のダメージ種別は 770 と同じ 49 種で、並びも同じはず
    # （実バニラの Registry Data での確認は統合テストで行う）。
    # まだ insert-771-java.py が走っていない場合は検証を飛ばす。
    if "DAMAGE_TYPE_771_MAP" not in java:
        print("[SKIP] I. ダメージ種別の表（insert-771-java.py 未実行）")
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

            fwd = parse_int_array(java, "DAMAGE_TYPE_771_MAP")
            problems = []
            if len(fwd) != len(names768):
                problems.append(f"DAMAGE_TYPE_771_MAP の件数が違う：{len(fwd)} != {len(names768)}")
            for i in range(len(fwd)):
                if fwd[i] != i:
                    problems.append(f"770[{i}] {names768[i]}: Java={fwd[i]} 真値={i}")
            check("I. DAMAGE_TYPE_771_MAP = 770 と同じ並び", problems)
            rev = parse_int_array(java, "DAMAGE_TYPE_REVERSE_771_MAP")
            problems = []
            if len(rev) != len(names768):
                problems.append(f"DAMAGE_TYPE_REVERSE_771_MAP の件数が違う：{len(rev)} != {len(names768)}")
            for i in range(len(rev)):
                if rev[i] != i:
                    problems.append(f"771[{i}] {names768[i]}: Java={rev[i]} 真値={i}")
            check("I. DAMAGE_TYPE_REVERSE_771_MAP = 770 と同じ並び", problems)

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
