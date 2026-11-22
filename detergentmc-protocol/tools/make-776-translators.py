#!/usr/bin/env python3
"""gen-775-translators.py から gen-776-translators.py を生成する。

gen-775-proxysession.py の shift() を1段ずらした shift776()
（tools/shift776.py。親が ~/workspace/tmp776/build-shift776.py で作った
ものをリポジトリに取り込んだ。12件の自己テスト付き）を流用する。
775のときの make-775-translators.py と同じ段取りだが、1点だけ違う：
gen-776-proxysession.py はまだ無いため、shift は gen-775-proxysession.py
から直接は取らず、shift776.py モジュールから取る。

shift776 では正しくならないため、生成後に gen-776-translators.py を
手で直すこと（make-775-translators.py の docstring と同じ注意）：
- KIND_774_TO_775（775節の KIND 表）は 775→776 の写像ではない。775 の成分種別は
  110 件、776 は 111 件（新規 1 件：sulfur_cube_content 78。jar ダンプ
  registry-26.2/data_component_type.txt と vv-26.2.json で確認）。
  KIND 表は 775→776 の写像（新規 1 件は -1）に書き直す。
  774→775 の 6 件挿入（additional_trade_cost 41 等）を持ち越してはいけない。
- PLAY_775_SECTION（手書き）は 776 用に書き直す。775→776 ではパケットIDも
  形式も変わらない（Update Time・Use Entity・Enchant Item は同一形式のため
  素通しにする。774→775 の作り直しを写してはいけない）。
  Registry Data は jukebox_song（bounce 追加で22曲）・damage_type
  （sulfur_cube_hot 追加で51件）の並べ替えが必要。enchantment は同一。
  jukebox_song／dialog のフィルタと targetNames に 776（V26_2）を足す。
- main() の抽出は extract_section ではなく extract_section_to_eof にする。
  775節はファイル末尾のため。774節の後ろにあった 762／761／760／759節は
  775節の後ろには無い。
- main() のマーカー・ガード・フィルタ修正は 776 用に直す。
- ProxySession の設定フェーズ 776 ハンドラ（776From775／775From776、
  776From774／773／…／766、772／771／…／766From776）は
  PROXYSESSION_CONFIG_SECTION として手書きで追加する。
  ただし ProxySession.java は別担当（A）が編集中のため、適用は
  親の「ProxySession is free」許可が出てから。gen-776-translators.py の
  main() は --skip-proxysession でその部分を飛ばせる。
- /tmp には書かない（再起動で消える）。デバッグ用の中間書き出しは削る。
"""
import importlib.util
import sys
from pathlib import Path

_HERE = Path(__file__).resolve().parent
_spec = importlib.util.spec_from_file_location("shift776", _HERE / "shift776.py")
_mod = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(_mod)
shift776 = _mod.shift776

SRC = _HERE / "gen-775-translators.py"
OUT = _HERE / "gen-776-translators.py"

src = SRC.read_text(encoding="utf-8")
# import 先だけ先に直す（shift776 はファイル名の 775 を 776 にしてしまうため）。
src = src.replace("gen-775-proxysession.py", "shift776.py")
src = src.replace('"gen775ps"', '"shift776"')
src = src.replace("shift = _mod.shift", "shift = _mod.shift776")
out = shift776(src)
OUT.write_text(out, encoding="utf-8")
print(f"wrote {OUT} ({len(out)} chars)", file=sys.stderr)
print("注意：生成直後の gen-776-translators.py は手直し前。KIND 表・"
      "PLAY_776_SECTION・PROXYSESSION_CONFIG_SECTION・main() を直すこと。",
      file=sys.stderr)
