"""107 対応に伴う既存テストの追随（Stage C）。

1. PacketIds.supportedProtocols() の全列挙リテラル
   `List.of(108, 109, 110, 210, …)` の先頭に 107 を挿入（全テストファイル）。
2. ClientAdapter*Test の最古固定ブロック：OLDEST 108→107、
   supported.get(N) の添字を +1 し、先頭に get(0)=107 の行を挿入、
   size 39→40、メソッド名・コメントの追随。
3. ClientAdapter108Test：全サーバー配列に 107 を追加し、
   「107 は未対応」の主張を取り除く（47 は未対応のまま）。
4. PacketIds393/401/404Test の PLAY_BED 存在範囲に 107（0x2F）を追加。

各置換は件数を数えて報告する。冪等ではない。
"""

import re
from pathlib import Path

TEST = (Path(__file__).parent.parent / "src" / "test" / "java"
        / "io" / "detergentmc" / "protocol")

report = []

# ---- 1. List.of(108, 109, 110, 210, …) へ 107 を挿入 ----
for path in sorted(TEST.glob("*Test.java")):
    if "107Test" in path.name:
        continue
    s = path.read_text(encoding="utf-8")
    n = s.count("List.of(108, 109, 110, 210,")
    if n:
        s = s.replace("List.of(108, 109, 110, 210,", "List.of(107, 108, 109, 110, 210,")
        path.write_text(s, encoding="utf-8")
        report.append(f"{path.name}: List.of に 107 を挿入 x{n}")

# ---- 2. ClientAdapter*Test の最古固定ブロック ----
OLDEST_FILES = [
    "ClientAdapter108Test", "ClientAdapter109Test", "ClientAdapter110Test",
    "ClientAdapter210Test", "ClientAdapter315Test", "ClientAdapter316Test",
    "ClientAdapter335Test", "ClientAdapter338Test", "ClientAdapter340Test",
    "ClientAdapter393Test", "ClientAdapter401Test", "ClientAdapter404Test",
    "ClientAdapter477Test", "ClientAdapter480Test", "ClientAdapter485Test",
    "ClientAdapter490Test", "ClientAdapter498Test", "ClientAdapter573Test",
    "ClientAdapter575Test", "ClientAdapter578Test", "ClientAdapter758Test",
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
    s = s.replace("assertEquals(108, ClientAdapter.OLDEST_SUPPORTED_CLIENT_PROTOCOL);",
                  "assertEquals(107, ClientAdapter.OLDEST_SUPPORTED_CLIENT_PROTOCOL);")
    s = s.replace("assertEquals(ProtocolVersion.V1_9_1, ClientAdapter.OLDEST_SUPPORTED_CLIENT_PROTOCOL);",
                  "assertEquals(ProtocolVersion.V1_9, ClientAdapter.OLDEST_SUPPORTED_CLIENT_PROTOCOL);")
    # 先頭に get(0)=107 の行を挿入
    anchor = "        List<Integer> supported = ClientAdapter.supportedClientProtocols();\n"
    assert anchor in s, f"{name}: supported 行が無い"
    s = s.replace(anchor, anchor
                  + "        assertEquals(107, supported.get(0), supported.toString());\n", 1)
    # size 39→40
    s = s.replace("assertEquals(39, supported.size(), supported.toString());",
                  "assertEquals(40, supported.size(), supported.toString());")
    # メソッド名・コメント
    s = s.replace("oldestSupportedIs108", "oldestSupportedIs107")
    s = s.replace("最古を 108 に下げても", "最古を 107 に下げても")
    assert s != orig, f"{name}: 変更が無い"
    path.write_text(s, encoding="utf-8")
    report.append(f"{name}: 最古ブロックを 107 に追随（get 添字 {idxs}）")

# ---- 3. ClientAdapter108Test の decide 追随 ----
path = TEST / "ClientAdapter108Test.java"
s = path.read_text(encoding="utf-8")
assert "new int[]{108, 109, 110," in s
s = s.replace("new int[]{108, 109, 110,", "new int[]{107, 108, 109, 110,", 1)
assert "assertFalse(ClientAdapter.decide(107, 108).accepted());" in s
s = s.replace("// 107 以下（1.9 以前）と 777 はこのツリーでは未対応のため断る。",
              "// 47（1.8 系）と 777 はこのツリーでは未対応のため断る。")
s = s.replace("        assertFalse(ClientAdapter.decide(107, 108).accepted());\n", "")
path.write_text(s, encoding="utf-8")
report.append("ClientAdapter108Test: decide のサーバー列と未対応主張を追随")

# ---- 4. PLAY_BED の存在範囲に 107 を追加 ----
for name, method_old, method_new in [
    ("PacketIds393Test", "bedExistsIn108And109And110And210And315And316And335And338And340And393And401And404",
     "bedExistsIn107And108And109And110And210And315And316And335And338And340And393And401And404"),
    ("PacketIds401Test", "bedExistsIn108And109And110And210And315And316And335And338And340And393And401And404",
     "bedExistsIn107And108And109And110And210And315And316And335And338And340And393And401And404"),
    ("PacketIds404Test", "bedExistsOnlyIn108And109And110And210And315And316And335And338And340And393And401And404",
     "bedExistsOnlyIn107And108And109And110And210And315And316And335And338And340And393And401And404"),
]:
    path = TEST / f"{name}.java"
    s = path.read_text(encoding="utf-8")
    assert method_old in s, f"{name}: メソッド名が無い"
    s = s.replace(method_old, method_new)
    anchor = """        // 108（1.9.1）にも PLAY_BED はある（0x2F、109 と同値）。
        assertEquals(OptionalInt.of(0x2F),
                PacketIds.id(PacketName.PLAY_BED, ProtocolVersion.V1_9_1));
"""
    assert anchor in s, f"{name}: 108 の PLAY_BED 断言が無い"
    s = s.replace(anchor, anchor + """        // 107（1.9）にも PLAY_BED はある（0x2F、108 と同値）。
        assertEquals(OptionalInt.of(0x2F),
                PacketIds.id(PacketName.PLAY_BED, ProtocolVersion.V1_9));
""", 1)
    excl = "&& other != ProtocolVersion.V1_9_1) {"
    assert excl in s, f"{name}: 除外条件が無い"
    s = s.replace(excl,
                  "&& other != ProtocolVersion.V1_9_1\n"
                  "                    && other != ProtocolVersion.V1_9) {", 1)
    path.write_text(s, encoding="utf-8")
    report.append(f"{name}: PLAY_BED の存在範囲に 107 を追加")

# ---- 5. ClientAdapter109Test / ClientAdapter110Test の decide 追随（追補） ----
# 両テストには「107 は未対応」という主張が残っており、107 解禁で
# decide(107, server) が受理されるため失敗する。コメントを
# 「47（1.8 系）と 777」に直し、decide(107, …) の assertFalse を
# 取り除いた。ClientAdapter109Test は fix-108 手順3と同じく
# 全サーバー配列の先頭に 107 も追加した（110Test の配列は
# 108 の回と同じく変更しない）。本スクリプトの初回実行後に
# 個別適用したため、ここには記録だけを残す。

print("\n".join(report))
print("完了")
