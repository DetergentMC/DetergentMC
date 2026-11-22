#!/usr/bin/env python3
"""ChunkTranslator / PlayTranslator の 775 節から 776 節を生成する。

方針: 775 節を抜き出して shift776()（tools/shift776.py。gen-775 の
shift() を1段ずらしたもの）で1段ずらし、FIXES（手書きの置き換え）を当て、
ファイル末尾の最終 `}` の直前に挿入する。PlayTranslator の 776 節は
手書きブロックで作る。

775→776 で shift だけでは足りない点（この生成器で直す）:
- 成分種別は775が110件、776が111件。新規は sulfur_cube_content 1件だけ
  （776側 78。jar ダンプ registry-26.2/data_component_type.txt と
  vv-26.2.json で確認。mc-26.2-protocol.json は 110 件のままで古い）。
  KIND 表は 775→776 の写像（新規 1 件は -1）にする。774→775 の 6 件挿入
  （additional_trade_cost 41、dye 43、pig/sound_variant 94、cow/sound_variant 96、
  chicken/sound_variant 98、cat/sound_variant 106）を持ち越してはいけない。
  sulfur_cube_content の中身は ItemStackTemplate 1個
  （26.2 jar の SulfurCubeContent：absorbedBlockItemStack 1個だけ）。
- 775→776 で形式が変わるものは無い（パケットID 0変更、パケットの Slot は両版とも
  [count][itemId][added][removed]、成分内の ItemStackTemplate は両版とも
  [itemId][count][added][removed]、チャンク・メタデータ・パーティクルの形式も同一）。
  26.1／26.2 両 jar で ItemStack$1（パケット Slot）と ItemStackTemplate の
  STREAM_CODEC が同一であることを確認済み。
  変わるのは番号だけ：ブロック状態 +2,493、ブロック +28、アイテム +31、
  音 +66、パーティクル +8（4〜10 に7件挿入＋124 に1件追加）、
  エンティティ +1（sulfur_cube 130）、属性 +5（0, 6, 9, 17, 27）、
  データコンポーネント +1（78）、バイオーム +1（sulfur_caves 53）、
  ブロックエンティティは bed 削除（775:25）・potent_sulfur 追加（776:48）、
  ダメージ種別 +1（sulfur_cube_hot 42）、ジュークボックス +1（bounce 4）、
  argumenttypes は color → team_color に改名（番号 16 は同じ）。
  エンチャント 43件・絵画 51種・楽器 8件・ダイアログ 3件・メニュー 25件・
  統計 77件・レシピ 21件・スロット表示 11件・環境属性 48件は同一。
  メタデータの型番号は同一（26.1／26.2 両 jar の EntityDataSerializers の
  宣言順が一致することを確認。mapMetadataType は恒等写像にする）。
- パーティクル：新規 8 件のうち sulfur_bubbles（4）・noxious_gas（5）・
  noxious_gas_cloud（6）・sulfur_cube_goo（124）はデータ無し（SimpleParticleType）。
  geyser（7）・geyser_plume（10）は waterBlocks i32 1個、
  geyser_base（8）・geyser_poof（9）は waterBlocks i32＋burstImpulseBase f32
  （26.2 jar の GeyserParticleOptions／GeyserBaseParticleOptions の
  STREAM_CODEC は ByteBufCodecs.INT＋FLOAT）。
  776 にだけある 8 種は 775 へ entity_effect（775=21）にフォールバックするが、
  entity_effect は色の i32 が要るため、データ無しの 4 種は色 0 を合成し、
  geyser 系 4 種はデータを読み飛ばしたうえで色 0 を合成する（色を書かないと
  パケットが壊れる。765→766 の translateParticle765To766 と同じ設計）。
  entity_effect は 775=21→776=28、block_crumble 115→122、firefly 116→123。
- ジュークボックスの歌は 21曲→22曲（bounce を index 4 に追加。
  775→776 は 4以上を+1、776→775 は 4をDROP、5以上を-1）。
  Registry Data の並べ替えは jukebox_song・damage_type・enchantment の
  対応表を追加し、jukebox_song の targetNames に 776 を足す。
  jukebox_song／dialog のフィルタにも 776（V26_2）を足す。
- Update Time・Use Entity・Enchant Item は 775・776 で形式が同一のため
  776 節では素通しにする（774→775 の作り直しを写してはいけない）。
  形式が同一であることは 26.1／26.2 両 jar で確認：
  ClientboundSetTimePacket（gameTime＋clockUpdates の Map。ClockNetworkState は
  varlong＋f32＋f32）、ServerboundInteractPacket（entityId＋hand＋location＋
  usingSecondaryAction）は両 jar で同一フィールド。
  なお clockUpdates のキーは Holder<WorldClock>（varint。0＝直書きの空レコード、
  N≧1＝レジストリ id N-1）で、775節の「id varint」モデルは正確には Holder
  エンコーディングである。775→776 は素通しのためこの節には影響しない。
  WorldClocks は両版とも OVERWORLD・THE_END の2件で同一。
- Join Game／Login Success／Teams／SpectatorAction は PlayTranslator 側では
  翻訳しない（ProxySession 側で別担当 A が実装中。775節にも翻訳子は無い）。
  SpectatorAction（0x3e）は 775 の spectate_entity から改名されただけで
  位置は同じ。
- ProxySession の設定フェーズ 776 ハンドラは PROXYSESSION_CONFIG_SECTION_776
  として手書きで追加する。ただし ProxySession.java は別担当（A）が編集中で、
  適用は親の「ProxySession is free」許可が出てから。main() は既定でこの部分を
  飛ばし、--apply-proxysession でだけ適用する（--skip-proxysession は既定と
  同じく飛ばす）。A のディスパッチのエントリ名と一致させること（2026-11-15 時点で
  ProxySession.java のディスパッチに既にある名前を使う）。
  注意：2026-11-15 時点で ProxySession.java には 776 ハンドラが 14 件既に
  ある（776From775／776From768／776From767／776From766／776／763From776／
  From776／767From776／766From776／776From769／776From770／775From776／
  769From776／768From776）。この生成器が足すのはディスパッチから参照されて
  いるのに未定義の 8 件だけ（776From774／776From773／776From772／776From771／
  774From776／772From776／771From776／770From776）。重複してはいけない。
- main() の抽出は extract_section_to_eof を使う。775節はファイル末尾のため。
  775節の後ろに 762／761／760／759節は無い。
- /tmp には書かない（再起動で消える）。
"""

import importlib.util
import re
import sys
from pathlib import Path

_HERE = Path(__file__).resolve().parent
_spec = importlib.util.spec_from_file_location("shift776", _HERE / "shift776.py")
_mod = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(_mod)
shift = _mod.shift776

ROOT = Path("/home/hatch/workspace/detergentmc")
CT = ROOT / "detergentmc-protocol/src/main/java/io/detergentmc/protocol/ChunkTranslator.java"
PT = ROOT / "detergentmc-protocol/src/main/java/io/detergentmc/protocol/PlayTranslator.java"
PS = ROOT / "detergentmc-protocol/src/main/java/io/detergentmc/protocol/ProxySession.java"


def extract_section(path, start_marker, end_marker):
    lines = path.read_text(encoding="utf-8").split("\n")
    start = None
    end = None
    for i, line in enumerate(lines):
        if start is None and start_marker in line:
            # 1行上の区切り線から開始する。
            start = i - 1
            assert "---" in lines[start], f"区切り線が見つからない: {lines[start]!r}"
        if start is not None and end_marker in line:
            end = i - 1  # 区切り線の直前の空行まで
            break
    assert start is not None and end is not None, (start_marker, end_marker)
    return "\n".join(lines[start:end]), start, end


def extract_section_to_eof(path, start_marker):
    """開始マーカーからファイル末尾の最終 `}` の直前まで。"""
    lines = path.read_text(encoding="utf-8").split("\n")
    start = None
    for i, line in enumerate(lines):
        if start_marker in line:
            start = i - 1
            assert "---" in lines[start], f"区切り線が見つからない: {lines[start]!r}"
            break
    assert start is not None, start_marker
    # 末尾の `}`（最終行）の直前の空行まで。
    end = len(lines) - 1
    assert lines[end] == "}" or lines[end] == "", lines[end]
    while lines[end - 1].strip() == "":
        end -= 1
    end -= 1  # 最後のメソッドの直後の空行を除く
    return "\n".join(lines[start:end]), start, end


def apply_fixes(text, fixes, label):
    for old, new in fixes:
        n = text.count(old)
        if n != 1:
            print(f"[{label}] FIX 失敗（一致 {n} 件）:\n---OLD---\n{old}\n---", file=sys.stderr)
            raise SystemExit(1)
        text = text.replace(old, new)
    return text


# ----------------------------------------------------------------------
# 置き換えヘルパー
# ----------------------------------------------------------------------

def replace_method(text, signature, new_text, label):
    """signature を含むメソッド1個を、直前の javadoc ごと new_text に置き換える。
    signature はメソッド名を含む一意の文字列（例: "translateSlot769To774(Reader"）。"""
    idx = text.find(signature)
    if idx < 0:
        raise SystemExit(f"[{label}] メソッドが見つからない: {signature}")
    if text.find(signature, idx + 1) >= 0:
        raise SystemExit(f"[{label}] メソッドが2件以上ある: {signature}")
    # メソッド開始行の先頭を探す。
    line_start = text.rfind("\n", 0, idx) + 1
    # 直前の javadoc（/** ... */）・行コメント・空行をさかのぼって含める。
    # 空行の前で止める（空行自体は text[:start] に残し、new_text 側の
    # 先頭の改行は除去する）。これで置き換え後のメソッド間に空白行が残る。
    # `//` 行は javadoc に直結しているもの（空行なし）だけ含める。
    start = line_start
    while True:
        prev_end = start - 1
        if prev_end <= 0:
            break
        prev_start = text.rfind("\n", 0, prev_end) + 1
        prev_line = text[prev_start:prev_end]
        stripped = prev_line.strip()
        if stripped == "":
            break
        if stripped.startswith("*") or stripped.startswith("/**") \
                or stripped.startswith("*/"):
            start = prev_start
            continue
        if stripped.startswith("//"):
            start = prev_start
            continue
        break
    if text[:start].endswith("\n\n"):
        new_text = new_text.lstrip("\n")
    # シグネチャ以後の最初の `{` から対応する `}` までを数える。
    brace = text.find("{", idx)
    assert brace > 0, signature
    depth = 0
    i = brace
    while i < len(text):
        if text[i] == "{":
            depth += 1
        elif text[i] == "}":
            depth -= 1
            if depth == 0:
                break
        i += 1
    assert depth == 0, signature
    end = i + 1
    # 末尾の改行は残す。
    return text[:start] + new_text + text[end:]


def fix(text, old, new, label):
    n = text.count(old)
    if n != 1:
        print(f"[{label}] FIX 失敗（一致 {n} 件）:\n---OLD---\n{old}\n---", file=sys.stderr)
        raise SystemExit(1)
    return text.replace(old, new)


def delete_method(text, signature, label):
    """signature を含むメソッド1個を、直前の javadoc ごと削除する。
    replace_method と同じ範囲特定を使う（削除後に空行1個が残る）。"""
    idx = text.find(signature)
    if idx < 0:
        raise SystemExit(f"[{label}] メソッドが見つからない: {signature}")
    if text.find(signature, idx + 1) >= 0:
        raise SystemExit(f"[{label}] メソッドが2件以上ある: {signature}")
    line_start = text.rfind("\n", 0, idx) + 1
    start = line_start
    while True:
        prev_end = start - 1
        if prev_end <= 0:
            break
        prev_start = text.rfind("\n", 0, prev_end) + 1
        prev_line = text[prev_start:prev_end]
        stripped = prev_line.strip()
        if stripped == "":
            break
        if stripped.startswith("*") or stripped.startswith("/**") \
                or stripped.startswith("*/"):
            start = prev_start
            continue
        if stripped.startswith("//"):
            start = prev_start
            continue
        break
    brace = text.find("{", idx)
    assert brace > 0, signature
    depth = 0
    i = brace
    while i < len(text):
        if text[i] == "{":
            depth += 1
        elif text[i] == "}":
            depth -= 1
            if depth == 0:
                break
        i += 1
    assert depth == 0, signature
    end = i + 1
    # メソッド直後の改行も1個除く（空行の二重化を防ぐ）。
    if text[end:end + 1] == "\n":
        end += 1
    return text[:start] + text[end:]


def extract_method(text, signature, label):
    """signature を含むメソッド1個を、直前の javadoc ごと抜き出す。
    replace_method と同じ範囲特定を使う。"""
    idx = text.find(signature)
    if idx < 0:
        raise SystemExit(f"[{label}] メソッドが見つからない: {signature}")
    if text.find(signature, idx + 1) >= 0:
        raise SystemExit(f"[{label}] メソッドが2件以上ある: {signature}")
    line_start = text.rfind("\n", 0, idx) + 1
    start = line_start
    while True:
        prev_end = start - 1
        if prev_end <= 0:
            break
        prev_start = text.rfind("\n", 0, prev_end) + 1
        prev_line = text[prev_start:prev_end]
        stripped = prev_line.strip()
        if stripped == "":
            break
        if stripped.startswith("*") or stripped.startswith("/**") \
                or stripped.startswith("*/"):
            start = prev_start
            continue
        if stripped.startswith("//"):
            start = prev_start
            continue
        break
    brace = text.find("{", idx)
    assert brace > 0, signature
    depth = 0
    i = brace
    while i < len(text):
        if text[i] == "{":
            depth += 1
        elif text[i] == "}":
            depth -= 1
            if depth == 0:
                break
        i += 1
    assert depth == 0, signature
    return text[start:i + 1]


def method_body(method_text):
    """メソッド全文から `{` 以後の本体（先頭の `{` を含む）を返す。"""
    brace = method_text.find("{")
    assert brace > 0
    return method_text[brace:]


def strip_javadoc(method_text):
    """メソッド全文から直前の javadoc／行コメントを除き、シグネチャ行から返す。"""
    lines = method_text.split("\n")
    for i, line in enumerate(lines):
        s = line.strip()
        if s.startswith("/**") or s.startswith("*") or s.startswith("*/") or s.startswith("//"):
            continue
        return "\n".join(lines[i:])
    raise SystemExit("シグネチャ行が見つからない")


def remap_case_labels(method_src, mapping, label):
    """switch の `case A, B, ... ->` のラベルだけを mapping で付け替える。
    mapping に無いラベルはそのまま（警告を出す）。本体の数値は触らない。"""
    def sub(m):
        labels = m.group(1)
        out = []
        for tok in labels.split(","):
            tok = tok.strip()
            if tok == "":
                continue
            n = int(tok)
            if n in mapping:
                out.append(str(mapping[n]))
            else:
                print(f"[{label}] 警告：case ラベル {n} に写像が無い（そのまま）", file=sys.stderr)
                out.append(tok)
        return "case " + ", ".join(out) + " ->"
    new_src, n = re.subn(r"case ((?:\d+\s*,\s*)*\d+)\s*->", sub, method_src)
    if n == 0:
        print(f"[{label}] 警告：case ラベルが1件も置き換わらなかった", file=sys.stderr)
    return new_src


def rename_calls(method_src, renames):
    """メソッド呼び出し名の置き換え（(old, new) の列。出現回数は問わない）。"""
    for old, new in renames:
        method_src = method_src.replace(old, new)
    return method_src


def insert_section_before_class_close(path, section, label, guard):
    """section をファイル末尾の最終 `}`（クラスの閉じ）の直前に挿入する。"""
    text = path.read_text(encoding="utf-8")
    assert guard not in text, f"[{label}] {guard} が既に存在する（2回目の実行？）"
    stripped = text.rstrip()
    assert stripped.endswith("}"), f"[{label}] ファイルの末尾が }} ではない"
    body = stripped[:-1].rstrip()
    new_text = body + "\n\n" + section + "\n}\n"
    path.write_text(new_text, encoding="utf-8")
    print(f"[{label}] 挿入した（{len(new_text)} chars）")


def insert_before_marker(path, section, label, marker, guard):
    """section を marker 行の直前（javadoc があればその直前）に挿入する。"""
    text = path.read_text(encoding="utf-8")
    assert guard not in text, f"[{label}] {guard} が既に存在する（2回目の実行？）"
    lines = text.split("\n")
    idx = None
    for i, line in enumerate(lines):
        if marker in line:
            idx = i
            break
    assert idx is not None, f"[{label}] マーカーが無い: {marker}"
    start = idx
    while start > 0 and lines[start - 1].strip().startswith("*"):
        start -= 1
    if start > 0 and lines[start - 1].strip().startswith("/**"):
        start -= 1
    new_lines = lines[:start] + [""] + section.strip("\n").split("\n") + [""] + lines[start:]
    path.write_text("\n".join(new_lines), encoding="utf-8")
    print(f"[{label}] 挿入した")

# ----------------------------------------------------------------------
# 成分種別の対応表（775 kind -> 776 kind）
# ----------------------------------------------------------------------

# 776 の新規成分種別（775 には無い。jar ダンプ registry-26.2/data_component_type.txt
# と vv-26.2.json より）：78 sulfur_cube_content。775 の 110 件はすべて 776 にある。
_NEW_KIND_776 = {78}
KIND_775_TO_776 = {}
_nxt = 0
for _k775 in range(110):
    while _nxt in _NEW_KIND_776:
        _nxt += 1
    KIND_775_TO_776[_k775] = _nxt
    _nxt += 1
KIND_776_TO_775 = {v: k for k, v in KIND_775_TO_776.items()}
for _n in _NEW_KIND_776:
    KIND_776_TO_775[_n] = -1  # 776 の新規種別は 775 では落とす
assert len(KIND_775_TO_776) == 110
assert len(KIND_776_TO_775) == 111
assert KIND_775_TO_776[0] == 0 and KIND_775_TO_776[77] == 77
assert KIND_775_TO_776[78] == 79 and KIND_775_TO_776[109] == 110
assert KIND_776_TO_775[78] == -1
assert KIND_776_TO_775[79] == 78 and KIND_776_TO_775[110] == 109


def java_int_array(name, values, per_line=16):
    lines = []
    for i in range(0, len(values), per_line):
        lines.append("            " + ", ".join(str(v) for v in values[i:i + per_line]) + ",")
    body = "\n".join(lines)
    return f"    private static final int[] {name} = {{\n{body}\n    }};\n"


KIND_775_TO_776_JAVA = java_int_array(
    "KIND_775_TO_776", [KIND_775_TO_776[i] for i in range(110)])
KIND_776_TO_775_JAVA = java_int_array(
    "KIND_776_TO_775",
    [KIND_776_TO_775.get(i, -1) for i in range(111)])

# 775節の KIND 表（shift 後の old として使う。775 節の KIND 表は 774→775 の
# 実写像（104→110）。774→775 の新規 6 種（additional_trade_cost 41、dye 43、
# pig/sound_variant 94、cow/sound_variant 96、chicken/sound_variant 98、
# cat/sound_variant 106）をここにだけ残す。776 の KIND 表は上の _NEW_KIND_776
# から作る。混同しないこと。
_NEW_KIND_775_OLD = {41, 43, 94, 96, 98, 106}
KIND_774_TO_775_OLD = {}
_nxt = 0
for _k774 in range(104):
    while _nxt in _NEW_KIND_775_OLD:
        _nxt += 1
    KIND_774_TO_775_OLD[_k774] = _nxt
    _nxt += 1
KIND_775_TO_774_OLD = {v: k for k, v in KIND_774_TO_775_OLD.items()}
for _n in _NEW_KIND_775_OLD:
    KIND_775_TO_774_OLD[_n] = -1  # 775 の新規種別は 774 では落とす
assert len(KIND_774_TO_775_OLD) == 104
assert len(KIND_775_TO_774_OLD) == 110
assert KIND_774_TO_775_OLD[0] == 0 and KIND_774_TO_775_OLD[40] == 40
assert KIND_774_TO_775_OLD[41] == 42 and KIND_774_TO_775_OLD[42] == 44
assert KIND_774_TO_775_OLD[93] == 97 and KIND_774_TO_775_OLD[94] == 99
assert KIND_774_TO_775_OLD[103] == 109
assert KIND_775_TO_774_OLD[41] == -1 and KIND_775_TO_774_OLD[43] == -1
assert KIND_775_TO_774_OLD[94] == -1 and KIND_775_TO_774_OLD[106] == -1
assert KIND_775_TO_774_OLD[42] == 41 and KIND_775_TO_774_OLD[109] == 103

# shift 後の 776 節に残る（誤った）KIND 表のテキスト。置き換えの old に使う。
# （775 節の 774→775 実写像を shift したもの。KIND_775_TO_776_JAVA／
#  KIND_776_TO_775_JAVA は CHUNK_STRING_FIXES の new 側で使う。）
KIND_775_TO_776_SHIFTED_JAVA = java_int_array(
    "KIND_775_TO_776", [KIND_774_TO_775_OLD[i] for i in range(104)])
KIND_776_TO_775_SHIFTED_JAVA = java_int_array(
    "KIND_776_TO_775",
    [KIND_775_TO_774_OLD.get(i, -1) for i in range(110)])


# 776節のヘッダーコメント（CHUNK_STRING_FIXES の new 側で使う）。
HEADER_776 = """    // 775 ⇔ 776（26.2）
    // 成分種別は775が110件、776が111件。新規は sulfur_cube_content（78）1件だけ
    // のため KIND 表で付け替える。中身のレジストリID（アイテム・音・
    // ブロック状態・エンティティ・属性・ダメージ種別・バイオーム・パーティクル・
    // ブロックエンティティ種別・ジュークボックス）がずれる。エンチャント・
    // メニュー・絵画は同一。
    // エンティティは sulfur_cube（776=130）の挿入で 130 番以降が+1ずれる
    // （happy_ghast は775・776とも58。falling_block は775・776とも51）。
    // パーティクルは 4〜10 に7件（sulfur_bubbles／noxious_gas／
    // noxious_gas_cloud／geyser／geyser_base／geyser_poof／geyser_plume）の挿入と
    // sulfur_cube_goo（124）の追加で 4番以降が+7ずれる（trail 56／
    // block_crumble 122／tinted_leaves 43／firefly 123／entity_effect 28）。
    // チャンクの heightmap は770・771・772・773・774・775・776とも直接の配列（長さ varint 無し）。"""

# 776節の KIND 表まわり（CHUNK_STRING_FIXES の new 側で使う）。
KIND_COUNT_776 = """    /** 成分種別の件数（775 は 110 件、776 は 111 件）。 */
    private static final int DATA_COMPONENT_TYPE_COUNT_776 = 111;"""

KIND_JAVADOC_1_776 = """    /**
     * 成分種別の対応表（775 kind → 776 kind）。776 の新規 1 種
     * （sulfur_cube_content 78）の挿入で 775 の 110 件は 78 番以降が+1ずれる。
     */"""

KIND_JAVADOC_2_776 = """    /**
     * 成分種別の対応表（776 kind → 775 kind）。776 の新規 1 種は
     * -1（落とす）。775 の 110 件はすべて 776 にある。
     */"""

# ----------------------------------------------------------------------
# ChunkTranslator 776 節の文字列 FIXES（シフト済みテキストに適用）
# ----------------------------------------------------------------------

CHUNK_STRING_FIXES = [
    # ヘッダーコメント（shift 版は 775→776 の説明を版番号だけずらしたもの。
    # vv-26.2.json／registry-26.2 のダンプで確認した 775→776 の差分で書き直す。
    # 先頭の "// 775 ⇔ 776（26.2）" 行は shift 済みのため置き換え範囲外。）
    (
        """    // 成分種別は775が104件、776が110件。6件の新規挿入（additional_trade_cost 41、
    // dye 43、pig/sound_variant 94、cow/sound_variant 96、chicken/sound_variant 98、
    // cat/sound_variant 106）のため KIND 表で付け替える。
    // 変わるのは中身のレジストリID（アイテム・音・ブロック状態）と成分の中身
    // （break_sound／blocks_attacks／piercing_weapon／kinetic_weapon の音ID、
    // chicken/variant・zombie_nautilus/variant の Holder 形式、bundle_contents・
    // charged_projectiles・container・use_remainder の Slot→ItemStackTemplate）。
    // エンティティ・エンチャント・ダメージ種別・バイオーム・属性・メニュー・
    // ブロックエンティティ種別・絵画・ジュークボックスは同一。
    // エンティティは774・775・776とも157件・同じ順序（happy_ghast は774・775・776とも58。
    // falling_block は774・775・776とも51）。
    // パーティクルは pause_mob_growth（50）・reset_mob_growth（51）の挿入で
    // 50番以降が+2ずれる（trail 49／block_crumble 115／tinted_leaves 36／firefly 116）。
    // チャンクの heightmap は770・771・772・773・774・775・776とも直接の配列（長さ varint 無し）。""",
        HEADER_776[HEADER_776.find("\n") + 1:],
    ),
    # 成分種別の件数（shift 版は 104→110。実際は 110→111）。
    (
        """    /** 成分種別の件数（775 は 104 件、776 は 110 件）。 */
    private static final int DATA_COMPONENT_TYPE_COUNT_776 = 110;""",
        KIND_COUNT_776,
    ),
    # KIND 表の javadoc（shift 版は 775 の 6 件挿入の説明。776 の 1 件に書き直す）。
    (
        """    /**
     * 成分種別の対応表（775 kind → 776 kind）。776 の新規 6 種
     * （additional_trade_cost 41、dye 43、pig/sound_variant 94、
     * cow/sound_variant 96、chicken/sound_variant 98、cat/sound_variant 106）
     * の挿入で 775 の 104 件は 41 番以降がずれる。
     */""",
        KIND_JAVADOC_1_776,
    ),
    (
        """    /**
     * 成分種別の対応表（776 kind → 775 kind）。776 の新規 6 種は
     * -1（落とす）。775 の 104 件はすべて 776 にある。
     */""",
        KIND_JAVADOC_2_776,
    ),
    # KIND 表そのもの（shift 版は 774→775 の実写像をずらした 104／110 件。
    # 775→776 の実写像 110／111 件に置き換える）。
    (KIND_775_TO_776_SHIFTED_JAVA, KIND_775_TO_776_JAVA),
    (KIND_776_TO_775_SHIFTED_JAVA, KIND_776_TO_775_JAVA),
    # パーティクル定数（shift 版は 775 の番号。776 では 4〜10 の挿入で
    # 4番以降が+7：trail 49→56、block_crumble 115→122、pale_oak 35→42、
    # tinted_leaves 36→43、firefly 116→123。entity_effect 21→28。）
    (
        "    private static final int PARTICLE_776_TRAIL = 49;",
        "    private static final int PARTICLE_776_TRAIL = 56;",
    ),
    (
        """    /** 776の block_crumble 粒子のID（776=115。775では113）。 */
    private static final int PARTICLE_776_BLOCK_CRUMBLE = 115;""",
        """    /** 776の block_crumble 粒子のID（776=122。775では115）。 */
    private static final int PARTICLE_776_BLOCK_CRUMBLE = 122;""",
    ),
    (
        """    /** 776の pale_oak_leaves 粒子のID（774・775・776とも35）。 */
    private static final int PARTICLE_776_PALE_OAK_LEAVES = 35;""",
        """    /** 776の pale_oak_leaves 粒子のID（776=42。775では35）。 */
    private static final int PARTICLE_776_PALE_OAK_LEAVES = 42;""",
    ),
    (
        """    /** 776の tinted_leaves 粒子のID（774・775・776とも36）。 */
    private static final int PARTICLE_776_TINTED_LEAVES = 36;""",
        """    /** 776の tinted_leaves 粒子のID（776=43。775では36）。 */
    private static final int PARTICLE_776_TINTED_LEAVES = 43;""",
    ),
    (
        """    /** 776の firefly 粒子のID（776=116。775では114）。 */
    private static final int PARTICLE_776_FIREFLY = 116;""",
        """    /** 776の firefly 粒子のID（776=123。775では116）。 */
    private static final int PARTICLE_776_FIREFLY = 123;""",
    ),
    # パーティクル節の説明（pause／reset の挿入、ではない。4〜10 の挿入＋124 の追加）。
    (
        """    // パーティクル（775 ⇔ 776）
    // パーティクルは pause_mob_growth（50）・reset_mob_growth（51）の挿入で
    // 50番以降が+2ずれるため、ID は対応表に通す。trail（774・775・776とも49）／
    // block_crumble（775=114→776=115）とも duration・ブロック状態ID付き。
    // entity_effect は774・775・776とも21。particle item のデータは
    // Slot（775）から ItemStackTemplate（776）へ変わる。""",
        """    // パーティクル（775 ⇔ 776）
    // パーティクルは 4〜10 に7件の挿入と sulfur_cube_goo（124）の追加で
    // 4番以降が+7ずれるため、ID は対応表に通す。trail（775=49→776=56）／
    // block_crumble（775=115→776=122）とも duration・ブロック状態ID付き。
    // entity_effect は775=21→776=28。particle item のデータは
    // ItemStackTemplate（775・776とも）のまま。""",
    ),
    # メタデータ節の説明（型番号は同一。22・24・29・31番の挿入は775の話）。
    (
        """    // エンティティメタデータ（775 ⇔ 776）
    // 型 14／15（ブロック状態）は774・775・776で同じ番号のため対応表に
    // 通すだけ（実質そのまま）。型 25（狼バリアント）・26（狼の音）は
    // varint のためそのまま写す。型 16／17（粒子）は ID を対応表に通す。
    // 型番号は775→776で22・24・29・31番の挿入（cat／cow／pig／chicken の
    // sound_variant）でずれる（zombie_nautilus_variant は775=28→776=32、
    // humanoid_arm は775=38→776=42）。""",
        """    // エンティティメタデータ（775 ⇔ 776）
    // 型 14／15（ブロック状態）は775・776で同じ番号のため対応表に
    // 通すだけ（実質そのまま）。型 25（狼バリアント）・26（狼の音）は
    // varint のためそのまま写す。型 16／17（粒子）は ID を対応表に通す。
    // 型番号は775・776とも 0〜42・同じ順序（26.1／26.2 両 jar の
    // EntityDataSerializers の宣言順が一致。cat／cow／pig／chicken の
    // sound_variant は775=22・24・29・31、776でも同じ）。""",
    ),
    # チャンクデータ節の説明（ずれるのはアイテム・音・ブロック状態だけ、ではない。
    # エンティティ・属性・ダメージ種別・バイオーム・パーティクル・
    # ブロックエンティティ種別・ジュークボックスもずれる。同一なのは
    # エンチャント・絵画だけ。）
    (
        """    // チャンクデータ（775 ⇔ 776）
    // 775⇔776ではアイテム・音・ブロック状態がずれる。エンティティ・
    // エンチャント・ダメージ種別・バイオーム・パーティクル・
    // ブロックエンティティ種別・属性・絵画・ジュークボックスは同一。
    // パレットの付け替えは対応表に任せる。""",
        """    // チャンクデータ（775 ⇔ 776）
    // 775⇔776ではアイテム・音・ブロック状態・エンティティ・属性・
    // ダメージ種別・バイオーム・パーティクル・ブロックエンティティ種別・
    // ジュークボックスがずれる。エンチャント・絵画は同一。
    // パレットの付け替えは対応表に任せる。""",
    ),
    # Spawn Entity の javadoc（エンティティは同一、ではない。sulfur_cube 追加）。
    (
        """     * Spawn Entity（775 → 776）。種別を付け替え、falling_block（774・775・776とも51）の
     * objectData（ブロック状態ID）も付け替える。エンティティは774・775・776とも
     * 157件・同じ順序のため対応表に通すだけ（実質そのまま）。""",
        """     * Spawn Entity（775 → 776）。種別を付け替え、falling_block（775・776とも51）の
     * objectData（ブロック状態ID）も付け替える。エンティティは sulfur_cube
     * （776=130）の挿入で 130 番以降が+1ずれるため対応表に通す。""",
    ),
    # jukebox_playable の javadoc（21曲で同一、ではない。bounce 追加で22曲）。
    (
        "    /** jukebox_playable を775→776向けに変換する。曲IDは772・773・774・775・776で同一（21曲・同じ並び）。直書きの音の Holder も付け替える。末尾の showInTooltip は770・771・772・773・774・775・776ともある。 */",
        "    /** jukebox_playable を775→776向けに変換する。曲IDは bounce（776=4）の追加で 4番以降が+1ずれる（22曲）。直書きの音の Holder も付け替える。末尾の showInTooltip は770・771・772・773・774・775・776ともある。 */",
    ),
    (
        "    /** jukebox_playable を776→775向けに変換する。曲IDは772・773・774・775・776で同一（21曲・同じ並び）。末尾の showInTooltip は770・771・772・773・774・775・776ともある。 */",
        "    /** jukebox_playable を776→775向けに変換する。曲IDは bounce（776=4）を落とし 5番以降を-1する（22曲→21曲）。直書きの音の Holder も付け替える。末尾の showInTooltip は770・771・772・773・774・775・776ともある。 */",
    ),
    # World Particles（下向き）の javadoc（pause／reset の挿入、ではない）。
    (
        """     * World Particles（776 → 775）。alwaysShow は770・771・772・773・774・775・776ともにあるため
     * そのまま通す。粒子は pause／reset_mob_growth の挿入で50番以降が+2ずれるため対応表に通す。落とす粒子は無い。""",
        """     * World Particles（776 → 775）。alwaysShow は770・771・772・773・774・775・776ともにあるため
     * そのまま通す。粒子は 4〜10 の挿入と sulfur_cube_goo（124）の追加で 4番以降が+7ずれるため対応表に通す。新規 8 種は entity_effect（775=21）にフォールバックする（落とす粒子は無い）。""",
    ),
    # translateParticle776To775 の javadoc（粒子は同一、ではない）。
    (
        """     * パーティクルは pause／reset_mob_growth の挿入で50番以降が+2ずれるため
     * 対応表に通す。落とす粒子は無いため常に変換できる（{@code true} を返す）。""",
        """     * パーティクルは 4〜10 の挿入と sulfur_cube_goo（124）の追加で 4番以降が+7ずれるため
     * 対応表に通す。新規 8 種は entity_effect（775=21）にフォールバックするため
     * 常に変換できる（{@code true} を返す）。""",
    ),
    # translateSlotComponents776To775 の javadoc（新規 6 種、ではない。1 種）。
    (
        "    /** スロットの added／removed 成分部分だけを776→775向けに変換する。種別番号は KIND 表で付け替える（775は104件、776は110件）。776 の新規 6 種は落とす。 */",
        "    /** スロットの added／removed 成分部分だけを776→775向けに変換する。種別番号は KIND 表で付け替える（775は110件、776は111件）。776 の新規 1 種（sulfur_cube_content 78）は落とす。 */",
    ),
]

# 削除するメソッド（shift 版では不要・誤りのため。replace 後に削除する）。
DELETE_METHODS = [
    # 775節の Slot→ItemStackTemplate／ItemStackTemplate→Slot 変換は
    # 776節では要らない（775・776とも ItemStackTemplate）。
    # translateSlotArray／translateUseRemainder／translateContainer／
    # translateParticleData は 776 用に置き換えるため、ここで消す。
    "translateSlot775To776AsTemplate(Reader reader, Writer writer)",
    "translateItemStackTemplate776To775AsSlot(Reader reader, Writer writer)",
]

# ----------------------------------------------------------------------
# ChunkTranslator 776 節のメソッド置き換え
#
# 方針：775→776 は形式が同一の箇所が多く、shift 済みテキストがそのまま
# 正しいメソッドも多い。置き換えるのは ID・番号がずれるものだけ。
# 派生は 775 節の原文（orig）から行う。shift 済みテキストを直すのではない。
# （shift は版番号だけずらすため、774→775 の差分（例：Slot→Template、
#   型番号の挿入）が 775→776 の差分として残ってしまう。）
#
# 罠：パケットレベルの Slot は 775・776とも [count][itemId][added][removed]
# （ItemStack$1 の decode が count を先に読む。26.1／26.2 両 jar で同一）。
# ItemStackTemplate [itemId][count][added][removed] は成分の中身
# （bundle／charged／container／use_remainder／particle item）だけの形式。
# そのため translateSlot775To776／776To775 は shift 通り（Slot→Slot）で
# 正しく、置き換えはしない。置き換えるのは成分内の Template を扱う
# SlotArray／UseRemainder／Container と ParticleData の item 経路。
# ----------------------------------------------------------------------

# パーティクル：774→775 は pause（50）・reset（51）の挿入で 50番以降+2。
PARTICLE_774_TO_775 = {i: (i if i < 50 else i + 2) for i in range(115)}
# パーティクル：775→776 は 4〜10 の挿入で 4番以降+7（124 は末尾追加）。
PARTICLE_775_TO_776 = {i: (i if i < 4 else i + 7) for i in range(117)}


# 注意：KIND_774_TO_776（774→776 の成分種別合成表）と
# METADATA_TYPE_774_TO_776（774→776 のメタデータ型番号表）は削除した。
# component-down・metadata-value-down は 775 派生に書き換えたため不要。
# 特にメタデータ型番号表は 774→775 の規則（<22 同一、<24 +1、<29 +2、
# <31 +3、他 +4）を写したものだったが、この規則は 774 型 23（wolf）・
# 27（chicken）・28（zombie）・29（global_pos）・30（painting）で誤った
# 番号を返す（775 の mapMetadataType774To775 の潜在バグ。26.1 jar の
# EntityDataSerializers 登録順で確認。775 は対象外のため直さない）。
# 774 派生で使うと 776 の 24・29・32・33 がずれ、22・25・31・34 が欠落する
# うえ重複 case でコンパイルも通らない。再導入してはいけない。


def _with_javadoc(new_javadoc, method_no_javadoc):
    return new_javadoc + "\n" + method_no_javadoc


def _rename_signature(method_text, old_name, new_name):
    assert old_name in method_text, old_name
    return method_text.replace(old_name, new_name, 1)


# --- バイオーム ---------------------------------------------------------

def build_biome_up():
    return """    /**
     * 775のバイオームID→776。sulfur_caves（776=53）の追加で
     * 53番以降が+1ずれる。
     */
    private static int mapBiome775To776(int id) {
        return id < 53 ? id : id + 1;
    }
"""


def build_biome_down():
    return """    /**
     * 776のバイオームID→775。sulfur_caves（776=53）は775に無いため
     * dripstone_caves（15）にフォールバックする（地下の洞窟バイオームで
     * 最も近い。plains は地上バイオームのため不適）。53より大は-1する。
     */
    private static int mapBiomeUp776(int id) {
        if (id == 53) {
            return 15; // dripstone_caves
        }
        return id < 53 ? id : id - 1;
    }
"""


# --- 成分 ---------------------------------------------------------------

def build_component_up(orig):
    """translateComponent775To776。派生元は 775 節の translateComponent775To774
    （775 の種別番号で dispatch。ラベルは 775 のまま正しい）。"""
    m = extract_method(orig, "translateComponent775To774(Reader reader, Writer writer, int kind775)",
                       "component-up")
    m = strip_javadoc(m)
    m = rename_calls(m, [("775To774", "775To776")])
    old_drop = """            case 41, 43, 94, 96, 98, 106 -> {
                // 775 の新規成分。774 に無いため中身を読み飛ばして落とす。
                skipComponentData775(reader, kind775);
                return false;
            }"""
    new_drop = """            case 41, 43, 94, 96, 106 -> writer.writeVarInt(reader.readVarInt()); // additional_trade_cost／dye／pig／cow／cat の sound_variant（775・776とも同じ種別番号・varint のためそのまま写す）
            case 98 -> { // chicken の sound_variant（Holder。775・776とも98。0 なら直書きの文字列）
                int holder = reader.readVarInt();
                writer.writeVarInt(holder);
                if (holder == 0) {
                    writer.writeString(reader.readString());
                }
            }"""
    assert old_drop in m, "component-up: drop ブロックが見つからない"
    m = m.replace(old_drop, new_drop)
    m = m.replace("// resolvable_profile（774・775で同一形式）",
                  "// resolvable_profile（775・776で同一形式）")
    m = m.replace("773・774・775とも中身なし", "773・774・775・776とも中身なし")
    # シグネチャ名は上の包括 rename（775To774→775To776）で既に変わっている。
    javadoc = """    /**
     * 成分1個を775→776向けに変換する。種別番号は775の番号で dispatch する
     * （775は110件、776は111件。KIND 表の対応は vv-26.2.json の
     * data_component_type による）。775 の 110 件はすべて 776 にあるため
     * 落とすことは無い。レジストリIDを付け替えるものだけ変換する。
     * 49・50 の中身は ItemStackTemplate のまま、75 container は
     * option 付き ItemStackTemplate の列のまま。
     * 97 chicken/variant、99 zombie_nautilus/variant は Holder のまま
     * （775・776とも registryEntryHolder）。
     */"""
    return _with_javadoc(javadoc, m)


def build_component_down(orig):
    """translateComponent776To775。派生元は 775 節の translateComponent775To774
    （775 の種別番号で dispatch。ラベルは 775→776 の KIND 表で付け替える）。
    775 の新規 6 種（41, 43, 94, 96, 98, 106）は 774 には無かったため
    774 派生（translateComponent774To775）では欠落し、default の
    copyComponentOpaque776 に落ちて例外になる。775 派生ならラベル付け替えで
    41, 43, 95, 97, 99, 107 として残るため、ここで通常の変換にする。
    776 の新規 1 種（78 sulfur_cube_content）は落とす。"""
    m = extract_method(orig, "translateComponent775To774(Reader reader, Writer writer, int kind775)",
                       "component-down")
    m = strip_javadoc(m)
    m = remap_case_labels(m, KIND_775_TO_776, "component-down")
    m = rename_calls(m, [("775To774", "776To775"),
                         ("copyComponentOpaque775", "copyComponentOpaque776"),
                         ("skipComponentData775", "skipComponentData776"),
                         ("kind775", "kind776")])
    m = m.replace("773・774・775とも中身なし", "773・774・775・776とも中身なし")
    # 775 の新規 6 種は 776 でも同じ成分として存在する（番号は 78 の挿入でずれる）。
    # 775→774 では落としていたが、776→775 では通常の変換にする。
    old_drop = """            case 41, 43, 95, 97, 99, 107 -> {
                // 775 の新規成分。774 に無いため中身を読み飛ばして落とす。
                skipComponentData776(reader, kind776);
                return false;
            }"""
    new_drop = """            case 41, 43, 95, 97, 107 -> writer.writeVarInt(reader.readVarInt()); // additional_trade_cost／dye／pig／cow／cat の sound_variant（775・776とも同じ成分・varint のためそのまま写す）
            case 99 -> { // chicken の sound_variant（Holder。775=98→776=99。0 なら直書きの文字列）
                int holder = reader.readVarInt();
                writer.writeVarInt(holder);
                if (holder == 0) {
                    writer.writeString(reader.readString());
                }
            }"""
    assert old_drop in m, "component-down: drop ブロックが見つからない"
    m = m.replace(old_drop, new_drop)
    old_default = "            default -> copyComponentOpaque776(reader, writer, kind776);"
    new_default = """            case 78 -> { // sulfur_cube_content（776 の新規成分。775 に無いため中身を読み飛ばして落とす）
                skipComponentData776(reader, kind776);
                return false;
            }
            default -> copyComponentOpaque776(reader, writer, kind776);"""
    assert old_default in m, "component-down: default が見つからない"
    m = m.replace(old_default, new_default)
    # シグネチャ名は上の包括 rename（775To774→776To775）で既に変わっている。
    javadoc = """    /**
     * 成分1個を776→775向けに変換する。種別番号は776の番号で dispatch する
     * （776は111件、775は110件。KIND 表の対応は vv-26.2.json の
     * data_component_type による）。775に無い新規 1 種（78 sulfur_cube_content）は
     * 中身を読み飛ばして {@code false} を返す（呼び出し側が落とす）。
     * レジストリIDを付け替えるものだけ変換する。
     * 49・50 の中身は ItemStackTemplate のまま、75 container は
     * option 付き ItemStackTemplate の列のまま。
     * 98 chicken/variant、100 zombie_nautilus/variant は Holder のまま
     * （775・776とも registryEntryHolder）。
     */"""
    return _with_javadoc(javadoc, m)


def build_skip_component(orig):
    """skipComponentData776。派生元は 775 節の skipComponentData775
    （775 の種別番号で dispatch。ラベルは KIND_775_TO_776 で付け替える）。"""
    m = extract_method(orig, "skipComponentData775(Reader reader, int kind775)",
                       "skip-component")
    m = strip_javadoc(m)
    m = remap_case_labels(m, KIND_775_TO_776, "skip-component")
    m = rename_calls(m, [
        ("skipComponentData774", "skipComponentData775"),
        ("skipItemStackTemplate775", "skipItemStackTemplate776"),
        ("skipBlocksAttacks775", "skipBlocksAttacks776"),
        ("skipProvidesTrimMaterial775", "skipProvidesTrimMaterial776"),
        ("skipPaintingVariantHolder775", "skipPaintingVariantHolder776"),
        ("mapComponentKind775To774", "mapComponentKind776To775"),
        ("kind774", "\x01KIND774\x01"),
        ("kind775", "kind776"),
        ("\x01KIND774\x01", "kind775"),
        ("未知の成分種別です（775）", "未知の成分種別です（776）"),
    ])
    old_default = "            default -> {"
    new_default = """            case 78 -> skipItemStackTemplate776(reader); // sulfur_cube_content（ItemStackTemplate 1個）
            default -> {"""
    assert old_default in m, "skip-component: default が見つからない"
    m = m.replace(old_default, new_default, 1)
    m = m.replace("skipComponentData775(Reader reader, int kind776)",
                  "skipComponentData776(Reader reader, int kind776)")
    javadoc = """    /**
     * 成分1個の中身を読み飛ばす（776形式）。translateComponent776To775 から
     * 776 にだけある sulfur_cube_content（78）を落とす場合にだけ呼ばれる。
     * 種別番号は776の番号で dispatch する（776は111件、775は110件）。
     */"""
    return _with_javadoc(javadoc, m)


# --- メタデータ型番号 ---------------------------------------------------

def build_metadata_type_up():
    return """    /**
     * メタデータの型番号を775→776に付け替える。26.1／26.2 両 jar の
     * EntityDataSerializers の宣言順が一致するため、型番号は 0〜42・
     * 同じ順序で恒等写像。
     */
    private static int mapMetadataType775To776(int type775) {
        return type775;
    }
"""


def build_metadata_type_down():
    return """    /**
     * メタデータの型番号を776→775に付け替える。26.1／26.2 両 jar の
     * EntityDataSerializers の宣言順が一致するため、型番号は 0〜42・
     * 同じ順序で恒等写像。落とす型は無い。
     */
    private static int mapMetadataType776To775(int type776) {
        return type776;
    }
"""


# --- メタデータ値 -------------------------------------------------------

def build_metadata_value_up(orig):
    """translateMetadataValue775To776。派生元は 775 節の
    translateMetadataValue775To774（775 の型番号で dispatch。ラベルはそのまま）。"""
    m = extract_method(orig, "translateMetadataValue775To774(Reader reader, Writer writer, int type)",
                       "metadata-value-up")
    m = strip_javadoc(m)
    m = rename_calls(m, [
        ("translateSlot775To774", "translateSlot775To776"),
        ("translateParticle775To774", "translateParticle775To776"),
        ("translateWolfVariantHolder775To774", "translateWolfVariantHolder775To776"),
        ("translatePaintingVariantHolder775To774", "translatePaintingVariantHolder775To776"),
        ("mapBlockStateUp775", "mapBlockState775To776"),
    ])
    old_drop = """            case 22, 24, 29, 31 -> { // cat／cow／pig／chicken の sound_variant（774に無い）
                reader.readVarInt();
                return false;
            }"""
    new_drop = "            case 22, 24, 29, 31 -> writer.writeVarInt(reader.readVarInt()); // cat／cow／pig／chicken の sound_variant（775・776とも同じ型番号のためそのまま写す）"
    assert old_drop in m, "metadata-value-up: drop ブロックが見つからない"
    m = m.replace(old_drop, new_drop)
    m = m.replace("// resolvable_profile（774・775で同一形式）",
                  "// resolvable_profile（775・776で同一形式）")
    m = _rename_signature(m, "translateMetadataValue775To774", "translateMetadataValue775To776")
    javadoc = """    /**
     * メタデータの値1個を775→776向けに変換する。落とすエントリは無い
     * （{@code false} を返すことは無い）。型番号は775側の番号で dispatch する。
     * 775・776とも型は42まで（0–42）・同じ順序。
     * 型番号の付け替えは mapMetadataType775To776 が行う（恒等写像）。
     */"""
    return _with_javadoc(javadoc, m)


def build_metadata_value_down(orig):
    """translateMetadataValue776To775。派生元は 775 節の
    translateMetadataValue775To774（775 の型番号で dispatch。775・776 の
    型番号は同一のためラベルはそのまま）。
    774 派生（translateMetadataValue774To775 を 774→776 の型番号表で
    付け替える）は誤りのため使わない。774→775 の型番号表は 774 型 23（wolf）・
    27（chicken）・28（zombie）・29（global_pos）・30（painting）で誤った
    番号を返す（775 の mapMetadataType774To775 の潜在バグ。26.1 jar の
    EntityDataSerializers 登録順で確認。775 は対象外のため直さない）。
    774 派生では 24・29・32・33 がずれ、22・25・31・34 が欠落するうえ、
    追加した case 22, 24, 29, 31 が重複してコンパイルも通らない。"""
    m = extract_method(orig, "translateMetadataValue775To774(Reader reader, Writer writer, int type)",
                       "metadata-value-down")
    m = strip_javadoc(m)
    m = rename_calls(m, [
        ("translateSlot775To774", "translateSlot776To775"),
        ("translateParticle775To774", "translateParticle776To775"),
        ("translateWolfVariantHolder775To774", "translateWolfVariantHolder776To775"),
        ("translatePaintingVariantHolder775To774", "translatePaintingVariantHolder776To775"),
        ("mapBlockStateUp775", "mapBlockStateUp776"),
    ])
    old_drop = """            case 22, 24, 29, 31 -> { // cat／cow／pig／chicken の sound_variant（774に無い）
                reader.readVarInt();
                return false;
            }"""
    new_drop = "            case 22, 24, 29, 31 -> writer.writeVarInt(reader.readVarInt()); // cat／cow／pig／chicken の sound_variant（775・776とも同じ型番号のためそのまま写す）"
    assert old_drop in m, "metadata-value-down: drop ブロックが見つからない"
    m = m.replace(old_drop, new_drop)
    m = m.replace("// resolvable_profile（774・775で同一形式）",
                  "// resolvable_profile（775・776で同一形式）")
    m = _rename_signature(m, "translateMetadataValue775To774", "translateMetadataValue776To775")
    javadoc = """    /**
     * メタデータの値1個を776→775向けに変換する。落とすエントリは無い
     * （{@code false} を返すことは無い）。型番号は776側の番号で dispatch する。
     * 775・776とも型は42まで（0–42）・同じ順序。
     * 22 cat_sound、23 cow、24 cow_sound、25 wolf_variant、26 wolf_sound、
     * 27 frog、28 pig、29 pig_sound、30 chicken、31 chicken_sound、
     * 32 zombie_nautilus_variant、33 optional_global_pos、34 painting。
     * 型番号の付け替えは mapMetadataType776To775 が行う（恒等写像）。
     */"""
    return _with_javadoc(javadoc, m)


# --- パーティクルデータ -------------------------------------------------

def build_particle_data_up(orig):
    """translateParticleData775To776。派生元は 775 節の
    translateParticleData774To775（774 の粒子IDで dispatch。ラベルは
    774→775 の粒子表で付け替える）。"""
    m = extract_method(orig, "translateParticleData774To775(Reader reader, Writer writer, int particleId)",
                       "particle-data-up")
    m = strip_javadoc(m)
    m = remap_case_labels(m, PARTICLE_774_TO_775, "particle-data-up")
    m = rename_calls(m, [
        ("mapBlockState774To775", "mapBlockState775To776"),
        ("translateSlot774To775AsTemplate", "translateItemStackTemplate775To776"),
    ])
    m = m.replace("// item：Slot から ItemStackTemplate へ",
                  "// item：ItemStackTemplate のまま")
    m = _rename_signature(m, "translateParticleData774To775", "translateParticleData775To776")
    javadoc = """    /** パーティクルのデータ部分を775→776向けに変換する（particleId は775のID）。4〜10 の挿入で 4番以降が+7ずれる。item のデータは ItemStackTemplate のまま。tinted_leaves／dragon_breath／effect／instant_effect／flash もデータ付きで変換する。 */"""
    return _with_javadoc(javadoc, m)


def build_particle_data_down(orig):
    """translateParticleData776To775。派生元は 775 節の
    translateParticleData775To774（775 の粒子IDで dispatch。ラベルは
    775→776 の粒子表で付け替える）。新規 8 種は 775 に無いため呼び出し側が
    entity_effect（775=21）にフォールバックする。entity_effect は色の
    i32 が要るため、データの無い 4・5・6・124 は色 0 を合成し、データ付きの
    geyser 系 4 種はデータを読み飛ばしたうえで色 0 を合成する（765→766 の
    translateParticle765To766 と同じ設計。色を書かないとパケットが壊れる）。"""
    m = extract_method(orig, "translateParticleData775To774(Reader reader, Writer writer, int particleId)",
                       "particle-data-down")
    m = strip_javadoc(m)
    m = remap_case_labels(m, PARTICLE_775_TO_776, "particle-data-down")
    m = rename_calls(m, [
        ("mapBlockStateUp775", "mapBlockStateUp776"),
        ("translateItemStackTemplate775To774AsSlot", "translateItemStackTemplate776To775"),
    ])
    old_default = """            default -> {
                // その他の粒子にデータは無い。
            }"""
    new_default = """            case 4, 5, 6, 124 -> writer.writeInt(0); // sulfur_bubbles／noxious_gas／noxious_gas_cloud／sulfur_cube_goo（776 の新規粒子。データ無し。entity_effect にフォールバックするため色の i32 として 0 を合成する）
            case 7 -> { // geyser（776 の新規粒子。waterBlocks の i32 を読み飛ばす。entity_effect にフォールバックするため色の i32 として 0 を合成する）
                reader.readInt();
                writer.writeInt(0);
            }
            case 8, 9 -> { // geyser_base／geyser_poof（776 の新規粒子。waterBlocks の i32＋burstImpulseBase の f32 を読み飛ばす。entity_effect にフォールバックするため色の i32 として 0 を合成する）
                reader.readInt();
                reader.readFloat();
                writer.writeInt(0);
            }
            case 10 -> { // geyser_plume（776 の新規粒子。waterBlocks の i32 を読み飛ばす。entity_effect にフォールバックするため色の i32 として 0 を合成する）
                reader.readInt();
                writer.writeInt(0);
            }
            default -> {
                // その他の粒子にデータは無い。
            }"""
    assert old_default in m, "particle-data-down: default が見つからない"
    m = m.replace(old_default, new_default)
    m = m.replace("// item：ItemStackTemplate から Slot へ",
                  "// item：ItemStackTemplate のまま")
    m = _rename_signature(m, "translateParticleData775To774", "translateParticleData776To775")
    javadoc = """    /** パーティクルのデータ部分を776→775向けに変換する（particleId は776のID）。4〜10 の挿入で 4番以降が+7ずれる。776にだけある 8 種（sulfur_bubbles／noxious_gas／noxious_gas_cloud／geyser／geyser_base／geyser_poof／geyser_plume／sulfur_cube_goo）は entity_effect（775=21）にフォールバックし、色の i32 として 0 を合成する。item のデータは ItemStackTemplate のまま。 */"""
    return _with_javadoc(javadoc, m)


# --- スロット配列／use_remainder／container（成分内の Template） ------

def build_slot_array_up(orig):
    """translateSlotArray775To776。派生元は translateSlotArray774To775。
    775・776とも中身は ItemStackTemplate のため AsTemplate ではなく
    Template ヘルパーを呼ぶ。"""
    m = extract_method(orig, "translateSlotArray774To775(Reader reader, Writer writer)",
                       "slot-array-up")
    m = strip_javadoc(m)
    m = rename_calls(m, [("translateSlot774To775AsTemplate", "translateItemStackTemplate775To776")])
    m = _rename_signature(m, "translateSlotArray774To775", "translateSlotArray775To776")
    javadoc = """    /** スロットの配列を775→776向けに変換する。中身は ItemStackTemplate のまま（775・776とも）。 */"""
    return _with_javadoc(javadoc, m)


def build_slot_array_down(orig):
    """translateSlotArray776To775。派生元は translateSlotArray775To774。"""
    m = extract_method(orig, "translateSlotArray775To774(Reader reader, Writer writer)",
                       "slot-array-down")
    m = strip_javadoc(m)
    m = rename_calls(m, [("translateItemStackTemplate775To774AsSlot", "translateItemStackTemplate776To775")])
    m = _rename_signature(m, "translateSlotArray775To774", "translateSlotArray776To775")
    javadoc = """    /** スロットの配列を776→775向けに変換する。中身は ItemStackTemplate のまま（775・776とも）。 */"""
    return _with_javadoc(javadoc, m)


def build_use_remainder_up(orig):
    """translateUseRemainder775To776。派生元は translateUseRemainder774To775。"""
    m = extract_method(orig, "translateUseRemainder774To775(Reader reader, Writer writer)",
                       "use-remainder-up")
    m = strip_javadoc(m)
    m = rename_calls(m, [("translateSlot774To775AsTemplate", "translateItemStackTemplate775To776")])
    m = _rename_signature(m, "translateUseRemainder774To775", "translateUseRemainder775To776")
    javadoc = """    /** use_remainder を775→776向けに変換する。ItemStackTemplate のまま（775・776とも）。 */"""
    return _with_javadoc(javadoc, m)


def build_use_remainder_down(orig):
    """translateUseRemainder776To775。派生元は translateUseRemainder775To774。"""
    m = extract_method(orig, "translateUseRemainder775To774(Reader reader, Writer writer)",
                       "use-remainder-down")
    m = strip_javadoc(m)
    m = rename_calls(m, [("translateItemStackTemplate775To774AsSlot", "translateItemStackTemplate776To775")])
    m = _rename_signature(m, "translateUseRemainder775To774", "translateUseRemainder776To775")
    javadoc = """    /** use_remainder を776→775向けに変換する。ItemStackTemplate のまま（775・776とも）。 */"""
    return _with_javadoc(javadoc, m)


def build_container_up(orig):
    """translateContainer775To776。派生元は translateContainer775To774。
    775・776とも option 付き ItemStackTemplate の列のため、option を
    そのまま写して Template ヘルパーを呼ぶ。"""
    m = extract_method(orig, "translateContainer775To774(Reader reader, Writer writer)",
                       "container-up")
    m = strip_javadoc(m)
    old_body = """            if (!reader.readBoolean()) {
                writer.writeVarInt(0); // 空スロット
                continue;
            }
            translateItemStackTemplate775To774AsSlot(reader, writer);"""
    new_body = """            boolean present = reader.readBoolean();
            writer.writeBoolean(present);
            if (!present) {
                continue;
            }
            translateItemStackTemplate775To776(reader, writer);"""
    assert old_body in m, "container-up: 本体が見つからない"
    m = m.replace(old_body, new_body)
    m = _rename_signature(m, "translateContainer775To774", "translateContainer775To776")
    javadoc = """    /**
     * container（775→776）。775・776とも option 付き ItemStackTemplate の列のため、
     * option と Template をそのまま変換する。
     */"""
    return _with_javadoc(javadoc, m)


def build_container_down(orig):
    """translateContainer776To775。派生元は translateContainer774To775。"""
    m = extract_method(orig, "translateContainer774To775(Reader reader, Writer writer)",
                       "container-down")
    m = strip_javadoc(m)
    old_body = """            int slotCount = reader.readVarInt();
            if (slotCount <= 0) {
                writer.writeBoolean(false);
                continue;
            }
            writer.writeBoolean(true);
            // slotCount を先読みした分、Template への変換は count を含めて行う。
            // （translateSlot774To775AsTemplate は count から読むため、ここでは
            //  既に読んだ count と itemId を使って直接書く。）
            int itemId = reader.readVarInt();
            writer.writeVarInt(RegistryRemapper.mapItem774To775(itemId));
            writer.writeVarInt(slotCount);
            translateSlotComponents774To775(reader, writer);"""
    new_body = """            if (!reader.readBoolean()) {
                writer.writeBoolean(false);
                continue;
            }
            writer.writeBoolean(true);
            translateItemStackTemplate776To775(reader, writer);"""
    assert old_body in m, "container-down: 本体が見つからない"
    m = m.replace(old_body, new_body)
    m = _rename_signature(m, "translateContainer774To775", "translateContainer776To775")
    javadoc = """    /**
     * container（776→775）。775・776とも option 付き ItemStackTemplate の列のため、
     * option と Template をそのまま変換する。
     */"""
    return _with_javadoc(javadoc, m)


# --- variant Holder -----------------------------------------------------

def build_variant_holder_up():
    return """    /**
     * chicken／zombie_nautilus variant（775→776）。registryEntryHolder のまま
     * （775・776とも Holder 形式。0 なら直書きの文字列、1 以上なら id）。
     */
    private static void translateVariantHolder775To776(Reader reader, Writer writer) {
        int holder = reader.readVarInt();
        writer.writeVarInt(holder);
        if (holder == 0) {
            writer.writeString(reader.readString());
        }
    }
"""


def build_variant_holder_down():
    return """    /**
     * chicken／zombie_nautilus variant（776→775）。registryEntryHolder のまま
     * （775・776とも Holder 形式。0 なら直書きの文字列、1 以上なら id）。
     */
    private static void translateVariantHolder776To775(Reader reader, Writer writer) {
        int holder = reader.readVarInt();
        writer.writeVarInt(holder);
        if (holder == 0) {
            writer.writeString(reader.readString());
        }
    }
"""


# --- tooltip_display ----------------------------------------------------

def build_tooltip_up():
    return """    /**
     * tooltip_display（775→776）。770・771・772・773・774・775・776とも
     * {hideTooltip bool, hiddenComponents の列}。hiddenComponents の種別番号は
     * KIND 表で付け替える（775 の 110 件はすべて 776 にあるため落とすことは無い）。
     */
    private static void translateTooltipDisplay775To776(Reader reader, Writer writer) {
        writer.writeBoolean(reader.readBoolean()); // hideTooltip
        int hidden = reader.readVarInt();
        Writer kept = new Writer();
        int keptCount = 0;
        for (int i = 0; i < hidden; i++) {
            int kind775 = reader.readVarInt();
            int kind776 = mapComponentKind775To776(kind775);
            if (kind776 < 0) {
                continue;
            }
            kept.writeVarInt(kind776);
            keptCount++;
        }
        writer.writeVarInt(keptCount);
        writer.writeBytes(kept.bytes());
    }
"""


def build_tooltip_down():
    return """    /**
     * tooltip_display（776→775）。770・771・772・773・774・775・776とも
     * {hideTooltip bool, hiddenComponents の列}。hiddenComponents の種別番号は
     * KIND 表で付け替える。sulfur_cube_content（78）は775に無いため落とす。
     */
    private static void translateTooltipDisplay776To775(Reader reader, Writer writer) {
        writer.writeBoolean(reader.readBoolean()); // hideTooltip
        int hidden = reader.readVarInt();
        Writer kept = new Writer();
        int keptCount = 0;
        for (int i = 0; i < hidden; i++) {
            int kind776 = reader.readVarInt();
            int kind775 = mapComponentKind776To775(kind776);
            if (kind775 < 0) {
                continue; // sulfur_cube_content（78）は775に無いため落とす
            }
            kept.writeVarInt(kind775);
            keptCount++;
        }
        writer.writeVarInt(keptCount);
        writer.writeBytes(kept.bytes());
    }
"""


def build_chunk_replacements(orig):
    """orig（775節の原文）から CHUNK_METHOD_REPLACEMENTS を作る。
    各要素は (signature776, new_text)。signature776 は置き換え対象の
    メソッド定義を一意に指す文字列（仮引数まで含める）。"""
    return [
        ("mapBiome775To776(int id)", build_biome_up()),
        ("mapBiomeUp776(int id)", build_biome_down()),
        ("translateComponent775To776(Reader reader, Writer writer, int kind775)",
         build_component_up(orig)),
        ("translateComponent776To775(Reader reader, Writer writer, int kind776)",
         build_component_down(orig)),
        ("skipComponentData776(Reader reader, int kind776)",
         build_skip_component(orig)),
        ("mapMetadataType775To776(int type775)", build_metadata_type_up()),
        ("mapMetadataType776To775(int type776)", build_metadata_type_down()),
        ("translateMetadataValue775To776(Reader reader, Writer writer, int type)",
         build_metadata_value_up(orig)),
        ("translateMetadataValue776To775(Reader reader, Writer writer, int type)",
         build_metadata_value_down(orig)),
        ("translateParticleData775To776(Reader reader, Writer writer, int particleId)",
         build_particle_data_up(orig)),
        ("translateParticleData776To775(Reader reader, Writer writer, int particleId)",
         build_particle_data_down(orig)),
        ("translateSlotArray775To776(Reader reader, Writer writer)",
         build_slot_array_up(orig)),
        ("translateSlotArray776To775(Reader reader, Writer writer)",
         build_slot_array_down(orig)),
        ("translateUseRemainder775To776(Reader reader, Writer writer)",
         build_use_remainder_up(orig)),
        ("translateUseRemainder776To775(Reader reader, Writer writer)",
         build_use_remainder_down(orig)),
        ("translateContainer775To776(Reader reader, Writer writer)",
         build_container_up(orig)),
        ("translateContainer776To775(Reader reader, Writer writer)",
         build_container_down(orig)),
        ("translateVariantHolder775To776(Reader reader, Writer writer)",
         build_variant_holder_up()),
        ("translateVariantHolder776To775(Reader reader, Writer writer)",
         build_variant_holder_down()),
        ("translateTooltipDisplay775To776(Reader reader, Writer writer)",
         build_tooltip_up()),
        ("translateTooltipDisplay776To775(Reader reader, Writer writer)",
         build_tooltip_down()),
    ]


# 776 節の末尾に追加するメソッド（shift 版には無い・shift 版が誤りのため新規）。
EXTRA_METHODS_776 = """    /**
     * ItemStackTemplate 1個を775→776向けに変換する。
     * [itemId][count][added][removed] のまま、itemId を付け替え、
     * 成分は translateSlotComponents775To776 で変換する。
     * （775・776とも ItemStackTemplate のため、空の保険は要らない。）
     */
    private static void translateItemStackTemplate775To776(Reader reader, Writer writer) {
        int itemId = reader.readVarInt();
        int count = reader.readVarInt();
        writer.writeVarInt(RegistryRemapper.mapItem775To776(itemId));
        writer.writeVarInt(count);
        translateSlotComponents775To776(reader, writer);
    }


    /**
     * ItemStackTemplate 1個を776→775向けに変換する。
     * [itemId][count][added][removed] のまま、itemId を付け替え、
     * 成分は translateSlotComponents776To775 で変換する。
     */
    private static void translateItemStackTemplate776To775(Reader reader, Writer writer) {
        int itemId = reader.readVarInt();
        int count = reader.readVarInt();
        writer.writeVarInt(RegistryRemapper.mapItemUp776(itemId));
        writer.writeVarInt(count);
        translateSlotComponents776To775(reader, writer);
    }
"""

# ----------------------------------------------------------------------
# PlayTranslator 776 節
# ----------------------------------------------------------------------

PLAY_776_SECTION = """
    // 775 ⇔ 776（26.2）
    // Join Game／Respawn／Synchronize Player Position／Login Success／
    // Client Information／Player Info／Set Held Item／Vehicle Move／
    // Player Chat は775と776で形式が同一のため変換器は要らない（素通し）。
    // 変わるのは Registry Data（レジストリの順序。26.2での変更は
    // ジュークボックス（bounce 追加で 4番以降+1、21→22曲）・ダメージ種別
    // （sulfur_cube_hot 追加で 42番以降+1、50→51件）。エンチャント・
    // 絵画は同一。minecraft:dialog は771からあるため通す）だけである。
    // Update Time・Enchant Item・Use Entity は775・776で形式が同一のため
    // 素通しする（774→775 で変わったものが 775→776 では変わらない）。
    // なお Registry Data の entity_type・sound・item・particle は775節と同じく
    // 素通し（順序は付け替えない。Spawn Entity の種別 ID だけ翻訳するため 775 と
    // 同じずれが残る。制限事項として記録する）。
    // ------------------------------------------------------------------

    /** Player Info（775 → 776）。形式は同一のため素通しする。 */
    public static byte[] translatePlayerInfo775To776(byte[] payload) {
        return payload;
    }

    /** Player Info（776 → 775）。形式は同一のため素通しする。 */
    public static byte[] translatePlayerInfo776To775(byte[] payload) {
        return payload;
    }

    /** Set Held Item（775 → 776）。形式は同一のため素通しする（varint のまま）。 */
    public static byte[] translateSetHeldItem775To776(byte[] payload) {
        return payload;
    }

    /** Set Held Item（776 → 775）。形式は同一のため素通しする。 */
    public static byte[] translateSetHeldItem776To775(byte[] payload) {
        return payload;
    }

    /** Vehicle Move（775 → 776）。形式は同一のため素通しする（末尾の onGround 付き）。 */
    public static byte[] translateVehicleMove775To776(byte[] payload) {
        return payload;
    }

    /** Vehicle Move（776 → 775）。形式は同一のため素通しする。 */
    public static byte[] translateVehicleMove776To775(byte[] payload) {
        return payload;
    }

    /** Player Chat（775 → 776）。先頭の globalIndex は770・771・772・773・774・775・776ともあるため素通しする。 */
    public static byte[] translatePlayerChat775To776(byte[] payload) {
        return payload;
    }

    /** Player Chat（776 → 775）。先頭の globalIndex は770・771・772・773・774・775・776ともあるため素通しする。 */
    public static byte[] translatePlayerChat776To775(byte[] payload) {
        return payload;
    }

    /**
     * Chat Message／Chat Command Signed（776 → 775）。末尾の checksum
     * （u8 1バイト）を取り除く。ペイロードが空の場合はそのまま返す。
     * （注意：現行の ProxySession は checksum を両版ともあるものとして
     * 素通しするため、このメソッドは未使用。775節の同名メソッドと同じ扱い。）
     */
    public static byte[] stripChatChecksum776(byte[] payload) {
        if (payload.length == 0) {
            return payload;
        }
        return new Reader(payload).readBytes(payload.length - 1);
    }

    /**
     * Chat Message／Chat Command Signed（775 → 776）。末尾に checksum
     * （u8 1バイト）を追加する。checksum の式は未確認のため 0x00 を入れる
     * （実機での確認は未実施。計算式そのものの確認も未実施）。
     * （注意：現行の ProxySession は checksum を両版ともあるものとして
     * 素通しするため、このメソッドは未使用。775節の同名メソッドと同じ扱い。）
     */
    public static byte[] appendChatChecksum776(byte[] payload) {
        Writer writer = new Writer();
        writer.writeBytes(payload);
        writer.writeByte(0);
        return writer.bytes();
    }

    /**
     * 775の Registry Data を776形式にする。minecraft:dialog は771から
     * あるためそのまま通す。エンチャント・絵画は775・776で同一のため
     * 並べ替えは実質そのまま。ジュークボックスは bounce（776=4）の追加で
     * 4番以降が+1ずれる（22曲）。ダメージ種別は sulfur_cube_hot（776=42）の
     * 追加で 42番以降が+1ずれる（51件）。entity_type・sound・item・particle は
     * 素通し（775節と同じ）。それ以外は1レジストリ分のペイロードを返す。
     */
    public static byte[] translateRegistryData775To776(byte[] payload) {
        Map<String, Tag.CompoundTag> reordered = reorderRegistries(
                parseRegistryData(payload),
                ProtocolVersion.V26_1, ProtocolVersion.V26_2);
        List<byte[]> built = buildRegistryData766(reordered);
        if (built.isEmpty()) {
            throw new IllegalArgumentException("Registry Data が空です。");
        }
        return built.get(0);
    }

    /**
     * 776の Registry Data を775形式にする。minecraft:dialog は775にも
     * あるため通す（reorderRegistries）。エンチャント・絵画は775・776で
     * 同一のため並べ替えは実質そのまま。ジュークボックスは bounce（776=4）を
     * 落とし 5番以降を-1する（22曲→21曲）。ダメージ種別は sulfur_cube_hot
     * （776=42）を落とし 43番以降を-1する（51件→50件）。
     * entity_type・sound・item・particle は素通し（775節と同じ）。
     * それ以外は1レジストリ分のペイロードを返す。空になった場合は {@code null} を返す。
     */
    public static byte[] translateRegistryData776To775(byte[] payload) {
        Map<String, Tag.CompoundTag> reordered = reorderRegistries(
                parseRegistryData(payload),
                ProtocolVersion.V26_2, ProtocolVersion.V26_1);
        List<byte[]> built = buildRegistryData766(reordered);
        return built.isEmpty() ? null : built.get(0);
    }

    /**
     * Update Time（775 → 776）。775・776とも {age i64, clockUpdates[
     * {holder varint, totalTicks varlong, partialTick f32, rate f32}]} のため
     * 素通しする。774→775 で {age, time, tickDayTime} から再構成したものが、
     * 775→776 では変わらない。
     * （26.1／26.2 両 jar で ClientboundSetTimePacket の形式が同一であることを
     * 確認済み。holder は varint で 0＝直書き、N≥1＝レジストリ id N−1。
     * 775 の翻訳子の「id varint」モデルは実はこの Holder 符号化だった。）
     */
    public static byte[] translateUpdateTime775To776(byte[] payload) {
        return payload;
    }

    /**
     * Update Time（776 → 775）。775・776とも {age i64, clockUpdates[...]} のため
     * 素通しする。774→775 の再構成は要らない。
     * （26.1／26.2 両 jar で ClientboundSetTimePacket の形式が同一であることを確認済み。）
     */
    public static byte[] translateUpdateTime776To775(byte[] payload) {
        return payload;
    }

    /**
     * Enchant Item（775 → 776）。windowId・enchantment とも varint
     * （775・776とも）のため素通しする。774 では enchantment が i8 だったが、
     * 775 で varint に戻った。775→776 では変わらない。
     */
    public static byte[] translateEnchantItem775To776(byte[] payload) {
        return payload;
    }

    /**
     * Enchant Item（776 → 775）。windowId・enchantment とも varint
     * （775・776とも）のため素通しする。
     */
    public static byte[] translateEnchantItem776To775(byte[] payload) {
        return payload;
    }

    /**
     * Use Entity（775 → 776）。775・776とも {target varint, hand varint,
     * location lpVec3（f32×3）, usingSecondaryAction bool} のため素通しする。
     * （26.1／26.2 両 jar で ServerboundInteractPacket の形式が同一であることを
     * 確認済み。775 の「sneaking」は usingSecondaryAction。
     * mouse==0 の location 補完の仮定は 775→776 では問題にならない。）
     */
    public static byte[] translateUseEntity775To776(byte[] payload) {
        return payload;
    }

    /**
     * Use Entity（776 → 775）。775・776とも {target varint, hand varint,
     * location lpVec3, usingSecondaryAction bool} のため素通しする。
     * （26.1／26.2 両 jar で ServerboundInteractPacket の形式が同一であることを確認済み。）
     */
    public static byte[] translateUseEntity776To775(byte[] payload) {
        return payload;
    }

"""


# PlayTranslator.java の Registry Data まわりに足す 776（V26_2）。
# （775 のときは enchantment／damage_type／jukebox／絵画が同一で
#  並べ替えは素通し、jukebox_song／dialog のフィルタと targetNames に
#  775 を足した。776 では jukebox_song と damage_type が変わるので
#  並べ替え（mapper）と targetNames が要る。）
PLAY_REGISTRY_FIXES = [
    # enchantment の mapper：775・776とも43件・同一順序（恒等写像だが
    # registryIdMapper の switch に 775776／776775 が無いと null になる）。
    (
        "                case 774775 -> RegistryRemapper::mapEnchantment774To775;\n"
        "                case 775774 -> RegistryRemapper::mapEnchantment775To774;",
        "                case 774775 -> RegistryRemapper::mapEnchantment774To775;\n"
        "                case 775774 -> RegistryRemapper::mapEnchantment775To774;\n"
        "                case 775776 -> RegistryRemapper::mapEnchantment775To776;\n"
        "                case 776775 -> RegistryRemapper::mapEnchantment776To775;",
    ),
    # jukebox_song の mapper：bounce（776=4）の追加で 4番以降+1。
    (
        "                case 774775 -> RegistryRemapper::mapJukeboxSong774To775;\n"
        "                case 775774 -> RegistryRemapper::mapJukeboxSong775To774;",
        "                case 774775 -> RegistryRemapper::mapJukeboxSong774To775;\n"
        "                case 775774 -> RegistryRemapper::mapJukeboxSong775To774;\n"
        "                case 775776 -> RegistryRemapper::mapJukeboxSong775To776;\n"
        "                case 776775 -> RegistryRemapper::mapJukeboxSong776To775;",
    ),
    # damage_type の mapper：sulfur_cube_hot（776=42）の追加で 42番以降+1。
    (
        "                case 774775 -> RegistryRemapper::mapDamageType774To775;\n"
        "                case 775774 -> RegistryRemapper::mapDamageType775To774;",
        "                case 774775 -> RegistryRemapper::mapDamageType774To775;\n"
        "                case 775774 -> RegistryRemapper::mapDamageType775To774;\n"
        "                case 775776 -> RegistryRemapper::mapDamageType775To776;\n"
        "                case 776775 -> RegistryRemapper::mapDamageType776To775;",
    ),
    # enchantment の targetNames：776 も 774 と同一の43件。
    (
        "                case 775 -> ENCHANTMENT_774_NAMES; // 774と同一の43件",
        "                case 775 -> ENCHANTMENT_774_NAMES; // 774と同一の43件\n"
        "                case 776 -> ENCHANTMENT_774_NAMES; // 775と同一の43件",
    ),
    # jukebox_song の targetNames：776 は 22曲（bounce は index 4）。
    (
        "                case 775 -> RegistryTables773.JUKEBOX_SONG_773_NAMES; // 774と同一の21曲",
        "                case 775 -> RegistryTables773.JUKEBOX_SONG_773_NAMES; // 774と同一の21曲\n"
        "                case 776 -> RegistryRemapper.JUKEBOX_SONG_776_NAMES; // 22曲、bounce は index 4",
    ),
    # damage_type の targetNames：776 は 51件（sulfur_cube_hot は index 42）。
    # 名前表は PlayTranslator 内に DAMAGE_776_NAMES として足す
    # （RegistryRemapper には damage_type の名前表が無い）。
    (
        "                case 775 -> DAMAGE_774_NAMES; // 774と同一の50件",
        "                case 775 -> DAMAGE_774_NAMES; // 774と同一の50件\n"
        "                case 776 -> DAMAGE_776_NAMES; // 51件、sulfur_cube_hot は index 42",
    ),
    # DAMAGE_776_NAMES：DAMAGE_774_NAMES（50件）に sulfur_cube_hot を
    # index 42 に挿入した 51件。775→776 は 42番以降が+1ずれるため、
    # 42番以降の名前が1つずつ後ろへずれる。DAMAGE_774_NAMES の直後に置く。
    (
        "            \"minecraft:wither\", \"minecraft:wither_skull\",\n"
        "    };\n"
        "\n"
        "    /**\n"
        "     * レジストリの順序が変わった組み合わせで、送信側の数値 id から",
        "            \"minecraft:wither\", \"minecraft:wither_skull\",\n"
        "    };\n"
        "\n"
        "    /** 776のダメージ種別の名前（51件。775 の 50 件に sulfur_cube_hot\n"
        "        が index 42 に追加。26.2 の datapack で確認）。 */\n"
        "    private static final String[] DAMAGE_776_NAMES = {\n"
        "            \"minecraft:arrow\", \"minecraft:bad_respawn_point\", \"minecraft:cactus\", \"minecraft:campfire\", \"minecraft:cramming\", \"minecraft:dragon_breath\",\n"
        "            \"minecraft:drown\", \"minecraft:dry_out\", \"minecraft:ender_pearl\", \"minecraft:explosion\", \"minecraft:fall\", \"minecraft:falling_anvil\",\n"
        "            \"minecraft:falling_block\", \"minecraft:falling_stalactite\", \"minecraft:fireball\", \"minecraft:fireworks\", \"minecraft:fly_into_wall\", \"minecraft:freeze\",\n"
        "            \"minecraft:generic\", \"minecraft:generic_kill\", \"minecraft:hot_floor\", \"minecraft:in_fire\", \"minecraft:in_wall\", \"minecraft:indirect_magic\",\n"
        "            \"minecraft:lava\", \"minecraft:lightning_bolt\", \"minecraft:mace_smash\", \"minecraft:magic\", \"minecraft:mob_attack\", \"minecraft:mob_attack_no_aggro\",\n"
        "            \"minecraft:mob_projectile\", \"minecraft:on_fire\", \"minecraft:out_of_world\", \"minecraft:outside_border\", \"minecraft:player_attack\", \"minecraft:player_explosion\",\n"
        "            \"minecraft:sonic_boom\", \"minecraft:spear\", \"minecraft:spit\", \"minecraft:stalagmite\", \"minecraft:starve\", \"minecraft:sting\",\n"
        "            \"minecraft:sulfur_cube_hot\", \"minecraft:sweet_berry_bush\", \"minecraft:thorns\", \"minecraft:thrown\", \"minecraft:trident\", \"minecraft:unattributed_fireball\",\n"
        "            \"minecraft:wind_charge\", \"minecraft:wither\", \"minecraft:wither_skull\",\n"
        "    };\n"
        "\n"
        "    /**\n"
        "     * レジストリの順序が変わった組み合わせで、送信側の数値 id から",
    ),
    # jukebox_song のフィルタ：776 にもあるため落とさない。
    (
        "                    && toProtocol != ProtocolVersion.V1_21_11\n"
        "                    && toProtocol != ProtocolVersion.V26_1) {\n"
        "                continue;\n"
        "            }\n"
        "            if (registryId.equals(\"minecraft:dialog\")",
        "                    && toProtocol != ProtocolVersion.V1_21_11\n"
        "                    && toProtocol != ProtocolVersion.V26_1\n"
        "                    && toProtocol != ProtocolVersion.V26_2) {\n"
        "                continue;\n"
        "            }\n"
        "            if (registryId.equals(\"minecraft:dialog\")",
    ),
    # dialog のフィルタ：776 にもあるため落とさない。
    (
        "                    && toProtocol != ProtocolVersion.V1_21_11\n"
        "                    && toProtocol != ProtocolVersion.V26_1) {\n"
        "                continue;\n"
        "            }\n"
        "            List<Tag> entries = registry.getValue().getList(\"value\")",
        "                    && toProtocol != ProtocolVersion.V1_21_11\n"
        "                    && toProtocol != ProtocolVersion.V26_1\n"
        "                    && toProtocol != ProtocolVersion.V26_2) {\n"
        "                continue;\n"
        "            }\n"
        "            List<Tag> entries = registry.getValue().getList(\"value\")",
    ),
]

# ----------------------------------------------------------------------
# ProxySession 設定フェーズ 776 ハンドラ（未定義の 8 件だけ）
#
# 2026-11-15 時点で ProxySession.java には 776 ハンドラが 14 件既にある
# ため、この生成器が足すのはディスパッチから参照されているのに未定義の
# 8 件だけ：776From774／776From773／776From772／776From771／
# 774From776／772From776／771From776／770From776。重複してはいけない。
# 適用は親の「ProxySession is free」許可が出てから（main() は既定で飛ばす）。
# ----------------------------------------------------------------------

# クライアント版 → ProtocolVersion 定数（辞書の reorder 先）。
_CONFIG_CLIENT_VERSION = {
    774: "V1_21_11",
    772: "V1_21_7",
    771: "V1_21_6",
    770: "V1_21_5",
}

# 段数（日本語）。
_CONFIG_STEPS_JA = {2: "二段", 3: "三段", 4: "四段", 5: "五段", 6: "六段"}


def _config_common_cases(server_ver, client_ver, registry_case, server_label):
    """設定ハンドラの Registry Data 以外の共通 case（0x00〜0x06、0x08〜0x10、
    0x11／0x12、default）。server_label は default の警告に出す版
    （サーバー側の版）。"""
    return f"""            case 0x00 -> {{ // Cookie Request：空の Cookie Response を返す
                String key = readCookieKey(packet.payload());
                yield Outcome.toServer(new MinecraftPacket(0x01,
                        emptyCookieResponsePayload(key)));
            }}
            case 0x01 -> Outcome.toClient(new MinecraftPacket(0x01, packet.payload())); // Custom Payload（同ID）
            case 0x02 -> // Disconnect（{server_ver}は NBT。{client_ver}も NBT）
                    Outcome.toClient(packet);
            case 0x03 -> // Finish Configuration
                    Outcome.toClient(packet);
            case 0x04 -> Outcome.toClient(packet); // Keep Alive（同ID）
            case 0x05 -> Outcome.toClient(packet); // Ping（同ID）
            case 0x06 -> Outcome.toClient(packet); // Reset Chat（同ID）
{registry_case}
            case 0x08 -> Outcome.toClient(packet); // Remove Resource Pack（同ID）
            case 0x09 -> {{ // Add Resource Pack（{server_ver}：UUID付き）
                PendingPack pack = PlayTranslator.parseConfigAddResourcePack(packet.payload());
                lastSentPack = pack;
                yield Outcome.toClient(new MinecraftPacket(0x09,
                        PlayTranslator.translateConfigAddResourcePack764To765(
                                pack, packOverrideForClient())));
            }}
            case 0x0A -> Outcome.toClient(packet); // Store Cookie（同ID）
            case 0x0B -> Outcome.toClient(packet); // Transfer（同ID）
            case 0x0C -> Outcome.toClient(packet); // Feature Flags（同ID）
            case 0x0D -> {{ // Tags：レジストリ番号がずれるため落とす
                LOG.info(() -> "レジストリ番号がずれるため Tags を{client_ver}向けに落とします（" + playerName + "）。");
                yield Outcome.drop();
            }}
            case 0x0E -> // Select Known Packs：空の一覧を返す
                    Outcome.toServer(new MinecraftPacket(0x07, emptySelectKnownPacksPayload()));
            case 0x0F -> Outcome.toClient(packet); // Custom Report Details（同ID）
            case 0x10 -> Outcome.toClient(packet); // Server Links（同ID）
            case 0x11, 0x12 -> // Clear Dialog（0x11）／Show Dialog（0x12）は771・772・773・774・775・776とも同ID・同形式のため素通し
                    Outcome.toClient(packet);
            default -> {{
                LOG.warning("{server_label}の設定フェーズで想定外のパケット（id=0x"
                        + Integer.toHexString(packet.id()) + "）を落とします。");
                yield Outcome.drop();
            }}"""


def build_config_up_handler(server_ver):
    """776クライアント＋内部 server_ver（774／773／772／771）。
    Registry Data は server_ver→…→775→776 へネストで並べ替える
    （既存の 776From775／776From768 と同じ形）。"""
    chain = " → ".join(str(v) for v in range(server_ver, 777))
    # ネストを段ごとに改行する（既存の 776From768 と同じ見た目）。
    lines = []
    lines.append("                        PlayTranslator.translateRegistryData775To776(")
    for a in range(775, server_ver, -1):
        lines.append(" " * (24 + 16 * (775 - a)) +
                     f"PlayTranslator.translateRegistryData{a - 1}To{a}(")
    lines.append(" " * (24 + 16 * (776 - server_ver)) + "packet.payload()" +
                 ")" * (776 - server_ver))
    nested = "\n".join(lines)
    registry_case = f"""            case 0x07 -> {{ // Registry Data：{server_ver}のエントリ配列を776の順序に並べ替える
                Map<String, Nbt.Tag.CompoundTag> parsed =
                        PlayTranslator.parseRegistryData(packet.payload());
                registries.putAll(parsed);
                yield Outcome.toClient(new MinecraftPacket(0x07,
{nested}));
            }}"""
    javadoc = f"""    /**
     * 776クライアント＋内部{server_ver}：設定フェーズを中継する。
     * {server_ver}と776の設定フェーズの番号は同じため、ほとんど素通しする。
     * Registry Data だけ{chain}へ{_CONFIG_STEPS_JA[776 - server_ver]}で並べ替え、Tags はレジストリ番号が
     * ずれるため落とす。Clear Dialog（0x11）／Show Dialog（0x12）は771・772・773・774・775・776とも
     * 同ID・同形式のため素通しする。Cookie Request には空の Cookie Response を、
     * Select Known Packs には空の一覧をプロキシがクライアント役として返す。
     */"""
    body = _config_common_cases(server_ver, 776, registry_case, f"{server_ver}")
    return (javadoc + "\n" +
            f"    private Outcome handleServerConfiguration776From{server_ver}(MinecraftPacket packet) {{\n" +
            "        return switch (packet.id()) {\n" + body + "\n        };\n    }\n")


def build_config_down_handler(client_ver):
    """client_ver（774／772／771／770）クライアント＋内部776。
    Registry Data は 776→775→…→client_ver へ順次並べ替える
    （既存の 772From775 と同じ形。null の段があれば落とす）。"""
    steps = list(range(776, client_ver, -1))  # [776, 775, ...]（from 側）
    chain = " → ".join(str(v) for v in range(776, client_ver - 1, -1))
    target = _CONFIG_CLIENT_VERSION[client_ver]
    lines = []
    lines.append(f"            case 0x07 -> {{ // Registry Data：776のエントリ配列を{client_ver}の順序に並べ替える")
    lines.append("                Map<String, Nbt.Tag.CompoundTag> parsed =")
    lines.append("                        PlayTranslator.parseRegistryData(packet.payload());")
    lines.append(f"                // {client_ver}向けの辞書は{client_ver}形式で蓄える。")
    lines.append("                registries.putAll(PlayTranslator.reorderRegistries(")
    lines.append(f"                        parsed, ProtocolVersion.V26_2, ProtocolVersion.{target}));")
    prev = "packet.payload()"
    for i, a in enumerate(steps):
        var = f"payload{a}to{a - 1}"
        if i == len(steps) - 1:
            var = "payload"
        lines.append(f"                byte[] {var} =")
        lines.append(f"                        PlayTranslator.translateRegistryData{a}To{a - 1}({prev});")
        lines.append(f"                if ({var} == null) {{")
        lines.append("                    yield Outcome.drop();")
        lines.append("                }")
        prev = var
    lines.append("                yield Outcome.toClient(new MinecraftPacket(0x07, payload));")
    lines.append("            }")
    registry_case = "\n".join(lines)
    dialog_note = (f"（minecraft:dialog は{client_ver}にもあるため通し、"
                   "jukebox_song の bounce・damage_type の sulfur_cube_hot は落とす）"
                   if client_ver == 774 else
                   f"（minecraft:dialog は{client_ver}にもあるため通す）")
    javadoc = f"""    /**
     * {client_ver}クライアント＋内部776：設定フェーズを中継する。
     * {client_ver}と776の設定フェーズの番号は同じため、ほとんど素通しする。
     * Registry Data だけ{chain}へ{_CONFIG_STEPS_JA[776 - client_ver]}で並べ替え{dialog_note}、Tags はレジストリ番号が
     * ずれるため落とす。Clear Dialog（0x11）／Show Dialog（0x12）は771・772・773・774・775・776とも
     * 同ID・同形式のため素通しする。Cookie Request には空の Cookie Response を、
     * Select Known Packs には空の一覧をプロキシがクライアント役として返す。
     */"""
    body = _config_common_cases(776, client_ver, registry_case, "776")
    return (javadoc + "\n" +
            f"    private Outcome handleServerConfiguration{client_ver}From776(MinecraftPacket packet) {{\n" +
            "        return switch (packet.id()) {\n" + body + "\n        };\n    }\n")


def build_proxysession_config_section_776():
    """未定義の 8 件だけを並べた設定フェーズ節。"""
    parts = []
    for server_ver in (774, 773, 772, 771):
        parts.append(build_config_up_handler(server_ver))
    for client_ver in (774, 772, 771, 770):
        parts.append(build_config_down_handler(client_ver))
    return "\n".join(parts)


PROXYSESSION_CONFIG_SECTION_776 = build_proxysession_config_section_776()


# ----------------------------------------------------------------------
# main
# ----------------------------------------------------------------------

def main():
    apply_ps = "--apply-proxysession" in sys.argv
    if "--skip-proxysession" in sys.argv and apply_ps:
        raise SystemExit("--apply-proxysession と --skip-proxysession は同時に指定できない")

    # --- ChunkTranslator 776 節 ---
    # 775節だけを抜き出す（775節はファイル末尾のため extract_section_to_eof）。
    chunk_sec, cs, ce = extract_section_to_eof(CT, "// 774 ⇔ 775（26.1）")
    print(f"chunk section: lines {cs + 1}-{ce + 1} ({len(chunk_sec)} chars)")
    chunk_shifted = shift(chunk_sec)
    print(f"chunk shifted: {len(chunk_shifted)} chars")

    chunk776 = apply_fixes(chunk_shifted, CHUNK_STRING_FIXES, "chunk-string")
    for sig, new_text in build_chunk_replacements(chunk_sec):
        chunk776 = replace_method(chunk776, sig, new_text, "chunk")
    for sig in DELETE_METHODS:
        chunk776 = delete_method(chunk776, sig, "chunk-delete")
    chunk776 = chunk776.rstrip() + "\n" + EXTRA_METHODS_776
    print(f"chunk776: {len(chunk776)} chars, "
          f"{chunk776.count('translate')} translate occurrences")
    insert_section_before_class_close(
        CT, chunk776, "chunk", "translateSlot775To776")

    # --- PlayTranslator 776 節（手書き） ---
    insert_section_before_class_close(
        PT, PLAY_776_SECTION, "play", "translatePlayerChat775To776")

    # --- PlayTranslator の Registry Data まわり ---
    pt_text = PT.read_text(encoding="utf-8")
    pt_text = apply_fixes(pt_text, PLAY_REGISTRY_FIXES, "play-registry")
    PT.write_text(pt_text, encoding="utf-8")
    print("[play-registry] OK")

    # --- ProxySession 設定フェーズ 8 ハンドラ ---
    # 既定では飛ばす（ProxySession.java は別担当 A が編集中）。
    # 親の「ProxySession is free」許可が出てから --apply-proxysession で適用する。
    if apply_ps:
        insert_before_marker(
            PS, PROXYSESSION_CONFIG_SECTION_776, "proxysession-config",
            "private Outcome handleServerConfiguration775From776(MinecraftPacket packet)",
            "private Outcome handleServerConfiguration776From774(MinecraftPacket packet)")
    else:
        print("[proxysession-config] スキップ（--apply-proxysession で適用）")

    print("完了。次は :detergentmc-protocol:compileJava で確認すること。")


if __name__ == "__main__":
    main()
