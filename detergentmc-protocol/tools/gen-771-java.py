#!/usr/bin/env python3
"""insert-770-java.py から insert-771-java.py を生成する。

方針: gen-770-proxysession.py の shift() と同じ「1段ずらす」考え方で
insert-770-java.py を 771 用にずらし、構造の変わる部分だけ FIXES で
直す。ずらしはプレースホルダで保護して二重シフトを防ぐ。

ずらすもの（770→771、769→770）：
- 単独の数字 769／770（16進リテラルは対象外。KINDS の件数は後で
  FIXES で置き換える）、PROTOCOL_769／770、V1_21_4／V1_21_5、
  「1.21.4」／「1.21.5」（「1.21.2／1.21.3」は 769 の 1.21.4 へ。
  その後「1.21.4」→「1.21.5」、「1.21.5」→「1.21.6」の順で
  ずらすと二重になるため、すべてプレースホルダ経由にする）。
- 矢印チェーン（→）・⇔・／リスト・・リスト：gen-770-proxysession.py
  の規則を 770 を軸に適用する。ただし 2要素ペアは意味で分ける：
  隣接ペア（769→770／770→769）は両端を +1、合成ペア
  （763→770／770→763 など）は対象側（770）だけ 771 に、
  769 を含むペア（767→769 など）は 769 だけ 770 にする。
- is770() チェーンには is771() を追加する。

FIXES で直すもの：
- KINDS：件数を 771 用に置き換え、769／770 の組と 770／771 の
  代替先を追加（タプルは件数 16＋代替先 9＝28 要素になる）。
- CONSTANTS_MAX／CONSTANTS_FALLBACK／CONSTANTS_BLOCK_ENTITY：
  771 の値に置き換える。
- adjacent_maps()：kind[21] → kind[27]（771 の代替先）。
- composed_maps()：pairs を 769／770 の組まで拡張し、代替先の
  位置を新しいタプルに合わせる。
- up_maps()：補正は無し（771 はプロパティ変更が無い）。代替先は
  770 側の定数 kind[26] をそのまま使う。
- adjacent_accessors()：コメントの「11件」→「19件」など 771 用に直す。
  属性は並びが同一ではない（camera_distance の挿入で 8 番以降が
  +1、waypoint_* が末尾に追加）ことをコメントに書く。
  エフェクト・ブロックエンティティ種別は 770 と 771 で同一。
- up_accessors()：コメントの「11件」→「19件」、新音 29 件の記述。
- composed_accessors()：pairs を 769／770 まで拡張。763 の組は
  ずらしで 764 になってしまうため元に戻し、769／770 の組は新規に書く
  （763：エフェクト 1ベース・ブロックエンティティ +1・音は
  SOUND_NAME_FALLBACK_768_MAP＋SOUND_NAME_FALLBACK_771_MAP。
  769：エフェクト恒等・ブロックエンティティ恒等・音は
  SOUND_NAME_FALLBACK_769_TO_771_MAP。
  770：エフェクト恒等・ブロックエンティティ恒等・音は
  SOUND_NAME_FALLBACK_770_TO_771_MAP）。
  768 の組は 769 になってしまうが、769 のブロックエンティティは
  768 と違って creaking_heart を持つため恒等写像に直す。
- small_registry_accessors()／damage_accessors()：ループを
  763〜770 まで拡張し、経由先を 770→771 にする。
- JAVADOC_770 → JAVADOC_771：差分を 771 用に書き換える。
- main()：ファイル名・チェックマーカー・アンカーを 771 用にする。
  合成表のアンカーは MENU_770_TO_768_MAP（770 の合成表の最後）に
  直す。ディスパッチのコメント置き換えは旧文（769 と 770 を含む
  もの）を正しく指定する。
"""
import json
import re
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
SRC = HERE / "insert-770-java.py"
OUT = HERE / "insert-771-java.py"
REPO_ROOT = HERE.parent.parent
DATA_DIR = REPO_ROOT / "detergentmc-protocol" / "build" / "remap-data"

_counter = [0]


def protect(text, pattern, repl_fn):
    holders = {}

    def sub(m):
        key = f"\x02{_counter[0]}\x03"
        _counter[0] += 1
        holders[key] = repl_fn(m)
        return key

    return pattern.sub(sub, text), holders


def restore(text, holders):
    for k, v in holders.items():
        text = text.replace(k, v)
    return text


ARROW = re.compile(r"\d{3}(?:→\d{3})+")
DBL = re.compile(r"\d{3}(?:⇔\d{3})+")
SLASH = re.compile(r"\d{3}(?:／\d{3})+")
DOT = re.compile(r"\d{3}(?:・\d{3})+")
PROTO_VER = re.compile(r"\d{3}＝\d+\.\d+(?:\.\d+)?(?:／\d+\.\d+(?:\.\d+)?)?"
                       r"|\d{3} \(1\.\d+(?:\.\d+)?(?:/1\.\d+(?:\.\d+)?)?\)")
IS_CHAIN = re.compile(r"((?:is(?:Server)?\d{3}\(\)\s*\|\|\s*)+is(?:Server)?770\(\))")


def shift_pair(m, sep):
    """2要素ペアのずらし。意味で分ける。"""
    a, b = m.group(0).split(sep)
    if (a, b) in (("769", "770"), ("770", "769")):
        return sep.join(str(int(n) + 1) for n in (a, b))
    if b == "770":
        return a + sep + "771"
    if a == "770":
        return "771" + sep + b
    if b == "769":
        return a + sep + "770"
    if a == "769":
        return "770" + sep + b
    # 769／770 を含まないペアはそのまま（例：766→767 は 771 でも同じ）。
    return m.group(0)


def shift_chain(m, kind):
    sep = "→" if kind == "arrow" else "⇔"
    nums = m.group(0).split(sep)
    if len(nums) == 2:
        return shift_pair(m, sep)
    if len(nums) >= 3 and nums[0] == "770":
        return "771" + sep + m.group(0)
    if nums[-1] == "770":
        return m.group(0) + sep + "771"
    if nums[-1] == "769":
        return m.group(0) + sep + "770"
    return m.group(0)


def shift_list(m, sep):
    nums = m.group(0).split(sep)
    if nums[-1] == "770":
        return m.group(0) + sep + "771"
    if nums[-1] == "769":
        return m.group(0) + sep + "770"
    return m.group(0)


def shift(text):
    text, h0 = protect(text, PROTO_VER, lambda m: m.group(0))
    text, h1 = protect(text, ARROW, lambda m: shift_chain(m, "arrow"))
    text, h2 = protect(text, DBL, lambda m: shift_chain(m, "dbl"))
    text, h3 = protect(text, SLASH, lambda m: shift_list(m, "／"))
    text, h4 = protect(text, DOT, lambda m: shift_list(m, "・"))

    def is_chain_sub(m):
        chain = m.group(1)
        last = re.search(r"is(Server)?770\(\)$", chain)
        suffix = "isServer771()" if last.group(1) else "is771()"
        key = f"\x02{_counter[0]}\x03"
        _counter[0] += 1
        is_chain_holders[key] = chain + " || " + suffix
        return key

    is_chain_holders = {}
    text = IS_CHAIN.sub(is_chain_sub, text)
    holders = {**h0, **h1, **h2, **h3, **h4, **is_chain_holders}
    # バージョン文字列・定数名はプレースホルダ経由で一度にずらす
    # （二重シフト防止）。
    ph = {}

    def ph_sub(repl):
        def sub(m):
            key = f"\x02{_counter[0]}\x03"
            _counter[0] += 1
            ph[key] = repl
            return key
        return sub

    text = re.sub(r"1\.21\.2／1\.21\.3", ph_sub("1.21.4"), text)
    text = re.sub(r"1\.21\.5", ph_sub("1.21.6"), text)
    text = re.sub(r"1\.21\.4", ph_sub("1.21.5"), text)
    text = re.sub(r"1\.21\.2", ph_sub("1.21.4"), text)
    text = re.sub(r"V1_21_5", ph_sub("V1_21_6"), text)
    text = re.sub(r"V1_21_4", ph_sub("V1_21_5"), text)
    text = re.sub(r"PROTOCOL_770", ph_sub("PROTOCOL_771"), text)
    text = re.sub(r"PROTOCOL_769", ph_sub("PROTOCOL_770"), text)
    text = re.sub(r"pack770", ph_sub("pack771"), text)
    text = re.sub(r"pack769", ph_sub("pack770"), text)
    holders.update(ph)
    # 単独の数字（16進リテラルは対象外）。まず 770 を保護してから
    # 769→770、保護分→771。
    text = re.sub(r"(?<!\d)770(?!\d)", "\x01770", text)
    text = re.sub(r"(?<!\d)769(?!\d)", "770", text)
    text = text.replace("\x01770", "771")
    return restore(text, holders)


def vv_sizes():
    """ViaVersion mapping から各バージョンの件数を引く。"""
    files = {"763": "vv-1.20.json", "764": "vv-1.20.2.json",
             "765": "vv-1.20.3.json", "766": "vv-1.20.5.json",
             "767": "vv-1.21.json", "768": "vv-1.21.2.json",
             "769": "vv-1.21.4.json", "770": "vv-1.21.5.json",
             "771": "vv-1.21.6.json"}
    out = {}
    for tag, fname in files.items():
        data = json.loads((DATA_DIR / fname).read_text(encoding="utf-8"))
        out[tag] = {kind: len(data[kind]) for kind in
                    ("blockstates", "items", "entities", "sounds",
                     "particles", "blocks", "menus")}
    return out


def kinds_block(sizes):
    """KINDS タプル（771用）を生成する。"""
    rows = []
    specs = [
        ("BlockState", "BLOCK_STATE", "ブロック状態", "blockstates",
         "STONE_BLOCK_STATE"),
        ("Item", "ITEM", "アイテム", "items", "STONE_ITEM"),
        ("EntityType", "ENTITY_TYPE", "エンティティ種別", "entities",
         "PIG_ENTITY_TYPE"),
        ("Sound", "SOUND", "音", "sounds", "GENERIC_SOUND"),
        ("Particle", "PARTICLE", "パーティクル", "particles",
         "ENTITY_EFFECT_PARTICLE"),
        ("Block", "BLOCK", "ブロック", "blocks", "STONE_BLOCK"),
        ("Menu", "MENU", "メニュー種別", "menus", "CRAFTING_MENU"),
    ]
    # 763／764 のパーティクル代替は 765 と違う定数（AMBIENT_PARTICLE_*）。
    # 770 の KINDS と同じ並びにする。
    for name, prefix, jp, kind, fb_base in specs:
        counts = []
        for src in ("763", "764", "765", "766", "767", "768", "769", "770"):
            counts.append(str(sizes[src][kind]))
            counts.append(str(sizes["771"][kind]))
        if prefix == "PARTICLE":
            fbs = ["AMBIENT_PARTICLE_763", "AMBIENT_PARTICLE_764",
                   "ENTITY_EFFECT_PARTICLE_765", "ENTITY_EFFECT_PARTICLE_766",
                   "ENTITY_EFFECT_PARTICLE_767", "ENTITY_EFFECT_PARTICLE_768",
                   "ENTITY_EFFECT_PARTICLE_769", "ENTITY_EFFECT_PARTICLE_770",
                   "ENTITY_EFFECT_PARTICLE_771"]
        else:
            fbs = [f"{fb_base}_{v}" for v in
                   ("763", "764", "765", "766", "767", "768", "769", "770", "771")]
        rows.append(
            f'    ("{name}", "{prefix}", "{jp}", '
            + ", ".join(counts) + ",\n     "
            + ", ".join(f'"{f}"' for f in fbs) + "),")
    return "KINDS = [\n" + "\n".join(rows) + "\n]\n"


def main():
    text = SRC.read_text(encoding="utf-8")
    text = shift(text)
    sizes = vv_sizes()
    # --- KINDS を置き換える（ずらしでは件数が合わないため） ---
    start = text.index("# kind: (Java メソッド名の語尾")
    end = text.index("]\n", start) + 2
    kinds_header = '''# kind: (Java メソッド名の語尾, RANGES の接頭辞, 日本語名,
#        763→771 の件数, 771→763 の件数, 764→771 の件数, 771→764 の件数,
#        765→771 の件数, 771→765 の件数, 766→771 の件数, 771→766 の件数,
#        767→771 の件数, 771→767 の件数, 768→771 の件数, 771→768 の件数,
#        769→771 の件数, 771→769 の件数, 770→771 の件数, 771→770 の件数,
#        763 側の代替先, 764 側の代替先, 765 側の代替先, 766 側の代替先,
#        767 側の代替先, 768 側の代替先, 769 側の代替先, 770 側の代替先,
#        771 側の代替先)
'''
    text = text[:start] + kinds_header + kinds_block(sizes) + text[end:]
    OUT.write_text(text, encoding="utf-8")
    print(f"insert-771-java.py を書き出した（KINDS のみ置き換え済み）。")
    print("sizes:", {k: sizes[k] for k in ("769", "770", "771")})
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
