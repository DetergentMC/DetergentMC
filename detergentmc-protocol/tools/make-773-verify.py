#!/usr/bin/env python3
"""verify-772-tables.py から verify-773-tables.py を作る（1回だけの作業用）。

機械的なずらし（772→773、771→772）と、773で意味が変わる箇所
（Bの補正なし・新音は銅関連など43件・chain→iron_chain の改名、
D(b)オラクルの隣接元が771→772、Gの属性35件同士、Hの773→772反転・
771追加、Iのダメージ種別773）をまとめて行う。

773 で新しく要る変更（772 には無かったもの）：
  - name_truth にブロック名だけの改名（chain[axis=...] →
    iron_chain[axis=...] のようなプロパティ付きの改名）を扱う分岐を追加。
    生成器の build_blockstates はブロック名の接頭辞だけを置き換えるため、
    真値も同じ決め方にしないと B・D で chain のブロック状態が合わない。
  - B の真値と D の下向きの改名に iron_chain→chain を入れる。
  - D の上向きの改名に chain→iron_chain を入れる（全 9 ソース）。
"""
import shutil
import sys
from pathlib import Path

SCRIPT_DIR = Path(__file__).resolve().parent
SRC = SCRIPT_DIR / "verify-772-tables.py"
DST = SCRIPT_DIR / "verify-773-tables.py"

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
i = s.find('"""RegistryRemapper.java の 772')
j = s.find('"""', i + 3)
assert i >= 0 and j > i
s = s[:i] + '''"""RegistryRemapper.java の 773 の表を検証する。

検証すること（すべて名前ベースの独立した真値と突き合わせる）：
  A. *_773 表（772→773）が生成スクリプトの出力（maps773.json）と
     一致すること（転記ミスの検出）。
  B. *_773 表の反転（Java の invert() と同じ決め方。補正なし）と
     ITEM_REVERSE_773 表が、名前から作った 773→772 の真値と一致
     すること。773 にしか無いものは代替先（石・ブタ・石の破壊音・
     entity_effect・作業台）になる。chain→iron_chain の改名は rename で扱う。
  C. 合成表（763↔773、764↔773、765↔773、766↔773、767↔773、768↔773、
     769↔773、770↔773、771↔773）が生成スクリプトの出力（maps_composed_773.json）と一致すること。
  D. 合成表が、両端のレジストリを名前で直接突き合わせた真値と一致
     すること。上向き（763／764／765／766／767／768／769／770／771→773）は不一致ゼロの
     はず。下向き（773→763／764／765／766／767／768／769／770／771）は (a) 同名があるものは
     その番号と一致すること、(b) 合成表全体が「773→772 の反転 ×
     772→相手の表」と一致すること（オラクル）。
  E. 生成スクリプトの再現性：--target 773 を再実行し、Java ファイル
     内の表と完全一致すること。
  F. ITEM_REVERSE_773 表が生成スクリプトの出力
     （map_items_rev773.json）と一致すること。
  G. 属性・エンチャントの表（ATTRIBUTE_773／ATTRIBUTE_REVERSE_773／
     ENCHANTMENT_773／ENCHANTMENT_REVERSE_773）が生成スクリプトの
     出力（gen773.txt）と一致し、属性の名前表が minecraft-data の
     protocol.json の Update Attributes の key mapper と一致し、
     エンチャントの表が minecraft-data の enchantments-*.json の
     名前の並びと一致すること。エンチャントの並びそのものが正しい
     かは実機の Registry Data での確認（統合テスト）で行う。
     属性は 772・773 とも 35 件で同一。ATTRIBUTE_773_MAP の入力は
     772 の 35 件、逆向きの入力は 773 の 35 件。
  H. 音の名前の表（SOUND_NAME_FALLBACKS_773_TO_*）が数値の表と
     一致すること（773 の音の名前 → 数値で写した先の名前）。

使い方：
  python3 tools/verify-773-tables.py [--data-dir DIR]
  終了コード 0 = すべて合格。1 = 不一致あり（詳細を標準出力に出す）。
"""''' + s[j + 3:]

# --- TABLES_FILES と頭コメント ---
rep(
    '# 772 の表の実体。RegistryRemapper.java の静的初期化子が 64KB を超えた\n'
    '# ため、表だけ別クラスへ分離した（tools/insert-772-tables.py を参照）。',
    '# 773 の表の実体。RegistryRemapper.java の静的初期化子が 64KB を超えた\n'
    '# ため、表だけ別クラスへ分離した（tools/insert-773-tables.py を参照）。',
    "TABLES_FILES のコメント")
rep(
    '    for name in ("RegistryTables772.java", "RegistryTables772ComposedUp.java",\n'
    '                 "RegistryTables772Down763.java", "RegistryTables772Down764.java",\n'
    '                 "RegistryTables772Down765.java", "RegistryTables772Down766.java",\n'
    '                 "RegistryTables772Down767.java", "RegistryTables772Down768.java",\n'
    '                 "RegistryTables772Down769.java", "RegistryTables772Down770.java")',
    '    for name in ("RegistryTables773.java", "RegistryTables773ComposedUp.java",\n'
    '                 "RegistryTables773Down763.java", "RegistryTables773Down764.java",\n'
    '                 "RegistryTables773Down765.java", "RegistryTables773Down766.java",\n'
    '                 "RegistryTables773Down767.java", "RegistryTables773Down768.java",\n'
    '                 "RegistryTables773Down769.java", "RegistryTables773Down770.java",\n'
    '                 "RegistryTables773Down771.java", "RegistryTables773SoundNames.java",\n'
    '                 "RegistryTables773Down763BlockState.java",\n'
    '                 "RegistryTables773Down764BlockState.java",\n'
    '                 "RegistryTables773Down765BlockState.java",\n'
    '                 "RegistryTables773Down766BlockState.java",\n'
    '                 "RegistryTables773Down767BlockState.java",\n'
    '                 "RegistryTables773Down768BlockState.java",\n'
    '                 "RegistryTables773Down769BlockState.java",\n'
    '                 "RegistryTables773Down770BlockState.java",\n'
    '                 "RegistryTables773Down771BlockState.java")',
    "TABLES_FILES")

# --- name_truth にブロック名だけの改名を追加（chain→iron_chain 用） ---
rep(
    '        if kind == "blockstates":\n'
    '            props = block_props(name)\n'
    '            found = None\n',
    '        if kind == "blockstates":\n'
    '            props = block_props(name)\n'
    '            # ブロック名だけの改名（プロパティ付きのブロック状態用）。\n'
    '            # chain[axis=...] → iron_chain[axis=...] のように、ブロック名\n'
    '            # の接頭辞だけが変わる改名は完全一致では拾えないため、同じ\n'
    '            # プロパティの改名先を探す（生成器の build_blockstates と同じ決め方）。\n'
    '            if block_name(name) in rename:\n'
    '                renamed = rename[block_name(name)]\n'
    '                if (renamed, frozenset(props.items())) in by_props:\n'
    '                    out[i] = by_props[(renamed, frozenset(props.items()))]\n'
    '                    continue\n'
    '            found = None\n',
    "name_truth ブロック名の改名")

# --- 音の代替表の定数名とコメント ---
rep(
    '# （770 で entity.wolf.howl が削除されたが、こちらは\n'
    '# sound_up_rename で扱う。）771 で削除された音（leash_knot の 2 件と\n'
    '# puffer_fish.ambient）は 772 の対応する音へ写す。772 で削除された\n'
    '# 音は無い（772 の新音は music_disc.lava_chicken だけ）。\n'
    'SOUND_FALLBACK_OLD_TO_772 = {',
    '# （770 で entity.wolf.howl が削除されたが、こちらは\n'
    '# sound_up_rename で扱う。）771 で削除された音（leash_knot の 2 件と\n'
    '# puffer_fish.ambient）は 773 の対応する音へ写す。773 で削除された\n'
    '# 音は無い（773 の新音は銅ゴーレム・棚・銅のチェスト関連など 43 件）。\n'
    'SOUND_FALLBACK_OLD_TO_773 = {',
    "SOUND_FALLBACK_OLD_TO_773")
rep(
    'SOUND_FALLBACK_OLD_TO_773 = {\n'
    '    "item.goat_horn.play": "item.goat_horn.sound.0",\n'
    '    "entity.goat.screaming.horn_break": "entity.goat.horn_break",\n'
    '    "entity.leash_knot.break": "item.lead.break",\n'
    '    "entity.leash_knot.place": "item.lead.tied",\n'
    '    "entity.puffer_fish.ambient": "entity.puffer_fish.blow_out",\n'
    '    # block.sand.wind は 770 だけにあり、771 で dry_grass.ambient\n'
    '    # へ写った。770→772 は合成表（D の対象）のため、ここで扱う。\n'
    '    "block.sand.wind": "block.dry_grass.ambient",\n'
    '}',
    'SOUND_FALLBACK_OLD_TO_773 = {\n'
    '    "item.goat_horn.play": "item.goat_horn.sound.0",\n'
    '    "entity.goat.screaming.horn_break": "entity.goat.horn_break",\n'
    '    "entity.leash_knot.break": "item.lead.break",\n'
    '    "entity.leash_knot.place": "item.lead.tied",\n'
    '    "entity.puffer_fish.ambient": "entity.puffer_fish.blow_out",\n'
    '    # block.sand.wind は 770 だけにあり、771 で dry_grass.ambient\n'
    '    # へ写った。770→773 は合成表（D の対象）のため、ここで扱う。\n'
    '    "block.sand.wind": "block.dry_grass.ambient",\n'
    '}',
    "SOUND_FALLBACK_OLD_TO_773 の中身")
rep(
    '# 772→相手の音の代替表。下向きの真値では、772 の音の名前のうち相手に\n'
    '# 無いものは、改名の逆向きか石の破壊音へ。771→772 の改名は無いため、\n'
    '# 772→765／763／764 では 766→767 の改名の逆向き（ominous→charge）\n'
    '# と 767→768 の削除音の逆向き（不要：772 側に無い）は出てこない。\n'
    '# 772 の新音（1件：music_disc.lava_chicken）は石の破壊音へ。\n'
    'SOUND_FALLBACK_772_TO_TARGET = {',
    '# 773→相手の音の代替表。下向きの真値では、773 の音の名前のうち相手に\n'
    '# 無いものは、改名の逆向きか石の破壊音へ。772→773 の改名は無いため、\n'
    '# 773→765／763／764 では 766→767 の改名の逆向き（ominous→charge）\n'
    '# と 767→768 の削除音の逆向き（不要：773 側に無い）は出てこない。\n'
    '# 773 の新音（43件：銅ゴーレム・棚・銅のチェスト関連など）は石の破壊音へ\n'
    '# （代替表に無いものは既定の石の破壊音へ）。\n'
    'SOUND_FALLBACK_773_TO_TARGET = {',
    "SOUND_FALLBACK_773_TO_TARGET")
rep(
    '# 772→771 の音の代替表（B の真値で使う。772 の新音は\n'
    '# music_disc.lava_chicken だけ。771 に対応する音は無く石の破壊音へ）。\n'
    'SOUND_FALLBACK_772_TO_771 = {\n'
    '    "music_disc.lava_chicken": "block.stone.break",\n'
    '}\n',
    '# 773→772 の音の代替表（B の真値で使う。773 の新音 43 件。\n'
    '# 772 に対応する音は無く石の破壊音へ）。\n'
    'SOUND_FALLBACK_773_TO_772 = {\n'
    '    "item.armor.equip_copper": "block.stone.break",\n'
    '    "block.copper_chest.close": "block.stone.break",\n'
    '    "block.copper_chest.open": "block.stone.break",\n'
    '    "block.copper_chest_weathered.close": "block.stone.break",\n'
    '    "block.copper_chest_weathered.open": "block.stone.break",\n'
    '    "block.copper_chest_oxidized.close": "block.stone.break",\n'
    '    "block.copper_chest_oxidized.open": "block.stone.break",\n'
    '    "entity.copper_golem.step": "block.stone.break",\n'
    '    "entity.copper_golem.hurt": "block.stone.break",\n'
    '    "entity.copper_golem.death": "block.stone.break",\n'
    '    "entity.copper_golem_weathered.step": "block.stone.break",\n'
    '    "entity.copper_golem_weathered.hurt": "block.stone.break",\n'
    '    "entity.copper_golem_weathered.death": "block.stone.break",\n'
    '    "entity.copper_golem_oxidized.step": "block.stone.break",\n'
    '    "entity.copper_golem_oxidized.hurt": "block.stone.break",\n'
    '    "entity.copper_golem_oxidized.death": "block.stone.break",\n'
    '    "entity.copper_golem.spin": "block.stone.break",\n'
    '    "entity.copper_golem_weathered.spin": "block.stone.break",\n'
    '    "entity.copper_golem_oxidized.spin": "block.stone.break",\n'
    '    "entity.copper_golem.no_item_get": "block.stone.break",\n'
    '    "entity.copper_golem.no_item_no_get": "block.stone.break",\n'
    '    "entity.copper_golem.item_drop": "block.stone.break",\n'
    '    "entity.copper_golem.item_no_drop": "block.stone.break",\n'
    '    "entity.copper_golem_become_statue": "block.stone.break",\n'
    '    "block.copper_golem_statue.break": "block.stone.break",\n'
    '    "block.copper_golem_statue.place": "block.stone.break",\n'
    '    "block.copper_golem_statue.hit": "block.stone.break",\n'
    '    "block.copper_golem_statue.step": "block.stone.break",\n'
    '    "block.copper_golem_statue.fall": "block.stone.break",\n'
    '    "entity.copper_golem.spawn": "block.stone.break",\n'
    '    "entity.copper_golem.shear": "block.stone.break",\n'
    '    "block.shelf.activate": "block.stone.break",\n'
    '    "block.shelf.break": "block.stone.break",\n'
    '    "block.shelf.deactivate": "block.stone.break",\n'
    '    "block.shelf.fall": "block.stone.break",\n'
    '    "block.shelf.hit": "block.stone.break",\n'
    '    "block.shelf.multi_swap": "block.stone.break",\n'
    '    "block.shelf.place": "block.stone.break",\n'
    '    "block.shelf.place_item": "block.stone.break",\n'
    '    "block.shelf.single_swap": "block.stone.break",\n'
    '    "block.shelf.step": "block.stone.break",\n'
    '    "block.shelf.take_item": "block.stone.break",\n'
    '    "weather.end_flash": "block.stone.break",\n'
    '}\n',
    "SOUND_FALLBACK_773_TO_772 を追加")

# --- main() のデータ読み込み ---
rep(
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
    '    java = JAVA_FILE.read_text(encoding="utf-8")\n'
    '    tables773 = "".join(p.read_text(encoding="utf-8") for p in TABLES_FILES)\n'
    '    vv = {v: json.loads((data_dir / f"vv-{v}.json").read_text(encoding="utf-8"))\n'
    '          for v in ("1.20", "1.20.2", "1.20.3", "1.20.5", "1.21", "1.21.2", "1.21.4", "1.21.5",\n'
    '                    "1.21.6", "1.21.7", "1.21.9")}\n'
    '    maps773 = json.loads((data_dir / "maps773.json").read_text(encoding="utf-8"))\n'
    '    maps_comp = json.loads((data_dir / "maps_composed_773.json").read_text(encoding="utf-8"))\n'
    '    rev773 = json.loads((data_dir / "map_items_rev773.json").read_text(encoding="utf-8"))\n'
    '    gen773 = (data_dir / "gen773.txt").read_text(encoding="utf-8")\n'
    '    # D (b) のオラクル用に 772 の合成表が必要。無ければ生成する。\n'
    '    maps_comp_772_path = data_dir / "maps_composed_772.json"\n'
    '    maps772_path = data_dir / "maps772.json"\n'
    '    if not maps_comp_772_path.exists() or not maps772_path.exists():\n'
    '        proc = subprocess.run(\n'
    '            [sys.executable, str(SCRIPT_DIR / "generate-remap-tables.py"),\n'
    '             "--target", "772", "--out", str(data_dir / "gen772-tmp.txt")],\n'
    '            capture_output=True, text=True, timeout=300)\n'
    '        if proc.returncode != 0:\n'
    '            print("[FAIL] 772 の表の生成に失敗した（D (b) のオラクル用）")\n'
    '            print(proc.stderr[-2000:])\n'
    '            return 1\n'
    '    maps_comp_772 = json.loads(maps_comp_772_path.read_text(encoding="utf-8"))\n'
    '    maps772 = json.loads(maps772_path.read_text(encoding="utf-8"))\n'
    '    tables772 = (REPO_ROOT / "detergentmc-protocol" / "src" / "main" / "java"\n'
    '                 / "io" / "detergentmc" / "protocol" / "RegistryTables772.java").read_text(\n'
    '                     encoding="utf-8")',
    "データ読み込み")

# --- 代替先の定数 ---
rep(
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
    '    # 773 側の代替先（772→773 の表の範囲外用。Java の定数と同じ値）。\n'
    '    # ブタは 773 では 97、石の破壊音は 773 では 1484。\n'
    '    fb773 = {"blockstates": 1, "items": 1, "entities": 97, "sounds": 1484,\n'
    '             "particles": 20, "blocks": 1, "menus": 12}\n'
    '    # 772 側の代替先（773→772 の反転の代替先。Java の定数と同じ値）。\n'
    '    # ブタは 772 では 95、石の破壊音は 772 では 1442。\n'
    '    fb772 = {"blockstates": 1, "items": 1, "entities": 95, "sounds": 1442,\n'
    '             "particles": 20, "blocks": 1, "menus": 12}\n'
    '    # 771 側の代替先（772→771 の反転の代替先。Java の定数と同じ値）。\n'
    '    # ブタは 771 では 95、石の破壊音は 771 では 1441。\n'
    '    fb771 = {"blockstates": 1, "items": 1, "entities": 95, "sounds": 1441,\n'
    '             "particles": 20, "blocks": 1, "menus": 12}',
    "代替先の定数")

if failed:
    print("失敗:", file=sys.stderr)
    for f in failed:
        print(" -", f, file=sys.stderr)
    sys.exit(1)
print("part 1 OK (docstring / TABLES_FILES / 音の代替表 / データ読み込み / 代替先）")

# --- A. *_773 表 ---
rep(
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
    '    # --- A. *_773 表が生成スクリプトの出力と一致する ---\n'
    '    for kind in kinds:\n'
    '        ranges = parse_int3_table(tables773, java_names[kind] + "_773_RANGES")\n'
    '        overrides = parse_int_table(tables773, java_names[kind] + "_773_OVERRIDES")\n'
    '        expanded = expand(ranges, overrides, len(vv["1.21.7"][kind]), fb773[kind])\n'
    '        gen = {int(k): v for k, v in maps773[kind].items()}\n'
    '        problems = [f"{kind}[{i}] Java={expanded[i]} 生成={gen[i]}"\n'
    '                    for i in range(len(vv["1.21.7"][kind]))\n'
    '                    if expanded[i] != gen[i]]\n'
    '        check(f"A. {kind}_773 表 = 生成出力", problems)',
    "A")

# --- F. ITEM_REVERSE_773 ---
rep(
    '    # --- F. ITEM_REVERSE_772 表が生成スクリプトの出力と一致する ---\n'
    '    ranges = parse_int3_table(tables772, "ITEM_REVERSE_772_RANGES")\n'
    '    overrides = parse_int_table(tables772, "ITEM_REVERSE_772_OVERRIDES")\n'
    '    expanded = expand(ranges, overrides, len(vv["1.21.7"]["items"]), 1)\n'
    '    gen = {int(k): v for k, v in rev772.items()}\n'
    '    problems = [f"items[{i}] Java={expanded[i]} 生成={gen[i]}"\n'
    '                for i in range(len(vv["1.21.7"]["items"]))\n'
    '                if expanded[i] != gen[i]]\n'
    '    check("F. ITEM_REVERSE_772 表 = 生成出力", problems)',
    '    # --- F. ITEM_REVERSE_773 表が生成スクリプトの出力と一致する ---\n'
    '    ranges = parse_int3_table(tables773, "ITEM_REVERSE_773_RANGES")\n'
    '    overrides = parse_int_table(tables773, "ITEM_REVERSE_773_OVERRIDES")\n'
    '    expanded = expand(ranges, overrides, len(vv["1.21.9"]["items"]), 1)\n'
    '    gen = {int(k): v for k, v in rev773.items()}\n'
    '    problems = [f"items[{i}] Java={expanded[i]} 生成={gen[i]}"\n'
    '                for i in range(len(vv["1.21.9"]["items"]))\n'
    '                if expanded[i] != gen[i]]\n'
    '    check("F. ITEM_REVERSE_773 表 = 生成出力", problems)',
    "F")

# --- B. 反転（区間ごと置き換え）---
# 773→772 に補正は無い。772 に blow_out の衝突は無い（772 に
# entity.puffer_fish.ambient が無いため）ので B の上書きは不要。
# chain→iron_chain の改名だけ rename で扱う。
NEW_B = '''    # --- B. *_773 表の反転と ITEM_REVERSE_773 が 773→772 の名前の真値と一致する ---
    # 773→772 に補正は無い。chain→iron_chain の改名は rename で扱う。
    defaults772 = {b["name"]: b["defaultState"]
                   for b in json.loads(
                       (data_dir / "1.21.8_blocks.json").read_text(encoding="utf-8"))}
    for kind in kinds:
        if kind == "items":
            ranges = parse_int3_table(tables773, "ITEM_REVERSE_773_RANGES")
            overrides = parse_int_table(tables773, "ITEM_REVERSE_773_OVERRIDES")
            up = expand(ranges, overrides, len(vv["1.21.9"]["items"]), 1)
        else:
            ranges = parse_int3_table(tables773, java_names[kind] + "_773_RANGES")
            overrides = parse_int_table(tables773, java_names[kind] + "_773_OVERRIDES")
            up = invert_java(ranges, overrides, len(vv["1.21.7"][kind]),
                             len(vv["1.21.9"][kind]), fb772[kind], [])
        truth = name_truth(
            vv["1.21.9"][kind], vv["1.21.7"][kind], kind, fb772[kind],
            rename={"iron_chain": "chain"},
            sound_fallbacks=SOUND_FALLBACK_773_TO_772 if kind == "sounds" else None,
            particle_fallbacks=None,
            item_fallbacks=None,
            block_fallbacks=None,
            target_defaults=defaults772 if kind == "blockstates" else None,
            drop_one_prop=(kind == "blockstates"))
        problems = []
        for i in range(len(vv["1.21.9"][kind])):
            if up[i] != truth[i]:
                problems.append(
                    f"{kind}[{i}] {vv['1.21.9'][kind][i]}: 反転={up[i]} "
                    f"真値={truth[i]}")
        check(f"B. {kind} 773→772 反転 = 名前の真値", problems)

'''
replace_section("    # --- B. *_772 表の反転", "    # --- C. 合成表", NEW_B, "B")

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
                               "sounds": 1417, "particles": 20, "blocks": 1, "menus": 12},
                    "1.21.6": {"blockstates": 1, "items": 1, "entities": 95,
                               "sounds": 1441, "particles": 20, "blocks": 1, "menus": 12}}
    for tag in ("763_TO_773", "773_TO_763", "764_TO_773", "773_TO_764",
                "765_TO_773", "773_TO_765", "766_TO_773", "773_TO_766",
                "767_TO_773", "773_TO_767", "768_TO_773", "773_TO_768",
                "769_TO_773", "773_TO_769", "770_TO_773", "773_TO_770",
                "771_TO_773", "773_TO_771"):
        for kind in kinds:
            ranges = parse_int3_table(
                tables773, java_names[kind] + "_" + tag + "_RANGES")
            overrides = parse_int_table(
                tables773, java_names[kind] + "_" + tag + "_OVERRIDES")
            if tag.endswith("773"):
                src_v = {"763": "1.20", "764": "1.20.2",
                         "765": "1.20.3", "766": "1.20.5",
                         "767": "1.21", "768": "1.21.2",
                         "769": "1.21.4", "770": "1.21.5",
                         "771": "1.21.6"}[tag[:3]]
                generic = fb773[kind]
            else:
                src_v = "1.21.9"
                generic = fb_by_target[{"763": "1.20", "764": "1.20.2",
                                        "765": "1.20.3", "766": "1.20.5",
                                        "767": "1.21", "768": "1.21.2",
                                        "769": "1.21.4", "770": "1.21.5",
                                        "771": "1.21.6"}[tag[7:10]]][kind]
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

# --- D. defaults に 1.21.9 を追加（773 の blocks は mc-data 1.21.9） ---
rep(
    '                     ("1.21.6", "1.21.6_blocks.json"),\n'
    '                     ("1.21.7", "1.21.8_blocks.json")):',
    '                     ("1.21.6", "1.21.6_blocks.json"),\n'
    '                     ("1.21.7", "1.21.8_blocks.json"),\n'
    '                     ("1.21.9", "1.21.9_blocks.json")):',
    "D defaults 1.21.9")

# --- D. 下向きの改名 dict に iron_chain→chain を追加 ---
rep(
    '    sound_down_rename = {\n'
    '        "turtle_scute": "scute", "short_grass": "grass",\n',
    '    sound_down_rename = {\n'
    '        "turtle_scute": "scute", "short_grass": "grass",\n'
    '        "iron_chain": "chain",\n',
    "sound_down_rename に iron_chain を追加")
rep(
    '    sound_down_rename_766_767 = {\n'
    '        "block.trial_spawner.ominous_activate":\n',
    '    sound_down_rename_766_767 = {\n'
    '        "iron_chain": "chain",\n'
    '        "block.trial_spawner.ominous_activate":\n',
    "sound_down_rename_766_767 に iron_chain を追加")

# --- D. pairs（区間ごと置き換え） ---
NEW_PAIRS = '''    pairs = [
        ("763_TO_773", "1.20", "1.21.9",
         {"grass": "short_grass", "scute": "turtle_scute",
          "chain": "iron_chain"},
         sound_up_rename,
         {"ambient_entity_effect": "entity_effect",
          "gust_emitter": "gust_emitter_large",
          "gust_dust": "small_gust"},
         {"trial_spawner": "spawner"}),
        ("773_TO_763", "1.21.9", "1.20",
         sound_down_rename,
         None, None,
         {"vault": "trial_spawner", "heavy_core": "stone"}),
        ("764_TO_773", "1.20.2", "1.21.9",
         {"grass": "short_grass", "scute": "turtle_scute",
          "chain": "iron_chain"},
         sound_up_rename,
         {"ambient_entity_effect": "entity_effect",
          "gust_emitter": "gust_emitter_large",
          "gust_dust": "small_gust"},
         {"trial_spawner": "spawner"}),
        ("773_TO_764", "1.21.9", "1.20.2",
         sound_down_rename,
         None, None,
         {"vault": "trial_spawner", "heavy_core": "stone"}),
        ("765_TO_773", "1.20.3", "1.21.9",
         {"grass": "short_grass", "scute": "turtle_scute",
          "chain": "iron_chain"},
         sound_up_rename,
         {"ambient_entity_effect": "entity_effect",
          "gust_emitter": "gust_emitter_large",
          "gust_dust": "small_gust"},
         {"trial_spawner": "spawner"}),
        ("773_TO_765", "1.21.9", "1.20.3",
         sound_down_rename,
         None, None,
         {"vault": "trial_spawner", "heavy_core": "stone"}),
        ("766_TO_773", "1.20.5", "1.21.9",
         {"chain": "iron_chain"}, sound_up_rename, {}, {}),
        ("773_TO_766", "1.21.9", "1.20.5",
         sound_down_rename_766_767,
         None, None,
         {"vault": "trial_spawner", "heavy_core": "stone"}),
        ("767_TO_773", "1.21", "1.21.9",
         {"chain": "iron_chain"}, sound_up_rename, {}, {}),
        ("773_TO_767", "1.21.9", "1.21",
         sound_down_rename_766_767,
         None, None,
         {"vault": "trial_spawner", "heavy_core": "stone"}),
        ("768_TO_773", "1.21.2", "1.21.9",
         {"chain": "iron_chain"}, sound_up_rename, {}, {}),
        ("773_TO_768", "1.21.9", "1.21.2",
         sound_down_rename_766_767,
         None, None, {}),
        ("769_TO_773", "1.21.4", "1.21.9",
         {"chain": "iron_chain"}, sound_up_rename, {}, {}),
        ("773_TO_769", "1.21.9", "1.21.4",
         sound_down_rename_766_767,
         None, None, {}),
        ("770_TO_773", "1.21.5", "1.21.9",
         {"chain": "iron_chain"}, sound_up_rename, {}, {}),
        ("773_TO_770", "1.21.9", "1.21.5",
         sound_down_rename_766_767,
         None, None, {}),
        ("771_TO_773", "1.21.6", "1.21.9",
         {"chain": "iron_chain"}, sound_up_rename, {}, {}),
        ("773_TO_771", "1.21.9", "1.21.6",
         sound_down_rename_766_767,
         None, None, {}),
    ]

'''
replace_section("    pairs = [\n", "    # D-down (b)", NEW_PAIRS, "D pairs")

# --- D(b) オラクル用 up772（区間ごと置き換え） ---
# 773→772 の反転に補正は無いため、772 の表の反転は補正なしで作る。
NEW_ORACLE = '''    # D-down (b) の合成オラクル用：772→相手は生成済みの合成表
    # （maps_composed_772.json）を使う。772→771 だけは合成表が無く
    # （771 が隣接元の版のため）、Java の *_772 表の反転で作る。
    # 772 の表が無ければ生成する（上の --target 772 の再生成と同じ）。
    up772 = {}
    for kind in kinds:
        ranges = parse_int3_table(tables772, java_names[kind] + "_772_RANGES")
        overrides = parse_int_table(tables772, java_names[kind] + "_772_OVERRIDES")
        up772[kind] = invert_java(ranges, overrides, len(vv["1.21.6"][kind]),
                                  len(vv["1.21.7"][kind]), fb771[kind], [])

'''
replace_section("    # D-down (b)", "    for tag, src_v, dst_v, item_rename",
                NEW_ORACLE, "D oracle setup")

# --- D ループ内の小さいずれ ---
rep('        down = tag.startswith("772")',
    '        down = tag.startswith("773")', "D down フラグ")
rep(
    '            ranges = parse_int3_table(\n'
    '                tables772, java_names[kind] + "_" + tag + "_RANGES")\n'
    '            overrides = parse_int_table(\n'
    '                tables772, java_names[kind] + "_" + tag + "_OVERRIDES")',
    '            ranges = parse_int3_table(\n'
    '                tables773, java_names[kind] + "_" + tag + "_RANGES")\n'
    '            overrides = parse_int_table(\n'
    '                tables773, java_names[kind] + "_" + tag + "_OVERRIDES")',
    "D 合成表の parse")
rep(
    '            generic = (fb772[kind] if tag.endswith("772")\n'
    '                       else fb_by_target[dst_v][kind])',
    '            generic = (fb773[kind] if tag.endswith("773")\n'
    '                       else fb_by_target[dst_v][kind])',
    "D generic")
rep(
    '                        # 768 の creaking_transient は 769 で削除され、\n'
    '                        # 生成器は creaking に落とす（generate-remap-tables.py\n'
    '                        # の 769 の改名表）。772 でも creaking のまま。',
    '                        # 768 の creaking_transient は 769 で削除され、\n'
    '                        # 生成器は creaking に落とす（generate-remap-tables.py\n'
    '                        # の 769 の改名表）。773 でも creaking のまま。',
    "D creaking_transient コメント")
rep(
    '                    sound_fallbacks=SOUND_FALLBACK_OLD_TO_772\n'
    '                    if kind == "sounds" else None,',
    '                    sound_fallbacks=SOUND_FALLBACK_OLD_TO_773\n'
    '                    if kind == "sounds" else None,',
    "D sound_fallbacks 上向き")
rep_all("→ 772 の真値", "→ 773 の真値", "D creaking_heart 真値", 2)
rep_all("（771 の反転と同じ決め方）", "（772 の反転と同じ決め方）",
        "D creaking_heart 補正コメント", 2)
rep_all("idx772", "idx773", "D idx773（G節の2件も含む）", 6)
rep_all('vv["1.21.7"]["blockstates"]', 'vv["1.21.9"]["blockstates"]',
        "D creaking_heart の対象版", 2)
rep(
    '                sound_fallbacks=SOUND_FALLBACK_772_TO_TARGET if kind == "sounds" else None,',
    '                sound_fallbacks=SOUND_FALLBACK_773_TO_TARGET if kind == "sounds" else None,',
    "D sound_fallbacks 下向き")
rep(
    '                # 772 の creaking_heart → 768 の真値（768 は creaking',
    '                # 773 の creaking_heart → 768 の真値（768 は creaking',
    "D creaking_heart 下向き 768")
rep(
    '                # 772 の creaking_heart → 769 の真値（769 は active＋axis＋',
    '                # 773 の creaking_heart → 769 の真値（769 は active＋axis＋',
    "D creaking_heart 下向き 769")
rep(
    '                # 772 の blow_out は 770 の ambient と blow_out の両方から',
    '                # 773 の blow_out は 770 の ambient と blow_out の両方から',
    "D blow_out 下向き")
# blow_out の上書きは ambient がある相手（770 以前）にだけ有効。
# 771 には ambient が無いため、773→771 では blow_out のまま。
rep(
    '            if kind == "sounds":\n'
    '                # 773 の blow_out は 770 の ambient と blow_out の両方から\n'
    '                # 写る（衝突）。invert() は先行勝ちで ambient を選ぶため、\n'
    '                # 合成表も相手の ambient になる。\n'
    '                idx_dst = {n: i for i, n in enumerate(vv[dst_v]["sounds"])}\n'
    '                truth[vv[src_v]["sounds"].index("entity.puffer_fish.blow_out")] = \\\n'
    '                    idx_dst["entity.puffer_fish.ambient"]\n',
    '            if kind == "sounds" and "entity.puffer_fish.ambient" in vv[dst_v]["sounds"]:\n'
    '                # 773 の blow_out は 770 の ambient と blow_out の両方から\n'
    '                # 写る（衝突）。invert() は先行勝ちで ambient を選ぶため、\n'
    '                # 合成表も相手の ambient になる。771 には ambient が無いため\n'
    '                # 773→771 では blow_out のまま（上書きしない）。\n'
    '                idx_dst = {n: i for i, n in enumerate(vv[dst_v]["sounds"])}\n'
    '                truth[vv[src_v]["sounds"].index("entity.puffer_fish.blow_out")] = \\\n'
    '                    idx_dst["entity.puffer_fish.ambient"]\n',
    "D blow_out の上書きは 771 を除く")

# --- D(b) オラクル部分（区間ごと置き換え） ---
NEW_DB = '''            # 下向き (b)：合成表全体が「773→772 の反転 × 772→相手の表」
            # と一致するはず。代替先の決め方は 772→相手の表に委ねる。
            # 772→771 だけ合成表が無いため up772（772→771 の反転）を使う。
            if dst_v == "1.21.6":
                oracle_map = up772[kind]
            else:
                tag2 = "772_TO_" + tag[7:10]
                oracle_raw = {int(k): v for k, v in maps_comp_772[tag2][kind].items()}
                oracle_map = [oracle_raw[i] for i in range(len(vv["1.21.7"][kind]))]
            if kind == "items":
                r = parse_int3_table(tables773, "ITEM_REVERSE_773_RANGES")
                o = parse_int_table(tables773, "ITEM_REVERSE_773_OVERRIDES")
                up773 = expand(r, o, len(vv["1.21.9"]["items"]), 1)
            else:
                r = parse_int3_table(tables773, java_names[kind] + "_773_RANGES")
                o = parse_int_table(tables773, java_names[kind] + "_773_OVERRIDES")
                up773 = invert_java(r, o, len(vv["1.21.7"][kind]),
                                    len(vv["1.21.9"][kind]), fb772[kind], [])
            problems = []
            for i in range(size):
                expected = oracle_map[up773[i]]
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
    # --target 773 を再実行し、Java ファイル内の表と完全一致する
    # （生成スクリプトをコピーした直後の転記ミスの検出）。
    tmp = data_dir / "verify-tmp-773.txt"
    proc = subprocess.run(
        [sys.executable, str(SCRIPT_DIR / "generate-remap-tables.py"),
         "--target", "773", "--out", str(tmp)],
        capture_output=True, text=True, timeout=300)
    if proc.returncode != 0:
        print("[FAIL] E. 生成スクリプト --target 773 が失敗した")
        print(proc.stderr[-2000:])
        failures.append("E-773")
    else:
        gen_re = tmp.read_text(encoding="utf-8")
        problems = []
        for m in re.finditer(
                r"^(?:public static final int\\[\\]\\[\\] )?([A-Z0-9_]+) = "
                r"compress\\(new int\\[\\]\\[\\]\\{(.*?)\\}\\);",
                gen_re, re.MULTILINE | re.DOTALL):
            name = m.group(1)
            # ATTRIBUTE_772_NAMES は RegistryTables772.java にあるため
            # 773 側には入れない（insert-773-tables.py と同じ）。
            if name == "ATTRIBUTE_772_NAMES":
                continue
            m2 = re.search(re.escape(name)
                           + r" = \\{(.*?)\\n    \\};", tables773, re.DOTALL)
            if not m2:
                problems.append(f"{name} が RegistryTables773*.java に無い")
        check("E. 生成スクリプト --target 773 の再現性", problems)

'''
replace_section("    # --- E. 生成スクリプト", "    # --- G.", NEW_E, "E")

# --- G. 属性・エンチャント（区間ごと置き換え） ---
NEW_G = '''    # --- G. 属性・エンチャントの表 ---
    for name, size, fb in (("ATTRIBUTE_773", 35, -1),
                           ("ATTRIBUTE_REVERSE_773", 35, -1),
                           ("ENCHANTMENT_773", 42, -1),
                           ("ENCHANTMENT_REVERSE_773", 42, -1)):
        ranges = parse_int3_table(tables773, name + "_RANGES")
        overrides = parse_int_table(tables773, name + "_OVERRIDES")
        expanded = expand(ranges, overrides, size, fb)
        gm_ranges = re.search(r"int\\[\\]\\[\\] " + re.escape(name) + r"_RANGES = \\{(.*?)\\n    \\};",
                              gen773, re.DOTALL)
        gm_overrides = re.search(r"int\\[\\]\\[\\] " + re.escape(name) + r"_OVERRIDES = \\{(.*?)\\n    \\};",
                                 gen773, re.DOTALL)
        if not gm_ranges or not gm_overrides:
            check(f"G. {name} 表 = 生成出力", [f"{name} が gen773.txt に無い"])
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
    mc772 = json.loads((data_dir / "mc-1.21.8-protocol.json").read_text(encoding="utf-8"))
    mc773 = json.loads((data_dir / "mc-1.21.9-protocol.json").read_text(encoding="utf-8"))
    for name, proto, source in (("ATTRIBUTE_772_NAMES", mc772, tables772),
                                ("ATTRIBUTE_773_NAMES", mc773, tables773)):
        java_names_list = parse_string_array(source, name)
        truth = attribute_names(proto)
        problems = [f"{name}[{i}] Java={java_names_list[i]} 真値={truth[i]}"
                    for i in range(len(truth))
                    if i >= len(java_names_list) or java_names_list[i] != truth[i]]
        if len(java_names_list) != len(truth):
            problems.append(f"{name} の件数が違う：Java={len(java_names_list)} 真値={len(truth)}")
        check(f"G. {name} = protocol.json", problems)
    # エンチャントの表 = minecraft-data の enchantments-*.json の並び。
    ench773 = [e["name"] for e in json.loads(
        (data_dir / "enchantments-1.21.9.json").read_text(encoding="utf-8"))]
    idx773 = {n: i for i, n in enumerate(ench773)}
    # 1.21.9 の enchantments は dataPaths.json により pc/1.21.9 を使う
    # （1.21.8 の 42 件と同じ名前・同じ並び）。
    for name, src in (("ENCHANTMENT_773", ench773),
                      ("ENCHANTMENT_REVERSE_773", ench773)):
        ranges = parse_int3_table(tables773, name + "_RANGES")
        overrides = parse_int_table(tables773, name + "_OVERRIDES")
        expanded = expand(ranges, overrides, len(src), -1)
        problems = []
        for i, n in enumerate(src):
            expected = idx773.get(n, -1)
            if expanded[i] != expected:
                problems.append(f"{name}[{i}] {n}: Java={expanded[i]} 真値={expected}")
        check(f"G. {name} = enchantments-*.json の並び", problems)

'''
replace_section("    # --- G.", "    # --- H.", NEW_G, "G")

# --- H. 音の名前の表（区間ごと置き換え） ---
NEW_H = '''    # --- H. 音の名前の表が数値の表と一致する ---
    sound_name_tables = {
        "SOUND_NAME_FALLBACKS_773_TO_771": ("1.21.9", "1.21.6", "773_TO_771"),
        "SOUND_NAME_FALLBACKS_773_TO_770": ("1.21.9", "1.21.5", "773_TO_770"),
        "SOUND_NAME_FALLBACKS_773_TO_769": ("1.21.9", "1.21.4", "773_TO_769"),
        "SOUND_NAME_FALLBACKS_773_TO_768": ("1.21.9", "1.21.2", "773_TO_768"),
        "SOUND_NAME_FALLBACKS_773_TO_767": ("1.21.9", "1.21", "773_TO_767"),
        "SOUND_NAME_FALLBACKS_773_TO_765": ("1.21.9", "1.20.3", "773_TO_765"),
        "SOUND_NAME_FALLBACKS_773_TO_763": ("1.21.9", "1.20", "773_TO_763"),
        "SOUND_NAME_FALLBACKS_773_TO_764": ("1.21.9", "1.20.2", "773_TO_764"),
        "SOUND_NAME_FALLBACKS_773_TO_766": ("1.21.9", "1.20.5", "773_TO_766"),
    }
    for name, (src_v, dst_v, tag) in sound_name_tables.items():
        pairs = parse_string_table(tables773, name)
        # 773→771 も合成表がある（771 は隣接元ではない）。772 のときの
        # 直接反転の特別処理は要らない。
        ranges = parse_int3_table(tables773, "SOUND_" + tag + "_RANGES")
        overrides = parse_int_table(tables773, "SOUND_" + tag + "_OVERRIDES")
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
    # DAMAGE_TYPE_773_MAP／REVERSE_773_MAP は生成スクリプトの出力では
    # なく insert-773-java.py の手書きのため、ここで独立に検証する。
    # 773 のダメージ種別は 771 と同じ 49 種で、並びも同じはず
    # （実バニラの Registry Data での確認は統合テストで行う）。
    # まだ insert-773-java.py が走っていない場合は検証を飛ばす。
    if "DAMAGE_TYPE_773_MAP" not in java:
        print("[SKIP] I. ダメージ種別の表（insert-773-java.py 未実行）")
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

            fwd = parse_int_array(java, "DAMAGE_TYPE_773_MAP")
            problems = []
            if len(fwd) != len(names768):
                problems.append(f"DAMAGE_TYPE_773_MAP の件数が違う：{len(fwd)} != {len(names768)}")
            for i in range(len(fwd)):
                if fwd[i] != i:
                    problems.append(f"772[{i}] {names768[i]}: Java={fwd[i]} 真値={i}")
            check("I. DAMAGE_TYPE_773_MAP = 771 と同じ並び", problems)
            rev = parse_int_array(java, "DAMAGE_TYPE_REVERSE_773_MAP")
            problems = []
            if len(rev) != len(names768):
                problems.append(f"DAMAGE_TYPE_REVERSE_773_MAP の件数が違う：{len(rev)} != {len(names768)}")
            for i in range(len(rev)):
                if rev[i] != i:
                    problems.append(f"773[{i}] {names768[i]}: Java={rev[i]} 真値={i}")
            check("I. DAMAGE_TYPE_REVERSE_773_MAP = 771 と同じ並び", problems)

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
