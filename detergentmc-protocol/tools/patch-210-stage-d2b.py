#!/usr/bin/env python3
"""210 Stage D2 の追補：ResourcePackMode に pack210 無しの互換
コンストラクタを追加する。

patch-210-stage-d2.py のステップ3は、アンカー（315無しコンスト
ラクタの委譲末尾）がステップ2の canonical 委譲更新で
"null, null" に変わっていたため適用できなかった。本スクリプトは
その1件だけを正しいアンカーで適用する。d2 の再実行はしないこと。
"""

import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent.parent
MODE = ROOT / "detergentmc-protocol/src/main/java/io/detergentmc/protocol/ResourcePackMode.java"

text = MODE.read_text(encoding="utf-8")
anchor = "pack340, pack338, pack335, null, null);\n        }"
assert text.count(anchor) == 1, f"アンカーが一意でない: {text.count(anchor)}"
new_ctor = anchor + """

        /** Backwards-compatible constructor without a 1.10.2 pack. */
        public Decision(Kind kind, ResourcePack pack, String problem,
                ResourcePack pack758, ResourcePack pack759, ResourcePack pack760, ResourcePack pack761, ResourcePack pack762, ResourcePack pack763, ResourcePack pack764,
                ResourcePack pack766, ResourcePack pack767, ResourcePack pack768,
                ResourcePack pack769, ResourcePack pack770, ResourcePack pack771,
                ResourcePack pack772, ResourcePack pack773, ResourcePack pack774,
                ResourcePack pack775, ResourcePack pack776, ResourcePack pack578,
                ResourcePack pack575, ResourcePack pack573, ResourcePack pack498,
                ResourcePack pack490, ResourcePack pack485, ResourcePack pack480, ResourcePack pack477,
                ResourcePack pack404, ResourcePack pack401, ResourcePack pack393, ResourcePack pack340,
                ResourcePack pack338, ResourcePack pack335, ResourcePack pack315) {
            this(kind, pack, problem, pack758, pack759, pack760, pack761, pack762, pack763, pack764, pack766, pack767, pack768, pack769, pack770, pack771, pack772, pack773, pack774, pack775, pack776, pack578, pack575, pack573, pack498, pack490, pack485, pack480, pack477, pack404, pack401, pack393, pack340, pack338, pack335, pack315, null);
        }"""
MODE.write_text(text.replace(anchor, new_ctor), encoding="utf-8")
print("ResourcePackMode に 210 無し互換コンストラクタを追加した")
