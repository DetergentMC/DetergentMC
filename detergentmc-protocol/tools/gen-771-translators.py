#!/usr/bin/env python3
"""ChunkTranslator / PlayTranslator の 770 節から 771 節を生成する。

方針: 770 節を抜き出して shift()（gen-771-proxysession.py のもの）で
1段ずらし、FIXES（手書きの置き換え）を当て、ファイル末尾の最終 `}` の
直前に挿入する。PlayTranslator の 771 節は手書きブロックで作る
（Player Info 等は770と771で同一形式のため素通しにする）。

770→771 で shift だけでは足りない点（FIXES で直す）:
- 成分種別は770・771とも96件・同じ番号のため KIND 表は恒等表になる。
- 769→770 で形式が変わった成分（unbreakable の bool→void、
  dyed_color の showTooltip 削除、enchantments／attribute_modifiers／
  trim／tool／item_block_predicates の末尾 bool の削除・追加、
  instrument のコンテナ化、jukebox_playable の showInTooltip 削除、
  hide_tooltip→tooltip_display 統合）は770・771で同形式のため、
  shift したヘルパーは誤って769形式を読み書きする。手書きで直す。
- チャンクの heightmap・paletted container は770・771とも直接配列
  （長さ varint 接頭辞なし）のため、769形式との変換をする
  shift 版は使えない。手書きで直す。
- パーティクルは770・771で同一（tinted_leaves／firefly も両方にある）
  ため、770→769 の落としは要らない。メタデータの型番号も同一。
- Explosion の knockback は770・771とも f64×3。
- jukebox_playable の曲IDは tears（771=17）の挿入で+1ずれる。
- Set Creative Slot は770・771とも UntrustedSlot。
- Spawn Entity：happy_ghast（771=56）は770では ghast（55）で代用する。
"""
import importlib.util
import re
import sys
from pathlib import Path

_HERE = Path(__file__).resolve().parent
_spec = importlib.util.spec_from_file_location("gen771ps", _HERE / "gen-771-proxysession.py")
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
    signature はメソッド名を含む一意の文字列（例: "translateSlot769To770(Reader"）。"""
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


# 成分種別の対応表（770 kind -> 771 kind。770・771とも96件・同じ番号のため恒等）。
KIND_770_TO_771 = {i: i for i in range(96)}
KIND_771_TO_770 = {i: i for i in range(96)}
assert len(KIND_770_TO_771) == 96
assert len(KIND_771_TO_770) == 96


def java_int_array(name, values, per_line=16):
    lines = []
    for i in range(0, len(values), per_line):
        lines.append("            " + ", ".join(str(v) for v in values[i:i + per_line]) + ",")
    body = "\n".join(lines)
    return f"    private static final int[] {name} = {{\n{body}\n    }};\n"


KIND_770_TO_771_JAVA = java_int_array(
    "KIND_770_TO_771", [KIND_770_TO_771[i] for i in range(96)])
KIND_771_TO_770_JAVA = java_int_array(
    "KIND_771_TO_770",
    [KIND_771_TO_770.get(i, -1) for i in range(96)])

# 770節の KIND 表（shift 後の old として使う。値は gen-770 と同じ）。
KIND_769_TO_770 = {
    **{i: i for i in range(0, 15)},
    15: -1, 16: 15,
    17: 16, 18: 17, 19: 18, 20: 19, 21: 20, 22: 21, 23: 22, 24: 23, 25: 24, 26: 25,
    27: 27, 28: 28, 29: 29, 30: 30, 31: 31, 32: 32,
    33: 34, 34: 35, 35: 36, 36: 37, 37: 38, 38: 39, 39: 40, 40: 41, 41: 42,
    42: 44, 43: 45, 44: 46, 45: 47, 46: 48, 47: 49, 48: 50, 49: 51, 50: 52,
    51: 54, 52: 55, 53: 57, 54: 58, 55: 59, 56: 60, 57: 61, 58: 62, 59: 63,
    60: 64, 61: 65, 62: 66, 63: 67, 64: 68, 65: 69, 66: 70,
}
assert len(KIND_769_TO_770) == 67
KIND_770_TO_769 = {v: k for k, v in KIND_769_TO_770.items() if v >= 0}
assert len(KIND_770_TO_769) == 66  # 15（hide_additional_tooltip）が落ちる分

# shift 後の 771 節に残る（誤った）KIND 表のテキスト。置き換えの old に使う。
KIND_770_TO_771_SHIFTED_JAVA = java_int_array(
    "KIND_770_TO_771", [KIND_769_TO_770[i] for i in range(67)])
KIND_771_TO_770_SHIFTED_JAVA = java_int_array(
    "KIND_771_TO_770",
    [KIND_770_TO_769.get(i, -1) for i in range(96)])



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


# 770節のヘッダーコメント（shift 後の old を shift() で作る）。
HEADER_770 = """    // 769 ⇔ 770（1.21.5）
    // 成分種別は769が67件、770が96件。番号が大きくずれるため KIND 表
    // （KIND_769_TO_770／KIND_770_TO_769）で付け替える。
    // 変わるのは中身のレジストリID（アイテム・ブロック状態・
    // エンティティ・音・パーティクル）と成分の形式（tooltip_display への
    // 統合、showTooltip／showInTooltip の削除、tool の
    // canDestroyBlocksInCreative 追加等）。バイオーム・エンチャント・属性・
    // メニュー・ダメージ種別は同一のため付け替えない。エンティティは
    // potion（769=99）が splash_potion（770=99）と lingering_potion
    // （770=100）に分かれる。tinted_leaves（770=35）と firefly
    // （770=113）は769に無いため770→769では落とす。
    // チャンクの heightmap は NBT から直接の配列に変わった。"""

HEADER_771 = """    // 770 ⇔ 771（1.21.6）
    // 成分種別は770・771とも96件・同じ番号のため KIND 表は恒等表。
    // 変わるのは中身のレジストリID（アイテム・ブロック状態・
    // エンティティ・音・パーティクル）と成分の中身（jukebox_playable の
    // 曲ID、break_sound／blocks_attacks の音ID）。バイオーム・
    // エンチャント・属性・メニュー・ダメージ種別・パーティクルは同一の
    // ため付け替えない。エンティティは happy_ghast（771=56）が増える。
    // tinted_leaves（35）と firefly（113）は770・771ともある。
    // チャンクの heightmap は770・771とも直接の配列（長さ varint 無し）。"""

# 770節の KIND 表まわり（shift 後の old を shift() で作る）。
KIND_COUNT_770 = """    /** 成分種別の件数（770 は 96 件。769 は 67 件）。 */
    private static final int DATA_COMPONENT_TYPE_COUNT_770 = 96;"""

KIND_COUNT_771 = """    /** 成分種別の件数（770・771とも 96 件）。 */
    private static final int DATA_COMPONENT_TYPE_COUNT_771 = 96;"""

KIND_JAVADOC_1_770 = """    /**
     * 成分種別の対応表（769 kind → 770 kind）。-1 は 770 に無いため落とす
     * （15＝hide_additional_tooltip）。16（hide_tooltip）は 15
     * （tooltip_display）になる。
     */"""

KIND_JAVADOC_1_771 = """    /**
     * 成分種別の対応表（770 kind → 771 kind）。770・771とも96件・
     * 同じ番号のため恒等表（落とす種別は無い）。
     */"""

KIND_JAVADOC_2_770 = """    /**
     * 成分種別の対応表（770 kind → 769 kind）。-1 は 769 に無いため落とす。
     * 15（tooltip_display）は内容次第で hide_tooltip（16）か
     * hide_additional_tooltip（15）になるためここでは -1 にしてある。
     */"""

KIND_JAVADOC_2_771 = """    /**
     * 成分種別の対応表（771 kind → 770 kind）。770・771とも96件・
     * 同じ番号のため恒等表（落とす種別は無い）。
     */"""


# ----------------------------------------------------------------------
# ChunkTranslator 771 節の文字列 FIXES（シフト済みテキストに適用）
# ----------------------------------------------------------------------

CHUNK_STRING_FIXES = [
    # ヘッダーコメント。
    (shift(HEADER_770), HEADER_771),
    # 成分種別の件数＋KIND 表の恒等表化。
    (shift(KIND_COUNT_770) + "\n\n" + shift(KIND_JAVADOC_1_770) + "\n"
     + KIND_770_TO_771_SHIFTED_JAVA + "\n" + shift(KIND_JAVADOC_2_770) + "\n"
     + KIND_771_TO_770_SHIFTED_JAVA,
     KIND_COUNT_771 + "\n\n" + KIND_JAVADOC_1_771 + "\n"
     + KIND_770_TO_771_JAVA + "\n" + KIND_JAVADOC_2_771 + "\n"
     + KIND_771_TO_770_JAVA),
    # パーティクル定数のコメント：tinted_leaves／firefly は770にもある。
    ("""    /** 771の tinted_leaves 粒子のID（770には無い）。 */
    private static final int PARTICLE_771_TINTED_LEAVES = 35;
    /** 771の firefly 粒子のID（770には無い）。 */
    private static final int PARTICLE_771_FIREFLY = 113;""",
     """    /** 771の tinted_leaves 粒子のID（770・771とも 35）。 */
    private static final int PARTICLE_771_TINTED_LEAVES = 35;
    /** 771の firefly 粒子のID（770・771とも 113）。 */
    private static final int PARTICLE_771_FIREFLY = 113;"""),
    # パーティクル（770→771）の case 番号：770側のIDに合わせる。
    ("""            case 1, 2, 28, 107 -> // block、block_marker、falling_dust、dust_pillar：ブロック状態IDを付け替える""",
     """            case 1, 2, 28, 108 -> // block、block_marker、falling_dust、dust_pillar：ブロック状態IDを付け替える"""),
    ("""            case 36 -> writer.writeFloat(reader.readFloat()); // sculk_charge：回転（1個の float）
            case 45 -> translateSlot770To771(reader, writer); // item
            case 46 -> translateVibration(reader, writer); // vibration（同一形式）
            case 101 -> writer.writeVarInt(reader.readVarInt()); // shriek：遅延 tick 数
            case 47 -> { // trail：target vec3f64＋color i32＋duration（769・770・771ともある）""",
     """            case 37 -> writer.writeFloat(reader.readFloat()); // sculk_charge：回転（1個の float）
            case 46 -> translateSlot770To771(reader, writer); // item
            case 47 -> translateVibration(reader, writer); // vibration（同一形式）
            case 102 -> writer.writeVarInt(reader.readVarInt()); // shriek：遅延 tick 数
            case 48 -> { // trail：target vec3f64＋color i32＋duration（770・771ともある）"""),
    ("""            case 111 -> // block_crumble：ブロック状態IDを付け替える
                    writer.writeVarInt(RegistryRemapper.mapBlockState770To771(reader.readVarInt()));""",
     """            case 112 -> // block_crumble：ブロック状態IDを付け替える
                    writer.writeVarInt(RegistryRemapper.mapBlockState770To771(reader.readVarInt()));"""),
    # Spawn Entity：happy_ghast（771=56）は770では ghast（55）で代用する。
    ("""            int mappedType = RegistryRemapper.mapEntityTypeUp771(type);""",
     """            int mappedType = type == 56
                    ? 55 // happy_ghast（771にしか無い）は ghast で代用する
                    : RegistryRemapper.mapEntityTypeUp771(type);"""),
    # skipComponentData771：enchantment_glint_override（16）は bool 1個。
    ("""            case 4 -> { } // unbreakable（void）
            case 15 -> { // tooltip_display""",
     """            case 4 -> { } // unbreakable（void）
            case 16 -> reader.readBoolean(); // enchantment_glint_override（bool）
            case 15 -> { // tooltip_display"""),
    # ボート variant のコメント：creaking_transient 削除は769→770の話。
    ("""     * 771の種別を770に直してから770の表を使う（ボート類は
     * creaking_transient 削除で770→771で1つずれている）。""",
     """     * 771の種別を770に直してから770の表を使う（ボート類は
     * happy_ghast 挿入で56番以降が1つずれるが、対応表で吸収する）。"""),
]


# 削除するメソッド（shift 版では不要・誤りのため。replace 後に削除する）。
DELETE_METHODS = [
    # hide_tooltip は770に無い（769→770の変換用だった）。
    "translateHideTooltip770To771(Reader reader, Writer writer)",
    # trim の材料／型の769用 ID 合成（770・771の直書きには無い）。
    "trimMaterialIngredient770(String assetBase)",
    "trimPatternTemplate770(String assetId)",
    # Heightmap の NBT⇔配列の変換（770・771とも直接配列のため不要）。
    "writeHeightmaps771(Writer writer, Tag tag)",
    "readHeightmaps771ToNbt(Reader reader)",
]


# ----------------------------------------------------------------------
# ChunkTranslator 771 節のメソッド置き換え
# ----------------------------------------------------------------------

CHUNK_METHOD_REPLACEMENTS = {
"translateComponent770To771(Reader reader, Writer writer, int kind770)": """    /**
     * 成分1個を770→771向けに変換する。770・771とも96件・同じ番号・
     * 同じ形式のため種別はそのまま。レジストリIDを付け替えるものだけ
     * 変換する。落とす成分は無い（{@code false} を返すことは無いが、
     * 呼び出し側の規約に合わせる）。
     */
    private static boolean translateComponent770To771(Reader reader, Writer writer, int kind770) {
        switch (kind770) {
            case 4 -> { } // unbreakable（void。770・771とも中身なし）
            case 10, 34 -> translateEnchantments770To771(reader, writer);
            case 11, 12 -> translateItemBlockPredicates770To771(reader, writer);
            case 13 -> translateAttributeModifiers770To771(reader, writer);
            case 14 -> translateCustomModelData770To771(reader, writer);
            case 15 -> translateTooltipDisplay770To771(reader, writer);
            case 16 -> writer.writeBoolean(reader.readBoolean()); // enchantment_glint_override
            case 20 -> translateFood770To771(reader, writer);
            case 21 -> translateConsumable770To771(reader, writer);
            case 22 -> translateUseRemainder770To771(reader, writer);
            case 25 -> translateTool770To771(reader, writer);
            case 26 -> { // weapon：varint＋float（レジストリ無し）
                writer.writeVarInt(reader.readVarInt());
                writer.writeFloat(reader.readFloat());
            }
            case 28 -> translateEquippable770To771(reader, writer);
            case 29 -> translateRepairable770To771(reader, writer);
            case 32 -> translateDeathProtection770To771(reader, writer);
            case 33 -> translateBlocksAttacks770To771(reader, writer);
            case 35 -> translateDyedColor770To771(reader, writer);
            case 40, 41, 66 -> translateSlotArray770To771(reader, writer);
            case 43 -> writer.writeFloat(reader.readFloat()); // potion_duration_scale
            case 47 -> {
                return translateTrim770To771(reader, writer);
            }
            case 52 -> {
                return translateInstrument770To771(reader, writer);
            }
            case 53 -> copyProvidesTrimMaterial770To771(reader, writer);
            case 55 -> translateJukeboxPlayable770To771(reader, writer);
            case 56 -> writer.writeString(reader.readString()); // provides_banner_patterns
            case 62 -> translateNoteBlockSound770To771(reader, writer);
            case 63 -> translateBannerPatterns770To771(reader, writer);
            case 65 -> translatePotDecorations770To771(reader, writer);
            case 71 -> translateBreakSound770To771(reader, writer);
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
            case 89 -> copyPaintingVariantHolder770To771(reader, writer);
            default -> copyComponentOpaque771(reader, writer, kind770);
        }
        return true;
    }
""",
"translateComponent771To770(Reader reader, Writer writer, int kind771)": """    /**
     * 成分1個を771→770向けに変換する。770・771とも96件・同じ番号・
     * 同じ形式のため種別はそのまま。レジストリIDを付け替えるものだけ
     * 変換する。770に無い種別は無いため、落とす成分は無い
     * （{@code false} を返すことは無いが、呼び出し側の規約に合わせる）。
     */
    private static boolean translateComponent771To770(Reader reader, Writer writer, int kind771) {
        switch (kind771) {
            case 4 -> { } // unbreakable（void。770・771とも中身なし）
            case 10, 34 -> translateEnchantments771To770(reader, writer);
            case 11, 12 -> translateItemBlockPredicates771To770(reader, writer);
            case 13 -> translateAttributeModifiers771To770(reader, writer);
            case 14 -> translateCustomModelData771To770(reader, writer);
            case 15 -> translateTooltipDisplay771To770(reader, writer);
            case 16 -> writer.writeBoolean(reader.readBoolean()); // enchantment_glint_override
            case 20 -> translateFood771To770(reader, writer);
            case 21 -> translateConsumable771To770(reader, writer);
            case 22 -> translateUseRemainder771To770(reader, writer);
            case 25 -> translateTool771To770(reader, writer);
            case 26 -> { // weapon：varint＋float（レジストリ無し）
                writer.writeVarInt(reader.readVarInt());
                writer.writeFloat(reader.readFloat());
            }
            case 28 -> translateEquippable771To770(reader, writer);
            case 29 -> translateRepairable771To770(reader, writer);
            case 32 -> translateDeathProtection771To770(reader, writer);
            case 33 -> translateBlocksAttacks771To770(reader, writer);
            case 35 -> translateDyedColor771To770(reader, writer);
            case 40, 41, 66 -> translateSlotArray771To770(reader, writer);
            case 43 -> writer.writeFloat(reader.readFloat()); // potion_duration_scale
            case 47 -> {
                return translateTrim771To770(reader, writer);
            }
            case 52 -> {
                return translateInstrument771To770(reader, writer);
            }
            case 53 -> copyProvidesTrimMaterial771To770(reader, writer);
            case 55 -> translateJukeboxPlayable771To770(reader, writer);
            case 56 -> writer.writeString(reader.readString()); // provides_banner_patterns
            case 62 -> translateNoteBlockSound771To770(reader, writer);
            case 63 -> translateBannerPatterns771To770(reader, writer);
            case 65 -> translatePotDecorations771To770(reader, writer);
            case 71 -> translateBreakSound771To770(reader, writer);
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
            case 89 -> copyPaintingVariantHolder771To770(reader, writer);
            default -> copyComponentOpaque771(reader, writer, kind771);
        }
        return true;
    }
""",
"translateSlotComponents771To770(Reader reader, Writer writer)": """    /** スロットの added／removed 成分部分だけを771→770向けに変換する。770・771で種別番号は同じため、tooltip_display（15）の特別扱いは要らない。 */
    private static void translateSlotComponents771To770(Reader reader, Writer writer) {
        int added = reader.readVarInt();
        Writer kept = new Writer();
        int keptCount = 0;
        for (int i = 0; i < added; i++) {
            int kind771 = reader.readVarInt();
            int kind770 = mapComponentKind771To770(kind771);
            if (kind770 < 0) {
                skipComponentData771(reader, kind771);
                continue;
            }
            Writer component = new Writer();
            if (!translateComponent771To770(reader, component, kind771)) {
                continue;
            }
            kept.writeVarInt(kind770);
            kept.writeBytes(component.bytes());
            keptCount++;
        }
        writer.writeVarInt(keptCount);
        writer.writeBytes(kept.bytes());
        int removed = reader.readVarInt();
        Writer keptRemoved = new Writer();
        int keptRemovedCount = 0;
        for (int i = 0; i < removed; i++) {
            int kind770 = mapComponentKind771To770(reader.readVarInt());
            if (kind770 < 0) {
                continue;
            }
            keptRemoved.writeVarInt(kind770);
            keptRemovedCount++;
        }
        writer.writeVarInt(keptRemovedCount);
        writer.writeBytes(keptRemoved.bytes());
    }
""",
"translateComponentList771To770(Reader reader, Writer writer)": """    /** 成分の列（個数 VarInt＋成分1個ずつ）を771→770向けに変換する。770・771で種別番号は同じため、tooltip_display（15）の特別扱いは要らない。 */
    private static void translateComponentList771To770(Reader reader, Writer writer) {
        int added = reader.readVarInt();
        Writer kept = new Writer();
        int keptCount = 0;
        for (int i = 0; i < added; i++) {
            int kind771 = reader.readVarInt();
            int kind770 = mapComponentKind771To770(kind771);
            if (kind770 < 0) {
                skipComponentData771(reader, kind771);
                continue;
            }
            Writer component = new Writer();
            if (!translateComponent771To770(reader, component, kind771)) {
                continue;
            }
            kept.writeVarInt(kind770);
            kept.writeBytes(component.bytes());
            keptCount++;
        }
        writer.writeVarInt(keptCount);
        writer.writeBytes(kept.bytes());
    }
""",
"translateTooltipDisplay771To770(Reader reader)": """    /** tooltip_display（771→770）。770・771とも {hideTooltip bool, hiddenComponents の列} のためそのまま写す。 */
    private static void translateTooltipDisplay771To770(Reader reader, Writer writer) {
        writer.writeBoolean(reader.readBoolean()); // hideTooltip
        int hidden = reader.readVarInt();
        writer.writeVarInt(hidden);
        for (int i = 0; i < hidden; i++) {
            writer.writeVarInt(reader.readVarInt());
        }
    }
""",
"translateUnbreakable770To771(Reader reader, Writer writer)": """    /** unbreakable を770→771向けに変換する。770・771とも void のため何もしない。 */
    private static void translateUnbreakable770To771(Reader reader, Writer writer) {
    }
""",
"translateUnbreakable771To770(Reader reader, Writer writer)": """    /** unbreakable を771→770向けに変換する。770・771とも void のため何もしない。 */
    private static void translateUnbreakable771To770(Reader reader, Writer writer) {
    }
""",
"translateDyedColor770To771(Reader reader, Writer writer)": """    /** dyed_color を770→771向けに変換する。770・771とも色 i32 だけ。 */
    private static void translateDyedColor770To771(Reader reader, Writer writer) {
        writer.writeInt(reader.readInt()); // color
    }
""",
"translateDyedColor771To770(Reader reader, Writer writer)": """    /** dyed_color を771→770向けに変換する。770・771とも色 i32 だけ。 */
    private static void translateDyedColor771To770(Reader reader, Writer writer) {
        writer.writeInt(reader.readInt()); // color
    }
""",
}


# 771節に新しく追加するヘルパー（shift 版には無いもの）。
# 置き換えの後に節の末尾へ追加する。
EXTRA_METHODS_771 = """
    /** tooltip_display（770→771）。770・771とも {hideTooltip bool, hiddenComponents の列} のためそのまま写す。 */
    private static void translateTooltipDisplay770To771(Reader reader, Writer writer) {
        writer.writeBoolean(reader.readBoolean()); // hideTooltip
        int hidden = reader.readVarInt();
        writer.writeVarInt(hidden);
        for (int i = 0; i < hidden; i++) {
            writer.writeVarInt(reader.readVarInt());
        }
    }

    /** break_sound（770→771）。音の Holder の音IDを付け替える。 */
    private static void translateBreakSound770To771(Reader reader, Writer writer) {
        translateSoundHolder770To771(reader, writer);
    }

    /** break_sound（771→770）。音の Holder の音IDを付け替える。 */
    private static void translateBreakSound771To770(Reader reader, Writer writer) {
        translateSoundHolder771To770(reader, writer);
    }

    /** blocks_attacks（770→771）。中の音の Holder だけ付け替える。type の IDSet はダメージ種別で、770・771とも49種・同じ並びのためそのまま写す。 */
    private static void translateBlocksAttacks770To771(Reader reader, Writer writer) {
        writer.writeFloat(reader.readFloat()); // blockDelaySeconds
        writer.writeFloat(reader.readFloat()); // disableCooldownScale
        int reductions = reader.readVarInt();
        writer.writeVarInt(reductions);
        for (int i = 0; i < reductions; i++) {
            writer.writeFloat(reader.readFloat()); // horizontalBlockingAngle
            if (reader.readBoolean()) { // type（option IDSet）
                writer.writeBoolean(true);
                copyIdSet(reader, writer);
            } else {
                writer.writeBoolean(false);
            }
            writer.writeFloat(reader.readFloat()); // base
            writer.writeFloat(reader.readFloat()); // factor
        }
        writer.writeFloat(reader.readFloat()); // itemDamage.threshold
        writer.writeFloat(reader.readFloat()); // itemDamage.base
        writer.writeFloat(reader.readFloat()); // itemDamage.factor
        if (reader.readBoolean()) { // bypassedBy（option string）
            writer.writeBoolean(true);
            writer.writeString(reader.readString());
        } else {
            writer.writeBoolean(false);
        }
        if (reader.readBoolean()) { // blockSound（option ItemSoundHolder）
            writer.writeBoolean(true);
            translateSoundHolder770To771(reader, writer);
        } else {
            writer.writeBoolean(false);
        }
        if (reader.readBoolean()) { // disableSound（option ItemSoundHolder）
            writer.writeBoolean(true);
            translateSoundHolder770To771(reader, writer);
        } else {
            writer.writeBoolean(false);
        }
    }

    /** blocks_attacks（771→770）。中の音の Holder だけ付け替える。 */
    private static void translateBlocksAttacks771To770(Reader reader, Writer writer) {
        writer.writeFloat(reader.readFloat()); // blockDelaySeconds
        writer.writeFloat(reader.readFloat()); // disableCooldownScale
        int reductions = reader.readVarInt();
        writer.writeVarInt(reductions);
        for (int i = 0; i < reductions; i++) {
            writer.writeFloat(reader.readFloat()); // horizontalBlockingAngle
            if (reader.readBoolean()) { // type（option IDSet）
                writer.writeBoolean(true);
                copyIdSet(reader, writer);
            } else {
                writer.writeBoolean(false);
            }
            writer.writeFloat(reader.readFloat()); // base
            writer.writeFloat(reader.readFloat()); // factor
        }
        writer.writeFloat(reader.readFloat()); // itemDamage.threshold
        writer.writeFloat(reader.readFloat()); // itemDamage.base
        writer.writeFloat(reader.readFloat()); // itemDamage.factor
        if (reader.readBoolean()) { // bypassedBy（option string）
            writer.writeBoolean(true);
            writer.writeString(reader.readString());
        } else {
            writer.writeBoolean(false);
        }
        if (reader.readBoolean()) { // blockSound（option ItemSoundHolder）
            writer.writeBoolean(true);
            translateSoundHolder771To770(reader, writer);
        } else {
            writer.writeBoolean(false);
        }
        if (reader.readBoolean()) { // disableSound（option ItemSoundHolder）
            writer.writeBoolean(true);
            translateSoundHolder771To770(reader, writer);
        } else {
            writer.writeBoolean(false);
        }
    }

    /** provides_trim_material（770→771）。材料の Holder を写す。trim 材料は770・771とも11種・同じ並びのためIDはそのまま。 */
    private static void copyProvidesTrimMaterial770To771(Reader reader, Writer writer) {
        boolean hasHolder = reader.readBoolean();
        writer.writeBoolean(hasHolder);
        if (!hasHolder) {
            writer.writeString(reader.readString()); // 材料名
            return;
        }
        int holder = reader.readVarInt();
        writer.writeVarInt(holder);
        if (holder != 0) {
            return;
        }
        writer.writeString(reader.readString()); // assetBase
        int count = reader.readVarInt();
        writer.writeVarInt(count);
        for (int i = 0; i < count; i++) {
            writer.writeString(reader.readString());
            writer.writeString(reader.readString());
        }
        copyOptionalAnonymousNbt(reader, writer); // description
    }

    /** provides_trim_material（771→770）。材料の Holder を写す。 */
    private static void copyProvidesTrimMaterial771To770(Reader reader, Writer writer) {
        boolean hasHolder = reader.readBoolean();
        writer.writeBoolean(hasHolder);
        if (!hasHolder) {
            writer.writeString(reader.readString()); // 材料名
            return;
        }
        int holder = reader.readVarInt();
        writer.writeVarInt(holder);
        if (holder != 0) {
            return;
        }
        writer.writeString(reader.readString()); // assetBase
        int count = reader.readVarInt();
        writer.writeVarInt(count);
        for (int i = 0; i < count; i++) {
            writer.writeString(reader.readString());
            writer.writeString(reader.readString());
        }
        copyOptionalAnonymousNbt(reader, writer); // description
    }

    /** painting/variant（770→771）の Holder を写す。絵画は770・771とも50種・同じ並びのためIDはそのまま。 */
    private static void copyPaintingVariantHolder770To771(Reader reader, Writer writer) {
        int holder = reader.readVarInt();
        writer.writeVarInt(holder);
        if (holder != 0) {
            return;
        }
        writer.writeInt(reader.readInt()); // width
        writer.writeInt(reader.readInt()); // height
        writer.writeString(reader.readString()); // assetId
        copyOptionalAnonymousNbt(reader, writer); // title
        copyOptionalAnonymousNbt(reader, writer); // author
    }

    /** painting/variant（771→770）の Holder を写す。 */
    private static void copyPaintingVariantHolder771To770(Reader reader, Writer writer) {
        int holder = reader.readVarInt();
        writer.writeVarInt(holder);
        if (holder != 0) {
            return;
        }
        writer.writeInt(reader.readInt()); // width
        writer.writeInt(reader.readInt()); // height
        writer.writeString(reader.readString()); // assetId
        copyOptionalAnonymousNbt(reader, writer); // title
        copyOptionalAnonymousNbt(reader, writer); // author
    }

    /** DataComponentMatchers（770→771）。exact は成分の列、partial は種別の列。 */
    private static void translateDataComponentMatchers770To771(Reader reader, Writer writer) {
        int exact = reader.readVarInt();
        Writer kept = new Writer();
        int keptCount = 0;
        for (int i = 0; i < exact; i++) {
            int kind770 = reader.readVarInt();
            Writer component = new Writer();
            if (!translateComponent770To771(reader, component, kind770)) {
                continue;
            }
            kept.writeVarInt(kind770);
            kept.writeBytes(component.bytes());
            keptCount++;
        }
        writer.writeVarInt(keptCount);
        writer.writeBytes(kept.bytes());
        int partial = reader.readVarInt();
        writer.writeVarInt(partial);
        for (int i = 0; i < partial; i++) {
            writer.writeVarInt(reader.readVarInt());
        }
    }

    /** DataComponentMatchers（771→770）。exact は成分の列、partial は種別の列。 */
    private static void translateDataComponentMatchers771To770(Reader reader, Writer writer) {
        int exact = reader.readVarInt();
        Writer kept = new Writer();
        int keptCount = 0;
        for (int i = 0; i < exact; i++) {
            int kind771 = reader.readVarInt();
            Writer component = new Writer();
            if (!translateComponent771To770(reader, component, kind771)) {
                continue;
            }
            kept.writeVarInt(mapComponentKind771To770(kind771));
            kept.writeBytes(component.bytes());
            keptCount++;
        }
        writer.writeVarInt(keptCount);
        writer.writeBytes(kept.bytes());
        int partial = reader.readVarInt();
        writer.writeVarInt(partial);
        for (int i = 0; i < partial; i++) {
            writer.writeVarInt(mapComponentKind771To770(reader.readVarInt()));
        }
    }
"""

# 置き換えの続き（成分ヘルパー：769→770の形式変換を含まないもの）。
CHUNK_METHOD_REPLACEMENTS.update({
"translateEnchantments770To771(Reader reader, Writer writer)": """    /** enchantments／stored_enchantments を770→771向けに変換する。IDは同一、末尾の showTooltip は770・771とも無い。 */
    private static void translateEnchantments770To771(Reader reader, Writer writer) {
        int count = reader.readVarInt();
        Writer kept = new Writer();
        int keptCount = 0;
        for (int i = 0; i < count; i++) {
            int id = reader.readVarInt();
            int level = reader.readVarInt();
            int mapped = RegistryRemapper.mapEnchantment770To771(id);
            if (mapped == RegistryRemapper.DROP) {
                logOnce("enchantment-drop-771",
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
"translateEnchantments771To770(Reader reader, Writer writer)": """    /** enchantments／stored_enchantments を771→770向けに変換する。IDは同一、末尾の showTooltip は770・771とも無い。 */
    private static void translateEnchantments771To770(Reader reader, Writer writer) {
        int count = reader.readVarInt();
        Writer kept = new Writer();
        int keptCount = 0;
        for (int i = 0; i < count; i++) {
            int id = reader.readVarInt();
            int level = reader.readVarInt();
            int mapped = RegistryRemapper.mapEnchantment771To770(id);
            if (mapped == RegistryRemapper.DROP) {
                logOnce("enchantment-drop-770-from-771",
                        "770に無いエンチャントを落とします（id=" + id + "）。");
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
"translateAttributeModifiers770To771(Reader reader, Writer writer)": """    /** attribute_modifiers を770→771向けに変換する。770・771とも {属性の配列} のみ（末尾の showTooltip は無い）。 */
    private static void translateAttributeModifiers770To771(Reader reader, Writer writer) {
        int count = reader.readVarInt();
        Writer kept = new Writer();
        int keptCount = 0;
        for (int i = 0; i < count; i++) {
            int attrId = reader.readVarInt();
            String name = reader.readString();
            double value = reader.readDouble();
            int operation = reader.readVarInt();
            int slot = reader.readVarInt();
            int mapped = RegistryRemapper.mapAttribute770To771(attrId);
            if (mapped == RegistryRemapper.DROP) {
                logOnce("attribute-modifier-drop-771",
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
"translateAttributeModifiers771To770(Reader reader, Writer writer)": """    /** attribute_modifiers を771→770向けに変換する。770・771とも {属性の配列} のみ。 */
    private static void translateAttributeModifiers771To770(Reader reader, Writer writer) {
        int count = reader.readVarInt();
        Writer kept = new Writer();
        int keptCount = 0;
        for (int i = 0; i < count; i++) {
            int attrId = reader.readVarInt();
            String name = reader.readString();
            double value = reader.readDouble();
            int operation = reader.readVarInt();
            int slot = reader.readVarInt();
            int mapped = RegistryRemapper.mapAttribute771To770(attrId);
            if (mapped == RegistryRemapper.DROP) {
                logOnce("attribute-modifier-drop-770-from-771",
                        "770に無い属性の修飾子を落とします（id=" + attrId + "）。");
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
"translateTrim770To771(Reader reader, Writer writer)": """    /** trim を770→771向けに変換する。材料・型の Holder。770・771とも showInTooltip は無い。 */
    private static boolean translateTrim770To771(Reader reader, Writer writer) {
        if (!translateTrimMaterial770To771(reader, writer)) {
            return false;
        }
        return translateTrimPattern770To771(reader, writer);
    }
""",
"translateTrim771To770(Reader reader, Writer writer)": """    /** trim を771→770向けに変換する。材料・型の Holder。770・771とも showInTooltip は無い。 */
    private static boolean translateTrim771To770(Reader reader, Writer writer) {
        if (!translateTrimMaterial771To770(reader, writer)) {
            return false;
        }
        return translateTrimPattern771To770(reader, writer);
    }
""",
"translateTrimMaterial770To771(Reader reader, Writer writer)": """    /** trim の材料の Holder を770→771向けに変換する。770・771とも直書きは {assetBase, overrides, description}（ingredientId は無い）。材料IDは11種・同じ並びのためそのまま。 */
    private static boolean translateTrimMaterial770To771(Reader reader, Writer writer) {
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
"translateTrimMaterial771To770(Reader reader, Writer writer)": """    /** trim の材料の Holder を771→770向けに変換する。直書きは {assetBase, overrides, description}。 */
    private static boolean translateTrimMaterial771To770(Reader reader, Writer writer) {
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
"translateTrimPattern770To771(Reader reader, Writer writer)": """    /** trim の型の Holder を770→771向けに変換する。770・771とも直書きは {assetId, description, decal}（templateItemId は無い）。 */
    private static boolean translateTrimPattern770To771(Reader reader, Writer writer) {
        int holder = reader.readVarInt();
        if (holder != 0) {
            int mapped = RegistryRemapper.mapTrimPattern770To771(holder - 1);
            if (mapped == RegistryRemapper.DROP) {
                logOnce("trim-pattern-drop-771",
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
"translateTrimPattern771To770(Reader reader, Writer writer)": """    /** trim の型の Holder を771→770向けに変換する。直書きは {assetId, description, decal}。 */
    private static boolean translateTrimPattern771To770(Reader reader, Writer writer) {
        int holder = reader.readVarInt();
        if (holder != 0) {
            int mapped = RegistryRemapper.mapTrimPattern771To770(holder - 1);
            if (mapped == RegistryRemapper.DROP) {
                logOnce("trim-pattern-drop-770-from-771",
                        "770に無い装飾パターンの trim を落とします（id=" + (holder - 1) + "）。");
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
"translateInstrument770To771(Reader reader, Writer writer)": """    /**
     * instrument コンポーネントを770→771向けに変換する。770・771とも
     * {hasHolder bool, data：true なら Holder／false なら楽器名} の
     * コンテナ。楽器IDは770・771とも8件・同じ並びのためそのまま。
     */
    private static boolean translateInstrument770To771(Reader reader, Writer writer) {
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
        translateSoundHolder770To771(reader, writer);
        writer.writeFloat(reader.readFloat()); // useDuration
        writer.writeFloat(reader.readFloat()); // range
        copyOptionalAnonymousNbt(reader, writer);
        return true;
    }
""",
"translateInstrument771To770(Reader reader, Writer writer)": """    /**
     * instrument コンポーネントを771→770向けに変換する。770・771とも
     * {hasHolder bool, data} のコンテナ。楽器IDは8件・同じ並びのためそのまま。
     */
    private static boolean translateInstrument771To770(Reader reader, Writer writer) {
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
        translateSoundHolder771To770(reader, writer);
        writer.writeFloat(reader.readFloat()); // useDuration
        writer.writeFloat(reader.readFloat()); // range
        copyOptionalAnonymousNbt(reader, writer);
        return true;
    }
""",
"translateJukeboxPlayable770To771(Reader reader, Writer writer)": """    /** jukebox_playable を770→771向けに変換する。曲IDは tears（771=17）の挿入で+1ずれる。直書きの音の Holder も付け替える。末尾の showInTooltip は770・771ともある。 */
    private static void translateJukeboxPlayable770To771(Reader reader, Writer writer) {
        boolean hasHolder = reader.readBoolean();
        writer.writeBoolean(hasHolder);
        if (hasHolder) {
            int holder = reader.readVarInt();
            if (holder == 0) {
                writer.writeVarInt(0);
                translateSoundHolder770To771(reader, writer);
                writer.writeAnonymousNbt(reader.readAnonymousNbt().tag()); // description
                writer.writeFloat(reader.readFloat()); // lengthInSeconds
                writer.writeVarInt(reader.readVarInt()); // comparatorOutput
            } else {
                int mapped = RegistryRemapper.mapJukeboxSong770To771(holder - 1);
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
"translateJukeboxPlayable771To770(Reader reader, Writer writer)": """    /** jukebox_playable を771→770向けに変換する。曲IDは tears（771=17）を "11" で代用し、18番以降は-1する。末尾の showInTooltip は770・771ともある。 */
    private static void translateJukeboxPlayable771To770(Reader reader, Writer writer) {
        boolean hasHolder = reader.readBoolean();
        writer.writeBoolean(hasHolder);
        if (hasHolder) {
            int holder = reader.readVarInt();
            if (holder == 0) {
                writer.writeVarInt(0);
                translateSoundHolder771To770(reader, writer);
                writer.writeAnonymousNbt(reader.readAnonymousNbt().tag()); // description
                writer.writeFloat(reader.readFloat()); // lengthInSeconds
                writer.writeVarInt(reader.readVarInt()); // comparatorOutput
            } else {
                int mapped = RegistryRemapper.mapJukeboxSong771To770(holder - 1);
                if (mapped == RegistryRemapper.DROP) {
                    // tears は770に無い。曲を落とす表現が無いため "11"（0）で代用する。
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
"translateTool770To771(Reader reader, Writer writer)": """    /** tool を770→771向けに変換する。ルールの中のブロックIDだけ付け替え、末尾の canDestroyBlocksInCreative は770・771ともあるため写す。 */
    private static void translateTool770To771(Reader reader, Writer writer) {
        int ruleCount = reader.readVarInt();
        writer.writeVarInt(ruleCount);
        for (int i = 0; i < ruleCount; i++) {
            translateIdSetBlocks770To771(reader, writer);
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
"translateTool771To770(Reader reader, Writer writer)": """    /** tool を771→770向けに変換する。ルールの中のブロックIDだけ付け替え、末尾の canDestroyBlocksInCreative は写す。 */
    private static void translateTool771To770(Reader reader, Writer writer) {
        int ruleCount = reader.readVarInt();
        writer.writeVarInt(ruleCount);
        for (int i = 0; i < ruleCount; i++) {
            translateIdSetBlocks771To770(reader, writer);
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
"translateItemBlockPredicates770To771(Reader reader, Writer writer)": """    /** can_place_on／can_break を770→771向けに変換する。述語の中のブロックIDだけ付け替える。末尾の showTooltip は770・771とも無い。 */
    private static void translateItemBlockPredicates770To771(Reader reader, Writer writer) {
        int count = reader.readVarInt();
        writer.writeVarInt(count);
        for (int i = 0; i < count; i++) {
            translateItemBlockPredicate770To771(reader, writer);
        }
    }
""",
"translateItemBlockPredicates771To770(Reader reader, Writer writer)": """    /** can_place_on／can_break を771→770向けに変換する。述語の中のブロックIDだけ付け替える。 */
    private static void translateItemBlockPredicates771To770(Reader reader, Writer writer) {
        int count = reader.readVarInt();
        writer.writeVarInt(count);
        for (int i = 0; i < count; i++) {
            translateItemBlockPredicate771To770(reader, writer);
        }
    }
""",
"translateItemBlockPredicate770To771(Reader reader, Writer writer)": """    /** ItemBlockPredicate 1個を770→771向けに変換する。述語の中のブロックIDだけ付け替え、末尾の DataComponentMatchers も変換する（770・771ともある）。 */
    private static void translateItemBlockPredicate770To771(Reader reader, Writer writer) {
        if (reader.readBoolean()) {
            writer.writeBoolean(true);
            translateIdSetBlocks770To771(reader, writer);
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
        translateDataComponentMatchers770To771(reader, writer);
    }
""",
"translateItemBlockPredicate771To770(Reader reader, Writer writer)": """    /** ItemBlockPredicate 1個を771→770向けに変換する。述語の中のブロックIDだけ付け替え、末尾の DataComponentMatchers も変換する。 */
    private static void translateItemBlockPredicate771To770(Reader reader, Writer writer) {
        if (reader.readBoolean()) {
            writer.writeBoolean(true);
            translateIdSetBlocks771To770(reader, writer);
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
        translateDataComponentMatchers771To770(reader, writer);
    }
""",
"translateParticle771To770(Reader reader, Writer writer)": """    /**
     * パーティクル1個（ID VarInt＋データ）を771→770向けに変換する。
     * パーティクルは770・771とも同一（tinted_leaves・firefly も両方に
     * ある）ため常に変換できる（{@code true} を返す）。
     */
    private static boolean translateParticle771To770(Reader reader, Writer writer) {
        int particleId = reader.readVarInt();
        writer.writeVarInt(RegistryRemapper.mapParticleUp771(particleId));
        translateParticleData771To770(reader, writer, particleId);
        return true;
    }
""",
"translateWorldParticles771To770(byte[] payload)": """    /**
     * World Particles（771 → 770）。alwaysShow は770・771ともにあるため
     * そのまま通す。粒子は770・771で同一のため落とすことは無い。
     */
    public static byte[] translateWorldParticles771To770(byte[] payload) {
        try {
            Reader reader = new Reader(payload);
            boolean longDistance = reader.readBoolean();
            boolean alwaysShow = reader.readBoolean(); // alwaysShow（770・771ともにある）
            double x = reader.readDouble();
            double y = reader.readDouble();
            double z = reader.readDouble();
            float offsetX = reader.readFloat();
            float offsetY = reader.readFloat();
            float offsetZ = reader.readFloat();
            float speed = reader.readFloat();
            int count = reader.readInt();
            Writer particleWriter = new Writer();
            translateParticle771To770(reader, particleWriter);
            requireEnd(reader);
            Writer writer = new Writer();
            writer.writeBoolean(longDistance);
            writer.writeBoolean(alwaysShow); // alwaysShow（770にもある。そのまま通す）
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
"translateMetadataValue770To771(Reader reader, Writer writer, int type)": """    /**
     * メタデータの値1個を770→771向けに変換する。落とすエントリは {@code false} を返す。
     * 型番号は770側の番号で dispatch する（770・771で型番号は同一）。
     * 23（ウシ）・24（狼）・25（狼の音）・26（カエル）・27（ブタ）・28（ニワトリ）は
     * 770・771とも varint。30（絵画）は Holder。
     */
    private static boolean translateMetadataValue770To771(Reader reader, Writer writer, int type) {
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
            case 7 -> translateSlot770To771(reader, writer);
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
            case 14, 15 -> writer.writeVarInt(RegistryRemapper.mapBlockState770To771(reader.readVarInt()));
            case 16 -> writeOptionalAnonymousNbt(writer, reader.readAnonymousNbt().tag());
            case 17 -> translateParticle770To771(reader, writer);
            case 18 -> {
                int count = reader.readVarInt();
                writer.writeVarInt(count);
                for (int i = 0; i < count; i++) {
                    translateParticle770To771(reader, writer);
                }
            }
            case 19 -> {
                writer.writeVarInt(reader.readVarInt());
                writer.writeVarInt(reader.readVarInt());
                writer.writeVarInt(reader.readVarInt());
            }
            case 20, 21, 22 -> writer.writeVarInt(reader.readVarInt());
            case 23, 25, 26, 27, 28 -> writer.writeVarInt(reader.readVarInt()); // cow／wolf_sound／frog／pig／chicken
            case 24 -> translateWolfVariantHolder770To771(reader, writer);
            case 29 -> {
                boolean present = reader.readBoolean();
                writer.writeBoolean(present);
                if (present) {
                    writer.writeString(reader.readString());
                    writer.writePositionRaw(reader.readPositionRaw());
                }
            }
            case 30 -> {
                return translatePaintingVariantHolder770To771(reader, writer);
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
"translateMetadataValue771To770(Reader reader, Writer writer, int type)": """    /**
     * メタデータの値1個を771→770向けに変換する。落とすエントリは {@code false} を返す。
     * 型番号は771側の番号で dispatch する（770・771で型番号は同一）。
     * 770に無い型は無いため落とすことは無い。
     */
    private static boolean translateMetadataValue771To770(Reader reader, Writer writer, int type) {
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
            case 7 -> translateSlot771To770(reader, writer);
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
            case 14, 15 -> writer.writeVarInt(RegistryRemapper.mapBlockStateUp771(reader.readVarInt()));
            case 16 -> writeOptionalAnonymousNbt(writer, reader.readAnonymousNbt().tag());
            case 17 -> {
                return translateParticle771To770(reader, writer);
            }
            case 18 -> {
                int count = reader.readVarInt();
                writer.writeVarInt(count);
                for (int i = 0; i < count; i++) {
                    translateParticle771To770(reader, writer);
                }
            }
            case 19 -> {
                writer.writeVarInt(reader.readVarInt());
                writer.writeVarInt(reader.readVarInt());
                writer.writeVarInt(reader.readVarInt());
            }
            case 20, 21, 22 -> writer.writeVarInt(reader.readVarInt());
            case 23, 25, 26, 27, 28 -> writer.writeVarInt(reader.readVarInt()); // cow／wolf_sound／frog／pig／chicken
            case 24 -> translateWolfVariantHolder771To770(reader, writer);
            case 29 -> {
                boolean present = reader.readBoolean();
                writer.writeBoolean(present);
                if (present) {
                    writer.writeString(reader.readString());
                    writer.writePositionRaw(reader.readPositionRaw());
                }
            }
            case 30 -> {
                return translatePaintingVariantHolder771To770(reader, writer);
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
"translateWolfVariantHolder770To771(Reader reader, Writer writer)": """    /** 狼バリアントの Holder を770→771向けに変換する。770・771ともただの varint（9種・同じ並び）のためそのまま写す。 */
    private static boolean translateWolfVariantHolder770To771(Reader reader, Writer writer) {
        writer.writeVarInt(reader.readVarInt());
        return true;
    }
""",
"translateWolfVariantHolder771To770(Reader reader, Writer writer)": """    /** 狼バリアントの Holder を771→770向けに変換する。770・771ともただの varint のためそのまま写す。 */
    private static boolean translateWolfVariantHolder771To770(Reader reader, Writer writer) {
        writer.writeVarInt(reader.readVarInt());
        return true;
    }
""",
"mapMetadataType770To771(int type770)": """    /** メタデータの型番号を770→771に付け替える。770・771で型番号は同一。 */
    private static int mapMetadataType770To771(int type770) {
        return type770;
    }
""",
"mapMetadataType771To770(int type771)": """    /** メタデータの型番号を771→770に付け替える。770・771で型番号は同一。 */
    private static int mapMetadataType771To770(int type771) {
        return type771;
    }
""",
"translateChunkData770To771(byte[] payload)": """    /** チャンクデータを770→771向けに変換する。heightmap は770・771とも直接の配列（varint 件数＋（type、長さ、long列））のためそのまま写す。セクションのブロック状態・バイオームとブロックエンティティ種別を付け替える。 */
    public static byte[] translateChunkData770To771(byte[] payload) {
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
            byte[] translatedSections = translateChunkSections770To771(chunkData);
            int blockEntityCount = reader.readVarInt();
            List<BlockEntityEntry> kept = new ArrayList<>();
            for (int i = 0; i < blockEntityCount; i++) {
                int packedXZ = reader.readUnsignedByte();
                int y = reader.readShort();
                int type = reader.readVarInt();
                Tag nbt = readOptionalAnonymousNbt(reader);
                int mapped = RegistryRemapper.mapBlockEntityType770To771(type);
                if (mapped == RegistryRemapper.DROP) {
                    LOG.warning("771に無いブロックエンティティ種別（770 type=" + type
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
"translateChunkData771To770(byte[] payload)": """    /** チャンクデータを771→770向けに変換する。heightmap は770・771とも直接の配列のためそのまま写す。 */
    public static byte[] translateChunkData771To770(byte[] payload) {
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
            byte[] translatedSections = translateChunkSections771To770(chunkData);
            int blockEntityCount = reader.readVarInt();
            List<BlockEntityEntry> kept = new ArrayList<>();
            for (int i = 0; i < blockEntityCount; i++) {
                int packedXZ = reader.readUnsignedByte();
                int y = reader.readShort();
                int type = reader.readVarInt();
                Tag nbt = readOptionalAnonymousNbt(reader);
                int mapped = RegistryRemapper.mapBlockEntityTypeUp771(type);
                if (mapped == RegistryRemapper.DROP) {
                    LOG.warning("770に無いブロックエンティティ種別（771 type=" + type
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
"translatePalettedContainer770To771(Reader reader, Writer writer, boolean blocks)": """        /**
         * パレット付きコンテナ1個を770→771向けに変換する。ブロック状態と
         * バイオームの両方を付け替える。770・771とも Data Array に
         * 長さ varint 接頭辞は無い。
         */
        private static void translatePalettedContainer770To771(Reader reader, Writer writer, boolean blocks) {
        int bitsPerEntry = reader.readUnsignedByte();
        if (bitsPerEntry > 32) {
            throw new IllegalArgumentException("Bits Per Entry が大きすぎます: " + bitsPerEntry);
        }
        writer.writeUnsignedByte(bitsPerEntry);
        int entryCount = blocks ? 4096 : 64;
        if (bitsPerEntry == 0) {
            int value = reader.readVarInt();
            writer.writeVarInt(blocks
                    ? RegistryRemapper.mapBlockState770To771(value)
                    : mapBiome770To771(value));
            // 770・771とも長さ接頭辞は無い。
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
                        ? RegistryRemapper.mapBlockState770To771(value)
                        : mapBiome770To771(value));
            }
            // 間接パレットの Data Array はインデックス列なので中身は変えない。
            // 770・771とも長さ接頭辞は無い。長さは bpe から計算する。
            int perLong = 64 / bitsPerEntry;
            int expected = (entryCount + perLong - 1) / perLong;
            for (int i = 0; i < expected; i++) {
                writer.writeLong(reader.readLong());
            }
            return;
        }
        // 直接パレット：Data Array の中身がレジストリ番号そのもの。
        // 770・771とも長さ接頭辞は無い。長さは bpe から計算する。
        int perLong = 64 / bitsPerEntry;
        int expected = (entryCount + perLong - 1) / perLong;
        long[] longs = new long[expected];
        for (int i = 0; i < expected; i++) {
            longs[i] = reader.readLong();
        }
        remapDirectEntries770To771(longs, bitsPerEntry, entryCount, blocks);
        for (long value : longs) {
            writer.writeLong(value);
        }
    }
""",
"translatePalettedContainer771To770(Reader reader, Writer writer, boolean blocks)": """        /** パレット付きコンテナ1個を771→770向けに変換する。ブロック状態とバイオームの両方を付け替える。770・771とも Data Array に長さ varint 接頭辞は無い。 */
        private static void translatePalettedContainer771To770(Reader reader, Writer writer, boolean blocks) {
        int bitsPerEntry = reader.readUnsignedByte();
        if (bitsPerEntry > 32) {
            throw new IllegalArgumentException("Bits Per Entry が大きすぎます: " + bitsPerEntry);
        }
        writer.writeUnsignedByte(bitsPerEntry);
        int entryCount = blocks ? 4096 : 64;
        if (bitsPerEntry == 0) {
            int value = reader.readVarInt();
            writer.writeVarInt(blocks
                    ? RegistryRemapper.mapBlockStateUp771(value)
                    : mapBiomeUp771(value));
            // 770・771とも長さ接頭辞は無い。
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
                        ? RegistryRemapper.mapBlockStateUp771(value)
                        : mapBiomeUp771(value));
            }
            // 間接パレットの Data Array はインデックス列なので中身は変えない。
            // 770・771とも長さ接頭辞は無い。長さは bpe から計算する。
            int perLong = 64 / bitsPerEntry;
            int expected = (entryCount + perLong - 1) / perLong;
            for (int i = 0; i < expected; i++) {
                writer.writeLong(reader.readLong());
            }
            return;
        }
        // 直接パレット：Data Array の中身がレジストリ番号そのもの。
        // 770・771とも長さ接頭辞は無い。長さは bpe から計算する。
        int perLong = 64 / bitsPerEntry;
        int expected = (entryCount + perLong - 1) / perLong;
        long[] longs = new long[expected];
        for (int i = 0; i < expected; i++) {
            longs[i] = reader.readLong();
        }
        remapDirectEntries771To770(longs, bitsPerEntry, entryCount, blocks);
        for (long value : longs) {
            writer.writeLong(value);
        }
    }
""",
"translateExplosion770To771(byte[] payload)": """    /** Explosion（770 → 771）。knockback は770・771とも f64×3。粒子・音を付け替える。 */
    public static byte[] translateExplosion770To771(byte[] payload) {
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
            translateParticle770To771(reader, writer);
            translateSoundHolder770To771(reader, writer);
            requireEnd(reader);
            return writer.bytes();
        } catch (IllegalArgumentException e) {
            LOG.warning("Explosion を変換できなかったため素通しします: " + e.getMessage());
            return payload;
        }
    }
""",
"translateExplosion771To770(byte[] payload)": """    /** Explosion（771 → 770）。knockback は770・771とも f64×3。粒子・音を付け替える。 */
    public static byte[] translateExplosion771To770(byte[] payload) {
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
            translateParticle771To770(reader, particleWriter);
            writer.writeBytes(particleWriter.bytes());
            translateSoundHolder771To770(reader, writer);
            requireEnd(reader);
            return writer.bytes();
        } catch (IllegalArgumentException e) {
            LOG.warning("Explosion を変換できなかったため素通しします: " + e.getMessage());
            return payload;
        }
    }
""",
"translateSetCreativeSlot770To771(byte[] payload)": """    /** Set Creative Slot（770 → 771）。770・771とも UntrustedSlot（成分は ByteArray の生バイト）。成分の中身を770→771に変換する。 */
    public static byte[] translateSetCreativeSlot770To771(byte[] payload) {
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
            writer.writeVarInt(RegistryRemapper.mapItem770To771(reader.readVarInt()));
            int added = reader.readVarInt();
            int removed = reader.readVarInt();
            Writer addedOut = new Writer();
            int keptAdded = 0;
            for (int i = 0; i < added; i++) {
                int kind770 = reader.readVarInt();
                byte[] data = reader.readByteArray();
                Writer component = new Writer();
                if (!translateComponent770To771(new Reader(data), component, kind770)) {
                    continue;
                }
                addedOut.writeVarInt(kind770);
                addedOut.writeByteArray(component.bytes());
                keptAdded++;
            }
            Writer removedOut = new Writer();
            int keptRemoved = 0;
            for (int i = 0; i < removed; i++) {
                int kind771 = mapComponentKind770To771(reader.readVarInt());
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
"translateSetCreativeSlot771To770(byte[] payload)": """    /** Set Creative Slot（771 → 770）。770・771とも UntrustedSlot。成分の中身を771→770に変換する。 */
    public static byte[] translateSetCreativeSlot771To770(byte[] payload) {
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
            writer.writeVarInt(RegistryRemapper.mapItemUp771(reader.readVarInt()));
            int added = reader.readVarInt();
            int removed = reader.readVarInt();
            Writer addedOut = new Writer();
            int keptAdded = 0;
            for (int i = 0; i < added; i++) {
                int kind771 = reader.readVarInt();
                byte[] data = reader.readByteArray();
                Writer component = new Writer();
                if (!translateComponent771To770(new Reader(data), component, kind771)) {
                    continue;
                }
                addedOut.writeVarInt(mapComponentKind771To770(kind771));
                addedOut.writeByteArray(component.bytes());
                keptAdded++;
            }
            Writer removedOut = new Writer();
            int keptRemoved = 0;
            for (int i = 0; i < removed; i++) {
                int kind770 = mapComponentKind771To770(reader.readVarInt());
                if (kind770 < 0) {
                    continue;
                }
                removedOut.writeVarInt(kind770);
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
# PlayTranslator 771 節（手書き）
# ----------------------------------------------------------------------

PLAY_771_SECTION = """
    // 770 ⇔ 771（1.21.6）
    // Join Game／Respawn／Synchronize Player Position／Login Success／
    // Client Information／Player Info／Set Held Item／Vehicle Move／
    // Player Chat は770と771で形式が同一のため変換器は要らない（素通し）。
    // 変わるのは Chat Message／Chat Command Signed（末尾の checksum u8 は
    // 770・771ともあるが、番号が 770→771 で+1ずれる。checksum の中身は
    // 770と同じ扱い）、Registry Data（レジストリの順序。1.21.6で追加
    // されたのは minecraft:dialog。jukebox_song は tears の挿入で
    // 17番以降が+1ずれる）だけである。
    // ------------------------------------------------------------------

    /** Player Info（770 → 771）。形式は同一のため素通しする。 */
    public static byte[] translatePlayerInfo770To771(byte[] payload) {
        return payload;
    }

    /** Player Info（771 → 770）。形式は同一のため素通しする。 */
    public static byte[] translatePlayerInfo771To770(byte[] payload) {
        return payload;
    }

    /** Set Held Item（770 → 771）。形式は同一のため素通しする（varint のまま）。 */
    public static byte[] translateSetHeldItem770To771(byte[] payload) {
        return payload;
    }

    /** Set Held Item（771 → 770）。形式は同一のため素通しする。 */
    public static byte[] translateSetHeldItem771To770(byte[] payload) {
        return payload;
    }

    /** Vehicle Move（770 → 771）。形式は同一のため素通しする（末尾の onGround 付き）。 */
    public static byte[] translateVehicleMove770To771(byte[] payload) {
        return payload;
    }

    /** Vehicle Move（771 → 770）。形式は同一のため素通しする。 */
    public static byte[] translateVehicleMove771To770(byte[] payload) {
        return payload;
    }

    /** Player Chat（770 → 771）。先頭の globalIndex は770・771ともあるため素通しする。 */
    public static byte[] translatePlayerChat770To771(byte[] payload) {
        return payload;
    }

    /** Player Chat（771 → 770）。先頭の globalIndex は770・771ともあるため素通しする。 */
    public static byte[] translatePlayerChat771To770(byte[] payload) {
        return payload;
    }

    /**
     * Chat Message／Chat Command Signed（771 → 770）。末尾の checksum
     * （u8 1バイト）を取り除く。ペイロードが空の場合はそのまま返す。
     */
    public static byte[] stripChatChecksum771(byte[] payload) {
        if (payload.length == 0) {
            return payload;
        }
        return new Reader(payload).readBytes(payload.length - 1);
    }

    /**
     * Chat Message／Chat Command Signed（770 → 771）。末尾に checksum
     * （u8 1バイト）を追加する。checksum の式は未確認のため 0x00 を入れる
     * （実バニラ 1.21.6 で checksum 0x00 を送って Player Chat のエコーを
     * 受け取れたことは確認済み。計算式そのものの確認は未実施）。
     */
    public static byte[] appendChatChecksum771(byte[] payload) {
        Writer writer = new Writer();
        writer.writeBytes(payload);
        writer.writeByte(0);
        return writer.bytes();
    }

    /**
     * 770の Registry Data を771形式にする。1.21.6で追加された
     * minecraft:dialog は770には無いため、770サーバーからの Registry Data
     * には含まれない（771クライアントへは欠けたまま送る。実バニラ
     * クライアントでの扱いは未確認）。
     * {@code minecraft:jukebox_song} は tears の挿入で17番以降が+1ずれる
     * ため並べ替える（reorderRegistries）。
     * それ以外は1レジストリ分のペイロードを返す。
     */
    public static byte[] translateRegistryData770To771(byte[] payload) {
        Map<String, Tag.CompoundTag> reordered = reorderRegistries(
                parseRegistryData(payload),
                ProtocolVersion.V1_21_5, ProtocolVersion.V1_21_6);
        List<byte[]> built = buildRegistryData766(reordered);
        if (built.isEmpty()) {
            throw new IllegalArgumentException("Registry Data が空です。");
        }
        return built.get(0);
    }

    /**
     * 771の Registry Data を770形式にする。minecraft:dialog は770には
     * 無いため落とす（reorderRegistries）。jukebox_song は tears を落として
     * 18番以降を-1する。それ以外は1レジストリ分のペイロードを返す。
     * 空になった場合は {@code null} を返す。
     */
    public static byte[] translateRegistryData771To770(byte[] payload) {
        Map<String, Tag.CompoundTag> reordered = reorderRegistries(
                parseRegistryData(payload),
                ProtocolVersion.V1_21_6, ProtocolVersion.V1_21_5);
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
        CT, "// 769 ⇔ 770（1.21.5）")
    print(f"chunk section: lines {cs+1}-{ce+1} ({len(chunk_sec)} chars)")
    Path("/tmp/chunk770_section.txt").write_text(chunk_sec, encoding="utf-8")
    chunk_shifted = shift(chunk_sec)
    Path("/tmp/chunk771_shifted.txt").write_text(chunk_shifted, encoding="utf-8")
    print(f"chunk shifted: {len(chunk_shifted)} chars")

    # --- ChunkTranslator 771 節 ---
    chunk771 = apply_fixes(chunk_shifted, CHUNK_STRING_FIXES, "chunk-string")
    for sig, new_text in CHUNK_METHOD_REPLACEMENTS.items():
        chunk771 = replace_method(chunk771, sig, new_text, "chunk")
    for sig in DELETE_METHODS:
        chunk771 = delete_method(chunk771, sig, "chunk-delete")
    chunk771 = chunk771.rstrip() + "\n" + EXTRA_METHODS_771
    print(f"chunk771: {len(chunk771)} chars, "
          f"{chunk771.count('translate')} translate occurrences")
    insert_section_before_class_close(
        CT, chunk771, "chunk", "translateSlot770To771")

    # --- PlayTranslator 771 節（手書き） ---
    insert_section_before_class_close(
        PT, PLAY_771_SECTION, "play", "translatePlayerChat770To771")

    # --- reorderRegistries の jukebox_song フィルタ：771 も残す ---
    # minecraft:dialog は771にしか無いため、771以外向けは落とす。
    pt_text = PT.read_text(encoding="utf-8")
    pt_text = fix(pt_text,
                  """            if (registryId.equals("minecraft:jukebox_song")
                    && toProtocol != ProtocolVersion.V1_21
                    && toProtocol != ProtocolVersion.V1_21_2
                    && toProtocol != ProtocolVersion.V1_21_4
                    && toProtocol != ProtocolVersion.V1_21_5) {""",
                  """            if (registryId.equals("minecraft:jukebox_song")
                    && toProtocol != ProtocolVersion.V1_21
                    && toProtocol != ProtocolVersion.V1_21_2
                    && toProtocol != ProtocolVersion.V1_21_4
                    && toProtocol != ProtocolVersion.V1_21_5
                    && toProtocol != ProtocolVersion.V1_21_6) {""",
                  "play-jukebox")
    pt_text = fix(pt_text,
                  """            if (registryId.equals("minecraft:jukebox_song")
                    && toProtocol != ProtocolVersion.V1_21
                    && toProtocol != ProtocolVersion.V1_21_2
                    && toProtocol != ProtocolVersion.V1_21_4
                    && toProtocol != ProtocolVersion.V1_21_5
                    && toProtocol != ProtocolVersion.V1_21_6) {
                continue;
            }""",
                  """            if (registryId.equals("minecraft:jukebox_song")
                    && toProtocol != ProtocolVersion.V1_21
                    && toProtocol != ProtocolVersion.V1_21_2
                    && toProtocol != ProtocolVersion.V1_21_4
                    && toProtocol != ProtocolVersion.V1_21_5
                    && toProtocol != ProtocolVersion.V1_21_6) {
                continue;
            }
            if (registryId.equals("minecraft:dialog")
                    && toProtocol != ProtocolVersion.V1_21_6) {
                continue;
            }""",
                  "play-dialog")
    # jukebox_song の並べ替え：770↔771 の対応表を追加する。
    pt_text = fix(pt_text,
                  """            case "minecraft:damage_type" -> switch (from * 1000 + to) {""",
                  """            case "minecraft:jukebox_song" -> switch (from * 1000 + to) {
                case 770771 -> RegistryRemapper::mapJukeboxSong770To771;
                case 771770 -> RegistryRemapper::mapJukeboxSong771To770;
                default -> null;
            };
            case "minecraft:damage_type" -> switch (from * 1000 + to) {""",
                  "play-jukebox-mapper")
    pt_text = fix(pt_text,
                  """            case "minecraft:trim_pattern" -> to == 767 || to == 768 || to == 769 ? TRIM_767_NAMES : TRIM_766_NAMES;""",
                  """            case "minecraft:trim_pattern" -> to == 767 || to == 768 || to == 769 ? TRIM_767_NAMES : TRIM_766_NAMES;
            case "minecraft:jukebox_song" -> switch (to) {
                case 771 -> RegistryTables771.JUKEBOX_SONG_771_NAMES;
                case 770 -> RegistryTables771.JUKEBOX_SONG_770_NAMES;
                case 767, 768, 769 -> RegistryTables771.JUKEBOX_SONG_770_NAMES;
                default -> null;
            };""",
                  "play-jukebox-names")
    PT.write_text(pt_text, encoding="utf-8")
    print("[play-registry] OK")
    print("完了。次は :detergentmc-protocol:compileJava で確認すること。")


if __name__ == "__main__":
    main()
