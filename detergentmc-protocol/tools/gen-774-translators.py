#!/usr/bin/env python3
"""ChunkTranslator / PlayTranslator の 773 節から 774 節を生成する。

方針: 773 節を抜き出して shift()（gen-774-proxysession.py のもの）で
1段ずらし、FIXES（手書きの置き換え）を当て、ファイル末尾の最終 `}` の
直前に挿入する。PlayTranslator の 774 節は手書きブロックで作る
（Player Info 等は773と774で同一形式のため素通しにする）。

773→774 で shift だけでは足りない点（FIXES で直す）:
- 成分種別は773が96件、774が104件。8件の新規挿入（use_effects 5、
  minimum_attack_charge 7、damage_type 8、attack_range 30、piercing_weapon 38、
  kinetic_weapon 39、swing_animation 40、zombie_nautilus/variant 94）のため
  KIND 表は恒等ではなく 773→774 の写像（新規 8 件は -1）になる。
- 772→773 で形式が変わった成分（unbreakable の bool→void、
  dyed_color の showTooltip 削除、enchantments／attribute_modifiers／
  trim／tool／item_block_predicates の末尾 bool の削除・追加、
  instrument のコンテナ化、jukebox_playable の showInTooltip 削除、
  hide_tooltip→tooltip_display 統合）は770・771・772・773・774で同形式のため、
  shift したヘルパーは誤って769形式を読み書きする。手書きで直す。
- チャンクの heightmap・paletted container は770・771・772・773・774とも直接配列
  （長さ varint 接頭辞なし）のため、769形式との変換をする
  shift 版は使えない。手書きで直す。
- パーティクルは773・774で同一。形式は同一。メタデータの型番号は
  773→774でずれる（zombie_nautilus_variant を28に挿入し、humanoid_arm を
  38に追加。772→773でも compound_tag の削除で17番以降がずれているため、
  メタデータの translate／map は773・774の番号で書き直す）。
- エンチャントは lunge（774=21）の挿入で21番以降が+1、ダメージ種別は
  spear（774=37）の挿入で37番以降が+1ずれる。Registry Data の並べ替え
  （reorderRegistries）に 773↔774 の写像と名前表を追加する。
- Explosion の knockback は770・771・772・773・774とも f64×3。
- jukebox_playable の曲IDは772・773・774で同一（lava_chicken は773で挿入済み）。
- Set Creative Slot は770・771・772・773・774とも UntrustedSlot。
- Spawn Entity：happy_ghast は774=58（773では57）、falling_block は
  774=51（773では50）。対応表で付け替える。
"""
import importlib.util
import re
import sys
from pathlib import Path

_HERE = Path(__file__).resolve().parent
_spec = importlib.util.spec_from_file_location("gen774ps", _HERE / "gen-774-proxysession.py")
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
    signature はメソッド名を含む一意の文字列（例: "translateSlot769To773(Reader"）。"""
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


# 成分種別の対応表（773 kind -> 774 kind）。
# 774 の新規成分種別（773 には無い。minecraft-data mc-1.21.11-protocol.json の
# SlotComponentType より）：5 use_effects、7 minimum_attack_charge、
# 8 damage_type、30 attack_range、38 piercing_weapon、39 kinetic_weapon、
# 40 swing_animation、94 zombie_nautilus/variant。773 の 96 件はすべて 774 にある。
_NEW_KIND_774 = {5, 7, 8, 30, 38, 39, 40, 94}
KIND_773_TO_774 = {}
_nxt = 0
for _k773 in range(96):
    while _nxt in _NEW_KIND_774:
        _nxt += 1
    KIND_773_TO_774[_k773] = _nxt
    _nxt += 1
KIND_774_TO_773 = {v: k for k, v in KIND_773_TO_774.items()}
for _n in _NEW_KIND_774:
    KIND_774_TO_773[_n] = -1  # 774 の新規種別は 773 では落とす
assert len(KIND_773_TO_774) == 96
assert len(KIND_774_TO_773) == 104
assert KIND_773_TO_774[0] == 0 and KIND_773_TO_774[5] == 6
assert KIND_773_TO_774[6] == 9 and KIND_773_TO_774[95] == 103
assert KIND_774_TO_773[5] == -1 and KIND_774_TO_773[94] == -1
assert KIND_774_TO_773[6] == 5 and KIND_774_TO_773[103] == 95


def java_int_array(name, values, per_line=16):
    lines = []
    for i in range(0, len(values), per_line):
        lines.append("            " + ", ".join(str(v) for v in values[i:i + per_line]) + ",")
    body = "\n".join(lines)
    return f"    private static final int[] {name} = {{\n{body}\n    }};\n"


KIND_773_TO_774_JAVA = java_int_array(
    "KIND_773_TO_774", [KIND_773_TO_774[i] for i in range(96)])
KIND_774_TO_773_JAVA = java_int_array(
    "KIND_774_TO_773",
    [KIND_774_TO_773.get(i, -1) for i in range(104)])

# 773節の KIND 表（shift 後の old として使う。773 節の KIND 表は恒等 96）。
KIND_772_TO_773 = {i: i for i in range(96)}
KIND_773_TO_772 = {i: i for i in range(96)}
# （以下、shift 版の誤った 769→773 表は削除。773 節の KIND 表は恒等のため
#  上の 2 行が old になる。KIND_769_TO_773 の定義は使わない。）
_KIND_769_TO_773_UNUSED = {
    **{i: i for i in range(0, 15)},
    15: -1, 16: 15,
    17: 16, 18: 17, 19: 18, 20: 19, 21: 20, 22: 21, 23: 22, 24: 23, 25: 24, 26: 25,
    27: 27, 28: 28, 29: 29, 30: 30, 31: 31, 32: 32,
    33: 34, 34: 35, 35: 36, 36: 37, 37: 38, 38: 39, 39: 40, 40: 41, 41: 42,
    42: 44, 43: 45, 44: 46, 45: 47, 46: 48, 47: 49, 48: 50, 49: 51, 50: 52,
    51: 54, 52: 55, 53: 57, 54: 58, 55: 59, 56: 60, 57: 61, 58: 62, 59: 63,
    60: 64, 61: 65, 62: 66, 63: 67, 64: 68, 65: 69, 66: 70,
}
assert len(KIND_772_TO_773) == 96
assert len(KIND_773_TO_772) == 96

# shift 後の 774 節に残る（誤った）KIND 表のテキスト。置き換えの old に使う。
# （773 節の恒等表を shift したもの＝恒等 96。これを実写像に置き換える。
#  KIND_773_TO_774_JAVA／KIND_774_TO_773_JAVA は CHUNK_STRING_FIXES の
#  new 側で使う。KIND_JAVA という名前の表は存在しない。）
KIND_773_TO_774_SHIFTED_JAVA = java_int_array(
    "KIND_773_TO_774", [KIND_772_TO_773[i] for i in range(96)])
KIND_774_TO_773_SHIFTED_JAVA = java_int_array(
    "KIND_774_TO_773",
    [KIND_773_TO_772.get(i, -1) for i in range(96)])



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


# 773節のヘッダーコメント（shift 後の old を shift() で作る）。
HEADER_773 = """    // 769 ⇔ 773（1.21.9）
    // 成分種別は769が67件、773が96件。番号が大きくずれるため KIND 表
    // （KIND_769_TO_773／KIND_773_TO_769）で付け替える。
    // 変わるのは中身のレジストリID（アイテム・ブロック状態・
    // エンティティ・音・パーティクル）と成分の形式（tooltip_display への
    // 統合、showTooltip／showInTooltip の削除、tool の
    // canDestroyBlocksInCreative 追加等）。バイオーム・エンチャント・属性・
    // メニュー・ダメージ種別は同一のため付け替えない。エンティティは
    // potion（769=99）が splash_potion（773=99）と lingering_potion
    // （773=100）に分かれる。tinted_leaves（773=35）と firefly
    // （773=113）は769に無いため773→772では落とす。
    // チャンクの heightmap は NBT から直接の配列に変わった。"""

HEADER_774 = """    // 773 ⇔ 774（1.21.11）
    // 成分種別は773が96件、774が104件。8件の新規挿入（use_effects 5、
    // minimum_attack_charge 7、damage_type 8、attack_range 30、
    // piercing_weapon 38、kinetic_weapon 39、swing_animation 40、
    // zombie_nautilus/variant 94）のため KIND 表で付け替える。
    // 変わるのは中身のレジストリID（アイテム・エンティティ・音・
    // エンチャント・ダメージ種別）と成分の中身（break_sound／
    // blocks_attacks の音ID）。ブロック状態・バイオーム・属性・メニュー・
    // パーティクル・ブロックエンティティ種別・絵画・ジュークボックスは同一。
    // エンティティは camel_husk（774=20）・nautilus（774=88）・parched
    // （774=97）・zombie_nautilus（774=152）の挿入でずれる（happy_ghast は
    // 774=58。773では57。falling_block は774=51。773では50）。
    // パーティクルは773・774で同一（trail 49／block_crumble 113）。
    // チャンクの heightmap は770・771・772・773・774とも直接の配列（長さ varint 無し）。"""
# （HEADER_774 は CHUNK_STRING_FIXES のヘッダー置き換えと内容を合わせるための
#  参考定義。実際の置き換えは CHUNK_STRING_FIXES のリテラルで行う。）

# 773節の KIND 表まわり（shift 後の old を shift() で作る）。
KIND_COUNT_773 = """    /** 成分種別の件数（773 は 96 件。769 は 67 件）。 */
    private static final int DATA_COMPONENT_TYPE_COUNT_773 = 96;"""

KIND_COUNT_774 = """    /** 成分種別の件数（773 は 96 件、774 は 104 件）。 */
    private static final int DATA_COMPONENT_TYPE_COUNT_774 = 104;"""

KIND_JAVADOC_1_773 = """    /**
     * 成分種別の対応表（769 kind → 773 kind）。-1 は 773 に無いため落とす
     * （15＝hide_additional_tooltip）。16（hide_tooltip）は 15
     * （tooltip_display）になる。
     */"""

KIND_JAVADOC_1_774 = """    /**
     * 成分種別の対応表（773 kind → 774 kind）。774 の新規 8 種
     * （use_effects 5、minimum_attack_charge 7、damage_type 8、attack_range 30、
     * piercing_weapon 38、kinetic_weapon 39、swing_animation 40、
     * zombie_nautilus/variant 94）の挿入で 773 の 96 件はすべてずれる。
     */"""

KIND_JAVADOC_2_773 = """    /**
     * 成分種別の対応表（773 kind → 769 kind）。-1 は 769 に無いため落とす。
     * 15（tooltip_display）は内容次第で hide_tooltip（16）か
     * hide_additional_tooltip（15）になるためここでは -1 にしてある。
     */"""

KIND_JAVADOC_2_774 = """    /**
     * 成分種別の対応表（774 kind → 773 kind）。774 の新規 8 種は
     * -1（落とす）。773 の 96 件はすべて 774 にある。
     */"""


# ----------------------------------------------------------------------
# ChunkTranslator 774 節の文字列 FIXES（シフト済みテキストに適用）
# ----------------------------------------------------------------------

CHUNK_STRING_FIXES = [
    # ヘッダーコメント（shift 版は 772→773 の説明を版番号だけずらしたもの。
    # vv-1.21.9.json／vv-1.21.11.json と mc-1.21.11-protocol.json で確認した
    # 773→774 の事実に直す。パーティクル定数（49／113／35／36／114）は
    # shift 版のまま正しいため、定数の置き換えは不要）。
    ("""    // 成分種別は770・771・772・773・774とも96件・同じ番号のため KIND 表は恒等表。
    // 変わるのは中身のレジストリID（アイテム・ブロック状態・
    // エンティティ・音・パーティクル）と成分の中身（break_sound／
    // blocks_attacks の音ID）。バイオーム・エンチャント・属性・メニュー・
    // ダメージ種別は同一のため付け替えない。エンティティは
    // happy_ghast（774=57。773では56）。パーティクルは copper_fire_flame
    // （774=5）の挿入で5番以降が1つずれるため対応表に通す。
    // tinted_leaves（774=36）と firefly（774=114）は770・771・772・773・774ともある。""",
     """    // 成分種別は773が96件、774が104件。8件の新規挿入（use_effects 5、
    // minimum_attack_charge 7、damage_type 8、attack_range 30、
    // piercing_weapon 38、kinetic_weapon 39、swing_animation 40、
    // zombie_nautilus/variant 94）のため KIND 表で付け替える。
    // 変わるのは中身のレジストリID（アイテム・エンティティ・音・
    // エンチャント・ダメージ種別）と成分の中身（break_sound／
    // blocks_attacks の音ID）。ブロック状態・バイオーム・属性・メニュー・
    // パーティクル・ブロックエンティティ種別・絵画・ジュークボックスは同一。
    // エンティティは camel_husk（774=20）・nautilus（774=88）・parched
    // （774=97）・zombie_nautilus（774=152）の挿入でずれる（happy_ghast は
    // 774=58。773では57。falling_block は774=51。773では50）。
    // パーティクルは773・774で同一（copper_fire_flame は773・774とも5。
    // trail 49／block_crumble 113／tinted_leaves 36／firefly 114）。"""),
    # 成分種別の件数：773 は 96 件、774 は 104 件（shift 版は 96 のまま）。
    ("""    /** 成分種別の件数（770・771・772・773・774とも 96 件）。 */
    private static final int DATA_COMPONENT_TYPE_COUNT_774 = 96;""",
     KIND_COUNT_774),
    # 成分種別の対応表（773→774）の javadoc：恒等ではない。
    ("""    /**
     * 成分種別の対応表（773 kind → 774 kind）。770・771・772・773・774とも96件・
     * 同じ番号のため恒等表（落とす種別は無い）。
     */""",
     KIND_JAVADOC_1_774),
    # 成分種別の対応表（774→773）の javadoc：774 の新規 8 種は落とす。
    ("""    /**
     * 成分種別の対応表（774 kind → 773 kind）。770・771・772・773・774とも96件・
     * 同じ番号のため恒等表（落とす種別は無い）。
     */""",
     KIND_JAVADOC_2_774),
    # 成分種別の対応表（773→774）：恒等 96 ではなく実写像にする。
    (KIND_773_TO_774_SHIFTED_JAVA, KIND_773_TO_774_JAVA),
    # 成分種別の対応表（774→773）：恒等 96 ではなく実写像（104 件）にする。
    (KIND_774_TO_773_SHIFTED_JAVA, KIND_774_TO_773_JAVA),
    # パーティクル節のヘッダー：パーティクルは 773・774 で同一（copper_fire_flame
    # は 773 で挿入済みで 774=5。shift 版の「774 で挿入」は誤り）。
    ("""    // パーティクルは copper_fire_flame（774=5）の挿入で5番以降が1つ
    // ずれるため、ID は対応表に通す。trail（774=49）／block_crumble
    // （774=113）とも duration・ブロック状態ID付き。""",
     """    // パーティクルは773・774で同一のため、ID は対応表に通すだけ
    // （実質そのまま）。trail（773・774とも49）／block_crumble
    // （773・774とも113）とも duration・ブロック状態ID付き。"""),
    # Spawn Entity（773→774）：falling_block は 773=50（shift 版の 49 は
    # 772 の番号）。新規は camel_husk・nautilus・parched・zombie_nautilus。
    ("""     * Spawn Entity（773 → 774）。種別を付け替え、falling_block（773=49）の
     * objectData（ブロック状態ID）も付け替える。エンティティは copper_golem
     * （774=27）と mannequin（774=82）の挿入でずれるため対応表に通す。""",
     """     * Spawn Entity（773 → 774）。種別を付け替え、falling_block（773=50）の
     * objectData（ブロック状態ID）も付け替える。エンティティは camel_husk
     * （774=20）・nautilus（774=88）・parched（774=97）・zombie_nautilus
     * （774=152）の挿入でずれるため対応表に通す。"""),
    ("""            int mappedObjectData = type == 49
                    ? RegistryRemapper.mapBlockState773To774(objectData)
                    : objectData;""",
     """            int mappedObjectData = type == 50
                    ? RegistryRemapper.mapBlockState773To774(objectData)
                    : objectData;"""),
    # Spawn Entity（774→773）：falling_block は 774=51（shift 版の 50 は
    # 773 の番号）。
    ("""     * Spawn Entity（774 → 773）。種別を付け替え、falling_block（774=50）の
     * objectData（ブロック状態ID）も付け替える。""",
     """     * Spawn Entity（774 → 773）。種別を付け替え、falling_block（774=51）の
     * objectData（ブロック状態ID）も付け替える。"""),
    ("""            int mappedObjectData = type == 50
                    ? RegistryRemapper.mapBlockStateUp774(objectData)
                    : objectData;""",
     """            int mappedObjectData = type == 51
                    ? RegistryRemapper.mapBlockStateUp774(objectData)
                    : objectData;"""),
    # ボート variant のコメント：happy_ghast は 774=58、773=57。
    ("""     * 774の種別を773に直してから773の表を使う（happy_ghast は774=57。
     * 773では56。種別は対応表に通す）。""",
     """     * 774の種別を773に直してから773の表を使う（happy_ghast は774=58。
     * 773では57。種別は対応表に通す）。"""),
    # チャンクデータ節の注記：ブロック状態は 773・774 で同一（+1,725 は
    # 772→773 の話）。ずれるのはアイテム・音・エンティティ・エンチャント・
    # ダメージ種別。パーティクル・ブロックエンティティ種別も同一。
    ("""    // 773⇔774ではブロック状態（銅系など+1,725）・アイテム・音・
    // エンティティ・パーティクル・ブロックエンティティ種別がずれる。
    // バイオーム・エンチャント・属性・絵画・ジュークボックスは同一。""",
     """    // 773⇔774ではアイテム・音・エンティティ・エンチャント・
    // ダメージ種別がずれる。ブロック状態・バイオーム・パーティクル・
    // ブロックエンティティ種別・属性・絵画・ジュークボックスは同一。"""),
    # translateChunkData773To774 の注記：ブロック状態・バイオーム・
    # ブロックエンティティ種別は同一のため対応表に通すだけ。
    ("""    /** チャンクデータを773→774向けに変換する。heightmap は770・771・772・773・774とも直接の配列（varint 件数＋（type、長さ、long列））のためそのまま写す。セクションのブロック状態・バイオームとブロックエンティティ種別を付け替える。 */""",
     """    /** チャンクデータを773→774向けに変換する。heightmap は770・771・772・773・774とも直接の配列（varint 件数＋（type、長さ、long列））のためそのまま写す。セクションのブロック状態・バイオーム・ブロックエンティティ種別は同一のため対応表に通すだけ。 */"""),
    # （絵画メタデータ Holder・メタデータ節の注記は shift 版のまま正しいため
    #  置き換えは不要。絵画は772・773・774とも51種・同じ並び。）
    # メタデータ節のヘッダー：型番号は772時代のまま（型 24 は773・774では
    # 狼の音、型 17／18 は773・774では particles／villager_data）。
    ("""    // 型 14／15（ブロック状態）は771・772・773・774で同じ番号のため対応表に
    // 通すだけ（実質そのまま）。型 24（狼バリアント）は Holder（varint）
    // のためそのまま写す。型 17／18（粒子）は ID を対応表に通す。""",
     """    // 型 14／15（ブロック状態）は773・774で同じ番号のため対応表に
    // 通すだけ（実質そのまま）。型 23（狼バリアント）・24（狼の音）は
    // Holder（varint）のためそのまま写す。型 16／17（粒子）は ID を対応表に通す。
    // 型番号は773→774で28番以降が+1ずれる（28＝zombie_nautilus_variant 挿入）。"""),
]


# 削除するメソッド（shift 版では不要・誤りのため。replace 後に削除する）。
DELETE_METHODS = [
    # 773節には770時代の削除対象（hide_tooltip／trim 769合成／
    # Heightmap NBT変換）は既に無いため、削除は不要。
]


# ----------------------------------------------------------------------
# ChunkTranslator 774 節のメソッド置き換え
# ----------------------------------------------------------------------

CHUNK_METHOD_REPLACEMENTS = {
"translateComponent773To774(Reader reader, Writer writer, int kind773)": """    /**
     * 成分1個を773→774向けに変換する。種別番号は773の番号で dispatch する
     * （773は96件、774は104件。KIND 表での付け替えは呼び出し側で行う）。
     * レジストリIDを付け替えるものだけ変換する。落とす成分は無い
     * （{@code false} を返すことは無いが、呼び出し側の規約に合わせる）。
     * 16は repair_cost（varint）。18が enchantment_glint_override（bool）。
     * （773節の同名メソッドが 16 を enchantment_glint_override の bool と
     * していたのは誤り。mc-1.21.9-protocol.json の SlotComponentType による。
     * bool で読むと repair_cost が 2 以上のときに値が壊れる。）
     */
    private static boolean translateComponent773To774(Reader reader, Writer writer, int kind773) {
        switch (kind773) {
            case 4 -> { } // unbreakable（void。770・771・772・773・774とも中身なし）
            case 10, 34 -> translateEnchantments773To774(reader, writer);
            case 11, 12 -> translateItemBlockPredicates773To774(reader, writer);
            case 13 -> translateAttributeModifiers773To774(reader, writer);
            case 14 -> translateCustomModelData773To774(reader, writer);
            case 15 -> translateTooltipDisplay773To774(reader, writer);
            case 16 -> writer.writeVarInt(reader.readVarInt()); // repair_cost（varint）
            case 18 -> writer.writeBoolean(reader.readBoolean()); // enchantment_glint_override
            case 20 -> translateFood773To774(reader, writer);
            case 21 -> translateConsumable773To774(reader, writer);
            case 22 -> translateUseRemainder773To774(reader, writer);
            case 25 -> translateTool773To774(reader, writer);
            case 26 -> { // weapon：varint＋float（レジストリ無し）
                writer.writeVarInt(reader.readVarInt());
                writer.writeFloat(reader.readFloat());
            }
            case 28 -> translateEquippable773To774(reader, writer);
            case 29 -> translateRepairable773To774(reader, writer);
            case 32 -> translateDeathProtection773To774(reader, writer);
            case 33 -> translateBlocksAttacks773To774(reader, writer);
            case 35 -> translateDyedColor773To774(reader, writer);
            case 40, 41, 66 -> translateSlotArray773To774(reader, writer);
            case 43 -> writer.writeFloat(reader.readFloat()); // potion_duration_scale
            case 47 -> {
                return translateTrim773To774(reader, writer);
            }
            case 52 -> {
                return translateInstrument773To774(reader, writer);
            }
            case 53 -> copyProvidesTrimMaterial773To774(reader, writer);
            case 55 -> translateJukeboxPlayable773To774(reader, writer);
            case 56 -> writer.writeString(reader.readString()); // provides_banner_patterns
            case 62 -> translateNoteBlockSound773To774(reader, writer);
            case 63 -> translateBannerPatterns773To774(reader, writer);
            case 65 -> translatePotDecorations773To774(reader, writer);
            case 71 -> translateBreakSound773To774(reader, writer);
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
            case 89 -> copyPaintingVariantHolder773To774(reader, writer);
            default -> copyComponentOpaque774(reader, writer, kind773);
        }
        return true;
    }
""",
"translateComponent774To773(Reader reader, Writer writer, int kind774)": """    /**
     * 成分1個を774→773向けに変換する。種別番号は774の番号で dispatch する
     * （774は104件。773の番号のまま書くと全段ずれるため注意。KIND 表の
     * 対応は mc-1.21.11-protocol.json の SlotComponentType による）。
     * 773に無い新規 8 種（5 use_effects、7 minimum_attack_charge、
     * 8 damage_type、30 attack_range、38 piercing_weapon、39 kinetic_weapon、
     * 40 swing_animation、94 zombie_nautilus/variant）は中身を読み飛ばして
     * {@code false} を返す（呼び出し側が落とす）。レジストリIDを付け替える
     * ものだけ変換する。16は attribute_modifiers、19が repair_cost（varint）、
     * 21が enchantment_glint_override（bool）。
     */
    private static boolean translateComponent774To773(Reader reader, Writer writer, int kind774) {
        switch (kind774) {
            case 4 -> { } // unbreakable（void。770・771・772・773・774とも中身なし）
            case 13, 41 -> translateEnchantments774To773(reader, writer);
            case 14, 15 -> translateItemBlockPredicates774To773(reader, writer);
            case 16 -> translateAttributeModifiers774To773(reader, writer);
            case 17 -> translateCustomModelData774To773(reader, writer);
            case 18 -> translateTooltipDisplay774To773(reader, writer);
            case 19 -> writer.writeVarInt(reader.readVarInt()); // repair_cost（varint）
            case 21 -> writer.writeBoolean(reader.readBoolean()); // enchantment_glint_override
            case 23 -> translateFood774To773(reader, writer);
            case 24 -> translateConsumable774To773(reader, writer);
            case 25 -> translateUseRemainder774To773(reader, writer);
            case 28 -> translateTool774To773(reader, writer);
            case 29 -> { // weapon：varint＋float（レジストリ無し）
                writer.writeVarInt(reader.readVarInt());
                writer.writeFloat(reader.readFloat());
            }
            case 32 -> translateEquippable774To773(reader, writer);
            case 33 -> translateRepairable774To773(reader, writer);
            case 36 -> translateDeathProtection774To773(reader, writer);
            case 37 -> translateBlocksAttacks774To773(reader, writer);
            case 42 -> translateDyedColor774To773(reader, writer);
            case 47, 48, 73 -> translateSlotArray774To773(reader, writer);
            case 50 -> writer.writeFloat(reader.readFloat()); // potion_duration_scale
            case 54 -> {
                return translateTrim774To773(reader, writer);
            }
            case 59 -> {
                return translateInstrument774To773(reader, writer);
            }
            case 60 -> copyProvidesTrimMaterial774To773(reader, writer);
            case 62 -> translateJukeboxPlayable774To773(reader, writer);
            case 63 -> writer.writeString(reader.readString()); // provides_banner_patterns
            case 69 -> translateNoteBlockSound774To773(reader, writer);
            case 70 -> translateBannerPatterns774To773(reader, writer);
            case 72 -> translatePotDecorations774To773(reader, writer);
            case 78 -> translateBreakSound774To773(reader, writer);
            case 79, 80, 81, 82, 83, 84, 85, 86, 87, 88, 89, 90, 91, 92,
                    95, 96, 98, 99, 100, 101, 102, 103 -> writer.writeVarInt(reader.readVarInt()); // 各種 variant／collar／color
            case 93 -> { // chicken/variant
                if (reader.readBoolean()) {
                    writer.writeBoolean(true);
                    writer.writeVarInt(reader.readVarInt());
                } else {
                    writer.writeBoolean(false);
                    writer.writeString(reader.readString());
                }
            }
            case 97 -> copyPaintingVariantHolder774To773(reader, writer);
            case 5, 7, 8, 30, 38, 39, 40, 94 -> {
                // 774 の新規成分。773 に無いため中身を読み飛ばして落とす。
                skipComponentData774(reader, kind774);
                return false;
            }
            default -> copyComponentOpaque774(reader, writer, kind774);
        }
        return true;
    }
""",
"skipComponentData774(Reader reader, int kind774)": """    /**
     * 成分データ1個分を読み飛ばす（774形式）。種別番号は774の番号で
     * dispatch する（shift 版は773の番号のままだったため書き直す。
     * 774の番号では 18＝tooltip_display、21＝enchantment_glint_override、
     * 29＝weapon、37＝blocks_attacks、50＝potion_duration_scale、
     * 60＝provides_trim_material、63＝provides_banner_patterns、
     * 78＝break_sound、93＝chicken/variant、97＝painting/variant）。
     * 774の新規 8 種はここで読み飛ばす（use_effects 5、minimum_attack_charge 7、
     * damage_type 8、attack_range 30、piercing_weapon 38、kinetic_weapon 39、
     * swing_animation 40、zombie_nautilus/variant 94）。形式は
     * mc-1.21.11-protocol.json の SlotComponent による。
     * それ以外は種別を773に直して773の読み飛ばしに委譲する。
     */
    private static void skipComponentData774(Reader reader, int kind774) {
        switch (kind774) {
            case 4 -> { } // unbreakable（void）
            case 18 -> { // tooltip_display
                reader.readBoolean(); // hideTooltip
                int hidden = reader.readVarInt();
                for (int i = 0; i < hidden; i++) {
                    reader.readVarInt();
                }
            }
            case 19 -> reader.readVarInt(); // repair_cost
            case 21 -> reader.readBoolean(); // enchantment_glint_override（bool）
            case 29 -> { // weapon
                reader.readVarInt(); // itemDamagePerAttack
                reader.readFloat(); // disableBlockingForSeconds
            }
            case 37 -> skipBlocksAttacks774(reader);
            case 50 -> reader.readFloat(); // potion_duration_scale
            case 60 -> skipProvidesTrimMaterial774(reader);
            case 63 -> reader.readString(); // provides_banner_patterns
            case 78 -> ExtraPlayTranslator.skipSoundHolder(reader); // break_sound
            case 93 -> { // chicken/variant（bool＋varint／string）
                if (reader.readBoolean()) {
                    reader.readVarInt();
                } else {
                    reader.readString();
                }
            }
            case 94 -> { // zombie_nautilus/variant（chicken/variant と同じ形式）
                if (reader.readBoolean()) {
                    reader.readVarInt();
                } else {
                    reader.readString();
                }
            }
            case 97 -> skipPaintingVariantHolder774(reader);
            case 79, 80, 81, 82, 83, 84, 85, 86, 87, 88, 89, 90, 91, 92,
                    95, 96, 98, 99, 100, 101, 102, 103 -> reader.readVarInt(); // 各種 variant／collar／color
            case 5 -> { // use_effects：bool＋bool＋f32
                reader.readBoolean(); // can_sprint
                reader.readBoolean(); // interact_vibrations
                reader.readFloat(); // speed_multiplier
            }
            case 7 -> reader.readFloat(); // minimum_attack_charge
            case 8 -> { // damage_type：bool＋varint／string
                if (reader.readBoolean()) {
                    reader.readVarInt();
                } else {
                    reader.readString();
                }
            }
            case 30 -> { // attack_range：f32×6
                for (int i = 0; i < 6; i++) {
                    reader.readFloat();
                }
            }
            case 38 -> { // piercing_weapon：bool＋bool＋option 音×2
                reader.readBoolean(); // dealsKnockback
                reader.readBoolean(); // dismounts
                if (reader.readBoolean()) { // sound（option）
                    ExtraPlayTranslator.skipSoundHolder(reader);
                }
                if (reader.readBoolean()) { // hitSound（option）
                    ExtraPlayTranslator.skipSoundHolder(reader);
                }
            }
            case 39 -> { // kinetic_weapon
                reader.readVarInt(); // contactCooldownTicks
                reader.readVarInt(); // delayTicks
                for (int i = 0; i < 3; i++) { // dismount／knockback／damage 条件（option）
                    if (reader.readBoolean()) {
                        reader.readVarInt(); // maxDurationTicks
                        reader.readFloat(); // minSpeed
                        reader.readFloat(); // minRelativeSpeed
                    }
                }
                reader.readFloat(); // forwardMovement
                reader.readFloat(); // damageMultiplier
                if (reader.readBoolean()) { // sound（option）
                    ExtraPlayTranslator.skipSoundHolder(reader);
                }
                if (reader.readBoolean()) { // hitSound（option）
                    ExtraPlayTranslator.skipSoundHolder(reader);
                }
            }
            case 40 -> { // swing_animation：varint＋varint
                reader.readVarInt(); // type（none／whack／stab）
                reader.readVarInt(); // duration
            }
            default -> {
                int kind773 = mapComponentKind774To773(kind774);
                if (kind773 < 0) {
                    throw new IllegalArgumentException("未知の成分種別です（774）: " + kind774);
                }
                skipComponentData773(reader, kind773);
            }
        }
    }
""",
"translateDataComponentMatchers773To774(Reader reader, Writer writer)": """    /** DataComponentMatchers（773→774）。exact は成分の列、partial は種別の列。種別番号は KIND 表で付け替える（shift 版は773の番号のまま書いていたため書き直す）。 */
    private static void translateDataComponentMatchers773To774(Reader reader, Writer writer) {
        int exact = reader.readVarInt();
        Writer kept = new Writer();
        int keptCount = 0;
        for (int i = 0; i < exact; i++) {
            int kind773 = reader.readVarInt();
            Writer component = new Writer();
            if (!translateComponent773To774(reader, component, kind773)) {
                continue;
            }
            kept.writeVarInt(mapComponentKind773To774(kind773));
            kept.writeBytes(component.bytes());
            keptCount++;
        }
        writer.writeVarInt(keptCount);
        writer.writeBytes(kept.bytes());
        int partial = reader.readVarInt();
        writer.writeVarInt(partial);
        for (int i = 0; i < partial; i++) {
            writer.writeVarInt(mapComponentKind773To774(reader.readVarInt()));
        }
    }
""",
"translateSlotComponents774To773(Reader reader, Writer writer)": """    /** スロットの added／removed 成分部分だけを774→773向けに変換する。種別番号は KIND 表で付け替える（773は96件、774は104件）。774 の新規 8 種は落とす。 */
    private static void translateSlotComponents774To773(Reader reader, Writer writer) {
        int added = reader.readVarInt();
        Writer kept = new Writer();
        int keptCount = 0;
        for (int i = 0; i < added; i++) {
            int kind774 = reader.readVarInt();
            int kind773 = mapComponentKind774To773(kind774);
            if (kind773 < 0) {
                skipComponentData774(reader, kind774);
                continue;
            }
            Writer component = new Writer();
            if (!translateComponent774To773(reader, component, kind774)) {
                continue;
            }
            kept.writeVarInt(kind773);
            kept.writeBytes(component.bytes());
            keptCount++;
        }
        writer.writeVarInt(keptCount);
        writer.writeBytes(kept.bytes());
        int removed = reader.readVarInt();
        Writer keptRemoved = new Writer();
        int keptRemovedCount = 0;
        for (int i = 0; i < removed; i++) {
            int kind773 = mapComponentKind774To773(reader.readVarInt());
            if (kind773 < 0) {
                continue;
            }
            keptRemoved.writeVarInt(kind773);
            keptRemovedCount++;
        }
        writer.writeVarInt(keptRemovedCount);
        writer.writeBytes(keptRemoved.bytes());
    }
""",
"translateComponentList774To773(Reader reader, Writer writer)": """    /** 成分の列（個数 VarInt＋成分1個ずつ）を774→773向けに変換する。種別番号は KIND 表で付け替える（773は96件、774は104件）。774 の新規 8 種は落とす。 */
    private static void translateComponentList774To773(Reader reader, Writer writer) {
        int added = reader.readVarInt();
        Writer kept = new Writer();
        int keptCount = 0;
        for (int i = 0; i < added; i++) {
            int kind774 = reader.readVarInt();
            int kind773 = mapComponentKind774To773(kind774);
            if (kind773 < 0) {
                skipComponentData774(reader, kind774);
                continue;
            }
            Writer component = new Writer();
            if (!translateComponent774To773(reader, component, kind774)) {
                continue;
            }
            kept.writeVarInt(kind773);
            kept.writeBytes(component.bytes());
            keptCount++;
        }
        writer.writeVarInt(keptCount);
        writer.writeBytes(kept.bytes());
    }
""",
"translateTooltipDisplay774To773(Reader reader, Writer writer)": """    /** tooltip_display（774→773）。770・771・772・773・774とも {hideTooltip bool, hiddenComponents の列} のためそのまま写す。 */
    private static void translateTooltipDisplay774To773(Reader reader, Writer writer) {
        writer.writeBoolean(reader.readBoolean()); // hideTooltip
        int hidden = reader.readVarInt();
        writer.writeVarInt(hidden);
        for (int i = 0; i < hidden; i++) {
            writer.writeVarInt(reader.readVarInt());
        }
    }
""",
"translateUnbreakable773To774(Reader reader, Writer writer)": """    /** unbreakable を773→774向けに変換する。770・771・772・773・774とも void のため何もしない。 */
    private static void translateUnbreakable773To774(Reader reader, Writer writer) {
    }
""",
"translateUnbreakable774To773(Reader reader, Writer writer)": """    /** unbreakable を774→773向けに変換する。770・771・772・773・774とも void のため何もしない。 */
    private static void translateUnbreakable774To773(Reader reader, Writer writer) {
    }
""",
"translateDyedColor773To774(Reader reader, Writer writer)": """    /** dyed_color を773→774向けに変換する。770・771・772・773・774とも色 i32 だけ。 */
    private static void translateDyedColor773To774(Reader reader, Writer writer) {
        writer.writeInt(reader.readInt()); // color
    }
""",
"translateDyedColor774To773(Reader reader, Writer writer)": """    /** dyed_color を774→773向けに変換する。770・771・772・773・774とも色 i32 だけ。 */
    private static void translateDyedColor774To773(Reader reader, Writer writer) {
        writer.writeInt(reader.readInt()); // color
    }
""",
"copyPaintingVariantHolder773To774(Reader reader, Writer writer)": """    /** painting/variant（773→774）の Holder を変換する。絵画は773が50種・774が51種（dennis を14に挿入）。14番以降は+1ずれる。 */
    private static void copyPaintingVariantHolder773To774(Reader reader, Writer writer) {
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
        int mapped = RegistryRemapper.mapPaintingVariant773To774(holder - 1);
        if (mapped == RegistryRemapper.DROP) {
            // 範囲外の絵画ID。落とす表現が無いため kebab（0）で代用する。
            mapped = 0;
        }
        writer.writeVarInt(mapped + 1);
    }
""",
"copyPaintingVariantHolder774To773(Reader reader, Writer writer)": """    /** painting/variant（774→773）の Holder を変換する。dennis（774=14）は773に無いため kebab（0）で代用する。14番より後は-1する。 */
    private static void copyPaintingVariantHolder774To773(Reader reader, Writer writer) {
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
        int mapped = RegistryRemapper.mapPaintingVariant774To773(holder - 1);
        if (mapped == RegistryRemapper.DROP) {
            // dennis は773に無い。落とす表現が無いため kebab（0）で代用する。
            mapped = 0;
        }
        writer.writeVarInt(mapped + 1);
    }
""",

"translateParticleData774To773(Reader reader, Writer writer, int particleId)": """    /** パーティクルのデータ部分を774→773向けに変換する（particleId は774のID）。粒子は773・774で同一のため case は両版共通のIDで書く。 */
    private static void translateParticleData774To773(Reader reader, Writer writer, int particleId) {
        switch (particleId) {
            case 1, 2, 29, 109 -> // block、block_marker、falling_dust、dust_pillar：ブロック状態IDを付け替える
                    writer.writeVarInt(RegistryRemapper.mapBlockStateUp774(reader.readVarInt()));
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
            case 47 -> translateSlot774To773(reader, writer); // item
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
                    writer.writeVarInt(RegistryRemapper.mapBlockStateUp774(reader.readVarInt()));
            default -> {
                // その他の粒子にデータは無い。
            }
        }
    }
""",
}


# 774節に新しく追加するヘルパー（shift 版には無いもの）。
# 773節を shift すると EXTRA の11件はすべて shift 版に既にある
# （同一内容）ため、ここには何も残さない。絵画2件だけは shift 版が
# そのまま写し（dennis を落とさない）ため、置換表へ移してある。
EXTRA_METHODS_774 = """
    /**
     * resolvable_profile を773⇔774でそのまま写す。773・774で形式は同一。
     * varint（0＝partial／1＝complete）＋本体＋PlayerSkinPatch。
     * partial：option name（string）、option UUID、properties
     * （varint 件数＋GameProfileProperty：string、string、option string）。
     * complete：UUID、name（string）、properties（同上）。
     * PlayerSkinPatch：option body（string）、option cape（string）、
     * option elytra（string）、option model（varint：0＝wide／1＝slim）。
     * 形式は mc-1.21.9-protocol.json／mc-1.21.11-protocol.json の
     * ResolvableProfile／PartialResolvableProfile／GameProfile／
     * GameProfileProperty／PlayerSkinPatch による。
     */
    private static void copyResolvableProfile(Reader reader, Writer writer) {
        int kind = reader.readVarInt();
        writer.writeVarInt(kind);
        if (kind == 0) {
            // partial
            copyOptionalString(reader, writer);
            copyOptionalUuid(reader, writer);
            copyGameProfileProperties(reader, writer);
        } else {
            // complete
            writer.writeUuid(reader.readUuid());
            writer.writeString(reader.readString());
            copyGameProfileProperties(reader, writer);
        }
        // PlayerSkinPatch
        copyOptionalString(reader, writer); // body
        copyOptionalString(reader, writer); // cape
        copyOptionalString(reader, writer); // elytra
        boolean hasModel = reader.readBoolean();
        writer.writeBoolean(hasModel);
        if (hasModel) {
            writer.writeVarInt(reader.readVarInt()); // model（0＝wide／1＝slim）
        }
    }

    /** GameProfileProperty の列（varint 件数＋（string、string、option string））をそのまま写す。 */
    private static void copyGameProfileProperties(Reader reader, Writer writer) {
        int count = reader.readVarInt();
        writer.writeVarInt(count);
        for (int i = 0; i < count; i++) {
            writer.writeString(reader.readString()); // name
            writer.writeString(reader.readString()); // value
            copyOptionalString(reader, writer); // signature
        }
    }

    /** option string（bool＋string）をそのまま写す。 */
    private static void copyOptionalString(Reader reader, Writer writer) {
        boolean present = reader.readBoolean();
        writer.writeBoolean(present);
        if (present) {
            writer.writeString(reader.readString());
        }
    }

    /** option UUID（bool＋UUID）をそのまま写す。 */
    private static void copyOptionalUuid(Reader reader, Writer writer) {
        boolean present = reader.readBoolean();
        writer.writeBoolean(present);
        if (present) {
            writer.writeUuid(reader.readUuid());
        }
    }
"""

# 置き換えの続き（成分ヘルパー：772→773の形式変換を含まないもの）。
CHUNK_METHOD_REPLACEMENTS.update({
"translateEnchantments773To774(Reader reader, Writer writer)": """    /** enchantments／stored_enchantments を773→774向けに変換する。lunge（774=21）の挿入で21番以降が+1ずれるため対応表に通す。末尾の showTooltip は770・771・772・773・774とも無い。 */
    private static void translateEnchantments773To774(Reader reader, Writer writer) {
        int count = reader.readVarInt();
        Writer kept = new Writer();
        int keptCount = 0;
        for (int i = 0; i < count; i++) {
            int id = reader.readVarInt();
            int level = reader.readVarInt();
            int mapped = RegistryRemapper.mapEnchantment773To774(id);
            if (mapped == RegistryRemapper.DROP) {
                logOnce("enchantment-drop-774",
                        "774に無いエンチャントを落とします（id=" + id + "）。");
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
"translateEnchantments774To773(Reader reader, Writer writer)": """    /** enchantments／stored_enchantments を774→773向けに変換する。lunge（774=21）は773に無いため落とす。21番以降は-1ずれる。末尾の showTooltip は770・771・772・773・774とも無い。 */
    private static void translateEnchantments774To773(Reader reader, Writer writer) {
        int count = reader.readVarInt();
        Writer kept = new Writer();
        int keptCount = 0;
        for (int i = 0; i < count; i++) {
            int id = reader.readVarInt();
            int level = reader.readVarInt();
            int mapped = RegistryRemapper.mapEnchantment774To773(id);
            if (mapped == RegistryRemapper.DROP) {
                logOnce("enchantment-drop-773-from-774",
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
"translateAttributeModifiers773To774(Reader reader, Writer writer)": """    /** attribute_modifiers を773→774向けに変換する。770・771・772・773・774とも {属性の配列} のみ（末尾の showTooltip は無い）。 */
    private static void translateAttributeModifiers773To774(Reader reader, Writer writer) {
        int count = reader.readVarInt();
        Writer kept = new Writer();
        int keptCount = 0;
        for (int i = 0; i < count; i++) {
            int attrId = reader.readVarInt();
            String name = reader.readString();
            double value = reader.readDouble();
            int operation = reader.readVarInt();
            int slot = reader.readVarInt();
            int mapped = RegistryRemapper.mapAttribute773To774(attrId);
            if (mapped == RegistryRemapper.DROP) {
                logOnce("attribute-modifier-drop-774",
                        "774に無い属性の修飾子を落とします（id=" + attrId + "）。");
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
"translateAttributeModifiers774To773(Reader reader, Writer writer)": """    /** attribute_modifiers を774→773向けに変換する。770・771・772・773・774とも {属性の配列} のみ。 */
    private static void translateAttributeModifiers774To773(Reader reader, Writer writer) {
        int count = reader.readVarInt();
        Writer kept = new Writer();
        int keptCount = 0;
        for (int i = 0; i < count; i++) {
            int attrId = reader.readVarInt();
            String name = reader.readString();
            double value = reader.readDouble();
            int operation = reader.readVarInt();
            int slot = reader.readVarInt();
            int mapped = RegistryRemapper.mapAttribute774To773(attrId);
            if (mapped == RegistryRemapper.DROP) {
                logOnce("attribute-modifier-drop-773-from-774",
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
"translateTrim773To774(Reader reader, Writer writer)": """    /** trim を773→774向けに変換する。材料・型の Holder。770・771・772・773・774とも showInTooltip は無い。 */
    private static boolean translateTrim773To774(Reader reader, Writer writer) {
        if (!translateTrimMaterial773To774(reader, writer)) {
            return false;
        }
        return translateTrimPattern773To774(reader, writer);
    }
""",
"translateTrim774To773(Reader reader, Writer writer)": """    /** trim を774→773向けに変換する。材料・型の Holder。770・771・772・773・774とも showInTooltip は無い。 */
    private static boolean translateTrim774To773(Reader reader, Writer writer) {
        if (!translateTrimMaterial774To773(reader, writer)) {
            return false;
        }
        return translateTrimPattern774To773(reader, writer);
    }
""",
"translateTrimMaterial773To774(Reader reader, Writer writer)": """    /** trim の材料の Holder を773→774向けに変換する。770・771・772・773・774とも直書きは {assetBase, overrides, description}（ingredientId は無い）。材料IDは11種・同じ並びのためそのまま。 */
    private static boolean translateTrimMaterial773To774(Reader reader, Writer writer) {
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
"translateTrimMaterial774To773(Reader reader, Writer writer)": """    /** trim の材料の Holder を774→773向けに変換する。直書きは {assetBase, overrides, description}。 */
    private static boolean translateTrimMaterial774To773(Reader reader, Writer writer) {
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
"translateTrimPattern773To774(Reader reader, Writer writer)": """    /** trim の型の Holder を773→774向けに変換する。770・771・772・773・774とも直書きは {assetId, description, decal}（templateItemId は無い）。 */
    private static boolean translateTrimPattern773To774(Reader reader, Writer writer) {
        int holder = reader.readVarInt();
        if (holder != 0) {
            int mapped = RegistryRemapper.mapTrimPattern773To774(holder - 1);
            if (mapped == RegistryRemapper.DROP) {
                logOnce("trim-pattern-drop-774",
                        "774に無い装飾パターンの trim を落とします（id=" + (holder - 1) + "）。");
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
"translateTrimPattern774To773(Reader reader, Writer writer)": """    /** trim の型の Holder を774→773向けに変換する。直書きは {assetId, description, decal}。 */
    private static boolean translateTrimPattern774To773(Reader reader, Writer writer) {
        int holder = reader.readVarInt();
        if (holder != 0) {
            int mapped = RegistryRemapper.mapTrimPattern774To773(holder - 1);
            if (mapped == RegistryRemapper.DROP) {
                logOnce("trim-pattern-drop-773-from-774",
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
})

CHUNK_METHOD_REPLACEMENTS.update({
"translateInstrument773To774(Reader reader, Writer writer)": """    /**
     * instrument コンポーネントを773→774向けに変換する。770・771・772・773・774とも
     * {hasHolder bool, data：true なら Holder／false なら楽器名} の
     * コンテナ。楽器IDは770・771・772・773・774とも8件・同じ並びのためそのまま。
     */
    private static boolean translateInstrument773To774(Reader reader, Writer writer) {
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
        translateSoundHolder773To774(reader, writer);
        writer.writeFloat(reader.readFloat()); // useDuration
        writer.writeFloat(reader.readFloat()); // range
        copyOptionalAnonymousNbt(reader, writer);
        return true;
    }
""",
"translateInstrument774To773(Reader reader, Writer writer)": """    /**
     * instrument コンポーネントを774→773向けに変換する。770・771・772・773・774とも
     * {hasHolder bool, data} のコンテナ。楽器IDは8件・同じ並びのためそのまま。
     */
    private static boolean translateInstrument774To773(Reader reader, Writer writer) {
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
        translateSoundHolder774To773(reader, writer);
        writer.writeFloat(reader.readFloat()); // useDuration
        writer.writeFloat(reader.readFloat()); // range
        copyOptionalAnonymousNbt(reader, writer);
        return true;
    }
""",
"translateJukeboxPlayable773To774(Reader reader, Writer writer)": """    /** jukebox_playable を773→774向けに変換する。曲IDは772・773・774で同一（21曲・同じ並び）。直書きの音の Holder も付け替える。末尾の showInTooltip は770・771・772・773・774ともある。 */
    private static void translateJukeboxPlayable773To774(Reader reader, Writer writer) {
        boolean hasHolder = reader.readBoolean();
        writer.writeBoolean(hasHolder);
        if (hasHolder) {
            int holder = reader.readVarInt();
            if (holder == 0) {
                writer.writeVarInt(0);
                translateSoundHolder773To774(reader, writer);
                writer.writeAnonymousNbt(reader.readAnonymousNbt().tag()); // description
                writer.writeFloat(reader.readFloat()); // lengthInSeconds
                writer.writeVarInt(reader.readVarInt()); // comparatorOutput
            } else {
                int mapped = RegistryRemapper.mapJukeboxSong773To774(holder - 1);
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
"translateJukeboxPlayable774To773(Reader reader, Writer writer)": """    /** jukebox_playable を774→773向けに変換する。曲IDは772・773・774で同一（21曲・同じ並び）。末尾の showInTooltip は770・771・772・773・774ともある。 */
    private static void translateJukeboxPlayable774To773(Reader reader, Writer writer) {
        boolean hasHolder = reader.readBoolean();
        writer.writeBoolean(hasHolder);
        if (hasHolder) {
            int holder = reader.readVarInt();
            if (holder == 0) {
                writer.writeVarInt(0);
                translateSoundHolder774To773(reader, writer);
                writer.writeAnonymousNbt(reader.readAnonymousNbt().tag()); // description
                writer.writeFloat(reader.readFloat()); // lengthInSeconds
                writer.writeVarInt(reader.readVarInt()); // comparatorOutput
            } else {
                int mapped = RegistryRemapper.mapJukeboxSong774To773(holder - 1);
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
"translateTool773To774(Reader reader, Writer writer)": """    /** tool を773→774向けに変換する。ルールの中のブロックIDだけ付け替え、末尾の canDestroyBlocksInCreative は770・771・772・773・774ともあるため写す。 */
    private static void translateTool773To774(Reader reader, Writer writer) {
        int ruleCount = reader.readVarInt();
        writer.writeVarInt(ruleCount);
        for (int i = 0; i < ruleCount; i++) {
            translateIdSetBlocks773To774(reader, writer);
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
"translateTool774To773(Reader reader, Writer writer)": """    /** tool を774→773向けに変換する。ルールの中のブロックIDだけ付け替え、末尾の canDestroyBlocksInCreative は写す。 */
    private static void translateTool774To773(Reader reader, Writer writer) {
        int ruleCount = reader.readVarInt();
        writer.writeVarInt(ruleCount);
        for (int i = 0; i < ruleCount; i++) {
            translateIdSetBlocks774To773(reader, writer);
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
"translateItemBlockPredicates773To774(Reader reader, Writer writer)": """    /** can_place_on／can_break を773→774向けに変換する。述語の中のブロックIDだけ付け替える。末尾の showTooltip は770・771・772・773・774とも無い。 */
    private static void translateItemBlockPredicates773To774(Reader reader, Writer writer) {
        int count = reader.readVarInt();
        writer.writeVarInt(count);
        for (int i = 0; i < count; i++) {
            translateItemBlockPredicate773To774(reader, writer);
        }
    }
""",
"translateItemBlockPredicates774To773(Reader reader, Writer writer)": """    /** can_place_on／can_break を774→773向けに変換する。述語の中のブロックIDだけ付け替える。 */
    private static void translateItemBlockPredicates774To773(Reader reader, Writer writer) {
        int count = reader.readVarInt();
        writer.writeVarInt(count);
        for (int i = 0; i < count; i++) {
            translateItemBlockPredicate774To773(reader, writer);
        }
    }
""",
"translateItemBlockPredicate773To774(Reader reader, Writer writer)": """    /** ItemBlockPredicate 1個を773→774向けに変換する。述語の中のブロックIDだけ付け替え、末尾の DataComponentMatchers も変換する（770・771・772・773・774ともある）。 */
    private static void translateItemBlockPredicate773To774(Reader reader, Writer writer) {
        if (reader.readBoolean()) {
            writer.writeBoolean(true);
            translateIdSetBlocks773To774(reader, writer);
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
        translateDataComponentMatchers773To774(reader, writer);
    }
""",
"translateItemBlockPredicate774To773(Reader reader, Writer writer)": """    /** ItemBlockPredicate 1個を774→773向けに変換する。述語の中のブロックIDだけ付け替え、末尾の DataComponentMatchers も変換する。 */
    private static void translateItemBlockPredicate774To773(Reader reader, Writer writer) {
        if (reader.readBoolean()) {
            writer.writeBoolean(true);
            translateIdSetBlocks774To773(reader, writer);
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
        translateDataComponentMatchers774To773(reader, writer);
    }
""",
"translateParticle774To773(Reader reader, Writer writer)": """    /**
     * パーティクル1個（ID VarInt＋データ）を774→773向けに変換する。
     * パーティクルは770・771・772・773・774とも同一（tinted_leaves・firefly も両方に
     * ある）ため常に変換できる（{@code true} を返す）。
     */
    private static boolean translateParticle774To773(Reader reader, Writer writer) {
        int particleId = reader.readVarInt();
        writer.writeVarInt(RegistryRemapper.mapParticleUp774(particleId));
        translateParticleData774To773(reader, writer, particleId);
        return true;
    }
""",
"translateWorldParticles774To773(byte[] payload)": """    /**
     * World Particles（774 → 773）。alwaysShow は770・771・772・773・774ともにあるため
     * そのまま通す。粒子は770・771・772・773・774で同一のため落とすことは無い。
     */
    public static byte[] translateWorldParticles774To773(byte[] payload) {
        try {
            Reader reader = new Reader(payload);
            boolean longDistance = reader.readBoolean();
            boolean alwaysShow = reader.readBoolean(); // alwaysShow（770・771・772・773・774ともにある）
            double x = reader.readDouble();
            double y = reader.readDouble();
            double z = reader.readDouble();
            float offsetX = reader.readFloat();
            float offsetY = reader.readFloat();
            float offsetZ = reader.readFloat();
            float speed = reader.readFloat();
            int count = reader.readInt();
            Writer particleWriter = new Writer();
            translateParticle774To773(reader, particleWriter);
            requireEnd(reader);
            Writer writer = new Writer();
            writer.writeBoolean(longDistance);
            writer.writeBoolean(alwaysShow); // alwaysShow（773にもある。そのまま通す）
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
"translateMetadataValue773To774(Reader reader, Writer writer, int type)": """    /**
     * メタデータの値1個を773→774向けに変換する。落とすエントリは {@code false} を返す。
     * 型番号は773側の番号で dispatch する。772→773 で compound_tag（16）が消えて
     * 17番以降が1つ前にずれたため、773の番号は772と違う（16＝particle、17＝particles、
     * 18＝villager_data、19＝optional_unsigned_int、28＝optional_global_pos、
     * 29＝painting、30＝sniffer、31＝armadillo、32＝copper_golem_state、
     * 33＝weathering、34＝vector3、35＝quaternion、36＝resolvable_profile）。
     * 型番号の付け替えは mapMetadataType773To774（28番以降は+1）が行う。
     */
    private static boolean translateMetadataValue773To774(Reader reader, Writer writer, int type) {
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
            case 7 -> translateSlot773To774(reader, writer);
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
            case 14, 15 -> writer.writeVarInt(RegistryRemapper.mapBlockState773To774(reader.readVarInt()));
            case 16 -> translateParticle773To774(reader, writer);
            case 17 -> {
                int count = reader.readVarInt();
                writer.writeVarInt(count);
                for (int i = 0; i < count; i++) {
                    translateParticle773To774(reader, writer);
                }
            }
            case 18 -> { // villager_data：type／profession／level
                writer.writeVarInt(reader.readVarInt());
                writer.writeVarInt(reader.readVarInt());
                writer.writeVarInt(reader.readVarInt());
            }
            case 19, 20, 21, 22 -> writer.writeVarInt(reader.readVarInt()); // optional_unsigned_int／pose／cat／cow
            case 23 -> translateWolfVariantHolder773To774(reader, writer);
            case 24, 25, 26, 27 -> writer.writeVarInt(reader.readVarInt()); // wolf_sound／frog／pig／chicken
            case 28 -> { // optional_global_pos
                boolean present = reader.readBoolean();
                writer.writeBoolean(present);
                if (present) {
                    writer.writeString(reader.readString());
                    writer.writePositionRaw(reader.readPositionRaw());
                }
            }
            case 29 -> {
                return translatePaintingVariantHolder773To774(reader, writer);
            }
            case 30, 31, 32, 33 -> writer.writeVarInt(reader.readVarInt()); // sniffer／armadillo／copper_golem／weathering
            case 34 -> { // vector3
                writer.writeFloat(reader.readFloat());
                writer.writeFloat(reader.readFloat());
                writer.writeFloat(reader.readFloat());
            }
            case 35 -> { // quaternion
                writer.writeFloat(reader.readFloat());
                writer.writeFloat(reader.readFloat());
                writer.writeFloat(reader.readFloat());
                writer.writeFloat(reader.readFloat());
            }
            case 36 -> copyResolvableProfile(reader, writer); // resolvable_profile（773・774で同一形式）
            default -> throw new IllegalArgumentException("未知のメタデータ型です: " + type);
        }
        return true;
    }
""",
"translateMetadataValue774To773(Reader reader, Writer writer, int type)": """    /**
     * メタデータの値1個を774→773向けに変換する。落とすエントリは {@code false} を返す。
     * 型番号は774側の番号で dispatch する。774は zombie_nautilus_variant（28）の
     * 挿入で28番以降が773より1つ後ろにずれる（29＝optional_global_pos、
     * 30＝painting、31＝sniffer、32＝armadillo、33＝copper_golem_state、
     * 34＝weathering、35＝vector3、36＝quaternion、37＝resolvable_profile）
     * ため、この版だけ mapMetadataType774To773 が -1 する。773には無い
     * zombie_nautilus_variant（28）と humanoid_arm（38）は項目ごと落とす。
     * 773側の番号一覧は translateMetadataValue773To774 の注記を参照。
     */
    private static boolean translateMetadataValue774To773(Reader reader, Writer writer, int type) {
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
            case 7 -> translateSlot774To773(reader, writer);
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
            case 14, 15 -> writer.writeVarInt(RegistryRemapper.mapBlockStateUp774(reader.readVarInt()));
            case 16 -> translateParticle774To773(reader, writer);
            case 17 -> {
                int count = reader.readVarInt();
                writer.writeVarInt(count);
                for (int i = 0; i < count; i++) {
                    translateParticle774To773(reader, writer);
                }
            }
            case 18 -> { // villager_data：type／profession／level
                writer.writeVarInt(reader.readVarInt());
                writer.writeVarInt(reader.readVarInt());
                writer.writeVarInt(reader.readVarInt());
            }
            case 19, 20, 21, 22 -> writer.writeVarInt(reader.readVarInt()); // optional_unsigned_int／pose／cat／cow
            case 23 -> translateWolfVariantHolder774To773(reader, writer);
            case 24, 25, 26, 27 -> writer.writeVarInt(reader.readVarInt()); // wolf_sound／frog／pig／chicken
            case 28, 38 -> { // zombie_nautilus_variant／humanoid_arm（773に無い）
                reader.readVarInt();
                return false;
            }
            case 29 -> { // optional_global_pos
                boolean present = reader.readBoolean();
                writer.writeBoolean(present);
                if (present) {
                    writer.writeString(reader.readString());
                    writer.writePositionRaw(reader.readPositionRaw());
                }
            }
            case 30 -> {
                return translatePaintingVariantHolder774To773(reader, writer);
            }
            case 31, 32, 33, 34 -> writer.writeVarInt(reader.readVarInt()); // sniffer／armadillo／copper_golem／weathering
            case 35 -> { // vector3
                writer.writeFloat(reader.readFloat());
                writer.writeFloat(reader.readFloat());
                writer.writeFloat(reader.readFloat());
            }
            case 36 -> { // quaternion
                writer.writeFloat(reader.readFloat());
                writer.writeFloat(reader.readFloat());
                writer.writeFloat(reader.readFloat());
                writer.writeFloat(reader.readFloat());
            }
            case 37 -> copyResolvableProfile(reader, writer); // resolvable_profile（773・774で同一形式）
            default -> throw new IllegalArgumentException("未知のメタデータ型です: " + type);
        }
        return true;
    }
""",
"translateWolfVariantHolder773To774(Reader reader, Writer writer)": """    /** 狼バリアントの Holder を773→774向けに変換する。770・771・772・773・774ともただの varint（9種・同じ並び）のためそのまま写す。 */
    private static boolean translateWolfVariantHolder773To774(Reader reader, Writer writer) {
        writer.writeVarInt(reader.readVarInt());
        return true;
    }
""",
"translateWolfVariantHolder774To773(Reader reader, Writer writer)": """    /** 狼バリアントの Holder を774→773向けに変換する。770・771・772・773・774ともただの varint のためそのまま写す。 */
    private static boolean translateWolfVariantHolder774To773(Reader reader, Writer writer) {
        writer.writeVarInt(reader.readVarInt());
        return true;
    }
""",
"mapMetadataType773To774(int type773)": """    /**
     * メタデータの型番号を773→774に付け替える。774は zombie_nautilus_variant（28）を
     * 挿入し、humanoid_arm（38）を末尾に追加した。773の型は36まで（0–36）。
     * 28番以降は+1ずれる。範囲外はそのまま返す（呼び出し側で検証する）。
     */
    private static int mapMetadataType773To774(int type773) {
        if (type773 < 0 || type773 > 36) {
            return type773;
        }
        return type773 < 28 ? type773 : type773 + 1;
    }
""",
"mapMetadataType774To773(int type774)": """    /**
     * メタデータの型番号を774→773に付け替える。774の型は38まで（0–38）。
     * zombie_nautilus_variant（28）と humanoid_arm（38）は773に無いため -1 を返す
     * （773節の mapMetadataTo773 は DROP（-1）を落とす規約のため）。
     * 29–37番は-1ずれる。範囲外はそのまま返す（呼び出し側で検証する）。
     */
    private static int mapMetadataType774To773(int type774) {
        if (type774 < 0 || type774 > 38) {
            return type774;
        }
        if (type774 == 28 || type774 == 38) {
            return -1;
        }
        return type774 < 28 ? type774 : type774 - 1;
    }
""",
"skipMetadataValue774(Reader reader, int type774)": """    /**
     * メタデータの値1個を読み飛ばす（774形式）。translateEntityMetadata774To773
     * から型番号の付け替えで落とす（mappedType &lt; 0）場合にだけ呼ばれる。
     * 774→773 で落とすのは zombie_nautilus_variant（28）と humanoid_arm（38）の
     * 2型のみで、どちらも varint のためここではそれだけを扱う。
     * （773節の skipMetadataValue773 と同じ方針。落とさない型の読み飛ばしは不要。）
     */
    private static void skipMetadataValue774(Reader reader, int type774) {
        switch (type774) {
            case 28, 38 -> reader.readVarInt(); // zombie_nautilus_variant／humanoid_arm
            default -> throw new IllegalArgumentException("読み飛ばせないメタデータ型です: " + type774);
        }
    }
""",
"translateChunkData773To774(byte[] payload)": """    /** チャンクデータを773→774向けに変換する。heightmap は770・771・772・773・774とも直接の配列（varint 件数＋（type、長さ、long列））のためそのまま写す。セクションのブロック状態・バイオームとブロックエンティティ種別を付け替える。 */
    public static byte[] translateChunkData773To774(byte[] payload) {
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
            byte[] translatedSections = translateChunkSections773To774(chunkData);
            int blockEntityCount = reader.readVarInt();
            List<BlockEntityEntry> kept = new ArrayList<>();
            for (int i = 0; i < blockEntityCount; i++) {
                int packedXZ = reader.readUnsignedByte();
                int y = reader.readShort();
                int type = reader.readVarInt();
                Tag nbt = readOptionalAnonymousNbt(reader);
                int mapped = RegistryRemapper.mapBlockEntityType773To774(type);
                if (mapped == RegistryRemapper.DROP) {
                    LOG.warning("774に無いブロックエンティティ種別（773 type=" + type
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
"translateChunkData774To773(byte[] payload)": """    /** チャンクデータを774→773向けに変換する。heightmap は770・771・772・773・774とも直接の配列のためそのまま写す。 */
    public static byte[] translateChunkData774To773(byte[] payload) {
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
            byte[] translatedSections = translateChunkSections774To773(chunkData);
            int blockEntityCount = reader.readVarInt();
            List<BlockEntityEntry> kept = new ArrayList<>();
            for (int i = 0; i < blockEntityCount; i++) {
                int packedXZ = reader.readUnsignedByte();
                int y = reader.readShort();
                int type = reader.readVarInt();
                Tag nbt = readOptionalAnonymousNbt(reader);
                int mapped = RegistryRemapper.mapBlockEntityTypeUp774(type);
                if (mapped == RegistryRemapper.DROP) {
                    LOG.warning("773に無いブロックエンティティ種別（774 type=" + type
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
"translatePalettedContainer773To774(Reader reader, Writer writer, boolean blocks)": """        /**
         * パレット付きコンテナ1個を773→774向けに変換する。ブロック状態と
         * バイオームの両方を付け替える。770・771・772・773・774とも Data Array に
         * 長さ varint 接頭辞は無い。
         */
        private static void translatePalettedContainer773To774(Reader reader, Writer writer, boolean blocks) {
        int bitsPerEntry = reader.readUnsignedByte();
        if (bitsPerEntry > 32) {
            throw new IllegalArgumentException("Bits Per Entry が大きすぎます: " + bitsPerEntry);
        }
        writer.writeUnsignedByte(bitsPerEntry);
        int entryCount = blocks ? 4096 : 64;
        if (bitsPerEntry == 0) {
            int value = reader.readVarInt();
            writer.writeVarInt(blocks
                    ? RegistryRemapper.mapBlockState773To774(value)
                    : mapBiome773To774(value));
            // 770・771・772・773・774とも長さ接頭辞は無い。
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
                        ? RegistryRemapper.mapBlockState773To774(value)
                        : mapBiome773To774(value));
            }
            // 間接パレットの Data Array はインデックス列なので中身は変えない。
            // 770・771・772・773・774とも長さ接頭辞は無い。長さは bpe から計算する。
            int perLong = 64 / bitsPerEntry;
            int expected = (entryCount + perLong - 1) / perLong;
            for (int i = 0; i < expected; i++) {
                writer.writeLong(reader.readLong());
            }
            return;
        }
        // 直接パレット：Data Array の中身がレジストリ番号そのもの。
        // 770・771・772・773・774とも長さ接頭辞は無い。長さは bpe から計算する。
        int perLong = 64 / bitsPerEntry;
        int expected = (entryCount + perLong - 1) / perLong;
        long[] longs = new long[expected];
        for (int i = 0; i < expected; i++) {
            longs[i] = reader.readLong();
        }
        remapDirectEntries773To774(longs, bitsPerEntry, entryCount, blocks);
        for (long value : longs) {
            writer.writeLong(value);
        }
    }
""",
"translatePalettedContainer774To773(Reader reader, Writer writer, boolean blocks)": """        /** パレット付きコンテナ1個を774→773向けに変換する。ブロック状態とバイオームの両方を付け替える。770・771・772・773・774とも Data Array に長さ varint 接頭辞は無い。 */
        private static void translatePalettedContainer774To773(Reader reader, Writer writer, boolean blocks) {
        int bitsPerEntry = reader.readUnsignedByte();
        if (bitsPerEntry > 32) {
            throw new IllegalArgumentException("Bits Per Entry が大きすぎます: " + bitsPerEntry);
        }
        writer.writeUnsignedByte(bitsPerEntry);
        int entryCount = blocks ? 4096 : 64;
        if (bitsPerEntry == 0) {
            int value = reader.readVarInt();
            writer.writeVarInt(blocks
                    ? RegistryRemapper.mapBlockStateUp774(value)
                    : mapBiomeUp774(value));
            // 770・771・772・773・774とも長さ接頭辞は無い。
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
                        ? RegistryRemapper.mapBlockStateUp774(value)
                        : mapBiomeUp774(value));
            }
            // 間接パレットの Data Array はインデックス列なので中身は変えない。
            // 770・771・772・773・774とも長さ接頭辞は無い。長さは bpe から計算する。
            int perLong = 64 / bitsPerEntry;
            int expected = (entryCount + perLong - 1) / perLong;
            for (int i = 0; i < expected; i++) {
                writer.writeLong(reader.readLong());
            }
            return;
        }
        // 直接パレット：Data Array の中身がレジストリ番号そのもの。
        // 770・771・772・773・774とも長さ接頭辞は無い。長さは bpe から計算する。
        int perLong = 64 / bitsPerEntry;
        int expected = (entryCount + perLong - 1) / perLong;
        long[] longs = new long[expected];
        for (int i = 0; i < expected; i++) {
            longs[i] = reader.readLong();
        }
        remapDirectEntries774To773(longs, bitsPerEntry, entryCount, blocks);
        for (long value : longs) {
            writer.writeLong(value);
        }
    }
""",
"translateExplosion773To774(byte[] payload)": """    /** Explosion（773 → 774）。knockback は770・771・772・773・774とも f64×3。粒子・音を付け替える。 */
    public static byte[] translateExplosion773To774(byte[] payload) {
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
            translateParticle773To774(reader, writer);
            translateSoundHolder773To774(reader, writer);
            requireEnd(reader);
            return writer.bytes();
        } catch (IllegalArgumentException e) {
            LOG.warning("Explosion を変換できなかったため素通しします: " + e.getMessage());
            return payload;
        }
    }
""",
"translateExplosion774To773(byte[] payload)": """    /** Explosion（774 → 773）。knockback は770・771・772・773・774とも f64×3。粒子・音を付け替える。 */
    public static byte[] translateExplosion774To773(byte[] payload) {
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
            translateParticle774To773(reader, particleWriter);
            writer.writeBytes(particleWriter.bytes());
            translateSoundHolder774To773(reader, writer);
            requireEnd(reader);
            return writer.bytes();
        } catch (IllegalArgumentException e) {
            LOG.warning("Explosion を変換できなかったため素通しします: " + e.getMessage());
            return payload;
        }
    }
""",
"translateSetCreativeSlot773To774(byte[] payload)": """    /** Set Creative Slot（773 → 774）。770・771・772・773・774とも UntrustedSlot（成分は ByteArray の生バイト）。成分の中身を773→774に変換する。 */
    public static byte[] translateSetCreativeSlot773To774(byte[] payload) {
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
            writer.writeVarInt(RegistryRemapper.mapItem773To774(reader.readVarInt()));
            int added = reader.readVarInt();
            int removed = reader.readVarInt();
            Writer addedOut = new Writer();
            int keptAdded = 0;
            for (int i = 0; i < added; i++) {
                int kind773 = reader.readVarInt();
                byte[] data = reader.readByteArray();
                Writer component = new Writer();
                if (!translateComponent773To774(new Reader(data), component, kind773)) {
                    continue;
                }
                addedOut.writeVarInt(mapComponentKind773To774(kind773));
                addedOut.writeByteArray(component.bytes());
                keptAdded++;
            }
            Writer removedOut = new Writer();
            int keptRemoved = 0;
            for (int i = 0; i < removed; i++) {
                int kind774 = mapComponentKind773To774(reader.readVarInt());
                if (kind774 < 0) {
                    continue;
                }
                removedOut.writeVarInt(kind774);
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
"translateSetCreativeSlot774To773(byte[] payload)": """    /** Set Creative Slot（774 → 773）。770・771・772・773・774とも UntrustedSlot。成分の中身を774→773に変換する。 */
    public static byte[] translateSetCreativeSlot774To773(byte[] payload) {
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
            writer.writeVarInt(RegistryRemapper.mapItemUp774(reader.readVarInt()));
            int added = reader.readVarInt();
            int removed = reader.readVarInt();
            Writer addedOut = new Writer();
            int keptAdded = 0;
            for (int i = 0; i < added; i++) {
                int kind774 = reader.readVarInt();
                byte[] data = reader.readByteArray();
                Writer component = new Writer();
                if (!translateComponent774To773(new Reader(data), component, kind774)) {
                    continue;
                }
                addedOut.writeVarInt(mapComponentKind774To773(kind774));
                addedOut.writeByteArray(component.bytes());
                keptAdded++;
            }
            Writer removedOut = new Writer();
            int keptRemoved = 0;
            for (int i = 0; i < removed; i++) {
                int kind773 = mapComponentKind774To773(reader.readVarInt());
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
})

# ----------------------------------------------------------------------
# PlayTranslator 774 節（手書き）
# ----------------------------------------------------------------------

PLAY_774_SECTION = """
    // 773 ⇔ 774（1.21.11）
    // Join Game／Respawn／Synchronize Player Position／Login Success／
    // Client Information／Player Info／Set Held Item／Vehicle Move／
    // Player Chat は773と774で形式が同一のため変換器は要らない（素通し）。
    // 変わるのは Chat Message／Chat Command Signed（末尾の checksum u8 は
    // 770・771・772・773・774ともある。番号は 773→774 で変わらない。checksum の中身は
    // 773と同じ扱い）、Registry Data（レジストリの順序。1.21.11での変更は
    // アイテム・音・エンティティの追加と、エンチャント lunge（21）の挿入・
    // ダメージ種別 spear（37）の挿入。ブロック状態・バイオーム・パーティクル・
    // 絵画・ジュークボックス・属性は同一。minecraft:dialog は771からあるため通す）
    // だけである。なお Registry Data の entity_type・sound・item・particle は
    // 773節と同じく素通し（順序は付け替えない。Spawn Entity の種別 ID だけ
    // 翻訳するため 773 と同じずれが残る。制限事項として記録する）。
    // ------------------------------------------------------------------

    /** Player Info（773 → 774）。形式は同一のため素通しする。 */
    public static byte[] translatePlayerInfo773To774(byte[] payload) {
        return payload;
    }

    /** Player Info（774 → 773）。形式は同一のため素通しする。 */
    public static byte[] translatePlayerInfo774To773(byte[] payload) {
        return payload;
    }

    /** Set Held Item（773 → 774）。形式は同一のため素通しする（varint のまま）。 */
    public static byte[] translateSetHeldItem773To774(byte[] payload) {
        return payload;
    }

    /** Set Held Item（774 → 773）。形式は同一のため素通しする。 */
    public static byte[] translateSetHeldItem774To773(byte[] payload) {
        return payload;
    }

    /** Vehicle Move（773 → 774）。形式は同一のため素通しする（末尾の onGround 付き）。 */
    public static byte[] translateVehicleMove773To774(byte[] payload) {
        return payload;
    }

    /** Vehicle Move（774 → 773）。形式は同一のため素通しする。 */
    public static byte[] translateVehicleMove774To773(byte[] payload) {
        return payload;
    }

    /** Player Chat（773 → 774）。先頭の globalIndex は770・771・772・773・774ともあるため素通しする。 */
    public static byte[] translatePlayerChat773To774(byte[] payload) {
        return payload;
    }

    /** Player Chat（774 → 773）。先頭の globalIndex は770・771・772・773・774ともあるため素通しする。 */
    public static byte[] translatePlayerChat774To773(byte[] payload) {
        return payload;
    }

    /**
     * Chat Message／Chat Command Signed（774 → 773）。末尾の checksum
     * （u8 1バイト）を取り除く。ペイロードが空の場合はそのまま返す。
     * （注意：現行の ProxySession は checksum を両版ともあるものとして
     * 素通しするため、このメソッドは未使用。773節の同名メソッドと同じ扱い。）
     */
    public static byte[] stripChatChecksum774(byte[] payload) {
        if (payload.length == 0) {
            return payload;
        }
        return new Reader(payload).readBytes(payload.length - 1);
    }

    /**
     * Chat Message／Chat Command Signed（773 → 774）。末尾に checksum
     * （u8 1バイト）を追加する。checksum の式は未確認のため 0x00 を入れる
     * （実機での確認は未実施。計算式そのものの確認も未実施）。
     * （注意：現行の ProxySession は checksum を両版ともあるものとして
     * 素通しするため、このメソッドは未使用。773節の同名メソッドと同じ扱い。）
     */
    public static byte[] appendChatChecksum774(byte[] payload) {
        Writer writer = new Writer();
        writer.writeBytes(payload);
        writer.writeByte(0);
        return writer.bytes();
    }

    /**
     * 773の Registry Data を774形式にする。minecraft:dialog は773から
     * あるためそのまま通す。並べ替えるのは enchantment（lunge を21に挿入）
     * と damage_type（spear を37に挿入）のみ（reorderRegistries）。
     * jukebox_song・絵画は773・774で同一。entity_type・sound・item・
     * particle は素通し（773節と同じ）。それ以外は1レジストリ分のペイロードを返す。
     */
    public static byte[] translateRegistryData773To774(byte[] payload) {
        Map<String, Tag.CompoundTag> reordered = reorderRegistries(
                parseRegistryData(payload),
                ProtocolVersion.V1_21_9, ProtocolVersion.V1_21_11);
        List<byte[]> built = buildRegistryData766(reordered);
        if (built.isEmpty()) {
            throw new IllegalArgumentException("Registry Data が空です。");
        }
        return built.get(0);
    }

    /**
     * 774の Registry Data を773形式にする。minecraft:dialog は773にも
     * あるため通す（reorderRegistries）。enchantment は lunge（774=21）を落とし、
     * damage_type は spear（774=37）を落として後ろを-1する。jukebox_song・
     * 絵画は773・774で同一。entity_type・sound・item・particle は素通し
     * （773節と同じ）。それ以外は1レジストリ分のペイロードを返す。
     * 空になった場合は {@code null} を返す。
     */
    public static byte[] translateRegistryData774To773(byte[] payload) {
        Map<String, Tag.CompoundTag> reordered = reorderRegistries(
                parseRegistryData(payload),
                ProtocolVersion.V1_21_11, ProtocolVersion.V1_21_9);
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
        CT, "// 772 ⇔ 773（1.21.9／1.21.10）")
    print(f"chunk section: lines {cs+1}-{ce+1} ({len(chunk_sec)} chars)")
    Path("/tmp/chunk773_section.txt").write_text(chunk_sec, encoding="utf-8")
    chunk_shifted = shift(chunk_sec)
    Path("/tmp/chunk774_shifted.txt").write_text(chunk_shifted, encoding="utf-8")
    print(f"chunk shifted: {len(chunk_shifted)} chars")

    # --- ChunkTranslator 774 節 ---
    chunk774 = apply_fixes(chunk_shifted, CHUNK_STRING_FIXES, "chunk-string")
    for sig, new_text in CHUNK_METHOD_REPLACEMENTS.items():
        chunk774 = replace_method(chunk774, sig, new_text, "chunk")
    for sig in DELETE_METHODS:
        chunk774 = delete_method(chunk774, sig, "chunk-delete")
    chunk774 = chunk774.rstrip() + "\n" + EXTRA_METHODS_774
    print(f"chunk774: {len(chunk774)} chars, "
          f"{chunk774.count('translate')} translate occurrences")
    insert_section_before_class_close(
        CT, chunk774, "chunk", "translateSlot773To774")

    # --- PlayTranslator 774 節（手書き） ---
    insert_section_before_class_close(
        PT, PLAY_774_SECTION, "play", "translatePlayerChat773To774")

    # --- reorderRegistries の jukebox_song フィルタ：774 も残す ---
    # （実コードには V1_21_2 の条件もある。旧 fix は落としていた。）
    pt_text = PT.read_text(encoding="utf-8")
    pt_text = fix(pt_text,
                  """            if (registryId.equals("minecraft:jukebox_song")
                    && toProtocol != ProtocolVersion.V1_21
                    && toProtocol != ProtocolVersion.V1_21_2
                    && toProtocol != ProtocolVersion.V1_21_4
                    && toProtocol != ProtocolVersion.V1_21_5
                    && toProtocol != ProtocolVersion.V1_21_6
                    && toProtocol != ProtocolVersion.V1_21_7
                    && toProtocol != ProtocolVersion.V1_21_9) {""",
                  """            if (registryId.equals("minecraft:jukebox_song")
                    && toProtocol != ProtocolVersion.V1_21
                    && toProtocol != ProtocolVersion.V1_21_2
                    && toProtocol != ProtocolVersion.V1_21_4
                    && toProtocol != ProtocolVersion.V1_21_5
                    && toProtocol != ProtocolVersion.V1_21_6
                    && toProtocol != ProtocolVersion.V1_21_7
                    && toProtocol != ProtocolVersion.V1_21_9
                    && toProtocol != ProtocolVersion.V1_21_11) {""",
                  "play-jukebox")
    # minecraft:dialog は771・772・773・774にしか無いため、771・772・773・774以外向けは落とす。
    # （既存のフィルタを拡張する。別フィルタを追加すると二重に落とす。）
    pt_text = fix(pt_text,
                  """            if (registryId.equals("minecraft:dialog")
                    && toProtocol != ProtocolVersion.V1_21_6
                    && toProtocol != ProtocolVersion.V1_21_7
                    && toProtocol != ProtocolVersion.V1_21_9) {""",
                  """            if (registryId.equals("minecraft:dialog")
                    && toProtocol != ProtocolVersion.V1_21_6
                    && toProtocol != ProtocolVersion.V1_21_7
                    && toProtocol != ProtocolVersion.V1_21_9
                    && toProtocol != ProtocolVersion.V1_21_11) {""",
                  "play-dialog")
    # jukebox_song の並べ替え：773↔774 の対応表を追加する。
    # （773・774で曲は同一のため対応表は実質そのままだが、Stage 2 で
    #  生成済みの mapJukeboxSong773To774／774To773 を通す。）
    pt_text = fix(pt_text,
                  """            case "minecraft:jukebox_song" -> switch (from * 1000 + to) {
                case 770771 -> RegistryRemapper::mapJukeboxSong770To771;
                case 771770 -> RegistryRemapper::mapJukeboxSong771To770;
                case 771772 -> RegistryRemapper::mapJukeboxSong771To772;
                case 772771 -> RegistryRemapper::mapJukeboxSong772To771;
                case 772773 -> RegistryRemapper::mapJukeboxSong772To773;
                case 773772 -> RegistryRemapper::mapJukeboxSong773To772;
                default -> null;
            };""",
                  """            case "minecraft:jukebox_song" -> switch (from * 1000 + to) {
                case 770771 -> RegistryRemapper::mapJukeboxSong770To771;
                case 771770 -> RegistryRemapper::mapJukeboxSong771To770;
                case 771772 -> RegistryRemapper::mapJukeboxSong771To772;
                case 772771 -> RegistryRemapper::mapJukeboxSong772To771;
                case 772773 -> RegistryRemapper::mapJukeboxSong772To773;
                case 773772 -> RegistryRemapper::mapJukeboxSong773To772;
                case 773774 -> RegistryRemapper::mapJukeboxSong773To774;
                case 774773 -> RegistryRemapper::mapJukeboxSong774To773;
                default -> null;
            };""",
                  "play-jukebox-mapper")
    # jukebox_song の targetNames：774 が無いと reorderRegistryEntries で
    # targetNames が null になり NPE になる（773・774で曲は同一のため
    # 773 の表を使う。PlayTranslator774Test で発見：2026-11-12）。
    pt_text = fix(pt_text,
                  """            case "minecraft:jukebox_song" -> switch (to) {
                case 773 -> RegistryTables773.JUKEBOX_SONG_773_NAMES;""",
                  """            case "minecraft:jukebox_song" -> switch (to) {
                case 774 -> RegistryTables773.JUKEBOX_SONG_773_NAMES; // 773と同一の21曲
                case 773 -> RegistryTables773.JUKEBOX_SONG_773_NAMES;""",
                  "play-jukebox-target-names")
    # enchantment の並べ替え：lunge（774=21）の挿入で773→774は+1ずれる。
    # 768→773は同一のため既存の mapper は768までしか無く、773774／774773
    # が無いと reorderRegistryEntries が素通ししてしまう。
    pt_text = fix(pt_text,
                  """                case 766768 -> RegistryRemapper::mapEnchantment766To768;
                case 768766 -> RegistryRemapper::mapEnchantment768To766;
                default -> null;
            };
            case "minecraft:painting_variant" -> switch (from * 1000 + to) {""",
                  """                case 766768 -> RegistryRemapper::mapEnchantment766To768;
                case 768766 -> RegistryRemapper::mapEnchantment768To766;
                case 773774 -> RegistryRemapper::mapEnchantment773To774;
                case 774773 -> RegistryRemapper::mapEnchantment774To773;
                default -> null;
            };
            case "minecraft:painting_variant" -> switch (from * 1000 + to) {""",
                  "play-enchantment-mapper")
    # damage_type の並べ替え：spear（774=37）の挿入で773→774は+1ずれる。
    pt_text = fix(pt_text,
                  """                case 766768 -> RegistryRemapper::mapDamageType766To768;
                case 768766 -> RegistryRemapper::mapDamageType768To766;
                default -> null;
            };
            default -> null;
        };
    }""",
                  """                case 766768 -> RegistryRemapper::mapDamageType766To768;
                case 768766 -> RegistryRemapper::mapDamageType768To766;
                case 773774 -> RegistryRemapper::mapDamageType773To774;
                case 774773 -> RegistryRemapper::mapDamageType774To773;
                default -> null;
            };
            default -> null;
        };
    }""",
                  "play-damage-mapper")
    # registryTargetNames：enchantment（770〜773は768の表、774は774の表）。
    # 774→773 の逆方向でも to=773 の表が要るため 770〜773 を明示する
    # （無いと default -> null で NPE になる）。
    pt_text = fix(pt_text,
                  """            case "minecraft:enchantment" -> switch (to) {
                case 765 -> ENCHANTMENT_765_NAMES;
                case 766 -> ENCHANTMENT_766_NAMES;
                case 767 -> ENCHANTMENT_767_NAMES;
                case 768, 769 -> ENCHANTMENT_768_NAMES;
                default -> null;
            };""",
                  """            case "minecraft:enchantment" -> switch (to) {
                case 765 -> ENCHANTMENT_765_NAMES;
                case 766 -> ENCHANTMENT_766_NAMES;
                case 767 -> ENCHANTMENT_767_NAMES;
                case 768, 769, 770, 771, 772, 773 -> ENCHANTMENT_768_NAMES;
                case 774 -> ENCHANTMENT_774_NAMES;
                default -> null;
            };""",
                  "play-enchantment-targets")
    # registryTargetNames：damage_type（770〜773は768の表、774は774の表）。
    # default は DAMAGE_766_NAMES（48件）のため、770〜774を明示しないと
    # 並べ替えで範囲外（774は50件）になって例外になる。
    pt_text = fix(pt_text,
                  """            case "minecraft:damage_type" -> switch (to) {
                case 767 -> DAMAGE_767_NAMES;
                case 768, 769 -> DAMAGE_768_NAMES;
                default -> DAMAGE_766_NAMES;
            };""",
                  """            case "minecraft:damage_type" -> switch (to) {
                case 767 -> DAMAGE_767_NAMES;
                case 768, 769, 770, 771, 772, 773 -> DAMAGE_768_NAMES;
                case 774 -> DAMAGE_774_NAMES;
                default -> DAMAGE_766_NAMES;
            };""",
                  "play-damage-targets")
    # ENCHANTMENT_774_NAMES（43件。lunge を21に挿入。
    # enchantments-1.21.11.json で検証済み）。
    pt_text = fix(pt_text,
                  """            "minecraft:sweeping_edge", "minecraft:swift_sneak", "minecraft:thorns", "minecraft:unbreaking", "minecraft:vanishing_curse", "minecraft:wind_burst",
    };

    // 766（1.20.5）の絵画は30件のコード登録順。""",
                  """            "minecraft:sweeping_edge", "minecraft:swift_sneak", "minecraft:thorns", "minecraft:unbreaking", "minecraft:vanishing_curse", "minecraft:wind_burst",
    };

    /** 774（1.21.11）のエンチャント名。768の42件に lunge（21）を加えた43件。 */
    private static final String[] ENCHANTMENT_774_NAMES = {
            "minecraft:aqua_affinity", "minecraft:bane_of_arthropods", "minecraft:binding_curse", "minecraft:blast_protection", "minecraft:breach", "minecraft:channeling",
            "minecraft:density", "minecraft:depth_strider", "minecraft:efficiency", "minecraft:feather_falling", "minecraft:fire_aspect", "minecraft:fire_protection",
            "minecraft:flame", "minecraft:fortune", "minecraft:frost_walker", "minecraft:impaling", "minecraft:infinity", "minecraft:knockback",
            "minecraft:looting", "minecraft:loyalty", "minecraft:luck_of_the_sea", "minecraft:lunge", "minecraft:lure", "minecraft:mending",
            "minecraft:multishot", "minecraft:piercing", "minecraft:power", "minecraft:projectile_protection", "minecraft:protection", "minecraft:punch",
            "minecraft:quick_charge", "minecraft:respiration", "minecraft:riptide", "minecraft:sharpness", "minecraft:silk_touch", "minecraft:smite",
            "minecraft:soul_speed", "minecraft:sweeping_edge", "minecraft:swift_sneak", "minecraft:thorns", "minecraft:unbreaking", "minecraft:vanishing_curse",
            "minecraft:wind_burst",
    };

    // 766（1.20.5）の絵画は30件のコード登録順。""",
                  "play-enchantment-774-names")
    # DAMAGE_774_NAMES（50件。spear を37に挿入。RegistryRemapper の
    # DAMAGE_TYPE_774_MAP で検証済み）。
    pt_text = fix(pt_text,
                  """            "minecraft:thorns", "minecraft:thrown", "minecraft:trident", "minecraft:unattributed_fireball", "minecraft:wind_charge", "minecraft:wither",
            "minecraft:wither_skull",
    };

    /**
     * レジストリの順序が変わった組み合わせで、送信側の数値 id から""",
                  """            "minecraft:thorns", "minecraft:thrown", "minecraft:trident", "minecraft:unattributed_fireball", "minecraft:wind_charge", "minecraft:wither",
            "minecraft:wither_skull",
    };

    /** 774（1.21.11）のダメージ種別名。768の49件に spear（37）を加えた50件。 */
    private static final String[] DAMAGE_774_NAMES = {
            "minecraft:arrow", "minecraft:bad_respawn_point", "minecraft:cactus", "minecraft:campfire", "minecraft:cramming", "minecraft:dragon_breath",
            "minecraft:drown", "minecraft:dry_out", "minecraft:ender_pearl", "minecraft:explosion", "minecraft:fall", "minecraft:falling_anvil",
            "minecraft:falling_block", "minecraft:falling_stalactite", "minecraft:fireball", "minecraft:fireworks", "minecraft:fly_into_wall", "minecraft:freeze",
            "minecraft:generic", "minecraft:generic_kill", "minecraft:hot_floor", "minecraft:in_fire", "minecraft:in_wall", "minecraft:indirect_magic",
            "minecraft:lava", "minecraft:lightning_bolt", "minecraft:mace_smash", "minecraft:magic", "minecraft:mob_attack", "minecraft:mob_attack_no_aggro",
            "minecraft:mob_projectile", "minecraft:on_fire", "minecraft:out_of_world", "minecraft:outside_border", "minecraft:player_attack", "minecraft:player_explosion",
            "minecraft:sonic_boom", "minecraft:spear", "minecraft:spit", "minecraft:stalagmite", "minecraft:starve", "minecraft:sting",
            "minecraft:sweet_berry_bush", "minecraft:thorns", "minecraft:thrown", "minecraft:trident", "minecraft:unattributed_fireball", "minecraft:wind_charge",
            "minecraft:wither", "minecraft:wither_skull",
    };

    /**
     * レジストリの順序が変わった組み合わせで、送信側の数値 id から""",
                  "play-damage-774-names")
    PT.write_text(pt_text, encoding="utf-8")
    print("[play-registry] OK")
    print("完了。次は :detergentmc-protocol:compileJava で確認すること。")


if __name__ == "__main__":
    main()
