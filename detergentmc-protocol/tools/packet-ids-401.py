"""PacketIds.java の各エントリについて 1.13.1（401）のIDを検証する。

tools/packet-ids-404.py と同じ方法：
  1. PacketIds.java のエントリ（名前・状態・方向・404〜776のID＝28引数）
     を読む。
  2. 各エントリについて、minecraft-data の protocol.json（1.14／1.14.1／
     1.14.3／1.14.4／1.15／1.15.2／1.18.2／1.19／1.19.2／1.19.3／1.19.4／
     1.20／1.20.2／1.20.3／1.20.5／1.21.1／1.21.3／1.21.4／1.21.5／
     1.21.6／1.21.9／1.21.11／26.1）の同じ状態・方向の mapper 表から、
     IDの組と一致する内部名を1件だけ求める。既存の値がすべて再現できる
     写像だけが正しいため、写像が間違っていればここで失敗する。
  3. その内部名で 401 のIDを 1.13.1 の protocol.json から引き、
     エントリの id404 と一致することを確認する（1.13.1 と 1.13.2 の
     protocol.json は mapper 表・全パケットのフィールド構成が一致し、
     共有型 slot の定義だけが違うことを別途平坦化比較で確認済み。
     そのため 401 のIDは全件 404 と同じはずで、このツールが
     エントリ単位の裏付けになる）。
  4. 401にあって表に無いパケットも一覧に出す（新規エントリの候補）。

使い方：
  python3 tools/packet-ids-401.py [--data-dir DIR] [--out FILE]
  --data-dir を省略すると ~/workspace/mcdata404/pc を使う
  （protocol.json を pc/<version>/protocol.json に置くこと。
  1.13.1 の protocol.json も pc/1.13.1/ に置くこと）。
  --out を省略すると ~/workspace/mcdata404/packet-ids-401.json に書き出す。
  終了コード 0 = 全件一意に写像でき、401のIDが404と全件一致した。
  1 = 写像できないエントリ、または404と違うIDがある。
"""

import argparse
import json
import re
import sys
from pathlib import Path

SCRIPT_DIR = Path(__file__).resolve().parent
REPO_ROOT = SCRIPT_DIR.parent.parent
DATA_DIR = Path.home() / "workspace" / "mcdata404" / "pc"
JAVA_FILE = (REPO_ROOT / "detergentmc-protocol" / "src" / "main" / "java"
             / "io" / "detergentmc" / "protocol" / "PacketIds.java")

STATE_MAP = {"HANDSHAKING": "handshaking", "LOGIN": "login",
             "CONFIGURATION": "configuration", "PLAY": "play"}
DIR_MAP = {"CLIENTBOUND": "toClient", "SERVERBOUND": "toServer"}

ALL_LABELS = ["404"] + ["477", "480", "485", "490", "498", "573", "575",
                        "578"] + [str(n) for n in range(758, 777)]
VERSION_FILES = (
    ("477", "1.14"), ("480", "1.14.1"), ("485", "1.14.1"), ("490", "1.14.3"),
    ("498", "1.14.4"), ("573", "1.15"),
    ("575", "1.15.2"),
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
    """PacketIds.java の enum エントリを抜き出す（28引数版）。"""
    arg = r"(-1|0x[0-9a-fA-F]+)"
    pattern = re.compile(
        r"(\w+)\(State\.(\w+), Direction\.(\w+), "
        + ", ".join([arg] * 28) + r"\)[,;]")
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
    # 401 の引き先は 1.13.1 の protocol.json そのもの。
    path401 = args.data_dir / "1.13.1" / "protocol.json"
    if not path401.exists():
        print(f"無い: {path401}", file=sys.stderr)
        return 1
    v401 = load_mappings(path401)

    entries = parse_packet_ids(JAVA_FILE.read_text(encoding="utf-8"))
    print(f"PacketIds.java のエントリ: {len(entries)} 件")

    result = {}
    problems = []
    for e in entries:
        state = STATE_MAP[e["state"]]
        direction = DIR_MAP[e["direction"]]
        ids = {lab: e[f"id{lab}"] for lab in LABELS}
        all_ids = {lab: e[f"id{lab}"] for lab in ALL_LABELS}
        candidates = set()
        for lab in LABELS:
            if ids[lab] < 0:
                continue
            table = versions[lab].get(state, {}).get(direction, {})
            hits = {n for n, i in table.items() if i == ids[lab]}
            candidates = hits if not candidates else candidates & hits
        if len(candidates) != 1:
            # 識別に使う全列が -1 のエントリ（例：776 で追加された
            # PLAY_SPECTATOR_ACTION）は 401 にも無いものとして扱う。
            if not candidates and all(v < 0 for v in ids.values()):
                result[e["name"]] = {
                    "mc": "", "state": e["state"],
                    "direction": e["direction"], "id401": -1,
                    **all_ids,
                }
                continue
            problems.append(
                f"{e['name']} ({e['state']}/{e['direction']} "
                + " ".join(f"{lab}={ids[lab]}" for lab in ids)
                + f"): 候補 {sorted(candidates)}")
            continue
        mc_name = candidates.pop()
        id401 = v401.get(state, {}).get(direction, {}).get(mc_name, -1)
        result[e["name"]] = {
            "mc": mc_name, "state": e["state"], "direction": e["direction"],
            "id401": id401,
            **all_ids,
        }
        if id401 != e["id404"]:
            problems.append(
                f"{e['name']} ({mc_name}): 401={id401:#04x} が "
                f"404={e['id404']:#04x} と違う")

    print(f"一意に写像できた: {len(result)} 件")
    removed = [n for n, r in result.items() if r["id401"] < 0]
    print(f"401で存在しない（-1）: {len(removed)} 件")
    print(f"404とIDが違う: {sum(1 for p in problems if 'と違う' in p)} 件")
    for p in problems:
        print(f"問題: {p}")

    # 401にあって表に無いパケット（新規エントリの候補）。
    # status 状態はこの表の対象外のため除く。
    known_mc = {(r["state"], r["direction"], r["mc"]) for r in result.values()}
    new_packets = []
    state_names = {"handshaking": "HANDSHAKING", "login": "LOGIN",
                   "configuration": "CONFIGURATION", "play": "PLAY"}
    dir_names = {"toClient": "CLIENTBOUND", "toServer": "SERVERBOUND"}
    for state, dirs in v401.items():
        if state not in state_names:
            continue
        for direction, table in dirs.items():
            for mc_name, i in sorted(table.items(), key=lambda kv: kv[1]):
                key = (state_names[state], dir_names[direction], mc_name)
                if key not in known_mc:
                    new_packets.append((key[0], key[1], mc_name, i))
    print(f"401にあって表に無いパケット: {len(new_packets)} 件")
    for state, direction, mc_name, i in new_packets:
        print(f"  {state}/{direction} {mc_name} = {i:#04x}")

    out = args.out or (Path.home() / "workspace" / "mcdata404"
                       / "packet-ids-401.json")
    out.write_text(json.dumps(result, ensure_ascii=False, indent=1),
                   encoding="utf-8")
    print(f"書き出し: {out}")
    return 1 if problems else 0


if __name__ == "__main__":
    sys.exit(main())
