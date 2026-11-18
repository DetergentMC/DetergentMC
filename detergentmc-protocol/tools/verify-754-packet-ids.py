#!/usr/bin/env python3
"""packet-ids-754.json の id754 を ViaVersion の 1.16.2 enum で独立照合する。

方法（verify-755-packet-ids.py と同じ）：
  1. mc-1.17-protocol.json と ViaVersion 1.17 enum を状態・方向・ID で
     突き合わせて、minecraft-data 内部名 -> ViaVersion enum 名の変換表
     を作る（両表の件数が一致し ID が一意なため全単射）。
  2. その変換表で packet-ids-754.json の各エントリの内部名を enum 名に
     変換し、ViaVersion 1.16.2 enum（クライアント行きは
     ClientboundPackets1_16_2、サーバー行きは ServerboundPackets1_16_2）
     の値を引いて id754 と突き合わせる。
  3. ViaVersion 1.16.2 enum の全エントリが表の id754 にあることも確認する
     （1.16.5にあって表に無いパケットが無いこと。MOVE_ENTITY 0x2a だけは
     1.17 で削除され PacketIds にエントリが無いため想定内の例外）。

ViaVersion の enum は宣言順が ID 順（getId() は ordinal()）。
コメントの 0x.. と宣言順が違う場合は警告を出し、宣言順を採用する。

ViaVersion の enum ファイルは
detergentmc-protocol/build/remap-data/via-version/ に複写してある
（ClientboundPackets1_16_2.java・ServerboundPackets1_16_2.java・
ClientboundPackets1_17.java・ServerboundPackets1_17.java・
ログイン base enum 2件・Protocol1_16_4To1_17.java）。
所在は Protocol1_16_4To1_17.java で確認した
（1.16.4 側は v1_16_1to1_16_2 パッケージの ClientboundPackets1_16_2／
ServerboundPackets1_16_2、1.17 側は v1_16_4to1_17 パッケージの
ClientboundPackets1_17／ServerboundPackets1_17）。
"""

import json
import re
import sys
from pathlib import Path

DATA_DIR = Path(__file__).resolve().parent.parent / "build" / "remap-data"
VV_DIR = DATA_DIR / "via-version"

STATE_MAP = {"PLAY": "play", "LOGIN": "login"}
DIR_MAP = {"CLIENTBOUND": "toClient", "SERVERBOUND": "toServer"}

VV_FILES_117 = {("play", "toClient"): "ClientboundPackets1_17.java",
                ("play", "toServer"): "ServerboundPackets1_17.java",
                ("login", "toClient"): "ClientboundLoginPackets.java",
                ("login", "toServer"): "ServerboundLoginPackets.java"}
VV_FILES_1162 = {("play", "toClient"): "ClientboundPackets1_16_2.java",
                 ("play", "toServer"): "ServerboundPackets1_16_2.java",
                 ("login", "toClient"): "ClientboundLoginPackets.java",
                 ("login", "toServer"): "ServerboundLoginPackets.java"}

# ViaVersion の enum 名の改名（1.17 と 1.16.2 で名前が違うもの）。
# minecraft-data の内部名の分割・改名に対応する（両 enum の名前一覧と
# Protocol1_16_4To1_17.java の SET_TITLES 分岐で実測）：
#   REMOVE_ENTITY → REMOVE_ENTITIES（entity_destroy の改名と同じ対応）
#   PLAYER_COMBAT_END／ENTER／KILL → PLAYER_COMBAT（combat_event の3分割）
#   SET_TITLE_TEXT／SET_SUBTITLE_TEXT／SET_TITLES_ANIMATION／
#   CLEAR_TITLES／SET_ACTION_BAR_TEXT → SET_TITLES（title の5分割）
#   INITIALIZE_BORDER／SET_BORDER_* → SET_BORDER（world_border の6分割）
#   PING → CONTAINER_ACK（クライアント行き transaction の置き換え）
#   PONG → CONTAINER_ACK（サーバー行き transaction の置き換え）
VV_RENAME_116 = {
    "REMOVE_ENTITY": "REMOVE_ENTITIES",
    "PLAYER_COMBAT_END": "PLAYER_COMBAT",
    "PLAYER_COMBAT_ENTER": "PLAYER_COMBAT",
    "PLAYER_COMBAT_KILL": "PLAYER_COMBAT",
    "SET_TITLE_TEXT": "SET_TITLES",
    "SET_SUBTITLE_TEXT": "SET_TITLES",
    "SET_TITLES_ANIMATION": "SET_TITLES",
    "CLEAR_TITLES": "SET_TITLES",
    "SET_ACTION_BAR_TEXT": "SET_TITLES",
    "INITIALIZE_BORDER": "SET_BORDER",
    "SET_BORDER_CENTER": "SET_BORDER",
    "SET_BORDER_LERP_SIZE": "SET_BORDER",
    "SET_BORDER_SIZE": "SET_BORDER",
    "SET_BORDER_WARNING_DELAY": "SET_BORDER",
    "SET_BORDER_WARNING_DISTANCE": "SET_BORDER",
    "PING": "CONTAINER_ACK",
    "PONG": "CONTAINER_ACK",
}

# ViaVersion 1.16.2 enum にあって表の id754 に無いことが想定内のもの。
#   MOVE_ENTITY 0x2a（クライアント行き）：1.17 で削除されたパケットで
#   PacketIds のどのバージョンにもエントリが無い。
#   ViaVersion の Protocol1_16_4To1_17 でも未登録（落とされる）。
EXPECTED_VV_ONLY = {
    ("play", "toClient"): {0x2A},
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
    mc117 = load_mappings(DATA_DIR / "mc-1.17-protocol.json")
    result = json.loads((DATA_DIR / "packet-ids-754.json").read_text(encoding="utf-8"))
    if len(result) != 272:
        # 空の写像（packet-ids-754.py を 21引数版の PacketIds.java に対して
        # 誤って実行した場合など）での空振り照合を防ぐ。
        print(f"packet-ids-754.json の件数が 272 件ではない（{len(result)} 件）。",
              file=sys.stderr)
        return 1

    vv117 = {k: parse_vv_enum(VV_DIR / f) for k, f in VV_FILES_117.items()}
    vv1162 = {k: parse_vv_enum(VV_DIR / f) for k, f in VV_FILES_1162.items()}

    # 内部名 -> enum 名の変換表（1.17 で突き合わせ）。
    mc_to_vv = {}
    for key in VV_FILES_117:
        state, direction = key
        mc_table = mc117[state][direction]
        vv_table = vv117[key]
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
            print(f"件数不一致 {key}: mc 1.17 が {len(mc_table)} 件、"
                  f"ViaVersion 1.17 が {len(vv_table)} 件", file=sys.stderr)
            return 1
        for vv_name, i in vv_table.items():
            if i not in by_id:
                print(f"ID不一致 {key}: ViaVersion の {vv_name}={i:#04x} が "
                      f"mc 1.17 に無い", file=sys.stderr)
                return 1
            mc_to_vv[(state, direction, by_id[i])] = vv_name
    print(f"内部名→enum名の変換表: {len(mc_to_vv)} 件")

    # 検証：id754 >= 0 のエントリを ViaVersion 1.16.2 enum で再現する。
    checked = 0
    mismatches = []
    skipped = []
    for name, r in sorted(result.items()):
        if r["id754"] < 0:
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
        vv_name = VV_RENAME_116.get(vv_name, vv_name)
        vv_table = vv1162.get((state, direction))
        if vv_table is None:
            skipped.append(name)
            continue
        actual = vv_table.get(vv_name)
        if actual is None:
            mismatches.append(f"{name}: {vv_name} が ViaVersion 1.16.2 enum に無いのに id754={r['id754']:#04x}")
            continue
        checked += 1
        if actual != r["id754"]:
            mismatches.append(f"{name}: ViaVersion 1.16.2 の {vv_name}={actual:#04x} が id754={r['id754']:#04x} と違う")
    print(f"ViaVersion 1.16.2 enum で照合: {checked} 件、不一致 {len(mismatches)} 件、対象外 {len(skipped)} 件")
    for m in mismatches:
        print(f"  不一致: {m}", file=sys.stderr)
    if skipped:
        print(f"  対象外（HANDSHAKING/CONFIGURATION・LOGIN）: {skipped}")

    # 逆向きの確認：ViaVersion 1.16.2 enum の全エントリが表の id754 にあること。
    # （packet-ids-754.py は 1.16.5にあって表に無いパケット 0件と報告している。
    #  ただし MOVE_ENTITY 0x2a だけは想定内の例外。）
    reverse_problems = []
    for key, vv_table in sorted(vv1162.items()):
        state, direction = key
        if state == "login":
            continue
        ours = {(r["mc"], r["id754"]) for r in result.values()
                if r["state"] == {"play": "PLAY"}[state]
                and r["direction"] == {"toClient": "CLIENTBOUND",
                                       "toServer": "SERVERBOUND"}[direction]
                and r["id754"] >= 0}
        our_ids = {i for _, i in ours}
        expected_only = EXPECTED_VV_ONLY.get((state, direction), set())
        for vv_name, i in sorted(vv_table.items(), key=lambda kv: kv[1]):
            if i not in our_ids and i not in expected_only:
                reverse_problems.append(
                    f"{state}/{direction} {vv_name}={i:#04x} が表の id754 に無い")
    print(f"逆向きの確認（ViaVersion 1.16.2 enum → 表）: 問題 {len(reverse_problems)} 件")
    for p in reverse_problems:
        print(f"  {p}", file=sys.stderr)

    # 削除パケットの確認：MOVE_ENTITY は 1.16.2 enum にあり、1.17 enum に無いこと。
    cb1162 = vv1162[("play", "toClient")]
    cb117 = vv117[("play", "toClient")]
    move_entity = cb1162.get("MOVE_ENTITY")
    print(f"MOVE_ENTITY の ViaVersion 1.16.2 での有無: "
          f"{'ある（' + hex(move_entity) + '）' if move_entity is not None else '無い'}、"
          f"1.17 での有無: {'ある' if 'MOVE_ENTITY' in cb117 else '無い（1.17 で削除と一致）'}")
    if move_entity is None or "MOVE_ENTITY" in cb117:
        reverse_problems.append("MOVE_ENTITY の有無が想定と違う")

    if mismatches or reverse_problems:
        return 1
    print("照合結果：不一致 0件。")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
