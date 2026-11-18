#!/usr/bin/env python3
"""Bedrock 1.19.51（プロトコル 560）の配線検証。

Java ループの verify-735-tables.py と verify-be-2193-tables.py /
verify-be-860-tables.py / verify-be-582-tables.py / verify-be-575-tables.py /
verify-be-567-tables.py にならい、表・定数・述語の前提を機械計数で
確認する。すべて実ファイル（BedrockVersion.java / BedrockProtocol.java /
BedrockSession.java / protocol-1.19.50.json / protocol-1.19.40.json /
protocol-1.19.62.json / .bin データ）に対するチェックで、推測はない。

- 560 の protocol.json は minecraft-data の `data/bedrock/1.19.50/` にある。
- 560 は 1.19.50／1.19.51 で共有するプロトコル（Minecraft Wiki
  「Bedrock Edition 1.19.50」／「Bedrock Edition 1.19.51」で確認。
  protocolVersions.json には 1.19.50 しか載っていないが、同ファイルは
  582 も 1.19.80 しか載せていないためホットフィックスの網羅性は無い）。
- 561〜566 はベータ／プレビュー版のプロトコル番号（561 は Preview
  1.19.60.20）。protocolVersions.json に 561〜566 のエントリは無い。
  568 とベータ 569〜574 を飛ばしたのと同じ扱いで飛ばす（BE-NOTES.md §6）。
- 560 の 1 つ下は 557（1.19.40。protocol.json は `data/bedrock/1.19.40/`）。
  上側の隣接は 567（1.19.62。protocol.json は `data/bedrock/1.19.62/`）。
- 述語の期待値は BedrockVersion.java の has*() を読んで決めたもの。
  このスクリプトは protocol.json 側の事実（フィールドの有無・順序・
  型）と突き合わせ、述語の前提が 560 で崩れていないことを確認する。
  述語そのものの実行時挙動は BedrockVersionTest で確認する。

使い方: python3 detergentmc-bedrock/tools/verify-be-560-tables.py
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


# ---------- A. protocol.json（560）の同一性 ----------
p150 = TMP / "protocol-1.19.50.json"
check("560 protocol.json が存在する", p150.is_file(), str(p150))
check("560 protocol.json の行数が 11,179 行（wc -l、改行数）",
      p150.is_file()
      and p150.read_text().count("\n") == 11179,
      f"{p150.read_text().count(chr(10))} 行" if p150.is_file() else "なし")
check("560 protocol.json のサイズが 288,987 バイト",
      p150.is_file() and p150.stat().st_size == 288987,
      f"{p150.stat().st_size} バイト" if p150.is_file() else "なし")
check("560 protocol.json の SHA-1 が 5fd84ac8…と一致",
      p150.is_file()
      and sha1(p150) == "5fd84ac85c9ed237e5aa8dc680a0c7cb87c5b9bd",
      sha1(p150) if p150.is_file() else "なし")

data560 = json.loads(p150.read_text())
types560 = data560["types"]
ids560 = packet_ids(data560)

# ---------- A2. 隣接バージョン（567・557）の protocol.json ----------
p162 = TMP / "protocol-1.19.62.json"
check("567 protocol.json（上側の隣接）が存在する", p162.is_file(), str(p162))
check("567 protocol.json の行数が 11,265 行・サイズが 291,388 バイト・SHA-1 が e146973c…と一致",
      p162.is_file() and p162.read_text().count("\n") == 11265
      and p162.stat().st_size == 291388
      and sha1(p162) == "e146973c54730e24532624f07123edfed28a15b0",
      f"{p162.read_text().count(chr(10))} 行・{p162.stat().st_size} バイト" if p162.is_file() else "なし")
p140 = TMP / "protocol-1.19.40.json"
check("557 protocol.json（下側の隣接）が存在する", p140.is_file(), str(p140))
check("557 protocol.json の行数が 11,130 行・サイズが 287,657 バイト・SHA-1 が f3f81376…と一致",
      p140.is_file() and p140.read_text().count("\n") == 11130
      and p140.stat().st_size == 287657
      and sha1(p140) == "f3f8137679253a10f2dfafb74d3c4be10d519883",
      f"{p140.read_text().count(chr(10))} 行・{p140.stat().st_size} バイト" if p140.is_file() else "なし")
check("1.19.51 の protocol.json は .tmp-be に存在しない（1.19.50 と同じ 560 を共有し、minecraft-data に 1.19.51 のディレクトリは無い）",
      not (TMP / "protocol-1.19.51.json").is_file())
check("561〜566 の protocol.json は .tmp-be に存在しない（ベータ／プレビュー版のため飛ばす）",
      not any((TMP / name).is_file() for name in [
          "protocol-1.19.60.json",
          "protocol-1.19.61.json", "protocol-1.19.63.json",
          "protocol-1.19.64.json", "protocol-1.19.65.json",
          "protocol-1.19.66.json"]))
pver = TMP / "protocolVersions.json"
check("protocolVersions.json が存在する", pver.is_file(), str(pver))
if pver.is_file():
    pv = json.loads(pver.read_text())
    check("protocolVersions.json に 561〜566 のエントリが無い",
          not any(561 <= e["version"] <= 566 for e in pv))
    check("protocolVersions.json の 560 は 1.19.50 のみ（1.19.51 は同ファイルに無い）",
          [e["minecraftVersion"] for e in pv if e["version"] == 560] == ["1.19.50"])
    check("protocolVersions.json の 560 の 1 つ下は 557（1.19.40）",
          max(e["version"] for e in pv if e["version"] < 560) == 557)

data567 = json.loads(p162.read_text()) if p162.is_file() else None
data557 = json.loads(p140.read_text()) if p140.is_file() else None
ids567 = packet_ids(data567) if data567 else {}
ids557 = packet_ids(data557) if data557 else {}

# ---------- B. パケット一覧（560）の件数・境界 ----------
check("560 のパケット数が 193 件", len(ids560) == 193, f"{len(ids560)} 件")
check("560 の最大パケット ID が 196", max(ids560) == 196, f"最大 {max(ids560)}")
check("567 のパケット数が 194 件", len(ids567) == 194, f"{len(ids567)} 件")
check("567 の最大パケット ID が 197", max(ids567) == 197, f"最大 {max(ids567)}")
check("557 のパケット数が 192 件", len(ids557) == 192, f"{len(ids557)} 件")
check("557 の最大パケット ID が 195", max(ids557) == 195, f"最大 {max(ids557)}")

d567 = TMP / "packet-diff-560-vs-567.json"
d557 = TMP / "packet-diff-557-vs-560.json"
check("560 vs 567 の差分 JSON が存在する", d567.is_file(), str(d567))
check("557 vs 560 の差分 JSON が存在する", d557.is_file(), str(d557))
if d567.is_file() and d557.is_file():
    j567 = json.loads(d567.read_text())
    j557 = json.loads(d557.read_text())
    check("560 vs 567：追加 1 件が 197 client_cheat_ability",
          j567["added"] == {"197": "client_cheat_ability"},
          json.dumps(j567["added"]))
    check("560 vs 567：削除 0・リネーム 0",
          j567["removed"] == {} and j567["renamed"] == {})
    check("560 vs 567：型変更 10 件",
          len(j567["changed_types"]) == 10, str(j567["changed_types"]))
    check("560 vs 567：型変更に Action・InputFlag・Recipes・Skin・SoundType・packet_command_request・packet_emote・packet_start_game が含まれる",
          all(t in j567["changed_types"] for t in ["Action", "InputFlag",
                                                   "Recipes", "Skin", "SoundType",
                                                   "packet_command_request",
                                                   "packet_emote", "packet_start_game"]))
    check("560 vs 567：型変更に ItemStackRequest は含まれない（cause は 560＝567 で 0〜13）",
          "ItemStackRequest" not in j567["changed_types"])
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
json_by_name = {v: k for k, v in ids560.items()}
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
check("enum エントリ V1_19_51 が存在する", "V1_19_51" in entry_by_name)
check("V1_19_51 のプロトコルが 560",
      entry_by_name.get("V1_19_51", (None,))[0] == 560,
      str(entry_by_name.get("V1_19_51")))
check("V1_19_51 の表示名が 1.19.51",
      entry_by_name.get("V1_19_51", (None, None))[1] == "1.19.51")
check("enum エントリが 66 件（64 プロトコル＋2193・2225 の重複各 1）",
      len(entries) == 66, f"{len(entries)} 件")
check("異なるプロトコル番号が 64 個",
      len({int(p) for _, p, _ in entries}) == 64)
check("V1_19_51 が V1_19_62 より先に宣言されている（昇順・最古端）",
      ver_src.index("V1_19_51(560") < ver_src.index("V1_19_62(567"))
check("enum の javadoc に 1.19.50 の protocol.json が含まれる",
      "1.19.50" in ver_src)
check("enum の javadoc に 560 は 1.19.50／1.19.51 で共有する旨がある",
      "1.19.50" in ver_src and "1.19.51" in ver_src)
check("enum の javadoc の protocol.json が 51 ファイルになっている",
      "51 ファイル" in ver_src)

# ---------- E. itemTableTag とアイテム対応表 ----------
m = re.search(r"case 560 -> \"(1_19_50)\";", ver_src)
check("itemTableTag：560 が 1_19_50 の表を使う", m is not None,
      m.group(0) if m else "case が見つからない")
tag_bin = RES / "bedrock_item_ids_1_19_50.bin"
check("560 専用のアイテム表 bedrock_item_ids_1_19_50.bin が存在する",
      tag_bin.is_file())
check("アイテム表のサイズが 10,496 バイト（1312 件×8）",
      tag_bin.is_file() and tag_bin.stat().st_size == 10496,
      f"{tag_bin.stat().st_size} バイト" if tag_bin.is_file() else "なし")
check("アイテム表の SHA-1 が eb690f6d…と一致（生成スクリプトの再実行と同一）",
      tag_bin.is_file()
      and sha1(tag_bin) == "eb690f6ddd19f40e4ddbe731d8da31e49a4d6195",
      sha1(tag_bin) if tag_bin.is_file() else "なし")
check("1.19.51 名のアイテム表は無い（タグは 1_19_50 が正しい）",
      not (RES / "bedrock_item_ids_1_19_51.bin").exists())
item_bins = sorted(RES.glob("bedrock_item_ids_*.bin"))
check("アイテム表 .bin が 38 本", len(item_bins) == 38, f"{len(item_bins)} 本")
summary_path = BE / "tools/item_tables_summary.json"
summary = json.loads(summary_path.read_text()) if summary_path.is_file() else {}
s560 = summary.get("560", {})
check("item_tables_summary.json の 560 が tag=bedrock-1.19.50・exact=828／shared=264／legacy=33／fallback=187",
      s560.get("tag") == "bedrock-1.19.50" and s560.get("exact") == 828
      and s560.get("shared") == 264 and s560.get("legacy") == 33
      and s560.get("fallback") == 187,
      str({k: s560.get(k) for k in ["tag", "exact", "shared", "legacy", "fallback"]}))

# ---------- F. 述語の前提と protocol.json（560）の突き合わせ ----------
sg = field_names(types560, "packet_start_game")
check("start_game が 70 フィールド（557 と同じ）", len(sg) == 70, f"{len(sg)} フィールド")
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

rpi = field_names(types560, "packet_resource_packs_info")
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
tpi = types560["TexturePackInfos"]
check("texture_packs の件数が li16（resourcePacksCountIsVarint=false）",
      tpi[0] == "array" and tpi[1].get("countType") == "li16",
      json.dumps(tpi[1].get("countType")) if isinstance(tpi[1], dict) else str(tpi))

rps = field_names(types560, "packet_resource_pack_stack")
check("resource_pack_stack に behavior_packs がある（hasBehaviorPacksInStack=true）",
      "behavior_packs" in rps)
check("resource_pack_stack に has_editor_packs が無い（hasEditorPacksInStack=false）",
      "has_editor_packs" not in rps)

me = field_type(types560, "packet_mob_equipment", "item")
check("mob_equipment の item が旧型 Item（mobEquipmentUsesItemV4=false）",
      me == "Item", str(me))
aie = field_type(types560, "packet_add_item_entity", "item")
check("add_item_entity の item が旧型 Item（addItemUsesItemV4=false）",
      aie == "Item", str(aie))
mae = container_fields(types560, "packet_mob_armor_equipment")
mae_items = [n for n, t in mae if t == "Item"]
check("mob_armor_equipment が旧型 Item×4（helmet/chestplate/leggings/boots）",
      mae_items == ["helmet", "chestplate", "leggings", "boots"],
      str(mae_items))
check("mob_armor_equipment に body が無い（mobArmorHasBodySlot=false）",
      "body" not in mae_items)
check("ItemV4 型が 560 の protocol.json に存在しない",
      "ItemV4" not in types560)

legacy = types560["TransactionLegacy"]
legacy_map = {f["name"]: f["type"] for f in legacy[1]}
legacy_tx = legacy_map.get("legacy_transactions")
check("Transaction の legacy_transactions が switch 形式（transactionLegacyIsOption=false）",
      isinstance(legacy_tx, list) and len(legacy_tx) >= 1
      and legacy_tx[0] == "switch"
      and legacy_tx[1].get("compareTo") == "legacy_request_id",
      json.dumps(legacy_tx)[:80])
tx = types560["Transaction"]
tx_map = {f["name"]: f["type"] for f in tx[1]}
check("Transaction の transaction_type／actions が素のフィールド（transactionFieldsAreOptions=false）",
      (isinstance(tx_map.get("transaction_type"), list)
       and tx_map["transaction_type"][0] == "mapper"
       and tx_map["transaction_type"][1].get("type") == "varint")
      and tx_map.get("actions") == "TransactionActions",
      f"type={json.dumps(tx_map.get('transaction_type'))[:60]} actions={tx_map.get('actions')}")
ta = types560["TransactionActions"]
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

# 560 と 567 の主要パケットが JSON 完全一致（StartGame・emote・command_request を除く）
if data567:
    types567 = data567["types"]
    for pname in ["packet_resource_packs_info",
                  "packet_resource_pack_stack", "packet_mob_equipment",
                  "packet_add_item_entity", "packet_mob_armor_equipment",
                  "packet_inventory_transaction", "packet_level_chunk",
                  "packet_add_player", "packet_add_entity",
                  "packet_update_block", "packet_move_player",
                  "packet_set_entity_data", "packet_player_auth_input", "Item"]:
        check(f"{pname} が 560 と 567 で JSON 完全一致",
              types560.get(pname) == types567.get(pname))
    check("packet_start_game は 560 と 567 で異なる（567 に emote_chat_muted が追加）",
          types560.get("packet_start_game") != types567.get("packet_start_game"))
    sg567 = field_names(types567, "packet_start_game")
    check("567 の start_game は 71 フィールドで 560 との差分は emote_chat_muted のみ",
          len(sg567) == 71 and [f for f in sg567 if f not in sg] == ["emote_chat_muted"]
          and [f for f in sg if f not in sg567] == [])
    check("packet_emote は 560 と 567 で異なる（567 は flags が mapper）",
          types560.get("packet_emote") != types567.get("packet_emote"))
    check("packet_command_request は 560 と 567 で異なる（567 は末尾に version）",
          types560.get("packet_command_request") != types567.get("packet_command_request"))

# 560 と 557 の主要パケットが JSON 完全一致（StartGame・ResourcePacks・Item・Transaction は同じ経路）
if data557:
    types557 = data557["types"]
    for pname in ["packet_start_game", "packet_resource_packs_info",
                  "packet_resource_pack_stack", "packet_mob_equipment",
                  "packet_add_item_entity", "packet_mob_armor_equipment",
                  "packet_inventory_transaction", "packet_level_chunk",
                  "packet_add_player", "packet_add_entity",
                  "packet_update_block", "packet_move_player",
                  "packet_set_entity_data"]:
        check(f"{pname} が 557 と 560 で JSON 完全一致",
              types560.get(pname) == types557.get(pname))

# ---------- G. 560／567／557 の境界 ----------
check("560 に 197 client_cheat_ability が無い（境界は 560／567 間）",
      197 not in ids560)
check("567 に 197 client_cheat_ability がある（境界は 560／567 間）",
      197 in ids567)
check("557 に 196 update_client_input_locks が無い（境界は 557／560 間）",
      196 not in ids557)
check("560 に 196 update_client_input_locks がある（境界は 557／560 間）",
      196 in ids560)
if data567 and data557:
    # ItemStackRequest の cause マッパー：557 は 11 まで、560・567 は 12・13 まで
    def cause_mappings(types):
        for f in types["ItemStackRequest"][1]:
            if f.get("name") == "cause":
                return f["type"][1]["mappings"]
        return {}
    c557 = cause_mappings(data557["types"])
    c560 = cause_mappings(types560)
    c567 = cause_mappings(data567["types"])
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
          mapper_len(data557["types"], "ContainerSlotType") == 60)
    check("560 の ContainerSlotType は 61 件で 21 が recipe_book",
          mapper_len(types560, "ContainerSlotType") == 61
          and types560["ContainerSlotType"][1]["mappings"].get("21") == "recipe_book")
    check("567 の ContainerSlotType も 61 件で 21 が recipe_book（560＝567）",
          mapper_len(data567["types"], "ContainerSlotType") == 61
          and data567["types"]["ContainerSlotType"][1]["mappings"].get("21") == "recipe_book")
    # SoundType：557 は 448 件、560 は 458 件、567 は 462 件
    check("557 の SoundType は 448 件、560 は 458 件、567 は 462 件",
          mapper_len(data557["types"], "SoundType") == 448
          and mapper_len(types560, "SoundType") == 458
          and mapper_len(data567["types"], "SoundType") == 462)
    check("560 の SoundType は 457 FenceGateClose まで（458 Insert・459 Pickup・460 InsertEnchanted・461 PickupEnchanted は 567 で追加）",
          types560["SoundType"][1]["mappings"].get("457") == "FenceGateClose"
          and "458" not in types560["SoundType"][1]["mappings"]
          and data567["types"]["SoundType"][1]["mappings"].get("458") == "Insert"
          and data567["types"]["SoundType"][1]["mappings"].get("461") == "PickupEnchanted")
    check("557 の SoundType の 443・444 は _（プレースホルダ）で、560 では StepSand・DashReady",
          data557["types"]["SoundType"][1]["mappings"].get("443") == "_"
          and data557["types"]["SoundType"][1]["mappings"].get("444") == "_"
          and types560["SoundType"][1]["mappings"].get("443") == "StepSand"
          and types560["SoundType"][1]["mappings"].get("444") == "DashReady")
    # MetadataFlags1：557 は 64、560 は 65 で can_dash が index 46 に挿入
    f1_557 = data557["types"]["MetadataFlags1"][1]["flags"]
    f1_560 = types560["MetadataFlags1"][1]["flags"]
    check("557 の MetadataFlags1 は 64 件、560 は 65 件",
          len(f1_557) == 64 and len(f1_560) == 65)
    check("560 の MetadataFlags1 の index 46 が can_dash（linger の直前）",
          f1_560[46] == "can_dash" and f1_560[47] == "linger",
          str(f1_560[44:49]))
    # WindowType：557 は 36 件、560 は 37 件で 34 chest_boat
    check("557 の WindowType は 36 件、560 は 37 件で 34 が chest_boat",
          mapper_len(data557["types"], "WindowType") == 36
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
          set(req_map(data557["types"])) == {"1", "2", "3"}
          and req_map(types560).get("4") == "import_from_save")
    check("557 の export response_type は 1〜2、560 は 3 import を追加",
          set(res_map(data557["types"])) == {"1", "2"}
          and res_map(types560).get("3") == "import")

# ---------- H. ブロックデータ（560 専用表は無いのが現状） ----------
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
check("560 専用のブロック表は無い（1.20.50 世代を共用。データ担当の範囲）",
      not list(RES.glob("bedrock_block_runtime_ids_1_19*"))
      and not list(RES.glob("canonical_block_states_1_19*")))
be560 = TMP / "be-560"
check("1.19.50 の canonical_block_states.nbt（調査用）が存在する",
      (be560 / "canon-bedrock-1.19.50.nbt").is_file())
check("1.19.50 パレットのサイズが 1,755,680 バイト・SHA-1 が 3bf9950b…と一致",
      (be560 / "canon-bedrock-1.19.50.nbt").is_file()
      and (be560 / "canon-bedrock-1.19.50.nbt").stat().st_size == 1755680
      and sha1(be560 / "canon-bedrock-1.19.50.nbt")
      == "3bf9950bacc592dedb15b11ddab8c820d0450394")
check("1.19.50 の required_item_list.json（調査用）が存在する",
      (be560 / "req-bedrock-1.19.50-required_item_list.json").is_file())
check("1.19.50 の required_item_list.json が 117,531 バイト・SHA-1 が 831a9f17…と一致・1,163 名",
      (be560 / "req-bedrock-1.19.50-required_item_list.json").is_file()
      and (be560 / "req-bedrock-1.19.50-required_item_list.json").stat().st_size == 117531
      and sha1(be560 / "req-bedrock-1.19.50-required_item_list.json")
      == "831a9f17930ce681d3ebdaab0e8d28e645c09024"
      and len(json.loads((be560 / "req-bedrock-1.19.50-required_item_list.json").read_text())) == 1163)
check("1.19.50 の biome_id_map.json が 1.19.60 のものとバイト同一（SHA-1 c82b9c74…）",
      (be560 / "biomeid-bedrock-1.19.50.json").is_file()
      and sha1(be560 / "biomeid-bedrock-1.19.50.json")
      == "c82b9c745c0e97bd4d3fa4cbf8337e6dccefb2b7")
check("1.19.50 の block_id_map.json が 1.19.60 のものとバイト同一（SHA-1 f3855a10…）",
      (be560 / "blockid-bedrock-1.19.50.json").is_file()
      and sha1(be560 / "blockid-bedrock-1.19.50.json")
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
