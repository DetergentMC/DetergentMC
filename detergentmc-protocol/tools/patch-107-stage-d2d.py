#!/usr/bin/env python3
"""107 Stage D2 の追補3：ResourcePackServer に「107 パック無し」の
BuiltPack 便利コンストラクタを追加する。

フルコンストラクタ（built, built210, built110, built109, built108,
built107, built315, ...）の定義を写し、built107 パラメータを
除いて委譲側は 107 スロットへ null, null を渡す形にする（他の
「〜無し」オーバーロードと同じ流儀）。ResourcePackServer107Test
のフォールバック検証が使う。ついでにフルコンストラクタの
javadoc の総数を forty に直す。
"""

import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent.parent
SERVER = ROOT / "detergentmc-protocol/src/main/java/io/detergentmc/protocol/ResourcePackServer.java"

text = SERVER.read_text(encoding="utf-8")
sig = ("    public ResourcePackServer(ResourcePackBuilder.BuiltPack built,\n"
       "            ResourcePackBuilder.BuiltPack built210,\n"
       "            ResourcePackBuilder.BuiltPack built110,\n"
       "            ResourcePackBuilder.BuiltPack built109,\n"
       "            ResourcePackBuilder.BuiltPack built108,\n"
       "            ResourcePackBuilder.BuiltPack built107,\n")
assert text.count(sig) == 1
start = text.index(sig)
jstart = text.rindex("    /** Convenience: serve all thirty-nine packs", 0, start)
end = text.index("\n    }\n", start) + len("\n    }\n")
full = text[jstart:end]

new = full.replace(
    "    /** Convenience: serve all thirty-nine packs, with an explicit prompt key. */",
    "    /** Convenience without a 1.9 pack (107 clients fall back to the 1.9.1 pack). */")
new = new.replace(
    "            ResourcePackBuilder.BuiltPack built107,\n", "")
new = new.replace(
    "built107.file(), built107.sha1(), ", "null, null, ")
assert "built107" not in new, new[:400]
full_fixed = full.replace("serve all thirty-nine packs", "serve all forty packs")
text = text[:jstart] + full_fixed + "\n\n" + new + text[end:]
SERVER.write_text(text, encoding="utf-8")
print("107 無し BuiltPack コンストラクタを追加した")
