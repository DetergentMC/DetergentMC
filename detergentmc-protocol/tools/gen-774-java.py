#!/usr/bin/env python3
"""insert-773-java.py から insert-774-java.py を生成する。

方針: gen-773-java.py の shift() と同じ「1段ずらす」考え方で
insert-773-java.py を 774 用にずらし、構造の変わる部分だけ FIXES で
直す。ずらしはプレースホルダで保護して二重シフトを防ぐ。

ずらすもの（773→774、772→773）：
- 単独の数字 772／773（16進リテラルは対象外。KINDS の件数は後で
  FIXES で置き換える）、PROTOCOL_772／773、V1_21_7／V1_21_9、
  「1.21.7」／「1.21.8」／「1.21.9」（プレースホルダ経由で一度に
  ずらすと二重シフトを防げる）。
- 矢印チェーン（→）・⇔・／リスト・・リスト：gen-773-java.py
  の規則を 773 を軸に適用する。ただし 2要素ペアは意味で分ける：
  隣接ペア（772→773／773→772）は両端を +1、合成ペア
  （763→773／773→763 など）は対象側（773）だけ 774 に、
  772 を含むペア（767→772 など）は 772 だけ 773 にする。
  前の隣接ペア（770→772／772→770）も両端を +1 する。
- is773() チェーンには is774() を追加する。

FIXES で直すもの（insert-774-java.py 生成後に別スクリプトで直す）：
- 歴史的事実のコメント：「771で削除された」「771で改名」などは
  774 でも 771 のままのため、ずらしで 772 になったものを戻す。
  （構造の 771→772 は正しいので、コメントだけ戻す）。
- KINDS：件数を 774 用に置き換え、772／773 の組と 773／774 の
  代替先を追加（タプルは件数 22＋代替先 12＝34 要素になる）。
- CONSTANTS_MAX：MAX_ITEM_774=1504、MAX_ENTITY_TYPE_774=156、
  MAX_SOUND_774=1837、MAX_EFFECT_774=39、MAX_ENCHANTMENT_774=42 に。
- CONSTANTS_FALLBACK：PIG_ENTITY_TYPE_774=100、GENERIC_SOUND_774=1535 に。
- CONSTANTS_BLOCK_ENTITY：値は 773 と同一（49 件・shelf 40・statue 48）。
  コメントだけ直す。
- adjacent_maps()：kind[33] → kind[36]（774 の代替先）。
  ITEM_REVERSE_774_MAP の expand サイズ 1488 → 1505。
- composed_maps()：pairs を 772 の組まで拡張し、代替先の位置を
  新しいタプルに合わせる（上向き 36、相手側は 763=25〜772=34）。
  771 の組は新規。
- up_maps()：補正は無し（774 はプロパティ変更が無い）。代替先は
  773 側の定数 kind[35]。
- adjacent_accessors()：774にしか無いアイテム17件（スポーンエッグ4・
  馬鎧1・槍7・ノーチラス鎧5）の記述。ブロックエンティティ種別は
  773 と同一のため恒等写像に直す（ずらしの +1 は誤り）。
  エンチャントは lunge が 21 に挿入され 21 以降が +1（「並びは同一」
  は誤り）。エフェクトは 773→774 が恒等（39 は 773 に無い）。
- up_accessors()：エフェクト 39（BreathOfTheNautilus）は 773 に
  無いため Speed へ落とす（ずらしの素通しは誤り）。ブロックエンティティ
  種別は恒等写像に直す。音は新音 67 件の記述。
- composed_accessors()：pairs を 772 まで拡張。772 の組は新規に書く
  （エフェクトは 39 を Speed へ、音は SOUND_NAME_FALLBACK_772_TO_774_MAP、
  ブロックエンティティは 772→773（棚の挿入）と 773→774（恒等）の連鎖）。
  766〜771 の組のエフェクト下向きも 39 を Speed へ直す。
  音の名前の連鎖は 772_MAP の段階を戻す（ずらしで 772_MAP が 773_MAP に
  置き換わって消えるため）。763 の組は 768_MAP＋770_MAP＋771_MAP＋
  772_MAP＋773_MAP＋774_MAP の 6 段にする。
- small_registry_accessors()／damage_accessors()：ループを
  763〜773 まで拡張し、経由先を 773→774 にする。絵画・ジュークボックスの
  歌・バナー・トリムは 773 と同一のため恒等写像のまま。
  ダメージ種別は spear が 37 に挿入され 50 件になるため恒等写像ではない
  （DAMAGE_TYPE_774_MAP／REVERSE_774_MAP は手書きの表）。
- JAVADOC_773 → JAVADOC_774：差分を 774 用に書き換える。
- main()：ファイル名・チェックマーカー・アンカーを 774 用にする。
  合成表のアンカーは MENU_773_TO_771_MAP（773 の合成表の最後）に
  直す。音の名前のアンカーは SOUND_NAME_FALLBACK_766_TO_773_MAP に
  直す。アクセサ（a）のアンカーは mapEnchantment773To764（773 の
  隣接アクセサの最後）に直す。ディスパッチのコメント置き換えは旧文
  （772 と 773 を含むもの）を正しく指定する。
"""
import json
import re
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
SRC = HERE / "insert-773-java.py"
OUT = HERE / "insert-774-java.py"
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
IS_CHAIN = re.compile(r"((?:is(?:Server)?\d{3}\(\)\s*\|\|\s*)+is(?:Server)?773\(\))")


def shift_pair(m, sep):
    """2要素ペアのずらし。意味で分ける。"""
    a, b = m.group(0).split(sep)
    if (a, b) in (("772", "773"), ("773", "772"),
                  ("771", "772"), ("772", "771")):
        return sep.join(str(int(n) + 1) for n in (a, b))
    if b == "773":
        return a + sep + "774"
    if a == "773":
        return "774" + sep + b
    if b == "772":
        return a + sep + "773"
    if a == "772":
        return "773" + sep + b
    # 771／772 を含まないペアはそのまま（例：766→767 は 773 でも同じ）。
    return m.group(0)


def shift_chain(m, kind):
    sep = "→" if kind == "arrow" else "⇔"
    nums = m.group(0).split(sep)
    if len(nums) == 2:
        return shift_pair(m, sep)
    if len(nums) >= 3 and nums[0] == "773":
        return "774" + sep + m.group(0)
    if nums[-1] == "773":
        return m.group(0) + sep + "774"
    if nums[-1] == "772":
        return m.group(0) + sep + "773"
    return m.group(0)


def shift_list(m, sep):
    nums = m.group(0).split(sep)
    if nums[-1] == "773":
        return m.group(0) + sep + "774"
    if nums[-1] == "772":
        return m.group(0) + sep + "773"
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
        suffix = "isServer774()" if last.group(1) else "is774()"
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

    text = re.sub(r"1\.21\.9", ph_sub("1.21.11"), text)
    text = re.sub(r"1\.21\.8", ph_sub("1.21.9"), text)
    text = re.sub(r"1\.21\.7", ph_sub("1.21.9"), text)
    text = re.sub(r"1\.21\.6", ph_sub("1.21.7"), text)
    text = re.sub(r"V1_21_9", ph_sub("V1_21_11"), text)
    text = re.sub(r"V1_21_7", ph_sub("V1_21_9"), text)
    text = re.sub(r"PROTOCOL_773", ph_sub("PROTOCOL_774"), text)
    text = re.sub(r"PROTOCOL_772", ph_sub("PROTOCOL_773"), text)
    holders.update(ph)
    # 単独の数字（16進リテラルは対象外）。まず 773 を保護してから
    # 772→773、771→772、保護分→774。771 もずらすのは、合成の相手
    # （771→773）が 772→774 になるため。ただし「771で削除された」
    # のような歴史的事実のコメントも一緒にずれてしまうため、生成後に
    # 戻すこと（FIXES を参照）。
    text = re.sub(r"(?<!\d)773(?!\d)", "\x01773", text)
    text = re.sub(r"(?<!\d)772(?!\d)", "773", text)
    text = re.sub(r"(?<!\d)771(?!\d)", "772", text)
    text = text.replace("\x01773", "774")
    return restore(text, holders)


def vv_sizes():
    """ViaVersion mapping から各バージョンの件数を引く。"""
    files = {"763": "vv-1.20.json", "764": "vv-1.20.2.json",
             "765": "vv-1.20.3.json", "766": "vv-1.20.5.json",
             "767": "vv-1.21.json", "768": "vv-1.21.2.json",
             "769": "vv-1.21.4.json", "770": "vv-1.21.5.json",
             "771": "vv-1.21.6.json", "772": "vv-1.21.7.json",
             "773": "vv-1.21.9.json", "774": "vv-1.21.11.json"}
    out = {}
    for tag, fname in files.items():
        data = json.loads((DATA_DIR / fname).read_text(encoding="utf-8"))
        out[tag] = {kind: len(data[kind]) for kind in
                    ("blockstates", "items", "entities", "sounds",
                     "particles", "blocks", "menus")}
    return out


def kinds_block(sizes):
    """KINDS タプル（774用）を生成する。"""
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
    # 772 の KINDS と同じ並びにする。
    for name, prefix, jp, kind, fb_base in specs:
        counts = []
        for src in ("763", "764", "765", "766", "767", "768", "769", "770", "771", "772", "773"):
            counts.append(str(sizes[src][kind]))
            # 第2列は「新しい側（774）」の件数。773 の件数ではない。
            # （773 では新側が 773 だったので sizes["773"] で正しかったが、
            #  774 ではアイテム 1488→1505・音 1771→1838 と増えるため、
            #  sizes["773"] のままだと合成下向き表の expand サイズが足りず、
            #  774 の末尾 ID が代替先に落ちる。773 で同じ罠を踏んで
            #  RegistryRemapper773Test で検出して修正した）。
            counts.append(str(sizes["774"][kind]))
        if prefix == "PARTICLE":
            fbs = ["AMBIENT_PARTICLE_763", "AMBIENT_PARTICLE_764",
                   "ENTITY_EFFECT_PARTICLE_765", "ENTITY_EFFECT_PARTICLE_766",
                   "ENTITY_EFFECT_PARTICLE_767", "ENTITY_EFFECT_PARTICLE_768",
                   "ENTITY_EFFECT_PARTICLE_769", "ENTITY_EFFECT_PARTICLE_770",
                   "ENTITY_EFFECT_PARTICLE_771",
                   "ENTITY_EFFECT_PARTICLE_772", "ENTITY_EFFECT_PARTICLE_773",
                   "ENTITY_EFFECT_PARTICLE_774"]
        else:
            fbs = [f"{fb_base}_{v}" for v in
                   ("763", "764", "765", "766", "767", "768", "769", "770",
                    "771", "772", "773", "774")]
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
#        763→774 の件数, 774→763 の件数, 764→774 の件数, 774→764 の件数,
#        765→774 の件数, 774→765 の件数, 766→774 の件数, 774→766 の件数,
#        767→774 の件数, 774→767 の件数, 768→774 の件数, 774→768 の件数,
#        769→774 の件数, 774→769 の件数, 770→774 の件数, 774→770 の件数,
#        771→774 の件数, 774→771 の件数, 772→774 の件数, 774→772 の件数,
#        773→774 の件数, 774→773 の件数,
#        763 側の代替先, 764 側の代替先, 765 側の代替先, 766 側の代替先,
#        767 側の代替先, 768 側の代替先, 769 側の代替先, 770 側の代替先,
#        771 側の代替先, 772 側の代替先, 773 側の代替先, 774 側の代替先)
'''
    text = text[:start] + kinds_header + kinds_block(sizes) + text[end:]
    OUT.write_text(text, encoding="utf-8")
    print(f"insert-774-java.py を書き出した（KINDS のみ置き換え済み）。")
    print("sizes:", {k: sizes[k] for k in ("772", "773", "774")})
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
