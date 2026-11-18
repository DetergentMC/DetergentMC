#!/usr/bin/env python3
"""Bedrock 1.19.11（プロトコル 534）の配線検証。

Java ループの verify-735-tables.py と verify-be-2193-tables.py /
verify-be-860-tables.py / verify-be-582-tables.py / verify-be-575-tables.py /
verify-be-567-tables.py / verify-be-560-tables.py / verify-be-557-tables.py /
verify-be-554-tables.py / verify-be-545-tables.py / verify-be-544-tables.py
にならい、表・定数・述語の前提を機械計数で確認する。すべて実ファイル
（BedrockVersion.java / BedrockProtocol.java / BedrockSession.java /
protocol-1.19.10.json / protocol-1.19.20.json / protocol-1.19.30.json /
protocol-1.19.40.json / protocol-1.19.50.json / protocol-1.19.62.json /
protocol-1.19.80.json / protocol-1.20.0.json / .bin データ）に対する
チェックで、推測はない。

- 534 の protocol.json は minecraft-data の `data/bedrock/1.19.10/` にある。
- 534 は 1.19.10 / 1.19.11 で共有するプロトコル（Minecraft Wiki
  「Bedrock Edition 1.19.10」「Bedrock Edition 1.19.11」の infobox
  「Protocol version 534」・同 Wiki「Protocol version」表で確認。
  protocolVersions.json でも 534＝1.19.10 のみ。displayName は共有する
  うち最新の 1.19.11（545 の V1_19_22 と同じ流儀））。
- **534 の protocol.json は 544 のものとバイト同一ではない**
  （10,646 行・275,518 バイト・SHA-1 `6586b076…`、cmp で差分あり）。
  534 と 544 の差分は追加 1（191 feature_registry）・削除 0・
  リネーム 0・型変更 8 件。
- 534 の 1 つ下は 527（1.19.1）。528〜533 にエントリは無い
  （530・532・533 は Preview 1.19.10.20／.21／.22 の番号、
  528・529・531 はスキップされた番号）。
- アイテム表は 557 の bedrock_item_ids_1_19_40.bin を共用する
  （bedrock-1.19.10 タグの required_item_list.json が 1.19.20／1.19.30／
  1.19.40 のものとバイト同一で、候補表もバイト同一。今回は 1.19.10 自体の
  実測で、545 回のような「代用」ではない。860 が 859 の表を共用するのと
  同じ扱い。BE-NOTES.md §11）。
- ブロックパレットは 1.19.40 とバイト同一ではない（8,289 エントリーで
  2 件少ない。差分は muddy_mangrove_roots の状態数のみ）。それでも
  ブロック表は新規不要（既存ペアを流用。589 と同じ根拠。BE-NOTES.md §11）。
- 述語の期待値は BedrockVersion.java の has*() を読んで決めたもの。
  このスクリプトは protocol.json 側の事実（フィールドの有無・順序・
  型）と突き合わせ、述語の前提が 534 で崩れていないことを確認する。
  述語そのものの実行時挙動は BedrockVersionTest で確認する。
- 534 回の実質的な変更：StartGame・UpdateAttributes・
  NetworkChunkPublisherUpdate のバージョン分岐。534 回まで
  persona_disabled 等の 544 追加分・emote_chat_muted・582 追加分・
  server_controlled_sound・modifiers・saved_chunks を無条件に書いて
  いたため、534〜582 の実ワイヤは余分なバイトを含んでいた
  （BE-NOTES.md §11）。このスクリプトの §J で分岐の存在を確認する。

使い方: python3 detergentmc-bedrock/tools/verify-be-534-tables.py
終了コード 0 = 全チェック通過。
"""
import hashlib
import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent.parent  # リポジトリ直下
BE = ROOT / "detergentmc-bedrock"
SRC = BE / "src/main/java/io/detergentmc/bedrock"
RES = BE / "src/main/resources/io/detergentmc/bedrock"
TMP = ROOT / ".tmp-be"  # protocol.json 等の調査用一時ファイル（リポジトリ直下）

CHECKS = []
FAILURES = []


def check(name, ok, detail=""):
    CHECKS.append(name)
    status = "PASS" if ok else "FAIL"
    print(f"[{status}] {name}" + (f" — {detail}" if detail else ""))
    if not ok:
        FAILURES.append(name)


def sha1(path):
    return hashlib.sha1(path.read_bytes()).hexdigest()


def container_fields(types, name):
    """protocol.json の container 型のフィールド名一覧（順序付き）を返す。"""
    t = types[name]
    assert t[0] == "container", f"{name} は container ではない: {t[0]}"
    return [(f["name"], f["type"]) for f in t[1]]


def field_names(types, name):
    return [n for n, _ in container_fields(types, name)]


def field_type(types, name, field):
    for n, t in container_fields(types, name):
        if n == field:
            return t
    return None


def packet_ids(data):
    mapper = data["types"]["mcpe_packet"][1][0]["type"][1]["mappings"]
    return {int(k): v for k, v in mapper.items()}


# ---------- A. protocol.json（534）の同一性 ----------
p110 = TMP / "protocol-1.19.10.json"
check("534 protocol.json が存在する", p110.is_file(), str(p110))
check("534 protocol.json の行数が 10,646 行（wc -l、改行数）",
      p110.is_file()
      and p110.read_text().count("\n") == 10646,
      f"{p110.read_text().count(chr(10))} 行" if p110.is_file() else "なし")
check("534 protocol.json のサイズが 275,518 バイト",
      p110.is_file() and p110.stat().st_size == 275518,
      f"{p110.stat().st_size} バイト" if p110.is_file() else "なし")
check("534 protocol.json の SHA-1 が 6586b076…と一致",
      p110.is_file()
      and sha1(p110) == "6586b07615e11c7a318c98da4170496ab2c95026",
      sha1(p110) if p110.is_file() else "なし")

data534 = json.loads(p110.read_text())
types534 = data534["types"]
ids534 = packet_ids(data534)

# ---------- A2. 隣接バージョン（544・554・557・560）の protocol.json ----------
p120 = TMP / "protocol-1.19.20.json"
check("544 protocol.json（上側の隣接）が存在する", p120.is_file(), str(p120))
check("544 protocol.json の行数が 10,836 行・サイズが 280,171 バイト・SHA-1 が 60acb22c…と一致",
      p120.is_file() and p120.read_text().count("\n") == 10836
      and p120.stat().st_size == 280171
      and sha1(p120) == "60acb22c50099d389704f9a811cf2682d0028241",
      f"{p120.read_text().count(chr(10))} 行・{p120.stat().st_size} バイト" if p120.is_file() else "なし")
check("534 の protocol.json は 544 のものとバイト同一ではない（SHA-1 が違う）",
      p110.is_file() and p120.is_file()
      and sha1(p110) != sha1(p120))
p130 = TMP / "protocol-1.19.30.json"
check("554 protocol.json が存在する", p130.is_file(), str(p130))
check("554 protocol.json の行数が 11,048 行・サイズが 285,450 バイト・SHA-1 が 960342ef…と一致",
      p130.is_file() and p130.read_text().count("\n") == 11048
      and p130.stat().st_size == 285450
      and sha1(p130) == "960342ef57d6207a8e7fd581dbb47eff34abd058",
      f"{p130.read_text().count(chr(10))} 行・{p130.stat().st_size} バイト" if p130.is_file() else "なし")
p140 = TMP / "protocol-1.19.40.json"
check("557 protocol.json が存在する", p140.is_file(), str(p140))
check("557 protocol.json の行数が 11,130 行・サイズが 287,657 バイト・SHA-1 が f3f81376…と一致",
      p140.is_file() and p140.read_text().count("\n") == 11130
      and p140.stat().st_size == 287657
      and sha1(p140) == "f3f8137679253a10f2dfafb74d3c4be10d519883",
      f"{p140.read_text().count(chr(10))} 行・{p140.stat().st_size} バイト" if p140.is_file() else "なし")
p150 = TMP / "protocol-1.19.50.json"
check("560 protocol.json が存在する", p150.is_file(), str(p150))
check("560 protocol.json の行数が 11,179 行・サイズが 288,987 バイト・SHA-1 が 5fd84ac8…と一致",
      p150.is_file() and p150.read_text().count("\n") == 11179
      and p150.stat().st_size == 288987
      and sha1(p150) == "5fd84ac85c9ed237e5aa8dc680a0c7cb87c5b9bd",
      f"{p150.read_text().count(chr(10))} 行・{p150.stat().st_size} バイト" if p150.is_file() else "なし")
pver = TMP / "protocolVersions.json"
check("protocolVersions.json が存在する", pver.is_file(), str(pver))
if pver.is_file():
    pv = json.loads(pver.read_text())
    check("protocolVersions.json に 528〜533 のエントリが無い",
          not any(528 <= e["version"] <= 533 for e in pv))
    check("protocolVersions.json の 534 は 1.19.10 のみ",
          [e["minecraftVersion"] for e in pv if e["version"] == 534] == ["1.19.10"])
    check("protocolVersions.json の 534 の 1 つ下は 527（1.19.1）",
          max(e["version"] for e in pv if e["version"] < 534) == 527)
    check("protocolVersions.json の 527 は 1.19.1",
          [e["minecraftVersion"] for e in pv if e["version"] == 527] == ["1.19.1"])
    check("protocolVersions.json の 544 は 1.19.20 のみ",
          [e["minecraftVersion"] for e in pv if e["version"] == 544] == ["1.19.20"])

data544 = json.loads(p120.read_text()) if p120.is_file() else None
data554 = json.loads(p130.read_text()) if p130.is_file() else None
data557 = json.loads(p140.read_text()) if p140.is_file() else None
data560 = json.loads(p150.read_text()) if p150.is_file() else None
ids544 = packet_ids(data544) if data544 else {}
ids554 = packet_ids(data554) if data554 else {}
ids557 = packet_ids(data557) if data557 else {}
ids560 = packet_ids(data560) if data560 else {}

# ---------- B. パケット一覧（534）の件数・境界 ----------
check("534 のパケット数が 187 件", len(ids534) == 187, f"{len(ids534)} 件")
check("534 の最大パケット ID が 190", max(ids534) == 190, f"最大 {max(ids534)}")
check("534 の型定義数が 289", len(types534) == 289, f"{len(types534)}")
check("544 のパケット数が 188 件", len(ids544) == 188, f"{len(ids544)} 件")
check("544 の最大パケット ID が 191", max(ids544) == 191, f"最大 {max(ids544)}")
check("544 の型定義数が 290", len(data544["types"]) == 290 if data544 else False)
check("554 のパケット数が 192 件", len(ids554) == 192, f"{len(ids554)} 件")
check("554 の最大パケット ID が 195", max(ids554) == 195, f"最大 {max(ids554)}")
check("554 の型定義数が 294", len(data554["types"]) == 294 if data554 else False)
check("534 の ID の欠番が 0・16・37・116（544・554 と同じ）",
      [i for i in range(max(ids534) + 1) if i not in ids534] == [0, 16, 37, 116])

d534 = TMP / "packet-diff-534-vs-544.json"
d544 = TMP / "packet-diff-544-vs-545.json"
d545 = TMP / "packet-diff-545-vs-554.json"
check("534 vs 544 の差分 JSON が存在する", d534.is_file(), str(d534))
check("544 vs 545 の差分 JSON が存在する", d544.is_file(), str(d544))
check("545 vs 554 の差分 JSON が存在する", d545.is_file(), str(d545))
if d534.is_file() and d544.is_file() and d545.is_file():
    j534 = json.loads(d534.read_text())
    j544 = json.loads(d544.read_text())
    j545 = json.loads(d545.read_text())
    check("534 vs 544：追加 1 件が 191 feature_registry",
          j534["added"] == {"191": "feature_registry"},
          json.dumps(j534["added"]))
    check("534 vs 544：削除 0・リネーム 0",
          j534["removed"] == {} and j534["renamed"] == {})
    check("534 vs 544：型変更 8 件（PlayerAttributes・mcpe_packet・packet_clientbound_map_item_data・packet_feature_registry・packet_map_info_request・packet_modal_form_response・packet_network_chunk_publisher_update・packet_start_game）",
          j534["changed_types"] == ["PlayerAttributes", "mcpe_packet",
                                     "packet_clientbound_map_item_data",
                                     "packet_feature_registry (only in B)",
                                     "packet_map_info_request",
                                     "packet_modal_form_response",
                                     "packet_network_chunk_publisher_update",
                                     "packet_start_game"],
          json.dumps(j534["changed_types"]))
    check("544 vs 545：追加 0・削除 0・リネーム 0・型変更 0（バイト同一）",
          j544["added"] == {} and j544["removed"] == {} and j544["renamed"] == {}
          and j544["changed_types"] == [],
          json.dumps(j544["changed_types"]))
    check("545 vs 554：追加 4 件が 192 server_stats・193 request_network_settings・194 game_test_request・195 game_test_results",
          j545["added"] == {"192": "server_stats", "193": "request_network_settings",
                            "194": "game_test_request", "195": "game_test_results"},
          json.dumps(j545["added"]))
    check("545 vs 554：削除 0・リネーム 0",
          j545["removed"] == {} and j545["renamed"] == {})
    check("545 vs 554：型変更 11 件",
          len(j545["changed_types"]) == 11, str(j545["changed_types"]))

# ---------- C. 核パケット ID：BedrockProtocol.java と protocol.json の一致 ----------
proto_src = (SRC / "BedrockProtocol.java").read_text()
java_packets = dict(re.findall(
    r"PACKET_(\w+)\s*=\s*0x([0-9a-fA-F]+);", proto_src))
# (Java 定数名, protocol.json のパケット名)
CORE = [
    ("LOGIN", "login"), ("PLAY_STATUS", "play_status"),
    ("DISCONNECT", "disconnect"),
    ("RESOURCE_PACKS_INFO", "resource_packs_info"),
    ("RESOURCE_PACK_STACK", "resource_pack_stack"),
    ("RESOURCE_PACK_CLIENT_RESPONSE", "resource_pack_client_response"),
    ("TEXT", "text"), ("SET_TIME", "set_time"),
    ("START_GAME", "start_game"), ("ADD_PLAYER", "add_player"),
    ("ADD_ENTITY", "add_entity"), ("REMOVE_ENTITY", "remove_entity"),
    ("ADD_ITEM_ENTITY", "add_item_entity"),
    ("MOVE_ENTITY", "move_entity"), ("MOVE_PLAYER", "move_player"),
    ("UPDATE_BLOCK", "update_block"), ("ADD_PAINTING", "add_painting"),
    ("ENTITY_EVENT", "entity_event"), ("MOB_EFFECT", "mob_effect"),
    ("UPDATE_ATTRIBUTES", "update_attributes"),
    ("INVENTORY_TRANSACTION", "inventory_transaction"),
    ("MOB_EQUIPMENT", "mob_equipment"),
    ("MOB_ARMOR_EQUIPMENT", "mob_armor_equipment"),
    ("INTERACT", "interact"), ("PLAYER_ACTION", "player_action"),
    ("SET_ENTITY_DATA", "set_entity_data"),
    ("SET_ENTITY_MOTION", "set_entity_motion"),
    ("SET_ENTITY_LINK", "set_entity_link"),
    ("PLAYER_LIST", "player_list"),
    ("SPAWN_EXPERIENCE_ORB", "spawn_experience_orb"),
    ("MOVE_ENTITY_DELTA", "move_entity_delta"),
    ("LEVEL_CHUNK", "level_chunk"),
    ("REQUEST_CHUNK_RADIUS", "request_chunk_radius"),
    ("CHUNK_RADIUS_UPDATED", "chunk_radius_update"),
    ("COMMAND_REQUEST", "command_request"),
    ("NETWORK_CHUNK_PUBLISHER_UPDATE", "network_chunk_publisher_update"),
    ("CLIENT_CACHE_STATUS", "client_cache_status"),
    ("PLAYER_AUTH_INPUT", "player_auth_input"),
    ("UPDATE_SUB_CHUNK_BLOCKS", "update_subchunk_blocks"),
    ("SUB_CHUNK", "subchunk"), ("SUB_CHUNK_REQUEST", "subchunk_request"),
]
json_by_name = {v: k for k, v in ids534.items()}
for const, pname in CORE:
    java_id = int(java_packets[const], 16)
    json_id = json_by_name.get(pname)
    check(f"パケット ID {const}（0x{java_id:02x}）が protocol.json と一致",
          json_id == java_id, f"json={json_id}")

# ---------- D. BedrockVersion の enum ----------
ver_src = (SRC / "BedrockVersion.java").read_text()
entries = re.findall(r"^\s+(V1_\w+)\((\d+),\s*\"([^\"]+)\"\)[;,]?\s*$",
                     ver_src, re.MULTILINE)
entry_by_name = {name: (int(p), disp) for name, p, disp in entries}
check("enum エントリ V1_19_11 が存在する", "V1_19_11" in entry_by_name)
check("V1_19_11 のプロトコルが 534",
      entry_by_name.get("V1_19_11", (None,))[0] == 534,
      str(entry_by_name.get("V1_19_11")))
check("V1_19_11 の表示名が 1.19.11",
      entry_by_name.get("V1_19_11", (None, None))[1] == "1.19.11")
check("enum エントリが 66 件（64 プロトコル＋2193・2225 の重複各 1）",
      len(entries) == 66, f"{len(entries)} 件")
check("異なるプロトコル番号が 64 個",
      len({int(p) for _, p, _ in entries}) == 64)
check("V1_19_11 が V1_19_20 より先に宣言されている（昇順・最古端）",
      ver_src.index("V1_19_11(534") < ver_src.index("V1_19_20(544"))
check("enum の javadoc に 1.19.10 の protocol.json が含まれる",
      "1.19.10" in ver_src)
check("enum の javadoc の protocol.json が 51 ファイルになっている",
      "51 ファイル" in ver_src)

# ---------- E. itemTableTag とアイテム対応表 ----------
m = re.search(r"case 534 -> \"(1_19_40)\";", ver_src)
check("itemTableTag：534 が 1_19_40 の表を使う（557 と共用）", m is not None,
      m.group(0) if m else "case が見つからない")
tag_bin = RES / "bedrock_item_ids_1_19_40.bin"
check("共用するアイテム表 bedrock_item_ids_1_19_40.bin が存在する",
      tag_bin.is_file())
check("アイテム表のサイズが 10,496 バイト（1312 件×8）",
      tag_bin.is_file() and tag_bin.stat().st_size == 10496,
      f"{tag_bin.stat().st_size} バイト" if tag_bin.is_file() else "なし")
check("アイテム表の SHA-1 が 5e8fe661…と一致（1.19.10 の候補表とバイト同一）",
      tag_bin.is_file()
      and sha1(tag_bin) == "5e8fe661a8ae7a9ae62c60402f0dadfd72525c34",
      sha1(tag_bin) if tag_bin.is_file() else "なし")
check("1.19.10 名のアイテム表は無い（タグは 1_19_40 が正しい。860 と同じ扱い）",
      not (RES / "bedrock_item_ids_1_19_10.bin").exists())
item_bins = sorted(RES.glob("bedrock_item_ids_*.bin"))
check("アイテム表 .bin が 38 本（新規なし）", len(item_bins) == 38, f"{len(item_bins)} 本")
summary_path = BE / "tools/item_tables_summary.json"
summary = json.loads(summary_path.read_text()) if summary_path.is_file() else {}
check("item_tables_summary.json に 534 のエントリは無い（860 と同じ扱い）",
      "534" not in summary)
s557 = summary.get("557", {})
check("item_tables_summary.json の 557 が tag=bedrock-1.19.40・exact=801／shared=264／legacy=33／fallback=214",
      s557.get("tag") == "bedrock-1.19.40" and s557.get("exact") == 801
      and s557.get("shared") == 264 and s557.get("legacy") == 33
      and s557.get("fallback") == 214,
      str({k: s557.get(k) for k in ["tag", "exact", "shared", "legacy", "fallback"]}))

# ---------- F. 述語の前提と protocol.json（534）の突き合わせ ----------
sg = field_names(types534, "packet_start_game")
check("start_game が 65 フィールド（544 は 70 フィールド）", len(sg) == 65, f"{len(sg)} フィールド")
check("start_game に hardcore が無い（hasHardcore=false）",
      "hardcore" not in sg)
check("start_game に level_id 前の識別子が無い（hasPreLevelIdentifiers=false・hasOwnerIdentifier=false）",
      not any(t in sg for t in ["server_identifier", "world_identifier",
                                "scenario_identifier", "owner_identifier"]))
check("start_game に movement_authority がある（hasMovementAuthority=true）",
      "movement_authority" in sg)
check("start_game に itemstates がある（hasItemstates=true）",
      "itemstates" in sg)
check("start_game に tick_death_systems が無い（hasTickDeathSystems=false）",
      "tick_death_systems" not in sg)
check("start_game に has_server_join_info／server_join_info が無い（hasJoinInfoTail=false）",
      "has_server_join_info" not in sg and "server_join_info" not in sg)
check("start_game に server_editor_connection_policy が無い（hasEditorPolicy=false）",
      "server_editor_connection_policy" not in sg)
check("start_game に is_chat_logging が無い（hasChatLogging=false）",
      "is_chat_logging" not in sg)
check("start_game に created_in_editor／exported_from_editor／block_network_ids_are_hashes が無い（582 で追加されるもの）",
      not any(t in sg for t in ["created_in_editor", "exported_from_editor",
                                "block_network_ids_are_hashes"]))
check("start_game に server_controlled_sound が無い（589 で追加されるもの）",
      "server_controlled_sound" not in sg)
check("start_game に emote_chat_muted が無い（567 で追加されるもの）",
      "emote_chat_muted" not in sg)
check("start_game に persona_disabled／custom_skins_disabled が無い（544 で追加されるもの）",
      "persona_disabled" not in sg and "custom_skins_disabled" not in sg)
check("start_game に chat_restriction_level／disable_player_interactions が無い（544 で追加されるもの）",
      "chat_restriction_level" not in sg and "disable_player_interactions" not in sg)
check("start_game に client_side_generation が無い（544 で追加されるもの）",
      "client_side_generation" not in sg)

rpi = field_names(types534, "packet_resource_packs_info")
check("resource_packs_info に has_addons が無い（hasAddons=false）",
      "has_addons" not in rpi)
check("resource_packs_info に force_server_packs がある（hasForceServerPacks=true）",
      "force_server_packs" in rpi)
check("resource_packs_info に behaviour_packs がある（hasBehaviourPacks=true）",
      "behaviour_packs" in rpi)
check("resource_packs_info に resource_pack_links が無い（hasResourcePackLinks=false）",
      "resource_pack_links" not in rpi)
check("resource_packs_info に world_template が無い（hasWorldTemplate=false）",
      "world_template" not in rpi)
check("resource_packs_info に disable_vibrant_visuals が無い（hasDisableVibrantVisuals=false）",
      "disable_vibrant_visuals" not in rpi)
tpi = types534["TexturePackInfos"]
check("texture_packs の件数が li16（resourcePacksCountIsVarint=false）",
      tpi[0] == "array" and tpi[1].get("countType") == "li16",
      json.dumps(tpi[1].get("countType")) if isinstance(tpi[1], dict) else str(tpi))

rps = field_names(types534, "packet_resource_pack_stack")
check("resource_pack_stack に behavior_packs がある（hasBehaviorPacksInStack=true）",
      "behavior_packs" in rps)
check("resource_pack_stack に has_editor_packs が無い（hasEditorPacksInStack=false）",
      "has_editor_packs" not in rps)

me = field_type(types534, "packet_mob_equipment", "item")
check("mob_equipment の item が旧型 Item（mobEquipmentUsesItemV4=false）",
      me == "Item", str(me))
aie = field_type(types534, "packet_add_item_entity", "item")
check("add_item_entity の item が旧型 Item（addItemUsesItemV4=false）",
      aie == "Item", str(aie))
mae = container_fields(types534, "packet_mob_armor_equipment")
mae_items = [n for n, t in mae if t == "Item"]
check("mob_armor_equipment が旧型 Item×4（helmet/chestplate/leggings/boots）",
      mae_items == ["helmet", "chestplate", "leggings", "boots"],
      str(mae_items))
check("mob_armor_equipment に body が無い（mobArmorHasBodySlot=false）",
      "body" not in mae_items)
check("ItemV4 型が 534 の protocol.json に存在しない",
      "ItemV4" not in types534)

legacy = types534["TransactionLegacy"]
legacy_map = {f["name"]: f["type"] for f in legacy[1]}
legacy_tx = legacy_map.get("legacy_transactions")
check("Transaction の legacy_transactions が switch 形式（transactionLegacyIsOption=false）",
      isinstance(legacy_tx, list) and len(legacy_tx) >= 1
      and legacy_tx[0] == "switch"
      and legacy_tx[1].get("compareTo") == "legacy_request_id",
      json.dumps(legacy_tx)[:80])
tx = types534["Transaction"]
tx_map = {f["name"]: f["type"] for f in tx[1]}
check("Transaction の transaction_type／actions が素のフィールド（transactionFieldsAreOptions=false）",
      (isinstance(tx_map.get("transaction_type"), list)
       and tx_map["transaction_type"][0] == "mapper"
       and tx_map["transaction_type"][1].get("type") == "varint")
      and tx_map.get("actions") == "TransactionActions",
      f"type={json.dumps(tx_map.get('transaction_type'))[:60]} actions={tx_map.get('actions')}")
ta = types534["TransactionActions"]
ta_fields = {f["name"]: f["type"] for f in ta[1]["type"][1] if "name" in f}
check("TransactionActions の old/new item が旧型 Item（transactionUsesItemV4=false）",
      ta_fields.get("old_item") == "Item" and ta_fields.get("new_item") == "Item",
      f"old={ta_fields.get('old_item')} new={ta_fields.get('new_item')}")
check("TransactionActions に window_id／flags の option 欄が無い",
      "window_id" not in ta_fields and "flags" not in ta_fields)
use_entity = tx_map["transaction_data"][1]["fields"]["item_use_on_entity"]
held = [f["type"] for f in use_entity[1] if f["name"] == "held_item"]
check("item_use_on_entity の held_item が旧型 Item",
      held == ["Item"], str(held))

# 534 と 544 の主要パケット（StartGame を除く 15 件）が JSON 完全一致
if data544:
    types544 = data544["types"]
    for pname in ["packet_resource_packs_info",
                  "packet_resource_pack_stack", "packet_mob_equipment",
                  "packet_add_item_entity", "packet_mob_armor_equipment",
                  "packet_inventory_transaction", "packet_level_chunk",
                  "packet_update_block", "packet_move_player",
                  "packet_add_player", "packet_add_entity",
                  "packet_set_entity_data", "packet_player_auth_input",
                  "packet_emote", "packet_command_request"]:
        check(f"{pname} が 534 と 544 で JSON 完全一致",
              types534.get(pname) == types544.get(pname))
    check("packet_start_game は 534 と 544 で異なる（544 追加分 5 フィールド）",
          types534.get("packet_start_game") != types544.get("packet_start_game"))

# 534 と 557 の主要パケット（StartGame と properties 3 件を除く 12 件）が JSON 完全一致
if data557:
    types557 = data557["types"]
    for pname in ["packet_resource_packs_info",
                  "packet_resource_pack_stack", "packet_mob_equipment",
                  "packet_add_item_entity", "packet_mob_armor_equipment",
                  "packet_inventory_transaction", "packet_level_chunk",
                  "packet_update_block", "packet_move_player",
                  "packet_player_auth_input",
                  "packet_emote", "packet_command_request"]:
        check(f"{pname} が 534 と 557 で JSON 完全一致",
              types534.get(pname) == types557.get(pname))
    check("packet_start_game は 534 と 557 で異なる（544 追加分 5 フィールド＋emote_chat_muted）",
          types534.get("packet_start_game") != types557.get("packet_start_game"))
    check("packet_add_player・packet_add_entity・packet_set_entity_data は 534 と 557 で異なる（557 に properties が追加）",
          types534.get("packet_add_player") != types557.get("packet_add_player")
          and types534.get("packet_add_entity") != types557.get("packet_add_entity")
          and types534.get("packet_set_entity_data") != types557.get("packet_set_entity_data"))

# ---------- G. 534／544／554／557／560／567／575／582／589 の境界 ----------
check("534 に 191 feature_registry が無い",
      191 not in ids534)
check("544 に 191 feature_registry がある（境界は 534／544 間）",
      ids544.get(191) == "feature_registry")
check("EntityProperties 型が 534 の protocol.json に存在しない",
      "EntityProperties" not in types534)
check("EntityProperties 型が 544 の protocol.json に存在しない",
      data544 is not None and "EntityProperties" not in data544["types"])
check("EntityProperties 型が 554 の protocol.json に存在しない（境界は 554／557 間）",
      data554 is not None and "EntityProperties" not in data554["types"])
check("EntityProperties 型が 557 の protocol.json に存在する",
      data557 is not None and "EntityProperties" in data557["types"])
if data554:
    types554 = data554["types"]
    check("534 の packet_add_player は 19 フィールド、544・554 も 19 フィールド（properties はいずれにも無い）",
          len(field_names(types534, "packet_add_player")) == 19
          and len(field_names(types544, "packet_add_player")) == 19
          and len(field_names(types554, "packet_add_player")) == 19)
    check("534 の packet_add_entity は 12 フィールド、544・554 も 12 フィールド（properties はいずれにも無い）",
          len(field_names(types534, "packet_add_entity")) == 12
          and len(field_names(types544, "packet_add_entity")) == 12
          and len(field_names(types554, "packet_add_entity")) == 12)
    check("534 の packet_set_entity_data は 3 フィールド、544・554 も 3 フィールド（properties はいずれにも無い）",
          len(field_names(types534, "packet_set_entity_data")) == 3
          and len(field_names(types544, "packet_set_entity_data")) == 3
          and len(field_names(types554, "packet_set_entity_data")) == 3)
    # ItemStackRequest：534 には cause フィールド自体が無く、554 は cause 0〜10 を持つ
    def cause_mappings(types):
        for f in types["ItemStackRequest"][1]:
            if f.get("name") == "cause":
                return f["type"][1]["mappings"]
        return None
    check("534 の ItemStackRequest に cause フィールドが無い",
          cause_mappings(types534) is None)
    c554 = cause_mappings(types554)
    check("554 の ItemStackRequest cause は 0〜10（10 cartography_text まで）",
          c554 is not None and "11" not in c554
          and c554.get("10") == "cartography_text",
          json.dumps(c554))
    # RecipeIngredient：534 は旧形式（network_id＋switch）、554 は新形式（type u8 mapper＋分岐）
    def recipe_first_field(types):
        return types["RecipeIngredient"][1][0]
    r534 = recipe_first_field(types534)
    r554 = recipe_first_field(types554)
    check("534 の RecipeIngredient は旧形式（network_id zigzag32＋switch）",
          r534.get("name") == "network_id" and r534.get("type") == "zigzag32"
          and types534["RecipeIngredient"][1][1]["type"][0] == "switch",
          json.dumps(r534)[:80])
    check("554 の RecipeIngredient は新形式（type u8 mapper、int_id_meta 等に分岐）",
          r554.get("name") == "type"
          and isinstance(r554.get("type"), list) and r554["type"][0] == "mapper"
          and r554["type"][1].get("type") == "u8"
          and "int_id_meta" in r554["type"][1].get("mappings", {}).values(),
          json.dumps(r554)[:120])
    # SoundType：534・554 ともに 443 件、442 の名前だけが変わる
    def mapper_len(types, name):
        return len(types[name][1]["mappings"])
    check("534 の SoundType は 443 件、554 も 443 件",
          mapper_len(types534, "SoundType") == 443
          and mapper_len(types554, "SoundType") == 443)
    check("534 の SoundType の 442 は Undefined、554 は EnchantingTableUse",
          types534["SoundType"][1]["mappings"].get("442") == "Undefined"
          and types554["SoundType"][1]["mappings"].get("442") == "EnchantingTableUse")
    # packet_text：534 の type マッパーに 11 は無く、554 は 11 json_announcement
    def text_type_map(types):
        for f in types["packet_text"][1]:
            if f["name"] == "type":
                return f["type"][1]["mappings"]
        return {}
    check("534 の packet_text type に 11 は無く、554 は 11 json_announcement",
          "11" not in text_type_map(types534)
          and text_type_map(types554).get("11") == "json_announcement")
    # packet_network_settings：534 は compression_threshold u16 の 1 フィールドのみ
    ns534 = field_names(types534, "packet_network_settings")
    ns554 = field_names(types554, "packet_network_settings")
    check("534 の packet_network_settings は compression_threshold の 1 フィールドのみ",
          ns534 == ["compression_threshold"],
          str(ns534))
    check("534 の compression_threshold は u16（554 は lu16）",
          field_type(types534, "packet_network_settings", "compression_threshold") == "u16"
          and field_type(types554, "packet_network_settings", "compression_threshold") == "lu16")
    check("554 の packet_network_settings は 5 フィールド（compression_algorithm・client_throttle・client_throttle_threshold・client_throttle_scalar を追加）",
          ns554 == ["compression_threshold", "compression_algorithm",
                    "client_throttle", "client_throttle_threshold",
                    "client_throttle_scalar"],
          str(ns554))
    # packet_structure_block_update：534 は 9 フィールド、554 は water_logged を末尾に追加
    sbu534 = field_names(types534, "packet_structure_block_update")
    sbu554 = field_names(types554, "packet_structure_block_update")
    check("534 の packet_structure_block_update は 9 フィールドで water_logged が無い",
          len(sbu534) == 9 and "water_logged" not in sbu534,
          str(sbu534))
    check("554 の packet_structure_block_update は 10 フィールドで末尾に water_logged を追加",
          len(sbu554) == 10 and sbu554[-1] == "water_logged"
          and sbu554[:9] == sbu534,
          str(sbu554))

# 534→544 で変わる 6 件の型（§B の型変更 8 件のうち実質 6 件）
if data544:
    types544 = data544["types"]
    # PlayerAttributes：534 は modifiers 無しの 5 項目、544 は modifiers 配列付き
    pa534 = types534["PlayerAttributes"][1]["type"][1]
    pa544 = types544["PlayerAttributes"][1]["type"][1]
    check("534 の PlayerAttributes は min／max／current／default／name の 5 項目で modifiers が無い",
          [f["name"] for f in pa534] == ["min", "max", "current", "default", "name"],
          str([f["name"] for f in pa534]))
    check("544 の PlayerAttributes は modifiers 配列付きの 6 項目",
          [f["name"] for f in pa544] == ["min", "max", "current", "default", "name", "modifiers"],
          str([f["name"] for f in pa544]))
    # packet_clientbound_map_item_data：534 は origin が無く、544 は origin（vec3i）付き
    check("534 の packet_clientbound_map_item_data に origin が無い",
          "origin" not in field_names(types534, "packet_clientbound_map_item_data"))
    check("544 の packet_clientbound_map_item_data に origin（vec3i）がある",
          field_type(types544, "packet_clientbound_map_item_data", "origin") == "vec3i")
    # packet_map_info_request：534 は map_id のみ、544 は client_pixels 付き
    check("534 の packet_map_info_request は map_id のみ",
          field_names(types534, "packet_map_info_request") == ["map_id"])
    check("544 の packet_map_info_request は map_id＋client_pixels",
          field_names(types544, "packet_map_info_request") == ["map_id", "client_pixels"])
    # packet_modal_form_response：534 は旧 2 フィールド形式、544 は has_response_data／has_cancel_reason 形式
    # （544 は cancel_reason が anon フィールドのため get("name") で拾う）
    mfr544 = [f.get("name") for f in types544["packet_modal_form_response"][1]]
    check("534 の packet_modal_form_response は form_id・data の 2 フィールド",
          field_names(types534, "packet_modal_form_response") == ["form_id", "data"])
    check("544 の packet_modal_form_response は has_response_data／has_cancel_reason 付き",
          "has_response_data" in mfr544
          and "has_cancel_reason" in mfr544)
    # packet_network_chunk_publisher_update：534 は saved_chunks が無く、544 は saved_chunks 付き
    check("534 の packet_network_chunk_publisher_update は coordinates・radius のみ",
          field_names(types534, "packet_network_chunk_publisher_update")
          == ["coordinates", "radius"])
    check("544 の packet_network_chunk_publisher_update は saved_chunks 付き",
          "saved_chunks" in field_names(types544, "packet_network_chunk_publisher_update"))
    # StartGame の 544 追加分 5 フィールド：534 に無く、544 にある
    sg544 = field_names(types544, "packet_start_game")
    for fname in ["persona_disabled", "custom_skins_disabled",
                  "chat_restriction_level", "disable_player_interactions",
                  "client_side_generation"]:
        check(f"start_game の {fname} は 534 に無く 544 にある（境界は 534／544 間）",
              fname not in sg and fname in sg544)

# emote_chat_muted：534・544 に無く、567 にある
p162 = TMP / "protocol-1.19.62.json"
data567 = json.loads(p162.read_text()) if p162.is_file() else None
check("567 protocol.json（1.19.62）が存在する", p162.is_file(), str(p162))
if data567:
    check("534・544 の start_game に emote_chat_muted が無く、567 にある（境界は 560／567 間）",
          "emote_chat_muted" not in sg
          and "emote_chat_muted" not in field_names(data544["types"], "packet_start_game")
          and "emote_chat_muted" in field_names(data567["types"], "packet_start_game"))
# created_in_editor・exported_from_editor・block_network_ids_are_hashes：534〜575 に無く、582 にある
p170 = TMP / "protocol-1.19.70.json"
p180 = TMP / "protocol-1.19.80.json"
data575 = json.loads(p170.read_text()) if p170.is_file() else None
data582 = json.loads(p180.read_text()) if p180.is_file() else None
check("575 protocol.json（1.19.70）が存在する", p170.is_file(), str(p170))
check("582 protocol.json（1.19.80）が存在する", p180.is_file(), str(p180))
if data575 and data582:
    for fname in ["created_in_editor", "exported_from_editor",
                  "block_network_ids_are_hashes"]:
        check(f"start_game の {fname} は 534・544・557・560・567・575 に無く、582 にある（境界は 575／582 間）",
              fname not in sg
              and fname not in field_names(data544["types"], "packet_start_game")
              and fname not in field_names(data557["types"], "packet_start_game")
              and fname not in field_names(data560["types"], "packet_start_game")
              and fname not in field_names(data567["types"], "packet_start_game")
              and fname not in field_names(data575["types"], "packet_start_game")
              and fname in field_names(data582["types"], "packet_start_game"))
# server_controlled_sound：582 までに無く、589 にある
p200 = TMP / "protocol-1.20.0.json"
data589 = json.loads(p200.read_text()) if p200.is_file() else None
check("589 protocol.json（1.20.0）が存在する", p200.is_file(), str(p200))
if data589:
    check("start_game の server_controlled_sound は 534〜582 に無く、589 にある（境界は 582／589 間）",
          "server_controlled_sound" not in sg
          and "server_controlled_sound" not in field_names(data544["types"], "packet_start_game")
          and "server_controlled_sound" not in field_names(data582["types"], "packet_start_game")
          and "server_controlled_sound" in field_names(data589["types"], "packet_start_game"))

# ---------- H. ブロックデータ（534 専用表は無いのが現状） ----------
block_bin = RES / "bedrock_block_runtime_ids_1_20_4.bin"
check("ブロックランタイム ID 表（1.20.4 世代）が存在する", block_bin.is_file())
check("ブロックランタイム ID 表のサイズが 56,624 バイト",
      block_bin.is_file() and block_bin.stat().st_size == 56624,
      f"{block_bin.stat().st_size} バイト" if block_bin.is_file() else "なし")
palette = RES / "canonical_block_states_1_20_50.nbt"
check("正規ブロックパレット（1.20.50）が存在する", palette.is_file())
check("正規ブロックパレットのサイズが 1,987,422 バイト",
      palette.is_file() and palette.stat().st_size == 1987422,
      f"{palette.stat().st_size} バイト" if palette.is_file() else "なし")
check("534 専用のブロック表は無い（1.20.50 世代を共用。データ担当の範囲）",
      not list(RES.glob("bedrock_block_runtime_ids_1_19*"))
      and not list(RES.glob("canonical_block_states_1_19*")))
be534 = TMP / "be-534"
check("1.19.10 の canonical_block_states.nbt（調査用・今回は 1.19.10 自体の実測）が存在する",
      (be534 / "canon-bedrock-1.19.10.nbt").is_file())
check("1.19.10 パレットのサイズが 1,224,985 バイト・SHA-1 が cde5786a…と一致（1.19.40 とは 2 エントリー違う）",
      (be534 / "canon-bedrock-1.19.10.nbt").is_file()
      and (be534 / "canon-bedrock-1.19.10.nbt").stat().st_size == 1224985
      and sha1(be534 / "canon-bedrock-1.19.10.nbt")
      == "cde5786a1334d61ba8d10cedb9e37508dc601d84")
check("1.19.10 の required_item_list.json（調査用・今回は 1.19.10 自体の実測）が存在する",
      (be534 / "req-bedrock-1.19.10-required_item_list.json").is_file())
check("1.19.10 の required_item_list.json が 114,288 バイト・SHA-1 が d5ea3b46…と一致・1,132 名（1.19.20／1.19.30／1.19.40 と同一）",
      (be534 / "req-bedrock-1.19.10-required_item_list.json").is_file()
      and (be534 / "req-bedrock-1.19.10-required_item_list.json").stat().st_size == 114288
      and sha1(be534 / "req-bedrock-1.19.10-required_item_list.json")
      == "d5ea3b468c9141ff11a8f7f6b38979b7ef08f3f4"
      and len(json.loads((be534 / "req-bedrock-1.19.10-required_item_list.json").read_text())) == 1132)
check("1.19.10 の biome_id_map.json が 1.19.20／1.19.30／1.19.40 のものとバイト同一（SHA-1 c82b9c74…）",
      (be534 / "biomeid-bedrock-1.19.10.json").is_file()
      and sha1(be534 / "biomeid-bedrock-1.19.10.json")
      == "c82b9c745c0e97bd4d3fa4cbf8337e6dccefb2b7")
check("1.19.10 の block_id_map.json が 1.19.20／1.19.30／1.19.40 のものとバイト同一（SHA-1 f3855a10…）",
      (be534 / "blockid-bedrock-1.19.10.json").is_file()
      and sha1(be534 / "blockid-bedrock-1.19.10.json")
      == "f3855a102c698f960020d928bcaef10d25724062")
check("1.19.10 の候補アイテム表が 1.19.40 の表とバイト同一（SHA-1 5e8fe661…）",
      (be534 / "bedrock_item_ids_1_19_10.bin").is_file()
      and sha1(be534 / "bedrock_item_ids_1_19_10.bin")
      == "5e8fe661a8ae7a9ae62c60402f0dadfd72525c34")

# ---------- I. 変更しないものの確認 ----------
check("TARGET_VERSION は 1.20.50 のまま（MOTD 専用）",
      'TARGET_VERSION = "1.20.50"' in proto_src)
session_src = (SRC / "BedrockSession.java").read_text()
check("Session の切断メッセージの対応範囲は 291-2193（RakNet）で 2225 は NetherNet 扱い",
      "(supported: 291-2193 over RakNet; 2225 needs NetherNet)" in session_src)
check("Session に NetherNet 切断の分岐がある",
      "isNetherNetProtocol(login.protocolVersion())" in session_src)
for fname in ["BedrockChunkTranslator.java",
              "JavaBackendConnection.java"]:
    src = (SRC / fname).read_text()
    check(f"{fname} は BedrockVersion を見ない（変更不要）",
          "BedrockVersion" not in src)
ent_src = (SRC / "BedrockEntityTranslator.java").read_text()
check("BedrockEntityTranslator.java は BedrockVersion を 2 箇所だけ参照する（431 回の goat_horn_count 分岐・toBedrockData の 3 引数版と既定 V1_20_50）",
      ent_src.count("BedrockVersion") == 2
      and "hasGoatHornCountKey()" in ent_src)

# ---------- J. 534 用のバージョン分岐がエンコーダにある ----------
packets_src = (SRC / "BedrockPackets.java").read_text()
for pred in ["hasStartGame544Fields()", "hasEmoteChatMuted()",
             "hasStartGame582Fields()", "hasServerControlledSound()",
             "hasAttributeModifiers()", "hasSavedChunks()"]:
    check(f"BedrockPackets.java が {pred} で分岐している",
          f"version.{pred}" in packets_src)
check("encodeUpdateAttributes のバージョン別オーバーロードがある",
      "encodeUpdateAttributes(long runtimeId,\n            float health, float maxHealth, BedrockVersion version)"
      in packets_src)
check("encodeNetworkChunkPublisherUpdate のバージョン別オーバーロードがある",
      "int radiusBlocks, BedrockVersion version)" in packets_src)
check("BedrockSession が encodeUpdateAttributes に bedrockVersion を渡している",
      "state.health, state.maxHealth, bedrockVersion" in session_src)
check("BedrockSession が encodeNetworkChunkPublisherUpdate に bedrockVersion を渡している",
      "radius * 16, bedrockVersion" in session_src)

# ---------- 集計 ----------
total = len(CHECKS)
passed = total - len(FAILURES)
print(f"\n{passed}/{total} checks passed.")
if FAILURES:
    print("失敗:")
    for f in FAILURES:
        print(f"  - {f}")
    sys.exit(1)
print("ALL CHECKS PASSED")
