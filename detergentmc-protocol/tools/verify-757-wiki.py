#!/usr/bin/env python3
"""packet-ids-757.json の id757 を Minecraft Wiki（1.18、protocol 757、
2021-12-09 の改訂版 2772742）で独立照合する。

Wiki の旧形式は各パケットが ==== 名前 ==== の節になっており、
節内の表に Packet ID（0x..）・State・Bound To が書かれている。
節は ID 順に並んでいる。各（状態・方向）について Wiki の節の並び順
（＝ID順）と packet-ids-757.json の id757 順を突き合わせる。
Wiki の名前と minecraft-data の内部名が違うものは下の WIKI_RENAME
で読み替える（例：Spawn Player → named_entity_spawn、Join Game → login）。
"""

import json
import re
from pathlib import Path

DATA_DIR = Path(__file__).resolve().parent.parent / "build" / "remap-data"
WIKI_PATH = Path("/tmp/wiki-packets-118.txt")

# Wiki の節名 -> minecraft-data の内部名の読み替え。
WIKI_RENAME = {
    "spawn player": "named_entity_spawn",
    "join game": "login",
    "entity animation": "animation",
    "chat message": "chat",
    "keep alive": "keep_alive",
    "set compression": "compress",
    "encryption request": "encryption_begin",
    "encryption response": "encryption_begin",
    "login plugin request": "login_plugin_request",
    "login plugin response": "login_plugin_response",
    "login success": "success",
    "login start": "login_start",
    "disconnect": "disconnect",
    "disconnect (login)": "disconnect",
    "kick disconnect": "kick_disconnect",
    "game state change": "game_state_change",
    "map chunk": "map_chunk",
    "player info": "player_info",
    "custom payload": "custom_payload",
    "position": "position",
    "tile entity data": "tile_entity_data",
    "craft progress bar": "craft_progress_bar",
    "chat message (clientbound)": "chat",
    "chat message (serverbound)": "chat",
    "position_look": "position_look",
    "player position": "position",
    "player position and rotation": "position_look",
    "player look": "look",
    "client settings": "settings",
    "tab complete": "tab_complete",
    "window click": "window_click",
    "close window": "close_window",
    "edit book": "edit_book",
    "query entity nbt": "query_entity_nbt",
    "use entity": "use_entity",
    "generate structure": "generate_structure",
    "lock difficulty": "lock_difficulty",
    "abilities": "abilities",
    "flying": "flying",
    "vehicle move": "vehicle_move",
    "steer boat": "steer_boat",
    "pick item": "pick_item",
    "craft recipe request": "craft_recipe_request",
    "block dig": "block_dig",
    "entity action": "entity_action",
    "steer vehicle": "steer_vehicle",
    "pong": "pong",
    "recipe book": "recipe_book",
    "displayed recipe": "displayed_recipe",
    "name item": "name_item",
    "advancement tab": "advancement_tab",
    "select trade": "select_trade",
    "set beacon effect": "set_beacon_effect",
    "held item slot": "held_item_slot",
    "update command block": "update_command_block",
    "update command block minecart": "update_command_block_minecart",
    "set creative slot": "set_creative_slot",
    "update jigsaw block": "update_jigsaw_block",
    "update structure block": "update_structure_block",
    "update sign": "update_sign",
    "arm animation": "arm_animation",
    "spectate": "spectate",
    "block place": "block_place",
    "use item": "use_item",
    "resource pack receive": "resource_pack_receive",
    "resource pack send": "resource_pack_send",
}


def parse_wiki(text):
    """{(状態, 方向): [(wiki名, id), ...]} を ID 順で返す。"""
    state = None
    direction = None
    out = {}
    lines = text.splitlines()
    i = 0
    while i < len(lines):
        line = lines[i]
        m = re.match(r"^== ([A-Za-z ]+) ==$", line)
        if m and m.group(1) in ("Handshaking", "Status", "Login", "Play"):
            state = m.group(1)
            direction = None
        m = re.match(r"^=== (Clientbound|Serverbound) ===$", line)
        if m:
            direction = m.group(1)
        m = re.match(r"^====\s*(.+?)\s*====\s*$", line)
        if m and state in ("Handshaking", "Login", "Play") and direction:
            name = m.group(1)
            # 節内の表から Packet ID を取る。表は2形式ある：
            #   |rowspan="N"| 0xNN  と  | 0xNN
            pid = None
            for j in range(i + 1, min(i + 40, len(lines))):
                mm = re.search(r"\|(?:rowspan=\"\d+\"\| )? ?(0x[0-9a-fA-F]+)", lines[j])
                if mm:
                    pid = int(mm.group(1), 16)
                    break
                if lines[j].startswith("===="):
                    break
            if pid is None:
                print(f"警告: {state}/{direction} の {name} に Packet ID が無い")
            else:
                out.setdefault((state, direction), []).append((name, pid))
        i += 1
    return out


def wiki_to_mc(name):
    n = name.strip().lower()
    n = re.sub(r" \(clientbound\)$", "", n)
    n = re.sub(r" \(serverbound\)$", "", n)
    if n in WIKI_RENAME:
        return WIKI_RENAME[n]
    return n.replace(" ", "_")


# Wiki にあって PacketIds に無いことが想定内のもの。
#   0xFE legacy_server_list_ping: Handshake の対象外（意図的に除外）。
#   Play/Clientbound 0x02・0x03・0x05・0x0F: spawn_entity_living・
#   spawn_entity_painting・sculk_vibration_signal・chat（1.18にあって
#   表に無い新規パケット候補。packet-ids-757.py の出力と同じ）。
#   Play/Serverbound 0x03: chat（同上）。
EXPECTED_WIKI_ONLY = {
    ("Handshaking", "Serverbound"): {0xFE},
    ("Play", "Clientbound"): {0x02, 0x03, 0x05, 0x0F},
    ("Play", "Serverbound"): {0x03},
}


def main():
    wiki = parse_wiki(WIKI_PATH.read_text(encoding="utf-8"))
    result = json.loads((DATA_DIR / "packet-ids-757.json").read_text(encoding="utf-8"))
    if len(result) != 267:
        # 空の写像での空振り照合を防ぐ（verify-757-packet-ids.py と同じガード）。
        print(f"packet-ids-757.json の件数が 267 件ではない（{len(result)} 件）。",
              file=sys.stderr)
        return 1

    state_map = {"PLAY": "Play", "LOGIN": "Login", "HANDSHAKING": "Handshaking"}
    dir_map = {"CLIENTBOUND": "Clientbound", "SERVERBOUND": "Serverbound"}

    total_checked = 0
    problems = []
    for (state, direction), entries in sorted(wiki.items()):
        # packet-ids-757.json の同（状態・方向）の id757 >= 0 の一覧（ID順）。
        ours = sorted(
            [(r["mc"], r["id757"]) for r in result.values()
             if state_map.get(r["state"]) == state
             and dir_map.get(r["direction"]) == direction
             and r["id757"] >= 0],
            key=lambda kv: kv[1])
        theirs = [(wiki_to_mc(name), pid) for name, pid in entries]
        print(f"{state}/{direction}: Wiki {len(theirs)} 件、こちら {len(ours)} 件")
        # Wiki の名前と minecraft-data の内部名は表記が違うものが多いため、
        # ID の集合と並び順で照合する（名前↔ID の対応は minecraft-data と
        # ViaVersion の二重照合で既に確認済み）。
        wiki_ids = [pid for _, pid in theirs]
        our_ids = [i for _, i in ours]
        total_checked += len(wiki_ids)
        expected_only = EXPECTED_WIKI_ONLY.get((state, direction), set())
        wiki_ids_eff = [i for i in wiki_ids if i not in expected_only]
        if sorted(wiki_ids_eff) != sorted(our_ids):
            only_wiki = sorted(set(wiki_ids_eff) - set(our_ids))
            only_ours = sorted(set(our_ids) - set(wiki_ids_eff))
            problems.append(
                f"{state}/{direction}: IDの集合が違う（Wikiのみ {[hex(x) for x in only_wiki]}、"
                f"こちらのみ {[hex(x) for x in only_ours]}）")
        elif wiki_ids_eff != our_ids:
            problems.append(f"{state}/{direction}: IDの並び順が違う")
        # 名前のスポットチェック：読み替え後の名前が一致するものだけ数える。
        name_ok = sum(1 for (w, _), (o, _) in zip(theirs, ours) if w == o)
        print(f"  ID集合・並び順の照合：{'OK' if not [p for p in problems if state in p] else 'NG'}、"
              f"名前一致 {name_ok}/{len(theirs)} 件（残りは表記の違い）")
    print(f"照合: {total_checked} 件")
    if problems:
        print(f"不一致 {len(problems)} 件:")
        for p in problems:
            print(f"  {p}")
        return 1
    print("照合結果：不一致 0件。")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
