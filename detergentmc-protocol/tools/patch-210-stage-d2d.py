#!/usr/bin/env python3
"""210 Stage D2 の追補3：ResourcePackServer に「210 パック無し」の
BuiltPack 便利コンストラクタを追加する。

フルコンストラクタ（built, built210, built315, ...）の定義を写し、
built210 パラメータを除いて委譲側は 210 スロットへ null, null を
渡す形にする（他の「〜無し」オーバーロードと同じ流儀）。
ResourcePackServer210Test のフォールバック検証が使う。
"""

import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent.parent
SERVER = ROOT / "detergentmc-protocol/src/main/java/io/detergentmc/protocol/ResourcePackServer.java"

text = SERVER.read_text(encoding="utf-8")
sig = ("    public ResourcePackServer(ResourcePackBuilder.BuiltPack built,\n"
       "            ResourcePackBuilder.BuiltPack built210,\n")
assert text.count(sig) == 1
start = text.index(sig)
# 直前の javadoc を含める
jstart = text.rindex("    /** Convenience: serve all thirty-five packs", 0, start)
end = text.index("\n    }\n", start) + len("\n    }\n")
full = text[jstart:end]

new = full.replace(
    "    /** Convenience: serve all thirty-five packs, with an explicit prompt key. */",
    "    /** Convenience without a 1.10.2 pack (210 clients fall back to the 1.11 pack). */")
new = new.replace(
    "            ResourcePackBuilder.BuiltPack built210,\n", "")
new = new.replace(
    "built210.file(), built210.sha1(), ", "null, null, ")
assert "built210" not in new, new[:400]
text = text[:end] + "\n\n" + new + text[end:]
SERVER.write_text(text, encoding="utf-8")
print("210 無し BuiltPack コンストラクタを追加した")
