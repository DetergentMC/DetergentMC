#!/usr/bin/env python3
"""Bedrock 1.19.70（プロトコル 575）の配線検証。

Java ループの verify-735-tables.py と verify-be-2193-tables.py /
verify-be-860-tables.py / verify-be-582-tables.py にならい、表・定数・
述語の前提を機械計数で確認する。すべて実ファイル（BedrockVersion.java /
BedrockProtocol.java / BedrockSession.java / protocol-1.19.70.json /
protocol-1.19.62.json / protocol-1.19.80.json / protocol-1.20.0.json /
.bin データ）に対するチェックで、推測はない。

- 575 の protocol.json は minecraft-data の `data/bedrock/1.19.70/` にある。
- 下側の隣接は 568（1.19.63）だが、minecraft-data に
  `data/bedrock/1.19.63/protocol.json` は存在しない（version.json のみ。
  GitHub API で確認）。そのため下側の比較は入手可能な最寄りの
  567（1.19.62、`data/bedrock/1.19.62/protocol.json`）で代用する。
  569〜574 はベータ版のプロトコル番号（BE-NOTES.md §2.9 の判断待ち）。
- 上側の隣接は 582（1.19.83。protocol.json は `data/bedrock/1.19.80/`）。
- 述語の期待値は BedrockVersion.java の has*() を読んで決めたもの。
  このスクリプトは protocol.json 側の事実（フィールドの有無・順序・
  型）と突き合わせ、述語の前提が 575 で崩れていないことを確認する。
  述語そのものの実行時挙動は BedrockVersionTest で確認する。

使い方: python3 detergentmc-bedrock/tools/verify-be-575-tables.py
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


# ---------- A. protocol.json（575）の同一性 ----------
p170 = TMP / "protocol-1.19.70.json"
check("575 protocol.json が存在する", p170.is_file(), str(p170))
check("575 protocol.json の行数が 11,330 行（wc -l、改行数）",
      p170.is_file()
      and p170.read_text().count("\n") == 11330,
      f"{p170.read_text().count(chr(10))} 行" if p170.is_file() else "なし")
check("575 protocol.json のサイズが 292,921 バイト",
      p170.is_file() and p170.stat().st_size == 292921,
      f"{p170.stat().st_size} バイト" if p170.is_file() else "なし")
check("575 protocol.json の SHA-1 が f786b85a…と一致",
      p170.is_file()
      and sha1(p170) == "f786b85aeca24cb5ee70348e94ecb5f9e81bd6ff",
      sha1(p170) if p170.is_file() else "なし")

data575 = json.loads(p170.read_text())
types575 = data575["types"]
ids575 = packet_ids(data575)

# ---------- A2. 隣接バージョン（567・582）の protocol.json ----------
p162 = TMP / "protocol-1.19.62.json"
check("567 protocol.json（下側の隣接、1.19.63 の protocol.json は存在しないため代用）が存在する",
      p162.is_file(), str(p162))
check("567 protocol.json の行数が 11,265 行・サイズが 291,388 バイト・SHA-1 が e146973c…と一致",
      p162.is_file() and p162.read_text().count("\n") == 11265
      and p162.stat().st_size == 291388
      and sha1(p162) == "e146973c54730e24532624f07123edfed28a15b0",
      f"{p162.read_text().count(chr(10))} 行・{p162.stat().st_size} バイト" if p162.is_file() else "なし")
p180 = TMP / "protocol-1.19.80.json"
check("582 protocol.json（上側の隣接）が存在する", p180.is_file(), str(p180))
check("582 protocol.json のサイズが 296,761 バイト・SHA-1 が 6ffbc225…と一致",
      p180.is_file() and p180.stat().st_size == 296761
      and sha1(p180) == "6ffbc22578675d6294ce5e29e81e42ff1593e8e7",
      f"{p180.stat().st_size} バイト" if p180.is_file() else "なし")

data567 = json.loads(p162.read_text()) if p162.is_file() else None
data582 = json.loads(p180.read_text()) if p180.is_file() else None
ids567 = packet_ids(data567) if data567 else {}
ids582 = packet_ids(data582) if data582 else {}

# ---------- B. パケット一覧（575）の件数・境界 ----------
check("575 のパケット数が 197 件", len(ids575) == 197, f"{len(ids575)} 件")
check("575 の最大パケット ID が 300", max(ids575) == 300, f"最大 {max(ids575)}")
check("567 のパケット数が 194 件", len(ids567) == 194, f"{len(ids567)} 件")
check("567 の最大パケット ID が 197", max(ids567) == 197, f"最大 {max(ids567)}")
check("582 のパケット数が 200 件", len(ids582) == 200, f"{len(ids582)} 件")
check("582 の最大パケット ID が 303", max(ids582) == 303, f"最大 {max(ids582)}")

d567 = TMP / "packet-diff-575-vs-567.json"
d582 = TMP / "packet-diff-575-vs-582.json"
check("575 vs 567 の差分 JSON が存在する", d567.is_file(), str(d567))
check("575 vs 582 の差分 JSON が存在する", d582.is_file(), str(d582))
if d567.is_file() and d582.is_file():
    j567 = json.loads(d567.read_text())
    j582 = json.loads(d582.read_text())
    check("575 vs 567：追加 3 件が 198 camera_presets・199 unlocked_recipes・300 camera_instruction",
          j567["added"] == {"198": "camera_presets",
                            "199": "unlocked_recipes",
                            "300": "camera_instruction"},
          json.dumps(j567["added"]))
    check("575 vs 567：削除 0・リネーム 0",
          j567["removed"] == {} and j567["renamed"] == {})
    check("575 vs 567：型変更 11 件",
          len(j567["changed_types"]) == 11, str(j567["changed_types"]))
    check("575 vs 582：追加 3 件が 301 compressed_biome_definitions・302 trim_data・303 open_sign",
          j582["added"] == {"301": "compressed_biome_definitions",
                            "302": "trim_data", "303": "open_sign"},
          json.dumps(j582["added"]))
    check("575 vs 582：削除 0・リネーム 0",
          j582["removed"] == {} and j582["renamed"] == {})
    check("575 vs 582：型変更 10 件",
          len(j582["changed_types"]) == 10, str(j582["changed_types"]))
    check("575 vs 582：型変更に packet_start_game が含まれる（582 で 3 フィールド追加）",
          "packet_start_game" in j582["changed_types"])

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
json_by_name = {v: k for k, v in ids575.items()}
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
check("enum エントリ V1_19_70 が存在する", "V1_19_70" in entry_by_name)
check("V1_19_70 のプロトコルが 575",
      entry_by_name.get("V1_19_70", (None,))[0] == 575,
      str(entry_by_name.get("V1_19_70")))
check("V1_19_70 の表示名が 1.19.70",
      entry_by_name.get("V1_19_70", (None, None))[1] == "1.19.70")
check("enum エントリが 66 件（64 プロトコル＋2193・2225 の重複各 1）",
      len(entries) == 66, f"{len(entries)} 件")
check("異なるプロトコル番号が 64 個",
      len({int(p) for _, p, _ in entries}) == 64)
check("V1_19_70 が V1_19_83 より先に宣言されている（昇順）",
      ver_src.index("V1_19_70(575") < ver_src.index("V1_19_83(582"))
check("enum の javadoc に 1.19.70 の protocol.json が含まれる",
      "1.19.70" in ver_src)

# ---------- E. itemTableTag とアイテム対応表 ----------
m = re.search(r"case 575 -> \"(1_19_70)\";", ver_src)
check("itemTableTag：575 が 1_19_70 の表を使う", m is not None,
      m.group(0) if m else "case が見つからない")
tag_bin = RES / "bedrock_item_ids_1_19_70.bin"
check("575 専用のアイテム表 bedrock_item_ids_1_19_70.bin が存在する",
      tag_bin.is_file())
check("アイテム表のサイズが 10,496 バイト（1312 件×8）",
      tag_bin.is_file() and tag_bin.stat().st_size == 10496,
      f"{tag_bin.stat().st_size} バイト" if tag_bin.is_file() else "なし")
check("アイテム表の SHA-1 が b999098a…と一致（生成スクリプトの再実行と同一）",
      tag_bin.is_file()
      and sha1(tag_bin) == "b999098a564ef85a8d1bd910909589cac228e3e2",
      sha1(tag_bin) if tag_bin.is_file() else "なし")
check("1.19.75 名のアイテム表は無い（タグは 1_19_70 が正しい）",
      not (RES / "bedrock_item_ids_1_19_75.bin").exists())
item_bins = sorted(RES.glob("bedrock_item_ids_*.bin"))
check("アイテム表 .bin が 38 本", len(item_bins) == 38, f"{len(item_bins)} 本")
summary_path = BE / "tools/item_tables_summary.json"
summary = json.loads(summary_path.read_text()) if summary_path.is_file() else {}
s575 = summary.get("575", {})
check("item_tables_summary.json の 575 が exact=856／shared=264／legacy=33／fallback=159",
      s575.get("exact") == 856 and s575.get("shared") == 264
      and s575.get("legacy") == 33 and s575.get("fallback") == 159,
      str(s575))

# ---------- F. 述語の前提と protocol.json（575）の突き合わせ ----------
sg = field_names(types575, "packet_start_game")
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

rpi = field_names(types575, "packet_resource_packs_info")
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
tpi = types575["TexturePackInfos"]
check("texture_packs の件数が li16（resourcePacksCountIsVarint=false）",
      tpi[0] == "array" and tpi[1].get("countType") == "li16",
      json.dumps(tpi[1].get("countType")) if isinstance(tpi[1], dict) else str(tpi))

rps = field_names(types575, "packet_resource_pack_stack")
check("resource_pack_stack に behavior_packs がある（hasBehaviorPacksInStack=true）",
      "behavior_packs" in rps)
check("resource_pack_stack に has_editor_packs が無い（hasEditorPacksInStack=false）",
      "has_editor_packs" not in rps)

me = field_type(types575, "packet_mob_equipment", "item")
check("mob_equipment の item が旧型 Item（mobEquipmentUsesItemV4=false）",
      me == "Item", str(me))
aie = field_type(types575, "packet_add_item_entity", "item")
check("add_item_entity の item が旧型 Item（addItemUsesItemV4=false）",
      aie == "Item", str(aie))
mae = container_fields(types575, "packet_mob_armor_equipment")
mae_items = [n for n, t in mae if t == "Item"]
check("mob_armor_equipment が旧型 Item×4（helmet/chestplate/leggings/boots）",
      mae_items == ["helmet", "chestplate", "leggings", "boots"],
      str(mae_items))
check("mob_armor_equipment に body が無い（mobArmorHasBodySlot=false）",
      "body" not in mae_items)

legacy = types575["TransactionLegacy"]
legacy_map = {f["name"]: f["type"] for f in legacy[1]}
legacy_tx = legacy_map.get("legacy_transactions")
check("Transaction の legacy_transactions が switch 形式（transactionLegacyIsOption=false）",
      isinstance(legacy_tx, list) and len(legacy_tx) >= 1
      and legacy_tx[0] == "switch"
      and legacy_tx[1].get("compareTo") == "legacy_request_id",
      json.dumps(legacy_tx)[:80])
tx = types575["Transaction"]
tx_map = {f["name"]: f["type"] for f in tx[1]}
check("Transaction の transaction_type／actions が素のフィールド（transactionFieldsAreOptions=false）",
      (isinstance(tx_map.get("transaction_type"), list)
       and tx_map["transaction_type"][0] == "mapper"
       and tx_map["transaction_type"][1].get("type") == "varint")
      and tx_map.get("actions") == "TransactionActions",
      f"type={json.dumps(tx_map.get('transaction_type'))[:60]} actions={tx_map.get('actions')}")
ta = types575["TransactionActions"]
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

# ---------- G. 567／582 の境界 ----------
check("567 に 198 camera_presets・199 unlocked_recipes・300 camera_instruction が無い（境界は 567／575 間）",
      198 not in ids567 and 199 not in ids567 and 300 not in ids567)
if data567:
    sg567 = field_names(data567["types"], "packet_start_game")
    check("567 の start_game に created_in_editor 等が無い（575 と同じ）",
          not any(t in sg567 for t in ["created_in_editor", "exported_from_editor",
                                       "block_network_ids_are_hashes"]))
if data582:
    sg582 = field_names(data582["types"], "packet_start_game")
    check("582 の start_game に created_in_editor／exported_from_editor／block_network_ids_are_hashes がある（境界は 575／582 間）",
          all(t in sg582 for t in ["created_in_editor", "exported_from_editor",
                                   "block_network_ids_are_hashes"]))
    check("582 の start_game に server_controlled_sound が無い（589 で追加されるもの）",
          "server_controlled_sound" not in sg582)
p200 = TMP / "protocol-1.20.0.json"
if p200.is_file():
    data589 = json.loads(p200.read_text())
    sg589 = field_names(data589["types"], "packet_start_game")
    check("589 の start_game に server_controlled_sound がある（境界は 582／589 間）",
          "server_controlled_sound" in sg589)
check("575 に 301 compressed_biome_definitions・302 trim_data・303 open_sign が無い（境界は 575／582 間）",
      301 not in ids575 and 302 not in ids575 and 303 not in ids575)

# ---------- H. ブロックデータ（575 専用表は無いのが現状） ----------
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
check("575 専用のブロック表は無い（1.20.50 世代を共用。データ担当の範囲）",
      not list(RES.glob("bedrock_block_runtime_ids_1_19*"))
      and not list(RES.glob("canonical_block_states_1_19*")))
be575 = TMP / "be-575"
check("1.19.70 の canonical_block_states.nbt（調査用）が存在する",
      (be575 / "canon-bedrock-1.19.70.nbt").is_file())
check("1.19.70 パレットのサイズが 1,758,270 バイト・SHA-1 が 48cc835a…と一致",
      (be575 / "canon-bedrock-1.19.70.nbt").is_file()
      and (be575 / "canon-bedrock-1.19.70.nbt").stat().st_size == 1758270
      and sha1(be575 / "canon-bedrock-1.19.70.nbt")
      == "48cc835a7acf40689c2d9734c0c6fb19070af0dd")
check("1.19.70 の required_item_list.json（調査用）が存在する",
      (be575 / "req-bedrock-1.19.70-required_item_list.json").is_file())
check("1.19.70 の required_item_list.json が 120,875 バイト・SHA-1 が fcbf8403…と一致・1,196 名",
      (be575 / "req-bedrock-1.19.70-required_item_list.json").is_file()
      and (be575 / "req-bedrock-1.19.70-required_item_list.json").stat().st_size == 120875
      and sha1(be575 / "req-bedrock-1.19.70-required_item_list.json")
      == "fcbf8403f915b738ec23f30065af9260408ef4d9"
      and len(json.loads((be575 / "req-bedrock-1.19.70-required_item_list.json").read_text())) == 1196)

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
