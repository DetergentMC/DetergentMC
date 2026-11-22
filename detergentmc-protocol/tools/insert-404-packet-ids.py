#!/usr/bin/env python3
"""PacketIds.java に 404 列を差し込む。

~/workspace/mcdata404/packet-ids-404.json（packet-ids-404.py の出力。
minecraft-data 1.13.2 の protocol.json から引いた 404 の ID）を読み、
各エントリの先頭（477 の前）に 404 の ID を挿入する。あわせて
id404 フィールド・コンストラクタ引数・id404()・idFor() の 404 分岐・
supportedProtocols()・isSupported()・ヘッダのプロトコル一覧も更新し、
404 にしか無い PLAY_BED（Use Bed、CB 0x33）を新規エントリとして足す。

使い方: python3 tools/insert-404-packet-ids.py [--data FILE]
--data を省略すると ~/workspace/mcdata404/packet-ids-404.json を使う。
書き換えた後は必ず git diff で内容を確認してからコミットすること。
"""
import argparse
import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
P = ROOT / "src/main/java/io/detergentmc/protocol/PacketIds.java"
DATA = Path("/home/hatch/workspace/mcdata404/packet-ids-404.json")

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
    data = {name: r["id404"] for name, r in raw.items()}
    text = P.read_text(encoding="utf-8")
    if "int id404" in text:
        print("skip（既存）: 404 列はすでにある")
        return 0

    # 1. エントリ行：先頭の ID（477）の前に 404 の ID を挿入する。
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
    print(f"エントリ {changed} 件に 404 列を挿入した")

    # 2. PLAY_BED の新規エントリ（404 のみ 0x33、他は -1）。
    bed_ids = ", ".join(["0x33"] + ["-1"] * 27)
    text = replace_once(
        text,
        "        PLAY_BLOCK_ACTION(State.PLAY, Direction.CLIENTBOUND,",
        "        PLAY_BED(State.PLAY, Direction.CLIENTBOUND, " + bed_ids + "),\n"
        "        PLAY_BLOCK_ACTION(State.PLAY, Direction.CLIENTBOUND,")

    # 3. フィールド・コンストラクタ・アクセサ。
    text = replace_once(text,
                        "        private final int id477;",
                        "        private final int id404;\n"
                        "        private final int id477;")
    text = replace_once(text,
                        "PacketName(State state, Direction direction, int id477, int id480,",
                        "PacketName(State state, Direction direction, int id404, int id477, int id480,")
    text = replace_once(text,
                        "            this.id477 = id477;",
                        "            this.id404 = id404;\n"
                        "            this.id477 = id477;")
    text = replace_once(text,
                        "        /** 477（1.14）でのID。存在しない場合は -1。 */\n"
                        "        public int id477() {",
                        "        /** 404（1.13.2）でのID。存在しない場合は -1。 */\n"
                        "        public int id404() {\n"
                        "            return id404;\n"
                        "        }\n\n"
                        "        /** 477（1.14）でのID。存在しない場合は -1。 */\n"
                        "        public int id477() {")

    # 4. idFor() の 404 分岐。
    text = replace_once(text,
                        "            if (protocol == ProtocolVersion.V1_14) {\n"
                        "                return id477;\n"
                        "            }",
                        "            if (protocol == ProtocolVersion.V1_13_2) {\n"
                        "                return id404;\n"
                        "            }\n"
                        "            if (protocol == ProtocolVersion.V1_14) {\n"
                        "                return id477;\n"
                        "            }")

    # 5. supportedProtocols() / isSupported()。
    text = replace_once(text,
                        "return List.of(ProtocolVersion.V1_14, ProtocolVersion.V1_14_1,",
                        "return List.of(ProtocolVersion.V1_13_2, ProtocolVersion.V1_14, ProtocolVersion.V1_14_1,")
    text = replace_once(text,
                        "        return protocol == ProtocolVersion.V1_14\n",
                        "        return protocol == ProtocolVersion.V1_13_2\n"
                        "                || protocol == ProtocolVersion.V1_14\n")

    # 6. ヘッダのプロトコル一覧。
    text = replace_once(text,
                        "プロトコル（477 / 480 / 485 / 490 / 498 / 573 / 575 / 578 / 758 /",
                        "プロトコル（404 / 477 / 480 / 485 / 490 / 498 / 573 / 575 / 578 / 758 /")
    text = replace_once(text,
                        "対応するプロトコルは 477（1.14）と 480（1.14.1）と",
                        "対応するプロトコルは 404（1.13.2）と 477（1.14）と 480（1.14.1）と")

    P.write_text(text, encoding="utf-8")
    print("PacketIds.java を更新した")
    return 0


if __name__ == "__main__":
    sys.exit(main())
