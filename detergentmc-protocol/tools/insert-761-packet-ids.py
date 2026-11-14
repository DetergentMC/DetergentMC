#!/usr/bin/env python3
"""PacketIds.java に 761 列を差し込む。

/tmp/mcdata/packet-ids-761.json（packet-ids-761.py の出力）を読み、
各エントリの 13 個の引数（762〜774）の先頭に 761 の ID を挿入する。
さらに 761〜763 にだけある 2 パケット（Named Entity Spawn、
Feature Flags）を新規エントリとして追加する。

使い方: python3 tools/insert-761-packet-ids.py
書き換えた後は必ず git diff で内容を確認してからコミットすること。
"""
import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
P = ROOT / "src/main/java/io/detergentmc/protocol/PacketIds.java"
DATA = Path("/tmp/mcdata/packet-ids-761.json")

# 761〜763 にだけあるパケット（minecraft-data の protocol.json で確認）。
# 764 以降は存在しない（-1）。Named Entity Spawn は 1.19.4 で Spawn Entity
# へ統合され、Feature Flags は 1.20.2 で設定フェーズへ移った。
NEW_ENTRIES = [
    # (挿入先の直前のエントリ名, 新エントリの行)
    ("PLAY_SPAWN_ENTITY",
     "        /** Named Entity Spawn。761〜763 にだけある（プレイヤーの出現）。"
     "762 以降の Spawn Entity とは別パケット。 */\n"
     "        PLAY_NAMED_ENTITY_SPAWN(State.PLAY, Direction.CLIENTBOUND, "
     "0x02, 0x03, 0x03, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1),"),
    ("PLAY_ENTITY_UPDATE_ATTRIBUTES",
     "        /** Feature Flags。761〜763 はプレイ状態、764 以降は設定フェーズ。 */\n"
     "        PLAY_FEATURE_FLAGS(State.PLAY, Direction.CLIENTBOUND, "
     "0x67, 0x6b, 0x6b, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1),"),
]


def fmt(v):
    return "-1" if v < 0 else f"0x{v:02x}"


def main():
    data = json.loads(DATA.read_text(encoding="utf-8"))
    text = P.read_text(encoding="utf-8")
    if "int id761" in text:
        print("skip（既存）: 761 列はすでにある")
        return 0

    pattern = re.compile(
        r"(\w+)\(State\.(\w+), Direction\.(\w+), "
        r"(-1|0x[0-9a-fA-F]+), (-1|0x[0-9a-fA-F]+), (-1|0x[0-9a-fA-F]+), "
        r"(-1|0x[0-9a-fA-F]+), (-1|0x[0-9a-fA-F]+), (-1|0x[0-9a-fA-F]+), "
        r"(-1|0x[0-9a-fA-F]+), (-1|0x[0-9a-fA-F]+), "
        r"(-1|0x[0-9a-fA-F]+), (-1|0x[0-9a-fA-F]+), "
        r"(-1|0x[0-9a-fA-F]+), (-1|0x[0-9a-fA-F]+), "
        r"(-1|0x[0-9a-fA-F]+)\)([,;])")

    def repl(m):
        name = m.group(1)
        if name not in data:
            print(f"JSON に無いエントリ: {name}", file=sys.stderr)
            raise SystemExit(1)
        id761 = fmt(data[name]["id761"])
        args = ", ".join(m.group(i) for i in range(4, 17))
        return (f"{name}(State.{m.group(2)}, Direction.{m.group(3)}, "
                f"{id761}, {args}){m.group(17)}")

    text, n = pattern.subn(repl, text)
    print(f"761 列を挿入: {n} 件")
    if n != len(data):
        print(f"件数が合わない: 挿入 {n} 件 / JSON {len(data)} 件", file=sys.stderr)
        raise SystemExit(1)

    # 新規エントリ 2 件
    for anchor_name, entry in NEW_ENTRIES:
        if entry.split("(")[0].strip().split()[-1] in text:
            print(f"skip（既存）: {anchor_name} の次")
            continue
        anchor_pat = re.compile(
            r"(        " + anchor_name + r"\(State\.\w+, Direction\.\w+, "
            r"(?:-1|0x[0-9a-fA-F]+)(?:, (?:-1|0x[0-9a-fA-F]+)){13}\)[,;]\n)")
        m = anchor_pat.search(text)
        if not m:
            print(f"アンカーが見つからない: {anchor_name}", file=sys.stderr)
            raise SystemExit(1)
        text = text[:m.end()] + entry + "\n" + text[m.end():]
        print(f"新規エントリを追加: {anchor_name} の次")

    # フィールド・コンストラクタ・アクセサ・idFor
    text = text.replace("        private final int id762;\n",
                        "        private final int id761;\n"
                        "        private final int id762;\n", 1)
    text = text.replace(
        "        PacketName(State state, Direction direction, int id762, int id763, int id764, int id765, int id766,\n",
        "        PacketName(State state, Direction direction, int id761, int id762, int id763, int id764, int id765, int id766,\n",
        1)
    text = text.replace("            this.id762 = id762;\n",
                        "            this.id761 = id761;\n"
                        "            this.id762 = id762;\n", 1)
    text = text.replace(
        '        /** 762（1.19.4）でのID。存在しない場合は -1。 */\n'
        "        public int id762() {\n"
        "            return id762;\n"
        "        }\n",
        '        /** 761（1.19.3）でのID。存在しない場合は -1。 */\n'
        "        public int id761() {\n"
        "            return id761;\n"
        "        }\n"
        "\n"
        '        /** 762（1.19.4）でのID。存在しない場合は -1。 */\n'
        "        public int id762() {\n"
        "            return id762;\n"
        "        }\n",
        1)
    text = text.replace(
        "            if (protocol == ProtocolVersion.V1_19_4) {\n"
        "                return id762;\n"
        "            }\n",
        "            if (protocol == ProtocolVersion.V1_19_3) {\n"
        "                return id761;\n"
        "            }\n"
        "            if (protocol == ProtocolVersion.V1_19_4) {\n"
        "                return id762;\n"
        "            }\n",
        1)
    for marker in ("private final int id761;", "int id761, int id762",
                   "this.id761 = id761;", "public int id761()",
                   "ProtocolVersion.V1_19_3"):
        if marker not in text:
            print(f"挿入できなかった: {marker}", file=sys.stderr)
            raise SystemExit(1)
    P.write_text(text, encoding="utf-8")
    print("PacketIds.java に 761 列を差し込んだ。")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
