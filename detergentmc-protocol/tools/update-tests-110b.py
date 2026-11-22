#!/usr/bin/env python3
"""110 Stage C の追補：update-tests-110.py の対象外だった既存テスト
（758〜776 世代と基底テスト）のピン留めを 110 追加に合わせて更新する。

- 全リストリテラル List.of(210, 315, ... は List.of(110, 210, ... へ
  （PacketIds758〜776Test、PacketIdsTest、ClientAdapterTest、
  ClientAdapter764Test など。210〜578 世代は適用済みで対象文字列が
  残っていないため二重適用にはならない）。
- ClientAdapter758〜776Test：supported.get(N) のピンを +1 し、
  get(0)=110 のピンを追加、OLDEST 210→110、件数 36→37。
- PacketIds393／401／404Test の PLAY_BED テスト：110（0x2F、210 と
  同値）を列挙へ追加する。
"""

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent.parent
TEST = ROOT / "detergentmc-protocol/src/test/java/io/detergentmc/protocol"

# 1. 全リストリテラルの先頭へ 110 を追加（全テストファイル対象）。
changed = 0
for path in sorted(TEST.glob("*Test.java")):
    text = path.read_text(encoding="utf-8")
    if "List.of(210, 315, 316," in text:
        text = text.replace("List.of(210, 315, 316,", "List.of(110, 210, 315, 316,")
        path.write_text(text, encoding="utf-8")
        changed += 1
        print(f"{path.name}: リスト先頭へ 110 を追加")
print(f"リスト更新 {changed} ファイル")

# 2. ClientAdapter758〜776Test のピンずらし。
for ver in ["758", "759", "760", "761", "762", "764", "766", "767", "768",
            "769", "770", "771", "772", "773", "774", "775", "776"]:
    path = TEST / f"ClientAdapter{ver}Test.java"
    if not path.exists():
        continue
    text = path.read_text(encoding="utf-8")
    orig = text
    text = re.sub(r"\.get\((\d+)\)",
                  lambda m: f".get({int(m.group(1)) + 1})", text)
    if text != orig:
        # get(1) になった最初のピンの直前に get(0)=110 のピンを入れる。
        m = re.search(r"(\n\s*)assertEquals\(315, supported\.get\(1\)", text)
        if m:
            indent = m.group(1)
            text = text.replace(m.group(0),
                                f"{indent}assertEquals(110, supported.get(0), supported.toString());"
                                + m.group(0), 1)
    text = text.replace(
        "assertEquals(210, ClientAdapter.OLDEST_SUPPORTED_CLIENT_PROTOCOL);",
        "assertEquals(110, ClientAdapter.OLDEST_SUPPORTED_CLIENT_PROTOCOL);")
    text = text.replace(
        "assertEquals(ProtocolVersion.V1_10_2, ClientAdapter.OLDEST_SUPPORTED_CLIENT_PROTOCOL);",
        "assertEquals(ProtocolVersion.V1_9_4, ClientAdapter.OLDEST_SUPPORTED_CLIENT_PROTOCOL);")
    text = text.replace(
        "assertEquals(36, ClientAdapter.supportedClientProtocols().size());",
        "assertEquals(37, ClientAdapter.supportedClientProtocols().size());")
    text = text.replace(
        "assertTrue(supported.contains(210));",
        "assertTrue(supported.contains(110));\n        assertTrue(supported.contains(210));")
    if text != orig:
        path.write_text(text, encoding="utf-8")
        print(f"{path.name}: ピンを更新")

# 3. PLAY_BED テスト（393／401／404）へ 110 を追加。
for ver in ["393", "401", "404"]:
    path = TEST / f"PacketIds{ver}Test.java"
    text = path.read_text(encoding="utf-8")
    orig = text
    text = text.replace("bedExistsIn210And315And316And335And338And340And393And401And404",
                        "bedExistsIn110And210And315And316And335And338And340And393And401And404")
    text = text.replace("bedExistsOnlyIn210And315And316And335And338And340And393And401And404",
                        "bedExistsOnlyIn110And210And315And316And335And338And340And393And401And404")
    anchor = """        // 210（1.10.2）にも PLAY_BED はある（0x2F、315 と同値）。
        assertEquals(OptionalInt.of(0x2F),
                PacketIds.id(PacketName.PLAY_BED, ProtocolVersion.V1_10_2));"""
    if text.count(anchor) == 1:
        text = text.replace(anchor, anchor + """
        // 110（1.9.4）にも PLAY_BED はある（0x2F、210 と同値）。
        assertEquals(OptionalInt.of(0x2F),
                PacketIds.id(PacketName.PLAY_BED, ProtocolVersion.V1_9_4));""")
    text = text.replace("&& other != ProtocolVersion.V1_10_2",
                        "&& other != ProtocolVersion.V1_10_2\n                    && other != ProtocolVersion.V1_9_4")
    if text != orig:
        path.write_text(text, encoding="utf-8")
        print(f"{path.name}: PLAY_BED 列挙へ 110 を追加")
print("完了")
