#!/usr/bin/env python3
"""4（1.7.2〜1.7.5）追加に伴う既存テストの列挙追随。

- PacketIds.supportedProtocols() の全一覧ピンに 4 を先頭へ追加。
- ClientAdapter の OLDEST ピン（5→4）、位置断言の +1 シフト、
  先頭に get(0)=4 の断言を挿入、件数 42→43。
- 「4 は未対応のため断る」断言を「4 は受け入れる」へ反転し、
  コメントも追随させる。ClientAdapterTest の旧すぎる例は 4→3。
- PacketIds393/401/404 の PLAY_BED 存在範囲に 4（0x0A、5 と同値）
  を追加する。

ClientAdapter5Test.java は専用テストとして手で書き換えるため除外。
冪等ではない（実行は1回だけ）。
"""
import re
import sys
from pathlib import Path

TEST_DIR = Path(__file__).resolve().parent.parent / "src" / "test" / "java" / "io" / "detergentmc" / "protocol"
EXCLUDE = {"ClientAdapter4Test.java", "PacketIds4Test.java",
           "ClientAdapter5Test.java"}

changed = []


def sub(pattern, repl, text, flags=0):
    return re.subn(pattern, repl, text, flags=flags)


for path in sorted(TEST_DIR.glob("*.java")):
    if path.name in EXCLUDE:
        continue
    text = path.read_text(encoding="utf-8")
    original = text
    total = 0

    # 1. PacketIds の全一覧ピン
    text, n = sub(r"List\.of\(5, 47, 107, 108, 109, 110, 210",
                  "List.of(4, 5, 47, 107, 108, 109, 110, 210", text)
    total += n

    # 2. OLDEST ピン
    text, n = sub(r"assertEquals\(5, ClientAdapter\.OLDEST_SUPPORTED_CLIENT_PROTOCOL\);",
                  "assertEquals(4, ClientAdapter.OLDEST_SUPPORTED_CLIENT_PROTOCOL);",
                  text)
    total += n
    text, n = sub(r"assertEquals\(ProtocolVersion\.V1_7_10, ClientAdapter\.OLDEST_SUPPORTED_CLIENT_PROTOCOL\);",
                  "assertEquals(ProtocolVersion.V1_7_2, ClientAdapter.OLDEST_SUPPORTED_CLIENT_PROTOCOL);",
                  text)
    total += n

    # 3. 位置断言の +1 シフト
    def shift(m):
        return (f"assertEquals({m.group(1)}, supported.get("
                f"{int(m.group(2)) + 1}), supported.toString());")

    text, n = sub(r"assertEquals\((\d+), supported\.get\((\d+)\), supported\.toString\(\)\);",
                  shift, text)
    total += n

    # 4. 先頭に get(0)=4 の断言を挿入（旧 get(0)=5 は get(1)=5 になった）
    lines = text.split("\n")
    out = []
    inserted = 0
    for line in lines:
        m = re.match(r"^(\s*)assertEquals\(5, supported\.get\(1\), supported\.toString\(\)\);$",
                     line)
        if m:
            out.append(f"{m.group(1)}assertEquals(4, supported.get(0), supported.toString());")
            inserted += 1
        out.append(line)
    text = "\n".join(out)
    total += inserted

    # 5. 件数 42→43
    text, n = sub(r"assertEquals\(42, supported\.size\(\), supported\.toString\(\)\);",
                  "assertEquals(43, supported.size(), supported.toString());", text)
    total += n
    text, n = sub(r"assertEquals\(42, supported\.size\(\)\);",
                  "assertEquals(43, supported.size());", text)
    total += n

    # 6. decide(4, ...) の拒否断言を反転＋コメント追随
    text, n = sub(r"// 4（1\.7\.2〜1\.7\.5）と 777 はこのツリーでは未対応のため断る。",
                  "// 777 はこのツリーでは未対応のため断る（4 は対応済みで受け入れる）。",
                  text)
    total += n
    text, n = sub(r"assertFalse\(ClientAdapter\.decide\(4, (\w+)\)\.accepted\(\)\);",
                  r"assertTrue(ClientAdapter.decide(4, \1).accepted());", text)
    total += n

    # 7. PLAY_BED の存在範囲に 4 を追加（393/401/404）
    text, n = sub(r"(PacketIds\.id\(PacketName\.PLAY_BED, ProtocolVersion\.V1_7_10\)\);)",
                  "\\1\n        // 4（1.7.2〜1.7.5）にも PLAY_BED はある（0x0A、5 と同値）。\n"
                  "        assertEquals(OptionalInt.of(0x0A),\n"
                  "                PacketIds.id(PacketName.PLAY_BED, ProtocolVersion.V1_7_2));",
                  text)
    total += n
    def add_exclusion(m):
        indent = m.group(1)
        return (f"{indent}&& other != ProtocolVersion.V1_7_10\n"
                f"{indent}&& other != ProtocolVersion.V1_7_2)")

    text, n = sub(r"^(\s*)&& other != ProtocolVersion\.V1_7_10\)",
                  add_exclusion, text, flags=re.M)
    total += n

    if text != original:
        path.write_text(text, encoding="utf-8")
        changed.append((path.name, total))

# ClientAdapterTest.rejectsOlderClients の旧すぎる例 4→3
path = TEST_DIR / "ClientAdapterTest.java"
text = path.read_text(encoding="utf-8")
text2, n = sub(r"List\.of\(4, 339, 754\)", "List.of(3, 339, 754)", text)
if n:
    path.write_text(text2, encoding="utf-8")
    changed.append((path.name, n))

for name, n in changed:
    print(f"{name}: {n} 件")
print(f"計 {len(changed)} ファイル")
