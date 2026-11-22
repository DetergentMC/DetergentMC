#!/usr/bin/env python3
"""PacketIds.java の各エントリに minecraft-data 1.14.4（498）のIDを付ける。

tools/packet-ids-578.py と同じ方法：
  1. PacketIds.java のエントリ（名前・状態・方向・573〜776のID＝22引数）を
     読む。
  2. 各エントリについて、minecraft-data の protocol.json（1.15／1.15.2／
     1.18.2／1.19／1.19.2／1.19.3／1.19.4／1.20／1.20.2／1.20.3／1.20.5／
     1.21.1／1.21.3／1.21.4／1.21.5／1.21.6／1.21.9／1.21.11／26.1）の
     同じ状態・方向の mapper 表から、IDの組と一致する内部名を1件だけ
     求める。既存の値がすべて再現できる写像だけが正しいため、写像が
     間違っていればここで失敗する。（772は minecraft-data の取得経路の
     都合で除外。776は minecraft-data に無いため除外。575 は protocol.json
     が 1.15.2 とバイト単位で同一のため 1.15.2 で引く。識別には使わない
     列があってもよい。）
  3. その内部名で 1.14.4 の protocol.json を引き、498のIDを取る。
     498に無い内部名は -1（498では存在しないパケット。例：設定フェーズ、
     署名付きチャット系）。
  4. 498にあって表に無いパケットも一覧に出す（新規エントリの候補）。
  5. 573（1.15）のIDとの差分も数える。498 と 573 は play の
     クライアント行きで85件のIDが違う（acknowledge_player_digging の
     位置が 0x08（573）→ 0x5c（498）に移り、間がずれる）ことが
     minecraft-data 同士の比較で分かっている。サーバー行きと
     ログイン・ハンドシェイクは全件同値のはずで、ここでも確認する。

使い方：
  python3 tools/packet-ids-498.py [--data-dir DIR] [--out FILE]
  --data-dir を省略すると /tmp/mcdata/pc を使う（protocol.json を
  pc/<version>/protocol.json に置くこと）。
  --out を省略すると /tmp/mcdata/packet-ids-498.json に書き出す。
  終了コード 0 = 全件一意に写像できた。1 = 写像できないエントリがある。
"""

import argparse
import json
import re
import sys
from pathlib import Path

SCRIPT_DIR = Path(__file__).resolve().parent
REPO_ROOT = SCRIPT_DIR.parent.parent
DATA_DIR = Path("/tmp/mcdata/pc")
JAVA_FILE = (REPO_ROOT / "detergentmc-protocol" / "src" / "main" / "java"
             / "io" / "detergentmc" / "protocol" / "PacketIds.java")

STATE_MAP = {"HANDSHAKING": "handshaking", "LOGIN": "login",
             "CONFIGURATION": "configuration", "PLAY": "play"}
DIR_MAP = {"CLIENTBOUND": "toClient", "SERVERBOUND": "toServer"}

ALL_LABELS = ["573", "575", "578"] + [str(n) for n in range(758, 777)]
VERSION_FILES = (
    ("573", "1.15"), ("575", "1.15.2"), ("578", "1.15.2"),
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
    """PacketIds.java の enum エントリを抜き出す（22引数版）。"""
    arg = r"(-1|0x[0-9a-fA-F]+)"
    pattern = re.compile(
        r"(\w+)\(State\.(\w+), Direction\.(\w+), "
        + ", ".join([arg] * 22) + r"\)[,;]")
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
    path498 = args.data_dir / "1.14.4" / "protocol.json"
    if not path498.exists():
        print(f"無い: {path498}", file=sys.stderr)
        return 1
    v498 = load_mappings(path498)

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
            # PLAY_SPECTATOR_ACTION）。498 のパケット集合は 573 の
            # 部分集合であることが protocol.json 同士の比較で確認済み
            # なので、573 に無いパケットは 498 にも無い。
            if not candidates and all(v < 0 for v in ids.values()):
                result[e["name"]] = {
                    "mc": "", "state": e["state"],
                    "direction": e["direction"], "id498": -1,
                    **{f"id{lab}": ids[lab] for lab in ids},
                }
                continue
            problems.append(
                f"{e['name']} ({e['state']}/{e['direction']} "
                + " ".join(f"{lab}={ids[lab]}" for lab in ids)
                + f"): 候補 {sorted(candidates)}")
            continue
        mc_name = candidates.pop()
        # 498には設定フェーズ自体が無いため、CONFIGURATION の表は存在しない。
        id498 = v498.get(state, {}).get(direction, {}).get(mc_name, -1)
        result[e["name"]] = {
            "mc": mc_name, "state": e["state"], "direction": e["direction"],
            "id498": id498,
            **{f"id{lab}": ids[lab] for lab in ids},
        }

    print(f"一意に写像できた: {len(result)} 件")
    removed = [n for n, r in result.items() if r["id498"] < 0]
    print(f"498で存在しない（-1）: {len(removed)} 件")
    for n in removed:
        print(f"  {n} ({result[n]['mc']})")
    changed = [n for n, r in result.items()
               if r["id498"] >= 0 and r["id498"] != r["id758"]]
    print(f"758とIDが違う: {len(changed)} 件")
    changed573 = [n for n, r in result.items()
                  if r["id498"] >= 0 and r["id573"] >= 0
                  and r["id498"] != r["id573"]]
    print(f"573とIDが違う: {len(changed573)} 件")
    for n in changed573:
        r = result[n]
        print(f"  {n}: 573={r['id573']:#04x} -> 498={r['id498']:#04x} ({r['mc']})")

    # 498にあって表に無いパケット（新規エントリの候補）。
    # status 状態はこの表の対象外のため除く。
    known_mc = {(r["state"], r["direction"], r["mc"]) for r in result.values()}
    new_packets = []
    state_names = {"handshaking": "HANDSHAKING", "login": "LOGIN",
                   "configuration": "CONFIGURATION", "play": "PLAY"}
    dir_names = {"toClient": "CLIENTBOUND", "toServer": "SERVERBOUND"}
    for state, dirs in v498.items():
        if state not in state_names:
            continue
        for direction, table in dirs.items():
            for mc_name, i in sorted(table.items(), key=lambda kv: kv[1]):
                key = (state_names[state], dir_names[direction], mc_name)
                if key not in known_mc:
                    new_packets.append((key[0], key[1], mc_name, i))
    print(f"498にあって表に無いパケット: {len(new_packets)} 件")
    for state, direction, mc_name, i in new_packets:
        print(f"  {state}/{direction} {mc_name} = {i:#04x}")

    out_path = args.out or Path("/tmp/mcdata/packet-ids-498.json")
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
