#!/usr/bin/env python3
"""insert-772-java.py から insert-773-java.py を生成する。

方針: gen-772-java.py の shift() と同じ「1段ずらす」考え方で
insert-772-java.py を 773 用にずらし、構造の変わる部分だけ FIXES で
直す。ずらしはプレースホルダで保護して二重シフトを防ぐ。

ずらすもの（772→773、771→772）：
- 単独の数字 771／772（16進リテラルは対象外。KINDS の件数は後で
  FIXES で置き換える）、PROTOCOL_771／772、V1_21_6／V1_21_7、
  「1.21.6」／「1.21.7」（プレースホルダ経由で一度にずらすと
  二重シフトを防げる）。
- 矢印チェーン（→）・⇔・／リスト・・リスト：gen-772-java.py
  の規則を 772 を軸に適用する。ただし 2要素ペアは意味で分ける：
  隣接ペア（771→772／772→771）は両端を +1、合成ペア
  （763→772／772→763 など）は対象側（772）だけ 773 に、
  771 を含むペア（767→771 など）は 771 だけ 772 にする。
  前の隣接ペア（769→771／771→769）も両端を +1 する。
- is772() チェーンには is773() を追加する。

FIXES で直すもの：
- KINDS：件数を 773 用に置き換え、771／772 の組と 772／773 の
  代替先を追加（タプルは件数 20＋代替先 11＝31 要素になる）。
- CONSTANTS_MAX／CONSTANTS_FALLBACK／CONSTANTS_BLOCK_ENTITY：
  773 の値に置き換える。
- adjacent_maps()：kind[30] → kind[33]（773 の代替先）。
- composed_maps()：pairs を 771／772 の組まで拡張し、代替先の
  位置を新しいタプルに合わせる。
- up_maps()：補正は無し（773 はプロパティ変更が無い）。ただしブロック
  エンティティ種別だけは棚の挿入（40）と銅ゴーレム像の追加（48）で
  手直しが要る。代替先は 772 側の定数 kind[32] をそのまま使う。
- adjacent_accessors()：コメントの「1件」→「73件」など 773 用に直す。
  属性は 772・773 で同一の 35 件、エフェクト・エンチャント・絵画・
  ジュークボックスの歌・バイオームは 772 と 773 で同一（恒等写像のまま）。
  ブロックエンティティ種別だけが変わる（shelf が 40 に挿入され 40 以降が
  +1、copper_golem_statue を 48 に追加して 49 件。隣接アクセサは
  ずらし＋DROP に直す）。
- up_accessors()：コメントの「19件」→「1件」、新音 1 件（レコード
  「ラヴァ・チキン」）の記述。絵画の逆向きもずらし＋DROP に直す。
- composed_accessors()：pairs を 771／772 まで拡張。771 の組は
  新規に書く（エフェクト恒等・音は SOUND_NAME_FALLBACK_771_TO_773_MAP。
  ブロックエンティティは 771→772（恒等）と 772→773（棚の挿入）の連鎖）。
  音の名前の連鎖は 771_MAP の段階を戻す（ずらしで 771_MAP が 772_MAP に
  置き換わって消えるため）。763 の組は 768_MAP＋770_MAP＋771_MAP＋
  772_MAP＋773_MAP の 5 段にする。
- small_registry_accessors()／damage_accessors()：ループを
  763〜772 まで拡張し、経由先を 772→773 にする。絵画・ジュークボックスの
  歌とも 772 と同一のため恒等写像のまま（772 で入れた dennis／lava_chicken
  の特別扱いは要らない）。
- ジュークボックスの曲と絵画：773 では 772 と同一（21 件・51 件）のため
  恒等写像のままで、手書きの対応は要らない（772 で入れた
  JUKEBOX_SONG_772_NAMES・mapJukeboxSong771To772／772To771・
  mapPaintingVariant771To772／772To771 は 772 用のまま残す）。
- JAVADOC_772 → JAVADOC_773：差分を 773 用に書き換える。
- main()：ファイル名・チェックマーカー・アンカーを 773 用にする。
  合成表のアンカーは MENU_772_TO_770_MAP（772 の合成表の最後）に
  直す。ディスパッチのコメント置き換えは旧文（771 と 772 を含む
  もの）を正しく指定する。
"""
import json
import re
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
SRC = HERE / "insert-772-java.py"
OUT = HERE / "insert-773-java.py"
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
IS_CHAIN = re.compile(r"((?:is(?:Server)?\d{3}\(\)\s*\|\|\s*)+is(?:Server)?772\(\))")


def shift_pair(m, sep):
    """2要素ペアのずらし。意味で分ける。"""
    a, b = m.group(0).split(sep)
    if (a, b) in (("771", "772"), ("772", "771"),
                  ("770", "771"), ("771", "770")):
        return sep.join(str(int(n) + 1) for n in (a, b))
    if b == "772":
        return a + sep + "773"
    if a == "772":
        return "773" + sep + b
    if b == "771":
        return a + sep + "772"
    if a == "771":
        return "772" + sep + b
    # 771／772 を含まないペアはそのまま（例：766→767 は 773 でも同じ）。
    return m.group(0)


def shift_chain(m, kind):
    sep = "→" if kind == "arrow" else "⇔"
    nums = m.group(0).split(sep)
    if len(nums) == 2:
        return shift_pair(m, sep)
    if len(nums) >= 3 and nums[0] == "772":
        return "773" + sep + m.group(0)
    if nums[-1] == "772":
        return m.group(0) + sep + "773"
    if nums[-1] == "771":
        return m.group(0) + sep + "772"
    return m.group(0)


def shift_list(m, sep):
    nums = m.group(0).split(sep)
    if nums[-1] == "772":
        return m.group(0) + sep + "773"
    if nums[-1] == "771":
        return m.group(0) + sep + "772"
    return m.group(0)


def shift(text):
    text, h0 = protect(text, PROTO_VER, lambda m: m.group(0))
    text, h1 = protect(text, ARROW, lambda m: shift_chain(m, "arrow"))
    text, h2 = protect(text, DBL, lambda m: shift_chain(m, "dbl"))
    text, h3 = protect(text, SLASH, lambda m: shift_list(m, "／"))
    text, h4 = protect(text, DOT, lambda m: shift_list(m, "・"))

    def is_chain_sub(m):
        chain = m.group(1)
        last = re.search(r"is(Server)?772\(\)$", chain)
        suffix = "isServer773()" if last.group(1) else "is773()"
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

    text = re.sub(r"1\.21\.8", ph_sub("1.21.9"), text)
    text = re.sub(r"1\.21\.7", ph_sub("1.21.9"), text)
    text = re.sub(r"1\.21\.6", ph_sub("1.21.7"), text)
    text = re.sub(r"1\.21\.5", ph_sub("1.21.6"), text)
    text = re.sub(r"V1_21_7", ph_sub("V1_21_9"), text)
    text = re.sub(r"V1_21_6", ph_sub("V1_21_7"), text)
    text = re.sub(r"PROTOCOL_772", ph_sub("PROTOCOL_773"), text)
    text = re.sub(r"PROTOCOL_771", ph_sub("PROTOCOL_772"), text)
    holders.update(ph)
    # 単独の数字（16進リテラルは対象外）。まず 772 を保護してから
    # 771→772、保護分→773。
    text = re.sub(r"(?<!\d)772(?!\d)", "\x01772", text)
    text = re.sub(r"(?<!\d)771(?!\d)", "772", text)
    text = text.replace("\x01772", "773")
    return restore(text, holders)


def vv_sizes():
    """ViaVersion mapping から各バージョンの件数を引く。"""
    files = {"763": "vv-1.20.json", "764": "vv-1.20.2.json",
             "765": "vv-1.20.3.json", "766": "vv-1.20.5.json",
             "767": "vv-1.21.json", "768": "vv-1.21.2.json",
             "769": "vv-1.21.4.json", "770": "vv-1.21.5.json",
             "771": "vv-1.21.6.json", "772": "vv-1.21.7.json",
             "773": "vv-1.21.9.json"}
    out = {}
    for tag, fname in files.items():
        data = json.loads((DATA_DIR / fname).read_text(encoding="utf-8"))
        out[tag] = {kind: len(data[kind]) for kind in
                    ("blockstates", "items", "entities", "sounds",
                     "particles", "blocks", "menus")}
    return out


def kinds_block(sizes):
    """KINDS タプル（773用）を生成する。"""
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
    # 771 の KINDS と同じ並びにする。
    for name, prefix, jp, kind, fb_base in specs:
        counts = []
        for src in ("763", "764", "765", "766", "767", "768", "769", "770", "771", "772"):
            counts.append(str(sizes[src][kind]))
            # 第2列は「新しい側（773）」の件数。772 の件数ではない。
            # （772 では新側が 772 だったので sizes["772"] で正しかったが、
            #  773 ではアイテム 1416→1488・音 1728→1771 と増えるため、
            #  sizes["772"] のままだと合成下向き表の expand サイズが 1 足りず、
            #  773 の末尾 ID が代替先に落ちる。RegistryRemapper773Test
            #  composed771To773AndBack で検出して修正した）。
            counts.append(str(sizes["773"][kind]))
        if prefix == "PARTICLE":
            fbs = ["AMBIENT_PARTICLE_763", "AMBIENT_PARTICLE_764",
                   "ENTITY_EFFECT_PARTICLE_765", "ENTITY_EFFECT_PARTICLE_766",
                   "ENTITY_EFFECT_PARTICLE_767", "ENTITY_EFFECT_PARTICLE_768",
                   "ENTITY_EFFECT_PARTICLE_769", "ENTITY_EFFECT_PARTICLE_770",
                   "ENTITY_EFFECT_PARTICLE_771",
                   "ENTITY_EFFECT_PARTICLE_772", "ENTITY_EFFECT_PARTICLE_773"]
        else:
            fbs = [f"{fb_base}_{v}" for v in
                   ("763", "764", "765", "766", "767", "768", "769", "770",
                    "771", "772", "773")]
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
#        763→773 の件数, 773→763 の件数, 764→773 の件数, 773→764 の件数,
#        765→773 の件数, 773→765 の件数, 766→773 の件数, 773→766 の件数,
#        767→773 の件数, 773→767 の件数, 768→773 の件数, 773→768 の件数,
#        769→773 の件数, 773→769 の件数, 770→773 の件数, 773→770 の件数,
#        771→773 の件数, 773→771 の件数, 772→773 の件数, 773→772 の件数,
#        763 側の代替先, 764 側の代替先, 765 側の代替先, 766 側の代替先,
#        767 側の代替先, 768 側の代替先, 769 側の代替先, 770 側の代替先,
#        771 側の代替先, 772 側の代替先, 773 側の代替先)
'''
    text = text[:start] + kinds_header + kinds_block(sizes) + text[end:]
    OUT.write_text(text, encoding="utf-8")
    print(f"insert-773-java.py を書き出した（KINDS のみ置き換え済み）。")
    print("sizes:", {k: sizes[k] for k in ("771", "772", "773")})
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
