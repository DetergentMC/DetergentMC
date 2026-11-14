#!/usr/bin/env python3
"""PacketIds.java の各エントリに minecraft-data 26.1（775）のIDを付ける。

tools/packet-ids-774.py と同じ方法（ただし 775 ではエントリが 16 引数
（759〜774）になっているため正規表現は 16 引数版）：
  1. PacketIds.java のエントリ（名前・状態・方向・759〜774のID）を
     読む。
  2. 各エントリについて、minecraft-data の protocol.json（1.20／1.20.2／
     1.20.3／1.20.5／1.21.1／1.21.3／1.21.4／1.21.5／1.21.6／1.21.8）の同じ状態・方向の mapper 表から、10つのIDの組と
     一致する内部名を1件だけ求める。既存の12つの値がすべて再現できる
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
  3. その内部名で 26.1 の protocol.json を引き、775のIDを取る。
     775に無い内部名は -1（削除されたパケット）。
     26.1／26.1.1／26.1.2 の protocol は pc/26.1 で 775 なので pc/26.1 の
     protocol.json を使う（dataPaths.json で確認済み）。
  4. 775にあって表に無いパケットも一覧に出す（新規パケットの候補。
     Ping Request など意図的に入れないものはここで除外する）。

使い方：
  python3 tools/packet-ids-775.py [--data-dir DIR] [--out FILE]
  --data-dir を省略すると detergentmc-protocol/build/remap-data を使う。
  --out を省略すると detergentmc-protocol/build/remap-data/packet-ids-775.json
  に書き出す。終了コード 0 = 全件一意に写像できた。
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
    """PacketIds.java の enum エントリを抜き出す（16引数版：759〜774）。"""
    id_pat = r"(-1|0x[0-9a-fA-F]+)"
    pattern = re.compile(
        r"(\w+)\(State\.(\w+), Direction\.(\w+), "
        + ", ".join([id_pat] * 16) + r"\)[,;]")
    entries = []
    for m in pattern.finditer(java_text):
        groups = m.groups()
        name, state, direction = groups[0], groups[1], groups[2]
        ids = [int(g, 0) for g in groups[3:]]
        entries.append({
            "name": name, "state": state, "direction": direction,
            "id759": ids[0], "id760": ids[1], "id761": ids[2],
            "id762": ids[3], "id763": ids[4], "id764": ids[5],
            "id765": ids[6], "id766": ids[7], "id767": ids[8],
            "id768": ids[9], "id769": ids[10], "id770": ids[11],
            "id771": ids[12], "id772": ids[13], "id773": ids[14],
            "id774": ids[15],
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
                         ("768", "mc-1.21.3-protocol.json"),
                         ("769", "mc-1.21.4-protocol.json"),
                         ("770", "mc-1.21.5-protocol.json"),
                         ("771", "mc-1.21.6-protocol.json"),
                         ("772", "mc-1.21.8-protocol.json"),
                         ("773", "mc-1.21.9-protocol.json"),
                         ("774", "mc-1.21.11-protocol.json"),
                         ("775", "mc-26.1-protocol.json")):
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
                 "766": e["id766"], "767": e["id767"], "768": e["id768"],
                 "769": e["id769"], "770": e["id770"], "771": e["id771"],
                 "772": e["id772"], "773": e["id773"], "774": e["id774"]}
        # 内部名の候補：存在するバージョンのIDがすべて一致するもの。
        candidates = set()
        has_any = False
        for v in ("763", "764", "765", "766", "767", "768", "769", "770", "771", "772", "773", "774"):
            if quint[v] < 0:
                continue
            has_any = True
            table = versions[v][state][direction]
            hits = {n for n, i in table.items() if i == quint[v]}
            candidates = hits if not candidates else candidates & hits
        if not has_any:
            # 763〜774 で全て -1 のためIDから内部名を特定できないエントリ。
            # 内部名は packet-ids-760.py の記録（760だけにあるパケット）。
            # 775 の protocol.json にも無いことを確認して id775 = -1 とする。
            old_only = {
                "PLAY_MESSAGE_HEADER": "message_header",
                "PLAY_SHOULD_DISPLAY_CHAT_PREVIEW": "should_display_chat_preview",
                "PLAY_CHAT_PREVIEW": "chat_preview",
                "PLAY_NAMED_SOUND_EFFECT": "named_sound_effect",
                "PLAY_CHAT_PREVIEW_TO_SERVER": "chat_preview",
            }
            mc_name = old_only.get(e["name"])
            if mc_name is None:
                problems.append(f"{e['name']}: 763〜774 で全て -1 だが既知の旧専用パケットではない")
                continue
            if mc_name in versions["775"][state][direction]:
                problems.append(f"{e['name']}: {mc_name} が 775 にあるのに旧専用扱いになっている")
                continue
            result[e["name"]] = {
                "mc": mc_name, "state": e["state"], "direction": e["direction"],
                "id759": e["id759"], "id760": e["id760"], "id761": e["id761"],
                "id762": e["id762"], "id763": e["id763"], "id764": e["id764"],
                "id765": e["id765"], "id766": e["id766"], "id767": e["id767"],
                "id768": e["id768"], "id769": e["id769"], "id770": e["id770"],
                "id771": e["id771"], "id772": e["id772"], "id773": e["id773"],
                "id774": e["id774"], "id775": -1,
            }
            continue
        if len(candidates) != 1:
            problems.append(
                f"{e['name']} ({e['state']}/{e['direction']} "
                f"763={e['id763']} 764={e['id764']} 765={e['id765']} 766={e['id766']} "
                f"767={e['id767']} 768={e['id768']} 769={e['id769']} 770={e['id770']} "
                f"771={e['id771']} 772={e['id772']} 773={e['id773']} 774={e['id774']}): 候補 {sorted(candidates)}")
            continue
        mc_name = candidates.pop()
        id775 = versions["775"][state][direction].get(mc_name, -1)
        result[e["name"]] = {
            "mc": mc_name, "state": e["state"], "direction": e["direction"],
            "id759": e["id759"], "id760": e["id760"], "id761": e["id761"],
            "id762": e["id762"], "id763": e["id763"], "id764": e["id764"],
            "id765": e["id765"], "id766": e["id766"], "id767": e["id767"],
            "id768": e["id768"], "id769": e["id769"], "id770": e["id770"],
            "id771": e["id771"], "id772": e["id772"], "id773": e["id773"],
            "id774": e["id774"], "id775": id775,
        }

    print(f"一意に写像できた: {len(result)} 件")
    removed = [n for n, r in result.items() if r["id775"] < 0]
    print(f"775で削除（-1）: {removed}")
    changed = [n for n, r in result.items()
               if r["id775"] >= 0 and r["id775"] != r["id774"]]
    print(f"774とIDが違う: {len(changed)} 件")
    for n in changed:
        r = result[n]
        print(f"  {n}: 774={r['id774']:#04x} -> 775={r['id775']:#04x} ({r['mc']})")

    # 775にあって表に無いパケット（新規パケットの候補）。
    # status 状態はこの表の対象外（PacketIds の javadoc 参照）のため除く。
    known_mc = {(r["state"], r["direction"], r["mc"]) for r in result.values()}
    new_packets = []
    state_names = {"handshaking": "HANDSHAKING", "login": "LOGIN",
                   "configuration": "CONFIGURATION", "play": "PLAY"}
    dir_names = {"toClient": "CLIENTBOUND", "toServer": "SERVERBOUND"}
    for state, dirs in versions["775"].items():
        if state not in state_names:
            continue
        for direction, table in dirs.items():
            for mc_name, i in sorted(table.items(), key=lambda kv: kv[1]):
                key = (state_names[state], dir_names[direction], mc_name)
                if key not in known_mc:
                    new_packets.append((key[0], key[1], mc_name, i))
    print(f"775にあって表に無いパケット: {len(new_packets)} 件")
    for state, direction, mc_name, i in new_packets:
        print(f"  {state}/{direction} {mc_name} = {i:#04x}")

    out_path = args.out or args.data_dir / "packet-ids-775.json"
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
