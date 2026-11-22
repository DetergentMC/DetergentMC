#!/usr/bin/env python3
"""PacketIds.java の各エントリに 1.14.2（485）のIDを付ける。

tools/packet-ids-490.py と同じ方法：
  1. PacketIds.java のエントリ（名前・状態・方向・490〜776のID＝24引数）を
     読む。
  2. 各エントリについて、minecraft-data の protocol.json（1.14.3／1.14.4／
     1.15／1.15.2／1.18.2／1.19／1.19.2／1.19.3／1.19.4／1.20／1.20.2／
     1.20.3／1.20.5／1.21.1／1.21.3／1.21.4／1.21.5／1.21.6／1.21.9／
     1.21.11／26.1）の同じ状態・方向の mapper 表から、IDの組と一致する
     内部名を1件だけ求める。既存の値がすべて再現できる写像だけが正しい
     ため、写像が間違っていればここで失敗する。（772は minecraft-data の
     取得経路の都合で除外。776は minecraft-data に無いため除外。575 は
     protocol.json が 1.15.2 とバイト単位で同一のため 1.15.2 で引く。
     識別には使わない列があってもよい。）
  3. その内部名で 485 のIDを取る。**485 の引き先には 1.14.1 の
     protocol.json を使う**。理由：minecraft-data には pc/1.14.2 の
     フォルダ自体が無く（dataPaths.json に 1.14.2 の項目も無い）、
     1.14.1 と 1.14.3 の protocol.json の mapper は全件一致（差分0件、
     形式の差は trade_list の canRestock だけ）で、ViaVersion の
     Protocol1_14_1To1_14_2 は中身が空（パケット集合・形式とも同一）で
     あることから、485 のパケットID集合は 480／490 と同一と確定できる。
     485に無い内部名は -1（設定フェーズ、署名付きチャット系、
     acknowledge_player_digging）。
  4. 485にあって表に無いパケットも一覧に出す（新規エントリの候補）。
  5. 490（1.14.3）のIDとの差分も数える。上の根拠から差分は 0 件のはずで、
     ここでも確認する。

使い方：
  python3 tools/packet-ids-485.py [--data-dir DIR] [--out FILE]
  --data-dir を省略すると /tmp/mcdata485/pc を使う（protocol.json を
  pc/<version>/protocol.json に置くこと）。
  --out を省略すると /tmp/mcdata485/packet-ids-485.json に書き出す。
  終了コード 0 = 全件一意に写像できた。1 = 写像できないエントリがある。
"""

import argparse
import json
import re
import sys
from pathlib import Path

SCRIPT_DIR = Path(__file__).resolve().parent
REPO_ROOT = SCRIPT_DIR.parent.parent
DATA_DIR = Path("/tmp/mcdata485/pc")
JAVA_FILE = (REPO_ROOT / "detergentmc-protocol" / "src" / "main" / "java"
             / "io" / "detergentmc" / "protocol" / "PacketIds.java")

STATE_MAP = {"HANDSHAKING": "handshaking", "LOGIN": "login",
             "CONFIGURATION": "configuration", "PLAY": "play"}
DIR_MAP = {"CLIENTBOUND": "toClient", "SERVERBOUND": "toServer"}

ALL_LABELS = ["490", "498", "573", "575", "578"] + [str(n) for n in range(758, 777)]
VERSION_FILES = (
    ("490", "1.14.3"), ("498", "1.14.4"), ("573", "1.15"), ("575", "1.15.2"),
    ("578", "1.15.2"),
    ("758", "1.18.2"), ("759", "1.19"), ("760", "1.19.2"),
    ("761", "1.19.3"), ("762", "1.19.4"), ("763", "1.20"),
    ("764", "1.20.2"), ("765", "1.20.3"), ("766", "1.20.5"),
    ("767", "1.21.1"), ("768", "1.21.3"), ("769", "1.21.4"),
    ("770", "1.21.5"), ("771", "1.21.6"), ("773", "1.21.9"),
    ("774", "1.21.11"), ("775", "26.1"),
)
LABELS = [lab for lab, _ in VERSION_FILES]


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
            mapper = packet[1][0]["type"][1]["mappings"]
            out[state][direction] = {name: int(hex_id, 16)
                                     for hex_id, name in mapper.items()}
    return out


def parse_packet_ids(java_text):
    """PacketIds.java の enum エントリを抜き出す（24引数版）。"""
    arg = r"(-1|0x[0-9a-fA-F]+)"
    pattern = re.compile(
        r"(\w+)\(State\.(\w+), Direction\.(\w+), "
        + ", ".join([arg] * 24) + r"\)[,;]")
    entries = []
    for m in pattern.finditer(java_text):
        name, state, direction = m.group(1), m.group(2), m.group(3)
        ids = [int(g, 0) for g in m.groups()[3:]]
        entries.append({"name": name, "state": state, "direction": direction,
                        **{f"id{lab}": v for lab, v in zip(ALL_LABELS, ids)}})
    return entries


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--data-dir", type=Path, default=DATA_DIR)
    parser.add_argument("--out", type=Path, default=None)
    args = parser.parse_args()

    versions = {}
    for label, folder in VERSION_FILES:
        path = args.data_dir / folder / "protocol.json"
        if not path.exists():
            print(f"無い: {path}（先に protocol.json を置くこと）", file=sys.stderr)
            return 1
        versions[label] = load_mappings(path)
    # 485 の引き先は 1.14.1 の protocol.json（モジュール冒頭の理由を参照）。
    path485 = args.data_dir / "1.14.1" / "protocol.json"
    if not path485.exists():
        print(f"無い: {path485}", file=sys.stderr)
        return 1
    v485 = load_mappings(path485)

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
            table = versions[lab].get(state, {}).get(direction, {})
            hits = {n for n, i in table.items() if i == ids[lab]}
            candidates = hits if not candidates else candidates & hits
        if len(candidates) != 1:
            # 識別に使う全列が -1 のエントリ（例：776 で追加された
            # PLAY_SPECTATOR_ACTION）。485 のパケット集合は 490 の
            # パケット集合と同一であることが ViaVersion の空プロトコルと
            # protocol.json 同士の比較で確認済みなので、490 に無い
            # パケットは 485 にも無い。
            if not candidates and all(v < 0 for v in ids.values()):
                result[e["name"]] = {
                    "mc": "", "state": e["state"],
                    "direction": e["direction"], "id485": -1,
                    **{f"id{lab}": ids[lab] for lab in ids},
                }
                continue
            problems.append(
                f"{e['name']} ({e['state']}/{e['direction']} "
                + " ".join(f"{lab}={ids[lab]}" for lab in ids)
                + f"): 候補 {sorted(candidates)}")
            continue
        mc_name = candidates.pop()
        # 485には設定フェーズ自体が無いため、CONFIGURATION の表は存在しない。
        id485 = v485.get(state, {}).get(direction, {}).get(mc_name, -1)
        result[e["name"]] = {
            "mc": mc_name, "state": e["state"], "direction": e["direction"],
            "id485": id485,
            **{f"id{lab}": ids[lab] for lab in ids},
        }

    print(f"一意に写像できた: {len(result)} 件")
    removed = [n for n, r in result.items() if r["id485"] < 0]
    print(f"485で存在しない（-1）: {len(removed)} 件")
    changed490 = [n for n, r in result.items()
                  if r["id485"] != r["id490"]]
    print(f"490とIDが違う: {len(changed490)} 件")
    for n in changed490:
        r = result[n]
        print(f"  {n}: 490={r['id490']:#04x} -> 485={r['id485']:#04x} ({r['mc']})")

    # 485にあって表に無いパケット（新規エントリの候補）。
    # status 状態はこの表の対象外のため除く。
    known_mc = {(r["state"], r["direction"], r["mc"]) for r in result.values()}
    new_packets = []
    state_names = {"handshaking": "HANDSHAKING", "login": "LOGIN",
                   "configuration": "CONFIGURATION", "play": "PLAY"}
    dir_names = {"toClient": "CLIENTBOUND", "toServer": "SERVERBOUND"}
    for state, dirs in v485.items():
        if state not in state_names:
            continue
        for direction, table in dirs.items():
            for mc_name, i in sorted(table.items(), key=lambda kv: kv[1]):
                key = (state_names[state], dir_names[direction], mc_name)
                if key not in known_mc:
                    new_packets.append((key[0], key[1], mc_name, i))
    print(f"485にあって表に無いパケット: {len(new_packets)} 件")
    for state, direction, mc_name, i in new_packets:
        print(f"  {state}/{direction} {mc_name} = {i:#04x}")

    out_path = args.out or Path("/tmp/mcdata485/packet-ids-485.json")
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
