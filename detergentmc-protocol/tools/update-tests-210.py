"""210 追加で変わる既存テストの期待値を更新する。

- ClientAdapter 系：最古が 210 になり総数が 36 になる。
  各テストの「最古」固定と件数、contains チェーンを追随させる。
- PacketIds 系：supportedProtocols の件数を 36 にする。
- ClientJoinFlow315Test：315 が二番目に古いプロトコルとして
  残ることの固定へ直す。

冪等ではない（2回実行すると assert で止まる箇所がある）。
"""

from pathlib import Path

TEST = (Path(__file__).parent.parent / "src" / "test" / "java"
        / "io" / "detergentmc" / "protocol")

FILES = [
    "ClientAdapter315Test.java",
    "ClientAdapter316Test.java",
    "ClientAdapter335Test.java",
    "ClientAdapter338Test.java",
    "ClientAdapter340Test.java",
    "ClientAdapter393Test.java",
    "ClientAdapter401Test.java",
    "ClientAdapter404Test.java",
    "ClientAdapter477Test.java",
    "ClientAdapter480Test.java",
    "ClientAdapter485Test.java",
    "ClientAdapter490Test.java",
    "ClientAdapter498Test.java",
    "ClientAdapter573Test.java",
    "ClientAdapter575Test.java",
    "ClientAdapter578Test.java",
    "PacketIds315Test.java",
    "PacketIds316Test.java",
    "PacketIds335Test.java",
    "PacketIds338Test.java",
    "PacketIds340Test.java",
    "PacketIds393Test.java",
    "PacketIds401Test.java",
    "PacketIds404Test.java",
    "PacketIds477Test.java",
    "PacketIds480Test.java",
    "PacketIds485Test.java",
    "PacketIds490Test.java",
    "PacketIds498Test.java",
    "PacketIds573Test.java",
    "PacketIds575Test.java",
    "PacketIds578Test.java",
    "ClientJoinFlow315Test.java",
]


def sub(t, old, new, required=True):
    if old not in t:
        if required:
            raise AssertionError(f"見つからない: {old[:60]}")
        return t
    return t.replace(old, new)


for name in FILES:
    p = TEST / name
    t = p.read_text(encoding="utf-8")
    orig = t
    if name.startswith("ClientAdapter"):
        t = sub(t, "assertEquals(35, supported.size(), supported.toString());",
                "assertEquals(36, supported.size(), supported.toString());",
                required=False)
        t = sub(t, "assertEquals(35, supported.size());",
                "assertEquals(36, supported.size());",
                required=False)
        # contains チェーンに 210 を足す（各テストの流儀に合わせる。
        # 再実行で二重に入らないよう、210 が既にあれば何もしない）
        if "contains(210)" not in t:
            t = sub(t, "assertTrue(supported.contains(315));",
                    "assertTrue(supported.contains(210));\n"
                    "        assertTrue(supported.contains(315));",
                    required=False)
            t = sub(t, "assertTrue(supported.contains(315), supported.toString());",
                    "assertTrue(supported.contains(210), supported.toString());\n"
                    "        assertTrue(supported.contains(315), supported.toString());",
                    required=False)
        # 最古の固定（315 テストのみ該当）
        t = sub(t, "assertEquals(315, ClientAdapter.OLDEST_SUPPORTED_CLIENT_PROTOCOL);",
                "assertEquals(210, ClientAdapter.OLDEST_SUPPORTED_CLIENT_PROTOCOL);",
                required=False)
        t = sub(t, "assertEquals(315, supported.get(0), supported.toString());",
                "assertEquals(210, supported.get(0), supported.toString());",
                required=False)
        t = sub(t, "assertEquals(316, supported.get(1), supported.toString());",
                "assertEquals(315, supported.get(1), supported.toString());",
                required=False)
        t = sub(t, "assertEquals(335, supported.get(2), supported.toString());",
                "assertEquals(316, supported.get(2), supported.toString());",
                required=False)
        t = sub(t, "assertEquals(338, supported.get(3), supported.toString());",
                "assertEquals(335, supported.get(3), supported.toString());",
                required=False)
        t = sub(t, "assertEquals(340, supported.get(4), supported.toString());",
                "assertEquals(338, supported.get(4), supported.toString());",
                required=False)
        t = sub(t, "void oldestSupportedIs315And316And335And338And340AreStillListed()",
                "void oldestSupportedIs210And315And316AreStillListed()",
                required=False)
    elif name.startswith("PacketIds"):
        t = sub(t, "assertEquals(35, PacketIds.supportedProtocols().size());",
                "assertEquals(36, PacketIds.supportedProtocols().size());",
                required=False)
        t = sub(t, "List.of(315, 316, 335, 338, 340,",
                "List.of(210, 315, 316, 335, 338, 340,",
                required=False)
    elif name.startswith("ClientJoinFlow"):
        # ClientJoinFlow315Test は一覧の固定を持たないため、
        # 該当する形がある場合だけ追随させる。
        t = sub(t, "assertEquals(315, supported.get(0));",
                "assertEquals(210, supported.get(0));", required=False)
        t = sub(t, "assertEquals(316, supported.get(1));",
                "assertEquals(315, supported.get(1));\n"
                "        assertEquals(316, supported.get(2));",
                required=False)
        t = sub(t, "assertEquals(35, supported.size());",
                "assertEquals(36, supported.size());", required=False)
    if t != orig:
        p.write_text(t, encoding="utf-8")
        print(f"{name} を更新した")
    else:
        print(f"{name} は変更なし")
