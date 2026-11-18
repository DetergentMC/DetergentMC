#!/usr/bin/env python3
"""Bedrock 1.19.41（プロトコル 557）の配線検証。

Java ループの verify-735-tables.py と verify-be-2193-tables.py /
verify-be-860-tables.py / verify-be-582-tables.py / verify-be-575-tables.py /
verify-be-567-tables.py / verify-be-560-tables.py にならい、表・定数・
述語の前提を機械計数で確認する。すべて実ファイル（BedrockVersion.java /
BedrockProtocol.java / BedrockSession.java / protocol-1.19.40.json /
protocol-1.19.50.json / protocol-1.19.62.json / .bin データ）に対する
チェックで、推測はない。

- 557 の protocol.json は minecraft-data の `data/bedrock/1.19.40/` にある。
- 557 は 1.19.40／1.19.41 で共有するプロトコル（Minecraft Wiki
  「Bedrock Edition 1.19.41」の infobox「Protocol version 557」と
  同 Wiki「Protocol version」表で確認。BedrockConnect の表も
  `v557 | 1.19.40/1.19.41`。protocolVersions.json には 1.19.40 しか
  載っていないが、同ファイルは 582 も 1.19.80 しか載せていないため
  ホットフィックスの網羅性は無い。567 回の教訓（「1.19.61 が共有」は
  誤り）を踏まえ Wiki の 2 ソースで確認した）。
- 555 は Preview 1.19.40.22 のプロトコル番号（Minecraft Wiki
  「Protocol version」表）。556 は同表の Bedrock 欄にエントリが無い
  （`| 556 |` は Java 19w39a のみ）。protocolVersions.json に
  555・556 のエントリは無い。561〜566（ベータ／プレビュー版）を
  飛ばしたのと同じ扱いで飛ばす（BE-NOTES.md §7）。
- 557 の 1 つ下は 554（1.19.30）。上側の隣接は 560（1.19.50）。
- 述語の期待値は BedrockVersion.java の has*() を読んで決めたもの。
  このスクリプトは protocol.json 側の事実（フィールドの有無・順序・
  型）と突き合わせ、述語の前提が 557 で崩れていないことを確認する。
  述語そのものの実行時挙動は BedrockVersionTest で確認する。

使い方: python3 detergentmc-bedrock/tools/verify-be-557-tables.py
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


# ---------- A. protocol.json（557）の同一性 ----------
p140 = TMP / "protocol-1.19.40.json"
check("557 protocol.json が存在する", p140.is_file(), str(p140))
check("557 protocol.json の行数が 11,130 行（wc -l、改行数）",
      p140.is_file()
      and p140.read_text().count("\n") == 11130,
      f"{p140.read_text().count(chr(10))} 行" if p140.is_file() else "なし")
check("557 protocol.json のサイズが 287,657 バイト",
      p140.is_file() and p140.stat().st_size == 287657,
      f"{p140.stat().st_size} バイト" if p140.is_file() else "なし")
check("557 protocol.json の SHA-1 が f3f81376…と一致",
      p140.is_file()
      and sha1(p140) == "f3f8137679253a10f2dfafb74d3c4be10d519883",
      sha1(p140) if p140.is_file() else "なし")

data557 = json.loads(p140.read_text())
types557 = data557["types"]
ids557 = packet_ids(data557)

# ---------- A2. 隣接バージョン（560・567・554）の protocol.json ----------
p150 = TMP / "protocol-1.19.50.json"
check("560 protocol.json（上側の隣接）が存在する", p150.is_file(), str(p150))
check("560 protocol.json の行数が 11,179 行・サイズが 288,987 バイト・SHA-1 が 5fd84ac8…と一致",
      p150.is_file() and p150.read_text().count("\n") == 11179
      and p150.stat().st_size == 288987
      and sha1(p150) == "5fd84ac85c9ed237e5aa8dc680a0c7cb87c5b9bd",
      f"{p150.read_text().count(chr(10))} 行・{p150.stat().st_size} バイト" if p150.is_file() else "なし")
p162 = TMP / "protocol-1.19.62.json"
check("567 protocol.json（上側の隣接の隣）が存在する", p162.is_file(), str(p162))
check("567 protocol.json の行数が 11,265 行・サイズが 291,388 バイト・SHA-1 が e146973c…と一致",
      p162.is_file() and p162.read_text().count("\n") == 11265
      and p162.stat().st_size == 291388
      and sha1(p162) == "e146973c54730e24532624f07123edfed28a15b0",
      f"{p162.read_text().count(chr(10))} 行・{p162.stat().st_size} バイト" if p162.is_file() else "なし")
check("1.19.41 の protocol.json は .tmp-be に存在しない（1.19.40 と同じ 557 を共有し、minecraft-data に 1.19.41 のディレクトリは無い）",
      not (TMP / "protocol-1.19.41.json").is_file())
p130 = TMP / "protocol-1.19.30.json"
check("554（1.19.30）の protocol.json が存在する（557 回の次の一手で取得済み）",
      p130.is_file(), str(p130))
check("554 protocol.json の行数が 11,048 行・サイズが 285,450 バイト・SHA-1 が 960342ef…と一致",
      p130.is_file() and p130.read_text().count("\n") == 11048
      and p130.stat().st_size == 285450
      and sha1(p130) == "960342ef57d6207a8e7fd581dbb47eff34abd058",
      f"{p130.read_text().count(chr(10))} 行・{p130.stat().st_size} バイト" if p130.is_file() else "なし")
check("555・556 の protocol.json は .tmp-be に存在しない（ベータ／プレビュー版のため飛ばす）",
      not any((TMP / name).is_file() for name in [
          "protocol-1.19.40.20.json", "protocol-1.19.40.21.json",
          "protocol-1.19.40.22.json", "protocol-1.19.40.23.json",
          "protocol-1.19.40.24.json"]))
pver = TMP / "protocolVersions.json"
check("protocolVersions.json が存在する", pver.is_file(), str(pver))
if pver.is_file():
    pv = json.loads(pver.read_text())
    check("protocolVersions.json に 555・556 のエントリが無い",
          not any(e["version"] in (555, 556) for e in pv))
    check("protocolVersions.json の 557 は 1.19.40 のみ（1.19.41 は同ファイルに無い）",
          [e["minecraftVersion"] for e in pv if e["version"] == 557] == ["1.19.40"])
    check("protocolVersions.json の 557 の 1 つ下は 554（1.19.30）",
          max(e["version"] for e in pv if e["version"] < 557) == 554)
    check("protocolVersions.json の 554 は 1.19.30",
          [e["minecraftVersion"] for e in pv if e["version"] == 554] == ["1.19.30"])

data560 = json.loads(p150.read_text()) if p150.is_file() else None
data567 = json.loads(p162.read_text()) if p162.is_file() else None
ids560 = packet_ids(data560) if data560 else {}
ids567 = packet_ids(data567) if data567 else {}

# ---------- B. パケット一覧（557）の件数・境界 ----------
check("557 のパケット数が 192 件", len(ids557) == 192, f"{len(ids557)} 件")
check("557 の最大パケット ID が 195", max(ids557) == 195, f"最大 {max(ids557)}")
check("560 のパケット数が 193 件", len(ids560) == 193, f"{len(ids560)} 件")
check("560 の最大パケット ID が 196", max(ids560) == 196, f"最大 {max(ids560)}")
check("567 のパケット数が 194 件", len(ids567) == 194, f"{len(ids567)} 件")
check("567 の最大パケット ID が 197", max(ids567) == 197, f"最大 {max(ids567)}")

d557 = TMP / "packet-diff-557-vs-560.json"
d567 = TMP / "packet-diff-560-vs-567.json"
check("557 vs 560 の差分 JSON が存在する", d557.is_file(), str(d557))
check("560 vs 567 の差分 JSON が存在する", d567.is_file(), str(d567))
if d557.is_file() and d567.is_file():
    j557 = json.loads(d557.read_text())
    j567 = json.loads(d567.read_text())
    check("557 vs 560：追加 1 件が 196 update_client_input_locks",
          j557["added"] == {"196": "update_client_input_locks"},
          json.dumps(j557["added"]))
    check("557 vs 560：削除 0・リネーム 0",
          j557["removed"] == {} and j557["renamed"] == {})
    check("557 vs 560：型変更 11 件",
          len(j557["changed_types"]) == 11, str(j557["changed_types"]))
    check("557 vs 560：型変更に ContainerSlotType・ItemStackRequest・MetadataFlags1・MetadataFlags2・SoundType・WindowType が含まれる",
          all(t in j557["changed_types"] for t in ["ContainerSlotType",
                                                   "ItemStackRequest", "MetadataFlags1",
                                                   "MetadataFlags2", "SoundType",
                                                   "WindowType"]))
    check("557 vs 560：型変更に packet_start_game は含まれない（557 と 560 の StartGame は同一）",
          "packet_start_game" not in j557["changed_types"])
    check("560 vs 567：追加 1 件が 197 client_cheat_ability",
          j567["added"] == {"197": "client_cheat_ability"},
          json.dumps(j567["added"]))
    check("560 vs 567：削除 0・リネーム 0",
          j567["removed"] == {} and j567["renamed"] == {})
    check("560 vs 567：型変更 10 件",
          len(j567["changed_types"]) == 10, str(j567["changed_types"]))
    check("560 vs 567：型変更に ItemStackRequest は含まれない（cause は 560＝567 で 0〜13）",
          "ItemStackRequest" not in j567["changed_types"])

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
json_by_name = {v: k for k, v in ids557.items()}
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
check("enum エントリ V1_19_41 が存在する", "V1_19_41" in entry_by_name)
check("V1_19_41 のプロトコルが 557",
      entry_by_name.get("V1_19_41", (None,))[0] == 557,
      str(entry_by_name.get("V1_19_41")))
check("V1_19_41 の表示名が 1.19.41",
      entry_by_name.get("V1_19_41", (None, None))[1] == "1.19.41")
check("enum エントリが 66 件（64 プロトコル＋2193・2225 の重複各 1）",
      len(entries) == 66, f"{len(entries)} 件")
check("異なるプロトコル番号が 64 個",
      len({int(p) for _, p, _ in entries}) == 64)
check("V1_19_41 が V1_19_51 より先に宣言されている（昇順・最古端）",
      ver_src.index("V1_19_41(557") < ver_src.index("V1_19_51(560"))
check("enum の javadoc に 1.19.40 の protocol.json が含まれる",
      "1.19.40" in ver_src)
check("enum の javadoc に 557 は 1.19.40／1.19.41 で共有する旨がある",
      "1.19.40" in ver_src and "1.19.41" in ver_src)
check("enum の javadoc の protocol.json が 51 ファイルになっている",
      "51 ファイル" in ver_src)

# ---------- E. itemTableTag とアイテム対応表 ----------
m = re.search(r"case 557 -> \"(1_19_40)\";", ver_src)
check("itemTableTag：557 が 1_19_40 の表を使う", m is not None,
      m.group(0) if m else "case が見つからない")
tag_bin = RES / "bedrock_item_ids_1_19_40.bin"
check("557 専用のアイテム表 bedrock_item_ids_1_19_40.bin が存在する",
      tag_bin.is_file())
check("アイテム表のサイズが 10,496 バイト（1312 件×8）",
      tag_bin.is_file() and tag_bin.stat().st_size == 10496,
      f"{tag_bin.stat().st_size} バイト" if tag_bin.is_file() else "なし")
check("アイテム表の SHA-1 が 5e8fe661…と一致（生成スクリプトの再実行と同一）",
      tag_bin.is_file()
      and sha1(tag_bin) == "5e8fe661a8ae7a9ae62c60402f0dadfd72525c34",
      sha1(tag_bin) if tag_bin.is_file() else "なし")
check("1.19.41 名のアイテム表は無い（タグは 1_19_40 が正しい）",
      not (RES / "bedrock_item_ids_1_19_41.bin").exists())
item_bins = sorted(RES.glob("bedrock_item_ids_*.bin"))
check("アイテム表 .bin が 38 本", len(item_bins) == 38, f"{len(item_bins)} 本")
summary_path = BE / "tools/item_tables_summary.json"
summary = json.loads(summary_path.read_text()) if summary_path.is_file() else {}
s557 = summary.get("557", {})
check("item_tables_summary.json の 557 が tag=bedrock-1.19.40・exact=801／shared=264／legacy=33／fallback=214",
      s557.get("tag") == "bedrock-1.19.40" and s557.get("exact") == 801
      and s557.get("shared") == 264 and s557.get("legacy") == 33
      and s557.get("fallback") == 214,
      str({k: s557.get(k) for k in ["tag", "exact", "shared", "legacy", "fallback"]}))

# ---------- F. 述語の前提と protocol.json（557）の突き合わせ ----------
sg = field_names(types557, "packet_start_game")
check("start_game が 70 フィールド（560 と同じ）", len(sg) == 70, f"{len(sg)} フィールド")
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

rpi = field_names(types557, "packet_resource_packs_info")
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
tpi = types557["TexturePackInfos"]
check("texture_packs の件数が li16（resourcePacksCountIsVarint=false）",
      tpi[0] == "array" and tpi[1].get("countType") == "li16",
      json.dumps(tpi[1].get("countType")) if isinstance(tpi[1], dict) else str(tpi))

rps = field_names(types557, "packet_resource_pack_stack")
check("resource_pack_stack に behavior_packs がある（hasBehaviorPacksInStack=true）",
      "behavior_packs" in rps)
check("resource_pack_stack に has_editor_packs が無い（hasEditorPacksInStack=false）",
      "has_editor_packs" not in rps)

me = field_type(types557, "packet_mob_equipment", "item")
check("mob_equipment の item が旧型 Item（mobEquipmentUsesItemV4=false）",
      me == "Item", str(me))
aie = field_type(types557, "packet_add_item_entity", "item")
check("add_item_entity の item が旧型 Item（addItemUsesItemV4=false）",
      aie == "Item", str(aie))
mae = container_fields(types557, "packet_mob_armor_equipment")
mae_items = [n for n, t in mae if t == "Item"]
check("mob_armor_equipment が旧型 Item×4（helmet/chestplate/leggings/boots）",
      mae_items == ["helmet", "chestplate", "leggings", "boots"],
      str(mae_items))
check("mob_armor_equipment に body が無い（mobArmorHasBodySlot=false）",
      "body" not in mae_items)
check("ItemV4 型が 557 の protocol.json に存在しない",
      "ItemV4" not in types557)

legacy = types557["TransactionLegacy"]
legacy_map = {f["name"]: f["type"] for f in legacy[1]}
legacy_tx = legacy_map.get("legacy_transactions")
check("Transaction の legacy_transactions が switch 形式（transactionLegacyIsOption=false）",
      isinstance(legacy_tx, list) and len(legacy_tx) >= 1
      and legacy_tx[0] == "switch"
      and legacy_tx[1].get("compareTo") == "legacy_request_id",
      json.dumps(legacy_tx)[:80])
tx = types557["Transaction"]
tx_map = {f["name"]: f["type"] for f in tx[1]}
check("Transaction の transaction_type／actions が素のフィールド（transactionFieldsAreOptions=false）",
      (isinstance(tx_map.get("transaction_type"), list)
       and tx_map["transaction_type"][0] == "mapper"
       and tx_map["transaction_type"][1].get("type") == "varint")
      and tx_map.get("actions") == "TransactionActions",
      f"type={json.dumps(tx_map.get('transaction_type'))[:60]} actions={tx_map.get('actions')}")
ta = types557["TransactionActions"]
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

# 557 と 560 の主要パケットが JSON 完全一致（StartGame・ResourcePacks・Item・Transaction は同じ経路）
if data560:
    types560 = data560["types"]
    for pname in ["packet_start_game", "packet_resource_packs_info",
                  "packet_resource_pack_stack", "packet_mob_equipment",
                  "packet_add_item_entity", "packet_mob_armor_equipment",
                  "packet_inventory_transaction", "packet_level_chunk",
                  "packet_add_player", "packet_add_entity",
                  "packet_update_block", "packet_move_player",
                  "packet_set_entity_data", "packet_player_auth_input",
                  "packet_emote", "packet_command_request"]:
        check(f"{pname} が 557 と 560 で JSON 完全一致",
              types557.get(pname) == types560.get(pname))

# 557 と 567 の主要パケット（StartGame を除く）が JSON 完全一致
if data567:
    types567 = data567["types"]
    for pname in ["packet_resource_packs_info",
                  "packet_resource_pack_stack", "packet_mob_equipment",
                  "packet_add_item_entity", "packet_mob_armor_equipment",
                  "packet_inventory_transaction", "packet_level_chunk",
                  "packet_add_player", "packet_add_entity",
                  "packet_update_block", "packet_move_player",
                  "packet_set_entity_data"]:
        check(f"{pname} が 557 と 567 で JSON 完全一致",
              types557.get(pname) == types567.get(pname))
    check("packet_start_game は 557 と 567 で異なる（567 に emote_chat_muted が追加）",
          types557.get("packet_start_game") != types567.get("packet_start_game"))
    sg567 = field_names(types567, "packet_start_game")
    check("567 の start_game は 71 フィールドで 557 との差分は emote_chat_muted のみ",
          len(sg567) == 71 and [f for f in sg567 if f not in sg] == ["emote_chat_muted"]
          and [f for f in sg if f not in sg567] == [])

# ---------- G. 557／560／567 の境界 ----------
check("557 に 196 update_client_input_locks が無い（境界は 557／560 間）",
      196 not in ids557)
check("560 に 196 update_client_input_locks がある（境界は 557／560 間）",
      196 in ids560)
check("560 に 197 client_cheat_ability が無い（境界は 560／567 間）",
      197 not in ids560)
check("567 に 197 client_cheat_ability がある（境界は 560／567 間）",
      197 in ids567)
if data560 and data567:
    # ItemStackRequest の cause マッパー：557 は 11 まで、560・567 は 12・13 まで
    def cause_mappings(types):
        for f in types["ItemStackRequest"][1]:
            if f.get("name") == "cause":
                return f["type"][1]["mappings"]
        return {}
    c557 = cause_mappings(types557)
    c560 = cause_mappings(types560)
    c567 = cause_mappings(types567)
    check("557 の ItemStackRequest cause は 11 slash_command_non_chat まで（12・13 は無い）",
          "12" not in c557 and "13" not in c557 and c557.get("11") == "slash_command_non_chat",
          json.dumps(c557))
    check("560 の ItemStackRequest cause に 12 scoreboard_text・13 ticking_area_text がある",
          c560.get("12") == "scoreboard_text" and c560.get("13") == "ticking_area_text",
          json.dumps(c560))
    check("567 の ItemStackRequest cause にも 12 scoreboard_text・13 ticking_area_text がある（560＝567）",
          c567.get("12") == "scoreboard_text" and c567.get("13") == "ticking_area_text",
          json.dumps(c567))
    # ContainerSlotType：557 は 60 件、560 は 61 件で 21 recipe_book が挿入
    def mapper_len(types, name):
        return len(types[name][1]["mappings"])
    check("557 の ContainerSlotType は 60 件",
          mapper_len(types557, "ContainerSlotType") == 60)
    check("560 の ContainerSlotType は 61 件で 21 が recipe_book",
          mapper_len(types560, "ContainerSlotType") == 61
          and types560["ContainerSlotType"][1]["mappings"].get("21") == "recipe_book")
    check("567 の ContainerSlotType も 61 件で 21 が recipe_book（560＝567）",
          mapper_len(types567, "ContainerSlotType") == 61
          and types567["ContainerSlotType"][1]["mappings"].get("21") == "recipe_book")
    # SoundType：557 は 448 件、560 は 458 件、567 は 462 件
    check("557 の SoundType は 448 件、560 は 458 件、567 は 462 件",
          mapper_len(types557, "SoundType") == 448
          and mapper_len(types560, "SoundType") == 458
          and mapper_len(types567, "SoundType") == 462)
    check("560 の SoundType は 457 FenceGateClose まで（458 Insert・459 Pickup・460 InsertEnchanted・461 PickupEnchanted は 567 で追加）",
          types560["SoundType"][1]["mappings"].get("457") == "FenceGateClose"
          and "458" not in types560["SoundType"][1]["mappings"]
          and types567["SoundType"][1]["mappings"].get("458") == "Insert"
          and types567["SoundType"][1]["mappings"].get("461") == "PickupEnchanted")
    check("557 の SoundType の 443・444 は _（プレースホルダ）で、560 では StepSand・DashReady",
          types557["SoundType"][1]["mappings"].get("443") == "_"
          and types557["SoundType"][1]["mappings"].get("444") == "_"
          and types560["SoundType"][1]["mappings"].get("443") == "StepSand"
          and types560["SoundType"][1]["mappings"].get("444") == "DashReady")
    # MetadataFlags1：557 は 64、560 は 65 で can_dash が index 46 に挿入
    f1_557 = types557["MetadataFlags1"][1]["flags"]
    f1_560 = types560["MetadataFlags1"][1]["flags"]
    check("557 の MetadataFlags1 は 64 件、560 は 65 件",
          len(f1_557) == 64 and len(f1_560) == 65)
    check("560 の MetadataFlags1 の index 46 が can_dash（linger の直前）",
          f1_560[46] == "can_dash" and f1_560[47] == "linger",
          str(f1_560[44:49]))
    # MetadataFlags2：557 は 42 件、560 は 45 件で末尾に 3 件追加
    f2_557 = types557["MetadataFlags2"][1]["flags"]
    f2_560 = types560["MetadataFlags2"][1]["flags"]
    check("557 の MetadataFlags2 は 42 件、560 は 45 件で sonic_boom・has_dash_cooldown・push_towards_closest_space を追加",
          len(f2_557) == 42 and len(f2_560) == 45
          and f2_560[42:] == ["sonic_boom", "has_dash_cooldown", "push_towards_closest_space"]
          and f2_557 == f2_560[:42],
          str(f2_560[40:]))
    # WindowType：557 は 36 件、560 は 37 件で 34 chest_boat
    check("557 の WindowType は 36 件、560 は 37 件で 34 が chest_boat",
          mapper_len(types557, "WindowType") == 36
          and mapper_len(types560, "WindowType") == 37
          and types560["WindowType"][1]["mappings"].get("34") == "chest_boat")
    # structure_template_data_export：557 は request 1〜3・response 1〜2、560 は 4 import_from_save・3 import を追加
    def req_map(types):
        for f in types["packet_structure_template_data_export_request"][1]:
            if f["name"] == "request_type":
                return f["type"][1]["mappings"]
        return {}
    def res_map(types):
        for f in types["packet_structure_template_data_export_response"][1]:
            if f["name"] == "response_type":
                return f["type"][1]["mappings"]
        return {}
    check("557 の export request_type は 1〜3、560 は 4 import_from_save を追加",
          set(req_map(types557)) == {"1", "2", "3"}
          and req_map(types560).get("4") == "import_from_save")
    check("557 の export response_type は 1〜2、560 は 3 import を追加",
          set(res_map(types557)) == {"1", "2"}
          and res_map(types560).get("3") == "import")

# ---------- H. ブロックデータ（557 専用表は無いのが現状） ----------
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
check("557 専用のブロック表は無い（1.20.50 世代を共用。データ担当の範囲）",
      not list(RES.glob("bedrock_block_runtime_ids_1_19*"))
      and not list(RES.glob("canonical_block_states_1_19*")))
be557 = TMP / "be-557"
check("1.19.40 の canonical_block_states.nbt（調査用）が存在する",
      (be557 / "canon-bedrock-1.19.40.nbt").is_file())
check("1.19.40 パレットのサイズが 1,225,154 バイト・SHA-1 が a8772a80…と一致",
      (be557 / "canon-bedrock-1.19.40.nbt").is_file()
      and (be557 / "canon-bedrock-1.19.40.nbt").stat().st_size == 1225154
      and sha1(be557 / "canon-bedrock-1.19.40.nbt")
      == "a8772a804c0ba665f8de539886144749a05a03ef")
check("1.19.40 の required_item_list.json（調査用）が存在する",
      (be557 / "req-bedrock-1.19.40-required_item_list.json").is_file())
check("1.19.40 の required_item_list.json が 114,288 バイト・SHA-1 が d5ea3b46…と一致・1,132 名",
      (be557 / "req-bedrock-1.19.40-required_item_list.json").is_file()
      and (be557 / "req-bedrock-1.19.40-required_item_list.json").stat().st_size == 114288
      and sha1(be557 / "req-bedrock-1.19.40-required_item_list.json")
      == "d5ea3b468c9141ff11a8f7f6b38979b7ef08f3f4"
      and len(json.loads((be557 / "req-bedrock-1.19.40-required_item_list.json").read_text())) == 1132)
check("1.19.40 の biome_id_map.json が 1.19.50／1.19.60 のものとバイト同一（SHA-1 c82b9c74…）",
      (be557 / "biomeid-bedrock-1.19.40.json").is_file()
      and sha1(be557 / "biomeid-bedrock-1.19.40.json")
      == "c82b9c745c0e97bd4d3fa4cbf8337e6dccefb2b7")
check("1.19.40 の block_id_map.json が 1.19.50／1.19.60 のものとバイト同一（SHA-1 f3855a10…）",
      (be557 / "blockid-bedrock-1.19.40.json").is_file()
      and sha1(be557 / "blockid-bedrock-1.19.40.json")
      == "f3855a102c698f960020d928bcaef10d25724062")

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
