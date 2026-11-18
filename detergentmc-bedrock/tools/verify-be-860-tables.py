#!/usr/bin/env python3
"""Bedrock 1.21.124（プロトコル 860）の配線検証。

Java ループの verify-735-tables.py と verify-be-2193-tables.py にならい、
表・定数・述語の前提を機械計数で確認する。すべて実ファイル
（BedrockVersion.java / BedrockProtocol.java / BedrockSession.java /
protocol-1.21.124.json / protocol-1.21.120.json / protocol-1.21.130.json /
.bin データ）に対するチェックで、推測はない。

- 860 の protocol.json は .tmp-be/protocol-1.21.124.json。859
  （1.21.120）の protocol.json とバイト同一のホットフィックス
  （2025-11-21。コンテンツ追加なし）で、プロトコル番号だけが
  859→860 に上がる。
- 898 の protocol.json は .tmp-be/protocol-1.21.130.json（境界確認用。
  tick_death_systems と behavior_packs が消えるのは 898）。
- 述語の期待値は BedrockVersion.java の has*() を読んで決めたもの。
  このスクリプトは protocol.json 側の事実（フィールドの有無・順序・
  型）と突き合わせ、述語の前提が 860 で崩れていないことを確認する。
  述語そのものの実行時挙動は BedrockVersionTest で確認する。

使い方: python3 detergentmc-bedrock/tools/verify-be-860-tables.py
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


def is_option(t):
    return isinstance(t, list) and len(t) >= 1 and t[0] == "option"


# ---------- A. protocol.json（860）の同一性 ----------
p124 = TMP / "protocol-1.21.124.json"
check("860 protocol.json が存在する", p124.is_file(), str(p124))
check("860 protocol.json のサイズが 374,860 バイト",
      p124.is_file() and p124.stat().st_size == 374860,
      f"{p124.stat().st_size} バイト" if p124.is_file() else "なし")
check("860 protocol.json の SHA-1 が 773b71ab…と一致",
      p124.is_file()
      and sha1(p124) == "773b71ab3fdde3261931364352a0905cf67e5518",
      sha1(p124) if p124.is_file() else "なし")

data860 = json.loads(p124.read_text())
types860 = data860["types"]
mapper860 = types860["mcpe_packet"][1][0]["type"][1]["mappings"]
ids860 = {int(k): v for k, v in mapper860.items()}

# ---------- A2. 859（1.21.120）とのバイト同一性 ----------
p120 = TMP / "protocol-1.21.120.json"
check("859 protocol.json が存在する", p120.is_file(), str(p120))
check("859 protocol.json のサイズが 374,860 バイト（860 と同じ）",
      p120.is_file() and p120.stat().st_size == 374860,
      f"{p120.stat().st_size} バイト" if p120.is_file() else "なし")
check("859 protocol.json の SHA-1 が 860 と同一（ホットフィックス）",
      p120.is_file() and p124.is_file() and sha1(p120) == sha1(p124),
      sha1(p120) if p120.is_file() else "なし")

# ---------- B. パケット一覧（860）の件数・境界 ----------
check("860 のパケット数が 224 件", len(ids860) == 224, f"{len(ids860)} 件")
check("860 の最大パケット ID が 331", max(ids860) == 331, f"最大 {max(ids860)}")
p130 = TMP / "protocol-1.21.130.json"
check("898 protocol.json（境界確認用）が存在する", p130.is_file(), str(p130))
if p130.is_file():
    data130 = json.loads(p130.read_text())
    mapper130 = data130["types"]["mcpe_packet"][1][0]["type"][1]["mappings"]
    ids130 = {int(k): v for k, v in mapper130.items()}
    check("898 のパケット数が 226 件", len(ids130) == 226, f"{len(ids130)} 件")
    check("898 の最大パケット ID が 332", max(ids130) == 332, f"最大 {max(ids130)}")
    check("898 の追加 2 件が 330 clientbound_data_store・332 serverbound_data_store",
          ids130.get(330) == "clientbound_data_store"
          and ids130.get(332) == "serverbound_data_store",
          f"330={ids130.get(330)} 332={ids130.get(332)}")
    check("898 の追加パケットが 860 に無い",
          330 not in ids860 and 332 not in ids860)

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
json_by_name = {v: k for k, v in ids860.items()}
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
check("enum エントリ V1_21_124 が存在する", "V1_21_124" in entry_by_name)
check("V1_21_124 のプロトコルが 860",
      entry_by_name.get("V1_21_124", (None,))[0] == 860,
      str(entry_by_name.get("V1_21_124")))
check("V1_21_124 の表示名が 1.21.124",
      entry_by_name.get("V1_21_124", (None, None))[1] == "1.21.124")
check("enum エントリが 66 件（64 プロトコル＋2193・2225 の重複各 1）",
      len(entries) == 66, f"{len(entries)} 件")
check("異なるプロトコル番号が 64 個",
      len({int(p) for _, p, _ in entries}) == 64)
check("V1_21_124 が V1_21_120 と V1_21_130 の間に宣言されている",
      ver_src.index("V1_21_120(859") < ver_src.index("V1_21_124(860")
      < ver_src.index("V1_21_130(898"))
check("enum の javadoc に 1.21.124 の protocol.json が含まれる",
      "1.21.124" in ver_src)

# ---------- E. itemTableTag とアイテム対応表 ----------
m = re.search(r"case 859, 860 -> \"(1_21_120)\";", ver_src)
check("itemTableTag：860 が 1_21_120 の表を共用する", m is not None,
      m.group(0) if m else "case が見つからない")
tag_bin = RES / "bedrock_item_ids_1_21_120.bin"
check("共用するアイテム表 bedrock_item_ids_1_21_120.bin が存在する",
      tag_bin.is_file())
check("アイテム表のサイズが 10,496 バイト（1312 件×8）",
      tag_bin.is_file() and tag_bin.stat().st_size == 10496,
      f"{tag_bin.stat().st_size} バイト" if tag_bin.is_file() else "なし")
check("1.21.124 専用のアイテム表は無い（共用のため。無いのが正しい）",
      not (RES / "bedrock_item_ids_1_21_124.bin").exists())
item_bins = sorted(RES.glob("bedrock_item_ids_*.bin"))
check("アイテム表 .bin が 38 本", len(item_bins) == 38, f"{len(item_bins)} 本")

# ---------- F. 述語の前提と protocol.json（860）の突き合わせ ----------
sg = field_names(types860, "packet_start_game")
check("start_game に hardcore がある（hasHardcore=true）", "hardcore" in sg)
check("start_game に is_chat_logging が無い（hasChatLogging=false）",
      "is_chat_logging" not in sg)
check("start_game に server_editor_connection_policy が無い（hasEditorPolicy=false）",
      "server_editor_connection_policy" not in sg)
check("start_game に has_server_join_info／server_join_info が無い（hasJoinInfoTail=false）",
      "has_server_join_info" not in sg and "server_join_info" not in sg)
ident4 = ["server_identifier", "world_identifier",
          "scenario_identifier", "owner_identifier"]
check("start_game の識別子 4 つが level_id の前にある（4 識別子形式）",
      all(t in sg for t in ident4)
      and all(sg.index(t) < sg.index("level_id") for t in ident4))
check("識別子 4 つのうち owner も level_id の前にある（hasOwnerIdentifier=true）",
      "owner_identifier" in sg
      and sg.index("owner_identifier") < sg.index("level_id"))
check("start_game に movement_authority が無い（hasMovementAuthority=false）",
      "movement_authority" not in sg)
check("start_game に itemstates が無い（hasItemstates=false）",
      "itemstates" not in sg)
check("start_game に tick_death_systems がある（hasTickDeathSystems=true）",
      "tick_death_systems" in sg)

rpi = field_names(types860, "packet_resource_packs_info")
check("resource_packs_info に has_addons がある（hasAddons=true）",
      "has_addons" in rpi)
check("resource_packs_info に disable_vibrant_visuals がある（hasDisableVibrantVisuals=true）",
      "disable_vibrant_visuals" in rpi)
check("resource_packs_info に world_template がある（hasWorldTemplate=true）",
      "world_template" in rpi)
check("resource_packs_info に force_server_packs が無い（hasForceServerPacks=false）",
      "force_server_packs" not in rpi)
check("resource_packs_info に behaviour_packs が無い（hasBehaviourPacks=false）",
      "behaviour_packs" not in rpi)
check("resource_packs_info に resource_pack_links が無い（hasResourcePackLinks=false）",
      "resource_pack_links" not in rpi)
tpi = types860["TexturePackInfos"]
check("texture_packs の件数が li16（resourcePacksCountIsVarint=false）",
      tpi[0] == "array" and tpi[1].get("countType") == "li16",
      json.dumps(tpi[1].get("countType")) if isinstance(tpi[1], dict) else str(tpi))

rps = field_names(types860, "packet_resource_pack_stack")
check("resource_pack_stack に behavior_packs がある（hasBehaviorPacksInStack=true）",
      "behavior_packs" in rps)
check("resource_pack_stack に has_editor_packs がある（hasEditorPacksInStack=true）",
      "has_editor_packs" in rps)

me = field_type(types860, "packet_mob_equipment", "item")
check("mob_equipment の item が旧型 Item（mobEquipmentUsesItemV4=false）",
      me == "Item", str(me))
aie = field_type(types860, "packet_add_item_entity", "item")
check("add_item_entity の item が旧型 Item（addItemUsesItemV4=false）",
      aie == "Item", str(aie))
mae = container_fields(types860, "packet_mob_armor_equipment")
mae_items = [n for n, t in mae if t == "Item"]
check("mob_armor_equipment が旧型 Item×5（helmet/chestplate/leggings/boots/body）",
      mae_items == ["helmet", "chestplate", "leggings", "boots", "body"],
      str(mae_items))
check("mob_armor_equipment に body がある（mobArmorHasBodySlot=true）",
      "body" in mae_items)

legacy = types860["TransactionLegacy"]
legacy_map = {f["name"]: f["type"] for f in legacy[1]}
legacy_tx = legacy_map.get("legacy_transactions")
check("Transaction の legacy_transactions が switch 形式（transactionLegacyIsOption=false）",
      isinstance(legacy_tx, list) and len(legacy_tx) >= 1
      and legacy_tx[0] == "switch"
      and legacy_tx[1].get("compareTo") == "legacy_request_id",
      json.dumps(legacy_tx)[:80])
tx = types860["Transaction"]
tx_map = {f["name"]: f["type"] for f in tx[1]}
check("Transaction の transaction_type／actions が素のフィールド（transactionFieldsAreOptions=false）",
      (isinstance(tx_map.get("transaction_type"), list)
       and tx_map["transaction_type"][0] == "mapper"
       and tx_map["transaction_type"][1].get("type") == "varint")
      and tx_map.get("actions") == "TransactionActions",
      f"type={json.dumps(tx_map.get('transaction_type'))[:60]} actions={tx_map.get('actions')}")
ta = types860["TransactionActions"]
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

# ---------- G. 898 の境界（tick_death_systems・behavior_packs は 898 で消える） ----------
if p130.is_file():
    types130 = data130["types"]
    sg130 = field_names(types130, "packet_start_game")
    check("898 の start_game に tick_death_systems が無い（境界は 860／898 間）",
          "tick_death_systems" not in sg130)
    rps130 = field_names(types130, "packet_resource_pack_stack")
    check("898 の resource_pack_stack に behavior_packs が無い（境界は 860／898 間）",
          "behavior_packs" not in rps130)
    check("898 の start_game にも識別子 4 つが level_id の前にある（hasOwnerIdentifier=true）",
          all(t in sg130 for t in ident4)
          and all(sg130.index(t) < sg130.index("level_id") for t in ident4))

# ---------- H. ブロックデータの存在（860 専用表は無いのが現状） ----------
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
check("1.21.124 専用のブロック表は無い（1.20.50 世代を共用。データ担当の範囲）",
      not list(RES.glob("bedrock_block_runtime_ids_1_21_124*"))
      and not list(RES.glob("canonical_block_states_1_21_124*")))

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
