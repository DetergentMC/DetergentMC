#!/usr/bin/env python3
"""315 Stage D2 の追補3：ResourcePackServer に「315 パック無し」の
BuiltPack 便利コンストラクタを追加する。

フルコンストラクタ（built, built315, built316, ...）の定義を写し、
built315 パラメータを除いて委譲側は 315 スロットへ null, null を
渡す形にする（他の「〜無し」オーバーロードと同じ流儀）。
ResourcePackServer315Test のフォールバック検証が使う。
"""

import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent.parent
SERVER = ROOT / "detergentmc-protocol/src/main/java/io/detergentmc/protocol/ResourcePackServer.java"

text = SERVER.read_text(encoding="utf-8")
sig = ("    public ResourcePackServer(ResourcePackBuilder.BuiltPack built,\n"
       "            ResourcePackBuilder.BuiltPack built315,\n")
assert text.count(sig) == 1
start = text.index(sig)
# 直前の javadoc を含める
jstart = text.rindex("    /** Convenience: serve all thirty-five packs", 0, start)
end = text.index("\n    }\n", start) + len("\n    }\n")
full = text[jstart:end]

new = full.replace(
    "    /** Convenience: serve all thirty-five packs, with an explicit prompt key. */",
    "    /** Convenience without a 1.11 pack (315 clients fall back to the 1.11.2 pack). */")
new = new.replace(
    "            ResourcePackBuilder.BuiltPack built315,\n", "")
new = new.replace(
    "built315.file(), built315.sha1(), ", "null, null, ")
assert "built315" not in new, new[:400]
text = text[:end] + "\n\n" + new + text[end:]
SERVER.write_text(text, encoding="utf-8")
print("315 無し BuiltPack コンストラクタを追加した")
