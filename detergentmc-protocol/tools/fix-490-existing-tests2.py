#!/usr/bin/env python3
"""490 追加で変わった既存テストの期待値を直す（第2弾：対応リスト系）。

490 が最古の対応プロトコルになったため、OLDEST や対応プロトコル
一覧を固定していた既存テストを新しい実態（先頭 490、次が 498、
全24件）に合わせる。

使い方: python3 tools/fix-490-existing-tests2.py
"""
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
TEST = ROOT / "src/test/java/io/detergentmc/protocol"


def sub(name, old, new, count=1):
    path = TEST / name
    text = path.read_text(encoding="utf-8")
    found = text.count(old)
    if found != count:
        print(f"NG[{name}]: {found} 件（期待 {count}）: {old[:60]!r}")
        sys.exit(1)
    path.write_text(text.replace(old, new), encoding="utf-8")
    print(f"{name} を更新した")


def main():
    # PacketIds498Test：対応プロトコル一覧の先頭に 490。
    sub("PacketIds498Test.java",
        "        assertEquals(List.of(498, 573, 575, 578, 758, 759, 760, 761, 762, 763, 764, 765, 766, 767, 768, 769, 770, 771, 772, 773, 774, 775, 776),\n"
        "                PacketIds.supportedProtocols());",
        "        assertEquals(List.of(490, 498, 573, 575, 578, 758, 759, 760, 761, 762, 763, 764, 765, 766, 767, 768, 769, 770, 771, 772, 773, 774, 775, 776),\n"
        "                PacketIds.supportedProtocols());")

    # ClientAdapter498Test：最古は 490 になった。
    sub("ClientAdapter498Test.java",
        "    void oldestSupportedIs498() {\n"
        "        assertEquals(498, ClientAdapter.OLDEST_SUPPORTED_CLIENT_PROTOCOL);\n"
        "        List<Integer> supported = ClientAdapter.supportedClientProtocols();\n"
        "        assertEquals(498, supported.get(0), supported.toString());\n"
        "        assertEquals(573, supported.get(1), supported.toString());\n"
        "        assertEquals(23, supported.size(), supported.toString());",
        "    void oldestSupportedIs490() {\n"
        "        // 490（1.14.3）対応で最古は 490 になった。498 は2番目。\n"
        "        assertEquals(490, ClientAdapter.OLDEST_SUPPORTED_CLIENT_PROTOCOL);\n"
        "        List<Integer> supported = ClientAdapter.supportedClientProtocols();\n"
        "        assertEquals(490, supported.get(0), supported.toString());\n"
        "        assertEquals(498, supported.get(1), supported.toString());\n"
        "        assertEquals(24, supported.size(), supported.toString());")

    # ClientAdapter573Test / 575Test：同じ形。
    for name in ("ClientAdapter573Test.java", "ClientAdapter575Test.java"):
        sub(name,
            "        assertEquals(498, ClientAdapter.OLDEST_SUPPORTED_CLIENT_PROTOCOL);\n"
            "        List<Integer> supported = ClientAdapter.supportedClientProtocols();\n"
            "        assertEquals(498, supported.get(0), supported.toString());\n"
            "        assertEquals(573, supported.get(1), supported.toString());\n"
            "        assertEquals(23, supported.size(), supported.toString());",
            "        assertEquals(490, ClientAdapter.OLDEST_SUPPORTED_CLIENT_PROTOCOL);\n"
            "        List<Integer> supported = ClientAdapter.supportedClientProtocols();\n"
            "        assertEquals(490, supported.get(0), supported.toString());\n"
            "        assertEquals(498, supported.get(1), supported.toString());\n"
            "        assertEquals(24, supported.size(), supported.toString());")

    # ClientAdapter578Test：並びが1つずつずれる。
    sub("ClientAdapter578Test.java",
        "        // 498（1.14.4）対応で最古は 498 になった。並びは 498・573・575・578…。\n"
        "        assertEquals(498, ClientAdapter.OLDEST_SUPPORTED_CLIENT_PROTOCOL);\n"
        "        List<Integer> supported = ClientAdapter.supportedClientProtocols();\n"
        "        assertEquals(498, supported.get(0), supported.toString());\n"
        "        assertEquals(573, supported.get(1), supported.toString());\n"
        "        assertEquals(575, supported.get(2), supported.toString());\n"
        "        assertEquals(578, supported.get(3), supported.toString());\n"
        "        assertEquals(23, supported.size(), supported.toString());",
        "        // 490（1.14.3）対応で最古は 490 になった。並びは 490・498・573・575・578…。\n"
        "        assertEquals(490, ClientAdapter.OLDEST_SUPPORTED_CLIENT_PROTOCOL);\n"
        "        List<Integer> supported = ClientAdapter.supportedClientProtocols();\n"
        "        assertEquals(490, supported.get(0), supported.toString());\n"
        "        assertEquals(498, supported.get(1), supported.toString());\n"
        "        assertEquals(573, supported.get(2), supported.toString());\n"
        "        assertEquals(575, supported.get(3), supported.toString());\n"
        "        assertEquals(578, supported.get(4), supported.toString());\n"
        "        assertEquals(24, supported.size(), supported.toString());")

    # ClientAdapter758Test：並びが1つずつずれる。
    sub("ClientAdapter758Test.java",
        "        // 498（1.14.4）対応で最古は 498 になった。並びは 498・573・575・578・758…。\n"
        "        assertEquals(498, ClientAdapter.OLDEST_SUPPORTED_CLIENT_PROTOCOL);\n"
        "        assertEquals(ProtocolVersion.V1_14_4, ClientAdapter.OLDEST_SUPPORTED_CLIENT_PROTOCOL);\n"
        "        List<Integer> supported = ClientAdapter.supportedClientProtocols();\n"
        "        assertTrue(supported.contains(758), supported.toString());\n"
        "        assertEquals(498, supported.get(0), supported.toString());\n"
        "        assertEquals(573, supported.get(1), supported.toString());\n"
        "        assertEquals(575, supported.get(2), supported.toString());\n"
        "        assertEquals(578, supported.get(3), supported.toString());\n"
        "        assertEquals(758, supported.get(4), supported.toString());",
        "        // 490（1.14.3）対応で最古は 490 になった。並びは 490・498・573・575・578・758…。\n"
        "        assertEquals(490, ClientAdapter.OLDEST_SUPPORTED_CLIENT_PROTOCOL);\n"
        "        assertEquals(ProtocolVersion.V1_14_3, ClientAdapter.OLDEST_SUPPORTED_CLIENT_PROTOCOL);\n"
        "        List<Integer> supported = ClientAdapter.supportedClientProtocols();\n"
        "        assertTrue(supported.contains(758), supported.toString());\n"
        "        assertEquals(490, supported.get(0), supported.toString());\n"
        "        assertEquals(498, supported.get(1), supported.toString());\n"
        "        assertEquals(573, supported.get(2), supported.toString());\n"
        "        assertEquals(575, supported.get(3), supported.toString());\n"
        "        assertEquals(578, supported.get(4), supported.toString());\n"
        "        assertEquals(758, supported.get(5), supported.toString());")

    # ClientAdapter764Test / ClientAdapterTest：一覧そのもの。
    for name in ("ClientAdapter764Test.java", "ClientAdapterTest.java"):
        sub(name,
            "List.of(498, 573, 575, 578, 758, 759, 760, 761, 762, 763, 764, 765, 766, 767, 768, 769, 770, 771, 772, 773, 774, 775, 776)",
            "List.of(490, 498, 573, 575, 578, 758, 759, 760, 761, 762, 763, 764, 765, 766, 767, 768, 769, 770, 771, 772, 773, 774, 775, 776)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
