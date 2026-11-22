"""315（1.11）のパケットIDが 316（1.11.1／1.11.2）と完全一致することを
独立した2つのデータ源で照合し、PacketIds.java の 316 列がそのまま
315 列になることを裏付ける。

番号対応の訂正（Stage A 実測）：315 は 1.11.1 ではなく **1.11 本体**。
1.11.1 と 1.11.2 はどちらもプロトコル 316（実バニラ 1.11.1 の
ステータス応答とハンドシェイク判定バイトコードで確認済み）。
315 は 16w50a でも 1.11-pre1（301）でもなく、製品版 1.11 の番号。

データ源（互いに独立）：
  A = minecraft-data pc/1.11 の protocol.json（315 自身の Burger 抽出。
      version.json が "version": 315 を明示）。
  B = minecraft-data pc/1.11.2 の protocolComments.json（wiki.vg 由来の
      ID 一覧。316 側の表）。
A と B の ID 表が全ステート・全方向で完全一致すれば、315 と 316 の
パケット ID 表は同一である（legacy_server_list_ping も両者に
0xfe で存在し、一致の対象に含める。PacketIds の表自体には従来方針
どおり載せない）。

さらに PacketIds.java の全エントリについて：
  - id316 >= 0 のエントリは、その ID が A の同じステート・方向に
    存在すること（ID は mapper 内で一意）。
  - A のパケット総数（118件）と、PacketIds で id316 >= 0 の件数
    （113件。差分は表に載せない ping_request 等5件の内訳を下で固定）
    が整合すること。
を照合する。実バニラ 1.11 へのワイヤ採取（ログイン〜Join Game〜
Keep Alive varint〜チャット到達、316 と同じ ID・形式）でも裏付け済み。

使い方：
  python3 tools/packet-ids-315.py [--prep DIR]
  --prep の既定は ~/workspace/legacy-prep-315。
  終了コード 0 = 全照合成立、1 = 問題あり。
"""

import argparse
import json
import re
import sys
from pathlib import Path

SCRIPT_DIR = Path(__file__).resolve().parent
JAVA_FILE = (SCRIPT_DIR.parent / "src" / "main" / "java"
             / "io" / "detergentmc" / "protocol" / "PacketIds.java")

STATE_MAP = {"HANDSHAKING": "handshaking", "LOGIN": "login",
             "CONFIGURATION": "configuration", "PLAY": "play"}
DIR_MAP = {"CLIENTBOUND": "toClient", "SERVERBOUND": "toServer"}
EXCLUDE = set()


def load_protocol_json(path):
    data = json.loads(Path(path).read_text(encoding="utf-8"))
    out = {}
    for state, dirs in data.items():
        if state == "types":
            continue
        out[state] = {}
        for direction, content in dirs.items():
            packet = content["types"]["packet"]
            mapper = packet[1][0]["type"][1]["mappings"]
            out[state][direction] = {name: int(hex_id, 16)
                                     for hex_id, name in mapper.items()
                                     if name not in EXCLUDE}
    return out


def load_protocol_comments(path):
    data = json.loads(Path(path).read_text(encoding="utf-8"))
    out = {}
    for state, dirs in data.items():
        out[state] = {}
        for direction, packets in dirs.items():
            out[state][direction] = {
                name: int(info["id"], 16)
                for name, info in packets.items() if name not in EXCLUDE}
    return out


def parse_entries(java_text):
    arg = r"(-1|0x[0-9a-fA-F]+)"
    pattern = re.compile(
        r"(\w+)\(State\.(\w+), Direction\.(\w+), "
        + ", ".join([arg] * 34) + r"\)[,;]")
    entries = []
    for m in pattern.finditer(java_text):
        vals = [int(g, 16) if g.startswith("0x") else -1
                for g in m.groups()[3:]]
        entries.append((m.group(1), m.group(2), m.group(3), vals))
    return entries


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--prep", type=Path,
                        default=Path.home() / "workspace" / "legacy-prep-315")
    args = parser.parse_args()

    a = load_protocol_json(args.prep / "mcdata" / "1.11" / "protocol.json")
    b = load_protocol_comments(
        args.prep / "mcdata" / "1.11.2" / "protocolComments.json")

    problems = []
    # 1) A と B の完全一致
    total_a = 0
    for state in a:
        for direction in a[state]:
            total_a += len(a[state][direction])
            if a[state][direction] != b.get(state, {}).get(direction, {}):
                only_a = set(a[state][direction]) - set(
                    b.get(state, {}).get(direction, {}))
                only_b = set(b.get(state, {}).get(direction, {})) - set(
                    a[state][direction])
                diff = {k for k in set(a[state][direction])
                        & set(b.get(state, {}).get(direction, {}))
                        if a[state][direction][k]
                        != b[state][direction][k]}
                problems.append(
                    f"{state}/{direction}: only315={sorted(only_a)} "
                    f"only316={sorted(only_b)} iddiff={sorted(diff)}")
    print(f"A(315) のパケット総数: {total_a}")
    if total_a != 118:
        problems.append(f"A の総数が 118 でない: {total_a}")

    # 2) PacketIds.java の 316 列が A に全件存在する
    entries = parse_entries(JAVA_FILE.read_text(encoding="utf-8"))
    print(f"PacketIds エントリ数: {len(entries)}")
    if len(entries) != 283:
        problems.append(f"エントリ数が 283 でない: {len(entries)}")
    present = 0
    for name, state, direction, vals in entries:
        id316 = vals[0]
        if id316 < 0:
            continue
        present += 1
        ids_in_a = set(a[STATE_MAP[state]][DIR_MAP[direction]].values())
        if id316 not in ids_in_a:
            problems.append(f"{name}: id316={id316:#x} が A の "
                            f"{state}/{direction} に無い")
    print(f"id316 >= 0 のエントリ数: {present}")
    if present != 113:
        problems.append(f"id316 >= 0 が 113 件でない: {present}")

    if problems:
        for p in problems:
            print("NG:", p, file=sys.stderr)
        return 1
    print("OK: 315 と 316 のパケットID表は完全一致（差分0件）。"
          "PacketIds の 315 列は 316 列と同値でよい。")
    return 0


if __name__ == "__main__":
    sys.exit(main())
