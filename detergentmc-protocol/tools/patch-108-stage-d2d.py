#!/usr/bin/env python3
"""108 Stage D2 の追補3：ResourcePackServer に「108 パック無し」の
BuiltPack 便利コンストラクタを追加する。

フルコンストラクタ（built, built210, built110, built109, built108,
built315, ...）の定義を写し、built108 パラメータを除いて委譲側は
108 スロットへ null, null を渡す形にする（他の「〜無し」
オーバーロードと同じ流儀）。ResourcePackServer108Test の
フォールバック検証が使う。ついでにフルコンストラクタの
javadoc の総数を thirty-nine に直す。
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
       "            ResourcePackBuilder.BuiltPack built108,\n")
assert text.count(sig) == 1
start = text.index(sig)
jstart = text.rindex("    /** Convenience: serve all thirty-eight packs", 0, start)
end = text.index("\n    }\n", start) + len("\n    }\n")
full = text[jstart:end]

new = full.replace(
    "    /** Convenience: serve all thirty-eight packs, with an explicit prompt key. */",
    "    /** Convenience without a 1.9.1 pack (108 clients fall back to the 1.9.2 pack). */")
new = new.replace(
    "            ResourcePackBuilder.BuiltPack built108,\n", "")
new = new.replace(
    "built108.file(), built108.sha1(), ", "null, null, ")
assert "built108" not in new, new[:400]
full_fixed = full.replace("serve all thirty-eight packs", "serve all thirty-nine packs")
text = text[:jstart] + full_fixed + "\n\n" + new + text[end:]
SERVER.write_text(text, encoding="utf-8")
print("108 無し BuiltPack コンストラクタを追加した")
