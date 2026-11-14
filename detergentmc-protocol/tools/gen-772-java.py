#!/usr/bin/env python3
"""insert-771-java.py から insert-772-java.py を生成する。

方針: gen-771-java.py の shift() と同じ「1段ずらす」考え方で
insert-771-java.py を 772 用にずらし、構造の変わる部分だけ FIXES で
直す。ずらしはプレースホルダで保護して二重シフトを防ぐ。

ずらすもの（771→772、770→771）：
- 単独の数字 770／771（16進リテラルは対象外。KINDS の件数は後で
  FIXES で置き換える）、PROTOCOL_770／771、V1_21_5／V1_21_6、
  「1.21.5」／「1.21.6」（プレースホルダ経由で一度にずらすと
  二重シフトを防げる）。
- 矢印チェーン（→）・⇔・／リスト・・リスト：gen-771-java.py
  の規則を 771 を軸に適用する。ただし 2要素ペアは意味で分ける：
  隣接ペア（770→771／771→770）は両端を +1、合成ペア
  （763→771／771→763 など）は対象側（771）だけ 772 に、
  770 を含むペア（767→770 など）は 770 だけ 771 にする。
  前の隣接ペア（769→770／770→769）も両端を +1 する。
- is771() チェーンには is772() を追加する。

FIXES で直すもの：
- KINDS：件数を 772 用に置き換え、770／771 の組と 771／772 の
  代替先を追加（タプルは件数 18＋代替先 10＝28 要素になる）。
- CONSTANTS_MAX／CONSTANTS_FALLBACK／CONSTANTS_BLOCK_ENTITY：
  772 の値に置き換える。
- adjacent_maps()：kind[27] → kind[30]（772 の代替先）。
- composed_maps()：pairs を 770／771 の組まで拡張し、代替先の
  位置を新しいタプルに合わせる。
- up_maps()：補正は無し（772 はプロパティ変更が無い）。代替先は
  771 側の定数 kind[29] をそのまま使う。
- adjacent_accessors()：コメントの「19件」→「1件」など 772 用に直す。
  属性は 771・772 で同一の 35 件、エフェクト・ブロックエンティティ種別も
  771 と 772 で同一。絵画だけが変わる（dennis が 14 に挿入され 51 件）。
  絵画の隣接アクセサは恒等写像ではなくずらし＋DROP に直す。
- up_accessors()：コメントの「19件」→「1件」、新音 1 件（レコード
  「ラヴァ・チキン」）の記述。絵画の逆向きもずらし＋DROP に直す。
- composed_accessors()：pairs を 770／771 まで拡張。770 の組は
  新規に書く（エフェクト恒等・ブロックエンティティ恒等・音は
  SOUND_NAME_FALLBACK_770_TO_772_MAP）。音の名前の連鎖は 770_MAP の
  段階を戻す（ずらしで 770_MAP が 771_MAP に置き換わって消えるため）。
  763 の組は 768_MAP＋770_MAP＋771_MAP＋772_MAP の 4 段にする。
- small_registry_accessors()／damage_accessors()：ループを
  763〜771 まで拡張し、経由先を 771→772 にする。絵画の隣接だけは
  恒等写像ではない（13 の挿入）ため別に直す。
- ジュークボックスの曲と絵画：insert-*-java.py では扱わないため、
  JUKEBOX_SONG_772_NAMES（lava_chicken を 9 に挿入した 21 件）と
  mapJukeboxSong771To772／772To771／jukeboxSongName772、絵画の
  mapPaintingVariant771To772／772To771 は手で RegistryRemapper.java
  に書き、PlayTranslator／ChunkTranslator の呼び出し側も直す。
- JAVADOC_771 → JAVADOC_772：差分を 772 用に書き換える。
- main()：ファイル名・チェックマーカー・アンカーを 772 用にする。
  合成表のアンカーは MENU_771_TO_769_MAP（771 の合成表の最後）に
  直す。ディスパッチのコメント置き換えは旧文（770 と 771 を含む
  もの）を正しく指定する。
"""
import json
import re
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
SRC = HERE / "insert-771-java.py"
OUT = HERE / "insert-772-java.py"
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
IS_CHAIN = re.compile(r"((?:is(?:Server)?\d{3}\(\)\s*\|\|\s*)+is(?:Server)?771\(\))")


def shift_pair(m, sep):
    """2要素ペアのずらし。意味で分ける。"""
    a, b = m.group(0).split(sep)
    if (a, b) in (("770", "771"), ("771", "770"),
                  ("769", "770"), ("770", "769")):
        return sep.join(str(int(n) + 1) for n in (a, b))
    if b == "771":
        return a + sep + "772"
    if a == "771":
        return "772" + sep + b
    if b == "770":
        return a + sep + "771"
    if a == "770":
        return "771" + sep + b
    # 770／771 を含まないペアはそのまま（例：766→767 は 772 でも同じ）。
    return m.group(0)


def shift_chain(m, kind):
    sep = "→" if kind == "arrow" else "⇔"
    nums = m.group(0).split(sep)
    if len(nums) == 2:
        return shift_pair(m, sep)
    if len(nums) >= 3 and nums[0] == "771":
        return "772" + sep + m.group(0)
    if nums[-1] == "771":
        return m.group(0) + sep + "772"
    if nums[-1] == "770":
        return m.group(0) + sep + "771"
    return m.group(0)


def shift_list(m, sep):
    nums = m.group(0).split(sep)
    if nums[-1] == "771":
        return m.group(0) + sep + "772"
    if nums[-1] == "770":
        return m.group(0) + sep + "771"
    return m.group(0)


def shift(text):
    text, h0 = protect(text, PROTO_VER, lambda m: m.group(0))
    text, h1 = protect(text, ARROW, lambda m: shift_chain(m, "arrow"))
    text, h2 = protect(text, DBL, lambda m: shift_chain(m, "dbl"))
    text, h3 = protect(text, SLASH, lambda m: shift_list(m, "／"))
    text, h4 = protect(text, DOT, lambda m: shift_list(m, "・"))

    def is_chain_sub(m):
        chain = m.group(1)
        last = re.search(r"is(Server)?771\(\)$", chain)
        suffix = "isServer772()" if last.group(1) else "is772()"
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

    text = re.sub(r"1\.21\.6", ph_sub("1.21.7"), text)
    text = re.sub(r"1\.21\.5", ph_sub("1.21.6"), text)
    text = re.sub(r"V1_21_6", ph_sub("V1_21_7"), text)
    text = re.sub(r"V1_21_5", ph_sub("V1_21_6"), text)
    text = re.sub(r"PROTOCOL_771", ph_sub("PROTOCOL_772"), text)
    text = re.sub(r"PROTOCOL_770", ph_sub("PROTOCOL_771"), text)
    holders.update(ph)
    # 単独の数字（16進リテラルは対象外）。まず 771 を保護してから
    # 770→771、保護分→772。
    text = re.sub(r"(?<!\d)771(?!\d)", "\x01771", text)
    text = re.sub(r"(?<!\d)770(?!\d)", "771", text)
    text = text.replace("\x01771", "772")
    return restore(text, holders)


def vv_sizes():
    """ViaVersion mapping から各バージョンの件数を引く。"""
    files = {"763": "vv-1.20.json", "764": "vv-1.20.2.json",
             "765": "vv-1.20.3.json", "766": "vv-1.20.5.json",
             "767": "vv-1.21.json", "768": "vv-1.21.2.json",
             "769": "vv-1.21.4.json", "770": "vv-1.21.5.json",
             "771": "vv-1.21.6.json", "772": "vv-1.21.7.json"}
    out = {}
    for tag, fname in files.items():
        data = json.loads((DATA_DIR / fname).read_text(encoding="utf-8"))
        out[tag] = {kind: len(data[kind]) for kind in
                    ("blockstates", "items", "entities", "sounds",
                     "particles", "blocks", "menus")}
    return out


def kinds_block(sizes):
    """KINDS タプル（772用）を生成する。"""
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
        for src in ("763", "764", "765", "766", "767", "768", "769", "770", "771"):
            counts.append(str(sizes[src][kind]))
            # 第2列は「新しい側（772）」の件数。771 の件数ではない。
            # （771 では新側が 771 だったので sizes["771"] で正しかったが、
            #  772 ではアイテム 1415→1416・音 1727→1728 と増えるため、
            #  sizes["771"] のままだと合成下向き表の expand サイズが 1 足りず、
            #  772 の末尾 ID が代替先に落ちる。RegistryRemapper772Test
            #  composed770To772AndBack で検出して修正した）。
            counts.append(str(sizes["772"][kind]))
        if prefix == "PARTICLE":
            fbs = ["AMBIENT_PARTICLE_763", "AMBIENT_PARTICLE_764",
                   "ENTITY_EFFECT_PARTICLE_765", "ENTITY_EFFECT_PARTICLE_766",
                   "ENTITY_EFFECT_PARTICLE_767", "ENTITY_EFFECT_PARTICLE_768",
                   "ENTITY_EFFECT_PARTICLE_769", "ENTITY_EFFECT_PARTICLE_770",
                   "ENTITY_EFFECT_PARTICLE_771", "ENTITY_EFFECT_PARTICLE_772"]
        else:
            fbs = [f"{fb_base}_{v}" for v in
                   ("763", "764", "765", "766", "767", "768", "769", "770", "771", "772")]
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
#        763→772 の件数, 772→763 の件数, 764→772 の件数, 772→764 の件数,
#        765→772 の件数, 772→765 の件数, 766→772 の件数, 772→766 の件数,
#        767→772 の件数, 772→767 の件数, 768→772 の件数, 772→768 の件数,
#        769→772 の件数, 772→769 の件数, 770→772 の件数, 772→770 の件数,
#        771→772 の件数, 772→771 の件数,
#        763 側の代替先, 764 側の代替先, 765 側の代替先, 766 側の代替先,
#        767 側の代替先, 768 側の代替先, 769 側の代替先, 770 側の代替先,
#        771 側の代替先, 772 側の代替先)
'''
    text = text[:start] + kinds_header + kinds_block(sizes) + text[end:]
    OUT.write_text(text, encoding="utf-8")
    print(f"insert-772-java.py を書き出した（KINDS のみ置き換え済み）。")
    print("sizes:", {k: sizes[k] for k in ("770", "771", "772")})
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
