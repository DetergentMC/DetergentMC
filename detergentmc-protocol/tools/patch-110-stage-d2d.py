#!/usr/bin/env python3
"""110 Stage D2 の追補3：ResourcePackServer に「110 パック無し」の
BuiltPack 便利コンストラクタを追加する。

フルコンストラクタ（built, built210, built110, built315, ...）の
定義を写し、built110 パラメータを除いて委譲側は 110 スロットへ
null, null を渡す形にする（他の「〜無し」オーバーロードと同じ
流儀）。ResourcePackServer110Test のフォールバック検証が使う。
"""

import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent.parent
SERVER = ROOT / "detergentmc-protocol/src/main/java/io/detergentmc/protocol/ResourcePackServer.java"

text = SERVER.read_text(encoding="utf-8")
sig = ("    public ResourcePackServer(ResourcePackBuilder.BuiltPack built,\n"
       "            ResourcePackBuilder.BuiltPack built210,\n"
       "            ResourcePackBuilder.BuiltPack built110,\n")
assert text.count(sig) == 1
start = text.index(sig)
jstart = text.rindex("    /** Convenience: serve all thirty-seven packs", 0, start)
end = text.index("\n    }\n", start) + len("\n    }\n")
full = text[jstart:end]

new = full.replace(
    "    /** Convenience: serve all thirty-seven packs, with an explicit prompt key. */",
    "    /** Convenience without a 1.9.4 pack (110 clients fall back to the 1.10.2 pack). */")
new = new.replace(
    "            ResourcePackBuilder.BuiltPack built110,\n", "")
new = new.replace(
    "built110.file(), built110.sha1(), ", "null, null, ")
assert "built110" not in new, new[:400]
text = text[:end] + "\n\n" + new + text[end:]
SERVER.write_text(text, encoding="utf-8")
print("110 無し BuiltPack コンストラクタを追加した")
