#!/usr/bin/env python3
"""PacketIds.java の各エントリに minecraft-data 1.21.3（768）のIDを付ける。

tools/packet-ids-767.py と同じ方法：
  1. PacketIds.java の216エントリ（名前・状態・方向・763／764／765／766／767のID）を
     読む。
  2. 各エントリについて、minecraft-data の protocol.json（1.20／1.20.2／
     1.20.3／1.20.5／1.21.1）の同じ状態・方向の mapper 表から、5つのIDの組と
     一致する内部名を1件だけ求める。既存の5つの値がすべて再現できる
     写像だけが正しいため、写像が間違っていればここで失敗する。
     （1.20.4 は 1.20.3 とプロトコルが同じため 1.20.3 を使う。
     1.20.6 は 1.20.5 とプロトコル 766 を共有するため 1.20.5 を使う。
     1.21／1.21.1 は protocol 767 を共有し、minecraft-data では
     pc/1.21.1 の protocol.json を使う。dataPaths.json で確認済み。
     1.21.2／1.21.3 は protocol 768 を共有するが、minecraft-data
     には pc/1.21.2 のフォルダが無い（dataPaths.json に 1.21.2 の
     項目自体が無い）。1.21.3 の protocol は pc/1.21.3 で、1.21.2
     と同じ 768 なので pc/1.21.3 の protocol.json を使う。
     dataPaths.json で確認済み。1.21.2 と 1.21.3 のレジストリ内容が
     同じことは本物のバニラ 1.21.2／1.21.3 サーバーの Registry Data
     の統合テスト（RegistryOrderCaptureTest）でも確認する。）
  3. その内部名で 1.21.3 の protocol.json を引き、768のIDを取る。
     768に無い内部名は -1（削除されたパケット）。
  4. 768にあって表に無いパケットも一覧に出す（新規パケットの候補。
     Ping Request など意図的に入れないものはここで除外する）。

使い方：
  python3 tools/packet-ids-768.py [--data-dir DIR] [--out FILE]
  --data-dir を省略すると detergentmc-protocol/build/remap-data を使う。
  --out を省略すると detergentmc-protocol/build/remap-data/packet-ids-768.json
  に書き出す。終了コード 0 = 216件すべて一意に写像できた。
  1 = 写像できない／複数候補のエントリがある。
"""

import argparse
import json
import re
import sys
from pathlib import Path

SCRIPT_DIR = Path(__file__).resolve().parent
REPO_ROOT = SCRIPT_DIR.parent.parent
DATA_DIR = REPO_ROOT / "detergentmc-protocol" / "build" / "remap-data"
JAVA_FILE = (REPO_ROOT / "detergentmc-protocol" / "src" / "main" / "java"
             / "io" / "detergentmc" / "protocol" / "PacketIds.java")

STATE_MAP = {"HANDSHAKING": "handshaking", "LOGIN": "login",
             "CONFIGURATION": "configuration", "PLAY": "play"}
DIR_MAP = {"CLIENTBOUND": "toClient", "SERVERBOUND": "toServer"}


def load_mappings(path):
    """protocol.json から {状態: {方向: {内部名: id}}} を作る。"""
    data = json.loads(Path(path).read_text(encoding="utf-8"))
    out = {}
    for state, dirs in data.items():
        if state == "types":
            continue
        out[state] = {}
        for direction, content in dirs.items():
            packet = content["types"]["packet"]
            # ["container", [{"name": "name", "type": ["mapper", {...}]}, ...]]
            mapper = packet[1][0]["type"][1]["mappings"]
            out[state][direction] = {name: int(hex_id, 16)
                                     for hex_id, name in mapper.items()}
    return out


def parse_packet_ids(java_text):
    """PacketIds.java の enum エントリを抜き出す（5引数版）。"""
    pattern = re.compile(
        r"(\w+)\(State\.(\w+), Direction\.(\w+), "
        r"(-1|0x[0-9a-fA-F]+), (-1|0x[0-9a-fA-F]+), (-1|0x[0-9a-fA-F]+), "
        r"(-1|0x[0-9a-fA-F]+), (-1|0x[0-9a-fA-F]+)\)[,;]")
    entries = []
    for m in pattern.finditer(java_text):
        name, state, direction, a, b, c, d, e = m.groups()
        entries.append({
            "name": name, "state": state, "direction": direction,
            "id763": int(a, 0), "id764": int(b, 0), "id765": int(c, 0),
            "id766": int(d, 0), "id767": int(e, 0),
        })
    return entries


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--data-dir", type=Path, default=DATA_DIR)
    parser.add_argument("--out", type=Path, default=None)
    args = parser.parse_args()

    versions = {}
    for label, fname in (("763", "mc-1.20-protocol.json"),
                         ("764", "mc-1.20.2-protocol.json"),
                         ("765", "mc-1.20.3-protocol.json"),
                         ("766", "mc-1.20.5-protocol.json"),
                         ("767", "mc-1.21.1-protocol.json"),
                         ("768", "mc-1.21.3-protocol.json")):
        path = args.data_dir / fname
        if not path.exists():
            print(f"無い: {path}（先に protocol.json を置くこと）", file=sys.stderr)
            return 1
        versions[label] = load_mappings(path)

    entries = parse_packet_ids(JAVA_FILE.read_text(encoding="utf-8"))
    print(f"PacketIds.java のエントリ: {len(entries)} 件")

    result = {}
    problems = []
    for e in entries:
        state = STATE_MAP[e["state"]]
        direction = DIR_MAP[e["direction"]]
        quint = {"763": e["id763"], "764": e["id764"], "765": e["id765"],
                 "766": e["id766"], "767": e["id767"]}
        # 内部名の候補：存在するバージョンのIDがすべて一致するもの。
        candidates = set()
        for v in ("763", "764", "765", "766", "767"):
            if quint[v] < 0:
                continue
            table = versions[v][state][direction]
            hits = {n for n, i in table.items() if i == quint[v]}
            candidates = hits if not candidates else candidates & hits
        if len(candidates) != 1:
            problems.append(
                f"{e['name']} ({e['state']}/{e['direction']} "
                f"763={e['id763']} 764={e['id764']} 765={e['id765']} 766={e['id766']} "
                f"767={e['id767']}): 候補 {sorted(candidates)}")
            continue
        mc_name = candidates.pop()
        id768 = versions["768"][state][direction].get(mc_name, -1)
        result[e["name"]] = {
            "mc": mc_name, "state": e["state"], "direction": e["direction"],
            "id763": e["id763"], "id764": e["id764"], "id765": e["id765"],
            "id766": e["id766"], "id767": e["id767"], "id768": id768,
        }

    print(f"一意に写像できた: {len(result)} 件")
    removed = [n for n, r in result.items() if r["id768"] < 0]
    print(f"768で削除（-1）: {removed}")
    changed = [n for n, r in result.items()
               if r["id768"] >= 0 and r["id768"] != r["id767"]]
    print(f"767とIDが違う: {len(changed)} 件")
    for n in changed:
        r = result[n]
        print(f"  {n}: 767={r['id767']:#04x} -> 768={r['id768']:#04x} ({r['mc']})")

    # 768にあって表に無いパケット（新規パケットの候補）。
    # status 状態はこの表の対象外（PacketIds の javadoc 参照）のため除く。
    known_mc = {(r["state"], r["direction"], r["mc"]) for r in result.values()}
    new_packets = []
    state_names = {"handshaking": "HANDSHAKING", "login": "LOGIN",
                   "configuration": "CONFIGURATION", "play": "PLAY"}
    dir_names = {"toClient": "CLIENTBOUND", "toServer": "SERVERBOUND"}
    for state, dirs in versions["768"].items():
        if state not in state_names:
            continue
        for direction, table in dirs.items():
            for mc_name, i in sorted(table.items(), key=lambda kv: kv[1]):
                key = (state_names[state], dir_names[direction], mc_name)
                if key not in known_mc:
                    new_packets.append((key[0], key[1], mc_name, i))
    print(f"768にあって表に無いパケット: {len(new_packets)} 件")
    for state, direction, mc_name, i in new_packets:
        print(f"  {state}/{direction} {mc_name} = {i:#04x}")

    out_path = args.out or args.data_dir / "packet-ids-768.json"
    out_path.write_text(json.dumps(result, indent=2, ensure_ascii=False)
                        + "\n", encoding="utf-8")
    print(f"写像を {out_path} に書き出した。")

    if problems:
        print("写像できないエントリ:", file=sys.stderr)
        for p in problems:
            print(f"  {p}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
