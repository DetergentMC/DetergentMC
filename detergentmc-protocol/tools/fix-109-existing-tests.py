"""109 対応に伴う既存テストの追随（Stage C）。

1. PacketIds.supportedProtocols() の全列挙リテラル
   `List.of(110, 210, …)` の先頭に 109 を挿入（全テストファイル）。
2. ClientAdapter*Test の最古固定ブロック：OLDEST 110→109、
   supported.get(N) の添字を +1 し、先頭に get(0)=109 の行を挿入、
   size 37→38、メソッド名・コメントの追随。
3. ClientAdapter110Test：全サーバー配列に 109 を追加し、
   「109 は未対応」の主張を 108 に付け替える。
4. PacketIds393/401/404Test の PLAY_BED 存在範囲に 109（0x2F）を追加。

各置換は件数を数えて報告する。冪等ではない。
"""

import re
from pathlib import Path

TEST = (Path(__file__).parent.parent / "src" / "test" / "java"
        / "io" / "detergentmc" / "protocol")

report = []

# ---- 1. List.of(110, 210, …) へ 109 を挿入 ----
for path in sorted(TEST.glob("*Test.java")):
    if "109Test" in path.name:
        continue
    s = path.read_text(encoding="utf-8")
    n = s.count("List.of(110, 210,")
    if n:
        s = s.replace("List.of(110, 210,", "List.of(109, 110, 210,")
        path.write_text(s, encoding="utf-8")
        report.append(f"{path.name}: List.of に 109 を挿入 x{n}")

# ---- 2. ClientAdapter*Test の最古固定ブロック ----
OLDEST_FILES = [
    "ClientAdapter110Test", "ClientAdapter210Test", "ClientAdapter315Test",
    "ClientAdapter316Test", "ClientAdapter335Test", "ClientAdapter338Test",
    "ClientAdapter340Test", "ClientAdapter393Test", "ClientAdapter401Test",
    "ClientAdapter404Test", "ClientAdapter477Test", "ClientAdapter480Test",
    "ClientAdapter485Test", "ClientAdapter490Test", "ClientAdapter498Test",
    "ClientAdapter573Test", "ClientAdapter575Test", "ClientAdapter578Test",
    "ClientAdapter758Test",
]
for name in OLDEST_FILES:
    path = TEST / f"{name}.java"
    s = path.read_text(encoding="utf-8")
    orig = s
    # get(N) の添字を +1（降順に置換して二重置換を防ぐ）
    idxs = sorted({int(m) for m in re.findall(r"supported\.get\((\d+)\)", s)},
                  reverse=True)
    for i in idxs:
        s = s.replace(f"supported.get({i})", f"supported.get({i + 1})")
    # OLDEST の値
    s = s.replace("assertEquals(110, ClientAdapter.OLDEST_SUPPORTED_CLIENT_PROTOCOL);",
                  "assertEquals(109, ClientAdapter.OLDEST_SUPPORTED_CLIENT_PROTOCOL);")
    s = s.replace("assertEquals(ProtocolVersion.V1_9_4, ClientAdapter.OLDEST_SUPPORTED_CLIENT_PROTOCOL);",
                  "assertEquals(ProtocolVersion.V1_9_2, ClientAdapter.OLDEST_SUPPORTED_CLIENT_PROTOCOL);")
    # 先頭に get(0)=109 の行を挿入
    anchor = "        List<Integer> supported = ClientAdapter.supportedClientProtocols();\n"
    assert anchor in s, f"{name}: supported 行が無い"
    s = s.replace(anchor, anchor
                  + "        assertEquals(109, supported.get(0), supported.toString());\n", 1)
    # size 37→38
    s = s.replace("assertEquals(37, supported.size(), supported.toString());",
                  "assertEquals(38, supported.size(), supported.toString());")
    # メソッド名・コメント
    s = s.replace("oldestSupportedIs110", "oldestSupportedIs109")
    s = s.replace("最古を 110 に下げても", "最古を 109 に下げても")
    assert s != orig, f"{name}: 変更が無い"
    path.write_text(s, encoding="utf-8")
    report.append(f"{name}: 最古ブロックを 109 に追随（get 添字 {idxs}）")

# ---- 3. ClientAdapter110Test の decide 追随 ----
path = TEST / "ClientAdapter110Test.java"
s = path.read_text(encoding="utf-8")
assert "new int[]{110, 210," in s
s = s.replace("new int[]{110, 210,", "new int[]{109, 110, 210,", 1)
assert "assertFalse(ClientAdapter.decide(109, 110).accepted());" in s
s = s.replace("// 109 以下（1.9.2 以前）と 777 はこのツリーでは未対応のため断る。",
              "// 108 以下（1.9.1 以前）と 777 はこのツリーでは未対応のため断る。")
s = s.replace("assertFalse(ClientAdapter.decide(109, 110).accepted());",
              "assertFalse(ClientAdapter.decide(108, 110).accepted());")
path.write_text(s, encoding="utf-8")
report.append("ClientAdapter110Test: decide のサーバー列と未対応主張を追随")

# ---- 4. PLAY_BED の存在範囲に 109 を追加 ----
for name, method_old, method_new in [
    ("PacketIds393Test", "bedExistsIn110And210And315And316And335And338And340And393And401And404",
     "bedExistsIn109And110And210And315And316And335And338And340And393And401And404"),
    ("PacketIds401Test", "bedExistsIn110And210And315And316And335And338And340And393And401And404",
     "bedExistsIn109And110And210And315And316And335And338And340And393And401And404"),
    ("PacketIds404Test", "bedExistsOnlyIn110And210And315And316And335And338And340And393And401And404",
     "bedExistsOnlyIn109And110And210And315And316And335And338And340And393And401And404"),
]:
    path = TEST / f"{name}.java"
    s = path.read_text(encoding="utf-8")
    assert method_old in s, f"{name}: メソッド名が無い"
    s = s.replace(method_old, method_new)
    anchor = """        // 110（1.9.4）にも PLAY_BED はある（0x2F、210 と同値）。
        assertEquals(OptionalInt.of(0x2F),
                PacketIds.id(PacketName.PLAY_BED, ProtocolVersion.V1_9_4));
"""
    assert anchor in s, f"{name}: 110 の PLAY_BED 断言が無い"
    s = s.replace(anchor, anchor + """        // 109（1.9.2）にも PLAY_BED はある（0x2F、110 と同値）。
        assertEquals(OptionalInt.of(0x2F),
                PacketIds.id(PacketName.PLAY_BED, ProtocolVersion.V1_9_2));
""", 1)
    excl = "&& other != ProtocolVersion.V1_9_4) {"
    assert excl in s, f"{name}: 除外条件が無い"
    s = s.replace(excl,
                  "&& other != ProtocolVersion.V1_9_4\n"
                  "                    && other != ProtocolVersion.V1_9_2) {", 1)
    path.write_text(s, encoding="utf-8")
    report.append(f"{name}: PLAY_BED の存在範囲に 109 を追加")

print("\n".join(report))
print("完了")
