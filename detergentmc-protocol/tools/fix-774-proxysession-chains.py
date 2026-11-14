#!/usr/bin/env python3
"""patch-proxysession-774.py の連鎖の潰れと段数を直す。

gen-774-proxysession.py の shift は多段連鎖の途中の段を落とす系統的欠陥があり、
771→772→773 を 771→773 に潰す（FIX_EDITS／FIX_BLOCKS で直した分以外に、
CONFIG ハンドラの Registry Data 呼び出しに 10件残る）。また段数のコメントは
shift が1段増やすのを追わないため、dispatch と CONFIG ハンドラで 13件ずれる。

このスクリプトは生成パッチ（patch-proxysession-774.py）に直接適用する。
生成器（gen-774-proxysession.py）自体は触らない。
"""
import sys
from pathlib import Path

SRC = Path("/home/hatch/workspace/detergentmc/detergentmc-protocol/tools/patch-proxysession-774.py")

text = SRC.read_text(encoding="utf-8")
failed = []


def rep(old, new, label, count=1):
    global text
    n = text.count(old)
    if n != count:
        failed.append(f"{label}: 一致 {n} 件 (期待 {count} 件): {old[:70]!r}")
        return
    text = text.replace(old, new, count)


# --- 1) 上向き Registry Data：771→773 の潰れ（変数形、4件） ---
rep(
    "                byte[] payload771to773 =\n"
    "                        PlayTranslator.translateRegistryData771To773(payload770to771);\n"
    "                yield Outcome.toClient(new MinecraftPacket(0x07,\n"
    "                        PlayTranslator.translateRegistryData773To774(payload771to773)))",
    "                byte[] payload771to772 =\n"
    "                        PlayTranslator.translateRegistryData771To772(payload770to771);\n"
    "                byte[] payload772to773 =\n"
    "                        PlayTranslator.translateRegistryData772To773(payload771to772);\n"
    "                yield Outcome.toClient(new MinecraftPacket(0x07,\n"
    "                        PlayTranslator.translateRegistryData773To774(payload772to773)))",
    "上向き変数形 771→773 の展開", count=4)

# --- 2) 上向き Registry Data：771→773 の潰れ（ネスト形、1件） ---
# ネスト全体を置き換える（内側の行も1段深くし、閉じ括弧を1つ増やす）。
rep(
    'yield Outcome.toClient(new MinecraftPacket(0x07,\n                        PlayTranslator.translateRegistryData773To774(\n                                PlayTranslator.translateRegistryData771To773(\n                                        PlayTranslator.translateRegistryData770To771(\n                                                PlayTranslator.translateRegistryData769To770(\n                                                        PlayTranslator.translateRegistryData768To769(packet.payload())))))));',
    'yield Outcome.toClient(new MinecraftPacket(0x07,\n                        PlayTranslator.translateRegistryData773To774(\n                                PlayTranslator.translateRegistryData772To773(\n                                        PlayTranslator.translateRegistryData771To772(\n                                                PlayTranslator.translateRegistryData770To771(\n                                                        PlayTranslator.translateRegistryData769To770(\n                                                                PlayTranslator.translateRegistryData768To769(packet.payload()))))))));',
    "上向きネスト形 771→773 の展開")

# --- 3) 下向き Registry Data：773→771 の潰れ（if 付き、4件） ---
rep(
    "                byte[] payload773to771 =\n"
    "                        PlayTranslator.translateRegistryData773To771(payload774to773);\n"
    "                if (payload773to771 == null) {\n"
    "                    yield Outcome.drop();\n"
    "                }\n"
    "                byte[] payload771to770 =\n"
    "                        PlayTranslator.translateRegistryData771To770(payload773to771);",
    "                byte[] payload773to772 =\n"
    "                        PlayTranslator.translateRegistryData773To772(payload774to773);\n"
    "                if (payload773to772 == null) {\n"
    "                    yield Outcome.drop();\n"
    "                }\n"
    "                byte[] payload772to771 =\n"
    "                        PlayTranslator.translateRegistryData772To771(payload773to772);\n"
    "                if (payload772to771 == null) {\n"
    "                    yield Outcome.drop();\n"
    "                }\n"
    "                byte[] payload771to770 =\n"
    "                        PlayTranslator.translateRegistryData771To770(payload772to771);",
    "下向き変数形 773→771 の展開（if 付き）", count=4)

# --- 4) 下向き Registry Data：773→771 の潰れ（if 無し、1件） ---
rep(
    "                byte[] payload773to771 =\n"
    "                        PlayTranslator.translateRegistryData773To771(payload774to773);\n"
    "                byte[] payload771to770 =\n"
    "                        PlayTranslator.translateRegistryData771To770(payload773to771);",
    "                byte[] payload773to772 =\n"
    "                        PlayTranslator.translateRegistryData773To772(payload774to773);\n"
    "                byte[] payload772to771 =\n"
    "                        PlayTranslator.translateRegistryData772To771(payload773to772);\n"
    "                byte[] payload771to770 =\n"
    "                        PlayTranslator.translateRegistryData771To770(payload772to771);",
    "下向き変数形 773→771 の展開（if 無し）")

# --- 5) dispatch コメントの段数（七段／六段 → 八段／七段） ---
# NOTE：gen-774 の FIX_EDITS「handleServerPlayFrom766 773 branch」が既に
# 直しているため、ここでは繰り返さない（再実行で 0 件になる）。

# (6)(7) は「で変換する」／括弧付きのため正規表現の対象外。明示的に直す。
rep("（765→767→768→769→770→771→772→773→774へ六段）",
    "（765→767→768→769→770→771→772→773→774へ七段）",
    "CONFIG javadoc (6) の段数")
rep("Registry Data だけ774→773→772→771→770→769→768→767→765へ六段で変換する",
    "Registry Data だけ774→773→772→771→770→769→768→767→765へ七段で変換する",
    "CONFIG javadoc (7) の段数")

# --- 6) CONFIG ハンドラ javadoc の段数（中間版数＝段数-1 の慣習で1段足す） ---
# 「Registry Data だけXXX→774へN段で並べ替え」の N を1つ増やす。
DAN = {"二段": "三段", "三段": "四段", "四段": "五段", "五段": "六段", "六段": "七段"}
import re
pat = re.compile(r"(Registry Data だけ(?:774→773→772→771→770→769→768→767(?:→765|→766)?|765→767→768→769→770→771→772→773→774|766→767→768→769→770→771→772→773→774|767→768→769→770→771→772→773→774|768→769→770→771→772→773→774|769→770→771→772→773→774|770→771→772→773→774|774→773→772→771→770→769|774→773→772→771→770→769→768)へ)(二段|三段|四段|五段|六段)(で並べ替え)")
found = pat.findall(text)
if len(found) != 9:
    failed.append(f"CONFIG javadoc の段数：一致 {len(found)} 件 (期待 9 件)")
else:
    text = pat.sub(lambda m: m.group(1) + DAN[m.group(2)] + m.group(3), text)

if failed:
    print("失敗:", file=sys.stderr)
    for f in failed:
        print(" -", f, file=sys.stderr)
    sys.exit(1)

SRC.write_text(text, encoding="utf-8")
print("patch-proxysession-774.py の連鎖の潰れ10件と段数13件を修正した。")
