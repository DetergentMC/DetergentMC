#!/usr/bin/env python3
"""771のステージ1に伴い「最新は770」固定のテストを771へ移す。

各置換はファイル中にちょうど指定回数だけ現れること。
ClientAdapter／ClientJoinFlow／ClientProxy の771受け入れは
ステージ2で行う（この時点では771の参加はできない）。
"""
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
TEST = ROOT / "src" / "test" / "java" / "io" / "detergentmc" / "protocol"

LISTS = [
    TEST / "PacketIds764Test.java",
    TEST / "PacketIds766Test.java",
    TEST / "PacketIds767Test.java",
    TEST / "PacketIds768Test.java",
    TEST / "PacketIds769Test.java",
    TEST / "PacketIds770Test.java",
]

# (ファイル, 旧, 新, 期待回数)
EDITS = []
for path in LISTS:
    EDITS.append((
        path,
        "assertEquals(List.of(763, 764, 765, 766, 767, 768, 769, 770), PacketIds.supportedProtocols());",
        "assertEquals(List.of(763, 764, 765, 766, 767, 768, 769, 770, 771), PacketIds.supportedProtocols());",
        1))
EDITS += [
    (TEST / "PacketIdsTest.java",
     "                // 764だけのパケット（CONFIG_RESOURCE_PACK_SEND）や766だけの\n"
     "                // パケット（CONFIG_COOKIE_REQUEST など）や767だけの\n"
     "                // パケット（CONFIG_SERVER_LINKS など）があるため、\n"
     "                // 765か764か766か767のどれかに存在すればよい。\n"
     "                assertTrue(PacketIds.id(name, V765).isPresent()\n"
     "                                || PacketIds.id(name, V764).isPresent()\n"
     "                                || PacketIds.id(name, 766).isPresent()\n"
     "                                || PacketIds.id(name, 767).isPresent(),\n"
     "                        name + \" は765か764か766か767に存在すること\");",
     "                // 764だけのパケット（CONFIG_RESOURCE_PACK_SEND）や766だけの\n"
     "                // パケット（CONFIG_COOKIE_REQUEST など）や767だけの\n"
     "                // パケット（CONFIG_SERVER_LINKS など）や771だけの\n"
     "                // パケット（CONFIG_CLEAR_DIALOG など）があるため、\n"
     "                // 765か764か766か767か771のどれかに存在すればよい。\n"
     "                assertTrue(PacketIds.id(name, V765).isPresent()\n"
     "                                || PacketIds.id(name, V764).isPresent()\n"
     "                                || PacketIds.id(name, 766).isPresent()\n"
     "                                || PacketIds.id(name, 767).isPresent()\n"
     "                                || PacketIds.id(name, 771).isPresent(),\n"
     "                        name + \" は765か764か766か767か771に存在すること\");",
     1),
    (TEST / "ProtocolVersion767Test.java",
     "        // 1.21.6 (771) is not known yet: it falls back to 765 with a\n"
     "        // warning, the same as any other unknown version.\n"
     "        assertEquals(765, ProtocolVersion.serverProtocolFor(\"1.21.6\"));",
     "        // 1.21.7 (772) is not known yet: it falls back to 765 with a\n"
     "        // warning, the same as any other unknown version.\n"
     "        assertEquals(765, ProtocolVersion.serverProtocolFor(\"1.21.7\"));",
     1),
    (TEST / "PacketIdsTest.java",
     "for (int protocol : new int[]{771, 340}) {",
     "for (int protocol : new int[]{772, 340}) {", 1),
    (TEST / "PacketIdsTest.java",
     "        assertTrue(PacketIds.isSupported(770));\n"
     "        assertEquals(List.of(763, 764, 765, 766, 767, 768, 769, 770), PacketIds.supportedProtocols());",
     "        assertTrue(PacketIds.isSupported(770));\n"
     "        assertTrue(PacketIds.isSupported(771));\n"
     "        assertEquals(List.of(763, 764, 765, 766, 767, 768, 769, 770, 771), PacketIds.supportedProtocols());",
     1),
    (TEST / "PacketTranslatorTest.java",
     "        // 未対応プロトコルは空（766・767・768・769・770は対応済みのため Join Game の",
     "        // 未対応プロトコルは空（766・767・768・769・770・771は対応済みのため Join Game の",
     1),
    (TEST / "PacketTranslatorTest.java",
     "        assertEquals(OptionalInt.of(0x29), PacketTranslator.translateId(\n"
     "                PacketName.PLAY_JOIN_GAME, 770, V765));",
     "        assertEquals(OptionalInt.of(0x29), PacketTranslator.translateId(\n"
     "                PacketName.PLAY_JOIN_GAME, 770, V765));\n"
     "        assertEquals(OptionalInt.of(0x29), PacketTranslator.translateId(\n"
     "                PacketName.PLAY_JOIN_GAME, 771, V765));",
     1),
    (TEST / "ProtocolVersion767Test.java",
     "    void latestKnownIsNow770AndDefaultIsUnchanged() {\n"
     "        // 770対応後、LATEST_KNOWN は 770（1.21.5）になった。\n"
     "        assertEquals(770, ProtocolVersion.LATEST_KNOWN);\n"
     "        assertEquals(ProtocolVersion.V1_21_5, ProtocolVersion.LATEST_KNOWN);",
     "    void latestKnownIsNow771AndDefaultIsUnchanged() {\n"
     "        // 771対応後、LATEST_KNOWN は 771（1.21.6）になった。\n"
     "        assertEquals(771, ProtocolVersion.LATEST_KNOWN);\n"
     "        assertEquals(ProtocolVersion.V1_21_6, ProtocolVersion.LATEST_KNOWN);",
     1),
    (TEST / "ProtocolVersion768Test.java",
     "    void latestKnownIs770AndDefaultIsUnchanged() {\n"
     "        assertEquals(770, ProtocolVersion.LATEST_KNOWN);\n"
     "        assertEquals(ProtocolVersion.V1_21_5, ProtocolVersion.LATEST_KNOWN);",
     "    void latestKnownIs771AndDefaultIsUnchanged() {\n"
     "        assertEquals(771, ProtocolVersion.LATEST_KNOWN);\n"
     "        assertEquals(ProtocolVersion.V1_21_6, ProtocolVersion.LATEST_KNOWN);",
     1),
    (TEST / "ProtocolVersion769Test.java",
     "    void latestKnownIs770AndDefaultIsUnchanged() {\n"
     "        assertEquals(770, ProtocolVersion.LATEST_KNOWN);\n"
     "        assertEquals(ProtocolVersion.V1_21_5, ProtocolVersion.LATEST_KNOWN);",
     "    void latestKnownIs771AndDefaultIsUnchanged() {\n"
     "        assertEquals(771, ProtocolVersion.LATEST_KNOWN);\n"
     "        assertEquals(ProtocolVersion.V1_21_6, ProtocolVersion.LATEST_KNOWN);",
     1),
    (TEST / "ProtocolVersion770Test.java",
     "    void latestKnownIs770AndDefaultIsUnchanged() {\n"
     "        assertEquals(770, ProtocolVersion.LATEST_KNOWN);\n"
     "        assertEquals(ProtocolVersion.V1_21_5, ProtocolVersion.LATEST_KNOWN);",
     "    void latestKnownIs771AndDefaultIsUnchanged() {\n"
     "        assertEquals(771, ProtocolVersion.LATEST_KNOWN);\n"
     "        assertEquals(ProtocolVersion.V1_21_6, ProtocolVersion.LATEST_KNOWN);",
     1),
]


def main():
    for path, old, new, expected in EDITS:
        src = path.read_text(encoding="utf-8")
        n = src.count(old)
        if n != expected:
            print(f"NG: {path.name}: {old[:60]!r} が {n} 回（{expected} 回のはず）",
                  file=sys.stderr)
            sys.exit(1)
        path.write_text(src.replace(old, new), encoding="utf-8")
        print(f"OK: {path.name}: {old[:50]!r}")
    print("「最新は770」のテストを771へ移した")


if __name__ == "__main__":
    main()
