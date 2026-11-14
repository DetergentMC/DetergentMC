#!/usr/bin/env python3
"""gen-773-translators.py から gen-774-translators.py を生成する。

gen-774-proxysession.py の shift() で1段ずらす。shift() は ProxySession 向けだが、
translators 生成器のバージョン番号・メソッド名・コメントも同じ規則でずれる。
ただし下記は shift では正しくならないため、生成後に手で直すこと：
- KIND_772_TO_773（773節の KIND 表の履歴値）は 773 節では恒等表（96件）なので、
  shift した KIND_773_TO_774 は誤り。773節の KIND 表（恒等）を old として使う。
  さらに 774 の成分種別は 104 件（8件の新規挿入）なので、KIND 表は恒等ではなく
  773→774 の写像（新規 8 件は -1）になる。
- PLAY_773_SECTION（手書き）は 774 用に書き直す。
- main() の抽出マーカー・ガード・フィルタ修正は 774 用に直す。
"""
import importlib.util
import sys
from pathlib import Path

_HERE = Path(__file__).resolve().parent
_spec = importlib.util.spec_from_file_location("gen774ps", _HERE / "gen-774-proxysession.py")
_mod = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(_mod)
shift = _mod.shift

SRC = _HERE / "gen-773-translators.py"
OUT = _HERE / "gen-774-translators.py"

src = SRC.read_text(encoding="utf-8")
# import 先だけ先に直す（shift はファイル名の 773 を 774 にしないため）。
src = src.replace("gen-773-proxysession.py", "gen-774-proxysession.py")
src = src.replace('"gen773ps"', '"gen774ps"')
out = shift(src)
OUT.write_text(out, encoding="utf-8")
print(f"wrote {OUT} ({len(out)} chars)", file=sys.stderr)
print("WARNINGS は gen-774-proxysession.py の shift() が出したもの（stderr を参照）", file=sys.stderr)
