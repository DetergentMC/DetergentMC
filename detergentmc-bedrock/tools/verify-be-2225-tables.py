#!/usr/bin/env python3
"""Bedrock 26.60（プロトコル 2225）の配線検証。

Java ループの verify-735-tables.py にならい、表・定数・述語の前提を
機械計数で確認する。すべて実ファイル（BedrockVersion.java /
BedrockProtocol.java / BedrockSession.java / BedrockServer.java /
protocol-1.26.60.json / protocol-1.26.51.json / protocolVersions /
.bin データ）に対するチェックで、推測はない。

- 2225 の protocol.json は .tmp-be/protocol-1.26.60.json（1.26.60 は
  1.26.60 / 1.26.62 / 1.26.63 / 1.26.65 が共有するプロトコル 2225 のもの）。
- 2193 の protocol.json は .tmp-be/protocol-1.26.51.json（境界確認用）。
- 2225 は NetherNet 世代。パケット形式は BedrockVersion の enum で
  対応するが、RakNet 側セッションは引き続き切断する（トランスポート
  未実装）。このスクリプトはパケット層の配線だけを検証する。
- 述語の期待値は BedrockVersion.java の has*() を読んで決めたもの。
  述語そのものの実行時挙動は BedrockVersionTest で確認する。

使い方: python3 detergentmc-bedrock/tools/verify-be-2225-tables.py
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


# ---------- A. protocol.json（2225）の同一性 ----------
p60 = TMP / "protocol-1.26.60.json"
check("2225 protocol.json が存在する", p60.is_file(), str(p60))
check("2225 protocol.json のサイズが 425,474 バイト",
      p60.is_file() and p60.stat().st_size == 425474,
      f"{p60.stat().st_size} バイト" if p60.is_file() else "なし")
check("2225 protocol.json の SHA-1 が d3714ad6…と一致",
      p60.is_file()
      and sha1(p60) == "d3714ad6c914166af3de4b162cc5c9fcce65e341",
      sha1(p60) if p60.is_file() else "なし")
check("2225 protocol.json の行数が 17,095 行（wc -l＝改行数）",
      p60.is_file() and p60.read_bytes().count(b"\n") == 17095,
      f"{p60.read_bytes().count(bytes([10]))} 行" if p60.is_file() else "なし")

data60 = json.loads(p60.read_text())
types60 = data60["types"]
mapper60 = types60["mcpe_packet"][1][0]["type"][1]["mappings"]
ids60 = {int(k): v for k, v in mapper60.items()}

p51 = TMP / "protocol-1.26.51.json"
data51 = json.loads(p51.read_text())
types51 = data51["types"]
mapper51 = types51["mcpe_packet"][1][0]["type"][1]["mappings"]
ids51 = {int(k): v for k, v in mapper51.items()}

# ---------- B. パケット一覧（2225）の件数・境界 ----------
check("2225 のパケット数が 254 件", len(ids60) == 254, f"{len(ids60)} 件")
check("2225 の最大パケット ID が 360", max(ids60) == 360, f"最大 {max(ids60)}")
check("2193 のパケット数が 246 件", len(ids51) == 246, f"{len(ids51)} 件")
check("2193 の最大パケット ID が 352", max(ids51) == 352, f"最大 {max(ids51)}")
check("2225 の追加 8 件（353〜360）が実測の名前と一致",
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
check("2225 で削除されたパケットは無い（2193 の ID が全て残る）",
      set(ids51) <= set(ids60))
check("既存パケットの ID は 2193 から変わっていない",
      all(ids51[i] == ids60[i] for i in ids51))

# ---------- C. 核パケット ID：BedrockProtocol.java と protocol.json の一致 ----------
proto_src = (SRC / "BedrockProtocol.java").read_text()
java_packets = dict(re.findall(
    r"PACKET_(\w+)\s*=\s*0x([0-9a-fA-F]+);", proto_src))
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
json_by_name = {v: k for k, v in ids60.items()}
for const, pname in CORE:
    java_id = int(java_packets[const], 16)
    json_id = json_by_name.get(pname)
    check(f"パケット ID {const}（0x{java_id:02x}）が 2225 の protocol.json と一致",
          json_id == java_id, f"json={json_id}")
check("追加 8 件は BedrockProtocol.java に定数が無い（この実装は使わない）",
      all(int(v, 16) not in range(353, 361) for v in java_packets.values()))

# ---------- D. BedrockVersion の enum ----------
ver_src = (SRC / "BedrockVersion.java").read_text()
entries = re.findall(r"^\s+(V1_\w+)\((\d+),\s*\"([^\"]+)\"\)[;,]?\s*$",
                     ver_src, re.MULTILINE)
entry_by_name = {name: (int(p), disp) for name, p, disp in entries}
check("enum エントリ V1_26_60 が存在する", "V1_26_60" in entry_by_name)
check("V1_26_60 のプロトコルが 2225",
      entry_by_name.get("V1_26_60", (None,))[0] == 2225,
      str(entry_by_name.get("V1_26_60")))
check("V1_26_60 の表示名が 1.26.60",
      entry_by_name.get("V1_26_60", (None, None))[1] == "1.26.60")
check("enum エントリ V1_26_65 が存在する", "V1_26_65" in entry_by_name)
check("V1_26_65 のプロトコルが 2225",
      entry_by_name.get("V1_26_65", (None,))[0] == 2225,
      str(entry_by_name.get("V1_26_65")))
check("V1_26_65 の表示名が 1.26.65",
      entry_by_name.get("V1_26_65", (None, None))[1] == "1.26.65")
check("enum エントリが 66 件（64 プロトコル＋2193・2225 の重複各 1）",
      len(entries) == 66, f"{len(entries)} 件")
check("異なるプロトコル番号が 64 個",
      len({int(p) for _, p, _ in entries}) == 64)
check("2225 を共有するのは V1_26_60 / V1_26_65 の 2 件だけ",
      [n for n, p, _ in entries if int(p) == 2225] == ["V1_26_60", "V1_26_65"])
check("V1_26_60 が V1_26_65 より先に宣言されている（fromProtocol は先勝ち）",
      ver_src.index("V1_26_60(2225") < ver_src.index("V1_26_65(2225"))
check("NETHERNET_PROTOCOL が 2225", "NETHERNET_PROTOCOL = 2225" in ver_src)

# ---------- E. itemTableTag とアイテム対応表 ----------
m = re.search(r"case 1001, 2168, 2169, 2193, 2225 -> \"(1_26_30)\";", ver_src)
check("itemTableTag：2225 が 1_26_30 の表を共用する", m is not None,
      m.group(0) if m else "case が見つからない")
tag_bin = RES / "bedrock_item_ids_1_26_30.bin"
check("共用するアイテム表 bedrock_item_ids_1_26_30.bin が存在する",
      tag_bin.is_file())
check("アイテム表のサイズが 10,496 バイト（1312 件×8）",
      tag_bin.is_file() and tag_bin.stat().st_size == 10496,
      f"{tag_bin.stat().st_size} バイト" if tag_bin.is_file() else "なし")
check("26.60 専用のアイテム表は無い（共用のため。無いのが正しい）",
      not (RES / "bedrock_item_ids_1_26_60.bin").exists())
item_bins = sorted(RES.glob("bedrock_item_ids_*.bin"))
check("アイテム表 .bin が 38 本", len(item_bins) == 38, f"{len(item_bins)} 本")

# ---------- F. 述語の前提と protocol.json（2225）の突き合わせ ----------
for pred in ["hasEditorLevelMigrationVersion", "hasPassengerOfBlock",
             "hasClientBiomeUpdate", "transactionHasHand"]:
    mm = re.search(pred + r"\(\) \{\s*return protocol >= 2225;", ver_src)
    check(f"述語 {pred} が protocol >= 2225 で定義されている", mm is not None)

sg60 = field_names(types60, "packet_start_game")
sg51 = field_names(types51, "packet_start_game")
check("2225 の start_game が 83 フィールド", len(sg60) == 83, f"{len(sg60)} 件")
check("2193 の start_game が 82 フィールド", len(sg51) == 82, f"{len(sg51)} 件")
check("editor_level_migration_version が 2225 の start_game の 18 番目",
      sg60[18] == "editor_level_migration_version", sg60[18])
check("その前後が exported_from_editor／day_cycle_stop_time",
      sg60[17] == "exported_from_editor" and sg60[19] == "day_cycle_stop_time")
check("editor_level_migration_version の型が mapper（u8）",
      isinstance(field_type(types60, "packet_start_game",
                            "editor_level_migration_version"), list)
      and field_type(types60, "packet_start_game",
                     "editor_level_migration_version")[0] == "mapper")
check("2193 の start_game に editor_level_migration_version が無い",
      "editor_level_migration_version" not in sg51)

ap60 = container_fields(types60, "packet_add_player")
ap51 = field_names(types51, "packet_add_player")
check("2225 の add_player が 21 フィールド", len(ap60) == 21, f"{len(ap60)} 件")
check("2193 の add_player が 20 フィールド", len(ap51) == 20, f"{len(ap51)} 件")
check("passenger_of_block が add_player の 18 番目で option",
      ap60[18][0] == "passenger_of_block" and is_option(ap60[18][1]),
      str(ap60[18]))
check("passenger_of_block の直後が device_id",
      ap60[19][0] == "device_id", ap60[19][0])
check("2193 の add_player に passenger_of_block が無い",
      "passenger_of_block" not in ap51)

ae60 = container_fields(types60, "packet_add_entity")
ae51 = field_names(types51, "packet_add_entity")
check("2225 の add_entity が 14 フィールド", len(ae60) == 14, f"{len(ae60)} 件")
check("add_entity の末尾が passenger_of_block（option）",
      ae60[-1][0] == "passenger_of_block" and is_option(ae60[-1][1]),
      str(ae60[-1]))
check("2193 の add_entity に passenger_of_block が無い",
      "passenger_of_block" not in ae51)
pob = types60.get("PassengerOfBlockData")
check("PassengerOfBlockData が 5 フィールドの container",
      pob is not None and pob[0] == "container" and len(pob[1]) == 5,
      json.dumps(pob)[:80] if pob else "なし")

lc60 = container_fields(types60, "packet_level_chunk")
check("2225 の level_chunk が 9 フィールド", len(lc60) == 9, f"{len(lc60)} 件")
check("level_chunk の末尾が client_biome_update（bool）",
      lc60[-1] == ("client_biome_update", "bool"), str(lc60[-1]))
check("2193 の level_chunk に client_biome_update が無い",
      "client_biome_update" not in field_names(types51, "packet_level_chunk"))

tx60 = types60["Transaction"]
tx_fields = {f["name"]: f["type"] for f in tx60[1]}
use_entity = tx_fields["transaction_data"][1]["fields"]["item_use_on_entity"]
use_names = [f["name"] for f in use_entity[1]]
check("2225 の item_use_on_entity に hand が hotbar_slot と held_item の間",
      use_names.index("hand") == use_names.index("hotbar_slot") + 1
      and use_names.index("hand") == use_names.index("held_item") - 1,
      str(use_names))
release = tx_fields["transaction_data"][1]["fields"]["item_release"]
rel_names = [f["name"] for f in release[1]]
check("2225 の item_release の末尾が hand", rel_names[-1] == "hand",
      str(rel_names))
tx51 = types51["Transaction"]
use51 = {f["name"]: f["type"] for f in tx51[1]}["transaction_data"][1]["fields"]["item_use_on_entity"]
check("2193 の item_use_on_entity に hand が無い",
      "hand" not in [f["name"] for f in use51[1]])

# 核パケットのうち形式が変わらないものの JSON 完全一致
for tname in ["packet_login", "packet_play_status", "packet_resource_packs_info",
              "packet_resource_pack_stack",
              "packet_resource_pack_client_response", "packet_text",
              "packet_set_entity_data", "packet_move_player",
              "packet_player_auth_input"]:
    check(f"{tname} の定義が 2193 と JSON 完全一致",
          types60[tname] == types51[tname])

# Skin / PlayerRecord の play_fab_id 移行（判断待ちで未対応の現状確認）
skin60 = json.dumps(types60["Skin"])
skin51 = json.dumps(types51["Skin"])
check("2225 の Skin に play_fab_id が無い（2193 にはある）",
      "play_fab_id" not in skin60 and "play_fab_id" in skin51)
packets_src = (SRC / "BedrockPackets.java").read_text()
check("writeEmptySkin は従来通り play fab id を書く（Skin 形式の分岐は判断待ち）",
      "// play fab id" in packets_src)

# ---------- G. protocolVersions.json の境界 ----------
pv_path = TMP / "protocolVersions-fresh.json"
if not pv_path.is_file():
    pv_path = TMP / "protocolVersions.json"
pv = json.loads(pv_path.read_text())
versions = sorted({e["version"] for e in pv})
check("protocolVersions.json の最大プロトコルが 2225",
      versions[-1] == 2225, f"最大 {versions[-1]}")
check("2225 のエントリは 1.26.60 の 1 件のみ",
      [e["minecraftVersion"] for e in pv if e["version"] == 2225] == ["1.26.60"],
      str([e["minecraftVersion"] for e in pv if e["version"] == 2225]))
check("2194〜2224 に protocolVersions.json のエントリは無い",
      all(v not in versions for v in range(2194, 2225)))

# ---------- H. ブロックデータの存在（26.60 専用表は無いのが現状） ----------
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
check("26.60 専用のブロック表は無い（1.20.50 世代を共用。データ担当の範囲）",
      not list(RES.glob("bedrock_block_runtime_ids_1_26*"))
      and not list(RES.glob("canonical_block_states_1_26*")))

# ---------- I. トランスポート側の現状（未実装の明示） ----------
check("TARGET_VERSION は 1.20.50 のまま（MOTD 専用）",
      'TARGET_VERSION = "1.20.50"' in proto_src)
session_src = (SRC / "BedrockSession.java").read_text()
check("Session の NetherNet 切断分岐がある（RakNet 経由の 2225 は切断）",
      "isNetherNetProtocol(login.protocolVersion())" in session_src)
check("Session の NetherNet 切断メッセージがパケット形式対応済みに更新されている",
      "the packet format is supported" in session_src
      and "the NetherNet transport is not implemented yet" in session_src)
check("Session の対応外メッセージが 291-2193／2225 の扱いを明示する",
      "(supported: 291-2193 over RakNet; 2225 needs NetherNet)" in session_src)
check("Session が encodeLevelChunk に bedrockVersion を渡す",
      "chunk.payload(), bedrockVersion)" in session_src)
server_src = (SRC / "BedrockServer.java").read_text()
check("BedrockServer の起動ログが 2225 の扱い（NetherNet リスナー未実装）を明示する",
      "2225 はパケット形式のみ対応し、NetherNet リスナーは未実装" in server_src)
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
