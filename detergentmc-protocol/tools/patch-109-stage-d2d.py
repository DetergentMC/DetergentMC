#!/usr/bin/env python3
"""109 Stage D2 の追補3：ResourcePackServer に「109 パック無し」の
BuiltPack 便利コンストラクタを追加する。

フルコンストラクタ（built, built210, built110, built109, built315,
...）の定義を写し、built109 パラメータを除いて委譲側は 109
スロットへ null, null を渡す形にする（他の「〜無し」オーバーロード
と同じ流儀）。ResourcePackServer109Test のフォールバック検証が使う。
ついでにフルコンストラクタの javadoc の総数を thirty-eight に直す。
"""

import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent.parent
SERVER = ROOT / "detergentmc-protocol/src/main/java/io/detergentmc/protocol/ResourcePackServer.java"

text = SERVER.read_text(encoding="utf-8")
sig = ("    public ResourcePackServer(ResourcePackBuilder.BuiltPack built,\n"
       "            ResourcePackBuilder.BuiltPack built210,\n"
       "            ResourcePackBuilder.BuiltPack built110,\n"
       "            ResourcePackBuilder.BuiltPack built109,\n")
assert text.count(sig) == 1
start = text.index(sig)
jstart = text.rindex("    /** Convenience: serve all thirty-seven packs", 0, start)
end = text.index("\n    }\n", start) + len("\n    }\n")
full = text[jstart:end]

new = full.replace(
    "    /** Convenience: serve all thirty-seven packs, with an explicit prompt key. */",
    "    /** Convenience without a 1.9.2 pack (109 clients fall back to the 1.9.4 pack). */")
new = new.replace(
    "            ResourcePackBuilder.BuiltPack built109,\n", "")
new = new.replace(
    "built109.file(), built109.sha1(), ", "null, null, ")
assert "built109" not in new, new[:400]
full_fixed = full.replace("serve all thirty-seven packs", "serve all thirty-eight packs")
text = text[:jstart] + full_fixed + "\n\n" + new + text[end:]
SERVER.write_text(text, encoding="utf-8")
print("109 無し BuiltPack コンストラクタを追加した")
