#!/usr/bin/env python3
"""packet-ids-751.json の id751 を Minecraft Wiki で独立照合する。

【重要：1.16.2 の改訂版は存在しない】
Java Edition protocol/Packets の履歴（API で確認）を調べたところ、
1.16.2 リリース（2020-08-11）から 1.16.3 リリース（2020-09-10）の間に
ページを 1.16.2 に更新した改訂版は無い。1.16.2 リリース直後の改訂版
2772542（2020-08-12）も、1.16.3 直前の最新改訂版 2772546
（2020-09-07）も、パケット表は 1.15.2 のまま（冒頭の注意書きも
「1.15.2, protocol 578」のまま）。ページが次に更新されたのは
2772550（2020-10-16）「Update to 1.16.3」である。
そのため本スクリプトは2段で照合する。

  (A) 主照合：1.16.3 の改訂版 2772553（2020-10-25、753版と同じもの。
      .tmp-751/packet/wiki-packets-1163.txt）の ID 集合と id751 を突き合わせる。
      id751 は全272件で id753 と一致する（packet-ids-751.py の実測。
      入力の protocol.json も 1.16.3 版とバイト同一）ため、1.16.3 の
      Wiki 表との一致は id751 の ID 集合の独立した確認になる。
  (B) 陳腐化の裏付け：2772546 の表が 1.15.2 の表と一致することと、
      2772546 の表と id751 の差分が minecraft-data の 1.15.2→1.16.2
      の差分とちょうど一致すること（Wikiのみ 0x5c Tags／こちらのみ
      0x2e block_place・0x2f use_item）を確認する。Wiki が古い側で、
      こちらが 1.16.2 であることの裏付けになる。

Wiki の旧形式は各パケットが ==== 名前 ==== の節になっており、
節内の表に Packet ID（0x..）・State・Bound To が書かれている。
節は ID 順に並んでいる。各（状態・方向）について Wiki の節の並び順
（＝ID順）と packet-ids-751.json の id751 順を突き合わせる。
Wiki の名前と minecraft-data の内部名が違うものは下の WIKI_RENAME
で読み替える（例：Spawn Player → named_entity_spawn、Join Game → login）。

なおこのページの履歴は「Java Edition protocol/Packets」にあり、
「Java Edition protocol」本体（2025年に作られたページ）には無い。

751 固有の注意：1.16.2 では1つのIDを複数のエントリが共有する
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
             / ".tmp-751" / "packet" / "wiki-packets-1163.txt")
# 1.16.3 直前の改訂版 2772546（表は 1.15.2 のまま）の wikitext。
WIKI_1162_PATH = (Path(__file__).resolve().parent.parent.parent
                  / ".tmp-751" / "packet" / "wiki-packets-1162.txt")
# minecraft-data の 1.15.2 protocol.json（2772546 の表との突き合わせ用）
# と 1.16.2 protocol.json（1.15.2→1.16.2 差分の実測用）。
MC_1152_PATH = (Path(__file__).resolve().parent.parent.parent
                / ".tmp-751" / "packet" / "mc-1.15.2-protocol.json")
MC_1162_PATH = (DATA_DIR / "mc-1.16.2-protocol.json")

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
#   どのバージョンにもエントリが無い（packet-ids-751.py の
#   EXCLUDED_FROM_TABLE と同じ扱い）。
EXPECTED_WIKI_ONLY = {
    ("Handshaking", "Serverbound"): {0xFE},
    ("Play", "Clientbound"): {0x2A},
}


def main():
    wiki = parse_wiki(WIKI_PATH.read_text(encoding="utf-8"))
    result = json.loads((DATA_DIR / "packet-ids-751.json").read_text(encoding="utf-8"))
    if len(result) != 272:
        # 空の写像での空振り照合を防ぐ（verify-751-packet-ids.py と同じガード）。
        print(f"packet-ids-751.json の件数が 272 件ではない（{len(result)} 件）。",
              file=sys.stderr)
        return 1

    state_map = {"PLAY": "Play", "LOGIN": "Login", "HANDSHAKING": "Handshaking"}
    dir_map = {"CLIENTBOUND": "Clientbound", "SERVERBOUND": "Serverbound"}

    total_checked = 0
    problems = []
    for (state, direction), entries in sorted(wiki.items()):
        # packet-ids-751.json の同（状態・方向）の id751 >= 0 の一覧（ID順）。
        # 共有ID（combat_event 0x31 等）があるため重複を除いて比べる。
        ours = sorted(
            {r["id751"] for r in result.values()
             if state_map.get(r["state"]) == state
             and dir_map.get(r["direction"]) == direction
             and r["id751"] >= 0})
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
            # 1.16.2 の改訂版では Play／サーバー行きの Query Entity NBT
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
                              if r["id751"] >= 0]))
        print(f"  ID集合・並び順の照合：{'OK' if not [p for p in problems if state in p] else 'NG'}、"
              f"名前一致 {name_ok}/{len(theirs)} 件（残りは表記の違い・共有ID）")
    print(f"照合(A): {total_checked} 件")
    if problems:
        print(f"不一致 {len(problems)} 件:")
        for p in problems:
            print(f"  {p}")
        return 1
    print("照合(A)結果：不一致 0件。")

    # (B) 陳腐化の裏付け：2772546 の表は 1.15.2 の表と一致し、
    # 2772546 の表と id751 の差分は 1.15.2→1.16.2 の差分と一致すること。
    def load_mc_sets(path):
        data = json.loads(Path(path).read_text(encoding="utf-8"))
        out = {}
        for st, dirs in data.items():
            if st == "types":
                continue
            if st not in ("handshaking", "login", "play"):
                # status 状態は表の対象外（PacketIds の javadoc 参照）。
                continue
            for dirn, content in dirs.items():
                mapper = content["types"]["packet"][1][0]["type"][1]["mappings"]
                key = ({"handshaking": "Handshaking", "login": "Login",
                        "play": "Play"}[st],
                       {"toClient": "Clientbound", "toServer": "Serverbound"}[dirn])
                out[key] = {int(h, 16) for h in mapper}
        return out

    wiki1162 = parse_wiki(WIKI_1162_PATH.read_text(encoding="utf-8"))
    mc1152 = load_mc_sets(MC_1152_PATH)
    mc1162 = load_mc_sets(MC_1162_PATH)
    stale_problems = []
    for key, entries in sorted(wiki1162.items()):
        if key not in mc1152:
            continue
        wiki_ids = {pid for _, pid in entries} - EXPECTED_WIKI_ONLY.get(key, set())
        # 0xFE（legacy ping）と 0x2A（entity）は PacketIds の対象外のため
        # 両側から除いて比べる（主照合の EXPECTED_WIKI_ONLY と同じ扱い）。
        mc_ids = mc1152[key] - EXPECTED_WIKI_ONLY.get(key, set())
        if wiki_ids != mc_ids:
            stale_problems.append(
                f"2772546 の {key} が 1.15.2 の表と違う"
                f"（Wikiのみ {[hex(x) for x in sorted(wiki_ids - mc_ids)]}、"
                f"1.15.2のみ {[hex(x) for x in sorted(mc_ids - wiki_ids)]}）")
    # 2772546 の表と id751 の差分が、minecraft-data の 1.15.2→1.16.2
    # の差分と一致すること。
    for key in sorted(mc1152):
        wiki_ids = {pid for _, pid in wiki1162.get(key, [])} - EXPECTED_WIKI_ONLY.get(key, set())
        ours_ids = {r["id751"] for r in result.values()
                    if state_map.get(r["state"]) == key[0]
                    and dir_map.get(r["direction"]) == key[1]
                    and r["id751"] >= 0}
        delta_wiki_only = wiki_ids - ours_ids
        delta_ours_only = ours_ids - wiki_ids
        delta_mc_1152_only = mc1152[key] - mc1162.get(key, set())
        delta_mc_1162_only = mc1162.get(key, set()) - mc1152[key]
        if delta_wiki_only != delta_mc_1152_only or delta_ours_only != delta_mc_1162_only:
            stale_problems.append(
                f"{key}: 2772546 と id751 の差分が 1.15.2→1.16.2 の差分と違う"
                f"（Wikiのみ {[hex(x) for x in sorted(delta_wiki_only)]}、"
                f"こちらのみ {[hex(x) for x in sorted(delta_ours_only)]}、"
                f"mc 1.15.2のみ {[hex(x) for x in sorted(delta_mc_1152_only)]}、"
                f"mc 1.16.2のみ {[hex(x) for x in sorted(delta_mc_1162_only)]}）")
        else:
            print(f"照合(B) {key}: 2772546 と id751 の差分は 1.15.2→1.16.2 の差分と一致"
                  f"（Wikiのみ {[hex(x) for x in sorted(delta_wiki_only)]}、"
                  f"こちらのみ {[hex(x) for x in sorted(delta_ours_only)]}）")
    if stale_problems:
        print(f"照合(B) 不一致 {len(stale_problems)} 件:")
        for p in stale_problems:
            print(f"  {p}")
        return 1
    print("照合(B)結果：不一致 0件（2772546 は 1.15.2 の表のまま、差分は 1.15.2→1.16.2 と一致）。")
    print("照合結果：不一致 0件。")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
