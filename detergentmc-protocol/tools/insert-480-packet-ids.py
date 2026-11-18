#!/usr/bin/env python3
"""PacketIds.java に 480 列を差し込む。

/tmp/mcdata480/packet-ids-480.json（packet-ids-480.py の出力。minecraft-data
1.14.1 の protocol.json から引いた 480 の ID）を読み、各エントリの先頭
（485 の前）に 480 の ID を挿入する。あわせて id480 フィールド・
コンストラクタ引数・id480()・idFor() の 480 分岐・supportedProtocols()・
isSupported()・ヘッダのプロトコル一覧も更新する。

使い方: python3 tools/insert-480-packet-ids.py [--data FILE]
--data を省略すると /tmp/mcdata480/packet-ids-480.json を使う。
書き換えた後は必ず git diff で内容を確認してからコミットすること。
"""
import argparse
import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
P = ROOT / "src/main/java/io/detergentmc/protocol/PacketIds.java"
DATA = Path("/tmp/mcdata480/packet-ids-480.json")

ENTRY_RE = re.compile(
    r"^(\s+)([A-Z][A-Z0-9_]*)\(State\.(?:PLAY|LOGIN|HANDSHAKING|CONFIGURATION|STATUS),"
    r" Direction\.(?:CLIENTBOUND|SERVERBOUND),"
    r" (-1|0x[0-9a-fA-F]+),")


def fmt(v):
    return "-1" if v < 0 else f"0x{v:02x}"


def replace_once(text, old, new):
    if old not in text:
        print(f"NG: 置換対象が見つからない: {old[:60]}...", file=sys.stderr)
        sys.exit(1)
    return text.replace(old, new, 1)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--data", type=Path, default=DATA)
    args = parser.parse_args()

    raw = json.loads(args.data.read_text(encoding="utf-8"))
    data = {name: r["id480"] for name, r in raw.items()}
    text = P.read_text(encoding="utf-8")
    if "int id480" in text:
        print("skip（既存）: 480 列はすでにある")
        return 0

    # 1. エントリ行：先頭の ID（485）の前に 480 の ID を挿入する。
    lines = text.split("\n")
    changed = 0
    seen = set()
    for i, line in enumerate(lines):
        m = ENTRY_RE.match(line)
        if not m:
            continue
        name = m.group(2)
        if name not in data:
            print(f"NG: データに無いエントリ: {name}", file=sys.stderr)
            return 1
        seen.add(name)
        ins = fmt(data[name]) + ", "
        pos = m.start(3)
        lines[i] = line[:pos] + ins + line[pos:]
        changed += 1
    if changed != len(data) or seen != set(data):
        print(f"NG: 挿入 {changed} 件 / データ {len(data)} 件が一致しない",
              file=sys.stderr)
        return 1
    text = "\n".join(lines)
    print(f"エントリ {changed} 件に 480 列を挿入した")

    # 2. フィールド・コンストラクタ・アクセサ。
    text = replace_once(text,
                        "        private final int id485;",
                        "        private final int id480;\n"
                        "        private final int id485;")
    text = replace_once(text,
                        "PacketName(State state, Direction direction, int id485, int id490, int id498, int id573, int id575,",
                        "PacketName(State state, Direction direction, int id480, int id485, int id490, int id498, int id573, int id575,")
    text = replace_once(text,
                        "            this.id485 = id485;",
                        "            this.id480 = id480;\n"
                        "            this.id485 = id485;")
    text = replace_once(text,
                        "        /** 485（1.14.2）でのID。存在しない場合は -1。 */\n"
                        "        public int id485() {",
                        "        /** 480（1.14.1）でのID。存在しない場合は -1。 */\n"
                        "        public int id480() {\n"
                        "            return id480;\n"
                        "        }\n\n"
                        "        /** 485（1.14.2）でのID。存在しない場合は -1。 */\n"
                        "        public int id485() {")

    # 3. idFor() の 480 分岐。
    text = replace_once(text,
                        "            if (protocol == ProtocolVersion.V1_14_2) {\n"
                        "                return id485;\n"
                        "            }",
                        "            if (protocol == ProtocolVersion.V1_14_1) {\n"
                        "                return id480;\n"
                        "            }\n"
                        "            if (protocol == ProtocolVersion.V1_14_2) {\n"
                        "                return id485;\n"
                        "            }")

    # 4. supportedProtocols() / isSupported()。
    text = replace_once(text,
                        "return List.of(ProtocolVersion.V1_14_2, ProtocolVersion.V1_14_3, ProtocolVersion.V1_14_4, ProtocolVersion.V1_15, ProtocolVersion.V1_15_1,",
                        "return List.of(ProtocolVersion.V1_14_1, ProtocolVersion.V1_14_2, ProtocolVersion.V1_14_3, ProtocolVersion.V1_14_4, ProtocolVersion.V1_15, ProtocolVersion.V1_15_1,")
    text = replace_once(text,
                        "        return protocol == ProtocolVersion.V1_14_2\n",
                        "        return protocol == ProtocolVersion.V1_14_1\n"
                        "                || protocol == ProtocolVersion.V1_14_2\n")

    # 5. ヘッダのプロトコル一覧。
    text = replace_once(text,
                        "プロトコル（485 / 490 / 498 / 573 / 575 / 578 / 758 /",
                        "プロトコル（480 / 485 / 490 / 498 / 573 / 575 / 578 / 758 /")
    text = replace_once(text,
                        "対応するプロトコルは 485（1.14.2）と 490（1.14.3）と 498（1.14.4）と 573（1.15）と 575（1.15.1）と",
                        "対応するプロトコルは 480（1.14.1）と 485（1.14.2）と 490（1.14.3）と 498（1.14.4）と 573（1.15）と 575（1.15.1）と")

    P.write_text(text, encoding="utf-8")
    print("PacketIds.java を更新した")
    return 0


if __name__ == "__main__":
    sys.exit(main())
