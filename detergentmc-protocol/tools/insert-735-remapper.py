#!/usr/bin/env python3
"""RegistryRemapper.java に 735（1.16）対応を差し込む。

雛形は tools/insert-736-remapper.py（隣接連鎖の配線＋書き込み前の
全入力検証・冪等ガード）。アンカー付き挿入のみ行う。アンカーが
見つからなければ失敗して何も書かない。
使い方: python3 insert-735-remapper.py
書き換えた後は必ず git diff で内容を確認してから javac で確認すること。

配線の方針（STAGE-A-735-NOTES.md §11.8 の申し送り）:
- 735 は合成表を作らない。735↔763〜775 は 735→736→751→753→754→755→756→757→759→760→
  761→762→763→合成表の連鎖で到達する（736〜762 と同じ隣接連鎖グループ）。
- 順向き（736→735）の展開済み表は RegistryTables735 の *_735_* 表を
  expand() で展開する。ブロック状態の下向き表（BLOCK_STATE_735_RANGES・
  BLOCK_STATE_735_OVERRIDES）だけは別クラス RegistryTables735BlockState
  に分けてある（<clinit> 64KB 対策。773／774／775 の *BlockState.java
  と同じ方式。import も 2 行になる）。
- 反転（735→736）の表は invert() で作る。補正は maps735-corrections.json
  から：全種 0 件（736 と 735 のレジストリ内容は同一のため、invert() の
  first-wins と名前の真値が食い違う箇所は無い。アイテムは invert()＋補正でも
  名前の真値と一致するが、766／777／736 の前例にならい、名前から作った
  既存の逆変換表（ITEM_REVERSE_735）を expand() でそのまま使う。
- エフェクトは表を作らずハードコードする（mapEffectUp774 の前例）。
  735 と 736 は 32件・同一順序のため恒等写像（DROP なし）。
- ブロックエンティティも表を作らない。735 は 33 件・736 も 33 件で
  同一順序のため恒等写像（DROP なし。736 と違い sculk_sensor のような
  片方だけの種別は無い）。
- ダメージ種別は 42 件の恒等写像のプレースホルダ（DAMAGE_TYPE_735_MAP／
  DAMAGE_TYPE_REVERSE_735_MAP。736 の DAMAGE_TYPE_736 と同じ扱い。
  1.16／1.16.1 のどちらにも damage_type レジストリは無いため、
  ワイヤ上の呼び出し元は無い）。
- 735 の展開済み表は private static final class Maps735 にまとめる。
  RegistryRemapper の static 初期化子は 64KB 制限に達しているため
  （736 追加後も外側は 64,669 バイト。残り 866 バイト）、735 の表を外側の
  static 初期化子に入れると code too large でコンパイルできない。
  735 以降も同じ方式にすること。
- バイオーム：順向き表（BIOME_735）は 736 側 174 件（id 0〜173）、反転表
  （BIOME_735_TO_736）は 735 側 174 件（id 0〜173。隙間の id は平原）。
  1.16 の 79 件はすべて 1.16.1 と同じ id・同じ名前。改名は無い。
  新規バイオームは無いため、順向き・反転とも隙間の id だけが平原（1）へ落ちる。
  反転の補正は RegistryTables735.java の BIOME_735_TO_736_* 表との差分から
  このスクリプトが機械的に作る（736 と同じ仕組み。0 件の見込み）。

書き込み前の検証（失敗したら何も書き込まない）:
- maps735-corrections.json の件数（全種 0 件）。各対が範囲内であることも確認する。
- RegistryTables735.java から読んだ表で、Java の expand()／invert() と
  同じ計算を Python で行い、invert(順向き)＋補正＝名前の真値
  （*_735_TO_736_* 表の展開）と全 7 種で一致することを確認する。
  アイテムは invert()＋補正＝ITEM_REVERSE_735 の展開とも一致することを確認する。
  バイオームは invert() と名前の真値の差分を補正として作り、
  invert(順向き)＋補正＝名前の真値（174 件）を確認する。
  パーティクルの invert() の fallback は wiring と同じ
  AMBIENT_PARTICLE_736（0）を使う（生成スクリプトは entity_effect
  （20）を使っているが、補正を当てた後は -1 が残らないため値は同じ。
  736 の前例と同じ扱い）。
- ダメージ種別・エンチャント・属性の展開結果の件数と代表値を
  確認する。735 側の代替先アンカー（pig＝59、block.stone.break＝809、
  ambient_entity_effect＝0、crafting＝11、stone＝1、plains＝1）を
  vv-1.16.json から実測で確認する。
- ディスパッチの連鎖文字列は手書きする。書き込み前に現在の
  RegistryRemapper.java の 736 分岐の式を読み、735 の連鎖が
  「736 分岐の外側に 736→735 段を 1 つ巻いたもの」（mapItemReverse
  だけは「736 分岐の最も内側に 735→736 段を 1 つ入れたもの」）と
  一致することを確認する。機械変換では段が 1 つ消える罠があるため
  （753 §12.5 参照）、手書きした文字列とファイルの 736 分岐を
  突き合わせてから書く。
"""
from pathlib import Path
import json
import re
import sys

ROOT = Path(__file__).resolve().parent.parent
REMAPPER = ROOT / "src/main/java/io/detergentmc/protocol/RegistryRemapper.java"
TABLES = ROOT / "src/main/java/io/detergentmc/protocol/RegistryTables735.java"
# ブロック状態の下向き表だけ別クラスに分けてある（<clinit> 64KB 対策。
# 773／774／775 の *BlockState.java と同じ方式）。検証は両ファイルの
# 表を合わせて読む。
TABLES_BLOCKSTATE = ROOT / "src/main/java/io/detergentmc/protocol/RegistryTables735BlockState.java"
CORRECTIONS_JSON = ROOT / "build/remap-data/maps735-corrections.json"
VV1161 = ROOT / "build/remap-data/vv-1.16.json"
BIOMES116 = ROOT / "build/remap-data/biomes-1.16.json"
BIOMES1161 = ROOT / "build/remap-data/biomes-1.16.1.json"

# 736側の最大ID（RegistryRemapper.java の MAX_*_736 定数と照合する）。
MAX_736 = {
    "blockstates": 17103, "items": 974, "entities": 106, "sounds": 984,
    "particles": 71, "blocks": 762, "menus": 23, "biomes": 173,
}
# 735側の最大ID（vv-1.16.json 等の実測件数−1。バイオームだけ id の最大値 173）。
MAX_735 = {
    "blockstates": 17103, "items": 974, "entities": 106, "sounds": 984,
    "particles": 71, "blocks": 762, "menus": 23, "biomes": 173,
}
# 736側の代替先（invert() の fallback。gen-735-tables.py の UP_735_FALLBACKS
# と同じ。ただしパーティクルだけ wiring では AMBIENT_PARTICLE_736（0）
# を使う。生成スクリプトの entity_effect（20）とは違うが、補正を当てた
# 後は -1 が残らないため結果は同じ。736 の前例と同じ扱い）。
FALLBACK_736 = {
    "blockstates": 1, "items": 1, "entities": 59, "sounds": 809,
    "particles": 0, "blocks": 1, "menus": 11, "biomes": 1,
}
# 735側の代替先（expand() の fallback。vv-1.16.json で実測した値。
# 音は両側とも同じ：block.stone.break は 735 でも 736 でも 809。
# （751 側は piglin_brute 系 7 音の分だけ後ろの 816 になるが、735／736 には無い）。
FALLBACK_735 = {
    "blockstates": 1, "items": 1, "entities": 59, "sounds": 809,
    "particles": 0, "blocks": 1, "menus": 11, "biomes": 1,
}
# 表名（RegistryTables735.java）。down は 736→735、up は 735→736。
KINDS = ["blockstates", "items", "entities", "sounds", "particles", "blocks", "menus"]
TABLE_PREFIX = {
    "blockstates": "BLOCK_STATE", "items": "ITEM", "entities": "ENTITY_TYPE",
    "sounds": "SOUND", "particles": "PARTICLE", "blocks": "BLOCK", "menus": "MENU",
}


def expand(ranges, overrides, size, fallback):
    """RegistryRemapper.expand() と同じ計算。"""
    out = [fallback] * size
    for lo, hi, delta in ranges:
        for i in range(lo, hi + 1):
            if i < size:
                out[i] = i + delta
    for x, y in overrides:
        if 0 <= x < size:
            out[x] = y
    return out


def invert(ranges, overrides, from_size, to_size, fallback, corrections):
    """RegistryRemapper.invert() と同じ計算。"""
    inverse = [-1] * to_size
    for lo, hi, delta in ranges:
        for x in range(lo, hi + 1):
            if x < from_size:
                y = x + delta
                if 0 <= y < to_size and inverse[y] < 0:
                    inverse[y] = x
    for x, y in overrides:
        if 0 <= x < from_size and 0 <= y < to_size and inverse[y] < 0:
            inverse[y] = x
    if corrections is not None:
        for to_id, from_id in corrections:
            if 0 <= to_id < to_size and 0 <= from_id < from_size:
                inverse[to_id] = from_id
    for i in range(to_size):
        if inverse[i] < 0:
            inverse[i] = fallback
    return inverse


def parse_int_table2(text, name):
    """{a, b} または {a, b, c} の対・三つ組を読む。"""
    m = re.search(r"static final int\[\]\[\] " + re.escape(name) + r" = \{(.*?)\n    \};",
                  text, re.DOTALL)
    if m is None:
        raise SystemExit(f"表が見つからない: {name}")
    out = []
    for tup in re.findall(r"\{(-?\d+), (-?\d+)(?:, (-?\d+))?\}", m.group(1)):
        vals = [int(v) for v in tup if v != ""]
        out.append(tuple(vals))
    return out


def java_int2_array(pairs, per_line=8):
    """int[][] の補正を Java のリテラルにする（1行に8対まで）。"""
    lines = []
    for i in range(0, len(pairs), per_line):
        chunk = pairs[i:i + per_line]
        lines.append("            " + ", ".join("{%d, %d}" % (a, b) for a, b in chunk) + ",")
    return "\n".join(lines)


def read_dispatch_branch(src, method_name, prefix):
    """dispatch メソッドの 736 分岐の return 式を読む（検証用）。
    735 分岐の挿入後（736 分岐が 2 番目以降）に再実行しても読めるよう、
    メソッド冒頭にアンカーせずファイル全体から 736 分岐を探す。
    prefix は return 式の先頭（例：mapBlock753To736(）。mapBlock と
    mapBlockState のように名前が接頭辞の関係にあるメソッドを区別するため、
    メソッド名だけでなく 736 への隣接段まで含める。"""
    m = re.search(
        r"if \(clientProtocol == PROTOCOL_736\) \{\n"
        r"            return (" + re.escape(prefix) + r".*?);\n"
        r"        \}",
        src, re.DOTALL)
    if m is None:
        raise SystemExit(f"dispatch の 736 分岐が見つからない: {method_name}")
    return m.group(1)


def main():
    tables_text = (TABLES.read_text(encoding="utf-8") + "\n"
                   + TABLES_BLOCKSTATE.read_text(encoding="utf-8"))
    corrections = json.loads(CORRECTIONS_JSON.read_text(encoding="utf-8"))
    remapper_text = REMAPPER.read_text(encoding="utf-8")

    # --- 検証0：既存の MAX_*_736 定数が想定値と一致する ---
    const_name = {
        "blockstates": "MAX_BLOCK_STATE_736", "items": "MAX_ITEM_736",
        "entities": "MAX_ENTITY_TYPE_736", "sounds": "MAX_SOUND_736",
        "particles": "MAX_PARTICLE_736", "blocks": "MAX_BLOCK_736",
        "menus": "MAX_MENU_736", "biomes": "MAX_BIOME_736",
    }
    for kind, expected in MAX_736.items():
        m = re.search(r"public static final int " + const_name[kind] + r" = (\d+);",
                      remapper_text)
        if m is None:
            raise SystemExit(f"既存の定数が見つからない: {const_name[kind]}")
        if int(m.group(1)) != expected:
            raise SystemExit(f"既存の {const_name[kind]} が違う：{m.group(1)} != {expected}")
    print("既存の MAX_*_736 定数を確認（8 件）")

    # --- 検証1：補正 JSON の件数（全種 0 件）---
    expected_counts = {"blockstates": 0, "items": 0, "entities": 0, "sounds": 0,
                       "particles": 0, "blocks": 0, "menus": 0}
    for kind, expected in expected_counts.items():
        got = corrections.get(kind)
        if got is None:
            raise SystemExit(f"補正 JSON に {kind} が無い")
        if len(got) != expected:
            raise SystemExit(f"補正 {kind} の件数が違う：{len(got)} != {expected}")
        for to_id, from_id in got:
            if not (0 <= to_id <= MAX_735[kind] and 0 <= from_id <= MAX_736[kind]):
                raise SystemExit(f"補正 {kind} の範囲外の対：[{to_id}, {from_id}]")
    print("補正 JSON の件数を確認：全種 0 件")

    # --- 検証2：invert(順向き)＋補正＝名前の真値（*_735_TO_736_* 表の展開）---
    for kind in KINDS:
        prefix = TABLE_PREFIX[kind]
        down_ranges = parse_int_table2(tables_text, prefix + "_735_RANGES")
        down_overrides = parse_int_table2(tables_text, prefix + "_735_OVERRIDES")
        truth_ranges = parse_int_table2(tables_text, prefix + "_735_TO_736_RANGES")
        truth_overrides = parse_int_table2(tables_text, prefix + "_735_TO_736_OVERRIDES")
        truth = expand(truth_ranges, truth_overrides, MAX_735[kind] + 1, FALLBACK_736[kind])
        corr = corrections[kind] if corrections[kind] else None
        got = invert(down_ranges, down_overrides,
                     MAX_736[kind] + 1, MAX_735[kind] + 1, FALLBACK_736[kind], corr)
        if got != truth:
            diffs = [i for i, (a, b) in enumerate(zip(got, truth)) if a != b]
            raise SystemExit(f"invert＋補正が名前の真値と違う（{kind}、{len(diffs)} 件）")
        print(f"invert＋補正＝名前の真値：{kind}（{len(truth)} 件）")
    # アイテムは invert()＋補正＝ITEM_REVERSE_735 の展開とも一致することを確認する。
    rev_ranges = parse_int_table2(tables_text, "ITEM_REVERSE_735_RANGES")
    rev_overrides = parse_int_table2(tables_text, "ITEM_REVERSE_735_OVERRIDES")
    rev = expand(rev_ranges, rev_overrides, MAX_735["items"] + 1, FALLBACK_736["items"])
    item_ranges = parse_int_table2(tables_text, "ITEM_735_RANGES")
    item_overrides = parse_int_table2(tables_text, "ITEM_735_OVERRIDES")
    item_inv = invert(item_ranges, item_overrides,
                      MAX_736["items"] + 1, MAX_735["items"] + 1,
                      FALLBACK_736["items"], corrections["items"])
    if item_inv != rev:
        raise SystemExit("invert(アイテム)＋補正が ITEM_REVERSE_735 と違う")
    print("invert(アイテム)＋補正＝ITEM_REVERSE_735 の展開（975 件）")

    # --- 検証3：バイオームは invert(補正あり)＝名前の真値 ---
    # 1.16 の 79 件はすべて 1.16.1 と同じ id・同じ名前。改名は無い。
    # 新規バイオームは無いため、下向きで平原（1）へ落ちるのは隙間の id だけ。
    # 反転で食い違うのは 0 件の見込みだが、736 と同じく
    # 差分から機械的に補正を作る（手入力しない）。
    bio_ranges = parse_int_table2(tables_text, "BIOME_735_RANGES")
    bio_overrides = parse_int_table2(tables_text, "BIOME_735_OVERRIDES")
    bio_truth = expand(parse_int_table2(tables_text, "BIOME_735_TO_736_RANGES"),
                       parse_int_table2(tables_text, "BIOME_735_TO_736_OVERRIDES"),
                       MAX_735["biomes"] + 1, FALLBACK_736["biomes"])
    bio_inv = invert(bio_ranges, bio_overrides,
                     MAX_736["biomes"] + 1, MAX_735["biomes"] + 1, FALLBACK_736["biomes"], None)
    bio_diffs = [(i, bio_truth[i]) for i in range(len(bio_truth)) if bio_inv[i] != bio_truth[i]]
    if len(bio_truth) != 174:
        raise SystemExit(f"バイオームの反転表が 174 件でない：{len(bio_truth)}")
    bio_inv_fixed = invert(bio_ranges, bio_overrides,
                           MAX_736["biomes"] + 1, MAX_735["biomes"] + 1,
                           FALLBACK_736["biomes"], bio_diffs)
    if bio_inv_fixed != bio_truth:
        raise SystemExit("invert(バイオーム)＋補正が名前の真値と違う")
    # 代表値：biomes-1.16.json から id を引いて恒等写像を確認する。
    # the_void・snowy_tundra・snowy_mountains・plains・mountains は
    # 順向き・反転とも恒等写像。隙間の先頭 id 51 は平原（1）へ。
    # （meadow は 1.18 の新規バイオームで 1.16 には無いため代表値に入れない）
    # 735 側（biomes-1.16.json）と 736 側（biomes-1.16.json）の
    # id→名前が一致することも確認する。
    biomes735 = json.loads(BIOMES116.read_text(encoding="utf-8"))
    biomes736 = json.loads(BIOMES1161.read_text(encoding="utf-8"))
    if {e["id"]: e["name"] for e in biomes735} != {e["id"]: e["name"] for e in biomes736}:
        raise SystemExit("バイオームの id→名前が 735 側と 736 側で違う")
    bio_id = {e["name"]: e["id"] for e in biomes735}
    gaps = [i for i in range(174) if i not in set(bio_id.values())]
    bio_down = expand(bio_ranges, bio_overrides, MAX_736["biomes"] + 1, FALLBACK_735["biomes"])
    checks = []
    for name in ("the_void", "snowy_tundra", "snowy_mountains", "plains", "mountains"):
        bid = bio_id[name]
        checks.append((bio_down[bid], bid))
        checks.append((bio_truth[bid], bid))
    checks.append((bio_down[gaps[0]], 1))
    checks.append((bio_truth[gaps[0]], 1))
    if any(got != want for got, want in checks):
        raise SystemExit(f"バイオームの真値がおかしい：{checks}")
    print(f"invert(バイオーム)＋補正＝名前の真値（174 件。補正 {len(bio_diffs)} 件・"
          f"代表値 {len(checks)} 件を確認。隙間の先頭 id {gaps[0]}→平原）")

    # --- 検証4：ダメージ種別・エンチャント・属性の展開 ---
    dmg = expand(parse_int_table2(tables_text, "DAMAGE_TYPE_735_RANGES"),
                 parse_int_table2(tables_text, "DAMAGE_TYPE_735_OVERRIDES"), 42, 16)
    dmg_rev = expand(parse_int_table2(tables_text, "DAMAGE_TYPE_735_TO_736_RANGES"),
                     parse_int_table2(tables_text, "DAMAGE_TYPE_735_TO_736_OVERRIDES"), 42, 16)
    if dmg != list(range(42)) or dmg_rev != list(range(42)):
        raise SystemExit("ダメージ種別が 42 件の恒等写像ではない")
    enc = expand(parse_int_table2(tables_text, "ENCHANTMENT_735_RANGES"),
                 parse_int_table2(tables_text, "ENCHANTMENT_735_OVERRIDES"), 38, -1)
    enc_rev = expand(parse_int_table2(tables_text, "ENCHANTMENT_REVERSE_735_RANGES"),
                     parse_int_table2(tables_text, "ENCHANTMENT_REVERSE_735_OVERRIDES"), 38, -1)
    if len(enc) != 38 or enc != list(range(38)):
        raise SystemExit(f"エンチャントの展開がおかしい：{enc[:10]}")
    if len(enc_rev) != 38 or enc_rev != list(range(38)):
        raise SystemExit(f"エンチャント逆向きの展開がおかしい：{enc_rev[:10]}")
    attr = expand(parse_int_table2(tables_text, "ATTRIBUTE_735_RANGES"),
                  parse_int_table2(tables_text, "ATTRIBUTE_735_OVERRIDES"), 13, -1)
    if attr != list(range(13)):
        raise SystemExit("属性が 13 件の恒等写像ではない")
    print("ダメージ種別 42 件恒等・エンチャント 38/38 件恒等・属性 13 件恒等を確認")

    # --- 検証5：735 側の代替先アンカーを vv-1.16.json から実測で確認 ---
    vv1161 = json.loads(VV1161.read_text(encoding="utf-8"))
    anchors = [
        (vv1161["blockstates"][1], "stone"), (vv1161["items"][1], "stone"),
        (vv1161["entities"][59], "pig"),
        (vv1161["sounds"][809], "block.stone.break"),
        (vv1161["particles"][0], "ambient_entity_effect"),
        (vv1161["blocks"][1], "stone"), (vv1161["menus"][11], "crafting"),
    ]
    if any(got != want for got, want in anchors):
        raise SystemExit(f"735 側の代替先アンカーが違う：{anchors}")
    print("735 側の代替先アンカーを確認（stone 1・pig 59・block.stone.break 809・"
          "ambient 0・crafting 11・plains 1）")

    # --- 補正の Java テキストを生成する ---
    blockstate_corr_java = java_int2_array([tuple(p) for p in corrections["blockstates"]])
    sound_corr_java = java_int2_array([tuple(p) for p in corrections["sounds"]])
    # バイオームの補正は上の検証3で差分から作ったもの（手入力しない）。
    biome_corr_java = java_int2_array(bio_diffs)

    # --- 検証6：ディスパッチの連鎖文字列（手書き）とファイルの 736 分岐を突き合わせる ---
    # chain_down（8 メソッド）と mapSoundName：735 の連鎖は 736 分岐の
    # 外側に 736→735 段を 1 つ巻いたもの。mapEffect も同じ。
    # mapItemReverse だけは上向きのため、736 分岐の最も内側に
    # 735→736 段を 1 つ入れたもの。
    # 括弧の数も数える（手書きの連鎖は開き＝閉じでなければならない）。
    # 736 分岐の式は手書きする（ファイルの 736 分岐と突き合わせる）。
    # sed の一括置換で作ると 736→735 段が落ちる罠があるため（736 §12.1 の1）、
    # ここは一括置換の結果を使わず手で全形を書く。内側の 753 段以降は
    # 736 分岐そのまま（754To753・755To754・…・763To762）。
    chain_down = {
        "mapBlockState": ("mapBlockState736To735",
                          "mapBlockState751To736(mapBlockState753To751(mapBlockState754To753(mapBlockState755To754(mapBlockState756To755(mapBlockState757To756(mapBlockState759To757(mapBlockState760To759(mapBlockState761To760(mapBlockState762To761(mapBlockState763To762(mapBlockState(id765))))))))))))"),
        "mapItem": ("mapItem736To735",
                          "mapItem751To736(mapItem753To751(mapItem754To753(mapItem755To754(mapItem756To755(mapItem757To756(mapItem759To757(mapItem760To759(mapItem761To760(mapItem762To761(mapItem763To762(mapItem(id765))))))))))))"),
        "mapEntityType": ("mapEntityType736To735",
                          "mapEntityType751To736(mapEntityType753To751(mapEntityType754To753(mapEntityType755To754(mapEntityType756To755(mapEntityType757To756(mapEntityType759To757(mapEntityType760To759(mapEntityType761To760(mapEntityType762To761(mapEntityType763To762(mapEntityType(id765))))))))))))"),
        "mapSound": ("mapSound736To735",
                          "mapSound751To736(mapSound753To751(mapSound754To753(mapSound755To754(mapSound756To755(mapSound757To756(mapSound759To757(mapSound760To759(mapSound761To760(mapSound762To761(mapSound763To762(mapSound(id765))))))))))))"),
        "mapParticle": ("mapParticle736To735",
                          "mapParticle751To736(mapParticle753To751(mapParticle754To753(mapParticle755To754(mapParticle756To755(mapParticle757To756(mapParticle759To757(mapParticle760To759(mapParticle761To760(mapParticle762To761(mapParticle763To762(mapParticle(id765))))))))))))"),
        "mapBlock": ("mapBlock736To735",
                          "mapBlock751To736(mapBlock753To751(mapBlock754To753(mapBlock755To754(mapBlock756To755(mapBlock757To756(mapBlock759To757(mapBlock760To759(mapBlock761To760(mapBlock762To761(mapBlock763To762(mapBlock(id765))))))))))))"),
        "mapMenu": ("mapMenu736To735",
                          "mapMenu751To736(mapMenu753To751(mapMenu754To753(mapMenu755To754(mapMenu756To755(mapMenu757To756(mapMenu759To757(mapMenu760To759(mapMenu761To760(mapMenu762To761(mapMenu763To762(mapMenu(id765))))))))))))"),
        "mapBlockEntityType": ("mapBlockEntityType736To735",
                          "mapBlockEntityType751To736(mapBlockEntityType753To751(mapBlockEntityType754To753(mapBlockEntityType755To754(mapBlockEntityType756To755(mapBlockEntityType757To756(mapBlockEntityType759To757(mapBlockEntityType760To759(mapBlockEntityType761To760(mapBlockEntityType762To761(mapBlockEntityType763To762(mapBlockEntityType(id765))))))))))))"),
    }
    chain_735 = {}
    for name, (wrapper, inner736) in chain_down.items():
        expr736 = read_dispatch_branch(remapper_text, name, name + "751To736(")
        if expr736 != inner736:
            raise SystemExit(f"dispatch の 736 分岐が想定と違う（{name}）: {expr736[:80]}")
        expr735 = f"{wrapper}({inner736})"
        if expr735.count("(") != expr735.count(")"):
            raise SystemExit(f"735 の連鎖の括弧が合わない（{name}）")
        chain_735[name] = expr735
    effect736 = read_dispatch_branch(remapper_text, "mapEffect", "mapEffect751To736(")
    if effect736 != ("mapEffect751To736(mapEffect753To751(mapEffect754To753(mapEffect755To754(mapEffect756To755(mapEffect757To756(mapEffect759To757(mapEffect(id765))))))))"):
        raise SystemExit(f"dispatch の 736 分岐が想定と違う（mapEffect）: {effect736[:80]}")
    effect735 = f"mapEffect736To735({effect736})"
    if effect735.count("(") != effect735.count(")"):
        raise SystemExit("735 の連鎖の括弧が合わない（mapEffect）")
    soundname736 = read_dispatch_branch(remapper_text, "mapSoundName", "mapSoundName751To736(")
    if soundname736 != ("mapSoundName751To736(mapSoundName753To751(mapSoundName754To753(mapSoundName755To754(mapSoundName756To755(mapSoundName757To756(mapSoundName759To757(mapSoundName760To759(mapSoundName761To760(mapSoundName762To761(mapSoundName763To762(mapSoundName(name))))))))))))"):
        raise SystemExit(f"dispatch の 736 分岐が想定と違う（mapSoundName）: {soundname736[:80]}")
    soundname735 = f"mapSoundName736To735({soundname736})"
    if soundname735.count("(") != soundname735.count(")"):
        raise SystemExit("735 の連鎖の括弧が合わない（mapSoundName）")
    itemrev736 = read_dispatch_branch(remapper_text, "mapItemReverse", "mapItemReverse(")
    # 735 分岐は 736 分岐の最も内側（mapItem736To751(id)）に 735→736 段を
    # 1 つ入れたもの。置換対象が 1 件だけであることを確認してから置き換える
    # （一括置換で段が落ちる罠を避ける。736 §12.1 の1）。
    if itemrev736.count("mapItem736To751(id)") != 1:
        raise SystemExit("mapItemReverse の 736 分岐に mapItem736To751(id) が 1 件でない")
    itemrev735 = itemrev736.replace("mapItem736To751(id)",
                                    "mapItem736To751(mapItem735To736(id))")
    if itemrev735.count("(") != itemrev735.count(")"):
        raise SystemExit("735 の連鎖の括弧が合わない（mapItemReverse）")
    print("ディスパッチの 735 連鎖を手書きし、736 分岐との突き合わせを確認（11 本）")

    src = remapper_text

    def insert_before(anchor, text, what, marker):
        nonlocal src
        if marker in src:
            print(f"skip（既存）: {what}")
            return
        if src.count(anchor) != 1:
            print(f"アンカーが 1 件でない: {what}: {src.count(anchor)} 件: {anchor[:80]!r}",
                  file=sys.stderr)
            raise SystemExit(1)
        idx = src.find(anchor)
        src = src[:idx] + text + src[idx:]

    def insert_after(anchor, text, what, marker):
        nonlocal src
        if marker in src:
            print(f"skip（既存）: {what}")
            return
        if src.count(anchor) != 1:
            print(f"アンカーが 1 件でない: {what}: {src.count(anchor)} 件: {anchor[:80]!r}",
                  file=sys.stderr)
            raise SystemExit(1)
        idx = src.find(anchor) + len(anchor)
        src = src[:idx] + text + src[idx:]

    # 1. import
    insert_before("import static io.detergentmc.protocol.RegistryTables736.*;",
                  "import static io.detergentmc.protocol.RegistryTables735.*;\n",
                  "import RegistryTables735", "RegistryTables735.*")
    # 1b. ブロック状態の下向き表は別クラス（RegistryTables735BlockState）。
    # <clinit> 64KB 対策の分割（773／774／775 の *BlockState.java と同じ方式）。
    insert_after("import static io.detergentmc.protocol.RegistryTables735.*;\n",
                 "import static io.detergentmc.protocol.RegistryTables735BlockState.*;\n",
                 "import RegistryTables735BlockState", "RegistryTables735BlockState.*")

    # 2. PROTOCOL_735（PROTOCOL_736 の直前）
    insert_before("    /** 736（1.16.1）のプロトコル番号。 */",
                  "    /** 735（1.16）のプロトコル番号。 */\n"
                  "    public static final int PROTOCOL_735 = 735;\n",
                  "PROTOCOL_735", "PROTOCOL_735 = 735")

    # 3. 735定数（GENERIC_DAMAGE_TYPE_736 の直後）
    constants_735 = """
    /** 735側の最大の番号（ViaVersion mapping-1.16.json の件数−1。バイオームだけ id の最大値）。 */
    public static final int MAX_BLOCK_STATE_735 = 17103;
    public static final int MAX_ITEM_735 = 974;
    public static final int MAX_ENTITY_TYPE_735 = 106;
    public static final int MAX_SOUND_735 = 984;
    public static final int MAX_PARTICLE_735 = 71;
    public static final int MAX_BLOCK_735 = 762;
    public static final int MAX_MENU_735 = 23;
    /** 735側のエンチャントは 0〜37（38件。736と同一順序）。 */
    public static final int MAX_ENCHANTMENT_735 = 37;
    /** 735側のバイオームは 0〜173（79件。id は 0〜173 の非連番）。 */
    public static final int MAX_BIOME_735 = 173;
    /** 735側のダメージ種別は 0〜41（42件。762 の順序の恒等写像。実バニラで検証する）。 */
    public static final int MAX_DAMAGE_TYPE_735 = 41;
    /** 735側のブロックエンティティ種別は 0〜32（33件。736と同一順序。DROP は無い）。 */
    public static final int MAX_BLOCK_ENTITY_735 = 32;
    /** 735側の属性は 0〜12（13件。736と同一順序）。 */
    public static final int MAX_ATTRIBUTE_735 = 12;
    /** 735側のエフェクトは 1〜32（32件。736と同一順序。1ベース）。 */
    public static final int MAX_EFFECT_735 = 32;

    /** 735側の代替先（mapping-1.16.json の番号。Speed だけ 1ベースで 1）。 */
    public static final int STONE_BLOCK_STATE_735 = 1;
    public static final int AIR_BLOCK_STATE_735 = 0;
    public static final int STONE_ITEM_735 = 1;
    public static final int PIG_ENTITY_TYPE_735 = 59;
    public static final int GENERIC_SOUND_735 = 809; // block.stone.break
    public static final int AMBIENT_PARTICLE_735 = 0; // ambient_entity_effect
    public static final int STONE_BLOCK_735 = 1;
    public static final int CRAFTING_MENU_735 = 11;
    public static final int SPEED_EFFECT_735 = 1;
    /** 735側の平原バイオーム（minecraft-data pc/1.16/biomes.json）。 */
    public static final int PLAINS_BIOME_735 = 1;
    /** 735側の generic ダメージ種別（762 と同一順序と仮定）。 */
    public static final int GENERIC_DAMAGE_TYPE_735 = 16;
"""
    insert_after("    public static final int GENERIC_DAMAGE_TYPE_736 = 16;\n",
                 constants_735, "735定数", "MAX_BLOCK_STATE_735")

    # 4. 735の展開済み表（751 ⇔ 736 の展開済み表の直前）
    maps_735 = """
    // ------------------------------------------------------------------
    // 736 ⇔ 735（1.16.1 ⇔ 1.16）の展開済み表。RegistryTables735
    // の区間＋上書きの表をクラスロード時に int[] へ展開する。
    // 順向き（736→735）は expand()、反転（735→736）は invert() で作る。
    // 補正は 0 件（736 と 735 のレジストリ内容は同一のため、invert() の
    // first-wins と名前の真値が食い違う箇所は無い）。
    // アイテムだけは 766／777／736 の前例にならい、名前から作った既存の
    // 逆変換表（ITEM_REVERSE_735）を expand() でそのまま使う
    // （invert()＋補正でも同じ結果になることを書き込み前に確認済み）。
    // エフェクトは表を作らない（アクセサでハードコードする。735 と 736 は
    // どちらも 32件・同一順序のため恒等写像で、DROP は無い）。
    // ブロックエンティティ種別も表を作らない。735 は 33 件・736 も 33 件で
    // 同一順序のため恒等写像（DROP は無い）。
    // 735 は合成表を作らない。
    // バイオームの反転表は 735 側 174 件（id 0〜173。隙間の id は平原）。
    // 735 の表は private static final class Maps735 にまとめる（RegistryRemapper の
    // static 初期化子は 64KB 制限に達しているため。736 追加後も外側は 64,669 バイト
    // のまま（残り 866 バイト）。Maps736 方式で外側は 0 バイト増）。
    // ------------------------------------------------------------------


    /** 736 ⇔ 735 の展開済み表をまとめたホルダ。RegistryRemapper の static 初期化子は
     * 64KB 制限に達しているため（736 追加後も外側は 64,669 バイト。残り 866 バイト）、735 の表の
     * 初期化はこの別クラスの static 初期化子に分けてある。735 以降も同じ方式にすること。
     * 初回アクセス時に初期化される（クラスロード時の初期化と等価。expand()／invert() は
     * 純粋関数）。アクセサからは Maps735.〜 で参照する。 */
    private static final class Maps735 {
        /** ブロック状態の反転の補正（735 id → 736 id）。0 件。
         * 736 と 735 のレジストリは同一のため invert() だけでも名前の
         * 真値と一致する。736 と同じ構造を保つために空の表を置く。
         * tools/maps735-corrections.json から生成。検証は
         * tools/verify-735-tables.py のチェックDを参照。 */
        private static final int[][] BLOCK_STATE_UP_735_CORRECTIONS = {
""" + blockstate_corr_java + """
        };

        /** 音の反転の補正（735 id → 736 id）。0 件。音は invert() だけでも
         * 名前の真値と一致する。736 と同じ構造を保つために空の表を置く。
         * tools/maps735-corrections.json から生成。検証は
         * tools/verify-735-tables.py のチェックDを参照。 */
        private static final int[][] SOUND_UP_735_CORRECTIONS = {
""" + sound_corr_java + """
        };

        /** バイオームの反転の補正（735 id → 736 id）。0 件。735 の 79 件は
         * すべて 736 と同じ id・同じ名前のため、invert() だけでも名前の
         * 真値と一致する。削除・改名が無いため当たらない。736 と同じ構造を保つために
         * 空の表を置く。
         * RegistryTables735.java の BIOME_735_TO_736_* 表との差分から生成。
         * 検証は tools/verify-735-tables.py のチェックGを参照。 */
        private static final int[][] BIOME_UP_735_CORRECTIONS = {
""" + biome_corr_java + """
        };

        /** 736のブロック状態ID → 735のブロック状態ID（RegistryTables735）。 */
        private static final int[] BLOCK_STATE_736_TO_735_MAP =
                expand(BLOCK_STATE_735_RANGES, BLOCK_STATE_735_OVERRIDES, MAX_BLOCK_STATE_736 + 1,
                        STONE_BLOCK_STATE_735);
        /** 735のブロック状態ID → 736のブロック状態ID（反転＋補正）。 */
        private static final int[] BLOCK_STATE_735_TO_736_MAP = invert(
                BLOCK_STATE_735_RANGES, BLOCK_STATE_735_OVERRIDES,
                MAX_BLOCK_STATE_736 + 1, MAX_BLOCK_STATE_735 + 1,
                STONE_BLOCK_STATE_736, BLOCK_STATE_UP_735_CORRECTIONS);
        /** 736のアイテムID → 735のアイテムID。 */
        private static final int[] ITEM_736_TO_735_MAP =
                expand(ITEM_735_RANGES, ITEM_735_OVERRIDES, MAX_ITEM_736 + 1, STONE_ITEM_735);
        /** 735のアイテムID → 736のアイテムID（名前から作った逆変換表）。 */
        private static final int[] ITEM_735_TO_736_MAP =
                expand(ITEM_REVERSE_735_RANGES, ITEM_REVERSE_735_OVERRIDES, MAX_ITEM_735 + 1,
                        STONE_ITEM_736);
        /** 736のエンティティ種別ID → 735のエンティティ種別ID。 */
        private static final int[] ENTITY_TYPE_736_TO_735_MAP =
                expand(ENTITY_TYPE_735_RANGES, ENTITY_TYPE_735_OVERRIDES, MAX_ENTITY_TYPE_736 + 1,
                        PIG_ENTITY_TYPE_735);
        /** 735のエンティティ種別ID → 736のエンティティ種別ID（反転）。 */
        private static final int[] ENTITY_TYPE_735_TO_736_MAP = invert(
                ENTITY_TYPE_735_RANGES, ENTITY_TYPE_735_OVERRIDES,
                MAX_ENTITY_TYPE_736 + 1, MAX_ENTITY_TYPE_735 + 1, PIG_ENTITY_TYPE_736, null);
        /** 736の音ID → 735の音ID。 */
        private static final int[] SOUND_736_TO_735_MAP =
                expand(SOUND_735_RANGES, SOUND_735_OVERRIDES, MAX_SOUND_736 + 1, GENERIC_SOUND_735);
        /** 735の音ID → 736の音ID（反転＋補正）。 */
        private static final int[] SOUND_735_TO_736_MAP = invert(
                SOUND_735_RANGES, SOUND_735_OVERRIDES,
                MAX_SOUND_736 + 1, MAX_SOUND_735 + 1,
                GENERIC_SOUND_736, SOUND_UP_735_CORRECTIONS);
        /** 736のパーティクルID → 735のパーティクルID。 */
        private static final int[] PARTICLE_736_TO_735_MAP =
                expand(PARTICLE_735_RANGES, PARTICLE_735_OVERRIDES, MAX_PARTICLE_736 + 1,
                        AMBIENT_PARTICLE_735);
        /** 735のパーティクルID → 736のパーティクルID（反転）。 */
        private static final int[] PARTICLE_735_TO_736_MAP = invert(
                PARTICLE_735_RANGES, PARTICLE_735_OVERRIDES,
                MAX_PARTICLE_736 + 1, MAX_PARTICLE_735 + 1,
                AMBIENT_PARTICLE_736, null);
        /** 736のブロックID → 735のブロックID。 */
        private static final int[] BLOCK_736_TO_735_MAP =
                expand(BLOCK_735_RANGES, BLOCK_735_OVERRIDES, MAX_BLOCK_736 + 1, STONE_BLOCK_735);
        /** 735のブロックID → 736のブロックID（反転）。 */
        private static final int[] BLOCK_735_TO_736_MAP = invert(
                BLOCK_735_RANGES, BLOCK_735_OVERRIDES,
                MAX_BLOCK_736 + 1, MAX_BLOCK_735 + 1, STONE_BLOCK_736, null);
        /** 736のメニュー種別ID → 735のメニュー種別ID。 */
        private static final int[] MENU_736_TO_735_MAP =
                expand(MENU_735_RANGES, MENU_735_OVERRIDES, MAX_MENU_736 + 1, CRAFTING_MENU_735);
        /** 735のメニュー種別ID → 736のメニュー種別ID（反転）。 */
        private static final int[] MENU_735_TO_736_MAP = invert(
                MENU_735_RANGES, MENU_735_OVERRIDES,
                MAX_MENU_736 + 1, MAX_MENU_735 + 1, CRAFTING_MENU_736, null);
        /** 736のバイオームID → 735のバイオームID。両側とも同じ 79 件で
         * 新規バイオームは無い。隙間の id は平原（1）へ落とす。 */
        private static final int[] BIOME_736_TO_735_MAP =
                expand(BIOME_735_RANGES, BIOME_735_OVERRIDES, MAX_BIOME_736 + 1, PLAINS_BIOME_735);
        /** 735のバイオームID → 736のバイオームID（反転＋補正。174 件）。 */
        private static final int[] BIOME_735_TO_736_MAP = invert(
                BIOME_735_RANGES, BIOME_735_OVERRIDES,
                MAX_BIOME_736 + 1, MAX_BIOME_735 + 1, PLAINS_BIOME_736,
                BIOME_UP_735_CORRECTIONS);
        /** ダメージ種別ID：736 → 735（762 の 42 件の恒等写像。1.16／1.16.1 の
         * どちらにも damage_type レジストリは無いため、ワイヤ上の呼び出し元は無い）。 */
        private static final int[] DAMAGE_TYPE_735_MAP = {
            0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19,
            20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32, 33, 34, 35, 36, 37,
            38, 39, 40, 41
        };
        /** ダメージ種別ID：735 → 736（762 の 42 件の恒等写像。呼び出し元は無い）。 */
        private static final int[] DAMAGE_TYPE_REVERSE_735_MAP = {
            0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19,
            20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32, 33, 34, 35, 36, 37,
            38, 39, 40, 41
        };
        /** 736の属性ID → 735の属性ID。並びは同一。範囲外は {@link #DROP}。 */
        private static final int[] ATTRIBUTE_735_MAP =
                expand(ATTRIBUTE_735_RANGES, ATTRIBUTE_735_OVERRIDES, MAX_ATTRIBUTE_736 + 1, DROP);
        /** 735の属性ID → 736の属性ID。並びは同一。範囲外は {@link #DROP}。 */
        private static final int[] ATTRIBUTE_REVERSE_735_MAP =
                expand(ATTRIBUTE_REVERSE_735_RANGES, ATTRIBUTE_REVERSE_735_OVERRIDES,
                        MAX_ATTRIBUTE_735 + 1, DROP);
        /** 736のエンチャントID → 735のエンチャントID。並びは同一。範囲外は {@link #DROP}。 */
        private static final int[] ENCHANTMENT_735_MAP =
                expand(ENCHANTMENT_735_RANGES, ENCHANTMENT_735_OVERRIDES,
                        MAX_ENCHANTMENT_736 + 1, DROP);
        /** 735のエンチャントID → 736のエンチャントID。並びは同一。範囲外は {@link #DROP}。 */
        private static final int[] ENCHANTMENT_REVERSE_735_MAP =
                expand(ENCHANTMENT_REVERSE_735_RANGES, ENCHANTMENT_REVERSE_735_OVERRIDES,
                        MAX_ENCHANTMENT_735 + 1, DROP);

        /** 736の音の名前 → 735の音の名前（Stop Sound 用）。数値の反転表と一致させる。 */
        private static final Map<String, String> SOUND_NAME_FALLBACK_736_TO_735_MAP =
                buildSoundNameMap(SOUND_NAME_FALLBACKS_736_TO_735);

        /** 735の音の名前 → 736の音の名前（Stop Sound 用）。改名は無い（空の表）。 */
        private static final Map<String, String> SOUND_NAME_FALLBACK_735_TO_736_MAP =
                buildSoundNameMap(SOUND_NAME_FALLBACKS_735_TO_736);
    }
"""
    insert_before("    // ------------------------------------------------------------------\n"
                  "    // 751 ⇔ 736（1.16.2 ⇔ 1.16.1）の展開済み表。RegistryTables736\n",
                  maps_735, "735の展開済み表", "BLOCK_STATE_736_TO_735_MAP")

    # 5. アクセサメソッド（751 ⇔ 736 節の直前）
    methods_735 = """
    // ------------------------------------------------------------------
    // 736 ⇔ 735（1.16.1 ⇔ 1.16）
    // レジストリは RegistryTables735（補正済み ViaVersion mapping-1.16.json を
    // 実バニラ 1.16 の registries.json／reports と突き合わせて確認したもの。
    // 736 とは同じ：実バニラの reports がバイト同一で、レジストリ内容は
    // 1.16 と 1.16.1 で変わっていない。
    // 順向き表・反転表とも全種恒等写像。補正は 0 件。
    // 735↔763〜775 は隣接表の連鎖（735→736→751→753→754→755→756→757→759→760→761→762→763→合成表）
    // で到達する。合成表は作らない。
    // パケットIDはこのスクリプトの範囲外（パケット担当が packet-ids-735.json で実測する）。
    // ------------------------------------------------------------------

    /** 736のブロック状態ID → 735のブロック状態ID。範囲外はSTONE_BLOCK_STATE_735 に相当する代替。 */
    public static int mapBlockState736To735(int id736) {
        if (id736 < 0 || id736 >= Maps735.BLOCK_STATE_736_TO_735_MAP.length) {
            logUnknownComposedOnce("736→735", "ブロック状態", id736);
            return STONE_BLOCK_STATE_735;
        }
        return Maps735.BLOCK_STATE_736_TO_735_MAP[id736];
    }

    /** 735のブロック状態ID → 736のブロック状態ID。範囲外はSTONE_BLOCK_STATE_736 に相当する代替。 */
    public static int mapBlockState735To736(int id735) {
        if (id735 < 0 || id735 >= Maps735.BLOCK_STATE_735_TO_736_MAP.length) {
            logUnknownComposedOnce("735→736", "ブロック状態", id735);
            return STONE_BLOCK_STATE_736;
        }
        return Maps735.BLOCK_STATE_735_TO_736_MAP[id735];
    }

    /** 736のアイテムID → 735のアイテムID。範囲外はSTONE_ITEM_735 に相当する代替。 */
    public static int mapItem736To735(int id736) {
        if (id736 < 0 || id736 >= Maps735.ITEM_736_TO_735_MAP.length) {
            logUnknownComposedOnce("736→735", "アイテム", id736);
            return STONE_ITEM_735;
        }
        return Maps735.ITEM_736_TO_735_MAP[id736];
    }

    /** 735のアイテムID → 736のアイテムID。範囲外はSTONE_ITEM_736 に相当する代替。 */
    public static int mapItem735To736(int id735) {
        if (id735 < 0 || id735 >= Maps735.ITEM_735_TO_736_MAP.length) {
            logUnknownComposedOnce("735→736", "アイテム", id735);
            return STONE_ITEM_736;
        }
        return Maps735.ITEM_735_TO_736_MAP[id735];
    }

    /** 736のエンティティ種別ID → 735のエンティティ種別ID。範囲外はPIG_ENTITY_TYPE_735 に相当する代替。 */
    public static int mapEntityType736To735(int id736) {
        if (id736 < 0 || id736 >= Maps735.ENTITY_TYPE_736_TO_735_MAP.length) {
            logUnknownComposedOnce("736→735", "エンティティ種別", id736);
            return PIG_ENTITY_TYPE_735;
        }
        return Maps735.ENTITY_TYPE_736_TO_735_MAP[id736];
    }

    /** 735のエンティティ種別ID → 736のエンティティ種別ID。範囲外はPIG_ENTITY_TYPE_736 に相当する代替。 */
    public static int mapEntityType735To736(int id735) {
        if (id735 < 0 || id735 >= Maps735.ENTITY_TYPE_735_TO_736_MAP.length) {
            logUnknownComposedOnce("735→736", "エンティティ種別", id735);
            return PIG_ENTITY_TYPE_736;
        }
        return Maps735.ENTITY_TYPE_735_TO_736_MAP[id735];
    }

    /** 736の音ID → 735の音ID。範囲外はGENERIC_SOUND_735 に相当する代替。 */
    public static int mapSound736To735(int id736) {
        if (id736 < 0 || id736 >= Maps735.SOUND_736_TO_735_MAP.length) {
            logUnknownComposedOnce("736→735", "音", id736);
            return GENERIC_SOUND_735;
        }
        return Maps735.SOUND_736_TO_735_MAP[id736];
    }

    /** 735の音ID → 736の音ID。範囲外はGENERIC_SOUND_736 に相当する代替。 */
    public static int mapSound735To736(int id735) {
        if (id735 < 0 || id735 >= Maps735.SOUND_735_TO_736_MAP.length) {
            logUnknownComposedOnce("735→736", "音", id735);
            return GENERIC_SOUND_736;
        }
        return Maps735.SOUND_735_TO_736_MAP[id735];
    }

    /** 736のパーティクルID → 735のパーティクルID。範囲外はAMBIENT_PARTICLE_735 に相当する代替。 */
    public static int mapParticle736To735(int id736) {
        if (id736 < 0 || id736 >= Maps735.PARTICLE_736_TO_735_MAP.length) {
            logUnknownComposedOnce("736→735", "パーティクル", id736);
            return AMBIENT_PARTICLE_735;
        }
        return Maps735.PARTICLE_736_TO_735_MAP[id736];
    }

    /** 735のパーティクルID → 736のパーティクルID。範囲外はAMBIENT_PARTICLE_736 に相当する代替。 */
    public static int mapParticle735To736(int id735) {
        if (id735 < 0 || id735 >= Maps735.PARTICLE_735_TO_736_MAP.length) {
            logUnknownComposedOnce("735→736", "パーティクル", id735);
            return AMBIENT_PARTICLE_736;
        }
        return Maps735.PARTICLE_735_TO_736_MAP[id735];
    }

    /** 736のブロックID → 735のブロックID。範囲外はSTONE_BLOCK_735 に相当する代替。 */
    public static int mapBlock736To735(int id736) {
        if (id736 < 0 || id736 >= Maps735.BLOCK_736_TO_735_MAP.length) {
            logUnknownComposedOnce("736→735", "ブロック", id736);
            return STONE_BLOCK_735;
        }
        return Maps735.BLOCK_736_TO_735_MAP[id736];
    }

    /** 735のブロックID → 736のブロックID。範囲外はSTONE_BLOCK_736 に相当する代替。 */
    public static int mapBlock735To736(int id735) {
        if (id735 < 0 || id735 >= Maps735.BLOCK_735_TO_736_MAP.length) {
            logUnknownComposedOnce("735→736", "ブロック", id735);
            return STONE_BLOCK_736;
        }
        return Maps735.BLOCK_735_TO_736_MAP[id735];
    }

    /** 736のメニュー種別ID → 735のメニュー種別ID。範囲外はCRAFTING_MENU_735 に相当する代替。 */
    public static int mapMenu736To735(int id736) {
        if (id736 < 0 || id736 >= Maps735.MENU_736_TO_735_MAP.length) {
            logUnknownComposedOnce("736→735", "メニュー", id736);
            return CRAFTING_MENU_735;
        }
        return Maps735.MENU_736_TO_735_MAP[id736];
    }

    /** 735のメニュー種別ID → 736のメニュー種別ID。範囲外はCRAFTING_MENU_736 に相当する代替。 */
    public static int mapMenu735To736(int id735) {
        if (id735 < 0 || id735 >= Maps735.MENU_735_TO_736_MAP.length) {
            logUnknownComposedOnce("735→736", "メニュー", id735);
            return CRAFTING_MENU_736;
        }
        return Maps735.MENU_735_TO_736_MAP[id735];
    }

    /**
     * 736のブロックエンティティ種別ID → 735のブロックエンティティ種別ID。
     * 735 は 33 件・736 も 33 件で同一順序のため恒等写像（DROP は無い。
     * 736 と違い、片方だけの種別は無い）。
     */
    public static int mapBlockEntityType736To735(int id736) {
        if (id736 < 0 || id736 > MAX_BLOCK_ENTITY_736) {
            logUnknownComposedOnce("736→735", "ブロックエンティティ種別", id736);
            return DROP;
        }
        return id736;
    }

    /**
     * 735のブロックエンティティ種別ID → 736のブロックエンティティ種別ID。
     * 0〜32 は恒等写像。
     */
    public static int mapBlockEntityType735To736(int id735) {
        if (id735 < 0 || id735 > MAX_BLOCK_ENTITY_735) {
            logUnknownComposedOnce("735→736", "ブロックエンティティ種別", id735);
            return DROP;
        }
        return id735;
    }

    /** 736のバイオームID → 735のバイオームID。範囲外はPLAINS_BIOME_735 に相当する代替。 */
    public static int mapBiome736To735(int id736) {
        if (id736 < 0 || id736 >= Maps735.BIOME_736_TO_735_MAP.length) {
            logUnknownComposedOnce("736→735", "バイオーム", id736);
            return PLAINS_BIOME_735;
        }
        return Maps735.BIOME_736_TO_735_MAP[id736];
    }

    /** 735のバイオームID → 736のバイオームID。範囲外はPLAINS_BIOME_736 に相当する代替。 */
    public static int mapBiome735To736(int id735) {
        if (id735 < 0 || id735 >= Maps735.BIOME_735_TO_736_MAP.length) {
            logUnknownComposedOnce("735→736", "バイオーム", id735);
            return PLAINS_BIOME_736;
        }
        return Maps735.BIOME_735_TO_736_MAP[id735];
    }

    /** 736のダメージ種別ID → 735のダメージ種別ID（恒等写像。ただし1.16／1.16.1 の codec に
     * damage_type レジストリは存在しないため、ワイヤ上の呼び出し元は無い）。 */
    public static int mapDamageType736To735(int id736) {
        if (id736 < 0 || id736 > MAX_DAMAGE_TYPE_736) {
            logUnknownOnce(PROTOCOL_735, "ダメージ種別", id736);
            return DROP;
        }
        return Maps735.DAMAGE_TYPE_735_MAP[id736];
    }

    /** 735のダメージ種別ID → 736のダメージ種別ID（恒等写像。ワイヤ上の呼び出し元は無い）。 */
    public static int mapDamageType735To736(int id735) {
        if (id735 < 0 || id735 > MAX_DAMAGE_TYPE_735) {
            logUnknownOnce(PROTOCOL_736, "ダメージ種別", id735);
            return DROP;
        }
        return Maps735.DAMAGE_TYPE_REVERSE_735_MAP[id735];
    }

    /**
     * 736のエフェクトID → 735のエフェクトID。735 と 736 は 32件・同一順序
     * のため恒等写像。範囲外は Speed（1）。
     */
    public static int mapEffect736To735(int id736) {
        if (id736 < 0 || id736 > MAX_EFFECT_736) {
            logUnknownComposedOnce("736→735", "エフェクト", id736);
            return SPEED_EFFECT_735;
        }
        return id736;
    }

    /**
     * 735のエフェクトID → 736のエフェクトID。1〜32 は恒等写像。
     * 範囲外は Speed（1）。
     */
    public static int mapEffect735To736(int id735) {
        if (id735 < 0 || id735 > MAX_EFFECT_735) {
            logUnknownComposedOnce("735→736", "エフェクト", id735);
            return SPEED_EFFECT_736;
        }
        return id735;
    }

    /** 736の属性ID → 735の属性ID。並びは同一。範囲外は {@link #DROP}。 */
    public static int mapAttribute736To735(int id736) {
        if (id736 < 0 || id736 > MAX_ATTRIBUTE_736) {
            logUnknownOnce(PROTOCOL_735, "属性", id736);
            return DROP;
        }
        return Maps735.ATTRIBUTE_735_MAP[id736];
    }

    /** 735の属性ID → 736の属性ID。並びは同一。範囲外は {@link #DROP}。 */
    public static int mapAttribute735To736(int id735) {
        if (id735 < 0 || id735 > MAX_ATTRIBUTE_735) {
            logUnknownOnce(PROTOCOL_736, "属性", id735);
            return DROP;
        }
        return Maps735.ATTRIBUTE_REVERSE_735_MAP[id735];
    }

    /** 735の属性ID → 属性の名前（Update Attributes は 1.16 では名前形式のため）。 */
    public static String attributeName735(int id735) {
        if (id735 < 0 || id735 >= ATTRIBUTE_735_NAMES.length) {
            return null;
        }
        return ATTRIBUTE_735_NAMES[id735];
    }

    /**
     * 属性の名前 → 735の属性ID（765／763／764 は属性が名前形式のため、
     * 735向けに変換するときに使う）。無い名前は {@link #DROP}。
     */
    public static int attributeId735(String name) {
        if (name == null) {
            return DROP;
        }
        String plain = stripMinecraftPrefix(name);
        for (int i = 0; i < ATTRIBUTE_735_NAMES.length; i++) {
            if (ATTRIBUTE_735_NAMES[i].equals(plain)) {
                return i;
            }
        }
        return DROP;
    }

    /** 736のエンチャントID → 735のエンチャントID。並びは同一。範囲外は {@link #DROP}。 */
    public static int mapEnchantment736To735(int id736) {
        if (id736 < 0 || id736 > MAX_ENCHANTMENT_736) {
            logUnknownOnce(PROTOCOL_735, "エンチャント", id736);
            return DROP;
        }
        return Maps735.ENCHANTMENT_735_MAP[id736];
    }

    /** 735のエンチャントID → 736のエンチャントID。並びは同一。範囲外は {@link #DROP}。 */
    public static int mapEnchantment735To736(int id735) {
        if (id735 < 0 || id735 > MAX_ENCHANTMENT_735) {
            logUnknownOnce(PROTOCOL_736, "エンチャント", id735);
            return DROP;
        }
        return Maps735.ENCHANTMENT_REVERSE_735_MAP[id735];
    }

    /** 736の音の名前 → 735の音の名前（Stop Sound 用）。数値の反転表と一致させる。 */
    public static String mapSoundName736To735(String name) {
        return mapSoundNameComposed(name, Maps735.SOUND_NAME_FALLBACK_736_TO_735_MAP);
    }

    /** 735の音の名前 → 736の音の名前（Stop Sound 用）。改名は無い。 */
    public static String mapSoundName735To736(String name) {
        return mapSoundNameComposed(name, Maps735.SOUND_NAME_FALLBACK_735_TO_736_MAP);
    }
"""
    insert_before("    // ------------------------------------------------------------------\n"
                  "    // 751 ⇔ 736（1.16.2 ⇔ 1.16.1）\n",
                  methods_735, "735アクセサ", "mapBlockState736To735")

    # 6. ディスパッチのコメント更新（735の連鎖を追記）
    # 注意：756 のスクリプトは old→new の replace を使っていたが、new が
    # old を部分文字列として含むため再実行のたびに 756 の行が増殖した
    # （RegistryRemapper.java の dispatch コメントに 756 の行が 5 回ある）。
    # 735 ではマーカー付きの insert_after で 1 回だけ追加する。
    dispatch_735_line = ("    // 735は736の表を経由する（735↔763〜775は"
                         "735→736→751→753→754→755→756→757→759→760→761→762→763→合成表の連鎖）。\n")
    insert_after("    // 736は751の表を経由する（736↔763〜775は"
                 "736→751→753→754→755→756→757→759→760→761→762→763→合成表の連鎖）。\n",
                 dispatch_735_line, "dispatch コメント 735",
                 "735は736の表を経由する")

    # 7. target 分岐への 735 追加（各 dispatch メソッドの 736 分岐の直前。
    # insert_after はメソッド冒頭の直後に入れるため、736 分岐より前に来る。
    # 下向きは 736 分岐の外側に 736→735 段を 1 つ巻く。
    # 括弧の数：736 分岐は 12 個の開き／12 個の閉じ、巻いた後は 13 個／13 個。
    # mapEffect だけは 736 分岐が 8 個／8 個で、巻いた後は 9 個／9 個。
    # 手書きした連鎖は上の検証6でファイルの 736 分岐と突き合わせてある。）
    for name, expr735 in chain_735.items():
        body = (f"        if (clientProtocol == PROTOCOL_735) {{\n"
                f"            return {expr735};\n"
                f"        }}\n")
        anchor = f"    public static int {name}(int id765, int clientProtocol) {{\n"
        marker = f"return {expr735};"
        insert_after(anchor, body, f"dispatch {name} 735", marker)

    # mapItemReverse（引数名が id）。上向き（クライアント→765）のため、
    # 新しい隣接段（735→736）は最も内側（最初に適用）に入れる。
    # 下向き（mapItem 等）とは逆で、外側に巻くのは誤り。
    # 注意：757／759／760／761／762 の分岐は外側に巻く形で作られており、
    # 隣接表を逆順に合成している（実測：757 分岐は正しい合成と 724/1101 件
    # 食い違う。例：tropical_fish_spawn_egg 757 id 926 の正しい 765 id は
    # 1067 だが 757 分岐は 1066 を返す。§12.5 参照）。756／751／736 分岐は
    # 正しい順序。735 分岐も正しい順序で合成する。他分岐の修正は
    # 統括の判断待ち。
    insert_after("    public static int mapItemReverse(int id, int clientProtocol) {\n",
                 "        if (clientProtocol == PROTOCOL_735) {\n"
                 f"            return {itemrev735};\n"
                 "        }\n",
                 "dispatch mapItemReverse 735",
                 f"return {itemrev735};")

    # mapEffect（736→735 は恒等写像。736 分岐の外側にそのままつなぐ）
    insert_after("    public static int mapEffect(int id765, int clientProtocol) {\n",
                 "        if (clientProtocol == PROTOCOL_735) {\n"
                 f"            return {effect735};\n"
                 "        }\n",
                 "dispatch mapEffect 735",
                 f"return {effect735};")

    # mapSoundName
    insert_after("    public static String mapSoundName(String name, int clientProtocol) {\n",
                 "        if (clientProtocol == PROTOCOL_735) {\n"
                 f"            return {soundname735};\n"
                 "        }\n",
                 "dispatch mapSoundName 735",
                 f"return {soundname735};")

    REMAPPER.write_text(src, encoding="utf-8")
    print("RegistryRemapper.java に 735 を差し込んだ。")


if __name__ == "__main__":
    raise SystemExit(main())
