#!/usr/bin/env python3
"""PacketIds.java に 573 列を差し込む。

/tmp/mc573/packet-ids-573.json（packet-ids-573.py の出力。573 の ID は
575 と同値であることが protocol.json のバイト一致で裏付けられている）を
読み、各エントリの先頭（575 の前）に 573 の ID を挿入する。あわせて
id573 フィールド・コンストラクタ引数・id573()・idFor() の 573 分岐・
supportedProtocols()・isSupported()・ヘッダのプロトコル一覧も更新する。

使い方: python3 tools/insert-573-packet-ids.py [--data FILE]
--data を省略すると /tmp/mc573/packet-ids-573.json を使う。
書き換えた後は必ず git diff で内容を確認してからコミットすること。
"""
import argparse
import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
P = ROOT / "src/main/java/io/detergentmc/protocol/PacketIds.java"
DATA = Path("/tmp/mc573/packet-ids-573.json")

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

    data = json.loads(args.data.read_text(encoding="utf-8"))
    text = P.read_text(encoding="utf-8")
    if "int id573" in text:
        print("skip（既存）: 573 列はすでにある")
        return 0

    # 1. エントリ行：先頭の ID（575）の前に 573 の ID を挿入する。
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
    print(f"エントリ {changed} 件に 573 列を挿入した")

    # 2. フィールド・コンストラクタ・アクセサ。
    text = replace_once(text,
                        "        private final int id575;",
                        "        private final int id573;\n"
                        "        private final int id575;")
    text = replace_once(text,
                        "PacketName(State state, Direction direction, int id575, int id578,",
                        "PacketName(State state, Direction direction, int id573, int id575, int id578,")
    text = replace_once(text,
                        "            this.id575 = id575;",
                        "            this.id573 = id573;\n"
                        "            this.id575 = id575;")
    text = replace_once(text,
                        "        /** 575（1.15.1）でのID。存在しない場合は -1。 */\n"
                        "        public int id575() {",
                        "        /** 573（1.15）でのID。存在しない場合は -1。 */\n"
                        "        public int id573() {\n"
                        "            return id573;\n"
                        "        }\n\n"
                        "        /** 575（1.15.1）でのID。存在しない場合は -1。 */\n"
                        "        public int id575() {")

    # 3. idFor() の 573 分岐。
    text = replace_once(text,
                        "            if (protocol == ProtocolVersion.V1_15_1) {\n"
                        "                return id575;\n"
                        "            }",
                        "            if (protocol == ProtocolVersion.V1_15) {\n"
                        "                return id573;\n"
                        "            }\n"
                        "            if (protocol == ProtocolVersion.V1_15_1) {\n"
                        "                return id575;\n"
                        "            }")

    # 4. supportedProtocols() / isSupported()。
    text = replace_once(text,
                        "return List.of(ProtocolVersion.V1_15_1, ProtocolVersion.V1_15_2,",
                        "return List.of(ProtocolVersion.V1_15, ProtocolVersion.V1_15_1, ProtocolVersion.V1_15_2,")
    text = replace_once(text,
                        "        return protocol == ProtocolVersion.V1_15_1\n",
                        "        return protocol == ProtocolVersion.V1_15\n"
                        "                || protocol == ProtocolVersion.V1_15_1\n")

    # 5. ヘッダのプロトコル一覧。
    text = replace_once(text,
                        "プロトコル（575 / 578 / 758 /",
                        "プロトコル（573 / 575 / 578 / 758 /")
    text = replace_once(text,
                        "対応するプロトコルは 575（1.15.1）と 578（1.15.2）と 758（1.18.2）",
                        "対応するプロトコルは 573（1.15）と 575（1.15.1）と 578（1.15.2）と 758（1.18.2）")

    P.write_text(text, encoding="utf-8")
    print("PacketIds.java を更新した")
    return 0


if __name__ == "__main__":
    sys.exit(main())
