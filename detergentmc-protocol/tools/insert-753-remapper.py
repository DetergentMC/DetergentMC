#!/usr/bin/env python3
"""RegistryRemapper.java に 753（1.16.3）対応を差し込む。

雛形は tools/insert-754-remapper.py（隣接連鎖の配線＋書き込み前の
全入力検証・冪等ガード）。アンカー付き挿入のみ行う。アンカーが
見つからなければ失敗して何も書かない。
使い方: python3 insert-753-remapper.py
書き換えた後は必ず git diff で内容を確認してから javac で確認すること。

配線の方針（STAGE-A-753-NOTES.md §11.8 の申し送り）:
- 753 は合成表を作らない。753↔763〜775 は 753→754→755→756→757→759→760→
  761→762→763→合成表の連鎖で到達する（754〜762 と同じ隣接連鎖グループ）。
- 順向き（754→753）の展開済み表は RegistryTables753 の *_753_* 表を
  expand() で展開する。ブロック状態の下向き表（BLOCK_STATE_753_RANGES・
  BLOCK_STATE_753_OVERRIDES）だけは別クラス RegistryTables753BlockState
  に分けてある（<clinit> 64KB 対策。773／774／775 の *BlockState.java
  と同じ方式。import も 2 行になる）。
- 反転（753→754）の表は invert() で作る。補正は maps753-corrections.json
  から：全種 0 件（754 と 753 のレジストリ内容は同一のため、invert() の
  first-wins と名前の真値の食い違いは無い）。アイテムは invert()＋補正でも
  名前の真値と一致するが、766／777／754 の前例にならい、名前から作った
  既存の逆変換表（ITEM_REVERSE_753）を expand() でそのまま使う。
- エフェクトは表を作らずハードコードする（mapEffectUp774 の前例）。
  753 と 754 は 32件・同一順序のため恒等写像（DROP なし）。
- ブロックエンティティも表を作らない。753 は 33 件・754 も 33 件で
  同一順序のため恒等写像（DROP なし。754 と違い sculk_sensor のような
  片方だけの種別は無い）。
- ダメージ種別は 42 件の恒等写像のプレースホルダ（DAMAGE_TYPE_753_MAP／
  DAMAGE_TYPE_REVERSE_753_MAP。754 の DAMAGE_TYPE_754 と同じ扱い。
  1.16.3／1.16.5 のどちらにも damage_type レジストリは無いため、
  ワイヤ上の呼び出し元は無い）。
- 753 の展開済み表は private static final class Maps753 にまとめる。
  RegistryRemapper の static 初期化子は 64KB 制限に達しているため
  （754 追加後も外側は 64,669 バイト。残り 866 バイト）、753 の表を外側の
  static 初期化子に入れると code too large でコンパイルできない。
  753 以降も同じ方式にすること。
- バイオーム：順向き表（BIOME_753）は 754 側 174 件（id 0〜173）、反転表
  （BIOME_753_TO_754）は 753 側 174 件（id 0〜173。隙間の id は平原）。
  1.16.3 の 79 件はすべて 1.16.5 と同じ id・同じ名前。改名は無い。
  新規バイオームは無いため、順向き・反転とも隙間の id だけが平原（1）へ落ちる。
  反転の補正は RegistryTables753.java の BIOME_753_TO_754_* 表との差分から
  このスクリプトが機械的に作る（754 と同じ仕組み。0 件の見込み）。

書き込み前の検証（失敗したら何も書き込まない）:
- maps753-corrections.json の件数（全種 0 件）。各対が範囲内であることも確認する。
- RegistryTables753.java から読んだ表で、Java の expand()／invert() と
  同じ計算を Python で行い、invert(順向き)＋補正＝名前の真値
  （*_753_TO_754_* 表の展開）と全 7 種で一致することを確認する。
  アイテムは invert()＋補正＝ITEM_REVERSE_753 の展開とも一致することを確認する。
  バイオームは invert() と名前の真値の差分を補正として作り、
  invert(順向き)＋補正＝名前の真値（174 件）を確認する。
  パーティクルの invert() の fallback は wiring と同じ
  AMBIENT_PARTICLE_754（0）を使う（生成スクリプトは entity_effect
  （20）を使っているが、補正を当てた後は -1 が残らないため値は同じ。
  754 の前例と同じ扱い）。
- ダメージ種別・エンチャント・属性の展開結果の件数と代表値を
  確認する。753 側の代替先アンカー（pig＝59、block.stone.break＝816、
  ambient_entity_effect＝0、crafting＝11、stone＝1、plains＝1）を
  vv-1.16.3.json から実測で確認する。
- ディスパッチの連鎖文字列は手書きする。書き込み前に現在の
  RegistryRemapper.java の 754 分岐の式を読み、753 の連鎖が
  「754 分岐の外側に 754→753 段を 1 つ巻いたもの」（mapItemReverse
  だけは「754 分岐の最も内側に 753→754 段を 1 つ入れたもの」）と
  一致することを確認する。機械変換では段が 1 つ消える罠があるため
  （754 §12.5 参照）、手書きした文字列とファイルの 754 分岐を
  突き合わせてから書く。
"""
from pathlib import Path
import json
import re
import sys

ROOT = Path(__file__).resolve().parent.parent
REMAPPER = ROOT / "src/main/java/io/detergentmc/protocol/RegistryRemapper.java"
TABLES = ROOT / "src/main/java/io/detergentmc/protocol/RegistryTables753.java"
# ブロック状態の下向き表だけ別クラスに分けてある（<clinit> 64KB 対策。
# 773／774／775 の *BlockState.java と同じ方式）。検証は両ファイルの
# 表を合わせて読む。
TABLES_BLOCKSTATE = ROOT / "src/main/java/io/detergentmc/protocol/RegistryTables753BlockState.java"
CORRECTIONS_JSON = ROOT / "build/remap-data/maps753-corrections.json"
VV1163 = ROOT / "build/remap-data/vv-1.16.3.json"
BIOMES116 = ROOT / "build/remap-data/biomes-1.16.json"

# 754側の最大ID（RegistryRemapper.java の MAX_*_754 定数と照合する）。
MAX_754 = {
    "blockstates": 17111, "items": 975, "entities": 107, "sounds": 991,
    "particles": 71, "blocks": 762, "menus": 23, "biomes": 173,
}
# 753側の最大ID（vv-1.16.json 等の実測件数−1。バイオームだけ id の最大値 173）。
MAX_753 = {
    "blockstates": 17111, "items": 975, "entities": 107, "sounds": 991,
    "particles": 71, "blocks": 762, "menus": 23, "biomes": 173,
}
# 754側の代替先（invert() の fallback。gen-753-tables.py の UP_753_FALLBACKS
# と同じ。ただしパーティクルだけ wiring では AMBIENT_PARTICLE_754（0）
# を使う。生成スクリプトの entity_effect（20）とは違うが、補正を当てた
# 後は -1 が残らないため結果は同じ。754 の前例と同じ扱い）。
FALLBACK_754 = {
    "blockstates": 1, "items": 1, "entities": 59, "sounds": 816,
    "particles": 0, "blocks": 1, "menus": 11, "biomes": 1,
}
# 753側の代替先（expand() の fallback。vv-1.16.3.json で実測した値）。
FALLBACK_753 = {
    "blockstates": 1, "items": 1, "entities": 59, "sounds": 816,
    "particles": 0, "blocks": 1, "menus": 11, "biomes": 1,
}
# 表名（RegistryTables753.java）。down は 754→753、up は 753→754。
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
    """dispatch メソッドの 754 分岐の return 式を読む（検証用）。
    753 分岐の挿入後（754 分岐が 2 番目以降）に再実行しても読めるよう、
    メソッド冒頭にアンカーせずファイル全体から 754 分岐を探す。
    prefix は return 式の先頭（例：mapBlock755To754(）。mapBlock と
    mapBlockState のように名前が接頭辞の関係にあるメソッドを区別するため、
    メソッド名だけでなく 755 への隣接段まで含める。"""
    m = re.search(
        r"if \(clientProtocol == PROTOCOL_754\) \{\n"
        r"            return (" + re.escape(prefix) + r".*?);\n"
        r"        \}",
        src, re.DOTALL)
    if m is None:
        raise SystemExit(f"dispatch の 754 分岐が見つからない: {method_name}")
    return m.group(1)


def main():
    tables_text = (TABLES.read_text(encoding="utf-8") + "\n"
                   + TABLES_BLOCKSTATE.read_text(encoding="utf-8"))
    corrections = json.loads(CORRECTIONS_JSON.read_text(encoding="utf-8"))
    remapper_text = REMAPPER.read_text(encoding="utf-8")

    # --- 検証0：既存の MAX_*_754 定数が想定値と一致する ---
    const_name = {
        "blockstates": "MAX_BLOCK_STATE_754", "items": "MAX_ITEM_754",
        "entities": "MAX_ENTITY_TYPE_754", "sounds": "MAX_SOUND_754",
        "particles": "MAX_PARTICLE_754", "blocks": "MAX_BLOCK_754",
        "menus": "MAX_MENU_754", "biomes": "MAX_BIOME_754",
    }
    for kind, expected in MAX_754.items():
        m = re.search(r"public static final int " + const_name[kind] + r" = (\d+);",
                      remapper_text)
        if m is None:
            raise SystemExit(f"既存の定数が見つからない: {const_name[kind]}")
        if int(m.group(1)) != expected:
            raise SystemExit(f"既存の {const_name[kind]} が違う：{m.group(1)} != {expected}")
    print("既存の MAX_*_754 定数を確認（8 件）")

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
            if not (0 <= to_id <= MAX_753[kind] and 0 <= from_id <= MAX_754[kind]):
                raise SystemExit(f"補正 {kind} の範囲外の対：[{to_id}, {from_id}]")
    print("補正 JSON の件数を確認：全種 0 件")

    # --- 検証2：invert(順向き)＋補正＝名前の真値（*_753_TO_754_* 表の展開）---
    for kind in KINDS:
        prefix = TABLE_PREFIX[kind]
        down_ranges = parse_int_table2(tables_text, prefix + "_753_RANGES")
        down_overrides = parse_int_table2(tables_text, prefix + "_753_OVERRIDES")
        truth_ranges = parse_int_table2(tables_text, prefix + "_753_TO_754_RANGES")
        truth_overrides = parse_int_table2(tables_text, prefix + "_753_TO_754_OVERRIDES")
        truth = expand(truth_ranges, truth_overrides, MAX_753[kind] + 1, FALLBACK_754[kind])
        corr = corrections[kind] if corrections[kind] else None
        got = invert(down_ranges, down_overrides,
                     MAX_754[kind] + 1, MAX_753[kind] + 1, FALLBACK_754[kind], corr)
        if got != truth:
            diffs = [i for i, (a, b) in enumerate(zip(got, truth)) if a != b]
            raise SystemExit(f"invert＋補正が名前の真値と違う（{kind}、{len(diffs)} 件）")
        print(f"invert＋補正＝名前の真値：{kind}（{len(truth)} 件）")
    # アイテムは invert()＋補正＝ITEM_REVERSE_753 の展開とも一致することを確認する。
    rev_ranges = parse_int_table2(tables_text, "ITEM_REVERSE_753_RANGES")
    rev_overrides = parse_int_table2(tables_text, "ITEM_REVERSE_753_OVERRIDES")
    rev = expand(rev_ranges, rev_overrides, MAX_753["items"] + 1, FALLBACK_754["items"])
    item_ranges = parse_int_table2(tables_text, "ITEM_753_RANGES")
    item_overrides = parse_int_table2(tables_text, "ITEM_753_OVERRIDES")
    item_inv = invert(item_ranges, item_overrides,
                      MAX_754["items"] + 1, MAX_753["items"] + 1,
                      FALLBACK_754["items"], corrections["items"])
    if item_inv != rev:
        raise SystemExit("invert(アイテム)＋補正が ITEM_REVERSE_753 と違う")
    print("invert(アイテム)＋補正＝ITEM_REVERSE_753 の展開（976 件）")

    # --- 検証3：バイオームは invert(補正あり)＝名前の真値 ---
    # 1.16.3 の 79 件はすべて 1.16.5 と同じ id・同じ名前。改名は無い。
    # 新規バイオームは無いため、下向きで平原（1）へ落ちるのは隙間の id だけ。
    # 反転で食い違うのは 0 件の見込みだが、754 と同じく
    # 差分から機械的に補正を作る（手入力しない）。
    bio_ranges = parse_int_table2(tables_text, "BIOME_753_RANGES")
    bio_overrides = parse_int_table2(tables_text, "BIOME_753_OVERRIDES")
    bio_truth = expand(parse_int_table2(tables_text, "BIOME_753_TO_754_RANGES"),
                       parse_int_table2(tables_text, "BIOME_753_TO_754_OVERRIDES"),
                       MAX_753["biomes"] + 1, FALLBACK_754["biomes"])
    bio_inv = invert(bio_ranges, bio_overrides,
                     MAX_754["biomes"] + 1, MAX_753["biomes"] + 1, FALLBACK_754["biomes"], None)
    bio_diffs = [(i, bio_truth[i]) for i in range(len(bio_truth)) if bio_inv[i] != bio_truth[i]]
    if len(bio_truth) != 174:
        raise SystemExit(f"バイオームの反転表が 174 件でない：{len(bio_truth)}")
    bio_inv_fixed = invert(bio_ranges, bio_overrides,
                           MAX_754["biomes"] + 1, MAX_753["biomes"] + 1,
                           FALLBACK_754["biomes"], bio_diffs)
    if bio_inv_fixed != bio_truth:
        raise SystemExit("invert(バイオーム)＋補正が名前の真値と違う")
    # 代表値：biomes-1.16.json から id を引いて恒等写像を確認する。
    # the_void・snowy_tundra・snowy_mountains・plains・mountains は
    # 順向き・反転とも恒等写像。隙間の先頭 id 51 は平原（1）へ。
    # （meadow は 1.18 の新規バイオームで 1.16.3 には無いため代表値に入れない）
    biomes753 = json.loads(BIOMES116.read_text(encoding="utf-8"))
    bio_id = {e["name"]: e["id"] for e in biomes753}
    gaps = [i for i in range(174) if i not in set(bio_id.values())]
    bio_down = expand(bio_ranges, bio_overrides, MAX_754["biomes"] + 1, FALLBACK_753["biomes"])
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
    dmg = expand(parse_int_table2(tables_text, "DAMAGE_TYPE_753_RANGES"),
                 parse_int_table2(tables_text, "DAMAGE_TYPE_753_OVERRIDES"), 42, 16)
    dmg_rev = expand(parse_int_table2(tables_text, "DAMAGE_TYPE_753_TO_754_RANGES"),
                     parse_int_table2(tables_text, "DAMAGE_TYPE_753_TO_754_OVERRIDES"), 42, 16)
    if dmg != list(range(42)) or dmg_rev != list(range(42)):
        raise SystemExit("ダメージ種別が 42 件の恒等写像ではない")
    enc = expand(parse_int_table2(tables_text, "ENCHANTMENT_753_RANGES"),
                 parse_int_table2(tables_text, "ENCHANTMENT_753_OVERRIDES"), 38, -1)
    enc_rev = expand(parse_int_table2(tables_text, "ENCHANTMENT_REVERSE_753_RANGES"),
                     parse_int_table2(tables_text, "ENCHANTMENT_REVERSE_753_OVERRIDES"), 38, -1)
    if len(enc) != 38 or enc != list(range(38)):
        raise SystemExit(f"エンチャントの展開がおかしい：{enc[:10]}")
    if len(enc_rev) != 38 or enc_rev != list(range(38)):
        raise SystemExit(f"エンチャント逆向きの展開がおかしい：{enc_rev[:10]}")
    attr = expand(parse_int_table2(tables_text, "ATTRIBUTE_753_RANGES"),
                  parse_int_table2(tables_text, "ATTRIBUTE_753_OVERRIDES"), 13, -1)
    if attr != list(range(13)):
        raise SystemExit("属性が 13 件の恒等写像ではない")
    print("ダメージ種別 42 件恒等・エンチャント 38/38 件恒等・属性 13 件恒等を確認")

    # --- 検証5：753 側の代替先アンカーを vv-1.16.json から実測で確認 ---
    vv1163 = json.loads(VV1163.read_text(encoding="utf-8"))
    anchors = [
        (vv1163["blockstates"][1], "stone"), (vv1163["items"][1], "stone"),
        (vv1163["entities"][59], "pig"),
        (vv1163["sounds"][816], "block.stone.break"),
        (vv1163["particles"][0], "ambient_entity_effect"),
        (vv1163["blocks"][1], "stone"), (vv1163["menus"][11], "crafting"),
    ]
    if any(got != want for got, want in anchors):
        raise SystemExit(f"753 側の代替先アンカーが違う：{anchors}")
    print("753 側の代替先アンカーを確認（stone 1・pig 59・block.stone.break 816・"
          "ambient 0・crafting 11・plains 1）")

    # --- 補正の Java テキストを生成する ---
    blockstate_corr_java = java_int2_array([tuple(p) for p in corrections["blockstates"]])
    sound_corr_java = java_int2_array([tuple(p) for p in corrections["sounds"]])
    # バイオームの補正は上の検証3で差分から作ったもの（手入力しない）。
    biome_corr_java = java_int2_array(bio_diffs)

    # --- 検証6：ディスパッチの連鎖文字列（手書き）とファイルの 754 分岐を突き合わせる ---
    # chain_down（8 メソッド）と mapSoundName：753 の連鎖は 754 分岐の
    # 外側に 754→753 段を 1 つ巻いたもの。mapEffect も同じ。
    # mapItemReverse だけは上向きのため、754 分岐の最も内側に
    # 753→754 段を 1 つ入れたもの。
    # 括弧の数も数える（手書きの連鎖は開き＝閉じでなければならない）。
    chain_down = {
        "mapBlockState": ("mapBlockState754To753",
                          "mapBlockState755To754(mapBlockState756To755(mapBlockState757To756(mapBlockState759To757(mapBlockState760To759(mapBlockState761To760(mapBlockState762To761(mapBlockState763To762(mapBlockState(id765)))))))))"),
        "mapItem": ("mapItem754To753",
                    "mapItem755To754(mapItem756To755(mapItem757To756(mapItem759To757(mapItem760To759(mapItem761To760(mapItem762To761(mapItem763To762(mapItem(id765)))))))))"),
        "mapEntityType": ("mapEntityType754To753",
                          "mapEntityType755To754(mapEntityType756To755(mapEntityType757To756(mapEntityType759To757(mapEntityType760To759(mapEntityType761To760(mapEntityType762To761(mapEntityType763To762(mapEntityType(id765)))))))))"),
        "mapSound": ("mapSound754To753",
                     "mapSound755To754(mapSound756To755(mapSound757To756(mapSound759To757(mapSound760To759(mapSound761To760(mapSound762To761(mapSound763To762(mapSound(id765)))))))))"),
        "mapParticle": ("mapParticle754To753",
                        "mapParticle755To754(mapParticle756To755(mapParticle757To756(mapParticle759To757(mapParticle760To759(mapParticle761To760(mapParticle762To761(mapParticle763To762(mapParticle(id765)))))))))"),
        "mapBlock": ("mapBlock754To753",
                     "mapBlock755To754(mapBlock756To755(mapBlock757To756(mapBlock759To757(mapBlock760To759(mapBlock761To760(mapBlock762To761(mapBlock763To762(mapBlock(id765)))))))))"),
        "mapMenu": ("mapMenu754To753",
                    "mapMenu755To754(mapMenu756To755(mapMenu757To756(mapMenu759To757(mapMenu760To759(mapMenu761To760(mapMenu762To761(mapMenu763To762(mapMenu(id765)))))))))"),
        "mapBlockEntityType": ("mapBlockEntityType754To753",
                               "mapBlockEntityType755To754(mapBlockEntityType756To755(mapBlockEntityType757To756(mapBlockEntityType759To757(mapBlockEntityType760To759(mapBlockEntityType761To760(mapBlockEntityType762To761(mapBlockEntityType763To762(mapBlockEntityType(id765)))))))))"),
    }
    chain_753 = {}
    for name, (wrapper, inner754) in chain_down.items():
        expr754 = read_dispatch_branch(remapper_text, name, name + "755To754(")
        if expr754 != inner754:
            raise SystemExit(f"dispatch の 754 分岐が想定と違う（{name}）: {expr754[:80]}")
        expr753 = f"{wrapper}({inner754})"
        if expr753.count("(") != expr753.count(")"):
            raise SystemExit(f"753 の連鎖の括弧が合わない（{name}）")
        chain_753[name] = expr753
    effect754 = read_dispatch_branch(remapper_text, "mapEffect", "mapEffect755To754(")
    if effect754 != ("mapEffect755To754(mapEffect756To755(mapEffect757To756(mapEffect759To757(mapEffect(id765)))))"):
        raise SystemExit(f"dispatch の 754 分岐が想定と違う（mapEffect）: {effect754[:80]}")
    effect753 = f"mapEffect754To753({effect754})"
    if effect753.count("(") != effect753.count(")"):
        raise SystemExit("753 の連鎖の括弧が合わない（mapEffect）")
    soundname754 = read_dispatch_branch(remapper_text, "mapSoundName", "mapSoundName755To754(")
    if soundname754 != ("mapSoundName755To754(mapSoundName756To755(mapSoundName757To756(mapSoundName759To757(mapSoundName760To759(mapSoundName761To760(mapSoundName762To761(mapSoundName763To762(mapSoundName(name)))))))))"):
        raise SystemExit(f"dispatch の 754 分岐が想定と違う（mapSoundName）: {soundname754[:80]}")
    soundname753 = f"mapSoundName754To753({soundname754})"
    if soundname753.count("(") != soundname753.count(")"):
        raise SystemExit("753 の連鎖の括弧が合わない（mapSoundName）")
    itemrev754 = read_dispatch_branch(remapper_text, "mapItemReverse", "mapItemReverse(")
    itemrev753 = ("mapItemReverse(mapItem762To763(mapItem761To762(mapItem760To761(mapItem759To760"
                  "(mapItem757To759(mapItem756To757(mapItem755To756(mapItem754To755(mapItem753To754(id))))))))))")
    if itemrev753 != itemrev754.replace("mapItem754To755(id)",
                                        "mapItem754To755(mapItem753To754(id))"):
        raise SystemExit("mapItemReverse の 753 連鎖が 754 分岐の内側への追加と一致しない")
    if itemrev753.count("(") != itemrev753.count(")"):
        raise SystemExit("753 の連鎖の括弧が合わない（mapItemReverse）")
    print("ディスパッチの 753 連鎖を手書きし、754 分岐との突き合わせを確認（11 本）")

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
    insert_before("import static io.detergentmc.protocol.RegistryTables754.*;",
                  "import static io.detergentmc.protocol.RegistryTables753.*;\n",
                  "import RegistryTables753", "RegistryTables753.*")
    # 1b. ブロック状態の下向き表は別クラス（RegistryTables753BlockState）。
    # <clinit> 64KB 対策の分割（773／774／775 の *BlockState.java と同じ方式）。
    insert_after("import static io.detergentmc.protocol.RegistryTables753.*;\n",
                 "import static io.detergentmc.protocol.RegistryTables753BlockState.*;\n",
                 "import RegistryTables753BlockState", "RegistryTables753BlockState.*")

    # 2. PROTOCOL_753（PROTOCOL_755 の直前）
    insert_before("    /** 755（1.17）のプロトコル番号。 */",
                  "    /** 753（1.16.4／1.16.3）のプロトコル番号。 */\n"
                  "    public static final int PROTOCOL_753 = 753;\n",
                  "PROTOCOL_753", "PROTOCOL_753 = 753")

    # 3. 753定数（GENERIC_DAMAGE_TYPE_754 の直後）
    constants_753 = """
    /** 753側の最大の番号（ViaVersion mapping-1.16.json の件数−1。バイオームだけ id の最大値）。 */
    public static final int MAX_BLOCK_STATE_753 = 17111;
    public static final int MAX_ITEM_753 = 975;
    public static final int MAX_ENTITY_TYPE_753 = 107;
    public static final int MAX_SOUND_753 = 991;
    public static final int MAX_PARTICLE_753 = 71;
    public static final int MAX_BLOCK_753 = 762;
    public static final int MAX_MENU_753 = 23;
    /** 753側のエンチャントは 0〜37（38件。754と同一順序）。 */
    public static final int MAX_ENCHANTMENT_753 = 37;
    /** 753側のバイオームは 0〜173（79件。id は 0〜173 の非連番）。 */
    public static final int MAX_BIOME_753 = 173;
    /** 753側のダメージ種別は 0〜41（42件。762 の順序の恒等写像。実バニラで検証する）。 */
    public static final int MAX_DAMAGE_TYPE_753 = 41;
    /** 753側のブロックエンティティ種別は 0〜32（33件。754と同一順序。DROP は無い）。 */
    public static final int MAX_BLOCK_ENTITY_753 = 32;
    /** 753側の属性は 0〜12（13件。754と同一順序）。 */
    public static final int MAX_ATTRIBUTE_753 = 12;
    /** 753側のエフェクトは 1〜32（32件。754と同一順序。1ベース）。 */
    public static final int MAX_EFFECT_753 = 32;

    /** 753側の代替先（mapping-1.16.json の番号。Speed だけ 1ベースで 1）。 */
    public static final int STONE_BLOCK_STATE_753 = 1;
    public static final int AIR_BLOCK_STATE_753 = 0;
    public static final int STONE_ITEM_753 = 1;
    public static final int PIG_ENTITY_TYPE_753 = 59;
    public static final int GENERIC_SOUND_753 = 816; // block.stone.break
    public static final int AMBIENT_PARTICLE_753 = 0; // ambient_entity_effect
    public static final int STONE_BLOCK_753 = 1;
    public static final int CRAFTING_MENU_753 = 11;
    public static final int SPEED_EFFECT_753 = 1;
    /** 753側の平原バイオーム（minecraft-data pc/1.16/biomes.json）。 */
    public static final int PLAINS_BIOME_753 = 1;
    /** 753側の generic ダメージ種別（762 と同一順序と仮定）。 */
    public static final int GENERIC_DAMAGE_TYPE_753 = 16;
"""
    insert_after("    public static final int GENERIC_DAMAGE_TYPE_754 = 16;\n",
                 constants_753, "753定数", "MAX_BLOCK_STATE_753")

    # 4. 753の展開済み表（755 ⇔ 754 の展開済み表の直前）
    maps_753 = """
    // ------------------------------------------------------------------
    // 754 ⇔ 753（1.16.4／1.16.5 ⇔ 1.16.3）の展開済み表。RegistryTables753
    // の区間＋上書きの表をクラスロード時に int[] へ展開する。
    // 順向き（754→753）は expand()、反転（753→754）は invert() で作る。
    // 補正は全種 0 件（754 と 753 のレジストリ内容は同一のため、
    // invert() の first-wins と名前の真値の食い違いは無い）。
    // アイテムだけは 766／777／754 の前例にならい、名前から作った既存の
    // 逆変換表（ITEM_REVERSE_753）を expand() でそのまま使う
    // （invert()＋補正でも同じ結果になることを書き込み前に確認済み）。
    // エフェクトは表を作らない（アクセサでハードコードする。753 と 754 は
    // どちらも 32件・同一順序のため恒等写像で、DROP は無い）。
    // ブロックエンティティ種別も表を作らない。753 は 33 件・754 も 33 件で
    // 同一順序のため恒等写像（DROP は無い）。
    // 753 は合成表を作らない。
    // バイオームの反転表は 753 側 174 件（id 0〜173。隙間の id は平原）。
    // 753 の表は private static final class Maps753 にまとめる（RegistryRemapper の
    // static 初期化子は 64KB 制限に達しているため。754 追加後も外側は 64,669 バイト
    // のまま（残り 866 バイト）。Maps754 方式で外側は 0 バイト増）。
    // ------------------------------------------------------------------


    /** 754 ⇔ 753 の展開済み表をまとめたホルダ。RegistryRemapper の static 初期化子は
     * 64KB 制限に達しているため（754 追加後も外側は 64,669 バイト。残り 866 バイト）、753 の表の
     * 初期化はこの別クラスの static 初期化子に分けてある。753 以降も同じ方式にすること。
     * 初回アクセス時に初期化される（クラスロード時の初期化と等価。expand()／invert() は
     * 純粋関数）。アクセサからは Maps753.〜 で参照する。 */
    private static final class Maps753 {
        /** ブロック状態の反転の補正（753 id → 754 id）。0 件。754 と 753 の
         * レジストリ内容は同一のため補正は不要。754 と同じ構造を保つために
         * 空の表を置く。
         * tools/maps753-corrections.json から生成。検証は
         * tools/verify-753-tables.py のチェックDを参照。 */
        private static final int[][] BLOCK_STATE_UP_753_CORRECTIONS = {
""" + blockstate_corr_java + """
        };

        /** 音の反転の補正（753 id → 754 id）。0 件。754 と 753 の
         * レジストリ内容は同一のため補正は不要。754 と同じ構造を保つために
         * 空の表を置く。
         * tools/maps753-corrections.json から生成。検証は
         * tools/verify-753-tables.py のチェックDを参照。 */
        private static final int[][] SOUND_UP_753_CORRECTIONS = {
""" + sound_corr_java + """
        };

        /** バイオームの反転の補正（753 id → 754 id）。0 件。753 の 79 件は
         * すべて 754 と同じ id・同じ名前のため、invert() だけでも名前の
         * 真値と一致する。削除・改名が無いため当たらない。754 と同じ構造を保つために
         * 空の表を置く。
         * RegistryTables753.java の BIOME_753_TO_754_* 表との差分から生成。
         * 検証は tools/verify-753-tables.py のチェックGを参照。 */
        private static final int[][] BIOME_UP_753_CORRECTIONS = {
""" + biome_corr_java + """
        };

        /** 754のブロック状態ID → 753のブロック状態ID（RegistryTables753）。 */
        private static final int[] BLOCK_STATE_754_TO_753_MAP =
                expand(BLOCK_STATE_753_RANGES, BLOCK_STATE_753_OVERRIDES, MAX_BLOCK_STATE_754 + 1,
                        STONE_BLOCK_STATE_753);
        /** 753のブロック状態ID → 754のブロック状態ID（反転＋補正）。 */
        private static final int[] BLOCK_STATE_753_TO_754_MAP = invert(
                BLOCK_STATE_753_RANGES, BLOCK_STATE_753_OVERRIDES,
                MAX_BLOCK_STATE_754 + 1, MAX_BLOCK_STATE_753 + 1,
                STONE_BLOCK_STATE_754, BLOCK_STATE_UP_753_CORRECTIONS);
        /** 754のアイテムID → 753のアイテムID。 */
        private static final int[] ITEM_754_TO_753_MAP =
                expand(ITEM_753_RANGES, ITEM_753_OVERRIDES, MAX_ITEM_754 + 1, STONE_ITEM_753);
        /** 753のアイテムID → 754のアイテムID（名前から作った逆変換表）。 */
        private static final int[] ITEM_753_TO_754_MAP =
                expand(ITEM_REVERSE_753_RANGES, ITEM_REVERSE_753_OVERRIDES, MAX_ITEM_753 + 1,
                        STONE_ITEM_754);
        /** 754のエンティティ種別ID → 753のエンティティ種別ID。 */
        private static final int[] ENTITY_TYPE_754_TO_753_MAP =
                expand(ENTITY_TYPE_753_RANGES, ENTITY_TYPE_753_OVERRIDES, MAX_ENTITY_TYPE_754 + 1,
                        PIG_ENTITY_TYPE_753);
        /** 753のエンティティ種別ID → 754のエンティティ種別ID（反転）。 */
        private static final int[] ENTITY_TYPE_753_TO_754_MAP = invert(
                ENTITY_TYPE_753_RANGES, ENTITY_TYPE_753_OVERRIDES,
                MAX_ENTITY_TYPE_754 + 1, MAX_ENTITY_TYPE_753 + 1, PIG_ENTITY_TYPE_754, null);
        /** 754の音ID → 753の音ID。 */
        private static final int[] SOUND_754_TO_753_MAP =
                expand(SOUND_753_RANGES, SOUND_753_OVERRIDES, MAX_SOUND_754 + 1, GENERIC_SOUND_753);
        /** 753の音ID → 754の音ID（反転＋補正）。 */
        private static final int[] SOUND_753_TO_754_MAP = invert(
                SOUND_753_RANGES, SOUND_753_OVERRIDES,
                MAX_SOUND_754 + 1, MAX_SOUND_753 + 1,
                GENERIC_SOUND_754, SOUND_UP_753_CORRECTIONS);
        /** 754のパーティクルID → 753のパーティクルID。 */
        private static final int[] PARTICLE_754_TO_753_MAP =
                expand(PARTICLE_753_RANGES, PARTICLE_753_OVERRIDES, MAX_PARTICLE_754 + 1,
                        AMBIENT_PARTICLE_753);
        /** 753のパーティクルID → 754のパーティクルID（反転）。 */
        private static final int[] PARTICLE_753_TO_754_MAP = invert(
                PARTICLE_753_RANGES, PARTICLE_753_OVERRIDES,
                MAX_PARTICLE_754 + 1, MAX_PARTICLE_753 + 1,
                AMBIENT_PARTICLE_754, null);
        /** 754のブロックID → 753のブロックID。 */
        private static final int[] BLOCK_754_TO_753_MAP =
                expand(BLOCK_753_RANGES, BLOCK_753_OVERRIDES, MAX_BLOCK_754 + 1, STONE_BLOCK_753);
        /** 753のブロックID → 754のブロックID（反転）。 */
        private static final int[] BLOCK_753_TO_754_MAP = invert(
                BLOCK_753_RANGES, BLOCK_753_OVERRIDES,
                MAX_BLOCK_754 + 1, MAX_BLOCK_753 + 1, STONE_BLOCK_754, null);
        /** 754のメニュー種別ID → 753のメニュー種別ID。 */
        private static final int[] MENU_754_TO_753_MAP =
                expand(MENU_753_RANGES, MENU_753_OVERRIDES, MAX_MENU_754 + 1, CRAFTING_MENU_753);
        /** 753のメニュー種別ID → 754のメニュー種別ID（反転）。 */
        private static final int[] MENU_753_TO_754_MAP = invert(
                MENU_753_RANGES, MENU_753_OVERRIDES,
                MAX_MENU_754 + 1, MAX_MENU_753 + 1, CRAFTING_MENU_754, null);
        /** 754のバイオームID → 753のバイオームID。両側とも同じ 79 件で
         * 新規バイオームは無い。隙間の id は平原（1）へ落とす。 */
        private static final int[] BIOME_754_TO_753_MAP =
                expand(BIOME_753_RANGES, BIOME_753_OVERRIDES, MAX_BIOME_754 + 1, PLAINS_BIOME_753);
        /** 753のバイオームID → 754のバイオームID（反転＋補正。174 件）。 */
        private static final int[] BIOME_753_TO_754_MAP = invert(
                BIOME_753_RANGES, BIOME_753_OVERRIDES,
                MAX_BIOME_754 + 1, MAX_BIOME_753 + 1, PLAINS_BIOME_754,
                BIOME_UP_753_CORRECTIONS);
        /** ダメージ種別ID：754 → 753（762 の 42 件の恒等写像。1.16.3／1.16.5 の
         * どちらにも damage_type レジストリは無いため、ワイヤ上の呼び出し元は無い）。 */
        private static final int[] DAMAGE_TYPE_753_MAP = {
            0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19,
            20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32, 33, 34, 35, 36, 37,
            38, 39, 40, 41
        };
        /** ダメージ種別ID：753 → 754（762 の 42 件の恒等写像。呼び出し元は無い）。 */
        private static final int[] DAMAGE_TYPE_REVERSE_753_MAP = {
            0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19,
            20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32, 33, 34, 35, 36, 37,
            38, 39, 40, 41
        };
        /** 754の属性ID → 753の属性ID。並びは同一。範囲外は {@link #DROP}。 */
        private static final int[] ATTRIBUTE_753_MAP =
                expand(ATTRIBUTE_753_RANGES, ATTRIBUTE_753_OVERRIDES, MAX_ATTRIBUTE_754 + 1, DROP);
        /** 753の属性ID → 754の属性ID。並びは同一。範囲外は {@link #DROP}。 */
        private static final int[] ATTRIBUTE_REVERSE_753_MAP =
                expand(ATTRIBUTE_REVERSE_753_RANGES, ATTRIBUTE_REVERSE_753_OVERRIDES,
                        MAX_ATTRIBUTE_753 + 1, DROP);
        /** 754のエンチャントID → 753のエンチャントID。並びは同一。範囲外は {@link #DROP}。 */
        private static final int[] ENCHANTMENT_753_MAP =
                expand(ENCHANTMENT_753_RANGES, ENCHANTMENT_753_OVERRIDES,
                        MAX_ENCHANTMENT_754 + 1, DROP);
        /** 753のエンチャントID → 754のエンチャントID。並びは同一。範囲外は {@link #DROP}。 */
        private static final int[] ENCHANTMENT_REVERSE_753_MAP =
                expand(ENCHANTMENT_REVERSE_753_RANGES, ENCHANTMENT_REVERSE_753_OVERRIDES,
                        MAX_ENCHANTMENT_753 + 1, DROP);

        /** 754の音の名前 → 753の音の名前（Stop Sound 用）。数値の反転表と一致させる。 */
        private static final Map<String, String> SOUND_NAME_FALLBACK_754_TO_753_MAP =
                buildSoundNameMap(SOUND_NAME_FALLBACKS_754_TO_753);

        /** 753の音の名前 → 754の音の名前（Stop Sound 用）。改名は無い（空の表）。 */
        private static final Map<String, String> SOUND_NAME_FALLBACK_753_TO_754_MAP =
                buildSoundNameMap(SOUND_NAME_FALLBACKS_753_TO_754);
    }
"""
    insert_before("    // ------------------------------------------------------------------\n"
                  "    // 755 ⇔ 754（1.17 ⇔ 1.16.4／1.16.5）の展開済み表。RegistryTables754\n",
                  maps_753, "753の展開済み表", "BLOCK_STATE_754_TO_753_MAP")

    # 5. アクセサメソッド（755 ⇔ 754 節の直前）
    methods_753 = """
    // ------------------------------------------------------------------
    // 754 ⇔ 753（1.16.4／1.16.5 ⇔ 1.16.3）
    // パケットIDは 754 と 753 で同じ（変更 0 件・新規 0 件・削除 0 件。
    // Join Game 0x24、Chunk Data 0x20、Respawn 0x39 は 754 と同じ。
    // packet-ids-753.json の実測を参照）。
    // レジストリは RegistryTables753（補正済み ViaVersion mapping-1.16.json を
    // 実バニラ 1.16.3 の registries.json／reports と突き合わせて確認したもの。
    // 754 とバイト同一のため 7 種すべて恒等写像）。
    // 753↔763〜775 は隣接表の連鎖（753→754→755→756→757→759→760→761→762→763→合成表）
    // で到達する。合成表は作らない。
    // Join Game はフィールド構成が同一（実バニラ 1.16.3 の生キャプチャで確認。
    // 30,847 バイト。dimensionCodec は dimension_type 4 件・worldgen/biome 79 件。
    // 1.16.5 の 30,847 バイトと同一（バイオーム 79 件・dimension element に
    // min_y／height 無しも同じ）。previousGameMode は 1.16.3 が u8・1.17 が i8 と
    // minecraft-data では違うが、ワイヤ上は同じ 1 バイト）。Respawn も同一。
    // ------------------------------------------------------------------

    /** 754のブロック状態ID → 753のブロック状態ID。範囲外はSTONE_BLOCK_STATE_753 に相当する代替。 */
    public static int mapBlockState754To753(int id754) {
        if (id754 < 0 || id754 >= Maps753.BLOCK_STATE_754_TO_753_MAP.length) {
            logUnknownComposedOnce("754→753", "ブロック状態", id754);
            return STONE_BLOCK_STATE_753;
        }
        return Maps753.BLOCK_STATE_754_TO_753_MAP[id754];
    }

    /** 753のブロック状態ID → 754のブロック状態ID。範囲外はSTONE_BLOCK_STATE_754 に相当する代替。 */
    public static int mapBlockState753To754(int id753) {
        if (id753 < 0 || id753 >= Maps753.BLOCK_STATE_753_TO_754_MAP.length) {
            logUnknownComposedOnce("753→754", "ブロック状態", id753);
            return STONE_BLOCK_STATE_754;
        }
        return Maps753.BLOCK_STATE_753_TO_754_MAP[id753];
    }

    /** 754のアイテムID → 753のアイテムID。範囲外はSTONE_ITEM_753 に相当する代替。 */
    public static int mapItem754To753(int id754) {
        if (id754 < 0 || id754 >= Maps753.ITEM_754_TO_753_MAP.length) {
            logUnknownComposedOnce("754→753", "アイテム", id754);
            return STONE_ITEM_753;
        }
        return Maps753.ITEM_754_TO_753_MAP[id754];
    }

    /** 753のアイテムID → 754のアイテムID。範囲外はSTONE_ITEM_754 に相当する代替。 */
    public static int mapItem753To754(int id753) {
        if (id753 < 0 || id753 >= Maps753.ITEM_753_TO_754_MAP.length) {
            logUnknownComposedOnce("753→754", "アイテム", id753);
            return STONE_ITEM_754;
        }
        return Maps753.ITEM_753_TO_754_MAP[id753];
    }

    /** 754のエンティティ種別ID → 753のエンティティ種別ID。範囲外はPIG_ENTITY_TYPE_753 に相当する代替。 */
    public static int mapEntityType754To753(int id754) {
        if (id754 < 0 || id754 >= Maps753.ENTITY_TYPE_754_TO_753_MAP.length) {
            logUnknownComposedOnce("754→753", "エンティティ種別", id754);
            return PIG_ENTITY_TYPE_753;
        }
        return Maps753.ENTITY_TYPE_754_TO_753_MAP[id754];
    }

    /** 753のエンティティ種別ID → 754のエンティティ種別ID。範囲外はPIG_ENTITY_TYPE_754 に相当する代替。 */
    public static int mapEntityType753To754(int id753) {
        if (id753 < 0 || id753 >= Maps753.ENTITY_TYPE_753_TO_754_MAP.length) {
            logUnknownComposedOnce("753→754", "エンティティ種別", id753);
            return PIG_ENTITY_TYPE_754;
        }
        return Maps753.ENTITY_TYPE_753_TO_754_MAP[id753];
    }

    /** 754の音ID → 753の音ID。範囲外はGENERIC_SOUND_753 に相当する代替。 */
    public static int mapSound754To753(int id754) {
        if (id754 < 0 || id754 >= Maps753.SOUND_754_TO_753_MAP.length) {
            logUnknownComposedOnce("754→753", "音", id754);
            return GENERIC_SOUND_753;
        }
        return Maps753.SOUND_754_TO_753_MAP[id754];
    }

    /** 753の音ID → 754の音ID。範囲外はGENERIC_SOUND_754 に相当する代替。 */
    public static int mapSound753To754(int id753) {
        if (id753 < 0 || id753 >= Maps753.SOUND_753_TO_754_MAP.length) {
            logUnknownComposedOnce("753→754", "音", id753);
            return GENERIC_SOUND_754;
        }
        return Maps753.SOUND_753_TO_754_MAP[id753];
    }

    /** 754のパーティクルID → 753のパーティクルID。範囲外はAMBIENT_PARTICLE_753 に相当する代替。 */
    public static int mapParticle754To753(int id754) {
        if (id754 < 0 || id754 >= Maps753.PARTICLE_754_TO_753_MAP.length) {
            logUnknownComposedOnce("754→753", "パーティクル", id754);
            return AMBIENT_PARTICLE_753;
        }
        return Maps753.PARTICLE_754_TO_753_MAP[id754];
    }

    /** 753のパーティクルID → 754のパーティクルID。範囲外はAMBIENT_PARTICLE_754 に相当する代替。 */
    public static int mapParticle753To754(int id753) {
        if (id753 < 0 || id753 >= Maps753.PARTICLE_753_TO_754_MAP.length) {
            logUnknownComposedOnce("753→754", "パーティクル", id753);
            return AMBIENT_PARTICLE_754;
        }
        return Maps753.PARTICLE_753_TO_754_MAP[id753];
    }

    /** 754のブロックID → 753のブロックID。範囲外はSTONE_BLOCK_753 に相当する代替。 */
    public static int mapBlock754To753(int id754) {
        if (id754 < 0 || id754 >= Maps753.BLOCK_754_TO_753_MAP.length) {
            logUnknownComposedOnce("754→753", "ブロック", id754);
            return STONE_BLOCK_753;
        }
        return Maps753.BLOCK_754_TO_753_MAP[id754];
    }

    /** 753のブロックID → 754のブロックID。範囲外はSTONE_BLOCK_754 に相当する代替。 */
    public static int mapBlock753To754(int id753) {
        if (id753 < 0 || id753 >= Maps753.BLOCK_753_TO_754_MAP.length) {
            logUnknownComposedOnce("753→754", "ブロック", id753);
            return STONE_BLOCK_754;
        }
        return Maps753.BLOCK_753_TO_754_MAP[id753];
    }

    /** 754のメニュー種別ID → 753のメニュー種別ID。範囲外はCRAFTING_MENU_753 に相当する代替。 */
    public static int mapMenu754To753(int id754) {
        if (id754 < 0 || id754 >= Maps753.MENU_754_TO_753_MAP.length) {
            logUnknownComposedOnce("754→753", "メニュー", id754);
            return CRAFTING_MENU_753;
        }
        return Maps753.MENU_754_TO_753_MAP[id754];
    }

    /** 753のメニュー種別ID → 754のメニュー種別ID。範囲外はCRAFTING_MENU_754 に相当する代替。 */
    public static int mapMenu753To754(int id753) {
        if (id753 < 0 || id753 >= Maps753.MENU_753_TO_754_MAP.length) {
            logUnknownComposedOnce("753→754", "メニュー", id753);
            return CRAFTING_MENU_754;
        }
        return Maps753.MENU_753_TO_754_MAP[id753];
    }

    /**
     * 754のブロックエンティティ種別ID → 753のブロックエンティティ種別ID。
     * 753 は 33 件・754 も 33 件で同一順序のため恒等写像（DROP は無い。
     * 754 と違い、片方だけの種別は無い）。
     */
    public static int mapBlockEntityType754To753(int id754) {
        if (id754 < 0 || id754 > MAX_BLOCK_ENTITY_754) {
            logUnknownComposedOnce("754→753", "ブロックエンティティ種別", id754);
            return DROP;
        }
        return id754;
    }

    /**
     * 753のブロックエンティティ種別ID → 754のブロックエンティティ種別ID。
     * 0〜32 は恒等写像。
     */
    public static int mapBlockEntityType753To754(int id753) {
        if (id753 < 0 || id753 > MAX_BLOCK_ENTITY_753) {
            logUnknownComposedOnce("753→754", "ブロックエンティティ種別", id753);
            return DROP;
        }
        return id753;
    }

    /** 754のバイオームID → 753のバイオームID。範囲外はPLAINS_BIOME_753 に相当する代替。 */
    public static int mapBiome754To753(int id754) {
        if (id754 < 0 || id754 >= Maps753.BIOME_754_TO_753_MAP.length) {
            logUnknownComposedOnce("754→753", "バイオーム", id754);
            return PLAINS_BIOME_753;
        }
        return Maps753.BIOME_754_TO_753_MAP[id754];
    }

    /** 753のバイオームID → 754のバイオームID。範囲外はPLAINS_BIOME_754 に相当する代替。 */
    public static int mapBiome753To754(int id753) {
        if (id753 < 0 || id753 >= Maps753.BIOME_753_TO_754_MAP.length) {
            logUnknownComposedOnce("753→754", "バイオーム", id753);
            return PLAINS_BIOME_754;
        }
        return Maps753.BIOME_753_TO_754_MAP[id753];
    }

    /** 754のダメージ種別ID → 753のダメージ種別ID（恒等写像。ただし1.16.3／1.16.5 の codec に
     * damage_type レジストリは存在しないため、ワイヤ上の呼び出し元は無い）。 */
    public static int mapDamageType754To753(int id754) {
        if (id754 < 0 || id754 > MAX_DAMAGE_TYPE_754) {
            logUnknownOnce(PROTOCOL_753, "ダメージ種別", id754);
            return DROP;
        }
        return Maps753.DAMAGE_TYPE_753_MAP[id754];
    }

    /** 753のダメージ種別ID → 754のダメージ種別ID（恒等写像。ワイヤ上の呼び出し元は無い）。 */
    public static int mapDamageType753To754(int id753) {
        if (id753 < 0 || id753 > MAX_DAMAGE_TYPE_753) {
            logUnknownOnce(PROTOCOL_754, "ダメージ種別", id753);
            return DROP;
        }
        return Maps753.DAMAGE_TYPE_REVERSE_753_MAP[id753];
    }

    /**
     * 754のエフェクトID → 753のエフェクトID。753 と 754 は 32件・同一順序
     * のため恒等写像。範囲外は Speed（1）。
     */
    public static int mapEffect754To753(int id754) {
        if (id754 < 0 || id754 > MAX_EFFECT_754) {
            logUnknownComposedOnce("754→753", "エフェクト", id754);
            return SPEED_EFFECT_753;
        }
        return id754;
    }

    /**
     * 753のエフェクトID → 754のエフェクトID。1〜32 は恒等写像。
     * 範囲外は Speed（1）。
     */
    public static int mapEffect753To754(int id753) {
        if (id753 < 0 || id753 > MAX_EFFECT_753) {
            logUnknownComposedOnce("753→754", "エフェクト", id753);
            return SPEED_EFFECT_754;
        }
        return id753;
    }

    /** 754の属性ID → 753の属性ID。並びは同一。範囲外は {@link #DROP}。 */
    public static int mapAttribute754To753(int id754) {
        if (id754 < 0 || id754 > MAX_ATTRIBUTE_754) {
            logUnknownOnce(PROTOCOL_753, "属性", id754);
            return DROP;
        }
        return Maps753.ATTRIBUTE_753_MAP[id754];
    }

    /** 753の属性ID → 754の属性ID。並びは同一。範囲外は {@link #DROP}。 */
    public static int mapAttribute753To754(int id753) {
        if (id753 < 0 || id753 > MAX_ATTRIBUTE_753) {
            logUnknownOnce(PROTOCOL_754, "属性", id753);
            return DROP;
        }
        return Maps753.ATTRIBUTE_REVERSE_753_MAP[id753];
    }

    /** 753の属性ID → 属性の名前（Update Attributes は 1.16.3 では名前形式のため）。 */
    public static String attributeName753(int id753) {
        if (id753 < 0 || id753 >= ATTRIBUTE_753_NAMES.length) {
            return null;
        }
        return ATTRIBUTE_753_NAMES[id753];
    }

    /**
     * 属性の名前 → 753の属性ID（765／763／764 は属性が名前形式のため、
     * 753向けに変換するときに使う）。無い名前は {@link #DROP}。
     */
    public static int attributeId753(String name) {
        if (name == null) {
            return DROP;
        }
        String plain = stripMinecraftPrefix(name);
        for (int i = 0; i < ATTRIBUTE_753_NAMES.length; i++) {
            if (ATTRIBUTE_753_NAMES[i].equals(plain)) {
                return i;
            }
        }
        return DROP;
    }

    /** 754のエンチャントID → 753のエンチャントID。並びは同一。範囲外は {@link #DROP}。 */
    public static int mapEnchantment754To753(int id754) {
        if (id754 < 0 || id754 > MAX_ENCHANTMENT_754) {
            logUnknownOnce(PROTOCOL_753, "エンチャント", id754);
            return DROP;
        }
        return Maps753.ENCHANTMENT_753_MAP[id754];
    }

    /** 753のエンチャントID → 754のエンチャントID。並びは同一。範囲外は {@link #DROP}。 */
    public static int mapEnchantment753To754(int id753) {
        if (id753 < 0 || id753 > MAX_ENCHANTMENT_753) {
            logUnknownOnce(PROTOCOL_754, "エンチャント", id753);
            return DROP;
        }
        return Maps753.ENCHANTMENT_REVERSE_753_MAP[id753];
    }

    /** 754の音の名前 → 753の音の名前（Stop Sound 用）。数値の反転表と一致させる。 */
    public static String mapSoundName754To753(String name) {
        return mapSoundNameComposed(name, Maps753.SOUND_NAME_FALLBACK_754_TO_753_MAP);
    }

    /** 753の音の名前 → 754の音の名前（Stop Sound 用）。改名は無い。 */
    public static String mapSoundName753To754(String name) {
        return mapSoundNameComposed(name, Maps753.SOUND_NAME_FALLBACK_753_TO_754_MAP);
    }
"""
    insert_before("    // ------------------------------------------------------------------\n"
                  "    // 755 ⇔ 754（1.17 ⇔ 1.16.4／1.16.5）\n",
                  methods_753, "753アクセサ", "mapBlockState754To753")

    # 6. ディスパッチのコメント更新（753の連鎖を追記）
    # 注意：756 のスクリプトは old→new の replace を使っていたが、new が
    # old を部分文字列として含むため再実行のたびに 756 の行が増殖した
    # （RegistryRemapper.java の dispatch コメントに 756 の行が 5 回ある）。
    # 753 ではマーカー付きの insert_after で 1 回だけ追加する。
    dispatch_753_line = ("    // 753は754の表を経由する（753↔763〜775は"
                         "753→754→755→756→757→759→760→761→762→763→合成表の連鎖）。\n")
    insert_after("    // 754は755の表を経由する（754↔763〜775は"
                 "754→755→756→757→759→760→761→762→763→合成表の連鎖）。\n",
                 dispatch_753_line, "dispatch コメント 753",
                 "753は754の表を経由する")

    # 7. target 分岐への 753 追加（各 dispatch メソッドの 754 分岐の直前。
    # insert_after はメソッド冒頭の直後に入れるため、754 分岐より前に来る。
    # 下向きは 754 分岐の外側に 754→753 段を 1 つ巻く。
    # 括弧の数：754 分岐は 8 個の開き／8 個の閉じ、巻いた後は 9 個／9 個。
    # mapEffect だけは 754 分岐が 4 個／4 個で、巻いた後は 5 個／5 個。
    # 手書きした連鎖は上の検証6でファイルの 754 分岐と突き合わせてある。）
    for name, expr753 in chain_753.items():
        body = (f"        if (clientProtocol == PROTOCOL_753) {{\n"
                f"            return {expr753};\n"
                f"        }}\n")
        anchor = f"    public static int {name}(int id765, int clientProtocol) {{\n"
        marker = f"return {expr753};"
        insert_after(anchor, body, f"dispatch {name} 753", marker)

    # mapItemReverse（引数名が id）。上向き（クライアント→765）のため、
    # 新しい隣接段（753→754）は最も内側（最初に適用）に入れる。
    # 下向き（mapItem 等）とは逆で、外側に巻くのは誤り。
    # 注意：757／759／760／761／762 の分岐は外側に巻く形で作られており、
    # 隣接表を逆順に合成している（実測：757 分岐は正しい合成と 724/1101 件
    # 食い違う。例：tropical_fish_spawn_egg 757 id 926 の正しい 765 id は
    # 1067 だが 757 分岐は 1066 を返す。§12.5 参照）。756／754 分岐は
    # 正しい順序。753 分岐も正しい順序で合成する。他分岐の修正は
    # 統括の判断待ち。
    insert_after("    public static int mapItemReverse(int id, int clientProtocol) {\n",
                 "        if (clientProtocol == PROTOCOL_753) {\n"
                 f"            return {itemrev753};\n"
                 "        }\n",
                 "dispatch mapItemReverse 753",
                 f"return {itemrev753};")

    # mapEffect（754→753 は恒等写像。754 分岐の外側にそのままつなぐ）
    insert_after("    public static int mapEffect(int id765, int clientProtocol) {\n",
                 "        if (clientProtocol == PROTOCOL_753) {\n"
                 f"            return {effect753};\n"
                 "        }\n",
                 "dispatch mapEffect 753",
                 f"return {effect753};")

    # mapSoundName
    insert_after("    public static String mapSoundName(String name, int clientProtocol) {\n",
                 "        if (clientProtocol == PROTOCOL_753) {\n"
                 f"            return {soundname753};\n"
                 "        }\n",
                 "dispatch mapSoundName 753",
                 f"return {soundname753};")

    REMAPPER.write_text(src, encoding="utf-8")
    print("RegistryRemapper.java に 753 を差し込んだ。")


if __name__ == "__main__":
    raise SystemExit(main())
