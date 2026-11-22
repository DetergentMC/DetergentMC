"""PacketIds.java の各エントリについて 1.12（335）のIDを求める。

tools/packet-ids-338.py と同じ方法：
  1. PacketIds.java のエントリ（名前・状態・方向・338〜776のID＝32引数）
     を読む。
  2. 各エントリについて、minecraft-data の protocol.json（1.13／1.13.1／
     1.13.2／1.14／1.14.1／1.14.3／1.14.4／1.15／1.15.2／1.18.2／1.19／
     1.19.2／1.19.3／1.19.4／1.20／1.20.2／1.20.3／1.20.5／1.21.1／
     1.21.3／1.21.4／1.21.5／1.21.6／1.21.9／1.21.11／26.1）の同じ
     状態・方向の mapper 表から、IDの組と一致する内部名を1件だけ求める。
     既存の値がすべて再現できる写像だけが正しいため、写像が間違って
     いればここで失敗する。
  3. その内部名で 335 のIDを 1.12 の protocol.json から引く。
     335 に無いパケットは -1。
  4. 335にあって表に無いパケットも一覧に出す（新規エントリの候補。
     legacy_server_list_ping と status 状態は従来方針どおり対象外）。

先行調査（~/workspace/legacy-prep-335/）の結論は「335⇔338 の差分は
レシピブック再編に集中し、ID差分53件はすべて機械的な挿入・削除で
説明が付く」。このスクリプトはその結論を鵜呑みにせず、1.12 の
protocol.json から独立に引き直した上で、次の機械規則が全件成立する
ことを照合する：
  - プレイ・クライアント行き：craft_recipe_response（338で 0x2b）は
    335 に無い（-1）。338 で 0x2c 以上のものは -1 ずれる。それ以外は
    同一。
  - プレイ・サーバー行き：craft_recipe_request（338で 0x12）は
    335 に無い（-1）。338 で 0x01〜0x11 のものは +1 ずれる
    （335 では先頭 0x01 に prepare_crafting_grid が居るため）。
    0x00 と 0x13 以上は同一（0x12 の欠落と相殺する）。
  - プレイ以外（handshaking／login／configuration）：全件同一。
  - 338 に無いエントリ（id338 = -1）は 335 にも無い。
1件でも食い違えば終了コード 1 で報告する。

1.12 の protocol.json は data-generator の無い世代のため、先行調査が
piston-meta 照合済みの実 jar と minecraft-data ローカルコピーで
確定したものを使う。なお minecraft-data の pc/1.12 は 335 自身の
データ（version.json の version が 335）である。

使い方：
  python3 tools/packet-ids-335.py [--data-dir DIR] [--target FILE]
                                  [--out FILE]
  --data-dir を省略すると ~/workspace/mcdata404/pc を使う
  （protocol.json を pc/<version>/protocol.json に置くこと）。
  --target を省略すると ~/workspace/legacy-prep-335/mcdata/1.12/
  protocol.json を使う。
  --out を省略すると ~/workspace/mcdata404/packet-ids-335.json に
  書き出す。
  終了コード 0 = 全エントリが一意に写像でき、335のIDを引けて、
  かつ機械規則が全件成立した。1 = 問題がある。
"""

import argparse
import json
import re
import sys
from pathlib import Path

SCRIPT_DIR = Path(__file__).resolve().parent
REPO_ROOT = SCRIPT_DIR.parent.parent
DATA_DIR = Path.home() / "workspace" / "mcdata404" / "pc"
TARGET_DEFAULT = (Path.home() / "workspace" / "legacy-prep-335" / "mcdata"
                  / "1.12" / "protocol.json")
JAVA_FILE = (REPO_ROOT / "detergentmc-protocol" / "src" / "main" / "java"
             / "io" / "detergentmc" / "protocol" / "PacketIds.java")

STATE_MAP = {"HANDSHAKING": "handshaking", "LOGIN": "login",
             "CONFIGURATION": "configuration", "PLAY": "play"}
DIR_MAP = {"CLIENTBOUND": "toClient", "SERVERBOUND": "toServer"}

ALL_LABELS = ["338", "340", "393", "401", "404", "477", "480", "485", "490",
              "498", "573", "575", "578"] + [str(n) for n in range(758, 777)]
VERSION_FILES = (
    ("393", "1.13"), ("401", "1.13.1"), ("404", "1.13.2"),
    ("477", "1.14"), ("480", "1.14.1"), ("485", "1.14.1"),
    ("490", "1.14.3"), ("498", "1.14.4"), ("573", "1.15"),
    ("575", "1.15.2"), ("578", "1.15.2"),
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
    """PacketIds.java の enum エントリを抜き出す（32引数版）。"""
    arg = r"(-1|0x[0-9a-fA-F]+)"
    pattern = re.compile(
        r"(\w+)\(State\.(\w+), Direction\.(\w+), "
        + ", ".join([arg] * 32) + r"\)[,;]")
    entries = []
    for m in pattern.finditer(java_text):
        name, state, direction = m.group(1), m.group(2), m.group(3)
        ids = [int(g, 0) for g in m.groups()[3:]]
        entries.append({"name": name, "state": state, "direction": direction,
                        **{f"id{lab}": v for lab, v in zip(ALL_LABELS, ids)}})
    return entries


def expected_id335(name, entry, id338):
    """先行調査の機械規則から期待される id335 を返す。"""
    if id338 < 0:
        return -1
    if entry["state"] != "PLAY":
        return id338
    if entry["direction"] == "CLIENTBOUND":
        if name == "PLAY_CRAFT_RECIPE_RESPONSE":
            return -1
        return id338 - 1 if id338 >= 0x2c else id338
    # SERVERBOUND
    if name == "PLAY_CRAFT_RECIPE_REQUEST":
        return -1
    if 0x01 <= id338 <= 0x11:
        return id338 + 1
    return id338


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--data-dir", type=Path, default=DATA_DIR)
    parser.add_argument("--target", type=Path, default=TARGET_DEFAULT)
    parser.add_argument("--out", type=Path, default=None)
    args = parser.parse_args()

    versions = {}
    for label, folder in VERSION_FILES:
        path = args.data_dir / folder / "protocol.json"
        if not path.exists():
            print(f"無い: {path}（先に protocol.json を置くこと）",
                  file=sys.stderr)
            return 1
        versions[label] = load_mappings(path)
    if not args.target.exists():
        print(f"無い: {args.target}", file=sys.stderr)
        return 1
    v335 = load_mappings(args.target)

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
            # パケット）は 335 にも無いものとして扱う。
            if not candidates and all(v < 0 for v in ids.values()):
                result[e["name"]] = {
                    "mc": "", "state": e["state"],
                    "direction": e["direction"], "id335": -1,
                    **all_ids,
                }
                continue
            problems.append(
                f"{e['name']} ({e['state']}/{e['direction']} "
                + " ".join(f"{lab}={ids[lab]}" for lab in ids)
                + f"): 候補 {sorted(candidates)}")
            continue
        mc_name = candidates.pop()
        id335 = v335.get(state, {}).get(direction, {}).get(mc_name, -1)
        result[e["name"]] = {
            "mc": mc_name, "state": e["state"], "direction": e["direction"],
            "id335": id335,
            **all_ids,
        }

    print(f"一意に写像できた: {len(result)} 件")
    removed = [n for n, r in result.items() if r["id335"] < 0]
    print(f"335で存在しない（-1）: {len(removed)} 件")
    diff338 = [n for n, r in result.items() if r["id335"] != r["338"]]
    print(f"338とIDが違う: {len(diff338)} 件")
    # 先行調査の機械規則（レシピブック再編の挿入・削除だけ）の照合。
    for n, r in result.items():
        exp = expected_id335(n, r, r["338"])
        if exp != r["id335"]:
            problems.append(
                f"{n}: 機械規則の期待値 {exp} != 実際 {r['id335']} "
                f"(id338={r['338']})")
    for p in problems:
        print(f"問題: {p}")

    # 335にあって表に無いパケット（新規エントリの候補）。
    # status 状態と legacy_server_list_ping はこの表の対象外のため除く。
    known_mc = {(r["state"], r["direction"], r["mc"]) for r in result.values()}
    new_packets = []
    state_names = {"handshaking": "HANDSHAKING", "login": "LOGIN",
                   "configuration": "CONFIGURATION", "play": "PLAY"}
    dir_names = {"toClient": "CLIENTBOUND", "toServer": "SERVERBOUND"}
    for state, dirs in v335.items():
        if state not in state_names:
            continue
        for direction, table in dirs.items():
            for mc_name, i in sorted(table.items(), key=lambda kv: kv[1]):
                if mc_name == "legacy_server_list_ping":
                    continue
                key = (state_names[state], dir_names[direction], mc_name)
                if key not in known_mc:
                    new_packets.append((key[0], key[1], mc_name, i))
    print(f"335にあって表に無いパケット: {len(new_packets)} 件")
    for state, direction, mc_name, i in new_packets:
        print(f"  {state}/{direction} {mc_name} = {i:#04x}")

    out = args.out or (Path.home() / "workspace" / "mcdata404"
                       / "packet-ids-335.json")
    out.write_text(json.dumps(result, ensure_ascii=False, indent=1),
                   encoding="utf-8")
    print(f"書き出し: {out}")
    return 1 if problems else 0


if __name__ == "__main__":
    sys.exit(main())
