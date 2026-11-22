"""110 追加で変わる既存テストの期待値を更新する。

- ClientAdapter 系：最古が 110 になり総数が 37 になる。
  supported.get(N) の位置固定は全件 +1 にずらし、先頭に
  get(0)=110 の固定を足す。OLDEST の固定と件数、contains
  チェーンも追随させる。
- PacketIds 系：supportedProtocols の件数を 37 にし、
  一覧リテラルに 110 を足す。
- ClientJoinFlow210Test：一覧の固定があれば追随させる。

冪等ではない（2回実行すると二重にずれる）。
"""

import re
from pathlib import Path

TEST = (Path(__file__).parent.parent / "src" / "test" / "java"
        / "io" / "detergentmc" / "protocol")

FILES = [
    "ClientAdapter210Test.java",
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
    "PacketIds210Test.java",
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
    "ClientJoinFlow210Test.java",
]


def sub(t, old, new, required=True):
    if old not in t:
        if required:
            raise AssertionError(f"見つからない: {old[:60]}")
        return t
    return t.replace(old, new)


GET_PIN = re.compile(r"supported\.get\((\d+)\)")


def shift_get_pins(t):
    """supported.get(N) の固定を全件 +1 し、先頭に get(0)=110 を足す。"""
    had_pin = GET_PIN.search(t) is not None
    t = GET_PIN.sub(lambda m: f"supported.get({int(m.group(1)) + 1})", t)
    if had_pin:
        # ずれ後の get(1) 行の前に get(0)=110 の行を差し込む
        t = re.sub(
            r"(\n( *)assertEquals\(210, supported\.get\(1\))",
            lambda m: (f"\n{m.group(2)}assertEquals(110, supported.get(0),"
                       f" supported.toString());{m.group(0)}"),
            t, count=1)
    return t


for name in FILES:
    p = TEST / name
    t = p.read_text(encoding="utf-8")
    orig = t
    if name.startswith("ClientAdapter"):
        t = sub(t, "assertEquals(36, supported.size(), supported.toString());",
                "assertEquals(37, supported.size(), supported.toString());",
                required=False)
        t = sub(t, "assertEquals(36, supported.size());",
                "assertEquals(37, supported.size());",
                required=False)
        if "contains(110)" not in t:
            t = sub(t, "assertTrue(supported.contains(210));",
                    "assertTrue(supported.contains(110));\n"
                    "        assertTrue(supported.contains(210));",
                    required=False)
            t = sub(t, "assertTrue(supported.contains(210), supported.toString());",
                    "assertTrue(supported.contains(110), supported.toString());\n"
                    "        assertTrue(supported.contains(210), supported.toString());",
                    required=False)
        t = sub(t, "assertEquals(210, ClientAdapter.OLDEST_SUPPORTED_CLIENT_PROTOCOL);",
                "assertEquals(110, ClientAdapter.OLDEST_SUPPORTED_CLIENT_PROTOCOL);",
                required=False)
        t = shift_get_pins(t)
        t = sub(t, "void oldestSupportedIs210And315And316AreStillListed()",
                "void oldestSupportedIs110And210And315And316AreStillListed()",
                required=False)
    elif name.startswith("PacketIds"):
        t = sub(t, "assertEquals(36, PacketIds.supportedProtocols().size());",
                "assertEquals(37, PacketIds.supportedProtocols().size());",
                required=False)
        t = sub(t, "List.of(210, 315, 316, 335, 338, 340,",
                "List.of(110, 210, 315, 316, 335, 338, 340,",
                required=False)
    elif name.startswith("ClientJoinFlow"):
        t = shift_get_pins(t)
        t = sub(t, "assertEquals(36, supported.size());",
                "assertEquals(37, supported.size());", required=False)
    if t != orig:
        p.write_text(t, encoding="utf-8")
        print(f"{name} を更新した")
    else:
        print(f"{name} は変更なし")
