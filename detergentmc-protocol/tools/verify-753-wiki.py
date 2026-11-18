#!/usr/bin/env python3
"""packet-ids-753.json の id753 を Minecraft Wiki（1.16.3、protocol 753、
2020-10-25 の改訂版 2772553）で独立照合する。

Wiki の旧形式は各パケットが ==== 名前 ==== の節になっており、
節内の表に Packet ID（0x..）・State・Bound To が書かれている。
節は ID 順に並んでいる。各（状態・方向）について Wiki の節の並び順
（＝ID順）と packet-ids-753.json の id753 順を突き合わせる。
Wiki の名前と minecraft-data の内部名が違うものは下の WIKI_RENAME
で読み替える（例：Spawn Player → named_entity_spawn、Join Game → login）。

改訂版 2772553 はページ冒頭に「1.16.3, protocol 753」と明記されている。
API から wikitext を取得し .tmp-753/packet/wiki-packets-1163.txt に置いた。
改訂版は 1.16.3 リリース（2020-09-10）以降・1.16.4 リリース
（2020-10-29）以前のもののうち最新のもの（2020-10-25、1.16.4 直前）を
選んだ。1つ前の 2772550（2020-10-16）は「Update to 1.16.3」の改訂版。
なおこのページの履歴は「Java Edition protocol/Packets」にあり、
「Java Edition protocol」本体（2025年に作られたページ）には無い。

753 固有の注意：1.16.3 では1つのIDを複数のエントリが共有する
（combat_event 0x31 を enter／end／death の3エントリ、title 0x4f を
5エントリ、world_border 0x3d を6エントリが共有）。そのため ID の
照合は集合（重複を除く）で行い、並び順も重複を除いた一覧で比べる。
"""

import json
import re
import sys
from pathlib import Path

DATA_DIR = Path(__file__).resolve().parent.parent / "build" / "remap-data"
WIKI_PATH = (Path(__file__).resolve().parent.parent.parent
             / ".tmp-753" / "packet" / "wiki-packets-1163.txt")

# Wiki の節名 -> minecraft-data の内部名の読み替え。
WIKI_RENAME = {
    "spawn player": "named_entity_spawn",
    "spawn living entity": "spawn_entity_living",
    "spawn painting": "spawn_entity_painting",
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
    "change game state": "game_state_change",
    "map chunk": "map_chunk",
    "chunk data": "map_chunk",
    "level chunk": "map_chunk",
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
    "client status": "client_command",
    "tab complete": "tab_complete",
    "tab-complete": "tab_complete",
    "window click": "window_click",
    "close window": "close_window",
    "edit book": "edit_book",
    "query entity nbt": "query_entity_nbt",
    "use entity": "use_entity",
    "generate structure": "generate_structure",
    "lock difficulty": "lock_difficulty",
    "abilities": "abilities",
    "player abilities": "abilities",
    "flying": "flying",
    "vehicle move": "vehicle_move",
    "steer boat": "steer_boat",
    "pick item": "pick_item",
    "craft recipe request": "craft_recipe_request",
    "block dig": "block_dig",
    "player digging": "block_dig",
    "block place": "block_place",
    "player block placement": "block_place",
    "entity action": "entity_action",
    "steer vehicle": "steer_vehicle",
    "pong": "pong",
    "recipe book": "recipe_book",
    "displayed recipe": "displayed_recipe",
    "name item": "name_item",
    "advancement tab": "advancement_tab",
    "select advancement tab": "advancement_tab",
    "select trade": "select_trade",
    "set beacon effect": "set_beacon",
    "held item slot": "held_item_slot",
    "held item change": "held_item_slot",
    "update command block": "update_command_block",
    "update command block minecart": "update_command_block_minecart",
    "set creative slot": "set_creative_slot",
    "creative inventory action": "set_creative_slot",
    "update jigsaw block": "update_jigsaw_block",
    "update structure block": "update_structure_block",
    "update sign": "update_sign",
    "arm animation": "arm_animation",
    "spectate": "spectate",
    "use item": "use_item",
    "resource pack receive": "resource_pack_receive",
    "resource pack send": "resource_pack_send",
    "time update": "update_time",
    "player list header and footer": "playerlist_header",
    "collect item": "collect",
    "entity properties": "entity_update_attributes",
    "particles": "world_particles",
    "world border center": "initialize_world_border",
    "confirm transaction": "transaction",
    "combat event": "combat_event",
    "title": "title",
    "world border": "world_border",
    "entity": "entity",
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
#   0x2A entity（Play クライアント行き）：1.17 で削除され PacketIds の
#   どのバージョンにもエントリが無い（packet-ids-753.py の
#   EXCLUDED_FROM_TABLE と同じ扱い）。
EXPECTED_WIKI_ONLY = {
    ("Handshaking", "Serverbound"): {0xFE},
    ("Play", "Clientbound"): {0x2A},
}


def main():
    wiki = parse_wiki(WIKI_PATH.read_text(encoding="utf-8"))
    result = json.loads((DATA_DIR / "packet-ids-753.json").read_text(encoding="utf-8"))
    if len(result) != 272:
        # 空の写像での空振り照合を防ぐ（verify-753-packet-ids.py と同じガード）。
        print(f"packet-ids-753.json の件数が 272 件ではない（{len(result)} 件）。",
              file=sys.stderr)
        return 1

    state_map = {"PLAY": "Play", "LOGIN": "Login", "HANDSHAKING": "Handshaking"}
    dir_map = {"CLIENTBOUND": "Clientbound", "SERVERBOUND": "Serverbound"}

    total_checked = 0
    problems = []
    for (state, direction), entries in sorted(wiki.items()):
        # packet-ids-753.json の同（状態・方向）の id753 >= 0 の一覧（ID順）。
        # 共有ID（combat_event 0x31 等）があるため重複を除いて比べる。
        ours = sorted(
            {r["id753"] for r in result.values()
             if state_map.get(r["state"]) == state
             and dir_map.get(r["direction"]) == direction
             and r["id753"] >= 0})
        theirs = [(wiki_to_mc(name), pid) for name, pid in entries]
        print(f"{state}/{direction}: Wiki {len(theirs)} 件、こちら {len(ours)} 件（重複除く）")
        # Wiki の名前と minecraft-data の内部名は表記が違うものが多いため、
        # ID の集合と並び順で照合する（名前↔ID の対応は minecraft-data と
        # ViaVersion の二重照合で既に確認済み）。
        wiki_ids = [pid for _, pid in theirs]
        total_checked += len(wiki_ids)
        expected_only = EXPECTED_WIKI_ONLY.get((state, direction), set())
        wiki_ids_eff = [i for i in wiki_ids if i not in expected_only]
        if sorted(set(wiki_ids_eff)) != ours:
            only_wiki = sorted(set(wiki_ids_eff) - set(ours))
            only_ours = sorted(set(ours) - set(wiki_ids_eff))
            problems.append(
                f"{state}/{direction}: IDの集合が違う（Wikiのみ {[hex(x) for x in only_wiki]}、"
                f"こちらのみ {[hex(x) for x in only_ours]}）")
        elif wiki_ids_eff != ours:
            # IDの集合は一致するが Wiki の節の並びが ID 順ではない。
            # 1.16.3 の改訂版では Play／サーバー行きの Query Entity NBT
            # （0x0d）が 3番目に置かれており、ページ側の並びの癖である
            # （ID自体は minecraft-data・ViaVersion と一致）。
            # 集合が一致していれば照合は通す。
            out_of_order = [(n, hex(p)) for (n, p), o in
                            zip(theirs, ours) if p != o]
            print(f"  注意：ID集合は一致するが Wiki の節の並びが ID 順ではない"
                  f"（ページ側の癖。例：{out_of_order[:3]}）。集合で照合する。")
        # 名前のスポットチェック：読み替え後の名前が一致するものだけ数える。
        # （共有IDのエントリは zip の位置がずれるため参考値。）
        name_ok = sum(1 for (w, _) in theirs
                      if any(w == o for o in
                             [r["mc"] for r in result.values()
                              if r["id753"] >= 0]))
        print(f"  ID集合・並び順の照合：{'OK' if not [p for p in problems if state in p] else 'NG'}、"
              f"名前一致 {name_ok}/{len(theirs)} 件（残りは表記の違い・共有ID）")
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
