"""110（1.9.4／1.9.3）のパケットIDが 210（1.10系）と完全一致
することを独立したデータ源で照合し、PacketIds.java の 210 列が
そのまま 110 列になることを裏付ける。

番号対応（Stage A 実測）：**110 は 1.9.4 と 1.9.3 の両リリース共通
プロトコル**。実バニラ 1.9.4 のステータス応答が protocol 110 を
返すことを自前の採取で確認済み（PREP-110 では 1.9.3 も 110 を
申告することを実測）。

データ源（互いに独立）：
  A = minecraft-data pc/1.9.4 の protocol.json（110 自身の抽出。
      version.json が version: 110 を明示）。
  B = minecraft-data pc/1.10 の protocol.json（210 自身の Burger
      抽出。別の抽出世代）。
A と B の ID 表が全ステート・全方向で完全一致し、かつフィールド
構造の差分が既知の3件（toClient sound_effect／named_sound_effect
の pitch が u8 ⇔ f32、toServer resource_pack_receive の先頭に
110 では hash がある）だけなら、110 と 210 のパケット ID 表は
同一である。

さらに PacketIds.java の全エントリについて：
  - id210 >= 0 のエントリは、その ID が A の同じステート・方向に
    存在すること（ID は mapper 内で一意）。
  - A のパケット総数と、PacketIds で id210 >= 0 の件数（113件。
    差分は表に載せない ping_request 等5件）が整合すること。
を照合する。実バニラ 1.9.4 へのワイヤ採取（ログイン〜Join Game
20/20 バイト消費〜Keep Alive varint 読み切り＋応答 0x0b〜応答後の
チャットがバニラログに到達）でも裏付け済み。フィールド構造の差分
3件は本スクリプトでも数える（types 節は完全一致のはず）。

使い方：
  python3 tools/packet-ids-110.py [--mcdata DIR]
  --mcdata の既定はローカルの minecraft-data の data/pc ディレクトリ。
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

# フィールド構造に差分があってよい既知の3件（play ステート）。
KNOWN_FORMAT_DIFFS = {
    ("toClient", "packet_sound_effect"),
    ("toClient", "packet_named_sound_effect"),
    ("toServer", "packet_resource_pack_receive"),
}


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
    return out, data


def parse_entries(java_text):
    arg = r"(-1|0x[0-9a-fA-F]+)"
    pattern = re.compile(
        r"(\w+)\(State\.(\w+), Direction\.(\w+), "
        + ", ".join([arg] * 36) + r"\)[,;]")
    entries = []
    for m in pattern.finditer(java_text):
        vals = [int(g, 16) if g.startswith("0x") else -1
                for g in m.groups()[3:]]
        entries.append((m.group(1), m.group(2), m.group(3), vals))
    return entries


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--mcdata", type=Path,
        default=Path("/home/hatch/mc-client/node_modules/minecraft-data"
                     "/minecraft-data/data/pc"))
    args = parser.parse_args()

    a, raw_a = load_protocol_json(args.mcdata / "1.9.4" / "protocol.json")
    b, raw_b = load_protocol_json(args.mcdata / "1.10" / "protocol.json")

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
                    f"{state}/{direction}: only110={sorted(only_a)} "
                    f"only210={sorted(only_b)} iddiff={sorted(diff)}")
    print(f"A(110) のパケット総数: {total_a}")
    if total_a != 118:
        problems.append(f"A の総数が 118 でない: {total_a}")

    # 2) types 節の完全一致と、play のフィールド構造差分が既知の3件だけ
    if raw_a["types"] != raw_b["types"]:
        problems.append("types 節が一致しない")
    format_diffs = set()
    for direction in ("toClient", "toServer"):
        ta = raw_a["play"][direction]["types"]
        tb = raw_b["play"][direction]["types"]
        for key in ta:
            if key.startswith("packet_") and ta[key] != tb.get(key):
                format_diffs.add((direction, key))
    print(f"play のフィールド構造差分: {sorted(format_diffs)}")
    if format_diffs != KNOWN_FORMAT_DIFFS:
        problems.append(
            f"フィールド構造差分が既知の3件と一致しない: {format_diffs}")

    # 3) PacketIds.java の 210 列が A に全件存在する
    entries = parse_entries(JAVA_FILE.read_text(encoding="utf-8"))
    print(f"PacketIds エントリ数: {len(entries)}")
    if len(entries) != 283:
        problems.append(f"エントリ数が 283 でない: {len(entries)}")
    present = 0
    for name, state, direction, vals in entries:
        id210 = vals[0]
        if id210 < 0:
            continue
        present += 1
        ids_in_a = set(a[STATE_MAP[state]][DIR_MAP[direction]].values())
        if id210 not in ids_in_a:
            problems.append(f"{name}: id210={id210:#x} が A の "
                            f"{state}/{direction} に無い")
    print(f"id210 >= 0 のエントリ数: {present}")
    if present != 113:
        problems.append(f"id210 >= 0 が 113 件でない: {present}")

    if problems:
        for p in problems:
            print("NG:", p, file=sys.stderr)
        return 1
    print("OK: 110 と 210 のパケットID表は完全一致（差分0件）。"
          "フィールド構造差分は既知の3件のみ。"
          "PacketIds の 110 列は 210 列と同値でよい。")
    return 0


if __name__ == "__main__":
    sys.exit(main())
