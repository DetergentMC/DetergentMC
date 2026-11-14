#!/usr/bin/env python3
"""gen-774-translators.py から gen-775-translators.py を生成する。

gen-775-proxysession.py の shift() で1段ずらす。shift() は ProxySession 向けだが、
translators 生成器のバージョン番号・メソッド名・コメントも同じ規則でずれる。
ただし下記は shift では正しくならないため、生成後に手で直すこと：
- KIND_773_TO_774（774節の KIND 表）は 774→775 の写像ではない。774 の成分種別は
  104 件、775 は 110 件（新規 6 件：additional_trade_cost 41、dye 43、
  pig/sound_variant 94、cow/sound_variant 96、chicken/sound_variant 98、
  cat/sound_variant 106）。KIND 表は 774→775 の写像（新規 6 件は -1）に書き直す。
  773→774 の 8 件挿入（use_effects 5 等）を持ち越してはいけない。
- PLAY_774_SECTION（手書き）は 775 用に書き直す。774→775 では Update Time・
  Use Entity・Enchant Item・Advancements の形式が変わるため、774節の素通しを
  そのまま写してはいけない。Registry Data は enchantment／damage_type／
  jukebox／絵画が同一のため並べ替えは素通しだが、jukebox_song／dialog の
  フィルタと targetNames に 775（V26_1）を足す。
- main() の抽出は extract_section_to_eof ではいけない。774節の後ろに
  762／761／760／759節があるため、extract_section で 774節だけを抜く。
- main() のマーカー・ガード・フィルタ修正は 775 用に直す。
- ProxySession の設定フェーズ 6 ハンドラ（775From773／772／771、
  772／771／770From775）は gen-775-proxysession.py では生成されないため、
  この生成器が PROXYSESSION_CONFIG_SECTION として手書きで追加する。
"""
import importlib.util
import sys
from pathlib import Path

_HERE = Path(__file__).resolve().parent
_spec = importlib.util.spec_from_file_location("gen775ps", _HERE / "gen-775-proxysession.py")
_mod = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(_mod)
shift = _mod.shift

SRC = _HERE / "gen-774-translators.py"
OUT = _HERE / "gen-775-translators.py"

src = SRC.read_text(encoding="utf-8")
# import 先だけ先に直す（shift はファイル名の 774 を 775 にしないため）。
src = src.replace("gen-774-proxysession.py", "gen-775-proxysession.py")
src = src.replace('"gen774ps"', '"gen775ps"')
out = shift(src)
OUT.write_text(out, encoding="utf-8")
print(f"wrote {OUT} ({len(out)} chars)", file=sys.stderr)
print("WARNINGS は gen-775-proxysession.py の shift() が出したもの（stderr を参照）", file=sys.stderr)
