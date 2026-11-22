"""5（1.7.6〜1.7.10）のパケット ID を minecraft-data pc/1.7 の
protocol.json から引き、PacketIds.java の 5 列の値を決める。

番号対応（Stage A 実測）：**47 は 1.8 / 1.8.1 / … / 1.8.9 の全10
リリース共通プロトコル**。先行調査（PREP-47）で全10版の実バニラを
起動し Status 応答が protocol 47 を申告することを確認済みで、
Stage A でも代表 1.8.9 を再起動して再確認した（Status は
`{"name":"1.8.9","protocol":47}`、Join Game は 17 バイトで
dimension を i8 として過不足なく消費、Keep Alive は varint で
4 バイト読み切り＋応答 0x00＋応答後のチャットがバニラログに到達、
Chunk Bulk は (8192+2048+2048)×セクション数＋バイオーム256 で
バイト数完全一致）。jar の SHA-1 は piston-meta の version json と
ライブ照合済み（1.8.9 サーバー jar =
b58b2ceb36e01bcd8dbf49c8fb66c55a9f0676cd、8,320,755 バイト、
javaVersion は majorVersion 8、pack.mcmeta／version.json／data/
は jar 内に存在しない、DataVersion の概念自体が無い）。

同定方法（packet-ids-340.py と同じ）：PacketIds.java の各エントリ
について、既存の各プロトコル列の ID をその版の protocol.json で
逆引きし、候補となる minecraft-data 内部名の積集合を取る。1件に
定まれば、その名前で pc/1.8 の ID を引く。47 で名前が違う2件は
改名表で吸収する（teams → scoreboard_team、
entity_update_attributes → update_attributes）。

使い方：
  python3 tools/packet-ids-47.py [--mcdata DIR] [--out FILE]
  終了コード 0 = 全エントリが一意に写像でき、47のIDを引けた。
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

# 列ラベル（PacketIds の引数順）→ 同定に使う minecraft-data のフォルダ。
# フォルダの無い列（108・485・766〜776）は同定に使わない（他の列で
# 一意に定まる）。108 は 109 と同一、485 は前後列で判別できる。
ALL_LABELS = ["47", "107", "108", "109", "110", "210", "315", "316",
              "335", "338", "340", "393", "401", "404", "477", "480",
              "485", "490", "498", "573", "575", "578", "758", "759",
              "760", "761", "762", "763", "764", "765", "766", "767",
              "768", "769", "770", "771", "772", "773", "774", "775",
              "776"]
VERSION_FILES = {
    "47": "1.8",
    "107": "1.9", "109": "1.9.2", "110": "1.9.4", "210": "1.10",
    "315": "1.11", "335": "1.12", "338": "1.12.1",
    "340": "1.12.2", "393": "1.13", "401": "1.13.1", "404": "1.13.2",
    "477": "1.14", "480": "1.14.1", "490": "1.14.3", "498": "1.14.4",
    "573": "1.15", "575": "1.15.1", "578": "1.15.2", "758": "1.18.2",
    "759": "1.19", "760": "1.19.2", "761": "1.19.3", "762": "1.19.4",
    "763": "1.20", "764": "1.20.2", "765": "1.20.3",
}
LABELS = list(VERSION_FILES)

# 5 で内部名が違うパケット（同定名 → pc/1.7 の名前）。47 と同じ2件。
RENAME_TO_5 = {
    "teams": "scoreboard_team",
    "entity_update_attributes": "update_attributes",
}


def load_mappings(path):
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
    arg = r"(-1|0x[0-9a-fA-F]+)"
    pattern = re.compile(
        r"(\w+)\(State\.(\w+), Direction\.(\w+), "
        + ", ".join([arg] * 41) + r"\)[,;]")
    entries = []
    for m in pattern.finditer(java_text):
        name, state, direction = m.group(1), m.group(2), m.group(3)
        ids = [int(g, 0) for g in m.groups()[3:]]
        entries.append({"name": name, "state": state,
                        "direction": direction,
                        **{f"id{lab}": v
                           for lab, v in zip(ALL_LABELS, ids)}})
    return entries


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--mcdata", type=Path,
        default=Path("/home/hatch/mc-client/node_modules/minecraft-data"
                     "/minecraft-data/data/pc"))
    parser.add_argument("--out", type=Path, default=None)
    args = parser.parse_args()

    versions = {}
    for label, folder in VERSION_FILES.items():
        path = args.mcdata / folder / "protocol.json"
        if not path.exists():
            print(f"無い: {path}", file=sys.stderr)
            return 1
        versions[label] = load_mappings(path)
    v5 = load_mappings(args.mcdata / "1.7" / "protocol.json")

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
            # 同定に使う列がすべて -1 のエントリは、同定対象の版
            # （〜765）より後に追加されたパケットであり、47 には
            # 存在しないため -1 で確定する。
            if not candidates and all(ids[lab] < 0 for lab in LABELS):
                result[e["name"]] = {"mc": "", "state": e["state"],
                                     "direction": e["direction"],
                                     "id5": -1}
                continue
            problems.append(
                f"{e['name']} ({e['state']}/{e['direction']}): "
                f"候補 {sorted(candidates)}")
            continue
        mc_name = candidates.pop()
        name5 = RENAME_TO_5.get(mc_name, mc_name)
        id5 = v5.get(state, {}).get(direction, {}).get(name5, -1)
        result[e["name"]] = {"mc": mc_name, "mc5": name5,
                             "state": e["state"],
                             "direction": e["direction"], "id5": id5,
                             "id47": e["id47"]}

    print(f"一意に写像できた: {len(result)} 件")
    removed = [n for n, r in result.items() if r["id5"] < 0]
    print(f"5で存在しない（-1）: {len(removed)} 件")
    both = {n: r for n, r in result.items()
            if r["id5"] >= 0 and r["id47"] >= 0}
    diff = [n for n, r in both.items() if r["id5"] != r["id47"]]
    print(f"5と47の両方に存在: {len(both)} 件、ID差分: {len(diff)} 件")
    for n in diff:
        print(f"  ID差分: {n} 5={both[n]['id5']:#x} 47={both[n]['id47']:#x}")

    known = {(STATE_MAP[r["state"]], DIR_MAP[r["direction"]],
              r.get("mc5", r["mc"])) for r in result.values() if r["mc"]}
    new_packets = []
    state_names = {v: k for k, v in STATE_MAP.items()}
    dir_names = {v: k for k, v in DIR_MAP.items()}
    for state, dirs in v5.items():
        if state not in state_names:
            continue
        for direction, table in dirs.items():
            for mc_name, i in sorted(table.items(), key=lambda kv: kv[1]):
                if mc_name == "legacy_server_list_ping":
                    continue
                if (state, direction, mc_name) not in known:
                    new_packets.append((state_names[state],
                                        dir_names[direction], mc_name, i))
    print(f"5にあって表に無いパケット: {len(new_packets)} 件")
    for s, d, n, i in new_packets:
        print(f"  {s}/{d} {n} = {i:#04x}")

    for p in problems:
        print(f"問題: {p}")
    if args.out:
        args.out.write_text(json.dumps(result, ensure_ascii=False,
                                       indent=1), encoding="utf-8")
        print(f"書き出し: {args.out}")
    return 1 if problems else 0


if __name__ == "__main__":
    sys.exit(main())
