"""210 追加で変わる既存テストの期待値の追随（第2弾）。

update-tests-210.py でカバーしきれなかった形を直す：
- 全プロトコル一覧の List.of(315, 316, ...) 完全一致（PacketIds
  系・ClientAdapter 系の全ファイル）→ 210 を先頭に足す。
- ClientAdapter 系の位置固定 supported.get(n) → n+1 にずらす
  （315／316 のテストは第1弾で更新済みのため対象外）。
- OLDEST の固定 315 → 210。
- PacketIds393／401／404 の PLAY_BED 存在範囲に 210 を足す
  （210 の PLAY_BED は 0x2F で 315 と同値）。

冪等ではない。
"""

import re
from pathlib import Path

TEST = (Path(__file__).parent.parent / "src" / "test" / "java"
        / "io" / "detergentmc" / "protocol")

LIST_OLD = "List.of(315, 316, 335, 338, 340,"
LIST_NEW = "List.of(210, 315, 316, 335, 338, 340,"

for p in sorted(TEST.glob("*Test.java")):
    name = p.name
    if name in ("ClientAdapter210Test.java", "PacketIds210Test.java"):
        continue
    t = p.read_text(encoding="utf-8")
    orig = t
    if LIST_OLD in t and LIST_NEW not in t:
        t = t.replace(LIST_OLD, LIST_NEW)
    if name.startswith("ClientAdapter") and name not in (
            "ClientAdapter315Test.java", "ClientAdapter316Test.java"):
        t = t.replace(
            "assertEquals(315, ClientAdapter.OLDEST_SUPPORTED_CLIENT_PROTOCOL);",
            "assertEquals(210, ClientAdapter.OLDEST_SUPPORTED_CLIENT_PROTOCOL);")
        t = t.replace(
            "assertEquals(ProtocolVersion.V1_11, ClientAdapter.OLDEST_SUPPORTED_CLIENT_PROTOCOL);",
            "assertEquals(ProtocolVersion.V1_10_2, ClientAdapter.OLDEST_SUPPORTED_CLIENT_PROTOCOL);")
        t = re.sub(r"supported\.get\((\d+)\)",
                   lambda m: f"supported.get({int(m.group(1)) + 1})", t)
        t = t.replace("assertEquals(35, supported.size()",
                      "assertEquals(36, supported.size()")
    if name in ("PacketIds393Test.java", "PacketIds401Test.java",
                "PacketIds404Test.java"):
        # PLAY_BED の存在範囲に 210 を追加する
        anchor = """        // 315（1.11）にも PLAY_BED はある（0x2F、316 と同値）。
        assertEquals(OptionalInt.of(0x2F),
                PacketIds.id(PacketName.PLAY_BED, ProtocolVersion.V1_11));
"""
        add = anchor + """        // 210（1.10.2）にも PLAY_BED はある（0x2F、315 と同値）。
        assertEquals(OptionalInt.of(0x2F),
                PacketIds.id(PacketName.PLAY_BED, ProtocolVersion.V1_10_2));
"""
        assert t.count(anchor) == 1, f"{name}: bed anchor"
        t = t.replace(anchor, add)
        cond = "                    && other != ProtocolVersion.V1_11) {"
        assert t.count(cond) == 1, f"{name}: bed cond"
        t = t.replace(cond,
                      "                    && other != ProtocolVersion.V1_11\n"
                      "                    && other != ProtocolVersion.V1_10_2) {")
        t = t.replace("bedExistsIn315And316And335And338And340And393And401And404",
                      "bedExistsIn210And315And316And335And338And340And393And401And404")
        t = t.replace("bedExistsOnlyIn315And316And335And338And340And393And401And404",
                      "bedExistsOnlyIn210And315And316And335And338And340And393And401And404")
    if t != orig:
        p.write_text(t, encoding="utf-8")
        print(f"{name} を更新した")
