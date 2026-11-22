"""47 対応に伴う既存テストの追随（Stage C）。

1. PacketIds.supportedProtocols() の全列挙リテラル
   `List.of(107, 108, 109, 110, 210, …)` の先頭に 47 を挿入（全テストファイル）。
2. ClientAdapter*Test の最古固定ブロック：OLDEST 107→47、
   supported.get(N) の添字を +1 し、先頭に get(0)=47 の行を挿入、
   size 40→41、メソッド名・コメントの追随。
3. ClientAdapter107/108/109/110Test：「47 は未対応」の主張を取り除く
   （5 と 777 は未対応のまま）。107Test は全サーバー配列にも 47 を追加。
4. ClientAdapterTest：rejectsOlderClients の 47 を 5（1.7 系）に差し替え。
5. PacketIds393/401/404Test の PLAY_BED 存在範囲に 47（0x0A）を追加。

各置換は件数を数えて報告する。冪等ではない。
"""

import re
from pathlib import Path

TEST = (Path(__file__).parent.parent / "src" / "test" / "java"
        / "io" / "detergentmc" / "protocol")

report = []

# ---- 1. List.of(107, 108, 109, 110, 210, …) へ 47 を挿入 ----
for path in sorted(TEST.glob("*Test.java")):
    if "47Test" in path.name:
        continue
    s = path.read_text(encoding="utf-8")
    n = s.count("List.of(107, 108, 109, 110, 210,")
    if n:
        s = s.replace("List.of(107, 108, 109, 110, 210,",
                      "List.of(47, 107, 108, 109, 110, 210,")
        path.write_text(s, encoding="utf-8")
        report.append(f"{path.name}: List.of に 47 を挿入 x{n}")

# ---- 2. ClientAdapter*Test の最古固定ブロック ----
for path in sorted(TEST.glob("ClientAdapter*Test.java")):
    if "47Test" in path.name:
        continue
    s = path.read_text(encoding="utf-8")
    orig = s
    idxs = sorted({int(m) for m in re.findall(r"supported\.get\((\d+)\)", s)},
                  reverse=True)
    for i in idxs:
        s = s.replace(f"supported.get({i})", f"supported.get({i + 1})")
    s = s.replace("assertEquals(107, ClientAdapter.OLDEST_SUPPORTED_CLIENT_PROTOCOL);",
                  "assertEquals(47, ClientAdapter.OLDEST_SUPPORTED_CLIENT_PROTOCOL);")
    s = s.replace("assertEquals(ProtocolVersion.V1_9, ClientAdapter.OLDEST_SUPPORTED_CLIENT_PROTOCOL);",
                  "assertEquals(ProtocolVersion.V1_8, ClientAdapter.OLDEST_SUPPORTED_CLIENT_PROTOCOL);")
    anchor = "        List<Integer> supported = ClientAdapter.supportedClientProtocols();\n"
    if anchor in s:
        s = s.replace(anchor, anchor
                      + "        assertEquals(47, supported.get(0), supported.toString());\n", 1)
    s = s.replace("assertEquals(40, supported.size(), supported.toString());",
                  "assertEquals(41, supported.size(), supported.toString());")
    s = s.replace("oldestSupportedIs107", "oldestSupportedIs47")
    s = s.replace("最古を 107 に下げても", "最古を 47 に下げても")
    if s != orig:
        path.write_text(s, encoding="utf-8")
        report.append(f"{path.name}: 最古ブロックを 47 に追随（get 添字 {idxs}）")

# ---- 3. 「47 は未対応」主張の除去 ----
path = TEST / "ClientAdapter107Test.java"
s = path.read_text(encoding="utf-8")
assert "new int[]{107, 108, 109," in s
s = s.replace("new int[]{107, 108, 109,", "new int[]{47, 107, 108, 109,", 1)
assert "assertFalse(ClientAdapter.decide(47, 107).accepted());" in s
s = s.replace("        // 47（1.8 系）と 777 はこのツリーでは未対応のため断る。\n"
              "        assertFalse(ClientAdapter.decide(47, 107).accepted());\n",
              "        // 5（1.7 系）と 777 はこのツリーでは未対応のため断る。\n"
              "        assertFalse(ClientAdapter.decide(5, 107).accepted());\n")
path.write_text(s, encoding="utf-8")
report.append("ClientAdapter107Test: decide のサーバー列と未対応主張を追随")

for name, srv in [("ClientAdapter108Test", 108), ("ClientAdapter109Test", 109),
                  ("ClientAdapter110Test", 110)]:
    path = TEST / f"{name}.java"
    s = path.read_text(encoding="utf-8")
    line = f"        assertFalse(ClientAdapter.decide(47, {srv}).accepted());\n"
    assert line in s, f"{name}: 47 未対応の断言が無い"
    s = s.replace(line, f"        assertFalse(ClientAdapter.decide(5, {srv}).accepted());\n")
    path.write_text(s, encoding="utf-8")
    report.append(f"{name}: 47 未対応の断言を 5 に追随")

# ---- 4. ClientAdapterTest の rejectsOlderClients ----
path = TEST / "ClientAdapterTest.java"
s = path.read_text(encoding="utf-8")
assert "List.of(47, 339, 754)" in s
s = s.replace("List.of(47, 339, 754)", "List.of(5, 339, 754)")
path.write_text(s, encoding="utf-8")
report.append("ClientAdapterTest: rejectsOlderClients の 47 を 5 に差し替え")

# ---- 5. PLAY_BED の存在範囲に 47 を追加 ----
for name, method_old, method_new in [
    ("PacketIds393Test", "bedExistsIn107And108And109And110And210And315And316And335And338And340And393And401And404",
     "bedExistsIn47And107And108And109And110And210And315And316And335And338And340And393And401And404"),
    ("PacketIds401Test", "bedExistsIn107And108And109And110And210And315And316And335And338And340And393And401And404",
     "bedExistsIn47And107And108And109And110And210And315And316And335And338And340And393And401And404"),
    ("PacketIds404Test", "bedExistsOnlyIn107And108And109And110And210And315And316And335And338And340And393And401And404",
     "bedExistsOnlyIn47And107And108And109And110And210And315And316And335And338And340And393And401And404"),
]:
    path = TEST / f"{name}.java"
    s = path.read_text(encoding="utf-8")
    assert method_old in s, f"{name}: メソッド名が無い"
    s = s.replace(method_old, method_new)
    anchor = """        // 107（1.9）にも PLAY_BED はある（0x2F、108 と同値）。
        assertEquals(OptionalInt.of(0x2F),
                PacketIds.id(PacketName.PLAY_BED, ProtocolVersion.V1_9));
"""
    assert anchor in s, f"{name}: 107 の PLAY_BED 断言が無い"
    s = s.replace(anchor, anchor + """        // 47（1.8〜1.8.9）にも PLAY_BED はある（0x0A）。
        assertEquals(OptionalInt.of(0x0A),
                PacketIds.id(PacketName.PLAY_BED, ProtocolVersion.V1_8));
""", 1)
    excl = "&& other != ProtocolVersion.V1_9) {"
    assert excl in s, f"{name}: 除外条件が無い"
    s = s.replace(excl,
                  "&& other != ProtocolVersion.V1_9\n"
                  "                    && other != ProtocolVersion.V1_8) {", 1)
    path.write_text(s, encoding="utf-8")
    report.append(f"{name}: PLAY_BED の存在範囲に 47 を追加")

print("\n".join(report))
print("完了")
