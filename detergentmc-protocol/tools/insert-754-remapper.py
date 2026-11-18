#!/usr/bin/env python3
"""RegistryRemapper.java に 754（1.16.4／1.16.5）対応を差し込む。

雛形は tools/insert-755-remapper.py（隣接連鎖の配線＋書き込み前の
全入力検証・冪等ガード）。アンカー付き挿入のみ行う。アンカーが
見つからなければ失敗して何も書かない。
使い方: python3 insert-754-remapper.py
書き換えた後は必ず git diff で内容を確認してから javac で確認すること。

配線の方針（STAGE-A-754-NOTES.md §11.8 の申し送り）:
- 754 は合成表を作らない。754↔763〜775 は 754→755→756→757→759→760→
  761→762→763→合成表の連鎖で到達する（755〜762 と同じ隣接連鎖グループ）。
- 順向き（755→754）の展開済み表は RegistryTables754 の *_754_* 表を
  expand() で展開する。ブロック状態の下向き表（BLOCK_STATE_754_RANGES・
  BLOCK_STATE_754_OVERRIDES）だけは別クラス RegistryTables754BlockState
  に分けてある（<clinit> 64KB 対策。773／774／775 の *BlockState.java
  と同じ方式。import も 2 行になる）。
- 反転（754→755）の表は invert() で作る。補正は maps754-corrections.json
  から：ブロック状態 46 件・音 35 件。アイテムは invert()＋補正でも
  名前の真値と一致するが、766／777／755 の前例にならい、名前から作った
  既存の逆変換表（ITEM_REVERSE_754）を expand() でそのまま使う。
  エンティティ・パーティクル・ブロック・メニューの補正は 0 件。
- エフェクトは表を作らずハードコードする（mapEffectUp774 の前例）。
  754 と 755 は 32件・同一順序のため恒等写像（DROP なし）。
- ブロックエンティティは 755 と違い恒等写像ではない。754 は 33 件
  （0〜32）、755 は 34 件（0〜33。sculk_sensor＝33 が 1.16.5 には無い）。
  755→754 は 0〜32 恒等・33→DROP、754→755 は 0〜32 恒等。
- ダメージ種別は 42 件の恒等写像のプレースホルダ（DAMAGE_TYPE_754_MAP／
  DAMAGE_TYPE_REVERSE_754_MAP。755 の DAMAGE_TYPE_755 と同じ扱い。
  1.16.5／1.17 のどちらにも damage_type レジストリは無いため、
  ワイヤ上の呼び出し元は無い）。
- 754 の展開済み表は private static final class Maps754 にまとめる。
  RegistryRemapper の static 初期化子は 64KB 制限に達しているため
  （755 追加後も外側は 64,669 バイト。残り 866 バイト）、754 の表を外側の
  static 初期化子に入れると code too large でコンパイルできない。
  754 以降も同じ方式にすること。
- バイオーム：順向き表（BIOME_754）は 755 側 176 件（id 0〜175）、反転表
  （BIOME_754_TO_755）は 754 側 174 件（id 0〜173。隙間の id は平原）。
  1.16.5 の 79 件はすべて 1.17 と同じ id・同じ名前。改名は無い。
  1.17 の新規 dripstone_caves（174）・lush_caves（175）は平原（1）へ落とす。
  反転の補正は RegistryTables754.java の BIOME_754_TO_755_* 表との差分から
  このスクリプトが機械的に作る（755 と同じ仕組み。0 件の見込み）。

書き込み前の検証（失敗したら何も書き込まない）:
- maps754-corrections.json の件数（ブロック状態 46・アイテム 5・音 35・
  他 0 件）。各対が範囲内であることも確認する。
- RegistryTables754.java から読んだ表で、Java の expand()／invert() と
  同じ計算を Python で行い、invert(順向き)＋補正＝名前の真値
  （*_754_TO_755_* 表の展開）と全 7 種で一致することを確認する。
  アイテムは invert()＋補正＝ITEM_REVERSE_754 の展開とも一致することを確認する。
  バイオームは invert() と名前の真値の差分を補正として作り、
  invert(順向き)＋補正＝名前の真値（174 件）を確認する。
  パーティクルの invert() の fallback は wiring と同じ
  AMBIENT_PARTICLE_755（0）を使う（生成スクリプトは entity_effect
  （22）を使っているが、補正を当てた後は -1 が残らないため値は同じ。
  755 の前例と同じ扱い）。
- ダメージ種別・エンチャント・属性の展開結果の件数と代表値を
  確認する。754 側の代替先アンカー（pig＝59、block.stone.break＝816、
  ambient_entity_effect＝0、crafting＝11、stone＝1、plains＝1）を
  vv-1.16.json から実測で確認する。
- ディスパッチの連鎖文字列は手書きする。書き込み前に現在の
  RegistryRemapper.java の 755 分岐の式を読み、754 の連鎖が
  「755 分岐の外側に 755→754 段を 1 つ巻いたもの」（mapItemReverse
  だけは「755 分岐の最も内側に 754→755 段を 1 つ入れたもの」）と
  一致することを確認する。機械変換では段が 1 つ消える罠があるため
  （755 §12.5 の 5）、手書きした文字列とファイルの 755 分岐を
  突き合わせてから書く。
"""
from pathlib import Path
import json
import re
import sys

ROOT = Path(__file__).resolve().parent.parent
REMAPPER = ROOT / "src/main/java/io/detergentmc/protocol/RegistryRemapper.java"
TABLES = ROOT / "src/main/java/io/detergentmc/protocol/RegistryTables754.java"
# ブロック状態の下向き表だけ別クラスに分けてある（<clinit> 64KB 対策。
# 773／774／775 の *BlockState.java と同じ方式）。検証は両ファイルの
# 表を合わせて読む。
TABLES_BLOCKSTATE = ROOT / "src/main/java/io/detergentmc/protocol/RegistryTables754BlockState.java"
CORRECTIONS_JSON = ROOT / "build/remap-data/maps754-corrections.json"
VV116 = ROOT / "build/remap-data/vv-1.16.json"
BIOMES116 = ROOT / "build/remap-data/biomes-1.16.json"

# 755側の最大ID（RegistryRemapper.java の MAX_*_755 定数と照合する）。
MAX_755 = {
    "blockstates": 20341, "items": 1099, "entities": 112, "sounds": 1189,
    "particles": 88, "blocks": 897, "menus": 23, "biomes": 175,
}
# 754側の最大ID（vv-1.16.json 等の実測件数−1。バイオームだけ id の最大値 173）。
MAX_754 = {
    "blockstates": 17111, "items": 975, "entities": 107, "sounds": 991,
    "particles": 71, "blocks": 762, "menus": 23, "biomes": 173,
}
# 755側の代替先（invert() の fallback。gen-754-tables.py の UP_754_FALLBACKS
# と同じ。ただしパーティクルだけ wiring では AMBIENT_PARTICLE_755（0）
# を使う。生成スクリプトの entity_effect（22）とは違うが、補正を当てた
# 後は -1 が残らないため結果は同じ。755 の前例と同じ扱い）。
FALLBACK_755 = {
    "blockstates": 1, "items": 1, "entities": 64, "sounds": 1005,
    "particles": 0, "blocks": 1, "menus": 11, "biomes": 1,
}
# 754側の代替先（expand() の fallback。vv-1.16.json で実測した値）。
FALLBACK_754 = {
    "blockstates": 1, "items": 1, "entities": 59, "sounds": 816,
    "particles": 0, "blocks": 1, "menus": 11, "biomes": 1,
}
# 表名（RegistryTables754.java）。down は 755→754、up は 754→755。
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
    """dispatch メソッドの 755 分岐の return 式を読む（検証用）。
    754 分岐の挿入後（755 分岐が 2 番目以降）に再実行しても読めるよう、
    メソッド冒頭にアンカーせずファイル全体から 755 分岐を探す。
    prefix は return 式の先頭（例：mapBlock756To755(）。mapBlock と
    mapBlockState のように名前が接頭辞の関係にあるメソッドを区別するため、
    メソッド名だけでなく 756 への隣接段まで含める。"""
    m = re.search(
        r"if \(clientProtocol == PROTOCOL_755\) \{\n"
        r"            return (" + re.escape(prefix) + r".*?);\n"
        r"        \}",
        src, re.DOTALL)
    if m is None:
        raise SystemExit(f"dispatch の 755 分岐が見つからない: {method_name}")
    return m.group(1)


def main():
    tables_text = (TABLES.read_text(encoding="utf-8") + "\n"
                   + TABLES_BLOCKSTATE.read_text(encoding="utf-8"))
    corrections = json.loads(CORRECTIONS_JSON.read_text(encoding="utf-8"))
    remapper_text = REMAPPER.read_text(encoding="utf-8")

    # --- 検証0：既存の MAX_*_755 定数が想定値と一致する ---
    const_name = {
        "blockstates": "MAX_BLOCK_STATE_755", "items": "MAX_ITEM_755",
        "entities": "MAX_ENTITY_TYPE_755", "sounds": "MAX_SOUND_755",
        "particles": "MAX_PARTICLE_755", "blocks": "MAX_BLOCK_755",
        "menus": "MAX_MENU_755", "biomes": "MAX_BIOME_755",
    }
    for kind, expected in MAX_755.items():
        m = re.search(r"public static final int " + const_name[kind] + r" = (\d+);",
                      remapper_text)
        if m is None:
            raise SystemExit(f"既存の定数が見つからない: {const_name[kind]}")
        if int(m.group(1)) != expected:
            raise SystemExit(f"既存の {const_name[kind]} が違う：{m.group(1)} != {expected}")
    print("既存の MAX_*_755 定数を確認（8 件）")

    # --- 検証1：補正 JSON の件数（ブロック状態 46・アイテム 5・音 35・他 0 件）---
    expected_counts = {"blockstates": 46, "items": 5, "entities": 0, "sounds": 35,
                       "particles": 0, "blocks": 0, "menus": 0}
    for kind, expected in expected_counts.items():
        got = corrections.get(kind)
        if got is None:
            raise SystemExit(f"補正 JSON に {kind} が無い")
        if len(got) != expected:
            raise SystemExit(f"補正 {kind} の件数が違う：{len(got)} != {expected}")
        for to_id, from_id in got:
            if not (0 <= to_id <= MAX_754[kind] and 0 <= from_id <= MAX_755[kind]):
                raise SystemExit(f"補正 {kind} の範囲外の対：[{to_id}, {from_id}]")
    print("補正 JSON の件数を確認：ブロック状態 46・アイテム 5・音 35・他 0 件")

    # --- 検証2：invert(順向き)＋補正＝名前の真値（*_754_TO_755_* 表の展開）---
    for kind in KINDS:
        prefix = TABLE_PREFIX[kind]
        down_ranges = parse_int_table2(tables_text, prefix + "_754_RANGES")
        down_overrides = parse_int_table2(tables_text, prefix + "_754_OVERRIDES")
        truth_ranges = parse_int_table2(tables_text, prefix + "_754_TO_755_RANGES")
        truth_overrides = parse_int_table2(tables_text, prefix + "_754_TO_755_OVERRIDES")
        truth = expand(truth_ranges, truth_overrides, MAX_754[kind] + 1, FALLBACK_755[kind])
        corr = corrections[kind] if corrections[kind] else None
        got = invert(down_ranges, down_overrides,
                     MAX_755[kind] + 1, MAX_754[kind] + 1, FALLBACK_755[kind], corr)
        if got != truth:
            diffs = [i for i, (a, b) in enumerate(zip(got, truth)) if a != b]
            raise SystemExit(f"invert＋補正が名前の真値と違う（{kind}、{len(diffs)} 件）")
        print(f"invert＋補正＝名前の真値：{kind}（{len(truth)} 件）")
    # アイテムは invert()＋補正＝ITEM_REVERSE_754 の展開とも一致することを確認する。
    rev_ranges = parse_int_table2(tables_text, "ITEM_REVERSE_754_RANGES")
    rev_overrides = parse_int_table2(tables_text, "ITEM_REVERSE_754_OVERRIDES")
    rev = expand(rev_ranges, rev_overrides, MAX_754["items"] + 1, FALLBACK_755["items"])
    item_ranges = parse_int_table2(tables_text, "ITEM_754_RANGES")
    item_overrides = parse_int_table2(tables_text, "ITEM_754_OVERRIDES")
    item_inv = invert(item_ranges, item_overrides,
                      MAX_755["items"] + 1, MAX_754["items"] + 1,
                      FALLBACK_755["items"], corrections["items"])
    if item_inv != rev:
        raise SystemExit("invert(アイテム)＋補正が ITEM_REVERSE_754 と違う")
    print("invert(アイテム)＋補正＝ITEM_REVERSE_754 の展開（976 件）")

    # --- 検証3：バイオームは invert(補正あり)＝名前の真値 ---
    # 1.16.5 の 79 件はすべて 1.17 と同じ id・同じ名前。改名は無い。
    # 1.17 の新規 dripstone_caves（174）・lush_caves（175）は下向きで
    # 平原（1）へ落とす。反転で食い違うのは 0 件の見込みだが、755 と同じく
    # 差分から機械的に補正を作る（手入力しない）。
    bio_ranges = parse_int_table2(tables_text, "BIOME_754_RANGES")
    bio_overrides = parse_int_table2(tables_text, "BIOME_754_OVERRIDES")
    bio_truth = expand(parse_int_table2(tables_text, "BIOME_754_TO_755_RANGES"),
                       parse_int_table2(tables_text, "BIOME_754_TO_755_OVERRIDES"),
                       MAX_754["biomes"] + 1, FALLBACK_755["biomes"])
    bio_inv = invert(bio_ranges, bio_overrides,
                     MAX_755["biomes"] + 1, MAX_754["biomes"] + 1, FALLBACK_755["biomes"], None)
    bio_diffs = [(i, bio_truth[i]) for i in range(len(bio_truth)) if bio_inv[i] != bio_truth[i]]
    if len(bio_truth) != 174:
        raise SystemExit(f"バイオームの反転表が 174 件でない：{len(bio_truth)}")
    bio_inv_fixed = invert(bio_ranges, bio_overrides,
                           MAX_755["biomes"] + 1, MAX_754["biomes"] + 1,
                           FALLBACK_755["biomes"], bio_diffs)
    if bio_inv_fixed != bio_truth:
        raise SystemExit("invert(バイオーム)＋補正が名前の真値と違う")
    # 代表値：biomes-1.16.json から id を引いて恒等写像を確認する。
    # the_void・snowy_tundra・snowy_mountains・plains・mountains は
    # 順向き・反転とも恒等写像。dripstone_caves・lush_caves は 1.16.5 に
    # 無いため順向きは平原（1）へ。隙間の先頭 id 51 も平原（1）へ。
    # （meadow は 1.18 の新規バイオームで 1.16.5 には無いため代表値に入れない）
    biomes754 = json.loads(BIOMES116.read_text(encoding="utf-8"))
    bio_id = {e["name"]: e["id"] for e in biomes754}
    gaps = [i for i in range(174) if i not in set(bio_id.values())]
    bio_down = expand(bio_ranges, bio_overrides, MAX_755["biomes"] + 1, FALLBACK_754["biomes"])
    checks = []
    for name in ("the_void", "snowy_tundra", "snowy_mountains", "plains", "mountains"):
        bid = bio_id[name]
        checks.append((bio_down[bid], bid))
        checks.append((bio_truth[bid], bid))
    bio755 = json.loads((ROOT / "build/remap-data/biomes-1.17.json")
                        .read_text(encoding="utf-8"))
    bio755_id = {e["name"]: e["id"] for e in bio755}
    for name in ("dripstone_caves", "lush_caves"):
        checks.append((bio_down[bio755_id[name]], 1))
    checks.append((bio_down[gaps[0]], 1))
    checks.append((bio_truth[gaps[0]], 1))
    if any(got != want for got, want in checks):
        raise SystemExit(f"バイオームの真値がおかしい：{checks}")
    print(f"invert(バイオーム)＋補正＝名前の真値（174 件。補正 {len(bio_diffs)} 件・"
          f"代表値 {len(checks)} 件を確認。隙間の先頭 id {gaps[0]}→平原）")

    # --- 検証4：ダメージ種別・エンチャント・属性の展開 ---
    dmg = expand(parse_int_table2(tables_text, "DAMAGE_TYPE_754_RANGES"),
                 parse_int_table2(tables_text, "DAMAGE_TYPE_754_OVERRIDES"), 42, 16)
    dmg_rev = expand(parse_int_table2(tables_text, "DAMAGE_TYPE_754_TO_755_RANGES"),
                     parse_int_table2(tables_text, "DAMAGE_TYPE_754_TO_755_OVERRIDES"), 42, 16)
    if dmg != list(range(42)) or dmg_rev != list(range(42)):
        raise SystemExit("ダメージ種別が 42 件の恒等写像ではない")
    enc = expand(parse_int_table2(tables_text, "ENCHANTMENT_754_RANGES"),
                 parse_int_table2(tables_text, "ENCHANTMENT_754_OVERRIDES"), 38, -1)
    enc_rev = expand(parse_int_table2(tables_text, "ENCHANTMENT_REVERSE_754_RANGES"),
                     parse_int_table2(tables_text, "ENCHANTMENT_REVERSE_754_OVERRIDES"), 38, -1)
    if len(enc) != 38 or enc != list(range(38)):
        raise SystemExit(f"エンチャントの展開がおかしい：{enc[:10]}")
    if len(enc_rev) != 38 or enc_rev != list(range(38)):
        raise SystemExit(f"エンチャント逆向きの展開がおかしい：{enc_rev[:10]}")
    attr = expand(parse_int_table2(tables_text, "ATTRIBUTE_754_RANGES"),
                  parse_int_table2(tables_text, "ATTRIBUTE_754_OVERRIDES"), 13, -1)
    if attr != list(range(13)):
        raise SystemExit("属性が 13 件の恒等写像ではない")
    print("ダメージ種別 42 件恒等・エンチャント 38/38 件恒等・属性 13 件恒等を確認")

    # --- 検証5：754 側の代替先アンカーを vv-1.16.json から実測で確認 ---
    vv116 = json.loads(VV116.read_text(encoding="utf-8"))
    anchors = [
        (vv116["blockstates"][1], "stone"), (vv116["items"][1], "stone"),
        (vv116["entities"][59], "pig"),
        (vv116["sounds"][816], "block.stone.break"),
        (vv116["particles"][0], "ambient_entity_effect"),
        (vv116["blocks"][1], "stone"), (vv116["menus"][11], "crafting"),
    ]
    if any(got != want for got, want in anchors):
        raise SystemExit(f"754 側の代替先アンカーが違う：{anchors}")
    print("754 側の代替先アンカーを確認（stone 1・pig 59・block.stone.break 816・"
          "ambient 0・crafting 11・plains 1）")

    # --- 補正の Java テキストを生成する ---
    blockstate_corr_java = java_int2_array([tuple(p) for p in corrections["blockstates"]])
    sound_corr_java = java_int2_array([tuple(p) for p in corrections["sounds"]])
    # バイオームの補正は上の検証3で差分から作ったもの（手入力しない）。
    biome_corr_java = java_int2_array(bio_diffs)

    # --- 検証6：ディスパッチの連鎖文字列（手書き）とファイルの 755 分岐を突き合わせる ---
    # chain_down（8 メソッド）と mapSoundName：754 の連鎖は 755 分岐の
    # 外側に 755→754 段を 1 つ巻いたもの。mapEffect も同じ。
    # mapItemReverse だけは上向きのため、755 分岐の最も内側に
    # 754→755 段を 1 つ入れたもの。
    # 括弧の数も数える（手書きの連鎖は開き＝閉じでなければならない）。
    chain_down = {
        "mapBlockState": ("mapBlockState755To754",
                          "mapBlockState756To755(mapBlockState757To756(mapBlockState759To757(mapBlockState760To759(mapBlockState761To760(mapBlockState762To761(mapBlockState763To762(mapBlockState(id765))))))))"),
        "mapItem": ("mapItem755To754",
                    "mapItem756To755(mapItem757To756(mapItem759To757(mapItem760To759(mapItem761To760(mapItem762To761(mapItem763To762(mapItem(id765))))))))"),
        "mapEntityType": ("mapEntityType755To754",
                          "mapEntityType756To755(mapEntityType757To756(mapEntityType759To757(mapEntityType760To759(mapEntityType761To760(mapEntityType762To761(mapEntityType763To762(mapEntityType(id765))))))))"),
        "mapSound": ("mapSound755To754",
                     "mapSound756To755(mapSound757To756(mapSound759To757(mapSound760To759(mapSound761To760(mapSound762To761(mapSound763To762(mapSound(id765))))))))"),
        "mapParticle": ("mapParticle755To754",
                        "mapParticle756To755(mapParticle757To756(mapParticle759To757(mapParticle760To759(mapParticle761To760(mapParticle762To761(mapParticle763To762(mapParticle(id765))))))))"),
        "mapBlock": ("mapBlock755To754",
                     "mapBlock756To755(mapBlock757To756(mapBlock759To757(mapBlock760To759(mapBlock761To760(mapBlock762To761(mapBlock763To762(mapBlock(id765))))))))"),
        "mapMenu": ("mapMenu755To754",
                    "mapMenu756To755(mapMenu757To756(mapMenu759To757(mapMenu760To759(mapMenu761To760(mapMenu762To761(mapMenu763To762(mapMenu(id765))))))))"),
        "mapBlockEntityType": ("mapBlockEntityType755To754",
                               "mapBlockEntityType756To755(mapBlockEntityType757To756(mapBlockEntityType759To757(mapBlockEntityType760To759(mapBlockEntityType761To760(mapBlockEntityType762To761(mapBlockEntityType763To762(mapBlockEntityType(id765))))))))"),
    }
    chain_754 = {}
    for name, (wrapper, inner755) in chain_down.items():
        expr755 = read_dispatch_branch(remapper_text, name, name + "756To755(")
        if expr755 != inner755:
            raise SystemExit(f"dispatch の 755 分岐が想定と違う（{name}）: {expr755[:80]}")
        expr754 = f"{wrapper}({inner755})"
        if expr754.count("(") != expr754.count(")"):
            raise SystemExit(f"754 の連鎖の括弧が合わない（{name}）")
        chain_754[name] = expr754
    effect755 = read_dispatch_branch(remapper_text, "mapEffect", "mapEffect756To755(")
    if effect755 != ("mapEffect756To755(mapEffect757To756(mapEffect759To757(mapEffect(id765))))"):
        raise SystemExit(f"dispatch の 755 分岐が想定と違う（mapEffect）: {effect755[:80]}")
    effect754 = f"mapEffect755To754({effect755})"
    if effect754.count("(") != effect754.count(")"):
        raise SystemExit("754 の連鎖の括弧が合わない（mapEffect）")
    soundname755 = read_dispatch_branch(remapper_text, "mapSoundName", "mapSoundName756To755(")
    if soundname755 != ("mapSoundName756To755(mapSoundName757To756(mapSoundName759To757(mapSoundName760To759(mapSoundName761To760(mapSoundName762To761(mapSoundName763To762(mapSoundName(name))))))))"):
        raise SystemExit(f"dispatch の 755 分岐が想定と違う（mapSoundName）: {soundname755[:80]}")
    soundname754 = f"mapSoundName755To754({soundname755})"
    if soundname754.count("(") != soundname754.count(")"):
        raise SystemExit("754 の連鎖の括弧が合わない（mapSoundName）")
    itemrev755 = read_dispatch_branch(remapper_text, "mapItemReverse", "mapItemReverse(")
    itemrev754 = ("mapItemReverse(mapItem762To763(mapItem761To762(mapItem760To761(mapItem759To760"
                  "(mapItem757To759(mapItem756To757(mapItem755To756(mapItem754To755(id)))))))))")
    if itemrev754 != itemrev755.replace("mapItem755To756(id)",
                                        "mapItem755To756(mapItem754To755(id))"):
        raise SystemExit("mapItemReverse の 754 連鎖が 755 分岐の内側への追加と一致しない")
    if itemrev754.count("(") != itemrev754.count(")"):
        raise SystemExit("754 の連鎖の括弧が合わない（mapItemReverse）")
    print("ディスパッチの 754 連鎖を手書きし、755 分岐との突き合わせを確認（11 本）")

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
    insert_before("import static io.detergentmc.protocol.RegistryTables755.*;",
                  "import static io.detergentmc.protocol.RegistryTables754.*;\n",
                  "import RegistryTables754", "RegistryTables754.*")
    # 1b. ブロック状態の下向き表は別クラス（RegistryTables754BlockState）。
    # <clinit> 64KB 対策の分割（773／774／775 の *BlockState.java と同じ方式）。
    insert_after("import static io.detergentmc.protocol.RegistryTables754.*;\n",
                 "import static io.detergentmc.protocol.RegistryTables754BlockState.*;\n",
                 "import RegistryTables754BlockState", "RegistryTables754BlockState.*")

    # 2. PROTOCOL_754（PROTOCOL_756 の直前）
    insert_before("    /** 756（1.17.1）のプロトコル番号。 */",
                  "    /** 754（1.16.4／1.16.5）のプロトコル番号。 */\n"
                  "    public static final int PROTOCOL_754 = 754;\n",
                  "PROTOCOL_754", "PROTOCOL_754 = 754")

    # 3. 754定数（GENERIC_DAMAGE_TYPE_755 の直後）
    constants_754 = """
    /** 754側の最大の番号（ViaVersion mapping-1.16.json の件数−1。バイオームだけ id の最大値）。 */
    public static final int MAX_BLOCK_STATE_754 = 17111;
    public static final int MAX_ITEM_754 = 975;
    public static final int MAX_ENTITY_TYPE_754 = 107;
    public static final int MAX_SOUND_754 = 991;
    public static final int MAX_PARTICLE_754 = 71;
    public static final int MAX_BLOCK_754 = 762;
    public static final int MAX_MENU_754 = 23;
    /** 754側のエンチャントは 0〜37（38件。755と同一順序）。 */
    public static final int MAX_ENCHANTMENT_754 = 37;
    /** 754側のバイオームは 0〜173（79件。id は 0〜173 の非連番）。 */
    public static final int MAX_BIOME_754 = 173;
    /** 754側のダメージ種別は 0〜41（42件。762 の順序の恒等写像。実バニラで検証する）。 */
    public static final int MAX_DAMAGE_TYPE_754 = 41;
    /** 754側のブロックエンティティ種別は 0〜32（33件。755 の sculk_sensor（33）は無い）。 */
    public static final int MAX_BLOCK_ENTITY_754 = 32;
    /** 754側の属性は 0〜12（13件。755と同一順序）。 */
    public static final int MAX_ATTRIBUTE_754 = 12;
    /** 754側のエフェクトは 1〜32（32件。755と同一順序。1ベース）。 */
    public static final int MAX_EFFECT_754 = 32;

    /** 754側の代替先（mapping-1.16.json の番号。Speed だけ 1ベースで 1）。 */
    public static final int STONE_BLOCK_STATE_754 = 1;
    public static final int AIR_BLOCK_STATE_754 = 0;
    public static final int STONE_ITEM_754 = 1;
    public static final int PIG_ENTITY_TYPE_754 = 59;
    public static final int GENERIC_SOUND_754 = 816; // block.stone.break
    public static final int AMBIENT_PARTICLE_754 = 0; // ambient_entity_effect
    public static final int STONE_BLOCK_754 = 1;
    public static final int CRAFTING_MENU_754 = 11;
    public static final int SPEED_EFFECT_754 = 1;
    /** 754側の平原バイオーム（minecraft-data pc/1.16/biomes.json）。 */
    public static final int PLAINS_BIOME_754 = 1;
    /** 754側の generic ダメージ種別（762 と同一順序と仮定）。 */
    public static final int GENERIC_DAMAGE_TYPE_754 = 16;
"""
    insert_after("    public static final int GENERIC_DAMAGE_TYPE_755 = 16;\n",
                 constants_754, "754定数", "MAX_BLOCK_STATE_754")

    # 4. 754の展開済み表（756 ⇔ 755 の展開済み表の直前）
    maps_754 = """
    // ------------------------------------------------------------------
    // 755 ⇔ 754（1.17 ⇔ 1.16.4／1.16.5）の展開済み表。RegistryTables754
    // の区間＋上書きの表をクラスロード時に int[] へ展開する。
    // 順向き（755→754）は expand()、反転（754→755）は invert() で作る。
    // ブロック状態は 46 件・音は 35 件の補正が必要（first-wins の罠。
    // rail 系の waterlogged・銅系と金・鉄の代替・音の動詞のすり替え）。
    // アイテムだけは 766／777／755 の前例にならい、名前から作った既存の
    // 逆変換表（ITEM_REVERSE_754）を expand() でそのまま使う
    // （invert()＋補正でも同じ結果になることを書き込み前に確認済み）。
    // エフェクトは表を作らない（アクセサでハードコードする。754 と 755 は
    // どちらも 32件・同一順序のため恒等写像で、DROP は無い）。
    // ブロックエンティティ種別も表を作らない。754 は 33 件・755 は 34 件で
    // 恒等写像ではないため、アクセサで 33（sculk_sensor）→DROP とする。
    // 754 は合成表を作らない。
    // バイオームの反転表は 754 側 174 件（id 0〜173。隙間の id は平原）。
    // 754 の表は private static final class Maps754 にまとめる（RegistryRemapper の
    // static 初期化子は 64KB 制限に達しているため。755 追加後も外側は 64,669 バイト
    // のまま（残り 866 バイト）。Maps755 方式で外側は 0 バイト増）。
    // ------------------------------------------------------------------


    /** 755 ⇔ 754 の展開済み表をまとめたホルダ。RegistryRemapper の static 初期化子は
     * 64KB 制限に達しているため（755 追加後も外側は 64,669 バイト。残り 866 バイト）、754 の表の
     * 初期化はこの別クラスの static 初期化子に分けてある。754 以降も同じ方式にすること。
     * 初回アクセス時に初期化される（クラスロード時の初期化と等価。expand()／invert() は
     * 純粋関数）。アクセサからは Maps754.〜 で参照する。 */
    private static final class Maps754 {
        /** ブロック状態の反転の補正（754 id → 755 id）。46 件。rail 系
         * （powered_rail・detector_rail・activator_rail・rail）の
         * waterlogged=false の variants が invert() では 755 側の
         * waterlogged 付き状態にすり替わるため、正しい 755 側の
         * waterlogged なし状態へ補正する。
         * tools/maps754-corrections.json から生成。検証は
         * tools/verify-754-tables.py のチェックDを参照。 */
        private static final int[][] BLOCK_STATE_UP_754_CORRECTIONS = {
""" + blockstate_corr_java + """
        };

        /** 音の反転の補正（754 id → 755 id）。35 件。新しいブロックの音が
         * invert() では first-wins で近いブロックの音にすり替わるため、
         * 正しい 755 側の音へ補正する（glass／grass／snow／wool／stone／
         * item_frame／squid／sheep／bucket／minecart 系）。
         * tools/maps754-corrections.json から生成。検証は
         * tools/verify-754-tables.py のチェックDを参照。 */
        private static final int[][] SOUND_UP_754_CORRECTIONS = {
""" + sound_corr_java + """
        };

        /** バイオームの反転の補正（754 id → 755 id）。0 件。754 の 79 件は
         * すべて 755 と同じ id・同じ名前のため、invert() だけでも名前の
         * 真値と一致する。755 では 26 件の補正が必要だったが、754 には
         * 削除・改名が無いため当たらない。755 と同じ構造を保つために
         * 空の表を置く。
         * RegistryTables754.java の BIOME_754_TO_755_* 表との差分から生成。
         * 検証は tools/verify-754-tables.py のチェックGを参照。 */
        private static final int[][] BIOME_UP_754_CORRECTIONS = {
""" + biome_corr_java + """
        };

        /** 755のブロック状態ID → 754のブロック状態ID（RegistryTables754）。 */
        private static final int[] BLOCK_STATE_755_TO_754_MAP =
                expand(BLOCK_STATE_754_RANGES, BLOCK_STATE_754_OVERRIDES, MAX_BLOCK_STATE_755 + 1,
                        STONE_BLOCK_STATE_754);
        /** 754のブロック状態ID → 755のブロック状態ID（反転＋補正）。 */
        private static final int[] BLOCK_STATE_754_TO_755_MAP = invert(
                BLOCK_STATE_754_RANGES, BLOCK_STATE_754_OVERRIDES,
                MAX_BLOCK_STATE_755 + 1, MAX_BLOCK_STATE_754 + 1,
                STONE_BLOCK_STATE_755, BLOCK_STATE_UP_754_CORRECTIONS);
        /** 755のアイテムID → 754のアイテムID。 */
        private static final int[] ITEM_755_TO_754_MAP =
                expand(ITEM_754_RANGES, ITEM_754_OVERRIDES, MAX_ITEM_755 + 1, STONE_ITEM_754);
        /** 754のアイテムID → 755のアイテムID（名前から作った逆変換表）。 */
        private static final int[] ITEM_754_TO_755_MAP =
                expand(ITEM_REVERSE_754_RANGES, ITEM_REVERSE_754_OVERRIDES, MAX_ITEM_754 + 1,
                        STONE_ITEM_755);
        /** 755のエンティティ種別ID → 754のエンティティ種別ID。 */
        private static final int[] ENTITY_TYPE_755_TO_754_MAP =
                expand(ENTITY_TYPE_754_RANGES, ENTITY_TYPE_754_OVERRIDES, MAX_ENTITY_TYPE_755 + 1,
                        PIG_ENTITY_TYPE_754);
        /** 754のエンティティ種別ID → 755のエンティティ種別ID（反転）。 */
        private static final int[] ENTITY_TYPE_754_TO_755_MAP = invert(
                ENTITY_TYPE_754_RANGES, ENTITY_TYPE_754_OVERRIDES,
                MAX_ENTITY_TYPE_755 + 1, MAX_ENTITY_TYPE_754 + 1, PIG_ENTITY_TYPE_755, null);
        /** 755の音ID → 754の音ID。 */
        private static final int[] SOUND_755_TO_754_MAP =
                expand(SOUND_754_RANGES, SOUND_754_OVERRIDES, MAX_SOUND_755 + 1, GENERIC_SOUND_754);
        /** 754の音ID → 755の音ID（反転＋補正）。 */
        private static final int[] SOUND_754_TO_755_MAP = invert(
                SOUND_754_RANGES, SOUND_754_OVERRIDES,
                MAX_SOUND_755 + 1, MAX_SOUND_754 + 1,
                GENERIC_SOUND_755, SOUND_UP_754_CORRECTIONS);
        /** 755のパーティクルID → 754のパーティクルID。 */
        private static final int[] PARTICLE_755_TO_754_MAP =
                expand(PARTICLE_754_RANGES, PARTICLE_754_OVERRIDES, MAX_PARTICLE_755 + 1,
                        AMBIENT_PARTICLE_754);
        /** 754のパーティクルID → 755のパーティクルID（反転）。 */
        private static final int[] PARTICLE_754_TO_755_MAP = invert(
                PARTICLE_754_RANGES, PARTICLE_754_OVERRIDES,
                MAX_PARTICLE_755 + 1, MAX_PARTICLE_754 + 1,
                AMBIENT_PARTICLE_755, null);
        /** 755のブロックID → 754のブロックID。 */
        private static final int[] BLOCK_755_TO_754_MAP =
                expand(BLOCK_754_RANGES, BLOCK_754_OVERRIDES, MAX_BLOCK_755 + 1, STONE_BLOCK_754);
        /** 754のブロックID → 755のブロックID（反転）。 */
        private static final int[] BLOCK_754_TO_755_MAP = invert(
                BLOCK_754_RANGES, BLOCK_754_OVERRIDES,
                MAX_BLOCK_755 + 1, MAX_BLOCK_754 + 1, STONE_BLOCK_755, null);
        /** 755のメニュー種別ID → 754のメニュー種別ID。 */
        private static final int[] MENU_755_TO_754_MAP =
                expand(MENU_754_RANGES, MENU_754_OVERRIDES, MAX_MENU_755 + 1, CRAFTING_MENU_754);
        /** 754のメニュー種別ID → 755のメニュー種別ID（反転）。 */
        private static final int[] MENU_754_TO_755_MAP = invert(
                MENU_754_RANGES, MENU_754_OVERRIDES,
                MAX_MENU_755 + 1, MAX_MENU_754 + 1, CRAFTING_MENU_755, null);
        /** 755のバイオームID → 754のバイオームID。1.17 の新規
         * dripstone_caves（174）・lush_caves（175）は平原（1）へ落とす。 */
        private static final int[] BIOME_755_TO_754_MAP =
                expand(BIOME_754_RANGES, BIOME_754_OVERRIDES, MAX_BIOME_755 + 1, PLAINS_BIOME_754);
        /** 754のバイオームID → 755のバイオームID（反転＋補正。174 件）。 */
        private static final int[] BIOME_754_TO_755_MAP = invert(
                BIOME_754_RANGES, BIOME_754_OVERRIDES,
                MAX_BIOME_755 + 1, MAX_BIOME_754 + 1, PLAINS_BIOME_755,
                BIOME_UP_754_CORRECTIONS);
        /** ダメージ種別ID：755 → 754（762 の 42 件の恒等写像。1.16.5／1.17 の
         * どちらにも damage_type レジストリは無いため、ワイヤ上の呼び出し元は無い）。 */
        private static final int[] DAMAGE_TYPE_754_MAP = {
            0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19,
            20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32, 33, 34, 35, 36, 37,
            38, 39, 40, 41
        };
        /** ダメージ種別ID：754 → 755（762 の 42 件の恒等写像。呼び出し元は無い）。 */
        private static final int[] DAMAGE_TYPE_REVERSE_754_MAP = {
            0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19,
            20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32, 33, 34, 35, 36, 37,
            38, 39, 40, 41
        };
        /** 755の属性ID → 754の属性ID。並びは同一。範囲外は {@link #DROP}。 */
        private static final int[] ATTRIBUTE_754_MAP =
                expand(ATTRIBUTE_754_RANGES, ATTRIBUTE_754_OVERRIDES, MAX_ATTRIBUTE_755 + 1, DROP);
        /** 754の属性ID → 755の属性ID。並びは同一。範囲外は {@link #DROP}。 */
        private static final int[] ATTRIBUTE_REVERSE_754_MAP =
                expand(ATTRIBUTE_REVERSE_754_RANGES, ATTRIBUTE_REVERSE_754_OVERRIDES,
                        MAX_ATTRIBUTE_754 + 1, DROP);
        /** 755のエンチャントID → 754のエンチャントID。並びは同一。範囲外は {@link #DROP}。 */
        private static final int[] ENCHANTMENT_754_MAP =
                expand(ENCHANTMENT_754_RANGES, ENCHANTMENT_754_OVERRIDES,
                        MAX_ENCHANTMENT_755 + 1, DROP);
        /** 754のエンチャントID → 755のエンチャントID。並びは同一。範囲外は {@link #DROP}。 */
        private static final int[] ENCHANTMENT_REVERSE_754_MAP =
                expand(ENCHANTMENT_REVERSE_754_RANGES, ENCHANTMENT_REVERSE_754_OVERRIDES,
                        MAX_ENCHANTMENT_754 + 1, DROP);

        /** 755の音の名前 → 754の音の名前（Stop Sound 用）。数値の反転表と一致させる。 */
        private static final Map<String, String> SOUND_NAME_FALLBACK_755_TO_754_MAP =
                buildSoundNameMap(SOUND_NAME_FALLBACKS_755_TO_754);

        /** 754の音の名前 → 755の音の名前（Stop Sound 用）。改名 1 件のみ。 */
        private static final Map<String, String> SOUND_NAME_FALLBACK_754_TO_755_MAP =
                buildSoundNameMap(SOUND_NAME_FALLBACKS_754_TO_755);
    }
"""
    insert_before("    // ------------------------------------------------------------------\n"
                  "    // 756 ⇔ 755（1.17.1 ⇔ 1.17）の展開済み表。RegistryTables755\n",
                  maps_754, "754の展開済み表", "BLOCK_STATE_755_TO_754_MAP")

    # 5. アクセサメソッド（756 ⇔ 755 節の直前）
    methods_754 = """
    // ------------------------------------------------------------------
    // 755 ⇔ 754（1.17 ⇔ 1.16.4／1.16.5）
    // パケットIDは 755 と 754 で違う（Join Game 0x26→0x24、Chunk Data 0x22→0x20、
    // Respawn 0x3D→0x39 など。packet-ids-754.json の実測を参照）。
    // レジストリは RegistryTables754（ViaVersion mapping-1.16.json を
    // 実バニラ 1.16.5 の registries.json／reports で補正したものから
    // 名前で突き合わせたもの）。
    // 754↔763〜775 は隣接表の連鎖（754→755→756→757→759→760→761→762→763→合成表）
    // で到達する。合成表は作らない。
    // Join Game はフィールド構成が同一（実バニラ 1.16.5 の生キャプチャで確認。
    // 30,847 バイト。dimensionCodec は dimension_type 4 件・worldgen/biome 79 件。
    // 1.17 の 31,555 バイトとの差はバイオーム 2 件と dimension element の
    // min_y／height の有無。previousGameMode は 1.16.5 が u8・1.17 が i8 と
    // minecraft-data では違うが、ワイヤ上は同じ 1 バイト）。Respawn も同一。
    // ------------------------------------------------------------------

    /** 755のブロック状態ID → 754のブロック状態ID。範囲外はSTONE_BLOCK_STATE_754 に相当する代替。 */
    public static int mapBlockState755To754(int id755) {
        if (id755 < 0 || id755 >= Maps754.BLOCK_STATE_755_TO_754_MAP.length) {
            logUnknownComposedOnce("755→754", "ブロック状態", id755);
            return STONE_BLOCK_STATE_754;
        }
        return Maps754.BLOCK_STATE_755_TO_754_MAP[id755];
    }

    /** 754のブロック状態ID → 755のブロック状態ID。範囲外はSTONE_BLOCK_STATE_755 に相当する代替。 */
    public static int mapBlockState754To755(int id754) {
        if (id754 < 0 || id754 >= Maps754.BLOCK_STATE_754_TO_755_MAP.length) {
            logUnknownComposedOnce("754→755", "ブロック状態", id754);
            return STONE_BLOCK_STATE_755;
        }
        return Maps754.BLOCK_STATE_754_TO_755_MAP[id754];
    }

    /** 755のアイテムID → 754のアイテムID。範囲外はSTONE_ITEM_754 に相当する代替。 */
    public static int mapItem755To754(int id755) {
        if (id755 < 0 || id755 >= Maps754.ITEM_755_TO_754_MAP.length) {
            logUnknownComposedOnce("755→754", "アイテム", id755);
            return STONE_ITEM_754;
        }
        return Maps754.ITEM_755_TO_754_MAP[id755];
    }

    /** 754のアイテムID → 755のアイテムID。範囲外はSTONE_ITEM_755 に相当する代替。 */
    public static int mapItem754To755(int id754) {
        if (id754 < 0 || id754 >= Maps754.ITEM_754_TO_755_MAP.length) {
            logUnknownComposedOnce("754→755", "アイテム", id754);
            return STONE_ITEM_755;
        }
        return Maps754.ITEM_754_TO_755_MAP[id754];
    }

    /** 755のエンティティ種別ID → 754のエンティティ種別ID。範囲外はPIG_ENTITY_TYPE_754 に相当する代替。 */
    public static int mapEntityType755To754(int id755) {
        if (id755 < 0 || id755 >= Maps754.ENTITY_TYPE_755_TO_754_MAP.length) {
            logUnknownComposedOnce("755→754", "エンティティ種別", id755);
            return PIG_ENTITY_TYPE_754;
        }
        return Maps754.ENTITY_TYPE_755_TO_754_MAP[id755];
    }

    /** 754のエンティティ種別ID → 755のエンティティ種別ID。範囲外はPIG_ENTITY_TYPE_755 に相当する代替。 */
    public static int mapEntityType754To755(int id754) {
        if (id754 < 0 || id754 >= Maps754.ENTITY_TYPE_754_TO_755_MAP.length) {
            logUnknownComposedOnce("754→755", "エンティティ種別", id754);
            return PIG_ENTITY_TYPE_755;
        }
        return Maps754.ENTITY_TYPE_754_TO_755_MAP[id754];
    }

    /** 755の音ID → 754の音ID。範囲外はGENERIC_SOUND_754 に相当する代替。 */
    public static int mapSound755To754(int id755) {
        if (id755 < 0 || id755 >= Maps754.SOUND_755_TO_754_MAP.length) {
            logUnknownComposedOnce("755→754", "音", id755);
            return GENERIC_SOUND_754;
        }
        return Maps754.SOUND_755_TO_754_MAP[id755];
    }

    /** 754の音ID → 755の音ID。範囲外はGENERIC_SOUND_755 に相当する代替。 */
    public static int mapSound754To755(int id754) {
        if (id754 < 0 || id754 >= Maps754.SOUND_754_TO_755_MAP.length) {
            logUnknownComposedOnce("754→755", "音", id754);
            return GENERIC_SOUND_755;
        }
        return Maps754.SOUND_754_TO_755_MAP[id754];
    }

    /** 755のパーティクルID → 754のパーティクルID。範囲外はAMBIENT_PARTICLE_754 に相当する代替。 */
    public static int mapParticle755To754(int id755) {
        if (id755 < 0 || id755 >= Maps754.PARTICLE_755_TO_754_MAP.length) {
            logUnknownComposedOnce("755→754", "パーティクル", id755);
            return AMBIENT_PARTICLE_754;
        }
        return Maps754.PARTICLE_755_TO_754_MAP[id755];
    }

    /** 754のパーティクルID → 755のパーティクルID。範囲外はAMBIENT_PARTICLE_755 に相当する代替。 */
    public static int mapParticle754To755(int id754) {
        if (id754 < 0 || id754 >= Maps754.PARTICLE_754_TO_755_MAP.length) {
            logUnknownComposedOnce("754→755", "パーティクル", id754);
            return AMBIENT_PARTICLE_755;
        }
        return Maps754.PARTICLE_754_TO_755_MAP[id754];
    }

    /** 755のブロックID → 754のブロックID。範囲外はSTONE_BLOCK_754 に相当する代替。 */
    public static int mapBlock755To754(int id755) {
        if (id755 < 0 || id755 >= Maps754.BLOCK_755_TO_754_MAP.length) {
            logUnknownComposedOnce("755→754", "ブロック", id755);
            return STONE_BLOCK_754;
        }
        return Maps754.BLOCK_755_TO_754_MAP[id755];
    }

    /** 754のブロックID → 755のブロックID。範囲外はSTONE_BLOCK_755 に相当する代替。 */
    public static int mapBlock754To755(int id754) {
        if (id754 < 0 || id754 >= Maps754.BLOCK_754_TO_755_MAP.length) {
            logUnknownComposedOnce("754→755", "ブロック", id754);
            return STONE_BLOCK_755;
        }
        return Maps754.BLOCK_754_TO_755_MAP[id754];
    }

    /** 755のメニュー種別ID → 754のメニュー種別ID。範囲外はCRAFTING_MENU_754 に相当する代替。 */
    public static int mapMenu755To754(int id755) {
        if (id755 < 0 || id755 >= Maps754.MENU_755_TO_754_MAP.length) {
            logUnknownComposedOnce("755→754", "メニュー", id755);
            return CRAFTING_MENU_754;
        }
        return Maps754.MENU_755_TO_754_MAP[id755];
    }

    /** 754のメニュー種別ID → 755のメニュー種別ID。範囲外はCRAFTING_MENU_755 に相当する代替。 */
    public static int mapMenu754To755(int id754) {
        if (id754 < 0 || id754 >= Maps754.MENU_754_TO_755_MAP.length) {
            logUnknownComposedOnce("754→755", "メニュー", id754);
            return CRAFTING_MENU_755;
        }
        return Maps754.MENU_754_TO_755_MAP[id754];
    }

    /**
     * 755のブロックエンティティ種別ID → 754のブロックエンティティ種別ID。
     * 754 は 33 件・755 は 34 件。0〜32 は恒等写像。33（sculk_sensor）は
     * 1.16.5 には無いため DROP。
     */
    public static int mapBlockEntityType755To754(int id755) {
        if (id755 < 0 || id755 > MAX_BLOCK_ENTITY_755) {
            logUnknownComposedOnce("755→754", "ブロックエンティティ種別", id755);
            return DROP;
        }
        if (id755 == 33) { // sculk_sensor は 1.16.5 には無い
            return DROP;
        }
        return id755;
    }

    /**
     * 754のブロックエンティティ種別ID → 755のブロックエンティティ種別ID。
     * 0〜32 は恒等写像。
     */
    public static int mapBlockEntityType754To755(int id754) {
        if (id754 < 0 || id754 > MAX_BLOCK_ENTITY_754) {
            logUnknownComposedOnce("754→755", "ブロックエンティティ種別", id754);
            return DROP;
        }
        return id754;
    }

    /** 755のバイオームID → 754のバイオームID。範囲外はPLAINS_BIOME_754 に相当する代替。 */
    public static int mapBiome755To754(int id755) {
        if (id755 < 0 || id755 >= Maps754.BIOME_755_TO_754_MAP.length) {
            logUnknownComposedOnce("755→754", "バイオーム", id755);
            return PLAINS_BIOME_754;
        }
        return Maps754.BIOME_755_TO_754_MAP[id755];
    }

    /** 754のバイオームID → 755のバイオームID。範囲外はPLAINS_BIOME_755 に相当する代替。 */
    public static int mapBiome754To755(int id754) {
        if (id754 < 0 || id754 >= Maps754.BIOME_754_TO_755_MAP.length) {
            logUnknownComposedOnce("754→755", "バイオーム", id754);
            return PLAINS_BIOME_755;
        }
        return Maps754.BIOME_754_TO_755_MAP[id754];
    }

    /** 755のダメージ種別ID → 754のダメージ種別ID（恒等写像。ただし1.16.5／1.17 の codec に
     * damage_type レジストリは存在しないため、ワイヤ上の呼び出し元は無い）。 */
    public static int mapDamageType755To754(int id755) {
        if (id755 < 0 || id755 > MAX_DAMAGE_TYPE_755) {
            logUnknownOnce(PROTOCOL_754, "ダメージ種別", id755);
            return DROP;
        }
        return Maps754.DAMAGE_TYPE_754_MAP[id755];
    }

    /** 754のダメージ種別ID → 755のダメージ種別ID（恒等写像。ワイヤ上の呼び出し元は無い）。 */
    public static int mapDamageType754To755(int id754) {
        if (id754 < 0 || id754 > MAX_DAMAGE_TYPE_754) {
            logUnknownOnce(PROTOCOL_755, "ダメージ種別", id754);
            return DROP;
        }
        return Maps754.DAMAGE_TYPE_REVERSE_754_MAP[id754];
    }

    /**
     * 755のエフェクトID → 754のエフェクトID。754 と 755 は 32件・同一順序
     * のため恒等写像。範囲外は Speed（1）。
     */
    public static int mapEffect755To754(int id755) {
        if (id755 < 0 || id755 > MAX_EFFECT_755) {
            logUnknownComposedOnce("755→754", "エフェクト", id755);
            return SPEED_EFFECT_754;
        }
        return id755;
    }

    /**
     * 754のエフェクトID → 755のエフェクトID。1〜32 は恒等写像。
     * 範囲外は Speed（1）。
     */
    public static int mapEffect754To755(int id754) {
        if (id754 < 0 || id754 > MAX_EFFECT_754) {
            logUnknownComposedOnce("754→755", "エフェクト", id754);
            return SPEED_EFFECT_755;
        }
        return id754;
    }

    /** 755の属性ID → 754の属性ID。並びは同一。範囲外は {@link #DROP}。 */
    public static int mapAttribute755To754(int id755) {
        if (id755 < 0 || id755 > MAX_ATTRIBUTE_755) {
            logUnknownOnce(PROTOCOL_754, "属性", id755);
            return DROP;
        }
        return Maps754.ATTRIBUTE_754_MAP[id755];
    }

    /** 754の属性ID → 755の属性ID。並びは同一。範囲外は {@link #DROP}。 */
    public static int mapAttribute754To755(int id754) {
        if (id754 < 0 || id754 > MAX_ATTRIBUTE_754) {
            logUnknownOnce(PROTOCOL_755, "属性", id754);
            return DROP;
        }
        return Maps754.ATTRIBUTE_REVERSE_754_MAP[id754];
    }

    /** 754の属性ID → 属性の名前（Update Attributes は 1.16.5 では名前形式のため）。 */
    public static String attributeName754(int id754) {
        if (id754 < 0 || id754 >= ATTRIBUTE_754_NAMES.length) {
            return null;
        }
        return ATTRIBUTE_754_NAMES[id754];
    }

    /**
     * 属性の名前 → 754の属性ID（765／763／764 は属性が名前形式のため、
     * 754向けに変換するときに使う）。無い名前は {@link #DROP}。
     */
    public static int attributeId754(String name) {
        if (name == null) {
            return DROP;
        }
        String plain = stripMinecraftPrefix(name);
        for (int i = 0; i < ATTRIBUTE_754_NAMES.length; i++) {
            if (ATTRIBUTE_754_NAMES[i].equals(plain)) {
                return i;
            }
        }
        return DROP;
    }

    /** 755のエンチャントID → 754のエンチャントID。並びは同一。範囲外は {@link #DROP}。 */
    public static int mapEnchantment755To754(int id755) {
        if (id755 < 0 || id755 > MAX_ENCHANTMENT_755) {
            logUnknownOnce(PROTOCOL_754, "エンチャント", id755);
            return DROP;
        }
        return Maps754.ENCHANTMENT_754_MAP[id755];
    }

    /** 754のエンチャントID → 755のエンチャントID。並びは同一。範囲外は {@link #DROP}。 */
    public static int mapEnchantment754To755(int id754) {
        if (id754 < 0 || id754 > MAX_ENCHANTMENT_754) {
            logUnknownOnce(PROTOCOL_755, "エンチャント", id754);
            return DROP;
        }
        return Maps754.ENCHANTMENT_REVERSE_754_MAP[id754];
    }

    /** 755の音の名前 → 754の音の名前（Stop Sound 用）。数値の反転表と一致させる。 */
    public static String mapSoundName755To754(String name) {
        return mapSoundNameComposed(name, Maps754.SOUND_NAME_FALLBACK_755_TO_754_MAP);
    }

    /** 754の音の名前 → 755の音の名前（Stop Sound 用）。改名 1 件のみ。 */
    public static String mapSoundName754To755(String name) {
        return mapSoundNameComposed(name, Maps754.SOUND_NAME_FALLBACK_754_TO_755_MAP);
    }
"""
    insert_before("    // ------------------------------------------------------------------\n"
                  "    // 756 ⇔ 755（1.17.1 ⇔ 1.17）\n",
                  methods_754, "754アクセサ", "mapBlockState755To754")

    # 6. ディスパッチのコメント更新（754の連鎖を追記）
    # 注意：756 のスクリプトは old→new の replace を使っていたが、new が
    # old を部分文字列として含むため再実行のたびに 756 の行が増殖した
    # （RegistryRemapper.java の dispatch コメントに 756 の行が 5 回ある）。
    # 754 ではマーカー付きの insert_after で 1 回だけ追加する。
    dispatch_754_line = ("    // 754は755の表を経由する（754↔763〜775は"
                         "754→755→756→757→759→760→761→762→763→合成表の連鎖）。\n")
    insert_after("    // 755は756の表を経由する（755↔763〜775は"
                 "755→756→757→759→760→761→762→763→合成表の連鎖）。\n",
                 dispatch_754_line, "dispatch コメント 754",
                 "754は755の表を経由する")

    # 7. target 分岐への 754 追加（各 dispatch メソッドの 755 分岐の直前。
    # insert_after はメソッド冒頭の直後に入れるため、755 分岐より前に来る。
    # 下向きは 755 分岐の外側に 755→754 段を 1 つ巻く。
    # 括弧の数：755 分岐は 8 個の開き／8 個の閉じ、巻いた後は 9 個／9 個。
    # mapEffect だけは 755 分岐が 4 個／4 個で、巻いた後は 5 個／5 個。
    # 手書きした連鎖は上の検証6でファイルの 755 分岐と突き合わせてある。）
    for name, expr754 in chain_754.items():
        body = (f"        if (clientProtocol == PROTOCOL_754) {{\n"
                f"            return {expr754};\n"
                f"        }}\n")
        anchor = f"    public static int {name}(int id765, int clientProtocol) {{\n"
        marker = f"return {expr754};"
        insert_after(anchor, body, f"dispatch {name} 754", marker)

    # mapItemReverse（引数名が id）。上向き（クライアント→765）のため、
    # 新しい隣接段（754→755）は最も内側（最初に適用）に入れる。
    # 下向き（mapItem 等）とは逆で、外側に巻くのは誤り。
    # 注意：757／759／760／761／762 の分岐は外側に巻く形で作られており、
    # 隣接表を逆順に合成している（実測：757 分岐は正しい合成と 724/1101 件
    # 食い違う。例：tropical_fish_spawn_egg 757 id 926 の正しい 765 id は
    # 1067 だが 757 分岐は 1066 を返す。§12.5 参照）。756／755 分岐は
    # 正しい順序。754 分岐も正しい順序で合成する。他分岐の修正は
    # 統括の判断待ち。
    insert_after("    public static int mapItemReverse(int id, int clientProtocol) {\n",
                 "        if (clientProtocol == PROTOCOL_754) {\n"
                 f"            return {itemrev754};\n"
                 "        }\n",
                 "dispatch mapItemReverse 754",
                 f"return {itemrev754};")

    # mapEffect（755→754 は恒等写像。755 分岐の外側にそのままつなぐ）
    insert_after("    public static int mapEffect(int id765, int clientProtocol) {\n",
                 "        if (clientProtocol == PROTOCOL_754) {\n"
                 f"            return {effect754};\n"
                 "        }\n",
                 "dispatch mapEffect 754",
                 f"return {effect754};")

    # mapSoundName
    insert_after("    public static String mapSoundName(String name, int clientProtocol) {\n",
                 "        if (clientProtocol == PROTOCOL_754) {\n"
                 f"            return {soundname754};\n"
                 "        }\n",
                 "dispatch mapSoundName 754",
                 f"return {soundname754};")

    REMAPPER.write_text(src, encoding="utf-8")
    print("RegistryRemapper.java に 754 を差し込んだ。")


if __name__ == "__main__":
    raise SystemExit(main())
