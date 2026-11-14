#!/usr/bin/env python3
"""ChunkTranslator / PlayTranslator の 771 節から 772 節を生成する。

方針: 771 節を抜き出して shift()（gen-772-proxysession.py のもの）で
1段ずらし、FIXES（手書きの置き換え）を当て、ファイル末尾の最終 `}` の
直前に挿入する。PlayTranslator の 772 節は手書きブロックで作る
（Player Info 等は771と772で同一形式のため素通しにする）。

771→772 で shift だけでは足りない点（FIXES で直す）:
- 成分種別は770・771・772とも96件・同じ番号のため KIND 表は恒等表になる。
- 770→771 で形式が変わった成分（unbreakable の bool→void、
  dyed_color の showTooltip 削除、enchantments／attribute_modifiers／
  trim／tool／item_block_predicates の末尾 bool の削除・追加、
  instrument のコンテナ化、jukebox_playable の showInTooltip 削除、
  hide_tooltip→tooltip_display 統合）は770・771・772で同形式のため、
  shift したヘルパーは誤って769形式を読み書きする。手書きで直す。
- チャンクの heightmap・paletted container は770・771・772とも直接配列
  （長さ varint 接頭辞なし）のため、769形式との変換をする
  shift 版は使えない。手書きで直す。
- パーティクルは770・771・772で同一（tinted_leaves／firefly も両方にある）
  ため、771→770 の落としは要らない。メタデータの型番号も同一。
- Explosion の knockback は770・771・772とも f64×3。
- jukebox_playable の曲IDは lava_chicken（772=9）の挿入で+1ずれる。
- Set Creative Slot は770・771・772とも UntrustedSlot。
- Spawn Entity：happy_ghast は771・772とも56のためそのまま通す。
"""
import importlib.util
import re
import sys
from pathlib import Path

_HERE = Path(__file__).resolve().parent
_spec = importlib.util.spec_from_file_location("gen772ps", _HERE / "gen-772-proxysession.py")
_mod = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(_mod)
shift = _mod.shift

ROOT = Path("/home/hatch/workspace/detergentmc")
CT = ROOT / "detergentmc-protocol/src/main/java/io/detergentmc/protocol/ChunkTranslator.java"
PT = ROOT / "detergentmc-protocol/src/main/java/io/detergentmc/protocol/PlayTranslator.java"


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


# （main はファイル末尾に定義する）

# ----------------------------------------------------------------------
# 置き換えヘルパー
# ----------------------------------------------------------------------

def replace_method(text, signature, new_text, label):
    """signature を含むメソッド1個を、直前の javadoc ごと new_text に置き換える。
    signature はメソッド名を含む一意の文字列（例: "translateSlot769To771(Reader"）。"""
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


# 成分種別の対応表（771 kind -> 772 kind。770・771・772とも96件・同じ番号のため恒等）。
KIND_771_TO_772 = {i: i for i in range(96)}
KIND_772_TO_771 = {i: i for i in range(96)}
assert len(KIND_771_TO_772) == 96
assert len(KIND_772_TO_771) == 96


def java_int_array(name, values, per_line=16):
    lines = []
    for i in range(0, len(values), per_line):
        lines.append("            " + ", ".join(str(v) for v in values[i:i + per_line]) + ",")
    body = "\n".join(lines)
    return f"    private static final int[] {name} = {{\n{body}\n    }};\n"


KIND_771_TO_772_JAVA = java_int_array(
    "KIND_771_TO_772", [KIND_771_TO_772[i] for i in range(96)])
KIND_772_TO_771_JAVA = java_int_array(
    "KIND_772_TO_771",
    [KIND_772_TO_771.get(i, -1) for i in range(96)])

# 771節の KIND 表（shift 後の old として使う。値は gen-771 と同じ）。
KIND_769_TO_771 = {
    **{i: i for i in range(0, 15)},
    15: -1, 16: 15,
    17: 16, 18: 17, 19: 18, 20: 19, 21: 20, 22: 21, 23: 22, 24: 23, 25: 24, 26: 25,
    27: 27, 28: 28, 29: 29, 30: 30, 31: 31, 32: 32,
    33: 34, 34: 35, 35: 36, 36: 37, 37: 38, 38: 39, 39: 40, 40: 41, 41: 42,
    42: 44, 43: 45, 44: 46, 45: 47, 46: 48, 47: 49, 48: 50, 49: 51, 50: 52,
    51: 54, 52: 55, 53: 57, 54: 58, 55: 59, 56: 60, 57: 61, 58: 62, 59: 63,
    60: 64, 61: 65, 62: 66, 63: 67, 64: 68, 65: 69, 66: 70,
}
assert len(KIND_769_TO_771) == 67
KIND_771_TO_769 = {v: k for k, v in KIND_769_TO_771.items() if v >= 0}
assert len(KIND_771_TO_769) == 66  # 15（hide_additional_tooltip）が落ちる分

# shift 後の 772 節に残る（誤った）KIND 表のテキスト。置き換えの old に使う。
KIND_771_TO_772_SHIFTED_JAVA = java_int_array(
    "KIND_771_TO_772", [KIND_769_TO_771[i] for i in range(67)])
KIND_772_TO_771_SHIFTED_JAVA = java_int_array(
    "KIND_772_TO_771",
    [KIND_771_TO_769.get(i, -1) for i in range(96)])



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


# 771節のヘッダーコメント（shift 後の old を shift() で作る）。
HEADER_771 = """    // 769 ⇔ 771（1.21.6）
    // 成分種別は769が67件、771が96件。番号が大きくずれるため KIND 表
    // （KIND_769_TO_771／KIND_771_TO_769）で付け替える。
    // 変わるのは中身のレジストリID（アイテム・ブロック状態・
    // エンティティ・音・パーティクル）と成分の形式（tooltip_display への
    // 統合、showTooltip／showInTooltip の削除、tool の
    // canDestroyBlocksInCreative 追加等）。バイオーム・エンチャント・属性・
    // メニュー・ダメージ種別は同一のため付け替えない。エンティティは
    // potion（769=99）が splash_potion（771=99）と lingering_potion
    // （771=100）に分かれる。tinted_leaves（771=35）と firefly
    // （771=113）は769に無いため771→770では落とす。
    // チャンクの heightmap は NBT から直接の配列に変わった。"""

HEADER_772 = """    // 771 ⇔ 772（1.21.7）
    // 成分種別は770・771・772とも96件・同じ番号のため KIND 表は恒等表。
    // 変わるのは中身のレジストリID（アイテム・ブロック状態・
    // エンティティ・音・パーティクル）と成分の中身（jukebox_playable の
    // 曲ID、break_sound／blocks_attacks の音ID）。バイオーム・
    // エンチャント・属性・メニュー・ダメージ種別・パーティクルは同一の
    // ため付け替えない。エンティティは happy_ghast（771・772とも56）。
    // tinted_leaves（35）と firefly（113）は770・771・772ともある。
    // チャンクの heightmap は770・771・772とも直接の配列（長さ varint 無し）。"""

# 771節の KIND 表まわり（shift 後の old を shift() で作る）。
KIND_COUNT_771 = """    /** 成分種別の件数（771 は 96 件。769 は 67 件）。 */
    private static final int DATA_COMPONENT_TYPE_COUNT_771 = 96;"""

KIND_COUNT_772 = """    /** 成分種別の件数（770・771・772とも 96 件）。 */
    private static final int DATA_COMPONENT_TYPE_COUNT_772 = 96;"""

KIND_JAVADOC_1_771 = """    /**
     * 成分種別の対応表（769 kind → 771 kind）。-1 は 771 に無いため落とす
     * （15＝hide_additional_tooltip）。16（hide_tooltip）は 15
     * （tooltip_display）になる。
     */"""

KIND_JAVADOC_1_772 = """    /**
     * 成分種別の対応表（771 kind → 772 kind）。770・771・772とも96件・
     * 同じ番号のため恒等表（落とす種別は無い）。
     */"""

KIND_JAVADOC_2_771 = """    /**
     * 成分種別の対応表（771 kind → 769 kind）。-1 は 769 に無いため落とす。
     * 15（tooltip_display）は内容次第で hide_tooltip（16）か
     * hide_additional_tooltip（15）になるためここでは -1 にしてある。
     */"""

KIND_JAVADOC_2_772 = """    /**
     * 成分種別の対応表（772 kind → 771 kind）。770・771・772とも96件・
     * 同じ番号のため恒等表（落とす種別は無い）。
     */"""


# ----------------------------------------------------------------------
# ChunkTranslator 772 節の文字列 FIXES（シフト済みテキストに適用）
# ----------------------------------------------------------------------

CHUNK_STRING_FIXES = [
    # 見出しの版数：772 は 1.21.7／1.21.8 の両方。
    ("    // 771 ⇔ 772（1.21.7）",
     "    // 771 ⇔ 772（1.21.7／1.21.8）"),
    # ヘッダーコメントの happy_ghast：771・772とも56のため「増える」ではない。
    # （shift 版ヘッダーは KIND 表・パーティクル定数・case 番号とも既に
    # 772版として正しいため、ヘッダー全体の置き換えはしない。）
    ("    // ため付け替えない。エンティティは happy_ghast（772=56）が増える。",
     "    // ため付け替えない。エンティティは happy_ghast（771・772とも56）。"),
    # パーティクル節のヘッダー：771・772でパーティクルは同一（挿入なし）。
    # pale_oak_leaves は770で34に挿入済み、trail は771・772とも48で
    # duration 付き、block_crumble は771・772とも112。
    ("""    // pale_oak_leaves（772=34）の挿入で34番以降が1つずれる。
    // trail（771=46→772=47）は772で末尾に duration varint が増えた。
    // block_crumble（771=113→772=111）はブロック状態IDを持つ。""",
     """    // パーティクルは771・772で同一のため、ID は対応表に通すだけ
    // （実質そのまま）。trail（771・772とも48）／block_crumble
    // （771・772とも112）とも duration・ブロック状態ID付き。"""),
    # Spawn Entity（772→771）：happy_ghast は771・772とも56のため、shift 版の
    # ghast 代用テンプレート（三項演算子）を identity の対応表に直す。
    ("""            int mappedType = type == 56
                    ? 55 // happy_ghast（772にしか無い）は ghast で代用する
                    : RegistryRemapper.mapEntityTypeUp772(type);""",
     """            int mappedType = RegistryRemapper.mapEntityTypeUp772(type);"""),
    # ボート variant のコメント：happy_ghast は771・772とも56のためずれない。
    ("""     * 772の種別を771に直してから771の表を使う（ボート類は
     * happy_ghast 挿入で56番以降が1つずれるが、対応表で吸収する）。""",
     """     * 772の種別を771に直してから771の表を使う（happy_ghast は
     * 771・772とも56のため、種別はそのまま対応表に通す）。"""),
    # ItemCost の注記：誤って読んでいたのは769→770の翻訳器（771で修正済み）。
    ("""     * （770→771の翻訳器は誤って「個数＋スロット列」と読んでいたため、
     * 772では正しい形式に直す。770→771も別途修正する。）""",
     """     * （770→771と同じ正しい形式で読む。769→770の翻訳器は誤って
     * 「個数＋スロット列」と読んでいた。）"""),
    # メタデータ節の注記：ブロック状態・狼バリアント・粒子とも771・772で同一。
    # （狼バリアントは型24で Holder（varint）。バイオームの IDSet は
    # メタデータではなくレジストリ側の話。）
    ("""    // 型 14／15（ブロック状態）は771⇔772で付け替える。
    // 型 23（狼バリアント）の直書きにはバイオームの IDSet があり、
    // 772では pale_garden 挿入でバイオームIDがずれるため付け替える。
    // 型 17／18（粒子）は trail の duration と pale_oak_leaves の扱いが変わる。""",
     """    // 型 14／15（ブロック状態）は771・772で同じ番号のため対応表に
    // 通すだけ（実質そのまま）。型 24（狼バリアント）は Holder（varint）
    // のためそのまま写す。型 17／18（粒子）も771・772で同一。"""),
    # 絵画メタデータ Holder（771→772）の注記：51種・dennis 挿入。
    ("""    /** 絵画バリアントの Holder を771→772向けに変換する。絵画は50種・同じ並びのため DROP にはならない（念のため）。 */""",
     """    /** 絵画バリアントの Holder を771→772向けに変換する。絵画は771が50種・772が51種（dennis を14に挿入）。771の有効なIDは DROP にならない（念のため）。 */"""),
    # 絵画メタデータ Holder（771→772）のログ：DROP は771側の無効ID。
    ("""                    "772に無い絵画のメタデータを落とします（id=" + (holder - 1) + "）。");""",
     """                    "771に無い絵画のメタデータを落とします（id=" + (holder - 1) + "）。");"""),
    # 絵画メタデータ Holder（772→771）の注記：dennis は DROP する。
    ("""    /** 絵画バリアントの Holder を772→771向けに変換する。絵画は50種・同じ並びのため DROP にはならない（念のため）。 */""",
     """    /** 絵画バリアントの Holder を772→771向けに変換する。dennis（772=14）は771に無いため DROP する（メタデータの項目ごと落とす）。 */"""),
    # チャンクデータ節の注記：ブロック状態・バイオームは771・772で不変。
    ("""    // 771⇔772ではブロック状態（樹脂／クリーキングハート）と
    // バイオーム（pale_garden 挿入）の両方がずれるため専用の変換器を使う。""",
     """    // 771⇔772ではブロック状態・バイオームは変わらない（アイテム・音・
    // 絵画・ジュークボックスだけがずれる）。パレットの付け替えは対応表に任せる。"""),
    # translateChunkData771To772 の注記：付け替えは対応表任せ（実質そのまま）。
    ("""    /** チャンクデータを771→772向けに変換する。heightmap は770・771・772とも直接の配列（varint 件数＋（type、長さ、long列））のためそのまま写す。セクションのブロック状態・バイオームとブロックエンティティ種別を付け替える。 */""",
     """    /** チャンクデータを771→772向けに変換する。heightmap は770・771・772とも直接の配列（varint 件数＋（type、長さ、long列））のためそのまま写す。セクションのブロック状態・バイオームとブロックエンティティ種別は771・772で同じ番号のため対応表に通すだけ（実質そのまま）。 */"""),
    # Spawn Entity（771→772）の注記：falling_block は771=49（コード通り）。
    # creaking_transient は769で削除済みのため771・772には無い。
    ("""     * Spawn Entity（771 → 772）。種別を付け替え、falling_block（771=50）の
     * objectData（ブロック状態ID）も付け替える。
     * creaking_transient（771=30）の削除で31番以降の種別が1つずれるため、
     * ボート類（oak_boat 85→84 等）も対応表で付け替わる。""",
     """     * Spawn Entity（771 → 772）。種別を付け替え、falling_block（771=49）の
     * objectData（ブロック状態ID）も付け替える。エンティティは771・772で
     * 同じ並びのため、種別は対応表に通すだけ（実質そのまま）。"""),
]


# 削除するメソッド（shift 版では不要・誤りのため。replace 後に削除する）。
DELETE_METHODS = [
    # 771節には770時代の削除対象（hide_tooltip／trim 769合成／
    # Heightmap NBT変換）は既に無いため、削除は不要。
]


# ----------------------------------------------------------------------
# ChunkTranslator 772 節のメソッド置き換え
# ----------------------------------------------------------------------

CHUNK_METHOD_REPLACEMENTS = {
"translateComponent771To772(Reader reader, Writer writer, int kind771)": """    /**
     * 成分1個を771→772向けに変換する。770・771・772とも96件・同じ番号・
     * 同じ形式のため種別はそのまま。レジストリIDを付け替えるものだけ
     * 変換する。落とす成分は無い（{@code false} を返すことは無いが、
     * 呼び出し側の規約に合わせる）。
     */
    private static boolean translateComponent771To772(Reader reader, Writer writer, int kind771) {
        switch (kind771) {
            case 4 -> { } // unbreakable（void。770・771・772とも中身なし）
            case 10, 34 -> translateEnchantments771To772(reader, writer);
            case 11, 12 -> translateItemBlockPredicates771To772(reader, writer);
            case 13 -> translateAttributeModifiers771To772(reader, writer);
            case 14 -> translateCustomModelData771To772(reader, writer);
            case 15 -> translateTooltipDisplay771To772(reader, writer);
            case 16 -> writer.writeBoolean(reader.readBoolean()); // enchantment_glint_override
            case 20 -> translateFood771To772(reader, writer);
            case 21 -> translateConsumable771To772(reader, writer);
            case 22 -> translateUseRemainder771To772(reader, writer);
            case 25 -> translateTool771To772(reader, writer);
            case 26 -> { // weapon：varint＋float（レジストリ無し）
                writer.writeVarInt(reader.readVarInt());
                writer.writeFloat(reader.readFloat());
            }
            case 28 -> translateEquippable771To772(reader, writer);
            case 29 -> translateRepairable771To772(reader, writer);
            case 32 -> translateDeathProtection771To772(reader, writer);
            case 33 -> translateBlocksAttacks771To772(reader, writer);
            case 35 -> translateDyedColor771To772(reader, writer);
            case 40, 41, 66 -> translateSlotArray771To772(reader, writer);
            case 43 -> writer.writeFloat(reader.readFloat()); // potion_duration_scale
            case 47 -> {
                return translateTrim771To772(reader, writer);
            }
            case 52 -> {
                return translateInstrument771To772(reader, writer);
            }
            case 53 -> copyProvidesTrimMaterial771To772(reader, writer);
            case 55 -> translateJukeboxPlayable771To772(reader, writer);
            case 56 -> writer.writeString(reader.readString()); // provides_banner_patterns
            case 62 -> translateNoteBlockSound771To772(reader, writer);
            case 63 -> translateBannerPatterns771To772(reader, writer);
            case 65 -> translatePotDecorations771To772(reader, writer);
            case 71 -> translateBreakSound771To772(reader, writer);
            case 72, 73, 74, 75, 76, 77, 78, 79, 80, 81, 82, 83, 84, 85,
                    87, 88, 90, 91, 92, 93, 94, 95 -> writer.writeVarInt(reader.readVarInt()); // 各種 variant／collar／color
            case 86 -> { // chicken/variant
                if (reader.readBoolean()) {
                    writer.writeBoolean(true);
                    writer.writeVarInt(reader.readVarInt());
                } else {
                    writer.writeBoolean(false);
                    writer.writeString(reader.readString());
                }
            }
            case 89 -> copyPaintingVariantHolder771To772(reader, writer);
            default -> copyComponentOpaque772(reader, writer, kind771);
        }
        return true;
    }
""",
"translateComponent772To771(Reader reader, Writer writer, int kind772)": """    /**
     * 成分1個を772→771向けに変換する。770・771・772とも96件・同じ番号・
     * 同じ形式のため種別はそのまま。レジストリIDを付け替えるものだけ
     * 変換する。771に無い種別は無いため、落とす成分は無い
     * （{@code false} を返すことは無いが、呼び出し側の規約に合わせる）。
     */
    private static boolean translateComponent772To771(Reader reader, Writer writer, int kind772) {
        switch (kind772) {
            case 4 -> { } // unbreakable（void。770・771・772とも中身なし）
            case 10, 34 -> translateEnchantments772To771(reader, writer);
            case 11, 12 -> translateItemBlockPredicates772To771(reader, writer);
            case 13 -> translateAttributeModifiers772To771(reader, writer);
            case 14 -> translateCustomModelData772To771(reader, writer);
            case 15 -> translateTooltipDisplay772To771(reader, writer);
            case 16 -> writer.writeBoolean(reader.readBoolean()); // enchantment_glint_override
            case 20 -> translateFood772To771(reader, writer);
            case 21 -> translateConsumable772To771(reader, writer);
            case 22 -> translateUseRemainder772To771(reader, writer);
            case 25 -> translateTool772To771(reader, writer);
            case 26 -> { // weapon：varint＋float（レジストリ無し）
                writer.writeVarInt(reader.readVarInt());
                writer.writeFloat(reader.readFloat());
            }
            case 28 -> translateEquippable772To771(reader, writer);
            case 29 -> translateRepairable772To771(reader, writer);
            case 32 -> translateDeathProtection772To771(reader, writer);
            case 33 -> translateBlocksAttacks772To771(reader, writer);
            case 35 -> translateDyedColor772To771(reader, writer);
            case 40, 41, 66 -> translateSlotArray772To771(reader, writer);
            case 43 -> writer.writeFloat(reader.readFloat()); // potion_duration_scale
            case 47 -> {
                return translateTrim772To771(reader, writer);
            }
            case 52 -> {
                return translateInstrument772To771(reader, writer);
            }
            case 53 -> copyProvidesTrimMaterial772To771(reader, writer);
            case 55 -> translateJukeboxPlayable772To771(reader, writer);
            case 56 -> writer.writeString(reader.readString()); // provides_banner_patterns
            case 62 -> translateNoteBlockSound772To771(reader, writer);
            case 63 -> translateBannerPatterns772To771(reader, writer);
            case 65 -> translatePotDecorations772To771(reader, writer);
            case 71 -> translateBreakSound772To771(reader, writer);
            case 72, 73, 74, 75, 76, 77, 78, 79, 80, 81, 82, 83, 84, 85,
                    87, 88, 90, 91, 92, 93, 94, 95 -> writer.writeVarInt(reader.readVarInt()); // 各種 variant／collar／color
            case 86 -> { // chicken/variant
                if (reader.readBoolean()) {
                    writer.writeBoolean(true);
                    writer.writeVarInt(reader.readVarInt());
                } else {
                    writer.writeBoolean(false);
                    writer.writeString(reader.readString());
                }
            }
            case 89 -> copyPaintingVariantHolder772To771(reader, writer);
            default -> copyComponentOpaque772(reader, writer, kind772);
        }
        return true;
    }
""",
"translateSlotComponents772To771(Reader reader, Writer writer)": """    /** スロットの added／removed 成分部分だけを772→771向けに変換する。770・771・772で種別番号は同じため、tooltip_display（15）の特別扱いは要らない。 */
    private static void translateSlotComponents772To771(Reader reader, Writer writer) {
        int added = reader.readVarInt();
        Writer kept = new Writer();
        int keptCount = 0;
        for (int i = 0; i < added; i++) {
            int kind772 = reader.readVarInt();
            int kind771 = mapComponentKind772To771(kind772);
            if (kind771 < 0) {
                skipComponentData772(reader, kind772);
                continue;
            }
            Writer component = new Writer();
            if (!translateComponent772To771(reader, component, kind772)) {
                continue;
            }
            kept.writeVarInt(kind771);
            kept.writeBytes(component.bytes());
            keptCount++;
        }
        writer.writeVarInt(keptCount);
        writer.writeBytes(kept.bytes());
        int removed = reader.readVarInt();
        Writer keptRemoved = new Writer();
        int keptRemovedCount = 0;
        for (int i = 0; i < removed; i++) {
            int kind771 = mapComponentKind772To771(reader.readVarInt());
            if (kind771 < 0) {
                continue;
            }
            keptRemoved.writeVarInt(kind771);
            keptRemovedCount++;
        }
        writer.writeVarInt(keptRemovedCount);
        writer.writeBytes(keptRemoved.bytes());
    }
""",
"translateComponentList772To771(Reader reader, Writer writer)": """    /** 成分の列（個数 VarInt＋成分1個ずつ）を772→771向けに変換する。770・771・772で種別番号は同じため、tooltip_display（15）の特別扱いは要らない。 */
    private static void translateComponentList772To771(Reader reader, Writer writer) {
        int added = reader.readVarInt();
        Writer kept = new Writer();
        int keptCount = 0;
        for (int i = 0; i < added; i++) {
            int kind772 = reader.readVarInt();
            int kind771 = mapComponentKind772To771(kind772);
            if (kind771 < 0) {
                skipComponentData772(reader, kind772);
                continue;
            }
            Writer component = new Writer();
            if (!translateComponent772To771(reader, component, kind772)) {
                continue;
            }
            kept.writeVarInt(kind771);
            kept.writeBytes(component.bytes());
            keptCount++;
        }
        writer.writeVarInt(keptCount);
        writer.writeBytes(kept.bytes());
    }
""",
"translateTooltipDisplay772To771(Reader reader, Writer writer)": """    /** tooltip_display（772→771）。770・771・772とも {hideTooltip bool, hiddenComponents の列} のためそのまま写す。 */
    private static void translateTooltipDisplay772To771(Reader reader, Writer writer) {
        writer.writeBoolean(reader.readBoolean()); // hideTooltip
        int hidden = reader.readVarInt();
        writer.writeVarInt(hidden);
        for (int i = 0; i < hidden; i++) {
            writer.writeVarInt(reader.readVarInt());
        }
    }
""",
"translateUnbreakable771To772(Reader reader, Writer writer)": """    /** unbreakable を771→772向けに変換する。770・771・772とも void のため何もしない。 */
    private static void translateUnbreakable771To772(Reader reader, Writer writer) {
    }
""",
"translateUnbreakable772To771(Reader reader, Writer writer)": """    /** unbreakable を772→771向けに変換する。770・771・772とも void のため何もしない。 */
    private static void translateUnbreakable772To771(Reader reader, Writer writer) {
    }
""",
"translateDyedColor771To772(Reader reader, Writer writer)": """    /** dyed_color を771→772向けに変換する。770・771・772とも色 i32 だけ。 */
    private static void translateDyedColor771To772(Reader reader, Writer writer) {
        writer.writeInt(reader.readInt()); // color
    }
""",
"translateDyedColor772To771(Reader reader, Writer writer)": """    /** dyed_color を772→771向けに変換する。770・771・772とも色 i32 だけ。 */
    private static void translateDyedColor772To771(Reader reader, Writer writer) {
        writer.writeInt(reader.readInt()); // color
    }
""",
"copyPaintingVariantHolder771To772(Reader reader, Writer writer)": """    /** painting/variant（771→772）の Holder を変換する。絵画は771が50種・772が51種（dennis を14に挿入）。14番以降は+1ずれる。 */
    private static void copyPaintingVariantHolder771To772(Reader reader, Writer writer) {
        int holder = reader.readVarInt();
        if (holder == 0) {
            writer.writeVarInt(0);
            writer.writeInt(reader.readInt()); // width
            writer.writeInt(reader.readInt()); // height
            writer.writeString(reader.readString()); // assetId
            copyOptionalAnonymousNbt(reader, writer); // title
            copyOptionalAnonymousNbt(reader, writer); // author
            return;
        }
        int mapped = RegistryRemapper.mapPaintingVariant771To772(holder - 1);
        if (mapped == RegistryRemapper.DROP) {
            // 範囲外の絵画ID。落とす表現が無いため kebab（0）で代用する。
            mapped = 0;
        }
        writer.writeVarInt(mapped + 1);
    }
""",
"copyPaintingVariantHolder772To771(Reader reader, Writer writer)": """    /** painting/variant（772→771）の Holder を変換する。dennis（772=14）は771に無いため kebab（0）で代用する。14番より後は-1する。 */
    private static void copyPaintingVariantHolder772To771(Reader reader, Writer writer) {
        int holder = reader.readVarInt();
        if (holder == 0) {
            writer.writeVarInt(0);
            writer.writeInt(reader.readInt()); // width
            writer.writeInt(reader.readInt()); // height
            writer.writeString(reader.readString()); // assetId
            copyOptionalAnonymousNbt(reader, writer); // title
            copyOptionalAnonymousNbt(reader, writer); // author
            return;
        }
        int mapped = RegistryRemapper.mapPaintingVariant772To771(holder - 1);
        if (mapped == RegistryRemapper.DROP) {
            // dennis は771に無い。落とす表現が無いため kebab（0）で代用する。
            mapped = 0;
        }
        writer.writeVarInt(mapped + 1);
    }
""",

}


# 772節に新しく追加するヘルパー（shift 版には無いもの）。
# 771節を shift すると EXTRA の11件はすべて shift 版に既にある
# （同一内容）ため、ここには何も残さない。絵画2件だけは shift 版が
# そのまま写し（dennis を落とさない）ため、置換表へ移してある。
EXTRA_METHODS_772 = ""

# 置き換えの続き（成分ヘルパー：770→771の形式変換を含まないもの）。
CHUNK_METHOD_REPLACEMENTS.update({
"translateEnchantments771To772(Reader reader, Writer writer)": """    /** enchantments／stored_enchantments を771→772向けに変換する。IDは同一、末尾の showTooltip は770・771・772とも無い。 */
    private static void translateEnchantments771To772(Reader reader, Writer writer) {
        int count = reader.readVarInt();
        Writer kept = new Writer();
        int keptCount = 0;
        for (int i = 0; i < count; i++) {
            int id = reader.readVarInt();
            int level = reader.readVarInt();
            int mapped = RegistryRemapper.mapEnchantment771To772(id);
            if (mapped == RegistryRemapper.DROP) {
                logOnce("enchantment-drop-772",
                        "772に無いエンチャントを落とします（id=" + id + "）。");
                continue;
            }
            kept.writeVarInt(mapped);
            kept.writeVarInt(level);
            keptCount++;
        }
        writer.writeVarInt(keptCount);
        writer.writeBytes(kept.bytes());
    }
""",
"translateEnchantments772To771(Reader reader, Writer writer)": """    /** enchantments／stored_enchantments を772→771向けに変換する。IDは同一、末尾の showTooltip は770・771・772とも無い。 */
    private static void translateEnchantments772To771(Reader reader, Writer writer) {
        int count = reader.readVarInt();
        Writer kept = new Writer();
        int keptCount = 0;
        for (int i = 0; i < count; i++) {
            int id = reader.readVarInt();
            int level = reader.readVarInt();
            int mapped = RegistryRemapper.mapEnchantment772To771(id);
            if (mapped == RegistryRemapper.DROP) {
                logOnce("enchantment-drop-771-from-772",
                        "771に無いエンチャントを落とします（id=" + id + "）。");
                continue;
            }
            kept.writeVarInt(mapped);
            kept.writeVarInt(level);
            keptCount++;
        }
        writer.writeVarInt(keptCount);
        writer.writeBytes(kept.bytes());
    }
""",
"translateAttributeModifiers771To772(Reader reader, Writer writer)": """    /** attribute_modifiers を771→772向けに変換する。770・771・772とも {属性の配列} のみ（末尾の showTooltip は無い）。 */
    private static void translateAttributeModifiers771To772(Reader reader, Writer writer) {
        int count = reader.readVarInt();
        Writer kept = new Writer();
        int keptCount = 0;
        for (int i = 0; i < count; i++) {
            int attrId = reader.readVarInt();
            String name = reader.readString();
            double value = reader.readDouble();
            int operation = reader.readVarInt();
            int slot = reader.readVarInt();
            int mapped = RegistryRemapper.mapAttribute771To772(attrId);
            if (mapped == RegistryRemapper.DROP) {
                logOnce("attribute-modifier-drop-772",
                        "772に無い属性の修飾子を落とします（id=" + attrId + "）。");
                continue;
            }
            kept.writeVarInt(mapped);
            kept.writeString(name);
            kept.writeDouble(value);
            kept.writeVarInt(operation);
            kept.writeVarInt(slot);
            keptCount++;
        }
        writer.writeVarInt(keptCount);
        writer.writeBytes(kept.bytes());
    }
""",
"translateAttributeModifiers772To771(Reader reader, Writer writer)": """    /** attribute_modifiers を772→771向けに変換する。770・771・772とも {属性の配列} のみ。 */
    private static void translateAttributeModifiers772To771(Reader reader, Writer writer) {
        int count = reader.readVarInt();
        Writer kept = new Writer();
        int keptCount = 0;
        for (int i = 0; i < count; i++) {
            int attrId = reader.readVarInt();
            String name = reader.readString();
            double value = reader.readDouble();
            int operation = reader.readVarInt();
            int slot = reader.readVarInt();
            int mapped = RegistryRemapper.mapAttribute772To771(attrId);
            if (mapped == RegistryRemapper.DROP) {
                logOnce("attribute-modifier-drop-771-from-772",
                        "771に無い属性の修飾子を落とします（id=" + attrId + "）。");
                continue;
            }
            kept.writeVarInt(mapped);
            kept.writeString(name);
            kept.writeDouble(value);
            kept.writeVarInt(operation);
            kept.writeVarInt(slot);
            keptCount++;
        }
        writer.writeVarInt(keptCount);
        writer.writeBytes(kept.bytes());
    }
""",
"translateTrim771To772(Reader reader, Writer writer)": """    /** trim を771→772向けに変換する。材料・型の Holder。770・771・772とも showInTooltip は無い。 */
    private static boolean translateTrim771To772(Reader reader, Writer writer) {
        if (!translateTrimMaterial771To772(reader, writer)) {
            return false;
        }
        return translateTrimPattern771To772(reader, writer);
    }
""",
"translateTrim772To771(Reader reader, Writer writer)": """    /** trim を772→771向けに変換する。材料・型の Holder。770・771・772とも showInTooltip は無い。 */
    private static boolean translateTrim772To771(Reader reader, Writer writer) {
        if (!translateTrimMaterial772To771(reader, writer)) {
            return false;
        }
        return translateTrimPattern772To771(reader, writer);
    }
""",
"translateTrimMaterial771To772(Reader reader, Writer writer)": """    /** trim の材料の Holder を771→772向けに変換する。770・771・772とも直書きは {assetBase, overrides, description}（ingredientId は無い）。材料IDは11種・同じ並びのためそのまま。 */
    private static boolean translateTrimMaterial771To772(Reader reader, Writer writer) {
        int holder = reader.readVarInt();
        writer.writeVarInt(holder);
        if (holder != 0) {
            return true;
        }
        writer.writeString(reader.readString()); // assetBase
        int count = reader.readVarInt();
        writer.writeVarInt(count);
        for (int i = 0; i < count; i++) {
            writer.writeString(reader.readString());
            writer.writeString(reader.readString());
        }
        copyOptionalAnonymousNbt(reader, writer); // description
        return true;
    }
""",
"translateTrimMaterial772To771(Reader reader, Writer writer)": """    /** trim の材料の Holder を772→771向けに変換する。直書きは {assetBase, overrides, description}。 */
    private static boolean translateTrimMaterial772To771(Reader reader, Writer writer) {
        int holder = reader.readVarInt();
        writer.writeVarInt(holder);
        if (holder != 0) {
            return true;
        }
        writer.writeString(reader.readString()); // assetBase
        int count = reader.readVarInt();
        writer.writeVarInt(count);
        for (int i = 0; i < count; i++) {
            writer.writeString(reader.readString());
            writer.writeString(reader.readString());
        }
        copyOptionalAnonymousNbt(reader, writer); // description
        return true;
    }
""",
"translateTrimPattern771To772(Reader reader, Writer writer)": """    /** trim の型の Holder を771→772向けに変換する。770・771・772とも直書きは {assetId, description, decal}（templateItemId は無い）。 */
    private static boolean translateTrimPattern771To772(Reader reader, Writer writer) {
        int holder = reader.readVarInt();
        if (holder != 0) {
            int mapped = RegistryRemapper.mapTrimPattern771To772(holder - 1);
            if (mapped == RegistryRemapper.DROP) {
                logOnce("trim-pattern-drop-772",
                        "772に無い装飾パターンの trim を落とします（id=" + (holder - 1) + "）。");
                return false;
            }
            writer.writeVarInt(mapped + 1);
            return true;
        }
        writer.writeVarInt(0);
        writer.writeString(reader.readString()); // assetId
        copyOptionalAnonymousNbt(reader, writer);
        writer.writeBoolean(reader.readBoolean()); // decal
        return true;
    }
""",
"translateTrimPattern772To771(Reader reader, Writer writer)": """    /** trim の型の Holder を772→771向けに変換する。直書きは {assetId, description, decal}。 */
    private static boolean translateTrimPattern772To771(Reader reader, Writer writer) {
        int holder = reader.readVarInt();
        if (holder != 0) {
            int mapped = RegistryRemapper.mapTrimPattern772To771(holder - 1);
            if (mapped == RegistryRemapper.DROP) {
                logOnce("trim-pattern-drop-771-from-772",
                        "771に無い装飾パターンの trim を落とします（id=" + (holder - 1) + "）。");
                return false;
            }
            writer.writeVarInt(mapped + 1);
            return true;
        }
        writer.writeVarInt(0);
        writer.writeString(reader.readString()); // assetId
        copyOptionalAnonymousNbt(reader, writer);
        writer.writeBoolean(reader.readBoolean()); // decal
        return true;
    }
""",
})

CHUNK_METHOD_REPLACEMENTS.update({
"translateInstrument771To772(Reader reader, Writer writer)": """    /**
     * instrument コンポーネントを771→772向けに変換する。770・771・772とも
     * {hasHolder bool, data：true なら Holder／false なら楽器名} の
     * コンテナ。楽器IDは770・771・772とも8件・同じ並びのためそのまま。
     */
    private static boolean translateInstrument771To772(Reader reader, Writer writer) {
        boolean hasHolder = reader.readBoolean();
        writer.writeBoolean(hasHolder);
        if (!hasHolder) {
            writer.writeString(reader.readString()); // 楽器名
            return true;
        }
        int holder = reader.readVarInt();
        writer.writeVarInt(holder);
        if (holder != 0) {
            return true;
        }
        translateSoundHolder771To772(reader, writer);
        writer.writeFloat(reader.readFloat()); // useDuration
        writer.writeFloat(reader.readFloat()); // range
        copyOptionalAnonymousNbt(reader, writer);
        return true;
    }
""",
"translateInstrument772To771(Reader reader, Writer writer)": """    /**
     * instrument コンポーネントを772→771向けに変換する。770・771・772とも
     * {hasHolder bool, data} のコンテナ。楽器IDは8件・同じ並びのためそのまま。
     */
    private static boolean translateInstrument772To771(Reader reader, Writer writer) {
        boolean hasHolder = reader.readBoolean();
        writer.writeBoolean(hasHolder);
        if (!hasHolder) {
            writer.writeString(reader.readString()); // 楽器名
            return true;
        }
        int holder = reader.readVarInt();
        writer.writeVarInt(holder);
        if (holder != 0) {
            return true;
        }
        translateSoundHolder772To771(reader, writer);
        writer.writeFloat(reader.readFloat()); // useDuration
        writer.writeFloat(reader.readFloat()); // range
        copyOptionalAnonymousNbt(reader, writer);
        return true;
    }
""",
"translateJukeboxPlayable771To772(Reader reader, Writer writer)": """    /** jukebox_playable を771→772向けに変換する。曲IDは lava_chicken（772=9）の挿入で+1ずれる。直書きの音の Holder も付け替える。末尾の showInTooltip は770・771・772ともある。 */
    private static void translateJukeboxPlayable771To772(Reader reader, Writer writer) {
        boolean hasHolder = reader.readBoolean();
        writer.writeBoolean(hasHolder);
        if (hasHolder) {
            int holder = reader.readVarInt();
            if (holder == 0) {
                writer.writeVarInt(0);
                translateSoundHolder771To772(reader, writer);
                writer.writeAnonymousNbt(reader.readAnonymousNbt().tag()); // description
                writer.writeFloat(reader.readFloat()); // lengthInSeconds
                writer.writeVarInt(reader.readVarInt()); // comparatorOutput
            } else {
                int mapped = RegistryRemapper.mapJukeboxSong771To772(holder - 1);
                if (mapped == RegistryRemapper.DROP) {
                    // 範囲外の曲ID。曲を落とす表現が無いため "11"（0）で代用する。
                    mapped = 0;
                }
                writer.writeVarInt(mapped + 1);
            }
        } else {
            writer.writeString(reader.readString()); // song name
        }
        writer.writeBoolean(reader.readBoolean()); // showInTooltip
    }
""",
"translateJukeboxPlayable772To771(Reader reader, Writer writer)": """    /** jukebox_playable を772→771向けに変換する。曲IDは lava_chicken（772=9）を "11" で代用し、10番以降は-1する。末尾の showInTooltip は770・771・772ともある。 */
    private static void translateJukeboxPlayable772To771(Reader reader, Writer writer) {
        boolean hasHolder = reader.readBoolean();
        writer.writeBoolean(hasHolder);
        if (hasHolder) {
            int holder = reader.readVarInt();
            if (holder == 0) {
                writer.writeVarInt(0);
                translateSoundHolder772To771(reader, writer);
                writer.writeAnonymousNbt(reader.readAnonymousNbt().tag()); // description
                writer.writeFloat(reader.readFloat()); // lengthInSeconds
                writer.writeVarInt(reader.readVarInt()); // comparatorOutput
            } else {
                int mapped = RegistryRemapper.mapJukeboxSong772To771(holder - 1);
                if (mapped == RegistryRemapper.DROP) {
                    // lava_chicken は771に無い。曲を落とす表現が無いため "11"（0）で代用する。
                    mapped = 0;
                }
                writer.writeVarInt(mapped + 1);
            }
        } else {
            writer.writeString(reader.readString()); // song name
        }
        writer.writeBoolean(reader.readBoolean()); // showInTooltip
    }
""",
"translateTool771To772(Reader reader, Writer writer)": """    /** tool を771→772向けに変換する。ルールの中のブロックIDだけ付け替え、末尾の canDestroyBlocksInCreative は770・771・772ともあるため写す。 */
    private static void translateTool771To772(Reader reader, Writer writer) {
        int ruleCount = reader.readVarInt();
        writer.writeVarInt(ruleCount);
        for (int i = 0; i < ruleCount; i++) {
            translateIdSetBlocks771To772(reader, writer);
            if (reader.readBoolean()) {
                writer.writeBoolean(true);
                writer.writeFloat(reader.readFloat());
            } else {
                writer.writeBoolean(false);
            }
            if (reader.readBoolean()) {
                writer.writeBoolean(true);
                writer.writeBoolean(reader.readBoolean());
            } else {
                writer.writeBoolean(false);
            }
        }
        writer.writeFloat(reader.readFloat()); // defaultMiningSpeed
        writer.writeVarInt(reader.readVarInt()); // damagePerBlock
        writer.writeBoolean(reader.readBoolean()); // canDestroyBlocksInCreative
    }
""",
"translateTool772To771(Reader reader, Writer writer)": """    /** tool を772→771向けに変換する。ルールの中のブロックIDだけ付け替え、末尾の canDestroyBlocksInCreative は写す。 */
    private static void translateTool772To771(Reader reader, Writer writer) {
        int ruleCount = reader.readVarInt();
        writer.writeVarInt(ruleCount);
        for (int i = 0; i < ruleCount; i++) {
            translateIdSetBlocks772To771(reader, writer);
            if (reader.readBoolean()) {
                writer.writeBoolean(true);
                writer.writeFloat(reader.readFloat());
            } else {
                writer.writeBoolean(false);
            }
            if (reader.readBoolean()) {
                writer.writeBoolean(true);
                writer.writeBoolean(reader.readBoolean());
            } else {
                writer.writeBoolean(false);
            }
        }
        writer.writeFloat(reader.readFloat()); // defaultMiningSpeed
        writer.writeVarInt(reader.readVarInt()); // damagePerBlock
        writer.writeBoolean(reader.readBoolean()); // canDestroyBlocksInCreative
    }
""",
"translateItemBlockPredicates771To772(Reader reader, Writer writer)": """    /** can_place_on／can_break を771→772向けに変換する。述語の中のブロックIDだけ付け替える。末尾の showTooltip は770・771・772とも無い。 */
    private static void translateItemBlockPredicates771To772(Reader reader, Writer writer) {
        int count = reader.readVarInt();
        writer.writeVarInt(count);
        for (int i = 0; i < count; i++) {
            translateItemBlockPredicate771To772(reader, writer);
        }
    }
""",
"translateItemBlockPredicates772To771(Reader reader, Writer writer)": """    /** can_place_on／can_break を772→771向けに変換する。述語の中のブロックIDだけ付け替える。 */
    private static void translateItemBlockPredicates772To771(Reader reader, Writer writer) {
        int count = reader.readVarInt();
        writer.writeVarInt(count);
        for (int i = 0; i < count; i++) {
            translateItemBlockPredicate772To771(reader, writer);
        }
    }
""",
"translateItemBlockPredicate771To772(Reader reader, Writer writer)": """    /** ItemBlockPredicate 1個を771→772向けに変換する。述語の中のブロックIDだけ付け替え、末尾の DataComponentMatchers も変換する（770・771・772ともある）。 */
    private static void translateItemBlockPredicate771To772(Reader reader, Writer writer) {
        if (reader.readBoolean()) {
            writer.writeBoolean(true);
            translateIdSetBlocks771To772(reader, writer);
        } else {
            writer.writeBoolean(false);
        }
        if (reader.readBoolean()) {
            writer.writeBoolean(true);
            int count = reader.readVarInt();
            writer.writeVarInt(count);
            for (int i = 0; i < count; i++) {
                writer.writeString(reader.readString()); // name
                boolean exact = reader.readBoolean();
                writer.writeBoolean(exact);
                if (exact) {
                    writer.writeString(reader.readString());
                } else {
                    writer.writeString(reader.readString());
                    writer.writeString(reader.readString());
                }
            }
        } else {
            writer.writeBoolean(false);
        }
        writer.writeAnonymousNbt(reader.readAnonymousNbt().tag()); // nbt
        translateDataComponentMatchers771To772(reader, writer);
    }
""",
"translateItemBlockPredicate772To771(Reader reader, Writer writer)": """    /** ItemBlockPredicate 1個を772→771向けに変換する。述語の中のブロックIDだけ付け替え、末尾の DataComponentMatchers も変換する。 */
    private static void translateItemBlockPredicate772To771(Reader reader, Writer writer) {
        if (reader.readBoolean()) {
            writer.writeBoolean(true);
            translateIdSetBlocks772To771(reader, writer);
        } else {
            writer.writeBoolean(false);
        }
        if (reader.readBoolean()) {
            writer.writeBoolean(true);
            int count = reader.readVarInt();
            writer.writeVarInt(count);
            for (int i = 0; i < count; i++) {
                writer.writeString(reader.readString()); // name
                boolean exact = reader.readBoolean();
                writer.writeBoolean(exact);
                if (exact) {
                    writer.writeString(reader.readString());
                } else {
                    writer.writeString(reader.readString());
                    writer.writeString(reader.readString());
                }
            }
        } else {
            writer.writeBoolean(false);
        }
        writer.writeAnonymousNbt(reader.readAnonymousNbt().tag()); // nbt
        translateDataComponentMatchers772To771(reader, writer);
    }
""",
"translateParticle772To771(Reader reader, Writer writer)": """    /**
     * パーティクル1個（ID VarInt＋データ）を772→771向けに変換する。
     * パーティクルは770・771・772とも同一（tinted_leaves・firefly も両方に
     * ある）ため常に変換できる（{@code true} を返す）。
     */
    private static boolean translateParticle772To771(Reader reader, Writer writer) {
        int particleId = reader.readVarInt();
        writer.writeVarInt(RegistryRemapper.mapParticleUp772(particleId));
        translateParticleData772To771(reader, writer, particleId);
        return true;
    }
""",
"translateWorldParticles772To771(byte[] payload)": """    /**
     * World Particles（772 → 771）。alwaysShow は770・771・772ともにあるため
     * そのまま通す。粒子は770・771・772で同一のため落とすことは無い。
     */
    public static byte[] translateWorldParticles772To771(byte[] payload) {
        try {
            Reader reader = new Reader(payload);
            boolean longDistance = reader.readBoolean();
            boolean alwaysShow = reader.readBoolean(); // alwaysShow（770・771・772ともにある）
            double x = reader.readDouble();
            double y = reader.readDouble();
            double z = reader.readDouble();
            float offsetX = reader.readFloat();
            float offsetY = reader.readFloat();
            float offsetZ = reader.readFloat();
            float speed = reader.readFloat();
            int count = reader.readInt();
            Writer particleWriter = new Writer();
            translateParticle772To771(reader, particleWriter);
            requireEnd(reader);
            Writer writer = new Writer();
            writer.writeBoolean(longDistance);
            writer.writeBoolean(alwaysShow); // alwaysShow（771にもある。そのまま通す）
            writer.writeDouble(x);
            writer.writeDouble(y);
            writer.writeDouble(z);
            writer.writeFloat(offsetX);
            writer.writeFloat(offsetY);
            writer.writeFloat(offsetZ);
            writer.writeFloat(speed);
            writer.writeInt(count);
            writer.writeBytes(particleWriter.bytes());
            return writer.bytes();
        } catch (IllegalArgumentException e) {
            LOG.warning("World Particles を変換できなかったため素通しします: " + e.getMessage());
            return payload;
        }
    }
""",
})

CHUNK_METHOD_REPLACEMENTS.update({
"translateMetadataValue771To772(Reader reader, Writer writer, int type)": """    /**
     * メタデータの値1個を771→772向けに変換する。落とすエントリは {@code false} を返す。
     * 型番号は771側の番号で dispatch する（770・771・772で型番号は同一）。
     * 23（ウシ）・24（狼）・25（狼の音）・26（カエル）・27（ブタ）・28（ニワトリ）は
     * 770・771・772とも varint。30（絵画）は Holder。
     */
    private static boolean translateMetadataValue771To772(Reader reader, Writer writer, int type) {
        switch (type) {
            case 0 -> writer.writeByte(reader.readByte());
            case 1 -> writer.writeVarInt(reader.readVarInt());
            case 2 -> writer.writeVarLong(reader.readVarLong());
            case 3 -> writer.writeFloat(reader.readFloat());
            case 4 -> writer.writeString(reader.readString());
            case 5 -> {
                Tag tag = reader.readAnonymousNbt().tag();
                if (tag == null) {
                    throw new IllegalArgumentException("メタデータのテキストが空です。");
                }
                writer.writeAnonymousNbt(tag);
            }
            case 6 -> {
                boolean present = reader.readBoolean();
                writer.writeBoolean(present);
                if (present) {
                    Tag tag = reader.readAnonymousNbt().tag();
                    if (tag == null) {
                        throw new IllegalArgumentException("メタデータのテキストが空です。");
                    }
                    writer.writeAnonymousNbt(tag);
                }
            }
            case 7 -> translateSlot771To772(reader, writer);
            case 8 -> writer.writeBoolean(reader.readBoolean());
            case 9 -> {
                writer.writeFloat(reader.readFloat());
                writer.writeFloat(reader.readFloat());
                writer.writeFloat(reader.readFloat());
            }
            case 10 -> writer.writePositionRaw(reader.readPositionRaw());
            case 11 -> {
                boolean present = reader.readBoolean();
                writer.writeBoolean(present);
                if (present) {
                    writer.writePositionRaw(reader.readPositionRaw());
                }
            }
            case 12 -> writer.writeVarInt(reader.readVarInt());
            case 13 -> {
                boolean present = reader.readBoolean();
                writer.writeBoolean(present);
                if (present) {
                    writer.writeBytes(reader.readBytes(16));
                }
            }
            case 14, 15 -> writer.writeVarInt(RegistryRemapper.mapBlockState771To772(reader.readVarInt()));
            case 16 -> writeOptionalAnonymousNbt(writer, reader.readAnonymousNbt().tag());
            case 17 -> translateParticle771To772(reader, writer);
            case 18 -> {
                int count = reader.readVarInt();
                writer.writeVarInt(count);
                for (int i = 0; i < count; i++) {
                    translateParticle771To772(reader, writer);
                }
            }
            case 19 -> {
                writer.writeVarInt(reader.readVarInt());
                writer.writeVarInt(reader.readVarInt());
                writer.writeVarInt(reader.readVarInt());
            }
            case 20, 21, 22 -> writer.writeVarInt(reader.readVarInt());
            case 23, 25, 26, 27, 28 -> writer.writeVarInt(reader.readVarInt()); // cow／wolf_sound／frog／pig／chicken
            case 24 -> translateWolfVariantHolder771To772(reader, writer);
            case 29 -> {
                boolean present = reader.readBoolean();
                writer.writeBoolean(present);
                if (present) {
                    writer.writeString(reader.readString());
                    writer.writePositionRaw(reader.readPositionRaw());
                }
            }
            case 30 -> {
                return translatePaintingVariantHolder771To772(reader, writer);
            }
            case 31, 32 -> writer.writeVarInt(reader.readVarInt()); // sniffer／armadillo
            case 33 -> {
                writer.writeFloat(reader.readFloat());
                writer.writeFloat(reader.readFloat());
                writer.writeFloat(reader.readFloat());
            }
            case 34 -> {
                writer.writeFloat(reader.readFloat());
                writer.writeFloat(reader.readFloat());
                writer.writeFloat(reader.readFloat());
                writer.writeFloat(reader.readFloat());
            }
            default -> throw new IllegalArgumentException("未知のメタデータ型です: " + type);
        }
        return true;
    }
""",
"translateMetadataValue772To771(Reader reader, Writer writer, int type)": """    /**
     * メタデータの値1個を772→771向けに変換する。落とすエントリは {@code false} を返す。
     * 型番号は772側の番号で dispatch する（770・771・772で型番号は同一）。
     * 771に無い型は無いため落とすことは無い。
     */
    private static boolean translateMetadataValue772To771(Reader reader, Writer writer, int type) {
        switch (type) {
            case 0 -> writer.writeByte(reader.readByte());
            case 1 -> writer.writeVarInt(reader.readVarInt());
            case 2 -> writer.writeVarLong(reader.readVarLong());
            case 3 -> writer.writeFloat(reader.readFloat());
            case 4 -> writer.writeString(reader.readString());
            case 5 -> {
                Tag tag = reader.readAnonymousNbt().tag();
                if (tag == null) {
                    throw new IllegalArgumentException("メタデータのテキストが空です。");
                }
                writer.writeAnonymousNbt(tag);
            }
            case 6 -> {
                boolean present = reader.readBoolean();
                writer.writeBoolean(present);
                if (present) {
                    Tag tag = reader.readAnonymousNbt().tag();
                    if (tag == null) {
                        throw new IllegalArgumentException("メタデータのテキストが空です。");
                    }
                    writer.writeAnonymousNbt(tag);
                }
            }
            case 7 -> translateSlot772To771(reader, writer);
            case 8 -> writer.writeBoolean(reader.readBoolean());
            case 9 -> {
                writer.writeFloat(reader.readFloat());
                writer.writeFloat(reader.readFloat());
                writer.writeFloat(reader.readFloat());
            }
            case 10 -> writer.writePositionRaw(reader.readPositionRaw());
            case 11 -> {
                boolean present = reader.readBoolean();
                writer.writeBoolean(present);
                if (present) {
                    writer.writePositionRaw(reader.readPositionRaw());
                }
            }
            case 12 -> writer.writeVarInt(reader.readVarInt());
            case 13 -> {
                boolean present = reader.readBoolean();
                writer.writeBoolean(present);
                if (present) {
                    writer.writeBytes(reader.readBytes(16));
                }
            }
            case 14, 15 -> writer.writeVarInt(RegistryRemapper.mapBlockStateUp772(reader.readVarInt()));
            case 16 -> writeOptionalAnonymousNbt(writer, reader.readAnonymousNbt().tag());
            case 17 -> {
                return translateParticle772To771(reader, writer);
            }
            case 18 -> {
                int count = reader.readVarInt();
                writer.writeVarInt(count);
                for (int i = 0; i < count; i++) {
                    translateParticle772To771(reader, writer);
                }
            }
            case 19 -> {
                writer.writeVarInt(reader.readVarInt());
                writer.writeVarInt(reader.readVarInt());
                writer.writeVarInt(reader.readVarInt());
            }
            case 20, 21, 22 -> writer.writeVarInt(reader.readVarInt());
            case 23, 25, 26, 27, 28 -> writer.writeVarInt(reader.readVarInt()); // cow／wolf_sound／frog／pig／chicken
            case 24 -> translateWolfVariantHolder772To771(reader, writer);
            case 29 -> {
                boolean present = reader.readBoolean();
                writer.writeBoolean(present);
                if (present) {
                    writer.writeString(reader.readString());
                    writer.writePositionRaw(reader.readPositionRaw());
                }
            }
            case 30 -> {
                return translatePaintingVariantHolder772To771(reader, writer);
            }
            case 31, 32 -> writer.writeVarInt(reader.readVarInt()); // sniffer／armadillo
            case 33 -> {
                writer.writeFloat(reader.readFloat());
                writer.writeFloat(reader.readFloat());
                writer.writeFloat(reader.readFloat());
            }
            case 34 -> {
                writer.writeFloat(reader.readFloat());
                writer.writeFloat(reader.readFloat());
                writer.writeFloat(reader.readFloat());
                writer.writeFloat(reader.readFloat());
            }
            default -> throw new IllegalArgumentException("未知のメタデータ型です: " + type);
        }
        return true;
    }
""",
"translateWolfVariantHolder771To772(Reader reader, Writer writer)": """    /** 狼バリアントの Holder を771→772向けに変換する。770・771・772ともただの varint（9種・同じ並び）のためそのまま写す。 */
    private static boolean translateWolfVariantHolder771To772(Reader reader, Writer writer) {
        writer.writeVarInt(reader.readVarInt());
        return true;
    }
""",
"translateWolfVariantHolder772To771(Reader reader, Writer writer)": """    /** 狼バリアントの Holder を772→771向けに変換する。770・771・772ともただの varint のためそのまま写す。 */
    private static boolean translateWolfVariantHolder772To771(Reader reader, Writer writer) {
        writer.writeVarInt(reader.readVarInt());
        return true;
    }
""",
"mapMetadataType771To772(int type771)": """    /** メタデータの型番号を771→772に付け替える。770・771・772で型番号は同一。 */
    private static int mapMetadataType771To772(int type771) {
        return type771;
    }
""",
"mapMetadataType772To771(int type772)": """    /** メタデータの型番号を772→771に付け替える。770・771・772で型番号は同一。 */
    private static int mapMetadataType772To771(int type772) {
        return type772;
    }
""",
"translateChunkData771To772(byte[] payload)": """    /** チャンクデータを771→772向けに変換する。heightmap は770・771・772とも直接の配列（varint 件数＋（type、長さ、long列））のためそのまま写す。セクションのブロック状態・バイオームとブロックエンティティ種別を付け替える。 */
    public static byte[] translateChunkData771To772(byte[] payload) {
        try {
            Reader reader = new Reader(payload);
            int chunkX = reader.readInt();
            int chunkZ = reader.readInt();
            int heightmapCount = reader.readVarInt();
            Writer heightmaps = new Writer();
            heightmaps.writeVarInt(heightmapCount);
            for (int i = 0; i < heightmapCount; i++) {
                int type = reader.readVarInt();
                int length = reader.readVarInt();
                heightmaps.writeVarInt(type);
                heightmaps.writeVarInt(length);
                for (int j = 0; j < length; j++) {
                    heightmaps.writeLong(reader.readLong());
                }
            }
            int dataLength = reader.readVarInt();
            byte[] chunkData = reader.readBytes(dataLength);
            byte[] translatedSections = translateChunkSections771To772(chunkData);
            int blockEntityCount = reader.readVarInt();
            List<BlockEntityEntry> kept = new ArrayList<>();
            for (int i = 0; i < blockEntityCount; i++) {
                int packedXZ = reader.readUnsignedByte();
                int y = reader.readShort();
                int type = reader.readVarInt();
                Tag nbt = readOptionalAnonymousNbt(reader);
                int mapped = RegistryRemapper.mapBlockEntityType771To772(type);
                if (mapped == RegistryRemapper.DROP) {
                    LOG.warning("772に無いブロックエンティティ種別（771 type=" + type
                            + "）のエントリをチャンクから落とします。");
                    continue;
                }
                kept.add(new BlockEntityEntry(packedXZ, y, mapped, nbt));
            }
            byte[] lightData = reader.rest();
            Writer writer = new Writer();
            writer.writeInt(chunkX);
            writer.writeInt(chunkZ);
            writer.writeBytes(heightmaps.bytes());
            writer.writeByteArray(translatedSections);
            writer.writeVarInt(kept.size());
            for (BlockEntityEntry entry : kept) {
                writer.writeUnsignedByte(entry.packedXZ());
                writer.writeShort(entry.y());
                writer.writeVarInt(entry.type());
                writeOptionalAnonymousNbt(writer, entry.nbt());
            }
            writer.writeBytes(lightData);
            return writer.bytes();
        } catch (IllegalArgumentException e) {
            LOG.warning("Chunk Data を変換できなかったため素通しします: " + e.getMessage());
            return payload;
        }
    }
""",
"translateChunkData772To771(byte[] payload)": """    /** チャンクデータを772→771向けに変換する。heightmap は770・771・772とも直接の配列のためそのまま写す。 */
    public static byte[] translateChunkData772To771(byte[] payload) {
        try {
            Reader reader = new Reader(payload);
            int chunkX = reader.readInt();
            int chunkZ = reader.readInt();
            int heightmapCount = reader.readVarInt();
            Writer heightmaps = new Writer();
            heightmaps.writeVarInt(heightmapCount);
            for (int i = 0; i < heightmapCount; i++) {
                int type = reader.readVarInt();
                int length = reader.readVarInt();
                heightmaps.writeVarInt(type);
                heightmaps.writeVarInt(length);
                for (int j = 0; j < length; j++) {
                    heightmaps.writeLong(reader.readLong());
                }
            }
            int dataLength = reader.readVarInt();
            byte[] chunkData = reader.readBytes(dataLength);
            byte[] translatedSections = translateChunkSections772To771(chunkData);
            int blockEntityCount = reader.readVarInt();
            List<BlockEntityEntry> kept = new ArrayList<>();
            for (int i = 0; i < blockEntityCount; i++) {
                int packedXZ = reader.readUnsignedByte();
                int y = reader.readShort();
                int type = reader.readVarInt();
                Tag nbt = readOptionalAnonymousNbt(reader);
                int mapped = RegistryRemapper.mapBlockEntityTypeUp772(type);
                if (mapped == RegistryRemapper.DROP) {
                    LOG.warning("771に無いブロックエンティティ種別（772 type=" + type
                            + "）のエントリをチャンクから落とします。");
                    continue;
                }
                kept.add(new BlockEntityEntry(packedXZ, y, mapped, nbt));
            }
            byte[] lightData = reader.rest();
            Writer writer = new Writer();
            writer.writeInt(chunkX);
            writer.writeInt(chunkZ);
            writer.writeBytes(heightmaps.bytes());
            writer.writeByteArray(translatedSections);
            writer.writeVarInt(kept.size());
            for (BlockEntityEntry entry : kept) {
                writer.writeUnsignedByte(entry.packedXZ());
                writer.writeShort(entry.y());
                writer.writeVarInt(entry.type());
                writeOptionalAnonymousNbt(writer, entry.nbt());
            }
            writer.writeBytes(lightData);
            return writer.bytes();
        } catch (IllegalArgumentException e) {
            LOG.warning("Chunk Data を変換できなかったため素通しします: " + e.getMessage());
            return payload;
        }
    }
""",
"translatePalettedContainer771To772(Reader reader, Writer writer, boolean blocks)": """        /**
         * パレット付きコンテナ1個を771→772向けに変換する。ブロック状態と
         * バイオームの両方を付け替える。770・771・772とも Data Array に
         * 長さ varint 接頭辞は無い。
         */
        private static void translatePalettedContainer771To772(Reader reader, Writer writer, boolean blocks) {
        int bitsPerEntry = reader.readUnsignedByte();
        if (bitsPerEntry > 32) {
            throw new IllegalArgumentException("Bits Per Entry が大きすぎます: " + bitsPerEntry);
        }
        writer.writeUnsignedByte(bitsPerEntry);
        int entryCount = blocks ? 4096 : 64;
        if (bitsPerEntry == 0) {
            int value = reader.readVarInt();
            writer.writeVarInt(blocks
                    ? RegistryRemapper.mapBlockState771To772(value)
                    : mapBiome771To772(value));
            // 770・771・772とも長さ接頭辞は無い。
            return;
        }
        boolean indirect = blocks ? bitsPerEntry <= 8 : bitsPerEntry <= 3;
        if (indirect) {
            int paletteLength = reader.readVarInt();
            if (paletteLength <= 0 || paletteLength > (1 << bitsPerEntry)) {
                throw new IllegalArgumentException("パレット長が不正です: " + paletteLength);
            }
            writer.writeVarInt(paletteLength);
            for (int i = 0; i < paletteLength; i++) {
                int value = reader.readVarInt();
                writer.writeVarInt(blocks
                        ? RegistryRemapper.mapBlockState771To772(value)
                        : mapBiome771To772(value));
            }
            // 間接パレットの Data Array はインデックス列なので中身は変えない。
            // 770・771・772とも長さ接頭辞は無い。長さは bpe から計算する。
            int perLong = 64 / bitsPerEntry;
            int expected = (entryCount + perLong - 1) / perLong;
            for (int i = 0; i < expected; i++) {
                writer.writeLong(reader.readLong());
            }
            return;
        }
        // 直接パレット：Data Array の中身がレジストリ番号そのもの。
        // 770・771・772とも長さ接頭辞は無い。長さは bpe から計算する。
        int perLong = 64 / bitsPerEntry;
        int expected = (entryCount + perLong - 1) / perLong;
        long[] longs = new long[expected];
        for (int i = 0; i < expected; i++) {
            longs[i] = reader.readLong();
        }
        remapDirectEntries771To772(longs, bitsPerEntry, entryCount, blocks);
        for (long value : longs) {
            writer.writeLong(value);
        }
    }
""",
"translatePalettedContainer772To771(Reader reader, Writer writer, boolean blocks)": """        /** パレット付きコンテナ1個を772→771向けに変換する。ブロック状態とバイオームの両方を付け替える。770・771・772とも Data Array に長さ varint 接頭辞は無い。 */
        private static void translatePalettedContainer772To771(Reader reader, Writer writer, boolean blocks) {
        int bitsPerEntry = reader.readUnsignedByte();
        if (bitsPerEntry > 32) {
            throw new IllegalArgumentException("Bits Per Entry が大きすぎます: " + bitsPerEntry);
        }
        writer.writeUnsignedByte(bitsPerEntry);
        int entryCount = blocks ? 4096 : 64;
        if (bitsPerEntry == 0) {
            int value = reader.readVarInt();
            writer.writeVarInt(blocks
                    ? RegistryRemapper.mapBlockStateUp772(value)
                    : mapBiomeUp772(value));
            // 770・771・772とも長さ接頭辞は無い。
            return;
        }
        boolean indirect = blocks ? bitsPerEntry <= 8 : bitsPerEntry <= 3;
        if (indirect) {
            int paletteLength = reader.readVarInt();
            if (paletteLength <= 0 || paletteLength > (1 << bitsPerEntry)) {
                throw new IllegalArgumentException("パレット長が不正です: " + paletteLength);
            }
            writer.writeVarInt(paletteLength);
            for (int i = 0; i < paletteLength; i++) {
                int value = reader.readVarInt();
                writer.writeVarInt(blocks
                        ? RegistryRemapper.mapBlockStateUp772(value)
                        : mapBiomeUp772(value));
            }
            // 間接パレットの Data Array はインデックス列なので中身は変えない。
            // 770・771・772とも長さ接頭辞は無い。長さは bpe から計算する。
            int perLong = 64 / bitsPerEntry;
            int expected = (entryCount + perLong - 1) / perLong;
            for (int i = 0; i < expected; i++) {
                writer.writeLong(reader.readLong());
            }
            return;
        }
        // 直接パレット：Data Array の中身がレジストリ番号そのもの。
        // 770・771・772とも長さ接頭辞は無い。長さは bpe から計算する。
        int perLong = 64 / bitsPerEntry;
        int expected = (entryCount + perLong - 1) / perLong;
        long[] longs = new long[expected];
        for (int i = 0; i < expected; i++) {
            longs[i] = reader.readLong();
        }
        remapDirectEntries772To771(longs, bitsPerEntry, entryCount, blocks);
        for (long value : longs) {
            writer.writeLong(value);
        }
    }
""",
"translateExplosion771To772(byte[] payload)": """    /** Explosion（771 → 772）。knockback は770・771・772とも f64×3。粒子・音を付け替える。 */
    public static byte[] translateExplosion771To772(byte[] payload) {
        try {
            Reader reader = new Reader(payload);
            Writer writer = new Writer();
            writer.writeDouble(reader.readDouble()); // x
            writer.writeDouble(reader.readDouble()); // y
            writer.writeDouble(reader.readDouble()); // z
            boolean hasKnockback = reader.readBoolean();
            writer.writeBoolean(hasKnockback);
            if (hasKnockback) {
                writer.writeDouble(reader.readDouble());
                writer.writeDouble(reader.readDouble());
                writer.writeDouble(reader.readDouble());
            }
            translateParticle771To772(reader, writer);
            translateSoundHolder771To772(reader, writer);
            requireEnd(reader);
            return writer.bytes();
        } catch (IllegalArgumentException e) {
            LOG.warning("Explosion を変換できなかったため素通しします: " + e.getMessage());
            return payload;
        }
    }
""",
"translateExplosion772To771(byte[] payload)": """    /** Explosion（772 → 771）。knockback は770・771・772とも f64×3。粒子・音を付け替える。 */
    public static byte[] translateExplosion772To771(byte[] payload) {
        try {
            Reader reader = new Reader(payload);
            Writer writer = new Writer();
            writer.writeDouble(reader.readDouble()); // x
            writer.writeDouble(reader.readDouble()); // y
            writer.writeDouble(reader.readDouble()); // z
            boolean hasKnockback = reader.readBoolean();
            writer.writeBoolean(hasKnockback);
            if (hasKnockback) {
                writer.writeDouble(reader.readDouble());
                writer.writeDouble(reader.readDouble());
                writer.writeDouble(reader.readDouble());
            }
            Writer particleWriter = new Writer();
            translateParticle772To771(reader, particleWriter);
            writer.writeBytes(particleWriter.bytes());
            translateSoundHolder772To771(reader, writer);
            requireEnd(reader);
            return writer.bytes();
        } catch (IllegalArgumentException e) {
            LOG.warning("Explosion を変換できなかったため素通しします: " + e.getMessage());
            return payload;
        }
    }
""",
"translateSetCreativeSlot771To772(byte[] payload)": """    /** Set Creative Slot（771 → 772）。770・771・772とも UntrustedSlot（成分は ByteArray の生バイト）。成分の中身を771→772に変換する。 */
    public static byte[] translateSetCreativeSlot771To772(byte[] payload) {
        try {
            Reader reader = new Reader(payload);
            Writer writer = new Writer();
            writer.writeShort(reader.readShort()); // slotId
            int count = reader.readVarInt();
            writer.writeVarInt(count);
            if (count <= 0) {
                requireEnd(reader);
                return writer.bytes();
            }
            writer.writeVarInt(RegistryRemapper.mapItem771To772(reader.readVarInt()));
            int added = reader.readVarInt();
            int removed = reader.readVarInt();
            Writer addedOut = new Writer();
            int keptAdded = 0;
            for (int i = 0; i < added; i++) {
                int kind771 = reader.readVarInt();
                byte[] data = reader.readByteArray();
                Writer component = new Writer();
                if (!translateComponent771To772(new Reader(data), component, kind771)) {
                    continue;
                }
                addedOut.writeVarInt(kind771);
                addedOut.writeByteArray(component.bytes());
                keptAdded++;
            }
            Writer removedOut = new Writer();
            int keptRemoved = 0;
            for (int i = 0; i < removed; i++) {
                int kind772 = mapComponentKind771To772(reader.readVarInt());
                if (kind772 < 0) {
                    continue;
                }
                removedOut.writeVarInt(kind772);
                keptRemoved++;
            }
            requireEnd(reader);
            writer.writeVarInt(keptAdded);
            writer.writeVarInt(keptRemoved);
            writer.writeBytes(addedOut.bytes());
            writer.writeBytes(removedOut.bytes());
            return writer.bytes();
        } catch (IllegalArgumentException e) {
            LOG.warning("Set Creative Slot を変換できなかったため素通しします: " + e.getMessage());
            return payload;
        }
    }
""",
"translateSetCreativeSlot772To771(byte[] payload)": """    /** Set Creative Slot（772 → 771）。770・771・772とも UntrustedSlot。成分の中身を772→771に変換する。 */
    public static byte[] translateSetCreativeSlot772To771(byte[] payload) {
        try {
            Reader reader = new Reader(payload);
            Writer writer = new Writer();
            writer.writeShort(reader.readShort()); // slotId
            int count = reader.readVarInt();
            writer.writeVarInt(count);
            if (count <= 0) {
                requireEnd(reader);
                return writer.bytes();
            }
            writer.writeVarInt(RegistryRemapper.mapItemUp772(reader.readVarInt()));
            int added = reader.readVarInt();
            int removed = reader.readVarInt();
            Writer addedOut = new Writer();
            int keptAdded = 0;
            for (int i = 0; i < added; i++) {
                int kind772 = reader.readVarInt();
                byte[] data = reader.readByteArray();
                Writer component = new Writer();
                if (!translateComponent772To771(new Reader(data), component, kind772)) {
                    continue;
                }
                addedOut.writeVarInt(mapComponentKind772To771(kind772));
                addedOut.writeByteArray(component.bytes());
                keptAdded++;
            }
            Writer removedOut = new Writer();
            int keptRemoved = 0;
            for (int i = 0; i < removed; i++) {
                int kind771 = mapComponentKind772To771(reader.readVarInt());
                if (kind771 < 0) {
                    continue;
                }
                removedOut.writeVarInt(kind771);
                keptRemoved++;
            }
            requireEnd(reader);
            writer.writeVarInt(keptAdded);
            writer.writeVarInt(keptRemoved);
            writer.writeBytes(addedOut.bytes());
            writer.writeBytes(removedOut.bytes());
            return writer.bytes();
        } catch (IllegalArgumentException e) {
            LOG.warning("Set Creative Slot を変換できなかったため素通しします: " + e.getMessage());
            return payload;
        }
    }
""",
})

# ----------------------------------------------------------------------
# PlayTranslator 772 節（手書き）
# ----------------------------------------------------------------------

PLAY_772_SECTION = """
    // 771 ⇔ 772（1.21.7／1.21.8）
    // Join Game／Respawn／Synchronize Player Position／Login Success／
    // Client Information／Player Info／Set Held Item／Vehicle Move／
    // Player Chat は771と772で形式が同一のため変換器は要らない（素通し）。
    // 変わるのは Chat Message／Chat Command Signed（末尾の checksum u8 は
    // 770・771・772ともある。番号は 771→772 で変わらない。checksum の中身は
    // 771と同じ扱い）、Registry Data（レジストリの順序。1.21.7での変更は
    // jukebox_song の lava_chicken 挿入（9）と絵画 dennis 挿入（14）など。
    // minecraft:dialog は771からあるため通す）だけである。
    // ------------------------------------------------------------------

    /** Player Info（771 → 772）。形式は同一のため素通しする。 */
    public static byte[] translatePlayerInfo771To772(byte[] payload) {
        return payload;
    }

    /** Player Info（772 → 771）。形式は同一のため素通しする。 */
    public static byte[] translatePlayerInfo772To771(byte[] payload) {
        return payload;
    }

    /** Set Held Item（771 → 772）。形式は同一のため素通しする（varint のまま）。 */
    public static byte[] translateSetHeldItem771To772(byte[] payload) {
        return payload;
    }

    /** Set Held Item（772 → 771）。形式は同一のため素通しする。 */
    public static byte[] translateSetHeldItem772To771(byte[] payload) {
        return payload;
    }

    /** Vehicle Move（771 → 772）。形式は同一のため素通しする（末尾の onGround 付き）。 */
    public static byte[] translateVehicleMove771To772(byte[] payload) {
        return payload;
    }

    /** Vehicle Move（772 → 771）。形式は同一のため素通しする。 */
    public static byte[] translateVehicleMove772To771(byte[] payload) {
        return payload;
    }

    /** Player Chat（771 → 772）。先頭の globalIndex は770・771・772ともあるため素通しする。 */
    public static byte[] translatePlayerChat771To772(byte[] payload) {
        return payload;
    }

    /** Player Chat（772 → 771）。先頭の globalIndex は770・771・772ともあるため素通しする。 */
    public static byte[] translatePlayerChat772To771(byte[] payload) {
        return payload;
    }

    /**
     * Chat Message／Chat Command Signed（772 → 771）。末尾の checksum
     * （u8 1バイト）を取り除く。ペイロードが空の場合はそのまま返す。
     */
    public static byte[] stripChatChecksum772(byte[] payload) {
        if (payload.length == 0) {
            return payload;
        }
        return new Reader(payload).readBytes(payload.length - 1);
    }

    /**
     * Chat Message／Chat Command Signed（771 → 772）。末尾に checksum
     * （u8 1バイト）を追加する。checksum の式は未確認のため 0x00 を入れる
     * （実機での確認は未実施。計算式そのものの確認も未実施）。
     */
    public static byte[] appendChatChecksum772(byte[] payload) {
        Writer writer = new Writer();
        writer.writeBytes(payload);
        writer.writeByte(0);
        return writer.bytes();
    }

    /**
     * 771の Registry Data を772形式にする。minecraft:dialog は771から
     * あるためそのまま通す。{@code minecraft:jukebox_song} は lava_chicken
     * の挿入（9）で9番以降が+1ずれるため並べ替える（reorderRegistries）。
     * それ以外は1レジストリ分のペイロードを返す。
     */
    public static byte[] translateRegistryData771To772(byte[] payload) {
        Map<String, Tag.CompoundTag> reordered = reorderRegistries(
                parseRegistryData(payload),
                ProtocolVersion.V1_21_6, ProtocolVersion.V1_21_7);
        List<byte[]> built = buildRegistryData766(reordered);
        if (built.isEmpty()) {
            throw new IllegalArgumentException("Registry Data が空です。");
        }
        return built.get(0);
    }

    /**
     * 772の Registry Data を771形式にする。minecraft:dialog は771にも
     * あるため通す（reorderRegistries）。jukebox_song は lava_chicken を落として
     * 10番以降を-1する。それ以外は1レジストリ分のペイロードを返す。
     * 空になった場合は {@code null} を返す。
     */
    public static byte[] translateRegistryData772To771(byte[] payload) {
        Map<String, Tag.CompoundTag> reordered = reorderRegistries(
                parseRegistryData(payload),
                ProtocolVersion.V1_21_7, ProtocolVersion.V1_21_6);
        List<byte[]> built = buildRegistryData766(reordered);
        return built.isEmpty() ? null : built.get(0);
    }

"""


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


def main():
    chunk_sec, cs, ce = extract_section_to_eof(
        CT, "// 770 ⇔ 771（1.21.6）")
    print(f"chunk section: lines {cs+1}-{ce+1} ({len(chunk_sec)} chars)")
    Path("/tmp/chunk771_section.txt").write_text(chunk_sec, encoding="utf-8")
    chunk_shifted = shift(chunk_sec)
    Path("/tmp/chunk772_shifted.txt").write_text(chunk_shifted, encoding="utf-8")
    print(f"chunk shifted: {len(chunk_shifted)} chars")

    # --- ChunkTranslator 772 節 ---
    chunk772 = apply_fixes(chunk_shifted, CHUNK_STRING_FIXES, "chunk-string")
    for sig, new_text in CHUNK_METHOD_REPLACEMENTS.items():
        chunk772 = replace_method(chunk772, sig, new_text, "chunk")
    for sig in DELETE_METHODS:
        chunk772 = delete_method(chunk772, sig, "chunk-delete")
    chunk772 = chunk772.rstrip() + "\n" + EXTRA_METHODS_772
    print(f"chunk772: {len(chunk772)} chars, "
          f"{chunk772.count('translate')} translate occurrences")
    insert_section_before_class_close(
        CT, chunk772, "chunk", "translateSlot771To772")

    # --- PlayTranslator 772 節（手書き） ---
    insert_section_before_class_close(
        PT, PLAY_772_SECTION, "play", "translatePlayerChat771To772")

    # --- reorderRegistries の jukebox_song フィルタ：772 も残す ---
    # （実コードには V1_21_2 の条件もある。shift 版の fix は落とし穴。）
    pt_text = PT.read_text(encoding="utf-8")
    pt_text = fix(pt_text,
                  """            if (registryId.equals("minecraft:jukebox_song")
                    && toProtocol != ProtocolVersion.V1_21
                    && toProtocol != ProtocolVersion.V1_21_2
                    && toProtocol != ProtocolVersion.V1_21_4
                    && toProtocol != ProtocolVersion.V1_21_5
                    && toProtocol != ProtocolVersion.V1_21_6) {""",
                  """            if (registryId.equals("minecraft:jukebox_song")
                    && toProtocol != ProtocolVersion.V1_21
                    && toProtocol != ProtocolVersion.V1_21_2
                    && toProtocol != ProtocolVersion.V1_21_4
                    && toProtocol != ProtocolVersion.V1_21_5
                    && toProtocol != ProtocolVersion.V1_21_6
                    && toProtocol != ProtocolVersion.V1_21_7) {""",
                  "play-jukebox")
    # minecraft:dialog は771・772にしか無いため、771・772以外向けは落とす。
    # （既存のフィルタを拡張する。別フィルタを追加すると二重に落とす。）
    pt_text = fix(pt_text,
                  """            if (registryId.equals("minecraft:dialog")
                    && toProtocol != ProtocolVersion.V1_21_6) {""",
                  """            if (registryId.equals("minecraft:dialog")
                    && toProtocol != ProtocolVersion.V1_21_6
                    && toProtocol != ProtocolVersion.V1_21_7) {""",
                  "play-dialog")
    # jukebox_song の並べ替え：771↔772 の対応表を追加する。
    pt_text = fix(pt_text,
                  """            case "minecraft:jukebox_song" -> switch (from * 1000 + to) {
                case 770771 -> RegistryRemapper::mapJukeboxSong770To771;
                case 771770 -> RegistryRemapper::mapJukeboxSong771To770;
                default -> null;
            };""",
                  """            case "minecraft:jukebox_song" -> switch (from * 1000 + to) {
                case 770771 -> RegistryRemapper::mapJukeboxSong770To771;
                case 771770 -> RegistryRemapper::mapJukeboxSong771To770;
                case 771772 -> RegistryRemapper::mapJukeboxSong771To772;
                case 772771 -> RegistryRemapper::mapJukeboxSong772To771;
                default -> null;
            };""",
                  "play-jukebox-mapper")
    pt_text = fix(pt_text,
                  """            case "minecraft:jukebox_song" -> switch (to) {
                case 771 -> RegistryTables771.JUKEBOX_SONG_771_NAMES;
                case 770 -> RegistryTables771.JUKEBOX_SONG_770_NAMES;""",
                  """            case "minecraft:jukebox_song" -> switch (to) {
                case 772 -> RegistryTables772.JUKEBOX_SONG_772_NAMES;
                case 771 -> RegistryTables771.JUKEBOX_SONG_771_NAMES;
                case 770 -> RegistryTables771.JUKEBOX_SONG_770_NAMES;""",
                  "play-jukebox-names")
    PT.write_text(pt_text, encoding="utf-8")
    print("[play-registry] OK")
    print("完了。次は :detergentmc-protocol:compileJava で確認すること。")


if __name__ == "__main__":
    main()
