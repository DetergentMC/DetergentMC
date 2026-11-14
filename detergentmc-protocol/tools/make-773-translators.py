#!/usr/bin/env python3
"""gen-772-translators.py から gen-773-translators.py を生成する。

gen-773-proxysession.py の shift() で1段ずらす。shift() は ProxySession 向けだが、
translators 生成器のバージョン番号・メソッド名・コメントも同じ規則でずれる。
ただし下記は shift では正しくならないため、生成後に手で直すこと：
- KIND_769_TO_771（771節の KIND 表の履歴値）は 772 節では恒等表（96件）なので、
  shift した KIND_770_TO_772 は誤り。772節の KIND 表（恒等）を old として使う。
- PLAY_772_SECTION（手書き）は 773 用に書き直す。
- main() の抽出マーカー・ガード・フィルタ修正は 773 用に直す。
"""
import importlib.util
import re
import sys
from pathlib import Path

_HERE = Path(__file__).resolve().parent
_spec = importlib.util.spec_from_file_location("gen773ps", _HERE / "gen-773-proxysession.py")
_mod = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(_mod)
shift = _mod.shift

SRC = _HERE / "gen-772-translators.py"
OUT = _HERE / "gen-773-translators.py"

src = SRC.read_text(encoding="utf-8")
# import 先だけ先に直す（shift はファイル名の 772 を 773 にしないため）。
src = src.replace("gen-772-proxysession.py", "gen-773-proxysession.py")
src = src.replace('"gen772ps"', '"gen773ps"')
out = shift(src)
OUT.write_text(out, encoding="utf-8")
print(f"wrote {OUT} ({len(out)} chars)", file=sys.stderr)
print("WARNINGS は gen-773-proxysession.py の shift() が出したもの（stderr を参照）", file=sys.stderr)
