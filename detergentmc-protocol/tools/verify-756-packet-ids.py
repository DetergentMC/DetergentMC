#!/usr/bin/env python3
"""packet-ids-756.json の id756 を ViaVersion の 1.17.1 enum で独立照合する。

方法（verify-757-packet-ids.py と同じ）：
  1. mc-1.18-protocol.json と ViaVersion 1.18 enum を状態・方向・ID で
     突き合わせて、minecraft-data 内部名 -> ViaVersion enum 名の変換表
     を作る（両表の件数が一致し ID が一意なため全単射）。
  2. その変換表で packet-ids-756.json の各エントリの内部名を enum 名に
     変換し、ViaVersion 1.17.1 enum（クライアント行きは
     ClientboundPackets1_17_1、サーバー行きは 1.17.1 が流用する
     ServerboundPackets1_17）の値を引いて id756 と突き合わせる。
  3. ViaVersion 1.17.1 enum の全エントリが表の id756 にあることも確認する
     （1.17.1にあって表に無いパケットが無いこと）。

ViaVersion の enum は宣言順が ID 順（getId() は ordinal()）。
コメントの 0x.. と宣言順が違う場合は警告を出し、宣言順を採用する。

ViaVersion の enum ファイルは
detergentmc-protocol/build/remap-data/via-version/ に複写してある
（ClientboundPackets1_17_1.java・ServerboundPackets1_17.java・
ClientboundPackets1_18.java・ログイン base enum 2件）。
所在は Protocol1_17To1_17_1.java で確認した
（クライアント行きは ClientboundPackets1_17_1、サーバー行きは
ServerboundPackets1_17 を 1.17 と 1.17.1 が共用）。
"""

import json
import re
import sys
from pathlib import Path

DATA_DIR = Path(__file__).resolve().parent.parent / "build" / "remap-data"
VV_DIR = DATA_DIR / "via-version"

STATE_MAP = {"PLAY": "play", "LOGIN": "login"}
DIR_MAP = {"CLIENTBOUND": "toClient", "SERVERBOUND": "toServer"}

VV_FILES_118 = {("play", "toClient"): "ClientboundPackets1_18.java",
                # 1.18 のサーバー行きは ViaVersion が 1.17 の enum を流用
                # （Protocol1_17_1To1_18.java で確認。757の前例）。
                ("play", "toServer"): "ServerboundPackets1_17.java",
                # ログインは ViaVersion が base の enum を全バージョンで流用。
                # ただし現行 master の base enum には 1.20.2 以降の
                # COOKIE_REQUEST 等が追加されているため、件数は mc 1.18
                # （toClient 5件・toServer 3件）と一致しない。
                # ID の並びは 0x00〜0x04 で一致するため、ID で突き合わせる。
                ("login", "toClient"): "ClientboundLoginPackets.java",
                ("login", "toServer"): "ServerboundLoginPackets.java"}
VV_FILES_1171 = {("play", "toClient"): "ClientboundPackets1_17_1.java",
                 # 1.17.1 のサーバー行きは ViaVersion が 1.17 の enum を流用
                 # （Protocol1_17To1_17_1.java で確認）。
                 ("play", "toServer"): "ServerboundPackets1_17.java",
                 ("login", "toClient"): "ClientboundLoginPackets.java",
                 ("login", "toServer"): "ServerboundLoginPackets.java"}

# ViaVersion の enum 名の改名（1.17.1 と 1.18 で名前が違うもの）。
# minecraft-data の内部名は 1.17.1・1.18 で同じ map_chunk だが、
# ViaVersion は 1.18 で LEVEL_CHUNK を LEVEL_CHUNK_WITH_LIGHT に
# 改名している。1.17.1 enum での照合時に読み替える。
# （両 enum の名前一覧を突き合わせて実測で確認。ほかに名前の違いは無い。
#  SET_SIMULATION_DISTANCE は 1.17.1 に無く、0x58 以降は -1 ずれる。）
VV_RENAME_1171 = {"LEVEL_CHUNK_WITH_LIGHT": "LEVEL_CHUNK"}


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


def parse_vv_enum(path):
    text = Path(path).read_text(encoding="utf-8")
    found = re.findall(
        r"^\s+([A-Z][A-Z0-9_]*)\s*[,;]\s*//\s*0x([0-9a-fA-F]+)",
        text, re.M)
    names = [n for n, _ in found]
    commented = [int(h, 16) for _, h in found]
    for i, (name, c) in enumerate(zip(names, commented)):
        if c != i:
            print(f"警告: {path} の {name} はコメント 0x{c:02X} だが宣言順は "
                  f"{i} (0x{i:02X}）。宣言順を採用する。", file=sys.stderr)
    return {name: i for i, name in enumerate(names)}


def main():
    mc118 = load_mappings(DATA_DIR / "mc-1.18-protocol.json")
    result = json.loads((DATA_DIR / "packet-ids-756.json").read_text(encoding="utf-8"))
    if len(result) != 272:
        # 空の写像（packet-ids-756.py を 19引数版の PacketIds.java に対して
        # 誤って実行した場合など）での空振り照合を防ぐ。
        print(f"packet-ids-756.json の件数が 272 件ではない（{len(result)} 件）。",
              file=sys.stderr)
        return 1

    vv118 = {k: parse_vv_enum(VV_DIR / f) for k, f in VV_FILES_118.items()}
    vv1171 = {k: parse_vv_enum(VV_DIR / f) for k, f in VV_FILES_1171.items()}

    # 内部名 -> enum 名の変換表（1.18 で突き合わせ）。
    mc_to_vv = {}
    for key in VV_FILES_118:
        state, direction = key
        mc_table = mc118[state][direction]
        vv_table = vv118[key]
        by_id = {}
        for mc_name, i in mc_table.items():
            by_id[i] = mc_name
        if state == "login":
            # ログインは件数が一致しない（base enum に 1.20.2 以降の
            # 追加がある）ため、ID で突き合わせる。
            for vv_name, i in vv_table.items():
                if i in by_id:
                    mc_to_vv[(state, direction, by_id[i])] = vv_name
            continue
        if len(mc_table) != len(vv_table):
            print(f"件数不一致 {key}: mc 1.18 が {len(mc_table)} 件、"
                  f"ViaVersion 1.18 が {len(vv_table)} 件", file=sys.stderr)
            return 1
        for vv_name, i in vv_table.items():
            if i not in by_id:
                print(f"ID不一致 {key}: ViaVersion の {vv_name}={i:#04x} が "
                      f"mc 1.18 に無い", file=sys.stderr)
                return 1
            mc_to_vv[(state, direction, by_id[i])] = vv_name
    print(f"内部名→enum名の変換表: {len(mc_to_vv)} 件")

    # 検証：id756 >= 0 のエントリを ViaVersion 1.17.1 enum で再現する。
    checked = 0
    mismatches = []
    skipped = []
    for name, r in sorted(result.items()):
        if r["id756"] < 0:
            continue
        if r["state"] in ("HANDSHAKING", "CONFIGURATION"):
            skipped.append(name)
            continue
        state = STATE_MAP[r["state"]]
        direction = DIR_MAP[r["direction"]]
        vv_name = mc_to_vv.get((state, direction, r["mc"]))
        if vv_name is None:
            mismatches.append(f"{name}: 内部名 {r['mc']} の ViaVersion 対応が無い")
            continue
        vv_name = VV_RENAME_1171.get(vv_name, vv_name)
        vv_table = vv1171.get((state, direction))
        if vv_table is None:
            skipped.append(name)
            continue
        actual = vv_table.get(vv_name)
        if actual is None:
            mismatches.append(f"{name}: {vv_name} が ViaVersion 1.17.1 enum に無いのに id756={r['id756']:#04x}")
            continue
        checked += 1
        if actual != r["id756"]:
            mismatches.append(f"{name}: ViaVersion 1.17.1 の {vv_name}={actual:#04x} が id756={r['id756']:#04x} と違う")
    print(f"ViaVersion 1.17.1 enum で照合: {checked} 件、不一致 {len(mismatches)} 件、対象外 {len(skipped)} 件")
    for m in mismatches:
        print(f"  不一致: {m}", file=sys.stderr)
    if skipped:
        print(f"  対象外（HANDSHAKING/CONFIGURATION・LOGIN）: {skipped}")

    # 逆向きの確認：ViaVersion 1.17.1 enum の全エントリが表の id756 にあること。
    # （packet-ids-756.py は 1.17.1にあって表に無いパケット 0件と報告している。）
    reverse_problems = []
    for key, vv_table in sorted(vv1171.items()):
        state, direction = key
        if state == "login":
            continue
        ours = {(r["mc"], r["id756"]) for r in result.values()
                if r["state"] == {"play": "PLAY"}[state]
                and r["direction"] == {"toClient": "CLIENTBOUND",
                                       "toServer": "SERVERBOUND"}[direction]
                and r["id756"] >= 0}
        our_ids = {i for _, i in ours}
        for vv_name, i in sorted(vv_table.items(), key=lambda kv: kv[1]):
            if i not in our_ids:
                reverse_problems.append(
                    f"{state}/{direction} {vv_name}={i:#04x} が表の id756 に無い")
    print(f"逆向きの確認（ViaVersion 1.17.1 enum → 表）: 問題 {len(reverse_problems)} 件")
    for p in reverse_problems:
        print(f"  {p}", file=sys.stderr)

    # 削除パケットの確認：SET_SIMULATION_DISTANCE は 1.17.1 enum に無いこと。
    cb1171 = vv1171[("play", "toClient")]
    sim = cb1171.get("SET_SIMULATION_DISTANCE")
    print(f"SET_SIMULATION_DISTANCE の ViaVersion 1.17.1 での有無: "
          f"{'ある（' + hex(sim) + '）' if sim is not None else '無い（削除と一致）'}")
    if sim is not None:
        reverse_problems.append("SET_SIMULATION_DISTANCE が 1.17.1 enum にある")

    if mismatches or reverse_problems:
        return 1
    print("照合結果：不一致 0件。")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
