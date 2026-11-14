#!/usr/bin/env python3
"""ChunkTranslator / PlayTranslator の 772 節から 773 節を生成する。

方針: 772 節を抜き出して shift()（gen-773-proxysession.py のもの）で
1段ずらし、FIXES（手書きの置き換え）を当て、ファイル末尾の最終 `}` の
直前に挿入する。PlayTranslator の 773 節は手書きブロックで作る
（Player Info 等は772と773で同一形式のため素通しにする）。

772→773 で shift だけでは足りない点（FIXES で直す）:
- 成分種別は770・771・772・773とも96件・同じ番号のため KIND 表は恒等表になる。
- 771→772 で形式が変わった成分（unbreakable の bool→void、
  dyed_color の showTooltip 削除、enchantments／attribute_modifiers／
  trim／tool／item_block_predicates の末尾 bool の削除・追加、
  instrument のコンテナ化、jukebox_playable の showInTooltip 削除、
  hide_tooltip→tooltip_display 統合）は770・771・772・773で同形式のため、
  shift したヘルパーは誤って769形式を読み書きする。手書きで直す。
- チャンクの heightmap・paletted container は770・771・772・773とも直接配列
  （長さ varint 接頭辞なし）のため、769形式との変換をする
  shift 版は使えない。手書きで直す。
- パーティクルは copper_fire_flame（773=5）の挿入で5番以降が1つずれる。
  形式は同一。メタデータの型番号も同一。
- Explosion の knockback は770・771・772・773とも f64×3。
- jukebox_playable の曲IDは772・773で同一（lava_chicken は772で挿入済み）。
- Set Creative Slot は770・771・772・773とも UntrustedSlot。
- Spawn Entity：happy_ghast は773=57（772では56）。対応表で付け替える。
"""
import importlib.util
import re
import sys
from pathlib import Path

_HERE = Path(__file__).resolve().parent
_spec = importlib.util.spec_from_file_location("gen773ps", _HERE / "gen-773-proxysession.py")
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
    signature はメソッド名を含む一意の文字列（例: "translateSlot769To772(Reader"）。"""
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


# 成分種別の対応表（772 kind -> 773 kind。770・771・772・773とも96件・同じ番号のため恒等）。
KIND_772_TO_773 = {i: i for i in range(96)}
KIND_773_TO_772 = {i: i for i in range(96)}
assert len(KIND_772_TO_773) == 96
assert len(KIND_773_TO_772) == 96


def java_int_array(name, values, per_line=16):
    lines = []
    for i in range(0, len(values), per_line):
        lines.append("            " + ", ".join(str(v) for v in values[i:i + per_line]) + ",")
    body = "\n".join(lines)
    return f"    private static final int[] {name} = {{\n{body}\n    }};\n"


KIND_772_TO_773_JAVA = java_int_array(
    "KIND_772_TO_773", [KIND_772_TO_773[i] for i in range(96)])
KIND_773_TO_772_JAVA = java_int_array(
    "KIND_773_TO_772",
    [KIND_773_TO_772.get(i, -1) for i in range(96)])

# 772節の KIND 表（shift 後の old として使う。値は gen-772 と同じ）。
KIND_769_TO_772 = {
    **{i: i for i in range(0, 15)},
    15: -1, 16: 15,
    17: 16, 18: 17, 19: 18, 20: 19, 21: 20, 22: 21, 23: 22, 24: 23, 25: 24, 26: 25,
    27: 27, 28: 28, 29: 29, 30: 30, 31: 31, 32: 32,
    33: 34, 34: 35, 35: 36, 36: 37, 37: 38, 38: 39, 39: 40, 40: 41, 41: 42,
    42: 44, 43: 45, 44: 46, 45: 47, 46: 48, 47: 49, 48: 50, 49: 51, 50: 52,
    51: 54, 52: 55, 53: 57, 54: 58, 55: 59, 56: 60, 57: 61, 58: 62, 59: 63,
    60: 64, 61: 65, 62: 66, 63: 67, 64: 68, 65: 69, 66: 70,
}
assert len(KIND_769_TO_772) == 67
KIND_772_TO_769 = {v: k for k, v in KIND_769_TO_772.items() if v >= 0}
assert len(KIND_772_TO_769) == 66  # 15（hide_additional_tooltip）が落ちる分

# shift 後の 773 節に残る（誤った）KIND 表のテキスト。置き換えの old に使う。
KIND_772_TO_773_SHIFTED_JAVA = java_int_array(
    "KIND_772_TO_773", [KIND_769_TO_772[i] for i in range(67)])
KIND_773_TO_772_SHIFTED_JAVA = java_int_array(
    "KIND_773_TO_772",
    [KIND_772_TO_769.get(i, -1) for i in range(96)])



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


# 772節のヘッダーコメント（shift 後の old を shift() で作る）。
HEADER_772 = """    // 769 ⇔ 772（1.21.7）
    // 成分種別は769が67件、772が96件。番号が大きくずれるため KIND 表
    // （KIND_769_TO_772／KIND_772_TO_769）で付け替える。
    // 変わるのは中身のレジストリID（アイテム・ブロック状態・
    // エンティティ・音・パーティクル）と成分の形式（tooltip_display への
    // 統合、showTooltip／showInTooltip の削除、tool の
    // canDestroyBlocksInCreative 追加等）。バイオーム・エンチャント・属性・
    // メニュー・ダメージ種別は同一のため付け替えない。エンティティは
    // potion（769=99）が splash_potion（772=99）と lingering_potion
    // （772=100）に分かれる。tinted_leaves（772=35）と firefly
    // （772=113）は769に無いため772→771では落とす。
    // チャンクの heightmap は NBT から直接の配列に変わった。"""

HEADER_773 = """    // 772 ⇔ 773（1.21.9）
    // 成分種別は770・771・772・773とも96件・同じ番号のため KIND 表は恒等表。
    // 変わるのは中身のレジストリID（アイテム・ブロック状態・
    // エンティティ・音・パーティクル）と成分の中身（jukebox_playable の
    // 曲ID、break_sound／blocks_attacks の音ID）。バイオーム・
    // エンチャント・属性・メニュー・ダメージ種別・パーティクルは同一の
    // ため付け替えない。エンティティは happy_ghast（771・772・773とも56）。
    // tinted_leaves（35）と firefly（113）は770・771・772・773ともある。
    // チャンクの heightmap は770・771・772・773とも直接の配列（長さ varint 無し）。"""

# 772節の KIND 表まわり（shift 後の old を shift() で作る）。
KIND_COUNT_772 = """    /** 成分種別の件数（772 は 96 件。769 は 67 件）。 */
    private static final int DATA_COMPONENT_TYPE_COUNT_772 = 96;"""

KIND_COUNT_773 = """    /** 成分種別の件数（770・771・772・773とも 96 件）。 */
    private static final int DATA_COMPONENT_TYPE_COUNT_773 = 96;"""

KIND_JAVADOC_1_772 = """    /**
     * 成分種別の対応表（769 kind → 772 kind）。-1 は 772 に無いため落とす
     * （15＝hide_additional_tooltip）。16（hide_tooltip）は 15
     * （tooltip_display）になる。
     */"""

KIND_JAVADOC_1_773 = """    /**
     * 成分種別の対応表（772 kind → 773 kind）。770・771・772・773とも96件・
     * 同じ番号のため恒等表（落とす種別は無い）。
     */"""

KIND_JAVADOC_2_772 = """    /**
     * 成分種別の対応表（772 kind → 769 kind）。-1 は 769 に無いため落とす。
     * 15（tooltip_display）は内容次第で hide_tooltip（16）か
     * hide_additional_tooltip（15）になるためここでは -1 にしてある。
     */"""

KIND_JAVADOC_2_773 = """    /**
     * 成分種別の対応表（773 kind → 772 kind）。770・771・772・773とも96件・
     * 同じ番号のため恒等表（落とす種別は無い）。
     */"""


# ----------------------------------------------------------------------
# ChunkTranslator 773 節の文字列 FIXES（シフト済みテキストに適用）
# ----------------------------------------------------------------------

CHUNK_STRING_FIXES = [
    # ヘッダーコメント：happy_ghast は773=57、パーティクルは copper_fire_flame
    # の挿入でずれる。jukebox_song の曲IDは772・773で同一。
    ("""    // 変わるのは中身のレジストリID（アイテム・ブロック状態・
    // エンティティ・音・パーティクル）と成分の中身（jukebox_playable の
    // 曲ID、break_sound／blocks_attacks の音ID）。バイオーム・
    // エンチャント・属性・メニュー・ダメージ種別・パーティクルは同一の
    // ため付け替えない。エンティティは happy_ghast（771・772・773とも56）。
    // tinted_leaves（35）と firefly（113）は770・771・772・773ともある。""",
     """    // 変わるのは中身のレジストリID（アイテム・ブロック状態・
    // エンティティ・音・パーティクル）と成分の中身（break_sound／
    // blocks_attacks の音ID）。バイオーム・エンチャント・属性・メニュー・
    // ダメージ種別は同一のため付け替えない。エンティティは
    // happy_ghast（773=57。772では56）。パーティクルは copper_fire_flame
    // （773=5）の挿入で5番以降が1つずれるため対応表に通す。
    // tinted_leaves（773=36）と firefly（773=114）は770・771・772・773ともある。"""),
    # パーティクル定数：copper_fire_flame（773=5）の挿入で5番以降が+1。
    ("""    /** 773の trail 粒子のID（vv-1.21.9.json の particles 順）。 */
    private static final int PARTICLE_773_TRAIL = 48;
    /** 773の block_crumble 粒子のID。 */
    private static final int PARTICLE_773_BLOCK_CRUMBLE = 112;
    /** 773の pale_oak_leaves 粒子のID（769・770・771・772・773とも 34）。 */
    private static final int PARTICLE_773_PALE_OAK_LEAVES = 34;
    /** 773の tinted_leaves 粒子のID（770・771・772・773とも 35）。 */
    private static final int PARTICLE_773_TINTED_LEAVES = 35;
    /** 773の firefly 粒子のID（770・771・772・773とも 113）。 */
    private static final int PARTICLE_773_FIREFLY = 113;""",
     """    /** 773の trail 粒子のID（vv-1.21.9.json の particles 順）。 */
    private static final int PARTICLE_773_TRAIL = 49;
    /** 773の block_crumble 粒子のID。 */
    private static final int PARTICLE_773_BLOCK_CRUMBLE = 113;
    /** 773の pale_oak_leaves 粒子のID（773=35。772では34）。 */
    private static final int PARTICLE_773_PALE_OAK_LEAVES = 35;
    /** 773の tinted_leaves 粒子のID（773=36。772では35）。 */
    private static final int PARTICLE_773_TINTED_LEAVES = 36;
    /** 773の firefly 粒子のID（773=114。772では113）。 */
    private static final int PARTICLE_773_FIREFLY = 114;"""),
    # パーティクル節のヘッダー：copper_fire_flame の挿入でずれる。
    ("""    // パーティクルは771・772・773で同一のため、ID は対応表に通すだけ
    // （実質そのまま）。trail（771・772・773とも48）／block_crumble
    // （771・772・773とも112）とも duration・ブロック状態ID付き。""",
     """    // パーティクルは copper_fire_flame（773=5）の挿入で5番以降が1つ
    // ずれるため、ID は対応表に通す。trail（773=49）／block_crumble
    // （773=113）とも duration・ブロック状態ID付き。"""),
    # translateParticle773To772 の注記：「同一」ではない（形式は同一）。
    ("""     * パーティクルは770・771・772・773とも同一（tinted_leaves・firefly も両方に
     * ある）ため常に変換できる（{@code true} を返す）。""",
     """     * パーティクルは copper_fire_flame（773=5）の挿入で5番以降が1つずれるが
     * 対応表で付け替えるため常に変換できる（{@code true} を返す）。"""),
    # Spawn Entity（773→772）：falling_block は773=50。
    ("""     * Spawn Entity（773 → 772）。種別を付け替え、falling_block（773=49）の
     * objectData（ブロック状態ID）も付け替える。""",
     """     * Spawn Entity（773 → 772）。種別を付け替え、falling_block（773=50）の
     * objectData（ブロック状態ID）も付け替える。"""),
    ("""            int mappedObjectData = type == 49
                    ? RegistryRemapper.mapBlockStateUp773(objectData)
                    : objectData;""",
     """            int mappedObjectData = type == 50
                    ? RegistryRemapper.mapBlockStateUp773(objectData)
                    : objectData;"""),
    # ボート variant のコメント：happy_ghast は773=57。
    ("""     * 773の種別を772に直してから772の表を使う（happy_ghast は
     * 771・772・773とも56のため、種別はそのまま対応表に通す）。""",
     """     * 773の種別を772に直してから772の表を使う（happy_ghast は773=57。
     * 772では56。種別は対応表に通す）。"""),
    # 絵画メタデータ Holder（772→773）の注記：絵画は772・773とも51種・同一。
    ("""    /** 絵画バリアントの Holder を772→773向けに変換する。絵画は772が50種・773が51種（dennis を14に挿入）。772の有効なIDは DROP にならない（念のため）。 */""",
     """    /** 絵画バリアントの Holder を772→773向けに変換する。絵画は772・773とも51種・同じ並びのため DROP にはならない（念のため）。 */"""),
    # 絵画メタデータ Holder（772→773）のログ：DROP は773側の無効ID。
    ("""            logOnce("painting-variant-drop-773",
                    "772に無い絵画のメタデータを落とします（id=" + (holder - 1) + "）。");""",
     """            logOnce("painting-variant-drop-773",
                    "773に無い絵画のメタデータを落とします（id=" + (holder - 1) + "）。");"""),
    # 絵画メタデータ Holder（773→772）の注記：dennis は772にもある。
    ("""    /** 絵画バリアントの Holder を773→772向けに変換する。dennis（773=14）は772に無いため DROP する（メタデータの項目ごと落とす）。 */""",
     """    /** 絵画バリアントの Holder を773→772向けに変換する。絵画は772・773とも51種・同じ並びのため DROP にはならない（念のため）。 */"""),
    # チャンクデータ節の注記：ブロック状態もずれる（+1,725）。バイオームは同一。
    ("""    // 772⇔773ではブロック状態・バイオームは変わらない（アイテム・音・
    // 絵画・ジュークボックスだけがずれる）。パレットの付け替えは対応表に任せる。""",
     """    // 772⇔773ではブロック状態（銅系など+1,725）・アイテム・音・
    // エンティティ・パーティクル・ブロックエンティティ種別がずれる。
    // バイオーム・エンチャント・属性・絵画・ジュークボックスは同一。
    // パレットの付け替えは対応表に任せる。"""),
    # translateChunkData772To773 の注記：バイオームは同一、ブロック状態は付替。
    ("""    /** チャンクデータを772→773向けに変換する。heightmap は770・771・772・773とも直接の配列（varint 件数＋（type、長さ、long列））のためそのまま写す。セクションのブロック状態・バイオームとブロックエンティティ種別を付け替える。 */""",
     """    /** チャンクデータを772→773向けに変換する。heightmap は770・771・772・773とも直接の配列（varint 件数＋（type、長さ、long列））のためそのまま写す。セクションのブロック状態・ブロックエンティティ種別を付け替える。バイオームは同一。 */"""),
    # Spawn Entity（772→773）の注記：エンティティは同じ並びではない。
    ("""     * Spawn Entity（772 → 773）。種別を付け替え、falling_block（772=49）の
     * objectData（ブロック状態ID）も付け替える。エンティティは771・772・773で
     * 同じ並びのため、種別は対応表に通すだけ（実質そのまま）。""",
     """     * Spawn Entity（772 → 773）。種別を付け替え、falling_block（772=49）の
     * objectData（ブロック状態ID）も付け替える。エンティティは copper_golem
     * （773=27）と mannequin（773=82）の挿入でずれるため対応表に通す。"""),
    # メタデータ節の注記：粒子IDは対応表に通す（型番号は同一）。
    ("""    // 型 14／15（ブロック状態）は771・772・773で同じ番号のため対応表に
    // 通すだけ（実質そのまま）。型 24（狼バリアント）は Holder（varint）
    // のためそのまま写す。型 17／18（粒子）も771・772・773で同一。""",
     """    // 型 14／15（ブロック状態）は771・772・773で同じ番号のため対応表に
    // 通すだけ（実質そのまま）。型 24（狼バリアント）は Holder（varint）
    // のためそのまま写す。型 17／18（粒子）は ID を対応表に通す。"""),
]


# 削除するメソッド（shift 版では不要・誤りのため。replace 後に削除する）。
DELETE_METHODS = [
    # 772節には770時代の削除対象（hide_tooltip／trim 769合成／
    # Heightmap NBT変換）は既に無いため、削除は不要。
]


# ----------------------------------------------------------------------
# ChunkTranslator 773 節のメソッド置き換え
# ----------------------------------------------------------------------

CHUNK_METHOD_REPLACEMENTS = {
"translateComponent772To773(Reader reader, Writer writer, int kind772)": """    /**
     * 成分1個を772→773向けに変換する。770・771・772・773とも96件・同じ番号・
     * 同じ形式のため種別はそのまま。レジストリIDを付け替えるものだけ
     * 変換する。落とす成分は無い（{@code false} を返すことは無いが、
     * 呼び出し側の規約に合わせる）。
     */
    private static boolean translateComponent772To773(Reader reader, Writer writer, int kind772) {
        switch (kind772) {
            case 4 -> { } // unbreakable（void。770・771・772・773とも中身なし）
            case 10, 34 -> translateEnchantments772To773(reader, writer);
            case 11, 12 -> translateItemBlockPredicates772To773(reader, writer);
            case 13 -> translateAttributeModifiers772To773(reader, writer);
            case 14 -> translateCustomModelData772To773(reader, writer);
            case 15 -> translateTooltipDisplay772To773(reader, writer);
            case 16 -> writer.writeBoolean(reader.readBoolean()); // enchantment_glint_override
            case 20 -> translateFood772To773(reader, writer);
            case 21 -> translateConsumable772To773(reader, writer);
            case 22 -> translateUseRemainder772To773(reader, writer);
            case 25 -> translateTool772To773(reader, writer);
            case 26 -> { // weapon：varint＋float（レジストリ無し）
                writer.writeVarInt(reader.readVarInt());
                writer.writeFloat(reader.readFloat());
            }
            case 28 -> translateEquippable772To773(reader, writer);
            case 29 -> translateRepairable772To773(reader, writer);
            case 32 -> translateDeathProtection772To773(reader, writer);
            case 33 -> translateBlocksAttacks772To773(reader, writer);
            case 35 -> translateDyedColor772To773(reader, writer);
            case 40, 41, 66 -> translateSlotArray772To773(reader, writer);
            case 43 -> writer.writeFloat(reader.readFloat()); // potion_duration_scale
            case 47 -> {
                return translateTrim772To773(reader, writer);
            }
            case 52 -> {
                return translateInstrument772To773(reader, writer);
            }
            case 53 -> copyProvidesTrimMaterial772To773(reader, writer);
            case 55 -> translateJukeboxPlayable772To773(reader, writer);
            case 56 -> writer.writeString(reader.readString()); // provides_banner_patterns
            case 62 -> translateNoteBlockSound772To773(reader, writer);
            case 63 -> translateBannerPatterns772To773(reader, writer);
            case 65 -> translatePotDecorations772To773(reader, writer);
            case 71 -> translateBreakSound772To773(reader, writer);
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
            case 89 -> copyPaintingVariantHolder772To773(reader, writer);
            default -> copyComponentOpaque773(reader, writer, kind772);
        }
        return true;
    }
""",
"translateComponent773To772(Reader reader, Writer writer, int kind773)": """    /**
     * 成分1個を773→772向けに変換する。770・771・772・773とも96件・同じ番号・
     * 同じ形式のため種別はそのまま。レジストリIDを付け替えるものだけ
     * 変換する。772に無い種別は無いため、落とす成分は無い
     * （{@code false} を返すことは無いが、呼び出し側の規約に合わせる）。
     */
    private static boolean translateComponent773To772(Reader reader, Writer writer, int kind773) {
        switch (kind773) {
            case 4 -> { } // unbreakable（void。770・771・772・773とも中身なし）
            case 10, 34 -> translateEnchantments773To772(reader, writer);
            case 11, 12 -> translateItemBlockPredicates773To772(reader, writer);
            case 13 -> translateAttributeModifiers773To772(reader, writer);
            case 14 -> translateCustomModelData773To772(reader, writer);
            case 15 -> translateTooltipDisplay773To772(reader, writer);
            case 16 -> writer.writeBoolean(reader.readBoolean()); // enchantment_glint_override
            case 20 -> translateFood773To772(reader, writer);
            case 21 -> translateConsumable773To772(reader, writer);
            case 22 -> translateUseRemainder773To772(reader, writer);
            case 25 -> translateTool773To772(reader, writer);
            case 26 -> { // weapon：varint＋float（レジストリ無し）
                writer.writeVarInt(reader.readVarInt());
                writer.writeFloat(reader.readFloat());
            }
            case 28 -> translateEquippable773To772(reader, writer);
            case 29 -> translateRepairable773To772(reader, writer);
            case 32 -> translateDeathProtection773To772(reader, writer);
            case 33 -> translateBlocksAttacks773To772(reader, writer);
            case 35 -> translateDyedColor773To772(reader, writer);
            case 40, 41, 66 -> translateSlotArray773To772(reader, writer);
            case 43 -> writer.writeFloat(reader.readFloat()); // potion_duration_scale
            case 47 -> {
                return translateTrim773To772(reader, writer);
            }
            case 52 -> {
                return translateInstrument773To772(reader, writer);
            }
            case 53 -> copyProvidesTrimMaterial773To772(reader, writer);
            case 55 -> translateJukeboxPlayable773To772(reader, writer);
            case 56 -> writer.writeString(reader.readString()); // provides_banner_patterns
            case 62 -> translateNoteBlockSound773To772(reader, writer);
            case 63 -> translateBannerPatterns773To772(reader, writer);
            case 65 -> translatePotDecorations773To772(reader, writer);
            case 71 -> translateBreakSound773To772(reader, writer);
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
            case 89 -> copyPaintingVariantHolder773To772(reader, writer);
            default -> copyComponentOpaque773(reader, writer, kind773);
        }
        return true;
    }
""",
"translateSlotComponents773To772(Reader reader, Writer writer)": """    /** スロットの added／removed 成分部分だけを773→772向けに変換する。770・771・772・773で種別番号は同じため、tooltip_display（15）の特別扱いは要らない。 */
    private static void translateSlotComponents773To772(Reader reader, Writer writer) {
        int added = reader.readVarInt();
        Writer kept = new Writer();
        int keptCount = 0;
        for (int i = 0; i < added; i++) {
            int kind773 = reader.readVarInt();
            int kind772 = mapComponentKind773To772(kind773);
            if (kind772 < 0) {
                skipComponentData773(reader, kind773);
                continue;
            }
            Writer component = new Writer();
            if (!translateComponent773To772(reader, component, kind773)) {
                continue;
            }
            kept.writeVarInt(kind772);
            kept.writeBytes(component.bytes());
            keptCount++;
        }
        writer.writeVarInt(keptCount);
        writer.writeBytes(kept.bytes());
        int removed = reader.readVarInt();
        Writer keptRemoved = new Writer();
        int keptRemovedCount = 0;
        for (int i = 0; i < removed; i++) {
            int kind772 = mapComponentKind773To772(reader.readVarInt());
            if (kind772 < 0) {
                continue;
            }
            keptRemoved.writeVarInt(kind772);
            keptRemovedCount++;
        }
        writer.writeVarInt(keptRemovedCount);
        writer.writeBytes(keptRemoved.bytes());
    }
""",
"translateComponentList773To772(Reader reader, Writer writer)": """    /** 成分の列（個数 VarInt＋成分1個ずつ）を773→772向けに変換する。770・771・772・773で種別番号は同じため、tooltip_display（15）の特別扱いは要らない。 */
    private static void translateComponentList773To772(Reader reader, Writer writer) {
        int added = reader.readVarInt();
        Writer kept = new Writer();
        int keptCount = 0;
        for (int i = 0; i < added; i++) {
            int kind773 = reader.readVarInt();
            int kind772 = mapComponentKind773To772(kind773);
            if (kind772 < 0) {
                skipComponentData773(reader, kind773);
                continue;
            }
            Writer component = new Writer();
            if (!translateComponent773To772(reader, component, kind773)) {
                continue;
            }
            kept.writeVarInt(kind772);
            kept.writeBytes(component.bytes());
            keptCount++;
        }
        writer.writeVarInt(keptCount);
        writer.writeBytes(kept.bytes());
    }
""",
"translateTooltipDisplay773To772(Reader reader, Writer writer)": """    /** tooltip_display（773→772）。770・771・772・773とも {hideTooltip bool, hiddenComponents の列} のためそのまま写す。 */
    private static void translateTooltipDisplay773To772(Reader reader, Writer writer) {
        writer.writeBoolean(reader.readBoolean()); // hideTooltip
        int hidden = reader.readVarInt();
        writer.writeVarInt(hidden);
        for (int i = 0; i < hidden; i++) {
            writer.writeVarInt(reader.readVarInt());
        }
    }
""",
"translateUnbreakable772To773(Reader reader, Writer writer)": """    /** unbreakable を772→773向けに変換する。770・771・772・773とも void のため何もしない。 */
    private static void translateUnbreakable772To773(Reader reader, Writer writer) {
    }
""",
"translateUnbreakable773To772(Reader reader, Writer writer)": """    /** unbreakable を773→772向けに変換する。770・771・772・773とも void のため何もしない。 */
    private static void translateUnbreakable773To772(Reader reader, Writer writer) {
    }
""",
"translateDyedColor772To773(Reader reader, Writer writer)": """    /** dyed_color を772→773向けに変換する。770・771・772・773とも色 i32 だけ。 */
    private static void translateDyedColor772To773(Reader reader, Writer writer) {
        writer.writeInt(reader.readInt()); // color
    }
""",
"translateDyedColor773To772(Reader reader, Writer writer)": """    /** dyed_color を773→772向けに変換する。770・771・772・773とも色 i32 だけ。 */
    private static void translateDyedColor773To772(Reader reader, Writer writer) {
        writer.writeInt(reader.readInt()); // color
    }
""",
"copyPaintingVariantHolder772To773(Reader reader, Writer writer)": """    /** painting/variant（772→773）の Holder を変換する。絵画は772が50種・773が51種（dennis を14に挿入）。14番以降は+1ずれる。 */
    private static void copyPaintingVariantHolder772To773(Reader reader, Writer writer) {
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
        int mapped = RegistryRemapper.mapPaintingVariant772To773(holder - 1);
        if (mapped == RegistryRemapper.DROP) {
            // 範囲外の絵画ID。落とす表現が無いため kebab（0）で代用する。
            mapped = 0;
        }
        writer.writeVarInt(mapped + 1);
    }
""",
"copyPaintingVariantHolder773To772(Reader reader, Writer writer)": """    /** painting/variant（773→772）の Holder を変換する。dennis（773=14）は772に無いため kebab（0）で代用する。14番より後は-1する。 */
    private static void copyPaintingVariantHolder773To772(Reader reader, Writer writer) {
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
        int mapped = RegistryRemapper.mapPaintingVariant773To772(holder - 1);
        if (mapped == RegistryRemapper.DROP) {
            // dennis は772に無い。落とす表現が無いため kebab（0）で代用する。
            mapped = 0;
        }
        writer.writeVarInt(mapped + 1);
    }
""",

"translateParticleData773To772(Reader reader, Writer writer, int particleId)": """    /** パーティクルのデータ部分を773→772向けに変換する（particleId は773のID）。copper_fire_flame（773=5）の挿入で5番以降が1つずれるため、case は773のIDで書く。 */
    private static void translateParticleData773To772(Reader reader, Writer writer, int particleId) {
        switch (particleId) {
            case 1, 2, 29, 109 -> // block、block_marker、falling_dust、dust_pillar：ブロック状態IDを付け替える
                    writer.writeVarInt(RegistryRemapper.mapBlockStateUp773(reader.readVarInt()));
            case 14 -> { // dust：色 i32＋大きさ
                writer.writeInt(reader.readInt());
                writer.writeFloat(reader.readFloat());
            }
            case 15 -> { // dust_color_transition：from i32＋to i32＋大きさ
                writer.writeInt(reader.readInt());
                writer.writeInt(reader.readInt());
                writer.writeFloat(reader.readFloat());
            }
            case 21 -> writer.writeInt(reader.readInt()); // entity_effect：色の i32
            case 38 -> writer.writeFloat(reader.readFloat()); // sculk_charge：回転（1個の float）
            case 47 -> translateSlot773To772(reader, writer); // item
            case 48 -> translateVibration(reader, writer); // vibration（同一形式）
            case 103 -> writer.writeVarInt(reader.readVarInt()); // shriek：遅延 tick 数
            case 49 -> { // trail：target vec3f64＋color i32＋duration
                writer.writeDouble(reader.readDouble());
                writer.writeDouble(reader.readDouble());
                writer.writeDouble(reader.readDouble());
                writer.writeInt(reader.readInt());
                writer.writeVarInt(reader.readVarInt()); // duration
            }
            case 113 -> // block_crumble：ブロック状態IDを付け替える
                    writer.writeVarInt(RegistryRemapper.mapBlockStateUp773(reader.readVarInt()));
            default -> {
                // その他の粒子にデータは無い。
            }
        }
    }
""",
}


# 773節に新しく追加するヘルパー（shift 版には無いもの）。
# 772節を shift すると EXTRA の11件はすべて shift 版に既にある
# （同一内容）ため、ここには何も残さない。絵画2件だけは shift 版が
# そのまま写し（dennis を落とさない）ため、置換表へ移してある。
EXTRA_METHODS_773 = ""

# 置き換えの続き（成分ヘルパー：771→772の形式変換を含まないもの）。
CHUNK_METHOD_REPLACEMENTS.update({
"translateEnchantments772To773(Reader reader, Writer writer)": """    /** enchantments／stored_enchantments を772→773向けに変換する。IDは同一、末尾の showTooltip は770・771・772・773とも無い。 */
    private static void translateEnchantments772To773(Reader reader, Writer writer) {
        int count = reader.readVarInt();
        Writer kept = new Writer();
        int keptCount = 0;
        for (int i = 0; i < count; i++) {
            int id = reader.readVarInt();
            int level = reader.readVarInt();
            int mapped = RegistryRemapper.mapEnchantment772To773(id);
            if (mapped == RegistryRemapper.DROP) {
                logOnce("enchantment-drop-773",
                        "773に無いエンチャントを落とします（id=" + id + "）。");
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
"translateEnchantments773To772(Reader reader, Writer writer)": """    /** enchantments／stored_enchantments を773→772向けに変換する。IDは同一、末尾の showTooltip は770・771・772・773とも無い。 */
    private static void translateEnchantments773To772(Reader reader, Writer writer) {
        int count = reader.readVarInt();
        Writer kept = new Writer();
        int keptCount = 0;
        for (int i = 0; i < count; i++) {
            int id = reader.readVarInt();
            int level = reader.readVarInt();
            int mapped = RegistryRemapper.mapEnchantment773To772(id);
            if (mapped == RegistryRemapper.DROP) {
                logOnce("enchantment-drop-772-from-773",
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
"translateAttributeModifiers772To773(Reader reader, Writer writer)": """    /** attribute_modifiers を772→773向けに変換する。770・771・772・773とも {属性の配列} のみ（末尾の showTooltip は無い）。 */
    private static void translateAttributeModifiers772To773(Reader reader, Writer writer) {
        int count = reader.readVarInt();
        Writer kept = new Writer();
        int keptCount = 0;
        for (int i = 0; i < count; i++) {
            int attrId = reader.readVarInt();
            String name = reader.readString();
            double value = reader.readDouble();
            int operation = reader.readVarInt();
            int slot = reader.readVarInt();
            int mapped = RegistryRemapper.mapAttribute772To773(attrId);
            if (mapped == RegistryRemapper.DROP) {
                logOnce("attribute-modifier-drop-773",
                        "773に無い属性の修飾子を落とします（id=" + attrId + "）。");
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
"translateAttributeModifiers773To772(Reader reader, Writer writer)": """    /** attribute_modifiers を773→772向けに変換する。770・771・772・773とも {属性の配列} のみ。 */
    private static void translateAttributeModifiers773To772(Reader reader, Writer writer) {
        int count = reader.readVarInt();
        Writer kept = new Writer();
        int keptCount = 0;
        for (int i = 0; i < count; i++) {
            int attrId = reader.readVarInt();
            String name = reader.readString();
            double value = reader.readDouble();
            int operation = reader.readVarInt();
            int slot = reader.readVarInt();
            int mapped = RegistryRemapper.mapAttribute773To772(attrId);
            if (mapped == RegistryRemapper.DROP) {
                logOnce("attribute-modifier-drop-772-from-773",
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
"translateTrim772To773(Reader reader, Writer writer)": """    /** trim を772→773向けに変換する。材料・型の Holder。770・771・772・773とも showInTooltip は無い。 */
    private static boolean translateTrim772To773(Reader reader, Writer writer) {
        if (!translateTrimMaterial772To773(reader, writer)) {
            return false;
        }
        return translateTrimPattern772To773(reader, writer);
    }
""",
"translateTrim773To772(Reader reader, Writer writer)": """    /** trim を773→772向けに変換する。材料・型の Holder。770・771・772・773とも showInTooltip は無い。 */
    private static boolean translateTrim773To772(Reader reader, Writer writer) {
        if (!translateTrimMaterial773To772(reader, writer)) {
            return false;
        }
        return translateTrimPattern773To772(reader, writer);
    }
""",
"translateTrimMaterial772To773(Reader reader, Writer writer)": """    /** trim の材料の Holder を772→773向けに変換する。770・771・772・773とも直書きは {assetBase, overrides, description}（ingredientId は無い）。材料IDは11種・同じ並びのためそのまま。 */
    private static boolean translateTrimMaterial772To773(Reader reader, Writer writer) {
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
"translateTrimMaterial773To772(Reader reader, Writer writer)": """    /** trim の材料の Holder を773→772向けに変換する。直書きは {assetBase, overrides, description}。 */
    private static boolean translateTrimMaterial773To772(Reader reader, Writer writer) {
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
"translateTrimPattern772To773(Reader reader, Writer writer)": """    /** trim の型の Holder を772→773向けに変換する。770・771・772・773とも直書きは {assetId, description, decal}（templateItemId は無い）。 */
    private static boolean translateTrimPattern772To773(Reader reader, Writer writer) {
        int holder = reader.readVarInt();
        if (holder != 0) {
            int mapped = RegistryRemapper.mapTrimPattern772To773(holder - 1);
            if (mapped == RegistryRemapper.DROP) {
                logOnce("trim-pattern-drop-773",
                        "773に無い装飾パターンの trim を落とします（id=" + (holder - 1) + "）。");
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
"translateTrimPattern773To772(Reader reader, Writer writer)": """    /** trim の型の Holder を773→772向けに変換する。直書きは {assetId, description, decal}。 */
    private static boolean translateTrimPattern773To772(Reader reader, Writer writer) {
        int holder = reader.readVarInt();
        if (holder != 0) {
            int mapped = RegistryRemapper.mapTrimPattern773To772(holder - 1);
            if (mapped == RegistryRemapper.DROP) {
                logOnce("trim-pattern-drop-772-from-773",
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
})

CHUNK_METHOD_REPLACEMENTS.update({
"translateInstrument772To773(Reader reader, Writer writer)": """    /**
     * instrument コンポーネントを772→773向けに変換する。770・771・772・773とも
     * {hasHolder bool, data：true なら Holder／false なら楽器名} の
     * コンテナ。楽器IDは770・771・772・773とも8件・同じ並びのためそのまま。
     */
    private static boolean translateInstrument772To773(Reader reader, Writer writer) {
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
        translateSoundHolder772To773(reader, writer);
        writer.writeFloat(reader.readFloat()); // useDuration
        writer.writeFloat(reader.readFloat()); // range
        copyOptionalAnonymousNbt(reader, writer);
        return true;
    }
""",
"translateInstrument773To772(Reader reader, Writer writer)": """    /**
     * instrument コンポーネントを773→772向けに変換する。770・771・772・773とも
     * {hasHolder bool, data} のコンテナ。楽器IDは8件・同じ並びのためそのまま。
     */
    private static boolean translateInstrument773To772(Reader reader, Writer writer) {
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
        translateSoundHolder773To772(reader, writer);
        writer.writeFloat(reader.readFloat()); // useDuration
        writer.writeFloat(reader.readFloat()); // range
        copyOptionalAnonymousNbt(reader, writer);
        return true;
    }
""",
"translateJukeboxPlayable772To773(Reader reader, Writer writer)": """    /** jukebox_playable を772→773向けに変換する。曲IDは772・773で同一（21曲・同じ並び）。直書きの音の Holder も付け替える。末尾の showInTooltip は770・771・772・773ともある。 */
    private static void translateJukeboxPlayable772To773(Reader reader, Writer writer) {
        boolean hasHolder = reader.readBoolean();
        writer.writeBoolean(hasHolder);
        if (hasHolder) {
            int holder = reader.readVarInt();
            if (holder == 0) {
                writer.writeVarInt(0);
                translateSoundHolder772To773(reader, writer);
                writer.writeAnonymousNbt(reader.readAnonymousNbt().tag()); // description
                writer.writeFloat(reader.readFloat()); // lengthInSeconds
                writer.writeVarInt(reader.readVarInt()); // comparatorOutput
            } else {
                int mapped = RegistryRemapper.mapJukeboxSong772To773(holder - 1);
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
"translateJukeboxPlayable773To772(Reader reader, Writer writer)": """    /** jukebox_playable を773→772向けに変換する。曲IDは772・773で同一（21曲・同じ並び）。末尾の showInTooltip は770・771・772・773ともある。 */
    private static void translateJukeboxPlayable773To772(Reader reader, Writer writer) {
        boolean hasHolder = reader.readBoolean();
        writer.writeBoolean(hasHolder);
        if (hasHolder) {
            int holder = reader.readVarInt();
            if (holder == 0) {
                writer.writeVarInt(0);
                translateSoundHolder773To772(reader, writer);
                writer.writeAnonymousNbt(reader.readAnonymousNbt().tag()); // description
                writer.writeFloat(reader.readFloat()); // lengthInSeconds
                writer.writeVarInt(reader.readVarInt()); // comparatorOutput
            } else {
                int mapped = RegistryRemapper.mapJukeboxSong773To772(holder - 1);
                if (mapped == RegistryRemapper.DROP) {
                    // lava_chicken は772に無い。曲を落とす表現が無いため "11"（0）で代用する。
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
"translateTool772To773(Reader reader, Writer writer)": """    /** tool を772→773向けに変換する。ルールの中のブロックIDだけ付け替え、末尾の canDestroyBlocksInCreative は770・771・772・773ともあるため写す。 */
    private static void translateTool772To773(Reader reader, Writer writer) {
        int ruleCount = reader.readVarInt();
        writer.writeVarInt(ruleCount);
        for (int i = 0; i < ruleCount; i++) {
            translateIdSetBlocks772To773(reader, writer);
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
"translateTool773To772(Reader reader, Writer writer)": """    /** tool を773→772向けに変換する。ルールの中のブロックIDだけ付け替え、末尾の canDestroyBlocksInCreative は写す。 */
    private static void translateTool773To772(Reader reader, Writer writer) {
        int ruleCount = reader.readVarInt();
        writer.writeVarInt(ruleCount);
        for (int i = 0; i < ruleCount; i++) {
            translateIdSetBlocks773To772(reader, writer);
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
"translateItemBlockPredicates772To773(Reader reader, Writer writer)": """    /** can_place_on／can_break を772→773向けに変換する。述語の中のブロックIDだけ付け替える。末尾の showTooltip は770・771・772・773とも無い。 */
    private static void translateItemBlockPredicates772To773(Reader reader, Writer writer) {
        int count = reader.readVarInt();
        writer.writeVarInt(count);
        for (int i = 0; i < count; i++) {
            translateItemBlockPredicate772To773(reader, writer);
        }
    }
""",
"translateItemBlockPredicates773To772(Reader reader, Writer writer)": """    /** can_place_on／can_break を773→772向けに変換する。述語の中のブロックIDだけ付け替える。 */
    private static void translateItemBlockPredicates773To772(Reader reader, Writer writer) {
        int count = reader.readVarInt();
        writer.writeVarInt(count);
        for (int i = 0; i < count; i++) {
            translateItemBlockPredicate773To772(reader, writer);
        }
    }
""",
"translateItemBlockPredicate772To773(Reader reader, Writer writer)": """    /** ItemBlockPredicate 1個を772→773向けに変換する。述語の中のブロックIDだけ付け替え、末尾の DataComponentMatchers も変換する（770・771・772・773ともある）。 */
    private static void translateItemBlockPredicate772To773(Reader reader, Writer writer) {
        if (reader.readBoolean()) {
            writer.writeBoolean(true);
            translateIdSetBlocks772To773(reader, writer);
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
        translateDataComponentMatchers772To773(reader, writer);
    }
""",
"translateItemBlockPredicate773To772(Reader reader, Writer writer)": """    /** ItemBlockPredicate 1個を773→772向けに変換する。述語の中のブロックIDだけ付け替え、末尾の DataComponentMatchers も変換する。 */
    private static void translateItemBlockPredicate773To772(Reader reader, Writer writer) {
        if (reader.readBoolean()) {
            writer.writeBoolean(true);
            translateIdSetBlocks773To772(reader, writer);
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
        translateDataComponentMatchers773To772(reader, writer);
    }
""",
"translateParticle773To772(Reader reader, Writer writer)": """    /**
     * パーティクル1個（ID VarInt＋データ）を773→772向けに変換する。
     * パーティクルは770・771・772・773とも同一（tinted_leaves・firefly も両方に
     * ある）ため常に変換できる（{@code true} を返す）。
     */
    private static boolean translateParticle773To772(Reader reader, Writer writer) {
        int particleId = reader.readVarInt();
        writer.writeVarInt(RegistryRemapper.mapParticleUp773(particleId));
        translateParticleData773To772(reader, writer, particleId);
        return true;
    }
""",
"translateWorldParticles773To772(byte[] payload)": """    /**
     * World Particles（773 → 772）。alwaysShow は770・771・772・773ともにあるため
     * そのまま通す。粒子は770・771・772・773で同一のため落とすことは無い。
     */
    public static byte[] translateWorldParticles773To772(byte[] payload) {
        try {
            Reader reader = new Reader(payload);
            boolean longDistance = reader.readBoolean();
            boolean alwaysShow = reader.readBoolean(); // alwaysShow（770・771・772・773ともにある）
            double x = reader.readDouble();
            double y = reader.readDouble();
            double z = reader.readDouble();
            float offsetX = reader.readFloat();
            float offsetY = reader.readFloat();
            float offsetZ = reader.readFloat();
            float speed = reader.readFloat();
            int count = reader.readInt();
            Writer particleWriter = new Writer();
            translateParticle773To772(reader, particleWriter);
            requireEnd(reader);
            Writer writer = new Writer();
            writer.writeBoolean(longDistance);
            writer.writeBoolean(alwaysShow); // alwaysShow（772にもある。そのまま通す）
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
"translateMetadataValue772To773(Reader reader, Writer writer, int type)": """    /**
     * メタデータの値1個を772→773向けに変換する。落とすエントリは {@code false} を返す。
     * 型番号は772側の番号で dispatch する（770・771・772・773で型番号は同一）。
     * 23（ウシ）・24（狼）・25（狼の音）・26（カエル）・27（ブタ）・28（ニワトリ）は
     * 770・771・772・773とも varint。30（絵画）は Holder。
     */
    private static boolean translateMetadataValue772To773(Reader reader, Writer writer, int type) {
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
            case 7 -> translateSlot772To773(reader, writer);
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
            case 14, 15 -> writer.writeVarInt(RegistryRemapper.mapBlockState772To773(reader.readVarInt()));
            case 16 -> writeOptionalAnonymousNbt(writer, reader.readAnonymousNbt().tag());
            case 17 -> translateParticle772To773(reader, writer);
            case 18 -> {
                int count = reader.readVarInt();
                writer.writeVarInt(count);
                for (int i = 0; i < count; i++) {
                    translateParticle772To773(reader, writer);
                }
            }
            case 19 -> {
                writer.writeVarInt(reader.readVarInt());
                writer.writeVarInt(reader.readVarInt());
                writer.writeVarInt(reader.readVarInt());
            }
            case 20, 21, 22 -> writer.writeVarInt(reader.readVarInt());
            case 23, 25, 26, 27, 28 -> writer.writeVarInt(reader.readVarInt()); // cow／wolf_sound／frog／pig／chicken
            case 24 -> translateWolfVariantHolder772To773(reader, writer);
            case 29 -> {
                boolean present = reader.readBoolean();
                writer.writeBoolean(present);
                if (present) {
                    writer.writeString(reader.readString());
                    writer.writePositionRaw(reader.readPositionRaw());
                }
            }
            case 30 -> {
                return translatePaintingVariantHolder772To773(reader, writer);
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
"translateMetadataValue773To772(Reader reader, Writer writer, int type)": """    /**
     * メタデータの値1個を773→772向けに変換する。落とすエントリは {@code false} を返す。
     * 型番号は773側の番号で dispatch する（770・771・772・773で型番号は同一）。
     * 772に無い型は無いため落とすことは無い。
     */
    private static boolean translateMetadataValue773To772(Reader reader, Writer writer, int type) {
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
            case 7 -> translateSlot773To772(reader, writer);
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
            case 14, 15 -> writer.writeVarInt(RegistryRemapper.mapBlockStateUp773(reader.readVarInt()));
            case 16 -> writeOptionalAnonymousNbt(writer, reader.readAnonymousNbt().tag());
            case 17 -> {
                return translateParticle773To772(reader, writer);
            }
            case 18 -> {
                int count = reader.readVarInt();
                writer.writeVarInt(count);
                for (int i = 0; i < count; i++) {
                    translateParticle773To772(reader, writer);
                }
            }
            case 19 -> {
                writer.writeVarInt(reader.readVarInt());
                writer.writeVarInt(reader.readVarInt());
                writer.writeVarInt(reader.readVarInt());
            }
            case 20, 21, 22 -> writer.writeVarInt(reader.readVarInt());
            case 23, 25, 26, 27, 28 -> writer.writeVarInt(reader.readVarInt()); // cow／wolf_sound／frog／pig／chicken
            case 24 -> translateWolfVariantHolder773To772(reader, writer);
            case 29 -> {
                boolean present = reader.readBoolean();
                writer.writeBoolean(present);
                if (present) {
                    writer.writeString(reader.readString());
                    writer.writePositionRaw(reader.readPositionRaw());
                }
            }
            case 30 -> {
                return translatePaintingVariantHolder773To772(reader, writer);
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
"translateWolfVariantHolder772To773(Reader reader, Writer writer)": """    /** 狼バリアントの Holder を772→773向けに変換する。770・771・772・773ともただの varint（9種・同じ並び）のためそのまま写す。 */
    private static boolean translateWolfVariantHolder772To773(Reader reader, Writer writer) {
        writer.writeVarInt(reader.readVarInt());
        return true;
    }
""",
"translateWolfVariantHolder773To772(Reader reader, Writer writer)": """    /** 狼バリアントの Holder を773→772向けに変換する。770・771・772・773ともただの varint のためそのまま写す。 */
    private static boolean translateWolfVariantHolder773To772(Reader reader, Writer writer) {
        writer.writeVarInt(reader.readVarInt());
        return true;
    }
""",
"mapMetadataType772To773(int type772)": """    /** メタデータの型番号を772→773に付け替える。770・771・772・773で型番号は同一。 */
    private static int mapMetadataType772To773(int type772) {
        return type772;
    }
""",
"mapMetadataType773To772(int type773)": """    /** メタデータの型番号を773→772に付け替える。770・771・772・773で型番号は同一。 */
    private static int mapMetadataType773To772(int type773) {
        return type773;
    }
""",
"translateChunkData772To773(byte[] payload)": """    /** チャンクデータを772→773向けに変換する。heightmap は770・771・772・773とも直接の配列（varint 件数＋（type、長さ、long列））のためそのまま写す。セクションのブロック状態・バイオームとブロックエンティティ種別を付け替える。 */
    public static byte[] translateChunkData772To773(byte[] payload) {
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
            byte[] translatedSections = translateChunkSections772To773(chunkData);
            int blockEntityCount = reader.readVarInt();
            List<BlockEntityEntry> kept = new ArrayList<>();
            for (int i = 0; i < blockEntityCount; i++) {
                int packedXZ = reader.readUnsignedByte();
                int y = reader.readShort();
                int type = reader.readVarInt();
                Tag nbt = readOptionalAnonymousNbt(reader);
                int mapped = RegistryRemapper.mapBlockEntityType772To773(type);
                if (mapped == RegistryRemapper.DROP) {
                    LOG.warning("773に無いブロックエンティティ種別（772 type=" + type
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
"translateChunkData773To772(byte[] payload)": """    /** チャンクデータを773→772向けに変換する。heightmap は770・771・772・773とも直接の配列のためそのまま写す。 */
    public static byte[] translateChunkData773To772(byte[] payload) {
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
            byte[] translatedSections = translateChunkSections773To772(chunkData);
            int blockEntityCount = reader.readVarInt();
            List<BlockEntityEntry> kept = new ArrayList<>();
            for (int i = 0; i < blockEntityCount; i++) {
                int packedXZ = reader.readUnsignedByte();
                int y = reader.readShort();
                int type = reader.readVarInt();
                Tag nbt = readOptionalAnonymousNbt(reader);
                int mapped = RegistryRemapper.mapBlockEntityTypeUp773(type);
                if (mapped == RegistryRemapper.DROP) {
                    LOG.warning("772に無いブロックエンティティ種別（773 type=" + type
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
"translatePalettedContainer772To773(Reader reader, Writer writer, boolean blocks)": """        /**
         * パレット付きコンテナ1個を772→773向けに変換する。ブロック状態と
         * バイオームの両方を付け替える。770・771・772・773とも Data Array に
         * 長さ varint 接頭辞は無い。
         */
        private static void translatePalettedContainer772To773(Reader reader, Writer writer, boolean blocks) {
        int bitsPerEntry = reader.readUnsignedByte();
        if (bitsPerEntry > 32) {
            throw new IllegalArgumentException("Bits Per Entry が大きすぎます: " + bitsPerEntry);
        }
        writer.writeUnsignedByte(bitsPerEntry);
        int entryCount = blocks ? 4096 : 64;
        if (bitsPerEntry == 0) {
            int value = reader.readVarInt();
            writer.writeVarInt(blocks
                    ? RegistryRemapper.mapBlockState772To773(value)
                    : mapBiome772To773(value));
            // 770・771・772・773とも長さ接頭辞は無い。
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
                        ? RegistryRemapper.mapBlockState772To773(value)
                        : mapBiome772To773(value));
            }
            // 間接パレットの Data Array はインデックス列なので中身は変えない。
            // 770・771・772・773とも長さ接頭辞は無い。長さは bpe から計算する。
            int perLong = 64 / bitsPerEntry;
            int expected = (entryCount + perLong - 1) / perLong;
            for (int i = 0; i < expected; i++) {
                writer.writeLong(reader.readLong());
            }
            return;
        }
        // 直接パレット：Data Array の中身がレジストリ番号そのもの。
        // 770・771・772・773とも長さ接頭辞は無い。長さは bpe から計算する。
        int perLong = 64 / bitsPerEntry;
        int expected = (entryCount + perLong - 1) / perLong;
        long[] longs = new long[expected];
        for (int i = 0; i < expected; i++) {
            longs[i] = reader.readLong();
        }
        remapDirectEntries772To773(longs, bitsPerEntry, entryCount, blocks);
        for (long value : longs) {
            writer.writeLong(value);
        }
    }
""",
"translatePalettedContainer773To772(Reader reader, Writer writer, boolean blocks)": """        /** パレット付きコンテナ1個を773→772向けに変換する。ブロック状態とバイオームの両方を付け替える。770・771・772・773とも Data Array に長さ varint 接頭辞は無い。 */
        private static void translatePalettedContainer773To772(Reader reader, Writer writer, boolean blocks) {
        int bitsPerEntry = reader.readUnsignedByte();
        if (bitsPerEntry > 32) {
            throw new IllegalArgumentException("Bits Per Entry が大きすぎます: " + bitsPerEntry);
        }
        writer.writeUnsignedByte(bitsPerEntry);
        int entryCount = blocks ? 4096 : 64;
        if (bitsPerEntry == 0) {
            int value = reader.readVarInt();
            writer.writeVarInt(blocks
                    ? RegistryRemapper.mapBlockStateUp773(value)
                    : mapBiomeUp773(value));
            // 770・771・772・773とも長さ接頭辞は無い。
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
                        ? RegistryRemapper.mapBlockStateUp773(value)
                        : mapBiomeUp773(value));
            }
            // 間接パレットの Data Array はインデックス列なので中身は変えない。
            // 770・771・772・773とも長さ接頭辞は無い。長さは bpe から計算する。
            int perLong = 64 / bitsPerEntry;
            int expected = (entryCount + perLong - 1) / perLong;
            for (int i = 0; i < expected; i++) {
                writer.writeLong(reader.readLong());
            }
            return;
        }
        // 直接パレット：Data Array の中身がレジストリ番号そのもの。
        // 770・771・772・773とも長さ接頭辞は無い。長さは bpe から計算する。
        int perLong = 64 / bitsPerEntry;
        int expected = (entryCount + perLong - 1) / perLong;
        long[] longs = new long[expected];
        for (int i = 0; i < expected; i++) {
            longs[i] = reader.readLong();
        }
        remapDirectEntries773To772(longs, bitsPerEntry, entryCount, blocks);
        for (long value : longs) {
            writer.writeLong(value);
        }
    }
""",
"translateExplosion772To773(byte[] payload)": """    /** Explosion（772 → 773）。knockback は770・771・772・773とも f64×3。粒子・音を付け替える。 */
    public static byte[] translateExplosion772To773(byte[] payload) {
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
            translateParticle772To773(reader, writer);
            translateSoundHolder772To773(reader, writer);
            requireEnd(reader);
            return writer.bytes();
        } catch (IllegalArgumentException e) {
            LOG.warning("Explosion を変換できなかったため素通しします: " + e.getMessage());
            return payload;
        }
    }
""",
"translateExplosion773To772(byte[] payload)": """    /** Explosion（773 → 772）。knockback は770・771・772・773とも f64×3。粒子・音を付け替える。 */
    public static byte[] translateExplosion773To772(byte[] payload) {
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
            translateParticle773To772(reader, particleWriter);
            writer.writeBytes(particleWriter.bytes());
            translateSoundHolder773To772(reader, writer);
            requireEnd(reader);
            return writer.bytes();
        } catch (IllegalArgumentException e) {
            LOG.warning("Explosion を変換できなかったため素通しします: " + e.getMessage());
            return payload;
        }
    }
""",
"translateSetCreativeSlot772To773(byte[] payload)": """    /** Set Creative Slot（772 → 773）。770・771・772・773とも UntrustedSlot（成分は ByteArray の生バイト）。成分の中身を772→773に変換する。 */
    public static byte[] translateSetCreativeSlot772To773(byte[] payload) {
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
            writer.writeVarInt(RegistryRemapper.mapItem772To773(reader.readVarInt()));
            int added = reader.readVarInt();
            int removed = reader.readVarInt();
            Writer addedOut = new Writer();
            int keptAdded = 0;
            for (int i = 0; i < added; i++) {
                int kind772 = reader.readVarInt();
                byte[] data = reader.readByteArray();
                Writer component = new Writer();
                if (!translateComponent772To773(new Reader(data), component, kind772)) {
                    continue;
                }
                addedOut.writeVarInt(kind772);
                addedOut.writeByteArray(component.bytes());
                keptAdded++;
            }
            Writer removedOut = new Writer();
            int keptRemoved = 0;
            for (int i = 0; i < removed; i++) {
                int kind773 = mapComponentKind772To773(reader.readVarInt());
                if (kind773 < 0) {
                    continue;
                }
                removedOut.writeVarInt(kind773);
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
"translateSetCreativeSlot773To772(byte[] payload)": """    /** Set Creative Slot（773 → 772）。770・771・772・773とも UntrustedSlot。成分の中身を773→772に変換する。 */
    public static byte[] translateSetCreativeSlot773To772(byte[] payload) {
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
            writer.writeVarInt(RegistryRemapper.mapItemUp773(reader.readVarInt()));
            int added = reader.readVarInt();
            int removed = reader.readVarInt();
            Writer addedOut = new Writer();
            int keptAdded = 0;
            for (int i = 0; i < added; i++) {
                int kind773 = reader.readVarInt();
                byte[] data = reader.readByteArray();
                Writer component = new Writer();
                if (!translateComponent773To772(new Reader(data), component, kind773)) {
                    continue;
                }
                addedOut.writeVarInt(mapComponentKind773To772(kind773));
                addedOut.writeByteArray(component.bytes());
                keptAdded++;
            }
            Writer removedOut = new Writer();
            int keptRemoved = 0;
            for (int i = 0; i < removed; i++) {
                int kind772 = mapComponentKind773To772(reader.readVarInt());
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
})

# ----------------------------------------------------------------------
# PlayTranslator 773 節（手書き）
# ----------------------------------------------------------------------

PLAY_773_SECTION = """
    // 772 ⇔ 773（1.21.9／1.21.10）
    // Join Game／Respawn／Synchronize Player Position／Login Success／
    // Client Information／Player Info／Set Held Item／Vehicle Move／
    // Player Chat は772と773で形式が同一のため変換器は要らない（素通し）。
    // 変わるのは Chat Message／Chat Command Signed（末尾の checksum u8 は
    // 770・771・772・773ともある。番号は 772→773 で変わらない。checksum の中身は
    // 772と同じ扱い）、Registry Data（レジストリの順序。1.21.9での変更は
    // ブロック状態・アイテム・音・パーティクル・エンティティの追加など。
    // jukebox_song・絵画・バイオーム・エンチャント・属性は同一。
    // minecraft:dialog は771からあるため通す）だけである。
    // ------------------------------------------------------------------

    /** Player Info（772 → 773）。形式は同一のため素通しする。 */
    public static byte[] translatePlayerInfo772To773(byte[] payload) {
        return payload;
    }

    /** Player Info（773 → 772）。形式は同一のため素通しする。 */
    public static byte[] translatePlayerInfo773To772(byte[] payload) {
        return payload;
    }

    /** Set Held Item（772 → 773）。形式は同一のため素通しする（varint のまま）。 */
    public static byte[] translateSetHeldItem772To773(byte[] payload) {
        return payload;
    }

    /** Set Held Item（773 → 772）。形式は同一のため素通しする。 */
    public static byte[] translateSetHeldItem773To772(byte[] payload) {
        return payload;
    }

    /** Vehicle Move（772 → 773）。形式は同一のため素通しする（末尾の onGround 付き）。 */
    public static byte[] translateVehicleMove772To773(byte[] payload) {
        return payload;
    }

    /** Vehicle Move（773 → 772）。形式は同一のため素通しする。 */
    public static byte[] translateVehicleMove773To772(byte[] payload) {
        return payload;
    }

    /** Player Chat（772 → 773）。先頭の globalIndex は770・771・772・773ともあるため素通しする。 */
    public static byte[] translatePlayerChat772To773(byte[] payload) {
        return payload;
    }

    /** Player Chat（773 → 772）。先頭の globalIndex は770・771・772・773ともあるため素通しする。 */
    public static byte[] translatePlayerChat773To772(byte[] payload) {
        return payload;
    }

    /**
     * Chat Message／Chat Command Signed（773 → 772）。末尾の checksum
     * （u8 1バイト）を取り除く。ペイロードが空の場合はそのまま返す。
     */
    public static byte[] stripChatChecksum773(byte[] payload) {
        if (payload.length == 0) {
            return payload;
        }
        return new Reader(payload).readBytes(payload.length - 1);
    }

    /**
     * Chat Message／Chat Command Signed（772 → 773）。末尾に checksum
     * （u8 1バイト）を追加する。checksum の式は未確認のため 0x00 を入れる
     * （実機での確認は未実施。計算式そのものの確認も未実施）。
     */
    public static byte[] appendChatChecksum773(byte[] payload) {
        Writer writer = new Writer();
        writer.writeBytes(payload);
        writer.writeByte(0);
        return writer.bytes();
    }

    /**
     * 772の Registry Data を773形式にする。minecraft:dialog は772から
     * あるためそのまま通す。{@code minecraft:jukebox_song} は lava_chicken
     * の挿入（9）で9番以降が+1ずれるため並べ替える（reorderRegistries）。
     * それ以外は1レジストリ分のペイロードを返す。
     */
    public static byte[] translateRegistryData772To773(byte[] payload) {
        Map<String, Tag.CompoundTag> reordered = reorderRegistries(
                parseRegistryData(payload),
                ProtocolVersion.V1_21_7, ProtocolVersion.V1_21_9);
        List<byte[]> built = buildRegistryData766(reordered);
        if (built.isEmpty()) {
            throw new IllegalArgumentException("Registry Data が空です。");
        }
        return built.get(0);
    }

    /**
     * 773の Registry Data を772形式にする。minecraft:dialog は772にも
     * あるため通す（reorderRegistries）。jukebox_song は lava_chicken を落として
     * 10番以降を-1する。それ以外は1レジストリ分のペイロードを返す。
     * 空になった場合は {@code null} を返す。
     */
    public static byte[] translateRegistryData773To772(byte[] payload) {
        Map<String, Tag.CompoundTag> reordered = reorderRegistries(
                parseRegistryData(payload),
                ProtocolVersion.V1_21_9, ProtocolVersion.V1_21_7);
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
        CT, "// 771 ⇔ 772（1.21.7／1.21.8）")
    print(f"chunk section: lines {cs+1}-{ce+1} ({len(chunk_sec)} chars)")
    Path("/tmp/chunk772_section.txt").write_text(chunk_sec, encoding="utf-8")
    chunk_shifted = shift(chunk_sec)
    Path("/tmp/chunk773_shifted.txt").write_text(chunk_shifted, encoding="utf-8")
    print(f"chunk shifted: {len(chunk_shifted)} chars")

    # --- ChunkTranslator 773 節 ---
    chunk773 = apply_fixes(chunk_shifted, CHUNK_STRING_FIXES, "chunk-string")
    for sig, new_text in CHUNK_METHOD_REPLACEMENTS.items():
        chunk773 = replace_method(chunk773, sig, new_text, "chunk")
    for sig in DELETE_METHODS:
        chunk773 = delete_method(chunk773, sig, "chunk-delete")
    chunk773 = chunk773.rstrip() + "\n" + EXTRA_METHODS_773
    print(f"chunk773: {len(chunk773)} chars, "
          f"{chunk773.count('translate')} translate occurrences")
    insert_section_before_class_close(
        CT, chunk773, "chunk", "translateSlot772To773")

    # --- PlayTranslator 773 節（手書き） ---
    insert_section_before_class_close(
        PT, PLAY_773_SECTION, "play", "translatePlayerChat772To773")

    # --- reorderRegistries の jukebox_song フィルタ：773 も残す ---
    # （実コードには V1_21_4 の条件もある。shift 版の fix は落とし穴。）
    pt_text = PT.read_text(encoding="utf-8")
    pt_text = fix(pt_text,
                  """            if (registryId.equals("minecraft:jukebox_song")
                    && toProtocol != ProtocolVersion.V1_21
                    && toProtocol != ProtocolVersion.V1_21_2
                    && toProtocol != ProtocolVersion.V1_21_4
                    && toProtocol != ProtocolVersion.V1_21_5
                    && toProtocol != ProtocolVersion.V1_21_6
                    && toProtocol != ProtocolVersion.V1_21_7) {""",
                  """            if (registryId.equals("minecraft:jukebox_song")
                    && toProtocol != ProtocolVersion.V1_21
                    && toProtocol != ProtocolVersion.V1_21_2
                    && toProtocol != ProtocolVersion.V1_21_4
                    && toProtocol != ProtocolVersion.V1_21_5
                    && toProtocol != ProtocolVersion.V1_21_6
                    && toProtocol != ProtocolVersion.V1_21_7
                    && toProtocol != ProtocolVersion.V1_21_9) {""",
                  "play-jukebox")
    # minecraft:dialog は771・772・773にしか無いため、771・772・773以外向けは落とす。
    # （既存のフィルタを拡張する。別フィルタを追加すると二重に落とす。）
    pt_text = fix(pt_text,
                  """            if (registryId.equals("minecraft:dialog")
                    && toProtocol != ProtocolVersion.V1_21_6
                    && toProtocol != ProtocolVersion.V1_21_7) {""",
                  """            if (registryId.equals("minecraft:dialog")
                    && toProtocol != ProtocolVersion.V1_21_6
                    && toProtocol != ProtocolVersion.V1_21_7
                    && toProtocol != ProtocolVersion.V1_21_9) {""",
                  "play-dialog")
    # jukebox_song の並べ替え：772↔773 の対応表を追加する。
    pt_text = fix(pt_text,
                  """            case "minecraft:jukebox_song" -> switch (from * 1000 + to) {
                case 770771 -> RegistryRemapper::mapJukeboxSong770To771;
                case 771770 -> RegistryRemapper::mapJukeboxSong771To770;
                case 771772 -> RegistryRemapper::mapJukeboxSong771To772;
                case 772771 -> RegistryRemapper::mapJukeboxSong772To771;
                default -> null;
            };""",
                  """            case "minecraft:jukebox_song" -> switch (from * 1000 + to) {
                case 770771 -> RegistryRemapper::mapJukeboxSong770To771;
                case 771770 -> RegistryRemapper::mapJukeboxSong771To770;
                case 771772 -> RegistryRemapper::mapJukeboxSong771To772;
                case 772771 -> RegistryRemapper::mapJukeboxSong772To771;
                case 772773 -> RegistryRemapper::mapJukeboxSong772To773;
                case 773772 -> RegistryRemapper::mapJukeboxSong773To772;
                default -> null;
            };""",
                  "play-jukebox-mapper")
    PT.write_text(pt_text, encoding="utf-8")
    print("[play-registry] OK")
    print("完了。次は :detergentmc-protocol:compileJava で確認すること。")


if __name__ == "__main__":
    main()
