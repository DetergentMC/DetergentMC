#!/usr/bin/env python3
"""ChunkTranslator / PlayTranslator の 774 節から 775 節を生成する。

方針: 774 節を抜き出して shift()（gen-775-proxysession.py のもの）で
1段ずらし、FIXES（手書きの置き換え）を当て、ファイル末尾の最終 `}` の
直前に挿入する。PlayTranslator の 775 節は手書きブロックで作る
（Player Info 等は774と775で同一形式のため素通しにする）。

774→775 で shift だけでは足りない点（FIXES で直す）:
- 成分種別は774が104件、775が110件。6件の新規挿入（additional_trade_cost 41、
  dye 43、pig/sound_variant 94、cow/sound_variant 96、chicken/sound_variant 98、
  cat/sound_variant 106）のため KIND 表は恒等ではなく 774→775 の写像
  （新規 6 件は -1）になる。773→774 の 8 件挿入（use_effects 5 等）を
  持ち越してはいけない（use_effects 等は774・775とも同じ番号）。
- 774→775 で形式が変わる成分は手書きで直す。chicken/variant と
  zombie_nautilus/variant は bool 形式から registryEntryHolder（varint。
  0＝直書きの文字列）へ、damage_type は内側の Holder が varint から
  registryEntryHolder（0＝直書きの DamageTypeData）へ、bundle_contents／
  charged_projectiles／container／use_remainder の中身は Slot から
  ItemStackTemplate へ（container はさらに option 化）。DyeColor 系
  （base_color／cat/collar 等）は線形 varint のためそのまま。
  piercing_weapon／kinetic_weapon は中身の音の Holder を付け替える。
- チャンクの heightmap・paletted container は770・771・772・773・774・775とも直接配列
  （長さ varint 接頭辞なし）のため、769形式との変換をする
  shift 版は使えない。手書きで直す。
- パーティクルは pause_mob_growth（50）・reset_mob_growth（51）の挿入で
  50番以降が+2ずれる。entity_effect は774・775とも21。particle item の
  データは Slot から ItemStackTemplate へ変わる。メタデータの型番号は
  22・24・29・31番の挿入（cat／cow／pig／chicken の sound_variant）でずれる
  （zombie_nautilus_variant は774=28→775=32、humanoid_arm は774=38→775=42）。
- エンチャント（43件）・ダメージ種別（50件）・エンティティ（157件）・絵画（51種）・
  ジュークボックスの歌（21曲）・属性（35件）・エフェクト（40件）・バイオーム・
  ブロックエンティティ種別（49件）は同一。アイテムは golden_dandelion（775=230）の
  追加で230番以降が+1、音は+64、ブロック状態は+202ずれる。Registry Data の
  並べ替え（reorderRegistries）は enchantment／damage_type／jukebox／絵画が
  同一のため素通しだが、jukebox_song／dialog のフィルタと targetNames に
  775（V26_1）を足す。
- Explosion の knockback は770・771・772・773・774・775とも f64×3。
- jukebox_playable の曲IDは772・773・774・775で同一。
- Set Creative Slot は770・771・772・773・774・775とも UntrustedSlot。
- Spawn Entity：エンティティは同一順序（falling_block は774・775とも51）。
  対応表で付け替える。
- Update Time は time・tickDayTime から clockUpdates 配列へ再構成する。
  Use Entity は再構成し、attack は独立パケットへ分離する。Enchant Item は
  enchantment が i8 から varint へ戻る。Advancements の icon は Slot から
  ItemStackTemplate へ変わるが、ExtraPlayTranslator の範囲のためこの生成器では
  翻訳子を作らず、監査報告に記録する。
- ProxySession の設定フェーズ 6 ハンドラ（775From773／772／771、
  772／771／770From775）は gen-775-proxysession.py では生成されないため、
  この生成器が PROXYSESSION_CONFIG_SECTION として手書きで追加する。
  main() の抽出は extract_section_to_eof ではいけない。774節の後ろに
  762／761／760／759節があるため、extract_section で 774節だけを抜く。
"""

import importlib.util
import re
import sys
from pathlib import Path

_HERE = Path(__file__).resolve().parent
_spec = importlib.util.spec_from_file_location("gen775ps", _HERE / "gen-775-proxysession.py")
_mod = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(_mod)
shift = _mod.shift

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


# （main はファイル末尾に定義する）

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


# 成分種別の対応表（774 kind -> 775 kind）。
# 成分種別の対応表（774 kind -> 775 kind）。
# 775 の新規成分種別（774 には無い。minecraft-data mc-26.1-protocol.json の
# SlotComponentType より）：41 additional_trade_cost、43 dye、
# 94 pig/sound_variant、96 cow/sound_variant、98 chicken/sound_variant、
# 106 cat/sound_variant。774 の 104 件はすべて 775 にある。
_NEW_KIND_775 = {41, 43, 94, 96, 98, 106}
KIND_774_TO_775 = {}
_nxt = 0
for _k774 in range(104):
    while _nxt in _NEW_KIND_775:
        _nxt += 1
    KIND_774_TO_775[_k774] = _nxt
    _nxt += 1
KIND_775_TO_774 = {v: k for k, v in KIND_774_TO_775.items()}
for _n in _NEW_KIND_775:
    KIND_775_TO_774[_n] = -1  # 775 の新規種別は 774 では落とす
assert len(KIND_774_TO_775) == 104
assert len(KIND_775_TO_774) == 110
assert KIND_774_TO_775[0] == 0 and KIND_774_TO_775[40] == 40
assert KIND_774_TO_775[41] == 42 and KIND_774_TO_775[42] == 44
assert KIND_774_TO_775[93] == 97 and KIND_774_TO_775[94] == 99
assert KIND_774_TO_775[103] == 109
assert KIND_775_TO_774[41] == -1 and KIND_775_TO_774[43] == -1
assert KIND_775_TO_774[94] == -1 and KIND_775_TO_774[106] == -1
assert KIND_775_TO_774[42] == 41 and KIND_775_TO_774[109] == 103


def java_int_array(name, values, per_line=16):
    lines = []
    for i in range(0, len(values), per_line):
        lines.append("            " + ", ".join(str(v) for v in values[i:i + per_line]) + ",")
    body = "\n".join(lines)
    return f"    private static final int[] {name} = {{\n{body}\n    }};\n"


KIND_774_TO_775_JAVA = java_int_array(
    "KIND_774_TO_775", [KIND_774_TO_775[i] for i in range(104)])
KIND_775_TO_774_JAVA = java_int_array(
    "KIND_775_TO_774",
    [KIND_775_TO_774.get(i, -1) for i in range(110)])

# 774節の KIND 表（shift 後の old として使う。774 節の KIND 表は 773→774 の
# 実写像（96→104）。773→774 の新規 8 種（use_effects 5、minimum_attack_charge 7、
# damage_type 8、attack_range 30、piercing_weapon 38、kinetic_weapon 39、
# swing_animation 40、zombie_nautilus/variant 94）をここにだけ残す。
# 775 の KIND 表は上の _NEW_KIND_775 から作る。混同しないこと。
_NEW_KIND_774_OLD = {5, 7, 8, 30, 38, 39, 40, 94}
KIND_773_TO_774_OLD = {}
_nxt = 0
for _k773 in range(96):
    while _nxt in _NEW_KIND_774_OLD:
        _nxt += 1
    KIND_773_TO_774_OLD[_k773] = _nxt
    _nxt += 1
KIND_774_TO_773_OLD = {v: k for k, v in KIND_773_TO_774_OLD.items()}
for _n in _NEW_KIND_774_OLD:
    KIND_774_TO_773_OLD[_n] = -1  # 774 の新規種別は 773 では落とす
assert len(KIND_773_TO_774_OLD) == 96
assert len(KIND_774_TO_773_OLD) == 104
assert KIND_773_TO_774_OLD[0] == 0 and KIND_773_TO_774_OLD[5] == 6
assert KIND_773_TO_774_OLD[6] == 9 and KIND_773_TO_774_OLD[95] == 103
assert KIND_774_TO_773_OLD[5] == -1 and KIND_774_TO_773_OLD[94] == -1
assert KIND_774_TO_773_OLD[6] == 5 and KIND_774_TO_773_OLD[103] == 95

# shift 後の 775 節に残る（誤った）KIND 表のテキスト。置き換えの old に使う。
# （774 節の 773→774 実写像を shift したもの。KIND_774_TO_775_JAVA／
#  KIND_775_TO_774_JAVA は CHUNK_STRING_FIXES の new 側で使う。）
KIND_774_TO_775_SHIFTED_JAVA = java_int_array(
    "KIND_774_TO_775", [KIND_773_TO_774_OLD[i] for i in range(96)])
KIND_775_TO_774_SHIFTED_JAVA = java_int_array(
    "KIND_775_TO_774",
    [KIND_774_TO_773_OLD.get(i, -1) for i in range(104)])



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


# 774節のヘッダーコメント（shift 後の old を shift() で作る）。
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
    // パーティクルは773・774で同一（copper_fire_flame は773・774とも5。
    // trail 49／block_crumble 113／tinted_leaves 36／firefly 114）。
    // チャンクの heightmap は770・771・772・773・774とも直接の配列（長さ varint 無し）。"""

HEADER_775 = """    // 774 ⇔ 775（26.1）
    // 成分種別は774が104件、775が110件。6件の新規挿入（additional_trade_cost 41、
    // dye 43、pig/sound_variant 94、cow/sound_variant 96、chicken/sound_variant 98、
    // cat/sound_variant 106）のため KIND 表で付け替える。
    // 変わるのは中身のレジストリID（アイテム・音・ブロック状態）と成分の中身
    // （break_sound／blocks_attacks／piercing_weapon／kinetic_weapon の音ID、
    // chicken/variant・zombie_nautilus/variant の Holder 形式、bundle_contents・
    // charged_projectiles・container・use_remainder の Slot→ItemStackTemplate）。
    // エンティティ・エンチャント・ダメージ種別・バイオーム・属性・メニュー・
    // ブロックエンティティ種別・絵画・ジュークボックスは同一。
    // エンティティは774・775とも157件・同じ順序（happy_ghast は774・775とも58。
    // falling_block は774・775とも51）。
    // パーティクルは pause_mob_growth（50）・reset_mob_growth（51）の挿入で
    // 50番以降が+2ずれる（trail 49／block_crumble 115／tinted_leaves 36／firefly 116）。
    // チャンクの heightmap は770・771・772・773・774・775とも直接の配列（長さ varint 無し）。"""
# （HEADER_775 は CHUNK_STRING_FIXES のヘッダー置き換えと内容を合わせるための
#  参考定義。実際の置き換えは CHUNK_STRING_FIXES のリテラルで行う。）

# 774節の KIND 表まわり（shift 後の old を shift() で作る）。
# 774節の KIND 表まわり（775 節の old として使う実際の 774 節の表）。
KIND_COUNT_774 = """    /** 成分種別の件数（773 は 96 件、774 は 104 件）。 */
    private static final int DATA_COMPONENT_TYPE_COUNT_774 = 104;"""

KIND_COUNT_775 = """    /** 成分種別の件数（774 は 104 件、775 は 110 件）。 */
    private static final int DATA_COMPONENT_TYPE_COUNT_775 = 110;"""

KIND_JAVADOC_1_774 = """    /**
     * 成分種別の対応表（773 kind → 774 kind）。774 の新規 8 種
     * （use_effects 5、minimum_attack_charge 7、damage_type 8、attack_range 30、
     * piercing_weapon 38、kinetic_weapon 39、swing_animation 40、
     * zombie_nautilus/variant 94）の挿入で 773 の 96 件はすべてずれる。
     */"""

KIND_JAVADOC_1_775 = """    /**
     * 成分種別の対応表（774 kind → 775 kind）。775 の新規 6 種
     * （additional_trade_cost 41、dye 43、pig/sound_variant 94、
     * cow/sound_variant 96、chicken/sound_variant 98、cat/sound_variant 106）
     * の挿入で 774 の 104 件は 41 番以降がずれる。
     */"""

KIND_JAVADOC_2_774 = """    /**
     * 成分種別の対応表（774 kind → 773 kind）。774 の新規 8 種は
     * -1（落とす）。773 の 96 件はすべて 774 にある。
     */"""

KIND_JAVADOC_2_775 = """    /**
     * 成分種別の対応表（775 kind → 774 kind）。775 の新規 6 種は
     * -1（落とす）。774 の 104 件はすべて 775 にある。
     */"""

# ----------------------------------------------------------------------
# ChunkTranslator 775 節の文字列 FIXES（シフト済みテキストに適用）
# ----------------------------------------------------------------------

CHUNK_STRING_FIXES = [
    # ヘッダーコメント（shift 版は 773→774 の説明を版番号だけずらしたもの。
    # vv-1.21.11.json／vv-26.1.json と mc-26.1-protocol.json で確認した
    # 774→775 の差分で書き直す。）
    (
        """    // 成分種別は774が96件、775が104件。8件の新規挿入（use_effects 5、
    // minimum_attack_charge 7、damage_type 8、attack_range 30、
    // piercing_weapon 38、kinetic_weapon 39、swing_animation 40、
    // zombie_nautilus/variant 94）のため KIND 表で付け替える。
    // 変わるのは中身のレジストリID（アイテム・エンティティ・音・
    // エンチャント・ダメージ種別）と成分の中身（break_sound／
    // blocks_attacks の音ID）。ブロック状態・バイオーム・属性・メニュー・
    // パーティクル・ブロックエンティティ種別・絵画・ジュークボックスは同一。
    // エンティティは camel_husk（775=20）・nautilus（775=88）・parched
    // （775=97）・zombie_nautilus（775=152）の挿入でずれる（happy_ghast は
    // 775=58。774では57。falling_block は775=51。774では50）。
    // パーティクルは773・774・775で同一（copper_fire_flame は773・774・775とも5。
    // trail 49／block_crumble 113／tinted_leaves 36／firefly 114）。
    // チャンクの heightmap は770・771・772・773・774・775とも直接の配列（長さ varint 無し）。""",
        HEADER_775[HEADER_775.find("\n") + 1:],
    ),
    # 成分種別の件数（shift 版は 96→104。実際は 104→110）。
    (
        """    /** 成分種別の件数（774 は 96 件、775 は 104 件）。 */
    private static final int DATA_COMPONENT_TYPE_COUNT_775 = 104;""",
        KIND_COUNT_775,
    ),
    # KIND 表の javadoc（shift 版は 773→774 の 8 件挿入の説明。775 の 6 件に書き直す）。
    (
        """    /**
     * 成分種別の対応表（774 kind → 775 kind）。775 の新規 8 種
     * （use_effects 5、minimum_attack_charge 7、damage_type 8、attack_range 30、
     * piercing_weapon 38、kinetic_weapon 39、swing_animation 40、
     * zombie_nautilus/variant 94）の挿入で 774 の 96 件はすべてずれる。
     */""",
        KIND_JAVADOC_1_775,
    ),
    (
        """    /**
     * 成分種別の対応表（775 kind → 774 kind）。775 の新規 8 種は
     * -1（落とす）。774 の 96 件はすべて 775 にある。
     */""",
        KIND_JAVADOC_2_775,
    ),
    # KIND 表そのもの（shift 版は 773→774 の実写像をずらした 96／104 件。
    # 774→775 の実写像 104／110 件に置き換える）。
    (KIND_774_TO_775_SHIFTED_JAVA, KIND_774_TO_775_JAVA),
    (KIND_775_TO_774_SHIFTED_JAVA, KIND_775_TO_774_JAVA),
    # パーティクル定数（shift 版は 773→774 の番号。775 では pause／reset
    # mob_growth の挿入で 50 番以降が+2：block_crumble 113→115、firefly 114→116。
    # tinted_leaves は774・775とも36、pale_oak は774・775とも35。）
    (
        """    /** 775の block_crumble 粒子のID。 */
    private static final int PARTICLE_775_BLOCK_CRUMBLE = 113;""",
        """    /** 775の block_crumble 粒子のID（775=115。774では113）。 */
    private static final int PARTICLE_775_BLOCK_CRUMBLE = 115;""",
    ),
    (
        """    /** 775の firefly 粒子のID（775=114。774では113）。 */
    private static final int PARTICLE_775_FIREFLY = 114;""",
        """    /** 775の firefly 粒子のID（775=116。774では114）。 */
    private static final int PARTICLE_775_FIREFLY = 116;""",
    ),
    (
        "    /** 775の tinted_leaves 粒子のID（775=36。774では35）。 */",
        "    /** 775の tinted_leaves 粒子のID（774・775とも36）。 */",
    ),
    (
        "    /** 775の pale_oak_leaves 粒子のID（775=35。774では34）。 */",
        "    /** 775の pale_oak_leaves 粒子のID（774・775とも35）。 */",
    ),
    # 船のコメント（happy_ghast は774・775とも58。774では57、ではない）。
    (
        """     * 775の種別を774に直してから774の表を使う（happy_ghast は775=58。
     * 774では57。種別は対応表に通す）。""",
        """     * 775の種別を774に直してから774の表を使う（happy_ghast は774・775とも58。
     * 種別は対応表に通す）。""",
    ),
    # パーティクル節の説明（773・774・775で同一、ではない。pause／reset の挿入で+2）。
    (
        """    // パーティクルは773・774・775で同一のため、ID は対応表に通すだけ
    // （実質そのまま）。trail（773・774・775とも49）／block_crumble
    // （773・774・775とも113）とも duration・ブロック状態ID付き。""",
        """    // パーティクルは pause_mob_growth（50）・reset_mob_growth（51）の挿入で
    // 50番以降が+2ずれるため、ID は対応表に通す。trail（774・775とも49）／
    // block_crumble（774=113→775=115）とも duration・ブロック状態ID付き。
    // entity_effect は774・775とも21。particle item のデータは
    // Slot（774）から ItemStackTemplate（775）へ変わる。""",
    ),
    # メタデータ節の説明（挿入は28番ではなく22・24・29・31番）。
    (
        """    // エンティティメタデータ（774 ⇔ 775）
    // 型 14／15（ブロック状態）は773・774・775で同じ番号のため対応表に
    // 通すだけ（実質そのまま）。型 23（狼バリアント）・24（狼の音）は
    // Holder（varint）のためそのまま写す。型 16／17（粒子）は ID を対応表に通す。
    // 型番号は774→775で28番以降が+1ずれる（28＝zombie_nautilus_variant 挿入）。""",
        """    // エンティティメタデータ（774 ⇔ 775）
    // 型 14／15（ブロック状態）は774・775で同じ番号のため対応表に
    // 通すだけ（実質そのまま）。型 25（狼バリアント）・26（狼の音）は
    // varint のためそのまま写す。型 16／17（粒子）は ID を対応表に通す。
    // 型番号は774→775で22・24・29・31番の挿入（cat／cow／pig／chicken の
    // sound_variant）でずれる（zombie_nautilus_variant は774=28→775=32、
    // humanoid_arm は774=38→775=42）。""",
    ),
    # チャンクデータ節の説明（ずれるのはアイテム・音・ブロック状態のみ。
    # エンティティ・エンチャント・ダメージ種別は同一）。
    (
        """    // チャンクデータ（774 ⇔ 775）
    // 774⇔775ではアイテム・音・エンティティ・エンチャント・
    // ダメージ種別がずれる。ブロック状態・バイオーム・パーティクル・
    // ブロックエンティティ種別・属性・絵画・ジュークボックスは同一。
    // パレットの付け替えは対応表に任せる。""",
        """    // チャンクデータ（774 ⇔ 775）
    // 774⇔775ではアイテム・音・ブロック状態がずれる。エンティティ・
    // エンチャント・ダメージ種別・バイオーム・パーティクル・
    // ブロックエンティティ種別・属性・絵画・ジュークボックスは同一。
    // パレットの付け替えは対応表に任せる。""",
    ),
    # Spawn Entity（上向き）の javadoc（falling_block は774・775とも51。
    # エンティティの挿入は無い）。
    (
        """     * Spawn Entity（774 → 775）。種別を付け替え、falling_block（774=50）の
     * objectData（ブロック状態ID）も付け替える。エンティティは camel_husk
     * （775=20）・nautilus（775=88）・parched（775=97）・zombie_nautilus
     * （775=152）の挿入でずれるため対応表に通す。""",
        """     * Spawn Entity（774 → 775）。種別を付け替え、falling_block（774・775とも51）の
     * objectData（ブロック状態ID）も付け替える。エンティティは774・775とも
     * 157件・同じ順序のため対応表に通すだけ（実質そのまま）。""",
    ),
    # Spawn Entity（上向き）の falling_block 判定（50→51）。
    (
        """            int mappedObjectData = type == 50
                    ? RegistryRemapper.mapBlockState774To775(objectData)
                    : objectData;""",
        """            int mappedObjectData = type == 51
                    ? RegistryRemapper.mapBlockState774To775(objectData)
                    : objectData;""",
    ),
    # エンチャントの javadoc（shift 版は lunge の挿入を語る。774・775とも43件・同一）。
    (
        "    /** enchantments／stored_enchantments を774→775向けに変換する。lunge（775=21）の挿入で21番以降が+1ずれるため対応表に通す。末尾の showTooltip は770・771・772・773・774・775とも無い。 */",
        "    /** enchantments／stored_enchantments を774→775向けに変換する。エンチャントは774・775とも43件・同じ並びのため対応表に通すだけ（実質そのまま）。末尾の showTooltip は770・771・772・773・774・775とも無い。 */",
    ),
    (
        "    /** enchantments／stored_enchantments を775→774向けに変換する。lunge（775=21）は774に無いため落とす。21番以降は-1ずれる。末尾の showTooltip は770・771・772・773・774・775とも無い。 */",
        "    /** enchantments／stored_enchantments を775→774向けに変換する。エンチャントは774・775とも43件・同じ並びのため対応表に通すだけ（実質そのまま）。末尾の showTooltip は770・771・772・773・774・775とも無い。 */",
    ),
    # World Particles（下向き）の javadoc（粒子は同一、ではない）。
    (
        """     * そのまま通す。粒子は770・771・772・773・774・775で同一のため落とすことは無い。""",
        """     * そのまま通す。粒子は pause／reset_mob_growth の挿入で50番以降が+2ずれるため
     * 対応表に通す。落とす粒子は無い。""",
    ),
    # translateParticle775To774 の javadoc（粒子は同一、ではない）。
    (
        """     * パーティクルは770・771・772・773・774・775とも同一（tinted_leaves・firefly も両方に
     * ある）ため常に変換できる（{@code true} を返す）。""",
        """     * パーティクルは pause／reset_mob_growth の挿入で50番以降が+2ずれるため
     * 対応表に通す。落とす粒子は無いため常に変換できる（{@code true} を返す）。""",
    ),

]

# 削除するメソッド（shift 版では不要・誤りのため。replace 後に削除する）。
DELETE_METHODS = [
    # 774節で定義された版数無しのヘルパー4件は、774節を shift すると
    # 775節にもそのまま現れて重複定義になるため、775節から削除する。
    # （774節の原本は残る。EXTRA_METHODS_775 には入れない。）
    "copyResolvableProfile(Reader reader, Writer writer)",
    "copyGameProfileProperties(Reader reader, Writer writer)",
    "copyOptionalString(Reader reader, Writer writer)",
    "copyOptionalUuid(Reader reader, Writer writer)",
]


# ----------------------------------------------------------------------
# ChunkTranslator 775 節のメソッド置き換え
# ----------------------------------------------------------------------

CHUNK_METHOD_REPLACEMENTS = {
"translateComponent774To775(Reader reader, Writer writer, int kind774)": """    /**
     * 成分1個を774→775向けに変換する。種別番号は774の番号で dispatch する
     * （774は104件、775は110件。KIND 表での付け替えは呼び出し側で行う）。
     * 774の成分はすべて775にあるため落とす成分は無い。レジストリIDを付け替える
     * ものだけ変換する。5 use_effects、7 minimum_attack_charge、8 damage_type、
     * 30 attack_range、38 piercing_weapon、39 kinetic_weapon、40 swing_animation は
     * 774・775とも同じ番号のためここで扱う（773→774の新規種別。775でも同じ形式）。
     * 25 use_remainder、47・48・73 の中身は Slot から ItemStackTemplate へ変わる。
     * 93 chicken/variant、94 zombie_nautilus/variant は bool 形式から
     * registryEntryHolder へ変わる。
     */
    private static boolean translateComponent774To775(Reader reader, Writer writer, int kind774) {
        switch (kind774) {
            case 4 -> { } // unbreakable（void。770・771・772・773・774・775とも中身なし）
            case 13, 41 -> translateEnchantments774To775(reader, writer);
            case 14, 15 -> translateItemBlockPredicates774To775(reader, writer);
            case 16 -> translateAttributeModifiers774To775(reader, writer);
            case 17 -> translateCustomModelData774To775(reader, writer);
            case 18 -> translateTooltipDisplay774To775(reader, writer);
            case 19 -> writer.writeVarInt(reader.readVarInt()); // repair_cost（varint）
            case 21 -> writer.writeBoolean(reader.readBoolean()); // enchantment_glint_override
            case 23 -> translateFood774To775(reader, writer);
            case 24 -> translateConsumable774To775(reader, writer);
            case 25 -> translateUseRemainder774To775(reader, writer);
            case 28 -> translateTool774To775(reader, writer);
            case 29 -> { // weapon：varint＋float（レジストリ無し）
                writer.writeVarInt(reader.readVarInt());
                writer.writeFloat(reader.readFloat());
            }
            case 32 -> translateEquippable774To775(reader, writer);
            case 33 -> translateRepairable774To775(reader, writer);
            case 36 -> translateDeathProtection774To775(reader, writer);
            case 37 -> translateBlocksAttacks774To775(reader, writer);
            case 42 -> translateDyedColor774To775(reader, writer);
            case 47, 48 -> translateSlotArray774To775(reader, writer);
            case 50 -> writer.writeFloat(reader.readFloat()); // potion_duration_scale
            case 54 -> {
                return translateTrim774To775(reader, writer);
            }
            case 59 -> {
                return translateInstrument774To775(reader, writer);
            }
            case 60 -> copyProvidesTrimMaterial774To775(reader, writer);
            case 62 -> translateJukeboxPlayable774To775(reader, writer);
            case 63 -> writer.writeString(reader.readString()); // provides_banner_patterns
            case 69 -> translateNoteBlockSound774To775(reader, writer);
            case 70 -> translateBannerPatterns774To775(reader, writer);
            case 72 -> translatePotDecorations774To775(reader, writer);
            case 73 -> translateContainer774To775(reader, writer);
            case 78 -> translateBreakSound774To775(reader, writer);
            case 79, 80, 81, 82, 83, 84, 85, 86, 87, 88, 89, 90, 91, 92,
                    95, 96, 98, 99, 100, 101, 102, 103 -> writer.writeVarInt(reader.readVarInt()); // 各種 variant／collar／color
            case 93, 94 -> translateVariantHolder774To775(reader, writer); // chicken／zombie_nautilus variant
            case 97 -> copyPaintingVariantHolder774To775(reader, writer);
            case 5 -> { // use_effects：bool＋bool＋f32
                writer.writeBoolean(reader.readBoolean()); // can_sprint
                writer.writeBoolean(reader.readBoolean()); // interact_vibrations
                writer.writeFloat(reader.readFloat()); // speed_multiplier
            }
            case 7 -> writer.writeFloat(reader.readFloat()); // minimum_attack_charge
            case 8 -> translateDamageType774To775(reader, writer);
            case 30 -> { // attack_range：f32×6
                for (int i = 0; i < 6; i++) {
                    writer.writeFloat(reader.readFloat());
                }
            }
            case 38 -> translatePiercingWeapon774To775(reader, writer);
            case 39 -> translateKineticWeapon774To775(reader, writer);
            case 40 -> { // swing_animation：varint＋varint
                writer.writeVarInt(reader.readVarInt()); // type（none／whack／stab）
                writer.writeVarInt(reader.readVarInt()); // duration
            }
            default -> copyComponentOpaque775From774(reader, writer, kind774);
        }
        return true;
    }
""",
"translateComponent775To774(Reader reader, Writer writer, int kind775)": """    /**
     * 成分1個を775→774向けに変換する。種別番号は775の番号で dispatch する
     * （775は110件、774は104件。KIND 表の対応は mc-26.1-protocol.json の
     * SlotComponentType による）。774に無い新規 6 種（41 additional_trade_cost、
     * 43 dye、94 pig/sound_variant、96 cow/sound_variant、98 chicken/sound_variant、
     * 106 cat/sound_variant）は中身を読み飛ばして {@code false} を返す
     * （呼び出し側が落とす）。レジストリIDを付け替えるものだけ変換する。
     * 49・50 の中身は ItemStackTemplate から Slot へ、75 container は
     * option 付き ItemStackTemplate の列から Slot の列へ変わる。
     * 97 chicken/variant、99 zombie_nautilus/variant は registryEntryHolder から
     * bool 形式へ変わる。
     */
    private static boolean translateComponent775To774(Reader reader, Writer writer, int kind775) {
        switch (kind775) {
            case 4 -> { } // unbreakable（void。770・771・772・773・774・775とも中身なし）
            case 13, 42 -> translateEnchantments775To774(reader, writer);
            case 14, 15 -> translateItemBlockPredicates775To774(reader, writer);
            case 16 -> translateAttributeModifiers775To774(reader, writer);
            case 17 -> translateCustomModelData775To774(reader, writer);
            case 18 -> translateTooltipDisplay775To774(reader, writer);
            case 19 -> writer.writeVarInt(reader.readVarInt()); // repair_cost（varint）
            case 21 -> writer.writeBoolean(reader.readBoolean()); // enchantment_glint_override
            case 23 -> translateFood775To774(reader, writer);
            case 24 -> translateConsumable775To774(reader, writer);
            case 25 -> translateUseRemainder775To774(reader, writer);
            case 28 -> translateTool775To774(reader, writer);
            case 29 -> { // weapon：varint＋float（レジストリ無し）
                writer.writeVarInt(reader.readVarInt());
                writer.writeFloat(reader.readFloat());
            }
            case 32 -> translateEquippable775To774(reader, writer);
            case 33 -> translateRepairable775To774(reader, writer);
            case 36 -> translateDeathProtection775To774(reader, writer);
            case 37 -> translateBlocksAttacks775To774(reader, writer);
            case 44 -> translateDyedColor775To774(reader, writer);
            case 49, 50 -> translateSlotArray775To774(reader, writer);
            case 52 -> writer.writeFloat(reader.readFloat()); // potion_duration_scale
            case 56 -> {
                return translateTrim775To774(reader, writer);
            }
            case 61 -> {
                return translateInstrument775To774(reader, writer);
            }
            case 62 -> copyProvidesTrimMaterial775To774(reader, writer);
            case 64 -> translateJukeboxPlayable775To774(reader, writer);
            case 65 -> writer.writeString(reader.readString()); // provides_banner_patterns
            case 71 -> translateNoteBlockSound775To774(reader, writer);
            case 72 -> translateBannerPatterns775To774(reader, writer);
            case 74 -> translatePotDecorations775To774(reader, writer);
            case 75 -> translateContainer775To774(reader, writer);
            case 80 -> translateBreakSound775To774(reader, writer);
            case 81, 82, 83, 84, 85, 86, 87, 88, 89, 90, 91, 92, 93, 95,
                    100, 101, 103, 104, 105, 107, 108, 109 -> writer.writeVarInt(reader.readVarInt()); // 各種 variant／collar／color
            case 97, 99 -> translateVariantHolder775To774(reader, writer); // chicken／zombie_nautilus variant
            case 102 -> copyPaintingVariantHolder775To774(reader, writer);
            case 5 -> { // use_effects：bool＋bool＋f32
                writer.writeBoolean(reader.readBoolean()); // can_sprint
                writer.writeBoolean(reader.readBoolean()); // interact_vibrations
                writer.writeFloat(reader.readFloat()); // speed_multiplier
            }
            case 7 -> writer.writeFloat(reader.readFloat()); // minimum_attack_charge
            case 8 -> translateDamageType775To774(reader, writer);
            case 30 -> { // attack_range：f32×6
                for (int i = 0; i < 6; i++) {
                    writer.writeFloat(reader.readFloat());
                }
            }
            case 38 -> translatePiercingWeapon775To774(reader, writer);
            case 39 -> translateKineticWeapon775To774(reader, writer);
            case 40 -> { // swing_animation：varint＋varint
                writer.writeVarInt(reader.readVarInt()); // type（none／whack／stab）
                writer.writeVarInt(reader.readVarInt()); // duration
            }
            case 41, 43, 94, 96, 98, 106 -> {
                // 775 の新規成分。774 に無いため中身を読み飛ばして落とす。
                skipComponentData775(reader, kind775);
                return false;
            }
            default -> copyComponentOpaque775(reader, writer, kind775);
        }
        return true;
    }
""",
"skipComponentData775(Reader reader, int kind775)": """    /**
     * 成分データ1個分を読み飛ばす（775形式）。種別番号は775の番号で
     * dispatch する。775の番号では 18＝tooltip_display、19＝repair_cost、
     * 21＝enchantment_glint_override、29＝weapon、37＝blocks_attacks、
     * 52＝potion_duration_scale、62＝provides_trim_material、
     * 65＝provides_banner_patterns、80＝break_sound、97＝chicken/variant、
     * 99＝zombie_nautilus/variant、102＝painting/variant。
     * 775の新規 6 種（41 additional_trade_cost、43 dye、94 pig/sound_variant、
     * 96 cow/sound_variant、98 chicken/sound_variant、106 cat/sound_variant）も
     * ここで読み飛ばす（98 は Holder、他は varint）。形式は
     * mc-26.1-protocol.json の SlotComponent による。
     * それ以外の種別は 774 に直して skipComponentData774 に委譲する。
     */
    private static void skipComponentData775(Reader reader, int kind775) {
        switch (kind775) {
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
            case 37 -> skipBlocksAttacks775(reader);
            case 52 -> reader.readFloat(); // potion_duration_scale
            case 62 -> skipProvidesTrimMaterial775(reader);
            case 65 -> reader.readString(); // provides_banner_patterns
            case 80 -> ExtraPlayTranslator.skipSoundHolder(reader); // break_sound
            case 97 -> { // chicken/variant（Holder：varint。0 なら文字列）
                if (reader.readVarInt() == 0) {
                    reader.readString();
                }
            }
            case 99 -> { // zombie_nautilus/variant（chicken/variant と同じ形式）
                if (reader.readVarInt() == 0) {
                    reader.readString();
                }
            }
            case 102 -> skipPaintingVariantHolder775(reader);
            case 41, 43, 81, 82, 83, 84, 85, 86, 87, 88, 89, 90, 91, 92, 93, 94,
                    95, 96, 100, 101, 103, 104, 105, 106, 107, 108, 109 -> reader.readVarInt(); // 各種 variant／collar／color／dye／sound_variant
            case 5 -> { // use_effects：bool＋bool＋f32
                reader.readBoolean(); // can_sprint
                reader.readBoolean(); // interact_vibrations
                reader.readFloat(); // speed_multiplier
            }
            case 7 -> reader.readFloat(); // minimum_attack_charge
            case 8 -> { // damage_type：bool＋Holder／string（Holder が 0 なら直書きの DamageTypeData）
                if (reader.readBoolean()) {
                    if (reader.readVarInt() == 0) {
                        reader.readString(); // msgId
                        reader.readVarInt(); // scaling
                        reader.readFloat(); // exhaustion
                        reader.readVarInt(); // effects
                        reader.readVarInt(); // deathMessageType
                    }
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
            case 25 -> skipItemStackTemplate775(reader); // use_remainder
            case 49, 50 -> { // bundle_contents／charged_projectiles：ItemStackTemplate の列
                int count = reader.readVarInt();
                for (int i = 0; i < count; i++) {
                    skipItemStackTemplate775(reader);
                }
            }
            case 75 -> { // container：option 付き ItemStackTemplate の列
                int count = reader.readVarInt();
                for (int i = 0; i < count; i++) {
                    if (reader.readBoolean()) {
                        skipItemStackTemplate775(reader);
                    }
                }
            }
            case 98 -> { // chicken/sound_variant（Holder：varint。0 なら文字列）
                if (reader.readVarInt() == 0) {
                    reader.readString();
                }
            }
            default -> {
                int kind774 = mapComponentKind775To774(kind775);
                if (kind774 < 0) {
                    throw new IllegalArgumentException("未知の成分種別です（775）: " + kind775);
                }
                skipComponentData774(reader, kind774);
            }
        }
    }
""",
"translateDataComponentMatchers774To775(Reader reader, Writer writer)": """    /** DataComponentMatchers（774→775）。exact は成分の列、partial は種別の列。種別番号は KIND 表で付け替える（774の成分はすべて775にあるため落とすことは無い）。 */
    private static void translateDataComponentMatchers774To775(Reader reader, Writer writer) {
        int exact = reader.readVarInt();
        Writer kept = new Writer();
        int keptCount = 0;
        for (int i = 0; i < exact; i++) {
            int kind774 = reader.readVarInt();
            Writer component = new Writer();
            if (!translateComponent774To775(reader, component, kind774)) {
                continue;
            }
            kept.writeVarInt(mapComponentKind774To775(kind774));
            kept.writeBytes(component.bytes());
            keptCount++;
        }
        writer.writeVarInt(keptCount);
        writer.writeBytes(kept.bytes());
        int partial = reader.readVarInt();
        writer.writeVarInt(partial);
        for (int i = 0; i < partial; i++) {
            writer.writeVarInt(mapComponentKind774To775(reader.readVarInt()));
        }
    }
""",
"translateSlotComponents775To774(Reader reader, Writer writer)": """    /** スロットの added／removed 成分部分だけを775→774向けに変換する。種別番号は KIND 表で付け替える（774は104件、775は110件）。775 の新規 6 種は落とす。 */
    private static void translateSlotComponents775To774(Reader reader, Writer writer) {
        int added = reader.readVarInt();
        Writer kept = new Writer();
        int keptCount = 0;
        for (int i = 0; i < added; i++) {
            int kind775 = reader.readVarInt();
            int kind774 = mapComponentKind775To774(kind775);
            if (kind774 < 0) {
                skipComponentData775(reader, kind775);
                continue;
            }
            Writer component = new Writer();
            if (!translateComponent775To774(reader, component, kind775)) {
                continue;
            }
            kept.writeVarInt(kind774);
            kept.writeBytes(component.bytes());
            keptCount++;
        }
        writer.writeVarInt(keptCount);
        writer.writeBytes(kept.bytes());
        int removed = reader.readVarInt();
        Writer keptRemoved = new Writer();
        int keptRemovedCount = 0;
        for (int i = 0; i < removed; i++) {
            int kind774 = mapComponentKind775To774(reader.readVarInt());
            if (kind774 < 0) {
                continue;
            }
            keptRemoved.writeVarInt(kind774);
            keptRemovedCount++;
        }
        writer.writeVarInt(keptRemovedCount);
        writer.writeBytes(keptRemoved.bytes());
    }
""",
"translateComponentList775To774(Reader reader, Writer writer)": """    /** 成分の列（個数 VarInt＋成分1個ずつ）を775→774向けに変換する。種別番号は KIND 表で付け替える（774は104件、775は110件）。775 の新規 6 種は落とす。 */
    private static void translateComponentList775To774(Reader reader, Writer writer) {
        int added = reader.readVarInt();
        Writer kept = new Writer();
        int keptCount = 0;
        for (int i = 0; i < added; i++) {
            int kind775 = reader.readVarInt();
            int kind774 = mapComponentKind775To774(kind775);
            if (kind774 < 0) {
                skipComponentData775(reader, kind775);
                continue;
            }
            Writer component = new Writer();
            if (!translateComponent775To774(reader, component, kind775)) {
                continue;
            }
            kept.writeVarInt(kind774);
            kept.writeBytes(component.bytes());
            keptCount++;
        }
        writer.writeVarInt(keptCount);
        writer.writeBytes(kept.bytes());
    }
""",
"translateTooltipDisplay775To774(Reader reader, Writer writer)": """    /** tooltip_display（775→774）。770・771・772・773・774・775とも {hideTooltip bool, hiddenComponents の列} のためそのまま写す。 */
    private static void translateTooltipDisplay775To774(Reader reader, Writer writer) {
        writer.writeBoolean(reader.readBoolean()); // hideTooltip
        int hidden = reader.readVarInt();
        writer.writeVarInt(hidden);
        for (int i = 0; i < hidden; i++) {
            writer.writeVarInt(reader.readVarInt());
        }
    }
""",
"translateUnbreakable774To775(Reader reader, Writer writer)": """    /** unbreakable を774→775向けに変換する。770・771・772・773・774・775とも void のため何もしない。 */
    private static void translateUnbreakable774To775(Reader reader, Writer writer) {
    }
""",
"translateUnbreakable775To774(Reader reader, Writer writer)": """    /** unbreakable を775→774向けに変換する。770・771・772・773・774・775とも void のため何もしない。 */
    private static void translateUnbreakable775To774(Reader reader, Writer writer) {
    }
""",
"translateDyedColor774To775(Reader reader, Writer writer)": """    /** dyed_color を774→775向けに変換する。770・771・772・773・774・775とも色 i32 だけ。 */
    private static void translateDyedColor774To775(Reader reader, Writer writer) {
        writer.writeInt(reader.readInt()); // color
    }
""",
"translateDyedColor775To774(Reader reader, Writer writer)": """    /** dyed_color を775→774向けに変換する。770・771・772・773・774・775とも色 i32 だけ。 */
    private static void translateDyedColor775To774(Reader reader, Writer writer) {
        writer.writeInt(reader.readInt()); // color
    }
""",
"copyPaintingVariantHolder774To775(Reader reader, Writer writer)": """    /** painting/variant（774→775）の Holder を変換する。絵画は774・775とも51種・同じ並びのため対応表に通すだけ（実質そのまま）。 */
    private static void copyPaintingVariantHolder774To775(Reader reader, Writer writer) {
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
        int mapped = RegistryRemapper.mapPaintingVariant774To775(holder - 1);
        if (mapped == RegistryRemapper.DROP) {
            // 範囲外の絵画ID。落とす表現が無いため kebab（0）で代用する。
            mapped = 0;
        }
        writer.writeVarInt(mapped + 1);
    }
""",
"copyPaintingVariantHolder775To774(Reader reader, Writer writer)": """    /** painting/variant（775→774）の Holder を変換する。絵画は774・775とも51種・同じ並びのため対応表に通すだけ（実質そのまま）。 */
    private static void copyPaintingVariantHolder775To774(Reader reader, Writer writer) {
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
        int mapped = RegistryRemapper.mapPaintingVariant775To774(holder - 1);
        if (mapped == RegistryRemapper.DROP) {
            // 範囲外の絵画ID。落とす表現が無いため kebab（0）で代用する。
            mapped = 0;
        }
        writer.writeVarInt(mapped + 1);
    }
""",

"translateParticleData775To774(Reader reader, Writer writer, int particleId)": """    /** パーティクルのデータ部分を775→774向けに変換する（particleId は775のID）。pause_mob_growth（50）・reset_mob_growth（51）は775にだけあるため来ない。item のデータは ItemStackTemplate から Slot へ変わる。tinted_leaves／dragon_breath／effect／instant_effect／flash もデータ付きで変換する（774節では抜けていた）。 */
    private static void translateParticleData775To774(Reader reader, Writer writer, int particleId) {
        switch (particleId) {
            case 1, 2, 29, 111 -> // block、block_marker、falling_dust、dust_pillar：ブロック状態IDを付け替える
                    writer.writeVarInt(RegistryRemapper.mapBlockStateUp775(reader.readVarInt()));
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
            case 47 -> translateItemStackTemplate775To774AsSlot(reader, writer); // item：ItemStackTemplate から Slot へ
            case 48 -> translateVibration(reader, writer); // vibration（同一形式）
            case 105 -> writer.writeVarInt(reader.readVarInt()); // shriek：遅延 tick 数
            case 49 -> { // trail：target vec3f64＋color i32＋duration
                writer.writeDouble(reader.readDouble());
                writer.writeDouble(reader.readDouble());
                writer.writeDouble(reader.readDouble());
                writer.writeInt(reader.readInt());
                writer.writeVarInt(reader.readVarInt()); // duration
            }
            case 115 -> // block_crumble：ブロック状態IDを付け替える
                    writer.writeVarInt(RegistryRemapper.mapBlockStateUp775(reader.readVarInt()));
            case 36 -> writer.writeInt(reader.readInt()); // tinted_leaves：色の i32
            case 8 -> writer.writeFloat(reader.readFloat()); // dragon_breath：威力
            case 16 -> { // effect：色 i32＋大きさ f32
                writer.writeInt(reader.readInt());
                writer.writeFloat(reader.readFloat());
            }
            case 46 -> { // instant_effect：色 i32＋大きさ f32
                writer.writeInt(reader.readInt());
                writer.writeFloat(reader.readFloat());
            }
            case 42 -> writer.writeInt(reader.readInt()); // flash：色の i32
            default -> {
                // その他の粒子にデータは無い。
            }
        }
    }
""",        "translateMetadataValue774To775(Reader reader, Writer writer, int type)": """    /**
     * メタデータの値1個を774→775向けに変換する。落とすエントリは {@code false} を返す。
     * 型番号は774側の番号で dispatch する。774の番号一覧：
     * 0 byte、1 varint、2 varlong、3 float、4 string、5 component、6 optional_component、
     * 7 slot、8 bool、9 rotations、10 block_pos、11 optional_block_pos、12 direction、
     * 13 optional_uuid、14／15 blockstate、16 particle、17 particles、18 villager_data、
     * 19 optional_unsigned_int、20 pose、21 cat、22 cow、23 wolf_variant、24 wolf_sound、
     * 25 frog、26 pig、27 chicken、28 zombie_nautilus_variant、29 optional_global_pos、
     * 30 painting、31 sniffer、32 armadillo、33 copper_golem、34 weathering、35 vector3、
     * 36 quaternion、37 resolvable_profile、38 humanoid_arm。
     * 775では 22・24・29・31番に cat／cow／pig／chicken の sound_variant が入る。
     * 型番号の付け替えは mapMetadataType774To775 が行う。774の型はすべて775にある。
     */
    private static boolean translateMetadataValue774To775(Reader reader, Writer writer, int type) {
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
            case 7 -> translateSlot774To775(reader, writer);
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
            case 14, 15 -> writer.writeVarInt(RegistryRemapper.mapBlockState774To775(reader.readVarInt()));
            case 16 -> translateParticle774To775(reader, writer);
            case 17 -> {
                int count = reader.readVarInt();
                writer.writeVarInt(count);
                for (int i = 0; i < count; i++) {
                    translateParticle774To775(reader, writer);
                }
            }
            case 18 -> { // villager_data：type／profession／level
                writer.writeVarInt(reader.readVarInt());
                writer.writeVarInt(reader.readVarInt());
                writer.writeVarInt(reader.readVarInt());
            }
            case 19, 20, 21, 22 -> writer.writeVarInt(reader.readVarInt()); // optional_unsigned_int／pose／cat／cow
            case 23 -> translateWolfVariantHolder774To775(reader, writer);
            case 24, 25, 26, 27 -> writer.writeVarInt(reader.readVarInt()); // wolf_sound／frog／pig／chicken
            case 28 -> writer.writeVarInt(reader.readVarInt()); // zombie_nautilus_variant
            case 29 -> { // optional_global_pos
                boolean present = reader.readBoolean();
                writer.writeBoolean(present);
                if (present) {
                    writer.writeString(reader.readString());
                    writer.writePositionRaw(reader.readPositionRaw());
                }
            }
            case 30 -> {
                return translatePaintingVariantHolder774To775(reader, writer);
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
            case 37 -> copyResolvableProfile(reader, writer); // resolvable_profile（774・775で同一形式）
            case 38 -> writer.writeVarInt(reader.readVarInt()); // humanoid_arm
            default -> throw new IllegalArgumentException("未知のメタデータ型です: " + type);
        }
        return true;
    }
""",
    "translateMetadataValue775To774(Reader reader, Writer writer, int type)": """    /**
     * メタデータの値1個を775→774向けに変換する。落とすエントリは {@code false} を返す。
     * 型番号は775側の番号で dispatch する。775の番号一覧：
     * 0 byte、1 varint、2 varlong、3 float、4 string、5 component、6 optional_component、
     * 7 slot、8 bool、9 rotations、10 block_pos、11 optional_block_pos、12 direction、
     * 13 optional_uuid、14／15 blockstate、16 particle、17 particles、18 villager_data、
     * 19 optional_unsigned_int、20 pose、21 cat、22 cat_sound、23 cow、24 cow_sound、
     * 25 wolf_variant、26 wolf_sound、27 frog、28 pig、29 pig_sound、30 chicken、
     * 31 chicken_sound、32 zombie_nautilus_variant、33 optional_global_pos、
     * 34 painting、35 sniffer、36 armadillo、37 copper_golem、38 weathering、39 vector3、
     * 40 quaternion、41 resolvable_profile、42 humanoid_arm。
     * 774に無い cat_sound（22）・cow_sound（24）・pig_sound（29）・chicken_sound（31）は
     * 項目ごと落とす。型番号の付け替えは mapMetadataType775To774 が行う。
     */
    private static boolean translateMetadataValue775To774(Reader reader, Writer writer, int type) {
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
            case 7 -> translateSlot775To774(reader, writer);
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
            case 14, 15 -> writer.writeVarInt(RegistryRemapper.mapBlockStateUp775(reader.readVarInt()));
            case 16 -> translateParticle775To774(reader, writer);
            case 17 -> {
                int count = reader.readVarInt();
                writer.writeVarInt(count);
                for (int i = 0; i < count; i++) {
                    translateParticle775To774(reader, writer);
                }
            }
            case 18 -> { // villager_data：type／profession／level
                writer.writeVarInt(reader.readVarInt());
                writer.writeVarInt(reader.readVarInt());
                writer.writeVarInt(reader.readVarInt());
            }
            case 19, 20, 21 -> writer.writeVarInt(reader.readVarInt()); // optional_unsigned_int／pose／cat
            case 22, 24, 29, 31 -> { // cat／cow／pig／chicken の sound_variant（774に無い）
                reader.readVarInt();
                return false;
            }
            case 23 -> writer.writeVarInt(reader.readVarInt()); // cow_variant
            case 25 -> translateWolfVariantHolder775To774(reader, writer);
            case 26, 27, 28, 30, 32 -> writer.writeVarInt(reader.readVarInt()); // wolf_sound／frog／pig／chicken／zombie_nautilus
            case 33 -> { // optional_global_pos
                boolean present = reader.readBoolean();
                writer.writeBoolean(present);
                if (present) {
                    writer.writeString(reader.readString());
                    writer.writePositionRaw(reader.readPositionRaw());
                }
            }
            case 34 -> {
                return translatePaintingVariantHolder775To774(reader, writer);
            }
            case 35, 36, 37, 38 -> writer.writeVarInt(reader.readVarInt()); // sniffer／armadillo／copper_golem／weathering
            case 39 -> { // vector3
                writer.writeFloat(reader.readFloat());
                writer.writeFloat(reader.readFloat());
                writer.writeFloat(reader.readFloat());
            }
            case 40 -> { // quaternion
                writer.writeFloat(reader.readFloat());
                writer.writeFloat(reader.readFloat());
                writer.writeFloat(reader.readFloat());
                writer.writeFloat(reader.readFloat());
            }
            case 41 -> copyResolvableProfile(reader, writer); // resolvable_profile（774・775で同一形式）
            case 42 -> writer.writeVarInt(reader.readVarInt()); // humanoid_arm
            default -> throw new IllegalArgumentException("未知のメタデータ型です: " + type);
        }
        return true;
    }
""",
    "mapMetadataType774To775(int type774)": """    /**
     * メタデータの型番号を774→775に付け替える。775は cat_sound_variant（22）、
     * cow_sound_variant（24）、pig_sound_variant（29）、chicken_sound_variant（31）を
     * 挿入した。774の型は38まで（0–38）。22・24・29・31番の挿入でずれる。
     * 774の型はすべて775にあるため -1 は返さない。範囲外はそのまま返す。
     */
    private static int mapMetadataType774To775(int type774) {
        if (type774 < 0 || type774 > 38) {
            return type774;
        }
        if (type774 < 22) {
            return type774;
        }
        if (type774 < 24) {
            return type774 + 1;
        }
        if (type774 < 29) {
            return type774 + 2;
        }
        if (type774 < 31) {
            return type774 + 3;
        }
        return type774 + 4;
    }
""",
    "mapMetadataType775To774(int type775)": """    /**
     * メタデータの型番号を775→774に付け替える。775の型は42まで（0–42）。
     * cat_sound_variant（22）、cow_sound_variant（24）、pig_sound_variant（29）、
     * chicken_sound_variant（31）は774に無いため -1 を返す。22・24・29・31番の
     * 挿入分を引く。範囲外はそのまま返す。
     */
    private static int mapMetadataType775To774(int type775) {
        if (type775 < 0 || type775 > 42) {
            return type775;
        }
        if (type775 == 22 || type775 == 24 || type775 == 29 || type775 == 31) {
            return -1;
        }
        if (type775 < 22) {
            return type775;
        }
        if (type775 < 24) {
            return type775 - 1;
        }
        if (type775 < 29) {
            return type775 - 2;
        }
        if (type775 < 31) {
            return type775 - 3;
        }
        return type775 - 4;
    }
""",
    "skipMetadataValue775(Reader reader, int type775)": """    /**
     * メタデータの値1個を読み飛ばす（775形式）。translateEntityMetadata775To774
     * から型番号の付け替えで落とす（mappedType &lt; 0）場合にだけ呼ばれる。
     * 775→774 で落とすのは cat_sound_variant（22）、cow_sound_variant（24）、
     * pig_sound_variant（29）、chicken_sound_variant（31）の 4型のみで、
     * どれも varint のためここではそれだけを扱う。
     */
    private static void skipMetadataValue775(Reader reader, int type775) {
        switch (type775) {
            case 22, 24, 29, 31 -> reader.readVarInt(); // cat／cow／pig／chicken の sound_variant
            default -> throw new IllegalArgumentException("読み飛ばせないメタデータ型です: " + type775);
        }
    }
""",
    "translateParticleData774To775(Reader reader, Writer writer, int particleId)": """    /** パーティクルのデータ部分を774→775向けに変換する（particleId は774のID）。pause_mob_growth（50）・reset_mob_growth（51）の挿入で50番以降が+2ずれる。item のデータは Slot から ItemStackTemplate へ変わる。tinted_leaves／dragon_breath／effect／instant_effect／flash もデータ付きで変換する（774節では抜けていた）。 */
    private static void translateParticleData774To775(Reader reader, Writer writer, int particleId) {
        switch (particleId) {
            case 1, 2, 29, 109 -> // block、block_marker、falling_dust、dust_pillar：ブロック状態IDを付け替える
                    writer.writeVarInt(RegistryRemapper.mapBlockState774To775(reader.readVarInt()));
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
            case 47 -> translateSlot774To775AsTemplate(reader, writer); // item：Slot から ItemStackTemplate へ
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
                    writer.writeVarInt(RegistryRemapper.mapBlockState774To775(reader.readVarInt()));
            case 36 -> writer.writeInt(reader.readInt()); // tinted_leaves：色の i32
            case 8 -> writer.writeFloat(reader.readFloat()); // dragon_breath：威力
            case 16 -> { // effect：色 i32＋大きさ f32
                writer.writeInt(reader.readInt());
                writer.writeFloat(reader.readFloat());
            }
            case 46 -> { // instant_effect：色 i32＋大きさ f32
                writer.writeInt(reader.readInt());
                writer.writeFloat(reader.readFloat());
            }
            case 42 -> writer.writeInt(reader.readInt()); // flash：色の i32
            default -> {
                // その他の粒子にデータは無い。
            }
        }
    }
""",
    "translateSlotArray774To775(Reader reader, Writer writer)": """    /** スロットの配列を774→775向けに変換する。中身は Slot から ItemStackTemplate へ変わる。 */
    private static void translateSlotArray774To775(Reader reader, Writer writer) {
        int count = reader.readVarInt();
        writer.writeVarInt(count);
        for (int i = 0; i < count; i++) {
            translateSlot774To775AsTemplate(reader, writer);
        }
    }
""",
    "translateSlotArray775To774(Reader reader, Writer writer)": """    /** スロットの配列を775→774向けに変換する。中身は ItemStackTemplate から Slot へ変わる。 */
    private static void translateSlotArray775To774(Reader reader, Writer writer) {
        int count = reader.readVarInt();
        writer.writeVarInt(count);
        for (int i = 0; i < count; i++) {
            translateItemStackTemplate775To774AsSlot(reader, writer);
        }
    }
""",
    "translateUseRemainder774To775(Reader reader, Writer writer)": """    /** use_remainder を774→775向けに変換する。Slot から ItemStackTemplate へ変わる。 */
    private static void translateUseRemainder774To775(Reader reader, Writer writer) {
        translateSlot774To775AsTemplate(reader, writer);
    }
""",
    "translateUseRemainder775To774(Reader reader, Writer writer)": """    /** use_remainder を775→774向けに変換する。ItemStackTemplate から Slot へ変わる。 */
    private static void translateUseRemainder775To774(Reader reader, Writer writer) {
        translateItemStackTemplate775To774AsSlot(reader, writer);
    }
""",

}


# 775節に新しく追加するヘルパー（shift 版には無いもの）。
# copyResolvableProfile 等は 774節の shift で既に 775節にあるためここには
# 入れない（重複するとコンパイルエラーになる）。ここに入れるのは 774→775 で
# 形式が変わる成分のための新規ヘルパーだけ。
EXTRA_METHODS_775 = """
    /**
     * スロット1個を774→775向けに ItemStackTemplate として変換する。
     * 774の Slot は [count][itemId][components]、775の ItemStackTemplate は
     * [itemId][count][added][removed][components][removeComponents]。
     * 空スロット（count 0）は Template にならない（itemId が無い）ため、
     * stone（itemId 1）・count 0・成分 0 件として書く（実在の particle item や
     * use_remainder が空になることは無い。あくまで桁合わせの保険）。
     * itemId は mapItem774To775、成分は translateSlotComponents774To775 で変換する。
     */
    private static void translateSlot774To775AsTemplate(Reader reader, Writer writer) {
        int count = reader.readVarInt();
        if (count <= 0) {
            writer.writeVarInt(1); // stone（空の保険）
            writer.writeVarInt(0); // count
            writer.writeVarInt(0); // added
            writer.writeVarInt(0); // removed
            return;
        }
        int itemId = reader.readVarInt();
        writer.writeVarInt(RegistryRemapper.mapItem774To775(itemId));
        writer.writeVarInt(count);
        translateSlotComponents774To775(reader, writer);
    }

    /**
     * ItemStackTemplate 1個を775→774向けに Slot として変換する。
     * [itemId][count][added][removed][components][removeComponents] から
     * [count][itemId][components] へ。itemId は mapItemUp775、成分は
     * translateSlotComponents775To774 で変換する。removeComponents は
     * Slot には無いため読むだけで捨てる（成分の除去指定は 774 の Slot では
     * 表せない。774 側は added の成分だけを持つ）。
     */
    private static void translateItemStackTemplate775To774AsSlot(Reader reader, Writer writer) {
        int itemId = reader.readVarInt();
        int count = reader.readVarInt();
        writer.writeVarInt(count);
        writer.writeVarInt(RegistryRemapper.mapItemUp775(itemId));
        translateSlotComponents775To774(reader, writer);
    }

    /**
     * container（774→775）。774は Slot の列、775は option 付き
     * ItemStackTemplate の列。空スロットは option false、非空は option true＋Template。
     */
    private static void translateContainer774To775(Reader reader, Writer writer) {
        int count = reader.readVarInt();
        writer.writeVarInt(count);
        for (int i = 0; i < count; i++) {
            int slotCount = reader.readVarInt();
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
            translateSlotComponents774To775(reader, writer);
        }
    }

    /**
     * container（775→774）。775は option 付き ItemStackTemplate の列、
     * 774は Slot の列。option false は空スロット（count 0）にする。
     */
    private static void translateContainer775To774(Reader reader, Writer writer) {
        int count = reader.readVarInt();
        writer.writeVarInt(count);
        for (int i = 0; i < count; i++) {
            if (!reader.readBoolean()) {
                writer.writeVarInt(0); // 空スロット
                continue;
            }
            translateItemStackTemplate775To774AsSlot(reader, writer);
        }
    }

    /**
     * ItemStackTemplate 1個分を読み飛ばす（775形式）。skipComponentData775 の
     * use_remainder／bundle_contents／charged_projectiles／container から使う。
     */
    private static void skipItemStackTemplate775(Reader reader) {
        reader.readVarInt(); // itemId
        reader.readVarInt(); // itemCount
        int added = reader.readVarInt();
        for (int i = 0; i < added; i++) {
            int kind775 = reader.readVarInt();
            skipComponentData775(reader, kind775);
        }
        int removed = reader.readVarInt();
        for (int i = 0; i < removed; i++) {
            reader.readVarInt(); // removeComponents の種別
        }
    }

    /**
     * piercing_weapon（774→775）。bool＋bool＋option 音×2。音の Holder は
     * translateSoundHolder774To775 で付け替える。形式は774・775で同一。
     */
    private static void translatePiercingWeapon774To775(Reader reader, Writer writer) {
        writer.writeBoolean(reader.readBoolean()); // dealsKnockback
        writer.writeBoolean(reader.readBoolean()); // dismounts
        if (reader.readBoolean()) { // sound（option）
            writer.writeBoolean(true);
            translateSoundHolder774To775(reader, writer);
        } else {
            writer.writeBoolean(false);
        }
        if (reader.readBoolean()) { // hitSound（option）
            writer.writeBoolean(true);
            translateSoundHolder774To775(reader, writer);
        } else {
            writer.writeBoolean(false);
        }
    }

    /**
     * piercing_weapon（775→774）。bool＋bool＋option 音×2。
     */
    private static void translatePiercingWeapon775To774(Reader reader, Writer writer) {
        writer.writeBoolean(reader.readBoolean()); // dealsKnockback
        writer.writeBoolean(reader.readBoolean()); // dismounts
        if (reader.readBoolean()) { // sound（option）
            writer.writeBoolean(true);
            translateSoundHolder775To774(reader, writer);
        } else {
            writer.writeBoolean(false);
        }
        if (reader.readBoolean()) { // hitSound（option）
            writer.writeBoolean(true);
            translateSoundHolder775To774(reader, writer);
        } else {
            writer.writeBoolean(false);
        }
    }

    /**
     * kinetic_weapon（774→775）。varint×2＋option 条件×3＋f32×2＋option 音×2。
     * 条件は {maxDurationTicks varint, minSpeed f32, minRelativeSpeed f32}。
     * 形式は774・775で同一。
     */
    private static void translateKineticWeapon774To775(Reader reader, Writer writer) {
        writer.writeVarInt(reader.readVarInt()); // contactCooldownTicks
        writer.writeVarInt(reader.readVarInt()); // delayTicks
        for (int i = 0; i < 3; i++) { // dismount／knockback／damage 条件（option）
            boolean present = reader.readBoolean();
            writer.writeBoolean(present);
            if (present) {
                writer.writeVarInt(reader.readVarInt()); // maxDurationTicks
                writer.writeFloat(reader.readFloat()); // minSpeed
                writer.writeFloat(reader.readFloat()); // minRelativeSpeed
            }
        }
        writer.writeFloat(reader.readFloat()); // forwardMovement
        writer.writeFloat(reader.readFloat()); // damageMultiplier
        if (reader.readBoolean()) { // sound（option）
            writer.writeBoolean(true);
            translateSoundHolder774To775(reader, writer);
        } else {
            writer.writeBoolean(false);
        }
        if (reader.readBoolean()) { // hitSound（option）
            writer.writeBoolean(true);
            translateSoundHolder774To775(reader, writer);
        } else {
            writer.writeBoolean(false);
        }
    }

    /**
     * kinetic_weapon（775→774）。varint×2＋option 条件×3＋f32×2＋option 音×2。
     */
    private static void translateKineticWeapon775To774(Reader reader, Writer writer) {
        writer.writeVarInt(reader.readVarInt()); // contactCooldownTicks
        writer.writeVarInt(reader.readVarInt()); // delayTicks
        for (int i = 0; i < 3; i++) { // dismount／knockback／damage 条件（option）
            boolean present = reader.readBoolean();
            writer.writeBoolean(present);
            if (present) {
                writer.writeVarInt(reader.readVarInt()); // maxDurationTicks
                writer.writeFloat(reader.readFloat()); // minSpeed
                writer.writeFloat(reader.readFloat()); // minRelativeSpeed
            }
        }
        writer.writeFloat(reader.readFloat()); // forwardMovement
        writer.writeFloat(reader.readFloat()); // damageMultiplier
        if (reader.readBoolean()) { // sound（option）
            writer.writeBoolean(true);
            translateSoundHolder775To774(reader, writer);
        } else {
            writer.writeBoolean(false);
        }
        if (reader.readBoolean()) { // hitSound（option）
            writer.writeBoolean(true);
            translateSoundHolder775To774(reader, writer);
        } else {
            writer.writeBoolean(false);
        }
    }

    /**
     * damage_type（774→775）。bool＋（true なら Holder、false なら文字列）。
     * 774の Holder は varint（id そのまま）、775の Holder は registryEntryHolder
     * （varint。0＝直書きの DamageTypeData {msgId string, scaling varint,
     * exhaustion f32, effects varint, deathMessageType varint}、それ以外は id＋1）。
     * ダメージ種別は774・775とも50件・同じ並びのため id はそのまま通す。
     */
    private static void translateDamageType774To775(Reader reader, Writer writer) {
        boolean hasHolder = reader.readBoolean();
        writer.writeBoolean(hasHolder);
        if (!hasHolder) {
            writer.writeString(reader.readString());
            return;
        }
        writer.writeVarInt(reader.readVarInt() + 1);
    }

    /**
     * damage_type（775→774）。bool＋（true なら registryEntryHolder、false なら文字列）。
     * Holder が 0 なら直書きの DamageTypeData を読んで捨てる（774 には直書きが無い。
     * 774 側は Holder 無しでは表せないため、ここでは Holder 0（＝直書き）のまま
     * 書くことはできない。直書きが来た場合は id 0（in_fire）の Holder として扱う。
     * 実際には直書きの damage_type はまず来ない）。
     */
    private static void translateDamageType775To774(Reader reader, Writer writer) {
        boolean hasHolder = reader.readBoolean();
        writer.writeBoolean(hasHolder);
        if (!hasHolder) {
            writer.writeString(reader.readString());
            return;
        }
        int holder = reader.readVarInt();
        if (holder == 0) {
            // 直書きの DamageTypeData を読んで捨て、id 0 の Holder として書く。
            reader.readString(); // msgId
            reader.readVarInt(); // scaling
            reader.readFloat(); // exhaustion
            reader.readVarInt(); // effects
            reader.readVarInt(); // deathMessageType
            writer.writeVarInt(0);
            return;
        }
        writer.writeVarInt(holder - 1);
    }

    /**
     * chicken／zombie_nautilus variant（774→775）。774は bool＋（true なら varint、
     * false なら文字列）、775は registryEntryHolder（varint。0＝直書きの文字列、
     * それ以外は id＋1）。variant の並びは774・775で同一のため id はそのまま通す。
     */
    private static void translateVariantHolder774To775(Reader reader, Writer writer) {
        if (reader.readBoolean()) {
            writer.writeVarInt(reader.readVarInt() + 1);
        } else {
            writer.writeVarInt(0);
            writer.writeString(reader.readString());
        }
    }

    /**
     * chicken／zombie_nautilus variant（775→774）。registryEntryHolder から
     * bool 形式へ。Holder が 0 なら文字列付きの false、1 以上なら id−1 の true。
     */
    private static void translateVariantHolder775To774(Reader reader, Writer writer) {
        int holder = reader.readVarInt();
        if (holder == 0) {
            writer.writeBoolean(false);
            writer.writeString(reader.readString());
        } else {
            writer.writeBoolean(true);
            writer.writeVarInt(holder - 1);
        }
    }

    /**
     * 成分1個の中身だけを写す（775形式。翻訳不要な成分用。774の種別をそのまま
     * copyComponentOpaque774 に委譲する。KIND 表の付け替えはしない。
     * copyComponentOpaque775 は 775 の種別を 774 に直してから委譲するため、
     * 774→775 の上向き dispatch の default には使えない。ここで分ける。）
     */
    private static void copyComponentOpaque775From774(Reader reader, Writer writer, int kind774) {
        copyComponentOpaque774(reader, writer, kind774);
    }
"""

# 置き換えの続き（成分ヘルパー：773→774の形式変換を含まないもの）。
CHUNK_METHOD_REPLACEMENTS.update({
"translateEnchantments774To775(Reader reader, Writer writer)": """    /** enchantments／stored_enchantments を774→775向けに変換する。エンチャントは774・775とも43件・同じ並びのため対応表に通すだけ（実質そのまま）。末尾の showTooltip は770・771・772・773・774・775とも無い。 */
    private static void translateEnchantments774To775(Reader reader, Writer writer) {
        int count = reader.readVarInt();
        Writer kept = new Writer();
        int keptCount = 0;
        for (int i = 0; i < count; i++) {
            int id = reader.readVarInt();
            int level = reader.readVarInt();
            int mapped = RegistryRemapper.mapEnchantment774To775(id);
            if (mapped == RegistryRemapper.DROP) {
                logOnce("enchantment-drop-775",
                        "775に無いエンチャントを落とします（id=" + id + "）。");
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
"translateEnchantments775To774(Reader reader, Writer writer)": """    /** enchantments／stored_enchantments を775→774向けに変換する。エンチャントは774・775とも43件・同じ並びのため対応表に通すだけ（実質そのまま）。末尾の showTooltip は770・771・772・773・774・775とも無い。 */
    private static void translateEnchantments775To774(Reader reader, Writer writer) {
        int count = reader.readVarInt();
        Writer kept = new Writer();
        int keptCount = 0;
        for (int i = 0; i < count; i++) {
            int id = reader.readVarInt();
            int level = reader.readVarInt();
            int mapped = RegistryRemapper.mapEnchantment775To774(id);
            if (mapped == RegistryRemapper.DROP) {
                logOnce("enchantment-drop-774-from-775",
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
"translateAttributeModifiers774To775(Reader reader, Writer writer)": """    /** attribute_modifiers を774→775向けに変換する。770・771・772・773・774・775とも {属性の配列} のみ（末尾の showTooltip は無い）。 */
    private static void translateAttributeModifiers774To775(Reader reader, Writer writer) {
        int count = reader.readVarInt();
        Writer kept = new Writer();
        int keptCount = 0;
        for (int i = 0; i < count; i++) {
            int attrId = reader.readVarInt();
            String name = reader.readString();
            double value = reader.readDouble();
            int operation = reader.readVarInt();
            int slot = reader.readVarInt();
            int mapped = RegistryRemapper.mapAttribute774To775(attrId);
            if (mapped == RegistryRemapper.DROP) {
                logOnce("attribute-modifier-drop-775",
                        "775に無い属性の修飾子を落とします（id=" + attrId + "）。");
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
"translateAttributeModifiers775To774(Reader reader, Writer writer)": """    /** attribute_modifiers を775→774向けに変換する。770・771・772・773・774・775とも {属性の配列} のみ。 */
    private static void translateAttributeModifiers775To774(Reader reader, Writer writer) {
        int count = reader.readVarInt();
        Writer kept = new Writer();
        int keptCount = 0;
        for (int i = 0; i < count; i++) {
            int attrId = reader.readVarInt();
            String name = reader.readString();
            double value = reader.readDouble();
            int operation = reader.readVarInt();
            int slot = reader.readVarInt();
            int mapped = RegistryRemapper.mapAttribute775To774(attrId);
            if (mapped == RegistryRemapper.DROP) {
                logOnce("attribute-modifier-drop-774-from-775",
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
"translateTrim774To775(Reader reader, Writer writer)": """    /** trim を774→775向けに変換する。材料・型の Holder。770・771・772・773・774・775とも showInTooltip は無い。 */
    private static boolean translateTrim774To775(Reader reader, Writer writer) {
        if (!translateTrimMaterial774To775(reader, writer)) {
            return false;
        }
        return translateTrimPattern774To775(reader, writer);
    }
""",
"translateTrim775To774(Reader reader, Writer writer)": """    /** trim を775→774向けに変換する。材料・型の Holder。770・771・772・773・774・775とも showInTooltip は無い。 */
    private static boolean translateTrim775To774(Reader reader, Writer writer) {
        if (!translateTrimMaterial775To774(reader, writer)) {
            return false;
        }
        return translateTrimPattern775To774(reader, writer);
    }
""",
"translateTrimMaterial774To775(Reader reader, Writer writer)": """    /** trim の材料の Holder を774→775向けに変換する。770・771・772・773・774・775とも直書きは {assetBase, overrides, description}（ingredientId は無い）。材料IDは11種・同じ並びのためそのまま。 */
    private static boolean translateTrimMaterial774To775(Reader reader, Writer writer) {
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
"translateTrimMaterial775To774(Reader reader, Writer writer)": """    /** trim の材料の Holder を775→774向けに変換する。直書きは {assetBase, overrides, description}。 */
    private static boolean translateTrimMaterial775To774(Reader reader, Writer writer) {
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
"translateTrimPattern774To775(Reader reader, Writer writer)": """    /** trim の型の Holder を774→775向けに変換する。770・771・772・773・774・775とも直書きは {assetId, description, decal}（templateItemId は無い）。 */
    private static boolean translateTrimPattern774To775(Reader reader, Writer writer) {
        int holder = reader.readVarInt();
        if (holder != 0) {
            int mapped = RegistryRemapper.mapTrimPattern774To775(holder - 1);
            if (mapped == RegistryRemapper.DROP) {
                logOnce("trim-pattern-drop-775",
                        "775に無い装飾パターンの trim を落とします（id=" + (holder - 1) + "）。");
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
"translateTrimPattern775To774(Reader reader, Writer writer)": """    /** trim の型の Holder を775→774向けに変換する。直書きは {assetId, description, decal}。 */
    private static boolean translateTrimPattern775To774(Reader reader, Writer writer) {
        int holder = reader.readVarInt();
        if (holder != 0) {
            int mapped = RegistryRemapper.mapTrimPattern775To774(holder - 1);
            if (mapped == RegistryRemapper.DROP) {
                logOnce("trim-pattern-drop-774-from-775",
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
})

CHUNK_METHOD_REPLACEMENTS.update({
"translateInstrument774To775(Reader reader, Writer writer)": """    /**
     * instrument コンポーネントを774→775向けに変換する。770・771・772・773・774・775とも
     * {hasHolder bool, data：true なら Holder／false なら楽器名} の
     * コンテナ。楽器IDは770・771・772・773・774・775とも8件・同じ並びのためそのまま。
     */
    private static boolean translateInstrument774To775(Reader reader, Writer writer) {
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
        translateSoundHolder774To775(reader, writer);
        writer.writeFloat(reader.readFloat()); // useDuration
        writer.writeFloat(reader.readFloat()); // range
        copyOptionalAnonymousNbt(reader, writer);
        return true;
    }
""",
"translateInstrument775To774(Reader reader, Writer writer)": """    /**
     * instrument コンポーネントを775→774向けに変換する。770・771・772・773・774・775とも
     * {hasHolder bool, data} のコンテナ。楽器IDは8件・同じ並びのためそのまま。
     */
    private static boolean translateInstrument775To774(Reader reader, Writer writer) {
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
        translateSoundHolder775To774(reader, writer);
        writer.writeFloat(reader.readFloat()); // useDuration
        writer.writeFloat(reader.readFloat()); // range
        copyOptionalAnonymousNbt(reader, writer);
        return true;
    }
""",
"translateJukeboxPlayable774To775(Reader reader, Writer writer)": """    /** jukebox_playable を774→775向けに変換する。曲IDは772・773・774・775で同一（21曲・同じ並び）。直書きの音の Holder も付け替える。末尾の showInTooltip は770・771・772・773・774・775ともある。 */
    private static void translateJukeboxPlayable774To775(Reader reader, Writer writer) {
        boolean hasHolder = reader.readBoolean();
        writer.writeBoolean(hasHolder);
        if (hasHolder) {
            int holder = reader.readVarInt();
            if (holder == 0) {
                writer.writeVarInt(0);
                translateSoundHolder774To775(reader, writer);
                writer.writeAnonymousNbt(reader.readAnonymousNbt().tag()); // description
                writer.writeFloat(reader.readFloat()); // lengthInSeconds
                writer.writeVarInt(reader.readVarInt()); // comparatorOutput
            } else {
                int mapped = RegistryRemapper.mapJukeboxSong774To775(holder - 1);
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
"translateJukeboxPlayable775To774(Reader reader, Writer writer)": """    /** jukebox_playable を775→774向けに変換する。曲IDは772・773・774・775で同一（21曲・同じ並び）。末尾の showInTooltip は770・771・772・773・774・775ともある。 */
    private static void translateJukeboxPlayable775To774(Reader reader, Writer writer) {
        boolean hasHolder = reader.readBoolean();
        writer.writeBoolean(hasHolder);
        if (hasHolder) {
            int holder = reader.readVarInt();
            if (holder == 0) {
                writer.writeVarInt(0);
                translateSoundHolder775To774(reader, writer);
                writer.writeAnonymousNbt(reader.readAnonymousNbt().tag()); // description
                writer.writeFloat(reader.readFloat()); // lengthInSeconds
                writer.writeVarInt(reader.readVarInt()); // comparatorOutput
            } else {
                int mapped = RegistryRemapper.mapJukeboxSong775To774(holder - 1);
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
"translateTool774To775(Reader reader, Writer writer)": """    /** tool を774→775向けに変換する。ルールの中のブロックIDだけ付け替え、末尾の canDestroyBlocksInCreative は770・771・772・773・774・775ともあるため写す。 */
    private static void translateTool774To775(Reader reader, Writer writer) {
        int ruleCount = reader.readVarInt();
        writer.writeVarInt(ruleCount);
        for (int i = 0; i < ruleCount; i++) {
            translateIdSetBlocks774To775(reader, writer);
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
"translateTool775To774(Reader reader, Writer writer)": """    /** tool を775→774向けに変換する。ルールの中のブロックIDだけ付け替え、末尾の canDestroyBlocksInCreative は写す。 */
    private static void translateTool775To774(Reader reader, Writer writer) {
        int ruleCount = reader.readVarInt();
        writer.writeVarInt(ruleCount);
        for (int i = 0; i < ruleCount; i++) {
            translateIdSetBlocks775To774(reader, writer);
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
"translateItemBlockPredicates774To775(Reader reader, Writer writer)": """    /** can_place_on／can_break を774→775向けに変換する。述語の中のブロックIDだけ付け替える。末尾の showTooltip は770・771・772・773・774・775とも無い。 */
    private static void translateItemBlockPredicates774To775(Reader reader, Writer writer) {
        int count = reader.readVarInt();
        writer.writeVarInt(count);
        for (int i = 0; i < count; i++) {
            translateItemBlockPredicate774To775(reader, writer);
        }
    }
""",
"translateItemBlockPredicates775To774(Reader reader, Writer writer)": """    /** can_place_on／can_break を775→774向けに変換する。述語の中のブロックIDだけ付け替える。 */
    private static void translateItemBlockPredicates775To774(Reader reader, Writer writer) {
        int count = reader.readVarInt();
        writer.writeVarInt(count);
        for (int i = 0; i < count; i++) {
            translateItemBlockPredicate775To774(reader, writer);
        }
    }
""",
"translateItemBlockPredicate774To775(Reader reader, Writer writer)": """    /** ItemBlockPredicate 1個を774→775向けに変換する。述語の中のブロックIDだけ付け替え、末尾の DataComponentMatchers も変換する（770・771・772・773・774・775ともある）。 */
    private static void translateItemBlockPredicate774To775(Reader reader, Writer writer) {
        if (reader.readBoolean()) {
            writer.writeBoolean(true);
            translateIdSetBlocks774To775(reader, writer);
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
        translateDataComponentMatchers774To775(reader, writer);
    }
""",
"translateItemBlockPredicate775To774(Reader reader, Writer writer)": """    /** ItemBlockPredicate 1個を775→774向けに変換する。述語の中のブロックIDだけ付け替え、末尾の DataComponentMatchers も変換する。 */
    private static void translateItemBlockPredicate775To774(Reader reader, Writer writer) {
        if (reader.readBoolean()) {
            writer.writeBoolean(true);
            translateIdSetBlocks775To774(reader, writer);
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
        translateDataComponentMatchers775To774(reader, writer);
    }
""",
"translateParticle775To774(Reader reader, Writer writer)": """    /**
     * パーティクル1個（ID VarInt＋データ）を775→774向けに変換する。
     * パーティクルは pause／reset_mob_growth の挿入で50番以降が+2ずれるため
     * 対応表に通す。落とす粒子は無いため常に変換できる（{@code true} を返す）。
     */
    private static boolean translateParticle775To774(Reader reader, Writer writer) {
        int particleId = reader.readVarInt();
        writer.writeVarInt(RegistryRemapper.mapParticleUp775(particleId));
        translateParticleData775To774(reader, writer, particleId);
        return true;
    }
""",
"translateWorldParticles775To774(byte[] payload)": """    /**
     * World Particles（775 → 774）。alwaysShow は770・771・772・773・774・775ともにあるため
     * そのまま通す。粒子は pause／reset_mob_growth の挿入で50番以降が+2ずれるため対応表に通す。落とす粒子は無い。
     */
    public static byte[] translateWorldParticles775To774(byte[] payload) {
        try {
            Reader reader = new Reader(payload);
            boolean longDistance = reader.readBoolean();
            boolean alwaysShow = reader.readBoolean(); // alwaysShow（770・771・772・773・774・775ともにある）
            double x = reader.readDouble();
            double y = reader.readDouble();
            double z = reader.readDouble();
            float offsetX = reader.readFloat();
            float offsetY = reader.readFloat();
            float offsetZ = reader.readFloat();
            float speed = reader.readFloat();
            int count = reader.readInt();
            Writer particleWriter = new Writer();
            translateParticle775To774(reader, particleWriter);
            requireEnd(reader);
            Writer writer = new Writer();
            writer.writeBoolean(longDistance);
            writer.writeBoolean(alwaysShow); // alwaysShow（774にもある。そのまま通す）
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
"translateWolfVariantHolder774To775(Reader reader, Writer writer)": """    /** 狼バリアントの Holder を774→775向けに変換する。770・771・772・773・774・775ともただの varint（9種・同じ並び）のためそのまま写す。 */
    private static boolean translateWolfVariantHolder774To775(Reader reader, Writer writer) {
        writer.writeVarInt(reader.readVarInt());
        return true;
    }
""",
"translateWolfVariantHolder775To774(Reader reader, Writer writer)": """    /** 狼バリアントの Holder を775→774向けに変換する。770・771・772・773・774・775ともただの varint のためそのまま写す。 */
    private static boolean translateWolfVariantHolder775To774(Reader reader, Writer writer) {
        writer.writeVarInt(reader.readVarInt());
        return true;
    }
""",
"translateChunkData774To775(byte[] payload)": """    /** チャンクデータを774→775向けに変換する。heightmap は770・771・772・773・774・775とも直接の配列（varint 件数＋（type、長さ、long列））のためそのまま写す。セクションのブロック状態・バイオームとブロックエンティティ種別を付け替える。 */
    public static byte[] translateChunkData774To775(byte[] payload) {
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
            byte[] translatedSections = translateChunkSections774To775(chunkData);
            int blockEntityCount = reader.readVarInt();
            List<BlockEntityEntry> kept = new ArrayList<>();
            for (int i = 0; i < blockEntityCount; i++) {
                int packedXZ = reader.readUnsignedByte();
                int y = reader.readShort();
                int type = reader.readVarInt();
                Tag nbt = readOptionalAnonymousNbt(reader);
                int mapped = RegistryRemapper.mapBlockEntityType774To775(type);
                if (mapped == RegistryRemapper.DROP) {
                    LOG.warning("775に無いブロックエンティティ種別（774 type=" + type
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
"translateChunkData775To774(byte[] payload)": """    /** チャンクデータを775→774向けに変換する。heightmap は770・771・772・773・774・775とも直接の配列のためそのまま写す。 */
    public static byte[] translateChunkData775To774(byte[] payload) {
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
            byte[] translatedSections = translateChunkSections775To774(chunkData);
            int blockEntityCount = reader.readVarInt();
            List<BlockEntityEntry> kept = new ArrayList<>();
            for (int i = 0; i < blockEntityCount; i++) {
                int packedXZ = reader.readUnsignedByte();
                int y = reader.readShort();
                int type = reader.readVarInt();
                Tag nbt = readOptionalAnonymousNbt(reader);
                int mapped = RegistryRemapper.mapBlockEntityTypeUp775(type);
                if (mapped == RegistryRemapper.DROP) {
                    LOG.warning("774に無いブロックエンティティ種別（775 type=" + type
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
"translatePalettedContainer774To775(Reader reader, Writer writer, boolean blocks)": """        /**
         * パレット付きコンテナ1個を774→775向けに変換する。ブロック状態と
         * バイオームの両方を付け替える。770・771・772・773・774・775とも Data Array に
         * 長さ varint 接頭辞は無い。
         */
        private static void translatePalettedContainer774To775(Reader reader, Writer writer, boolean blocks) {
        int bitsPerEntry = reader.readUnsignedByte();
        if (bitsPerEntry > 32) {
            throw new IllegalArgumentException("Bits Per Entry が大きすぎます: " + bitsPerEntry);
        }
        writer.writeUnsignedByte(bitsPerEntry);
        int entryCount = blocks ? 4096 : 64;
        if (bitsPerEntry == 0) {
            int value = reader.readVarInt();
            writer.writeVarInt(blocks
                    ? RegistryRemapper.mapBlockState774To775(value)
                    : mapBiome774To775(value));
            // 770・771・772・773・774・775とも長さ接頭辞は無い。
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
                        ? RegistryRemapper.mapBlockState774To775(value)
                        : mapBiome774To775(value));
            }
            // 間接パレットの Data Array はインデックス列なので中身は変えない。
            // 770・771・772・773・774・775とも長さ接頭辞は無い。長さは bpe から計算する。
            // バニラの BitStorage はエントリを long の境界をまたいで連続して詰めるため、

            // long 数は (entryCount * bitsPerEntry + 63) / 64（旧式は bits が 64 を

            // 割り切らないときに多めに数えた。2026-11-14）。

            int expected = (entryCount * bitsPerEntry + 63) / 64;
            for (int i = 0; i < expected; i++) {
                writer.writeLong(reader.readLong());
            }
            return;
        }
        // 直接パレット：Data Array の中身がレジストリ番号そのもの。
        // 770・771・772・773・774・775とも長さ接頭辞は無い。長さは bpe から計算する。
        // バニラの BitStorage はエントリを long の境界をまたいで連続して詰めるため、

        // long 数は (entryCount * bitsPerEntry + 63) / 64（旧式は bits が 64 を

        // 割り切らないときに多めに数えた。2026-11-14）。

        int expected = (entryCount * bitsPerEntry + 63) / 64;
        long[] longs = new long[expected];
        for (int i = 0; i < expected; i++) {
            longs[i] = reader.readLong();
        }
        remapDirectEntries774To775(longs, bitsPerEntry, entryCount, blocks);
        for (long value : longs) {
            writer.writeLong(value);
        }
    }
""",
"translatePalettedContainer775To774(Reader reader, Writer writer, boolean blocks)": """        /** パレット付きコンテナ1個を775→774向けに変換する。ブロック状態とバイオームの両方を付け替える。770・771・772・773・774・775とも Data Array に長さ varint 接頭辞は無い。 */
        private static void translatePalettedContainer775To774(Reader reader, Writer writer, boolean blocks) {
        int bitsPerEntry = reader.readUnsignedByte();
        if (bitsPerEntry > 32) {
            throw new IllegalArgumentException("Bits Per Entry が大きすぎます: " + bitsPerEntry);
        }
        writer.writeUnsignedByte(bitsPerEntry);
        int entryCount = blocks ? 4096 : 64;
        if (bitsPerEntry == 0) {
            int value = reader.readVarInt();
            writer.writeVarInt(blocks
                    ? RegistryRemapper.mapBlockStateUp775(value)
                    : mapBiomeUp775(value));
            // 770・771・772・773・774・775とも長さ接頭辞は無い。
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
                        ? RegistryRemapper.mapBlockStateUp775(value)
                        : mapBiomeUp775(value));
            }
            // 間接パレットの Data Array はインデックス列なので中身は変えない。
            // 770・771・772・773・774・775とも長さ接頭辞は無い。長さは bpe から計算する。
            // バニラの BitStorage はエントリを long の境界をまたいで連続して詰めるため、

            // long 数は (entryCount * bitsPerEntry + 63) / 64（旧式は bits が 64 を

            // 割り切らないときに多めに数えた。2026-11-14）。

            int expected = (entryCount * bitsPerEntry + 63) / 64;
            for (int i = 0; i < expected; i++) {
                writer.writeLong(reader.readLong());
            }
            return;
        }
        // 直接パレット：Data Array の中身がレジストリ番号そのもの。
        // 770・771・772・773・774・775とも長さ接頭辞は無い。長さは bpe から計算する。
        // バニラの BitStorage はエントリを long の境界をまたいで連続して詰めるため、

        // long 数は (entryCount * bitsPerEntry + 63) / 64（旧式は bits が 64 を

        // 割り切らないときに多めに数えた。2026-11-14）。

        int expected = (entryCount * bitsPerEntry + 63) / 64;
        long[] longs = new long[expected];
        for (int i = 0; i < expected; i++) {
            longs[i] = reader.readLong();
        }
        remapDirectEntries775To774(longs, bitsPerEntry, entryCount, blocks);
        for (long value : longs) {
            writer.writeLong(value);
        }
    }
""",
"translateExplosion774To775(byte[] payload)": """    /** Explosion（774 → 775）。knockback は770・771・772・773・774・775とも f64×3。粒子・音を付け替える。 */
    public static byte[] translateExplosion774To775(byte[] payload) {
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
            translateParticle774To775(reader, writer);
            translateSoundHolder774To775(reader, writer);
            requireEnd(reader);
            return writer.bytes();
        } catch (IllegalArgumentException e) {
            LOG.warning("Explosion を変換できなかったため素通しします: " + e.getMessage());
            return payload;
        }
    }
""",
"translateExplosion775To774(byte[] payload)": """    /** Explosion（775 → 774）。knockback は770・771・772・773・774・775とも f64×3。粒子・音を付け替える。 */
    public static byte[] translateExplosion775To774(byte[] payload) {
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
            translateParticle775To774(reader, particleWriter);
            writer.writeBytes(particleWriter.bytes());
            translateSoundHolder775To774(reader, writer);
            requireEnd(reader);
            return writer.bytes();
        } catch (IllegalArgumentException e) {
            LOG.warning("Explosion を変換できなかったため素通しします: " + e.getMessage());
            return payload;
        }
    }
""",
"translateSetCreativeSlot774To775(byte[] payload)": """    /** Set Creative Slot（774 → 775）。770・771・772・773・774・775とも UntrustedSlot（成分は ByteArray の生バイト）。成分の中身を774→775に変換する。 */
    public static byte[] translateSetCreativeSlot774To775(byte[] payload) {
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
            writer.writeVarInt(RegistryRemapper.mapItem774To775(reader.readVarInt()));
            int added = reader.readVarInt();
            int removed = reader.readVarInt();
            Writer addedOut = new Writer();
            int keptAdded = 0;
            for (int i = 0; i < added; i++) {
                int kind774 = reader.readVarInt();
                byte[] data = reader.readByteArray();
                Writer component = new Writer();
                if (!translateComponent774To775(new Reader(data), component, kind774)) {
                    continue;
                }
                addedOut.writeVarInt(mapComponentKind774To775(kind774));
                addedOut.writeByteArray(component.bytes());
                keptAdded++;
            }
            Writer removedOut = new Writer();
            int keptRemoved = 0;
            for (int i = 0; i < removed; i++) {
                int kind775 = mapComponentKind774To775(reader.readVarInt());
                if (kind775 < 0) {
                    continue;
                }
                removedOut.writeVarInt(kind775);
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
"translateSetCreativeSlot775To774(byte[] payload)": """    /** Set Creative Slot（775 → 774）。770・771・772・773・774・775とも UntrustedSlot。成分の中身を775→774に変換する。 */
    public static byte[] translateSetCreativeSlot775To774(byte[] payload) {
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
            writer.writeVarInt(RegistryRemapper.mapItemUp775(reader.readVarInt()));
            int added = reader.readVarInt();
            int removed = reader.readVarInt();
            Writer addedOut = new Writer();
            int keptAdded = 0;
            for (int i = 0; i < added; i++) {
                int kind775 = reader.readVarInt();
                byte[] data = reader.readByteArray();
                Writer component = new Writer();
                if (!translateComponent775To774(new Reader(data), component, kind775)) {
                    continue;
                }
                addedOut.writeVarInt(mapComponentKind775To774(kind775));
                addedOut.writeByteArray(component.bytes());
                keptAdded++;
            }
            Writer removedOut = new Writer();
            int keptRemoved = 0;
            for (int i = 0; i < removed; i++) {
                int kind774 = mapComponentKind775To774(reader.readVarInt());
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
})

# ----------------------------------------------------------------------
# PlayTranslator 775 節（手書き）
# ----------------------------------------------------------------------

PLAY_775_SECTION = """
    // 774 ⇔ 775（26.1）
    // Join Game／Respawn／Synchronize Player Position／Login Success／
    // Client Information／Player Info／Set Held Item／Vehicle Move／
    // Player Chat は774と775で形式が同一のため変換器は要らない（素通し）。
    // 変わるのは Registry Data（レジストリの順序。26.1での変更はアイテム・音・
    // ブロック状態の追加。エンチャント・ダメージ種別・エンティティ・絵画・
    // ジュークボックス・属性・エフェクト・バイオームは同一。minecraft:dialog は
    // 771からあるため通す）、Update Time（time・tickDayTime から clockUpdates
    // 配列へ再構成。age は両版にある）、Enchant Item（enchantment が i8 から
    // varint へ戻る）、Use Entity（再構成。attack は独立パケットへ分離）だけである。
    // なお Registry Data の entity_type・sound・item・particle は774節と同じく
    // 素通し（順序は付け替えない。Spawn Entity の種別 ID だけ翻訳するため 774 と
    // 同じずれが残る。制限事項として記録する）。
    // ------------------------------------------------------------------

    /** Player Info（774 → 775）。形式は同一のため素通しする。 */
    public static byte[] translatePlayerInfo774To775(byte[] payload) {
        return payload;
    }

    /** Player Info（775 → 774）。形式は同一のため素通しする。 */
    public static byte[] translatePlayerInfo775To774(byte[] payload) {
        return payload;
    }

    /** Set Held Item（774 → 775）。形式は同一のため素通しする（varint のまま）。 */
    public static byte[] translateSetHeldItem774To775(byte[] payload) {
        return payload;
    }

    /** Set Held Item（775 → 774）。形式は同一のため素通しする。 */
    public static byte[] translateSetHeldItem775To774(byte[] payload) {
        return payload;
    }

    /** Vehicle Move（774 → 775）。形式は同一のため素通しする（末尾の onGround 付き）。 */
    public static byte[] translateVehicleMove774To775(byte[] payload) {
        return payload;
    }

    /** Vehicle Move（775 → 774）。形式は同一のため素通しする。 */
    public static byte[] translateVehicleMove775To774(byte[] payload) {
        return payload;
    }

    /** Player Chat（774 → 775）。先頭の globalIndex は770・771・772・773・774・775ともあるため素通しする。 */
    public static byte[] translatePlayerChat774To775(byte[] payload) {
        return payload;
    }

    /** Player Chat（775 → 774）。先頭の globalIndex は770・771・772・773・774・775ともあるため素通しする。 */
    public static byte[] translatePlayerChat775To774(byte[] payload) {
        return payload;
    }

    /**
     * Chat Message／Chat Command Signed（775 → 774）。末尾の checksum
     * （u8 1バイト）を取り除く。ペイロードが空の場合はそのまま返す。
     * （注意：現行の ProxySession は checksum を両版ともあるものとして
     * 素通しするため、このメソッドは未使用。774節の同名メソッドと同じ扱い。）
     */
    public static byte[] stripChatChecksum775(byte[] payload) {
        if (payload.length == 0) {
            return payload;
        }
        return new Reader(payload).readBytes(payload.length - 1);
    }

    /**
     * Chat Message／Chat Command Signed（774 → 775）。末尾に checksum
     * （u8 1バイト）を追加する。checksum の式は未確認のため 0x00 を入れる
     * （実機での確認は未実施。計算式そのものの確認も未実施）。
     * （注意：現行の ProxySession は checksum を両版ともあるものとして
     * 素通しするため、このメソッドは未使用。774節の同名メソッドと同じ扱い。）
     */
    public static byte[] appendChatChecksum775(byte[] payload) {
        Writer writer = new Writer();
        writer.writeBytes(payload);
        writer.writeByte(0);
        return writer.bytes();
    }

    /**
     * 774の Registry Data を775形式にする。minecraft:dialog は771から
     * あるためそのまま通す。エンチャント・ダメージ種別・ジュークボックス・
     * 絵画は774・775で同一のため並べ替えは実質そのまま（reorderRegistries は
     * 774→775 の対応表を通す）。entity_type・sound・item・particle は素通し
     * （774節と同じ）。それ以外は1レジストリ分のペイロードを返す。
     */
    public static byte[] translateRegistryData774To775(byte[] payload) {
        Map<String, Tag.CompoundTag> reordered = reorderRegistries(
                parseRegistryData(payload),
                ProtocolVersion.V1_21_11, ProtocolVersion.V26_1);
        List<byte[]> built = buildRegistryData766(reordered);
        if (built.isEmpty()) {
            throw new IllegalArgumentException("Registry Data が空です。");
        }
        return built.get(0);
    }

    /**
     * 775の Registry Data を774形式にする。minecraft:dialog は774にも
     * あるため通す（reorderRegistries）。エンチャント・ダメージ種別・
     * ジュークボックス・絵画は774・775で同一のため並べ替えは実質そのまま。
     * entity_type・sound・item・particle は素通し（774節と同じ）。
     * それ以外は1レジストリ分のペイロードを返す。空になった場合は {@code null} を返す。
     */
    public static byte[] translateRegistryData775To774(byte[] payload) {
        Map<String, Tag.CompoundTag> reordered = reorderRegistries(
                parseRegistryData(payload),
                ProtocolVersion.V26_1, ProtocolVersion.V1_21_11);
        List<byte[]> built = buildRegistryData766(reordered);
        return built.isEmpty() ? null : built.get(0);
    }

    /**
     * Update Time（774 → 775）。774は {age i64, time i64, tickDayTime bool}、
     * 775は {age i64, clockUpdates[{id varint, totalTicks varlong, partialTick f32, rate f32}]}。
     * age はそのまま写す。time は id 0 の時計の totalTicks にし、tickDayTime は
     * rate にする（true＝1.0f、false＝0.0f）。partialTick は 0.0f にする。
     * （注：775 の clock id の意味と rate の単位は未確認。775 の時計が複数ある
     * 場合、id 0 以外の時計は作らない。実機での確認は未実施。）
     */
    public static byte[] translateUpdateTime774To775(byte[] payload) {
        try {
            Reader reader = new Reader(payload);
            long age = reader.readLong();
            long time = reader.readLong();
            boolean tickDayTime = reader.readBoolean();
            if (reader.remaining() != 0) {
                throw new IllegalArgumentException(
                        "Update Time に余分なバイトがあります（残り=" + reader.remaining() + "）。");
            }
            Writer writer = new Writer();
            writer.writeLong(age);
            writer.writeVarInt(1); // clockUpdates の件数
            writer.writeVarInt(0); // id（既定の時計）
            writer.writeVarLong(time); // totalTicks
            writer.writeFloat(0.0f); // partialTick
            writer.writeFloat(tickDayTime ? 1.0f : 0.0f); // rate
            return writer.bytes();
        } catch (IllegalArgumentException e) {
            return payload;
        }
    }

    /**
     * Update Time（775 → 774）。775は {age i64, clockUpdates[...]}、
     * 774は {age i64, time i64, tickDayTime bool}。age はそのまま写す。
     * time は id 0 の時計の totalTicks（無ければ最初の時計、無ければ 0）、
     * tickDayTime は rate != 0 とする。id 0 以外の時計は落とす。
     * （注：clock id の意味と rate の単位は未確認。実機での確認は未実施。）
     */
    public static byte[] translateUpdateTime775To774(byte[] payload) {
        try {
            Reader reader = new Reader(payload);
            long age = reader.readLong();
            int count = reader.readVarInt();
            if (count < 0) {
                throw new IllegalArgumentException("Update Time の時計の件数が負です: " + count);
            }
            long time = 0;
            boolean tickDayTime = false;
            boolean haveFirst = false;
            long firstTime = 0;
            boolean firstTickDayTime = false;
            boolean foundId0 = false;
            for (int i = 0; i < count; i++) {
                int id = reader.readVarInt();
                long totalTicks = reader.readVarLong();
                reader.readFloat(); // partialTick
                float rate = reader.readFloat();
                if (!haveFirst) {
                    firstTime = totalTicks;
                    firstTickDayTime = rate != 0.0f;
                    haveFirst = true;
                }
                if (id == 0) {
                    time = totalTicks;
                    tickDayTime = rate != 0.0f;
                    foundId0 = true;
                }
            }
            if (!foundId0) {
                time = firstTime;
                tickDayTime = firstTickDayTime;
            }
            if (reader.remaining() != 0) {
                throw new IllegalArgumentException(
                        "Update Time に余分なバイトがあります（残り=" + reader.remaining() + "）。");
            }
            Writer writer = new Writer();
            writer.writeLong(age);
            writer.writeLong(time);
            writer.writeBoolean(tickDayTime);
            return writer.bytes();
        } catch (IllegalArgumentException e) {
            return payload;
        }
    }

    /**
     * Enchant Item（774 → 775）。windowId は両版とも varint（ContainerID）。
     * enchantment は774が i8、775が varint のため変換する。
     */
    public static byte[] translateEnchantItem774To775(byte[] payload) {
        try {
            Reader reader = new Reader(payload);
            int windowId = reader.readVarInt();
            int enchantment = reader.readByte();
            if (reader.remaining() != 0) {
                throw new IllegalArgumentException(
                        "Enchant Item に余分なバイトがあります（残り=" + reader.remaining() + "）。");
            }
            Writer writer = new Writer();
            writer.writeVarInt(windowId);
            writer.writeVarInt(enchantment);
            return writer.bytes();
        } catch (IllegalArgumentException e) {
            return payload;
        }
    }

    /**
     * Enchant Item（775 → 774）。enchantment は775が varint、774が i8。
     * varint が i8 の範囲（-128〜127）に収まらない場合は素通しする
     * （実在のエンチャント ID は 0〜42 のため起きない）。
     */
    public static byte[] translateEnchantItem775To774(byte[] payload) {
        try {
            Reader reader = new Reader(payload);
            int windowId = reader.readVarInt();
            int enchantment = reader.readVarInt();
            if (enchantment < -128 || enchantment > 127) {
                throw new IllegalArgumentException(
                        "Enchant Item のエンチャント ID が i8 の範囲外です: " + enchantment);
            }
            if (reader.remaining() != 0) {
                throw new IllegalArgumentException(
                        "Enchant Item に余分なバイトがあります（残り=" + reader.remaining() + "）。");
            }
            Writer writer = new Writer();
            writer.writeVarInt(windowId);
            writer.writeByte(enchantment);
            return writer.bytes();
        } catch (IllegalArgumentException e) {
            return payload;
        }
    }

    /**
     * Use Entity（774 → 775）。774は {target varint, mouse varint,
     * x／y／z f32（mouse==2 のときだけ）, hand varint（mouse==0／2 のときだけ）,
     * sneaking bool}、775は {target varint, hand varint（0＝main_hand／1＝off_hand）,
     * location lpVec3（f32×3）, sneaking bool}。
     * mouse==2（interact_at）はそのまま変換する。mouse==0（interact）は
     * location が無いため (0, 0, 0) を補う（775 側は常に location を持つ）。
     * mouse==1（attack）は 775 では独立パケット（attack 0x01）のため
     * ここでは表せず、null を返す（呼び出し側が attack パケットへ振り分けるか落とす）。
     * （注：mouse==0 の location 補完は仮定。実機での確認は未実施。）
     */
    public static byte[] translateUseEntity774To775(byte[] payload) {
        try {
            Reader reader = new Reader(payload);
            int target = reader.readVarInt();
            int mouse = reader.readVarInt();
            float x = 0.0f;
            float y = 0.0f;
            float z = 0.0f;
            int hand = 0;
            if (mouse == 2) {
                x = reader.readFloat();
                y = reader.readFloat();
                z = reader.readFloat();
                hand = reader.readVarInt();
            } else if (mouse == 0) {
                hand = reader.readVarInt();
            } else if (mouse == 1) {
                // attack：775 の use_entity では表せない。
                return null;
            } else {
                throw new IllegalArgumentException("Use Entity の mouse が未知です: " + mouse);
            }
            boolean sneaking = reader.readBoolean();
            if (reader.remaining() != 0) {
                throw new IllegalArgumentException(
                        "Use Entity に余分なバイトがあります（残り=" + reader.remaining() + "）。");
            }
            Writer writer = new Writer();
            writer.writeVarInt(target);
            writer.writeVarInt(hand);
            writer.writeFloat(x);
            writer.writeFloat(y);
            writer.writeFloat(z);
            writer.writeBoolean(sneaking);
            return writer.bytes();
        } catch (IllegalArgumentException e) {
            return payload;
        }
    }

    /**
     * Use Entity（775 → 774）。775は {target varint, hand varint, location lpVec3,
     * sneaking bool}、774は {target varint, mouse varint, x／y／z f32, hand varint,
     * sneaking bool}。775 には素の interact（mouse==0）の形が無いため、
     * 常に mouse==2（interact_at）として変換する（location は常に付ける）。
     */
    public static byte[] translateUseEntity775To774(byte[] payload) {
        try {
            Reader reader = new Reader(payload);
            int target = reader.readVarInt();
            int hand = reader.readVarInt();
            float x = reader.readFloat();
            float y = reader.readFloat();
            float z = reader.readFloat();
            boolean sneaking = reader.readBoolean();
            if (reader.remaining() != 0) {
                throw new IllegalArgumentException(
                        "Use Entity に余分なバイトがあります（残り=" + reader.remaining() + "）。");
            }
            Writer writer = new Writer();
            writer.writeVarInt(target);
            writer.writeVarInt(2); // mouse：interact_at
            writer.writeFloat(x);
            writer.writeFloat(y);
            writer.writeFloat(z);
            writer.writeVarInt(hand);
            writer.writeBoolean(sneaking);
            return writer.bytes();
        } catch (IllegalArgumentException e) {
            return payload;
        }
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



# ----------------------------------------------------------------------
# ProxySession 設定フェーズ 6 ハンドラ（手書き）
# ----------------------------------------------------------------------
# gen-775-proxysession.py は play 二段ハンドラだけを生成し、設定フェーズの
# 775From773／772／771・772／771／770From775 は生成しない。dispatch 側は
# 既にこれら 6 メソッドを呼んでいる（Stage B/C の patch で追加済み）ため、
# ここで定義だけを追加する。設定フェーズのパケット番号は771〜775で変わらない
# （新規パケットなし）。Registry Data の段数は 774版の同名ハンドラと同じ。
# 順方向（775From773／772／771）は 774版の 774From773／772／771 と同じく
# dialog（0x11／0x12）の case を持たない。逆方向（772／771From775）は
# 772／771From774 と同じく dialog を素通しし、770From775 は 770From774 と
# 同じく dialog を落とす。逆方向は各段の translateRegistryData が null を
# 返したら drop する（774版と同じ）。

PROXYSESSION_CONFIG_SECTION = """
    /**
     * 775クライアント＋内部773：設定フェーズを中継する。
     * 773と775の設定フェーズの番号は同じため、ほとんど素通しする。
     * Registry Data だけ773→774→775へ二段で並べ替え、Tags はレジストリ番号が
     * ずれるため落とす。Cookie Request には空の Cookie Response を、
     * Select Known Packs には空の一覧をプロキシがクライアント役として返す。
     */
    private Outcome handleServerConfiguration775From773(MinecraftPacket packet) {
        return switch (packet.id()) {
            case 0x00 -> { // Cookie Request：空の Cookie Response を返す
                String key = readCookieKey(packet.payload());
                yield Outcome.toServer(new MinecraftPacket(0x01,
                        emptyCookieResponsePayload(key)));
            }
            case 0x01 -> Outcome.toClient(new MinecraftPacket(0x01, packet.payload())); // Custom Payload（同ID）
            case 0x02 -> // Disconnect（773は NBT。775も NBT）
                    Outcome.toClient(packet);
            case 0x03 -> // Finish Configuration
                    Outcome.toClient(packet);
            case 0x04 -> Outcome.toClient(packet); // Keep Alive（同ID）
            case 0x05 -> Outcome.toClient(packet); // Ping（同ID）
            case 0x06 -> Outcome.toClient(packet); // Reset Chat（同ID）
            case 0x07 -> { // Registry Data：773のエントリ配列を775の順序に並べ替える
                Map<String, Nbt.Tag.CompoundTag> parsed =
                        PlayTranslator.parseRegistryData(packet.payload());
                registries.putAll(parsed);
                yield Outcome.toClient(new MinecraftPacket(0x07,
                        PlayTranslator.translateRegistryData774To775(
                                PlayTranslator.translateRegistryData773To774(packet.payload()))));
            }
            case 0x08 -> Outcome.toClient(packet); // Remove Resource Pack（同ID）
            case 0x09 -> { // Add Resource Pack（773：UUID付き）
                PendingPack pack = PlayTranslator.parseConfigAddResourcePack(packet.payload());
                lastSentPack = pack;
                yield Outcome.toClient(new MinecraftPacket(0x09,
                        PlayTranslator.translateConfigAddResourcePack764To765(
                                pack, packOverrideForClient())));
            }
            case 0x0A -> Outcome.toClient(packet); // Store Cookie（同ID）
            case 0x0B -> Outcome.toClient(packet); // Transfer（同ID）
            case 0x0C -> Outcome.toClient(packet); // Feature Flags（同ID）
            case 0x0D -> { // Tags：レジストリ番号がずれるため落とす
                LOG.info(() -> "レジストリ番号がずれるため Tags を775向けに落とします（" + playerName + "）。");
                yield Outcome.drop();
            }
            case 0x0E -> // Select Known Packs：空の一覧を返す
                    Outcome.toServer(new MinecraftPacket(0x07, emptySelectKnownPacksPayload()));
            case 0x0F -> Outcome.toClient(packet); // Custom Report Details（同ID）
            case 0x10 -> Outcome.toClient(packet); // Server Links（同ID）
            default -> {
                LOG.warning("773の設定フェーズで想定外のパケット（id=0x"
                        + Integer.toHexString(packet.id()) + "）を落とします。");
                yield Outcome.drop();
            }
        };
    }

    /**
     * 775クライアント＋内部772：設定フェーズを中継する。
     * 772と775の設定フェーズの番号は同じため、ほとんど素通しする。
     * Registry Data だけ772→773→774→775へ三段で並べ替え、Tags はレジストリ番号が
     * ずれるため落とす。Cookie Request には空の Cookie Response を、
     * Select Known Packs には空の一覧をプロキシがクライアント役として返す。
     */
    private Outcome handleServerConfiguration775From772(MinecraftPacket packet) {
        return switch (packet.id()) {
            case 0x00 -> { // Cookie Request：空の Cookie Response を返す
                String key = readCookieKey(packet.payload());
                yield Outcome.toServer(new MinecraftPacket(0x01,
                        emptyCookieResponsePayload(key)));
            }
            case 0x01 -> Outcome.toClient(new MinecraftPacket(0x01, packet.payload())); // Custom Payload（同ID）
            case 0x02 -> // Disconnect（772は NBT。775も NBT）
                    Outcome.toClient(packet);
            case 0x03 -> // Finish Configuration
                    Outcome.toClient(packet);
            case 0x04 -> Outcome.toClient(packet); // Keep Alive（同ID）
            case 0x05 -> Outcome.toClient(packet); // Ping（同ID）
            case 0x06 -> Outcome.toClient(packet); // Reset Chat（同ID）
            case 0x07 -> { // Registry Data：772のエントリ配列を775の順序に並べ替える
                Map<String, Nbt.Tag.CompoundTag> parsed =
                        PlayTranslator.parseRegistryData(packet.payload());
                registries.putAll(parsed);
                yield Outcome.toClient(new MinecraftPacket(0x07,
                        PlayTranslator.translateRegistryData774To775(
                                PlayTranslator.translateRegistryData773To774(
                                        PlayTranslator.translateRegistryData772To773(packet.payload())))));
            }
            case 0x08 -> Outcome.toClient(packet); // Remove Resource Pack（同ID）
            case 0x09 -> { // Add Resource Pack（772：UUID付き）
                PendingPack pack = PlayTranslator.parseConfigAddResourcePack(packet.payload());
                lastSentPack = pack;
                yield Outcome.toClient(new MinecraftPacket(0x09,
                        PlayTranslator.translateConfigAddResourcePack764To765(
                                pack, packOverrideForClient())));
            }
            case 0x0A -> Outcome.toClient(packet); // Store Cookie（同ID）
            case 0x0B -> Outcome.toClient(packet); // Transfer（同ID）
            case 0x0C -> Outcome.toClient(packet); // Feature Flags（同ID）
            case 0x0D -> { // Tags：レジストリ番号がずれるため落とす
                LOG.info(() -> "レジストリ番号がずれるため Tags を775向けに落とします（" + playerName + "）。");
                yield Outcome.drop();
            }
            case 0x0E -> // Select Known Packs：空の一覧を返す
                    Outcome.toServer(new MinecraftPacket(0x07, emptySelectKnownPacksPayload()));
            case 0x0F -> Outcome.toClient(packet); // Custom Report Details（同ID）
            case 0x10 -> Outcome.toClient(packet); // Server Links（同ID）
            default -> {
                LOG.warning("772の設定フェーズで想定外のパケット（id=0x"
                        + Integer.toHexString(packet.id()) + "）を落とします。");
                yield Outcome.drop();
            }
        };
    }

    /**
     * 775クライアント＋内部771：設定フェーズを中継する。
     * 771と775の設定フェーズの番号は同じため、ほとんど素通しする。
     * Registry Data だけ771→772→773→774→775へ四段で並べ替え、Tags はレジストリ番号が
     * ずれるため落とす。Cookie Request には空の Cookie Response を、
     * Select Known Packs には空の一覧をプロキシがクライアント役として返す。
     */
    private Outcome handleServerConfiguration775From771(MinecraftPacket packet) {
        return switch (packet.id()) {
            case 0x00 -> { // Cookie Request：空の Cookie Response を返す
                String key = readCookieKey(packet.payload());
                yield Outcome.toServer(new MinecraftPacket(0x01,
                        emptyCookieResponsePayload(key)));
            }
            case 0x01 -> Outcome.toClient(new MinecraftPacket(0x01, packet.payload())); // Custom Payload（同ID）
            case 0x02 -> // Disconnect（771は NBT。775も NBT）
                    Outcome.toClient(packet);
            case 0x03 -> // Finish Configuration
                    Outcome.toClient(packet);
            case 0x04 -> Outcome.toClient(packet); // Keep Alive（同ID）
            case 0x05 -> Outcome.toClient(packet); // Ping（同ID）
            case 0x06 -> Outcome.toClient(packet); // Reset Chat（同ID）
            case 0x07 -> { // Registry Data：771のエントリ配列を775の順序に並べ替える
                Map<String, Nbt.Tag.CompoundTag> parsed =
                        PlayTranslator.parseRegistryData(packet.payload());
                registries.putAll(parsed);
                yield Outcome.toClient(new MinecraftPacket(0x07,
                        PlayTranslator.translateRegistryData774To775(
                                PlayTranslator.translateRegistryData773To774(
                                        PlayTranslator.translateRegistryData772To773(
                                                PlayTranslator.translateRegistryData771To772(packet.payload()))))));
            }
            case 0x08 -> Outcome.toClient(packet); // Remove Resource Pack（同ID）
            case 0x09 -> { // Add Resource Pack（771：UUID付き）
                PendingPack pack = PlayTranslator.parseConfigAddResourcePack(packet.payload());
                lastSentPack = pack;
                yield Outcome.toClient(new MinecraftPacket(0x09,
                        PlayTranslator.translateConfigAddResourcePack764To765(
                                pack, packOverrideForClient())));
            }
            case 0x0A -> Outcome.toClient(packet); // Store Cookie（同ID）
            case 0x0B -> Outcome.toClient(packet); // Transfer（同ID）
            case 0x0C -> Outcome.toClient(packet); // Feature Flags（同ID）
            case 0x0D -> { // Tags：レジストリ番号がずれるため落とす
                LOG.info(() -> "レジストリ番号がずれるため Tags を775向けに落とします（" + playerName + "）。");
                yield Outcome.drop();
            }
            case 0x0E -> // Select Known Packs：空の一覧を返す
                    Outcome.toServer(new MinecraftPacket(0x07, emptySelectKnownPacksPayload()));
            case 0x0F -> Outcome.toClient(packet); // Custom Report Details（同ID）
            case 0x10 -> Outcome.toClient(packet); // Server Links（同ID）
            default -> {
                LOG.warning("771の設定フェーズで想定外のパケット（id=0x"
                        + Integer.toHexString(packet.id()) + "）を落とします。");
                yield Outcome.drop();
            }
        };
    }

    /**
     * 772クライアント＋内部775：設定フェーズを中継する。
     * 772と775の設定フェーズの番号は同じため、ほとんど素通しする。
     * Registry Data だけ775→774→773→772へ三段で並べ替え、Tags はレジストリ番号が
     * ずれるため落とす。Clear Dialog（0x11）／Show Dialog（0x12）は771・772・
     * 773・774・775とも同ID・同形式のため素通しする。Cookie Request には空の
     * Cookie Response を、Select Known Packs には空の一覧をプロキシが
     * クライアント役として返す。
     */
    private Outcome handleServerConfiguration772From775(MinecraftPacket packet) {
        return switch (packet.id()) {
            case 0x00 -> { // Cookie Request：空の Cookie Response を返す
                String key = readCookieKey(packet.payload());
                yield Outcome.toServer(new MinecraftPacket(0x01,
                        emptyCookieResponsePayload(key)));
            }
            case 0x01 -> Outcome.toClient(new MinecraftPacket(0x01, packet.payload())); // Custom Payload（同ID）
            case 0x02 -> // Disconnect（775は NBT。772も NBT）
                    Outcome.toClient(packet);
            case 0x03 -> // Finish Configuration
                    Outcome.toClient(packet);
            case 0x04 -> Outcome.toClient(packet); // Keep Alive（同ID）
            case 0x05 -> Outcome.toClient(packet); // Ping（同ID）
            case 0x06 -> Outcome.toClient(packet); // Reset Chat（同ID）
            case 0x07 -> { // Registry Data：775のエントリ配列を772の順序に並べ替える
                Map<String, Nbt.Tag.CompoundTag> parsed =
                        PlayTranslator.parseRegistryData(packet.payload());
                // 772向けの辞書は772形式で蓄える。
                registries.putAll(PlayTranslator.reorderRegistries(
                        parsed, ProtocolVersion.V26_1, ProtocolVersion.V1_21_7));
                byte[] payload775to774 =
                        PlayTranslator.translateRegistryData775To774(packet.payload());
                if (payload775to774 == null) {
                    yield Outcome.drop();
                }
                byte[] payload774to773 =
                        PlayTranslator.translateRegistryData774To773(payload775to774);
                if (payload774to773 == null) {
                    yield Outcome.drop();
                }
                byte[] payload = PlayTranslator.translateRegistryData773To772(payload774to773);
                if (payload == null) {
                    yield Outcome.drop();
                }
                yield Outcome.toClient(new MinecraftPacket(0x07, payload));
            }
            case 0x08 -> Outcome.toClient(packet); // Remove Resource Pack（同ID）
            case 0x09 -> { // Add Resource Pack（775：UUID付き）
                PendingPack pack = PlayTranslator.parseConfigAddResourcePack(packet.payload());
                lastSentPack = pack;
                yield Outcome.toClient(new MinecraftPacket(0x09,
                        PlayTranslator.translateConfigAddResourcePack764To765(
                                pack, packOverrideForClient())));
            }
            case 0x0A -> Outcome.toClient(packet); // Store Cookie（同ID）
            case 0x0B -> Outcome.toClient(packet); // Transfer（同ID）
            case 0x0C -> Outcome.toClient(packet); // Feature Flags（同ID）
            case 0x0D -> { // Tags：レジストリ番号がずれるため落とす
                LOG.info(() -> "レジストリ番号がずれるため Tags を772向けに落とします（" + playerName + "）。");
                yield Outcome.drop();
            }
            case 0x0E -> // Select Known Packs：空の一覧を返す
                    Outcome.toServer(new MinecraftPacket(0x07, emptySelectKnownPacksPayload()));
            case 0x0F -> Outcome.toClient(packet); // Custom Report Details（同ID）
            case 0x10 -> Outcome.toClient(packet); // Server Links（同ID）
            case 0x11, 0x12 -> // Clear Dialog（0x11）／Show Dialog（0x12）は771・772・773・774・775とも同ID・同形式のため素通し
                    Outcome.toClient(packet);
            default -> {
                LOG.warning("775の設定フェーズで想定外のパケット（id=0x"
                        + Integer.toHexString(packet.id()) + "）を落とします。");
                yield Outcome.drop();
            }
        };
    }

    /**
     * 771クライアント＋内部775：設定フェーズを中継する。
     * 771と775の設定フェーズの番号は同じため、ほとんど素通しする。
     * Registry Data だけ775→774→773→772→771へ四段で並べ替え、Tags はレジストリ番号が
     * ずれるため落とす。Clear Dialog（0x11）／Show Dialog（0x12）は771にも
     * あるため素通しする。Cookie Request には空の Cookie Response を、
     * Select Known Packs には空の一覧をプロキシがクライアント役として返す。
     */
    private Outcome handleServerConfiguration771From775(MinecraftPacket packet) {
        return switch (packet.id()) {
            case 0x00 -> { // Cookie Request：空の Cookie Response を返す
                String key = readCookieKey(packet.payload());
                yield Outcome.toServer(new MinecraftPacket(0x01,
                        emptyCookieResponsePayload(key)));
            }
            case 0x01 -> Outcome.toClient(new MinecraftPacket(0x01, packet.payload())); // Custom Payload（同ID）
            case 0x02 -> // Disconnect（775は NBT。771も NBT）
                    Outcome.toClient(packet);
            case 0x03 -> // Finish Configuration
                    Outcome.toClient(packet);
            case 0x04 -> Outcome.toClient(packet); // Keep Alive（同ID）
            case 0x05 -> Outcome.toClient(packet); // Ping（同ID）
            case 0x06 -> Outcome.toClient(packet); // Reset Chat（同ID）
            case 0x07 -> { // Registry Data：775のエントリ配列を771の順序に並べ替える
                Map<String, Nbt.Tag.CompoundTag> parsed =
                        PlayTranslator.parseRegistryData(packet.payload());
                // 771向けの辞書は771形式で蓄える。
                registries.putAll(PlayTranslator.reorderRegistries(
                        parsed, ProtocolVersion.V26_1, ProtocolVersion.V1_21_6));
                byte[] payload775to774 =
                        PlayTranslator.translateRegistryData775To774(packet.payload());
                if (payload775to774 == null) {
                    yield Outcome.drop();
                }
                byte[] payload774to773 =
                        PlayTranslator.translateRegistryData774To773(payload775to774);
                if (payload774to773 == null) {
                    yield Outcome.drop();
                }
                byte[] payload773to772 =
                        PlayTranslator.translateRegistryData773To772(payload774to773);
                if (payload773to772 == null) {
                    yield Outcome.drop();
                }
                byte[] payload = PlayTranslator.translateRegistryData772To771(payload773to772);
                if (payload == null) {
                    yield Outcome.drop();
                }
                yield Outcome.toClient(new MinecraftPacket(0x07, payload));
            }
            case 0x08 -> Outcome.toClient(packet); // Remove Resource Pack（同ID）
            case 0x09 -> { // Add Resource Pack（775：UUID付き）
                PendingPack pack = PlayTranslator.parseConfigAddResourcePack(packet.payload());
                lastSentPack = pack;
                yield Outcome.toClient(new MinecraftPacket(0x09,
                        PlayTranslator.translateConfigAddResourcePack764To765(
                                pack, packOverrideForClient())));
            }
            case 0x0A -> Outcome.toClient(packet); // Store Cookie（同ID）
            case 0x0B -> Outcome.toClient(packet); // Transfer（同ID）
            case 0x0C -> Outcome.toClient(packet); // Feature Flags（同ID）
            case 0x0D -> { // Tags：レジストリ番号がずれるため落とす
                LOG.info(() -> "レジストリ番号がずれるため Tags を771向けに落とします（" + playerName + "）。");
                yield Outcome.drop();
            }
            case 0x0E -> // Select Known Packs：空の一覧を返す
                    Outcome.toServer(new MinecraftPacket(0x07, emptySelectKnownPacksPayload()));
            case 0x0F -> Outcome.toClient(packet); // Custom Report Details（同ID）
            case 0x10 -> Outcome.toClient(packet); // Server Links（同ID）
            case 0x11, 0x12 -> // Clear Dialog（0x11）／Show Dialog（0x12）は771・772・773・774・775とも同ID・同形式のため素通し
                    Outcome.toClient(packet);
            default -> {
                LOG.warning("775の設定フェーズで想定外のパケット（id=0x"
                        + Integer.toHexString(packet.id()) + "）を落とします。");
                yield Outcome.drop();
            }
        };
    }

    /**
     * 770クライアント＋内部775：設定フェーズを中継する。
     * 770と775の設定フェーズの番号は同じため、ほとんど素通しする。
     * Registry Data だけ775→774→773→772→771→770へ五段で並べ替え、Tags はレジストリ番号が
     * ずれるため落とす。Clear Dialog（0x11）／Show Dialog（0x12）は770に無いため
     * 落とす。Cookie Request には空の Cookie Response を、Select Known
     * Packs には空の一覧をプロキシがクライアント役として返す。
     */
    private Outcome handleServerConfiguration770From775(MinecraftPacket packet) {
        return switch (packet.id()) {
            case 0x00 -> { // Cookie Request：空の Cookie Response を返す
                String key = readCookieKey(packet.payload());
                yield Outcome.toServer(new MinecraftPacket(0x01,
                        emptyCookieResponsePayload(key)));
            }
            case 0x01 -> Outcome.toClient(new MinecraftPacket(0x01, packet.payload())); // Custom Payload（同ID）
            case 0x02 -> // Disconnect（775は NBT。770も NBT）
                    Outcome.toClient(packet);
            case 0x03 -> // Finish Configuration
                    Outcome.toClient(packet);
            case 0x04 -> Outcome.toClient(packet); // Keep Alive（同ID）
            case 0x05 -> Outcome.toClient(packet); // Ping（同ID）
            case 0x06 -> Outcome.toClient(packet); // Reset Chat（同ID）
            case 0x07 -> { // Registry Data：775のエントリ配列を770の順序に並べ替える
                Map<String, Nbt.Tag.CompoundTag> parsed =
                        PlayTranslator.parseRegistryData(packet.payload());
                // 770向けの辞書は770形式で蓄える。
                registries.putAll(PlayTranslator.reorderRegistries(
                        parsed, ProtocolVersion.V26_1, ProtocolVersion.V1_21_5));
                byte[] payload775to774 =
                        PlayTranslator.translateRegistryData775To774(packet.payload());
                if (payload775to774 == null) {
                    yield Outcome.drop();
                }
                byte[] payload774to773 =
                        PlayTranslator.translateRegistryData774To773(payload775to774);
                if (payload774to773 == null) {
                    yield Outcome.drop();
                }
                byte[] payload773to772 =
                        PlayTranslator.translateRegistryData773To772(payload774to773);
                if (payload773to772 == null) {
                    yield Outcome.drop();
                }
                byte[] payload772to771 =
                        PlayTranslator.translateRegistryData772To771(payload773to772);
                if (payload772to771 == null) {
                    yield Outcome.drop();
                }
                byte[] payload = PlayTranslator.translateRegistryData771To770(payload772to771);
                if (payload == null) {
                    yield Outcome.drop();
                }
                yield Outcome.toClient(new MinecraftPacket(0x07, payload));
            }
            case 0x08 -> Outcome.toClient(packet); // Remove Resource Pack（同ID）
            case 0x09 -> { // Add Resource Pack（775：UUID付き）
                PendingPack pack = PlayTranslator.parseConfigAddResourcePack(packet.payload());
                lastSentPack = pack;
                yield Outcome.toClient(new MinecraftPacket(0x09,
                        PlayTranslator.translateConfigAddResourcePack764To765(
                                pack, packOverrideForClient())));
            }
            case 0x0A -> Outcome.toClient(packet); // Store Cookie（同ID）
            case 0x0B -> Outcome.toClient(packet); // Transfer（同ID）
            case 0x0C -> Outcome.toClient(packet); // Feature Flags（同ID）
            case 0x0D -> { // Tags：レジストリ番号がずれるため落とす
                LOG.info(() -> "レジストリ番号がずれるため Tags を770向けに落とします（" + playerName + "）。");
                yield Outcome.drop();
            }
            case 0x0E -> // Select Known Packs：空の一覧を返す
                    Outcome.toServer(new MinecraftPacket(0x07, emptySelectKnownPacksPayload()));
            case 0x0F -> Outcome.toClient(packet); // Custom Report Details（同ID）
            case 0x10 -> Outcome.toClient(packet); // Server Links（同ID）
            case 0x11, 0x12 -> // Show Dialog／Clear Dialog（770に無い）
                    Outcome.drop();
            default -> {
                LOG.warning("775の設定フェーズで想定外のパケット（id=0x"
                        + Integer.toHexString(packet.id()) + "）を落とします。");
                yield Outcome.drop();
            }
        };
    }
"""


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



def main():
    # 774節だけを抜き出す（後ろに 762／761／760／759節があるため
    # extract_section_to_eof は使えない）。
    chunk_sec, cs, ce = extract_section(
        CT, "// 773 ⇔ 774（1.21.11）", "// 763 ⇔ 762（1.20／1.20.1 ⇔ 1.19.4）")
    print(f"chunk section: lines {cs+1}-{ce+1} ({len(chunk_sec)} chars)")
    Path("/tmp/chunk774_section.txt").write_text(chunk_sec, encoding="utf-8")
    chunk_shifted = shift(chunk_sec)
    Path("/tmp/chunk775_shifted.txt").write_text(chunk_shifted, encoding="utf-8")
    print(f"chunk shifted: {len(chunk_shifted)} chars")

    # --- ChunkTranslator 775 節 ---
    chunk775 = apply_fixes(chunk_shifted, CHUNK_STRING_FIXES, "chunk-string")
    for sig, new_text in CHUNK_METHOD_REPLACEMENTS.items():
        chunk775 = replace_method(chunk775, sig, new_text, "chunk")
    for sig in DELETE_METHODS:
        chunk775 = delete_method(chunk775, sig, "chunk-delete")
    chunk775 = chunk775.rstrip() + "\n" + EXTRA_METHODS_775
    print(f"chunk775: {len(chunk775)} chars, "
          f"{chunk775.count('translate')} translate occurrences")
    insert_section_before_class_close(
        CT, chunk775, "chunk", "translateSlot774To775")

    # --- PlayTranslator 775 節（手書き） ---
    insert_section_before_class_close(
        PT, PLAY_775_SECTION, "play", "translatePlayerChat774To775")

    # --- reorderRegistries の jukebox_song フィルタ：775 も残す ---
    pt_text = PT.read_text(encoding="utf-8")
    pt_text = fix(pt_text,
                  """            if (registryId.equals("minecraft:jukebox_song")
                    && toProtocol != ProtocolVersion.V1_21
                    && toProtocol != ProtocolVersion.V1_21_2
                    && toProtocol != ProtocolVersion.V1_21_4
                    && toProtocol != ProtocolVersion.V1_21_5
                    && toProtocol != ProtocolVersion.V1_21_6
                    && toProtocol != ProtocolVersion.V1_21_7
                    && toProtocol != ProtocolVersion.V1_21_9
                    && toProtocol != ProtocolVersion.V1_21_11) {""",
                  """            if (registryId.equals("minecraft:jukebox_song")
                    && toProtocol != ProtocolVersion.V1_21
                    && toProtocol != ProtocolVersion.V1_21_2
                    && toProtocol != ProtocolVersion.V1_21_4
                    && toProtocol != ProtocolVersion.V1_21_5
                    && toProtocol != ProtocolVersion.V1_21_6
                    && toProtocol != ProtocolVersion.V1_21_7
                    && toProtocol != ProtocolVersion.V1_21_9
                    && toProtocol != ProtocolVersion.V1_21_11
                    && toProtocol != ProtocolVersion.V26_1) {""",
                  "play-jukebox")
    # minecraft:dialog は771・772・773・774・775にしか無いため、771・772・773・774・775以外向けは落とす。
    pt_text = fix(pt_text,
                  """            if (registryId.equals("minecraft:dialog")
                    && toProtocol != ProtocolVersion.V1_21_6
                    && toProtocol != ProtocolVersion.V1_21_7
                    && toProtocol != ProtocolVersion.V1_21_9
                    && toProtocol != ProtocolVersion.V1_21_11) {""",
                  """            if (registryId.equals("minecraft:dialog")
                    && toProtocol != ProtocolVersion.V1_21_6
                    && toProtocol != ProtocolVersion.V1_21_7
                    && toProtocol != ProtocolVersion.V1_21_9
                    && toProtocol != ProtocolVersion.V1_21_11
                    && toProtocol != ProtocolVersion.V26_1) {""",
                  "play-dialog")
    # jukebox_song の並べ替え：774↔775 の対応表を追加する。
    # （773・774・775で曲は同一のため対応表は実質そのまま。Stage A で
    #  生成済みの mapJukeboxSong774To775／775To774 を通す。）
    pt_text = fix(pt_text,
                  """                case 773774 -> RegistryRemapper::mapJukeboxSong773To774;
                case 774773 -> RegistryRemapper::mapJukeboxSong774To773;
                default -> null;""",
                  """                case 773774 -> RegistryRemapper::mapJukeboxSong773To774;
                case 774773 -> RegistryRemapper::mapJukeboxSong774To773;
                case 774775 -> RegistryRemapper::mapJukeboxSong774To775;
                case 775774 -> RegistryRemapper::mapJukeboxSong775To774;
                default -> null;""",
                  "play-jukebox-mapper")
    # jukebox_song の targetNames：775 が無いと reorderRegistryEntries で
    # targetNames が null になり NPE になる（773・774・775で曲は同一のため
    # 773 の表を使う。774 のときの 5a17f8c と同じ罠）。
    pt_text = fix(pt_text,
                  """            case "minecraft:jukebox_song" -> switch (to) {
                case 774 -> RegistryTables773.JUKEBOX_SONG_773_NAMES; // 773と同一の21曲""",
                  """            case "minecraft:jukebox_song" -> switch (to) {
                case 775 -> RegistryTables773.JUKEBOX_SONG_773_NAMES; // 774と同一の21曲
                case 774 -> RegistryTables773.JUKEBOX_SONG_773_NAMES; // 773と同一の21曲""",
                  "play-jukebox-target-names")
    # enchantment の並べ替え：774↔775 の対応表を追加する。
    # （エンチャントは774・775とも43件・同じ並びのため対応表は実質そのまま。
    #  無いと reorderRegistryEntries が素通ししてしまう。）
    pt_text = fix(pt_text,
                  """                case 773774 -> RegistryRemapper::mapEnchantment773To774;
                case 774773 -> RegistryRemapper::mapEnchantment774To773;
                default -> null;""",
                  """                case 773774 -> RegistryRemapper::mapEnchantment773To774;
                case 774773 -> RegistryRemapper::mapEnchantment774To773;
                case 774775 -> RegistryRemapper::mapEnchantment774To775;
                case 775774 -> RegistryRemapper::mapEnchantment775To774;
                default -> null;""",
                  "play-enchantment-mapper")
    # damage_type の並べ替え：774↔775 の対応表を追加する。
    # （ダメージ種別は774・775とも50件・同じ並びのため対応表は実質そのまま。）
    pt_text = fix(pt_text,
                  """                case 773774 -> RegistryRemapper::mapDamageType773To774;
                case 774773 -> RegistryRemapper::mapDamageType774To773;
                default -> null;""",
                  """                case 773774 -> RegistryRemapper::mapDamageType773To774;
                case 774773 -> RegistryRemapper::mapDamageType774To773;
                case 774775 -> RegistryRemapper::mapDamageType774To775;
                case 775774 -> RegistryRemapper::mapDamageType775To774;
                default -> null;""",
                  "play-damage-mapper")
    # registryTargetNames：enchantment は774・775とも ENCHANTMENT_774_NAMES（43件・同一）。
    # 新しい表は作らない（lunge は774に既にある。773→774 の罠に注意）。
    pt_text = fix(pt_text,
                  """                case 768, 769, 770, 771, 772, 773 -> ENCHANTMENT_768_NAMES;
                case 774 -> ENCHANTMENT_774_NAMES;
                default -> null;""",
                  """                case 768, 769, 770, 771, 772, 773 -> ENCHANTMENT_768_NAMES;
                case 774 -> ENCHANTMENT_774_NAMES;
                case 775 -> ENCHANTMENT_774_NAMES; // 774と同一の43件
                default -> null;""",
                  "play-enchantment-targets")
    # registryTargetNames：damage_type は774・775とも DAMAGE_774_NAMES（50件・同一）。
    # 新しい表は作らない（spear は774に既にある。default は DAMAGE_766_NAMES（48件）のため
    # 775 を明示しないと並べ替えで範囲外になって例外になる）。
    pt_text = fix(pt_text,
                  """                case 768, 769, 770, 771, 772, 773 -> DAMAGE_768_NAMES;
                case 774 -> DAMAGE_774_NAMES;
                default -> DAMAGE_766_NAMES;""",
                  """                case 768, 769, 770, 771, 772, 773 -> DAMAGE_768_NAMES;
                case 774 -> DAMAGE_774_NAMES;
                case 775 -> DAMAGE_774_NAMES; // 774と同一の50件
                default -> DAMAGE_766_NAMES;""",
                  "play-damage-targets")
    PT.write_text(pt_text, encoding="utf-8")
    print("[play-registry] OK")

    # --- ProxySession 設定フェーズ 6 ハンドラ（手書き） ---
    insert_before_marker(
        PS, PROXYSESSION_CONFIG_SECTION, "proxysession-config",
        "private Outcome handleServerConfiguration769From775(MinecraftPacket packet)",
        "private Outcome handleServerConfiguration775From773(MinecraftPacket packet)")

    print("完了。次は :detergentmc-protocol:compileJava で確認すること。")


if __name__ == "__main__":
    main()
