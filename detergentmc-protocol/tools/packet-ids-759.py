#!/usr/bin/env python3
"""PacketIds.java の各エントリに minecraft-data 1.19（759）のIDを付ける。

tools/packet-ids-760.py と同じ方法：
  1. PacketIds.java のエントリ（名前・状態・方向・760〜774のID）を
     読む。
  2. 各エントリについて、minecraft-data の protocol.json（1.19.2／
     1.19.3／1.19.4／1.20／1.20.2／1.20.3／1.20.5／1.21.1／1.21.3／
     1.21.4／1.21.5／1.21.6／1.21.8／1.21.9／1.21.11）の同じ状態・
     方向の mapper 表から、15個のIDの組と一致する内部名を1件だけ求める。
     既存の15個の値がすべて再現できる写像だけが正しいため、写像が
     間違っていればここで失敗する。
  3. その内部名で 1.19 の protocol.json を引き、759のIDを取る。
     759に無い内部名は -1（759では存在しない／まだ追加されていない
     パケット。例：設定フェーズのパケット、761以降で追加された
     パケット、760で追加された署名付きチャット関連）。
  4. 759にあって表に無いパケットも一覧に出す（新規パケットの候補。
     759と760でパケットIDが変わるものは別途数えて報告する。
     ここでは表の網羅性の確認として出す）。

使い方：
  python3 tools/packet-ids-759.py [--data-dir DIR] [--mcdata759 DIR]
  --data-dir を省略すると ~/workspace/mcdata760 を使う（protocol.json を
  <version>/protocol.json に置くこと）。
  --mcdata759 を省略すると ~/workspace/mcdata759/protocol-1.19.json を使う。
  終了コード 0 = 全件一意に写像できた。1 = 写像できないエントリがある。
"""

import argparse
import json
import re
import sys
from pathlib import Path

SCRIPT_DIR = Path(__file__).resolve().parent
REPO_ROOT = SCRIPT_DIR.parent.parent
DATA_DIR = Path.home() / "workspace" / "mcdata760"
DATA759_DIR = Path.home() / "workspace" / "mcdata759"
JAVA_FILE = (REPO_ROOT / "detergentmc-protocol" / "src" / "main" / "java"
             / "io" / "detergentmc" / "protocol" / "PacketIds.java")

STATE_MAP = {"HANDSHAKING": "handshaking", "LOGIN": "login",
             "CONFIGURATION": "configuration", "PLAY": "play"}
DIR_MAP = {"CLIENTBOUND": "toClient", "SERVERBOUND": "toServer"}

VERSION_FILES = (
    ("763", "1.20"), ("764", "1.20.2"), ("765", "1.20.3"),
    ("766", "1.20.5"), ("767", "1.21.1"), ("768", "1.21.3"),
    ("769", "1.21.4"), ("770", "1.21.5"), ("771", "1.21.6"),
    ("772", "1.21.8"), ("773", "1.21.9"), ("774", "1.21.11"),
    ("762", "1.19.4"),
    ("761", "1.19.3"),
    ("760", "1.19.2"),
)

LABELS = ["760", "761", "762", "763", "764", "765", "766", "767", "768",
          "769", "770", "771", "772", "773", "774"]


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
    """PacketIds.java の enum エントリを抜き出す（15引数版：760〜774）。"""
    pattern = re.compile(
        r"(\w+)\(State\.(\w+), Direction\.(\w+), "
        r"(-1|0x[0-9a-fA-F]+), (-1|0x[0-9a-fA-F]+), (-1|0x[0-9a-fA-F]+), "
        r"(-1|0x[0-9a-fA-F]+), (-1|0x[0-9a-fA-F]+), (-1|0x[0-9a-fA-F]+), "
        r"(-1|0x[0-9a-fA-F]+), (-1|0x[0-9a-fA-F]+), "
        r"(-1|0x[0-9a-fA-F]+), (-1|0x[0-9a-fA-F]+), "
        r"(-1|0x[0-9a-fA-F]+), (-1|0x[0-9a-fA-F]+), "
        r"(-1|0x[0-9a-fA-F]+), (-1|0x[0-9a-fA-F]+), "
        r"(-1|0x[0-9a-fA-F]+)\)[,;]")
    entries = []
    for m in pattern.finditer(java_text):
        name, state, direction = m.group(1), m.group(2), m.group(3)
        ids = [int(g, 0) for g in m.groups()[3:]]
        entries.append({"name": name, "state": state, "direction": direction,
                        **{f"id{lab}": v for lab, v in zip(LABELS, ids)}})
    return entries


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--data-dir", type=Path, default=DATA_DIR)
    parser.add_argument("--mcdata759", type=Path, default=DATA759_DIR)
    parser.add_argument("--out", type=Path, default=None)
    args = parser.parse_args()

    versions = {}
    for label, folder in VERSION_FILES:
        path = args.data_dir / folder / "protocol.json"
        if not path.exists():
            print(f"無い: {path}（先に protocol.json を置くこと）", file=sys.stderr)
            return 1
        versions[label] = load_mappings(path)
    path759 = args.mcdata759 / "protocol-1.19.json"
    if not path759.exists():
        print(f"無い: {path759}", file=sys.stderr)
        return 1
    versions["759"] = load_mappings(path759)

    entries = parse_packet_ids(JAVA_FILE.read_text(encoding="utf-8"))
    print(f"PacketIds.java のエントリ: {len(entries)} 件")

    result = {}
    problems = []
    for e in entries:
        state = STATE_MAP[e["state"]]
        direction = DIR_MAP[e["direction"]]
        ids = {lab: e[f"id{lab}"] for lab in LABELS}
        candidates = set()
        for lab in LABELS:
            if ids[lab] < 0:
                continue
            table = versions[lab][state][direction]
            hits = {n for n, i in table.items() if i == ids[lab]}
            candidates = hits if not candidates else candidates & hits
        if len(candidates) != 1:
            problems.append(
                f"{e['name']} ({e['state']}/{e['direction']} "
                + " ".join(f"{lab}={ids[lab]}" for lab in ids)
                + f"): 候補 {sorted(candidates)}")
            continue
        mc_name = candidates.pop()
        # 759には設定フェーズ自体が無いため、CONFIGURATION の表は存在しない。
        id759 = versions["759"].get(state, {}).get(direction, {}).get(mc_name, -1)
        result[e["name"]] = {
            "mc": mc_name, "state": e["state"], "direction": e["direction"],
            "id759": id759,
            **{f"id{lab}": ids[lab] for lab in ids},
        }

    print(f"一意に写像できた: {len(result)} 件")
    removed = [n for n, r in result.items() if r["id759"] < 0]
    print(f"759で存在しない（-1）: {len(removed)} 件")
    for n in removed:
        print(f"  {n} ({result[n]['mc']})")
    changed = [n for n, r in result.items()
               if r["id759"] >= 0 and r["id759"] != r["id760"]]
    print(f"760とIDが違う: {len(changed)} 件")
    for n in changed:
        r = result[n]
        print(f"  {n}: 760={r['id760']:#04x} -> 759={r['id759']:#04x} ({r['mc']})")

    # 759にあって表に無いパケット（新規パケットの候補）。
    # status 状態はこの表の対象外のため除く。
    known_mc = {(r["state"], r["direction"], r["mc"]) for r in result.values()}
    new_packets = []
    state_names = {"handshaking": "HANDSHAKING", "login": "LOGIN",
                   "configuration": "CONFIGURATION", "play": "PLAY"}
    dir_names = {"toClient": "CLIENTBOUND", "toServer": "SERVERBOUND"}
    for state, dirs in versions["759"].items():
        if state not in state_names:
            continue
        for direction, table in dirs.items():
            for mc_name, i in sorted(table.items(), key=lambda kv: kv[1]):
                key = (state_names[state], dir_names[direction], mc_name)
                if key not in known_mc:
                    new_packets.append((key[0], key[1], mc_name, i))
    print(f"759にあって表に無いパケット: {len(new_packets)} 件")
    for state, direction, mc_name, i in new_packets:
        print(f"  {state}/{direction} {mc_name} = {i:#04x}")

    out_path = args.out or args.mcdata759 / "packet-ids-759.json"
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
