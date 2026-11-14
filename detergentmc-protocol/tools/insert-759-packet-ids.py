#!/usr/bin/env python3
"""PacketIds.java に 759 列を差し込む。

~/workspace/mcdata759/packet-ids-759.json（packet-ids-759.py の出力）を読み、
各エントリの 15 個の引数（760〜774）の先頭に 759 の ID を挿入する。
759 にだけあるパケットは legacy_server_list_ping（0xfe）のみで、
ハンドシェイク／ステータス系のためこの表の対象外（760のときと同じ）。
そのため新規エントリの追加は無い。

使い方: python3 tools/insert-759-packet-ids.py
書き換えた後は必ず git diff で内容を確認してからコミットすること。
"""
import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
P = ROOT / "src/main/java/io/detergentmc/protocol/PacketIds.java"
DATA = Path.home() / "workspace" / "mcdata759" / "packet-ids-759.json"


def fmt(v):
    return "-1" if v < 0 else f"0x{v:02x}"


def main():
    data = json.loads(DATA.read_text(encoding="utf-8"))
    text = P.read_text(encoding="utf-8")
    if "int id759" in text:
        print("skip（既存）: 759 列はすでにある")
        return 0

    pattern = re.compile(
        r"(\w+)\(State\.(\w+), Direction\.(\w+), "
        r"(-1|0x[0-9a-fA-F]+), (-1|0x[0-9a-fA-F]+), (-1|0x[0-9a-fA-F]+), "
        r"(-1|0x[0-9a-fA-F]+), (-1|0x[0-9a-fA-F]+), (-1|0x[0-9a-fA-F]+), "
        r"(-1|0x[0-9a-fA-F]+), (-1|0x[0-9a-fA-F]+), "
        r"(-1|0x[0-9a-fA-F]+), (-1|0x[0-9a-fA-F]+), "
        r"(-1|0x[0-9a-fA-F]+), (-1|0x[0-9a-fA-F]+), "
        r"(-1|0x[0-9a-fA-F]+), (-1|0x[0-9a-fA-F]+), "
        r"(-1|0x[0-9a-fA-F]+)\)([,;])")

    def repl(m):
        name = m.group(1)
        if name not in data:
            print(f"JSON に無いエントリ: {name}", file=sys.stderr)
            raise SystemExit(1)
        id759 = fmt(data[name]["id759"])
        args = ", ".join(m.group(i) for i in range(4, 19))
        return (f"{name}(State.{m.group(2)}, Direction.{m.group(3)}, "
                f"{id759}, {args}){m.group(19)}")

    text, n = pattern.subn(repl, text)
    print(f"759 列を挿入: {n} 件")
    if n != len(data):
        print(f"件数が合わない: 挿入 {n} 件 / JSON {len(data)} 件", file=sys.stderr)
        raise SystemExit(1)

    # フィールド・コンストラクタ・アクセサ・idFor
    text = text.replace("        private final int id760;\n",
                        "        private final int id759;\n"
                        "        private final int id760;\n", 1)
    text = text.replace(
        "        PacketName(State state, Direction direction, int id760, int id761, int id762, int id763, int id764, int id765, int id766,\n",
        "        PacketName(State state, Direction direction, int id759, int id760, int id761, int id762, int id763, int id764, int id765, int id766,\n",
        1)
    text = text.replace("            this.id760 = id760;\n",
                        "            this.id759 = id759;\n"
                        "            this.id760 = id760;\n", 1)
    text = text.replace(
        '        /** 760（1.19.1／1.19.2）でのID。存在しない場合は -1。 */\n'
        "        public int id760() {\n"
        "            return id760;\n"
        "        }\n",
        '        /** 759（1.19）でのID。存在しない場合は -1。 */\n'
        "        public int id759() {\n"
        "            return id759;\n"
        "        }\n"
        "\n"
        '        /** 760（1.19.1／1.19.2）でのID。存在しない場合は -1。 */\n'
        "        public int id760() {\n"
        "            return id760;\n"
        "        }\n",
        1)
    text = text.replace(
        "            if (protocol == ProtocolVersion.V1_19_1) {\n"
        "                return id760;\n"
        "            }\n",
        "            if (protocol == ProtocolVersion.V1_19) {\n"
        "                return id759;\n"
        "            }\n"
        "            if (protocol == ProtocolVersion.V1_19_1) {\n"
        "                return id760;\n"
        "            }\n",
        1)
    # supportedProtocols() と isSupported() に V1_19 を入れる。
    text = text.replace(
        "        return List.of(ProtocolVersion.V1_19_1, ProtocolVersion.V1_19_3, ProtocolVersion.V1_19_4, ProtocolVersion.V1_20_1,\n",
        "        return List.of(ProtocolVersion.V1_19, ProtocolVersion.V1_19_1, ProtocolVersion.V1_19_3, ProtocolVersion.V1_19_4, ProtocolVersion.V1_20_1,\n",
        1)
    text = text.replace(
        "        return protocol == ProtocolVersion.V1_19_1\n",
        "        return protocol == ProtocolVersion.V1_19\n"
        "                || protocol == ProtocolVersion.V1_19_1\n",
        1)
    for marker in ("private final int id759;", "int id759, int id760",
                   "this.id759 = id759;", "public int id759()",
                   "ProtocolVersion.V1_19)", "ProtocolVersion.V1_19, "):
        if marker not in text:
            print(f"挿入できなかった: {marker}", file=sys.stderr)
            raise SystemExit(1)
    P.write_text(text, encoding="utf-8")
    print("PacketIds.java に 759 列を差し込んだ。")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
