#!/usr/bin/env python3
"""Bedrock 26.52（プロトコル 2193）の配線検証。

Java ループの verify-735-tables.py にならい、表・定数・述語の前提を
機械計数で確認する。すべて実ファイル（BedrockVersion.java /
BedrockProtocol.java / BedrockSession.java / protocol-1.26.51.json /
protocol-1.26.60.json / .bin データ）に対するチェックで、推測はない。

- 2193 の protocol.json は .tmp-be/protocol-1.26.51.json（1.26.51 は
  1.26.50 / 1.26.51 / 1.26.52 が共有するプロトコル 2193 のもの）。
- 2225 の protocol.json は .tmp-be/protocol-1.26.60.json（NetherNet 世代。
  2193 との境界確認用）。
- 述語の期待値は BedrockVersion.java の has*() を読んで決めたもの。
  このスクリプトは protocol.json 側の事実（フィールドの有無・順序・
  型）と突き合わせ、述語の前提が 2193 で崩れていないことを確認する。
  述語そのものの実行時挙動は BedrockVersionTest で確認する。

使い方: python3 detergentmc-bedrock/tools/verify-be-2193-tables.py
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


# ---------- A. protocol.json（2193）の同一性 ----------
p51 = TMP / "protocol-1.26.51.json"
check("2193 protocol.json が存在する", p51.is_file(), str(p51))
check("2193 protocol.json のサイズが 413,096 バイト",
      p51.is_file() and p51.stat().st_size == 413096,
      f"{p51.stat().st_size} バイト" if p51.is_file() else "なし")
check("2193 protocol.json の SHA-1 が f4d60c7e…と一致",
      p51.is_file()
      and sha1(p51) == "f4d60c7e21328f3362aa9a74f5f5fa34f1b58eae",
      sha1(p51) if p51.is_file() else "なし")

data51 = json.loads(p51.read_text())
types51 = data51["types"]
mapper51 = types51["mcpe_packet"][1][0]["type"][1]["mappings"]
ids51 = {int(k): v for k, v in mapper51.items()}

# ---------- B. パケット一覧（2193）の件数・境界 ----------
check("2193 のパケット数が 246 件", len(ids51) == 246, f"{len(ids51)} 件")
check("2193 の最大パケット ID が 352", max(ids51) == 352, f"最大 {max(ids51)}")
check("2225 で追加された ID 353〜360 が 2193 に無い",
      all(i not in ids51 for i in range(353, 361)))

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
json_by_name = {v: k for k, v in ids51.items()}
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
check("enum エントリ V1_26_52 が存在する", "V1_26_52" in entry_by_name)
check("V1_26_52 のプロトコルが 2193",
      entry_by_name.get("V1_26_52", (None,))[0] == 2193,
      str(entry_by_name.get("V1_26_52")))
check("V1_26_52 の表示名が 1.26.52",
      entry_by_name.get("V1_26_52", (None, None))[1] == "1.26.52")
check("enum エントリが 66 件（64 プロトコル＋2193・2225 の重複各 1）",
      len(entries) == 66, f"{len(entries)} 件")
check("異なるプロトコル番号が 64 個",
      len({int(p) for _, p, _ in entries}) == 64)
check("2193 を共有するのは V1_26_50 / V1_26_52 の 2 件だけ",
      [n for n, p, _ in entries if int(p) == 2193] == ["V1_26_50", "V1_26_52"])
check("V1_26_50 が V1_26_52 より先に宣言されている（fromProtocol は先勝ち）",
      ver_src.index("V1_26_50(2193") < ver_src.index("V1_26_52(2193"))
check("fromProtocol の先勝ちが javadoc に明記されている",
      "最初に一致した V1_26_50" in ver_src)

# ---------- E. itemTableTag とアイテム対応表 ----------
m = re.search(r"case 1001, 2168, 2169, 2193, 2225 -> \"(1_26_30)\";", ver_src)
check("itemTableTag：2193 が 1_26_30 の表を共用する", m is not None,
      m.group(0) if m else "case が見つからない")
tag_bin = RES / "bedrock_item_ids_1_26_30.bin"
check("共用するアイテム表 bedrock_item_ids_1_26_30.bin が存在する",
      tag_bin.is_file())
check("アイテム表のサイズが 10,496 バイト（1312 件×8）",
      tag_bin.is_file() and tag_bin.stat().st_size == 10496,
      f"{tag_bin.stat().st_size} バイト" if tag_bin.is_file() else "なし")
check("26.52 専用のアイテム表は無い（共用のため。無いのが正しい）",
      not (RES / "bedrock_item_ids_1_26_52.bin").exists())
item_bins = sorted(RES.glob("bedrock_item_ids_*.bin"))
check("アイテム表 .bin が 38 本", len(item_bins) == 38, f"{len(item_bins)} 本")

# ---------- F. 述語の前提と protocol.json（2193）の突き合わせ ----------
sg = field_names(types51, "packet_start_game")
check("start_game に hardcore がある（hasHardcore=true）", "hardcore" in sg)
check("start_game に is_chat_logging が無い（hasChatLogging=false）",
      "is_chat_logging" not in sg)
check("start_game に server_editor_connection_policy がある（hasEditorPolicy=true）",
      "server_editor_connection_policy" in sg)
check("start_game に has_server_join_info／server_join_info がある（hasJoinInfoTail=true）",
      "has_server_join_info" in sg and "server_join_info" in sg)
tail = ["server_identifier", "scenario_identifier",
        "world_identifier", "owner_identifier"]
check("start_game の末尾に識別子 4 つが server_join_info の後にある",
      all(t in sg for t in tail)
      and all(sg.index(t) > sg.index("server_join_info") for t in tail))
check("識別子 4 つは level_id の前に無い（hasPreLevelIdentifiers/hasOwnerIdentifier=false）",
      all(sg.index(t) > sg.index("level_id") for t in tail))
check("start_game に movement_authority が無い（hasMovementAuthority=false）",
      "movement_authority" not in sg)
check("start_game に itemstates が無い（hasItemstates=false）",
      "itemstates" not in sg)
check("start_game に tick_death_systems が無い（hasTickDeathSystems=false）",
      "tick_death_systems" not in sg)

rpi = field_names(types51, "packet_resource_packs_info")
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
tpi = types51["TexturePackInfos"]
check("texture_packs の件数が varint（resourcePacksCountIsVarint=true）",
      tpi[0] == "array" and tpi[1].get("countType") == "varint",
      json.dumps(tpi[1].get("countType")) if isinstance(tpi[1], dict) else str(tpi))

rps = field_names(types51, "packet_resource_pack_stack")
check("resource_pack_stack に behavior_packs が無い（hasBehaviorPacksInStack=false）",
      "behavior_packs" not in rps)
check("resource_pack_stack に has_editor_packs がある（hasEditorPacksInStack=true）",
      "has_editor_packs" in rps)

me = field_type(types51, "packet_mob_equipment", "item")
check("mob_equipment の item が ItemV4（mobEquipmentUsesItemV4=true）",
      me == "ItemV4", str(me))
aie = field_type(types51, "packet_add_item_entity", "item")
check("add_item_entity の item が ItemV4（addItemUsesItemV4=true）",
      aie == "ItemV4", str(aie))
mae = container_fields(types51, "packet_mob_armor_equipment")
mae_items = [n for n, t in mae if t == "ItemV4"]
check("mob_armor_equipment が ItemV4×5（helmet/chestplate/leggings/boots/body）",
      mae_items == ["helmet", "chestplate", "leggings", "boots", "body"],
      str(mae_items))

legacy = types51["TransactionLegacy"]
legacy_map = {f["name"]: f["type"] for f in legacy[1]}
legacy_tx = legacy_map.get("legacy_transactions")
check("Transaction の legacy_transactions が option 形式（transactionLegacyIsOption=true）",
      is_option(legacy_tx), json.dumps(legacy_tx)[:80])
actions = types51["TransactionActions"]
act_fields = {f["name"]: f["type"] for f in actions[1]["type"][1]}
check("TransactionActions の window_id／flags が option 形式（transactionFieldsAreOptions=true）",
      is_option(act_fields.get("window_id")) and is_option(act_fields.get("flags")))
check("TransactionActions の old/new item が ItemV4（transactionUsesItemV4=true）",
      act_fields.get("old_item") == "ItemV4" and act_fields.get("new_item") == "ItemV4")
tx = types51["Transaction"]
tx_map = {f["name"]: f["type"] for f in tx[1]}
use_entity = tx_map["transaction_data"][1]["fields"]["item_use_on_entity"]
held = [f["type"] for f in use_entity[1] if f["name"] == "held_item"]
check("item_use_on_entity の held_item が ItemV4",
      held == ["ItemV4"], str(held))

# ---------- G. NetherNet（2225）の境界 ----------
check("NETHERNET_PROTOCOL が 2225", "NETHERNET_PROTOCOL = 2225" in ver_src)
check("2225 が対応プロトコル一覧にある（パケット形式のみ。NetherNet 世代）",
      2225 in {int(p) for _, p, _ in entries})
p60 = TMP / "protocol-1.26.60.json"
check("2225 protocol.json（1.26.60）が存在する", p60.is_file())
if p60.is_file():
    data60 = json.loads(p60.read_text())
    mapper60 = data60["types"]["mcpe_packet"][1][0]["type"][1]["mappings"]
    ids60 = {int(k): v for k, v in mapper60.items()}
    check("2225 のパケット数が 254 件", len(ids60) == 254, f"{len(ids60)} 件")
    check("2225 の最大パケット ID が 360", max(ids60) == 360, f"最大 {max(ids60)}")
    check("2225 の追加 8 件（353〜360）が 2193 との差分と一致",
          [ids60[i] for i in range(353, 361)] == [
              "clientbound_matchmaking_state",
              "serverbound_stonecutter_set_recipe",
              "clientbound_stonecutter_set_recipe",
              "serverbound_matchmaking_cancel",
              "set_passenger_of_block",
              "serverbound_cursor_item_drag",
              "clientbound_play_audio_content",
              "serverbound_register_audio_content",
          ])

# ---------- H. ブロックデータの存在（26.52 専用表は無いのが現状） ----------
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
check("26.52 専用のブロック表は無い（1.20.50 世代を共用。データ担当の範囲）",
      not list(RES.glob("bedrock_block_runtime_ids_1_26*"))
      and not list(RES.glob("canonical_block_states_1_26*")))

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
